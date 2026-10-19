<a id="back-to-top"></a>

# Ngoại lệ tùy chỉnh

## Menu
- [Khi nào cần định nghĩa loại ngoại lệ riêng](#custom-exceptions-001)
- [Chọn extends Exception hay extends RuntimeException](#custom-exceptions-002)
- [Đặt tên theo tình huống thất bại và mức trừu tượng](#custom-exceptions-003)
- [Constructor hỗ trợ message và cause](#custom-exceptions-004)
- [Thuộc tính ngữ cảnh có kiểu và tránh dữ liệu dư thừa](#custom-exceptions-005)
- [Thiết kế hệ phân cấp theo cách bên gọi cần xử lý](#custom-exceptions-006)
- [Giữ thông tin lỗi độc lập với câu chữ của message](#custom-exceptions-007)
- [Giới hạn generic: lớp con Throwable không được có tham số kiểu](#custom-exceptions-008)
- [Serialization và serialVersionUID khi hợp đồng có yêu cầu](#custom-exceptions-009)
- [Tránh kế thừa trực tiếp Throwable hoặc Error khi không phù hợp](#custom-exceptions-010)

## <a id="custom-exceptions-001">Khi nào cần định nghĩa loại ngoại lệ riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào cần định nghĩa loại ngoại lệ riêng trong Java?

**Ngoại lệ tùy chỉnh (custom exception)** là một lớp do ứng dụng định nghĩa và kế thừa `Exception`, `RuntimeException` hoặc một lớp ngoại lệ phù hợp khác. Mục tiêu của nó không chỉ là đổi tên một lỗi: một loại ngoại lệ tốt phải biểu đạt **một ý nghĩa thất bại riêng**, giúp caller xử lý đúng, bảo vệ ranh giới abstraction hoặc mang dữ liệu chẩn đoán có cấu trúc.

Ví dụ, hệ thống đặt hàng có thể phân biệt:

- Người gọi truyền `orderId` rỗng: đây là **tham số không hợp lệ**, thường có thể dùng `IllegalArgumentException` có sẵn.
- Đơn hàng chưa ở trạng thái cho phép thanh toán: đây có thể là **lỗi bất biến/trạng thái miền nghiệp vụ** cần một `InvalidOrderStateException` riêng nếu nhiều caller xử lý khác nhau.
- Cổng thanh toán không kết nối được: đây là **lỗi hạ tầng**; tầng nghiệp vụ có thể bọc `IOException` thành `PaymentGatewayUnavailableException` để không làm API công khai phụ thuộc loại lỗi I/O thấp tầng.
- Thẻ bị từ chối theo một quyết định hợp lệ của ngân hàng: nhiều hệ thống nên trả **kết quả nghiệp vụ** như `PaymentDecision.DECLINED` thay vì ném ngoại lệ, vì đây có thể là một kết quả dự kiến của quy trình.

**Không có quy tắc “mỗi thông điệp lỗi cần một class exception”.** Câu hỏi đúng là: *loại lỗi này có cần một hợp đồng xử lý hoặc thông tin riêng mà ngoại lệ hiện có chưa biểu đạt rõ không?*

Bài học thuộc Java Core – Exception, chủ đề **Ngoại lệ tùy chỉnh**, tập trung vào tiêu chí quyết định khi nào nên hoặc không nên tạo loại ngoại lệ riêng; bao gồm checked/unchecked, cấu trúc constructor, cause, dữ liệu lỗi, ranh giới API, thiết kế phân cấp và ví dụ Java 17+ chạy độc lập.

## 1. Ngoại lệ riêng giải quyết vấn đề gì?

Giả sử một phương thức ở tầng thanh toán ném:

~~~java
throw new RuntimeException("Payment gateway unavailable");
~~~

Đây là mảnh mã Java minh họa. Caller nhận được một `RuntimeException` chung chung. Nếu muốn xử lý riêng sự cố cổng thanh toán mà không bắt nhầm các lỗi lập trình khác, caller có thể phải kiểm tra message:

~~~java
catch (RuntimeException e) {
    if (e.getMessage().contains("gateway")) {
        // Thử lại hoặc đưa vào hàng đợi xử lý sau.
    }
}
~~~

Đây là **phản ví dụ về thiết kế**, không phải cách nên áp dụng: `getMessage()` có thể bằng `null` (gây `NullPointerException` khi gọi `contains`), nội dung thông báo có thể thay đổi theo ngôn ngữ hoặc refactor, và việc bắt rộng `RuntimeException` dễ gom cả những lỗi không liên quan.

Nếu lỗi cổng thanh toán có nghĩa riêng và caller cần nhánh xử lý riêng:

~~~java
public final class PaymentGatewayUnavailableException
        extends RuntimeException {
    public PaymentGatewayUnavailableException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là một **lớp Java độc lập**, có thể lưu trong `PaymentGatewayUnavailableException.java`. Caller có thể bắt theo kiểu cụ thể:

~~~java
catch (PaymentGatewayUnavailableException e) {
    // Chính sách xử lý lỗi cổng thanh toán.
}
~~~

Kiểu lỗi mang ngữ nghĩa ổn định hơn nội dung message. Khi áp dụng kiểu riêng, ứng dụng có thể thêm trường mã lỗi, định danh tác vụ hoặc thuộc tính cần thiết, miễn là không làm lộ thông tin nhạy cảm.

**Lợi ích chính:** chuyển lỗi thành một phần **hợp đồng có kiểu** của API, thay vì bắt người gọi suy luận từ những chuỗi văn bản không ổn định.

## 2. Năm tín hiệu nên cân nhắc tạo custom exception

### 2.1. Caller cần phân biệt lỗi này để xử lý khác

Nếu những tầng gọi cần hành động khác nhau cho các tình huống lỗi, loại exception riêng là một tín hiệu mạnh.

Ví dụ:

| Tình huống | Hành động có thể phù hợp | Có cần kiểu riêng? |
| --- | --- | --- |
| Đơn hàng không ở trạng thái được phép thanh toán | Từ chối thao tác, báo trạng thái hiện tại | Có thể hữu ích nếu là hợp đồng miền |
| Cổng thanh toán tạm thời không sẵn sàng | Retry có giới hạn, cảnh báo hoặc đẩy vào hàng chờ | Có thể hữu ích nếu caller cần nhận diện |
| `age` âm ở hàm đơn giản | Sửa tham số đầu vào | Thường dùng `IllegalArgumentException` |
| Lỗi parser số nguyên | Xử lý `NumberFormatException` trực tiếp khi thích hợp | Không nhất thiết |
| Một thao tác cho ra kết quả `DECLINED` dự kiến | Trả `PaymentDecision` | Thường không cần exception |

Nếu caller **không bao giờ xử lý khác**, loại mới có thể chỉ làm tăng số class mà không đem lại ích lợi.

### 2.2. Có ranh giới abstraction cần bảo vệ

Một module nghiệp vụ không nên bắt mọi caller biết tầng lưu trữ đang dùng tệp, JDBC hay HTTP. Thay vì phơi ra vô số lỗi hạ tầng, module có thể chuyển sang loại lỗi công khai:

~~~java
static Account loadAccount(String accountId)
        throws AccountRepositoryException {
    try {
        return readFromDatabase(accountId);
    } catch (java.sql.SQLException cause) {
        throw new AccountRepositoryException(
                "Không thể tải tài khoản", cause);
    }
}
~~~

Đây là **mã minh họa không độc lập**, giả định `Account`, `AccountRepositoryException` và `readFromDatabase` được khai báo phù hợp.

Ý nghĩa:

- Caller nhận hợp đồng `AccountRepositoryException` ổn định.
- Module có thể thay JDBC bằng API khác mà không buộc caller hiểu toàn bộ chi tiết triển khai.
- `cause` bảo toàn ngoại lệ gốc để điều tra.

Tuy nhiên, không phải tầng nào cũng cần wrapper mới. Nếu một tầng không thêm ngữ cảnh hoặc chính sách xử lý, cứ cho lỗi phù hợp lan truyền có thể rõ ràng hơn.

### 2.3. Lỗi mang dữ liệu cấu trúc có ích cho xử lý

Khi caller cần lấy mã lỗi hoặc thông tin miền một cách **an toàn theo kiểu**, một lớp tùy chỉnh có trường riêng sẽ tốt hơn parsing message.

~~~java
public final class OrderConflictException
        extends IllegalStateException {
    private final String orderId;

    public OrderConflictException(String orderId) {
        super("Order is not in a valid state");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
~~~

Đây là lớp Java độc lập nếu lưu trong `OrderConflictException.java`. Caller đọc `getOrderId()` thay vì tách mã đơn hàng từ `getMessage()`.

**Lưu ý riêng tư:** không tự động đưa toàn bộ đơn hàng, thông tin thẻ hoặc dữ liệu người dùng vào exception. Ngoại lệ có thể được ghi vào log hoặc đi qua nhiều tầng. Chỉ giữ các trường thật cần thiết và đã được phép lưu.

### 2.4. Một lỗi thuộc hợp đồng công khai của thư viện

Trong API thư viện hoặc module được nhiều nơi sử dụng, custom exception có thể thể hiện rõ **một tập hợp lỗi mà client được phép dựa vào**.

Ví dụ API:

~~~java
public Receipt issueReceipt(String orderId)
        throws ReceiptGenerationException
~~~

Đây là **chữ ký minh họa**, không phải một chương trình độc lập. Nó cho biết caller cần tính tới một nhóm lỗi phát hành hóa đơn. Nếu điều đó có ý nghĩa với client, `ReceiptGenerationException` có thể là một phần hợp đồng versioning.

Khi đã công khai một kiểu exception, đừng đổi hoặc xóa tùy tiện: mã của bên gọi có thể `catch` kiểu ấy, cấu hình retry dựa theo nó, hoặc gắn trạng thái HTTP dựa trên loại exception.

### 2.5. Muốn phân biệt trạng thái bất thường với lỗi lập trình

Có những lỗi nên được tách rõ khỏi `NullPointerException`, `IndexOutOfBoundsException` hay `IllegalArgumentException` thường gặp trong code vì nguyên nhân và cách khắc phục khác.

Ví dụ `LeaseExpiredException` trong module quản lý quyền truy cập tạm thời có thể có ý nghĩa hơn `IllegalStateException` chung chung, nếu caller được thiết kế để yêu cầu cấp lại lease.

Dù vậy, hãy cẩn thận: **“có tên nghiệp vụ” chưa đủ**. Một hệ thống nhỏ có thể xử lý tốt với ngoại lệ chuẩn cộng message rõ ràng. Chỉ tạo kiểu khi có lợi ích cụ thể về API, phân nhánh hoặc khả năng diễn đạt.

## 3. Trường hợp nên dùng exception chuẩn thay vì tự định nghĩa

JDK đã có một tập hợp ngoại lệ giàu ý nghĩa:

| Ngoại lệ có sẵn | Tình huống thường phù hợp |
| --- | --- |
| `IllegalArgumentException` | Giá trị đối số vi phạm tiền điều kiện |
| `NullPointerException` | Tham chiếu null không được chấp nhận |
| `IllegalStateException` | Trạng thái hiện tại không cho phép thao tác |
| `UnsupportedOperationException` | Thao tác không được triển khai/hỗ trợ |
| `NoSuchElementException` | Không có phần tử theo hợp đồng truy cập |
| `NumberFormatException` | Chuỗi không thể phân tích thành số hợp lệ |
| `IOException` | Thất bại I/O |
| `SQLException` | Lỗi theo API JDBC |

Nếu lỗi là một **vi phạm tiền điều kiện kỹ thuật thông thường**, `IllegalArgumentException` hoặc `IllegalStateException` kèm message rõ nghĩa thường đủ.

Ví dụ:

~~~java
static int positive(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException(
                "value must be positive");
    }
    return value;
}
~~~

Đây là phương thức Java hợp lệ khi đặt trong một lớp. Không cần `NotPositiveValueException` riêng nếu caller chỉ cần sửa đối số và không phân biệt nhiều lớp lỗi miền.

### Đừng tạo custom exception chỉ để thay chữ trong message

So sánh:

~~~java
throw new IllegalArgumentException("quantity must be > 0");
~~~

và:

~~~java
throw new InvalidQuantityException("quantity must be > 0");
~~~

Đoạn thứ hai giả định một lớp tùy chỉnh tồn tại. Nếu caller vẫn xử lý cả hai bằng đúng một cách và không cần thêm dữ liệu, lớp mới có thể không mang lại giá trị.

Ngoại lệ chuẩn giúp giảm API, giảm code cần duy trì và tận dụng kiến thức chung của lập trình viên Java.

## 4. Exception không phải cách biểu diễn mọi kết quả nghiệp vụ không thuận lợi

Một kết quả **không được chấp nhận** không phải lúc nào cũng là **thất bại bất thường của chương trình**.

Ví dụ dịch vụ thanh toán trả lời thẻ không được chấp nhận. Nếu đó là một kết quả thường thấy của quy trình, kiểu kết quả tường minh có thể dễ dùng hơn:

~~~java
enum PaymentStatus {
    APPROVED, DECLINED
}

record PaymentDecision(PaymentStatus status, String reason) {}
~~~

Đây là hai khai báo Java 17+ có thể nằm trong một lớp. Caller phân nhánh theo `status` mà không cần một cơ chế exception cho kết quả dự kiến.

| Tiêu chí | Trả kết quả tường minh | Ném exception |
| --- | --- | --- |
| Kết quả xảy ra thường xuyên và là một lựa chọn hợp lệ | Thường rõ ràng | Có thể gây cảm giác lỗi bất thường |
| Caller luôn phải xét nhiều nhánh nghiệp vụ | `enum`/`record`/sealed result phù hợp | Dễ khiến nhánh bị bỏ sót nếu unchecked |
| Không thể hoàn tất thao tác do hạ tầng hoặc bất biến sai | Có thể dùng kết quả lỗi theo API | Exception thường phù hợp |
| Cần lan truyền qua nhiều tầng mà không xử lý ngay | Cần trả kết quả qua từng tầng | Exception hỗ trợ lan truyền |
| Muốn truyền nhiều thông tin người dùng phải thấy | Kết quả thường thuận tiện | Cần tránh rò rỉ stack trace nội bộ |

Không có một quy tắc tuyệt đối cho mọi domain. Một API có thể chọn exception cho việc không tìm thấy đối tượng; API khác chọn `Optional.empty()` hoặc một kết quả `NOT_FOUND`. Quan trọng là tính **nhất quán của hợp đồng** và sự phù hợp với hành vi caller.

### Không dùng exception làm đường rẽ thông thường trong vòng lặp

~~~java
try {
    return Integer.parseInt(text);
} catch (NumberFormatException e) {
    return 0;
}
~~~

Đây là mảnh mã minh họa. Nếu yêu cầu chỉ là “thử parse một lần”, cách này có thể hợp lý. Nhưng nếu dữ liệu không hợp lệ xuất hiện liên tục như một trạng thái dự kiến của giao thức, hãy cân nhắc API parse trả trạng thái rõ ràng hoặc kiểm tra định dạng phù hợp thay vì lạm dụng việc tạo exception để điều khiển vòng lặp nóng.

Đừng tối ưu hóa bằng phỏng đoán: quyết định còn phụ thuộc tính đơn giản, độ đúng và phép đo hiệu năng thực tế.

## 5. Chọn checked hay unchecked cho custom exception?

Trước khi tạo một lớp lỗi riêng, cần chọn **hợp đồng mà caller sẽ nhìn thấy**:

| Hướng | Nghĩa vụ lúc biên dịch | Khi cân nhắc |
| --- | --- | --- |
| `extends Exception` (không qua `RuntimeException`) | Caller phải `catch` hoặc khai báo `throws` nếu có thể để checked exception thoát ra | API cố ý yêu cầu người gọi xét một điều kiện thất bại đã công bố |
| `extends RuntimeException` | Caller không bắt buộc `catch` hoặc `throws` | Vi phạm trạng thái/tiền điều kiện, hoặc hợp đồng chủ động dành việc xử lý lỗi cho boundary |

**Có thể phục hồi** không đồng nghĩa **phải checked**: một `NumberFormatException` unchecked vẫn có thể được UI xử lý; một `IOException` checked có thể không phục hồi được. Cần xét tác động lên chữ ký API, việc override và khách hàng sử dụng thư viện. Chọn loại theo trách nhiệm xử lý, không theo cảm giác lỗi “nặng”.

## 6. Định nghĩa constructor: message và cause

Custom exception cần giữ **mô tả dễ hiểu** và **nguyên nhân gốc** khi bọc lỗi từ tầng khác. Tối thiểu, công bố những overload mà caller thực sự cần:

~~~java
public final class RepositoryAccessException extends Exception {
    public RepositoryAccessException(String message) {
        super(message);
    }

    public RepositoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là lớp độc lập nếu lưu trong `RepositoryAccessException.java`. Khi bắt `SQLException` rồi chuyển thành `RepositoryAccessException`, hãy dùng constructor `(message, ex)` để `getCause()` giữ đúng **Throwable gốc**. Chỉ nối `ex.getMessage()` vào một câu mới sẽ làm mất chuỗi cause và stack trace của ex.

Java không tự kế thừa constructor từ superclass, vì vậy muốn cho người gọi truyền `cause`, subclass phải cung cấp overload thích hợp. Không cần thêm cả bốn constructor mặc định chỉ để tuân một mẫu máy móc.

## 7. Thêm trường cấu trúc khi có nhu cầu thực tế

Nếu caller cần xử lý theo mã lỗi hoặc truy vấn ID, hãy đưa chúng vào **trường có kiểu**, không yêu cầu caller parse `getMessage()`. Ví dụ:

~~~java
enum OrderFailureCode { ALREADY_PAID, STOCK_INSUFFICIENT }

final class OrderOperationException extends RuntimeException {
    private final OrderFailureCode code;
    private final String orderId;

    OrderOperationException(OrderFailureCode code, String orderId) {
        super("order operation rejected");
        this.code = java.util.Objects.requireNonNull(code);
        this.orderId = java.util.Objects.requireNonNull(orderId);
    }

    OrderFailureCode code() { return code; }
    String orderId() { return orderId; }
}
~~~

Ví dụ khai báo Java 17; các loại có thể đặt ở tệp thích hợp cùng package. Chỉ thêm field nếu nó phục vụ quyết định thực tế của caller hoặc điều tra. Tránh giữ đồng thời ID ở nhiều field, lưu toàn bộ entity mutable hoặc chứa bí mật vào exception. Mã enum và ID có thể được dùng để phân loại/log an toàn theo chính sách; message vẫn là câu chữ diễn giải.

## 8. Ví dụ Java 17+ độc lập: khi nào dùng loại riêng và khi nào không

Chương trình dưới đây cố ý dùng **ba cách biểu diễn thất bại khác nhau**:

1. `IllegalArgumentException` có sẵn cho `orderId` sai.
2. `InvalidOrderStateException` (unchecked, riêng) cho lỗi bất biến trạng thái có ý nghĩa miền và trường `orderId`.
3. `GatewayUnavailableException` (checked, riêng) cho thất bại hạ tầng cần giữ `IOException` làm cause.
4. `PaymentDecision` dưới dạng `record` cho việc thẻ bị từ chối — **một kết quả nghiệp vụ hợp lệ**, không ném exception.

Tạo tệp **`CustomExceptionDecisionDemo.java`**:

~~~java
import java.io.IOException;

public class CustomExceptionDecisionDemo {

    record PaymentDecision(boolean approved, String reason) {}

    static final class InvalidOrderStateException
            extends IllegalStateException {
        private final String orderId;

        InvalidOrderStateException(
                String orderId, String message) {
            super(message);
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }
    }

    static final class GatewayUnavailableException
            extends Exception {
        private final String code;

        GatewayUnavailableException(
                String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    public static void main(String[] args) {
        show("O-1", true, true, true);
        show("O-2", true, false, true);
        show("O-3", false, true, true);
        show("O-4", true, true, false);

        try {
            charge("", true, true, true);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "invalid argument: " + e.getMessage());
        } catch (GatewayUnavailableException e) {
            System.out.println("unexpected gateway error");
        }
    }

    static void show(
            String orderId,
            boolean ready,
            boolean approved,
            boolean gatewayOnline) {
        try {
            PaymentDecision decision =
                    charge(orderId, ready, approved, gatewayOnline);

            String status = decision.approved()
                    ? "APPROVED"
                    : "DECLINED";
            System.out.println(
                    orderId + " decision=" + status
                            + " reason=" + decision.reason());
        } catch (InvalidOrderStateException e) {
            System.out.println(
                    orderId + " state-error=" + e.getMessage()
                            + " order=" + e.orderId());
        } catch (GatewayUnavailableException e) {
            System.out.println(
                    orderId + " gateway-error=" + e.code());
            System.out.println(
                    "cause=" + e.getCause().getClass().getSimpleName()
                            + ": " + e.getCause().getMessage());
        }
    }

    static PaymentDecision charge(
            String orderId,
            boolean ready,
            boolean approved,
            boolean gatewayOnline)
            throws GatewayUnavailableException {

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "orderId must not be blank");
        }

        if (!ready) {
            throw new InvalidOrderStateException(
                    orderId, "order is not ready");
        }

        try {
            callGateway(gatewayOnline);
        } catch (IOException cause) {
            throw new GatewayUnavailableException(
                    "GATEWAY_OFFLINE",
                    "payment gateway unavailable",
                    cause);
        }

        if (!approved) {
            return new PaymentDecision(false, "card declined");
        }

        return new PaymentDecision(true, "accepted");
    }

    static void callGateway(boolean online) throws IOException {
        if (!online) {
            throw new IOException("connection refused");
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac CustomExceptionDecisionDemo.java
java CustomExceptionDecisionDemo
~~~

**Stdout chính xác:**

~~~text
O-1 decision=APPROVED reason=accepted
O-2 decision=DECLINED reason=card declined
O-3 state-error=order is not ready order=O-3
O-4 gateway-error=GATEWAY_OFFLINE
cause=IOException: connection refused
invalid argument: orderId must not be blank
~~~

### Lần theo luồng thành công

`show("O-1", true, true, true)` gọi `charge` với tham số hợp lệ, đơn hàng sẵn sàng và gateway trực tuyến. `callGateway` không ném lỗi; `charge` trả `PaymentDecision(true, "accepted")`. Caller in `APPROVED`.

### Từ chối thẻ: không tạo exception tùy chỉnh

`show("O-2", true, false, true)` vẫn gọi gateway thành công. Quyết định nghiệp vụ là `DECLINED`, nên `charge` trả `PaymentDecision(false, "card declined")`. Đây là ví dụ quan trọng rằng **không phải mọi kết quả không thành công đều cần ngoại lệ**.

Nếu hệ thống có quy định khác, từ chối thanh toán có thể được biểu diễn bằng một kiểu kết quả đa dạng hơn; mục tiêu là người gọi thấy rõ đây là một trạng thái **được dự liệu**.

### Vi phạm bất biến: custom unchecked exception

`show("O-3", false, true, true)` gặp trạng thái đơn hàng chưa sẵn sàng. `charge` ném `InvalidOrderStateException` chứa mã đơn hàng riêng. `show` bắt đúng loại lỗi này và đọc `orderId()` mà không phân tích message.

`InvalidOrderStateException extends IllegalStateException` là một lựa chọn hợp lý nếu lỗi này **thực sự là tình huống trạng thái không cho phép thao tác** và nhiều tầng xử lý cần nhận diện nó. Trong ứng dụng quá nhỏ, `IllegalStateException` có sẵn cũng có thể đủ.

### Lỗi hạ tầng: custom checked exception và cause

`show("O-4", true, true, false)` khiến `callGateway` ném `IOException("connection refused")`. `charge` bắt lỗi rồi bọc thành `GatewayUnavailableException` chứa:

- `code = "GATEWAY_OFFLINE"` làm mã phân loại ổn định cho caller;
- `message = "payment gateway unavailable"` mô tả hoạt động tầng thanh toán;
- `cause` là chính `IOException` gốc.

Vì `GatewayUnavailableException extends Exception`, `charge` khai báo `throws GatewayUnavailableException`. Caller buộc xử lý hoặc chuyển tiếp loại checked exception đó theo hợp đồng.

### Tham số trống: ngoại lệ chuẩn là đủ

`charge("", ...)` ném `IllegalArgumentException` vì `orderId` vi phạm tiền điều kiện; ví dụ không tạo `BlankOrderIdException` riêng. Đây là lựa chọn đơn giản, rõ ràng và phù hợp khi caller chỉ cần sửa dữ liệu đầu vào.

**Lưu ý:** Chương trình mô phỏng logic; `boolean approved` và `boolean gatewayOnline` là các đầu vào giả lập, không phải giao thức thanh toán thật. Không dùng ví dụ này để suy ra cách triển khai bảo mật, chống trùng giao dịch hoặc chính sách retry của một hệ thống thanh toán thực tế.

## 9. Thiết kế phân cấp exception riêng: đơn giản trước

Một base class như `BillingException extends Exception` có ích nếu caller thực sự muốn `catch (BillingException)` cho cả một nhóm lỗi. Khi caller cần hai hành động khác nhau, có thể tạo subtype như `PaymentDeclinedException` và `PaymentProviderUnavailableException`, nếu chúng phù hợp với cùng họ.

Nhưng **không tạo cây sâu chỉ để mô phỏng sơ đồ hệ thống**. Nếu mọi lỗi đều được xử lý giống nhau, một exception có mã lý do typed có thể phù hợp hơn hàng chục subtype. Caller nên phân nhánh theo **hành động có thể thực hiện**, không theo tên driver, HTTP client hay database nội bộ. Chi tiết hạ tầng nên giữ qua `cause` khi chuyển abstraction.

## 10. Thiết kế checked exception và khả năng tương thích API

Một custom checked exception trở thành phần có ảnh hưởng đáng kể tới mã gọi:

~~~java
public Receipt generate(String orderId)
        throws ReceiptGenerationException
~~~

Đây là một chữ ký API minh họa. Bên gọi phải chọn `try/catch` hoặc khai báo `throws`. Nếu thêm một checked exception mới vào chữ ký công khai, source code của client có thể cần thay đổi.

Những cân nhắc:

- Checked exception có giúp caller **thật sự** xử lý hoặc phục hồi không?
- Nó mô tả một loại lỗi có thể dự kiến từ tác vụ hay chỉ lỗi lập trình?
- Có khả năng đổi hạ tầng bên dưới nhưng giữ hợp đồng không?
- Việc bắt/khai báo lặp qua nhiều tầng có làm API phức tạp không?
- Một kết quả kiểu `Result`/`Optional` có rõ nghĩa hơn cho trạng thái thiếu dữ liệu dự kiến không?

Khi đã tạo API công khai, tài liệu hóa **khi nào ném ngoại lệ, caller có thể làm gì và ngoại lệ có giữ cause hay không**. Không đơn thuần công bố tên kiểu rồi để người dùng đoán.

### `throws` không có nghĩa lần nào cũng thất bại

Phương thức:

~~~java
static String readConfig(boolean available)
        throws java.io.IOException {
    if (!available) {
        throw new java.io.IOException("unavailable");
    }
    return "ready";
}
~~~

Đây là phương thức Java hợp lệ trong lớp. `throws IOException` là khai báo khả năng ném checked exception theo hợp đồng. Với `readConfig(true)`, kết quả bình thường là `"ready"`.

Điều tương tự áp dụng khi custom checked exception xuất hiện ở `throws`: khai báo không tự làm ngoại lệ xảy ra; chỉ những đường thực thi thực sự ném nó mới kết thúc bất thường.

## 11. Khi dùng unchecked exception riêng, caller có thể bỏ qua không?

Với custom unchecked exception, compiler không bắt buộc caller viết `catch` hoặc `throws`. Nhưng điều đó không biến lỗi thành sự kiện vô hại.

~~~java
static void verifyState(boolean ready) {
    if (!ready) {
        throw new InvalidOrderStateException(
                "O-7", "order is not ready");
    }
}
~~~

Đây là phương thức minh họa sử dụng lớp lồng trong ví dụ chạy được; nếu viết ở ngoài cần truy cập đúng lớp. Khi `ready = false`, ngoại lệ vẫn ném ra và có thể làm request hoặc tác vụ thất bại nếu không có handler.

Cần có một tầng biên phù hợp để xử lý, chẳng hạn chuyển lỗi miền thành HTTP 409 theo chính sách API, báo lỗi lệnh CLI, rollback transaction, hoặc ghi log có kiểm soát.

**Không bắt `RuntimeException` hoặc `Exception` rộng khắp chỉ để ứng dụng “không chết”.** Làm thế có thể che lỗi lập trình và tiếp tục xử lý ở trạng thái không còn đáng tin cậy. Chỉ bắt ở những ranh giới có trách nhiệm cụ thể.

## 12. Đừng phân loại retry chỉ từ tên ngoại lệ

Một loại lỗi riêng có thể hỗ trợ phân loại retry, nhưng “lỗi mạng” không tự động đồng nghĩa **được retry an toàn**.

Ví dụ một request thanh toán có thể đã được phía nhà cung cấp xử lý, sau đó kết nối đứt trước khi nhận phản hồi. Thử lại bừa bãi có thể tạo giao dịch trùng.

Khi thiết kế ngoại lệ liên quan đến hạ tầng, cần phân biệt:

- Có thể xác định thao tác **chưa** được thực thi ở phía dịch vụ hay không?
- Tác vụ có **idempotent** không?
- Có idempotency key hoặc cơ chế deduplication không?
- Có giới hạn số lần thử, backoff và timeout hợp lý không?
- Có mã lỗi cấu trúc hoặc metadata nào chỉ ra retryable theo hợp đồng không?

Một tên `GatewayUnavailableException` chỉ nói **loại thất bại**, không đủ để xác nhận một thao tác viết dữ liệu có thể gọi lại an toàn. Nếu chính sách retry là quan trọng, hãy thiết kế nó độc lập hoặc thông qua dữ liệu được hợp đồng xác nhận.

## 13. Khi nào không nên phân cấp theo từng mã lỗi?

Một hệ thống có hàng chục mã thất bại không nhất thiết cần hàng chục class `Exception`. Có thể dùng một kiểu exception cho một nhóm ngữ nghĩa, kèm enum mã lỗi:

~~~java
enum ImportFailureCode {
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    SOURCE_UNAVAILABLE
}

final class ImportException extends Exception {
    private final ImportFailureCode code;

    ImportException(
            ImportFailureCode code,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    ImportFailureCode code() {
        return code;
    }
}
~~~

Đây là các khai báo Java 17+ hợp lệ trong cùng package hoặc lớp bao thích hợp.

Giải pháp này có thể phù hợp nếu caller xử lý chung nhóm lỗi nhập dữ liệu, nhưng cần mã lỗi chi tiết để hiển thị hoặc ghi nhận. Ngược lại, nếu một vài mã có cách khôi phục rất khác biệt và được bắt trực tiếp ở nhiều nơi, subtype riêng có thể rõ hơn.

### Trade-off: kiểu riêng hay enum mã lỗi?

| Cách tiếp cận | Ưu điểm | Đánh đổi |
| --- | --- | --- |
| Một subtype riêng mỗi tình huống | `catch` theo kiểu, rõ ngữ nghĩa, IDE dễ khám phá | Nhiều class, tăng API surface |
| Một nhóm exception + mã lỗi enum | Ít class, dữ liệu mã lỗi ổn định | Caller có thể cần `switch` theo mã |
| Exception chuẩn + message | Đơn giản, ít boilerplate | Ít ngữ nghĩa miền cho machine |
| Kết quả nghiệp vụ có trạng thái | Rõ nhánh dự kiến và có kiểu dữ liệu | Phải truyền kết quả qua các tầng gọi |

Không có lựa chọn tối ưu chung cho mọi dự án. Bắt đầu bằng cấu trúc đơn giản, tăng số kiểu khi hợp đồng và nhu cầu thật xuất hiện.

## 14. Cause và suppressed exceptions trong custom exception

Một custom exception không nên làm mất nguyên nhân gốc khi bọc lỗi:

~~~java
catch (java.io.IOException cause) {
    throw new GatewayUnavailableException(
            "GATEWAY_OFFLINE",
            "gateway unavailable",
            cause);
}
~~~

Đây là đoạn minh họa trong lớp có thể truy cập `GatewayUnavailableException`. Constructor của lớp riêng gọi `super(message, cause)`, nhờ đó `getCause()` trả `IOException` gốc.

### Cause khác suppressed

- `getCause()`: một ngoại lệ làm **nguyên nhân trực tiếp** của lỗi ngoài.
- `getSuppressed()`: các ngoại lệ phụ không nên thay thế lỗi chính, thường gặp khi đóng tài nguyên trong try-with-resources.

Ví dụ:

~~~java
Exception main = new Exception(
        "work failed",
        new java.io.IOException("read failed"));

main.addSuppressed(new java.io.IOException("close failed"));
~~~

Đây là mảnh mã minh họa. Cause là `"read failed"`, suppressed exception là `"close failed"`. Không phải cả hai đều là những mắt xích `getCause()` liên tiếp.

### Chú ý constructor và lớp kế thừa

Nếu custom exception không định nghĩa overload có cause, bạn có thể cân nhắc `initCause(...)` khi lớp cho phép và cause chưa được khởi tạo. Nhưng với mã mới, thường nên **tạo constructor nhận cause ngay từ đầu**. Constructor không được kế thừa tự động.

## 15. Message, mã lỗi và dữ liệu nhạy cảm

Exception thường xuất hiện trong log, tracing và báo lỗi vận hành. Vì vậy, các trường dữ liệu của custom exception phải có chính sách rõ.

Nên:

- Đặt message mô tả **thao tác và điều kiện thất bại**, không chỉ `"error occurred"`.
- Dùng mã lỗi ổn định cho nhánh xử lý; tránh parse message.
- Giữ cause khi chuyển abstraction để phục vụ điều tra.
- Chỉ giữ metadata cần thiết (ví dụ mã tác vụ nội bộ không nhạy cảm).
- Phân biệt message kỹ thuật trong log với thông điệp cho người dùng.

Tránh:

- Lưu mật khẩu, access token, chuỗi kết nối nguyên vẹn hoặc dữ liệu thẻ.
- Gắn cả request/body chứa dữ liệu nhạy cảm vào exception.
- Dùng `getMessage()` như giao thức máy-máy ổn định.
- Trả thẳng `printStackTrace()` hoặc toàn bộ chain ra API công khai.

Một kiểu ngoại lệ tốt còn phải phù hợp với chiến lược observability và bảo mật của ứng dụng.

## 16. Lớp ngoại lệ có cần `serialVersionUID`?

`Throwable` triển khai `Serializable`, vì vậy một custom exception có thể nhận cảnh báo IDE liên quan tới `serialVersionUID`. **Không phải mọi exception đều cần cam kết định dạng serialization.** Nếu hệ thống thực sự lưu hoặc trao đổi Java-serialized Throwable giữa các phiên bản, hãy xác định UID, tương thích class/field và kiểm thử dữ liệu cũ đọc bằng phiên bản mới; chỉ thêm `serialVersionUID = 1L` không bảo đảm tương thích.

Với HTTP/message queue/public API, thường nên công bố **DTO lỗi có schema, mã lỗi và context được kiểm soát** thay vì serialize nguyên đồ thị Throwable, vốn có thể mang cause, suppressed và thông tin nhạy cảm.

## 17. Có nên đưa mọi lỗi dưới một `BaseApplicationException`?

Một lớp cha chung có thể hữu ích khi framework hoặc ứng dụng cần handler biên:

~~~java
public class ApplicationException extends RuntimeException {
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Nhưng nếu mọi lỗi đều kế thừa nó, caller bắt `ApplicationException` sẽ nhận một tập hợp lỗi rất rộng, không chắc có một cách phục hồi chung. Một phân cấp lớn cũng dễ trở thành nơi chứa mọi mã lỗi không liên quan.

**Một base exception chỉ có ích nếu nó thực sự biểu diễn một hợp đồng chung.** Ví dụ một module billing có nhóm lỗi có cùng cơ chế ghi nhận hoặc ánh xạ lỗi ở tầng biên. Không cần tạo base exception cho từng dự án mới chỉ để tuân theo một khuôn OOP.

Nếu các loại ngoại lệ ở các module hoàn toàn khác nhau, dùng các loại JDK có sẵn hoặc các nhóm nhỏ độc lập có thể dễ bảo trì hơn.

## 18. Kiểm thử custom exception

Một custom exception nên được kiểm thử ở **hai mức**.

### Mức lớp: constructor và metadata

Kiểm tra:

- `getMessage()` nhận đúng thông điệp.
- `getCause()` giữ **chính đối tượng cause** (dùng `==` hoặc `assertSame` trong framework test).
- Getter metadata như `code()` hoặc `orderId()` trả đúng giá trị.
- Kiểu checked/unchecked phù hợp quan hệ kế thừa.
- Có hay không khả năng làm lộ dữ liệu không mong muốn qua message.

Đoạn kiểm tra đơn giản không cần framework:

~~~java
java.io.IOException root =
        new java.io.IOException("unavailable");

GatewayUnavailableException e =
        new GatewayUnavailableException(
                "GATEWAY_OFFLINE", "gateway down", root);

if (e.getCause() != root) {
    throw new AssertionError("cause was lost");
}
if (!"GATEWAY_OFFLINE".equals(e.code())) {
    throw new AssertionError("wrong code");
}
~~~

Đây là mảnh mã kiểm tra có thể đặt trong lớp chứa `GatewayUnavailableException` của ví dụ đầy đủ.

### Mức hành vi: đúng tình huống thì ném đúng loại

Ví dụ:

- `charge` với `orderId` rỗng phải ném `IllegalArgumentException`.
- `charge` với trạng thái không sẵn sàng phải ném `InvalidOrderStateException`.
- `charge` với gateway offline phải ném `GatewayUnavailableException` và giữ `IOException` làm cause.
- `charge` với thẻ bị từ chối hợp lệ phải **trả kết quả `DECLINED`**, không ném exception.

Kiểm thử theo kiểu và dữ liệu cấu trúc tốt hơn kiểm thử phụ thuộc toàn bộ stack trace, số dòng mã nguồn hoặc format `printStackTrace()`.

## 19. Những sai lầm phổ biến

### Sai lầm 1 — Một loại exception cho mỗi câu message

Tạo nhiều class chỉ khác lời thông báo khiến API phình to mà không có lợi ích xử lý thực tế.

### Sai lầm 2 — Bắt `RuntimeException` rồi so sánh `getMessage()`

Message không ổn định và có thể null. Dùng kiểu lỗi cụ thể hoặc mã lỗi có cấu trúc nếu caller thật sự cần phân nhánh.

### Sai lầm 3 — Tạo custom exception cho mọi đầu vào sai

Các lỗi tiền điều kiện đơn giản thường đã có `IllegalArgumentException` hoặc `IllegalStateException`. Chỉ tạo loại riêng khi có hợp đồng miền rõ ràng.

### Sai lầm 4 — Dùng exception cho mọi kết quả từ chối

Nếu từ chối là một kết quả nghiệp vụ dự kiến, kiểu kết quả tường minh có thể phù hợp hơn.

### Sai lầm 5 — Chọn checked vì lỗi “nghiêm trọng” và unchecked vì lỗi “nhẹ”

Phân loại checked/unchecked liên quan hợp đồng compile-time, không phải thang đo độ nghiêm trọng.

### Sai lầm 6 — Quên truyền cause trong constructor

`super(message)` không tự bảo toàn cause. Dùng `super(message, cause)` khi bọc ngoại lệ tầng thấp.

### Sai lầm 7 — Quên khai báo `throws` cho custom checked

Lớp kế thừa `Exception` (không phải `RuntimeException`) thường là checked. Phương thức ném nó phải bắt hoặc khai báo; caller cũng vậy theo quy tắc Java.

### Sai lầm 8 — Tạo cây kế thừa sâu không có người dùng

Phân cấp quá chi tiết tăng phụ thuộc và tạo gánh nặng bảo trì. Chọn số lớp đúng nhu cầu phân nhánh thực sự.

### Sai lầm 9 — Làm lộ hạ tầng trong hợp đồng nghiệp vụ

Trả trực tiếp `SQLException` hoặc `IOException` ở mọi API cấp cao có thể khiến caller phụ thuộc vào công nghệ hiện tại; cân nhắc bọc ở ranh giới module khi có ích.

### Sai lầm 10 — Bọc mọi lỗi ở mọi tầng

Ngoại lệ có chuỗi wrapper dài nhưng không thêm thông tin khiến log khó đọc. Chỉ bọc khi cần đổi abstraction hoặc bổ sung ngữ cảnh.

### Sai lầm 11 — Giả định tên exception quyết định retry an toàn

Lỗi gateway có thể xuất hiện sau khi một yêu cầu ghi đã được thực thi. Retry còn cần idempotency, timeout, backoff và hợp đồng nhà cung cấp.

### Sai lầm 12 — Lạm dụng `catch (Exception)` hoặc `catch (Throwable)`

Bắt quá rộng có thể che lỗi lập trình hoặc các `Error` nghiêm trọng. Chỉ bắt ở tầng có trách nhiệm và chiến lược xử lý thích hợp.

### Sai lầm 13 — Dùng message như mã lỗi ổn định

Đổi cách diễn đạt message có thể làm sai logic. Tách code hoặc subtype khỏi message khi cần xử lý tự động.

### Sai lầm 14 — Thêm trường dữ liệu nhạy cảm vào exception

Lỗi có thể được ghi log, in stack trace hoặc chuyển qua hệ thống giám sát; tránh để lộ thông tin bí mật.

### Sai lầm 15 — Tự bật/tắt stack trace để “tối ưu” mà không đo đạc

Một số constructor nâng cao có thể hạn chế thu thập stack trace hoặc suppressed exceptions, nhưng đó là đánh đổi khả năng chẩn đoán. Đừng biến nó thành mặc định cho custom exception thông thường.

### Sai lầm 16 — Nghĩ mọi constructor của `Exception` tự truyền xuống lớp con

Constructor **không được kế thừa**. Muốn public overload nào thì custom exception phải tự định nghĩa overload đó.

## 20. Checklist trước khi tạo một loại ngoại lệ riêng

Hãy tự trả lời những câu hỏi sau:

1. **Đây thực sự là trạng thái bất thường hay chỉ kết quả dự kiến?** Có nên trả enum/record/result thay vì ném?
2. **JDK đã có loại ngoại lệ diễn đạt đúng chưa?** `IllegalArgumentException`, `IllegalStateException`, `IOException`... có đủ không?
3. **Caller cần bắt loại này riêng không?** Nếu có, họ sẽ hành động khác thế nào?
4. **Có ranh giới abstraction cần bảo vệ không?** Loại riêng có giúp module không phụ thuộc hạ tầng không?
5. **Checked hay unchecked?** Quy tắc biên dịch có phù hợp cách client cần phục hồi?
6. **Có cần metadata có cấu trúc không?** Mã lỗi/trạng thái/ID an toàn có thật sự được dùng không?
7. **Cause được giữ lại chưa?** Khi bọc lỗi, constructor có `super(message, cause)` không?
8. **Có đơn giản hơn không?** Một class kèm enum code, loại chuẩn hoặc kết quả tường minh có rõ hơn nhiều subclass không?
9. **Có chính sách log/API bảo vệ thông tin không?** Message và cause có thể chứa dữ liệu nhạy cảm.
10. **Có test theo loại, message cần thiết, cause và metadata không?** Thay vì kiểm tra chuỗi stack trace dễ thay đổi.

Nếu các câu trả lời cho thấy loại lỗi mới sẽ **thay đổi cách caller hiểu và xử lý thất bại**, custom exception có thể là một lựa chọn tốt. Nếu không, thường nên bắt đầu với loại chuẩn hoặc dữ liệu kết quả.

## 21. Bài tập tự kiểm tra

1. Khi nào một `IllegalArgumentException` có sẵn đủ tốt hơn tạo `InvalidAgeException`?
2. Exception riêng có lợi ích gì hơn so sánh chuỗi `getMessage()`?
3. Lỗi “thẻ bị từ chối” có bắt buộc ném exception không?
4. Vì sao lỗi trạng thái đơn hàng có thể cần một subtype của `IllegalStateException`?
5. Khi nào nên bọc `IOException` bằng exception ở tầng nghiệp vụ?
6. `getCause()` giúp gì khi tạo wrapper?
7. Nếu chỉ dùng `super(message)`, thông tin cause có tự được giữ không?
8. Nếu custom exception kế thừa `Exception` nhưng không phải `RuntimeException` thì nghĩa vụ checked thế nào?
9. Một exception kế thừa `RuntimeException` có phải vì thế mà không thể làm request thất bại không?
10. Custom exception có bắt buộc phải có constructor không tham số không?
11. Nếu caller xử lý 12 mã lỗi giống nhau, có nhất thiết tạo 12 class không?
12. Khi nào một enum mã lỗi trong exception hữu ích?
13. Mã lỗi và message khác nhau ra sao về độ ổn định?
14. Có nên kế thừa `Throwable` trực tiếp cho ngoại lệ nghiệp vụ thông thường không?
15. Vì sao không nên dùng `Error` làm lớp cha của lỗi nghiệp vụ thông thường?
16. Custom exception có thể giữ trường `orderId` không? Nên giới hạn dữ liệu đó thế nào?
17. Vì sao tên `GatewayUnavailableException` chưa đủ để quyết định retry một lệnh thanh toán?
18. Tạo subtype riêng cho từng tầng ứng dụng có luôn làm chain dễ hiểu hơn không?
19. Khi thêm custom checked exception mới vào public API, client có thể phải thay đổi gì?
20. `serialVersionUID` có bắt buộc để `throw/catch` hoạt động không?
21. Cách kiểm thử constructor có bảo toàn cause chính xác là gì?
22. Khi nào caller nên xử lý kết quả `DECLINED` như một giá trị thay vì `catch`?
23. `catch (Throwable)` có phải lựa chọn mặc định để xử lý mọi custom exception không?
24. Exception riêng có thể giúp che chi tiết JDBC khi module chuyển sang hạ tầng khác như thế nào?
25. Nêu ba câu hỏi quan trọng nhất trước khi tạo một lớp exception mới.

### Đáp án gợi ý

1. Khi chỉ là vi phạm đối số thông thường, không có nhánh xử lý miền riêng.
2. Caller phân nhánh theo kiểu ổn định, dễ đọc và an toàn hơn message.
3. Không; có thể biểu diễn bằng kết quả nghiệp vụ tường minh.
4. Khi đó là một bất biến miền cần nhận diện và xử lý riêng.
5. Khi ranh giới API cần đổi abstraction hoặc bổ sung ngữ cảnh có ích.
6. Giữ đối tượng nguyên nhân gốc và thông tin chẩn đoán của nó.
7. Không; nên gọi `super(message, cause)` nếu muốn giữ cause.
8. Phương thức/caller phải bắt hoặc khai báo theo quy tắc checked exceptions.
9. Không; unchecked vẫn được ném và có thể lan truyền làm thao tác thất bại.
10. Không; chỉ tạo overload cần cho hợp đồng.
11. Không; một nhóm exception kèm enum code có thể rõ hơn.
12. Khi máy phải phân loại lỗi mà không parsing message.
13. Code dành cho hợp đồng xử lý; message dành cho người đọc và có thể thay đổi.
14. Thường không; ưu tiên `Exception`/`RuntimeException` hoặc subtype có nghĩa.
15. `Error` dành cho các lỗi nghiêm trọng của môi trường/VM, không phải hợp đồng thất bại nghiệp vụ thường ngày.
16. Có, nếu cần và an toàn; tránh dữ liệu cá nhân/bí mật quá chi tiết.
17. Vì một lần gọi có thể đã có side effect; retry còn cần idempotency.
18. Không; chain dài không thêm ngữ cảnh có thể khó điều tra hơn.
19. Có thể cần thêm `catch` hoặc `throws` và chính sách xử lý tương ứng.
20. Không; chỉ cân nhắc serialization nếu dự án có yêu cầu.
21. Kiểm tra `getCause() == originalCause` và metadata/message đúng.
22. Khi từ chối là trạng thái dự kiến của giao thức và caller cần xử lý như một nhánh thông thường.
23. Không; bắt quá rộng có thể nuốt `Error` và lỗi không liên quan.
24. Caller chỉ biết exception công khai của module; cause còn giữ thông tin kỹ thuật cho logging.
25. Lỗi có khác biệt ngữ nghĩa/cách xử lý không? Loại chuẩn hoặc result đã đủ chưa? Caller/ranh giới API được lợi gì?

## 22. Ghi nhớ

**Hãy định nghĩa custom exception khi cần một ngữ nghĩa thất bại riêng, một hợp đồng xử lý có kiểu, dữ liệu lỗi cấu trúc hoặc một ranh giới abstraction rõ ràng.** Đừng tạo lớp mới chỉ để thay chuỗi message hay cho mọi trạng thái không thuận lợi. Ưu tiên ngoại lệ chuẩn cho tiền điều kiện đơn giản, kết quả tường minh cho nhánh nghiệp vụ dự kiến, và custom exception khi caller thực sự cần phân biệt. Chọn **checked hoặc unchecked** theo hợp đồng API, giữ **cause** khi bọc lỗi, tránh phân cấp sâu không cần thiết, và kiểm thử cả dữ liệu lẫn hành vi ném/bắt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-002">Chọn extends Exception hay extends RuntimeException</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `extends Exception` hay `extends RuntimeException` khi tạo ngoại lệ tùy chỉnh trong Java

Khi thiết kế một ngoại lệ tùy chỉnh (*custom exception*) trong Java, hai cách khai báo phổ biến là `class MyException extends Exception` và `class MyException extends RuntimeException`. Cả hai đều tạo một loại exception có thể `throw`, `catch`, truyền `cause` và lưu stack trace. Khác biệt quan trọng nhất là **nghĩa vụ kiểm tra lúc biên dịch** của bên gọi:

- **`extends Exception` trực tiếp** (hoặc kế thừa qua một lớp con checked) tạo **checked exception**: mã gọi phải bắt hoặc khai báo khả năng lan truyền exception theo quy tắc **catch-or-declare**.
- **`extends RuntimeException`** tạo **unchecked exception**: mã gọi không bị trình biên dịch buộc bắt hoặc khai báo, dù exception vẫn có thể khiến thao tác thất bại ở runtime.

Đây là quyết định về **hợp đồng API, trách nhiệm của caller, khả năng tiến hóa phần mềm và cách diễn đạt lỗi**, không chỉ là chuyện thêm hay bớt từ khóa `throws`. Không có quy tắc máy móc “lỗi nghiệp vụ thì checked, lỗi kỹ thuật thì unchecked” hoặc “có thể phục hồi thì checked, không thể phục hồi thì unchecked” áp dụng chính xác cho mọi hệ thống.

Bài này trình bày cơ chế ngôn ngữ, tiêu chí lựa chọn theo tình huống, ví dụ Java 17 chạy được, hợp đồng khi kế thừa/override, wrapping và cause, xử lý ở ranh giới ứng dụng, ảnh hưởng tương thích API, kiểm thử và các sai lầm thiết kế thường gặp.

## 1. Nhìn đúng hệ phân cấp `Throwable`

Sơ đồ rút gọn:

```text
java.lang.Throwable
├── java.lang.Error                         (unchecked)
│   └── ...
└── java.lang.Exception
    ├── YourCheckedException                 (checked)
    ├── java.io.IOException                  (checked)
    └── java.lang.RuntimeException           (unchecked)
        ├── YourUncheckedException           (unchecked)
        ├── java.lang.IllegalArgumentException
        └── java.lang.IllegalStateException
```

Phân loại theo Java:

| Lớp gốc của ngoại lệ mới | Phân loại | Caller có bắt buộc `catch` hoặc `throws` không? |
| --- | --- | --- |
| `extends Exception` trực tiếp | Checked | Có, nếu đường gọi có thể phát sinh exception đó |
| `extends SomeCheckedException` | Checked | Có |
| `extends RuntimeException` | Unchecked | Không |
| `extends SomeRuntimeException` | Unchecked | Không |
| `extends Error` | Unchecked | Không, nhưng thường **không** thích hợp cho lỗi nghiệp vụ |
| `extends Throwable` trực tiếp | Checked | Có, nhưng thường không nên vì tách khỏi nhánh `Exception` thông dụng |

Lưu ý: **`RuntimeException` vốn là lớp con của `Exception`**. Vì vậy câu “mọi lớp con của `Exception` đều checked” là sai; ngoại lệ thuộc nhánh `RuntimeException` không chịu bắt buộc catch-or-declare.

Đa số ngoại lệ ứng dụng nên thuộc một trong hai nhánh `Exception` (checked) hoặc `RuntimeException` (unchecked). `Error` được dành chủ yếu cho những điều kiện nghiêm trọng ở mức JVM/hệ thống; không nên chọn nó chỉ để tránh viết `throws`.

## 2. Khác biệt nằm ở **compile time**, không nằm ở khả năng ném tại runtime

Cú pháp khai báo tối thiểu:

```java
class DataImportException extends Exception {
    DataImportException(String message) {
        super(message);
    }
}

class InvalidImportRequestException extends RuntimeException {
    InvalidImportRequestException(String message) {
        super(message);
    }
}
```

Đây là **hai lớp minh họa**, chưa có `main`. Cả hai đều có thể ném:

```java
throw new DataImportException("Không đọc được dữ liệu");
// hoặc ở tình huống khác:
throw new InvalidImportRequestException("Sai tham số");
```

Đây là **hai lựa chọn độc lập**, không đặt liên tiếp trong cùng một luồng mã.

Với checked `DataImportException`, phương thức chứa câu lệnh `throw` phải **bắt hoặc khai báo** nó; nếu không, compiler báo lỗi. Với unchecked `InvalidImportRequestException`, Java không buộc thêm `throws` trong chữ ký. Tuy nhiên cả hai khi được ném đều gây **abrupt completion** của đường thực thi hiện tại và đều tìm handler `catch` tương thích trên call stack.

**Không có chuyện unchecked exception “nhẹ hơn”, “không phải exception thật” hay “không làm chương trình dừng”.** Khác biệt là ràng buộc tĩnh đối với mã gọi; hệ quả runtime vẫn phải được thiết kế xử lý.

## 3. Ví dụ đầy đủ: checked custom exception bắt buộc phải được xử lý

Lưu tệp **`CheckedCustomDemo.java`**:

```java
class MissingConfigException extends Exception {
    MissingConfigException(String message) {
        super(message);
    }
}

public class CheckedCustomDemo {
    static String loadConfig(boolean present)
            throws MissingConfigException {
        if (!present) {
            throw new MissingConfigException(
                    "Thiếu cấu hình bắt buộc");
        }
        return "ready";
    }

    public static void main(String[] args) {
        try {
            System.out.println("Kết quả: " + loadConfig(true));
            System.out.println("Kết quả: " + loadConfig(false));
        } catch (MissingConfigException e) {
            System.out.println("Không thể tiếp tục: " + e.getMessage());
        }
        System.out.println("Đã xử lý ở ranh giới main");
    }
}
```

Biên dịch/chạy Java 17+:

```bash
javac -encoding UTF-8 --release 17 CheckedCustomDemo.java
java CheckedCustomDemo
```

**Stdout chính xác** trên console UTF-8:

```text
Kết quả: ready
Không thể tiếp tục: Thiếu cấu hình bắt buộc
Đã xử lý ở ranh giới main
```

Phân tích:

1. `MissingConfigException extends Exception` nên là **checked**.
2. `loadConfig` có đường `throw new MissingConfigException(...)`, vì vậy khai báo `throws MissingConfigException`.
3. `main` bắt loại đã công bố, quyết định không thực hiện bước nạp tiếp theo, rồi chạy mã sau handler.
4. Khi lần gọi thứ hai ném exception, dòng `System.out.println("Kết quả: " + loadConfig(false))` **không** được in: biểu thức bên phải không hoàn tất.
5. Loại lỗi giúp caller phân biệt thiếu cấu hình với những loại thất bại khác mà API có thể báo.

Nếu bỏ `throws MissingConfigException` khỏi `loadConfig` mà không bắt tại đó, code **không biên dịch**. Nếu bỏ `catch` trong `main` mà không thêm `throws` phù hợp, `main` cũng không biên dịch. `throws` không bắt lỗi; nó chỉ cho phép exception đi tới caller theo hợp đồng.

## 4. Ví dụ đầy đủ: unchecked custom exception không ép caller viết `throws`

Lưu **`UncheckedCustomDemo.java`**:

```java
class InvalidQuantityException extends RuntimeException {
    InvalidQuantityException(String message) {
        super(message);
    }
}

public class UncheckedCustomDemo {
    static int calculateTotal(int quantity, int unitPrice) {
        if (quantity <= 0) {
            throw new InvalidQuantityException(
                    "quantity phải lớn hơn 0");
        }
        return quantity * unitPrice;
    }

    public static void main(String[] args) {
        System.out.println("Hợp lệ: " + calculateTotal(2, 50));

        try {
            calculateTotal(0, 50);
            System.out.println("Không tới dòng này");
        } catch (InvalidQuantityException e) {
            System.out.println("Không hợp lệ: " + e.getMessage());
        }

        System.out.println("Đã xử lý đầu vào");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UncheckedCustomDemo.java
java UncheckedCustomDemo
```

**Stdout chính xác:**

```text
Hợp lệ: 100
Không hợp lệ: quantity phải lớn hơn 0
Đã xử lý đầu vào
```

`calculateTotal` không có `throws InvalidQuantityException` mà vẫn biên dịch, vì exception này là unchecked. `main` vẫn **có thể chủ động `catch`** để chuyển lỗi thành thông báo phù hợp. Việc bắt unchecked exception không bị cấm; chỉ là **không bắt buộc bởi compiler**.

Đây là ví dụ về tiền điều kiện của phương thức. Trong một hệ thống nhận dữ liệu từ người dùng, tầng nhập liệu thường nên xác thực trước và trả một kết quả kiểm tra hợp lệ, thay vì để exception do sai đầu vào đi xuyên các tầng. Việc chọn kiểu exception không thay thế thiết kế luồng xác thực.

## 5. Câu hỏi thiết kế quan trọng nhất: ai có trách nhiệm xử lý?

Thay vì hỏi “loại lỗi nào nghiêm trọng hơn?”, hãy hỏi:

**Caller có phải được buộc bằng hợp đồng Java để nhận diện và quyết định một kiểu thất bại đã công bố không, hay chúng ta muốn để nó lan truyền và xử lý tại ranh giới phù hợp?**

Khi một API thực hiện một thao tác có thất bại dự kiến, được mô tả rõ, và caller có một số cách xử lý hợp lệ, **checked exception có thể làm hợp đồng tường minh**. Ví dụ một bộ đọc dữ liệu phải phân biệt lỗi đọc có thể chuyển nguồn dự phòng.

Khi một lỗi biểu thị vi phạm tiền điều kiện hoặc trạng thái do cách gọi sai, **unchecked exception thường phù hợp hơn**, để mã có thể thất bại rõ ràng mà không buộc mọi tầng trung gian khai báo hoặc bắt một lỗi lập trình chưa biết cách sửa.

Nhưng cần nhớ:

- Một `IOException` là checked dù có tình huống không thể khắc phục.
- Một `IllegalArgumentException` là unchecked dù tầng giao diện có thể yêu cầu người dùng sửa đầu vào.
- Nhiều framework chọn unchecked domain exceptions để có ranh giới tập trung xử lý lỗi.
- Một hệ thống batch có thể dùng checked exception cho những điều kiện yêu cầu caller quyết định retry hoặc bỏ qua bản ghi.
- Một kết quả nghiệp vụ dự kiến có thể nên được trả bằng **giá trị/kết quả có trạng thái**, không phải exception bất kể checked hay unchecked.

Do đó, **“có thể phục hồi hay không” là một tín hiệu thiết kế, không phải phép thử duy nhất**.

## 6. Khi nào nên cân nhắc `extends Exception`?

Checked custom exception có ích khi những điều kiện sau hợp lý với API:

1. **Loại thất bại là một phần được công bố của hợp đồng phương thức.** Người dùng API cần nhận biết một điều kiện cụ thể để quyết định luồng tiếp theo.
2. **Caller có trách nhiệm đưa ra lựa chọn.** Ví dụ thử cấu hình dự phòng, dừng tác vụ, yêu cầu một lựa chọn khác hoặc báo lỗi tầng giao tiếp.
3. **Việc bắt/khai báo mang giá trị thực tế**, không chỉ là những `catch` rỗng sinh ra để làm compiler hết báo lỗi.
4. **Loại lỗi đủ ổn định** để được đưa vào chữ ký API và tài liệu phiên bản.
5. **Phạm vi API được kiểm soát.** Việc lan truyền checked exception qua nhiều lớp trừu tượng không gây ra quá nhiều phụ thuộc không cần thiết.

Ví dụ hợp đồng:

```java
static Report importReport(Path file)
        throws ReportFormatException, IOException {
    // Đọc file; phát hiện định dạng sai hoặc lỗi I/O.
}
```

Đây là **chữ ký minh họa**, chưa có định nghĩa `Report`, `ReportFormatException` và phần thân. `ReportFormatException extends Exception` có thể là lựa chọn nếu caller được kỳ vọng phân biệt dữ liệu sai định dạng với lỗi I/O để phản hồi phù hợp.

Nếu caller chỉ có thể thất bại và tất cả tầng trung gian phải bắt rồi ném lại mà không bổ sung gì, lợi ích của checked exception có thể thấp hơn chi phí hợp đồng phải truyền xuyên suốt.

## 7. Khi nào nên cân nhắc `extends RuntimeException`?

Unchecked custom exception thường hữu ích khi:

1. **Vi phạm tiền điều kiện lập trình** như đầu vào bắt buộc bị `null`, số lượng âm không được phép, hoặc gọi phương thức sai trình tự.
2. **Trạng thái hiện tại không phù hợp để thực hiện thao tác**, thường là một invariant hoặc `IllegalStateException` có ý nghĩa ở tầng ứng dụng.
3. **Caller thông thường không có hành động xử lý cục bộ hữu ích**; lỗi được chuyển tới một ranh giới giám sát hoặc được sửa ở nơi dùng API.
4. **Kiến trúc muốn tập trung xử lý exception**, ví dụ ở tầng HTTP/job/controller, thay vì ép mọi service/repository truyền `throws`.
5. **API nội bộ hoặc callback** có giới hạn về checked exceptions; cần hợp đồng unchecked phù hợp thay vì khai báo sai hoặc nuốt lỗi.

Ví dụ:

```java
class InvalidWorkflowStateException extends RuntimeException {
    InvalidWorkflowStateException(String message) {
        super(message);
    }
}
```

Nếu một đơn đã hủy nhưng mã tiếp tục cố hoàn tất nó, class này có thể truyền tín hiệu **vi phạm trạng thái workflow**. Caller có thể bắt cụ thể, nhưng thường lỗi cần được xử lý ở ranh giới biết trạng thái tổng thể.

Cần phân biệt **trạng thái không hợp lệ do bug** với **yêu cầu nghiệp vụ bị từ chối bình thường**. Ví dụ số dư không đủ hoặc sản phẩm hết hàng có thể được thiết kế thành kết quả domain có trạng thái thay vì ném exception. Unchecked exception không tự biến một kết quả dự kiến thành lỗi hệ thống.

## 8. Bảng quyết định nhanh

| Tình huống | Gợi ý ban đầu | Điều phải cân nhắc |
| --- | --- | --- |
| Phương thức công bố thất bại I/O và caller cần quyết định fallback | Checked hoặc giữ `IOException` | Bắt/khai báo ở caller mang ý nghĩa không? |
| Hàm tiện ích bị truyền tham số vi phạm hợp đồng | Unchecked | Nên dùng `IllegalArgumentException` có sẵn hay class riêng? |
| Trạng thái đối tượng bị dùng sai trình tự | Unchecked | `IllegalStateException` có đủ diễn đạt không? |
| Parser nhận tệp không hợp lệ và caller phải xử lý riêng | Checked có thể thích hợp | Định dạng sai là ngoại lệ hay kết quả dự kiến? |
| Luồng nhập liệu của người dùng có giá trị sai thường xuyên | Kết quả xác thực có cấu trúc | Đừng dùng exception để điều khiển luồng bình thường nếu không cần |
| Tầng service chuyển `IOException` thành lỗi domain | Tùy hợp đồng service | Giữ `cause` và chọn checked/unchecked dựa vào API |
| Request handler cần đưa mọi lỗi bất ngờ về phản hồi an toàn | Thường xử lý ở ranh giới, có thể dùng unchecked | Đừng làm lộ stack trace hoặc biến bug thành thành công |
| Thư viện công khai cần hứa loại lỗi cụ thể ổn định | Checked có giá trị trong một số API | Chi phí tiến hóa chữ ký/khả năng tương thích |
| Sự cố có thể gây hỏng JVM/không thể tiếp tục | Không tự tạo domain `Error` | Chính sách phục hồi/vận hành riêng, đừng nuốt `Error` |
| Cancellation bằng `InterruptedException` | Thường tiếp tục checked exception gốc | Không phá tín hiệu interrupt vì tiện xử lý |

Bảng trên là **khung quyết định**, không phải luật cứng. Kiểu exception nên phản ánh **ngữ nghĩa của API**, chính sách khôi phục và các quy ước nhất quán trong hệ thống.

## 9. `throws` của checked exception ảnh hưởng người gọi như thế nào?

Giả sử một API:

```java
static void exportData() throws ExportFailedException {
    // ...
}
```

với `ExportFailedException extends Exception`. Một caller muốn gọi `exportData()` có thể chọn:

**Xử lý tại chỗ:**

```java
try {
    exportData();
} catch (ExportFailedException e) {
    showExportError(e);
}
```

**Cho lan truyền:**

```java
static void runExport() throws ExportFailedException {
    exportData();
}
```

Đây là các **đoạn minh họa độc lập**, còn thiếu định nghĩa method phụ. Nhánh đầu phù hợp nếu tầng hiện tại biết cách xử lý. Nhánh thứ hai phù hợp nếu caller cao hơn chịu trách nhiệm. Cả hai đều thể hiện **catch-or-declare**.

Nếu đổi `ExportFailedException` sang `extends RuntimeException`, compiler không buộc caller phải viết handler hoặc `throws` nữa. Nhưng **exception vẫn có thể xảy ra**. Tài liệu API cần công bố ý nghĩa và cách phản ứng, thay vì chỉ dựa vào việc compiler có cảnh báo hay không.

Không nên cho rằng viết `throws` làm exception “chạy lên” còn bỏ `throws` khiến nó “biến mất”: cả checked và unchecked đều lan truyền theo đường thực thi khi không được bắt; `throws` là thông tin hợp đồng có kiểm tra cho checked exception.

## 10. Có cần viết `throws MyRuntimeException` không?

Không bắt buộc:

```java
static void validate(int amount)
        throws InvalidQuantityException {
    if (amount <= 0) {
        throw new InvalidQuantityException("amount <= 0");
    }
}
```

Đây là **phương thức minh họa** trong ngữ cảnh đã có class `InvalidQuantityException extends RuntimeException`. Chữ ký `throws InvalidQuantityException` hợp lệ nhưng **không làm compiler buộc caller bắt nó**.

Có thể chọn đưa unchecked exception vào `throws` để người đọc nhận biết một số điều kiện đặc biệt, nhưng cách này có thể gây hiểu nhầm rằng caller phải catch. Một lựa chọn thường rõ hơn là **JavaDoc `@throws`** để mô tả tiền điều kiện và ý nghĩa:

```java
/**
 * @throws InvalidQuantityException nếu amount không dương
 */
static void validate(int amount) {
    if (amount <= 0) {
        throw new InvalidQuantityException("amount <= 0");
    }
}
```

Đây là **phần minh họa**. Với public API, nên tài liệu hóa cả loại unchecked exception nếu caller cần biết để sử dụng đúng hoặc xử lý ở ranh giới.

## 11. Nên tạo class riêng hay dùng exception sẵn có?

Không phải lỗi nào cũng cần một custom exception. Trước khi chọn hai nhánh kế thừa, hỏi **“Có cần một loại mới không?”**

Các exception sẵn có có thể đã đủ:

| Ý nghĩa | Lớp sẵn có thường phù hợp |
| --- | --- |
| Tham số không hợp lệ | `IllegalArgumentException` |
| Tham số bắt buộc là `null` | `NullPointerException`, thường qua `Objects.requireNonNull` |
| Trạng thái đối tượng không phù hợp | `IllegalStateException` |
| Giá trị chuỗi không phân tích được thành số | `NumberFormatException` |
| Lỗi thao tác I/O | `IOException` và lớp con cụ thể |
| Lỗi định dạng số người dùng nhập | Có thể bắt `NumberFormatException` ở ranh giới và chuyển thành kết quả xác thực |

Custom exception hữu ích khi cần **phân biệt bằng kiểu** giữa các điều kiện quan trọng cho caller, cung cấp mã lỗi ổn định, hoặc gắn ngữ nghĩa module khác với loại Java chung.

Không nên tạo các class chỉ khác message nhưng không có tác dụng định tuyến hoặc kiểm thử, ví dụ `BadInput1Exception`, `BadInput2Exception` không có hợp đồng rõ ràng. Ngược lại, nếu hai lỗi cần những chính sách phục hồi khác nhau, loại riêng có thể giúp tránh `if (e.getMessage().contains(...))` mong manh.

## 12. Thiết kế constructor đúng: message, cause và cả hai

Một custom exception tốt thường có constructor tối thiểu phù hợp với hợp đồng. Ví dụ checked:

```java
public class ReportFormatException extends Exception {
    public ReportFormatException(String message) {
        super(message);
    }

    public ReportFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **một class độc lập** cần đặt trong `ReportFormatException.java` nếu khai báo `public`. Phần quan trọng là các constructor gọi `super(...)` để giữ **detail message** và, khi có, **cause**.

Ví dụ unchecked:

```java
public class OrderStateException extends RuntimeException {
    public OrderStateException(String message) {
        super(message);
    }

    public OrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là class độc lập `OrderStateException.java`. Dù checked hay unchecked, **cause không được tự động tạo** nếu chỉ truyền message. Với `super(message, cause)`, caller có thể lần theo `getCause()`.

Bạn không bắt buộc tạo đủ bốn constructor (`()`, `(String)`, `(Throwable)`, `(String, Throwable)`) cho mọi class. Chỉ cung cấp những constructor cần thiết để tránh trạng thái exception khó hiểu. Nếu muốn giữ một nguyên nhân kỹ thuật, thường nên có dạng `(String, Throwable)`.

## 13. Ví dụ thực hành: bọc `IOException` thành checked custom exception

Nếu tầng đọc tệp có lỗi I/O nhưng API xử lý báo cáo muốn công bố **loại lỗi riêng**, có thể dùng checked exception wrapper. Lưu **`CheckedWrappingDemo.java`**:

```java
import java.io.IOException;

class ReportLoadException extends Exception {
    ReportLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class CheckedWrappingDemo {
    static String readRaw() throws IOException {
        throw new IOException("Không đọc được nguồn");
    }

    static String loadReport() throws ReportLoadException {
        try {
            return readRaw();
        } catch (IOException e) {
            throw new ReportLoadException("Không tải được báo cáo", e);
        }
    }

    public static void main(String[] args) {
        try {
            loadReport();
        } catch (ReportLoadException e) {
            System.out.println("Lỗi báo cáo: " + e.getMessage());
            System.out.println("Nguyên nhân: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CheckedWrappingDemo.java
java CheckedWrappingDemo
```

**Stdout chính xác:**

```text
Lỗi báo cáo: Không tải được báo cáo
Nguyên nhân: IOException
Cause message: Không đọc được nguồn
```

Phương thức `loadReport` công bố một checked `ReportLoadException` thay vì `IOException`. Caller buộc xử lý/khai báo loại domain đã công bố; `cause` vẫn lưu ngoại lệ gốc để chẩn đoán.

**Wrapping là quyết định thay đổi abstraction**, không chỉ một mẹo “thay tên lỗi”. Nếu caller vẫn cần xử lý riêng `IOException` để đổi nguồn dự phòng, giấu loại đó sau wrapper chưa chắc hợp lý. Có thể chọn giữ `throws IOException` hoặc cung cấp mã lỗi có cấu trúc.

## 14. Ví dụ thực hành: bọc checked thành unchecked tại ranh giới phù hợp

Một tầng service có thể công bố unchecked exception theo hợp đồng để các tầng trung gian không buộc truyền checked exception.

Lưu **`UncheckedWrappingDemo.java`**:

```java
import java.io.IOException;

class ServiceUnavailableException extends RuntimeException {
    ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class UncheckedWrappingDemo {
    static void connect() throws IOException {
        throw new IOException("Kết nối bị từ chối");
    }

    static void performService() {
        try {
            connect();
        } catch (IOException e) {
            throw new ServiceUnavailableException(
                    "Dịch vụ chưa sẵn sàng", e);
        }
    }

    public static void main(String[] args) {
        try {
            performService();
        } catch (ServiceUnavailableException e) {
            System.out.println("Service: " + e.getMessage());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 UncheckedWrappingDemo.java
java UncheckedWrappingDemo
```

**Stdout chính xác:**

```text
Service: Dịch vụ chưa sẵn sàng
Cause: IOException
```

`performService` không cần khai báo `throws` vì nó ném `ServiceUnavailableException extends RuntimeException`. Dù compiler không buộc `main` catch, handler ở ranh giới vẫn có thể quyết định cách thông báo hoặc thất bại an toàn.

Không lạm dụng **`throw new RuntimeException(e)`** một cách vô nghĩa ở mọi tầng. Việc bọc exception chỉ có giá trị khi nó thể hiện hợp đồng mới, thêm ngữ cảnh hữu ích hoặc đáp ứng một ranh giới API cụ thể; cần giữ `cause` để không mất stack trace gốc.

## 15. Một exception tùy chỉnh có mã lỗi ổn định

Message có thể thay đổi theo ngôn ngữ hoặc nội dung; **đừng dùng message để chọn nhánh chương trình**. Một custom exception có thể cung cấp `errorCode` ổn định.

Lưu **`ErrorCodeDemo.java`**:

```java
enum OrderError {
    INVALID_STATE,
    DUPLICATE_REQUEST
}

class OrderException extends RuntimeException {
    private final OrderError code;

    OrderException(OrderError code, String message) {
        super(message);
        this.code = code;
    }

    OrderError getCode() {
        return code;
    }
}

public class ErrorCodeDemo {
    static void commit(boolean alreadyCommitted) {
        if (alreadyCommitted) {
            throw new OrderException(
                    OrderError.DUPLICATE_REQUEST,
                    "Yêu cầu đã được xử lý");
        }
    }

    public static void main(String[] args) {
        try {
            commit(true);
        } catch (OrderException e) {
            System.out.println("Code: " + e.getCode());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 ErrorCodeDemo.java
java ErrorCodeDemo
```

**Stdout chính xác:**

```text
Code: DUPLICATE_REQUEST
Message: Yêu cầu đã được xử lý
```

Enum giúp caller chuyển hướng xử lý bằng **kiểu/mã lỗi có cấu trúc**, không bằng `e.getMessage().contains(...)`. Tuy nhiên, mã lỗi nghiệp vụ vẫn cần được đặt vào đúng hợp đồng: một yêu cầu trùng hợp lệ có thể phù hợp hơn với **kết quả idempotent** hoặc kiểu kết quả thay vì exception, tùy hệ thống.

Tính ổn định của mã lỗi không đồng nghĩa với việc có thể thay đổi ngữ nghĩa của mã bất kỳ mà không ảnh hưởng caller. Hãy tài liệu hóa phạm vi các mã được hỗ trợ.

## 16. Checked custom exception và quy tắc kế thừa/override phương thức

Lựa chọn checked/unchecked ảnh hưởng cách thực hiện interface và override method:

- Khi override một phương thức, bạn **không được khai báo checked exception mới hoặc rộng hơn** so với hợp đồng checked exception của phương thức bị override.
- Có thể khai báo **loại checked exception hẹp hơn** hoặc không khai báo.
- Có thể ném **unchecked exception** (nhánh `RuntimeException`) mà không bị giới hạn tương tự bởi mệnh đề `throws`.
- Việc override hợp lệ hay không do **kiểu tĩnh và chữ ký** của Java, không phải do caller có định bắt lỗi hay không.

Ví dụ chạy được **`OverrideContractDemo.java`**:

```java
import java.io.FileNotFoundException;
import java.io.IOException;

interface Source {
    String read() throws IOException;
}

class LocalSource implements Source {
    @Override
    public String read() throws FileNotFoundException {
        throw new FileNotFoundException("Không thấy tệp");
    }
}

public class OverrideContractDemo {
    public static void main(String[] args) {
        Source source = new LocalSource();

        try {
            System.out.println(source.read());
        } catch (IOException e) {
            System.out.println("Bắt: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 OverrideContractDemo.java
java OverrideContractDemo
```

**Stdout chính xác:**

```text
Bắt: FileNotFoundException
```

`Source.read()` cho phép `IOException`. `LocalSource.read()` khai báo `FileNotFoundException`, một lớp con checked của `IOException`, nên override hợp lệ.

Nếu một interface có `void run();` **không khai báo checked exception**, implementation không được tự thêm `throws MyCheckedException` vào phương thức override. Đây là một yếu tố thực tế khi thiết kế callback, framework API hoặc phương thức được cung cấp cho bên thứ ba.

**Điểm thiết kế:** nếu một API dự kiến bắt buộc caller xử lý checked exception, hợp đồng đó nên được công bố đúng từ cấp interface/base method. Không thể sửa bằng cách chỉ thêm checked `throws` ở implementation override.

## 17. Checked exception trong lambda và functional interface

Hạn chế checked exceptions còn xuất hiện trong lambda. Ví dụ `Runnable.run()` không khai báo checked exception:

```java
Runnable r = () -> {
    // throw new MyCheckedException("Lỗi"); // Không hợp lệ.
};
```

Đây là **mã minh họa**; giả sử `MyCheckedException extends Exception`. Nếu viết dòng `throw` thực sự mà không xử lý checked exception bên trong lambda, mã bị lỗi compile vì `Runnable.run()` không cho phép checked exception đó.

Có những lựa chọn hợp lệ:

1. Xử lý checked exception **trong lambda**, nếu ở đây có cách phục hồi.
2. Dùng **functional interface riêng có `throws` phù hợp** khi sở hữu API.
3. Chuyển sang một unchecked exception có `cause`, **chỉ khi hợp đồng callback chấp nhận chuyển đổi**.
4. Thiết kế lại nơi gọi để checked exception được xử lý ở tầng hợp lý thay vì ép buộc callback làm công việc không thuộc trách nhiệm.

Ví dụ functional interface riêng:

```java
@FunctionalInterface
interface CheckedTask {
    void run() throws DataImportException;
}
```

Đây là **interface minh họa**, cần `DataImportException extends Exception` được khai báo. Khi lambda có target type `CheckedTask`, nó có thể ném loại checked được công bố. Chọn loại exception vì **hợp đồng API**, không vì cần một cú pháp nhanh để compile qua.

## 18. Thay đổi loại checked/unchecked ảnh hưởng caller và tương thích API

Đây không phải quyết định hoàn toàn riêng tư của class. Khi công bố một thư viện hoặc module, người dùng API có thể viết:

```java
try {
    client.execute();
} catch (YourCheckedException e) {
    recover(e);
}
```

Đây là **mã khung minh họa**. Nếu API hoặc cấu trúc kế thừa thay đổi:

- **Thêm một checked exception** mới vào đường thoát của phương thức có thể buộc mã nguồn caller sửa `catch` hoặc `throws` để biên dịch lại.
- **Đổi một checked exception thành unchecked** có thể ảnh hưởng các `catch`, tài liệu và kỳ vọng caller; đôi khi `catch` riêng cho một checked loại không còn có thể phát sinh từ `try` sẽ trở thành mã không hợp lệ khi biên dịch.
- **Thay loại exception** dù vẫn unchecked có thể phá các handler bắt theo lớp cụ thể.
- **Đổi superclass** của exception có thể ảnh hưởng các mối quan hệ `instanceof`, catch ordering, serialization và hợp đồng công khai.
- **Giữ nguyên chữ ký binary** chưa đủ để bảo đảm hành vi và khả năng tương thích mã nguồn, vì checked-exception checking diễn ra ở compile time và ứng dụng có thể phụ thuộc quy tắc xử lý cũ.

Vì vậy, nên chọn và tài liệu hóa sớm với API công khai, đồng thời thiết kế **phân loại lỗi có khả năng tiến hóa**. Đừng đổi giữa `Exception` và `RuntimeException` chỉ để dọn bớt `throws` khi chưa xem ảnh hưởng tới các module sử dụng.

## 19. Mối quan hệ giữa checked/unchecked và hành vi phục hồi

Một lỗi có thể **phục hồi được** nhưng unchecked. Ví dụ `NumberFormatException` có thể được giao diện nhập liệu bắt và yêu cầu nhập lại. Ngược lại, một `IOException` checked có thể xảy ra vì đĩa hỏng hoặc nguồn dữ liệu không còn tồn tại; caller có thể chỉ có lựa chọn dừng thao tác.

Bởi vậy, phân tích nên theo câu hỏi:

- Tầng hiện tại **có đủ thông tin** để phục hồi không?
- Caller **phải bị ép** bằng trình biên dịch để xét loại lỗi này hay không?
- Hệ thống có chính sách phục hồi ở **ranh giới cuối** không?
- Đây là **thất bại bất thường** hay **kết quả nghiệp vụ dự kiến**?
- Việc khôi phục có thực sự giữ **bất biến/side effect** hợp lệ không?

Ví dụ: API thanh toán báo “số tiền vượt hạn mức” có thể được thiết kế thành đối tượng kết quả từ chối hợp lệ, không nhất thiết `PaymentException`. Nhưng lỗi “dịch vụ ngân hàng không kết nối được” là sự cố kỹ thuật, có thể dùng exception, với phân loại checked/unchecked tùy hợp đồng.

**Khả năng phục hồi phụ thuộc ngữ cảnh, không phải do superclass tự quyết định.**

## 20. Vì sao không nên bắt mọi `Exception` rồi tiếp tục?

Một cách “giảm phiền” khi dùng checked exception là viết:

```java
try {
    doWork();
} catch (Exception ignored) {
    // Không làm gì.
}
```

Đây là **mã khung phản ví dụ**, `doWork()` chưa được định nghĩa. `catch (Exception)` có thể bắt cả checked lẫn `RuntimeException`. Việc nuốt lỗi khiến caller có thể tin thao tác thành công dù dữ liệu chưa hoàn chỉnh hoặc trạng thái bị hỏng.

Tương tự, một unchecked exception cũng không nên bị chặn mù quáng chỉ để hệ thống tiếp tục. Tầng ranh giới có thể cần bắt rộng để ngăn một request làm dừng vòng phục vụ, nhưng phải **ghi nhận thất bại, trả trạng thái chính xác và có chính sách thích hợp**.

Phân biệt ba hành vi:

1. **Phục hồi thật sự**: có fallback hợp lệ, kết quả được phép.
2. **Ném lại/bọc lỗi**: tầng hiện tại không thể phục hồi nhưng chuyển tín hiệu với ngữ cảnh đúng.
3. **Nuốt lỗi**: không xử lý, không báo thất bại, để caller hiểu nhầm — thường là bug.

Việc chọn `extends RuntimeException` không cho phép bỏ qua bước thiết kế chính sách xử lý.

## 21. Quản lý `InterruptedException`: ngoại lệ checked đặc biệt

`InterruptedException` là checked exception thường được dùng trong các thao tác chờ hoặc bị ngắt như `Thread.sleep`. Nó còn có ý nghĩa **tín hiệu yêu cầu dừng/hủy** một công việc đang chờ.

Khi chuyển `InterruptedException` thành một custom unchecked exception, không nên xóa mất ngữ nghĩa interrupt. Một ví dụ API đồng bộ có thể truyền tiếp `throws InterruptedException` là lựa chọn rõ nhất nếu hợp đồng cho phép.

Nếu ranh giới API không cho phép checked `throws InterruptedException`, một phương án thường dùng là:

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Thao tác bị ngắt", e);
}
```

Đây là **mã minh họa trong phương thức**. Việc gọi `Thread.currentThread().interrupt()` khôi phục cờ interrupt mà những thao tác như `sleep` thường đã xóa khi ném exception. Wrapper có cause giữ dấu vết gốc.

Tuy nhiên, **không áp dụng mẫu này máy móc cho mọi API**. Nếu caller cần phân biệt ngắt để dừng một tác vụ, để checked `InterruptedException` lan tiếp có thể đúng hơn. Điều quan trọng là không nuốt hoặc coi interrupt là một lỗi thông thường được phép bỏ qua.

## 22. Không nên kế thừa `Error` cho ngoại lệ nghiệp vụ

`Error` và các lớp con là unchecked, nhưng điều đó không khiến `extends Error` thành lựa chọn thay thế cho `extends RuntimeException`. `Error` thường biểu thị những tình huống nghiêm trọng về môi trường/JVM hoặc các điều kiện không được mã nghiệp vụ thông thường xử lý như một sự cố dự kiến.

Ví dụ không nên làm:

```java
// Không khuyến nghị cho lỗi nghiệp vụ:
class ProductUnavailableError extends Error {
    ProductUnavailableError(String message) {
        super(message);
    }
}
```

Đây là class minh họa cho **thiết kế không nên dùng**. Với một sản phẩm không có sẵn, có thể chọn kết quả domain, hoặc một exception phù hợp thuộc `Exception`/`RuntimeException` nếu thật sự cần.

Tương tự, thường không nên `extends Throwable` trực tiếp: một số handler của người sử dụng API viết `catch (Exception e)` sẽ **không bắt** được loại trực tiếp chỉ thuộc `Throwable` mà không nằm trong `Exception`. Nên giữ hệ phân cấp theo quy ước chuẩn.

## 23. Cấu trúc phân cấp custom exception cho một module

Khi module có nhiều loại lỗi và caller cần phân biệt bằng kiểu, có thể tạo lớp nền có mã lỗi rồi cho các loại cụ thể kế thừa. Nhưng phải **giữ thống nhất chính sách checked/unchecked theo từng nhánh**, không tạo cây quá sâu.

Ví dụ thiết kế unchecked:

```java
class ApplicationException extends RuntimeException {
    ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class ConfigurationException extends ApplicationException {
    ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **các class minh họa**. `catch (ApplicationException e)` sẽ bắt cả `ConfigurationException`; `catch (ConfigurationException e)` cho phép xử lý cụ thể hơn. Nếu viết hai nhánh `catch` trong cùng cấu trúc, nhánh cụ thể phải đứng trước lớp cha.

Một thiết kế khác có thể là checked base `ApplicationException extends Exception`, nhưng khi đó các loại con checked sẽ đi vào hợp đồng `throws` của các phương thức có thể ném chúng. **Đừng chọn base checked chỉ vì nghe “nghiêm túc hơn”**; phải đánh giá caller có cần nghĩa vụ catch-or-declare với cả nhánh lỗi hay không.

Nếu một lỗi không thuộc cùng chính sách xử lý, không cần cố ép nó vào cùng cây kế thừa. Có thể dùng exception sẵn có hoặc nhánh class khác và ánh xạ về mã lỗi ở ranh giới.

## 24. Dùng `catch` nhiều loại khi caller có chính sách chung

Một caller có thể muốn xử lý nhiều ngoại lệ bằng một nhánh, kể cả khi hai loại thuộc checked và unchecked:

```java
try {
    execute();
} catch (ReportLoadException | IllegalArgumentException e) {
    System.out.println("Thất bại: "
            + e.getClass().getSimpleName());
}
```

Đây là **mã khung minh họa**, giả định `ReportLoadException extends Exception` và `execute()` có thể ném hai loại đã nêu. `ReportLoadException` và `IllegalArgumentException` không có quan hệ subtype, nên cú pháp multi-catch là hợp lệ.

Tham số multi-catch **ngầm định `final`**, không thể gán lại. Nó nhận **một object exception tại runtime**; loại runtime thực của object được giữ nguyên.

Nếu chính sách xử lý hai lỗi khác nhau, nên dùng **hai handler riêng** để giữ ý nghĩa, thay vì gộp chỉ để giảm số dòng. Không gộp một loại cha và một loại con trong cùng multi-catch vì compiler từ chối phần kiểu chồng lắp.

## 25. Kiểm thử checked/unchecked: không chỉ dựa vào khả năng biên dịch

Với checked exception, compiler giúp kiểm tra những đường ném theo chữ ký, nhưng **không bảo đảm phục hồi đúng**. Với unchecked exception, test cũng cần xác nhận hành vi vì caller không bị buộc catch.

Một test có giá trị cần kiểm tra:

| Trường hợp | Nội dung kiểm chứng |
| --- | --- |
| Đường thành công | Không ném exception, kết quả đúng |
| Tiền điều kiện không hợp lệ | Loại unchecked cụ thể, message hoặc mã lỗi hợp đồng |
| Thất bại checked được công bố | Caller phải catch/declare; runtime ném đúng loại |
| Bọc ngoại lệ | Wrapper đúng loại; `getCause()` giữ object gốc |
| Ném lại | Caller vẫn nhận đúng exception gốc nếu hợp đồng yêu cầu |
| Không có handler | Thao tác thực sự thất bại, không trả giá trị thành công giả |
| Handler tại ranh giới | Phản hồi cuối an toàn, không lộ dữ liệu nhạy cảm |
| Cleanup cùng thất bại | Primary, cause và suppressed được giữ đúng |
| Thay đổi API | Mã caller mẫu vẫn biên dịch với phiên bản mới |
| Callback và override | Không có checked `throws` trái hợp đồng kế thừa |

Có thể dùng JUnit `assertThrows` trong dự án, nhưng không cần thư viện ngoài để kiểm tra cơ bản. Ví dụ tự kiểm tra Java 17 dưới đây cho cả hai nhánh.

## 26. Chương trình tự kiểm tra cả checked và unchecked

Lưu **`CustomExceptionAssertionsDemo.java`**:

```java
class CustomChecked extends Exception {
    CustomChecked(String message) {
        super(message);
    }
}

class CustomUnchecked extends RuntimeException {
    CustomUnchecked(String message) {
        super(message);
    }
}

public class CustomExceptionAssertionsDemo {
    static void checked() throws CustomChecked {
        throw new CustomChecked("checked");
    }

    static void unchecked() {
        throw new CustomUnchecked("unchecked");
    }

    public static void main(String[] args) {
        try {
            checked();
            throw new AssertionError("Thiếu checked exception");
        } catch (CustomChecked e) {
            if (!"checked".equals(e.getMessage())) {
                throw new AssertionError("Sai checked message");
            }
            System.out.println("PASS checked");
        }

        try {
            unchecked();
            throw new AssertionError("Thiếu unchecked exception");
        } catch (CustomUnchecked e) {
            if (!"unchecked".equals(e.getMessage())) {
                throw new AssertionError("Sai unchecked message");
            }
            System.out.println("PASS unchecked");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 CustomExceptionAssertionsDemo.java
java CustomExceptionAssertionsDemo
```

**Stdout chính xác:**

```text
PASS checked
PASS unchecked
```

Chương trình chạy thành công khi cả hai exception đúng loại/message. `AssertionError` được dùng để chủ động làm test thất bại nếu không ném hoặc sai kết quả, không phụ thuộc tùy chọn `-ea` của từ khóa `assert`.

Việc chạy thành công không chứng minh một thiết kế checked/unchecked luôn phù hợp: test chỉ xác nhận **hành vi đã chọn**, còn chất lượng hợp đồng cần được review theo trách nhiệm caller, khả năng phục hồi và cách tiến hóa API.

## 27. Các sai lầm thường gặp

### 27.1. “Lỗi có thể sửa được thì luôn `extends Exception`”

Không đúng. Một input không hợp lệ do người dùng có thể sửa nhưng `NumberFormatException` là unchecked. Ngược lại, một `IOException` checked có thể do lỗi hạ tầng chưa có cách khắc phục. Thiết kế cần xem **ai quyết định và có nên buộc caller** xử lý tại compile time.

### 27.2. “Lỗi không quan trọng thì `extends RuntimeException`”

Sai. Unchecked exception vẫn có thể rất nghiêm trọng; nó chỉ không bị bắt/khai báo bắt buộc. Đừng dùng để đánh dấu mức độ ưu tiên xử lý.

### 27.3. “`extends Exception` sẽ bắt mọi bug ở caller”

Không. Checked exception chỉ được compiler kiểm tra theo **những đường ném được mô tả**. `RuntimeException` và `Error` vẫn có thể xảy ra dù không khai báo. Compiler không đảm bảo ứng dụng không có bug runtime.

### 27.4. “`catch (Exception)` là an toàn nhất”

Handler quá rộng có thể bắt cả `RuntimeException` do bug rồi che mất lỗi. Nếu chỉ xử lý một thất bại cụ thể, hãy bắt **loại cụ thể**.

### 27.5. “Cứ dùng `throws Exception` là đủ”

Mệnh đề quá rộng làm caller mất thông tin về loại lỗi. Nên khai báo các checked exception phù hợp hợp đồng nếu API cần tính chính xác.

### 27.6. “Muốn bỏ checked thì `throw new RuntimeException(e.getMessage())`”

Cách này tạo exception mới **không giữ cause**, làm mất thông tin chẩn đoán. Nếu phải bọc, dùng constructor có `cause`, ví dụ `new ServiceException("...", e)`.

### 27.7. “Custom exception nào cũng cần bốn constructor, field và nhiều lớp con”

Không bắt buộc. Chọn API gọn, có ý nghĩa; class riêng chỉ khi tăng khả năng phân biệt và quản lý lỗi.

### 27.8. “Có checked exception là caller đã xử lý xong”

Caller có thể chỉ `catch` rồi bỏ qua, in log trùng lặp hoặc trả kết quả sai. Compiler kiểm tra nghĩa vụ cú pháp, không đánh giá chất lượng phục hồi.

### 27.9. “Unchecked exception không cần tài liệu”

Sai. Bên dùng API vẫn cần biết tiền điều kiện, mã lỗi, hoàn cảnh thất bại và kỳ vọng xử lý, thường qua JavaDoc `@throws` và hướng dẫn sử dụng.

### 27.10. “Chuyển giữa checked và unchecked không ảnh hưởng ai”

Thay đổi có thể làm hỏng mã nguồn caller, logic `catch`, hợp đồng interface/override và cách framework xử lý. Cần đánh giá tương thích trước khi đổi superclass.

### 27.11. “Dùng `extends Error` để tránh `throws` là hợp lý”

Không nên. `Error` không phải vùng dành cho lỗi nghiệp vụ thông thường; chọn `RuntimeException` nếu hợp đồng cần unchecked.

### 27.12. “Mọi kết quả nghiệp vụ đều nên dùng custom exception”

Không nhất thiết. Những trường hợp dự kiến như tìm kiếm không có kết quả hoặc yêu cầu được từ chối hợp lệ có thể phù hợp với `Optional`, `enum`, `sealed interface` hoặc kiểu kết quả có trạng thái.

## 28. Câu hỏi tự kiểm tra

**Câu 1.** `class DataException extends Exception` trực tiếp là checked hay unchecked?  
**Đáp án:** Checked; caller phải catch hoặc declare khi đường gọi có thể ném.

**Câu 2.** `class StateException extends RuntimeException` có thể được bắt bằng `catch (Exception e)` không?  
**Đáp án:** Có. `RuntimeException` nằm trong nhánh `Exception`, dù unchecked.

**Câu 3.** Vì sao không khai báo `throws` vẫn có thể ném `StateException`?  
**Đáp án:** Vì nó là unchecked exception; compiler không bắt buộc catch-or-declare.

**Câu 4.** Nếu `extends Exception`, liệu exception tự phục hồi hoặc caller tự xử lý đúng không?  
**Đáp án:** Không. Compiler chỉ buộc bắt/khai báo. Phục hồi cần logic có ý nghĩa.

**Câu 5.** Có nên tạo `CustomInvalidArgumentException` thay mọi `IllegalArgumentException` không?  
**Đáp án:** Chỉ khi class mới tăng khả năng phân loại/hợp đồng thực sự; nếu không, loại sẵn có có thể đủ.

**Câu 6.** Khi wrapping `IOException` thành exception dịch vụ, làm sao giữ nguyên nhân?  
**Đáp án:** Constructor `(String, Throwable)` và gọi `super(message, cause)`, để `getCause()` giữ exception gốc.

**Câu 7.** Đổi một custom checked exception thành unchecked có thể làm thay đổi caller không?  
**Đáp án:** Có. Nghĩa vụ catch/declare, tính hợp lệ của handler, override và kỳ vọng API có thể thay đổi.

**Câu 8.** Một phương thức override có thể khai báo checked exception rộng hơn base method không?  
**Đáp án:** Không, theo quy tắc checked-exception của Java; có thể hẹp hơn nếu hợp lệ.

**Câu 9.** Tại sao lambda target type `Runnable` không thể tùy ý ném custom checked exception?  
**Đáp án:** Vì `Runnable.run()` không khai báo checked exception đó; phải xử lý hoặc dùng functional interface khác phù hợp.

**Câu 10.** `getMessage()` có nên dùng để quyết định mã lỗi nghiệp vụ không?  
**Đáp án:** Không nên; message có thể thay đổi/được dịch. Dùng loại hoặc mã lỗi có cấu trúc.

**Câu 11.** Unchecked exception có thể được catch ở ranh giới HTTP/CLI không?  
**Đáp án:** Có. Không bắt buộc không đồng nghĩa không thể bắt.

**Câu 12.** `Error` thuộc checked hay unchecked? Có phù hợp cho lỗi “không đủ số dư” không?  
**Đáp án:** `Error` là unchecked nhưng không phù hợp cho lỗi nghiệp vụ như vậy.

**Câu 13.** Khi nào một kết quả có trạng thái tốt hơn exception?  
**Đáp án:** Khi thất bại là nhánh nghiệp vụ dự kiến và caller cần xử lý thường xuyên theo luồng bình thường.

**Câu 14.** Tại sao phải xử lý `InterruptedException` cẩn thận nếu định bọc thành unchecked?  
**Đáp án:** Nó mang tín hiệu ngắt/hủy. Cần truyền tiếp nếu hợp đồng cho phép hoặc phục hồi trạng thái interrupt khi phù hợp.

**Câu 15.** Nếu client luôn bắt `catch (ApplicationException e)`, subclass của `ApplicationException` có được bắt không?  
**Đáp án:** Có, nếu thuộc nhánh kế thừa đó và exception được ném vào phạm vi handler.

## 29. Checklist quyết định cho một custom exception mới

Trước khi tạo `XxxException`, hãy tự rà soát:

1. **Đây có phải exception không?** Hay là kết quả nghiệp vụ dự kiến nên được trả có trạng thái?
2. **Loại sẵn có có đủ rõ không?** Ví dụ `IllegalArgumentException`/`IllegalStateException`.
3. **Caller có bắt buộc phải xét loại lỗi này ở compile time không?** Nếu có và mang giá trị rõ ràng, cân nhắc checked.
4. **Caller thường không thể xử lý ở tầng gần mà cần ranh giới cuối?** Cân nhắc unchecked và tài liệu hóa.
5. **Loại lỗi ổn định theo hợp đồng API chứ?** Tránh thay superclass liên tục.
6. **Có cần biến thể recovery khác nhau?** Nếu có, phân loại bằng kiểu/mã lỗi cụ thể.
7. **Có giữ `cause` khi chuyển lỗi từ tầng thấp không?** Đảm bảo constructor phù hợp.
8. **Message có an toàn và hữu ích không?** Không đưa mật khẩu/token/nội dung nhạy cảm.
9. **Có mã lỗi ổn định cho ứng dụng không?** Không parse text message.
10. **Có interface/callback ảnh hưởng checked `throws` không?** Xem quy tắc override và functional interface.
11. **Có ngữ nghĩa cancellation/interrupt không?** Không tự nuốt tín hiệu hủy.
12. **Có tình huống cleanup làm phát sinh suppressed không?** Giữ cause/suppressed khi chẩn đoán.
13. **Handler cuối nằm ở đâu?** HTTP, CLI, job scheduler, UI hoặc caller thư viện.
14. **Đã test hành vi cả thành công và thất bại chưa?** Không chỉ dựa vào compiler.
15. **Việc thay đổi loại exception có phá caller đã triển khai không?** Xem xét cả source/API compatibility.
16. **Có đang viết `catch` chỉ để im lặng bỏ qua lỗi không?** Tránh biến thất bại thành thành công giả.

## 30. Kết luận

**`extends Exception` và `extends RuntimeException` tạo hai hợp đồng khác nhau cho custom exception Java**:

- **Checked (`extends Exception` trực tiếp):** caller có nghĩa vụ **catch hoặc declare** nếu phương thức có thể ném lỗi; phù hợp khi việc công bố và buộc xét một thất bại cụ thể là một phần có giá trị của API.
- **Unchecked (`extends RuntimeException`):** caller **không bị ép** bởi compiler; phù hợp với nhiều trường hợp vi phạm tiền điều kiện, trạng thái và các thiết kế xử lý tập trung, nhưng lỗi vẫn phải được chẩn đoán/điều phối đúng.

Không có quy tắc duy nhất dựa vào mức độ nghiêm trọng hoặc khả năng phục hồi. Hãy chọn theo **ý nghĩa lỗi, trách nhiệm caller, độ ổn định của API, chính sách xử lý cuối và trải nghiệm người dùng**. Giữ nguyên nhân qua `cause` khi wrapping, tài liệu hóa unchecked exceptions, dùng kết quả có trạng thái cho nhánh nghiệp vụ dự kiến, và tránh tạo cây exception phức tạp không có lợi ích.

Quy tắc thực hành gọn nhất: **hãy dùng checked khi việc ép caller xử lý/khai báo là một phần hữu ích của hợp đồng; dùng unchecked khi không muốn áp đặt nghĩa vụ ấy; và chỉ tạo exception mới khi loại mới diễn đạt một tình huống mà caller hoặc hệ thống thực sự cần phân biệt**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-003">Đặt tên theo tình huống thất bại và mức trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên ngoại lệ tùy chỉnh theo tình huống thất bại và mức trừu tượng trong Java

Khi thiết kế **custom exception** trong Java, mục tiêu không chỉ là đặt tên một lớp kế thừa `Exception` hoặc `RuntimeException`. Tên ngoại lệ cần giúp người đọc trả lời hai câu hỏi: **“Điều gì đã thất bại?”** và **“Tầng nào chịu trách nhiệm diễn giải thất bại đó?”**. Một tên tốt làm rõ hành vi hợp đồng, cách khôi phục và ý nghĩa của `catch`; một tên chung chung hoặc rò rỉ chi tiết triển khai có thể khiến API khó sử dụng, khó bảo trì và khó thay đổi hạ tầng.

Ví dụ trong hệ thống đơn hàng:

- `OrderNotFoundException`: không tìm thấy đơn hàng mà người gọi yêu cầu.
- `OrderAlreadyPaidException`: thao tác thanh toán bị từ chối vì đơn đã được thanh toán.
- `PaymentDeclinedException`: yêu cầu thanh toán bị từ chối theo kết quả nghiệp vụ của quá trình thanh toán.
- `PaymentGatewayUnavailableException`: không thể liên hệ hoặc sử dụng cổng thanh toán để xác định kết quả.

Những lớp trên đều có thể là **tên minh họa do ứng dụng định nghĩa**, không phải lớp dựng sẵn của Java. Chúng truyền đạt các tình huống khác nhau. Đặc biệt, “bị từ chối” và “không gọi được hệ thống để biết kết quả” **không tương đương**: chính sách retry và thông báo người dùng có thể khác nhau.

Bài học hướng dẫn cách chọn tên ngoại lệ dựa trên tình huống thất bại và **mức trừu tượng** (domain, application/service, infrastructure, API boundary), khi nào dùng lại ngoại lệ chuẩn, khi nào tạo lớp riêng, cách chọn checked/unchecked, giữ nguyên nhân gốc, thiết kế constructor và kiểm thử hành vi. Ví dụ sử dụng Java 17+.

## 1. Ngoại lệ tùy chỉnh là gì và vì sao cần tên rõ nghĩa?

Ngoại lệ tùy chỉnh là một lớp do ứng dụng hoặc thư viện định nghĩa, kế thừa một lớp thuộc phân cấp `Throwable`, thường là `Exception` hoặc `RuntimeException`:

~~~java
// Một kiểu checked exception.
class OrderNotFoundException extends Exception {
    OrderNotFoundException(String message) {
        super(message);
    }
}

// Một kiểu unchecked exception.
class OrderAlreadyPaidException extends IllegalStateException {
    OrderAlreadyPaidException(String message) {
        super(message);
    }
}
~~~

Tên riêng hữu ích khi caller cần **nhận biết một trường hợp thất bại có ý nghĩa** khác với những trường hợp khác. Chẳng hạn, caller có thể hiển thị “không tìm thấy đơn” cho `OrderNotFoundException`, nhưng chặn việc thanh toán trùng cho `OrderAlreadyPaidException`.

Ngược lại, nếu phương thức chỉ nhận một đối số âm trong khi hợp đồng yêu cầu không âm, một `IllegalArgumentException` chuẩn thường đã đủ:

~~~java
static void reserve(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity phải dương");
    }
}
~~~

Một lớp `BadQuantityException` không tự động tốt hơn nếu nó không bổ sung hợp đồng, hành vi phân loại hoặc thông tin hữu ích. Tạo ngoại lệ tùy chỉnh cần có lý do thiết kế cụ thể, tránh tạo nhiều loại chỉ để tên nhìn có vẻ “nghiệp vụ”.

**Nguyên tắc:** tên ngoại lệ là một phần của **ngôn ngữ API**, không phải chỉ là nhãn cho thông điệp log. Khi nó được công bố từ một thư viện hoặc module, các lớp client có thể sử dụng chính kiểu ngoại lệ đó trong `catch`, kiểm thử hoặc tài liệu.

## 2. Đặt tên theo tình huống thất bại: “điều gì xảy ra?”

Một tên tốt thường diễn đạt một **trạng thái hoặc kết quả không mong muốn**, gắn với đối tượng/thao tác cụ thể:

| Tình huống | Tên nên cân nhắc | Tên quá mơ hồ |
| --- | --- | --- |
| Không tìm thấy đơn được yêu cầu | `OrderNotFoundException` | `NotFoundError`, `DataException` |
| Thanh toán lại một đơn đã trả tiền | `OrderAlreadyPaidException` | `ProcessException`, `FailException` |
| Hết hàng trong lúc đặt | `InsufficientInventoryException` | `StockErrorException`, `SomethingWrongException` |
| Không nạp được cấu hình ứng dụng | `ConfigurationLoadException` | `FileErrorException`, `ServiceException` |
| Phiên sử dụng đã hết hạn | `SessionExpiredException` | `ExpiredException` |
| Không thể xác thực quyền truy cập | `AuthorizationDeniedException` | `SecurityProblemException` |
| Đơn bị khóa nên không chỉnh sửa được | `OrderLockedException` | `UpdateException` |
| Dịch vụ cổng thanh toán không khả dụng | `PaymentGatewayUnavailableException` | `HttpException` ở tầng nghiệp vụ |

Mỗi tên “nên cân nhắc” là **phương án thiết kế minh họa**, cần kiểm tra ngôn ngữ miền của dự án. Nếu API chuẩn đã có kiểu phù hợp, ưu tiên dùng lại thay vì tạo lớp mới.

Từ khóa thường hữu ích trong tên:

- **`NotFound`**: đối tượng cần tìm không tồn tại theo hợp đồng tra cứu; không dùng cho mọi lỗi kết nối khiến thao tác tra cứu không hoàn thành.
- **`Already...`**: thao tác đang vi phạm trạng thái đã hoàn tất, ví dụ `OrderAlreadyPaidException`.
- **`Invalid...`**: quy tắc dữ liệu hoặc trạng thái không hợp lệ, nhưng cần chỉ rõ *cái gì* không hợp lệ.
- **`Expired`**: tài nguyên phiên/token đã hết hiệu lực theo thời gian, không chỉ là “lỗi hệ thống”.
- **`Unavailable`**: phụ thuộc/hệ thống tạm thời không thể sử dụng, không đồng nghĩa yêu cầu bị từ chối vì nghiệp vụ.
- **`Load`/`Parse`/`Decode`**: thất bại của thao tác chuyển đổi/nạp trong một abstraction; nên phân biệt dữ liệu sai định dạng với không truy cập được dữ liệu.

Tên tốt không nhất thiết dài. `OrderNotFoundException` đã rõ trong một domain cụ thể; `OrderLookupFailedDueToInternalDatabaseConnectivityException` thường vừa rò rỉ triển khai vừa không ổn định.

## 3. Thế nào là “mức trừu tượng” của ngoại lệ?

Một hệ thống thường có nhiều tầng:

~~~text
API / UI
   ↓
Application / Service
   ↓
Domain / Business Rules
   ↓
Infrastructure / Database / Files / Network
~~~

Sơ đồ này là ví dụ tổ chức phổ biến, không phải cấu trúc bắt buộc của mọi ứng dụng. Cùng một sự cố có thể được **diễn giải khác** khi đi qua ranh giới tầng.

Ví dụ một file cấu hình bị từ chối truy cập:

1. Hạ tầng gọi `Files.readString(path)` và nhận `IOException` hoặc lớp con phù hợp.
2. Tầng nạp cấu hình biết thao tác của mình là **nạp cấu hình ứng dụng**, nên có thể bọc thành `ConfigurationLoadException`, giữ `IOException` làm cause.
3. Tầng API/UI quyết định cách báo cho người dùng, có thể ghi log toàn bộ lỗi nội bộ và trả thông điệp an toàn.

Ở tầng hạ tầng, `IOException` đã có ý nghĩa. Ở tầng cấu hình, chỉ nói `IOException` có thể là quá chi tiết hoặc quá hẹp với ý nghĩa của hợp đồng. Nhưng không phải cứ đi qua tầng mới là bắt buộc bọc; chỉ bọc khi **tạo ra ngữ nghĩa mới có giá trị**.

### 3.1. Ngoại lệ domain

Tên nhấn mạnh quy tắc hoặc khái niệm nghiệp vụ:

~~~java
// Các tên minh họa trong domain đơn hàng.
class OrderAlreadyPaidException extends IllegalStateException { /* ... */ }
class InsufficientInventoryException extends RuntimeException { /* ... */ }
~~~

Ở đây, kiểu ngoại lệ không nên phụ thuộc vào chi tiết rằng dữ liệu được lưu ở PostgreSQL, MongoDB, file CSV hay API bên ngoài. Nếu thay kho lưu trữ mà tên domain vẫn đúng, đó là tín hiệu mức trừu tượng hợp lý.

### 3.2. Ngoại lệ application/service

Tên diễn đạt thao tác mà một dịch vụ cung cấp đã thất bại:

~~~java
// Tên minh họa ở tầng dịch vụ.
class ConfigurationLoadException extends RuntimeException { /* ... */ }
class ReportGenerationException extends Exception { /* ... */ }
~~~

Caller biết **nạp cấu hình** hay **tạo báo cáo** thất bại, trong khi lỗi chi tiết của parser, tệp, mạng hoặc renderer có thể nằm trong `cause`.

### 3.3. Ngoại lệ infrastructure

Tên có thể phản ánh một phụ thuộc kỹ thuật ở đúng tầng đó, ví dụ `StorageUnavailableException` trong adapter lưu trữ. Nhưng không phải mọi IOException/SQLException đều cần một wrapper riêng. Hãy giữ exception chuẩn khi nó đã đủ thể hiện hợp đồng mà caller ở tầng đó cần xử lý.

### 3.4. Ranh giới API

Ở ranh giới REST/HTTP, GraphQL hoặc UI, có thể cần chuyển exception nội bộ thành **response có cấu trúc** hoặc thông báo thân thiện. Một HTTP status/error code không nhất thiết phải là tên lớp exception. Ví dụ `OrderNotFoundException` có thể được mapping thành HTTP 404 trong một API cụ thể, nhưng lớp domain không nên mặc định mang tên `Http404Exception` chỉ để thuận tiện cho controller.

**Không được mặc định** mọi `NotFound` luôn là HTTP 404, mọi lỗi validation đều HTTP 400 hay mọi exception đều tương ứng một status cố định; mapping là chính sách của từng API.

## 4. Chọn tên theo điều kiện thất bại, không theo câu lệnh ném

Xét phương thức:

~~~java
static void pay(Order order) {
    if (order.isPaid()) {
        throw new OrderAlreadyPaidException(order.id());
    }
    // Thực hiện thao tác thanh toán hợp lệ.
}
~~~

Đây là **đoạn minh họa**, cần định nghĩa `Order` và `OrderAlreadyPaidException` thích hợp. Quy tắc ở đây là “đơn đã thanh toán không được thanh toán lại”. Tên `OrderAlreadyPaidException` mô tả **điều kiện**, không mô tả thao tác “throw”.

Các tên kém rõ nghĩa:

- `PayException`: chỉ nói có liên quan đến thanh toán, không biết lý do.
- `InvalidException`: không biết cái gì bất hợp lệ.
- `RuntimeFailureException`: phản ánh kỹ thuật thay vì hợp đồng.
- `Exception1`, `CustomException`, `MyException`: không có nghĩa miền.
- `OrderException`: có thể dùng như lớp cha phân nhóm nhưng thường quá rộng nếu ném trực tiếp cho mọi lỗi cụ thể.

Một lớp cha `OrderException` **có thể hợp lý** khi caller có nhu cầu catch toàn bộ một nhóm lỗi domain, nhưng các lớp con vẫn cần phản ánh từng tình huống. Không nên tạo sâu nhiều tầng kế thừa chỉ để mô phỏng toàn bộ sơ đồ nghiệp vụ.

## 5. Độ chi tiết phù hợp: cụ thể đến mức có ích

Có hai thái cực không tốt:

**Quá rộng:** chỉ có `ApplicationException` và truyền message khác nhau cho tất cả trường hợp. Caller phải so sánh chuỗi thông điệp để quyết định cách xử lý:

~~~java
// Mã minh họa không nên dùng để phân loại lỗi.
catch (ApplicationException ex) {
    if ("NOT_FOUND".equals(ex.getMessage())) {
        // Fragile: phụ thuộc string message.
    }
}
~~~

**Quá hẹp:** tạo một lớp cho từng câu lệnh hoặc từng chi tiết hạ tầng, chẳng hạn `PostgresQuery42TimeoutOnOrdersTableException`. Khi đổi database, kiểu exception không còn diễn đạt hợp đồng ổn định, dù người gọi chỉ cần biết thao tác lưu trữ tạm thời không khả dụng.

Câu hỏi quyết định có cần một subtype riêng:

1. **Caller có hành động khác nhau không?** Ví dụ thiếu hàng thì yêu cầu chọn số lượng khác, cổng thanh toán lỗi thì thông báo thử lại.
2. **Đây có phải một điều kiện ổn định của hợp đồng API không?** Hay chỉ là bug/chi tiết nội bộ có thể biến mất khi refactor?
3. **Có thể tài liệu hóa chính xác “khi nào ném” không?** Nếu không, tên kiểu có thể đang quá mơ hồ.
4. **Tầng sử dụng có hiểu tên đó không?** Nếu caller là domain order, tên chứa vendor SDK có thể lệch abstraction.
5. **Một kiểu chuẩn đã đủ chưa?** Với null, chỉ số, trạng thái sai, Java thường đã có `NullPointerException`, `IndexOutOfBoundsException`, `IllegalArgumentException`, `IllegalStateException`.

Ưu tiên **ít loại nhưng có ý nghĩa khác biệt rõ ràng** hơn là một cây ngoại lệ khổng lồ không mang lại cách xử lý thực tế.

## 6. Java 17+ chạy được: `OrderNotFoundException` và `OrderAlreadyPaidException`

Ví dụ này tách hai tình huống domain khác nhau: tra cứu không có kết quả và thao tác thanh toán bị từ chối vì trạng thái hiện tại.

Lưu thành `OrderNamingDemo.java`:

~~~java
public class OrderNamingDemo {
    enum Status { DRAFT, PAID }

    static final class Order {
        final String id;
        Status status = Status.DRAFT;

        Order(String id) {
            this.id = id;
        }
    }

    static final class OrderNotFoundException extends Exception {
        OrderNotFoundException(String orderId) {
            super("Không tìm thấy đơn " + orderId);
        }
    }

    static final class OrderAlreadyPaidException extends IllegalStateException {
        OrderAlreadyPaidException(String orderId) {
            super("Đơn " + orderId + " đã thanh toán");
        }
    }

    static Order find(String orderId) throws OrderNotFoundException {
        if (!"A-17".equals(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return new Order(orderId);
    }

    static void pay(Order order) {
        if (order.status == Status.PAID) {
            throw new OrderAlreadyPaidException(order.id);
        }
        order.status = Status.PAID;
    }

    public static void main(String[] args) throws OrderNotFoundException {
        Order order = find("A-17");
        pay(order);
        System.out.println("Trạng thái: " + order.status);
        try {
            pay(order);
        } catch (OrderAlreadyPaidException ex) {
            System.out.println("Nghiệp vụ: " + ex.getMessage());
        }
        try {
            find("B-42");
        } catch (OrderNotFoundException ex) {
            System.out.println("Tra cứu: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 OrderNamingDemo.java
java OrderNamingDemo
~~~

Stdout chính xác:

~~~text
Trạng thái: PAID
Nghiệp vụ: Đơn A-17 đã thanh toán
Tra cứu: Không tìm thấy đơn B-42
~~~

### Phân tích các quyết định đặt tên

- **`OrderNotFoundException`**: biểu diễn kết quả tra cứu không có đơn phù hợp. Ở chương trình minh họa, lớp này là checked exception, nên `find` khai báo `throws OrderNotFoundException`; caller bắt hoặc tiếp tục khai báo kiểu đó.
- **`OrderAlreadyPaidException`**: biểu diễn trạng thái hiện tại khiến thao tác không hợp lệ; ví dụ chọn kế thừa `IllegalStateException`, nên nó là unchecked exception và caller không bắt buộc `catch`.
- Tên của hai ngoại lệ cho phép tách đường xử lý mà **không kiểm tra text message**. Đây là lợi ích thực chất của kiểu exception riêng.
- `find` là bộ mô phỏng tra cứu ổn định, còn `pay` chỉ đổi trạng thái trong bộ nhớ. Chương trình không triển khai thanh toán thật, không xử lý giao dịch đồng thời, không chứng minh idempotency của API thanh toán.
- Nếu trong ứng dụng thực, “không tìm thấy” là kết quả bình thường dự kiến, có thể thích hợp hơn khi dùng `Optional<Order>` thay vì ném checked exception. Đây là **lựa chọn thiết kế API**, không có một đáp án đúng duy nhất.
- Nếu “đã thanh toán” được coi là kết quả idempotent thành công, API có thể trả về trạng thái thay vì ném `OrderAlreadyPaidException`. Đề bài tập trung vào việc **đặt tên khi đã quyết định đây là ngoại lệ**, không áp đặt rằng tình huống này luôn phải ném.

### Vì sao dùng `extends IllegalStateException` thay vì `extends RuntimeException`?

Cả hai đều hợp lệ cho unchecked exception. Chọn `IllegalStateException` diễn đạt rằng lỗi liên quan đến **trạng thái của đối tượng hoặc thao tác**, và cho phép caller có nhu cầu bắt nhóm `IllegalStateException` nếu thật sự phù hợp. Đổi lại, cần bảo đảm ý nghĩa “state không cho phép” đúng với hợp đồng. Nếu dự án có phân cấp domain chung với những lý do rõ ràng, có thể chọn lớp cha khác.

Không nên quyết định parent class chỉ theo tên nghe đẹp. Parent class là một phần quan hệ kiểu và ảnh hưởng tới `catch`, vì vậy nên khớp **ngữ nghĩa lẫn chính sách checked/unchecked**.

## 7. Java 17+ chạy được: bọc lỗi I/O tại ranh giới cấu hình ứng dụng

Ở tầng hạ tầng, Java đã có `IOException`; caller của tầng ứng dụng lại quan tâm hơn đến việc **nạp cấu hình** có thành công hay không. `ConfigurationLoadException` là một tên gắn với trách nhiệm của tầng trên.

Lưu thành `ConfigurationBoundaryDemo.java`:

~~~java
import java.io.IOException;

public class ConfigurationBoundaryDemo {
    static final class ConfigurationLoadException extends RuntimeException {
        ConfigurationLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String readFromStorage(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("tệp cấu hình không đọc được");
        }
        return "mode=prod";
    }

    static String loadConfiguration(boolean fail) {
        try {
            return readFromStorage(fail);
        } catch (IOException ex) {
            throw new ConfigurationLoadException(
                    "Không thể nạp cấu hình ứng dụng", ex);
        }
    }

    public static void main(String[] args) {
        System.out.println("Giá trị: " + loadConfiguration(false));
        try {
            loadConfiguration(true);
        } catch (ConfigurationLoadException ex) {
            System.out.println("Tầng ứng dụng: " + ex.getMessage());
            System.out.println("Nguyên nhân kỹ thuật: "
                    + ex.getCause().getClass().getSimpleName()
                    + " - " + ex.getCause().getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ConfigurationBoundaryDemo.java
java ConfigurationBoundaryDemo
~~~

Stdout chính xác:

~~~text
Giá trị: mode=prod
Tầng ứng dụng: Không thể nạp cấu hình ứng dụng
Nguyên nhân kỹ thuật: IOException - tệp cấu hình không đọc được
~~~

**Điều cần học:**

1. `readFromStorage` công bố `throws IOException` vì đó là hợp đồng kỹ thuật của bước đọc.
2. `loadConfiguration` chuyển sang lỗi có ý nghĩa **ứng dụng**: không thể nạp cấu hình. Nó không dùng tên `FileReadErrorException` ở abstraction cao hơn.
3. Constructor `(message, cause)` giữ lại `IOException` để có thể truy vấn `getCause()` và in stack trace đầy đủ khi debug.
4. Caller `loadConfiguration` có thể bắt một loại lỗi cấp ứng dụng ổn định dù chi tiết lưu cấu hình thay đổi.
5. `ConfigurationLoadException` trong ví dụ là unchecked vì extends `RuntimeException`. Đây là quyết định minh họa. Nếu caller phải thực hiện một bước khôi phục có kiểm tra ở thời điểm biên dịch, dự án có thể chọn checked exception.
6. `readFromStorage` dùng giá trị mô phỏng để stdout không phụ thuộc file thật. Trong code sản phẩm, logic phải giải quyết `IOException` của API đọc thực tế và chính sách logging/bảo mật dữ liệu.

**Không bọc nếu không tạo giá trị mới.** Nếu abstraction gọi `readFromStorage` đã công bố `IOException` và caller đang xử lý đúng loại đó, việc thêm `new RuntimeException("read failed", ex)` chỉ để đổi tên có thể làm hợp đồng kém rõ hơn.

## 8. Tên theo loại thất bại hay thao tác thất bại?

Hai cách đặt tên đều có thể hữu ích:

- **Theo tình huống:** `OrderNotFoundException`, `SessionExpiredException`, `InsufficientInventoryException`.
- **Theo thao tác:** `ConfigurationLoadException`, `ReportGenerationException`, `InvoiceExportException`.

Chọn cách phản ánh tốt hơn điều **người gọi thực sự muốn biết**. Với `OrderNotFoundException`, caller phân biệt được tìm không thấy so với không liên hệ được hệ thống. Với `ReportGenerationException`, có thể chấp nhận một tên theo thao tác nếu mọi thất bại tạo báo cáo đều có chính sách xử lý tương tự ở tầng sử dụng.

Nhưng `ProcessFailedException` hoặc `OperationException` không xác định được thao tác hay tình huống. Nếu một lớp không thể tách biệt hợp đồng từ tên, cần xem nó có nên tồn tại hay không.

Sơ đồ lựa chọn:

~~~text
Có một trường hợp thất bại cụ thể?
    |
    +-- Có --> Caller cần phân biệt và xử lý riêng?
    |             |
    |             +-- Có --> Đặt tên theo điều kiện rõ ràng.
    |             |
    |             +-- Không --> Dùng exception chuẩn hoặc lớp cha phù hợp.
    |
    +-- Không --> Một thao tác ở abstraction hiện tại thất bại?
                  |
                  +-- Có --> Tên theo thao tác/mức trừu tượng nếu có giá trị.
                  |
                  +-- Không --> Xem lại có cần custom exception không.
~~~

Sơ đồ chỉ là **hướng dẫn tư duy**, không thay thế phân tích hợp đồng và chính sách lỗi của sản phẩm.

## 9. Phân biệt kết quả nghiệp vụ với lỗi hạ tầng

Một sai sót nguy hiểm là dùng cùng một kiểu cho hai điều kiện không tương đương.

Ví dụ với thanh toán:

| Điều kiện | Tên minh họa | Phản ứng có thể khác |
| --- | --- | --- |
| Ngân hàng/cổng thanh toán xác nhận giao dịch bị từ chối | `PaymentDeclinedException` | Hiển thị lý do an toàn; yêu cầu phương thức thanh toán khác |
| Cổng thanh toán không phản hồi | `PaymentGatewayUnavailableException` | Kiểm tra trạng thái giao dịch trước khi retry; không tuyên bố thanh toán bị từ chối |
| Đơn đã thanh toán trước đó | `OrderAlreadyPaidException` | Tránh thực hiện thu tiền lặp; trả trạng thái phù hợp |
| Dữ liệu request không hợp lệ | `IllegalArgumentException` hoặc kiểu validation của API | Sửa dữ liệu đầu vào trước khi gọi lại |

**Cảnh báo quan trọng:** “cổng thanh toán timeout” không nhất thiết chứng minh giao dịch chưa thực hiện. Nếu việc retry có thể tạo thanh toán trùng, xử lý an toàn cần cơ chế idempotency/đối soát theo hệ thống thanh toán. Đây là ví dụ cho thấy **tên exception không chỉ ảnh hưởng log, mà còn ảnh hưởng quyết định nghiệp vụ**.

Không nên đặt `PaymentFailedException` cho mọi kết quả nếu caller cần phân biệt từ chối, đã xử lý và không biết trạng thái. Ngược lại, nếu API thật sự chỉ công bố một lỗi cấp cao và mọi nguyên nhân đều có cùng cách xử lý, một lớp tổng quát kèm cause có thể hợp lý.

## 10. Ranh giới giữa kiểm tra đầu vào và ngoại lệ domain

Các loại chuẩn như `IllegalArgumentException`, `IllegalStateException`, `NullPointerException` rất hữu ích khi hợp đồng không đòi hỏi kiểu riêng.

Ví dụ:

~~~java
static void setPageSize(int pageSize) {
    if (pageSize < 1 || pageSize > 500) {
        throw new IllegalArgumentException("pageSize phải từ 1 đến 500");
    }
}
~~~

Nếu đây chỉ là precondition của một phương thức nội bộ, không cần tạo `InvalidPageSizeException` trong hầu hết trường hợp. Nhưng nếu một API công khai có chính sách thông báo hoặc cách phục hồi đặc biệt cho một tình huống validation, kiểu riêng có thể hữu ích.

Cách quyết định:

- **Vi phạm tiền điều kiện của lời gọi:** thường xem xét exception chuẩn.
- **Quy tắc/domain invariant cụ thể:** xem xét tên domain nếu caller cần phân biệt.
- **Một kết quả dự kiến bình thường:** xem xét return type/`Optional`/result object thay vì lạm dụng exception.
- **Một phụ thuộc hệ thống thất bại:** có thể giữ exception chuẩn hoặc bọc ở boundary cấp cao.
- **Bug lập trình:** thường không cần lớp `UnexpectedBugException` riêng cho từng vị trí.

Tránh cấu trúc mà mọi lỗi đều `catch (Exception ex)` rồi ném `BusinessException("thất bại")`; điều này có thể biến bug kỹ thuật thành lỗi nghiệp vụ và làm mất phân loại mà người vận hành cần.

## 11. Chọn `Exception` hay `RuntimeException` theo hợp đồng

Đặt tên nói lên **điều kiện thất bại**. Lựa chọn superclass quyết định **cách Java kiểm tra** tại nơi gọi:

| Đặc điểm | `extends Exception` (không thuộc RuntimeException) | `extends RuntimeException` |
| --- | --- | --- |
| Phân loại | Checked | Unchecked |
| Caller có bị compiler buộc bắt hoặc khai báo khi có thể thoát? | Có | Không |
| Thích hợp khi | API cần công bố một tình huống có khả năng xử lý rõ ở ranh giới lời gọi | Vi phạm hợp đồng/trạng thái hoặc chính sách dự án muốn truyền lỗi mà không ép catch |
| Có thể dùng constructor `(message, cause)`? | Có | Có |
| Có nên đặt tên theo domain/boundary? | Có | Có |

**Không có quy tắc “lỗi nghiệp vụ luôn checked” hay “lỗi hạ tầng luôn unchecked”.** Ví dụ `OrderNotFoundException` có thể checked hoặc unchecked tùy hợp đồng; thiếu đơn hàng cũng có thể được biểu diễn bằng `Optional.empty()`. Tên biểu đạt điều kiện không nhất thiết mã hóa lựa chọn checked/unchecked.

Cân nhắc cụ thể:

1. Caller có đường phục hồi hợp lý và bạn muốn **bắt buộc nhận diện** trường hợp đó tại compile time không?
2. Đây là lỗi sử dụng API sai hay một tình huống có thể xảy ra dù caller gọi đúng?
3. Hệ sinh thái/framework trong dự án có ràng buộc hay quy ước nào?
4. Thay đổi checked exception trong public API có thể khiến các caller cũ phải sửa `catch`/`throws`; bạn đã cân nhắc tương thích chưa?
5. Nếu dùng unchecked, đã tài liệu hóa điều kiện lỗi đủ rõ chưa?

Không kéo mọi lỗi domain vào `RuntimeException` chỉ để tránh viết `throws`; cũng không biến mọi lỗi có thể xảy ra thành checked exception khiến chữ ký API quá tải.

## 12. Constructor nên cung cấp thông tin gì?

Một custom exception đơn giản, hữu ích thường hỗ trợ thông điệp và cause:

~~~java
public class ConfigurationLoadException extends RuntimeException {
    public ConfigurationLoadException(String message) {
        super(message);
    }

    public ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là **lớp hoàn chỉnh**, lưu trong `ConfigurationLoadException.java` nếu dùng trực tiếp. Không cần viết tất cả các constructor một cách máy móc; chỉ công bố những overload caller thật sự cần.

Khi lỗi có metadata ổn định, có thể thêm field mang ý nghĩa hợp đồng:

~~~java
public class OrderNotFoundException extends Exception {
    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Không tìm thấy đơn " + orderId);
        this.orderId = orderId;
    }

    public String orderId() {
        return orderId;
    }
}
~~~

Mã trên là lớp hoàn chỉnh, dùng tên riêng của hệ thống và getter `orderId()`. Nếu cần API rộng, cần quyết định có nên công bố mã định danh và thông tin gì cho caller hay không; **không để dữ liệu nhạy cảm** như thông tin thanh toán, token, mật khẩu hoặc dữ liệu cá nhân vào message/log chỉ để dễ debug.

Nếu lỗi bao gồm một `cause`, constructor truyền nó vào `super(message, cause)` thay vì chỉ lưu `cause.getMessage()`, vì chuỗi text không bảo toàn loại exception, stack trace và các suppressed errors của nguyên nhân.

### Có nên công bố enum mã lỗi?

Trong API cần phân loại có cấu trúc, có thể dùng một `ErrorCode` hoặc kiểu kết quả được tài liệu hóa thay vì tạo vô số exception subtype. Ví dụ các lỗi cùng chính sách xử lý có thể được phân biệt bằng `code()`; nhưng nếu caller thường `catch (SpecificException)`, subtype riêng có thể rõ ràng hơn.

**Không dùng text message làm mã lỗi ổn định.** Thông điệp có thể được dịch, chỉnh sửa và bổ sung ngữ cảnh; những thay đổi đó không nên làm hỏng logic caller.

## 13. Giữ cause đúng mức khi chuyển abstraction

Một phương thức có thể bọc ngoại lệ của tầng dưới để cung cấp ngữ nghĩa tầng trên:

~~~java
// Minh họa trong catch (IOException ex).
throw new ConfigurationLoadException(
        "Không thể nạp cấu hình ứng dụng", ex);
~~~

Người dùng tầng application nhìn thấy `ConfigurationLoadException`; người điều tra có thể gọi `getCause()` để thấy `IOException` gốc. Điều này **khác với đổi tên tùy ý**:

~~~java
// Không nên làm mất cause:
catch (IOException ex) {
    throw new ConfigurationLoadException("Không thể nạp cấu hình ứng dụng");
}
~~~

Đoạn trên hợp lệ nếu constructor `(String)` tồn tại, nhưng nguyên nhân kỹ thuật đã bị bỏ khỏi chuỗi cause. Nếu có yêu cầu an toàn thông tin, hãy quản lý phần trình bày ra người dùng và log nội bộ thay vì mặc định vứt bỏ toàn bộ thông tin lỗi.

**Giữ cause không phải cớ để bọc mọi tầng.** Nếu một tầng chỉ chuyển tiếp ngoại lệ đúng hợp đồng đã công bố, có thể không cần wrapper. Bọc nhiều tầng với cùng nội dung `"failed"` khiến log rối mà không tăng ý nghĩa.

Khi có suppressed exceptions từ `try-with-resources`, nếu tầng dưới đã nhận chúng và ngoại lệ đó được dùng làm cause của wrapper, chúng vẫn gắn với cause. Khi điều tra, kiểm tra suppressed trên các tầng liên quan, không chỉ outermost.

## 14. Tên exception có được chứa công nghệ cụ thể không?

**Có thể, nhưng phải đúng vị trí abstraction.**

Ở một adapter PostgreSQL, tên kỹ thuật có thể hợp lý nếu API công khai của adapter gắn chặt với PostgreSQL. Nhưng nếu một tầng domain/order public API được thiết kế để không phụ thuộc công nghệ lưu trữ, `PostgresConnectionRefusedException` truyền thẳng ra caller có thể khiến domain bị khóa vào triển khai hiện tại.

Ví dụ phân biệt:

~~~text
Infrastructure:
    SQLException("connection refused")
            |
            v
Application boundary:
    OrderRepositoryUnavailableException(..., cause)
            |
            v
Order service:
    không thể tra cứu đơn ở lượt hiện tại
~~~

Sơ đồ này là minh họa; không bắt buộc mỗi tầng phải tạo thêm một đối tượng ngoại lệ. Chọn một boundary có trách nhiệm để diễn đạt điều kiện có ý nghĩa cho caller, rồi giữ cause kỹ thuật.

Nếu API domain **cần** phân biệt “không tìm thấy đơn” và “không thực hiện được tra cứu”, đừng biến cả hai thành `OrderNotFoundException`. Không trả được kết quả do mạng lỗi không chứng minh đơn hàng không tồn tại.

## 15. Đặt tên nhóm và cây kế thừa: đơn giản trước

Bạn có thể tổ chức:

~~~java
// Khung tên minh họa.
class OrderException extends RuntimeException { /* ... */ }
class OrderAlreadyPaidException extends OrderException { /* ... */ }
class OrderLockedException extends OrderException { /* ... */ }
~~~

Một `catch (OrderException ex)` có thể bắt các lỗi domain có cùng superclass, còn caller cần xử lý cụ thể có thể bắt subtype. Tuy nhiên cần xem xét:

- Những lỗi có thực sự cùng **chính sách xử lý** ở nơi bắt rộng không?
- Cây kế thừa có đang ép những điều kiện khác nhau vào cùng một nhóm không?
- Có trùng lặp với superclass chuẩn như `IllegalStateException` không?
- Có cần hỗ trợ cả checked và unchecked không? Một cây kế thừa đơn không thể vừa là checked vừa là unchecked theo hai nhánh khác nhau.
- Tên superclass `OrderException` có giúp gì thực tế hay chỉ tạo thêm tầng?

Kế thừa không bắt buộc để có tên custom exception tốt. Nhiều ứng dụng dùng các lớp exception độc lập và chỉ tạo một hierarchy khi có **giá trị xử lý chung** thật sự.

## 16. Naming convention và khả năng đọc mã

Một số quy ước thực dụng của Java:

1. **Tên lớp dùng PascalCase**, ví dụ `OrderNotFoundException`.
2. **Hậu tố `Exception`** giúp người đọc và IDE nhận biết loại lớp trong phân cấp ngoại lệ. Đây là quy ước rất phổ biến.
3. **Hạn chế hậu tố `Error`** cho lỗi ứng dụng bình thường; `java.lang.Error` thuộc nhánh có ngữ nghĩa khác và thường không phải cơ chế báo lỗi nghiệp vụ có thể phục hồi.
4. Dùng từ **cụ thể, được nhóm hiểu giống nhau**. Nếu domain gọi đối tượng là `Invoice`, đừng có chỗ dùng `Bill` và chỗ khác `Invoice` không nhất quán.
5. **Không mã hóa toàn bộ thông điệp lỗi** trong tên lớp, ví dụ `OrderWithIdMissingInDatabaseException`; phần message và metadata có thể mang chi tiết động.
6. Giữ tên **ổn định theo hợp đồng**. Tên gắn với vendor hoặc cơ sở dữ liệu dễ lỗi thời khi đổi nền tảng.
7. Với lỗi cùng tình huống nhưng nhiều ngữ cảnh, cân nhắc một loại exception + field/code rõ nghĩa thay vì các lớp trùng nhau chỉ khác tên thao tác.

Ngôn ngữ codebase có thể có quy tắc khác; cần thống nhất với phong cách hiện hữu, nhưng không từ bỏ tính chính xác về ý nghĩa lỗi chỉ để thỏa mẫu tên.

## 17. Khi nào cần tài liệu hóa `@throws`?

Tài liệu JavaDoc nên nói rõ **điều kiện ném**, không chỉ lặp tên exception:

~~~java
/**
 * Thanh toán một đơn đang ở trạng thái cho phép.
 *
 * @param order đơn hàng hợp lệ cần thanh toán
 * @throws OrderAlreadyPaidException nếu đơn đã được thanh toán
 */
static void pay(Order order) {
    // Thân triển khai nghiệp vụ.
}
~~~

Đây là minh họa, cần định nghĩa `Order` và `OrderAlreadyPaidException`. Tài liệu này giúp caller biết khi nào lỗi xuất hiện và liệu có nên kiểm tra trạng thái trước khi gọi hay không.

Một JavaDoc không hữu ích:

~~~java
/**
 * @throws OrderAlreadyPaidException khi có OrderAlreadyPaidException
 */
~~~

Thẻ `@throws` là **tài liệu**, còn mệnh đề `throws SomeCheckedException` trong chữ ký là **cú pháp ngôn ngữ có tác động đến compile-time checking**. Với unchecked exception, có thể tài liệu hóa bằng `@throws` ngay cả khi chữ ký không khai báo `throws`.

Cần đồng bộ tên, điều kiện trong JavaDoc, kiểu được ném trong code và test. Nếu hợp đồng đã quy định `OrderNotFoundException`, đừng triển khai một nhánh lại ném `IllegalArgumentException` cho chính trường hợp đó mà không có lý do.

## 18. Những phản ví dụ giúp nhận diện sai mức trừu tượng

### 18.1. Biến lỗi hạ tầng thành “not found”

~~~java
// Anti-pattern: không được coi lỗi truy vấn DB là bằng chứng đối tượng không tồn tại.
catch (SQLException ex) {
    throw new OrderNotFoundException("Không thấy đơn");
}
~~~

Đoạn này minh họa một quyết định **sai ý nghĩa**; nó còn phụ thuộc constructor tương ứng. Nếu truy vấn thất bại do database offline, bạn chưa xác định đơn có tồn tại không. Nên dùng `OrderLookupException`, `RepositoryUnavailableException` hoặc một loại phù hợp với contract và giữ `cause`, thay vì khẳng định `NotFound`.

### 18.2. Gộp từ chối thanh toán với timeout

~~~text
PaymentDeclinedException
    != PaymentGatewayUnavailableException
~~~

Bị từ chối là một **kết quả đã biết**; không thể liên hệ là **thiếu kết quả xác định**. Trong hệ thống thu tiền, nhầm hai loại có thể dẫn tới retry sai.

### 18.3. Lạm dụng tên `ValidationException`

`ValidationException` có thể hợp lý nếu API thống nhất nhiều lỗi validation vào một loại kèm danh sách trường/vi phạm. Nhưng nếu mọi `ValidationException` chỉ có message tự do, caller khó tách “sai email”, “thiếu mã” và “vi phạm giới hạn”. Khi cần xử lý có cấu trúc, cân nhắc object chứa danh sách violations thay vì tạo một exception subtype cho mỗi field.

### 18.4. Đổi tên hạ tầng theo từng dòng code

`JdbcQueryFailedAtLine52Exception` phản ánh vị trí triển khai dễ thay đổi, không phải abstraction ổn định. Stack trace/cause đã cung cấp vị trí kỹ thuật; tên type nên đóng góp **ý nghĩa semantic** khác.

## 19. Kiểm thử exception name như một phần hợp đồng

Đừng chỉ kiểm thử message. Nên có các kiểm thử về:

- **Đúng loại exception cho đúng điều kiện:** đơn không tồn tại → `OrderNotFoundException`, đơn đã thanh toán → `OrderAlreadyPaidException`.
- **Không phát sinh exception đó trong điều kiện khác:** database timeout không được báo “không tìm thấy đơn” nếu chưa xác minh kết quả.
- **Cause được bảo toàn:** nếu tầng service bọc `IOException`, `getCause()` phải là ngoại lệ kỹ thuật gốc (hoặc một wrapper trung gian được công bố).
- **Thông tin có cấu trúc:** nếu exception có `orderId()`, field phải khớp id đầu vào và không bị trộn với message.
- **Phân biệt checked/unchecked đúng hợp đồng:** compiler yêu cầu catch-or-declare cho checked exception; unchecked không buộc.
- **Khả năng tương thích:** khi thêm hay thay loại lỗi public API, các caller xử lý theo type có thể phải cập nhật.

Ví dụ test thủ công không cần thư viện bên ngoài:

~~~java
// Minh họa trong một phương thức có lớp OrderNamingDemo truy cập được.
try {
    OrderNamingDemo.find("B-42");
    throw new AssertionError("Dự kiến phải ném OrderNotFoundException");
} catch (OrderNamingDemo.OrderNotFoundException ex) {
    System.out.println("Đúng loại: " + ex.getClass().getSimpleName());
}
~~~

Trong source `OrderNamingDemo` của mục 6, các lớp lồng có quyền truy cập package phù hợp; đặt đoạn test cùng package hoặc điều chỉnh modifier khi tách thành API công khai. Đây là **đoạn minh họa không độc lập**, vì phụ thuộc code từ ví dụ trước.

Điểm kiểm tra là **kiểu ngoại lệ**. Nếu chỉ assert string `"Không tìm thấy"`, một loại ngoại lệ sai vẫn có thể lọt qua vì tình cờ cùng message.

## 20. Khi nào không nên tạo custom exception?

Không phải mọi tình huống không thành công đều cần `throw`. Một số lựa chọn khác:

| Tình huống | Lựa chọn có thể phù hợp | Lý do |
| --- | --- | --- |
| Tìm một đối tượng theo id, không tồn tại là chuyện thường | `Optional<Order>` hoặc kiểu kết quả | Không buộc caller vào đường exceptional cho một kết quả dự kiến |
| Phân tích dữ liệu người dùng với nhiều vi phạm | Result chứa danh sách `ValidationError` | Hiển thị nhiều lỗi cùng lúc thay vì chỉ lỗi đầu tiên |
| Dữ liệu đầu vào lập trình sai rõ ràng | `IllegalArgumentException` | Exception chuẩn đã diễn đạt precondition |
| Chỉ số ngoài giới hạn | `IndexOutOfBoundsException` | Exception chuẩn đã rõ nghĩa |
| Tác vụ I/O thông thường | `IOException` hoặc lớp con chuẩn | Không thêm wrapper nếu tầng caller đã chấp nhận hợp đồng I/O |
| Luồng nghiệp vụ có thể chờ/chọn phương án | Result object/enum có trạng thái | Caller xử lý mọi kết quả chủ động |

Custom exception phù hợp nhất khi **lỗi thật sự có tính exceptional trong hợp đồng cụ thể** và caller được lợi từ việc phân biệt bằng type hoặc metadata.

## 21. Checklist đặt tên trước khi công bố API

Trước khi tạo hoặc đổi tên exception, tự hỏi:

1. **Điều kiện chính xác nào làm loại exception này được ném?**
2. **Tên có chỉ rõ domain object hoặc thao tác thất bại không?**
3. **Tên có khiến người đọc tưởng một kết quả đã được xác định, trong khi thực tế chỉ biết thao tác không hoàn thành không?**
4. **Nó thuộc abstraction nào và caller ở tầng đó có hiểu được không?**
5. **Nó rò rỉ vendor, table, file path, HTTP status hoặc chi tiết implementation không cần thiết không?**
6. **Một exception chuẩn hay result object đã đủ chưa?**
7. **Có cần caller xử lý riêng theo type không? Nếu không, có thể gom một nhóm hợp lý không?**
8. **Nên checked hay unchecked theo nghĩa vụ của caller?**
9. **Đã bảo toàn `cause`, stack trace và các thông tin cần chẩn đoán chưa?**
10. **Thông điệp/metadata có tránh lộ dữ liệu nhạy cảm không?**
11. **JavaDoc `@throws` và kiểm thử có nói cùng một điều kiện không?**
12. **Tên và hợp đồng có còn hợp lý nếu đổi implementation?**

Nếu không thể diễn đạt điều kiện bằng một câu cụ thể như “ném khi đơn ở trạng thái PAID mà API không cho thanh toán lại”, tên custom exception có thể đang quá rộng hoặc bài toán vẫn chưa được mô hình hóa đúng.

## 22. Lỗi thường gặp

| Sai lầm | Vì sao nguy hiểm? | Cách làm tốt hơn |
| --- | --- | --- |
| Đặt `MyException`, `CustomException`, `Exception1` | Không nói được điều kiện hay abstraction | Đặt tên theo trường hợp thất bại |
| Đặt mọi lỗi là `BusinessException` | Caller phải phân tích text/code mơ hồ | Phân nhóm có ý nghĩa hoặc dùng result có cấu trúc |
| Gọi timeout là `NotFoundException` | Có thể báo sai rằng đối tượng không tồn tại | Phân biệt “không có kết quả” và “không tra cứu được” |
| Gọi mọi thất bại thanh toán là `PaymentDeclinedException` | Dễ nhầm timeout với bị từ chối | Tách trạng thái biết được và trạng thái chưa xác định |
| Bọc `IOException` mà bỏ `cause` | Mất stack trace/loại lỗi gốc | `super(message, cause)` |
| Dùng lớp exception chứa tên vendor ở domain API | Khóa abstraction vào chi tiết hạ tầng | Bọc tại boundary bằng tên ổn định |
| Tạo custom exception cho mọi `IllegalArgumentException` | API phình to mà ít lợi ích | Dùng built-in nếu đủ nghĩa |
| Chọn checked/unchecked chỉ theo sở thích | Làm caller khó xử lý hoặc bỏ qua trường hợp cần bắt | Thiết kế theo hợp đồng và đường phục hồi |
| Lạm dụng `Error` cho lỗi nghiệp vụ | Sai kỳ vọng phân cấp `Throwable` | Kế thừa `Exception`/`RuntimeException` phù hợp |
| Dùng message làm mã điều khiển | Message đổi là logic lỗi | Dùng type/code có hợp đồng |
| Bọc lỗi ở mọi tầng với message giống nhau | Cause chain dài, log nhiễu | Bọc khi có ngữ nghĩa mới |
| Tên quá cụ thể về SQL/file/line | Khó thay đổi triển khai | Diễn đạt nghĩa ở abstraction hiện tại |

## 23. Tự kiểm tra kiến thức

**Câu 1.** Khi lookup đơn thất bại vì database offline, nên ném `OrderNotFoundException` không?

**Đáp án:** Không chỉ vì query thất bại. Không tra cứu được **không chứng minh** đơn không tồn tại. Dùng loại lỗi thể hiện phụ thuộc không khả dụng hoặc thao tác lookup thất bại, phù hợp với abstraction.

**Câu 2.** `OrderAlreadyPaidException` và `OrderNotFoundException` có cùng nghĩa không?

**Đáp án:** Không. Một loại là vi phạm trạng thái thanh toán; loại kia là không tìm được đối tượng.

**Câu 3.** Tên ngoại lệ nên gắn với tên bảng SQL hay ngữ nghĩa public API?

**Đáp án:** Ở public domain/application API, ưu tiên ngữ nghĩa ổn định mà caller hiểu. Chi tiết SQL phù hợp hơn trong tầng hạ tầng hoặc cause.

**Câu 4.** Có bắt buộc mọi custom exception đều extends `RuntimeException` không?

**Đáp án:** Không. Có thể kế thừa `Exception` để tạo checked exception; lựa chọn theo hợp đồng bắt/khai báo của caller.

**Câu 5.** Vì sao `ConfigurationLoadException` có thể tốt hơn `IOException` tại một application boundary?

**Đáp án:** Nó giải thích **thao tác nạp cấu hình** thất bại, không buộc caller hiểu nguồn cấu hình là tệp, mạng hay lưu trữ khác; `IOException` có thể được giữ làm cause.

**Câu 6.** Nếu không có caller nào xử lý `InvalidPageSizeException` khác với `IllegalArgumentException`, có nên tạo lớp riêng không?

**Đáp án:** Thường không cần, trừ khi có hợp đồng hoặc metadata đặc biệt khác làm kiểu riêng hữu ích.

**Câu 7.** Thông điệp `ex.getMessage()` có nên dùng làm mã lỗi bền vững không?

**Đáp án:** Không. Nên dùng exception type hoặc error code được tài liệu hóa khi cần phân loại.

**Câu 8.** Khi bọc `IOException`, constructor custom exception nên có gì?

**Đáp án:** Nên nhận `message` và `cause`, chuyển vào `super(message, cause)` để bảo toàn thông tin chẩn đoán.

**Câu 9.** Một exception có tên theo thao tác như `ReportGenerationException` luôn là lựa chọn sai không?

**Đáp án:** Không. Nó phù hợp khi failure ở mức thao tác đó là đơn vị trừu tượng caller cần xử lý. Tên chung `OperationException` mới thường khó dùng.

**Câu 10.** Nếu một API coi “đã thanh toán” là kết quả idempotent thành công, có nhất thiết ném `OrderAlreadyPaidException` không?

**Đáp án:** Không. Khi tình huống là kết quả hợp lệ theo hợp đồng, có thể trả trạng thái phù hợp. Lựa chọn exception phụ thuộc vào ngữ nghĩa API.

## 24. Tài liệu tham khảo

- [Java SE 17 API — `Exception`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Exception.html): constructor exception checked và mối liên hệ với `Throwable`.
- [Java SE 17 API — `RuntimeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): unchecked exception, constructor message/cause.
- [Java SE 17 API — `IllegalArgumentException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalArgumentException.html) và [`IllegalStateException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalStateException.html): hai loại chuẩn phổ biến có thể dùng thay vì tự tạo lớp.
- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, stack trace, suppressed exceptions và phương thức chẩn đoán.
- [Java Language Specification 17 — §11, Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): quy tắc ngoại lệ checked/unchecked và xử lý.
- [Java Language Specification 17 — §8.4.6, Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): mệnh đề `throws` của phương thức.

**Ghi nhớ:** custom exception nên được đặt tên theo **điều kiện thất bại có giá trị với caller** và **mức abstraction công bố lỗi**. Dùng tên riêng khi nó giúp phân loại/hành động; dùng exception chuẩn khi đã đủ; tránh tên mơ hồ hoặc rò rỉ hạ tầng; và giữ `cause` khi bọc để không mất nguyên nhân kỹ thuật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-004">Constructor hỗ trợ message và cause</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor hỗ trợ `message` và `cause` khi thiết kế ngoại lệ tùy chỉnh trong Java

Khi tạo một lớp ngoại lệ tùy chỉnh (*custom exception*), việc kế thừa `Exception` hay `RuntimeException` chỉ là bước đầu. Muốn người gọi hiểu **điều gì thất bại** và người vận hành truy được **nguyên nhân kỹ thuật ban đầu**, ngoại lệ nên có constructor nhận **message** và **cause**:

```java
public DataLoadException(String message, Throwable cause) {
    super(message, cause);
}
```

Hai tham số có nhiệm vụ khác nhau: `message` giải thích lỗi trong ngữ cảnh của **lớp đang báo lỗi**, còn `cause` tham chiếu đến **đối tượng `Throwable` đã khiến thao tác thất bại**. Khi giữ cả hai, chương trình có thể tạo thêm thông tin ở mỗi tầng mà không mất stack trace, loại ngoại lệ và suppressed exceptions của lỗi gốc.

Bài viết tập trung vào cách thiết kế constructor cho ngoại lệ tùy chỉnh trong Java 17+, các overload thường dùng, cách truyền `super(...)`, sự khác biệt giữa `message` và `cause`, checked/unchecked exception, `null`, `initCause()`, cách bảo toàn chẩn đoán qua nhiều tầng và các lỗi triển khai phổ biến.

## 1. `message` và `cause` không phải cùng một thứ

`Throwable` cung cấp hai thuộc tính chẩn đoán cơ bản:

| Thuộc tính | API đọc | Ý nghĩa | Ví dụ |
| --- | --- | --- | --- |
| *Detail message* | `getMessage()` | Mô tả lỗi của **đối tượng ngoại lệ hiện tại** | `Cannot load customer profile` |
| *Cause* | `getCause()` | Đối tượng ngoại lệ trước đó, trực tiếp góp phần gây ra ngoại lệ hiện tại | `IOException("disk unavailable")` |
| Stack trace | `getStackTrace()` / `printStackTrace()` | Dấu vết nơi thông tin lỗi được ghi lại | Frame ở repository/service |
| Suppressed exceptions | `getSuppressed()` | Lỗi phụ được giữ lại, thường xảy ra khi đóng tài nguyên | `IOException("close failed")` |

Ví dụ giả lập chuỗi lỗi:

```text
DataLoadException("Cannot load customer profile")   ← message tầng service
    |
    +-- cause: IOException("disk unavailable")      ← nguyên nhân ở tầng I/O
                     |
                     +-- stack trace: thao tác đọc tệp
```

Lỗi `IOException` vẫn là **một đối tượng riêng**: nó không biến thành `DataLoadException`. Constructor nhận `cause` chỉ tạo **mối liên kết nguyên nhân**, để người dùng `getCause()` và công cụ in stack trace có thể đi từ lỗi nghiệp vụ về lỗi hạ tầng.

**Thông điệp không tự động thay thế nguyên nhân.** Viết `super("Cannot load: " + ex.getMessage())` có thể giúp người đọc thấy một câu mô tả, nhưng `getCause()` của ngoại lệ mới vẫn bằng `null` và mối liên kết đến đối tượng `ex` bị mất.

## 2. Chọn lớp cha: checked hay unchecked?

Một custom exception thường kế thừa một trong hai lớp:

- **`Exception`** (không kế thừa `RuntimeException`): checked exception. Nếu phương thức có khả năng để lỗi ấy thoát ra, Java thường buộc bên gọi xử lý bằng `catch` hoặc khai báo `throws` phù hợp.
- **`RuntimeException`**: unchecked exception. Không bắt buộc khai báo `throws` để biên dịch, nhưng vẫn có thể và thường nên dùng `cause` để bảo toàn lỗi gốc.

Ví dụ chữ ký:

```java
class DataLoadException extends Exception {
    DataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

class ServiceFailureException extends RuntimeException {
    ServiceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là hai lớp minh họa hợp lệ, không có `main` độc lập. Hai kiểu có cùng constructor nhưng **hợp đồng với bên gọi** khác nhau. Không chọn checked hay unchecked chỉ vì muốn viết ít mã `try-catch` hơn; hãy dựa vào khả năng phục hồi, hợp đồng API và quy tắc thiết kế của module.

Không nên tạo một ngoại lệ nghiệp vụ bình thường bằng `extends Error`. Nhánh `Error` biểu diễn các trường hợp mà ứng dụng thường không được kỳ vọng phục hồi theo cách thông thường.

## 3. Bốn constructor phổ biến của custom exception

Một ngoại lệ có thể cung cấp bốn overload, tương ứng các cách tạo đối tượng phổ biến trong Java:

```java
public class DataLoadException extends Exception {
    public DataLoadException() {
        super();
    }

    public DataLoadException(String message) {
        super(message);
    }

    public DataLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataLoadException(Throwable cause) {
        super(cause);
    }
}
```

Đây là **tệp lớp hợp lệ** nếu lưu thành `DataLoadException.java`; nó không chứa `main()`. Các overload có vai trò riêng:

| Constructor | Cách dùng điển hình | `getMessage()` | `getCause()` |
| --- | --- | --- | --- |
| `DataLoadException()` | Lỗi đơn giản không có ngữ cảnh chi tiết | `null` | `null` |
| `DataLoadException(String message)` | Báo lỗi mới độc lập | Chuỗi `message` | `null` |
| `DataLoadException(String message, Throwable cause)` | Dịch ngoại lệ tại ranh giới tầng, thêm bối cảnh | Chính chuỗi `message` được truyền | Chính đối tượng `cause` |
| `DataLoadException(Throwable cause)` | Wrapper gần như chỉ bao nguyên nhân | `cause == null ? null : cause.toString()` | Chính đối tượng `cause` |

Có thể không cần đủ bốn overload nếu API chỉ có một hoặc hai nhu cầu tạo lỗi rõ ràng. Tuy nhiên, khi xuất bản một thư viện cho nhiều bên dùng, cung cấp các overload có ý nghĩa giúp người gọi không phải gắn `cause` muộn hoặc tự xây message vụng về.

**Lưu ý:** Constructor `DataLoadException(Throwable cause)` không tạo một message nghiệp vụ mới. Theo hợp đồng `Exception(Throwable cause)`, message được dẫn xuất từ `cause.toString()`. Nếu cần hiển thị ngữ cảnh có ý nghĩa, thường ưu tiên overload `(String, Throwable)`.

## 4. Chương trình Java 17+: kiểm tra chính xác từng overload

Lưu thành `ConstructorOverloadsDemo.java`:

```java
import java.io.IOException;

public class ConstructorOverloadsDemo {
    static class DomainException extends Exception {
        DomainException() {
            super();
        }

        DomainException(String message) {
            super(message);
        }

        DomainException(String message, Throwable cause) {
            super(message, cause);
        }

        DomainException(Throwable cause) {
            super(cause);
        }
    }

    public static void main(String[] args) {
        IOException original = new IOException("disk offline");

        DomainException empty = new DomainException();
        DomainException messageOnly =
                new DomainException("cannot load profile");
        DomainException both =
                new DomainException("cannot load profile", original);
        DomainException causeOnly = new DomainException(original);

        System.out.println("emptyMessage=" + empty.getMessage());
        System.out.println("emptyCause=" + empty.getCause());

        System.out.println("messageOnly=" + messageOnly.getMessage());
        System.out.println("messageOnlyCause=" + messageOnly.getCause());

        System.out.println("bothMessage=" + both.getMessage());
        System.out.println("bothCauseIsOriginal="
                + (both.getCause() == original));

        System.out.println("causeOnlyMessage=" + causeOnly.getMessage());
        System.out.println("causeOnlyIsOriginal="
                + (causeOnly.getCause() == original));
    }
}
```

Biên dịch và chạy từ thư mục có tệp:

```bash
javac -encoding UTF-8 ConstructorOverloadsDemo.java
java ConstructorOverloadsDemo
```

Kết quả xác định:

```text
emptyMessage=null
emptyCause=null
messageOnly=cannot load profile
messageOnlyCause=null
bothMessage=cannot load profile
bothCauseIsOriginal=true
causeOnlyMessage=java.io.IOException: disk offline
causeOnlyIsOriginal=true
```

Ở dạng `(String, Throwable)`, Java **không tự chèn** message từ cause vào message của wrapper. Ta chủ động quyết định câu mô tả có ý nghĩa ở tầng hiện tại. Ở dạng `(Throwable)`, message mặc định được lấy từ `cause.toString()`, bao gồm tên lớp `java.io.IOException` và message của cause trong ví dụ.

Lệnh so sánh `==` chứng minh wrapper giữ tham chiếu đến **chính đối tượng nguyên nhân ban đầu**, không tự tạo bản sao của `IOException`.

## 5. Cách viết `super(message, cause)` đúng

Constructor là nơi khởi tạo đối tượng ngoại lệ và phải gọi constructor của lớp cha theo quy tắc Java. Trong Java 17, lời gọi constructor tường minh `super(...)` hoặc `this(...)` phải là câu lệnh đầu tiên của constructor.

Mẫu chuẩn:

```java
public ConfigLoadException(String message, Throwable cause) {
    super(message, cause);
}
```

Đoạn trên là **constructor minh họa** của một lớp `ConfigLoadException` kế thừa `Exception` hoặc `RuntimeException` có overload tương ứng.

Nếu muốn chuẩn hóa message trước khi chuyển cho lớp cha, có thể dùng biểu thức trong tham số:

```java
public ConfigLoadException(String key, Throwable cause) {
    super("Unable to load config key: " + key, cause);
}
```

Trong thiết kế API thật, cần cân nhắc xem `key` có chứa dữ liệu nhạy cảm không trước khi đưa vào message và log.

**Không viết**:

```java
public ConfigLoadException(String message, Throwable cause) {
    super(message);
    initCause(cause);
}
```

Mã trên có thể hợp lệ trong một lớp kế thừa `Exception` nếu cause chưa được khởi tạo, nhưng đây là cách vòng vèo không cần thiết: khi đã có `super(message, cause)` thì nên dùng trực tiếp nó. Nếu `initCause` bị gọi thêm một lần ở nơi khác, hợp đồng khởi tạo cause một lần sẽ bị vi phạm.

**Đặc biệt không gọi cả `super(message, cause)` và `initCause(cause)`:** cause đã được khởi tạo qua constructor hai tham số; lần gọi thêm ném `IllegalStateException`.

## 6. Trường hợp `null` và trạng thái khởi tạo cause

Đây là một khác biệt tinh tế giữa các constructor:

- `super(message)` tạo đối tượng có cause **chưa được khởi tạo**. Sau đó `initCause(...)` có thể được gọi một lần.
- `super(message, null)` tạo đối tượng có cause **đã được khởi tạo thành `null`**. Sau đó `initCause(...)` **không** được phép gọi lại.
- `super(cause)` nhận `null` hợp lệ, message được suy ra thành `null` nếu cause bằng `null`; cause vẫn được coi là đã khởi tạo.
- `getCause() == null` **không cho biết** cause đã được khởi tạo hay chưa.

| Cách tạo | `getCause()` ban đầu | Có thể `initCause(realCause)` sau đó? |
| --- | --- | --- |
| `new Exception("message")` | `null` | Có, một lần nếu chưa dùng |
| `new Exception("message", null)` | `null` | Không |
| `new Exception((Throwable) null)` | `null` | Không |
| `new Exception("message", original)` | `original` | Không |

Vì cả `String` và `Throwable` đều có overload một tham số, câu lệnh `new Exception(null)` có thể **mơ hồ lúc biên dịch**. Khi thật sự cần truyền `null`, dùng ép kiểu tường minh `(String) null` hoặc `(Throwable) null`, hoặc constructor hai tham số như `new Exception("message", null)` để nói rõ ý định.

### Chương trình Java 17+: `null` không đồng nghĩa “chưa khởi tạo cause”

Lưu thành `NullCauseConstructorDemo.java`:

```java
public class NullCauseConstructorDemo {
    static class SampleException extends Exception {
        SampleException(String message) {
            super(message);
        }

        SampleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) {
        SampleException messageOnly = new SampleException("one");
        messageOnly.initCause(new IllegalStateException("original"));
        System.out.println("firstLinked="
                + (messageOnly.getCause() instanceof IllegalStateException));

        SampleException explicitNull =
                new SampleException("two", null);
        System.out.println("nullCause=" + explicitNull.getCause());

        try {
            explicitNull.initCause(new IllegalStateException("late"));
        } catch (IllegalStateException ex) {
            System.out.println("initAgain="
                    + ex.getClass().getSimpleName());
        }
    }
}
```

```bash
javac -encoding UTF-8 NullCauseConstructorDemo.java
java NullCauseConstructorDemo
```

Kết quả:

```text
firstLinked=true
nullCause=null
initAgain=IllegalStateException
```

Đừng xây ứng dụng dựa trên việc tự do gắn cause sau nhiều tầng. Mẫu dễ hiểu nhất là tạo wrapper với đúng message và cause ngay lúc bắt lỗi:

```java
throw new DataLoadException("Cannot load profile", original);
```

Đây là đoạn minh họa khi `original` là một `Throwable` và `DataLoadException` có constructor `(String, Throwable)`.

## 7. Checked exception qua nhiều tầng: đặt ngữ cảnh vào wrapper

Giả sử tầng repository gặp `IOException`, tầng service muốn cung cấp một ngoại lệ nghiệp vụ kiểm tra được (*checked*). Một lớp custom có constructor `(String, Throwable)` là cách trình bày ý nghĩa lỗi của tầng service mà không bắt bên gọi phụ thuộc vào ngoại lệ hạ tầng.

Lưu thành `CheckedTranslationDemo.java`:

```java
import java.io.IOException;

public class CheckedTranslationDemo {
    static class ProfileLoadException extends Exception {
        ProfileLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String repository() throws IOException {
        throw new IOException("file read failed");
    }

    static String service() throws ProfileLoadException {
        try {
            return repository();
        } catch (IOException ex) {
            throw new ProfileLoadException(
                    "Could not load user profile", ex);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(service());
        } catch (ProfileLoadException ex) {
            System.out.println("type=" + ex.getClass().getSimpleName());
            System.out.println("message=" + ex.getMessage());
            System.out.println("causeType="
                    + ex.getCause().getClass().getSimpleName());
            System.out.println("causeMessage=" + ex.getCause().getMessage());
        }
        System.out.println("end");
    }
}
```

```bash
javac -encoding UTF-8 CheckedTranslationDemo.java
java CheckedTranslationDemo
```

Kết quả:

```text
type=ProfileLoadException
message=Could not load user profile
causeType=IOException
causeMessage=file read failed
end
```

Cơ chế:

1. `repository()` tạo/ném `IOException`.
2. `service()` bắt lỗi hạ tầng và **tạo đối tượng mới** `ProfileLoadException` với message nghiệp vụ.
3. Constructor `super(message, cause)` giữ `IOException` thành nguyên nhân của wrapper.
4. Vì `ProfileLoadException extends Exception`, `service()` khai báo `throws ProfileLoadException`. Tầng `main()` bắt loại đó.
5. `main()` có thể tiếp tục sau `catch` vì lỗi đã được xử lý ở ranh giới phù hợp.

**Không phải mọi tầng đều nên bọc ngoại lệ một lần nữa.** Nếu tầng trung gian chưa có ngữ cảnh mới, lan truyền nguyên ngoại lệ có thể tốt hơn tạo nhiều lớp wrapper giống nhau.

## 8. Custom unchecked exception cũng cần constructor nhận cause

Ngoại lệ unchecked không yêu cầu `throws` ở bên gọi, nhưng việc giữ cause vẫn rất quan trọng. Một lỗi về cấu hình hoặc trạng thái dịch vụ có thể cần được biểu diễn bởi kiểu domain-specific unchecked exception thay vì lộ `IOException` tới tầng business.

Lưu thành `UncheckedTranslationDemo.java`:

```java
import java.io.IOException;

public class UncheckedTranslationDemo {
    static class ConfigurationFailure extends RuntimeException {
        ConfigurationFailure(String message) {
            super(message);
        }

        ConfigurationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String loadConfig() throws IOException {
        throw new IOException("permission denied");
    }

    static String initializeService() {
        try {
            return loadConfig();
        } catch (IOException ex) {
            throw new ConfigurationFailure(
                    "Service configuration unavailable", ex);
        }
    }

    public static void main(String[] args) {
        try {
            initializeService();
        } catch (ConfigurationFailure ex) {
            System.out.println("unchecked="
                    + (ex instanceof RuntimeException));
            System.out.println("message=" + ex.getMessage());
            System.out.println("causeIsIo="
                    + (ex.getCause() instanceof IOException));
        }
    }
}
```

```bash
javac -encoding UTF-8 UncheckedTranslationDemo.java
java UncheckedTranslationDemo
```

Kết quả:

```text
unchecked=true
message=Service configuration unavailable
causeIsIo=true
```

`initializeService()` không cần `throws ConfigurationFailure` vì đó là unchecked exception. Điều này **không đồng nghĩa** ngoại lệ không thể khiến chương trình kết thúc đột ngột. Nếu không có `catch` phù hợp, nó vẫn tiếp tục lan truyền theo cơ chế ngoại lệ Java.

Chỉ nên chọn unchecked nếu hợp đồng module thực sự muốn biểu thị lỗi không bắt buộc xử lý tại mọi nơi; không tự động bọc mọi checked exception thành unchecked để tránh quy tắc của trình biên dịch.

## 9. `cause` là tham chiếu tới đối tượng gốc, không phải một bản sao

Khi gọi:

```java
IOException original = new IOException("disk offline");
DataLoadException wrapper =
        new DataLoadException("Cannot load profile", original);
```

đối tượng `wrapper.getCause()` là **chính `original`**. Java không copy thông điệp, stack trace và suppressed exceptions thành một bản `IOException` khác khi dùng constructor `(String, Throwable)`.

Hệ quả:

- `wrapper.getMessage()` mô tả lỗi ở tầng wrapper; không phải tên lỗi gốc.
- `wrapper.getCause().getMessage()` mô tả lỗi gốc, nếu `getCause()` khác `null`.
- `wrapper.getStackTrace()` thường ghi nơi wrapper được tạo.
- `wrapper.getCause().getStackTrace()` thường ghi nơi đối tượng cause đã được tạo hoặc nơi trace của nó được cập nhật.
- Danh sách suppressed của cause **vẫn gắn với cause**, không tự được chuyển sang wrapper.

### Chương trình Java 17+: giữ cả suppressed khi bọc ngoại lệ

Lưu thành `SuppressedCauseDemo.java`:

```java
import java.io.IOException;

public class SuppressedCauseDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static class DataLoadException extends Exception {
        DataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void repository() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("read failed");
        }
    }

    static void service() throws DataLoadException {
        try {
            repository();
        } catch (IOException ex) {
            throw new DataLoadException("Data access failed", ex);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (DataLoadException ex) {
            Throwable cause = ex.getCause();

            System.out.println("wrapper=" + ex.getMessage());
            System.out.println("wrapperSuppressed="
                    + ex.getSuppressed().length);
            System.out.println("cause=" + cause.getMessage());
            System.out.println("causeSuppressed="
                    + cause.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 SuppressedCauseDemo.java
java SuppressedCauseDemo
```

Kết quả:

```text
wrapper=Data access failed
wrapperSuppressed=0
cause=read failed
causeSuppressed=close failed
```

Lỗi từ thân `try` là primary của `IOException`; lỗi `close()` được suppress vào chính đối tượng đó. Khi service tạo `DataLoadException(..., ex)`, wrapper **không tự sao chép** suppressed array của cause. Nhưng vì cause được giữ nguyên, vẫn truy được lỗi phụ từ `ex.getCause().getSuppressed()`.

Khi log, nên chuyển cả đối tượng `Throwable` cho logger để công cụ có thể in message, trace, cause và suppressed, thay vì chỉ ghi `getMessage()` rồi làm mất quan hệ này.

## 10. Tại sao không nên chỉ nhận `String message` rồi tự nối tên lỗi gốc?

Mẫu dễ gặp nhưng thiếu thông tin:

```java
catch (IOException ex) {
    throw new DataLoadException(
            "Unable to load: " + ex.getMessage());
}
```

Đây là đoạn **minh họa**, giả định `DataLoadException(String)` có sẵn. Người đọc thấy văn bản mô tả, nhưng `getCause()` là `null` và không còn liên kết lập trình được tới `ex`.

Cách nên dùng:

```java
catch (IOException ex) {
    throw new DataLoadException(
            "Unable to load customer profile", ex);
}
```

Ngoại lệ gốc vẫn tồn tại như cause; nếu thực sự muốn bổ sung message của `ex` vào phần mô tả cho con người thì có thể làm, nhưng cần cân nhắc lặp thông tin trong log và dữ liệu nhạy cảm.

Một nguyên tắc về message:

- **Mô tả ngữ cảnh ở tầng hiện tại**: thao tác nào thất bại và đối tượng miền nghiệp vụ nào bị ảnh hưởng.
- **Không lộ bí mật**: tránh mật khẩu, token, chuỗi kết nối, thông tin định danh nhạy cảm.
- **Không thay cause bằng message**: cause giữ loại, stack trace và thông tin nguyên nhân gốc.
- **Giữ message ổn định về ý nghĩa** cho việc đọc log, nhưng đừng yêu cầu code nghiệp vụ parse message text để xác định mã lỗi.

Nếu bên gọi phải đưa ra quyết định dựa trên loại lỗi, cung cấp kiểu ngoại lệ, enum mã lỗi hoặc thuộc tính có kiểu thích hợp thay vì cố phân tích một chuỗi `getMessage()`.

## 11. `getMessage()` khác `toString()` và `printStackTrace()`

Đừng nhầm các API chẩn đoán:

| API | Thông tin chính |
| --- | --- |
| `getMessage()` | Chỉ detail message của đối tượng hiện tại, có thể `null` |
| `toString()` | Thông thường tên lớp lỗi và detail message nếu có |
| `getCause()` | Đối tượng cause hoặc `null` |
| `printStackTrace()` | Biểu diễn chẩn đoán gồm đối tượng lỗi, các frame, chuỗi cause và suppressed khi có |
| `getSuppressed()` | Mảng các ngoại lệ phụ của chính đối tượng đó |

Chương trình nhỏ để so sánh, lưu thành `MessageDisplayDemo.java`:

```java
import java.io.IOException;

public class MessageDisplayDemo {
    static class ImportFailure extends Exception {
        ImportFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) {
        IOException original = new IOException("invalid bytes");
        ImportFailure wrapper =
                new ImportFailure("Cannot import document", original);

        System.out.println("message=" + wrapper.getMessage());
        System.out.println("simpleType="
                + wrapper.getClass().getSimpleName());
        System.out.println("causeMessage="
                + wrapper.getCause().getMessage());
        System.out.println("causeType="
                + wrapper.getCause().getClass().getSimpleName());
    }
}
```

```bash
javac -encoding UTF-8 MessageDisplayDemo.java
java MessageDisplayDemo
```

Kết quả:

```text
message=Cannot import document
simpleType=ImportFailure
causeMessage=invalid bytes
causeType=IOException
```

Ví dụ tránh so sánh nguyên văn `printStackTrace()` vì số dòng nguồn, tên file và số frame phụ thuộc nơi biên dịch/chạy. Thay vào đó, kiểm tra những thuộc tính xác định mà API cam kết. Nếu cần in trace thực tế, dùng `wrapper.printStackTrace()` ở nơi phù hợp và nhớ rằng overload không tham số ghi vào `System.err`.

## 12. Constructor có trường dữ liệu nghiệp vụ: lưu thêm thông tin mà không bỏ cause

Có khi message không đủ để code xử lý: ví dụ ngoại lệ cần mang `resourceName` hoặc `errorCode`. Có thể thêm trường bất biến, nhưng constructor vẫn nên truyền cause cho lớp cha.

```java
public final class RecordValidationException extends Exception {
    private final String field;

    public RecordValidationException(
            String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
```

Đây là **lớp hợp lệ** nếu lưu trong `RecordValidationException.java`; không có `main()`. Bên gọi có thể truy:

```java
RecordValidationException failure =
        new RecordValidationException(
                "email", "Email validation failed", original);

System.out.println(failure.getField());
System.out.println(failure.getCause() == original);
```

Đây là đoạn sử dụng minh họa khi `original` đã được định nghĩa là một `Throwable` trong scope. Cả thuộc tính có kiểu và cause được giữ riêng.

Nếu lỗi đơn giản chỉ vì đầu vào không hợp lệ, có thể không có cause nào; không cần tạo một `Throwable` giả để điền vào. Khi đó truyền `null` vào overload `(String, Throwable)` là hợp lệ, hoặc cung cấp overload `(String)` nếu hợp đồng cần.

**Thông điệp không nên là nguồn dữ liệu duy nhất** cho những quyết định như retry, HTTP status, field bị lỗi hoặc loại khắc phục. Những giá trị dùng trong logic nên được cung cấp qua kiểu dữ liệu ổn định.

## 13. Sai lầm trong constructor chaining

Có thể dùng `this(...)` để giảm trùng mã, nhưng phải hiểu việc nó chọn constructor nào.

Ví dụ:

```java
class ValidationFailure extends Exception {
    ValidationFailure(String message) {
        this(message, null);
    }

    ValidationFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **mã hợp lệ**. Tuy nhiên, nó **không hoàn toàn giống** constructor `ValidationFailure(String message) { super(message); }` về trạng thái cause:

- Với `this(message, null)`, constructor hai tham số chạy `super(message, null)` và **đã khởi tạo cause thành `null`**.
- Với `super(message)`, cause chưa được khởi tạo và `initCause(...)` có thể được gọi một lần sau đó.

Nếu lớp được thiết kế để mọi cause phải được quyết định ngay khi tạo đối tượng, gọi `this(message, null)` có thể là lựa chọn có chủ đích. Nếu muốn giữ đúng ngữ nghĩa constructor `Exception(String)` tiêu chuẩn, viết `super(message)` trực tiếp.

**Tránh gọi `super(...)` và `this(...)` trong cùng một constructor**: mỗi constructor chỉ được chọn một constructor invocation tường minh tương ứng theo cú pháp. Không thể gọi cả hai như các hàm thông thường.

### Ví dụ lỗi do gọi `initCause` sau khi đã dùng constructor có cause

```java
class WrongFailure extends Exception {
    WrongFailure(String message, Throwable cause) {
        super(message, cause);
        initCause(cause); // Chạy tới đây sẽ ném IllegalStateException.
    }
}
```

Đây là **mã biên dịch được nhưng constructor thất bại lúc chạy**. Lý do: `super(message, cause)` đã khởi tạo cause; `initCause` không cho khởi tạo lần thứ hai. Sửa bằng cách **xóa `initCause(cause)`**.

Nếu viết `super(message)` rồi `initCause(cause)`, mã có thể hoạt động lần đầu nhưng dài và dễ bị thay đổi sai; khi đã có overload hai tham số, không có lợi thế rõ ràng so với `super(message, cause)`.

## 14. Một số giới hạn cần kiểm tra khi triển khai

### 14.1. Cause không thể là chính đối tượng wrapper

Một ngoại lệ không thể dùng chính nó làm cause qua `initCause(this)`. Điều này bị từ chối bằng `IllegalArgumentException`. Khi viết constructor nhận `Throwable cause`, hãy truyền **đối tượng ngoại lệ gốc khác với wrapper đang được tạo**, không cố tự liên kết đối tượng với chính nó để giữ stack trace.

Trong thực tế, một tham số `cause` bình thường truyền từ `catch (Exception ex)` vào constructor `new DomainException(..., ex)` là đối tượng khác, nên hợp lệ.

### 14.2. Cause có thể bằng `null`

`super(message, null)` hợp lệ: nghĩa là chưa có nguyên nhân được biết hoặc không có một ngoại lệ cụ thể gây ra lỗi. Nhưng nó vẫn khởi tạo cause **một lần**. Không dùng `getCause() == null` để kết luận `initCause(...)` có thể gọi lại.

### 14.3. Không tạo vòng cause hoặc chain vô nghĩa

Không nên dùng cause để lưu một ngoại lệ hoàn toàn không liên quan, hoặc tạo chu trình tham chiếu qua nhiều đối tượng. Điều đó có thể làm stack trace khó đọc và phá ý nghĩa “gây ra lỗi này”. Lỗi phụ xảy ra khi cleanup nên được biểu diễn bằng suppressed exceptions trong các ngữ cảnh thích hợp, không ép tất cả thành cause.

### 14.4. Không tự động vô hiệu hóa stack trace

Các lớp `Exception` và `RuntimeException` có constructor protected mở rộng hỗ trợ `enableSuppression` và `writableStackTrace`. Đây là công cụ nâng cao. Tắt stack trace hoặc suppression có thể giảm lượng thông tin chẩn đoán quan trọng; không nên xem đó là mặc định cho mọi custom exception.

### 14.5. `serialVersionUID` là mối quan tâm riêng

Các ngoại lệ Java kế thừa `Throwable` và có khả năng tham gia Java serialization. Nếu một custom exception là phần của hợp đồng serialization lâu dài, cân nhắc `serialVersionUID` và khả năng tương thích phiên bản. Nhưng đây là vấn đề thiết kế serialization, **không phải điều kiện để constructor `(String, Throwable)` hoạt động**.

## 15. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Sửa như thế nào |
| --- | --- | --- |
| Chỉ viết `MyException(String message)` trong khi phải chuyển nguyên nhân gốc. | Mất cause khi chuyển đổi lỗi. | Thêm `MyException(String, Throwable)` và gọi `super(message, cause)`. |
| Bọc lỗi bằng `new MyException(ex.getMessage())`. | Chỉ sao chép text, mất liên kết tới `ex`. | Truyền `ex` làm cause. |
| Viết `super(cause)` dù cần message theo ngữ cảnh tầng hiện tại. | Message chỉ là `cause.toString()`. | Dùng `super("Context...", cause)`. |
| Gọi `initCause()` sau `super(message, cause)`. | `IllegalStateException` lúc chạy. | Không gọi `initCause` nữa. |
| Xem `getCause() == null` như “chưa từng khởi tạo cause”. | Có thể gặp lỗi khi khởi tạo lần hai. | Quyết định cause ngay ở constructor. |
| Dùng `new MyException(null)` khi có overload `String` và `Throwable`. | Mơ hồ lúc biên dịch. | Dùng ép kiểu hoặc overload hai tham số rõ ràng. |
| Ném `new MyException("failure", null)` cho mọi trường hợp kể cả khi có ngoại lệ gốc. | Cause thật bị bỏ qua. | Chỉ dùng `null` khi không có cause thích hợp. |
| Bọc nhiều tầng mà không thêm thông tin mới. | Chain dài, log khó đọc. | Chỉ chuyển loại khi vượt qua ranh giới hợp đồng có ý nghĩa. |
| Đưa token, mật khẩu hoặc thông tin nhạy cảm vào message. | Rủi ro rò rỉ qua log/trace. | Message có ngữ cảnh nhưng không chứa bí mật. |
| Chỉ kiểm tra `getMessage()`, không đọc cause và suppressed. | Bỏ qua nguyên nhân kỹ thuật và lỗi đóng. | Log đối tượng `Throwable` và truy chain khi cần. |
| Dùng checked exception nhưng quên `throws` ở tầng gọi. | Không biên dịch. | Khai báo `throws` phù hợp hoặc xử lý tại `catch`. |
| Dùng unchecked cho mọi lỗi chỉ để bớt khai báo. | Hợp đồng lỗi khó rõ ràng. | Chọn checked/unchecked dựa vào cách bên gọi xử lý. |
| Định nghĩa constructor tên khác tên lớp hoặc thêm kiểu trả về. | Không phải constructor hợp lệ. | Constructor trùng tên lớp, không có kiểu trả về. |

## 16. Quy trình thiết kế một custom exception có message và cause

Khi xây một lớp mới, có thể làm theo các bước sau:

1. **Xác định ý nghĩa lỗi:** lỗi cần đại diện cho sự kiện nào ở module? Đã có ngoại lệ chuẩn nào đủ phù hợp chưa?
2. **Chọn checked/unchecked:** bên gọi có được kỳ vọng phục hồi không, API cần yêu cầu xử lý lúc biên dịch hay không?
3. **Chọn các overload cần thiết:** tối thiểu `(String, Throwable)` nếu có chuyển đổi lỗi; thêm `(String)` nếu có những lỗi độc lập không có cause.
4. **Gọi constructor lớp cha đúng:** `super(message, cause)`, `super(message)` hoặc `super(cause)` tương ứng.
5. **Giữ ngữ cảnh:** tạo message diễn đạt **thao tác ở tầng hiện tại** thay vì chỉ lặp lại message của nguyên nhân.
6. **Giữ object cause:** truyền ngoại lệ bắt được, không tự tạo bản sao hoặc chuỗi thay thế.
7. **Kiểm tra `null`:** có cho phép cause thiếu không? Nếu có, chỉ gắn `null` khi thực sự không có lỗi gốc.
8. **Kiểm tra contract:** checked exception có được `throws` hoặc `catch` đúng không? Trong override/interface có làm rộng thêm checked exceptions không?
9. **Kiểm tra chẩn đoán:** khi in log còn thấy cause chain và suppressed exceptions của lỗi gốc không?
10. **Đánh giá phạm vi thông tin:** message có lộ dữ liệu nhạy cảm hoặc khó dùng cho vận hành không?

Một thiết kế nhỏ, minh bạch thường tốt hơn nhiều constructor với hành vi tùy biến không nhất quán. Chỉ thêm trường nghiệp vụ, mã lỗi hoặc logic xử lý khi có nhu cầu thực sự.

## 17. Tự kiểm tra

**Câu 1.** Vì sao `super(message, cause)` tốt hơn `super(message + cause.getMessage())` khi chuyển đổi ngoại lệ?  
Vì cách đầu **giữ đối tượng cause** cùng stack trace và thông tin phụ; cách sau chỉ ghép chuỗi, có thể mất liên kết và còn có nguy cơ NPE khi cause bằng `null`.

**Câu 2.** Constructor `MyException(Throwable cause) { super(cause); }` tạo message gì?  
Thông thường `cause.toString()`, hoặc `null` nếu cause là `null`, theo hợp đồng của lớp cha.

**Câu 3.** `new Exception("failed", null)` có hợp lệ không?  
**Có.** Cause bằng `null` được phép và được xem là đã khởi tạo.

**Câu 4.** Sau `super(message, null)`, có thể gọi `initCause(realCause)` không?  
**Không.** Cause đã được khởi tạo bằng constructor hai tham số.

**Câu 5.** Hai constructor `MyException(String)` và `MyException(Throwable)` tồn tại; `new MyException(null)` có luôn biên dịch không?  
**Không.** Lời gọi có thể mơ hồ vì `null` phù hợp cả `String` và `Throwable` mà hai kiểu không có quan hệ kế thừa trực tiếp.

**Câu 6.** Custom exception kế thừa `RuntimeException` có được dùng constructor nhận cause không?  
**Có.** Khác biệt checked/unchecked không làm mất cơ chế chaining.

**Câu 7.** Khi wrapper đã có cause, suppressed exceptions của cause có tự được copy lên wrapper không?  
**Không.** Chúng vẫn ở đối tượng cause; có thể đọc qua `wrapper.getCause().getSuppressed()` khi cause không `null`.

**Câu 8.** Tại sao không nên bắt mọi ngoại lệ rồi tạo `new MyException("error")` ở từng tầng?  
Vì có thể mất cause, lặp wrapper vô nghĩa, che giấu ranh giới trách nhiệm và làm chẩn đoán khó hơn.

**Câu 9.** Có bắt buộc custom exception phải cung cấp đủ bốn overload không?  
**Không.** Chỉ cần những overload được hợp đồng sử dụng; `(String, Throwable)` đặc biệt hữu ích khi chuyển đổi lỗi.

**Câu 10.** Ngoại lệ có cause đầy đủ đã là đủ để bảo đảm log an toàn và dễ đọc chưa?  
**Chưa.** Vẫn cần message có ngữ cảnh, kiểm soát thông tin nhạy cảm và trình bày đầy đủ chain ở tầng logging phù hợp.

## Tài liệu tham khảo chuẩn

- [Java SE 21 API — `Throwable`: message, cause, `initCause()` và stack trace](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html).
- [Java SE 21 API — `Exception`: các constructor, `Exception(Throwable)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Exception.html).
- [Java SE 21 API — `RuntimeException`: các constructor, checked/unchecked](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/RuntimeException.html).
- [JLS Java SE 21 — §11.1.1: checked và unchecked exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.1.1).
- [JLS Java SE 21 — §11.2: Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).
- [JLS Java SE 21 — §8.8.7: Constructor Body](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.8.7).

**Ghi nhớ:** Một constructor custom exception tốt không chỉ tạo một dòng message: nó bảo toàn nguyên nhân bằng `super(message, cause)`, giữ thông tin lỗi gốc để truy vấn và giúp mỗi tầng diễn đạt đúng ngữ cảnh của mình. Chọn overload theo hợp đồng API, xử lý `null` rõ ràng và tránh tự làm mất cause bằng cách chỉ sao chép text.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-005">Thuộc tính ngữ cảnh có kiểu và tránh dữ liệu dư thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính ngữ cảnh có kiểu và tránh dữ liệu dư thừa trong ngoại lệ tùy chỉnh Java

> **Phạm vi:** Java Core → Exception → Ngoại lệ tùy chỉnh. Một custom exception tốt không chỉ mang `message` và `stack trace`. Khi caller cần **xử lý khác nhau theo mã lỗi, định danh tài nguyên, tên trường hoặc trạng thái tác vụ**, exception có thể cung cấp **thuộc tính ngữ cảnh có kiểu rõ ràng**. Tuy nhiên thêm mọi dữ liệu vào exception không tự làm API tốt hơn: các trường trùng lặp, có thể tính từ trường khác, dễ sai lệch hoặc chứa dữ liệu nhạy cảm sẽ khiến việc xử lý và bảo trì khó khăn.

## 1. Vấn đề cần giải quyết

Hãy hình dung một dịch vụ đặt hàng từ chối yêu cầu vì số lượng đặt vượt số lượng tồn. Cách ném đơn giản:

```java
throw new IllegalArgumentException(
        "Đơn ORD-21 đặt 8 sản phẩm, chỉ còn 3");
```

Chuỗi này có thể đủ để **con người đọc log**, nhưng nếu UI cần biết chính xác nên hiện nút “giảm số lượng”, caller phải tách text hoặc suy đoán. Đó là một hợp đồng dễ vỡ: thay ngôn ngữ, dấu câu hay cách diễn đạt là code phân tích message có thể hỏng.

Một ngoại lệ tùy chỉnh có ngữ cảnh có kiểu giúp tách hai mục đích:

- **Thông tin máy có thể sử dụng ổn định:** `ErrorCode.INSUFFICIENT_STOCK`, `orderId`, `requestedQuantity`, `availableQuantity`.
- **Thông tin phục vụ con người:** `getMessage()` mô tả điều đã xảy ra. Message có thể thay đổi cách viết theo yêu cầu logging mà không ảnh hưởng code quyết định hành vi.

Nhưng cũng không nên lưu lặp `requestedQuantity` trong exception, trong một `Map` riêng, trong một chuỗi `details` và thêm một bản sao của toàn bộ `Order`. Một nguồn dữ liệu đáng tin sẽ giúp giảm nguy cơ các bản sao mâu thuẫn.

**Mục tiêu:** lưu **vừa đủ, có ý nghĩa, có kiểu rõ ràng, bất biến ở mức phù hợp, an toàn để chuyển qua tầng xử lý và không giữ dư dữ liệu**.

## 2. “Thuộc tính ngữ cảnh có kiểu” nghĩa là gì?

Một custom exception có thể định nghĩa các trường mang ngữ nghĩa riêng:

```java
final class InvalidQuantityException extends IllegalArgumentException {
    private final String orderId;
    private final int requestedQuantity;
    private final int availableQuantity;

    InvalidQuantityException(String orderId,
                             int requestedQuantity,
                             int availableQuantity) {
        super("Số lượng đặt vượt số lượng tồn");
        this.orderId = orderId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    String orderId() {
        return orderId;
    }

    int requestedQuantity() {
        return requestedQuantity;
    }

    int availableQuantity() {
        return availableQuantity;
    }
}
```

Đây là một **khai báo lớp hợp lệ nhưng chưa phải chương trình có `main`**. Caller nhận `InvalidQuantityException` có thể đọc trực tiếp:

```java
// Ví dụ trong một handler tương thích:
System.out.println(e.orderId());
System.out.println(e.requestedQuantity());
System.out.println(e.availableQuantity());
```

Thay vì:

```java
// Phản ví dụ: không nên parse thông điệp để đưa ra quyết định.
String message = e.getMessage();
// Tìm "ORD-" rồi tách số lượng bằng substring/regex.
```

Thuộc tính được gọi là **có kiểu** vì chúng dùng `int` cho số lượng, `String` với ngữ nghĩa rõ (hoặc kiểu ID chuyên biệt nếu cần), `enum` cho mã lỗi, `Instant` cho thời điểm, `URI` cho tài nguyên, hay một `record` để gói thông tin có liên quan. Không phải tất cả đều nên có trong cùng một exception.

### 2.1. Thế nào là ngữ cảnh hữu ích?

Một trường hữu ích khi ít nhất một trong các mục tiêu sau cần đến nó:

1. **Caller chọn cách xử lý:** mã lỗi hoặc phân loại nghiệp vụ.
2. **Chẩn đoán:** ID tác vụ/tài nguyên giúp kết hợp với log mà không đưa vào dữ liệu nhạy cảm.
3. **Phản hồi người dùng:** tên trường và giới hạn hợp lệ để hiển thị hướng dẫn sửa.
4. **Quan sát hệ thống:** mã subsystem hoặc request ID để liên kết sự kiện.
5. **Khôi phục an toàn:** thông tin được phép dùng để chọn nguồn thay thế, retry có điều kiện hoặc yêu cầu đầu vào mới.

Đừng thêm trường chỉ vì “có sẵn ở thời điểm ném”. Ngoại lệ nên lưu **ngữ cảnh tối thiểu cần cho hợp đồng xử lý lỗi**.

## 3. So sánh ba cấp độ biểu diễn

| Cách thiết kế | Ví dụ | Điểm mạnh | Hạn chế |
| --- | --- | --- | --- |
| Chỉ `message` | `throw new IllegalArgumentException("...");` | Ngắn gọn, đủ cho lỗi ít cần xử lý đặc biệt | Không cung cấp trường typed cho caller |
| `Map<String,Object>` tùy ý | `Map.of("orderId", id, "qty", 8)` | Dễ mở rộng, phù hợp telemetry không có schema cố định | Key sai chính tả, sai kiểu runtime, khó khám phá, dễ lộ dữ liệu |
| Trường có kiểu / `record` / `enum` | `ErrorCode`, `OrderContext` | Compile-time type checking, API rõ, IDE hỗ trợ, dễ viết test | Thêm lớp/API; cần cân nhắc thay đổi schema và tính tương thích |

**Không có quy tắc** mọi exception phải có `record` hay `enum`. Nếu tầng gọi chỉ cần nhận biết “tham số không hợp lệ”, `IllegalArgumentException` với message tốt có thể đủ. Dùng custom exception khi có **hợp đồng xử lý đặc thù thực sự**.

## 4. Chọn cây kế thừa cho custom exception

Mọi exception Java đều thuộc hệ `Throwable`:

```text
Throwable
├── Exception
│   ├── IOException
│   └── RuntimeException
│       ├── IllegalArgumentException
│       └── IllegalStateException
└── Error
```

Nếu đây là vi phạm hợp đồng của caller (như số lượng âm hoặc không đủ tồn kho theo API cụ thể), một subclass của `IllegalArgumentException` hoặc `RuntimeException` có thể hợp lý. Nếu caller **được kỳ vọng xử lý có kiểm tra compile-time** (ví dụ thất bại một thao tác có thể phục hồi theo hợp đồng), subclass của `Exception` checked có thể hữu ích.

Ví dụ định nghĩa **checked exception**:

```java
final class ImportFailedException extends Exception {
    ImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Ví dụ **unchecked exception**:

```java
final class InvalidRequestException extends IllegalArgumentException {
    InvalidRequestException(String message) {
        super(message);
    }
}
```

Đây là các lớp minh họa, không có `main`. Việc ngoại lệ checked/unchecked **không quyết định** liệu các thuộc tính ngữ cảnh nên có kiểu hay không. Hai việc phải xem riêng: hợp đồng xử lý lỗi và dữ liệu lỗi cần mang theo.

### 4.1. Đừng tạo `Error` cho lỗi nghiệp vụ

`Error` dành cho nhánh lỗi mà ứng dụng thông thường không nên xem như sự cố nghiệp vụ có thể phục hồi. Một lớp `InvalidOrderError extends Error` chỉ để mang `orderId` thường làm sai ý nghĩa, đồng thời bỏ qua những handler `catch(Exception)` thông thường. Nếu cần thông báo vi phạm hợp đồng hay sự cố nghiệp vụ, dùng kiểu exception phù hợp.

## 5. Java 17 — thiết kế mã lỗi, context `record` và ngoại lệ chuyên biệt

Ví dụ hoàn chỉnh sau có:

- `enum ErrorCode` thay chuỗi mã lỗi tự do.
- `record OrderContext` gom các thuộc tính **cùng thuộc một tình huống**.
- `InsufficientStockException` kế thừa `IllegalArgumentException`, giữ code và context typed.
- Handler chọn hành động bằng `code()`, không parse `getMessage()`.

Tạo tệp **`TypedOrderExceptionDemo.java`**:

```java
import java.util.Objects;

public class TypedOrderExceptionDemo {
    enum ErrorCode {
        INSUFFICIENT_STOCK,
        INVALID_QUANTITY
    }

    record OrderContext(String orderId,
                        int requestedQuantity,
                        int availableQuantity) {
        OrderContext {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("Thiếu orderId");
            }
            if (requestedQuantity < 0 || availableQuantity < 0) {
                throw new IllegalArgumentException(
                        "Số lượng không được âm");
            }
        }
    }

    static final class InsufficientStockException
            extends IllegalArgumentException {
        private final ErrorCode code;
        private final OrderContext context;

        InsufficientStockException(OrderContext context) {
            super("Số lượng đặt vượt số lượng tồn");
            this.code = ErrorCode.INSUFFICIENT_STOCK;
            this.context = Objects.requireNonNull(context);
        }

        ErrorCode code() {
            return code;
        }

        OrderContext context() {
            return context;
        }
    }

    static void reserve(OrderContext context) {
        if (context.requestedQuantity() >
                context.availableQuantity()) {
            throw new InsufficientStockException(context);
        }
        System.out.println("OK: " + context.orderId());
    }

    public static void main(String[] args) {
        OrderContext normal = new OrderContext("ORD-1", 2, 5);
        OrderContext shortage = new OrderContext("ORD-2", 7, 3);

        reserve(normal);

        try {
            reserve(shortage);
        } catch (InsufficientStockException e) {
            System.out.println("Code: " + e.code());
            System.out.println("Order: " + e.context().orderId());
            System.out.println("Requested: "
                    + e.context().requestedQuantity());
            System.out.println("Available: "
                    + e.context().availableQuantity());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 TypedOrderExceptionDemo.java
java TypedOrderExceptionDemo
```

**Stdout chính xác:**

```text
OK: ORD-1
Code: INSUFFICIENT_STOCK
Order: ORD-2
Requested: 7
Available: 3
Message: Số lượng đặt vượt số lượng tồn
```

**Điểm học được:**

1. Caller biết lỗi thiếu hàng qua **loại exception** hoặc `ErrorCode.INSUFFICIENT_STOCK`, không phụ thuộc text tiếng Việt.
2. `OrderContext` chỉ chứa thông tin của tình huống đặt hàng. Các trường số lượng là `int`, không phải text cần parse lại.
3. `code` được đặt cố định trong subclass này; nếu chỉ duy nhất một loại lỗi có thể phát sinh, thêm cả subclass chuyên biệt **và** mã cố định có thể dư. Phần sau giải thích cách chọn một nguồn sự thật.
4. `record` tự cung cấp accessor, `equals`, `hashCode` và `toString` dựa trên các component. **Không nên log `toString` mặc định của record nếu nó có dữ liệu nhạy cảm.**
5. Điều kiện bất biến trong constructor record giúp tránh tạo một `OrderContext` có số lượng âm. Đây là validation cho **dữ liệu ngữ cảnh**, không thay thế toàn bộ validation đầu vào của nghiệp vụ.
6. `InsufficientStockException` lưu một tham chiếu `OrderContext` thay vì lưu thêm ba bản sao riêng của `orderId`, `requestedQuantity`, `availableQuantity`.

### 5.1. Nếu đã có subtype chuyên biệt, có cần thêm `ErrorCode`?

Không phải lúc nào cũng cần. Với handler:

```java
catch (InsufficientStockException e) {
    // Loại Java đã đủ để phân biệt lỗi thiếu hàng.
}
```

việc lưu thêm `code = INSUFFICIENT_STOCK` có thể **lặp lại cùng một sự thật**. Tuy nhiên mã lỗi vẫn hữu ích nếu API cần trả mã ổn định cho client, ghi metric thống nhất giữa nhiều loại exception, hoặc mapping sang hệ thống ngoài.

Hãy quyết định:

- Nếu toàn bộ hệ thống thuần Java và subtype đủ rõ: có thể chỉ giữ **kiểu exception** cùng context.
- Nếu mã lỗi là một phần giao thức/telemetry: giữ một mã ổn định; cân nhắc **derive code từ subtype** thay vì lưu bản sao có thể không đồng bộ.
- Nếu một loại exception bao phủ nhiều tình huống: `enum` `code` có thể là dữ liệu thực sự cần thiết.
- Nếu mã lỗi được lưu song song với subtype, thiết kế constructor tránh nhận một mã bất kỳ trái với subtype.

### 5.2. Không truyền code tùy ý vào subclass có ý nghĩa cố định

Một thiết kế dễ tạo mâu thuẫn:

```java
// Phản ví dụ thiết kế, không phải chương trình đầy đủ.
new InsufficientStockException(
        ErrorCode.INVALID_QUANTITY, context);
```

Nếu cho phép constructor như vậy, kiểu Java nói “không đủ tồn” nhưng code lại nói “số lượng không hợp lệ”. Nên **suy ra mã cố định từ loại exception**, hoặc đổi thiết kế sang một exception chung với code được kiểm soát, thay vì tạo hai trường hợp đồng trái nhau.

## 6. Quy tắc “một nguồn sự thật” cho ngữ cảnh

Dữ liệu dư thừa có nhiều dạng, không chỉ là hai trường có tên giống nhau.

| Dữ liệu | Dạng dư thừa | Thiết kế thường tốt hơn |
| --- | --- | --- |
| `orderId` trong context và trường riêng cùng object | Hai nơi giữ cùng ID | Một `OrderContext.orderId()` |
| `amount` và `amountText` | Text có thể suy từ số | Lưu số, format khi hiển thị |
| `status` và `isFailure` | Boolean suy từ trạng thái | Chỉ lưu status có kiểu |
| `errorCode` và `errorName` | Tên có thể suy từ enum | Chỉ lưu `ErrorCode`, lấy `name()` khi cần |
| `cause` và `originalException` | Hai tham chiếu đến cùng lỗi gốc | Dùng `getCause()` nếu đó là quan hệ nguyên nhân |
| `message` và `description` cùng copy | Hai chuỗi mô tả cùng ý nghĩa | Thông điệp phục vụ người đọc; ngữ cảnh riêng dùng typed fields |
| `timestamp` và `ageMillis` | `ageMillis` thay đổi theo thời gian | Lưu thời điểm cần thiết, tính tuổi ở thời điểm đọc |
| `userProfile` đầy đủ và `userId` | Giữ quá nhiều dữ liệu, rủi ro PII | Chỉ ID an toàn hoặc request ID |
| `Map<String,Object>` và các getter có kiểu cùng dữ liệu | Hai schema phải đồng bộ | Chọn typed API là nguồn chính; tạo map chỉ khi cần xuất |
| `Throwable` được lưu trong một field riêng và truyền vào `super(message, cause)` | Hai tham chiếu giống cause | Lấy `getCause()`, không giữ bản sao |

**Không cấm message đề cập lại số lượng để người đọc hiểu.** “Tránh dư thừa” chủ yếu nói đến **trạng thái nguồn dữ liệu** và các bản sao có thể lệch nhau. Message thường là **biểu diễn** của dữ liệu hoặc phần mô tả tự nhiên; quan trọng là **không parse ngược message để ra quyết định**.

### 6.1. Chỉ lưu số liệu nền, tính giá trị dẫn xuất khi cần

Ví dụ có `requestedQuantity` và `availableQuantity`:

```java
int shortage = Math.max(
        0, context.requestedQuantity()
                - context.availableQuantity());
```

Nếu `shortage` chỉ là phép tính xác định từ hai trường kia, không cần trường thứ ba `shortage` trong exception. Lưu cả ba tạo rủi ro một constructor hoặc refactor tính sai.

**Ngoại lệ có chủ đích:** đôi khi cần lưu “snapshot” của giá trị dẫn xuất do quy tắc tính phụ thuộc tỷ giá, thời điểm, phiên bản thuật toán hoặc trạng thái ngoài. Khi đó giá trị không thực sự suy lại được từ hai input hiện có và có thể đáng lưu, nhưng phải mô tả ý nghĩa snapshot.

### 6.2. Đừng lưu cả object domain lớn chỉ để lấy ID

Không nên:

```java
// Ví dụ phản thiết kế; Order là đối tượng nghiệp vụ lớn.
class FailedOrderException extends RuntimeException {
    private final Order entireOrder;
}
```

Đây không phải một lớp hoàn chỉnh. Giữ `entireOrder` trong Throwable có thể vô tình kéo theo dữ liệu lớn, trạng thái mutable, liên kết đến session/connection, hoặc dữ liệu nhạy cảm. Exception có thể tồn tại lâu hơn thao tác gây lỗi do log queue, retry framework hoặc storage.

Thay bằng **định danh tối thiểu** và một số trường có ý nghĩa phục hồi/chẩn đoán:

```java
private final String orderId;
private final ErrorCode code;
```

Những trường này phải có ngữ nghĩa rõ. Với ID có nhiều loại dễ nhầm, có thể dùng value object typed hơn là `String` chung.

## 7. Giá trị bất biến: `final` là cần nhưng chưa đủ

Đối tượng exception bản thân **không hoàn toàn bất biến**, vì `Throwable` có các API như `addSuppressed` và `setStackTrace`. Tuy vậy, **các trường ngữ cảnh tùy chỉnh** nên được giữ ổn định sau khi exception được tạo để caller, logger và nhiều thread không quan sát các giá trị khác nhau.

Điều cần kiểm tra:

- Dùng `private final` cho trường context.
- Kiểm tra `null` và invariants ở constructor.
- Không cung cấp setter thay đổi error code hoặc ID sau khi lỗi đã xảy ra.
- Nếu context chứa `List`/`Set`/`Map`, tạo defensive copy bất biến thích hợp.
- Với các object mutable lồng bên trong collection, copy collection **chưa phải deep copy**. Ưu tiên component immutable hoặc snapshot các trường cần thiết.
- Không lưu các resource đang mở hoặc object domain có vòng đời dài/ngắn không kiểm soát.
- Hạn chế đưa exception object có mutable context qua nhiều thread nếu không có hợp đồng concurrency rõ.

### 7.1. Java 17 — record với `List.copyOf` để giữ snapshot

Tạo tệp **`ImmutableContextDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImmutableContextDemo {
    record BatchContext(String batchId, List<String> itemIds) {
        BatchContext {
            Objects.requireNonNull(batchId, "batchId");
            itemIds = List.copyOf(
                    Objects.requireNonNull(itemIds, "itemIds"));
        }
    }

    static final class BatchFailedException
            extends RuntimeException {
        private final BatchContext context;

        BatchFailedException(BatchContext context) {
            super("Lô xử lý thất bại");
            this.context = Objects.requireNonNull(context);
        }

        BatchContext context() {
            return context;
        }
    }

    public static void main(String[] args) {
        List<String> original = new ArrayList<>(
                List.of("A", "B"));

        BatchContext snapshot =
                new BatchContext("B-1", original);
        BatchFailedException e =
                new BatchFailedException(snapshot);

        original.add("C");

        System.out.println("Gốc: " + original.size());
        System.out.println("Snapshot: "
                + e.context().itemIds().size());
        System.out.println("Items: "
                + String.join(",", e.context().itemIds()));

        try {
            e.context().itemIds().add("D");
        } catch (UnsupportedOperationException ignored) {
            System.out.println("Không thể sửa snapshot");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ImmutableContextDemo.java
java ImmutableContextDemo
```

Stdout:

```text
Gốc: 3
Snapshot: 2
Items: A,B
Không thể sửa snapshot
```

**Vì sao có ý nghĩa?**

1. `original` là danh sách mutable do bên ngoài giữ.
2. `List.copyOf` tạo một danh sách không sửa được, với các phần tử `String` vốn immutable.
3. Sửa `original` sau khi tạo exception không làm thay đổi context bên trong.
4. Getter `context().itemIds()` không cho caller thêm phần tử.
5. Nếu danh sách chứa những object mutable khác `String`, `List.copyOf` chỉ bảo vệ cấu trúc danh sách, **không biến các phần tử mutable thành immutable**.

Lưu ý: `List.copyOf` từ chối phần tử `null`. Nếu `null` có ý nghĩa trong mô hình ngữ cảnh, cần hợp đồng khác, nhưng thông thường danh sách ID không nên chứa `null`.

## 8. Dùng `enum` hay `record` cho mã lỗi và các trường ngữ cảnh?

Một `enum` tốt cho tập giá trị đóng, có danh sách hữu hạn và ý nghĩa ổn định:

```java
enum ValidationCode {
    MISSING_FIELD,
    INVALID_FORMAT,
    OUT_OF_RANGE
}
```

Một `record` phù hợp để mô tả nhóm dữ liệu bất biến ở mức tham chiếu và có hành vi value-based:

```java
record FieldIssue(String field, ValidationCode code,
                  int min, int max) {
}
```

Đây là ví dụ kiểu khai báo, không có `main`. Hãy xét liệu mọi trường đều có ý nghĩa cho **mọi giá trị code**. Nếu `MISSING_FIELD` không liên quan đến `min`/`max`, một record chứa cả bốn trường có thể tạo trạng thái khó hiểu.

Các cách đơn giản hơn:

- Chia thành loại context riêng cho từng nhóm lỗi nếu schema thực sự khác nhau.
- Dùng một `ErrorCode` và field ID, chỉ thêm giới hạn khi loại lỗi thực sự cần.
- Dùng sealed hierarchy hoặc subtype chuyên biệt khi hợp đồng đủ phức tạp; không bắt buộc cho mọi hệ thống.
- Tránh “mega context” với hàng chục trường `null` và flags khó nhất quán.

### 8.1. Trường bắt buộc và optional

Một thuộc tính bắt buộc nên được xác nhận ngay ở constructor. Ví dụ:

```java
this.orderId = Objects.requireNonNull(orderId, "orderId");
```

Với giá trị tùy chọn, phải có chính sách rõ: `null` có thể là hợp đồng hợp lệ trong phạm vi nội bộ, hoặc sử dụng cách biểu diễn có nghĩa riêng. Không thêm `Optional` field theo thói quen mà không cân nhắc serialization, API và cách dùng.

### 8.2. Không coi mọi chuỗi `String` là “mất type safety”

`orderId` dưới dạng `String` đôi khi là lựa chọn hợp lý khi API hiện tại giao tiếp bằng chuỗi. Type safety là mức độ phù hợp với hợp đồng, không phải số lượng wrapper class. Nếu thường xuyên nhầm `userId` và `orderId` đều là `String`, một typed ID như `record OrderId(String value)` có thể giảm nhầm lẫn đáng kể.

## 9. Không nhân bản cause và suppressed vào các trường riêng

`Throwable` đã có:

```java
Throwable getCause()
Throwable[] getSuppressed()
StackTraceElement[] getStackTrace()
String getMessage()
```

Vì vậy custom exception thông thường **không cần** thêm:

```java
// Thiết kế dư thừa, KHÔNG khuyến nghị:
private final Throwable originalException;
private final String originalMessage;
private final StackTraceElement[] originalStack;
```

Nếu `originalException` chính là cause, truyền nó cho `super(message, cause)` và đọc lại qua `getCause()`. Lưu thêm `originalMessage` và mảng stack làm tăng bộ nhớ, phức tạp vòng đời và có thể giữ bản chụp không cần thiết.

Tương tự, nếu try-with-resources đã gắn các lỗi đóng qua `addSuppressed`, không cần thêm `List<Throwable> cleanupErrors` song song, trừ khi đó là cấu trúc nghiệp vụ khác với ý nghĩa độc lập.

### 9.1. `message` không thay thế context typed

Không thể nói ngược rằng vì `getMessage()` đã chứa `orderId` nên mọi trường typed là dư. Message có mục đích hỗ trợ người đọc và có thể thay đổi theo ngôn ngữ. Nếu caller cần order ID để truy cứu hoặc API phải trả mã lỗi, typed context mới là **dữ liệu hợp đồng**.

Một thiết kế nhất quán có thể:

- Lưu `orderId` và `ErrorCode` ở các trường typed.
- Dùng một message ngắn, ví dụ “Không đủ tồn kho”.
- Khi log, kết hợp các getter để hiển thị có cấu trúc.
- Khi trả API, map code/context sang response được kiểm soát.
- Không tách `getMessage()` để lấy lại order ID.

### 9.2. Ranh giới giữa cause và context

`cause` trả lời “Throwable nào đã dẫn đến exception này?”. `context` trả lời “trường hợp/định danh/tham số nào liên quan?”. Một `IOException` nên là cause nếu nó thực sự gây ra failure. Không nên đưa `IOException` vào `Map<String,Object>` của context để thay cause chain.

## 10. Java 17 — checked exception có context typed và giữ cause

Tạo tệp **`PaymentProcessingDemo.java`**:

```java
import java.io.IOException;
import java.util.Objects;

public class PaymentProcessingDemo {
    enum FailureCode {
        PROVIDER_UNAVAILABLE,
        INVALID_RESPONSE
    }

    record PaymentContext(String transactionId, String providerCode) {
        PaymentContext {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(providerCode, "providerCode");
        }
    }

    static final class PaymentProcessingException extends Exception {
        private final FailureCode failureCode;
        private final PaymentContext context;

        PaymentProcessingException(FailureCode failureCode,
                                   PaymentContext context,
                                   Throwable cause) {
            super("Không thể xử lý thanh toán", cause);
            this.failureCode =
                    Objects.requireNonNull(failureCode);
            this.context = Objects.requireNonNull(context);
        }

        FailureCode failureCode() {
            return failureCode;
        }

        PaymentContext context() {
            return context;
        }
    }

    static void invokeProvider() throws IOException {
        throw new IOException("Kết nối thất bại");
    }

    static void charge(PaymentContext context)
            throws PaymentProcessingException {
        try {
            invokeProvider();
        } catch (IOException e) {
            throw new PaymentProcessingException(
                    FailureCode.PROVIDER_UNAVAILABLE,
                    context, e);
        }
    }

    public static void main(String[] args) {
        PaymentContext context =
                new PaymentContext("TX-17", "PROVIDER-A");
        try {
            charge(context);
        } catch (PaymentProcessingException e) {
            System.out.println("Code: " + e.failureCode());
            System.out.println("Transaction: "
                    + e.context().transactionId());
            System.out.println("Provider: "
                    + e.context().providerCode());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 PaymentProcessingDemo.java
java PaymentProcessingDemo
```

Stdout:

```text
Code: PROVIDER_UNAVAILABLE
Transaction: TX-17
Provider: PROVIDER-A
Cause: IOException
Message: Không thể xử lý thanh toán
```

**Diễn giải:**

- `PaymentProcessingException extends Exception` là **checked exception**, nên `charge` khai báo `throws PaymentProcessingException`.
- `FailureCode` cho phép caller chọn chính sách theo một giá trị ổn định, không dùng `getMessage().contains("thanh toán")`.
- `PaymentContext` chứa transaction ID và mã nhà cung cấp cần cho điều tra. Không giữ object thanh toán đầy đủ hoặc số thẻ.
- `IOException` gốc được truyền vào `super(message, cause)`. Nó **không** được lưu lặp trong field `originalIOException`.
- `getCause()` cung cấp đường chẩn đoán lỗi tầng I/O.
- Mã ví dụ không thực hiện thanh toán thực: chỉ minh họa thiết kế hợp đồng lỗi và đầu ra có thể tái tạo.

### 10.1. Vì sao không dùng `String failureCode`?

Có thể dùng chuỗi nếu mã lỗi phải khớp với giao thức ngoài và tập giá trị thay đổi động. Nhưng trong một module Java có tập mã cố định, `enum` giúp compiler và IDE nhận biết lựa chọn; giảm lỗi chính tả và giúp logic `switch` rõ ràng.

Khi ánh xạ ra HTTP/JSON, **đừng tự động lấy `enum.ordinal()` làm mã lỗi ổn định**: thứ tự enum có thể thay đổi. Dùng tên hoặc một trường mã giao thức được kiểm soát nếu API công khai yêu cầu tương thích dài hạn.

## 11. Chọn context dựa trên người xử lý lỗi

Cùng một failure có thể được quan sát ở các tầng khác nhau:

| Người đọc exception | Dữ liệu thường cần | Không nên tự nhét vào exception |
| --- | --- | --- |
| Caller Java | Loại exception, error code, định danh, field lỗi | Bản sao toàn bộ request |
| UI/Form validation | Tên trường, mã validation, giới hạn cần thiết | Stack trace, SQL, token |
| Dịch vụ điều phối | ID tác vụ, giai đoạn, thông tin retry phù hợp | `Connection`, `InputStream` đang mở |
| Hệ thống logging | Request/correlation ID, code, cause, stack | Password hoặc khóa bí mật |
| Client API bên ngoài | Mã lỗi công khai và thông điệp an toàn | Tên class nội bộ, đường file, cause chain thô |
| Hệ thống metric | Code hữu hạn/cardinality thấp | orderId, userId hoặc mã transaction làm nhãn metric cao-cardinality |

**Tối thiểu hóa ngữ cảnh không đồng nghĩa bỏ thông tin cần thiết**. Ví dụ một request ID an toàn có thể giúp liên kết log, nhưng không nhất thiết lưu nó trong mọi exception nếu logging context (MDC hoặc tracing context) đã có và lifecycle được quản lý đúng.

### 11.1. Không đẩy mọi ngữ cảnh vào exception

Nếu một thông tin chỉ dùng để log ở một tầng mà không cần đi theo exception, có thể truyền qua logging context/tracing phù hợp. Ngược lại, nếu caller thực sự cần biết field gây lỗi để xử lý, đặt field typed trong exception có thể rõ ràng hơn phụ thuộc một cơ chế log side-channel.

Câu hỏi thực dụng là **ai chịu trách nhiệm sử dụng thông tin, trong bao lâu và ở đâu**.

## 12. Dữ liệu nhạy cảm, dữ liệu lớn và bảo mật

Ngoại lệ có thể bị log tự động, hiển thị trong crash report, gửi tới hệ thống giám sát hoặc giữ lại trong queue; các thuộc tính của nó có thể bị truy cập qua debug/reflection/serialization tùy môi trường.

Tránh đưa vào context:

- Password, access token, refresh token, session cookie hoặc khóa bí mật.
- Số thẻ thanh toán đầy đủ, dữ liệu xác thực, toàn bộ hồ sơ người dùng.
- Body request chưa được lọc, JSON chứa thông tin cá nhân hoặc header `Authorization`.
- Stream/connection và đối tượng có vòng đời ngoài ý định của exception.
- Collection cực lớn hoặc object graph có thể khiến memory retention tăng.
- Các giá trị mà caller không có quyền truy cập.

Thay vào đó, chọn **ID tham chiếu không nhạy cảm**, mã lỗi, tên trường cho phép công khai và những giá trị đã được kiểm soát. Ngay cả ID cũng có thể là dữ liệu cần bảo vệ theo ngữ cảnh hệ thống; không có danh sách “luôn an toàn” cho mọi ứng dụng.

### 12.1. Java 17 — typed context nhưng không giữ giá trị bí mật

Tạo tệp **`SafeValidationContextDemo.java`**:

```java
import java.util.Objects;

public class SafeValidationContextDemo {
    enum Field {
        API_TOKEN,
        EMAIL,
        AMOUNT
    }

    enum ValidationCode {
        MISSING,
        INVALID
    }

    static final class SafeValidationException
            extends IllegalArgumentException {
        private final Field field;
        private final ValidationCode code;
        private final String requestId;

        SafeValidationException(Field field,
                                ValidationCode code,
                                String requestId) {
            super("Dữ liệu đầu vào không hợp lệ");
            this.field = Objects.requireNonNull(field);
            this.code = Objects.requireNonNull(code);
            this.requestId =
                    Objects.requireNonNull(requestId);
        }

        Field field() {
            return field;
        }

        ValidationCode code() {
            return code;
        }

        String requestId() {
            return requestId;
        }
    }

    static void checkToken(String token, String requestId) {
        if (token == null || token.isBlank()) {
            throw new SafeValidationException(
                    Field.API_TOKEN,
                    ValidationCode.MISSING,
                    requestId);
        }
    }

    public static void main(String[] args) {
        try {
            checkToken("", "REQ-42");
        } catch (SafeValidationException e) {
            System.out.println("Field: " + e.field());
            System.out.println("Code: " + e.code());
            System.out.println("Request: " + e.requestId());
            System.out.println("Message: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SafeValidationContextDemo.java
java SafeValidationContextDemo
```

Stdout:

```text
Field: API_TOKEN
Code: MISSING
Request: REQ-42
Message: Dữ liệu đầu vào không hợp lệ
```

**Tại sao an toàn hơn?** Exception chỉ chứa tên trường và mã lỗi, không mang token đầu vào. Điều đó không thay cho các biện pháp bảo vệ còn lại, nhưng giảm rủi ro vô tình log chính giá trị bí mật trong `toString()`, message hoặc object context.

Cũng không nên gọi `getMessage()` với một chuỗi được tạo từ toàn bộ token: nhiều framework log message mặc định.

### 12.2. Đừng serialize nguyên trạng exception cho client

Một response API nên là một DTO được chọn lọc, ví dụ `{code, field, requestId, safeMessage}`. Không trả thẳng exception, stack trace, cause, suppressed và toàn bộ custom fields. Khác biệt này vừa liên quan bảo mật vừa giúp hợp đồng API độc lập với implementation Java.

## 13. Thông điệp người đọc và mã cho máy

`getMessage()` là văn bản; không bảo đảm không đổi giữa phiên bản, ngôn ngữ, cấu hình hoặc mức chi tiết. `errorCode` typed dùng khi machine logic cần chính sách ổn định.

Ví dụ handler hợp lý:

```java
switch (e.code()) {
    case INSUFFICIENT_STOCK ->
            System.out.println("Đề xuất giảm số lượng");
    case INVALID_QUANTITY ->
            System.out.println("Yêu cầu số lượng hợp lệ");
}
```

Đây là mảnh code minh họa cần biến `e` có accessor `code()` trả `ErrorCode`. Mục tiêu là **không phân nhánh theo substring message**.

Nếu ứng dụng đa ngôn ngữ, thường lưu code/field và map sang thông điệp theo locale tại **tầng hiển thị**, thay vì đặt một chuỗi đã địa phương hóa làm nguồn sự thật duy nhất trong exception.

### 13.1. Lỗi về thứ tự ưu tiên

Nếu handler phân loại theo subtype, đôi khi **không cần mã lỗi**. Nếu handler phân loại theo mã lỗi, đôi khi **không cần subclass cho từng mã**. Không nhất thiết nên tạo hai hệ phân loại song song rồi cố đồng bộ tất cả mapping.

Hãy chọn một thiết kế mạch lạc và cung cấp adapter/mapping khi cần giao tiếp với tầng ngoài, thay vì lưu đồng thời nhiều bản sao trong mọi Throwable.

## 14. Bảo toàn quan hệ `cause` và `suppressed`

Khi một lỗi tầng thấp gây ra lỗi tầng cao:

```java
try {
    callExternalService();
} catch (java.io.IOException e) {
    throw new ServiceOperationException(
            "Không thể gọi dịch vụ", context, e);
}
```

Đây là đoạn minh họa, giả định `ServiceOperationException` có constructor đúng. Thuộc tính `context` mô tả tác vụ (ví dụ ID và pha), `cause` là exception đã phát sinh. Hai thứ **không thay thế nhau**.

Trong try-with-resources, lỗi đóng tài nguyên có thể được gắn dưới dạng **suppressed exception** vào primary. Không nên tự nhân bản chúng thành `List<Throwable> closeFailures` trong custom exception nếu dữ liệu `getSuppressed()` đã đủ.

### 14.1. Java 17 — context và cause là hai trách nhiệm riêng

Tạo tệp **`ContextWithCauseDemo.java`**:

```java
import java.io.IOException;
import java.util.Objects;

public class ContextWithCauseDemo {
    record TaskContext(String taskId, String phase) {
        TaskContext {
            Objects.requireNonNull(taskId);
            Objects.requireNonNull(phase);
        }
    }

    static final class TaskFailedException
            extends RuntimeException {
        private final TaskContext context;

        TaskFailedException(TaskContext context, Throwable cause) {
            super("Tác vụ thất bại", cause);
            this.context = Objects.requireNonNull(context);
        }

        TaskContext context() {
            return context;
        }
    }

    public static void main(String[] args) {
        IOException original =
                new IOException("Nguồn dữ liệu mất kết nối");
        TaskFailedException error = new TaskFailedException(
                new TaskContext("JOB-9", "READ"),
                original);

        System.out.println("Task: " + error.context().taskId());
        System.out.println("Phase: " + error.context().phase());
        System.out.println("Message: " + error.getMessage());
        System.out.println("Cause: "
                + error.getCause().getClass().getSimpleName());
        System.out.println("Suppressed: "
                + error.getSuppressed().length);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ContextWithCauseDemo.java
java ContextWithCauseDemo
```

Stdout:

```text
Task: JOB-9
Phase: READ
Message: Tác vụ thất bại
Cause: IOException
Suppressed: 0
```

Exception giữ `TaskContext` cho mục đích nhận diện tác vụ, còn `IOException` nằm trong `getCause()`. Không cần trường thứ hai `private IOException originalIOException`. Vì chương trình không có lỗi phụ từ đóng/dọn, suppressed count bằng `0`.

## 15. Những quy tắc constructor quan trọng

Constructor của subclass `Throwable` nên thiết lập đúng state ngay từ đầu:

```java
public final class DataImportException extends Exception {
    private final String importId;
    private final FailureCode code;

    public DataImportException(String importId,
                               FailureCode code,
                               Throwable cause) {
        super("Nhập dữ liệu thất bại", cause);
        this.importId =
                java.util.Objects.requireNonNull(importId);
        this.code =
                java.util.Objects.requireNonNull(code);
    }

    public String importId() {
        return importId;
    }

    public FailureCode code() {
        return code;
    }
}
```

Đây là **mẫu lớp**, giả sử `FailureCode` đã được định nghĩa và tệp/lớp được đặt đúng quy tắc Java. Không phải chương trình standalone.

Đảm bảo:

- Lời gọi `super(message, cause)` được đặt đúng theo quy tắc constructor Java.
- Trường bắt buộc được validate; dùng `Objects.requireNonNull` khi cần.
- Không lưu bản sao `cause`; gọi `getCause()` khi cần.
- Không mở file, gọi mạng hay thao tác lớn trong constructor exception; exception nên nhẹ, dễ tạo khi đường lỗi xảy ra.
- Không gọi method có thể override từ constructor nếu nó phụ thuộc field subclass chưa khởi tạo xong.
- Cân nhắc constructor overload nếu caller có cả trường hợp có/không có cause; đừng tạo overload vô nghĩa không ai sử dụng.
- Thông điệp ngắn, không chứa dữ liệu cần bảo vệ.

### 15.1. Nếu gọi `super` rồi validate field mới thì sao?

Một số validation có thể ném `NullPointerException` hoặc `IllegalArgumentException` trong constructor trước khi exception dự định được tạo xong. Điều đó hợp lệ về mặt Java nhưng caller sẽ thấy lỗi xây dựng exception, không phải exception mong muốn. Cần xác định **ngữ cảnh nào luôn có sẵn** ở đường lỗi và thiết kế constructor để các giá trị bắt buộc thực sự có thể cung cấp.

Không làm “context required” giả nếu thực tế nhiều đường lỗi không có ID. Có thể tách loại exception hoặc dùng hợp đồng nullable được ghi rõ khi phù hợp.

## 16. Lựa chọn lưu snapshot thay vì tham chiếu đến trạng thái đang thay đổi

Giả sử một `Order` có trạng thái mutable, được giữ làm field:

```java
// Anti-pattern khi caller có thể tiếp tục sửa order.
private final Order order;
```

Dù field là `final`, **đối tượng `order` vẫn có thể thay đổi** sau khi exception được tạo. Khi log ở thời điểm muộn hơn, dữ liệu hiện tại không còn phản ánh trạng thái khi lỗi xảy ra.

Thay bằng dữ liệu snapshot:

```java
record QuantitySnapshot(String orderId,
                        int requested,
                        int availableAtFailure) {
}
```

Một `record` chứa `String` và `int` như trên là value context đơn giản và ổn định. Tên `availableAtFailure` giúp người đọc hiểu giá trị là **snapshot tại thời điểm xảy ra lỗi**, không phải lượng tồn hiện hành.

Đây là khác biệt quan trọng:

- “Available quantity” hiện tại thuộc domain store và có thể thay đổi.
- “Available at failure” là quan sát trong một lần xử lý.
- Nếu caller muốn lấy lượng tồn **mới nhất**, nó phải gọi nguồn dữ liệu phù hợp, không coi exception chứa state live.

### 16.1. Snapshot không tự là deep copy

Record có `List<OrderLine>` mà `OrderLine` mutable có thể vẫn thay đổi qua tham chiếu. `List.copyOf` chỉ bảo vệ cấu trúc danh sách, không sao chép toàn bộ object con. Tùy mức rủi ro, chọn component immutable, chuyển thành các ID/giá trị primitive, hoặc viết logic sao chép sâu có mục đích.

## 17. Logging và serialization: hệ quả của việc thêm field

Java `Throwable` triển khai `Serializable`, nhưng điều đó **không** có nghĩa mọi custom field tự động phù hợp để serialize qua Java serialization. Nếu class exception có field trỏ đến object không serializable, việc serialize có thể thất bại. Trong Java hiện đại, không nên chọn Java native serialization làm giao thức public API chỉ vì `Exception` có sẵn `Serializable`.

Nếu dự án thực sự phải serialize exception qua một biên giới Java nội bộ:

- Các kiểu field cần tương thích với yêu cầu serialization, hoặc có chiến lược ánh xạ.
- Cân nhắc `serialVersionUID`, tương thích thay đổi class và rủi ro version skew.
- Không lưu object chứa credential/resource trong exception serialized.
- Không phụ thuộc vào khả năng sửa field ngữ cảnh không tương thích giữa các phiên bản.
- Nên cân nhắc chuyển lỗi thành **DTO có schema ổn định** cho HTTP, event, message queue thay vì serialize Throwable trực tiếp.

`record` được Java hỗ trợ serialization theo quy tắc riêng nếu components serializable; record **không tự biến mọi component thành serializable**. Nếu serialization là yêu cầu, cần đánh giá/test cấu trúc thực tế.

### 17.1. Dữ liệu có kiểu không nhất thiết “an toàn cho metric”

Một `ErrorCode` là nhãn metric tốt vì có số lượng giá trị giới hạn. Một `orderId`, `transactionId` hoặc email có thể tạo số lượng nhãn cực lớn (*high cardinality*). Dù các trường này có kiểu rõ, vẫn không nên dùng trực tiếp chúng làm nhãn metric. Đưa ID vào log/tracing theo chính sách phù hợp, còn metric thường nên dùng code/subsystem cố định.

## 18. Kiểm thử hợp đồng của custom exception

Một test tốt không chỉ hỏi “đã ném exception chưa?”. Với custom exception có context typed, nên kiểm tra:

1. **Đúng loại exception** ở đường lỗi.
2. **Đúng code** nếu code là một phần hợp đồng.
3. **Đúng context fields** với input.
4. **Invariants** của constructor/record.
5. **Cause** được giữ khi bọc ngoại lệ thấp hơn.
6. **Immutability** của context nếu input có collection mutable.
7. **Không chứa dữ liệu nhạy cảm** trong message/context được log hoặc response.
8. **Đường thành công** không ném ngoại lệ không cần thiết.

Với JUnit 5, ví dụ test ý tưởng:

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TypedOrderExceptionDemoTest {
    @Test
    void shortageProvidesTypedDetails() {
        var context = new TypedOrderExceptionDemo.OrderContext(
                "ORD-5", 9, 2);

        var error = assertThrows(
                TypedOrderExceptionDemo.InsufficientStockException.class,
                () -> TypedOrderExceptionDemo.reserve(context));

        assertEquals(
                TypedOrderExceptionDemo.ErrorCode.INSUFFICIENT_STOCK,
                error.code());
        assertEquals("ORD-5", error.context().orderId());
        assertEquals(9, error.context().requestedQuantity());
        assertEquals(2, error.context().availableQuantity());
    }
}
```

**Đây là file test minh họa, không phải một tệp `javac` độc lập:** cần JUnit Jupiter trên classpath và đặt file test cùng default package hoặc điều chỉnh visibility/package phù hợp. Mã tập trung kiểm tra **dữ liệu có cấu trúc**, không parse text của message.

### 18.1. Không test ngữ cảnh dựa trên `toString()` mặc định

`record.toString()` và `Throwable.toString()` dành cho hiển thị, không phải schema ổn định để phần mềm parse. Test getter/field type thay vì yêu cầu một cách định dạng string không liên quan đến hợp đồng API.

### 18.2. Test tránh dư thừa dữ liệu

Kiểm tra một context mutable bị thay đổi sau khi ném exception: các getter trong exception có giữ snapshot đúng không? Nếu code có hai field biểu diễn cùng một giá trị, có thể test tính nhất quán — nhưng tốt hơn là loại bỏ field dư ngay từ thiết kế, không phải thêm test để duy trì hai bản sao mãi mãi.

## 19. Chọn giữa custom exception và kết quả lỗi có cấu trúc

Không phải mọi validation failure đều nên ném exception. Nếu người dùng nhập form thường xuyên có nhiều field sai, một kiểu kết quả:

```java
record ValidationIssue(String field, String code) {}
record ValidationResult(
        boolean valid,
        java.util.List<ValidationIssue> issues) {}
```

có thể phù hợp hơn dùng `Throwable` cho từng lỗi dự kiến. Đây là **định nghĩa kiểu minh họa**, không có chương trình `main`. Khi có nhiều lỗi đồng thời, một tập `ValidationIssue` thường diễn đạt dữ liệu trực tiếp hơn danh sách suppressed exceptions (vốn phục vụ quan hệ lỗi phụ).

Quyết định theo semantics:

- **Exception:** không thể hoàn thành thao tác đúng hợp đồng, cần unwinding hoặc caller xử lý đường thất bại.
- **Result/validation errors:** thất bại dự kiến như một phần luồng giao tiếp bình thường, có thể tổng hợp nhiều vấn đề và trả về mà không ném.

Nếu dùng custom exception, giữ ngữ cảnh trong nó có ý nghĩa rõ ràng; không chuyển mọi kết quả nghiệp vụ thành exception chỉ vì có thể tạo typed fields.

## 20. Các lỗi phổ biến và cách sửa

| Lỗi thiết kế | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Parse `getMessage()` để lấy order ID/code | Text thay đổi không theo hợp đồng máy | Cung cấp getter typed |
| `Map<String,Object>` cho toàn bộ context | Key/kiểu sai chỉ lộ khi chạy | Dùng field/record/enum khi schema đã biết |
| Giữ đồng thời `quantity` và `quantityText` | Dễ lệch khi format hay update | Chỉ lưu quantity, format ở presentation |
| Giữ toàn bộ entity domain mutable | Log muộn thấy state khác, rò rỉ dữ liệu | Lưu snapshot tối thiểu |
| `private final List<T>` nhưng không copy | Caller vẫn có thể sửa list | `List.copyOf`; kiểm tra mutable elements |
| Dùng `record` và nghĩ tự deep immutable | Component có thể trỏ đến object mutable | Chọn component immutable/snapshot |
| Lưu thêm `originalException` cạnh `cause` | Trùng dữ liệu Throwable đã có | `super(message, cause)` + `getCause()` |
| Lưu `errorCode` tùy ý trong subtype có ý nghĩa cố định | Type và code có thể mâu thuẫn | Derive code, giới hạn constructor |
| Đưa password/token vào message | Có thể bị log tự động | Chỉ lưu field/code và ID an toàn |
| Dùng ID người dùng làm metric label | High cardinality và rủi ro riêng tư | Metric theo code hữu hạn, ID trong log được kiểm soát |
| Trả Throwable nguyên trạng qua HTTP | Rò rỉ stack, cause, field nội bộ | Map sang DTO đã lọc |
| Thêm hàng chục getter “phòng khi cần” | API phình to, versioning khó | Chỉ trường có use case thực tế |
| Dùng `Error` cho lỗi domain | Sai ngữ nghĩa và handler thường gặp | `Exception`/`RuntimeException` thích hợp |
| Bắt custom exception rồi trả “OK” | Che thất bại, làm sai trạng thái | Phục hồi thật hoặc báo failure |
| Tắt cause để giảm độ dài log | Mất đường truy nguyên | Giữ cause; chỉnh policy logger nếu cần |
| Dùng suppressed như danh sách tất cả validation issues | Sai ngữ nghĩa quan hệ Throwable | Dùng result có danh sách issue |
| Dùng native serialization làm public error protocol | Ràng buộc implementation và nguy cơ dữ liệu | Dùng DTO/versioned schema |
| Xem thuộc tính context là state live | Snapshot đã cũ sau khi ném | Tên field “atFailure” và đọc store khi cần giá trị mới |

## 21. Bài tập vận dụng

### Bài 1 — Tìm dữ liệu dư thừa

Một exception có `orderId`, `order`, `orderIdText` và `context.getOrderId()`. Hãy chọn một nơi làm nguồn sự thật. Giải thích vì sao giữ nguyên object `order` có thể làm state thay đổi và dữ liệu bí mật bị giữ lại.

### Bài 2 — Mã lỗi hay subtype?

Thiết kế `InsufficientStockException` với `ErrorCode.INSUFFICIENT_STOCK`. Nêu một trường hợp mà code thật sự hữu ích (mapping sang API/metric) và một trường hợp subtype Java đã đủ. Nếu giữ cả hai, constructor phải ngăn loại và code trái nhau ra sao?

### Bài 3 — Snapshot collection

Chạy `ImmutableContextDemo`, thử bỏ `List.copyOf` khỏi compact constructor. Sau khi `original.add("C")`, `context.itemIds().size()` sẽ thay đổi như thế nào? Tại sao `private final BatchContext context` không đủ bảo đảm snapshot nếu record giữ tham chiếu tới danh sách mutable?

### Bài 4 — Cause hay field riêng?

Trong `PaymentProcessingDemo`, thử thêm `private final IOException original;` cùng tham chiếu được đưa vào `super(message, e)`. Nó có tạo thêm khả năng xử lý nào không? **Không**, nếu caller đã có `getCause()` và không cần hợp đồng typed đặc biệt khác; nên loại bản sao.

### Bài 5 — Bảo mật

Thay ví dụ `SafeValidationContextDemo` để input là một token giả rất dài. Kiểm tra rằng stdout và các getter vẫn **không** xuất token. Nêu các nơi khác có thể vô tình log raw input và cách kiểm soát.

### Bài 6 — Enum và giao thức bên ngoài

Giả sử API trả JSON code `"STOCK_LOW"`, còn enum Java tên `INSUFFICIENT_STOCK`. Thiết kế mapper explicit thay vì phụ thuộc `ordinal()` hoặc ngầm coi `enum.name()` là hợp đồng bất biến. Test mapping theo từng mã công khai.

### Bài 7 — Nhiều lỗi validation

Một form có đồng thời 3 trường sai. Bạn sẽ tạo 3 suppressed exceptions hay một `ValidationResult` chứa 3 `ValidationIssue`? Giải thích tại sao kết quả cấu trúc thường diễn đạt validation dự kiến rõ hơn suppressed errors.

### Bài 8 — Dữ liệu có thể suy ra

Một context lưu `requested=7`, `availableAtFailure=3`, `shortage=4`. Nếu shortage luôn là `max(0, requested-available)`, hãy bỏ trường dư và tính khi cần. Nếu nghiệp vụ có quy tắc làm tròn hoặc tỷ giá theo thời điểm, lúc nào giá trị derived snapshot lại đáng giữ?

### Bài 9 — Kiểm thử hợp đồng

Viết JUnit test xác nhận `PaymentProcessingException` là checked, có code/context đúng và `getCause()` là `IOException` gốc. Không assert chuỗi `getMessage()` nếu message không phải hợp đồng máy ổn định.

### Bài 10 — Thiết kế giới hạn sử dụng

Một exception đang lưu danh sách 100.000 item IDs. Đây là thông tin có thật nhưng có nên đưa hết vào Throwable không? Hãy đề xuất một batch ID, số lượng, vài chỉ số lỗi hoặc một vị trí lưu báo cáo riêng, để exception không giữ dữ liệu quá lớn.

## 22. Checklist thiết kế exception có ngữ cảnh

- [ ] Custom exception có thật sự cần thiết ngoài các exception chuẩn hoặc kiểu result không?
- [ ] Chọn đúng checked/unchecked theo hợp đồng của caller chưa?
- [ ] Có xác định rõ ai sử dụng mỗi field context không?
- [ ] Field có kiểu phù hợp (`enum`, số, ID typed, `record`) thay vì parse message chưa?
- [ ] Các trường có trạng thái nhất quán và được validate trong constructor chưa?
- [ ] Những giá trị có thể tính lại đã được **derive thay vì lưu lặp** chưa?
- [ ] Có tránh lưu `cause`/stack/suppressed lần thứ hai khi `Throwable` đã cung cấp API chưa?
- [ ] Nếu subtype và code cùng biểu đạt một loại lỗi, chúng có được bảo đảm nhất quán không?
- [ ] Có tránh lưu object domain lớn, mutable, collection thừa hoặc resource đang mở chưa?
- [ ] Những collection context cần snapshot đã được defensive copy chưa?
- [ ] Đã biết `final` không khiến toàn bộ object graph bất biến chưa?
- [ ] Message có phục vụ con người và không bị code phân tích để ra quyết định chưa?
- [ ] Có loại trừ token, password, body/header nhạy cảm khỏi message/context chưa?
- [ ] Logger/API response có chính sách riêng và không serialize raw Throwable ra ngoài chưa?
- [ ] ID có được dùng đúng trong logging/tracing thay vì metric label high-cardinality chưa?
- [ ] Những field thêm vào exception có ý nghĩa về versioning/serialization nếu hệ thống dùng chúng chưa?
- [ ] Test có kiểm tra type/code/context/cause, snapshot và đường lỗi không?
- [ ] Khi error xảy ra, caller có cách xử lý hoặc báo thất bại đúng hợp đồng chưa?

**Kết luận:** Hãy thiết kế custom exception như một **báo cáo thất bại có cấu trúc nhỏ gọn**. Giữ những trường typed thật sự hữu ích cho caller, dùng `enum`/`record` khi tăng sự rõ ràng, kiểm soát snapshot và quyền riêng tư, **không nhân bản dữ liệu đã có** trong `Throwable` hoặc trường khác. `message` giúp con người hiểu; `code` và context giúp phần mềm xử lý; `cause` giữ đường nguyên nhân. Cân bằng ba nhóm thông tin này làm exception dễ đọc, dễ kiểm thử và ổn định hơn khi ứng dụng lớn lên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-006">Thiết kế hệ phân cấp theo cách bên gọi cần xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế hệ phân cấp Exception theo cách bên gọi cần xử lý

> **Java Core – Exception** · Nhóm: **Ngoại lệ tùy chỉnh (custom exceptions)** · Mục tiêu: thiết kế cây ngoại lệ theo **quyết định xử lý của caller**, không theo tên class nội bộ của hệ thống. Ví dụ chính tương thích **Java 17+**.

## 1. Tư tưởng thiết kế cốt lõi

Một exception có ích không chỉ nói “đã có lỗi gì” mà còn giúp **bên gọi biết nên làm gì tiếp theo**.

Ví dụ trong chức năng thanh toán/đặt hàng, khi giao dịch thất bại, caller có thể cần một trong các hành động khác biệt:

- **Yêu cầu khách hàng sửa đầu vào:** địa chỉ giao hàng thiếu thông tin, dữ liệu không hợp lệ.
- **Thử lại sau theo chính sách an toàn:** dịch vụ thanh toán tạm thời không phản hồi, quá tải.
- **Chuyển kiểm tra thủ công:** giao dịch cần người quản trị xác minh.
- **Báo lỗi kỹ thuật:** ứng dụng vi phạm invariant hoặc có bug, không được coi là lỗi thông thường cần khách sửa.

Những hành động trên là cơ sở để chọn các **nhánh exception**. Một cây ngoại lệ nên thể hiện sự khác nhau có giá trị đối với người xử lý lỗi, thay vì phản ánh mọi lớp công nghệ bên trong.

**Nguyên tắc:** *Nếu hai lỗi bắt buộc caller xử lý giống nhau, chưa chắc chúng cần hai lớp exception khác nhau. Nếu hai lỗi đòi hỏi hai hành động khác nhau và caller phải phân biệt ổn định, đó là tín hiệu mạnh để có hai kiểu riêng hoặc một mã lỗi có cấu trúc rõ ràng.*

### 1.1. Hệ phân cấp đề xuất cho một API Checkout

~~~text
java.lang.Throwable
└── java.lang.Exception
    └── CheckoutException                  ← hợp đồng checked của API
        ├── CorrectionNeededException      ← khách hàng cần sửa dữ liệu
        ├── RetryableCheckoutException     ← lỗi tạm thời, xử lý retry có điều kiện
        └── ManualReviewException          ← chuyển kiểm tra thủ công

java.lang.RuntimeException
└── IllegalStateException                  ← lỗi kỹ thuật, không phải lỗi Checkout dự kiến
~~~

Cây này là **ví dụ mô hình hóa**, không phải bộ exception chuẩn có sẵn trong JDK. Các kiểu Checkout sẽ được định nghĩa đầy đủ trong chương trình runnable ở phần 5.

Caller có thể tổ chức handler dựa trên hành động:

~~~java
try {
    checkout(request);
} catch (CorrectionNeededException ex) {
    askCustomerToFix(ex);
} catch (RetryableCheckoutException ex) {
    scheduleSafeRetry(ex);
} catch (ManualReviewException ex) {
    sendToReviewQueue(ex);
} catch (CheckoutException ex) {
    reportGenericCheckoutFailure(ex);
}
~~~

Đây là **đoạn mã phác họa**, chưa thể chạy độc lập vì các biến, kiểu và phương thức handler là tên tượng trưng. Nó minh họa đúng chủ ý: caller chọn nhánh bằng **khả năng xử lý**, không cần biết tên package chứa gateway hay loại driver mạng.

## 2. Đừng phân cấp chỉ theo nguồn lỗi kỹ thuật

Cách phân loại thuần kỹ thuật thường giống:

~~~text
ServiceException
├── DatabaseException
├── HttpException
├── SocketException
├── JsonException
└── QueueException
~~~

Nếu caller của chức năng đặt hàng không có khả năng xử lý riêng một lỗi database so với một lỗi socket, việc phải catch từng loại chỉ buộc tầng nghiệp vụ biết cấu trúc hạ tầng. Đổi từ JDBC sang HTTP hoặc thay thư viện JSON có thể làm **hợp đồng exception bị thay đổi** dù cách caller phục hồi không đổi.

Ví dụ:

| Nguyên nhân hạ tầng | Ý nghĩa đối với caller | Nhánh lỗi nghiệp vụ khả dĩ |
| --- | --- | --- |
| Gateway trả tín hiệu tạm quá tải | Có thể lên lịch thử lại **nếu an toàn** | `RetryableCheckoutException` |
| Timeout trước khi biết giao dịch đã hoàn tất chưa | Cần đối soát/idempotency trước khi retry | Không tự động suy mọi timeout là retryable |
| Người dùng gửi địa chỉ thiếu trường | Yêu cầu sửa đầu vào | `CorrectionNeededException` |
| Quy tắc gian lận yêu cầu phê duyệt | Đưa vào hàng đợi kiểm tra | `ManualReviewException` |
| Một thao tác trên đối tượng null ngoài hợp đồng | Báo bug/hạ tầng, không giả vờ đã xử lý nghiệp vụ | `NullPointerException` hoặc lỗi kỹ thuật thích hợp |

**Quan trọng:** cùng một loại exception kỹ thuật có thể dẫn tới nhiều quyết định nghiệp vụ, tùy ngữ cảnh. Ví dụ một `IOException` có thể là timeout có khả năng phục hồi, tệp cấu hình bị hỏng cần vận hành can thiệp hoặc lỗi quyền truy cập không nên retry. Vì thế đừng ánh xạ *mọi* `IOException` thành `RetryableCheckoutException` theo thói quen. Phải xác định **ý nghĩa và trạng thái thao tác**.

Ngược lại, hai công nghệ khác nhau có thể gây lỗi cần xử lý giống nhau. Nếu trong cả hai trường hợp caller chỉ có thể đề nghị khách nhập lại thông tin, có thể gom thành cùng nhánh lỗi nghiệp vụ kèm chi tiết có cấu trúc.

## 3. Bắt đầu từ quyết định của caller, không bắt đầu từ extends

Thiết kế tốt bắt đầu bằng **bảng hành động**:

| Câu hỏi caller cần trả lời | Hành động sau khi bắt | Khác biệt type có ích? |
| --- | --- | --- |
| Khách có thể sửa đầu vào không? | Hiển thị lỗi cụ thể, cho nhập lại | Có nếu khác retry/escalate |
| Hệ thống có thể retry an toàn không? | Lên lịch retry có giới hạn và idempotency | Có nếu cần nhánh retry riêng |
| Cần con người kiểm tra không? | Chuyển quy trình xử lý thủ công | Có nếu cách xử lý khác hẳn |
| Đây có phải lỗi lập trình không? | Ghi log, báo động, dừng đúng ranh giới | Nên tách khỏi nhóm lỗi dự kiến |
| Tất cả lỗi chỉ cần trả cùng thông báo? | Dùng handler chung ở boundary | Có thể chỉ cần một lớp + mã lỗi |
| Caller không thể khắc phục được? | Cho lỗi lan hoặc bọc để thêm ngữ cảnh | Không cần tạo subtype vô nghĩa |

Quy trình thực hiện:

1. Viết ra **những phản ứng hợp lệ** mà caller thực sự có thể thực hiện.
2. Gom các tình huống có cùng phản ứng thành một nhóm.
3. Xác định các nhánh cần phân biệt **ở thời điểm biên dịch** (kiểu exception) và các chi tiết chỉ là **dữ liệu** (mã, ID, message).
4. Chọn base exception có ý nghĩa làm hợp đồng chung của API.
5. Chọn chính sách checked/unchecked phù hợp cho từng tầng.
6. Xác định nơi chuyển exception hạ tầng thành exception hợp đồng, giữ nguyên cause.
7. Viết caller demo hoặc test trước để kiểm tra cây exception có thực sự giúp điều khiển logic hay không.

Nếu caller phải viết nhiều câu `instanceof`, đọc chuỗi `getMessage()` hoặc ép kiểu liên tục để xác định nên làm gì, hệ phân cấp hiện tại có thể chưa phản ánh đúng phân loại hành động.

## 4. Checked hay unchecked trong hệ phân cấp custom?

Java có hai nhóm cơ bản:

- **Checked exception**: kế thừa `Exception` nhưng **không** thuộc nhánh `RuntimeException`. Compiler yêu cầu xử lý hoặc khai báo `throws` phù hợp.
- **Unchecked exception**: kế thừa `RuntimeException` hoặc `Error`. Compiler không bắt buộc khai báo trong chữ ký.

Ví dụ dưới đây thiết kế `CheckoutException extends Exception`, vì ba tình huống nghiệp vụ được xem là **kết quả thất bại dự kiến** mà caller của API nên có chính sách. Chữ ký:

~~~java
static String checkout(String scenario) throws CheckoutException
~~~

buộc bên gọi của method phải xử lý hoặc khai báo exception checked theo quy tắc Java.

Điều đó **không có nghĩa mọi lỗi nghiệp vụ luôn phải checked**. Một thư viện có thể chọn unchecked theo chính sách API, đặc biệt nếu tầng gọi không thể phục hồi trực tiếp hoặc framework mong cơ chế propagation tự do. Khi quyết định, xét:

- Caller **có thể và thường phải** phản ứng một cách hữu ích không?
- Có hợp lý khi buộc mọi caller khai báo/bắt nhóm này không?
- Thay đổi API trong tương lai có làm người dùng mã nguồn phải sửa hàng loạt không?
- Hệ thống có tầng boundary dùng thông tin lỗi để chuyển thành HTTP response, job retry hoặc trạng thái giao dịch không?

**Không dùng `Error` làm base cho lỗi nghiệp vụ.** `Error` thuộc nhánh dành cho các tình huống nghiêm trọng mà ứng dụng thường không kỳ vọng khôi phục. Cũng không nên custom exception trực tiếp từ `Throwable` nếu không có lý do cực kỳ đặc biệt: như vậy dễ tạo nhóm không được các handler `catch (Exception)` thông thường bao phủ.

## 5. Chương trình Java 17 hoàn chỉnh: caller chọn cách xử lý

Lưu mã thành tệp **`CallerOrientedExceptionsDemo.java`**. Để chương trình chạy trong một file, các exception được khai báo là **public static nested classes**. Trong thư viện thực tế, các exception public thường được đặt trong những file riêng để tạo API rõ ràng.

~~~java
import java.io.IOException;

public class CallerOrientedExceptionsDemo {
    public static abstract class CheckoutException extends Exception {
        private static final long serialVersionUID = 1L;

        protected CheckoutException(String message) {
            super(message);
        }

        protected CheckoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class CorrectionNeededException
            extends CheckoutException {
        private static final long serialVersionUID = 1L;

        public CorrectionNeededException(String message) {
            super(message);
        }
    }

    public static final class RetryableCheckoutException
            extends CheckoutException {
        private static final long serialVersionUID = 1L;

        public RetryableCheckoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ManualReviewException
            extends CheckoutException {
        private static final long serialVersionUID = 1L;

        public ManualReviewException(String message) {
            super(message);
        }
    }

    private static String checkout(String scenario)
            throws CheckoutException {
        switch (scenario) {
            case "invalid":
                throw new CorrectionNeededException(
                        "shipping address missing");
            case "temporary":
                IOException technical =
                        new IOException("gateway timeout");
                throw new RetryableCheckoutException(
                        "payment service temporarily unavailable",
                        technical);
            case "review":
                throw new ManualReviewException(
                        "risk review needed");
            case "unexpected":
                throw new IllegalStateException(
                        "internal invariant broken");
            case "ok":
                return "ORDER-100";
            default:
                throw new CorrectionNeededException(
                        "unknown request");
        }
    }

    private static void caller(String scenario) {
        System.out.println("== " + scenario + " ==");

        try {
            System.out.println("receipt=" + checkout(scenario));
        } catch (CorrectionNeededException ex) {
            System.out.println(
                    "action=ask-customer; reason=" + ex.getMessage());
        } catch (RetryableCheckoutException ex) {
            System.out.println(
                    "action=retry-later; reason=" + ex.getMessage());
            System.out.println("cause="
                    + ex.getCause().getClass().getSimpleName());
        } catch (ManualReviewException ex) {
            System.out.println(
                    "action=manual-review; reason=" + ex.getMessage());
        } catch (CheckoutException ex) {
            System.out.println(
                    "action=generic-checkout-failure; type="
                    + ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            System.out.println(
                    "action=technical-alert; type="
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        caller("invalid");
        caller("temporary");
        caller("review");
        caller("ok");
        caller("unexpected");
    }
}
~~~

Chạy từ thư mục chứa file, với JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 -encoding UTF-8 CallerOrientedExceptionsDemo.java
java CallerOrientedExceptionsDemo
~~~

**Stdout chính xác, đã được kiểm chứng khi biên dịch và chạy chương trình:**

~~~text
== invalid ==
action=ask-customer; reason=shipping address missing
== temporary ==
action=retry-later; reason=payment service temporarily unavailable
cause=IOException
== review ==
action=manual-review; reason=risk review needed
== ok ==
receipt=ORDER-100
== unexpected ==
action=technical-alert; type=IllegalStateException
~~~

Chương trình dùng mã mô phỏng ổn định, không gọi mạng thật hoặc xử lý thanh toán thật. Việc in `action=retry-later` **chỉ minh họa quyết định của caller**, không thực hiện retry tự động.

## 6. Vì sao cây exception trong ví dụ hợp lý?

### 6.1. Một base checked mô tả hợp đồng của API

~~~java
public static abstract class CheckoutException extends Exception {
    private static final long serialVersionUID = 1L;

    protected CheckoutException(String message) {
        super(message);
    }

    protected CheckoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Class gốc `CheckoutException` nói rằng có một **thất bại dự kiến thuộc quá trình checkout**. Nó không nêu hành động cụ thể, nhưng cho phép code cấp cao khai báo một hợp đồng chung: `throws CheckoutException`.

Chọn `abstract` khiến tác giả dịch vụ phải sử dụng subtype (hoặc tạo thêm subtype khi thật sự cần) thay vì ném một lỗi base mơ hồ trong những tình huống cần phân biệt. Các constructor `protected` hỗ trợ lớp con truyền message/cause và tránh bị gọi tùy tiện từ code ngoài khi muốn duy trì chính sách phân loại.

**Lưu ý:** Không phải mọi project đều cần base abstract. Nếu hệ thống chủ yếu có một loại lỗi nghiệp vụ, một exception cụ thể duy nhất có thể rõ ràng hơn cây phân cấp. Chỉ tạo base khi có lợi ích thật đối với phương thức caller và quản lý hợp đồng.

### 6.2. CorrectionNeededException: người dùng cần sửa dữ liệu

Trong nhánh `invalid`, dịch vụ ném:

~~~java
throw new CorrectionNeededException(
        "shipping address missing");
~~~

Caller thấy kiểu `CorrectionNeededException` và thực hiện hành động cụ thể: hiển thị lỗi cho người dùng/cho nhập lại. Không cần bắt `IllegalArgumentException` cho mọi sự cố rồi đoán xem đó là lỗi dữ liệu khách nhập hay bug trong hệ thống.

**Đừng trả trực tiếp message nội bộ cho khách hàng** nếu nó chứa thông tin riêng tư hoặc cấu trúc hệ thống. Trong API thật, handler nên ánh xạ sang mã lỗi ổn định, nội dung UI đã được chọn lọc và có thể quốc tế hóa.

### 6.3. RetryableCheckoutException: có khả năng thử lại theo chính sách

Trong nhánh `temporary`, một `IOException("gateway timeout")` được bọc:

~~~java
throw new RetryableCheckoutException(
        "payment service temporarily unavailable",
        technical);
~~~

Caller bắt nhánh retry và thấy `getCause()` là `IOException`. Điều này giữ được dấu vết kỹ thuật mà không bắt caller phụ thuộc trực tiếp vào loại exception của thư viện mạng.

**Đặc biệt quan trọng trong thanh toán:** “lỗi có thể thử lại” không đồng nghĩa “gọi lại ngay là an toàn”. Một timeout có thể xảy ra **sau khi bên nhận đã ghi giao dịch thành công**. Retry cần đi kèm kiểm tra trạng thái, **idempotency key**, giới hạn số lần, backoff, timeout và tránh nhân đôi khoản thu. Nếu không thể xác minh an toàn, phân loại sang trạng thái cần đối soát/thủ công thay vì gắn nhãn retryable theo cảm tính.

Ví dụ chỉ **chỉ thị caller dự kiến hành động**, không đưa ra một thuật toán retry giao dịch tài chính thật.

### 6.4. ManualReviewException: cần người xử lý

Nhánh `review` biểu thị giao dịch chưa thể được tự động hoàn tất. Caller có thể tạo ticket hoặc trạng thái chờ phê duyệt, thay vì coi đây là lỗi tạm thời để retry vô hạn hoặc lỗi nhập liệu buộc người dùng sửa.

Một API thật có thể cần trạng thái `PENDING_REVIEW` dưới dạng **kết quả nghiệp vụ** thay vì exception, nhất là nếu chờ kiểm tra là một phần của luồng hoạt động bình thường. Hãy dùng exception khi đó thực sự là **đường kết thúc bất thường** theo hợp đồng, không vì muốn lưu mọi trạng thái trong một class Throwable.

### 6.5. IllegalStateException: lỗi không thuộc nhóm nghiệp vụ dự kiến

Trong nhánh `unexpected`, code chủ động tạo `IllegalStateException` để biểu diễn bất biến nội bộ bị phá vỡ. Nó không kế thừa `CheckoutException`, và caller mẫu bắt tại boundary bằng `catch (RuntimeException)` để in một **cảnh báo kỹ thuật**.

Đây là cấu trúc mô phỏng cho mục đích quan sát. **Không nên bắt mọi RuntimeException rồi mặc nhiên tiếp tục nghiệp vụ** như thể đã phục hồi. Trong ứng dụng thật, boundary có thể log, trả lỗi máy chủ, dừng job hoặc chuyển sự cố đến bộ phận vận hành. Những lỗi logic như `NullPointerException`, `IndexOutOfBoundsException` không nên được đóng gói thành lỗi “khách nhập sai” chỉ để đạt một handler chung.

## 7. Thứ tự catch và hợp đồng subtype

Khi có cả subtype và base trong một cấu trúc:

~~~java
try {
    checkout("temporary");
} catch (CorrectionNeededException ex) {
    handleCorrection(ex);
} catch (RetryableCheckoutException ex) {
    handleRetryable(ex);
} catch (ManualReviewException ex) {
    handleReview(ex);
} catch (CheckoutException ex) {
    handleOtherCheckout(ex);
}
~~~

Đây là **đoạn minh họa**, các phương thức handler cần định nghĩa. Thứ tự **các subtype trước base** là bắt buộc để tránh catch base che các catch cụ thể. Nếu đặt `catch (CheckoutException)` lên trước, các nhánh con ở phía sau sẽ **không thể tới được** và compiler từ chối.

Ba subtype ngang hàng trong ví dụ không kế thừa lẫn nhau, vì vậy thứ tự **giữa chính ba subtype** không bị ràng buộc bởi quan hệ superclass/subclass. Có thể xếp theo cách người đọc hiểu rõ luồng xử lý nhất.

### Vì sao vẫn có catch base ở cuối?

Trong chương trình chính, `catch (CheckoutException ex)` là **nhánh dự phòng** để xử lý một subtype khác có thể được bổ sung sau này, hoặc một đường phát sinh từ phương thức có chữ ký chung.

Nếu thư viện phát hành thêm một subtype mới, caller cũ đang bắt base ở cuối vẫn có chính sách fallback. Nhưng fallback cũng phải **an toàn**: không nên im lặng coi subtype chưa biết là “có thể retry”. Ví dụ in `generic-checkout-failure` thể hiện thái độ bảo thủ: báo lỗi chung hoặc chuyển xử lý, không tự suy hành động đặc thù.

**Cảnh báo:** catch base quá sớm làm mất ý nghĩa của hệ phân cấp. Có base để tiến hóa API khác với việc mọi tầng đều bắt base rồi bỏ qua phân loại.

## 8. Cần bao nhiêu cấp trong cây exception?

Một cây sâu có thể trông “kiến trúc” nhưng gây khó đọc:

~~~text
ApplicationException
  └─ DomainException
      └─ CommerceException
          └─ CheckoutException
              └─ PaymentException
                  └─ GatewayException
                      └─ TimeoutException
~~~

Nếu caller không có chính sách ở từng cấp, phần lớn các tầng là chi phí nhận thức và bảo trì. Những catch lồng nhau nhiều kiểu chung đôi khi khiến mã khó theo dõi và tên lớp không truyền được hành động.

Một cây **nông, hành động rõ ràng** thường dễ dùng hơn. Có thể áp dụng câu hỏi phản biện:

- Caller có **thực sự catch** kiểu trung gian này không?
- Nếu bỏ cấp trung gian, có mất một chính sách xử lý có ý nghĩa không?
- Hai subtype hiện tại khác nhau về **hành vi** hay chỉ về message/cause?
- Nếu một thay đổi thư viện kỹ thuật xảy ra, tên và phân cấp custom có buộc caller sửa không?
- Nếu phát hành subtype mới, caller cũ có fallback an toàn không?

Không phải mọi tình huống mới đều cần một subtype. Nếu hai sự cố đều yêu cầu người dùng sửa đầu vào, có thể dùng cùng `CorrectionNeededException` và thêm mã lý do có cấu trúc.

## 9. Dùng mã lỗi có cấu trúc thay vì tạo class cho mọi chi tiết

Giả sử một nhóm lỗi yêu cầu sửa dữ liệu có nhiều lý do: thiếu địa chỉ, thiếu số điện thoại, mã bưu chính không hợp lệ. Nếu mọi lý do đều dẫn đến một hành động chung, tạo ba class exception mới có thể không cần thiết.

Một cách mô hình hóa:

~~~java
enum CorrectionCode {
    ADDRESS_MISSING,
    PHONE_MISSING,
    POSTAL_CODE_INVALID
}

final class CorrectionNeededException extends Exception {
    private final CorrectionCode code;

    CorrectionNeededException(CorrectionCode code, String message) {
        super(message);
        this.code = code;
    }

    CorrectionCode code() {
        return code;
    }
}
~~~

Đoạn trên là **các khai báo kiểu Java hợp lệ để minh họa thiết kế**, chưa bao gồm chương trình `main`. Caller có thể bắt một kiểu `CorrectionNeededException`, xử lý chung việc cho sửa dữ liệu rồi ánh xạ `code()` đến trường UI thích hợp.

Tuy nhiên, **nếu mã lỗi bắt đầu quyết định những luồng xử lý hoàn toàn khác nhau**, một enum quá rộng chỉ chuyển gánh nặng từ các catch thành một `switch` phức tạp. Khi đó hãy cân nhắc dùng subtype hoặc một mô hình kết quả nghiệp vụ khác.

**Không phân loại lỗi bằng cách so sánh `getMessage()`**: message dành cho người đọc/log, có thể đổi do bản dịch hoặc chỉnh câu chữ. Class type, enum code và hợp đồng API rõ ràng đáng tin hơn.

## 10. Giữ cause khi chuyển lỗi hạ tầng thành lỗi nghiệp vụ

Ngoại lệ tùy chỉnh thường nằm tại **ranh giới tầng**. Tầng kỹ thuật có thể ném `IOException`, `SQLException` hoặc exception của HTTP client; tầng dịch vụ chuyển sang exception mang ý nghĩa hợp đồng.

Mẫu:

~~~java
try {
    gatewayCall();
} catch (IOException ex) {
    if (knownTransientCondition(ex)) {
        throw new RetryableCheckoutException(
                "Gateway temporarily unavailable", ex);
    }
    throw new IllegalStateException(
            "Gateway failure needs investigation", ex);
}
~~~

Đây là **mã minh họa**, `gatewayCall()` và `knownTransientCondition()` chưa được định nghĩa. Điều quan trọng không phải luôn chọn đúng hai loại nêu trên, mà là **chỉ gắn nhãn retry sau khi phân loại hợp lệ** và **giữ nguyên cause** bằng constructor nhận `Throwable`.

Các lỗi thường gặp:

- Bắt mọi `Exception` rồi ném `new CheckoutException("failed")`, làm mất loại lỗi gốc và khả năng khôi phục.
- Bắt `IOException` rồi đánh dấu retryable mà không biết yêu cầu có thể đã được hệ thống ngoài thực hiện.
- Chỉ chuyển message sang exception mới, bỏ mất `cause` và stack trace nguồn gốc.
- Dùng exception hạ tầng làm public API dù caller không nên phụ thuộc vào thư viện đó.

Nếu cần log, một logger tốt sẽ ghi **exception object**, gồm cause và suppressed exceptions, không chỉ message. Cẩn trọng với thông tin riêng tư trong message.

## 11. Thiết kế constructor và thông tin lỗi

Custom exception nên cân nhắc cung cấp constructor phù hợp để lưu message và cause. Ví dụ base ở chương trình chính:

~~~java
protected CheckoutException(String message) {
    super(message);
}

protected CheckoutException(String message, Throwable cause) {
    super(message, cause);
}
~~~

Các constructor thực tế nằm bên trong lớp `CheckoutException`. Một lớp con có thể chỉ công khai những kiểu khởi tạo phù hợp với ngữ nghĩa của nó; không bắt buộc phải copy cả bốn overload constructor từ `Exception` chỉ để “đầy đủ”.

Một số thông tin bổ sung có thể hữu ích:

| Dữ liệu | Nên lưu bằng gì? | Lưu ý |
| --- | --- | --- |
| Loại hành động cần thực hiện | Subtype hoặc cấu trúc kết quả | Phải ổn định và có ý nghĩa caller |
| Mã lý do có cấu trúc | Enum/string code có hợp đồng | Không dùng message làm mã |
| Thông điệp chẩn đoán | `getMessage()` | Không lộ token, mật khẩu, dữ liệu nhạy cảm |
| Lỗi nguồn gốc | `cause` | Giữ chain để debug |
| Định danh giao dịch/operation | Trường typed hoặc context bên ngoài | Không công khai vô điều kiện |
| Thời gian retry dự kiến | Chính sách retry hoặc trường được chuẩn hóa | Không bảo đảm mọi lỗi transient đều retry an toàn |
| Lỗi cleanup thứ cấp | `getSuppressed()` | Không đồng nhất với cause |

Nếu một class exception là API công khai có khả năng được serialize, cần cân nhắc tương thích giữa phiên bản. Các exception trong chương trình chính khai báo `serialVersionUID` vì `Throwable` hỗ trợ `Serializable`; trường này không làm ngoại lệ an toàn hơn, cũng không thay thế chính sách versioning/serialization.

## 12. Checked exception không thể tự động bắt RuntimeException

Trong chương trình chính, `checkout(String)` khai báo:

~~~java
private static String checkout(String scenario)
        throws CheckoutException
~~~

Method vẫn có thể ném `IllegalStateException` vì đó là unchecked exception. Caller bắt từng subtype `CheckoutException`, sau đó có một handler `RuntimeException` ở tầng boundary để ghi cảnh báo.

**Không suy rằng một phương thức khai báo `throws CheckoutException` thì mọi thất bại runtime đều phải là subtype của CheckoutException.** Compiler chỉ áp dụng quy tắc `throws` bắt buộc cho checked exceptions; unchecked exceptions có thể phát sinh từ code nội bộ bất kể chữ ký.

Cũng không nên viết mọi nghiệp vụ như sau:

~~~java
try {
    doSomething();
} catch (Exception ex) {
    System.out.println("Handled");
}
~~~

Đây là đoạn minh họa thiếu định nghĩa `doSomething()`. Nó bắt rất nhiều exception thuộc nhánh `Exception`, bao gồm cả `RuntimeException` do bug, và biến tất cả thành một thông báo chung. Trong nhiều hệ thống, cách này làm lỗi bị “nuốt” và chương trình tiếp tục ở trạng thái không nhất quán. Một handler rộng chỉ nên tồn tại tại boundary có chính sách phản hồi, logging và dừng/khôi phục rõ.

## 13. Hợp đồng tiến hóa của một thư viện public

Một hệ phân cấp exception có thể là **public API**. Khi đã có caller thực tế, thay đổi cây không còn chỉ là việc đổi tên class.

### 13.1. Thêm subtype mới

Ví dụ một bản phát hành thêm `VerificationPendingException extends CheckoutException`. Một caller cũ chỉ có ba catch cụ thể **và** catch base cuối vẫn xử lý theo fallback chung. Một caller cố liệt kê toàn bộ subtype mà không có fallback có thể cần điều chỉnh tùy chữ ký và cấu trúc code.

Đừng để subtype mới âm thầm mặc định được retry nếu nó chưa có chính sách rõ. Chọn fallback bảo thủ ở boundary, đồng thời đánh dấu metric/log để đội phát triển bổ sung xử lý khi cần.

### 13.2. Đổi checked thành unchecked hoặc ngược lại

Đổi `extends Exception` thành `extends RuntimeException` có thể thay đổi cả cách compiler yêu cầu `throws`/catch và ý nghĩa hợp đồng đối với caller. Đổi ngược lại có thể khiến nhiều nơi gọi không biên dịch. Cần coi đây là **thay đổi API quan trọng**, không chỉ sửa inheritance cho đẹp.

### 13.3. Thay đổi cha/con của một exception

Nếu một subtype đang được xử lý bởi `catch (RetryableCheckoutException)` mà sau này chuyển sang nhóm khác, behavior của caller sẽ đổi dù exception class vẫn giữ tên. Các handler theo kiểu runtime phụ thuộc cây kế thừa.

Vì vậy tổ chức class theo **hành động caller ổn định** có giá trị lâu dài hơn gắn chặt vào hạ tầng vốn thay đổi thường xuyên.

### 13.4. Không nên hứa cây class là tập đóng khi không bảo đảm

Các subtype được đánh dấu `final` trong chương trình mẫu để không tạo thêm những nhánh con tùy ý bên dưới từng hành động. Nhưng base `CheckoutException` vẫn cho phép thêm subtype trong tương lai. Do đó **không** được hiểu cây hiện tại chỉ có ba trường hợp mãi mãi.

Java 17 có sealed classes để hạn chế tập subtype khi cần một phân loại đóng và được kiểm soát, nhưng đó là quyết định riêng: sealed hierarchy có thể thích hợp khi API muốn exhaustive modeling, trong khi hệ thống cần mở rộng plugin/exceptions có thể không muốn đóng cây. Đừng chọn sealed chỉ vì Java 17 có hỗ trợ.

## 14. Kiểm thử hệ phân cấp theo hành vi của caller

Một bài kiểm thử tốt không chỉ kiểm tra “đã ném exception”, mà còn kiểm tra **caller chọn đúng phản ứng**.

Với ví dụ chính:

| Scenario | Exception hoặc giá trị trả về | Hành động caller |
| --- | --- | --- |
| `invalid` | `CorrectionNeededException` | Yêu cầu sửa dữ liệu |
| `temporary` | `RetryableCheckoutException` với cause `IOException` | Đánh dấu xử lý retry sau, không retry mù |
| `review` | `ManualReviewException` | Chuyển kiểm tra thủ công |
| `ok` | `ORDER-100` | Xử lý thành công |
| `unexpected` | `IllegalStateException` | Cảnh báo kỹ thuật tại boundary |

Các kiểm thử thực tế nên xét:

1. **Đúng kiểu exception** khi một tình huống đã được phân loại.
2. **Cause có được giữ không** khi dịch từ hạ tầng sang hợp đồng nghiệp vụ.
3. **Hành động caller** có đúng hay không, tránh bug biến lỗi cần sửa thành retry.
4. **Fallback có an toàn không** khi gặp subtype chưa biết.
5. **Không nuốt RuntimeException** và không trả về kết quả thành công giả.
6. **Không dựa vào message** để chọn nhánh; message có thể thay đổi.
7. **Chính sách retry có idempotency/bounded retry** và không nhân đôi tác vụ có side effect.
8. **Lỗi kỹ thuật có log/metric thích hợp** mà không rò rỉ dữ liệu nhạy cảm.

Trong chương trình console, việc kiểm chứng stdout theo từng scenario đã chứng minh cơ chế catch và ý nghĩa của cây. Khi chuyển vào project, có thể dùng JUnit 5 (nếu đã có) để viết test riêng cho service và caller; không cần thêm framework chỉ để giải thích quy tắc Java.

## 15. So sánh các phương án thiết kế

| Phương án | Khi phù hợp | Rủi ro |
| --- | --- | --- |
| Một class `CheckoutException` duy nhất | Mọi caller đều xử lý chung | Không có phân biệt hành động trực tiếp bằng kiểu |
| Base + subtype theo hành động | Caller cần sửa, retry, review theo nhánh khác nhau | Có thể nhiều class nếu lạm dụng |
| Một exception + enum reason | Cùng hành động nhưng nhiều nguyên nhân chi tiết | Enum có thể biến thành một switch phức tạp |
| Exception theo công nghệ (DB/HTTP/JSON) | Caller thực sự là tầng hạ tầng và biết cách khắc phục riêng | Rò rỉ implementation detail lên tầng nghiệp vụ |
| Unchecked exception tùy chỉnh | Chính sách API không buộc caller bắt từng lỗi | Caller có thể bỏ qua lỗi dự kiến |
| Kiểu kết quả như `Result/Status` thay vì exception | Thất bại/đang chờ là trạng thái bình thường thường gặp | Cần thiết kế result type và xử lý tường minh |
| Wrapper exception + cause | Chuyển tầng để thêm ngữ cảnh nhưng giữ lỗi gốc | Có thể lạm dụng việc bọc nhiều tầng và làm log dài |

**Điểm chọn lựa:** Một mô hình hợp lý không phải mô hình có nhiều subtype nhất, mà là mô hình khiến caller viết được **luồng xử lý đúng, dễ hiểu, ít phụ thuộc và an toàn**.

## 16. Những lỗi thiết kế thường gặp

1. **Tạo class cho từng thông báo lỗi.** Nếu cách khắc phục không đổi, sử dụng cùng class với mã lý do có cấu trúc.
2. **Tạo class theo từng tên driver/protocol.** Dễ làm hỏng hợp đồng khi đổi thư viện kỹ thuật.
3. **Mọi exception đều extends một base rất rộng rồi catch base ở mọi tầng.** Làm mất phân loại hành động.
4. **Dùng checked exception cho mọi bug.** Bắt lập trình viên xử lý cả những vấn đề không thể phục hồi hợp lý.
5. **Dùng unchecked exception để che những thất bại dự kiến nhưng không có chính sách.** Caller có thể bất ngờ mất cơ hội xử lý.
6. **Gắn nhãn retryable cho mọi timeout.** Rất nguy hiểm với thao tác có side effect nếu không có idempotency và đối soát.
7. **Đổi hệ phân cấp liên tục theo implementation.** Khiến caller cũ bị ảnh hưởng dù chức năng không đổi.
8. **Không giữ cause.** Khó truy dấu lỗi gốc trong log.
9. **So sánh message bằng chuỗi.** Ngôn ngữ hiển thị và wording không nên quyết định luồng xử lý.
10. **Cho người dùng thấy trực tiếp message hạ tầng.** Dễ rò rỉ cấu trúc hệ thống hoặc dữ liệu nhạy cảm.
11. **Tạo cây kế thừa quá sâu.** Gây chi phí bảo trì khi không có handler nào dùng tầng trung gian.
12. **Dùng exception cho một trạng thái nghiệp vụ bình thường.** Ví dụ pending approval thường là trạng thái hợp lệ, có thể mô hình hóa bằng result.
13. **Bọc tất cả RuntimeException thành lỗi người dùng nhập sai.** Che giấu lỗi lập trình.
14. **Không kiểm thử handler fallback.** Type mới xuất hiện có thể bị xử lý bằng hành động không an toàn.
15. **Giả định có cause thì caller nên catch kiểu cause.** Caller nên sử dụng contract exception của tầng hiện tại; cause chủ yếu phục vụ chẩn đoán hoặc một chiến lược xử lý được thiết kế rõ.
16. **Gọi là retryable nhưng không đưa chính sách retry vào hệ thống.** Phân loại exception chỉ là một phần của workflow; retry phải có giới hạn và kiểm soát.

## 17. Các câu hỏi tự kiểm tra

**Câu 1.** Tại sao `PaymentHttpException` và `PaymentDatabaseException` không nhất thiết là hai nhánh tốt trong API Checkout?

*Đáp án:* Nếu caller xử lý hai lỗi giống nhau và không cần biết công nghệ bên trong, sự phân chia này làm lộ hạ tầng. Nên gom theo chính sách xử lý thực tế khi phù hợp.

**Câu 2.** Ba quyết định “sửa dữ liệu”, “retry có điều kiện”, “kiểm tra thủ công” có thể được thể hiện thế nào?

*Đáp án:* Có thể tạo các subtype có ý nghĩa riêng dưới một base CheckoutException, hoặc mô hình hóa theo mã/trạng thái có cấu trúc khi phù hợp. Quan trọng là caller phân biệt được đúng hành động.

**Câu 3.** Nếu `CorrectionNeededException extends CheckoutException` thì catch nào phải đứng trước khi có cả hai?

*Đáp án:* `catch (CorrectionNeededException)` phải đứng trước `catch (CheckoutException)`. Đặt base trước che subtype và gây lỗi biên dịch.

**Câu 4.** Một `IOException` khi gọi payment gateway có luôn là lỗi nên retry không?

*Đáp án:* Không. Cần phân loại trạng thái thực tế, đánh giá idempotency và khả năng giao dịch đã được thực hiện trước timeout.

**Câu 5.** Vì sao constructor `RetryableCheckoutException(String, Throwable)` hữu ích?

*Đáp án:* Cho phép giữ cause hạ tầng khi đưa ra một exception nghiệp vụ ổn định, giúp caller xử lý theo contract và vẫn có dấu vết kỹ thuật để debug.

**Câu 6.** Một method khai báo `throws CheckoutException` có thể ném `NullPointerException` không?

*Đáp án:* Có. NullPointerException là unchecked và không buộc phải được liệt kê trong throws.

**Câu 7.** Nếu hai trường dữ liệu lỗi đều khiến khách nhập lại, có bắt buộc tạo hai subclass exception không?

*Đáp án:* Không. Có thể dùng cùng một lớp lỗi hành động “cần sửa” và một enum/code chi tiết, nếu đó là hợp đồng ổn định, dễ dùng.

**Câu 8.** Thêm subtype `VerificationPendingException` có nên mặc định được retry trong catch base không?

*Đáp án:* Không. Fallback cần bảo thủ: ghi nhận lỗi chưa được phân loại rõ, trả trạng thái tổng quát hoặc chuyển kiểm tra theo chính sách an toàn.

**Câu 9.** Nếu thao tác “pending review” là trạng thái thường xuyên, không phải lỗi bất thường, nên cân nhắc gì?

*Đáp án:* Dùng status/result object hoặc một kiểu kết quả có nhánh rõ, thay vì mặc nhiên ném exception để biểu diễn trạng thái bình thường.

**Câu 10.** Làm sao biết cây exception đang trở nên quá sâu?

*Đáp án:* Xem từng cấp có được caller catch để quyết định hành động thật không. Nếu nhiều cấp không có chính sách xử lý riêng, có thể đơn giản hóa.

## 18. Tài liệu đối chiếu

- [Java SE 17 JLS – Exceptions, chương 11](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): phân biệt checked và unchecked, phân tích exception và yêu cầu khai báo.
- [Java SE 17 JLS – try/catch, mục 14.20](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): quy tắc lựa chọn catch phù hợp và thứ tự các handler.
- [Java SE 17 JLS – throw, mục 14.18](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.18): điều kiện biểu thức và ngữ nghĩa ném Throwable.
- [Java SE 17 API – Exception](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Exception.html): class gốc checked phổ biến.
- [Java SE 17 API – RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): nhánh unchecked dành cho nhiều lỗi không buộc khai báo.
- [Java SE 17 API – Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): cause, message, stack trace và suppressed exceptions.
- [Java SE 17 API – IllegalStateException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalStateException.html): một ví dụ unchecked exception thể hiện trạng thái ứng dụng không hợp lệ.

**Tóm lại:** Hãy thiết kế hệ phân cấp **từ phía caller**: họ cần sửa dữ liệu, thử lại có kiểm soát, chuyển người xử lý hay báo lỗi kỹ thuật? Cho mỗi nhánh một **hợp đồng hành động ổn định**, không tạo class theo mọi chi tiết hạ tầng. Giữ cause, đặt catch cụ thể trước catch base, chọn checked/unchecked có chủ đích và để fallback an toàn khi API tiến hóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-007">Giữ thông tin lỗi độc lập với câu chữ của message</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ thông tin lỗi độc lập với câu chữ của `message` trong ngoại lệ tùy chỉnh Java

Một ngoại lệ Java thường có thông điệp được lấy bằng `getMessage()`, chẳng hạn `"quantity must be positive"`. Chuỗi này hữu ích cho người đọc và chẩn đoán, nhưng **không nên trở thành khóa điều khiển chương trình**: chỉ cần sửa câu chữ, dịch thông điệp sang tiếng Việt hoặc đổi thư viện là các nhánh `if` so sánh message có thể hoạt động sai.

Với **ngoại lệ tùy chỉnh (custom exception)**, cách thiết kế bền vững hơn là tách ba trách nhiệm:

1. **Kiểu ngoại lệ** biểu diễn nhóm thất bại phù hợp với hợp đồng API, ví dụ `ValidationException` hoặc `OrderException`.
2. **Mã lỗi và các thuộc tính có kiểu** biểu diễn sự thật mà chương trình cần dựa vào, ví dụ `ValidationCode.OUT_OF_RANGE`, tên trường `quantity` hoặc một mã thao tác đã được chuẩn hóa.
3. **`message`** biểu diễn một câu giải thích phục vụ người đọc/log, có thể thay đổi mà không làm đổi logic xử lý; **`cause`** giữ đối tượng ngoại lệ gốc khi có chuyển đổi lỗi giữa các tầng.

> Nguyên tắc: **điều khiển bằng kiểu và dữ liệu có cấu trúc; giải thích bằng message; giữ nguyên nhân bằng cause.** Không phân nhánh nghiệp vụ dựa trên việc message chứa một từ hay khớp nguyên văn một câu.

## 1. Vấn đề của việc dùng `getMessage()` làm mã lỗi

Ví dụ thiết kế mong manh:

```java
try {
    placeOrder();
} catch (IllegalArgumentException ex) {
    if ("quantity must be positive".equals(ex.getMessage())) {
        showQuantityError();
    } else {
        showGenericError();
    }
}
```

Đây là **mã minh họa**, giả định các phương thức được khai báo phù hợp. Chỉ cần người viết sửa câu từ `"quantity must be positive"` thành `"quantity must be greater than zero"`, người gọi có thể rơi vào nhánh lỗi chung, dù bản chất lỗi hoàn toàn không đổi.

Các vấn đề khác:

- **Địa phương hóa:** bản tiếng Việt `"Số lượng phải lớn hơn 0"` không khớp chuỗi tiếng Anh.
- **Thông tin ngữ cảnh:** message có thể thêm tên trường, mã yêu cầu hoặc thao tác, làm so sánh toàn chuỗi không còn đúng.
- **Thay thư viện:** một thư viện parse số có thể thay cách tạo `NumberFormatException.getMessage()`.
- **Thiếu message:** `Throwable.getMessage()` có thể trả về `null`; gọi `ex.getMessage().contains(...)` có nguy cơ ném `NullPointerException` ngay trong handler.
- **Độ ổn định:** message là giao diện chẩn đoán cho người đọc, không nhất thiết là giao thức máy móc ổn định.
- **An toàn:** cố đưa toàn bộ giá trị đầu vào hoặc bí mật vào message chỉ để dễ phân nhánh có thể làm lộ thông tin qua log và stack trace.

Cũng đừng sửa cách so sánh thành:

```java
if (ex.getMessage().contains("quantity")) {
    // ...
}
```

Vì một lỗi nội bộ không liên quan có thể tình cờ chứa từ `quantity`. So khớp văn bản bằng `contains` hay regex chỉ làm phụ thuộc vào câu chữ theo một cách khó kiểm soát hơn.

## 2. “Thông tin lỗi độc lập với message” thực chất là gì?

Giả sử quy tắc nhập liệu có ba nguyên nhân:

| Sự thật về lỗi | Mã có cấu trúc | Message có thể hiển thị |
| --- | --- | --- |
| Thiếu số lượng bắt buộc | `REQUIRED_FIELD` | `quantity is required` |
| Số lượng không phải số nguyên hợp lệ | `INVALID_INTEGER` | `quantity must be an integer` |
| Số lượng nằm ngoài phạm vi 1–100 | `OUT_OF_RANGE` | `quantity must be between 1 and 100` |

**Mã lỗi** mang ý nghĩa chương trình cần hiểu và phải được duy trì ổn định theo hợp đồng. **Message** giúp người đọc hiểu điều kiện thất bại, có thể được viết lại cho rõ, được dịch hoặc dùng từ khác trong log.

Khi caller cần biết lỗi ở trường nào, có thể thêm thuộc tính `field`:

```java
String field = ex.getField();
```

Câu trên là **mã minh họa**: `ex` phải là ngoại lệ tùy chỉnh có phương thức `getField()`. Caller dùng thuộc tính thay vì tìm từ `quantity` trong message.

Với lỗi có nhiều thông số, các thuộc tính còn có thể là `minAllowed`, `maxAllowed`, `operation` hoặc `retryable`. Nhưng **không nên thêm mọi dữ liệu thô** vào exception: chỉ giữ những gì cần thiết, có hợp đồng rõ và được phép xuất hiện trong đối tượng có thể bị log.

### Bốn loại dữ liệu cần tách riêng

| Thành phần | Mục đích | Có thể thay đổi vì lý do gì? |
| --- | --- | --- |
| `Throwable` subtype | Phân loại nhóm thất bại | Thiết kế API Java |
| Mã lỗi (`enum`/hằng giá trị) | Phân nhánh ổn định, mapping sang phản hồi | Chỉ đổi có kế hoạch tương thích |
| Ngữ cảnh có kiểu | Biết trường/tác vụ/quy tắc liên quan | Theo hợp đồng dữ liệu |
| `message` | Giải thích/log/chẩn đoán | Câu chữ, bản địa hóa, cải thiện trải nghiệm |
| `cause` | Bảo toàn ngoại lệ gốc | Phụ thuộc đường lỗi và thao tác được gọi |

Một exception có thể không có cause nếu lỗi phát sinh trực tiếp từ điều kiện nghiệp vụ; không nên tạo cause giả chỉ để mọi constructor đều có cause.

## 3. Chọn kiểu ngoại lệ phù hợp trước khi thêm thuộc tính

Trong bài toán **không phụ thuộc vào cách viết message**, câu hỏi đầu là caller cần phân biệt bằng **subtype**, **mã lỗi**, hay cả hai:

- Chọn subtype (`InvalidQuantityException`, `PaymentDeclinedException`) khi caller muốn `catch` theo một hành động khác biệt.
- Chọn một exception với `ValidationCode` typed khi nhiều lý do có cùng cách xử lý nhưng UI/API cần mã cụ thể.
- Chọn checked hay unchecked theo hợp đồng caller, không theo việc lỗi có thể “sửa được” hay không.
- Nếu xác thực thất bại là outcome dự kiến của một form, một `ValidationResult` có cấu trúc có thể phù hợp hơn ném exception hàng loạt.

Dù lựa chọn nào, **không so sánh `getMessage()` để điều khiển flow**.

## 4. Thiết kế `enum` mã lỗi

Một mã typed giúp người gọi phân nhánh mà không phụ thuộc ngôn ngữ của message:

~~~java
enum ValidationCode {
    REQUIRED_FIELD, INVALID_INTEGER, OUT_OF_RANGE
}
~~~

`enum` hữu ích trong cùng module; code public qua JSON nên có chuỗi ổn định/versioned cùng chính sách cho **unknown code**. Đừng dùng `ordinal()` làm mã giao thức: đổi thứ tự enum sẽ đổi số. Nếu tên enum có thể đổi theo refactor, giữ một property `publicCode` cố định thay vì dùng `name()` làm hợp đồng public không được kiểm soát.

**Mã phải mô tả ý nghĩa lỗi**, không chỉ là bản viết hoa message. Việc cập nhật bản dịch từ `"must be an integer"` sang `"enter a whole number"` không làm thay đổi `INVALID_INTEGER`.

## 5. Thuộc tính của custom exception nên được thiết kế thế nào?

Để **giữ thông tin lỗi độc lập với message**, custom exception có thể đặt `private final ValidationCode code` và `private final String field`, công bố getter/record accessor rõ nghĩa và kiểm tra `null` ở constructor. Luôn dùng `super(message, cause)` khi chuyển một lỗi kỹ thuật thực sự; không tạo `cause` giả cho lỗi validation tự phát.

Caller đọc `ex.getCode()` và `ex.getField()`; UI tự ánh xạ mã sang thông điệp an toàn theo locale. **Không cần** parse tên field hay dải giá trị từ `ex.getMessage()`. Khi exception vượt qua ranh giới JSON/API, chỉ serialize DTO có schema được cho phép, không công khai cả stack trace hoặc `Throwable`.

Các quy tắc chi tiết về bất biến sâu, tránh hai bản sao ID, snapshot collection và tối thiểu hóa context được áp dụng trong bài thiết kế ngữ cảnh có kiểu; ở đây, trọng tâm là chứng minh **đổi câu chữ không làm đổi kết quả phân loại**.

## 6. Ví dụ Java 17+ hoàn chỉnh: validation có mã, trường và cause

Ví dụ này mô phỏng kiểm tra số lượng đơn hàng, không cần file hoặc dịch vụ ngoài. Lưu tệp **`StructuredValidationDemo.java`**:

```java
import java.util.Objects;

public class StructuredValidationDemo {

    enum ValidationCode {
        REQUIRED_FIELD,
        INVALID_INTEGER,
        OUT_OF_RANGE
    }

    static final class ValidationException
            extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final ValidationCode code;
        private final String field;

        ValidationException(
                ValidationCode code,
                String field,
                String message) {
            this(code, field, message, null);
        }

        ValidationException(
                ValidationCode code,
                String field,
                String message,
                Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
            this.field = Objects.requireNonNull(field, "field");
        }

        ValidationCode getCode() {
            return code;
        }

        String getField() {
            return field;
        }
    }

    static int parseQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(
                    ValidationCode.REQUIRED_FIELD,
                    "quantity",
                    "Quantity is required");
        }

        final int number;
        try {
            number = Integer.parseInt(raw);
        } catch (NumberFormatException cause) {
            throw new ValidationException(
                    ValidationCode.INVALID_INTEGER,
                    "quantity",
                    "Quantity must be a whole number",
                    cause);
        }

        if (number < 1 || number > 100) {
            throw new ValidationException(
                    ValidationCode.OUT_OF_RANGE,
                    "quantity",
                    "Quantity must be between 1 and 100");
        }

        return number;
    }

    static void runCase(String raw) {
        try {
            System.out.println("OK quantity=" + parseQuantity(raw));
        } catch (ValidationException ex) {
            System.out.println("ERROR code=" + ex.getCode());
            System.out.println("field=" + ex.getField());
            System.out.println("message=" + ex.getMessage());
            System.out.println(
                    "cause="
                    + (ex.getCause() == null
                            ? "none"
                            : ex.getCause().getClass().getSimpleName()));
        }
    }

    public static void main(String[] args) {
        runCase("5");
        runCase(null);
        runCase("abc");
        runCase("0");
        runCase("200");
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 StructuredValidationDemo.java
java StructuredValidationDemo
```

**Stdout chính xác:**

```text
OK quantity=5
ERROR code=REQUIRED_FIELD
field=quantity
message=Quantity is required
cause=none
ERROR code=INVALID_INTEGER
field=quantity
message=Quantity must be a whole number
cause=NumberFormatException
ERROR code=OUT_OF_RANGE
field=quantity
message=Quantity must be between 1 and 100
cause=none
ERROR code=OUT_OF_RANGE
field=quantity
message=Quantity must be between 1 and 100
cause=none
```

### Phân tích chương trình

**`runCase("5")`:** chuỗi có số nguyên `5`, nằm trong khoảng 1–100; `parseQuantity` trả `5`, không có ngoại lệ.

**`runCase(null)`:** điều kiện `raw == null || raw.isBlank()` có *short-circuit*, nên không gọi `isBlank()` khi `raw` null. Custom exception được tạo với `REQUIRED_FIELD` và trường `quantity`, không có cause vì lỗi được xác định trực tiếp.

**`runCase("abc")`:** `Integer.parseInt` ném `NumberFormatException`. Code bắt lỗi ở nơi có thể thêm ngữ cảnh; nó tạo `ValidationException` có mã `INVALID_INTEGER`, giữ `NumberFormatException` làm cause. Handler nhìn thấy mã ổn định mà không phải dựa vào văn bản `For input string ...` vốn phụ thuộc JDK.

**`runCase("0")` và `runCase("200")`:** parse thành số thành công nhưng vượt giới hạn nghiệp vụ, nên mã đều là `OUT_OF_RANGE`. Một mã lỗi có thể đại diện cho cùng một quy tắc ngay cả khi giá trị đầu vào khác nhau.

Chương trình cố ý **không in giá trị thô gây lỗi**. Dữ liệu nhập đôi khi nhạy cảm hoặc rất dài, không nên ghép trực tiếp mọi input vào `message`.

### Thay message có làm thay đổi logic không?

Giả sử sửa:

```java
"Quantity must be a whole number"
```

thành:

```java
"Please enter a valid integer for quantity"
```

Đây là ví dụ sửa **duy nhất một chuỗi trong code**. Đầu ra `message=...` sẽ đổi, nhưng mã `INVALID_INTEGER` và field `quantity` vẫn giữ nguyên. Nếu caller dùng `getCode()`, logic phân nhánh **không đổi**. Đây là tiêu chí quan trọng của thiết kế yêu cầu “giữ thông tin lỗi độc lập với câu chữ của message”.

## 7. Cách caller xử lý bằng mã lỗi thay vì bằng message

Với ngoại lệ `ValidationException` ở ví dụ, một handler có thể dùng:

```java
catch (ValidationException ex) {
    switch (ex.getCode()) {
        case REQUIRED_FIELD ->
                markFieldRequired(ex.getField());
        case INVALID_INTEGER ->
                markFieldInvalid(ex.getField());
        case OUT_OF_RANGE ->
                markFieldOutOfRange(ex.getField());
    }
}
```

Đây là **đoạn minh họa**, giả định các hàm `markField...` tồn tại và cấu trúc nằm sau `try` phù hợp. Caller dựa vào giá trị `enum`. Không cần gọi `getMessage()` để quyết định xử lý.

Lợi ích của `switch` theo enum:

1. Chính sách xử lý tách biệt với cách diễn đạt thông điệp.
2. Dễ test nhánh `REQUIRED_FIELD` mà không khóa bài test vào câu tiếng Anh.
3. Có thể địa phương hóa thông báo.
4. IDE có thể phát hiện nơi cần thay đổi nếu mã lỗi mới được thêm.
5. Phạm vi xử lý rõ hơn một chuỗi `if/else` kiểm tra các từ khóa tùy ý.

**Tuy nhiên, handler vẫn cần hiểu ý nghĩa từng mã.** Nếu một tên mã được đổi hoặc ý nghĩa bị tái sử dụng cho một lỗi khác, “có enum” không tự cứu được tính tương thích. Mã lỗi phải có định nghĩa và quy trình quản lý vòng đời.

## 8. Địa phương hóa và ánh xạ ra API độc lập với message kỹ thuật

Một API có thể muốn trả thông điệp tiếng Việt hoặc tiếng Anh tùy locale, trong khi exception nội bộ giữ thông điệp kỹ thuật để hỗ trợ điều tra. Cách làm hợp lý là **chuyển từ mã lỗi sang message công khai ở ranh giới giao tiếp**.

Ví dụ:

```text
ValidationException {
  code = OUT_OF_RANGE,
  field = "quantity",
  message = "Quantity must be between 1 and 100"
}
                  │
                  ▼
         API error mapping
                  │
           ┌──────┴──────┐
           ▼             ▼
     Locale vi-VN   Locale en-US
           │             │
           ▼             ▼
 "Số lượng phải"   "Quantity must"
 "từ 1 đến 100"    "be from 1 to 100"
```

Thông điệp công khai có thể thay đổi theo locale mà **không sửa mã `OUT_OF_RANGE`** và không cần phân tích chuỗi từ `getMessage()`.

### Ví dụ Java 17+ hoàn chỉnh: cùng mã lỗi, hai message công khai

Lưu tệp **`ErrorCodeMappingDemo.java`**:

```java
public class ErrorCodeMappingDemo {

    enum ErrorCode {
        ORDER_NOT_FOUND("ORD_404"),
        ORDER_ALREADY_CLOSED("ORD_409");

        private final String publicCode;

        ErrorCode(String publicCode) {
            this.publicCode = publicCode;
        }

        String publicCode() {
            return publicCode;
        }
    }

    static final class OrderException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final ErrorCode code;
        private final String internalOrderId;

        OrderException(
                ErrorCode code,
                String internalOrderId,
                String diagnosticMessage) {
            super(diagnosticMessage);
            this.code = java.util.Objects.requireNonNull(
                    code, "code");
            this.internalOrderId =
                    java.util.Objects.requireNonNull(
                            internalOrderId, "internalOrderId");
        }

        ErrorCode getCode() {
            return code;
        }

        String getInternalOrderId() {
            return internalOrderId;
        }
    }

    record ApiError(String code, String message) {}

    static void process(String mode) {
        switch (mode) {
            case "missing" -> throw new OrderException(
                    ErrorCode.ORDER_NOT_FOUND,
                    "internal-17",
                    "Order lookup returned no record");
            case "closed" -> throw new OrderException(
                    ErrorCode.ORDER_ALREADY_CLOSED,
                    "internal-18",
                    "Cannot update a closed order");
            default -> System.out.println("result=OK");
        }
    }

    static ApiError toPublicError(
            OrderException ex, boolean vietnamese) {

        String message = switch (ex.getCode()) {
            case ORDER_NOT_FOUND -> vietnamese
                    ? "Không tìm thấy đơn hàng"
                    : "Order not found";
            case ORDER_ALREADY_CLOSED -> vietnamese
                    ? "Đơn hàng đã đóng"
                    : "Order is already closed";
        };

        return new ApiError(
                ex.getCode().publicCode(),
                message);
    }

    static void runCase(String mode) {
        try {
            process(mode);
        } catch (OrderException ex) {
            ApiError en = toPublicError(ex, false);
            ApiError vi = toPublicError(ex, true);

            System.out.println("code=" + en.code());
            System.out.println("en=" + en.message());
            System.out.println("vi=" + vi.message());
            System.out.println(
                    "diagnostic=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        runCase("ok");
        runCase("missing");
        runCase("closed");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ErrorCodeMappingDemo.java
java ErrorCodeMappingDemo
```

**Stdout chính xác:**

```text
result=OK
code=ORD_404
en=Order not found
vi=Không tìm thấy đơn hàng
diagnostic=Order lookup returned no record
code=ORD_409
en=Order is already closed
vi=Đơn hàng đã đóng
diagnostic=Cannot update a closed order
```

### Tại sao không trả nguyên `ex.getMessage()` cho client?

Trong ví dụ, `OrderException` giữ `internalOrderId` để tầng nội bộ nhận biết tác vụ, nhưng DTO `ApiError` **không chứa ID nội bộ**. Đây là sự phân tách có chủ đích: client chỉ cần mã công khai và thông điệp phù hợp.

Đối với production:

- Không tự động trả message nội bộ ra HTTP response; có thể chứa đường dẫn, SQL, cấu hình hoặc dữ liệu nhạy cảm.
- Ánh xạ mã lỗi sang **HTTP status** bằng quy tắc rõ ràng nếu ứng dụng là API web (ví dụ lỗi không tìm thấy tài nguyên thường có thể ánh xạ sang 404, lỗi trạng thái tùy ngữ nghĩa có thể là 409). Quyết định tùy hợp đồng thực tế.
- Nếu message đa ngôn ngữ dùng resource bundle hoặc hệ thống localization, khóa tra cứu nên đến từ **mã lỗi ổn định**, không phải chính câu tiếng Anh.
- Khi thay đổi câu chữ, test vẫn kiểm tra code và dữ liệu có cấu trúc; test nội dung bản dịch có thể là một nhóm kiểm tra riêng.
- Bản ghi log nên có mã tương quan không nhạy cảm và đối tượng `Throwable`, nhưng không ghi tràn lan ID cá nhân.

**Đoạn `diagnostic=...` trong stdout là cho mục đích minh họa.** Trong ứng dụng thật, thông điệp này thuộc log nội bộ được kiểm soát, không thuộc phản hồi API.

## 9. Vì sao phải giữ `cause` khi chuyển ngoại lệ thư viện sang ngoại lệ tùy chỉnh?

Một lỗi có cấu trúc không thay thế **dấu vết kỹ thuật** của nguyên nhân gốc.

Ví dụ:

```java
static int parseAge(String raw) {
    try {
        return Integer.parseInt(raw);
    } catch (NumberFormatException cause) {
        throw new ValidationException(
                ValidationCode.INVALID_INTEGER,
                "age",
                "Age must be an integer",
                cause);
    }
}
```

Đây là **phương thức minh họa** cần đặt trong phạm vi nhìn thấy `ValidationException` và `ValidationCode` ở ví dụ trước. Caller dựa vào `INVALID_INTEGER` và `age` để quyết định phản hồi, trong khi `getCause()` còn trỏ tới `NumberFormatException` gốc để điều tra.

### Cách làm mất dấu vết

```java
catch (NumberFormatException ex) {
    throw new ValidationException(
            ValidationCode.INVALID_INTEGER,
            "age",
            "Age is invalid");
}
```

Nếu chỉ tạo wrapper không có `cause`, handler ở tầng ngoài khó biết lỗi thật do định dạng, số vượt giới hạn `int` hay một chi tiết parse khác. Có những tình huống cố tình bỏ cause vì chính sách bảo mật hoặc tầng trừu tượng, nhưng đó phải là quyết định có chủ đích, không phải do quên constructor.

**`cause` không phải message.** Việc tạo chuỗi `"invalid: " + ex.getMessage()` không tương đương giữ nguyên đối tượng `ex`. Mất kiểu, stack trace và các suppressed exceptions của nguyên nhân.

### Ném lại cùng đối tượng

Nếu tầng trung gian không cần đổi hợp đồng lỗi, `throw ex;` ném lại cùng ngoại lệ. Điều này giữ nguyên mã lỗi, field, cause và stack trace đang được ghi trên đối tượng. Không cần tạo wrapper mới ở mọi tầng.

### Tránh biến `cause` thành mã lỗi công khai

Caller không nên dò tên lớp `NumberFormatException` trong `getCause()` rồi mặc định coi nó là lỗi field `quantity`; tầng parse mới biết tên field, nên tầng đó nên cung cấp **mã validation và tên trường có cấu trúc**. Cause phục vụ chẩn đoán, không thay cho hợp đồng nghiệp vụ.

## 10. Ngoại lệ tùy chỉnh với nhiều thông số ngữ cảnh

Nếu lỗi có những thuộc tính giúp caller hành động chính xác, bạn có thể thêm field có kiểu:

```java
final class RangeViolationException
        extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final int minimum;
    private final int maximum;

    RangeViolationException(
            String field, int minimum, int maximum) {
        super("Field is outside the allowed range");
        this.field = java.util.Objects.requireNonNull(
                field, "field");
        this.minimum = minimum;
        this.maximum = maximum;
    }

    String getField() {
        return field;
    }

    int getMinimum() {
        return minimum;
    }

    int getMaximum() {
        return maximum;
    }
}
```

Đây là **một lớp minh họa** có thể đặt trong cùng package thích hợp. Handler có thể tạo thông báo theo ngôn ngữ và hệ thống UI dựa trên `field`, `minimum` và `maximum`, không cần parse ngược các con số từ message.

### Dữ liệu nào không nên cho vào field của exception?

Không nên tự động lưu:

- Mật khẩu, token, cookie, khóa API.
- Toàn bộ request body hoặc dữ liệu nhạy cảm của khách hàng.
- Payload cực lớn hoặc object mutable có vòng đời phức tạp.
- Các tham chiếu tới kết nối/stream chỉ để “đọc lại ngữ cảnh” sau khi chúng đã đóng.
- Đối tượng nội bộ có ràng buộc đa luồng mà caller không thể quản lý.

Các field nằm trong đối tượng ngoại lệ có thể bị log, serialize, quan sát bởi debugger hoặc telemetry. Chỉ giữ ngữ cảnh **cần thiết, nhỏ gọn và theo chính sách bảo mật**.

### Đừng quên bất biến thực sự

`private final List<String> details` chỉ làm **tham chiếu danh sách** không đổi; nếu danh sách mutable thì nội dung vẫn có thể đổi. Nếu custom exception cần giữ collection ổn định, cân nhắc snapshot:

```java
this.details = java.util.List.copyOf(details);
```

Đây là đoạn minh họa, áp dụng nếu `details` là một danh sách không null có phần tử phù hợp. Tùy yêu cầu, cần defensive copy cho các đối tượng mutable sâu hơn nữa.

## 11. Cấu trúc DTO cho phản hồi lỗi và hợp đồng công khai

Một **đối tượng ngoại lệ** và một **phản hồi API** phục vụ hai mục tiêu khác nhau:

| Ngoại lệ nội bộ | DTO phản hồi công khai |
| --- | --- |
| Mang kiểu Java/stack trace/cause | Mang schema ổn định cho client |
| Có message chẩn đoán nội bộ | Có message được phép công khai và bản địa hóa |
| Có thể giữ ngữ cảnh kỹ thuật | Chỉ chứa trường được quy định trong hợp đồng |
| Caller Java có thể bắt theo subtype | Client nhận mã string/status/trường đã định nghĩa |
| Có thể thay đổi khi refactor nội bộ | Cần quản lý tương thích phiên bản |

Ví dụ schema minh họa:

```json
{
  "code": "VAL_003",
  "field": "quantity",
  "message": "Số lượng phải nằm trong khoảng cho phép"
}
```

Mã lỗi là trường được cam kết theo phiên bản API; message là chuỗi hiển thị có thể thay đổi. Trong hệ thống thật cần quy định rõ những field nào ổn định, trạng thái HTTP, cách sử dụng request/correlation ID và điều kiện lộ thông tin.

### Quy tắc về mã ổn định

Nếu một hệ thống đã phát hành mã `ORD_404` cho lỗi “không tìm thấy đơn hàng”, hãy tránh dùng lại mã đó cho lỗi “đơn hàng đóng” ở phiên bản sau. Việc tái sử dụng ý nghĩa khiến client dùng mã cũ hiểu sai.

Một bộ mã ổn định thường có:

- Tên hoặc giá trị công khai **duy nhất**.
- Định nghĩa chính xác về điều kiện lỗi.
- Phạm vi áp dụng ở module/domain.
- Hướng dẫn client có thể xử lý thế nào.
- Chính sách thêm mã mới, thay thế và ngừng hỗ trợ mã cũ.
- Kiểm thử bảo đảm không đổi ý nghĩa khi sửa message.

Không nhất thiết mọi ứng dụng nhỏ đều cần registry phức tạp, nhưng ngay từ đầu nên xem mã lỗi là **dữ liệu có hợp đồng**, khác với câu chữ tự do.

## 12. Dùng `switch` theo enum: lợi ích và giới hạn

Java 17 hỗ trợ `switch expression`, thuận tiện để ánh xạ mã lỗi sang giá trị:

```java
String publicMessage = switch (code) {
    case REQUIRED_FIELD -> "Required field";
    case INVALID_INTEGER -> "Invalid integer";
    case OUT_OF_RANGE -> "Outside allowed range";
};
```

Đây là đoạn minh họa với `code` có kiểu `ValidationCode` từ ví dụ trước. Không có `default` nên nếu thêm enum constant mà chưa xử lý, trình biên dịch có thể phát hiện switch expression chưa bao phủ đủ các giá trị.

Đây là lợi ích quan trọng của **mã có kiểu** so với `if (message.contains(...))`. Tuy nhiên:

- Caller phải quản lý trường hợp mở rộng enum theo kế hoạch tương thích.
- Nếu mã là string nhận từ hệ thống ngoài, cần kiểm tra input không xác định thay vì gọi `Enum.valueOf` vô điều kiện.
- Enum là lựa chọn tốt khi tập mã được biết khi biên dịch; hệ thống plugin/extensible có thể cần registry hoặc value object thay vì một enum duy nhất.
- Không nên tạo enum tên giống tiếng Anh message rồi coi nó là sự phân tách; enum cần biểu diễn **ý nghĩa ổn định**, không chỉ gắn số thứ tự cho câu chữ.

### Nếu cần xác định lỗi có thể thử lại?

Một số hệ thống thêm thuộc tính `retryable`, nhưng cần thận trọng. “Có thể retry” thường phụ thuộc cả **tình huống xử lý, số lần thử, idempotency, trạng thái giao dịch** và chính sách tầng gọi, không chỉ là tên exception. Không nên mặc định mọi lỗi `IOException` đều retry hoặc mọi lỗi `ValidationException` đều không bao giờ xử lý tiếp; quy tắc phụ thuộc hợp đồng.

Dữ liệu có cấu trúc giúp quyết định chính xác hơn, nhưng **không thay thế việc hiểu ngữ nghĩa tác vụ**.

## 13. Kiểm thử đúng hợp đồng thay vì kiểm tra toàn bộ message

Một bài test dễ vỡ:

```java
// Minh họa: kiểm thử phụ thuộc câu chữ.
assertEquals(
        "Quantity must be a whole number",
        exception.getMessage());
```

Mỗi lần sửa câu chữ hợp lý có thể khiến test thất bại dù hành vi không thay đổi.

Với exception có code và field, bài test nên ưu tiên:

```java
// Minh họa: cần framework assert và biến exception thích hợp.
assertEquals(
        ValidationCode.INVALID_INTEGER,
        exception.getCode());
assertEquals("quantity", exception.getField());
assertInstanceOf(
        NumberFormatException.class,
        exception.getCause());
```

Đây là **các câu lệnh minh họa dạng JUnit**, không phải chương trình độc lập; `assertInstanceOf` là một API trong các phiên bản JUnit phù hợp. Test giúp xác nhận:

- **Phân loại ổn định:** mã `INVALID_INTEGER`.
- **Ngữ cảnh:** trường `quantity`.
- **Nguyên nhân:** `NumberFormatException` được giữ lại.

Có thể kiểm tra message riêng khi message thật sự là phần của hợp đồng công khai hoặc khi kiểm thử tài nguyên bản địa hóa. Khi ấy cần xác định trước rằng thay đổi câu chữ là thay đổi có chủ đích, không coi nó là giao thức xử lý lỗi.

### Kiểm thử không phụ thuộc framework

Ngay cả khi không dùng JUnit, có thể viết các phép kiểm tra thuần Java:

```java
if (ex.getCode() != ValidationCode.INVALID_INTEGER) {
    throw new AssertionError("Unexpected validation code");
}
if (!"quantity".equals(ex.getField())) {
    throw new AssertionError("Unexpected field");
}
if (!(ex.getCause() instanceof NumberFormatException)) {
    throw new AssertionError("Original cause not preserved");
}
```

Đây là đoạn minh họa đặt bên trong handler có `ex` đúng kiểu. Các kiểm tra dựa trên **dữ liệu**, không dựa vào câu chữ của `getMessage()`.

## 14. Tương thích phiên bản khi mã lỗi trở thành hợp đồng

Trong một ứng dụng chỉ có một module, enum có thể thay đổi đồng bộ với caller. Nhưng với thư viện Java public, API ngoài hoặc hệ thống microservices, mã lỗi có thể được nhiều client sử dụng.

Những thay đổi cần cân nhắc:

| Thay đổi | Mức ảnh hưởng điển hình |
| --- | --- |
| Sửa dấu câu, cách diễn đạt message nội bộ | Không nên ảnh hưởng logic dựa trên code |
| Dịch message công khai | Không nên đổi mã lỗi hay field có cấu trúc |
| Thêm enum constant | Caller có `switch` không đầy đủ có thể cần cập nhật |
| Đổi tên enum constant được client Java sử dụng | Có thể phá vỡ tương thích nguồn/binary |
| Đổi giá trị `publicCode` đã phát hành | Có thể phá vỡ client hoặc dữ liệu lưu trữ |
| Dùng lại code cũ cho lỗi mới | Dễ gây hiểu sai âm thầm, nên tránh |
| Đổi subtype exception checked/unchecked | Thay đổi hợp đồng xử lý của caller |
| Xóa field/context đang được client dùng | Có thể phá vỡ API và test |
| Ngừng truyền `cause` từng có trước đó | Làm giảm khả năng chẩn đoán |

Một mã có cấu trúc chỉ hữu ích lâu dài nếu **được định nghĩa và quản trị nhất quán**. Đừng giả định việc dùng `enum` tự động làm mọi thay đổi tương thích.

## 15. Những sai lầm thường gặp

| Sai lầm | Tại sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| `getMessage().equals("...")` để phân nhánh | Đổi chữ hoặc null làm hỏng logic | Dùng mã lỗi/thuộc tính có kiểu |
| `getMessage().contains("...")` | Trùng từ, phụ thuộc thư viện/ngôn ngữ | Phân loại theo hợp đồng |
| Gộp mọi lỗi trong `RuntimeException("failed")` | Không phân biệt nhóm lỗi và nguyên nhân | Chọn subtype/code phù hợp |
| Tạo một class cho mỗi biến thể message | Dễ bùng nổ kiểu mà logic giống nhau | Dùng một loại exception có enum khi phù hợp |
| Chỉ lưu code trong message, không có getter | Caller vẫn phải parse chuỗi | Lưu field `private final` và getter |
| Đổi message thành tiếng Việt rồi làm thay đổi logic | Phá vỡ phân tách trình bày/xử lý | Locale mapping dựa trên code |
| Bọc ngoại lệ nhưng bỏ cause | Mất stack trace, loại lỗi gốc, suppressed | Constructor nhận `Throwable cause` |
| Đưa secret hoặc payload thô vào field/message | Dễ rò rỉ log/telemetry | Chỉ lưu ngữ cảnh an toàn |
| Dùng `enum.name()` làm mã public rồi đổi tên hằng | Gãy hợp đồng public code | Dùng giá trị mã riêng ổn định nếu cần |
| Đặt toàn bộ dữ liệu business vào một string `detail` | Trở lại phụ thuộc câu chữ | Tạo field có kiểu cho dữ liệu cần thiết |
| Nghĩ `private final List` là immutable | Phần tử/danh sách vẫn có thể mutable | Defensive copy đúng mức |
| Trả toàn bộ `Throwable` từ API | Lộ implementation và dữ liệu nội bộ | Chuyển thành DTO công khai |
| Gắn thông điệp người dùng vào ngoại lệ tầng thấp | Trộn i18n vào thư viện/logic | Translate tại boundary giao tiếp |
| Test toàn bộ message dù chưa phải hợp đồng | Test giòn, cản sửa câu chữ | Test code, field, cause và mapping riêng |

## 16. Bài tập luyện tập

**Bài A — phát hiện hợp đồng mong manh**

Một controller có:

```java
if (ex.getMessage().startsWith("quantity")) {
    return "INVALID_QUANTITY";
}
```

Điều gì xảy ra nếu thư viện thay message thành `"Invalid value for field quantity"`?

**Đáp án:** nhánh xử lý có thể sai dù bản chất lỗi không đổi. Cần dùng `ex.getCode()`/`ex.getField()` hoặc một loại ngoại lệ chuyên biệt, không dựa vào prefix văn bản.

**Bài B — cause có phải mã nghiệp vụ không?**

Tầng parse bắt `NumberFormatException` rồi tạo `ValidationException(ValidationCode.INVALID_INTEGER, "quantity", ..., cause)`. Caller có nên nhìn `cause.getMessage()` để quyết định field bị lỗi?

**Không.** Caller dùng field và code có cấu trúc. Cause phục vụ chẩn đoán nguồn thất bại, không cần chứa tên field nghiệp vụ.

**Bài C — sửa message có phá hợp đồng không?**

Thay `"Quantity must be between 1 and 100"` bằng `"Enter a quantity from 1 to 100"`. Nếu code vẫn là `OUT_OF_RANGE`, field là `quantity` và các điều kiện validation không đổi thì các client dựa trên code có bị đổi nhánh không?

**Không.** Đây là mục tiêu của việc tách dữ liệu lỗi khỏi câu chữ.

**Bài D — enum hay string?**

Ứng dụng có năm lỗi validation đã biết lúc biên dịch. Nên bắt đầu từ `enum` hay các string tự do rải khắp mã nguồn?

**`enum`** thường giúp kiểm tra kiểu và refactor dễ hơn. Nếu phải phát hành mã qua network, thêm giá trị public ổn định và chính sách versioning.

**Bài E — dữ liệu nhạy cảm**

Có nên lưu raw API token trong field `private final String token` của `AuthenticationException` để tiện log không?

**Không mặc định.** Field trong exception có thể lộ qua log/debugger/telemetry. Chỉ lưu mã lỗi và ngữ cảnh được cho phép; không tạo thông điệp chứa secret.

**Bài F — test theo message**

Test đang so sánh toàn bộ message ở `ValidationException`. Nếu QA muốn cải thiện thông điệp tiếng Việt, có phải sửa tất cả test nghiệp vụ không?

**Không nên.** Tách test phân loại (code/field/cause) khỏi test bản dịch/câu chữ. Chỉ test toàn bộ message tại nơi message được định nghĩa là hợp đồng.

**Bài G — một lỗi, hai ngôn ngữ**

`OrderException` mang `ORDER_NOT_FOUND`. API muốn tiếng Việt hoặc tiếng Anh. Có nên sửa `ex.getMessage()` của từng exception cho phù hợp locale?

**Không cần.** Tầng API có thể ánh xạ `ORDER_NOT_FOUND` thành thông báo phù hợp ngôn ngữ; mã lỗi giữ nguyên.

**Bài H — final field có đủ không?**

Custom exception có `private final Map<String, Object> metadata` và getter trả trực tiếp map. Có thể khẳng định ngữ cảnh không bị đổi không?

**Không.** `final` chỉ khóa tham chiếu; map có thể bị sửa qua getter hoặc tham chiếu khác. Dùng defensive copy và kiểu dữ liệu có cấu trúc phù hợp, cẩn thận với object mutable bên trong.

**Bài I — đổi ý nghĩa mã lỗi**

Hôm nay `ORD_404` nghĩa là “không tìm thấy đơn hàng”; ngày mai hệ thống dùng lại `ORD_404` nghĩa là “không thể thanh toán”. Có tốt không?

**Không.** Client đang xử lý mã cũ có thể hiểu sai âm thầm. Cần mã mới cho ý nghĩa mới và chính sách tương thích.

## 17. Checklist thiết kế custom exception ổn định

Khi tạo hoặc xem xét một lớp ngoại lệ tùy chỉnh, hãy tự kiểm tra:

1. **Loại lỗi có cần một subtype riêng không?** Nếu caller không xử lý khác, có thể dùng subtype chuẩn hoặc một custom exception theo nhóm domain.
2. **Checked hay unchecked?** Chọn theo hợp đồng phục hồi thực sự của caller, không chọn ngẫu nhiên.
3. **Có mã lỗi ổn định không?** Dùng `enum`, code string có hằng hoặc một value object thay vì ghép từ message.
4. **Những thuộc tính nào caller thực sự cần?** Ví dụ `field`, `operation`, `resourceType`, giá trị giới hạn; tránh phình to và tránh bí mật.
5. **Các field quan trọng có `private final` và getter không?** Có cần defensive copy đối với dữ liệu mutable không?
6. **Constructor có giữ cause không?** Khi wrapper được tạo từ lỗi thư viện, bảo toàn đối tượng gốc khi phù hợp.
7. **Message có chỉ là phần giải thích không?** Thử đổi câu chữ xem nhánh code có bị thay đổi không.
8. **API công khai có dùng mã riêng không?** Nếu có, mã phải ổn định qua phiên bản và khác thông điệp địa phương hóa.
9. **Có mapping rõ ở ranh giới giao tiếp không?** Không tự trả `Throwable` hoặc stack trace nguyên văn cho client.
10. **Có chính sách đổi mã/thêm mã mới không?** Định nghĩa ý nghĩa và tránh tái sử dụng mã cũ.
11. **Kiểm thử tập trung vào code, field, cause chưa?** Đừng khóa các bài test nghiệp vụ vào dấu câu của message.
12. **Log có đủ dấu vết nhưng không lộ dữ liệu không?** Thông tin lỗi có cấu trúc và cause phải đi kèm kiểm soát truy cập.
13. **Các hành vi xử lý có dựa trên ngữ nghĩa, không dựa trên message không?** Chẳng hạn logic retry, HTTP mapping và i18n phải theo hợp đồng.
14. **Đã kiểm tra các trường hợp lỗi trực tiếp và lỗi wrapper chưa?** Lỗi trực tiếp có thể không có cause; lỗi wrapper có thể mang cause/suppressed.

## Kết luận

**`message` dành cho người đọc; mã lỗi và dữ liệu có kiểu dành cho chương trình.** Khi thiết kế ngoại lệ tùy chỉnh Java, hãy chọn subtype phản ánh nhóm thất bại, lưu mã lỗi ổn định cùng thuộc tính cần thiết, giữ `cause` khi chuyển lỗi, và xây dựng thông điệp tự nhiên, dễ hiểu nhưng **không biến câu chữ thành điều kiện điều khiển**.

Cách tách này giúp hệ thống sửa thông điệp, địa phương hóa, chuyển từ console sang API, refactor nội bộ và nâng cấp thư viện mà **không vô tình đổi cách caller nhận diện và xử lý lỗi**. Mã lỗi vẫn cần hợp đồng tương thích, còn dữ liệu được lưu trong exception phải nhỏ gọn, có ý nghĩa và an toàn khi được ghi log.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-008">Giới hạn generic: lớp con Throwable không được có tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn generic: lớp con `Throwable` không được có tham số kiểu trong Java

Java cho phép lập trình generic để viết mã dùng được với nhiều kiểu dữ liệu, nhưng đặt ra một giới hạn đặc biệt với hệ phân cấp ngoại lệ: **một lớp generic không được là lớp con trực tiếp hoặc gián tiếp của `java.lang.Throwable`**. Vì vậy, các khai báo như `class MyException<T> extends Exception`, `class MyRuntimeException<T> extends RuntimeException` và `class MyError<T> extends Error` đều **không biên dịch**.

Đây là một quy tắc của **Java Language Specification (JLS) §8.1.2**, không phải lỗi thiếu import hay vấn đề riêng của một phiên bản `javac`. Cơ chế bắt ngoại lệ trong Java/JVM dựa trên **lớp ngoại lệ lúc chạy**, không dựa vào tham số generic `<T>` vốn bị xóa trong quá trình biên dịch (*type erasure*).

Quan trọng không kém: **điều bị cấm là lớp ngoại lệ có tham số kiểu**, chứ không phải mọi cách sử dụng generics liên quan đến ngoại lệ. Một lớp ngoại lệ **không generic** vẫn có thể chứa trường `List<String>`; phương thức generic có thể khai báo `throws E` với `E extends Throwable`; lớp `Result<T, E>` có thể mang theo một ngoại lệ. Nhưng tham số kiểu `E` không được đặt trực tiếp làm kiểu trong `catch (E ex)`.

Bài này trình bày nguyên nhân, cú pháp đúng/sai, các ngoại lệ thiết kế thường gặp, cách giữ metadata có kiểu, generic `throws`, lỗi khi sử dụng `catch` và các phương án thay thế có thể áp dụng trong Java 17+.

## 1. Quy tắc chính xác trong JLS §8.1.2

JLS quy định rằng một **generic class** không được là **direct or indirect subclass** của `Throwable`. Một lớp là generic khi **bản thân khai báo lớp** có phần khai báo tham số kiểu, ví dụ `class Box<T>` hoặc `class Pair<K, V>`.

Hai hình thức sau **đều bị cấm**:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
class TypedException<T> extends Exception {
    TypedException(String message) {
        super(message);
    }
}

class TypedRuntimeFailure<T> extends RuntimeException {
    TypedRuntimeFailure(String message) {
        super(message);
    }
}
```

Ở cả hai lớp, bản thân khai báo lớp có `<T>` và lớp cha nằm trên nhánh `Throwable`. `javac` thường báo lỗi có ý nghĩa như:

```text
a generic class may not extend java.lang.Throwable
```

Thông báo có thể thay đổi theo JDK, nhưng **nguyên nhân compile-time không thay đổi**.

Giới hạn không chỉ áp dụng với `Exception`:

| Khai báo | Kết quả | Lý do |
| --- | --- | --- |
| `class GenericFailure<T> extends Throwable` | Không hợp lệ | Generic class trực tiếp kế thừa `Throwable` |
| `class GenericFailure<T> extends Exception` | Không hợp lệ | Generic class gián tiếp kế thừa `Throwable` |
| `class GenericFailure<T> extends RuntimeException` | Không hợp lệ | Runtime exception vẫn là `Throwable` |
| `class GenericFailure<T> extends Error` | Không hợp lệ | `Error` là nhánh con của `Throwable` |
| `class InputFailure extends Exception` | Hợp lệ | Lớp ngoại lệ **không có tham số kiểu** |
| `class Box<T>` | Hợp lệ | Đây không phải lớp con của `Throwable` |
| `class Outcome<T, E extends Exception>` | Hợp lệ khi không kế thừa `Throwable` | Lớp generic chứa/chuyển một ngoại lệ, không phải chính ngoại lệ |

**Lưu ý:** một lớp ngoại lệ có thể dùng nhiều kiểu generic trong thân mà vẫn **không phải lớp generic**. Tiêu chí là khai báo lớp đó có danh sách tham số kiểu `<...>` hay không và có kế thừa `Throwable` hay không.

## 2. “Gián tiếp” có nghĩa là gì?

Giả sử đã có một lớp ngoại lệ không generic:

```java
class ApplicationFailure extends RuntimeException {
    ApplicationFailure(String message) {
        super(message);
    }
}
```

Lớp này hợp lệ. Nhưng cách kế thừa sau **không hợp lệ**:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
class GenericServiceFailure<T> extends ApplicationFailure {
    GenericServiceFailure(String message) {
        super(message);
    }
}
```

Chuỗi kế thừa thực chất là:

```text
GenericServiceFailure<T>
        |
        extends
        v
ApplicationFailure
        |
        extends
        v
RuntimeException
        |
        extends
        v
Exception
        |
        extends
        v
Throwable
```

Mặc dù `GenericServiceFailure<T>` **không viết trực tiếp** `extends Throwable`, nó vẫn là một lớp con gián tiếp. Do đó giới hạn vẫn áp dụng.

Không thể “né” quy tắc bằng cách đặt thêm lớp ngoại lệ trung gian không generic. Điều kiện là **toàn bộ quan hệ kế thừa của chính lớp generic**, không chỉ tên lớp cha xuất hiện ngay sau `extends`.

## 3. Vì sao Java cấm generic exception class?

### 3.1. Generics bị xóa tham số kiểu ở runtime

Java chủ yếu hiện thực generics theo cơ chế **type erasure**. Ví dụ:

```java
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List<Integer> numbers = new java.util.ArrayList<>();

System.out.println(names.getClass() == numbers.getClass());
```

Đây là đoạn mã chạy được khi đặt trong phương thức. Kết quả là `true` vì cả hai đối tượng đều có lớp runtime `ArrayList`; tham số kiểu `String` và `Integer` không tạo ra hai lớp Java runtime riêng biệt.

Đối với generic exception *giả định*:

```java
// CHỈ LÀ GIẢ THUYẾT; GenericFailure<T> bị JLS cấm khai báo.
GenericFailure<String> a;
GenericFailure<Integer> b;
```

Nếu Java cho phép loại lớp này, hai tham số hóa sẽ không trở thành hai lớp ngoại lệ runtime riêng. Cơ chế xử lý ngoại lệ không thể dựa vào tham số generic để phân biệt chúng.

### 3.2. `catch` lựa chọn kiểu ngoại lệ theo lớp runtime

Java chọn nhánh `catch` đầu tiên tương thích với **kiểu runtime của đối tượng ném ra**. JVM sử dụng thông tin lớp trong bảng exception handler của mã bytecode; nó không có hai handler độc lập chỉ dựa vào `T = String` hay `T = Integer` của cùng một lớp generic bị erasure.

Hãy tưởng tượng mã sau nếu một lớp generic exception được cho phép:

```java
// GIẢ ĐỊNH SAI — KHÔNG THỂ BIÊN DỊCH TRONG JAVA:
try {
    throw new GenericFailure<String>();
} catch (GenericFailure<String> ex) {
    System.out.println("String");
} catch (GenericFailure<Integer> ex) {
    System.out.println("Integer");
}
```

Ví dụ trên chỉ dùng để **giải thích tính bất khả thi của thiết kế**, không phải cú pháp cần sửa vài ký tự là chạy được. Hai dạng tham số hóa sẽ có cùng lớp runtime; không thể chọn handler theo `String` hoặc `Integer` như những kiểu ngoại lệ hoàn toàn khác nhau.

JLS §8.1.2 nêu rõ nguyên nhân của giới hạn là **cơ chế `catch` của JVM chỉ hoạt động với các lớp không generic**. Đây là ràng buộc chính thức của ngôn ngữ, không đơn thuần là một lời khuyên về phong cách lập trình.

## 4. Phân biệt ba điều dễ bị đánh đồng

| Tình huống | Hợp lệ? | Giải thích |
| --- | --- | --- |
| `class Failure<T> extends Exception` | **Không** | Chính lớp exception là generic |
| `class Failure extends Exception { List<String> details; }` | **Có** | Lớp exception không generic, trường sử dụng một kiểu generic cụ thể |
| `class Holder<T> { T data; Exception failure; }` | **Có** | Lớp generic không kế thừa Throwable |
| `class Result<T, E extends Exception>` không extends Throwable | **Có** | Tham số E biểu diễn loại lỗi đi kèm, không phải loại lớp Result |
| `static <E extends Exception> void relay(E ex) throws E` | **Có** | Method generic dùng type variable trong mệnh đề throws |
| `catch (E ex)` với `E extends Exception` | **Không** | Type variable không được làm kiểu tham số catch |
| `catch (IOException | SQLException ex)` | **Có** nếu try có thể ném các loại checked này | Multi-catch liệt kê **các lớp cụ thể**, không phải type variables |
| `class Failure extends RuntimeException { <T> T detailAs(Class<T> type) ... }` | **Có** | Phương thức generic trong lớp exception không generic |

**Hai từ “generic” và “exception” xuất hiện trong cùng file không gây lỗi.** Chỉ khi khai báo vi phạm một quy tắc cụ thể của ngôn ngữ, chẳng hạn **generic subclass of Throwable** hoặc **type variable in catch**, trình biên dịch mới từ chối.

## 5. Thiết kế đúng: custom exception không generic nhưng có metadata có kiểu

Thông thường, thông tin nghiệp vụ cần mang theo ngoại lệ là một **giá trị cụ thể**: tên trường bị lỗi, danh sách vi phạm, mã lỗi hoặc ID tác vụ. Không cần biến bản thân *lớp ngoại lệ* thành generic.

Ví dụ:

```java
import java.util.List;

class ValidationFailure extends Exception {
    private final List<String> violations;

    ValidationFailure(String message, List<String> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
```

Đây là **lớp hợp lệ**, không chứa `main()` độc lập. Lớp `ValidationFailure` không có tham số kiểu, nhưng trường `List<String>` vẫn được kiểm tra kiểu lúc biên dịch. `List.copyOf(...)` tạo bản sao không thể sửa qua các thao tác thông thường của `List`, giảm nguy cơ mã ngoài thay đổi danh sách đã gắn với ngoại lệ.

### Chương trình Java 17+ hoàn chỉnh

Lưu thành `ValidationFailureDemo.java`:

```java
import java.util.List;

public class ValidationFailureDemo {
    static final class ValidationFailure extends Exception {
        private final List<String> violations;

        ValidationFailure(String message, List<String> violations) {
            super(message);
            this.violations = List.copyOf(violations);
        }

        List<String> getViolations() {
            return violations;
        }
    }

    static <T> T requirePresent(T value, String field)
            throws ValidationFailure {
        if (value == null) {
            throw new ValidationFailure(
                    "Invalid field: " + field,
                    List.of(field + " is required"));
        }
        return value;
    }

    public static void main(String[] args) {
        try {
            System.out.println("valid="
                    + requirePresent("C-42", "customerId"));
            requirePresent(null, "customerId");
        } catch (ValidationFailure ex) {
            System.out.println("type="
                    + ex.getClass().getSimpleName());
            System.out.println("message=" + ex.getMessage());
            System.out.println("violations=" + ex.getViolations());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ValidationFailureDemo.java
java ValidationFailureDemo
```

Kết quả:

```text
valid=C-42
type=ValidationFailure
message=Invalid field: customerId
violations=[customerId is required]
```

Phân tích:

1. `ValidationFailure` là **custom checked exception không generic**.
2. Trường `List<String>` chứa thông tin đã biết cấu trúc; không biến ngoại lệ thành `ValidationFailure<String>`.
3. Phương thức `requirePresent` **là generic** vì khai báo `<T>` và trả cùng loại T của tham số. Generic method có thể ném một loại ngoại lệ **cụ thể** `ValidationFailure`.
4. `main()` bắt loại ngoại lệ cụ thể này; đây là kiểu `catch` hợp lệ.
5. Lỗi có thông điệp và metadata riêng mà không vi phạm JLS §8.1.2.

Nếu metadata nhạy cảm, cân nhắc loại nào được phép đi vào exception/log. Một field có kiểu không có nghĩa được phép chứa mật khẩu, token hoặc thông tin cá nhân cần bảo vệ.

## 6. Phương thức generic có thể khai báo `throws E`

Mệnh đề `throws` được khai báo trên **phương thức hoặc constructor**, không phải trên bản thân lớp ngoại lệ. JLS §8.4.6 cho phép một **type variable** làm kiểu trong danh sách `throws`, với điều kiện kiểu đó là subtype của `Throwable`.

Ví dụ:

```java
static <E extends Exception> void rethrow(E failure) throws E {
    throw failure;
}
```

Đây là **phương thức minh họa**, có thể đặt trong một lớp thông thường. Nó được phép dùng `E` trong chữ ký `throws E` và trong biểu thức `throw failure;`. Tại điểm gọi, `E` được suy ra từ kiểu của đối tượng ngoại lệ.

### Chương trình Java 17+ kiểm chứng generic `throws`

Lưu thành `GenericThrowsDemo.java`:

```java
import java.io.IOException;
import java.text.ParseException;

public class GenericThrowsDemo {
    static <E extends Exception> void rethrow(E failure) throws E {
        throw failure;
    }

    static <E extends Exception> void check(
            boolean fail, E failure) throws E {
        if (fail) {
            rethrow(failure);
        }
    }

    public static void main(String[] args) {
        try {
            check(true, new IOException("disk failure"));
        } catch (IOException ex) {
            System.out.println("io=" + ex.getMessage());
        }

        try {
            check(true, new ParseException("bad token", 0));
        } catch (ParseException ex) {
            System.out.println("parse=" + ex.getMessage());
        }

        try {
            check(false, new IOException("not thrown"));
            System.out.println("success");
        } catch (IOException ex) {
            System.out.println("unreachable on this input");
        }
    }
}
```

```bash
javac -encoding UTF-8 GenericThrowsDemo.java
java GenericThrowsDemo
```

Kết quả:

```text
io=disk failure
parse=bad token
success
```

Ở đây:

- Lớp `GenericThrowsDemo` **không generic**.
- Hai phương thức có type variable `E extends Exception` nên là **generic methods**.
- `throws E` được JLS cho phép; ở các lời gọi minh họa, compiler suy ra `E` tương ứng với `IOException` hoặc `ParseException`.
- Các `catch` vẫn dùng **lớp ngoại lệ cụ thể**.
- `check(false, ...)` không ném trong lần chạy này, nhưng chữ ký vẫn mô tả khả năng ném E; đoạn `catch(IOException)` vẫn hợp lệ.

Điểm khác biệt trọng tâm: **generic method có thể truyền loại ngoại lệ qua type parameter**, trong khi **generic exception class** vẫn không được khai báo.

## 7. Tại sao `catch (E ex)` lại không hợp lệ?

JLS §14.20 đặt ra quy tắc riêng: **không được dùng type variable làm kiểu của một exception parameter** trong `catch`.

Ví dụ sau **cố ý không biên dịch**:

```java
class InvalidGenericCatch {
    static <E extends Exception> void handle(E sample) throws E {
        try {
            throw sample;
        } catch (E ex) { // Lỗi biên dịch: type variable trong catch.
            System.out.println(ex.getMessage());
        }
    }
}
```

Không phải vì `E extends Exception` là sai; chính **vị trí của E trong `catch`** bị JLS cấm. Việc dùng `throws E` ở chữ ký phương thức là hợp lệ.

Một cách xử lý hợp lệ khi thật sự muốn dùng mã chung là bắt **kiểu lớp cụ thể được thiết kế cho giao diện xử lý**:

```java
static <E extends Exception> void handle(E sample) {
    try {
        throw sample;
    } catch (Exception ex) {
        System.out.println(ex.getMessage());
    }
}
```

Đây là phương thức minh họa hợp lệ; `catch (Exception ex)` là một kiểu lớp cụ thể, không phải type variable. Dù vậy, việc bắt rộng `Exception` có thể nuốt nhiều lỗi hơn ý định. Chỉ sử dụng khi phương thức thật sự có chính sách xử lý chung; nếu cần chuyển nguyên loại `E` cho bên gọi, **cho ngoại lệ lan truyền bằng `throws E`** thường chính xác hơn.

Lưu ý rằng một nhánh `catch` kiểm tra **kiểu runtime của ngoại lệ**. Kiểu tham số là type variable không tạo ra class token đủ để JVM phân biệt những tham số generic khác nhau.

## 8. Thay thế bằng generic class *chứa* lỗi, không *là* lỗi

Một giải pháp thiết kế phổ biến là **giữ ngoại lệ thành lớp thường** và đưa generics vào một lớp mô tả kết quả, trạng thái hoặc cơ chế xử lý.

Ví dụ cấu trúc:

```java
final class Outcome<T, E extends Exception> {
    private final T value;
    private final E failure;

    Outcome(T value, E failure) {
        this.value = value;
        this.failure = failure;
    }

    T valueOrThrow() throws E {
        if (failure != null) {
            throw failure;
        }
        return value;
    }
}
```

Đây là **một lớp generic hợp lệ** vì `Outcome` không kế thừa `Throwable`. Hai type parameter mang ý nghĩa khác nhau:

- `T`: giá trị thành công;
- `E extends Exception`: loại ngoại lệ được lưu nếu thất bại.

### Chương trình Java 17+ chạy được

Lưu thành `GenericOutcomeDemo.java`:

```java
import java.io.IOException;

public class GenericOutcomeDemo {
    static final class Outcome<T, E extends Exception> {
        private final T value;
        private final E failure;

        Outcome(T value, E failure) {
            this.value = value;
            this.failure = failure;
        }

        T valueOrThrow() throws E {
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }

    public static void main(String[] args) {
        Outcome<String, IOException> good =
                new Outcome<>("READY", null);
        Outcome<String, IOException> bad =
                new Outcome<>(null, new IOException("file unavailable"));

        try {
            System.out.println("good=" + good.valueOrThrow());
            System.out.println("bad=" + bad.valueOrThrow());
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 GenericOutcomeDemo.java
java GenericOutcomeDemo
```

Kết quả:

```text
good=READY
caught=file unavailable
```

Với `good`, trường `failure` bằng `null` nên trả về chuỗi `READY`. Với `bad`, phương thức `valueOrThrow()` ném **chính đối tượng `IOException` đã lưu**; lệnh `println("bad=" + ...)` không được thực thi. `catch (IOException)` bắt lỗi cụ thể ở tầng gọi.

**Lưu ý về thiết kế:** lớp `Outcome` trên minh họa cơ chế generic và ngoại lệ. Một Result/Outcome production-grade nên có bất biến rõ ràng như “chỉ một trong value hoặc failure được thiết lập”, factory methods, null policy và mô hình trạng thái nhất quán. Không nên sử dụng thiết kế demo để biểu diễn mọi trường hợp dữ liệu mà không bổ sung các kiểm tra đó.

## 9. Generic methods trong một lớp ngoại lệ không generic

Một custom exception vẫn có thể sở hữu **generic method**, ví dụ để đọc metadata đã lưu qua một runtime class token.

Lưu thành `TypedDetailDemo.java`:

```java
public class TypedDetailDemo {
    static final class DomainFailure extends RuntimeException {
        private final Object detail;

        DomainFailure(String message, Object detail) {
            super(message);
            this.detail = detail;
        }

        <T> T detailAs(Class<T> type) {
            return type.cast(detail);
        }
    }

    public static void main(String[] args) {
        DomainFailure failure =
                new DomainFailure("invalid amount", Integer.valueOf(25));

        Integer amount = failure.detailAs(Integer.class);
        System.out.println("detail=" + amount);
        System.out.println("message=" + failure.getMessage());

        try {
            String wrong = failure.detailAs(String.class);
            System.out.println(wrong);
        } catch (ClassCastException ex) {
            System.out.println("wrong-type=ClassCastException");
        }
    }
}
```

```bash
javac -encoding UTF-8 TypedDetailDemo.java
java TypedDetailDemo
```

Kết quả:

```text
detail=25
message=invalid amount
wrong-type=ClassCastException
```

Lớp `DomainFailure` là một **non-generic exception class**, không có `<T>` trên khai báo lớp. Phương thức `detailAs` mới có `<T>`; nó dùng `Class<T>.cast(...)` để kiểm tra kiểu được yêu cầu khi chạy, thay vì ép kiểu mù hoặc nghĩ rằng erasure tự bảo vệ dữ liệu.

Ví dụ này minh họa tính hợp lệ của generic method, **không phải lời khuyên lưu mọi metadata của exception dưới dạng `Object`**. Trong nhiều API, thuộc tính với kiểu cụ thể như `String fieldName`, `int errorCode` hoặc `List<String> violations` dễ đọc, dễ kiểm thử và an toàn hơn.

## 10. Phân biệt generic `throws`, checked exception và `RuntimeException`

Để hiểu những giới hạn này, cần tách ba tầng quy tắc:

1. **Khai báo lớp:** generic class không được kế thừa `Throwable` trực tiếp hoặc gián tiếp (JLS §8.1.2).
2. **Khai báo phương thức:** một type variable có bound phù hợp `Throwable` được phép xuất hiện trong `throws` (JLS §8.4.6).
3. **Tham số `catch`:** type variable không được dùng làm kiểu exception parameter (JLS §14.20).

Việc một ngoại lệ là **checked** hay **unchecked** là trục quy tắc khác:

| Loại | Ví dụ | Nghĩa vụ bình thường của bên gọi |
| --- | --- | --- |
| Checked exception | `IOException`, custom `extends Exception` (không thuộc `RuntimeException`) | Bắt hoặc khai báo `throws` nếu có thể thoát ra |
| Unchecked exception | `RuntimeException` và các lớp con | Không bắt buộc khai báo `throws` |
| Unchecked error | `Error` và các lớp con | Không bắt buộc khai báo `throws`; thường không phải nhóm cần bắt để phục hồi |

**Cả checked và unchecked đều không được phép khai báo lớp ngoại lệ generic.** Viết `extends RuntimeException` không phải đường vòng để có `MyException<String>` hợp lệ.

Ngược lại, `<E extends Exception> ... throws E` là một khái niệm về **generic method và type bound**. Không cần tạo ra `Exception<E>` để thực hiện nó.

## 11. `catch` cụ thể và multi-catch vẫn dùng như bình thường

Khi có hai loại lỗi mà một quy trình cần xử lý chung, Java hỗ trợ multi-catch:

```java
try {
    readOrQuery();
} catch (java.io.IOException | java.sql.SQLException ex) {
    reportFailure(ex);
}
```

Đây là **đoạn minh họa**, giả sử `readOrQuery()` có thể ném hai checked exceptions tương ứng và `reportFailure(Exception)` đã được định nghĩa.

Các loại trong multi-catch là những **lớp ngoại lệ cụ thể**, được phân biệt tại runtime. Cú pháp này **không đòi generic exception class**. Một multi-catch duy nhất chọn và thực thi một thân xử lý cho các loại tương ứng.

Điều Java không cho phép là đặt một **type variable** thay cho tên lớp trong nhánh `catch`:

```java
// CỐ Ý KHÔNG HỢP LỆ:
catch (E ex) {
    // E extends Exception vẫn không hợp lệ ở vị trí này.
}
```

Với mã điều phối generic cần xử lý mọi `E` theo một chính sách, bạn có thể chọn bắt một lớp cha cụ thể ở tầng có trách nhiệm chung, hoặc không bắt và để `throws E` truyền lỗi cho tầng trên. Không viết `catch (T)` chỉ để làm cho mã trông “tổng quát”.

## 12. Các phương án thiết kế thay thế và khi nào dùng

| Nhu cầu thực tế | Thiết kế nên cân nhắc | Vì sao |
| --- | --- | --- |
| Một lỗi nghiệp vụ có các thuộc tính cố định | Non-generic `DomainException` với trường `String`, `int`, `enum`, `List<String>` | Giữ ngoại lệ cụ thể, thuộc tính có kiểu |
| Nhiều loại lỗi riêng biệt cần bắt riêng | Các lớp `FileFailure`, `ValidationFailure`, `ServiceFailure` không generic | `catch` theo kiểu runtime riêng |
| Muốn cùng một thuật toán chuyển tiếp nhiều loại checked exception | Generic method `<E extends Exception> ... throws E` | Giữ chính xác type bound mà không tạo generic exception |
| Muốn kết quả có giá trị hoặc lỗi có kiểu | Generic `Result<T,E>`/`Outcome<T,E>` không extends Throwable | Generics nằm ở vùng quản lý kết quả |
| Muốn chuyển lỗi giữa các tầng | Non-generic custom exception nhận `(String, Throwable)` | Giữ cause chain, tránh mất ngữ cảnh |
| Muốn đọc detail theo kiểu yêu cầu | Generic method `<T> T detailAs(Class<T>)`, hoặc trường cụ thể | Có thể kiểm tra kiểu runtime bằng `Class<T>` |
| Muốn phân loại nhiều loại lỗi để phản hồi khác nhau | Các `catch` cụ thể, thứ tự chuyên biệt → tổng quát | Phù hợp cơ chế JVM và hợp đồng Java |

Đừng tạo một phân cấp exception quá sâu chỉ để mô phỏng các tham số generic. Chọn lớp ngoại lệ đại diện cho **ý nghĩa lỗi cần bắt hoặc truyền**, còn dữ liệu bổ sung mang theo lỗi có thể nằm trong các trường có kiểu.

## 13. Những lỗi hiểu sai và cách sửa

| Hiểu sai | Kết quả thực tế | Cách sửa |
| --- | --- | --- |
| “`Exception<T>` hoặc `RuntimeException<T>` là kiểu chuẩn của JDK.” | Các lớp đó không generic. | Dùng `Exception`, `RuntimeException` hoặc custom exception không generic. |
| “Chỉ `extends Throwable` trực tiếp mới bị cấm.” | Kế thừa gián tiếp cũng bị cấm. | Kiểm tra toàn chuỗi lớp cha. |
| “Thêm một lớp ngoại lệ trung gian giúp hợp lệ hóa `Failure<T>`.” | Không; `Failure<T>` vẫn là subclass of Throwable. | Chuyển T sang field, method hoặc result holder. |
| “Tạo generic exception rồi phân biệt `catch (Failure<String>)` với `catch (Failure<Integer>)`.” | Không có lớp generic exception hợp lệ; type erasure cũng không cho handler phân biệt kiểu tham số. | Sử dụng lớp ngoại lệ cụ thể khác nhau khi thật sự cần phân nhánh runtime. |
| “Có giới hạn này nên exception không được chứa `List<String>`.” | Trường generic trong non-generic exception là hợp lệ. | Khai báo trường có kiểu, không thêm `<T>` lên lớp. |
| “Phương thức generic không thể ném checked exception.” | `throws E` được phép nếu bound phù hợp. | Sử dụng generic `throws` có chủ đích. |
| “Nếu `throws E` hợp lệ thì `catch (E ex)` cũng hợp lệ.” | JLS cấm type variable trong exception parameter. | Bắt lớp cụ thể hoặc truyền lỗi lên trên. |
| “Một lớp `Result<T,E>` chứa lỗi cũng bị cấm.” | Chỉ bị cấm nếu lớp generic đó là subclass of Throwable. | Giữ Result là lớp dữ liệu, không extends Throwable. |
| “`T extends Throwable` tự động tạo ngoại lệ lớp `T` mới.” | Type variable không phải constructor/lớp runtime mới. | Nhận đối tượng lỗi hoặc dùng factory rõ ràng. |
| “Cứ dùng `catch(Exception)` là đúng khi viết mã generic.” | Bắt quá rộng có thể che lỗi ngoài chính sách. | Cân nhắc throws E hoặc các catch cụ thể. |
| “Lưu metadata vào `Object` rồi ép kiểu tùy ý là type-safe.” | Có thể gây `ClassCastException`. | Ưu tiên trường có kiểu hoặc `Class<T>.cast` kiểm tra. |
| “Muốn một lỗi mang T thì phải `extends Exception<T>`.” | JDK `Exception` không có type parameter; custom generic exception bị cấm. | Thiết kế non-generic exception chứa metadata có kiểu, hoặc generic result. |

## 14. Quy trình chọn thiết kế cho custom exception

Khi muốn viết `MyException<T>`, thử làm theo các bước:

**Bước 1 — Xác định điều đang được generic hóa.** Đó là loại dữ liệu của lỗi, loại kết quả, chiến lược xử lý hay danh tính class ngoại lệ? Nếu chỉ là thông tin liên quan tới sự cố, nó thường nên là **field**, không phải tham số của lớp exception.

**Bước 2 — Định nghĩa các kiểu ngoại lệ mà `catch` cần phân biệt.** Nếu người gọi cần xử lý “sai định dạng” khác với “thiếu tệp”, có thể dùng hai lớp ngoại lệ cụ thể không generic hoặc một lớp chung với mã lỗi ổn định, tùy hợp đồng.

**Bước 3 — Giữ nguyên nhân gốc.** Custom exception nên có constructor nhận `(String message, Throwable cause)` khi chuyển lỗi từ tầng dưới. Không thay `cause` chỉ bằng `cause.getMessage()`.

**Bước 4 — Chuyển tính generic tới nơi hợp lệ.** Sử dụng generic method để viết thuật toán dùng lại hoặc generic result holder nếu cần mang theo một giá trị T và một lỗi E.

**Bước 5 — Kiểm tra vị trí tham số kiểu.** Khai báo lớp exception **không có `<T>`**; nếu type variable nằm trong `throws E` thì bound phải thích hợp; trong `catch (E ex)` thì không hợp lệ.

**Bước 6 — Kiểm tra checked exception.** Các phương thức có thể ném checked exception phải được `catch` hoặc khai báo `throws` đúng kiểu, kể cả khi mã gọi nằm trong một lớp generic.

**Bước 7 — Viết test với cả đường thành công lẫn thất bại.** Tránh chỉ nhìn xem đoạn mã “có vẻ generic”; hãy thực sự kiểm tra `javac`, rồi kiểm tra đúng kiểu ngoại lệ, message, cause và đầu ra.

**Bước 8 — Giới hạn metadata.** Các trường trong exception không nên lưu dữ liệu nhạy cảm rồi xuất thẳng sang log. Chỉ mang thông tin cần thiết cho quyết định xử lý và chẩn đoán.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `class NetworkError<T> extends RuntimeException` có hợp lệ không?  
**Không.** Đây là generic class gián tiếp kế thừa `Throwable`, bị JLS §8.1.2 cấm.

**Câu 2.** `class NetworkError extends RuntimeException { List<String> hosts; }` thì sao?  
**Hợp lệ** về giới hạn generic exception. Lớp không generic, còn `List<String>` chỉ là kiểu của field.

**Câu 3.** Tại sao `catch (Failure<String>)` và `catch (Failure<Integer>)` không phải giải pháp?  
Vì generic exception class như `Failure<T>` đã bị cấm; hơn nữa, JVM không phân biệt hai tham số hóa bằng kiểu handler runtime do type erasure.

**Câu 4.** Một phương thức có thể khai báo `<E extends Exception> void relay(E ex) throws E` không?  
**Có.** JLS cho phép type variable trong mệnh đề `throws` nếu bound phù hợp.

**Câu 5.** Tại sao `catch (E ex)` không được dùng ngay trong phương thức generic nói trên?  
Vì JLS §14.20 cấm dùng type variable làm kiểu của exception parameter.

**Câu 6.** Generic `Result<T,E extends Exception>` có bị cấm không?  
**Không**, nếu nó không kế thừa trực tiếp hoặc gián tiếp `Throwable`.

**Câu 7.** `class GenericFailure<T> extends ApplicationFailure` có hợp lệ khi `ApplicationFailure extends RuntimeException` không?  
**Không.** Lớp generic đó vẫn là subclass gián tiếp của `Throwable`.

**Câu 8.** Một lớp exception không generic có thể khai báo `<T> T detailAs(Class<T> type)` không?  
**Có.** Generic method không biến chính lớp exception thành generic.

**Câu 9.** `throw ex;` với `E extends Exception` có thể biên dịch trong generic method không?  
**Có**, nếu hợp đồng checked exception được đáp ứng, ví dụ phương thức khai báo `throws E`.

**Câu 10.** Nhu cầu chính là một lỗi kèm thông tin `List<String>`. Nên làm gì?  
Thường tạo **non-generic custom exception** với trường `List<String>` và constructor phù hợp. Chỉ dùng Result holder nếu mô hình giao tiếp chủ đích muốn biểu diễn thành công/thất bại dưới dạng giá trị.

## Tài liệu chuẩn

- [Java Language Specification 21 — §8.1.2 Generic Classes and Type Parameters](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.1.2): giới hạn generic subclass of `Throwable`.
- [JLS 21 — §8.4.6 Method Throws](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.6): type variable được phép xuất hiện trong mệnh đề `throws`.
- [JLS 21 — §14.20 The `try` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20): type variable không được dùng làm kiểu tham số `catch`.
- [JLS 21 — §14.18 The `throw` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.18): quy tắc ném đối tượng ngoại lệ.
- [JLS 21 — §4.6 Type Erasure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6): ý nghĩa của việc xóa thông tin kiểu generic.
- [JLS 21 — §11.2 Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2): checked exception và nghĩa vụ `catch`/`throws`.

**Ghi nhớ:** Java **cấm generic class là lớp con của `Throwable`**, nhưng **không cấm dùng generic field, generic method, generic result holder hay `throws E`** để làm việc với ngoại lệ. Chọn một lớp ngoại lệ không generic mang ý nghĩa lỗi ổn định, giữ metadata bằng trường có kiểu, và chỉ generic hóa phần thuật toán hoặc dữ liệu thực sự cần tính tổng quát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-009">Serialization và serialVersionUID khi hợp đồng có yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Serialization và `serialVersionUID` khi hợp đồng ngoại lệ tùy chỉnh có yêu cầu

**Ngoại lệ tùy chỉnh trong Java có thể được tuần tự hóa (serialization)** vì `java.lang.Throwable` đã triển khai `java.io.Serializable`. Tuy nhiên, việc một exception *có thể* đi qua cơ chế Java Object Serialization không đồng nghĩa mọi ứng dụng *nên* ghi ngoại lệ đó thành byte, lưu lâu dài hoặc gửi qua mạng. Nếu thật sự có **hợp đồng** lưu/truyền đối tượng exception bằng Java serialization, cần thiết kế kỹ **`serialVersionUID`, cấu trúc các field, cause, suppressed exceptions, tính tương thích phiên bản và an toàn deserialization**.

Bài học tập trung vào câu hỏi: **Khi nào việc serialization là một phần hợp đồng của custom exception, và `serialVersionUID` phải được xử lý như thế nào?** Các ví dụ độc lập dùng Java 17 trở lên.

Tóm tắt trước:

- Kế thừa `Exception` hoặc `RuntimeException` đều dẫn tới một lớp con của `Throwable`, nên **đều nằm trong hệ phân cấp hỗ trợ `Serializable`**.
- `serialVersionUID` là **mã phiên bản của lớp serializable** dùng trong kiểm tra tương thích dữ liệu khi deserialization; nó **không** phải ID của một object hay mã lỗi nghiệp vụ.
- **Không phải lúc nào custom exception cũng cần công bố hợp đồng serialization.** Chỉ vì IDE gợi ý “add `serialVersionUID`” không có nghĩa chương trình phải lưu/gửi exception giữa các tiến trình.
- Khi thực sự cần tương thích giữa các phiên bản của **cùng lớp**, việc khai báo rõ `private static final long serialVersionUID` và duy trì serialized form có thể có giá trị.
- **Giữ cùng `serialVersionUID` chưa bảo đảm mọi thay đổi đều tương thích.** Cấu trúc lớp, kiểu field, hierarchy, logic serialization tùy chỉnh và các lớp trong đồ thị đối tượng vẫn quan trọng.
- **Không deserialize byte không đáng tin cậy bằng `ObjectInputStream` một cách tùy tiện**; Java native serialization có rủi ro bảo mật và ràng buộc phiên bản lớn. Với API công khai, thường nên chuyển thông tin lỗi thành DTO/schema rõ ràng.

## 1. Bắt đầu từ hệ phân cấp: custom exception đã `Serializable`

Hệ phân cấp liên quan:

```text
java.lang.Object
    └── java.lang.Throwable implements java.io.Serializable
          ├── java.lang.Error
          └── java.lang.Exception
                ├── CustomCheckedException
                └── java.lang.RuntimeException
                      └── CustomUncheckedException
```

Nếu khai báo:

```java
class ImportFailedException extends Exception {
    ImportFailedException(String message) {
        super(message);
    }
}

class WorkflowFailureException extends RuntimeException {
    WorkflowFailureException(String message) {
        super(message);
    }
}
```

đây là **hai class minh họa**, không có `main`. Cả hai kế thừa khả năng Java serialization theo `Throwable`; không cần viết thêm `implements Serializable` chỉ để bật tính năng ấy.

Điều này **không** thay đổi quy tắc checked/unchecked:

- `ImportFailedException` là **checked exception**; caller phải catch hoặc declare khi có thể ném.
- `WorkflowFailureException` là **unchecked exception**; caller không bị bắt buộc bởi compiler.
- **Cả hai** có thể được truyền cho `ObjectOutputStream.writeObject` về mặt kiểu, nhưng quá trình ghi có thể thất bại nếu đồ thị object chứa field không serializable hoặc gặp điều kiện khác.

**Phân loại checked/unchecked và khả năng serialization là hai trục khác nhau.** Không chọn `extends RuntimeException` để “dễ serialize”, cũng không chọn `extends Exception` chỉ vì muốn lưu ngoại lệ.

## 2. Serialization nghĩa là gì trong bài này?

**Java Object Serialization** là cơ chế ghi trạng thái của một đồ thị object được hỗ trợ thành một định dạng byte có thể đọc lại bởi `ObjectInputStream` trong một môi trường Java tương thích.

Hai API tiêu biểu:

- `ObjectOutputStream.writeObject(object)`: ghi object/đồ thị object.
- `ObjectInputStream.readObject()`: đọc lại object từ stream và có thể tạo các instance Java phù hợp.

Ví dụ khung:

```java
try (ObjectOutputStream out =
             new ObjectOutputStream(outputStream)) {
    out.writeObject(exception);
}
```

Đây là **mã minh họa**, cần `outputStream`, `exception` và các import được khai báo, đồng thời phải xử lý/khai báo checked `IOException`. Việc ghi object **không** đồng nghĩa một exception được `throw`. Ghi một object ngoại lệ là thao tác dữ liệu; Java không tự ném exception ấy chỉ vì serialize nó.

Cần phân biệt:

| Hoạt động | Mục đích | Có liên quan trực tiếp đến `serialVersionUID` không? |
| --- | --- | --- |
| `throw new MyException(...)` | Báo lỗi trên call stack hiện tại | Không phải thao tác đọc/ghi serialized form |
| `catch (MyException e)` | Bắt một exception đang lan truyền | Không |
| `e.getMessage()` | Đọc thông điệp | Không |
| `e.printStackTrace()` | In thông tin chẩn đoán | Không |
| `writeObject(e)` | Serialize đồ thị object | Có, khi ghi mô tả lớp serializable |
| `readObject()` | Deserialize đồ thị object | Có, khi kiểm tra tương thích lớp với dữ liệu đã ghi |

Không nên gộp “log exception”, “gửi message cho người dùng” và “Java serialize object Throwable” thành một khái niệm. Đa số hoạt động log và API response **không cần** native Java serialization.

## 3. `serialVersionUID` là gì?

Trong Java serialization, lớp serializable có một **serial version UID** kiểu `long`. Khi deserialize, Java sử dụng thông tin của lớp trong stream và mô tả lớp ở phía đọc để kiểm tra khả năng tương thích, trong đó có việc so sánh `serialVersionUID` đối với những lớp áp dụng cơ chế phiên bản thông thường.

Khai báo chuẩn:

```java
private static final long serialVersionUID = 1L;
```

Nên viết field này **bên trong chính lớp custom exception** nếu bạn quyết định chủ động quản lý phiên bản serialized form.

Giải thích từng thành phần:

- `private`: đây là chi tiết triển khai của lớp, không phải dữ liệu API mà caller thường truy cập.
- `static`: thuộc lớp, không thuộc từng instance exception.
- `final`: là hằng số; không thay đổi theo object.
- `long`: kiểu yêu cầu cho giá trị serial version UID.
- `1L`: một giá trị minh họa, **không bắt buộc phải là một số ngẫu nhiên hay duy nhất trên toàn ứng dụng**.

Một số điều **serialVersionUID không phải**:

- Không phải “mã lỗi” để hiển thị cho người dùng.
- Không phải version của thư viện theo Semantic Versioning.
- Không phải định danh duy nhất của từng instance Throwable.
- Không tự động nâng lên khi build phiên bản phần mềm mới.
- Không mã hóa hay bảo vệ dữ liệu.
- Không bảo đảm lớp tương thích khi schema thay đổi.
- Không bắt buộc có cùng giá trị giữa tất cả các class cùng nhánh exception.

Mỗi lớp serializable trong phân cấp có thể có **UID của chính lớp đó**. UID khai báo ở lớp cha không thay thế việc quản lý serialized form của lớp con khi lớp con có hợp đồng riêng.

## 4. Khi nào thực sự cần quan tâm tới UID?

Nên xem xét kỹ khi **hợp đồng đã xác định** rằng object exception cần được Java serialize và **đọc lại sau đó**, nhất là qua các phiên bản chương trình khác nhau. Ví dụ:

1. Một thành phần nội bộ đang lưu các Throwable bằng `ObjectOutputStream` và cần phục hồi chúng bằng `ObjectInputStream`.
2. Một hệ thống Java-to-Java cũ có giao thức thực tế dựa trên Java serialization, nơi exception được truyền dưới dạng object serializable.
3. Một chức năng lưu trạng thái hoặc cơ chế kiểm thử tương thích đã được thiết kế sử dụng serialized object của exception.
4. Một thư viện có cam kết rằng **serialized form của custom exception** ổn định giữa các bản phát hành nhất định.

Trong những tình huống đó, cần xác định **phạm vi phiên bản phải tương thích, thời gian lưu dữ liệu, chính sách nâng cấp và phạm vi field cần ổn định**.

Ngược lại, nếu exception chỉ:

- được `throw`/`catch` trong cùng chương trình,
- được ghi vào log,
- được ánh xạ thành HTTP status và error response,
- được trình bày trong một CLI,
- hoặc được dùng trong unit test thông thường,

thì **không nhất thiết có yêu cầu runtime về Java serialization**. Việc chủ động khai báo UID vẫn có thể hợp lý để giữ một quy ước code hoặc tránh cảnh báo công cụ, nhưng không nên tuyên bố “đã hỗ trợ lưu/gửi exception tương thích lâu dài” chỉ nhờ thêm một dòng UID.

## 5. Compiler có tự tạo UID nếu không khai báo không?

Có cơ chế tạo **UID mặc định** cho lớp serializable khi lớp không khai báo một `serialVersionUID` hợp lệ. Giá trị mặc định được tính từ thông tin cấu trúc của lớp theo thuật toán serialization của Java.

Nhưng giá trị tính tự động có thể **thay đổi khi mã nguồn hoặc chữ ký lớp thay đổi**, kể cả những thay đổi không có vẻ là “thay đổi dữ liệu” đối với người viết. Điều đó có thể khiến dữ liệu đã serialize bằng phiên bản cũ không đọc được bởi phiên bản mới.

Vì vậy, với những class thật sự cam kết tương thích serialized form, **khai báo rõ UID** giúp việc quản lý phiên bản có chủ đích hơn:

```java
public class JobFailureException extends Exception {
    private static final long serialVersionUID = 1L;

    public JobFailureException(String message) {
        super(message);
    }
}
```

Đây là **một class hoàn chỉnh nhưng không có `main`**, nên lưu thành `JobFailureException.java`. Phải phân biệt:

- UID **ổn định** chỉ giúp vượt qua một trong các kiểm tra tương thích.
- Để tiếp tục tương thích, những thay đổi serialized form cũng phải tuân thủ các quy tắc Java serialization.
- Cố giữ UID của một thay đổi **không tương thích** không làm object trở nên tự tương thích.

Nếu không có yêu cầu persistence/compatibility thật, không cần thiết kế cơ chế migration chỉ vì IDE đã phát ra cảnh báo `serial`.

## 6. Một custom exception có UID nên khai báo như thế nào?

Ví dụ checked exception chứa message, cause và mã lỗi đơn giản:

```java
public class DataLoadException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public DataLoadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DataLoadException(
            String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

Đây là **một class Java độc lập** để đặt trong `DataLoadException.java`. `Throwable` đã hỗ trợ Serializable, vì vậy class không cần ghi `implements Serializable` lần nữa.

Các quyết định thiết kế:

- `errorCode` là `String` và serializable.
- `message` và `cause` được chuyển cho superclass.
- `serialVersionUID = 1L` chủ động đánh dấu phiên bản serialized form của class hiện tại.
- `getErrorCode()` là hợp đồng ứng dụng, **không** phải cơ chế phiên bản Java serialization.
- Các field được thêm về sau cần xem xét tương thích, chứ không mặc định đổi UID sau mọi commit.

Nếu class dùng `enum` làm error code thì enum có cơ chế serialization đặc thù; các tên enum và chính sách tiến hóa enum vẫn phải được cân nhắc. Mã lỗi trên API công khai còn cần ổn định theo hợp đồng nghiệp vụ, độc lập với UID.

## 7. Ví dụ Java 17 chạy được: round-trip một custom exception

Ví dụ này chứng minh message, mã lỗi, cause và suppressed exception có thể được ghi rồi đọc lại khi toàn bộ đồ thị object phù hợp với Java serialization.

Lưu thành **`CustomExceptionRoundTripDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class ImportException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String code;

    ImportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String getCode() {
        return code;
    }
}

public class CustomExceptionRoundTripDemo {
    static byte[] encode(ImportException error) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out =
                     new ObjectOutputStream(bytes)) {
            out.writeObject(error);
        }
        return bytes.toByteArray();
    }

    static ImportException decode(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            return (ImportException) in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        IOException source = new IOException("Tệp không đọc được");
        ImportException original = new ImportException(
                "IMPORT_IO", "Nhập dữ liệu thất bại", source);
        original.addSuppressed(
                new IllegalStateException("Cleanup thất bại"));

        byte[] data = encode(original);
        ImportException restored = decode(data);

        System.out.println("Cùng instance: " + (original == restored));
        System.out.println("Mã lỗi: " + restored.getCode());
        System.out.println("Message: " + restored.getMessage());
        System.out.println("Cause type: "
                + restored.getCause().getClass().getSimpleName());
        System.out.println("Cause message: "
                + restored.getCause().getMessage());
        System.out.println("Suppressed: "
                + restored.getSuppressed().length);
        System.out.println("Lỗi phụ: "
                + restored.getSuppressed()[0].getMessage());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 CustomExceptionRoundTripDemo.java
java CustomExceptionRoundTripDemo
```

**Stdout chính xác** với các lớp từ ví dụ và stream chỉ chứa dữ liệu do chương trình tự tạo:

```text
Cùng instance: false
Mã lỗi: IMPORT_IO
Message: Nhập dữ liệu thất bại
Cause type: IOException
Cause message: Tệp không đọc được
Suppressed: 1
Lỗi phụ: Cleanup thất bại
```

Giải thích:

1. `original` là instance `ImportException` có field `code`, message và cause.
2. `addSuppressed` gắn thêm một Throwable phụ.
3. `ObjectOutputStream` ghi đồ thị object vào byte array.
4. `ObjectInputStream` đọc một **instance mới** tương ứng với dữ liệu serialized.
5. `original == restored` là `false`, vì hai instance không giống identity.
6. Message, field `code`, cause và suppressed được phục hồi theo cơ chế mặc định trong tình huống này.
7. **Không có lệnh `throw restored`** trong chương trình. Deserialization không đồng nghĩa ném lỗi đã phục hồi.

Đoạn `main throws Exception` ở đây chỉ để tập trung vào cơ chế serialization; trong ứng dụng thực, ranh giới sẽ cần chính sách bắt và xử lý lỗi I/O, dữ liệu sai định dạng, thiếu class hoặc dữ liệu không đáng tin.

## 8. Deserialize không “chạy lại” lần lỗi ban đầu

Một đối tượng exception được deserialize là **dữ liệu đã được phục hồi**, không phải sự kiện lỗi đang tự xảy ra lại.

Nói rõ:

- `getMessage()` trên object phục hồi đọc message đã được serialize.
- `getCause()` có thể tham chiếu đến cause object đã được phục hồi.
- `getSuppressed()` có thể chứa các object được phục hồi.
- `getStackTrace()` có thể cung cấp dữ liệu trace đã ghi tại lần lỗi cũ, tùy serialized form và cơ chế Throwable.
- `readObject()` **không tự thực hiện `throw restored`**.
- Nếu sau đó mã chủ động `throw restored`, nó ném object ấy; thông thường lệnh `throw` không tự làm mới stack trace của object.

Stack trace đọc lại thường **không** chỉ tới thời điểm decode. Nó là dữ liệu của lỗi đã được ghi và serialize trước đó, trừ khi code/JVM có hành vi đặc biệt như `fillInStackTrace()` hoặc `setStackTrace()`.

Vì vậy không nên dùng stack trace đã deserialize để suy luận rằng chương trình ở thời điểm hiện tại vừa chạy qua các frame cũ.

## 9. Khi nào xuất hiện `InvalidClassException`?

`java.io.InvalidClassException` là một loại `ObjectStreamException` và có thể xảy ra khi Java nhận thấy **lớp ở phía đọc không tương thích với lớp được mô tả trong stream**.

Tình huống phổ biến:

1. Phiên bản V1 của `com.example.DataLoadException` được serialize với UID = `1L`.
2. Phiên bản V2 ở phía đọc có cùng tên lớp nhưng UID = `2L`.
3. Khi `ObjectInputStream` cố phục hồi object, việc kiểm tra phiên bản có thể dẫn tới **`InvalidClassException`** do UID không khớp.

Đây là **mô tả tình huống qua hai phiên bản**, không phải một chương trình nguồn đơn chạy đồng thời hai định nghĩa trùng tên. Trong cùng một class loader, không thể tùy ý định nghĩa hai phiên bản có cùng binary name theo cách chỉ đặt hai class cùng tên vào một file.

Ví dụ khai báo V1 và V2 để hiểu vấn đề:

**V1** – `DataLoadException.java` được build/phân phối ở bản đầu:

```java
public class DataLoadException extends Exception {
    private static final long serialVersionUID = 1L;

    public DataLoadException(String message) {
        super(message);
    }
}
```

**V2** – class **cùng binary name** ở bản mới:

```java
public class DataLoadException extends Exception {
    private static final long serialVersionUID = 2L;

    public DataLoadException(String message) {
        super(message);
    }
}
```

Đây là **hai phiên bản thay thế**, không được đặt cùng lúc trên cùng classpath. Dữ liệu được ghi bằng V1 rồi đọc bởi V2 có thể không qua được kiểm tra UID vì phiên bản stream và local khác nhau.

Một thông báo lỗi thực tế thường đề cập `stream classdesc serialVersionUID` và `local class serialVersionUID`. Tên class, đường dẫn và số UID hiển thị tùy tình huống; không phụ thuộc nguyên văn message exception để quyết định nghiệp vụ.

**Không nên tăng UID chỉ vì tăng phiên bản phát hành.** Khi muốn duy trì tương thích của serialized form, cần có lý do để giữ hoặc thay đổi UID dựa trên hợp đồng tương thích, không theo lịch phiên bản một cách máy móc.

## 10. UID khớp cũng có thể vẫn không đọc được dữ liệu cũ

Giả sử giữ `serialVersionUID = 1L` qua nhiều phiên bản. Điều đó **không bảo đảm** mọi thay đổi lớp tương thích với object stream cũ.

Một số thay đổi cần đặc biệt thận trọng:

- **Đổi kiểu field** đã được ghi trong serialized form có thể gây không tương thích.
- **Đổi tên hoặc package của class** có thể thay đổi binary name cần giải quyết khi đọc.
- **Thay đổi quan hệ kế thừa serializable/non-serializable** hoặc chuyển giữa cơ chế `Serializable` và `Externalizable` có các quy tắc đặc thù.
- **Thêm logic `writeObject`/`readObject` tùy chỉnh** không nhất quán giữa hai phiên bản có thể làm đọc sai dữ liệu, dù UID vẫn giữ.
- **Thay đổi bất biến (invariant) của class** có thể khiến trạng thái phục hồi trước đó không còn hợp lệ.
- **Thay đổi field thành kiểu không serializable** có thể làm ghi object thất bại với `NotSerializableException`.
- **Lớp của cause/field lồng bên trong** không còn tồn tại hoặc thay đổi không tương thích có thể khiến cả đồ thị không phục hồi được.

Đổi UID cũng **không tự chuyển dữ liệu** sang schema mới. Cơ chế version/migration là trách nhiệm riêng của người thiết kế giao thức lưu trữ.

Nếu serialized form thực sự là hợp đồng, cần xác định cụ thể **được phép thay đổi field nào**, đọc ngược từ phiên bản nào, và có test dùng dữ liệu thật của phiên bản cũ hay không.

## 11. Thêm field có luôn phá tính tương thích?

Trong nhiều trường hợp **default serialization** của Java, việc thêm một field serializable mới trong class có cùng UID là một **thay đổi có thể tương thích theo hướng đọc dữ liệu cũ bằng phiên bản mới**. Field mới không tồn tại trong stream cũ và có thể nhận **giá trị mặc định** khi deserialization.

Ví dụ tư duy:

**V1:**

```java
class ImportFailure extends Exception {
    private static final long serialVersionUID = 1L;
    String code;
}
```

**V2:**

```java
class ImportFailure extends Exception {
    private static final long serialVersionUID = 1L;
    String code;
    String category;
}
```

Đây là **hai phiên bản thay thế**, không phải hai class được biên dịch cùng tên trong một bộ nguồn. Nếu V2 đọc dữ liệu V1 theo quy tắc mặc định và không có thay đổi không tương thích khác, field `category` có thể nhận **`null`**, vì stream V1 không có dữ liệu cho field này.

Tương tự:

- Field `int` mới có thể nhận `0` khi không có dữ liệu.
- Field `boolean` mới có thể nhận `false`.
- Field tham chiếu mới có thể nhận `null`.

**Điểm dễ sai:** đừng giả định initializer trong mã nguồn V2 tự chạy và thay các giá trị đó trong deserialization mặc định. Đối với các lớp serializable được phục hồi, constructor và field initializer của chính lớp serializable **không được thực thi như quá trình `new` bình thường**. Những constructor liên quan phần superclass đầu tiên không serializable tuân theo quy tắc riêng.

Nếu `category` mới là bắt buộc, cần thiết kế logic phục hồi/kiểm tra phiên bản cũ hoặc duy trì schema cụ thể. **Chỉ giữ UID cũ không làm xuất hiện dữ liệu chưa từng được ghi**.

## 12. Xóa một field có luôn phá tính tương thích?

Với default Java serialization, việc **xóa một field** đôi khi vẫn cho phép đọc dữ liệu cũ bằng class mới: dữ liệu field không còn hiện diện trong class đọc có thể bị bỏ qua theo cơ chế ánh xạ field.

Nhưng không được suy ra mọi trường hợp đều an toàn:

- Có thể mất thông tin cần thiết cho logic phục hồi hoặc chẩn đoán.
- Code `readObject` tùy chỉnh có thể phụ thuộc field cũ.
- Các lớp lồng nhau hoặc thay đổi kiểu khác có thể vẫn gây không tương thích.
- Hợp đồng API của getter có thể đã thay đổi dù dữ liệu đọc được.

Ví dụ: bỏ field `requestId` để tránh ghi dữ liệu nhạy cảm là một quyết định về bảo mật và schema, không chỉ một thay đổi trình biên dịch. Cần đánh giá mọi nơi đọc object cũ và nơi sử dụng field ấy.

Với dữ liệu cần tồn tại lâu dài, **tập hợp các thay đổi tương thích phải được kiểm chứng bằng test cross-version**, không dựa duy nhất vào suy đoán rằng Java sẽ bỏ qua phần dư.

## 13. Không phải field nào trong custom exception cũng serializable

Kế thừa `Throwable` có nghĩa class có thể tham gia Java serialization, **không có nghĩa mọi field instance mà bạn thêm đều tự serializable**.

Ví dụ một exception nắm tham chiếu tới `Thread`:

```java
class WorkerFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Thread workerThread;

    WorkerFailure(String message, Thread workerThread) {
        super(message);
        this.workerThread = workerThread;
    }
}
```

Đây là **class minh họa**. `Thread` không triển khai `Serializable`, vì vậy nếu default serialization cố ghi field `workerThread` khác `null`, nó có thể ném `NotSerializableException`.

Hệ quả quan trọng:

- **Có UID không sửa được lỗi field không serializable**.
- Không nên đưa những object như thread, socket, service/client, lock, callback hoặc một đối tượng hạ tầng sống vào serialized form của exception chỉ để debug.
- Nếu cần lưu thông tin nhận diện, dùng dữ liệu có cấu trúc như `String workerName`, `String operationId` hoặc mã lỗi thay vì object đang hoạt động.
- Nếu field chỉ dùng lúc runtime, cân nhắc `transient`; nhưng phải chấp nhận dữ liệu ấy **không được phục hồi**.

Các field mới của custom exception cần được review như một **data model** nếu class tham gia serialization; không chỉ đơn giản thêm bất cứ thứ gì tiện dùng.

## 14. Ví dụ chạy được: `NotSerializableException` từ field

Lưu **`NonSerializableFieldDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.IOException;

class TaskFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Thread thread;

    TaskFailure(String message, Thread thread) {
        super(message);
        this.thread = thread;
    }
}

public class NonSerializableFieldDemo {
    public static void main(String[] args) throws IOException {
        TaskFailure error =
                new TaskFailure("Task thất bại", Thread.currentThread());

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(error);
            System.out.println("Không tới dòng này");
        } catch (NotSerializableException e) {
            System.out.println("Không thể serialize: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 NonSerializableFieldDemo.java
java NonSerializableFieldDemo
```

**Stdout chính xác** trên Java 17 thông thường:

```text
Không thể serialize: java.lang.Thread
```

Dù `TaskFailure extends RuntimeException` và khai báo UID rõ ràng, `Thread` không thể ghi bằng default serialization. `NotSerializableException` mô tả lớp không serializable gặp trong đồ thị object.

Đây là **lỗi ở thao tác serialize**, không phải `TaskFailure` tự được ném. Chương trình bắt `NotSerializableException` do `ObjectOutputStream` phát sinh.

Nếu sau này bỏ field `thread` hoặc thay nó bằng `String threadName`, cần cân nhắc serialized form mới và khả năng đọc dữ liệu cũ nếu đã công bố hợp đồng.

## 15. Field `transient`: giữ dữ liệu chỉ cho runtime, không đưa vào stream

Từ khóa `transient` đánh dấu một field instance **không tham gia default serialization** của class. Điều này hữu ích khi field giữ thông tin tạm thời, nhạy cảm hoặc object không serializable.

Ví dụ:

```java
class JobFailure extends Exception {
    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private transient String debugNote;

    JobFailure(String errorCode, String debugNote) {
        super("Job thất bại");
        this.errorCode = errorCode;
        this.debugNote = debugNote;
    }
}
```

Đây là **class minh họa**, chưa có `main`. Sau deserialize mặc định:

- `errorCode` có thể được phục hồi.
- `debugNote` **không được lưu**, nên trường tham chiếu thường nhận `null`.
- `serialVersionUID` không phải field instance của object, và không được lưu như trạng thái instance thông thường.

Không dùng `transient` để che việc class không có thiết kế schema: nếu một field thực sự là bất biến bắt buộc cho xử lý sau khi phục hồi, loại bỏ nó khỏi serialized form có thể tạo object ở trạng thái thiếu dữ liệu.

Bảo mật cũng không chỉ là đánh dấu `transient`: detail message, cause, suppressed exceptions hoặc các field khác vẫn có thể chứa thông tin nhạy cảm.

## 16. Ví dụ chạy được: dữ liệu `transient` biến mất sau deserialize

Lưu **`TransientExceptionDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class RemoteTaskException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String code;
    private transient String localDebug;

    RemoteTaskException(String code, String localDebug) {
        super("Remote task failed");
        this.code = code;
        this.localDebug = localDebug;
    }

    String getCode() {
        return code;
    }

    String getLocalDebug() {
        return localDebug;
    }
}

public class TransientExceptionDemo {
    public static void main(String[] args) throws Exception {
        RemoteTaskException original =
                new RemoteTaskException("TASK_01", "trace chỉ ở máy này");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(original);
        }

        RemoteTaskException copy;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(buffer.toByteArray()))) {
            copy = (RemoteTaskException) in.readObject();
        }

        System.out.println("Code: " + copy.getCode());
        System.out.println("Message: " + copy.getMessage());
        System.out.println("Debug null: " + (copy.getLocalDebug() == null));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 TransientExceptionDemo.java
java TransientExceptionDemo
```

**Stdout chính xác:**

```text
Code: TASK_01
Message: Remote task failed
Debug null: true
```

Lý do `debugNote`/`localDebug` không tồn tại sau phục hồi là **chính sách `transient`**, không phải lỗi `serialVersionUID`. Mã ứng dụng dùng object phục hồi phải chấp nhận và xử lý trạng thái đó.

## 17. Cause, suppressed exceptions và stack trace cũng ảnh hưởng hợp đồng

`Throwable` lưu nhiều dữ liệu hơn message. Một custom exception có thể mang:

- **Detail message**: thông qua `getMessage()`.
- **Cause**: tham chiếu exception nguyên nhân, truy cập `getCause()`.
- **Suppressed exceptions**: mảng lỗi phụ, truy cập `getSuppressed()`.
- **Stack trace**: dữ liệu các stack frame khi object được tạo/điền trace hoặc bị sửa theo API.
- **Field custom**: mã lỗi, loại tác vụ, tham số, ngữ cảnh chẩn đoán.

Khi dùng Java serialization mặc định trong một tình huống được hỗ trợ, các đối tượng liên quan như cause và suppressed có thể thuộc **đồ thị object cần được ghi/đọc**.

Cần chú ý:

1. Một cause cũng là `Throwable` nhưng có thể là lớp con tùy chỉnh với field không serializable.
2. Một suppressed exception cũng có thể chứa các field tùy chỉnh và các quan hệ cause/suppressed tiếp theo.
3. Stack trace có thể chứa thông tin môi trường và vị trí source của nơi lỗi được tạo, **không phải** stack hiện tại khi decode.
4. Object đọc lại không tự trải qua một lần thất bại mới.
5. Các trạng thái exception đặc biệt (tắt suppression, tắt writable stack trace) có thể ảnh hưởng dữ liệu quan sát được.
6. Việc serialization thành công không phải bằng chứng rằng dữ liệu đủ an toàn để hiển thị cho người dùng.

Không kiểm thử chỉ message nếu hợp đồng đã hứa có mã lỗi/cause/suppressed; cần test những phần dữ liệu được cam kết.

## 18. `serialVersionUID` của custom subclass khác `serialVersionUID` của `Throwable`

`Throwable` là một class serializable có serialized form của riêng nó. Lớp exception do bạn tạo **cũng có mô tả lớp và UID riêng** theo cơ chế Java serialization.

Ví dụ:

```java
class BaseImportException extends Exception {
    private static final long serialVersionUID = 1L;
}

class CsvImportException extends BaseImportException {
    private static final long serialVersionUID = 2L;
}
```

Đây là **hai class minh họa**, không có `main`. Không có quy tắc bắt buộc hai UID giống nhau hay bắt buộc chúng phải khác nhau. Mỗi UID liên quan tới **serialized form của chính lớp tương ứng**, không phải một hệ thống đánh số phiên bản chung.

Nếu `CsvImportException` có field riêng, serialized form của nó cần được quản lý phù hợp. Đổi serialized form của lớp cha cũng có thể ảnh hưởng khả năng phục hồi một object lớp con.

**Không được suy luận rằng vì superclass đã có UID nên lớp con khỏi phải xem xét hợp đồng của mình.** Đồng thời, không cần tạo một hệ thống “UID theo cây” nếu chẳng có yêu cầu đọc object giữa các phiên bản.

## 19. Lệnh `serialver` dùng để xem UID

JDK cung cấp công cụ **`serialver`** để tra cứu `serialVersionUID` của một class serializable mà JDK có thể load.

Ví dụ với class đã compile:

```bash
javac --release 17 JobFailureException.java
serialver JobFailureException
```

Đây là **lệnh minh họa**, giả định file `JobFailureException.java` có class đã được trình bày ở phần trước và JDK cung cấp `serialver` trên `PATH`.

Nếu class nằm trong package, truyền **tên đầy đủ** và đặt classpath phù hợp:

```bash
serialver -classpath build/classes com.example.JobFailureException
```

Đây là ví dụ sử dụng cú pháp CLI, không mặc định đường dẫn `build/classes` tồn tại trong mọi dự án.

Công cụ có thể giúp kiểm tra UID đang được công nhận, nhưng **không thể thay thế việc kiểm thử serialization/deserialization giữa hai phiên bản**. Hai class có UID giống nhau vẫn có thể khác biệt về schema hay hành vi sau phục hồi.

Đừng dùng kết quả `serialver` như một mã lỗi nghiệp vụ, đừng chép một UID ngẫu nhiên từ class khác vào chỉ vì muốn “đồng bộ”.

## 20. Có nên tăng UID mỗi lần thay đổi class?

**Không phải lúc nào cũng tăng.** Đây là hiểu lầm lớn.

Một chính sách thực tế:

- Nếu class không công bố hợp đồng serialization bền vững: có thể dùng UID cố định theo quy ước, hoặc chưa cần quản lý tương thích dữ liệu giữa các bản.
- Nếu class cam kết **tương thích ngược**, giữ UID thích hợp trong phạm vi các thay đổi serialized form được phép, đồng thời test dữ liệu từ phiên bản trước.
- Nếu có thay đổi thật sự **phá vỡ khả năng phục hồi** và chính sách là từ chối stream cũ, thay đổi UID có thể là một cách bộc lộ việc không tương thích; vẫn cần cân nhắc migration và quy trình nâng cấp.
- Nếu dữ liệu phải được duy trì dù class đổi sâu, đừng trông chờ UID đơn lẻ: thiết kế schema/versioning ngoài hoặc cách đọc chuyển đổi dữ liệu có chủ đích.

Số `1L` không biểu thị “ứng dụng phiên bản 1.0”. Không có quy tắc Java yêu cầu khi ứng dụng lên bản 2.0 thì mọi UID phải trở thành `2L`.

## 21. Serialization compatibility khác API compatibility

Một class có thể:

- **Biên dịch với code caller cũ** nhưng không đọc được object bytes cũ.
- **Đọc được object bytes cũ** nhưng hành vi public API đã thay đổi khiến caller không còn hoạt động đúng.
- Có `serialVersionUID` khớp nhưng thiếu field bắt buộc sau khi deserialize.
- Có message/cause phục hồi được nhưng mất một field `transient` mà logic nghiệp vụ lại phụ thuộc.

Ví dụ một bản cập nhật đổi tên getter `getCode()` thành `code()` có thể gây lỗi cho mã Java gọi API dù serialized form không đổi. Ngược lại, thay đổi kiểu field riêng tư có thể ảnh hưởng serialization dù chữ ký public gần như giữ nguyên.

Vì vậy cần hai nhóm kiểm thử khác nhau:

| Loại kiểm thử | Câu hỏi |
| --- | --- |
| API/source compatibility | Caller cũ còn compile và sử dụng được không? |
| Serialization compatibility | Dữ liệu byte được ghi bởi bản cũ có đọc được ở bản mới không? |
| Behavioral compatibility | Object phục hồi có còn tuân thủ ý nghĩa và bất biến hợp đồng không? |
| Security compatibility | Nội dung dữ liệu có còn an toàn và được phép phục hồi ở bối cảnh mới không? |

**UID chỉ phục vụ một phần của hàng thứ hai.**

## 22. Vì sao không nên dùng Java serialization làm định dạng public API cho exception?

Trong hệ thống hiện đại, nhiều ứng dụng cần gửi thông tin lỗi qua HTTP, message queue hoặc lưu nhật ký dài hạn. Mục tiêu thường là **gửi một mô tả có cấu trúc về lỗi**, không phải truyền toàn bộ đối tượng Java `Throwable`.

Với native Java serialization, người nhận có thể bị ràng buộc vào:

- Binary name/package và classpath Java của phía gửi.
- `serialVersionUID` và serialized form của nhiều class.
- Đồ thị object gồm cause/suppressed/custom fields.
- Các quy tắc tương thích Java serialization và version của thư viện.
- Rủi ro an ninh khi deserialize dữ liệu không đáng tin cậy.
- Thông tin nội bộ hoặc nhạy cảm vô tình xuất hiện trong message/stack trace.

Với API công khai, thường nên **chuyển exception thành DTO/error envelope** chứa những trường được thiết kế rõ, ví dụ:

```json
{
  "code": "IMPORT_FAILED",
  "message": "Không thể nhập dữ liệu",
  "requestId": "req-123"
}
```

Đây là **mẫu JSON**, không phải định dạng do `ObjectOutputStream` tạo. Phía nhận hiểu schema mà không cần load class Java của server. Những chi tiết kỹ thuật như stack trace, cause và suppressed thường nên ở hệ thống log/tracing bảo vệ phù hợp, không trả thẳng cho người dùng.

Java serialization không phải bị “cấm” trong mọi ứng dụng; nó chỉ cần một **bối cảnh tin cậy, hợp đồng rõ ràng và lý do thực tế**.

## 23. Mô hình thay thế: error DTO thay vì serialize `Throwable`

Khi mục tiêu chỉ là **truyền trạng thái thất bại**, tạo một dữ liệu lỗi độc lập có thể đơn giản hơn. Java 17 có `record`:

```java
record ErrorInfo(String code, String message) {
}

class ImportFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    ImportFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    ErrorInfo toErrorInfo() {
        return new ErrorInfo(code, "Không thể nhập dữ liệu");
    }
}
```

Đây là **hai loại minh họa**, không có `main`. `ErrorInfo` diễn tả dữ liệu giao tiếp được phép, trong khi `ImportFailure` vẫn giữ exception và chẩn đoán nội bộ.

`record` trong Java có cơ chế đối xử với serialization riêng khi triển khai `Serializable`; ở đây ví dụ **chỉ mô tả DTO**, không tự đưa record đó vào Java native object stream. Nếu chọn JSON hoặc giao thức khác, cần bộ mã hóa/giải mã phù hợp.

Một schema thông điệp lỗi ổn định có thể gồm: `code`, message đã rà soát, request ID và loại phản hồi. **Không đưa stack trace vào DTO công khai** chỉ vì exception object có nó.

## 24. Rủi ro bảo mật khi deserialize object không đáng tin cậy

`ObjectInputStream.readObject()` không đơn thuần đọc vài chuỗi vô hại; nó có thể kích hoạt cơ chế tạo/khôi phục các object Java và thực thi logic phục hồi của các lớp liên quan. Đồ thị object phức tạp hoặc class có hành vi deserialization nguy hiểm có thể tạo rủi ro.

Các nguyên tắc:

1. **Không deserialize dữ liệu từ người lạ hoặc mạng không tin cậy** bằng Java native serialization trừ khi có thiết kế an toàn nghiêm túc.
2. Ưu tiên **giao thức dữ liệu có schema rõ ràng** khi mục tiêu là trao đổi lỗi.
3. Nếu bắt buộc dùng Java serialization trong môi trường kiểm soát, giới hạn **loại class có thể được deserialize**, kích thước, độ sâu và số lượng tham chiếu qua cơ chế thích hợp như `ObjectInputFilter`.
4. Kiểm soát classpath, các dependency và phiên bản được phép.
5. Sau deserialize, **xác thực dữ liệu và bất biến** trước khi sử dụng trong nghiệp vụ.
6. Không coi `serialVersionUID` là cơ chế xác thực nguồn gửi hoặc chống sửa đổi dữ liệu.
7. Không lưu/hiển thị nguyên message, stack trace, cause và suppressed ở nơi không có quyền truy cập phù hợp.
8. Nếu lưu stream dài hạn, phải có chính sách lưu trữ, mã hóa khi phù hợp, xoay phiên bản và xóa dữ liệu.

**Bộ lọc deserialization là biện pháp giảm thiểu, không phải lý do để tùy tiện mở một endpoint nhận `ObjectInputStream` từ Internet.** Ví dụ round-trip trong bài chỉ xử lý byte tạo ngay trong cùng chương trình để minh họa hành vi Java.

## 25. Kiểm thử tương thích giữa các phiên bản đúng cách

Một test round-trip **trong cùng bản build** giúp xác nhận object có thể serialize và deserialize ngay lúc đó, nhưng **không đủ** để chứng minh dữ liệu từ bản cũ đọc được ở bản mới.

Một quy trình kiểm thử có ý nghĩa:

1. **Định nghĩa hợp đồng:** phiên bản nào phải đọc được stream của phiên bản nào? Có cần đọc ngược hay chỉ đọc dữ liệu cũ bằng bản mới?
2. **Tạo fixture byte từ bản phát hành thật**, bằng JDK và class của phiên bản đã công bố. Lưu fixture kiểm thử ở nơi được kiểm soát.
3. **Đọc fixture bằng bản mới**, với chính classpath, policy và filter mà ứng dụng dự kiến sử dụng.
4. Kiểm tra **message, error code, cause, suppressed** và các field thực sự nằm trong hợp đồng.
5. Kiểm tra object phục hồi **đạt bất biến**; không chỉ không ném `InvalidClassException`.
6. Kiểm thử đường lỗi: UID lệch, class thiếu, field không serializable, stream hỏng và dữ liệu vượt giới hạn.
7. Kiểm thử việc tạo object của **bản mới** rồi đọc bằng **bản cũ** chỉ nếu hợp đồng yêu cầu chiều ngược đó.
8. Ghi nhận quyết định: giữ UID, thay UID, thực hiện migration hay dừng hỗ trợ dữ liệu quá cũ.

Nếu không lưu fixture và chỉ chạy `serialize -> deserialize` bằng cùng class hiện tại, một thay đổi bất tương thích qua các bản có thể không được phát hiện.

## 26. Khi nào nên viết `readObject` / `writeObject` tùy chỉnh?

Java cho phép một số class triển khai logic serialization tùy chỉnh, chẳng hạn:

```java
private void writeObject(ObjectOutputStream out)
        throws IOException {
    out.defaultWriteObject();
}

private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
}
```

Đây là **cặp phương thức minh họa**, chỉ có ý nghĩa khi được khai báo trong một lớp serializable phù hợp và import các loại `java.io`. Chúng không cần thiết nếu chỉ muốn round-trip mặc định.

Khi hợp đồng serialized form thực sự phức tạp, có thể dùng hook để kiểm soát dữ liệu hoặc kiểm tra bất biến. Nhưng:

- Mã `readObject`/`writeObject` tùy chỉnh làm **hợp đồng binary trở nên khó tiến hóa** hơn nếu thiếu tài liệu và fixture test.
- Không được quên gọi `defaultReadObject`/`defaultWriteObject` khi schema mong đợi cơ chế mặc định.
- Hook phải xét dữ liệu không hợp lệ, thiếu field, thay đổi phiên bản và an toàn deserialization.
- Mã tùy chỉnh có thể làm phát sinh `IOException` hoặc `ClassNotFoundException` đúng hợp đồng.
- Một Throwable còn có các thuộc tính superclass như cause/stack trace; thiết kế custom field không được giả định mình kiểm soát toàn bộ serialized form của superclass.

Đối với phần lớn custom exception ứng dụng, **tránh thêm logic serialization tùy chỉnh chỉ để hết cảnh báo IDE**. Nếu không cần lưu/truyền object, giữ thiết kế exception đơn giản sẽ an toàn và dễ bảo trì hơn.

## 27. Serializing một exception không phải cơ chế lưu lịch sử đáng tin tuyệt đối

Một exception là **dữ liệu chẩn đoán**, nhiều thành phần của nó gắn với thời điểm và môi trường chạy: message kỹ thuật, tên lớp, stack trace, cause và suppressed. Khi lưu thành object bytes, các chi tiết đó có thể trở nên:

- Cũ và không phản ánh code hiện tại.
- Khó đọc nếu class đã đổi hoặc bị xóa.
- Nhạy cảm vì có tên file, package, thông tin hệ thống hoặc dữ liệu người dùng.
- Khó lọc/tìm kiếm hơn một log hoặc sự kiện có schema rõ ràng.
- Không còn đầy đủ nếu các field `transient` bị bỏ hoặc suppression/stack trace bị tắt.

Nếu yêu cầu nghiệp vụ là **audit trail**, thường nên lưu event có cấu trúc: thời điểm, mã lỗi ổn định, ID tác vụ, hệ thống phát sinh và metadata cho phép. Throwable bytes có thể là dữ liệu phụ dành cho chẩn đoán nội bộ nếu được cấp quyền và có chính sách lưu trữ rõ.

Đừng dùng `serialVersionUID` thay cho một **schema version** có ngữ nghĩa trong tài liệu audit hoặc API giao tiếp.

## 28. Các hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Chỉ exception `implements Serializable` mới ghi bằng `ObjectOutputStream`.” | `Throwable` đã triển khai `Serializable`, nên lớp con custom nằm trong hệ phân cấp đó. |
| “`extends Exception` mới Serializable; `extends RuntimeException` không.” | Cả hai đều kế thừa `Throwable`. |
| “Bất kỳ custom exception nào cũng bắt buộc khai báo UID để chạy.” | `throw`/`catch` không cần UID; Java có cơ chế UID mặc định khi serialization. |
| “Thêm `serialVersionUID` làm mọi field đều serializable.” | Không. Field chứa object không serializable vẫn có thể gây `NotSerializableException`. |
| “`serialVersionUID` là ID của từng exception instance.” | Không. Nó liên quan version của class khi serialization. |
| “Mỗi lần nâng phiên bản ứng dụng phải tăng UID.” | Không; quyết định dựa trên hợp đồng tương thích serialized form. |
| “Giữ UID giống nhau luôn đọc được mọi stream cũ.” | Không. Thay đổi schema, hierarchy và custom logic vẫn có thể không tương thích. |
| “Đổi UID không ảnh hưởng dữ liệu đã lưu.” | Có thể gây `InvalidClassException` khi đọc dữ liệu từ UID trước. |
| “Thêm field mới luôn có giá trị từ initializer khi deserialize dữ liệu cũ.” | Không. Field thiếu trong stream thường nhận giá trị mặc định theo cơ chế serialization. |
| “`transient` field tự được lưu nếu constructor gán.” | Không trong default serialization; constructor của serializable class cũng không chạy như `new` bình thường khi phục hồi. |
| “Cause/suppressed của exception luôn được copy sang một DTO/API response.” | Không. Đó là hai thiết kế khác nhau; bạn phải chủ động chọn dữ liệu. |
| “Deserialize exception nghĩa là exception ấy được ném.” | Không. `readObject` trả một object; ném là thao tác riêng. |
| “UID là cơ chế bảo mật.” | Không. Nó không xác thực và không bảo vệ stream khỏi dữ liệu độc hại. |
| “Round-trip cùng bản build bảo đảm tương thích với bản cũ.” | Không. Phải kiểm thử cross-version bằng fixture dữ liệu cũ. |
| “Đã có Throwable thì cứ serialize để gửi HTTP.” | Không. DTO/schema có cấu trúc thường phù hợp hơn cho giao tiếp công khai. |
| “`getStackTrace()` sau decode chỉ đến dòng decode.” | Thông thường nó phản ánh thông tin đã được lưu trong object; decode không tự làm mới trace. |

## 29. Câu hỏi tự kiểm tra

**1. `class MyException extends Exception` đã Serializable chưa?**  
Đã có khả năng tham gia Java serialization vì kế thừa `Throwable`, không cần lặp lại `implements Serializable`.

**2. `class MyException extends RuntimeException` có khác ở điểm đó không?**  
Không. Khác biệt checked/unchecked liên quan bắt/khai báo, không phải bản chất `Serializable`.

**3. `serialVersionUID = 1L` dùng để xác định instance exception nào?**  
Không xác định instance. Nó là phiên bản của class trong cơ chế kiểm tra serialized form.

**4. Vì sao nên khai báo UID tường minh khi có hợp đồng tương thích lâu dài?**  
Để chủ động quản lý UID của class thay vì phụ thuộc tính toán mặc định có thể đổi theo chi tiết cấu trúc class.

**5. Hai phiên bản cùng UID có luôn tương thích không?**  
Không. Cần xem schema, kiểu field, class hierarchy, các lớp trong đồ thị và logic đọc/ghi.

**6. Tăng UID cho mỗi bản phát hành có phải luôn đúng không?**  
Không. Điều đó có thể làm mất khả năng đọc dữ liệu cũ mà không đem lại lợi ích.

**7. Field custom chứa `Thread` khác `null`, default serialization có thể xảy ra lỗi gì?**  
`NotSerializableException` vì `Thread` không serializable.

**8. Field `transient` sẽ thế nào khi đọc lại bằng default serialization?**  
Không được lưu; với field tham chiếu, thường nhận `null` nếu không có cơ chế phục hồi tùy chỉnh.

**9. Đọc lại serialized exception có làm ném nó lên call stack không?**  
Không. Phải chủ động `throw` object nếu muốn ném.

**10. Cause và suppressed có được serialize trong ví dụ round-trip đã cho không?**  
Có, vì đồ thị được thiết kế gồm các Throwable serializable phù hợp; kết quả được kiểm tra bằng `getCause()` và `getSuppressed()`.

**11. Nếu deserialize object đã được tạo ở phiên bản cũ, stack trace có phải ảnh chụp call stack mới không?**  
Thông thường không. Đó là dữ liệu trace đã lưu; decode không tự chụp lại.

**12. Với API HTTP, có bắt buộc gửi `Throwable` bằng Java serialization để truyền lỗi không?**  
Không. Thường nên dùng error DTO và một giao thức có schema rõ ràng.

**13. `InvalidClassException` có thể báo điều gì?**  
Một lớp ở phía đọc không tương thích với lớp được mô tả trong stream, thường gặp khi UID lệch.

**14. Có thể dùng UID để xác thực dữ liệu đến từ một máy chủ tin cậy không?**  
Không. UID không phải chữ ký số hay cơ chế xác thực.

**15. Tại sao cần fixture từ bản cũ?**  
Vì round-trip chỉ với cùng bản build không kiểm tra được tương thích dữ liệu giữa các phiên bản.

**16. Nếu thêm field mới sau khi bản cũ đã ghi stream, giá trị của field mới sẽ thế nào?**  
Theo default serialization ở trường hợp tương thích, field vắng trong stream có thể nhận giá trị mặc định, ví dụ `null` cho tham chiếu.

**17. Một exception object có một cause custom không serializable hoàn toàn thì sao?**  
Serialization của toàn bộ đồ thị có thể thất bại vì field không serializable trong lớp cause, dù exception ngoài đã có UID.

**18. Khi nào phải nghĩ tới `ObjectInputFilter`?**  
Khi ứng dụng thực sự dùng Java deserialization và cần áp giới hạn/lọc các class có thể được phục hồi trong bối cảnh đã được đánh giá an toàn.

## 30. Checklist trước khi cam kết serialized form của custom exception

1. **Có yêu cầu Java native serialization thật không?** Nếu chỉ log hoặc trả API response, đừng tạo hợp đồng không cần thiết.
2. **Phạm vi tin cậy là gì?** Stream đến từ chính chương trình, hệ thống nội bộ đã kiểm soát hay nguồn bên ngoài?
3. **Class có khai báo UID tường minh không?** Nếu cam kết tương thích, phải quản lý UID có chủ đích.
4. **Có phân biệt UID với schema/error code của ứng dụng không?**
5. **Mỗi field instance có serializable không?** Có object sống (thread, socket, service) đang bị giữ trong exception không?
6. **Có field nên đánh dấu `transient` không?** Hệ quả sau phục hồi đã được xử lý chưa?
7. **Có dữ liệu nhạy cảm trong message/cause/suppressed/stack trace không?**
8. **Dữ liệu từ các phiên bản cũ có phải đọc được không?** Phải giữ fixture và test cross-version.
9. **Thêm/xóa/đổi kiểu field có tương thích theo hợp đồng không?**
10. **Thay đổi class name, package hoặc hierarchy có ảnh hưởng serialized form không?**
11. **Có custom `readObject`/`writeObject` không?** Có kiểm tra dữ liệu/bất biến và khả năng tương thích không?
12. **Có test cho `InvalidClassException`, `ClassNotFoundException`, `NotSerializableException` và stream hỏng không?**
13. **Có bảo vệ `ObjectInputStream` khỏi dữ liệu không đáng tin cậy không?** Đánh giá nhu cầu filter và giới hạn độ sâu/kích thước.
14. **Có thể dùng DTO/schema rõ ràng thay vì serialize toàn bộ Throwable không?**
15. **Object phục hồi có còn đúng ý nghĩa nghiệp vụ và không tự kích hoạt hành động nguy hiểm không?**
16. **Có chính sách deprecation/migration dữ liệu serialized cũ không?**
17. **Có theo dõi thay đổi của các class cause/suppressed và dependency liên quan không?**
18. **Có phân biệt hợp đồng API/source compatibility với serialization compatibility không?**
19. **Đã xác nhận serialization không bị dùng sai làm giải pháp ghi log/audit mặc định chưa?**
20. **Nếu không có hợp đồng serialization, đã tránh tạo cam kết tương thích dài hạn không cần thiết chưa?**

## 31. Kết luận

**Custom exception Java đã có khả năng Serializable thông qua `Throwable`**, bất kể checked hay unchecked. Tuy nhiên, cần tách khả năng kỹ thuật đó khỏi **yêu cầu hợp đồng**: hầu hết lỗi chỉ cần ném, bắt, log hoặc chuyển thành thông điệp có cấu trúc; không nhất thiết phải lưu/gửi Throwable bằng Java Object Serialization.

Khi hợp đồng **thật sự đòi hỏi** serialize và deserialize custom exception qua các phiên bản, hãy **khai báo và quản lý `serialVersionUID` tường minh**, hiểu serialized form của từng lớp và field, giữ message/cause/suppressed theo dữ liệu được phép, kiểm thử bằng byte fixture của phiên bản cũ và đặt an toàn deserialization lên hàng đầu.

**UID khớp chỉ là một điều kiện tương thích, không phải bảo đảm toàn phần; UID cũng không phải mã lỗi hoặc cơ chế bảo mật.** Nếu cần giao tiếp API bền vững, một error DTO/schema rõ ràng thường có ít ràng buộc hơn, dễ quản lý tương thích và an toàn hơn việc truyền toàn bộ đồ thị `Throwable`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-exceptions-010">Tránh kế thừa trực tiếp Throwable hoặc Error khi không phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh kế thừa trực tiếp `Throwable` hoặc `Error` khi không phù hợp trong Java

Khi tạo **ngoại lệ tùy chỉnh (custom exception)**, câu hỏi đầu tiên không chỉ là *“đặt tên class gì?”* mà còn là *“class đó nên kế thừa nhánh nào trong cây `Throwable`?”*. Lựa chọn lớp cha quyết định loại lỗi có bị kiểm tra lúc biên dịch hay không, các khối `catch` có nhận được nó hay không, ý nghĩa mà API gửi đến caller và mức độ dễ bảo trì.

**Quy tắc thực hành cho hầu hết ứng dụng Java:**

- Với lỗi ứng dụng mà bên gọi **được kỳ vọng bắt hoặc khai báo** theo hợp đồng checked exception, cân nhắc kế thừa `Exception` hoặc một subclass checked phù hợp.
- Với lỗi tham số, trạng thái hoặc hợp đồng ứng dụng sử dụng **unchecked exception**, cân nhắc `RuntimeException` hay một subclass như `IllegalStateException`.
- **Không kế thừa trực tiếp `Throwable`** chỉ để “tạo loại lỗi tổng quát nhất”: điều này thường tạo một checked exception không thuộc nhánh `Exception` và gây bất ngờ cho caller.
- **Không kế thừa `Error`** cho những thất bại nghiệp vụ, validation, I/O hay lỗi tích hợp thông thường: nhánh này có ý nghĩa đặc biệt và không nên dùng như cách né `throws`.

Hai lựa chọn cuối không phải lúc nào cũng bị Java cấm về mặt cú pháp. Điều quan trọng là chúng thường **không đúng ngữ nghĩa hoặc hợp đồng xử lý** của lỗi mà bạn đang thiết kế.

## 1. Đọc đúng cây kế thừa `Throwable`

Phân cấp rút gọn:

~~~text
java.lang.Object
    |
    +-- java.lang.Throwable
          |
          +-- java.lang.Error
          |      +-- AssertionError
          |      +-- LinkageError
          |      +-- VirtualMachineError
          |             +-- OutOfMemoryError
          |
          +-- java.lang.Exception
                 |
                 +-- IOException
                 |
                 +-- RuntimeException
                        +-- IllegalArgumentException
                        +-- IllegalStateException
                        +-- NullPointerException
~~~

Trong Java:

- `Throwable` là lớp gốc của đối tượng có thể dùng với `throw` và nhận qua `catch`.
- `Exception` là nhánh thường dùng để biểu diễn những điều kiện ngoại lệ mà ứng dụng có thể quyết định xử lý.
- `RuntimeException` là nhánh **unchecked**, thường dùng cho những điều kiện không bắt buộc caller phải bắt hoặc khai báo ở compile-time.
- `Error` cũng là nhánh **unchecked**, thường biểu thị vấn đề nghiêm trọng của VM, liên kết lớp, bất biến hệ thống hoặc những điều kiện mà mã ứng dụng thông thường không được kỳ vọng phục hồi.
- Một lớp **kế thừa trực tiếp `Throwable`**, nhưng không thuộc `RuntimeException` hoặc `Error`, là **checked exception**.

Điều kiện checked/unchecked được suy ra từ **kiểu kế thừa**, không từ tên class và cũng không từ mức độ “nguy hiểm” mà người viết gán cho lỗi.

### Phân biệt “có thể viết” với “nên viết”

Ví dụ Java **hợp lệ về cú pháp**:

~~~java
final class DirectFailure extends Throwable {
    DirectFailure(String message) {
        super(message);
    }
}
~~~

Cũng có thể định nghĩa:

~~~java
final class BusinessFailureError extends Error {
    BusinessFailureError(String message) {
        super(message);
    }
}
~~~

Nhưng hai cách đó có hậu quả quan trọng mà không thể nhìn thấy chỉ bằng tên class. Bài học sẽ phân tích vì sao trong phần lớn code nghiệp vụ nên chọn hướng khác.

## 2. Vì sao kế thừa trực tiếp `Throwable` thường là lựa chọn sai?

Một custom exception trực tiếp kế thừa `Throwable` nằm **ngoài cả nhánh `Exception` lẫn `Error`**. Java vẫn cho ném nó, nhưng nhiều API và handler của ứng dụng được thiết kế theo `Exception`, không theo lớp gốc `Throwable`.

Ví dụ:

~~~java
final class DirectFailure extends Throwable {
    DirectFailure(String message) {
        super(message);
    }
}

static void work() throws DirectFailure {
    throw new DirectFailure("cannot complete work");
}
~~~

Đây là hai khai báo minh họa có thể đặt trong cùng một lớp Java.

`DirectFailure` ở đây là **checked**. Vì vậy `work()` phải bắt hoặc khai báo nó như ví dụ. Caller cũng cần xử lý hoặc chuyển tiếp loại checked đó.

### 2.1. `catch (Exception)` không nhận được subclass trực tiếp của `Throwable`

~~~java
static void caller() throws DirectFailure {
    try {
        work();
    } catch (Exception e) {
        System.out.println("Exception handler");
    }
}
~~~

Đây là phương thức minh họa **có thể biên dịch** khi `work()` ném `DirectFailure`. `catch (Exception e)` không bắt được `DirectFailure` vì nó không kế thừa `Exception`. Do đó `caller()` vẫn phải khai báo `throws DirectFailure` hoặc có một handler khác phù hợp.

Caller thông thường dễ lầm tưởng `catch (Exception)` xử lý toàn bộ lỗi kiểu “exception” của ứng dụng, nhưng với một subtype trực tiếp của `Throwable`, giả định đó không đúng.

### 2.2. Có thể làm chữ ký `throws` trở nên quá rộng

Nếu thư viện dùng `throws Throwable` để tránh nêu từng lỗi:

~~~java
static void execute() throws Throwable {
    throw new DirectFailure("cannot complete work");
}
~~~

thì caller có thể bị buộc bắt/khai báo `Throwable` hoặc một kiểu phù hợp với lỗi được khai báo ở nơi gọi. Chữ ký đó rộng đến mức trộn các tình huống thông thường với cả `Error`, khiến hợp đồng kém rõ ràng.

Không nên sử dụng `throws Throwable` trong API nghiệp vụ chỉ để “an toàn cho mọi tương lai”. Càng rộng không có nghĩa càng tốt; nó có thể bắt người dùng API xử lý một tập hợp loại lỗi không phù hợp.

### 2.3. Không ăn khớp với nhiều ranh giới xử lý `Exception`

Trong ứng dụng thực tế, những lớp xử lý chung, bộ ánh xạ response hoặc mã xử lý nghiệp vụ có thể chủ động nhận `Exception`. Một subtype trực tiếp của `Throwable` **không thuộc nhóm đó**.

Không khẳng định mọi framework đều không nhận được nó: một framework có thể dùng `Throwable` hoặc các cơ chế đặc biệt. Nhưng việc chọn trực tiếp `Throwable` làm cha **tăng khả năng tạo tình huống bất ngờ**, và cần có hợp đồng rõ ràng nếu thực sự muốn làm vậy.

**Kết luận:** Trừ trường hợp thiết kế framework/máy ảo có lý do đặc biệt, lỗi tùy chỉnh thông thường nên nằm trong nhánh `Exception` hoặc `RuntimeException`, không trực tiếp dưới `Throwable`.

## 3. Vì sao không nên kế thừa `Error` cho lỗi nghiệp vụ?

`Error` là nhánh unchecked của `Throwable`, nhưng nó **không phải tên thay thế cho mọi lỗi nghiêm trọng**.

Một số lớp chuẩn minh họa ý nghĩa khác biệt:

| Lớp | Ý nghĩa tiêu biểu |
| --- | --- |
| `OutOfMemoryError` | JVM không cấp phát được bộ nhớ theo yêu cầu |
| `StackOverflowError` | Quá trình gọi lồng sâu gây tràn stack |
| `LinkageError` | Vấn đề liên kết/định nghĩa lớp |
| `AssertionError` | Một khẳng định nội bộ bị vi phạm |
| `Error` | Lớp cha chung của nhóm lỗi đặc biệt |

Không phải mọi đối tượng `Error` trong mọi môi trường đều có mức độ nghiêm trọng giống nhau, và một chương trình đặc biệt có thể cố ý dùng `AssertionError` trong kiểm thử. Tuy nhiên, **ý nghĩa thông thường của `Error` không phải lỗi nghiệp vụ có thể phục hồi**.

### 3.1. `catch (Exception)` không bắt `Error`

~~~java
try {
    throw new AssertionError("Unexpected invariant failure");
} catch (Exception e) {
    System.out.println("Exception caught");
} catch (AssertionError e) {
    System.out.println("AssertionError caught");
}
~~~

Đây là mảnh mã Java hợp lệ. Đầu ra là:

~~~text
AssertionError caught
~~~

`AssertionError` không kế thừa `Exception`, nên nhánh đầu không nhận nó. Nhánh sau chỉ được dùng ở đây để **minh họa phân cấp kiểu**; không nên biến việc bắt rộng `Error` thành cách phục hồi thông thường ở production.

Nếu bạn tự định nghĩa `PaymentDeclinedError extends Error` cho một kết quả thanh toán dự kiến, các handler vốn bắt `Exception` có thể không nhận nó. Tên `Error` còn truyền sai thông điệp rằng lỗi thuộc loại bất thường nghiêm trọng như lỗi của VM hoặc bất biến hệ thống.

### 3.2. Dùng `Error` để tránh checked exception là sai động cơ

Một số người muốn tránh `throws` nên nghĩ rằng:

~~~java
final class DatabaseUnavailableError extends Error {
    DatabaseUnavailableError(String message) {
        super(message);
    }
}
~~~

Đây là **phản ví dụ về thiết kế**, dù class biên dịch. `DatabaseUnavailableError` là unchecked vì nằm dưới `Error`, nhưng điều đó không biến mất kết nối cơ sở dữ liệu thành lỗi máy ảo. Nếu muốn một lỗi ứng dụng unchecked với ý nghĩa đúng, thường nên dùng `RuntimeException` hoặc một subclass thích hợp.

### 3.3. Tại sao không “bắt `Throwable` rồi tiếp tục”?

~~~java
try {
    performBusinessAction();
} catch (Throwable t) {
    logAndContinue(t);
}
~~~

Đây là đoạn minh họa có hai hàm giả định. Bắt `Throwable` bao gồm `Exception`, `RuntimeException`, `Error` và cả subtype trực tiếp của `Throwable`. Nếu cứ tiếp tục nghiệp vụ sau mọi `Error`, hệ thống có thể vận hành ở trạng thái không an toàn hoặc không còn đủ tài nguyên.

**Bắt `Throwable` chỉ nên có ở các ranh giới đặc biệt với chính sách lỗi rất rõ**, chẳng hạn một số khung thực thi phải ghi nhận trạng thái trước khi thất bại. Nó không phải handler mặc định cho request, thao tác cơ sở dữ liệu hoặc xử lý nhập liệu.

## 4. Chọn lớp cha đúng: `Exception`, `RuntimeException` hay lớp chuyên biệt?

Một cây quyết định đơn giản:

~~~text
Có thật sự cần tạo loại ngoại lệ riêng?
             |
     +-------+-------+
     |               |
    Không            Có
     |               |
Sử dụng ngoại lệ     Caller cần hợp đồng checked?
chuẩn hoặc kiểu           |
kết quả dữ liệu       +----+----+
                      |         |
                     Có        Không
                      |         |
               extends Exception   extends RuntimeException
               (checked)           hoặc subclass unchecked
~~~

Sơ đồ là **định hướng thực hành**, không phải công thức cứng. Một số API chọn unchecked cho lỗi có thể phục hồi, vì caller được thiết kế xử lý ở tầng biên; một số API chọn checked để buộc tầng gọi cân nhắc việc thất bại.

### 4.1. Dùng `Exception` cho hợp đồng checked

~~~java
public final class ConfigurationLoadException extends Exception {
    public ConfigurationLoadException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là một lớp độc lập trong `ConfigurationLoadException.java`.

Khi dùng:

~~~java
static void initialize() throws ConfigurationLoadException {
    try {
        readConfiguration();
    } catch (java.io.IOException cause) {
        throw new ConfigurationLoadException(
                "Cannot initialize configuration", cause);
    }
}
~~~

Đoạn phương thức giả định `readConfiguration()` đã được định nghĩa. Vì `ConfigurationLoadException` là checked, caller cần bắt hoặc khai báo phù hợp.

**Khi hữu ích:** lỗi có ý nghĩa rõ trong hợp đồng, caller được kỳ vọng có quyết định khôi phục hoặc chuyển tiếp, và việc compile-time yêu cầu xử lý đem lại giá trị.

### 4.2. Dùng `RuntimeException` cho hợp đồng unchecked

~~~java
public final class DomainStateException
        extends RuntimeException {
    public DomainStateException(String message) {
        super(message);
    }
}
~~~

Đây là một lớp Java độc lập. Caller không bị trình biên dịch yêu cầu bắt/khai báo nó, nhưng nó vẫn có thể lan truyền và làm thao tác thất bại.

**Khi hữu ích:** một bất biến nghiệp vụ bị vi phạm, một trạng thái không hợp lệ hoặc chính sách API muốn xử lý lỗi tập trung, không áp đặt checked exception ở mọi tầng.

### 4.3. Kế thừa subclass JDK khi nó đã mang đúng nghĩa

Nếu loại lỗi riêng thực sự là một trạng thái không hợp lệ, có thể dùng:

~~~java
public final class OrderNotReadyException
        extends IllegalStateException {
    public OrderNotReadyException(String orderId) {
        super("Order " + orderId + " is not ready");
    }
}
~~~

Đây là một lớp độc lập. Nó vừa có thể được nhận bởi `catch (OrderNotReadyException e)`, vừa thuộc nhóm `IllegalStateException`.

Tuy nhiên, không nên kế thừa một lớp JDK chỉ vì tên nghe có vẻ gần. Ví dụ, một lỗi mất kết nối không phải lỗi tham số; `extends IllegalArgumentException` sẽ truyền sai ngữ nghĩa.

**Mẹo lựa chọn:** cân nhắc cả điều kiện phát sinh lỗi, cách caller dự kiến xử lý, ranh giới module và việc lớp cha có đúng ý nghĩa hay không.

## 5. Checked và unchecked: quy tắc chính xác

Java chia nhóm ngoại lệ cho mục đích kiểm tra tại biên dịch:

**Unchecked:** `RuntimeException` và tất cả subclass của nó; `Error` và tất cả subclass của nó.

**Checked:** các subclass khác của `Throwable` không nằm trong hai nhóm trên. Vì thế **`Throwable` trực tiếp và subclass trực tiếp của nó** là checked.

| Khai báo lớp | Loại | Caller có nghĩa vụ checked? |
| --- | --- | --- |
| `class A extends Exception` | Checked, trừ khi A thực sự kế thừa RuntimeException — điều này không xảy ra với câu lệnh trên | Có |
| `class B extends IOException` | Checked | Có |
| `class C extends RuntimeException` | Unchecked | Không |
| `class D extends IllegalStateException` | Unchecked | Không |
| `class E extends Error` | Unchecked | Không |
| `class F extends Throwable` | Checked | Có |

Cần phân biệt:

- **Checked** không nghĩa là “nhẹ” hay “có thể luôn sửa lỗi”.
- **Unchecked** không nghĩa là “không thể xử lý” hay “không cần log”.
- **Error** không nghĩa là mọi đối tượng được tạo ra từ nó đều do JVM tự ném.
- **Hợp đồng `throws`** mô tả khả năng ngoại lệ checked có thể truyền ra, **không** bảo đảm mọi lần gọi đều ném lỗi.

Khi một API có thể gặp `IOException` do đọc file, giữ `IOException` hoặc định nghĩa subclass checked phù hợp có thể tự nhiên hơn tạo một `Error` chỉ vì sợ viết `throws`.

## 6. Các vấn đề thực tế khi `extends Throwable` trực tiếp

### Vấn đề 1 — Bên gọi bắt `Exception` nhưng vẫn để lọt lỗi

~~~java
final class DirectIssue extends Throwable {
    DirectIssue(String message) {
        super(message);
    }
}

static void throwDirect() throws DirectIssue {
    throw new DirectIssue("direct issue");
}

static void caller() throws DirectIssue {
    try {
        throwDirect();
    } catch (Exception e) {
        System.out.println("Caught as Exception");
    }
}
~~~

Đoạn này có thể đặt các khai báo trong một lớp Java. `caller()` vẫn phải khai báo `throws DirectIssue` vì `catch (Exception)` **không bao phủ** `DirectIssue`.

Nếu thay `DirectIssue extends Throwable` bằng `DirectIssue extends Exception`, một `catch (Exception e)` phù hợp có thể bắt nó. Đó là lý do phân cấp loại ngoại lệ quan trọng hơn việc chỉ nhìn từ khóa `throw`.

### Vấn đề 2 — Chữ ký API trở nên khác thường

~~~java
public void save() throws Throwable
~~~

Đây là chữ ký minh họa. Một public API yêu cầu caller xét `Throwable` là hợp đồng rất rộng. Nó gợi ý rằng phương thức có thể ném cả những nhóm mà mã nghiệp vụ không nên được yêu cầu khôi phục theo cách thông thường.

Nếu nhóm lỗi thực sự chỉ là lỗi I/O hoặc lỗi lưu dữ liệu nghiệp vụ, hãy khai báo `throws IOException`, `throws StorageException` hoặc một nhóm ngoại lệ cụ thể theo kiến trúc.

### Vấn đề 3 — Các handler và tooling có thể không theo kỳ vọng

Code xử lý lỗi với `catch (Exception)` hoặc handler được cấu hình cho các subclass `Exception` sẽ không nhận trực tiếp subtype `Throwable` ở ngoài nhánh đó. Điều này gây ra tình huống handler có vẻ tổng quát nhưng không nhận lỗi được cho là “ngoại lệ ứng dụng”.

Không suy ra rằng tất cả framework sẽ thất bại: những hệ thống bắt `Throwable` có thể nhận. Nhưng đó không phải một lý do tốt để làm loại custom exception thông thường nằm ngoài nhánh `Exception`.

### Vấn đề 4 — Khó bảo trì hợp đồng checked

Một dự án có nhiều lớp kế thừa `Throwable` trực tiếp dễ dẫn tới các chữ ký `throws Throwable` lan rộng. Khi client muốn thu hẹp trách nhiệm, việc đổi loại ngoại lệ và làm cho phần bắt lỗi đúng ngữ nghĩa có thể tốn công.

Với mã nghiệp vụ mới, chọn lớp cha được quy ước và biểu đạt đúng trách nhiệm caller ngay từ đầu sẽ tốt hơn.

## 7. Các vấn đề thực tế khi `extends Error` cho lỗi ứng dụng

Ví dụ **thiết kế không nên áp dụng**:

~~~java
public final class CustomerNotFoundError extends Error {
    public CustomerNotFoundError(String customerId) {
        super("Customer not found: " + customerId);
    }
}
~~~

Dù mã này có thể biên dịch, “không tìm thấy khách hàng” thông thường là một trạng thái nghiệp vụ hoặc kết quả truy vấn, không phải một sự cố thuộc nhánh `Error`.

Phương án cần xét:

- Nếu “không tìm thấy” là kết quả bình thường của tra cứu: cân nhắc `Optional<Customer>` hoặc kiểu kết quả có mã `NOT_FOUND`.
- Nếu thiếu khách hàng vi phạm tiền điều kiện hoặc bất biến: có thể dùng `IllegalArgumentException`, `IllegalStateException` hoặc subclass unchecked phù hợp.
- Nếu hợp đồng API yêu cầu caller bắt cụ thể: cân nhắc một checked `CustomerNotFoundException extends Exception`.
- Nếu dự án dùng lỗi miền unchecked nhất quán: cân nhắc `CustomerNotFoundException extends RuntimeException`.

Mỗi lựa chọn có ý nghĩa riêng. Không có một câu trả lời duy nhất cho mọi ứng dụng, nhưng **`extends Error` thường không phù hợp**.

### Tác động lên các khối `catch`

~~~java
try {
    findCustomer("C-100");
} catch (Exception e) {
    // Handler dành cho các lỗi Exception.
}
~~~

Đây là đoạn minh họa. Nếu `findCustomer` ném `CustomerNotFoundError extends Error`, handler này không bắt được đối tượng đó. Điều này thường không phù hợp với kỳ vọng của tầng ứng dụng đang chuẩn bị phản hồi “không tìm thấy khách hàng”.

Nếu loại riêng kế thừa `RuntimeException` hoặc `Exception`, handler rộng `catch (Exception)` có thể nhận nó, mặc dù **vẫn nên dùng catch hẹp hơn** khi tầng hiện tại có cách xử lý riêng.

## 8. Khi nào có thể hợp lý để tạo subtype của `Error`?

Từ “tránh khi không phù hợp” không có nghĩa mã Java **cấm tuyệt đối** người phát triển tạo subclass `Error`.

Có những tình huống rất đặc biệt trong thư viện cấp thấp, công cụ nội bộ hoặc cơ chế kiểm tra bất biến mà tác giả chủ động muốn biểu đạt thất bại không thuộc hợp đồng ngoại lệ nghiệp vụ thông thường. Một số thư viện kiểm thử sử dụng `AssertionError` để báo một phép kiểm tra thất bại. `AssertionError` thuộc nhánh `Error` theo thiết kế.

Tuy nhiên, trước khi cân nhắc một subclass `Error` riêng, cần trả lời:

1. Lỗi này thực sự có ý nghĩa tương tự một vấn đề hệ thống/khẳng định nội bộ mà caller thông thường không nên xử lý không?
2. Có biết rõ điều gì xảy ra khi `catch (Exception)` không bắt được nó không?
3. Có cần hệ thống tiếp tục hoạt động sau lỗi đó không? Nếu có, dùng `Error` có thể gây hiểu nhầm.
4. Có thể dùng `AssertionError` hoặc exception chuẩn phù hợp thay vì tạo một nhánh `Error` mới không?
5. Tất cả handler và công cụ quan sát có hiểu hợp đồng bất thường này không?

Với code dịch vụ nghiệp vụ, web API, batch job, repository, validation và tích hợp thông thường, **gần như luôn có lựa chọn hợp lý hơn subclass `Error`**.

## 9. Không cần custom exception nếu loại chuẩn đã đủ

Một hệ thống đơn giản có thể chỉ cần:

~~~java
static int divide(int numerator, int denominator) {
    if (denominator == 0) {
        throw new IllegalArgumentException(
                "denominator must not be zero");
    }
    return numerator / denominator;
}
~~~

Đây là phương thức Java hợp lệ. Không cần tạo `ZeroDenominatorThrowable extends Throwable` hoặc `ZeroDenominatorError extends Error`; cả hai sẽ làm hợp đồng khó hiểu hơn.

Tương tự:

- Đối số sai → `IllegalArgumentException`, hoặc `NullPointerException` khi một tham chiếu bắt buộc không được null.
- Trạng thái không phù hợp → `IllegalStateException`.
- Thao tác không hỗ trợ → `UnsupportedOperationException`.
- Lỗi I/O → `IOException` hoặc subtype.
- Thất bại JDBC → `SQLException` ở ranh giới tương ứng.

Nếu nhiều client phải xử lý một loại lỗi miền theo cách riêng, **lúc đó** mới cân nhắc thêm class cụ thể. Loại custom tốt phải có lợi ích rõ ràng, không chỉ có tên dài hơn.

## 10. Đừng dùng ngoại lệ cho mọi kết quả nghiệp vụ dự kiến

Một kết quả không thành công không phải lúc nào cũng là lỗi bất thường.

Ví dụ, tra cứu mặt hàng có thể không tìm thấy ID:

~~~java
static java.util.Optional<String> lookupItem(String id) {
    if ("SKU-1".equals(id)) {
        return java.util.Optional.of("Keyboard");
    }
    return java.util.Optional.empty();
}
~~~

Đây là phương thức Java hợp lệ. Caller có thể xử lý nhánh `Optional.empty()` như một kết quả dự kiến, thay vì tạo `ItemNotFoundError` hoặc `ItemNotFoundThrowable`.

Tương tự, thanh toán bị từ chối có thể được biểu diễn bằng `enum` hoặc `record` chứa trạng thái `DECLINED`. Chỉ nên dùng ngoại lệ khi hợp đồng thực sự coi tình huống là thất bại cần lan truyền, thay vì một kết quả mà caller luôn phải xét.

Bảng quyết định:

| Tình huống | Phương án thường hợp lý |
| --- | --- |
| Lỗi tham số đơn giản | Exception chuẩn như `IllegalArgumentException` |
| Lỗi trạng thái miền cần bắt riêng | Custom `RuntimeException` hoặc subtype phù hợp |
| Lỗi recoverable theo hợp đồng compile-time | Custom checked `Exception` |
| Lỗi I/O ở tầng thấp | `IOException` hoặc subclass |
| Không tìm thấy dữ liệu là kết quả dự kiến | `Optional`/result type |
| Lỗi VM hoặc sự cố rất đặc biệt ở tầng thấp | Các lớp `Error` có ý nghĩa phù hợp; không biến thành lỗi nghiệp vụ |
| Muốn “bắt hết và xử lý tiếp” | Không mặc định `catch (Throwable)`; thiết kế biên xử lý đúng loại |

## 11. Ví dụ Java 17+ chạy độc lập: so sánh bốn nhóm lỗi

Chương trình sau chứa:

1. **Checked custom exception đúng hướng** kế thừa `Exception`.
2. **Unchecked custom exception đúng hướng** kế thừa `IllegalStateException`.
3. **Direct subclass của `Throwable`** để thấy nó là checked nhưng không thuộc `Exception`.
4. **`AssertionError` chuẩn** để thấy nhánh `Error` không đi qua `catch (Exception)`. Đây là minh họa kiểm tra kiểu, không phải mẫu xử lý `Error` cho production.

Tạo tệp **`ThrowableInheritanceDemo.java`**:

~~~java
import java.io.IOException;

public class ThrowableInheritanceDemo {

    static final class ConfigurationFailure extends Exception {
        ConfigurationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class OrderStateFailure
            extends IllegalStateException {
        OrderStateFailure(String message) {
            super(message);
        }
    }

    // Phản ví dụ về thiết kế: chỉ dùng để so sánh phân cấp.
    static final class DirectThrowableFailure extends Throwable {
        DirectThrowableFailure(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        System.out.println("== checked ==");
        try {
            initializeConfig(true);
            System.out.println("configuration=ready");
        } catch (ConfigurationFailure e) {
            System.out.println("unexpected configuration failure");
        }

        try {
            initializeConfig(false);
        } catch (ConfigurationFailure e) {
            System.out.println(
                    "configuration=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
            System.out.println(
                    "cause=" + e.getCause().getClass().getSimpleName()
                            + ": " + e.getCause().getMessage());
        }

        System.out.println("== unchecked ==");
        try {
            verifyOrder(false);
        } catch (IllegalStateException e) {
            System.out.println(
                    "state=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }

        System.out.println("== direct Throwable ==");
        try {
            throwDirect();
        } catch (DirectThrowableFailure e) {
            System.out.println(
                    "direct=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }

        System.out.println("== Error hierarchy ==");
        try {
            throw new AssertionError("illustrative assertion");
        } catch (Exception e) {
            System.out.println("caught by Exception");
        } catch (AssertionError e) {
            System.out.println(
                    "assertion=" + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
        }

        System.out.println("app=completed");
    }

    static void initializeConfig(boolean available)
            throws ConfigurationFailure {
        try {
            readConfig(available);
        } catch (IOException cause) {
            throw new ConfigurationFailure(
                    "cannot initialize config", cause);
        }
    }

    static void readConfig(boolean available) throws IOException {
        if (!available) {
            throw new IOException("disk inaccessible");
        }
    }

    static void verifyOrder(boolean paid) {
        if (!paid) {
            throw new OrderStateFailure(
                    "cannot ship before payment");
        }
    }

    static void throwDirect() throws DirectThrowableFailure {
        throw new DirectThrowableFailure("direct root");
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac ThrowableInheritanceDemo.java
java ThrowableInheritanceDemo
~~~

**Stdout dự kiến chính xác:**

~~~text
== checked ==
configuration=ready
configuration=ConfigurationFailure: cannot initialize config
cause=IOException: disk inaccessible
== unchecked ==
state=OrderStateFailure: cannot ship before payment
== direct Throwable ==
direct=DirectThrowableFailure: direct root
== Error hierarchy ==
assertion=AssertionError: illustrative assertion
app=completed
~~~

### Giải thích nhóm checked

`initializeConfig(true)` hoàn tất bình thường. Khi `available=false`, `readConfig` ném `IOException("disk inaccessible")`. Tầng cấu hình bọc nó thành `ConfigurationFailure` và giữ chính `IOException` trong `getCause()`. `main` bắt loại checked được khai báo; không cần `catch(Throwable)`.

### Giải thích nhóm unchecked

`OrderStateFailure extends IllegalStateException`, và `IllegalStateException extends RuntimeException`, nên ngoại lệ này là unchecked. Caller có thể `catch(IllegalStateException)` để xử lý trạng thái không cho phép thao tác. Đây là ví dụ hợp lý nếu lỗi trạng thái đơn hàng có ý nghĩa riêng trong API.

### Giải thích nhóm direct Throwable

`DirectThrowableFailure extends Throwable` không nằm dưới `Exception` hoặc `Error` nên là checked. `throwDirect()` phải khai báo `throws DirectThrowableFailure`. `main` dùng handler hẹp `catch(DirectThrowableFailure e)`. Nếu chỉ có `catch(Exception e)`, loại lỗi này **không được bắt**.

### Giải thích nhóm Error

`AssertionError` thuộc nhánh `Error`, không thuộc `Exception`. Vì thế trong khối `try` cuối, `catch (Exception e)` không chạy; `catch (AssertionError e)` chạy. Đây là **ví dụ kiểm tra đặc tính của cây kế thừa**, không phải khuyến nghị bắt và bỏ qua những `Error` không kiểm soát được.

Chương trình không cố tình gây `OutOfMemoryError`, `StackOverflowError` hay dừng JVM. Tạo các sự cố đó để “thử nghiệm” không cần thiết và có thể làm môi trường chạy không ổn định.

## 12. Mã phản ví dụ: bắt `Exception` không thể thay nghĩa của subclass `Throwable`

Xét:

~~~java
static void demonstrate() throws DirectThrowableFailure {
    try {
        throwDirect();
    } catch (Exception e) {
        System.out.println("not reached for DirectThrowableFailure");
    }
}
~~~

Đây là phương thức có thể đặt trong cùng lớp `ThrowableInheritanceDemo`.

**Tại sao cần `throws DirectThrowableFailure`?** Vì handler `catch(Exception)` không nhận subtype trực tiếp của `Throwable`. Lời gọi vẫn có khả năng truyền checked `DirectThrowableFailure` ra ngoài.

Nếu bỏ `throws DirectThrowableFailure` mà không thêm handler tương ứng, sẽ có **lỗi biên dịch** vì ngoại lệ checked chưa được bắt hoặc khai báo.

Sửa theo đúng mục tiêu nghiệp vụ không phải lúc nào cũng là thêm `catch(Throwable)`. Thường giải pháp tốt hơn là **thiết kế lại loại lỗi riêng để kế thừa `Exception`** ngay từ đầu, nếu nó thực sự là ngoại lệ checked của ứng dụng.

## 13. Có thể kế thừa một exception chuẩn chuyên biệt không?

Có, nếu điều kiện lỗi giữ **quan hệ “is-a” có ý nghĩa** với lớp cha.

Ví dụ:

~~~java
public final class InsufficientOrderStateException
        extends IllegalStateException {
    public InsufficientOrderStateException(String message) {
        super(message);
    }
}
~~~

Đây là lớp Java độc lập nếu lưu trong `InsufficientOrderStateException.java`.

Khi đó:

- `catch (InsufficientOrderStateException e)` xử lý riêng.
- `catch (IllegalStateException e)` cũng có thể nhận vì quan hệ subtype.
- `catch (RuntimeException e)` là handler rộng hơn, nhưng không nên dùng tùy tiện.

Ngược lại, một lỗi kết nối mạng không trở thành `IllegalArgumentException` chỉ vì thư viện muốn đưa nó vào nhánh unchecked. Hãy chọn superclass đúng ý nghĩa, không chỉ đúng đặc tính compile-time.

## 14. Cause và constructor của custom exception

Lựa chọn lớp cha đúng chỉ là bước đầu. Khi bọc một ngoại lệ tầng thấp, custom exception phải **bảo toàn cause** bằng constructor phù hợp.

~~~java
public final class RepositoryUnavailableException
        extends Exception {

    public RepositoryUnavailableException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là một lớp độc lập. Caller có thể tạo:

~~~java
throw new RepositoryUnavailableException(
        "Repository is unavailable", sqlException);
~~~

Đây là đoạn minh họa có biến `sqlException` và cần xử lý checked exception theo hợp đồng. Constructor `super(message, cause)` giúp `getCause()` của wrapper giữ tham chiếu lỗi gốc.

**Không viết chỉ:**

~~~java
super(message + ": " + cause.getMessage());
~~~

trong constructor của lớp lỗi nếu mục tiêu là giữ cause. Nối message không làm `getCause()` của wrapper có nguyên nhân.

### Constructor không tự được kế thừa

Nếu `Exception` cung cấp constructor `(String, Throwable)`, lớp `RepositoryUnavailableException` vẫn **phải khai báo** overload tương ứng nếu muốn caller gọi được. Java không kế thừa constructor như phương thức instance.

### Đừng tắt stack trace/suppression theo mặc định

Một số lớp gốc như `Exception` và `RuntimeException` có constructor `protected` cho phép điều khiển `enableSuppression` và `writableStackTrace`. Không nên tắt hai tính năng đó chỉ để cải thiện hiệu năng giả định: nó có thể làm mất dữ liệu chẩn đoán hữu ích. Hãy đo đạc và xác định chính sách rõ ràng trước khi dùng cấu hình đặc biệt.

## 15. `Error` không đồng nghĩa “lỗi nghiêm trọng nhất cần catch”

Nhiều người mới học nghĩ rằng lỗi nặng thì kế thừa `Error`, còn lỗi nhẹ thì `Exception`. Java không dùng tiêu chí đơn giản như vậy.

Ví dụ:

- Không khởi động được một hệ thống nghiệp vụ do thiếu cấu hình có thể là sự cố **rất nghiêm trọng** đối với hoạt động, nhưng vẫn hoàn toàn có thể biểu diễn bằng một `Exception` hoặc `RuntimeException` thích hợp.
- Một `AssertionError` do test chủ động ném có thể mô tả một bất biến sai trong môi trường kiểm thử, không phải toàn bộ JVM đã hỏng.
- Một `IOException` do mất kết nối có thể đòi hỏi chính sách retry/khôi phục, không phải subclass của `Error`.
- Một `OutOfMemoryError` có thể làm chương trình không còn đủ tài nguyên để tiếp tục an toàn, nên việc catch rồi chạy tiếp là rất rủi ro.

Chọn superclass dựa vào **bản chất hợp đồng và cách phục hồi dự kiến**, không dựa vào việc người dùng có cảm thấy lỗi “nặng” hay không.

## 16. Đừng tạo một `BaseApplicationThrowable`

Một kiến trúc đôi khi bắt đầu bằng:

~~~java
public class BaseApplicationThrowable extends Throwable {
    public BaseApplicationThrowable(String message) {
        super(message);
    }
}
~~~

Đây là **phản ví dụ thiết kế** nếu định dùng cho tất cả ngoại lệ nghiệp vụ. Nó khiến mọi subclass checked của hệ thống nằm ngoài `Exception`, dẫn đến cùng vấn đề về `catch(Exception)` và chữ ký `throws`.

Nếu dự án thực sự cần một base exception để nhóm nhiều loại lỗi:

~~~java
public class ApplicationException extends Exception {
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

hoặc một base unchecked:

~~~java
public class ApplicationRuntimeException extends RuntimeException {
    public ApplicationRuntimeException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Các đoạn là hai **lớp minh họa độc lập**, không phải bắt buộc phải tạo cả hai. Việc có một superclass chung chỉ hữu ích khi có một **hợp đồng chung thực sự**; không cần xây phân cấp sâu chỉ để thỏa một sơ đồ OOP.

## 17. Khi nào nên bắt `Throwable` ở ranh giới đặc biệt?

Một số môi trường hệ thống muốn ghi nhận thất bại của một đơn vị thực thi, ngay cả khi lỗi không nằm trong nhánh `Exception`. Khi đó có thể thấy:

~~~java
try {
    executePlugin();
} catch (Throwable failure) {
    recordFailure(failure);
    throw failure;
}
~~~

Đây là **mảnh mã minh họa**, trong đó `executePlugin` và `recordFailure` do hệ thống cung cấp; phương thức bao quanh phải đáp ứng quy tắc checked exception và kiểu được ném lại.

Đặc điểm quan trọng là handler **ghi nhận rồi ném tiếp**, không ngầm tuyên bố mọi `Error` đều có thể phục hồi.

Trong thực tế, nếu cần xử lý cả `Error` và `Exception` ở một ranh giới framework, phải có phân tích cụ thể về:

- Tài nguyên còn có thể hoạt động an toàn hay không.
- Mức độ thu hồi trạng thái sau `OutOfMemoryError` hoặc `LinkageError`.
- Cách tránh vòng lặp ghi log hoặc lỗi thứ cấp do thiếu bộ nhớ.
- Việc báo cáo lỗi một lần và quyết định dừng/tái khởi động ở đâu.
- Hợp đồng của thư viện/framework trong môi trường thực tế.

**Đây là trường hợp đặc biệt, không phải lý do để tạo custom `Error` cho nghiệp vụ.**

## 18. Khác biệt với `finally` và try-with-resources

Một số người chọn `Error` vì muốn làm cho lỗi “thoát nhanh” qua các handler. Nhưng `Error` **không tự bỏ qua các khối `finally`** khi Java rời cấu trúc `try` theo luồng ngoại lệ bình thường.

Ví dụ:

~~~java
try {
    throw new AssertionError("assertion failed");
} finally {
    System.out.println("cleanup");
}
~~~

Đây là đoạn minh họa. `finally` vẫn được thực thi trước khi `AssertionError` tiếp tục truyền ra ngoài, miễn là luồng Java còn thực hiện được quá trình rời `try`.

Tuy nhiên, những sự cố làm JVM/tiến trình không thể tiếp tục (kill cưỡng bức, `Runtime.halt`, crash, mất điện) không được bảo đảm có cơ hội dọn dẹp. Sự khác nhau ở đây là **cách JVM/tiến trình kết thúc**, không phải cứ là subtype `Error` thì “bỏ qua finally”.

Try-with-resources cũng hoạt động khi mã Java có cơ hội rời scope; nó không trở thành cơ chế chống mất dữ liệu do VM dừng cưỡng bức.

## 19. Cách thiết kế API không buộc caller bắt nhầm `Error`

Một API tốt phân định:

1. **Lỗi mà caller cần xử lý theo hợp đồng**.
2. **Lỗi lập trình hoặc vi phạm trạng thái**.
3. **Sự cố môi trường nghiêm trọng** mà ứng dụng không tự giải quyết ở mỗi lời gọi.
4. **Kết quả nghiệp vụ dự kiến**.

Ví dụ API đọc cấu hình có thể chọn:

~~~java
public Configuration read(String path)
        throws ConfigurationLoadException
~~~

Đây là chữ ký minh họa. Nó cho caller biết một checked failure có ý nghĩa rõ: đọc cấu hình có thể thất bại.

Một API thực thi thao tác trạng thái có thể chọn unchecked `IllegalStateException` hay một subtype cụ thể để báo bất biến bị vi phạm.

Một API tra cứu không tìm thấy dữ liệu có thể trả `Optional.empty()` thay vì ném exception.

Không cần quảng bá `throws Throwable` hoặc cho caller bắt `Error` chỉ để “bao trùm mọi tình huống”. API càng đúng phạm vi, caller càng dễ viết mã hợp lệ.

## 20. Câu hỏi cần trả lời trước khi chọn superclass

| Câu hỏi | Hướng suy nghĩ |
| --- | --- |
| Caller có được yêu cầu xử lý/khai báo lỗi lúc biên dịch không? | Chọn checked `Exception` hoặc subtype phù hợp |
| Lỗi là vi phạm trạng thái/tiền điều kiện? | `RuntimeException` hoặc subclass JDK phù hợp |
| Có cần type riêng để phân biệt hành động? | Custom subtype trong nhánh đúng |
| Đây là kết quả dự kiến thay vì ngoại lệ? | Xem `Optional`, enum, record hoặc result type |
| Có đang định dùng `Throwable` để tránh chọn nhánh? | Dừng và xác định hợp đồng chính xác |
| Có đang định dùng `Error` vì không thích `throws`? | Dùng unchecked `RuntimeException` nếu ngữ nghĩa phù hợp |
| Có thật sự là cơ chế VM/framework đặc biệt không? | Chỉ cân nhắc nhánh đặc biệt khi hiểu đầy đủ hậu quả |
| Có cần giữ nguyên nhân tầng thấp không? | Constructor `(String, Throwable)` với `super(message, cause)` |

Nếu bạn không thể nêu rõ một lợi ích cụ thể của việc trực tiếp kế thừa `Throwable` hoặc `Error`, đó là tín hiệu mạnh rằng chúng **không phù hợp**.

## 21. Những hiểu lầm và lỗi thường gặp

### 21.1. “`Throwable` là lớp cha của tất cả lỗi nên kế thừa trực tiếp sẽ linh hoạt hơn”

Đúng là `Throwable` là gốc, nhưng trực tiếp kế thừa nó khiến custom type nằm ngoài `Exception`, trở thành checked và không được `catch(Exception)` nhận. Đó thường là **mất tương thích về kỳ vọng**, không phải tăng linh hoạt.

### 21.2. “Một lỗi nặng phải kế thừa `Error`”

Sai. Mức độ ảnh hưởng kinh doanh không phải tiêu chí phân nhánh trong cây `Throwable`. Lỗi cấu hình khiến dịch vụ không khởi động vẫn có thể là custom `Exception`.

### 21.3. “`Error` luôn là lỗi do JVM tự ném”

Không. Code có thể tự `throw new AssertionError(...)`, và cũng có thể định nghĩa subtype `Error`. Vấn đề nằm ở ý nghĩa và cách hệ thống xử lý, không phải cú pháp.

### 21.4. “`Error` không bao giờ được bắt”

Không nên diễn giải tuyệt đối. Có trường hợp kiểm thử hoặc ranh giới hệ thống đặc biệt bắt một loại `Error` cụ thể. Tuy nhiên, **bắt mọi `Error` rồi tiếp tục nghiệp vụ như bình thường là thực hành nguy hiểm**.

### 21.5. “`catch(Exception)` bắt mọi `Throwable`”

Sai. Nó không bắt `Error` và không bắt subtype trực tiếp khác của `Throwable` nằm ngoài `Exception`.

### 21.6. “`catch(Throwable)` luôn tốt hơn vì tổng quát”

Không. Nó có thể gom các lỗi mà ứng dụng không có cách khôi phục an toàn. Dùng handler hẹp nhất phù hợp trách nhiệm của tầng.

### 21.7. “Nếu kế thừa `RuntimeException` thì lỗi sẽ không làm chương trình dừng”

Sai. Unchecked vẫn được ném, truyền và có thể làm tác vụ kết thúc bất thường. Unchecked chỉ không buộc `catch`/`throws` theo luật checked.

### 21.8. “`extends Throwable` thì là unchecked vì Throwable ở cấp cao nhất”

Sai. Những subtype của `Throwable` không nằm dưới `RuntimeException` hay `Error` là checked.

### 21.9. “`throws Throwable` mô tả API tốt hơn `throws IOException`”

Thường không. Một chữ ký quá rộng làm caller khó biết lỗi nào thực sự là hợp đồng có thể phục hồi.

### 21.10. “Một custom exception bắt buộc phải kế thừa trực tiếp `Exception`”

Không. Có thể kế thừa `RuntimeException` hoặc subclass như `IllegalStateException`, hoặc một lớp checked chuyên biệt phù hợp. Quan trọng là superclass đúng ngữ nghĩa.

### 21.11. “Cứ đổi lớp cha thành `Error` để khỏi thêm `throws`”

Sai định hướng. Nếu muốn unchecked vì hợp đồng, cân nhắc `RuntimeException`. `Error` mang ý nghĩa khác.

### 21.12. “Tên lớp có chữ Error sẽ khiến nó thuộc nhánh `Error`”

Không. Phân loại phụ thuộc `extends`, không phải tên. Dù vậy, đặt tên `SomethingError` cho loại kế thừa `RuntimeException` có thể vẫn gây hiểu lầm, nên đặt tên phản ánh vai trò.

### 21.13. “Lỗi trong `finally` chỉ xảy ra với `Exception`”

Không. Một `Throwable` bất kỳ có thể truyền qua cấu trúc `try/finally`; luật thực thi `finally` không phụ thuộc tên lớp ngoại lệ.

### 21.14. “Kế thừa `Error` là cách biểu diễn business rejection”

Không. Một quyết định từ chối hợp lệ nên được thiết kế bằng result/enum nếu phù hợp; nếu là lỗi nghiệp vụ bất thường thì dùng exception đúng nhánh.

### 21.15. “Constructor của lớp cha tự được kế thừa”

Không. Khi custom exception cần message/cause, phải khai báo constructor và gọi `super(...)` phù hợp.

### 21.16. “Bọc lỗi bằng một message mới đã giữ nguyên cause”

Không. Chỉ sao chép `cause.getMessage()` không tạo liên kết cause. Truyền đối tượng gốc tới constructor hoặc cơ chế thiết lập nguyên nhân hợp lệ.

## 22. Tự kiểm tra kiến thức

1. `Throwable` khác `Exception` và `Error` trong cây kế thừa như thế nào?
2. `class X extends Throwable` là checked hay unchecked? Vì sao?
3. `class Y extends Error` là checked hay unchecked?
4. Một `catch (Exception e)` có bắt được `DirectThrowableFailure extends Throwable` không?
5. Một `catch (Exception e)` có bắt được `AssertionError` không?
6. Vì sao `throws Throwable` thường là chữ ký quá rộng cho API nghiệp vụ?
7. Khi nào nên chọn `extends Exception` cho lớp lỗi riêng?
8. Khi nào nên chọn `extends RuntimeException`?
9. Vì sao không nên chọn `extends Error` chỉ để tránh `throws`?
10. Một sự cố cấu hình rất nghiêm trọng có nhất thiết là `Error` không?
11. Nếu lỗi trạng thái đơn hàng có nghĩa miền riêng, bạn có thể kế thừa lớp chuẩn nào?
12. Nếu người dùng nhập tham số sai đơn giản, có nhất thiết phải tạo custom exception không?
13. Có thể dùng `Optional.empty()` cho trạng thái không tìm thấy dự kiến không?
14. Chọn `Error` cho lỗi nghiệp vụ có thể khiến handler nào bỏ sót nó?
15. `throw new AssertionError("x")` có làm `finally` bị bỏ qua theo luồng ngoại lệ thông thường không?
16. Một custom checked exception có cần khai báo trong `throws` khi lan ra caller không?
17. Nếu custom unchecked exception không bị bắt, có thể xảy ra gì?
18. `getCause()` giúp gì khi chuyển lỗi hạ tầng thành lỗi nghiệp vụ?
19. Khi nào một `catch(Throwable)` đặc biệt có thể tồn tại hợp lý?
20. Khi bắt lỗi `Error` ở một ranh giới đặc biệt, vì sao không nên mặc định tiếp tục công việc?
21. Tại sao subtype của `IllegalStateException` có thể phù hợp hơn subtype trực tiếp của `RuntimeException` trong một số trường hợp?
22. Hai loại lỗi khác nhau về thông điệp có bắt buộc cần hai subclass không?
23. Nếu thư viện định nghĩa `BaseApplicationThrowable extends Throwable` cho tất cả lỗi, vấn đề compile-time là gì?
24. Constructor `(String, Throwable)` trong custom exception nên gọi gì?
25. Có nên đặt logic nghiệp vụ trong message exception để caller parse không?

### Gợi ý đáp án

1. `Throwable` là lớp gốc; `Exception` và `Error` là hai nhánh con trực tiếp quan trọng.
2. Checked, vì không nằm dưới `RuntimeException` hay `Error`.
3. Unchecked.
4. Không.
5. Không.
6. Nó bắt caller xét nhóm rất rộng, kể cả những loại không phù hợp với hợp đồng ứng dụng.
7. Khi caller được kỳ vọng bắt hoặc khai báo lỗi có thể xử lý theo hợp đồng.
8. Khi dùng unchecked exception đúng ý nghĩa và không muốn buộc checked handling ở mỗi tầng.
9. `Error` có ý nghĩa đặc biệt; chọn `RuntimeException` nếu thực sự cần unchecked cho nghiệp vụ.
10. Không; mức độ nghiêm trọng không đồng nghĩa nhánh `Error`.
11. Ví dụ `IllegalStateException`.
12. Không; `IllegalArgumentException` có thể đủ.
13. Có, nếu hợp đồng coi đó là kết quả dự kiến.
14. Ví dụ handler `catch(Exception)`.
15. Không, `finally` thông thường vẫn chạy nếu luồng Java rời `try`.
16. Có, khi không được bắt và thuộc checked exception.
17. Nó vẫn có thể truyền lên ngoài và làm tác vụ thất bại.
18. Bảo toàn nguyên nhân và stack trace gốc.
19. Trong một số ranh giới framework/VM cần ghi nhận rồi xử lý theo chính sách đặc biệt.
20. Vì có thể thiếu tài nguyên hoặc hệ thống đã ở trạng thái không an toàn.
21. Vì nó giữ đúng ý nghĩa “trạng thái không phù hợp” đồng thời cho phép handler rộng theo superclass.
22. Không, nếu caller không xử lý khác và loại chuẩn cùng message đã đủ.
23. Các subtype vẫn là checked nhưng không thuộc `Exception`; `catch(Exception)` không bao phủ.
24. `super(message, cause)` khi superclass hỗ trợ.
25. Không; dùng kiểu lỗi hoặc mã lỗi có cấu trúc.

## 23. Tổng kết

**Kế thừa trực tiếp `Throwable` hoặc `Error` không bị cấm tuyệt đối, nhưng thường sai mục đích trong custom exception của ứng dụng.** Subtype trực tiếp của `Throwable` là checked nhưng không nằm trong nhánh `Exception`, dễ gây bất ngờ cho caller và handler. Subtype `Error` là unchecked nhưng mang ngữ nghĩa lỗi đặc biệt, không phải đường tắt thay cho `RuntimeException` khi muốn tránh `throws`.

Hãy chọn **`Exception` cho custom checked exception**, **`RuntimeException` hoặc subclass chuyên biệt phù hợp cho custom unchecked exception**, và **các exception có sẵn hoặc kiểu kết quả** khi chúng đã diễn đạt đủ. Một lớp ngoại lệ tốt phải giúp caller hiểu, phân biệt hoặc xử lý tình huống đúng hơn — không chỉ làm cây kế thừa phức tạp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
