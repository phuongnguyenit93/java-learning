<a id="back-to-top"></a>

# Thiết kế kiến trúc

## Menu
- [Chọn mối quan tâm đủ ổn định để tách thành aspect](#aspect-architecture-design-001)
- [Xác định nơi áp dụng và nơi tuyệt đối không được can thiệp](#aspect-architecture-design-002)
- [Nguyên tắc trách nhiệm đơn và độ kết dính của aspect](#aspect-architecture-design-003)
- [Phụ thuộc ẩn giữa mã nghiệp vụ với cấu hình pointcut](#aspect-architecture-design-004)
- [Pointcut mong manh khi đổi tên, di chuyển hoặc nạp chồng phương thức](#aspect-architecture-design-005)
- [Biên giới mô-đun và khả năng hiểu hành vi từ mã nguồn](#aspect-architecture-design-006)
- [So sánh với decorator, middleware, interceptor và instrumentation](#aspect-architecture-design-007)
- [Giữ quy tắc nghiệp vụ cốt lõi tường minh khi cần](#aspect-architecture-design-008)
- [Tài liệu hóa phạm vi, hợp đồng và các quyết định về thứ tự](#aspect-architecture-design-009)

## <a id="aspect-architecture-design-001">Chọn mối quan tâm đủ ổn định để tách thành aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn mối quan tâm đủ ổn định để tách thành Aspect

## 1. Mục tiêu của quyết định kiến trúc

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP)**, một **aspect** tập trung xử lý một mối quan tâm xuyên suốt (**cross-cutting concern**) xuất hiện tại nhiều vị trí trong hệ thống. Các mối quan tâm thường gặp gồm ghi nhật ký tại ranh giới nghiệp vụ, đo lường, tracing, kiểm soát truy cập, giao dịch, kiểm toán hoặc cơ chế giới hạn tài nguyên.

Tuy nhiên, **việc một đoạn mã lặp lại nhiều lần chưa đủ để biến nó thành aspect**. Logic có thể lặp vì nhiều use case chia sẻ cùng quy tắc nghiệp vụ, cùng thuật toán hoặc cùng đối tượng miền. Những phần này thường phù hợp hơn với service, component, hàm chung hoặc abstraction nghiệp vụ tường minh.

Tách thành aspect là một quyết định về **ranh giới trách nhiệm**: hành vi được đưa ra khỏi thân chương trình nhưng vẫn được thực thi bằng cách chặn các điểm thích hợp. Vì thế cần xem xét cả **độ ổn định của quy tắc**, **độ ổn định của điểm cắt**, **tính nhất quán giữa các nơi áp dụng**, **hợp đồng lỗi**, **khả năng kiểm thử** và **chi phí diễn giải hành vi ẩn**.

**Luận điểm chính:** chỉ nên chọn một concern làm aspect khi nó có chính sách đủ chung và bền vững, có điểm áp dụng xác định được, ít phụ thuộc chi tiết nội bộ của từng use case và đem lại lợi ích nhất quán lớn hơn độ phức tạp của cơ chế interception.

## 2. Các thuật ngữ nền tảng của AOP

- **Concern:** một mối quan tâm hoặc trách nhiệm của hệ thống, chẳng hạn bảo mật hay ghi log.
- **Cross-cutting concern:** concern áp dụng trên nhiều thành phần hoặc luồng xử lý, làm mã xử lý phân tán nếu viết trực tiếp ở từng nơi.
- **Join point:** điểm trong hoạt động chương trình mà cơ chế AOP có thể can thiệp, chẳng hạn lần gọi hoặc thực thi một phương thức. Loại join point được hỗ trợ tùy cơ chế.
- **Pointcut:** quy tắc chọn một tập join point để áp dụng hành vi chung.
- **Advice:** hành vi chạy before, after, after returning, after throwing hoặc around join point, tùy mô hình AOP.
- **Aspect:** đơn vị đóng gói pointcut cùng advice cho một hoặc nhiều chính sách liên quan.
- **Weaving/interception:** cách đưa advice vào đường thực thi, có thể qua công cụ biến đổi mã hoặc lớp trung gian kiểu proxy/interceptor.
- **Proceed:** hành động cho phép đường thực thi tiếp tục tới join point/chuỗi advice tiếp theo; lời gọi quanh proceed có thể mang ý nghĩa khác nhau tùy hệ thống AOP.

Điểm quan trọng về mặt kiến trúc: **aspect không phải nơi chứa mọi logic dùng chung**. Nó là nơi quản lý một chính sách hoặc hành vi áp dụng **theo các join point được chọn**, thường không nằm trong API nghiệp vụ công khai. Nếu caller cần chủ động chọn chính sách tại từng lần gọi, một lời gọi API rõ ràng có thể phù hợp hơn.

## 3. Phân biệt ba loại mã thường bị nhầm

| Loại trách nhiệm | Ví dụ | Giải pháp thường phù hợp |
| --- | --- | --- |
| Quy tắc nghiệp vụ dùng chung | Tính chiết khấu theo hợp đồng, định giá, xác nhận hạn mức | Domain service, policy object, hàm nghiệp vụ |
| Cơ chế hạ tầng tái sử dụng | Serialize dữ liệu, truy vấn kho lưu trữ, gửi HTTP | Thư viện, component, adapter |
| Chính sách áp dụng tại nhiều boundary | Ghi business outcome, kiểm tra quyền theo action, đo duration | Aspect nếu thỏa các điều kiện lựa chọn |

Ví dụ “ghi thời gian của mọi operation nghiệp vụ được đánh dấu rõ” có thể tách thành aspect vì **thuật toán quan sát** gần như giống nhau. Nhưng “đơn hàng của khách VIP giảm 7%” là quy tắc định giá; dù nhiều phương thức cần tính chiết khấu, không nên giấu nó trong advice bắt các phương thức có tên chứa “calculate”.

Một trường hợp nằm giữa là **kiểm soát truy cập**: mechanism kiểm tra và deny có thể làm aspect, còn **quyết định Alice được sửa tài liệu X không** phải dựa trên policy và resource thực, thường cần một thành phần phân quyền có hợp đồng rõ. Aspect điều phối tại boundary chứ không tự thay thế logic miền.

## 4. Tính ổn định ở đây nghĩa là gì?

Một concern đủ ổn định không có nghĩa **không bao giờ thay đổi**. Nó có nghĩa có thể dự đoán thay đổi và quản lý trong một điểm chung mà ít gây tác động ngoài ý muốn.

Có ít nhất bốn chiều ổn định:

1. **Ổn định ngữ nghĩa:** ý nghĩa của hành vi không đổi khi các use case được refactor. Ví dụ “một completion event trên mỗi operation” có ý nghĩa ổn định hơn “in tên mọi method private”.
2. **Ổn định chính sách:** cách áp dụng phần chung gần như thống nhất; khác biệt được biểu diễn qua metadata hoặc tham số policy rõ ràng, không phải vô số nhánh if theo tên class.
3. **Ổn định điểm cắt:** các join point được chọn biểu diễn contract có ý nghĩa, không phụ thuộc đường dẫn package, tên helper hoặc chữ ký nội bộ dễ thay đổi.
4. **Ổn định vòng đời:** advice hiểu chính xác mốc bắt đầu, mốc kết thúc, lỗi và cancellation của công việc mà nó đo hoặc kiểm soát.

Một concern có thể ổn định về chính sách nhưng không ổn định về điểm cắt. Chẳng hạn đo kết quả của một workflow kéo dài là yêu cầu bền vững, song không thể chỉ chặn một phương thức trả về Future rồi coi đó là kết thúc workflow. Khi ấy phải thiết kế lại điểm quan sát hoặc observer theo kết quả bất đồng bộ; không ép vào một around advice đồng bộ.

## 5. Các câu hỏi sàng lọc trước khi tạo Aspect

### 5.1. Concern có thật sự cắt ngang nhiều vùng độc lập không?

Nếu chỉ một use case áp dụng, mã tường minh tại use case thường dễ đọc hơn. Nếu nhiều chức năng độc lập cùng cần chính sách tương đương, AOP có thể giảm phân tán. Nhưng đừng lấy **số dòng trùng** làm chỉ số duy nhất: đôi khi vài dòng ghi log lặp lại là hợp lý, trong khi một aspect có pointcut khó hiểu gây rủi ro lớn.

### 5.2. Có thể mô tả chính sách bằng một câu ngắn, không kể tên từng use case?

Một chính sách phù hợp: “Ghi đúng một bản ghi outcome với duration cho mỗi operation được đánh dấu”. Một “chính sách” đáng nghi: “Khi loại invoice X thì kiểm tra A, loại order Y thì sửa dữ liệu B, trừ khi class nằm ở package Z”. Trường hợp sau chứa nhiều nhánh nghiệp vụ, khó giữ ổn định trong aspect.

### 5.3. Có điểm cắt tường minh, đủ tin cậy không?

Tốt: operation được đánh dấu bằng một metadata rõ hoặc contract interface được review. Dễ vỡ: mọi phương thức có tên bắt đầu bằng process, mọi public method, hoặc mọi method trong package dễ bị di chuyển. Một điểm cắt tốt còn phải bao phủ **các đường gọi thực tế**; nếu một phần nghiệp vụ bypass proxy, mục tiêu bảo vệ/quan sát không đạt.

### 5.4. Advice có thể bảo toàn hành vi của join point không?

Đối với logging hoặc metric, việc thêm advice thường không được đổi kết quả, exception hay số lần thực hiện nghiệp vụ. Với authorization, chủ ý duy nhất là từ chối những lời gọi không được phép trước side effect. Với transaction hoặc retry, hành vi là một phần của hợp đồng và phải được công bố rõ; không tự biến read operation thành retry có side effect lặp.

### 5.5. Ai sở hữu vòng đời và kết quả?

Nếu một method trả CompletionStage, advice sau method return có thể đo duration của lời gọi ngắn, chưa đo tác vụ. Nếu concern cần toàn bộ duration thì cần completion observer hoặc điểm khác. Với stream lười, publisher creation và subscription là hai thời điểm khác nhau. Nếu vòng đời không phù hợp với join point sẵn có, AOP không nên che giấu vấn đề.

### 5.6. Có kiểm chứng được phạm vi và outcome không?

Có thể kiểm thử rằng mọi protected operation thật sự bị chặn, mỗi operation được đo một lần, lỗi gốc không bị nuốt và helper không bị instrument ngoài ý muốn không? Nếu không, complexity của aspect có thể lớn hơn lợi ích.

## 6. Ma trận đánh giá trước khi quyết định

Dùng bảng dưới như công cụ trao đổi, không phải thuật toán cộng điểm cứng.

| Tiêu chí | Tín hiệu thuận lợi | Tín hiệu rủi ro |
| --- | --- | --- |
| Nhiều nơi cùng cần | Nhiều use case có nhu cầu giống nhau | Chỉ một caller đặc thù |
| Chính sách đồng nhất | Một contract và một cách xử lý rõ | Nhiều ngoại lệ dựa trên loại nghiệp vụ |
| Điểm cắt ổn định | Boundary/annotation/contract rõ | Glob package hoặc tên helper dễ đổi |
| Coupling với miền | Chỉ cần metadata tối thiểu | Cần đọc và sửa nhiều đối tượng nghiệp vụ |
| Bảo toàn hành vi | Advice không đổi result/exception ngoài chủ đích | Retry/sửa kết quả ngầm |
| Vòng đời | Khớp invocation hoặc có lifecycle observer đúng | Async completion bị coi như method return |
| Kiểm thử | Có thể xác minh số lượt, lỗi và exclusion | Hành vi ẩn, coverage không biết |
| Thứ tự composition | Quan hệ với advice khác rõ | Kết quả phụ thuộc thứ tự khó kiểm soát |
| Khả năng vận hành | Có owner, metric/log ổn định | Log trùng, counter tăng đôi, overhead lớn |
| Khả năng thay đổi | Thêm use case mới qua metadata rõ | Mỗi tính năng phải sửa nhiều if trong aspect |

Nếu nhiều ô ở cột rủi ro cùng xuất hiện, ưu tiên component hoặc API tường minh, hoặc tách concern thành **mechanism chung + policy nghiệp vụ riêng**. Không cần “cố đạt đủ điểm” để dùng AOP.

## 7. Những concern thường có tiềm năng tách thành Aspect

### 7.1. Logging tại ranh giới nghiệp vụ

Aspect có thể nhận operation name ổn định, timestamp, duration, outcome và correlation ID, rồi ghi event theo schema chung. Điểm cắt nên là boundary có ý nghĩa, không phải mọi method helper.

Điều ổn định: **hợp đồng log và nơi đặt logging**. Điều biến thiên: tên operation và một số metadata cho phép.

Rủi ro: log tất cả arguments làm lộ dữ liệu, log success khi method trả về stage chưa xong, nhiều aspect cùng phát event cuối khiến đếm trùng.

### 7.2. Metrics và tracing

Đếm mỗi business operation một lần hoặc tạo trace span ở boundary rõ ràng là ví dụ điển hình của cross-cutting concern. Phải xác định đơn vị: request, operation, attempt hay dependency call.

Rủi ro: hai instrumenter cùng tăng counter, tạo hai semantic duplicate spans hoặc trộn thời gian invocation đồng bộ với thời gian completion bất đồng bộ trong một histogram. Có thể có nhiều span hợp lệ; chúng phải mô tả **phạm vi khác nhau**.

### 7.3. Phân quyền tại operation boundary

Một authorization advice có thể nhận context đã xác thực, resolve action và resource, hỏi policy evaluator và từ chối trước khi proceed. Cơ chế chung là **đọc context và enforce decision**; logic đánh giá quyền theo tenant/resource có thể ở policy engine hoặc domain boundary.

Rủi ro: bypass pointcut, tin context caller tự tạo, nhầm authenticated với authorized, bỏ qua TOCTOU và quyền bị thu hồi.

### 7.4. Transaction boundary

Một aspect có thể bắt đầu/kết thúc transaction theo contract use case. Nhưng transaction không chỉ là một before/after đơn giản: commit, rollback, exception semantics, nested invocation, transaction propagation và thread affinity có quy tắc riêng. Không gắn transaction vòng quanh mọi phương thức chỉ vì chúng gọi repository.

Rủi ro: transaction được giữ qua tác vụ async, commit sớm hơn nghiệp vụ cần thiết hoặc rollback theo lỗi không đúng policy.

### 7.5. Audit quyết định hoặc sự kiện đã xác nhận

Nếu nhiều operation cần chuẩn audit và có điểm commit/decision đáng tin, một aspect có thể hỗ trợ thêm metadata và ghi nhận đúng mốc. Nhưng **audit có tính bền vững** không được bảo đảm chỉ bằng một lệnh logger chạy trong advice; phải thiết kế trách nhiệm lưu trữ, tính nhất quán và failure semantics riêng.

### 7.6. Giới hạn tài nguyên hoặc rate limiting

Aspect có thể bảo vệ operation boundary bằng permit/budget theo policy, nhưng chỉ khi khóa giới hạn, vòng đời permit và cách giải phóng được xác định. Không giữ permit của lời gọi ngắn cho công việc kéo dài nếu chúng thuộc hai vòng đời khác nhau; một task dài cần cơ chế sở hữu permit theo task.

## 8. Những concern thường không nên giấu trong Aspect

**Định giá, tính thuế, chiết khấu:** đây là quyết định miền; thay đổi thường theo loại đơn hàng và luật nghiệp vụ, cần được biểu diễn tường minh trong domain service hoặc policy object.

**Tạo/chỉnh sửa trạng thái miền:** một advice tự thay đổi order status sau bất kỳ method tên save nào làm hành vi khó kiểm chứng và khó giữ invariant giao dịch.

**Validation nghiệp vụ đặc thù:** một số validation cấu trúc có thể là cơ chế chung, nhưng quy tắc “chỉ duyệt đơn khi tồn kho và hạn mức thỏa” thường phải gắn với use case/domain. Không đồng nhất validation mọi DTO với authorization mọi operation.

**Chọn luồng xử lý:** nếu aspect quyết định gọi nhà cung cấp A hay B, rẽ nhánh sản phẩm theo loại khách hoặc tính toán cách hoàn tiền, luồng nghiệp vụ bị giấu; strategy/policy object dễ review hơn.

**Mặc định retry mọi thứ:** retry có thể lặp side effect, gây double charge hoặc gửi notification nhiều lần. Chỉ khi policy có hợp đồng idempotency, loại lỗi retry được và giới hạn attempts rõ mới xem xét đưa mechanism retry vào một aspect.

**Caching mọi getter:** cache key, TTL, invalidation, tenant isolation, tính nhất quán và dữ liệu nhạy cảm khác nhau giữa operations. Một điểm chung về mechanism chưa đủ chứng minh chính sách cache đồng nhất.

Một phép thử đơn giản: **nếu bỏ aspect đi, business rule có bị mất nghĩa hay chỉ mất một cơ chế hạ tầng?** Nếu mất logic quyết định kết quả nghiệp vụ, concern có thể nằm sai nơi. Tuy nhiên security enforcement là trường hợp đặc biệt: được phép thay đổi quyền thực thi theo chính sách, nên câu hỏi đúng hơn là “policy bảo mật có được mô tả tường minh và bao phủ đầy đủ không?”

## 9. Thiết kế pointcut như một hợp đồng kiến trúc

Pointcut bền vững phải gắn với **ý nghĩa của boundary**. Những lựa chọn có thể dùng:

1. **Annotation tường minh:** một method hoặc contract được đánh dấu là operation cần quan sát. Người review nhìn thấy ý định.
2. **Interface nghiệp vụ có phạm vi rõ:** một tập operation public được chỉ định thuộc nhóm được bảo vệ.
3. **Contract metadata ngoài mã:** dùng registry/config được kiểm soát, có kiểm thử tương ứng.
4. **Cấu trúc module ổn định:** package/class pointcut chỉ nên dùng khi kiến trúc thực sự bảo đảm tên/phạm vi bền.

Ví dụ hai pointcut giả định:

~~~text
Dễ vỡ: mọi method public trong package *service*
Ổn định hơn: các method được chỉ định là BusinessOperation
~~~

Trong thực tế, annotation đặt trên interface có thể không được một công cụ nhìn thấy trên implementation nếu nó không tìm metadata phù hợp. Proxy có thể không chặn self-invocation; weaving theo execution join point có cách bao phủ khác. Cần kiểm chứng **đường gọi runtime**, không chỉ điểm cắt trên giấy.

Không nên yêu cầu mọi team nhớ thêm annotation bằng thói quen. Hãy có coverage test hoặc checklist review đối chiếu tập protected operations với tập được instrument. Một protected method mới bị bỏ sót có thể nguy hiểm hơn việc giữ một ít code tường minh.

## 10. Advice phải có contract rất rõ

### Before

Phù hợp kiểm tra precondition, authorization trước side effect hoặc thiết lập một scope đã định nghĩa. Nhưng advice phải tránh tác dụng phụ không dự kiến; nếu nó từ chối, cần exception/kết quả theo hợp đồng.

### After returning

Quan sát method đã trả về bình thường; **không đồng nghĩa operation bất đồng bộ đã thành công**. Một method trả Result.Failure vẫn là return bình thường.

### After throwing

Quan sát exception xảy ra ở invocation được chặn; có thể không nhìn thấy lỗi task nền nếu lỗi xảy ra sau khi method return.

### Around

Cho phép đo thời gian, quản lý scope hoặc điều phối proceed. Đây là điểm mạnh nhưng cũng rủi ro: advice có thể vô tình gọi proceed hai lần, thay đổi exception, giữ lock quá lâu, làm nghẽn lời gọi hoặc tác động đến return type.

Một contract nên viết rõ:

~~~text
Một lời gọi được áp dụng advice:
  - cách chọn join point và loại trừ
  - metadata bắt buộc
  - số lần proceed được phép (thường đúng một)
  - mốc bắt đầu/kết thúc được đo
  - hành vi với return bình thường, lỗi đồng bộ, async failure
  - cách bảo toàn result/exception
  - thời điểm tạo và đóng scope
  - overhead và failure policy của observer
~~~

Nếu không thể viết contract ngắn gọn mà phải dựa vào điều kiện của từng tính năng mới, hãy xem lại mức độ ổn định của concern.

## 11. Java 17: minh họa tách một concern có hợp đồng ổn định

Ví dụ dùng JDK dynamic proxy để minh họa AOP độc lập với thư viện cụ thể. Chúng ta muốn ghi **bắt đầu và kết quả cho mỗi operation được đánh dấu**, không log các helper hoặc mọi method. Chính sách logging ổn định và có thể dùng cho nhiều operation. Trong khi đó, quyết định chuẩn hóa mã đơn hàng vẫn nằm trong implementation nghiệp vụ.

Tạo tệp **StableAspectDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class StableAspectDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface BusinessOperation {
        String value();
    }

    interface OrderService {
        @BusinessOperation("order.submit")
        String submit(String orderId);

        String status(String orderId);
    }

    static final class RealOrderService implements OrderService {
        @Override
        public String submit(String orderId) {
            return "SUBMITTED:" + normalize(orderId);
        }

        @Override
        public String status(String orderId) {
            return "PENDING";
        }

        private String normalize(String orderId) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("missing order id");
            }
            return orderId.trim();
        }
    }

    static final class OperationLogAdvice implements InvocationHandler {
        private final OrderService target;

        OperationLogAdvice(OrderService target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            BusinessOperation mark =
                    method.getAnnotation(BusinessOperation.class);

            if (mark == null) {
                return invokeTarget(method, args);
            }

            String name = mark.value();
            System.out.println("BEGIN " + name);
            try {
                Object result = invokeTarget(method, args);
                System.out.println("END " + name + " outcome=success");
                return result;
            } catch (Throwable failure) {
                System.out.println("END " + name
                        + " outcome=failure type="
                        + failure.getClass().getSimpleName());
                throw failure;
            }
        }

        private Object invokeTarget(Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapper) {
                throw wrapper.getCause();
            }
        }
    }

    public static void main(String[] args) {
        OrderService service = (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[] { OrderService.class },
                new OperationLogAdvice(new RealOrderService()));

        System.out.println("status=" + service.status("O-9"));
        System.out.println("result=" + service.submit(" O-9 "));

        try {
            service.submit(" ");
        } catch (IllegalArgumentException error) {
            System.out.println("caller_error="
                    + error.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac StableAspectDemo.java
java StableAspectDemo
~~~

Stdout xác định:

~~~text
status=PENDING
BEGIN order.submit
END order.submit outcome=success
result=SUBMITTED:O-9
BEGIN order.submit
END order.submit outcome=failure type=IllegalArgumentException
caller_error=IllegalArgumentException
~~~

**Giải thích:**

- Metadata BusinessOperation là **dấu hiệu lựa chọn join point**; chỉ method submit được instrument theo chính sách log. Method status không phát log, và normalize chỉ làm nhiệm vụ miền.
- Advice gọi target **một lần** rồi trả lại kết quả nguyên vẹn; trường hợp lỗi thì ném lại nguyên nhân gốc qua InvocationTargetException để không đổi contract ngoại lệ.
- Hành vi logging không cần biết cách normalize order ID. Nếu thay thuật toán normalize hoặc tách thành nhiều helper, điểm phát log tại operation boundary vẫn đúng.
- Chính sách ở ví dụ chỉ cho rằng “method trả bình thường” là outcome success của invocation đồng bộ. Nếu trả Result chứa failure hoặc CompletionStage chưa hoàn tất, cần thiết kế outcome observer phù hợp; không dùng nguyên xi advice này để báo hoàn thành nghiệp vụ bất đồng bộ.
- System.out được dùng để có stdout kiểm chứng. Trong sản phẩm thật nên dùng structured logger có schema ổn định, redaction, level, ngân sách log và xử lý lỗi observer.
- Proxy chỉ chặn lời gọi qua interface proxy. Gọi trực tiếp RealOrderService không qua advice; các cơ chế weaving khác có tập join point khác. Đây là mô hình giảng dạy, không phải bảo đảm interception toàn cục.

Điều làm concern logging này đủ thích hợp cho aspect không phải vì nó chỉ chiếm vài dòng, mà vì **ý nghĩa, đầu vào cần thiết, điểm cắt và kết quả quan sát đều ổn định khi miền thay đổi**.

## 12. So sánh phương án trước và sau

**Trước khi tách**: mỗi use case tự viết các dòng bắt đầu, kết thúc, duration và phân loại lỗi. Nếu một nhóm quên finally, log có thể thiếu outcome; nếu thêm một trường correlation, phải chỉnh ở nhiều nơi.

**Sau khi tách một concern ổn định**: phương thức nghiệp vụ khai báo rõ boundary, một advice chung phát event theo schema. Thêm operation có thể chỉ cần khai báo metadata và test coverage. Ngược lại, caller phải hiểu rằng advice sẽ chạy; cần tài liệu thiết kế và quan sát được runtime weaving/proxy.

| Thuộc tính | Viết ở từng use case | Aspect ở boundary rõ |
| --- | --- | --- |
| Vị trí chính sách logging | Phân tán | Tập trung |
| Thêm metadata thống nhất | Sửa nhiều chỗ | Sửa một advice/schema |
| Hiện rõ hành vi ngay trong method | Rõ | Cần biết pointcut |
| Nguy cơ thiếu logging | Từng method có thể quên | Có thể bỏ sót join point |
| Bảo toàn kết quả nghiệp vụ | Dễ nhìn trực tiếp | Cần kiểm thử advice |
| Thay đổi thuật toán miền | Ít ảnh hưởng cơ chế | Không cần đổi pointcut nếu boundary ổn định |
| Khả năng chẩn đoán | Phụ thuộc từng tác giả | Có schema đồng nhất |
| Chi phí abstraction | Thấp khi ít điểm | Đáng giá khi nhiều điểm phù hợp |

AOP không loại mọi sự phân tán. Một số thông tin chuyên biệt như lỗi nghiệp vụ có mã riêng vẫn cần được nghiệp vụ/policy cung cấp. Đừng biến một aspect chung thành nơi chứa điều kiện của từng use case chỉ để giữ tất cả logic trong một lớp.

## 13. Khi cơ chế và chính sách cần tách đôi

Một mô hình bền vững cho concern có nhiều biến thể là:

~~~text
Business operation
   -> AOP advice tại boundary
      -> cung cấp trusted context, action, resource
      -> gọi Policy / Strategy được chỉ định
      -> áp dụng quyết định chung
   -> proceed hoặc từ chối
~~~

Ví dụ:

- Aspect biết **khi nào** kiểm tra authorization; policy engine biết **ai được làm gì trên tài nguyên nào**.
- Aspect biết **khi nào** bắt đầu/đóng metric; metadata nghiệp vụ biết tên operation/outcome code.
- Aspect biết **khi nào** bắt đầu transaction; transaction policy chỉ ra read-only, propagation hoặc rollback conditions.
- Aspect biết **khi nào** xin rate-limit permit; rate-limit policy biết key/quota/time window và vòng đời permit.

Tách như vậy giúp chính sách có thể thay đổi mà không ép pointcut thành một ma trận if/else theo tên class. Tuy nhiên, không tách bằng mọi giá: khi chỉ một operation có nhu cầu rất riêng, một API tường minh dễ hiểu và ít rủi ro hơn.

## 14. Thứ tự nhiều Aspect và tác động đến quyết định

Một operation có thể đồng thời chịu nhiều concern:

~~~text
TracingAdvice
  -> AuthorizationAdvice
     -> RetryAdvice
        -> TransactionAdvice
           -> businessOperation
~~~

Chỉ cần đổi thứ tự, hành vi quan sát đã có thể khác:

- **Authorization ngoài retry:** kiểm tra quyền một lần trước mọi attempt; cần xem quyền có thể hết hạn giữa các lần thử không.
- **Authorization trong retry:** kiểm tra lại mỗi attempt; hữu ích trong một số hoàn cảnh nhưng có chi phí và failure semantics khác.
- **Timer ngoài retry:** duration bao gồm các attempt và thời gian chờ giữa chúng.
- **Timer trong retry:** tạo một mẫu mỗi attempt, phải đặt tên attempt duration.
- **Transaction ngoài retry:** có thể giữ cùng transaction trong nhiều attempts và khó rollback đúng, trong khi transaction bên trong có scope khác.
- **Tracing ngoài/in giữa:** span cha và child spans có thể mang phạm vi khác nhau; đừng phát semantic duplicate spans do instrument hai tầng cùng một công việc.

**Một concern dù ổn định một mình cũng có thể không còn ổn định khi kết hợp với concern khác.** Vì thế quyết định tách thành aspect phải xét luật composition: thứ tự, nesting, exception propagation, context propagation và idempotency.

Với retry, phải xác minh nghiệp vụ có chịu lặp hay không. Không chọn retry aspect rộng chỉ vì “xử lý lỗi mạng” xuất hiện ở nhiều method; một operation chuyển tiền không thể bị gọi lại tùy tiện chỉ dựa trên exception type.

## 15. Vòng đời đồng bộ và bất đồng bộ

Một around advice chạy quanh phương thức đồng bộ thường có thể xác định thời điểm vào, return, throw. Nhưng khi một phương thức chỉ gửi công việc đi và trả handle, **invocation** kết thúc trước **task**.

~~~text
Caller / Aspect                  Worker
--------------                   ---------------------
before proceed()
proceed() -> trả CompletionStage
after returning
caller tiếp tục                 task chạy
                                task thất bại/thành công
                                completion callback
~~~

Đối với concern logging/metric “thời gian hoàn tất operation”, cơ chế chung cần hiểu loại async API. Một advice sau proceed không thể tự khẳng định task đã thành công. Có thể cần quan sát CompletionStage, Future theo hợp đồng, callback lifecycle, job completion hoặc subscription. Với Publisher lười, việc tạo publisher không đồng nghĩa có subscription; không tự subscribe để ép đo.

Khi gắn callback, giữ những quy tắc:

- Callback có thể chạy ở thread khác hoặc chạy ngay lúc đăng ký nếu stage đã xong.
- Không giữ lock, transaction hay scope ngắn của invocation tới lúc công việc dài hoàn tất nếu resource không có hợp đồng như vậy.
- Không cho observer tự ném exception làm thay đổi kết quả của dependent stage ngoài chủ đích.
- Không tăng business outcome ở cả method return và completion.
- Timeout hoặc cancel của handle chưa bảo đảm task đã dừng và tài nguyên nền đã cleanup.

**Nếu concern không có cùng vòng đời giữa các điểm áp dụng, nó có thể cần nhiều adapter rõ ràng hơn là một aspect “tổng quát cho mọi method”.**

## 16. Scope, thread và các rủi ro ẩn

Một số aspect tạo scope theo thread để gắn tracing context, security context hoặc transaction. Với tác vụ đồng bộ, try/finally có thể bảo đảm cleanup; nhưng thread pool và callbacks làm scope phức tạp hơn.

Các rủi ro:

- ThreadLocal còn dữ liệu sau lời gọi, khiến request sau trên cùng thread dùng nhầm context.
- Lời gọi lồng nhau remove context nhưng không restore parent.
- Callback ở thread khác cố đóng scope có thread affinity của caller.
- Advice giữ connection hoặc permit đến lúc async completion dù task không cần giữ nó.
- Context của caller được truyền sang task dài hơn hiệu lực của token/ủy quyền.

Giải pháp thường là gắn scope với đúng logical operation, truyền snapshot tối thiểu và bất biến tới worker, tạo worker scope riêng và cleanup ở chính worker. Với security, context propagation không tự xác minh quyền; cần nguồn context tin cậy và quy tắc reauthorization/expiry phù hợp.

Một concern cần phức tạp hóa lifecycle như vậy mới vận hành đúng có thể vẫn đáng tách, nhưng chi phí kiến trúc phải được nhận diện trước.

## 17. Tính dễ kiểm thử là tiêu chí lựa chọn

Một aspect phù hợp nên có bộ kiểm thử tập trung để giữ contract chung:

| Tình huống | Kỳ vọng |
| --- | --- |
| Method thuộc boundary hợp lệ | Advice chạy đúng số lần đã quy định |
| Method helper ngoài pointcut | Không bị áp dụng ngoài chủ đích |
| Method return bình thường | Kết quả nguyên vẹn, event theo contract |
| Method ném lỗi | Lỗi gốc được lan truyền, cleanup đúng |
| Nested aspect | Không đo trùng, thứ tự có thể giải thích |
| Retry | Operation và attempt được phân biệt |
| Direct target/self-invocation | Không có bypass ngoài ý muốn |
| Method mới/overload mới | Coverage được review và kiểm thử |
| Async completion | Mốc return khác mốc hoàn tất được giữ đúng |
| Scope nested / thread reuse | Context được restore và remove |
| Observer/logger gặp lỗi | Chính sách failure rõ, không che lỗi nghiệp vụ |
| Nhiều requests đồng thời | Không chia sẻ mutable state làm lẫn outcome |
| Đầu vào nhạy cảm | Không log token, credential hoặc payload nguyên bản |

Nên kiểm thử **pointcut mapping ở runtime** và một vài đường gọi tích hợp, không chỉ unit test trực tiếp advice. Một advice unit test có thể pass dù proxy không được lắp vào hệ thống hoặc một protected method bị gọi trực tiếp.

Kiểm thử số lần **proceed** đặc biệt quan trọng với around advice. Khi một lời gọi đủ điều kiện, target thường chạy đúng một lần; khi bị từ chối truy cập trước side effect, target phải chạy 0 lần. Với policy retry chủ ý, nhiều lần chạy phải được ghi nhận theo attempt và có bảo đảm idempotency.

## 18. Khả năng quan sát chính cơ chế AOP

Aspect che bớt mã lặp nhưng cũng tạo **hành vi không hiện trên thân method**. Do đó cần cơ chế giúp người đọc biết:

- Một operation đang chịu những aspect nào?
- Pointcut có chọn đúng những join point dự kiến không?
- Advice chạy theo thứ tự nào?
- Vì sao một lời gọi bị DENY, RETRY, ROLLBACK hoặc bị ghi metric?
- Có bao nhiêu nguồn phát cùng signal?
- Khi một aspect bị lỗi, result/exception nghiệp vụ thay đổi ra sao?

Nên có tài liệu ngắn về metadata, ownership của event/metric, biểu đồ invocation chain và kết quả kiểm thử hiệu lực. Tránh log mọi lần chặn để “debug aspect” trong môi trường production: dễ tạo vòng lặp logging, lộ dữ liệu và thêm overhead. Dùng diagnostic logging có kiểm soát, giới hạn phạm vi và không chứa bí mật.

## 19. Chi phí hiệu năng, coupling và tiến hóa

AOP có thể giảm số dòng lặp nhưng không xóa chi phí:

1. Proxy/interceptor/weaving có overhead gọi method; có thể tạo allocation cho metadata, timer, closure.
2. Mỗi advice có thể ghi log, metric, trace và gửi exporter, làm tăng I/O và áp lực GC.
3. Pointcut rộng làm instrument nhiều helper không cần thiết.
4. Cơ chế lấy tham số bằng reflection hoặc resolve resource có thể đắt hơn bản thân nghiệp vụ ngắn.
5. Multiple aspect layers làm stack trace và exception flow khó theo dõi.
6. Một aspect chứa danh sách special-case của nhiều domain trở thành điểm coupling cao: team A sửa chính sách làm team B đổi hành vi.

Khi đánh giá lợi ích, so sánh tổng chi phí sửa đổi, test coverage, readability, runtime overhead và xác suất bỏ sót policy. Với codebase nhỏ, một interceptor tường minh hoặc hàm wrapper đơn giản có thể rõ ràng hơn. Với nhiều module có boundary chung, aspect được kiểm soát tốt có thể giảm lỗi phân tán đáng kể.

Không nên dựa vào số dòng code giảm để xác định hiệu quả kiến trúc. Một aspect duy nhất nhưng thay đổi hành vi của 500 phương thức có thể có tác động lớn hơn 500 lời gọi API tường minh.

## 20. Các dấu hiệu một Aspect đang phình thành “God Aspect”

Cảnh báo khi:

- Pointcut khớp gần như mọi method trong hệ thống nhưng loại trừ bằng danh sách dài.
- Advice chứa nhiều switch/if dựa trên tên class, tên method, loại khách hàng hoặc trạng thái nghiệp vụ.
- Muốn thêm một use case mới phải sửa logic aspect thay vì chỉ khai báo contract/metadata.
- Aspect tự đọc và ghi nhiều aggregate/domain object.
- Một advice vừa retry, cache, authorize, log, transform result và cập nhật database.
- Thứ tự chạy giữa các aspect không còn ai giải thích được.
- Nhiều team cùng sửa một aspect khi policy nghiệp vụ từng team thay đổi.
- Thay tên helper gây lỗi không mong đợi ở metric, transaction hoặc authorization.
- Kiểm thử phải bật hàng loạt infrastructure mới xác minh một quy tắc nhỏ.

Cách cải thiện: thu hẹp pointcut, chia theo trách nhiệm thật, đưa policy đặc thù về domain service/strategy và giữ advice làm bộ điều phối cơ chế chung. Nếu chỉ một số nhỏ join point cần policy, có thể thay bằng API tường minh cho dễ đánh giá.

## 21. Quy trình quyết định và triển khai

**Bước 1 — Nêu vấn đề bằng kết quả mong muốn.** Ví dụ: “Mọi operation được bảo vệ phải kiểm tra quyền trước side effect và ghi một audit decision”, thay vì “muốn thêm aspect cho module”.

**Bước 2 — Kiểm kê các điểm áp dụng.** Có bao nhiêu use case thực sự cần concern? Chúng chia sẻ cùng contract? Có ngoại lệ mang tính nghiệp vụ không?

**Bước 3 — Viết hợp đồng chính sách.** Ghi rõ input, outcome, exception, lifecycle, giới hạn dữ liệu, tính bất biến và các điều kiện loại trừ.

**Bước 4 — Đánh giá bốn chiều ổn định.** Ngữ nghĩa, chính sách, pointcut và lifecycle có giữ nguyên qua một lần refactor hợp lý không?

**Bước 5 — Đối chiếu các phương án khác.** Service, policy object, decorator, explicit interceptor, wrapper function hoặc middleware có thể đáp ứng đơn giản hơn? Chọn phương án với coupling và nguy cơ ẩn ít nhất cho nhu cầu hiện tại.

**Bước 6 — Thiết kế pointcut tường minh.** Ưu tiên contract/annotation/registry có thể kiểm chứng, tránh quét toàn bộ helper.

**Bước 7 — Định nghĩa composition.** Khi có authorization, transaction, retry, cache, trace, xác định thứ tự và owner của kết quả/metric.

**Bước 8 — Kiểm thử và đo overhead.** Kịch bản thành công, lỗi, nested, async, concurrency, bypass, double counting và mã nghiệp vụ không bị thay đổi ngoài dự tính.

**Bước 9 — Triển khai theo phạm vi nhỏ trước.** Chọn vài operation đại diện, đo độ rõ và sự chính xác, rồi mở rộng nếu policy thật sự đủ chung.

**Bước 10 — Giữ governance.** Khi thêm operation, thay metadata hay sửa pointcut, yêu cầu review phạm vi ảnh hưởng và chạy lại kiểm thử coverage/behavior.

## 22. Câu hỏi tự đánh giá

1. Một concern áp dụng ở ba nơi khác nhau nhưng mỗi nơi có điều kiện nghiệp vụ riêng. Nên đưa tất cả vào một aspect không? **Thông thường không**: trước hết tách mechanism chung khỏi policy miền, sau đó đánh giá liệu còn có boundary policy ổn định để instrument không.
2. Thêm một method helper làm số log tăng gấp ba. Dấu hiệu gì? **Pointcut có thể quá rộng hoặc phụ thuộc cấu trúc mã thay vì operation contract**.
3. Hai timers đo cùng method, một timer ngoài retry và một timer trong retry. Có chắc đo trùng không? **Không** nếu chúng lần lượt đo operation và attempts, có tên/scope rõ. Nếu cùng phát một loại business-operation sample thì có nguy cơ trùng ngữ nghĩa.
4. Aspect đo duration của method trả CompletionStage. Đó có phải end-to-end duration không? **Không nhất thiết**: method có thể chỉ trả handle. Cần observer theo completion contract.
5. Một aspect kiểm tra role trước phương thức chuyển tiền, nhưng method được gọi trực tiếp không qua proxy. Có an toàn không? **Không**: enforcement có thể bị bypass; cần quản lý các đường gọi và bảo vệ invariant ở vị trí thích hợp.
6. Một chính sách logging ổn định nhưng nhiều use case cần ẩn dữ liệu khác nhau. Có nên bỏ AOP không? **Chưa chắc**: schema chung và allowlist metadata có thể tách biệt; không để aspect tự serialize toàn bộ arguments.

## 23. Checklist review thiết kế

- [ ] Concern thực sự xuyên nhiều boundary độc lập.
- [ ] Có contract chung ổn định, không phải tập hợp quy tắc nghiệp vụ rời rạc.
- [ ] Pointcut dựa vào ngữ nghĩa operation, có coverage test và exclusion rõ.
- [ ] Advice biết chính xác mốc invocation, completion, failure và cancellation.
- [ ] Kết quả và exception nghiệp vụ được bảo toàn ngoài thay đổi có chủ đích.
- [ ] Ownership metric/log/trace được phân định, không double count.
- [ ] Thứ tự aspect với transaction, retry, authorization, cache đã được kiểm tra.
- [ ] Cơ chế không cho phép bypass quyền hoặc transaction invariant quan trọng.
- [ ] Context/thread scope được cleanup đúng; async có propagation phù hợp.
- [ ] Concern không biến thành kho chứa if/else theo từng use case.
- [ ] Logging không lộ dữ liệu; cardinality metric được kiểm soát.
- [ ] Có thử nghiệm performance và behavior khi nhiều aspect cùng hoạt động.
- [ ] Có kế hoạch review pointcut và metadata khi chương trình tiến hóa.

**Kết luận:** một concern là ứng viên tốt cho aspect khi **quy tắc chung ổn định hơn những phương thức mà nó áp dụng**, điểm cắt diễn đạt được ý nghĩa nghiệp vụ hoặc hạ tầng, và advice có hợp đồng đủ rõ để giữ đúng hành vi. AOP hiệu quả nhất ở nơi nó **tập trung được chính sách chung** mà không che giấu các quyết định miền vốn cần xuất hiện công khai trong thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-002">Xác định nơi áp dụng và nơi tuyệt đối không được can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định nơi áp dụng và nơi tuyệt đối không được can thiệp trong kiến trúc AOP

## 1. Vì sao đây là quyết định kiến trúc quan trọng?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), một **aspect** gom một mối quan tâm cắt ngang nhiều thành phần, chẳng hạn đo thời gian, ghi log, tracing, kiểm tra quyền hoặc quản lý một chính sách thực thi. **Pointcut** chọn các **join point** mà **advice** được áp dụng. Vì vậy, thiết kế AOP không chỉ là viết logic trong advice; phần quan trọng hơn là xác định **chính xác tập điểm được phép can thiệp** và **tập điểm phải loại trừ**.

Can thiệp quá hẹp khiến một số đường gọi bỏ qua chính sách. Can thiệp quá rộng có thể làm chậm chương trình, lộ dữ liệu, tạo vòng lặp đệ quy trong logging, làm thay đổi ngoại lệ hoặc vô tình thực thi lại tác dụng phụ. Nghiêm trọng hơn, một lời khuyên chung kiểu “áp dụng cho mọi phương thức” có thể bao quanh chính công cụ mà advice cần gọi để hoạt động.

Câu hỏi kiến trúc cần trả lời trước khi viết pointcut:

1. **Chính sách nhằm bảo vệ hoặc quan sát điều gì?** Một nghiệp vụ, một yêu cầu, một giao dịch, một lần retry, một tài nguyên hay một callback?
2. **Join point nào đại diện đúng ranh giới đó?** Method call, method execution, constructor hay điểm khác tùy mô hình AOP?
3. **Có thể thay đổi kết quả không?** Advice chỉ quan sát, có thể từ chối, có thể gọi lại hành vi đích hay có thể thay đổi đầu ra?
4. **Những đối tượng nào tuyệt đối phải loại trừ khỏi pointcut đang xét?** Logger/exporter, công cụ đo chính advice, một số phương thức hạ tầng, code nhạy cảm hoặc phương thức có hợp đồng không chấp nhận việc can thiệp ấy.
5. **Làm cách nào chứng minh điểm đúng được bao phủ và điểm cấm không bị can thiệp?** Phải có test và quan sát cơ chế thực thi thực tế.

**Nguyên tắc:** không có danh sách “mọi AOP đều tuyệt đối cấm” độc lập với loại advice. Một điểm rất phù hợp cho tracing có thể không phù hợp cho retry; một thao tác nghiệp vụ thích hợp cho kiểm tra quyền vẫn có thể không được phép để advice tự sửa kết quả. Do đó, phải định nghĩa các *vùng cấm theo từng chính sách*. Một số bất biến về bảo mật và tính đúng đắn phải được bảo vệ tại ranh giới đáng tin cậy, bất kể AOP có hoạt động hay không.

## 2. Phân biệt nơi áp dụng, nơi không áp dụng và nơi chưa xác minh

Khi đánh giá một join point, nên phân thành ba trạng thái thiết kế:

| Trạng thái | Ý nghĩa | Hành động |
| --- | --- | --- |
| **ALLOW / áp dụng** | Có lý do rõ ràng để advice chạy; hành vi được phép thay đổi trong phạm vi hợp đồng | Đưa vào pointcut có chủ đích, có test |
| **DENY / loại trừ** | Advice được xét có thể gây đệ quy, sai ngữ nghĩa, rủi ro bảo mật hoặc effect không an toàn | Loại trừ tường minh và kiểm thử không bị intercept |
| **REVIEW / chưa kết luận** | Thiếu thông tin về kiểu join point, tác dụng phụ, transaction, async hoặc khả năng weave | Không mở rộng pointcut đến khi có bằng chứng |

Ví dụ:

- **Timing advice**: phù hợp với các entry point nghiệp vụ có thời lượng đáng quan tâm; có thể cần loại trừ chính bộ ghi metric để tránh recursion và đo lường nhiễu.
- **Retry advice**: chỉ áp dụng khi đã xác định lỗi retryable và effect được thiết kế an toàn khi lặp; phải loại trừ các thao tác không idempotent chưa có chống trùng.
- **Authorization advice**: phù hợp ở các ranh giới yêu cầu quyền; nhưng không được coi đó là rào chắn duy nhất nếu đường gọi có thể bypass interception.
- **Transaction advice**: phù hợp quanh một đơn vị công việc giao dịch; không tự bao phủ mọi tài nguyên ngoài giao dịch, và không nên mở transaction kéo dài qua khoảng chờ backoff một cách vô ý.
- **Result-recording advice**: phải đứng ở đúng ranh giới outcome; không ghi “committed” khi transaction chưa commit hoặc “async success” khi phương thức mới trả về future.

Quyết định ALLOW/DENY/REVIEW cần ghi **lý do và loại advice**, tránh một nhãn “không AOP” mơ hồ áp lên toàn bộ hệ thống.

## 3. Bắt đầu từ concern và đơn vị đo, không bắt đầu từ tên package

Một cách tiếp cận thiếu an toàn là chọn toàn bộ một package rồi áp cùng một advice cho mọi phương thức. Cách này dễ tạo phạm vi can thiệp ngoài dự kiến khi thêm lớp hoặc refactor.

Hãy định nghĩa mối quan tâm trước:

- **Quan sát yêu cầu:** ranh giới từ tiếp nhận đến trả kết quả; cần biết một request có thể qua nhiều hàm con và callback.
- **Quan sát nghiệp vụ:** ranh giới thao tác logic như đặt hàng hoặc duyệt tài liệu; một phương thức có thể chỉ khởi tạo tác vụ nên chưa đại diện completion.
- **Đếm truy cập dữ liệu:** chọn những operation đã được định nghĩa rõ; tránh mỗi getter nhỏ đều tạo một event.
- **Kiểm soát quyền:** chọn các entry point nhạy cảm và ranh giới thực thi effect; đảm bảo quyền dựa trên principal, action, resource đáng tin cậy.
- **Chính sách retry:** chọn toàn operation có thể lặp an toàn hoặc một bước có semantics lặp rõ ràng, không chỉ vì phương thức có thể ném exception.
- **Giao dịch:** chọn đơn vị cập nhật dữ liệu cần commit/rollback đồng bộ, với quy tắc propagation rõ ràng.

Một đơn vị nghiệp vụ thường có ranh giới quan sát tốt hơn các phương thức nhỏ như getX(), setY(), hashCode() hoặc toString(). Tuy nhiên, nếu cần instrument một nhóm hàm nhỏ phục vụ profiling chuyên sâu, đó vẫn có thể là quyết định có chủ đích và được kiểm soát hiệu năng.

## 4. Phân tích điểm can thiệp thật sự có sẵn

Mô hình AOP không đồng nhất. Không được xây quy tắc kiến trúc dựa trên giả định rằng mọi lời gọi Java đều có thể bị bắt.

### 4.1. Proxy-based interception

Một proxy nhận lời gọi rồi có thể chạy advice trước khi chuyển tiếp đến target:

~~~text
caller ---> proxy [advice] ---> target.method()
caller -----------------------> target.method()  (đường trực tiếp)
~~~

Nếu một đối tượng khác giữ tham chiếu tới **target gốc**, nó có thể gọi trực tiếp và không qua proxy. Một số hình thức **self-invocation** trên chính target cũng không quay lại proxy. Cơ chế proxy qua interface, subclass hoặc adapter có giới hạn khác nhau về method có thể can thiệp.

### 4.2. Weaving tại điểm thực thi hoặc điểm gọi

Một cơ chế weaving có thể ghép advice vào bytecode tại những join point đã chọn. Ví dụ, weaving tại **method execution** có thể bao phủ thực thi phương thức được chọn dù đường vào không qua proxy. Nhưng điều đó phụ thuộc mã nào được weave, classloader nào tải lớp, thời điểm weaving và loại join point mà công cụ hỗ trợ.

Phương thức final/private/static, constructor, method handle, native code và code nạp động cần được đánh giá theo **cơ chế cụ thể**. Không thể khẳng định tất cả loại điểm ấy luôn bị bỏ qua hoặc luôn được hỗ trợ trong mọi AOP.

### 4.3. Cấu hình và thứ tự cũng thuộc phạm vi

Một pointcut hợp lệ về cú pháp chưa bảo đảm runtime đã intercept:

- Có thể không match chữ ký, visibility, kiểu hoặc call site được mong đợi.
- Có thể chưa đăng ký aspect ở build/deployment đang chạy.
- Có thể có lớp không được weave, đối tượng được khởi tạo theo đường khác.
- Có thể advice ngoài cùng trả kết quả mà không gọi proceed(), nên advice bên trong không chạy.
- Có thể chương trình có đường vào khác tới cùng side effect.

**Phải kiểm tra đường gọi ở runtime**, không chỉ nhìn vào việc khai báo aspect.

## 5. Nơi thường phù hợp để áp dụng AOP

### 5.1. Entry point có ý nghĩa nghiệp vụ

Các phương thức ở biên một use case, chẳng hạn placeOrder(), approveDocument() hoặc calculateQuote(), thường thích hợp cho tracing, correlation ID, kiểm tra quyền, đo thời lượng và ghi kết quả.

Ưu điểm là có đơn vị quan sát rõ: một lượt operation, outcome và effect liên quan. Nếu một use case bao gồm nhiều bước bất đồng bộ, cần phân biệt lúc phương thức trả handle với lúc tác vụ thật sự hoàn tất.

### 5.2. Ranh giới truy cập tài nguyên

Có thể dùng aspect để thu thập số liệu truy cập hoặc áp chính sách nhất quán tại các lời gọi tới kho dữ liệu, dịch vụ từ xa, file hoặc hàng đợi. Nhưng phải biết sự kiện được đo là **attempt**, **giao dịch**, **request gửi đi** hay **kết quả đã xác nhận**.

Ví dụ, ghi log “database updated” tại thời điểm phương thức gọi SQL trả về có thể sai nếu transaction chưa commit. Nếu cần khẳng định dữ liệu đã cam kết, phải quan sát mốc commit thực.

### 5.3. Điểm kiểm soát tập trung có đường đi xác định

AOP hữu ích khi một nhóm hành vi phải dùng chung các chính sách như xác thực ngữ cảnh, logging cấu trúc, chuẩn hóa lỗi hoặc kiểm tra điều kiện kỹ thuật. Điều kiện là tập join point được quản lý chặt và không có đường bypass quan trọng.

Với chính sách bảo mật, điểm can thiệp có thể giúp giảm lặp, nhưng **nguồn tin cậy và cưỡng chế quyền** phải tồn tại tại ranh giới phù hợp với tài nguyên, không dựa duy nhất vào sự may mắn rằng lời gọi luôn qua proxy.

### 5.4. Các thao tác có hợp đồng retry/idempotency rõ

Retry advice phù hợp với một số thao tác đọc không tạo effect, hoặc thao tác ghi được thiết kế idempotent với khóa ổn định và đối soát kết quả. Nếu retry bao cả giao dịch, mỗi attempt thường cần ranh giới transaction mới theo chính sách của resource manager.

Phải xác định lỗi nào retryable, số attempts, backoff, deadline, cancellation, trạng thái commit chưa chắc chắn và mọi effect ngoài transaction.

## 6. Những vùng phải loại trừ khỏi một số loại advice

Bảng dưới đây là **ma trận theo loại chính sách**; “loại trừ” không có nghĩa cấm mọi hình thức quan sát trong mọi hoàn cảnh.

| Vùng/hoạt động | Advice rủi ro | Vì sao phải loại trừ hoặc xem xét cực kỳ cẩn thận? | Thay thế hoặc điều kiện cho phép |
| --- | --- | --- | --- |
| Chính logger, exporter, metric registry hoặc hàm publish telemetry của aspect | Log/tracing/metrics quá rộng | Advice gọi logger; logger lại match pointcut; gây vòng đệ quy, tràn stack hoặc khuếch đại event | Loại trừ hạ tầng observability, dùng guard chống reentrancy và kiểm thử |
| Hàm mà advice dùng để lấy quyền hoặc truy vấn policy | Authorization advice cùng đường | Guard cần policy evaluator; evaluator lại bị cùng guard chặn, có thể đệ quy hoặc bế tắc | Tách trusted policy engine khỏi pointcut hoặc định nghĩa chain rõ |
| Bộ tạo proxy, weaving, classloader, bootstrap lõi | Advice tổng quát | Hạ tầng cần hoạt động để advice tồn tại; instrumentation không kiểm soát có thể gây lỗi khởi tạo | Giới hạn rõ tập lớp hỗ trợ, kiểm tra vòng phụ thuộc và initialization |
| equals/hashCode/toString hoặc getter rất nhỏ | Retry, transaction, logging tần suất cao | Có thể được gọi vô số lần từ collection/logging; tăng overhead, recursion và làm đổi contract | Chỉ instrument có chủ đích khi có nhu cầu profiling/diagnostics cụ thể |
| Hàm gửi tiền, thanh toán, email hoặc phát event chưa chống trùng | Retry | Mỗi proceed() có thể lặp effect không rollback được | Chỉ retry sau khi có idempotency/deduplication và hiểu failure semantics |
| Method trả future/publisher nhưng advice chỉ bắt return đồng bộ | Transaction, timeout, recorder cuối | Return handle không đồng nghĩa hoàn thành logic; commit/ghi success có thể quá sớm | Dùng cơ chế theo dõi completion và lifecycle tài nguyên tương ứng |
| Hàm dọn dẹp, rollback, close tài nguyên | Retry hoặc observer gây throw | Lặp cleanup có thể không an toàn; lỗi mới có thể che lỗi gốc | Cơ chế cleanup chuyên trách, exception policy rõ |
| Phần cập nhật trạng thái bất biến nghiệp vụ | Advice biến đổi kết quả hoặc skip | Có thể bỏ qua validation/authorization và invariant cốt lõi | Đưa bất biến bắt buộc vào domain/resource boundary đáng tin |
| API có dữ liệu nhạy cảm | Logging đầu vào/đầu ra tự động | Có thể lộ token, thông tin cá nhân hoặc payload mật | Chỉ ghi trường allowlisted; redaction theo hợp đồng |
| Code nền chạy trước lúc cấu hình context sẵn sàng | Context/authorization phụ thuộc ThreadLocal | Thiếu principal hoặc mang context cũ | Khởi tạo scope đúng, xác định service principal rõ ràng |
| Thread pool, scheduler, callback/worker chung | Advice giữ state mutable/thread-bound | Có thể rò context giữa các request hoặc giải phóng tài nguyên sai thời điểm | Snapshot bất biến, bind/restore per-task, instrument đúng nơi execute |
| Native/external code không thuộc tập join point | Policy dựa hoàn toàn vào interception | Chính sách có thể không chạy | Dùng guard ở API/tài nguyên đáng tin hoặc adapter kiểm soát đường vào |

Một quy tắc có tính **bắt buộc** đối với kiến trúc là: *không được để những hành vi nhạy cảm dựa hoàn toàn vào một interceptor mà đường thực thi có thể bỏ qua*. Điều này khác với việc cấm tuyệt đối mọi advice tại chính hành vi đó.

## 7. Chống đệ quy và vòng phụ thuộc của instrumentation

Một lỗi điển hình:

~~~text
around anyMethod():
    log("enter")
    proceed()
    log("exit")
~~~

Nếu chính log() match pointcut “anyMethod”, chuỗi có thể trở thành:

~~~text
advice for business()
  -> log()
      -> advice for log()
          -> log()
              -> advice for log()
                  ...
~~~

Hậu quả có thể là StackOverflowError, treo ứng dụng, log storm hoặc tiêu thụ tài nguyên lớn. Tương tự nếu metric collector, exporter, tracer, cache hay authorization evaluator nằm trong cùng tập điểm bị instrument.

### 7.1. Ưu tiên loại trừ tường minh

Thiết kế pointcut có danh sách ALLOW theo hành vi đích cùng danh sách DENY đối với hạ tầng:

~~~text
selectedJoinPoints =
    businessOperationExecution
    AND NOT telemetryImplementation
    AND NOT policyEvaluatorInternals
    AND NOT instrumentationBootstrap
~~~

Đây là **biểu thức khái niệm**, không phải cú pháp cụ thể. Tên các khối cần được ánh xạ thành pointcut của mô hình AOP được chọn.

### 7.2. Reentrancy guard chỉ là lớp dự phòng

Có thể dùng cờ theo scope để tránh advice xử lý lặp trên chính thread hoặc chuỗi gọi mà nó tạo ra. Nhưng guard phải có finally để khôi phục trạng thái. Guard dựa trên ThreadLocal không tự bảo vệ callback trên thread khác; một số hệ thống cần context propagation riêng.

Một guard chống reentrancy có thể vô tình che event hợp lệ ở lời gọi lồng nhau. Vì vậy cần phân biệt **đệ quy không chủ đích của instrumentation** với **lời gọi nghiệp vụ lồng nhau hợp lệ**. Ưu tiên thiết kế pointcut đúng rồi dùng guard như một biện pháp phòng vệ bổ sung.

### 7.3. Tách luồng dữ liệu quan sát

Nếu mọi lần ghi metric đi ngược qua cùng pipeline được instrument, hệ thống có thể tự khuếch đại. Cần thiết kế telemetry path có phạm vi riêng, cơ chế giới hạn tải và chính sách xử lý lỗi observer. Dữ liệu quan sát không nên phụ thuộc vào chính dữ liệu quan sát đến mức tạo vòng không có điểm dừng.

## 8. Giữ nguyên hợp đồng của những phương thức không cho phép thay đổi

Một số method có hợp đồng rất nhạy cảm về tính thuần, tính quyết định hoặc không có side effect. Ví dụ:

- equals() phải thể hiện quan hệ tương đương thích hợp, nhất quán với hashCode().
- hashCode() nên ổn định khi trạng thái dùng để tính hash không đổi.
- toString() có thể được gọi từ công cụ log; lời gọi này không nên tạo effect nghiệp vụ nguy hiểm.
- Các getter hoặc phép toán số học thuần có thể được dùng trong thuật toán có giả định chặt về độ trễ và lỗi.

Một advice chỉ đo thời lượng một cách an toàn có thể chấp nhận được trong profiling, nhưng advice **retry**, **cache có biến đổi dữ liệu**, **ghi effect bắt buộc**, **thay thế giá trị** hoặc **ném lỗi mới** có thể phá hợp đồng phương thức.

Không có nghĩa tất cả những method này tuyệt đối không bao giờ được instrument: một profiler có thể cần chúng. Nhưng chính sách can thiệp phải được chứng minh không làm mất các bất biến về ý nghĩa, hiệu năng và tài nguyên của chúng.

### 8.1. Không dùng AOP để “vá” bất biến lõi một cách vô hình

Ví dụ một thao tác chuyển tiền phải bảo đảm số dư đủ, tài khoản hợp lệ và hạn mức không bị vượt. Nếu chỉ đặt kiểm tra trong advice, một đường gọi không qua proxy/weaving có thể bỏ qua tất cả.

Bất biến nghiệp vụ cần được bảo vệ ở ranh giới thực hiện effect hoặc trong giao dịch/resource layer thích hợp. AOP có thể bổ sung đo lường, trace và tái sử dụng một số kiểm tra, nhưng không được biến nó thành điểm thất bại đơn duy nhất cho tính đúng đắn cốt lõi.

## 9. Loại advice quyết định mức can thiệp được phép

### 9.1. Before advice

Before advice thường làm việc **trước** hành vi đích: kiểm tra tham số kỹ thuật, ghi sự kiện bắt đầu hoặc quyết định từ chối nếu chính sách cho phép. Rủi ro là side effect của before advice có thể xảy ra ngay cả khi hành vi đích thất bại hoặc bị từ chối ở lớp trong.

### 9.2. After returning advice

Thấy một lượt thực thi trả về bình thường; không tự có nghĩa nghiệp vụ đã commit hoặc future đã hoàn tất. Nếu cần đo “operation success”, phải xác định mốc completion thực tế.

### 9.3. After throwing advice

Có thể thấy ngoại lệ được ném đồng bộ từ join point theo ngữ nghĩa mô hình. Không tự thấy lỗi được gửi qua CompletionStage, callback hoặc terminal signal về sau.

### 9.4. After/finally advice

Phù hợp dọn scope ngắn hạn nếu nó bao quanh đúng lifecycle. Nhưng “finally của lời gọi tạo future” có thể chạy khi công việc nền vẫn tiếp tục. Không đóng tài nguyên mà worker còn sử dụng chỉ vì phương thức tạo future đã return.

### 9.5. Around advice

Có quyền lực lớn nhất: có thể không gọi proceed(), gọi một lần, gọi nhiều lần, thay exception, thay giá trị trả về hoặc thay đổi thời điểm thực thi. Đây là nơi cần kiểm soát nghiêm nhất.

Đối với các operation không được phép retry hoặc skip, hợp đồng của around advice phải được quy định rõ như:

~~~text
- proceed() đúng một lần
- không thay đổi danh tính/cấu trúc giá trị trả về
- không nuốt ngoại lệ của target
- observer failure không thay outcome của nghiệp vụ
- không kéo dài hoặc đóng sai lifecycle tài nguyên
~~~

Nếu có chính sách chủ đích cho phép khác đi, như cache/fallback/retry, phải chứng minh điều kiện tương ứng và ghi rõ trong thiết kế.

## 10. Tương tác giữa nhiều aspect: nơi được can thiệp thay đổi theo thứ tự

Các advice có thể lồng nhau:

~~~text
Outer(Middle(Inner(operation)))
~~~

Lớp ngoài đi vào trước và ra sau. Nếu một advice ngoài **không gọi proceed()**, toàn bộ advice phía trong có thể bị bỏ qua. Vì vậy, bảng pointcut đúng vẫn có thể không đủ nếu thứ tự áp dụng không phù hợp.

### 10.1. Kiểm tra quyền và cache

~~~text
Cache(Authorization(readPrivateData))
~~~

Khi cache hit, Cache có thể trả dữ liệu mà không chạy Authorization bên trong. Với dữ liệu riêng tư, đây là vùng phải review nghiêm ngặt. Cần đảm bảo guard bao phủ cả cache hit/miss, cache key gắn đúng context, và mọi tài nguyên được bảo vệ ở ranh giới tin cậy.

### 10.2. Retry và giao dịch

~~~text
Retry(Transaction(operation))
~~~

Nếu Transaction thực sự tạo giao dịch mới mỗi lần proceed(), mỗi attempt có scope commit/rollback riêng.

~~~text
Transaction(Retry(operation))
~~~

Mọi attempt có thể nằm trong cùng transaction; sau lỗi đầu, transaction có thể trở thành rollback-only hoặc aborted. Đặc biệt không nên để backoff dài làm giữ lock trong transaction mà không có chủ ý.

Một pointcut retry đúng **không được mặc nhiên áp vào mọi method ghi dữ liệu**. Nó phải đi cùng idempotency và trạng thái commit.

### 10.3. Recorder và lỗi telemetry

~~~text
Retry(RecordAttempt(Transaction(operation)))
~~~

Nếu RecordAttempt ném lỗi sau khi transaction đã commit, Retry có thể vô tình chạy lại nghiệp vụ chỉ vì recorder gặp sự cố. Với log/metric best effort, cần cô lập lỗi observer. Nếu recorder bắt buộc, phải định nghĩa một chính sách lỗi và transactional consistency riêng.

### 10.4. Fallback và kiểm tra quyền

~~~text
Fallback(Authorization(operation))
~~~

Nếu Fallback bắt luôn lỗi từ chối quyền và tạo một đường chạy thay thế có tác dụng phụ, nó có thể làm vô hiệu hóa chính sách. Fallback cần phân loại exception, không tự áp cho authorization errors.

## 11. Một ví dụ Java 17+: can thiệp có chọn lọc và loại trừ telemetry

Ví dụ dùng Java Dynamic Proxy để **mô phỏng một interceptor áp dụng có chủ đích**. Chỉ phương thức được gắn annotation @Observed trên interface mới được advice quan sát. Hai phương thức health() và emitMetric() không thuộc pointcut; đặc biệt emitMetric() phải được loại khỏi advice ghi metric để tránh instrumentation tự gọi lại chính nó.

Đây là ví dụ về **phạm vi interception**, không phải lời khuyên biến annotation thành quy tắc bảo mật tự động hay một ví dụ bytecode weaving tổng quát.

Tạo file **SelectiveAspectDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class SelectiveAspectDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface Operations {
        @Observed
        String execute(String input);

        String health();

        void emitMetric(String event);
    }

    static final class RealOperations implements Operations {
        @Override
        public String execute(String input) {
            System.out.println("business execute " + input);
            return "done-" + input;
        }

        @Override
        public String health() {
            System.out.println("business health");
            return "UP";
        }

        @Override
        public void emitMetric(String event) {
            System.out.println("metric " + event);
        }
    }

    static Object invokeTarget(
            Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public static void main(String[] args) {
        RealOperations target = new RealOperations();

        Operations proxy = (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[]{Operations.class},
                (instance, method, arguments) -> {
                    if (!method.isAnnotationPresent(Observed.class)) {
                        return invokeTarget(target, method, arguments);
                    }

                    System.out.println("advice before execute");
                    try {
                        Object result = invokeTarget(
                                target, method, arguments);
                        System.out.println("advice after execute");

                        // emitMetric KHÔNG thuộc tập method được quan sát.
                        // Gọi trực tiếp target giúp demo tránh vòng proxy.
                        try {
                            target.emitMetric("execute.success");
                        } catch (RuntimeException ignored) {
                            // Telemetry best effort trong demo.
                        }
                        return result;
                    } catch (Throwable error) {
                        System.out.println("advice observed failure");
                        throw error;
                    }
                });

        System.out.println("caller=" + proxy.execute("A"));
        System.out.println("health=" + proxy.health());
        proxy.emitMetric("manual");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac SelectiveAspectDemo.java
java SelectiveAspectDemo
~~~

Stdout chính xác:

~~~text
advice before execute
business execute A
advice after execute
metric execute.success
caller=done-A
business health
health=UP
metric manual
~~~

Phân tích:

1. execute() có @Observed trong interface; InvocationHandler chạy advice, sau đó chuyển lời gọi tới target.
2. health() không match, vì vậy không có dòng “advice before/after” cho lời gọi health.
3. emitMetric() cũng không match. Dòng “metric manual” được in mà không kích hoạt advice.
4. Advice có thể phát metric mà không tự lặp vô hạn qua chính pointcut của nó.
5. Demo giữ nguyên giá trị trả về “done-A” và chỉ quan sát, không retry, không cache, không thay đổi kết quả.

**Giới hạn:** Java Dynamic Proxy của ví dụ là một dạng interception qua interface; lời gọi trực tiếp target không đi qua proxy. Trong production, không nên tùy tiện truy cập target thô ở các đường nghiệp vụ nhạy cảm. Ví dụ gọi target.emitMetric() trực tiếp để minh họa vùng telemetry được cô lập, không phải mẫu cho phép bỏ kiểm soát quyền.

Ví dụ cũng cố ý chỉ in log/metric đơn giản. Một hệ thống đo lường thật cần thêm giới hạn dữ liệu, quản lý lỗi ghi log, xử lý cạnh tranh, context và thiết kế quan sát bất đồng bộ.

## 12. Xác định vùng cấm bằng chính sách pointcut

Một pointcut nên có **điều kiện dương** xác định công việc cần bao phủ và **điều kiện âm** dành cho các phần đã biết phải loại.

Mẫu giả mã độc lập công cụ:

~~~text
observedBusinessOperation(joinPoint) =
    matchesApprovedBusinessBoundary(joinPoint)
    AND NOT matchesTelemetryInternals(joinPoint)
    AND NOT matchesAdviceInfrastructure(joinPoint)
    AND NOT matchesBootstrapOrLifecycleInternals(joinPoint)
    AND NOT isExplicitlyExcluded(joinPoint)
~~~

Tùy loại advice, còn có điều kiện:

~~~text
retryableOperation(joinPoint) =
    explicitlyRetryApproved(joinPoint)
    AND hasDefinedRetryableFailurePolicy(joinPoint)
    AND hasSafeRepeatedEffectContract(joinPoint)
    AND NOT isCleanupOrCompensation(joinPoint)
~~~

~~~text
authorizationBoundary(joinPoint) =
    handlesProtectedActionAndResource(joinPoint)
    AND canResolveTrustedPrincipal(joinPoint)
    AND canDenyBeforeSensitiveEffect(joinPoint)
~~~

Các biểu thức trên chỉ trình bày **ý tưởng policy**, không thay thế việc xác minh ngữ nghĩa pointcut trong công cụ đang dùng. Đặc biệt, điều kiện “has safe effect contract” không phải điều mà một engine AOP bất kỳ tự nhận biết được; nó phải được chứng minh từ thiết kế và kiểm thử.

### 12.1. Allowlist và denylist

**Allowlist** là danh sách ranh giới được phê duyệt: tên operation, annotation có nghĩa rõ ràng, package/module ổn định hoặc tập signature đã kiểm toán.

**Denylist** loại trừ các vùng nguy hiểm: instrumenter, exporter, phương thức lifecycle và những loại thao tác mà advice tương ứng không được đụng tới.

Hai lớp nên bổ trợ nhau: allowlist thu hẹp phạm vi theo mục đích; denylist phòng ngừa các vùng chắc chắn không an toàn. Chỉ dựa vào denylist trên pointcut “mọi method” dễ bỏ sót những thành phần mới chưa biết; chỉ dựa vào allowlist nhưng không xác minh proxy/weaving có thể bỏ qua những đường cần được bảo vệ.

### 12.2. Quyền thay đổi danh sách áp dụng

Với aspect có thể làm thay đổi hành vi (authorization, retry, transaction, fallback), việc thêm một method vào allowlist không phải thay đổi trang trí. Cần review:

- Effect và mức độ nhạy cảm.
- Phạm vi transaction và async.
- Loại lỗi, giá trị trả về.
- Rủi ro lặp, skip hoặc can thiệp sai lifecycle.
- Test chứng minh pointcut match đúng method và không match vùng cấm.

Tài liệu kiến trúc nên chỉ rõ **owner của policy** và cách đánh giá thay đổi đó.

## 13. Bảo mật: nơi không được chỉ dựa vào advice

Với ranh giới truy cập nhạy cảm, câu “tuyệt đối không được can thiệp” có hai nghĩa khác nhau:

1. **Không được đưa vào một advice nguy hiểm**, như retry không idempotent hoặc log payload bí mật.
2. **Không được để một thao tác nhạy cảm chỉ phụ thuộc vào advice để cưỡng chế quyền**, khi có khả năng đường gọi bỏ qua interceptor.

Một phép kiểm tra quyền đáng tin cậy cần thông tin về **principal**, **action**, **resource** và **ngữ cảnh** đã được xác minh. Nếu một worker, job, callback hoặc caller khác có thể gọi thẳng target, việc kiểm tra ở proxy bên ngoài không đủ.

Những điểm cần xét:

- Self-invocation không qua proxy trong một số mô hình.
- Caller giữ target gốc hoặc tự tạo object không có interceptor.
- Pointcut chỉ match interface nhưng đường gọi thực tế dùng method ngoài interface.
- Method không có khả năng override dưới cơ chế subclass proxy.
- Code không được weave hoặc không được instrument tại thời điểm chạy.
- Cache hoặc fallback trả kết quả trước khi guard bên trong có cơ hội chạy.
- Background worker chạy với context thiếu danh tính hoặc dùng principal đã lỗi thời.

Không thể tuyên bố một hệ thống an toàn chỉ vì “annotation đã có”. Hãy kiểm tra điều kiện quyền tại ranh giới có khả năng ngăn **effect thực sự**, bổ sung kiểm soát ở tài nguyên khi thích hợp, và kiểm thử các đường vào khác nhau.

### Dữ liệu nhạy cảm và log

Một advice ghi đối số/tất cả giá trị trả về có thể thu thập mật khẩu, secret, token, dữ liệu sức khỏe, thông tin thanh toán hoặc dữ liệu cá nhân. Do vậy cần policy dữ liệu tách khỏi pointcut:

- Chỉ ghi trường được phép bằng allowlist.
- Loại bỏ hoặc che thông tin nhạy cảm theo hợp đồng.
- Hạn chế kích thước, retention và phạm vi chia sẻ.
- Kiểm tra lỗi format/toString có thể tiết lộ dữ liệu hoặc sinh effect.
- Không ghi mặc định toàn bộ payload chỉ vì aspect có thể lấy nó.

## 14. Ngữ cảnh, thread và bất đồng bộ quyết định nơi kết thúc advice

Khi method trả về một Future hoặc CompletionStage, around advice thường hoàn tất lượt **gọi phương thức** khi nhận handle. Tác vụ logic có thể kết thúc trên thread khác muộn hơn.

~~~text
t0 advice before
t1 method tạo future
t2 method trả future
t3 advice after return
...
t4 future complete/fail/cancel
~~~

Nếu mục tiêu là đo thời gian **invocation**, t0–t3 có thể đúng. Nếu mục tiêu là đo **operation end-to-end**, việc ghi success tại t3 là quá sớm.

Một số rủi ro:

- Transaction bị đóng trong finally ở t3 nhưng worker còn đang dùng tài nguyên đó.
- ThreadLocal chứa correlation/principal bị xóa trước khi task capture context.
- Không khôi phục ThreadLocal ở worker sau callback khiến tác vụ sau dùng context cũ.
- Timeout chỉ dừng chờ, nhưng task vẫn đang chạy và có thể tạo effect.
- Publisher có nhiều subscription; mỗi subscription có ranh giới khác nhau, không có một completion duy nhất cho mọi người dùng.

**Quyết định áp dụng:** instrument ở đúng ranh giới thực thi phù hợp (ví dụ nơi worker thực sự chạy) hoặc theo dõi completion qua hợp đồng async của API. Không kéo dài thread-bound resource tùy ý qua callback chỉ để làm cho một advice “bao đủ” thao tác.

## 15. Tác dụng phụ, retry và transaction: vùng không được phép lặp mù

Nếu advice retry có quyền gọi proceed() nhiều lần, mọi effect trong phạm vi ấy phải được kiểm tra:

| Tác dụng phụ | Nếu proceed() chạy nhiều lần | Ranh giới an toàn cần có |
| --- | --- | --- |
| Đọc dữ liệu thuần | Chủ yếu tăng tải, có thể đọc phiên bản khác | Deadline, retry budget, consistency phù hợp |
| Ghi bản ghi database | Có thể tạo bản ghi trùng, tăng counter nhiều lần | Transaction và idempotency/unique key |
| Chuyển tiền | Có thể thu/chuyển nhiều lần | Idempotency key bền, reconciliation |
| Gửi email | Có thể gửi nhiều bản | Outbox, event ID, deduplication |
| Công bố message | Có thể phát trùng hoặc sai thứ tự | Outbox/consumer idempotency, sequencing |
| Tạo audit event | Có thể đếm trùng hoặc mất bản ghi | Cấp độ audit rõ, độ bền và khóa chống trùng |
| Cleanup/compensation | Có thể hoàn tác hai lần hoặc phá trạng thái | Hợp đồng idempotent riêng, chính sách lỗi cụ thể |

Một transaction database không tự rollback email, thanh toán ngoài hay message đã rời tiến trình. Nếu commit trả lỗi không chắc chắn, retry không có idempotency có thể gây effect trùng ngay cả khi attempt trước đã commit.

Không đưa method có effect nguy hiểm vào pointcut retry vì thấy nó dùng cùng annotation hoặc cùng package. Chỉ mở rộng khi operation đã có hợp đồng chống lặp rõ ràng và được kiểm thử bằng tình huống crash, timeout, commit unknown.

## 16. Hiệu năng và độ tin cậy: tránh can thiệp mọi lời gọi nhỏ

Một method có thể được gọi hàng triệu lần, ví dụ equals(), hashCode(), các getter nhỏ, serializer hoặc vòng lặp tính toán. Advice trước/sau mỗi lượt có thể gây:

- Thời gian overhead vượt quá phần việc thật.
- Tăng cấp phát object/context, áp lực garbage collection.
- Tranh chấp lock/bộ đếm tập trung.
- Log/trace cardinality quá cao và lưu trữ đắt.
- Sai kết quả đo do chính đo lường chiếm phần lớn thời gian.
- Reentrancy khi formatter hoặc logger gọi lại phương thức bị instrument.

Nếu cần profiling sâu, hãy đặt **mục tiêu, sampling và giới hạn hiệu năng**, có chiến lược tắt/giảm instrumentation và đo overhead. Điều này khác với dùng cùng một pointcut rộng trong đường production bình thường.

Một số advice cần tuyệt đối không block trong luồng latency-sensitive theo hợp đồng. Nếu ghi log qua exporter chậm, phải quyết định rõ overflow policy: drop, backpressure hay fail operation. Với telemetry best effort, việc exporter lỗi không nên trở thành lỗi nghiệp vụ.

## 17. Vòng đời tài nguyên: nơi phải tránh can thiệp không đúng thời điểm

Trong hệ thống có connection, lock, file handle, transaction hoặc scope context, advice có thể nắm một ranh giới ngắn hơn hoặc dài hơn vòng đời tài nguyên thực.

Ví dụ:

~~~text
around method:
    open resource
    future = proceed()
    close resource
    return future
~~~

Nếu future tiếp tục dùng resource, close xảy ra quá sớm. Ngược lại, nếu advice giữ resource mãi cho tới khi một event hoàn tất không bảo đảm được gửi, có thể rò tài nguyên.

Cần xác định:

1. Ai sở hữu resource?
2. Resource được dùng bởi thread/callback nào?
3. Tín hiệu nào chứng minh mọi thao tác sử dụng đã xong?
4. Có đường throw đồng bộ, exception async, timeout, cancel hoặc shutdown không?
5. Cleanup lỗi có che nguyên nhân lỗi nghiệp vụ không?
6. Có transaction nào đã ở trạng thái rollback-only hoặc kết thúc trước khi advice giả định không?

Các điểm như close(), rollback(), unlock(), cleanup() không nhất thiết cấm mọi instrumentation, nhưng phải **loại khỏi retry hoặc advice thay đổi thực thi** nếu không có hợp đồng cho phép gọi lặp và không phá tính an toàn.

## 18. Kiểm thử đúng nơi áp dụng và đúng vùng cấm

Một test chỉ thấy phương thức nghiệp vụ trả kết quả đúng là chưa đủ. Phải kiểm tra **advice có chạy ở nơi được chọn và không chạy ở nơi bị loại**.

### 18.1. Kiểm thử độ bao phủ dương tính

- Với operation thuộc allowlist, xác minh advice chạy đúng số lần.
- Nếu có authorization, xác minh quyền được kiểm tra trước side effect.
- Nếu có transaction, xác minh begin/commit/rollback đúng thứ tự thực tế.
- Nếu có recorder cuối, xác minh outcome phản ánh cả commit hoặc async completion theo hợp đồng.
- Nếu có retry, xác minh số attempts và các lần tạo transaction phù hợp.

### 18.2. Kiểm thử vùng loại trừ

- Gọi trực tiếp logger, exporter hoặc emitMetric(), xác minh không kích hoạt advice đang dùng nó.
- Gọi health(), infrastructure utility và method bị đánh dấu loại trừ; xác minh không có thêm advice.
- Kiểm tra equals()/hashCode()/toString() nếu chúng nằm trong vùng cấm của pointcut liên quan.
- Nếu có cơ chế proxy, kiểm tra self-invocation, target thô và các đường khởi tạo khác.
- Nếu có weaving, kiểm tra class/method thực sự được weave trong build đang triển khai.
- Khi cache hit hoặc fallback, kiểm tra có bỏ qua guard/recorder bắt buộc hay không.

### 18.3. Kiểm thử lỗi và ảnh hưởng phụ

Dùng fault injection ở các điểm:

- Lỗi target trước khi có effect.
- Lỗi sau khi effect bên ngoài đã xảy ra.
- Commit thất bại hoặc không rõ kết quả.
- Logger/metric exporter ném exception.
- Retry sau timeout, cancellation trong lúc worker vẫn chạy.
- Nhiều request đồng thời, nhiều callback hoặc task dùng lại thread.

Một chính sách chỉ được chấp nhận khi test xác minh **kết quả, lỗi, số lượt effect, thứ tự sự kiện và bảo toàn tài nguyên**, không chỉ assert một exception được ném.

### 18.4. Test ma trận

| Nhóm đường | Expected ALLOW | Expected DENY | Tín hiệu cần quan sát |
| --- | --- | --- | --- |
| Business entry point | Tracing/metric operation | Advice không liên quan | Event bắt đầu/kết thúc đúng scope |
| Telemetry exporter | Hoạt động bình thường | Chính advice đang gọi exporter | Không recursion, một lần ghi |
| Authorization boundary | Guard chạy | Bypass guard | Principal/action/resource và không có effect trái quyền |
| Sensitive mutation | Transaction thích hợp | Retry thiếu idempotency | Commit/rollback và số side effect |
| Async worker | Context/guard đúng lúc thực thi | Giả định chỉ method submit đã đủ | Completion outcome và context khôi phục |
| Cleanup/lifecycle | Dọn dẹp chuẩn | Retry/biến đổi không được duyệt | Resource release đúng một lần theo hợp đồng |

## 19. Quy trình ra quyết định theo kiến trúc

Có thể thực hiện qua chín bước:

**Bước 1 — Liệt kê concern.** Phân biệt observability, security, transaction, retry, caching, fallback và lifecycle. Không gom tất cả thành một “general aspect”.

**Bước 2 — Xác định hợp đồng hành vi.** Method có side effect không? Có thể gọi lặp hoặc skip không? Giá trị trả về có đại diện cho kết quả cuối không?

**Bước 3 — Chọn ranh giới logic.** Chọn operation/service boundary có ý nghĩa, thay vì chọn mọi method trong một package. Với async, xác định cả ranh giới completion.

**Bước 4 — Xác định cơ chế AOP và join point thực sự.** Proxy hay weaving, method call hay execution, ràng buộc method modifier/classloader và đường gọi nội bộ.

**Bước 5 — Tạo bảng ALLOW/DENY/REVIEW theo từng policy.** Ghi rõ phương thức và lý do. Đặc biệt loại trừ hạ tầng của chính advice và các operation không phù hợp retry/transaction.

**Bước 6 — Vẽ thứ tự advice.** Ví dụ RecordFinal(Retry(Transaction(operation))). Xác định lớp nào có thể bỏ qua proceed(), gọi nhiều lần hoặc chuyển đổi lỗi.

**Bước 7 — Xác định ownership và trust boundary.** Tài nguyên sống đến khi nào? Principal có đáng tin cậy? Kiểm tra quyền có điểm cưỡng chế không thể bypass không?

**Bước 8 — Kiểm thử cả điểm được áp dụng và điểm bị loại.** Bao gồm self-call, target trực tiếp, worker, callback, retry, timeout, commit lỗi và telemetry failure.

**Bước 9 — Giám sát và review khi đổi hệ thống.** Mỗi method mới, thay đổi interface, build/weaving scope hoặc đường vào mới đều có thể làm thay đổi tập join point. Phải cập nhật chính sách và test cùng thay đổi.

## 20. Những sai lầm thường gặp

**Sai lầm 1: pointcut chọn toàn bộ package và mọi method.** Khi thêm lớp telemetry hoặc utility, chúng cũng bị can thiệp. Khắc phục bằng allowlist theo hành vi và loại trừ hạ tầng, cùng test độ bao phủ.

**Sai lầm 2: kiểm tra quyền chỉ ở proxy.** Một đường đi tới target gốc hoặc lời gọi nội bộ có thể bypass. Đưa quyết định quyền tới ranh giới thực thi effect đáng tin cậy, rồi dùng AOP hỗ trợ nhất quán.

**Sai lầm 3: retry mọi lời gọi ghi dữ liệu.** Lặp effect ngoài transaction có thể tạo thanh toán/email trùng. Chỉ retry nơi idempotency/đối soát đã được bảo đảm.

**Sai lầm 4: instrument logger của chính aspect.** Tạo vòng đệ quy và tăng tải không kiểm soát. Loại trừ telemetry internals và dùng reentrancy guard khi cần.

**Sai lầm 5: coi method return là task success.** Future có thể hoàn tất lỗi sau khi method trả về. Phải theo dõi đúng completion nếu policy yêu cầu outcome logic.

**Sai lầm 6: xem mọi lỗi là retryable.** Authorization denial, validation failure, business rejection hay lỗi exporter không nên bị retry mù.

**Sai lầm 7: đóng tài nguyên trong finally khi worker vẫn dùng.** Sai ownership/lifecycle. Đặt cleanup tại ranh giới sử dụng cuối thật sự.

**Sai lầm 8: dùng tên annotation thay cho chứng cứ interception.** Một aspect khai báo tồn tại không chứng minh pointcut match và runtime can thiệp mọi đường cần bảo vệ. Cần test trên cơ chế/artefact thực.

**Sai lầm 9: log toàn bộ đối số và kết quả.** Có thể lộ dữ liệu mật, gây chi phí lớn hoặc kích hoạt toString() nguy hiểm. Chỉ ghi metadata allowlisted.

**Sai lầm 10: chỉ kiểm tra nơi phải có advice.** Không kiểm tra nơi **không được** có advice sẽ bỏ sót recursion, chi phí và effect ngoài ý muốn.

## 21. Tình huống tự kiểm tra

**Tình huống A.** Một timing aspect match tất cả method, bao gồm chính MetricsExporter.publish(). Advice gọi publish() để ghi metric. Rủi ro gì?

**Đáp án:** publish() có thể lại kích hoạt timing aspect và gọi publish() tiếp, sinh đệ quy. Cần loại trừ exporter khỏi pointcut và kiểm thử không có reentrancy không mong muốn.

**Tình huống B.** Một retry aspect được gắn cho chargeCard(). Lần đầu phía thanh toán đã ghi nhận giao dịch nhưng caller timeout. Có thể gọi lại an toàn chỉ nhờ transaction database nội bộ không?

**Đáp án:** Không. Transaction database nội bộ không tự rollback effect ở bên thanh toán. Cần idempotency key/kiểm tra kết quả và reconciliation theo hợp đồng.

**Tình huống C.** Authorization advice chạy khi gọi target qua proxy, nhưng bên trong target một method gọi this.delete(). Guard có chắc chạy?

**Đáp án:** Không thể chắc với proxy-based interception. Lời gọi this.delete() có thể không đi qua proxy. Với weaving tại method execution, hành vi có thể khác; phải kiểm chứng join point thực tế và cưỡng chế quyền tại ranh giới đáng tin cậy.

**Tình huống D.** Recorder after-returning thấy một CompletableFuture đã được trả về, rồi log “completed successfully”. Điều gì sai?

**Đáp án:** Lần gọi đã trả handle không có nghĩa future hoàn tất thành công. Muốn đo operation outcome phải quan sát completion của đối tượng kết quả theo hợp đồng.

**Tình huống E.** Một advice caching được đặt ngoài authorization; cache hit trả dữ liệu riêng tư ngay. Cần đánh giá gì?

**Đáp án:** Authorization bên trong có thể bị bỏ qua vì cache không gọi proceed(). Cần kiểm tra quyền cả nhánh hit/miss, key phân vùng đúng và guard dữ liệu nhạy cảm.

**Tình huống F.** Có nên cấm toàn bộ instrumentation trên hashCode()?

**Đáp án:** Không có quy tắc phổ quát. Trong monitoring production thông thường, nên tránh advice tạo effect lớn, retry hay thay đổi kết quả. Một công cụ profiling chuyên biệt vẫn có thể cần đo hashCode() nếu đã kiểm soát overhead và semantics.

**Tình huống G.** Test chỉ assert method nằm trong allowlist được intercept một lần. Đã đủ chưa?

**Đáp án:** Chưa. Cần test vùng loại trừ không bị instrument, đường gọi khác tới same effect không bypass security, và error/async/resource lifecycle đúng.

**Tình huống H.** Một cleanup method ném lỗi khi đóng resource, advice trong finally cũng ném lỗi log. Người gọi có thể mất thông tin gì?

**Đáp án:** Lỗi của observer có thể che mất lỗi cleanup hoặc lỗi nghiệp vụ trước đó. Cần chính sách bảo toàn nguyên nhân/suppressed exception hoặc xử lý observer best effort, tùy hợp đồng.

## 22. Kết luận kỹ thuật

Xác định nơi áp dụng AOP là bài toán **chọn đúng ranh giới hành vi**; xác định nơi không được can thiệp là bài toán **bảo vệ hợp đồng, hiệu năng, an toàn truy cập và vòng đời tài nguyên**. Không nên bắt đầu bằng pointcut quá rộng rồi hy vọng các ngoại lệ sẽ tự được xử lý.

Một thiết kế tốt xác định rõ:

- **ALLOW:** các operation/join point có lý do can thiệp và policy được phê duyệt.
- **DENY:** những thành phần mà loại advice đó có thể gây đệ quy, lặp effect, sai outcome hoặc thay đổi bất biến.
- **REVIEW:** những trường hợp chưa chứng minh được semantics của phương thức, proxy/weaving, transaction hoặc async.
- **VALIDATION:** test độ bao phủ dương tính, vùng loại trừ, đường bypass, thứ tự advice, lỗi và tác dụng phụ.

AOP hữu ích khi các concern cắt ngang được áp dụng có chủ đích. Chất lượng kiến trúc nằm ở việc **nơi được chọn thực sự cần advice, nơi bị loại không vô tình chạy advice và mọi bất biến quan trọng vẫn được bảo vệ ở ranh giới đáng tin cậy**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-003">Nguyên tắc trách nhiệm đơn và độ kết dính của aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Nguyên tắc trách nhiệm đơn và độ kết dính của Aspect trong thiết kế AOP

## 1. Mục tiêu và bối cảnh

**Aspect-Oriented Programming (AOP)** là cách mô-đun hóa các *mối quan tâm cắt ngang* (*cross-cutting concerns*) xuất hiện ở nhiều lớp hoặc nhiều thao tác: quan sát thời gian thực thi, ghi log, truy vết, kiểm tra quyền, kiểm soát retry, quản lý ngữ cảnh và một số chính sách thực thi khác. Một **aspect** tập hợp các **advice** được áp dụng tại các **join point** do **pointcut** lựa chọn. Tùy cơ chế thực thi, mã aspect có thể được kết hợp bằng proxy, decorator, trình tạo mã hoặc weaving.

Tách mã ra khỏi nghiệp vụ **không tự động làm kiến trúc tốt hơn**. Nếu một aspect ôm cả bảo mật, retry, cache, transaction, log và chuyển đổi lỗi, nó có thể trở thành một “siêu mô-đun” mà chỉ cần sửa một yêu cầu nhỏ cũng ảnh hưởng nhiều luồng thực thi. Ngược lại, chia mỗi dòng log hay mỗi lời gọi kiểm tra thành một aspect riêng có thể khiến hệ thống khó hiểu vì có quá nhiều điểm chặn và thứ tự phối hợp.

Hai nguyên tắc thiết kế giải quyết tình huống này:

1. **Single Responsibility Principle (SRP — trách nhiệm đơn):** một aspect nên có **một lý do chính để thay đổi**, xuất phát từ một chính sách hoặc một trách nhiệm có chủ sở hữu rõ ràng.
2. **Cohesion (độ kết dính):** các advice, pointcut, cấu hình, trạng thái và phép đo trong cùng aspect cần **gắn chặt với mục tiêu chung ấy**. Những thành phần chỉ tình cờ cùng xuất hiện tại một phương thức chưa chắc thuộc cùng một trách nhiệm.

Mục tiêu thực hành là nhận ra đâu là một mối quan tâm có thể mô-đun hóa, đặt ranh giới cho aspect, thiết kế pointcut nhất quán, phối hợp nhiều aspect và kiểm thử khả năng thay đổi độc lập. Bài trình bày **AOP tổng quát từ cơ bản tới nâng cao** và ví dụ Java 17+ bằng decorator minh họa cách tổ chức quanh một lời gọi.

## 2. SRP trong aspect có nghĩa gì?

### 2.1. “Một trách nhiệm” là một lý do thay đổi

Không nên diễn giải SRP thành “mỗi aspect chỉ có một method/advice”. Một aspect quan sát hiệu năng có thể cần:

- Advice trước khi gọi để ghi mốc thời gian.
- Advice sau khi hoàn tất để tính độ trễ.
- Xử lý đường lỗi để cập nhật metric.
- Hàm nội bộ chuẩn hóa tên thao tác và nhãn metric.
- Bộ cấu hình quyết định mốc đo, loại join point được quan sát và ngân sách overhead.

Những thành phần này cùng phục vụ **một chính sách đo thời gian/hiệu năng**, được cập nhật khi yêu cầu observability đổi. Chúng có thể tạo thành một aspect kết dính cao dù có nhiều hàm.

Ngược lại, một aspect chứa cả luật “vai trò nào được phê duyệt đơn hàng”, “khi lỗi I/O thì retry mấy lần” và “log theo định dạng JSON nào” có **nhiều lý do độc lập để thay đổi**: thay chính sách bảo mật, thay chiến lược chịu lỗi, thay định dạng telemetry. Gom chúng vào một nơi làm tăng tác động của thay đổi và nguy cơ phá hành vi khác.

Dùng câu hỏi: **Ai yêu cầu thay đổi, vì sao phải thay, và có cần sửa những hành vi khác không?** Nếu ba thay đổi có thể do ba nhóm/chính sách vận hành khác nhau quyết định, đó là dấu hiệu phải cân nhắc tách.

### 2.2. “Một chức năng kỹ thuật” chưa chắc là một trách nhiệm

Tên gọi `InfrastructureAspect`, `CommonAspect` hay `UtilsAspect` chưa chứng minh phần mã kết dính. Đặc biệt, “mọi việc trước/sau lời gọi” không phải một mục đích thiết kế cụ thể. Một phần logic có chung đặc điểm kỹ thuật như “đều dùng reflection” không có nghĩa chúng cùng trách nhiệm.

Đồng thời, không cần tách máy móc theo từng **kiểu advice** (before/after/around). Nếu một policy cần cả before và after để giữ trạng thái chính xác, tách chúng sang các aspect chỉ để mỗi file có một advice có thể làm lộ chi tiết nội bộ và tăng nguy cơ sai thứ tự.

### 2.3. Những yếu tố cần xem cùng nhau

Một ranh giới aspect thường gồm:

| Thành phần | Cần kết dính theo điều gì? | Dấu hiệu bất thường |
| --- | --- | --- |
| **Policy/contract** | Một cam kết hoặc quy tắc nhất quán | Cùng aspect có nhiều rule không liên quan |
| **Pointcut** | Tập join point phục vụ đúng policy | Pointcut quá rộng, chặn mọi method |
| **Advice** | Những bước cần thiết để thực hiện policy | Advice làm cả nghiệp vụ và nhiều kỹ thuật |
| **State** | Dữ liệu nội bộ thuộc vòng đời policy | Biến đếm/timer/cache quyền dùng chung không rõ |
| **Configuration** | Các tham số thay đổi cùng policy | Một config bật tắt nhiều tính năng độc lập |
| **Dependencies** | Những dịch vụ thiết yếu để thi hành policy | Aspect phụ thuộc cả DB, HTTP, cache, mail, logger |
| **Tests** | Xác minh một hành vi/contract rõ | Chỉnh logging khiến test authorization hỏng |
| **Observability** | Đo được tác động của chính policy | Không biết lỗi phát sinh ở lớp advice nào |

Một aspect kết dính cao có giao diện hẹp, tên biểu đạt đúng policy, không lưu trạng thái ngoại lai và có tập kiểm thử tương ứng với thay đổi dự kiến.

## 3. Nhận biết các dạng cohesion

Trong thiết kế mô-đun nói chung, những thành phần gắn với nhau vì “cùng thời điểm” hoặc “cùng công cụ” thường kết dính yếu hơn các thành phần cùng phục vụ một mục đích chức năng rõ ràng.

Áp vào AOP:

- **Cùng xuất hiện theo thời điểm:** ghi log, kiểm tra quyền, mở transaction đều xảy ra trước một operation. Đây là lý do yếu để đưa chúng chung một aspect.
- **Cùng dùng dữ liệu đầu vào:** trace ID, principal và request ID cùng nằm trong context. Chúng có thể chia sẻ một abstraction context nhưng chưa chắc cùng thuộc một policy.
- **Cùng quy trình:** mở phạm vi đo, chạy nghiệp vụ, ghi kết quả và đóng phạm vi đo. Đây thường là một trách nhiệm observability thống nhất.
- **Cùng bất biến:** mọi advice trong nhóm cần bảo đảm “mỗi operation chỉ công bố một terminal metric”. Chúng có liên hệ chức năng chặt chẽ.
- **Cùng lý do thay đổi:** khi quy định đo latency đổi, các điểm bắt đầu/kết thúc, nhãn và timer cần đổi đồng bộ; một aspect duy nhất có thể là hợp lý.

Không phải cứ nhiều advice là cohesion thấp. Vấn đề là **mức phụ thuộc ngữ nghĩa giữa những advice**, không phải số dòng mã.

### 3.1. Cohesion của pointcut

Pointcut kết dính khi mọi join point được chọn đều chia sẻ **cùng semantics mà policy cần**. Ví dụ, một policy đếm thời gian truy vấn chỉ chọn các thao tác truy vấn có ranh giới hoàn tất rõ ràng.

Pointcut như “mọi method bắt đầu bằng `get`” có thể vô tình chặn:

- Method lấy dữ liệu công khai.
- Method đọc thông tin nhạy cảm.
- Method đọc dữ liệu cache, không truy cập I/O.
- Method chỉ trả một `Future` chưa hoàn tất.
- Getter đơn giản trong domain model.

Nếu advice áp cùng một timeout, transaction hoặc quyền lên những thao tác rất khác nhau, đó là **thiếu cohesion giữa policy và tập join point**. Hãy dùng các contract hoặc metadata ổn định, signature và ranh giới API có ý nghĩa, rồi kiểm thử chính xác danh sách join point được chặn.

### 3.2. Cohesion của state

Aspect ghi metric có thể sở hữu counter hoặc timer; aspect retry có thể sở hữu retry budget; aspect bảo mật có thể cần policy evaluator. Không nên dùng một mutable map trong “CommonAspect” để chứa cả trace context, authentication decision và retry counter chỉ vì tiện truy cập.

Đặc biệt, aspect instance có thể được chia sẻ giữa nhiều lời gọi. State gắn với mỗi invocation phải có phạm vi và đồng bộ rõ ràng; lưu principal hoặc timestamp của request gần nhất trên field dùng chung có thể gây race và rò dữ liệu giữa người dùng.

## 4. Vì sao một “God Aspect” gây hại?

Xét đoạn giả mã quanh một method bất kỳ:

~~~text
around serviceCall(args):
    if not authorized(args): return emptyResult()
    beginTransaction()
    key = buildCacheKey(args)
    if cache.has(key): return cache.get(key)
    for retry in 1..3:
        try:
            result = proceed(args)
            cache.put(key, result)
            logFullArguments(args, result)
            commit()
            return result
        catch Exception:
            logError()
    rollback()
    return defaultValue()
~~~

Đây là **giả mã anti-pattern**, không phải một chương trình chạy được. Nó chứa nhiều lỗi tiềm tàng:

- Trả kết quả rỗng khi thiếu quyền có thể che hành vi từ chối.
- Cache hit trả ra trước khi transaction được đóng đúng đường đi.
- Retry có thể thực thi side effect nhiều lần.
- Cache có thể nhận dữ liệu chưa commit.
- Exception gốc có thể biến mất sau nhiều lần lặp.
- Log toàn bộ tham số có thể lộ dữ liệu nhạy cảm.
- Transaction có thể không được rollback ở mọi nhánh.
- Thay đổi định dạng log có thể vô tình ảnh hưởng các nhánh xử lý lỗi.
- Không rõ ai chịu trách nhiệm cho từng invariant hoặc thứ tự.

Ngay cả khi khắc phục các lỗi cú pháp/luồng, việc gom tất cả vào một aspect vẫn làm **mỗi thay đổi của policy có phạm vi tác động quá rộng**.

### 4.1. Dấu hiệu phát hiện khi review

Một số cảnh báo:

1. Tên `EverythingAspect`, `GenericAspect`, `ServiceInterceptor` nhưng không mô tả được mục đích duy nhất.
2. Có nhiều cờ `enableSecurity`, `enableCache`, `enableRetry`, `enableAudit` trong cùng một cấu hình.
3. Advice nhận hàng loạt dependency chỉ để chọn nhánh theo loại method.
4. Pointcut có nhiều điều kiện đặc biệt “trừ method X”, “chỉ áp cho service Y”.
5. Test của một tính năng phải dựng database, cache, mạng và policy khác không liên quan.
6. Thay đổi thứ tự log lại làm thay đổi transaction hoặc exception.
7. Một lời gọi được bảo vệ theo kiểu “nếu method có annotation A thì check quyền, nếu B thì cache” nhưng không có hợp đồng nhất quán về composition.
8. Nhóm phát triển không thể chỉ ra owner của policy hoặc nguyên nhân sửa gần nhất của aspect.

Đây là dấu hiệu cần đánh giá kiến trúc, không phải mệnh lệnh bắt buộc chia mọi file ngay lập tức. Chi phí phối hợp, hiệu năng và giới hạn của cơ chế weaving cũng ảnh hưởng ranh giới tối ưu.

## 5. Phân tách trách nhiệm có chủ đích

Thay vì một aspect làm tất cả, có thể tách:

| Aspect / module | Trách nhiệm chính | Không nên tự quyết |
| --- | --- | --- |
| `AuthorizationAspect` | Yêu cầu quyết định allow/deny theo policy đã công bố | Tạo fallback “thành công” khi bị từ chối |
| `TracingAspect` | Tạo/kết nối span, lan truyền trace context, đóng vòng đời span | Quyết định có retry hay rollback nghiệp vụ |
| `MetricsAspect` | Ghi số lần gọi, lỗi, latency theo contract đo lường | Chuyển exception lỗi thành giá trị khác |
| `RetryAspect` | Thực hiện lại thao tác theo policy retry công khai | Tự cho phép retry lệnh ghi chưa có idempotency |
| `TransactionAspect` | Bắt đầu, commit, rollback trong một transaction boundary được định nghĩa | Thay thế nguyên tử nghiệp vụ phân tán bằng giả định |
| `CachingAspect` | Tra/lưu kết quả với key, TTL, invalidation và consistency rõ | Bỏ qua kiểm tra quyền của dữ liệu được bảo vệ |
| `AuditAspect` | Ghi sự kiện kiểm toán theo hợp đồng độ bền và dữ liệu | Dùng log debug có sampling như chứng cứ kiểm toán đầy đủ |
| `ContextAspect` | Thiết lập/khôi phục context thuộc lời gọi khi cần | Dùng field dùng chung gây rò principal giữa invocation |

Bảng thể hiện một phương án hợp lý; đôi khi một policy kỹ thuật cần nhiều lớp nhỏ hoặc vài concern gắn kết có thể ở cùng một aspect. Điều cốt lõi là **lý do thay đổi, hợp đồng và chủ sở hữu**.

### 5.1. Một aspect nhiều advice vẫn có thể đúng SRP

Ví dụ một `LatencyAspect`:

~~~java
// Minh hoa API gia dinh, khong phai chuong trinh doc lap.
long startedAt = System.nanoTime();
try {
    return proceed();
} finally {
    metrics.recordDuration(System.nanoTime() - startedAt);
}
~~~

Nó có một trách nhiệm là đo elapsed time, một đường vào và một đường ra. Nếu một phương thức trả `CompletionStage`, advice đồng bộ đo **thời gian tạo stage**; muốn đo đến completion phải gắn callback thích hợp. Mở rộng để hỗ trợ async vẫn có thể thuộc **cùng lý do thay đổi** nếu hợp đồng của aspect là “đo thời gian hoàn tất operation”, miễn bảo đảm logic terminal một lần và cleanup đúng.

### 5.2. Cái gì nên là helper/library thay vì aspect khác?

Một số code như `normalizeMetricName`, `captureContext` hoặc `safeTruncate` có thể là **hàm hỗ trợ dùng chung**, không nhất thiết là một aspect mới. Aspect chỉ nên tồn tại khi cần tham gia composition tại join point. Tách một thuật toán thuần thành một “aspect” chỉ để dùng lại là thêm interception và dependency không cần thiết.

Ngược lại, nếu một chính sách phải được chèn ở nhiều join point và có vòng đời riêng, đóng gói nó vào aspect thay vì lặp ở từng domain service có thể hữu ích.

## 6. Ví dụ Java 17+: mỗi decorator đại diện một trách nhiệm

Ví dụ dưới cho thấy ba lớp cắt ngang tách biệt: **authorization**, **tracing** và **metrics**. Chúng được cài bằng decorator quanh interface để quan sát rõ composition. Decorator không tự động weaving, nhưng hành vi `next.call()` tương đương ý tưởng `proceed()` của around advice. Từng lớp có một mục đích, và nghiệp vụ không biết lớp nào đang bao quanh.

Lưu file `AspectCohesionDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicLong;

public class AspectCohesionDemo {
    interface OrderOperation {
        String place(String role, String orderId);
    }

    static final class CoreOperation implements OrderOperation {
        @Override
        public String place(String role, String orderId) {
            System.out.println("domain: process");
            return "receipt:" + orderId;
        }
    }

    static final class AuthorizationAspect implements OrderOperation {
        private final OrderOperation next;
        private final AtomicLong denied = new AtomicLong();

        AuthorizationAspect(OrderOperation next) {
            this.next = next;
        }

        @Override
        public String place(String role, String orderId) {
            if (!"operator".equals(role)) {
                denied.incrementAndGet();
                throw new SecurityException("forbidden");
            }
            return next.place(role, orderId);
        }

        long deniedCount() {
            return denied.get();
        }
    }

    static final class TracingAspect implements OrderOperation {
        private final OrderOperation next;

        TracingAspect(OrderOperation next) {
            this.next = next;
        }

        @Override
        public String place(String role, String orderId) {
            System.out.println("trace: begin place");
            try {
                String result = next.place(role, orderId);
                System.out.println("trace: success");
                return result;
            } catch (RuntimeException e) {
                System.out.println("trace: error");
                throw e;
            }
        }
    }

    static final class MetricsAspect implements OrderOperation {
        private final OrderOperation next;
        private final AtomicLong attempts = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();

        MetricsAspect(OrderOperation next) {
            this.next = next;
        }

        @Override
        public String place(String role, String orderId) {
            attempts.incrementAndGet();
            try {
                String result = next.place(role, orderId);
                successes.incrementAndGet();
                return result;
            } catch (RuntimeException e) {
                failures.incrementAndGet();
                throw e;
            }
        }

        String summary() {
            return "attempts=" + attempts.get()
                    + " successes=" + successes.get()
                    + " failures=" + failures.get();
        }
    }

    public static void main(String[] args) {
        AuthorizationAspect auth =
                new AuthorizationAspect(new CoreOperation());
        TracingAspect tracing = new TracingAspect(auth);
        MetricsAspect metrics = new MetricsAspect(tracing);

        System.out.println("caller: " + metrics.place("operator", "order-1"));
        try {
            metrics.place("guest", "order-2");
        } catch (SecurityException e) {
            System.out.println("caller: " + e.getMessage());
        }

        System.out.println("metrics: " + metrics.summary());
        System.out.println("auth: denied=" + auth.deniedCount());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AspectCohesionDemo.java
java AspectCohesionDemo
~~~

**Stdout chính xác:**

~~~text
trace: begin place
domain: process
trace: success
caller: receipt:order-1
trace: begin place
trace: error
caller: forbidden
metrics: attempts=2 successes=1 failures=1
auth: denied=1
~~~

Điểm cần nhận ra:

- `AuthorizationAspect` chỉ kiểm tra quyền theo policy đơn giản, không đo metric hay ghi trace; đường không được phép không vào `CoreOperation`.
- `TracingAspect` đánh dấu vòng đời lời gọi và phân biệt success/error nhưng **không thay** kết quả hay exception.
- `MetricsAspect` chỉ đếm các sự kiện; mọi invocation đi qua wrapper đều được đếm, bao gồm một lần bị từ chối. Định nghĩa `failures` ở demo bao gồm `SecurityException`; sản phẩm thực có thể phải tách `denied` và `businessFailed`.
- `CoreOperation` sở hữu logic nghiệp vụ. Trong demo, role chỉ là chuỗi để trình bày cấu trúc; hệ thống thực phải lấy principal từ ngữ cảnh xác thực tin cậy và cần kiểm soát quyền tại boundary phù hợp.
- Thay trace formatter chỉ cần sửa `TracingAspect`. Thay rule phân quyền chỉ cần sửa `AuthorizationAspect` và test bảo mật. Thay tên metric chỉ cần sửa `MetricsAspect`.
- Thứ tự wrapper ở đây là **Metrics -> Tracing -> Authorization -> Core**. Nếu chuyển `MetricsAspect` vào phía trong `AuthorizationAspect`, một lần bị từ chối sẽ không tăng `attempts`. Đây là thay đổi semantics thống kê nên phải được quyết định theo hợp đồng metric.

Ví dụ cố ý đồng bộ và không có cache/transaction để minh họa SRP rõ ràng. Những policy cắt ngang khác có thể được thêm bằng lớp riêng khi thực sự cần. Đừng nhầm “mỗi aspect làm một việc” với “cứ thêm bao nhiêu aspect cũng không có rủi ro”: composition order và performance vẫn phải được xét.

## 7. Độ kết dính và mức độ phụ thuộc giữa các aspect

**Cohesion** trả lời: các phần bên trong một aspect có phục vụ cùng chính sách không? **Coupling** trả lời: một aspect phải biết hoặc thay đổi theo các aspect khác nhiều đến mức nào?

Một kiến trúc đáng mong muốn thường có **cohesion cao**, đồng thời giảm **coupling không cần thiết**. Ví dụ:

- Metrics không cần gọi trực tiếp `AuthorizationAspect.check()`; nó quan sát kết quả/loại lỗi theo hợp đồng.
- Tracing không nên trực tiếp điều chỉnh retry counter.
- Authorization không nên biết cache exporter format ra sao.
- Transaction không nên tự format log nhạy cảm hoặc quyết định sampling.

Tuy nhiên có những **phụ thuộc ngữ nghĩa có thật** cần nêu rõ. Cache trả dữ liệu nhạy cảm phải được đặt sau enforcement đúng mức. Retry và transaction phải phối hợp để không lặp side effect ngoài hợp đồng. Tracing có thể cần biết một logical operation chứa bao nhiêu attempt để đặt span và tags hợp lý. Tách file **không xóa các phụ thuộc này**; nó khiến chúng được mô tả ở cấu trúc composition thay vì ẩn trong một phương thức dài.

### 7.1. Không tạo vòng phụ thuộc

Một vòng như sau khó vận hành:

~~~text
TracingAspect -> MetricsAspect -> RetryAspect
       ^                             |
       +-----------------------------+
~~~

Nếu mỗi aspect gọi ngược trực tiếp vào aspect khác hoặc interceptor lại gọi chính join point mình chặn, có thể xảy ra recursion, double-count hoặc deadlock. Đặt các abstraction hẹp ở tầng thấp hơn:

- `TraceContext` truyền ngữ cảnh; không buộc Metrics phải gọi Tracing.
- `PolicyDecision` là kết quả trung lập được Authorization cung cấp ở boundary.
- `OperationOutcome`/`ExecutionAttempt` mô tả kết quả để telemetry nhận thông tin.
- `Clock`/`Scheduler` có thể là dependency độc lập, giúp kiểm thử.

Những abstraction dùng chung cần có **quy tắc sở hữu trạng thái**; nếu mọi aspect chỉnh cùng một mutable context map không ràng buộc, vòng phụ thuộc chỉ đổi hình thức.

### 7.2. DIP và phụ thuộc vào hợp đồng

Nguyên tắc đảo ngược phụ thuộc (Dependency Inversion Principle) hữu ích trong AOP: advice phụ thuộc interface nhỏ như `MetricRecorder`, `PolicyEvaluator` hoặc `Clock` thay vì tạo thẳng database client, network exporter và cache ở trong. Điều này cho phép:

- Test advice với fake không cần dựng toàn bộ hạ tầng.
- Thay backend quan sát hay policy engine mà không đổi logic interception.
- Giữ mã nghiệp vụ độc lập với công cụ weaving cụ thể.
- Bắt rõ failure mode của dependency: không có evaluator, recorder bị lỗi, clock nhảy hoặc exporter bị nghẽn.

Không biến interface thành tầng trừu tượng vô nghĩa cho một hàm nhỏ. Đặt abstraction ở nơi nó bảo vệ ranh giới thay đổi thật.

## 8. Thứ tự composition thuộc kiến trúc, không thuộc SRP riêng lẻ

Một aspect có SRP đúng vẫn có thể làm hệ thống sai khi đặt sai thứ tự. Ví dụ:

### 8.1. Authorization và cache

~~~text
Authorization -> Cache -> Domain   (authorization chạy trên mọi đường trả)
Cache -> Authorization -> Domain   (cache hit có thể bỏ qua authorization)
~~~

Nếu cache chứa dữ liệu được bảo vệ và trả trực tiếp khi hit, chuỗi thứ hai có thể làm lộ dữ liệu. Cache key phân theo user giảm một số rủi ro nhưng **không thay thế việc kiểm tra quyền** khi quyền bị thu hồi hay context đổi. Vì vậy một `AuthorizationAspect` kết dính cao vẫn cần composition contract rõ với `CachingAspect`.

### 8.2. Retry và transaction

~~~text
Retry -> Transaction -> Domain
Transaction -> Retry -> Domain
~~~

Hai thứ tự có thể tạo khác biệt lớn. Với retry ở ngoài, mỗi attempt có thể có transaction riêng. Với transaction ở ngoài, nhiều attempt có thể nằm trong một transaction không phù hợp để khôi phục sau lỗi. Quy tắc chỉ hợp lệ khi operation cho phép retry và cơ sở dữ liệu có semantics tương ứng. Một giao dịch thất bại có thể phải rollback toàn bộ trước attempt mới; không được giả định mọi transaction vẫn usable sau bất kỳ exception nào.

### 8.3. Logging, tracing và lỗi

Nếu một error-mapping aspect bên ngoài biến exception thành kết quả fallback, một metrics aspect bên trong ghi lỗi còn một metrics aspect bên ngoài ghi success. Đây có thể đúng nếu họ đo **hai đại lượng khác nhau** (attempt outcome vs caller-visible outcome), nhưng phải đặt tên rõ. Nếu không, dashboard mâu thuẫn là dấu hiệu ranh giới contract chưa rõ.

Phân tách trách nhiệm tạo ra **những điểm phối hợp có thể nhìn thấy và kiểm thử**. Đừng để thứ tự advice phụ thuộc ngẫu nhiên vào classpath, thứ tự đăng ký không được bảo đảm hoặc tên file.

## 9. SRP có liên quan thế nào đến business rules?

Một mối quan tâm cắt ngang xuất hiện ở nhiều module chưa chắc được phép ẩn hoàn toàn trong Aspect. Quy tắc như “đơn hàng đã hủy không được giao”, “số dư không được âm”, “không chuyển tiền hai lần” thuộc bất biến domain, phải có hợp đồng nghiệp vụ và kiểm thử độc lập. Nếu chỉ có một aspect thực hiện kiểm tra, đường gọi bypass proxy/weaving có thể phá bất biến.

Aspect có thể giúp **áp dụng chính sách đã công bố** ở nhiều boundary, ví dụ một cơ chế kiểm tra quyền chuẩn. Nhưng điều kiện allow/deny phải có nguồn policy và nơi bảo vệ đáng tin. Đừng dùng một “ValidationAspect” tổng quát để biến dữ liệu sai thành dữ liệu có vẻ hợp lệ hoặc nuốt exception nhằm làm hệ thống chạy tiếp.

Một cách phân chia:

| Nhu cầu | Chủ sở hữu chính | Vai trò hợp lý của aspect |
| --- | --- | --- |
| Bất biến của entity | Domain model / service | Có thể đo/ghi outcome, không là nguồn bảo vệ duy nhất |
| Điều kiện API | API contract / boundary | Tái sử dụng validation đã khai báo, giữ nguyên lỗi |
| Kiểm soát quyền | Security policy và boundary đáng tin | Áp dụng PEP theo pointcut rõ, test bypass |
| Retry | Contract thao tác, idempotency, failure policy | Điều phối attempt đúng điều kiện |
| Audit | Hợp đồng kiểm toán và đường lưu tin cậy | Thu thập event đúng vòng đời, không biến audit thành log debug |
| Performance telemetry | Observability contract | Đo theo semantic đã chọn, giảm overhead |

**SRP không có nghĩa policy chỉ được phép xuất hiện đúng một nơi.** Có thể cần nhiều lớp bảo vệ cùng bất biến ở các boundary khác nhau; điều quan trọng là chúng có trách nhiệm và hợp đồng được đặt tên, tránh các bản sao khác nghĩa.

## 10. Độ kết dính ở những ranh giới bất đồng bộ

Một method có thể trả về `CompletionStage<T>` ngay khi công việc chưa hoàn tất. Nếu một aspect đo thành công ngay lúc method trả stage, nó đang đo **return của handle** chứ chưa đo terminal outcome của nghiệp vụ. Việc hỗ trợ async là quyết định về **semantics của policy**, không phải phép bổ sung callback tùy tiện.

Nếu `TracingAspect` cam kết quan sát một logical operation đến khi kết thúc, nó có thể cần:

- Mở span khi bắt đầu.
- Truyền context qua executor/callback một cách tường minh.
- Gắn observer vào completion stage thích hợp.
- Đánh dấu success/error/cancel/timeout đúng thời điểm.
- Đảm bảo đóng span **một lần**, tránh race giữa completion và cancellation.
- Không làm hỏng kết quả nghiệp vụ nếu exporter hỏng.

Những việc này có cohesion cao dưới một trách nhiệm tracing. Nhưng nó không có quyền tự ý quyết định retry, rollback hay đóng resource của worker khi chỉ nhìn thấy handle đã timeout.

Một `MetricsAspect` có thể ghi cùng thời gian nhưng theo ngữ nghĩa khác (thời gian queue, thời gian worker, thời gian caller chờ). Không dùng tên `latency` chung chung; định nghĩa điểm bắt đầu/kết thúc để metric có thể so sánh.

Với stream nhiều phần tử, một aspect có thể quản lý lifecycle **subscription** và một module riêng đếm item; việc chia hay gộp phụ thuộc chúng có cùng budget, owner và lý do thay đổi không. Không tạo một chuỗi aspect cho mỗi item nếu chi phí vượt ngân sách.

## 11. Lựa chọn độ hạt: quá to và quá nhỏ

### 11.1. Aspect quá lớn

Khi một aspect chứa nhiều policy độc lập, chi phí thay đổi tăng vì:

- Bề mặt test lớn và dễ có regression ngoài mục tiêu.
- Cấu hình phình ra, các tổ hợp cờ khó bao phủ hết.
- Một exception không rõ phát sinh từ rule nào.
- Các đội sở hữu policy độc lập cùng sửa một file, tăng xung đột.
- Khó benchmark chi phí riêng của từng policy.
- Khó thay một cơ chế weaving hoặc một backend mà không đụng toàn module.

### 11.2. Aspect quá nhỏ

Nếu mỗi bước của một policy thành aspect riêng, có thể gặp:

- Bắt buộc đặt thứ tự cực kỳ chi tiết để một operation đúng.
- Trạng thái `startTime` hoặc correlation context phải truyền qua nhiều nơi.
- Một thay đổi policy kéo theo chỉnh cả chuỗi component.
- Mỗi layer thêm interceptor invocation/callback, có thể tăng overhead.
- Khó truy vết vì một hành vi bị chia quá nhiều file.

Ví dụ không cần tách `LatencyStartAspect` và `LatencyStopAspect` nếu cả hai bắt buộc đi cùng nhau để đo một khoảng thời gian đúng. Around advice nhỏ gọn quản lý cả start/stop thường rõ hơn và an toàn hơn khi exception xảy ra.

### 11.3. Quy tắc chọn độ hạt

Hãy gom những thành phần có:

1. Cùng **invariant/policy**.
2. Cùng **lý do thay đổi** và owner.
3. Cùng **vòng đời invocation/context**.
4. Cùng **phạm vi pointcut** có thể giải thích.
5. Cùng **chiến lược kiểm thử**.

Hãy tách khi hai nhóm có contract, owner, thứ tự điều phối hoặc lifecycle khác nhau và có thể phát triển độc lập. Đừng quyết định chỉ bằng số lượng method, số dòng code hoặc tên “concern”.

## 12. Thiết kế pointcut để hỗ trợ cohesion

Pointcut quyết định nơi aspect chạy. Chọn sai phạm vi có thể buộc advice chứa hàng loạt nhánh đặc biệt, làm cohesion giảm:

~~~java
// GIA MA / minh hoa, khong phai API AOP cu the:
// if (methodName.startsWith("get") && !isHealthCheck()
//     && !isInternalCall() && !isCachedSpecialCase()) { ... }
~~~

Nếu liên tục phải bổ sung exception theo tên method, hãy xem lại abstraction đầu vào. Có thể nên dùng:

- **Contract/metadata** biểu đạt chính sách cần áp, ví dụ marker `@MeasuredOperation` do hệ thống tự định nghĩa.
- **API boundary** ổn định như một nhóm endpoint hoặc interface xác định.
- **Rule cấu hình có kiểm thử** mô tả các thao tác được phép, không chọn theo tên ngẫu nhiên.
- **Phân nhóm theo lifecycle**: đồng bộ, async, streaming có thể cần những advice hoặc adapter khác nhau.
- **Kiểm thử join-point coverage** để phát hiện method mới vô tình không được chặn.

Pointcut càng rộng không đồng nghĩa khả năng bao phủ càng an toàn. Nếu một Aspect bảo mật chỉ chặn method bên ngoài proxy, self-invocation có thể bypass; nếu một MetricAspect chặn cả phương thức telemetry nội bộ, nó có thể tự gọi lặp vô hạn.

## 13. Kiểm thử SRP và cohesion bằng khả năng thay đổi độc lập

Một bộ kiểm thử hữu ích gồm bốn tầng.

### 13.1. Kiểm thử riêng từng aspect

Với target giả:

- `AuthorizationAspect`: allow đi qua đúng một lần; deny không chạy target; lỗi loại đúng; không phát log chứa secret.
- `MetricsAspect`: đếm success, failure, cancellation theo hợp đồng; metric exporter fail không làm hỏng nghiệp vụ nếu chính sách cho phép.
- `TracingAspect`: context được khôi phục, span đóng đúng một lần ở mọi đường, không giữ context của invocation khác.
- `RetryAspect`: chỉ retry lỗi đủ điều kiện; bảo toàn deadline và idempotency; không nhân side effect khi không an toàn.

Không cần dựng toàn bộ application để kiểm thử một policy đơn lẻ. Nếu test phải cần mọi subsystem, aspect có thể có quá nhiều dependency.

### 13.2. Kiểm thử composition

Test riêng lẻ xanh chưa đủ. Phải xác minh thứ tự thực:

| Tình huống | Điều cần khẳng định |
| --- | --- |
| Authorization deny + metrics | Định nghĩa request/rejection được đếm đúng; domain không chạy |
| Cache hit sau khi quyền thu hồi | Không bypass kiểm tra quyền |
| Retry sau lỗi ở transaction | Transaction mới/rollback đúng policy |
| Trace khi gọi lồng nhau | Parent-child context đúng, không tạo span trùng |
| Fallback và exception mapping | Caller-visible outcome và attempt outcome được ghi khác tên |
| Async completion/timeout | Đóng observer đúng vòng đời, không phát hai terminal event |
| Self-call bypass proxy | Không làm mất invariant nghiệp vụ hoặc bảo vệ dữ liệu |
| Exporter chậm/không khả dụng | Giới hạn overhead/queue của telemetry |

### 13.3. Kiểm thử thay đổi có chủ đích

Thử thay policy logging trong test:

1. Đổi định dạng trace.
2. Xác nhận các test tracing cần cập nhật.
3. Kiểm thử authorization, business invariant, transaction và retry không đổi.

Sau đó thử thay quyền từ `operator` sang tập vai trò cấu hình: test authorization thay đổi đúng; metric semantics không vô tình đổi. Nếu cùng một thay đổi đòi sửa mọi Aspect, cân nhắc lại coupling, interface và vị trí state chung.

### 13.4. Performance và concurrency

AOP thêm code tại các join point được chặn; nhiều aspect lồng nhau có thể tăng chi phí gọi, cấp phát, context switch hoặc contention. Benchmark cả:

- Độ trễ bình thường và p99 với policy bật/tắt.
- Lượng allocation, kích thước context và overhead từng advice.
- Chi phí của pointcut matching và reflection (nếu có).
- Tác động của logging/metrics sink bị quá tải.
- Race khi aspect instance được dùng chung bởi nhiều luồng.
- Số lần invoke thực tế khi kết hợp retry, cache và transaction.

Không bỏ kiểm tra quyền vì benchmark thấy tốn kém; chọn kiến trúc tối ưu mà vẫn bảo đảm hợp đồng bảo mật.

## 14. Quy trình tái cấu trúc một aspect đang ôm quá nhiều việc

Một trình tự thực dụng giúp tách dần mà không thay đổi hành vi ngoài ý muốn:

1. **Liệt kê hành vi hiện có:** tất cả pointcut, before/around/after, exception mapping, retry, caching, transaction, audit và telemetry.
2. **Ghi lại observable contract:** khi nào target chạy, chạy bao nhiêu lần, thứ tự thay đổi trạng thái, exception/return, resource cleanup và metric.
3. **Nhóm theo lý do thay đổi:** policy nào có owner/cấu hình/vòng đời riêng; đánh dấu chỗ cần composition ordering.
4. **Viết characterization tests:** bảo toàn hành vi **hợp lệ** hiện tại, đồng thời xác định các anti-pattern phải sửa theo yêu cầu đã phê duyệt.
5. **Trích xuất dependency chung đúng tầng:** `PolicyEvaluator`, `TraceContext`, `Clock`, `CacheAdapter`; không kéo logic nghiệp vụ vào “common helper”.
6. **Tách một policy một lần:** ví dụ telemetry trước, rồi authorization, tránh thay mọi chính sách cùng lúc.
7. **Khai báo order rõ:** xem xét cache hit, denial, retry, commit và callback async ở cả đường success/failure.
8. **Chạy test integration và benchmark:** xác minh chưa mất join point, chưa đếm trùng, chưa thay exception hoặc side effect.
9. **Xóa nhánh obsolete:** sau khi policy đã tách, bỏ cờ, guard và code cũ không dùng, tránh hai đường xử lý song song.
10. **Tài liệu hóa owner và contract:** mô tả mỗi aspect chịu trách nhiệm gì, không chịu trách nhiệm gì và cách composition.

Trong refactor, **không mặc định mọi hành vi cũ đều đúng**. Nếu God Aspect đang nuốt `SecurityException`, đó là bug cần có quyết định sửa hợp đồng và bài kiểm thử phù hợp; việc giữ hành vi sai chỉ để “không đổi regression” có thể không chấp nhận được.

## 15. Dấu hiệu kiến trúc khỏe mạnh

Một aspect được thiết kế tốt thường có những đặc điểm sau:

- Tên phản ánh một policy cụ thể, ví dụ `LatencyMetricsAspect`, `AuthorizationEnforcementAspect`.
- Tập join point có chung semantics và có bài test chứng minh coverage.
- Các advice và helper phục vụ cùng một invariant hoặc một lý do thay đổi.
- Ít dependency, giao diện hẹp; dependency là interface có contract rõ.
- State theo đúng vòng đời và được xử lý an toàn khi concurrency/async.
- Exception/return không bị thay ngoài phạm vi policy công bố.
- Thứ tự với aspect khác được cấu hình, tài liệu hóa và kiểm thử.
- Không vô tình bypass bảo mật khi cache hit, retry, callback hoặc direct call.
- Metrics/log mô tả được policy nào gây thay đổi và số lần chạy.
- Khi đổi một chính sách, phạm vi code và test cần đổi có thể dự đoán được.

### 15.1. Checklist nhanh khi review

| Câu hỏi | Dấu hiệu tốt | Dấu hiệu cần xem lại |
| --- | --- | --- |
| Vì sao aspect thay đổi? | Một chủ sở hữu/policy rõ | Nhiều nhóm yêu cầu không liên quan |
| Pointcut chọn gì? | Join point cùng contract | Chọn theo tên quá rộng và nhiều ngoại lệ |
| Advice gọi target mấy lần? | Theo policy đã định | Ngầm retry hoặc bỏ qua proceed |
| State thuộc ai? | Invocation/adapter phù hợp | Trộn principal, retry count và cache state |
| Thêm tính năng mới? | Mô-đun riêng hoặc mở rộng trách nhiệm hợp lý | Thêm `if` vào aspect tổng |
| Tách một policy? | Không phải sửa các policy không liên quan | Phải sửa cả authorization, cache, metric |
| Kiểm thử được không? | Fake target + fake dependency | Cần dựng mọi hạ tầng |
| Bảo mật/consistency? | Có integration tests cho order | Dựa trên giả định thứ tự ngẫu nhiên |

**Kết luận thực hành:** một aspect có SRP tốt khi **mọi phần của nó cùng phục vụ một lý do thay đổi**, không phải khi nó ngắn nhất hoặc chỉ có một advice. Cohesion cao giúp chính sách dễ hiểu, dễ sửa và dễ kiểm thử. Khi cần nhiều aspect, hãy coi **thứ tự composition và hợp đồng nghiệp vụ** là phần thiết kế bắt buộc để các mô-đun độc lập phối hợp đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-004">Phụ thuộc ẩn giữa mã nghiệp vụ với cấu hình pointcut</a>

<details>
<summary>Xem chi tiết</summary>

# Phụ thuộc ẩn giữa mã nghiệp vụ với cấu hình pointcut trong Aspect-Oriented Programming (AOP)

## 1. Vấn đề kiến trúc: mã không gọi advice nhưng vẫn phụ thuộc vào advice

**Aspect-Oriented Programming (AOP)** tách các mối quan tâm cắt ngang như logging, metrics, tracing, transaction, retry, audit và kiểm soát truy cập khỏi thân phương thức nghiệp vụ. Một **pointcut** xác định tập *join point* được chọn; **advice** thêm hành vi tại những điểm ấy; cơ chế **weaving**, instrumentation hoặc proxy kết nối advice với mã chạy thật.

Điểm dễ bị bỏ qua là **phụ thuộc vẫn tồn tại dù không có lời gọi trực tiếp trong source code**. Một phương thức có thể hoạt động đúng chỉ khi advice quanh nó thực hiện một việc ngầm định: mở transaction, lưu audit event, truyền context, kiểm tra quyền, biến đổi lỗi, đo thời gian hay retry. Nếu pointcut ngừng khớp sau lần đổi tên/package/chữ ký, phương thức vẫn có thể biên dịch và các unit test trực tiếp vẫn chạy, nhưng hợp đồng vận hành hoặc an toàn đã bị phá vỡ.

Đây là **phụ thuộc ẩn giữa mã nghiệp vụ và cấu hình pointcut**: cấu hình chọn join point dựa trên cấu trúc chương trình, trong khi hành vi thực tế của chương trình phụ thuộc ngược lại vào cấu hình đó. Compiler thường không kiểm tra được toàn bộ mối quan hệ này, nhất là với biểu thức chọn điểm ở runtime, file cấu hình hoặc metadata được đọc khi khởi động.

Ví dụ:

~~~text
Ý định: mọi thao tác thay đổi trạng thái đơn hàng phải có audit.

Pointcut: tên phương thức bắt đầu bằng "submit".
Ban đầu: submitOrder(...) được chọn -> audit chạy.
Refactor: đổi thành placeOrder(...) -> nghiệp vụ vẫn chạy.
Hệ quả: pointcut không còn chọn phương thức -> audit mất âm thầm.
~~~

Sai sót này không nhất thiết hiện ra bằng một exception. Đặc biệt nguy hiểm khi advice là một phần của ranh giới bảo mật, transaction hoặc cơ chế bảo đảm tính nhất quán.

**Mục tiêu:** hiểu chính xác phụ thuộc ẩn hình thành thế nào; phân loại rủi ro khi refactor; dùng một thiết kế khai báo chính sách rõ ràng và các phép kiểm tra tự động để phát hiện mất/nhân đôi advice; phân biệt những gì AOP thật sự bảo đảm với những gì kiến trúc vẫn phải bảo vệ ở boundary nghiệp vụ.

## 2. Những khái niệm cần phân biệt

| Khái niệm | Ý nghĩa | Liên quan tới phụ thuộc ẩn |
| --- | --- | --- |
| Join point | Điểm mà cơ chế AOP hỗ trợ can thiệp, chẳng hạn lời gọi hoặc thực thi phương thức | Nếu đường chạy không đi qua join point được chọn, advice không có tác dụng |
| Pointcut | Điều kiện nhận diện join point: tên, kiểu, package, annotation, chữ ký, context... | Có thể phụ thuộc vào cách đặt tên hoặc tổ chức mã thay vì ý nghĩa nghiệp vụ |
| Advice | Hành vi trước/sau/quanh join point, khi trả kết quả hoặc ném lỗi | Thay đổi đặc tính quan sát, an toàn hoặc semantics của lời gọi |
| Weaving / proxy | Cơ chế gắn advice vào execution | Quyết định những lời gọi nào có thể bị chặn trên thực tế |
| Cross-cutting contract | Cam kết áp dụng cho nhiều thao tác, như “mọi lệnh ghi phải được audit” | Hợp đồng có thể không hiện diện trong chữ ký phương thức |
| Pointcut coverage | Phần giao giữa tập join point **cần** áp dụng và tập join point **thực tế** được chọn | Mất coverage hoặc chọn thừa đều là lỗi kiến trúc |

**Pointcut không phải policy nghiệp vụ.** Một biểu thức “mọi phương thức bắt đầu bằng `save`” là cách chọn code theo hình dạng. Nó không bảo đảm mọi thao tác có side effect đều nằm trong tập đó. Ngược lại, tên `savePreview` chưa chắc đại diện cho một hành động ghi trạng thái quan trọng.

Điểm quan trọng hơn: **“method được chọn” khác “advice đã chạy cho đúng đường nghiệp vụ”**. Một pointcut có thể khớp một phương thức trên lý thuyết nhưng đường gọi thực tế đi trực tiếp vào target, đi qua lớp khác, chạy trên thread callback hoặc không đi qua proxy. Cần kiểm tra cả tập khớp và đường thực thi thật.

## 3. Hai chiều của phụ thuộc và chiều thứ ba giữa các aspect

### 3.1 Aspect phụ thuộc vào cấu trúc mã nghiệp vụ

Một pointcut thường biểu diễn các điều kiện kiểu:

~~~text
execution(package.orders.*.submit*(..))
annotation(Audited)
within(package.services.*)
method_signature = (name, parameter_types, declaring_type)
~~~

Đây là **biểu thức khái niệm** để diễn đạt các kiểu lựa chọn, không phải cú pháp chung do mọi engine AOP đều hỗ trợ. Nếu đổi `package.orders` thành `package.checkout` hoặc `submitOrder` thành `placeOrder`, việc chọn có thể thay đổi dù logic bên trong không đổi.

Điều này là coupling từ cấu hình aspect tới *hình dạng* codebase: cấu hình cần biết package, modifier, khai báo method hoặc metadata nào tồn tại.

### 3.2 Mã nghiệp vụ phụ thuộc vào tác dụng của advice

Ví dụ phương thức gọi repository giả định transaction đã được mở bởi advice. Nếu bị gọi trực tiếp mà không qua cơ chế AOP, kết quả có thể khác, side effect không được rollback hoặc kiểm soát truy cập bị bỏ qua. Đây là coupling theo chiều ngược lại: mã nghiệp vụ không import aspect, nhưng hành vi đúng của nó lại trông chờ side effect từ aspect.

Cần phân biệt **hỗ trợ quan sát** và **bất biến bắt buộc**:

- Mất một phép đo latency có thể làm giảm khả năng chẩn đoán nhưng không nhất thiết làm thay đổi kết quả nghiệp vụ.
- Mất audit bắt buộc, kiểm tra quyền, transaction hoặc ràng buộc nhất quán có thể là lỗi nghiêm trọng.
- Retry advice có thể làm tăng số lần chạy mã gốc và side effect; nếu nghiệp vụ ngầm trông đợi retry, đó là một phụ thuộc dễ bị bỏ sót.
- Một exception translator có thể làm caller kỳ vọng loại lỗi khác với loại mà phương thức gốc ném ra.

**Không nên biến một hiệu ứng ngầm thành điều kiện an toàn duy nhất nếu kiến trúc còn các đường truy cập không được bảo vệ.** Đối với ranh giới quan trọng, cần một điểm cưỡng chế thống nhất và/hoặc kiểm soát bổ sung tại tầng sở hữu dữ liệu/tài nguyên.

### 3.3 Các aspect phụ thuộc vào thứ tự nhau

Giả sử cùng join point có:

- Authorization advice.
- Transaction advice.
- Retry advice.
- Audit advice.
- Metrics advice.

Thứ tự có thể quyết định việc kiểm tra quyền diễn ra trước hay sau side effect, mỗi lần retry có transaction riêng hay dùng chung, audit đếm một request logic hay từng attempt, metrics đo thời gian nghiệp vụ hay cả retry, và exception nào được quan sát.

Ví dụ ý tưởng:

~~~text
Metrics(Retry(Transaction(Operation)))
  -> metrics đo cả thời gian retry.

Retry(Metrics(Transaction(Operation)))
  -> metrics có thể ghi từng attempt bên trong retry.
~~~

Không có một thứ tự mặc định đúng cho mọi bài toán. Nhưng **thứ tự là một phần của hợp đồng thực thi**, phải được khai báo, kiểm tra và đưa vào test tích hợp. Nếu chỉ đổi ưu tiên aspect mà dashboard thay đổi đơn vị đếm, hệ thống đã thay đổi semantics của phép đo dù mã nghiệp vụ vẫn như cũ.

## 4. Cách pointcut trở nên giòn khi mã được refactor

### 4.1 Đổi tên phương thức

Pointcut dùng `get*`, `save*`, `delete*`, `submit*` có thể mất match khi đổi động từ, dùng alias hoặc gom API. Tên cũng có thể mang ý nghĩa khác với hành vi thật. **Không dùng prefix tên method làm nguồn quyết định quyền truy cập hoặc transaction bắt buộc.**

### 4.2 Đổi package hoặc chuyển lớp

Pointcut giới hạn `within(com.company.orders..*)` có thể bỏ sót phương thức khi di chuyển nó sang module mới. Ngược lại, thêm một utility class vào package được chọn có thể khiến advice bắt cả code không dự kiến.

### 4.3 Đổi chữ ký, overload hoặc generic

Thay `save(String id)` bằng `save(OrderId id)`, thêm tham số context, overload method, hoặc tạo bridge method do generic có thể thay đổi cách engine nhận diện chữ ký. Java reflection trên interface có thể thấy annotation khác với reflection trên implementation. Không suy ra từ một method name rằng mọi overload có cùng chính sách.

### 4.4 Đổi visibility, phương thức static/final và cơ chế weaving

Khả năng chặn method `private`, `final`, `static`, constructor, field access, self-invocation hoặc lời gọi trực tiếp phụ thuộc engine và cách gắn advice. Một cơ chế dùng proxy interface sẽ có khả năng khác cơ chế weaving bytecode. Không ghi kết luận “AOP luôn chặn mọi lời gọi” hoặc “AOP luôn bỏ qua mọi phương thức nội bộ” mà không xác nhận công nghệ đang dùng.

### 4.5 Chuyển đồng bộ sang bất đồng bộ

Trước refactor, phương thức trả kết quả sau khi hoàn thành công việc. Sau refactor, nó trả `CompletionStage` hoặc publisher gần như ngay lập tức. Advice quanh lời gọi cũ giờ chỉ nhìn thấy lúc **trả đối tượng biểu diễn tác vụ**, chưa phải lúc tác vụ hoàn tất.

Hệ quả có thể là transaction đóng trước khi background task thao tác dữ liệu, metrics báo success quá sớm, hoặc authorization context không còn hiện diện khi callback dùng tài nguyên. Nếu chính sách cần theo dõi vòng đời async, phải chọn rõ join point của completion, subscription, terminal và cancellation, đồng thời quản lý context.

### 4.6 Thay đổi đường gọi và bypass

Một lớp ban đầu chỉ được gọi qua proxy nhưng sau refactor lại được tạo trực tiếp bằng `new`, hoặc code nội bộ gọi `this.operation()`, có thể đi vòng điểm chặn. Test chỉ gọi trực tiếp vào implementation sẽ không xác nhận AOP hoạt động. Phải dùng đúng entrypoint mà production gọi và kiểm tra thêm đường bypass có thể xảy ra.

## 5. Phân biệt thiếu coverage và chọn thừa

Gọi:

- `R` là tập join point **bắt buộc** phải áp dụng một chính sách.
- `M` là tập join point **được pointcut chọn** trong runtime/biên dịch thực tế.

Khi đó:

- **False negative:** phần thuộc `R` nhưng không thuộc `M`. Ví dụ thao tác ghi không được audit.
- **False positive:** phần thuộc `M` nhưng không thuộc `R`. Ví dụ pointcut mở transaction cho một hàm chuyển đổi dữ liệu thuần túy.
- **Đúng mục tiêu:** phần giao `R ∩ M`, nhưng vẫn cần kiểm tra thời điểm và thứ tự chạy advice.

Có thể minh họa:

~~~text
Bắt buộc bảo vệ R:  [submit] [place] [cancel]
Pointcut hiện tại M: [submit] [status] [helper]

Thiếu: [place] [cancel]
Chọn thừa: [status] [helper]
~~~

False positive cũng có hại: đo lặp, tăng CPU, log dữ liệu nhạy cảm, mở transaction không cần thiết, đổi loại exception hoặc khiến chức năng bị từ chối sai. Một điểm cắt rất rộng **không bảo đảm an toàn hơn** nếu nó thay đổi semantics ngoài dự kiến.

Với chính sách bảo mật, không nên dựa vào việc “không có lỗi trong log” để suy ra coverage đủ: **một advice không bao giờ chạy sẽ không tự ghi log rằng mình đã bị bỏ qua**.

## 6. Ví dụ Java 17+ độc lập: lỗi do pointcut theo tên

Ví dụ dùng **JDK dynamic proxy** để minh họa một điểm cắt theo tên và một điểm cắt có khai báo tường minh. Hai method `submit` và `place` đều là thao tác nghiệp vụ có yêu cầu audit. Pointcut theo tên chỉ bắt `submit`, nên `place` chạy mà mất audit; phiên bản dùng `@Audited` chọn cả hai theo metadata đã khai báo.

**Tệp: `PointcutCouplingDemo.java`**

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class PointcutCouplingDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Audited {}

    public interface Orders {
        @Audited
        String submit(String id);

        @Audited
        String place(String id);

        String status(String id);
    }

    public static final class OrdersImpl implements Orders {
        @Override
        public String submit(String id) {
            return "saved " + id;
        }

        @Override
        public String place(String id) {
            return "saved " + id;
        }

        @Override
        public String status(String id) {
            return "status " + id;
        }
    }

    static Orders decorate(Orders target, Predicate<Method> pointcut) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() != Object.class
                    && pointcut.test(method)) {
                System.out.println("audit " + method.getName());
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                // Giữ nguyên nguyên nhân lỗi nghiệp vụ.
                throw ex.getCause();
            }
        };

        return (Orders) Proxy.newProxyInstance(
            Orders.class.getClassLoader(),
            new Class<?>[] { Orders.class },
            handler
        );
    }

    public static void main(String[] args) {
        Orders target = new OrdersImpl();

        Orders byName = decorate(
            target, method -> method.getName().startsWith("submit")
        );
        System.out.println("-- name-based pointcut --");
        System.out.println(byName.submit("A"));
        System.out.println(byName.place("B"));

        Orders byContract = decorate(
            target, method -> method.isAnnotationPresent(Audited.class)
        );
        System.out.println("-- explicit annotation --");
        System.out.println(byContract.submit("C"));
        System.out.println(byContract.place("D"));
        System.out.println(byContract.status("E"));
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac PointcutCouplingDemo.java
java PointcutCouplingDemo
~~~

**Stdout xác định:**

~~~text
-- name-based pointcut --
audit submit
saved A
saved B
-- explicit annotation --
audit submit
saved C
audit place
saved D
status E
~~~

Đọc kết quả:

1. `byName.submit("A")` in `audit submit` trước `saved A` vì phương thức có tên khớp prefix.
2. `byName.place("B")` vẫn thực thi business code và trả `saved B` nhưng không có audit; đây là **mất hành vi ngầm** mà việc nhìn giá trị trả về không phát hiện được.
3. `byContract.submit("C")` và `byContract.place("D")` đều có `@Audited` trên **method của interface**, do đó proxy nhận metadata và ghi audit.
4. `status("E")` không có annotation và không được audit theo chính sách minh họa.

Cần hiểu đúng giá trị của annotation: **khai báo tường minh giúp phản ánh ý định**, nhưng **không tự bảo đảm đầy đủ**. Nếu thêm phương thức ghi mới rồi quên `@Audited`, phiên bản annotation cũng có thể bỏ sót. Muốn bảo đảm mọi operation bắt buộc phải được bảo vệ, phải có danh sách chính sách chuẩn và bài kiểm tra đối chiếu coverage. Với việc kiểm soát quyền, “không có annotation” ở ranh giới cần bảo vệ phải dẫn tới **từ chối hoặc lỗi cấu hình**, thay vì “cho phép vì không thấy rule”.

**Giới hạn của mẫu:** `decorate` tạo một proxy trên interface. Nó chỉ áp dụng advice cho lời gọi đi qua proxy đó; gọi `target.place("B")` trực tiếp sẽ không qua advice. Annotation được đặt trên interface vì `Method` mà JDK proxy cung cấp cho handler mô tả phương thức interface. Cơ chế AOP khác có thể lấy metadata từ implementation và có quy tắc kế thừa riêng. Ví dụ in audit trên console nhằm dễ kiểm chứng; hệ production cần audit sink có bảo đảm độ bền, điều kiện thất bại và chính sách bảo vệ dữ liệu phù hợp.

## 7. Biến phụ thuộc ẩn thành hợp đồng có thể kiểm tra

### 7.1 Định nghĩa danh mục các operation cần áp chính sách

Thay vì để một regex âm thầm quyết định tác vụ nào quan trọng, lập một **operation catalog** hoặc manifest:

~~~yaml
operations:
  orders.submit:
    audit: required
    authorization: order.write
    transaction: required
  orders.place:
    audit: required
    authorization: order.write
    transaction: required
  orders.status:
    audit: optional
    authorization: order.read
    transaction: none
~~~

Đây là **YAML minh họa kiến trúc**, không phải file cấu hình của một engine cụ thể. Mỗi operation có ID nghiệp vụ ổn định và yêu cầu chính sách rõ. Một lớp ánh xạ từ chữ ký method hoặc annotation sang operation ID phải được xác minh, đặc biệt khi đổi tên/refactor.

Manifest có thể được dùng trong bài kiểm tra khởi động hoặc build để so sánh:

- Operation nào bắt buộc có audit, authorization, transaction?
- Pointcut runtime hiện chọn những join point nào?
- Có operation bị bỏ sót, chọn thừa, trùng policy hoặc thiếu mapping không?
- Có method mới cần phân loại mà chưa có mục trong manifest không?

Khi câu trả lời không đúng, phát cảnh báo **có thể kiểm chứng** hoặc dừng khởi động đối với invariant bắt buộc. Không chỉ in một thông báo rồi để hệ thống bảo mật vận hành trong trạng thái mơ hồ.

### 7.2 Annotation với nội dung giàu ngữ nghĩa

Annotation tường minh như `@RequiresPermission("order.write")` hoặc `@Audited(category = "order")` bền hơn phụ thuộc vào prefix tên, vì nó mang **ý định** sang gần code nghiệp vụ. Tuy vậy:

- Không dùng annotation như bằng chứng người gọi đã có quyền; advice phải kiểm tra principal, action, resource và context với dữ liệu tin cậy.
- Cần quy định annotation đặt trên interface, implementation hay cả hai; kiểm tra inheritance, overload, generic bridge và default method theo công cụ thực tế.
- Annotation ở sai vị trí hoặc không được công cụ đọc có thể vẫn tạo miss. Test coverage phải dùng đường gọi thực.
- Một operation chuyển ý nghĩa từ “chỉ đọc” sang “cập nhật” cần thay policy ngay cả khi annotation vẫn còn.
- Nhiều annotation có thứ tự tương tác: authorization, retry, transaction, audit phải có hợp đồng thực thi rõ.

### 7.3 Biên cưỡng chế chính sách rõ ràng

Với thao tác cần **bảo đảm an toàn**, không nên chỉ dựa vào một matcher động không được kiểm thử. Kiến trúc có thể lựa chọn:

- Một cổng xử lý lệnh (`command boundary`) bắt buộc đưa mọi lệnh ghi qua middleware có policy rõ.
- Một interface truy cập tài nguyên được bao bọc và không tiết lộ đối tượng gốc cho caller không tin cậy.
- Kiểm tra quyền và ràng buộc nhất quán tại nơi thực sự ghi dữ liệu, transaction hoặc kho tài nguyên.
- Build-time weaving kết hợp kiểm tra điểm cắt và kiểm tra end-to-end; cần xác nhận kiểu join point thực tế chứ không suy từ tên công nghệ.

Các lựa chọn này có mục tiêu chung là **người sửa code nhìn thấy hợp đồng cần giữ** và **đường bypass bị giới hạn, phát hiện hoặc chặn**.

## 8. Kiểm soát thay đổi pointcut như một thay đổi hành vi

Một thay đổi cấu hình pointcut có thể có tác động tương đương sửa code nghiệp vụ. Review cần trả lời:

| Loại thay đổi | Nguy cơ | Kiểm tra đề xuất |
| --- | --- | --- |
| Đổi tên method | Mất match hoặc thêm match do prefix | Diff tập join point trước/sau |
| Chuyển package | Mất matcher theo package | Test coverage/operation catalog |
| Thêm overload | Matcher chọn thêm hoặc bỏ một chữ ký | Kiểm tra signature chính xác |
| Di chuyển annotation | Runtime không đọc được metadata | Kiểm tra annotation resolution trên đường gọi thật |
| Thay interface/implementation | Proxy không còn chặn lời gọi dự định | Integration test qua entrypoint production |
| Đổi advice order | Metrics, retry, transaction, authorization đổi semantics | Test hợp đồng kết quả và thứ tự side effect |
| Chuyển sang async | Advice kết thúc trước công việc | Test completion, lỗi, hủy và context propagation |
| Mở rộng wildcard | Instrument utility/hạ tầng, recursion hoặc overhead | So sánh tập chọn và performance test |
| Thay wiring hoặc tạo target trực tiếp | Bypass advice | Test từ đầu vào đến điểm sử dụng resource |

Nếu công cụ AOP hỗ trợ liệt kê match, xuất danh sách đó vào CI và so sánh với tập kỳ vọng. Nếu không, viết phép thử chạy một đường đại diện cho từng operation và dùng một **fake advice/probe** xác nhận số lần kích hoạt, thứ tự và dữ liệu join point. Cần test âm tính để chứng minh operation ngoài scope không bị can thiệp.

**Kiểm thử unit trực tiếp vào target chưa đủ.** Nó xác minh logic gốc, nhưng không xác minh weaving, proxy wiring, pointcut, thứ tự aspect, context hay exception translation. Ít nhất một lớp integration test phải gọi từ boundary thật.

## 9. Thiết kế test đặc biệt cho phụ thuộc ẩn

### 9.1 Bất biến về coverage

Đối với mỗi operation bắt buộc audit:

1. Khi được gọi qua entrypoint hợp lệ, audit advice phải chạy đúng số lần theo hợp đồng.
2. Khi code bị đổi tên hoặc thay chữ ký nhưng vẫn cùng operation ID, yêu cầu audit vẫn còn.
3. Một operation ngoài phạm vi không nhận audit ngoài ý muốn.
4. Khi thiếu binding bắt buộc, test hoặc khởi động phải báo lỗi thay vì bỏ qua im lặng.

Đối với authorization, bổ sung kiểm tra rằng bị từ chối thì **thao tác gốc không được chạy**, thông tin từ chối không làm lộ dữ liệu riêng và mọi đường gọi tài nguyên nhạy cảm đều được bảo vệ.

### 9.2 Bất biến về thứ tự và side effect

Với phối hợp authorization–transaction–retry–audit, dùng probe ghi trình tự các sự kiện. Chẳng hạn:

~~~text
auth-check
transaction-begin
business-start
business-end
transaction-commit
audit-record
~~~

Thứ tự trên chỉ là **một hợp đồng minh họa**, không phải quy tắc phổ quát. Khi đổi thứ tự aspect, test phải đánh giá quy tắc nghiệp vụ thực: authorization cần chạy trước side effect; audit có thể phải gắn với mỗi attempt hoặc kết quả commit tùy yêu cầu; transaction có thể bao quanh từng attempt hay toàn hoạt động. Quan trọng là test **có kỳ vọng tường minh**.

### 9.3 Bất biến về đường bypass

- Tạo đối tượng qua cơ chế wiring thông thường và xác nhận lời gọi được chặn.
- Thử tham chiếu trực tiếp vào target nếu kiến trúc có thể tạo ra nó; test phải phát hiện hoặc ràng buộc quyền truy cập.
- Thử self-invocation, callback, background job, reflection hoặc API đi vào từ đường khác nếu chúng thực sự có trong ứng dụng.
- Với multiple classloaders hoặc dynamic proxy, kiểm tra đúng interface/class và classloader cần áp dụng.
- Thử exception, cancellation và các đường kết thúc sớm: advice có phát side effect thiếu hoặc nhân đôi không?

### 9.4 Khi refactor

Lập test mang tính *refactoring regression*: đổi tên một method giả lập, đổi package, thêm overload hoặc chuyển API sync sang async trong nhánh thử nghiệm, rồi quan sát coverage và hợp đồng điều khiển. Nếu pointcut không còn khớp thì test phải thất bại rõ ràng. Đây là cách hữu ích để phát hiện những phụ thuộc mà compiler thông thường không nhìn thấy.

## 10. Thứ tự thực thi: vì sao phụ thuộc dễ sinh lỗi ngầm?

Giả sử có quanh cùng một thao tác:

~~~text
Authorization -> Retry -> Transaction -> Business
~~~

Nếu retry bên ngoài transaction, mỗi attempt có thể có transaction riêng. Nếu transaction ở ngoài retry, nhiều attempt có thể nằm trong một transaction, dẫn tới semantics rollback và lock khác. Nếu metrics nằm ngoài retry, nó đo latency tổng thể; nếu metrics nằm trong, nó đo mỗi attempt.

Những điều cần được nêu rõ trong tài liệu kiến trúc:

- Advice nào được phép *không gọi* thao tác gốc? Ví dụ authorization từ chối hoặc cache hit.
- Advice nào được phép gọi lại thao tác gốc? Retry có giới hạn và chỉ khi nghiệp vụ idempotent hoặc đã giải quyết side effect.
- Exception nào được chuyển đổi? Caller mong đợi loại lỗi nào?
- Điểm bắt đầu/kết thúc một transaction hoặc một phép đo là gì?
- Mỗi attempt và mỗi logical request có cùng policy không?
- Việc thay đổi thứ tự aspect có cần đánh giá migration và cập nhật dashboard/test không?

Một hệ thống có thể chạy “không lỗi” nhưng báo số liệu sai sau thay đổi: ban đầu metrics đếm một request logic, sau đó đếm từng retry attempt. Đây cũng là lỗi do phụ thuộc ẩn, dù không có exception.

## 11. AOP và tác vụ bất đồng bộ: nơi coupling dễ bị bỏ sót

Khi một method `process()` đổi từ:

~~~java
Result process(Command command);
~~~

thành:

~~~java
CompletionStage<Result> process(Command command);
~~~

các chữ ký trên là **minh họa API**, không phải chương trình độc lập. Nếu advice vẫn ghi nhận success trong `after returning`, nó chỉ biết method đã trả về stage. Stage đó có thể thất bại sau vài giây.

Với một publisher sự kiện, lời gọi tạo publisher khác với `subscribe`, từng `onNext`, terminal signal và `cancel`. Một method có thể tạo publisher nhưng chưa thực thi nghiệp vụ trước khi có subscriber. Vì thế:

- Tracing/metrics cần quyết định đo lời gọi tạo đối tượng, hoàn tất của stage, hay vòng đời subscription.
- Transaction/session/resource context không được giả định tự kéo dài qua callback và thread khác.
- Authorization có thể cần thực thi lại tại thời điểm truy cập tài nguyên nếu quyền có thể thay đổi.
- Advice quan sát stream phải bảo toàn `request(n)`, thứ tự, error và cancellation; không tạo subscription phụ hoặc queue không giới hạn.
- Một context nằm trong `ThreadLocal` ở thread gọi không tự lan qua executor; cần propagation tường minh và đóng scope đúng vòng đời.

Không có một cơ chế advice chung tự bảo đảm tất cả semantics này. **Chuyển từ sync sang async là thay đổi hợp đồng**, phải review pointcut và vòng đời của các aspect liên quan.

## 12. Những mức độ phụ thuộc và cách ứng xử

| Mức | Ví dụ | Yêu cầu khi thiếu advice |
| --- | --- | --- |
| Quan sát tùy chọn | Đo thời gian phục vụ dashboard | Ứng dụng có thể tiếp tục; phát hiện thiếu telemetry qua coverage/health |
| Quan sát cần tuân thủ | Audit lệnh ghi bắt buộc | Phải có policy fail-closed hoặc kiểm soát bổ sung đã được duyệt |
| Kiểm soát truy cập | Kiểm tra subject/action/resource/context | Không cho phép thao tác không được xác minh quyền |
| Tính nhất quán dữ liệu | Transaction/lock/atomicity | Bảo vệ tại biên dữ liệu hoặc đảm bảo mọi đường đều qua enforcement |
| Điều khiển tải | Rate limit/concurrency limit | Mọi entrypoint liên quan phải chịu cùng chính sách, gồm retry và async |
| Thay đổi semantics | Caching, retry, exception translation | Test giá trị, side effect, lỗi và retry contract |

Đừng dùng một tiêu chí chung “có logging là ổn” cho cả những hành vi bắt buộc và tùy chọn. Mỗi phụ thuộc cần **mức độ bảo đảm** tương ứng: review, test coverage, runtime assertion, hoặc bảo vệ bổ sung tại resource boundary.

## 13. Định nghĩa chính xác metadata và trách nhiệm ownership

Một cách hạn chế coupling là phân chia rõ:

- **Nhóm nghiệp vụ sở hữu ý nghĩa operation:** một lệnh là đọc, ghi, publish, thanh toán hay hành động nhạy cảm.
- **Nhóm platform/AOP sở hữu cơ chế thực thi:** pointcut, wiring, propagation, logging, đo và kết hợp advice.
- **Nhóm policy/bảo mật sở hữu quy tắc cho phép hoặc yêu cầu audit:** action, subject/resource, thời điểm kiểm tra, trường hợp ngoại lệ.

Mỗi thay đổi operation phải đi kèm kiểm tra policy mapping. Nếu có thể, tự động hóa bằng một danh mục method đã đăng ký so với manifest. Những key ổn định theo nghiệp vụ (ví dụ `order.place`) giúp giảm phụ thuộc vào cấu trúc source code, nhưng chúng cũng phải được version hóa và không được dùng như bằng chứng cấp quyền.

Khi đổi cấu hình điểm cắt, cần có thông tin **trước/sau**: số join point được match, danh sách mới và mất, số lời gọi advice khi chạy workload đại diện, cùng ảnh hưởng hiệu năng. Review “đổi một dòng regex” vẫn cần nghiêm túc nếu dòng đó chọn hàng nghìn điểm thực thi.

## 14. Chọn pointcut như thế nào cho bền vững?

Không có một biểu thức duy nhất phù hợp mọi hệ thống. Một số phương án:

**Theo tên/signature:** nhanh, ít annotation, hữu ích cho các quy ước mang tính quan sát nhẹ; dễ bị refactor làm drift. Dùng khi có test kiểm tra danh sách khớp và hậu quả bỏ sót không nguy hiểm.

**Theo package/class:** thuận tiện nếu module thực sự trùng với boundary cần áp dụng; có thể chọn thừa khi module chứa hạ tầng hoặc bỏ sót khi di chuyển class.

**Theo annotation/metadata khai báo:** thể hiện ý định gần code, giảm nhầm lẫn do tên; cần kiểm tra coverage, vị trí annotation, inheritance và thay đổi semantics.

**Theo manifest/operation registry:** khai báo chính sách tập trung, có thể kiểm tra đầy đủ; đòi hỏi giữ mapping luôn cập nhật và giải quyết mâu thuẫn giữa registry với code.

**Theo boundary kiến trúc:** middleware hoặc cổng xử lý lệnh không cho caller đi vòng, dễ gắn invariant; có thể cần điều chỉnh thiết kế module và phân quyền truy cập target. Kết hợp với kiểm soát ở data layer khi policy yêu cầu bảo đảm mạnh.

Một chiến lược tốt là dùng **pointcut để áp dụng một hợp đồng được khai báo**, sau đó dùng CI/integration test để chứng minh mọi đường quan trọng đã đi qua hợp đồng ấy. Cần cho phép các operation công khai thực sự bằng chính sách công khai **tường minh** thay vì “không match thì tự được phép”.

## 15. Các lỗi thường gặp

**“Mã không import aspect, nên hai phần độc lập.”** Phụ thuộc runtime vẫn hiện diện nếu thiếu advice làm mất audit, transaction, quyền hoặc semantics cần thiết.

**“Refactor đổi tên method mà compile được là an toàn.”** Compiler thường không biết biểu thức pointcut động và hợp đồng chọn join point. Cần kiểm tra coverage sau refactor.

**“Dùng wildcard rộng sẽ không bao giờ bỏ sót.”** Wildcard rộng có thể bắt thêm join point ngoài ý muốn, tạo recursion, đếm lặp, overhead hoặc side effect sai.

**“Thêm annotation nghĩa là đã an toàn tuyệt đối.”** Annotation có thể bị quên, đặt sai vị trí, thay đổi semantics hoặc không được engine nhìn thấy. Đặc biệt, annotation yêu cầu quyền chưa chứng minh người gọi được phép.

**“Unit test gọi implementation trực tiếp đã xác minh AOP.”** Nó thường bỏ qua chính điểm cần kiểm chứng: proxy/weaving, pointcut và thứ tự advice.

**“Advice around đã chạy nên nghiệp vụ chắc chắn được gọi một lần.”** Một around advice có thể bỏ qua hoặc gọi lại `proceed` tùy thiết kế; phải kiểm tra semantics của từng loại policy.

**“Nếu advice fail thì bỏ qua để ứng dụng tiếp tục là an toàn.”** Với telemetry tùy chọn có thể chấp nhận theo chính sách; với authorization hoặc audit bắt buộc, bỏ qua có thể mở lỗ hổng.

**“Chuyển method sang async không ảnh hưởng aspect.”** Lifecycle, thời gian đo, transaction, resource và context có thể thay đổi lớn khi method chỉ trả một handle của tác vụ chưa kết thúc.

**“Hai aspect đều đúng thì thứ tự nào cũng đúng.”** Retry–transaction–audit–metrics có thể cho kết quả khác nhau khi thay thứ tự.

## 16. Quy trình làm việc để kiểm soát coupling

1. **Lập danh mục hành vi cắt ngang:** phân loại audit, authorization, transaction, retry, metrics, tracing và hành vi nào bắt buộc.
2. **Viết tập operation cần áp dụng:** xác định một nguồn kỳ vọng có thể kiểm chứng như annotation/manifest/boundary rõ ràng.
3. **Đo tập join point thực sự được chọn:** ở build/startup hoặc trong một bài test tích hợp; so sánh với tập kỳ vọng.
4. **Bổ sung negative tests:** chứng minh operation ngoài phạm vi không bị chặn sai và yêu cầu thiếu binding bắt buộc bị từ chối.
5. **Kiểm tra đường gọi production:** không chỉ gọi target trực tiếp; xem self-invocation, factory, callback, reflection và job nền nếu có.
6. **Kiểm tra thứ tự và lifecycle:** test retry, exception, transaction, cancellation, completion và context async khi liên quan.
7. **Review refactor và cấu hình cùng nhau:** đổi package/tên/chữ ký, thêm overload, đổi wiring hoặc advice order đều cần cập nhật contract.
8. **Theo dõi runtime:** đếm advice invocation và so với operation thực tế ở tầng biên; cảnh báo biến mất bất thường, nhưng không thay thế coverage test.
9. **Giữ bảo vệ ở boundary quan trọng:** dữ liệu/quan hệ sở hữu/điều kiện nhất quán không được dựa duy nhất vào một matcher có thể bị bypass.

## 17. Kết luận

AOP tách mã cắt ngang ra khỏi thân nghiệp vụ, nhưng **không xóa phụ thuộc** giữa chúng. Điểm phụ thuộc chuyển sang *pointcut*, cơ chế gắn advice, thứ tự thực thi và vòng đời của các tác vụ. Khi mã được đổi tên, di chuyển, tạo overload, thay kiểu gọi hoặc chuyển sang bất đồng bộ, một aspect có thể mất tác dụng mà code vẫn biên dịch và kết quả đơn giản vẫn trông đúng. Thiết kế bền vững đòi hỏi **policy tường minh, coverage có thể kiểm tra, test đường gọi thực và bảo vệ bổ sung tại boundary có rủi ro cao**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-005">Pointcut mong manh khi đổi tên, di chuyển hoặc nạp chồng phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut mong manh khi đổi tên, di chuyển hoặc nạp chồng phương thức trong AOP

## 1. Vấn đề cần nhận diện

**Aspect-Oriented Programming (AOP)** dùng **pointcut** để chọn những **join point** mà advice sẽ chạy. Khi một ứng dụng được refactor, tên phương thức, tên lớp, package, interface, danh sách tham số và đường gọi thường thay đổi. Nếu pointcut dựa chặt vào các chi tiết này, mã nghiệp vụ vẫn biên dịch và chạy bình thường nhưng hành vi xuyên suốt (logging, transaction, kiểm tra quyền, retry, giám sát) có thể **im lặng biến mất** hoặc **áp nhầm vào lời gọi khác**.

Ví dụ một bộ ghi audit ban đầu chặn thao tác `OrderService.submit(String)`. Sau đó nhóm phát triển:

- Đổi tên `submit(String)` thành `place(String)` để đúng ngôn ngữ nghiệp vụ.
- Chuyển interface từ package cũ sang package mới.
- Thêm overload `submit(int)` để tra cứu đơn theo mã số.
- Giữ nguyên một biểu thức pointcut `execution(* *.submit(..))`.

Khi đó lời gọi tạo đơn có thể không được audit, còn overload chỉ để đọc lại bị instrument nhầm. Nếu advice dùng để phân quyền hoặc quản lý transaction, hậu quả không chỉ là một dòng log thiếu: đó có thể là sai chính sách thực thi.

Bài này giúp bạn:

1. Hiểu selector theo tên, package, type, chữ ký, modifier và annotation có thể đổi nghĩa thế nào sau refactor.
2. Phân biệt **false negative** (bỏ sót join point phải được chặn) và **false positive** (chặn thừa join point không thuộc chính sách).
3. Thiết kế pointcut theo **hợp đồng ngữ nghĩa** và đường thực thi thật, đồng thời kiểm soát sự dịch chuyển của hợp đồng.
4. Xử lý overload, override, generic bridge method, inheritance, proxy và weaving theo đúng mô hình công cụ.
5. Kiểm thử **phạm vi chọn join point** và **việc advice thực sự chạy**, gồm cả đường gọi trực tiếp, self-invocation, callback và interface.
6. Thực hành với ví dụ Java 17 có thể biên dịch và chạy để thấy lỗi của pointcut phụ thuộc tên/lớp và cách khắc phục bằng metadata nghiệp vụ.

Trọng tâm là **AOP tổng quát**. Các biểu thức như `execution(...)` dưới đây minh họa một họ cú pháp pointcut thường gặp; không giả định mọi thư viện/interceptor đều hỗ trợ cùng ngữ pháp.

## 2. Pointcut không phải là tên hàm

Ba khái niệm cần tách rõ:

- **Join point:** sự kiện/vị trí có thể bị quan sát: thực thi method, gọi method, truy cập field, xây dựng object… Tập khả dụng tùy công cụ weaving/interception.
- **Pointcut:** vị từ chọn join point dựa trên thuộc tính của chúng; nó có thể xét tên, chữ ký, type, annotation, phạm vi lexical, đối tượng runtime hoặc điều kiện khác.
- **Advice:** hành vi được kích hoạt cho các join point được pointcut chọn, chẳng hạn ghi audit, đo duration, kiểm tra quyền hoặc áp retry.

Một selector minh họa:

```text
execution(* example.orders.OldOrderService.submit(..))
```

Ý tưởng là chọn *execution join point* của method tên `submit` trong một type cụ thể. Ngay khi type được di chuyển hoặc method đổi tên, tập join point được chọn có thể trở thành rỗng. Ngay cả khi tập này không rỗng, selector không tự chứng minh lời gọi nghiệp vụ mong muốn thực sự đi qua nó.

**Biên dịch thành công không chứng minh pointcut còn đúng.** Một biểu thức pointcut đặt trong string/config có thể không được compiler Java refactor cùng với biểu tượng method, và một số cơ chế chỉ xác minh selector khi khởi động hoặc khi join point xuất hiện.

## 3. Thế nào là pointcut “mong manh”?

Đặt:

- `E`: tập join point **mong muốn** được chính sách chọn.
- `M`: tập join point **thực sự** được pointcut chọn.
- `E \ M`: join point bị bỏ sót (**false negative**).
- `M \ E`: join point bị chọn thừa (**false positive**).

Một refactor hợp lệ có thể làm thay đổi cả `E` và `M`. Ta cần xác định mục tiêu **ngữ nghĩa** trước, rồi kiểm tra selector vẫn chọn đúng tập mới.

Ví dụ bảng:

| Thay đổi | Pointcut ban đầu | Rủi ro |
|---|---|---|
| Đổi `submit` thành `place` | Khớp tên `submit` | Bỏ sót thao tác cần audit |
| Chuyển `old.orders.Service` sang `new.orders.Service` | Khớp FQN của lớp/package cũ | Bỏ sót toàn bộ thao tác mới |
| Thêm `submit(int)` | Khớp `submit(..)` | Bắt cả overload khác nghĩa |
| Đổi `submit(String)` thành `submit(Command)` | Khớp tham số `String` | Bỏ sót vì chữ ký đổi |
| Đổi implementation nhưng giữ interface | Match dựa trên declaring class/implementation | Có thể trượt theo loại join point |
| Thêm override trong subclass | Match chỉ một method hoặc type | Có thể bỏ sót override thực thi |
| Sinh bridge method do generics | Đếm/match tất cả method reflect | Có thể tính hai method hoặc chọn sai |
| Chuyển từ gọi qua proxy sang gọi trực tiếp | Proxy interception | Advice không còn chạy |
| Mở rộng wildcard package | `..*Service.*(..)` | Chọn nhiều helper hoặc method không liên quan |

Độ mong manh là kết quả của **sự lệ thuộc vào chi tiết dễ thay đổi**. Không có nghĩa luôn phải tránh exact signature: đôi khi chính xác một overload là hợp đồng quan trọng. Điều cần làm là chọn đặc tính **được quản lý và kiểm thử như một phần kiến trúc**.

## 4. Ba nguồn thay đổi quan trọng

### 4.1. Đổi tên phương thức

Một pointcut chỉ dựa trên chuỗi `submit` có thể mất tác dụng khi refactor thành `place`, dù nghiệp vụ vẫn là đặt hàng. Tên method phục vụ thiết kế API nhưng không phải lúc nào cũng là định danh ổn định của **business action**.

```text
Trước:
  OrderActions.submit(String orderId)   -> ORDER_PLACE

Sau:
  OrderActions.place(String orderId)    -> ORDER_PLACE

Pointcut theo tên:
  name == "submit"    // mất thao tác ORDER_PLACE sau refactor

Pointcut theo mã action đã kiểm tra:
  action == ORDER_PLACE  // vẫn nhận diện thao tác đổi tên
```

Một số thay đổi tên còn làm lộ sự khác biệt ngữ nghĩa thật: `save` trước đây có thể vừa tạo vừa cập nhật, sau refactor thành `create` và `update` với chính sách khác nhau. Khi đó không nên copy máy móc cùng annotation/mã action cho cả hai. **Ổn định selector không quan trọng hơn đúng hợp đồng nghiệp vụ**.

### 4.2. Di chuyển lớp, package, module

Pointcut theo tên package hay lớp cụ thể dễ vỡ khi nhóm phát triển tái cấu trúc module, đổi namespace hoặc thay implementation. Nếu advice được weave tại bytecode, cần hiểu nó matching **declaring type**, **runtime type**, subtype hierarchy hay location lexical. Nếu dùng proxy, cần biết proxy hiện thực interface nào và join point thực tế nằm ở phía gọi hay method execution.

```text
Trước:
  old.application.OrderCommands.submit(...)

Sau:
  app.orders.usecase.OrderCommands.place(...)
```

Một selector chuỗi gắn với `old.application..*` có thể không được refactoring tool cập nhật. Ngược lại, wildcard `*..*` quá rộng sẽ bắt nhiều thao tác không cần. Đừng giải quyết selector bỏ sót bằng một biểu thức wildcard không kiểm soát; cần xác định **ranh giới kiến trúc có tên và có hợp đồng**.

Trong triển khai thực tế, package có thể vẫn là tín hiệu hữu ích để xác định vùng ứng dụng cần kiểm tra, nhưng nên kết hợp **hợp đồng method** thay vì dùng package làm toàn bộ quyết định.

### 4.3. Nạp chồng phương thức (overloading)

Java cho phép một type có nhiều method cùng tên, khác danh sách kiểu tham số:

```java
// Mảnh minh họa, không phải chương trình độc lập.
void submit(String orderId); // đặt đơn
void submit(int orderNumber); // tra cứu đơn cũ
```

Selector `name == "submit"` chặn cả hai, trong khi selector `name + parameterTypes == (String)` chỉ chặn một overload. Nếu overload mới là một hành động khác, chặn cả hai có thể dẫn tới:

- Transaction không cần thiết quanh lời gọi chỉ đọc.
- Metric bị đếm sai; tên span trùng nhau dù action khác.
- Retry không an toàn hoặc không phù hợp với overload mới.
- Quy tắc quyền nhầm: người được phép tạo đơn bị buộc phải có quyền tra cứu, hoặc ngược lại.
- Log đối số không phù hợp, tăng khả năng lộ thông tin.

**Overloading không phải overriding.** Overload được chọn từ chữ ký tại compile time; override là implementation mới của method có quan hệ kế thừa, với dispatch tại runtime. Một pointcut phải giải thích nó chọn **declaration, implementation, call site hay execution**.

## 5. Tại sao selector chỉ theo tên thường quá yếu?

Tên thường biểu thị cách code được viết, không đầy đủ ý nghĩa nghiệp vụ. Ví dụ `save` có thể vừa:

- Lưu hóa đơn, đòi transaction và audit.
- Ghi một cache entry, không cần transaction nghiệp vụ.
- Lưu nhật ký hệ thống, không nên lặp vô điều kiện.
- Là helper xây dựng object, không có thao tác bảo mật.

Một selector rộng kiểu “mọi phương thức có tên `save`” sẽ tác động đồng loạt. Còn selector cực hẹp vào FQN và chữ ký có thể vỡ sau khi đổi interface, di chuyển package hoặc điều chỉnh overload.

Lựa chọn thực tiễn:

1. Chọn **logical boundary** mà team duy trì được như một hợp đồng: command/query service, nghiệp vụ tạo/duyệt/xuất dữ liệu.
2. Định danh hành động bằng mã có ý nghĩa ổn định (`ORDER_PLACE`, `ORDER_LOOKUP`), annotation hoặc registry explicit.
3. Kết hợp ràng buộc phạm vi để không mở selector ra toàn ứng dụng.
4. Kiểm thử cả danh sách join point được chọn và khả năng lời gọi thật thực sự đi qua interception.
5. Khi refactor, cập nhật contract và test cùng thay đổi mã nghiệp vụ.

Một annotation không phải “lá bùa” giải quyết mọi trường hợp. Nó có thể bị quên, bị copy sai, mất do retention, đặt trên interface nhưng interceptor chỉ đọc implementation, hoặc chính method có ý nghĩa mới trong khi annotation cũ chưa đổi.

## 6. Biến ngữ nghĩa nghiệp vụ thành metadata

Một hướng thiết kế là khai báo ý định:

```java
// Mảnh minh họa API, không phải chương trình độc lập.
@BusinessAction("ORDER_PLACE")
Receipt place(CreateOrderCommand command);

@BusinessAction("ORDER_LOOKUP")
OrderView submit(int orderNumber);
```

Interception chọn join point theo annotation và/hoặc registry, sau đó đọc mã action để điều khiển advice. Nếu `place` đổi thành `confirm` nhưng ý nghĩa không đổi, mã `ORDER_PLACE` có thể được giữ. Nếu hành động đổi nghĩa, người sửa phải cập nhật chính sách và test.

Cần kiểm soát những vấn đề sau:

- **Retention:** annotation được đọc bằng reflection tại runtime cần `@Retention(RetentionPolicy.RUNTIME)`. Với weaving diễn ra ở thời điểm khác, yêu cầu cụ thể phụ thuộc cơ chế.
- **Target:** annotation method khác annotation type; đặt sai vị trí không được tự động hiểu là áp cho từng method.
- **Interface vs implementation:** `method.getAnnotation(...)` xem metadata trên đúng đối tượng `Method` đang đọc. Không được giả định annotation trên interface tự sao sang implementation.
- **Kế thừa:** `@Inherited` chủ yếu ảnh hưởng truy vấn annotation ở class, không tự khiến annotation method của interface/overridden method được kế thừa khi truy vấn reflection.
- **Khả năng thiếu khai báo:** một method mới ở boundary bảo vệ phải có policy phù hợp hoặc được phân loại public bằng cơ chế rõ ràng; không ngầm coi không có annotation nghĩa là không cần kiểm tra.
- **Tính ổn định của mã action:** mã action phải có catalog và owner; rename của mã action cũng cần migration có kiểm soát.
- **Nhiều semantics trên một method:** ví dụ một operation có thể yêu cầu audit và transaction. Tách metadata/policy cho từng concern nếu chúng không có cùng tập join point.

Với API bên thứ ba không thể gắn annotation, một **typed registry** ánh xạ method/signature sang chính sách có thể phù hợp. Nhưng registry phải được đồng bộ và được test coverage khi API thay đổi.

## 7. Ví dụ Java 17 hoàn chỉnh: pointcut theo tên, chữ ký và action

Ví dụ này chỉ dùng JDK: reflection và dynamic proxy.

- `fragileExact` mô phỏng selector buộc method phải thuộc `LegacyCommands.submit(String)`.
- `fragileName` mô phỏng selector chỉ dựa vào tên `submit`.
- `semantic` chọn mã hành động `ORDER_PLACE` từ annotation trên method.
- `Commands.place(String)` là thao tác đổi tên, cùng ý nghĩa đặt đơn.
- `Commands.submit(int)` là overload mới với ý nghĩa **tra cứu**, dù tên giống method cũ.
- Proxy `guarded` tạo interception theo metadata. Nó minh họa kỹ thuật chặn lời gọi, chưa có quyết định phân quyền hay transaction thực tế.

Lưu vào file **PointcutStabilityDemo.java**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PointcutStabilityDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface BusinessAction { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PublicAction {}

    interface LegacyCommands {
        @BusinessAction("ORDER_PLACE")
        String submit(String orderId);
    }

    interface Commands {
        @BusinessAction("ORDER_PLACE")
        String place(String orderId);

        @BusinessAction("ORDER_LOOKUP")
        String submit(int orderNumber);

        @PublicAction
        String health();
    }

    static final class Handler implements Commands {
        public String place(String orderId) { return "placed:" + orderId; }
        public String submit(int orderNumber) { return "lookup:" + orderNumber; }
        public String health() { return "ok"; }
    }

    static boolean fragileExact(Method method) {
        return method.getDeclaringClass() == LegacyCommands.class
                && method.getName().equals("submit")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class;
    }

    static boolean fragileName(Method method) {
        return method.getName().equals("submit");
    }

    static boolean semantic(Method method) {
        BusinessAction action = method.getAnnotation(BusinessAction.class);
        return action != null && action.value().equals("ORDER_PLACE");
    }

    static Commands guarded(Commands target) {
        return (Commands) Proxy.newProxyInstance(
                Commands.class.getClassLoader(),
                new Class<?>[] { Commands.class },
                (proxy, method, args) -> {
                    BusinessAction action = method.getAnnotation(BusinessAction.class);
                    if (action != null) {
                        System.out.println("intercept=" + action.value());
                    } else if (!method.isAnnotationPresent(PublicAction.class)) {
                        throw new IllegalStateException("Unclassified operation");
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        Method old = LegacyCommands.class.getMethod("submit", String.class);
        Method renamed = Commands.class.getMethod("place", String.class);
        Method overloaded = Commands.class.getMethod("submit", int.class);
        System.out.println("legacy_exact=" + fragileExact(old));
        System.out.println("renamed_exact=" + fragileExact(renamed));
        System.out.println("renamed_name=" + fragileName(renamed));
        System.out.println("overload_name=" + fragileName(overloaded));
        System.out.println("renamed_semantic=" + semantic(renamed));
        System.out.println("overload_semantic=" + semantic(overloaded));

        Commands api = guarded(new Handler());
        System.out.println(api.place("A-7"));
        System.out.println(api.submit(7));
        System.out.println(api.health());
    }
}
```

Biên dịch và chạy bằng Java 17:

```bash
javac --release 17 -encoding UTF-8 PointcutStabilityDemo.java
java PointcutStabilityDemo
```

Kết quả đã chạy kiểm chứng:

```text
legacy_exact=true
renamed_exact=false
renamed_name=false
overload_name=true
renamed_semantic=true
overload_semantic=false
intercept=ORDER_PLACE
placed:A-7
intercept=ORDER_LOOKUP
lookup:7
ok
```

Giải thích theo từng dòng:

1. `legacy_exact=true`: selector cũ còn match chữ ký trong `LegacyCommands`.
2. `renamed_exact=false`: thao tác sau refactor nằm ở interface `Commands`, tên `place`, nên matcher buộc type/chữ ký cũ bị bỏ sót.
3. `renamed_name=false`: matcher theo tên `submit` cũng bỏ sót thao tác đặt đơn đã đổi tên.
4. `overload_name=true`: matcher theo tên lại chọn overload `submit(int)`, một hành động tra cứu khác.
5. `renamed_semantic=true` và `overload_semantic=false`: khi hỏi riêng *“có phải ORDER_PLACE không?”*, metadata phân biệt đúng hai nghiệp vụ.
6. Khi gọi thật qua proxy, hai method có action được instrument với mã thích hợp; `health()` được khai báo `@PublicAction` nên chạy không có dòng intercept.

**Quan trọng:** `semantic` trong ví dụ chỉ chọn `ORDER_PLACE`, còn proxy `guarded` cố ý intercept *mọi method có `@BusinessAction`* để log mã action. Hai hành vi này không mâu thuẫn: selector lọc một action cụ thể khác với một interceptor chung cho tất cả business action.

**Phạm vi:** JDK proxy chỉ intercept các lời gọi **qua interface của proxy**. Ví dụ không thay thế một engine weaving đầy đủ, không chứng minh mọi join point trong ứng dụng đều được chặn. `@BusinessAction` và `@PublicAction` được khai báo trên interface; nếu chuyển annotation sang implementation thì cách lấy metadata phải thay theo hợp đồng. Handler demo cũng không trình bày đầy đủ hành vi các method của `Object` trên proxy; khi dùng production cần xác định rõ cách xử lý `equals/hashCode/toString` và những method ngoài interface được bảo vệ.

## 8. Điểm chặn theo kiểu `call` và `execution` khác nhau

AOP có thể quan sát **lời gọi method** tại call site hoặc **việc thực thi method** tại implementation; công cụ khác nhau hỗ trợ tập join point khác nhau.

```text
Caller -> [call join point] -> dynamic dispatch -> [execution join point] -> body
```

Điểm cần suy nghĩ:

- Method gọi có thể được khai báo ở interface/superclass; method thực thi có thể nằm ở subclass khác.
- Compile-time type tại call site khác runtime target type.
- Pointcut chọn `call` trong một package có thể bỏ qua caller chuyển sang package khác dù method không đổi.
- Pointcut chọn `execution` theo declaring type có thể thay đổi khi method được override hoặc chuyển sang một implementation khác.
- Proxy interception chặn những lời gọi đi qua proxy; self-invocation bằng `this.method()` thường không đi qua proxy. Weaving có thể hỗ trợ phạm vi khác, nhưng phải dựa vào engine đang dùng, không suy luận chung.

Vì vậy câu “pointcut match method X” còn thiếu thông tin. Cần viết rõ: **join point loại nào, code/lớp nào, điều kiện static hay dynamic, được gắn tại build/load/runtime, và đường gọi nào được bao phủ**.

## 9. Overriding, inheritance và generic bridge methods

### 9.1. Override làm thay đổi nơi thực thi

Một method công khai qua interface có thể được implement bởi nhiều lớp. Nếu pointcut match một lớp cụ thể, implementation mới có thể không được chặn. Ngược lại, một selector rộng theo subtype có thể bắt thêm implementation mà policy khác nhau.

Trước khi refactor, xác định rõ:

- Advice thuộc hợp đồng **giao diện** hay thuộc implementation?
- Có bao gồm override mới trong subclass?
- Có method default trên interface và lời gọi vào nó không?
- Dynamic proxy có nhận đúng interface method không? Weaving có chọn execution của implementation không?
- Có method synthetic/bridge mà công cụ có thể trình bày như join point riêng không?

### 9.2. Generic và type erasure

Java xóa phần lớn thông tin generic ở runtime. Một method như `process(List<Order>)` và `process(List<Customer>)` không thể là hai overload chỉ khác generic element type vì chúng có cùng erasure. Dựa trên tên generic được suy ra từ source để xây runtime pointcut có thể không khớp như mong đợi.

Compiler cũng có thể sinh **bridge method** nhằm bảo toàn polymorphism sau type erasure. Reflection có `Method.isBridge()` và `Method.isSynthetic()`; không tự cho rằng mọi method thu được từ `getDeclaredMethods()` là một business operation độc lập.

Một công cụ AOP có thể đã xử lý bridge/synthetic cho bạn, hoặc không. Nếu tự làm interception/registry dựa trên reflection, cần chính sách chuẩn hóa method gốc/bridge rõ ràng và test với generic override.

### 9.3. Return type và covariant return

Java không phân biệt overload chỉ bằng return type. Một phương thức override có thể trả subtype phù hợp (*covariant return*), và có thể liên quan đến bridge method. Selector hoặc registry lưu đầy đủ mô tả bytecode phải xử lý sự khác biệt giữa chữ ký Java ở source và method descriptor trong bytecode.

### 9.4. Varargs

`send(String...)` được biên dịch thành tham số mảng `String[]`. Pointcut/registry dựa trên runtime parameter types nhìn thấy array, không phải “số đối số tùy ý” như ở call site. Reflection có `Method.isVarArgs()` để xác nhận ngữ nghĩa. Đổi từ `send(String)` thành `send(String...)` có thể làm exact selector cũ không còn match.

## 10. Tên tham số, modifier và annotation cũng có giới hạn

Pointcut hoặc advice đôi khi dựa vào tên tham số để suy ý nghĩa:

```java
// Mảnh minh họa, không phải chương trình độc lập.
void approve(String documentId, String reviewerId);
```

Ở runtime Java, `Parameter.getName()` có thể chỉ trả `arg0`/`arg1` nếu không biên dịch với `-parameters` và không có nguồn metadata khác. Việc đổi tên biến cũng có thể phá quy tắc dù hợp đồng thao tác không đổi. Dùng kiểu, vị trí, annotation hoặc request schema có kiểm soát khi cần trích tài nguyên cho policy.

Các modifier như `public`, `protected`, `private`, `static`, `final` có thể ảnh hưởng khả năng intercept theo cơ chế. Không phải mọi engine có thể chặn constructor, field access hoặc method final/private giống nhau. Đừng dùng một pointcut “rộng trên giấy” rồi mặc định mọi kiểu member đều được quan sát.

**Annotation matching** cũng cần test retention và lookup location. Thêm annotation vào interface, implementation hoặc override có thể không có cùng tác dụng. Không copy annotation từ method gốc tới method mới nếu ngữ nghĩa đã đổi.

## 11. Hậu quả khác nhau theo loại advice

Cùng một false negative/false positive có mức rủi ro rất khác nhau:

| Concern | Bỏ sót pointcut | Bắt thừa pointcut |
|---|---|---|
| Logging/observability | Thiếu trace, audit, metric | Log rác, dữ liệu nhạy cảm, tăng chi phí |
| Authorization | Có thể chạy thao tác mà chưa kiểm quyền | Từ chối thao tác hợp lệ hoặc áp policy sai |
| Transaction | Thiếu ranh giới commit/rollback mong muốn | Transaction dài, lock thừa, quy tắc commit sai |
| Retry | Bỏ mất khả năng phục hồi transient | Gọi lại side effect không idempotent |
| Cache | Không dùng cache ở nơi cần | Cache sai dữ liệu/ngữ cảnh actor |
| Rate limit | Đường gọi mới tránh giới hạn | Chặn cả helper đọc nhẹ |
| Timing/metrics | Mất dữ liệu đo theo nghiệp vụ | Nhân đôi metric, làm sai cardinality |

Với **phân quyền và tính toàn vẹn dữ liệu**, đừng dựa duy nhất vào một selector tên method mà không có guard/constraint sát thao tác nghiệp vụ. AOP có thể là PEP ở boundary, nhưng nội bộ vẫn cần xác minh các bất biến quan trọng trong transaction/storage khi nhiều đường vào tồn tại.

## 12. Thiết kế pointcut bền vững hơn

Một thiết kế hợp lý thường có **hai lớp điều kiện**:

1. **Phạm vi kiến trúc:** chỉ chọn nhóm operation boundary do ứng dụng quản lý.
2. **Phân loại ngữ nghĩa:** chọn business action/annotation/registry cụ thể được quy định bằng hợp đồng.

Ví dụ diễn đạt khái niệm:

```text
isManagedBusinessBoundary(method)
    AND operationContract(method).action == ORDER_PLACE
```

Như vậy, việc di chuyển package có thể yêu cầu cập nhật boundary definition và kiểm thử coverage, nhưng không bắt mọi method chỉ vì tên giống nhau. Ngược lại, annotation đánh dấu action không vô tình áp dụng đến các helper ngoài boundary.

Các nguyên tắc:

- **Nguyên tắc tối thiểu:** chỉ intercept phạm vi cần cho concern.
- **Explicit contract:** mỗi operation nhạy cảm phải được khai báo policy rõ, kể cả khi public.
- **Fail closed phù hợp:** nếu một operation thuộc boundary bảo vệ nhưng thiếu classification, không tự cho chạy; một policy bị thiếu phải phát hiện khi build/startup hoặc bị từ chối theo hợp đồng.
- **Tách ngữ nghĩa khỏi tên implementation:** business action ID có thể giữ ổn định qua rename, move, đổi implementation khi ý định không đổi.
- **Có kiểm thử phủ pointcut:** lời gọi thật phải đi qua advice, không chỉ metadata phản chiếu.
- **Không kết hợp quá nhiều mối quan tâm vào một selector:** authorization, telemetry, retry và transaction có thể cần tập join point khác nhau.
- **Review thay đổi contract:** tách/tổng hợp operation phải được xét lại chính sách; không giữ ID cũ chỉ vì tiện tương thích.

Không có “pointcut bất biến” miễn phí. Mục tiêu là khiến thay đổi có tác động lên selector được **phát hiện và đánh giá có chủ đích**.

## 13. Kiểm thử pointcut như một phần của hợp đồng kiến trúc

Kiểm thử chỉ xem method trả kết quả đúng là chưa đủ. Cần test cụ thể **advice có chạy cho đúng tập lời gọi không**.

### 13.1. Test positive

Tạo fixture có một lời gọi bắt buộc được chặn; assertion có thể kiểm tra:

- Advice tăng counter hoặc ghi event test-only.
- Đúng mã action và metadata được dùng.
- `proceed()` được gọi đúng theo hợp đồng (ví dụ một lần khi không có retry).
- Policy thực sự từ chối trước side effect khi áp dụng authorization.

### 13.2. Test negative

Tạo method giống tên nhưng khác nghĩa hoặc nằm ngoài boundary. Assert advice không chạy ở nơi không thuộc scope. Ví dụ overload `submit(int)` không bị áp nhầm policy `ORDER_PLACE`.

### 13.3. Test refactor

Trong bài kiểm thử kiến trúc, liệt kê các command/query method trong vùng được quản lý và yêu cầu **được phân loại**. Sau đó:

- Rename method trên fixture: semantic action đúng vẫn được chọn.
- Move type sang package mới của boundary: coverage không bị âm thầm mất.
- Thêm overload khác nghĩa: không nhận policy của overload cũ.
- Thêm implementation/override: lời gọi thật vẫn qua điểm chặn cần thiết.
- Thêm method không khai báo: build/test thất bại thay vì tự mặc định public.
- Bỏ annotation: test chỉ ra lỗi phân loại hoặc fail-closed.

Những test này phải cập nhật khi hợp đồng nghiệp vụ thay đổi, thay vì cứng hóa tên method cũ mãi mãi.

### 13.4. Phân biệt test match và test execution

Một test `pointcut.matches(Method)` có thể PASS nhưng advice **không bao giờ chạy** vì ứng dụng gọi implementation trực tiếp, gọi nội bộ qua `this`, hoặc sử dụng entrypoint khác. Cần cả:

- **Static/metadata test:** selector và registry có chọn đúng method mong muốn?
- **Integration test:** lời gọi qua đường thực tế có kích hoạt interceptor/weaving không?
- **Behavior test:** side effect, exception, audit/metric/transaction thực sự đúng không?

Với phản xạ Java, lọc hoặc chuẩn hóa method synthetic/bridge khi đếm coverage; tránh đếm bridge thành operation riêng hoặc làm bỏ sót contract method thật.

## 14. Refactor an toàn: thay đổi từng bước

Khi chuyển một pointcut mong manh sang hợp đồng ổn định hơn:

**Bước 1 — Chụp tập join point hiện tại.** Từ cấu hình và test, ghi lại phương thức nào thực sự bị chặn, concern nào dùng pointcut đó và mục đích nghiệp vụ.

**Bước 2 — Xác định tập mong muốn.** Chủ sở hữu nghiệp vụ xác nhận action nào phải được authorization, transaction, retry, audit… Đừng coi tập đang bị chặn là đúng tuyệt đối: lỗi cũ có thể đã tồn tại.

**Bước 3 — Định nghĩa metadata/registry.** Có tên action rõ, phạm vi type, cách xử lý public command và policy khi thiếu metadata.

**Bước 4 — Tạo test coverage.** Assertion dương/âm cho rename, overload, implementation mới, self-call, exception, async. Với bảo mật, dùng test “không có side effect khi thiếu quyền”.

**Bước 5 — Chuyển interception và kiểm tra trùng.** Nếu pointcut cũ và mới chạy song song, tránh ghi audit đôi, mở hai transaction hoặc retry lồng nhau. Có thể so sánh song song ở chế độ chỉ đánh giá (shadow) khi phù hợp, nhưng phải tránh vô tình nhân hiệu ứng.

**Bước 6 — Gỡ selector cũ sau khi xác minh.** Tài liệu hóa ranh giới mới, cách khai báo method và kiểm tra trong CI.

**Bước 7 — Rà soát sau mỗi thay đổi.** Thêm overload/method mới, move package hoặc thay đường gọi là thay đổi kiến trúc cần review policy.

Đặc biệt cẩn trọng với việc “thay hết bằng wildcard `*`”: làm vậy có thể chữa false negative bằng cách tăng mạnh false positive.

## 15. Pointcut coverage trong các tình huống bất đồng bộ

Một method có thể trả `CompletableFuture` hoặc một đối tượng mô tả luồng xử lý bất đồng bộ. Pointcut trên **method entry** chỉ chặn lúc tạo handle, không đồng nghĩa chặn mọi callback, attempt, subscription hay handler chạy về sau.

Ví dụ:

```text
submitOperation() --returns future--> caller
     |
     +-- executor task --> callback --> external side effect
```

Nếu refactor chuyển side effect từ phần đồng bộ của method vào callback, một advice trước method có thể vẫn match về tên/type nhưng **không còn bao trùm thời điểm hiệu ứng phát sinh**. Đó là thay đổi **ngữ nghĩa join point**, không thể phát hiện chỉ bằng kiểm tra chuỗi tên.

Với ranh giới bất đồng bộ:

- Xác định action được **chấp nhận**, **xếp lịch** hay **thực hiện** ở đâu.
- Kiểm tra quyền, transaction và dữ liệu tại nơi side effect thực sự xảy ra nếu trạng thái có thể đổi.
- Propagate correlation/security context qua executor/scheduler bằng snapshot và restore, không giả định `ThreadLocal` tự đi theo.
- Kiểm tra completion khi cần đo thời gian/ghi kết quả cuối cùng, thay vì dừng tại thời điểm trả future.
- Bảo đảm pointcut không chỉ đánh dấu API wrapper mà bỏ sót consumer hoặc task thực hiện.

Đối với queue và message handler, một thay đổi route hoặc serializer cũng có thể làm handler mới không thuộc pointcut cũ. Cần test từ entrypoint đến effect.

## 16. Những lỗi phổ biến

| Hiện tượng | Nguyên nhân | Hướng sửa |
|---|---|---|
| Advice mất sau rename | Match literal method name | Gắn theo business action contract và test positive |
| Move package xong không còn trace | Match FQN/package cũ | Kiểm soát boundary registry và test khi move |
| Thêm overload làm policy chạy sai | Wildcard `methodName(..)` | Phân biệt action, parameter signature hoặc annotation |
| Thay implementation làm interceptor im lặng | Selector gắn implementation cũ | Xác định join point/declaring type và test execution |
| Self-invocation bỏ qua guard | Proxy chỉ chặn lời gọi qua proxy | Giữ boundary rõ và kiểm tra sát dữ liệu |
| Pointcut trên interface không áp vào implementation | Đọc annotation sai vị trí | Lookup method/metadata theo chiến lược rõ |
| Advice chạy hai lần | Bridge method hoặc hai interception layer | Chuẩn hóa method và dedup theo scope |
| Method mới không được bảo vệ | Không có metadata/kiểm tra contract | Default deny tại managed boundary, CI test |
| Pointcut wildcard bắt cả helper | Match theo pattern quá rộng | Giới hạn phạm vi + semantic action |
| Test PASS nhưng production bypass | Chỉ unit test predicate | Integration test đường gọi thật |
| AOP match vẫn sai thời điểm | Side effect chuyển sang future/queue | Đặt guard/observation tại execution boundary phù hợp |

## 17. Checklist rà soát pointcut trước release

- [ ] Tập join point mong muốn được mô tả bằng **ý nghĩa nghiệp vụ**, không chỉ chuỗi tên method.
- [ ] Selector xác định rõ loại join point (`call`, `execution` hoặc proxy invocation) và phạm vi type.
- [ ] Rename, move package/module và overload đã được kiểm thử cả positive lẫn negative.
- [ ] Overriding, inheritance, generic bridge, varargs và covariant return được xem xét khi có liên quan.
- [ ] Annotation hoặc registry được đặt/đọc đúng interface/implementation và retention.
- [ ] Method mới ở boundary nhạy cảm không thể im lặng bỏ qua policy.
- [ ] Không có raw instance/self-invocation hoặc entrypoint ngoài scope vô tình bypass enforcement.
- [ ] Có test chứng minh advice **thực sự chạy**, không chỉ kiểm tra selector bằng reflection.
- [ ] Authorization/transaction/retry/audit không bị áp nhầm overload hoặc chạy hai lần.
- [ ] Async callback/message consumer được kiểm tra tại ranh giới side effect tương ứng.
- [ ] Phạm vi instrument và action registry được cập nhật cùng refactor trong code review/CI.
- [ ] Monitoring theo logical action cho thấy thay đổi đột ngột về số lần interception khi rollout.

## 18. Ghi nhớ

**Pointcut dựa vào hình dạng mã nguồn sẽ mong manh khi hình dạng ấy thay đổi.** Đổi tên phương thức có thể gây bỏ sót, nạp chồng có thể gây chọn nhầm, còn di chuyển lớp hoặc đổi đường gọi có thể khiến advice không chạy dù ứng dụng vẫn bình thường. Để bền vững, cần **ranh giới nghiệp vụ rõ, metadata/registry có ngữ nghĩa, phạm vi interception xác định và kiểm thử cả phần được chọn lẫn phần không được chọn**. Với concern quan trọng như phân quyền hay tính toàn vẹn dữ liệu, hãy bổ sung bảo vệ sát side effect để một thay đổi pointcut không trở thành một đường truy cập ngoài chính sách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-006">Biên giới mô-đun và khả năng hiểu hành vi từ mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Biên giới mô-đun và khả năng hiểu hành vi từ mã nguồn khi dùng AOP

## 1. Vấn đề kiến trúc: mã nguồn của phương thức chưa phản ánh toàn bộ hành vi lúc chạy

Trong lập trình hướng khía cạnh (*Aspect-Oriented Programming — AOP*), một phương thức có thể trông rất đơn giản khi đọc mã nguồn nhưng hành vi thực tế còn chịu ảnh hưởng của các advice được gắn vào những join point tương ứng. Ví dụ phương thức xử lý thanh toán chỉ chứa thuật toán nghiệp vụ, trong khi khi gọi qua điểm chặn AOP, hệ thống có thể kiểm tra quyền, tạo trace, đo thời gian, kiểm soát retry hoặc ghi audit.

Cách tổ chức này có lợi: nhiều chức năng dùng chung cơ chế đo đạc và kiểm soát mà không cần sao chép mã. Nhưng nó đồng thời đặt ra hai câu hỏi kiến trúc:

1. **Biên giới mô-đun nằm ở đâu?** Mô-đun nào sở hữu hợp đồng nghiệp vụ, mô-đun nào sở hữu cơ chế AOP, và phần nào được phép biết chi tiết của mô-đun khác?
2. **Có thể hiểu hành vi từ mã nguồn đến mức nào?** Khi đọc một phương thức, người bảo trì phải tìm thêm pointcut, advice, cấu hình weaving/interception, thứ tự áp dụng và đường gọi thực tế nào để biết điều gì sẽ xảy ra?

Một ứng dụng có AOP tốt cần giữ được cả hai: tách trách nhiệm xuyên suốt và giúp người đọc truy tìm được **luồng thực thi có hiệu lực** (*effective execution path*).

## 2. Các khái niệm nền tảng và cách chúng liên quan tới mô-đun

- **Mô-đun kiến trúc:** đơn vị đóng gói một trách nhiệm, có giao diện công khai, các phần triển khai nội bộ và quy tắc phụ thuộc. Nó có thể là package, thư viện, thành phần triển khai độc lập hoặc module của Java Platform Module System (JPMS), tùy kiến trúc.
- **Join point:** vị trí AOP có thể can thiệp, ví dụ lúc phương thức được gọi hoặc thực thi. Các cơ chế AOP khác nhau hỗ trợ những loại join point khác nhau.
- **Pointcut:** biểu thức/quy tắc chọn tập join point chịu chính sách.
- **Advice:** hành vi được thực hiện trước, sau hoặc bao quanh join point; với around advice, phần logic có thể quyết định có chuyển tiếp vào thao tác gốc không.
- **Aspect:** tập hợp pointcut và advice mô tả một mối quan tâm xuyên suốt.
- **Weaving/interception:** cơ chế kết nối advice vào những join point phù hợp, có thể qua proxy, công cụ sửa bytecode lúc build/load hoặc cơ chế khác.

**Mô-đun và aspect không phải cùng một trục phân chia.** Một mô-đun nghiệp vụ có thể sở hữu chức năng đặt hàng. Một mô-đun observability sở hữu logic trace dùng chung nhiều mô-đun; một mô-đun chính sách truy cập có thể áp dụng các kiểm tra xuyên suốt. Tuy nhiên, việc aspect áp dụng ở nhiều nơi không làm nó trở thành chủ sở hữu của toàn bộ quy tắc nghiệp vụ các nơi đó.

### Biên giới cần bảo vệ

| Biên giới | Nên chứa | Không nên tự động kéo sang |
| --- | --- | --- |
| API/hợp đồng mô-đun nghiệp vụ | Kiểu tham số, giá trị trả về, lỗi có chủ đích, ý nghĩa thao tác | Chi tiết đo lường/collector nội bộ |
| Triển khai nội bộ | Thuật toán, bất biến nghiệp vụ, truy cập dữ liệu | Điểm mở công khai chỉ để pointcut nhận diện |
| Mô-đun chính sách AOP | Advice, pointcut, cơ chế ghi metric/trace hoặc kiểm tra chung | Quy tắc domain chưa được xác nhận và dữ liệu riêng tư của mọi mô-đun |
| Thành phần lắp ghép (*composition root*) | Đăng ký aspect, cấu hình scope/thứ tự, lắp các cổng gọi | Logic nghiệp vụ phân tán không kiểm soát |
| Kiểm thử/quan sát | Bằng chứng cho đường gọi trực tiếp và đường qua advice | Giả định rằng chỉ đọc code thân phương thức đã là đủ |

Biên giới mô-đun chính là hợp đồng về điều gì được công khai, điều gì được che giấu và bên nào phải chịu trách nhiệm nếu một hành vi thay đổi.

## 3. Khác biệt giữa luồng gọi nhìn thấy và luồng gọi thực tế

Giả sử đọc được:

~~~java
public String charge(int amount) {
    return ledger.recordCharge(amount);
}
~~~

Đoạn này chỉ cho biết hành vi **trong thân phương thức**, không đủ kết luận về lời gọi khi có AOP. Nếu composition root lắp một advice kiểm tra quyền bên trong một advice tracing, đường chạy có thể là:

~~~text
caller
  |
  v
Tracing advice: enter
  |
  v
Authorization advice: check permission
  |                           |
  | allow                     | deny
  v                           v
Charge method                 throw SecurityException
  |                           |
  v                           |
Authorization advice: return  |
  |                           |
  +------------+--------------+
               v
Tracing advice: success/error
               |
               v
caller receives result/exception
~~~

Nếu thứ tự lắp ngược lại, từ chối quyền có thể diễn ra **trước khi tracing bắt đầu**. Cả hai cách đều có thể hợp lý trong những hợp đồng khác nhau, nhưng người đọc không thể suy ra chỉ từ thân charge().

Một cách diễn đạt thực tế:

> Hành vi quan sát được phụ thuộc vào thân phương thức, các advice thực sự được áp dụng, thứ tự của chúng, cơ chế interception, cấu hình triển khai và đường gọi.

Không có nghĩa mọi hệ thống cần mô tả từng bước runtime bằng tài liệu dài. Cần đủ thông tin để người đọc **lần ra đúng nơi phát sinh hành vi** và kiểm chứng giả thuyết bằng kiểm thử.

## 4. Những nguyên tắc để AOP không phá vỡ biên giới mô-đun

### 4.1. Phụ thuộc vào hợp đồng ổn định, không bám vào chi tiết nội bộ

Pointcut dựa vào tên lớp nội bộ, thứ tự package hoặc phương thức private cụ thể có thể bị ảnh hưởng khi mô-đun refactor. Nếu chính sách thật sự áp dụng cho mọi lời gọi qua một cổng nghiệp vụ, ưu tiên biểu diễn ranh giới công khai và quy tắc chọn có thể kiểm thử.

Chẳng hạn “đo mọi lần gọi qua cổng PaymentOperations” mô tả ranh giới rõ hơn “đo tất cả phương thức có tên chứa process thuộc một package bất kỳ”. Nhưng lựa chọn cụ thể còn tùy cơ chế weaving: với dynamic proxy dựa trên interface, chỉ các lời gọi đi qua proxy mới được chặn.

### 4.2. Mô-đun nghiệp vụ giữ bất biến nghiệp vụ

Kiểm tra số dư, hạn mức, trạng thái đơn hàng, quyền trên đúng tài nguyên, phát sinh sự kiện domain và tính nhất quán transaction thuộc hợp đồng nghiệp vụ. Một aspect kiểm tra quyền tổng quát có thể được tái sử dụng, nhưng aspect không nên tự suy diễn rằng “thao tác thành công tức đã thanh toán” nếu hợp đồng nghiệp vụ chưa xác nhận.

Tương tự, một advice ghi audit truy cập khác với cơ chế tạo sự kiện nghiệp vụ đáng tin: hai loại bản ghi có vòng đời và điều kiện phát sinh khác nhau.

### 4.3. Mô-đun AOP cung cấp cơ chế có giới hạn

Một aspect tốt có nhiệm vụ xác định: nó nhận dữ liệu gì, chạy khi nào, có thể chặn hay thay đổi kết quả không, và lỗi trong aspect được xử lý thế nào. Các trách nhiệm thường thích hợp để dùng chung là:

- Trace và correlation ID tại ranh giới đã chọn.
- Đo duration/số lần gọi và trạng thái kỹ thuật.
- Kiểm tra permission tổng quát theo policy rõ ràng.
- Cơ chế timeout/retry cho thao tác thật sự an toàn khi lặp, đã có hợp đồng.
- Cache cho các thao tác có kết quả tái sử dụng theo khóa và ngữ cảnh đúng.

Nếu cùng một aspect vừa đo thời gian, quyết định giao dịch, cấp quyền cho từng đối tượng và sửa payload nghiệp vụ thì biên giới trách nhiệm đã quá mờ. Khi một chính sách có tác động nghiệp vụ, phải xác định người sở hữu và cách kiểm thử riêng.

### 4.4. Composition root cho thấy cách các mô-đun kết hợp

Thành phần lắp ghép là nơi dễ đọc nhất để biết mô-đun nào được gắn advice nào, cấu hình thứ tự và điều kiện kích hoạt. Có thể dùng khai báo cấu hình, explicit decorators hoặc đăng ký weaving tùy nền tảng.

Nguyên tắc hữu ích là các quyết định ở composition root phải **có thể tìm kiếm, đối chiếu và kiểm thử**. Không làm người bảo trì phải suy từ những quy tắc ngầm không được kiểm soát qua hàng chục module.

## 5. Ví dụ Java 17: tách phần nghiệp vụ khỏi hai lớp advice

Ví dụ dùng Java dynamic proxy để minh họa interception ở một ranh giới interface. Nó chạy bằng JDK chuẩn, không cần thư viện AOP bên ngoài. Thành phần nghiệp vụ chỉ ghi nhận khoản phí giả định; một proxy kiểm tra quyền, một proxy khác ghi trace kỹ thuật. Composition root thể hiện rõ thứ tự áp dụng.

Mặc dù đặt trong một tệp để dễ chạy, các kiểu trong ví dụ đại diện cho những phần có thể tách sang các mô-đun khác nhau.

Tạo tệp **ModularAopDemo.java**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ModularAopDemo {
    record RequestContext(String subject, Set<String> permissions) {
        RequestContext {
            Objects.requireNonNull(subject);
            permissions = Set.copyOf(permissions);
        }
    }

    interface PaymentOperations {
        String charge(RequestContext context, int amount);
    }

    // Trách nhiệm nghiệp vụ: xử lý một lần charge hợp lệ.
    static final class PaymentCore implements PaymentOperations {
        final AtomicInteger charges = new AtomicInteger();

        @Override
        public String charge(RequestContext context, int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            charges.incrementAndGet();
            System.out.println("core:charge");
            return "receipt-" + amount;
        }
    }

    static Object invokeTarget(
            Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static PaymentOperations proxy(InvocationHandler handler) {
        return (PaymentOperations) Proxy.newProxyInstance(
                PaymentOperations.class.getClassLoader(),
                new Class<?>[] { PaymentOperations.class },
                handler
        );
    }

    // Advice kiểm soát permission tổng quát.
    static PaymentOperations authorize(PaymentOperations target) {
        return proxy((instance, method, args) -> {
            RequestContext context = (RequestContext) args[0];
            if (context == null
                    || !context.permissions().contains("CHARGE")) {
                throw new SecurityException("forbidden");
            }
            return invokeTarget(target, method, args);
        });
    }

    // Advice quan sát kỹ thuật, không ra quyết định nghiệp vụ.
    static PaymentOperations trace(PaymentOperations target) {
        return proxy((instance, method, args) -> {
            System.out.println("trace:start");
            try {
                Object result = invokeTarget(target, method, args);
                System.out.println("trace:success");
                return result;
            } catch (Throwable failure) {
                System.out.println(
                        "trace:error=" + failure.getClass().getSimpleName());
                throw failure;
            }
        });
    }

    public static void main(String[] args) {
        PaymentCore core = new PaymentCore();

        // Composition root: tracing bao ngoài kiểm tra quyền.
        PaymentOperations secured = authorize(core);
        PaymentOperations exposed = trace(secured);

        RequestContext allowed = new RequestContext(
                "alice", Set.of("CHARGE"));
        RequestContext denied = new RequestContext(
                "bob", Set.of());

        System.out.println(
                "allowed=" + exposed.charge(allowed, 100));

        try {
            exposed.charge(denied, 200);
        } catch (SecurityException ex) {
            System.out.println("denied=" + ex.getMessage());
        }

        System.out.println("coreCalls=" + core.charges.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ModularAopDemo.java
java ModularAopDemo
~~~

**Stdout theo đúng thứ tự:**

~~~text
trace:start
core:charge
trace:success
allowed=receipt-100
trace:start
trace:error=SecurityException
denied=forbidden
coreCalls=1
~~~

Giải thích:

1. Lời gọi hợp lệ đi qua trace, sau đó authorize, rồi mới tới PaymentCore. Phương thức gốc được gọi đúng một lần và tạo ra receipt-100.
2. Lời gọi không có quyền vẫn đi qua trace bên ngoài, nhưng bị authorize từ chối. PaymentCore không thực thi cho yêu cầu này.
3. Do tracing bọc bên ngoài authorization, hệ thống ghi được cả lần bị từ chối. Nếu đổi thành authorize(trace(core)), lần bị từ chối không đi qua trace. Đây là **khác biệt hành vi do thứ tự lắp advice**, dù mã của PaymentCore không đổi.
4. Nếu một thành phần khác giữ tham chiếu trực tiếp đến core và gọi core.charge(...), cả hai proxy đều bị bỏ qua. Composition root phải chỉ phát hành đúng cổng đã được bảo vệ ra ngoài ranh giới tin cậy.

**Giới hạn minh họa:** RequestContext được truyền như tham số để tái hiện được các tình huống. Trong hệ thống thật, subject và permissions phải đến từ danh tính đã xác thực và nguồn phân quyền tin cậy, không do người gọi tự khai. PaymentCore không xử lý thanh toán thật, transaction, idempotency hay bảo vệ tài nguyên cụ thể. Cơ chế dùng ở đây là dynamic proxy cho interface; phạm vi join point của cơ chế weaving khác có thể rộng hoặc khác hoàn toàn.

### Những điều mã nguồn cho thấy và không cho thấy

Đọc riêng PaymentCore cho biết nghiệp vụ thuần, điều kiện amount > 0 và sự tăng bộ đếm charge. Muốn biết yêu cầu không có quyền bị từ chối hay có trace hay không, phải đọc **authorize()**, **trace()** và **main()**. Muốn biết một đường gọi có đi qua advice thật không, cần xem đối tượng nào được truyền cho caller và kiểm thử đường gọi ấy.

Cách làm này minh họa yêu cầu về khả năng hiểu hành vi: nhà phát triển có thể lần từ cổng công khai tới composition root, qua chuỗi advice rồi đến triển khai lõi; không cần suy đoán ở các điểm ẩn.

## 6. AOP có thể tạo phụ thuộc kiến trúc ẩn như thế nào?

### 6.1. Pointcut quá rộng

Giả sử một quy tắc chọn mọi phương thức có tiền tố save trong toàn bộ ứng dụng. Khi một mô-đun khác thêm saveDraft() với ý nghĩa mới, advice có thể tự động tác động vào phương thức ấy mà người viết không biết. Nếu advice chỉ ghi metric nhẹ, tác động có thể là overhead và nhiễu số liệu. Nếu advice thực hiện retry, ghi audit bắt buộc hoặc chặn quyền, hậu quả còn lớn hơn.

Thay đổi cấu trúc package, tên phương thức hoặc phạm vi public cũng có thể vô tình làm pointcut ngừng khớp. Cần xác định **phạm vi dự định** và có kiểm thử xác nhận tập join point được chọn.

### 6.2. Aspect đọc cấu trúc nội bộ của nhiều mô-đun

Một advice dùng reflection để truy cập trường private, suy đoán ID người dùng từ vị trí tham số hoặc gọi trực tiếp repository nội bộ sẽ phụ thuộc vào chi tiết triển khai. Refactor hợp lệ của mô-đun có thể phá aspect.

Nên cung cấp contract công khai như operation name, metadata của ngữ cảnh, interface chính sách hoặc adapter được chủ sở hữu mô-đun chấp nhận. Nếu cần khai báo pointcut bằng annotation, định nghĩa ngữ nghĩa annotation rõ ràng và tránh tạo vòng phụ thuộc giữa mô-đun domain với thư viện cơ chế không cần thiết.

### 6.3. Mô-đun nghiệp vụ phụ thuộc ngược vào aspect

Một nghiệp vụ luôn cần ghi “transaction đã commit” mà chỉ trông đợi một advice có thể bật/tắt hoặc bỏ qua sẽ trở nên mong manh. Những bất biến và hiệu ứng bắt buộc phải được biểu diễn trong hợp đồng nghiệp vụ/transaction; aspect chỉ nên thực hiện chính sách xuyên suốt theo thiết kế có kiểm chứng.

Tương tự, không cho cơ chế tracing quyết định tính thành công của nghiệp vụ nếu mất log; một collector quan sát gặp lỗi không nên mặc định làm hỏng kết quả trả về.

### 6.4. Có quá nhiều cơ chế can thiệp cùng một điểm

Khi authorization, cache, retry, transaction, tracing và exception mapping đều quanh một join point, số thứ tự khả dĩ tăng nhanh. Không có thứ tự “luôn đúng” cho mọi hệ thống. Phải xác định phụ thuộc ngữ nghĩa, chẳng hạn:

- Quyền truy cập phải được kiểm soát trước khi dữ liệu từ cache có thể được tiết lộ.
- Retry chỉ an toàn với lời gọi idempotent hoặc đã có thiết kế chống lặp.
- Tracing bên ngoài hay bên trong retry quyết định đếm một thao tác tổng hay từng attempt.
- Thời điểm transaction bắt đầu/commit quyết định advice nào quan sát được thành công thật sự.
- Exception translation có thể làm thay đổi loại lỗi mà advice khác nhìn thấy.

Cần ghi nhận thứ tự và lý do cho những tương tác có hậu quả nghiệp vụ hoặc bảo mật.

## 7. Khi nào mã nguồn dễ hiểu, khi nào trở nên khó đọc?

### Khả năng hiểu được cải thiện khi

- Cổng public mô tả rõ dữ liệu đầu vào, kết quả và lỗi có thể xảy ra.
- Mô-đun lõi tập trung vào domain, không trộn logging và metric ở mọi lớp.
- Mỗi aspect có một nhiệm vụ dễ gọi tên, phạm vi pointcut được giới hạn và kiểm thử được.
- Quy tắc kích hoạt và thứ tự được công khai ở composition root hoặc nơi đăng ký tương đương.
- Trace/log cung cấp danh tính operation nhất quán giữa module, nhưng không làm rò rỉ dữ liệu nhạy cảm.
- Sơ đồ luồng và tài liệu nêu cả các đường không được weaving hoặc không đi qua proxy.
- Kiểm thử tích hợp chạy trên cấu hình lắp ghép thật thay vì chỉ gọi trực tiếp mã lõi.

### Khả năng hiểu bị giảm khi

- Người đọc phải mở rất nhiều aspect để biết một phương thức có được gọi hay không.
- Pointcut chọn theo mẫu tên/package mơ hồ nên thêm mã mới vô tình thay đổi hành vi.
- Advice âm thầm thay kết quả, nuốt lỗi hoặc retry thao tác có tác dụng phụ.
- Thứ tự khía cạnh được xác định bởi cấu hình khó tìm hoặc không được kiểm thử.
- Kiểu phương thức/return value bị proxy đổi khác hợp đồng.
- Cơ chế context dùng trạng thái toàn cục ngầm và không truyền đúng qua async.
- Hai mô-đun lạm dụng dữ liệu nội bộ của nhau để tạo quyết định trong advice.

Một nguyên tắc hữu ích là **mã lõi rõ nghĩa và đường can thiệp cũng phải tra cứu được**. Tránh đánh đổi sự lặp lại nhỏ của vài dòng code bằng một hệ thống chính sách không ai xác định được luồng chạy.

## 8. Khả năng truy vết hành vi: từ source đến runtime

Khi điều tra câu hỏi “vì sao phương thức A trả lỗi trước khi chạy?”, người bảo trì cần một lộ trình nhất quán:

1. **Tìm cổng công khai:** interface hoặc API mô tả thao tác, đầu vào và lỗi.
2. **Tìm điểm lắp ghép:** cổng thực tế được tạo từ implementation và những proxy/advice nào?
3. **Tìm pointcut:** join point cần xem có thật sự được chọn không? Có điều kiện cấu hình hoặc annotation nào?
4. **Xác định thứ tự advice:** lớp nào bọc ngoài, lớp nào bọc trong, và lúc ném lỗi luồng đi qua lớp nào?
5. **Kiểm tra quyền/nguồn dữ liệu:** advice dựa vào metadata, ThreadLocal, parameter hay trạng thái nào; dữ liệu đó đáng tin không?
6. **Theo dõi proceed/invoke:** trường hợp nào bỏ qua gọi lõi, trường hợp nào gọi nhiều lần, trường hợp nào biến đổi kết quả?
7. **Đối chiếu bằng kiểm thử/trace:** sử dụng đường gọi thật, xác nhận luồng kết quả, lỗi và hành vi khi bật/tắt aspect.

Một bảng **bản đồ can thiệp** theo module có thể giúp:

| Cổng hoặc nhóm thao tác | Aspect áp dụng | Thứ tự có ý nghĩa | Điều kiện cần xác minh |
| --- | --- | --- | --- |
| Charge operations | Trace, authorization | Trace bao ngoài authorization | DENY không chạy lõi; có trace cho DENY |
| Query read-only | Authorization, cache, metrics | Quyền được thực thi trước khi tiết lộ dữ liệu | Không rò dữ liệu qua cache hit |
| External call có retry | Trace, retry, timeout | Scope một request so với từng attempt | Idempotency và giới hạn thời gian |
| Publish event | Metrics, exception handling | Quan sát lần gửi và trạng thái gửi | Không nhầm attempt với domain event |

Bảng này là tài liệu kiến trúc minh họa, **không phải định nghĩa mặc định** cho mọi dự án. Lưu nó gần cấu hình lắp ghép và cập nhật khi pointcut/thứ tự đổi; tốt hơn nữa, kiểm thử tự động có thể xác minh một phần bất biến từ bản đồ.

## 9. Mô-đun trong Java: package, interface và JPMS

Trong Java, chữ “module” có thể chỉ một thành phần kiến trúc hoặc một JPMS module khai báo bằng module-info.java. Hai khái niệm liên quan nhưng không đồng nhất:

- Package giúp tổ chức mã và kiểm soát truy cập theo visibility.
- Interface và kiểu dữ liệu công khai xác định hợp đồng phụ thuộc.
- JPMS có thể khai báo requires, exports và opens, qua đó điều khiển khả năng đọc module, khả năng truy cập API và quyền reflection theo chính sách runtime.
- Một mô-đun kiến trúc vẫn có thể gồm nhiều package/JPMS modules; ngược lại, một JPMS module có thể chứa nhiều trách nhiệm nếu thiết kế chưa tốt.

Nếu cơ chế AOP dùng reflection hay proxy, phạm vi exports/opens và accessibility của các kiểu có thể ảnh hưởng khả năng interception. **Không nên mở toàn bộ package nội bộ chỉ để một pointcut tiện truy cập.** Ưu tiên thiết kế API cho điểm tích hợp rõ và cấp quyền truy cập tối thiểu theo cơ chế thực tế sử dụng.

Không phải mọi phương thức private, constructor hay lời gọi nội bộ đều có thể bị can thiệp chỉ vì dự án đã bật AOP. Đây là giới hạn phụ thuộc công nghệ weaving; cần xác minh bằng tài liệu cơ chế đang dùng và kiểm thử đường thực tế.

## 10. Self-invocation và đường gọi bypass cơ chế AOP

Với cơ chế interception dựa trên proxy, lời gọi từ bên ngoài đi qua proxy có thể bị advice chặn. Nhưng một phương thức trong implementation gọi một phương thức khác trực tiếp trên cùng đối tượng (self-invocation) có thể không đi qua proxy.

Ví dụ mô hình:

~~~text
caller -> proxy -> Service.publicMethod()
                       |
                       v
                  this.internalMethod()
                       |
                    (không đi qua proxy)
~~~

Nếu một chính sách bảo mật hoặc giao dịch chỉ nằm ở interceptor của internalMethod(), đường gọi nội bộ trên một đối tượng như vậy có thể bỏ qua chính sách. Cơ chế bytecode weaving tại điểm thực thi có thể có phạm vi khác; không được áp dụng kết luận self-invocation của proxy cho mọi cơ chế AOP.

Giải pháp phụ thuộc trách nhiệm: đặt enforcement ở cổng đáng tin mà mọi đường cần bảo vệ phải đi qua, trích một cộng tác viên có interface được bọc đúng, hoặc dùng phương án weaving được xác nhận. Đối với bất biến nghiệp vụ bắt buộc, không đặt toàn bộ an toàn vào một advice có thể bị bỏ qua bởi đường gọi hợp lệ khác.

## 11. Phân biệt cross-cutting concern với nghiệp vụ cốt lõi

Không phải logic xuất hiện ở nhiều nơi đều nên chuyển thành aspect.

**Các cơ chế phù hợp** thường có tính thống nhất: đo thời gian một nhóm lời gọi, tạo span, gắn correlation ID, kiểm tra quyền tổng quát theo policy rõ, ghi nhận thống kê cache.

**Các quy tắc cần thuộc domain** thường liên quan ý nghĩa: thanh toán có hợp lệ, đơn hàng đã chuyển trạng thái, tồn kho có cho phép đặt, người dùng có thể sửa một tài nguyên trong trạng thái nào, event nghiệp vụ nào phải phát sinh.

Một phép thử thiết kế: nếu vô hiệu hóa aspect, thuật toán domain có vẫn xác định đúng kết quả theo hợp đồng không? Với advice chỉ ghi telemetry, câu trả lời thường là có. Với aspect bảo mật hoặc transaction bắt buộc, vô hiệu hóa có thể gây vi phạm; khi đó phải coi nó là **thành phần bắt buộc của ranh giới thực thi**, có kiểm thử và cấu hình fail-closed thay vì một tùy chọn ẩn.

Không dùng việc “đặt trong aspect” để tránh phải xác định owner cho quy tắc. Cơ chế triển khai chỉ là một phần của quyết định kiến trúc; tính đúng và quyền sở hữu policy vẫn phải có người chịu trách nhiệm.

## 12. Thứ tự advice, ngoại lệ và hành vi trả về

Một advice có thể thay đổi nhiều thứ ngoài việc ghi log:

- **Before advice** có thể từ chối trước khi lõi chạy.
- **Around advice** có thể bỏ qua, lặp lại hoặc thay đổi kết quả từ thao tác gốc.
- **After-returning** chỉ thấy đường trả về bình thường theo join point được chọn; không có nghĩa transaction đã commit ở một nơi khác.
- **After-throwing** thấy ngoại lệ đi qua ranh giới tương ứng; không phải mọi lỗi bất đồng bộ xảy ra sau khi phương thức trả về đều được quan sát ở đây.
- **After/finally** có thể chạy cả đường thành công và thất bại, nhưng lỗi mới từ code quan sát có thể che mất lỗi gốc nếu xử lý kém.

Nếu một phương thức trả CompletionStage/Future, return của phương thức tạo tác vụ thường chỉ chứng tỏ đã trả handle; không đồng nghĩa công việc bất đồng bộ hoàn tất. Một aspect chỉ đo khoảng trước/sau proceed có thể báo duration nhỏ hơn nhiều so với độ trễ kết quả cuối. Khi yêu cầu quan sát completion phải gắn callback hoặc instrument nơi hoàn tất, và nêu rõ trạng thái lỗi/huỷ.

Đây là ví dụ điển hình cho khả năng hiểu mã nguồn: một phương thức có vẻ “trả kết quả” có thể thực tế mới hoàn tất **bước gửi việc**. Tài liệu hợp đồng và tên metric phải xác định rõ ranh giới được đo.

## 13. Kiểm thử ranh giới mô-đun và khả năng hiểu hành vi

Nên có các lớp kiểm thử phục vụ mục đích khác nhau:

### Kiểm thử logic lõi

Gọi trực tiếp implementation để xác nhận bất biến nghiệp vụ, loại trừ việc advice gây nhiễu. Ví dụ kiểm tra amount không hợp lệ bị từ chối và không tạo charge. Kiểm thử này **không** chứng minh cổng đã được bảo vệ bởi AOP.

### Kiểm thử chuỗi advice thực tế

Khởi tạo đúng composition root hoặc đúng cấu hình weaving rồi xác nhận:

1. Lời gọi được phép đi qua advice theo thứ tự mong muốn và vào lõi đúng số lần.
2. Lời gọi bị từ chối không vào lõi, trả lỗi đúng hợp đồng.
3. Advice ghi telemetry không thay đổi giá trị trả về hoặc ngoại lệ gốc.
4. Bật/tắt các phần quan sát tùy chọn không làm đổi nghiệp vụ.
5. Các điểm không được chọn bởi pointcut không bị can thiệp ngoài ý muốn.
6. Các đường self-invocation, direct-call và async được xử lý đúng giới hạn cơ chế.

### Kiểm thử kiến trúc

Kiểm tra dependency direction và ownership: ví dụ module domain không import collector quan sát, mô-đun AOP không gọi trực tiếp lớp repository nội bộ, các kiểu API được công khai tối thiểu, và danh sách pointcut áp dụng cho đúng cổng dự định.

Đối với chính sách bảo mật và transaction, nên có kiểm thử tích hợp trên đường đi thật. Mock lời gọi trực tiếp đến class lõi có thể bỏ qua proxy và cho kết quả đánh lừa.

## 14. Sai lầm phổ biến và cách khắc phục

| Sai lầm | Tại sao làm khó hiểu hoặc sai kiến trúc? | Cách sửa |
| --- | --- | --- |
| Đọc thân phương thức rồi cho rằng đó là toàn bộ runtime | Không nhìn thấy advice và composition | Lần theo cổng, pointcut, thứ tự, đường gọi thật |
| Pointcut trùm toàn bộ namespace quá rộng | Mô-đun mới vô tình chịu chính sách cũ | Chọn ranh giới có ý nghĩa; kiểm thử match set |
| Aspect dùng reflection truy cập nhiều private field | Coupling với chi tiết nội bộ | Cấp metadata/hợp đồng tối thiểu |
| Nhiều aspect tự sửa cùng return value | Kết quả phụ thuộc thứ tự khó theo dõi | Phân rõ owner và thứ tự, hạn chế biến đổi |
| Retry mọi phương thức ở advice | Có thể lặp thao tác không idempotent | Chỉ retry theo hợp đồng và chính sách rõ |
| Tracing hoặc cache che kiểm tra quyền | Có thể lộ dữ liệu hoặc mất audit | Xác minh thứ tự và ranh giới bảo mật |
| Coi return bình thường là commit/domain event | Sự kiện sai khi rollback hoặc async còn chạy | Quan sát đúng mốc nghiệp vụ |
| Bảo vệ chỉ bằng proxy nhưng caller giữ reference tới core | Đường trực tiếp bypass advice | Composition root chỉ xuất cổng được bảo vệ |
| Đọc code lõi nhưng không kiểm thử aspect | Test xanh dù runtime thiếu policy | Kiểm thử lắp ghép và đường thực thi thực |
| Bật aspect theo cấu hình khó tìm | Khó tái hiện hành vi giữa môi trường | Tập trung cấu hình và công khai trạng thái áp dụng |

## 15. Checklist để giữ AOP vừa hữu ích vừa dễ bảo trì

1. Xác định hợp đồng công khai và owner của mỗi mô-đun.
2. Viết rõ lý do cross-cutting concern cần áp dụng xuyên nhiều cổng.
3. Giữ logic domain, bất biến và sự kiện nghiệp vụ trong phạm vi sở hữu đúng.
4. Định nghĩa pointcut bằng tiêu chí ổn định, kiểm chứng được tập join point.
5. Giới hạn dữ liệu nội bộ mà aspect được phép đọc/ghi.
6. Tập trung nơi đăng ký và thứ tự advice; mô tả lý do của các thứ tự quan trọng.
7. Bảo vệ tất cả đường gọi cần enforcement, gồm direct-call, self-invocation và async.
8. Ghi rõ khi nào advice được phép chặn, retry, cache hoặc đổi return/exception.
9. Cho người đọc đường truy tìm từ API công khai đến code lõi và các aspect thực sự áp dụng.
10. Kiểm thử song song domain thuần, composition có AOP và ranh giới kiến trúc.
11. Đo overhead của advice và tránh logging/metric quá nặng trong đường nóng.
12. Xem lại pointcut và chính sách AOP khi refactor API, di chuyển package, thay đổi giao dịch hoặc bổ sung mô-đun.

**Kết luận:** AOP có thể làm mã nghiệp vụ gọn và tăng tái sử dụng cơ chế xuyên suốt, nhưng nó thêm một lớp hành vi mà người đọc thân phương thức không nhìn thấy ngay. Kiến trúc tốt đặt aspect ở ranh giới rõ ràng, giữ quyền sở hữu domain, công khai composition và thứ tự can thiệp, đồng thời có kiểm thử xác nhận hành vi runtime. Khi đó, tách mô-đun và khả năng hiểu mã nguồn hỗ trợ nhau thay vì xung đột.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-007">So sánh với decorator, middleware, interceptor và instrumentation</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh Aspect (AOP) với Decorator, Middleware, Interceptor và Instrumentation

## 1. Vì sao cần phân biệt?

Khi muốn bổ sung ghi log, đo thời gian, kiểm tra quyền, cache, retry hoặc theo dõi lỗi vào nhiều thành phần, lập trình viên thường gặp những tên gọi: **Aspect-Oriented Programming (AOP)**, **Decorator**, **Middleware**, **Interceptor** và **Instrumentation**. Chúng có thể cùng giải quyết một vấn đề xuyên suốt (*cross-cutting concern*), nhưng **không cùng cấp độ khái niệm, không luôn cùng phạm vi can thiệp và không bảo đảm cùng khả năng kiểm soát lời gọi**.

- **AOP** là cách tổ chức chương trình theo các *concern* cắt ngang nhiều điểm thực thi: định nghĩa *join point*, *pointcut*, *advice* và cách gắn chúng vào chương trình.
- **Decorator** là mẫu thiết kế bao một đối tượng sau một giao diện tương thích để thêm hành vi qua lời gọi vào wrapper.
- **Middleware** thường là một giai đoạn trong *pipeline* xử lý request, message hoặc một tác vụ, có thể gọi chặng kế tiếp và xử lý kết quả trên đường quay về.
- **Interceptor** là một cơ chế/hook chặn một loại hoạt động nhất định, có thể chạy trước/sau hoặc quyết định tiếp tục, tùy hợp đồng.
- **Instrumentation** là kỹ thuật chèn điểm quan sát hoặc điều khiển vào chương trình/runtime, có thể thông qua bytecode, compiler, agent, profiling hook hoặc mã đo thủ công.

Những phạm trù này **có thể chồng lấn**: một bộ máy AOP có thể triển khai advice bằng interceptor, proxy hoặc bytecode instrumentation; một decorator có thể chứa logic như một advice; middleware có thể là interceptor chuyên cho pipeline. Không nên xếp chúng thành những công nghệ hoàn toàn loại trừ nhau.

Bài học nhằm giúp chọn đúng công cụ dựa trên **điểm can thiệp**, **phạm vi bao phủ**, **cách cấu hình**, **ngữ nghĩa của proceed/next**, **khả năng short-circuit**, **chi phí vận hành** và **yêu cầu về tính đúng đắn**.

## 2. Trục so sánh quan trọng nhất: can thiệp ở đâu?

Một thiết kế cần trả lời trước: **Sự kiện nào tạo ra hook?** Không thể chọn chỉ dựa vào việc muốn “thêm log”.

| Trục | Câu hỏi thiết kế |
| --- | --- |
| **Đối tượng bị chặn** | Một method của đối tượng, một request qua pipeline, một API call, hay sự kiện JVM/runtime? |
| **Đường đi được bảo vệ/quan sát** | Có buộc tất cả đường gọi đi qua wrapper/hook không? |
| **Bắt đầu/kết thúc** | Quanh lời gọi đồng bộ, toàn request, từng message, hay tới lúc tác vụ bất đồng bộ hoàn tất? |
| **Khả năng quyết định luồng** | Chỉ quan sát, biến đổi dữ liệu, gọi lại, trì hoãn, hay có thể không chạy target? |
| **Khả năng chọn điểm** | Đăng ký thủ công, chọn route, pattern method, annotation, signature, hay chèn bytecode? |
| **Ngữ cảnh** | Tham số method, request headers, principal, transaction, trace context, thread state? |
| **Chi phí/độ phức tạp** | Wrapper thủ công, registry middleware, proxy, weaving, agent hoặc profiler? |
| **Tính minh bạch** | Caller và người đọc code có nhận ra logic bổ sung không? Khả năng debug/test ra sao? |

Cùng một hoạt động `charge(order)` có thể được nhìn qua middleware của request đầu vào, interceptor ở tầng transport client, decorator của `PaymentClient`, advice tại execution của `PaymentService.charge`, hoặc instrumentation quanh lệnh I/O. **Mỗi điểm phản ánh một phạm vi khác nhau**; không thể dùng dấu “request đã trả 200” để kết luận mọi việc ở worker nền đã hoàn tất.

## 3. AOP: mô hình concern → pointcut → advice

AOP giải quyết vấn đề một chính sách lặp trên nhiều nơi trong chương trình. Các khái niệm nền tảng:

- **Aspect:** đơn vị đóng gói concern xuyên suốt và các quy tắc áp dụng.
- **Join point:** vị trí trong thực thi có thể được bộ máy AOP hỗ trợ can thiệp, ví dụ call/execution của method. Bộ máy khác nhau hỗ trợ tập join point khác nhau.
- **Pointcut:** biểu thức chọn join point cần áp chính sách.
- **Advice:** logic chạy tại join point, chẳng hạn *before*, *after returning*, *after throwing*, *after/finally* hoặc *around* theo khả năng nền tảng.
- **Weaving:** quá trình gắn advice vào join point, có thể xảy ra khi biên dịch, tải lớp hoặc tại runtime. Một số hệ thống dùng proxy thay vì biến đổi bytecode.
- **Proceed:** lời gọi tiếp tục tới advice tiếp theo hoặc target trong *around advice*, theo hợp đồng của bộ máy.

Sơ đồ khái niệm:

~~~text
                  +-----------------------------+
Policy "audit" -->| Aspect: pointcut + advice   |
                  +--------------+--------------+
                                 |
                 +---------------+----------------+
                 |               |                |
          join point A      join point B     join point C
          method call       method exec      runtime event
                 |               |                |
             [audit]          [audit]           [audit]
~~~

Không phải mọi bộ máy AOP đều hỗ trợ cả ba loại ví dụ trong sơ đồ: **join point là tập điểm mà công cụ cụ thể cho phép**, chứ không tự động là “mọi thứ xảy ra trong chương trình”.

### Ưu điểm

AOP hữu ích khi một chính sách thực sự xuyên nhiều method/type, cần được chọn bằng pointcut, có thể quản lý nhất quán ở một nơi và thay đổi mà ít làm xáo trộn nghiệp vụ. Ví dụ: gắn measurement lên toàn bộ một tập operation, áp policy kiểm soát truy cập ở những boundary được quy định, hoặc thêm trace correlation.

### Chi phí

Logic có thể trở nên “ẩn” với người chỉ nhìn body phương thức. Pointcut quá rộng, thứ tự advice phức tạp, proxy không được sử dụng hoặc weaving chưa kích hoạt có thể khiến hành vi bất ngờ. AOP không tự bảo đảm rằng mọi method đều bị chặn hoặc transaction/caching/security thực sự đúng.

## 4. Decorator: một đối tượng bao một đối tượng khác

**Decorator** là mẫu thiết kế cấu trúc: một wrapper triển khai giao diện tương đương rồi ủy quyền cho đối tượng bên trong, thêm hành vi trước/sau lời gọi hoặc thay đổi cách trả kết quả.

~~~text
Caller --> LoggingDecorator --> CachingDecorator --> RealService
              |                     |
           ghi log               hit: trả ngay
                                 miss: gọi tiếp
~~~

Một decorator có thể thêm tracing, validation, giới hạn số lần gọi, caching hoặc biến đổi kết quả. Vì wrapper **nắm quyền gọi delegate**, nó cũng có thể không gọi delegate, gọi nhiều lần hoặc thay đổi exception nếu hợp đồng cho phép.

Điểm mạnh:

- Tường minh tại nơi tạo/lắp ráp object graph; dễ hiểu thứ tự các wrapper.
- Không cần bộ weaving hoặc runtime agent.
- Dễ viết unit test bằng fake delegate.
- Có thể dùng trong môi trường Java thuần, bất kể có framework AOP hay không.

Giới hạn:

- Mỗi wrapper thường phải triển khai/forward các method của giao diện; nhiều interface hoặc nhiều type có thể tạo boilerplate.
- Chỉ lời gọi đi qua wrapper được bảo vệ. Đoạn code giữ tham chiếu tới đối tượng gốc có thể bỏ qua decorator.
- Lời gọi nội bộ từ method của đối tượng gốc tới method khác trên cùng `this` không đi vòng qua wrapper bên ngoài.
- Nếu không tổ chức composition rõ ràng, wrapper order có thể thay đổi hành vi.
- Metadata hay join point nằm ngoài giao diện/đường delegate không tự được intercept.

**Điểm khác với AOP:** decorator là **cách bao đối tượng cụ thể**; AOP là **mô hình mô tả và áp chính sách trên tập join point**. Có thể dùng decorator để triển khai một aspect cho một interface, nhưng chỉ dùng vài decorator không mặc nhiên có ngôn ngữ pointcut hoặc weaving.

## 5. Middleware: phần tử trong pipeline xử lý

**Middleware** là một tầng trong chuỗi xử lý, thường được tổ chức kiểu:

~~~text
Request --> Auth --> Trace --> RateLimit --> Handler
                \      |         |         /
                 \----- Response <---------/
~~~

Một middleware thường nhận **context của pipeline**, thao tác request/message, sau đó gọi `next` hoặc không gọi tiếp. Nó có thể trả response lỗi sớm, thêm header, đo thời gian, xử lý exception hoặc gắn correlation ID.

Mẫu tổng quát:

~~~text
middleware(context, next):
    before(context)
    if rejected(context):
        return rejection
    result = next(context)
    after(context, result)
    return result
~~~

Đây là pseudocode. Cơ chế `next` không nhất thiết là một lời gọi đồng bộ: ở pipeline bất đồng bộ, `next` có thể trả future/promise hoặc dùng callback. Phải đọc hợp đồng của pipeline để biết “after” chạy khi return handle hay sau completion.

Middleware phù hợp khi concern chủ yếu sống ở **boundary của pipeline**: HTTP request/response, message ingestion, RPC call chain, queue processing, command pipeline. Nó thường thấy được headers, transport metadata, session/request context; nhưng không tự quan sát tất cả method Java mà handler gọi sâu bên trong.

**Giới hạn đường đi:** một scheduled job không đi qua pipeline HTTP thì middleware HTTP không chạy; một function gọi trực tiếp business service cũng có thể bỏ qua pipeline. Nếu một thao tác nhạy cảm phải luôn được bảo vệ, chỉ gắn authorization ở HTTP middleware là chưa đủ khi còn entry point khác.

Middleware có thể được cài như một chuỗi interceptor hoặc decorator của handler. Tên “middleware” nhấn mạnh **vai trò trong pipeline**, không bắt buộc một kỹ thuật bytecode cụ thể.

## 6. Interceptor: hợp đồng chặn một loại hoạt động

**Interceptor** là một tên rộng cho hook được mời chạy trước, quanh hoặc sau một hoạt động mà hệ thống quản lý: method invocation, request dispatch, database call, serialization, message send, v.v.

Một interceptor có thể:

- Nhận metadata của invocation và parameters.
- Ghi nhận trạng thái trước khi chạy.
- Quyết định có gọi `proceed()`/`next()` không nếu API hỗ trợ.
- Bọc kết quả, dịch exception hoặc thực hiện logic sau thực thi.
- Chuyển tiếp request hoặc từ chối nó.

Khái niệm này **không quy định sẵn pointcut, weaving, cách tìm tất cả call site hay phạm vi cần bảo vệ**. Một method interceptor triển khai trên dynamic proxy chỉ thấy lời gọi qua proxy. Một transport interceptor chỉ thấy request đi qua transport. Một interceptor ở compiler/runtime có thể thấy những điểm khác.

**Không phải interceptor nào cũng được quyền bỏ qua target.** Một callback chỉ đọc dữ liệu trước/sau sự kiện là observer; một quanh-invocation interceptor mới có thể short-circuit nếu API cho phép. “Interceptor” mô tả vị trí/hợp đồng hook, còn “aspect” nhấn mạnh concern và quy tắc chọn tập join point. Chúng thường phối hợp: AOP runtime phân giải pointcut rồi gọi một chuỗi method interceptors.

## 7. Instrumentation: thay đổi hoặc quan sát thực thi ở tầng kỹ thuật

**Instrumentation** là quá trình thêm mã quan sát/kiểm soát vào chương trình. Có hai dạng cần phân biệt:

1. **Instrumentation thủ công:** lập trình viên chèn log, counter, span hoặc timer tại các method/tác vụ cần theo dõi.
2. **Instrumentation tự động/động:** compiler plugin, bytecode transformer, Java agent, class-load hook, profiler hoặc tooling runtime thêm điểm quan sát vào các vùng có thể instrument.

Instrumentation có thể là **cơ chế để thực hiện AOP weaving**, nhưng cũng có thể phục vụ profiling/telemetry mà không có khái niệm aspect hay pointcut ở lớp ứng dụng. Ví dụ agent ghi lại số lần chạy method bằng bytecode instrumentation có thể hữu ích cho chẩn đoán; nó không tự là một transaction/security aspect.

### Java agent ở mức khái niệm

Trong Java, một agent có thể dùng `java.lang.instrument.Instrumentation`, với manifest thích hợp và điểm vào như `premain` hoặc `agentmain` theo chế độ cài đặt, để đăng ký `ClassFileTransformer` và biến đổi bytecode của lớp phù hợp. Việc biến đổi chính xác bị giới hạn bởi JVM/class format, class loader, module boundaries và khả năng retransformation/redefinition được hỗ trợ. **Không phải mọi phép đổi đều được phép với lớp đã load**, và không phải mọi agent có khả năng skip method hoặc sửa semantics tùy ý.

Dòng xử lý minh họa:

~~~text
JVM loads class
    |
    +--> transformer được đăng ký?
            |
            +--> chọn lớp/method có thể transform
                     |
                     +--> tạo bytecode đã instrument hợp lệ
                              |
                              +--> class được dùng để thực thi
~~~

Với lớp đã load, nếu công cụ và JVM hỗ trợ, có thể cần retransformation; chỉ đăng ký transformer sau lúc class đã được load không có nghĩa lớp ấy đương nhiên được thay đổi.

Rủi ro instrumentation:

- Bytecode hỏng hoặc không tương thích làm class không load được.
- Overhead CPU, memory allocation hoặc trace volume lớn.
- Thay đổi hành vi do thời điểm instrumentation/class loading.
- Context class loader, dynamic class generation, module access và version JVM.
- Khó debug vì logic không thấy trong source của method.
- Instrumentation sâu làm tăng bề mặt quan sát dữ liệu nhạy cảm; cần redaction và giới hạn quyền agent.

Instrumentation phù hợp khi cần bao phủ một tập method lớn mà khó sửa source hoặc cần quan sát sâu ở runtime. Nhưng nếu chỉ muốn thêm logic cho một interface do chính mình kiểm soát, decorator/interceptor đơn giản hơn thường dễ kiểm chứng.

## 8. Bảng so sánh tổng hợp

| Tiêu chí | AOP | Decorator | Middleware | Interceptor | Instrumentation |
| --- | --- | --- | --- | --- | --- |
| **Bản chất** | Mô hình tách cross-cutting concern bằng pointcut/advice | Mẫu bao đối tượng cùng giao diện | Thành phần pipeline | Hợp đồng hook quanh một hoạt động | Kỹ thuật chèn điểm quan sát/điều khiển |
| **Điểm áp dụng** | Join point do nền tảng hỗ trợ | Lời gọi qua wrapper | Chặng request/message/job pipeline | Điểm mà host phát hook | Vị trí source/bytecode/runtime được instrument |
| **Cách chọn phạm vi** | Pointcut, metadata, quy tắc | Lắp wrapper cụ thể | Đăng ký pipeline/route | Đăng ký hook/selector tùy host | Chọn lớp/method/event/bytecode |
| **Short-circuit** | Có nếu around advice/hệ thống hỗ trợ | Có khi wrapper quyết định không delegate | Có nếu pipeline cho phép không gọi next | Phụ thuộc interceptor contract | Phụ thuộc loại instrumentation/hook |
| **Self-invocation** | Proxy-based có thể bỏ qua; weaving tùy join point | Đường gọi nội bộ của delegate bỏ wrapper | Không liên quan trực tiếp method nội bộ | Tùy host/proxy | Tùy điểm instrument |
| **Lời gọi không qua boundary** | Không được chặn nếu ngoài pointcut/interception | Bỏ qua wrapper | Không đi qua pipeline | Host không kích hoạt hook | Ngoài vùng instrument |
| **Phụ thuộc runtime** | Proxy, weaving hoặc cơ chế khác | Chủ yếu composition object | Pipeline host | Interceptor host | Tooling/JVM/compiler/agent |
| **Quan sát sâu trong hàm** | Tùy join point mà bộ máy hỗ trợ | Không tự động | Không tự động | Tùy loại hook | Có thể nếu cơ chế hỗ trợ |
| **Độ rõ của luồng gọi** | Logic có thể ẩn bởi pointcut/weaving | Thứ tự wrapper tương đối tường minh | Chuỗi pipeline thường rõ | Tùy registry/hook | Có thể khó truy vết từ source |
| **Phù hợp** | Policy xuyên nhiều điểm | Mở rộng một object/interface | Xử lý đầu/cuối pipeline | Hook vào tầng có API | Tracing/profiling/weaving sâu |

Bảng mô tả **khuynh hướng phổ biến**, không phải giới hạn tuyệt đối. Chẳng hạn một middleware cho request có thể được viết như một decorator của handler; một aspect có thể được thực thi bằng một chuỗi interceptors; instrumentation có thể đóng vai trò weaving cho AOP.

## 9. Ví dụ Java 17: cùng chức năng log theo ba cơ chế

Ví dụ độc lập sau cho thấy rõ **decorator**, **method interceptor qua dynamic proxy** và **middleware pipeline**. Cả ba đều ghi log trước/sau, nhưng điểm điều khiển khác nhau.

Lưu toàn bộ mã dưới tên **`MechanismComparisonDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MechanismComparisonDemo {
    interface PriceService {
        int quote(String sku, int base);
    }

    static final class PriceServiceImpl implements PriceService {
        @Override
        public int quote(String sku, int base) {
            System.out.println("[service] quote " + sku);
            return base + 3;
        }
    }

    static final class LoggingDecorator implements PriceService {
        private final PriceService delegate;

        LoggingDecorator(PriceService delegate) {
            this.delegate = delegate;
        }

        @Override
        public int quote(String sku, int base) {
            System.out.println("[decorator] before");
            int result = delegate.quote(sku, base);
            System.out.println("[decorator] after");
            return result;
        }
    }

    @FunctionalInterface
    interface MethodInterceptor {
        Object invoke(Invocation invocation) throws Throwable;
    }

    record Invocation(Object target, Method method, Object[] arguments) {
        Object proceed() throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }

    static PriceService withInterceptor(
            PriceService target, MethodInterceptor interceptor) {
        return (PriceService) Proxy.newProxyInstance(
                PriceService.class.getClassLoader(),
                new Class<?>[]{ PriceService.class },
                (proxy, method, args) ->
                        interceptor.invoke(new Invocation(target, method, args)));
    }

    record Request(String path) {}

    record Response(int status, String body) {}

    @FunctionalInterface
    interface Handler {
        Response handle(Request request);
    }

    @FunctionalInterface
    interface Middleware {
        Handler wrap(Handler next);
    }

    public static void main(String[] args) {
        System.out.println("=== Decorator ===");
        PriceService decorated =
                new LoggingDecorator(new PriceServiceImpl());
        System.out.println("result=" + decorated.quote("A", 10));

        System.out.println("=== Interceptor ===");
        PriceService intercepted = withInterceptor(
                new PriceServiceImpl(),
                invocation -> {
                    System.out.println("[interceptor] before");
                    Object value = invocation.proceed();
                    System.out.println("[interceptor] after");
                    return value;
                });
        System.out.println("result=" + intercepted.quote("B", 20));

        System.out.println("=== Middleware ===");
        Handler terminal = request -> {
            System.out.println("[handler] " + request.path());
            return new Response(200, "ok");
        };
        Middleware logging = next -> request -> {
            System.out.println("[middleware] before");
            Response response = next.handle(request);
            System.out.println("[middleware] after");
            return response;
        };
        Handler pipeline = logging.wrap(terminal);
        Response response = pipeline.handle(new Request("/items"));
        System.out.println("status=" + response.status());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MechanismComparisonDemo.java
java MechanismComparisonDemo
~~~

Đầu ra chính xác:

~~~text
=== Decorator ===
[decorator] before
[service] quote A
[decorator] after
result=13
=== Interceptor ===
[interceptor] before
[service] quote B
[interceptor] after
result=23
=== Middleware ===
[middleware] before
[handler] /items
[middleware] after
status=200
~~~

**Phân tích từng phần:**

- `LoggingDecorator` tự triển khai `PriceService` rồi gọi `delegate.quote(...)`. Đây là **object composition**: caller nhận một đối tượng bọc cụ thể, không có pointcut tự động.
- `MethodInterceptor` là hợp đồng hook. Dynamic proxy giữ vai trò **cơ chế interception** cho method trên `PriceService` được gọi qua proxy; `Invocation.proceed()` gọi target bằng reflection và unwrap `InvocationTargetException`. Mã ví dụ không định nghĩa một pointcut đa lớp; nếu thêm bộ chọn method và nhiều advice, nó có thể là cơ sở của một AOP runtime nhỏ.
- `Middleware` nhận `Handler next`, trả một `Handler` mới và xử lý trước/sau `next.handle(...)`. Đây là **pipeline composition**: middleware thấy `Request/Response`, không tự thấy những method sâu bên trong service.
- Cả ba in “before/after” vì ba lời gọi đều **đồng bộ**, target return bình thường. Nếu target ném exception, dòng “after” như viết ở đây không chạy; cần `try/finally` cho cleanup hoặc `catch` cho failure logging tùy policy.
- `PriceServiceImpl` không có phương thức nào gọi method khác trên chính nó trong demo. Nếu có *self-invocation*, việc gọi qua decorator/proxy bên ngoài không tự áp hook vào đường gọi nội bộ.
- Ví dụ không dùng agent. Instrumentation bytecode thực sự cần build/manifest/hook và bảo đảm tương thích runtime riêng; dùng mã giả để “giả làm” agent sẽ dễ tạo cảm giác sai về mức bao phủ.

Từ góc nhìn AOP, một *around advice* có thể có hình dạng khá giống `MethodInterceptor`, nhưng AOP còn yêu cầu **quy tắc chọn join point** và cơ chế gắn advice vào các điểm được chọn. Vì vậy không nên nói dynamic proxy và AOP là hai lựa chọn hoàn toàn độc lập.

## 10. Có thể short-circuit ở đâu?

Mọi cơ chế cho phép bao quanh lời gọi đều **có thể** bỏ qua chặng kế tiếp nếu contract cho phép. Tuy nhiên, thời điểm và ý nghĩa khác nhau:

| Chỗ short-circuit | Ví dụ | Điều sẽ bị bỏ qua |
| --- | --- | --- |
| Decorator của service | Cache hit trả giá trị từ wrapper | Delegate và mọi logic bên trong nó |
| Around advice tại method | Policy deny hoặc cache hit không gọi proceed | Advice phía trong và target theo thứ tự nesting |
| Middleware ở pipeline | Thiếu token trả 401 sớm | Middleware phía trong và handler phía sau |
| Method interceptor | Từ chối invocation | Target method nếu hook kiểm soát proceed |
| Instrumented hook | Có thể từ chối/đổi return nếu hook cho phép | Phụ thuộc điểm chèn; không mặc nhiên được quyền skip |

Một profiler chỉ quan sát stack sample **không có cơ chế short-circuit method**. Một observer chạy sau sự kiện cũng không thể quay lại ngăn việc target đã xảy ra. Hãy đọc đúng contract của tool/hook, đừng suy khả năng từ nhãn “instrumentation” hay “interceptor”.

Với middleware trả response sớm, có thể bỏ qua luôn metrics/audit ở các tầng **phía trong**. Với aspect caching bao ngoài security advice, cache hit có thể bỏ qua authorization ở phía trong. Thứ tự phải được thiết kế theo quy định **chính sách nào bắt buộc chạy trên mọi lời gọi**.

## 11. Phân biệt “phạm vi lời gọi” và “phạm vi yêu cầu”

Middleware của một HTTP request thường quan sát đường request từ lúc vào pipeline đến lúc pipeline tạo response. Một method advice đo từ lúc một method được gọi tới lúc method return. Instrumentation của một lệnh I/O có thể đo đến lúc hệ điều hành trả trạng thái hoặc đến completion thực tế tùy API.

Ví dụ:

~~~text
HTTP middleware start
   +-- trace interceptor
       +-- service method advice
           +-- schedule background job
           +-- return CompletionStage
       +-- method advice returns
   +-- HTTP pipeline returns handle/response
background worker later completes
HTTP middleware completion? -> depends on its async contract
~~~

**Không đồng nhất các mốc.** Với method trả `CompletionStage<T>`, after-returning advice chạy khi có stage, không phải khi computation hoàn tất. Nếu middleware chỉ quan sát return của handler, nó có thể ghi “xử lý xong” quá sớm. Nếu middleware pipeline được thiết kế để đợi stage/terminal signal thì thời gian quan sát khác. Instrumentation đo method exit cũng không tự đo completion của task phát sinh ở luồng khác.

Cần đặt tên rõ:

- `method.return_latency`: đến khi method trả handle.
- `operation.completion_latency`: tới khi contract báo công việc xong.
- `request.pipeline_latency`: theo thời điểm pipeline/host xác nhận hoàn tất.
- `resource.operation_latency`: theo hợp đồng của thao tác tài nguyên được instrument.

Với publisher/lazy stream, việc trả publisher chưa chắc bắt đầu thực thi; subscription, onNext, onComplete/onError và cancellation là những mốc riêng. Không chọn bừa một hook trên method return rồi gọi đó là toàn bộ stream latency.

## 12. Chọn theo vị trí của concern

### 12.1. Cross-cutting concern trên nhiều phương thức

Nếu cần một policy nhất quán áp trên tập method độc lập, chẳng hạn đo latency của mọi command theo naming hoặc metadata, **AOP** với pointcut/advice thường phù hợp. Chú ý proxy/weaving coverage, method visibility, self-invocation và ordering.

Nếu chỉ một hoặc vài interface cần chức năng bổ sung, **decorator** có thể rõ ràng và dễ test hơn, đặc biệt khi object graph do ứng dụng tạo và kiểm soát.

### 12.2. Concern của giao thức hoặc pipeline

Nếu cần đọc header, chặn request không hợp lệ, gắn request ID, chuẩn hóa response hoặc xử lý error của route, **middleware** phù hợp vì nhận được context ở đúng tầng pipeline. Không để middleware giao thức thay thế các kiểm tra resource-level bắt buộc khi cùng nghiệp vụ còn có entry path khác.

Nếu host đã có **interceptor API** ổn định ở transport, ORM, serializer, method invocation, client API, hãy dùng đúng extension point thay vì thêm lớp instrumentation sâu hơn chỉ vì muốn “không sửa source”.

### 12.3. Quan sát hệ thống sâu hoặc thư viện không kiểm soát source

**Instrumentation** hữu ích để profiling, tracing call graph, đo allocation, chèn hooks vào thư viện hoặc áp policy quan sát runtime rộng. Nên đánh giá overhead, rủi ro tương thích, quyền agent, class loading và khả năng bảo trì. Không dùng bytecode transformation chỉ để thêm hai dòng log ở một interface nội bộ nếu decorator là đủ.

### 12.4. Chính sách bảo mật và giao dịch

Kiểm tra quyền hoặc transaction không chỉ là câu hỏi “có hook chưa?”. Một điểm enforcement có hiệu lực phải bao phủ **mọi đường tới hành động cần bảo vệ**, được thực thi trước side effect và có context đáng tin cậy. Chọn proxy AOP, decorator hay middleware đều có thể bị bypass nếu caller giữ raw target hoặc có đường gọi trực tiếp resource.

Một transaction advice cũng không tự tạo tính nguyên tử: còn cần transaction manager và tài nguyên hỗ trợ; một method return handle bất đồng bộ không chứng minh transaction của worker đã hoàn tất. Đặt enforcement/transaction ở nơi có hợp đồng và ownership phù hợp quan trọng hơn tên kỹ thuật.

## 13. Những giới hạn Java cần nhớ

### 13.1. Proxy, method visibility và self-invocation

Với **interface dynamic proxy**, chỉ các method gọi qua interface proxy được interceptor quan sát. Lời gọi trực tiếp implementation hoặc lời gọi nội bộ `this.method()` có thể bỏ qua proxy ngoài. Subclass proxy có giới hạn đối với method không thể override và những đường gọi không qua wrapper. Bytecode weaving tại execution join point có thể intercept trong trường hợp khác, nhưng chỉ nếu công cụ thật sự cấu hình đúng.

Không nên nói “AOP luôn bỏ qua self-invocation”: đó là giới hạn điển hình của **proxy-based interception**, không phải định nghĩa chung của AOP.

### 13.2. Exception và kiểu trả về

Decorator và middleware thường dùng lời gọi Java trực tiếp. Reflective interceptor có thể gặp `InvocationTargetException` và cần truyền đúng `cause` cho caller. Dynamic proxy có các quy tắc Java về checked exception: ném checked exception không khai báo qua một method interface có thể dẫn tới wrapper runtime. Các hook không được tự thay lỗi nghiệp vụ bằng giá trị thành công ngoài ý muốn.

Nếu short-circuit, giá trị phải tương thích return type và contract. Không được trả `null` cho primitive return, trả một object sai kiểu hoặc làm mất cancellation/exception semantics của stage.

### 13.3. Thread context và async

Logging/tracing/authorization context có thể được giữ trong `ThreadLocal` nhưng không tự lan qua executor hay callbacks. Decorator, middleware, interceptor, advice và instrumentation đều phải quan tâm propagation và cleanup. Hook chạy trong một thread chỉ chứng minh trạng thái trong scope đó; nếu worker tiếp tục ở thread khác, cần thiết kế context lifecycle phù hợp.

### 13.4. Thứ tự và nested interception

Có thể có nhiều decorator lồng nhau, nhiều middleware trong pipeline, nhiều interceptors trong registry và nhiều advice với pointcut trùng. Outer wrapper chạy trước trên đường đi vào, thường chạy sau trên đường đi ra, nhưng short-circuit và exception làm thay đổi đường đi. Phải vẽ chuỗi thực tế, thay vì chỉ nói “cùng có log trước/sau nên như nhau”.

## 14. Ví dụ phân tích một kiến trúc thực tế

Giả sử hệ thống nhận request để lấy báo cáo, dùng service nội bộ và gọi thư viện tạo PDF:

~~~text
Client
  |
  v
Request pipeline
  +-- Middleware: request ID
  +-- Middleware: authenticate
  +-- Middleware: rate limit
  |
  v
ReportHandler
  |
  v
ReportService facade
  +-- AOP advice: authorization + metrics
  +-- Decorator: caching of safe report summary
  |
  v
PDF rendering library
  +-- Instrumentation: measure allocation/CPU
  |
  v
Storage client interceptor
  +-- record retries / endpoint latency
~~~

Từng lớp có lý do riêng:

- Middleware gắn với request boundary, truy cập headers và danh tính ở đầu vào.
- Security advice ở service boundary vẫn cần vì service có thể còn được gọi từ batch/CLI/message consumer.
- Decorator cache thuộc interface cụ thể, có logic key/invalidation riêng và chỉ short-circuit nếu không bỏ security bắt buộc.
- Instrumentation đo phần thư viện không tiện sửa source; agent cần kiểm soát overhead.
- Client interceptor theo dõi outbound I/O, không đồng nhất một lần retry với toàn bộ use-case thành công.

**Phạm vi cùng tồn tại** phải được xem xét để tránh double-count metrics. Ví dụ request latency và method latency là hai chỉ số khác, có thể lồng nhau. Một trace có thể có span cho cả pipeline và service nhưng không được tạo correlation giả rằng mọi hoạt động ở worker nền đều kết thúc cùng lúc.

## 15. Mô hình quyết định lựa chọn

Hãy đánh giá theo thứ tự:

1. **Sự kiện cần can thiệp thực sự là gì?** Method call, object operation, inbound request, outbound RPC hay runtime event?
2. **Ai sở hữu đối tượng/pipeline?** Có thể tự tạo wrapper và kiểm soát mọi caller hay không?
3. **Phạm vi cần bao phủ rộng đến đâu?** Một interface, một nhóm method, toàn giao thức hay mọi thư viện?
4. **Có cần quyền kiểm soát proceed/next không?** Chỉ quan sát sau sự kiện khác với cho phép short-circuit/retry.
5. **Có thể xuất hiện lời gọi bypass?** Raw target, self-invocation, direct resource access, job background, reflection hoặc agent chưa instrument?
6. **Cần dữ liệu context gì?** Request headers, method args, identity/tenant, transaction, JVM state?
7. **Chính sách có đòi tính đúng đắn mạnh?** Authorization và transaction cần được bảo đảm ở enforcement boundary, không chỉ nhờ vị trí hook tiện lợi.
8. **Chi phí vận hành có tương xứng không?** Complexity của weaving/agent so với decorator hoặc middleware?
9. **Async lifecycle kết thúc ở đâu?** Return handle, completion stage, terminal event hay resource callback?
10. **Cách kiểm thử coverage là gì?** Có case âm tính chứng minh lời gọi không qua hook sẽ bị phát hiện/chặn đúng không?

### Một số quy tắc chọn nhanh

| Nhu cầu | Điểm khởi đầu phù hợp | Lý do |
| --- | --- | --- |
| Thêm logging cho một interface tự quản lý | Decorator | Dễ nhìn, rõ delegate, test đơn giản |
| Áp policy lặp trên nhiều method theo quy tắc | AOP | Pointcut/advice đóng gói concern |
| Chặn request thiếu token trước routing | Middleware của request pipeline | Có context/next ở đúng boundary |
| Theo dõi method do thư viện đã có interceptor API công bố | Interceptor của thư viện | Dùng đúng extension point |
| Profiling thư viện/JVM không thể sửa source | Instrumentation | Có thể quan sát tầng thấp hoặc diện rộng |
| Cache với quyền bỏ qua target tại join point | Around advice hoặc decorator | Cần điều khiển proceed/delegate |
| Quan sát outbound client retries | Client interceptor | Gắn với mỗi attempt hoặc logical call theo host |
| Authorization của thao tác nhạy cảm từ nhiều entry point | Enforcement ở service/resource với coverage kiểm chứng | Tránh chỉ bảo vệ một đường request |

Không có câu trả lời tuyệt đối rằng AOP “mạnh hơn” decorator hay instrumentation “tốt hơn” middleware. Các cơ chế khác nhau về **boundary** và **hợp đồng**, không tạo một thang bậc tuyến tính.

## 16. Sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| “AOP = dynamic proxy” | AOP là mô hình; proxy chỉ là một cách triển khai | Xác định join points, pointcuts và cơ chế áp |
| “Interceptor luôn là AOP” | Nhiều hook chỉ ở một API/pipeline | Interceptor có thể là thành phần của AOP, không bắt buộc |
| “Middleware chỉ là logging decorator” | Middleware mang request/message context và pipeline semantics | Xem contract next, lifecycle, order |
| “Instrumentation chỉ là log thủ công” | Có thể dùng agent, compiler, bytecode hoặc runtime hooks | Phân biệt manual/automatic instrumentation |
| “Agent có thể chặn tất cả method” | Giới hạn class loader, module, bytecode, hook và JVM | Xác minh coverage và quyền hook |
| “AOP bảo vệ mọi lời gọi nội bộ” | Proxy có thể bỏ qua self-invocation | Kiểm tra kỹ thuật interception thật |
| “Thêm cache ngoài auth luôn an toàn” | Cache hit có thể không gọi auth phía trong | Thiết kế ordering và security boundary |
| “After advice luôn chạy khi task xong” | Method return có thể chỉ trả future | Tách method exit và async completion |
| “Đổi exception giúp thống nhất lỗi” | Có thể che cause/đổi hợp đồng | Giữ semantics lỗi và stack trace |
| “Có pointcut thì mọi nơi được instrument” | Pointcut có thể sai hoặc weaving chưa kích hoạt | Kiểm thử runtime coverage |
| “Cùng in before/after tức là hai kỹ thuật tương đương” | Phạm vi, context và khả năng bypass khác nhau | So sánh đường đi và contract thực tế |
| “Thêm nhiều hook càng an toàn” | Có thể duplicate logging, deadlock hoặc overhead | Kiến trúc enforcement và ownership rõ |

## 17. Checklist kiểm chứng thiết kế

- **Boundary:** đã xác định method, object, pipeline hay runtime event chưa?
- **Selection:** pointcut/registration chọn đúng các điểm cần áp chưa?
- **Execution:** đã chứng minh hook thực sự chạy trong đường gọi production chưa?
- **Bypass:** raw target, self-invocation, callback, CLI/job hoặc direct resource có đi vòng không?
- **Semantics:** `proceed`/`next` được gọi đúng số lần, đúng thời điểm, đúng exception/return contract không?
- **Ordering:** những kiểm tra bắt buộc có chạy cả khi cache/fallback trả sớm không?
- **Async:** metric và cleanup có bám đúng completion signal theo contract không?
- **State/context:** thread-safety, correlation ID, principal và transaction scope có đúng không?
- **Overhead:** latency, memory, class loading và số lượng sự kiện có được đo thực tế không?
- **Maintainability:** người bảo trì có tìm được nơi policy được gắn, thay đổi và kiểm thử không?
- **Safety:** nếu pointcut hoặc instrumentation không áp được, cơ chế bảo vệ quan trọng có fail closed hoặc có kiểm soát bổ sung không?
- **Verification:** có kiểm thử kết quả và đường đi, không chỉ assert rằng một dòng log xuất hiện không?

## 18. Kết luận

**AOP** là mô hình tổ chức logic xuyên suốt qua join point/pointcut/advice. **Decorator** là wrapper object. **Middleware** là một chặng trong pipeline. **Interceptor** là hợp đồng hook quanh một hoạt động. **Instrumentation** là kỹ thuật chèn hoặc thu thập tín hiệu thực thi, có thể được dùng để triển khai AOP.

Chúng có thể kết hợp: một AOP runtime dùng interceptors; một middleware có hình thức decorator của handler; instrumentation có thể thực hiện weaving; decorator có thể biểu diễn một concern. Điều quyết định lựa chọn không phải tên gọi mà là **hook có thực sự bao phủ đường gọi cần xử lý, có đủ quyền điều khiển luồng và có bảo toàn ngữ nghĩa nghiệp vụ hay không**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-008">Giữ quy tắc nghiệp vụ cốt lõi tường minh khi cần</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ quy tắc nghiệp vụ cốt lõi tường minh khi cần trong AOP

## 1. Vấn đề thiết kế: AOP giúp tách mối quan tâm, nhưng không nên che giấu nghiệp vụ

Trong **Aspect-Oriented Programming (AOP)**, một aspect đóng gói hành vi có thể áp dụng tại nhiều **join point** thông qua **pointcut** và **advice**. Những cơ chế như đo thời gian, ghi log có cấu trúc, kiểm tra điều kiện truy cập tại một ranh giới hoặc tạo trace span có thể phù hợp với cách tổ chức này. Nhờ đó, phần thân của nhiều phương thức không phải lặp đi lặp lại cùng một đoạn hạ tầng.

Tuy nhiên, một quyết định như **giá đơn hàng được tính thế nào, khi nào cho phép chuyển trạng thái, điều kiện nào khiến giao dịch được chấp thuận hay từ chối, hoặc ai chịu trách nhiệm sửa dữ liệu** không nên biến mất khỏi mô hình nghiệp vụ chỉ vì có thể chèn logic bằng advice.

**Nguyên tắc trung tâm:** những quy tắc quyết định kết quả và trạng thái cốt lõi của hệ thống cần có **hợp đồng tường minh, tên gọi mang ý nghĩa nghiệp vụ, vị trí sở hữu rõ ràng và kiểm thử trực tiếp**. AOP nên hỗ trợ các mối quan tâm xuyên suốt ở ranh giới thích hợp; không làm quy tắc miền trở thành một tác dụng phụ vô hình của tên phương thức hay thứ tự aspect.

Trong thực tế, vẫn có các chính sách được áp dụng xuyên suốt và ảnh hưởng đến quyền thực thi, như authorization, transaction hoặc rate limiting. Vì vậy không thể nói mọi advice phải thuần túy quan sát. Tiêu chí đúng là **một thay đổi có ý nghĩa miền phải được thể hiện bằng chính sách hoặc hợp đồng được khai báo rõ**, có thể tìm thấy, đánh giá và xác minh, thay vì giấu một nhánh đặc thù trong advice quá rộng.

## 2. Phân biệt logic nghiệp vụ cốt lõi với cross-cutting concern

**Business rule (quy tắc nghiệp vụ):** điều kiện, phép tính hoặc chuyển trạng thái mà hệ thống phải tuân thủ để cho ra kết quả đúng. Ví dụ: đơn hàng chỉ được chốt nếu mặt hàng còn được bán; mức giảm giá phụ thuộc hạng khách và chương trình khuyến mại; thanh toán không thể vượt hạn mức đã được cấp.

**Business invariant (bất biến nghiệp vụ):** điều kiện phải tiếp tục đúng tại các mốc cam kết trạng thái, chẳng hạn tổng tiền không âm theo quy tắc miền, một mã hoàn tiền không được thực hiện hai lần, hoặc một tài nguyên không được chuyển chủ sở hữu trái phép.

**Cross-cutting concern:** một trách nhiệm lặp trên nhiều phần mà cơ chế áp dụng thường giống nhau, chẳng hạn ghi sự kiện hoàn tất thao tác, đo duration, correlation ID, telemetry hoặc kiểm tra một policy đã được mô tả.

**Application service / use case:** nơi điều phối lời gọi, chính sách và các thành phần miền để thực hiện một ý định nghiệp vụ cụ thể.

**Domain service / policy object:** nơi mô tả quy tắc không tự nhiên thuộc một entity hoặc cần dùng lại; hợp đồng đầu vào/đầu ra đủ rõ để kiểm thử.

**Aspect:** cơ chế can thiệp tại join point, có thể bao quanh một use case hoặc một boundary để áp dụng một chính sách chung. Aspect không tự phân biệt được phương thức là nghiệp vụ hay helper nếu không có pointcut/metadata được thiết kế đúng.

| Câu hỏi | Thường là logic miền | Thường là concern xuyên suốt |
| --- | --- | --- |
| Có quyết định giá, hạn mức, quyền sở hữu, chuyển trạng thái? | Có | Chỉ hỗ trợ bảo vệ/quan sát |
| Có cần chuyên gia nghiệp vụ phê duyệt quy tắc? | Thường có | Phụ thuộc chính sách chung |
| Kết quả có thay đổi giữa các loại sản phẩm/khách hàng? | Thường có | Cơ chế thường giống nhau |
| Có thể mô tả bằng operation name, outcome, duration chung? | Có thể cung cấp metadata | Thường là trọng tâm |
| Nếu bỏ hành vi này thì kết quả nghiệp vụ sai? | Dấu hiệu cần tường minh | Nếu là authorization, phải có enforcement được công bố rõ |
| Có thể kiểm thử bằng input và output nghiệp vụ trực tiếp? | Nên làm | Thường kiểm thử interception/metadata |

Bảng chỉ là công cụ phân tích. Không phải mọi trách nhiệm đều chỉ thuộc một bên. Một thiết kế tốt có thể dùng **aspect để kích hoạt kiểm tra**, còn **domain policy** định nghĩa nội dung điều kiện. Chẳng hạn advice biết khi nào phải hỏi quyền; policy biết một nhân viên có được phê duyệt hồ sơ nào hay không.

## 3. “Tường minh” nghĩa là gì?

Một quy tắc đủ tường minh khi người bảo trì có thể:

1. **Tìm được nó** theo tên use case, policy hoặc invariant.
2. **Biết ai sở hữu**: entity, domain service, application service, policy object hay một cấu hình nghiệp vụ chính thức.
3. **Biết khi nào nó chạy**, tại bước nào so với đọc dữ liệu, ghi dữ liệu và commit.
4. **Đọc hợp đồng**: đầu vào, kết quả, trạng thái thất bại, side effect và điều kiện áp dụng.
5. **Kiểm thử trực tiếp** quy tắc mà không cần dựng cả hệ thống weaving hoặc một chuỗi proxy khó quan sát.
6. **Đánh giá tác động** khi sửa luật: những use case nào bị ảnh hưởng, dữ liệu nào cần migration, các đường thực thi nào phải giữ invariant.
7. **Phân biệt** kết quả nghiệp vụ với hành vi đo lường/logging và xử lý lỗi hạ tầng.

Tường minh không bắt buộc viết một chuỗi câu lệnh if lớn trong từng method. Một đối tượng chính sách có tên rõ, một bảng quyết định được kiểm soát, một state machine hoặc một rule evaluator có version cũng có thể tường minh nếu điểm gọi, hợp đồng và owner được công bố.

Không nên áp đặt một phương pháp biểu diễn duy nhất. Mục đích là giúp quyết định nghiệp vụ **có địa chỉ kiến trúc và có bằng chứng kiểm chứng**, thay vì chỉ xảy ra vì một pointcut vô tình khớp.

## 4. Dấu hiệu đã đưa quá nhiều nghiệp vụ vào Aspect

### 4.1. Advice sửa kết quả trả về theo loại khách

Một around advice thấy phương thức tính giá, gọi proceed rồi bí mật giảm 10% nếu một ThreadLocal chứa nhãn VIP. Người đọc phương thức tính giá sẽ hiểu sai rằng nó trả giá cuối; quy tắc discount lại không nằm ở nơi định nghĩa giá và thứ tự áp dụng thuế/chiết khấu có thể thay đổi theo advice order.

### 4.2. Advice chuyển trạng thái nghiệp vụ

Một advice after-returning quanh mọi method tên save tự chuyển đơn hàng sang APPROVED. Việc lưu dữ liệu và quyết định phê duyệt là hai ý nghĩa khác nhau; tên method hoặc một lời gọi kỹ thuật không đủ xác định điều kiện phê duyệt.

### 4.3. Pointcut mã hóa tên class và tên method đặc thù

Ví dụ quy tắc “khi class tên PremiumOrder và method tên update thì áp dụng thuế vùng X” phụ thuộc cấu trúc mã. Refactor tên lớp khiến quy tắc biến mất, trong khi yêu cầu nghiệp vụ không hề thay đổi.

### 4.4. Thêm use case mới phải sửa nhiều nhánh if trong một Aspect

Một aspect chứa những điều kiện riêng cho Order, Invoice, Payment, Membership và Refund dần trở thành điểm hội tụ của những domain không liên quan. Các nhóm sở hữu domain khác nhau có thể gây thay đổi ngoài ý muốn cho nhau.

### 4.5. Lỗi hoặc retry của advice thay đổi trạng thái nghiệp vụ không được công bố

Một around advice chung tự retry mọi exception, bao gồm cả phương thức chuyển tiền không có idempotency. Một lần người dùng yêu cầu có thể tạo hai side effect. Nếu retry là một chính sách có chủ ý, nó phải có ràng buộc về idempotency, loại lỗi retry được, số lần thử và outcome, thay vì âm thầm hoạt động trên mọi method.

### 4.6. Tests chỉ kiểm tra proxy, bỏ qua hợp đồng miền

Nếu bài test về giảm giá phải dựng weaving và cấu hình pointcut để biết 10% được áp dụng thế nào, khả năng logic định giá đã nằm sai lớp. Kiểm thử giá nên gọi pricing policy hoặc use case có contract rõ; bài test aspect chỉ cần chứng minh phần concern xuyên suốt chạy đúng.

## 5. Khi nào quy tắc miền cần ở entity, service hay policy?

Không có đáp án cố định cho mọi mô hình, nhưng có những hướng dẫn hữu ích:

| Quy tắc | Vị trí ứng viên | Lý do |
| --- | --- | --- |
| Một trạng thái chỉ có thể chuyển từ DRAFT sang APPROVED khi đủ điều kiện | Entity/aggregate hoặc domain service | Bảo vệ invariant trạng thái |
| Giá cuối tính từ giá gốc, thuế và khuyến mại | Pricing policy/domain service | Có công thức nghiệp vụ rõ, thay đổi theo loại đơn |
| Một tác vụ phải gọi ba dịch vụ theo một quy trình | Application service/use case | Điều phối workflow và outcome |
| Một action cần quyền trên resource | Authorization policy + điểm enforcement | Quyết định theo subject/action/resource |
| Ghi operation outcome theo schema chung | Logging aspect ở business boundary | Cơ chế chung, ít phụ thuộc loại nghiệp vụ |
| Đo duration của mỗi operation | Metrics aspect hoặc instrumentation theo lifecycle | Quan sát chung và tách khỏi công thức nghiệp vụ |
| Áp dụng retry cho dependency có hợp đồng an toàn | Retry policy + interceptor riêng | Cần contract idempotency và loại lỗi |
| Kiểm tra giới hạn tồn kho trước cập nhật | Domain/storage boundary, giao dịch thích hợp | Cần ngăn race và bảo vệ dữ liệu thực |

Một **domain service** không bắt buộc phải có trạng thái. Điều quan trọng là input, output và điều kiện rõ ràng. Một **policy object** giúp thay đổi luật mà không đặt một danh sách switch/if theo tên method trong advice.

## 6. Đừng nhầm “cross-cutting” với “mọi nơi đều làm giống nhau”

Một yêu cầu có thể xuất hiện ở nhiều domain nhưng chỉ giống nhau ở tên.

Ví dụ “validate” xuất hiện ở tạo tài khoản, đặt hàng và thanh toán. Validation cấu trúc dữ liệu có thể dùng một abstraction chung, nhưng điều kiện thanh toán vượt hạn mức, tuổi người dùng hoặc tồn kho không giống nhau. Không thể chỉ tạo một ValidationAspect khớp mọi method có tên process rồi coi các luật là đồng nhất.

Tương tự:

- **Caching:** cùng cơ chế lưu cache nhưng key, TTL, tenant isolation và invalidation khác biệt theo nghiệp vụ.
- **Retry:** cùng vòng lặp retry nhưng mức độ idempotent, backoff và error classification khác nhau.
- **Transaction:** cùng cơ chế begin/commit nhưng phạm vi atomicity và rollback rule có thể khác.
- **Authorization:** cùng enforcement nhưng policy tài nguyên và delegation khác.
- **Logging:** có thể dùng schema chung nhưng không có quyền in toàn bộ argument chứa dữ liệu nhạy cảm.

Vì thế cần tách **mechanism ổn định** và **policy biến thiên**. Aspect chỉ điều phối mechanism tại boundary; từng domain cung cấp metadata/policy cần thiết.

## 7. Một ví dụ phản thiết kế và cách cải thiện

### Phản thiết kế: advice thay đổi giá

Giả mã không khuyến nghị:

~~~text
around *calculatePrice*(...):
    price = proceed()
    if currentUser.isPremium():
        return price * 0.9
    return price
~~~

Những lỗi kiến trúc có thể gặp:

1. Một method trả price đã tính thuế và một method khác trả base price; 10% bị áp dụng khác mốc.
2. Caller không thấy discount là một phần của hợp đồng.
3. Advice phụ thuộc context hiện hành có thể mất khi chuyển executor hoặc thread.
4. Khi một method gọi method khác cũng khớp pointcut, discount có thể bị áp dụng hai lần.
5. Refactor tên method có thể làm mất hoặc nhân đôi quy tắc.
6. Phép toán số thực có thể gây lỗi làm tròn tiền tệ nếu thiếu money type/chính sách rounding.
7. Luật khuyến mại có thể thay đổi theo thời gian nhưng không có version hoặc policy owner.
8. Kết quả trả về được đổi sau proceed mà không tương ứng với dữ liệu đã ghi hoặc audit.

### Thiết kế rõ trách nhiệm

~~~text
CheckoutUseCase
    -> kiểm tra input / lập kế hoạch
    -> PricingPolicy.calculate(request, customerContext)
    -> tạo PriceQuote theo công thức được kiểm thử
    -> xác nhận/ghi trạng thái khi phù hợp

ObservabilityAspect
    -> quan sát boundary use case
    -> ghi start/outcome/metric
    -> trả nguyên kết quả của use case
~~~

PricingPolicy có thể là một interface với nhiều cách tính cụ thể; một bộ quy tắc ngoài mã cũng có thể dùng nếu được quản lý/version hóa. Điều phải rõ là **một use case chủ động gọi policy chính thức**, và policy quyết định giá. Aspect không cần biết khách hàng thuộc hạng nào.

## 8. Java 17 chạy được: quy tắc giá tường minh, Aspect chỉ ghi sự kiện

Ví dụ minh họa hai trách nhiệm:

- **PricingService** chịu trách nhiệm tính giá theo quy tắc nghiệp vụ cụ thể: số lượng phải dương, đơn giá tính bằng cent không âm, khách premium giảm 10% của subtotal; phần cent lẻ trong discount được làm tròn xuống bằng chia số nguyên.
- **OperationLoggingAdvice** quan sát những method có metadata BusinessOperation, ghi bắt đầu/kết thúc hoặc lỗi, nhưng không tự sửa quote.

Ví dụ dùng JDK dynamic proxy để giải thích cơ chế can thiệp ở interface method; không phụ thuộc thư viện AOP riêng.

Tạo tệp **ExplicitBusinessRulesDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ExplicitBusinessRulesDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface BusinessOperation {
        String value();
    }

    record QuoteRequest(int quantity, int unitPriceCents, boolean premium) { }

    record PriceQuote(int subtotalCents, int discountCents, int totalCents) { }

    interface PricingService {
        @BusinessOperation("price.quote")
        PriceQuote quote(QuoteRequest request);
    }

    static final class DefaultPricingService implements PricingService {
        @Override
        public PriceQuote quote(QuoteRequest request) {
            if (request == null || request.quantity() <= 0
                    || request.unitPriceCents() < 0) {
                throw new IllegalArgumentException("invalid quote request");
            }

            // Chính sách giá là mã miền tường minh.
            int subtotal = Math.multiplyExact(
                    request.quantity(), request.unitPriceCents());
            int discount = request.premium() ? subtotal / 10 : 0;
            int total = Math.subtractExact(subtotal, discount);
            return new PriceQuote(subtotal, discount, total);
        }
    }

    static final class OperationLoggingAdvice implements InvocationHandler {
        private final PricingService target;

        OperationLoggingAdvice(PricingService target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            BusinessOperation metadata =
                    method.getAnnotation(BusinessOperation.class);
            if (metadata == null) {
                return invokeTarget(method, args);
            }

            String operation = metadata.value();
            System.out.println("BEGIN " + operation);
            try {
                Object result = invokeTarget(method, args);
                System.out.println("END " + operation + " outcome=returned");
                return result;
            } catch (Throwable error) {
                System.out.println("END " + operation + " outcome=threw type="
                        + error.getClass().getSimpleName());
                throw error;
            }
        }

        private Object invokeTarget(Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapper) {
                throw wrapper.getCause();
            }
        }
    }

    public static void main(String[] args) {
        PricingService service = (PricingService) Proxy.newProxyInstance(
                PricingService.class.getClassLoader(),
                new Class<?>[] { PricingService.class },
                new OperationLoggingAdvice(new DefaultPricingService())
        );

        PriceQuote premium = service.quote(new QuoteRequest(3, 1000, true));
        System.out.println("premium_total=" + premium.totalCents());

        PriceQuote regular = service.quote(new QuoteRequest(2, 500, false));
        System.out.println("regular_total=" + regular.totalCents());

        try {
            service.quote(new QuoteRequest(0, 1000, true));
        } catch (IllegalArgumentException error) {
            System.out.println("rejected="
                    + error.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy trên Java 17 trở lên:

~~~bash
javac ExplicitBusinessRulesDemo.java
java ExplicitBusinessRulesDemo
~~~

Stdout dự kiến chính xác:

~~~text
BEGIN price.quote
END price.quote outcome=returned
premium_total=2700
BEGIN price.quote
END price.quote outcome=returned
regular_total=1000
BEGIN price.quote
END price.quote outcome=threw type=IllegalArgumentException
rejected=IllegalArgumentException
~~~

### Phân tích code

**Quy tắc miền được đọc ngay trong DefaultPricingService.** QuoteRequest kiểm soát quantity và unitPriceCents; khi hợp lệ, subtotal = quantity × unitPriceCents; discount premium = floor(subtotal / 10) vì phép chia số nguyên; tổng = subtotal − discount. Với 3 × 1000 cent, subtotal 3000, discount 300, total 2700. Với 2 × 500 cent và khách thường, discount 0, total 1000.

**Math.multiplyExact và Math.subtractExact** giúp phát hiện tràn số nguyên bằng ArithmeticException thay vì cho giá trị không đúng. Ứng dụng thực tế có thể dùng một kiểu Money và decimal arithmetic theo quy tắc làm tròn, currency và hạn mức riêng; ví dụ dùng int cent để stdout dễ xác minh.

**Aspect không hiểu công thức giá.** Nó chỉ xem một method có BusinessOperation và ghi outcome. Khi chính sách premium đổi từ 10% sang mức mới, ta sửa pricing policy và test giá mà không chỉnh pointcut hoặc logic logging.

**Advice bảo toàn kết quả và exception.** Proxy gọi target đúng một lần, trả nguyên PriceQuote hoặc ném lại nguyên nhân qua InvocationTargetException. Dòng "outcome=returned" chỉ mô tả method đồng bộ trả bình thường; nếu API trả một đối tượng chứa thất bại hoặc Future chưa hoàn tất, định nghĩa business success phải khác.

**Ví dụ chỉ instrument lời gọi qua proxy.** Gọi trực tiếp DefaultPricingService sẽ không kích hoạt advice, dù công thức miền vẫn chính xác. Đây là một lý do rất quan trọng để quy tắc cốt lõi không được đặt độc quyền trong cơ chế chặn có thể bị bypass.

**Dòng log không chứa request.** Một aspect production cần dùng structured logger, schema ổn định, correlation phù hợp và cơ chế redaction. Không log argument nguyên bản chỉ vì reflection có thể đọc được nó.

## 9. Vì sao không nên đặt quy tắc cốt lõi độc quyền trong proxy?

Cơ chế AOP có thể chặn các điểm khác nhau:

- **Proxy/interceptor:** thường thấy lời gọi đi qua đối tượng bọc. Lời gọi trực tiếp target hoặc self-invocation có thể không đi qua interceptor.
- **Weaving trên call join point:** chọn các vị trí gọi method; tập điểm cắt có thể khác execution join point.
- **Weaving trên execution join point:** can thiệp lúc method thực thi; không đồng nghĩa mọi construction, field access hoặc async task sẽ được chặn.
- **Instrumentation khi chạy:** có thể bị thay đổi bởi cấu hình, môi trường, version agent hoặc cách nạp mã.

Nếu quy tắc về giá hoặc một state invariant chỉ tồn tại trong advice, đường thực thi bypass được advice sẽ thiếu quy tắc. Với logging, bỏ log có thể là lỗi quan sát; với hoàn tiền hoặc chuyển tiền, bỏ quy tắc cốt lõi có thể gây sai dữ liệu và rủi ro đáng kể.

Cách giảm rủi ro:

1. Đặt domain invariant tại nơi thực hiện state transition hoặc policy được gọi tường minh.
2. Xác định các entry paths: API, batch job, message consumer, scheduler, CLI, test utility và gọi giữa service.
3. Sử dụng AOP cho policy enforcement tại boundary khi có lý do, nhưng không giả định nó thay thế mọi bảo vệ ở domain/storage.
4. Test cả đường gọi có aspect và những đường trực tiếp có thể tồn tại.
5. Có coverage test/architecture test cho protected operations nếu an toàn phụ thuộc vào pointcut.

## 10. “Giữ tường minh” không có nghĩa copy-paste logic ở nhiều nơi

Một hiểu nhầm là chỉ có hai lựa chọn: viết cùng quy tắc trong mọi phương thức hoặc đưa nó vào aspect. Thực tế có thể tách quy tắc thành abstraction có tên:

~~~text
OrderApplicationService
  -> PricingPolicy.calculate()
  -> InventoryPolicy.ensureReservable()
  -> OrderAggregate.confirm()

RefundApplicationService
  -> RefundEligibilityPolicy.check()
  -> RefundAggregate.issue()

Cross-cutting aspects
  -> logging / metrics / tracing tại boundary
  -> authorization enforcement theo chính sách đã khai báo
~~~

Mỗi use case gọi policy phù hợp, không phải chứa lại toàn bộ code. Tính tường minh nằm ở **đường phụ thuộc, tên và hợp đồng**, không nằm ở số dòng. Nếu một policy được nhiều use case dùng, service hoặc policy object có thể tái sử dụng mà vẫn biểu diễn quy tắc như một quyết định miền.

Một aspect quá “thông minh” thường biến lời gọi nghiệp vụ thành một bí mật: người đọc thấy calculate(), nhưng discount và tax lại được ghép thêm ở những vòng advice mà không có dấu hiệu trong hợp đồng. Loại coupling này làm refactor và bảo trì khó hơn lợi ích giảm trùng lặp mã.

## 11. Invariant cần bảo vệ gần thời điểm thay đổi trạng thái

Có những quy tắc đọc trạng thái có thể thay đổi giữa lúc kiểm tra và lúc dùng (**TOCTOU – time of check to time of use**).

Ví dụ:

~~~text
T1: advice kiểm tra balance >= withdrawal
T2: một transaction khác thay đổi balance
T3: business method thực hiện withdrawal
~~~

Một kiểm tra tại method entry không bảo đảm bất biến vẫn đúng ở T3. Với thao tác nhạy cảm, cần xác định biện pháp nhất quán: kiểm tra và cập nhật trong transaction thích hợp, optimistic version, conditional update hoặc locking theo mô hình dữ liệu.

Phần kiểm tra có thể được hỗ trợ bởi aspect ở boundary, nhưng **invariant về trạng thái bền vững** không nên chỉ dựa vào một kết quả boolean đọc từ quá sớm. Nó thuộc hợp đồng của thao tác ghi/commit có thẩm quyền.

Tương tự:

- “Chỉ được chuyển đơn DRAFT → APPROVED” phải bảo đảm trong logic chuyển trạng thái, kể cả khi caller là batch job.
- “Không hoàn tiền hai lần cùng payment” cần idempotency key/unique constraint hoặc cơ chế chống lặp thích hợp.
- “Không vượt tồn kho” cần xử lý cạnh tranh đồng thời tại thời điểm ghi.
- “Không truy cập dữ liệu tenant khác” cần bảo vệ nguồn dữ liệu và phân quyền resource; không chỉ tin tenant ID do request tự khai.
- “Không dùng quyền đã thu hồi” cần xem thời hạn authorization decision và mốc kiểm tra lại.

## 12. Authorization: cơ chế chung và luật truy cập riêng

Authorization là ví dụ cần phân chia trách nhiệm cẩn thận. Một authorization aspect có thể:

1. Chọn những protected operations đã đánh dấu hoặc thuộc contract.
2. Lấy **trusted principal** và invocation context.
3. Resolve action cùng resource descriptor.
4. Gọi policy evaluator.
5. Deny trước proceed khi không đủ quyền.
6. Ghi event quyết định an toàn và cleanup context.

Nhưng **quy tắc** “nhân viên chỉ phê duyệt hợp đồng thuộc phòng ban mình và dưới hạn mức được cấp” cần được mô tả trong policy rõ, có kiểm thử và dữ liệu resource đáng tin. Không nên nhét một danh sách điều kiện đặc biệt của từng loại hợp đồng vào một AuthorizationAspect dùng chung.

Ngoài ra, context từ ThreadLocal không tự bảo đảm danh tính đã được xác thực. Quyền trên tài nguyên có thể thay đổi; quyết định allow phải có phạm vi, hạn dùng và cơ chế revalidation phù hợp. Lời gọi trực tiếp vượt proxy không nên cho phép bỏ qua enforcement quan trọng.

**Đây không phải lý do tránh AOP trong bảo mật.** Đây là lý do phân định **enforcement mechanism** với **policy quyết định** và thiết kế kiểm soát ở các trust boundary đúng.

## 13. Retry, transaction và caching cũng có quy tắc miền

### Retry

Một retry advice tổng quát chỉ an toàn cho tập operation có hợp đồng xử lý lặp phù hợp. Việc gửi email, thu tiền hay chuyển trạng thái có thể không idempotent. Không mặc nhiên retry mọi RuntimeException.

Mô tả policy bằng các thành phần tường minh: attempt limit, backoff, retryable exceptions, deadline, idempotency strategy và cách xử lý kết quả sau lần thử cuối. Nếu một operation không chịu được retry, phải loại trừ nó hoặc cấu trúc lại cơ chế.

### Transaction

Một transaction aspect có thể bắt đầu/kết thúc transaction, nhưng **ranh giới atomicity** là quyết định gắn với use case và bất biến. Nếu chia một operation thành nhiều transaction hoặc giữ transaction xuyên async boundary một cách máy móc, trạng thái có thể không như mong muốn. Commit/rollback cần được định nghĩa theo luật nghiệp vụ và API lưu trữ.

### Cache

Một cache advice có thể giảm số lần tính toán, nhưng key, TTL, invalidation, tenant isolation, consistency và dữ liệu nhạy cảm phải rõ. Nếu cache trả một giá cũ sau khi chương trình khuyến mại thay đổi, policy có thể sai. Đừng coi “mọi method bắt đầu bằng get” là đủ chứng minh cache an toàn.

Một tiêu chí chung: khi **policy chứa tri thức miền biến thiên**, giữ tri thức đó trong thành phần tường minh, dù mechanism được điều phối bằng advice.

## 14. Thứ tự các Aspect có thể thay đổi ý nghĩa

Một operation có thể bị bọc bởi nhiều aspect:

~~~text
LoggingAdvice
  -> AuthorizationAdvice
    -> RetryAdvice
      -> TransactionAdvice
        -> businessOperation
~~~

Với thứ tự này, logging ở ngoài có thể thấy cả denial; retry có thể thực thi bên trong transaction advice nhiều lần. Đảo thứ tự có thể khiến transaction bao toàn bộ retry, authorization check xảy ra từng attempt hoặc timer đo những phạm vi khác nhau.

Phải xác định và kiểm thử:

- Advice nào được phép **chặn không cho proceed**?
- Advice nào được phép **lặp proceed** và khi nào?
- Exception nào dẫn tới rollback hoặc retry?
- Timer đo operation, attempt hay dependency?
- Business outcome được ghi tại mốc nào?
- Nếu một advice ném exception, các lớp ngoài nhìn thấy gì?
- Nếu có security context, scope được tạo/đóng trên thread nào?
- Với task bất đồng bộ, những lớp advice nào kết thúc trước task?

Một quy tắc cốt lõi được thực hiện thông qua nhiều advice không có thứ tự được kiểm soát sẽ khó đảm bảo tính đúng. Việc gọi policy tường minh trong use case giúp làm rõ ít nhất phần quyết định nghiệp vụ.

## 15. Vòng đời bất đồng bộ: return chưa phải kết quả cuối

Một method có thể chỉ submit công việc và trả Future hoặc CompletionStage:

~~~text
around advice enters
  -> proceed() returns CompletionStage
around advice exits
  -> task still executing
  -> completion occurs later
~~~

Nếu aspect đặt luật “chỉ chốt đơn khi tồn kho đủ” ở after-returning, nó có thể chạy **trước khi công việc xác nhận tồn kho hoàn tất**. Tương tự, ghi success vào outcome ngay khi method trả stage là sai mốc.

Cần tách:

- **Invocation lifecycle:** thời gian method được chặn chạy cho tới return/throw đồng bộ.
- **Task lifecycle:** từ lúc công việc thực tế chạy tới khi dừng, cleanup tài nguyên.
- **Completion lifecycle:** tín hiệu kết quả được hoàn tất, có thể thành công, lỗi, hủy hoặc timeout theo API.
- **Business commit lifecycle:** thay đổi trạng thái đã được xác nhận bền vững.

Một rule phụ thuộc dữ liệu cuối phải được thực thi ở nơi có dữ liệu và có quyền thay đổi trạng thái. Một observer completion có thể ghi metric/log đúng mốc, nhưng không nên thay policy được thực thi ở task/transaction boundary.

Đừng giữ lock, transaction hoặc scope của invocation ngắn đến lúc task dài hoàn tất nếu resource không được thiết kế cho vòng đời đó. Khi truyền identity hoặc correlation qua executor, dùng snapshot phù hợp, scope riêng ở worker và try/finally cleanup.

## 16. Logging và tracing hỗ trợ hiểu nghiệp vụ, không định nghĩa nghiệp vụ

Một aspect logging có thể phát:

~~~json
{
  "event": "operation_finished",
  "operation": "order.checkout",
  "outcome": "success",
  "duration_ms": 37,
  "correlation_id": "req-42"
}
~~~

Đây là dữ liệu minh họa, không phải một kết quả stdout của Java. Nó giúp tìm kiếm và tổng hợp, nhưng **không phải bằng chứng một giao dịch đã commit** nếu event được ghi trước commit.

Các lưu ý:

- Không log password, token, thông tin thanh toán hoặc toàn bộ object vì tiện.
- Tên operation và error code nên ổn định và có tập giá trị hữu hạn; không lấy ID mỗi đơn hàng làm label metric thông thường.
- Nếu một operation có nhiều retry attempts, không đếm mỗi attempt là một business operation.
- Nếu nhiều aspect/instrumenter cùng ghi business completion, có thể đo trùng.
- Audit có yêu cầu tính toàn vẹn nên có cơ chế riêng, không mặc định logger.info tương đương audit event bền vững.

**Quy tắc nghiệp vụ** nên sinh outcome rõ ràng; **aspect observability** tiêu thụ outcome đó để quan sát, thay vì suy đoán hoặc âm thầm sửa outcome sau khi nghiệp vụ trả kết quả.

## 17. Trường hợp hợp lệ cho aspect có ảnh hưởng hành vi

Không phải mọi advice đều phải chỉ đọc. Một số concern mang nghĩa kiểm soát luồng và có thể được triển khai bằng aspect nếu contract được khai báo:

| Concern | Tác động có chủ ý | Điều kiện an toàn quan trọng |
| --- | --- | --- |
| Authorization | Deny trước khi xử lý | Principal tin cậy, policy resource, không bypass |
| Rate limiting | Từ chối hoặc chờ permit | Key, quota, tài nguyên và vòng đời permit rõ |
| Transaction | Commit/rollback | Boundary atomicity, propagation, exception policy |
| Retry | Thực hiện lại attempt | Idempotency, deadline, loại lỗi, số lần thử |
| Circuit breaker | Fail fast khi phụ thuộc lỗi | Trạng thái breaker, fallback và lỗi rõ |
| Input normalization chung | Chuẩn hóa một tập input có contract | Không đổi ngầm ý nghĩa miền riêng |

Điểm chung: **tác động được công khai trong API, metadata hoặc kiến trúc**, và có test cho cả allow/deny, side effect, exception, concurrent execution và lựa chọn pointcut. Với những logic thay đổi liên tục theo domain, có thể giữ policy tường minh còn aspect chịu phần enforcement.

Nếu một concern chỉ xuất hiện ở một nơi hoặc đòi nhiều ngoại lệ nghiệp vụ, gọi một API trực tiếp thường đơn giản hơn.

## 18. Những sai lầm thường gặp và sửa thế nào

### Sai lầm A: “Có code lặp là cần aspect”

Nhiều đoạn tính giá lặp nhau có thể nên được thay bằng PricingPolicy. AOP phù hợp hơn khi **chính sách áp dụng tại các join point** là ổn định, không phụ thuộc công thức từng nơi.

### Sai lầm B: “Quy tắc nghiệp vụ đặt trong advice vẫn dễ bảo trì”

Một advice áp dụng discount dựa trên method name làm chính sách khó truy vết, test và refactor. Di chuyển luật sang policy với đầu vào/ra rõ và để advice phục vụ quan sát/boundary enforcement.

### Sai lầm C: “Nếu pointcut khớp thì mọi đường gọi đều được bảo vệ”

Proxy có thể bị bypass; self-invocation hoặc gọi target trực tiếp không đi qua interceptor tương ứng. Kiểm thử runtime và đặt invariant quan trọng ở vị trí không dễ bỏ qua.

### Sai lầm D: “Thay đổi thứ tự aspect chỉ ảnh hưởng log”

Retry, transaction, authorization và cache có thể thay đổi hành vi thực, số lần side effect hoặc thời điểm commit. Ghi contract composition và test thứ tự.

### Sai lầm E: “Method trả về bình thường là nghiệp vụ thành công”

Một method có thể trả Result.Failure, Future chưa hoàn tất hoặc dữ liệu chưa commit. Observer và policy phải hiểu lifecycle, không gán success bằng một after-returning hook máy móc.

### Sai lầm F: “Giấu quy tắc ở một annotation là đủ tường minh”

Annotation chỉ có thể mô tả metadata; khi chính sách phụ thuộc hàng chục trường hoặc nhánh miền, cần component/policy được đặt tên, version hóa và kiểm thử. Không để annotation string trở thành một ngôn ngữ điều khiển nghiệp vụ khó kiểm tra.

### Sai lầm G: “Lỗi instrumentation có thể làm hỏng operation”

Một logger hoặc serializer ném exception trong advice có thể che exception gốc; một callback phụ có thể đổi trạng thái dependent stage. Cần quy định failure policy để quan sát không vô tình làm đổi contract nghiệp vụ.

### Sai lầm H: “Càng nhiều aspect càng tách biệt tốt”

Một hệ thống có nhiều aspect chồng pointcut có thể sinh log/metric trùng, rối thứ tự và giảm khả năng hiểu. Cần ownership cho từng concern, scope rõ, naming nhất quán và kiểm thử tích hợp.

## 19. Bộ câu hỏi quyết định dùng AOP hay abstraction tường minh

Trước khi tạo aspect mới, hãy trả lời:

1. **Ai là người sở hữu quy tắc?** Team miền, nhóm bảo mật hay nhóm hạ tầng?
2. **Quy tắc có thay đổi theo sản phẩm/khách hàng không?** Nếu có, policy miền nên được biểu diễn ở đâu?
3. **Một người chỉ đọc use case có hiểu business outcome không?** Nếu không, có luật bị giấu trong advice?
4. **Bỏ cơ chế AOP có làm sai dữ liệu không?** Nếu có, cần bảo vệ invariant ở domain/storage hoặc enforcement boundary đáng tin.
5. **Pointcut có bền vững khi đổi tên, tách helper hoặc di chuyển class không?**
6. **Có cần gọi proceed nhiều lần hoặc thay đổi return value không?** Nếu có, contract và idempotency đã rõ chưa?
7. **Có nhiều cơ chế cùng quan sát một operation không?** Nếu có, ai chịu trách nhiệm metric/log outcome?
8. **Có async, retry, transaction hoặc cancellation không?** Các mốc lifecycle đã được phân biệt?
9. **Có thể kiểm thử policy riêng mà không cần weaving không?**
10. **Một lần sửa business rule có ảnh hưởng những operation không liên quan?** Nếu có, phạm vi advice quá rộng?
11. **Chi phí performance/traceability có tương xứng lợi ích giảm phân tán mã không?**
12. **Có đường gọi nào đi vòng qua proxy hoặc observer không?** Invariant còn đúng trên đường đó không?

Nếu đa số câu trả lời chỉ ra logic miền có nhiều biến thể hoặc phụ thuộc state, ưu tiên **policy object, domain service, state machine hoặc use case method**. Nếu concern có semantics nhất quán và điểm cắt ổn định, aspect là ứng viên tốt.

## 20. Kiểm thử kiến trúc và hành vi

### Kiểm thử chính sách nghiệp vụ

- Tất cả trường hợp premium/regular và biên quantity, đơn giá đều cho kết quả theo công thức.
- Giá trị không hợp lệ ném exception được định nghĩa.
- Overflow, rounding và currency được xử lý theo contract.
- Thay đổi rule không bắt buộc sửa logging advice.
- Domain invariant vẫn đúng khi gọi implementation trực tiếp, không qua proxy.
- Side effect phải phản ánh kết quả policy tại commit, không phản ánh giá trị đã lỗi thời.

### Kiểm thử Aspect

- Pointcut chỉ chọn operations cần quan sát hoặc bảo vệ.
- Advice gọi proceed đúng một lần khi không có retry được thiết kế riêng.
- Return value và exception gốc được giữ nguyên.
- Method helper hoặc direct call không có logging nếu không thuộc điểm cắt.
- Nhiều aspect lồng nhau không đếm business outcome trùng.
- Với async, không ghi completion chỉ vì method trả stage.
- Thread/correlation context được cleanup trong finally, không rò vào thread pool.
- Failure của observer có chính sách rõ, không vô tình làm business call sai.

### Kiểm thử tích hợp

- Đường vào thật (request, message, job) gọi tới use case chính xác.
- Protected operations không bypass enforcement cần thiết.
- Thứ tự authorization, retry, transaction và logging đúng contract.
- Một thay đổi pointcut không vô tình khiến luật nghiệp vụ mất hiệu lực.
- Một domain refactor không khiến số business events tăng/giảm vô cớ.

Một tiêu chuẩn mạnh: **kiểm thử quy tắc miền vẫn thành công khi tắt các aspect chỉ mang chức năng quan sát**. Với aspect authorization/transaction ảnh hưởng behavior, cần kiểm thử riêng các ranh giới bắt buộc, không vô hiệu hóa cơ chế an toàn trong hệ thống đang vận hành.

## 21. Quy trình cải tổ một Aspect đang chứa quá nhiều business logic

**Bước 1 — Khoanh vùng behavior.** Liệt kê những câu điều kiện trong advice quyết định giá, trạng thái, hạn mức, quyền sở hữu hoặc side effect; đánh dấu những nhánh thuần hạ tầng.

**Bước 2 — Xác định chủ sở hữu miền.** Quy tắc nào thuộc pricing, order, payment, membership hay inventory? Ai phê duyệt thay đổi policy?

**Bước 3 — Viết kiểm thử đặc tả hiện hành.** Bao gồm trường hợp thường, biên, failure, concurrency và dữ liệu cũ. Mục tiêu là bảo toàn kết quả đúng, đồng thời phơi bày behavior đã sai hoặc mơ hồ.

**Bước 4 — Tách policy có tên và hợp đồng.** Đưa công thức, state transition và điều kiện chuyên biệt về domain service/policy/aggregate. Công bố input, output, lỗi, rounding và version.

**Bước 5 — Cho use case gọi policy tường minh.** Luồng đọc code thể hiện thời điểm quyết định nghiệp vụ, không dựa vào tên method để lời khuyên AOP kích hoạt.

**Bước 6 — Thu hẹp advice còn mechanism chung.** Logging, metrics, tracing hoặc enforcement đã có metadata và policy component đúng nghĩa; xóa các nhánh logic miền không còn cần thiết.

**Bước 7 — Xác minh mọi đường gọi.** Direct implementation, proxy, nested call, async, retry và các entry paths khác nhau.

**Bước 8 — Đối chiếu kết quả và observability.** Nghiệp vụ sau refactor giữ semantics dự kiến; telemetry chỉ phát các tín hiệu đúng owner, không nhân đôi.

Không cần di chuyển mọi nhánh trong một lần. Mỗi phần được tách phải có contract và kiểm thử trước khi thay đường gọi, đặc biệt nếu hệ thống đã có các workflow chứa dữ liệu quan trọng.

## 22. Checklist review nhanh

- [ ] Quy tắc quyết định business outcome xuất hiện qua service/entity/policy có tên rõ.
- [ ] Domain invariant được bảo vệ ở điểm có thẩm quyền, kể cả khi bypass proxy.
- [ ] Aspect áp dụng concern xuyên suốt có semantics chung và pointcut đã được kiểm chứng.
- [ ] Pointcut không phụ thuộc tràn lan vào tên helper hoặc package dễ đổi.
- [ ] Metadata/annotation diễn đạt chính sách công khai, không che giấu hàng loạt trường hợp miền.
- [ ] Advice bảo toàn return value và exception, trừ tác động đã được công bố.
- [ ] Retry có idempotency và error policy; transaction có boundary chính xác.
- [ ] Async invocation, task completion và durable commit được phân biệt.
- [ ] Logging/audit không tự tạo nguồn chân lý nghiệp vụ và không lộ dữ liệu nhạy cảm.
- [ ] Metric/span/log có owner, không double count khi nhiều công cụ cùng hoạt động.
- [ ] Policy có unit tests độc lập; Aspect có tests riêng cho interception/composition.
- [ ] Domain refactor không khiến quy tắc biến mất do đổi tên hoặc thay đường gọi.
- [ ] Quyết định tổ chức mã giúp người bảo trì hiểu điều kiện nghiệp vụ từ use case.

## 23. Tóm lại qua một tình huống

Giả sử đội phát triển nhận yêu cầu: “Khách premium được giảm 10% cho một tập sản phẩm hợp lệ; đơn hàng vượt hạn mức phải từ chối; mọi yêu cầu báo giá cần có duration và outcome log.”

Kiến trúc rõ ràng:

- **PricingPolicy** xác định hàng nào được giảm, tỷ lệ, thứ tự tính thuế và rounding.
- **Eligibility/LimitPolicy** xác định hạn mức và những điều kiện từ chối.
- **QuoteUseCase** gọi các policy, tạo kết quả và điều phối hành vi.
- **ObservabilityAspect** ghi một event kết thúc, duration và correlation ID theo boundary đã chọn.
- **Authorization mechanism** chỉ cho phép các đối tượng đủ quyền truy cập operation, với policy riêng nếu cần.

Khi tỷ lệ chiết khấu đổi, không phải sửa aspect đo thời gian; khi schema log đổi, không phải viết lại PricingPolicy. Khi thêm một use case báo giá, người review nhìn thấy chính sách được gọi và cách chọn pointcut. Các quy tắc ảnh hưởng business outcome vẫn có tên, quyền sở hữu và kiểm thử.

**Bài học:** giữ quy tắc nghiệp vụ cốt lõi tường minh là điều kiện để AOP phát huy lợi ích mà không làm mất khả năng giải thích hệ thống. Aspect xử lý mechanism xuyên suốt khi điểm cắt và hợp đồng đủ rõ; chính sách miền quyết định giá trị, trạng thái và invariant tại nơi có thể xác minh chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-architecture-design-009">Tài liệu hóa phạm vi, hợp đồng và các quyết định về thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Tài liệu hóa phạm vi, hợp đồng và các quyết định về thứ tự trong AOP

## 1. Mục tiêu và vấn đề cần giải quyết

Aspect-Oriented Programming (AOP) đưa những mối quan tâm cắt ngang như xác thực, phân quyền, ghi log, đo lường, transaction, retry và audit ra khỏi thân phương thức nghiệp vụ. Một **pointcut** mô tả tập *join point* được chọn; **advice** thực hiện hành vi tại những điểm đó; cơ chế **weaving**, instrumentation hay proxy khiến lời gọi thật đi qua advice.

Tuy nhiên, tách mã không có nghĩa tách trách nhiệm hoặc loại bỏ phụ thuộc. Một phương thức có thể trông như không liên quan đến audit, nhưng trong production lại phải có audit; một lời gọi có thể yêu cầu transaction trước khi truy cập dữ liệu; hoặc một kết quả bất đồng bộ cần giữ context tới khi hoàn tất. Nếu việc này chỉ tồn tại trong biểu thức pointcut và cấu hình thứ tự ở nơi khác, người sửa mã rất khó biết thay đổi nào làm mất hợp đồng.

**Tài liệu kiến trúc AOP cần trả lời được ba câu hỏi:**

1. **Phạm vi:** Những điểm thực thi nào phải, có thể hoặc tuyệt đối không được nhận advice? Đường lời gọi nào đi qua cơ chế AOP?
2. **Hợp đồng:** Advice được quan sát hoặc thay đổi những gì? Nó có thể bỏ qua thao tác gốc, gọi lại, thay đổi exception, ngăn access, mở transaction, cấp tài nguyên hay tạo side effect không?
3. **Thứ tự:** Khi nhiều advice cùng áp dụng, cái nào ở ngoài/trong, thứ tự vào/ra và thứ tự xử lý lỗi là gì; tại sao chọn như thế và cách xác minh ra sao?

Bài này tập trung vào **AOP nói chung**, không ràng buộc người học vào một thư viện hoặc cơ chế gắn advice nhất định. Cú pháp biểu thức pointcut và quy tắc ưu tiên cụ thể luôn phải được tra theo engine đang sử dụng; tài liệu kiến trúc cần nêu chính xác quy tắc của engine được chọn thay vì giả định mọi engine có cùng semantics.

## 2. Từ join point tới phạm vi áp dụng thực tế

### 2.1 Phân biệt các tầng phạm vi

- **Join point model:** những điểm mà engine có khả năng can thiệp: method execution, method call, constructor, field access hay các loại khác tùy engine.
- **Pointcut expression:** điều kiện khai báo chọn các join point trong mô hình đó: package, lớp, tên, chữ ký, annotation hoặc ngữ cảnh lời gọi.
- **Weaving/wiring:** cách thực sự gắn advice. Proxy qua interface thường chỉ can thiệp lời gọi tới đối tượng proxy; bytecode weaving có thể có bề mặt khác.
- **Runtime entrypoint:** đường mà chương trình thật gọi tới nghiệp vụ. Một phương thức xuất hiện trong danh sách match chưa có nghĩa mọi đường tới nó đều được chặn.
- **Logical operation:** đơn vị nghiệp vụ cần chính sách, chẳng hạn `order.place` hoặc `payment.capture`. Một operation có thể đi qua nhiều method và một method có thể phục vụ nhiều operation.

Đừng dùng một câu mơ hồ như “áp dụng cho toàn bộ service”. Cần xác định cụ thể tập đầu vào, các ngoại lệ, cách match và cách quan sát runtime.

### 2.2 Ghi phạm vi bằng danh mục operation

Một danh mục tốt có thể biểu diễn như bảng:

| Operation | Điểm vào / join point | Advice bắt buộc | Ngoại lệ / không áp dụng | Đơn vị |
| --- | --- | --- | --- | --- |
| `order.place` | Cổng nhận lệnh đặt hàng | Authorization, transaction, audit, metrics | Không áp dụng lên helper thuần túy | Một lệnh logic |
| `order.quote` | Cổng báo giá | Metrics, tracing | Không cần transaction ghi | Một lời gọi |
| `payment.capture` | Lệnh thu tiền | Authorization, idempotency, audit, metrics | Không tự retry thao tác không idempotent | Một ý định thanh toán |
| `invoice.preview` | Tạo bản xem thử | Metrics | Không ghi audit thay đổi dữ liệu | Một lần tạo preview |

Đây là **ví dụ kiến trúc**. Khi dùng trong dự án, mỗi hàng còn cần ID ổn định, owner, nơi khai báo pointcut, điểm giải quyết context, mức độ quan trọng và bộ test minh chứng. Tên method có thể xuất hiện để định vị code, nhưng **không tự trở thành căn cứ cấp quyền**.

### 2.3 Ghi danh sách bao phủ và khoảng trống

Có thể biểu diễn:

- `R`: tập operation/join point **bắt buộc** áp chính sách.
- `M`: tập join point **thực sự match và được instrument**.
- `R ∖ M`: những điểm cần chính sách nhưng bị bỏ sót (*false negative*).
- `M ∖ R`: những điểm bị áp dụng ngoài dự kiến (*false positive*).

Ví dụ pointcut dùng tên `save*` có thể bỏ sót `placeOrder` dù phương thức cũng ghi dữ liệu. Một pointcut dùng package có thể bắt nhầm utility thuần túy. Tài liệu cần ghi **điều kiện khớp** lẫn **danh sách kỳ vọng**, vì chỉ chép nguyên regex không nói được ý định.

Nếu engine không hỗ trợ xuất danh sách match, sử dụng integration test qua entrypoint thật với advice thử nghiệm để ghi các điểm thực thi được quan sát. Khi thay đổi pointcut, so sánh tập thực tế trước/sau.

### 2.4 Những đường gọi cần được ghi rõ

| Đường gọi hoặc trường hợp | Rủi ro tài liệu thường bỏ sót |
| --- | --- |
| Lời gọi từ bên ngoài qua proxy | Thường là đường dễ thấy nhất, nhưng cấu hình có thể thiếu |
| Target được gọi trực tiếp | Đi vòng proxy, advice không chạy |
| Self-invocation (`this.method()`) | Có thể không đi qua proxy tùy engine |
| Lớp/constructor/static/final/private method | Khả năng can thiệp phụ thuộc kỹ thuật weaving/proxy |
| Method overload, generic bridge, annotation trên interface | Resolver có thể chọn nhầm chữ ký hoặc không thấy metadata |
| Callback, task trên executor, consumer sự kiện | Đường vào khác với lời gọi HTTP/CLI ban đầu |
| Method trả future/publisher | Trả handle chưa đồng nghĩa hoàn tất công việc |
| Reflection và dynamic dispatch | Điểm chặn phụ thuộc vào đường gọi và engine thực tế |

Khi cần bảo vệ một tài nguyên quan trọng, tài liệu phải chỉ ra **ranh giới cưỡng chế** mà mọi đường tới tài nguyên đều bắt buộc đi qua, hoặc lớp kiểm soát bổ sung tại kho dữ liệu/resource. Không dựa vào giả định “advice áp dụng mọi nơi” khi chưa kiểm chứng.

## 3. Hợp đồng của một advice gồm những gì?

Một **advice contract** là mô tả rõ quyền và nghĩa vụ của mã cắt ngang. Nên ghi:

- **Precondition:** điều gì phải có trước khi advice chạy: principal đã xác thực, request context, deadline, transaction state, cấu hình metric.
- **Execution semantics:** advice có gọi thao tác gốc không, tối đa bao nhiêu lần, thời điểm nào; có được short-circuit hoặc retry không.
- **Success semantics:** caller nhận kết quả gốc hay kết quả biến đổi? Có ghi audit/commit/cache hay không?
- **Failure semantics:** exception gốc được giữ nguyên, chuyển đổi hay nuốt; lỗi instrumentation có được làm hỏng nghiệp vụ không?
- **Cancellation/interruption:** khi cancel, timeout hoặc thread bị interrupt, xử lý tài nguyên và tín hiệu như thế nào.
- **Ownership:** ai cấp và ai giải phóng lock, transaction, context scope, semaphore permit, span, buffer.
- **Side effects:** ghi log/audit, tạo telemetry, gọi dịch vụ phụ, retry, ghi cache, phát sự kiện; có thực hiện trước hay sau commit?
- **Invariants:** điều kiện bắt buộc giữ, ví dụ “một call được chấp nhận gây đúng một lần gọi gốc” hoặc “không side effect khi chưa được phân quyền”.
- **Non-functional properties:** overhead, allocation, timeout, giới hạn queue và hành vi khi backend telemetry lỗi.

Không phải advice nào cũng được phép “thực thi gốc đúng một lần”. Advice caching có thể trả cache hit mà không gọi gốc; retry advice có thể cố ý gọi nhiều lần. Điều quan trọng là **ngoại lệ khỏi nguyên tắc một lần phải được ghi rõ**, kèm idempotency, số lần tối đa và tác động side effect.

### 3.1 Bảng hợp đồng minh họa

| Aspect | Có thể chặn lời gọi? | Có thể gọi lại gốc? | Thay đổi kết quả/lỗi? | Tài nguyên và điều kiện |
| --- | --- | --- | --- | --- |
| Authorization | Có, khi deny | Không | Có thể trả lỗi từ chối định nghĩa trước | Principal/action/resource/context tin cậy |
| Metrics | Không, nếu quan sát thuần túy | Không | Không, theo chính sách cách ly telemetry | Bộ đếm/histogram, bộ đệm có giới hạn |
| Retry | Có thể dừng sau số lần thử | Có, theo chính sách | Có thể đưa lỗi cuối khi hết lượt | Idempotency, backoff, deadline, hủy |
| Transaction | Có thể rollback/đưa lỗi | Thường không | Theo hợp đồng transaction | Begin/commit/rollback, ownership |
| Audit bắt buộc | Có thể fail-closed | Không tự gọi lại nghiệp vụ | Có thể từ chối nếu audit không được đảm bảo | Kho audit có độ bền được định nghĩa |
| Tracing | Không, nếu chỉ quan sát | Không | Không, trừ chính sách đã công bố | Span/context scope, propagation |

Bảng không áp đặt mọi dự án phải thực hiện như vậy; nó bắt người thiết kế **quyết định tường minh**. Một aspect đo lường có thể được thiết kế fail-open, còn một policy audit có thể fail-closed. Không được lấy chính sách tiện nhất cho telemetry áp dụng ngầm cho bảo mật.

### 3.2 Hợp đồng lỗi và `finally`

Một around advice đồng bộ thường đặt cleanup trong `finally`:

~~~java
long started = System.nanoTime();
try {
    return proceedOnce();
} finally {
    long elapsed = System.nanoTime() - started;
    recordMeasurement(elapsed);
}
~~~

Đây là **pseudocode Java**, các hàm `proceedOnce` và `recordMeasurement` chỉ biểu diễn ý tưởng. Cần lưu ý `recordMeasurement` có thể ném exception và che mất kết quả/lỗi nghiệp vụ nếu không cô lập lỗi đo đạc theo hợp đồng. Tương tự, transaction advice phải phân biệt thành công và exception, xử lý rollback/commit theo semantics thật.

Điều kiện đặc biệt như `Error` nghiêm trọng, huỷ tác vụ hoặc tiến trình bị dừng không thể được bảo đảm giải phóng tài nguyên chỉ bằng lời hứa “finally luôn chạy”. Tài liệu cần giới hạn mức bảo đảm và cơ chế khôi phục bên ngoài nếu có.

## 4. Thứ tự advice là một phần của hành vi quan sát được

Giả sử có ba decorator dạng around:

~~~text
A(B(C(operation)))
~~~

Khi thành công, thứ tự đi vào là:

~~~text
A enter
B enter
C enter
operation
C exit
B exit
A exit
~~~

Đây là **thứ tự lồng nhau dạng stack**, không phải “A rồi B rồi C và kết thúc”. Với `finally`, phần thoát thường diễn ra theo chiều ngược lại. Nếu một advice ném lỗi hoặc short-circuit, những advice nằm phía trong có thể **không hề chạy**.

### 4.1 Tài liệu phải dùng từ “outside/inside”

Câu “authorization trước transaction” vẫn có thể bị hiểu mơ hồ nếu engine có quy tắc ưu tiên ngược với cách viết danh sách. Ghi rõ:

~~~text
Outer -> inner:
  Audit -> Authorization -> Transaction -> Operation

Một lần thành công:
  audit-start
  authorization-check
  transaction-open
  business
  transaction-commit
  audit-success

Một lần bị deny:
  audit-start
  authorization-deny
  audit-failure
  (transaction không được mở, business không chạy)
~~~

Thứ tự này là **ví dụ hợp đồng đã chọn**, không phải thứ tự mặc định của mọi AOP engine. Engine nào cũng cần được kiểm tra mapping cụ thể từ cấu hình order/precedence sang chuỗi ngoài→trong.

### 4.2 Những thay đổi khi đổi vị trí aspect

**Authorization ngoài transaction:** chính sách phân quyền được kiểm tra trước khi mở transaction nghiệp vụ; từ chối quyền không mở transaction đó. Nếu policy cần đọc dữ liệu phải nhất quán với transaction, việc kiểm tra có thể cần cơ chế phối hợp khác; ghi rõ lựa chọn.

**Transaction ngoài authorization:** kiểm tra quyền nằm trong transaction, có thể giúp đọc resource state nhất quán, nhưng một request bị deny vẫn có chi phí transaction. Nếu quyền cần được kiểm tra trước bất kỳ side effect nào, các side effect khác ở tầng transaction/begin phải được đánh giá.

**Retry ngoài transaction:** mỗi attempt có thể mở transaction riêng. **Transaction ngoài retry:** nhiều attempt có thể nằm trong cùng một transaction tùy semantics; rollback, lock và khả năng retry của tài nguyên có thể khác. Không tự coi hai cách tương đương.

**Metrics ngoài retry:** thường đo latency của **logical operation**, gồm các lần thử và chờ backoff. **Metrics trong retry:** đo từng attempt. Cả hai đều có giá trị, nhưng **counter, mẫu số lỗi và tên metric** khác nhau.

**Audit ngoài cache:** có thể audit cả request cache hit. **Audit trong cache:** nếu cache trả về trước khi gọi gốc, advice bên trong có thể không chạy. Nếu audit là yêu cầu bắt buộc với mọi lần đọc nhạy cảm, thứ tự đó có thể vi phạm hợp đồng.

**Tracing ngoài async callback:** span có thể chỉ bao quanh thời gian tạo future. Muốn span end-to-end phải thiết kế closure tại completion và truyền context theo vòng đời.

Bởi vậy, “thứ tự” không chỉ là số ưu tiên. Tài liệu nên có **trace thời gian, semantics trong các nhánh success/deny/failure/cancel và lý do chọn**, rồi test với chính đường thực thi đó.

## 5. Ví dụ Java 17+: chứng minh thứ tự bằng composition tường minh

Để thấy cách **outside/inside** thay đổi hành vi, chương trình sau dùng interface và decorator thuần Java. Đây là mô hình minh họa cơ chế bao quanh tương đương với around advice; nó không phụ thuộc runtime weaving. Các hàm `audit`, `authorize` và `transaction` đại diện cho ba advice, còn `business` là hành vi gốc.

**Tệp: `AspectOrderDemo.java`**

~~~java
public class AspectOrderDemo {
    @FunctionalInterface
    interface Operation {
        String call(String id);
    }

    static Operation audit(Operation next) {
        return id -> {
            System.out.println("audit enter");
            try {
                String result = next.call(id);
                System.out.println("audit success");
                return result;
            } catch (RuntimeException error) {
                System.out.println("audit failure");
                throw error;
            } finally {
                System.out.println("audit exit");
            }
        };
    }

    static Operation authorize(Operation next) {
        return id -> {
            System.out.println("auth check");
            if (!id.startsWith("ok-")) {
                throw new SecurityException("denied");
            }
            return next.call(id);
        };
    }

    static Operation transaction(Operation next) {
        return id -> {
            // Đây chỉ là dấu vết thứ tự, không phải transaction ACID.
            System.out.println("tx begin");
            try {
                String result = next.call(id);
                System.out.println("tx commit");
                return result;
            } catch (RuntimeException error) {
                System.out.println("tx rollback");
                throw error;
            } finally {
                System.out.println("tx close");
            }
        };
    }

    static void invoke(Operation operation, String id) {
        try {
            System.out.println("caller=" + operation.call(id));
        } catch (SecurityException denied) {
            System.out.println("caller=DENIED");
        }
    }

    public static void main(String[] args) {
        Operation business = id -> {
            System.out.println("business " + id);
            return "saved-" + id;
        };

        Operation auditOutside =
            audit(authorize(transaction(business)));
        System.out.println("=== audit outside ===");
        invoke(auditOutside, "ok-7");
        invoke(auditOutside, "bad-3");

        Operation authOutside =
            authorize(audit(transaction(business)));
        System.out.println("=== auth outside ===");
        invoke(authOutside, "bad-3");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AspectOrderDemo.java
java AspectOrderDemo
~~~

**Stdout chính xác:**

~~~text
=== audit outside ===
audit enter
auth check
tx begin
business ok-7
tx commit
tx close
audit success
audit exit
caller=saved-ok-7
audit enter
auth check
audit failure
audit exit
caller=DENIED
=== auth outside ===
auth check
caller=DENIED
~~~

**Phân tích:**

- Với `audit(authorize(transaction(business)))`, audit **bao ngoài** authorization và transaction. Khi `ok-7` được phép, cả ba lớp chạy theo thứ tự vào rồi thoát ngược.
- Với `bad-3` ở biến thể audit ngoài cùng, authorization từ chối trước khi transaction và business chạy. Audit vẫn ghi được một lần từ chối và `audit exit` luôn xuất hiện.
- Với `authorize(audit(transaction(business)))`, authorization nằm **ngoài** audit. Khi bị deny ngay từ authorization, audit không chạy. Chỉ thay đổi thứ tự đã làm thay đổi hành vi quan sát được.
- Code `transaction` **không mở transaction thật**, chỉ mô phỏng lifecycle để diễn giải thứ tự. Một implementation production còn phải quản lý commit/rollback/connection, transaction nesting và lỗi khi commit; không được suy ra tính nguyên tử dữ liệu từ các dòng in.
- Kiểm tra `id.startsWith("ok-")` chỉ là **điều kiện demo**, hoàn toàn không phải chính sách bảo mật thực tế. Authorization thật phải dùng principal đã xác thực, action, resource và context tin cậy; không được suy quyền chỉ từ ID hoặc prefix do caller gửi.

Khi dùng engine AOP thực tế, bạn không nhất thiết gọi decorator thủ công, nhưng **hợp đồng thứ tự** cần mô tả kết quả tương tự và được xác minh bằng test tích hợp qua điểm chặn thật. Nếu engine không cho phép đảm bảo thứ tự như yêu cầu, phải điều chỉnh thiết kế hoặc hợp đồng thay vì giả vờ thứ tự không quan trọng.

## 6. Mẫu tài liệu dành cho một aspect

Một bản ghi gọn nhưng đủ khả năng kiểm chứng có thể dùng cấu trúc sau:

~~~yaml
aspectId: order-audit
purpose: "Ghi nhận kết quả các lệnh thay đổi đơn hàng"
owner: "Nhóm sở hữu kiểm soát audit"
criticality: required
version: 1

scope:
  operationIds: ["order.place", "order.cancel"]
  joinPointKind: "method execution tại command boundary"
  pointcutBinding: "annotation/manifest đã kiểm tra"
  exclusions: ["health probes", "pure utilities"]
  runtimeEntrypoints: ["command gateway", "job consumer"]

contract:
  allowedToShortCircuit: false
  originalInvocationCount: 1
  success: "ghi audit sau kết quả được xác nhận theo chính sách"
  error: "ghi loại lỗi an toàn, giữ nguyên lỗi gốc"
  cancellation: "ghi canceled theo hợp đồng vòng đời"
  timeout: "có timeout rõ cho sink"
  dataClassification: "không ghi token, secret hoặc payload nhạy cảm"
  auditSinkFailure: "theo chính sách fail-closed đã được duyệt"

order:
  outside: ["authorization", "transaction"]
  inside: ["metrics"] 
  rationale: "ghi lại cả các kết quả bị từ chối trước thực thi"
  verifiedSequence: "xác nhận trong integration test"

verification:
  coverageTest: "đối chiếu danh mục operation với pointcut match"
  orderTest: "success, deny, exception, retry, cancellation"
  performanceTest: "overhead tại tải đỉnh"
~~~

**Đây chỉ là mẫu YAML minh họa**, không phải cấu hình thực thi của một framework. Giá trị `outside/inside` cần định nghĩa một quy ước nhất quán; ví dụ này đòi hỏi nhóm kỹ thuật phải suy ra thứ tự cụ thể cùng cơ chế mapping theo engine. Nếu có nhiều lớp nằm ngoài/trong, hãy ghi **một chuỗi lồng nhau đầy đủ** và không chỉ hai danh sách rời.

Một mô tả như “audit chạy sau transaction” cũng chưa đủ khi cần biết nó ghi trước commit, sau commit, sau rollback hay sau kết quả được công bố. Nếu yêu cầu lưu audit chắc chắn đi cùng thay đổi dữ liệu, phải xác định mô hình độ bền và atomicity của sink; chỉ gắn một advice ở “sau” không tự tạo bảo đảm giao dịch.

### Các trường tài liệu không thể thiếu

1. **Tên và trách nhiệm:** phân biệt aspect quan sát, aspect thay đổi semantics và aspect kiểm soát bắt buộc.
2. **Owner và đường tới code/config:** biết người chịu trách nhiệm sửa pointcut và cập nhật test.
3. **Operation contract:** chính sách tác động tới request logic, lời gọi method, attempt hay event?
4. **Join point/kỹ thuật gắn:** method execution hay call; proxy, weaving hoặc instrumentation; giới hạn các loại method.
5. **Điều kiện chọn:** biểu thức thực tế, annotation, manifest, danh sách exclude và đường bypass có thể xảy ra.
6. **Hành vi qua mọi trạng thái:** success, failure, deny, timeout, retry, cache hit, cancel, async completion.
7. **Thứ tự/tương tác:** đầy đủ outer→inner, ai nắm transaction/context, ai đếm logical operation và attempt.
8. **Ngưỡng vận hành:** timeout, overhead, budget memory/queue, logging rate, sampling và degraded mode.
9. **Test chứng minh:** nơi kiểm tra coverage/ordering, các lệnh chạy và kết quả kỳ vọng.
10. **Lý do quyết định:** các phương án đã cân nhắc, rủi ro, lúc nào phải xem xét lại.

## 7. ADR: ghi quyết định thứ tự bằng lý do, không chỉ cấu hình

**Architecture Decision Record (ADR)** phù hợp để ghi các quyết định ảnh hưởng toàn codebase. Nó cần có:

- **Bối cảnh:** hệ thống có những aspect nào, điểm nào chịu transaction, audit, retry, cache, tracing; lỗi thực tế nào cần ngăn.
- **Yêu cầu:** không có side effect trước authorization; audit phải ghi cả deny; retry phải có giới hạn; metrics end-to-end; deadline.
- **Các phương án:** authorization ngoài/ trong transaction, audit ngoài/trong authorization, retry ngoài/trong transaction.
- **Quyết định cụ thể:** outer→inner và semantic trên nhánh success/error/deny/cancel.
- **Lập luận:** vì sao thứ tự đó đúng cho mục tiêu dữ liệu/bảo mật/quan sát.
- **Hệ quả:** chi phí, bất lợi, những trường hợp vẫn chưa được bảo vệ.
- **Kiểm chứng:** integration test hoặc trace minh chứng, và điều kiện cần review lại.

Ví dụ ADR rút gọn:

~~~markdown
# ADR: Audit bao ngoài Authorization

## Trạng thái
Accepted

## Bối cảnh
Mọi lời gọi vào cổng đặt hàng cần ghi nhận kết quả cho phép/từ chối.

## Quyết định
Chuỗi outer -> inner: Audit -> Authorization -> Transaction -> Business.
Deny bởi Authorization không mở Transaction và không chạy Business.
Audit phát một bản ghi kết quả deny với dữ liệu đã được làm sạch.

## Hệ quả
Có chi phí audit cho các lời gọi bị từ chối.
Lỗi audit sink được xử lý theo chính sách riêng đã được phê duyệt.

## Kiểm chứng
Integration test xác nhận sequence khi success, deny, business failure.
~~~

Đây là **một mẫu tài liệu ADR**, không phải mô tả về một hệ thống đã triển khai. ADR phải được liên kết từ operation catalog và từ code/config pointcut để người refactor có thể truy ngược lý do.

## 8. Quy tắc đặt tên metric và định nghĩa mẫu số cũng là hợp đồng

Khi metrics được lồng cùng retry hoặc cache, thứ tự ảnh hưởng trực tiếp số đếm:

- `requests_total`: đếm yêu cầu logic ở biên; một yêu cầu có thể tạo nhiều attempts.
- `attempts_total`: đếm từng lần thử.
- `denied_total`: đếm các lần bị từ chối trước khi nghiệp vụ chạy.
- `operation_duration_seconds`: cần biết bao gồm queue/retry/backoff hay chỉ một attempt.
- `transaction_duration_seconds`: chỉ thời gian transaction thực tế, không tự tương đương thời gian end-to-end.

Với một request bị lỗi hai lần, rồi thành công lần ba, nếu metrics ở ngoài retry, nó có thể ghi một logical request thành công; metrics ở trong retry có thể ghi ba attempts với hai lỗi. Cả hai bộ số đều có thể đúng theo định nghĩa, nhưng không được trộn mẫu số của chúng.

**Tài liệu cần định nghĩa đơn vị đo, điểm bắt đầu/kết thúc, loại lỗi, cách xử lý timeout/cancel và cách aggregating qua nhiều instance.** Không nên dùng một tên metric cũ khi thay đổi semantics của advice; cân nhắc version hoặc tên mới để dashboard và cảnh báo không im lặng đổi ý nghĩa.

## 9. Hợp đồng cho lời gọi bất đồng bộ và stream

### 9.1 Future/CompletionStage

Một around advice bao quanh method trả về `CompletionStage<Result>` có thể kết thúc ngay lúc method trả stage, dù I/O và kết quả thật sẽ tới sau. Cần ghi rõ:

- **Giới hạn quan sát:** đo thời gian phương thức trả stage hay thời gian stage hoàn tất?
- **Thời điểm kết thúc:** success/exceptional completion/cancellation/timeout, có đúng một lần terminal không?
- **Resource ownership:** transaction, semaphore permit, trace span hoặc context scope được giải phóng ở đâu?
- **Failure mapping:** exception tại lúc tạo stage khác exceptional completion về sau.
- **Callback affinity:** callback có thể chạy trên thread hoàn tất stage; tránh I/O chặn hoặc ném lỗi telemetry làm thay đổi kết quả.
- **Context propagation:** `ThreadLocal` trên thread gọi không tự đi qua executor.

Không thể tự động bảo đảm một transaction nằm bên ngoài lời gọi tạo future sẽ bao trùm toàn bộ việc xử lý bất đồng bộ. Nếu cần transaction cho phần xử lý thực tế, chọn boundary ở nơi thực hiện thao tác dữ liệu và xác minh bằng test.

### 9.2 Publisher và luồng sự kiện

Một publisher có thể chưa chạy khi object được tạo, sau đó có nhiều subscription và nhiều phần tử. Tài liệu phải nói rõ **một operation** đang được tính theo lần tạo publisher, lần subscribe, từng `onNext` hay toàn vòng đời subscription.

Nếu advice can thiệp vào callback, phải bảo toàn số `request(n)`, `cancel`, tín hiệu terminal và thứ tự theo hợp đồng của giao thức. Một advice logging không được tự `subscribe` để “xem dữ liệu”, thay đổi demand hoặc tạo queue vô hạn rồi làm hỏng backpressure. Đối với nguồn dài vô hạn, không thể giả định “sẽ có onComplete” để đóng mọi tài nguyên.

### 9.3 Cancellation và timeout

Cần phân biệt **trạng thái đại diện** với **công việc nền thật sự đã dừng**. Ví dụ hủy một `CompletableFuture` không đảm bảo mọi I/O đã bị ngắt. Nếu một advice trả permit ngay khi future chuyển sang canceled nhưng job nền vẫn sử dụng tài nguyên, concurrency limiter có thể nhận thêm công việc quá capacity thực. Chính sách phải xác định “in-flight” theo tài nguyên thực tế và có cơ chế cleanup phù hợp.

## 10. Chi phí quan sát và giới hạn tài nguyên là một phần tài liệu

Thứ tự advice cũng thay đổi chi phí và hành vi khi quá tải:

| Aspect / vị trí | Ảnh hưởng có thể xảy ra |
| --- | --- |
| Tracing outer | Bao cả authorization, chờ retry và transaction; span tồn tại lâu hơn |
| Metrics inner | Ít đo overhead bên ngoài; có thể chỉ thấy attempts được phép |
| Audit outer | Ghi cả deny/cache hit nếu cơ chế bên trong có thể short-circuit |
| Rate limit outer | Chặn trước khi tạo tài nguyên tốn kém |
| Rate limit inner | Một lớp outer vẫn chạy, thậm chí tạo side effect trước khi bị từ chối |
| Retry outer | Có thể tạo nhiều lần vào các aspect bên trong |
| Transaction outer | Giữ transaction qua retry/backoff nếu thiết kế như vậy, có thể làm tăng lock time |

Không kết luận “outer luôn tốt hơn”: lựa chọn phải bám vào contract. Mỗi aspect có overhead; điểm cắt rộng còn có thể instrument thư viện metrics/logging và dẫn đến recursion. Tài liệu cần ghi scope exclude và cách kiểm soát cấp phát bộ nhớ, cardinality label, queue và I/O.

Đối với audit/logging không đồng bộ, định nghĩa queue capacity, timeout, retry, trường hợp collector lỗi và chính sách mất bản ghi. Nếu audit bắt buộc, một hàng đợi vô hạn không phải giải pháp bảo đảm độ bền; nó có thể dẫn tới OOM. Nếu telemetry chỉ phục vụ vận hành, có thể chấp nhận sampling hoặc bỏ log theo chính sách rõ.

## 11. Bản đồ phụ thuộc và ranh giới trách nhiệm

Một module có thể không import aspect nhưng vẫn trông chờ hành vi từ aspect. Tài liệu kiến trúc nên thể hiện các quan hệ như:

~~~mermaid
flowchart TD
  A[Entrypoint / command gateway] --> B[Chuỗi advice]
  B --> C[Business operation]
  C --> D[Repository / resource]
  E[Operation catalog] --> B
  F[Policy và hợp đồng] --> B
  G[ADR thứ tự] --> B
  H[Integration tests] --> A
  H --> B
  H --> D
~~~

Các nhãn ở sơ đồ biểu diễn **trách nhiệm**, không áp đặt một framework cụ thể. **Operation catalog** khai báo những gì cần bảo vệ; **policy** nói hành vi mong đợi; **ADR** giải thích thứ tự; **test** chứng minh điểm chặn hiện thực đúng; **resource boundary** giữ các bất biến sống còn.

Phân chia owner cũng cần rõ:

- Nhóm nghiệp vụ sở hữu ý nghĩa operation và thông tin tài nguyên.
- Nhóm hạ tầng AOP sở hữu cơ chế gắn pointcut, vòng đời advice và thứ tự thực thi.
- Nhóm bảo mật/dữ liệu sở hữu chính sách quyền, yêu cầu audit, transaction, độ nhất quán và các biên bảo vệ.
- Nhóm vận hành sở hữu metric/SLO và ngân sách chi phí; thay đổi semantics cần được phối hợp để tránh phá dashboard.

Đây là cách giảm “không ai biết ai sửa pointcut” khi một thay đổi nhỏ tác động nhiều module.

## 12. Kiểm thử tài liệu bằng hành vi thực thi

Một tài liệu chỉ có ý nghĩa nếu các điểm quan trọng được **kiểm chứng tự động**. Cần ít nhất ba lớp:

### 12.1 Kiểm tra coverage tĩnh hoặc khi khởi động

- Liệt kê operation yêu cầu advice và tập pointcut thực tế match.
- Phát hiện phương thức mới không được phân loại, binding thiếu, pointcut đã cũ.
- Kiểm tra overload, annotation trên interface/implementation, bridge/default method, static/final/private nếu engine liên quan.
- Kiểm tra exclude để ngăn chọn nhầm telemetry/runtime infrastructure.
- Phát hiện đường bypass được biết trước và kiểm soát entrypoint.

Khi một policy bắt buộc bị thiếu, CI/startup nên thất bại hoặc xử lý fail-closed theo kiến trúc, chứ không chỉ để một dòng cảnh báo khó thấy.

### 12.2 Integration test thứ tự và semantics

Test phải đi qua đường gọi như production, không chỉ gọi trực tiếp đối tượng nghiệp vụ. Dùng probe ghi sequence để so sánh với ADR:

| Tình huống | Điều cần xác nhận |
| --- | --- |
| Thành công | Thứ tự enter/exit và ownership đúng |
| Authorization deny | Không có side effect nghiệp vụ; audit có/không theo contract |
| Exception nghiệp vụ | Rollback/translation, audit/metrics và lỗi gốc đúng |
| Retry hai lần rồi thành công | Số transaction, số attempt, số audit và metric đúng |
| Cache hit | Những advice cần chạy vẫn được thực thi |
| Timeout/cancel | Tài nguyên và context được giải phóng đúng lifecycle |
| Gọi target trực tiếp hoặc self-invocation | Phát hiện bypass, hoặc chứng minh boundary không cho phép |
| Future hoàn tất thất bại về sau | Không ghi “success” ngay khi trả stage |
| Concurrent calls | Không rò context, không dùng chung state sai |
| Đổi thứ tự aspect | Test phát hiện khác biệt với sequence đã quyết định |

### 12.3 Regression khi refactor

Chạy kiểm tra sau các thay đổi sau: đổi tên method, di chuyển package, thêm overload, sửa annotation, thay interface, đổi proxy wiring, chuyển sync→async hoặc thay precedence. Diff tập join point và tập operation đã instrument phải được xem như một phần review. Test bảo mật/transaction cần kiểm tra **lời gọi thực sự**, không chỉ việc matcher nhìn thấy tên trên metadata.

Không có test đơn lẻ nào thay thế được cả ba tầng. Ví dụ unit test business có thể pass ngay cả khi audit advice không được gắn; integration test chỉ dùng case thành công có thể bỏ sót sai thứ tự xử lý deny/rollback.

## 13. Quy trình duy trì tài liệu cùng mã

Một quy trình dễ áp dụng:

1. **Khi tạo operation:** đặt ID nghiệp vụ, phân loại yêu cầu audit, quyền, transaction, retry, metrics và xác định chủ sở hữu.
2. **Khi khai báo pointcut:** nêu rõ join point model, biểu thức/mapping, exclusions, engine và giới hạn kỹ thuật.
3. **Khi triển khai advice:** viết hợp đồng kết quả, lỗi, side effects, số lần gọi gốc, cleanup và async semantics.
4. **Khi kết hợp aspect:** ghi chuỗi outer→inner và sequence cho các nhánh quan trọng, kèm ADR lý do.
5. **Khi viết test:** bao phủ operation phải match, không được match, nhánh deny/error, thứ tự và đường bypass.
6. **Khi refactor:** cập nhật operation catalog, pointcut diff và ADR nếu thứ tự/hợp đồng thay đổi.
7. **Khi triển khai:** đối chiếu số advice invocation, metric và dấu hiệu thiếu coverage, nhưng không dùng dashboard thay cho test.
8. **Khi đánh giá sự cố:** truy ngược operation → pointcut → advice contract → order ADR → test và resource boundary; ghi nhận nguyên nhân và sửa cơ chế phát hiện.

Đối với tài liệu dài hạn, nên đặt catalog/ADR cạnh code hoặc trong kho tài liệu có version, liên kết rõ commit/build và có người review. Mọi thay đổi pointcut hoặc thứ tự cần được coi như **thay đổi hành vi runtime**, kể cả khi không sửa dòng nào trong business method.

## 14. Checklist trước khi phê duyệt thay đổi AOP

- [ ] Xác định mục tiêu nghiệp vụ và mức độ bắt buộc của mỗi aspect.
- [ ] Danh mục operation và tập pointcut match được cập nhật, có ghi những trường hợp exclude.
- [ ] Cơ chế proxy/weaving và đường bypass được mô tả, kiểm tra bằng đường gọi thật.
- [ ] Hợp đồng advice ghi rõ có được short-circuit, retry, thay kết quả/lỗi hoặc sinh side effect không.
- [ ] Cách bắt đầu/kết thúc của transaction, metric, span, audit và concurrency slot được xác định.
- [ ] Thứ tự outer→inner và trình tự success/deny/error/cancel được viết rõ.
- [ ] Đã phân biệt logical operation với attempt, cache hit, method invocation và subscription.
- [ ] Có tài liệu riêng cho future/stream nếu hệ thống có ranh giới bất đồng bộ.
- [ ] Không có quyền mặc định khi thiếu annotation/policy cho thao tác nhạy cảm.
- [ ] Unit, integration, coverage regression và performance test phù hợp đã được chạy.
- [ ] ADR giải thích tại sao chọn thứ tự, hậu quả và điều kiện cần xem xét lại.
- [ ] Owner và nguồn sự thật của hợp đồng đã được xác định.

## 15. Những hiểu lầm phổ biến

**“Có pointcut là đã có tài liệu phạm vi.”** Không đủ: biểu thức khớp chỉ diễn tả điều kiện chọn, không nêu danh sách nghiệp vụ **phải được chọn** hay đường runtime có thể bỏ qua.

**“AOP loại bỏ coupling.”** AOP giảm các lời gọi lặp trong business code nhưng chuyển một phần phụ thuộc sang pointcut, cấu hình và semantics runtime. Coupling đó cần được thể hiện trong tài liệu và test.

**“Thứ tự advice là chi tiết triển khai.”** Không đúng khi nó quyết định audit có thấy deny, transaction có bao retry, metrics đếm request hay attempt, hoặc authorization xảy ra trước side effect.

**“Một bảng ghi A trước B là đủ.”** Thiếu ngữ nghĩa outside/inside, exit path, exception path và cách engine ánh xạ order. Hãy ghi chuỗi lồng nhau hoàn chỉnh cùng trace kỳ vọng.

**“Around advice chỉ quan sát.”** Nó có thể short-circuit, gọi nhiều lần, thay đổi kết quả/lỗi, quản lý tài nguyên và context. Từng khả năng cần hợp đồng rõ.

**“Unit test business đã đủ kiểm tra AOP.”** Lời gọi trực tiếp vào target có thể bỏ qua advice. Cần integration test qua entrypoint thật.

**“Thêm annotation là chắc chắn đã áp dụng.”** Annotation có thể bị quên hoặc không được engine nhìn thấy ở vị trí định nghĩa. Cần so sánh operation bắt buộc với coverage thực tế.

**“Đã bắt được lời gọi async tức đã đo được toàn thời gian.”** Method có thể trả stage/publisher trước khi công việc hoàn tất. Cần ghi rõ điểm đo theo vòng đời.

**“Audit và metrics đều là logging, lỗi sink thì bỏ qua hết.”** Yêu cầu vận hành và yêu cầu tuân thủ có thể rất khác nhau. Tài liệu phải định nghĩa cách phản ứng với lỗi sink theo từng contract.

## 16. Kết luận

Đối với AOP, **phạm vi – hợp đồng – thứ tự** là ba phần của cùng một quyết định kiến trúc. Phạm vi cho biết *đường thực thi nào được can thiệp*; hợp đồng cho biết *advice được phép làm gì và phải bảo toàn điều gì*; thứ tự cho biết *các hành vi tương tác như thế nào ở cả nhánh vào, nhánh ra, lỗi và hủy*. Tài liệu tốt cần có operation catalog, pointcut coverage, advice contract, chuỗi outer→inner, ADR giải thích lựa chọn, và test chứng minh trên đường gọi thật. Khi các phần này được quản lý cùng code và cấu hình, việc đổi tên, refactor, thêm aspect hoặc chuyển sang bất đồng bộ sẽ ít có nguy cơ làm thay đổi hành vi trong im lặng.

</details>

- [Quay lại đầu trang](#back-to-top)
