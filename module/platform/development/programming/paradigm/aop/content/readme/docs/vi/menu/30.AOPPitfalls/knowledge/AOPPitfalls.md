<a id="back-to-top"></a>

# Sai lầm thường gặp

## Menu
- [Dùng aspect cho mọi đoạn mã trùng mà không xét ý nghĩa](#aop-pitfalls-001)
- [Pointcut quá rộng hoặc phụ thuộc chi tiết dễ thay đổi](#aop-pitfalls-002)
- [Advice làm thay đổi kết quả hoặc nuốt lỗi ngoài dự kiến](#aop-pitfalls-003)
- [Gọi proceed sai số lần hoặc sai thời điểm](#aop-pitfalls-004)
- [Lưu trạng thái yêu cầu trong aspect dùng chung](#aop-pitfalls-005)
- [Không xác định thứ tự cho các aspect có phụ thuộc](#aop-pitfalls-006)
- [Bổ sung quá nhiều hành vi ẩn khiến chương trình khó hiểu](#aop-pitfalls-007)

## <a id="aop-pitfalls-001">Dùng aspect cho mọi đoạn mã trùng mà không xét ý nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: Dùng aspect cho mọi đoạn mã trùng mà không xét ý nghĩa

Khi học lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một ý tưởng dễ gây hiểu nhầm là: “Đoạn mã xuất hiện ở nhiều nơi thì đưa ngay vào aspect để tránh lặp”. Sự lặp lại về **cú pháp** không có nghĩa là nhiều đoạn mã cùng thực hiện **một trách nhiệm**. AOP thích hợp khi một hành vi có ý nghĩa rõ ràng cần được áp dụng **xuyên suốt nhiều join point** theo quy tắc ổn định. AOP không phải cơ chế thay thế mặc định cho mọi hàm chung, lớp nghiệp vụ, chính sách miền hay phép trừu tượng hóa thông thường.

Ví dụ, hai phương thức đều có điều kiện “giá trị ít nhất 10”, nhưng một nơi là ngưỡng phân luồng vận chuyển theo khối lượng, nơi khác là ngưỡng hạng khách hàng theo số đơn hàng. Chúng trông giống nhau nhưng khác đơn vị đo, chủ thể quyết định, người sở hữu quy tắc, thông báo lỗi và lịch thay đổi. Đưa điều kiện đó thành một advice “áp dụng mọi nơi” sẽ trộn lẫn hai chính sách độc lập, khiến sửa quy tắc vận chuyển có thể vô tình đổi chính sách khách hàng.

Mục tiêu của bài là nhận diện **khi nào sự trùng lặp phản ánh một mối quan tâm xuyên suốt thực sự**, khi nào nó phản ánh logic miền riêng, và cách lựa chọn ranh giới aspect sao cho chương trình vẫn dễ hiểu, kiểm thử và thay đổi.

## 1. Phân biệt “mã giống nhau” và “một mối quan tâm xuyên suốt”

Một **mối quan tâm (concern)** là trách nhiệm hoặc lý do thay đổi của một phần chương trình. Hai đoạn mã có thể giống nhau ở hiện tại nhưng có **lý do thay đổi khác nhau**. Ngược lại, cùng một concern như ghi nhận thời gian thực thi có thể được cài đặt lặp lại ở nhiều phương thức khác nhau, đôi khi không giống từng ký tự.

Một **cross-cutting concern** thường cần hiện diện tại nhiều vị trí trong những lớp chức năng khác nhau, chẳng hạn đo độ trễ, thu thập dấu vết thực thi hoặc kiểm toán một loại thao tác đã được định nghĩa rõ. Trong AOP, logic này có thể được đóng gói bằng advice; pointcut chọn nơi áp dụng; weaving hoặc interception đưa advice vào quá trình chạy. Cơ chế AOP cụ thể quyết định loại join point có sẵn và giới hạn của advice.

Cần phân biệt ba câu hỏi:

1. **Trùng cú pháp?** Hai đoạn cùng dùng if, try/finally, phép so sánh, kiểm tra null hoặc cùng gọi một API.
2. **Trùng ý nghĩa và lý do thay đổi?** Cả hai có cùng chính sách, cùng chủ sở hữu, cùng tiêu chí đúng/sai và sẽ thay đổi đồng thời khi yêu cầu đổi không?
3. **Cần tác động xuyên suốt ở join point?** Hành vi đó có thể xác định bằng một hợp đồng điểm áp dụng rõ ràng, không phải bằng tên hàm hoặc biểu thức quá rộng, và có thể chèn mà không bóp méo logic nghiệp vụ không?

Câu trả lời “có” cho câu đầu tiên **không kéo theo** hai câu sau. Đôi khi chia sẻ một **hàm, giá trị miền, service hoặc thư viện** là hợp lý; đôi khi nên giữ hai đoạn mã riêng vì chúng chỉ giống nhau tình cờ. Aspect nên được chọn từ ý nghĩa và hợp đồng hành vi, không từ số lần một cấu trúc cú pháp lặp lại.

## 2. Ba dạng lặp cần xử lý khác nhau

| Dạng lặp | Ví dụ | Cách xử lý thường phù hợp | Vì sao |
| --- | --- | --- | --- |
| Giống cú pháp, khác chính sách | “>= 10” là kilogram trong giao vận, nhưng là số lần mua trong chương trình thưởng | Giữ chính sách trong đúng miền, dùng tên/kiểu dữ liệu rõ nghĩa | Hai quy tắc có thể thay đổi độc lập |
| Cùng logic miền thật sự | Nhiều luồng đều tính phí theo một bảng giá do cùng nhóm quản lý | Hàm miền, policy object, domain service hoặc thư viện chung | Nơi gọi cần biết công khai quy tắc nào được dùng |
| Cùng hành vi kỹ thuật xuyên suốt | Ghi metric thời gian cho mọi thao tác thuộc nhóm đã công bố | Aspect hoặc interceptor/decorator với pointcut chính xác | Hành vi bổ sung không thay nội dung quyết định nghiệp vụ |

Ngoài ra còn có **lặp vì công cụ hoặc kiến trúc**: mã chuyển đổi dữ liệu, triển khai giao diện, phần boilerplate. Tùy trường hợp, dùng helper, template method, code generation, composition hoặc một API rõ ràng. Việc loại lặp này xuất hiện nhiều nơi không tự chứng minh cần pointcut.

Một tiêu chí quan trọng là **quyền sở hữu và lý do thay đổi**. Nếu chính sách A thay đổi theo quy định giao vận còn chính sách B thay đổi theo chiến dịch khách hàng, chúng phải giữ khả năng thay đổi độc lập, dù hiện tại cùng biểu thức. Gộp chúng vào cùng aspect tạo ra coupling ẩn.

## 3. Vì sao lạm dụng aspect gây hại

### 3.1. Che khuất quy tắc nghiệp vụ

Khi phương thức tên calculateShipping chỉ nhìn thấy một lời gọi nghiệp vụ nhưng một advice tự động sửa giá trị, người đọc không thể hiểu đầy đủ quy tắc chỉ từ mã phương thức. Điều đó đặc biệt nguy hiểm với định giá, giới hạn số lượng, quyền truy cập, điều kiện hoàn tiền và các điều kiện kiểm tra đầu vào theo ngữ cảnh.

Aspect có thể phù hợp cho một chính sách bắt buộc **được định nghĩa và sở hữu tập trung**, nếu hợp đồng điểm áp dụng và semantics đã rõ. Nhưng **đưa mọi loại kiểm tra vào advice** khiến chính sách miền không còn ở vị trí cần được đánh giá trong luồng nghiệp vụ.

### 3.2. Ghép chung các lý do thay đổi độc lập

Một pointcut gom mọi phương thức có tiền tố validate, check hoặc calculate trông có vẻ tiện lợi. Tuy nhiên tên phương thức là dấu hiệu cấu trúc, chưa chắc phản ánh hợp đồng ngữ nghĩa. Đổi tên, thêm overload hoặc chuyển package có thể làm tập join point biến đổi ngoài ý muốn. Một chỉnh sửa nhỏ ở advice có thể ảnh hưởng cả nghiệp vụ không liên quan.

Mức độ rủi ro tăng theo **số join point × độ nhạy cảm ngữ nghĩa × mức khó phát hiện**. Cần giới hạn phạm vi aspect theo mục đích rõ ràng và có phép kiểm chứng điểm áp dụng.

### 3.3. Thêm hành vi không tường minh

Với advice dạng around, thứ tự thực thi, thời điểm tiếp tục lời gọi gốc, giá trị trả về, ngoại lệ và side effect đều có thể bị thay đổi bởi logic bao quanh. Cơ chế và ngôn ngữ AOP cụ thể quyết định cách tiếp tục lời gọi gốc hoạt động; không thể mặc định tất cả around advice có cùng semantics.

Một lời gọi tưởng là thuần tính toán có thể bất ngờ ghi file, mở kết nối, gọi API, retry hoặc thay tham số nếu logic này được đưa vào aspect. Cách làm đó khiến debugging khó hơn và phép kiểm thử unit vốn chỉ đọc mã nghiệp vụ không còn phản ánh đầy đủ hành vi runtime.

### 3.4. Làm pointcut quá rộng và tăng chi phí

Một biểu thức chọn “mọi phương thức” có thể bắt cả mã hạ tầng, test utilities, hàm trợ giúp và các lời gọi không cần quan sát. Tùy công cụ, advice có thể gây chi phí dispatch, đo đồng hồ, cấp phát, logging, lock hoặc I/O ở từng join point. Với tần suất cao, phần chi phí nhỏ mỗi lần có thể tích lũy đáng kể.

Nếu aspect bắt cả các lời gọi bên trong chính hệ thống logging/monitoring, có thể xuất hiện reentrancy, recursion hoặc nhân đôi sự kiện. Đây là hậu quả của việc chọn phạm vi theo hình dạng mã thay vì chủ đích.

### 3.5. Làm phụ thuộc vào cơ chế AOP bị che giấu

Một giải pháp dùng proxy chỉ quan sát những lời gọi đi qua proxy; lời gọi trực tiếp nội bộ có thể bỏ qua hành vi bổ sung. Một giải pháp weaving bytecode có tập join point và thời điểm tác động khác. Nếu thiết kế nghiệp vụ **đòi hỏi** một aspect chạy để quy tắc chính xác nhưng người viết code không biết giới hạn của cơ chế, hành vi có thể thay đổi theo đường gọi.

Với các điều kiện nghiệp vụ cốt lõi, cách biểu diễn trực tiếp bằng đối tượng miền hoặc service thường dễ chứng minh tính đúng đắn hơn. AOP cần một hợp đồng triển khai minh bạch nếu được dùng cho yêu cầu bắt buộc.

## 4. Tình huống trước–sau: cùng biểu thức, khác ý nghĩa

Giả sử một ứng dụng có hai quy tắc:

- Đơn hàng có khối lượng từ 10 kg trở lên được chuyển sang luồng FREIGHT; ít hơn đi PARCEL.
- Khách có ít nhất 10 đơn hàng hoàn tất đạt hạng GOLD; ít hơn có hạng STANDARD.

Hai điều kiện dùng toán tử và hằng số giống nhau, nhưng **kg** khác **completed orders**. Nếu tạo advice “mọi hàm có tham số số nguyên từ 10 trở lên áp dụng ưu đãi”, chúng ta đã nhầm tiêu chí kết hợp dữ liệu với tiêu chí kết hợp hành vi.

Phân tích quyết định:

| Câu hỏi | Vận chuyển | Hạng khách hàng |
| --- | --- | --- |
| Đơn vị đầu vào | Kilogram | Số đơn hoàn tất |
| Lý do thay đổi | Chính sách logistics | Chương trình khách hàng |
| Đầu ra | FREIGHT/PARCEL | GOLD/STANDARD |
| Ai sở hữu | Nhóm vận hành giao vận | Nhóm kinh doanh khách hàng |
| Có nên dùng chung một aspect điều kiện >= 10? | Không | Không |

Trong tình huống này, giữ mỗi quy tắc trong đúng đối tượng chính sách. Nếu quy tắc thực sự được dùng bởi nhiều luồng **cùng miền**, đưa nó vào một hàm miền hoặc policy được đặt tên rõ nghĩa. Không ép hai nghiệp vụ trùng cú pháp đi qua một aspect.

Ngược lại, nếu yêu cầu là **ghi một sự kiện rằng thao tác đã thành công hay lỗi** cho cả hai nhóm, đó có thể là concern xuyên suốt với hợp đồng độc lập. Advice có thể chèn chức năng ghi sự kiện mà **không quyết định** loại vận chuyển hay hạng khách hàng.

## 5. Ví dụ Java 17 chạy độc lập: quy tắc miền riêng, hành vi ghi nhận chung

Chương trình dưới đây dùng JDK dynamic proxy làm ví dụ **chặn lời gọi kiểu AOP**. Đây không phải một trình dệt bytecode tổng quát: nó chỉ minh họa interception các lời gọi đi qua interface do proxy triển khai. Logic miền vẫn nằm trong hai class riêng; phần ghi nhận kết quả lời gọi thành công/thất bại nằm ở một nơi chung.

Lưu file **MeaningBeforeAspect.java**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class MeaningBeforeAspect {
    public interface Action {
        String perform(int value);
    }

    public static final class Shipping implements Action {
        @Override
        public String perform(int kilograms) {
            if (kilograms < 0) {
                throw new IllegalArgumentException("kg must be >= 0");
            }
            return kilograms >= 10 ? "FREIGHT" : "PARCEL";
        }
    }

    public static final class Loyalty implements Action {
        @Override
        public String perform(int completedOrders) {
            if (completedOrders < 0) {
                throw new IllegalArgumentException(
                        "orders must be >= 0");
            }
            return completedOrders >= 10 ? "GOLD" : "STANDARD";
        }
    }

    public static Action withAudit(Action target, List<String> events) {
        return (Action) Proxy.newProxyInstance(
                Action.class.getClassLoader(),
                new Class<?>[] { Action.class },
                (proxy, method, arguments) -> {
                    String name = target.getClass().getSimpleName();
                    try {
                        Object result = method.invoke(target, arguments);
                        events.add(name + ":OK");
                        return result;
                    } catch (InvocationTargetException ex) {
                        events.add(name + ":ERROR");
                        throw ex.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();

        Action shipping = withAudit(new Shipping(), events);
        Action loyalty = withAudit(new Loyalty(), events);

        System.out.println("Shipping: " + shipping.perform(12));
        System.out.println("Loyalty: " + loyalty.perform(12));

        try {
            shipping.perform(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }

        System.out.println("Events: " + events);
    }
}
```

Biên dịch, chạy bằng JDK 17 trở lên:

```bash
javac --release 17 MeaningBeforeAspect.java
java MeaningBeforeAspect
```

Kết quả:

```text
Shipping: FREIGHT
Loyalty: GOLD
Rejected: kg must be >= 0
Events: [Shipping:OK, Loyalty:OK, Shipping:ERROR]
```

**Điểm cần quan sát:**

1. Shipping và Loyalty cùng dùng ngưỡng 10 nhưng không chia sẻ policy vì ngữ nghĩa độc lập. Nếu sau này ngưỡng logistics đổi thành 15 kg, kết quả hạng khách hàng vẫn giữ nguyên.
2. Phần ghi nhận được áp dụng bên ngoài mã nghiệp vụ và **không thay đổi kết quả** của lời gọi thành công; ngoại lệ gốc được mở lại từ InvocationTargetException và chuyển tới caller.
3. List sự kiện chỉ là công cụ minh họa có tính xác định. Nếu dùng cho nhiều luồng đồng thời, phải chọn phương án ghi sự kiện thread-safe, hạn chế dữ liệu nhạy cảm và xử lý được lỗi của kênh ghi nhận.
4. Advice trong sản phẩm thực tế cần quy định rõ xử lý cả unchecked error, ngoại lệ từ chính hệ thống ghi log, lời gọi bất đồng bộ và các join point đặc biệt. Đoạn proxy này **không** định nghĩa chính sách đầy đủ cho các tình huống đó.
5. Đường gọi trực tiếp đến đối tượng Shipping không đi qua proxy sẽ không tạo sự kiện. Vì thế cần tài liệu hóa điểm áp dụng và test hợp đồng interception; không suy ra mọi lệnh gọi đều được chặn.

Để tự kiểm tra quyết định thiết kế, thử thay **chỉ** ngưỡng của Shipping thành 15 và chạy lại. Khi gọi shipping.perform(12), kết quả mới là PARCEL; Loyalty vẫn trả GOLD. Sự độc lập đó là một thuộc tính có chủ đích. Nếu ngưỡng bị nhét chung trong một aspect, yêu cầu “thay vận chuyển nhưng giữ chính sách khách hàng” trở nên khó thực hiện và khó kiểm thử hơn.

## 6. Khi nào nên chọn aspect?

Một aspect hợp lý thường trả lời rõ được cả bốn câu hỏi:

**(a) Hành vi này có một mục đích và một owner không?** Ví dụ “ghi metric thời lượng cho nhóm thao tác công khai” hoặc “thêm trace context vào các lời gọi được chỉ định” có chủ thể sở hữu và quy tắc đánh giá độc lập với phần lớn nghiệp vụ.

**(b) Có thể chọn join point theo hợp đồng ổn định không?** Dùng phạm vi package/interface/annotation/điểm gọi theo công cụ hỗ trợ hoặc danh sách explicit join point khi cần; không chọn chỉ vì những phương thức đang có vài dòng giống nhau. Tên và cấu trúc có thể dùng để biểu diễn một hợp đồng **đã được xác định bằng ngữ nghĩa**, chứ không phải nguồn tạo ra hợp đồng.

**(c) Advice có giữ đúng semantics nghiệp vụ không?** Với aspect mang tính quan sát, giá trị trả về, ngoại lệ, thứ tự side effect và số lần thực thi tác vụ gốc phải giữ nguyên theo yêu cầu. Với aspect cố ý thay đổi semantics, phải có hợp đồng nghiệp vụ rõ ràng, owner và phép kiểm chứng.

**(d) Có thể kiểm thử và vận hành độc lập không?** Có test pointcut matching và số lần advice chạy, có số đo chi phí, tài liệu hóa trình tự chạy và cơ chế debugging. Nếu câu trả lời là “chỉ hoạt động vì hiện các class trùng mẫu tên”, phạm vi aspect chưa đáng tin.

Một số trường hợp sử dụng có thể phù hợp:

- Thu thập thời gian thực thi/metric ở các đường gọi quan sát được và đã định nghĩa trước, với cardinality và sampling được kiểm soát.
- Ghi dấu kiểm toán cho **một nhóm thao tác được phân loại cụ thể**, khi cấu trúc sự kiện, quyền sở hữu và yêu cầu dữ liệu đã rõ.
- Thiết lập và dọn dẹp context của lời gọi trong một ranh giới kỹ thuật có hợp đồng nhất quán.
- Kiểm tra một quy tắc kỹ thuật chung tại nhiều entry point **chỉ khi** semantics, chính sách thất bại và điểm thực thi đã được xác nhận là giống nhau.

Ngay cả trong các trường hợp này, aspect là **một lựa chọn**, bên cạnh decorator, explicit interceptor hoặc các API được gọi trực tiếp. Có thể chọn giải pháp tường minh hơn nếu số điểm cần áp dụng ít, thay đổi giữa các trường hợp lớn hoặc cần hiện rõ luồng nghiệp vụ tại vị trí gọi.

## 7. Khi nào nên giữ logic ở hàm, lớp hoặc chính sách miền?

Các tín hiệu mạnh để **không tự động chuyển thành aspect**:

- Điều kiện dùng dữ liệu miền như giá, hạn mức, số lượng, trạng thái đơn hàng, chính sách hoàn tiền hoặc eligibility.
- Hai đoạn có cùng biểu thức nhưng được quyết định bởi các nhóm nghiệp vụ khác nhau.
- Người sửa cần nhìn trực tiếp rule khi đọc phương thức hoặc khi review nghiệp vụ.
- Các đường chạy cần thông báo lỗi khác nhau, xử lý ngoại lệ khác nhau hoặc cần truyền kết quả khác nhau.
- Chỉ có một vài nơi dùng, áp dụng bằng lời gọi tường minh dễ hiểu hơn pointcut.
- Kết quả phụ thuộc thứ tự các bước hoặc transaction boundary; “giấu” logic phía sau advice khiến thứ tự khó nhận biết.
- Muốn tái sử dụng cùng thuật toán có tham số cấu hình, nhưng chẳng có join point xuyên suốt phù hợp.

Nếu logic thuộc một miền và thật sự được chia sẻ, ưu tiên một **abstraction có tên nghiệp vụ**. Ví dụ ShippingPolicy.chooseRoute(weight) được nhiều luồng vận chuyển gọi; RewardPolicy.classify(completedOrders) được các luồng khách hàng gọi. Không nên tạo một UniversalThresholdAspect so sánh giá trị số bất kỳ với 10.

Nếu cần áp dụng hành vi bổ sung cho đúng **một đối tượng** thay vì chọn pointcut xuyên suốt, composition hoặc decorator có thể minh bạch hơn. Nếu mỗi use case chỉ có vài lời gọi và việc gọi policy chính là nội dung cần đọc hiểu, lời gọi tường minh thường hiệu quả hơn “ma thuật” chặn ngầm.

## 8. Quy trình phân tích đoạn mã trùng trước khi refactor

Đừng bắt đầu từ công cụ. Hãy bắt đầu từ **bản đồ trách nhiệm**:

1. **Liệt kê các vị trí trùng:** class/phương thức, đầu vào, đầu ra, ngoại lệ, side effect, caller và tần suất chạy. Đừng lấy việc giống một vài dòng làm kết luận.
2. **Viết lại mục đích bằng một câu nghiệp vụ:** “đo độ trễ của nhóm X” khác “chọn nhóm vận chuyển” dù cả hai cùng dùng if/try/finally.
3. **Hỏi về lý do thay đổi:** nếu chỉ một rule thay đổi, các đoạn kia có cần thay không? Nếu không, chúng không chia sẻ một chính sách duy nhất.
4. **Nhận diện owner và ranh giới:** ai phê duyệt logic, tiêu chí đúng/sai, thông báo lỗi và ngưỡng hiệu năng? Nếu owner khác nhau, cẩn trọng với một abstraction chung.
5. **Xác định điểm thực thi:** liệu có join point rõ ràng và được công cụ hỗ trợ? Có lời gọi bị bỏ sót do proxy, class loader, inlining hoặc mode weaving không?
6. **Chọn phương án nhỏ nhất đáp ứng ý nghĩa:** giữ nguyên nếu lặp ngẫu nhiên; trích hàm/kiểu miền nếu chung quy tắc; decorator/interceptor nếu chỉ cần wrap một ranh giới; aspect nếu concern cần áp dụng nhất quán ở nhiều join point.
7. **Xác minh semantics trước–sau:** so kết quả, ngoại lệ, side effect, thứ tự lời gọi và phân quyền; với aspect, test pointcut đúng cả trường hợp được chọn và trường hợp bị loại.
8. **Đo chi phí và tính dễ bảo trì:** benchmark nếu advice chạy thường xuyên, kiểm tra stack trace, khả năng tìm nguồn hành vi, độ phức tạp cấu hình và mức ảnh hưởng của mỗi lần sửa.

Đặt câu hỏi phản chứng quan trọng: **“Nếu hai đoạn code này ngày mai trông khác hẳn nhau nhưng vẫn cùng trách nhiệm, tôi có muốn áp dụng cùng advice không?”** Nếu có, đó là tín hiệu concern xuyên suốt đáng cân nhắc. Và **“Nếu hai đoạn vẫn giống nhau nhưng một yêu cầu nghiệp vụ thay đổi riêng, tôi có muốn chúng tách ra không?”** Nếu có, đừng gộp chỉ vì trùng cú pháp.

## 9. Pointcut dựa trên cấu trúc chỉ an toàn khi hợp đồng ngữ nghĩa đã rõ

Trong nhiều công cụ AOP, pointcut mô tả join point bằng biểu thức dựa trên tên, kiểu, package, annotation hoặc ngữ cảnh chạy. Cú pháp và tập join point được hỗ trợ tùy công cụ. Một selector cấu trúc có thể rất tiện nhưng không tự mang nghĩa nghiệp vụ.

Ví dụ có thể tồn tại nhiều phương thức có tên bắt đầu bằng validate, nhưng:

- validatePayment kiểm tra điều kiện thanh toán theo chính sách tài chính.
- validateStock kiểm tra số tồn kho theo thời điểm.
- validateMessageFormat kiểm tra cú pháp thông điệp.
- validateTelemetryPayload xác nhận cấu trúc dữ liệu quan sát.

Những phương thức này không có cùng error policy hoặc side effect. Pointcut kiểu “mọi validate*” không làm chúng có semantics giống nhau. Việc duy nhất chúng cùng tên chỉ là **đặc điểm chữ viết**.

Nếu thật sự có nhu cầu gắn advice kiểm toán cho một nhóm điểm vào hệ thống, hãy định nghĩa một **danh sách/nhóm thao tác được phê duyệt** và tạo test cho nó. Nếu chọn annotation làm dấu, annotation cũng phải có ngữ nghĩa rõ ràng, quy tắc sử dụng, review và test; biến mọi annotation thành một nhãn mơ hồ sẽ lặp lại lỗi thiết kế dưới hình thức khác.

Đồng thời cần để ý **thay đổi phạm vi theo thời gian**: thêm method mới trùng wildcard, chuyển package, sinh code tự động hoặc đổi loader có thể làm join point tăng/giảm. Các công cụ khác nhau có thể áp dụng aspect ở execution, call, constructor hay field access khác nhau; test phải phù hợp với khả năng thực tế của công cụ đã chọn.

## 10. Kiểm thử để phát hiện aspect bị dùng sai chỗ

Kiểm thử tốt phải phát hiện cả **áp dụng thiếu** lẫn **áp dụng thừa**.

| Loại kiểm thử | Mục đích | Ví dụ khẳng định |
| --- | --- | --- |
| Unit test nghiệp vụ không advice | Chứng minh chính sách miền tự thân đúng | Shipping ở 9/10/15 kg; Loyalty ở 9/10/15 đơn |
| Contract test pointcut | Đảm bảo đúng tập join point | Advice ghi metric chạy cho thao tác đã chọn, không chạy cho helper |
| Equivalence test | Advice kỹ thuật không đổi kết quả/ngoại lệ gốc | Bật/tắt advice trả cùng kết quả và cùng loại ngoại lệ |
| Advice ordering test | Phát hiện thay đổi thứ tự/tác dụng phụ | Kiểm toán được ghi ở giai đoạn hợp đồng yêu cầu |
| Integration test | Kiểm tra đường gọi và cơ chế áp dụng thực | Đường đi qua proxy/weaver đúng; không double advice |
| Concurrency test | Kiểm tra state dùng chung và context | Không rò context, race condition hay ghi đè sự kiện |
| Performance test | Đánh giá tác động khi join point nhiều | p95/p99, throughput, CPU, GC nằm trong ngưỡng |
| Change-isolation test | Chứng minh hai quy tắc giống cú pháp vẫn tách biệt | Đổi ngưỡng vận chuyển không đổi hạng khách |

Với aspect dạng quan sát, test nên kiểm tra **không can thiệp semantics** theo hợp đồng của thao tác gốc. Nếu yêu cầu thực tế cho phép advice chủ động ngắt, retry hay thay đổi kết quả, test phải mô tả rõ hành vi đó, số lần gọi gốc, cách xử lý ngoại lệ và ảnh hưởng lên side effect; đừng dùng khẩu hiệu “advice luôn trong suốt” khi bản thân yêu cầu không như vậy.

Để test ảnh hưởng của việc pointcut mở rộng, nên có các trường hợp **negative**: helper mới có tên giống tên nghiệp vụ, utility có cùng annotation sai mục đích, hoặc phương thức không thuộc nhóm được quan sát. Đây là cách phát hiện cấu hình AOP “bắt nhầm” mà test happy path không phát hiện được.

## 11. Dấu hiệu cần cảnh giác khi review AOP

| Dấu hiệu | Rủi ro | Câu hỏi review |
| --- | --- | --- |
| “Có ba đoạn if giống nhau nên làm aspect” | Đồng nhất nhầm chính sách | Có cùng owner và lý do thay đổi không? |
| Pointcut bắt toàn bộ class/method | Chi phí và side effect lan rộng | Tập join point chủ đích đã có test chưa? |
| Advice chứa bảng giá hoặc quy tắc phân loại | Ẩn miền trong hạ tầng | Quy tắc nên thuộc policy/service nào? |
| Advice thay tham số, kết quả hoặc ngoại lệ không được công bố | Phá hợp đồng caller | Có tài liệu và test semantics không? |
| Cùng một thay đổi làm hỏng hai nghiệp vụ độc lập | Coupling theo cấu trúc mã | Có cần tách owner và chính sách không? |
| Test một phương thức trực tiếp nhưng runtime gọi qua proxy | Độ phủ test không phản ánh đường chạy | Test đã chạy trên cơ chế áp dụng thật chưa? |
| Advice tự bắt hệ thống logging/telemetry | Recursion, log trùng, nghẽn tài nguyên | Có loại trừ mã hạ tầng và test số lần gọi không? |
| Một aspect chứa nhiều trách nhiệm không liên quan | Khó hiểu, khó rollback | Có thể chia theo từng concern và hợp đồng không? |
| Cần đọc nhiều cấu hình ngầm để hiểu một rule | Giảm khả năng bảo trì | Có cách biểu diễn trực tiếp dễ theo dõi hơn không? |

Đừng đánh giá chất lượng AOP bằng số dòng code bị xóa. Một refactor “giảm lặp” nhưng làm khó debug, tăng phụ thuộc và trộn chính sách có thể khiến **tổng chi phí bảo trì tăng**.

## 12. Tiêu chí quyết định và bài tập suy nghĩ

Trước khi tạo aspect mới, tự trả lời năm câu:

1. **Ý nghĩa:** Có thể đặt một tên concern độc lập với tên phương thức và cấu trúc if/try hiện tại không?
2. **Biên:** Có thể mô tả chính xác điểm nào phải nhận advice và điểm nào không, đồng thời kiểm thử hai tập đó không?
3. **Hợp đồng:** Giá trị trả về, ngoại lệ, thứ tự và side effect nào phải được giữ nguyên? Phần nào được chủ động thay đổi?
4. **Chủ sở hữu:** Khi business rule thay đổi, ai phê duyệt và phạm vi bị ảnh hưởng là gì?
5. **Giá trị ròng:** Lợi ích của việc tập trung hành vi có lớn hơn chi phí cấu hình ngầm, truy vết, testing và tài nguyên hay không?

Áp dụng cho các tình huống sau:

- **A:** Năm phương thức chuyển định dạng ngày cùng dùng quy tắc timezone do một thư viện chung quản lý. **Gợi ý:** ưu tiên utility/API chuyển đổi thống nhất nếu caller cần kết quả chuyển đổi; không nhất thiết cần aspect.
- **B:** Mười loại thao tác thuộc danh mục đã phê duyệt đều cần một bản ghi thời gian hoàn thành với cấu trúc giống nhau. **Gợi ý:** có thể dùng aspect nếu điểm áp dụng, trạng thái lỗi và cơ chế logging rõ ràng; kiểm thử không bắt nhầm thao tác ngoài danh mục.
- **C:** Hai nghiệp vụ dùng chung “nếu số lượng >= 10 thì đổi kết quả”, nhưng đơn vị đo và owner khác nhau. **Gợi ý:** giữ policy riêng, không gộp bằng điều kiện cú pháp.
- **D:** Một advice đang che giấu bước kiểm tra hạn mức riêng của từng loại giao dịch. **Gợi ý:** đưa rule về policy miền có tên; nếu tồn tại một điều kiện bảo vệ kỹ thuật chung thực sự, định nghĩa nó như concern độc lập và kiểm thử đầy đủ.

Quyết định cuối cùng phụ thuộc vào **ý nghĩa của hành vi và ranh giới thay đổi**, không phụ thuộc đơn thuần vào số dòng mã trùng. AOP đạt giá trị khi một concern xuyên suốt được đặt đúng chỗ, với pointcut có chủ đích và hợp đồng được kiểm chứng. Việc cố đưa mọi đoạn mã trùng vào aspect thường chỉ chuyển sự lặp nhìn thấy được thành phụ thuộc và hành vi ngầm khó phát hiện hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-002">Pointcut quá rộng hoặc phụ thuộc chi tiết dễ thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut quá rộng hoặc phụ thuộc chi tiết dễ thay đổi

## 1. Vấn đề cốt lõi

Trong **Aspect-Oriented Programming (AOP)**, *pointcut* xác định **join point nào** được lựa chọn để áp dụng *advice*. Một pointcut có thể được viết rất ngắn nhưng ảnh hưởng tới hàng trăm phương thức. Khi lựa chọn sai phạm vi, AOP có thể trở thành nguồn lỗi kín đáo: hành vi bổ sung xuất hiện ngoài dự kiến, bỏ sót điểm cần xử lý, chi phí tăng lên hoặc kết quả thay đổi sau một lần đổi tên package tưởng như vô hại.

Có hai dạng sai lầm cần phân biệt:

1. **Quá rộng (overmatching):** pointcut khớp nhiều join point hơn ý định. Ví dụ, chỉ cần audit thao tác đặt hàng nhưng lại áp dụng ghi log cho mọi phương thức trong package nghiệp vụ, kể cả getter, kiểm tra nội bộ và thao tác nhạy cảm.
2. **Phụ thuộc chi tiết dễ thay đổi (brittle matching):** pointcut xác định “đúng” hành vi bằng tên lớp, tên phương thức, vị trí file/package, kiểu tham số hoặc cách tổ chức code vốn không phải một phần của hợp đồng nghiệp vụ. Khi refactor, advice âm thầm biến mất hoặc áp dụng sang phương thức khác.

Cũng có lỗi đối xứng là **khớp quá hẹp (undermatching)**: pointcut chỉ nhận một số join point đáng lẽ phải được bảo vệ hoặc quan sát. Một pointcut tốt cần đồng thời có **độ bao phủ đúng** và **độ chọn lọc đúng**, không chỉ “có chạy” ở ví dụ đầu tiên.

Bài này bàn về **AOP nói chung**, minh họa cú pháp pointcut theo phong cách AspectJ khi cần thiết. Phạm vi join point hỗ trợ phụ thuộc công cụ weaving/interception cụ thể; không được giả định mọi hệ AOP đều cung cấp cùng bộ designator hoặc cùng cách áp dụng lời gọi nội bộ.

## 2. Khái niệm cần giữ tách biệt

- **Join point:** điểm trong quá trình thực thi mà mô hình AOP cho phép can thiệp; một số hệ thống hỗ trợ method execution, method call, field access, constructor, handler... tùy cơ chế.
- **Pointcut:** điều kiện chọn một tập join point. Có thể gồm phần xác định tĩnh (kiểu, chữ ký, annotation) và điều kiện phụ thuộc ngữ cảnh runtime.
- **Advice:** logic bổ sung trước, sau hoặc bao quanh join point đã chọn.
- **Weaving/interception:** cách kết nối advice tới join point, chẳng hạn biến đổi bytecode khi build/nạp lớp hoặc chuyển lời gọi qua đối tượng trung gian.
- **Shadow:** trong một số mô hình weaving, vị trí mã có thể trở thành join point khi chạy; việc một shadow được chọn hay không có thể được quyết định một phần ở build time.

**Pointcut là hợp đồng chọn hành vi.** Hợp đồng ấy cần nói được “vì sao một join point thuộc phạm vi”, có ví dụ phản chứng và được kiểm thử khi codebase thay đổi.

## 3. Vì sao pointcut quá rộng thường nguy hiểm?

### 3.1. Ví dụ đối chiếu

Giả sử chỉ muốn đo thời gian các thao tác nghiệp vụ cập nhật đơn hàng. Một biểu thức minh họa theo cú pháp AspectJ:

```java
// Quá rộng: mọi method execution trong package demo và các package con.
pointcut almostEverything():
    execution(* demo..*.*(..));
```

Đây là **mảnh cú pháp bên trong một aspect**, không phải chương trình Java độc lập. `execution` chọn method execution join point; `*` đại diện mẫu kiểu/tên phù hợp; `(..)` khớp danh sách tham số tùy ý; `demo..*` là mẫu kiểu trong package `demo` và các mức package con phù hợp. Cách khớp cụ thể phải được xác minh bằng công cụ AOP đang sử dụng.

Nếu package chứa `OrderService.place`, `OrderService.status`, `Inventory.read` và cả các helper, advice có thể chạy rất nhiều lần ngoài dự tính. Nếu pointcut được dùng để audit dữ liệu, một getter từng là “vô hại” cũng có thể làm lộ thông tin trong log hoặc tạo chi phí không cần thiết.

Một lựa chọn chọn lọc hơn:

```java
// Hẹp theo chữ ký và ý định hiện tại:
pointcut placeOrder():
    execution(public void demo.order.OrderService.place(java.lang.String));
```

Biểu thức này nhắm một thao tác cụ thể, nhưng đánh đổi bằng **phụ thuộc mạnh vào tên lớp và chữ ký**. Thay đổi tên phương thức, kiểu tham số hay chuyển nơi cài đặt có thể khiến pointcut không còn khớp. Nên coi đây là giải pháp hợp lý khi chữ ký ổn định và có test bảo vệ, không phải lựa chọn mặc định cho mọi concern.

### 3.2. Hệ quả về tính đúng

Một pointcut quá rộng có thể:

- Ghi dữ liệu riêng tư hoặc nhạy cảm ngoài chính sách vì advice thu thập args/result trên các phương thức không định quan sát.
- Thực thi kiểm tra quyền hay logic giao dịch ở những ngữ cảnh không thích hợp, khiến chương trình lỗi bất ngờ.
- Chạy advice nhiều lần trong một chuỗi gọi lồng nhau; cùng request có thể bị đo/ghi nhiều event thay vì một event nghiệp vụ.
- Kéo vào phương thức gọi đệ quy hoặc các thao tác kỹ thuật, gây tăng độ sâu call stack, logging storm hoặc tái nhập (*re-entrancy*).
- Thay đổi thứ tự side effect, exception hoặc kết quả khi dùng around advice xử lý sai `proceed`.
- Khi một developer thêm phương thức mới dưới package đã bị chặn, advice cũng áp dụng cho phương thức đó mà không qua quyết định thiết kế rõ ràng.

Tất cả vấn đề trên **có thể tồn tại dù unit test nghiệp vụ thuần vẫn pass**, vì sai lệch chỉ xuất hiện khi cơ chế weaving/interception được kích hoạt trên artifact thực sự chạy.

### 3.3. Hệ quả về hiệu năng

Dù advice rất nhẹ, số lần thực thi mới là yếu tố quyết định:

```text
Chi phí tương đối ≈ số join point được chặn ×
                    chi phí dispatch/điều kiện/advice mỗi lần
                    + tác động phụ lên allocation, I/O, JIT và GC
```

Đây là mô hình để suy nghĩ, không phải công thức benchmark chính xác. Một lời gọi ghi log trong đường nóng chạy hàng triệu lần có thể tốn nhiều hơn việc intercept một API biên chỉ chạy vài trăm lần. Pointcut mở rộng còn có thể tăng khối lượng tìm khớp/weaving, số class bị biến đổi, bộ nhớ metadata hoặc thời gian nạp lớp; mức độ tùy công cụ.

**Nguyên tắc:** trước khi tối ưu một handler vài nanosecond, hãy kiểm tra có đang intercept quá nhiều join point hay không.

## 4. Vì sao pointcut theo tên và cấu trúc dễ hỏng?

Xem một số kiểu phụ thuộc:

| Dạng ghép pointcut | Thay đổi khiến nó lệch | Triệu chứng |
|---|---|---|
| `execution(* *..*Service.*(..))` | Đổi hậu tố tên lớp, tách interface/implementation | Advice không còn chạy dù ý định nghiệp vụ không đổi |
| `within(com.acme.oldpackage..*)` | Chuyển lớp sang package mới | Toàn bộ nhánh hành vi mất advice |
| `execution(* *.save*(..))` | Đổi tên `save` thành `persist`, thêm `saveDraft` | Vừa có thể bỏ sót vừa có thể khớp nhầm |
| `execution(* *(java.lang.String))` | Đổi DTO, overload, boxing/thay tham số | Hành vi áp dụng theo hình dạng chữ ký thay vì ý nghĩa |
| `call(* SomeType.*(..))` | Chuyển logic gọi, thêm wrapper/adapter | Điểm gọi thay đổi dù method target không đổi |
| Annotation mang ý nghĩa nghiệp vụ | Quên gắn/bỏ nhầm marker; annotation bị dùng sai | Pointcut cũng có thể sai dù không phụ thuộc tên |

Các ví dụ cú pháp trong bảng chỉ để thể hiện **kiểu coupling**; một số mẫu viết ngắn cần được đặt vào biểu thức pointcut đầy đủ và kiểm tra với cú pháp của weaver được chọn.

Mức độ mong manh phụ thuộc **tính ổn định của dấu hiệu**. Tên package thường phản ánh bố cục kỹ thuật, trong khi một marker có chủ đích như `@AuditedOperation` có thể diễn đạt rõ hợp đồng. Nhưng marker cũng cần người sở hữu, quy trình review, chính sách kế thừa và test để tránh bị gắn nhầm.

### 4.1. Một lần refactor gây mất advice âm thầm

Trước refactor:

```text
demo.order.OrderService.place(String)
  └─ pointcut theo đúng tên/chữ ký → có audit
```

Sau refactor:

```text
demo.order.CheckoutWorkflow.submit(OrderCommand)
  └─ giữ nguyên ý nghĩa "xác nhận đặt hàng"
  └─ pointcut cũ theo OrderService.place(String) → không còn khớp
```

Nếu compiler không được cấu hình cảnh báo cho pointcut bắt buộc không khớp, build có thể vẫn thành công. Chỉ một kiểm thử có cả **luồng nghiệp vụ** và **kỳ vọng advice** mới dễ phát hiện vấn đề.

### 4.2. Sai lầm khi dựa vào package name

Package được đổi để sắp xếp thư mục, chia module, hoặc cô lập thành phần dùng chung. Một pointcut tên package có thể:

- Mất hiệu lực khi package đổi tên.
- Vô tình bao phủ class được chuyển vào package phù hợp.
- Tạo phạm vi khác nhau theo phiên bản artifact, class loader hoặc cấu hình weaving.
- Khi dùng thư viện bên ngoài, tên package thay đổi ngoài quyền kiểm soát của đội ứng dụng.

Package vẫn hữu ích làm **vòng giới hạn kỹ thuật bên ngoài** (scope), đặc biệt để tránh weave code của JDK, thư viện hoặc chính infrastructure aspect. Nhưng riêng package thường không đủ diễn đạt một quy tắc nghiệp vụ.

## 5. Các nguyên tắc thiết kế pointcut bền vững

### 5.1. Bắt đầu từ hợp đồng nghiệp vụ

Hãy viết rõ bằng lời:

> “Audit các **thao tác xác nhận đơn hàng được công bố như API nghiệp vụ**; không audit truy vấn trạng thái, helper hay chức năng quản trị.”

Sau đó mới chọn cách biểu đạt: marker annotation, interface/hợp đồng, type hierarchy, chữ ký ổn định hoặc tổ hợp điều kiện. Nếu không thể chỉ rõ tập join point mong đợi bằng lời và test, pointcut có nguy cơ được viết theo cấu trúc code tiện tay.

### 5.2. Dùng ranh giới có chủ đích

Những cách định nghĩa phạm vi thường có ích:

1. **Marker mang ngữ nghĩa:** annotate đúng phương thức/type cần concern, ví dụ `@AuditedOperation`, để việc review dễ xác định ý định. Chính sách gắn marker và nơi marker được đọc cần rõ.
2. **Interface hoặc type ổn định:** áp dụng cho các implementation của một capability có hợp đồng ổn định. Chú ý khác biệt giữa execution method và proxy interface interception.
3. **Named pointcut:** gom biểu thức phức tạp vào pointcut có tên rõ nghĩa; nhiều advice dùng cùng một định nghĩa để tránh lệch logic.
4. **Giới hạn package/type:** dùng thêm điều kiện tránh quét toàn bộ ứng dụng hay dependency; không lấy wildcard rộng làm toàn bộ hợp đồng.
5. **Explicit exclusion:** loại trừ helper/diagnostics hoặc các lớp mà advice tự sử dụng, khi cần để tránh đệ quy hoặc tác động ngoài ý muốn.

Điểm cuối không có nghĩa cứ thêm `!within(...)` là chữa được gốc vấn đề. Exclusion liên tục phình to là tín hiệu pointcut gốc quá rộng hoặc boundary không được định nghĩa rõ.

### 5.3. Ưu tiên tính đọc được

So sánh hai ý tưởng:

```java
// Khó hiểu ý đồ, nhiều wildcard:
execution(* *(..)) && !within(demo.debug..*)
                  && !within(demo.generated..*)
                  && !within(demo.internal..*)
```

```java
// Ví dụ mảnh biểu thức mang ý định rõ hơn:
execution(public * demo.orders.api..*(..))
    && @annotation(demo.audit.AuditedOperation)
```

Đoạn thứ hai vẫn cần kiểm tra chính xác kiểu method được match và semantics của `@annotation` ở framework/weaver đang dùng. Điểm mấu chốt là **phạm vi API + dấu hiệu nghiệp vụ có chủ đích**, chứ không phải mọi tên phương thức trông giống nghiệp vụ.

### 5.4. Điều kiện tĩnh và động

Một số bộ pointcut có thể lọc tại weave time theo signature/type/annotation, còn điều kiện theo argument hoặc control flow phải được xét tại runtime. Ví dụ `args(...)` có thể ràng buộc kiểu/giá trị ngữ cảnh, `if()` có thể đặt điều kiện động, `cflow(...)` chọn ngữ cảnh luồng gọi; cú pháp và chi phí phụ thuộc công cụ.

Không đưa mọi logic lọc vào điều kiện động của advice nếu có thể xác định tĩnh ranh giới đáng tin. Tuy nhiên, một điều kiện phụ thuộc **dữ liệu request** có thể bắt buộc được đánh giá runtime; tránh kết luận rằng mọi điều kiện động đều nên loại bỏ.

## 6. Ví dụ Java 17+ có thể chạy: pointcut chính xác và test phủ định

Đây là ví dụ **weaving bằng AspectJ độc lập**, sử dụng Java 17. Nó thể hiện rằng lời gọi nghiệp vụ được audit còn phương thức truy vấn trạng thái không bị ảnh hưởng. Cần công cụ `ajc` và runtime thư viện AspectJ cùng phiên bản. Cú pháp `.aj` được biên dịch bởi AspectJ compiler, **không phải bởi `javac` thuần**.

### 6.1. Tạo mã nguồn

Cấu trúc:

```text
pointcut-example/
└── src/
    └── demo/
        └── orders/
            ├── OrderService.java
            ├── Main.java
            └── OrderAudit.aj
```

`src/demo/orders/OrderService.java`:

```java
package demo.orders;

public final class OrderService {
    public void place(String id) {
        System.out.println("PLACED: " + id);
    }

    public String status() {
        return "READY";
    }
}
```

`src/demo/orders/Main.java`:

```java
package demo.orders;

public final class Main {
    public static void main(String[] args) {
        OrderService service = new OrderService();
        service.place("A-17");
        System.out.println("STATUS: " + service.status());
    }
}
```

`src/demo/orders/OrderAudit.aj`:

```java
package demo.orders;

public aspect OrderAudit {
    pointcut confirmedOrder():
        execution(public void demo.orders.OrderService.place(java.lang.String));

    before(): confirmedOrder() {
        System.out.println("AUDIT: placing order");
    }
}
```

`confirmedOrder` là named pointcut, tách định nghĩa phạm vi khỏi advice. `execution` nhắm vào việc **thực thi** `place(String)`, vì vậy lời gọi `status()` không thuộc phạm vi. Ví dụ không đòi hỏi thay đổi method Java để gọi aspect một cách thủ công.

### 6.2. Biên dịch, weave và thực thi

Giả sử chạy từ thư mục `pointcut-example`, có JDK 17 và đã lấy hai JAR cùng phiên bản, ví dụ AspectJ 1.9.24:

```text
tools/aspectjtools-1.9.24.jar
tools/aspectjrt-1.9.24.jar
```

Chạy trên POSIX shell (Linux/macOS); dấu `:` trong classpath đổi thành `;` trên Windows:

```bash
mkdir -p build/woven

java -cp tools/aspectjtools-1.9.24.jar \
  org.aspectj.tools.ajc.Main \
  -17 -encoding UTF-8 \
  -classpath tools/aspectjrt-1.9.24.jar \
  -showWeaveInfo \
  -d build/woven \
  src/demo/orders/OrderService.java \
  src/demo/orders/Main.java \
  src/demo/orders/OrderAudit.aj

java -cp "build/woven:tools/aspectjrt-1.9.24.jar" \
  demo.orders.Main
```

Các dòng stdout của chương trình (không tính thông báo compiler/weaver in khi build):

```text
AUDIT: placing order
PLACED: A-17
STATUS: READY
```

Điều có thể quan sát:

- Advice xuất hiện **một lần** trước `place`.
- Business method vẫn tạo kết quả `PLACED: A-17`.
- `status()` trả `READY` và **không có thêm dòng audit**.
- Biểu thức pointcut hẹp đã chọn đúng **một kiểu execution join point** được minh họa.

Việc test output không chứng minh mọi biến thể của chương trình đều đúng; nó là kiểm thử tối thiểu cho một cặp **positive/negative**. Cần kiểm tra trực tiếp weave report và chạy bài test với chính artifact đóng gói sẽ triển khai.

### 6.3. Thay bằng pointcut quá rộng thì điều gì đổi?

Chỉ để minh họa hậu quả, nếu đổi nội dung `confirmedOrder()` thành:

```java
pointcut confirmedOrder():
    execution(* demo.orders..*.*(..));
```

Thì pointcut nhắm gần như toàn bộ method execution của các type trong package này, bao gồm `status` và `main`, tùy weaving scope/cơ chế. Thứ tự/số dòng audit sẽ không còn tương đương output của phiên bản hẹp. Nếu sau này thêm `OrderService.healthCheck`, pointcut rộng cũng có thể áp dụng cho nó mà không cần sửa aspect. **Đây là một thay đổi hợp đồng quan sát đáng kể**, không phải chỉ đổi cách viết regex.

Không nên dựa vào một output cố định cho biến thể quá rộng vì woven join point còn phụ thuộc các method/constructor/compiler-generated artifact và cấu hình; nên quan sát weave report thực tế của phiên bản đang chạy.

### 6.4. Kiểm thử positive và negative một cách cơ học

Với phiên bản pointcut hẹp, có thể tạo một test shell tối giản:

```bash
actual=$(java -cp "build/woven:tools/aspectjrt-1.9.24.jar" \
  demo.orders.Main)
expected=$(printf 'AUDIT: placing order\nPLACED: A-17\nSTATUS: READY')
test "$actual" = "$expected"
```

Nếu advice mất hiệu lực sau refactor, dòng `AUDIT` biến mất và test fail. Nếu advice khớp thừa `status()` hoặc `Main.main`, dòng audit bổ sung cũng làm test fail. Tại CI, nên thực hiện clean output và dùng assertion rõ ràng trên side effect thay vì chỉ kiểm tra stdout nếu chương trình thực có concurrency/async logging.

## 7. Annotation như một hợp đồng ngữ nghĩa

Khi nhiều phương thức mang cùng một ý nghĩa xuyên suốt, dùng một marker nhất quán có thể giảm coupling vào tên lớp, package và phương thức. Ví dụ sau là **minh họa cách thiết kế**; cần đặt nó trong cấu hình AspectJ hoàn chỉnh và kiểm thử lại nếu tích hợp thực.

Khai báo `demo.audit.AuditedOperation`:

```java
package demo.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditedOperation {
}
```

Gắn marker có chủ đích vào phương thức cập nhật nghiệp vụ:

```java
@AuditedOperation
public void place(String id) {
    // Business code.
}
```

Trong aspect kiểu AspectJ:

```java
pointcut auditedOperation():
    execution(public * demo.orders..*.*(..))
    && @annotation(demo.audit.AuditedOperation);
```

Ý nghĩa: giới hạn phạm vi tới public method execution trong khu vực nghiệp vụ đã xác định **và** có annotation audit. Khi đổi `place` thành `submit` hoặc đổi kiểu tham số, marker vẫn thể hiện ý định cần audit nếu được giữ đúng tại phương thức mới.

**Không phải annotation là thuốc chữa mọi tình huống:**

- Nếu developer quên gắn marker cho phương thức mới, advice vẫn bị bỏ sót.
- Nếu marker được copy sang helper hoặc getter khi refactor, advice có thể khớp thừa.
- Nếu marker đặt trên interface method nhưng weaving kiểm tra method implementation, việc match có thể khác với kỳ vọng. Cần test cụ thể; không mặc định method annotation tự động được kế thừa.
- `@Inherited` trong Java áp dụng cho annotation trên class, không khiến method annotation tự động “thừa kế” theo overriding.
- Retention policy, module visibility và công cụ weaving có thể ảnh hưởng cách kiểm tra annotation. Không mặc định annotation có retention `SOURCE` luôn hiện diện cho mọi trường hợp binary/runtime weaving.

Phương án bền vững là **đặt marker ở ranh giới chính sách được review**, test các triển khai quan trọng, và dùng static analysis/conventions để phát hiện quên marker khi cần.

## 8. Các cặp lựa chọn pointcut dễ nhầm

### 8.1. `call` khác `execution`

```java
// Hai loại join point khác nhau, chỉ dùng nếu weaver hỗ trợ:
call(* demo.orders.OrderService.place(..))
execution(* demo.orders.OrderService.place(..))
```

- `call` chọn điểm **gọi** method từ caller (nếu loại join point đó được hỗ trợ và caller nằm trong phạm vi weaving).
- `execution` chọn **thực thi** thân method target (nếu target được weave).
- Một call site mới, wrapper hoặc caller không được weave có thể ảnh hưởng `call` khác với `execution`.
- Nếu AOP dựa trên proxy, tập join point thực tế có thể chỉ gồm lời gọi đi qua proxy. Không chuyển kết luận của bytecode weaving sang proxy một cách máy móc.

Nếu mục tiêu là “mọi lần chạy thao tác đặt hàng”, execution-level thường bám sát ý định hơn; nếu cần “mọi lời gọi đi ra từ tầng X sang tầng Y”, call-level có thể phản ánh boundary khác. Phải chọn theo **sự kiện cần theo dõi**, không chỉ cú pháp dễ viết.

### 8.2. `within` khác `withincode`

- `within(TypePattern)` giới hạn join point thuộc vùng kiểu/class phù hợp.
- `withincode(SignaturePattern)` giới hạn join point theo lexical code nơi chúng xuất hiện, tùy loại join point.
- Một điều kiện lexical có thể thay đổi khi chuyển câu lệnh sang helper, cho dù nghiệp vụ vẫn như cũ.

Dùng điều kiện lexical khi chính **vị trí code** là một phần của hợp đồng, không thay cho marker nghiệp vụ có ngữ nghĩa ổn định.

### 8.3. `this` và `target`

Trong mô hình pointcut hỗ trợ hai designator này, `this` nói về đối tượng thực thi join point theo ngữ cảnh, còn `target` nói về đối tượng đích của join point; giá trị cụ thể và ý nghĩa đối với các loại join point phải đọc theo ngữ nghĩa của công cụ. Với proxy-based interception, proxy object và target có thể khác nhau. Dùng nhầm có thể khiến pointcut chỉ khớp một implementation hoặc sai kiểu runtime.

Không thể khái quát “`this` luôn là caller” hay “`target` luôn là proxy” cho mọi kiểu join point.

### 8.4. `args` khác kiểm tra kiểu trong chữ ký

Một signature pattern có thể chọn theo kiểu tham số được **khai báo**; `args(...)` thường liên quan đến đối tượng/tham số có mặt tại runtime và có thể binding giá trị. Overload, subtype hoặc đối tượng `null` có thể dẫn tới hành vi match khác với trực giác. Khi dùng các điều kiện đó để kiểm soát an toàn hoặc giao dịch, hãy đưa các kiểu cạnh biên vào test.

## 9. Rủi ro cụ thể khi pointcut quá rộng trong advice quan trọng

### 9.1. Logging, audit và observability

Một advice log mọi tham số trên `execution(* *(..))` có thể vô tình đọc password, token, dữ liệu cá nhân hoặc payload lớn. Vấn đề không chỉ là throughput mà còn là **phạm vi dữ liệu được phép quan sát**. Thực hiện allowlist join point và field, loại bỏ hoặc che dữ liệu nhạy cảm, kiểm tra output trên cả success và exception. Khi sửa tên hoặc di chuyển lớp, phải kiểm tra lại allowlist và exclusion.

### 9.2. Authorization

Nếu quyền truy cập phụ thuộc một pointcut theo tên phương thức như `*Admin*`, việc refactor tên có thể làm biến mất việc kiểm tra dù endpoint/hành vi vẫn nhạy cảm. Chính sách an toàn không nên chỉ trông chờ vào một mẫu tên không được test. Cần kiểm thử trường hợp bị từ chối và được cho phép, đồng thời bảo đảm mọi entry point được bảo vệ. Đừng coi một advice chạy ở “ít nhất một test” là chứng cứ mọi đường vào đều an toàn.

### 9.3. Transaction và resource cleanup

Advice quản lý transaction hoặc resource có thể phá vỡ ranh giới vòng đời nếu match helper và method con, tạo transaction lồng nhau hay đóng resource không đúng thời điểm. Với around advice, việc gọi `proceed` sai số lần hoặc nuốt ngoại lệ có thể làm thay đổi ý nghĩa nghiệp vụ. Xác định rõ boundary mà advice phải sở hữu và kiểm tra nested calls, exception path và retry.

### 9.4. Metric/tracing

Một yêu cầu nghiệp vụ gọi nhiều method nhỏ. Nếu pointcut quá rộng, hệ thống tạo vô số span hoặc metric label, làm tăng chi phí, độ trễ và cardinality. Pointcut theo naming convention rộng còn mở rộng theo code mới mà không có kiểm soát. Ưu tiên điểm có ý nghĩa quan sát, tách trace cấp giao dịch khỏi metric chi tiết nếu cần.

## 10. Quản lý pointcut khi refactor

Pointcut phải nằm trong phạm vi review như một **public behavioral contract** của hệ thống, dù không xuất hiện trong method signature thông thường.

### 10.1. Trước refactor

1. Liệt kê concern bị ảnh hưởng: audit, tracing, cache, kiểm tra quyền, transaction...
2. Xác định named pointcut và danh sách join point hiện được weave/intercept.
3. Chốt các **must match** (bắt buộc khớp) và **must not match** (bắt buộc không khớp).
4. Kiểm tra điểm yếu: name pattern, package, overload, visibility, annotation placement, call/execution semantics, target loader.
5. Nếu đổi method signature/di chuyển lớp, lập bảng mapping join point cũ sang join point mới theo **ý nghĩa nghiệp vụ**.

### 10.2. Trong refactor

- Khi đổi tên package hoặc phương thức, tìm pointcut có thể tham chiếu đến cấu trúc cũ; IDE rename không nhất thiết cập nhật mọi chuỗi pattern.
- Nếu aspect chứa wildcard, chạy lại báo cáo weaving: wildcard có thể match thêm code vừa được chuyển vào scope.
- Khi tách method nghiệp vụ thành nhiều helper, quyết định advice thuộc boundary ngoài hay toàn bộ helper; đừng để cơ chế match tự quyết.
- Nếu dùng marker, kiểm tra marker được giữ ở **method thực thi đúng ý định**, không chỉ copy trên interface.
- Nếu thay đổi call path hoặc chuyển từ lời gọi trực tiếp sang proxy/adapter, kiểm tra lại loại join point mà cơ chế AOP hỗ trợ.

### 10.3. Sau refactor

Chạy lại kiểm thử trên **artifact đã weave/đã cấu hình interception**. So sánh tập join point trước/sau và giải thích phần chênh lệch. Không chỉ so sánh số lượng tổng vì một join point mất và một join point thừa có thể làm tổng số không đổi.

Nếu dùng AspectJ, công cụ `ajc` có thể in thông tin weaving qua `-showWeaveInfo`. Log giúp rà soát nhưng định dạng có thể đổi giữa phiên bản; kết hợp với test hành vi. Các công cụ AOP khác có thể có cách báo join point riêng.

## 11. Kiểm thử pointcut: bảng quyết định rõ ràng

Giả sử mục tiêu là audit các thao tác đặt hàng thật sự:

| Trường hợp | Mong đợi | Lý do |
|---|---|---|
| `OrderService.place("A-17")` | **MATCH** | Thao tác cần audit |
| `OrderService.status()` | **NO MATCH** | Truy vấn chỉ đọc |
| `OrderService.place(...)` sau đổi tham số | **MATCH** nếu cùng ngữ nghĩa | Không được mất audit vì thay DTO |
| `CheckoutWorkflow.submit(...)` sau chuyển nghiệp vụ | **MATCH** khi đây là entry point mới | Boundary được thay thế |
| Helper `OrderFormatter.format(...)` | **NO MATCH** | Không phải hành vi audit |
| Method mới `OrderService.preview(...)` | **NO MATCH** nếu chỉ xem trước | Tránh wildcard khớp thêm |
| Method audit tự gọi đến logger/helper | **NO MATCH** đối với chính hạ tầng advice | Tránh recursion và log storm |
| Thao tác cập nhật qua nhiều thread/loader | **MATCH** theo hợp đồng ở môi trường đích | Không bỏ sót vì môi trường chạy |

### 11.1. Kiểm thử ba tầng

**(A) Kiểm tra khai báo:** pointcut thể hiện rõ tập join point dự kiến; tài liệu mô tả scope và exclusion. Có thể lint phát hiện mẫu đặc biệt rộng (ví dụ `execution(* *(..))`) mà không có giới hạn bổ sung.

**(B) Kiểm tra weaving/cấu hình:** xác nhận class/method được chọn đúng qua weave report hoặc introspection của hệ thống AOP. Tập join point thay đổi phải được reviewer nhìn thấy.

**(C) Kiểm tra hành vi runtime:** chạy các case dương/âm và xác minh side effect, giá trị trả về, exception, thứ tự, số lần advice, cùng phần code **không** bị chặn.

Không có tầng nào thay thế hoàn toàn hai tầng còn lại. Một biểu thức “trông đúng” vẫn có thể không được áp dụng lên lớp runtime vì class loader, weaving scope, phiên bản artifact hoặc cơ chế proxy.

### 11.2. Kiểm thử biến đổi có chủ đích (mutation test)

Để kiểm tra sức mạnh của test, thử một số sửa đổi có kiểm soát trong branch phát triển:

- Đổi tên class/method được audit nhưng giữ ý nghĩa nghiệp vụ.
- Thêm method `preview` hoặc `healthCheck` cùng package.
- Tạm bỏ marker annotation khỏi một thao tác cần audit.
- Dời method qua helper, thêm overload hoặc thay kiểu DTO.

Nếu test vẫn pass khi advice mất khỏi điểm bắt buộc, bộ test chưa đủ mạnh. Khi kiểm thử matcher, đừng tạo một “test” chỉ kiểm tra cùng một chuỗi pattern được copy từ implementation; cần kiểm tra **hành vi có ý nghĩa**.

## 12. Hiệu năng, khả năng bảo trì và lựa chọn chiến lược

| Chiến lược | Lợi thế | Đánh đổi |
|---|---|---|
| Chữ ký tường minh | Dễ dự đoán phạm vi hiện tại, ít match thừa | Giòn khi refactor method/class; nhiều khai báo |
| Package/type boundary | Dễ tổ chức theo khu vực code | Package không nhất thiết mang nghĩa nghiệp vụ |
| Annotation marker | Biểu đạt policy rõ, ổn định trước nhiều lần rename | Cần gắn marker đúng, kiểm tra retention/placement |
| Interface/capability | Bám hợp đồng phân lớp/công bố | Có thể khác khi execution nằm ở implementation hay proxy |
| Named pointcut tổ hợp | Tái sử dụng, dễ review và mở rộng có kiểm soát | Biểu thức phức tạp, cần test cả inclusion/exclusion |
| Điều kiện runtime | Cho phép quyết định theo ngữ cảnh dữ liệu | Tăng chi phí và độ khó dự đoán; dễ có logic ẩn trong advice |

Không có lựa chọn tốt nhất cho mọi hệ thống. Độ ổn định cần được đánh giá bằng câu hỏi: **“Nếu code được refactor nhưng ý nghĩa nghiệp vụ không đổi, pointcut có còn chọn đúng tập hành vi không?”**

## 13. Các lỗi phổ biến và cách khắc phục

**Lỗi 1 — Chọn toàn bộ ứng dụng để “không bỏ sót”.** Kết quả là advice chạy ở getter/helper, tạo quá nhiều log/span và có thể chạm dữ liệu nhạy cảm. *Khắc phục:* định nghĩa positive scope và negative scope, thêm case không được khớp.

**Lỗi 2 — Tin wildcard tự diễn đạt nghiệp vụ.** `save*(..)` không có nghĩa “mọi thao tác ghi bền vững”; một method `savePreview` có thể chỉ tạo bản nháp. *Khắc phục:* xác định marker/capability hoặc chữ ký ổn định và có review.

**Lỗi 3 — Gắn pointcut vào package dễ thay đổi.** Refactor thư mục làm biến mất advice mà build vẫn thành công. *Khắc phục:* thêm test bảo vệ semantics và kiểm tra weave report trước/sau đổi package.

**Lỗi 4 — Chỉ test case được khớp.** Pointcut match quá rộng mà test vẫn pass. *Khắc phục:* kiểm thử must-not-match, đặc biệt khi thêm method/package mới.

**Lỗi 5 — Giả định annotation luôn được kế thừa.** Method marker trên interface/base class không nhất thiết được nhìn thấy ở method implementation/override trong mọi cơ chế AOP. *Khắc phục:* định nghĩa chính sách placement và kiểm thử binary/runtime concrete class.

**Lỗi 6 — Nhầm loại join point.** `call` khác `execution`; proxy interception không tương đương weaving tất cả call site. *Khắc phục:* mô tả sự kiện cần bắt và kiểm tra class loader/weaving boundary.

**Lỗi 7 — Advice tự kích hoạt lại.** Broad pointcut bao gồm helper của advice, gây recursion hay logging storm. *Khắc phục:* tách infrastructure scope, loại trừ rõ ràng, test đường gây tái nhập.

**Lỗi 8 — Giữ blacklist exclusion ngày càng dài.** Mỗi class mới sinh thêm `!within(...)` và khó hiểu. *Khắc phục:* thiết kế allowlist dựa trên ranh giới nghiệp vụ.

**Lỗi 9 — Lấy thành công compiler làm tiêu chí duy nhất.** Compiler hợp lệ không đồng nghĩa join point yêu cầu đã được weave. *Khắc phục:* kiểm tra match thực và chạy integration test trên artifact cuối.

**Lỗi 10 — Bỏ qua tải thật.** Advice được đặt lên method nóng mà chỉ test chức năng. *Khắc phục:* đo invocation count, allocation, CPU, tail latency ở đường có AOP bật; sửa scope trước khi micro-optimize.

## 14. Checklist review pointcut trước khi đưa vào production

- [ ] Pointcut có tên và mô tả **hành vi cần chọn**, không chỉ cấu trúc mã.
- [ ] Đã liệt kê ít nhất một **must match** và một **must not match** đại diện.
- [ ] Wildcard được giới hạn bằng package/type/marker khi có thể; không vô tình quét hạ tầng advice hoặc dependency.
- [ ] Có lý do rõ nếu phụ thuộc class name, method name, signature hay package; refactor tương ứng đã được test.
- [ ] Hiểu đúng loại join point (**call, execution, field, constructor...**) và giới hạn của cơ chế AOP đang dùng.
- [ ] Không giả định annotation tự kế thừa hoặc proxy chặn mọi lời gọi nội bộ.
- [ ] Đã kiểm tra trường hợp overload, visibility, exception, đường gọi lồng nhau và concurrency nếu liên quan.
- [ ] Có test bắt được lời gọi bị bỏ sót **và** bị match thừa; không chỉ test rằng “có một advice đã chạy”.
- [ ] Weave/interception thực sự được kích hoạt trên artifact và JVM sẽ triển khai.
- [ ] Đã đánh giá chi phí với invocation count thực tế và rủi ro dữ liệu nhạy cảm trong advice.
- [ ] Khi refactor, diff tập join point trước/sau được xem xét theo nghiệp vụ, kể cả nếu tổng số match không đổi.

## 15. Kết luận thực hành

Một pointcut tốt chọn **đúng hành vi theo một hợp đồng rõ ràng**. Pointcut quá rộng làm advice lan sang mã không thuộc policy; pointcut quá phụ thuộc tên và vị trí làm policy biến mất khi refactor. Cách sửa hiệu quả là xác định ranh giới nghiệp vụ trước, chọn điều kiện tĩnh/marker/capability phù hợp, kiểm thử cả **match và no-match**, và kiểm tra weaving trên artifact thực tế. Khi code được tái cấu trúc nhưng ý nghĩa không đổi, điều cần được giữ ổn định là **tập hành vi chịu advice**, không phải chuỗi ký tự của phương thức cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-003">Advice làm thay đổi kết quả hoặc nuốt lỗi ngoài dự kiến</a>

<details>
<summary>Xem chi tiết</summary>

# Advice làm thay đổi kết quả hoặc nuốt lỗi ngoài dự kiến

## 1. Vấn đề: kết quả nghiệp vụ bị thay đổi khi đi qua aspect

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **advice** là hành vi chạy tại hoặc xung quanh một **join point** đã được chọn bằng **pointcut**. Advice thường phục vụ logging, đo thời gian, kiểm tra quyền, audit, retry hoặc chuẩn hóa lỗi. Những mối quan tâm đó có thể ảnh hưởng rất lớn đến lời gọi nghiệp vụ dù mã của phương thức đích không thay đổi.

Một sai lầm khó phát hiện là advice **vô tình làm đổi giá trị trả về** hoặc **bắt lỗi rồi trả kết quả thành công giả**. Chẳng hạn:

- Phương thức đích trả mã xác nhận CONFIRMED:A-1, nhưng advice around luôn trả OK.
- Phương thức đích ném IllegalArgumentException vì thiếu đầu vào, nhưng advice catch rồi trả OK; caller tưởng thao tác thành công.
- Advice trả null trong khi caller mong đợi một kiểu nguyên thủy hoặc một giá trị bắt buộc khác null.
- Advice chuyển ngoại lệ thành một ngoại lệ chung và làm mất cause, khiến hệ thống phía ngoài không còn phân biệt được lỗi kiểm tra dữ liệu, lỗi tạm thời và lỗi nghiêm trọng.
- Advice ném lỗi trong phần ghi log hoặc cleanup, làm che mất exception gốc của target.

**Mục tiêu bài học:** nhận diện chính xác nơi hợp đồng bị thay đổi, hiểu cách advice điều khiển continuation, tái hiện lỗi bằng Java 17+, thiết kế chính sách xử lý lỗi có chủ đích, và viết kiểm thử bảo vệ cả giá trị lẫn ngoại lệ, tác dụng phụ, số lần gọi.

**Phạm vi:** các nguyên lý AOP cơ bản và nâng cao áp dụng cho nhiều mô hình proxy hoặc weaving. Ví dụ dùng JDK dynamic proxy để nhìn thấy rõ đường điều khiển; không đồng nhất nó với mọi công cụ AOP.

## 2. Hợp đồng quan sát được của một lời gọi

Khi đánh giá advice đúng hay sai, đừng chỉ hỏi “chương trình có chạy được không?”. Trước tiên phải định nghĩa **hợp đồng của join point** từ góc nhìn caller:

1. **Điều kiện đầu vào:** kiểu dữ liệu, giá trị được chấp nhận, kiểm tra quyền và trạng thái ban đầu.
2. **Giá trị trả về:** kiểu và ngữ nghĩa của kết quả, nullability, danh tính đối tượng và dữ liệu bắt buộc.
3. **Ngoại lệ:** loại lỗi được công bố, thông điệp/cause cần giữ, trường hợp nào caller phải nhìn thấy lỗi.
4. **Tác dụng phụ:** ghi dữ liệu, gửi thông báo, tiêu thụ hàng đợi, thực hiện một lần hay có thể lặp.
5. **Điều khiển luồng:** phương thức đích chạy bao nhiêu lần; advice có được bỏ qua hoặc gọi lại target không?
6. **Đặc tính phi chức năng:** tracing, thời gian, audit và yêu cầu nhất quán khi có nhiều lời gọi song song.

Một advice **trong suốt về nghiệp vụ** (business-transparent) phải bảo toàn các mục mà chính sách của nó không được phép đổi: giá trị thành công, ngoại lệ, số lần thực thi và tác dụng phụ. Advice logging điển hình nên quan sát rồi truyền tiếp kết quả hoặc lỗi. Ngược lại, một aspect *cố ý* retry, cache, authorization, fallback hoặc chuyển đổi exception có thể làm thay đổi hành vi; nhưng thay đổi đó phải được mô tả thành hợp đồng, không được xảy ra ngầm.

| Kết quả của target | Advice được thiết kế chỉ để ghi log | Triệu chứng advice sai |
| --- | --- | --- |
| Trả object X | Ghi sự kiện rồi trả đúng X | Trả giá trị khác, mất metadata hoặc đổi identity |
| Trả null hợp lệ | Giữ nguyên null | Thay null bằng đối tượng giả hoặc ném lỗi ngoài quy định |
| Ném ngoại lệ E | Ghi lỗi rồi ném lại E | Nuốt E và trả OK |
| Ném E có cause | Giữ E/cause, hoặc wrap đúng chính sách | Tạo lỗi mới không kèm cause |
| Tạo đúng một side effect | Không tự ý kích hoạt target thêm lần nữa | Gọi proceed hai lần dẫn đến ghi dữ liệu trùng |

Một số hệ AOP có advice loại after-returning hoặc around cho phép tác động đến kết quả với mức độ khác nhau. Khả năng thực tế phụ thuộc công cụ. **Đừng suy diễn rằng mọi advice có cùng quyền đổi return hoặc exception**; khi cần thay kết quả, phải kiểm tra semantics của kiểu advice và engine đang dùng.

## 3. Vì sao around advice dễ làm thay đổi hợp đồng nhất?

Với around advice, phần tiếp tục thực thi thường được biểu diễn bằng một hành động như proceed/continuation. Mô hình khái niệm:

~~~text
caller
  |
  v
around advice
  |-- mã trước proceed
  |-- proceed() --------> advice bên trong / target
  |                         |
  |                         +--> value hoặc Throwable
  |-- mã sau proceed / catch / finally
  |
  +--> return hoặc throw ------------> caller
~~~

Điểm quan trọng: **giá trị đi ra ngoài advice là giá trị advice return**, không tự động là giá trị target return. Ngoại lệ target sẽ không đến caller nếu advice catch rồi kết thúc bình thường. Có bốn lựa chọn điều khiển căn bản:

- **Không gọi proceed:** target không thực thi; có thể hợp lệ cho authorization từ chối hoặc cache hit.
- **Gọi proceed một lần:** thông thường phù hợp logging/timing; nhưng vẫn có thể thay đổi return hoặc exception.
- **Gọi proceed nhiều lần:** thực hiện lại phần còn lại của chain; có thể phù hợp retry nhưng rủi ro tác dụng phụ trùng.
- **Gọi proceed rồi tạo giá trị/ngoại lệ khác:** hoàn toàn có thể là chủ đích (fallback/translation), nhưng phá tính trong suốt nếu không nằm trong hợp đồng.

### 3.1. Ba lỗi chỉ khác một vài dòng

Ví dụ rút gọn theo ký hiệu quanh continuation; đây là **đoạn minh họa**, không phải chương trình độc lập:

~~~java
// Sai: bỏ kết quả thật rồi thay bằng một hằng số.
Object rewrite(Proceed next) throws Throwable {
    next.call();
    return "OK";
}

// Sai: che lỗi kiểm tra dữ liệu bằng kết quả thành công giả.
Object swallow(Proceed next) throws Throwable {
    try {
        return next.call();
    } catch (IllegalArgumentException failure) {
        return "OK";
    }
}

// Đúng với chính sách quan sát: bảo toàn kết quả/lỗi.
Object transparent(Proceed next) throws Throwable {
    try {
        Object result = next.call();
        logSuccess();
        return result;
    } catch (Throwable failure) {
        logFailure(failure);
        throw failure;
    }
}
~~~

Trong đoạn cuối, logSuccess/logFailure chỉ là **hàm giả định**, chưa có định nghĩa. Dù logic xử lý kết quả đã đúng hướng, mã thực tế vẫn phải xem xét lỗi của chính hệ logging, nếu không một lỗi ghi log có thể thay lỗi nghiệp vụ. Chương trình hoàn chỉnh ở mục 5 dùng System.out để minh họa và có lệnh chạy cụ thể.

### 3.2. Return trong finally có thể ghi đè cả return và throw

Java cho phép return trong finally, nhưng đây thường là bẫy nghiêm trọng:

~~~java
Object dangerous(Proceed next) throws Throwable {
    try {
        return next.call();
    } finally {
        return "OK"; // Ghi đè kết quả hoặc cả ngoại lệ đang truyền ra.
    }
}
~~~

Đây là đoạn minh họa sử dụng interface Proceed ở mục 5. Dù next.call() trả bất kỳ giá trị nào hoặc ném ngoại lệ, finally có return sẽ khiến phương thức kết thúc bằng "OK" (trừ tình huống đặc biệt như JVM dừng bất thường). Một trường hợp tương tự là **ném ngoại lệ mới trong finally**, làm che kết quả/lỗi đang xảy ra.

Việc cố viết một advice “luôn trả kết quả tốt” thường tạo **thành công giả**: thao tác nghiệp vụ thực sự thất bại nhưng caller không còn thông tin để rollback, báo lỗi hoặc thử lại đúng cách.

## 4. Các kịch bản đổi giá trị trả về sai

### 4.1. Bỏ kết quả của proceed

Sai sót rõ nhất là gọi proceed để target chạy nhưng bỏ giá trị trả về rồi return một giá trị mặc định. Khi caller dùng String, lỗi có thể khó nhận ra vì cả "OK" và "CONFIRMED:A-1" đều hợp lệ về kiểu. Khi caller dùng Boolean hay mã trạng thái, kết quả sai có thể làm thay đổi quyết định nghiệp vụ.

**Dấu hiệu:** log bên trong target ghi đúng return nhưng caller nhận dữ liệu khác; debugger dừng tại target cho thấy giá trị khác giá trị cuối cùng.

**Cách tìm:** log hoặc assert giá trị ngay trước return của target, sau proceed của từng advice và ngay tại caller; đừng chỉ đọc một đầu của đường gọi.

### 4.2. Ép kiểu và null không hợp đồng

Advice có thể trả một đối tượng không tương thích với kiểu phương thức. Với proxy hoặc mã generated, caller có thể gặp ClassCastException khi kết quả được cast, hoặc NullPointerException khi unboxing null thành kiểu nguyên thủy. **Vị trí stack trace** có thể nằm ở lớp proxy/caller, nhưng nguyên nhân là advice đã trả giá trị không phù hợp.

Ví dụ minh họa không độc lập:

~~~java
// Giả sử join point thật công bố trả về int.
Object wrongResult(Proceed next) {
    return null; // Rủi ro unboxing null hoặc vi phạm hợp đồng non-null.
}
~~~

Không nên “khắc phục” bằng cách bọc mọi return thành String. Hãy bảo toàn kiểu và ngữ nghĩa của phương thức đích. Nếu thật sự đổi kiểu public API, đó là thay đổi hợp đồng ở cấp API, không phải một chính sách advice bí mật.

### 4.3. Thay đổi đối tượng trả về

Advice có thể clone, wrap, cache hoặc chỉnh sửa đối tượng được trả về. Caller có thể phụ thuộc vào identity, tính bất biến, version field hoặc metadata mà việc thay đối tượng làm mất. Ngay cả khi toString() cho kết quả giống nhau, phép so sánh identity hay vòng đời resource có thể đã khác.

**Câu hỏi chẩn đoán:** advice chỉ quan sát kết quả hay có nhiệm vụ biến đổi dữ liệu? Nếu không có yêu cầu biến đổi, tránh mutate và trả lại chính object từ proceed.

### 4.4. Giá trị trả về của lời gọi bất đồng bộ

Một phương thức trả CompletionStage, Future hoặc một đối tượng biểu diễn kết quả trì hoãn có hai thời điểm riêng: **trả handle** và **hoàn tất phép xử lý bên trong**. Advice around chỉ bao quanh thời điểm trả handle có thể chưa biết thành công/thất bại cuối cùng.

Nếu advice catch quanh proceed nhưng lỗi xuất hiện sau đó khi future hoàn tất, catch này không bắt được lỗi đó. Tương tự, biến một CompletionStage thành một String trả trực tiếp có thể phá kiểu và semantics. Muốn chuyển đổi lỗi async phải làm theo hợp đồng của abstraction và bảo toàn cancellation, completion, propagation và ngữ cảnh thực thi.

## 5. MRE Java 17+: đối chiếu advice sai và advice bảo toàn hợp đồng

Đây là chương trình độc lập, chạy không cần thư viện ngoài JDK. Nó tách target CheckoutImpl, hợp đồng advice, lớp proxy và hàm quan sát để thấy **giá trị thật, giá trị bị thay và ngoại lệ bị nuốt**.

**Lưu mã nguyên vẹn trong tệp AdviceOutcomeDemo.java:**

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class AdviceOutcomeDemo {
    interface Checkout { String confirm(String id); }

    static final class CheckoutImpl implements Checkout {
        @Override public String confirm(String id) {
            if (id.isBlank()) throw new IllegalArgumentException("missing id");
            return "CONFIRMED:" + id;
        }
    }
    @FunctionalInterface interface Proceed { Object call() throws Throwable; }
    @FunctionalInterface interface Advice { Object around(Proceed next) throws Throwable; }
    static Checkout wrap(Checkout target, Advice advice) {
        return (Checkout) Proxy.newProxyInstance(
            Checkout.class.getClassLoader(), new Class<?>[] {Checkout.class},
            (proxy, method, args) -> advice.around(() -> {
                try { return method.invoke(target, args); }
                catch (InvocationTargetException wrapper) { throw wrapper.getCause(); }
            }));
    }
    static void observe(String label, Checkout service, String id) {
        try { System.out.println(label + " -> " + service.confirm(id)); }
        catch (IllegalArgumentException failure) {
            System.out.println(label + " -> ERROR:" + failure.getMessage());
        }
    }
    public static void main(String[] args) {
        Advice rewrite = next -> { next.call(); return "OK"; };
        Advice swallow = next -> {
            try { return next.call(); }
            catch (IllegalArgumentException failure) { return "OK"; }
        };
        Advice transparent = next -> {
            try {
                Object value = next.call();
                System.out.println("AUDIT success");
                return value;
            } catch (Throwable failure) {
                System.out.println("AUDIT error=" +
                        failure.getClass().getSimpleName());
                throw failure;
            }
        };
        observe("plain-valid", new CheckoutImpl(), "A-1");
        observe("rewrite-valid", wrap(new CheckoutImpl(), rewrite), "A-1");
        observe("plain-invalid", new CheckoutImpl(), "");
        observe("swallow-invalid", wrap(new CheckoutImpl(), swallow), "");
        observe("safe-valid", wrap(new CheckoutImpl(), transparent), "A-1");
        observe("safe-invalid", wrap(new CheckoutImpl(), transparent), "");
    }
}
~~~

Lệnh chạy bằng JDK 17 trở lên, trong thư mục chứa file:

~~~bash
javac AdviceOutcomeDemo.java
java AdviceOutcomeDemo
~~~

**Stdout đã kiểm chứng bằng cách biên dịch/chạy trên JDK 21:**

~~~text
plain-valid -> CONFIRMED:A-1
rewrite-valid -> OK
plain-invalid -> ERROR:missing id
swallow-invalid -> OK
AUDIT success
safe-valid -> CONFIRMED:A-1
AUDIT error=IllegalArgumentException
safe-invalid -> ERROR:missing id
~~~

**Giải thích từng quan sát:**

- Với **plain-valid**, target trả dữ liệu đúng: CONFIRMED:A-1.
- Với **rewrite-valid**, target vẫn có thể thực thi bình thường, nhưng advice gọi next.call() rồi vứt kết quả và return OK. Caller không hề biết kết quả xác nhận gốc.
- Với **plain-invalid**, id rỗng tạo IllegalArgumentException("missing id"); caller nhận đúng lỗi.
- Với **swallow-invalid**, advice bắt IllegalArgumentException rồi return OK. Từ phía caller, tình trạng thất bại đã biến thành thành công giả.
- Với **safe-valid**, advice ghi audit rồi return đúng value của target.
- Với **safe-invalid**, advice ghi loại lỗi và ném lại chính Throwable; caller vẫn thấy thông điệp missing id.

Phần **wrap** tạo JDK dynamic proxy cho interface Checkout, dùng reflection để gọi target. Method.invoke gói lỗi do target ném trong InvocationTargetException, vì thế bộ chặn phải lấy wrapper.getCause() nếu muốn advice nhìn thấy ngoại lệ gốc. Điều này đặc trưng cho đường gọi reflection trong ví dụ, không phải một quy tắc về exception wrapper của mọi engine AOP.

**Phạm vi chứng minh:** ví dụ có một advice cho mỗi proxy, một lời gọi tuần tự, không side effect bền vững và lỗi được tạo xác định. Nó chứng minh tính chất điều khiển return/exception của around advice qua continuation. Nó chưa chứng minh về pointcut, thứ tự nhiều aspect, weaving, retry đa luồng hoặc quyền truy cập thực tế.

## 6. Advice nuốt lỗi: vì sao nguy hiểm hơn một log sai?

“Nuốt lỗi” xảy ra khi một ngoại lệ hoặc trạng thái thất bại đáng lẽ phải tới caller nhưng advice **chặn đường truyền lỗi và kết thúc bình thường**. Việc catch exception để ghi log tự nó không sai; sai ở quyết định tiếp theo khi advice được kỳ vọng trong suốt.

Xét đường truyền sau:

~~~text
caller
  |
  v
Advice.around
  |
  +---- proceed() --> Target
                      |
                      +---- throws IllegalArgumentException
  |
  +---- catch IllegalArgumentException
          |
          +---- return "OK" --> caller thấy thành công
~~~

Hậu quả có thể gồm:

- **Sai giao thức nghiệp vụ:** caller đánh dấu thao tác thành công dù target không cập nhật dữ liệu.
- **Mất quyết định xử lý lỗi:** tầng ngoài không thể trả mã lỗi phù hợp, cảnh báo người dùng hoặc retry theo quy tắc.
- **Sai giám sát:** tỷ lệ thành công giả tăng, error-rate giảm dù lỗi thật vẫn phát sinh.
- **Sai điều khiển giao dịch/tác dụng phụ:** code phía ngoài tiếp tục một nhánh thành công có thể gây thao tác không nên thực hiện.
- **Khó điều tra:** log ở target có lỗi, nhưng response/return ở caller cho biết thành công.

Có trường hợp **cố ý** biến lỗi thành giá trị fallback như cache miss hoặc xử lý một lỗi cụ thể thuộc hợp đồng. Khi đó cần quy định rõ **được phép bắt loại lỗi nào**, giá trị fallback có nghĩa gì, liệu có được xem là thành công không, và có cần ghi metric riêng. Không đánh đồng fallback được thiết kế với nuốt lỗi vô tình.

### 6.1. Catch quá rộng

Advice kiểu “catch Throwable rồi return null” thường che cả nhiều loại lỗi không được phép phục hồi. Catch Exception cũng có thể bao trùm lỗi lập trình không thuộc lỗi nghiệp vụ dự kiến. Ngay cả catch đúng loại vẫn có thể sai nếu đó là lỗi mà caller bắt buộc phải nhìn thấy.

**Nguyên tắc:** chỉ xử lý được khi có một hợp đồng phục hồi cụ thể; còn lại **ném tiếp**. Đừng chuyển mọi ngoại lệ thành trạng thái OK chỉ để tránh hiển thị stack trace.

### 6.2. Catch đúng nhưng ném sai

Ví dụ dưới đây là **đoạn mã minh họa** dùng kiểu Proceed của mục 5:

~~~java
Object loseCause(Proceed next) throws Throwable {
    try {
        return next.call();
    } catch (IllegalArgumentException original) {
        throw new IllegalStateException("failed"); // Mất liên kết cause.
    }
}
~~~

Nếu phải chuyển loại lỗi theo một hợp đồng rõ ràng, giữ cause:

~~~java
Object translate(Proceed next) throws Throwable {
    try {
        return next.call();
    } catch (IllegalArgumentException original) {
        throw new IllegalStateException("invalid request", original);
    }
}
~~~

Ở biến thể thứ hai, caller nhận IllegalStateException thay vì IllegalArgumentException: **đây vẫn là một thay đổi hành vi**, chỉ hợp lệ nếu API hoặc chính sách chuyển đổi lỗi cho phép. Giữ cause giúp truy vết nhưng không tự biến đổi thay đổi đó thành “trong suốt”.

### 6.3. Không nhận ra lỗi phụ sinh ra trong cleanup/logging

Nếu target ném lỗi E1 rồi nhánh finally của advice ném lỗi E2, E2 có thể trở thành lỗi được truyền ra và che E1. Do đó, code dọn tài nguyên hoặc ghi audit cần chính sách riêng cho lỗi phụ.

Trong code tự quản lý nhiều lỗi, có thể gắn lỗi phụ bằng Throwable.addSuppressed nếu thích hợp và nếu còn giữ được lỗi chính. Tuy nhiên, không tùy tiện gọi addSuppressed trên Throwable từ thư viện hoặc các đối tượng được chia sẻ; phải hiểu quy tắc sở hữu và không tạo chu kỳ nguyên nhân. Cơ chế try-with-resources của Java cũng có quy tắc suppressed để bảo tồn lỗi chính khi đóng resource gặp lỗi.

### 6.4. Ngoại lệ checked, unchecked và proxy

Một JDK dynamic proxy có thể gói **checked exception không được khai báo trong chữ ký interface** bằng UndeclaredThrowableException nếu InvocationHandler cho ngoại lệ đó thoát ra. Đây là một lớp wrapper về mặt hợp đồng proxy, không tự đồng nghĩa advice đã “nuốt” lỗi. Cần đọc cả cause của wrapper và chữ ký phương thức. Với phương thức ném unchecked exception như chương trình ở mục 5, lỗi gốc có thể đi thẳng đến caller.

## 7. Phân tích chính xác return, throw, proceed và finally

Một cách chẩn đoán hiệu quả là gắn mỗi advice với bốn điểm quan sát:

- **ENTER:** advice vừa bắt đầu.
- **PROCEED-RESULT:** next.proceed() trở về bình thường với giá trị R.
- **PROCEED-THROW:** next.proceed() ném ngoại lệ E.
- **ADVICE-OUTCOME:** lời gọi advice cuối cùng return giá trị nào hoặc throw ngoại lệ nào?

Bảng kiểm tra:

| Kết quả proceed | Kết quả advice | Diễn giải |
| --- | --- | --- |
| return R | return chính R | Trong suốt về return trong ca thử này |
| return R | return S khác R | Có thay đổi return, cần chứng minh chính sách cho phép |
| return R | throw E | Advice tạo lỗi mới hoặc làm lỗi ghi log/cleanup che kết quả |
| throw E | throw chính E | Đường truyền lỗi được bảo toàn |
| throw E | throw F có E là cause | Có chuyển đổi lỗi có truy vết; phải đúng hợp đồng |
| throw E | return R | Ngoại lệ bị xử lý/fallback; phải chứng minh đây là chủ đích |
| Không gọi proceed | return/throw | Chặn trước target; chỉ hợp lệ trong policy cho phép |
| Gọi proceed nhiều lần | return/throw | Retry/replay; cần xem idempotency và tác dụng phụ |

**Phân biệt reference và equality:** với giá trị immutable như String, có thể so sánh nội dung; nhưng với object result, chính sách trong suốt thường cần trả lại **chính reference** target đưa ra. Khi không thể bảo toàn identity vì phải wrap/copy theo chính sách, cần ghi rõ loại đối tượng mới và thuộc tính bắt buộc phải giữ.

**Cẩn thận với finally:** finally chạy cả khi return và khi throw. Đặt return/throw trong finally dễ thay kết quả đang trên đường ra. Nên dùng finally để cleanup không đổi outcome, đồng thời xử lý được lỗi cleanup theo chính sách rõ.

## 8. Tương tác nhiều aspect: lỗi do thứ tự, không chỉ do từng advice

Một lời gọi có thể đi qua chuỗi **TRACE → RETRY → VALIDATE → TARGET**. Các mối quan tâm tưởng như độc lập nhưng kết quả tùy thuộc vào thứ tự:

~~~text
Trường hợp A: RETRY nằm ngoài TRANSLATE
RETRY
  TRANSLATE
    TARGET -> IllegalArgumentException
  TRANSLATE -> IllegalStateException
RETRY kiểm tra IllegalArgumentException: không khớp, không thử lại

Trường hợp B: TRANSLATE nằm ngoài RETRY
TRANSLATE
  RETRY
    TARGET -> IllegalArgumentException
    RETRY có thể bắt lỗi gốc để cân nhắc thử lại
  RETRY trả thành công hoặc lỗi cuối
TRANSLATE chỉ xử lý outcome cuối
~~~

Đây là mô hình minh họa. Loại ngoại lệ nào thực sự có thể retry và nên retry phải được định nghĩa theo nghiệp vụ, **không** tự động coi IllegalArgumentException là lỗi tạm thời. Nếu retry lặp một thao tác không idempotent, có thể ghi hai lần dù advice cuối cùng trả thành công.

Khi nhiều aspect cùng có catch/return, phải biết:
- Advice nào nằm ngoài, advice nào nằm trong?
- Exception có bị wrap/translate trước khi tới interceptor quyết định retry không?
- Một failure ở attempt đầu đã được xử lý hay cần đi tới caller?
- Advice logging đang tính **mỗi attempt** hay **mỗi yêu cầu logic**?
- Có advice nào tự bắt lại lời gọi của nó thông qua pointcut, dẫn đến reentrancy không?

**Không suy luận precedence từ tên class hoặc vị trí trong file**. Tập join point, precedence và cách đan advice do engine AOP cụ thể quyết định. Xác minh bằng cấu hình và trace quan sát thực tế; nếu hai aspect đổi thứ tự làm lỗi biến mất, ghi đó là bằng chứng về tương tác, không tự động kết luận từng aspect đều đúng.

## 9. Cách tái hiện lỗi và xác định advice gây ra

### Bước 1 — Ghi hợp đồng trước khi thay code

Viết một câu định lượng: với đầu vào X, target phải trả R; với đầu vào Y, target phải ném E; side effect S thực hiện N lần. Đặt tiêu chí PASS/FAIL ở **caller**, vì caller là nơi thấy outcome cuối.

Ví dụ ở mục 5:
- id="A-1" phải trả "CONFIRMED:A-1".
- id="" phải ném IllegalArgumentException với message "missing id".
- Advice audit không được biến hai outcome thành "OK".

### Bước 2 — Thu hẹp thành MRE

Giữ một target, một phương thức, một advice và một lời gọi xác định. Loại bỏ mạng, cơ sở dữ liệu, queue, lịch trình và thời gian thực nếu chúng không liên quan. Lưu stack trace, loại exception và số lần target/proceed. Khi đã cô lập được một advice, mới thêm advice thứ hai để tìm lỗi tương tác.

### Bước 3 — So sánh năm đường đi

1. Target trực tiếp với đầu vào hợp lệ.
2. Target có advice với đầu vào hợp lệ.
3. Target trực tiếp với đầu vào lỗi.
4. Target có advice với đầu vào lỗi.
5. Cùng điều kiện sau khi sửa advice.

Ma trận này cho phép xác định **sai lệch do advice** thay vì nhầm với logic target đã sai sẵn. Với nhiều aspect, thêm các ca chỉ A, chỉ B, A ngoài B và B ngoài A.

### Bước 4 — Đặt log tại các ranh giới

Đừng chỉ log ở catch. Ghi:
- Giá trị và loại đối tượng target thực sự return, hoặc exception nó ném.
- Outcome ngay sau mỗi lần proceed.
- Outcome cuối cùng của mỗi advice.
- Outcome được caller quan sát.
- Số lần proceed và target được gọi; mã nhận dạng logical request/attempt nếu có retry.

Khi test concurrency, dùng correlation id và đồng bộ có kiểm soát. Không nối stack trace của các thread thành một call stack đồng bộ duy nhất.

### Bước 5 — Xem cơ chế áp dụng thật

Nếu dùng proxy, bảo đảm caller nhận proxy chứ không giữ trực tiếp target. Nếu dùng compile-time hoặc load-time weaving, kiểm tra bytecode đã biến đổi, phiên bản weaver, classloader và join point đúng. Một MRE JDK proxy có thể chứng minh lỗi ở logic around, nhưng không đủ để chứng minh một cấu hình weaving đã chọn đúng phương thức.

### Bước 6 — Sửa một biến, chạy lại các ca

Đổi một nhánh return hoặc catch, rồi chạy lại toàn bộ ma trận. Tránh vừa sửa pointcut, vừa đổi precedence, vừa thay logic để rồi không biết yếu tố nào giải quyết vấn đề.

## 10. Viết kiểm thử bảo vệ hợp đồng

**Không chỉ assert “không ném exception”.** Đó là một assertion nguy hiểm cho ca nuốt lỗi: advice sai ở ví dụ thậm chí sẽ làm assertion này thành công.

| Ca thử | Đầu vào | Mong đợi |
| --- | --- | --- |
| Success transparency | Dữ liệu hợp lệ | Return đúng nội dung và kiểu; reference giữ nguyên nếu hợp đồng yêu cầu |
| Failure transparency | Dữ liệu không hợp lệ | Caller nhận lỗi đúng class/cause theo hợp đồng |
| Advice logging failure | Sink ghi log lỗi | Outcome theo chính sách logging; không âm thầm đổi lỗi nghiệp vụ |
| Null return | Target trả null hợp lệ | Advice không tự đổi null nếu không có chính sách |
| Primitive return | Target trả int/boolean | Không null-unboxing, sai kiểu hoặc đổi giá trị |
| Multiple proceed | Advice retry với target giả | Số attempt đúng, side effect chỉ lặp khi được phép |
| Nested advice | A bao B hoặc B bao A | Return/throw, số log và thứ tự sự kiện đúng |
| Concurrent calls | Hai request có context khác nhau | Không lẫn result/exception/correlation giữa các request |

### 10.1. Test đơn vị bằng continuation giả

Có thể kiểm tra advice riêng mà không cần engine:

~~~java
// Đoạn kiểm thử minh họa đặt cùng phạm vi có các kiểu Advice, Proceed.
Advice transparent = next -> next.call();
Object marker = new Object();
Object result = transparent.around(() -> marker);
if (result != marker) {
    throw new AssertionError("result identity changed");
}
~~~

Đoạn này không có main/khai báo lớp độc lập; mục đích là giải thích điều kiện test. Một test khác dùng continuation ném sẵn một exception E rồi khẳng định advice ném lại **chính E** khi chính sách yêu cầu bảo toàn identity.

Trong hệ thống thật, không phải mọi advice đều phải bảo toàn Throwable identity; một advice chuyển đổi ngoại lệ có chủ đích được kiểm thử theo hợp đồng **loại lỗi mới và cause**.

### 10.2. Test lỗi bị nuốt không cần mock phức tạp

Dùng target ném IllegalArgumentException("missing id"), gọi qua advice, và viết assertion rằng caller **phải nhận ngoại lệ**. Test thất bại nếu advice trả "OK". Với phương thức có side effect, dùng counter/in-memory store để chắc rằng việc nuốt lỗi không làm caller đánh dấu thành công rồi ghi dữ liệu ngoài ý muốn.

### 10.3. Test thất bại của chính advice

Trường hợp ít được kiểm tra: log sink, formatter hoặc metadata extractor của advice ném lỗi, trong khi target đã thành công hoặc đã ném exception E. Hãy dựng fake sink ném exception cố định; xác định policy của aspect là:

- **Fail closed:** lỗi của cơ chế kiểm soát bắt buộc phải khiến lời gọi thất bại.
- **Fail open có kiểm soát:** một công cụ quan sát không bắt buộc có thể ghi nhận qua kênh khác và không làm đổi outcome nghiệp vụ.
- **Truyền lỗi:** nếu error policy yêu cầu caller biết lỗi của advice.

Không áp dụng một chính sách chung cho mọi loại aspect. Với kiểm soát truy cập và audit bắt buộc, việc “cứ trả OK” thường gây hại nghiêm trọng.

## 11. Khi nào chuyển đổi return hoặc exception là hợp lệ?

Không phải mọi biến đổi do advice đều là bug. AOP cho phép thể hiện chính sách xuyên suốt, nhưng chính sách phải **cụ thể, có phạm vi và có test**.

### Authorization

Advice có thể **không gọi proceed** khi quyền không hợp lệ. Outcome hợp lệ thường là từ chối hoặc ném lỗi rõ ràng theo hợp đồng. Trả một chuỗi "OK" khi không được phép là sai hiển nhiên.

### Cache

Advice có thể trả kết quả từ cache mà không chạy target. Điều đó hợp lệ khi cache được phép theo hợp đồng dữ liệu, khóa cache đúng, không trả dữ liệu sai scope, và xử lý invalidation phù hợp. Cache hit không được che lỗi từ thao tác bắt buộc phải thực hiện mỗi lần.

### Retry

Advice có thể gọi proceed nhiều lần với điều kiện retry rõ. Phải kiểm tra idempotency, số attempt, backoff và ngoại lệ cuối. Không tự ý retry mọi exception; tuyệt đối không gộp “đã thử lại” với “đã thành công”.

### Fallback

Advice có thể trả giá trị dự phòng khi một lỗi **được liệt kê rõ là có thể phục hồi** xảy ra. Giá trị dự phòng phải là một kết quả có ngữ nghĩa hợp lệ, caller biết trạng thái degraded nếu cần, và có metric. Một fallback thành công giả cho thao tác ghi hoặc thanh toán là nguy hiểm.

### Exception translation

Advice có thể chuyển lỗi tầng thấp sang lỗi API có ý nghĩa hơn, với cause đầy đủ và mapping chính xác. Ví dụ, lỗi truy cập tài nguyên có thể trở thành lỗi dịch vụ; nhưng chuyển mọi lỗi sang IllegalStateException không phân biệt sẽ làm mất khả năng xử lý có chủ đích.

### Result normalization

Advice có thể chuẩn hóa output nếu **hợp đồng của join point đã tuyên bố điều đó**, chẳng hạn chuẩn hóa text. Tuy nhiên, làm biến đổi object có identity hoặc semantics đặc biệt có thể phá invariants. Đừng tự ý thay return khi nhiệm vụ ban đầu chỉ là tracing/timing.

**Tiêu chuẩn chung:** người gọi và người sở hữu nghiệp vụ đã chấp nhận thay đổi; scope join point được giới hạn; thay đổi về return, exception và side effect được kiểm thử.

## 12. Các trường hợp nâng cao dễ bỏ sót

### 12.1. Sai vị trí return sau nhiều proceed

Một advice có thể gọi proceed hai lần, lấy R1 rồi R2 nhưng lại return R1, hoặc return một hằng số. Khi target không idempotent, side effect có thể xảy ra hai lần trong khi caller chỉ thấy một kết quả. Ghi mỗi attempt riêng, xác định outcome nào là outcome cuối theo chính sách retry.

### 12.2. Xử lý lỗi trong advice cuối cùng làm mất cause

Lớp proxy, reflection và mã woven có thể thay hình thức stack trace. Nếu advice tạo Throwable mới mà không giữ cause, việc điều tra sẽ khó hơn. Đọc trace đầy đủ gồm cause, suppressed; phân biệt **nơi lỗi được tạo** và **nơi lỗi được chuyển đổi**. Một frame ở adapter/proxy chưa đủ chứng minh adapter gây ra lỗi nghiệp vụ.

### 12.3. Bắt Throwable có thể bao gồm những lỗi không thể phục hồi

Lệnh catch Throwable có phạm vi rộng hơn catch Exception. Trong advice quan sát, bắt rồi **ném lại** có thể giúp ghi dấu vết; nhưng làm việc quá nhiều trong catch, ghi log có thể ném lỗi hoặc trả fallback chung là nguy hiểm. Không cố phục hồi mọi lỗi thuộc nhóm Error. Thiết kế failure policy cho từng loại tình huống.

### 12.4. Lỗi nằm trong lời gọi bất đồng bộ

Với API bất đồng bộ, “return thành công” có thể chỉ là trả một future chưa hoàn tất. Một advice ghi “success” ngay sau proceed có thể chỉ chứng minh future đã được tạo. Cần phân biệt lỗi ném **đồng bộ trước khi trả future** với lỗi **hoàn tất future trong trạng thái thất bại**. Việc gắn handler xử lý completion phải bảo toàn completion/cancellation và không làm rò context.

### 12.5. Mutable shared state giữa các request

Nếu advice giữ lastResult hoặc lastError trong field dùng chung, hai luồng có thể đổi dữ liệu của nhau. Khi đó caller nhận outcome sai dù mỗi target riêng lẻ đều đúng. Dùng trạng thái theo invocation, không chia sẻ mutable result giữa request nếu không có thiết kế đồng bộ rõ; thử đồng thời với input khác nhau để phát hiện.

### 12.6. Pointcut quá rộng làm advice tác động sai phương thức

Một advice fallback chỉ dành cho read-only query nhưng pointcut chọn cả thao tác ghi có thể khiến lỗi ghi bị nuốt. Luôn thử cả **join point phải match** và **join point không được match**. Tập join point hỗ trợ khác nhau theo engine; phải xác minh thực tế thay vì đoán từ tên method.

## 13. Quy trình review code advice trước khi triển khai

Khi review một aspect có around advice, đi theo đường xử lý cho từng nhánh:

1. **Input/selection:** pointcut nào kích hoạt advice? Có phương thức nào không phù hợp bị chọn?
2. **Enter:** advice có kiểm tra trước và ném lỗi đúng chính sách hay không?
3. **Continuation:** proceed được gọi 0, 1 hay nhiều lần; có điều kiện giới hạn và guard reentrancy không?
4. **Success:** sau proceed, có return lại đúng giá trị, kiểu, nullability, identity hoặc contract biến đổi được duyệt không?
5. **Failure:** catch ngoại lệ nào? Có ném lại, wrap giữ cause, hoặc fallback đúng điều kiện không?
6. **Finally/cleanup:** code cleanup có thể return/throw làm che outcome hay không? Lỗi phụ được xử lý thế nào?
7. **Composition:** advice nằm ngoài hay trong các aspect khác? Exception có bị translate trước khi đến retry/audit không?
8. **Runtime:** proxy/weaving áp dụng vào đúng lời gọi chưa? Các trường hợp gọi nội bộ, async và classloader đã được xét chưa?
9. **Evidence:** kiểm thử qua caller, đầu ra/ngoại lệ thật, side effect, số lần gọi và log sự kiện có chứng minh hợp đồng không?

Trong pull request hay tài liệu thiết kế, phần mô tả thay đổi nên nêu một tình huống **trước–sau**: “Trước đây, khi target ném IllegalArgumentException('missing id'), caller nhận 'OK'. Sau sửa, caller nhận đúng IllegalArgumentException và không có trạng thái thành công giả.” Việc mô tả bằng quan sát của caller giúp người review xác minh mục tiêu chính xác.

## 14. Sai lầm thường gặp khi cố sửa lỗi

| Cách sửa sai | Vì sao chưa đúng | Cách kiểm chứng/sửa |
| --- | --- | --- |
| Xóa catch để hết bị nuốt lỗi | Có thể làm mất logging, cleanup hoặc policy phục hồi thật sự cần thiết | Chỉ ném tiếp những lỗi phải truyền; giữ nhánh phục hồi đã định nghĩa rõ |
| Luôn return giá trị của proceed | Có thể phá chính sách authorization/cache/fallback có chủ đích | So sánh với hợp đồng riêng của loại aspect |
| Catch rồi throw new RuntimeException(e.getMessage()) | Mất cause và type | Giữ cause hoặc ném lại chính E khi hợp đồng yêu cầu |
| Return trong finally để bảo đảm có output | Ghi đè lỗi và return đang truyền ra | Dùng finally cho cleanup; tránh return/throw ngoài policy |
| Đổi thứ tự aspect rồi kết luận đã sửa | Có thể che triệu chứng, lỗi xảy ra ở join point khác | Test ma trận precedence và xác định nguyên nhân |
| Chỉ assert status=200 hoặc “không lỗi” | Nuốt lỗi được coi là thành công | Assert cụ thể giá trị, exception, side effect |
| Chỉ unit-test advice trực tiếp | Không phát hiện cấu hình pointcut/proxy/weaving sai | Thêm integration test qua đường gọi thật |
| Catch mọi Throwable và trả fallback | Che cả lỗi không thể phục hồi | Giới hạn loại lỗi, ngữ cảnh, hợp đồng fallback |
| Log đủ nhiều nhưng không có request id | Khó nối target, advice và caller trong concurrency | Correlate bằng logical-call id và attempt id |
| Kiểm tra giá trị return mà quên gọi lặp | Target có thể thực hiện hai lần dù return cuối đúng | Đếm proceed, target, side effect thực tế |

## 15. Bài tập tự luyện

**Bài tập 1 — Phát hiện lỗi đổi return.** Trong chương trình mục 5, đổi rewrite thành return kết quả của next.call() và chạy lại. Kỳ vọng dòng rewrite-valid trả CONFIRMED:A-1. Giải thích vì sao trước đó chương trình vẫn biên dịch nhưng sai ngữ nghĩa.

**Bài tập 2 — Phát hiện lỗi bị nuốt.** Đổi swallow thành catch rồi throw failure. Kỳ vọng dòng swallow-invalid thể hiện ERROR:missing id. Viết assertion ở caller để sai lầm “return OK” khiến test fail.

**Bài tập 3 — Kiểm thử finally.** Viết một advice có finally chứa return "OK" và bọc một target ném lỗi. Quan sát outcome; sau đó bỏ return trong finally và ném lại lỗi. Nêu nguyên nhân theo quy tắc điều khiển của Java.

**Bài tập 4 — Thử nhiều aspect.** Thêm một advice chuyển đổi IllegalArgumentException thành IllegalStateException có cause; ghép với một advice bắt riêng IllegalArgumentException. Đổi thứ tự, in event trace và giải thích tại sao cùng target có thể dẫn đến kết quả khác nhau. Đừng dùng exception đó làm ví dụ mặc định cho retry sản xuất.

**Bài tập 5 — Fallback có chủ đích.** Đề xuất một API read-only được phép trả kết quả cache khi nguồn dữ liệu tạm lỗi. Ghi rõ những lỗi được fallback, thời hạn dữ liệu, cách báo trạng thái degraded và test rằng các lỗi ngoài danh sách vẫn phải truyền đi.

## 16. Checklist chốt một advice an toàn về kết quả/lỗi

- [ ] Hợp đồng của join point được mô tả bằng đầu vào, return, exception, side effect và tính lặp an toàn.
- [ ] Đã xác định advice là trong suốt hay có quyền chuyển đổi kết quả/lỗi theo chính sách.
- [ ] Với lời gọi thành công, return đúng value/kiểu/nullability/identity cần thiết.
- [ ] Với lỗi, ném lại hoặc chuyển đổi đúng hợp đồng; giữ cause, suppressed khi thích hợp.
- [ ] Không có return hoặc throw ngoài chính sách trong finally/cleanup làm che outcome gốc.
- [ ] Số lần proceed được kiểm soát; retry có giới hạn và cân nhắc idempotency.
- [ ] Pointcut và precedence đúng; không chọn nhầm join point hoặc tự tái nhập.
- [ ] Advice xử lý lỗi của chính logging/audit/fallback theo failure policy đã thiết kế.
- [ ] Test có cả ca thành công, lỗi, null/kiểu đặc biệt, nhiều advice và concurrency khi liên quan.
- [ ] Đã xác minh qua đường gọi AOP thật; log/trace có thể đối chiếu outcome target với caller.

**Kết luận:** advice là một phần của đường thực thi công khai dù nằm ngoài mã nghiệp vụ. Sai lầm phổ biến không nằm ở việc có catch hoặc có return, mà ở **không bảo toàn hợp đồng quan sát được**: thay giá trị mà caller cần hoặc biến thất bại thành thành công không được cho phép. Đọc từng nhánh proceed/return/throw, so sánh target với caller và khóa hành vi bằng kiểm thử hồi quy là cách chắc chắn để phát hiện và sửa lỗi này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-004">Gọi proceed sai số lần hoặc sai thời điểm</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi proceed sai số lần hoặc sai thời điểm trong AOP

## 1. Bài toán và mục tiêu học tập

Trong **Aspect-Oriented Programming (AOP)**, **around advice** có thể quyết định có tiếp tục xử lý một join point hay không. Với cú pháp **AspectJ code style**, `proceed(...)` chuyển quyền điều khiển tới advice có độ ưu tiên thấp hơn hoặc computation gốc nếu không còn advice ở phía trong. Một lỗi nhỏ ở lệnh này có thể làm toàn bộ nghiệp vụ đổi nghĩa:

- **Thiếu `proceed()`:** một thao tác đáng lẽ được thực hiện bị bỏ qua, caller nhận kết quả thay thế hoặc phần xử lý bên trong không chạy.
- **Thừa `proceed()`:** cùng một computation có thể chạy nhiều lần, tạo side effect lặp, gửi request trùng, tính tiền nhiều lần, ghi log/metric nhiều lần.
- **`proceed()` đặt sai thời điểm:** validation xảy ra sau hành động nguy hiểm; khóa bị lấy quá muộn; metric “thành công” được ghi trước khi biết kết quả; cleanup không chạy khi có exception.

Không có quy tắc “mọi advice luôn phải proceed đúng một lần”: cache hit hoặc từ chối yêu cầu có thể **cố ý không proceed**; retry có thể **cố ý proceed nhiều lần**. Điều quan trọng là mỗi nhánh phải tuân thủ **hợp đồng về số lần thực hiện, thứ tự sự kiện, giá trị trả về, ngoại lệ và side effect** của join point.

Bài này dùng **AspectJ** làm ngôn ngữ minh họa cho các khái niệm AOP cơ bản và nâng cao. Các triển khai AOP khác có thể hỗ trợ tập join point và cú pháp tiếp tục khác nhau. Sau bài, bạn sẽ phân tích được luồng around/proceed, thiết kế đúng timing, kiểm tra retry/idempotency, nhận diện precedence và tạo kiểm thử có bằng chứng.

## 2. `proceed()` thực sự làm gì?

Cho một method:

```java
public String calculate() {
    return "done";
}
```

Một around advice AspectJ có thể bao quanh execution join point:

```aspectj
String around(): execution(String demo.Task.calculate()) {
    System.out.println("before");
    String result = proceed();
    System.out.println("after");
    return result;
}
```

Đây là snippet cần class `demo.Task` và aspect bao ngoài để biên dịch. Nếu chuỗi advice không chặn execution, trình tự là:

```text
caller
  → around: "before"
      → proceed()
          → advice phía trong (nếu có)
              → thân calculate()
          ← kết quả hoặc exception
  → around: "after" (chỉ khi proceed trả về bình thường)
  → caller
```

Trong AspectJ, `proceed()` **không mặc nhiên là một lời gọi Java trực tiếp tới method gốc**. Nó chuyển sang advice có precedence thấp hơn và sau đó mới tới computation thực, trừ khi advice phía trong quyết định ngừng. Một around advice phía ngoài có thể gọi `proceed` đúng một lần, nhưng advice phía trong gọi hai lần; phần thân method vẫn có thể chạy hai lần.

Nếu `proceed()` ném exception, các câu lệnh phía sau nó trong cùng `try` sẽ bị bỏ qua. Phần `catch`/`finally` tương ứng vẫn tuân theo quy tắc Java. Không nên dùng việc xuất hiện một log trong advice như bằng chứng đầy đủ về số lần chạy computation.

### So sánh với các advice khác

| Loại advice | Ý nghĩa | Quan hệ với `proceed` |
| --- | --- | --- |
| `before` | Chạy trước computation ở join point | Không tự kiểm soát continuation bằng `proceed`. |
| `after returning` | Chạy khi phần xử lý bên trong trả về bình thường | Không tương đương mọi lệnh “sau `proceed`”, vì `proceed` có thể ném lỗi. |
| `after throwing` | Chạy khi phần bên trong ném exception thích hợp | Có thể quan sát lỗi dù outer around về sau trả fallback, tùy precedence. |
| `after` | Chạy khi xử lý tiếp theo hoàn tất hoặc throw | Thích hợp khi chỉ cần kết thúc/cleanup. |
| `around` | Quyết định có tiếp tục, số lần tiếp tục và có thay đổi giá trị/exception không | Có quyền điều khiển mạnh nhất, nên đòi hỏi test chặt. |

Chỉ cần log “trước” hoặc “sau” thường có thể dùng before/after advice, giúp tránh các lỗi quản lý continuation không cần thiết.

## 3. Không gọi, gọi một lần và gọi nhiều lần

### 3.1. 0 lần: short-circuit hay lỗi?

```aspectj
String around(): execution(String demo.Cacheable.load()) {
    return "cached-value";
}
```

Advice này trả về mà không gọi `proceed`. Nếu đó là kết quả cache hợp lệ theo hợp đồng, hành vi có thể đúng. Nhưng nếu `load()` cần cập nhật trạng thái hoặc chứa kiểm tra bắt buộc, short-circuit khiến phần việc ấy bị bỏ qua. Advice phía trong có precedence thấp hơn cũng có thể bị bỏ qua. Phải định nghĩa rõ những thao tác nào **được phép** không xảy ra ở cache hit.

### 3.2. 1 lần: đường xử lý thông thường

```aspectj
String around(): execution(String demo.Cacheable.load()) {
    String result = proceed();
    return result;
}
```

Nếu không có tầng advice phía trong nào nhân bản execution, thân method chạy một lần. Gọi `proceed` một lần thường phù hợp với validation, tracing và đo thời gian trên thao tác vốn cần thực hiện đúng một lần.

### 3.3. 2 lần: không phải “đọc lại kết quả”

```aspectj
String around(): execution(String demo.Cacheable.load()) {
    String first = proceed();
    String second = proceed();
    return first + "|" + second;
}
```

Hai lệnh có thể khiến computation phía trong chạy hai lần. Kết quả có thể khác nhau nếu method tăng counter, đọc trạng thái biến đổi hoặc gọi dịch vụ ngoài. Nếu chỉ muốn dùng cùng kết quả hai lần, phải lưu biến sau **một** `proceed`:

```aspectj
String around(): execution(String demo.Cacheable.load()) {
    String once = proceed();
    return once + "|" + once;
}
```

Đặc biệt nguy hiểm khi method thực hiện `createOrder()`, `withdraw()`, ghi file, phát sự kiện hoặc gửi email. Thất bại của lần thứ hai không tự đảo ngược side effect đã thành công của lần thứ nhất.

## 4. Ví dụ AspectJ hoàn chỉnh, chạy được trên JDK 17+

Ví dụ sau có một class lưu counter và một aspect điều khiển lần lượt **0, 1, 2 lần proceed** cùng đường exception. Dùng AspectJ code style (tệp `.aj`), không cần framework ứng dụng.

Cấu trúc:

```text
demo/
  Counter.java
  Driver.java
  ProceedControl.aj
```

**Tệp `demo/Counter.java`**

```java
package demo;

public class Counter {
    private int calls;

    public String skip() {
        calls++;
        System.out.println("body skip count=" + calls);
        return "skip-" + calls;
    }

    public String once() {
        calls++;
        System.out.println("body once count=" + calls);
        return "once-" + calls;
    }

    public String twice() {
        calls++;
        System.out.println("body twice count=" + calls);
        return "twice-" + calls;
    }

    public String fail() {
        calls++;
        System.out.println("body fail count=" + calls);
        throw new IllegalStateException("boom");
    }

    public int count() {
        return calls;
    }
}
```

**Tệp `demo/Driver.java`**

```java
package demo;

public class Driver {
    public static void main(String[] args) {
        Counter counter = new Counter();
        System.out.println("skip=" + counter.skip());
        System.out.println("once=" + counter.once());
        System.out.println("twice=" + counter.twice());

        try {
            counter.fail();
        } catch (IllegalStateException ex) {
            System.out.println("main caught " + ex.getMessage());
        }

        System.out.println("count=" + counter.count());
    }
}
```

**Tệp `demo/ProceedControl.aj`**

```aspectj
package demo;

public aspect ProceedControl {
    String around(): execution(String demo.Counter.skip()) {
        System.out.println("around skip: no proceed");
        return "cached";
    }

    String around(): execution(String demo.Counter.once()) {
        System.out.println("around once: before");
        String result = proceed();
        System.out.println("around once: after");
        return result;
    }

    String around(): execution(String demo.Counter.twice()) {
        System.out.println("around twice: first");
        String first = proceed();
        System.out.println("around twice: second");
        String second = proceed();
        System.out.println("around twice: finish");
        return first + "|" + second;
    }

    String around(): execution(String demo.Counter.fail()) {
        System.out.println("around fail: before");
        try {
            return proceed();
        } catch (IllegalStateException ex) {
            System.out.println("around fail: caught " + ex.getMessage());
            throw ex;
        } finally {
            System.out.println("around fail: finally");
        }
    }
}
```

Dùng `ajc` hỗ trợ Java 17 và thư viện `aspectjrt.jar` tương ứng. Ví dụ lệnh cho Unix-like shell, với `ASPECTJ_HOME` là thư mục AspectJ và `ajc` có trong `PATH`:

```bash
ajc -17 -classpath "$ASPECTJ_HOME/lib/aspectjrt.jar" -d out \
  demo/Counter.java demo/Driver.java demo/ProceedControl.aj
java -cp "out:$ASPECTJ_HOME/lib/aspectjrt.jar" demo.Driver
```

Trên Windows, classpath dùng dấu `;` và cú pháp biến môi trường thay đổi theo shell. Lệnh `javac` thuần túy không biên dịch trực tiếp tệp AspectJ `.aj`.

**Stdout xác định của chương trình:**

```text
around skip: no proceed
skip=cached
around once: before
body once count=1
around once: after
once=once-1
around twice: first
body twice count=2
around twice: second
body twice count=3
around twice: finish
twice=twice-2|twice-3
around fail: before
body fail count=4
around fail: caught boom
around fail: finally
main caught boom
count=4
```

Giải thích:

- `skip()`: body không chạy vì không proceed; count vẫn 0.
- `once()`: proceed một lần, count tăng thành 1; log “after” xuất hiện sau log body.
- `twice()`: mỗi proceed tăng count, lần lượt thành 2 và 3; hai return value khác nhau.
- `fail()`: body tăng count thành 4 rồi throw; advice log trong catch, ném lại exception và thực hiện finally; caller bắt được “boom”.
- `count()` không bị pointcut nào chọn, trả 4; đây là kiểm chứng nội bộ về số lần method body thực sự được chạy.

Nếu chạy lại cùng nguồn nghiệp vụ nhưng không dệt aspect, kết quả khác: `skip` cũng chạy body, `twice` chỉ chạy một lần. Vì thế việc kiểm thử phải thực hiện với bytecode **đã dệt**.

## 5. Sai thời điểm: xác thực đặt sau side effect

Điều kiện đầu vào phải được kiểm tra **trước khi** kích hoạt computation cần bảo vệ. Snippet dưới đây cố ý sai:

```aspectj
String around(String id):
        execution(String demo.Repository.fetch(String)) && args(id) {
    String result = proceed(id);
    if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("invalid id");
    }
    return result;
}
```

Đây là ví dụ minh họa cần class `Repository`; nếu `fetch` đã truy cập mạng hoặc ghi audit, side effect đã xảy ra trước khi validation fail. Throw ở cuối không xóa được những thao tác đó.

Thứ tự hợp lý:

```aspectj
String around(String id):
        execution(String demo.Repository.fetch(String)) && args(id) {
    if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("invalid id");
    }
    return proceed(id);
}
```

Trong trường hợp này, **không gọi proceed khi input sai là chủ đích**. Nếu validation là quy tắc bảo vệ quan trọng, cần kiểm thử rằng pointcut thật sự được weave đúng mọi entry point; không xem advice là bảo đảm duy nhất khi có đường gọi không được instrument.

## 6. Sai thời điểm: lock, context và resource

Nếu muốn thân method chạy **trong một khóa**, thứ tự sau không có tác dụng bảo vệ:

```text
proceed()
lock.lock()
lock.unlock()
```

Thân method đã chạy xong trước khi khóa được lấy. Cần bao quanh phép tính:

```java
lock.lock();
try {
    return proceed();
} finally {
    lock.unlock();
}
```

Đây là **snippet bên trong around advice**, cần khai báo `lock` và return type tương thích mới biên dịch; không phải class Java độc lập. `finally` bảo đảm release kể cả khi `proceed()` throw. Vẫn phải kiểm tra nguy cơ deadlock, thứ tự lấy nhiều lock và reentrancy nếu method gọi lại chính nó.

Tương tự với tracing/security/correlation context: thiết lập context trước proceed, restore context cũ trong finally sau khi proceed kết thúc. Không đặt context vào thread local sau proceed rồi kết luận computation đã chạy với context đó.

Khi cleanup cũng có thể throw, cần chính sách giữ lỗi gốc (chẳng hạn suppressed exceptions hoặc exception handling thích hợp). Không cho lỗi cleanup vô tình che mất nguyên nhân thất bại ban đầu nếu hợp đồng cần giữ nó.

## 7. Sai thời điểm: đo thời gian và log thành công

**Đo đồng bộ đúng phần computation bên trong:**

```aspectj
String around(): execution(String demo.Service.read()) {
    long started = System.nanoTime();
    try {
        return proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        System.out.println("elapsedNanos=" + elapsed);
    }
}
```

Snippet cần `demo.Service.read()` và aspect hoàn chỉnh. `System.nanoTime()` đo **khoảng thời gian trôi qua**, không phải thời điểm lịch. `finally` được chạy cả khi proceed throw. Tuy nhiên việc in stdout trên đường nóng có thể lấn át chi phí method thực tế; dùng cách tổng hợp metric hiệu quả hơn khi cần benchmark.

**Đánh dấu thành công quá sớm:**

```aspectj
String around(): execution(String demo.Service.save()) {
    System.out.println("save succeeded");
    return proceed();
}
```

Đây là log **trước thao tác**; nếu method throw, log sai. Success log phải nằm **sau một `proceed()` đã trả về bình thường**. Failure log có thể ở catch phù hợp; không nuốt exception ngoài chính sách.

Không nên đưa log “after” chỉ vào sau lệnh proceed nếu mục tiêu là audit mọi kết thúc: khi exception xảy ra, code đó không chạy. Dùng finally hoặc advice after thích hợp.

## 8. Exception, return và finally

Một bẫy Java ảnh hưởng trực tiếp tới around advice:

```java
try {
    return proceed();
} finally {
    return fallback;
}
```

Snippet yêu cầu biến `fallback`, hợp đồng return type và một around advice bao ngoài; **không phải code Java độc lập**. Trong Java, `return` từ finally **lấn át** cả return ở try lẫn exception phát sinh trong try. Vì thế caller có thể thấy fallback dù method gốc thất bại. Tương tự, exception trong finally có thể che exception của `proceed`.

Mẫu cần dùng khi chỉ cleanup:

```java
Resource resource = acquire();
try {
    return proceed();
} finally {
    release(resource);
}
```

Đây là pseudocode Java minh họa, không có class `Resource`/hàm `acquire` cụ thể. Không thêm return vào finally; quản lý lỗi cleanup theo policy.

### Catch thay đổi hợp đồng

```aspectj
String around(): execution(String demo.Remote.fetch()) {
    try {
        return proceed();
    } catch (RuntimeException ex) {
        return "";
    }
}
```

Nếu caller cần biết truy vấn thất bại để retry, trả chuỗi rỗng có thể **nuốt lỗi** và gây xử lý sai ở nơi khác. Fallback chỉ nên dùng khi hợp đồng thực sự cho phép và phân biệt được kết quả hợp lệ với trạng thái lỗi.

Khi có `after throwing` ở chuỗi precedence thấp hơn, nó có thể đã chạy trước lúc outer around catch exception. Ngược lại, nếu outer around hoàn toàn không proceed, advice sâu hơn có thể không chạy. Đừng suy “catch đã nuốt mọi after throwing” nếu chưa biết chuỗi advice và thứ tự ưu tiên.

## 9. Truyền tham số vào `proceed(...)`

AspectJ code style cho phép thay giá trị được bind qua pointcut:

```aspectj
String around(String text):
        execution(String demo.Echo.echo(String)) && args(text) {
    String normalized = text.trim();
    return proceed(normalized);
}
```

Đây là snippet cần `demo.Echo.echo(String)`. Nếu thân method trả `"[" + text + "]"` và đầu vào là `"  hi  "`, kết quả của nhánh này có thể là `"[hi]"` vì around cung cấp giá trị mới khi tiếp tục.

Cần đặc biệt phân biệt:

- Với **AspectJ code style**, arguments của `proceed(...)` tương ứng những tham số hình thức/binding của around advice, **không mặc nhiên là danh sách toàn bộ tham số của method gốc**.
- Với **AspectJ annotation style**, phương thức `ProceedingJoinPoint.proceed(Object[])` có quy tắc sắp xếp tham số **khác**: khi bind `this`/`target` chúng có vị trí đặc thù, sau đó là các đối số join point theo thứ tự gốc. Không sao chép mảng theo thứ tự formals của code style một cách máy móc.
- Đổi binding sai kiểu có thể gây `ClassCastException` hoặc giá trị truyền xuống computation không đúng dự kiến.
- Khi chỉ gán lại biến local mà không truyền giá trị mới bằng `proceed(...)` phù hợp, không thể kết luận args phía trong đã đổi.
- Việc thay tham số còn có thể ảnh hưởng advice phía trong về validation, logging, identity và authorization; phải đảm bảo điều đó nằm trong hợp đồng.

Đừng coi `proceed(Object[])` là một lời gọi method tùy ý với bao nhiêu tham số cũng được.

## 10. Retry bằng nhiều `proceed()`: điều kiện cần

Một chính sách retry hợp lệ phải xác định rõ:

1. **Loại lỗi retry được:** timeout nhất thời, failure tạm thời theo nghiệp vụ, không phải mọi exception.
2. **Số lần tối đa:** ví dụ `maxAttempts=2` nghĩa là tổng số lần gọi phần trong tối đa hai, bao gồm attempt đầu.
3. **Idempotency/deduplication:** lần thử đầu có thể đã gây side effect dù trả lỗi; retry không được tạo tác động kép ngoài hợp đồng.
4. **Cleanup giữa các attempt:** mỗi lần thử có thể cần resource/connection/context mới; đừng dùng lại trạng thái đã hỏng.
5. **Cancel/interrupt/backoff:** thời gian chờ và interrupt phải được xử lý theo chính sách, nhất là trên thread pool.
6. **Báo lỗi cuối:** khi hết lượt retry, truyền exception cuối hoặc kết quả thay thế hợp đồng; không giả vờ thành công.
7. **Metrics:** phân biệt số *attempts* với số *logical operations*.

Snippet sau chỉ để minh họa hình thức retry tối đa hai lần, **không chạy độc lập nếu chưa có** `demo.RemoteReader.read()` và một aspect:

```aspectj
String around(): execution(String demo.RemoteReader.read()) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
        try {
            return proceed();
        } catch (IllegalStateException ex) {
            last = ex;
        }
    }
    throw last;
}
```

Nhánh retry trên chỉ dành cho `IllegalStateException`, không có nghĩa mọi lỗi loại này thật sự nên retry. Lỗi loại khác sẽ đi ra ngay. Nếu attempt đầu có side effect rồi throw, attempt sau có thể gây side effect lần thứ hai. Với lệnh ghi tiền hoặc tạo đơn hàng, phải có **idempotency key được hệ thống đích thực thi**, hoặc thiết kế nghiệp vụ phù hợp, trước khi dùng kiểu retry này.

Ví dụ nguy hiểm là gửi yêu cầu đến dịch vụ ngoài, dịch vụ đã xử lý thành công nhưng kết nối mất trước khi nhận response. Around advice bắt timeout rồi proceed lại; nếu phía đích không deduplicate, kết quả có thể là hai đơn hàng mặc dù caller chỉ gọi một lần.

## 11. Advice precedence: một lệnh ở ngoài, nhiều lần ở trong

Giả sử around A có precedence cao hơn around B, cả hai match cùng execution join point:

```text
caller
  → A around
       → A.proceed() (một lần)
            → B around
                 → B.proceed() (lần 1)
                      → method body
                 → B.proceed() (lần 2)
                      → method body
       ← A tiếp tục
```

A chỉ gọi proceed một lần nhưng method body có thể chạy hai lần. Số lượng `proceed()` trong **một** aspect không đủ để suy số body invocations khi có advice khác.

Với AspectJ, thứ tự giữa các aspect có thể được xác định bằng `declare precedence` hoặc các quy tắc khác liên quan tới kế thừa; nếu không xác định được precedence, không nên suy thứ tự từ tên file hay classpath. Một outer around không proceed có thể chặn các advice precedence thấp hơn. Một advice ở lớp trong còn có thể dùng around return fallback, thay đổi exception hoặc tự gọi proceed lặp.

**Cách kiểm chứng:** đặt counter ở ba tầng (outer around entry, inner around entry, method body), chạy một lời gọi cố định và đo riêng. Cần ghi cả join point kind/signature để tránh đếm hai join point khác nhau như một.

## 12. Nhầm `call` với `execution`

```aspectj
call(* demo.Job.process(..))
execution(* demo.Job.process(..))
```

Hai pointcut này chọn **khác loại join point**:

- `call` quan sát vị trí gọi method (call site); cần call site đó nằm trong phạm vi được weave.
- `execution` quan sát lúc method body bắt đầu; cần class chứa thân method được weave.
- Một lời gọi có thể đi qua cả call advice và execution advice nếu cả hai join point đều được dệt; các around advice thuộc **hai lớp join point** có thể lồng nhau.
- Với reflection, loader hoặc đường dispatch khác nhau, tập join point quan sát được phụ thuộc weaver và phạm vi bytecode đã dệt.

Vì thế hai dòng log “around entered” chưa chứng minh có hai lần `proceed`. Có thể là hai advice, hoặc một call join point cùng một execution join point. Ghi `thisJoinPointStaticPart.getKind()` và chữ ký từ `getSignature()` khi điều tra.

### Không gọi method gốc trực tiếp thay `proceed`

Đoạn nguy hiểm:

```aspectj
String around(demo.Job job):
        execution(String demo.Job.process()) && this(job) {
    return job.process();
}
```

Đây là snippet cần class `Job`; với execution advice đúng trên method ấy, `job.process()` có thể kích hoạt lại chính advice và tạo đệ quy vô hạn. Nó **không tương đương** `proceed()`. Muốn tiếp tục computation hiện tại, dùng cú pháp `proceed(...)` theo binding tương ứng.

Ngược lại, một method gốc **tự đệ quy** có thể tạo nhiều execution join point hợp lệ dù mỗi frame quanh chỉ proceed một lần. Phải phân biệt recurrence của nghiệp vụ và continuation bị gọi thừa trong một frame.

## 13. Sai thời điểm với bất đồng bộ

Một method có thể trả `CompletionStage<T>` hoặc future ngay, rồi công việc hoàn tất sau đó:

```text
t0: around bắt đầu
t1: proceed() trả một CompletionStage chưa complete
t2: around trả stage cho caller
t3: tác vụ bất đồng bộ hoàn tất
```

Nếu ghi “operation completed” ở `t2` thì đó chỉ là **completion của việc trả stage**, không phải completion của tác vụ ở `t3`. Đo thời gian quanh proceed cho **submission/return latency**; muốn đo **end-to-end completion** phải gắn observer vào completion theo API phù hợp, xử lý đúng exception/cancellation/context và quyết định có thay đổi stage trả cho caller hay không.

Không tùy tiện lưu một continuation và gọi `proceed` trễ trong callback/thread khác. Với API đồng bộ, caller cần return value hoặc exception theo thời gian gốc; chuyển việc tiếp tục sang thread khác có thể thay đổi hoàn toàn contract, khóa, context thread và thứ tự advice. Chuyển từ synchronous sang async là quyết định thiết kế API, không chỉ là di chuyển một dòng `proceed`.

## 14. Lỗi về return type, exception và side effect

Around advice có quyền trả kết quả thay thế nên phải giữ tính tương thích:

- Trả `null` khi caller cần primitive có thể gây `NullPointerException` qua unboxing.
- Trả kiểu sai có thể gây lỗi runtime/cast hoặc lỗi ngay khi compile.
- Trả default từ catch rộng có thể giấu lỗi gốc và làm mất thông tin retry.
- Một phép transformation trên return value phải xét trường hợp giá trị null.
- Exception checked phải phù hợp contract và giới hạn ngôn ngữ/pointcut, không tự tiện thay exception tùy ý.
- Với multiple proceed, việc “chỉ dùng kết quả lần cuối” **không xóa side effect** của các lần trước.
- Thực thi cleanup trong finally là đúng hướng, nhưng `return`/`throw` từ finally có thể che kết quả hoặc nguyên nhân ban đầu.

Phải kiểm tra cả **giá trị caller thấy** lẫn **trạng thái hệ thống sau lời gọi**, đặc biệt cho retry, cache và các thao tác ghi.

## 15. Kiểm thử đúng số lần và đúng thứ tự

Một test cho quanh advice không nên chỉ assert kết quả trả về. Cần quan sát:

| Kịch bản | Body invocations mong đợi | Assertion quan trọng |
| --- | ---: | --- |
| Cache miss | 1 | Giá trị thật, side effect một lần. |
| Cache hit hợp lệ | 0 | Giá trị cache đúng, không bỏ qua bước bắt buộc. |
| Validation fail | 0 | Exception đúng, state không đổi. |
| Success bình thường | 1 | Before/lock/start xảy ra trước body; cleanup/end sau. |
| Computation throw | 1 | Finally chạy; ngoại lệ tới caller đúng hợp đồng. |
| Retry lỗi tạm rồi thành công | Theo maxAttempts, có thể 2 | Attempts và side effect/idempotency đúng. |
| Hai around cùng match | Tùy precedence và proceed của tầng trong | Counter riêng cho advice và body. |
| Đệ quy | Theo số frame nghiệp vụ | Không nhầm recursion với double proceed. |
| Async return | Theo số lần submission hợp đồng | Phân biệt stage được tạo và đã complete. |

Ví dụ `Counter` ở mục 4 có thể dùng các assertion:

1. Sau `skip()`, `count() == 0`.
2. Sau `once()`, `count() == 1` và kết quả `"once-1"`.
3. Sau `twice()`, `count() == 3` và kết quả `"twice-2|twice-3"`.
4. Sau `fail()`, caller nhận `IllegalStateException("boom")` và `count() == 4`.
5. Thứ tự stdout như mục 4: “before” trước body, “after” chỉ khi return bình thường, “finally” ngay cả khi throw.

Trong ứng dụng thực, nên dùng bộ đếm ở **thân method** hoặc hệ thống thực hiện side effect; một counter chỉ nằm trong advice ngoài không chứng minh method body đã chạy bao nhiêu lần. Test cần chạy trên bytecode đã được weaving, với pointcut và precedence thực tế.

## 16. Quy trình chẩn đoán nhanh

1. **Xác định hợp đồng:** khi nào body phải chạy 0, 1 hoặc nhiều lần? Những nhánh cache/reject/retry nào được phép?
2. **Ghi join point thật:** loại `call`/`execution`, signature, class được dệt và đường gọi. Loại trừ chuyện một method bị quan sát tại hai join point.
3. **Vẽ chuỗi precedence:** outer/inner advice, vị trí `proceed()` của từng tầng, và nhánh return/throw.
4. **Đánh dấu các nhánh điều kiện:** `if/return/catch/finally/loop`, có nhánh nào thiếu proceed, chạy thừa hoặc nuốt lỗi?
5. **Đếm thân computation:** tăng counter ngay trong method body để tách lỗi advice entry khỏi double execution.
6. **Kiểm tra thứ tự bảo vệ:** validation và acquire lock trước proceed; result processing sau proceed thành công; cleanup trong finally.
7. **Phân tích idempotency:** nếu có retry, lần trước đã commit side effect chưa? Có deduplication thực sự không?
8. **Xét asynchronous boundary:** caller đang nhận kết quả cuối hay mới nhận future? Measurement và success log đang ở thời điểm nào?
9. **Sửa đúng nguyên nhân:** gọi proceed đúng số lần, đổi điểm đặt logic, giới hạn retry hoặc dùng before/after advice nếu không cần điều khiển continuation.
10. **Chạy hồi quy:** assert count, order, return, exception và side effect cho nhánh hợp lệ lẫn không hợp lệ.

Mẫu ghi lại bằng chứng:

```text
Join point: execution(String demo.Counter.twice())
Hợp đồng: thân phương thức chạy một lần
Around entries: 1
Proceed invocations: 2
Body entries: 2
State trước: 1
State sau: 3
Return: twice-2|twice-3
Nguyên nhân: proceed thứ hai tái thực thi computation
Sửa: lưu String result = proceed() rồi dùng lại result
Test: state chỉ tăng một đơn vị, không side effect lặp
```

## 17. Checklist phòng ngừa

- [ ] Around advice thật sự cần kiểm soát continuation; nếu chỉ log trước/sau, đã cân nhắc before/after.
- [ ] Số lần `proceed` trên từng nhánh được mô tả rõ (0, 1 hoặc N).
- [ ] Short-circuit trả kết quả thay thế hợp lệ và không bỏ các bước bắt buộc.
- [ ] Không gọi `proceed` lần hai chỉ để đọc lại kết quả cũ.
- [ ] Validation/context/lock/resource được thiết lập trước proceed khi cần bảo vệ computation.
- [ ] Cleanup/restore/end timing chạy trong finally ở đúng phạm vi.
- [ ] Không return từ finally để che lỗi của proceed.
- [ ] Catch không đổi loại lỗi hoặc fallback ngoài hợp đồng.
- [ ] Retry có bound, lỗi retry được định nghĩa, idempotency được kiểm chứng.
- [ ] Cách binding tham số `proceed(...)` phù hợp cú pháp AspectJ đang dùng.
- [ ] Đã xem precedence và phân biệt `call` với `execution`.
- [ ] Đã phân biệt completion của future với việc trả future.
- [ ] Test đo trực tiếp body count, thứ tự và side effect trên class đã weave.

**Kết luận:** `proceed()` là lệnh **tiếp tục phép tính bên trong join point**, không phải hàm đọc lại kết quả. Đặt đúng một lệnh trong một nhánh success bình thường giúp giữ hợp đồng một lần thực thi; bỏ qua hoặc lặp chỉ hợp lệ khi nghiệp vụ cho phép. Thứ tự của validation, lock, metric, exception và cleanup so với proceed quyết định tính đúng đắn của toàn bộ advice.

## Tài liệu tham khảo

- [AspectJ Programming Guide — Advice Semantics](https://eclipse.dev/aspectj/doc/released/progguide/semantics-advice.html): around advice, `proceed`, lời gọi tới advice có precedence thấp hơn.
- [AspectJ Programming Guide — Language Semantics](https://eclipse.dev/aspectj/doc/latest/progguide/semantics.html): tham số `proceed(...)` trong code style, thay giá trị và kiểu trả về.
- [AspectJ — Pointcuts and Advice](https://eclipse.dev/aspectj/doc/released/adk15notebook/ataspectj-pcadvice.html): khác biệt `ProceedingJoinPoint.proceed(Object[])` với code style.
- [Java SE 17 JLS — Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html): `try/catch/finally`, return và abrupt completion.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-005">Lưu trạng thái yêu cầu trong aspect dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Lưu trạng thái yêu cầu trong aspect dùng chung: sai lầm thường gặp trong AOP

## 1. Bài toán: một aspect được nhiều yêu cầu sử dụng

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **aspect** tập trung những hành vi áp dụng xuyên suốt nhiều vị trí trong chương trình, như ghi nhật ký, đo thời gian, tracing, kiểm tra điều kiện hoặc kiểm soát tài nguyên. **Pointcut** chọn các **join point**; **advice** là phần mã bổ sung được thực hiện tại các vị trí đó. Với **around advice**, mã có thể thực hiện việc chuẩn bị, gọi tiếp hành vi gốc, quan sát kết quả rồi dọn dẹp.

Một sai lầm nguy hiểm là đưa **trạng thái riêng của từng yêu cầu (request/invocation state)** vào một trường (field) của **aspect instance dùng chung**, chẳng hạn:

- ID yêu cầu, ID truy vết hoặc người dùng hiện tại.
- Thời điểm bắt đầu xử lý để đo latency.
- Tham số, kết quả, ngoại lệ hoặc thông tin phân quyền của lần gọi.
- Cờ đánh dấu đang ghi log, đang mở transaction hoặc đang thực hiện advice.
- Danh sách resource, buffer, span hoặc object dùng để theo dõi một yêu cầu.

Khi nhiều thread hoặc nhiều tác vụ đồng thời đi qua cùng một aspect instance, các trường có thể bị **ghi đè, đọc nhầm, phát sinh data race hoặc giữ object lâu hơn vòng đời yêu cầu**.

**Điểm cần xác định ngay:** AOP không quy định mọi aspect đều có duy nhất một instance. Mô hình khởi tạo có thể theo toàn ứng dụng, theo đối tượng đích, theo từng lần gọi, theo cấu hình weaving hoặc theo cơ chế khác. Sai lầm ở đây xuất hiện **khi trạng thái của từng lần gọi bị đặt vào một instance thực sự được chia sẻ bởi nhiều lần gọi**. Hãy kiểm tra vòng đời aspect trong công nghệ sử dụng, thay vì mặc định “aspect luôn singleton”.

### Mục tiêu sau bài học

Bạn sẽ biết cách phân biệt **cấu hình có thể chia sẻ** và **dữ liệu chỉ thuộc một lần gọi**; giải thích vì sao `volatile`, `synchronized` và các collection concurrent không tự giải quyết nhầm lẫn ownership; lựa chọn biến cục bộ, context theo phạm vi thực thi, `ThreadLocal` hoặc cơ chế truyền context tường minh; và viết kiểm thử đồng thời phát hiện lỗi rò rỉ dữ liệu giữa các yêu cầu.

## 2. Trạng thái chia sẻ khác trạng thái theo yêu cầu như thế nào?

Một instance aspect có thể được tái sử dụng hợp lệ. Vấn đề không nằm ở việc tái sử dụng aspect, mà ở **loại dữ liệu được gắn với vòng đời của nó**.

| Loại dữ liệu | Ví dụ | Vòng đời hợp lý |
| --- | --- | --- |
| Cấu hình không đổi | Tên thao tác, quy tắc pointcut, ngưỡng cấu hình, danh sách trường an toàn | Có thể lưu tại instance dùng chung nếu bất biến hoặc đồng bộ đúng |
| Bộ đếm tổng hợp | Số lần advice chạy, tổng số lỗi | Có thể chia sẻ qua cấu trúc concurrent/atomic, không chứa danh tính một request |
| Trạng thái một invocation | `startNanos`, `requestId`, tham số, kết quả, exception | Thuộc duy nhất một lần chạy advice |
| Context theo luồng | Correlation context, cờ chống đệ quy khi chạy đồng bộ | Chỉ dùng nếu thread ownership và cleanup được bảo đảm |
| Context đi qua nhiều thread | Trace context, deadline, thông tin tác vụ | Cần truyền tường minh hoặc abstraction có scope phù hợp |
| Tài nguyên theo yêu cầu | File tạm, span, buffer, permit, kết nối do advice mượn | Phải có owner và đường kết thúc rõ ràng |

Một trường `final` chứa cấu hình bất biến thường an toàn để đọc từ nhiều thread. Nhưng `final` trên một tham chiếu trỏ đến `HashMap` có thể thay đổi **không làm map bất biến hoặc thread-safe**. Tương tự, dùng `ConcurrentHashMap` đúng cách có thể bảo vệ thao tác cấu trúc của map, nhưng nếu dùng map để lưu vô hạn mọi request thì vẫn có nguy cơ rò rỉ bộ nhớ và lẫn thông tin.

## 3. Ví dụ sai: đưa request ID vào trường của aspect

Đây là mã giả minh họa một around advice trong aspect dùng chung; `proceed()` đại diện cho lời gọi tiếp tục đến logic gốc theo API AOP tương ứng:

```java
// Minh họa sai, không phải chương trình Java độc lập.
final class SharedRequestAdvice {
    private String currentRequestId;  // Trường thuộc instance dùng chung.

    Object around(String requestId) throws Throwable {
        currentRequestId = requestId;
        Object result = proceed();
        recordAudit(currentRequestId, result);
        return result;
    }
}
```

Giả sử hai yêu cầu A và B cùng đi vào một instance:

```text
Thời điểm    Thread A                         Thread B
----------------------------------------------------------------------
t1           currentRequestId = "A"
t2                                            currentRequestId = "B"
t3           proceed() hoàn tất
t4           recordAudit("B", resultOfA)       ← Sai yêu cầu!
t5                                            recordAudit("B", resultOfB)
```

Lỗi không yêu cầu JVM “hỏng bộ nhớ”. Nó phát sinh do hai lời gọi hợp lệ cùng dùng một vị trí lưu dữ liệu không phù hợp vòng đời. Kết quả có thể là log của A mang correlation ID của B, metric ghi nhầm tenant hoặc phản hồi của một người dùng bị liên kết với thông tin người dùng khác.

Nếu `currentRequestId` là trường thường, còn có thêm vấn đề về **data race và khả năng nhìn thấy dữ liệu** theo Java Memory Model. Nhưng ngay cả khi thay bằng:

```java
// Vẫn sai về ownership nếu state thuộc từng lời gọi.
private volatile String currentRequestId;
```

thì mỗi lần gọi vẫn ghi chung một ô trạng thái. `volatile` giúp thiết lập một số bảo đảm visibility/happens-before cho hoạt động đọc/ghi, **không tạo một biến request ID riêng cho từng lời gọi**.

Tương tự, thay trường bằng `AtomicReference<String>` chỉ làm cập nhật tham chiếu trở thành atomic; nó không bảo đảm lời gọi A luôn đọc ID của A nếu B cập nhật xen giữa.

## 4. Phân loại hậu quả

### 4.1. Dữ liệu sai hoặc rò rỉ giữa các yêu cầu

Trường chia sẻ lưu `requestId`, danh tính người dùng hoặc metadata phân quyền có thể gây cross-request contamination. Nguy cơ nghiêm trọng hơn khi advice dựa trên trường này để **ra quyết định**, ví dụ chọn tenant, kiểm tra giới hạn truy cập hoặc tìm dữ liệu của người dùng.

Đây vừa là lỗi đồng thời vừa là lỗi bảo mật và tính đúng đắn. Không nên xem đây đơn thuần là “log hơi sai”.

### 4.2. Đo latency sai

Ví dụ:

```java
// Minh họa sai, không phải chương trình độc lập.
private long startNanos;

Object around() throws Throwable {
    startNanos = System.nanoTime();
    try {
        return proceed();
    } finally {
        long elapsed = System.nanoTime() - startNanos;
        emitLatency(elapsed);
    }
}
```

Nếu A ghi thời điểm bắt đầu rồi B ghi đè `startNanos` trước khi A tính thời lượng, số đo của A sẽ sai. Thay `long` bằng một kiểu atomic hoặc `volatile` vẫn không sửa việc **dùng chung biến cho hai invocation khác nhau**.

### 4.3. Trạng thái trước và sau không còn khớp nhau

Một aspect có thể giữ `arguments` lúc vào phương thức để so sánh với `result` lúc thoát. Nếu giữ các giá trị này trong field dùng chung, advice sau có thể ghép tham số của request B với kết quả request A. Với logic quản lý resource, nhầm resource giữa hai lần gọi còn có thể dẫn tới đóng sai handle hoặc giải phóng nhầm permit.

### 4.4. Rò rỉ bộ nhớ

Nếu aspect giữ reference đến `request`, `response`, `args`, exception hoặc context sau khi lời gọi xong, object graph có thể tiếp tục reachable qua aspect instance lâu sống. Vì vậy GC chưa thể thu gom dù request đã kết thúc.

Một biến field duy nhất thường chỉ giữ *một* đồ thị object tại một thời điểm, nhưng vẫn có thể giữ đồ thị đó quá lâu. Một `Map<requestId, Context>` hoặc danh sách được tích lũy không giới hạn thì còn có thể tăng bộ nhớ tỷ lệ với số lần gọi.

### 4.5. Deadlock và suy giảm hiệu năng khi sửa sai bằng khóa quá rộng

Một phản ứng phổ biến là thêm `synchronized` cho toàn bộ around advice. Cách này có thể làm hai request không ghi đè lên nhau tại thời điểm đó, **nhưng lại tuần tự hóa mọi lời gọi dùng chung lock**. Nếu `proceed()` chứa I/O, gọi dịch vụ từ xa hoặc chờ tác vụ khác, contention và độ trễ có thể tăng mạnh; còn có nguy cơ deadlock khi luồng gọi lồng nhau giữ các khóa khác.

Giải pháp đúng thường là **không chia sẻ state của invocation**, thay vì khóa toàn bộ quá trình xử lý chỉ để bảo vệ một biến đáng lẽ là biến cục bộ.

## 5. Cách sửa ưu tiên: biến cục bộ trong around advice

Với lời gọi đồng bộ, biến cục bộ thường là lựa chọn đơn giản và rõ ràng nhất:

```java
// Mã giả, phụ thuộc cơ chế proceed của hệ thống AOP.
Object around(String requestId) throws Throwable {
    long startNanos = System.nanoTime();
    String correlationId = requestId;

    try {
        Object result = proceed();
        recordSuccess(correlationId, result);
        return result;
    } catch (Throwable error) {
        recordFailure(correlationId, error);
        throw error;
    } finally {
        recordLatency(correlationId, System.nanoTime() - startNanos);
    }
}
```

Mỗi invocation có một bộ local variables riêng, kể cả khi nhiều invocation chạy song song trên cùng một instance aspect. Không có field mutable cần tranh chấp cho `correlationId` hay `startNanos`.

Các điểm cần nhớ:

1. Một **reference cục bộ** vẫn có thể trỏ vào một object mutable được các thread khác chia sẻ. Local variable không tự làm object trở nên thread-safe; nó chỉ tách vị trí lưu tham chiếu theo invocation.
2. `requestId` phải lấy từ nguồn đáng tin và được kiểm tra phù hợp; không mặc định có thể đưa mọi ID/người dùng vào log.
3. Nếu telemetry có thể lỗi, cần chính sách bảo đảm sự cố logging không vô tình che ngoại lệ nghiệp vụ. Một `finally` chứa lệnh ném lỗi vẫn có thể thay thế kết quả/ngoại lệ ban đầu.
4. Nếu `proceed()` trả về một tác vụ bất đồng bộ, thời điểm **trả đối tượng đại diện cho kết quả** không nhất thiết là thời điểm **công việc hoàn tất**. Để đo thời lượng tác vụ, phải gắn cleanup với lifecycle của tác vụ đó.

Biến local phù hợp khi tất cả logic cần context nằm trong một dynamic call scope. Nếu nhiều method cần đọc cùng context mà không thể truyền qua tham số, cân nhắc công cụ truyền context có scope rõ ràng.

## 6. Ví dụ chạy được: tái hiện ghi đè state giữa hai request

Mô phỏng dưới đây dùng `java.lang.reflect.Proxy` để đặt một lớp advice trước một interface nghiệp vụ. Đây là **một minh họa của runtime interception**, không phải định nghĩa đầy đủ về pointcut hoặc weaving trong mọi hệ thống AOP.

Chương trình sử dụng `CountDownLatch` để chủ động sắp xếp hai lời gọi:

- A ghi `currentRequestId = "A"` rồi tạm chờ.
- B ghi `currentRequestId = "B"` sau khi A đã ghi.
- A và B cùng đọc field. Cả hai thấy `"B"` mặc dù A thuộc request khác.
- Bản sửa lưu ID của từng invocation vào **biến cục bộ**.

Tạo tệp `SharedAspectStateDemo.java`:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SharedAspectStateDemo {

    interface Operation {
        String handle(String requestId);
    }

    static final class BusinessOperation implements Operation {
        @Override
        public String handle(String requestId) {
            return "ok:" + requestId;
        }
    }

    // Sai: trạng thái invocation được giữ trong cùng một instance advice.
    static final class UnsafeAdvice implements InvocationHandler {
        private final Operation target;
        private volatile String currentRequestId;

        private final CountDownLatch aStored = new CountDownLatch(1);
        private final CountDownLatch bStored = new CountDownLatch(1);

        final Map<String, String> observed = new ConcurrentHashMap<>();

        UnsafeAdvice(Operation target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String requestId = (String) args[0];

            if ("A".equals(requestId)) {
                currentRequestId = requestId;
                aStored.countDown();
                bStored.await();
            } else if ("B".equals(requestId)) {
                aStored.await();
                currentRequestId = requestId;
                bStored.countDown();
            } else {
                throw new IllegalArgumentException("Unknown demo request");
            }

            // A đọc "B": field đã bị B ghi đè trong khi A đang chờ.
            observed.put(requestId, currentRequestId);
            return callOriginal(target, method, args);
        }
    }

    // Đúng: mỗi invocation dùng tham chiếu requestId cục bộ của mình.
    static final class SafeAdvice implements InvocationHandler {
        private final Operation target;
        final Map<String, String> observed = new ConcurrentHashMap<>();

        SafeAdvice(Operation target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String requestId = (String) args[0];

            // Mỗi lần gọi có requestId riêng, không ghi vào field chia sẻ.
            observed.put(requestId, requestId);
            return callOriginal(target, method, args);
        }
    }

    private static Object callOriginal(
            Operation target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static Operation wrap(
            Operation target, InvocationHandler advice) {
        return (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[]{Operation.class},
                advice
        );
    }

    private static String[] runTwoRequests(Operation operation)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = executor.submit(() -> operation.handle("A"));
            Future<String> b = executor.submit(() -> operation.handle("B"));

            return new String[]{
                    a.get(5, TimeUnit.SECONDS),
                    b.get(5, TimeUnit.SECONDS)
            };
        } finally {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        Operation target = new BusinessOperation();

        UnsafeAdvice unsafeAdvice = new UnsafeAdvice(target);
        Operation unsafe = wrap(target, unsafeAdvice);
        String[] businessResults = runTwoRequests(unsafe);

        SafeAdvice safeAdvice = new SafeAdvice(target);
        Operation safe = wrap(target, safeAdvice);
        runTwoRequests(safe);

        System.out.println("unsafe A observed="
                + unsafeAdvice.observed.get("A"));
        System.out.println("unsafe B observed="
                + unsafeAdvice.observed.get("B"));
        System.out.println("safe A observed="
                + safeAdvice.observed.get("A"));
        System.out.println("safe B observed="
                + safeAdvice.observed.get("B"));
        System.out.println("business results="
                + businessResults[0] + "," + businessResults[1]);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SharedAspectStateDemo.java
java SharedAspectStateDemo
```

Kết quả mong đợi khi chương trình chạy bình thường:

```text
unsafe A observed=B
unsafe B observed=B
safe A observed=A
safe B observed=B
business results=ok:A,ok:B
```

**Vì sao kết quả có tính xác định?** `aStored` buộc B chờ A lưu `"A"`; `bStored` buộc A chờ B lưu `"B"` trước khi A đọc. Sau đó cả hai đọc trường `volatile` đã được B cập nhật. Đây là tình huống có điều phối rõ ràng, không dựa vào việc “chạy nhiều lần may ra gặp race”. Hai request vẫn trả đúng kết quả nghiệp vụ, nhưng bản ghi quan sát của A đã sai; đây chính là kiểu lỗi có thể ẩn rất lâu trong hệ thống thật.

Map `observed` trong demo được dùng như **bộ sưu tập kết quả kiểm thử**, có hai key cố định. Nó không phải lời khuyên lưu mọi request vĩnh viễn trong map của aspect thật: với request ID liên tục mới, map không có eviction có thể tăng không giới hạn.

**Giới hạn của ví dụ:** `Proxy` chỉ áp dụng cho lời gọi đi qua proxy interface này; một hệ thống weave trực tiếp vào bytecode có thể có các join point khác. Quy tắc tránh state dùng chung vẫn đúng bất kể loại interception nếu instance advice được tái sử dụng. Các timeout 5 giây là guard cho demo, không phải bảo đảm hiệu năng ở mọi môi trường.

## 7. Vì sao khóa, volatile và atomic không thay thế được scope?

Bốn kỹ thuật thường bị nhầm lẫn:

| Cách xử lý | Nó bảo đảm điều gì? | Tại sao chưa đủ cho request state? |
| --- | --- | --- |
| `volatile` | Visibility và quy tắc ordering nhất định cho đọc/ghi biến | A vẫn có thể thấy giá trị B vừa ghi |
| `AtomicReference` | Thao tác atomic trên **một** tham chiếu dùng chung | Tham chiếu vẫn thuộc một chỗ lưu cho nhiều request |
| `synchronized` hoặc lock | Bảo vệ critical section theo cùng cơ chế khóa | Muốn A giữ ID của A qua toàn bộ `proceed()` có thể phải khóa cả nghiệp vụ, gây contention và deadlock |
| Concurrent collection | An toàn ở các thao tác được công cụ bảo đảm trên cấu trúc dữ liệu | Không tự đặt hạn dùng, ownership, quan hệ giữa record và invocation |

Một lock bao quanh chỉ lệnh ghi `currentRequestId` không bảo đảm A đọc được ID A sau khi B cập nhật. Muốn dùng cấu trúc dùng chung một cách đúng đắn cần **định danh chính xác mỗi invocation, quản lý vòng đời và chỉ giữ dữ liệu cần thiết**; nhưng với một ID chỉ được dùng tại entry/exit, local variable ngắn gọn hơn nhiều.

Khi có trạng thái tổng hợp thực sự được phép chia sẻ (ví dụ tổng số lần advice chạy), các bộ đếm atomic hoặc loại concurrent phù hợp là hợp lý:

```java
// Minh họa: đây là aggregate metric, không phải state một request.
private final java.util.concurrent.atomic.LongAdder callCount =
        new java.util.concurrent.atomic.LongAdder();

void beforeInvocation() {
    callCount.increment();
}
```

`LongAdder` phù hợp cho bộ đếm cộng dồn trong nhiều kịch bản cạnh tranh; khi đọc `sum()` đồng thời với cập nhật, kết quả là snapshot không nhất thiết atomic so với tất cả cập nhật đang diễn ra. Nó không thích hợp để lưu correlation ID hay identity của request.

## 8. Khi cần context ở nhiều lớp lời gọi: ThreadLocal có giới hạn rõ ràng

Trong một call stack đồng bộ, đôi khi nhiều lời gọi sâu bên trong cần đọc cùng correlation context mà không truyền tham số qua từng hàm. `ThreadLocal` gắn một giá trị với từng thread và có thể giúp tránh chia sẻ giá trị giữa các thread *khi được quản lý đúng*.

**Mã sai:**

```java
// Minh họa, không phải chương trình Java độc lập.
static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

Object around(RequestContext request) throws Throwable {
    CURRENT.set(request);
    return proceed(); // Nếu lỗi hoặc thread pool tái sử dụng, context còn sót.
}
```

Nếu dùng thread pool, thread có thể xử lý request khác sau đó. Vì thiếu cleanup, context cũ có thể bị giữ lâu, lộ sang xử lý sau hoặc làm object graph không thể được GC thu gom theo kỳ vọng.

**Mẫu quản lý scope tốt hơn:**

```java
// Minh họa, không phải chương trình Java độc lập.
private static final ThreadLocal<RequestContext> CURRENT =
        new ThreadLocal<>();

Object around(RequestContext current) throws Throwable {
    RequestContext previous = CURRENT.get();
    CURRENT.set(current);
    try {
        return proceed();
    } finally {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
```

Vì sao phải lưu `previous`? Nếu advice có thể lồng nhau trên cùng thread, một scope bên trong cần **phục hồi context của scope ngoài**, thay vì xóa bỏ nó. `finally` được dùng để xử lý cả đường trả kết quả và đường ngoại lệ.

Điều kiện để cách này phù hợp:

1. Nhiệm vụ được thực hiện trong thread có vòng đời rõ ràng.
2. Việc thiết lập và phục hồi được cân bằng ở **mọi đường kết thúc**, kể cả failure.
3. Context nhỏ, không giữ các object khổng lồ hoặc bí mật không cần thiết.
4. Một context không bị dùng làm căn cứ phân quyền vượt quá thời điểm hoặc nơi đã được kiểm chứng.
5. Nếu công việc chuyển thread, có cơ chế truyền context riêng; `ThreadLocal` thông thường **không tự động đi theo logical request**.

**Tránh lạm dụng `InheritableThreadLocal`:** nó không phải cơ chế truyền context request đáng tin cậy qua executor pool; thread đã tồn tại có thể không nhận giá trị mới và thời điểm sao chép không tương ứng vòng đời task. Một số runtime cung cấp cơ chế structured context hoặc scoped context riêng; cần đối chiếu phiên bản Java và đặc tính công nghệ thay vì giả định chúng giống `ThreadLocal`.

## 9. Yêu cầu bất đồng bộ: vòng đời logic dài hơn lời gọi phương thức

Đây là trường hợp dễ bị bỏ sót khi đã quen với advice đồng bộ.

Giả sử `proceed()` trả về `CompletionStage<T>`. Method có thể trả về ngay trong khi phép tính còn chạy ở thread khác. Nếu quanh advice chỉ lưu thời điểm bắt đầu và ghi “hoàn tất” trong `finally` sau khi `proceed()` trả về stage, số đo là **thời gian tạo stage**, chưa chắc là thời gian công việc thật sự hoàn tất.

Mô hình thời gian:

```text
Thread gọi
  around advice bắt đầu
        |
        +--> proceed() trả CompletionStage [chưa xong]
        |
  around advice thoát -------------------------------+
                                                       |
Worker thread                                          |
  thực thi công việc -----------------------------------+
  stage hoàn tất
        |
        +--> callback ghi nhận kết quả/cleanup
```

Khi muốn theo dõi *completion* của tác vụ, context theo invocation phải được gắn đúng vòng đời bất đồng bộ. Ví dụ ở mức ý tưởng:

```java
// Minh họa, không phải chương trình độc lập.
// Hàm delegate() được giả định trả CompletionStage<Result>.
long startNanos = System.nanoTime();
String localRequestId = requestId;

CompletionStage<Result> stage = delegate();

return stage.whenComplete((result, failure) -> {
    long elapsed = System.nanoTime() - startNanos;
    recordOutcome(localRequestId, elapsed, failure);
});
```

**Những giới hạn quan trọng của minh họa:**

- Callback `whenComplete` có thể chạy trên thread hoàn tất stage hoặc thread khác, tùy completion state và thư viện. Không đọc `ThreadLocal` của thread ban đầu bên trong callback rồi tưởng nó vẫn đại diện cho request.
- `whenComplete` tạo một stage phụ thuộc. Nếu callback ném ngoại lệ, cách stage kết thúc có thể bị ảnh hưởng theo hợp đồng `CompletionStage`. Logger/metric recorder phải có chính sách lỗi rõ ràng.
- Nếu lời gọi tạo stage thất bại **trước khi trả stage**, advice cần xử lý đường ngoại lệ đồng bộ.
- Nếu một thao tác bị hủy, timeout hoặc không bao giờ hoàn tất, cần thiết kế hợp đồng cleanup/deadline và tránh giữ state vô hạn.
- Một closure callback capture request object lớn có thể giữ nó trong toàn bộ thời gian stage pending. Chỉ capture thông tin tối thiểu, bất biến và được phép sử dụng.
- Không giả định `CompletableFuture.cancel(...)` sẽ tự dừng thread hoặc đóng mọi resource; quản lý cancellation phụ thuộc hợp đồng của công việc thực tế.

Nếu nhiều request cùng chạy, **context theo logical task** là cách mô tả đúng hơn context “mỗi thread” trong hệ thống bất đồng bộ. Truyền context qua tham số hoặc một immutable context object thường dễ kiểm tra ownership hơn.

## 10. Vòng đời, phạm vi aspect và quản lý trạng thái đúng chỗ

Trước khi chọn nơi lưu state, phân biệt:

**Per-invocation:** dữ liệu có ý nghĩa duy nhất trong một lần lời gọi, như `startNanos` hoặc `requestId`. Nên đặt trong biến cục bộ advice hoặc một context riêng được truyền qua các bước liên quan.

**Per-target/per-object:** dữ liệu thuộc về một đối tượng nghiệp vụ cụ thể. Có thể đặt ở owner của đối tượng đó hoặc mô hình aspect có vòng đời gắn với đối tượng khi công nghệ hỗ trợ. Nhưng nhiều thread có thể đồng thời sử dụng **cùng đối tượng đích**, nên per-target không tự đồng nghĩa thread-safe.

**Per-thread:** chỉ hợp lý khi ngữ nghĩa thực sự theo thread và cleanup chuẩn. Một request chuyển qua hai thread không còn trùng một per-thread scope; hai request chạy nối tiếp trên một thread cũng không được chia sẻ ngữ cảnh.

**Per-application/per-aspect-instance:** phù hợp cho configuration không đổi, bộ đếm tổng hợp, cache có chính sách và những resource được chia sẻ có owner rõ ràng. Không phù hợp cho field `currentUser`, `currentTenant` hoặc `requestResult` dùng như “biến hiện tại”.

**Per-transaction/per-operation:** đôi khi logical operation kéo dài qua nhiều lời gọi lồng nhau. Cần một context độc lập có ID, lifecycle và owner rõ ràng, thay vì dựa vào field singleton. Nếu nhiều operations có thể overlap, cơ chế lưu phải tách từng operation.

Một cache hoặc registry tập trung đôi khi là yêu cầu thực sự. Khi đó cần policy gồm: key ổn định đúng identity, giới hạn số entry, TTL hoặc signal hoàn tất để xóa, xử lý cancel/failure, không chứa bí mật thô, và monitoring retained memory. Đừng biến map không giới hạn thành giải pháp mặc định cho vấn đề chia sẻ state.

## 11. Reentrancy: lời gọi lồng nhau ngay trong cùng một thread

Lỗi tương tự có thể xảy ra **không cần hai thread** nếu aspect được gọi lồng nhau và ghi state vào cùng field:

```text
Advice outer: currentMethod = "outer"
  → proceed(outer)
      → gọi inner
          → Advice inner: currentMethod = "inner"
          → proceed(inner), rồi trả về
  → Advice outer đọc currentMethod = "inner"  ← Sai!
```

`synchronized` trên method advice có thể **không giúp**, vì một lock reentrant cho phép cùng thread vào lại và thay đổi field. Trường hợp này củng cố nguyên tắc: **request state phải gắn với invocation, không gắn với thread hoặc instance một cách tùy tiện**.

Nếu buộc dùng ngữ cảnh theo thread, cấu trúc stack có push/pop cân bằng và `finally` có thể đại diện cho nested scopes; nhưng làm thế tăng độ phức tạp và vẫn phải giải quyết chuyển thread. Nếu chỉ cần lưu thông tin cho đoạn advice đang chạy, dùng local variables vẫn tốt hơn.

## 12. Cảnh báo khi lưu context vào map dùng chung

Một số hệ thống lưu:

```java
// Mã minh họa: cần policy vòng đời và giới hạn trước khi dùng thật.
private final Map<String, RequestContext> contexts =
        new ConcurrentHashMap<>();
```

Cách này **không mặc nhiên sai** nếu thực sự cần tra cứu context theo ID qua nhiều thành phần, nhưng khác hẳn việc dùng `currentContext`. Để an toàn:

1. Dùng key không va chạm trong phạm vi cần bảo đảm và xác nhận quyền truy cập trước khi trả context.
2. Xóa entry khi request hoàn tất, bị lỗi, hủy hoặc hết hạn.
3. Có giới hạn capacity và giám sát số entry, TTL, eviction, tỷ lệ miss.
4. Không chứa trực tiếp token, mật khẩu hoặc request body nếu không cần.
5. Không giả định mọi thao tác nhiều bước trên concurrent map là một giao dịch nguyên tử; dùng API atomic thích hợp hoặc thiết kế trạng thái rõ ràng.
6. Kiểm tra tình huống ID được tái sử dụng, retry và nhiều request đồng thời.
7. Phân biệt completion của lời gọi đồng bộ với completion thực sự của tác vụ bất đồng bộ.

Một `WeakHashMap` cũng không thay thế ownership: key có thể được giữ sống ở nơi khác, value có thể tham chiếu ngược key, và behavior/đồng bộ có thể không đáp ứng yêu cầu. Chính sách giải phóng theo lifecycle thường rõ ràng và đáng kiểm chứng hơn.

## 13. Hậu quả bảo mật của việc lẫn context giữa request

Đặt dữ liệu nhạy cảm trong field chung của aspect có thể làm lỗi phân quyền dễ xuất hiện:

- Request A thuộc tenant X, nhưng advice đọc tenant Y từ request B.
- Log hoặc trace của người dùng A mang ID người dùng B.
- Bộ điều tiết/throttle ghi nhận sai chủ thể, làm một người dùng ảnh hưởng người khác.
- Một permission decision nhận identity không tương ứng với lần gọi gốc.
- Một request đã kết thúc vẫn để lại reference tới credential, token hoặc payload trong instance dài hạn.

Khi thiết kế aspect liên quan bảo mật, **lấy thông tin từ nguồn đã xác thực trong đúng invocation** và không dùng shared mutable field làm nguồn chân lý. Context bất biến hoặc truyền tường minh giúp việc kiểm tra dễ hơn, nhưng bản thân đối tượng bất biến không chứng minh dữ liệu là đáng tin: cần kiểm soát nguồn, phạm vi, quyền và thời điểm hợp lệ.

Ngoài ra, hãy tránh log toàn bộ context “để dễ debug”: có thể vô tình tiết lộ giá trị nhạy cảm. Chỉ chọn những trường an toàn được cho phép và kiểm soát thời gian lưu trữ.

## 14. Kiểm thử phát hiện lỗi đồng thời và rò rỉ state

### 14.1. Tạo lịch xen kẽ có kiểm soát

Một test chạy hai thread nhưng không có phối hợp có thể không gặp race trong hàng nghìn lần. Với lỗi đã biết, sử dụng `CountDownLatch`, barrier hoặc các điểm dừng test để cố ý tạo thứ tự:

```text
A: ghi state của A → chờ
B: chờ A ghi → ghi state của B → cho phép A tiếp tục
A: đọc state
Test: A phải đọc A; nếu đọc B thì FAIL.
```

Bài demo trước sử dụng đúng chiến lược này. Trong test thực tế, thêm timeout cho chờ latch/future để tránh test treo khi code thay đổi, đồng thời xác nhận mọi task hoàn tất hoặc được hủy theo chính sách của test.

### 14.2. Kiểm thử isolation dưới concurrency

Chạy nhiều lời gọi độc lập có định danh khác nhau và bảo đảm:

- Mỗi bản ghi audit/metric/trace chứa identity của **chính invocation đó**.
- Không có request kết quả A bị ghép tham số hoặc exception của request B.
- Kết quả vẫn đúng khi thứ tự hoàn thành khác thứ tự bắt đầu.
- Không có lỗi khi nhiều thread gọi cùng aspect instance hoặc cùng target object.
- Advice lồng nhau phục hồi đúng context của scope bên ngoài.
- Aspect không bỏ qua cleanup trên đường exception, timeout, cancellation.

### 14.3. Kiểm thử thread pool và bất đồng bộ

Với executor có ít thread tái sử dụng:

1. Task 1 đặt context rồi kết thúc cả theo success/failure.
2. Task 2 chạy trên cùng worker và không được nhìn thấy context cũ.
3. Nếu task 2 có context mới, phải thấy đúng context mới.
4. Nếu code truyền task sang thread khác, context cần được truyền/chỉ định tại đúng boundary.
5. Sau khi chuỗi task kết thúc, kiểm tra không còn resource hoặc object lớn bị giữ qua `ThreadLocal`/queue.

Đặc biệt test callback chạy **sau khi around advice đồng bộ đã thoát**; đây là nơi mô hình vòng đời không đúng thường lộ ra.

### 14.4. Theo dõi bộ nhớ và hiệu năng

Sau kiểm thử tải dài hơn, quan sát live heap sau GC, allocation rate, số task tồn đọng, thread pool, queue depth và kích thước registry/context map. Nếu nghi leak, phân tích heap dump theo GC roots và đường retained references: tìm aspect instance, static field, `ThreadLocalMap`, lambda/callback và map giữ `RequestContext`.

Việc sử dụng heap dump, profiling hoặc logging nặng trên hệ thống đang phục vụ yêu cầu có thể có chi phí đáng kể và tạo dữ liệu chứa thông tin nhạy cảm. Chọn quy trình phù hợp và không dựa vào một ảnh chụp bộ nhớ để kết luận leak.

### 14.5. Không dừng ở việc test trả về đúng kết quả

Một aspect logging có thể ghi sai ID nhưng **không thay đổi return value**, giống ví dụ `SharedAspectStateDemo`. Vì vậy test cần kiểm tra **observable side effects** do advice tạo ra, không chỉ hàm nghiệp vụ vẫn trả đúng dữ liệu.

## 15. Danh sách sai lầm phổ biến và cách thay thế

| Sai lầm | Vì sao sai | Hướng xử lý |
| --- | --- | --- |
| Trường `currentRequest` trong aspect dùng chung | Lẫn state giữa invocation, giữ tham chiếu lâu | Local variable hoặc context theo invocation |
| Trường `startTime` để đo mọi lời gọi | Bị lời gọi khác ghi đè | `long startNanos` cục bộ |
| Thêm `volatile` hoặc `AtomicReference` vào field request | Visibility/atomicity không sửa ownership | Tách state cho từng invocation |
| `synchronized` bao quanh toàn bộ `proceed()` | Tuần tự hóa công việc, contention lớn | Không chia sẻ request state; chỉ lock trạng thái tổng hợp thật cần |
| Dùng `ThreadLocal` nhưng không cleanup | Thread pool giữ dữ liệu qua request khác | `try/finally`, restore previous hoặc remove |
| Coi `ThreadLocal` là context bất đồng bộ | Logical request có thể đổi thread | Truyền context theo task hoặc cơ chế scope phù hợp |
| Ghi end-time ở `finally` khi nhận `CompletionStage` | Chỉ đo thời điểm trả stage | Theo dõi completion/cancel theo lifecycle thật |
| Lưu vô hạn `requestId → Context` | Heap tăng và lưu dữ liệu quá hạn | Dùng giới hạn, TTL và xóa theo completion |
| Dùng state aspect làm căn cứ phân quyền | Dữ liệu có thể bị lẫn request | Lấy identity đã xác thực của invocation hiện tại |
| Chỉ test single-thread | Race hoặc nesting không xuất hiện | Deterministic concurrency test và test reentrancy |
| Ghi toàn bộ args/context để debug | Giữ reference, lộ bí mật, tốn tài nguyên | Bản tóm tắt an toàn, không giữ object gốc |

## 16. Checklist khi review một aspect dùng chung

- [ ] Đã xác minh mô hình khởi tạo aspect và khả năng nhiều invocation dùng chung một instance?
- [ ] Field có thuộc một request/invocation hay thuộc cấu hình/aggregate được phép chia sẻ?
- [ ] Nếu bỏ một request, field của aspect có còn giữ reference đến object của request đó?
- [ ] `requestId`, user/tenant identity, start time, result, exception đã là local hoặc context scope phù hợp?
- [ ] Có data race, reentrancy hoặc việc lời gọi khác ghi đè field không?
- [ ] Có dùng `volatile`/atomic/lock để che lỗi state bị đặt sai vòng đời không?
- [ ] Nếu dùng `ThreadLocal`, mọi đường success/failure/nested đều phục hồi hoặc dọn đúng không?
- [ ] Nếu công việc bất đồng bộ, context/cleanup theo đúng logical completion chưa?
- [ ] Nếu dùng map/queue, capacity, TTL, cancellation cleanup và retained references được quản lý chưa?
- [ ] Event quan sát có đúng ID của invocation và không lẫn bí mật giữa các người dùng?
- [ ] Kiểm thử có trường hợp nhiều thread, nested advice và thứ tự hoàn tất đảo ngược?
- [ ] Việc sửa lỗi bảo toàn kết quả và ngoại lệ nghiệp vụ?

## Kết luận

**Aspect được chia sẻ không đồng nghĩa trạng thái của từng yêu cầu có thể chia sẻ.** Một field như `currentRequestId`, `currentUser` hoặc `startNanos` là nơi lưu không phù hợp cho nhiều invocation đồng thời, kể cả khi dùng `volatile` hoặc atomic reference. Lỗi có thể xuất hiện cả với lời gọi lồng nhau trên cùng thread.

Với lời gọi đồng bộ, **biến cục bộ của advice** là phương án ưu tiên. Khi phải truyền context qua nhiều lớp hoặc nhiều thread, cần một cơ chế có **identity, owner, phạm vi sống và cleanup rõ ràng**. Kiểm thử nên cố ý tạo interleaving, đo cả side effects của advice và kiểm tra rằng request đã kết thúc không tiếp tục bị giữ trong state lâu sống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-006">Không xác định thứ tự cho các aspect có phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: Không xác định thứ tự cho các aspect có phụ thuộc trong AOP

## 1. Vì sao thứ tự aspect là một phần của tính đúng?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một **aspect** đóng gói hành vi xuyên suốt như kiểm tra đầu vào, kiểm toán, bảo vệ truy cập, đo lường hoặc quản lý vòng đời thao tác. **Pointcut** chọn các **join point**, còn **advice** thực thi trước, sau hoặc bao quanh phép tính tại join point được chọn. Khi **nhiều advice từ nhiều aspect cùng khớp một join point**, chúng không chỉ là những “hàm chạy cùng lúc”: lời gọi thường có một thứ tự điều khiển cụ thể.

Sai lầm **“không xác định thứ tự cho các aspect có phụ thuộc”** xuất hiện khi thiết kế ngầm giả định aspect A phải chạy trước hoặc bao ngoài aspect B, nhưng mã/cấu hình không biểu đạt quan hệ này. Trình dệt (weaver), classpath, cách sắp xếp mã hoặc phiên bản công cụ có thể dẫn đến một thứ tự khác kỳ vọng. Kể cả khi quan sát thấy thứ tự ổn định trong nhiều lần thử, đó chưa phải là **hợp đồng ngữ nghĩa** mà hệ thống được phép dựa vào.

Ví dụ điển hình:

- **Kiểm toán và xác thực:** nhật ký cần ghi cả yêu cầu bị từ chối. Nếu xác thực từ chối trước khi advice kiểm toán được vào, yêu cầu thất bại không được ghi.
- **Chuẩn hóa và kiểm tra:** kiểm tra yêu cầu nhận dữ liệu đã chuẩn hóa; đảo thứ tự có thể dẫn đến lỗi giả hoặc bỏ sót lỗi.
- **Đo lường và cache:** cần phân biệt đo toàn bộ đường request kể cả cache hit hay chỉ đo thân phương thức thật sự chạy.
- **Transaction và kiểm toán kết quả:** kiểm toán “đã commit” khi nghiệp vụ mới return nhưng commit thực sự có thể thất bại sau đó là sai ngữ nghĩa; vị trí advice và ranh giới hoàn tất cần đúng.
- **Retry và tính idempotent:** retry bao quanh kiểm toán có thể ghi nhiều bản ghi cho một yêu cầu; kiểm toán bao quanh retry có thể ghi một bản ghi tổng thể. Không có thứ tự nào luôn đúng cho mọi nghiệp vụ.

Đây là bài về **AOP nói chung**, có ví dụ ngôn ngữ **AspectJ code-style** để minh họa một cơ chế xác định precedence rõ ràng. Các công cụ AOP khác có thể đặt tên/khai báo thứ tự khác; hãy dựa trên hợp đồng của công cụ đang sử dụng.

## 2. Phân biệt thứ tự áp dụng, thứ tự chạy và quan hệ phụ thuộc

Ba khái niệm dễ bị lẫn:

1. **Thứ tự precedence (độ ưu tiên):** quan hệ quyết định advice nào bao ngoài hoặc được xét trước advice khác khi cùng khớp một join point.
2. **Thứ tự thực thi thực tế:** thứ tự các lệnh trong advice/body/exception path xuất hiện khi chạy, phụ thuộc loại advice, nhánh, `proceed` và việc có exception.
3. **Quan hệ phụ thuộc nghiệp vụ:** một bước chỉ hợp lệ sau bước khác, hoặc cần bao trọn bước khác. Đây là thứ ta phải đặc tả trước khi chọn cơ chế precedence.

Một precedence xác định không tự chứng minh hành vi đúng: **nếu cấu hình ưu tiên được khai báo theo thứ tự sai**, ứng dụng vẫn sai nhưng sai một cách nhất quán. Để tránh điều đó, cần viết hợp đồng bằng ngôn ngữ nghiệp vụ:

- “Audit phải thấy cả thất bại từ Validation.”
- “Validation phải chạy trước Metrics bên trong.”
- “Metrics chỉ đo đường nghiệp vụ được phép thực thi.”
- “Nếu Validation từ chối, thân nghiệp vụ không được chạy.”
- “Khi nội bộ ném exception, bước bao ngoài phải dọn dẹp và ghi lại kết quả lỗi.”

Sau đó mới thiết kế dạng advice và precedence đáp ứng từng yêu cầu.

### 2.1. Khi nào các aspect thực sự có phụ thuộc?

Không phải mọi cặp aspect đều cần ép thứ tự. Hai aspect có thể độc lập nếu chúng không cùng chọn một join point, hoặc nếu hoán đổi vị trí không làm thay đổi dữ liệu, side effect, kết quả, exception, thời điểm xử lý hay dữ liệu giám sát cần quan sát.

Tuy vậy, nhiều tác vụ tưởng độc lập lại không giao hoán:

```text
normalize -> validate  !=  validate -> normalize
authorize -> execute   !=  execute -> authorize
retry -> audit         !=  audit -> retry
decrypt -> inspect     !=  inspect -> decrypt
```

Ký hiệu `!=` ở đây nói rằng **không thể mặc định tương đương**; với một số đầu vào đặc biệt kết quả có thể trùng. Hãy kiểm thử ngữ nghĩa thay vì dựa vào trực giác “cả hai advice đều chỉ là before”.

## 3. Các loại advice và tác động của precedence

Trong mô hình AspectJ, độ ưu tiên cao hơn được đặt ở **phía ngoài** khi dựng chuỗi áp dụng advice tại join point. Tác động quan sát tùy loại advice:

| Advice | Khi có ưu tiên cao hơn | Ý nghĩa cần nhớ |
| --- | --- | --- |
| `before` | Thực thi trước advice có ưu tiên thấp hơn nếu đường đi tiếp diễn | Nếu ném exception có thể chặn advice phía trong và thân gốc |
| `around` | Đi vào trước, có thể gọi `proceed(...)` để vào advice thấp hơn/thân gốc | Có thể bỏ qua, thay tham số/kết quả hoặc gọi tiếp nhiều lần |
| `after returning` | Bao ngoài phép tính phía trong, body chạy khi phần phía trong hoàn thành bình thường | Trên đường trở ra, tầng ngoài chạy sau tầng trong |
| `after throwing` | Body chạy khi phần phía trong ném lỗi phù hợp | Một tầng trong có thể chuyển exception thành kết quả tùy thiết kế |
| `after` (finally) | Body chạy khi phần phía trong kết thúc theo hai đường | Tầng ngoài chạy khi unwind trở ra, kể cả lỗi |

Một hình dung hữu ích cho hai `around` có thứ tự `Outer` cao hơn `Inner`:

```text
Caller
  |
  v
Outer: enter
  |
  +--> proceed Outer
           |
           v
       Inner: enter
           |
           +--> proceed Inner --> original body
           |
       Inner: exit
  |
Outer: exit
  |
Caller
```

Vì vậy, **ưu tiên cao nhất không có nghĩa advice đó luôn in log đầu tiên và cũng luôn in log kết thúc đầu tiên**. Tầng có độ ưu tiên cao đi vào trước nhưng khi hoàn tất bình thường sẽ rời ra sau. Điều này rất quan trọng với `around` và `after`.

Nếu một `around` không gọi `proceed`, advice ưu tiên thấp hơn và thân gốc ở phía trong có thể **không được chạy**. Một `before` ném exception cũng có thể ngăn các tầng sau chạy. Khi xem log chỉ từ thành công, bạn có thể bỏ sót hành vi này trên đường lỗi.

## 4. Quy tắc precedence trong AspectJ code-style

Để có ví dụ cụ thể, AspectJ định nghĩa các quy tắc cho advice tại **cùng một join point**:

1. Nếu có `declare precedence` và aspect A được liệt kê trước B, **advice của A có precedence cao hơn** advice của B.
2. Nếu không có khai báo có hiệu lực mà A là **subaspect** của B, advice định nghĩa trong A có precedence cao hơn advice định nghĩa trong B.
3. Nếu các advice ở **hai aspect khác nhau**, không có quan hệ precedence được xác định từ các quy tắc trên, thì **thứ tự giữa chúng không được định nghĩa**. Không nên dựa vào thứ tự tên file, compiler input, đường classpath hay lần chạy quan sát được.
4. Nếu advice cùng nằm trong **một aspect**, AspectJ có quy tắc dựa trên thứ tự khai báo: nếu một trong hai là `after` thì advice xuất hiện **sau** có precedence cao hơn; trong các trường hợp còn lại, advice xuất hiện **trước** có precedence cao hơn. Những tình huống tạo vòng thứ tự không hợp lệ có thể làm compiler báo lỗi.

Cú pháp khai báo:

```aspectj
declare precedence: FirstAspect, SecondAspect, ThirdAspect;
```

Một aspect riêng có thể quản lý quy tắc:

```aspectj
public aspect AspectOrder {
    declare precedence: AuditAspect, ValidationAspect, MetricsAspect;
}
```

Ý nghĩa: khi cùng match join point, `AuditAspect` ở ngoài `ValidationAspect`, và `ValidationAspect` có precedence cao hơn `MetricsAspect`. Khai báo precedence là ràng buộc **về advice**, không phải lời bảo đảm tất cả advice luôn được gọi trong mỗi đường chạy: pointcut và nhánh `proceed` vẫn quyết định mức độ thực thi.

### 4.1. Type pattern, wildcard và kế thừa

AspectJ hỗ trợ `TypePatternList` trong `declare precedence`, chẳng hạn:

```aspectj
declare precedence: *..*Security*, TracingAspect, *;
```

Ý nghĩa khái quát: các aspect khớp nhóm tên Security có precedence cao, tiếp theo `TracingAspect`, sau đó nhóm aspect còn lại. Trong một danh sách precedence, wildcard `*` dùng cho “các kiểu không khớp pattern nào khác” chỉ được xuất hiện **tối đa một lần** theo cú pháp được hỗ trợ.

Phải kiểm tra **tên kiểu cụ thể**, package và aspect kế thừa mà pattern thực sự match. Với aspect trừu tượng làm lớp cha, đôi khi cần `+` để chọn cả các subaspect cụ thể: một khai báo chỉ nhắm tới abstract aspect mà không match concrete aspect có thể không cho hiệu lực mong đợi.

Không nên đưa mọi aspect vào một thứ tự toàn cục quá rộng nếu chỉ vài nhóm có phụ thuộc. Một thứ tự ép buộc không cần thiết làm tăng coupling và dễ tạo va chạm khi thêm aspect mới.

### 4.2. Khai báo mâu thuẫn và vòng precedence

Nếu hai khai báo đặt hai thứ tự trái nhau:

```aspectj
declare precedence: FirstAspect, SecondAspect;
declare precedence: SecondAspect, FirstAspect;
```

Khi advice của hai aspect đó cùng gặp tại một join point, mâu thuẫn phải được xử lý; AspectJ có thể báo lỗi vòng precedence. Theo mô tả chính thức, các khai báo riêng lẻ có thể cùng tồn tại hợp lệ khi hai aspect **không cùng chia sẻ join point**. Không đơn giản kết luận “hai khai báo đảo nhau luôn là lỗi biên dịch” mà bỏ qua điều kiện có/không có join point chồng lấn.

Trong một danh sách, lặp một aspect ở nhiều vị trí là lỗi:

```aspectj
declare precedence: FirstAspect, SecondAspect, FirstAspect;
```

Thay vì phân tán các ràng buộc quan trọng qua nhiều module khó kiểm tra, hãy dùng một nguồn khai báo kiến trúc đủ dễ tìm và có kiểm thử hợp đồng.

## 5. Ví dụ hoàn chỉnh: Audit phải bao ngoài Validation và Metrics

Yêu cầu nghiệp vụ:

- Audit ghi bắt đầu/kết thúc **kể cả khi Validation từ chối**.
- Validation chạy trước việc ghi metric của thao tác hợp lệ.
- Metrics bao quanh thân `TransferService.transfer` để ghi bắt đầu/kết thúc khi xử lý tiếp tục.
- Nếu số tiền không hợp lệ thì Validation ném lỗi, Metrics và thân chuyển tiền không chạy.
- Caller xử lý lỗi và in kết quả rõ ràng.

Vì vậy, quan hệ cần có: **`AuditAspect > ValidationAspect > MetricsAspect`** khi cả ba match cùng join point.

### 5.1. Tệp `src/demo/Main.java`

```java
package demo;

public class Main {
    public static void main(String[] args) {
        TransferService service = new TransferService();

        service.transfer(5);

        try {
            service.transfer(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}

class TransferService {
    void transfer(int amount) {
        System.out.println("core=" + amount);
    }
}
```

### 5.2. Tệp `src/demo/AuditAspect.aj`

```aspectj
package demo;

public aspect AuditAspect {
    void around():
        execution(void demo.TransferService.transfer(int)) {
        System.out.println("audit enter");
        try {
            proceed();
        } finally {
            System.out.println("audit exit");
        }
    }
}
```

`AuditAspect` dùng `around` để bọc toàn bộ đường thực thi phía trong. `finally` bảo đảm ghi `audit exit` trên cả đường thành công và đường ném exception. Việc ghi này ở ví dụ là `System.out.println` để quan sát thứ tự; hệ thực tế cần chính sách ghi log phù hợp, nhất là khi log cũng có thể ném exception hoặc bị chặn.

### 5.3. Tệp `src/demo/ValidationAspect.aj`

```aspectj
package demo;

public aspect ValidationAspect {
    before(int amount):
        execution(void demo.TransferService.transfer(int))
        && args(amount) {
        System.out.println("validate=" + amount);
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "amount must be positive");
        }
    }
}
```

`ValidationAspect` kiểm tra input. Nếu không hợp lệ, `before` ném lỗi; advice và thân gốc có precedence thấp hơn không được tiếp tục như đường thành công. `args(amount)` bind số tiền từ join point.

### 5.4. Tệp `src/demo/MetricsAspect.aj`

```aspectj
package demo;

public aspect MetricsAspect {
    void around():
        execution(void demo.TransferService.transfer(int)) {
        System.out.println("metrics enter");
        try {
            proceed();
        } finally {
            System.out.println("metrics exit");
        }
    }
}
```

`MetricsAspect` được chạy phía trong Validation. Trong ví dụ, metrics chỉ đếm công việc đã qua kiểm tra. Nếu yêu cầu là đo **mọi** request, gồm request bị từ chối, cần thiết kế metrics ở phía ngoài Validation hoặc thêm metric từ chối; đó là quyết định về semantics.

### 5.5. Tệp `src/demo/AspectOrder.aj`

```aspectj
package demo;

public aspect AspectOrder {
    declare precedence:
        AuditAspect,
        ValidationAspect,
        MetricsAspect;
}
```

Đây là **một khai báo tường minh** bảo vệ thứ tự phụ thuộc. `AspectOrder` không cần advice riêng; nó chứa ràng buộc precedence giữa ba aspect cụ thể. Khi compiler/weaver hỗ trợ kiểu khai báo này, source order và số thứ tự file không cần được dùng để suy diễn.

### 5.6. Biên dịch và chạy

Dùng **JDK 17+**, phiên bản **AspectJ compiler** hỗ trợ Java 17 và thư viện runtime `aspectjrt.jar` tương thích. Java thông thường không biên dịch được `.aj`; dùng `ajc`. Giả sử đang đứng ở thư mục gốc project, có `aspectjrt.jar` cạnh thư mục `src`:

```powershell
ajc -17 -showWeaveInfo -d out -classpath "aspectjrt.jar" src/demo/Main.java src/demo/AuditAspect.aj src/demo/ValidationAspect.aj src/demo/MetricsAspect.aj src/demo/AspectOrder.aj
java -cp "out;aspectjrt.jar" demo.Main
```

Trên Linux/macOS, dùng classpath separator `:`:

```bash
ajc -17 -showWeaveInfo -d out -classpath aspectjrt.jar src/demo/Main.java src/demo/AuditAspect.aj src/demo/ValidationAspect.aj src/demo/MetricsAspect.aj src/demo/AspectOrder.aj
java -cp "out:aspectjrt.jar" demo.Main
```

**Stdout của chương trình Java (không tính thông tin weaving compiler):**

```text
audit enter
validate=5
metrics enter
core=5
metrics exit
audit exit
audit enter
validate=0
audit exit
caught=amount must be positive
```

Phân tích:

- Lượt `transfer(5)`: Audit vào trước, Validation kiểm tra, Metrics vào, thân `TransferService` chạy. Khi return, Metrics thoát trước rồi Audit thoát.
- Lượt `transfer(0)`: Audit vào trước, Validation ném `IllegalArgumentException`. Metrics và thân gốc không chạy; Audit `finally` vẫn chạy trước khi lỗi thoát tới caller.
- `AspectOrder` bảo vệ đúng ý định “Audit thấy cả lỗi từ Validation”, không phụ thuộc việc công cụ hôm nay tình cờ chọn thứ tự nào.
- Nếu bỏ `AspectOrder`, **không khẳng định một stdout thay thế cụ thể**: thứ tự không được xác định theo hợp đồng khi các advice ở những aspect độc lập không có quan hệ precedence; cần dùng kiểm thử để bắt vi phạm.

**Điều kiện của ví dụ:** đây là ba aspect cùng chọn `execution(void demo.TransferService.transfer(int))`; một thay đổi pointcut khiến chúng không còn chia sẻ join point sẽ thay đổi phạm vi áp dụng mà không nhất thiết thay đổi thứ tự khai báo.

## 6. Vì sao thay thứ tự có thể tạo lỗi rất khó tìm?

### 6.1. Audit đặt phía trong Validation

Nếu đảo `ValidationAspect` trước `AuditAspect`, với input `0` thì Validation có thể ném lỗi **trước** khi Audit được gọi. Kết quả: mất log bắt đầu/kết thúc cho request bị từ chối. Đường happy path có thể vẫn hợp lý nên chỉ test input thành công sẽ không phát hiện lỗi.

### 6.2. Metrics đặt phía ngoài Validation

Nếu Metrics có precedence cao hơn Validation, lời gọi bị từ chối vẫn đi qua `metrics enter/exit`. Đây không nhất thiết là bug; nó là bug **nếu** metric được định nghĩa là “số thao tác đã qua xác thực và vào thân xử lý”. Phải ghi rõ metric đo điều gì rồi mới xác định thứ tự.

### 6.3. Retry đặt trong hay ngoài Audit

Giả sử có RetryAspect dùng `around` và `proceed` nhiều lần khi lỗi tạm thời. Nếu Audit nằm **bên trong** Retry, mỗi lần thử có thể sinh một cặp log. Nếu Audit **bên ngoài**, một request có thể có một bản ghi tổng thể cho toàn bộ quá trình retry. Cả hai đều hữu ích cho những mục đích khác nhau.

Nhưng cần đặc biệt cẩn trọng: **gọi lại `proceed` không mặc nhiên bảo đảm idempotence**. Với cập nhật dữ liệu, thanh toán hay gửi thông điệp, advice retry sai có thể nhân đôi side effect. Precedence phải được xem cùng chính sách retry, transaction boundary và cơ chế chống thực thi trùng.

### 6.4. Transaction và lưu dấu vết kết quả

Nếu một around advice ghi “success” ngay sau khi phần bên trong return nhưng **việc commit thực tế xảy ra ở tầng bên ngoài sau đó**, dấu vết có thể báo thành công sớm. Ngược lại, nếu phần ghi log nằm ngoài toàn bộ ranh giới transaction, nó có thể quan sát cả lỗi commit. Vấn đề không giải quyết chỉ bằng đặt tên `TransactionAspect` trước/ sau `AuditAspect`: phải biết **đúng điểm commit**, xử lý exception và yêu cầu bền vững của audit.

### 6.5. Mã chuẩn hóa dữ liệu và kiểm tra

Nếu NormalizationAspect sửa dữ liệu trước ValidationAspect, kết quả kiểm tra phụ thuộc phiên bản **đã chuẩn hóa**. Nếu đảo thứ tự, Validation kiểm tra dữ liệu thô. Khi request chứa whitespace, Unicode form, kiểu chữ hoặc format chưa chuẩn, hai kết quả có thể khác. Đặc tả cần nói rõ validation thực hiện trên dữ liệu nào và kiểm thử các ca gây khác biệt.

## 7. Tính toán thứ tự bằng đồ thị phụ thuộc

Khi nhiều aspect liên quan, xem mỗi aspect là một node; quan hệ “A phải bao ngoài hoặc chạy trước B” là một cạnh `A -> B` với **ngữ nghĩa được ghi rõ** (không chỉ là nhãn “A trước B”). Ví dụ:

```text
Audit -> Validation -> Metrics -> Original
```

Nếu quan hệ chỉ áp dụng tại một nhóm join point, hãy giới hạn phạm vi trong bản thiết kế. Một aspect có thể cần đi ngoài aspect khác ở luồng xử lý A nhưng đi trong ở luồng B; áp một precedence toàn cục lên hai aspect đó có thể **không mô tả được cả hai yêu cầu**. Khi xuất hiện mâu thuẫn, hãy tách aspect theo trách nhiệm, đổi vị trí join point hoặc thiết kế lại contract, thay vì đoán thứ tự.

### 7.1. Quy trình phân tích đồ thị

1. Liệt kê các aspect và join point mà chúng có thể cùng match.
2. Với từng cặp có tác động qua lại, ghi rõ **quan hệ bắt buộc** và lý do: dữ liệu, kết quả, side effect, exception hay phạm vi đo lường.
3. Chuyển quan hệ nhất quán thành các cạnh có hướng. Nếu phát hiện chu trình A -> B -> C -> A tại cùng nhóm join point, các yêu cầu có thể mâu thuẫn.
4. Xác định cách biểu đạt tương ứng bằng precedence, nhóm aspect hoặc cơ chế áp dụng của AOP engine.
5. Thêm **test hành vi** để kiểm tra thứ tự và đường lỗi; sơ đồ không thay thế test.

Topological sort hữu ích trong thiết kế để nhận biết thứ tự hợp lệ, nhưng không được nhầm nó với một thuật toán tự động của mọi AOP engine. Chính compiler/weaver mới có hợp đồng cụ thể về giải quyết precedence.

### 7.2. Phụ thuộc không đồng nghĩa mọi advice phải chạy

Một `before` có thể throw. Một `around` có thể return sớm hoặc gọi `proceed` nhiều lần. Điều kiện động như `if(...)` hoặc `cflow(...)` có thể khiến một advice chỉ chạy ở một số lời gọi. Vì vậy, precedence là quan hệ **điều phối khi advice cùng áp dụng**, còn số lần thực thi vẫn phụ thuộc đường đi thực tế.

## 8. Kiểm chứng thứ tự bằng test thay vì suy từ file build

Một test tốt nên có **cả đường thành công và đường lỗi**:

| Tình huống | Điều cần khẳng định |
| --- | --- |
| Input hợp lệ | Audit vào trước Validation; Metrics bắt đầu trước core; Metrics thoát trước Audit |
| Input không hợp lệ | Audit vẫn chạy; Validation ném lỗi; Metrics và core không chạy |
| Body ném exception | Hai finally thực thi đúng đường unwind nếu đã đi vào; caller nhận loại lỗi mong muốn |
| Around không gọi `proceed` | Advice/thân ở phía trong bị chặn theo contract |
| Around gọi `proceed` nhiều lần | Số lượt advice/thân bên trong được tính đúng, side effect an toàn |
| Nhiều threads | Không dùng một StringBuilder/log chung không đồng bộ để suy thứ tự tất cả thread |
| Một aspect mới cùng pointcut | Precedence vẫn rõ và test phản ánh contract mới |
| Compiler/weaver được nâng phiên bản | Thứ tự quan sát và handling của exception không đổi ngoài phạm vi được duyệt |

Với ví dụ ở mục 5, CI có thể chụp stdout rồi so với golden output ở trên. Khi kiểm thử thực tế, tốt hơn là thu **các sự kiện có cấu trúc** (ví dụ `AUDIT_ENTER`, `VALIDATE`, `METRICS_ENTER`, `CORE`, `METRICS_EXIT`, `AUDIT_EXIT`) vào một collector dùng riêng cho mỗi test hoặc mỗi luồng; từ đó khẳng định quan hệ có thứ tự và số lần. Đừng so chuỗi log toàn cục của nhiều thread như thể tất cả sự kiện phải theo một thứ tự tuần tự duy nhất.

**Không chỉ chạy cùng compiler một lần và kết luận precedence đúng.** Bản build tiếp theo có thể thêm aspect, nới wildcard pointcut hoặc đổi tập class được dệt. Cần giữ test khi refactor, đổi công cụ build, thay JDK và thay cấu hình dệt mã.

### 8.1. Phân biệt phát hiện thứ tự với phát hiện điểm dệt

`ajc -showWeaveInfo` hữu ích để biết advice được dệt tại join point nào. Tuy nhiên, thông tin đó **không thay thế** test xác nhận thứ tự thực thi và nhánh throw/return. Nên kết hợp:

- **Weaving inventory:** aspect và advice nào áp dụng tại method/join point nào.
- **Precedence declaration:** hợp đồng xếp lớp giữa các aspect phụ thuộc.
- **Behavior tests:** sự kiện thực tế, exception, số lần `proceed`, kết quả và side effect.
- **Bytecode/source inspection:** khi nghi build dùng class chưa dệt, class loader khác hoặc helper do weaver sinh.

Một bộ test chỉ xác nhận các aspect **đều chạy** vẫn có thể bỏ qua lỗi **chạy sai thứ tự**.

## 9. Những cách khắc phục sai lầm

### 9.1. Khai báo precedence tường minh, có chỗ sở hữu rõ ràng

Với AspectJ code-style, dùng `declare precedence` cho các cặp/nhóm có phụ thuộc. Đặt quy tắc tại module kiến trúc phù hợp, có bình luận mô tả **vì sao** cần thứ tự; kiểm tra rằng bản build thực sự bao gồm aspect chứa khai báo này.

Không phân tán những ràng buộc chồng chéo vào nhiều thư viện mà không có cơ chế rà soát. Khi version dependency thêm một aspect mới, xác minh pattern trong precedence vẫn chọn đúng tập aspect.

### 9.2. Ưu tiên contract theo hành vi thay vì thứ tự “tên nghe có vẻ đúng”

“Security trước Logging” quá mơ hồ: có thể cần logging **bao ngoài** security để ghi cả yêu cầu bị từ chối, hoặc logging **bên trong** security để tránh ghi dữ liệu chưa được xác thực. Phải ghi rõ ngữ nghĩa mong muốn và điều kiện fail. Tương tự, transaction ngoài/ trong retry ảnh hưởng rollback, phạm vi lock và thời điểm side effect.

### 9.3. Tránh coupling vòng và vòng phụ thuộc ngầm

Nếu Validation đọc dữ liệu do Normalization tạo, đồng thời Normalization lại cần kết quả Validation, đó là vòng logic. Không có một danh sách precedence tuyến tính nào đồng thời đáp ứng hai quan hệ ngược nhau tại cùng một phép tính. Tách bước, xác định dữ liệu trung gian hoặc chuyển một phần logic về nơi thích hợp.

### 9.4. Giảm số aspect cùng chặn một join point nếu cần

Nếu sáu aspect cùng viết lại cùng đối số, biến đổi kết quả và bắt exception, độ khó kiểm soát tăng mạnh. Có thể gộp logic thuộc **cùng một trách nhiệm** vào một aspect có thứ tự nội bộ rõ ràng hoặc tách join point theo giai đoạn xử lý. Chỉ gộp khi kiến trúc và khả năng bảo trì cho phép, tránh tạo aspect khổng lồ.

### 9.5. Đặt test âm tính, exception và kiểm thử tải

Các vấn đề precedence thường lộ rõ khi validation thất bại, `proceed` bị bỏ qua hoặc aspect bắt rồi đổi exception. Chọn ca test khiến các thứ tự **cho kết quả khác nhau**; chỉ chạy happy path thường không đủ. Với side effect và concurrency, kiểm tra thread context, tương tác lock, dữ liệu chia sẻ, retry và tính idempotent.

## 10. Gỡ lỗi khi thứ tự quan sát khác kỳ vọng

Thực hiện theo trình tự:

1. **Đúng bản runtime chưa?** Kiểm tra classpath và lớp đã dệt. Nếu dùng load-time weaving, xem cấu hình agent/class loader và thời điểm nạp lớp.
2. **Pointcut có cùng match join point?** So chữ ký, `call` so với `execution`, phạm vi `within`, dynamic conditions.
3. **Các aspect có concrete không?** Kiểm tra aspect được dệt thực sự; abstract aspect và pattern inheritance có thể tác động đến precedence.
4. **`declare precedence` có được biên dịch/nạp không?** Một file khai báo chỉ nằm trong source tree nhưng không được đưa vào build sẽ không có hiệu lực.
5. **Quan hệ có bị che bởi khai báo khác?** Tìm các ràng buộc tạo vòng hoặc mâu thuẫn tại cùng join point; không dựa vào thứ tự compiler input.
6. **Tầng quanh `proceed` có short-circuit không?** Đặt breakpoint trước/sau `proceed` và ở advice phía trong.
7. **Có exception không?** `before` throw hoặc `around` bắt/throw có thể cắt ngắn trình tự log.
8. **Logger có bất đồng bộ không?** Thứ tự dòng xuất log giữa threads không hẳn phản ánh thứ tự sự kiện trong một call stack.
9. **Có thay đổi tại weaving không?** So weave info, artifact, cấu hình và bản weaver.

Trong ví dụ, breakpoint ở `AuditAspect` trước `proceed`, `ValidationAspect` trước kiểm tra, `MetricsAspect` trước `proceed` và thân `TransferService` sẽ cho biết cụ thể tầng nào đã chạy. Với input `0`, nếu debugger không vào `MetricsAspect` thì đó là hành vi đúng theo ràng buộc đã khai báo.

## 11. Các ngộ nhận và lỗi phổ biến

**“File aspect nào được compile trước thì advice của nó chạy trước.”** Không có hợp đồng như vậy cho các aspect độc lập không có quan hệ precedence.

**“Tên aspect bắt đầu bằng A sẽ ưu tiên hơn B.”** Thứ tự chữ cái không phải quy tắc ngữ nghĩa của AOP.

**“Advice ưu tiên cao chạy đầu cả khi vào lẫn khi ra.”** Tầng ưu tiên cao thường nằm ngoài: đi vào trước, nhưng `after`/phần sau `proceed` của tầng ngoài chạy sau khi phần phía trong hoàn tất.

**“Khai báo thứ tự là xong, khỏi cần test.”** Nếu thứ tự khai báo sai hoặc dynamic pointcut/exception làm cắt luồng, hệ thống vẫn có thể sai.

**“Chỉ cần mọi advice được gọi là đủ.”** Thứ tự thực hiện có thể thay đổi kết quả, exception, side effect và metric.

**“`around` luôn phải gọi `proceed` đúng một lần.”** Nó có thể gọi không lần nào hoặc nhiều lần tùy cú pháp và ngữ nghĩa. Thiết kế nghiệp vụ có thể yêu cầu đúng một lần nhưng cần kiểm thử điều đó.

**“`after returning` và `after` luôn giống nhau.”** `after` kiểu finally bao cả đường throw; `after returning` chỉ trên return bình thường.

**“Thêm một aspect mới không ảnh hưởng aspect cũ.”** Aspect mới có thể chia sẻ join point, chặn tiếp tục, biến đổi kết quả hoặc làm precedence thiếu xác định.

**“Khai báo toàn cục một thứ tự cho mọi aspect là an toàn.”** Có thể tạo coupling khó bảo trì và mâu thuẫn nếu các cặp cần thứ tự khác nhau ở các nhóm join point.

**“Quan sát được thứ tự đúng qua hai lần chạy là đủ chứng minh.”** Cần ràng buộc tường minh và test hồi quy, nhất là khi nâng công cụ hoặc đổi phạm vi weaving.

## 12. Checklist thiết kế trước khi phát hành

1. Liệt kê **tập aspect có thể cùng match** từng nhóm join point quan trọng.
2. Viết **thứ tự phụ thuộc theo ngữ nghĩa**: dữ liệu cần có, vị trí xử lý exception, ranh giới side effect, đo lường và kiểm toán.
3. Xác định aspect nào **bao ngoài** và aspect nào **nằm trong**; hiểu thứ tự khi đi vào và khi unwind.
4. Phát hiện mâu thuẫn hoặc vòng phụ thuộc, điều chỉnh thiết kế trước khi cấu hình.
5. Khai báo precedence bằng cơ chế chính thức của AOP engine; với AspectJ code-style là `declare precedence` khi thích hợp.
6. Kiểm tra các type pattern, abstract/concrete aspects và những khai báo precedence chồng lấn.
7. Kiểm thử happy path, validation thất bại, body throw, `around` short-circuit, retry và dữ liệu bị biến đổi.
8. Dùng weave info hoặc artifact inspection để chắc chắn các aspect đã được dệt vào đúng join point.
9. Giữ test hồi quy chạy khi thay đổi dependency, aspect, JDK, weaver và cấu hình.
10. Chỉ kết luận hệ thống đúng khi **precedence được xác định và quan sát hành vi phù hợp contract**.

**Kết luận:** khi hai hoặc nhiều aspect có quan hệ phụ thuộc, để thứ tự mặc định là một lỗi thiết kế tiềm ẩn. Hãy xác định rõ **ai phải bao ngoài ai, điều gì xảy ra trên đường thành công và đường lỗi**, biểu đạt quan hệ bằng cơ chế precedence phù hợp và bảo vệ nó bằng kiểm thử hành vi. Đừng dựa vào thứ tự tình cờ từ tên file, build hay một lần debug.

## Tài liệu tham khảo

- Eclipse AspectJ Programming Guide — Advice Precedence, quy tắc giữa/cùng aspect và ảnh hưởng của `proceed`: https://eclipse.dev/aspectj/doc/latest/progguide/semantics-advice.html
- Eclipse AspectJ Programming Guide — `declare precedence`, TypePatternList và các khai báo mâu thuẫn: https://eclipse.dev/aspectj/doc/latest/progguide/semantics-declare.html
- Eclipse AspectJ Quick Reference — cú pháp advice và `declare precedence`: https://eclipse.dev/aspectj/doc/latest/progguide/quickreference.html
- Eclipse AspectJ Language Semantics — precedence ở concrete/sub-aspects: https://eclipse.dev/aspectj/doc/latest/progguide/semantics.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-pitfalls-007">Bổ sung quá nhiều hành vi ẩn khiến chương trình khó hiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: Bổ sung quá nhiều hành vi ẩn khiến chương trình khó hiểu

Aspect-Oriented Programming (AOP) giúp gom những hành vi xuyên suốt vào aspect, chọn nơi áp dụng qua pointcut rồi đưa advice vào luồng thực thi bằng weaving hoặc interception. Cơ chế này hữu ích khi cùng một trách nhiệm kỹ thuật xuất hiện ở nhiều điểm. Tuy nhiên, nếu một lời gọi tưởng như đơn giản lại bị nhiều advice tác động mà không có hợp đồng rõ ràng, **mã nhìn thấy và hành vi thật có thể khác nhau đáng kể**. Đó là lỗi thiết kế “bổ sung quá nhiều hành vi ẩn”.

Ví dụ, một lời gọi `charge(amount)` trong mã nguồn có thể bị bọc bởi advice kiểm toán, đo thời gian, đổi ngữ cảnh, kiểm tra điều kiện, mở transaction, retry, chuyển ngoại lệ và ghi log. Người đọc một phương thức không nhìn thấy ngay thứ tự thực thi hay số lần nghiệp vụ được gọi. Nếu một advice tự động retry thao tác thu tiền sau lỗi không chắc chắn, side effect có thể xảy ra hai lần. Nếu một advice nuốt ngoại lệ, caller có thể hiểu sai trạng thái giao dịch. Dù từng advice được viết “để tái sử dụng”, tổng thể trở nên khó dự đoán.

Bài này tập trung vào **AOP nói chung**, ở cả mức căn bản và nâng cao: cách nhận diện hành vi ẩn, kiểm soát phạm vi pointcut, thứ tự advice, thay đổi semantics, truy vết luồng thực thi, kiểm thử và lựa chọn thiết kế dễ bảo trì. Cú pháp hay khả năng áp dụng chi tiết phụ thuộc vào công cụ AOP và cách weaving, vì vậy không giả định mọi công cụ hỗ trợ cùng loại join point.

## 1. “Hành vi ẩn” trong AOP thực sự là gì?

Trong AOP, **join point** là điểm của quá trình thực thi có thể áp dụng logic bổ sung; **pointcut** mô tả tập join point; **advice** chạy ở trước, sau hoặc bao quanh điểm được chọn; **aspect** đóng gói concern và các quy tắc áp dụng. **Weaving** kết hợp logic của aspect với chương trình, trong khi một số cách hiện thực dùng proxy/interceptor để điều hướng lời gọi lúc chạy.

“Hành vi ẩn” không nhất thiết là điều xấu: đó là tính chất khi người đọc thân phương thức không thấy đầy đủ phần logic do AOP thêm vào. Mức độ rủi ro phụ thuộc vào **advice tác động đến điều gì**, **phạm vi áp dụng có dễ nhận biết hay không**, và **người phát triển có cách kiểm chứng hành vi thực tế hay không**.

Có thể chia hành vi theo mức độ can thiệp:

| Mức độ | Ví dụ | Rủi ro cần kiểm soát |
| --- | --- | --- |
| Quan sát tương đối thụ động | Đếm lời gọi, ghi thời lượng, ghi trace | Chi phí hiệu năng, lỗi logger, dữ liệu nhạy cảm, đếm trùng |
| Thay đổi ngữ cảnh | Đặt correlation ID, mở/đóng scope, context thread | Rò rỉ context, sai thứ tự cleanup, mất context khi async |
| Kiểm soát đường gọi | Chặn khi thiếu điều kiện, giới hạn tốc độ, cache | Có thể bỏ qua method gốc hoặc trả kết quả khác |
| Thay đổi giao dịch/thử lại | Retry, timeout, transaction boundary, fallback | Có thể gọi lại tác vụ không idempotent hoặc che lỗi |
| Thay đổi dữ liệu/ngữ nghĩa | Sửa tham số, giá trị trả về, ngoại lệ hoặc side effect | Hợp đồng nghiệp vụ bị thay đổi ngoài mã phương thức |

Một advice quan sát vẫn có tác dụng phụ nếu logging đồng bộ gây chậm, ghi log thất bại làm ném ngoại lệ, hoặc tập metric dùng cardinality quá cao. Vì vậy chữ “quan sát” không phải giấy phép để bỏ kiểm thử.

Tính “ẩn” xuất hiện khi lời gọi trong mã và các phần mở rộng được lưu ở những nơi khác nhau. Tính **khó hiểu** xuất hiện khi người đọc không thể trả lời: *advice nào đang chạy, vì sao nó chạy, chạy trước hay sau advice khác, nó có đổi kết quả hoặc số lần gọi gốc không?*

## 2. Tình huống điển hình: một lời gọi, nhiều lớp hành vi

Giả sử caller gọi `processor.execute(request)` nhưng cùng join point được bao bởi các advice: bảo vệ đầu vào, ghi kiểm toán, đo thời gian và retry.

Luồng thực thi thực tế có thể mang dạng:

```text
Caller
  |
  v
Advice A: kiểm tra điều kiện
  |
  v
Advice B: bắt đầu ghi kiểm toán
  |
  v
Advice C: bắt đầu đo thời gian
  |
  v
Advice D: retry/fallback
  |
  +--> Target: execute(request)
  |       |
  |       +--> side effect / exception / return
  |       |
  |       +--> có thể được gọi lần nữa nếu D retry
  |
  v
Advice C: kết thúc đo thời gian
  |
  v
Advice B: kết thúc kiểm toán
  |
  v
Caller nhận kết quả hoặc ngoại lệ
```

Đây là **một cấu hình giả định** để minh họa. Thứ tự thật phải được xác định theo định nghĩa và cơ chế áp dụng của công cụ đang dùng; không được tự cho rằng mọi AOP engine sắp xếp advice giống sơ đồ này.

Bây giờ hãy đặt các câu hỏi mà mã phương thức gốc không trả lời:

- Bản ghi kiểm toán mô tả **một yêu cầu** hay **mỗi lần retry**?
- Timer đo **toàn bộ quá trình retry** hay **một lần thử**?
- Nếu target đã ghi dữ liệu nhưng ném timeout trước khi caller nhận xác nhận, việc retry có lặp side effect không?
- Khi advice kiểm tra điều kiện từ chối, kiểm toán có ghi nhận lần từ chối không?
- Nếu exporter log thất bại, advice quan sát có làm yêu cầu nghiệp vụ thất bại không?
- Nếu hai advice cùng chuyển đổi ngoại lệ, ngoại lệ nào đến caller?
- Nếu target gọi nội bộ một method khác, method đó có thật sự đi qua cơ chế interception hay không?

Mỗi câu đều cần câu trả lời dựa trên **hợp đồng hành vi và kiểm thử**, không dựa vào cảm giác “AOP đã lo hết”.

## 3. Dấu hiệu AOP đã tạo ra quá nhiều hành vi ngầm

### 3.1. Không thể suy ra tác động khi đọc hoặc debug một đường gọi

Bạn thấy một phương thức rất ngắn, nhưng lỗi hoặc dữ liệu trả về chỉ xuất hiện khi chạy trong cấu hình weaving thật. Unit test gọi thẳng đối tượng qua đường khác lại hoạt động bình thường. Điều này có thể do lời gọi trực tiếp bỏ qua proxy, do khác class loader, do pointcut không khớp ở môi trường test, hoặc do thứ tự advice thay đổi.

Dấu hiệu đặc biệt rõ khi người debug phải mở hàng loạt file cấu hình, aspect và danh sách agent để tìm vì sao method được gọi lại, ném ngoại lệ khác hoặc thay dữ liệu.

### 3.2. Mỗi aspect trở thành nơi thêm chức năng mới

Một aspect ban đầu chỉ đo thời gian, sau đó được thêm điều kiện cấp quyền, ghi kiểm toán, cache và fallback để “tiện tận dụng pointcut có sẵn”. Lúc này cùng một aspect có nhiều **lý do thay đổi độc lập**, khó kiểm chứng và khó rollback. Số dòng mã nghiệp vụ có thể giảm, nhưng mức coupling và chi phí suy luận tăng.

Nên tách concern theo hợp đồng riêng: concern đo thời gian, concern kiểm toán, concern điều khiển retry. Tuy nhiên tách thành nhiều aspect vẫn chưa đủ nếu chúng có thứ tự phụ thuộc mơ hồ hoặc pointcut quá rộng. Cần giới hạn điểm áp dụng và tài liệu hóa tương tác.

### 3.3. Pointcut mang tính suy đoán

Một pointcut chọn mọi phương thức bắt đầu bằng `save`, `validate` hoặc mọi phương thức trong một package lớn có thể vô tình nhận thêm join point khi người khác đổi tên class hoặc thêm hàm mới. Tên phương thức và vị trí source là **đặc điểm cấu trúc**, không tự bảo đảm có chung semantics.

Nếu muốn áp dụng một chính sách kỹ thuật cho tập thao tác cụ thể, cần mô tả **tập thao tác mục tiêu** bằng điều kiện có ý nghĩa và có test khẳng định cả trường hợp được chọn lẫn không được chọn.

### 3.4. Advice âm thầm đổi hợp đồng return/exception

Một around advice có thể gọi target không đủ lần, gọi hai lần hoặc không gọi, sửa tham số, bắt ngoại lệ rồi trả giá trị mặc định. Điều đó có thể là hành vi được thiết kế hợp lệ cho một concern cụ thể, nhưng khi bị ẩn và không được công bố thì rất nguy hiểm.

Caller cần biết liệu thao tác có thể bị retry, ngắt trước khi vào target, bị trả kết quả cache, hay được chuyển ngoại lệ. **Thay đổi semantics phải được xem như thay đổi API/hợp đồng**, dù nó được triển khai bằng advice.

### 3.5. Lỗi chỉ xảy ra khi có tương tác giữa nhiều advice

Advice A hoạt động đúng khi đứng một mình, advice B cũng vậy, nhưng A+B tạo ra:

- Trace ghi hai lần hoặc thừa một span khi chạy lồng nhau.
- Timer bị tính ngoài ranh giới mong muốn.
- Transaction đóng trước khi advice đọc dữ liệu trả về.
- Retry lặp một side effect không idempotent.
- Cleanup context sai thứ tự, gây rò dữ liệu sang yêu cầu khác.
- Deadlock khi advice logging dùng cùng lock hoặc phụ thuộc thao tác đang được intercept.

Vấn đề nằm ở **tương tác và thứ tự**, không nhất thiết ở từng advice đơn lẻ.

## 4. Cơ chế AOP làm tăng hoặc giảm độ khó theo dõi

Không thể đánh giá hành vi ẩn chỉ từ tên “AOP”; phải xét cách áp dụng.

| Cách áp dụng | Điều giúp truy vết | Rủi ro thường gặp |
| --- | --- | --- |
| Weaving khi biên dịch | Có thể lưu/kiểm tra class đã weave; build tái hiện được | Mã chạy khác source nhìn thấy, khó suy ra từ source nếu không có bản đồ weave |
| Weaving sau biên dịch | Có artifact đầu vào/đầu ra để so sánh | Có thể dệt thêm ngoài ý muốn, ảnh hưởng metadata/chữ ký hoặc binary compatibility |
| Weaving lúc tải lớp | Có thể bật/tắt hoặc thay cấu hình agent theo môi trường | Hai môi trường có cùng JAR nhưng khác agent/cấu hình cho hành vi khác nhau |
| Proxy/interception runtime | Có điểm bao rõ ràng nếu wiring tường minh | Một số lời gọi không đi qua proxy, có thể có nhiều lớp proxy lồng nhau |

**Ghi nhớ:** một số cơ chế chỉ intercept method execution; một số khác có thể xử lý call, constructor, field access hoặc loại join point khác. Không nên mô tả tất cả advice như thể chúng luôn xuất hiện ở mọi đường gọi.

Đặc biệt, với các proxy chỉ chặn lời gọi qua một đối tượng wrapper, lời gọi trực tiếp vào target hoặc self-invocation có thể không đi qua wrapper. Với weaving bytecode, hành vi lại phụ thuộc các loại join point, thời điểm dệt và class loader. Test phải dùng **đúng đường chạy sản xuất** để chứng minh điểm áp dụng.

Mức dễ hiểu còn phụ thuộc công cụ có xuất danh sách class/join point đã weave, thống kê advice match, cấu hình order và dấu vết thực thi hay không. Khi công cụ hỗ trợ, nên lưu các thông tin đó cùng artifact phát hành hoặc report kiểm thử.

## 5. Chỉ cho phép advice ẩn trong một “ngân sách hành vi”

Để tránh thiết kế ngày càng khó đoán, nhóm phát triển có thể đặt một **hợp đồng cho mỗi concern** với các trường tối thiểu:

| Trường | Cần mô tả |
| --- | --- |
| Mục đích | Aspect thêm điều gì và ai sở hữu trách nhiệm này? |
| Phạm vi | Những join point nào phải áp dụng, những điểm nào phải loại trừ? |
| Cơ chế | Compile-time, load-time, post-compile hay proxy/interception? |
| Thời điểm và thứ tự | Before/after/around, tương tác với advice khác theo thứ tự nào? |
| Target invocation | Có gọi đúng một lần, có thể bỏ qua hoặc retry? Điều kiện cụ thể là gì? |
| Kết quả và ngoại lệ | Có giữ nguyên, chuyển đổi hay che giấu? Caller biết bằng cách nào? |
| Side effect | Ghi log, mạng, I/O, transaction, cache, dữ liệu nào bị thay đổi? |
| Failure policy | Lỗi của chính advice có khiến tác vụ gốc thất bại không? |
| Concurrent/async | Context và trạng thái dùng chung được truyền/dọn như thế nào? |
| Bằng chứng | Test pointcut, order, semantics, log/tracing và chi phí tài nguyên |

Nếu không giải thích được một trường quan trọng, có thể điểm áp dụng đang quá rộng hoặc đang cố giấu một trách nhiệm vốn cần được gọi trực tiếp.

Ví dụ hợp đồng cho advice đo thời gian:

> “Chỉ quan sát các lời gọi thuộc nhóm thao tác X, không sửa tham số, kết quả hoặc ngoại lệ; bắt đầu timer trước lần gọi gốc, ghi kết quả trong finally, không retry; lỗi xuất metric được xử lý theo chính sách đã định; timer đo một lần gọi toàn bộ; ghi dữ liệu có giới hạn và không chứa dữ liệu nhạy cảm.”

Ngay cả hợp đồng này còn cần trả lời: khi một around advice retry khác bọc bên ngoài/ở trong timer, “một lần gọi toàn bộ” là một yêu cầu hay một attempt? **Thứ tự advice phải được kiểm chứng cùng với định nghĩa đơn vị đo.**

## 6. Ví dụ Java 17: thấy rõ thứ tự hai lớp advice

Đoạn Java dưới đây dùng **JDK dynamic proxy** để minh họa interception và thứ tự các lớp wrapper. Đây là một ví dụ runnable độc lập nhằm làm lộ call chain; không phải trình dệt mã tổng quát và không đại diện cho mọi loại join point của mọi hệ AOP.

Chương trình dùng một tác vụ có thể thành công hoặc ném ngoại lệ. Hai lớp có nhãn `audit` và `metrics` chỉ ghi dấu vào danh sách trong bộ nhớ để quan sát thứ tự, không triển khai hệ thống kiểm toán hay thu thập chỉ số thật.

Lưu thành `HiddenAdviceDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class HiddenAdviceDemo {
    @FunctionalInterface
    public interface Task {
        String run(String input);
    }

    static Task surround(Task target, String name, List<String> events) {
        return (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] { Task.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("run")) {
                        return method.invoke(target, args);
                    }

                    events.add(name + ":before");
                    try {
                        Object result = method.invoke(target, args);
                        events.add(name + ":after");
                        return result;
                    } catch (InvocationTargetException ex) {
                        events.add(name + ":error");
                        throw ex.getCause();
                    }
                });
    }

    static void execute(Task task, String input) {
        try {
            System.out.println("Result: " + task.run(input));
        } catch (IllegalArgumentException ex) {
            System.out.println("Error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();

        Task core = input -> {
            events.add("core");
            if (input.isBlank()) {
                throw new IllegalArgumentException("blank input");
            }
            return "accepted:" + input;
        };

        Task observed = surround(
                surround(core, "audit", events),
                "metrics",
                events
        );

        execute(observed, "A");
        execute(observed, "");
        System.out.println("Trace: " + String.join(" | ", events));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 HiddenAdviceDemo.java
java HiddenAdviceDemo
```

Stdout xác định:

```text
Result: accepted:A
Error: blank input
Trace: metrics:before | audit:before | core | audit:after | metrics:after | metrics:before | audit:before | core | audit:error | metrics:error
```

**Giải thích:**

1. Ở `observed`, wrapper metrics nằm **ngoài** wrapper audit, nên chạy `metrics:before` trước `audit:before`; lúc thành công, `audit:after` diễn ra trước `metrics:after`.
2. Khi input rỗng, method gốc ném `IllegalArgumentException`. Reflection bọc ngoại lệ trong `InvocationTargetException`; wrapper unwrap bằng `ex.getCause()`, nên caller nhận lại lỗi gốc, không có bản ghi `:after` trên đường lỗi.
3. Mỗi lần gọi method gốc đúng một lần trong ví dụ. Nếu thêm advice retry, phải kiểm chứng lại số lần gọi và tính idempotent của side effect.
4. Việc gắn wrapper trong `main` là một **điểm lắp ráp tường minh** để nhìn thấy hai lớp behavior. Trong hệ thống dùng pointcut/weaving, sự lắp ráp có thể do cơ chế AOP thực hiện; cần cung cấp tài liệu hoặc công cụ truy vết tương đương.
5. `ArrayList` chỉ phục vụ demo chạy tuần tự. Khi có nhiều thread, phải chọn cấu trúc dữ liệu và chiến lược truyền context an toàn. Không dùng danh sách không đồng bộ này để làm log dùng chung ngoài production.

Nếu đổi thứ tự thành `surround(surround(core, "metrics", events), "audit", events)`, advice audit sẽ thành lớp ngoài. Kết quả nghiệp vụ vẫn có thể giống nhau nhưng **thứ tự sự kiện, khoảng thời gian đo và semantics trong tình huống lỗi có thể đổi**. Đây là lý do không nên coi thứ tự advice là một chi tiết phụ.

## 7. So sánh trước và sau khi kiểm soát hành vi ẩn

Hãy xét cùng một nghiệp vụ `saveDocument`.

**Thiết kế khó hiểu:** một method đơn giản bị pointcut bắt vào nhiều aspect: cache tự động, retry, kiểm toán, đo thời gian, chuyển exception và thông báo. Danh sách aspect nằm ở nhiều nơi; không có mô tả thứ tự và không có test kiểm tra số lần target chạy. Người sửa phương thức không thể biết tác động nếu thêm một thao tác ghi file.

**Thiết kế dễ hiểu hơn:** phương thức nghiệp vụ biểu diễn trực tiếp các bước có ý nghĩa miền; các concern kỹ thuật có hợp đồng riêng; danh sách join point và thứ tự được ghi nhận ở điểm cấu hình/weaving; test xác minh tương tác giữa các advice. Hành vi ảnh hưởng mạnh đến semantics (như retry một thao tác có side effect) được mô tả ở API hoặc tổ chức thành bước gọi tường minh khi cần.

| Thuộc tính | Nhiều hành vi ẩn không kiểm soát | Hành vi AOP có hợp đồng |
| --- | --- | --- |
| Đọc hiểu | Phải phỏng đoán và truy tìm nhiều aspect | Biết tập pointcut, thứ tự và chính sách từ tài liệu/cấu hình |
| Thay đổi | Một aspect sửa có thể tác động ngoài ý muốn | Có owner, phạm vi, test positive/negative |
| Debug | Dễ bỏ sót advice khi xem source và stack | Có trace hoặc bản đồ các advice đã áp dụng |
| Ngoại lệ | Bị đổi hoặc nuốt mà caller không biết | Có hợp đồng truyền/biến đổi lỗi được kiểm thử |
| Hiệu năng | Chi phí tích lũy ở nhiều join point | Có ngân sách overhead và benchmark đại diện |
| Triển khai | Môi trường có thể khác agent/weaving | Ghi rõ version, mode và cấu hình khởi động |

Cần nhấn mạnh: “có hợp đồng” không có nghĩa loại bỏ mọi chức năng AOP. Nó làm cho các tác động xuyên suốt **có thể phát hiện, hiểu, đo và thử nghiệm**.

## 8. Những vấn đề nâng cao khi nhiều aspect tương tác

### 8.1. Thứ tự advice không mang tính giao hoán

Hai advice A và B nhìn có vẻ độc lập nhưng nói chung `A(B(target))` không tương đương `B(A(target))`.

Ví dụ, đặt timer **ngoài** advice retry sẽ đo toàn bộ thời gian của các attempt và thời gian chờ giữa attempt; đặt timer **trong** retry sẽ đo từng attempt riêng. Đặt kiểm toán bên trong retry có thể ghi một bản ghi cho mỗi lần thử; đặt ngoài có thể chỉ ghi một bản ghi ở cấp yêu cầu. Cả hai cách đều có thể hữu ích nếu phù hợp mục tiêu, nhưng phải chọn có chủ đích.

Nếu bộ công cụ AOP có cơ chế chỉ định precedence/order, quản lý nó như một phần hợp đồng. Nếu không hỗ trợ order đủ rõ, cân nhắc đóng gói tương tác liên quan trong một thành phần tường minh hoặc tránh mô hình phụ thuộc vào thứ tự ngầm.

### 8.2. Retry, timeout và side effect không idempotent

Advice có thể khiến một method gốc chạy nhiều lần. Với thao tác đọc thuần, có thể ít rủi ro hơn; với gửi email, trừ tiền, ghi file hoặc gọi API có side effect, việc retry có thể gây trùng. **AOP không tự bảo đảm idempotency hoặc exactly-once semantics.**

Nếu cần retry, xác định trường hợp lỗi nào cho phép retry, giới hạn lần thử, khoảng đợi, idempotency key, cơ chế deduplication và trạng thái giao dịch. Khi các điều kiện gắn chặt vào use case cụ thể, một API retry được gọi tường minh có thể làm logic dễ hiểu hơn aspect rộng.

Timeout cũng cần phân biệt **ngừng chờ** với **dừng thực thi**. Một advice báo timeout không đồng nghĩa tác vụ gốc đã dừng hoặc side effect đã được rollback; điều này phụ thuộc cơ chế và khả năng hủy ở tầng thấp.

### 8.3. Chuyển ngoại lệ và chính sách thất bại

Advice quan sát thường muốn ghi lỗi rồi phát lại lỗi gốc. Nếu ghi log thất bại ngay trong quá trình xử lý ngoại lệ, lỗi phụ có thể che ngoại lệ nghiệp vụ. Hãy xác định ưu tiên: lỗi nào là lỗi chính, lỗi nào chỉ là thông tin bổ sung, và liệu thất bại của kênh metric có làm hỏng tác vụ không.

Đối với advice dạng around, một mẫu khái niệm được sử dụng khi cần cleanup là:

```text
before
try:
    result = proceed to the original invocation
    return result
finally:
    release context or record completion
```

Đây là **pseudocode**, không phải cú pháp chung của mọi công cụ AOP. Nó chỉ minh họa nguyên tắc cleanup cho cả thành công và ngoại lệ; chính sách xử lý lỗi trong finally vẫn phải được thiết kế để không vô ý che ngoại lệ gốc.

### 8.4. Context theo luồng và bất đồng bộ

ThreadLocal có thể hỗ trợ ngữ cảnh ở luồng đồng bộ, nhưng một tác vụ bất đồng bộ hoặc chuyển thread có thể mất context nếu không truyền tường minh. Ngược lại, không dọn ThreadLocal đúng cách có thể rò dữ liệu sang yêu cầu khác khi luồng được tái sử dụng.

Khi dùng aspect để mở/đóng trace hoặc security context, cần phân biệt **kết thúc lời gọi trả về** với **hoàn tất công việc bất đồng bộ**. Nếu method trả về một đối tượng biểu diễn tác vụ chưa hoàn tất, advice chạy sau khi method trả về chưa chắc tương đương “công việc đã xong”. Hợp đồng phải quy định rõ điểm hoàn tất nào được quan sát.

### 8.5. Reentrancy và chặn nhầm hạ tầng

Nếu pointcut chặn vào chính phương thức mà logger/metric exporter gọi, advice có thể tự gọi lại chính nó. Kết quả có thể là vòng lặp, log trùng, stack overflow hoặc khuếch đại tải. Cần loại trừ hạ tầng khỏi tập pointcut, hoặc sử dụng cơ chế guard/reentrancy control đã được kiểm chứng.

Đừng chỉ kiểm tra tên method; xác nhận danh sách join point thực tế và các tình huống nested invocation để chắc chắn guard không che cả lời gọi hợp lệ.

### 8.6. Trạng thái dùng chung và tính đồng thời

Một aspect có state chia sẻ giữa các lời gọi có thể tạo race condition khi viết bộ đếm, cache, map hoặc correlation state. Nếu phải ghi metric, chọn primitive hoặc bộ thu thập được thiết kế cho concurrency, và tránh giữ dữ liệu request trong field dùng chung không được đồng bộ.

Không thể suy ra “advice chạy quanh mỗi method” là mỗi advice có instance riêng. Lifecycle/instance semantics phụ thuộc ngôn ngữ và framework AOP được dùng; khi thiếu cơ chế cô lập rõ ràng, phải kiểm tra concurrency và scope của trạng thái.

## 9. Thiết kế pointcut có thể kiểm chứng

Một pointcut an toàn cần xác định **vì sao** join point thuộc phạm vi, không chỉ nó trông như thế nào. Ví dụ “mọi phương thức có tiền tố get” quá rộng để mặc định gắn logging của dữ liệu nhạy cảm; một nhóm thao tác đã định nghĩa hợp đồng quan sát, với chính sách loại trừ dữ liệu, thường rõ ràng hơn.

Một bộ kiểm thử nên có:

- **Positive cases:** gọi đúng các join point phải nhận advice và đếm chính xác số lần chạy.
- **Negative cases:** utility, method nội bộ, điểm xử lý dữ liệu nhạy cảm hoặc lớp ngoài phạm vi phải không bị bắt.
- **Boundary cases:** method overload, cùng tên khác package, lớp kế thừa, generic bridge, self-invocation, caller khác class loader nếu được hệ thống sử dụng.
- **Failure cases:** method gốc ném checked/unchecked exception, advice ném lỗi, method chưa bắt đầu do guard, hoặc lời gọi bị retry.
- **Evolution cases:** thêm một method mới có tên trùng mẫu wildcard không làm aspect tự mở rộng ngoài hợp đồng.

Không phải mọi công cụ hỗ trợ tất cả join point trong danh sách. Chỉ kiểm tra những trường hợp thực sự tương ứng với cơ chế AOP đang chạy; điều quan trọng là test **tập điểm phải bắt và tập điểm không được bắt**, trên đúng artifact đã weave hoặc đúng đường proxy/runtime.

Cách quản lý hiệu quả là lưu **bản kê concern → pointcut → advice → nơi áp dụng → owner**. Khi xuất bản artifact, ghi nhận phiên bản toolchain và cấu hình weaving để người khác có thể tái hiện chính xác hành vi quan sát.

## 10. Kiểm thử hành vi tích hợp, không chỉ unit test cho từng advice

Unit test advice đơn lẻ không chứng minh A+B+C hoạt động đúng khi chồng lên nhau. Nên có một số bài test ở mức chương trình thực thi:

| Nhóm test | Cần khẳng định |
| --- | --- |
| Semantics | Bật/tắt advice quan sát cho cùng giá trị trả về và ngoại lệ; tác vụ gốc không bị thay đổi không chủ đích |
| Invocation count | Target chạy đúng số lần; nếu retry được phép, số lần phù hợp chính sách |
| Ordering | Before/after/error/cleanup diễn ra theo trật tự quy định |
| Pointcut coverage | Chỉ những join point đã công bố được chặn; negative cases phải không phát sinh advice |
| Error handling | Exception gốc được giữ hoặc đổi đúng hợp đồng; lỗi advice không che lỗi chính ngoài dự kiến |
| Concurrent execution | Không race, mất context hoặc rò state giữa yêu cầu |
| Async completion | Nếu method trả kết quả tương lai, advice đo đúng mốc hoàn tất theo hợp đồng |
| Resource usage | Chi phí CPU, allocation, GC, I/O, latency p95/p99 ở tải đại diện đạt ngưỡng |
| Deployment parity | Build/test và runtime thực sự sử dụng cùng mode, phiên bản weaver/agent và cấu hình cần thiết |

Đối với một aspect vốn được thiết kế chỉ để quan sát, **kiểm tra tương đương hành vi trước–sau** là một tiêu chuẩn rất có giá trị. Với aspect chủ động thay đổi semantics, cần test chính thay đổi đó như một phần API; không thể đòi hỏi tương đương tuyệt đối rồi bỏ qua mục tiêu thiết kế.

Nên có fixture giúp thu thập thứ tự sự kiện theo cách xác định, giống ví dụ Java ở trên. Các assertions về trace có thể chỉ ra chính xác lớp advice đã thay đổi. Ở production, hãy gắn correlation ID và log có cấu trúc, nhưng tránh log tham số/giá trị trả về chứa thông tin nhạy cảm.

## 11. Truy vết khi hành vi runtime khác source

Một quy trình debug thực dụng:

1. **Tái hiện từ đúng entry point.** Đảm bảo caller đi qua proxy hoặc bytecode đã weave, đúng cấu hình và đúng biến thể runtime.
2. **Xác định cơ chế AOP.** Hỏi build-time, post-compile, load-time hay runtime interceptor; ghi phiên bản công cụ, agent, class loader và cấu hình.
3. **Liệt kê aspect áp dụng.** Dựa trên báo cáo weaving, instrumentation logs, pointcut matching hoặc traces của hệ thống thực tế nếu có. Không suy từ source một mình.
4. **Dựng thứ tự before/around/after.** Vẽ call chain từ ngoài vào trong; xác định advice nào có thể bỏ qua, retry, thay tham số, sửa return hoặc exception.
5. **So sánh đường có và không có advice.** Dùng baseline có kiểm soát, giữ cùng input và dữ liệu, kiểm tra semantics và side effect. Không tắt logic bảo vệ bắt buộc ngoài môi trường thử nghiệm được kiểm soát.
6. **Kiểm tra số lần gọi.** Phân biệt một yêu cầu, một attempt và một join point; đếm target invocation và external calls để tìm duplicate.
7. **Kiểm tra lỗi thứ cấp.** Logger, telemetry, context cleanup hay exporter có lỗi trước khi lỗi gốc được truyền lên không?
8. **Kiểm tra hiệu năng và concurrency.** Dùng latency, allocation, GC, queue hoặc profiler khi nghi ngờ advice làm hệ thống chậm hoặc race.
9. **Ghi lại kết quả.** Cập nhật hợp đồng pointcut/advice và test regression, thay vì chỉ vá lỗi ở một luồng chạy.

Stack trace có thể giúp nhưng không luôn hiển thị cấu trúc pointcut đầy đủ. Một số công cụ weaving biến đổi bytecode hoặc sinh wrapper khiến đường đi khó nhìn từ trace đơn thuần; cần kết hợp cấu hình và bằng chứng runtime.

## 12. Cách giảm hành vi ẩn mà vẫn giữ lợi ích AOP

**Giới hạn một aspect vào một concern nhất quán.** Nếu một aspect vừa làm cache, vừa retry, vừa ghi kiểm toán, vừa thay quy tắc nghiệp vụ, nên phân tách trách nhiệm. Sau khi tách, xác định lại thứ tự và tương tác, không chỉ chia file.

**Thu hẹp pointcut.** Chọn tập join point dựa trên nghĩa của ranh giới kỹ thuật hoặc hợp đồng đã được phê duyệt. Định nghĩa exclusions và có test negative để ngăn thêm method mới bị bắt tình cờ.

**Làm lộ ranh giới áp dụng.** Dù AOP thực thi tự động, kiến trúc cần có tài liệu hoặc bản đồ “method nhóm X → những advice Y,Z” để reviewer dễ truy tìm. Nếu công cụ không thể liệt kê tự động, lưu một bảng ngắn gọn được review cùng mã.

**Giữ các quy tắc miền dễ thấy.** Những quyết định như cách tính giá, phê duyệt giao dịch hoặc điều kiện hoàn tiền thường cần biểu diễn rõ bằng domain policy/service hoặc lời gọi tường minh. Chỉ đưa vào aspect khi đó thật sự là một concern xuyên suốt có hợp đồng phù hợp và các giới hạn được hiểu rõ.

**Hạn chế around advice có quyền đổi control flow.** Advice chỉ quan sát nên giữ nguyên kết quả/ngoại lệ và thường tránh gọi lại target. Những advice có quyền retry, short-circuit, cache hoặc fallback phải được review ở mức semantics, vì chúng tác động trực tiếp tới số lần gọi và side effect.

**Cho phép vô hiệu hóa có kiểm soát để chẩn đoán.** Ở môi trường test, cấu hình độc lập từng concern giúp so sánh nguyên nhân. Không mặc định tắt aspect bảo vệ bắt buộc ở production để tìm lỗi; ưu tiên test fixture và chế độ chẩn đoán đã được phê duyệt.

**Cân nhắc decorator hoặc lời gọi tường minh.** Khi số điểm áp dụng nhỏ hoặc thứ tự hành vi là cốt lõi nghiệp vụ, một chuỗi decorator được lắp ráp tại composition root có thể dễ đọc hơn pointcut rộng. AOP vẫn phù hợp khi hành vi xuyên suốt nhiều join point và cơ chế áp dụng được quản lý tốt.

**Tạo bằng chứng vận hành.** Advice count, error rate, CPU, allocation, p95/p99 latency, dropped telemetry, queue depth và số lần retry giúp phát hiện bất thường mà đọc source không thấy. Chọn metric phù hợp concern, tránh tự biến instrumentation thành điểm nghẽn.

## 13. Sai lầm khi “sửa” sự khó hiểu

| Cách xử lý có vẻ nhanh | Tại sao chưa đủ | Cách cải thiện |
| --- | --- | --- |
| Chỉ thêm comment ở method gốc | Comment có thể lỗi thời khi pointcut đổi | Tài liệu hóa hợp đồng ở nguồn cấu hình và test áp dụng |
| Chỉ đổi tên aspect rõ hơn | Không giải quyết được order và side effect | Ghi rõ phạm vi, semantics, failure policy và precedence |
| Dồn nhiều aspect thành một file | Vẫn có nhiều lý do thay đổi trong cùng đơn vị | Tách theo concern và quản lý tương tác |
| Bắt tất cả method rồi dùng if trong advice | Phạm vi chặn quá lớn và khó đánh giá overhead | Thu hẹp pointcut ngay từ đầu |
| Chỉ kiểm thử đường chạy thành công | Bỏ qua retry, ngoại lệ và cleanup | Thử lỗi, negative cases, concurrency và async |
| Tắt một advice rồi kết luận đã tìm nguyên nhân | Có thể chỉ thay thứ tự và che triệu chứng | Dựng call chain, so paired baseline và ghi số lần target gọi |
| In toàn bộ input/output để debug | Có thể lộ dữ liệu và làm tăng chi phí | Log có cấu trúc, tối thiểu hóa thông tin, sampling theo chính sách |
| Giả định mọi advice “trong suốt” | Retry/cache/short-circuit có chủ đích thay đổi semantics | Phân loại rõ advice quan sát và advice điều khiển |

Một số thay đổi thiết kế nên được nhìn như một thay đổi API: nếu thêm aspect làm method có thể được gọi hai lần, trả từ cache hoặc đổi loại exception, review và test theo tác động tới caller chứ không theo số dòng mới của aspect.

## 14. Checklist review một aspect trước khi triển khai

- [ ] Aspect có **một concern rõ ràng** và một chủ thể chịu trách nhiệm.
- [ ] Pointcut dựa trên hợp đồng áp dụng, có danh sách trường hợp thuộc/không thuộc phạm vi.
- [ ] Biết chính xác cơ chế interception/weaving và các giới hạn join point.
- [ ] Có tài liệu hóa thứ tự với các advice khác và test order quan trọng.
- [ ] Xác định số lần target được gọi: một, nhiều, hoặc có thể bị bỏ qua.
- [ ] Giá trị trả về và exception được bảo toàn hoặc biến đổi đúng hợp đồng công khai.
- [ ] Side effect do advice tạo ra được công bố: log, network, cache, transaction, retry, tracing.
- [ ] Chính sách thất bại của advice rõ ràng, đặc biệt lỗi logging/telemetry/cleanup.
- [ ] Không để pointcut tự bắt hạ tầng của nó và gây recursion hay nhân đôi.
- [ ] State/concurrency và async context được kiểm thử ở đường chạy thực.
- [ ] Có cách xem danh sách advice được áp dụng vào một điểm và truy dấu khi debug.
- [ ] Đã đo overhead với tải đại diện nếu advice nằm trên đường gọi nóng.
- [ ] Test tích hợp xác minh semantics khi nhiều advice chồng lên nhau.
- [ ] Môi trường runtime dùng đúng phiên bản, artifact và cấu hình đã được kiểm thử.

**Nguyên tắc thực hành:** AOP có giá trị khi làm cho trách nhiệm xuyên suốt dễ duy trì **mà không khiến hành vi quan trọng trở thành bí ẩn**. Khi thêm một advice, luôn biết nó chạy ở đâu, khi nào, theo thứ tự nào, có thể đổi điều gì và làm sao chứng minh được điều đó. Nếu những câu hỏi này không có đáp án, việc ẩn thêm hành vi sau pointcut sẽ khiến chương trình ngày càng khó hiểu dù thân phương thức có ít dòng hơn.


</details>

- [Quay lại đầu trang](#back-to-top)
