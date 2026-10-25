<a id="back-to-top"></a>

# Advice trước và sau

## Menu
- [Advice trước khi điểm can thiệp thực thi](#advice-lifecycle-001)
- [Advice sau khi hoàn tất thành công và xử lý giá trị trả về](#advice-lifecycle-002)
- [Advice khi có ngoại lệ và ngữ cảnh lỗi](#advice-lifecycle-003)
- [Advice khi kết thúc bất kể thành công hay thất bại](#advice-lifecycle-004)
- [Phân biệt hành vi sau hoàn tất với hành vi luôn dọn dẹp](#advice-lifecycle-005)
- [Thứ tự advice trong trường hợp có nhiều lớp can thiệp](#advice-lifecycle-006)
- [Ngoại lệ phát sinh từ advice và ảnh hưởng tới lời gọi gốc](#advice-lifecycle-007)
- [Giới hạn thay đổi dữ liệu theo loại advice và công cụ](#advice-lifecycle-008)
- [Thiết kế advice nhỏ, rõ mục đích và ít tác dụng phụ](#advice-lifecycle-009)

## <a id="advice-lifecycle-001">Advice trước khi điểm can thiệp thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Advice trước khi điểm can thiệp thực thi (Before Advice) trong AOP

## 1. Before advice là gì?

**Before advice** là đoạn hành vi được một aspect yêu cầu chạy **trước join point đã được pointcut chọn**, theo ngữ nghĩa của mô hình Aspect-Oriented Programming (AOP) đang sử dụng. Đây là một cách đặt logic xuyên cắt, ví dụ logging, tracing, kiểm toán lượt thử hay kiểm tra điều kiện kỹ thuật, tại một tập điểm thực thi mà không phải chép các lời gọi helper vào từng phương thức nghiệp vụ.

Có bốn thành phần cần xác định:

1. **Join point:** sự kiện nào có thể can thiệp: method call, method execution, field access, constructor hoặc một loại được công cụ hỗ trợ.
2. **Pointcut:** điều kiện nào chọn join point, ví dụ chữ ký, type, annotation, vị trí caller hoặc kiểu receiver.
3. **Advice:** điều gì phải xảy ra trước, và hậu quả nếu hành vi trước thành công hoặc thất bại.
4. **Weaving/interception:** công cụ gắn hành vi vào bytecode hoặc đường gọi thực tế như thế nào.

Luồng tổng quát:

~~~text
Caller
  │
  ▼
Join point phù hợp pointcut?
  ├─ Không ──► Chạy đường thực thi gốc
  └─ Có
       │
       ▼
    Before advice
       │
       ├─ Kết thúc bình thường ──► Tiếp tục join point
       │
       └─ Ném lỗi ──► Đường ngoại lệ;
                     phần gốc có thể chưa được thực thi
~~~

Before advice mô tả **thời điểm hành vi được gắn vào luồng**, chứ không bảo đảm join point nào cũng xuất hiện hoặc mọi lời gọi đều được chặn. Nếu pointcut không khớp, lớp không được weave hoặc caller bỏ qua proxy, before advice không có cơ hội chạy ở điểm đó.

## 2. Đặt before advice trong mô hình AOP

| Thuật ngữ | Ý nghĩa | Vai trò trong bài |
|---|---|---|
| **Join point** | Điểm trong quá trình chạy có thể được nhận diện | Xác định hành vi nào sắp diễn ra |
| **Pointcut** | Tập điều kiện chọn join point | Quyết định phạm vi áp dụng advice |
| **Before advice** | Hành vi chạy trước điểm được chọn | Ghi nhận hoặc kiểm tra trước thao tác |
| **Target** | Đối tượng hoặc mã chịu tác động | Cung cấp ngữ cảnh để quan sát/lọc |
| **Weaver** | Công cụ kết hợp aspect với chương trình | Thực thi việc gắn advice vào đúng điểm |
| **Aspect** | Đơn vị tổ chức pointcut, advice và cấu hình | Gom trách nhiệm xuyên cắt |

Ví dụ giả mã **không phải chương trình Java có thể biên dịch**:

~~~text
aspect ExecutionTrace
    pointcut observedOperations =
        execution of methods marked @Observed

    before observedOperations:
        writeStartEvent()
end
~~~

Ý nghĩa: trước khi một execution được chọn bắt đầu, chạy mã ghi dấu. Nhưng đây mới là đặc tả ý định. Bộ dệt hoặc interceptor cần thực sự nhận diện được annotation và tham gia vào đường chạy.

Một ví dụ cú pháp theo **AspectJ**, là một ngôn ngữ AOP cụ thể:

~~~java
// Đoạn minh họa cú pháp AspectJ.
// Cần trình biên dịch/bộ dệt tương ứng, không chạy bằng javac đơn thuần.

aspect TraceAspect {
    before(): execution(* example.OrderService.submit(..)) {
        System.out.println("before submit");
    }
}
~~~

Không được suy rằng mọi engine AOP hỗ trợ các designator hay join point giống AspectJ. Ví dụ chỉ giúp nhận diện cấu trúc **before + pointcut + body**.

## 3. Thời điểm: trước method call khác trước method execution

### 3.1. Before ở method-call join point

Một **call join point** nằm ở vị trí phát lệnh gọi trong mã caller.

~~~java
final class Client {
    void submit(OrderService service) {
        service.create();
    }
}
~~~

Đây là đoạn Java minh họa chưa có định nghĩa OrderService và main. Nếu bộ dệt hỗ trợ method-call join point tại biểu thức **service.create()**, advice trước call chạy tại **biên caller** trước khi lời gọi được chuyển tới phương thức đích.

~~~text
Client.submit()
    │
    ▼
call join point cho service.create()
    │
    ▼
before advice ở phía caller
    │
    ▼
gọi Service.create()
~~~

Call pointcut có thể phân biệt **nơi phát lệnh gọi**, và có thể kết hợp điều kiện caller, vị trí lexical hoặc target runtime theo mô hình hỗ trợ.

### 3.2. Before ở method-execution join point

Một **execution join point** biểu thị việc bắt đầu thực thi thân một phương thức.

~~~text
Caller thực hiện lời gọi
   │
   ▼
dispatch đến thân Service.create()
   │
   ▼
execution join point
   │
   ▼
before advice ở phía phương thức
   │
   ▼
thân Service.create() chạy
~~~

Một execution có thể được gọi từ nhiều caller; advice ở biên execution có thể quan sát các lần thực thi từ các caller khác nhau nếu lớp và join point thực sự được weave.

Điểm khác biệt:

| Tiêu chí | Before tại call | Before tại execution |
|---|---|---|
| Nằm ở đâu? | Biểu thức gọi bên caller | Điểm đi vào thân phương thức đích |
| Quan tâm chính | Ai phát lời gọi và tại vị trí nào | Phương thức thực sự nào bắt đầu chạy |
| Ảnh hưởng của đa hình | Chữ ký/vị trí call có thể giữ nguyên | Implementation thực thi có thể thay đổi |
| Giới hạn thường gặp | Phải instrument được nơi gọi | Phải instrument được thân method hoặc chặn đúng đường chạy |
| Phạm vi proxy đơn giản | Không tự có call join point tại mọi source site | Có thể có interception khi gọi qua proxy, tùy cơ chế |

Một request có thể chứa cả call và execution join point. Nếu pointcut chọn cả hai, advice có thể chạy **hai lượt tại hai điểm khác nhau**, dù nghiệp vụ chỉ thực hiện một thao tác ở mức cao.

### 3.3. Đánh giá đối số có thể thất bại trước join point

Ví dụ Java:

~~~java
service.create(loadInput());
~~~

Nếu **loadInput()** ném ngoại lệ, chương trình có thể không đến lời gọi Service.create. Khi đó trước advice chọn **execution của create** chắc chắn không có execution đó để quan sát. Một before advice tại call cũng phải tuân theo ngữ nghĩa cụ thể của join point trong công cụ, không nên suy rằng nó chạy trước toàn bộ biểu thức con tạo tham số.

Điểm học quan trọng: **trước join point** là mốc của mô hình AOP, không có nghĩa **trước mọi công việc liên quan đến thao tác**.

## 4. Before advice có gì khác các loại advice khác?

| Loại | Thời điểm/khả năng chính | Điều không nên suy |
|---|---|---|
| **Before** | Trước join point khớp | Không mặc nhiên biết kết quả cuối |
| **After returning** | Sau khi join point hoàn tất bình thường | Không nhất thiết chứng minh giao dịch bất đồng bộ/ngoài hệ thống đã commit |
| **After throwing** | Khi join point kết thúc bằng ngoại lệ phù hợp | Không thay thế mọi chính sách exception toàn hệ thống |
| **After/finally** | Khi rời join point theo ngữ nghĩa công cụ | Không tự có nghĩa thao tác thành công |
| **Around** | Bao lấy điểm chọn; có thể điều phối proceed trong mô hình hỗ trợ | Không đồng nhất với một before đơn thuần |

Một **before advice** thông thường không có quyền trực tiếp gọi lệnh **proceed()** để quyết định thực thi target mấy lần hoặc thay return value. Năng lực đó thuộc around advice trong những mô hình cho phép. Tuy nhiên, before advice vẫn có thể ảnh hưởng việc target được thực thi qua **ngoại lệ và side effect**.

Đặc biệt, trước advice không thể tự lấy giá trị trả về mà target **chưa tạo ra**. Muốn tính duration, ghi completion status, quan sát exception hoặc thay return, cần cơ chế thích hợp sau/bao quanh.

## 5. Nếu before advice hoàn tất bình thường

Giả sử advice chỉ ghi log:

~~~text
before: request begins
core: business method starts
core: business method returns
~~~

Đây là trace minh họa, không phải stdout của đoạn Java độc lập.

Nếu advice kết thúc bình thường, phần thực thi gốc có thể tiếp tục tại join point. Với advice quan sát thuần túy, giá trị trả về và ngoại lệ của target thường được giữ theo hợp đồng ban đầu. Nhưng việc đặt advice **không tự bảo đảm thuần túy**: mã advice có thể ghi dữ liệu, thay đổi trạng thái dùng chung, lấy lock hoặc gọi thêm các method.

Vì thế khi đánh giá trước advice, cần hỏi:

- Nó có làm thay đổi dữ liệu đầu vào hoặc trạng thái của target không?
- Nó có thể throw một exception khác không?
- Nó có thể gọi vào phương thức cũng khớp pointcut, gây reentrancy không?
- Nó có làm thay đổi timing, lock ordering hoặc hiệu năng không?

Nếu một concern được mô tả là logging, thiết kế tốt thường giới hạn nó vào ghi nhận cần thiết và không thay đổi nghiệp vụ ngoài ý định.

## 6. Nếu before advice tự ném ngoại lệ

Giả sử advice kiểm tra một context bắt buộc:

~~~text
before join point:
    if context is missing:
        throw MissingContext

target:
    perform work
~~~

Đây là giả mã. Khi advice throw và không có một tầng điều phối khác bắt lỗi/tiếp tục, phần hành vi gốc ở join point có thể **chưa chạy**. Caller sẽ nhận lỗi đến từ advice, không phải từ method gốc.

Điều này khác hoàn toàn với trường hợp target ném lỗi:

~~~text
before runs normally
    ↓
target starts
    ↓
target throws
    ↓
caller handles target error
~~~

Với target ném lỗi, before đã chạy từ trước. Vì vậy ghi một event “đã bắt đầu xử lý” bằng before có thể hợp lệ, nhưng ghi “xử lý thành công” là sai vì tương lai chưa được xác định.

Ngoài ra, ngay cả khi advice throw, side effect nó đã tạo ra trước khi throw có thể vẫn tồn tại. AOP không tự rollback toàn bộ tác dụng phụ của advice.

### 6.1. Khi nào dùng before để chặn?

Một kiểm tra kỹ thuật xuyên cắt có thể từ chối thao tác bằng exception. Nhưng khi kiểm tra liên quan **bảo mật** hoặc **bất biến nghiệp vụ**, cần bảo đảm mọi đường đi cần bảo vệ đều đi qua đúng pointcut/cơ chế interception. Một proxy bị bỏ qua, subclass không nằm trong phạm vi weave hoặc lời gọi nội bộ có thể làm policy không được áp dụng như mong đợi.

Không nên chỉ thêm một before advice rồi mặc định đã có bảo đảm an toàn ở toàn hệ thống.

## 7. Ví dụ Java 17 chạy được: before bằng interceptor qua proxy

Chương trình sau thể hiện ba trường hợp: lời gọi thành công qua bộ chặn, target ném ngoại lệ sau khi before chạy, và một lời gọi trực tiếp target bỏ qua bộ chặn. Đây là **JDK dynamic proxy**, một ví dụ cụ thể của runtime interception; **không phải bytecode weaving tổng quát**.

Tạo file **BeforeAdviceDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Locale;

public class BeforeAdviceDemo {
    interface Work {
        String process(String input);
        void fail();
    }

    static final class Core implements Work {
        public String process(String input) {
            System.out.println("core: process " + input);
            return input.toUpperCase(Locale.ROOT);
        }

        public void fail() {
            System.out.println("core: fail");
            throw new IllegalStateException("boom");
        }
    }

    static Work withBefore(Work target) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] {Work.class},
                (proxy, method, arguments) -> {
                    System.out.println("before: " + method.getName());
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Work original = new Core();
        Work observed = withBefore(original);

        System.out.println("result=" + observed.process("alpha"));

        try {
            observed.fail();
        } catch (IllegalStateException exception) {
            System.out.println("caught: " + exception.getMessage());
        }

        System.out.println("direct=" + original.process("beta"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc cao hơn:

~~~bash
javac --release 17 BeforeAdviceDemo.java
java BeforeAdviceDemo
~~~

Stdout:

~~~text
before: process
core: process alpha
result=ALPHA
before: fail
core: fail
caught: boom
core: process beta
direct=BETA
~~~

**Giải thích chi tiết:**

1. **observed.process("alpha")** đi qua proxy nên handler chạy dòng **before: process**, sau đó target thực thi và trả **ALPHA**.
2. **observed.fail()** cũng đi qua proxy. Before chạy trước, rồi target ném **IllegalStateException**; đây là ví dụ “before đã ghi nhận lượt bắt đầu nhưng thao tác thất bại”.
3. Khi lời gọi reflection tạo **InvocationTargetException**, handler dùng **getCause()** để chuyển tiếp lỗi do target gây ra; caller bắt đúng **IllegalStateException** với thông điệp **boom**.
4. **original.process("beta")** gọi target trực tiếp, không đi qua proxy; do đó không có dòng before. Điều này chứng minh điểm chặn phải có mặt trên đường thực thi thật.
5. Handler trong ví dụ không triển khai bộ ngôn ngữ pointcut đầy đủ. Nó chỉ chặn các lời gọi đến các method thuộc giao diện **Work** trên reference proxy tương ứng.

### 7.1. Ví dụ chứng minh được điều gì?

Nó chứng minh cơ chế **thực hiện hành vi trước một lời gọi được chặn** và ảnh hưởng của đường trực tiếp/proxy. Nó không chứng minh tất cả loại join point như constructor, field access, private method hoặc direct bytecode execution đều có thể bị chặn bởi JDK proxy.

Đối với một ngôn ngữ AOP có compile-time hoặc load-time weaving, cần chạy ví dụ tích hợp thật với weaver cụ thể để xác minh tập điểm và thứ tự advice trên những loại join point khác.

## 8. Thông tin có thể đọc trong before advice

Thông tin có thể được công cụ cung cấp hoặc bind vào advice:

- Tên và loại **join point**.
- Chữ ký method/constructor/field nếu loại điểm cho phép.
- Đối tượng đang thực thi và **target** ở những loại join point có các đối tượng đó.
- Tham số đã được đánh giá của lời gọi; type/annotation/metadata phù hợp.
- Trace ID, context hoặc thời điểm bắt đầu nếu đã được ứng dụng cung cấp.

Nhưng **return value chưa có** ở thời điểm before advice. Một method có thể trả về future đại diện cho tác vụ còn chạy, nên advice trước lại càng không có thông tin về việc tác vụ đó hoàn thành.

Trong một ngôn ngữ AOP hỗ trợ binding, ví dụ cú pháp minh họa AspectJ:

~~~java
// Minh họa cú pháp AspectJ, không phải class Java độc lập.
before(String id):
        execution(* example.OrderService.submit(String))
        && args(id) {
    System.out.println("submit begins: " + id);
}
~~~

Điều kiện **args(id)** thể hiện việc bind tham số. Nó không có nghĩa mọi local variable trong target đều truy cập được. Nếu cần kết quả hoặc exception từ target, thiết kế phải sử dụng advice sau hoặc around phù hợp.

### 8.1. Đối số mutable và side effect

Trước advice có reference đến một danh sách mutable có thể thay đổi danh sách đó, gây tác động lên phần thân target sau này. Ví dụ minh họa Java:

~~~java
static void audit(java.util.List<String> values) {
    values.add("marker");  // Sửa nội dung cùng một list.
    values = new java.util.ArrayList<>(); // Chỉ gán lại biến local.
}
~~~

Đây chưa phải chương trình độc lập. Việc gán lại một parameter local của advice không tự thay parameter đang được caller truyền cho target. Nhưng mutate object được chia sẻ có thể làm thay đổi nghiệp vụ. Vì thế một before advice dùng để quan sát nên mặc định **không sửa input**.

## 9. Thứ tự nhiều before advice

Hai aspect có thể có before advice cùng khớp một join point:

~~~text
join point khớp
    ├─ before từ Aspect A
    ├─ before từ Aspect B
    └─ target method
~~~

Sơ đồ trên chỉ minh họa một thứ tự có thể xảy ra. **A trước B** hay **B trước A** phụ thuộc quy tắc precedence/order của công cụ và cấu hình. Không được mặc định thứ tự theo tên file, package hoặc thời gian viết source.

Nếu thứ tự ảnh hưởng hành vi, cần nêu rõ thứ tự yêu cầu và kiểm thử tích hợp, ví dụ:

- Kiểm tra thông tin ngữ cảnh trước khi log dữ liệu sử dụng context ấy.
- Kiểm soát quyền trước khi thao tác có side effect.
- Xác định một hoặc nhiều advice có thể ném lỗi, ngăn những bước sau.

### 9.1. Khi before nằm dưới around

Trong một số cơ chế AOP, **around advice** có thể chặn hoặc chuyển tiếp điểm qua lệnh tương đương **proceed**. Nếu around không chuyển tiếp tới phần join point nơi before được gắn, một số before advice bên trong đường đó có thể không chạy. Vì vậy không được kết luận “mọi before trên điểm khớp luôn chạy” mà không xét các tầng điều phối và precedence.

### 9.2. Before không thay cleanup

Một thao tác cần bảo đảm giải phóng resource sau target không thể chỉ dựa vào before. Cần after/finally hoặc một đường bao quanh có finally tương ứng. Tương tự, một metric duration cần thời điểm **kết thúc thực sự**, không thể tính chỉ từ một event bắt đầu.

## 10. Bao nhiêu lần before chạy trên một nghiệp vụ?

Số lần advice chạy phụ thuộc **số join point thực sự khớp**, không nhất thiết bằng số request nghiệp vụ.

Các trường hợp thường gặp:

- **Call và execution cùng được chọn:** một lời gọi có thể dẫn đến hai điểm khác nhau.
- **Nested calls:** phương thức A gọi B rồi C, trước advice có thể chạy ở cả A, B, C.
- **Recursion:** cùng một method được thực thi nhiều lượt đệ quy, advice có thể chạy từng lượt.
- **Retry:** một yêu cầu có thể dẫn đến nhiều lần thử target; before nằm trong vòng retry có thể chạy nhiều lần.
- **Polymorphism:** call site giữ nguyên nhưng runtime target thay đổi, điều kiện target có thể khớp ở lần này nhưng không khớp lần khác.
- **Dynamic pointcut:** chữ ký tĩnh khớp nhưng điều kiện runtime về argument, receiver hoặc control flow không thỏa nên advice không chạy.

Nếu muốn metric **số yêu cầu từ bên ngoài**, nên chọn một biên yêu cầu ổn định. Nếu muốn metric **số lần thực thi phương thức**, một execution pointcut phù hợp sẽ diễn đạt mục tiêu đó tốt hơn. Đừng đếm một event rồi đặt nhãn mang ý nghĩa khác.

## 11. Kế thừa, override và lời gọi nội bộ

### 11.1. Override có thể thay execution method

Với **Base ref = new Child()** và Child override **work()**, Java dispatch đến **Child.work()**. Một pointcut chọn chính xác execution của thân Base.work không có nghĩa tự áp dụng cho mọi lần gọi work qua reference Base. Cần làm rõ chọn theo call signature, declaring type, runtime type hay subtype pattern.

### 11.2. Self-invocation khi dùng proxy

Ví dụ:

~~~java
class Service {
    void outer() {
        inner();
    }

    void inner() {
        // Hành vi cần quan sát
    }
}
~~~

Nếu một proxy ngoài Service chỉ chặn lời gọi từ caller qua proxy, thì **outer()** gọi **inner()** trực tiếp trên target có thể không kích hoạt handler của proxy lần thứ hai. Nhưng một bytecode weaver có thể chèn advice vào execution của inner và vẫn quan sát được lời gọi nội bộ nếu lớp/method nằm trong phạm vi weave.

Do đó **đường đi qua proxy** và **điểm thực thi được weave** là hai cơ chế cần phân biệt, không áp đặt giới hạn của cơ chế này lên mọi AOP.

### 11.3. Các loại method đặc biệt

| Trường hợp | Điều cần xác minh |
|---|---|
| **Static** | Không có receiver instance; các điều kiện this/target không tự tương đương tên lớp. |
| **Private** | Không hiện diện trên JDK interface proxy; bytecode weaver có thể hỗ trợ execution nếu mô hình cho phép. |
| **Final** | Không thể override bằng subclass proxy theo cách thông thường, nhưng công nghệ weaving có giới hạn khác. |
| **Constructor** | Object có thể chưa hoàn tất bất biến; trước constructor không giống trước mọi bước tạo object. |
| **Class initializer** | Thường gắn với vòng đời lớp, không phải mỗi instance. |

Chọn before ở những điểm vòng đời sớm có thể khiến advice đọc trạng thái chưa hợp lệ hoặc phát tán reference chưa khởi tạo xong. Không nên truy cập đối tượng thiếu an toàn chỉ để lấy thêm thông tin log.

## 12. Chọn những concern thích hợp

### 12.1. Trace bắt đầu

Before advice phù hợp với log **bắt đầu** thao tác tại ranh giới kỹ thuật đã chọn. Nó không cung cấp một kết luận về thành công. Một hệ thống trace hoàn chỉnh cần correlation ID và điểm kết thúc tương ứng.

### 12.2. Audit lượt thử

Ghi **attempted action** trước thao tác có thể có ý nghĩa, kể cả khi target thất bại. Nhưng ghi **succeeded** trong before là không chính xác. Audit bắt buộc cần hợp đồng về lưu bền, xử lý thất bại và tính nguyên tử; AOP không tự giải quyết.

### 12.3. Kiểm tra kỹ thuật

Một chính sách chung về context, cấu hình hoặc điều kiện thực thi có thể được kiểm tra trước nhiều method. Nhưng phải kiểm tra bao phủ, lỗi và khả năng bypass. Quy tắc miền quan trọng vẫn nên có hợp đồng rõ ở nơi sở hữu nghiệp vụ.

### 12.4. Metrics lượt bắt đầu

Một counter tăng ở before đo số lượt **đi vào join point được chọn**. Nếu target throw, lượt vào vẫn được tính. Nếu cần phân biệt success/failure, phải có phần quan sát hoàn tất.

### 12.5. Khi nào không phù hợp?

| Nhu cầu | Before đơn lẻ? | Khả năng phù hợp hơn |
|---|---|---|
| Ghi nhận lượt bắt đầu | Có | Before |
| Quan sát giá trị trả về | Không | After returning / around |
| Ghi ngoại lệ target | Không | After throwing / around |
| Đo latency chính xác | Không | Before + after có context, hoặc around |
| Cleanup bắt buộc | Không | After/finally hoặc around với finally |
| Retry nhiều lần | Không | Around/Decorator hoặc điều phối retry |
| Cache trả kết quả không gọi target | Không | Around hoặc thành phần có quyền điều phối |
| Thay kết quả trả về | Không theo semantics thông thường | Around hoặc xử lý output được định nghĩa |

## 13. Lỗi thường gặp và cách phòng tránh

**Lỗi 1: Nhầm “trước” với “đã thành công”.** Before chỉ chứng minh đã đi vào điểm; target chưa xử lý xong. Hãy dùng nhãn event đúng.

**Lỗi 2: Điểm cắt quá rộng.** Mọi method đều kích hoạt log, kể cả helper logging, tạo reentrancy hoặc spam. Hãy chọn ranh giới rõ và loại trừ hạ tầng advice khi cần.

**Lỗi 3: Advice tự ném lỗi ngoài ý định.** Logging lỗi I/O làm target không chạy. Xác định rõ policy: fail-open hay fail-closed theo yêu cầu thực tế, không giấu lựa chọn đó.

**Lỗi 4: Tưởng before có thể thay return trực tiếp.** Before không tự có kết quả target và không quản lý proceed như around.

**Lỗi 5: Quên đường gọi trực tiếp target.** Khi dùng proxy, caller có thể bỏ qua interception nếu cầm object gốc.

**Lỗi 6: Nhầm call với execution.** Cùng một nghiệp vụ có nhiều join point; placement quyết định event được đếm.

**Lỗi 7: Bỏ qua thứ tự aspect.** Hai before có thể phụ thuộc lẫn nhau nhưng thứ tự không được định nghĩa theo giả định của người viết. Cần precedence và test.

**Lỗi 8: Đọc dữ liệu nhạy cảm.** Log toàn bộ argument/target có thể lộ token và dữ liệu cá nhân. Chỉ ghi những trường cần thiết.

**Lỗi 9: Dùng trạng thái global không đồng bộ.** Advice chạy đồng thời, một counter hoặc context mutable không an toàn gây sai metric.

**Lỗi 10: Kiểm thử bằng cách gọi advice thủ công.** Test đó chỉ kiểm tra nội dung hàm, chưa chứng minh weaving/pointcut thực sự hoạt động.

## 14. Quy trình thiết kế và kiểm thử before advice

1. **Xác định sự kiện cần quan sát:** ví dụ “mỗi lần thực thi public operation” hoặc “mỗi call từ nhóm caller A”. Diễn đạt cụ thể, tránh “mọi request” khi chưa có ranh giới request.
2. **Chọn loại join point:** call hay execution, hoặc điểm khác nếu được engine hỗ trợ.
3. **Viết pointcut với phạm vi ổn định:** kết hợp signature/type/annotation/context khi cần, tránh match rộng ngoài ý định.
4. **Viết before nhỏ và dự đoán side effect:** xác định nó có thể throw không, có mutate state không, cần dữ liệu nào.
5. **Xác minh cơ chế dệt:** đúng artifact, class loader, proxy reference hoặc agent; kiểm tra đường gọi thật.
6. **Thử ca khớp và không khớp:** đảm bảo event xuất hiện đúng nơi, không xuất hiện ngoài phạm vi.
7. **Thử ngoại lệ:** before throw và target throw là hai trường hợp khác nhau; kiểm tra body gốc có chạy không.
8. **Thử nested/retry/self-invocation:** xác nhận số lần chính xác và đường đi bypass nếu có.
9. **Thử nhiều aspect:** xác định precedence và logging sequence.
10. **Đo trên workload thật:** chi phí của logging, reflection, lock, allocation, network I/O có thể chiếm tỷ lệ lớn.
11. **Đảm bảo bảo mật dữ liệu:** không lưu bí mật/PII tùy ý, không giữ reference target lâu.
12. **Lưu regression test:** sau khi thêm subclass, đổi tên method, tách class hoặc thay cấu hình weave, các join point vẫn đúng mong đợi.

### Ma trận kiểm thử gợi ý

| Tình huống | Cần chứng minh |
|---|---|
| Pointcut khớp | Before chạy trước thân được chọn |
| Pointcut không khớp | Advice không chạy |
| Before ném lỗi | Target không tự chạy tiếp ở đường lỗi đó |
| Target ném lỗi | Before đã chạy, lỗi truyền theo hợp đồng |
| Caller bypass proxy | Interceptor không xuất hiện trên đường bypass |
| Gọi method nội bộ | Có thể bỏ qua proxy; kiểm tra theo cơ chế thật |
| Hai aspect cùng khớp | Precedence và số lượt ổn định |
| Recursion hoặc retry | Mỗi event phản ánh đúng biên đã chọn |
| Subclass override | Tập execution được chọn vẫn đúng |
| Thực thi đa luồng | Advice quản lý trạng thái an toàn |

## 15. Tự kiểm tra

**Câu 1.** Before advice chạy trước phương thức save rồi save throw. Có thể nói before advice không chạy vì save thất bại không?

**Đáp:** Không. Before đã chạy trước khi target throw.

**Câu 2.** Ghi “thanh toán hoàn tất” trong before có chính xác không?

**Đáp:** Không. Đó là lời khẳng định về tương lai chưa xảy ra.

**Câu 3.** Nếu before advice tự throw, target có chắc vẫn chạy không?

**Đáp:** Không; trên đường ngoại lệ không bị tầng khác bắt và tiếp tục, target có thể không được thực hiện.

**Câu 4.** Tại sao call và execution có thể khiến một request tạo hai bản ghi before?

**Đáp:** Vì đó là hai join point khác nhau, cùng được pointcut lựa chọn.

**Câu 5.** Vì sao original.process trong Java demo không in before?

**Đáp:** Vì nó bỏ qua reference dynamic proxy và đi thẳng vào Core.

**Câu 6.** Before có thể đọc kết quả trả về của target tại thời điểm bắt đầu không?

**Đáp:** Không. Phải đợi target hoàn tất và dùng advice phù hợp.

**Câu 7.** Khi target được retry ba lần, metric before đo điều gì?

**Đáp:** Tùy điểm đặt: có thể là số lần thử nếu inside retry, hoặc số yêu cầu bên ngoài nếu before ở biên ngoài retry.

**Câu 8.** Có thể tin một unit test gọi tay method ghi log là bằng chứng advice đã được weave đúng không?

**Đáp:** Không. Cần test tích hợp trên đường đi thật và kiểm tra kết quả pointcut.

## 16. Sơ đồ ghi nhớ

~~~text
                 POINTCUT (chọn join point)
                           │
                           ▼
                       Có khớp?
                    ┌──────┴──────┐
                   Không         Có
                    │             │
                    ▼             ▼
                Chạy gốc      BEFORE ADVICE
                                  │
                             ┌────┴────┐
                          Kết thúc    Throw
                          bình thường   │
                             │         ▼
                             ▼       Lỗi trước
                       JOIN POINT     target
                       TIẾP TỤC       có thể
                             │        không chạy
                         ┌───┴───┐
                         ▼       ▼
                      Trả về  Target throw
~~~

**Nguyên lý:** before advice áp dụng hành vi **trước một join point thực sự được chọn và intercept**, có thể nhìn metadata/context đã tồn tại, nhưng không có kết quả tương lai của target. Tính đúng đắn phụ thuộc **loại join point, phạm vi pointcut, đường weaving/interception, thứ tự advice, ngoại lệ và side effect**. Nếu cần hoàn tất, kết quả, cleanup, retry hoặc thay return, hãy dùng mô hình advice hay cơ chế điều phối có khả năng tương ứng.

## Tài liệu đọc thêm

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): before/after/around, join point và precedence của một ngôn ngữ AOP cụ thể.
- [Java Language Specification, Java SE 17, §15.12](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): Java method invocation, argument evaluation và dynamic method lookup.
- [JDK 17 InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): cơ chế JDK proxy dùng trong ví dụ chạy được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-002">Advice sau khi hoàn tất thành công và xử lý giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Advice sau khi hoàn tất thành công và xử lý giá trị trả về

## 1. Mục tiêu và vị trí của after-returning advice

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **advice** là hành vi bổ sung được áp dụng ở một *join point* (điểm có thể can thiệp) được pointcut lựa chọn. **After-returning advice** là loại advice chạy **khi thao tác được quan sát hoàn tất bình thường**, tức không thoát khỏi phạm vi quan sát bằng ngoại lệ. Đây là thời điểm thích hợp để quan sát giá trị trả về, ghi nhận kết quả thành công, thống kê, cập nhật metrics và bổ sung hành vi kỹ thuật mà không làm lẫn vào logic nghiệp vụ.

Ví dụ: phương thức `Catalog.lookup("A7")` trả `"item:A7"`. Khi join point của nó được chọn, một advice sau khi trả về có thể ghi loại kết quả và thời gian phản hồi. Người viết `lookup()` vẫn chỉ cần lo việc tìm mặt hàng, không phải sao chép mã ghi log vào mọi phương thức tương tự.

**Định nghĩa quan trọng:**

> After-returning advice được kích hoạt sau khi join point hoàn tất **bình thường**; nếu có binding kết quả, advice có thể quan sát giá trị trả về theo khả năng của cơ chế AOP.

Từ “thành công” trong tên gọi chỉ nói **thành công về luồng điều khiển tại join point** (normal completion), không tự khẳng định nghiệp vụ đã đạt mục tiêu. Một phương thức trả `false`, `null`, mã trạng thái `NOT_FOUND` hoặc `Result.failure(...)` vẫn là **normal return** nếu nó không ném ngoại lệ. Vì vậy, nếu muốn đo “thành công nghiệp vụ”, advice phải diễn giải thêm hợp đồng kết quả hoặc sử dụng tín hiệu miền thích hợp.

Ngược lại, khi target ném ngoại lệ ra khỏi join point và không có lớp bên trong xử lý rồi trả về bình thường, **after-returning advice gắn ở chính ranh giới đó không chạy**. Tình huống ấy liên quan đến `after-throwing`, `after/finally` hoặc một `around` có nhánh lỗi, tùy cơ chế.

## 2. Phân biệt after returning với những advice dễ nhầm

| Loại advice | Thời điểm/điều kiện kích hoạt | Có kết quả bình thường? | Mục đích phổ biến |
| --- | --- | --- | --- |
| **Before** | Trước khi chuyển điều khiển vào điểm được chọn | Chưa | Ghi bắt đầu, xác thực ngữ cảnh |
| **After returning** | Chỉ sau khi điểm được chọn hoàn tất bình thường | Có thể quan sát nếu cơ chế cung cấp | Audit thành công, kiểm tra/đếm giá trị trả về |
| **After throwing** | Khi điểm được chọn kết thúc bằng ngoại lệ | Không có kết quả trả bình thường của lần đó | Audit thất bại, phân loại ngoại lệ |
| **After / finally** | Khi phạm vi được bao rời đi dù bình thường hoặc lỗi | Không phải lúc nào cũng có kết quả | Cleanup, đo thời gian tổng quát |
| **Around** | Bao quanh lời thực thi, có quyền chuyển tiếp | Có thể nhận và xử lý kết quả/ngoại lệ | Cache, đo thời gian, retry có chủ đích |

Giả sử một join point được chọn chạy theo đường:

~~~text
Caller
  |
  v
[Before] ---> [Target method]
                   |
           +-------+--------+
           |                |
      normal return      throws error
           |                |
           v                v
    [After returning]  [After throwing]
           |                |
           +-------+--------+
                   v
              [After/finally]
                   |
                   v
       Caller nhận kết quả hoặc lỗi
~~~

Sơ đồ **chỉ minh họa những nhánh khái niệm**, không phải đặc tả thứ tự chung cho tất cả công cụ. Khi nhiều aspect cùng áp dụng, thứ tự và cách lồng advice phải tuân theo hợp đồng precedence/weaving của công nghệ đó. Quan trọng nhất là hai nhánh **after returning** và **after throwing** phân biệt theo cách join point kết thúc.

Nếu một phương thức có `try/catch` bên trong, nó có thể ném lỗi rồi **tự bắt** và trả về giá trị dự phòng. Từ bên ngoài ranh giới thực thi phương thức, kết quả cuối vẫn là normal completion. Do đó, after-returning có thể chạy dù đã có ngoại lệ nội bộ. Người thiết kế phải xác định muốn quan sát **kết quả cuối** hay **mọi ngoại lệ từng phát sinh**; đó không phải cùng một yêu cầu.

## 3. Giá trị trả về: khai báo, runtime và binding

### 3.1. Kiểu trả về khai báo khác đối tượng thực tế

Phương thức `Object find()` có thể trả về một `String`, một đối tượng khác hoặc `null`. Chữ ký khai báo `Object`, còn loại đối tượng runtime phụ thuộc từng lần chạy. Nếu một pointcut chọn theo kiểu trả về khai báo, kết quả matching có thể khác với việc advice kiểm tra loại đối tượng trả về thực tế.

Ví dụ:

~~~java
Object result = target.find(); // khai báo Object
if (result instanceof String s) {
    System.out.println("Chuỗi có độ dài " + s.length());
}
~~~

Đây là đoạn **minh họa**, không phải một chương trình chạy riêng vì `target` chưa được định nghĩa. Thông điệp: trong advice, chỉ ép kiểu kết quả sau khi đã xác minh hợp đồng hoặc kiểu thực tế. Không giả định phương thức nào có tên `find` cũng trả `String`.

### 3.2. Giá trị `null` vẫn có thể là normal return

`String lookup("missing")` trả `null` không hề ném ngoại lệ nếu `null` nằm trong hợp đồng trả về. After-returning vẫn có thể chạy. Nếu code advice thực hiện `result.toString()` không có kiểm tra, **chính advice** có thể ném `NullPointerException` và làm caller thất bại dù target hoàn tất bình thường.

Nên dùng cách quan sát an toàn như `String.valueOf(result)` khi chỉ cần nhãn hiển thị, nhưng vẫn phải xem xét dữ liệu nhạy cảm và chi phí `toString()`. Một đối tượng có `toString()` phức tạp hoặc phát sinh lỗi không phù hợp để log tùy tiện.

### 3.3. Phương thức `void` có hoàn tất bình thường hay không?

Có. `void ping()` chạy đến cuối hoặc `return;` mà không ném lỗi là một **normal completion**. After-returning có thể được kích hoạt ở join point ấy nếu cơ chế AOP hỗ trợ loại join point/return type tương ứng. Nhưng **không có giá trị trả về nghiệp vụ** để binding như một đối tượng `String`.

Trong Java Reflection, `Method.invoke()` trả `null` khi target khai báo `void`. Vì vậy, `result == null` trong bộ bao reflection **không đủ** phân biệt giữa “trả tham chiếu `null`” và “target `void`”. Phải xem `method.getReturnType() == void.class` hoặc hợp đồng do AOP engine cung cấp.

### 3.4. Primitive, boxing và generic

Các phương thức trả `int`, `boolean` hoặc `long` cũng hoàn tất bình thường. Nếu một API advice cung cấp mọi kết quả thông qua kiểu `Object`, giá trị primitive có thể được **boxing** thành `Integer`, `Boolean` hoặc `Long`. Đây là chi tiết của adapter/engine, không có nghĩa chữ ký target đã đổi thành wrapper.

Kiểu generic như `List<String>` có thể bị **type erasure** tại runtime. Không suy diễn rằng chỉ nhìn `Object result` đủ chứng minh mọi phần tử đều là `String`. Hãy kiểm tra cách công nghệ bind kết quả và cấu trúc dữ liệu thực tế.

## 4. Quan sát giá trị trả về hay thay thế giá trị trả về?

Đây là khác biệt quan trọng nhất của chủ đề.

**After-returning advice có mục tiêu điển hình là quan sát việc hoàn tất bình thường.** Nhiều mô hình AOP tách loại advice này khỏi quyền **thay thế giá trị trả về**: nó được gọi sau khi target đã tính kết quả, và không dùng giá trị `return` của chính phương thức advice như giá trị mới cho caller. Tuy nhiên, API của từng engine có thể có các khả năng đặc biệt; cần đọc hợp đồng cụ thể thay vì khẳng định một giới hạn cú pháp cho mọi công cụ.

Cần tách ba trường hợp:

1. **Quan sát không can thiệp:** advice đọc giá trị để tính metrics, kiểm tra loại dữ liệu hoặc ghi audit, rồi caller vẫn nhận kết quả gốc.
2. **Sửa đối tượng mutable được chia sẻ:** nếu advice lấy được tham chiếu tới `List`, `Map` hoặc DTO mutable và thay đổi bên trong nó, caller có thể nhìn thấy thay đổi **ngay cả khi advice không thay thế tham chiếu trả về**. Đây là một tác dụng phụ có thể phá hợp đồng và gây race condition; không nên làm nếu chính sách chỉ là logging.
3. **Thay kết quả thật sự:** đổi `null` thành object mặc định, cache result, làm giàu dữ liệu trước khi caller nhận, hoặc áp dụng fallback là **thay đổi ngữ nghĩa**. Tình huống này thường cần `around` advice hoặc một cơ chế transformation được engine hỗ trợ, với kiểm thử chính xác kiểu, ngoại lệ và số lần target chạy.

Pseudocode minh họa (không phải API chạy độc lập):

~~~text
afterReturning(joinPoint, returnedValue):
    metrics.observe(returnedValue)   # chỉ quan sát

around(joinPoint, proceed):
    original = proceed()
    return transform(original)       # có thể thay kết quả cho caller
~~~

Không nên chọn `around` chỉ vì muốn in giá trị trả về. Quyền kiểm soát `proceed` mang thêm khả năng gọi target **0, 1 hoặc nhiều lần** và dễ phát sinh lỗi khi quên chuyển tiếp, nhân đôi tác dụng phụ hoặc nuốt ngoại lệ.

### 4.1. Return value của advice khác return value của target

Nếu một engine yêu cầu hàm advice có kiểu `void`, `Object` hoặc một kiểu khác, kiểu trả về của **hàm advice** không nên bị nhầm với kiểu trả về của **join point target**. Chức năng của after-returning được xác định bằng hợp đồng áp dụng advice; tự viết `return newValue;` trong hàm advice không bảo đảm caller nhận `newValue`.

### 4.2. Một kết quả “lỗi” vẫn có thể là normal return

Ví dụ:

~~~java
record SaveResult(boolean success, String message) {}

SaveResult save(boolean accepted) {
    if (!accepted) return new SaveResult(false, "rejected");
    return new SaveResult(true, "saved");
}
~~~

Đây là ví dụ chỉ về ý nghĩa kết quả, không phải một chương trình độc lập có `main`. Cả hai nhánh **đều là normal return**. After-returning chạy ở cả hai nếu pointcut khớp; không thể đếm mọi lần advice chạy như “số giao dịch thành công”. Muốn thống kê nghiệp vụ phải đọc `success()` hoặc dùng sự kiện miền thích hợp.

## 5. Ví dụ Java 17+ chạy được: after-returning trên nhiều loại kết quả

Để minh họa mà không cần một engine AOP chuyên biệt, ta xây dựng **JDK dynamic proxy** đóng vai trò adapter ở ranh giới lời gọi. Chương trình có phương thức trả `String`, trả `null`, trả `int`, `void`, trả `CompletableFuture` và ném ngoại lệ.

- `ResultObserver.afterReturning(...)` đại diện cho **hành vi sau normal completion**.
- Trong `observe()`, chỉ khi `method.invoke()` **trả bình thường**, observer mới được gọi.
- Nếu target ném ngoại lệ, `InvocationTargetException` của reflection được bóc để giữ nguyên nguyên nhân gốc; không chạy `afterReturning` cho lần gọi đó.
- Bộ hiển thị kiểm tra `void`, `null` và `Future` để tạo stdout ổn định, không lệ thuộc định danh/hash của object.
- Ví dụ **không phải AOP engine đầy đủ**: nó chỉ chặn lời gọi qua interface proxy, không tự thêm join point trong thân target.

Tạo file `AfterReturningDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

public final class AfterReturningDemo {
    interface Catalog {
        String lookup(String key);
        int twice(int number);
        void ping();
        CompletableFuture<String> async(String key);
        String fail();
    }

    static final class CatalogCore implements Catalog {
        @Override
        public String lookup(String key) {
            System.out.println("BUSINESS lookup " + key);
            return "none".equals(key) ? null : "item:" + key;
        }

        @Override
        public int twice(int number) {
            return number * 2;
        }

        @Override
        public void ping() {
            System.out.println("BUSINESS ping");
        }

        @Override
        public CompletableFuture<String> async(String key) {
            System.out.println("BUSINESS async");
            return new CompletableFuture<>();
        }

        @Override
        public String fail() {
            System.out.println("BUSINESS fail");
            throw new IllegalStateException("storage unavailable");
        }
    }

    static final class ResultObserver {
        void afterReturning(Method method, Object result) {
            String display;
            if (method.getReturnType() == void.class) {
                display = "<void>";
            } else if (result instanceof CompletableFuture<?> future) {
                display = "Future(done=" + future.isDone() + ")";
            } else {
                display = String.valueOf(result);
            }
            System.out.println("AFTER_RETURNING "
                    + method.getName() + " -> " + display);
        }
    }

    static Catalog observe(Catalog target, ResultObserver observer) {
        return (Catalog) Proxy.newProxyInstance(
                Catalog.class.getClassLoader(),
                new Class<?>[]{Catalog.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(target, args);
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        System.out.println("EXCEPTION "
                                + method.getName() + " -> "
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                    observer.afterReturning(method, result);
                    return result;
                });
    }

    public static void main(String[] args) {
        Catalog catalog = observe(new CatalogCore(), new ResultObserver());
        System.out.println("CALLER " + catalog.lookup("A7"));
        System.out.println("CALLER " + catalog.lookup("none"));
        System.out.println("CALLER " + catalog.twice(3));
        catalog.ping();
        System.out.println("CALLER ping done");

        CompletableFuture<String> pending = catalog.async("A7");
        System.out.println("CALLER future.done=" + pending.isDone());
        pending.complete("async:A7");
        System.out.println("CALLER async.result=" + pending.join());

        try {
            catalog.fail();
        } catch (IllegalStateException original) {
            System.out.println("CALLER caught " + original.getMessage());
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AfterReturningDemo.java
java AfterReturningDemo
~~~

Đầu ra chính xác (đã kiểm chứng bằng trình biên dịch Java với `--release 17`):

~~~text
BUSINESS lookup A7
AFTER_RETURNING lookup -> item:A7
CALLER item:A7
BUSINESS lookup none
AFTER_RETURNING lookup -> null
CALLER null
AFTER_RETURNING twice -> 6
CALLER 6
BUSINESS ping
AFTER_RETURNING ping -> <void>
CALLER ping done
BUSINESS async
AFTER_RETURNING async -> Future(done=false)
CALLER future.done=false
CALLER async.result=async:A7
BUSINESS fail
EXCEPTION fail -> IllegalStateException
CALLER caught storage unavailable
~~~

### 5.1. Phân tích từng nhánh

**`lookup("A7")`:** target tính `"item:A7"` rồi trả bình thường. Observer nhận và in chính giá trị đó, sau đó caller cũng nhận `"item:A7"`. Điều này thể hiện mô hình advice **quan sát mà không thay thế** kết quả.

**`lookup("none")`:** target trả `null` bình thường. Observer in `null` và caller vẫn nhận `null`. Không có lỗi chỉ vì kết quả là `null`.

**`twice(3)`:** target trả `int 6`; reflection biểu diễn qua `Object` với boxing. Observer in `6`, nhưng chữ ký interface vẫn là `int twice(int)`, không bị thay đổi.

**`ping()`:** target có return type `void`. `Method.invoke()` cung cấp `null` cho adapter, nhưng observer kiểm tra `void.class` nên in `<void>` thay vì nhầm với một kết quả tham chiếu `null`.

**`async("A7")`:** target tạo và trả ngay một `CompletableFuture` **chưa hoàn tất**. Vì phương thức đã return bình thường, after-returning chạy và quan sát `Future(done=false)`; mã caller mới hoàn tất Future sau đó. Đây là bằng chứng rằng **method return** khác **asynchronous completion**.

**`fail()`:** target ném `IllegalStateException`. Adapter in một sự kiện `EXCEPTION` riêng (chỉ để minh họa đường lỗi), rồi caller bắt nguyên nhân gốc; **không có dòng `AFTER_RETURNING fail`**. Đường `EXCEPTION` của adapter không phải một instance của after-returning advice.

### 5.2. Phạm vi chính xác của ví dụ

Proxy chỉ quan sát lời gọi **đi qua đối tượng `catalog`** theo interface `Catalog`. Nếu gọi `new CatalogCore().lookup("A7")` trực tiếp, `ResultObserver` không được gọi. Không suy diễn từ ví dụ rằng mọi private method, constructor, field access, self-invocation hay lời gọi trực tiếp đều tạo được join point. Một cơ chế weaving khác có thể có độ phủ khác, nhưng cần xác minh tài liệu và kiểm thử thực tế.

Ở ví dụ, mọi phương thức khai báo trong `Catalog` đều đi qua observer nếu trả bình thường; ta chưa đặt điều kiện pointcut lọc phương thức cụ thể. Trong ứng dụng, có thể ghép bộ chọn chữ ký hoặc annotation để giảm phạm vi, tránh log thông tin không cần thiết.

## 6. Khi nào kết quả được coi là hoàn tất?

### 6.1. Phương thức trả về bình thường từ `try/catch`

~~~java
String load() {
    try {
        return readPrimary();
    } catch (IllegalStateException error) {
        return "fallback";
    }
}
~~~

Đây là **pseudocode Java minh họa**, chưa độc lập vì `readPrimary()` chưa định nghĩa. Nếu lỗi được handler bắt và `load()` trả `"fallback"`, after-returning ở ranh giới `load()` có thể chạy và thấy `"fallback"`. Dù hệ thống đã có một lỗi nội bộ, lần thực thi `load()` cuối cùng **không ném ngoại lệ ra ngoài**.

Nếu handler cũng ném lỗi, hoặc một `finally` phía sau ném lỗi, thì kết quả cuối có thể là exceptional completion và after-returning sẽ không chạy ở ranh giới được xét.

### 6.2. Return sớm

Một phương thức có nhiều nhánh `return` đều là kết thúc bình thường khi không ném lỗi. After-returning hoạt động theo **sự kiện kết thúc của join point**, không phụ thuộc target đã đi qua một hay nhiều lệnh `return` trong source. Nếu advice cần biết nhánh logic nào đã được đi qua, chỉ nhìn giá trị trả về có thể không đủ; cần tín hiệu nghiệp vụ rõ hơn.

### 6.3. `void` và constructor

Phương thức `void` có thể hoàn tất bình thường, nhưng **constructor** là một loại join point khác trong những mô hình hỗ trợ nó. Không tự áp dụng quy tắc “method after returning” lên constructor, field access hoặc exception handler nếu công cụ không định nghĩa cách áp dụng. Việc một constructor hoàn tất bình thường không có nghĩa một pointcut chọn method execution tự động chọn được constructor.

## 7. Lỗi phát sinh trong chính after-returning advice

Một trong những nguy cơ lớn là: **target đã thành công, nhưng after-returning advice tự gây ngoại lệ**.

Ví dụ advisor ghi `result.toString()` khi `result == null`, hoặc gọi dịch vụ audit đang mất kết nối. Target đã hoàn tất bình thường nhưng nếu lỗi từ advice được truyền tới caller, caller có thể thấy **toàn bộ lời gọi thất bại**. Trong trường hợp này:

- “Target thành công” và “caller nhận kết quả thành công” là hai phát biểu khác nhau.
- Chính sách error-handling của advice phải được mô tả, không tự ý nuốt mọi lỗi hoặc để lỗi thứ cấp thay thế lỗi quan trọng.
- Lỗi quan sát (metrics, logging) có thể được báo qua một kênh riêng nếu yêu cầu cho phép, nhưng cách xử lý cần nhất quán và an toàn.
- Nếu advice cố tình kiểm tra và từ chối kết quả, đó đã là hành vi có tác động lên hợp đồng caller; cần coi là quyết định thiết kế, không chỉ là một log statement.

Trong ví dụ Java, `observer.afterReturning(...)` nằm **sau** khối `try/catch` dành riêng cho `method.invoke(...)`. Nhờ đó, lỗi do observer ném không bị gắn nhầm nhãn là `EXCEPTION` của target. Việc phân biệt nguồn lỗi rất quan trọng khi thiết kế advice có độ tin cậy cao.

### 7.1. Tránh ghi dữ liệu nhạy cảm

Kết quả trả về có thể chứa mã truy cập, mật khẩu đã băm, định danh người dùng, thông tin thanh toán hoặc payload cá nhân. Advice không nên serialize toàn bộ object graph và đẩy vào log chỉ vì đó là giá trị dễ lấy từ join point. Hãy chọn trường cần thiết, masking dữ liệu, giới hạn độ dài, sampling và chính sách retention phù hợp.

### 7.2. Không tạo vòng gọi ngoài ý muốn

Nếu advice ghi log bằng một API cũng bị chính pointcut audit lựa chọn, việc ghi log có thể kích hoạt advice lần nữa và dẫn tới **đệ quy không kiểm soát**. Pointcut nên loại trừ cơ chế logging/metrics nội bộ hoặc sử dụng cách ngăn reentrancy có hợp đồng rõ ràng.

## 8. Bất đồng bộ: after returning khác after completion

Trong hệ thống bất đồng bộ, một phương thức có thể trả về một `CompletableFuture<T>`, promise, stream hoặc handle tới công việc sẽ diễn ra sau. Có ít nhất hai thời điểm tách biệt:

1. **Method return:** phương thức trả về đối tượng handle bình thường. After-returning của method execution/call có thể chạy ngay.
2. **Task completion:** nội dung bất đồng bộ hoàn tất thành công hoặc thất bại. Điều này có thể xảy ra sau đó, ở thread khác, hoặc không bao giờ xảy ra nếu tác vụ bị treo.

Ví dụ `async()` vừa trả một `CompletableFuture` pending: after-returning thấy *handle* đó, không có sẵn `T` bên trong. Nếu Future hoàn tất bằng ngoại lệ sau đó, phương thức tạo Future vẫn có thể đã **normal return**. Không dùng duy nhất số lần after-returning trên phương thức trả Future làm thống kê số tác vụ thành công.

Nếu muốn quan sát kết quả cuối, có thể gắn một callback completion vào handle, với chính sách rõ về completion bình thường, completion ngoại lệ và cancellation. Đây là **quan sát bất đồng bộ thêm**, không đồng nghĩa với advice `after returning` trên method ban đầu. Nếu callback làm biến đổi Future được trả, nó có thể thay đổi semantics và cần kiểm thử.

Ví dụ minh họa callback (chưa độc lập):

~~~java
CompletableFuture<String> future = startAsyncJob();
future.whenComplete((value, error) -> {
    if (error == null) {
        recordTaskSuccess(value);
    } else {
        recordTaskFailure(error);
    }
});
~~~

`startAsyncJob`, `recordTaskSuccess` và `recordTaskFailure` là các thành phần giả định. `whenComplete` đăng ký hành vi quan sát kết quả cuối; exception do callback sinh ra có quy tắc ảnh hưởng tới stage trả về tùy API và cần xem xét. Không tự giả định `whenComplete` chạy trên thread hay ngữ cảnh giống caller ban đầu.

## 9. Thứ tự nhiều advice ảnh hưởng kết quả quan sát

Giả sử `A` và `B` cùng quan sát việc trả về của một phương thức, nhưng `B` nằm trong một `around` của `A`. Trình tự thực tế tùy cơ chế kết hợp, chẳng hạn:

~~~text
Around A enter
  Around B enter
    Target -> original result
  Around B exit
Around A exit
Caller receives final result
~~~

Nếu tầng B là một **transformer around** làm `original` thành `modified` trước khi trả cho A, thì một after-returning observer ở **trong** B có thể thấy `original`, còn observer nằm **ngoài** B có thể thấy `modified`. Đây là lý do “giá trị trả về được advice nhìn thấy” luôn gắn với **join point và tầng wrapper cụ thể**, không phải lúc nào cũng là kết quả cuối cùng caller nhận.

Cần làm rõ các vấn đề sau trước khi kết hợp:

- Advice nào chạy trước/sau; thứ tự do precedence, cấu hình hoặc weaving quy định, **không mặc định dựa trên thứ tự source file**.
- After-returning của một tầng có quan sát kết quả trước hay sau các phép biến đổi ở tầng khác?
- Một tầng `around` có thể bỏ gọi `proceed` không? Nếu nó trả kết quả cache bình thường, advice nằm phía ngoài có thể quan sát normal return dù target không chạy.
- Nếu một tầng ném ngoại lệ sau khi target return, các advice bao ngoài có thể thấy **exceptional completion**, trong khi observer bên trong đã thấy normal completion.

Đối với hệ thống đòi hỏi audit chính xác, nên quy định **“kết quả sau tầng nào”** được đo và kiểm thử nhiều advice cùng khớp.

## 10. Thiết kế hợp đồng quan sát giá trị trả về

Một after-returning advice tốt nên mô tả cụ thể:

| Quyết định | Câu hỏi cần trả lời |
| --- | --- |
| Phạm vi pointcut | Phương thức nào được chọn? Có loại trừ logging/metrics nội bộ không? |
| Loại kết quả | Nhìn kiểu khai báo, kiểu runtime hay cả hai? Có `void`, `null`, primitive, generic? |
| Ngữ nghĩa thành công | Đếm normal return hay thành công nghiệp vụ trong `Result`? |
| Dữ liệu quan sát | Chỉ đếm, ghi mã trạng thái, kích thước hoặc hash an toàn? |
| Lỗi advice | Logging/metrics thất bại được báo riêng hay làm thất bại lời gọi? |
| Tác động mutable | Có vô tình thay đổi object graph được caller dùng không? |
| Thứ tự | Advice chạy trước/sau transformer nào? |
| Async | Kết quả đo tại method return hay task completion cuối? |
| Khả năng can thiệp | Call/execution join point, proxy, weaving và self-invocation được hỗ trợ ra sao? |
| Tài nguyên | Có sampling, giới hạn chi phí và bộ đếm an toàn đồng thời không? |

Một ví dụ về đo lường đúng ngữ nghĩa: thay vì ghi `businessSuccess++` ở mọi after-returning của `save()`, có thể ghi `normalReturns++` trước; nếu `save()` trả `SaveResult` có flag `success`, một phép đo riêng mới dựa vào flag đó để thống kê miền. Hai metric sẽ khác nhau khi nghiệp vụ từ chối một yêu cầu mà không ném lỗi.

## 11. Kiểm thử after-returning

Có thể dùng bảng sau làm contract test:

| Trường hợp | Kết thúc target | Có after-returning tại ranh giới ấy? | Kiểm tra bổ sung |
| --- | --- | --- | --- |
| Trả `"item:A7"` | Bình thường | Có nếu pointcut khớp | Advice/caller thấy đúng giá trị |
| Trả `null` | Bình thường | Có | Không `NullPointerException` do observer |
| Trả `int 6` | Bình thường | Có | Boxing trong adapter không đổi chữ ký API |
| Method `void` | Bình thường | Có nếu join point/engine hỗ trợ | Phân biệt `void` với trả tham chiếu `null` |
| Target ném `IllegalStateException` | Ngoại lệ | Không | Caller nhận đúng ngoại lệ gốc |
| Target ném lỗi rồi tự bắt, trả fallback | Bình thường ở ranh giới ngoài | Có | Phân biệt normal return với nghiệp vụ đã phục hồi |
| Trả `Result.failure(...)` | Bình thường | Có | Không tự cộng vào business success |
| Trả Future còn pending | Bình thường tại method | Có | Không coi Future pending là tác vụ thành công |
| Future thất bại sau khi method đã return | Bình thường tại method; thất bại trong task | Có tại method; event task tùy cơ chế riêng | Quan sát completion đúng |
| Advice tự ném lỗi | Target có thể thành công | Target normal completion đã xảy ra | Chính sách lỗi và caller contract rõ |
| Gọi target trực tiếp bỏ qua proxy | Theo target | Advice proxy không chạy | Phân biệt matching và interception |
| Hai advice khác thứ tự | Tùy tầng | Theo từng điểm/tầng | Kiểm tra giá trị mỗi tầng và caller |

Đừng chỉ unit test một method `afterReturning(...)` bằng mock result. Nó kiểm tra phần logic observer nhưng **không chứng minh** engine đã chọn đúng pointcut, chạy đúng loại advice và giữ nguyên kết quả trả cho caller. Phải có integration test đi qua ranh giới thực được instrument.

### Những assertion hữu ích

- Gọi `lookup("A7")` qua proxy: observer chạy **một lần**, nhận `"item:A7"` và caller vẫn nhận `"item:A7"`.
- Gọi `lookup("none")`: observer vẫn chạy, xử lý `null` an toàn.
- Gọi `fail()`: observer **không** chạy; caller nhận `IllegalStateException` gốc.
- Gọi `async("A7")`: observer thấy Future lúc return; hoàn tất Future không tự kích hoạt **thêm lần after-returning của cùng method**.
- Gọi trực tiếp `CatalogCore`: không có sự kiện từ observer của proxy.
- Thay thứ tự các wrapper/transformer: ghi lại kết quả observer thấy ở từng tầng, so sánh với kết quả cuối caller.

## 12. Lỗi hiểu sai phổ biến

1. **“After returning luôn là nghiệp vụ thành công.”** Sai: trả `false`, `null` hoặc `Result.failure` vẫn là normal completion.
2. **“After returning chạy cả khi ném ngoại lệ.”** Sai tại ranh giới đã chọn; đó là nhánh exceptional completion.
3. **“Trả `null` nghĩa là không có return.”** Sai; `null` là giá trị của kiểu tham chiếu và khác `void`.
4. **“Phương thức `void` không thể hoàn tất bình thường.”** Sai; nó có normal completion dù không có result object.
5. **“Advice có thể `return` gì thì caller nhận nấy.”** Không có quy tắc chung như vậy; phải xét hợp đồng after-returning của engine. Biến đổi kết quả thường là trách nhiệm của `around` hoặc API chuyên dụng.
6. **“Sửa một DTO mutable trong advice không ảnh hưởng caller.”** Sai; cùng tham chiếu có thể được sử dụng tiếp.
7. **“Future đã được trả tức tác vụ đã xong.”** Sai; hãy phân biệt return handle và completion.
8. **“Target thành công thì caller chắc chắn thành công.”** Advice phía sau có thể tự gây lỗi hoặc biến đổi kết quả.
9. **“Hai advice sẽ luôn quan sát một giá trị giống nhau.”** Thứ tự wrapper/transformer có thể khiến các tầng thấy các kết quả khác nhau.
10. **“Đặt after-returning là chặn được mọi phương thức.”** Khả năng intercept phụ thuộc pointcut, call/execution, proxy/weaving và đường gọi thực tế.
11. **“Log toàn bộ giá trị trả về là an toàn và rẻ.”** Dữ liệu có thể nhạy cảm, object lớn, `toString()` tốn chi phí hoặc có side effect.
12. **“Sau khi target trả bình thường, advice không thể ném lỗi.”** Sai; lỗi do chính advice vẫn có thể ảnh hưởng caller.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Phương thức trả `Result(success=false)` mà không ném exception. After-returning có chạy không?  
**Đáp án:** Có thể chạy nếu pointcut khớp và engine hỗ trợ; đó là normal return dù nghiệp vụ báo thất bại.

**Câu 2.** Sự khác nhau giữa `void` và kết quả `null` khi quan sát qua Java Reflection là gì?  
**Đáp án:** Cả hai có thể cho `Method.invoke()` trả `null`, nhưng `void` không có giá trị trả về nghiệp vụ, còn `null` là giá trị của một phương thức có kiểu trả về tham chiếu; dùng return type để phân biệt.

**Câu 3.** Muốn thay kết quả `"A"` thành `"B"` trước khi caller nhận, có nên mặc định dùng after-returning để làm không?  
**Đáp án:** Không. Nhiều mô hình after-returning được thiết kế để quan sát; việc thay kết quả thường cần `around` hoặc cơ chế transformation được công cụ hỗ trợ và kiểm thử.

**Câu 4.** Một phương thức trả `CompletableFuture` pending rồi tác vụ lỗi sau đó. Sau khi phương thức return có thể có after-returning không?  
**Đáp án:** Có, vì bản thân method đã trả Future bình thường; muốn biết lỗi của tác vụ phải quan sát completion riêng.

**Câu 5.** Nếu `lookup()` thành công nhưng advice ghi log ném exception, caller có chắc nhận kết quả của `lookup()` không?  
**Đáp án:** Không. Điều đó phụ thuộc việc lỗi advice được xử lý hay lan ra; phải quy định và kiểm thử chính sách lỗi.

**Câu 6.** Vì sao không nên chỉ nhìn một biến `result` để quyết định tính số “nghiệp vụ thành công”?  
**Đáp án:** Kết quả có thể là `null`, status thất bại trong một response object hoặc handle bất đồng bộ còn pending; định nghĩa thành công nghiệp vụ cần hợp đồng rõ hơn normal completion.

## 14. Tổng kết

**After-returning advice** kích hoạt sau khi join point được chọn **hoàn tất bình thường** và thường dùng để **quan sát giá trị trả về** mà không lẫn vào logic nghiệp vụ. Hiểu đúng loại advice này cần phân biệt **normal return với thành công nghiệp vụ**, **`void` với `null`**, **kiểu khai báo với giá trị runtime**, **quan sát với thay thế kết quả**, **method return với task completion bất đồng bộ**, và **lỗi target với lỗi của advice**. Khi nhiều aspect cùng tham gia, hãy xác định ranh giới và thứ tự để biết chính xác giá trị nào được quan sát. Kiểm thử kết quả caller, nhánh ngoại lệ và phạm vi weaving/proxy để bảo đảm chính sách cắt ngang đúng ý định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-003">Advice khi có ngoại lệ và ngữ cảnh lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Advice khi có ngoại lệ và ngữ cảnh lỗi

## 1. Vì sao advice xử lý ngoại lệ là phần cốt lõi của AOP?

Trong Aspect-Oriented Programming (**AOP**), **advice** là hành vi được kết hợp tại những **join point** do **pointcut** lựa chọn. Nhiều chính sách xuyên suốt phải phản ứng khác nhau giữa hai kết quả:

- Phương thức **trả về bình thường**: ghi số lần thành công, đo thời gian, kết thúc trace với trạng thái OK.
- Phương thức **kết thúc bằng ngoại lệ**: ghi sự kiện thất bại, gắn loại lỗi, dọn dẹp context, bảo toàn lỗi để caller quyết định cách xử lý.

Loại advice thường được gọi là **after-throwing advice** hoặc **advice khi ném ngoại lệ** phục vụ trường hợp thứ hai. Đây là khái niệm AOP tổng quát; **tên cú pháp, loại join point, khả năng lấy ngoại lệ và ngữ nghĩa thứ tự giữa các advice phụ thuộc ngôn ngữ/công cụ**. Không nên suy luận rằng mọi hệ thống AOP đều có cùng bộ API.

Sau bài học, bạn có thể:

1. Phân biệt **before**, **after-returning**, **after-throwing**, **after/finally** và **around** trên hai luồng kết thúc thành công/thất bại.
2. Xác định khi nào một exception thực sự **thoát ra khỏi join point** và khi nào nó chỉ bị bắt, xử lý bên trong target.
3. Đọc ngữ cảnh lỗi: loại exception, message, cause, suppressed exceptions, join point, đối số, correlation ID và thông tin trace; phân biệt thông tin nên/không nên ghi.
4. Viết advice quan sát lỗi nhưng **không nuốt lỗi, không đổi kiểu và không che lỗi gốc** ngoài chủ đích đã được công bố.
5. Giải thích giới hạn của proxy, reflection, weaving và điểm lỗi xảy ra trong một chuỗi nhiều advice.
6. Phân biệt một method **ném exception ngay** với một API bất đồng bộ **trả về handle rồi thất bại sau**.
7. Tự chạy ví dụ Java 17 minh họa after-throwing, cleanup, checked exception và runtime exception.

## 2. Mô hình cơ bản: join point và đường ngoại lệ

Giả sử một method `send()` được pointcut chọn. Tối giản hóa các trạng thái có thể xảy ra:

```text
caller
  |
  v
before advice
  |
  v
target.send()
  |
  +---- trả về bình thường ------> after-returning advice
  |                                   |
  |                                   v
  |                              after/finally
  |
  +---- ném ngoại lệ ra ngoài ---> after-throwing advice
                                      |
                                      v
                                 after/finally
                                      |
                                      v
                            caller nhận exception
```

Sơ đồ này minh họa **điều kiện kích hoạt**, không ấn định trật tự giữa nhiều aspect trong tất cả công cụ. Một advice riêng có thể chạy ở nhiều thời điểm, một quanh (*around*) có thể bọc các lời gọi khác nhau, và thứ tự thực tế phụ thuộc quy tắc precedence của engine.

### 2.1. Những loại advice dễ nhầm

| Loại hành vi | Điều kiện xảy ra | Giá trị sử dụng |
| --- | --- | --- |
| **Before** | Trước khi join point tiếp tục vào phần được chặn, nếu đã được chọn. | Mở trace, đánh dấu bắt đầu, ghi correlation ID. |
| **After returning** | Điểm được chọn **kết thúc bình thường**. | Ghi thành công, kiểm tra thông tin kết quả nếu công cụ hỗ trợ. |
| **After throwing** | Điểm được chọn **kết thúc bằng một ngoại lệ thoát ra**, thường có thể lọc theo loại lỗi. | Đếm lỗi, ghi error metadata, đánh dấu span thất bại. |
| **After/finally** | Điểm kết thúc bằng return **hoặc** throw theo ngữ nghĩa công cụ. | Dọn tài nguyên/context; ghi tổng thời lượng cả success/failure. |
| **Around** | Bao quanh phần được chọn, có thể gọi `proceed` hoặc tương đương. | Điều phối trước/sau, bắt ngoại lệ, quyết định xử lý tiếp có chủ đích. |

Tên `after throwing` mô tả **kết cục mà join point quan sát**. Nó không có nghĩa advice chỉ chạy khi có dòng `throw` trực tiếp trong source của target. Một method có thể gọi thư viện ném lỗi, và lỗi đó thoát qua method; ngược lại method có thể tự `throw` rồi `catch` và xử lý thành công trước khi return.

### 2.2. Ví dụ quan trọng: exception bị bắt bên trong target

```java
String load() {
    try {
        return readRemote();
    } catch (java.io.IOException ex) {
        return "cached";
    }
}
```

Nếu `readRemote()` ném `IOException` nhưng `load()` bắt lỗi và trả `"cached"`, thì **method execution của `load()` kết thúc bình thường**. After-throwing advice gắn vào **execution của `load()`** thông thường không thấy `IOException` thoát ra. After-returning advice của `load()` có thể chạy.

Một công cụ hỗ trợ join point ở **execution của `readRemote()`**, **call `readRemote()`** hoặc **exception handler** có thể quan sát sự kiện khác, nếu pointcut và weaving tương ứng được cài. Đây là lý do phải nêu **loại join point** thay vì nói mơ hồ “advice sẽ thấy mọi exception xảy ra trong hàm”.

### 2.3. Nếu before advice ném ngoại lệ thì sao?

Ví dụ before advice kiểm tra hoặc ghi log rồi ném lỗi **trước khi target bắt đầu**. Caller có thể thấy lời gọi thất bại dù target không được chạy. Tuy nhiên, after-throwing advice gắn chính xác vào **target execution** và advice bao ngoài cùng chuỗi có thể nhìn thấy các điểm lỗi khác nhau. Thứ tự và vùng bao phủ quyết định advice nào chạy khi chính một advice khác lỗi.

Không nên tuyên bố “mọi advice after-throwing chắc chắn bắt mọi lỗi trong chain”. Hãy vẽ các lớp advice và kiểm thử trên engine thật.

## 3. Những loại lỗi và trạng thái cần phân biệt

### 3.1. Lỗi từ target

Đây là lỗi sinh ra trong phần nghiệp vụ hoặc do hàm mà target gọi, rồi **không được xử lý trước khi thoát khỏi join point**.

Ví dụ:

```java
void dispatch(String id) {
    if (id == null) {
        throw new IllegalArgumentException("missing id");
    }
    // xử lý
}
```

Advice quan sát có thể ghi `IllegalArgumentException`, nhưng không nên thay quyết định xử lý nghiệp vụ của caller. Nếu phải chuyển đổi loại lỗi, cần hợp đồng được công bố về kiểu và thông tin cause.

### 3.2. Lỗi từ chính advice

Lời gọi `metrics.recordFailure(ex)` có thể ném exception nếu backend đo lường gặp sự cố. Nếu advice ném lỗi mới khi target đã ném lỗi cũ, caller có nguy cơ chỉ thấy **lỗi của hệ thống đo lường**, còn lỗi gốc bị che.

Do đó phải có chính sách rõ:

- **Best-effort observability:** lỗi ghi log/metric thường không được thay đổi kết quả nghiệp vụ; có thể bắt và báo qua kênh dự phòng được kiểm soát.
- **Chính sách bắt buộc:** một sự kiện audit phải được lưu bền vững có thể yêu cầu thất bại nếu ghi không thành công; đây là quyết định có ảnh hưởng đến hợp đồng, transaction và bảo đảm dữ liệu.
- **Không nuốt lỗi gốc:** advice không được lặng lẽ trả về thành công trừ khi chức năng recovery được thiết kế và kiểm thử có chủ đích.

### 3.3. Lỗi từ cơ chế can thiệp

Lỗi khi tạo proxy, tìm method, resolve metadata, weave bytecode, load class hoặc xác định pointcut không nhất thiết là lỗi target. Việc gán tất cả exception cho cùng nhóm “thao tác nghiệp vụ thất bại” có thể làm sai metric và chẩn đoán.

Ví dụ với Java reflection, `Method.invoke` dùng `InvocationTargetException` để bao ngoại lệ do target method ném. Nếu lời gọi reflection thất bại **trước khi vào target**, loại lỗi có thể khác. Advice cần biết nguồn lỗi nào đang quan sát để tránh gỡ cause sai hoặc báo cáo sai.

### 3.4. Lỗi bất đồng bộ

Một API có thể trả về `CompletableFuture`/promise ngay mà không ném exception, sau đó future hoàn thành thất bại. **Return của method** và **completion của công việc** là hai mốc khác nhau:

```text
method submit() trả CompletableFuture   -> return bình thường
                                        -> tác vụ chạy sau
                                        -> future hoàn tất exceptionally
```

Một after-throwing advice đặt tại **execution của `submit()`** không tự quan sát failure đến sau trong future. Muốn ghi thất bại end-to-end cần hook/callback tại sự kiện completion, với xử lý cancellation và các dạng wrapping exception theo abstraction bất đồng bộ đang dùng.

## 4. Ngữ nghĩa after-throwing: chọn theo kết cục và loại lỗi

### 4.1. Phân biệt “throw xảy ra” và “throw thoát ra”

Một `throw` có thể được xử lý ngay trong target:

```java
int parseOrDefault(String value) {
    try {
        return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
        return 0;
    }
}
```

`parseOrDefault("xyz")` vẫn **trả về 0 bình thường**. Advice after-throwing trên execution của `parseOrDefault` không nên ghi “method này ném NumberFormatException ra ngoài”. Nếu muốn thống kê số lần fallback, hãy thêm tín hiệu tường minh ở logic xử lý hoặc quan sát join point phù hợp khác nếu công cụ hỗ trợ.

Trong những mô hình có *exception handler join point*, điểm bắt lỗi là một loại điểm khác; nó không đồng nghĩa với after-throwing trên toàn bộ execution.

### 4.2. Lọc exception theo kiểu

Nhiều hệ thống AOP hỗ trợ điều kiện để advice chỉ phản ứng với một loại ngoại lệ hoặc nhóm kế thừa:

```text
join point: execution of operation
when throwing: IOException
advice: recordTechnicalFailure(...)
```

Đây là **giả mã**, không phải cú pháp chung. Cần xác minh cơ chế lọc có dùng quan hệ subtype hay chỉ exact type, và có hỗ trợ binding đối tượng exception hay không.

Nếu một method ném `IllegalArgumentException` nhưng pointcut/advice chỉ nhận `IOException`, advice đó có thể **không chạy**, dù lời gọi vẫn thất bại. Từ việc không có log after-throwing **không thể suy ra method thành công**.

### 4.3. Matching thuộc điểm nào: call, execution hay proxy invocation?

- **Method execution:** Theo dõi exception thoát khỏi lần thực thi body theo mô hình hỗ trợ.
- **Method call:** Theo dõi exception mà một điểm gọi cụ thể quan sát, nếu công cụ có call join point.
- **Proxy invocation:** Theo dõi exception đi qua wrapper/proxy handler, không tự bao gồm lời gọi bypass proxy.
- **Constructor / field / handler join point:** Chỉ có thể dùng nếu công cụ cung cấp đúng loại điểm đó.

Cùng một exception có thể đi qua nhiều điểm được chọn. Nếu advice ghi metric tại cả call và execution, một lỗi nghiệp vụ có thể tạo **nhiều bản ghi**. Cần quyết định xem metric đếm **exception crossing a boundary**, **invocation failure** hay **logical operation failure**.

## 5. Ngữ cảnh lỗi gồm những gì?

Một bản ghi lỗi tốt phục vụ chẩn đoán, nhưng không làm lộ dữ liệu và không vô tình sửa hành vi. Cần phân loại theo nguồn.

### 5.1. Ngữ cảnh của join point

Tùy khả năng công cụ, advice có thể có:

- **Kind:** method call, method execution hoặc sự kiện khác.
- **Signature:** tên phương thức, loại khai báo, kiểu tham số/return.
- **Target/this:** tham chiếu đối tượng đích hoặc đối tượng nhận lời gọi; hai khái niệm có thể khác ở call point hoặc proxy.
- **Arguments:** các tham số đã bind ở thời điểm join point xảy ra.
- **Annotation/metadata:** marker, mức giám sát hoặc chính sách được đặt tại declaration.
- **Location:** source/bytecode location nếu engine có thông tin vị trí.
- **Timing:** mốc bắt đầu và kết thúc, số lần thử, attempt ID.

**Không phải mọi engine đưa tất cả trường này cho advice.** Đừng đọc tên method từ stack trace rồi giả định nó tương đương metadata chính thức của join point. Stack trace có thể thay đổi theo inlining, reflection, mã sinh và trạng thái debug symbols.

### 5.2. Ngữ cảnh của exception

Trong Java, các thông tin hay dùng gồm:

| Trường | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `ex.getClass().getName()` | Tên loại exception. | Thường phù hợp làm nhóm lỗi có cardinality thấp hơn message tự do. |
| `ex.getMessage()` | Nội dung lỗi do mã tạo ra. | Có thể chứa dữ liệu riêng tư, input, token hoặc đường dẫn. Cần lọc/sanitize trước khi ghi. |
| `ex.getCause()` | Ngoại lệ nguồn được liên kết với lỗi hiện tại. | Có thể có chuỗi cause; cần tránh lặp vô hạn khi tự duyệt cấu trúc tùy biến. |
| `ex.getSuppressed()` | Các lỗi phụ được ghi nhận bên cạnh lỗi chính. | Quan trọng trong cleanup/try-with-resources; không thay thế exception gốc. |
| `ex.getStackTrace()` | Những frame thể hiện đường đi dẫn đến Throwable theo runtime. | Có chi phí và dữ liệu nhạy cảm; log chọn lọc, không mặc định in toàn bộ mọi lượt. |

Một Throwable có thể được bọc nhiều lớp. Đừng chỉ nhìn `getMessage()` của lớp ngoài rồi kết luận nguyên nhân gốc; cũng đừng tự ý gỡ mọi lớp wrapping, vì lớp wrapper có thể là một phần của **hợp đồng API**.

### 5.3. Ngữ cảnh nghiệp vụ và tracing

Một sự kiện lỗi hữu ích thường cần:

- **Operation name:** tên ổn định cho thao tác đang quan sát.
- **Correlation/trace ID:** liên kết các log của cùng request hoặc tác vụ.
- **Attempt ID:** phân biệt mỗi lần thử trong retry với một logical operation.
- **Result category:** validation, transient I/O, timeout, cancellation hoặc lỗi không phân loại.
- **Thời gian / môi trường:** mốc timestamp, service/module/version ở mức cần thiết.
- **Outcome:** success/failure/cancelled, **được định nghĩa thống nhất**.

Không nên lưu nguyên body request, mật khẩu, khóa API, thẻ thanh toán, dữ liệu y tế hoặc thông tin nhận dạng không cần thiết. Có thể dùng danh mục allowlist cho metadata được phép ghi. Nếu phải ghi message từ exception, cần đánh giá khả năng message chứa input người dùng.

### 5.4. Ngữ cảnh trên thread không tự theo tác vụ async

Một `ThreadLocal` correlation ID có thể hoạt động trong cùng thread, nhưng không tự bảo đảm truyền sang executor, callback hoặc coroutine. Advice lỗi ở thread khác có thể ghi mất trace ID hoặc giữ nhầm context nếu lifecycle không được quản lý. Với hệ thống bất đồng bộ, hãy thiết kế cơ chế truyền và dọn context theo abstraction của hệ thống, kiểm tra cả đường success, failure và cancellation.

## 6. Bảo toàn lỗi: nguyên tắc quan trọng nhất với advice quan sát

### 6.1. Mặc định nên truyền tiếp exception gốc

Trong một advice chỉ nhằm quan sát, hành vi mong muốn thường là:

```text
try:
    return proceed()
catch error:
    recordErrorBestEffort(error)
    throw same error
```

Giả mã trên chỉ diễn đạt ý tưởng. `proceed()`, bind exception, thứ tự advice và cách truyền tiếp tùy công cụ.

Nếu aspect biến `IOException` thành `RuntimeException` mà không có hợp đồng rõ, caller có thể mất khả năng xử lý lỗi đã dự kiến. Nếu aspect nuốt lỗi rồi trả một giá trị mặc định, metric “thành công” có thể tăng trong khi thao tác thực tế đã thất bại. Nếu một method được khai báo checked exception, việc chuyển đổi cần tuân thủ hợp đồng compile/runtime của Java.

### 6.2. Đừng để logging che exception gốc

Đây là ví dụ *chỉ minh họa ý tưởng*, không phải chương trình độc lập:

```java
try {
    return proceed();
} catch (Throwable original) {
    try {
        recordError(original);
    } catch (RuntimeException telemetryFailure) {
        // Chính sách minh họa: bỏ qua lỗi telemetry để giữ lỗi gốc.
    }
    throw original;
}
```

Khi hiện thực thật, cần chú ý loại Throwable có thể được ném theo hợp đồng của around advice và cơ chế build. Đoạn này giả định `proceed` và phương thức bao quanh cho phép ném `Throwable`.

Không mặc định bắt mọi `Throwable` của mã ứng dụng rồi tiếp tục chạy như thường. `Error` trên Java có thể chỉ ra sự cố nghiêm trọng, như cạn bộ nhớ; các chính sách telemetry nên tránh cấp phát hoặc thao tác phức tạp trong những tình huống đó. Việc bắt để **quan sát rồi ném lại ngay** khác với việc **nuốt và phục hồi**.

### 6.3. Đừng để `finally` thay kết cục ngoài ý định

```java
try {
    return compute();
} finally {
    cleanup();
}
```

Nếu `compute()` ném lỗi và `cleanup()` cũng ném lỗi, lỗi cleanup có thể trở thành lỗi được caller quan sát, che đi lỗi compute. Advice sau cùng cũng có rủi ro tương tự. Đối với thao tác cleanup đặc biệt quan trọng, cần thiết kế cách **ưu tiên lỗi gốc**, đính kèm lỗi phụ, hoặc bảo đảm cleanup không ném ngoại lệ ngoài hợp đồng.

Trong Java, **try-with-resources** có quy tắc *suppressed exceptions* để giữ lỗi chính từ thân `try` khi đóng tài nguyên cũng lỗi. Nếu tự viết around advice tương tự, cần chú ý `Throwable.addSuppressed` và các điều kiện biên; không tùy tiện thay ngữ nghĩa thư viện hoặc tự nuốt mọi lỗi cleanup.

## 7. Thực hành Java 17: quan sát after-throwing và context qua dynamic proxy

Ví dụ sau dùng **Java JDK dynamic proxy** để mô phỏng một cơ chế AOP dựa trên interface. Annotation `@Observed` được đặt trên method của interface. Handler chọn những lời gọi qua proxy có marker, in hành vi trước, sau khi trả về, sau khi ném lỗi và khi cleanup.

**Giới hạn:** Đây không phải bộ máy AOP toàn diện. Handler chỉ chặn lời gọi **đi qua proxy** và dùng `Method` của interface để đọc annotation. Nó không tự can thiệp field access, constructor, lời gọi trực tiếp đến target hoặc self-invocation.

Tạo file **`ErrorAdviceDemo.java`**:

```java
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ErrorAdviceDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed { }

    interface Sender {
        @Observed
        String send(String id) throws IOException;
        String health();
    }

    static final class RealSender implements Sender {
        @Override
        public String send(String id) throws IOException {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            if ("NETWORK".equals(id)) {
                throw new IOException("remote unavailable");
            }
            System.out.println("TARGET send " + id);
            return "SENT " + id;
        }

        @Override
        public String health() {
            System.out.println("TARGET health");
            return "UP";
        }
    }

    static Sender observed(Sender target) {
        return (Sender) Proxy.newProxyInstance(
                Sender.class.getClassLoader(),
                new Class<?>[] { Sender.class },
                (proxy, method, args) -> {
                    boolean selected = method.isAnnotationPresent(Observed.class);
                    if (selected) {
                        System.out.println("ADVICE before " + method.getName());
                    }

                    try {
                        Object result = method.invoke(target, args);
                        if (selected) {
                            System.out.println("ADVICE returning " + method.getName());
                        }
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        if (selected) {
                            System.out.println("ADVICE throwing "
                                    + original.getClass().getSimpleName()
                                    + ": " + original.getMessage());
                        }
                        throw original;
                    } finally {
                        if (selected) {
                            System.out.println("ADVICE finally " + method.getName());
                        }
                    }
                }
        );
    }

    public static void main(String[] args) throws IOException {
        Sender target = new RealSender();
        Sender proxy = observed(target);

        System.out.println("RESULT " + proxy.send("OK"));
        System.out.println("RESULT " + proxy.health());

        try {
            proxy.send("NETWORK");
        } catch (IOException ex) {
            System.out.println("CALLER IOException: " + ex.getMessage());
        }

        try {
            proxy.send("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER IllegalArgumentException: "
                    + ex.getMessage());
        }

        System.out.println("RESULT " + target.send("DIRECT"));
    }
}
```

Biên dịch và chạy:

```bash
javac ErrorAdviceDemo.java
java ErrorAdviceDemo
```

Kết quả **đúng thứ tự**:

```text
ADVICE before send
TARGET send OK
ADVICE returning send
ADVICE finally send
RESULT SENT OK
TARGET health
RESULT UP
ADVICE before send
ADVICE throwing IOException: remote unavailable
ADVICE finally send
CALLER IOException: remote unavailable
ADVICE before send
ADVICE throwing IllegalArgumentException: id required
ADVICE finally send
CALLER IllegalArgumentException: id required
TARGET send DIRECT
RESULT SENT DIRECT
```

### 7.1. Phân tích theo ba đường

**Đường trả về thành công:** `proxy.send("OK")` chạy before → target → returning → finally. `IOException` và `IllegalArgumentException` không có ở đường này nên không có `ADVICE throwing`.

**Đường checked exception:** `proxy.send("NETWORK")` đi vào target, target ném `IOException`. Reflection tạo `InvocationTargetException` làm wrapper. Handler lấy `wrapped.getCause()`, ghi loại và message, rồi **ném đúng exception gốc**. Vì `Sender.send` khai báo `throws IOException`, proxy có thể truyền checked exception này đến caller đúng hợp đồng. `finally` vẫn chạy.

**Đường runtime exception:** `proxy.send("")` ném `IllegalArgumentException`. Handler xử lý tương tự, caller thấy đúng loại runtime exception. Không có dòng `TARGET send ...` vì lỗi validation xảy ra trước lúc ghi dòng đó.

**Đường không được chọn:** `proxy.health()` đi qua handler nhưng `Observed` không có trên method `health`, nên không in dòng ADVICE. `target.send("DIRECT")` gọi trực tiếp target, không qua proxy nên cũng không in ADVICE dù method interface được đánh dấu.

### 7.2. Tại sao không catch tất cả exception rồi gọi là “lỗi target”?

Handler chỉ catch `InvocationTargetException` để nhận diện lỗi **phát sinh từ phần được `Method.invoke` gọi**. Nếu `method.invoke` thất bại do điều kiện reflection/infrastructure khác, catch block trên không gắn nhãn nhầm lỗi đó là lỗi nghiệp vụ. `finally` vẫn chạy cho các ngoại lệ được ném bên trong `try`; vì vậy demo dùng nó để minh họa cleanup. Trong hệ thống thật, cần phân loại lỗi infrastructure/advice riêng để không đo sai tỷ lệ lỗi target.

Ví dụ cũng **không** xử lý sự cố khi chính lệnh `System.out.println` bên trong advice thất bại. Nếu cần cam kết “telemetry không ảnh hưởng chức năng”, code thực tế phải đặt kênh ghi an toàn và chính sách lỗi phù hợp thay vì coi `println` là luôn thành công.

### 7.3. Checked exceptions và dynamic proxy

Java dynamic proxy có quy tắc quan trọng: một checked exception thoát khỏi `InvocationHandler` mà **không được khai báo** bởi method interface thích hợp có thể bị bọc trong `java.lang.reflect.UndeclaredThrowableException`. Vì vậy không được giả định mọi `Throwable` do handler ném đều đến caller cùng lớp ngoại lệ gốc.

Trong demo, `IOException` **được khai báo** bởi `send(String)`, còn `IllegalArgumentException` là unchecked exception, nên cách truyền lỗi là hợp lệ. Nếu thay `throws IOException` bằng một interface không khai báo checked exception tương ứng, cần kiểm thử kiểu caller thực sự nhận.

### 7.4. Lưu ý về tên class, runtime và annotation

- Tên tệp phải là `ErrorAdviceDemo.java` vì class public tương ứng là `ErrorAdviceDemo`.
- Annotation `@Observed` có `RetentionPolicy.RUNTIME` để reflection tra cứu được.
- Annotation đặt ở **interface method** và handler đọc `Method` nhận từ proxy theo interface. Nếu chỉ chuyển marker sang implementation, phép `method.isAnnotationPresent(Observed.class)` trong ví dụ **không tự tra cứu declaration khác**.
- Code sử dụng tính năng Java phổ biến, tương thích JDK 17 trở lên, không cần thư viện AOP ngoài JDK.

### 7.5. Bài thực hành mở rộng

Hãy thử lần lượt, mỗi lần dự đoán stdout trước khi chạy:

1. Bỏ `@Observed` khỏi interface method `send`: handler vẫn gọi target, nhưng không in dòng ADVICE.
2. Gọi `target.send("NETWORK")` trong `try/catch`: caller nhận `IOException` nhưng không có advice vì bỏ qua proxy.
3. Đổi điều kiện target để một nhánh **catch IOException nội bộ và trả fallback**: after-throwing của proxy không chạy ở nhánh fallback; after-returning có thể chạy.
4. Viết một handler khác ghi lỗi sau khi catch rồi trả giá trị giả: đánh giá vì sao đó là **thay đổi hợp đồng**, không còn quan sát thuần.
5. Thêm một advice/wrapper ngoài proxy: thử nghiệm thứ tự log khi lỗi target phát sinh và khi một tầng advice tự ném lỗi.

## 8. Around advice và quy tắc “proceed”

Một số mô hình AOP có **around advice**, cho phép bao quanh phần thực thi qua thao tác thường gọi `proceed`. Giả mã:

```text
around selected join point:
    context = startContext()
    try:
        result = proceed()
        context.markSuccess()
        return result
    catch error:
        context.markFailure(error)
        throw same error
    finally:
        context.close()
```

**Điểm cần nhớ:**

- `proceed` có thể tiếp tục vào advice bên trong rồi mới tới target; không nhất thiết gọi thẳng target.
- Việc gọi `proceed` 0 lần, 1 lần hay nhiều lần tùy năng lực engine và ý định thiết kế; có thể khiến target không chạy hoặc chạy lặp.
- `catch` trong around có thể quan sát lỗi thoát từ **các tầng nằm bên trong**, gồm cả target và advice khác, không chỉ exception được ném ở source của target.
- Advice có thể chuyển exception thành kết quả hoặc chuyển kiểu, nhưng đây là thay đổi ngữ nghĩa phải có hợp đồng và ca kiểm thử.
- `finally` là nơi phù hợp để đóng context nếu việc cleanup không làm hỏng exception gốc.

### 8.1. Đếm lỗi và retry

Nếu một around advice gọi `proceed` nhiều lần để retry, after-throwing ở bên trong có thể ghi **mỗi attempt lỗi**, trong khi advice ngoài cùng chỉ thấy lần cuối thành công. Hai bộ đếm có nghĩa khác nhau:

- **Attempt failure count:** số lần thử thất bại.
- **Logical operation failure count:** số thao tác cuối cùng thất bại sau mọi lần thử.

Hãy chọn metric cần đo và vị trí đặt advice theo ý nghĩa ấy. Đối với tác vụ có side effect (gửi tiền, tạo hóa đơn, phát sự kiện), tự động retry từ aspect có thể thực hiện thao tác nhiều lần và gây sai dữ liệu nếu thiếu idempotency, backoff, timeout và điều kiện retry hợp lệ.

## 9. Nhiều advice: ai nhìn thấy lỗi nào?

Giả sử có hai advice around A và B bao quanh một target:

```text
A.before
  B.before
    target()
  B.after / B.catch
A.after / A.catch
```

Khi target ném lỗi:

1. B ở tầng trong có thể ghi nhận lỗi trước.
2. Nếu B **ném tiếp lỗi**, A có thể ghi nhận cùng lỗi.
3. Nếu B **bắt rồi trả fallback**, A có thể thấy kết thúc bình thường.
4. Nếu B **thay lỗi gốc bằng lỗi mới**, A sẽ thấy loại lỗi do B ném ra.
5. Nếu A ghi lỗi sau B, cùng một logical failure có thể tạo **hai bản ghi** ở các tầng quan sát.

Thứ tự A/B không được suy từ thứ tự xuất hiện của file source; nó do cơ chế AOP và cấu hình precedence quyết định. Khi có nhiều aspect, nên định nghĩa rõ:

- Advice nào chịu trách nhiệm logging, tracing, transaction hoặc retry?
- Có bao nhiêu bản ghi cho **một attempt** hoặc **một logical operation**?
- Lớp nào có quyền biến đổi exception?
- Advice nằm trong hay ngoài transaction boundary và điểm commit?
- Nếu advice ném lỗi, những advice phía ngoài có quan sát được không?

### 9.1. Ví dụ ngữ nghĩa transaction

Một method có thể **trả về bình thường** nhưng giao dịch chưa commit. Nếu advice “success” nằm ở bên trong transaction boundary, log success có thể xuất hiện **trước** khi commit thất bại ở tầng ngoài. Để ghi nhận thành công bền vững, phải quan sát đúng mốc commit/outbox hoặc cơ chế giao dịch được công bố; after-returning của một method không tự chứng minh dữ liệu đã được lưu bền vững.

Điểm này là một ví dụ điển hình tại sao **join point và thứ tự advice** quyết định ngữ nghĩa quan sát lỗi.

## 10. Ngữ cảnh lỗi trong logging, tracing và audit

### 10.1. Một cấu trúc bản ghi gợi ý

Một sự kiện lỗi có thể chứa trường:

```text
event = "operation_failed"
operation = "payment-authorize"
outcome = "failure"
error.type = "IOException"
error.category = "dependency"
trace_id = "<id được truyền trong context>"
attempt = 2
duration_ms = <số đo đã tính>
```

Đây là **mẫu dữ liệu minh họa**, không phải chuẩn bắt buộc hoặc output của chương trình Java ở trên. Ở môi trường thực tế, `trace_id` phải đến từ context hợp lệ; không tự phát sinh một ID mới riêng cho mỗi tầng advice rồi phá mất khả năng tương quan.

Không nên dùng toàn bộ `error.message` làm nhãn metric vì thường làm cardinality tăng rất cao và có thể chứa dữ liệu người dùng. Với metric, ưu tiên loại lỗi/nhóm thao tác có số giá trị được kiểm soát; với log chi tiết, áp quy tắc redaction và retention.

### 10.2. Phân biệt audit và log lỗi

**Log lỗi** thường phục vụ chẩn đoán và có thể là best-effort. **Audit** có thể là bằng chứng bắt buộc cho một hành động nhạy cảm, cần kiểm soát độ bền, thứ tự, chống sửa và quan hệ với transaction.

Nếu policy yêu cầu audit phải được ghi thành công trước khi thao tác được coi là hoàn tất, một aspect chỉ gọi logger không tự tạo bảo đảm đó. Cần thiết kế hợp đồng lưu trữ/transaction phù hợp. Advice có thể là điểm kích hoạt, nhưng không thay thế thiết kế tính nhất quán dữ liệu.

### 10.3. Không ghi dữ liệu nhạy cảm từ exception

Ngoại lệ có thể chứa SQL text, đường dẫn nội bộ, token, thông tin người dùng và dữ liệu request. Không tự động serialize toàn bộ arguments, target hoặc stack trace ở mọi lỗi. Hãy dùng allowlist trường an toàn và bộ lọc/redaction; kiểm soát ai được truy cập log và thời gian lưu.

### 10.4. Phân loại và mức độ cảnh báo

Không phải ngoại lệ nào cũng là sự cố vận hành cần báo động. `IllegalArgumentException` do input không hợp lệ có thể là vấn đề phía caller; `IOException` từ dependency có thể là lỗi hạ tầng; cancellation có thể là hành vi mong đợi. Phân loại dựa trên hợp đồng API và ngữ cảnh, không chỉ tên exception.

Nếu đo tỷ lệ lỗi, định nghĩa mẫu số: tổng invocation, tổng request, tổng attempt hay tổng logical operation. Không trộn kết quả của các lớp advice khác nhau vào một chỉ số mà không có quy tắc chống trùng.

## 11. Bất đồng bộ: failure sau khi method trả về

Xét API trả `CompletionStage<String>`:

```java
CompletionStage<String> fetchAsync(String key)
```

Có ít nhất ba trường hợp:

1. **Ném lỗi đồng bộ trước khi return:** execution của `fetchAsync` kết thúc bằng throw; after-throwing tương ứng có thể chạy.
2. **Return một stage đã hoàn tất exceptionally:** lời gọi `fetchAsync` **trả về bình thường** một đối tượng stage; lỗi thuộc trạng thái completion của stage.
3. **Return stage chưa hoàn tất, sau đó lỗi:** cũng là normal method return, rồi future failure xảy ra muộn hơn.

Một advice muốn quan sát **lỗi cuối cùng của tác vụ** phải đăng ký hook vào completion bằng abstraction phù hợp, chẳng hạn `whenComplete` của `CompletionStage` trong Java; đồng thời tránh đổi kết quả/cancellation hoặc tạo side effect không mong muốn. Ví dụ khái niệm:

```java
CompletionStage<String> stage = fetchAsync(key);
stage.whenComplete((value, failure) -> {
    if (failure != null) {
        // ghi nhận kết quả bất đồng bộ; cân nhắc wrapping exception
    }
});
```

Đoạn trên là **mã minh họa không độc lập**, giả định `fetchAsync` và `key` đã tồn tại; không dùng như một chương trình chạy trực tiếp. `whenComplete` và các combinator có quy tắc về lỗi callback và stage phụ thuộc; cần xử lý riêng nếu mục tiêu là tuyệt đối không đổi semantics. Ngoài ra, biểu diễn lỗi từ `join()`, `get()` hoặc chain async có thể có wrapper; không tự gỡ/đổi exception tùy tiện.

## 12. Tính đúng đắn khi gắn advice theo pointcut

Một advice after-throwing tốt không chỉ có logic catch đúng; nó còn phải **được áp dụng đúng nơi**.

### 12.1. Kiểm thử điểm được chọn và điểm bị loại

- **Positive:** method được pointcut chọn ném exception ra ngoài; advice failure ghi nhận đúng một occurrence theo hợp đồng.
- **Negative thành công:** cùng method trả bình thường; không ghi failure.
- **Negative không khớp pointcut:** method khác ném lỗi; không được advice này xử lý.
- **Bị bắt nội bộ:** target bắt lỗi và trả fallback; after-throwing trên target execution không chạy.
- **Direct target:** khi dùng proxy, lời gọi bypass proxy không chạy handler.
- **Self-invocation:** proxy đơn giản có thể không chặn lời gọi method khác trên `this`; weaving có thể khác.
- **Lỗi trước target:** before advice hoặc cơ chế tạo proxy lỗi; cần xác định tầng after-throwing nào thấy.
- **Nhiều advice:** thứ tự, đếm trùng, thay exception và truyền tiếp được kiểm thử.
- **Checked exception:** caller nhận loại đúng hay `UndeclaredThrowableException` ở biên dynamic proxy.
- **Async:** phân biệt lỗi đồng bộ và stage hoàn thành exceptionally.
- **Telemetry failure:** lỗi logger/metrics có che mất lỗi gốc không?

### 12.2. Ma trận ca kiểm thử cụ thể

| Tình huống | Target execution kết thúc | Advice failure kỳ vọng | Caller kỳ vọng |
| --- | --- | --- | --- |
| Method được chọn trả `"OK"` | Bình thường | Không chạy. | Nhận `"OK"`. |
| Method được chọn ném `IOException` | Ném ra ngoài. | Chạy, nếu matcher cho phép kiểu này. | Nhận exception theo hợp đồng. |
| Method được chọn ném `IllegalArgumentException` | Ném ra ngoài. | Chạy nếu không chỉ lọc `IOException`. | Nhận runtime exception. |
| Target catch exception và return fallback | Bình thường. | Không chạy đối với execution đó. | Nhận fallback. |
| Method không thuộc pointcut ném lỗi | Ném ra ngoài. | Advice cụ thể này không chạy. | Vẫn nhận lỗi theo đường thường. |
| Proxy bị bypass | Target có thể ném. | Handler của proxy không chạy. | Nhận exception trực tiếp từ target. |
| Method trả stage rồi stage lỗi | Method return bình thường. | After-throwing đồng bộ không tự chạy. | Xử lý lỗi tại stage. |
| Advice logging tự ném | Có thể target đã lỗi. | Phụ thuộc vùng advice/cơ chế. | Cần chính sách để không che lỗi gốc ngoài chủ đích. |

## 13. Sai lầm thường gặp và cách sửa

**Sai lầm 1 — “Có `throw` trong source thì after-throwing chắc chắn chạy”.** Sai nếu exception bị bắt bên trong trước khi thoát khỏi join point. Hãy đặt đúng loại điểm và vẽ đường truyền lỗi.

**Sai lầm 2 — “After-finally và after-throwing như nhau”.** After/finally liên quan đến cả hai kết cục, còn after-throwing nhắm kết cục exception. Với cleanup cần tính cả success/failure.

**Sai lầm 3 — “Advice ghi lỗi thì nên nuốt exception để request không thất bại”.** Đây là thay đổi hợp đồng. Với logging/metrics quan sát, mặc định giữ lỗi gốc và để caller quyết định.

**Sai lầm 4 — “Dùng `ex.getMessage()` làm mã lỗi duy nhất”.** Message có thể đổi theo input, phiên bản và chứa bí mật. Dùng classification ổn định và metadata đã lọc.

**Sai lầm 5 — “Một lỗi chỉ tạo một bản ghi after-throwing”.** Nếu nhiều advice, nhiều join point hoặc nhiều lần thử cùng quan sát, số bản ghi có thể lớn hơn một. Phải định nghĩa phạm vi đếm và correlation.

**Sai lầm 6 — “Advice after-throwing bao mọi lỗi hệ thống”.** Sai nếu điểm không được chọn, đường gọi bypass proxy, lỗi phát sinh ngoài vùng hook hoặc loại exception không match.

**Sai lầm 7 — “`finally` luôn an toàn”.** Cleanup có thể ném lỗi và che lỗi gốc. Cần chính sách bảo toàn lỗi chính và xử lý lỗi phụ.

**Sai lầm 8 — “Method async trả future nghĩa là thành công”.** Nó chỉ có thể cho biết thao tác tạo/gửi task đã trả về; trạng thái cuối cần quan sát completion.

**Sai lầm 9 — “Exception do reflection bọc là loại lỗi nghiệp vụ mới”.** Với `Method.invoke`, `InvocationTargetException` là wrapper cho lỗi target; gỡ wrapper phải có hiểu biết về hợp đồng gọi và loại checked exception.

**Sai lầm 10 — “Hễ có annotation thì advice chạy”.** Annotation chỉ là metadata. Cần matcher, loại join point và cơ chế weaving/proxy thực sự đi qua lời gọi.

## 14. Tự kiểm tra kiến thức

**Câu 1.** `process()` bắt `IOException` từ `read()` rồi trả dữ liệu cache. After-throwing trên execution của `process` có chạy không?

**Đáp án:** Không đối với `IOException` đã được xử lý nội bộ, vì execution của `process` trả về bình thường. Một advice trên điểm khác, nếu được hỗ trợ, có thể quan sát lỗi của `read`.

**Câu 2.** Vì sao advice after-throwing nên được thiết kế để truyền lại đúng Throwable gốc?

**Đáp án:** Để giữ hợp đồng của target, khả năng xử lý của caller, chain cause và phân loại lỗi; chuyển/nuốt lỗi là thay đổi hành vi cần chủ đích.

**Câu 3.** Method interface khai báo `throws IOException`. JDK proxy handler nhận `InvocationTargetException` từ `Method.invoke`. Làm gì để caller nhận `IOException`?

**Đáp án:** Lấy `getCause()` là IOException và ném tiếp cause đó. Nếu tự ném checked exception không được method interface khai báo, proxy có thể bọc thành `UndeclaredThrowableException`.

**Câu 4.** Lỗi của chính logger xảy ra trong `finally` sau khi target ném lỗi. Rủi ro lớn nhất là gì?

**Đáp án:** Exception logger có thể che lỗi target, khiến caller và metric nhận sai nguyên nhân.

**Câu 5.** Khi target tự catch rồi retry ba lần trước khi return thành công, after-throwing gắn execution của toàn bộ target có ghi ba lỗi attempt không?

**Đáp án:** Không, nếu các lỗi attempt đều được bắt nội bộ và execution cuối cùng trả bình thường. Muốn đếm attempt phải đặt hook ở phạm vi phù hợp hoặc phát metric tường minh tại cơ chế retry.

**Câu 6.** Một `CompletableFuture` được return bình thường rồi hoàn thành exceptionally. Advice failure trên execution method có đủ để phát hiện không?

**Đáp án:** Không. Cần quan sát trạng thái completion của future; không đồng nhất return bình thường với tác vụ thành công.

**Câu 7.** Vì sao cần trace ID và attempt ID khác nhau khi quan sát lỗi?

**Đáp án:** Trace ID liên kết nhiều sự kiện của cùng request/luồng, còn attempt ID phân biệt các lượt thử khi retry. Một logical operation có thể gồm nhiều attempts.

**Câu 8.** Nếu hai around advice đều ghi exception nhưng advice trong chuyển lỗi thành kết quả fallback, advice ngoài sẽ nhìn thấy gì?

**Đáp án:** Tùy thứ tự/lớp bao; nếu tầng trong catch và return fallback, tầng ngoài thường quan sát kết thúc bình thường thay vì lỗi target đã bị xử lý.

## 15. Checklist trước khi áp dụng vào hệ thống thật

1. **Ranh giới:** Join point nào được chọn? Method call hay execution, proxy hay weaving?
2. **Kết cục:** Có phân biệt return, throw, cancellation và async completion không?
3. **Kiểu lỗi:** After-throwing quan sát Throwable nào? Có lọc theo exception class không?
4. **Ngữ cảnh:** Method/target/arguments/metadata/correlation ID được engine hỗ trợ ở đâu?
5. **Riêng tư:** Có loại bỏ bí mật, dữ liệu nhạy cảm và message chưa kiểm soát không?
6. **Bảo toàn hợp đồng:** Advice có đổi result, exception, cause hoặc timing một cách không mong muốn không?
7. **Lỗi của advice:** Logging/metrics/audit failure được xử lý theo policy rõ ràng chưa?
8. **Cleanup:** Có giữ lỗi gốc và giải phóng tài nguyên trên cả success/failure không?
9. **Thứ tự:** Nhiều advice, transaction, retry và audit có thứ tự đúng mục đích không?
10. **Kiểm thử:** Đủ ca success, target throw, target catch nội bộ, bypass, advice throw, async và checked exception chưa?

## Kết luận

**Advice khi có ngoại lệ** giúp tập trung chính sách quan sát và xử lý kỹ thuật cho các join point **kết thúc bằng lỗi thoát ra ngoài**. Nhưng muốn dùng đúng phải xác định rõ **loại join point**, **đường exception thực sự đi qua**, **ngữ cảnh có thể truy cập** và **hợp đồng cần bảo toàn**. After-throwing không đồng nghĩa “mọi lệnh throw trong source”, after/finally không đồng nghĩa chỉ lỗi, và kết quả method async không đồng nghĩa trạng thái cuối của công việc. Một thiết kế đáng tin là thiết kế biết rõ nơi advice chạy, nơi nó không chạy, giữ nguyên exception gốc khi chỉ quan sát, bảo vệ dữ liệu nhạy cảm và có bộ kiểm thử xác nhận cả success lẫn failure.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-004">Advice khi kết thúc bất kể thành công hay thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Advice khi kết thúc bất kể thành công hay thất bại trong AOP

> **Mục tiêu học tập:** Hiểu advice chạy khi rời một join point theo cả nhánh hoàn tất bình thường lẫn nhánh ném ngoại lệ (*after/finally advice*); phân biệt nó với advice chỉ chạy trước, chỉ chạy sau thành công, chỉ chạy sau thất bại và advice bao quanh; biết thiết kế thao tác dọn dẹp, đo lường và quan sát mà không làm sai kết quả hoặc che giấu ngoại lệ của chương trình.

## 1. Vì sao cần một advice chạy ở cả hai nhánh?

Giả sử chương trình muốn ghi rằng một thao tác đã **kết thúc**, bất kể thao tác trả về giá trị mong muốn hay ném lỗi. Nếu đặt dòng ghi nhận chỉ sau lời gọi nghiệp vụ thì khi lời gọi ném ngoại lệ, chương trình có thể không tới được dòng ghi nhận đó.

~~~java
String result = processOrder();
System.out.println("Operation finished");
~~~

Đây là **mảnh mã minh họa**, không phải chương trình chạy độc lập. Nếu `processOrder()` ném ngoại lệ, câu `println` tiếp theo không chạy theo đường thực thi thông thường.

Tư duy AOP đặt ra một **mối quan tâm xuyên suốt**: ghi dấu kết thúc, giải phóng phạm vi quan sát, đóng một bộ đo hoặc thu dọn ngữ cảnh cho nhiều điểm thực thi. Khi áp dụng cho những join point được pointcut chọn, **after/finally advice** diễn đạt: *“Thực hiện hành vi này khi hoạt động đang được quan sát rời khỏi phạm vi tương ứng, bất kể hoàn tất bình thường hay bất thường.”*

Điều đó hữu ích cho việc quan sát, thu dọn trạng thái và phục hồi ngữ cảnh dùng chung. Nhưng cần hiểu thật chính xác **kết thúc hoạt động nào**, **ngoại lệ đi qua ranh giới nào**, **advice nào được kích hoạt**, và **những trường hợp không có một lần thoát thông thường nào**.

## 2. Đặt after/finally advice trong mô hình thành phần AOP

Các khái niệm liên quan:

| Khái niệm | Vai trò | Ví dụ |
|---|---|---|
| **Join point** | Điểm/sự kiện được mô hình AOP nhận diện | Một lần thực thi phương thức `charge()` |
| **Pointcut** | Điều kiện lựa chọn tập join point | Chọn các thao tác thanh toán cần quan sát |
| **Advice** | Hành vi bổ sung tại các điểm được chọn | Ghi dấu bắt đầu/kết thúc |
| **Aspect** | Mô-đun tổ chức một mối quan tâm xuyên suốt | Theo dõi thời lượng giao dịch |
| **Weaving** | Cách kết hợp advice với chương trình gốc | Chèn/kết nối logic quan sát tại những điểm phù hợp |

After/finally advice là **một loại advice**, nghĩa là nó trả lời câu hỏi *“hành vi bổ sung chạy khi nào so với join point?”*. Nó không tự trả lời *“điểm nào được chọn?”*; câu hỏi đó thuộc **pointcut**.

Giả sử một pointcut chọn thực thi `charge()` và `refund()`. Cùng một after/finally advice có thể được áp dụng khi mỗi lần thực thi được chọn rời phạm vi, dù trả về bình thường hay ném ngoại lệ.

**Không có một cú pháp thống nhất cho tất cả hệ AOP.** Tên gọi *after*, *finally*, *after (finally)*, *after-returning* và *after-throwing* có thể được tài liệu hóa khác nhau tùy mô hình. Bài này dùng **after/finally** để chỉ loại advice muốn chạy ở **cả hai cách thoát**; khi triển khai phải kiểm tra hợp đồng thực tế của công cụ.

## 3. Hai cách hoàn tất cần phân biệt

Với một join point đại diện cho thực thi phương thức thông thường, có hai kiểu kết thúc chính mà ta cần quan sát:

1. **Hoàn tất bình thường:** phương thức chạy tới cuối hoặc thực hiện `return`; caller tiếp tục với kết quả (có thể là `void`).
2. **Hoàn tất bằng ngoại lệ:** phương thức hoặc một hoạt động bên trong ném ngoại lệ, và ngoại lệ đi ra khỏi ranh giới join point đang được quan sát.

Mô hình ý tưởng:

~~~text
                   Vào join point được chọn
                              |
                              v
                     Thực thi phần gốc
                              |
                 +------------+-------------+
                 |                          |
                 v                          v
           Return bình thường       Thoát bằng ngoại lệ
                 |                          |
                 +------------+-------------+
                              |
                              v
                     After/finally advice
                              |
                 +------------+-------------+
                 |                          |
                 v                          v
         Caller nhận kết quả        Ngoại lệ tiếp tục lan truyền
         theo hợp đồng gốc           theo hợp đồng gốc
~~~

Sơ đồ diễn tả **trường hợp lý tưởng khi advice không tự làm thay đổi kết quả**, và một hoạt động thực sự có cơ hội thoát khỏi phạm vi theo hai đường trên.

**Không phải mọi lần “có lỗi ở đâu đó” đều là hoàn tất bằng ngoại lệ của join point.** Nếu phương thức tự bắt ngoại lệ rồi trả về giá trị bình thường, join point bên ngoài đã **hoàn tất bình thường**. Nếu một tác vụ bất đồng bộ ném lỗi ở luồng khác sau khi phương thức lập lịch đã trả về, method join point ban đầu cũng không tự trở thành một lần thoát do ngoại lệ.

## 4. So sánh before, after-returning, after-throwing, after/finally và around

| Loại advice khái niệm | Khi nào chạy? | Điều cần nhớ |
|---|---|---|
| **Before** | Trước khi vào hoạt động được chọn, theo ranh giới được công cụ xác định | Không chứng minh phần gốc sau đó sẽ chạy thành công |
| **After-returning** | Khi hoạt động được quan sát hoàn tất bình thường | Thích hợp với kết quả thành công ở cấp ranh giới, không nhất thiết thành công nghiệp vụ |
| **After-throwing** | Khi hoạt động được quan sát thoát bằng ngoại lệ được cơ chế nhận diện | Chỉ phản ánh ngoại lệ **đi ra khỏi** phạm vi được chọn |
| **After/finally** | Khi hoạt động được quan sát rời phạm vi bình thường **hoặc** do ngoại lệ | Thích hợp để ghi dấu kết thúc, dọn dẹp hoặc phục hồi ngữ cảnh |
| **Around** | Bao quanh hoạt động được chọn, thường có khả năng điều khiển việc tiếp tục phần gốc | Có thể mô phỏng hành vi finally qua `try/finally`, nhưng không phải mọi around advice đều giữ nguyên luồng gốc |

After/finally **không phải** “after-returning cộng với after-throwing” theo nghĩa bắt buộc công cụ sẽ thực thi hai advice độc lập. Về mặt **điều kiện kích hoạt mong muốn**, after/finally bao phủ cả hai kiểu thoát. Cơ chế tổ chức và thứ tự có thể khác.

### 4.1. Ví dụ nhầm lẫn hay gặp

Một thao tác `pay()` trả về một đối tượng `PaymentResult.failed()` nhưng **không ném ngoại lệ**. Ở góc nhìn thực thi phương thức, đó là **normal return**. Một after-returning advice có thể chạy dù kết quả nghiệp vụ là “thất bại”.

Ngược lại, một thao tác có thể xử lý nghiệp vụ thành công nhưng advice khác ném ngoại lệ trong quá trình điều phối. Điều gì được quan sát là “thoát bằng ngoại lệ” phụ thuộc ranh giới kết hợp advice. Vì vậy cần phân biệt **trạng thái nghiệp vụ** với **cách phương thức kết thúc**.

## 5. Vì sao after/finally hữu ích với mối quan tâm xuyên suốt?

Trong một ứng dụng có hàng chục thao tác, cùng một chính sách có thể cần ghi nhận thời điểm kết thúc hoặc phục hồi trạng thái tại nhiều vị trí. Nếu mỗi phương thức tự viết các khối `try/finally`, chính sách sẽ phân tán và dễ thiếu nhất quán.

Một after/finally advice có thể tập trung hành vi:

- Ghi dấu `operation finished` ngay khi rời một ranh giới đã chọn.
- Cập nhật bộ đếm số lần **hoàn tất phạm vi**, bất kể bình thường hay ngoại lệ.
- Hoàn tất phép đo thời lượng từ một mốc bắt đầu tương ứng.
- Xóa hoặc phục hồi ngữ cảnh tạm thời gắn với đúng phạm vi lời gọi.
- Dọn dẹp tài nguyên **do chính lớp chính sách đã tạo và sở hữu**, theo hợp đồng rõ ràng.
- Ghi nhận sự kiện “đã rời thao tác” mà không giả định thành công.

Đây là các ví dụ phù hợp khi hành vi áp dụng ở nhiều join point và có thể diễn đạt thành chính sách độc lập với nghiệp vụ. Nếu thao tác dọn dẹp đòi hỏi hiểu sâu trạng thái nội bộ của một giao dịch cụ thể, việc giấu nó trong aspect có thể làm chương trình khó giải thích và kiểm chứng.

## 6. Tư duy try/finally trong Java để hiểu after/finally advice

Trong Java, khối `finally` được thực thi khi điều khiển rời một khối `try` theo cách thông thường, kể cả `return` hoặc ném ngoại lệ, **nếu quá trình thực thi thật sự đi qua cơ chế unwinding tương ứng**.

Ví dụ ngắn:

~~~java
static int calculate() {
    try {
        return 7;
    } finally {
        System.out.println("finish");
    }
}
~~~

Đoạn trên là **mã minh họa rời**, không phải tệp độc lập. Khi gọi `calculate()` bình thường, `finally` chạy **trước khi** caller nhận giá trị `7`.

Với ngoại lệ:

~~~java
static void fail() {
    try {
        throw new IllegalArgumentException("invalid");
    } finally {
        System.out.println("finish");
    }
}
~~~

Nếu caller gọi `fail()`, thông báo `finish` xuất hiện trong khi ngoại lệ vẫn tiếp tục lan truyền, miễn là khối finally hoàn tất bình thường.

Đây là **phép tương tự ngữ nghĩa**. After/finally advice không bắt buộc được công cụ triển khai bằng chính cú pháp `try/finally` trong source. Bộ máy AOP có thể weaving bytecode, xử lý tại call site, wrapper hoặc cơ chế khác. Nó phải tôn trọng các giới hạn của mô hình join point tương ứng.

### 6.1. “Bất kể thành công hay thất bại” có phạm vi

Khẳng định hợp lý là:

> Khi một join point **được chọn, được cơ chế quan sát và rời phạm vi theo con đường mà advice hỗ trợ**, after/finally advice nhằm chạy ở cả đường hoàn tất bình thường và đường ném ngoại lệ.

Không được hứa rằng advice chạy **sau mọi sự cố có thể xảy ra**. Chẳng hạn, tiến trình bị kết thúc cưỡng bức, JVM bị dừng ngay, hệ thống mất điện hoặc một vòng lặp không kết thúc có thể khiến không có sự kiện rời phạm vi để kích hoạt after/finally.

## 7. Ví dụ Java 17 hoàn chỉnh: kết quả bình thường và ngoại lệ

Ví dụ sau dùng Java thuần, không phụ thuộc thư viện ngoài. Một phương thức `invokeObserved` đóng vai trò wrapper mô phỏng **before advice**, **after-returning**, **after-throwing** và **after/finally** cho lời gọi đồng bộ.

**Đây là chương trình mô phỏng khái niệm**, không phải một implementation AOP tự weaving. Trong AOP thực sự, pointcut sẽ chọn join point và cơ chế weaving gắn advice tương ứng.

Lưu tệp `FinallyAdviceDemo.java`:

~~~java
import java.util.concurrent.Callable;

public class FinallyAdviceDemo {
    static <T> T invokeObserved(String operation, Callable<T> work)
            throws Exception {
        System.out.println("[before] " + operation);
        try {
            T result = work.call();
            System.out.println("[after-returning] " + operation);
            return result;
        } catch (Exception error) {
            System.out.println("[after-throwing] " + operation
                    + ": " + error.getMessage());
            throw error;
        } finally {
            System.out.println("[after-finally] " + operation);
        }
    }

    public static void main(String[] args) throws Exception {
        int value = invokeObserved("success", () -> {
            System.out.println("[business] success");
            return 42;
        });
        System.out.println("[caller] value=" + value);

        try {
            invokeObserved("failure", () -> {
                System.out.println("[business] failure");
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException error) {
            System.out.println("[caller] caught=" + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy với Java 17 hoặc mới hơn:

~~~bash
javac FinallyAdviceDemo.java
java FinallyAdviceDemo
~~~

Stdout chính xác:

~~~text
[before] success
[business] success
[after-returning] success
[after-finally] success
[caller] value=42
[before] failure
[business] failure
[after-throwing] failure: boom
[after-finally] failure
[caller] caught=boom
~~~

### 7.1. Đọc từng dòng của lần thành công

1. Wrapper in `[before] success`.
2. Lambda nghiệp vụ chạy và in `[business] success`.
3. `work.call()` trả `42`, nên nhánh `after-returning` chạy.
4. Trước khi hàm wrapper thực sự trả `42`, `finally` chạy và in `[after-finally] success`.
5. Caller mới có thể in `[caller] value=42`.

Điều đó cho thấy **after/finally advice đi trước bước caller nhận kết quả** theo wrapper này, dù lệnh `return result` nằm trong `try`.

### 7.2. Đọc lần thất bại

1. Lambda in `[business] failure`, rồi ném `IllegalStateException("boom")`.
2. Wrapper bắt ngoại lệ trong `catch (Exception error)`, in nhánh `after-throwing` và ném lại **chính ngoại lệ đó**.
3. `finally` vẫn chạy và in `[after-finally] failure`.
4. Caller bắt ngoại lệ và in `[caller] caught=boom`.

Điều này minh họa rằng **after/finally chạy ở cả hai nhánh**. Nó không nuốt ngoại lệ gốc; `throw error` bảo toàn đối tượng ngoại lệ trong tình huống này.

### 7.3. Những giới hạn của chương trình minh họa

- `Callable<T>` cho phép lambda ném `Exception`. Khối `catch (Exception)` ở ví dụ **không bắt các loại `Error`**, nhưng `finally` vẫn có thể chạy khi `Error` gây unwinding bình thường.
- Phần `after-returning` là mã `println` đặt trong `try`; nếu chính thao tác ghi nhận này ném lỗi, nhánh `catch` của wrapper cũng có thể bắt nó. Vì vậy đây là mô phỏng dễ hiểu **trên đường chạy bình thường**, không cam kết cùng cơ chế phân loại ngoại lệ của mọi hệ AOP khi nhiều advice được kết hợp.
- Không có pointcut được áp dụng tự động; chỉ lời gọi đi qua `invokeObserved` mới có hành vi bổ sung.
- Một advice thực có thể được cấu hình theo thứ tự khác, chạy quanh phạm vi khác hoặc có ràng buộc khác về ngoại lệ và giá trị trả về. Luôn dựa vào hợp đồng của cơ chế AOP đang dùng.

## 8. Gắn advice vào join point nào thì “kết thúc” có nghĩa gì?

Không nên hiểu “kết thúc” như một sự kiện chung của cả hệ thống. Cần chọn chính xác loại join point.

| Loại điểm theo mô hình hỗ trợ | “Kết thúc” được hiểu theo ranh giới nào? |
|---|---|
| **Method execution** | Phần thực thi phương thức được quan sát kết thúc bình thường hoặc ném ngoại lệ |
| **Method call** | Hoạt động gọi tại call site được quan sát rời phạm vi theo ngữ nghĩa công cụ |
| **Constructor execution** | Phạm vi thực thi constructor tương ứng kết thúc; không tự đồng nghĩa toàn bộ vòng đời đối tượng đã thành công |
| **Field get/set** | Hoạt động truy cập trường kết thúc nếu mô hình cho phép can thiệp loại điểm đó |
| **Sự kiện ở ranh giới khác** | Phụ thuộc sự kiện và cách công cụ định nghĩa lifecycle |

Một mô hình AOP chỉ hỗ trợ method invocation qua proxy **không đương nhiên can thiệp vào constructor, field access hay mọi lời gọi nội bộ**. Sau/finally advice không thể chạy tại một loại điểm mà công cụ hoàn toàn không nhận diện.

### 8.1. Method call và execution khác nhau

Một method có thể được gọi từ nhiều vị trí. Advice tại call site và advice tại method execution có thể bao những vùng khác nhau; nếu cả hai cùng tồn tại, việc ghép các lời gọi lồng nhau có thể tạo ra nhiều lần ghi nhận.

Thời điểm vào/ra của một join point không nên được suy từ tên phương thức mà không xác định **loại điểm** và **phạm vi weaving**.

## 9. After/finally không bảo đảm “thành công”

Một thao tác có thể trả về bình thường nhưng trạng thái nghiệp vụ là thất bại:

~~~java
record PaymentResult(boolean accepted, String reason) {}

PaymentResult pay() {
    return new PaymentResult(false, "insufficient balance");
}
~~~

Đây là mảnh Java minh họa, không phải chương trình độc lập. Method `pay()` trong ví dụ **trả về bình thường** một kết quả `accepted=false`. After-returning advice gắn với method execution có thể được kích hoạt; after/finally cũng có thể chạy.

Nếu muốn ghi hai số liệu **số lời gọi đã kết thúc** và **số thanh toán được chấp nhận**, cần hai tiêu chí khác nhau:

- Số **lời gọi đã kết thúc**: đếm khi rời đúng phạm vi.
- Số **thanh toán thành công theo nghiệp vụ**: đọc hợp đồng `PaymentResult.accepted()` tại nơi có thể xác định kết quả, nếu cơ chế cho phép và việc quan sát phù hợp.

Một ngoại lệ cũng có thể được bắt và xử lý **bên trong** method. Nếu cuối cùng method trả về bình thường, after-throwing ở ranh giới method bên ngoài không tự chạy cho ngoại lệ nội bộ đã được xử lý.

**Rút ra:** “Sau khi hoàn tất” là ngữ nghĩa **luồng điều khiển**; “thành công” có thể là ngữ nghĩa **nghiệp vụ**. Hai khái niệm cần tài liệu và kiểm thử riêng.

## 10. Câu hỏi quan trọng: after/finally có thấy ngoại lệ bị bắt bên trong không?

Xét:

~~~java
int load() {
    try {
        return parseValue();
    } catch (IllegalArgumentException error) {
        return -1;
    }
}
~~~

Đây là đoạn mã minh họa. Nếu `parseValue()` ném `IllegalArgumentException`, method `load()` có thể trả `-1\) bình thường. Một after/finally advice đặt ở **method execution của `load()`** vẫn chạy vì method hoàn tất; nhưng việc đó **không chứng minh có một ngoại lệ thoát qua join point `load()`**.

Một after-throwing advice ở chính ranh giới `load()` thường không có ngoại lệ để quan sát trong trường hợp này, vì ngoại lệ đã được xử lý bên trong.

Ngược lại, nếu advice được gắn vào join point tương ứng với `parseValue()` và ngoại lệ thoát ra từ chính method đó, advice tại ranh giới `parseValue()` có thể nhìn thấy đường thất bại.

Do đó cần luôn nói rõ: **“ngoại lệ thoát khỏi điểm nào?”**. Không thể dựa vào một dòng log after/finally để kết luận nơi phát sinh lỗi hoặc trạng thái cuối của toàn yêu cầu.

## 11. Advice chạy cuối có thể vô tình che giấu ngoại lệ gốc

Trong Java, nếu một khối `finally` tự ném ngoại lệ, nó có thể thay thế kết quả đang chờ trả về hoặc ngoại lệ đang lan truyền.

Ví dụ rời:

~~~java
static int example() {
    try {
        return 5;
    } finally {
        throw new IllegalStateException("logging failed");
    }
}
~~~

Method này **không trả về `5` bình thường**; ngoại lệ trong finally thay thế việc return. Đây là lý do một after/finally advice phục vụ quan sát phải đặc biệt thận trọng.

Cũng có trường hợp:

~~~java
static void exampleFailure() {
    try {
        throw new IllegalArgumentException("business failed");
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
~~~

Ngoại lệ `cleanup failed` thay thế đường ném ngoại lệ `business failed` trong đoạn này. Không được coi hai ngoại lệ tự động được giữ song song khi tự viết kiểu `finally` trên.

### 11.1. Nguyên tắc khi viết advice quan sát

Với advice chỉ nhằm ghi log hoặc thống kê, mục tiêu thường là **không làm thay đổi hành vi nghiệp vụ**. Cần thiết kế để lỗi của logger/telemetry không tùy tiện che nguyên nhân gốc. Cách chọn chính sách lỗi tùy mức độ bắt buộc của quan sát: ghi nhận best-effort, chuyển lỗi sang kênh khác hoặc để hoạt động thất bại có chủ đích.

Nếu việc dọn dẹp là **bắt buộc để bảo toàn an toàn/tính đúng đắn**, không nên tự động nuốt mọi ngoại lệ của cleanup. Khi nhiều lỗi xảy ra, cơ chế báo lỗi phải được định nghĩa rõ và có kiểm thử.

### 11.2. Vì sao tránh return trong finally?

~~~java
static int badExample() {
    try {
        throw new IllegalStateException("root problem");
    } finally {
        return 10;
    }
}
~~~

Đoạn này là mã minh họa có chủ đích về **anti-pattern**. Trong Java, return của finally sẽ che đường ném ngoại lệ đang chờ và khiến caller nhận `10`. Một advice mô phỏng after/finally không nên vô tình làm điều tương tự bằng cách thay kết quả hay bỏ qua hành vi gốc.

## 12. After/finally so với try-with-resources

Java có **try-with-resources**, cơ chế quản lý tài nguyên thực thi `AutoCloseable.close()` ngay khi thoát phạm vi. Khi cả xử lý chính lẫn `close()` đều ném lỗi, Java có cơ chế **suppressed exceptions** để thường giữ ngoại lệ chính, đồng thời lưu lỗi đóng tài nguyên như ngoại lệ phụ.

Ví dụ:

~~~java
try (var stream = openStream()) {
    consume(stream);
}
~~~

Đây là mã khái niệm không độc lập; `openStream` và `consume` chưa được định nghĩa. Nó minh họa **quyền sở hữu tài nguyên**: mã mở stream có thể quản lý vòng đời tại đúng vị trí bằng try-with-resources.

After/finally advice có thể hữu ích khi aspect sở hữu một **ngữ cảnh theo lời gọi** cần thu dọn trên nhiều phương thức. Nhưng nó không nên thay thế tùy tiện cho việc quản lý tài nguyên nghiệp vụ có chủ đích ở lớp sở hữu tài nguyên.

Ví dụ, kết nối hoặc stream được mở bên trong một nhánh nghiệp vụ và phải đóng theo hợp đồng riêng thường nên được quản lý tại ranh giới sở hữu đó. Một aspect chỉ biết rằng phương thức “đã kết thúc” có thể **không biết** tài nguyên nào còn hợp lệ hoặc cần bàn giao cho caller.

## 13. Dọn dẹp ngữ cảnh theo luồng: dùng finally đúng cách

Một trường hợp phù hợp với mô hình before + after/finally là **đặt tạm một ngữ cảnh** (ví dụ mã tương quan phục vụ trace) khi vào hoạt động và **phục hồi ngữ cảnh trước đó** khi rời hoạt động.

Khó khăn nảy sinh khi lời gọi **lồng nhau**: lớp bên trong không nên đơn giản xóa sạch ngữ cảnh của lớp ngoài. Cần ghi nhớ trạng thái trước khi vào và phục hồi đúng sau khi thoát.

### 13.1. Chương trình Java 17 có thể chạy độc lập

Lưu tệp `ContextCleanupDemo.java`:

~~~java
import java.util.concurrent.Callable;

public class ContextCleanupDemo {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    static <T> T withContext(String value, Callable<T> operation)
            throws Exception {
        String previous = CONTEXT.get();
        CONTEXT.set(value);

        try {
            return operation.call();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }

    static String context() {
        return String.valueOf(CONTEXT.get());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("before=" + context());

        withContext("outer", () -> {
            System.out.println("outer-enter=" + context());

            withContext("inner", () -> {
                System.out.println("inner=" + context());
                return null;
            });

            System.out.println("outer-restored=" + context());
            return null;
        });

        System.out.println("after-outer=" + context());

        try {
            withContext("failure", () -> {
                System.out.println("failing=" + context());
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException error) {
            System.out.println("caught=" + error.getMessage());
        }

        System.out.println("after-error=" + context());
    }
}
~~~

Chạy:

~~~bash
javac ContextCleanupDemo.java
java ContextCleanupDemo
~~~

Stdout chính xác:

~~~text
before=null
outer-enter=outer
inner=inner
outer-restored=outer
after-outer=null
failing=failure
caught=boom
after-error=null
~~~

### 13.2. Bài học của ví dụ

- Trước khi vào phạm vi, `ThreadLocal` chưa có giá trị trên luồng hiện tại, nên `String.valueOf(null)` cho chuỗi `"null"`.
- Phạm vi `inner` giữ lại `previous="outer"`, rồi sau đó **phục hồi outer** thay vì xóa.
- Khi phạm vi `outer` kết thúc, giá trị trước đó là null nên `remove()` tránh giữ giá trị không còn cần trên thread.
- Lần gọi `failure` ném `IllegalStateException`; `finally` vẫn phục hồi giá trị trước, nên caller thấy `after-error=null`.

Đây là **wrapper Java thuần** minh họa hành vi dọn dẹp tương tự after/finally. Nó không tự weaving và chỉ chạy khi caller gọi `withContext`.

### 13.3. Cảnh báo với bất đồng bộ

`ThreadLocal` gắn với **luồng hiện tại**. Khi một hoạt động tạo tác vụ để chạy trên thread khác, việc kết thúc của lời gọi tạo tác vụ và kết thúc thực sự của công việc **không đồng nghĩa**. Không được mặc định ngữ cảnh trong ví dụ được truyền sang thread khác, đặc biệt khi dùng thread pool hoặc các mô hình đồng thời khác.

Nếu ngữ cảnh cần đi qua tác vụ bất đồng bộ, phải có cơ chế **truyền/ngắt kết nối và dọn dẹp** phù hợp tại chính ranh giới chạy tác vụ; after/finally ở phương thức lập lịch ban đầu không tự quản lý vòng đời công việc ở thread khác.

## 14. Before và after/finally phải ghép đúng phạm vi

Khi trước hoạt động tạo trạng thái tạm, sau hoạt động phải thu dọn **đúng trạng thái tương ứng**.

Một lỗi phổ biến:

~~~text
before: tăng depth của context
after-finally: gán depth = 0
~~~

Nếu các join point lồng nhau, việc xóa về 0 ở nhánh bên trong có thể phá trạng thái của nhánh bên ngoài. Cần cấu trúc dạng **stack**, bộ đếm cân bằng hoặc cơ chế lưu-phục hồi.

Tương tự, nếu after/finally đọc thời điểm bắt đầu để tính duration, phải bảo đảm mỗi **lần thực thi** có bản ghi bắt đầu riêng. Không dùng một biến toàn cục đơn giản cho tất cả yêu cầu đồng thời.

### 14.1. “Một lần trước, một lần sau” không tự được bảo đảm bởi nhiều aspect

Nếu pointcut before và pointcut after không giống nhau, hoặc một advice khác khiến phần gốc không được gọi, có thể xuất hiện tình trạng mất cân đối. Một ranh giới **around** dùng `try/finally` bao đúng phần thực thi được tiếp tục đôi khi dễ diễn đạt cặp vào/ra hơn, nhưng vẫn cần tuân thủ semantics của cơ chế AOP.

Khi có nhiều advice, điểm vào/ra và thứ tự thực tế phải được kiểm chứng. Không dùng tên advice làm bằng chứng mọi nhánh hoạt động đã được bao phủ.

## 15. Thứ tự nhiều advice: after/finally nào chạy trước?

Giả sử hai aspect cùng áp dụng cho một join point:

- Một aspect đo thời lượng.
- Một aspect đặt ngữ cảnh trace và thu dọn ngữ cảnh khi kết thúc.

Nếu aspect đo thời lượng nằm **bên ngoài** aspect ngữ cảnh, phép đo có thể bao gồm chi phí thiết lập/thu dọn trace. Nếu nằm bên trong, phép đo có thể không bao gồm những bước đó.

Mô hình lồng nhau:

~~~text
outer.before
    inner.before
        business
    inner.after-finally
outer.after-finally
~~~

Đây là thứ tự trong trường hợp **các lớp bao được lồng theo sơ đồ**. Công cụ AOP có thể có quy tắc ưu tiên hoặc cấu hình sắp xếp riêng; không tự suy ra thứ tự chỉ từ tên class hoặc thời điểm khai báo.

### 15.1. Ngoại lệ từ advice phía trong

Nếu advice phía trong ném lỗi khi thu dọn, advice phía ngoài có thể nhìn thấy một đường thoát khác với ngoại lệ gốc, tùy semantics kết hợp. Điều này quan trọng khi thiết kế bộ đo, logger và chính sách xử lý lỗi.

Hãy kiểm thử **cả tình huống phần gốc ném lỗi** và **tình huống một advice tự phát sinh lỗi**, thay vì chỉ kiểm tra output của nhánh thành công.

## 16. Không phải mọi thất bại đều kích hoạt after/finally

Cụm “bất kể thành công hay thất bại” có điều kiện tiên quyết: **join point cần quan sát phải đã được kích hoạt và có một đường rời phạm vi mà công cụ xử lý**.

Những tình huống cần phân biệt:

1. **Pointcut không chọn điểm:** lời gọi không khớp tên, chữ ký hoặc điều kiện động → after/finally của pointcut này không chạy.
2. **Điểm ngoài mô hình join point:** công cụ không hỗ trợ loại điểm (ví dụ field access khi chỉ có method proxy) → không có điểm để gắn advice.
3. **Không đi qua weaving/runtime path:** caller gọi thẳng đối tượng đích thay vì proxy cần thiết → cơ chế proxy không quan sát.
4. **Điểm chưa thật sự xảy ra:** nhánh mã không được thực thi → không có lần gọi tương ứng.
5. **Lỗi xảy ra trước điểm được chọn:** ví dụ đánh giá đối số ném lỗi trước khi constructor execution bắt đầu → after/finally ở constructor execution không có hoạt động đã bắt đầu để xử lý.
6. **Hoạt động chưa kết thúc:** method chờ vô hạn hoặc một vòng lặp không rời phạm vi → chưa có sự kiện “after”.
7. **Tiến trình dừng bất thường:** runtime bị kết thúc cưỡng bức, mất điện hoặc cơ chế kết thúc không unwind thông thường → không có bảo đảm chạy cleanup.
8. **Một around advice bao ngoài bỏ qua phần gốc:** điểm thực thi phần gốc có thể không phát sinh; việc một advice after có chạy hay không phụ thuộc chính xác phạm vi và cách các advice được tổ chức.

Không được biến after/finally thành một cơ chế **bảo đảm giao sự kiện bền vững** cho mọi tình huống. Với yêu cầu kiểm toán không được mất bản ghi trong cả sự cố tiến trình, cần cơ chế lưu trữ/đồng bộ giao dịch hoặc thiết kế bền vững tương ứng.

## 17. Advice kết thúc và tác vụ bất đồng bộ: kết thúc của cái gì?

Giả sử:

~~~java
Future<?> future = executor.submit(() -> performLongJob());
~~~

Đây là mảnh Java minh họa, chưa phải chương trình độc lập. Nếu after/finally advice được gắn vào **method lập lịch hoặc submit**, nó có thể chạy khi method trả về `Future`, trong khi công việc thực sự vẫn chưa hoàn tất.

Các mốc thời gian khác nhau:

~~~text
T0: vào submit(...)
T1: submit(...) trả Future      ← method execution kết thúc
T2: tác vụ bắt đầu trên worker
T3: tác vụ hoàn tất / thất bại  ← vòng đời công việc kết thúc
~~~

After/finally tại join point của `submit` phản ánh ranh giới **T1**. Muốn quan sát **T3**, cần một join point hoặc cơ chế callback/hoàn tất phù hợp với vòng đời tác vụ.

Một phương thức có thể trả `CompletionStage` hoặc đối tượng đại diện hoạt động bất đồng bộ. Trả đối tượng đại diện bình thường không đồng nghĩa tác vụ sẽ hoàn tất thành công. Advice gắn vào method return không tự biết kết quả cuối của tác vụ, trừ khi cơ chế có thêm hợp đồng theo dõi trạng thái ấy.

## 18. Advice kết thúc và resource cleanup: ai sở hữu tài nguyên?

Thao tác dọn dẹp nên đi cùng **quyền sở hữu**. Một aspect có thể tạo một timer hoặc context scope ở before/around và thu dọn nó ở after/finally. Nhưng nếu tài nguyên do nghiệp vụ mở rồi trả tham chiếu cho caller sử dụng tiếp, một advice “thấy method kết thúc” không nên tùy tiện đóng tài nguyên đó.

Ví dụ, method `openStream()` trả về một `InputStream` cần được caller đọc sau khi lời gọi kết thúc. Nếu after/finally đóng stream ngay khi method trả, caller nhận một tài nguyên không sử dụng được.

Do đó, không nên đánh đồng:

- **Kết thúc một lời gọi method.**
- **Kết thúc quyền sở hữu tài nguyên.**
- **Kết thúc toàn bộ giao dịch hay yêu cầu.**

After/finally được đặt đúng khi phạm vi của việc dọn dẹp **thực sự kết thúc cùng join point**. Nếu vòng đời dài hơn, cần ranh giới khác.

## 19. Điều kiện động trong pointcut có thể làm số lần finally khác dự đoán

Một pointcut có thể chọn `processOrder` chỉ khi đối số `priority=true`. Những lời gọi `processOrder(false)` vẫn có thể thực thi nghiệp vụ, nhưng không khớp pointcut và **không có after/finally advice của pointcut đó**.

Pseudocode mô hình:

~~~text
pointcut observed:
    execution(processOrder(boolean priority))
    AND priority = true

after-finally observed:
    record("selected invocation finished")
~~~

Đây **không phải cú pháp của một công cụ AOP cụ thể**. Ý nghĩa: after/finally diễn ra khi **join point được chọn** rời phạm vi. Không thể lấy số dòng record để suy ra tổng mọi lời gọi `processOrder` nếu pointcut chỉ bao phủ một tập con.

Ngoài điều kiện động, phạm vi weaving cũng có thể giới hạn việc quan sát. Số lần advice xuất hiện phải được diễn giải theo **tập join point thực sự được chọn và đi qua cơ chế can thiệp**.

## 20. Thiết kế bộ đo thời lượng với after/finally

Một ví dụ hữu ích là đo thời gian đồng bộ:

~~~text
before:
    t0 = monotonicTime()

after-finally:
    elapsed = monotonicTime() - t0
    recordElapsed(elapsed)
~~~

Pseudocode trên là mô hình khái niệm. Trong Java, một nguồn thời gian đo khoảng là `System.nanoTime()`; giá trị của nó có ý nghĩa đo **chênh lệch** chứ không đại diện mốc thời gian lịch.

Các điểm cần kiểm tra:

- Mỗi lần join point được chọn có **t0 riêng**, kể cả lời gọi lồng nhau và đồng thời.
- `t0` phải được tạo ở ranh giới phù hợp và sẵn có khi after/finally chạy.
- Thời lượng phản ánh **phần nào được bao bởi advice**, không tự động toàn bộ vòng đời nghiệp vụ.
- Kết thúc bằng ngoại lệ vẫn có thời lượng; muốn phân loại thành công/thất bại cần thông tin bổ sung.
- Nếu logger/metrics bị lỗi, không được vô ý che giấu ngoại lệ nghiệp vụ.
- Chi phí của chính hệ thống đo cần được đánh giá ở các join point tần suất cao.

Không nên in một giá trị thời lượng cụ thể như thể đó là stdout xác định: số đo phụ thuộc máy và môi trường thực thi.

## 21. Các lỗi phổ biến khi thiết kế after/finally advice

### 21.1. Ghi nhãn “SUCCESS” khi chỉ biết đã thoát

Sai: after/finally luôn ghi `SUCCESS`. Điều đó làm nhánh ngoại lệ cũng bị báo thành công.

Sửa: ghi `FINISHED` nếu chỉ biết đã rời phạm vi; ghi thành công/thất bại dựa trên **đúng thông tin kết quả/ngoại lệ** được cho phép.

### 21.2. Giữ `ThreadLocal` sau khi lời gọi kết thúc

Nếu before đặt context mà after/finally không xóa/phục hồi, thread pool có thể tái sử dụng luồng chứa context cũ. Sửa bằng cấu trúc lưu-phục hồi theo phạm vi và kiểm thử cả nhánh lỗi lẫn nhánh lồng nhau.

### 21.3. Thao tác trong finally làm mất ngoại lệ gốc

Ghi log, serialize dữ liệu hoặc đóng tài nguyên có thể tự lỗi. Cần hợp đồng xử lý lỗi riêng, tránh thay thế nguyên nhân chính một cách không mong muốn.

### 21.4. Cho rằng after/finally luôn được chạy một lần

Một vòng lặp vô hạn không thoát; một around advice có thể bỏ qua phần gốc; pointcut có thể không khớp; một cơ chế có thể quan sát điểm ở nhiều lớp bao khác nhau. Phải đo số lần theo **đúng join point, đúng runtime path**.

### 21.5. Nhầm return của method với hoàn tất công việc bất đồng bộ

Nếu method trả handle của tác vụ đang chạy, after/finally của method chỉ quan sát method. Muốn đo công việc phải xác định ranh giới hoàn tất tác vụ.

### 21.6. Dọn dẹp tài nguyên không thuộc quyền sở hữu

Một aspect tự động đóng đối tượng trả về hoặc tài nguyên do caller còn sử dụng có thể phá hợp đồng API. Dọn dẹp phải gắn với chủ thể sở hữu tài nguyên.

### 21.7. Bỏ qua thứ tự nhiều advice

Một aspect thay đổi ngữ cảnh và một aspect đọc ngữ cảnh có thể tương tác khác nhau tùy thứ tự vào/ra. Cần quy định ranh giới, thứ tự và kiểm thử hành vi cùng hoạt động.

## 22. Quy trình thiết kế và xác minh một after/finally advice

1. **Nêu chính xác mục tiêu:** ghi “đã rời phạm vi”, dọn ngữ cảnh, đo thời lượng hay thực hiện chính sách nào?
2. **Chọn loại join point:** method execution, method call hoặc loại khác có được công cụ hỗ trợ?
3. **Định nghĩa pointcut:** điểm nào được bao gồm, loại trừ, điều kiện động nào có thể khiến advice không chạy?
4. **Xác định ranh giới hoàn tất:** return bình thường, ngoại lệ đi ra khỏi join point, hay lifecycle dài hơn như tác vụ bất đồng bộ?
5. **Xác định quyền sở hữu dữ liệu/tài nguyên:** after/finally chỉ thu dọn trạng thái thuộc đúng phạm vi.
6. **Thiết kế chính sách lỗi của advice:** nếu cleanup/logging tự lỗi thì làm gì, có bảo toàn nguyên nhân gốc không?
7. **Xét lồng nhau, đồng thời và thứ tự aspect:** có cần lưu-phục hồi ngữ cảnh, dữ liệu theo từng lần gọi hay cấu hình ưu tiên?
8. **Kiểm thử nhánh return:** advice chạy đúng số lần, caller nhận nguyên kết quả.
9. **Kiểm thử nhánh throw:** advice chạy đúng số lần, ngoại lệ gốc và loại lỗi không bị mất khi không có chủ đích.
10. **Kiểm thử không khớp và đường ngoài weaving:** không gán nhầm việc không có advice thành lời gọi thành công.
11. **Kiểm thử vòng đời bất đồng bộ nếu có:** kết thúc method và kết thúc tác vụ có được phân biệt?
12. **Quan sát trên môi trường thực:** ghi rõ công cụ AOP, loại join point được hỗ trợ và phạm vi weaving; không suy từ một ví dụ wrapper Java thuần sang mọi công cụ.

Một chính sách after/finally tốt phải **đúng về phạm vi, đúng về thời điểm và an toàn với lỗi**, không chỉ tạo đủ hai dòng log trong một ví dụ đơn giản.

## 23. Bài tập tự kiểm tra

### Bài 1. Trả về bình thường

Một method `getTotal()` trả số `0` bình thường. Pointcut chọn method đó. After/finally advice có chạy không?

**Đáp án:** theo semantics after/finally thông thường, có, khi join point được quan sát kết thúc bình thường. Giá trị `0` không tự biến lời gọi thành thất bại.

### Bài 2. Ngoại lệ thoát khỏi method

Một method ném `IllegalArgumentException` ra caller. After-returning, after-throwing và after/finally có vai trò gì?

**Đáp án:** after-returning gắn vào ranh giới method **không được kích hoạt cho đường throw**; after-throwing phù hợp với đường ngoại lệ; after/finally nhằm chạy khi rời phạm vi ở đường này. Cơ chế cụ thể quyết định thứ tự và điều kiện chi tiết.

### Bài 3. Ngoại lệ được xử lý nội bộ

Method `load()` bắt một ngoại lệ rồi trả `-1`. Một after-throwing gắn vào `load()` có được kích hoạt vì “từng có lỗi bên trong” không?

**Đáp án:** không chỉ vì lý do đó. Ở ranh giới `load()`, method trả về bình thường. After/finally vẫn chạy khi method rời phạm vi.

### Bài 4. Vòng lặp không kết thúc

Một method được chọn bị kẹt trong vòng lặp vô hạn. After/finally đã chắc chắn chạy chưa?

**Đáp án:** chưa. Không có lần rời phạm vi bình thường/ngoại lệ để kích hoạt hành vi “after”.

### Bài 5. Ngoại lệ từ advice

Một thao tác nghiệp vụ ném `IllegalStateException("business")`, sau đó lời gọi ghi log trong `finally` cũng ném `IllegalArgumentException("log")`. Caller có chắc nhận lỗi nghiệp vụ ban đầu không?

**Đáp án:** không. Nếu viết Java `finally` trực tiếp mà ném ngoại lệ mới, lỗi đó có thể thay thế ngoại lệ đang lan truyền. Cần chính sách lỗi/cleanup rõ và kiểm thử.

### Bài 6. Tác vụ bất đồng bộ

Một method `enqueue()` trả về nhanh, còn worker xử lý sau vài phút. After/finally tại method `enqueue` biểu thị điều gì?

**Đáp án:** nó biểu thị lời thực thi `enqueue` rời phạm vi; không tự chứng minh worker đã hoàn tất hay thành công.

### Bài 7. Ngữ cảnh lồng nhau

Một outer advice đặt context “outer”; inner advice đặt “inner”. Inner kết thúc. Nên khôi phục giá trị nào?

**Đáp án:** giá trị **“outer”** trước đó, không xóa toàn bộ, nếu hợp đồng yêu cầu context lồng nhau.

### Bài 8. Pointcut không khớp

Một method `saveDraft()` không thuộc pointcut chọn `submit*`. After/finally advice của pointcut đó có phải chạy vì method đã kết thúc không?

**Đáp án:** không. Chỉ những join point **được chọn và được cơ chế can thiệp quan sát** mới thuộc phạm vi advice này.

## 24. Ghi nhớ

- **After/finally advice** diễn tả hành vi khi rời một join point đã được chọn, ở cả **normal return** và **exceptional exit** theo semantics của công cụ.
- **After-returning**, **after-throwing** và **after/finally** là những ý nghĩa khác nhau; không dùng từ “after” mà bỏ qua khác biệt.
- “Kết thúc” chỉ nói về **ranh giới được quan sát**, không tự nói “thành công nghiệp vụ”, “toàn bộ yêu cầu hoàn tất” hay “tác vụ bất đồng bộ hoàn tất”.
- Sau khi chạy advice, **giá trị trả về và ngoại lệ gốc** nên được bảo toàn theo hợp đồng; mã trong finally tự ném lỗi có thể làm thay đổi điều đó.
- Khi dọn dẹp, phải đúng **quyền sở hữu tài nguyên**, đúng **phạm vi ngữ cảnh** và an toàn với **lời gọi lồng nhau/đồng thời**.
- **Pointcut** quyết định những điểm có advice; **weaving** và runtime path quyết định những điểm thực sự được quan sát. Một hoạt động ngoài mô hình join point sẽ không được cứu bằng một after/finally advice rộng.
- Các tình huống không thoát thông thường, chấm dứt tiến trình và tác vụ đang chạy lâu hơn method không có bảo đảm “advice cuối sẽ chạy”.

Hãy luôn đặt hai câu hỏi khi thiết kế: **“Chính xác hoạt động nào đã kết thúc?”** và **“Nếu chính advice này thất bại thì kết quả/ngoại lệ của hoạt động gốc còn được giữ đúng hay không?”**. Đó là ranh giới giữa một after/finally advice dễ kiểm chứng và một hành vi kỹ thuật có thể vô tình làm thay đổi chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-005">Phân biệt hành vi sau hoàn tất với hành vi luôn dọn dẹp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt hành vi sau hoàn tất với hành vi luôn dọn dẹp trong AOP

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), một **advice** có thể chạy trước, sau hoặc bao quanh một điểm can thiệp (*join point*). Ở phía “sau”, có ba tình huống rất dễ bị nhầm:

1. Thao tác chính **trả về bình thường**: có kết quả thành công (có thể là `null` hoặc phương thức `void`).
2. Thao tác chính **kết thúc bằng ngoại lệ**: không có giá trị trả về thành công của lần gọi đó.
3. Dù theo nhánh nào, một số công việc **phải được dọn dẹp khi rời khỏi phạm vi**: giải phóng tài nguyên đã sở hữu, kết thúc bối cảnh đo đạc hoặc khôi phục trạng thái tạm thời.

Các mô hình AOP thường diễn tả tương ứng bằng **after-returning**, **after-throwing** và **after/finally**. Trong bài này, “hành vi sau hoàn tất thành công” được dùng để nói đến **after-returning**, còn “luôn dọn dẹp” là hành vi kiểu **finally** sau khi phạm vi điều khiển đã được thiết lập. Tên `after completion` trong một số thư viện có thể chỉ nhánh *cả thành công lẫn lỗi*, vì thế phải xem **ngữ nghĩa thực tế**, không chỉ dựa vào tên gọi.

Một khía cạnh quan sát có thể muốn ghi thời gian xử lý ở cả hai nhánh, nhưng chỉ thông báo “thành công” khi phương thức thực sự trả về bình thường. Nhầm hai ý này dẫn đến log sai, rò rỉ tài nguyên hoặc vô tình che khuất ngoại lệ nghiệp vụ.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Phân biệt chính xác **before**, **after-returning**, **after-throwing**, **after/finally** và **around** trong ngữ cảnh kết thúc một lời gọi.
- Giải thích vì sao “sau khi phương thức kết thúc” không tự đồng nghĩa “sau khi phương thức thành công”.
- Chọn advice phù hợp cho ghi nhận kết quả, kiểm toán, đo thời lượng, xử lý ngoại lệ và dọn tài nguyên.
- Mô tả thứ tự chạy khi có nhiều advice lồng nhau, cả đường trả về và đường ném lỗi.
- Chỉ ra được khi nào `finally` có hiệu lực và các trường hợp nó không thể bảo đảm việc dọn dẹp thực tế.
- Giữ nguyên **kết quả trả về, ngoại lệ gốc và số lần thực thi** đối với advice chỉ nhằm quan sát.
- Phân biệt việc một phương thức **trả về đối tượng tác vụ bất đồng bộ** với lúc công việc bất đồng bộ thực sự hoàn thành.
- Viết và đọc một ví dụ Java 17 chạy được, đối chiếu chính xác đầu ra thành công, thất bại và đường gọi bỏ qua proxy.

## 2. Mô hình tư duy: một điểm vào, nhiều đường kết thúc

Giả sử có chức năng `reserveTicket(id)`:

```text
Người gọi
   |
   v
[Before advice: ghi bắt đầu]
   |
   v
[Thao tác giữ vé]
   |
   +---- Trả về bình thường ----> [After-returning: ghi thành công]
   |                                      |
   +---- Ném ngoại lệ ------------> [After-throwing: ghi thất bại]
                                          |
                                 [After/finally: dọn dẹp]
                                          |
                                 Trả kết quả hoặc ném lỗi
```

Sơ đồ trên mô tả **hai nhánh kết quả hội tụ vào bước dọn dẹp**, không có nghĩa nhánh trả về và nhánh lỗi đều chạy. Một lần thực thi bình thường đi qua một trong hai nhánh kết quả, sau đó thực hiện phần dọn dẹp đã được thiết lập. Nếu chính advice trước đó ném lỗi hoặc chuyển hướng điều khiển trước khi phần dọn dẹp được thiết lập, phạm vi bao phủ sẽ khác.

### 2.1. Bảng so sánh ý nghĩa

| Loại hành vi | Điều kiện chạy | Có thể gắn với dữ kiện nào? | Dùng khi |
| --- | --- | --- | --- |
| **Before** | Trước điểm được chọn | Đối số, metadata, đối tượng liên quan | Ghi bắt đầu, kiểm tra điều kiện trước khi tiếp tục |
| **After-returning** | Thao tác/chuỗi bên trong trả về bình thường | Giá trị trả về nếu cơ chế hỗ trợ | Ghi thành công, quan sát kết quả hợp lệ |
| **After-throwing** | Thao tác/chuỗi bên trong kết thúc bằng ngoại lệ | Ngoại lệ được ném nếu được hỗ trợ | Ghi thất bại, phân loại lỗi |
| **After/finally** | Khi rời phạm vi đã được bảo vệ, bình thường hoặc do ngoại lệ | Một số dữ kiện ngữ cảnh, không tự có cả return/error | Thu hồi tài nguyên, kết thúc bối cảnh |
| **Around** | Bao quanh điểm và quyết định việc chuyển tiếp | Đối số trước, kết quả/lỗi sau khi tiếp tục | Điều phối đúng thứ tự, đo thời gian, tài nguyên có vòng đời |

**Chú ý về từ ngữ:** Nhiều mô hình gọi advice vô điều kiện là `after` hoặc `after finally`; một số API khác dùng tên `after completion`. Ngược lại, “completed successfully” thường mô tả **after-returning**. Cần kiểm tra hợp đồng của công nghệ để tránh đọc sai chữ “completion”.

### 2.2. Hoàn tất bình thường không nhất thiết là nghiệp vụ thành công

Một phương thức có thể trả về bình thường nhưng báo thất bại bằng dữ liệu:

```java
record PaymentResult(boolean approved, String reason) {}

PaymentResult charge() {
    return new PaymentResult(false, "DECLINED");
}
```

Đây là đoạn minh họa độc lập về mặt cú pháp Java nhưng chưa có chương trình `main`. `charge()` **trả về bình thường** nên về ngữ nghĩa điều khiển sẽ phù hợp với **after-returning**. Tuy nhiên kết quả `approved=false` cho biết thanh toán bị từ chối theo miền nghiệp vụ.

Vì thế:

- **After-returning** nói về cách kết thúc lời gọi, không tự xác nhận thành công nghiệp vụ.
- **After-throwing** nói về cách kết thúc do ngoại lệ, không tự quyết định lỗi đó có thể phục hồi hay không.
- **After/finally** nói về dọn dẹp khi rời phạm vi, không tự nói thao tác đã commit, được xác nhận hay được người dùng chấp nhận.

Nếu cần ghi “đã thanh toán thành công”, hãy kiểm tra **hợp đồng kết quả hoặc sự kiện nghiệp vụ được xác nhận**, không đơn giản dựa vào việc phương thức không ném lỗi.

## 3. Từ cách kết thúc đến hành vi cần chọn

Thay vì học lại định nghĩa dài của từng advice, hãy đi từ **kết cục tại đúng join point đang được quan sát**. Một lời gọi đồng bộ có hai đường thoát chính: trả về bình thường (*normal completion*) hoặc thoát bằng ngoại lệ (*exceptional completion*). Hai đường này dẫn tới hành vi ghi nhận khác nhau, nhưng **cả hai đều có thể cần giải phóng tài nguyên mà advice sở hữu**.

~~~text
Join point được chọn và thực sự đi qua cơ chế can thiệp
    |
    +-- Trả về bình thường ----> after-returning (quan sát kết quả)
    |                               |
    |                               +--> after/finally (dọn phạm vi)
    |
    +-- Ném lỗi ra biên -------> after-throwing (quan sát lỗi)
                                    |
                                    +--> after/finally (dọn phạm vi)
~~~

Sơ đồ mô tả điều kiện theo nghĩa khái niệm, **không cam kết thứ tự cú pháp của nhiều advice riêng biệt trong mọi công cụ**. Với around advice triển khai bằng `try/catch/finally`, luồng điều khiển có thể được đọc trực tiếp theo các khối lồng nhau. Nếu một advice phía trong ném hoặc chuyển đổi lỗi, tầng ngoài quan sát **kết cục của cả tầng trong**, chứ không nhất thiết kết cục nguyên thủy của target.

### 3.1. Nhánh return: biết *đã trả về*, chưa biết *thành công nghiệp vụ*

After-returning được chọn khi join point kết thúc bình thường; `null`, `false`, `Result.failure(...)` hoặc giá trị số `0` vẫn là **normal return** nếu không có ngoại lệ thoát ra. Phương thức `void` cũng có thể kết thúc bình thường, nhưng không có đối tượng kết quả để lấy như một return value thông thường. Cơ chế binding kết quả và lựa chọn method `void` cần kiểm tra ở công cụ AOP cụ thể.

Nếu chỉ cần ghi số lượt method hoàn tất bình thường, after-returning có thể phù hợp. Nếu muốn ghi đơn hàng **đã lưu bền vững** hoặc thanh toán **được xác nhận**, phải quan sát trạng thái nghiệp vụ hoặc ranh giới xác nhận tương ứng; một normal return chưa đủ.

After-returning thường dùng để quan sát kết quả. Nếu chính việc xuất metric/log từ advice ném lỗi, caller vẫn có thể gặp lỗi **sau khi target đã trả thành công**. Vì vậy lỗi của advice phải có chính sách riêng, không được mặc định luôn vô hại.

### 3.2. Nhánh throw: theo dõi *lỗi thoát khỏi biên*, không phải mọi lỗi từng phát sinh

After-throwing phản ứng khi một ngoại lệ phù hợp **thoát ra khỏi join point được chọn**. Nếu target bắt một `IOException` bên trong rồi trả dữ liệu dự phòng, method ngoài kết thúc bình thường: after-throwing trên method ấy không tự chạy chỉ vì từng có `throw` ở sâu bên trong.

Java có `Throwable`, `Exception`, `RuntimeException` và `Error`, nhưng cách bind/lọc loại lỗi tùy công nghệ. Quan sát lỗi khác với nuốt, thay hay phục hồi từ lỗi. Khi advice chỉ có nhiệm vụ ghi nhận, cần bảo toàn đối tượng/ý nghĩa ngoại lệ của target theo hợp đồng; một exception mới do logger hoặc formatter ném ra có thể che nguyên nhân ban đầu.

### 3.3. Nhánh rời phạm vi: dọn đúng tài nguyên bất kể return hay throw

After/finally dành cho công việc cần thực hiện khi **rời phạm vi đang được bảo vệ**, không dùng nhãn `SUCCESS` chỉ vì cleanup chạy. Điểm then chốt là advice đã thực sự bao phủ phạm vi đó; nếu pointcut không khớp, phần gốc không được chuyển tiếp, JVM bị kết thúc đột ngột hoặc thao tác không bao giờ rời phạm vi, không thể hứa “luôn chạy” theo nghĩa tuyệt đối.

Với một resource do advice tự sở hữu, sơ đồ tối giản:

~~~java
// Minh họa Java, Resource và proceed() chưa được định nghĩa.
Resource resource = openAdviceResource();
try {
    return proceed();
} finally {
    resource.close();
}
~~~

Nếu bước `openAdviceResource()` thất bại thì chưa có resource để đóng. Nếu advice phải mở **nhiều** tài nguyên nối tiếp, phải bảo vệ cả các tài nguyên đã mở thành công khi bước mở tiếp theo thất bại. Với `AutoCloseable`, `try-with-resources` thường diễn đạt quyền sở hữu và quy tắc *suppressed exceptions* rõ hơn một `finally` tự viết.

## 4. Điểm khác biệt quyết định: ghi nhận trạng thái hay hoàn trả quyền sở hữu?

Một bộ ghi log kết quả và một bộ dọn context không có cùng tiêu chí hoàn thành. Khi target trả về bình thường, logic quan sát có thể sử dụng return value; khi target throw, bộ ghi lỗi có thể dùng exception; nhưng **bộ dọn context, khóa hoặc resource do advice lấy phải xét cả hai**.

Ví dụ trong bài toán giữ vé:

- `reserveTicket("T-1")` trả `ReservationResult(rejected=true)`: after-returning có thể chạy vì method trả bình thường; after-throwing không chạy ở biên này; cleanup vẫn cần thực hiện.
- `reserveTicket("T-2")` ném `IllegalStateException` ra caller: after-throwing có thể chạy nếu pointcut và loại lỗi phù hợp; after-returning của cùng biên không chạy; cleanup vẫn cần thực hiện.
- Một advice ghi audit lỗi khi xuất sự kiện: đây là **lỗi phát sinh từ advice**. Kết quả caller quan sát phụ thuộc cấu trúc bao, chính sách lỗi và thứ tự các advice.
- Một kết nối do caller truyền vào còn cần được sử dụng sau khi lời gọi trả về: advice không được tự động đóng kết nối ấy chỉ vì có after/finally. Quyền sở hữu quyết định nơi cleanup.

Vì vậy hãy viết hai hợp đồng khác nhau: **khi nào một event được ghi** và **ai có trách nhiệm đóng/phục hồi tài nguyên nào**. Tránh biến cùng một nhánh “after” thành nơi mặc định vừa log thành công, vừa ghi lỗi, vừa đóng tất cả resource.

## 5. Ba câu hỏi trước khi chọn advice

1. **Biên nào đang được quan sát?** Method call, method execution hay proxy invocation; trả một `CompletionStage` có thể chỉ là trả handle chứ chưa phải completion của tác vụ nền. Kết cục được xét theo *biên đã chọn*, không theo cảm nhận “request đã xong”.
2. **Lời gọi rời biên theo đường nào?** Normal return (kể cả `null`, `void`, kết quả báo thất bại), exception thoát ra, hay thao tác tiếp tục bất đồng bộ. Việc chọn after-returning/after-throwing phải dựa trên đường này.
3. **Ai sở hữu tài nguyên và lỗi từ cleanup phải xử lý ra sao?** Cleanup có thể thất bại; với `try/finally` thông thường, lỗi trong `finally` có thể che return hoặc exception đang chờ. Không được tùy tiện nuốt lỗi cleanup nghiêm trọng, cũng không được để một lỗi telemetry tùy chọn đổi kết quả nghiệp vụ ngoài ý định.

Các phần tiếp theo dùng **ma trận đường điều khiển, ví dụ Java 17 có ba nhánh và tình huống tài nguyên thật** để kiểm chứng các quyết định trên. Điều cốt lõi của bài là phân biệt **mốc quan sát kết cục** với **trách nhiệm giải phóng tài nguyên** trong cùng một luồng thực thi.

## 6. So sánh bằng ma trận đường điều khiển

| Tình huống ở thao tác bên trong | After-returning | After-throwing | After/finally |
| --- | --- | --- | --- |
| Trả về `"OK"` | Có | Không | Có |
| Trả về `null` bình thường | Có theo ngữ nghĩa hoàn tất; binding phải phù hợp | Không | Có |
| Phương thức `void` kết thúc bình thường | Có nếu advice không đòi bind giá trị không hợp lệ | Không | Có |
| Trả `Result.failure` bình thường | Có | Không | Có |
| Ném `IllegalArgumentException` | Không | Có nếu khớp điều kiện ngoại lệ | Có |
| Ném `IOException` | Không | Có nếu khớp điều kiện ngoại lệ | Có |
| Advice trước đó chặn, không hề thực thi điểm đang xét | Phụ thuộc phạm vi và chuỗi bên trong có thực sự được vào hay không | Phụ thuộc phạm vi | Chỉ nếu cấu trúc finally của advice đã được thiết lập |
| Phương thức trả `CompletableFuture` đang pending | Có thể thấy lời gọi tạo future đã trả về | Không chỉ vì future có thể thất bại sau này | Dọn dẹp phạm vi lời gọi tạo future, chưa chắc dọn sau tác vụ async |

Đây là **ma trận ý tưởng**. Một biểu thức advice có thể bị giới hạn thêm bởi pointcut, kiểu giá trị ràng buộc hoặc loại join point; bảng không khẳng định một advice được khai báo sẽ tự động áp dụng tại mọi điểm.

### 6.1. Kết thúc phương thức không phải hoàn tất giao dịch

Một phương thức có thể trả về trước khi giao dịch ngoài, ghi xuống đĩa, gửi mạng hoặc công việc phụ thực sự được xác nhận. Ghi `SUCCESS` ở after-returning **chưa chứng minh** dữ liệu đã commit hoặc bên nhận đã chấp nhận.

Nếu cần sự kiện “đã xác nhận đặt vé”, phải chọn **ranh giới nghiệp vụ có cam kết rõ ràng** hoặc dữ liệu kết quả thể hiện trạng thái xác nhận; không suy ra điều đó từ lifecycle chung của một lời gọi.

## 7. Ví dụ Java 17 độc lập: ba nhánh và một bước dọn dẹp

Ví dụ này dùng **JDK dynamic proxy** để minh họa hành vi bổ sung quanh phương thức giao diện. Mã này **không biến đổi bytecode của lớp đích** và không tự can thiệp lời gọi trực tiếp, constructor hay mọi loại join point AOP. Nó chỉ làm rõ thứ tự before, after-returning, after-throwing và finally, hoàn toàn chạy được bằng Java 17 trở lên.

Tạo tệp `AdviceCompletionDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AdviceCompletionDemo {
    interface TicketPort {
        String book(String ticketCode);
    }

    static final class TicketService implements TicketPort {
        @Override
        public String book(String ticketCode) {
            if (ticketCode == null || ticketCode.isBlank()) {
                throw new IllegalArgumentException("Mã vé rỗng");
            }
            return "BOOKED:" + ticketCode;
        }
    }

    private static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static TicketPort withAdvice(TicketPort target) {
        return (TicketPort) Proxy.newProxyInstance(
                TicketPort.class.getClassLoader(),
                new Class<?>[] { TicketPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("book")) {
                        return invokeTarget(method, target, args);
                    }

                    System.out.println("[BEFORE] book");
                    System.out.println("[ACQUIRE] context");
                    try {
                        Object result;
                        try {
                            result = invokeTarget(method, target, args);
                        } catch (Throwable error) {
                            System.out.println("[AFTER_THROWING] "
                                    + error.getClass().getSimpleName());
                            throw error;
                        }

                        System.out.println("[AFTER_RETURNING] " + result);
                        return result;
                    } finally {
                        System.out.println("[CLEANUP] context");
                    }
                });
    }

    public static void main(String[] args) {
        TicketPort observed = withAdvice(new TicketService());

        System.out.println("CALLER " + observed.book("T-7"));

        try {
            observed.book(" ");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER_ERROR " + error.getMessage());
        }

        System.out.println("DIRECT " + new TicketService().book("T-9"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 AdviceCompletionDemo.java
java AdviceCompletionDemo
```

Đầu ra chính xác:

```text
[BEFORE] book
[ACQUIRE] context
[AFTER_RETURNING] BOOKED:T-7
[CLEANUP] context
CALLER BOOKED:T-7
[BEFORE] book
[ACQUIRE] context
[AFTER_THROWING] IllegalArgumentException
[CLEANUP] context
CALLER_ERROR Mã vé rỗng
DIRECT BOOKED:T-9
```

### 7.1. Phân tích đường thành công

1. Proxy nhận lời gọi `observed.book("T-7")`, in `[BEFORE]` và ghi bước tạo bối cảnh minh họa.
2. `invokeTarget` chuyển lời gọi tới phương thức đích; đích trả `BOOKED:T-7`.
3. Không phát sinh lỗi ở thân đích, nên nhánh `catch` bên trong không chạy. Dòng `[AFTER_RETURNING]` được in.
4. Trước khi `return result` thực sự trả điều khiển ra khỏi `try`, khối `finally` in `[CLEANUP]`.
5. Người gọi nhận kết quả, sau đó in `CALLER BOOKED:T-7`.

`[ACQUIRE] context` và `[CLEANUP] context` chỉ là **mô phỏng vòng đời tài nguyên**, không phải hệ thống mở/đóng tài nguyên thật. Mục đích là thấy đúng thời điểm dọn dẹp.

### 7.2. Phân tích đường ngoại lệ

Với `observed.book(" ")`, `ticketCode.isBlank()` là `true` nên đích ném `IllegalArgumentException`.

- Reflection bọc lỗi đích trong `InvocationTargetException`; `invokeTarget` bóc `getCause()` và ném lại nguyên nhân ban đầu.
- Khối `catch` bên trong in `[AFTER_THROWING] IllegalArgumentException`, rồi ném lại đúng đối tượng lỗi.
- Khối `finally` bên ngoài vẫn in `[CLEANUP]` trước khi ngoại lệ đến người gọi.
- Người gọi bắt ngoại lệ và in thông báo `Mã vé rỗng`.
- Không có `[AFTER_RETURNING]` cho lần gọi lỗi.

### 7.3. Vì sao lời gọi trực tiếp không có advice?

`new TicketService().book("T-9")` dùng trực tiếp đối tượng đích, không đi qua proxy. Do đó chỉ in `DIRECT BOOKED:T-9`. Đây là giới hạn của **cơ chế proxy đang minh họa**, không phải khẳng định mọi cơ chế weaving đều bỏ qua lời gọi trực tiếp.

Một cơ chế biến đổi bytecode phù hợp có thể can thiệp tại điểm thực thi khác; mỗi công nghệ có mô hình join point và phạm vi kết hợp riêng.

### 7.4. Vì sao đặt phần dọn dẹp trong `finally` ngoài?

Cấu trúc được chọn là:

```text
before
acquire
try:
    try:
        result = proceed-to-target
    catch error:
        after-throwing(error)
        throw error
    after-returning(result)
    return result
finally:
    cleanup
```

Đây là **giả mã cấu trúc**, giúp tránh viết phần dọn dẹp hai lần. Nó bảo đảm cleanup chạy khi đoạn bên trong kết thúc theo return hoặc throw trong phạm vi try, theo ngữ nghĩa Java thông thường. Dòng `[AFTER_RETURNING]` nằm **ngoài khối catch cho target**, nên nếu việc ghi success tự gặp lỗi, ta không gán nhầm nó là lỗi do phương thức đích tạo ra.

Nếu `[ACQUIRE]` là thao tác cấp phát tài nguyên thật và có thể lỗi, cần cấu trúc lại việc nhận/quản lý tài nguyên để chỉ dọn những gì đã cấp thành công; không dựa máy móc vào vị trí hai câu `println` này.

## 8. Ví dụ ranh giới tài nguyên thật: try-with-resources

Trong Java, lựa chọn thường rõ hơn cho đối tượng có vòng đời là **try-with-resources**. Nó bảo vệ việc đóng tài nguyên đã mở thành công và có quy tắc xử lý lỗi đóng khác với một `finally` thủ công.

Ví dụ độc lập, lưu thành `ResourceAdviceDemo.java`:

```java
public class ResourceAdviceDemo {
    static final class TraceScope implements AutoCloseable {
        TraceScope() {
            System.out.println("OPEN");
        }

        @Override
        public void close() {
            System.out.println("CLOSE");
        }
    }

    static String run(boolean fail) {
        try (TraceScope ignored = new TraceScope()) {
            if (fail) {
                throw new IllegalArgumentException("Đích thất bại");
            }
            return "OK";
        }
    }

    public static void main(String[] args) {
        System.out.println("RESULT " + run(false));
        try {
            run(true);
        } catch (IllegalArgumentException error) {
            System.out.println("ERROR " + error.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 ResourceAdviceDemo.java
java ResourceAdviceDemo
```

Đầu ra:

```text
OPEN
CLOSE
RESULT OK
OPEN
CLOSE
ERROR Đích thất bại
```

Đây là ví dụ về **cơ chế dọn dẹp Java** để áp dụng khi xây dựng một advice có tài nguyên. Nó không phải một aspect đầy đủ hay bằng chứng mọi hệ AOP hỗ trợ nguồn tài nguyên tự động giống nhau. Nếu `close()` ném lỗi khi thân `try` cũng đã ném lỗi, Java try-with-resources có thể giữ lỗi thân try là lỗi chính và đính lỗi đóng dưới dạng **suppressed exception**.

### 8.1. Advice phải sở hữu đúng tài nguyên

Một advice chỉ nên đóng tài nguyên nó sở hữu hoặc được hợp đồng giao trách nhiệm đóng. Ví dụ nếu người gọi đưa vào một `InputStream` và giữ quyền sở hữu, advice không nên tự động đóng stream chỉ vì lời gọi đã kết thúc. Ngược lại, nếu advice tự mở scope đo đạc, nó phải khôi phục scope theo đúng vòng đời được định nghĩa.

Dùng câu hỏi: “Ai cấp phát? Ai sở hữu? Trường hợp nào được phép đóng? Nếu đã đóng rồi thì sao?” trước khi biến một hành vi dọn dẹp thành chính sách cắt ngang.

## 9. Thứ tự khi có nhiều advice và aspect

Khi các aspect lồng nhau, thứ tự before và sau không nhất thiết giống nhau. Giả sử:

```text
Outer(
   Inner(
      Task()
   )
)
```

Nếu cả Outer và Inner đều ghi trước, sau thành công và dọn dẹp, đường bình thường có thể theo cấu trúc:

```text
Outer before
Inner before
Task
Inner after-returning
Inner finally
Outer after-returning
Outer finally
```

Khi Task ném lỗi:

```text
Outer before
Inner before
Task throws
Inner after-throwing
Inner finally
Outer after-throwing
Outer finally
```

Đây là **thứ tự mô hình A(B(Task))** với các khối bao quanh được tổ chức theo cấu trúc này. Thứ tự ưu tiên mặc định giữa nhiều aspect không được suy ra từ tên lớp hay thứ tự viết trong file; phải tra quy tắc của cơ chế đang dùng và kiểm chứng bằng chương trình.

### 9.1. Một advice bên trong có thể thay kết quả mà advice bên ngoài quan sát

Nếu Inner bắt lỗi rồi trả một giá trị thay thế, Outer có thể thấy **đường trả về bình thường** của chuỗi bên trong, dù Task đã ném lỗi. Khi đó:

- Inner có thể chạy logic xử lý lỗi của chính nó.
- Outer có thể chạy after-returning vì thứ nó quan sát là **Inner(Task)** đã hoàn tất bình thường.
- Không có mâu thuẫn: hai advice đang quan sát **hai ranh giới khác nhau**.

Tương tự, nếu Inner trả kết quả nhưng after-returning của Inner ném lỗi, Outer có thể đi vào nhánh after-throwing. Muốn phân tích đúng phải biết **ranh giới điều khiển mà mỗi advice bao quanh**.

### 9.2. Retry và đo thời gian

```text
Timing(Retry(Task))  -> thời gian toàn bộ chuỗi thử lại
Retry(Timing(Task))  -> thời gian riêng từng lần thử (nếu mỗi lần qua Timing)
```

Việc giải phóng tài nguyên theo lần thử hay theo cả chuỗi sẽ khác nhau. Một scope dành riêng cho từng lần gọi bên trong retry phải được dọn theo từng lần; một scope dành cho toàn bộ yêu cầu có thể cần bao ngoài. Không đặt dọn dẹp sai vòng đời rồi giả định `finally` tự sửa được thiết kế đó.

## 10. Bất đồng bộ: “trả về” chưa chắc “công việc đã hoàn tất”

Đây là một trong những sai lầm khó phát hiện nhất.

Một phương thức có thể nhanh chóng trả về `CompletableFuture<Result>`, một đối tượng đại diện cho công việc chạy tiếp:

```java
CompletableFuture<String> loadAsync() {
    return CompletableFuture.supplyAsync(() -> slowOperation());
}
```

Đây là **mã minh họa**; `slowOperation()` cần được định nghĩa trong chương trình thực.

Trong một cơ chế can thiệp **lời gọi phương thức**, after-returning có thể chạy **ngay khi `loadAsync()` trả về đối tượng future**, dù tác vụ bên trong vẫn đang chạy và có thể thành công hoặc thất bại sau đó.

Nếu finally đóng tài nguyên mà công việc trong future vẫn cần dùng, ta có thể **đóng quá sớm**.

### 10.1. Hai nghĩa của “completion”

| Sự kiện | Điều đã biết | Điều chưa chắc |
| --- | --- | --- |
| `loadAsync()` trả một future | Việc tạo/trả đối tượng future kết thúc bình thường | Tác vụ bất đồng bộ hoàn thành hay chưa |
| Future hoàn tất thành công | Kết quả bất đồng bộ đã có | Chưa chắc thao tác ngoài như hệ thống thanh toán đã được xác nhận theo nghĩa nghiệp vụ |
| Future hoàn tất ngoại lệ | Phép tính bất đồng bộ báo lỗi | Không có kết quả thành công của tác vụ |
| Scope của advice quanh lời gọi kết thúc | Vòng đời đồng bộ của lời gọi đã đóng | Tài nguyên được công việc async sử dụng có thể vẫn cần sống |

Để theo dõi **kết quả bất đồng bộ**, một chính sách có thể cần gắn callback lên future hoặc dùng cơ chế context-aware do kiến trúc định nghĩa. Chẳng hạn, `whenComplete((value, error) -> ...)` được gọi khi future hoàn tất; cách `error` được biểu diễn có thể là ngoại lệ bọc của cơ chế future.

### 10.2. Lựa chọn thời điểm dọn dẹp với async

- Nếu tài nguyên chỉ phục vụ **việc tạo future**, có thể đóng khi phương thức trả về.
- Nếu tài nguyên phục vụ **tác vụ trong future**, phải quản lý vòng đời xuyên qua completion thật, đồng thời truyền đúng quyền sở hữu tới tác vụ.
- Nếu bối cảnh gắn với luồng hiện tại, chuyển việc sang luồng khác có thể làm mất bối cảnh; cần cơ chế truyền và khôi phục riêng.
- Nếu callback có thể chạy trên luồng khác, phần dọn dẹp phải an toàn đồng thời và tránh đóng trùng.

Không dùng duy nhất finally bao quanh phương thức tạo future để tuyên bố “mọi công việc bất đồng bộ đã được dọn”. Phạm vi dọn dẹp phải tương ứng với **vòng đời thực của tài nguyên**.

## 11. Kiểm toán, log và ý nghĩa thành công

Một chính sách kiểm toán thường cần trả lời “ai làm gì, khi nào, kết quả nào đã được xác nhận”. Dùng lifecycle advice mà không định nghĩa tiêu chí thành công sẽ dễ sinh sự kiện sai.

Ví dụ `submitOrder()` có thể:

1. Trả một mã yêu cầu để xử lý sau (đồng bộ return, nghiệp vụ chưa hoàn tất).
2. Ném lỗi vì đầu vào không hợp lệ (throw).
3. Trả một kết quả `REJECTED` bình thường (return nhưng nghiệp vụ từ chối).
4. Hoàn tất xử lý thành công và dữ liệu đã được xác nhận (thành công nghiệp vụ).

After-returning chỉ xác nhận **mục 1, 3, 4 đi qua đường trả về bình thường**; nó không phân biệt nghiệp vụ đã xác nhận hay chưa nếu không đọc hợp đồng kết quả.

### 11.1. Ghi nhận sự kiện đúng ranh giới

- Với log kỹ thuật: có thể dùng before + after-returning/after-throwing + finally để theo dõi mọi kết quả lời gọi.
- Với đo thời gian: có thể lấy mốc bắt đầu và dùng finally ghi thời lượng, dù thành công hay lỗi, miễn phạm vi được bảo vệ đúng.
- Với sự kiện “đơn đã được xuất”: cần căn cứ vào **trạng thái nghiệp vụ được xác nhận** chứ không chỉ một lần return.
- Với tài nguyên: đóng ở nơi sở hữu đúng vòng đời, không đóng đối tượng của người gọi khi hợp đồng không cho phép.
- Với kiểm soát quyền: nếu kiểm tra thất bại ở before, phải bảo đảm công việc chính không chạy; không đợi after-returning mới quyết định có cho phép.

## 12. Lỗi dọn dẹp: thiết kế và xử lý một cách có chủ đích

### 12.1. Làm mất lỗi gốc

Mã sau có rủi ro:

```java
try {
    operation();
} finally {
    throw new IllegalStateException("Cleanup failed");
}
```

Đây là **đoạn minh họa**, `operation()` chưa được định nghĩa. Nếu `operation()` đã ném lỗi khác, lỗi từ finally có thể thay thế lỗi gốc. Nếu operation trả về thành công, người gọi vẫn nhận ngoại lệ cleanup.

Điều này không luôn sai về nghiệp vụ: có trường hợp đóng tài nguyên thất bại thực sự phải khiến thao tác báo lỗi. Nhưng quyết định ấy phải được thiết kế, không nảy sinh vô tình do code logging.

### 12.2. Lỗi cleanup có được bỏ qua không?

Không có quy tắc “bỏ qua mọi lỗi dọn dẹp” áp dụng cho tất cả:

- Với tài nguyên quan trọng, lỗi đóng/flush có thể làm mất dữ liệu và cần được báo.
- Với phép đo best-effort, lỗi hệ đo không nhất thiết nên làm thất bại nghiệp vụ.
- Với kiểm toán bắt buộc, việc ghi thất bại có thể thay đổi quyền thực hiện nghiệp vụ và đòi hỏi cơ chế tin cậy hơn là vài dòng log.

Cần phân biệt **nghĩa vụ vận hành**, **độ tin cậy** và **hợp đồng API**, rồi quyết định rõ cách giữ ngoại lệ chính, cách đính lỗi phụ hoặc điều kiện làm thao tác thất bại.

### 12.3. Dọn dẹp không đồng nghĩa rollback

`finally` có thể gọi `unlock()` hoặc `close()`. Nó **không tự đảo ngược** các cập nhật dữ liệu đã xảy ra. Rollback giao dịch phụ thuộc cơ chế giao dịch và ranh giới nghiệp vụ; nếu tác vụ đã gửi yêu cầu mạng hoặc phát sự kiện, những tác dụng phụ đó có thể đã xảy ra.

Đừng viết `after/finally` rồi tin rằng hệ thống “quay về trạng thái ban đầu” khi ném lỗi. Đó là hai trách nhiệm khác nhau.

## 13. Kiểm thử từng nhánh của advice

Chỉ kiểm tra lời gọi thành công chưa đủ. Một bộ kiểm thử phù hợp nên bao phủ:

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Đích trả kết quả bình thường | After-returning và cleanup chạy; after-throwing không chạy |
| Đích ném ngoại lệ | After-throwing và cleanup chạy; lỗi gốc đến người gọi |
| Đích trả `null` hoặc `void` | Không nhầm thành throw, binding return phù hợp |
| Đích trả đối tượng `Result.failure` | Không suy luận thành công nghiệp vụ chỉ từ normal return |
| Advice before ném lỗi | Xác định scope nào đã được thiết lập và tài nguyên nào cần dọn |
| Advice after-returning ném lỗi | Quan sát ngoại lệ ra ngoài và cleanup; không nhầm là lỗi từ target |
| Cleanup ném lỗi | Xác minh lỗi gốc được giữ hay bị che và chính sách mong muốn |
| Lời gọi ngoài pointcut | Không chạy advice ngoài phạm vi |
| Lời gọi bỏ qua proxy hoặc lớp chưa được weaving | Ghi nhận giới hạn của cơ chế |
| Hai aspect lồng nhau | Kiểm tra thứ tự và phạm vi của từng advice |
| Tác vụ trả future trước khi hoàn tất | Phân biệt return của phương thức và completion của future |
| Nhiều luồng cùng gọi | Bối cảnh và tài nguyên mỗi lời gọi không lẫn nhau |

### 13.1. Phát hiện dọn dẹp hai lần hoặc thiếu dọn dẹp

Nếu cùng một thao tác được bọc bởi hai lớp quản lý tài nguyên, có thể có hai lời gọi `close()` hoặc khôi phục trạng thái sai thứ tự. Nếu một advice chỉ cleanup sau thành công, tài nguyên có thể bị rò rỉ khi ném lỗi.

Một kiểm thử tốt nên đếm **số lần chiếm/nhả** tài nguyên và điều kiện đúng của chúng, thay vì chỉ nhìn một dòng log xuất hiện.

### 13.2. Chẩn đoán có hệ thống

Khi cleanup không chạy hoặc chạy sai:

1. Kiểm tra pointcut có khớp điểm mong muốn và loại join point có được hỗ trợ không.
2. Kiểm tra code đã được weaving hoặc lời gọi thật đi qua proxy/interceptor chưa.
3. Kiểm tra advice có thiết lập vùng try/finally **trước** điểm có thể phát sinh lỗi hay không.
4. Xác định lỗi đến từ target, advice trước, advice sau hay cleanup.
5. Kiểm tra có nhiều aspect đang thay đổi đường điều khiển không.
6. Kiểm tra vòng đời tài nguyên kết thúc đồng bộ hay bất đồng bộ.
7. Kiểm tra các đường return, throw, cancellation và kết thúc bất thường tương ứng.
8. Kiểm tra after-returning có đang gắn nhãn “thành công nghiệp vụ” cho kết quả từ chối không.
9. Kiểm tra việc ghi log/kiểm toán có làm lộ dữ liệu nhạy cảm hoặc che lỗi gốc không.

## 14. Sai lầm thường gặp

| Hiểu sai hoặc lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Cho rằng “after” luôn là “thành công” | Ghi trạng thái sai ở nhánh lỗi | Phân biệt after-returning, after-throwing, after/finally |
| Dùng after-returning để đóng mọi tài nguyên | Rò rỉ khi target ném lỗi | Dùng phạm vi finally/đóng tài nguyên theo vòng đời |
| Ghi “SUCCESS” vì phương thức không ném lỗi | Nhầm `Result.failure` hoặc công việc còn pending | Đọc hợp đồng kết quả và ranh giới xác nhận |
| Dùng after-throwing để cleanup nhưng quên đường bình thường | Rò rỉ hoặc thiếu khôi phục khi thành công | Tách cleanup thành hành vi bao phủ cả hai nhánh |
| Nghĩ `finally` luôn chạy trong mọi sự cố | Kỳ vọng sai khi VM dừng cưỡng bức | Thiết kế khôi phục tài nguyên ngoài tiến trình khi cần |
| Đặt `try` sau nhiều bước cấp phát | Không dọn phần đã cấp khi bước sau lỗi | Thiết kế thứ tự cấp phát/giải phóng rõ ràng |
| Ném lỗi/return trong finally mà không xét hậu quả | Che ngoại lệ hoặc đổi kết quả gốc | Xử lý lỗi dọn dẹp có chủ đích |
| Đóng tài nguyên của người gọi | Gây lỗi ở lần sử dụng tiếp theo | Xác định rõ ownership |
| Cho rằng finally quanh hàm trả future đồng nghĩa future đã xong | Đóng quá sớm, mất bối cảnh | Buộc cleanup vào completion thật nếu tài nguyên sống qua async |
| Không xét nhiều aspect lồng nhau | Sai thứ tự đo, retry, cleanup | Xác định precedence và kiểm thử đường chạy |
| Chỉ test nhánh thành công | Không phát hiện rò rỉ/lỗi che khuất | Test return, throw, lỗi advice và bypass |

## 15. Bài tập vận dụng có đáp án

### Bài 1 — Chọn advice đúng

Bạn muốn ghi thời lượng xử lý của mọi lời gọi thuộc một nhóm phương thức, kể cả lời gọi thất bại; đồng thời muốn ghi kích thước kết quả khi trả về bình thường.

**Đáp án:** Có thể dùng scope bắt đầu đo trước lời gọi và kết thúc đo bằng hành vi kiểu finally, đồng thời dùng after-returning để quan sát kích thước kết quả. Nếu kết quả là `null` hoặc `void`, phải tuân thủ hợp đồng binding phù hợp. Không dùng after-returning làm nơi duy nhất kết thúc đồng hồ vì nhánh throw bị bỏ sót.

### Bài 2 — Kết quả từ chối

`authorize()` trả `AuthorizationResult(false)` mà không ném ngoại lệ. After-throwing có chạy vì “xác thực thất bại” không?

**Đáp án:** Không. Về luồng điều khiển, phương thức đã trả về bình thường. After-returning là nhánh phù hợp; việc xác định được phép hay bị từ chối phải đọc giá trị bên trong đối tượng kết quả.

### Bài 3 — Dọn dẹp và lỗi gốc

Một lời gọi ném `IOException` rồi `finally` ném `IllegalStateException`. Người gọi chắc chắn nhận `IOException` không?

**Đáp án:** Không. Trong `try/finally` Java thông thường, ngoại lệ từ finally có thể thay ngoại lệ gốc. Có thể cần thiết kế giữ lỗi chính và ghi lỗi phụ; try-with-resources có quy tắc suppressed exception hữu ích khi tài nguyên `AutoCloseable` được sử dụng đúng.

### Bài 4 — Lỗi trước khi vào vùng bảo vệ

Một advice cấp tài nguyên A thành công, cấp B thất bại, rồi mới dự kiến đi vào `try/finally` để đóng A/B. A có chắc được đóng không?

**Đáp án:** Không. Nếu lỗi xảy ra trước khi đi vào `try`, phần `finally` đó không được thiết lập. Cần tổ chức scope sao cho mỗi tài nguyên cấp thành công có đường đóng khi bước tiếp theo thất bại.

### Bài 5 — Future còn chạy

`sendAsync()` trả `CompletableFuture` chưa hoàn tất. Advice `after-returning` in `SUCCESS` và `finally` đóng kết nối mà tác vụ trong future còn sử dụng. Có những vấn đề nào?

**Đáp án:** Hàm đã trả về future nhưng tác vụ chưa chắc thành công. Log `SUCCESS` có thể sai nghĩa, và kết nối có thể bị đóng quá sớm. Cần phân biệt completion của lời gọi đồng bộ với completion của công việc bất đồng bộ và gắn ownership/cleanup vào vòng đời thật.

### Bài 6 — Hai lớp around

Có `Outer(Inner(Task))`. Task trả `OK`, nhưng after-returning của Inner ném lỗi. Outer có nhất thiết chạy after-returning không?

**Đáp án:** Không. Outer quan sát chuỗi Inner(Task); khi Inner ném lỗi, Outer có thể đi vào nhánh after-throwing dù Task đã trả `OK`. Cần xét ranh giới mà từng advice bao quanh, không chỉ kết quả của thân Task.

### Bài 7 — Retry và finally

Một advice retry gọi tác vụ ba lần; mỗi lần tạo một scope theo dõi riêng. Bạn nên dọn scope theo từng lần hay một lần ngoài cùng?

**Đáp án:** Nếu mỗi lần thử sở hữu scope riêng, phải dọn **theo từng lần**, kể cả khi lần đó thất bại rồi được thử lại. Scope chung của toàn yêu cầu có thể đặt bên ngoài chuỗi retry nếu đó là vòng đời đã định. Không để một scope theo lần thử tồn tại qua các lần gọi sau mà không có chủ đích.

## 16. Ghi nhớ

- **After-returning** chỉ gắn với **trả về bình thường**; điều này chưa bảo đảm thành công nghiệp vụ.
- **After-throwing** gắn với **đường ném ngoại lệ**; dữ liệu lỗi khác dữ liệu kết quả thành công.
- **After/finally** nhằm **dọn dẹp khi rời phạm vi được bảo vệ**, thường bao phủ cả return lẫn throw, nhưng không hứa chạy trong mọi sự cố của tiến trình.
- Việc dọn dẹp chỉ đúng khi **phạm vi và quyền sở hữu tài nguyên** đã được xác định; lỗi cleanup có thể che lỗi gốc nếu xử lý bất cẩn.
- **Nhiều aspect lồng nhau** làm thay đổi thứ tự; một advice bên ngoài quan sát kết quả của chuỗi bên trong, có thể khác kết quả đích trực tiếp.
- Với tác vụ bất đồng bộ, **phương thức trả future** và **future thật sự hoàn tất** là hai thời điểm khác nhau.
- Đối với advice chỉ quan sát, hãy **bảo toàn kết quả, ngoại lệ và số lần thực thi**, kiểm thử cả nhánh thành công, lỗi, cleanup failure và đường bypass.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-006">Thứ tự advice trong trường hợp có nhiều lớp can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự advice trong trường hợp có nhiều lớp can thiệp

Khi chỉ có một advice ở một điểm nối (join point), ta có thể hình dung đơn giản: mã **before** chạy trước nghiệp vụ, mã **after** chạy sau nghiệp vụ. Nhưng trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một join point có thể được nhiều aspect hoặc nhiều advice cùng chọn. Lúc đó, câu hỏi quan trọng không còn chỉ là “advice nào chạy?” mà còn là **“chạy theo thứ tự nào, cái nào bao ngoài cái nào, và kết quả sẽ ra sao nếu một lớp chặn, ném lỗi hoặc thay đổi kết quả?”**.

Đây không chỉ là vấn đề thứ tự dòng log. Thứ tự có thể quyết định kiểm tra quyền xảy ra trước hay sau khi mở giao dịch, phép đo thời gian có bao gồm kiểm tra quyền không, ngoại lệ có đi tới lớp rollback không, và dữ liệu nào được ghi lại khi nghiệp vụ thất bại.

Bài viết trình bày các quy tắc tư duy tổng quát, dùng mô hình advice lồng nhau và ví dụ Java 17 có thể chạy độc lập. **Không tồn tại một thứ tự mặc định phổ quát cho mọi hệ thống AOP.** Cú pháp khai báo độ ưu tiên, cách kết hợp advice, xử lý trường hợp đồng mức và một số ngữ nghĩa phụ thuộc vào mô hình join point/weaving của công cụ cụ thể.

## 1. Từ một advice đến nhiều lớp can thiệp

Trong AOP:

- **Join point** là một điểm thực thi được mô hình hỗ trợ can thiệp, chẳng hạn method call hoặc method execution.
- **Pointcut** chọn tập join point mà một advice/aspect quan tâm.
- **Advice** là xử lý bổ sung trước, sau hoặc bao quanh điểm đó.
- **Aspect** nhóm các pointcut, advice và trạng thái liên quan tới một mối quan tâm cắt ngang.
- **Weaving/combination** quyết định cách hành vi bổ sung được kết hợp với luồng nghiệp vụ.

Giả sử thao tác `pay()` đồng thời chịu ba mối quan tâm:

1. **Kiểm soát quyền:** từ chối yêu cầu không có quyền thanh toán.
2. **Giao dịch:** chuẩn bị tài nguyên, commit khi thành công, rollback khi thất bại.
3. **Quan sát:** ghi log và đo độ trễ.

Nếu cả ba cùng áp dụng tại một điểm, việc “có cả ba” chưa đủ bảo đảm đúng. Một thứ tự là kiểm tra quyền **trước** khi mở tài nguyên giao dịch; một thứ tự khác là mở giao dịch rồi mới kiểm tra quyền. Cả hai đều có thể được một cơ chế kết hợp tạo ra, nhưng chi phí và ngữ nghĩa khác nhau.

Điều đầu tiên cần xác định là **tập advice thực sự áp dụng**. Hai advice chỉ có thể tranh thứ tự tại một lần thực thi nếu pointcut của chúng đều khớp join point đang xét và cơ chế thật sự can thiệp được tại điểm đó. Quy tắc chọn điểm và quy tắc sắp xếp là **hai trách nhiệm riêng**.

## 2. Tư duy chính: các lớp lồng nhau

Với các xử lý kiểu **around** có quyền tiếp tục gọi hành vi kế tiếp, một cách hiểu trực quan là tạo các lớp bọc:

~~~text
Bên gọi
  |
  v
[OUTER: kiểm tra quyền]
  |
  v
[MIDDLE: giao dịch]
  |
  v
[INNER: ghi log]
  |
  v
[NGHIỆP VỤ]
  |
  v
[INNER: kết thúc ghi log]
  |
  v
[MIDDLE: kết thúc giao dịch]
  |
  v
[OUTER: kết thúc]
  |
  v
Bên gọi nhận kết quả/ngoại lệ
~~~

Đây là **một thứ tự lồng nhau do ta lựa chọn để minh họa**, không phải thứ tự bắt buộc của bất kỳ nền tảng nào.

Một lớp bao ngoài thường **đi vào trước** lớp bên trong; khi các lớp cho phép gọi tiếp và thực thi bình thường, quá trình kết thúc diễn ra theo chiều **ngược lại**:

- **Đi vào:** Outer → Middle → Inner → nghiệp vụ.
- **Đi ra:** nghiệp vụ → Inner → Middle → Outer.

Mẫu này tương tự các lời gọi hàm lồng nhau và cơ chế ngăn xếp (stack), thường được gọi là “vào trước, ra sau” trong cách tổ chức wrapper.

Tuy nhiên, đừng suy rộng rằng **mọi advice before/after đều được triển khai bằng wrapper**. Hệ thống AOP có thể weave advice ở các điểm trước/sau cụ thể hoặc áp dụng quy tắc ưu tiên riêng. Mô hình lồng nhau ở đây giúp phân tích hành vi; với nền tảng thật, phải xác minh thứ tự theo hợp đồng của nó.

## 3. Các loại advice và thứ tự trong mỗi nhánh

Các dạng phổ biến theo ý nghĩa:

| Advice | Thời điểm | Khi nhiều lớp lồng nhau |
| --- | --- | --- |
| **Before** | Trước khi phần được can thiệp bắt đầu | Ở mô hình wrapper: outer trước inner |
| **After returning / success** | Sau khi phần được can thiệp kết thúc bình thường | Inner xử lý thành công rồi outer |
| **After throwing / error** | Sau khi phần được can thiệp kết thúc bằng ngoại lệ | Inner quan sát lỗi rồi outer, nếu lỗi được truyền tiếp |
| **After / finally** | Khi thoát phạm vi dù thành công hay thất bại | Inner kết thúc/cleanup trước outer |
| **Around** | Bao lấy phần thực thi kế tiếp | Có thể thực hiện trước/sau, gọi tiếp, chặn hoặc xử lý kết quả và lỗi |

Các tên và khả năng chính xác khác nhau theo cơ chế AOP. Đặc biệt, **after returning không đồng nghĩa finally**; nhánh thành công không chạy khi phần được bao quanh kết thúc bằng ngoại lệ. **After throwing** cũng không được xem như đoạn chạy bắt buộc sau mọi lời gọi; nó gắn với đường lỗi theo hợp đồng.

### 3.1 Nhánh thành công

~~~text
OUTER before
  MIDDLE before
    INNER before
      BUSINESS success
    INNER after-success
    INNER finally
  MIDDLE after-success
  MIDDLE finally
OUTER after-success
OUTER finally
~~~

Nếu các lớp cùng có xử lý success và finally, thứ tự cụ thể của **success trước finally** trong đoạn mô tả trên đến từ cấu trúc `try/catch/finally` minh họa ở phần ví dụ. Không tự lấy sơ đồ này làm tuyên bố về ưu tiên giữa các loại advice được khai báo độc lập trong mọi công cụ.

### 3.2 Nhánh thất bại

~~~text
OUTER before
  MIDDLE before
    INNER before
      BUSINESS throws error
    INNER after-error
    INNER finally
  MIDDLE after-error
  MIDDLE finally
OUTER after-error
OUTER finally
CALLER receives error
~~~

Nếu lớp trong **nuốt ngoại lệ và trả về một kết quả bình thường**, lớp ngoài có thể thấy **thành công**, chứ không còn thấy lỗi. Nếu lớp trong ném **ngoại lệ mới**, lớp ngoài thấy ngoại lệ mới. Như vậy, **thứ tự và khả năng thay đổi luồng điều khiển của advice ảnh hưởng trực tiếp tới các nhánh chạy**.

### 3.3 Vì sao finally có giá trị?

Các việc như dừng đồng hồ, xóa ngữ cảnh tạm, trả tài nguyên hoặc ghi nhận kết thúc nên được đặt ở cơ chế **được bảo đảm thực thi lúc rời phạm vi tương ứng**. Trong Java, `finally` là một công cụ diễn đạt hành vi này. Tuy nhiên, một lỗi mới ném trong `finally` vẫn có thể che mất ngoại lệ ban đầu hoặc thay đổi kết quả; mã cleanup cần được thiết kế cẩn thận.

## 4. Thứ tự trước không nhất thiết trùng thứ tự sau

Một nhầm lẫn phổ biến là:

> “Nếu A chạy trước B, thì A cũng phải chạy trước B ở phần after.”

Điều này không đúng với **cấu trúc lồng nhau**. Nếu A bao B, A có thể chạy trước B ở lúc vào, nhưng khi kết thúc thì B chạy phần after/finally trước A.

~~~text
A.before
  B.before
    nghiệp vụ
  B.after
A.after
~~~

Nếu đọc log mà chỉ nhìn các dòng before rồi kết luận thứ tự after phải giống như vậy, người học có thể tưởng rằng công cụ chạy sai. Thực tế, đảo chiều khi thoát là hệ quả thông thường của việc **A bao ngoài B**.

Nên phân biệt hai định nghĩa về ưu tiên:

- **Thứ tự vào (entry/outer precedence):** A ở ngoài B nên mã trước trong A chạy sớm hơn B.
- **Thứ tự thoát (exit/unwind order):** A nhận kết quả sau khi B đã hoàn thành phần hậu xử lý.

Một số mô hình AOP định nghĩa quan hệ ưu tiên theo kiểu **lớp nào có precedence cao hơn sẽ bao ngoài**; các mô hình khác có thể đặt quy tắc cụ thể cho từng loại advice. Chỉ khi biết chính xác mô hình của nền tảng mới kết luận thứ tự từng phần.

## 5. Ví dụ Java 17 chạy độc lập: ba lớp advice

Chương trình sau **tự tổ chức các lớp bọc bằng Java** nhằm mô phỏng *ngữ nghĩa nhiều around advice* mà không cần một thư viện AOP cụ thể. Nó minh họa:

- Thứ tự đi vào `SEC → TX → LOG`.
- Thứ tự đi ra `LOG → TX → SEC`.
- Sự khác nhau giữa đường thành công và đường ném ngoại lệ.
- `finally` của từng lớp vẫn chạy khi phần nằm trong nó ném lỗi.

Lưu thành file `AdviceOrderDemo.java`:

~~~java
public class AdviceOrderDemo {
    @FunctionalInterface
    interface Action {
        String call() throws Exception;
    }

    static Action layer(String name, Action next) {
        return () -> {
            System.out.println(name + " BEFORE");
            try {
                String result = next.call();
                System.out.println(name + " SUCCESS " + result);
                return result;
            } catch (Exception error) {
                System.out.println(name + " ERROR "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println(name + " FINALLY");
            }
        };
    }

    static String perform(String value) {
        System.out.println("BUSINESS " + value);
        if ("bad".equals(value)) {
            throw new IllegalArgumentException("bad request");
        }
        return "ok:" + value;
    }

    static void run(String value) {
        System.out.println("CASE " + value);

        Action business = () -> perform(value);
        Action withLog = layer("LOG", business);
        Action withTransaction = layer("TX", withLog);
        Action withSecurity = layer("SEC", withTransaction);

        try {
            System.out.println("RESULT " + withSecurity.call());
        } catch (Exception ex) {
            System.out.println("CALLER " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        run("good");
        run("bad");
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac AdviceOrderDemo.java
java AdviceOrderDemo
~~~

Stdout chính xác theo đoạn mã:

~~~text
CASE good
SEC BEFORE
TX BEFORE
LOG BEFORE
BUSINESS good
LOG SUCCESS ok:good
LOG FINALLY
TX SUCCESS ok:good
TX FINALLY
SEC SUCCESS ok:good
SEC FINALLY
RESULT ok:good
CASE bad
SEC BEFORE
TX BEFORE
LOG BEFORE
BUSINESS bad
LOG ERROR IllegalArgumentException
LOG FINALLY
TX ERROR IllegalArgumentException
TX FINALLY
SEC ERROR IllegalArgumentException
SEC FINALLY
CALLER bad request
~~~

### 5.1 Vì sao thứ tự ra lại đảo chiều?

Trong `run`, `layer("LOG", business)` là lớp trong cùng; `layer("TX", withLog)` bọc ngoài nó; và `layer("SEC", withTransaction)` là lớp ngoài cùng. Gọi `withSecurity.call()` thực hiện:

1. In `SEC BEFORE`.
2. Gọi tiếp vào `TX`, in `TX BEFORE`.
3. Gọi tiếp vào `LOG`, in `LOG BEFORE`.
4. Gọi phần `business`.
5. Phần `LOG` nhận kết quả hoặc lỗi, chạy khối `finally`.
6. Phần `TX` nhận kết quả hoặc lỗi từ `LOG`, chạy `finally`.
7. Phần `SEC` nhận kết quả hoặc lỗi từ `TX`, chạy `finally`.

Đây là **ngăn xếp các lời gọi**. Khi xảy ra ngoại lệ, các `catch` và `finally` tương ứng được xử lý từ phạm vi trong ra ngoài, trừ khi một lớp đã bắt và biến đổi lỗi.

### 5.2 Ví dụ không có một cơ chế kiểm tra quyền hay giao dịch thật

Các tên `SEC` và `TX` chỉ đặt nhãn cho lớp bọc để người đọc nhìn thấy thứ tự. Chương trình **không xác thực quyền**, **không mở/commit/rollback giao dịch**. Muốn tạo aspect thật cho những chức năng đó phải có chính sách, tài nguyên và các ranh giới thực thi cụ thể.

Chương trình cũng không đăng ký pointcut, không tự weave bytecode và không chứng minh thứ tự mặc định của bất kỳ nền tảng AOP nào. Mô hình wrapper này minh họa rõ một **thứ tự do người thiết kế chủ động ghép**, để phân tích hành vi trước khi chuyển sang cú pháp công cụ thật.

## 6. Nếu thay thứ tự các lớp thì điều gì thay đổi?

Giả sử giữ cùng ba xử lý, nhưng đổi từ:

~~~text
SEC → TX → LOG → BUSINESS
~~~

sang:

~~~text
LOG → TX → SEC → BUSINESS
~~~

Sự khác nhau không chỉ ở thứ tự in:

- Nếu **SEC ở ngoài cùng** và có thể từ chối trước khi gọi tiếp, yêu cầu không hợp lệ có thể dừng **trước khi TX mở giao dịch**.
- Nếu **TX ở ngoài SEC**, tài nguyên giao dịch có thể được mở rồi mới bị từ chối, dẫn đến chi phí và nhánh rollback/cleanup phát sinh sớm hơn.
- Nếu **LOG ở ngoài SEC**, một số lần từ chối có thể được ghi log từ lớp ngoài (nếu lớp log xử lý được lỗi trả về).
- Nếu **LOG ở trong SEC**, khi SEC từ chối và **không gọi tiếp**, LOG bên trong có thể **không chạy**, nên bộ đếm nằm trong LOG có thể không thấy các lần từ chối.
- Nếu advice đo thời gian nằm **ngoài TX**, phép đo có thể gồm cả thời gian mở/đóng giao dịch; nằm **trong TX** thì phạm vi đo hẹp hơn.

Một thiết kế cần nói rõ **muốn tính những hoạt động nào vào phép đo/log** và **muốn lớp nào có quyền từ chối trước lớp nào**. Thứ tự chỉ có thể được đánh giá là đúng khi gắn với mục tiêu nghiệp vụ và vận hành.

## 7. Before/after riêng rẽ và around không thể gộp máy móc

Trong mô hình wrapper, `before`, `after-success`, `after-error` và `finally` chỉ là các điểm mã nằm trước/sau lệnh gọi tiếp. Nhưng trong một hệ thống AOP thật, chúng có thể là những **advice độc lập** với quy tắc ưu tiên khác nhau.

Ví dụ diễn tả mong muốn:

~~~text
Before(Security)
Before(Audit)
Execution(Business)
AfterReturning(Audit)
AfterFinally(Security)
~~~

Đây là **một trình tự mong muốn**, không phải cú pháp/đầu ra mặc định. Ta chưa thể biết nó có đúng với công cụ đang dùng hay không cho tới khi xác nhận:

1. Security và Audit có cùng khớp điểm đang xét?
2. Cơ chế có hỗ trợ before, after returning, finally tại loại join point đó?
3. Có khai báo precedence hay thứ tự advice nào được công cụ hỗ trợ?
4. Thứ tự áp dụng giữa **các aspect khác nhau** và **nhiều advice trong cùng một aspect** có được định nghĩa riêng không?
5. Nếu cùng độ ưu tiên hoặc thiếu quan hệ precedence, hành vi có xác định không?

**Đừng suy thứ tự chạy chỉ từ thứ tự dòng source code, thứ tự định nghĩa aspect, thứ tự tên file hoặc thứ tự liệt kê trong Reflection.** Chỉ khi hợp đồng công cụ nêu rõ chúng mới có thể được dùng làm quy tắc.

## 8. Cách chỉ định thứ tự khi nền tảng có hỗ trợ

Các mô hình AOP có thể cung cấp cơ chế **priority**, **precedence**, **ordering**, hoặc quan hệ **trước–sau** giữa aspect/advice. Tên cú pháp và cách diễn giải khác nhau, nên ví dụ dưới dùng giả mã ý định:

~~~text
outermost: Security
next:      Transaction
innermost: Observability

effective nesting:
Security( Transaction( Observability( Business ) ) )
~~~

Trình tự này thể hiện thứ tự **lồng nhau**. Nếu một nền tảng dùng số thứ tự, phải đọc kỹ **số lớn hay nhỏ có ưu tiên cao**. Nếu dùng danh sách hoặc quan hệ thứ tự, phải đọc xem nó điều khiển **entry order**, **precedence**, **execution order của before**, hay một ý nghĩa riêng.

### 8.1 Quan hệ thứ tự bộ phận và những trường hợp đồng mức

Không phải mọi mối quan tâm đều cần một thứ tự tổng quát. Ví dụ có thể chỉ bắt buộc `Security` bao ngoài `Transaction`, còn một aspect đo lường độc lập có thể đặt ngoài cả hai hoặc trong tùy mục tiêu. Khi không định nghĩa đầy đủ các quan hệ liên quan, nền tảng có thể có cơ chế tie-break riêng hoặc không bảo đảm thứ tự.

Vì vậy nên thiết kế **những quan hệ thứ tự thật sự có ý nghĩa**, và kiểm thử nơi còn khả năng thay đổi.

### 8.2 Tránh yêu cầu vòng mâu thuẫn

Các quy tắc như `A trước B`, `B trước C`, nhưng `C trước A` tạo ra chu trình. Một thứ tự tuyến tính không thể thỏa cả ba. Hệ thống có thể phát hiện lỗi, từ chối cấu hình hoặc áp dụng quy tắc khác; không nên tự suy kết quả. Khi thứ tự aspect ngày càng nhiều, nên vẽ đồ thị phụ thuộc để tránh mâu thuẫn.

### 8.3 Thứ tự ổn định khi thay đổi mã

Thêm một aspect mới có thể làm thay đổi thứ tự có hiệu lực tại một join point, ngay cả khi mã nghiệp vụ không đổi. Các bài kiểm thử nên kiểm tra **quan hệ chức năng quan trọng** thay vì chỉ so sánh một tập tên advice. Ví dụ: “quyền được kiểm tra trước khi tạo tác dụng phụ” quan trọng hơn “Security có số ưu tiên 10”.

## 9. Nhiều advice của cùng một aspect

Một aspect có thể chứa nhiều advice, thậm chí nhiều advice cùng thuộc một loại (ví dụ hai before). Không nên mặc định thứ tự nguồn khai báo của chúng là thứ tự chạy.

Có thể gặp các tình huống:

- Mô hình công bố thứ tự trong cùng aspect riêng với thứ tự giữa các aspect.
- Mô hình cho phép sắp xếp rõ nhiều advice.
- Thứ tự khi đồng hạng không được bảo đảm, hoặc phụ thuộc cách weaving.
- Một advice khớp ở call join point, advice khác khớp ở execution join point: trông như cùng một thao tác nghiệp vụ nhưng thực ra tác động tại **hai điểm nối khác nhau**.

Nếu hai advice phải nối tiếp có phụ thuộc dữ liệu chặt chẽ, chẳng hạn bước B buộc phải đọc trạng thái do A vừa khởi tạo, đôi khi **gom chúng thành một advice với thứ tự nội bộ rõ ràng** dễ duy trì hơn việc dựa vào thứ tự tình cờ giữa hai advice độc lập.

Trường hợp mã xử lý cần logic cụ thể theo trạng thái kết quả, cân nhắc gom nó vào một cấu trúc `try/catch/finally` rõ ràng nếu mô hình hiện tại không đưa ra bảo đảm về thứ tự.

## 10. Các kịch bản làm thứ tự thay đổi luồng thực thi

### 10.1 Before advice ném lỗi hoặc chặn tiếp tục

Một before advice có thể phát hiện dữ liệu sai hoặc không đủ quyền rồi ném ngoại lệ. Khi đó:

- Nghiệp vụ chưa chắc được chạy.
- Những advice nằm **sau điểm từ chối trên đường đi vào** có thể chưa chạy.
- Các lớp bao ngoài có xử lý lỗi/finally có thể vẫn quan sát được việc từ chối.
- Không được giả định một `after returning` liên quan đến nghiệp vụ sẽ chạy khi nghiệp vụ chưa thực sự diễn ra.

Mục tiêu kiểm soát quyền thường yêu cầu việc từ chối diễn ra **trước tác dụng phụ cần bảo vệ**, và mọi lối vào thích hợp đều chịu kiểm tra. Thứ tự advice không thay thế việc xác định đúng phạm vi join point.

### 10.2 Around advice bỏ qua `proceed`

Một around advice có thể quyết định không gọi tiếp (nếu cơ chế cho phép). Khi đó các lớp bên trong và nghiệp vụ không chạy. Điều này có thể được dùng cho caching, circuit breaking hoặc một chính sách bỏ qua có chủ đích, nhưng phải tuân thủ hợp đồng phương thức và không vô tình bỏ qua kiểm tra bắt buộc.

Nếu lớp cache nằm **ngoài** kiểm tra quyền và trả cache hit mà không gọi tiếp, lớp kiểm tra quyền bên trong sẽ không chạy. Thiết kế đó có thể tạo lỗ hổng nếu dữ liệu cache phải được bảo vệ theo người dùng. Vì vậy cần phân tích rõ **lớp nào có thể short-circuit lớp nào**.

### 10.3 Around advice gọi tiếp nhiều lần

Khi cơ chế cho phép gọi `proceed` nhiều lần, các advice bên trong và nghiệp vụ có thể thực thi nhiều lần. Điều này có thể hợp lệ cho retry, nhưng có thể tạo tác dụng phụ lặp lại: thanh toán hai lần, tăng counter hai lần hoặc gửi thông báo trùng.

Một lớp đo lường ở ngoài retry có thể đếm **một yêu cầu logic**, còn lớp ở trong có thể đếm **nhiều lần thử**. Hai phép đo đều có thể đúng, nhưng đơn vị đo khác nhau. Cần ghi rõ tên metrics: `requests` khác `attempts`.

### 10.4 Advice chuyển đổi ngoại lệ

Giả sử nghiệp vụ ném `IOException`, inner advice bắt rồi trả một đối tượng kết quả thất bại thay vì ném tiếp. Khi đó:

- Inner advice có thể đi qua nhánh lỗi rồi chuyển thành giá trị bình thường.
- Outer advice có thể nhìn thấy **lần gọi trả về bình thường**, chạy xử lý success.
- Một transaction wrapper bên ngoài có thể hiểu đây là thành công nếu nó chỉ rollback khi có ngoại lệ.

Vì vậy, khi gắn advice với giao dịch, **không được chỉ dựa vào tên after-error**. Cần biết một lớp có thể nuốt lỗi, đổi loại lỗi hoặc trả dữ liệu thất bại dưới dạng giá trị thường hay không.

### 10.5 Advice tự ném ngoại lệ trong phần after/finally

Một lỗi phát sinh **sau khi nghiệp vụ thành công** trong inner advice có thể khiến outer advice nhìn thấy lỗi. Ngược lại, một lỗi trong `finally` có thể che mất ngoại lệ gốc. Hệ thống ghi log/metrics và cleanup nên hạn chế các tác dụng phụ gây lỗi, có chính sách rõ việc giữ và báo lỗi thứ cấp, tránh làm sai hợp đồng nghiệp vụ.

## 11. Quan sát log và trace không được suy thành thứ tự nghiệp vụ

Một hệ thống có nhiều luồng, tác vụ bất đồng bộ và bộ ghi log không đồng bộ có thể hiển thị dòng log theo thứ tự khác với **quan hệ happens-before** của một chuỗi xử lý cụ thể. Ngay cả khi wrapper chạy đúng thứ tự, việc lưu/truyền log sang hệ thống ngoài có thể được xếp lịch độc lập.

Do đó:

- Dùng **correlation ID** hoặc trace ID để nhóm các sự kiện thuộc cùng lần chạy.
- Khi cần chứng minh thứ tự advice, ghi sự kiện trực tiếp vào một danh sách tuần tự trong một phép thử đồng bộ, thay vì suy từ log của nhiều thread.
- Phân biệt thứ tự **bắt đầu xử lý**, thứ tự **kết thúc callback**, và thứ tự **dữ liệu hiện ra trên hệ thống log**.
- Không cho rằng after returning của phương thức trả về `Future`/`CompletionStage` có nghĩa công việc bất đồng bộ đã hoàn tất. Advice sau-return có thể chỉ chạy khi phương thức **trả về đối tượng đại diện kết quả**, trước khi tác vụ hoàn tất. Muốn xử lý lúc hoàn tất cần móc đúng tín hiệu hoàn thành theo cơ chế phù hợp.
- Không mặc định context gắn với thread tự truyền qua mọi thread hoặc tác vụ nền.

### 11.1 Đo thời gian đặt ở lớp nào?

~~~text
METRICS(
    SECURITY(
        TRANSACTION(
            BUSINESS
        )
    )
)
~~~

Nếu metrics bao ngoài tất cả, thời gian đo có thể bao gồm kiểm tra quyền và xử lý giao dịch. Nếu metrics đặt ngay quanh business, nó có thể **không tính** phần đó. Lúc yêu cầu bị từ chối trước business, metrics bên trong business không chạy, còn metrics bên ngoài có thể ghi độ trễ từ chối. Phạm vi đo cần được **đặt tên chính xác**, không gọi mọi thời gian đo là “thời gian nghiệp vụ”.

## 12. Hệ quả của pointcut và nhiều join point

Một method call thường dẫn tới một method execution, nhưng không thể luôn coi chúng là **một join point**. Nếu một aspect chọn **call** còn một aspect khác chọn **execution**, cả hai có thể tác động trên cùng thao tác ở hai vị trí khác nhau.

~~~text
Caller
  |
  +--> call advice
          |
          +--> execution advice
                  |
                  +--> method body
~~~

Sơ đồ chỉ là trường hợp khái niệm đơn giản. Sự có mặt và thứ tự thực tế phụ thuộc weaving, dispatch, loại lời gọi và những điểm mà mô hình hỗ trợ.

Các ví dụ dễ gây bất ngờ:

- Một lời gọi đến nhiều phương thức lồng nhau làm sinh thêm nhiều execution join point.
- Một advice ghi log gọi helper; helper lại thỏa pointcut, gây **tự can thiệp/đệ quy advice**.
- Proxy chỉ chặn lời gọi qua proxy; self-invocation trên đối tượng gốc có thể bỏ qua advice proxy.
- Bridge/synthetic methods hoặc mã sinh có thể xuất hiện trong class file, nhưng một bộ AOP có thể bỏ qua chúng hoặc có quy tắc khác. Không mặc định mọi phương thức có trong Reflection đều tạo execution advice.
- Constructor, field access, exception handler và các điểm đặc biệt khác chỉ có khi mô hình AOP hỗ trợ chúng.

Một kết luận đúng về thứ tự tại **cùng một điểm và cùng một tập advice** không tự động áp dụng cho các chuỗi điểm nối khác nhau.

## 13. Xử lý nhiều aspect: thiết kế theo bất biến thay vì tên gọi

### 13.1 Bảo mật và tài nguyên giao dịch

Bất biến cần giữ: *một yêu cầu bị từ chối không được thực hiện tác dụng phụ được bảo vệ*. Từ đó suy ra cần kiểm tra quyền tại điểm đủ sớm và không bị bỏ qua bởi cache, retry hoặc proxy bypass.

Thứ tự mở giao dịch trước/sau kiểm tra quyền tùy mục đích và hợp đồng, nhưng thông thường muốn tránh khởi tạo tài nguyên không cần thiết trước khi biết yêu cầu được phép xử lý. Tuy nhiên, nếu bản thân quá trình kiểm tra quyền yêu cầu một ngữ cảnh giao dịch nhất quán, cần thiết kế ranh giới riêng; không nên áp đặt một thứ tự duy nhất cho mọi hệ thống.

### 13.2 Giao dịch và retry

Nếu retry nằm **ngoài transaction**, mỗi lần thử có thể được thực hiện trong giao dịch riêng, nếu cơ chế và cách cấu hình thực sự tạo giao dịch mới cho mỗi attempt. Nếu retry ở **trong cùng một transaction**, nhiều lần thử có thể chia sẻ trạng thái đã bị lỗi, dẫn đến hành vi khác.

Điều quan trọng là **xác định ranh giới giao dịch và ranh giới lần thử**, không đơn giản chỉ ghi “Retry ưu tiên cao hơn Transaction”. Các lỗi chỉ định rollback, cách đánh dấu transaction không dùng được và tính idempotent của nghiệp vụ phải được xử lý theo hợp đồng thật.

### 13.3 Ghi log và bảo mật dữ liệu

Đặt logging ngoài kiểm tra quyền có thể giúp quan sát từ chối, nhưng cũng có thể vô tình ghi dữ liệu chưa được phép công bố. Đặt logging trong lớp kiểm tra quyền có thể bảo vệ dữ liệu tốt hơn, nhưng mất sự kiện từ chối. Có thể thiết kế **audit từ chối** với dữ liệu tối thiểu ở phạm vi ngoài và **log nghiệp vụ** ở phạm vi sau xác thực, thay vì cố dùng một advice duy nhất cho mọi nhu cầu.

## 14. Kiểm thử thứ tự advice

Không nên chỉ kiểm tra “mỗi advice đều đã chạy”. Phải kiểm tra cả **thứ tự, số lần, nhánh và tác dụng quan sát được**.

| Kịch bản | Điều cần kiểm chứng |
| --- | --- |
| Nghiệp vụ thành công | Các lớp vào và ra theo thứ tự kỳ vọng; kết quả không bị thay đổi trái ý |
| Nghiệp vụ ném lỗi | Nhánh error/finally đúng; ngoại lệ được truyền tiếp hoặc chuyển đổi theo hợp đồng |
| Before chặn | Những lớp bên trong không chạy; cleanup lớp ngoài thực hiện nếu cần |
| Around không gọi tiếp | Không tự giả định nghiệp vụ/advice bên trong đã chạy |
| Retry gọi tiếp nhiều lần | Đếm đúng số attempt và số request logic |
| Inner nuốt lỗi | Outer nhìn đúng tín hiệu success hoặc trạng thái lỗi đã chuyển đổi |
| After/finally ném lỗi | Không vô tình che lỗi ban đầu ngoài chính sách |
| Hai advice đồng ưu tiên | Nếu nền tảng không bảo đảm thứ tự, không đặt bất biến phụ thuộc vào thứ tự đó |
| Nhiều join point | Biết đây là call hay execution và phạm vi weave có bao phủ tất cả đường gọi không |
| Bất đồng bộ | Phân biệt lúc trả về handle và lúc hoàn thành thật |
| Thay thứ tự | Quan sát sự khác biệt đúng mục đích về transaction, security, logging hoặc timing |

Một kỹ thuật hữu ích là kiểm thử bằng **danh sách sự kiện tuần tự**:

~~~text
expected = [
  "SEC.before",
  "TX.before",
  "LOG.before",
  "BUSINESS",
  "LOG.success",
  "LOG.finally",
  "TX.success",
  "TX.finally",
  "SEC.success",
  "SEC.finally"
]
~~~

Đây là dạng dữ liệu giả mã phục vụ assertion, không phải cú pháp của một bộ AOP cụ thể. Danh sách này tương ứng với **mô hình lồng nhau đã chọn** và nhánh thành công; với hệ thống khác phải tạo expected theo hợp đồng thực tế.

Để tránh kiểm thử giòn, nên phân biệt **ràng buộc thứ tự bắt buộc** (ví dụ Security trước tác dụng phụ) với thứ tự không quan trọng (ví dụ hai phép đo độc lập). Nếu thứ tự hai advice đồng mức không được định nghĩa, hãy tách chúng hoặc cấu hình precedence rõ ràng thay vì phụ thuộc vào kết quả tình cờ của một lần chạy.

## 15. Các lỗi hiểu sai phổ biến

1. **“Advice khai báo đầu tiên luôn chạy trước.”** Không có quy tắc chung như vậy; phải đọc hợp đồng ordering/precedence của nền tảng.
2. **“Before nào chạy trước thì after của nó cũng chạy trước.”** Không đúng với lớp bọc lồng nhau, vì phần ra thường đảo chiều.
3. **“After chạy ngay cả khi lỗi.”** Phải phân biệt after-success, after-error và finally.
4. **“Có nhiều advice nên tất cả đều chắc chắn chạy.”** Before có thể ném lỗi, around có thể không gọi tiếp hoặc điểm không được weave.
5. **“Chỉ cần sắp xếp là bảo đảm kiểm soát quyền.”** Nếu pointcut không bao phủ đường vào hoặc advice bị short-circuit từ bên ngoài, thứ tự không đủ bảo vệ.
6. **“Outer advice luôn thấy lỗi nguyên gốc.”** Inner advice có thể chuyển đổi, nuốt hoặc thay ngoại lệ.
7. **“Chạy một lần phương thức tức mỗi advice chạy đúng một lần.”** Retry, multiple join points, bridge/proxy hoặc một cơ chế weave cụ thể có thể tạo các số lần khác nhau.
8. **“Log ra theo thứ tự này chứng minh mọi thread đều chạy vậy.”** Bản thân log và xử lý bất đồng bộ có thể ảnh hưởng thứ tự hiển thị.
9. **“Số thứ tự nhỏ luôn có ưu tiên cao hơn.”** Quy ước số và ý nghĩa priority phụ thuộc mô hình; không được đoán.
10. **“Chỉ có một thứ tự đúng cho Security–Transaction–Logging.”** Thứ tự phải được thiết kế từ các bất biến cụ thể và phạm vi quan sát/chi phí mong muốn.

## 16. Bài tập tự kiểm tra

### Bài 1: Thứ tự bọc

Cho ba lớp `A(B(C(Business)))`, mỗi lớp in `before`, sau đó gọi lớp trong, in `success` khi trả về và `finally` ở mọi nhánh.

Nếu nghiệp vụ thành công, thứ tự nào là đúng?

**Đáp:** `A.before → B.before → C.before → Business → C.success → C.finally → B.success → B.finally → A.success → A.finally`.

### Bài 2: Chặn từ lớp ngoài

Vẫn cấu trúc trên, nhưng `A` phát hiện không đủ quyền rồi ném ngoại lệ **trước khi gọi B**. Có thể khẳng định `B.before`, `C.before` và `Business` chạy không?

**Đáp:** không. Chúng không được gọi trong đường xử lý này. Những xử lý catch/finally của phạm vi *đã được vào* có thể chạy theo cách được tổ chức. Cần thiết kế logging từ chối ở một phạm vi thực sự nhìn thấy lỗi đó.

### Bài 3: Lỗi bị chuyển thành thành công

`C` bắt ngoại lệ từ nghiệp vụ rồi trả giá trị dự phòng. `B` và `A` có bắt buộc chạy nhánh after-error không?

**Đáp:** không. Nếu chúng nhận một giá trị bình thường từ C, chúng có thể chạy nhánh success. Vì vậy không được phụ thuộc đơn độc vào ngoại lệ để rollback khi một lớp bên trong có thể chuyển đổi lỗi.

### Bài 4: Đo latency

Muốn đo toàn bộ thời gian từ khi yêu cầu vào lớp kiểm tra quyền đến khi trả kết quả. Nên đặt lớp đo thời gian ngoài hay trong lớp kiểm tra quyền?

**Đáp:** đặt bao ngoài phạm vi cần đo để phép đo bao gồm phần đó; sau đó xác minh cả nhánh từ chối. Nếu chỉ muốn đo phần nghiệp vụ, đặt trong phạm vi đã qua kiểm tra. Không có “vị trí tốt nhất” khi chưa xác định đại lượng.

### Bài 5: Hai loại điểm nối

Một advice chọn method call, advice khác chọn method execution của cùng phương thức. Có thể lấy cùng quy tắc precedence giữa hai advice ở *một join point* để chắc chắn toàn bộ chương trình có thứ tự giống nhau không?

**Đáp:** không thể suy như vậy. Đó là hai loại điểm nối và có thể xảy ra ở hai vị trí khác nhau. Cần hiểu cơ chế weave và đường gọi thực tế.

## 17. Checklist áp dụng thực tế

Khi một nhóm dự định thêm aspect mới vào hệ thống đang có nhiều advice, nên ghi nhận:

- **Các join point thật sự liên quan**, pointcut nào khớp ở mỗi điểm.
- **Loại advice** (before, after-success, after-error, finally, around) và khả năng tiếp tục/chặn/đổi lỗi.
- **Quan hệ bao ngoài–bên trong** mong muốn; thứ tự lúc vào và lúc thoát.
- **Bất biến bắt buộc:** xác thực trước tác dụng phụ, rollback khi cần, cleanup không rò tài nguyên, không đếm trùng.
- **Cấu hình ordering theo nền tảng:** quy tắc ưu tiên, tie-break, trường hợp mâu thuẫn, khác biệt giữa nhiều aspect và advice trong một aspect.
- **Nhánh biên:** ngoại lệ, retry, cache hit, short-circuit, bất đồng bộ, self-invocation và phương thức không thuộc phạm vi weaving.
- **Bài kiểm thử thứ tự:** một vài trace mong đợi rõ ràng trên các trường hợp thành công, từ chối và lỗi, cộng với kiểm thử tính đúng đắn nghiệp vụ.

**Kết luận:** Trong mô hình nhiều lớp can thiệp, thứ tự advice phải được hiểu như **một quan hệ bao ngoài–bên trong có thể thay đổi luồng điều khiển**, chứ không chỉ là thứ tự khai báo. Với lớp bọc lồng nhau, xử lý trước thường đi **ngoài → trong**, còn xử lý sau/tháo ngăn xếp thường đi **trong → ngoài**. Tuy nhiên, cách xác định thứ tự, nhóm advice và join point phụ thuộc nền tảng AOP; thiết kế đúng phải dựa trên hợp đồng cụ thể, nhánh thành công/lỗi và các bất biến nghiệp vụ được kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-007">Ngoại lệ phát sinh từ advice và ảnh hưởng tới lời gọi gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ phát sinh từ advice và ảnh hưởng tới lời gọi gốc trong AOP

## 1. Hiểu đúng vấn đề

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **aspect** tập trung một mối quan tâm xuyên suốt, chẳng hạn kiểm toán, ghi log, kiểm soát quyền, theo dõi thời gian hoặc giám sát lỗi. Một **pointcut** chọn các **join point** mà công cụ AOP hỗ trợ; **advice** chứa hành vi được thực thi tại các điểm đã chọn thông qua cơ chế weaving hoặc interception.

Advice không nằm ngoài chương trình theo nghĩa “không thể gây lỗi”. **Advice cũng là mã chạy thật**, có thể tạo đối tượng, gọi API, truy cập bộ nhớ, làm I/O và **ném ngoại lệ**. Ngoại lệ từ advice có thể làm thay đổi điều mà mã gọi ban đầu nhận được, kể cả khi thân phương thức nghiệp vụ không hề có lỗi.

Hãy phân biệt ba nguồn thất bại:

1. **Thất bại của mã nghiệp vụ gốc:** phương thức được gọi ném lỗi khi đang xử lý dữ liệu.
2. **Thất bại trong advice:** mã trước/sau/bao quanh join point ném lỗi khi ghi log, kiểm toán, xác thực, dọn dẹp hoặc biến đổi kết quả.
3. **Thất bại của cơ chế can thiệp:** cấu hình weaving, bộ chặn, phản xạ hoặc các thành phần trung gian gây lỗi. Nguồn này cần phân tích riêng, không mặc định là lỗi nghiệp vụ.

Tùy **thời điểm** và **thứ tự** advice, một lỗi có thể:

- Làm phần nghiệp vụ **không được chạy**.
- Phát sinh **sau khi nghiệp vụ đã thực hiện thành công**, khiến caller vẫn nhận ngoại lệ.
- **Che khuất** ngoại lệ nghiệp vụ gốc.
- Được bao thành ngoại lệ mới có **cause** để lưu vết nguồn.
- Bị advice quanh thao tác **bắt rồi chuyển thành kết quả khác**, nếu hợp đồng và cơ chế cho phép.
- Tạo lỗi mới ở tầng dọn dẹp hoặc tầng advice ngoài cùng.

Trọng tâm không chỉ là câu hỏi “advice có thể ném lỗi không?”, mà còn là: **lỗi xuất hiện ở ranh giới nào, phần gốc có chạy hay chưa, caller nhận gì, và làm sao không làm mất thông tin quan trọng?**

## 2. Cần biết các vai trò trước khi lần theo ngoại lệ

| Thành phần | Vai trò | Liên hệ đến lỗi |
| --- | --- | --- |
| **Caller** | Gọi thao tác và quan sát kết quả/ngoại lệ | Hợp đồng caller có thể bị advice thay đổi |
| **Join point** | Điểm có thể can thiệp trong mô hình thực thi | Xác định ranh giới nào được quan sát |
| **Pointcut** | Chọn các join point cần advice | Chọn sai điểm có thể khiến lỗi lan tới nơi ngoài dự kiến |
| **Before advice** | Chạy trước phần được chọn | Có thể ngăn phần gốc chạy nếu ném lỗi |
| **After returning advice** | Chạy sau một phần thực thi hoàn tất bình thường | Lỗi của advice có thể biến một lần thành công thành thất bại bên ngoài |
| **After throwing advice** | Chạy trên đường kết thúc bằng ngoại lệ phù hợp | Có thể log lỗi gốc, nhưng cũng có thể ném lỗi riêng |
| **After/finally advice** | Chạy lúc kết thúc theo ngữ nghĩa công cụ | Lỗi khi dọn dẹp có thể che lỗi trước đó |
| **Around advice** | Bao quanh và kiểm soát việc tiếp tục | Có thể chuyển tiếp, bắt, thay thế hoặc bỏ qua kết quả/lỗi nếu được phép |
| **Weaving/interception** | Kết hợp advice với mã cần can thiệp | Quyết định đường gọi nào thực sự đi qua advice |

**Một nguyên tắc cốt lõi:** ngoại lệ được nhìn thấy ở một ranh giới chưa chắc là ngoại lệ được tạo ra bởi chính phần nghiệp vụ của ranh giới ấy. Advice phía trong có thể đã thay thế nó trước khi chuyển lên tầng ngoài.

## 3. Lỗi ở before advice: phần nghiệp vụ có thể chưa từng bắt đầu

Xét một thao tác `withdraw()` có kiểm tra chính sách trước khi xử lý:

```text
Caller
  |
  v
Before advice
  |
  +-- kiểm tra thành công --> business withdraw() --> kết quả
  |
  +-- ném lỗi -----------> caller nhận lỗi
                            business withdraw() không chạy
```

Ví dụ mã giả AOP, **không phải cú pháp Java độc lập**:

```text
before selectedWithdrawal(context):
    if not authorized(context.actor):
        throw AccessDenied("permission required")
```

Nếu before advice thực sự ở trên đường chuyển tiếp đến phần nghiệp vụ, nó có thể ném lỗi và chặn bước tiếp theo. Hậu quả:

- Thân nghiệp vụ có thể **không chạy**, nên không được suy rằng đã có một giao dịch rút tiền.
- Caller có thể nhận ngoại lệ phát sinh từ chính sách, không phải từ `withdraw()`.
- Một advice khác nằm **bên ngoài** có thể quan sát lỗi đó; advice ở **bên trong** chưa được bắt đầu có thể không chạy.
- Điều kiện retry của caller cần nhận biết đây là lỗi chính sách, không coi mọi lỗi đều đáng thử lại.

Không phải mọi cơ chế AOP đều cho một before advice cùng quyền kiểm soát như around advice. Nhưng trong các mô hình cho phép trước-advice ném ngoại lệ qua ranh giới join point, đó là một cơ chế ngăn tiếp tục tự nhiên.

### 3.1. Khi before advice ném lỗi có phải luôn là sai?

Không. **Kiểm soát quyền hoặc bảo vệ điều kiện tiền đề** có thể chủ động ném ngoại lệ để từ chối thao tác. Đây là hành vi có chủ ý nếu được mô tả trong hợp đồng.

Ngược lại, một before advice chỉ dùng để **ghi log kỹ thuật** nhưng lại ném lỗi vì hệ thống log tạm thời không hoạt động có thể làm dừng nghiệp vụ dù không phải mục tiêu thiết kế. Có nên coi log là bắt buộc hay không phải được quyết định rõ: audit pháp lý bắt buộc khác telemetry tùy chọn.

### 3.2. Kiểm tra “nghiệp vụ chưa chạy” bằng cách nào?

Trong kiểm thử, dùng counter hoặc một target giả có thể ghi lại số lần được gọi. Với case từ chối hợp lệ, thường mong muốn:

```text
beforeEntered = 1
businessEntered = 0
callerReceived = AccessDenied
```

Đây là một kịch bản giả định. Nếu nhiều advice nằm lồng nhau, cần định nghĩa những counter nào được đo để tránh nhầm một advice chưa chạy với tất cả advice đều chưa chạy.

## 4. Lỗi ở after returning advice: thành công bên trong, thất bại bên ngoài

Giả sử phần gốc đã ghi đơn hàng và trả `"ORDER-123"`. Sau đó, after returning advice ném lỗi khi ghi metric:

```text
Caller
  |
  v
BusinessOperation
  |
  +-- lưu đơn thành công
  |
  +-- return "ORDER-123"
  |
  v
After returning advice
  |
  +-- metrics client ném MetricFailure
  |
  v
Caller nhận MetricFailure, không nhận "ORDER-123"
```

**Đây là một trong những tình huống nguy hiểm nhất khi xử lý ngoại lệ từ advice.** Caller có thể nhận lỗi dù phần nghiệp vụ đã có tác dụng phụ và hoàn tất. Nếu caller retry vô điều kiện, một thao tác không idempotent có nguy cơ được thực hiện lần nữa.

Ví dụ:

- Ghi đơn hàng xong rồi lỗi khi ghi metric.
- Gửi email thành công rồi lỗi lúc log sự kiện.
- Hoàn tất chuyển trạng thái rồi lỗi lúc ghi audit ngoài giao dịch.
- Gửi yêu cầu tới hệ thống ngoài được chấp nhận, sau đó advice ném lỗi.

Không được suy ra “caller nhận ngoại lệ” đồng nghĩa “nghiệp vụ chưa thực thi” hoặc “mọi tác dụng phụ đã rollback”. **Ngoại lệ không tự bảo đảm giao dịch được hoàn tác.**

### 4.1. Có phải mọi after returning advice luôn chạy trên mọi kết quả?

Không. Nó chỉ chạy trên **đường hoàn tất bình thường mà nó được thiết kế để quan sát**, với các điều kiện kiểu giá trị trả về nếu công cụ có cơ chế bind/lọc tương ứng. Nếu một advice bên trong đã chuyển kết quả thành lỗi, after returning ở tầng ngoài có thể không chạy; sau đó có thể phát sinh luồng after throwing ở tầng phù hợp.

Trong ví dụ bài này, cụm “after returning” dùng theo nghĩa tổng quát; vị trí chính xác và tương tác với các advice khác phụ thuộc hệ thống AOP.

## 5. Lỗi ở after throwing advice: mất dấu ngoại lệ gốc

Một after throwing advice thường dùng để ghi nhận sự cố:

```text
BusinessOperation -> throw OriginalFailure
                         |
                         v
                    After throwing advice
                         |
                         +-- ghi nhận được --> rethrow OriginalFailure
                         |
                         +-- tự ném AuditFailure
                                           |
                                           v
                                  Caller thấy AuditFailure
                                  (nếu không giữ OriginalFailure)
```

Nếu advice kết thúc bình thường, mô hình after throwing thường để ngoại lệ gốc **tiếp tục lan truyền** theo ngữ nghĩa công cụ. Nhưng nếu chính advice lại ném một lỗi mới, lỗi được quan sát ở ranh giới ngoài có thể khác.

Đoạn mã giả:

```text
afterThrowing operation(error):
    auditSystem.record(error)  // thao tác này có thể ném AuditFailure
```

Khi `auditSystem.record` thất bại, ta có **hai vấn đề**: lỗi nghiệp vụ `OriginalFailure` và lỗi của hệ thống kiểm toán `AuditFailure`. Nếu không thiết kế cách lưu nguyên nhân, caller hoặc hệ thống quan sát chỉ thấy lỗi sau.

### 5.1. After throwing không đồng nghĩa “được quyền nuốt lỗi”

Trong nhiều mô hình AOP, after throwing advice có chức năng **quan sát** nhánh thất bại; nó không tự biến lỗi thành một return thành công. Muốn chủ động bắt rồi chuyển lỗi thành kết quả dự phòng, thường cần một **around advice** hoặc tầng xử lý lỗi có quyền điều khiển quá trình tiếp tục.

Không nên giả định chỉ cần viết `return` bên trong thân của một advice sau lỗi thì phương thức gốc sẽ đổi thành công. Cách xử lý phụ thuộc loại advice và hợp đồng ngôn ngữ công cụ.

### 5.2. Lỗi nào cần ưu tiên?

Nếu đây là telemetry **không bắt buộc**, có thể muốn giữ `OriginalFailure` làm lỗi chính và ghi `AuditFailure` là lỗi phụ trợ. Nếu audit **bắt buộc** đến mức không có nhật ký thì toàn bộ thao tác phải thất bại, kết quả chính sách có thể khác. Cần thiết kế trước, không để một lỗi ngẫu nhiên trong advice tự quyết định thứ tự ưu tiên.

## 6. Lỗi trong after/finally advice: chuyện “mới” có thể che chuyện “cũ”

Một after/finally advice thường nhằm dọn dẹp hoặc ghi nhận khi phần được quan sát kết thúc. Nhưng **finally không có nghĩa là được phép ném ngoại lệ không kiểm soát**.

Ví dụ Java thông thường:

```java
static void example() {
    try {
        throw new IllegalArgumentException("original");
    } finally {
        throw new IllegalStateException("cleanup");
    }
}
```

Đây là **đoạn mã minh họa**, không phải chương trình độc lập. Nếu chạy, caller nhận `IllegalStateException("cleanup")`; ngoại lệ `IllegalArgumentException("original")` bị che khuất trừ khi được lưu lại ở nơi khác.

Điều tương tự có thể xảy ra khi một advice sau kết thúc ném ngoại lệ trong quá trình nhả tài nguyên, gửi log hoặc cập nhật trạng thái.

Bảng tình huống:

| Phần nghiệp vụ | Advice kết thúc | Kết quả quan sát có thể xảy ra |
| --- | --- | --- |
| Return thành công | Không lỗi | Caller nhận kết quả thành công |
| Return thành công | Ném `CleanupFailure` | Caller nhận lỗi từ advice |
| Ném `OriginalFailure` | Không lỗi | Caller nhận lỗi gốc |
| Ném `OriginalFailure` | Ném `CleanupFailure` | Lỗi cuối có thể che lỗi gốc |
| Không bắt đầu vì before lỗi | Advice ngoài có finally | Advice ngoài có thể chạy khi unwinding |

**Lưu ý:** “after/finally advice” trong một công cụ AOP và khối `finally` trong Java có thể khác về **ranh giới** mà chúng bao quanh. Vì thế hàng cuối phụ thuộc vào advice nào đã được kích hoạt và thuộc lớp bao nào. Không có lời hứa chung rằng mọi after advice đều chạy nếu một before advice khác đã ngăn tiến tới điểm cần can thiệp.

### 6.1. Cleanup cũng có thể có tác dụng phụ

Một advice nhả khóa có thể ném lỗi do cơ sở hạ tầng. Nếu cleanup buộc phải hoàn tất để duy trì bất biến hệ thống, không thể chỉ “bỏ qua ngoại lệ để giữ log đẹp”. Cần phân loại lỗi nghiêm trọng và xây hợp đồng phục hồi phù hợp.

Điểm chung vẫn là **bảo toàn thông tin nguyên nhân** và không đưa quyết định fail-open/fail-closed vào một `catch` tùy tiện.

## 7. Around advice có thể thay đổi toàn bộ đường đi của ngoại lệ

Around advice có quyền điều khiển việc chuyển tiếp tới phần được chọn theo ngữ nghĩa công cụ: gọi tiếp (`proceed`), bắt lỗi, thay đổi giá trị trả về hoặc không chuyển tiếp.

Mã giả trung lập công cụ:

```text
around selectedOperation(context):
    beforeWork(context)
    try:
        result = proceed(context)
        afterSuccessWork(context, result)
        return result
    catch error:
        afterFailureWork(context, error)
        throw error
    finally:
        finalWork(context)
```

Đây là **mã giả AOP**, không phải Java độc lập. Trong mã trên, `catch` bao gồm cả `afterSuccessWork`; nếu after-success tự ném lỗi thì `catch` cũng bắt lỗi đó. Việc gọi `afterFailureWork` vì vậy **không tự chứng minh lỗi bắt được phát sinh từ phần nghiệp vụ gốc**.

Đây là một bẫy quan trọng khi lập trình around advice.

### 7.1. Cần phân biệt nguồn lỗi theo ranh giới

Nếu chính sách yêu cầu `afterFailureWork` chỉ chạy khi **phần được tiếp tục** thất bại, mã giả phải chia phạm vi `try/catch`:

```text
around selectedOperation(context):
    beforeWork(context)
    try:
        result = proceed(context)
    catch operationError:
        afterFailureWork(context, operationError)
        throw operationError

    afterSuccessWork(context, result)
    return result
```

Đoạn thứ hai cho phép phân biệt lỗi `proceed` với lỗi từ `afterSuccessWork`. Dĩ nhiên `proceed` có thể bao gồm **advice ở lớp bên trong**, chứ không nhất thiết chỉ phần thân nghiệp vụ gốc. Muốn phân biệt sâu hơn cần quan sát đúng ranh giới.

### 7.2. Nuốt lỗi để trả fallback chỉ khi hợp đồng cho phép

Mã giả:

```text
around queryOperation(context):
    try:
        return proceed(context)
    catch KnownTransientReadError:
        return cachedFallback
```

Đây có thể là chiến lược hợp lệ cho một phép đọc có fallback rõ ràng. Nhưng với thanh toán, cập nhật dữ liệu hoặc cấp quyền, chuyển mọi ngoại lệ thành `success` có thể đánh lừa caller và phá hợp đồng nghiệp vụ.

Các câu hỏi phải được trả lời: lỗi nào có thể phục hồi, kết quả fallback có đúng kiểu/ý nghĩa, tác dụng phụ đã xảy ra chưa và caller có cần biết sự cố không?

## 8. Ví dụ Java 17 chạy được: sáu đường đi của ngoại lệ

Java thuần không có một cú pháp aspect tích hợp. Để quan sát chắc chắn tác động của các lỗi advice, ví dụ dùng **bộ chặn viết tay** với các hàm mang vai trò before/after-returning/after-throwing/finally. Nó minh họa **luồng điều khiển**, không phải hiện thực của một ngôn ngữ AOP cụ thể và không có pointcut/weaving tự động.

Tạo tệp `AdviceExceptionDemo.java`:

```java
public class AdviceExceptionDemo {
    interface Service {
        String process(String mode);
    }

    static final class BusinessService implements Service {
        @Override
        public String process(String mode) {
            System.out.println("business:" + mode);
            if ("business-fail".equals(mode) || "audit-fail".equals(mode)) {
                throw new IllegalArgumentException("business failed");
            }
            return "OK:" + mode;
        }
    }

    static void before(String mode) {
        System.out.println("before:" + mode);
        if ("before-fail".equals(mode)) {
            throw new SecurityException("blocked by advice");
        }
    }

    static void afterReturning(String mode, String result) {
        System.out.println("afterReturning:" + mode);
        if ("after-return-fail".equals(mode)) {
            throw new IllegalStateException("after failed");
        }
    }

    static void afterThrowing(String mode, RuntimeException original) {
        System.out.println("afterThrowing:" + mode
                + ":" + original.getClass().getSimpleName());
        if ("audit-fail".equals(mode)) {
            throw new IllegalStateException("audit failed");
        }
    }

    static void afterFinally(String mode) {
        System.out.println("finally:" + mode);
        if ("cleanup-fail".equals(mode)) {
            throw new IllegalStateException("cleanup failed");
        }
    }

    static String intercepted(Service target, String mode) {
        try {
            before(mode);

            String result;
            try {
                result = target.process(mode);
            } catch (RuntimeException original) {
                afterThrowing(mode, original);
                throw original;
            }

            afterReturning(mode, result);
            return result;
        } finally {
            afterFinally(mode);
        }
    }

    public static void main(String[] args) {
        Service service = new BusinessService();
        String[] modes = {
                "ok", "before-fail", "business-fail",
                "after-return-fail", "audit-fail", "cleanup-fail"
        };

        for (String mode : modes) {
            System.out.println("case:" + mode);
            try {
                String result = intercepted(service, mode);
                System.out.println("caller:result:" + result);
            } catch (RuntimeException error) {
                System.out.println("caller:error:"
                        + error.getClass().getSimpleName()
                        + ":" + error.getMessage());
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AdviceExceptionDemo.java
java AdviceExceptionDemo
```

Đầu ra mong đợi:

```text
case:ok
before:ok
business:ok
afterReturning:ok
finally:ok
caller:result:OK:ok
case:before-fail
before:before-fail
finally:before-fail
caller:error:SecurityException:blocked by advice
case:business-fail
before:business-fail
business:business-fail
afterThrowing:business-fail:IllegalArgumentException
finally:business-fail
caller:error:IllegalArgumentException:business failed
case:after-return-fail
before:after-return-fail
business:after-return-fail
afterReturning:after-return-fail
finally:after-return-fail
caller:error:IllegalStateException:after failed
case:audit-fail
before:audit-fail
business:audit-fail
afterThrowing:audit-fail:IllegalArgumentException
finally:audit-fail
caller:error:IllegalStateException:audit failed
case:cleanup-fail
before:cleanup-fail
business:cleanup-fail
afterReturning:cleanup-fail
finally:cleanup-fail
caller:error:IllegalStateException:cleanup failed
```

### 8.1. Tại sao sáu ca có kết quả khác nhau?

| Ca | Mã nghiệp vụ có chạy? | Nguồn lỗi (nếu có) | Caller nhận |
| --- | --- | --- | --- |
| `ok` | Có | Không | Kết quả `OK:ok` |
| `before-fail` | **Không** | Before advice | `SecurityException` |
| `business-fail` | Có | Nghiệp vụ | `IllegalArgumentException` |
| `after-return-fail` | Có và đã trả kết quả nội bộ | After-returning advice | `IllegalStateException` |
| `audit-fail` | Có và đã ném lỗi gốc | After-throwing advice | `IllegalStateException` thay lỗi gốc |
| `cleanup-fail` | Có và đã trả kết quả nội bộ | Finally advice | `IllegalStateException` |

Chú ý `finally:before-fail`: trong ví dụ, **toàn bộ hàm `intercepted` được bọc bởi một khối `finally` của Java**, kể cả before. Không được suy từ đó rằng mọi after/finally advice của mọi engine AOP đều chạy khi một before advice khác ném lỗi; mỗi loại advice có ranh giới riêng.

Tương tự, `afterThrowing` ở mã này chỉ bắt ngoại lệ từ `target.process`, không bắt ngoại lệ của `afterReturning`. Đây là **thiết kế rõ ranh giới nhằm minh họa nguồn lỗi**; cách phân lớp của engine AOP thực sẽ tùy mô hình và thứ tự precedence.

### 8.2. Vì sao `audit-fail` làm mất lỗi gốc?

Trong `afterThrowing`, ta cố tình ném `new IllegalStateException("audit failed")` mà **không giữ liên hệ** đến `original`. Sau đó `throw original` trong `intercepted` không chạy được vì advice đã ném trước.

Ở hệ thống thật, mất liên hệ tới `IllegalArgumentException("business failed")` khiến điều tra sự cố khó hơn. Không nên xây handling production theo mẫu mất dấu này; nó là **phản ví dụ để kiểm chứng tác hại**.

### 8.3. Điều gì không được suy từ ví dụ?

Không nên khẳng định mọi before/after/around advice dùng đúng thứ tự hoặc cú pháp như các hàm Java trên. Ví dụ chỉ minh họa **khối điều khiển tương đương** và cách Java truyền ngoại lệ qua `try/catch/finally`. Nếu cần hành vi chính xác của một công cụ AOP, phải kiểm tra loại join point, cú pháp advice, ưu tiên, quy tắc checked exceptions và phạm vi weaving của chính công cụ đó.

## 9. Phân biệt “throw”, “rethrow”, “wrap”, “suppress” và “swallow”

Khi advice gặp ngoại lệ, có nhiều cách xử lý. Tên các cách này nên dùng chính xác:

| Cách | Hành vi | Rủi ro/ý nghĩa |
| --- | --- | --- |
| **Throw new** | Tạo lỗi mới từ advice | Caller có thể thấy lỗi advice thay vì lỗi gốc |
| **Rethrow original** | Ném lại cùng đối tượng ngoại lệ gốc | Giữ kiểu và cause gốc, không nhất thiết giữ được mọi ngữ cảnh |
| **Wrap with cause** | Tạo ngoại lệ mới có `cause` là ngoại lệ gốc | Đổi hợp đồng lỗi nhưng vẫn liên kết nguyên nhân |
| **Add suppressed** | Gắn lỗi thứ cấp với lỗi chính | Giữ thứ tự ưu tiên nguyên nhân và thông tin lỗi phụ |
| **Swallow** | Bắt lỗi rồi bỏ qua | Có thể chuyển lỗi thành thành công giả hoặc làm mất dấu sự cố |
| **Return fallback** | Biến lỗi thành kết quả dự phòng | Chỉ đúng nếu hợp đồng và tính an toàn cho phép |

Ví dụ Java độc lập trong phạm vi một phương thức:

```java
try {
    runOriginal();
} catch (RuntimeException original) {
    throw new IllegalStateException("operation failed", original);
}
```

Đây là **đoạn minh họa**, giả sử có phương thức `runOriginal()`. Caller nhận `IllegalStateException`, nhưng `getCause()` trỏ tới lỗi gốc. Việc wrap là chủ động thay đổi kiểu ngoại lệ, nên phải có lý do rõ.

**Một phân biệt kỹ thuật:** `Throwable.addSuppressed(secondary)` **không** thay đổi cause của lỗi chính và **không** tự ném lại lỗi; nó chỉ bổ sung dữ liệu chẩn đoán. Ta vẫn cần ném lại lỗi chính nếu đó là hợp đồng mong muốn.

## 10. Giữ lỗi gốc khi công việc quan sát thất bại

Khi nghiệp vụ đã thất bại mà advice ghi log/audit phụ trợ cũng thất bại, thường có hai mục tiêu:

1. Không để lỗi phụ trợ **che nguyên nhân gốc**.
2. Không làm mất thông tin rằng hệ thống phụ trợ cũng bị lỗi.

Một chiến lược tổng quát:

```text
try:
    performOriginal()
catch original:
    try:
        reportFailure(original)
    catch reportingError:
        attachSecondaryError(original, reportingError)
    throw original
```

Đây là **mã giả**, cần điều chỉnh theo chính sách và công cụ.

Trong Java, có thể minh họa bằng hàm sau (đoạn phụ trợ, không phải chương trình độc lập):

```java
static void callPreservingPrimary() {
    try {
        throw new IllegalArgumentException("original failed");
    } catch (RuntimeException original) {
        try {
            throw new IllegalStateException("audit failed");
        } catch (RuntimeException audit) {
            original.addSuppressed(audit);
        }
        throw original;
    }
}
```

Caller nhận `IllegalArgumentException`; `getSuppressed()` có `IllegalStateException` từ audit. Việc ghi suppressed là quyết định thiết kế: không được coi mọi lỗi phụ trợ đều có thể tiếp tục an toàn, nhất là khi chính sách bảo mật hoặc kiểm toán bắt buộc yêu cầu từ chối hoạt động.

### 10.1. Không dùng một catch quá rộng cho mọi tình huống

Bắt `Throwable` một cách tùy tiện có thể bao gồm các lỗi nghiêm trọng không nên chuyển thành một kết quả bình thường; bắt `Exception` quá rộng có thể nuốt những loại lỗi mà hệ thống chưa có chiến lược phục hồi.

Phải chọn kiểu ngoại lệ tương thích với hợp đồng nghiệp vụ và nền tảng. Đừng dùng `catch` trống chỉ để "giữ cho hệ thống chạy tiếp": nó làm mất dấu sự cố và có thể vi phạm yêu cầu vận hành.

### 10.2. Cẩn thận khi tự thêm suppressed

Không được tự suppress một ngoại lệ vào chính nó; trong Java, `addSuppressed` không cho phép self-suppression. Cần kiểm tra quan hệ các đối tượng lỗi và không lặp gắn cùng một lỗi không cần thiết.

Trong nhiều trường hợp, chuẩn `try-with-resources` đã hỗ trợ suppressed cho lỗi khi đóng tài nguyên: không cần viết lại cơ chế thủ công trừ khi có ràng buộc thực sự khác.

## 11. Ví dụ Java 17 về primary và suppressed exceptions

Ví dụ độc lập dưới đây minh họa cách **Java tự giữ lỗi chính** khi một `AutoCloseable.close()` cũng thất bại. Đây không phải một aspect tự động; cơ chế này hữu ích để hiểu cách quản lý lỗi từ cleanup do advice hoặc nghiệp vụ điều phối.

Tạo `SuppressedExceptionDemo.java`:

```java
public class SuppressedExceptionDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("cleanup:close");
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("business:run");
            throw new IllegalArgumentException("business failed");
        } catch (RuntimeException error) {
            System.out.println("caller:primary:"
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage());

            for (Throwable secondary : error.getSuppressed()) {
                System.out.println("caller:suppressed:"
                        + secondary.getClass().getSimpleName()
                        + ":" + secondary.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac SuppressedExceptionDemo.java
java SuppressedExceptionDemo
```

Kết quả:

```text
business:run
cleanup:close
caller:primary:IllegalArgumentException:business failed
caller:suppressed:IllegalStateException:close failed
```

Khi có lỗi trong thân `try`, cơ chế `try-with-resources` của Java **giữ ngoại lệ chính** và gắn lỗi đóng tài nguyên vào suppressed. Một `finally` thủ công ném lỗi mới không tự động làm điều này.

**Bài học dành cho aspect:** nếu advice thực hiện cleanup có khả năng thất bại, hãy chủ động thiết kế chính sách xác định **lỗi nào là chính**, **lỗi nào là phụ** và **caller cần nhìn thấy gì**. Không nên suy rằng mọi engine AOP tự quản lý suppressed như `try-with-resources`.

## 12. Ngoại lệ checked và unchecked khi advice can thiệp

Java phân biệt **checked exceptions** (thường phải khai báo hoặc xử lý) và **unchecked exceptions** (`RuntimeException` cùng các lớp con; `Error` là nhánh unchecked khác). Điều này ảnh hưởng đến điều gì một advice được phép ném qua join point.

Ví dụ:

```java
interface Lookup {
    String find(String id);
}
```

Nếu chữ ký `find` không khai báo một checked exception như `java.io.IOException`, việc chèn một advice ném checked exception ấy qua cùng ranh giới **có thể không hợp lệ hoặc bị biến đổi/bọc** tùy ngôn ngữ AOP, công cụ weaving, proxy và hợp đồng của phương thức.

Một số ngôn ngữ AOP có quy tắc kiểm tra **checked exceptions của advice** tương thích với chữ ký join point. Một số cơ chế proxy dùng reflection có thể có quy tắc riêng về việc bọc checked exception không khai báo. Vì vậy **không có quy tắc chung** rằng mọi advice đều ném được mọi loại checked exception mà caller sẽ nhìn thấy nguyên dạng.

Các khuyến nghị:

- Không để một advice chỉ phục vụ telemetry bất ngờ đưa thêm checked exception vào API đã công bố.
- Khi cần wrap lỗi thành unchecked, giữ `cause` nếu phù hợp và mô tả thay đổi hợp đồng.
- Khi advice kiểm soát nghiệp vụ có quyền từ chối thao tác, dùng loại lỗi ổn định, được caller hiểu rõ.
- Kiểm thử dưới đúng cơ chế weaving/interception, không chỉ với Java helper mô phỏng.

### 12.1. Vì sao `Error` cần đối xử riêng?

Các lỗi cấp môi trường như thiếu bộ nhớ hoặc lỗi liên kết không phải ngoại lệ nghiệp vụ bình thường. Đừng áp dụng một `catch(Throwable)` để âm thầm biến mọi thứ thành thành công. Cách xử lý phải tôn trọng tình trạng còn hoạt động được của tiến trình và hợp đồng nền tảng.

## 13. Nhiều aspect và thứ tự ưu tiên: ai thấy lỗi của ai?

Khi nhiều advice áp dụng tại cùng join point, nên hình dung các **lớp bao quanh**:

```text
Caller
   |
   v
OuterAudit.around
   |
   v
InnerMetrics.around
   |
   v
BusinessOperation
   |
   v
InnerMetrics kết thúc
   |
   v
OuterAudit kết thúc
   |
   v
Caller
```

Trong một cấu hình có thứ tự này:

```text
OuterAudit(InnerMetrics(BusinessOperation))
```

Nếu `InnerMetrics` ném `MetricFailure` sau khi nghiệp vụ trả kết quả, `OuterAudit` có thể nhìn thấy `MetricFailure` như một lỗi của phần `proceed` mà nó bao quanh, **dù thân nghiệp vụ đã thành công**.

Ngược lại, nếu `OuterAudit` ném lỗi sau khi lớp bên trong trả về, một advice nằm hoàn toàn bên trong không thể tự quan sát sự kiện xảy ra về sau ở lớp ngoài.

Từ đó:

1. Một after throwing advice ở **ranh giới ngoài** có thể nhìn thấy lỗi advice ở lớp trong, tùy thứ tự thực tế.
2. Không nên kết luận “đã thấy after throwing ⇒ nghiệp vụ gốc thất bại”.
3. Thứ tự advice có thể ảnh hưởng lỗi được nhìn thấy và liệu phần gốc có được chạy không.
4. Phải xác minh quy tắc ưu tiên riêng của công cụ; thứ tự khai báo trong file không luôn là thứ tự có hiệu lực.

### 13.1. Trường hợp hai advice cùng ném lỗi

Giả sử business ném `B`, advice trong ném `I`, advice ngoài ném `O`. Nếu mỗi lớp thay ngoại lệ đang được lan truyền bằng ngoại lệ của mình, caller cuối cùng có thể chỉ thấy `O`. Nhưng nếu các lớp giữ `cause` hoặc suppressed, chuỗi lỗi có thể chứa `B` và `I`.

Đừng cố suy diễn một thứ tự cố định cho mọi loại advice; hãy vẽ chuỗi quanh điểm được chọn và kiểm thử từng đường thoát.

## 14. Retry và lỗi advice: nguy cơ lặp side effect

Một around advice có thể thử lại `proceed` khi có lỗi:

```text
around operation:
    try:
        return proceed()
    catch TemporaryFailure:
        return proceed()
```

Đây là mã giả; cơ chế cụ thể phải cho phép gọi lại `proceed`.

Nhưng nếu lỗi đầu tiên **xuất hiện sau khi business đã hoàn tất** — ví dụ từ metrics after-returning của một advice ở bên trong — retry ở lớp ngoài có thể gọi lại **toàn bộ chuỗi**, bao gồm nghiệp vụ đã có tác dụng phụ.

Ví dụ chuyển tiền:

```text
Lần 1: transfer thành công -> advice ghi metric lỗi
Retry quanh chuỗi
Lần 2: transfer được thực hiện lần nữa
```

Đây là mối nguy lớn: **lỗi kỹ thuật của advice không chứng minh thao tác nghiệp vụ an toàn để thử lại**.

Cần có:

- Phân loại lỗi theo **nguồn** và **giai đoạn**.
- Ràng buộc retry chỉ với thao tác được xác định có thể thử lại.
- Idempotency key và cơ chế chống lặp khi có side effect.
- Giới hạn số lần thử và chính sách backoff nếu cần.
- Kiểm thử “business thành công nhưng advice lỗi” chứ không chỉ “business ném lỗi”.

## 15. Giao dịch và tính nguyên tử: lỗi advice không đồng nghĩa rollback

Lỗi xảy ra trong advice có thể đi qua ranh giới giao dịch, nhưng tác động phụ thuộc vào cách ứng dụng quản lý giao dịch:

- Advice lỗi **trước** khi bắt đầu công việc: phần nghiệp vụ chưa chạy.
- Advice lỗi **trong** một giao dịch đang mở: có thể khiến giao dịch bị đánh dấu thất bại nếu cơ chế giao dịch và xử lý lỗi quy định vậy.
- Advice lỗi **sau khi commit** hoặc sau khi hệ thống ngoài đã xác nhận: dữ liệu có thể đã tồn tại, bất chấp caller nhận ngoại lệ.
- Advice ghi audit ở **kho dữ liệu riêng** có thể không tham gia cùng giao dịch với dữ liệu nghiệp vụ.

Không có bảo đảm AOP chung rằng một advice ném `RuntimeException` sẽ tự hoàn tác mọi thay đổi, nhất là đối với I/O mạng, email, hàng đợi hoặc hệ thống phân tán.

Nếu đòi hỏi nhất quán giữa nghiệp vụ và audit, cần thiết kế ranh giới giao dịch, outbox hoặc một cơ chế phối hợp đáng tin thay vì dựa vào việc ném exception.

## 16. Đường gọi đồng bộ và bất đồng bộ có ranh giới lỗi khác nhau

Với hàm đồng bộ, advice có thể ném lỗi trước khi phương thức trả về và caller bắt trực tiếp tại lời gọi.

Nhưng với hàm trả future hoặc handle:

```text
Caller -> startTask() -> Future được trả về
                         |
                         +-- công việc thực chạy về sau
                             -> Future success / failure
```

Các tình huống khác nhau:

1. **Before advice ném lỗi đồng bộ:** `startTask()` có thể không trả future.
2. **After returning advice ném lỗi:** phương thức gốc có thể đã tạo/lên lịch công việc rồi, nhưng caller không nhận được future như mong đợi.
3. **Công việc bất đồng bộ thất bại về sau:** lỗi có thể được lưu trong trạng thái future/completion, không thoát trực tiếp ở method return.
4. **Advice hoặc callback gắn vào completion ném lỗi:** trạng thái của stage phụ thuộc cách callback được nối vào pipeline và hợp đồng thư viện.

Không được mặc định một after throwing advice trên **method execution của phương thức tạo future** sẽ nhìn thấy mọi lỗi xảy ra về sau trong tác vụ. Cần chọn ranh giới hoàn tất phù hợp và xử lý truyền ngữ cảnh qua luồng/tác vụ.

### 16.1. Lỗi ở callback quan sát

Một callback dùng để ghi log lỗi của future có thể tự ném lỗi. Theo API bất đồng bộ cụ thể, nó có thể khiến **một completion stage mới** thất bại hoặc được xử lý theo quy tắc callback. Điều quan trọng là không lẫn lỗi callback với lỗi tác vụ ban đầu.

Khi kiểm thử, giữ riêng định danh `taskId`, lỗi tác vụ và lỗi hệ thống quan sát. Chỉ kết luận kết quả khi đã xác định stage nào caller thực sự chờ/nhận.

## 17. Thiết kế hợp đồng exception của aspect

Một aspect được dùng ở nhiều chỗ nên có chính sách lỗi thành văn thay vì để logic phụ trợ tự xử lý theo thói quen.

### 17.1. Ma trận chính sách gợi ý

| Mối quan tâm | Nếu advice lỗi | Định hướng kiểm thử |
| --- | --- | --- |
| Metrics tùy chọn | Có thể ghi nhận lỗi thứ cấp và giữ lỗi/kết quả nghiệp vụ | Metrics hỏng không đổi hợp đồng |
| Logging chẩn đoán | Hạn chế tác động, không log dữ liệu nhạy cảm | Logger lỗi hoặc đầy đĩa |
| Audit bắt buộc | Theo chính sách fail-closed/fail-open đã xác định | Từ chối đúng ranh giới, không che lỗi |
| Kiểm tra quyền | Chủ động từ chối khi điều kiện không đạt | Target không chạy |
| Cleanup tài nguyên | Không làm mất nguyên nhân gốc nếu đã có | Primary/suppressed/cause |
| Retry | Chỉ retry lỗi được phân loại và thao tác an toàn | Business đã thành công nhưng advice sau lỗi |
| Dịch ngoại lệ | Chuyển đổi có chủ đích, giữ cause | Caller nhận đúng kiểu thông báo |

Bảng không phải một tập quy tắc bắt buộc cho mọi hệ thống. Ví dụ, dữ liệu audit bắt buộc có thể có yêu cầu không được mất bản ghi; bỏ qua lỗi audit lúc đó là vi phạm chính sách.

### 17.2. Hợp đồng cho caller nên nói gì?

Tài liệu API hoặc thiết kế hệ thống nên làm rõ:

- Những lỗi từ điều kiện nghiệp vụ và những lỗi từ cơ sở hạ tầng có thể xảy ra.
- Khi nhận lỗi, tác dụng phụ đã xảy ra hay chưa và cách kiểm tra trạng thái.
- Caller có được retry không, và nếu có, dùng idempotency key nào.
- Lỗi gốc được giữ bằng `cause`, suppressed, error code hay trace ID như thế nào.
- Trường hợp nào advice có thể chủ động từ chối thao tác.

Nếu caller không thể phân biệt “chưa chạy” với “đã chạy nhưng post-processing lỗi”, hệ thống rất dễ có hành vi thử lại nguy hiểm.

## 18. Ghi nhận ngoại lệ an toàn và hữu ích

Một aspect ghi lỗi nên thu thập đủ dữ liệu để truy vết mà không tạo lỗi phụ không cần thiết.

Thông tin thường hữu ích:

- Loại join point và chữ ký của điểm được chọn.
- Một correlation ID phù hợp với yêu cầu/tác vụ.
- Giai đoạn lỗi: before, business/proceed, after returning, after throwing, finally.
- Kiểu ngoại lệ, thông điệp đã loại thông tin nhạy cảm.
- Cause và suppressed nếu phù hợp.
- Tình trạng tác dụng phụ khi hệ thống có thể xác nhận.
- Thời gian và định danh lần gọi.

Không nên `toString()` toàn bộ tham số/đối tượng trong một advice xử lý lỗi: phương thức `toString()` cũng có thể ném ngoại lệ, lộ thông tin nhạy cảm hoặc gây thêm I/O. Ngay cả thao tác ghi log cũng cần mức phòng thủ phù hợp với tầm quan trọng của hệ thống.

### 18.1. Không làm lỗi ghi log đệ quy qua pointcut

Nếu advice xử lý lỗi bằng cách gọi một logger mà chính logger cũng khớp pointcut đang áp dụng, chuỗi sau có thể xảy ra:

```text
business fails
   -> error advice
       -> logger.log()
           -> same pointcut
               -> error advice / logger...
```

Cần xác định phạm vi pointcut và biện pháp tránh tái nhập. Biện pháp không nên là một cờ global bất cẩn, vì nó có thể vô hiệu hóa advice của những invocation khác đang chạy đồng thời.

## 19. Cách kiểm thử tác động exception của advice

Đừng chỉ kiểm thử “advice có chạy” hoặc “caller nhận exception”. Cần kiểm tra **đúng nguồn**, **đúng số lần chạy nghiệp vụ**, **đúng thứ tự** và **đúng thông tin nguyên nhân**.

| Trường hợp | Business chạy? | Điều caller cần nhận theo hợp đồng |
| --- | --- | --- |
| Before bình thường, business thành công | Một lần | Kết quả nghiệp vụ |
| Before ném lỗi chủ động | Không | Lỗi từ chối đúng loại |
| Before logging lỗi ngoài ý muốn | Tùy chính sách | Không mất thông tin và không phá hợp đồng vô cớ |
| Business ném lỗi | Một lần | Lỗi gốc hoặc lỗi có cause gốc |
| After returning ném lỗi | Business đã chạy | Phân biệt lỗi hậu xử lý với thất bại nghiệp vụ |
| After throwing ném lỗi mới | Business đã lỗi | Cause/suppressed được bảo toàn nếu chính sách yêu cầu |
| Finally ném lỗi sau thành công | Business đã chạy | Nhận biết cleanup là nguồn lỗi |
| Finally ném lỗi khi business cũng lỗi | Business đã lỗi | Giữ lỗi chính và lỗi phụ |
| Hai aspect cùng khớp | Tùy chuỗi advice | Thứ tự và đối tượng lỗi rõ ràng |
| Around nuốt lỗi | Business có thể đã lỗi | Chỉ chấp nhận fallback hợp đồng |
| Around retry | Có thể nhiều lần | Không lặp side effect ngoài ý định |
| Tác vụ bất đồng bộ lỗi sau return | Công việc chạy về sau | Đúng completion boundary |

### 19.1. Kiểm thử số lần chạy nghiệp vụ

Dùng counter hoặc target giả để xác nhận:

- Before advice từ chối: **0 lần**.
- After-returning advice ném lỗi: **1 lần** trước khi caller nhận lỗi.
- Advice retry: xác nhận số lần `proceed`/thực thi gốc đúng với chính sách.
- Advice chain bị chặn bên ngoài: advice trong có thể chưa chạy, dù pointcut kỹ thuật của nó phù hợp.

### 19.2. Kiểm thử primary/cause/suppressed

Không chỉ so sánh message. Hãy kiểm tra:

```java
error.getClass()
error.getCause()
error.getSuppressed()
```

Đây là **ba lời gọi minh họa**, không phải đoạn Java độc lập. Với những trường hợp cần bảo toàn lỗi gốc, hãy dùng `assertSame`/so sánh identity hoặc kiểm tra đúng cause chain phù hợp, thay vì chỉ tìm một chuỗi văn bản trong log.

### 19.3. Kiểm thử đường không được can thiệp

Nếu một aspect chỉ nên áp dụng cho `PaymentService.pay()`, cần có ca âm tính để xác minh nó **không** áp dụng cho helper, logger, method nội bộ hoặc thành phần không thuộc chính sách. Chọn quá rộng có thể làm một lỗi advice lan tới hệ thống không liên quan.

### 19.4. Kiểm thử nhiều luồng

Nếu advice lưu đối tượng `currentError` trong biến dùng chung, hai invocation đồng thời có thể ghi đè nhau. Nên bảo đảm lỗi, correlation ID và thời điểm xử lý gắn với **đúng invocation**, không bị chia sẻ ngoài ý định.

## 20. Những hiểu nhầm cần tránh

**“Advice chỉ chạy bổ sung nên không thể ảnh hưởng kết quả.”** Sai. Một exception từ advice có thể là exception cuối cùng mà caller thấy.

**“Business trả thành công thì caller luôn nhận thành công.”** Sai khi có after-returning/finally advice ném lỗi hoặc around advice thay đổi kết quả.

**“Caller nhận exception nghĩa là business đã rollback.”** Không tự động. Tác dụng phụ, giao dịch và I/O bên ngoài có ranh giới riêng.

**“After throwing advice chắc chắn nhìn thấy mọi lỗi trong ứng dụng.”** Không. Nó chỉ áp dụng tại các join point và ranh giới đã chọn, theo loại lỗi/kiểu advice và khả năng công cụ.

**“Ngoại lệ thấy ở after throwing chắc chắn từ business.”** Không. Một advice ở lớp trong có thể là nguồn lỗi.

**“Dùng finally sẽ tự giữ ngoại lệ gốc.”** Không với `finally` thường nếu cleanup ném lỗi; `try-with-resources` có cơ chế suppressed riêng.

**“Bắt mọi lỗi trong around rồi return null là an toàn.”** Không. Có thể phá hợp đồng kiểu trả về, che sự cố và đánh lừa caller.

**“Retry khi có lỗi nào cũng đúng.”** Không. Lỗi từ advice hậu xử lý có thể xảy ra sau khi nghiệp vụ đã thành công.

**“Checked exception từ advice luôn đi qua caller như unchecked exception.”** Không thể giả định. Java và công cụ AOP có các giới hạn/hợp đồng riêng.

**“Một ca thành công đủ chứng minh xử lý lỗi đúng.”** Không. Cần kiểm thử đường before fail, business fail, post-processing fail, cleanup fail và kết hợp nhiều aspect.

## 21. Bài tập luyện phân tích luồng ngoại lệ

### Bài 1 — Before ném lỗi

`before` kiểm tra quyền ném `AccessDenied`; business chưa được gọi. Caller bắt được gì và bộ đếm thực thi business là bao nhiêu?

**Gợi ý đáp án:** caller nhận lỗi từ before (hoặc lỗi đã được lớp advice ngoài chuyển đổi theo hợp đồng); business được gọi **0 lần** nếu lỗi chặn trước khi chuyển tiếp.

### Bài 2 — After returning ném lỗi

`placeOrder` đã ghi đơn thành công, nhưng after returning ném `MetricsFailure`. Caller nên làm gì trước khi retry?

**Gợi ý đáp án:** xác minh trạng thái đơn và yêu cầu idempotency. Không nên giả định chưa có đơn chỉ vì caller nhận lỗi. Thiết kế error model nên phân biệt lỗi hậu xử lý.

### Bài 3 — Hai ngoại lệ liên tiếp

Business ném `IllegalArgumentException("B")`; after throwing advice ném `IllegalStateException("A")` mà không có cause. Caller thấy gì?

**Gợi ý đáp án:** ở chuỗi giả định lỗi advice được đẩy ra ranh giới ngoài, caller thấy `IllegalStateException("A")`; lỗi B có thể bị che khuất. Nếu dùng `new IllegalStateException("A", original)`, caller có thể truy vết B qua `getCause()`.

### Bài 4 — Finally che lỗi

Một `finally` ném `CleanupError` trong lúc đang lan truyền `OriginalError`. Nếu không dùng cơ chế bảo toàn, lỗi nào đến caller?

**Gợi ý đáp án:** trong Java `finally` thông thường, lỗi cleanup mới có thể thay thế lỗi đang lan truyền. Cần thiết kế primary/suppressed/cause nếu phải giữ cả hai.

### Bài 5 — Around catch quá rộng

Một around advice viết `try { proceed(); logSuccess(); } catch (Exception e) { logBusinessFailure(e); }`. Nếu `logSuccess` ném lỗi thì có gì sai?

**Gợi ý đáp án:** catch cũng bắt lỗi `logSuccess`, khiến hệ thống phân loại sai lỗi advice thành lỗi business. Cần thu hẹp phạm vi catch hoặc gắn rõ nguồn lỗi.

### Bài 6 — Retry nguy hiểm

Một outer advice retry toàn bộ `proceed` khi gặp lỗi. Business hoàn tất, inner advice ném lỗi khi xuất metric. Điều gì có thể xảy ra?

**Gợi ý đáp án:** outer retry có thể thực thi business thêm một lần, tạo side effect trùng. Cần phân loại lỗi và cơ chế idempotency.

### Bài 7 — Lỗi bất đồng bộ

Một phương thức `startTask()` trả `Future` bình thường; task hoàn tất lỗi sau đó. After throwing advice của method execution có chắc nhìn thấy lỗi task không?

**Gợi ý đáp án:** không. Nó quan sát ranh giới method return/throw; lỗi của tác vụ có thể nằm trong Future và xuất hiện ở completion sau đó.

## 22. Checklist thiết kế advice an toàn trước ngoại lệ

- [ ] Đã ghi rõ advice có thể ném những loại lỗi nào và theo chính sách gì.
- [ ] Đã phân biệt lỗi từ before, business/proceed, after returning, after throwing và cleanup.
- [ ] Đã biết thời điểm business có thể đã tạo side effect.
- [ ] Đã xác định lỗi nào là primary khi nhiều tầng cùng thất bại.
- [ ] Có chính sách giữ cause/suppressed thay vì vô tình che lỗi gốc.
- [ ] Không catch rộng hoặc nuốt lỗi mà không có hợp đồng phục hồi.
- [ ] Không retry thao tác có side effect chỉ vì lỗi hậu xử lý.
- [ ] Đã kiểm tra giới hạn checked exception theo công cụ AOP và chữ ký Java.
- [ ] Đã xác định thứ tự và ranh giới khi nhiều aspect cùng khớp.
- [ ] Đã kiểm thử cả đường thành công, before fail, business fail, after fail và finally fail.
- [ ] Đã kiểm tra các đường gọi ngoài phạm vi weaving hoặc qua proxy nếu có.
- [ ] Đã kiểm tra bối cảnh đồng thời, reentrancy và bất đồng bộ nếu liên quan.
- [ ] Telemetry không vô tình làm lộ dữ liệu nhạy cảm hoặc phá nghiệp vụ.
- [ ] Caller có hướng xử lý khi nhận lỗi nhưng side effect có thể đã xảy ra.

## 23. Kết luận

**Advice có thể thay đổi kết quả quan sát của một lời gọi bằng cách ném ngoại lệ, kể cả khi mã nghiệp vụ gốc không ném lỗi.** Vị trí phát sinh quyết định tác động: trước thao tác có thể ngăn phần gốc chạy; sau khi trả về có thể đổi thành công thành thất bại; trên nhánh lỗi hoặc lúc dọn dẹp có thể che ngoại lệ gốc.

Muốn dùng AOP an toàn cần **xác định đúng ranh giới**, **giữ nguyên nhân gốc khi phù hợp**, **phân loại lỗi theo nguồn**, **kiểm soát retry và side effect**, và **kiểm thử toàn bộ chuỗi advice**, không chỉ kiểm thử riêng từng phương thức. Đó là khác biệt giữa một aspect chỉ tiện lợi trên sơ đồ và một aspect đáng tin cậy khi chạy trong hệ thống thực.

### Tài liệu tham khảo để đối chiếu ngữ nghĩa của một công cụ AOP

- [AspectJ Programming Guide — Advice](https://eclipse.dev/aspectj/doc/released/progguide/semantics-advice.html)
- [AspectJ Programming Guide — The AspectJ Language](https://eclipse.dev/aspectj/doc/latest/progguide/language.html)

Các liên kết trên giúp tra cứu định nghĩa và giới hạn của một công cụ cụ thể. Những sơ đồ và ví dụ Java thuần trong bài dùng để giải thích **nguyên lý AOP tổng quát**, không ngụ ý mọi engine AOP có cùng API hay thứ tự chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-008">Giới hạn thay đổi dữ liệu theo loại advice và công cụ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thay đổi dữ liệu theo loại advice và công cụ trong AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, *advice* là hành vi bổ sung được áp dụng tại những **join point** mà một *pointcut* lựa chọn. Người học thường biết các loại advice như “trước”, “sau khi thành công”, “sau khi lỗi”, “sau khi kết thúc” và “bao quanh”. Nhưng một câu hỏi quan trọng hơn là:

**Advice có thể đọc, chỉnh sửa hoặc thay thế những dữ liệu nào của lời gọi phương thức, và việc đó có thật sự thay đổi hành vi mà người gọi quan sát không?**

Câu trả lời phụ thuộc vào **loại advice**, **thời điểm nó chạy**, **khả năng truy cập ngữ cảnh**, **hợp đồng của công cụ AOP** và **cách weaving/interception được triển khai**. Không thể mặc định mọi before/after advice đều được thay tham số, trả kết quả mới hoặc nuốt ngoại lệ; cũng không thể khẳng định mọi hệ thống AOP đều cung cấp một around advice có toàn quyền can thiệp như nhau.

> **Phạm vi:** Bài này trình bày AOP cơ bản và nâng cao nói chung. Các tên before, after-returning, after-throwing, after/finally và around là khái niệm; cú pháp khai báo, quy tắc binding và quyền thay đổi dữ liệu cụ thể khác nhau giữa các công cụ.

## 1. Định vị advice trong mô hình AOP

Một **join point** là điểm trong quá trình chạy nơi cơ chế AOP có thể chèn hành vi, chẳng hạn một lần gọi hay thực thi phương thức. **Pointcut** lựa chọn các điểm đó theo loại, tên, chữ ký, ngữ cảnh hoặc các điều kiện được hỗ trợ. **Advice** là phần xử lý thực thi tại những điểm được chọn. **Aspect** thường đóng gói pointcut và advice phục vụ một mối quan tâm cắt ngang, chẳng hạn logging, tracing, audit, quan sát hiệu năng hoặc chính sách truy cập.

Với lời gọi đồng bộ thông thường, luồng khái niệm:

```text
Caller
  |
  |  before advice
  v
Target.method(arguments)
  |
  +---- trả về kết quả ------> after-returning advice
  |
  +---- ném ngoại lệ --------> after-throwing advice
  |
  +---- kết thúc theo cả hai nhánh ---> after/finally advice
  |
Caller nhận kết quả hoặc ngoại lệ
```

**Around advice** có thể bao phủ cả vùng này, tùy cơ chế:

```text
around: trước proceed
   |
   +---- gọi tiếp target/chuỗi advice (proceed)
   |           |
   |           +---- return hoặc throw
   |
around: có thể quan sát, thay đổi kết quả/ngoại lệ, hoặc quyết định không proceed
   |
Caller
```

Sơ đồ nhằm mô tả quyền kiểm soát khái niệm, **không phải lời hứa** rằng mọi AOP engine có cùng các kiểu advice, thứ tự hoặc ngôn ngữ pointcut.

### Mấu chốt: quan sát khác với thay thế

Giả sử hàm đích nhận `String name` và trả về `String greeting`. Một advice có thể:

1. **Quan sát** đối số `name`, không làm thay đổi nó.
2. **Sửa một object mutable** được tham chiếu qua đối số, làm thay đổi trạng thái mà target có thể thấy.
3. **Thay đối số thực sự truyền tới target** (ví dụ chuyển `" a "` thành `"a"`) nếu advice và API chuyển tiếp hỗ trợ.
4. **Quan sát** giá trị trả về.
5. **Thay giá trị mà caller nhận** nếu vị trí advice có quyền trả kết quả thay thế.
6. **Quan sát, truyền tiếp, chuyển đổi hoặc nuốt ngoại lệ**, nếu hợp đồng advice cho phép.

Các hành vi trên **không tương đương**. Đặc biệt, thay một biến local trong advice không tự động sửa biến local của caller hay vùng tham số đã được truyền theo giá trị của Java.

## 2. Bảng so sánh quyền can thiệp theo loại advice

| Loại advice | Khi nào chạy? | Thường làm tốt | Điều **không nên mặc định** |
| --- | --- | --- | --- |
| **Before** | Trước khi vào điểm xử lý đích | Validate, log đầu vào, khởi tạo ngữ cảnh | Trả một giá trị thay thế kết quả method; thay hẳn các argument của lời gọi theo mặc định |
| **After-returning** | Khi thao tác kết thúc **bình thường** | Log kết quả, metric thành công, đọc return value | Thay return value cho caller chỉ bằng cách gán lại tham số advice |
| **After-throwing** | Khi thao tác kết thúc bằng **ngoại lệ** | Audit lỗi, tracing, cleanup tùy contract | Tự biến ngoại lệ thành kết quả thành công hoặc nuốt lỗi trong mọi DSL |
| **After/finally** | Sau thao tác trên cả nhánh bình thường và bất thường, theo semantics engine | Cleanup, đóng scope, metric kết thúc | Luôn nhận đầy đủ return/throw context hoặc chủ động thay outcome |
| **Around** | Bao quanh bước chuyển tiếp được hỗ trợ | Điều khiển `proceed`, biến đổi arguments/return, recovery, retry | Có thể can thiệp mọi kiểu join point, mọi method, hoặc sửa tùy ý bất chấp type contract |

Từ **“thường”** quan trọng: bảng mô tả mô hình phổ biến, không xác nhận một năng lực phổ quát. Có hệ thống chỉ hỗ trợ interceptor kiểu before/after; có hệ thống cho phép around chèn vào bytecode; có hệ thống cho phép binding dữ liệu nhưng không cho đổi outcome; có hệ thống đưa ra API `proceed` với những ràng buộc riêng.

### Quy tắc thiết kế nhanh

**Nếu cần quan sát:** ưu tiên advice ít quyền nhất, ví dụ before hoặc after thích hợp. **Nếu cần thay đổi cách target được gọi hay kết quả caller thấy:** xem xét around hoặc cơ chế tương đương đã được công cụ cam kết hỗ trợ. **Nếu thao tác là bất biến nghiệp vụ hoặc bảo mật bắt buộc:** bảo vệ thêm tại ranh giới đáng tin cậy; không phụ thuộc độc nhất vào một đường can thiệp có thể bị bypass.

## 3. Before advice: được làm gì trước khi phương thức chạy?

Một **before advice** chạy trước phần công việc được chọn. Đây là vị trí thích hợp để:

- Ghi log tên thao tác, mã tương quan, tham số đã được ẩn dữ liệu nhạy cảm.
- Tạo mốc đo thời gian hoặc thiết lập scope quan sát.
- Kiểm tra các điều kiện tiền đề được phép kiểm tra tại boundary.
- Từ chối thao tác bằng ngoại lệ nếu đó là hợp đồng được cho phép.
- Khởi tạo tài nguyên hoặc ngữ cảnh cần được cleanup tương ứng.

Ví dụ, **mã giả**:

```text
before save(document):
    log("begin", document.id)
    ensureValid(document)
```

Đây không phải cú pháp Java hay pointcut chuẩn. Nếu `ensureValid` ném lỗi trước khi target bắt đầu, target có thể không chạy. Tuy nhiên, quyền **chặn** này không biến before advice thành một cơ chế tùy ý **trả về kết quả mới**.

### 3.1. Vì sao before thường không thay được return value?

Tại thời điểm before chạy, target chưa trả kết quả nào. Một hàm before có tham số `String input` hoặc `Method method` không tự có hợp đồng “giá trị tôi `return` là kết quả thay thế của target”. Nhiều API before có kiểu trả về `void` hoặc bỏ qua return value của advice.

Nếu muốn bỏ qua target và trả một giá trị thay thế cho caller, cần **điểm kiểm soát luồng** như around hoặc interception hook có return contract cụ thể. Một before advice ném ngoại lệ có thể ngăn target chạy, nhưng đó là **kết thúc bất thường**, không phải “trả kết quả thành công tùy ý”.

### 3.2. Sửa object đối số: có thể, nhưng đó là hành vi khác

Giả sử target nhận một `MutableRequest`. Before advice nhận tham chiếu tới cùng object rồi gọi `request.setStatus(...)`. Nếu target sau đó đọc object ấy, nó **có thể thấy trạng thái mới**. Đây là **mutation trên object chia sẻ**, không chứng minh before advice có quyền **thay instance đối số** từ A sang B.

Trong Java, tham số luôn được **truyền theo giá trị**: đối với object, giá trị được sao chép là **reference**. Việc advice gán `request = new MutableRequest(...)` cho biến local của chính advice không khiến caller hay target tự động nhận reference mới.

Thậm chí khi mutation có hiệu lực, hành vi ấy có thể nguy hiểm: thay object trước validation, gây race giữa thread, phá invariant hoặc khiến log khác với dữ liệu gốc. Tốt hơn nên giữ before advice mang tính quan sát, trừ khi mutation là contract rõ ràng và được kiểm thử.

## 4. After-returning: quan sát kết quả không đồng nghĩa thay kết quả

Một **after-returning advice** chạy sau khi phương thức đích **kết thúc thành công**, trước khi quyền điều khiển tiếp tục theo cơ chế AOP.

Ví dụ:

```text
result = target.fetch(id)
afterReturning(result):
    metric("fetch.success")
    logMetadata(result)
```

Điểm này thích hợp để ghi metric thành công, log thông tin kết quả không nhạy cảm hoặc cập nhật quan sát độc lập. Tuy nhiên, nếu advice chỉ nhận bản sao của tham chiếu kết quả rồi gán:

```java
// Mã minh họa tại bên trong advice, không phải chương trình độc lập.
String value = "original";
value = "replacement";
```

thì thao tác gán vào `value` **không thay** một kết quả mà caller sẽ nhận ở bên ngoài. Việc thay return value đòi hỏi API advice có **hợp đồng thay thế giá trị trả về**; không suy luận từ việc có biến `returnValue` trong tham số advice.

### 4.1. Mutable return value là một ngoại lệ dễ gây nhầm lẫn

Nếu target trả về một `List` mutable và after-returning advice được cấp **cùng tham chiếu** rồi gọi `list.clear()`, caller nhận cùng object nhưng thấy dữ liệu đã bị thay. Đó là **mutation trạng thái**, không phải thay reference của kết quả trả về.

Tách biệt ba khả năng:

- **Read-only:** chỉ quan sát list.
- **In-place mutation:** sửa các phần tử của chính list; caller có thể quan sát thay đổi.
- **Replacement:** trả một list object hoàn toàn khác cho caller; cần contract hỗ trợ đổi outcome.

Một công cụ AOP có thể cho phép bước thứ nhất, có thể không cấm bước thứ hai về mặt Java nếu đối tượng mutable, nhưng không nhất thiết hỗ trợ bước thứ ba từ after-returning.

### 4.2. Hành vi khi target ném lỗi

Nếu target kết thúc bằng ngoại lệ, **after-returning** theo đúng nghĩa “returning bình thường” **không phải nơi quan sát lần thất bại đó**. Hãy dùng after-throwing hoặc cleanup dạng finally nếu phù hợp. Không gộp hai loại sự kiện vì chúng thể hiện hai outcome khác nhau.

## 5. After-throwing: biết lỗi, nhưng được đổi lỗi đến đâu?

**After-throwing advice** gắn với trường hợp thao tác kết thúc bất thường. Nó phù hợp để:

- Ghi loại lỗi, mã lỗi và ngữ cảnh an toàn.
- Đếm metric thất bại hoặc gắn trạng thái vào trace.
- Audit trường hợp từ chối/không thành công.
- Thực hiện dọn dẹp theo contract nếu không có ranh giới finally khác.

Ví dụ khái niệm:

```text
try:
    target.process()
catch error:
    afterThrowing(error)
    throw error
```

Mục tiêu thông thường là **truyền tiếp lỗi gốc**. Một số hệ thống cho phép advice ném một ngoại lệ mới, làm thay outcome; nhưng nhiều API after-throwing **không** cho phép “trả một giá trị bình thường” thay cho ngoại lệ. Phải xem hợp đồng của công cụ.

### 5.1. Không thể đồng nhất thay exception với recovery

Có ba hành vi riêng:

1. **Observe + rethrow:** log rồi giữ nguyên loại/lỗi.
2. **Map exception:** chuyển `IllegalArgumentException` thành loại lỗi khác nếu cơ chế cho phép; thay đổi contract ngoại lệ caller thấy.
3. **Recover:** ngăn lỗi lan ra và trả kết quả thành công/fallback; thường cần cơ chế bao quanh có thể điều khiển luồng.

Một after-throwing advice có thể tự ném lỗi mới, nhưng điều đó chưa chứng minh nó có thể **khôi phục và trả giá trị bình thường** như around. Cần phân biệt “thay throwable” với “chuyển outcome từ throw thành return”.

### 5.2. Exception từ chính advice

Advice cũng là code và có thể thất bại. Nếu logger hoặc audit sink ném lỗi, ngoại lệ mới có thể che mất ngoại lệ nghiệp vụ tùy cách engine xử lý. Thiết kế phải xác định rõ *logging thất bại có được làm lời gọi thất bại hay không?*, *nên giữ lỗi nào làm nguyên nhân chính?*, và *có dùng suppressed exceptions hay báo lỗi phụ không?*.

Không viết advice bắt `Throwable` rồi nuốt mọi thứ chỉ để “hệ thống không lỗi”. Điều đó che giấu cả lỗi nghiêm trọng. Khi cần giữ nguyên exception, phải truyền tiếp đúng đối tượng lỗi theo contract.

## 6. After/finally: cleanup cho cả hai nhánh

Một **after/finally advice** theo mô hình thông thường chạy khi rời join point ở cả nhánh **return** và **throw**. Nó rất có giá trị để:

- Đóng scope tracing/timing.
- Khôi phục ngữ cảnh trước đó.
- Giải phóng tài nguyên được advice sở hữu.
- Ghi metric thời gian hoàn tất, bất kể outcome.

Dạng logic:

```java
// Mã minh họa cách Java try/finally hoạt động.
long start = System.nanoTime();
try {
    work();
} finally {
    long elapsed = System.nanoTime() - start;
    recordDuration(elapsed);
}
```

Đoạn này không phải tệp Java chạy độc lập. Nó minh họa **finally chạy cả khi work trả về và khi work ném lỗi**. Tuy nhiên, không phải every “after” của mọi DSL đều có semantic finally; tên API có thể phân biệt after-returning, after-throwing, after-finally theo cách riêng.

### 6.1. Vì sao không nên đổi outcome trong finally?

Một khối `finally` thông thường dùng để cleanup, không để trả kết quả khác hoặc che lỗi. Trong Java, một `return` hoặc một exception mới từ `finally` có thể ghi đè luồng kết thúc trước đó. Trong AOP, nếu cleanup advice ném lỗi, outcome của caller có thể thay đổi tương tự theo cơ chế đang dùng.

Do vậy:

- Không chủ ý `return` kết quả nghiệp vụ từ cleanup.
- Đảm bảo cleanup an toàn và có chính sách xử lý lỗi phụ.
- Với context scope, ghi nhớ giá trị cũ và **restore trong finally** ngay cả khi target ném lỗi.
- Kiểm thử không chỉ nhánh thành công mà cả thất bại và trường hợp cleanup ném lỗi.

**Một after/finally advice không mặc nhiên có quyền truy cập hoặc thay đổi return value**, dù nó biết một lượt gọi đã kết thúc.

## 7. Around advice: điểm kiểm soát luồng mạnh nhất khi được hỗ trợ

**Around advice** có thể chạy **trước và sau** thao tác được chọn. Với một API `proceed` hay cơ chế tương đương, around quyết định khi nào/bao nhiêu lần gọi tiếp, cách đưa đối số vào bước tiếp theo, và có xử lý kết quả/ngoại lệ sau đó hay không.

```text
around(arguments):
    prepared = normalize(arguments)
    try:
        result = proceed(prepared)
        return transform(result)
    catch knownError:
        return fallback
```

Đây là **pseudocode**, không phải cú pháp universal. Một số engine hỗ trợ `proceed(newArguments)`; số khác chỉ cung cấp `proceed()`, hoặc hạn chế số/lọai đối số có thể thay. Có engine giới hạn around cho method execution/proxy invocation; có engine hỗ trợ nhiều loại join point hơn.

### 7.1. Những khả năng thường gặp

Nếu engine hỗ trợ đầy đủ, around có thể:

- **Quan sát** hoặc **sửa đối số** trước khi gọi tiếp.
- **Không gọi tiếp**, qua đó bỏ qua target và trả giá trị mặc định.
- **Gọi tiếp đúng một lần** rồi biến đổi kết quả trước khi trả cho caller.
- **Gọi tiếp nhiều lần**, ví dụ retry có điều kiện.
- **Bắt ngoại lệ** và giữ nguyên, chuyển đổi hoặc phục hồi.
- **Đo thời gian** của toàn bộ chuỗi advice/target mà nó bao ngoài.

Mỗi khả năng đều phải tuân theo **type contract** và **semantic contract**. Nếu method trả `int`, một around advice trả `String` có thể gây lỗi kiểu khi runtime unbox/cast. Nếu method trả `void`, return value tùy ý thường không có ý nghĩa cho caller.

### 7.2. “Có thể” không có nghĩa “nên”

Around quá mạnh có thể che mất hành vi nghiệp vụ: bỏ qua một thao tác cần chạy, thực thi thanh toán hai lần, nuốt lỗi giao dịch hoặc sửa kết quả không còn đúng contract. Chỉ dùng mức can thiệp này khi concern thực sự cần thay luồng và có test bảo vệ.

**Một before + một after** thường dễ đọc hơn một around phức tạp nếu nhu cầu chỉ là log trước và sau. Không chọn around chỉ vì muốn viết một advice bao trùm mọi logic.

## 8. Ví dụ Java 17+ chạy được: before/after chỉ quan sát, around đổi argument và outcome

Ví dụ sau sử dụng hai **dynamic proxy** nối nhau để mô phỏng hai nhóm trách nhiệm:

1. **Proxy quan sát** in “before”, “after-returning”, “after-throwing”, “after-finally” rồi **không đổi** argument, return value hay exception.
2. **Proxy ngoài** mô phỏng **around advice**, trim đối số trước khi gọi tiếp, bọc kết quả bình thường và phục hồi một loại ngoại lệ được nhận diện.

Ví dụ chạy được với **Java 17+**, không cần thư viện AOP. Nó minh họa contract interception của proxy và **không phải DSL pointcut đầy đủ**. Đừng suy luận mọi AOP framework có đúng tập quyền như hai proxy này.

Lưu toàn bộ mã vào `AdviceDataLimitsDemo.java` bằng UTF-8:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

public class AdviceDataLimitsDemo {
    public interface Operation {
        String process(String value);
    }

    public static final class OperationImpl implements Operation {
        @Override
        public String process(String value) {
            System.out.println("target " + value);
            if ("fail".equals(value)) {
                throw new IllegalArgumentException("blocked");
            }
            return value.toUpperCase(Locale.ROOT);
        }
    }

    // Gọi target qua reflection nhưng chuyển tiếp lỗi gốc.
    static Object invoke(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    // Mô phỏng before, after-returning, after-throwing, after/finally.
    static Operation withObservation(Operation target) {
        return (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] { Operation.class },
                (proxy, method, args) -> {
                    System.out.println("before " + args[0]);
                    try {
                        Object result = invoke(method, target, args);
                        System.out.println("after-return " + result);
                        return result; // Giữ nguyên kết quả.
                    } catch (Throwable error) {
                        System.out.println(
                                "after-throw "
                                + error.getClass().getSimpleName()
                                + ":" + error.getMessage());
                        throw error; // Giữ nguyên ngoại lệ.
                    } finally {
                        System.out.println("after-finally");
                    }
                });
    }

    // Mô phỏng around: đổi argument, đổi kết quả, có thể recover.
    static Operation withAround(Operation next) {
        return (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] { Operation.class },
                (proxy, method, args) -> {
                    String prepared = ((String) args[0]).trim();
                    System.out.println("around-enter " + prepared);
                    try {
                        String result = (String) invoke(
                                method, next, new Object[] { prepared });
                        System.out.println("around-map " + result);
                        return "<<" + result + ">>";
                    } catch (IllegalArgumentException error) {
                        System.out.println(
                                "around-recover " + error.getMessage());
                        return "RECOVERED";
                    } finally {
                        System.out.println("around-exit");
                    }
                });
    }

    public static void main(String[] args) {
        Operation target = new OperationImpl();
        Operation observed = withObservation(target);
        Operation service = withAround(observed);

        System.out.println("caller " + service.process(" ok "));
        System.out.println("caller " + service.process(" fail "));

        // Bỏ qua cả hai proxy:
        System.out.println("caller-direct " + target.process("plain"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 AdviceDataLimitsDemo.java
java AdviceDataLimitsDemo
```

**Đầu ra chuẩn:**

```text
around-enter ok
before ok
target ok
after-return OK
after-finally
around-map OK
around-exit
caller <<OK>>
around-enter fail
before fail
target fail
after-throw IllegalArgumentException:blocked
after-finally
around-recover blocked
around-exit
caller RECOVERED
target plain
caller-direct PLAIN
```

### 8.1. Đường thành công

`service.process(" ok ")` đi qua **proxy ngoài**, nơi chuỗi được trim thành `"ok"`. Đây là **thay đối số truyền tới bước tiếp theo** bằng cách tạo `new Object[]{prepared}`, không phải “chỉnh biến local trong before”.

Proxy quan sát nhận `"ok"` và in `before ok`. Target nhận đúng `"ok"`, in `target ok` rồi trả `"OK"`. Proxy quan sát in `after-return OK`, trả **nguyên** `"OK"`, và in `after-finally`.

Proxy ngoài nhận `"OK"` và tạo kết quả mới `"<<OK>>"`. Caller vì vậy nhận `"<<OK>>"`, không nhận trực tiếp `"OK"`. **Đây là replacement của return outcome** tại ranh giới around.

### 8.2. Đường thất bại

`service.process(" fail ")` được trim thành `"fail"`. Target in `target fail` rồi ném `IllegalArgumentException("blocked")`. Hàm `invoke` mở lớp bọc `InvocationTargetException` để giữ lỗi gốc.

Proxy quan sát in `after-throw IllegalArgumentException:blocked`, chuyển tiếp cùng lỗi, rồi luôn in `after-finally`. Nó **không trả một giá trị thành công**.

Proxy ngoài bắt `IllegalArgumentException` và quyết định **không truyền lỗi tới caller**, thay bằng chuỗi `"RECOVERED"`. Đây là recovery từ lỗi sang return outcome. Dù vậy, target vẫn đã chạy và thất bại; “caller thấy thành công” **không biến thao tác đã thất bại thành thành công về mặt nghiệp vụ**.

### 8.3. Đường gọi trực tiếp

`target.process("plain")` không đi qua proxy nào nên chỉ có dòng `target plain`. Dù các điều kiện method có thể tương ứng với logic interceptor, **đường gọi thực tế không được weave/intercept** nên advice không chạy. Khả năng thay dữ liệu chỉ tồn tại khi execution path đi qua cơ chế AOP.

### 8.4. Những hạn chế cố ý của demo

- Không có pointcut DSL hay hệ thống weaving tổng quát; handler áp dụng với interface proxy.
- Không minh họa advice cho private/static method, field access, constructor hay self-invocation.
- Việc cast `(String) args[0]` trong around giả định đầu vào không null; code production cần contract hoặc validation rõ ràng.
- Recovery trả `"RECOVERED"` chỉ để minh họa, **không phải một chính sách an toàn mặc định**.
- Không hỗ trợ transaction, retry hay propagation ngữ cảnh tự động.
- Không khẳng định một engine thực tế cho phép khai báo before/after độc lập với chính các quyền của proxy demonstration.

## 9. Thay đổi đối số: thay reference khác với sửa trạng thái

Đây là nguồn nhầm lẫn lớn nhất khi người học thấy advice nhận được mảng argument.

### 9.1. Java truyền theo giá trị

Đối với primitive, Java sao chép giá trị số/boolean. Đối với object, Java sao chép **giá trị của reference**. Vì vậy:

```java
// Hàm minh họa, có thể đặt trong một class.
static void changeLocalReference(String name) {
    name = "new";
}

static void mutateList(java.util.List<String> items) {
    items.add("extra");
}
```

Sau `changeLocalReference(x)`, biến `x` bên caller vẫn giữ reference cũ. Nhưng sau `mutateList(list)`, list caller giữ có thể chứa phần tử mới vì **cùng một object bị thay đổi**.

Nếu advice giữ tham chiếu tới object được truyền vào target, nó có thể làm mutation qua API của object, dù bản thân advice không có quyền **thay argument slot** tại điểm chuyển tiếp. Nếu một API around cho phép cung cấp `newArguments` vào `proceed`, đó lại là quyền khác: **thay giá trị truyền tới bước tiếp theo**.

### 9.2. Đừng suy từ việc sửa Object[] thành sửa lời gọi

Một interceptor có thể nhận `Object[] args`. Việc làm `args[0] = replacement` có thể ảnh hưởng bước chuyển tiếp trong **một implementation cụ thể** nếu nó dùng chính mảng đó để invoke target. Nhưng không thể mặc định mọi API advice chia sẻ cùng mảng, không sao chép hay cho phép thay input. Một số công cụ truyền bản sao hoặc chỉ hỗ trợ đọc. Hãy dùng API chính thức của công cụ để thay argument nếu có.

### 9.3. Ràng buộc kiểu, số lượng và overload

Một method khai báo `calculate(int)` không thể nhận một `String` tùy ý chỉ vì around muốn vậy. Nếu method nhận hai tham số, mảng argument chuyển tiếp phải phù hợp về **số lượng, thứ tự và kiểu**. Với overload, method đã được chọn trước khi around can thiệp có thể không tự đổi sang overload khác sau khi argument bị biến đổi.

Ví dụ Java về overload:

```java
// Hai phương thức thuộc một class minh họa:
void save(String name) { }
void save(Object value) { }
```

Lời gọi `save((Object) "hi")` chọn overload `save(Object)` theo kiểu biểu thức compile-time. Nếu around thay value runtime bằng một `String`, điều đó **không tự bắt JVM chọn lại** overload `save(String)`. Nó vẫn cần chuyển tiếp qua method descriptor được chọn, trừ khi cơ chế AOP cố ý thực hiện một lời gọi khác.

## 10. Thay đổi kết quả: cần giữ hợp đồng phương thức

Khi around có quyền trả kết quả thay thế, giá trị thay thế phải tương thích với kiểu trả về và semantics của API.

| Return contract của target | Điều phải cân nhắc khi thay |
| --- | --- |
| `String` | Có thể trả chuỗi khác nếu contract cho phép; null có hợp lệ không? |
| `int` | Runtime có thể yêu cầu wrapper số phù hợp rồi unbox; trả null hoặc sai loại sẽ lỗi |
| `boolean` | Kết quả thay đổi có thể đảo quyết định nghiệp vụ |
| `void` | Không có kết quả hữu ích để caller tiêu thụ; behavior phụ thuộc API interception |
| `Optional<T>` | Phải giữ invariant của Optional và kiểu giá trị bên trong |
| `CompletableFuture<T>` | Thay chính object future khác với thay kết quả bên trong future sau khi hoàn tất |
| Một object mutable | Có thể đổi reference hoặc mutate trạng thái; hai hành vi có hệ quả khác |

**Tính hợp lệ về kiểu chỉ là mức tối thiểu**. Trả một `String` đúng kiểu vẫn có thể sai nghiệp vụ, ví dụ trả “paid” trong khi giao dịch chưa được thực hiện.

### 10.1. Covariant return và generic type

Một method override có thể khai báo kiểu trả về hẹp hơn kiểu ở lớp cha theo quy tắc Java. Reflection hoặc interceptor làm việc với một method metadata cụ thể phải nắm đúng return contract, không chỉ dựa vào tên method.

Với generics, type erasure có thể khiến runtime chỉ thấy một raw type. Việc cast một `List<String>` có thể thành công ở mức raw `List` nhưng không chứng minh từng phần tử là `String`. Around advice thay kết quả bằng một list sai loại phần tử có thể gây lỗi **muộn hơn ở caller**. Những contract generic cần được kiểm thử ở mức hành vi, không chỉ kiểm tra `instanceof List`.

### 10.2. Null và giá trị mặc định

Nếu một method non-null theo contract, around trả `null` có thể gây lỗi tại caller dù chữ ký Java cho phép reference null. Với primitive return, proxy có thể gặp lỗi khi unbox null. Nếu muốn fallback, phải chọn giá trị phù hợp semantics thay vì trả tùy ý chỉ để không ném exception.

## 11. Thay đổi ngoại lệ và tính minh bạch của advice

Ở mức quyền kiểm soát luồng, outcome của một lời gọi có thể mô hình hóa là:

```text
Outcome<T> =
    Return(T value)
    OR
    Throw(Throwable error)
```

Đây là **mô hình khái niệm**, không phải khai báo kiểu Java chuẩn. Một advice quan sát lý tưởng giữ nguyên outcome. Một around advice có thể thay outcome nếu được công cụ cho phép:

```text
Return(x)  -> Return(transform(x))
Throw(e)   -> Throw(map(e))
Throw(e)   -> Return(fallback)
Return(x)  -> Throw(policyError)
```

Bốn chuyển đổi này là bốn quyết định khác nhau. Mỗi chuyển đổi cần contract, test và lý do rõ ràng.

### 11.1. Đừng nuốt lỗi vô điều kiện

Một advice `catch (Exception) { return defaultValue; }` có thể khiến caller tin thao tác đã thành công, dù mục tiêu đã ném lỗi do dữ liệu không hợp lệ, lưu trữ thất bại hoặc một trạng thái bất biến bị phá. Đối với tác vụ có side effect, fallback có thể làm trạng thái hệ thống không nhất quán.

Nếu thực sự cần phục hồi, hãy phân loại lỗi **có thể phục hồi**, giữ lỗi gốc trong telemetry, và phân biệt rõ trạng thái như “đã dùng cache cũ” với “thực thi mới thành công”.

### 11.2. Checked exceptions và API proxy

Trong Java, interceptor dùng reflection thường gặp `InvocationTargetException` bọc lỗi target; cần mở lớp bọc nếu muốn giữ Throwable gốc. Với proxy dựa trên interface, một checked exception không được khai báo thích hợp trong contract interface có thể bị runtime bọc dưới dạng một ngoại lệ proxy cụ thể. Vì vậy không thể tự coi “ném bất kỳ Throwable” là luôn giữ nguyên loại ngoại lệ mà caller thấy ở mọi công cụ.

### 11.3. Phục hồi có ảnh hưởng tới after advice nào?

Một target ném lỗi có thể khiến after-throwing bên trong chạy. Nếu một around bên ngoài **recover** rồi trả giá trị, một advice được gắn **bên ngoài around** có thể quan sát outcome **thành công**. Không có mâu thuẫn: chúng quan sát **các ranh giới khác nhau** trong chuỗi advice. Thứ tự và mức lồng quyết định outcome mà từng advice thấy.

## 12. Thứ tự advice có thể đổi dữ liệu được quan sát

Giả sử ba aspect cùng khớp một method:

- **ValidationAspect:** kiểm tra đầu vào.
- **TransformAspect:** chuẩn hóa đầu vào.
- **AuditAspect:** ghi lại thao tác.

Nếu Validation nằm **ngoài** Transform, nó kiểm tra đầu vào trước khi chuẩn hóa. Nếu Transform nằm ngoài Validation, nó có thể biến đổi argument rồi mới đi vào Validation. Hai thứ tự tạo chính sách khác nhau.

```text
Thứ tự A:
  Validate(raw)
    -> Transform(raw -> normalized)
      -> Target(normalized)

Thứ tự B:
  Transform(raw -> normalized)
    -> Validate(normalized)
      -> Target(normalized)
```

Cả hai sơ đồ chỉ diễn tả nesting khái niệm; khả năng thực tế phụ thuộc API around và precedence của công cụ.

**Pointcut** quyết định *ở đâu* advice áp dụng. **Precedence/ordering** quyết định *lớp nào bên ngoài/lớp nào bên trong* khi nhiều advice cùng khớp. Không nên nhầm việc thay thứ tự với “đổi pointcut”. Thứ tự không được định nghĩa rõ có thể làm log nhận dữ liệu khác kết quả nghiệp vụ, hoặc khiến lỗi bị quan sát như thành công.

### 12.1. Exception và finally khi nhiều lớp lồng nhau

Xét quanh một target:

```text
OuterAround.before
  InnerObservation.before
    Target
  InnerObservation.after-return-or-throw
  InnerObservation.finally
OuterAround.after-or-recover
OuterAround.finally
```

Nếu target ném lỗi, inner có thể ghi “failed”, outer recover rồi trả fallback. Nếu audit cần ghi *kết quả caller thấy*, audit phải được đặt tại ranh giới phản ánh outcome cuối cùng, không nhất thiết sát target. Nếu audit cần ghi *target thực sự đã thất bại*, nó phải quan sát ranh giới bên trong thích hợp.

## 13. Retry: gọi tiếp nhiều lần là thay đổi rất lớn

Một around advice có thể muốn retry bằng cách gọi `proceed` nhiều lần, **nếu công cụ cho phép**. Nhưng điều đó làm phương thức đích có khả năng thực thi **nhiều lần cho một lời gọi của caller**.

```text
Caller gọi transfer() một lần
  AroundRetry:
    proceed #1 -> target ghi dữ liệu rồi ném timeout
    proceed #2 -> target ghi lại cùng dữ liệu
```

Nếu thao tác không idempotent, giá trị đã được ghi ở lần #1 có thể chưa được caller xác nhận, và lần #2 tạo side effect trùng lặp. Retry phải có:

- Danh sách lỗi thực sự có thể retry.
- Giới hạn số lần và tổng thời gian.
- Idempotency key hoặc contract chống lặp phù hợp.
- Timeout/deadline/cancellation nhất quán.
- Chính sách backoff và xử lý kết quả cuối.
- Test đối với “thành công một phần trước khi ném lỗi”.

**Một around advice không tự làm retry an toàn.** Khi concern chỉ là quan sát, tuyệt đối không gọi target nhiều lần chỉ để “thử lại” sau lỗi.

## 14. Quyền thay dữ liệu còn phụ thuộc loại join point

Ví dụ có hai loại join point:

- **Method call:** điểm phát lời gọi ở caller.
- **Method execution:** lúc target bắt đầu thực thi.

Một cơ chế weaving vào **call site** có thể có quyền can thiệp tại phía caller. Một cơ chế weaving vào **execution entry** có thể chỉ nhìn thấy metadata/dữ liệu sau bước lựa chọn method và đánh giá arguments. Thay dữ liệu ở hai vị trí có thể mang ý nghĩa khác nhau.

Ví dụ, đối số là biểu thức có side effect:

```java
// Minh họa:
service.process(buildInput());
```

Nếu `buildInput()` đã chạy và tạo dữ liệu trước khi advice execution bắt đầu, việc advice thay reference đối số **không “hoàn tác” tác dụng phụ của buildInput()**. Tương tự, một advice quanh method execution không nhất thiết can thiệp được vào lỗi xảy ra khi caller đang đánh giá argument trước khi vào target.

**Không mặc định AOP có quyền đảo ngược thời gian thực thi**: advice có thể chọn dữ liệu mới cho bước tiếp theo, nhưng không tự hoàn tác hiệu ứng đã diễn ra ở caller.

## 15. Giới hạn của công cụ: proxy, interception và weaving

Một lời khuyên mạnh về mặt thiết kế vẫn vô dụng nếu đường gọi thực tế không đi qua nơi công cụ áp dụng advice.

| Cơ chế | Vị trí can thiệp thường thấy | Giới hạn cần xem |
| --- | --- | --- |
| **Proxy dựa trên interface** | Lời gọi đi qua object proxy | Không tự bắt trực tiếp target, private/static method hoặc self-invocation trong target |
| **Proxy dựa trên subclass** | Override/intercept những method có thể can thiệp trong subclass | Final/private/static và việc gọi không qua proxy có thể tạo giới hạn |
| **Compile-time weaving** | Bytecode/mã được ghép advice khi biên dịch | Phải build đúng phần mã; khả năng join point tùy engine |
| **Post-compile weaving** | Biến đổi bytecode đã biên dịch | Phải dùng artifact đã weave và tương thích bytecode |
| **Load-time weaving** | Kết hợp khi class được nạp | Class loader, cấu hình, thứ tự nạp và phạm vi instrument ảnh hưởng coverage |
| **Interceptor tại pipeline** | Điểm hook do một nền tảng cung cấp | Có thể chỉ cho phép một số kiểu dữ liệu/hành vi và không phải join point tổng quát |

Một API “before callback” không trở thành around advice chỉ vì được đặt trong class tên Aspect. Ngược lại, một interceptor có khả năng nhận handler bao quanh lời gọi có thể mô phỏng một số hành vi around mà không dùng cú pháp AOP chuyên biệt.

### 15.1. Self-invocation

```java
class OrderService {
    void submit() {
        validate();  // gọi nội bộ qua this
    }

    void validate() { }
}
```

Ở kiểu proxy đứng ngoài target, lời gọi `this.validate()` trong target có thể bỏ qua proxy. Dù pointcut chọn `validate()`, advice không nhất thiết chạy nếu không có đường interception phù hợp.

Với weaving trực tiếp ở mức bytecode, các phương thức nội bộ có thể được nhận diện ở những join point khác, tùy công cụ. Do đó “self-invocation luôn bị bỏ qua” là kết luận quá rộng; đúng hơn phải nói **proxy-based interception có giới hạn đường đi**, còn bytecode weaving có các khả năng khác.

### 15.2. Static, private, final, constructor và native

Không phải mọi cơ chế đều có quyền can thiệp vào những vị trí này. Một số có thể chọn method execution và chèn advice vào bytecode, nhưng cũng có giới hạn theo language/runtime. Constructor, initialization, field access hoặc exception handler có thể là các loại join point khác nếu công cụ hỗ trợ. **Không dùng cùng một bảng quyền thay argument/return cho mọi loại điểm can thiệp** mà không kiểm tra API thực tế.

## 16. Binding đối số và return value: phân biệt dữ liệu đọc với dữ liệu ghi

Một công cụ AOP có thể cấp các biến cho advice, ví dụ đối số, target object, method metadata, return value hoặc Throwable. Câu hỏi cần hỏi là:

1. Dữ liệu này là **bản sao, tham chiếu đến object hay một view bất biến**?
2. Advice có được **đọc** thôi hay được sửa?
3. Nếu sửa, thay đổi đó tác động vào **object** hay **slot argument/return**?
4. Nếu muốn thay slot, phải gọi API đặc biệt như `proceed(arguments)` hay chỉ gán biến?
5. Advice có được quyền **bỏ qua lời gọi tiếp** không?
6. Nếu thất bại, Throwable nào đến caller?
7. Khi nhiều advice cùng áp dụng, advice nào thấy dữ liệu trước và sau biến đổi?

Một biến tên `result` không tự trở thành “handle cho return value của caller”. Việc API cho bind kết quả có thể chỉ nhằm **quan sát**. Tương tự, thấy mảng `arguments` không chứng minh có quyền ghi lại đúng các slot sẽ được target sử dụng.

### 16.1. Một ví dụ sai về cách hiểu reference

```java
// Minh họa cục bộ; không phải một advice hoàn chỉnh.
static void observer(String input) {
    input = input.trim();  // Chỉ đổi biến local input.
}
```

Nếu caller đã có `String raw = " x "` rồi gọi `observer(raw)`, biến `raw` vẫn tham chiếu `" x "`. Muốn target nhận `"x"`, cơ chế điều khiển bước gọi tiếp phải thực sự dùng reference mới tại ranh giới invoke/proceed.

## 17. Async return: after-returning của method không nhất thiết thấy kết quả cuối

Một method có thể trả `CompletableFuture<String>` ngay sau khi lên lịch tác vụ. **After-returning của method** có thể thấy **object future**, không thấy chuỗi `String` sẽ được tính ra sau.

```java
// Minh họa kiểu return, chưa phải chương trình hoàn chỉnh.
java.util.concurrent.CompletableFuture<String> result =
        java.util.concurrent.CompletableFuture.supplyAsync(
                () -> "finished");
```

Những điều khác nhau:

- **Return của method:** một `CompletableFuture<String>`.
- **Completion của future:** thành công với `"finished"`, thất bại với exception hoặc cancellation.
- **After-returning advice:** quan sát future khi method trả về, theo semantics join point.
- **Completion callback:** có thể quan sát outcome cuối cùng khi future hoàn tất, nhưng là cơ chế/điểm xử lý khác.

Nếu around advice thay **object future** bằng future khác, nó có thể thay giao diện kết quả mà caller nhận. Nếu nó muốn biến đổi giá trị bên trong future, có thể cần compose stage mới với `thenApply`/`handle`/`exceptionally` theo hợp đồng API. Việc đó khác với việc gán lại một biến `result` trong after-returning.

Không được mặc định callback chạy trong cùng thread với advice; phải cân nhắc context propagation, lỗi, cancellation và deadline.

## 18. Trạng thái, concurrency và dữ liệu mutable

Ngay cả một advice chỉ dự định “quan sát” cũng có thể gây side effect nếu nó dùng dữ liệu mutable.

### 18.1. Mutate tham số được chia sẻ

Nếu advice gọi setter trên một request object được dùng đồng thời, target có thể thấy giá trị trung gian hoặc race. Nếu cần chuẩn hóa, nên cân nhắc tạo **bản sao bất biến** rồi chuyển nó cho bước tiếp theo qua API cho phép đổi arguments; điều này giảm việc thay trực tiếp object chung, nhưng vẫn phải tuân thủ contract và lifetime của dữ liệu.

### 18.2. Mutate giá trị trả về

Thay đổi collection do target trả về có thể gây lỗi khi caller đang duyệt trên thread khác hoặc khi target giữ cùng collection. Muốn thêm metadata quan sát, không nên chèn field nhạy cảm vào object trả về chỉ để logging dễ hơn.

### 18.3. State của aspect

Nếu một instance aspect/interceptor được chia sẻ qua nhiều thread, trường mutable như `currentArgument` hoặc `lastResult` có thể bị ghi đè chéo. Biến local theo invocation thường an toàn hơn. Với context gắn theo thread, phải truyền và restore đúng khi qua executor/async, không mặc định `ThreadLocal` đi theo tác vụ.

### 18.4. Time-of-check/time-of-use

Một before advice kiểm tra `request.amount`, nhưng giá trị bị thread khác sửa trước khi target sử dụng, có thể dẫn đến **TOCTOU**. AOP không tự tạo tính nguyên tử. Quy tắc nghiệp vụ quan trọng cần thực thi dưới cơ chế dữ liệu/giao dịch/snapshot đáng tin cậy.

## 19. Bảo mật và bất biến nghiệp vụ: advice không phải phép màu

Một **validation aspect** có thể hữu ích để áp dụng quy tắc nhập liệu chung. Nhưng nếu đường gọi trực tiếp target không đi qua advice hoặc nếu có task nội bộ bỏ qua proxy, một **security invariant** chỉ đặt ở aspect có thể bị bỏ lọt.

Cần phân biệt:

- **Chính sách phụ trợ:** log, tracing, metric, audit quan sát.
- **Chính sách bảo vệ bắt buộc:** quyền truy cập, invariant trạng thái, phân quyền dữ liệu, xác thực giao dịch.
- **Chính sách điều khiển luồng:** retry, fallback, caching, circuit breaking.

Các chính sách điều khiển luồng có thể có trong AOP, nhưng phải đáp ứng semantic contract. Quy tắc bảo vệ bắt buộc cần được bảo vệ ở ranh giới mà mọi đường thực thi liên quan đều đi qua, không chỉ ở một proxy dễ bypass.

### Không ghi dữ liệu nhạy cảm khi “quan sát”

Một before advice có quyền đọc arguments không có nghĩa nên ghi toàn bộ object vào log. Dữ liệu có thể chứa mật khẩu, token, địa chỉ, thông tin sức khỏe hoặc nội dung bí mật. Nên chọn các trường an toàn, ẩn danh/ẩn dữ liệu nhạy cảm và giới hạn nhãn metric có cardinality cao.

## 20. Quy trình quyết định loại advice

Khi có yêu cầu mới, hỏi lần lượt:

1. **Chỉ cần chạy logic trước target?** Nếu có, xem before advice. Có cần chặn bằng lỗi không?
2. **Chỉ cần quan sát kết quả thành công?** Xem after-returning. Có cần biết thất bại nữa không?
3. **Chỉ cần quan sát thất bại?** Xem after-throwing. Có muốn truyền nguyên lỗi không?
4. **Cần dọn dẹp bất kể outcome?** Xem after/finally hoặc scope có `try/finally`.
5. **Cần thay đối số thực sự truyền tới target, bỏ qua target, đổi return hoặc recover lỗi?** Xem around hay interception API **được xác nhận có quyền điều khiển đó**.
6. **Có nhiều aspect cùng áp dụng?** Xác định precedence trước khi chọn vị trí quan sát/biến đổi.
7. **Có async hoặc mutable object?** Phân biệt return của method và completion của công việc; phân tích ownership/concurrency.
8. **Có đường bypass cơ chế AOP?** Kiểm tra direct call, self-invocation, reflection, thread/task và proxy coverage.
9. **Việc đổi outcome có an toàn nghiệp vụ không?** Xác minh contract, idempotency, type, error và logging.
10. **Công cụ AOP thực tế hỗ trợ gì?** Đọc đặc tả tool về loại join point, binding, `proceed`, precedence, checked exception và loại return.

### Ma trận lựa chọn nhanh

| Mục tiêu | Lựa chọn thường hợp lý |
| --- | --- |
| Ghi nhận bắt đầu | Before |
| Đếm thành công | After-returning |
| Ghi nhận lỗi gốc | After-throwing |
| Đóng trace scope ở mọi nhánh | After/finally |
| Chuẩn hóa argument **được chuyển tiếp** | Around hoặc hook có argument replacement |
| Bọc result trả về | Around hoặc hook có return replacement |
| Chuyển exception thành fallback | Around hoặc mechanism hỗ trợ recovery |
| Retry | Around có hợp đồng `proceed` và idempotency phù hợp |
| Kiểm tra logic quyền bắt buộc | Boundary đáng tin cậy; AOP chỉ là một lớp bổ sung nếu không đảm bảo coverage |
| Đo completion của future | Completion callback/stage observation, không chỉ after-returning của method |

## 21. Kiểm thử hành vi: không chỉ kiểm tra advice đã chạy

Muốn biết một loại advice thực sự có quyền thay dữ liệu không, phải kiểm thử từ **góc nhìn caller** và **góc nhìn target**.

| Ca kiểm thử | Câu hỏi cần trả lời |
| --- | --- |
| Before đọc argument | Target có nhận nguyên argument không? |
| Before gán lại biến local | Caller/target có bị thay reference không? |
| Before mutate object mutable | Target và caller có thấy mutation không, và có được phép không? |
| After-returning quan sát | Giá trị caller nhận có giữ nguyên không? |
| After-returning cố trả giá trị khác | Công cụ bỏ qua, hỗ trợ hay báo lỗi? |
| After-throwing ghi lỗi | Caller nhận Throwable gốc hay bị thay? |
| After/finally cleanup | Chạy đủ trên success và throw không? |
| Around đổi arguments | Target nhận giá trị mới hay vẫn giá trị cũ? |
| Around đổi result | Caller có nhận đúng kiểu/giá trị mới không? |
| Around recover | Target đã thất bại nhưng caller có nhận fallback đúng contract không? |
| Around không proceed | Target có thực sự không chạy không, side effect nào còn? |
| Around proceed hai lần | Target chạy mấy lần, có side effect trùng không? |
| Nhiều aspect | Thứ tự vào/ra và dữ liệu quan sát có đúng precedence không? |
| Gọi target trực tiếp | Có bypass interceptor/proxy không? |
| Async target | Advice thấy future hay kết quả completion? |
| Exception từ advice | Lỗi chính/ phụ nào tới caller? |
| Chạy đồng thời | Advice có lẫn argument/result giữa requests không? |

Một regression test nên so sánh **input mà target thực sự nhận**, **output/exception caller thực sự thấy** và **số lần target chạy**. Chỉ xác nhận log “advice activated” không chứng minh dữ liệu được thay.

### Phương pháp kiểm thử từng lớp

- **Unit test:** logic advice riêng, phân biệt observer và transformer.
- **Integration test:** đường proxy/weaving thật, thứ tự advice và đường bypass.
- **Contract test:** giá trị trả về, checked/unchecked exception, idempotency, null, generic type và async completion.
- **Concurrency test:** mutable arguments, ThreadLocal/context và cleanup khi exception/cancellation.

Các công cụ AOP khác nhau có thể cho kết quả khác nhau đối với cùng một pseudocode, nên test **trên cơ chế đang triển khai**.

## 22. Những hiểu lầm phổ biến

1. **“Before advice có thể return để bỏ qua target.”** Không mặc định; thường cần around hoặc hook được định nghĩa để thay kết quả.
2. **“Gán biến trong advice sẽ sửa argument của caller.”** Java truyền theo giá trị; gán reference local không thay biến bên ngoài.
3. **“After-returning đổi giá trị bằng cách gán lại result.”** Chỉ đúng nếu tool cấp API thay return, không phải vì có biến result.
4. **“After-throwing luôn phục hồi được lỗi.”** Quan sát/mapping/recovery là các quyền khác nhau.
5. **“After advice luôn giống finally.”** Cần phân biệt after-returning, after-throwing và after/finally theo contract.
6. **“Around luôn chạy target một lần.”** Nó có thể gọi không lần, một lần hoặc nhiều lần nếu API hỗ trợ.
7. **“Đổi argument ở runtime sẽ chọn lại overload Java.”** Overload thường được lựa chọn theo kiểu tĩnh trước đó.
8. **“Return type đúng là đủ an toàn.”** Còn invariant nghiệp vụ, nullability, idempotency và generic contract.
9. **“Aspect quan sát không thể đổi hành vi.”** Logging có thể ném lỗi, latency tăng, hoặc mutate object nếu code viết sai.
10. **“Có pointcut là mọi lời gọi đều được can thiệp.”** Phạm vi proxy/weaving thực tế có thể bỏ sót direct call hoặc self-invocation.
11. **“Around retry không có hại nếu target báo timeout.”** Target có thể đã gây side effect trước khi timeout.
12. **“Advice nào nhìn thấy exception cũng nhìn thấy cùng outcome.”** Around bên ngoài có thể recover lỗi mà advice bên trong vừa quan sát.
13. **“Future trả về là công việc đã xong.”** Method return và asynchronous completion là hai thời điểm khác nhau.
14. **“Mọi công cụ AOP có cùng binding/proceed.”** Cần đối chiếu khả năng cụ thể với mỗi loại advice và join point.

## 23. Bài tập tự luyện

1. Chạy `AdviceDataLimitsDemo.java` và giải thích tại sao `before` thấy `"ok"` chứ không phải `" ok "`.
2. Bỏ `trim()` ở around. Xác định đầu ra và kết quả `process(" fail ")` thay đổi như thế nào; vì sao chuỗi có khoảng trắng không còn bằng `"fail"`?
3. Đổi result wrapper từ `"<<...>>"` thành `"[...]"`. Chỉ ra dòng nào chứng minh **caller** nhận kết quả đã thay chứ không phải target tự trả kết quả đó.
4. Chuyển `before` sang một hàm Java chỉ gán biến local `String value = "changed"`, nhưng không đổi mảng argument dùng để invoke. Target có nhận `"changed"` không? Giải thích.
5. Cho target trả `List<String>` mutable. So sánh việc advice thêm phần tử vào cùng list với việc around trả một list khác.
6. Cho around cố trả `null` từ method có kiểu trả `int` trong một cơ chế proxy. Cần kiểm tra loại lỗi nào và ở ranh giới nào?
7. Viết test xác minh lỗi `IllegalArgumentException("blocked")` được before/after observer truyền tiếp nguyên vẹn khi không có around recovery.
8. Viết test đổi thứ tự observer và transformer, rồi phân tích advice nào thấy dữ liệu gốc, advice nào thấy dữ liệu đã chuẩn hóa.
9. Thiết kế retry cho thao tác lưu trạng thái: nêu giới hạn lần thử, idempotency key, deadline và khả năng side effect trước lỗi.
10. Thêm một method trả `CompletableFuture<String>` và giải thích tại sao after-returning không tự thấy giá trị `String` cuối cùng.
11. Tạo một target chứa `this.internalMethod()`, so sánh gọi qua proxy với lời gọi nội bộ. Điều gì chứng minh giới hạn can thiệp?
12. Viết một bảng quyết định cho từng yêu cầu “quan sát”, “mutation object”, “replace argument”, “replace return”, “recover exception”; chọn loại advice tối thiểu và điều kiện công cụ cần có.

**Tự kiểm tra:** Vì sao mutation trên object khác với thay argument slot? After-returning có mặc nhiên thay được return không? Around có thể đổi outcome theo những cách nào? Nếu around ở ngoài recover lỗi, after-throwing ở trong có còn ghi nhận target thất bại không? Làm sao chứng minh thao tác biến đổi có tác động tới caller, không chỉ tới biến local của advice?

## Kết luận

**Quyền thay dữ liệu không chỉ phụ thuộc việc code được gọi là “advice”, mà phụ thuộc thời điểm chạy, loại advice, dữ liệu được bind và API điều khiển bước tiếp theo.** Before và các after advice thường phù hợp với **quan sát, validate hoặc cleanup**; chúng không mặc định thay argument slot, return value hay biến throw thành return. Around thường có khả năng **điều khiển lời gọi và outcome** rộng hơn, nhưng chỉ trong giới hạn cụ thể của engine, join point, type contract và đường weaving. Thiết kế tốt phải phân biệt **đọc**, **mutation object**, **thay input truyền tiếp**, **thay kết quả** và **thay luồng ngoại lệ**, sau đó kiểm chứng bằng kết quả caller/target thật, không chỉ nhìn log.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-009">Thiết kế advice nhỏ, rõ mục đích và ít tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế advice nhỏ, rõ mục đích và ít tác dụng phụ

## 1. Vì sao thiết kế advice lại quan trọng?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, *advice* là hành vi bổ sung được đưa vào một **join point** (điểm thực thi có thể can thiệp) được **pointcut** lựa chọn. Advice rất hữu ích cho các mối quan tâm cắt ngang như metrics, audit, tracing, kiểm tra chính sách hoặc dọn dẹp tài nguyên: nhiều nơi cần cùng một quy tắc nhưng mã nghiệp vụ không cần gọi lặp đi lặp lại cùng một đoạn xử lý.

Đặc điểm khiến advice hữu ích cũng làm nó dễ nguy hiểm: **mã bổ sung chạy không hiển hiện ngay tại chỗ gọi nghiệp vụ**. Một advice quá lớn, nhiều tác dụng phụ hoặc tự thay đổi kết quả có thể ảnh hưởng hàng trăm phương thức mà người bảo trì không nhận ra. Vì vậy, nguyên tắc cốt lõi là:

> **Một advice nên có một mục đích rõ, hợp đồng tác động nhỏ và phạm vi pointcut vừa đủ.** Nếu chỉ được thiết kế để quan sát, nó nên giữ nguyên kết quả, ngoại lệ và số lần thực thi nghiệp vụ.

Ví dụ, khi gọi `submitOrder()`:

- **Core business:** kiểm tra quy tắc đặt hàng, điều chỉnh trạng thái đơn hàng và trả kết quả.
- **Audit advice:** ghi nhận sự kiện “bắt đầu / hoàn tất / thất bại” bằng thông tin được phép lưu.
- **Metric advice:** ghi số lần hoàn tất và độ trễ theo nhãn ổn định.
- **Tracing advice:** gắn hoặc chuyển tiếp định danh tương quan tới hệ thống theo dõi.

Những hành vi trên có thể cùng áp dụng cho một join point nhưng **không nhất thiết nên nhét tất cả vào một advice duy nhất**. Tách nhỏ giúp đọc, thử, thay đổi và quyết định chính sách lỗi cho từng mối quan tâm mà không kéo theo phần còn lại.

## 2. Bốn ranh giới cần làm rõ trước khi viết advice

Một thiết kế dễ bảo trì bắt đầu từ những câu hỏi cụ thể, không bắt đầu bằng một hàm `around` thật dài.

| Ranh giới | Câu hỏi | Ví dụ hợp đồng |
| --- | --- | --- |
| **Điểm áp dụng** | Advice chạy ở join point nào? | Chỉ thao tác ghi đơn hàng tại ranh giới API |
| **Thời điểm** | Trước, sau thành công, sau lỗi hay bao quanh? | Audit thành công ở normal return; lỗi ở exceptional exit |
| **Dữ liệu** | Advice cần nhìn và được phép lưu điều gì? | Tên thao tác và loại kết thúc, không ghi payload khách hàng |
| **Tác động** | Advice được quyền thay đổi điều gì? | Chỉ quan sát; không sửa tham số, result hay ngoại lệ |

Một advice **không có pointcut hợp lý** sẽ không giải quyết đúng vấn đề: nếu pointcut quá rộng, nó ghi thừa hoặc làm chậm nhiều đoạn không liên quan; nếu quá hẹp, hành vi cần thiết bị thiếu. Nhưng **pointcut và advice vẫn là hai trách nhiệm riêng**. Pointcut diễn đạt “ở đâu”; advice diễn đạt “làm gì”.

Thiết kế “nhỏ” không được hiểu là chỉ vài dòng code. Một advice có thể gọi dịch vụ độc lập để thực hiện một chính sách phức tạp, miễn cho người đọc thấy **một mục đích duy nhất** và **hợp đồng rõ ràng** tại ranh giới can thiệp.

## 3. Nguyên tắc một trách nhiệm: mỗi advice trả lời một câu hỏi

Một advice có mùi thiết kế khi nó vừa ghi audit, vừa sửa đầu vào, vừa retry, vừa quản lý cache, vừa chuyển ngoại lệ, vừa đổi kết quả trả về. Các chức năng này khác nhau ở yêu cầu, rủi ro, số lần thực thi và cách xử lý thất bại.

So sánh:

| Mẫu thiết kế | Đặc điểm | Rủi ro bảo trì |
| --- | --- | --- |
| **Advice “tất cả trong một”** | Một `around` vừa kiểm quyền, ghi log, retry, cache, đo thời gian | Thứ tự phụ thuộc ngầm; khó phân biệt lỗi do phần nào |
| **Audit advice** | Chỉ ghi sự kiện trước/sau với dữ liệu tối thiểu | Kiểm thử chính sách audit riêng |
| **Metric advice** | Chỉ đo/đếm bằng nhãn có giới hạn | Có thể đo overhead riêng và thay backend riêng |
| **Authorization advice** | Chỉ áp dụng quyết định truy cập ở ranh giới đã định | Hợp đồng từ chối rõ; không che quy tắc miền |
| **Retry advice** | Chỉ quản lý số lần chuyển tiếp theo chính sách an toàn | Kiểm soát idempotency và giới hạn thử |
| **Transformation advice** | Chỉ thay kết quả theo một yêu cầu được công bố | Kiểm thử kiểu trả về và contract caller |

Không phải lúc nào cũng bắt buộc nhiều aspect: **tách về mặt trách nhiệm** mới là mục tiêu. Một engine có thể cung cấp những loại advice khác nhau trong cùng một aspect, trong khi mã triển khai vẫn được chia thành những hàm/dịch vụ có tên tốt. Ngược lại, tách thành nhiều class mà mỗi class vẫn xử lý nhiều chính sách không làm thiết kế tốt hơn.

Một kỹ thuật đặt tên hữu ích: tên advice nên chỉ ra **hành động cắt ngang** và **phạm vi** — ví dụ `recordOrderCompletion`, `countApiFailures`, `traceExternalCalls`. Tránh những tên chung như `handleEverything` hoặc `doMoreWork` khiến người đọc không thể dự đoán tác động.

## 4. Chọn loại advice ít quyền lực nhất đủ đáp ứng yêu cầu

Các loại advice có quyền can thiệp khác nhau. Dùng loại **đơn giản nhất đáp ứng yêu cầu**, thay vì luôn dùng `around` vì nó linh hoạt.

| Nhu cầu | Kiểu advice thường phù hợp | Lưu ý |
| --- | --- | --- |
| Ghi sự kiện bắt đầu | **Before** | Không có kết quả trả về |
| Chỉ ghi hoàn tất bình thường | **After returning** | Bình thường không đồng nghĩa nghiệp vụ thành công |
| Ghi ngoại lệ thoát khỏi join point | **After throwing** | Quan sát lỗi khác với nuốt hoặc đổi lỗi |
| Cleanup/ghi số liệu trên cả hai nhánh | **After / finally** | Bảo toàn nguyên nhân lỗi và phạm vi cleanup |
| Cần trực tiếp điều khiển thực thi | **Around** | Phải quản lý `proceed`, kết quả, exception và số lần gọi |

Giả sử chỉ muốn in thời điểm một phương thức hoàn tất thành công. `After returning` nói đúng mục đích và không yêu cầu ta quyết định có gọi target hay không. Nếu dùng `around`, chỉ một lỗi quên `proceed` có thể khiến nghiệp vụ **không chạy**, hoặc gọi `proceed` lần hai có thể gây **tác dụng phụ lặp**.

Tuy nhiên, **around** hoàn toàn hợp lý khi cần bao quanh lời gọi để đo thời gian chính xác qua cả hai nhánh, áp dụng caching hoặc điều khiển retry. Điều quan trọng là **thực thi đúng quyền mà hợp đồng cho phép**, không phải né tuyệt đối một loại advice.

## 5. Hợp đồng “ít tác dụng phụ” nghĩa là gì?

*Side effect* là thay đổi trạng thái quan sát được ngoài giá trị trả về thuần túy. Advice được thiết kế để ghi audit hoặc metrics **vẫn có tác dụng phụ hợp lệ** — nó ghi sự kiện hoặc cập nhật bộ đếm. “Ít tác dụng phụ” không có nghĩa “không được ghi log”; nó nghĩa là **chỉ gây đúng tác dụng phụ cần cho mục đích đã công bố, với ảnh hưởng tối thiểu lên nghiệp vụ**.

Đối với một advice chỉ quan sát, hãy dùng các bất biến làm tiêu chí thiết kế:

1. **Không thay kết quả nghiệp vụ:** caller nhận cùng giá trị và kiểu kết quả như khi không có advice.
2. **Không thay loại và ngữ nghĩa ngoại lệ:** ngoại lệ gốc được chuyển tiếp, trừ khi có chính sách biến đổi được công bố.
3. **Không tự ý đổi số lần gọi target:** một lời gọi qua aspect quan sát dẫn tới đúng một lời gọi nghiệp vụ nếu đường chạy vốn như vậy.
4. **Không sửa dữ liệu đầu vào hoặc object graph kết quả:** tránh ghi dữ liệu mutable vào chỗ chia sẻ hoặc biến đổi đối tượng theo cách caller không dự đoán.
5. **Không chèn lời gọi tốn thời gian vô hạn vào đường nóng:** hoạt động quan sát phải có chi phí và timeout hợp lý.
6. **Không lộ dữ liệu vượt mục đích:** log thông tin tối thiểu và phân quyền khi lưu trữ.

Mọi quy tắc đều có ngoại lệ theo nhu cầu hệ thống. Ví dụ authorization advice có thể cố ý từ chối truy cập, caching advice có thể bỏ gọi target, retry advice có thể gọi nhiều lần. Nhưng những khác biệt ấy cần xuất hiện trong **tên, hợp đồng, tài liệu và test**, không được ẩn sau một advice mang tên “logging”.

## 6. Quan sát dữ liệu an toàn: đầu vào, đầu ra, exception

Một advice có thể được cấp thông tin phương thức, đối số, kết quả hoặc ngoại lệ. Khả năng tiếp cận dữ liệu **không phải sự cho phép ghi mọi dữ liệu vào log**.

### 6.1. Tham số và kết quả

Không nên in `Arrays.toString(args)` hoặc serialize thẳng kết quả cho tất cả join point, đặc biệt với tài khoản, thông tin thanh toán, token, mật khẩu hoặc dữ liệu cá nhân. Thay vào đó, cân nhắc chỉ ghi:

- Tên hoặc mã thao tác đã được chuẩn hóa.
- Mã kết quả được cho phép, không phải toàn bộ response.
- Thời lượng, số đếm và trạng thái normal/exceptional completion.
- Correlation ID được tạo cho mục đích quan sát, không dùng token xác thực làm trace ID.

`toString()` của một object cũng có thể chậm, đụng dữ liệu nhạy cảm, chứa cấu trúc rất lớn, hoặc tự ném lỗi. **Không coi `toString()` là phép quan sát luôn an toàn**. Nếu advice chỉ cần biết `result == null` hoặc loại kết quả, hãy dùng kiểm tra tối thiểu thay vì dump nội dung.

### 6.2. Mutable object

Nếu method trả một `List<Order>` mutable và advice thực hiện `result.clear()`, caller sẽ thấy danh sách bị xóa dù advice không thay tham chiếu. Tương tự, advice thay đổi map đầu vào có thể khiến target xử lý dữ liệu khác. Đây là can thiệp nghiệp vụ thật, không còn là ghi nhận sự kiện.

Muốn biến đổi dữ liệu có chủ đích, cần một layer có hợp đồng riêng — thường là `around` hoặc cơ chế transformation tương ứng — và test rõ hành vi trước/sau. Advice quan sát nên coi các object được nhìn thấy là **read-only về mặt chính sách**, kể cả khi Java type chưa thật sự immutable.

### 6.3. Ngoại lệ

Advice ghi lỗi nên phân biệt **class lỗi**, nhãn xử lý và những trường có thể tiết lộ dữ liệu. Không phải message ngoại lệ nào cũng an toàn để lưu, vì nó có thể chứa email, đường dẫn nội bộ hoặc nội dung đầu vào.

Một advice `after throwing` chỉ log rồi chuyển tiếp lỗi khác với advice bắt và **nuốt** ngoại lệ. Nuốt lỗi có thể khiến caller tưởng thao tác thành công. Nếu yêu cầu là chuyển `SQLException` thành `StorageUnavailableException` (ví dụ ý niệm), đó là một chính sách thay đổi hợp đồng cần test riêng, không nên nằm ngầm trong audit advice.

## 7. Lỗi trong chính advice: fail-open, fail-closed và mức độ quan trọng

Một thách thức khó nhất là **observer tự gặp lỗi**. Ví dụ backend metrics đang quá tải, audit sink không ghi được, serializer lỗi hoặc API phát sự kiện timeout. Ta phải trả lời:

> Khi công cụ quan sát thất bại, nghiệp vụ có tiếp tục hoạt động hay cần bị từ chối?

Có ít nhất hai chính sách:

| Chính sách | Ý nghĩa | Khi cân nhắc | Rủi ro |
| --- | --- | --- | --- |
| **Fail-open** | Cho nghiệp vụ tiếp tục dù kênh quan sát lỗi | Metrics/log kỹ thuật không bắt buộc | Mất dữ liệu quan sát, cần có dấu hiệu giám sát riêng |
| **Fail-closed** | Không cho nghiệp vụ tiếp tục khi hành vi bắt buộc không hoàn tất | Yêu cầu kiểm soát hoặc audit bắt buộc theo hợp đồng | Giảm availability, lỗi hạ tầng có thể chặn giao dịch |

**Không có một lựa chọn đúng cho mọi advice.** Nếu audit là điều kiện tuân thủ bắt buộc, âm thầm bỏ qua lỗi có thể vi phạm yêu cầu. Nếu chỉ là biểu đồ latency phụ trợ, ném exception ra caller vì hệ thống metrics ngừng hoạt động có thể không phù hợp. Chính sách phải được thống nhất trước, được đặt tên và có kiểm thử mô phỏng backend lỗi.

Với advice fail-open, cũng không nên “nuốt mọi `Throwable`” vô điều kiện: nó có thể che `Error` nghiêm trọng hoặc phá tính đúng của hệ thống. Hãy giới hạn loại lỗi hạ tầng được phép xử lý, có dấu hiệu báo sự cố và tránh fallback tạo vòng đệ quy. Với fail-closed, phải làm rõ lúc nào từ chối, trả loại lỗi nào, và khi nào có khả năng rollback hoặc hậu quả giao dịch đã xảy ra.

**Thứ tự thời điểm quan trọng:** nếu audit bắt buộc phải hoàn tất trước khi giao dịch được chấp nhận, chỉ ghi ở `after returning` có thể quá muộn để đảm bảo tính nguyên tử. Khi có ràng buộc giao dịch, một cơ chế outbox hoặc đồng bộ dữ liệu được thiết kế phù hợp có thể cần thiết. Advice không tự biến hai hệ thống độc lập thành một giao dịch nguyên tử.

## 8. Ví dụ Java 17+ chạy được: tách audit và metrics

Ví dụ sau triển khai **mô hình AOP tối giản bằng JDK dynamic proxy**. Mục tiêu không phải cung cấp engine pointcut tổng quát mà để nhìn rõ thiết kế:

- `OrderCore` chỉ chứa nghiệp vụ và ném lỗi hợp đồng khi số lượng không hợp lệ.
- `AuditAdvice` chỉ ghi sự kiện bắt đầu/thành công/thất bại, không ghi input nhạy cảm hoặc sửa kết quả.
- `MetricAdvice` chỉ đếm **số lời gọi hoàn tất**, gồm thành công và thất bại, bằng `AtomicInteger`.
- Adapter `instrument()` chọn duy nhất phương thức `submit` và ghép hai advice theo thứ tự rõ ràng. `health()` không khớp nên bỏ qua hai advice.
- `callTarget()` bóc `InvocationTargetException` để caller nhìn thấy nguyên nhân nghiệp vụ gốc.

**Đây là chương trình Java độc lập**, không phải cú pháp của một engine AOP chuyên dụng. Tạo file `SmallAdviceDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public final class SmallAdviceDemo {
    interface OrderService {
        String submit(int quantity);
        String health();
    }

    static final class OrderCore implements OrderService {
        @Override
        public String submit(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            System.out.println("BUSINESS submit");
            return "accepted:" + quantity;
        }

        @Override
        public String health() {
            return "READY";
        }
    }

    @FunctionalInterface
    interface Proceeding {
        Object proceed() throws Throwable;
    }

    static final class AuditAdvice {
        Object apply(String operation, Proceeding next) throws Throwable {
            System.out.println("AUDIT begin " + operation);
            try {
                Object result = next.proceed();
                System.out.println("AUDIT success " + operation);
                return result;
            } catch (Throwable error) {
                System.out.println("AUDIT failure " + operation + " "
                        + error.getClass().getSimpleName());
                throw error;
            }
        }
    }

    static final class MetricAdvice {
        private final AtomicInteger completed = new AtomicInteger();

        Object apply(Proceeding next) throws Throwable {
            try {
                return next.proceed();
            } finally {
                completed.incrementAndGet();
            }
        }

        int completedCalls() {
            return completed.get();
        }
    }

    static Object callTarget(OrderService target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static OrderService instrument(OrderService target, AuditAdvice audit,
                                   MetricAdvice metrics) {
        return (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[] { OrderService.class },
                (proxy, method, args) -> {
                    Proceeding original = () -> callTarget(target, method, args);
                    if (!method.getName().equals("submit")) {
                        return original.proceed();
                    }
                    return metrics.apply(
                            () -> audit.apply("submit", original));
                });
    }

    public static void main(String[] args) {
        AuditAdvice audit = new AuditAdvice();
        MetricAdvice metrics = new MetricAdvice();
        OrderService service = instrument(new OrderCore(), audit, metrics);

        System.out.println("STATUS " + service.health());
        System.out.println("RESULT " + service.submit(2));
        try {
            service.submit(0);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        System.out.println("METRIC completed=" + metrics.completedCalls());
    }
}
~~~

Biên dịch/chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 SmallAdviceDemo.java
java SmallAdviceDemo
~~~

Đầu ra đã được kiểm chứng:

~~~text
STATUS READY
AUDIT begin submit
BUSINESS submit
AUDIT success submit
RESULT accepted:2
AUDIT begin submit
AUDIT failure submit IllegalArgumentException
CALLER quantity must be positive
METRIC completed=2
~~~

### 8.1. Đọc luồng thành công

`health()` là phương thức không khớp; nó chỉ trả `READY` và **không sinh audit hoặc metrics**. Với `submit(2)`, adapter áp dụng `MetricAdvice` bao ngoài `AuditAdvice`:

~~~text
MetricAdvice.enter
  AuditAdvice.begin
    OrderCore.submit(2) -> "accepted:2"
  AuditAdvice.success
MetricAdvice.finally increments completed
Caller receives "accepted:2"
~~~

`AuditAdvice` không sửa `"accepted:2"` và `MetricAdvice` trả lại đúng kết quả của tầng trong. Kết quả caller nhận được bảo toàn. `MetricAdvice` chỉ đếm sau khi tầng bên trong kết thúc, không yêu cầu biết loại dữ liệu được trả về.

### 8.2. Đọc luồng thất bại

Với `submit(0)`, nghiệp vụ ném `IllegalArgumentException` ngay trước câu lệnh `BUSINESS submit`. `AuditAdvice` ghi loại ngoại lệ, ném lại chính đối tượng đó. `MetricAdvice` tăng bộ đếm trong `finally` và cho ngoại lệ tiếp tục đến caller. Không có `AUDIT success` hoặc `RESULT` cho lời gọi lỗi.

**Bộ đếm `completed=2` không phải số lượt thành công.** Nó đếm hai lần `submit` kết thúc (một normal, một exceptional). Nếu muốn success count và failure count riêng, hãy định nghĩa **hai metric có ý nghĩa rõ ràng** hoặc một cơ chế phân loại trạng thái, không đặt tên mập mờ `successes` cho một counter trong `finally`.

### 8.3. Giới hạn thiết kế của ví dụ

- `System.out.println` chỉ là sink minh họa, không phải hạ tầng audit sản xuất với bảo đảm lưu trữ; nếu sink lỗi, kết quả có thể bị ảnh hưởng. Phần ví dụ **không hiện thực fail-open/fail-closed**; phải quyết định chính sách trước khi dùng thực tế.
- Bộ chọn `method.getName().equals("submit")` được giữ đơn giản để dễ đọc. Khi có overload, cần lọc thêm chữ ký/annotation để không áp dụng nhầm.
- JDK proxy chỉ chặn lời gọi **qua interface proxy**. Gọi trực tiếp `new OrderCore().submit(2)` không qua advice; self-invocation trong target cũng có thể bỏ qua proxy.
- `AtomicInteger` xử lý cập nhật counter đồng thời nhưng không tự giải quyết các vấn đề chia sẻ ngữ cảnh, bảo mật và tính nguyên tử của audit.
- Hai advice đang dùng một dạng **around wrapper** để dễ minh họa hợp đồng chuyển tiếp. Trong engine AOP có các kiểu `before`, `after returning`, `after throwing` và `finally` riêng, ta có thể phân chia vị trí thực thi thích hợp với mục đích của từng advice.

## 9. Tách điểm quan sát khỏi cơ chế ghi sự kiện

Một advice được viết tốt có thể đóng vai trò **adapter**: đọc context tối thiểu và gọi một dịch vụ chuyên trách. Ví dụ, advice audit không cần biết logic gửi HTTP, format JSON, vòng đời connection, retry của message broker hoặc nơi lưu trữ. Những chi tiết đó thuộc về **audit sink/transport**; pointcut và advice chỉ quyết định **khi nào phát sự kiện và dữ liệu nào cần phát**.

Sơ đồ:

~~~text
selected join point
      |
      v
  AuditAdvice
      |
      +--> AuditEvent {operation, outcome, correlationId}
                   |
                   v
              AuditSink
                |
         +------+------+
         |             |
      Storage       Queue/Stream
~~~

Sơ đồ này không phải lời khuyên phải dùng queue trong mọi tình huống. Điểm mấu chốt là **phân tách việc quyết định sự kiện với cách ghi nó**. Nếu audit có yêu cầu durability hoặc tính nhất quán với giao dịch, sink và cơ chế bảo đảm cần được lựa chọn theo yêu cầu; đưa event vào queue không tự động bảo đảm “chắc chắn không mất dữ liệu”.

Các interface nhỏ thường dễ fake trong kiểm thử:

~~~java
interface AuditSink {
    void record(AuditEvent event);
}

record AuditEvent(String operation, String outcome) {}
~~~

Đây là **đoạn minh họa hợp đồng**, không phải chương trình chạy riêng. Advice có thể cung cấp một `AuditEvent` nhỏ, còn sink giả trong unit test ghi nhận đúng sự kiện và mô phỏng lỗi. Không cần mock cả engine AOP để kiểm tra cách serialize hoặc quyết định lưu trữ.

## 10. Thứ tự advice và các tác dụng phụ gián tiếp

Hai advice đều nhỏ nhưng kết hợp sai thứ tự vẫn có thể gây hành vi không mong đợi. Giả sử có audit, authorization, retry và metrics:

~~~text
Timing
  Audit
    Authorization
      Retry
        Business
~~~

Nếu authorization từ chối trước khi business chạy, audit nằm ngoài authorization có thể thấy sự kiện bị từ chối; audit nằm phía trong authorization có thể **không hề chạy**. Nếu retry bên trong metrics, một metric có thể bao toàn bộ các lần thử; nếu metrics bên trong retry, mỗi lần thử có thể được đo riêng. Tên metric phải phản ánh thứ nó thực sự đếm.

Không có một thứ tự “chuẩn” cho mọi ứng dụng. Hãy mô tả trước:

1. Ai được phép chặn target?
2. Ai cần thấy cả thất bại do authorization?
3. Số lần gọi target có thay đổi khi retry không?
4. Advice nào bắt đầu và kết thúc phép đo?
5. Khi một advice ném lỗi, những tầng phía ngoài sẽ thấy nó dưới dạng gì?
6. Một advice bên trong có thay đổi kết quả trước khi advice ngoài nhìn thấy không?

**Precedence thuộc hợp đồng AOP cụ thể**, không được đoán từ tên file, thứ tự method hay thứ tự khai báo class. Viết integration test với thứ tự đã chọn thay vì chỉ unit test riêng từng advice.

### 10.1. Phân biệt lỗi của target và lỗi của advice

Giả sử after-returning advice tự ném `IllegalStateException` trong lúc serialize kết quả. Target có thể đã hoàn tất bình thường, nhưng một wrapper ngoài có thể quan sát **exceptional completion**. Nếu nó ghi “nghiệp vụ thất bại” một cách máy móc, metric sẽ sai.

Cần phân biệt ít nhất ba nguồn: lỗi nghiệp vụ, lỗi chính sách chủ động từ chối, và lỗi hạ tầng do chính advice. Đừng biến mọi exception trong chuỗi advice thành cùng một nhãn “business_failure”.

### 10.2. Tránh recursion khi advice ghi log

Nếu pointcut chọn mọi lời gọi dịch vụ và audit sink cũng sử dụng một dịch vụ nằm trong tập ấy, một sự kiện audit có thể kích hoạt advice audit lần nữa, rồi tiếp tục vô hạn. Cách giảm rủi ro:

- Loại trừ lớp/hợp đồng của logging hoặc audit sink khỏi pointcut.
- Sử dụng một ranh giới kỹ thuật rõ ràng giữa phần quan sát và phần phát sự kiện.
- Nếu cần chặn reentrancy, quản lý guard theo phạm vi lời gọi, cleanup trong `finally`, và kiểm thử lồng nhau.

Không dùng biến `static boolean` toàn cục làm guard ngây thơ: nó có thể chặn nhầm giữa các thread, không xử lý lồng nhau và dễ bị giữ lại sau ngoại lệ.

## 11. Trạng thái dùng chung và thread-safety

Một advice có thể dùng lại qua hàng nghìn lời gọi đồng thời. Điều đó khiến các field mutable đáng cân nhắc:

- **Không lưu dữ liệu riêng của mỗi lời gọi trong field của singleton advice** nếu không có cơ chế cách ly. Ví dụ field `currentUserId` có thể bị thread khác ghi đè.
- **Cấu hình bất biến** như tên service hoặc policy id thường an toàn hơn khi được khởi tạo một lần.
- **Bộ đếm chung** có thể dùng `AtomicInteger`, `AtomicLong`, `LongAdder` hoặc hệ metrics tùy yêu cầu độ chính xác/tải.
- **Ngữ cảnh riêng của request** có thể được cung cấp qua đối số advice, ngữ cảnh do engine quản lý hoặc cơ chế propagation thích hợp; không tự giả định `ThreadLocal` đi theo `CompletableFuture` giữa thread.

Trong ví dụ, `AuditAdvice` không giữ state theo lời gọi. `MetricAdvice` chỉ có `AtomicInteger` chung, phù hợp cho minh họa và các cập nhật đồng thời đơn giản. Với hệ thống thực, nên đo overhead và tránh nhãn metric có độ phân biệt quá lớn như mọi mã đơn hàng/user ID, vì chúng có thể làm nổ cardinality và bộ nhớ.

## 12. Vấn đề bất đồng bộ và kết quả trả về

Một phương thức `CompletableFuture<Result> start()` có thể trả Future ngay trong khi công việc bên trong chưa xong. Advice `after returning` quan sát normal return của **method**, không nhất thiết quan sát **completion của task**. Nếu audit chỉ đếm “đã nhận yêu cầu”, đó có thể là điểm phù hợp. Nếu muốn đếm “đơn hàng cuối cùng hoàn tất thành công”, phải quan sát completion của Future hoặc sự kiện nghiệp vụ tương ứng.

Không nên bắt mọi lời gọi bất đồng bộ bằng cách chặn thread hoặc gọi `join()` trong advice chỉ để lấy kết quả: nó có thể thay đổi latency, deadlock, điều kiện timeout và xử lý ngoại lệ. Thay vào đó, dùng callback hoặc instrumentation tương ứng khi cần, với hợp đồng xử lý cancellation và exceptional completion.

Một lời khuyên thực tế: **đặt tên metric/event theo thời điểm thật được đo**. `requestAccepted` khác `taskCompleted`; `methodReturnedNormally` khác `businessSucceeded`. Đặt tên đúng giúp tránh lẫn lộn ngay cả khi logic advice nhỏ.

## 13. Khả năng can thiệp của công cụ là một phần hợp đồng

Advice tốt nhưng chạy không đúng nơi vẫn tạo hệ thống khó dự đoán. Với AOP, phải xác nhận kỹ thuật được dùng có thể quan sát loại join point đã chọn:

| Cơ chế/đường gọi | Điều có thể khác nhau |
| --- | --- |
| Proxy dựa trên interface | Thường chỉ thấy lời gọi qua interface proxy |
| Proxy cần override method | Có hạn chế với `final`/`private`, phụ thuộc kỹ thuật |
| Bytecode weaving | Có thể chèn sâu hơn nếu engine hỗ trợ loại điểm và lớp được weaving |
| Self-invocation `this.method()` | Có thể bypass proxy khi gọi nội bộ |
| Constructor, field access, exception handler | Không phải engine nào cũng hỗ trợ những join point này |
| Tác vụ bất đồng bộ | Không đảm bảo tự truyền ngữ cảnh theo thread/task |
| Method call và method execution | Có thể là hai join point khác nhau với phạm vi khác nhau |

Không nên khẳng định “đã khai báo pointcut là chắc chắn áp dụng lên mọi phương thức” hoặc “mọi engine đều có chung cú pháp advice”. Hãy kiểm thử **đường gọi thực** qua ranh giới proxy/weaving và các trường hợp không khớp. Điều này quan trọng khi dùng advice để kiểm quyền hoặc audit bắt buộc, vì một đường bypass có thể làm mất chính sách bảo vệ.

## 14. Quy trình refactor một advice quá lớn

Khi thấy một advice dài và có nhiều mối quan tâm, có thể cải tiến theo trình tự:

1. **Liệt kê toàn bộ side effect hiện có:** gọi logger, ghi DB, sửa input, thay output, retry, chuyển exception, đo thời gian, cập nhật cache.
2. **Vẽ thứ tự hành vi hiện tại:** trước target, sau normal return, sau exception, trong finally. Ghi nhận số lần `proceed` và điều kiện target bị bỏ qua.
3. **Tách chính sách theo mục tiêu:** audit, metrics, authorization, retry, transformation. Đặt tên rõ các hợp đồng.
4. **Di chuyển logic hạ tầng sang dịch vụ riêng:** ví dụ tách serialization và transport ra khỏi advice audit.
5. **Quy định chính sách thất bại cho từng phần:** audit bắt buộc khác metrics phụ trợ.
6. **Chọn loại advice với quyền kiểm soát tối thiểu:** không giữ mọi thứ trong một `around` nếu có lựa chọn đơn giản hơn.
7. **Cố định precedence của các thành phần** và chạy các ca lỗi/return/cancellation.
8. **So sánh hành vi trước và sau refactor:** giá trị trả, loại exception, số lần target chạy, sự kiện audit, metric và thời lượng.
9. **Kiểm thử đường không đi qua advice:** gọi trực tiếp target, self-invocation, overload và tác vụ bất đồng bộ.
10. **Đo overhead** trên tải đại diện, không chỉ kiểm tra tính đúng trong unit test.

Refactor đúng không nhất thiết làm tổng số dòng ít hơn. Mục tiêu là **giảm coupling và rủi ro**, giúp thay đổi chính sách logging không làm thay đổi retry hoặc hợp đồng của API nghiệp vụ.

## 15. Bảng kiểm thử cho một advice nhỏ và an toàn

| Ca thử | Mục đích | Kỳ vọng |
| --- | --- | --- |
| Lời gọi được chọn, return bình thường | Giữ kết quả | Caller nhận kết quả gốc |
| Lời gọi được chọn, ném ngoại lệ | Giữ lỗi | Caller nhận cùng nguyên nhân/loại lỗi theo hợp đồng |
| Điểm không được chọn | Kiểm pointcut | Không sinh tác dụng phụ của advice |
| Lời gọi được chọn lần thứ hai | Số lần chạy | Không nhân đôi target ngoài chính sách |
| Kết quả `null` | Quan sát an toàn | Không tự phát sinh `NullPointerException` |
| Method `void` hoặc primitive | Đúng loại dữ liệu | Không ép kiểu sai |
| Audit sink lỗi | Chính sách lỗi | Fail-open/closed theo hợp đồng, cảnh báo phù hợp |
| Hai advice cùng áp dụng | Kiểm thứ tự | Event và metric đúng phạm vi |
| Lời gọi đồng thời | Trạng thái chia sẻ | Không race hoặc rò dữ liệu giữa các lần gọi |
| Trường hợp `CompletableFuture` | Phân biệt thời điểm | Không gọi method return là task completion |
| Gọi trực tiếp target | Kiểm interception | Không giả định advice trên proxy tự chạy |
| Điểm lặp/đệ quy logging | Kiểm reentrancy | Không tạo chuỗi gọi vô hạn |

**Đừng chỉ gọi thẳng hàm advice trong unit test.** Cách ấy xác minh thuật toán advice nhưng không xác minh pointcut, weaving, proxy và thứ tự thực tế. Cần ít nhất một integration test đi qua chính đường can thiệp của ứng dụng.

Một tiêu chí rất hữu ích là **behavior-preserving test** cho advice quan sát:

~~~text
runWithoutAdvice(input) -> (result, exceptionType, businessCallCount)
runWithAdvice(input)    -> (result, exceptionType, businessCallCount)

Kỳ vọng:
  result và exceptionType tương đương theo hợp đồng
  businessCallCount không tăng ngoài ý định
  chỉ các quan sát được cho phép xuất hiện thêm
~~~

Đây là pseudocode mô tả test, không phải mã có thể chạy riêng. Nếu advice có chủ đích thay đổi hành vi, hãy thay các assertion tương ứng bằng **hợp đồng chuyển đổi đã công bố**, thay vì lặng lẽ bỏ kiểm thử.

## 16. Lỗi thiết kế thường gặp

1. **Một advice quản mọi trách nhiệm:** vừa audit, caching, retry, auth, sửa kết quả và chuyển ngoại lệ; khó cô lập lỗi và phụ thuộc ngầm.
2. **Dùng `around` dù chỉ cần quan sát một thời điểm:** mở rộng quyền kiểm soát luồng không cần thiết.
3. **Quên `proceed` hoặc gọi hai lần:** bỏ thực thi nghiệp vụ hoặc lặp lại tác dụng phụ.
4. **Ghi log nguyên payload hoặc exception message nhạy cảm:** rủi ro bảo mật và chi phí lớn.
5. **Sửa result/args mutable dù advice được mô tả “chỉ quan sát”:** caller và target nhận dữ liệu ngoài hợp đồng.
6. **Nuốt mọi lỗi của logger:** có thể mất audit bắt buộc hoặc che lỗi hạ tầng nghiêm trọng.
7. **Để lỗi metrics tự ném phá kết quả nghiệp vụ:** nếu chính sách định nghĩa metrics fail-open, hành vi này sai hợp đồng.
8. **Bộ đếm không thread-safe trong advice dùng chung:** kết quả không ổn định dưới tải đồng thời.
9. **Không kiểm tra precedence:** audit có thể bỏ lỡ từ chối auth; metrics có thể đo sai vùng hoặc đếm sai retry.
10. **Không loại trừ logger khỏi pointcut:** tạo reentrancy và vòng gọi vô hạn.
11. **Coi normal return là thành công nghiệp vụ:** `Result.failure` hoặc `false` có thể là kết quả bình thường.
12. **Cho rằng proxy nhìn thấy mọi lời gọi:** self-invocation, gọi target trực tiếp và các loại join point chưa hỗ trợ có thể bypass.
13. **Thêm network call chậm vào mọi điểm nóng:** tăng tail latency dù logic nghiệp vụ không thay đổi.
14. **Giấu bất biến nghiệp vụ trong advice kỹ thuật:** khiến người đọc code miền không thấy điều kiện quan trọng và việc bypass trở nên nguy hiểm.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Advice ghi log có thể vẫn có side effect không?  
**Đáp án:** Có. Nó ghi dữ liệu vào một nơi khác; mục tiêu là side effect **được cho phép, tối thiểu và dự đoán được**, không phải tuyệt đối không có side effect.

**Câu 2.** Vì sao metrics và audit có thể nên là hai advice tách biệt?  
**Đáp án:** Chúng có mục đích, dữ liệu, hạ tầng và chính sách xử lý lỗi khác nhau; tách trách nhiệm giúp kiểm thử và thay đổi độc lập.

**Câu 3.** Nếu audit ghi lỗi và để exception từ logger thay exception nghiệp vụ, có phải advice “chỉ quan sát” đã bảo toàn hợp đồng không?  
**Đáp án:** Không. Caller có thể nhìn thấy loại lỗi khác; phải quyết định fail-open/fail-closed và kiểm thử.

**Câu 4.** `MetricAdvice` tăng `completed` trong `finally`. Có thể gọi đây là số giao dịch thành công không?  
**Đáp án:** Không; bộ đếm bao gồm cả normal và exceptional completion trong phạm vi được bao.

**Câu 5.** Vì sao cần test `health()` không khớp pointcut?  
**Đáp án:** Kiểm thử các trường hợp **không được chọn** giúp phát hiện overmatching, tránh advice chạy thừa.

**Câu 6.** Nếu một advice cần retry `submitOrder()`, giữ một lượt `proceed` có phải hợp đồng phù hợp không?  
**Đáp án:** Retry có thể gọi nhiều lần có chủ đích; vì vậy phải xác định số lần tối đa, lỗi được phép retry, và bảo vệ idempotency/tác dụng phụ của nghiệp vụ.

**Câu 7.** Có thể dùng một after-returning của method trả Future để kết luận task thành công không?  
**Đáp án:** Không. Nó có thể chỉ cho biết method đã trả Future bình thường; task có thể chưa hoàn tất.

## 18. Tổng kết

Một **advice tốt không chỉ “chạy đúng thời điểm” mà còn “chỉ làm đúng điều được giao”**. Hãy giới hạn mỗi advice ở một mục đích rõ, chọn loại advice ít quyền lực nhất phù hợp, thiết kế dữ liệu quan sát an toàn, và quy định riêng chính sách khi chính advice gặp lỗi. Với advice chỉ quan sát, ưu tiên **giữ nguyên giá trị trả về, ngoại lệ và số lần thực thi target**; những thay đổi có chủ đích phải là hợp đồng công khai có kiểm thử. Cuối cùng, kiểm tra thứ tự nhiều advice, trạng thái chia sẻ, phạm vi proxy/weaving và đường bất đồng bộ để các hành vi cắt ngang thực sự nâng cao tính bảo trì thay vì tạo tác dụng phụ khó truy vết.

</details>

- [Quay lại đầu trang](#back-to-top)
