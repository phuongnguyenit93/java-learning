<a id="back-to-top"></a>

# Gỡ lỗi và chẩn đoán

## Menu
- [Xác nhận aspect đã được cấu hình và nạp](#aop-debugging-diagnostics-001)
- [Kiểm tra mã hoặc mô-đun có nằm trong phạm vi dệt](#aop-debugging-diagnostics-002)
- [Đọc thông báo điểm đã được dệt và điểm không phù hợp](#aop-debugging-diagnostics-003)
- [Đối chiếu chữ ký thực tế với biểu thức pointcut](#aop-debugging-diagnostics-004)
- [Phân biệt lỗi không khớp pointcut với lời gọi không qua proxy](#aop-debugging-diagnostics-005)
- [Đọc ngăn xếp lời gọi có advice và mã được sinh](#aop-debugging-diagnostics-006)
- [Đặt điểm dừng trong advice, mã gốc và cơ chế gọi tiếp](#aop-debugging-diagnostics-007)
- [Chẩn đoán đệ quy, advice chạy nhiều lần hoặc sai thứ tự](#aop-debugging-diagnostics-008)
- [Tạo tình huống tái hiện tối thiểu cho lỗi tương tác aspect](#aop-debugging-diagnostics-009)

## <a id="aop-debugging-diagnostics-001">Xác nhận aspect đã được cấu hình và nạp</a>

<details>
<summary>Xem chi tiết</summary>

# Xác nhận aspect đã được cấu hình và nạp trong AOP

## 1. Vì sao kiểm tra một tệp aspect tồn tại là chưa đủ?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một **aspect** mô-đun hóa mối quan tâm cắt ngang như ghi nhật ký, đo thời gian, tracing, kiểm soát truy cập hoặc theo dõi lỗi. Một **pointcut** chọn những **join point** mà aspect cần can thiệp; **advice** là logic được thực thi tại những điểm ấy. Tùy mô hình AOP, cơ chế áp dụng có thể là weaving, interceptor, proxy, decorator hay một dạng instrumentation khác.

Khi lời ghi log mong đợi không xuất hiện, nhiều người kết luận ngay rằng pointcut sai. Nhưng một aspect có thể **chưa hề được đăng ký, nạp hoặc gắn vào chương trình**. Ngược lại, aspect có thể đã nạp thành công mà vẫn **không có join point nào khớp** hoặc lời gọi đi qua một đường không được instrument.

Vì vậy, cần phân biệt ít nhất bốn lớp bằng chứng:

1. **Được khai báo/cấu hình**: chương trình đã có thông tin rằng aspect nên được áp dụng.
2. **Được phát hiện và nạp/khởi tạo**: mã aspect hoặc đối tượng chứa advice đã được runtime/build pipeline nhận diện và sẵn sàng hoạt động.
3. **Được liên kết với điểm thực thi phù hợp**: weaving, proxy hoặc interceptor chain thực sự có mặt tại join point cần quan sát.
4. **Đã kích hoạt trong một lượt thực thi cụ thể**: điểm thực thi xảy ra, pointcut/điều kiện runtime thỏa mãn, và advice chạy đúng theo chính sách.

Không được suy từ mức 1 rằng mức 4 chắc chắn đúng. Tương tự, một log không xuất hiện **không đủ** để kết luận aspect chưa nạp: log có thể bị lọc, sink hỏng, nhánh điều kiện không thỏa hoặc thao tác chưa đi qua join point.

Mục tiêu cuối cùng không phải “thấy tên aspect trong cấu hình” mà là có **bằng chứng kiểm chứng được từ cấu hình đến hành vi thật**.

## 2. Bộ khái niệm chẩn đoán

**Aspect:** module định nghĩa concern, pointcut và advice hoặc các quy tắc liên quan. Một số hiện thực AOP tạo đối tượng aspect ở runtime; một số dệt logic thành bytecode từ trước. Vì vậy, “đã load một instance aspect” không phải cách kiểm tra phổ quát.

**Join point:** vị trí mà cơ chế AOP có thể quan sát/can thiệp, ví dụ lời gọi phương thức hoặc thực thi thân phương thức tùy join point model.

**Pointcut:** biểu thức/quy tắc chọn join point. Có thể chứa điều kiện tĩnh trên method/type và điều kiện động trên arguments, object hoặc control flow.

**Advice:** hành vi chạy trước, sau hoặc bao quanh join point, có thể điều khiển việc thực thi target theo contract.

**Weaving:** thao tác gắn aspect vào chương trình. Việc weaving có thể xảy ra khi biên dịch, với bytecode có sẵn, khi nạp lớp hoặc qua các cơ chế instrumentation phù hợp. Không phải mọi AOP đều dùng weaving bytecode.

**Proxy/interceptor:** lớp trung gian hoặc chuỗi callback kiểm soát lời gọi đi qua một đối tượng đại diện. Nếu caller gọi thẳng target, lời gọi có thể không đi qua proxy.

**Activation:** một lời chạy cụ thể làm advice thực thi. Để xác nhận activation, cần một lượt gọi và bằng chứng độc lập như bộ đếm advice, marker test-only hoặc kết quả có thể quan sát được.

**Coverage:** phạm vi các kiểu điểm, lớp, method, call site và đường vào thực sự được bảo vệ. Một lần advice chạy **chưa chứng minh** toàn bộ ứng dụng có coverage đầy đủ.

## 3. Chuỗi kiểm chứng từ ý định đến hành vi

~~~text
Aspect được khai báo
         |
         v
Cấu hình / registration được đọc
         |
         v
Aspect được nhận diện và sẵn sàng
         |
         v
Target/join point thuộc phạm vi instrument
         |
         v
Pointcut match (và điều kiện động đúng)
         |
         v
Caller đi qua đúng ranh giới
         |
         v
Advice thực sự chạy
         |
         v
Lỗi/giá trị/side effect vẫn đúng chính sách
~~~

Mỗi mũi tên là một giả định có thể kiểm tra riêng. Không được nhảy từ “có annotation/tệp cấu hình” sang “advice chắc chắn chạy” khi chưa xác minh đường nối ở giữa.

Bảng kiểm chứng:

| Mức | Câu hỏi | Bằng chứng phù hợp |
| --- | --- | --- |
| Khai báo | Có quy tắc chọn aspect trong cấu hình/build không? | Nguồn cấu hình và artifact build đúng |
| Registration | Bộ quản lý đã đăng ký aspect/interceptor chưa? | Metadata hoặc danh sách registration ở runtime |
| Nạp/khởi tạo | Mã aspect có thể được runtime sử dụng không? | Diagnostic lifecycle, log nạp hoặc kiểm tra instance khi có |
| Instrumentation | Target/call site có được weave/wrap không? | Weave report, kiểm tra bytecode/proxy chain hoặc công cụ phù hợp |
| Pointcut | Chữ ký và điều kiện có match không? | Diagnostic match, test gọi đúng method/context |
| Invocation | Caller thực sự đi qua điểm được instrument không? | Trace test-only hoặc invocation spy |
| Activation | Advice đã chạy bao nhiêu lần và khi nào? | Bộ đếm/marker từ chính advice |
| Outcome | Advice có giữ/đổi kết quả theo policy không? | Assert giá trị, exception và side effect |

Một chương trình có thể pass các bước đầu mà fail bước cuối, hoặc ngược lại một log tình cờ xuất hiện từ code thủ công nhưng aspect thực ra chưa kích hoạt. Cần bằng chứng **liên quan đúng cơ chế AOP**.

## 4. Bước 1 — xác định cấu hình có hiệu lực, không chỉ cấu hình trên đĩa

“Đã cấu hình” nghĩa là bản cấu hình đó **được build hoặc runtime hiện tại thực sự sử dụng**. Những lỗi thường thấy:

- Tệp cấu hình nằm trong source tree nhưng không được đưa vào package/artifact cuối.
- Một profile hoặc biến môi trường làm tắt instrumentation.
- Điều kiện cho phép tải aspect chỉ bật ở development mà tắt ở production.
- Build đã thay đổi nhưng ứng dụng đang chạy bytecode/artifact cũ.
- Hai cấu hình cùng tên, một bản mới không được chọn.
- Một module/plug-in chứa aspect chưa được đưa vào classpath/module path.
- Cấu hình load-time agent không được truyền cho đúng tiến trình.
- Aspect registration được thực thi quá muộn: một số đối tượng/call site đã được tạo trước khi interceptor được lắp đặt.

Các câu hỏi cần trả lời:

1. **Tiến trình nào** đang được chẩn đoán, dùng artifact/build nào?
2. **Bản cấu hình hiệu lực** chứa lựa chọn aspect nào?
3. **Ai là nơi đọc cấu hình**: build tool, class loader, agent, container của ứng dụng hay mã tự đăng ký interceptor?
4. **Thời điểm** cấu hình được đọc là trước hay sau target được biên dịch/tạo/nạp?
5. Có điều kiện feature toggle, environment hoặc predicate nào làm aspect không đăng ký không?

Khi dùng cấu hình runtime, nên xuất một **diagnostic summary có kiểm soát**: tên component, phiên bản cấu hình, loại instrumentation, danh sách target pattern và trạng thái bật/tắt. Không cần xuất secret/token hay toàn bộ cấu hình nhạy cảm để chứng minh aspect được chọn.

## 5. Bước 2 — phân biệt “được tạo/nạp” với “đã dệt vào target”

Một nhầm lẫn phổ biến là xem constructor hoặc static initializer của một class aspect đã chạy như bằng chứng aspect hoạt động ở mọi join point. Điều đó chỉ chứng minh code đã được nạp/khởi tạo theo đường đó; **không chứng minh** pointcut match hay method target đã instrument.

Ngược lại, trong một số cơ chế weaving tĩnh, logic advice có thể đã được gắn vào bytecode mà không cần quản lý một instance aspect giống đối tượng thông thường. Vì vậy, không nên đặt tiêu chí “phải tìm thấy bean/instance tên X” làm luật chung cho AOP.

### Trong mô hình interceptor hoặc proxy

Bằng chứng có thể gồm:

- Registration của interceptor/advice có mặt trong chain.
- Object mà caller dùng có đúng là đối tượng đại diện/decorator hay không.
- Invocation handler đang trỏ đến target và chain phù hợp.
- Đối tượng có được xây qua composition root/factory phù hợp hay tạo thẳng bằng constructor.
- Registration có đúng phiên bản, pointcut hoặc thứ tự interceptor không.

### Trong mô hình weaving

Bằng chứng có thể gồm:

- Compiler/weaver report về những lớp và join point được dệt.
- Class/jar hoặc artifact thực tế đã chứa kết quả weaving.
- Agent/class loader được kích hoạt trên đúng tiến trình khi load-time weaving.
- Target class đã được nạp sau khi instrumentation được thiết lập nếu cơ chế yêu cầu.
- Mã chạy thật có phải bytecode đã dệt hay một bản duplicate không được dệt.

**Một lớp được load thành công khác với một lớp đã được woven.** Hai trạng thái này có thể đồng thời đúng, chỉ một trạng thái đúng, hoặc ý nghĩa khác nhau tùy mô hình.

## 6. Bước 3 — khớp pointcut và xác định đúng loại join point

Khi registration và instrumentation đã đúng mà advice vẫn không chạy, mới nên điều tra **selection**:

- Tên class, package, module hoặc method có khác pattern không?
- Method signature có thêm overload, đổi kiểu trả về, đổi parameter không?
- Pointcut chọn **method call** hay **method execution**?
- Kiểu join point đó được runtime hỗ trợ không?
- Method thuộc class đã được instrument hay được nạp bởi class loader khác?
- Điều kiện annotation có retention và vị trí phù hợp không?
- Điều kiện argument, object identity hoặc dynamic control flow đã thỏa chưa?
- Có điều kiện loại trừ hoặc pointcut phủ định làm mất match không?
- Aspect có bị precedence/around advice khác short-circuit trước khi đến phần bên trong không?

Ví dụ biểu thức pointcut trong một ngôn ngữ AOP cụ thể có thể chọn method execution:

~~~java
// Mã minh họa ý tưởng một ngôn ngữ pointcut; không phải Java độc lập.
execution(* ReportService.export(..))
~~~

**Điểm tổng quát:** phải phân biệt “cấu hình aspect đã nạp” với “biểu thức chọn đúng điểm”. Cú pháp trên chỉ minh họa một ngôn ngữ pointcut, không được coi là tiêu chuẩn bắt buộc của mọi công nghệ AOP.

### Call và execution có thể cho kết quả khác

~~~text
CallerA ----- call site A ----+
                             |
CallerB ----- call site B ---> Target.process()
                             ^ method execution
~~~

Một cơ chế instrument các call site chỉ áp dụng được tại những caller được instrument. Một cơ chế instrument method execution có thể quan sát phần thân method bất kể caller cụ thể, miễn phần thân đã được instrument. Vì vậy, cần kiểm tra **đường caller thật** khi chẩn đoán, không chỉ tên method.

Một self-call trong cùng object có thể bỏ qua proxy-based interception nhưng vẫn đi qua method-execution weaving. Không kết luận “AOP luôn bỏ qua lời gọi nội bộ” hoặc “AOP luôn bắt được lời gọi nội bộ” khi chưa biết cơ chế đang dùng.

## 7. Bước 4 — tạo probe tối thiểu có kết quả quan sát độc lập

Cách đáng tin nhất để xác nhận advice hoạt động là tạo một **phép thử có mục tiêu**:

1. Chọn một method/operation nhỏ, deterministic và không có side effect nguy hiểm.
2. Bảo đảm test gọi đúng đối tượng hoặc artifact cần instrument.
3. Bật marker ở chính advice cho môi trường test, hoặc đếm số lần advice thực thi.
4. Gọi method đúng một lần qua đường cần kiểm tra.
5. Assert kết quả nghiệp vụ vẫn đúng và advice được chạy đúng số lần.
6. Có **negative control**: gọi đường không instrument (nếu cho phép) để thấy marker không xuất hiện, hoặc vô hiệu hóa registration trong test.
7. Nếu có nhiều advice, kiểm tra thứ tự và tương tác; một before chạy không có nghĩa around/after cũng đúng.

Cần tách ba dấu hiệu:

- **Target chạy**: chỉ chứng minh method nghiệp vụ được gọi.
- **Advice marker chạy**: chứng minh một advice cụ thể đã kích hoạt.
- **Kết quả nghiệp vụ đúng**: chứng minh việc thêm advice không làm sai return/exception trong trường hợp test.

Một marker đặt ngoài advice, ví dụ handler đã tự ghi dòng “ABOUT TO CALL”, **không phải** bằng chứng advice chạy.

### Không dùng production logging làm nguồn duy nhất

Nếu log sink bị tắt, bị filter hoặc lỗi, advice có thể đã chạy mà dòng log không được xuất ra. Ngược lại, một dòng log có thể được in thủ công từ handler mà không liên quan aspect. Trong test, ưu tiên counter/spy được đặt trực tiếp ở advice và assertion rõ ràng. Trong production, có thể dùng event/metrics chẩn đoán riêng, theo chính sách bảo mật và tải hệ thống.

## 8. Ví dụ Java 17+ chạy được: chứng minh registration và interception

Ví dụ sau không cần một thư viện AOP bên ngoài. Nó dùng Java dynamic proxy để **minh họa một cơ chế invocation interception**, từ đó thấy rõ các tầng bằng chứng. Dynamic proxy của Java là một **phương tiện minh họa**, không phải định nghĩa đầy đủ của AOP hay thay thế mọi khả năng weaving.

Mục tiêu:

- Có một handler đóng vai trò advice quanh method invocation.
- Khi caller dùng object proxy, handler chạy.
- Khi caller cầm object đích và gọi trực tiếp, handler **không chạy**.
- Khi target tự gọi method khác trên cùng instance, lời gọi nội bộ không tự đi qua proxy bên ngoài.
- Vẫn kiểm chứng giá trị trả về của từng lời gọi và số lượt target chạy.

Tạo tệp **AspectActivationDemo.java**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AspectActivationDemo {
    interface Work {
        String run(String input);
        String internal();
    }

    static final class WorkImpl implements Work {
        int runs = 0;

        @Override
        public String run(String input) {
            runs++;
            System.out.println("TARGET run " + input);
            return "OK:" + input;
        }

        @Override
        public String internal() {
            System.out.println("TARGET internal");
            return run("inner");
        }
    }

    static final class TraceHandler implements InvocationHandler {
        private final Work target;
        int adviceCalls = 0;

        TraceHandler(Work target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            adviceCalls++;
            System.out.println("ADVICE before " + method.getName());
            try {
                Object result = method.invoke(target, args);
                System.out.println("ADVICE after " + method.getName());
                return result;
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }

    public static void main(String[] args) {
        WorkImpl target = new WorkImpl();
        TraceHandler handler = new TraceHandler(target);

        Work proxy = (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] { Work.class },
                handler);

        System.out.println("PROXY " + Proxy.isProxyClass(proxy.getClass()));
        System.out.println("RESULT outer=" + proxy.run("outer"));
        System.out.println("RESULT internal=" + proxy.internal());
        System.out.println("RESULT direct=" + target.run("direct"));
        System.out.println("ADVICE_CALLS " + handler.adviceCalls);
        System.out.println("TARGET_RUNS " + target.runs);

        if (handler.adviceCalls != 2 || target.runs != 3) {
            throw new AssertionError("Unexpected interception coverage");
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac AspectActivationDemo.java
java AspectActivationDemo
~~~

Đầu ra dự kiến (xác định đối với chương trình này):

~~~text
PROXY true
ADVICE before run
TARGET run outer
ADVICE after run
RESULT outer=OK:outer
ADVICE before internal
TARGET internal
TARGET run inner
ADVICE after internal
RESULT internal=OK:inner
TARGET run direct
RESULT direct=OK:direct
ADVICE_CALLS 2
TARGET_RUNS 3
~~~

### Giải thích vì sao hai bộ đếm khác nhau

- Object proxy được tạo và caller sử dụng qua interface Work; dòng PROXY true xác nhận class proxy được tạo.
- proxy.run("outer") đi qua InvocationHandler: adviceCalls tăng một lần; target.run cũng tăng một lần.
- proxy.internal() đi qua InvocationHandler một lần nữa, nên adviceCalls tăng thành hai.
- Thân target.internal() gọi run("inner") **trên chính đối tượng target**, không đi qua proxy; vì thế không xuất hiện dòng ADVICE before run ở bước này.
- target.run("direct") được gọi thẳng trên target, cũng không đi qua proxy.
- Tổng cộng có **hai lời gọi interceptor** và **ba lần thực thi method run của target** (outer, inner, direct).

Điều này minh họa khác biệt giữa:

1. Registration/creation của proxy.
2. Caller sử dụng proxy ở đúng ranh giới.
3. Advice thực sự kích hoạt.
4. Phạm vi không bao gồm lời gọi trực tiếp hoặc self-call.

**Giới hạn của ví dụ:** Java dynamic proxy intercept lời gọi qua những interface được proxy triển khai; nó không tự thêm method-execution weaving vào bytecode target, không bắt mọi field access và không phải một AOP engine toàn diện. Với cơ chế instrumentation khác, self-invocation có thể có behavior khác. Không chuyển trực tiếp kết luận của ví dụ thành quy tắc chung cho mọi AOP.

## 9. Bằng chứng “đã nạp” có giá trị đến đâu?

Có thể sắp các tín hiệu chẩn đoán theo độ mạnh:

| Bằng chứng | Kết luận cho phép | Không đủ chứng minh |
| --- | --- | --- |
| Tệp aspect/annotation có trong source | Có khai báo về mặt source | Runtime dùng đúng file đó |
| Cấu hình hiệu lực nhắc đến aspect | Runtime/build được yêu cầu áp dụng aspect | Aspect đã instrument target |
| Class aspect đã được class loader nạp | Class hiện diện trong tiến trình/loader | Có join point match |
| Constructor/initializer aspect chạy | Đối tượng/thành phần được khởi tạo | Mỗi method cần bảo vệ đều đi qua advice |
| Có woven-bytecode/weaver report | Target/join point tương ứng đã instrument theo report | Một lượt runtime sẽ nhất định kích hoạt |
| Object proxy tồn tại | Có đại diện được tạo | Mọi caller đều dùng object proxy |
| Counter trong advice tăng sau lời gọi test | Advice đã chạy trên lượt đó | Mọi đường vào đều được bảo vệ |
| Runtime test trên nhiều đường PASS | Coverage đã được kiểm chứng trong tập trường hợp test | Bao phủ tuyệt đối mọi đường/phiên bản chưa kiểm thử |

Không có một tín hiệu duy nhất thay thế toàn bộ chuỗi. Một xác nhận “aspect đã nạp” mạnh nhất thường là **tập bằng chứng**: cấu hình hiệu lực, registration/weaving đúng target, và test chạy advice trong đường vào mong muốn.

## 10. Chẩn đoán khi advice không chạy: cây quyết định

~~~text
Không thấy advice chạy
        |
        v
Cấu hình hiệu lực có bật/đăng ký aspect?
   | KHÔNG -> kiểm tra packaging, profile, module, startup
   | CÓ
   v
Mã aspect đã được nhận diện/nạp theo mô hình AOP?
   | KHÔNG -> kiểm tra discovery, loader, agent, compiler, classpath
   | CÓ
   v
Target/call site đã được instrument/wrap?
   | KHÔNG -> kiểm tra weaving/proxy creation và object identity
   | CÓ
   v
Pointcut match join point và điều kiện runtime?
   | KHÔNG -> kiểm tra signature, annotation, args, exclusion
   | CÓ
   v
Caller đã đi qua đúng ranh giới?
   | KHÔNG -> direct call, self-call, object ngoài vòng quản lý
   | CÓ
   v
Có advice khác short-circuit hoặc order gây khác biệt?
   | CÓ -> kiểm tra chain và precedence
   | KHÔNG
   v
Advice có chạy nhưng bằng chứng/log bị lọc/hỏng?
   | CÓ -> dùng counter/spy/test marker trực tiếp
   | KHÔNG -> kiểm tra bản build, concurrency, runtime diagnostics
~~~

Đây là **thứ tự điều tra có tính kinh tế**: trước tiên chứng minh có registration và instrumentation, sau đó mới đào sâu pointcut. Đừng bắt đầu bằng việc đổi pattern vô tội vạ, vì nó có thể vô tình mở rộng coverage gây ảnh hưởng ngoài mong muốn.

## 11. Những nguyên nhân cấu hình và nạp thường gặp

### 11.1. Chưa đưa aspect vào artifact

Một file source xuất hiện trong IDE nhưng bị loại khỏi compilation/package. Hãy kiểm tra artifact mà tiến trình thực sự chạy, không chỉ working tree của developer.

### 11.2. Mã được biên dịch nhưng không được weaving

Một compilation Java thông thường có thể tạo class target không chứa instrumentation trong khi aspect definition vẫn nằm trong dự án. Nếu mô hình yêu cầu compile-time weaving, phải chắc build pipeline đã chạy compiler/weaver với cả aspect và target.

### 11.3. Agent load-time không được bật ở tiến trình thật

Một môi trường local có thể truyền agent cho JVM còn staging/production thì không. Hãy kiểm tra launch arguments và class loader của đúng tiến trình. Không kết luận dựa vào một console khác hay process chưa đúng.

### 11.4. Object đích được tạo thẳng

Nếu chỉ những object xây qua một factory/proxying layer mới có interceptor, code gọi new Target() có thể bỏ qua. Kiểm tra object identity và class của object caller đang giữ.

### 11.5. Đăng ký sau khi object hoặc class đã được tạo/nạp

Một số cơ chế yêu cầu registration trước khi tạo proxy hoặc trước khi class được instrument. Nếu setup chạy muộn, những đối tượng cũ có thể không được cập nhật tự động.

### 11.6. Chọn nhầm loại join point

Một pointcut cho call site không tự động tương đương pointcut cho method execution. Nếu caller không được instrument, advice call có thể không chạy dù method được thực thi.

### 11.7. Điều kiện động không thỏa

Pointcut có thể khớp tên method nhưng chỉ kích hoạt khi tham số, annotation, actor hoặc control flow đúng. Cần test cả trường hợp thỏa và không thỏa; nếu chỉ nhìn một lượt không match, dễ chẩn đoán sai.

### 11.8. Một aspect khác thay luồng thực thi

Around advice ngoài cùng có thể trả fallback mà không tiếp tục chain, nên target và advice phía trong không chạy. Đây là **hành vi điều khiển hợp lệ theo một policy**, không nhất thiết là lỗi nạp aspect.

### 11.9. Chạy một bản class khác

Đường classpath, duplicate dependency, shading, jar cũ hoặc class loader riêng có thể làm runtime chọn bytecode khác bản đã inspect. So sánh build identifier, class origin và phiên bản; không log thông tin nhạy cảm ngoài nhu cầu chẩn đoán.

### 11.10. Lỗi telemetry đánh lừa kết quả

Logger bị filter hoặc exception bị bắt ở nơi khác làm marker biến mất. Advice có thể chạy nhưng không tạo dòng log; dùng spy/counter có assert thay vì chỉ tìm chuỗi văn bản.

## 12. Đối chiếu bằng chứng nạp aspect với cơ chế AOP đang chạy

Không có một dấu hiệu **“aspect đã load”** áp dụng cho mọi cơ chế. Hãy chọn **bằng chứng kích hoạt** phù hợp, rồi thử một join point dương và một join point âm:

| Cơ chế | Bằng chứng tối thiểu về cấu hình/nạp | Bằng chứng bổ sung để xác nhận advice hoạt động |
| --- | --- | --- |
| **Compile-time weaving** | Aspect và source target thực sự là input của compiler AOP ở build tương ứng | Weave diagnostics/artifact đã dệt và phép gọi vào method target trong artifact cuối |
| **Post-compile weaving** | Binary aspect/weaver được chạy trên đúng class/JAR input | Output woven **khác** bản gốc, được đóng gói và nạp thật; test dương/âm trên output |
| **Load-time weaving** | Agent/config đã hiệu lực **trước khi** class mục tiêu được định nghĩa và classloader nằm trong scope | Log weaving cho class đích và phép gọi sau nạp; không giả định một class cũ tự được dệt lại |
| **Proxy/interceptor** | Registry/composition root tạo chain và caller giữ đúng **proxy reference** | Counter trong interceptor cho thấy lời gọi đi qua chain; kiểm tra self-call/direct target riêng |
| **Decorator thủ công** | Wrapper được lắp đúng vị trí cung cấp object cho caller | Test đi qua wrapper, đối chứng với đường vào dùng core trực tiếp |

Ví dụ: khi constructor aspect được gọi nhưng interceptor counter vẫn bằng 0, chỉ có **bằng chứng class/instance đã được tạo**, chưa thể khẳng định matcher đúng hay đường gọi đã được dệt/chặn. Nếu weave log có match nhưng runtime chạy JAR nguyên bản, lỗi nằm ở **artifact triển khai**, không phải nhất thiết ở pointcut.

Với mỗi cơ chế, kết luận cuối cùng cần hai tầng riêng: **đã lắp/cấu hình/nạp đúng** và **advice thực sự chạy tại join point dự kiến mà không thay đổi sai return, exception hoặc side effect**. Không thay pointcut theo cảm tính khi tầng trước chưa có bằng chứng.

## 13. Bất đồng bộ: aspect có nạp nhưng điểm quan sát chưa đúng

Một lời gọi có thể trả CompletableFuture đang pending hoặc đăng ký callback rồi trả về ngay. Around/after-returning trên lời gọi này có thể chạy hoàn toàn chính xác, nhưng không xuất hiện log “tác vụ hoàn thành” vì tác vụ chưa hoàn tất.

Ví dụ timeline:

~~~text
t0: caller -> advice -> submitTask()
t1: submitTask trả future pending; advice after-returning chạy
t2: worker thực thi, có thể trên luồng khác
t3: future complete success/error/cancel
~~~

Nếu mục tiêu là xác nhận **aspect ở invocation** được nạp và chạy, marker ở t0/t1 là thích hợp. Nếu mục tiêu là quan sát **completion** ở t3, cần hook đúng mô hình bất đồng bộ; việc after-returning không chờ completion **không phải lỗi activation**.

Những vấn đề khác:

- Callback có thể chạy ngay khi đăng ký hoặc muộn sau đó; đừng mặc định luôn trên thread khác.
- ThreadLocal/correlation context không tự theo executor; diagnostic logger có thể thiếu trace ID dù advice chạy.
- Lỗi trong completion callback có thể ảnh hưởng dependent stage; marker test không nên đổi outcome nghiệp vụ.
- Một task bị hủy trước khi chạy có thể không tạo execution advice/marker, dù submission advice đã chạy.
- Publisher có thể chỉ được tạo chứ chưa được subscribe; không tự subscribe từ advice để “kiểm thử” vì có thể tạo side effect mới.

Vì thế, trước khi kết luận aspect chưa được nạp, hãy viết rõ **join point nào** cần xuất hiện và **sự kiện nào** phải làm nó phát sinh.

## 14. Nhiều đường vào và độ bao phủ

Ngay cả sau khi test một lời gọi có marker đúng, hệ thống vẫn có thể bỏ sót:

- HTTP endpoint phụ hoặc thao tác quản trị.
- CLI/batch/scheduler.
- Message consumer và retry worker.
- Callback, event listener hoặc executor task.
- Direct service call, self-invocation hoặc reflection.
- Method overload hoặc override mới.
- Một class loader/module khác.
- Một instance được tạo ngoài factory quản lý.
- Một phiên bản artifact mới chưa có cấu hình weaving tương ứng.

Nếu concern là logging, thiếu một đường có thể làm mất thông tin vận hành. Nếu concern là authorization, thiếu một đường có thể trở thành lỗ hổng. Do đó phải kiểm thử **cùng chính sách tại những đường vào quan trọng**.

Một test hữu ích không chỉ ghi “adviceCalls > 0”. Nó xác minh **đúng số lần** và **đúng đối tượng**: một lượt gọi qua proxy có thể làm adviceCalls tăng 1; lời gọi nội bộ có thể không tăng; một phương thức bị gọi lại do retry có thể tăng nhiều lần theo chính sách.

## 15. Diagnostic marker nên được thiết kế thế nào?

Một marker chẩn đoán tốt phải:

1. Được phát **trong chính advice** đang cần xác minh.
2. Có định danh join point/operation rõ ràng.
3. Phân biệt phase (before, after-returning, after-throwing, around).
4. Có correlation ID hoặc invocation ID khi concurrent execution quan trọng.
5. Được bật có kiểm soát, tránh gây nhiều log khi tải lớn.
6. Không tiết lộ tham số nhạy cảm, token, mật khẩu hay nội dung dữ liệu.
7. Không tự throw hoặc block làm thay đổi kết quả nghiệp vụ ngoài chính sách.
8. Có cách kiểm thử độc lập ngoài việc nhìn stdout.

Ví dụ fragment có tính minh họa:

~~~java
// Fragment trong advice, counter được cung cấp qua test fixture.
long n = adviceCounter.incrementAndGet();
debugProbe.record("method-entry", n);
~~~

Đây không phải chương trình độc lập: adviceCounter và debugProbe là thành phần của test. Trong production có thể cần counters riêng cho nhiều luồng và sampling; tránh thay đổi hiệu năng quá mức hoặc che lỗi business bằng lỗi của reporter.

Nếu logger mất dữ liệu, counter trong test chạy ổn định hơn. Nếu counter cũng không tăng, tiếp tục truy vết registration/instrumentation/pointcut/invocation.

## 16. Precedence và short-circuit: khởi tạo đúng vẫn có thể không chạy advice mong đợi

Giả sử có chuỗi:

~~~text
Around A (ngoài cùng)
  -> Before B
    -> Around C
      -> Target
~~~

Nếu A không gọi phần tiếp tục, B, C và Target có thể không được chạy. B không phát marker không nhất thiết là B chưa đăng ký. Nó có thể nằm phía trong nhánh đã bị short-circuit.

Một failure khác là A ném exception trước khi proceed, khiến chain dừng. Nếu thứ tự advice không được thiết kế rõ, một security check có thể chạy sau một action không an toàn hoặc telemetry ghi nhận sai.

Khi nhiều aspect cùng match:

- Định nghĩa ai phải chạy trước/ngoài/ trong.
- Ghi marker theo phase thay vì một log chung.
- Kiểm tra trường hợp target thành công và target ném lỗi.
- Kiểm tra around có gọi continuation chính xác số lần theo hợp đồng không.
- Thử nhánh fallback/deny/cached result có chủ đích.
- Kiểm tra retry không nhân đôi side effect ngoài policy.

**Aspect đã nạp** chỉ là điều kiện cần; **thứ tự và điều kiện control flow** còn quyết định advice cụ thể có chạy hay không.

## 17. Quan sát lỗi và giá trị trả về để tránh false positive

Một bài test “có marker” có thể pass nhưng aspect vẫn sai nếu:

- Advice gọi target hai lần.
- Advice trả giá trị thay thế không đúng.
- Advice nuốt exception nghiệp vụ.
- After/finally logger ném exception làm che lỗi target.
- Advice thực thi trên một thread khác không được phép, làm đổi context.
- Lời gọi bị chặn dù chính sách phải cho phép.
- Advice chặn cả method không nằm trong phạm vi dự định vì pointcut quá rộng.

Bởi vậy, test nên assert:

| Trường hợp | Bằng chứng cần có |
| --- | --- |
| Target success | Caller nhận đúng return value và target chạy đúng số lần |
| Target throw | Caller nhận loại/cause/đối tượng exception đúng hợp đồng |
| Logging failure | Không che business outcome nếu policy best effort |
| Denied access | Không gọi target, không có side effect nếu policy yêu cầu |
| Async completion | Observer phù hợp thấy đúng terminal event |
| Direct/self-call | Coverage được định nghĩa và kiểm chứng |
| Multiple aspects | Thứ tự và số lần gọi đúng policy |

Nếu aspect là quan sát thuần túy, test phải chứng minh **không thay đổi outcome nghiệp vụ**. Nếu aspect cố ý điều khiển luồng như access control hoặc caching, outcome mới phải được mô tả thành hợp đồng cụ thể.

## 18. Khi nào dùng debug logs, weave report, test spy và bytecode inspection?

| Kỹ thuật | Điểm mạnh | Giới hạn |
| --- | --- | --- |
| Cấu hình hiệu lực / registration listing | Xác nhận phần mềm có ý định bật aspect | Không chứng minh điểm được can thiệp |
| Build/weaver diagnostics | Cho biết những điểm được dệt hoặc lý do không dệt | Có thể không phản ánh artifact được chạy |
| Runtime class/proxy inspection | Xác minh caller đang giữ proxy hoặc bản class cụ thể | Không chứng minh pointcut động sẽ match |
| Marker/counter trực tiếp trong advice | Bằng chứng advice đã chạy ở lượt test | Chỉ bao phủ lượt gọi đã thử |
| Integration test từng entry point | Chứng minh đường vào thực tế | Có thể thiếu đường chưa được liệt kê |
| Bytecode inspection | Xem dấu hiệu instrumentation ở class/jar | Không thay kiểm thử behavior runtime |
| Telemetry/trace production | Quan sát nhiều tình huống thực | Sampling, filter, drop hoặc thiếu context |
| Negative control | Giúp phân biệt có instrumentation với chạy bình thường | Cần chọn nhánh điều khiển hợp lệ để không đổi dữ liệu |

Không dùng một công cụ để giải đáp câu hỏi mà nó không cung cấp bằng chứng. Ví dụ **class loaded** không chứng minh **advice executed**, và **advice executed** trên một test không chứng minh **mọi caller đều covered**.

## 19. Quy trình chẩn đoán thực tế theo mức chi phí

### Pha A — Xác nhận mục tiêu

Viết một câu kiểm chứng rõ ràng: “Advice ghi sự kiện vào của operation X phải chạy đúng một lần khi method Y được gọi qua đường Z với context hợp lệ”. Nếu không có mục tiêu, các log chung rất dễ làm nhóm nhầm triệu chứng.

### Pha B — Kiểm tra cấu hình có hiệu lực

Xác định đúng artifact, tiến trình, phiên bản, class loader và profile. Tìm evidence rằng aspect được đăng ký/kích hoạt. Không sửa pointcut trước khi đã chứng minh runtime có dùng cấu hình cần thiết.

### Pha C — Kiểm tra instrument target

Với weaving, xem diagnostics/output binary; với proxy, xem object/handler chain; với decorator, xem wiring của caller. Tách “đối tượng tồn tại” với “đúng đường thực thi đi qua nó”.

### Pha D — Kiểm tra selection

Xác minh signature, annotation, loại join point, điều kiện args/cflow, exclusion và version của target. Thử một pointcut rất hẹp trên method test an toàn thay vì nới rộng toàn hệ thống.

### Pha E — Kích hoạt thử nghiệm có kiểm soát

Dùng marker test-only đặt trong advice, gọi một lượt deterministic và assert số lần advice/target cũng như return/exception. Có negative control khi khả thi.

### Pha F — Mở rộng coverage và phục hồi

Kiểm thử direct call, self-invocation, callback, worker, retry và startup order. Sau khi tìm được lỗi, sửa đúng tầng (packaging, registration, instrumentation, selection hay control flow), rồi chạy lại regression tests.

Đây là quy trình **đi từ cấu hình đến evidence**, thay vì sửa ngẫu nhiên nhiều thành phần và khó xác định nguyên nhân.

## 20. Những lỗi chẩn đoán phổ biến

**“Có file aspect trong dự án nghĩa là đã nạp.”** Sai. Build/runtime có thể không dùng file đó.

**“Class aspect đã được load nên advice chắc chắn chạy.”** Sai. Chưa chứng minh target được instrument hoặc pointcut match.

**“Có proxy thì mọi method đều đi qua proxy.”** Sai. Caller có thể giữ target trực tiếp, hoặc self-call không đi qua proxy.

**“Không có log nghĩa là advice không chạy.”** Chưa chắc. Logging có thể bị filter hoặc fail.

**“Pointcut đúng trên giấy nên runtime chắc match.”** Sai. Có điều kiện động, overload, type visibility và phạm vi weaving.

**“Advice chạy một lần là cả hệ thống đã covered.”** Sai. Cần test những đường vào quan trọng.

**“Around ngoài cùng không proceed cũng là lỗi cấu hình của advice bên trong.”** Sai. Đây là thay đổi control flow có thể được thiết kế; cần xem precedence/policy.

**“After returning chạy nghĩa là async task thành công.”** Sai. Chỉ method đã trả về handle bình thường.

**“Dùng constructor log của aspect làm kiểm thử duy nhất.”** Sai. Nó không chứng minh activation tại join point.

**“Kiểm tra class file ở build directory là đủ.”** Sai. Process có thể đang chạy artifact khác.

## 21. Bài tập tự đánh giá

**Câu 1.** Một lớp aspect có trong source, nhưng khi chạy không có log. Bước đầu tiên nên kiểm tra gì?

**Đáp án:** Xác định cấu hình/build/artifact có hiệu lực và cơ chế AOP đã đăng ký/nhận diện aspect hay chưa. Không đổi pointcut ngay khi chưa biết aspect có tham gia runtime hay không.

**Câu 2.** Runtime đã khởi tạo aspect instance. Điều đó chứng minh method nhạy cảm được bảo vệ chưa?

**Đáp án:** Chưa. Cần bằng chứng join point được instrument, pointcut match và caller đi qua ranh giới thực thi đúng.

**Câu 3.** Một Java dynamic proxy tồn tại, nhưng caller gọi trực tiếp target. Advice có chạy không?

**Đáp án:** Không qua proxy đó. Phải kiểm tra object reference caller dùng hoặc một điểm enforcement khác. Trong cơ chế weaving vào method execution, kết quả có thể khác.

**Câu 4.** Trong ví dụ Java, vì sao ADVICE_CALLS là 2 nhưng TARGET_RUNS là 3?

**Đáp án:** Hai external call đi qua proxy (run outer và internal). Target.internal tự gọi run inner, rồi main gọi trực tiếp target.run direct, nên ba lần run nhưng chỉ hai lượt interceptor.

**Câu 5.** Lời ghi log ở advice biến mất. Làm sao phân biệt advice không chạy với logger bị filter?

**Đáp án:** Dùng counter/spy test-only nằm trong chính advice và assert sau một lượt gọi. Đồng thời quan sát trạng thái logger theo kênh chẩn đoán phù hợp.

**Câu 6.** Tại sao một advice khác có thể khiến advice đã nạp không chạy?

**Đáp án:** Around advice bên ngoài có thể short-circuit hoặc throw trước khi tiếp tục chain. Precedence và nhánh control flow quyết định điểm nằm trong có được thực thi hay không.

**Câu 7.** After returning của method trả CompletableFuture chạy rồi; có chứng minh task đã hoàn tất không?

**Đáp án:** Không. Đây là invocation return. Cần hook completion để chứng minh trạng thái cuối của future.

**Câu 8.** Một integration test thấy advice chạy từ HTTP nhưng CLI không chạy. Nên xem gì?

**Đáp án:** Lối wiring/target của CLI, phạm vi instrumentation/pointcut, class loader và guard bắt buộc. Không được suy rằng một test HTTP đủ bảo vệ mọi entry point.

**Câu 9.** Một report weaving ghi method X đã được dệt, nhưng marker test không hiện. Có mâu thuẫn không?

**Đáp án:** Không nhất thiết. Có thể runtime dùng artifact khác, pointcut động không thỏa, caller không đi vào đúng path hoặc marker/logger bị vô hiệu. Cần kiểm tra evidence từng tầng.

**Câu 10.** Sau khi xác nhận advice đã chạy, còn phải kiểm tra gì?

**Đáp án:** Số lần gọi target, giá trị/exception, side effect, thứ tự nhiều advice, coverage đường vào và tác động khi instrumentation lỗi.

## 22. Checklist xác nhận aspect đã cấu hình và nạp

- [ ] Đã xác nhận đúng tiến trình, artifact và phiên bản đang chạy.
- [ ] Đã đọc **cấu hình có hiệu lực**, không chỉ file mẫu.
- [ ] Đã xác minh registration/discovery của aspect hoặc interceptor.
- [ ] Đã biết cơ chế áp dụng là weaving, proxy, decorator hay dạng khác.
- [ ] Đã kiểm tra target/call site được instrument đúng.
- [ ] Đã xác nhận pointcut match cả điều kiện tĩnh và động liên quan.
- [ ] Đã có test gọi đúng object và join point cần quan sát.
- [ ] Đã ghi nhận marker/counter **bên trong advice** và so khớp số lần chạy.
- [ ] Đã có negative control phù hợp để phát hiện bypass.
- [ ] Đã kiểm tra direct call, self-call, overload và đường async khi có.
- [ ] Đã kiểm tra error/return/side effect không bị thay đổi ngoài chính sách.
- [ ] Đã kiểm tra precedence/short-circuit nếu có nhiều aspect.
- [ ] Đã phân biệt log bị mất với advice không kích hoạt.
- [ ] Đã xác nhận coverage trên các đường vào quan trọng, không chỉ một luồng.
- [ ] Đã lưu evidence có thể lặp lại trong integration test để phòng regression.

## Kết luận

“Aspect đã được cấu hình và nạp” nên được coi là **một mệnh đề cần có bằng chứng theo nhiều tầng**: cấu hình có hiệu lực, cơ chế đăng ký/nhận diện, instrumentation tại join point, pointcut phù hợp và advice thực sự kích hoạt trong đường chạy mục tiêu. AOP không tự bảo đảm mọi lời gọi đều có advice chỉ vì một module aspect tồn tại. Cách chẩn đoán đáng tin là đi từng tầng từ build/runtime đến test activation, đồng thời kiểm tra đúng return, exception, side effect và những đường vào khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-002">Kiểm tra mã hoặc mô-đun có nằm trong phạm vi dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra mã hoặc mô-đun có nằm trong phạm vi dệt (weaving scope)

## 1. Mục tiêu và tình huống thực tế

Trong **Aspect-Oriented Programming (AOP)**, có thể viết đúng pointcut nhưng advice vẫn không chạy. Ví dụ một aspect chọn `execution(void demo..*.run())`, còn ứng dụng có hai method `demo.core.Core.run()` và `demo.extra.Extra.run()`. Pointcut match chữ ký của **cả hai**; tuy nhiên nếu trình dệt (weaver) chỉ xử lý bytecode `Core.class`, lời gọi vào `Extra.run()` sẽ không có advice.

Đây là lỗi thường gặp sau khi:
- Chuyển một class sang package hoặc mô-đun build khác.
- Tách source thành JAR hoặc dependency được compile riêng.
- Đổi cấu hình compile-time/binary/load-time weaving.
- Đóng gói ứng dụng tạo ra bản class plain và woven trùng tên.
- Đổi classloader hoặc sử dụng module path.
- Chỉ cấu hình aspect ở một test/build profile mà không đưa vào bản chạy thực tế.

Sau bài này, bạn biết cách phân biệt **pointcut match** với **bytecode được weave**, kiểm tra các input của compiler/weaver, cách AspectJ tìm aspect, cách runtime chọn class, và cách chứng minh một join point đã thực sự được instrument. Bài có ví dụ Java 17 chạy được cho ba trường hợp: chỉ weave Core, weave thêm Extra, và dùng load-time weaving giới hạn trong package Core.

Phạm vi là AOP cơ bản và nâng cao nói chung, lấy AspectJ làm ví dụ về bytecode weaving; không giả định mọi cơ chế AOP có cùng tập join point hay tính năng.

## 2. Bốn câu hỏi phải trả lời trước khi sửa pointcut

Một đường đi chẩn đoán:

```text
Aspect có được phát hiện và bật?
   |
Target class/JAR có nằm trong weaving input/scope?
   |
Bytecode đã được biến đổi và được JVM load đúng bản?
   |
Join point thực thi có thỏa pointcut và điều kiện runtime?
   |
Advice chạy
```

Mỗi câu hỏi kiểm tra một thành phần độc lập:

| Lớp | Chức năng | Bằng chứng cần có |
|---|---|---|
| **Aspect discovery** | Compiler/weaver tìm thấy aspect và khai báo | Compiled aspect, source input, aspect path, agent configuration |
| **Weaving scope** | Xác định loại bytecode nào có thể được biến đổi | Danh sách source, `-inpath`, LTW include/exclude |
| **Class loading** | Chương trình dùng đúng class đã biến đổi | Artifact chạy thật, classpath/module path, classloader, class loading log |
| **Pointcut / runtime matching** | Join point và điều kiện tại lời gọi thỏa quy tắc | Weave-info và một lần gọi thử thực sự kích hoạt advice |

**Classpath không đồng nghĩa với weaving input.** Việc một JAR xuất hiện trong danh sách dependency chỉ chứng minh compiler có thể tìm thấy type để giải quyết reference, không chứng minh mọi class trong JAR được weave. Tương tự, việc ứng dụng chạy có `aspectjrt` không chứng minh có agent LTW.

Nếu bật logging chi tiết mà vẫn không thấy event, không nên lập tức mở selector thành wildcard rộng hơn. Một pointcut `execution(* *(..))` quá rộng có thể instrument helper nóng, in dữ liệu nhạy cảm hoặc áp nhầm transaction/retry.

## 3. Những cách weave được dùng trong thực tế

### Compile-time weaving (CTW)

Compiler AspectJ `ajc` nhận source Java và aspect source, tạo bytecode có advice tại join point được chọn:

```text
Core.java + ScopeTrace.aj --ajc--> Core.class đã weave
Extra.java --javac--> Extra.class chưa weave
```

Nếu `Extra.java` không được `ajc` xử lý, một `execution` advice trên thân `Extra.run()` không tự xuất hiện chỉ vì pointcut khớp cú pháp.

### Post-compile/binary weaving

Một class/JAR được biên dịch bằng Java compiler, sau đó truyền cho AspectJ:

```text
Extra.java --javac--> out-extra/Extra.class
out-extra --ajc -inpath + -aspectpath--> out-extra-woven/Extra.class
```

Cần phân biệt `-inpath` (**đối tượng cần weave**) và `-aspectpath` (**nơi chứa compiled aspect**). `-classpath` cung cấp những dependency cần để resolve type.

### Load-time weaving (LTW)

Weaver can thiệp khi JVM nạp class. Nó cần agent/cơ chế load phù hợp, compiled aspects có thể nhìn thấy, cấu hình include/exclude, và class chưa bị nạp trước khi agent có cơ hội biến đổi.

```text
plain class --classloader + weaving agent + config--> loaded transformed class
```

Với AspectJ, `aspectjweaver` và cấu hình `META-INF/aop.xml` thường được dùng để khởi tạo LTW. **`aspectjrt` đơn lẻ không bật LTW.** Classloader/module boundaries có thể khiến cấu hình hoạt động ở một thành phần nhưng không ở thành phần khác.

## 4. Kiểm tra phạm vi theo đúng loại join point

Hai dạng điểm chặn cần phân biệt:

```text
Caller method -- call join point --> Target.run()
                                      |
                                      +-- execution join point ở thân Target.run()
```

- `execution(void demo.core.Core.run())` chọn việc thực thi method. Để advice chạy, **Core bytecode chứa execution** phải được weave hoặc biến đổi khi load.
- `call(void demo.core.Core.run())` chọn vị trí thực hiện lời gọi trong **caller**. Cần weave bytecode nơi call site tồn tại, không nhất thiết chỉ weave Core.
- Một lời gọi `this.inner()` trong cùng object vẫn đi vào execution đã weave, nhưng nhiều cơ chế **proxy interception** chỉ chặn lời gọi đi qua proxy, vì vậy tự gọi nội bộ có thể bypass proxy.

Điều đó giải thích vì sao “mô-đun nào phải weave?” không có đáp án chung nếu chưa biết đang cần `call`, `execution` hay proxy invocation.

## 5. Ví dụ thực nghiệm: cấu trúc nguồn

Bốn file:

```text
src/
├── demo/
│   ├── App.java
│   ├── core/Core.java
│   └── extra/Extra.java
└── ScopeTrace.aj
```

**src/demo/core/Core.java:**

```java
package demo.core;
public final class Core {
    public void run() {
        System.out.println("core-body");
    }
}
```

**src/demo/extra/Extra.java:**

```java
package demo.extra;
public final class Extra {
    public void run() {
        System.out.println("extra-body");
    }
}
```

**src/demo/App.java:**

```java
package demo;
import demo.core.Core;
import demo.extra.Extra;
public final class App {
    public static void main(String[] args) {
        new Core().run();
        new Extra().run();
    }
}
```

**src/ScopeTrace.aj:**

```java
public aspect ScopeTrace {
    pointcut selected(): execution(void demo..*.run());

    before(): selected() {
        System.out.println("weave:" +
                thisJoinPointStaticPart.getSignature()
                        .getDeclaringType().getSimpleName());
    }
}
```

Pointcut `execution(void demo..*.run())` đủ rộng để match cả `Core.run()` và `Extra.run()`. Nó không đổi giữa các lượt test. `ScopeTrace.aj` là cú pháp AspectJ code-style và phải được biên dịch bằng compiler phù hợp như `ajc`, không phải Java source thuần cho `javac`.

Khi advice chạy, nó in `weave:Core` hoặc `weave:Extra` trước dòng body. Nếu không có advice, body vẫn in `core-body` hoặc `extra-body`. Vì vậy output tách bạch “method có chạy?” và “advice có chạy?”.

## 6. Chuẩn bị Java 17 và AspectJ

Các ví dụ được thử với **AspectJ 1.9.24** và `ajc -17`. Cần JDK hỗ trợ Java 17, Maven hoặc cách cung cấp các JAR tương ứng.

```bash
mvn -B dependency:get -Dartifact=org.aspectj:aspectjtools:1.9.24 -Dtransitive=false
mvn -B dependency:get -Dartifact=org.aspectj:aspectjrt:1.9.24 -Dtransitive=false
mvn -B dependency:get -Dartifact=org.aspectj:aspectjweaver:1.9.24 -Dtransitive=false
```

Các lệnh dưới đây dùng **Windows PowerShell**, chạy tại thư mục chứa `src`. JAR được tham chiếu theo Maven local repository mặc định; nếu cấu hình repository khác, cần sửa đường dẫn tương ứng.

```powershell
$repo = Join-Path $HOME ".m2/repository/org/aspectj"
$tools = Join-Path $repo "aspectjtools/1.9.24/aspectjtools-1.9.24.jar"
$rt = Join-Path $repo "aspectjrt/1.9.24/aspectjrt-1.9.24.jar"
$weaver = Join-Path $repo "aspectjweaver/1.9.24/aspectjweaver-1.9.24.jar"
```

Với Linux/macOS, nhiều entry classpath được ngăn bằng dấu hai chấm `:` thay cho dấu chấm phẩy `;`.

## 7. Case A: chỉ mô-đun Core được weave

Biên dịch Extra bằng `javac`, còn `Core.java` và `ScopeTrace.aj` bằng `ajc`. Sau đó biên dịch `App` để gọi cả hai:

```powershell
New-Item -ItemType Directory -Force out-extra,out-core-woven,out-app | Out-Null

javac --release 17 -d out-extra src/demo/extra/Extra.java

java -cp $tools org.aspectj.tools.ajc.Main -17 -showWeaveInfo -classpath $rt -d out-core-woven src/demo/core/Core.java src/ScopeTrace.aj

javac --release 17 -classpath "out-extra;out-core-woven" -d out-app src/demo/App.java

java -cp "out-app;out-core-woven;out-extra;$rt" demo.App
```

**Output nghiệp vụ đã kiểm chứng:**

```text
weave:Core
core-body
extra-body
```

Giải thích:

1. `Core.run()` được weave, nên xuất hiện `weave:Core`.
2. `Extra.run()` có chạy, chứng minh bởi `extra-body`.
3. Không xuất hiện `weave:Extra`, dù pointcut mô tả chữ ký của method đó.
4. Vì Extra được biên dịch bằng `javac` riêng và không đưa vào `ajc` như target input, execution bytecode của nó vẫn là bản plain.

Khi có `-showWeaveInfo`, `ajc` báo execution join point `demo.core.Core.run()` được advice của `ScopeTrace` áp dụng. Thông báo dạng diagnostics có thể chứa file path/line number khác giữa các máy và không được tính vào stdout nghiệp vụ cố định ở trên.

Đây là **bằng chứng trực tiếp rằng pointcut scope và weaving scope khác nhau**.

## 8. Case B: mở rộng weaving input sang Extra

Vẫn giữ nguyên `ScopeTrace.aj` và bytecode woven của Core. Lần này dùng `-inpath` để weave class đã biên dịch của Extra:

```powershell
New-Item -ItemType Directory -Force out-extra-woven | Out-Null

java -cp $tools org.aspectj.tools.ajc.Main -17 -showWeaveInfo -classpath "out-extra;out-core-woven;$rt" -inpath out-extra -aspectpath out-core-woven -d out-extra-woven

java -cp "out-app;out-core-woven;out-extra-woven;$rt" demo.App
```

**Output đã kiểm chứng:**

```text
weave:Core
core-body
weave:Extra
extra-body
```

Kết quả thay đổi vì:

- `-inpath out-extra` xác định những class bytecode cần weave.
- `-aspectpath out-core-woven` giúp công cụ tìm compiled aspect `ScopeTrace`.
- `-d out-extra-woven` tạo một output mới đã biến đổi.
- Lệnh chạy dùng `out-extra-woven` **thay cho** `out-extra`, nên JVM chọn đúng bản class đã weave.

Ở Case B, `-showWeaveInfo` còn có thông báo join point của `demo.extra.Extra.run()` được advice áp dụng. Kết hợp **build diagnostics** và **runtime event** là cách xác minh mạnh hơn chỉ nhìn rằng compiler trả exit code 0.

Nếu lệnh binary weaving có lỗi, đừng tiếp tục chạy bản class cũ rồi coi đó là kết quả mới. Trong build tự động, nên dừng khi `ajc` trả mã lỗi khác 0.

## 9. Phân biệt JAR/classpath với artifact đã weave

Một nguyên nhân phổ biến khác: `out-extra-woven/Extra.class` có tồn tại nhưng runtime lại nạp bản plain ở `out-extra` vì có hai bản trùng binary name trên classpath.

Ví dụ:

```text
Mong muốn:
  out-app;out-core-woven;out-extra-woven;aspectjrt

Nguy cơ:
  out-app;out-core-woven;out-extra;out-extra-woven;aspectjrt
```

Với classloader thông thường, tìm kiếm class có thứ tự và có thể chọn bản plain trước. Một custom classloader còn có cơ chế ưu tiên parent/child riêng. **Không khẳng định JVM sẽ hợp nhất hai bản class.**

Những phép kiểm tra có giá trị:

- In đúng command line/startup classpath hoặc module path đang dùng.
- Xem nội dung JAR cuối cùng, xác nhận không có class trùng tên do shading/assembly.
- Quan sát class loading bằng tùy chọn JVM phù hợp, ví dụ `-Xlog:class+load=info` trên JVM hỗ trợ Unified Logging.
- Kiểm tra `Class.getProtectionDomain().getCodeSource()` trong môi trường debug nếu nguồn class được khai báo; đừng dựa tuyệt đối vào đó khi loader cung cấp nguồn `null`.
- Phân biệt một type cùng binary name được nạp bởi **hai classloader** thành hai định nghĩa runtime khác nhau.

**Thông điệp then chốt:** build tạo bản woven không có nghĩa deployment chọn bản woven.

## 10. Case C: load-time weaving theo include scope

Để quan sát cùng hiện tượng ở load time, tạo `Core.class` **plain** rồi dùng AspectJ weaver agent chỉ include package `demo.core`. `ScopeTrace.class` được lấy từ output đã biên dịch ở Case A.

**Tạo Core plain:**

```powershell
New-Item -ItemType Directory -Force out-core-plain,out-config/META-INF | Out-Null
javac --release 17 -d out-core-plain src/demo/core/Core.java
```

Lưu tệp **out-config/META-INF/aop.xml**:

```xml
<aspectj>
    <aspects>
        <aspect name="ScopeTrace"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="demo.core.*"/>
    </weaver>
</aspectj>
```

Ở đây `<aspects>` khai báo aspect; `<include within="demo.core.*"/>` chỉ cho phép những type thuộc nhóm `demo.core` nằm trong LTW scope. Điều kiện này khác pointcut `execution(void demo..*.run())` ở source aspect.

**Chạy với agent:**

```powershell
java "-javaagent:$weaver" -cp "out-config;out-app;out-core-plain;out-extra;out-core-woven;$rt" demo.App
```

Vì `out-core-plain` được liệt kê trước `out-core-woven`, class Core được JVM tìm từ bản plain; agent biến đổi nó lúc load. Thư mục `out-core-woven` còn cung cấp **compiled aspect**. Trong dự án thật nên đóng gói aspect riêng để không vô tình có các target class trùng tên.

**Stdout nghiệp vụ đã kiểm chứng:**

```text
weave:Core
core-body
extra-body
```

Ở thí nghiệm LTW trên JVM mới, có thể có thêm cảnh báo/diagnostic từ weaver trong stderr. `-showWeaveInfo` cũng báo weave thành công đối với `Core.run()`. Những thông báo đó phụ thuộc phiên bản và môi trường; không phải ba dòng stdout cố định.

Điều được chứng minh: có agent LTW nhưng **Extra vẫn không được weave** vì cấu hình `include` giới hạn target types. Aspect và pointcut được nhận diện đúng không có nghĩa mọi classloader/type đều được instrument.

## 11. Debug load-time weaving theo từng tầng

Khi LTW không chạy, thực hiện theo thứ tự:

1. **Agent**: JVM có thật sự được khởi động với `-javaagent:path/to/aspectjweaver.jar`? JAR runtime khác với JAR weaver.
2. **Config**: `META-INF/aop.xml` có nằm trên resource path mà loader/weaver nhìn thấy không? Artifact fat JAR, shading hoặc packaging có làm mất file?
3. **Aspect**: tên aspect đúng, class đã biên dịch và dependency cần thiết có thể load được không?
4. **Include/exclude**: lớp target có bị bộ lọc `within` loại trừ không? Mẫu include của weaver không phải là pointcut của advice.
5. **Classloader**: JVM dùng loader nào để định nghĩa class? Agent có hoạt động cho loader đó không?
6. **Thời điểm**: target class đã load trước khi cấu hình/weaver được áp dụng chưa?
7. **Class target**: loader có thật sự nạp class cần instrument không, hay một bản trùng tên từ artifact khác?
8. **Join point**: sau khi thấy class thuộc scope, pointcut có match method/annotation/args và điều kiện động không?
9. **Runtime path**: phương thức có được gọi thật không? Có overload, callback, interface implementation hoặc nhánh điều kiện khác?

Chỉ khi có bằng chứng weaving input đúng và class đã load phù hợp mà advice vẫn không chạy, mới nên sửa pointcut. Đừng thay đổi đồng thời agent, pointcut, packaging và API vì sẽ mất khả năng khoanh vùng nguyên nhân.

## 12. Mô-đun build khác mô-đun Java (JPMS)

Từ “mô-đun” có thể chỉ:

- **Mô-đun của build/kiến trúc**: source set, subproject, JAR, package, library.
- **JPMS module**: một module được định nghĩa bằng `module-info.java` với module path, exports/opens và dependency graph.

Với mô-đun build, hãy tìm class/JAR đầu ra và xác định có nằm trong source input, `-inpath` hoặc phạm vi LTW hay không.

Với JPMS, việc weave còn có thể chịu ảnh hưởng module readability, runtime encapsulation, classloader và JVM/AspectJ compatibility. Không nên thêm hàng loạt tùy chọn `--add-opens` một cách mù quáng: cần chỉ ra package nào thực sự cần được mở và cơ chế nào yêu cầu điều đó. Một câu lệnh hoạt động trên classpath không phải bằng chứng nó sẽ hoạt động giống hệt khi chuyển toàn bộ sang module path.

Đừng suy ra “module X phụ thuộc vào thư viện aspect” đồng nghĩa “mọi class trong module X được weave”. Đây là hai quan hệ hoàn toàn khác nhau.

## 13. Loại join point quyết định nơi cần weave

Ví dụ `App.main` gọi `Core.run`:

```text
App.class: bytecode chứa lệnh gọi -> call join point
Core.class: bytecode chứa method body -> execution join point
```

Đối với `execution`, mục tiêu weave là code **chứa thân method**. Đối với `call`, mục tiêu weave là code **của caller** chứa vị trí gọi method. Một lỗi cấu hình hay gặp là chỉ weave target nhưng muốn đo `call` tại một module caller khác; hoặc chỉ weave caller nhưng muốn áp `execution` vào method target.

Với proxy interception, lớp proxy và đường gọi qua proxy là mấu chốt. Self-invocation trong implementation có thể không đi qua proxy. Với AspectJ execution weaving, một self-call vẫn đi vào thân method đã được biến đổi. Không đánh đồng những cơ chế này khi viết kiểm thử phạm vi.

## 14. Đã weave nhưng không có event: các nguyên nhân không phải scope

Ngay cả khi weave-info xác nhận một shadow/join point đã được instrument, một testcase cụ thể vẫn có thể không thấy advice:

- Method chưa được gọi.
- Gọi overload khác hoặc implementation khác với selector.
- `args`, `this`, `target` hay điều kiện runtime của pointcut không thỏa.
- Annotation ở interface nhưng pointcut tìm metadata trên implementation (hoặc ngược lại).
- Compiler sinh generic bridge/synthetic method và matcher chọn signature ngoài dự kiến.
- Advice lọc event, sampling hoặc sink làm mất thông tin.
- Method trả future sớm nhưng side effect diễn ra trong task khác không nằm trong scope.

Nên phân biệt **static match tại vị trí bytecode** với **dynamic match tại một lần chạy**. Dùng fixture đơn giản có `execution` không phụ thuộc điều kiện runtime để xác minh weaving scope, sau đó mới kiểm tra pointcut động và logic ứng dụng.

## 15. Mô-đun bất đồng bộ và callback

Một method nằm trong mô-đun core có thể chỉ gửi việc sang queue/executor, còn side effect thực sự nằm trong mô-đun worker khác:

```text
core.submit() -> returns future
                    |
                    +--> worker.execute() -> actual side effect
```

Nếu chỉ weave execution của `core.submit`, advice quanh nó thường chạy đến thời điểm **trả future handle**. Nó không tự động chặn execution của `worker.execute` nếu class worker không thuộc weaving scope.

Khi cần truy vết/cấp quyền/transaction tại chỗ phát sinh hiệu ứng, hãy:

- Xác định method thật chứa side effect và module/JAR sở hữu nó.
- Đưa đúng bytecode vào scope nếu chính sách AOP yêu cầu.
- Phân biệt event `submitted` và `completed`, quan sát future completion riêng.
- Truyền và khôi phục trace/security context qua executor nếu cần, không giả định `ThreadLocal` tự lan qua thread khác.
- Test cả đường gọi thành công, lỗi và retry trong worker.

Điều này giúp tránh một cảm giác an toàn giả: aspect trên API wrapper vẫn in log nhưng operation thực tế không được giám sát ở tầng xử lý.

## 16. Checklist xác minh trước release

- [ ] Biết đang dùng compile-time, binary hay load-time weaving, hoặc proxy interception.
- [ ] Phân biệt pointcut scope và weaving input scope.
- [ ] Liệt kê chính xác class/JAR/module cần instrument.
- [ ] Biết những source đưa vào `ajc` và những class đưa vào `-inpath`.
- [ ] Compiled aspect có ở đúng `-aspectpath` hoặc LTW config.
- [ ] Có `-showWeaveInfo` hoặc diagnostics phù hợp cho join point mong muốn.
- [ ] Runtime load bản class đã weave, không phải bản plain cùng binary name.
- [ ] Với LTW có `-javaagent`, `aop.xml`, include/exclude và loader thích hợp.
- [ ] Có positive test cho class phải weave và negative test cho class không thuộc phạm vi.
- [ ] Test phủ việc đổi package, tách module/JAR, shade/packaging và build profile.
- [ ] Phân biệt `call` và `execution` khi quyết định weave caller hay target.
- [ ] Kiểm tra overload, override, annotation, bridge method và điều kiện runtime nếu thiếu advice.
- [ ] Kiểm tra async task/consumer/callback nếu side effect chạy ngoài module ban đầu.
- [ ] Không ghi token/secret vào weave diagnostics hoặc issue report.

## 17. Quy trình chẩn đoán ngắn gọn

**Bước 1:** Viết một testcase nhỏ gọi trực tiếp method mong muốn, khẳng định body chạy.

**Bước 2:** Xác định kiểu join point và xem target nằm ở caller, target hay proxy boundary.

**Bước 3:** Kiểm tra lệnh build hoặc agent configuration, chỉ ra danh sách input và scope cụ thể.

**Bước 4:** Bật weave-info, xác định join point có được advice áp dụng ở bytecode không.

**Bước 5:** Xem bản class/JAR thực sự được JVM load, classloader và duplicate classpath entries.

**Bước 6:** So sánh event runtime với expected bằng assertion toàn bộ chuỗi, không chỉ tìm một dòng log bất kỳ.

**Bước 7:** Nếu class đã weave nhưng event thiếu, kiểm tra pointcut static/dynamic, overload, annotation và đường gọi.

**Bước 8:** Đưa test positive/negative vào CI để những lần đổi build hoặc module sau không làm mất advice âm thầm.

## 18. Tổng kết

**Một pointcut đúng về mặt chữ ký chưa chứng minh advice sẽ chạy.** Cần có aspect được nhận diện, target nằm trong phạm vi weaving, bytecode được biến đổi và runtime thực sự load đúng bản class. Khi debug, hãy tách **“không được weave”** khỏi **“pointcut không match”** và **“đường gọi thực tế không đi qua join point”**.

Ba trường hợp Core/Extra chứng minh sự khác biệt bằng cùng một pointcut: ban đầu chỉ Core được weave, sau khi đưa Extra vào `-inpath` thì Extra có advice, còn LTW chỉ include Core nên Extra vẫn plain. Kết hợp **weave-info + runtime behavior + artifact/classloader inspection** giúp kiểm tra chính xác mô-đun nào thật sự nằm trong phạm vi dệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-003">Đọc thông báo điểm đã được dệt và điểm không phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thông báo điểm đã được dệt và điểm không phù hợp trong AOP

## 1. Mục tiêu: đọc thông báo chẩn đoán thay vì đoán pointcut

Trong Aspect-Oriented Programming (AOP), một **pointcut** lựa chọn tập các *join point*, **advice** diễn tả hành vi muốn thực thi tại những điểm đó, còn **weaving** hoặc cơ chế chặn lời gọi khác biến ý định trên thành hành vi lúc chương trình chạy. Một vấn đề phổ biến khi gỡ lỗi là: source code đã chứa aspect, chương trình vẫn chạy, nhưng log/audit/metrics hay kiểm soát truy cập không xuất hiện tại phương thức mong muốn.

Muốn chẩn đoán chính xác, cần phân biệt **bốn lớp bằng chứng**:

1. **Aspect được nhận diện/đăng ký:** trình biên dịch hoặc weaver đã biết sự tồn tại của aspect.
2. **Một class được đưa qua weaver:** class ấy có được xem xét để biến đổi bytecode, nhưng chưa chắc có join point khớp.
3. **Một join point được dệt:** weaver đã áp một advice hoặc thay đổi liên quan vào vị trí bytecode cụ thể.
4. **Advice chạy thật tại runtime:** chương trình thực sự đi qua join point, các điều kiện động được thỏa, và cơ chế xử lý advice hoạt động.

Bốn trạng thái **không đồng nghĩa**. Một dòng `weaving 'MyClass'` có thể chỉ báo rằng weaver đang xử lý class, trong khi một dòng `weaveinfo Join point ...` cho thông tin cụ thể hơn về điểm cắt đã được dệt. Nhưng ngay cả thông báo có dệt ở build time cũng chưa chứng minh lần chạy hiện tại đã vào join point hoặc điều kiện động đã thỏa.

Bài này giải thích cách đọc thông báo **điểm đã dệt**, **điểm/advice không phù hợp**, cùng kỹ thuật chia nhỏ pointcut để tìm nguyên nhân. Ví dụ sử dụng công cụ AspectJ (`ajc`, `-showWeaveInfo`, `-Xlint`) để quan sát, nhưng các bước suy luận—kiểm tra phạm vi, join point, điều kiện, bytecode và hành vi—áp dụng cho AOP nói chung.

### Kết quả học tập

Sau bài này, bạn có thể:

- Phân biệt thông báo compiler/weaver với kết quả thực thi advice.
- Đọc các tín hiệu `info`, `debug`, `weaveinfo`, `warning` và `error` trong đúng ngữ cảnh.
- Giải thích `adviceDidNotMatch` mà không suy diễn quá mức.
- Chẩn đoán pointcut sai loại join point, sai chữ ký, phạm vi weaving thiếu class hoặc đường gọi không qua điểm chặn.
- Phân biệt `call` và `execution` để biết classfile nào phải được dệt.
- Tái hiện có đối chứng bằng chương trình Java 17/AspectJ, log build, stdout và test âm tính.
- Thiết kế quy trình CI đánh giá *bằng chứng dệt* và *bằng chứng runtime* riêng biệt.

## 2. Từ source code tới advice chạy: sơ đồ nguyên nhân

~~~text
Khai báo aspect/pointcut
          |
          v
Compiler/weaver có nhận aspect không?
          |
          v
Classfile chứa join point có trong phạm vi weaving không?
          |
          v
Pointcut có khớp join point ở giai đoạn dệt không?
          |
          v
Advice có được dệt tại join point đó không?
          |
          v
Runtime có thực sự chạy qua join point đó không?
          |
          v
Các điều kiện động có đúng không?
          |
          v
Advice thực thi với kết quả/số lần/thứ tự mong đợi
~~~

Một bài gỡ lỗi hiệu quả không bắt đầu bằng việc thay bừa biểu thức pointcut. Hãy xác định **bước đầu tiên có bằng chứng thiếu**.

Ví dụ:

- Nếu không thấy aspect được đăng ký: xem cấu hình aspect, đường dẫn artifact và cách nạp.
- Nếu aspect có đăng ký nhưng class không được xem xét: xem include/exclude, classloader, build module, `inpath` hoặc agent.
- Nếu class được xem xét nhưng không có thông báo dệt mong đợi: xem loại join point, chữ ký và điều kiện pointcut.
- Nếu thấy dòng dệt nhưng runtime không có event: xem có đi qua đường gọi đó chưa, điều kiện động, thứ tự advice, exception và artifact đang chạy.
- Nếu advice chạy nhưng kết quả sai: xem hợp đồng của around/before/after advice, số lần `proceed`, retry, cleanup và biến đổi lỗi.

Cần luôn tách **bằng chứng compiler** ra khỏi **bằng chứng runtime**. Một pointcut có thể có điều kiện chỉ quyết định tại runtime; chỉ nhìn thông báo build chưa đủ.

## 3. Các nhóm thông báo và độ tin cậy của từng nhóm

| Tín hiệu | Nó chứng minh điều gì? | Nó **không** chứng minh điều gì? |
| --- | --- | --- |
| `info register aspect X` | Weaver nhận/đăng ký aspect X | X đã áp advice vào target cụ thể |
| `debug weaving 'Target'` | Weaver đang xử lý Target | Trong Target có advice match |
| `weaveinfo Join point '...' ...` | Một join point cụ thể nhận thao tác weaving/advice theo thông tin báo | Runtime đã đi qua điểm đó trong một request cụ thể |
| `warning adviceDidNotMatch` | Advice không match join point nào trong thế giới code mà đợt dệt biết/kiểm soát | Advice đó mãi mãi không thể match ở mọi deployment |
| `warning invalidAbsoluteTypeName` | Tên kiểu tuyệt đối trong pointcut không tìm thấy trong thế giới đã phân giải | Chắc chắn là bug nếu kiểu thuộc module chưa đưa vào build |
| `warning typeNotExposedToWeaver` | Có loại code cần cho weaving nhưng chưa được cung cấp cho weaver | Chỉ sửa pointcut sẽ chắc chắn giải quyết |
| `error` | Build hoặc weaving không thành công theo lỗi cụ thể | Artifact cũ trên classpath đã biến mất |

Các dòng ví dụ dùng **mẫu hình rút gọn**. Văn bản, mức log, số dòng, path, classloader và thông tin join point có thể khác giữa các phiên bản AspectJ hoặc phương thức compile/load-time weaving. **Không sao chép một log minh họa như log thực sự vừa chạy.**

### Ba nhầm lẫn cần tránh ngay

**Thấy “register aspect” rồi nghĩ advice đã hoạt động:** sai. Đây mới là bằng chứng đăng ký.

**Thấy “weaving class” rồi nghĩ tất cả method trong class được instrument:** sai. Weaver có thể xem xét class và không dệt advice nào.

**Thấy “weaveinfo” rồi nghĩ mọi lời gọi tới method đều đã được quan sát:** chưa đúng. Runtime phải đi qua bytecode đã weave, pointcut có thể có kiểm tra động, chương trình có thể chạy bản classfile khác và advice ngoài có thể short-circuit.

## 4. Bật thông tin chẩn đoán với công cụ minh họa AspectJ

### 4.1 Những tùy chọn chính

Đối với AspectJ compiler/weaver `ajc`, những tùy chọn hữu ích gồm:

- `-showWeaveInfo`: in thông báo về những join point nhận weaving. Đây là tín hiệu cụ thể nhất để kiểm tra một điểm đã được weave hay chưa.
- `-verbose`: phát thông tin xử lý/đăng ký ở mức rộng hơn.
- `-debug`: hữu ích trong bối cảnh thông báo debug của weaver, đặc biệt khi chẩn đoán load-time weaving; việc một class “được xem xét” không chứng minh advice đã dệt.
- `-Xlint` hoặc `-Xlint:warning`: phát các chẩn đoán về lỗi tiềm ẩn của crosscutting code.
- `-Xlint:error`: nâng mức mặc định của các nhóm lint lên error nếu phù hợp chính sách build.
- `-Xlintfile path/to/policy.properties`: định mức báo cho từng loại cảnh báo khi cần một policy chi tiết.
- `-log path/to/file`: lưu thông báo compiler vào file khi cần lưu bằng chứng build.

Tùy chọn nào hoạt động và cách cung cấp nó phụ thuộc compiler/weaver đang chạy; hãy kiểm tra phiên bản toolchain thực tế. Không dùng `-nowarn` hoặc tắt tất cả lint để “làm xanh build” khi điểm cắt bắt buộc đang mất match.

Một lệnh ví dụ ở môi trường đã cài `ajc`:

~~~bash
ajc -17 -showWeaveInfo -Xlint:warning \
    -classpath aspectjrt.jar \
    -d out WeaveMessagesDemo.java DiagnosticAspects.aj
~~~

Đây là **cú pháp bash nhiều dòng** bằng dấu `\`. Ở shell không hỗ trợ nối lệnh này, viết các tùy chọn trên một dòng. `-17` chỉ ra mức nguồn/bytecode Java 17 khi bản `ajc` hỗ trợ. `aspectjrt.jar` phải là đường dẫn tới runtime tương thích; nếu thư viện đã có trên classpath hệ thống, không cần lặp lại.

### 4.2 Khác biệt khi load-time weaving

Khi dùng **load-time weaving (LTW)**, log chẩn đoán liên quan **classloader cụ thể** và thời điểm class được định nghĩa. Tùy cấu hình, có thể bật các tùy chọn tương ứng trong cấu hình weaver hoặc system property như:

~~~text
aj.weaving.verbose=true
org.aspectj.weaver.showWeaveInfo=true
~~~

Đây là ví dụ **system properties** của AspectJ, không phải Java source hay cấu hình chung của mọi runtime. Trong môi trường thực tế, cần xác nhận weaver/agent đã hoạt động **trước khi** class đích được nạp. Nếu class đã load rồi, việc bật log sau đó không tự dệt lại class ấy theo mọi mô hình.

Một log hiển thị classloader không có nghĩa tất cả classloader trong ứng dụng cũng chịu cùng cấu hình. Mỗi loader có thể có danh sách include/exclude hoặc thời điểm khởi tạo khác nhau.

## 5. Ví dụ AspectJ hoàn chỉnh: một pointcut match, một pointcut không match

Chương trình nhỏ sau tạo ra ba loại bằng chứng:

1. Advice đầu khớp `execution(void Work.process(String))` và được dệt.
2. Advice thứ hai chọn `execution(void Work.ping(int))`, nhưng chương trình chỉ có `Work.ping()` không tham số; **không có join point tương ứng** trong đợt biên dịch.
3. Runtime thật gọi `process("A")` và `ping()`, nên chỉ advice thứ nhất in dòng “advice”.

**Tệp `WeaveMessagesDemo.java`:**

~~~java
public class WeaveMessagesDemo {
    public static void main(String[] args) {
        Work work = new Work();
        work.process("A");
        work.ping();
    }
}

class Work {
    void process(String value) {
        System.out.println("work=" + value);
    }

    void ping() {
        System.out.println("ping");
    }
}
~~~

**Tệp `DiagnosticAspects.aj`:**

~~~java
public aspect DiagnosticAspects {
    before(): execution(void Work.process(String)) {
        System.out.println("advice:process");
    }

    before(): execution(void Work.ping(int)) {
        System.out.println("advice:ping-int");
    }
}
~~~

**Lệnh biên dịch và chạy (Unix-like shell):**

~~~bash
mkdir -p out
ajc -17 -showWeaveInfo -Xlint:warning \
    -classpath aspectjrt.jar \
    -d out WeaveMessagesDemo.java DiagnosticAspects.aj
java -cp "out:aspectjrt.jar" WeaveMessagesDemo
~~~

Với Windows, ký tự phân cách classpath thường là dấu `;`, ví dụ `out;aspectjrt.jar`. Hai tệp cần nằm trong cùng thư mục làm việc hoặc truyền đường dẫn tương ứng.

**Stdout của chương trình khi build/weave thành công:**

~~~text
advice:process
work=A
ping
~~~

**Đọc log biên dịch:** `-showWeaveInfo` dự kiến đưa ra một thông báo dạng `weaveinfo Join point 'method-execution(void Work.process(java.lang.String))' ...` cho advice thứ nhất. Do advice thứ hai không có điểm khớp trong tập source này, lint `adviceDidNotMatch` có thể được phát ra. Cấu trúc đầy đủ của dòng, đường dẫn, thứ tự và mức cảnh báo còn phụ thuộc bản compiler và các tùy chọn lint, vì vậy **không tuyên bố nguyên văn stderr cố định**.

Dòng `advice:ping-int` **không thể xuất hiện** trong lần chạy bình thường được minh họa, vì không có method `Work.ping(int)`. Đây là lỗi chọn chữ ký, không phải bằng chứng mọi class đã bị bỏ ngoài phạm vi weaving.

### 5.1 Đối chứng khi build bằng javac thuần

Để xác minh sự khác nhau giữa bản có weaving và bản không weaving, biên dịch **chỉ Java** vào thư mục độc lập:

~~~bash
mkdir -p plain
javac -d plain WeaveMessagesDemo.java
java -cp plain WeaveMessagesDemo
~~~

**Stdout của bản plain:**

~~~text
work=A
ping
~~~

Không thể dùng `javac` để biên dịch cú pháp `aspect` trong tệp `.aj`. Khi chỉ build Java, classfile `Work` không được dệt bằng AspectJ; sự khác biệt stdout phản ánh chính điều này. Tuy nhiên, nếu chạy trên môi trường còn bật load-time weaving khác, cần tắt cơ chế đó khi thực hiện đối chứng plain.

### 5.2 Phép kiểm thử có mục đích sau khi sửa pointcut

Để khớp method `ping()` thật, sửa dòng chọn từ `Work.ping(int)` thành `Work.ping()`, sau đó build lại bản woven sạch. Lúc ấy advice tương ứng có thể được dệt, và stdout kỳ vọng thêm một dòng trước “ping”:

~~~text
advice:process
work=A
advice:ping-int
ping
~~~

Đây là **dự đoán nếu chỉ sửa pointcut** mà chưa đổi tên chuỗi in; chuỗi `advice:ping-int` vẫn giữ nguyên do literal trong thân advice. Trong code có ý nghĩa hơn, nên đổi nhãn thành `advice:ping` để tránh gây hiểu nhầm. Kết quả trên chỉ dùng để chứng minh **việc khớp chữ ký thay đổi khả năng dệt**, không liên quan giá trị của tên thông báo.

## 6. Đọc `adviceDidNotMatch` đúng nghĩa

`adviceDidNotMatch` là một loại lint điển hình được phát ra khi một advice **không có join point nào mà compiler/weaver xác định được là có thể bị ảnh hưởng** trong phạm vi đang được xử lý.

Nó thường gợi ý:

- Sai tên phương thức hoặc tên kiểu trong pointcut.
- Sai số lượng/kiểu tham số, sai modifier hoặc định dạng pattern.
- Pointcut chọn `call` trong khi phần code có `execution` khác kỳ vọng.
- Phạm vi `within`, `withincode`, annotation hoặc điều kiện ghép bị hạn chế quá mức.
- Class/JAR chứa vị trí cần dệt không nằm trong tập source/inpath được cung cấp.
- Tính năng aspect nằm trong thư viện dùng lại, còn target sẽ được weave **ở một bước hoặc ứng dụng khác**.

Điểm cuối đặc biệt quan trọng: nếu biên dịch một **thư viện aspect** mà chưa cung cấp mã ứng dụng sử dụng thư viện, thông báo “không match” có thể là **hợp lệ trong build thư viện**. Đừng coi một warning duy nhất là bằng chứng chính sách của hệ thống production đang sai, và cũng đừng mặc định bỏ qua mọi warning.

### 6.1 Ba cách hiểu sai cần tránh

**Sai:** “`adviceDidNotMatch` nghĩa là method đích không tồn tại trong mọi chương trình.”  
**Đúng:** Trong phạm vi mà đợt compile/weave hiện có thể thấy, advice không tìm thấy điểm phù hợp; method có thể tồn tại ở module/classloader khác hoặc trong build sau.

**Sai:** “Không có warning `adviceDidNotMatch` nghĩa là mọi lời gọi đều được instrument.”  
**Đúng:** Một advice khớp **ít nhất một** điểm không chứng minh nó khớp **tất cả** operation bắt buộc.

**Sai:** “Không có `weaveinfo` nghĩa là không có advice.”  
**Đúng:** Trước hết xác nhận đã bật `-showWeaveInfo` và đang đọc đúng output/log. Một số cơ chế chỉ xuất thông báo trong phạm vi build/loader của chúng; test runtime mới xác nhận hành vi thực.

### 6.2 Khi nào biến cảnh báo thành lỗi build?

Có thể cân nhắc **fail build** khi advice bắt buộc của một ứng dụng khép kín mất toàn bộ match, hoặc khi `typeNotExposedToWeaver` chỉ ra operation nhạy cảm nằm ngoài phạm vi weaving.

Không nên áp quy tắc “mọi lời cảnh báo adviceDidNotMatch đều fail” cho toàn bộ thư viện aspect dùng lại mà không có ngoại lệ có lý do. Chính sách CI nên có **allowlist có chủ sở hữu**, ghi rõ advice nào cố ý chưa match tại giai đoạn build và ở đâu sẽ được kiểm thử sau.

Nếu dùng `-Xlintfile` để điều chỉnh mức từng warning, không tắt nguyên một lớp cảnh báo một cách im lặng. Với authorization, audit bắt buộc hoặc transaction, cần một **coverage test** xác nhận toàn bộ operation thuộc diện bảo vệ; warning đơn lẻ không đủ thay thế test đó.

## 7. Những cảnh báo hữu ích khác

| Tên lint | Diễn giải thực hành | Hướng kiểm tra |
| --- | --- | --- |
| `invalidAbsoluteTypeName` | Tên kiểu tuyệt đối không tìm thấy hoặc không phân giải được | Kiểm tra package, classpath, import, relocation/rename, binary compat |
| `typeNotExposedToWeaver` | Kiểu có thể cần được dệt nhưng không được đưa vào phạm vi kiểm soát | Kiểm tra `inpath`, input JAR, module và classloader |
| `unmatchedSuperTypeInCall` | Kiểu khai báo trong `call` khác kiểu tĩnh của receiver ở vị trí gọi | Phân biệt compile-time receiver type với runtime target |
| `aspectExcludedByConfiguration` | Aspect bị loại khỏi cấu hình weaving, có thể do include/exclude | Kiểm tra bộ lọc cấu hình và phiên bản artifact |
| `adviceDidNotMatch` | Advice không tìm được join point tiềm năng | Thu nhỏ pointcut, kiểm tra đầu vào build, xác định ý đồ thư viện |

**Mức cảnh báo và tên bản ghi cần được xác thực theo phiên bản weaver.** Chúng là các tín hiệu chẩn đoán, không phải phép chứng minh đầy đủ của bảo mật hoặc tính đúng đắn.

Một lỗi `invalidAbsoluteTypeName` có thể xuất hiện vì package đã đổi sau refactor. Không nên “sửa” bằng cách thay mọi kiểu bằng `*` nếu điều đó làm pointcut trở nên quá rộng, chạm cả code hạ tầng hoặc dữ liệu nhạy cảm.

`unmatchedSuperTypeInCall` là gợi ý đặc biệt có giá trị: trong AspectJ, `call` thường bị ràng buộc bởi **kiểu tĩnh của biểu thức receiver tại call site**, không đơn giản bằng implementation class được gọi tại runtime. Nếu muốn match theo đối tượng runtime, hãy xem xét ràng buộc `target(Type)` theo semantics phù hợp, rồi kiểm chứng trên tập join point thực tế.

## 8. Gỡ lỗi pointcut theo phương pháp tách điều kiện

Một biểu thức pointcut lớn thường có dạng:

~~~text
P = kind && signature && lexicalScope && runtimeContext && exclusions
~~~

Nếu P không match, viết lại toàn bộ biểu thức theo cảm tính sẽ khó biết vấn đề ở đâu. Thay vào đó:

1. **Chọn đúng loại join point:** ví dụ `execution` thay vì `call` khi muốn dệt vào body method.
2. **Giới hạn phạm vi đơn giản:** lấy một lớp/phương thức chắc chắn nằm trong artifact đang weave.
3. **Thêm điều kiện chữ ký:** tên, return type, modifier và tham số; xác nhận method thật qua source/bytecode.
4. **Thêm điều kiện phạm vi lexical:** `within`/`withincode` theo vị trí code.
5. **Thêm điều kiện động:** `this`, `target`, `args`, `cflow` hoặc `if` khi có lý do.
6. **Thêm exclusions:** loại trừ các phương thức hoặc package không cần advice.
7. **Sau mỗi bước:** build sạch, đọc `weaveinfo`, so sánh danh sách join point, rồi test hành vi runtime.

Việc tách này giúp phân biệt **điều kiện tĩnh không match** với **điều kiện động chỉ được quyết định lúc thực thi**.

### 8.1 Ví dụ pointcut sai logic boolean

Không thể đồng thời là *method-call join point* và *method-execution join point* tại cùng một join point riêng lẻ:

~~~java
// Sai về ý nghĩa: không thể đồng thời là call và execution
call(* Work.*(..)) && execution(* Work.*(..))
~~~

Đây là **biểu thức AspectJ minh họa**, không phải chương trình độc lập. Nếu muốn chọn cả hai loại join point, hãy dùng:

~~~java
// Union của hai loại join point
call(* Work.*(..)) || execution(* Work.*(..))
~~~

Cẩn thận: chọn cả call và execution có thể làm **một thao tác nghiệp vụ** kích hoạt advice ở cả vị trí gọi lẫn thân method, dẫn đến đếm đôi nếu metric của bạn là request logic. Hãy xác định đơn vị quan sát trước khi đổi `&&` thành `||`.

### 8.2 `this`/`target` không có trong mọi ngữ cảnh

Ví dụ chọn `this(Work)` tại một điểm gọi từ method static có thể không match vì không tồn tại receiver “đang thực thi” tương ứng. Tương tự, `target(Work)` không phù hợp cho nhiều constructor-call join point trước khi đối tượng được tạo. Khi warning không match, cần xem loại join point và cách ràng buộc context, không chỉ kiểu tên class.

### 8.3 `args` là điều kiện theo vị trí

`args(String)` yêu cầu một danh sách tham số tương ứng với pattern của nó; nếu method có nhiều tham số, cần viết pattern phù hợp, ví dụ `args(String, ..)` khi muốn tham số đầu là String và cho phép các tham số sau. `args` kiểm tra điều kiện tại join point theo runtime semantics, không thay thế được kiểm tra số lượng và thứ tự tham số.

### 8.4 `within` khác `withincode`

- `within(TypePattern)` giới hạn theo vị trí lexical bên trong một kiểu.
- `withincode(SignaturePattern)` giới hạn theo vị trí lexical bên trong code của một member cụ thể.

Với `call` join point, vị trí lexical thuộc **caller**; với `execution`, vị trí lexical thuộc **body method được thực thi**. Vì vậy một pointcut `within(Work) && call(...)` có thể chỉ match các call site viết bên trong Work, chứ không match mọi lời gọi từ class bất kỳ vào Work.

Nếu muốn kiểm soát *execution* các method của Work, điểm bắt đầu nên là một `execution` pointcut có chữ ký rõ, không dùng `within` đơn độc rồi suy ra nó bắt mọi lời gọi.

## 9. Phân biệt `call` và `execution` khi đọc weaveinfo

### 9.1 `execution`: mã chứa thân method

`execution(void Work.process(String))` chọn thời điểm thực thi phần thân `process`. Để chèn execution advice, weaver phải có quyền xử lý **bytecode chứa method body** đó.

Một caller hoàn toàn không weave vẫn có thể gọi body của Work đã weave và kích hoạt execution advice. Đây là lý do thông báo `method-execution(...)` gắn với vị trí method body, không đòi hỏi mọi caller đều weave.

### 9.2 `call`: mã chứa vị trí gọi

`call(void Work.process(String))` chọn vị trí gọi, như dòng:

~~~java
work.process("A");
~~~

Nếu class chứa lời gọi đã weave, `call` advice có thể hiện diện tại đó, **dù class chứa body Work chưa weave**. Ngược lại, caller không weave thì `call` advice tại call site ấy không xuất hiện, kể cả body Work đã weave.

Vì vậy, trước một warning không match, cần trả lời:

- Pointcut chọn method **call** hay method **execution**?
- Code được đưa cho weaver gồm **caller** hay **target**?
- `-showWeaveInfo` đang hiển thị điểm ở phía nào?
- Tên kiểu/chữ ký tại call site là kiểu tĩnh nào? Có overload/generic bridge không?
- Có áp cùng advice ở call và execution gây đếm đôi hay không?

### 9.3 Direct call, self-invocation và proxy

Nếu hệ thống dùng JDK dynamic proxy, lời gọi `target.inner()` trực tiếp không đi qua proxy handler; lời gọi `target.outer()` rồi `this.inner()` trên target thường không tự vòng lại proxy. Nhưng với execution bytecode weaving tại `inner`, lời gọi nội bộ **vẫn có thể** nhận advice.

Do đó, không dùng log `weaveinfo` của bytecode weaving để kết luận một proxy đang chặn method, và cũng không dùng test self-invocation của proxy để kết luận bytecode weaving thất bại. Phải ghi rõ cơ chế và nơi điểm cắt thực sự được gắn.

## 10. Đọc một log chẩn đoán theo từng câu hỏi

Giả sử công cụ cho các nhóm thông báo sau (đây là **mô phỏng cấu trúc**, không phải bản chép log thực):

~~~text
INFO: aspect registered: AuditAspect
DEBUG: class Work presented to weaver
WEAVEINFO: method-execution(void Work.process(java.lang.String)) received advice
WARNING: adviceDidNotMatch for execution(void Work.ping(int))
~~~

Diễn giải chính xác:

1. Aspect được đăng ký, nên không cần bắt đầu bằng câu hỏi “có nhận aspect không?”.
2. Work đã được đưa qua weaver. Dòng này vẫn chưa chứng minh mọi method của Work nhận advice.
3. `process(String)` có một join point được dệt. Cần so sánh với danh mục điểm **phải** được dệt, không chỉ chấp nhận một match bất kỳ.
4. `ping(int)` là một điểm không phù hợp trong chương trình mẫu; cần kiểm tra chữ ký phương thức thật là `ping()`.
5. Cuối cùng chạy chương trình và xác nhận side effect advice, vì runtime có thể chạy bản artifact khác hoặc không đi qua điểm đã dệt.

Một dòng thông báo có thể chứa thêm **tên advice**, **aspect sở hữu advice**, **nguồn/vị trí dòng code** và **join point kind**. Khi điều tra, hãy lưu ít nhất những trường ấy để so sánh build.

## 11. Quy trình đọc log để khoanh vùng advice không khớp

Mục đích của bài này là biến **weave diagnostics** thành kết luận có kiểm chứng, thay vì lặp lại toàn bộ checklist cấu hình AOP. Lưu compiler/weaver version, tập class được xử lý, full pointcut và log gốc cùng phiên bản artifact.

1. **Aspect có xuất hiện trong log registration không?** Nếu không, chưa thể kết luận pointcut sai; xác minh aspect path/cấu hình trước.
2. **Có chứng cứ weaver xem xét đúng class hoặc call site không?** Với `execution` kiểm tra class chứa thân method; với `call` kiểm tra class chứa lệnh gọi. Thiếu input/weaving scope khác với không khớp chữ ký.
3. **Có `weaveinfo` cho đúng kind và chữ ký không?** So đủ declaring type, return type, tham số/overload, vị trí và advice ID; “weaving class” không bảo đảm method cần tìm nhận advice.
4. **Có `adviceDidNotMatch` hoặc lint type lookup không?** Xem phạm vi compiler đang xử lý và điều kiện động; cảnh báo không match trong một library build không tự chứng minh advice sẽ không match khi weave ứng dụng tiêu thụ.
5. **Tách từng điều kiện pointcut để kiểm tra giả thuyết.** Chọn join point kind và signature cụ thể trước, sau đó thêm `within`, `args`, `target`, `cflow` hoặc exclusions; so diff tập join point dự kiến với actual, không sửa bằng wildcard quá rộng.
6. **Kiểm tra runtime đối chứng.** Run artifact vừa weave, khẳng định một positive case được advice đúng số lần và một negative case không nhận advice; một dynamic predicate false có thể làm advice không chạy dù class đã được instrument.
7. **Chốt nguyên nhân hẹp nhất.** Ghi rõ “aspect chưa đăng ký”, “target ngoài scope”, “signature/kind sai”, “runtime condition false” hay “artifact chưa được chạy”; sửa đúng tầng rồi giữ log + regression test để so ở build sau.

**Mẫu báo cáo cần lưu:** `kind / expected signature / actual signature / aspect registration / class processing / weaveinfo or lint / runtime result / root cause`. Một warning đơn lẻ không đủ để chứng minh coverage; một weaveinfo đơn lẻ cũng không đảm bảo caller đã đi vào join point.

## 12. Đọc thông báo khi có điều kiện động

Không phải mọi điều kiện đều quyết định được tại build time. Pointcut chứa `this`, `target`, `args`, `cflow` hoặc `if` có thể cần kiểm tra dữ liệu runtime.

Ví dụ khái niệm:

~~~text
execution(* Work.process(..)) && target(Work) && runtimeCondition
~~~

Đây là **biểu thức mô tả**, `runtimeCondition` không phải pointcut designator độc lập có thể sao chép vào AspectJ. Ý nghĩa là: body Work.process được instrument tại nơi có thể match, nhưng điều kiện động có thể không đúng ở một lượt gọi cụ thể.

Một dòng weaveinfo có thể được phát ra vì weaver cài logic kiểm tra tại join point; **không đồng nghĩa mọi lượt chạy đều đi vào thân advice**. Khi test, phải chạy cả trường hợp điều kiện true và false, ghi event trước/sau tại runtime, và đảm bảo khi false không có side effect từ advice.

Trường hợp `cflow` đặc biệt dễ nhầm: compile-time weaving có thể chuẩn bị logic kiểm tra control flow, nhưng chỉ những lần thực thi nằm trong flow phù hợp mới nhận hành vi. Một test gọi target trực tiếp ngoài flow có thể không ghi event dù cùng method body đã được weave.

## 13. Một warning có thể hợp lệ, nhưng lỗ hổng coverage vẫn không được chấp nhận

Đối với **library aspect** dùng lại, một advice chưa match trong module library có thể là điều bình thường. Khi thư viện được áp cho ứng dụng thật, join point mới có thể xuất hiện. Cần một bài test tích hợp ở ứng dụng sử dụng thư viện để xác nhận số lượng và vị trí match.

Đối với **aspect bảo mật/audit bắt buộc**, “advice đã match ít nhất một điểm” lại không đủ. Giả sử các operation cần bảo vệ là:

~~~text
Required = {order.place, order.cancel, order.refund}
Matched  = {order.place, order.cancel}
Missing  = {order.refund}
~~~

Khi đó sẽ không nhất thiết có cảnh báo `adviceDidNotMatch`: advice đã match hai điểm. Nhưng operation `order.refund` bị bỏ sót, vi phạm chính sách. Vì vậy:

- Định nghĩa một **operation catalog** hoặc danh sách điểm bắt buộc.
- So sánh **Required** với **Matched**.
- Kiểm tra các đường thực thi gọi trực tiếp, job nền, callbacks và self-invocation.
- Áp dụng *default deny* hoặc bảo vệ bổ sung ở tầng tài nguyên khi chính sách yêu cầu.
- Xem cảnh báo lint như tín hiệu chẩn đoán, không thay thế phép chứng minh coverage.

Trong trường hợp chỉ dùng metrics tùy chọn, thiếu một điểm có thể làm mất quan sát chứ không thay đổi kết quả nghiệp vụ; vẫn cần tài liệu scope để dashboard không sai lệch âm thầm.

## 14. Dấu hiệu đáng ngờ khi sửa pointcut quá rộng

Một số cách “sửa nhanh” có thể che giấu bug:

- Thay `execution(void Work.process(String))` bằng `execution(* *(..))` để thấy nhiều weaveinfo: có thể instrument cả code hạ tầng, dẫn tới recursion và overhead.
- Thay `&&` bằng `||` tùy tiện: mở rộng tập join point ngoài dự kiến, thậm chí đếm cả call và execution.
- Bỏ mọi điều kiện `within`: advice có thể áp lên library hoặc method nhạy cảm mà không được kiểm soát.
- Tắt toàn bộ `-Xlint`: làm biến mất tín hiệu thiếu match mà không làm advice xuất hiện.
- Bỏ qua `typeNotExposedToWeaver`: khi target thực sự nằm ngoài build scope, pointcut đúng tên cũng không chèn được execution advice.
- Thêm ghi log trong advice và xem “thấy log” là đủ: advice có thể chạy hai lần, biến đổi exception hoặc bỏ qua original method.
- Chỉ đọc stdout của một demo đã weave rồi suy ra toàn bộ ứng dụng: module khác/classloader khác có thể không được xử lý.

Quy tắc sửa an toàn là: **dùng một điểm khớp hẹp, có thể chứng minh, rồi mở rộng có kiểm soát**. Ghi lại số join point trước/sau và test các vị trí không được chọn.

## 15. CI và tài liệu chẩn đoán có thể tái hiện

Một báo cáo điều tra nên có ít nhất:

~~~text
Aspect identifier: ...
Advice signature/pointcut: ...
Join point model: call / execution / ...
Expected operations: ...
Runtime entrypoints: ...
JDK + AspectJ compiler/weaver version: ...
Build mode: compile-time / post-compile / load-time weaving
Classpath / aspectpath / inpath / loader: ...
Weave diagnostics enabled: -showWeaveInfo, -Xlint:warning
Relevant weaveinfo: ...
Relevant lint messages: ...
Observed runtime advice invocations: ...
Observed original method invocations: ...
Matched but not required: ...
Required but not matched: ...
Fix and verification: ...
~~~

Đây là **mẫu báo cáo**, không phải kết quả đo của một ứng dụng cụ thể.

Ở CI, lưu compiler output và kiểm tra:

1. Có advice bắt buộc nào không được dệt?
2. Tập join point match có tăng/giảm bất thường sau refactor không?
3. Cảnh báo `adviceDidNotMatch` hoặc kiểu không được expose có ngoại lệ được duyệt không?
4. Tất cả operations quan trọng có test runtime qua entrypoint thật không?
5. Có kiểm thử negative/exclusion và bản build plain/woven đối chứng khi cần không?
6. Khi đổi toolchain hoặc classloader, danh mục join point đã được cập nhật/đối chiếu chưa?

Log weaving có thể lớn. Giữ đủ chi tiết cho điều tra nhưng tránh đẩy dữ liệu nhạy cảm từ advice vào log. Không đưa request body, access token, password hay ID định danh không cần thiết vào tên metric hay thông báo debug.

## 16. Những lỗi suy luận thường gặp

**“Có dòng weaving class thì advice chắc chắn đã dệt.”** Sai. Một class được weaver xử lý có thể không có join point match; cần xem `weaveinfo` cụ thể.

**“Có một dòng weaveinfo thì mọi phương thức trong class đều có advice.”** Sai. Weaveinfo nói về join point cụ thể, phải so với danh mục điểm cần bảo vệ.

**“Có warning adviceDidNotMatch nghĩa là chương trình chắc chắn bị lỗi.”** Không nhất thiết với aspect library hoặc thế giới code chưa hoàn chỉnh. Nhưng nếu đó là advice bắt buộc trong build application đầy đủ, cần điều tra.

**“Không có warning thì không thể bỏ sót join point.”** Sai. Advice có thể match một điểm nhưng bỏ sót nhiều operation khác.

**“Call và execution là cùng một vị trí.”** Sai. Call gắn với call site; execution gắn với method body. Phạm vi weaving có thể bao phủ một bên nhưng không bao phủ bên còn lại.

**“Runtime không thấy log nên compiler không weave.”** Chưa chắc. Có thể code không tới đó, điều kiện động false, chạy nhầm artifact, log collector chưa flush hoặc advice ngoài short-circuit.

**“Self-invocation luôn bỏ qua advice.”** Sai trong trường hợp body method đã được bytecode-weave cho execution advice. Kết luận bypass của một loại proxy không áp dụng cho mọi AOP.

**“Xóa một điều kiện pointcut khiến warning biến mất nghĩa là fix an toàn.”** Chưa chắc. Nó có thể tạo false positives, tăng overhead hoặc biến đổi hành vi của nhiều method không mong đợi.

## 17. Checklist thực hành

- [ ] Đã xác định cơ chế AOP: proxy, compile-time, post-compile hay load-time weaving.
- [ ] Đã biết loại join point: `call`, `execution` hay loại khác.
- [ ] Aspect thực sự được compiler/weaver nhận và không bị cấu hình loại trừ.
- [ ] Bytecode chứa join point thực sự nằm trong phạm vi weaving.
- [ ] Đã bật và đọc `-showWeaveInfo` cho build cần kiểm tra.
- [ ] Đã xem xét `-Xlint` và nguyên nhân của `adviceDidNotMatch` khi có.
- [ ] Đã kiểm tra `invalidAbsoluteTypeName`, `typeNotExposedToWeaver` và loại warning liên quan.
- [ ] Pointcut được tách thành loại điểm, chữ ký, lexical scope, dynamic context và exclusions.
- [ ] Có phân biệt điều kiện tĩnh với điều kiện chỉ đánh giá tại runtime.
- [ ] Có test advice chạy đúng số lần và original method đúng số lần.
- [ ] Có positive test, negative test và regression khi đổi tên/package/chữ ký.
- [ ] Có kiểm tra đường proxy/direct/self-invocation/job/callback khi áp dụng.
- [ ] Có bản woven/plain đối chứng hoặc bằng chứng artifact tương đương.
- [ ] Đã so sánh tập **bắt buộc** với tập **thực tế match**, không chỉ tìm một weaveinfo bất kỳ.
- [ ] Không tắt cảnh báo hoặc mở rộng wildcard chỉ để làm build xanh.
- [ ] Báo cáo cuối ghi rõ nguồn log, version toolchain, classpath và cách tái hiện.

## 18. Tài liệu tham khảo

- [AspectJ Problem Diagnosis Guide](https://eclipse.dev/aspectj/doc/latest/pdguide/pdguide.html) — thông báo compiler/weaver, `-showWeaveInfo`, lint và kỹ thuật phân rã pointcut.
- [AspectJ ajc Compiler/Weaver Guide](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html) — các tùy chọn `-showWeaveInfo`, `-Xlint`, `-Xlintfile`, build và classpath.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html) — mô hình join point, ý nghĩa pointcut và advice, giới hạn weaving.

## 19. Kết luận

**Đọc đúng thông báo weaving là đọc đúng mức bằng chứng.** Aspect đăng ký, class được đưa qua weaver, join point được dệt và advice chạy thật là bốn trạng thái cần kiểm tra riêng. `-showWeaveInfo` giúp xác nhận những điểm đã được dệt; `-Xlint` giúp phát hiện các trường hợp không match, sai kiểu hoặc không đủ phạm vi weaving; test runtime chứng minh chương trình thật đã đi qua advice và giữ đúng hợp đồng. Với AOP có tác động tới bảo mật hoặc tính toàn vẹn dữ liệu, cần thêm **so sánh tập operation bắt buộc với tập join point thực tế** để phát hiện cả những điểm bị bỏ sót mà không tạo một warning rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-004">Đối chiếu chữ ký thực tế với biểu thức pointcut</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu chữ ký thực tế với biểu thức pointcut

## 1. Mục tiêu và bản chất của việc đối chiếu

Khi một advice không chạy, chạy sai phương thức hoặc chạy quá rộng, câu hỏi đầu tiên không phải “advice viết đúng chưa?” mà là **join point đang xảy ra có thật sự khớp với pointcut hay không?** Muốn trả lời, phải đối chiếu ba thành phần độc lập:

1. **Join point cần bắt:** sự kiện đang xét là *gọi phương thức* (`call`), *thực thi phương thức* (`execution`), đọc/ghi trường hay sự kiện khác?
2. **Chữ ký thực tế:** tên phương thức, kiểu khai báo, kiểu trả về, danh sách kiểu tham số, visibility, `static` và những chi tiết khác mà bộ dệt (weaver) thực sự nhìn thấy.
3. **Biểu thức pointcut:** mẫu chữ ký, điều kiện về vị trí mã, đối tượng/đối số lúc chạy, annotation và tổ hợp điều kiện.

Một phương thức có thể tồn tại, được gọi thành công và vẫn **không** khớp một pointcut. Ngược lại, pointcut dùng ký tự đại diện quá rộng có thể khớp hàng trăm vị trí không mong muốn. Đây là vấn đề chẩn đoán AOP nói chung; các ví dụ dùng **cú pháp pointcut của AspectJ** để có quy tắc cụ thể và khả năng kiểm chứng. Không nên suy rộng nguyên xi cú pháp đó sang mọi công cụ AOP: mỗi hệ thống chỉ hỗ trợ tập join point và cơ chế weaving mà nó định nghĩa.

### Những dấu hiệu hay gặp

- Không xuất hiện log của `before()` dù code trong phương thức đã chạy.
- Pointcut dự kiến bắt một overload nhưng lại bắt nhiều overload, hoặc bỏ sót overload.
- Pointcut `call(...)` và `execution(...)` cho số sự kiện khác nhau.
- Một phương thức khi gọi qua kiểu interface, lớp con hoặc qua đường gọi nội bộ cho kết quả khó hiểu.
- Biểu thức đúng về mặt cú pháp nhưng không có join point tương ứng trong tập class được dệt.

**Nguyên tắc điều tra:** quan sát chữ ký thực tế và loại join point trước; đối chiếu từng phần của mẫu; cuối cùng kiểm tra phạm vi weaving và điều kiện động. Không dùng kết quả của một chuỗi log duy nhất để suy ra mọi nguyên nhân.

## 2. Chữ ký Java, chữ ký join point và vị trí xảy ra sự kiện

Xét đoạn Java:

```java
package demo;

public class OrderService {
    public String place(String code) {
        return "placed:" + code;
    }

    public String place(int number) {
        return "numeric:" + number;
    }

    public void cancel(String code) {
        System.out.println("cancel:" + code);
    }
}
```

Ba phương thức có thể phân biệt bằng các đặc điểm:

| Phương thức | Kiểu trả về | Kiểu khai báo | Tên | Kiểu tham số |
| --- | --- | --- | --- | --- |
| `place(String)` | `java.lang.String` | `demo.OrderService` | `place` | `java.lang.String` |
| `place(int)` | `java.lang.String` | `demo.OrderService` | `place` | `int` |
| `cancel(String)` | `void` | `demo.OrderService` | `cancel` | `java.lang.String` |

**Java phân biệt overload bằng tên và danh sách kiểu tham số**, không bằng kiểu trả về đơn thuần. Nhưng **mẫu chữ ký pointcut** của AspectJ còn có thể ràng buộc kiểu trả về, modifier và kiểu khai báo. Vì thế `execution(String demo.OrderService.place(String))` là một bộ lọc chặt hơn `execution(* demo.OrderService.place(..))`.

**Join point** là một sự kiện có thể chọn để gắn advice, không đồng nhất với một dòng khai báo phương thức:

- `call(String demo.OrderService.place(String))`: điểm gọi phù hợp, gắn với **call site** (vị trí mã có lời gọi).
- `execution(String demo.OrderService.place(String))`: lúc thân phương thức phù hợp bắt đầu thực thi, gắn với **method body**.
- `get(* demo.OrderService.someField)` / `set(* demo.OrderService.someField)`: sự kiện đọc/ghi trường theo mẫu trường, không phải gọi getter/setter.
- `within(demo.OrderService)` kiểm tra nơi mã của join point **được định nghĩa**; `withincode(* demo.OrderService.place(..))` kiểm tra join point nằm trong mã của phương thức đó.

Ví dụ lời gọi `service.place("A01")` nằm trong `demo.Main.main`: với join point `call`, `within(demo.Main)` có thể đúng và `within(demo.OrderService)` sai. Với join point `execution` của `place`, tình huống đảo lại. Vì vậy chỉ nhìn tên phương thức được gọi không đủ để kết luận biểu thức có khớp.

## 3. Đọc chính xác cú pháp mẫu chữ ký

Dạng khái quát cho phương thức trong AspectJ:

```text
execution(modifiers-pattern? return-type-pattern
          declaring-type-pattern?method-name-pattern(parameter-pattern)
          throws-pattern?)
```

Không có nghĩa phải luôn viết hết mọi phần. Ví dụ:

| Mẫu | Cách hiểu |
| --- | --- |
| `execution(* *(..))` | Thực thi phương thức với kiểu trả về, tên và số lượng/kiểu tham số bất kỳ. |
| `execution(String demo.OrderService.place(String))` | Chỉ phương thức có các thành phần chỉ định đúng mẫu. |
| `execution(* demo.OrderService.place(..))` | Mọi overload tên `place` theo mẫu trên kiểu khai báo tương ứng. |
| `execution(* demo.OrderService.place())` | Chỉ overload không có tham số. |
| `execution(* demo.OrderService.place(*))` | Chính xác một tham số, kiểu bất kỳ. |
| `execution(* demo.OrderService.place(String, ..))` | Tham số đầu là `String`, theo sau là 0 hoặc nhiều tham số khác. |
| `execution(public !static * demo.OrderService.*(..))` | Các phương thức public không static trong phạm vi kiểu và tên đã nêu. |
| `execution(* demo..*.place(..))` | Các kiểu phù hợp trong namespace `demo` và các cấp con theo quy tắc mẫu kiểu. |

Phân biệt chính xác các ký hiệu:

- `*` trong vị trí kiểu trả về hoặc tên: đại diện cho phần tử bất kỳ theo **vị trí** đang xét. `place*` khớp những tên bắt đầu bằng `place`, không tự giới hạn ở tên `place`.
- `()`: không tham số; `(*)`: **một** tham số bất kỳ; `(..)`: **0 hoặc nhiều** tham số bất kỳ; `(String, ..)`: từ một tham số, trong đó đầu tiên phải là `String`.
- `..` trong **mẫu package/type** có tác dụng khác `..` trong **danh sách tham số**. Đừng đọc mọi dấu hai chấm thành wildcard chung.
- `+` trong một *type pattern*, ví dụ `demo.BaseService+`, có thể chọn kiểu gốc và các subtype của nó. Không lẫn `+` với biểu thức cộng trong Java.
- `!` phủ định mẫu/điều kiện được phép; `&&` yêu cầu đồng thời; `||` cho phép một trong các điều kiện. Dùng dấu ngoặc để tránh hiểu sai ưu tiên.
- Tên kiểu đầy đủ `java.lang.String` giúp loại trừ nhầm lẫn tên lớp; tên viết ngắn chỉ nên dùng khi môi trường biên dịch và import giải quyết duy nhất.

Ví dụ điểm dễ nhầm:

```text
execution(* demo.OrderService.place(*))
execution(* demo.OrderService.place(..))
```

Dòng thứ nhất có thể khớp cả `place(String)` và `place(int)` vì mỗi phương thức có đúng một tham số. Dòng thứ hai còn có thể khớp thêm overload không tham số hoặc nhiều tham số. Nếu muốn chỉ chọn tham số `String`, phải ghi kiểu `String` thay cho `*`.

**Kiểu trả về cũng là phần so khớp.** `execution(void demo.OrderService.place(..))` không khớp hai phương thức `place` trên vì chúng trả về `String`. Tên phương thức giống nhau không thể “cứu” một return-type pattern sai.

## 4. Bảng đối chiếu minh bạch trước khi kiểm thử

Giả sử class `demo.OrderService` chỉ có ba phương thức ở mục 2 và tất cả đều được dệt cho `execution`. Với ký hiệu ✓ là khớp:

| Pointcut | `place(String)` | `place(int)` | `cancel(String)` | Giải thích |
| --- | :---: | :---: | :---: | --- |
| `execution(String demo.OrderService.place(String))` | ✓ | ✗ | ✗ | Đúng cả tên, return type và tham số. |
| `execution(* demo.OrderService.place(..))` | ✓ | ✓ | ✗ | Tên `place`, mọi overload. |
| `execution(* demo.OrderService.place(*))` | ✓ | ✓ | ✗ | Một tham số nhưng không ràng buộc kiểu. |
| `execution(* demo.OrderService.*(String))` | ✓ | ✗ | ✓ | Mọi tên, đúng một tham số `String`. |
| `execution(void demo.OrderService.*(..))` | ✗ | ✗ | ✓ | Ràng buộc kiểu trả về `void`. |
| `execution(* demo.OrderService.*())` | ✗ | ✗ | ✗ | Không phương thức nào không có tham số. |

Bảng này kiểm **mẫu chữ ký**. Khi pointcut thực tế có thêm `&& within(...)`, `&& args(...)`, `&& target(...)`, annotation hoặc `cflow(...)`, cần lập thêm cột cho từng điều kiện; kết quả của toàn biểu thức là phép giao/hiệp/phủ định tương ứng. Một điều kiện `&&` sai làm cả pointcut không khớp.

## 5. Cách lấy chứng cứ về chữ ký thực tế

### 5.1. Từ mã nguồn: ghi đủ các thành phần

Đối với mỗi phương thức nghi vấn, chép lại dưới dạng có ngữ cảnh:

```text
public java.lang.String demo.OrderService.place(java.lang.String)
public java.lang.String demo.OrderService.place(int)
public void demo.OrderService.cancel(java.lang.String)
```

Đây là bản ghi **để phân tích**, không phải cú pháp pointcut phải copy nguyên trạng. Tên biến `code`, `number` không tham gia so khớp kiểu tham số. Cũng không được dùng kiểu của giá trị truyền vào để thay thế cho chữ ký đã khai báo: `place(Object)` không trở thành `place(String)` chỉ vì đối số thời điểm chạy là một `String`.

### 5.2. Dùng Java Reflection để xem khai báo được runtime tải

Chương trình độc lập sau đây chạy được bằng JDK 17+, không yêu cầu thư viện AOP:

**Tệp `SignatureInspector.java`**

```java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

public class SignatureInspector {
    static class Example {
        public String place(String code) { return code; }
        public String place(int number) { return Integer.toString(number); }
        protected void cancel(String code) {}
    }

    public static void main(String[] args) {
        Method[] methods = Example.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)
                .thenComparing(m -> Arrays.toString(m.getParameterTypes())));

        for (Method m : methods) {
            String params = Arrays.stream(m.getParameterTypes())
                    .map(Class::getSimpleName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            System.out.printf("%s %s %s(%s)%n",
                    Modifier.toString(m.getModifiers()),
                    m.getReturnType().getSimpleName(),
                    m.getName(),
                    params);
        }
    }
}
```

Chạy:

```bash
javac SignatureInspector.java
java SignatureInspector
```

Output:

```text
protected void cancel(String)
public String place(String)
public String place(int)
```

`getDeclaredMethods()` cho các phương thức khai báo trực tiếp trên class, có thể bao gồm private và các phương thức do compiler tạo; **không tự bao gồm các phương thức kế thừa**. `getMethods()` nhìn các phương thức public có thể truy cập, bao gồm kế thừa. Các thông tin của `Method` rất hữu ích để xác minh tên, modifier, return type, parameter types; tuy nhiên **Reflection không thay thế quan sát join point được dệt**. Một phương thức xuất hiện trong Reflection vẫn có thể không có advice vì chưa nằm trong phạm vi weaving, vì `call` không có call site được dệt, hoặc vì điều kiện pointcut khác sai.

Khi nghi ngờ overload, generic hoặc bridge, có thể in bổ sung `m.isBridge()`, `m.isSynthetic()`, `m.toGenericString()`. So sánh tiếp bytecode bằng `javap -p -s -c demo.OrderService` khi cần xem descriptor và lời gọi thực tế. Với `javap`, ký hiệu descriptor như `Ljava/lang/String;` là dạng JVM, không phải văn bản đặt trực tiếp vào mẫu AspectJ.

### 5.3. Từ chính một join point AspectJ đã được dệt

Trong advice phục vụ chẩn đoán, các API `thisJoinPointStaticPart.getKind()` và `getSignature().toLongString()` cho biết loại và chữ ký join point. `thisJoinPoint.getArgs()` cho thấy **giá trị** đối số ở sự kiện đó. Khi in giá trị, lưu ý có thể chứa dữ liệu nhạy cảm, đối tượng lớn hoặc `toString()` gây tác dụng phụ.

**Tách rõ hai tầng chứng cứ:** Reflection/bytecode xác minh thành viên và đường gọi; thông tin join point xác minh sự kiện mà weaver đã bắt được. Không dùng một tầng làm bằng chứng tuyệt đối cho tầng kia.

## 6. Ví dụ AspectJ native: tái hiện lỗi và sửa pointcut

Ví dụ này dùng **AspectJ source weaving** (tệp `.aj`, trình biên dịch `ajc`), không cần container/framework. Cấu trúc:

```text
demo/
  OrderService.java
  Main.java
  Trace.aj
```

**Tệp `demo/OrderService.java`**

```java
package demo;

public class OrderService {
    public String place(String code) {
        return "placed:" + code;
    }

    public String place(int number) {
        return "numeric:" + number;
    }

    public void cancel(String code) {
        System.out.println("cancel:" + code);
    }
}
```

**Tệp `demo/Main.java`**

```java
package demo;

public class Main {
    public static void main(String[] args) {
        OrderService service = new OrderService();
        System.out.println(service.place("A01"));
        System.out.println(service.place(7));
        service.cancel("A01");
    }
}
```

**Tệp `demo/Trace.aj`**

```java
package demo;

public aspect Trace {
    before(): execution(String demo.OrderService.place(String)) {
        System.out.println("[pointcut] place(String)");
    }
}
```

Cài **AspectJ compiler `ajc` hỗ trợ Java 17** (AspectJ 1.9.8 trở lên) và bảo đảm `aspectjrt.jar` tương ứng có trên classpath. Với hệ thống Unix-like đặt `ASPECTJ_HOME` tới thư mục cài AspectJ, dùng:

```bash
ajc -17 -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" -d out demo/OrderService.java demo/Main.java demo/Trace.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" demo.Main
```

Trên Windows, chuyển dấu ngăn cách classpath từ `:` sang `;` và dùng cú pháp biến môi trường phù hợp với shell. Lệnh trên yêu cầu `ajc` có trong `PATH`; chạy bằng `javac` đơn thuần sẽ không biên dịch cú pháp `aspect`/`before` của `.aj`.

Stdout dự kiến của mã nguồn nêu trên, khi weaving thành công:

```text
[pointcut] place(String)
placed:A01
numeric:7
cancel:A01
```

Điểm đáng quan sát: advice chạy trước `place(String)`; `place(int)` không bị bắt dù cùng tên; `cancel(String)` không bị bắt dù cùng kiểu tham số. **Đây là bằng chứng đối chiếu mẫu, không phải bằng chứng rằng mọi loại lời gọi đều đã được instrument.**

Thử lần lượt các thay đổi độc lập (mỗi lần chỉ sửa đúng biểu thức trong `Trace.aj`, biên dịch lại rồi chạy):

1. `execution(* demo.OrderService.place(..))`: in log trước **cả hai** overload `place`.
2. `execution(* demo.OrderService.place(String))`: chỉ log overload `String`; bỏ ràng buộc kiểu trả về nhưng vẫn giới hạn tham số.
3. `execution(* demo.OrderService.place())`: không log; biểu thức hợp lệ nhưng không có overload không tham số.
4. `execution(void demo.OrderService.place(String))`: không log; sai return type.
5. `execution(* demo.OrderService.*(String))`: log cho `place(String)` **và** `cancel(String)`.

Các phép thử đó cố ý thay **một điều kiện** tại một thời điểm: nếu sửa đồng thời return type, tên và tham số, ta không biết thành phần nào làm tập join point thay đổi. Hãy ghi mẫu, join point dự kiến, kết quả thực tế vào bảng rồi so sánh.

## 7. Khi chữ ký đúng nhưng pointcut vẫn không khớp

### 7.1. Nhầm `call` và `execution`

`call(...)` chọn sự kiện tại mã **gọi**; `execution(...)` chọn sự kiện vào **thân phương thức**. Với một call site ở class A gọi method class B:

- `call(...) && within(A)` có thể khớp nếu call site ở A.
- `execution(...) && within(B)` có thể khớp nếu thân phương thức định nghĩa ở B.
- Việc weaving chỉ đưa B vào phạm vi mà không đưa A vào có thể cho kết quả khác khi thử `execution` và `call`.
- Một thân phương thức có thể chạy nhiều lần từ nhiều call site; điểm dệt và số lần sự kiện quan sát cần được phân biệt.
- `call` không quan sát mọi chuyển điều khiển giống `execution`: chẳng hạn lời gọi `super` tới instance method có các quy tắc join point đặc biệt.

Khi chẩn đoán, đánh dấu rõ **file chứa lời gọi**, **file chứa phương thức** và **class nào được biên dịch/dệt**, thay vì suy từ tên method.

### 7.2. Kiểu khai báo, interface, kế thừa và dispatch

Java có **kiểu tĩnh** ở call site và **kiểu thực tế** của đối tượng khi chạy. Biểu thức chữ ký (đặc biệt ở `call`) có thể phụ thuộc chữ ký mà compiler nhìn thấy ở vị trí gọi; `execution` liên quan đến method body thực sự thực thi. Đặc biệt khi phương thức được khai báo ở interface hoặc lớp cha, hãy thu thập:

- Kiểu của biến nhận lời gọi (ví dụ `Service`), kiểu đối tượng (ví dụ `DefaultService`).
- Method được khai báo/override ở đâu, chữ ký của phần khai báo và phần override.
- Mẫu kiểu đang viết là `BaseService`, `BaseService+` hay `ConcreteService`.
- Join point là `call` hay `execution` và đoạn bytecode nào đã được dệt.

Không áp dụng một câu “đổi sang tên class runtime là chắc chắn khớp” cho mọi trường hợp. Thử pointcut chỉ tên method với wildcard trong phạm vi hẹp, sau đó thêm type pattern đủ chính xác.

### 7.3. `args(...)` không phải danh sách tham số của `execution(...)`

`execution(* demo.OrderService.place(String))` kiểm tra **chữ ký khai báo**. `args(String)` kiểm tra **kiểu ngữ cảnh/giá trị đối số phù hợp tại join point** theo ngữ nghĩa pointcut. Ví dụ phương thức khai báo `handle(Object)` nhận một `String` có thể khiến điều kiện động về đối số khác với việc yêu cầu chữ ký `handle(String)`. Điều này đặc biệt quan trọng khi đối số có polymorphism hoặc `null`; không đánh đồng giá trị runtime với parameter type khai báo.

Tương tự, `this(SomeType)` kiểm tra đối tượng thực thi hiện tại trong ngữ cảnh join point, còn `target(SomeType)` kiểm tra đối tượng đích. Tại method call hai đối tượng có thể khác nhau; trong ngữ cảnh static, không có instance `this`. Nếu biểu thức `execution(...) && this(...) && target(...)` không khớp, phải xác minh từng thành phần chứ không chỉ method signature.

### 7.4. Điều kiện vị trí và luồng điều khiển

`within(TypePattern)` lọc phạm vi mã định nghĩa join point; `withincode(MethodPattern)` lọc các join point bên trong vùng mã của method/constructor tương ứng. `cflow(P)` chọn join point nằm trong luồng điều khiển của P (bao gồm P); `cflowbelow(P)` loại chính P. Vì thế hai lần gọi cùng một method, từ hai đường chạy khác nhau, có thể cho kết quả khác dù cùng chữ ký.

Ví dụ `execution(* demo.OrderService.*(..)) && cflow(execution(* demo.Main.trigger(..)))` cần cả phương thức đích lẫn ngữ cảnh luồng điều khiển của `trigger`. Nếu không có trigger phù hợp trong stack, phần chữ ký đúng vẫn không đủ.

### 7.5. Annotation, overload, varargs và generic

- `@annotation(Tracked)` yêu cầu annotation theo quy tắc join point hỗ trợ; phân biệt annotation của phương thức với annotation trên **kiểu**, **tham số** hoặc **đối số lúc chạy**. Kiểm tra target của annotation, `@Retention` và đường weaving thay vì suy từ tên.
- `String... values` trong Java là tham số varargs, ở bytecode về cơ bản có dạng mảng `String[]`. Khi mẫu không khớp, kiểm tra chữ ký biên dịch và cách pointcut biểu diễn kiểu tham số.
- Với `List<String>` và `List<Integer>`, Java generics chịu **type erasure**; đừng giả định runtime có thể phân biệt mọi type argument như ở mã nguồn. Dùng descriptor bytecode và giới hạn của weaver làm cơ sở.
- Override với kiểu trả về hiệp biến hoặc implement generic interface có thể phát sinh **bridge/synthetic methods** do compiler tạo. Đầu ra Reflection hoặc `javap -p -v` giúp nhận diện; số advice chạy thực tế còn phụ thuộc join point, kiểu bytecode, phiên bản và phạm vi weaving.
- Constructor dùng pattern khác method: ví dụ `execution(demo.OrderService.new(..))` để chọn constructor execution; không cố tìm constructor tên `OrderService` như method thông thường. Đọc/ghi field cũng có pattern riêng.
- Modifier như `private`/`protected`/`static` ảnh hưởng mẫu chữ ký và khả năng quan sát join point theo từng weaving mechanism. Một công cụ AOP không hỗ trợ chọn private method không đồng nghĩa với việc cú pháp AspectJ `execution(private * *(..))` sai.

## 8. Quy trình gỡ lỗi theo từng lớp

Đi theo thứ tự sau và ghi chứng cứ ngay tại từng bước:

1. **Chốt hiện tượng:** advice mong đợi chạy ở phương thức nào, lúc gọi hay lúc thực thi, số lần mong đợi là bao nhiêu? Nếu có nhiều overload, liệt kê tất cả.
2. **Xác minh class chạy thật:** đúng bản build/classpath, đúng classloader và đúng bytecode? Nếu vừa sửa source nhưng chưa rebuild, mọi phép đối chiếu sau đó có thể vô nghĩa.
3. **Lấy chữ ký:** đọc khai báo, dùng Reflection/`javap` nếu cần; ghi modifier, return type, declaring type, tên, parameter types, annotation, bridge/synthetic flags.
4. **Tách pointcut:** đánh dấu `call`/`execution`, tách method pattern, lexical scope, runtime context, annotation và control-flow conditions.
5. **Lập bảng đúng/sai cho từng thành phần:** mỗi conjunct `&&` có một cột. Nếu một thành phần sai, giải thích *tại sao*, không sửa ngẫu nhiên các thành phần khác.
6. **Thu hẹp mẫu thử:** trong một môi trường kiểm soát, bắt đầu từ `execution(* demo.OrderService.*(..))` với lớp đích cụ thể, xác nhận có sự kiện rồi lần lượt thêm tên, return type, tham số và modifier. Tránh `execution(* *(..))` trên toàn chương trình đang chạy vì quá nhiều sự kiện và log.
7. **Kiểm tra weaving:** có biên dịch/dệt class đích không, dùng compile-time, post-compile hay load-time weaving, class có được tải trước khi weaver hoạt động không, có exclusion/include nào làm mất class? Bật thông tin weave phù hợp với compiler/weaver, chẳng hạn `ajc -showWeaveInfo` khi khả dụng, thay vì suy từ absence of log.
8. **Đối chiếu số sự kiện:** tạo call site tối giản, chạy một phương thức tại một thời điểm, xem thông tin `thisJoinPointStaticPart` ở điểm đã xác nhận dệt. Tách lỗi pointcut khỏi lỗi advice (exception, filter trong body advice, logger level).
9. **Khôi phục mức ràng buộc đúng:** khi đã biết nguyên nhân, viết lại pointcut đủ chặt, có bài kiểm thử khớp và không khớp, rồi chạy lại trên binary thật.

### Mẫu phiếu chẩn đoán

```text
Mục tiêu:          demo.OrderService.place(String)
Join point kind:   execution
Chữ ký đo được:    public java.lang.String demo.OrderService.place(java.lang.String)
Pointcut đang xét: execution(void demo.OrderService.place(String))
Return type:       FAIL — thực tế String, biểu thức yêu cầu void
Declaring type:    PASS
Method name:       PASS
Parameters:        PASS
Weaving:           class được biên dịch/dệt, xác minh riêng
Kết luận:          pointcut không match vì return-type pattern
Bản sửa:           execution(String demo.OrderService.place(String))
```

Phiếu tương tự có thể dùng cho lỗi scope: `call(... ) && within(demo.OrderService)` ở lời gọi đặt trong `demo.Main` có chữ ký method match nhưng lexical scope fail. Giữ hai kết luận tách biệt.

## 9. Những cách sửa dễ gây lỗi mới

| Cách làm vội | Vì sao nguy hiểm | Cách làm có kiểm chứng |
| --- | --- | --- |
| Thay bằng `execution(* *(..))` và để nguyên trong hệ thống | Khớp rộng, làm nhiễu log và có thể gây overhead. | Giới hạn package/type rồi siết dần return/name/params. |
| Đổi `call` thành `execution` mà không xác định mục tiêu | Thay luôn ngữ nghĩa sự kiện và nơi dệt, có thể làm advice chạy lúc khác. | Xác định muốn quan sát call site hay method body. |
| Nhìn log thiếu rồi kết luận pattern sai | Class có thể chưa được dệt; advice có thể lỗi hoặc log bị lọc. | Xác minh build, weaving, chạy thử pointcut tối giản. |
| So sánh `args(String)` với tham số `Object` rồi xem là mâu thuẫn | Nhầm type động với chữ ký khai báo. | Ghi cả parameter type và runtime argument type. |
| Đọc `(..)` như “một tham số bất kỳ” | Bắt cả phương thức 0/nhiều tham số. | Dùng `(*)` khi chính xác một tham số bất kỳ. |
| Chỉ xem mã nguồn generic | Erasure/bridge làm bytecode khác kỳ vọng. | Dùng Reflection và `javap` để đối chiếu binary. |
| Tin rằng mọi phương thức nhìn thấy ở Reflection đều được advice | Reflection liệt kê method, không chứng minh join point đã dệt. | Kiểm tra weave info và chạy test có điểm gọi cụ thể. |

## 10. Bài luyện tập và tự kiểm tra

Với các phương thức sau (giả sử chúng thực sự có `execution` join point trong phạm vi weaving):

```java
public String find(String id) { return id; }
public String find(long id) { return Long.toString(id); }
public void find() {}
public boolean find(String id, int limit) { return true; }
```

Hãy dự đoán số phương thức khớp từng biểu thức:

1. `execution(* *.find(..))` → **4**; không ràng buộc kiểu trả về, tham số.
2. `execution(String *.find(*))` → **2**; hai overload một tham số trả về `String`.
3. `execution(* *.find(String))` → **1**; chính xác một tham số `String`.
4. `execution(* *.find(String, ..))` → **2**; `find(String)` và `find(String,int)`.
5. `execution(void *.find())` → **1**; phương thức không tham số trả về `void`.

Mỗi kết quả chỉ dựa trên **mẫu chữ ký** giả định; nếu thêm điều kiện context hoặc giới hạn weaving, kết quả thực tế có thể nhỏ hơn. Tự thực hành bằng cách đặt các phương thức trong một class riêng, dệt cùng một aspect rồi so sánh ma trận dự đoán với các sự kiện quan sát.

## 11. Checklist trước khi kết luận

- [ ] Đã phân biệt `call`, `execution` hay join point loại khác.
- [ ] Đã ghi chữ ký *đầy đủ* (modifier, return type, declaring type, name, parameter types).
- [ ] Đã giải mã đúng `*`, `()`, `(*)`, `(..)`, `..` ở type pattern và `+`.
- [ ] Đã xét overload, inheritance/interface, generic erasure và bridge khi có liên quan.
- [ ] Đã đánh giá độc lập `within`, `withincode`, `this`, `target`, `args`, annotation và `cflow` nếu biểu thức có chúng.
- [ ] Đã xác minh class/call site được dệt và chạy đúng binary.
- [ ] Đã kiểm thử tối thiểu cả một phương thức **phải match** và một phương thức **không được match**.
- [ ] Pointcut sau sửa đủ hẹp để tránh log sai chỗ và chi phí advice không cần thiết.

**Kết luận:** đối chiếu chữ ký là phép so khớp có cấu trúc giữa *join point kind + chữ ký thật + các điều kiện pointcut + phạm vi weaving*. Khi biết thành phần cụ thể nào không khớp, ta sửa được biểu thức với thay đổi nhỏ, dự đoán được những phương thức bị ảnh hưởng và kiểm chứng kết quả bằng một tình huống có thể lặp lại.

## Tài liệu đọc thêm

- [AspectJ Programming Guide — Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): ngữ nghĩa các pointcut designator và signature patterns.
- [AspectJ Programming Guide — Join Points and Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/language-joinPoints.html): ví dụ về `call`/`execution` và tổ hợp điều kiện.
- [AspectJ Development Guide — ajc](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): lệnh biên dịch, in thông tin weaving và quản lý tập class đầu vào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-005">Phân biệt lỗi không khớp pointcut với lời gọi không qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt pointcut không khớp và lời gọi không đi qua proxy trong AOP

## 1. Vấn đề: cùng không thấy advice, nhưng có hai nguyên nhân khác nhau

Khi một advice không xuất hiện trong log hoặc một kiểm tra cắt ngang không chạy, kết luận “pointcut viết sai” có thể dẫn đến sửa nhầm chỗ. Với AOP dùng proxy/interceptor, một lời gọi chỉ đi qua advice khi **thực sự chạm tới đường chặn** và **được bộ chọn join point/pointcut chấp nhận**. Hai lỗi cần phân biệt:

1. **Pointcut không khớp:** lời gọi **đã đi vào** proxy/interceptor nhưng phép chọn không áp dụng advice cho sự kiện này; có thể sai tên/phạm vi phương thức, sai loại join point, điều kiện annotation/tham số không đạt, hoặc loại sự kiện không được hỗ trợ.
2. **Lời gọi không đi qua proxy:** phương thức đích vẫn chạy nhưng caller nắm tham chiếu đối tượng gốc, gọi nội bộ trên `this`, đi theo nhánh bỏ qua wrapper hoặc chạy ở nơi chưa được chặn. Pointcut có thể đúng với phương thức ấy, nhưng không có cơ hội được đánh giá trên đường gọi thực tế.

Cần thêm trạng thái thứ ba: **đã đến interceptor, matcher báo khớp, nhưng advice vẫn không chạy**. Đây là lỗi ở cấu hình advice/chuỗi interceptor, thứ tự, điều kiện runtime, nhánh exception hoặc bản thân cơ chế quan sát; không nên ép thành một trong hai kết luận trên.

> Phạm vi bài: các khái niệm Aspect-Oriented Programming (AOP) nói chung, đặc biệt là **proxy-based AOP** so với **weaving vào mã**. Không có một quy tắc hoạt động duy nhất áp dụng cho mọi bộ công cụ AOP.

## 2. Mô hình hai tầng: bắt được lời gọi rồi mới chọn advice

Một pipeline AOP dựa trên proxy có dạng khái niệm:

```text
Caller
  |
  v
Reference đến proxy? -- KHÔNG --> Target.method() (không có proxy interception)
  |
 CÓ
  v
Proxy / interception boundary
  |
  v
Có nhận được loại lời gọi này? -- KHÔNG --> không tạo join point phù hợp
  |
 CÓ
  v
Đánh giá pointcut / matcher
  |
  +-- FALSE --> Target.method() (bỏ qua advice tương ứng)
  |
 TRUE
  v
Advice chain --> proceed / gọi Target.method()
```

Phân biệt các khái niệm:

| Khái niệm | Vai trò và ý nghĩa khi chẩn đoán |
| --- | --- |
| **Target** | Đối tượng chứa logic nghiệp vụ; gọi trực tiếp target có thể hoàn toàn bỏ qua proxy. |
| **Proxy** | Đối tượng trung gian đóng vai trò điểm đi vào interception; tên biến là `service` không chứng minh nó là proxy. |
| **Join point** | Sự kiện có thể tác động: gọi phương thức, thực thi phương thức, đọc/ghi trường, khởi tạo... tùy mô hình AOP. |
| **Pointcut** | Quy tắc nhận diện một tập join point, có thể gồm điều kiện tĩnh và runtime. |
| **Advice** | Đoạn xử lý `before`, `after`, `around`... được gắn vào join point đã chọn. |
| **Weaving** | Cách đưa hành vi AOP vào luồng thực thi, ví dụ biến đổi bytecode hoặc can thiệp trong biên dịch/nạp lớp. |

**Công thức tư duy:** trong mô hình proxy, thiếu advice có thể do **đường gọi không được chặn** hoặc **interceptor có chạy nhưng pointcut không chọn**. Vì vậy, dấu hiệu “không thấy dòng log của advice” **không đủ** để phân biệt.

Một hệ thống có thể đánh giá pointcut sớm để quyết định có tạo proxy hay không; không phải mọi bộ công cụ đều gọi bộ matcher trên **mọi** lần invocation. Sơ đồ trên biểu diễn các điều kiện logic, không khẳng định thứ tự triển khai nội bộ của mọi thư viện.

## 3. Trường hợp A — điểm vào đã được chặn, pointcut không khớp

Một số nguyên nhân thường gặp:

### 3.1. Sai signature, phạm vi hoặc kiểu phương thức

Pointcut dự kiến chọn `InvoiceService.submit(..)` nhưng code gọi `InvoiceService.preview(..)`; hoặc đối tượng nằm ở package khác; hoặc chữ ký có overload khác kiểu tham số/kiểu trả về. Tên method tương tự nhau chưa chứng minh cùng một signature.

Kiểm tra **tên lớp khai báo**, method name, parameter types, kiểu trả về, modifier, và loại join point đúng với DSL đang dùng. Với generic, Java có thể sinh **bridge method** để duy trì tương thích type erasure; compiler-generated member cũng cần được tính đến nếu matcher hoặc weaver quan sát các method đó.

### 3.2. Nhầm `call` và `execution`

Trong mô hình AOP hỗ trợ hai loại join point này:

- `call` là sự kiện tại **vị trí thực hiện lời gọi** (caller); cần vị trí gọi thuộc vùng được weave/quan sát.
- `execution` là sự kiện tại **việc thực thi thân phương thức** (callee); cần thân phương thức tương ứng được weave/quan sát.

Ví dụ ký pháp pointcut kiểu AspectJ, **chỉ để đọc ý nghĩa**, không phải chương trình Java độc lập:

```java
// Chọn vị trí gọi process(...) trong mã được weave:
call(* demo.Payments.process(..))

// Chọn nơi thực thi thân process(...) trong mã được weave:
execution(* demo.Payments.process(..))
```

Một ứng dụng chỉ instrument phương thức thực thi không tự nhiên tạo ra mọi join point `call` tại các caller, và ngược lại. Nếu nền tảng interception chỉ hỗ trợ method invocation trên proxy, cú pháp `call`/`execution` của công cụ weaving cũng không được mặc định hỗ trợ. Chẩn đoán phải bắt đầu từ tập join point **thực tế được AOP engine cung cấp**.

### 3.3. Điều kiện annotation, đối tượng hoặc đối số không đạt

Pointcut có thể bị ràng buộc bởi annotation, subtype, trạng thái runtime hoặc giá trị tham số. Chẳng hạn, matcher yêu cầu một annotation trên **implementation method**, còn phép tra cứu metadata lại được thực hiện trên **interface method** (hoặc chiều ngược lại). Annotation không phải lúc nào cũng được kế thừa/hiển thị theo cách mà người viết pointcut dự đoán.

Một pointcut có thể khớp với một overload nhưng không khớp overload khác; với runtime type conditions, cùng một method cũng có thể khớp ở lần gọi này và không khớp ở lần gọi khác. Khi điều tra, ghi rõ cả **static signature**, **runtime receiver**, **args**, loại pointcut và vị trí metadata được đọc.

### 3.4. Điều kiện loại trừ quá rộng hoặc thiếu điều kiện

Các phủ định kiểu `!within(...)`, phạm vi package, cflow và điều kiện loại trừ có thể khiến đúng method bị loại. Khi đọc biểu thức phức tạp, hãy tách nó thành từng vế và ghi kết quả từng vế đối với **một join point cụ thể**. Tối giản biểu thức trong phép thử nhỏ là cách xác minh hiệu quả, nhưng bản cuối phải giữ chính xác ranh giới nghiệp vụ.

### 3.5. Làm thế nào chứng minh thật sự là lỗi match?

Đặt một log chẩn đoán **ở biên interceptor**, **trước khi áp dụng điều kiện pointcut**, chẳng hạn:

```text
interceptor.enter method=preview
pointcut.evaluate method=preview result=false
target.enter method=preview
```

Đây là bằng chứng lời gọi đã đi qua interceptor; chỉ có điều advice không được chọn. Nếu bộ công cụ không phơi bày matcher trực tiếp, tạo một interceptor “bắt mọi lời gọi hỗ trợ” **tạm thời**, có phạm vi hẹp và dấu vết tối thiểu, rồi so sánh. Không cần dựa vào log bên trong advice đang mất tích.

## 4. Trường hợp B — lời gọi bỏ qua proxy/interception boundary

Một proxy theo kiểu **ủy quyền** thường giữ một đối tượng target bên trong. Caller gọi phương thức qua proxy để vào advice; caller gọi trực tiếp target thì đi thẳng tới nghiệp vụ.

### 4.1. Caller giữ tham chiếu target thô

```text
Client -> proxy.process()  -> interceptor -> advice (nếu match) -> target.process()
Client -> target.process() -------------------------------> target.process()
```

Cùng `target.process()` được thực thi ở cả hai đường; chỉ đường đầu tạo cơ hội cho proxy advice. Những nơi dễ giữ sai tham chiếu: tự tạo target bằng `new` rồi chuyển đi, expose target qua field/getter, đăng ký callback bằng target, hoặc một đối tượng khác đã nhận target trước khi lớp proxy được bọc vào.

Để xác minh, kiểm tra đối tượng thực sự mà **caller nhận** tại thời điểm gọi, không chỉ biến được đặt tên “proxy” ở đoạn cấu hình.

### 4.2. Self-invocation trên `this`

Trong một proxy ủy quyền, khi `proxy.outer()` chuyển tới `target.outer()`, bên trong `target.outer()` có thể gọi `this.inner()`. Lệnh `this.inner()` thực thi trên target, **không quay lại proxy**; advice trên `inner` có thể vắng mặt dù pointcut chọn đúng `inner`.

```text
proxy.outer()
  -> interceptor thấy outer
     -> target.outer()
        -> this.inner()   // gọi thẳng trong target, không qua proxy
```

Đây là tình huống kinh điển để tạo phép thử **cùng một phương thức**: gọi `proxy.inner()` có advice, còn gọi `proxy.outer()` làm `this.inner()` chạy mà không có advice của inner.

**Giới hạn quan trọng:** self-invocation **không luôn luôn** bypass AOP ở mọi cơ chế. Weaving tại `execution` của phương thức có thể chặn được lời gọi nội bộ vì instrumentation ở thân phương thức. Một proxy **kế thừa và override** cũng có semantics khác proxy **ủy quyền**; lời gọi ảo trên `this` có thể đi qua override nếu receiver thực sự là subclass proxy. Hãy kiểm tra cơ chế cụ thể và tham chiếu thực tế trước khi chẩn đoán.

### 4.3. Giới hạn kiểu proxy và method

Với `java.lang.reflect.Proxy` trong JDK, proxy thực hiện **các interface được khai báo**; một method chỉ có trên concrete class nhưng không có trong interface tương ứng không thể được gọi qua biến interface proxy đó. Phương thức `Object` (như `equals`, `hashCode`, `toString`) có các quy tắc dispatch riêng cần xét khi logging/đo lường.

Với proxy kiểu subclass/override, `final` method không thể được override, `private` method không thể bị override, `static` method không phải virtual dispatch, constructor không phải một method instance để override. Nhưng đừng khái quát rằng “method final không bao giờ được aspect can thiệp”: một **interface-based delegation proxy** vẫn có thể intercept lời gọi trước khi ủy quyền tới implementation có method final; cơ chế weaving vào bytecode cũng có khả năng khác.

Một số interceptor chặn ở biên method, không chặn field access, constructor execution hoặc call site. Nếu thao tác cần theo dõi nằm ngoài tập join point hỗ trợ, phải đổi cơ chế AOP hoặc thiết kế điểm chặn — việc sửa chuỗi ký tự pointcut có thể không giải quyết được.

### 4.4. Không nên đánh đồng “không qua proxy” với “không có weaving”

Với AOP dùng bytecode weaving, advice có thể được chèn vào `execution` của một phương thức. Khi đó lời gọi đến từ target, từ `this`, hoặc qua tham chiếu bất kỳ vẫn có thể gặp advice **nếu phương thức được weave và join point khớp**. Điều tra kiểu weaving cần kiểm tra lớp đã biến đổi, classloader, thời điểm load, cấu hình weave, thay vì chỉ kiểm tra có proxy hay không.

## 5. Ví dụ Java 17 chạy độc lập: tách hai lỗi chỉ bằng một phép thử

Ví dụ dưới đây tự xây một **interface delegation proxy** bằng Java Reflection. Đây là mô phỏng nguyên tắc AOP: `interceptor` là nơi quan sát lời gọi, điều kiện `method.getName().equals("detail")` đóng vai trò pointcut, và dòng `advice: detail` đại diện cho advice.

Lưu toàn bộ mã thành **`ProxyDiagnosisDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class ProxyDiagnosisDemo {
    public interface Work {
        void entry();
        void detail();
        void skipped();
    }

    public static final class WorkImpl implements Work {
        @Override
        public void entry() {
            System.out.println("target.entry");
            detail(); // self-invocation trên target, không qua proxy ủy quyền
        }

        @Override
        public void detail() {
            System.out.println("target.detail");
        }

        @Override
        public void skipped() {
            System.out.println("target.skipped");
        }
    }

    public static void main(String[] args) {
        Work target = new WorkImpl();
        Work proxy = (Work) Proxy.newProxyInstance(
            Work.class.getClassLoader(),
            new Class<?>[] { Work.class },
            (proxyInstance, method, methodArgs) -> {
                boolean matches = method.getName().equals("detail");
                System.out.println(
                    "interceptor: " + method.getName() + " match=" + matches
                );
                if (matches) {
                    System.out.println("advice: detail");
                }
                try {
                    return method.invoke(target, methodArgs);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        );

        System.out.println("1. proxy.skipped()");
        proxy.skipped();

        System.out.println("2. proxy.detail()");
        proxy.detail();

        System.out.println("3. target.detail()");
        target.detail();

        System.out.println("4. proxy.entry()");
        proxy.entry();
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac ProxyDiagnosisDemo.java
java ProxyDiagnosisDemo
```

Kết quả chuẩn:

```text
1. proxy.skipped()
interceptor: skipped match=false
target.skipped
2. proxy.detail()
interceptor: detail match=true
advice: detail
target.detail
3. target.detail()
target.detail
4. proxy.entry()
interceptor: entry match=false
target.entry
target.detail
```

**Diễn giải từng ca:**

1. `proxy.skipped()`: interceptor **đã chạy** (có dòng `interceptor`) nhưng matcher trả `false`; đây là **pointcut không khớp**.
2. `proxy.detail()`: interceptor chạy, matcher `true`, advice chạy; đây là **đường chuẩn** để chứng minh method `detail` *có thể* được advice bắt.
3. `target.detail()`: chỉ có `target.detail`, **không có `interceptor`**; cùng method `detail` có khả năng khớp nhưng caller đi **thẳng vào target**, tức bypass proxy.
4. `proxy.entry()`: interceptor thấy `entry`, matcher `false` nên không có advice trên `entry`; trong thân `entry`, `this.detail()` chạy trực tiếp trên target, do đó **không thấy** dòng interceptor riêng của `detail` dù nó là method được chọn khi gọi qua proxy. Một lần gọi này minh họa cả việc `entry` không khớp và self-invocation của `detail` bypass proxy.

Điểm thú vị: `method.invoke(target, ...)` chuyển cuộc gọi từ proxy sang target. Khi code của target gọi một method khác bằng `this`, nó không quay lại InvocationHandler. Mã tự động bỏ nguyên nhân do cấu hình môi trường cụ thể, giúp quan sát riêng cơ chế.

**Cần nhớ giới hạn ví dụ:** đây là `java.lang.reflect.Proxy` chuẩn của JDK, không phải một engine AOP hoàn chỉnh. Nó chỉ chứng minh quy tắc về điểm vào, matcher và self-invocation ở proxy ủy quyền. `Proxy` chỉ định nghĩa chặn lời gọi thông qua các interface được liệt kê; không thể dùng nó để suy luận trực tiếp về bytecode weaving.

## 6. Ma trận kiểm chứng phân biệt nguyên nhân

Đừng kiểm tra mỗi ca “đang lỗi”. Tạo bốn đường gọi trên **cùng method cần quan sát**, nếu kiến trúc cho phép:

| Ca thử | Đường gọi | Interceptor probe | Pointcut | Kết luận |
| --- | --- | --- | --- | --- |
| A | Gọi method khớp qua proxy | Có | true | Advice phải có cơ hội chạy; nếu không, kiểm tra advice chain/activation. |
| B | Gọi method không khớp qua proxy | Có | false | Chứng cứ của **pointcut mismatch**, không phải bypass. |
| C | Gọi method khớp trực tiếp target | Không | Không được đánh giá ở biên proxy | Chứng cứ của **bypass proxy** cho đường gọi đó. |
| D | Gọi qua proxy một method A, A gọi method B trên `this` của target | Có cho A; không có cho B | Tùy A, B | Nếu B khớp khi gọi trực tiếp proxy nhưng không được chặn ở đây: **self-invocation bypass**. |

Trên công cụ không cho log trực tiếp ở điểm intercept, có thể dùng một interceptor thử nghiệm cực hẹp với predicate `true` hoặc thống kê invocation trên boundary đang điều tra. Nếu một interceptor khác chạy còn advice mong muốn không chạy, đã có chứng cứ đường gọi không bỏ qua **toàn bộ** interception boundary; tuy nhiên cần đối chiếu nó có thực sự ở **cùng boundary** hay không.

**Không suy luận vòng tròn:** “pointcut đang đúng” chỉ dựa trên chữ ký mà chưa chứng minh nó match theo metadata/runtime của engine. “Có proxy” chỉ dựa trên việc đối tượng từng được tạo proxy mà chưa chứng minh caller đang giữ proxy tại thời điểm gọi.

## 7. Quy trình chẩn đoán từng bước

### Bước 1 — Chứng minh nghiệp vụ có thật sự được chạy

Đầu tiên ghi marker ngay trong thân method hoặc kiểm chứng side effect/kết quả. Nếu target chưa bao giờ chạy, “thiếu advice” có thể chỉ do luồng điều khiển không đến đó. Hạn chế marker gây side effect; tránh dùng `toString()` phức tạp của đối tượng tham số ngay trong interceptor vì việc format log có thể lại kích hoạt code bị advice.

Ghi ít nhất: `requestId`, `invocationId`, `thread`, signature, timestamp/sequence, và outcome. Cần phân biệt “một request” với “một method invocation” vì một request có thể gọi cùng method nhiều lần.

### Bước 2 — Xác định đúng caller và reference

Lần từ call site thực tế đến receiver thực tế:

- Caller đang nắm **proxy** hay **target gốc**? Reference có bị thay thế, cache, truyền qua callback hoặc tự tạo bằng `new` không?
- Đường gọi có đi qua interface/decorator/delegating wrapper được thiết kế không?
- Method được gọi từ bên ngoài hay từ chính target thông qua `this`?
- Receiver có nằm đúng phạm vi lớp/module/classloader mà cơ chế interception quản lý không?

Nếu đang dùng `java.lang.reflect.Proxy`, có thể kiểm tra:

```java
boolean jdkProxy = Proxy.isProxyClass(receiver.getClass());
```

Đoạn này là **mảnh minh họa**, giả sử `receiver` là đối tượng đã xác định; không phải chương trình độc lập. Kết quả `false` chỉ có nghĩa đối tượng ấy **không phải** lớp proxy do `java.lang.reflect.Proxy` tạo, không chứng minh nó không thuộc một loại proxy khác. Để kiểm tra identity, `receiver == expectedProxy` đáng tin hơn so sánh `equals()` do `equals()` có thể được override hoặc intercept.

### Bước 3 — Đặt “probe” trước pointcut, rồi so event và matcher

Các dấu vết tối thiểu theo thứ tự:

```text
caller: BEFORE call              (biết call site)
interceptor: ENTER method=...    (đã vào interception boundary)
pointcut: MATCH=true|false      (khi engine phơi bày phép đánh giá)
advice: ENTER advice=...         (advice được gọi)
target: ENTER method=...         (logic nghiệp vụ chạy)
```

Ghi các marker có correlation ID chung. Với một số engine, matcher được tính trước khi interceptor tạo ra, nên không phải lúc nào có đủ cả năm marker; khi đó dùng diagnostics về danh sách method được chặn, loại join point và bảng cấu hình proxy/weaving do engine cung cấp.

Cách đọc:

- Có `target`, **không có interceptor**, trong khi caller được xác nhận đã gọi đúng target: khả năng cao là bypass/không instrument; cần xác minh probe đã được gắn ở đúng boundary.
- Có `interceptor`, có `MATCH=false`: **pointcut không khớp**.
- Có `interceptor`, có `MATCH=true`, thiếu `advice`: kiểm tra advice registration, chain, điều kiện bật/tắt, lỗi trước advice.
- Có `advice` mà log dự kiến không thấy: lỗi có thể ở nhánh logic logging, appender/filter, ngoại lệ hoặc thời điểm flush; không phải tự động do AOP.
- Không có `target` lẫn `interceptor`: tìm nhánh nghiệp vụ trước; không thể từ đó kết luận pointcut.

### Bước 4 — Phân rã pointcut theo **join point thực tế**

Ghi rõ:

1. **Event kind:** `call` hay `execution`, hoặc method interception không phân biệt hai loại.
2. **Static signature:** declaring class, method name, return type, parameter types; xét override/bridge/overload.
3. **Runtime receiver:** proxy class, implementation class, classloader.
4. **Annotation/type/args:** đọc annotation trên interface hay implementation? Matcher xử lý generic/erasure ra sao? Có `args`/dynamic condition không?
5. **Loại trừ:** pointcut bị loại bởi `within`, `!within`, control flow hoặc điều kiện khác?
6. **Vùng weaving:** caller/callee có được weave không, lớp đã load trước khi cấu hình instrumentation có hiệu lực chưa?

Dùng một join point cụ thể làm đầu vào để thử từng vế; chỉ khi hiểu vì sao nó bị loại mới sửa pointcut chính thức. Không nới pointcut toàn hệ thống chỉ để “bắt cho được”, vì như vậy có thể tạo đệ quy logging/audit hoặc nhân đôi side effect.

### Bước 5 — Tái hiện bằng phép thử có kiểm soát

Với proxy ủy quyền, thử cùng một method qua reference proxy và reference target (nếu quyền truy cập/kiến trúc thử nghiệm cho phép). Giữ input, thread, trạng thái và feature flags tương đương; so **số lần target chạy**, **số lần interceptor vào** và **số lần advice thực thi**. Nếu proxy method chạy advice còn target raw chạy không, bộ matcher đã có ca kiểm chứng dương và lỗi ở **đường gọi**.

Với weaving, đổi cách kiểm tra: lấy bằng chứng lớp/method đã weave, join point kind và điều kiện matcher. “Không có proxy” không phải một lỗi trong kiến trúc weaving thuần túy.

### Bước 6 — Sửa đúng tầng và kiểm chứng regression

Sau khi xác nhận nguyên nhân, sửa đúng call graph, reference hoặc predicate; chạy lại cả positive và negative tests, kể cả lời gọi nội bộ, exception path và concurrency nếu ứng dụng có. Chỉ giữ diagnostics chi tiết trong phạm vi thích hợp; tracing mọi method vĩnh viễn có thể làm tăng chi phí và tự tạo các lời gọi lồng.

## 8. Các dạng bypass proxy thường gây nhầm lẫn

### 8.1. Self-invocation và direct target access

Hai nhánh này gần nhau nhưng khác vị trí: **self-invocation** sinh từ bên trong target; **direct target access** có thể do caller bên ngoài nắm sai reference. Cả hai tạo hiện tượng method chạy nhưng không vào proxy ủy quyền.

Cách sửa kiến trúc có thể là để caller chỉ nhận proxy, tách chức năng cần advice ra một component được gọi qua boundary hợp lệ, hoặc chọn weaving nếu yêu cầu thực sự là interception ở execution của mọi lời gọi. Không biến việc gọi vòng qua proxy thành sự phụ thuộc ngầm nếu thiết kế có thể thay đổi rõ ràng hơn.

### 8.2. Proxy chỉ bọc một phần API

Một interface proxy không cung cấp các method ngoài hợp đồng interface đó; một subclass proxy không thể override tất cả hình thức method. Chỉ thay pointcut từ `foo` sang `*` không tạo ra join point cho method mà proxy không thể chặn. Cần đối chiếu **proxy technology**, method visibility/modifiers và khả năng chặn thực tế.

### 8.3. Proxy lồng nhau hoặc bị bỏ qua ở một tầng

Hệ thống có thể có nhiều decorator/proxy liên tiếp: `Caller → Proxy A → Proxy B → Target`. Log xuất hiện ở A không chứng minh B cũng được đi qua; có thể A gọi trực tiếp target ở một nhánh. Đặt probe có tên boundary rõ ràng (`A.enter`, `B.enter`) và correlation ID để định vị chính xác tầng biến mất.

### 8.4. Callback, sự kiện và bất đồng bộ

Khi target tự đăng ký `this` làm listener hoặc callback, lời gọi về sau có thể đánh thẳng vào target. Khi nhiệm vụ chuyển qua thread khác, thiếu marker có thể do đường gọi khác, ngữ cảnh quan sát bị mất hoặc log chưa được gom đúng. Đừng vội sửa matcher trước khi dựng chuỗi dispatch và phân biệt receiver thực tế từng callback.

## 9. Trường hợp vẫn thiếu advice dù đã qua proxy và pointcut khớp

Đây là nhóm thứ ba nên giữ riêng trong điều tra:

- **Advice chưa được đăng ký/active:** aspect không được nạp, pointcut kết nối sai advice, hoặc thứ tự khởi tạo khiến cấu hình không áp dụng.
- **Chuỗi interceptor không tiếp tục:** một `around` ở ngoài có thể quyết định không gọi `proceed`/next; advice ở bên trong vì vậy không được thực thi.
- **Advice loại khác chạy trên nhánh khác:** `after returning` không chạy khi join point ném ngoại lệ; `after throwing` không chạy khi trả về bình thường. Logging sau `proceed` không ở `finally` cũng có thể biến mất khi có lỗi.
- **Dữ liệu runtime phân nhánh:** bộ matcher tĩnh cho phép method nhưng điều kiện runtime chặn một invocation riêng.
- **Quan sát bị sai:** logger bị filter, hai appender bị cấu hình ngược, log async bị chậm hoặc correlation ID không được truyền.

Một proxy được tạo không bảo đảm mọi advice sẽ được chạy. Quy trình chẩn đoán cần tách ít nhất ba sự kiện: **intercepted**, **selected**, **advice actually entered**.

## 10. Lựa chọn cách sửa theo bằng chứng

| Kết luận có bằng chứng | Hướng sửa thích hợp | Điều cần xác nhận lại |
| --- | --- | --- |
| Interceptor đã thấy method, matcher false | Chỉnh đúng pointcut signature, annotation lookup, loại join point, điều kiện runtime hoặc exclude | Negative case vẫn không được chọn; positive case được chọn |
| Caller nắm target thô | Chuyển tham chiếu tới đúng proxy tại call site/khâu lắp ghép | Một lời gọi qua proxy xuất hiện probe và advice; logic nghiệp vụ không đổi |
| `this.inner()` bỏ qua delegating proxy | Đổi cấu trúc call boundary; chuyển logic sang đối tượng được chặn, hoặc cân nhắc weaving | Gọi inner từ ngoài và từ luồng nghiệp vụ đều đáp ứng contract |
| Method không nằm trong khả năng proxy | Dùng API/proxy model có thể chặn method, hoặc weaving phù hợp | Method thực tế đã xuất hiện ở interception boundary |
| Cấu hình weaving bỏ sót lớp/call site | Sửa phạm vi weaving, classloader hoặc thời điểm instrumentation | Có bằng chứng bytecode/join point đúng môi trường thực thi |
| Matcher true, advice không vào | Kiểm tra registration, chain, `around`, runtime condition và exception path | Advice chạy ở đúng nhánh, đúng thứ tự và đúng số lần |
| Advice vào nhưng log không thấy | Sửa logic quan sát và propagation, không nới pointcut | Trace khớp sự kiện thực, không còn gap giả |

Khi sửa, ưu tiên thay đổi **nhỏ nhất ở tầng đã xác định**. Nới rộng pointcut khi lỗi là gọi trực tiếp target thường không có tác dụng; thay thế reference bằng proxy khi lỗi là matcher `false` cũng không giải quyết được điều kiện khớp.

## 11. Những bẫy suy luận và giới hạn cần nhớ

1. **“Method chạy thì advice phải chạy.”** Sai: chạy nghiệp vụ không chứng minh đi qua proxy hoặc được pointcut chọn.
2. **“Method nằm đúng package nên pointcut chắc chắn khớp.”** Sai: có thể khác method signature, join point kind, annotation source, runtime args hoặc exclude condition.
3. **“Có biến proxy là mọi caller đều dùng proxy.”** Sai: nhiều chỗ vẫn giữ target thô hay tham chiếu từ trước.
4. **“Self-invocation luôn bỏ qua AOP.”** Sai với các cơ chế weave tại method execution; nó phụ thuộc đường gọi và cách AOP được cài.
5. **“Method final không thể có advice.”** Không đúng với mọi proxy: delegation qua interface có thể chặn trước khi gọi final implementation; subclass override có hạn chế riêng.
6. **“Khi gọi qua proxy, matcher luôn được đánh giá ngay lúc runtime invocation.”** Một số framework tối ưu/chốt match trước, và có thể có phần runtime guard; nên quan sát ở tầng engine phù hợp.
7. **“Chỉ cần thấy interceptor của một method là interceptor của method khác chắc chắn cũng chạy.”** Sai: boundary, surface API và đường gọi có thể khác.
8. **“Log của advice không xuất hiện tức advice không chạy.”** Sai nếu log bị filter, nhánh code không đến lệnh log hoặc log bị gửi qua sink khác.
9. **“Hai proxy cùng tên type là cùng instance.”** Sai: dùng identity của object và correlation thay vì chỉ in class name hoặc `equals()`.
10. **“Tăng log trên mọi phương thức là cách debug an toàn.”** Không chắc: tracing có thể làm thay đổi thời gian, kích hoạt recursion hoặc sinh log quá lớn; giới hạn phạm vi và tránh gọi code nghiệp vụ từ logger.

## 12. Checklist kiểm nghiệm sau khi sửa

- [ ] Target method chắc chắn được thực thi trong trường hợp cần kiểm tra.
- [ ] Đã xác định receiver ở **call site thực tế** là proxy, target gốc hay một wrapper khác.
- [ ] Đã xác định rõ AOP dùng proxy/delegation, subclass override, weaving hay tổ hợp.
- [ ] Đã đặt hoặc thu thập bằng chứng ở **biên interception**, độc lập với advice đang thiếu.
- [ ] Đã kiểm chứng pointcut trên join point thực (event kind, chữ ký, receiver, annotation, runtime args).
- [ ] Đã thử đường **proxy + match true** và **proxy + match false**.
- [ ] Đã thử **direct target** và **self-invocation** nếu chúng thuộc kiến trúc.
- [ ] Đã tách lỗi “matcher true nhưng advice không thực thi” khỏi hai nhóm nguyên nhân chính.
- [ ] Đã kiểm chứng method modifiers, interface/subclass limitations và phạm vi weaving khi liên quan.
- [ ] Đã chạy lại positive/negative tests cùng các đường exception, callback, async nếu chúng xuất hiện trong bug.
- [ ] Không tạo thêm advice trùng, vòng gọi logging hoặc làm thay đổi business side effect.

**Kết luận kỹ thuật:** Phải quan sát **đường gọi có đến interception boundary hay không** trước khi đổ lỗi cho pointcut. Khi đã đến boundary, kiểm tra matcher và advice chain; khi chưa đến, lần theo receiver và cơ chế proxy/weaving. Hai nguyên nhân dẫn tới cùng một biểu hiện, nhưng nằm ở **hai tầng hoàn toàn khác nhau** và đòi hỏi hai cách sửa khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-006">Đọc ngăn xếp lời gọi có advice và mã được sinh</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc ngăn xếp lời gọi có advice và mã được sinh

## 1. Vì sao stack trace của chương trình AOP trông khác mã nguồn?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một lời gọi nghiệp vụ có thể được bao quanh bởi nhiều **advice**: ghi log, đo thời gian, xác thực, thử lại, kiểm soát giao dịch hoặc đổi cách xử lý ngoại lệ. Tùy cơ chế áp dụng AOP, runtime còn có thể tạo **proxy**, trình chuyển tiếp lời gọi, hàm phụ trợ hoặc mã đã được **weave** (đan vào bytecode). Vì vậy, ngăn xếp lời gọi có thể chứa những frame không có dòng mã trực tiếp tương ứng trong phần nghiệp vụ mà lập trình viên đang đọc.

Mục tiêu của bài này là biết **đọc đúng đường đi thực thi**, nhận ra ranh giới giữa target, advice và mã do công cụ tạo ra, rồi truy tìm nguyên nhân lỗi mà không đổ lỗi cho frame lạ xuất hiện trong trace.

Sau bài học, bạn có thể:

- Đọc stack trace theo chiều từ vị trí ném ngoại lệ về người gọi, đồng thời phân biệt ngoại lệ gốc với wrapper và cause.
- Nhận diện khả năng xuất hiện của frame advice, target, proxy, reflection, bridge, lambda và mã được weave.
- Hiểu vì sao cùng một ngoại lệ có thể tạo các dạng stack trace khác nhau tùy điểm quan sát và thứ tự advice.
- Dùng một chương trình Java 17+ độc lập để tái hiện và phân loại các frame thực tế.
- Sử dụng trace đầy đủ, thông tin proxy/weaving, vị trí bytecode và các tín hiệu chẩn đoán để xác minh giả thuyết.

**Giới hạn phạm vi:** đây là kiến thức AOP tổng quát. Java dynamic proxy trong ví dụ là một phương tiện học cơ chế chặn lời gọi và mã sinh lúc chạy. Những hệ AOP dùng weaving khi biên dịch hoặc khi tải lớp có thể hiện ra các frame khác nhau; phải kiểm tra cơ chế và phiên bản cụ thể trước khi kết luận.

## 2. Phân biệt “call stack đang chạy” với “stack trace của ngoại lệ”

Một **stack frame** biểu diễn một lần kích hoạt phương thức: có phương thức đang thực hiện, điểm quay về và dữ liệu thực thi liên quan. Khi phương thức A gọi B, thông thường frame B nằm phía trên frame A trong ngăn xếp đang hoạt động. Khi B trả về, frame B được gỡ khỏi ngăn xếp.

Có hai loại thông tin thường bị gọi chung là “stack”:

| Loại | Chụp tại thời điểm nào? | Trả lời câu hỏi gì? |
| --- | --- | --- |
| **Stack trace của Throwable** | Thường được ghi tại lúc đối tượng Throwable được tạo/điền trace, có thể thay đổi nếu gọi fillInStackTrace hoặc thay thế Throwable. | Ngoại lệ đã phát sinh qua đường gọi nào? |
| **Call stack hiện tại** | Tại thời điểm đọc Thread.getStackTrace, StackWalker hoặc thread dump. | Luồng này đang thực hiện điều gì ngay bây giờ? |

Điều này dẫn tới một bẫy rất phổ biến: trong catch của advice, **target có thể đã unwind khỏi ngăn xếp hiện tại**, nhưng target vẫn xuất hiện trong stack trace **được lưu trong ngoại lệ gốc**. Nếu advice chụp ngăn xếp mới bằng Thread.currentThread().getStackTrace(), trace đó mô tả thời điểm catch, không nhất thiết mô tả nơi lỗi xuất hiện.

Với Java, đối tượng StackTraceElement thường có:

- Tên class và phương thức (getClassName, getMethodName).
- Tên tệp và số dòng nếu thông tin có sẵn (getFileName, getLineNumber).
- Dấu hiệu phương thức native (isNativeMethod).
- Trong các JDK hiện đại, thông tin module/class loader có thể hiện trong cách biểu diễn frame.

**Không được giả định mọi frame đều có file và số dòng:** mã sinh lúc chạy, class thiếu debug information, native method hoặc lớp được biến đổi có thể xuất hiện dưới dạng Unknown Source hay không có số dòng. Tên class của proxy sinh động cũng có thể khác giữa JDK và các lần chạy.

## 3. Cách đọc một stack trace có advice

Xét sơ đồ minh họa, không phải log cố định cho mọi JVM:

~~~text
java.lang.IllegalArgumentException: invalid
    at example.OperationImpl.run(OperationImpl.java:...)
    at ...reflective invocation...
    at example.TraceHandler.invoke(TraceHandler.java:...)
    at ...$Proxy0.run(Unknown Source)
    at example.Application.main(Application.java:...)
~~~

Ở stack trace thường được in bởi Throwable.printStackTrace(), **frame trên cùng** thường chỉ tới vị trí Throwable ghi nhận trace; đi dần xuống là các bước gọi dẫn tới vị trí đó. Với ngoại lệ phát sinh từ target trong sơ đồ:

1. **OperationImpl.run**: mã nghiệp vụ tạo/ném IllegalArgumentException; bắt đầu phân tích nguyên nhân ở đây.
2. **Reflection**: tầng trung gian chuyển tiếp lời gọi bằng Method.invoke; sự hiện diện của nó không tự chứng minh đây là nơi tạo ra lỗi nghiệp vụ.
3. **TraceHandler.invoke**: lời gọi đã đi qua advice hoặc bộ chặn đang bao quanh target.
4. **$Proxy0.run**: phương thức chuyển tiếp do runtime tạo cho proxy; nó kết nối người gọi với handler.
5. **Application.main**: caller phía ngoài đã gọi qua proxy.

**Đọc theo chiều ngược khi cần dựng luồng:** người gọi ở dưới → proxy → advice → reflection → target ở trên. Ngược lại, để truy vết ngoại lệ từ nơi ném, đọc từ trên xuống dưới. Cùng một trace nhưng hai hướng giúp trả lời hai câu hỏi khác nhau.

### 3.1. Phân loại frame bằng vai trò, không chỉ tên

| Vai trò | Dấu hiệu nhận diện | Điều cần xác minh |
| --- | --- | --- |
| Target nghiệp vụ | Class/phương thức đã viết, nơi phát lỗi hoặc tính toán. | Lỗi xuất phát thật từ target, hay target chỉ bị gọi lại? |
| Advice | Hàm advice, handler, interceptor hoặc đoạn mã do công cụ weave vào luồng thực thi. | Advice chạy trước, quanh hay sau target; advice có đổi return/exception không? |
| Proxy/dispatcher | Tên lớp được tạo động hoặc lớp forwarding. | Caller có đi qua proxy không; proxy áp dụng cho những phương thức nào? |
| Reflection/method handle | java.lang.reflect, cơ chế invoke hay adapter. | Có wrapper ngoại lệ? Có cần đi tiếp vào cause để thấy lỗi gốc không? |
| Generated helper | Hàm phụ trợ, accessor, bridge, lambda, method sinh bởi bytecode tool. | Đây là mã tạo/cải biến nào, và có source map/line mapping không? |
| Caller | Mã gọi API/proxy/target. | Ai kích hoạt lời gọi, qua đường nào, có bỏ qua interception không? |

Các dấu hiệu này chỉ là **manh mối**, không phải quy tắc đặt tên chuẩn cho mọi công cụ. Một advice được weave trực tiếp vào target có thể **không có frame advice riêng** như một phương thức độc lập. Một proxy có thể không được đặt tên theo mẫu “$Proxy”. Một frame tên giống mã sinh chưa đủ để xác nhận nguồn gốc; dùng cơ chế áp dụng và bytecode nếu cần.

### 3.2. Join point và pointcut ảnh hưởng cách hiểu trace

**Join point** là vị trí/sự kiện thực thi mà AOP có thể can thiệp; **pointcut** xác định tập join point được chọn. Một hệ AOP có thể hỗ trợ join point khi **gọi** phương thức, khi **thực thi** phương thức, truy cập trường, xử lý ngoại lệ hoặc kiểu sự kiện khác; không có bảo đảm mọi hệ đều hỗ trợ các loại giống nhau.

Đặc biệt, phân biệt **vị trí call** với **vị trí execution**. Nếu target thực thi dưới một lớp proxy, trace có thể chứa proxy như một điểm chuyển tiếp. Nếu bytecode được weave vào target, advice có thể nằm ngay trong thân code đã biến đổi. Sự khác biệt về frame là hệ quả của mô hình thực thi, không nhất thiết là sai lệch chức năng.

## 4. Thứ tự advice quyết định các frame được quan sát

Giả sử có hai advice around: **TRACE** ghi dấu vết và **RETRY** gọi lại khi gặp lỗi tạm thời. Chúng có thể lồng nhau theo hai cách:

~~~text
TRACE ngoài RETRY:
caller
  TRACE.enter
    RETRY.enter
      target attempt 1 -> lỗi
      target attempt 2 -> thành công
  TRACE.exit

RETRY ngoài TRACE:
caller
  RETRY.enter
    TRACE.enter
      target attempt 1 -> lỗi
    TRACE.error
    TRACE.enter
      target attempt 2 -> thành công
    TRACE.exit
~~~

Một Throwable phát sinh ở lần đầu có thể ghi lại những tầng đang nằm trên đường gọi tới target. Nếu advice ở vòng ngoài xử lý và nuốt ngoại lệ, người gọi có thể chỉ nhận kết quả thành công và **không có stack trace lỗi được ném tiếp**. Nếu advice catch rồi **ném lại cùng đối tượng Throwable**, trace gốc thường vẫn mô tả vị trí trước đó; nếu advice tạo **Throwable mới**, frame trên cùng của ngoại lệ mới lại phản ánh nơi tạo/ghi stack mới. Việc đọc cả **cause chain** trở nên rất quan trọng.

Với advice before hoặc after, không nên suy luận từ tên loại advice rằng một frame chắc chắn xuất hiện trước hay sau một frame target trong stack trace. Chỉ những phương thức **đang có mặt trên ngăn xếp khi trace được ghi** mới có thể hiện diện trong trace đó. Ví dụ: một đoạn after chạy trong finally khi đang unwind có thể thực thi nhưng không xuất hiện trong stack trace được lưu trước đó của ngoại lệ.

Vì vậy, để xác định thứ tự thực:
- Ghi sự kiện ngay tại ranh giới **advice enter/exit**, **trước/sau proceed**, target enter và exception catch.
- Ghi một định danh lời gọi logic và số thứ tự attempt nếu có retry.
- Đối chiếu trace và log sự kiện. Đừng kết luận precedence từ thứ tự class trên đĩa hoặc tên phương thức.

## 5. Ví dụ Java 17+: nhận diện frame target, advice, proxy sinh động và caller

Ví dụ này sử dụng JDK dynamic proxy để mô phỏng một advice kiểu around. Target ném IllegalArgumentException. Handler gọi target bằng reflection, bắt InvocationTargetException, lấy **ngoại lệ gốc**, đọc stack trace của ngoại lệ, rồi ném lại chính ngoại lệ đó.

Lưu toàn bộ mã sau thành **AdviceStackTraceDemo.java**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AdviceStackTraceDemo {
    interface Operation {
        String run();
    }

    static final class OperationImpl implements Operation {
        @Override
        public String run() {
            throw new IllegalArgumentException("invalid");
        }
    }

    static final class TraceHandler implements InvocationHandler {
        private final Operation target;

        TraceHandler(Operation target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            System.out.println("advice: enter");
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapper) {
                Throwable original = wrapper.getCause();
                System.out.println("advice: caught "
                        + original.getClass().getSimpleName());
                printRelevantFrames(original);
                throw original;
            } finally {
                System.out.println("advice: exit");
            }
        }
    }

    private static void printRelevantFrames(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            String owner = frame.getClassName();
            String method = frame.getMethodName();
            if (owner.equals(OperationImpl.class.getName())) {
                System.out.println("frame: TARGET." + method);
            } else if (owner.equals(TraceHandler.class.getName())) {
                System.out.println("frame: ADVICE." + method);
            } else if (owner.contains("$Proxy") && method.equals("run")) {
                System.out.println("frame: GENERATED_PROXY." + method);
            } else if (owner.equals(AdviceStackTraceDemo.class.getName())
                    && method.equals("main")) {
                System.out.println("frame: CALLER." + method);
            }
        }
    }

    public static void main(String[] args) {
        Operation operation = (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] { Operation.class },
                new TraceHandler(new OperationImpl()));
        try {
            operation.run();
        } catch (IllegalArgumentException failure) {
            System.out.println("caller: " + failure.getMessage());
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac AdviceStackTraceDemo.java
java AdviceStackTraceDemo
~~~

**Stdout xác nhận khi chạy bằng JDK 21** (mã dùng cú pháp tương thích Java 17):

~~~text
advice: enter
advice: caught IllegalArgumentException
frame: TARGET.run
frame: ADVICE.invoke
frame: GENERATED_PROXY.run
frame: CALLER.main
advice: exit
caller: invalid
~~~

Chương trình in **các frame quan trọng đã lọc và gắn nhãn**, nên kết quả trên không phải nguyên văn Throwable.printStackTrace(). Phần phản chiếu Java có thể thêm các frame khác tùy phiên bản JDK. Bộ lọc cố ý không in số dòng hay package proxy để đầu ra minh họa ổn định.

### 5.1. Giải thích vì sao thứ tự đó xuất hiện

- Khi main gọi **operation.run()**, đối tượng đang được gọi là proxy triển khai interface Operation. Phương thức proxy sinh động chuyển tiếp vào InvocationHandler.
- **TraceHandler.invoke** tương đương một điểm bao quanh lời gọi: ghi “enter”, gọi method.invoke trên target rồi quan sát kết quả/lỗi.
- **Method.invoke** ném InvocationTargetException bọc Throwable do target ném. Handler dùng getCause để lấy IllegalArgumentException gốc.
- **failure.getStackTrace()** chứa đường đi lúc ngoại lệ gốc hình thành, từ OperationImpl.run ngược qua handler, proxy rồi main. Bởi vậy các frame được lọc theo thứ tự TARGET → ADVICE → GENERATED_PROXY → CALLER.
- Handler ném lại **cùng đối tượng** original. Khối finally in “advice: exit” khi handler kết thúc; main sau đó bắt được IllegalArgumentException với thông điệp “invalid”.

**Điểm tinh tế:** trong lúc printRelevantFrames(original) đang chạy, OperationImpl.run đã không còn thực thi trên call stack hiện hành, nhưng frame TARGET vẫn có trong **trace được lưu của ngoại lệ**. Nếu thay original.getStackTrace() bằng Thread.currentThread().getStackTrace(), những frame được nhìn thấy sẽ mô tả việc đang ở trong hàm chẩn đoán.

### 5.2. Ví dụ chứng minh điều gì và không chứng minh điều gì?

Ví dụ chứng minh rằng một lời gọi qua proxy có thể tạo đường đi gồm mã nghiệp vụ, tầng phản chiếu, một handler như advice và class được sinh động; bản trace lưu trong Throwable vẫn giúp truy ngược nguồn phát lỗi.

Ví dụ **không** tự động khẳng định rằng mọi hệ AOP đều dùng JDK proxy, Method.invoke, hay một class có tên chứa “$Proxy”. Nếu cơ chế đan bytecode vào lúc biên dịch hoặc tải lớp, sự xuất hiện và vị trí frame sẽ khác. Chỉ dùng chương trình này làm mô hình giải thích; muốn kết luận lỗi thực tế phải tái hiện bằng cơ chế AOP thực tế của ứng dụng.

## 6. Nhìn xuyên qua exception wrapper và chuỗi nguyên nhân

Khi đọc trace, hãy đi từ **lớp ngoại lệ quan sát được** về **root cause có ý nghĩa**, nhưng không bỏ qua wrapper: wrapper có thể cho biết tầng nào đã chuyển lỗi.

### 6.1. Reflection: InvocationTargetException

Khi một phương thức được gọi qua Method.invoke và phương thức đích ném exception, tầng reflection có thể báo InvocationTargetException. Trong ví dụ, catch ở handler nhận wrapper, sau đó lấy **wrapper.getCause()**. Stack trace của wrapper nói về việc reflection báo lỗi; stack trace của cause nói về nơi lỗi nghiệp vụ hình thành.

Có hai hành vi rất khác nhau:
- **throw original**: truyền tiếp cùng Throwable; vị trí trace gốc vẫn hữu ích.
- **throw new IllegalStateException("context", original)**: thêm ngữ cảnh và giữ nguyên nhân bên trong cause chain; trace ngoài bắt đầu ở tầng advice.

Lỗi thường gặp là tạo Throwable mới nhưng **không đặt cause**, dẫn đến mất đường truy vết nguyên nhân. Ngược lại, cứ ném lại cùng loại exception không phải lúc nào cũng đúng về hợp đồng API; phải giữ chính sách xử lý lỗi được yêu cầu.

### 6.2. Proxy và ngoại lệ checked không được khai báo

Với một số cơ chế proxy trên Java, nếu InvocationHandler phát ra một **checked exception** không được khai báo trong chữ ký phương thức interface, caller có thể nhận UndeclaredThrowableException. Khi đó cần đọc **cause** bên trong để tìm checked exception gốc. Đây là quy tắc liên quan đến hợp đồng ngoại lệ của JDK dynamic proxy; không áp đặt nó lên mọi hệ weaving.

### 6.3. Chuỗi cause, suppressed và phần rút gọn của trace

Khi in lỗi bằng Throwable.printStackTrace, có thể gặp:

~~~text
java.lang.IllegalStateException: higher-level failure
    at ...
Caused by: java.lang.IllegalArgumentException: invalid
    at ...
    ... 3 more
Suppressed: java.io.IOException: close failed
    at ...
~~~

- **Caused by** là nhánh nguyên nhân được liên kết bằng Throwable.getCause().
- **Suppressed** là các ngoại lệ phụ được đính vào Throwable, thường thấy khi dọn dẹp tài nguyên hoặc có lỗi phụ đồng thời với lỗi chính.
- **... N more** là cách rút gọn các frame cuối trùng với trace bao ngoài; nó không có nghĩa là n phương thức chạy thêm hay n lỗi nữa.

Để điều tra nghiêm túc, lưu **đầy đủ class, message, cause chain, suppressed chain và frame**, đồng thời giữ mối quan hệ giữa chúng. Một dòng “message” riêng lẻ hiếm khi đủ để kết luận lỗi thuộc target hay advice.

## 7. Mã sinh ra từ đâu và vì sao không có source tương ứng?

### 7.1. Proxy được sinh ở thời gian chạy

Runtime có thể tạo một class đứng giữa caller và target. Call stack có thể có tên như **jdk.proxy1.$Proxy0.run**; con số 0, tên module/package và vị trí nguồn chỉ mang tính minh họa, có thể thay đổi. Hãy dựa vào đối tượng thật và công cụ kiểm tra, không cố đoán từ đuôi số.

Trong ví dụ Java, có thể kiểm tra trực tiếp:

~~~java
boolean generatedProxy =
        java.lang.reflect.Proxy.isProxyClass(operation.getClass());
~~~

Đây là **đoạn minh họa để chèn trong main sau khi tạo operation**, không phải chương trình chạy độc lập. Hàm isProxyClass trả lời chính xác câu hỏi một Class có phải **JDK dynamic proxy class** hay không; nó không phát hiện mọi dạng proxy hoặc weaving của các công cụ khác.

### 7.2. Bytecode được weave vào target

Với weaving, công cụ có thể sửa bytecode đã biên dịch hoặc biến đổi nó trong quá trình tải lớp. Có ít nhất ba kiểu hình dáng trace khả dĩ:

1. Advice chạy qua một phương thức phụ trợ và tạo **frame riêng**.
2. Mã advice được chèn trực tiếp vào thân phương thức đích, nên stack trace chỉ hiện **frame của target** tại thời điểm lỗi.
3. Một lớp dispatch/trampoline hoặc method synthetic trung gian xuất hiện do cách compiler/weaver tổ chức code.

Không thể xác định chắc “advice không chạy” chỉ vì không thấy tên aspect ở stack trace. Phải kiểm tra bytecode, sự kiện tại ranh giới advice và cách engine chọn join point.

### 7.3. Lambda, bridge, accessor và runtime helper

Một trace Java cũng có thể chứa phương thức lambda, bridge do generics/covariant return, accessor do compiler, reflection, method-handle hoặc runtime helper. Chúng có thể xuất hiện khi **không có AOP**. Tên chứa ký hiệu đặc biệt hoặc không có file nguồn **không đủ** để quy tất cả thành aspect.

Khi không rõ frame thuộc về ai, đặt câu hỏi: “Class này được tải từ đâu, ai tạo/biến đổi nó, nó gọi tới phương thức nào, và có log/bytecode nào xác nhận?” Phân tích nhiệm vụ của frame thay vì chỉ nhìn tên.

## 8. Kỹ thuật xác minh từ stack trace đến bytecode

### Bước 1 — Giữ bản trace gốc

Chụp Throwable.printStackTrace đầy đủ, bao gồm cause và suppressed. Đừng xóa các frame “internal” trước khi tìm được điểm phát lỗi. Nếu ghi từ nhiều luồng, thêm tên luồng, định danh lời gọi logic và thời điểm. Hạn chế ghi dữ liệu nhạy cảm trong thông điệp ngoại lệ hoặc tham số.

### Bước 2 — Đánh dấu các ranh giới

Khoanh: **target throw site**, **advice bắt/chuyển đổi lỗi**, **proxy/dispatcher**, **caller**. Ghi rõ frame nào có line chính xác, frame nào không. Vẽ tuyến luồng từ caller tới target, sau đó vẽ tuyến ngoại lệ quay ngược ra các catch/finally.

### Bước 3 — Kiểm tra class và cơ chế áp dụng thực tế

Xác nhận object được caller sử dụng có phải proxy không; nếu không phải, xác định có weaving ở class đích không. Kiểm tra công cụ tạo proxy, classloader, module và phương thức được chọn. Mã gọi trực tiếp đối tượng gốc có thể khác với mã gọi qua proxy; trong cơ chế proxy cụ thể, lời gọi nội bộ trên cùng target có thể không đi qua interceptor.

### Bước 4 — So sánh có và không có advice

Chạy lại đầu vào xác định với advice bật/tắt theo từng aspect; ghi cùng định dạng trace và chuỗi sự kiện. Nếu tắt logging advice mà lỗi vẫn xảy ra, logging có thể chỉ đang quan sát lỗi từ target. Nếu lỗi biến mất khi đảo thứ tự aspect, kiểm tra phạm vi catch/proceed, chuyển đổi ngoại lệ và tác dụng phụ.

### Bước 5 — Đọc bytecode khi mã nguồn không giải thích frame

Nếu có tệp .class đã biên dịch, lệnh **javap** giúp xem bytecode và bảng số dòng:

~~~bash
javac -g AdviceStackTraceDemo.java
javap -c -l -p 'AdviceStackTraceDemo$TraceHandler'
~~~

Trong dòng javap, tên class lồng nhau được đặt trong dấu nháy đơn để ký tự **$** không bị shell mở rộng. Lệnh này đọc bytecode của class TraceHandler đã biên dịch trên đĩa, không tự động giải mã **class proxy được tạo trong bộ nhớ**.

Với JDK dynamic proxy, một số phiên bản JDK cho phép lưu các class được tạo qua tùy chọn **-Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true**. Đây là công cụ chẩn đoán phụ thuộc vào implementation và có thể tạo tệp .class trong thư mục chạy; thử trong thư mục tạm rồi kiểm tra tệp sinh thực tế. Nếu bytecode đã weave trong môi trường triển khai, lấy **class sau biến đổi** hoặc bản dump do engine hỗ trợ; javap trên tệp nguồn **trước weaving** có thể không khớp mã đang chạy.

Một frame ghi Unknown Source không nhất thiết là lỗi bản đồ nguồn. Nó có thể chỉ thiếu thông tin file/line. Nếu có file lineNumberTable, so sánh số dòng trace với **đúng bản class đã triển khai**, không dựa vào tệp nguồn đã thay đổi sau build.

### Bước 6 — Xác nhận thay đổi bằng cùng một ca tái hiện

Sau khi điều chỉnh pointcut, thứ tự hoặc cơ chế exception handling, chạy lại **đúng đầu vào và điều kiện**. So sánh stack và các bất biến nghiệp vụ: có còn đúng số lần target chạy, advice chạy, retry, log, tác dụng phụ hay không? Một trace “ngắn hơn” không tự nó chứng minh bug đã được sửa.

## 9. Phân biệt những trường hợp dễ gây ngộ nhận

### 9.1. Exception được ném lại sau khi advice đã chạy

Nếu advice thực hiện finally rồi ném lại cùng Throwable, stack trace gốc vẫn có thể dừng ở target và chứa frame advice tại **thời điểm Throwable được tạo**. Điều này không phủ nhận việc finally đã chạy sau đó; trạng thái thực thi và stack snapshot là hai sự kiện khác thời điểm.

Nếu advice tạo exception mới khi rethrow, trace ngoài có thể bắt đầu từ advice. Cần đọc cause thay vì chỉ nhìn frame đầu của wrapper và kết luận target không tham gia.

### 9.2. Lỗi xảy ra trong chính advice

Nếu advice ném lỗi **trước khi proceed**, target có thể hoàn toàn chưa chạy. Nếu advice ném lỗi **sau khi proceed trả về**, target có thể đã thành công, nhưng caller nhận lỗi từ advice. Đánh dấu số lần proceed, return của target và vị trí throw. Trường hợp handler “chỉ dùng để log” vẫn có thể làm lời gọi thất bại nếu chính việc ghi log ném exception.

### 9.3. Chuyển luồng hoặc bất đồng bộ

Khi target được chạy trong một thread khác, stack trace của exception trên worker mô tả **đường gọi trong worker**, không nhất thiết chứa caller ở thread khởi tạo tác vụ. Nếu caller quan sát lỗi qua Future hoặc cấu trúc kết quả bất đồng bộ, có thể có exception wrapper và trace riêng phía caller.

Đừng nối hai stack trace thành một chuỗi đồng bộ giả. Dùng **correlation id**, thông tin quan hệ tác vụ và thời điểm lên lịch/hoàn thành để ghép lại một **luồng nguyên nhân logic**. Nếu dùng ThreadLocal để theo dõi advice, phải kiểm tra cơ chế truyền và dọn state giữa các task; không giả định tự động được truyền qua mọi thread.

### 9.4. Nhiều advice và recursion

Advice gọi một hàm helper mà chính helper lại khớp pointcut có thể tái nhập hoặc sinh recursion không mong muốn. Trace có thể lặp các nhóm frame. Kiểm tra pointcut, độ sâu lần gọi, guard tái nhập và số lần proceed. Đừng xóa bớt frame lặp trước khi biết đó là recursion thực, retry có chủ ý hay chỉ thông tin hiển thị trùng.

### 9.5. Không xuất hiện frame advice

Những lời giải thích khả dĩ gồm: join point không được chọn; caller đi vòng qua proxy; advice đã bị loại bởi điều kiện runtime; bytecode được weave theo cách không tạo frame độc lập; frame đã bị cắt bởi hệ thống ghi log; hoặc stack được chụp **sau khi advice đã kết thúc**. Kiểm tra theo bằng chứng, không tuyên bố “aspect chưa chạy” từ một trace duy nhất.

### 9.6. Stack bị cắt ngắn, lineNumber âm hoặc tên class lạ

Nguồn ghi log có thể giới hạn số frame; runtime có thể thiếu số dòng cho mã sinh; một số tối ưu hóa hoặc cơ chế JVM có thể ảnh hưởng cách stack được cung cấp. So sánh trên cùng JDK và cấu hình khi tái hiện. Đừng dựng assertion hồi quy dựa trên tên như “$Proxy0” hoặc số dòng của code generated: chúng không phải giao diện ổn định để ứng dụng phụ thuộc.

## 10. Quy trình chẩn đoán một ca thực tế từ trace

Giả sử API nghiệp vụ thất bại nhưng log chỉ lộ ra một exception ở tầng proxy. Thực hiện quy trình ngắn sau:

1. **Xác nhận lỗi thực tế:** lấy type/message của exception caller nhận; giữ cause và suppressed. Phân biệt lỗi cuối cùng với một attempt lỗi đã được phục hồi.
2. **Tìm điểm phát sinh:** từ stack trace của cause sâu nhất có ý nghĩa, tìm frame mã nghiệp vụ hoặc advice gần vị trí throw. Đọc mã tại **đúng phiên bản class**.
3. **Gắn nhãn đường gọi:** đánh dấu caller, generated dispatcher/proxy, advice, reflection, target. Dùng trace theo chiều dưới → trên để biết ai gọi ai.
4. **Tái tạo thứ tự advice:** dùng sự kiện trước/sau proceed và catch/finally; xác định advice nào bao ngoài, advice nào bao trong. Không chỉ dựa vào vị trí frame khi đã có rethrow.
5. **Thử một thay đổi:** tắt từng aspect, siết pointcut, đổi precedence nếu công cụ cho phép, hoặc thay quy tắc exception handling. Ghi cả thay đổi của return, error, số lần gọi và side effect.
6. **Xác nhận cơ chế:** nếu chỉ lỗi trong môi trường đã weave, phải tái hiện với bản đã weave. Nếu chỉ lỗi khi qua proxy, so sánh class thực sự mà caller giữ với đối tượng target.
7. **Tạo kiểm thử hồi quy:** assert hợp đồng kết quả, type/cause exception và các bất biến có ý nghĩa; tránh assert nguyên văn mọi frame có thể thay đổi giữa JDK.

**Bảng chẩn đoán ví dụ:**

| Quan sát | Giả thuyết nên thử | Phép kiểm tra |
| --- | --- | --- |
| Trace có proxy nhưng không có target | Advice ném trước proceed, hoặc target không được gọi. | Đếm proceed và target-enter. |
| Throwable bên ngoài phát sinh ở advice | Advice wrap/rethrow lỗi hoặc tự ném lỗi. | Kiểm tra cause và nơi tạo Throwable. |
| Target xuất hiện hai lần theo log sự kiện | Retry hoặc vòng lặp do advice. | Gắn attempt id và đếm continuation. |
| Frame generated không có số dòng | Mã sinh thiếu thông tin nguồn. | Xác định class runtime; kiểm tra bytecode/source mapping. |
| Trên thread worker không thấy main của caller | Có ranh giới bất đồng bộ. | Nối theo correlation id và quan hệ tác vụ. |
| Bật một aspect là tên frame thay đổi | Chuỗi dispatch hoặc bytecode thay đổi. | So sánh stack đầy đủ, precedence, cấu hình áp dụng. |

## 11. Lỗi phổ biến khi đọc stack AOP và cách tránh

- **Đổ lỗi cho frame đầu tiên có tên lạ:** wrapper/dispatcher có thể nằm trên đường đi nhưng không phải nơi lỗi hình thành. Luôn đọc root cause và điểm throw.
- **Coi thứ tự in từ trên xuống là thứ tự lời gọi:** stack trace từ throw site về caller; muốn mô tả tuyến gọi, thường cần đọc phần frame theo chiều ngược.
- **Cho rằng catch luôn xuất hiện như một frame mới:** catch có thể chạy sau khi ngăn xếp target đã unwind; ngoại lệ giữ một bản trace cũ.
- **Không đọc cause/suppressed:** che mất target gốc hoặc lỗi dọn dẹp xảy ra khi advice kết thúc.
- **Gán mọi synthetic/helper cho AOP:** compiler, reflection, lambda và runtime cũng tạo code trung gian.
- **Coi tên class proxy và số dòng là chuẩn ổn định:** tên proxy, thứ tự frame nội bộ, file/line thay đổi theo implementation và build.
- **Dùng proxy sample để khẳng định lỗi trong weaving:** mô hình chặn khác nhau có thể có tập join point và cấu trúc bytecode khác nhau.
- **Chỉ kiểm tra stack mà không đo hành vi:** thiếu số lần proceed, retry, exception transform và side effect thì khó chứng minh lỗi tương tác.
- **Gộp call stack nhiều thread thành một stack duy nhất:** quan hệ async cần correlation/causality riêng.
- **Chỉnh sửa stack trace để giấu frame trước khi phân tích:** nếu lọc cho người đọc, luôn lưu bản gốc để đối chiếu.

## 12. Bài tập tự kiểm tra

**Bài tập 1 — Đổi điểm quan sát.** Trong ví dụ, thêm lệnh in kết quả Thread.currentThread().getStackTrace() ngay trong catch. Đánh dấu khác biệt so với original.getStackTrace(). Giải thích vì sao frame của target có thể biến mất khỏi ảnh chụp hiện tại nhưng vẫn ở trace của ngoại lệ.

**Bài tập 2 — Wrap exception.** Thay dòng ném lại trong handler bằng:

~~~java
throw new IllegalStateException("advice failed", original);
~~~

Đây là **một dòng thay thế**, không phải chương trình độc lập. Chạy lại cần sửa nhánh catch ở main để bắt IllegalStateException; in cả cause. Dự đoán: ngoại lệ ngoài bắt đầu ở handler, trong khi cause vẫn chỉ về OperationImpl.run. Kiểm chứng bằng trace thật.

**Bài tập 3 — Phân biệt lỗi nghiệp vụ và lỗi advice.** Di chuyển lệnh ném lỗi từ OperationImpl.run sang TraceHandler.invoke **trước method.invoke**. Dự đoán frame target và các dòng sự kiện sẽ thay đổi như thế nào? Thêm bộ đếm target-entry và chứng minh target không chạy.

**Bài tập 4 — So sánh mechanism.** Với engine AOP bạn đang dùng, dựng cùng một target/advice nhưng thay proxy interception bằng weaving nếu engine hỗ trợ. Thu trace thực tế, đánh dấu mỗi frame theo vai trò rồi giải thích những frame thêm/mất. Không ép hai cơ chế phải có trace giống nhau.

## 13. Checklist kết thúc phân tích stack trace

- [ ] Đã xác định đây là Throwable stack trace hay call stack hiện tại, và thời điểm chụp.
- [ ] Có bản trace đầy đủ cùng exception type, message, causes và suppressed.
- [ ] Đã tìm điểm throw thật; phân biệt target, advice, dispatch/generated và caller.
- [ ] Biết join point/pointcut và cơ chế AOP dùng trong chương trình; không suy diễn từ tên class.
- [ ] Đã đối chiếu precedence, số lần proceed, catch/rethrow/wrap và side effect với trace.
- [ ] Nếu có generated frame, biết mã được tạo/biến đổi lúc nào và phiên bản class nào đang chạy.
- [ ] Với xử lý async, có correlation id hoặc dấu vết nhân quả giữa các thread/task.
- [ ] Kết luận dựa trên MRE hoặc phép thử có điều kiện xác định; kiểm thử hồi quy bảo vệ hợp đồng nghiệp vụ.

**Điểm cốt lõi:** stack trace AOP là **dấu vết của đường thực thi thực tế tại thời điểm Throwable ghi nhận trace**, có thể chứa code do runtime hoặc compiler tạo. Tên frame lạ là manh mối để lần theo ranh giới interception; bằng chứng quyết định nằm ở nơi phát sinh exception, chuỗi cause, thứ tự advice/proceed và cơ chế áp dụng AOP thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-007">Đặt điểm dừng trong advice, mã gốc và cơ chế gọi tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt điểm dừng trong advice, mã gốc và cơ chế gọi tiếp trong AOP

## 1. Tại sao cần đặt breakpoint ở ba nơi?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **pointcut** chọn các **join point**, còn **advice** bổ sung hành vi tại các join point đó. **Weaving** kết hợp aspect với mã chương trình ở thời điểm biên dịch, sau biên dịch hoặc khi nạp lớp. Việc debug trở nên khó hơn một lời gọi Java thông thường vì nhiều advice có thể bao lấy cùng một join point.

Muốn giải thích chính xác một ca lỗi, cần quan sát riêng:

1. **Trong advice:** điều kiện pointcut có khớp không, các biến bind có giá trị gì, nhánh nào đang chạy?
2. **Trong mã gốc:** thân phương thức nghiệp vụ đã chạy hay chưa, nhận tham số nào, trả kết quả gì hoặc ném lỗi gì?
3. **Ở cơ chế gọi tiếp (`proceed(...)`):** `around advice` có cho phép thực thi tiếp không, chuyển đối số nào, chạy bao nhiêu lần và trả về hoặc ném ngoại lệ ra sao?

**Breakpoint trong `around advice` không tự chứng minh thân mã gốc đã chạy.** Một `around` có thể return/throw trước khi gọi `proceed`. Ngược lại, breakpoint trong mã gốc không nói lên mọi advice đã chạy trước đó.

Các ví dụ sử dụng **AspectJ code-style** để minh họa cơ chế AOP dệt mã nói chung. File `.aj` cần compiler/weaver hỗ trợ; debugger có thể hiển thị các helper synthetic do bytecode weaving sinh ra, tùy IDE và phiên bản công cụ.

## 2. Những vị trí breakpoint quan trọng

| Nơi đặt breakpoint | Ý nghĩa chính | Cần kiểm tra thêm |
| --- | --- | --- |
| Dòng đầu `before advice` | Pointcut chọn join point và advice bắt đầu | Mã gốc đã chạy hay chưa |
| Dòng đầu `around advice` | Luồng đã tới lớp bao quanh | `proceed` có được gọi không |
| Ngay **trước** `proceed` | Giá trị/nhánh trước lúc tiếp tục | Còn advice nào nằm bên trong |
| Trong thân phương thức gốc | Thân gốc thật sự được thực thi | Tham số có bị thay đổi không |
| Ngay **sau** `proceed` | Đường tiếp tục đã trả về bình thường | Kết quả có bị biến đổi tiếp không |
| `catch`/`finally` trong around | Theo dõi đường lỗi và dọn dẹp | Lỗi bị ném lại hay bị chặn |
| `after returning` | Join point kết thúc bình thường | Giá trị trả về đã được bind đúng |
| `after throwing` | Join point kết thúc bằng exception phù hợp | Lỗi thoát từ tầng nào |
| `after` kiểu finally | Join point kết thúc theo cả hai đường | Đừng nhầm với `after returning` |
| Dòng sau lời gọi ở caller | Caller nhận điều khiển và kết quả | Không chạy nếu exception thoát ra |

`before`, `after returning`, `after throwing` và `after` không có cú pháp `proceed` để gọi tiếp như `around`. Các lời khuyên này có vai trò vòng đời khác nhau. Khi nhiều advice cùng khớp, thứ tự thật sự tùy **precedence** và mô hình triển khai; không suy đoán thứ tự chỉ từ cách viết source.

## 3. `proceed(...)` không chỉ là lời gọi Java thường

Trong **AspectJ code-style**, `proceed(...)` là cấu trúc đặc biệt bên trong `around advice`. Nó tiếp tục phần còn lại của phép tính ở join point, có thể đi qua advice khác rồi mới tới thân gốc. Với nhiều around, `proceed` của advice ngoài thường kích hoạt advice trong.

```text
caller
  |
around A: trước proceed
  |
proceed A --------------------+
                              |
                       advice B (nếu có)
                              |
                        mã gốc
                              |
                           return
                              |
around A: sau proceed <-------+
  |
caller
```

Sơ đồ minh họa một chuỗi có thứ tự xác định, không bảo đảm thứ tự của mọi hệ thống.

Các trường hợp đặc biệt:

- **`proceed` trả về bình thường:** lệnh ngay sau nó trong advice được thực thi và có thể đọc/sửa kết quả.
- **`proceed` ném ngoại lệ:** lệnh sau nó bị bỏ qua trừ khi có `catch`; `finally` vẫn chạy theo ngữ nghĩa Java.
- **Không có `proceed`:** thân join point tiếp theo có thể bị bỏ qua hoàn toàn vì around đã return hoặc throw.
- **Có nhiều `proceed`:** thân gốc có thể chạy nhiều lần trong một lần gọi của caller, nếu chuỗi advice cho phép.
- **`proceed` nhận tham số khác:** giá trị thân gốc nhận được có thể khác tham số lúc caller gọi.

Một debugger sử dụng **Step Into** tại `proceed` có thể đi vào helper, closure hoặc advice bên trong trước khi đến thân gốc. Vì vậy, phải đặt breakpoint trực tiếp trong thân gốc để kiểm chứng phần tính toán đã chạy.

## 4. Ví dụ hoàn chỉnh có thể biên dịch và debug

Ví dụ sử dụng Java 17+, AspectJ compiler (`ajc`) tương thích và `aspectjrt.jar`. `before`/`after` chọn `main` còn `around` chọn `Worker.doubleValue`; các loại advice này không cùng tranh một join point nên thứ tự minh họa rõ ràng.

### 4.1. `src/demo/Main.java`

```java
package demo;

public class Main {
    public static void main(String[] args) {
        int input = args.length == 0 ? 3 : Integer.parseInt(args[0]);
        System.out.println("main entered");

        Worker worker = new Worker();
        int result = worker.doubleValue(input);

        System.out.println("main result=" + result);
    }
}

class Worker {
    int doubleValue(int value) {
        System.out.println("worker value=" + value);
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        return value * 2;
    }
}
```

### 4.2. `src/demo/TraceAspect.aj`

```aspectj
package demo;

public aspect TraceAspect {
    before(): execution(void demo.Main.main(String[])) {
        System.out.println("before main");
    }

    after(): execution(void demo.Main.main(String[])) {
        System.out.println("after main");
    }

    pointcut doubling(int value):
        execution(int demo.Worker.doubleValue(int))
        && args(value);

    int around(int value): doubling(value) {
        System.out.println("around enter=" + value);

        try {
            int result = proceed(value);
            System.out.println("around returned=" + result);
            return result;
        } finally {
            System.out.println("around finally");
        }
    }
}
```

Giải thích:

- `execution(void demo.Main.main(String[]))` chọn việc **thực thi** `main`, do đó `before main` chạy lúc vào và `after main` chạy khi kết thúc.
- `execution(int demo.Worker.doubleValue(int))` chọn việc **thực thi** phương thức `doubleValue`. `args(value)` bind tham số vào advice.
- `proceed(value)` tiếp tục phép tính và trả về số nguyên nếu thành công. Dòng `around returned=...` chỉ xuất hiện trên đường return bình thường.
- Khối `finally` bảo đảm dòng `around finally` được thực thi khi `proceed` trả về hoặc ném exception trong cơ chế unwind thông thường.
- Nhánh `value < 0` cung cấp ca lỗi để thử exception breakpoint và xác nhận dòng sau `proceed` bị bỏ qua.

### 4.3. Biên dịch và chạy có thông tin debug

Giả sử `ajc` đã được cài trên PATH và `aspectjrt.jar` đặt ở thư mục chạy lệnh.

**Windows PowerShell:**

```powershell
ajc -17 -g -preserveAllLocals -showWeaveInfo -d out -classpath "aspectjrt.jar" src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out;aspectjrt.jar" demo.Main
```

**Linux/macOS:**

```bash
ajc -17 -g -preserveAllLocals -showWeaveInfo -d out -classpath aspectjrt.jar src/demo/Main.java src/demo/TraceAspect.aj
java -cp "out:aspectjrt.jar" demo.Main
```

Ý nghĩa các cờ: `-g` sinh debug symbols (line, vars, source); `-preserveAllLocals` cố giữ các biến cục bộ để inspect; `-showWeaveInfo` giúp xác nhận advice được dệt vào lớp/điểm nối nào. Nếu hệ thống build của IDE tạo một bản class Java chưa dệt, cần sửa cấu hình để Debug dùng **output của `ajc`**.

**Stdout kỳ vọng** khi chạy mặc định, input `3`:

```text
before main
main entered
around enter=3
worker value=3
around returned=6
around finally
main result=6
after main
```

Với `demo.Main -1`, các dòng **stdout** trước khi lỗi thoát ra là:

```text
before main
main entered
around enter=-1
worker value=-1
around finally
after main
```

JVM còn in stack trace `IllegalArgumentException: negative` lên stderr; dòng stderr có thể hiển thị xen kẽ stdout tùy terminal. Hai dòng `around returned=...` và `main result=...` không xuất hiện vì `proceed` không trả về bình thường.

## 5. Quy trình breakpoint theo thứ tự dễ kiểm chứng

Đặt các điểm dừng vào **dòng có lệnh thực thi** trong mã nguồn:

| Mã | Vị trí | Giá trị hoặc hành vi cần quan sát |
| --- | --- | --- |
| BP1 | `before`: dòng in `before main` | Advice `before` bắt đầu |
| BP2 | `Main.main`: dòng in `main entered` | Bắt đầu mã gốc `main` |
| BP3 | `around`: dòng in `around enter=...` | Around match; `value=3` |
| BP4 | `around`: dòng `int result = proceed(value);` | Trước khi gọi tiếp; tham số `value=3` |
| BP5 | `Worker.doubleValue`: dòng in `worker value=...` | Thân gốc được gọi với `value=3` |
| BP6 | `around`: dòng in `around returned=...` | `proceed` quay về; `result=6` |
| BP7 | `around`: dòng in `around finally` | Dọn dẹp ở cả đường return/throw |
| BP8 | `Main.main`: dòng in `main result=...` | Caller nhận `result=6` |
| BP9 | `after`: dòng in `after main` | Kết thúc join point `main` |

Dùng **Resume** liên tiếp trên đường input dương để kiểm tra BP1 → BP9. Ở mỗi breakpoint, ghi **thread**, **call stack**, **frame đang chọn**, **binding trong advice**, **tham số tại thân gốc** và **giá trị trả về**. Biến `value` trong advice và biến `value` của phương thức gốc không phải cùng local variable dù cùng tên.

Với input `-1`, bật **Exception Breakpoint** trên `IllegalArgumentException`: breakpoint trong `Worker` cho thấy dữ liệu gây lỗi; BP6 và BP8 không dừng; BP7 và BP9 vẫn chạy khi tiếp tục. Nếu debugger hỗ trợ cả “caught” và “uncaught”, cần chọn chế độ mong muốn để tránh bỏ sót lỗi bị advice xử lý.

### 5.1. Step Into, Step Over và Step Out

- **Step Into** ngay tại `proceed(value)`: có thể chuyển vào helper/around khác, không nhất thiết chạm `Worker` ngay. Giữ BP5 để xác minh.
- **Step Over** tại `proceed(value)`: vẫn thực thi toàn bộ lời gọi tiếp và có thể dừng tại BP5; nếu trả về bình thường, dừng ở lệnh tiếp theo trong advice.
- **Step Out** trong `Worker`: có thể trở lại helper hoặc frame của aspect, không hẳn trở thẳng về `Main`.
- **Continue/Resume**: giảm phụ thuộc vào debug mapping của các synthetic frame.
- **Conditional breakpoint**: chỉ dừng khi `value == 7`, hoặc hit count thứ N, hữu ích với join point được gọi rất nhiều lần.

Nếu debugger không hỗ trợ breakpoint ở `.aj` hoặc hiển thị *unverified*, hãy kiểm tra plugin/integration, debug symbols, thời điểm dệt, line number table và artifact đang chạy trước khi chỉnh pointcut.

## 6. `call` và `execution` tạo breakpoint ở vị trí khác nhau

Trong AspectJ:

- `call(int demo.Worker.doubleValue(int))`: chọn vị trí **gọi phương thức** trong `Main.main`, miễn mã caller nằm trong phạm vi dệt.
- `execution(int demo.Worker.doubleValue(int))`: chọn thời điểm **thân phương thức được thực thi** trong `Worker`.

```text
Main.main:
  result = worker.doubleValue(input)
           ^ shadow cho call
                     |
                     v
Worker.doubleValue:
  ^ shadow cho execution
  [breakpoint ở dòng thân phương thức]
```

Nếu caller không được dệt, `call` có thể không áp dụng dù `execution` trong callee đã được dệt. Hai pointcut có thể khác nhau ở số join point và ngữ nghĩa; không đổi loại chỉ để dễ debug. Nếu advice ở `call` bị dừng trước khi `Worker` vào thân, đó là hợp lý.

## 7. Khi around có nhiều tầng, bỏ qua hoặc nhân đôi gọi tiếp

### 7.1. Nhiều around và thứ tự precedence

Giả sử aspect A là lớp ngoài và B là lớp trong theo precedence đã xác minh:

```text
Caller
  -> around A: before
       -> proceed A
            -> around B: before
                 -> proceed B
                     -> thân gốc
                 <- around B: after
       <- around A: after
  <- Caller
```

`proceed` của A vào B trước khi đến gốc. Breakpoint ở synthetic helper không có nghĩa thân gốc đã bị gọi lặp; cần đếm lần vào `Worker`. Khi có nhiều advice, xác minh cấu hình precedence và trình dệt trước khi kết luận.

### 7.2. Short-circuit: không gọi `proceed`

Đoạn sau **thay thế riêng** cho around trong ví dụ, không thêm cạnh around cũ:

```aspectj
int around(int value):
    execution(int demo.Worker.doubleValue(int)) && args(value) {
    if (value == 0) {
        return 0;
    }
    return proceed(value);
}
```

Nếu input `0`, breakpoint nhánh return dừng nhưng breakpoint ở thân gốc không dừng, do advice đã trả kết quả mà không tiếp tục. Điều này có thể là thiết kế hợp lệ (cache/short-circuit) hoặc lỗi nghiệp vụ; phải xác nhận yêu cầu.

### 7.3. Đổi đối số khi gọi tiếp

```aspectj
int around(int value):
    execution(int demo.Worker.doubleValue(int)) && args(value) {
    return proceed(value + 1);
}
```

Nếu caller truyền `3`, thân gốc nhận `4` và trả `8`. Khi debug, đối chiếu tham số ở ba nơi: caller, around và thân gốc. Trong AspectJ code-style, tham số `proceed(...)` gắn với binding của advice; các phong cách khai báo advice khác có quy tắc ánh xạ tham số khác nhau, không sao chép cơ học.

### 7.4. Gọi `proceed` nhiều lần

```aspectj
int around(int value):
    execution(int demo.Worker.doubleValue(int)) && args(value) {
    int first = proceed(value);
    int second = proceed(value);
    return first + second;
}
```

Với `value=3`, thân `Worker` được gọi hai lần, mỗi lần cho `6`, kết quả caller là `12`. Nếu tính toán có side effect như ghi cơ sở dữ liệu hoặc thanh toán, điều này có thể không chấp nhận được. Breakpoint trong mã gốc và số lượt hit cho biết tình huống gọi tiếp nhiều lần.

## 8. Gỡ lỗi exception và đường trả về

```text
around -> proceed(...)
             |
             +-- bình thường --> dòng sau proceed -> finally
             |
             +-- throw -------> catch nếu có -> finally
```

Nếu exception thoát khỏi `proceed`, dòng ngay sau `proceed` không chạy. Nếu around **bắt lỗi và trả về giá trị thay thế**, các advice khác ở tầng ngoài có thể quan sát kết thúc bình thường; vì vậy, ý nghĩa “returning” hoặc “throwing” phụ thuộc join point và precedence chính xác.

Quy trình điều tra: đặt exception breakpoint tại vị trí `throw` gốc, kiểm tra `catch`/`finally` trong around, kiểm tra `after throwing` và `after returning` nếu có, cuối cùng xác nhận caller nhận kết quả hay exception. Chỉ nhìn breakpoint sau call ở caller có thể bỏ qua lỗi đã được xử lý trong advice.

## 9. Vì sao debugger không dừng như kỳ vọng?

### 9.1. Advice không dừng nhưng mã gốc vẫn chạy

Kiểm tra (1) chữ ký và loại `call`/`execution`, overload, `within`, `if`, `cflow` và binding; (2) thông tin `-showWeaveInfo` xác nhận có woven shadow; (3) output class thực thi có phải phiên bản **đã dệt**; (4) debugger đã bind breakpoint vào class thật; (5) class loader và phạm vi cấu hình load-time weaving; (6) điều kiện động của pointcut có đang false.

### 9.2. Advice dừng nhưng mã gốc không dừng

Có thể quanh join point short-circuit trước `proceed`, quanh tầng trong không gọi tiếp, `proceed` ném exception, hoặc breakpoint đang nằm ở overload/class không được thực thi. Đặt ba breakpoint trước `proceed`, trong thân gốc và sau `proceed` để tách nguyên nhân.

### 9.3. Thân gốc dừng nhiều lần

Kiểm tra số lần `proceed`, đệ quy, retry, vòng lặp, các thread/caller khác nhau và breakpoint ở phương thức dùng chung. Đối chiếu **call stack và thread** thay vì kết luận ngay weaving đã chèn trùng advice.

### 9.4. Debugger nhảy vào synthetic helper hoặc dòng không như source

Weaver có thể sinh helper/closure và biến đổi dòng bytecode. Điều chỉnh bộ lọc Step Into nếu IDE hỗ trợ, dùng breakpoint cố định, kiểm tra **line numbers** và **bytecode sau dệt**:

```bash
javap -classpath out -c -l -p demo.Worker
javap -classpath out -c -l -p demo.Main
```

`-c` in bytecode; `-l` in thông tin debug theo line/local; `-p` hiển thị thành viên không public. Đừng dựa vào tên helper cụ thể vì trình dệt và phiên bản có thể thay đổi. Nếu mã nguồn khác bytecode đang chạy, rebuild/reweave rồi restart debugger khi cần.

## 10. Weaving time, source mapping và các biến ngữ cảnh

| Cách dệt | Điều quan trọng khi debug |
| --- | --- |
| **Compile-time** | `ajc` tạo class đã dệt; IDE phải chạy đúng output đó |
| **Post-compile** | Runtime phải dùng artifact đã dệt, không dùng bản class gốc còn trong classpath |
| **Load-time** | Weaver/agent phải được kích hoạt trước khi class liên quan được load và dùng đúng class loader |

Nguồn `.aj` tồn tại trong IDE chưa bảo đảm advice đã được weave. Giữ **source, symbols, class output và debugger session** từ cùng một lần build. Hot-swap Java thông thường có thể không thay thế được các biến đổi cấu trúc của bytecode dệt.

Trong AspectJ advice, có thể xem metadata join point bằng `thisJoinPointStaticPart`, `thisJoinPoint.getSignature()`, `thisJoinPoint.getArgs()`, `thisJoinPoint.getThis()`, `thisJoinPoint.getTarget()` tùy loại join point. `getThis()` hoặc `getTarget()` có thể `null` ở ngữ cảnh thích hợp. Không nhầm `this` của instance aspect với đối tượng nghiệp vụ của join point. Expression evaluation có thể thực thi getter/`toString()` gây side effect, I/O hoặc khóa; ưu tiên inspect biến cục bộ thuần.

## 11. Debug an toàn ở đường nóng và đa luồng

AOP có thể chặn những phương thức được gọi hàng triệu lần; dừng mọi breakpoint làm thay đổi lịch luồng và có thể che race condition. Sử dụng **conditional breakpoint**, **hit count**, **thread filter**, **logpoint/tracepoint** (nếu IDE hỗ trợ), và **exception breakpoint** để tập trung vào ca lỗi. Biểu thức điều kiện trong debugger nên thuần, đơn giản và không làm thay đổi đối tượng.

Khi lỗi phụ thuộc timing, kết hợp dữ liệu của debugger với trace/log/JFR/profiler ít can thiệp hơn. Với công việc bất đồng bộ, đừng mặc định các bước ở hai thread thuộc cùng một chuỗi gọi; luôn kiểm tra thread ID và dữ liệu định danh phiên xử lý.

## 12. Các sai lầm thường gặp

- **“Break trong around chứng tỏ thân gốc đã chạy.”** Sai: `proceed` có thể chưa được gọi.
- **“Step Over tại `proceed` bỏ qua thân gốc.”** Sai: lời gọi tiếp vẫn thực thi; chỉ khác cách trình bày stepping.
- **“Step Into tại `proceed` bắt buộc đi thẳng vào phương thức gốc.”** Sai: có thể vào advice khác hoặc helper tổng hợp.
- **“Dòng sau `proceed` luôn chạy.”** Sai nếu có exception hoặc đường điều khiển khác.
- **“`after` đồng nghĩa `after returning`.”** Sai: `after` kiểu finally còn chạy khi ném lỗi.
- **“`call` và `execution` chọn cùng một vị trí.”** Sai: caller và callee khác nhau.
- **“Synthetic frame là bằng chứng mã gốc chạy nhiều lần.”** Không; cần đếm lượt vào thân gốc.
- **“Dùng `this` trong aspect để xác định target là đủ.”** Không; phải xét ngữ cảnh join point và đúng stack frame.
- **“Không hit breakpoint chứng tỏ pointcut sai.”** Có thể chưa dệt, runtime chạy nhầm artifact hoặc thiếu source debug mapping.
- **“Đặt breakpoint vô điều kiện ở mọi advice luôn tốt.”** Quá nhiều điểm dừng có thể làm méo timing và khó nhìn luồng thực.

## 13. Checklist thực hành

1. Xác định pointcut thực sự muốn debug và loại `call`/`execution`.
2. Bảo đảm class đã dệt, đúng nguồn và có debug symbols; kiểm tra `-showWeaveInfo`.
3. Đặt breakpoint tối thiểu **trước `proceed`**, **trong mã gốc**, **sau `proceed`**.
4. Bổ sung breakpoint ở caller trước/sau lời gọi; đánh dấu ca return, throw và finally.
5. Kiểm tra biến bind trong advice và đối số thật của thân phương thức; xác nhận stack frame/thread.
6. Với nhiều around, kiểm tra precedence, tầng gọi tiếp và số lần `proceed` thực thi.
7. Nếu debugger stepping lạ, đọc log dệt và bytecode rồi đối chiếu đúng artifact.
8. Khi lỗi đa luồng hoặc đường nóng, dùng điều kiện/hit count và trace ít can thiệp.
9. Kết luận bằng bằng chứng: **advice nào khớp, thân gốc chạy bao nhiêu lần, kết quả/exception đi đâu**.

**Kết luận:** để hiểu luồng AOP, cần kết hợp breakpoint ở advice, thân mã gốc và vị trí `proceed`, rồi kiểm chứng cả đường trả về bình thường lẫn đường ngoại lệ. Khi thấy mã tổng hợp hoặc thứ tự lạ, hãy xem source map, precedence và bytecode đã dệt trước khi kết luận chương trình chạy sai.

## Tài liệu tham khảo

- AspectJ Programming Guide — ngữ nghĩa advice và `proceed`: https://eclipse.dev/aspectj/doc/latest/progguide/semantics-advice.html
- AspectJ Language — các loại advice, `call` và `execution`: https://eclipse.dev/aspectj/doc/latest/progguide/language.html
- AspectJ Development Guide — `ajc`, `-g`, `-preserveAllLocals` và `-showWeaveInfo`: https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html
- AspectJ annotation-style advice — khác biệt ánh xạ tham số `proceed`: https://eclipse.dev/aspectj/doc/latest/adk15notebook/ataspectj.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-008">Chẩn đoán đệ quy, advice chạy nhiều lần hoặc sai thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán đệ quy, advice chạy nhiều lần hoặc sai thứ tự trong Aspect-Oriented Programming (AOP)

## 1. Vấn đề cần hiểu

Trong AOP, một `aspect` đóng gói hành vi cắt ngang (ghi log, kiểm tra quyền, đo thời gian, audit...), một `pointcut` chọn những `join point` cần tác động, còn `advice` là đoạn xử lý được thực thi trước, sau hoặc bao quanh join point đó. Cơ chế áp dụng advice có thể là weaving vào bytecode, biên dịch có weaving, nạp lớp có weaving, hoặc một tầng interception khác. Vì thế, số lần một phương thức nghiệp vụ được gọi **không nhất thiết bằng** số lần một advice chạy.

Ba nhóm triệu chứng thường bị lẫn với nhau:

- **Đệ quy hoặc tái nhập (recursion/reentrancy):** advice hoặc phương thức được advice gọi tiếp một đường thực thi lại khớp pointcut, tạo vòng lặp hoặc lời gọi lồng nhau không mong muốn.
- **Advice chạy nhiều lần:** một tác vụ nhìn từ bên ngoài chỉ xảy ra một lần, nhưng có nhiều join point phù hợp, nhiều advice cùng khớp, advice gọi `proceed` nhiều lần, hoặc có cơ chế gọi lại.
- **Advice sai thứ tự:** thứ tự trước/sau của các aspect khác dự kiến do precedence, nesting của `around`, dạng `before`/`after`, ngoại lệ, hoặc phụ thuộc vào thứ tự không được bảo đảm.

Mục tiêu chẩn đoán là tìm **join point nào đã khớp, advice nào chạy, vì sao chạy và quan hệ gọi lồng nhau giữa chúng**. Không vội kết luận lỗi chỉ từ việc xuất hiện hai dòng log giống nhau.

## 2. Mô hình thực thi để đọc đúng dấu vết

Các thành phần phải phân biệt:

| Thành phần | Câu hỏi chẩn đoán |
| --- | --- |
| Join point | Sự kiện thực thi nào được quan sát: gọi hàm (`call`), thực thi hàm (`execution`), khởi tạo đối tượng, truy cập trường...? |
| Pointcut | Biểu thức nào chọn sự kiện đó, ở phạm vi lớp/gói/phương thức nào? |
| Advice | `before`, `after`, `after returning`, `after throwing` hay `around`? Có bao nhiêu khai báo áp dụng? |
| Weaving/interception | Mã bị tác động tại vị trí gọi hay vị trí thực thi? Những lớp nào thực sự được instrument/weave? |
| Control flow | Có lời gọi lồng, gọi lại, vòng lặp, callback hoặc chuyển luồng không? |
| Precedence | Nếu nhiều advice cùng áp dụng, quy tắc nào xác lập thứ tự bao ngoài và bên trong? |

Ví dụ một vòng `around` lồng nhau với thứ tự ngoài là Trace, trong là Guard:

```text
Trace.before
  Guard.before
    Target.execute
  Guard.after
Trace.after
```

Các dòng `after` xuất hiện **ngược thứ tự** `before` là hành vi đúng của cơ chế stack/unwind, không phải dấu hiệu advice sắp xếp sai. Nếu `Target.execute` ném ngoại lệ, chỉ các khối `finally` hoặc advice loại phù hợp mới nhất thiết hoàn tất; `after returning` không chạy trên đường ngoại lệ.

Đặc biệt với AOP có join point `call` và `execution`: một lời gọi thông thường có thể kích hoạt cả advice bắt sự kiện gọi ở caller **và** advice bắt sự kiện chạy thân phương thức ở callee. Đây là hai join point khác nhau dù cùng tên phương thức. Cần kiểm tra code ở cả hai phía có được weave hay không.

## 3. Chẩn đoán đệ quy hoặc advice tái nhập

### 3.1. Advice gọi hàm phụ nhưng hàm phụ lại khớp pointcut

Mẫu điển hình: pointcut bắt mọi phương thức trong `demo.service`; advice gọi `AuditService.record` nằm trong chính phạm vi đó.

```java
// Ví dụ cú pháp AspectJ để phân tích; cần toolchain hỗ trợ weaving,
// không phải một tệp Java biên dịch độc lập bằng javac.
public aspect BroadAuditAspect {
    before(): execution(* demo.service..*.*(..)) {
        demo.service.AuditService.record("entered");
    }
}
```

Nếu `AuditService.record` cũng khớp `execution(* demo.service..*.*(..))` thì luồng có thể là:

```text
PaymentService.pay
  -> BroadAuditAspect.before
       -> AuditService.record
            -> BroadAuditAspect.before
                 -> AuditService.record
                      -> ... (StackOverflowError)
```

Dấu hiệu: stack trace xen kẽ frame của advice và hàm phụ; cùng một signature xuất hiện nhiều lần, độ sâu tăng liên tục; số dòng log tăng đột biến.

**Cách sửa gốc:** thu hẹp pointcut theo đúng nghiệp vụ, đặt logger/audit sink ngoài vùng bị bắt, hoặc loại trừ rõ vùng hạ tầng. Chẳng hạn:

```java
public aspect NarrowAuditAspect {
    before(): execution(* demo.service.PaymentService.pay(..)) {
        System.out.println("payment entered");
    }
}
```

Một điều kiện loại trừ hợp lý khi vẫn cần pointcut rộng:

```java
pointcut tracked():
    execution(* demo.service..*.*(..))
    && !within(demo.service.AuditService);
```

`within(T)` xét **vị trí khai báo (lexical)** của join point; nó hữu ích để bỏ thực thi nằm trong lớp `T`. Nhưng `!within(MyAspect)` **không đủ** để chặn lời gọi từ advice đi vào một phương thức của lớp nghiệp vụ khác rồi quay lại pointcut: thực thi phương thức nghiệp vụ ấy vẫn được khai báo ngoài aspect.

### 3.2. Đệ quy do logic nghiệp vụ, advice chỉ ghi nhận

Có trường hợp `A.f()` gọi lại `A.f()` hoặc hai hàm `A.f() → B.g() → A.f()`. Advice trước mỗi lần `execution` sẽ chạy đúng một lần cho **mỗi mức đệ quy**. Không sửa bằng cách tắt advice nếu lỗi thực sự ở điều kiện dừng hoặc chu trình gọi của nghiệp vụ.

Cách nhận diện: stack trace chủ yếu lặp lại hàm nghiệp vụ; khi vô hiệu hóa advice quan sát, vòng gọi vẫn tồn tại. Đếm riêng số **lần vào join point** và số **lần chạy advice** để tránh quy trách nhiệm nhầm.

### 3.3. Ngăn ghi log lồng nhau bằng control-flow pointcut

Khi mục đích chỉ là đo một tác vụ cấp ngoài, có thể loại sự kiện nằm **bên dưới** một execution đã khớp:

```java
public aspect OutermostTraceAspect {
    pointcut task():
        execution(* demo.service.PaymentService.pay(..));

    before(): task() && !cflowbelow(task()) {
        System.out.println("outermost payment");
    }
}
```

`cflowbelow(task())` nhận diện join point trong luồng thực thi động bên dưới một `task()` đã hoạt động. Phủ định nó chỉ ghi nhận lần vào ngoài cùng trong cùng control flow. Đổi lại, các lời gọi lồng nhau có chủ đích sẽ bị bỏ khỏi log. Đây là biện pháp **lọc quan sát**, không tự chữa vòng đệ quy nghiệp vụ hay vòng advice gọi helper. `cflow` và `cflowbelow` phụ thuộc vào đặc tính runtime của công cụ AOP đang dùng; xác nhận công cụ hỗ trợ trước khi áp dụng.

### 3.4. Reentrancy guard bằng trạng thái theo luồng

Trong một interception tùy biến, có thể tránh việc chính hoạt động logging kích hoạt lại logging bằng guard theo thread, với `try/finally` bắt buộc để giải phóng cờ. Ví dụ Java 17 sau **chạy độc lập**, mô phỏng wrapper advice (không phải ví dụ weaving):

```java
public final class ReentryGuardDemo {
    private static final ThreadLocal<Boolean> ACTIVE =
            ThreadLocal.withInitial(() -> false);

    static void advised(Runnable action) {
        if (ACTIVE.get()) {
            action.run(); // Vẫn cho hành vi nghiệp vụ tiếp tục.
            return;
        }

        ACTIVE.set(true);
        try {
            System.out.println("enter");
            action.run();
        } finally {
            System.out.println("exit");
            ACTIVE.remove();
        }
    }

    static void audit() {
        advised(() -> System.out.println("audit"));
    }

    public static void main(String[] args) {
        advised(() -> {
            System.out.println("work");
            audit();
        });
    }
}
```

Chạy:

```bash
javac ReentryGuardDemo.java
java ReentryGuardDemo
```

```text
enter
work
audit
exit
```

Guard này chặn **wrapper logging lặp lại trong cùng thread**, không chặn `action.run()` tự gọi đệ quy vô hạn; muốn chữa lỗi đó phải sửa call graph và điều kiện dừng. Luôn `remove()` trong `finally` để không giữ cờ trên thread được tái sử dụng. Không coi `ThreadLocal` là cơ chế lan truyền ngữ cảnh qua tác vụ bất đồng bộ: công việc trên thread khác có trạng thái riêng, cần một chính sách truyền context rõ ràng.

## 4. Vì sao advice chạy nhiều lần?

| Nguyên nhân | Quan sát điển hình | Kiểm tra và hướng xử lý |
| --- | --- | --- |
| Một request đi qua nhiều join point | Một request ID, nhiều signature hoặc nhiều tầng delegate | Ghi rõ signature và depth; chỉ bắt tầng cần đo |
| `call` và `execution` cùng được bắt | Cùng hành vi nhưng event kind khác | Kiểm tra loại join point và vị trí weave; chọn event phù hợp |
| Nhiều aspect/advice khớp một join point | Cùng signature, khác advice/aspect ID | Liệt kê advice và pointcut; tách trách nhiệm hoặc đặt precedence |
| Advice `around` gọi `proceed()` nhiều lần | Target chạy hai lần từ cùng một advice invocation | Đếm số `proceed()`, kiểm tra retry và nhánh điều kiện |
| Có retry, callback, listener, vòng lặp hoặc lời gọi lồng | Join point thực sự bị gọi lặp | Theo dõi từng attempt, phân biệt request ID và invocation ID |
| Weaving/interception được đăng ký hoặc áp dụng lặp | Trace xuất hiện nhiều lớp wrapper giống nhau | Kiểm tra cấu hình build, agent, classloader và điểm instrument |
| Hàm được biên dịch thành nhiều đường gọi (ví dụ bridge/delegation) | Khác signature descriptor, tên gần giống | Kiểm tra bytecode/signature, test theo join point thực thay vì tên hiển thị |

**Điểm mấu chốt của `around`:** nhiều API AOP cho phép advice quyết định có tiếp tục vào mục tiêu hay không; gọi `proceed` hai lần có thể cố ý thực thi lại nghiệp vụ, kèm hai lần side effect. Trong chính sách retry, phải kiểm tra idempotency và chỉ retry ở trường hợp được cho phép. Nếu không có chủ đích retry, `proceed` thường chỉ được gọi một lần trên nhánh thành công.

Không suy luận rằng hai log trùng nhau chứng minh cùng một advice chạy hai lần. Một advice có thể ghi log lúc vào và lúc ra; hai instance aspect cũng có thể in nội dung giống nhau; hệ thống log có thể có hai appender. Cần thêm nhãn `phase`, `aspect`, `advice`, `event-kind`, `invocation-id` và số thứ tự sự kiện để phân biệt.

## 5. Vì sao thứ tự advice không như dự kiến?

### 5.1. Precedence và nesting

Khi nhiều aspect cùng áp dụng tại một join point, thứ tự không nên suy ra từ tên class, thứ tự khởi tạo, hay vị trí file trong dự án. Trong AOP kiểu AspectJ, có thể khai báo precedence tường minh (mảnh cú pháp sau đặt trong một `aspect`):

```java
declare precedence: SecurityAspect, TraceAspect, MetricsAspect;
```

Theo thứ tự này, aspect có precedence cao hơn được áp dụng như lớp bao ngoài: `before` của nó chạy sớm hơn; phần kết thúc của `after`/`around` xảy ra muộn hơn khi unwinding. Không nhầm “Trace trước Metrics” ở đầu vào với “Trace trước Metrics” cả lúc thoát.

Nếu công cụ AOP khác không hỗ trợ `declare precedence`, cần dùng cơ chế ordering/priority được nó công bố và viết phép thử về thứ tự thực thi. Khi không có quy tắc xác định, thứ tự giữa các aspect độc lập có thể không được bảo đảm; không dựa vào thứ tự vô tình thấy trong một lần chạy.

### 5.2. Ví dụ Java 17 về thứ tự `around`

Chương trình độc lập sau mô phỏng chuỗi advice lồng nhau; nó minh họa nguyên lý thứ tự mà không cần thư viện AOP:

```java
public final class AdviceOrderDemo {
    interface Action {
        void run();
    }

    static Action around(String name, Action next) {
        return () -> {
            System.out.println(name + " before");
            try {
                next.run();
            } finally {
                System.out.println(name + " after");
            }
        };
    }

    public static void main(String[] args) {
        Action target = () -> System.out.println("business");
        Action chain = around("Trace", around("Guard", target));
        chain.run();
    }
}
```

Chạy:

```bash
javac AdviceOrderDemo.java
java AdviceOrderDemo
```

```text
Trace before
Guard before
business
Guard after
Trace after
```

Giải thích: `Trace` bao ngoài `Guard`, nên “before” đi từ ngoài vào trong, còn `finally` chạy khi stack trở ra. Nếu phát hiện “after” ngược “before”, trước tiên hãy so với mô hình nesting này.

### 5.3. Advice sau, ngoại lệ, và tác động của `around`

- `before`: chạy trước join point; nếu nó ném ngoại lệ, mục tiêu có thể không được thực thi.
- `after returning`: chỉ chạy khi join point kết thúc bình thường; không dùng để bảo đảm dọn tài nguyên sau lỗi.
- `after throwing`: chạy khi join point kết thúc bằng ngoại lệ thuộc kiểu được bắt.
- `after` (kiểu finally): áp dụng khi join point thoát bình thường hoặc bất thường.
- `around`: có thể bao quanh, bắt/chuyển ngoại lệ, không gọi `proceed`, hoặc gọi nhiều lần; do đó quyết định cả số lần vào mục tiêu và phần advice nào được tới.

Trên đường ngoại lệ, một log “end” đặt sau `proceed()` mà không ở `finally` có thể biến mất. Đây là nhánh thực thi khác, chưa đủ chứng minh “sai thứ tự”. Cần phân tích cả thứ tự trên đường thành công và trên đường lỗi.

### 5.4. Trường hợp nhiều advice nằm trong cùng aspect

Thứ tự tương đối có thể chịu quy tắc riêng của ngôn ngữ/weaver, bao gồm loại advice và vị trí khai báo. Khi nghiệp vụ yêu cầu một thứ tự bắt buộc, tránh dựa vào những chi tiết dễ thay đổi: gom thao tác phụ thuộc nhau vào một `around` với cấu trúc `try/finally` rõ ràng, hoặc tách thành các aspect có precedence được khai báo và kiểm chứng bằng test.

## 6. Quy trình chẩn đoán có thể lặp lại

1. **Cố định ca tái hiện.** Ghi input, số lần gọi dự kiến, đường thành công/thất bại, số thread và cấu hình weaving. Tắt retry ngoài ý muốn trong phép thử cơ sở nếu có quyền kiểm soát.
2. **Phân biệt các mức đếm.** Ghi `requestId` cho tác vụ ngoài, `invocationId` cho từng lần vào join point, `adviceId` cho từng advice, `thread` và `depth` cho nesting. Một request có thể hợp lệ chứa nhiều invocation.
3. **Lấy dấu vết ít can thiệp.** Ghi signature tĩnh (loại lớp, tên phương thức, event kind), advice name, phase và timestamp/sequence. Tránh gọi `toString()` tùy ý lên tham số: nó có thể gây side effect hoặc lại khớp pointcut.
4. **Dựng call graph.** Nếu cùng ID có độ sâu tăng mãi, lần theo chu kỳ `advice → helper → join point → advice` hay chu kỳ nghiệp vụ. Nếu depth không tăng mà invocation tăng, ưu tiên xem vòng lặp, retry, callback, `proceed` lặp hoặc nhiều request.
5. **Liệt kê điểm khớp thực tế.** So từng advice với pointcut của nó: `execution` hay `call`, package/type pattern, `within`/`withincode`, điều kiện tham số, control flow, cấu hình weave. Đừng chỉ đọc tên phương thức in trên log.
6. **Kiểm tra thứ tự xác định.** Vẽ các wrapper `around`; xác nhận precedence và thứ tự `before`/`after`. Thử ít nhất một nhánh thành công và một nhánh ném ngoại lệ.
7. **Thử sửa tối thiểu.** Thu hẹp pointcut, loại trừ helper hoặc điều chỉnh precedence; so số invocation và side effect trước/sau. Nếu sửa lỗi đệ quy, xác nhận logic nghiệp vụ hoàn tất đúng.
8. **Bật lại điều kiện thực tế.** Thử tải đồng thời, callback bất đồng bộ, retry chủ đích và cùng phiên bản weaving khi chạy thật. Ghi nhận khác biệt thread/classloader nếu có.

Một dạng trace hữu ích:

```text
seq=01 req=R7 inv=I1 depth=0 thread=T1 event=execution:PaymentService.pay aspect=Trace advice=around phase=enter
seq=02 req=R7 inv=I1 depth=0 thread=T1 event=execution:PaymentService.pay aspect=Guard advice=around phase=enter
seq=03 req=R7 inv=I2 depth=1 thread=T1 event=execution:AuditService.record aspect=Trace advice=before phase=enter
seq=04 req=R7 inv=I1 depth=0 thread=T1 event=execution:PaymentService.pay aspect=Guard advice=around phase=exit
seq=05 req=R7 inv=I1 depth=0 thread=T1 event=execution:PaymentService.pay aspect=Trace advice=around phase=exit
```

Các dòng chỉ minh họa **định dạng dấu vết**, không phải output của mã Java bên trên. `I2` giúp nhận ra một join point khác xuất hiện bên trong `I1`. Nếu liên tục xuất hiện `I3`, `I4`,... ở depth tăng, cần điều tra tái nhập. Nếu `I1` xuất hiện với hai `aspect` ở cùng depth, đó có thể chỉ là nhiều advice hợp lệ.

## 7. Chẩn đoán sâu theo cơ chế AOP

### 7.1. Vị trí weave và định nghĩa join point

- Với `execution`, advice chạy tại việc **thực thi thân phương thức**; mọi nơi gọi vào cùng thân được weave đều có thể quan sát.
- Với `call`, advice gắn vào **vị trí lệnh gọi**; caller không được weave thì sự kiện call ở caller đó sẽ không bị advice quan sát.
- Một lớp được instrument từ nhiều tầng, hoặc đoạn gọi qua decorator/delegate, có thể tạo các wrapper lồng nhau. Cần đối chiếu class thực tế, classloader và điểm instrument.
- Những phương thức cầu nối (bridge), overload, hàm sinh bởi compiler hay chuyển tiếp nội bộ có thể làm số event lớn hơn trực giác. Không “dedupe” bằng tên phương thức đơn thuần; so đầy đủ signature và event kind.

Với weaving trực tiếp vào bytecode, lời gọi nội bộ từ một phương thức sang phương thức khác vẫn có thể gặp join point `execution` của phương thức đích nếu đã weave. Với các mô hình interception chỉ chặn ở biên lời gọi, lời gọi nội bộ có thể không đi qua interceptor. Phải xác định **cơ chế đang dùng** thay vì áp dụng một quy tắc chung cho mọi AOP.

### 7.2. Đa luồng và bất đồng bộ

Sự kiện hoàn tất trên thread khác có thể xen vào trace của thread đầu tiên. Timestamp một mình không mô tả thứ tự nhân quả giữa hai thread. Nên có correlation ID được truyền chủ động và sequence theo mỗi invocation; nếu cần xác lập thứ tự xuyên thread, theo dõi sự kiện schedule/start/complete hoặc quan hệ parent/child.

Guard theo `ThreadLocal` chỉ chặn tái nhập trong thread hiện tại. Không dùng nó để bảo đảm duy nhất một lần trên toàn request phân tán qua nhiều thread. Những đường chạy dùng async, callback hoặc executor đòi hỏi chính sách truyền context và ranh giới scope riêng.

### 7.3. Khi điều tra StackOverflowError

Lấy stack trace đầy đủ khi có thể; tìm đoạn frame lặp ngắn nhất. Nếu có helper logging/audit trong chu kỳ, tạm thay nó bằng xuất dữ liệu tối thiểu không gây side effect để cô lập vòng. Tránh thêm log trên mọi lời gọi khi lỗi nặng: chính instrumentation mới có thể làm tràn stack sớm hơn hoặc tạo vòng mới. Đối với tiến trình thật, giới hạn sampling và kích thước log, tái hiện trong môi trường có kiểm soát trước khi thay đổi pointcut.

## 8. Chiến lược sửa theo đúng nguyên nhân

| Nguyên nhân đã xác nhận | Sửa ưu tiên | Kiểm tra sau sửa |
| --- | --- | --- |
| Advice gọi helper nằm trong pointcut | Thu hẹp pointcut; loại trừ helper hoặc chuyển sink ra ngoài phạm vi | Một nghiệp vụ tạo số event mong muốn; không có frame lặp |
| Đệ quy nghiệp vụ thật | Sửa điều kiện dừng hoặc chu trình phụ thuộc | Nghiệp vụ có kết quả đúng, không còn đệ quy vô hạn |
| Quan sát cả `call` lẫn `execution` | Chọn join point đúng nhu cầu hoặc phân biệt hai nguồn đếm | Số lần ghi tương ứng với định nghĩa metric |
| `around` gọi `proceed` lặp | Chỉ gọi đúng số lần cần thiết; tách retry có kiểm soát | Side effect không nhân đôi ngoài thiết kế |
| Nhiều advice thiếu thứ tự | Khai báo precedence hoặc một pipeline rõ thứ tự | Trace vào/ra đúng nesting trên cả success và exception |
| Dọn dẹp sai nhánh ngoại lệ | Dùng `finally` hoặc advice tương ứng | Resource cleanup xuất hiện ở cả hai nhánh |
| Interception/weaving bị lặp | Sửa vị trí cấu hình/đăng ký và vòng build/load | Một lớp chỉ được gắn số interceptor dự kiến |
| Async làm mất correlation | Truyền context rõ qua task boundary | Trace ghép đúng request và invocation |

Tránh cách “sửa” bằng cách xóa toàn bộ aspect quan sát, bỏ qua mọi lần gọi lồng nhau hoặc dùng biến `static boolean` chung cho toàn tiến trình: các cách này dễ che lỗi thật và khiến concurrent request ảnh hưởng lẫn nhau. Guard chỉ dành cho thao tác có nguy cơ tái nhập, phải có scope rõ và giải phóng trong `finally`.

## 9. Phép thử xác nhận

Nên kiểm thử đủ các trường hợp sau, với kỳ vọng mô tả bằng số lượng **join point**, **lần chạy từng advice**, **lần thực thi nghiệp vụ** và thứ tự sự kiện:

1. **Một lời gọi thành công, không lồng:** mỗi advice khớp một lần theo định nghĩa, `around` bao ngoài đúng thứ tự, một side effect.
2. **Một lời gọi ném ngoại lệ:** xác định advice nào được phép chạy; `finally` vẫn dọn tài nguyên, lỗi không bị vô tình nuốt.
3. **Lời gọi lồng có chủ đích:** quyết định có đo cả cấp con hay chỉ cấp ngoài; bảo đảm lọc `cflowbelow` phù hợp kỳ vọng.
4. **Helper/audit gọi từ advice:** không tạo chu kỳ tái nhập; audit vẫn thực hiện đúng một lần theo thiết kế.
5. **Nhiều aspect cùng khớp:** thứ tự được kiểm chứng trên cả pha enter/exit, không dựa vào một lần quan sát ngẫu nhiên.
6. **Retry có chủ đích:** tổng số attempt và side effect đúng contract; `proceed` lặp chỉ khi chính sách cho phép.
7. **Nhiều thread hoặc async:** trace giữ đúng correlation, guard không rò rỉ và không làm ảnh hưởng request khác.
8. **Chạy với cấu hình weave thật:** kết quả test phản ánh đúng call/execution, classloader và phạm vi instrumentation khi triển khai.

## 10. Những lỗi suy luận phổ biến

- “Một request chỉ được gọi phương thức một lần, vậy advice chỉ được chạy một lần.” Sai khi request chứa nhiều join point, lời gọi lồng, retry hoặc nhiều advice.
- “Advice `after` in ra ngược `before` chứng tỏ sai precedence.” Sai nếu đó là thứ tự stack unwind của các wrapper lồng nhau.
- “Thêm `!within(AspectName)` luôn chữa đệ quy.” Không đúng nếu advice gọi **phương thức khác** vẫn nằm trong phạm vi pointcut.
- “Dùng `!cflowbelow(...)` là hết lỗi đệ quy.” Nó có thể chỉ ẩn các sự kiện con; vòng gọi nghiệp vụ vẫn tiếp diễn.
- “Bỏ `finally` không sao vì đã có log sau `proceed`.” Khi có ngoại lệ, code sau `proceed` có thể không chạy.
- “ThreadLocal là một cờ cho cả request.” Nó gắn với thread, không tự lan qua thread khác.
- “Tên phương thức giống nhau là cùng join point.” Phải xem event kind, signature, lớp khai báo và vị trí weave.
- “Cứ có hai log là bị weave hai lần.” Cần loại trừ appender trùng, hai advice khác nhau và logging ở cả enter/exit trước.

## 11. Checklist kết thúc điều tra

- [ ] Đã xác định chính xác pointcut, join point kind, signature và tất cả advice khớp.
- [ ] Đã dựng quan hệ `caller → advice → helper → target` và đánh dấu chỗ tái nhập hoặc lặp.
- [ ] Đã phân biệt lời gọi nghiệp vụ thật, `proceed`, retry, lời gọi lồng và nhiều aspect.
- [ ] Đã xác nhận precedence, thứ tự nesting và hành vi khi ném ngoại lệ.
- [ ] Đã sửa nguyên nhân gốc: phạm vi match, call graph, guard/scope hoặc cấu hình weaving.
- [ ] Đã kiểm chứng số lần chạy và side effect trên đường thành công, thất bại, concurrent và async nếu có.
- [ ] Đã bảo đảm công cụ chẩn đoán tự nó không tạo vòng gọi hoặc làm thay đổi đáng kể hành vi đang quan sát.

**Nguyên tắc cốt lõi:** luôn đối chiếu “một lần chạy advice” với **join point cụ thể và chuỗi điều khiển thực tế**, sau đó sửa đúng điểm gây tái nhập, nhân đôi hoặc thứ tự không xác định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-debugging-diagnostics-009">Tạo tình huống tái hiện tối thiểu cho lỗi tương tác aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo tình huống tái hiện tối thiểu cho lỗi tương tác aspect

## 1. Mục tiêu và vấn đề cần giải quyết

Khi nhiều aspect cùng tác động lên một join point, lỗi có thể chỉ xuất hiện **khi các aspect hoạt động đồng thời**, dù từng aspect chạy đúng khi đứng riêng. Ví dụ: một aspect ghi dấu vết bao quanh một aspect thử lại thao tác; kết quả nghiệp vụ thành công nhưng hệ thống vẫn ghi nhận lỗi, tạo hai bản ghi theo dõi hoặc thực hiện tác dụng phụ nhiều lần.

**Tình huống tái hiện tối thiểu** (minimal reproducible example, MRE) là chương trình nhỏ nhất vẫn tái hiện **đúng triệu chứng cần điều tra**, có bước chạy, đầu vào, điều kiện môi trường, kết quả thực tế và kết quả mong đợi rõ ràng. “Tối thiểu” không có nghĩa là xóa hết mọi chi tiết: phải giữ các điều kiện làm phát sinh tương tác aspect.

Sau bài này, bạn có thể:
- Xác định join point, pointcut, advice, thứ tự bao quanh và cơ chế áp dụng aspect liên quan trực tiếp đến lỗi.
- Rút một ca lỗi lớn thành ví dụ nhỏ, độc lập, có hành vi quan sát được và kết quả xác định.
- Tách tác động của từng aspect, thay đổi **một biến mỗi lần**, rồi chứng minh điều kiện cần để lỗi xảy ra.
- Phân biệt lỗi logic advice với sai lệch do proxy, weaving, pointcut, lớp tải động hoặc dữ liệu chia sẻ.
- Chuyển MRE thành kiểm thử hồi quy sau khi sửa.

**Phạm vi:** kiến thức AOP nói chung. Ví dụ chạy bằng JDK dynamic proxy để minh họa rõ chuỗi around-interceptor; những hệ AOP dùng compile-time weaving hoặc load-time weaving có cách bắt join point và cấu hình thứ tự riêng. Không mặc định một cơ chế đại diện cho mọi hệ AOP.

## 2. Bản đồ các thành phần gây tương tác

| Thành phần | Câu hỏi chẩn đoán | Triệu chứng thường gặp |
| --- | --- | --- |
| **Join point** | Chính xác lời gọi, thực thi, truy cập hay sự kiện nào được can thiệp? | Đặt log ở sai điểm; sự kiện bị ghi hai lần. |
| **Pointcut** | Hai mẫu chọn điểm cắt có giao nhau ngoài dự kiến không? | Advice chạy cả ở hàm phụ, logger hoặc hàm được gọi lồng nhau. |
| **Advice** | Before, after, after-returning, after-throwing hay around? | Nuốt/đổi ngoại lệ; thay giá trị trả về; bỏ qua thao tác đích. |
| **Thứ tự/precedence** | Aspect nào nằm ngoài, aspect nào nằm trong chuỗi gọi? | Một lần thử lại tạo hai trace; hành vi sau lỗi khác nhau. |
| **Proceed/continuation** | Advice around gọi tiếp mấy lần, trong nhánh nào? | Target chạy 0, 1 hoặc nhiều lần; lặp vô hạn; mất ngoại lệ gốc. |
| **State và vòng đời** | Dữ liệu lưu ở aspect instance, theo đối tượng, thread hay lời gọi? | Rò trạng thái giữa các yêu cầu; lỗi phụ thuộc thứ tự chạy test. |
| **Cơ chế áp dụng** | Proxy, compile-time weaving, load-time weaving hay mô hình khác? | Một lời gọi không được chặn; cùng mã nhưng khác hành vi khi chạy. |

Trong around advice, **lồng nhau khác với chạy lần lượt**:

~~~text
TRACE ngoài RETRY:                 RETRY ngoài TRACE:
TRACE.before                      RETRY.begin
  RETRY.begin                       TRACE.before
    target()                          target()
    retry nếu lỗi                   TRACE.error nếu lỗi
    target() lần nữa                RETRY.repeat
TRACE.after                          TRACE.before
                                       target() lần nữa
                                     TRACE.after
~~~

Đây là hai cấu trúc có thể quan sát khác nhau ngay cả khi kết quả trả về giống nhau. Quy tắc precedence thực tế phụ thuộc công cụ AOP; cần ghi cấu hình và bằng chứng, không suy luận từ thứ tự các tệp, tên class hay vị trí khai báo.

## 3. Quy trình thu nhỏ lỗi có thể kiểm chứng

### Bước 1 — Đóng băng triệu chứng và hợp đồng mong đợi

Viết một câu mô tả có thể đo được, chẳng hạn:

> Với **một lời gọi nghiệp vụ**, nếu lần đầu ném lỗi tạm thời rồi lần thử lại thành công, kết quả phải là “OK”, target thực thi đúng hai lần và trace cấp yêu cầu chỉ bắt đầu **một lần**, không phát sinh cảnh báo lỗi cuối cùng.

Ghi đồng thời **kết quả thực tế**, không chỉ mô tả “aspect lỗi”. Chọn ít nhất một tín hiệu cho mỗi loại: thứ tự sự kiện, số lần gọi target, giá trị trả về, ngoại lệ, tác dụng phụ và thời điểm xuất hiện. Phân biệt *một yêu cầu logic* với *một lần thực thi vật lý*.

### Bước 2 — Chụp điều kiện tái hiện

Ghi phiên bản JDK, hệ AOP/phiên bản và cách kích hoạt, đường biên đối tượng được chặn, cấu hình pointcut, precedence, loại weaving/proxy, các tùy chọn chạy và đầu vào. Với lỗi phụ thuộc chạy song song, ghi rõ số luồng, cách phối hợp, có hay không trạng thái tĩnh và vòng đời đối tượng. Không đưa dịch vụ ngoài, mạng hay thời gian thực vào MRE nếu chúng không phải điều kiện gây lỗi.

### Bước 3 — Giữ lại lát cắt nhỏ nhất

Bắt đầu với **một target**, **một phương thức**, **hai aspect thật sự va chạm** và **một ca gọi**. Tách phụ thuộc bên ngoài thành giả lập xác định, dùng sự kiện lưu trong bộ nhớ thay cho log phân tán. Xóa từng phần và chạy lại sau mỗi lần xóa; thành phần nào xóa khiến lỗi biến mất là manh mối quan trọng. Giữ lại các phần bắt buộc cho cơ chế interception, ví dụ interface nếu đang dùng JDK proxy.

### Bước 4 — Xây ma trận cô lập

Chạy theo cùng một đầu vào và điều kiện đặt lại trạng thái:

1. Không aspect: xác nhận target và phép thử lỗi tạm thời hoạt động theo thiết kế.
2. Chỉ aspect A: xác nhận hành vi riêng của A.
3. Chỉ aspect B: xác nhận hành vi riêng của B.
4. A ngoài B: ghi chuỗi sự kiện và kết quả.
5. B ngoài A (nếu cấu hình cho phép): chỉ đổi thứ tự, ghi lại cùng các quan sát.

Nếu lỗi chỉ xảy ra ở một tổ hợp, bạn đã thu hẹp được nguyên nhân. Nếu lỗi xuất hiện cả khi chỉ có A, xem lại chính A và giả định ban đầu. Nếu không tái hiện, kiểm tra sự khác nhau giữa bản tối thiểu và chương trình gốc: điểm cắt, kiểu join point, cơ chế áp dụng, lớp tải động, đối tượng bị chặn và vòng đời.

### Bước 5 — Biến mô tả thành bằng chứng

MRE phải có mã đầy đủ, lệnh chạy, stdout/ngoại lệ mong đợi, biểu đồ thứ tự hoặc danh sách sự kiện và tiêu chí PASS/FAIL. Chốt **một giả thuyết mỗi lượt**: ví dụ “đặt TRACE vào trong RETRY khiến TRACE quan sát hai attempt, nên ghi một lỗi tạm thời đã được khôi phục”.

## 4. MRE Java 17+: hai hành vi quanh một lời gọi

Tình huống giả định có hai aspect theo ngữ nghĩa: **TRACE** theo dõi một yêu cầu và **RETRY** thử lại một lần khi gặp IllegalStateException. Target thất bại xác định ở lần thực thi đầu tiên và thành công ở lần thứ hai. Chuỗi around được tạo bằng JDK dynamic proxy và danh sách interceptor để ta **kiểm soát chính xác thứ tự**, không phụ thuộc trình biên dịch AOP bên ngoài.

Lưu mã sau thành **AspectInteractionRepro.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AspectInteractionRepro {
    private static final List<String> EVENTS = new ArrayList<>();

    public interface Service {
        String execute();
    }

    public static final class ServiceImpl implements Service {
        private int attempts = 0;

        @Override
        public String execute() {
            int n = ++attempts;
            EVENTS.add("target:" + n);
            if (n == 1) {
                throw new IllegalStateException("temporary");
            }
            return "OK";
        }
    }

    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface Around {
        Object invoke(Proceed next) throws Throwable;
    }

    private static final Around TRACE = next -> {
        EVENTS.add("trace.before");
        try {
            Object result = next.call();
            EVENTS.add("trace.after");
            return result;
        } catch (Throwable failure) {
            EVENTS.add("trace.error");
            throw failure;
        }
    };

    private static final Around RETRY = next -> {
        EVENTS.add("retry.begin");
        try {
            return next.call();
        } catch (IllegalStateException transientFailure) {
            EVENTS.add("retry.repeat");
            return next.call();
        }
    };

    private static Object chain(List<Around> interceptors, int index, Proceed target)
            throws Throwable {
        if (index == interceptors.size()) {
            return target.call();
        }
        return interceptors.get(index)
                .invoke(() -> chain(interceptors, index + 1, target));
    }

    private static Service proxied(Service target, List<Around> interceptors) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] {Service.class},
                (proxy, method, args) -> chain(interceptors, 0, () -> {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                })
        );
    }

    private static void scenario(String label, List<Around> interceptors) {
        EVENTS.clear();
        Service service = proxied(new ServiceImpl(), interceptors);
        String result = service.execute();

        System.out.println(label + " => " + result);
        System.out.println(String.join(" | ", EVENTS));
        long traceCount = EVENTS.stream()
                .filter("trace.before"::equals)
                .count();
        System.out.println("trace.before count=" + traceCount);
    }

    public static void main(String[] args) {
        scenario("TRACE then RETRY", List.of(TRACE, RETRY));
        scenario("RETRY then TRACE", List.of(RETRY, TRACE));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**, trong thư mục chứa tệp:

~~~bash
javac AspectInteractionRepro.java
java AspectInteractionRepro
~~~

Stdout đã được kiểm tra với JDK 21 (mã chỉ dùng tính năng có ở Java 17):

~~~text
TRACE then RETRY => OK
trace.before | retry.begin | target:1 | retry.repeat | target:2 | trace.after
trace.before count=1
RETRY then TRACE => OK
retry.begin | trace.before | target:1 | trace.error | retry.repeat | trace.before | target:2 | trace.after
trace.before count=2
~~~

**Giải thích theo từng tầng:**

- **ServiceImpl.execute()** tăng bộ đếm và phát lỗi ở attempt 1; attempt 2 trả “OK”. Mỗi scenario tạo ServiceImpl mới nên kết quả không phụ thuộc lần chạy trước.
- **Proceed** biểu diễn continuation: gọi nó sẽ đi tới interceptor tiếp theo hoặc target. Một advice around có thể gọi continuation nhiều lần; trong ví dụ, RETRY gọi lại đúng một lần khi nhận lỗi quy định.
- **TRACE** bao quanh toàn bộ continuation của nó. Nếu TRACE nằm ngoài RETRY, lỗi tạm thời được RETRY xử lý bên trong và TRACE chỉ thấy kết quả cuối cùng.
- Nếu **RETRY nằm ngoài TRACE**, mỗi attempt tạo một lượt đi qua TRACE. TRACE ghi trace.error cho attempt đầu dù yêu cầu cuối cùng thành công; trace.before xuất hiện hai lần.
- **Proxy.newProxyInstance** là một cách hiện thực interception qua interface, phù hợp để cô lập logic tương tác. Nó không tự thực hiện pointcut weaving lên mọi join point và không chứng minh những giới hạn của một hệ weaving khác.

Trong hợp đồng “TRACE đếm một lần cho mỗi yêu cầu logic”, tình huống thứ hai là **triệu chứng lỗi có thể tái hiện**: cùng kết quả “OK” nhưng thừa một trace và một bản ghi lỗi đã được phục hồi. Nếu yêu cầu thực tế là “một trace cho mỗi attempt”, tình huống thứ hai lại hợp lệ. Vì thế trước khi sửa cần xác nhận **ngữ nghĩa mong đợi**, không gọi mọi kết quả khác nhau là bug.

**Giới hạn chủ ý của ví dụ:** Đây là môi trường một luồng, interface proxy, exception đã định trước và thứ tự chain do mã đặt trực tiếp. Không kiểm tra recursion, gọi nội bộ trên cùng đối tượng, phương thức không qua proxy, weaving bytecode hay race condition; nếu đó là phần gây lỗi thật thì MRE kế tiếp phải giữ lại cơ chế tương ứng.

## 5. Đọc bằng chứng và khoanh vùng nguyên nhân

Đầu tiên đối chiếu **trạng thái kết thúc** và **đường đi**. Chỉ kiểm tra giá trị “OK” sẽ bỏ sót lỗi tương tác, bởi hai nhánh đều thành công. Đếm số lần target và advice chạy; ghi số lần continuation được gọi. Nếu hệ thống có metric hoặc thông báo lỗi, phân biệt lỗi attempt đã được phục hồi với lỗi cuối cùng của lời gọi logic.

Một bảng quan sát tối thiểu cho ví dụ:

| Biến thể | Giá trị trả về | Target thực thi | TRACE bắt đầu | TRACE ghi lỗi | Đạt hợp đồng trace/yêu cầu? |
| --- | --- | ---: | ---: | ---: | --- |
| TRACE ngoài RETRY | OK | 2 | 1 | 0 | Có |
| RETRY ngoài TRACE | OK | 2 | 2 | 1 | Không |

**Giả thuyết được chứng minh ở cấp MRE:** Thứ tự lồng around thay đổi phạm vi mà TRACE quan sát. TRACE ngoài RETRY thấy một yêu cầu đã phục hồi; TRACE trong RETRY nhìn từng attempt, bao gồm exception trung gian. Đây là lời giải thích cho kết quả thử nghiệm, **chưa tự động chứng minh** một ứng dụng thực tế dùng cùng precedence hay cùng cơ chế proxy.

Để đi từ MRE về ứng dụng, so sánh bằng chứng tại từng ranh giới: lời gọi đi qua proxy hay mã đã woven, điểm cắt thực sự khớp bao nhiêu lần, thứ tự before/around/after, số lần proceed, xử lý ngoại lệ trước/sau advice, và đối tượng/thread nào giữ state.

## 6. Mở rộng MRE theo nhóm lỗi tương tác

### 6.1. Hai pointcut chồng lên nhau

Triệu chứng: một phương thức nhận hai advice cùng nhiệm vụ; hoặc advice A ghi log bằng một phương thức cũng bị pointcut B chặn. Thu nhỏ bằng cách khai báo **một join point mong đợi** và **một join point không mong đợi**, rồi in mỗi lần match với tên phương thức và loại join point. Chứng minh lỗi mất đi khi siết điều kiện pointcut, không chỉ khi tắt cả aspect.

Không đánh đồng **call** (vị trí thực hiện lời gọi) với **execution** (thân phương thức bắt đầu chạy): các mô hình AOP hỗ trợ những kiểu join point khác nhau. Nếu hệ chỉ chặn thông qua proxy, một lời gọi không đi qua proxy có thể không được can thiệp; kết luận phải gắn với cơ chế cụ thể.

### 6.2. Around không chuyển tiếp, chuyển tiếp nhiều lần hoặc chuyển sai ngữ cảnh

Ghi sự kiện ở trước/sau mỗi lần gọi continuation và ở điểm vào target. Dùng counter xác định, không dựa vào thời gian chờ. Trường hợp “target không chạy” có thể do advice trả sớm; “target chạy hai lần” có thể là retry có chủ ý hoặc bug. Chỉ sửa sau khi đối chiếu hợp đồng nghiệp vụ và khả năng chạy lặp an toàn (*idempotency*).

Các lỗi dễ bỏ sót: gọi tiếp hai lần rồi trả kết quả lần đầu; bắt Throwable nhưng đổi thành lỗi khác; return ở nhánh cuối mà bỏ cleanup; advice gọi chính phương thức thuộc pointcut của nó gây tái nhập/reentrancy. Với recursion, đặt giới hạn sâu trong bản chẩn đoán và ghi dấu vết theo depth để tránh tràn stack.

### 6.3. Thứ tự và xử lý ngoại lệ

Tạo target ném chính xác một loại ngoại lệ. Đặt A ngoài B rồi B ngoài A, ghi **loại lỗi, thông điệp, cause và nơi bắt lỗi**. Phân biệt before/after-returning/after-throwing với around: một advice “sau khi ném lỗi” chỉ thấy lỗi thực sự đi qua ranh giới của nó; lỗi đã được advice bên trong xử lý có thể không xuất hiện ở tầng ngoài.

Nếu một aspect chuyển đổi exception và aspect khác quyết định retry theo type, thay thứ tự có thể thay kết quả nghiệp vụ, không riêng việc ghi log. MRE nên giữ đúng exception type và quy tắc chuyển đổi tối thiểu.

### 6.4. Trạng thái, thread và lifecycle

Kiểm tra ba trường hợp riêng: lời gọi tuần tự cùng instance, lời gọi tuần tự trên instance mới và nhiều lời gọi song song. Mỗi test reset state hoặc tạo đối tượng mới có chủ ý. Nếu chuyển sang song song mới lỗi, dùng công cụ đồng bộ có điểm chờ kiểm soát (ví dụ CountDownLatch), ghi thread và request id; không dựng race bằng sleep ngẫu nhiên rồi coi lúc PASS lúc FAIL là đủ bằng chứng.

Tránh suy diễn rằng context theo thread tự động được truyền qua mọi kiểu tác vụ bất đồng bộ. Nếu aspect dùng ThreadLocal hoặc dữ liệu per-request, MRE phải thể hiện **ranh giới chuyển thread** và quy tắc dọn dẹp.

### 6.5. Cơ chế chặn không khớp kỳ vọng

Phân biệt cụ thể:
- **Proxy interception:** một lời gọi phải đi qua đối tượng proxy để được áp dụng; kiểu proxy quyết định những phương thức nào được hỗ trợ. Gọi trực tiếp đối tượng gốc hoặc gọi nội bộ bỏ qua proxy có thể không kích hoạt advice.
- **Compile-time weaving:** mã được biến đổi khi biên dịch; cần giữ lệnh biên dịch, đường classpath và chứng cứ aspect đã được weave.
- **Load-time weaving:** mã được biến đổi khi class load; cần giữ cấu hình/agent tương ứng và thông tin classloader. Class đã được tải trước thời điểm áp dụng hoặc tải qua loader khác có thể cho kết quả khác, tùy công cụ.
- **Những hệ AOP khác:** xác nhận tập join point và cơ chế precedence thực tế từ chính công cụ đang dùng; không áp đặt quy tắc của một hệ lên hệ khác.

Nếu nghi ngờ vấn đề weaving hoặc classloader, ví dụ proxy ở mục 4 chỉ giúp kiểm chứng **logic thứ tự**, không thay thế được bài tái hiện chạy bằng engine thật. Giữ riêng hai MRE: một bản minh họa logic, một bản sát công nghệ để xác nhận nguyên nhân trong môi trường đích.

## 7. Chuẩn hóa phép thử hồi quy

Từ mã chạy bằng tay, viết assertion dựa trên **chuỗi sự kiện chính xác** và các thuộc tính của hợp đồng. Với tình huống ở mục 4, biến thể đáp ứng “một trace cho mỗi yêu cầu” có chuỗi sự kiện kỳ vọng:

~~~text
trace.before
retry.begin
target:1
retry.repeat
target:2
trace.after
~~~

Đừng chỉ assert kết quả trả về, vì cả hai thứ tự đều trả “OK”. Có thể thêm assertion: số sự kiện “target:” là 2, số “trace.before” là 1, không có “trace.error”, và toàn bộ chuỗi bằng danh sách kỳ vọng. Một khi sửa cấu hình hoặc mã aspect, chạy kiểm thử này cùng bản dùng cơ chế AOP thực tế để phát hiện thứ tự thay đổi trở lại.

Với ứng dụng nhiều thread, kiểm thử cần đồng bộ được các thời điểm thiết yếu, có timeout **để phát hiện treo**, ghi bất biến về số lượt xử lý và trạng thái cuối; một lần chạy ngẫu nhiên thành công chưa chứng minh không có race.

## 8. Hồ sơ MRE có thể chuyển giao

Một ca tái hiện tốt cho người khác phải tự đủ thông tin. Có thể dùng khung điền sau trong báo cáo lỗi thực tế (đây là khung báo cáo, không phải phần cần điền khi đọc bài):

| Trường | Nội dung cần ghi |
| --- | --- |
| **Tuyên bố lỗi** | Điều kiện đầu vào, kết quả mong đợi và một khác biệt quan sát được. |
| **Môi trường** | JDK, engine AOP + phiên bản, loại weaving/proxy, config cần thiết. |
| **Tệp tối thiểu** | Target, các aspect, mã kích hoạt và test/assertion. |
| **Cách chạy** | Lệnh build/chạy sao chép được; biến môi trường hay tham số bắt buộc. |
| **Kết quả mong đợi** | stdout, số lần target/advice, kiểu ngoại lệ/tác dụng phụ. |
| **Kết quả thực tế** | Chuỗi sự kiện đầy đủ, dấu hiệu lỗi; không bỏ attempt trung gian. |
| **Ma trận cô lập** | Không aspect; chỉ A; chỉ B; A+B; đổi thứ tự nếu có thể. |
| **Giới hạn tái hiện** | Cơ chế đã thay bằng mô phỏng, điều kiện đã loại bỏ, độ ổn định. |

Khi chia sẻ, loại bỏ thông tin nhạy cảm, phụ thuộc hạ tầng không cần thiết và dữ liệu ngẫu nhiên. Mỗi tệp/thiết lập còn lại phải có lý do rõ ràng: bỏ nó thì lỗi biến mất, môi trường không chạy, hoặc bằng chứng không còn phân biệt được các giả thuyết.

## 9. Sai lầm thường gặp và cách sửa

1. **Giữ cả ứng dụng lớn:** lỗi có thể do hàng chục thành phần; thu nhỏ từng phụ thuộc và chạy lại sau mỗi bước.
2. **Xóa đúng điều kiện gây lỗi:** loại bỏ một aspect, kiểu join point, state, precedence hay classloader khiến MRE hết lỗi nhưng cũng hết ý nghĩa. Ghi lý do cần giữ từng phần.
3. **Chỉ nhìn kết quả cuối:** hai chain cùng trả “OK”, song một chain sinh cảnh báo và trace thừa. Quan sát cả event trace, exception và số lần gọi.
4. **Cho rằng thứ tự khai báo là thứ tự chạy:** thứ tự thực tế phải được chứng minh bằng trace và cấu hình precedence của engine.
5. **Trộn retry với logic nghiệp vụ không idempotent:** chạy lại có thể tạo thanh toán, ghi dữ liệu hoặc gửi thông báo hai lần; chọn tác dụng phụ giả trong MRE để không gây hậu quả thực.
6. **Dùng sleep để giả lập race:** kết quả không ổn định và khó quy trách nhiệm; đồng bộ bằng tín hiệu có kiểm soát.
7. **Thử qua proxy rồi kết luận về weaving:** hai cơ chế có tập join point khác nhau. Đưa điều kiện công nghệ gốc trở lại trước khi chốt nguyên nhân.
8. **Không reset state giữa các ca:** bộ đếm, cache, static field hoặc context còn sót làm sai ma trận so sánh.
9. **Không có tiêu chí fail rõ:** một MRE hữu ích phải phân biệt được chạy đúng/sai chỉ từ lệnh và output/assertion.
10. **Sửa và đo nhiều biến cùng lúc:** chỉ thay một điều kiện (thứ tự, pointcut, quy tắc ngoại lệ, state), ghi kết quả rồi mới thử biến tiếp theo.

## 10. Checklist hoàn tất bài tái hiện

- [ ] Một câu mô tả triệu chứng, hợp đồng mong đợi và tín hiệu sai có thể kiểm tra.
- [ ] Đủ target, các aspect đang tương tác, join point và cơ chế áp dụng để lỗi vẫn tái hiện.
- [ ] Tất cả đầu vào và trạng thái khởi đầu xác định; lệnh chạy và phiên bản phụ thuộc được ghi.
- [ ] Có baseline, từng aspect riêng, hai aspect cùng chạy và phép đổi thứ tự khi phù hợp.
- [ ] Thu được chuỗi sự kiện, số lần proceed/target và ngoại lệ hoặc giá trị trả về tương ứng.
- [ ] Chỉ ra rõ thành phần tối thiểu khiến kết quả đổi, không suy diễn vượt cơ chế đã thử.
- [ ] Có assertion hồi quy về **hành vi cần bảo vệ**, bao gồm tác dụng phụ quan trọng.

**Kết luận kỹ thuật:** MRE hiệu quả giữ nguyên *quan hệ tương tác* giữa aspect nhưng loại bỏ biến số không cần thiết. Trong ví dụ, bằng chứng không nằm ở kết quả “OK” mà ở phạm vi trace quan sát từng attempt hay cả yêu cầu logic. Khi gắn lại engine AOP và loại join point thật, chuỗi sự kiện xác định giúp tìm đúng pointcut, precedence hoặc advice cần sửa và bảo vệ bản sửa bằng kiểm thử hồi quy.


</details>

- [Quay lại đầu trang](#back-to-top)
