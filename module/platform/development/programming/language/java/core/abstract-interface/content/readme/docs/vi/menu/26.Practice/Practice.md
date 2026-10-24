<a id="back-to-top"></a>

# Thực hành

## Menu
- [Xây dựng hợp đồng thanh toán với nhiều cách triển khai](#practice-001)
- [Tạo lớp trừu tượng chia sẻ kiểm tra dữ liệu và nhật ký](#practice-002)
- [Thực hiện Template Method cho quy trình nhập dữ liệu](#practice-003)
- [Giải quyết bài toán hình thoi với nhiều interface](#practice-004)
- [Tách interface lớn thành các vai trò nhỏ](#practice-005)
- [Thay cây kế thừa khó mở rộng bằng composition](#practice-006)
- [Xây dựng kho dữ liệu generic với nhiều cách lưu trữ](#practice-007)
- [Mô hình hóa kết quả bằng sealed interface và record](#practice-008)
- [So sánh lambda với anonymous class trong cùng bài toán](#practice-009)
- [Hoàn thiện ví dụ tích hợp và bộ kiểm thử hợp đồng](#practice-010)

## <a id="practice-001">Xây dựng hợp đồng thanh toán với nhiều cách triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng hợp đồng thanh toán với nhiều cách triển khai

Một hệ thống checkout có thể cho phép thanh toán bằng tiền mặt, thẻ hoặc ví điện tử. Mỗi cách thanh toán có quy trình riêng, nhưng phần còn lại của ứng dụng vẫn cần một cách thống nhất để yêu cầu thanh toán và nhận kết quả. Trong Java, ta có thể dùng một interface làm **hợp đồng thanh toán**, sau đó viết nhiều lớp triển khai hợp đồng đó.

Bài thực hành này xây dựng một ví dụ Java 17+ chạy được. Các lớp thanh toán chỉ mô phỏng kết quả để minh họa thiết kế; chúng không kết nối ngân hàng, ví điện tử hay thực hiện giao dịch thật.

## 1. Xác định hợp đồng trước khi viết các lớp

Trước tiên, xác định những điều mà mọi phương thức thanh toán phải cung cấp:

- Có tên để giao diện hoặc log có thể hiển thị.
- Nhận một yêu cầu thanh toán hợp lệ.
- Trả về một kết quả có cấu trúc thống nhất, bao gồm mã đơn hàng, số tiền, trạng thái và thông điệp.
- Thể hiện kết quả nghiệp vụ như được chấp nhận, bị từ chối hoặc đang chờ xử lý.
- Có cách báo lỗi kỹ thuật nhất quán khi không thể hoàn tất quy trình thanh toán.

Hợp đồng gồm cả chữ ký phương thức và ý nghĩa của chúng. Chỉ có cùng tên và kiểu tham số là chưa đủ: mọi lớp triển khai cũng phải tuân thủ những quy tắc mà bên gọi dựa vào.

Trong ví dụ này, ta dùng ba kiểu dữ liệu:

- `PaymentRequest`: dữ liệu đầu vào, yêu cầu mã đơn hàng không rỗng và số tiền lớn hơn 0.
- `PaymentResult`: kết quả thống nhất cho mọi cách thanh toán.
- `PaymentMethod`: interface quy định tên và thao tác thanh toán.

## 2. Tạo interface PaymentMethod

~~~java
import java.util.Objects;

enum PaymentStatus {
    APPROVED,
    DECLINED,
    PENDING
}

record PaymentRequest(String orderId, long amountVnd) {
    PaymentRequest {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }
}

record PaymentResult(
        String orderId,
        String method,
        long amountVnd,
        PaymentStatus status,
        String message) {
    PaymentResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }
}

final class PaymentException extends Exception {
    PaymentException(String message) {
        super(message);
    }

    PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

interface PaymentMethod {
    String name();

    PaymentResult pay(PaymentRequest request) throws PaymentException;
}
~~~

Đây là phần khai báo dữ liệu và hợp đồng. `PaymentStatus` phân biệt kết quả nghiệp vụ; `PaymentException` dành cho lỗi kỹ thuật mà phương thức không thể xử lý tại chỗ. Một giao dịch bị từ chối là kết quả nghiệp vụ bình thường, nên có thể biểu diễn bằng `DECLINED` thay vì coi mọi trường hợp đó là lỗi chương trình.

Hợp đồng `PaymentMethod` đặt ra các quy tắc sau:

1. Bên gọi cung cấp một `PaymentRequest` hợp lệ, không phải `null`.
2. `pay` trả về một `PaymentResult` không phải `null`, gắn với đúng mã đơn hàng và số tiền yêu cầu.
3. `APPROVED`, `DECLINED` và `PENDING` là các trạng thái nghiệp vụ có ý nghĩa riêng; không được xem `PENDING` như đã thanh toán thành công.
4. Lỗi kỹ thuật có thể được báo qua `PaymentException`; mọi implementation cần giữ chính sách lỗi nhất quán.
5. `name()` trả về tên không rỗng, có thể dùng để hiển thị hoặc ghi log.

Các quy tắc ngữ nghĩa này quan trọng ngang chữ ký Java. Chúng giúp lớp sử dụng `PaymentMethod` không cần biết từng implementation làm việc bên trong ra sao.

## 3. Tạo nhiều implementation

Mỗi lớp dưới đây thực hiện cùng một hợp đồng nhưng cung cấp tên và thông điệp riêng. Trong ví dụ, tất cả kết quả đều là mô phỏng.

~~~java
import java.util.Objects;

final class CashPayment implements PaymentMethod {
    @Override
    public String name() {
        return "Cash";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(
            request.orderId(),
            name(),
            request.amountVnd(),
            PaymentStatus.APPROVED,
            "Cash payment simulated"
        );
    }
}

final class CardPayment implements PaymentMethod {
    @Override
    public String name() {
        return "Card";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(
            request.orderId(),
            name(),
            request.amountVnd(),
            PaymentStatus.APPROVED,
            "Card payment simulated"
        );
    }
}

final class EWalletPayment implements PaymentMethod {
    @Override
    public String name() {
        return "E-wallet";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(
            request.orderId(),
            name(),
            request.amountVnd(),
            PaymentStatus.APPROVED,
            "E-wallet payment simulated"
        );
    }
}
~~~

Cả ba lớp đều dùng `implements PaymentMethod` và cung cấp các phương thức bắt buộc. Mã gọi có thể làm việc với kiểu `PaymentMethod` mà không cần phụ thuộc vào tên lớp cụ thể.

Ví dụ mô phỏng này không kiểm tra hạn mức thẻ, số dư ví hay xác nhận của ngân hàng. Trong hệ thống thật, mỗi implementation sẽ gọi một nhà cung cấp hoặc xử lý một quy trình riêng, đồng thời phải tuân thủ cùng hợp đồng đầu vào, đầu ra và lỗi.

## 4. Để dịch vụ checkout phụ thuộc vào interface

Lớp checkout không cần biết đang dùng tiền mặt, thẻ hay ví điện tử. Nó chỉ cần một `PaymentMethod`.

~~~java
import java.util.Objects;

final class CheckoutService {
    private final PaymentMethod paymentMethod;

    CheckoutService(PaymentMethod paymentMethod) {
        this.paymentMethod =
            Objects.requireNonNull(paymentMethod, "paymentMethod");
    }

    PaymentResult checkout(PaymentRequest request)
            throws PaymentException {
        Objects.requireNonNull(request, "request");
        return paymentMethod.pay(request);
    }
}
~~~

Việc truyền implementation qua constructor được gọi là **dependency injection**. `CheckoutService` không tự tạo `new CardPayment()` bên trong, vì vậy bên gọi có thể chọn cách thanh toán phù hợp khi khởi tạo dịch vụ.

Thiết kế này cũng áp dụng nguyên tắc phụ thuộc vào abstraction: checkout dựa trên hợp đồng ổn định, còn chi tiết thanh toán nằm trong những lớp riêng. Khi cần thêm chuyển khoản ngân hàng, có thể tạo `BankTransferPayment implements PaymentMethod` mà không cần sửa logic checkout, miễn là lớp mới tuân thủ hợp đồng.

## 5. Chương trình hoàn chỉnh có thể chạy

Lưu toàn bộ các khai báo trên cùng với đoạn `main` dưới đây vào một tệp tên **PaymentContractDemo.java**. Để tránh trùng khai báo, khi ghép thành tệp hoàn chỉnh, giữ các import ở đầu tệp một lần và đặt tất cả các kiểu phụ trợ cùng với lớp public trong cùng tệp.

~~~java
import java.util.List;
import java.util.Objects;

enum PaymentStatus {
    APPROVED,
    DECLINED,
    PENDING
}

record PaymentRequest(String orderId, long amountVnd) {
    PaymentRequest {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }
}

record PaymentResult(
        String orderId,
        String method,
        long amountVnd,
        PaymentStatus status,
        String message) {
    PaymentResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }
}

final class PaymentException extends Exception {
    PaymentException(String message) {
        super(message);
    }

    PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

interface PaymentMethod {
    String name();

    PaymentResult pay(PaymentRequest request) throws PaymentException;
}

final class CashPayment implements PaymentMethod {
    @Override
    public String name() {
        return "Cash";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(request.orderId(), name(),
            request.amountVnd(), PaymentStatus.APPROVED,
            "Cash payment simulated");
    }
}

final class CardPayment implements PaymentMethod {
    @Override
    public String name() {
        return "Card";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(request.orderId(), name(),
            request.amountVnd(), PaymentStatus.APPROVED,
            "Card payment simulated");
    }
}

final class EWalletPayment implements PaymentMethod {
    @Override
    public String name() {
        return "E-wallet";
    }

    @Override
    public PaymentResult pay(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        return new PaymentResult(request.orderId(), name(),
            request.amountVnd(), PaymentStatus.APPROVED,
            "E-wallet payment simulated");
    }
}

final class CheckoutService {
    private final PaymentMethod paymentMethod;

    CheckoutService(PaymentMethod paymentMethod) {
        this.paymentMethod =
            Objects.requireNonNull(paymentMethod, "paymentMethod");
    }

    PaymentResult checkout(PaymentRequest request)
            throws PaymentException {
        Objects.requireNonNull(request, "request");
        return paymentMethod.pay(request);
    }
}

public class PaymentContractDemo {
    public static void main(String[] args) throws PaymentException {
        List<PaymentMethod> methods = List.of(
            new CashPayment(),
            new CardPayment(),
            new EWalletPayment()
        );

        for (int i = 0; i < methods.size(); i++) {
            PaymentMethod method = methods.get(i);
            PaymentRequest request =
                new PaymentRequest("DEMO-" + (i + 1), 150_000L);
            CheckoutService checkout = new CheckoutService(method);
            PaymentResult result = checkout.checkout(request);

            System.out.printf(
                "%s | %s | %d | %s | %s%n",
                result.orderId(),
                result.method(),
                result.amountVnd(),
                result.status(),
                result.message()
            );
        }
    }
}
~~~

Chạy bằng Java 17 trở lên:

~~~bash
javac PaymentContractDemo.java
java PaymentContractDemo
~~~

Kết quả:

~~~text
DEMO-1 | Cash | 150000 | APPROVED | Cash payment simulated
DEMO-2 | Card | 150000 | APPROVED | Card payment simulated
DEMO-3 | E-wallet | 150000 | APPROVED | E-wallet payment simulated
~~~

Ba đơn hàng demo được tạo riêng để so sánh ba implementation. Đây chỉ là mô phỏng; một ứng dụng thực tế không được thử lần lượt nhiều phương thức trên cùng một đơn hàng theo cách có thể phát sinh nhiều lần thu tiền.

**Lưu ý khi ghép ví dụ:** các mục 2–4 minh họa từng phần để giải thích thiết kế; mục 5 là tệp độc lập hoàn chỉnh. Chỉ biên dịch một phiên bản `PaymentContractDemo.java` có mỗi kiểu được khai báo đúng một lần.

## 6. Vì sao hợp đồng tốt hơn các nhánh if/else rải rác?

Một cách triển khai ban đầu có thể kiểm tra loại thanh toán ở nhiều nơi:

~~~java
if (methodType.equals("CASH")) {
    // Handle cash
} else if (methodType.equals("CARD")) {
    // Handle card
} else if (methodType.equals("WALLET")) {
    // Handle wallet
}
~~~

Khi thêm phương thức mới, các nhánh này có thể phải được cập nhật ở nhiều vị trí. Nếu từng nhánh có quy tắc kết quả hoặc lỗi khác nhau, việc bảo trì sẽ khó hơn.

Với `PaymentMethod`, logic gọi thanh toán chỉ cần gọi `paymentMethod.pay(request)`. Chọn implementation là trách nhiệm của nơi cấu hình ứng dụng, factory hoặc tầng điều phối. Đừng xóa mọi điều kiện nghiệp vụ: điều kiện xác thực, giới hạn rủi ro và quyền sử dụng phương thức vẫn có thể cần thiết. Mục tiêu là tập trung hành vi riêng của mỗi phương thức trong implementation tương ứng.

## 7. Cách kiểm tra một implementation có tuân thủ hợp đồng

Khi viết lớp mới như `BankTransferPayment`, kiểm tra các điều sau:

- Tên phương thức không rỗng và có ý nghĩa.
- Đầu vào hợp lệ được xử lý theo đúng quy tắc đã công bố.
- Kết quả không phải `null`, có đúng mã đơn hàng và số tiền được yêu cầu.
- `APPROVED`, `DECLINED` và `PENDING` được dùng đúng nghĩa; không báo thành công khi chưa có xác nhận.
- Lỗi kỹ thuật tuân thủ chính sách ngoại lệ đã định nghĩa.
- Không làm thay đổi hợp đồng chung bằng một hành vi bất ngờ, chẳng hạn trả số tiền khác mà không ghi rõ quy tắc phí.
- Mã gọi không cần ép kiểu về lớp cụ thể để sử dụng hành vi đã cam kết.

Một bài kiểm tra hữu ích là chạy cùng một bộ kiểm tra hợp đồng cho mọi implementation. Có thể dùng JUnit để kiểm tra các trường hợp đầu vào hợp lệ, đầu vào không hợp lệ, kết quả bị từ chối và lỗi nhà cung cấp. Khi kiểm tra lỗi, phân biệt lỗi đầu vào do người gọi và lỗi kỹ thuật phát sinh trong quá trình xử lý.

## 8. Mở rộng sang thanh toán thực tế

Ví dụ demo giữ kết quả đơn giản để tập trung vào interface. Một tích hợp thanh toán thực tế thường cần thêm các khái niệm sau:

- **Mã giao dịch của nhà cung cấp:** để đối chiếu và hỗ trợ tra soát.
- **Idempotency key:** để việc gửi lại yêu cầu sau timeout không tạo giao dịch trùng. Mã đơn hàng đơn thuần chỉ đủ khi nhà cung cấp và thiết kế của bạn bảo đảm tính duy nhất cho mỗi lần thanh toán.
- **Trạng thái chờ:** có thể nhà cung cấp đã nhận yêu cầu nhưng chưa có kết quả cuối cùng. Khi đó cần truy vấn hoặc nhận webhook; không nên tự chuyển `PENDING` thành `APPROVED`.
- **Timeout và lỗi mạng:** timeout không chứng minh rằng giao dịch thất bại; hệ thống cần xác minh trạng thái trước khi quyết định thử lại.
- **Bảo mật:** không ghi log dữ liệu thẻ nhạy cảm, bảo vệ thông tin xác thực và tuân thủ yêu cầu bảo mật của nhà cung cấp.
- **Phí và tiền tệ:** nếu mỗi phương thức tính phí khác nhau, hợp đồng cần định nghĩa rõ số tiền yêu cầu, phí, tổng tiền bị thu và đơn vị tiền tệ.

Khi các yêu cầu này xuất hiện, hãy mở rộng `PaymentRequest` và `PaymentResult` có chủ đích, rồi cập nhật hợp đồng cùng tất cả implementation. Không nên để mỗi lớp tự hiểu trường dữ liệu theo một nghĩa khác nhau.

## 9. Lỗi thiết kế thường gặp

- **Đặt logic của từng phương thức trong CheckoutService:** làm dịch vụ phụ thuộc vào chi tiết và buộc phải sửa khi thêm cách thanh toán. Hãy để implementation thực hiện hành vi riêng qua interface.
- **Tạo interface chỉ có tên nhưng không có hợp đồng rõ ràng:** chữ ký phương thức cần đi kèm ý nghĩa đầu vào, đầu ra và lỗi.
- **Cho các implementation trả kết quả không nhất quán:** ví dụ lớp này trả `null`, lớp khác trả một kết quả thiếu mã đơn hàng. Bên gọi sẽ phải viết nhiều nhánh phòng vệ và không còn tin được hợp đồng.
- **Dùng exception cho mọi trường hợp bị từ chối:** từ chối thanh toán có thể là kết quả nghiệp vụ bình thường; phân biệt nó với lỗi kỹ thuật.
- **Xem PENDING như thành công:** kết quả chưa xác định không đồng nghĩa tiền đã được thu.
- **Dùng cùng một đơn hàng để chạy thử tất cả phương thức thanh toán thật:** có thể dẫn tới nhiều lần thu tiền. Dùng đơn hàng thử riêng hoặc môi trường sandbox.
- **Cho rằng interface tự bảo đảm tính đúng đắn:** trình biên dịch kiểm tra chữ ký, còn quy tắc nghiệp vụ vẫn cần được thể hiện bằng tài liệu, kiểm thử và cách triển khai.

Điểm cốt lõi của bài thực hành là định nghĩa một hợp đồng ổn định cho thao tác thanh toán, triển khai hợp đồng bằng nhiều lớp độc lập và để dịch vụ checkout làm việc qua kiểu interface. Khi thêm một phương thức mới, ta có thể bổ sung implementation mà vẫn giữ cách gọi chung và những cam kết mà toàn bộ hệ thống dựa vào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-002">Tạo lớp trừu tượng chia sẻ kiểm tra dữ liệu và nhật ký</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo lớp trừu tượng chia sẻ kiểm tra dữ liệu và nhật ký

Khi nhiều lớp xử lý dữ liệu có cùng quy trình đầu vào, chúng thường lặp lại các bước như kiểm tra dữ liệu rỗng, chuẩn hóa chuỗi, ghi nhật ký bắt đầu/kết thúc và xử lý lỗi. Nếu mỗi lớp tự viết lại các bước này, quy tắc dễ lệch nhau: lớp này quên kiểm tra `null`, lớp khác ghi log không nhất quán, hoặc thứ tự kiểm tra và xử lý khác nhau.

Một **lớp trừu tượng** (*abstract class*) cho phép gom phần quy trình chung vào một nơi, đồng thời để lớp con định nghĩa phần xử lý riêng. Bài thực hành này xây dựng một bộ xử lý văn bản có chung bước xác thực và ghi nhật ký; các lớp con chỉ quyết định biến đổi dữ liệu đã hợp lệ như thế nào.

## 1. Xác định phần chung và phần thay đổi

Giả sử ứng dụng có hai yêu cầu: chuyển văn bản thành chữ hoa và đếm số từ. Hai yêu cầu khác nhau ở thao tác xử lý, nhưng cùng cần:

1. Ghi nhật ký khi bắt đầu.
2. Từ chối dữ liệu `null` hoặc chỉ chứa khoảng trắng.
3. Loại bỏ khoảng trắng ở hai đầu trước khi xử lý.
4. Chạy logic chuyên biệt của bộ xử lý.
5. Ghi nhật ký thành công hoặc ghi lại lỗi đầu vào.
6. Trả kết quả cho bên gọi.

Các bước này tạo thành một quy trình ổn định. Chỉ bước xử lý chuyên biệt cần thay đổi giữa các lớp. Ta sẽ đặt quy trình chung trong phương thức `process` của lớp trừu tượng và khai báo một phương thức trừu tượng `transform` để lớp con cài đặt.

Cách phân chia này giúp mọi bộ xử lý tuân theo cùng một trình tự mà không cần sao chép mã xác thực và nhật ký.

## 2. Vì sao dùng abstract class?

Lớp trừu tượng phù hợp khi nhiều lớp con cần dùng chung cả **hành vi** lẫn **trạng thái**. Lớp cơ sở có thể giữ tên bộ xử lý, triển khai logic xác thực và cung cấp phương thức ghi log. Lớp con chỉ cần triển khai điểm mở rộng.

Một interface thường mô tả hợp đồng mà lớp triển khai phải đáp ứng; interface cũng có thể có phương thức `default` và phương thức `static`, nhưng không cung cấp trạng thái instance thông thường bằng các trường instance. Abstract class phù hợp khi ta muốn chia sẻ trường, constructor, phương thức đã triển khai và các phương thức trừu tượng trong cùng một hệ phân cấp.

Không nên tạo lớp trừu tượng chỉ vì muốn tránh vài dòng lặp lại. Hãy dùng nó khi các lớp thực sự thuộc cùng một khái niệm và có một quy trình chung ổn định. Nếu chỉ cần truyền một quy tắc xử lý đơn giản, hàm lambda hoặc một interface chức năng có thể nhẹ nhàng hơn.

## 3. Thiết kế lớp cơ sở

Lớp `AbstractTextProcessor` có ba trách nhiệm:

- `process(String input)` là điểm vào công khai và điều phối quy trình chung.
- `validate(String input)` kiểm tra dữ liệu và trả về chuỗi đã loại bỏ khoảng trắng đầu/cuối.
- `transform(String input)` là điểm mở rộng để lớp con thực hiện công việc riêng.

Phương thức `process` được khai báo `final` để lớp con không thể ghi đè rồi vô tình bỏ qua xác thực hoặc ghi log. Phương thức `validate` và `log` là `private` vì chỉ lớp cơ sở cần gọi chúng. `transform` là `protected abstract` để các lớp con có thể cài đặt nhưng người gọi bên ngoài không thể trực tiếp gọi nó thông qua API công khai.

Quy trình dùng `try/catch` để ghi nhật ký khi xác thực hoặc xử lý ném `IllegalArgumentException`, rồi ném lại ngoại lệ để bên gọi vẫn biết thao tác thất bại. Việc ghi log không được âm thầm biến lỗi thành kết quả hợp lệ.

## 4. Chương trình Java 17+ hoàn chỉnh

Lưu toàn bộ mã dưới đây vào tệp `SharedValidationLoggingDemo.java`. Ví dụ đặt lớp trừu tượng, hai lớp con và lớp chạy trong cùng một tệp để dễ biên dịch; chỉ `SharedValidationLoggingDemo` là lớp `public`.

~~~java
import java.util.Locale;

abstract class AbstractTextProcessor {
    private final String name;

    protected AbstractTextProcessor(String name) {
        this.name = name;
    }

    public final String process(String input) {
        log("Bắt đầu xử lý");
        try {
            String validated = validate(input);
            String result = transform(validated);
            log("Xử lý thành công");
            return result;
        } catch (IllegalArgumentException ex) {
            log("Xử lý thất bại: " + ex.getMessage());
            throw ex;
        }
    }

    private String validate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(
                "Dữ liệu không được null hoặc chỉ chứa khoảng trắng"
            );
        }
        return input.strip();
    }

    protected abstract String transform(String input);

    private void log(String message) {
        System.out.println("[" + name + "] " + message);
    }
}

final class UppercaseProcessor extends AbstractTextProcessor {
    UppercaseProcessor() {
        super("UPPER");
    }

    @Override
    protected String transform(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}

final class WordCountProcessor extends AbstractTextProcessor {
    WordCountProcessor() {
        super("WORDS");
    }

    @Override
    protected String transform(String input) {
        return "Số từ: " + input.split("\\s+").length;
    }
}

public class SharedValidationLoggingDemo {
    public static void main(String[] args) {
        AbstractTextProcessor uppercase = new UppercaseProcessor();
        AbstractTextProcessor wordCount = new WordCountProcessor();

        System.out.println("Kết quả: " + uppercase.process("  hello Java  "));
        System.out.println("Kết quả: " + wordCount.process("learn abstract class"));

        try {
            uppercase.process("   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("Đã bắt lỗi: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac SharedValidationLoggingDemo.java
java SharedValidationLoggingDemo
~~~

Kết quả stdout chính xác:

~~~text
[UPPER] Bắt đầu xử lý
[UPPER] Xử lý thành công
Kết quả: HELLO JAVA
[WORDS] Bắt đầu xử lý
[WORDS] Xử lý thành công
Kết quả: Số từ: 3
[UPPER] Bắt đầu xử lý
[UPPER] Xử lý thất bại: Dữ liệu không được null hoặc chỉ chứa khoảng trắng
Đã bắt lỗi: Dữ liệu không được null hoặc chỉ chứa khoảng trắng
~~~

Kết quả cho thấy hai lớp con có hành vi khác nhau nhưng cùng tuân theo trình tự xử lý. `UppercaseProcessor` chuyển chuỗi thành chữ hoa; `WordCountProcessor` đếm từ. Cả hai đều tự động dùng cùng bước kiểm tra và cùng định dạng log vì kế thừa `process` từ lớp cơ sở.

Lần gọi cuối truyền một chuỗi chỉ chứa khoảng trắng. Phương thức `validate` ném `IllegalArgumentException`; lớp cơ sở ghi log thất bại rồi ném lại ngoại lệ. Khối `catch` trong `main` xử lý ngoại lệ đó và in thông báo cuối cùng.

## 5. Hiểu luồng chạy và Template Method

Thiết kế trên là một ví dụ của mẫu **Template Method**. Phương thức `process` định nghĩa khung thuật toán: ghi log bắt đầu → xác thực → biến đổi → ghi log thành công. Bước `transform` được giao cho lớp con.

Luồng chạy thành công:

~~~text
process(input)
    |
    v
log("Bắt đầu xử lý")
    |
    v
validate(input)
    |
    v
transform(validated)
    |
    v
log("Xử lý thành công")
    |
    v
return result
~~~

Nếu xác thực hoặc `transform` ném `IllegalArgumentException`, luồng chuyển sang nhánh ghi log thất bại rồi ném lại ngoại lệ. Những ngoại lệ thuộc loại khác không bị bắt trong ví dụ này, nên sẽ truyền lên bên gọi. Đây là lựa chọn có chủ đích: chỉ những lỗi được xác định trong hợp đồng xử lý mới được ghi log theo nhánh lỗi dự kiến.

Từ khóa `final` trên `process` giúp bảo vệ khung thuật toán. Nếu không có nó, một lớp con có thể ghi đè phương thức và quên gọi `validate` hoặc bỏ qua nhật ký. Dùng `final` khi việc tuân thủ thứ tự chung là một bất biến của thiết kế; nếu lớp con thực sự cần thay đổi toàn bộ quy trình, có thể cần một kiến trúc khác.

## 6. Vai trò của constructor, mức truy cập và override

Constructor `AbstractTextProcessor(String name)` được khai báo `protected` để lớp con có thể gọi nó qua `super(...)`. Không thể khởi tạo trực tiếp một lớp trừu tượng bằng `new AbstractTextProcessor(...)`; phải tạo một lớp cụ thể triển khai phương thức trừu tượng.

Trường `name` là `private final`, nên chỉ lớp cơ sở truy cập và gán giá trị trong constructor. Điều này giữ tên logger nhất quán trong suốt vòng đời đối tượng. Trong ứng dụng thực tế, constructor cũng nên kiểm tra tên có hợp lệ hay không, ví dụ từ chối `null` hoặc chuỗi rỗng.

Các lớp con đánh dấu `transform` bằng `@Override` để trình biên dịch xác minh chữ ký ghi đè. Nếu viết sai tên phương thức hoặc sai kiểu tham số, lỗi sẽ được phát hiện lúc biên dịch. Phương thức trừu tượng không có thân; lớp cụ thể phải cung cấp phần cài đặt.

`UppercaseProcessor` dùng `Locale.ROOT` khi chuyển chữ hoa để tránh kết quả phụ thuộc locale mặc định của máy, điều hữu ích với phép chuẩn hóa mang tính kỹ thuật. Ví dụ đếm từ tách chuỗi đã chuẩn hóa bằng biểu thức chính quy `\\s+`; đây là cách đếm từ đơn giản cho văn bản phân tách bằng khoảng trắng, không phải bộ phân tích ngôn ngữ tự nhiên cho mọi ngôn ngữ.

## 7. Điều chỉnh thiết kế cho ứng dụng thật

Ví dụ dùng `System.out.println` để người học nhìn thấy kết quả ngay. Trong ứng dụng thực tế, hãy dùng hệ thống logging như SLF4J với Logback hoặc API logging phù hợp của dự án, để có cấp độ log, timestamp, thông tin tương quan và khả năng định tuyến đầu ra. Tránh ghi toàn bộ dữ liệu nhạy cảm của người dùng vào log.

Quy tắc xác thực nên phản ánh yêu cầu miền nghiệp vụ. Ví dụ, bộ xử lý mã đơn hàng có thể kiểm tra định dạng mã; bộ xử lý email có thể kiểm tra các điều kiện được ứng dụng yêu cầu. Không nên đặt quy tắc riêng của một lớp con vào lớp cơ sở nếu các lớp khác không có cùng điều kiện đó.

Cũng cần phân biệt xác thực đầu vào với xác thực kết quả. Lớp cơ sở hiện kiểm tra chuỗi đầu vào trước khi gọi `transform`. Nếu mọi kết quả đều phải tuân theo một hợp đồng chung, có thể thêm một bước kiểm tra kết quả sau `transform`; hãy làm điều đó chỉ khi có quy tắc chung rõ ràng.

Nếu cần ghi log thời lượng, có thể đo thời gian bắt đầu và kết thúc trong `process`. Nếu muốn log cả ngoại lệ ngoài `IllegalArgumentException`, cần quyết định rõ liệu phương thức có bắt `RuntimeException`, ngoại lệ có kiểm tra hay chỉ ghi log tại lớp gọi. Bắt quá rộng rồi nuốt lỗi sẽ che giấu sự cố và khiến bên gọi tưởng rằng xử lý thành công.

## 8. Lỗi thường gặp

- **Lặp lại validation trong từng lớp con:** tạo ra nguy cơ quy tắc khác nhau. Đặt quy tắc thực sự dùng chung ở lớp cơ sở.
- **Để lớp con ghi đè phương thức điều phối:** có thể phá vỡ thứ tự xác thực và log. Cân nhắc khai báo phương thức khung là `final`.
- **Gọi `transform` trước khi kiểm tra đầu vào:** dữ liệu rỗng có thể gây lỗi khó hiểu bên trong lớp con. Xác thực trước bước xử lý.
- **Ghi log rồi nuốt ngoại lệ:** làm bên gọi không biết rằng thao tác thất bại. Ghi log và ném lại lỗi, hoặc chuyển đổi sang ngoại lệ miền nghiệp vụ theo một hợp đồng rõ ràng.
- **Bắt mọi ngoại lệ mà không phân biệt:** có thể che lỗi lập trình hoặc lỗi hệ thống. Chỉ bắt những loại lỗi mà lớp có thể xử lý có ý nghĩa.
- **Đưa mọi thứ vào lớp cơ sở:** lớp cha sẽ khó bảo trì nếu chứa quá nhiều quy tắc chỉ đúng với một lớp con. Chỉ chia sẻ phần thật sự chung.
- **Dùng kế thừa chỉ để tái sử dụng một phương thức nhỏ:** có thể tạo quan hệ lớp cứng nhắc. Nếu các đối tượng không có quan hệ “là một”, hãy cân nhắc composition hoặc truyền một interface xử lý vào constructor.

## 9. Checklist khi xây dựng lớp trừu tượng dùng chung

Trước khi áp dụng mẫu này vào dự án, hãy tự kiểm tra:

1. Các lớp con có cùng một quy trình nghiệp vụ cốt lõi không?
2. Bước nào bắt buộc phải luôn chạy và nên nằm trong phương thức khung?
3. Bước nào thực sự khác nhau và nên trở thành phương thức trừu tượng hoặc hook?
4. Mức truy cập của từng trường/phương thức có giới hạn quyền truy cập phù hợp không?
5. Có nên dùng `final` để bảo vệ các bất biến của quy trình không?
6. Khi lỗi xảy ra, lớp cơ sở sẽ ghi log, chuyển đổi hay ném lại ngoại lệ nào?
7. Ví dụ có thể biên dịch, chạy và cho đầu ra dự đoán được không?

Nếu câu trả lời cho các bước chung và các bước thay đổi rõ ràng, abstract class có thể làm cho thiết kế dễ hiểu hơn, giảm trùng lặp và giữ hành vi nhất quán. Nếu ranh giới chưa rõ, hãy bắt đầu bằng hai lớp cụ thể và chỉ trích xuất lớp cơ sở sau khi đã nhận diện được quy trình chung.

## 10. Ghi nhớ

Một lớp trừu tượng hữu ích khi nhiều lớp con cần dùng chung trạng thái và một phần quy trình, nhưng vẫn có các bước xử lý riêng. Trong bài này, `process` là Template Method điều phối xác thực, biến đổi và ghi log; `transform` là điểm mở rộng của từng lớp con. Hãy bảo vệ các bước bắt buộc, kiểm tra dữ liệu trước khi xử lý, ghi log có ý nghĩa và để ngoại lệ tiếp tục truyền đến nơi có thể xử lý chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-003">Thực hiện Template Method cho quy trình nhập dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hiện Template Method cho quy trình nhập dữ liệu

Template Method là mẫu thiết kế hành vi trong đó lớp cha định nghĩa **bộ khung cố định của một thuật toán**, còn các lớp con tùy biến một số bước cụ thể. Với quy trình nhập dữ liệu, lớp cha có thể quyết định thứ tự kiểm tra nguồn, đọc dữ liệu, phân tích từng dòng, xác thực bản ghi, lưu kết quả và thông báo hoàn tất. Mỗi định dạng như CSV hoặc TSV chỉ cần cung cấp cách phân tích dòng và cách lưu phù hợp.

Mẫu này hữu ích khi nhiều quy trình nhập dữ liệu có cùng các bước và thứ tự, nhưng khác nhau ở một vài chi tiết. Nó giúp tránh việc mỗi lớp tự viết lại toàn bộ quy trình và vô tình bỏ qua một bước quan trọng.

## 1. Cấu trúc Template Method trong bài toán nhập dữ liệu

Trong ví dụ này, `DataImporter` là lớp cha trừu tượng. Phương thức `importFile()` là **Template Method** và được khai báo `final`, vì vậy lớp con không thể thay đổi trình tự các bước.

Quy trình được giữ nhất quán như sau:

1. Kiểm tra đường dẫn nguồn có trỏ tới tệp thông thường hay không.
2. Đọc các dòng từ tệp bằng UTF-8.
3. Bỏ qua dòng trống; phân tích từng dòng thành bản ghi.
4. Xác thực các trường của bản ghi.
5. Gọi bước lưu dữ liệu do lớp con triển khai.
6. Báo số bản ghi đã nhập thành công.

`parseLine()` và `persist()` là các bước trừu tượng buộc lớp con triển khai. `validateSource()`, `readSource()`, `validateRecord()` và `afterImport()` là các hook có cài đặt mặc định mà lớp con có thể ghi đè khi cần. Không phải hook nào cũng cần được ghi đè.

## 2. Ví dụ Java 17+ chạy độc lập

Ví dụ gồm `CsvImporter` và `TsvImporter`. Cả hai sử dụng chung thuật toán `importFile()`, nhưng phân tích dữ liệu theo dấu phẩy hoặc ký tự tab và thực hiện bước lưu riêng. Chương trình tự tạo các tệp ví dụ tạm thời rồi xóa chúng sau khi chạy.

Lưu đoạn mã dưới đây vào `TemplateMethodImportDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TemplateMethodImportDemo {
    private record ImportRecord(String name, String email) {}

    private abstract static class DataImporter {
        public final void importFile(Path file) throws IOException {
            Objects.requireNonNull(file, "file");
            validateSource(file);
            System.out.println("Start import: " + file.getFileName());
            List<String> lines = readSource(file);
            List<ImportRecord> records = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                ImportRecord record = parseLine(line, i + 1);
                validateRecord(record, i + 1);
                records.add(record);
            }
            persist(List.copyOf(records));
            afterImport(records.size());
        }

        protected void validateSource(Path file) {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Input file not found: " + file);
            }
        }
        protected List<String> readSource(Path file) throws IOException {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        protected abstract ImportRecord parseLine(String line, int lineNumber);
        protected void validateRecord(ImportRecord record, int lineNumber) {
            if (record.name().isBlank() || !record.email().contains("@")) {
                throw new IllegalArgumentException("Invalid record at line " + lineNumber);
            }
        }
        protected abstract void persist(List<ImportRecord> records);
        protected void afterImport(int count) {
            System.out.println("Import complete: " + count + " records");
        }
    }

    private static final class CsvImporter extends DataImporter {
        @Override protected ImportRecord parseLine(String line, int lineNumber) {
            String[] fields = line.split(",", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Expected 2 CSV fields at line " + lineNumber);
            }
            return new ImportRecord(fields[0].trim(), fields[1].trim());
        }
        @Override protected void persist(List<ImportRecord> records) {
            System.out.println("Persisting CSV records:");
            for (ImportRecord record : records) {
                System.out.println("  " + record.name() + " -> " + record.email());
            }
        }
    }

    private static final class TsvImporter extends DataImporter {
        @Override protected ImportRecord parseLine(String line, int lineNumber) {
            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Expected 2 TSV fields at line " + lineNumber);
            }
            return new ImportRecord(fields[0].trim(), fields[1].trim());
        }
        @Override protected void persist(List<ImportRecord> records) {
            System.out.println("Persisting TSV records:");
            for (ImportRecord record : records) {
                System.out.println("  " + record.name() + " -> " + record.email());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("template-method-import-");
        Path csv = directory.resolve("people.csv");
        Path tsv = directory.resolve("people.tsv");
        try {
            Files.write(csv, List.of(
                "Alice,alice@example.com",
                "Bob,bob@example.com"
            ), StandardCharsets.UTF_8);
            Files.write(tsv, List.of(
                "Carol\tcarol@example.com"
            ), StandardCharsets.UTF_8);
            new CsvImporter().importFile(csv);
            new TsvImporter().importFile(tsv);
        } finally {
            Files.deleteIfExists(csv);
            Files.deleteIfExists(tsv);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```text
javac TemplateMethodImportDemo.java
java TemplateMethodImportDemo
```

Đầu ra chính xác:

```text
Start import: people.csv
Persisting CSV records:
  Alice -> alice@example.com
  Bob -> bob@example.com
Import complete: 2 records
Start import: people.tsv
Persisting TSV records:
  Carol -> carol@example.com
Import complete: 1 records
```

## 3. Vì sao đây là Template Method?

Điểm quyết định là `importFile()` điều phối toàn bộ thuật toán và không cho lớp con ghi đè phương thức này. Cả hai importer đều phải đi qua kiểm tra nguồn, đọc tệp, phân tích, xác thực, lưu và hoàn tất theo cùng trình tự. Lớp con chỉ thay đổi những điểm mở rộng được thiết kế sẵn.

`parseLine()` là điểm mở rộng cần thiết vì mỗi định dạng có cú pháp riêng. `persist()` cho phép mỗi importer thay cách lưu dữ liệu; trong ví dụ, chúng in bản ghi ra màn hình để có thể quan sát. Trong ứng dụng thật, phần này có thể ghi vào cơ sở dữ liệu hoặc gửi dữ liệu tới một dịch vụ.

`validateRecord()` có logic mặc định dùng chung, nên cả CSV lẫn TSV đều phải có tên không rỗng và email chứa ký tự `@`. `afterImport()` cũng có mặc định dùng chung. Lớp con có thể mở rộng hook nếu cần thêm bước như ghi số liệu, nhưng nên gọi lại hành vi cha nếu hành vi đó vẫn cần thiết.

## 4. Cách áp dụng vào hệ thống thực tế

- Đặt trong lớp cha các bước thực sự giống nhau và thứ tự mà mọi quy trình phải tuân thủ, ví dụ xác minh nguồn, đọc, chuẩn hóa, xác thực và lưu.
- Tạo hook cho phần khác nhau giữa các định dạng, chẳng hạn phân tích dòng CSV, TSV hoặc một định dạng nội bộ.
- Truyền cấu hình qua constructor hoặc tham số khi khác biệt chỉ là dữ liệu cấu hình, như tên bảng hoặc quy tắc ánh xạ. Không cần tạo một lớp con cho mọi giá trị cấu hình.
- Xác định rõ xử lý lỗi: dừng toàn bộ khi một dòng không hợp lệ, bỏ qua dòng lỗi, hay gom lỗi để báo cáo cuối cùng. Đó là quyết định nghiệp vụ, không nên để hành vi phát sinh ngẫu nhiên.
- Nếu bước lưu cần giao dịch, hãy xác định ranh giới giao dịch ở mức quy trình để tránh nhập một phần dữ liệu ngoài ý muốn.

Ví dụ này dùng `split()` đơn giản nên chỉ minh họa nguyên tắc thiết kế. Nó chưa xử lý dấu phẩy nằm trong trường CSV được đặt trong dấu ngoặc kép, tiêu đề cột, mã hóa lỗi hay quy tắc email đầy đủ. Với CSV sản xuất, nên dùng thư viện phân tích CSV chuyên dụng và bổ sung chiến lược giao dịch, ghi log cùng báo cáo lỗi phù hợp.

## 5. Template Method so với cách viết lặp

Không dùng mẫu này, mỗi importer có thể tự viết một phương thức nhập riêng. Theo thời gian, một lớp có thể quên xác thực, lớp khác có thể lưu trước khi kiểm tra xong, và cách báo lỗi trở nên không nhất quán. Template Method tập trung phần trình tự vào một nơi để các triển khai chia sẻ quy tắc chung.

Tuy nhiên, không nên biến lớp cha thành một framework có quá nhiều hook. Nếu các quy trình khác nhau gần như hoàn toàn, hoặc cần kết hợp các bước theo nhiều cấu hình, composition với các đối tượng chiến lược có thể dễ thay đổi hơn. Dùng Template Method khi trình tự tổng thể tương đối ổn định và chỉ một số bước cần chuyên biệt hóa.

## 6. Lỗi thường gặp

1. **Cho phép lớp con ghi đè thuật toán chính.** Nếu thứ tự bước là bất biến của thiết kế, khai báo Template Method là `final` để lớp con không vô tình bỏ qua xác thực hoặc cleanup.
2. **Đặt mọi thứ vào lớp cha.** Chỉ giữ logic dùng chung trong lớp cha; hành vi riêng theo định dạng nên nằm ở hook hoặc lớp con thích hợp.
3. **Dùng hook nhưng không xác định hợp đồng.** Ghi rõ thời điểm hook chạy, dữ liệu nhận vào, lỗi có thể ném và liệu hook có được bỏ qua hay không.
4. **Xử lý lỗi không nhất quán.** Cần quyết định rõ khi một bản ghi lỗi thì dừng, tiếp tục, hay hoàn tác. Ví dụ này ném ngoại lệ và dừng quy trình khi dòng sai.
5. **Nhầm Template Method với Strategy.** Template Method dựa vào kế thừa và một khung thuật toán ở lớp cha; Strategy đóng gói một thuật toán hoặc chính sách có thể thay thế thông qua composition.

## 7. Checklist khi thiết kế

- Thuật toán có trình tự chung ổn định giữa các importer không?
- Phần nào bắt buộc phải giống nhau, và phần nào thực sự cần tùy biến?
- Template Method có nên là `final` để bảo vệ thứ tự không?
- Hook có tên rõ ràng, hợp đồng rõ ràng và phạm vi trách nhiệm nhỏ không?
- Quy tắc xác thực, lỗi, giao dịch và lưu dữ liệu đã được xác định chưa?
- Ví dụ đã kiểm thử ít nhất một định dạng hợp lệ và trường hợp đầu vào không hợp lệ chưa?

Khi các câu trả lời cho thấy quy trình có bộ khung chung nhưng khác nhau ở một số bước, Template Method giúp gom luồng điều khiển vào một nơi và giữ các phần chuyên biệt ở đúng điểm mở rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-004">Giải quyết bài toán hình thoi với nhiều interface</a>

<details>
<summary>Xem chi tiết</summary>

# Giải quyết bài toán hình thoi (diamond problem) với nhiều interface

Trong Java, một lớp chỉ có thể `extends` một lớp cha, nhưng có thể `implements` nhiều interface. Điều này cho phép một đối tượng có nhiều vai trò và nhiều hợp đồng kiểu. Tuy nhiên, khi các interface tạo thành một đồ thị kế thừa hình thoi và mỗi nhánh cung cấp một `default method` cùng chữ ký nhưng khác cách thực hiện, lớp ở đáy hình thoi phải quyết định hành vi nào là đúng.

Đó là **diamond problem** (bài toán hình thoi). Java cho phép đa kế thừa kiểu qua interface nhưng không tự chọn tùy tiện giữa hai default method xung đột. Trình biên dịch yêu cầu lớp giải quyết sự mơ hồ ngay tại thời điểm biên dịch.

## 1. Nhận diện cấu trúc hình thoi

Xét một thiết bị vừa có vai trò kết nối mạng, vừa có vai trò lưu trữ:

~~~text
                 Device
                /      \
       NetworkDevice   StorageDevice
                \      /
                SmartDevice
~~~

- `Device` là interface chung.
- `NetworkDevice` và `StorageDevice` đều kế thừa `Device`.
- `SmartDevice` implements cả hai nhánh để cung cấp hai vai trò cho cùng một đối tượng.

Nếu `Device` khai báo một default method tên `status()`, cả hai nhánh có thể kế thừa phương thức đó. Khi đó chúng đang cùng dùng một implementation gốc, nên không có hai cách thực hiện khác nhau để lựa chọn. Tình huống xung đột thực sự xuất hiện khi mỗi nhánh **ghi đè** `status()` với ý nghĩa riêng:

~~~java
interface NetworkDevice extends Device {
    @Override
    default String status() {
        return "network";
    }
}

interface StorageDevice extends Device {
    @Override
    default String status() {
        return "storage";
    }
}
~~~

Nếu `SmartDevice` implements cả `NetworkDevice` và `StorageDevice` mà không ghi đè `status()`, Java không biết cần kế thừa implementation nào. Kết quả là lỗi biên dịch, không phải một lựa chọn ngẫu nhiên khi chạy chương trình.

## 2. Quy tắc Java dùng để giải quyết xung đột

Khi nhiều interface cung cấp phương thức có cùng chữ ký, xét theo thứ tự sau:

1. **Phương thức instance cụ thể của lớp có ưu tiên hơn default method của interface.** Nếu lớp cha là một class có phương thức cụ thể phù hợp, phương thức đó được ưu tiên theo quy tắc phân giải của Java.
2. **Interface cụ thể hơn có ưu tiên hơn interface cha.** Nếu một interface con ghi đè default method của interface cha, lớp triển khai interface con sẽ nhận phiên bản cụ thể hơn, miễn là không có xung đột khác giữa các nhánh không liên quan.
3. **Hai default method xung đột từ các interface không có quan hệ kế thừa cụ thể phải được lớp triển khai ghi đè.** Lớp có thể tự định nghĩa hành vi mới hoặc chủ động gọi các implementation mong muốn bằng cú pháp `InterfaceName.super.method()`.
4. **Các phương thức abstract trùng chữ ký thường có thể được triển khai bằng một phương thức duy nhất.** Nếu các khai báo không tương thích, chẳng hạn kiểu trả về không thể thỏa mãn đồng thời, chương trình sẽ không biên dịch.
5. **Không dựa vào thứ tự liệt kê interface trong `implements`.** Đổi thứ tự `implements A, B` thành `implements B, A` không phải cách giải quyết xung đột.

Cần phân biệt hai trường hợp: cùng kế thừa một default method từ một interface tổ tiên không nhất thiết gây mơ hồ; hai nhánh ghi đè phương thức đó bằng các implementation khác nhau mới tạo xung đột cần giải quyết.

## 3. Cách viết lớp giải quyết xung đột

Có hai lựa chọn phổ biến:

- **Tự định nghĩa lại hành vi** khi lớp kết hợp có quy tắc riêng. Đây thường là lựa chọn rõ nhất nếu hành vi của đối tượng không đơn giản là một trong hai nhánh.
- **Gọi rõ implementation của từng interface** bằng `NetworkDevice.super.status()` hoặc `StorageDevice.super.status()` khi muốn tái sử dụng logic đã có.

Cú pháp `InterfaceName.super.method()` chỉ dùng để gọi default method từ interface trực tiếp mà lớp triển khai. Trong ví dụ, `NetworkDevice` và `StorageDevice` là các interface trực tiếp của `SmartDevice`, nên các lời gọi đó hợp lệ. Không thể dùng `Device.super.status()` ở đây để bỏ qua các nhánh trung gian, vì `Device` không phải interface trực tiếp được `SmartDevice` triển khai.

Không bắt buộc phải kết hợp cả hai implementation. Lớp có thể trả về một trạng thái tổng hợp, chọn một nhánh theo quy tắc nghiệp vụ, hoặc cung cấp một định nghĩa mới — miễn là quyết định đó rõ ràng và phù hợp với hợp đồng công khai.

## 4. Ví dụ Java hoàn chỉnh, có thể chạy

Lưu toàn bộ mã sau vào file `InterfaceDiamondDemo.java`. Ví dụ dùng ba interface và một lớp triển khai cả hai nhánh. `SmartDevice` giải quyết xung đột `status()` bằng cách gọi tường minh default method của từng interface; đồng thời nó cung cấp các phương thức abstract về mã mạng và dung lượng lưu trữ.

~~~java
import java.util.Objects;

interface Device {
    default String status() {
        return "generic";
    }
}

interface NetworkDevice extends Device {
    @Override
    default String status() {
        return "network";
    }

    String networkId();
}

interface StorageDevice extends Device {
    @Override
    default String status() {
        return "storage";
    }

    long capacityGb();
}

final class SmartDevice implements NetworkDevice, StorageDevice {
    private final String networkId;
    private final long capacityGb;

    SmartDevice(String networkId, long capacityGb) {
        this.networkId = Objects.requireNonNull(networkId, "networkId");
        if (capacityGb <= 0) {
            throw new IllegalArgumentException("capacityGb must be positive");
        }
        this.capacityGb = capacityGb;
    }

    @Override
    public String status() {
        return NetworkDevice.super.status() + "+" + StorageDevice.super.status();
    }

    @Override
    public String networkId() {
        return networkId;
    }

    @Override
    public long capacityGb() {
        return capacityGb;
    }
}

public class InterfaceDiamondDemo {
    public static void main(String[] args) {
        SmartDevice smart = new SmartDevice("net-7", 512);

        Device device = smart;
        NetworkDevice network = smart;
        StorageDevice storage = smart;

        System.out.println("status=" + device.status());
        System.out.println("networkId=" + network.networkId());
        System.out.println("capacityGb=" + storage.capacityGb());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac InterfaceDiamondDemo.java
java InterfaceDiamondDemo
~~~

Output chính xác:

~~~text
status=network+storage
networkId=net-7
capacityGb=512
~~~

### Đọc kết quả

- Biến `device` có kiểu tham chiếu `Device`, nhưng đối tượng thực tế là `SmartDevice`. Lời gọi `device.status()` dispatch tới phương thức ghi đè trong `SmartDevice`, nên kết quả là `network+storage`.
- Biến `network` có kiểu tham chiếu `NetworkDevice`; nó có thể gọi `networkId()`, vì phương thức đó thuộc hợp đồng của interface này.
- Biến `storage` có kiểu tham chiếu `StorageDevice`; nó có thể gọi `capacityGb()`.
- Cùng một đối tượng thực hiện nhiều interface mà không cần đa kế thừa class. Mỗi interface thể hiện một vai trò, còn lớp cụ thể kết hợp các vai trò đó.

Constructor cũng kiểm tra dữ liệu đầu vào: mã mạng không được null và dung lượng phải dương. Những kiểm tra này là quy tắc của đối tượng, không phải cơ chế giải quyết diamond problem, nhưng chúng minh họa nơi lớp cụ thể chịu trách nhiệm bảo vệ invariant.

## 5. Thấy lỗi trước khi sửa

Nếu bỏ phần ghi đè `status()` khỏi `SmartDevice`, đồng thời giữ nguyên việc triển khai `networkId()` và `capacityGb()`, cấu trúc sau sẽ không biên dịch:

~~~java
final class AmbiguousDevice implements NetworkDevice, StorageDevice {
    @Override
    public String networkId() {
        return "net-7";
    }

    @Override
    public long capacityGb() {
        return 512;
    }

    // Thiếu override status():
    // hai default method không liên quan cùng cung cấp status().
}
~~~

Trình biên dịch báo rằng lớp kế thừa các default method không liên quan có cùng chữ ký `status()`. Cách sửa không phải ép kiểu đối tượng ở nơi gọi, đổi thứ tự interface hoặc chọn bằng `if/else` bên ngoài. Cần định nghĩa rõ `status()` ngay trong lớp triển khai (hoặc thiết kế lại các interface nếu hai hợp đồng vốn không nên xung đột).

## 6. Các trường hợp liên quan cần phân biệt

### Interface con ghi đè interface cha

Nếu `FastNetworkDevice` extends `NetworkDevice` và ghi đè `status()`, implementation của interface con cụ thể hơn implementation kế thừa từ `NetworkDevice`. Khi một lớp chỉ nhận các đường kế thừa không xung đột, Java ưu tiên phương thức từ interface cụ thể hơn.

### Lớp cha đã có phương thức cụ thể

Nếu lớp triển khai extends một class có phương thức instance cụ thể phù hợp với chữ ký, phương thức của class được ưu tiên hơn default method. Điều này giúp default method của interface không tự ý thay thế hành vi đã có của lớp cha.

### Hai interface chỉ khai báo cùng phương thức abstract

Nếu hai interface cùng khai báo một phương thức abstract tương thích, lớp thường chỉ cần triển khai một lần để đáp ứng cả hai hợp đồng. Không có hai default implementation cạnh tranh để lựa chọn. Tuy vậy, kiểu trả về, visibility và các điều kiện của chữ ký vẫn phải hợp lệ theo Java.

### Phương thức static của interface

Phương thức static thuộc interface khai báo nó và được gọi bằng tên interface, ví dụ `Device.someUtility()` nếu phương thức đó được khai báo trong `Device`. Không xử lý xung đột static method như xung đột default method; static method không được kế thừa và override theo cơ chế đa hình instance.

## 7. Lỗi thường gặp và cách tránh

- **Cho rằng Java cấm nhiều interface:** Java cho phép một lớp implements nhiều interface. Giới hạn một lớp cha áp dụng cho kế thừa class, không phải số interface.
- **Cho rằng mọi hình thoi đều lỗi:** nếu các nhánh cùng kế thừa một implementation gốc mà không ghi đè khác nhau, không nhất thiết có xung đột.
- **Quên override khi hai default method khác nhau:** lỗi xuất hiện ở thời điểm biên dịch. Giải quyết tại lớp kết hợp để hành vi trở nên minh bạch.
- **Gọi `Device.super.status()` từ lớp không implements trực tiếp `Device`:** hãy gọi default method qua interface trực tiếp phù hợp, hoặc tự viết hành vi kết hợp.
- **Dùng nhiều interface chỉ để tạo hình thoi:** cấu trúc kế thừa không tự tạo ra giá trị. Mỗi interface nên đại diện cho một hợp đồng hoặc vai trò có ý nghĩa, chẳng hạn khả năng kết nối và khả năng lưu trữ.
- **Đặt cùng tên phương thức cho hai ý nghĩa khác nhau:** nếu hai interface dùng `status()` với ngữ nghĩa không tương thích, cân nhắc đổi tên, tách trách nhiệm hoặc dùng adapter thay vì bắt mọi lớp kết hợp phải giải quyết cùng một xung đột.
- **Dùng default method cho trạng thái đối tượng:** interface không mang trạng thái instance như class. Dữ liệu riêng của đối tượng nên được giữ trong lớp triển khai hoặc collaborator phù hợp.

## 8. Nguyên tắc thiết kế khi có nhiều interface

Mỗi interface nên mô tả một hợp đồng rõ ràng và có mức độ kết dính cao. Một lớp có thể triển khai nhiều interface để kết hợp các khả năng độc lập, nhưng không nên chia nhỏ một cách máy móc chỉ để đạt số lượng interface lớn.

Khi thiết kế API có default method, hãy chọn tên và ngữ nghĩa đủ đặc thù, cân nhắc ảnh hưởng khi các interface được kết hợp, và ghi đè tường minh nếu các default method có thể xung đột. Với lớp triển khai, hãy quyết định hành vi tổng hợp ở một nơi thay vì để client tự lựa chọn hoặc ép kiểu theo từng nhánh.

**Tóm lại:** Java hỗ trợ đa kế thừa kiểu thông qua interface nhưng buộc phải làm rõ hành vi khi nhiều default method không tương thích cùng xuất hiện. Xác định nhánh nào sở hữu hợp đồng, hiểu quy tắc ưu tiên của Java, rồi ghi đè trong lớp kết hợp hoặc gọi rõ implementation bằng `InterfaceName.super.method()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-005">Tách interface lớn thành các vai trò nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Tách interface lớn thành các vai trò nhỏ

Tách một interface lớn thành các interface nhỏ theo vai trò là cách áp dụng **Interface Segregation Principle (ISP)** — nguyên lý phân tách interface trong SOLID. Nguyên lý này khuyên rằng một client không nên bị buộc phụ thuộc vào những thao tác mà nó không sử dụng. Thay vì một interface bao trùm mọi khả năng của hệ thống, hãy tạo các hợp đồng nhỏ, có ý nghĩa rõ ràng và gắn với từng nhóm nhu cầu.

Ví dụ, một thiết bị đa chức năng có thể in, quét và gửi fax. Nhưng một máy in cơ bản chỉ có khả năng in. Nếu cả hai buộc phải triển khai cùng một interface `Machine` với đủ ba phương thức, lớp máy in cơ bản phải cung cấp những phương thức không hỗ trợ hoặc ném ngoại lệ giả tạo. Chia hợp đồng thành `Printable`, `Scannable` và `Faxable` giúp mỗi lớp chỉ cam kết những khả năng nó thực sự có.

## 1. Nguyên lý ISP nói gì?

Robert C. Martin mô tả ISP theo hướng client nên phụ thuộc vào các interface nhỏ phù hợp với nhu cầu của mình. Một interface lớn có thể gom nhiều phương thức vì chúng cùng thuộc một đối tượng trong thế giới thực, nhưng điều đó chưa chắc có nghĩa chúng thuộc cùng một hợp đồng phần mềm.

ISP không yêu cầu mọi interface chỉ có một phương thức. Mục tiêu là gom các phương thức có liên quan và thường được sử dụng cùng nhau bởi cùng một nhóm client. Một interface có nhiều phương thức vẫn hợp lý nếu chúng tạo thành một vai trò nhất quán.

Có thể diễn đạt nguyên tắc bằng ba câu hỏi:

- Client này thực sự cần gọi những thao tác nào?
- Khi thêm một khả năng mới vào interface, những client nào bị buộc phải thay đổi dù không sử dụng khả năng đó?
- Mỗi implementation có thể thực hiện đúng tất cả phương thức mà nó cam kết không?

Nếu một nhóm client chỉ dùng một phần nhỏ của interface và những phần còn lại thay đổi độc lập, interface có thể đang đảm nhiệm quá nhiều vai trò.

## 2. Dấu hiệu của một interface quá lớn

Hãy hình dung hợp đồng `Machine` có các phương thức `print()`, `scan()` và `fax()`. Những dấu hiệu sau cho thấy nên xem xét phân tách:

- Một lớp chỉ hỗ trợ một phần phương thức và phải ném `UnsupportedOperationException` hoặc trả về giá trị giả cho các phương thức còn lại.
- Client chỉ dùng một số phương thức nhưng vẫn phụ thuộc vào toàn bộ interface. Việc thêm phương thức mới có thể buộc client hoặc implementation không liên quan phải sửa mã.
- Những nhóm phương thức thay đổi vì các lý do khác nhau. Chẳng hạn, khả năng quét tài liệu thay đổi theo loại scanner, trong khi khả năng gửi fax phụ thuộc vào cấu hình liên lạc.
- Các implementation cần những phụ thuộc khác nhau để đáp ứng các phương thức. Một máy in đơn giản không nên phải cấu hình dịch vụ fax.
- Kiểm thử trở nên khó hiểu vì một implementation giả phải khai báo nhiều hành vi không liên quan đến kịch bản đang kiểm tra.

Một interface nhiều phương thức tự nó chưa phải là vấn đề. Nếu mọi implementation cần toàn bộ phương thức và các phương thức cùng thuộc một hợp đồng gắn kết, tách interface có thể chỉ tạo thêm sự phức tạp.

## 3. Từ một interface lớn đến các interface theo vai trò

Trước khi phân tách, thiết kế thường có dạng:

```text
Machine
  ├── print(document)
  ├── scan()
  └── fax(document, destination)

BasicPrinter implements Machine
  └── chỉ có khả năng in

OfficeMultifunctionDevice implements Machine
  ├── in
  ├── quét
  └── fax
```

Với thiết kế này, `BasicPrinter` phải khai báo hành vi quét và fax dù phần cứng không hỗ trợ. Nếu triển khai bằng ngoại lệ, mọi client gọi nhầm phương thức chỉ phát hiện vấn đề lúc chạy. Nếu dùng phương thức rỗng, lỗi có thể bị che giấu.

Sau khi tách theo vai trò:

```text
Printable     Scannable     Faxable
     ^             ^            ^
     |             |            |
BasicPrinter   Scanner      FaxDevice

OfficeMultifunctionDevice implements
Printable, Scannable, Faxable
```

Một lớp có thể triển khai nhiều interface nhỏ nếu nó thật sự cung cấp nhiều khả năng. Một client chỉ cần in có thể phụ thuộc vào `Printable`; nó không cần biết đối tượng có thể quét hoặc gửi fax hay không.

## 4. Ví dụ Java hoàn chỉnh

Chương trình sau minh họa ba interface theo vai trò, một máy in chỉ in, một thiết bị đa chức năng và các dịch vụ phụ thuộc vào hợp đồng tối thiểu cần thiết.

Lưu toàn bộ mã vào `Main.java`:

```java
interface Printable {
    void print(String document);
}

interface Scannable {
    String scan();
}

interface Faxable {
    void fax(String document, String destination);
}

final class BasicPrinter implements Printable {
    @Override
    public void print(String document) {
        System.out.println("print:" + document);
    }
}

final class OfficeMultifunctionDevice
        implements Printable, Scannable, Faxable {
    @Override
    public void print(String document) {
        System.out.println("print:" + document);
    }

    @Override
    public String scan() {
        return "scanned-page";
    }

    @Override
    public void fax(String document, String destination) {
        System.out.println("fax:" + document + "->" + destination);
    }
}

final class PrintService {
    private final Printable printer;

    PrintService(Printable printer) {
        this.printer = printer;
    }

    void printInvoice(String invoice) {
        printer.print(invoice);
    }
}

final class ScanService {
    private final Scannable scanner;

    ScanService(Scannable scanner) {
        this.scanner = scanner;
    }

    void archive() {
        System.out.println("scan:" + scanner.scan());
    }
}

public class Main {
    public static void main(String[] args) {
        Printable basic = new BasicPrinter();
        PrintService printService = new PrintService(basic);
        printService.printInvoice("invoice-42");

        OfficeMultifunctionDevice office =
                new OfficeMultifunctionDevice();
        new ScanService(office).archive();
        office.fax("contract-7", "555-0100");
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```text
javac Main.java
java Main
```

Kết quả stdout chính xác:

```text
print:invoice-42
scan:scanned-page
fax:contract-7->555-0100
```

### Vì sao thiết kế này đáp ứng ISP?

- `BasicPrinter` chỉ triển khai `Printable`. Nó không cần khai báo phương thức quét hoặc fax.
- `OfficeMultifunctionDevice` triển khai cả ba interface vì nó thật sự cung cấp đủ ba khả năng.
- `PrintService` nhận một đối tượng kiểu `Printable`. Dịch vụ này không cần phụ thuộc vào scanner hay fax.
- `ScanService` chỉ yêu cầu `Scannable`. Có thể truyền một scanner độc lập hoặc thiết bị đa chức năng vào dịch vụ mà không đổi mã của dịch vụ.
- Các service nhận dependency qua constructor. Đây là constructor injection đơn giản: dependency được thể hiện ngay trong hợp đồng mà client cần.

Điểm quan trọng là kiểu tham số thể hiện đúng nhu cầu của client. Nếu `PrintService` nhận `OfficeMultifunctionDevice` trực tiếp, service sẽ phụ thuộc vào một implementation cụ thể và mất đi lợi ích của interface nhỏ.

## 5. Quy trình phân tách một interface trong dự án thực tế

Không nên tách interface chỉ bằng cách đếm số phương thức. Hãy đi từ các client và hành vi thực tế:

1. **Liệt kê client.** Xác định lớp hoặc module nào sử dụng interface, và ghi lại các phương thức mỗi client thực sự gọi.
2. **Nhóm phương thức theo vai trò.** Tìm các phương thức cùng phục vụ một nhu cầu, cùng thay đổi vì một lý do hoặc thường được sử dụng cùng nhau.
3. **Xác định hợp đồng nhỏ.** Tạo interface cho mỗi vai trò có ý nghĩa, với tên thể hiện khả năng hoặc trách nhiệm như `Readable`, `Writable`, `Searchable` hay `Exportable`.
4. **Cập nhật kiểu phụ thuộc của client.** Cho mỗi client nhận đúng interface nhỏ nhất đủ để thực hiện công việc. Ưu tiên phụ thuộc qua constructor hoặc phương thức thay vì tự tạo implementation bên trong client.
5. **Cập nhật implementation.** Một lớp chỉ triển khai những vai trò nó thực sự hỗ trợ. Lớp cần nhiều vai trò có thể implements nhiều interface.
6. **Loại bỏ hành vi giả.** Khi các interface nhỏ đã được áp dụng, xem lại những phương thức rỗng hoặc ngoại lệ chỉ tồn tại vì interface cũ quá rộng.
7. **Kiểm thử theo vai trò và hợp đồng.** Kiểm tra client hoạt động với mọi implementation hợp lệ của interface mà nó yêu cầu, kể cả implementation giả trong unit test.
8. **Đánh giá độ phức tạp sau khi tách.** Nếu một interface mới chỉ được dùng ở một nơi và không diễn tả một ranh giới có ý nghĩa, cân nhắc gộp lại.

Khi refactor hệ thống đang chạy, có thể giữ interface cũ làm lớp tương thích tạm thời và cho implementation hiện có triển khai các interface nhỏ mới. Sau khi client được chuyển sang hợp đồng mới, loại bỏ interface cũ trong một thay đổi có kiểm soát nếu không còn người dùng nào phụ thuộc vào nó. Việc giữ tương thích phụ thuộc vào yêu cầu API công khai và quy tắc phiên bản của dự án.

## 6. Những nguyên tắc thiết kế cần nhớ

**Interface nhỏ theo client, không nhất thiết nhỏ theo đối tượng.** Một thiết bị vật lý có thể làm nhiều việc, nhưng client đặt hàng in chỉ cần khả năng in. Hãy thiết kế hợp đồng quanh nhu cầu của client, đồng thời bảo đảm tên và hành vi của interface vẫn có ý nghĩa trong miền bài toán.

**Tách theo khả năng và trách nhiệm, không phải tách máy móc từng phương thức.** Tạo một interface cho mỗi phương thức đôi khi hữu ích, nhưng cũng có thể làm hệ thống khó hiểu nếu những thao tác luôn đi cùng nhau. ISP yêu cầu hợp đồng vừa đủ, không phải số lượng interface tối đa.

**Interface không phải nơi để khai báo mọi khả năng có thể tưởng tượng.** Chỉ thêm phương thức khi nó thuộc trách nhiệm của hợp đồng. Nếu một khả năng tùy chọn có thể được mô hình hóa thành một vai trò riêng, client không cần khả năng đó sẽ không phải phụ thuộc vào nó.

**Implementation có thể kết hợp nhiều vai trò.** Java cho phép một lớp implements nhiều interface. Việc tách interface không buộc phải tách một đối tượng thành nhiều lớp nếu những khả năng thực sự thuộc cùng một đối tượng và cùng vòng đời.

**ISP bổ trợ cho Dependency Inversion Principle (DIP).** DIP hướng tới phụ thuộc vào abstraction thay vì implementation cụ thể; ISP giúp abstraction đó không quá rộng đối với client. Hai nguyên lý bổ sung nhau: một client phụ thuộc vào một interface trừu tượng nhưng chứa quá nhiều phương thức vẫn có thể bị ảnh hưởng bởi thay đổi không liên quan.

## 7. Khi nào nên giữ interface lớn?

Giữ nguyên interface có thể là lựa chọn tốt nếu:

- Các phương thức tạo thành một hợp đồng gắn kết và hầu hết implementation hợp lệ đều cần chúng.
- Các client thường sử dụng gần như toàn bộ phương thức cùng nhau.
- Tách interface làm tăng số kiểu, tên và lớp chuyển tiếp mà không giảm sự phụ thuộc thực tế.
- Interface là API công khai đã ổn định và chi phí phá vỡ tương thích lớn hơn lợi ích của việc tách ngay. Có thể lên kế hoạch cho API nhỏ hơn ở phiên bản tiếp theo hoặc thêm các abstraction mới mà vẫn giữ đường tương thích.

Đừng nhầm việc một lớp có nhiều phương thức với vi phạm ISP. Cần xem mối liên hệ giữa client, hợp đồng và thay đổi trong hệ thống.

## 8. Các lỗi thường gặp

- **Tách theo số lượng thay vì trách nhiệm:** interface có một phương thức chưa chắc tốt hơn interface có ba phương thức liên quan.
- **Tạo interface chỉ để tránh một dependency trên giấy:** nếu client vẫn gọi nhiều abstraction rộng khác, vấn đề phụ thuộc có thể chưa được giải quyết.
- **Giữ client phụ thuộc vào implementation cụ thể:** phân tách interface mà các service vẫn khai báo tham số bằng lớp đa chức năng sẽ làm giảm lợi ích.
- **Để phương thức không được hỗ trợ trả về `null` hoặc không làm gì:** cách này che giấu vi phạm hợp đồng và tạo lỗi khó tìm. Nên xác định đúng khả năng mà implementation cam kết.
- **Chia quá vụn:** quá nhiều interface với ranh giới mơ hồ làm người đọc khó biết phải dùng kiểu nào. Đặt tên theo vai trò và giữ các phương thức liên quan cùng nhau.
- **Thay đổi API công khai mà không kiểm tra tương thích:** client bên ngoài có thể đang biên dịch dựa trên interface cũ. Hãy cân nhắc chiến lược chuyển đổi và kiểm thử tương thích.

## 9. Checklist rà soát

Trước khi hoàn tất việc phân tách, hãy kiểm tra:

- [ ] Mỗi interface có tên mô tả rõ một vai trò hoặc nhóm hành vi liên quan.
- [ ] Mỗi client chỉ phụ thuộc vào những thao tác nó cần.
- [ ] Mỗi implementation có thể đáp ứng đầy đủ hợp đồng mà nó implements.
- [ ] Không còn phương thức giả, rỗng hoặc ném ngoại lệ chỉ vì interface quá rộng.
- [ ] Client có thể được kiểm thử với implementation nhỏ hoặc test double phù hợp.
- [ ] Các lớp cần nhiều khả năng vẫn có thể implements nhiều interface.
- [ ] Việc thay đổi tương thích với các client và API hiện hữu đã được xem xét.

Tách interface lớn thành các vai trò nhỏ giúp giới hạn ảnh hưởng của thay đổi, làm rõ trách nhiệm và giúp implementation dễ thay thế hơn. Bắt đầu từ những client đang bị buộc phụ thuộc vào phương thức chúng không sử dụng; sau đó tạo các hợp đồng vừa đủ, chuyển client sang các hợp đồng đó và xác minh hành vi bằng kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-006">Thay cây kế thừa khó mở rộng bằng composition</a>

<details>
<summary>Xem chi tiết</summary>

# Thay cây kế thừa khó mở rộng bằng composition

Khi một hệ thống có nhiều cách gửi thông báo, nhiều định dạng nội dung và nhiều chính sách xử lý, dùng một subclass cho mọi tổ hợp sẽ nhanh chóng tạo ra một cây kế thừa khó duy trì. **Composition** giải quyết vấn đề bằng cách để một đối tượng giữ và phối hợp các thành phần có trách nhiệm riêng, thay vì mã hóa mọi biến thể vào quan hệ `extends`.

Bài thực hành này đi từ thiết kế dễ phình to sang thiết kế kết hợp các hành vi độc lập, sau đó kiểm tra bằng một chương trình Java 17+ chạy được.

## 1. Nhận diện cây kế thừa đang khó mở rộng

Giả sử ứng dụng gửi thông báo và có các chiều thay đổi sau:

- Kênh gửi: email, SMS, push notification.
- Cách trình bày: văn bản thường, có tiền tố ưu tiên, hoặc mẫu theo ngôn ngữ.
- Chính sách gửi: gửi một lần, thử lại khi thất bại, hoặc ghi log chi tiết.

Nếu mỗi subclass đại diện cho một tổ hợp, ta có thể phải tạo `EmailNotifier`, `UrgentEmailNotifier`, `SmsNotifier`, `UrgentSmsNotifier`, `RetryingEmailNotifier`, `UrgentRetryingSmsNotifier` và tiếp tục thêm lớp khi có biến thể mới. Với nhiều chiều độc lập, số tổ hợp có thể tăng theo tích số lựa chọn của từng chiều. Ví dụ 3 kênh × 3 cách trình bày × 2 chính sách đã tạo ra 18 tổ hợp tiềm năng.

Vấn đề không phải là “có nhiều subclass thì luôn sai”. Kế thừa vẫn hữu ích khi lớp con thực sự là một dạng chuyên biệt của lớp cha và có thể tuân thủ hợp đồng của lớp cha. Vấn đề xuất hiện khi ta dùng kế thừa chỉ để ghép các tính năng độc lập với nhau.

Các dấu hiệu thường gặp:

- Thêm một kênh mới buộc phải tạo nhiều subclass cho từng mức ưu tiên hoặc chính sách.
- Cùng một đoạn code gửi hoặc định dạng bị sao chép trong nhiều subclass.
- Muốn đổi hành vi lúc chạy thì phải chọn một class khác hoặc xây lại cả cây đối tượng.
- Một subclass kế thừa hành vi không cần dùng, hoặc override nhiều phương thức để vô hiệu hóa hành vi từ lớp cha.
- Thay đổi ở superclass gây ảnh hưởng ngoài dự kiến đến các lớp con.

## 2. Vì sao composition phù hợp?

Composition có nghĩa là một đối tượng sử dụng các đối tượng khác để thực hiện công việc. Đối tượng cấp cao chịu trách nhiệm điều phối; mỗi thành phần chịu trách nhiệm về một hành vi cụ thể.

Với ví dụ thông báo, ta có thể tách thành:

- `MessageSender`: gửi thông điệp qua một kênh.
- `MessageFormatter`: chuyển nội dung đầu vào thành nội dung sẽ gửi.
- `NotificationService`: phối hợp formatter và sender theo đúng thứ tự.

Nhờ đó, kênh gửi và cách trình bày trở thành hai chiều độc lập. Ta có thể kết hợp `EmailSender` với `PlainFormatter`, hoặc `SmsSender` với `PriorityFormatter` mà không cần viết một subclass cho từng cặp.

Composition thường đi cùng dependency injection: các thành phần được truyền vào qua constructor. Cách này giúp thay thế implementation dễ dàng, kiểm thử bằng fake/mock thuận tiện và thể hiện rõ những dependency mà một class cần.

## 3. Thiết kế Java bằng các interface nhỏ

Định nghĩa hợp đồng theo vai trò thay vì theo từng tổ hợp:

```java
interface MessageSender {
    void send(String message);
}

interface MessageFormatter {
    String format(String message);
}
```

`MessageSender` chỉ quan tâm đến việc gửi chuỗi. `MessageFormatter` chỉ quan tâm đến cách biến đổi chuỗi. Chúng không cần biết nhau và không phụ thuộc vào `NotificationService`.

Đối tượng điều phối giữ các dependency và ủy quyền công việc:

```java
final class NotificationService {
    private final MessageSender sender;
    private final MessageFormatter formatter;

    NotificationService(MessageSender sender, MessageFormatter formatter) {
        this.sender = java.util.Objects.requireNonNull(sender);
        this.formatter = java.util.Objects.requireNonNull(formatter);
    }

    void notify(String message) {
        String formatted = formatter.format(message);
        sender.send(formatted);
    }
}
```

Constructor bảo đảm các dependency không phải `null`, và các field `final` giúp những dependency này không bị thay thế vô tình sau khi tạo service. Phương thức `notify` không cần biết gửi qua email hay SMS, cũng không cần biết nội dung được định dạng theo cách nào.

## 4. Chương trình hoàn chỉnh có thể chạy

Lưu đoạn mã dưới đây thành `CompositionDemo.java`. Các sender trong ví dụ chỉ in ra console để minh họa kiến trúc; chúng không gửi email hay SMS thật.

```java
public class CompositionDemo {
    interface MessageSender {
        void send(String message);
    }

    interface MessageFormatter {
        String format(String message);
    }

    static final class EmailSender implements MessageSender {
        @Override
        public void send(String message) {
            System.out.println("EMAIL: " + message);
        }
    }

    static final class SmsSender implements MessageSender {
        @Override
        public void send(String message) {
            System.out.println("SMS: " + message);
        }
    }

    static final class PlainFormatter implements MessageFormatter {
        @Override
        public String format(String message) {
            return message;
        }
    }

    static final class PriorityFormatter implements MessageFormatter {
        @Override
        public String format(String message) {
            return "[PRIORITY] " + message;
        }
    }

    static final class NotificationService {
        private final MessageSender sender;
        private final MessageFormatter formatter;

        NotificationService(MessageSender sender, MessageFormatter formatter) {
            this.sender = java.util.Objects.requireNonNull(sender);
            this.formatter = java.util.Objects.requireNonNull(formatter);
        }

        void notify(String message) {
            sender.send(formatter.format(message));
        }
    }

    public static void main(String[] args) {
        NotificationService normalEmail = new NotificationService(
                new EmailSender(), new PlainFormatter());
        normalEmail.notify("Build completed");

        NotificationService prioritySms = new NotificationService(
                new SmsSender(), new PriorityFormatter());
        prioritySms.notify("Server is down");

        NotificationService priorityEmail = new NotificationService(
                new EmailSender(), new PriorityFormatter());
        priorityEmail.notify("Deployment started");
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CompositionDemo.java
java CompositionDemo
```

Stdout chính xác:

```text
EMAIL: Build completed
SMS: [PRIORITY] Server is down
EMAIL: [PRIORITY] Deployment started
```

Ba cấu hình sử dụng hai loại sender và hai loại formatter mà không cần các class như `PriorityEmailNotifier` hay `PrioritySmsNotifier`. `NotificationService` chỉ phối hợp các hành vi, còn mỗi implementation nhỏ tập trung vào một trách nhiệm.

## 5. Thêm biến thể mà không sửa service

Giả sử cần gửi thông báo qua push notification. Ta thêm một implementation mới:

```java
static final class PushSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("PUSH: " + message);
    }
}
```

Sau đó tạo service với `new PushSender()` và formatter mong muốn. Không cần thêm nhánh `if/else` vào `NotificationService`, và cũng không cần tạo subclass mới cho mọi kiểu trình bày.

Tương tự, muốn thêm định dạng có mã sự kiện, ta tạo một implementation `MessageFormatter` mới. Chỉ những cấu hình cần hành vi mới sẽ dùng formatter đó. Các thành phần hiện có tiếp tục hoạt động độc lập.

Trong ứng dụng thực tế, có thể cấu hình các dependency bằng constructor trực tiếp, factory hoặc dependency injection framework. Framework không phải điều kiện để áp dụng composition; việc truyền dependency qua constructor đã thể hiện nguyên tắc này.

## 6. So sánh kế thừa và composition

| Tiêu chí | Cây kế thừa cho từng tổ hợp | Composition |
| --- | --- | --- |
| Thêm kênh mới | Có thể phải tạo nhiều subclass mới. | Thêm một `MessageSender` implementation. |
| Thêm cách định dạng | Có thể nhân số subclass theo các biến thể hiện có. | Thêm một `MessageFormatter` implementation. |
| Kết hợp hành vi | Các tổ hợp thường được mã hóa trong tên và cấu trúc class. | Chọn dependency khi tạo đối tượng. |
| Kiểm thử | Có thể cần khởi tạo một cây class phức tạp. | Có thể truyền fake sender và formatter đơn giản. |
| Quan hệ mô hình | Phù hợp khi lớp con thật sự là một dạng của lớp cha. | Phù hợp khi đối tượng phối hợp các vai trò độc lập. |
| Chi phí | Cây class có thể phình to và tạo phụ thuộc chặt. | Có thêm interface/đối tượng và bước cấu hình. |

Composition không tự động tốt hơn trong mọi trường hợp. Nếu chỉ có một hành vi ổn định, không có nhiều biến thể và quan hệ “là một” thực sự rõ ràng, một class đơn giản hoặc kế thừa có thể dễ hiểu hơn. Tránh tạo interface và lớp bọc chỉ để chuyển tiếp một lời gọi mà không mang lại lợi ích thiết kế.

## 7. Lỗi thường gặp khi chuyển sang composition

### Tách thành quá nhiều abstraction

Không nên tạo interface cho mọi class theo thói quen. Hãy tách một trách nhiệm khi có lý do thực tế: hành vi thay đổi độc lập, có nhiều implementation, cần thay thế trong kiểm thử hoặc ranh giới đó làm thiết kế dễ hiểu hơn.

### Đưa điều phối vào sai nơi

`NotificationService` nên điều phối trình tự format rồi send. `EmailSender` không nên tự quyết định định dạng ưu tiên nếu định dạng đó là một chiều thay đổi riêng. Nếu mỗi sender chứa cùng một logic định dạng, ta lại tạo ra trùng lặp.

### Dùng kế thừa trong các thành phần nhỏ không cần thiết

Các implementation có thể là class cụ thể độc lập. Chỉ dùng `extends` khi thực sự cần tái sử dụng hoặc chuyên biệt hóa một loại có quan hệ phù hợp; không cần tạo một superclass chung chỉ vì vài class tình cờ có một dòng code giống nhau.

### Tạo dependency có thể thay đổi không kiểm soát

Nếu dependency không cần thay đổi sau khi tạo service, hãy dùng field `final` và truyền qua constructor. Nếu cần đổi hành vi trong vòng đời đối tượng, hãy thiết kế điểm thay đổi rõ ràng thay vì cho phép mọi field bị thay thế tùy ý.

### Quên các quy tắc nghiệp vụ khi ghép thành phần

Composition chỉ giúp ghép hành vi; nó không tự giải quyết thứ tự gửi, retry, timeout, xử lý lỗi hoặc tính nhất quán. Nếu thêm retry, hãy xác định thao tác nào được thử lại, exception nào được xem là tạm thời và nguy cơ gửi trùng. Những quy tắc đó cần hợp đồng và kiểm thử riêng.

## 8. Cách thực hành refactor an toàn

Khi gặp một cây kế thừa khó mở rộng trong dự án, hãy tiến hành theo từng bước:

1. Liệt kê các chiều thay đổi độc lập, chẳng hạn kênh gửi, định dạng và chính sách retry.
2. Tìm phần code trùng lặp hoặc subclass chỉ khác nhau ở một hành vi nhỏ.
3. Chọn một trách nhiệm có ranh giới rõ và định nghĩa interface nhỏ nếu cần nhiều implementation hoặc cần thay thế dễ dàng.
4. Di chuyển hành vi đó sang một thành phần riêng; để đối tượng chính giữ vai trò điều phối.
5. Truyền thành phần qua constructor và giữ hợp đồng hiện có của đối tượng công khai.
6. Thêm kiểm thử để chứng minh hành vi trước và sau refactor tương đương với các tình huống đang được hỗ trợ.
7. Thêm một biến thể mới để kiểm tra thiết kế: nếu chỉ cần thêm implementation và cấu hình lại dependency, ranh giới thường đã hợp lý.

Không cần chuyển cả cây class trong một lần. Refactor một nhánh có giá trị, giữ kiểm thử chạy ổn định, rồi tiếp tục khi xuất hiện nhu cầu cụ thể.

## 9. Bài tập tự kiểm tra

1. Nếu có 4 kênh gửi và 3 định dạng, một thiết kế subclass theo từng tổ hợp có thể cần bao nhiêu class tổ hợp? Vì sao con số đó có thể tăng thêm khi có retry?
2. Trong ví dụ, vì sao `NotificationService` không cần biết lớp sender cụ thể?
3. Hãy thêm `UppercaseFormatter` chuyển thông điệp thành chữ hoa. Bạn có phải sửa `NotificationService` không?
4. Hãy viết một `MessageSender` giả chỉ ghi lại thông điệp nhận được, sau đó kiểm thử rằng formatter chạy trước sender.
5. Khi nào composition trở thành phức tạp không cần thiết? Hãy nêu một trường hợp mà một class đơn giản sẽ rõ ràng hơn.

## Kết luận

Khi một cây kế thừa mã hóa nhiều chiều thay đổi độc lập, hãy cân nhắc chuyển từng hành vi thành thành phần có trách nhiệm rõ ràng và để đối tượng chính phối hợp chúng bằng composition. Thiết kế này giúp kết hợp linh hoạt, thêm biến thể với ít thay đổi hơn và kiểm thử từng hành vi riêng. Hãy giữ abstraction ở mức cần thiết, bảo toàn hợp đồng hiện tại và refactor từng bước có kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-007">Xây dựng kho dữ liệu generic với nhiều cách lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng kho dữ liệu generic với nhiều cách lưu trữ

Một **kho dữ liệu (repository)** cung cấp API để lưu, tìm, liệt kê và xóa đối tượng. Với **generic** trong Java, cùng một hợp đồng có thể dùng cho nhiều kiểu dữ liệu mà vẫn giữ kiểm tra kiểu lúc biên dịch. Kết hợp interface với nhiều implementation giúp ứng dụng chọn cách lưu trữ mà không phải viết lại logic nghiệp vụ.

Bài thực hành xây dựng hợp đồng `Repository<ID, T>` với hai backend:

- **In-memory:** dùng `Map` giữ dữ liệu trong bộ nhớ của tiến trình.
- **File-backed:** ghi bản đồ dữ liệu xuống tệp và nạp lại khi tạo repository mới.

Phần gọi sử dụng `Repository<String, Book>`, nên có thể chuyển giữa các backend mà không đổi kiểu API mà ứng dụng nhìn thấy.

## 1. Thiết kế hợp đồng generic

Một repository thường cần hai tham số kiểu:

- `T`: kiểu đối tượng được lưu, ví dụ `Book`.
- `ID`: kiểu khóa để tìm một đối tượng, ví dụ `String` hoặc `UUID`.

Nếu viết riêng `BookRepository`, `CustomerRepository` và `OrderRepository`, các thao tác cơ bản có thể bị lặp lại. Interface generic gom chúng vào một hợp đồng:

~~~java
interface Repository<ID, T> {
    void save(T entity) throws IOException;
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean deleteById(ID id) throws IOException;
}
~~~

Đây là trích đoạn để minh họa hợp đồng; chương trình đầy đủ ở phần 5 bao gồm tất cả import và implementation cần thiết.

- `save` thêm hoặc thay thế đối tượng có cùng ID.
- `findById` trả về `Optional<T>`: có giá trị khi tìm thấy, rỗng khi không có.
- `findAll` trả về danh sách kết quả.
- `deleteById` trả về `true` nếu có đối tượng bị xóa, ngược lại trả về `false`.

Hợp đồng khai báo `IOException` cho thao tác ghi vì backend tệp có thể gặp lỗi. Implementation in-memory không cần ném ngoại lệ này, nhưng vẫn dùng được cùng API. Interface che giấu khác biệt triển khai trong khi vẫn thể hiện loại lỗi mà bên gọi cần xử lý.

## 2. Xác định khóa và giữ đúng kiểu

Một kho dùng `Map<ID, T>` cần biết ID của từng đối tượng. Ví dụ nhận một `Function<T, ID>` ở constructor:

~~~java
Function<Book, String> idExtractor = Book::id;
~~~

`Book::id` là method reference đến accessor của record. Repository gọi hàm này để lấy khóa mà không cần biết cấu trúc cụ thể của `Book`.

Cách này cho phép tái sử dụng repository cho nhiều loại đối tượng nếu bên gọi cung cấp hàm trích xuất ID thích hợp. ID nên không null và ổn định khi đối tượng được lưu. Nếu ID thay đổi sau khi lưu, thao tác tìm hoặc xóa bằng ID mới có thể không tìm thấy mục cũ.

## 3. Backend in-memory

`InMemoryRepository<ID, T>` dùng `LinkedHashMap<ID, T>`. Khóa là ID, giá trị là đối tượng; lưu ID đã tồn tại thay thế giá trị cũ, và `LinkedHashMap` giữ thứ tự chèn khi liệt kê. Dữ liệu mất khi tiến trình kết thúc hoặc repository bị loại bỏ.

Đây là lựa chọn hữu ích cho dữ liệu tạm thời, cache đơn giản, bài tập và kiểm thử. Nó không cung cấp độ bền qua lần chạy chương trình.

## 4. Backend file-backed

`FileRepository<ID, T>` giữ một `Map` trong bộ nhớ khi đang chạy và ghi bản đồ xuống tệp sau mỗi lần lưu hoặc xóa. Khi tạo instance mới, repository nạp bản đồ từ tệp.

Vì ví dụ dùng Java Object Serialization, cả ID và đối tượng phải triển khai `Serializable`. Đây là ràng buộc của implementation lưu tệp, không phải yêu cầu của interface generic nói chung. Backend khác có thể dùng JSON, cơ sở dữ liệu quan hệ hoặc dịch vụ từ xa với những ràng buộc riêng.

Ví dụ ghi ra tệp tạm rồi chuyển vào vị trí đích để giảm nguy cơ để lại tệp dữ liệu dở dang nếu bước ghi thất bại. Mã demo chỉ dành cho tệp đáng tin cậy của ứng dụng: **không dùng `ObjectInputStream` để giải tuần tự tệp do người dùng không tin cậy cung cấp**. Object serialization còn ràng buộc định dạng dữ liệu với các lớp Java, nên không mặc nhiên là lựa chọn tốt cho lưu trữ lâu dài.

## 5. Chương trình Java 17+ hoàn chỉnh

Lưu toàn bộ mã sau vào **GenericRepositoryDemo.java**. Chương trình khai báo hợp đồng, hai implementation và kiểu dữ liệu minh họa trong cùng một tệp để dễ chạy thử.

~~~java
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

interface Repository<ID, T> {
    void save(T entity) throws IOException;
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean deleteById(ID id) throws IOException;
}

final class InMemoryRepository<ID, T> implements Repository<ID, T> {
    private final Map<ID, T> data = new LinkedHashMap<>();
    private final Function<T, ID> idExtractor;

    InMemoryRepository(Function<T, ID> idExtractor) {
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
    }

    @Override
    public synchronized void save(T entity) {
        Objects.requireNonNull(entity, "entity");
        ID id = Objects.requireNonNull(idExtractor.apply(entity), "entity id");
        data.put(id, entity);
    }

    @Override
    public synchronized Optional<T> findById(ID id) {
        return Optional.ofNullable(data.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public synchronized List<T> findAll() {
        return List.copyOf(data.values());
    }

    @Override
    public synchronized boolean deleteById(ID id) {
        return data.remove(Objects.requireNonNull(id, "id")) != null;
    }
}

final class FileRepository<ID extends Serializable, T extends Serializable>
        implements Repository<ID, T> {
    private final Path file;
    private final Function<T, ID> idExtractor;
    private final Map<ID, T> data;

    FileRepository(Path file, Function<T, ID> idExtractor) throws IOException {
        this.file = Objects.requireNonNull(file, "file");
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
        this.data = load();
    }

    @Override
    public synchronized void save(T entity) throws IOException {
        Objects.requireNonNull(entity, "entity");
        ID id = Objects.requireNonNull(idExtractor.apply(entity), "entity id");
        data.put(id, entity);
        persist();
    }

    @Override
    public synchronized Optional<T> findById(ID id) {
        return Optional.ofNullable(data.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public synchronized List<T> findAll() {
        return List.copyOf(data.values());
    }

    @Override
    public synchronized boolean deleteById(ID id) throws IOException {
        boolean removed = data.remove(Objects.requireNonNull(id, "id")) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private Map<ID, T> load() throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }

        try (ObjectInputStream input =
                     new ObjectInputStream(Files.newInputStream(file))) {
            Object stored;
            try {
                stored = input.readObject();
            } catch (ClassNotFoundException ex) {
                throw new IOException("Stored class is unavailable", ex);
            }

            if (!(stored instanceof Map<?, ?> storedMap)) {
                throw new IOException("Unexpected repository file format");
            }

            Map<ID, T> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : storedMap.entrySet()) {
                result.put((ID) entry.getKey(), (T) entry.getValue());
            }
            return result;
        }
    }

    private void persist() throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "repo-", ".tmp");

        try {
            try (ObjectOutputStream output =
                         new ObjectOutputStream(Files.newOutputStream(temporary))) {
                output.writeObject(data);
            }

            try {
                Files.move(temporary, absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, absolute,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

record Book(String id, String title) implements Serializable {}

public class GenericRepositoryDemo {
    public static void main(String[] args) throws IOException {
        Repository<String, Book> memory = new InMemoryRepository<>(Book::id);
        memory.save(new Book("b1", "Java"));
        System.out.println("Memory: " + memory.findById("b1").orElseThrow());

        Path file = Files.createTempFile("books-", ".bin");
        Files.deleteIfExists(file);

        try {
            Repository<String, Book> disk = new FileRepository<>(file, Book::id);
            disk.save(new Book("b2", "Generics"));

            Repository<String, Book> reopened =
                    new FileRepository<>(file, Book::id);
            System.out.println("File: " + reopened.findById("b2").orElseThrow());
            System.out.println("Count after reopen: " + reopened.findAll().size());

            System.out.println("Deleted b2: " + reopened.deleteById("b2"));
            Repository<String, Book> afterDelete =
                    new FileRepository<>(file, Book::id);
            System.out.println("Count after delete: " + afterDelete.findAll().size());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac GenericRepositoryDemo.java
java GenericRepositoryDemo
~~~

Stdout chính xác:

~~~text
Memory: Book[id=b1, title=Java]
File: Book[id=b2, title=Generics]
Count after reopen: 1
Deleted b2: true
Count after delete: 0
~~~

Dòng `Count after reopen: 1` cho thấy dữ liệu được đọc lại từ tệp khi tạo repository mới. Sau khi xóa `b2`, một instance mới đọc được số lượng bằng 0, xác nhận thay đổi đã được ghi xuống tệp.

## 6. Generic và interface tách trách nhiệm ra sao?

Ứng dụng khai báo biến theo interface:

~~~java
Repository<String, Book> repository =
        new InMemoryRepository<>(Book::id);
~~~

Nếu cần backend lưu tệp, có thể khởi tạo bằng implementation khác:

~~~java
Repository<String, Book> repository =
        new FileRepository<>(path, Book::id);
~~~

Hai đoạn trên minh họa cùng kiểu hợp đồng; trong một chương trình chỉ khai báo biến `repository` một lần và khởi tạo bằng backend được chọn. Phần nghiệp vụ tiếp tục gọi `save`, `findById` hoặc `findAll` mà không cần phụ thuộc trực tiếp vào lớp cụ thể.

Ba ý thiết kế quan trọng:

1. **Generic giữ an toàn kiểu.** `Repository<String, Book>` dùng `Book` làm dữ liệu và `String` làm khóa; trình biên dịch phát hiện nhiều trường hợp truyền nhầm kiểu.
2. **Interface định nghĩa hợp đồng.** Cả hai implementation cung cấp cùng thao tác nhưng dùng cơ chế lưu khác nhau.
3. **Dependency được chọn ở ranh giới khởi tạo.** Có thể truyền repository vào service qua constructor để logic nghiệp vụ không tự tạo hoặc phụ thuộc cứng vào backend.

Generic không tự tạo ra tính bền dữ liệu, đồng bộ giữa nhiều tiến trình hay giao dịch. Những thuộc tính đó phụ thuộc vào backend và hợp đồng cụ thể.

## 7. So sánh và giới hạn

| Tiêu chí | In-memory | File-backed trong ví dụ |
|---|---|---|
| Giữ dữ liệu sau khi tiến trình dừng | Không | Có, trong tệp đã ghi |
| Chi phí thao tác thông thường | Thường thấp | Có thêm I/O khi ghi |
| Yêu cầu kiểu dữ liệu | Không cần `Serializable` | ID và T phải `Serializable` |
| Chia sẻ giữa nhiều tiến trình | Không | Không được đồng bộ giữa nhiều instance |
| Phù hợp | Cache, dữ liệu tạm, test | Demo, dữ liệu nhỏ và tệp đáng tin cậy |

Implementation file-backed này nạp toàn bộ dữ liệu vào bộ nhớ lúc khởi tạo rồi ghi lại bản đồ sau mỗi thay đổi. Trong ví dụ, `save`/`deleteById` thay đổi `Map` trước khi ghi tệp; nếu `persist()` ném `IOException`, trạng thái bộ nhớ có thể đã đổi dù thao tác báo lỗi. Ứng dụng thật cần chính sách khôi phục/rollback hoặc cập nhật theo bản sao rồi chỉ công bố trạng thái sau khi lưu thành công. Hai instance cùng mở một tệp không tự đồng bộ trạng thái; các thao tác đồng thời từ nhiều tiến trình cũng không được khóa hoặc điều phối. Ứng dụng thực cần nhiều người ghi, dữ liệu lớn, truy vấn, giao dịch hoặc phục hồi lỗi nên cân nhắc cơ sở dữ liệu hay backend chuyên dụng.

Các kiểm tra nên có khi phát triển repository:

- Lưu hai đối tượng có cùng ID phải thay thế đúng đối tượng cũ.
- Tìm ID chưa tồn tại phải trả về `Optional.empty()`.
- Xóa ID chưa tồn tại phải trả về `false`.
- ID và đối tượng null phải được xử lý nhất quán.
- Lỗi đọc/ghi phải được chuyển lên nơi có thể quyết định retry, báo lỗi hoặc khôi phục.
- Kiểm tra file không hợp lệ, phiên bản lớp thay đổi và quyền truy cập tệp.
- Nếu cần dùng đồng thời, xác định chính sách đồng bộ và tính nhất quán thay vì giả định `synchronized` trên từng instance bảo vệ được một tệp dùng chung.

## 8. Lỗi thường gặp

- **Gắn kiểu cụ thể vào hợp đồng generic.** Nếu interface khai báo trực tiếp `Book`, nó khó dùng cho kiểu khác. Đặt tham số kiểu ở vị trí cần tổng quát hóa.
- **Dùng `Object` thay cho generic.** Cách này làm mất kiểm tra kiểu và thường buộc bên gọi ép kiểu.
- **Không xác định quy tắc ID.** Repository cần cách nhất quán để lấy khóa; ID null hoặc thay đổi sẽ làm tra cứu khó dự đoán.
- **Cho rằng mọi backend có cùng hành vi.** Cùng interface không có nghĩa là cùng độ trễ, độ bền, giao dịch hoặc giới hạn đồng thời.
- **Bỏ qua lỗi I/O.** Lưu xuống tệp có thể thất bại do quyền, dung lượng hoặc lỗi hệ thống. Không nên báo thành công khi dữ liệu chưa được ghi.
- **Giải tuần tự dữ liệu không đáng tin cậy.** Java native serialization không phù hợp để đọc tùy tiện dữ liệu không tin cậy; chọn định dạng và cơ chế kiểm tra an toàn phù hợp.
- **Để logic nghiệp vụ phụ thuộc implementation cụ thể.** Nếu service gọi trực tiếp chi tiết riêng của `FileRepository`, việc thay backend sẽ khó hơn và lợi ích của interface bị giảm.

## 9. Quy trình thực hành

1. Xác định kiểu dữ liệu `T`, kiểu khóa `ID` và thao tác tối thiểu mà nghiệp vụ cần.
2. Khai báo `Repository<ID, T>` bằng kiểu trả về rõ nghĩa như `Optional<T>` và `List<T>`.
3. Viết implementation đơn giản trước, chẳng hạn `InMemoryRepository`, để kiểm tra hợp đồng.
4. Thêm backend khác mà không đổi cách phần nghiệp vụ gọi interface.
5. Kiểm thử cùng bộ hành vi trên từng implementation, rồi kiểm tra riêng các thuộc tính đặc thù như độ bền, lỗi I/O và đồng thời.
6. Chọn backend theo yêu cầu thực tế về vòng đời dữ liệu, quy mô, truy vấn, vận hành và phục hồi.

Một repository generic tái sử dụng cùng hợp đồng cho nhiều kiểu dữ liệu; các implementation cung cấp cách lưu cụ thể. Khi tách hai trách nhiệm này rõ ràng, ứng dụng dễ thử nghiệm, dễ thay backend và giữ logic nghiệp vụ ít phụ thuộc vào chi tiết lưu trữ hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-008">Mô hình hóa kết quả bằng sealed interface và record</a>

<details>
<summary>Xem chi tiết</summary>

# Mô hình hóa kết quả bằng sealed interface và record

## Mục tiêu

Khi một thao tác có thể kết thúc theo nhiều cách, chẳng hạn thành công, thất bại hoặc đang chờ xử lý, ta thường cần biểu diễn rõ từng trường hợp trong kiểu dữ liệu. Kết hợp `sealed interface` với `record` cho phép định nghĩa một tập hợp các biến thể có giới hạn và mô tả dữ liệu của mỗi biến thể bằng cú pháp ngắn gọn.

`sealed interface` quy định những kiểu nào được phép triển khai trực tiếp. Mỗi `record` biểu diễn một dạng kết quả cụ thể, chứa các thành phần dữ liệu và cung cấp sẵn constructor, accessor, `equals()`, `hashCode()` và `toString()`. Cách kết hợp này hữu ích khi muốn người đọc và trình biên dịch hiểu được những dạng kết quả mà một API có thể trả về.

## 1. Vì sao nên mô hình hóa kết quả thành các biến thể?

Giả sử một API tìm kiếm tài nguyên. Kết quả có thể là:

- Thành công: có dữ liệu và mã trạng thái.
- Thất bại: có mã lỗi và thông điệp giải thích.
- Đang chờ: có thông tin về thao tác đang thực hiện.

Một thiết kế thường gặp là trả về `Object`, dùng `null` cho một số trường hợp, hoặc trả về một lớp chứa nhiều trường có thể rỗng. Các cách đó dễ tạo ra trạng thái không hợp lệ: một kết quả vừa có dữ liệu thành công vừa có thông tin lỗi, hoặc không có thông tin nào để giải thích kết quả.

Một mô hình kiểu đóng biểu diễn mỗi trường hợp thành một kiểu riêng. Khi đó, dữ liệu cần thiết cho thành công nằm trong record thành công, dữ liệu lỗi nằm trong record thất bại, còn kết quả đang chờ có cấu trúc riêng. Người gọi có thể xử lý từng trường hợp bằng cách kiểm tra kiểu cụ thể thay vì suy luận từ tổ hợp nhiều trường nullable.

## 2. Vai trò của sealed interface và record

Một sealed interface khai báo danh sách kiểu con trực tiếp bằng `permits`:

```java
sealed interface ApiResult permits Success, Failure, Pending {
}
```

Danh sách này chỉ gồm các kiểu con trực tiếp. Mỗi kiểu được liệt kê phải thực sự `implements ApiResult`. Với một phân cấp sealed, các kiểu con trực tiếp phải đóng hoặc mở rộng phân cấp theo quy tắc của Java: lớp con phải là `final`, `sealed` hoặc `non-sealed`; record vốn đã ngầm định `final`.

Các record có thể triển khai sealed interface:

```java
record Success(String value, int status) implements ApiResult {
}

record Failure(String code, String message) implements ApiResult {
}

record Pending(String operation) implements ApiResult {
}
```

Mỗi record định nghĩa dữ liệu của một trường hợp. Java tự tạo accessor theo tên thành phần, ví dụ `value()`, `status()` hoặc `message()`. Record cũng có phương thức so sánh giá trị và biểu diễn chuỗi dựa trên các thành phần của nó.

Record không thể kế thừa một lớp ứng dụng tùy chỉnh vì nó đã kế thừa trực tiếp `java.lang.Record`; cú pháp khai báo record không có mệnh đề `extends` cho lớp cha tùy chỉnh. Tuy nhiên, record có thể triển khai một hoặc nhiều interface, bao gồm sealed interface. Đây là lý do mô hình `sealed interface + record` phù hợp cho các biến thể dữ liệu.

## 3. Ví dụ hoàn chỉnh có thể chạy trên Java 17+

Ví dụ sau mô hình hóa ba kết quả của một thao tác: thành công, thất bại và đang chờ. Các constructor kiểm tra dữ liệu đầu vào để ngăn tạo ra kết quả không hợp lệ. Phương thức `describe` xử lý từng biến thể bằng pattern matching cho `instanceof`, tính năng đã được hỗ trợ chính thức từ Java 16.

Lưu nội dung vào tệp `ResultModelDemo.java`:

```java
import java.util.List;
import java.util.Objects;

public class ResultModelDemo {
    public static void main(String[] args) {
        List<ApiResult> results = List.of(
            new Success("OK", 200),
            new Failure("NOT_FOUND", "resource not found"),
            new Pending("import-catalog")
        );

        for (ApiResult result : results) {
            System.out.println(describe(result));
        }
    }

    private static String describe(ApiResult result) {
        if (result instanceof Success success) {
            return "Success: " + success.value()
                    + " (" + success.status() + ")";
        }
        if (result instanceof Failure failure) {
            return "Failure: " + failure.code()
                    + " - " + failure.message();
        }
        if (result instanceof Pending pending) {
            return "Pending: " + pending.operation();
        }

        throw new IllegalStateException(
            "Unsupported result type: " + result.getClass().getName()
        );
    }
}

sealed interface ApiResult permits Success, Failure, Pending {
}

record Success(String value, int status) implements ApiResult {
    Success {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException("success status must be 2xx");
        }
    }
}

record Failure(String code, String message) implements ApiResult {
    Failure {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}

record Pending(String operation) implements ApiResult {
    Pending {
        Objects.requireNonNull(operation, "operation must not be null");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
    }
}
```

Biên dịch và chạy:

```text
javac --release 17 -encoding UTF-8 ResultModelDemo.java
java ResultModelDemo
```

Kết quả chuẩn:

```text
Success: OK (200)
Failure: NOT_FOUND - resource not found
Pending: import-catalog
```

Ví dụ cho thấy mỗi biến thể mang đúng dữ liệu của nó. `Success` kiểm tra dữ liệu thành công và mã trạng thái; `Failure` yêu cầu mã lỗi và thông điệp; `Pending` yêu cầu tên thao tác. Các kiểm tra này giữ bất biến cơ bản ngay khi đối tượng được tạo. Vì đây là biến thể `Success` cho kết quả theo HTTP, mã trạng thái được giới hạn ở nhóm `2xx`; một phản hồi `4xx` hoặc `5xx` cần được biểu diễn bằng biến thể lỗi thay vì tạo `Success` có nghĩa mâu thuẫn.

## 4. Cách người gọi xử lý các biến thể

Khi nhận một `ApiResult`, người gọi nên xác định loại kết quả rồi xử lý theo dữ liệu của loại đó. Trong ví dụ, `instanceof Success success` vừa kiểm tra kiểu vừa tạo biến tham chiếu có kiểu cụ thể để truy cập các accessor. Các nhánh tiếp theo xử lý `Failure` và `Pending`.

Vì `ApiResult` là sealed và các kiểu con được biết trước, danh sách các biến thể là hữu hạn trong phạm vi phân cấp đó. Điều này giúp rà soát logic dễ hơn: nếu thêm một kiểu kết quả mới, người phát triển có thể tìm các điểm xử lý hiện tại và quyết định cách bổ sung nhánh tương ứng.

Tuy nhiên, đoạn `if/instanceof` không tự bảo đảm mọi nhánh đều đã được xử lý. Nhánh cuối ném `IllegalStateException` để biểu thị trường hợp không mong đợi. Khi bổ sung một biến thể mới, cần cập nhật các hàm xử lý và kiểm thử liên quan.

Java 21 trở lên hỗ trợ pattern matching cho `switch` như một tính năng chính thức, có thể kết hợp với sealed hierarchy để viết phép phân nhánh rõ ràng hơn. Trên Java 17, pattern matching cho `switch` vẫn là preview, nên ví dụ này dùng `instanceof` để biên dịch mà không cần bật preview.

## 5. Giữ mô hình hợp lệ và dễ mở rộng

### Tránh trạng thái mâu thuẫn

Không nên gom tất cả trường vào một record duy nhất như `Result(String value, String errorCode, String message, boolean pending)` nếu nhiều tổ hợp trường có thể xuất hiện nhưng không có ý nghĩa. Một record thành công có cả dữ liệu lỗi, hoặc một kết quả thất bại không có mã lỗi, làm tăng số trạng thái mà mọi người gọi phải kiểm tra.

Các biến thể riêng giúp giới hạn cấu trúc dữ liệu có thể tạo. Constructor compact của record cho phép kiểm tra đầu vào mà không cần viết lại phần gán các thành phần. Nếu dữ liệu đầu vào vi phạm bất biến, có thể ném `IllegalArgumentException` hoặc `NullPointerException` một cách nhất quán.

### Thêm biến thể mới có chủ đích

Nếu API cần thêm trạng thái như `Cancelled`, hãy bổ sung kiểu đó vào `permits` và triển khai sealed interface. Sau đó, cập nhật các hàm xử lý, tài liệu và kiểm thử. Thay đổi tập biến thể có thể ảnh hưởng đến mã khách hàng vốn giả định chỉ có các trường hợp cũ, vì vậy cần xem xét tương thích khi phát hành thư viện công khai.

Các kiểu được phép kế thừa hoặc triển khai phải tuân thủ điều kiện vị trí: trong named module, chúng phải thuộc cùng module; trong unnamed module, chúng phải ở cùng package. Đặt một kiểu con ở sai module hoặc package sẽ khiến phân cấp không hợp lệ.

### Hiểu giới hạn của record

Record phù hợp với dữ liệu có thành phần được xác định rõ và không cần kế thừa trạng thái từ lớp cha ứng dụng. Các thành phần record là final, nhưng tính bất biến chỉ ở mức nông: nếu một thành phần tham chiếu đến một đối tượng mutable, đối tượng đó vẫn có thể thay đổi. Trong ví dụ, các thành phần đều là `String`, vốn bất biến, nên vấn đề này không phát sinh.

Không cần tạo getter theo kiểu JavaBean như `getValue()`; accessor của record dùng trực tiếp tên thành phần, chẳng hạn `value()`. Nếu API yêu cầu quy ước hoặc hành vi khác, có thể khai báo thêm phương thức, nhưng cần giữ ý nghĩa rõ ràng và tránh trùng lặp không cần thiết.

## 6. Khi nào nên dùng mô hình này?

| Tình huống | Thiết kế phù hợp |
|---|---|
| Một thao tác có vài kết quả đã xác định rõ | Sealed interface cùng các record đại diện cho từng kết quả |
| Mỗi kết quả mang bộ dữ liệu khác nhau | Một record riêng cho mỗi biến thể |
| Cần giới hạn kiểu kết quả mà mã ứng dụng được phép tạo | Sealed interface với danh sách `permits` tường minh |
| Các kiểu cần chia sẻ trạng thái từ lớp cha tùy chỉnh | Xem xét sealed class với các lớp con thông thường |
| Các kết quả cần mở rộng bởi thư viện bên ngoài | Cân nhắc interface không sealed hoặc một nhánh `non-sealed`, hiểu rõ rằng phạm vi biến thể sẽ mở rộng |

Không nên chọn sealed chỉ vì cú pháp mới hơn. Sealed hữu ích khi việc giới hạn các biến thể là một phần của thiết kế. Nếu bên thứ ba cần tự định nghĩa kiểu triển khai mới, một interface mở có thể phù hợp hơn.

## Ghi nhớ

- `sealed interface` giới hạn những kiểu được phép triển khai trực tiếp; `permits` liệt kê các kiểu con trực tiếp.
- `record` mô tả dữ liệu của một biến thể, cung cấp accessor và các phương thức dựa trên giá trị; record ngầm định là `final`.
- Record không thể kế thừa lớp ứng dụng tùy chỉnh, nhưng có thể triển khai sealed interface.
- Tách thành các record riêng giúp tránh tổ hợp trường nullable và trạng thái mâu thuẫn.
- Sau khi thêm biến thể, cập nhật các điểm xử lý và kiểm thử; sealed giúp xác định phạm vi kiểu nhưng không tự bảo đảm logic nghiệp vụ đã đầy đủ.
- Ví dụ dùng Java 17 và `instanceof` pattern matching, không cần bật preview.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-009">So sánh lambda với anonymous class trong cùng bài toán</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh lambda với anonymous class trong cùng bài toán

Lambda expression và anonymous class đều cho phép cung cấp một triển khai tại chỗ cho một kiểu đích trong Java. Khi làm việc với functional interface, cả hai có thể thực hiện cùng một nhiệm vụ và cho ra kết quả giống nhau. Khác biệt nằm ở cú pháp, phạm vi của từ khóa this, khả năng khai báo trạng thái riêng và loại interface mà mỗi cách có thể triển khai.

Bài này so sánh hai cách viết trong cùng bài toán kiểm tra một chuỗi có nội dung hay không, sau đó dùng một ví dụ ngắn để làm rõ sự khác nhau về this. Các ví dụ hoàn chỉnh dùng Java 17; lambda và functional interface có từ Java 8.

## 1. Bài toán chung: kiểm tra chuỗi hợp lệ

Giả sử chương trình cần một quy tắc xác định chuỗi có giá trị sử dụng được hay không. Quy tắc trả về true khi chuỗi khác null và không rỗng hoặc chỉ chứa khoảng trắng. Java có sẵn functional interface Predicate&lt;T&gt; với phương thức trừu tượng test(T), phù hợp cho bài toán này.

Có thể triển khai cùng quy tắc bằng lambda:

~~~java
Predicate<String> validator =
        text -> text != null && !text.isBlank();
~~~

Hoặc bằng anonymous class:

~~~java
Predicate<String> validator = new Predicate<String>() {
    @Override
    public boolean test(String text) {
        return text != null && !text.isBlank();
    }
};
~~~

Cả hai biến đều có kiểu Predicate&lt;String&gt;, nên client gọi validator.test(text) theo cùng một cách. Lambda rút gọn phần cú pháp bao quanh phương thức; anonymous class thể hiện tường minh một lớp vô danh triển khai interface và ghi đè phương thức test.

## 2. Chương trình hoàn chỉnh để so sánh

Lưu mã sau thành tệp LambdaVsAnonymousDemo.java:

~~~java
import java.util.function.Predicate;

public class LambdaVsAnonymousDemo {
    private final String name = "outer";

    public static void main(String[] args) {
        Predicate<String> lambdaValidator =
                text -> text != null && !text.isBlank();

        Predicate<String> anonymousValidator = new Predicate<>() {
            @Override
            public boolean test(String text) {
                return text != null && !text.isBlank();
            }
        };

        String[] samples = {"Java", "", "   "};
        for (String sample : samples) {
            System.out.println("'" + sample + "' -> lambda="
                    + lambdaValidator.test(sample) + ", anonymous="
                    + anonymousValidator.test(sample));
        }

        new LambdaVsAnonymousDemo().compareThis();
    }

    private void compareThis() {
        Runnable lambda =
                () -> System.out.println("lambda this=" + this.name);

        Runnable anonymous = new Runnable() {
            private final String name = "anonymous";

            @Override
            public void run() {
                System.out.println("anonymous this=" + this.name);
                System.out.println("outer via qualified this="
                        + LambdaVsAnonymousDemo.this.name);
            }
        };

        lambda.run();
        anonymous.run();
    }
}
~~~

Biên dịch và chạy:

~~~text
javac LambdaVsAnonymousDemo.java
java LambdaVsAnonymousDemo
~~~

Kết quả stdout chính xác:

~~~text
'Java' -> lambda=true, anonymous=true
'' -> lambda=false, anonymous=false
'   ' -> lambda=false, anonymous=false
lambda this=outer
anonymous this=anonymous
outer via qualified this=outer
~~~

Ba dòng đầu cho thấy hai cách triển khai cùng quy tắc và cho kết quả giống nhau với chuỗi có nội dung, chuỗi rỗng và chuỗi chỉ có khoảng trắng. Ba dòng sau minh họa một khác biệt ngữ nghĩa quan trọng: lambda dùng this của đối tượng bao quanh, còn this bên trong anonymous class trỏ đến chính đối tượng vô danh đó.

Trong ví dụ, cú pháp new Predicate<>() dùng toán tử diamond với anonymous class, được hỗ trợ từ Java 9. Nếu cần tương thích với Java 8, hãy viết new Predicate&lt;String&gt;() đầy đủ kiểu tham số.

## 3. Khác biệt về cú pháp và kiểu đích

Lambda có dạng tổng quát gồm danh sách tham số, mũi tên và thân biểu thức hoặc khối lệnh. Khi kiểu tham số có thể suy ra từ kiểu đích, thường không cần khai báo kiểu tường minh.

~~~java
Predicate<String> hasText = text -> !text.isBlank();
Predicate<String> hasTextExplicit =
        (String text) -> !text.isBlank();
~~~

Anonymous class dùng biểu thức new để khai báo một lớp không có tên, đồng thời tạo một instance của lớp đó. Phần thân có thể khai báo các trường, phương thức và khối khởi tạo, nhưng phương thức cần thiết của interface phải được triển khai theo đúng chữ ký.

Cả hai đều cần ngữ cảnh kiểu phù hợp. Lambda chỉ dùng được khi kiểu đích là functional interface: interface có đúng một phương thức trừu tượng theo quy tắc Java, sau khi tính đến các phương thức tương ứng với public của Object và các phương thức default hoặc static. Annotation @FunctionalInterface không bắt buộc, nhưng giúp compiler báo lỗi nếu interface không còn thỏa điều kiện.

Anonymous class có thể triển khai một interface không phải functional interface, vì nó có thể định nghĩa nhiều phương thức trừu tượng. Nó cũng có thể kế thừa một lớp cụ thể và ghi đè các phương thức của lớp đó. Lambda không thể thay thế trực tiếp những trường hợp này.

Ví dụ, interface sau có hai phương thức trừu tượng:

~~~java
interface AuditAction {
    void record(String event);
    void flush();
}
~~~

Anonymous class có thể cung cấp cả hai phương thức:

~~~java
AuditAction action = new AuditAction() {
    @Override
    public void record(String event) {
        System.out.println("record: " + event);
    }

    @Override
    public void flush() {
        System.out.println("flush");
    }
};
~~~

Không thể viết một lambda duy nhất để triển khai AuditAction, bởi không có một phương thức trừu tượng duy nhất làm hợp đồng hàm đích. Nếu API được thiết kế để nhận lambda, hãy tạo một functional interface với một thao tác chính và tách các trách nhiệm khác sang API phù hợp.

## 4. Khác biệt của this và phạm vi tên

Lambda không tạo ra một phạm vi this mới. Khi thân lambda tham chiếu this, nó đang nói đến đối tượng của lớp bao quanh. Điều này giúp lambda đọc trường hoặc gọi phương thức của đối tượng bên ngoài một cách tự nhiên.

Anonymous class có phạm vi đối tượng riêng. Bên trong phương thức của anonymous class, this tham chiếu instance vô danh. Muốn truy cập đối tượng bao quanh, có thể dùng cú pháp OuterClass.this, trong đó OuterClass là tên lớp bao quanh.

Trong chương trình trên:

- Lambda in lambda this=outer vì this.name là trường của LambdaVsAnonymousDemo.
- Anonymous class khai báo trường name riêng có giá trị anonymous; vì vậy this.name trong run() truy cập trường của anonymous class.
- LambdaVsAnonymousDemo.this.name truy cập tường minh trường name của đối tượng bên ngoài.

Đây là khác biệt ngữ nghĩa, không chỉ là khác biệt về số dòng code. Khi chuyển một anonymous class thành lambda hoặc ngược lại, hãy kiểm tra mọi tham chiếu this, super, các trường cùng tên và lời gọi phương thức. Một phép chuyển đổi máy móc có thể làm thay đổi hành vi nếu mã ban đầu dựa vào đối tượng this của anonymous class.

## 5. Trạng thái, trường và phương thức bổ sung

Anonymous class có thể khai báo các trường instance, khối khởi tạo và phương thức bổ sung. Ví dụ ở trên khai báo trường name riêng bên trong anonymous class. Những thành viên này thuộc về instance vô danh và có thể được sử dụng bởi các phương thức của nó.

Lambda không khai báo một lớp mới theo cú pháp nguồn, nên không thể chứa khai báo trường instance hay phương thức thành viên riêng như phần thân của anonymous class. Nếu cần trạng thái có vòng đời riêng, nhiều thao tác liên quan hoặc một API có nhiều phương thức, hãy cân nhắc anonymous class, lớp có tên hoặc một lớp riêng.

Lambda vẫn có thể đọc các biến cục bộ trong phạm vi bao quanh, miễn là chúng là final hoặc effectively final. Quy tắc capture này cũng áp dụng cho anonymous class. Effectively final nghĩa là biến được gán một lần và không bị gán lại sau đó.

~~~java
String prefix = "event: ";
Predicate<String> hasPrefix =
        text -> text != null && text.startsWith(prefix);
~~~

Ví dụ hợp lệ vì prefix không bị gán lại. Nếu chương trình gán prefix một giá trị khác sau khi tạo lambda, mã sẽ không biên dịch. Với cả lambda lẫn anonymous class, khi cần thay đổi trạng thái theo thời gian, thường nên đóng gói trạng thái vào một đối tượng có trách nhiệm rõ ràng thay vì cố sửa biến cục bộ đã capture.

## 6. Khi nào nên dùng từng cách?

Ưu tiên lambda khi biểu diễn một hành động đơn giản thông qua functional interface, chẳng hạn Predicate, Function, Consumer, Supplier, Comparator hoặc một interface hàm do ứng dụng định nghĩa. Lambda giúp tập trung vào logic cần thực hiện và giảm phần cú pháp triển khai.

Anonymous class phù hợp khi cần triển khai nhiều phương thức trừu tượng, thêm trạng thái riêng, ghi đè nhiều hành vi hoặc sử dụng một lớp vô danh làm kiểu con của lớp cụ thể. Nó cũng có thể giúp làm rõ cấu trúc khi triển khai phức tạp hơn một biểu thức ngắn.

| Tiêu chí | Lambda | Anonymous class |
|---|---|---|
| Kiểu đích | Bắt buộc là functional interface | Có thể là interface thường hoặc lớp có thể kế thừa |
| Số phương thức cần triển khai | Một phương thức hàm | Có thể triển khai nhiều phương thức |
| Cú pháp | Thường ngắn gọn, kiểu tham số có thể được suy ra | Tường minh với new và phần thân lớp |
| this | this của phạm vi bao quanh | this của instance vô danh |
| Trường instance và phương thức bổ sung | Không khai báo trực tiếp trong thân lambda | Có thể khai báo trong anonymous class |
| Capture biến cục bộ | Biến phải final hoặc effectively final | Cùng quy tắc |
| Phù hợp nhất | Một hành động hoặc phép biến đổi | Triển khai có nhiều thành viên hoặc trạng thái riêng |

Không nên kết luận rằng lambda luôn nhanh hơn hoặc luôn dùng ít bộ nhớ hơn anonymous class. Cách triển khai ở runtime và tối ưu hóa của JVM phụ thuộc ngữ cảnh; lựa chọn nên dựa trên hợp đồng API, tính đúng đắn, khả năng đọc hiểu và đo đạc thực tế nếu hiệu năng là vấn đề.

## 7. Các lỗi thường gặp khi chuyển đổi

- **Dùng lambda cho interface có nhiều phương thức trừu tượng:** không có một functional method duy nhất để lambda triển khai. Hãy dùng anonymous class hoặc thiết kế lại API.
- **Quên kiểm tra this:** lambda dùng this của lớp bao quanh, trong khi anonymous class có this riêng. Kiểm tra các tham chiếu this và tên trường trùng nhau.
- **Gán lại biến đã capture:** biến cục bộ được dùng trong lambda hoặc anonymous class phải là final hoặc effectively final.
- **Bỏ sót phần thân của phương thức:** khi chuyển anonymous class sang lambda, giữ nguyên logic, điều kiện, thứ tự tác dụng phụ và cách xử lý exception.
- **Cho rằng hai cách luôn tương đương:** chúng có thể thực hiện cùng kết quả trong một bài toán đơn giản, nhưng khác về phạm vi this, thành viên lớp và kiểu đích.
- **Dùng anonymous class khi lambda đủ rõ:** với một thao tác đơn giản, phần cú pháp lớp dài có thể che khuất logic chính.
- **Dùng lambda khi cần một đối tượng có nhiều trách nhiệm:** nếu có nhiều phương thức, trạng thái riêng hoặc hành vi phức tạp, một lớp có tên có thể dễ kiểm thử và bảo trì hơn.

## 8. Quy trình so sánh và kiểm thử

Khi chuyển một đoạn code từ anonymous class sang lambda hoặc ngược lại, hãy làm theo quy trình:

1. Xác định kiểu đích và phương thức mà client thực sự gọi.
2. Viết hai triển khai với cùng điều kiện đầu vào, logic và kết quả kỳ vọng.
3. Chạy cả hai trên các trường hợp bình thường, đầu vào rỗng, null nếu API cho phép, và các trường hợp biên có ý nghĩa.
4. So sánh tác dụng phụ, exception, thứ tự gọi phương thức và việc capture biến.
5. Kiểm tra đặc biệt this, trường cùng tên và phương thức được kế thừa.
6. Biên dịch với phiên bản Java mục tiêu để phát hiện cú pháp hoặc API không được hỗ trợ.

Trong ví dụ kiểm tra chuỗi, cả hai triển khai trả về cùng kết quả cho ba đầu vào đã thử. Ví dụ this cho thấy tại sao việc so sánh chỉ dựa trên kết quả của một bài toán đơn giản là chưa đủ để kết luận hai cấu trúc luôn thay thế được cho nhau.

## 9. Bài tập tự luyện

1. Thay Predicate&lt;String&gt; bằng một functional interface tự định nghĩa có phương thức boolean isValid(String text). Viết cả lambda và anonymous class, sau đó kiểm thử cùng bộ dữ liệu.
2. Tạo một interface có hai phương thức trừu tượng. Triển khai bằng anonymous class và giải thích vì sao không thể dùng một lambda trực tiếp.
3. Thêm một biến cục bộ được capture vào cả hai cách triển khai. Xác minh rằng việc gán lại biến đó sau khi khai báo sẽ gây lỗi biên dịch.
4. Trong anonymous class, khai báo một trường trùng tên với trường của lớp ngoài. So sánh this.field với OuterClass.this.field.
5. Chuyển một anonymous class hiện có sang lambda và liệt kê những khác biệt cần kiểm tra trước khi chấp nhận thay đổi.

Cách chọn đúng bắt đầu từ hợp đồng mà client cần: nếu đó là một thao tác duy nhất của functional interface, lambda thường là lựa chọn gọn và rõ; nếu cần nhiều phương thức, trạng thái riêng hoặc phạm vi đối tượng this độc lập, anonymous class hoặc lớp có tên thể hiện ý định chính xác hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-010">Hoàn thiện ví dụ tích hợp và bộ kiểm thử hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn thiện ví dụ tích hợp và bộ kiểm thử hợp đồng

Một API Java có thể kết hợp interface công khai, default method và abstract class hỗ trợ. Để các cách triển khai khác nhau vẫn tuân thủ cùng một hợp đồng, hãy kiểm thử hành vi quan sát được của interface trên **từng implementation**. Bộ kiểm thử hợp đồng (contract test suite) gom các điều kiện chung vào một nơi và chạy lại chúng cho mọi lớp triển khai.

Bài này xây dựng một ví dụ hoàn chỉnh về bộ định dạng văn bản. Một lớp triển khai interface trực tiếp; lớp còn lại kế thừa abstract class để dùng lại quy trình chung. Cả hai cùng chạy qua một bộ kiểm thử, sau đó các kiểm thử riêng xác nhận đầu ra đặc thù của từng lớp.

## 1. Hợp đồng cần kiểm thử

Interface `TextFormatter` cung cấp hai thao tác:

- `format(String)` định dạng văn bản. Đầu vào `null` bị từ chối bằng `NullPointerException`; khoảng trắng ở hai đầu không ảnh hưởng kết quả.
- `supports(String)` là default method, trả về `true` khi chuỗi khác `null` và không rỗng sau khi xét khoảng trắng. Với `null` hoặc chuỗi chỉ chứa khoảng trắng, kết quả là `false`.

Hợp đồng mô tả những gì người gọi được phép dựa vào, không yêu cầu mọi implementation phải tạo ra chuỗi giống hệt nhau. Ví dụ, một formatter có thể trả về `Java`, còn formatter khác trả về `[JAVA]`. Cả hai vẫn đáp ứng hợp đồng nếu chúng tuân thủ các điều kiện đã nêu.

Bộ kiểm thử sẽ xác nhận rằng mỗi formatter:

1. Hỗ trợ văn bản không trống.
2. Không tuyên bố hỗ trợ `null` hoặc chuỗi trống sau khi bỏ khoảng trắng.
3. Trả về kết quả khác `null` với đầu vào hợp lệ.
4. Cho kết quả tương đương khi đầu vào chỉ khác khoảng trắng ở hai đầu, kể cả ký tự khoảng trắng Unicode.
5. Có kết quả ổn định khi gọi lại với cùng đầu vào.
6. Xử lý chuỗi chỉ có khoảng trắng nhất quán với chuỗi rỗng.
7. Ném đúng `NullPointerException` khi `format` nhận `null`.

Các điều kiện này áp dụng cho mọi implementation. Kiểm thử riêng có thể bổ sung những khẳng định cụ thể về kiểu định dạng của từng lớp.

## 2. Vai trò của từng thành phần

```text
TextFormatter (hợp đồng công khai)
    ├── PlainTextFormatter (implements trực tiếp)
    └── AbstractTextFormatter (lớp hỗ trợ)
            └── BracketTextFormatter (extends)
```

`TextFormatter` là kiểu mà mã khách hàng phụ thuộc vào. Default method `supports` cung cấp một hành vi nhỏ, có thể tính được từ đầu vào và không cần trạng thái đối tượng.

`AbstractTextFormatter` dùng Template Method: phương thức `format` là `final`, kiểm tra đầu vào và chuẩn hóa khoảng trắng trước khi chuyển phần còn lại cho hook `formatTrimmed`. Lớp con chỉ quyết định cách trình bày chuỗi đã chuẩn hóa.

`PlainTextFormatter` triển khai interface trực tiếp để minh họa rằng abstract class là tùy chọn. Lớp này tự thực hiện quy tắc bỏ khoảng trắng ở hai đầu. Dù cấu trúc triển khai khác nhau, cả hai lớp phải vượt qua cùng bộ kiểm thử hợp đồng.

## 3. Ví dụ tích hợp chạy được trên Java 17+

Lưu toàn bộ mã sau vào tệp `FormatterContractDemo.java`. Các kiểu phụ không khai báo `public` nên có thể đặt cùng tệp với lớp chạy chính.

```java
import java.util.Locale;
import java.util.Objects;

interface TextFormatter {
    String format(String input);

    default boolean supports(String input) {
        return input != null && !input.isBlank();
    }
}

abstract class AbstractTextFormatter implements TextFormatter {
    @Override
    public final String format(String input) {
        Objects.requireNonNull(input, "input");
        return formatTrimmed(input.strip());
    }

    protected abstract String formatTrimmed(String trimmedInput);
}

class PlainTextFormatter implements TextFormatter {
    @Override
    public String format(String input) {
        Objects.requireNonNull(input, "input");
        return input.strip();
    }
}

class BracketTextFormatter extends AbstractTextFormatter {
    @Override
    protected String formatTrimmed(String trimmedInput) {
        return "[" + trimmedInput.toUpperCase(Locale.ROOT) + "]";
    }
}

public class FormatterContractDemo {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) {
            throw new AssertionError("Contract check failed: " + description);
        }
    }

    private static void expectThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String description) {
        try {
            action.run();
        } catch (Throwable actual) {
            check(expected.isInstance(actual),
                    description + " (wrong exception: " + actual + ")");
            return;
        }
        check(false, description + " (no exception thrown)");
    }

    private static void verifyContract(String name, TextFormatter formatter) {
        check(formatter.supports("Java"), name + " supports nonblank text");
        check(!formatter.supports(null), name + " does not support null");
        check(!formatter.supports(" \t "), name + " does not support blank text");

        String expected = formatter.format("Java");
        check(expected != null, name + " returns a non-null result");
        check(expected.equals(formatter.format(" Java ")),
                name + " ignores leading and trailing whitespace");
        check(expected.equals(formatter.format("\u2003Java\u2003")),
                name + " ignores Unicode edge whitespace");
        check(expected.equals(formatter.format("Java")), name + " is deterministic");
        check(formatter.format("   ").equals(formatter.format("")),
                name + " treats whitespace-only input like empty input");
        expectThrows(NullPointerException.class, () -> formatter.format(null),
                name + " rejects null input");

        System.out.println("PASS " + name + ": 9 contract checks");
    }

    public static void main(String[] args) {
        TextFormatter plain = new PlainTextFormatter();
        TextFormatter bracketed = new BracketTextFormatter();

        verifyContract("PlainTextFormatter", plain);
        verifyContract("BracketTextFormatter", bracketed);

        check("Java".equals(plain.format(" Java ")),
                "plain formatter's specific output");
        check("[JAVA]".equals(bracketed.format(" Java ")),
                "bracket formatter's specific output");

        System.out.println("Plain: " + plain.format(" Java "));
        System.out.println("Bracket: " + bracketed.format(" Java "));
        System.out.println("Contract suite passed: " + checks + " checks");
    }
}
```

Ở đây `strip()` (Java 11+) xử lý khoảng trắng Unicode nhất quán với `String.isBlank()`, trong khi `trim()` không loại bỏ mọi ký tự khoảng trắng Unicode. Bộ kiểm thử thêm trường hợp `U+2003` (EM SPACE) ở hai đầu để xác minh điều này.

Biên dịch và chạy:

```text
javac -encoding UTF-8 FormatterContractDemo.java
java FormatterContractDemo
```

Kết quả chuẩn:

```text
PASS PlainTextFormatter: 9 contract checks
PASS BracketTextFormatter: 9 contract checks
Plain: Java
Bracket: [JAVA]
Contract suite passed: 20 checks
```

Bộ kiểm thử chạy chín điều kiện hợp đồng cho mỗi formatter, tổng cộng 18 kiểm tra chung, rồi chạy hai kiểm tra đầu ra riêng. Nếu bất kỳ điều kiện nào sai, `check` ném `AssertionError` và chương trình dừng với thông tin kiểm tra thất bại. Cách này cho phép chạy ví dụ ngay bằng JDK, không cần cài thêm thư viện kiểm thử.

## 4. Vì sao cần kiểm thử từng implementation?

Kiểm thử một lớp không chứng minh rằng mọi lớp tuân thủ interface đều đúng. `PlainTextFormatter` có thể bỏ khoảng trắng chính xác, trong khi một implementation khác vô tình trả lại đầu vào nguyên trạng. Nếu chỉ kiểm thử lớp thứ nhất, lỗi của lớp thứ hai sẽ không được phát hiện.

Lời gọi `verifyContract` nhận tên và một `TextFormatter`, vì vậy cùng logic kiểm thử được dùng lại cho cả hai đối tượng. Khi thêm implementation thứ ba, chỉ cần tạo đối tượng và gọi cùng hàm hợp đồng:

```java
TextFormatter another = new AnotherTextFormatter();
verifyContract("AnotherTextFormatter", another);
```

Dòng trên là đoạn minh họa để thêm vào chương trình; `AnotherTextFormatter` phải được định nghĩa và đáp ứng cùng hợp đồng trước khi biên dịch.

Nên chạy bộ kiểm thử hợp đồng trên mọi implementation công khai, kể cả implementation của module khác hoặc của bên thứ ba nếu dự án kiểm soát được việc kiểm thử đó. Khi thêm một default method mới vào interface, hãy bổ sung các điều kiện liên quan vào bộ hợp đồng để mọi implementation đều được kiểm tra theo cùng một chuẩn.

## 5. Kiểm thử hợp đồng và kiểm thử riêng khác nhau thế nào?

**Kiểm thử hợp đồng** xác nhận quy tắc chung mà người gọi được quyền dựa vào. Những quy tắc này không nên phụ thuộc vào chi tiết nội bộ của một lớp.

**Kiểm thử riêng của implementation** xác nhận hành vi đặc thù mà lớp đó cam kết. Trong ví dụ, hai kiểm tra sau chỉ thuộc về các formatter cụ thể:

- `PlainTextFormatter` trả về `Java` khi đầu vào là `" Java "`.
- `BracketTextFormatter` trả về `[JAVA]` cho cùng đầu vào.

Không nên đưa yêu cầu “mọi formatter phải trả về chuỗi viết hoa trong ngoặc vuông” vào bộ hợp đồng chung, vì điều đó sẽ loại bỏ hợp lệ các formatter có mục đích khác. Hãy phân biệt điều gì thuộc interface và điều gì thuộc riêng một implementation.

## 6. Abstract class giúp duy trì hợp đồng ra sao?

`AbstractTextFormatter` khai báo `format` là `final` để lớp con không thể vô tình bỏ qua các bước kiểm tra `null` và chuẩn hóa khoảng trắng. Các lớp con chỉ ghi đè `formatTrimmed`, nhận đầu vào đã được chuẩn hóa.

Đây là cách sử dụng Template Method để giữ các bước bất biến trong lớp cơ sở. Nó hữu ích khi nhiều lớp cần chung một quy trình và chỉ khác nhau ở một bước. Tuy nhiên, nó không tự đảm bảo toàn bộ hợp đồng của interface: phương thức hook vẫn có thể trả về `null` hoặc tạo kết quả không phù hợp. Bộ kiểm thử hợp đồng cần tiếp tục xác minh kết quả quan sát được.

Với `PlainTextFormatter`, quy tắc được thực hiện trực tiếp trong `format`. Việc lặp lại một chút mã là có chủ đích để minh họa lựa chọn triển khai interface mà không kế thừa abstract class. Trong API thực tế, cân nhắc chi phí bảo trì của phần lặp và mức độ cần thiết của lớp hỗ trợ.

## 7. Mở rộng sang JUnit

Trong dự án lớn, có thể chuyển cùng ý tưởng sang JUnit 5 để mỗi điều kiện là một test độc lập, dễ xem kết quả và dễ tích hợp vào CI. Một cách tổ chức phổ biến là tạo lớp kiểm thử hợp đồng cơ sở với một phương thức factory cung cấp đối tượng cần kiểm tra, rồi tạo lớp con cho từng implementation. Các test chung được kế thừa và chạy riêng cho từng lớp.

Ví dụ cấu trúc:

```text
src/
  main/java/
    TextFormatter.java
    AbstractTextFormatter.java
    PlainTextFormatter.java
    BracketTextFormatter.java
  test/java/
    TextFormatterContractTest.java
    PlainTextFormatterTest.java
    BracketTextFormatterTest.java
```

Lớp kiểm thử hợp đồng trong JUnit sẽ đặt các khẳng định về `supports`, xử lý `null`, chuẩn hóa đầu vào và tính ổn định vào một nơi. Mỗi lớp kiểm thử cụ thể cung cấp formatter thông qua factory hoặc phương thức khởi tạo. Các test riêng cho đầu ra `Java` và `[JAVA]` vẫn ở lớp kiểm thử của implementation tương ứng.

Dù dùng harness tự viết hay JUnit, nguyên tắc vẫn như nhau: cùng một bộ điều kiện phải chạy trên mọi implementation, còn các chi tiết riêng phải được kiểm tra tách biệt. Harness trong ví dụ chính là phiên bản độc lập, có thể biên dịch và chạy ngay mà không phụ thuộc công cụ ngoài.

## 8. Các lỗi thường gặp

- **Chỉ kiểm thử lớp hỗ trợ:** lớp triển khai interface trực tiếp vẫn có thể vi phạm hợp đồng.
- **Kiểm thử chi tiết nội bộ:** kiểm tra hook được gọi bao nhiêu lần hoặc trường nội bộ có giá trị gì thường làm test phụ thuộc cấu trúc, thay vì hành vi công khai.
- **Đưa đầu ra đặc thù vào hợp đồng chung:** yêu cầu mọi implementation tạo cùng một kiểu chuỗi sẽ làm hợp đồng quá chặt nếu interface chỉ cam kết những thuộc tính tổng quát.
- **Bỏ qua trường hợp biên:** `null`, chuỗi rỗng, chuỗi chỉ có khoảng trắng và đầu vào có khoảng trắng hai đầu thường phát hiện lỗi hợp đồng.
- **Không cập nhật bộ kiểm thử khi hợp đồng đổi:** nếu thêm một default method hoặc thay đổi quy tắc đầu vào, hãy cập nhật test chung và chạy lại trên tất cả implementation.
- **Chỉ nhìn số lượng test thành công:** các test phải kiểm tra điều kiện có ý nghĩa; một loạt assertion lặp lại cùng một hành vi không thay thế được độ bao phủ của hợp đồng.

## 9. Quy trình hoàn thiện ví dụ tích hợp

1. Xác định các điều kiện công khai mà mọi implementation phải tuân theo.
2. Đặt hợp đồng trong interface; chỉ dùng default method cho hành vi chung phù hợp.
3. Cung cấp abstract class khi có quy trình triển khai thực sự dùng chung, và để các lớp có thể triển khai interface trực tiếp khi cần.
4. Viết một bộ kiểm thử nhận interface làm kiểu đầu vào, không phụ thuộc lớp cụ thể.
5. Chạy bộ hợp đồng cho từng implementation, sau đó bổ sung test riêng cho hành vi đặc thù.
6. Biên dịch, chạy test và xác nhận kết quả đầu ra thực tế trước khi đưa ví dụ vào tài liệu.

Một ví dụ tích hợp hoàn chỉnh cần thể hiện cả thiết kế lẫn cách xác minh thiết kế đó. Interface xác định hành vi mà mã khách hàng được quyền trông đợi; abstract class hỗ trợ tái sử dụng các bước chung; bộ kiểm thử hợp đồng xác nhận mọi cách triển khai đều giữ đúng những bảo đảm công khai ấy.

</details>

- [Quay lại đầu trang](#back-to-top)
