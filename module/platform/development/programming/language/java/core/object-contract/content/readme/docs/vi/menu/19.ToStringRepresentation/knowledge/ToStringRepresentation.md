<a id="back-to-top"></a>

# Biểu diễn toString

## Menu
- [Vai trò của toString() trong quan sát và chẩn đoán](#to-string-representation-001)
- [Dạng mặc định tên lớp, ký hiệu @ và hash code dạng thập lục phân](#to-string-representation-002)
- [Vì sao phần sau @ không được hiểu là địa chỉ bộ nhớ](#to-string-representation-003)
- [Thiết kế chuỗi ngắn gọn, có tên trường và đủ thông tin](#to-string-representation-004)
- [Không đưa mật khẩu, token hoặc dữ liệu nhạy cảm vào biểu diễn](#to-string-representation-005)
- [Phân biệt toString() với tuần tự hóa và định dạng trao đổi ổn định](#to-string-representation-006)
- [Không dùng toString() để quyết định equals() hoặc hashCode()](#to-string-representation-007)
- [Tránh đệ quy vô hạn trong quan hệ tham chiếu hai chiều](#to-string-representation-008)
- [Tránh tính toán nặng hoặc tác dụng phụ khi tạo biểu diễn](#to-string-representation-009)
- [Arrays.toString(), deepToString() và biểu diễn thành phần lồng](#to-string-representation-010)

## <a id="to-string-representation-001">Vai trò của toString() trong quan sát và chẩn đoán</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `toString()` trong quan sát và chẩn đoán đối tượng Java

## 1. `toString()` giúp quan sát điều gì?

Khi chương trình chạy, người phát triển cần biết **đối tượng đang đại diện cho cái gì, trạng thái đáng chú ý ra sao, và dữ liệu nào giúp liên hệ một hiện tượng với yêu cầu hoặc bản ghi cụ thể**. Phương thức `toString()` là một điểm mở rộng chuẩn của Java để cung cấp **biểu diễn văn bản dễ đọc** cho một đối tượng.

`toString()` đặc biệt hữu ích trong:

- **Gỡ lỗi:** quan sát đối tượng tại breakpoint, cửa sổ biến của IDE, hoặc các dòng in tạm trong quá trình điều tra.
- **Ghi log:** đưa một số trường có ý nghĩa vào log để liên kết lỗi với mã đơn hàng, trạng thái xử lý, số lần thử lại hoặc thời điểm nghiệp vụ.
- **Chẩn đoán sự cố:** đối chiếu trạng thái trước và sau thao tác, phát hiện giá trị bị sai, dữ liệu trống hoặc đối tượng đi nhầm luồng xử lý.
- **Quan sát collection:** khi in một `List`, `Set` hoặc `Map`, biểu diễn của các phần tử thường dựa vào `toString()`, nên một triển khai có ích giúp đọc kết quả dễ hơn.
- **Thông báo nội bộ và kiểm tra thủ công:** nhanh chóng hiểu đối tượng mà không phải tự in từng getter.

Ví dụ, với một yêu cầu xử lý thanh toán, biểu diễn:

```text
Payment{id='PAY-42', status='PENDING', attempts=2}
```

có giá trị chẩn đoán hơn dạng mặc định như:

```text
com.example.Payment@4e25154f
```

Chuỗi hex ở ví dụ thứ hai chỉ mang tính minh họa, không phải địa chỉ RAM hay kết quả cố định của chương trình. Dữ liệu trạng thái hữu ích cần được chọn **theo mục đích quan sát**, tránh lộ mã xác thực, thông tin cá nhân hoặc các trường rất dài.

**Phạm vi quan trọng:** `toString()` là một biểu diễn thuận tiện cho con người, **không phải** giao thức serialization, JSON contract, khóa định danh hay bằng chứng hai object bằng nhau. Những nhiệm vụ đó thuộc API, mô hình dữ liệu, `equals()`/`hashCode()` hoặc serializer chuyên dụng.

## 2. `Object.toString()` mặc định hoạt động thế nào?

Mọi lớp Java thông thường đều kế thừa `toString()` từ `java.lang.Object` trừ khi tự ghi đè. Hình thức kết quả mặc định của `Object.toString()` tương đương:

```java
getClass().getName() + "@" + Integer.toHexString(hashCode())
```

Trong biểu thức này:

1. `getClass().getName()` trả tên lớp runtime (kể cả package, và với lớp lồng nhau có thể xuất hiện ký hiệu `$`).
2. `hashCode()` được gọi trên đối tượng hiện tại; **nếu lớp đã ghi đè `hashCode()`, giá trị đó cũng được dùng**.
3. `Integer.toHexString(...)` biến số hash thành chuỗi hex.

Điều này lý giải vì sao nhìn thấy `TênLớp@...` thường chỉ cho biết loại đối tượng và một mã hash, không hiển thị trường nghiệp vụ.

Các hiểu nhầm cần tránh:

- **Phần sau `@` không mặc nhiên là địa chỉ bộ nhớ.** Đặc tả `Object.toString()` nói tới hash dạng hex, không hứa vị trí RAM; JVM có thể di chuyển đối tượng trong quá trình quản lý bộ nhớ.
- **Hash không bảo đảm duy nhất.** Hai object khác nhau có thể có cùng hash do va chạm, và mã hash không thay thế `==` khi cần kiểm tra cùng tham chiếu.
- **Chuỗi mặc định không ổn định xuyên lần chạy.** Không lưu hoặc phân tích nó như ID cố định trong database, sự kiện hay API.
- **Đừng suy diễn ý nghĩa từ hex.** Ngay cả khi chuỗi trông giống một giá trị định danh, nó không chứng minh trạng thái nghiệp vụ, phiên bản dữ liệu hay tính toàn vẹn của đối tượng.

Nếu cần gắn sự kiện log với một thực thể, hãy dùng **mã nghiệp vụ rõ ràng** như `orderId` hoặc `paymentId`, kèm trace/request ID theo cơ chế logging phù hợp.

## 3. Ghi đè `toString()`: chọn thông tin phù hợp cho chẩn đoán

Chữ ký phương thức chuẩn:

```java
@Override
public String toString() {
    return "Type{field=value}";
}
```

Đây là mảnh code minh họa bên trong một lớp. Khi override đúng, phương thức phải `public`, không nhận tham số và trả một `String`. Annotation `@Override` giúp trình biên dịch phát hiện lỗi chữ ký.

Ví dụ với một thanh toán:

```java
public final class Payment {
    private final String id;
    private final String status;
    private final int attempts;
    private final String accessToken;

    public Payment(String id, String status, int attempts, String accessToken) {
        this.id = id;
        this.status = status;
        this.attempts = attempts;
        this.accessToken = accessToken;
    }

    @Override
    public String toString() {
        return "Payment{id='" + id
            + "', status='" + status
            + "', attempts=" + attempts + "}";
    }
}
```

Đây là lớp có thể biên dịch nếu lưu vào `Payment.java`, nhưng chưa có `main` để tự chạy. Nó minh họa việc chọn trường: `id` hỗ trợ liên hệ với bản ghi; `status` và `attempts` cho biết trạng thái; `accessToken` **không** xuất hiện trong kết quả vì chứa thông tin nhạy cảm.

Không có nghĩa cứ trường `private` là phải đưa vào `toString()`. Chọn một tập trường **ngắn, có ý nghĩa, đủ để giải thích sự kiện**. Ví dụ không nên in toàn bộ payload HTTP, binary data, token đăng nhập, thẻ thanh toán, email cá nhân hoặc danh sách hàng triệu phần tử chỉ vì chúng có trong đối tượng.

### 3.1. Dùng `StringBuilder` khi có nhiều trường tùy chọn

Với biểu diễn phức tạp, `StringBuilder` giúp ghép các phần dễ đọc và có điều kiện. Với vài trường, nối chuỗi bằng `+` như ví dụ trên là hoàn toàn hợp lý, vì trình biên dịch và runtime Java đã có cơ chế tối ưu phù hợp cho các biểu thức nối chuỗi thông thường.

`String.format(...)` cũng có thể tạo kết quả rõ ràng, nhưng nếu `toString()` được gọi với tần suất cao, định dạng phức tạp có thể làm tăng chi phí. Hãy đo lường khi tối ưu, không thay cách viết rõ ràng bằng kỹ thuật thủ công chỉ vì giả định hiệu năng.

### 3.2. Kết quả cần dễ đọc và đủ nhất quán

Một số thói quen hữu ích:

- Mở đầu bằng tên loại đối tượng dễ nhận biết, ví dụ `Payment{...}` hoặc `JobState{...}`.
- Ghi tên field trước giá trị: `attempts=2` dễ hiểu hơn một danh sách chỉ gồm số.
- Giữ thứ tự trường nhất quán giữa các lần gọi.
- Với field có thể `null`, cho ra giá trị rõ ràng nếu field đó hữu ích để chẩn đoán.
- Chỉ hiển thị một phần hoặc số lượng phần tử nếu collection rất lớn.
- Nếu có mã định danh ổn định, ưu tiên in nó hơn thông tin tham chiếu/identity hash.
- Không biến văn bản `toString()` thành một định dạng máy móc mà bên khác phải parse để vận hành hệ thống.

Nếu muốn log có cấu trúc thật sự, dùng API ghi log có trường riêng (ví dụ payment ID, event name, status, trace ID), thay vì trích xuất dữ liệu bằng regex từ `toString()`.

## 4. Khi nào Java tự gọi `toString()`?

Có nhiều con đường khiến biểu diễn của đối tượng xuất hiện mà bạn không gọi `obj.toString()` bằng tay.

### 4.1. `System.out.println(obj)`

Khi in một tham chiếu kiểu `Object`, `PrintStream.println(Object)` chuyển giá trị thành chuỗi theo cơ chế `String.valueOf(Object)`. Đối với đối tượng khác `null`, thao tác này gọi `toString()`; với tham chiếu `null`, văn bản nhận được là `"null"`.

```java
Object value = new Object();
System.out.println(value); // Dạng tên lớp @ hex hash (giá trị cụ thể tùy lần chạy).
System.out.println((Object) null); // null
```

Đây là mảnh code minh họa; dòng đầu có kết quả hash không xác định trước. Không hardcode stdout của một object chưa ghi đè `toString()` vào bài kiểm thử.

### 4.2. Ghép chuỗi

`"item=" + obj` chuyển đối tượng thành biểu diễn chuỗi khi đánh giá biểu thức nối chuỗi. Vì thế:

```java
String message = "payment=" + payment;
```

cũng sử dụng `toString()` của `payment` khi biến không `null`. Cần nhớ thời điểm đánh giá: phép nối chuỗi được thực hiện khi dựng `message`, kể cả khi log về sau bị bỏ qua. Trong luồng logging hiệu năng cao, nên dùng cơ chế message/parameterized logging phù hợp thư viện để tránh dựng thông điệp không cần thiết; khả năng trì hoãn chuyển chuỗi phụ thuộc API/logging framework.

### 4.3. `String.valueOf(...)` và `Objects.toString(...)`

`String.valueOf((Object) obj)` an toàn với `null` và tạo `"null"`; `Objects.toString(obj, "n/a")` cho phép cung cấp giá trị mặc định khi `obj == null`. Không nên viết `obj.toString()` khi `obj` có thể `null` mà không có kiểm tra, vì sẽ ném `NullPointerException`.

### 4.4. Collection và mảng

Các implementation quen thuộc của `List`, `Set` và `Map` có `toString()` thể hiện các phần tử/entry; đối tượng phần tử sẽ được chuyển sang chuỗi khi biểu diễn collection. Nếu phần tử có `toString()` rối, vô ích hoặc làm lộ dữ liệu, in collection cũng có vấn đề tương tự.

**Mảng là một trường hợp đặc biệt:** gọi `toString()` trực tiếp trên `int[]` thường chỉ cho dạng `[I@...`, vì mảng không có biểu diễn nội dung riêng do `Object.toString()` cung cấp. Dùng:

- `Arrays.toString(array)` để in phần tử của mảng một chiều.
- `Arrays.deepToString(array)` khi cần biểu diễn lồng nhau của mảng object, ví dụ `int[][]`.

Không được xem các phương thức in mảng là giải pháp serializing bền vững; chúng cung cấp biểu diễn tiện cho quan sát.

## 5. Ví dụ Java 17 có kết quả xác định

Ví dụ đầy đủ sau chỉ in các giá trị có thể dự đoán, đồng thời chỉ ra các giới hạn của `toString()`: hai đối tượng có cùng text vẫn không nhất thiết cùng tham chiếu hoặc bằng nhau theo `equals()`; biểu diễn cũng có thể bỏ qua trường bí mật.

Lưu tệp **`ToStringDiagnosticsDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ToStringDiagnosticsDemo {
    static final class Payment {
        private final String paymentId;
        private final String status;
        private final int attempts;
        private final String accessToken;

        Payment(String paymentId, String status,
                int attempts, String accessToken) {
            this.paymentId = Objects.requireNonNull(paymentId);
            this.status = Objects.requireNonNull(status);
            this.attempts = attempts;
            this.accessToken = Objects.requireNonNull(accessToken);
        }

        @Override
        public String toString() {
            return "Payment{id='" + paymentId
                + "', status='" + status
                + "', attempts=" + attempts + "}";
        }
    }

    record PaymentSummary(String paymentId, String status, int attempts) {}

    static final class NoOverride {}

    public static void main(String[] args) {
        Payment first = new Payment("PAY-42", "PENDING", 2, "secret-123");
        Payment second = new Payment("PAY-42", "PENDING", 2, "other-secret");

        System.out.println("Object: " + first);
        System.out.println("Same text: "
            + first.toString().equals(second.toString()));
        System.out.println("Same reference: " + (first == second));
        System.out.println("Same equals: " + first.equals(second));
        System.out.println("Secret exposed: "
            + first.toString().contains("secret-123"));

        PaymentSummary summary =
            new PaymentSummary("PAY-42", "PENDING", 2);
        System.out.println("Record: " + summary);
        System.out.println("List: " + List.of(first, second));
        System.out.println("Array: " + Arrays.toString(new int[] {2, 4}));
        System.out.println("Matrix: "
            + Arrays.deepToString(new int[][] {{1, 2}, {3, 4}}));
        System.out.println("Null: " + String.valueOf((Object) null));

        Object plain = new NoOverride();
        String defaultText = plain.toString();
        String prefix = NoOverride.class.getName() + "@";
        System.out.println("Default form matches class@: "
            + defaultText.startsWith(prefix));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp bằng **JDK 17 trở lên**:

```bash
javac ToStringDiagnosticsDemo.java
java ToStringDiagnosticsDemo
```

Kết quả chuẩn:

```text
Object: Payment{id='PAY-42', status='PENDING', attempts=2}
Same text: true
Same reference: false
Same equals: false
Secret exposed: false
Record: PaymentSummary[paymentId=PAY-42, status=PENDING, attempts=2]
List: [Payment{id='PAY-42', status='PENDING', attempts=2}, Payment{id='PAY-42', status='PENDING', attempts=2}]
Array: [2, 4]
Matrix: [[1, 2], [3, 4]]
Null: null
Default form matches class@: true
```

**Giải thích:**

1. `Payment.toString()` cho văn bản tập trung vào mã thanh toán, trạng thái và số lần thử, nên dòng `Object` phù hợp để đọc log.
2. `first` và `second` có cùng chuỗi `toString()` vì các trường được chọn giống nhau; nhưng `first == second` là `false` (hai object), và `Payment` không override `equals()` nên `first.equals(second)` cũng là `false`. **Đừng suy ra equality từ biểu diễn chuỗi.**
3. Mỗi `Payment` có token nội bộ, nhưng `toString()` không đưa token vào chuỗi; kiểm tra chuỗi không chứa `"secret-123"` trả `false` cho “Secret exposed”.
4. `record PaymentSummary` được Java sinh sẵn `toString()` theo tên record và toàn bộ component của record. Điều này tiện cho dữ liệu tóm tắt không nhạy cảm; nếu record chứa token hoặc dữ liệu cá nhân, hành vi mặc định có thể làm lộ chúng khi log.
5. `List` hiển thị mỗi phần tử thông qua biểu diễn tương ứng. Việc in toàn bộ collection có thể làm log dài bất ngờ khi danh sách lớn.
6. `Arrays.toString` và `Arrays.deepToString` in nội dung của mảng một cấp hoặc mảng lồng nhau; dùng trực tiếp `array.toString()` không đáp ứng mục tiêu đó.
7. Dòng cuối chỉ kiểm tra tiền tố định dạng mặc định của `Object.toString()`, không khẳng định một giá trị hex cụ thể.

## 6. `toString()` liên quan gì tới `equals()` và `hashCode()`?

Ba phương thức có vai trò khác nhau:

| Phương thức | Câu hỏi trả lời | Điều cần bảo đảm |
| --- | --- | --- |
| `equals(Object)` | Hai đối tượng có được coi là bằng nhau theo quan hệ equality của class không? | Phản xạ, đối xứng, bắc cầu, nhất quán, không bằng `null` |
| `hashCode()` | Mã băm phục vụ các cấu trúc băm là bao nhiêu? | Đối tượng bằng nhau theo `equals()` phải có cùng hash |
| `toString()` | Biểu diễn văn bản hữu ích cho người quan sát là gì? | Không có hợp đồng bắt buộc là duy nhất, có thể parse hoặc phản ánh tất cả field |

Những hệ quả thường bị hiểu sai:

- **Hai object có `toString()` bằng nhau không nhất thiết `equals()` bằng nhau.** Bạn có thể chủ động bỏ các trường khỏi biểu diễn chẩn đoán.
- **Hai object `equals()` bằng nhau cũng không được ngôn ngữ buộc phải có `toString()` giống nhau.** Chẳng hạn hai entity cùng ID nhưng status cập nhật ở thời điểm khác nhau vẫn có thể in hai status khác.
- **`toString()` không nên được dùng làm `hashCode()` hay khóa bản ghi.** Tên field, thứ tự field và chính sách che giấu dữ liệu có thể thay đổi theo phiên bản ứng dụng.
- **Không sửa `equals/hashCode` chỉ để khiến log trông giống nhau.** Quan hệ bằng nhau là một quyết định về miền nghiệp vụ hoặc giá trị, không phải một yêu cầu định dạng.
- **Không dựa vào chuỗi `Class@hash` để suy luận về memory identity.** Hash có thể va chạm, lớp có thể override hash, và địa chỉ bộ nhớ không được cung cấp theo định dạng đó.

Nếu cần dữ liệu để so sánh tự động trong test, kiểm tra các field hoặc `equals()` theo định nghĩa của lớp. Chỉ kiểm tra `toString()` khi định dạng biểu diễn chính là hành vi bạn muốn cam kết; nếu không, kiểm thử chính xác từng dấu câu của `toString()` thường làm test dễ vỡ khi thay đổi cách hiển thị.

## 7. Phân biệt `toString()` với logging có cấu trúc và serialization

### 7.1. Log nên chứa thông tin ngữ cảnh

Log có ích cần trả lời được “sự kiện gì, khi nào, ở đâu, liên quan yêu cầu/bản ghi nào, trạng thái nào”. `toString()` của một object chỉ là một phần dữ liệu để bổ sung ngữ cảnh.

Ví dụ một dòng log hữu ích về mặt ý nghĩa:

```text
event=payment_retry requestId=REQ-18 paymentId=PAY-42 status=PENDING attempt=2
```

Đây là văn bản minh họa, không phải output do một API log cụ thể sinh ra. Ở hệ thống lớn, việc log các trường có tên riêng giúp tìm kiếm/lọc tốt hơn so với chuỗi `Payment{...}` tự do.

Với thư viện logging hỗ trợ tham số, ưu tiên gọi theo mẫu của thư viện khi phù hợp, để tránh tạo thông điệp tốn chi phí khi mức log bị tắt. Tuy nhiên, chi tiết định dạng và thời điểm gọi `toString()` phụ thuộc thư viện; **không suy đoán rằng mọi API log đều trì hoãn việc tính chuỗi**.

### 7.2. Serialization không phải `toString()`

`toString()` có thể thay đổi hình thức chỉ để giúp người đọc log. Không dùng nó làm JSON, CSV, payload mạng, khóa cache hay dữ liệu lưu database. Một serializer chuyên dụng phải có quy tắc rõ về tên trường, kiểu, escaping, phiên bản schema và kiểm soát các trường được công bố.

Một chuỗi `toString()` như `Order{id='O1', status='NEW'}` không phải JSON hợp lệ; cố parse ngược bằng regex rất dễ hỏng khi status chứa dấu nháy, xuống dòng hoặc khi thứ tự field thay đổi.

### 7.3. Exception và stack trace

`Throwable.toString()` thông thường cho tên lớp exception và thông điệp lỗi; nó **không đồng nghĩa với toàn bộ stack trace**. Muốn quan sát stack trace, sử dụng cơ chế của logger để ghi exception/stack trace hoặc gọi `printStackTrace` trong bối cảnh thích hợp. Không cố nhồi toàn bộ stack trace hay object graph vào `toString()` của domain object.

## 8. An toàn dữ liệu: thông tin nào không nên xuất hiện?

`toString()` rất dễ được gọi gián tiếp khi in object hoặc collection; vì vậy cần xem đây là một **bề mặt có thể làm lộ dữ liệu**.

Các ví dụ nên hạn chế/che giấu khi biểu diễn đối tượng gồm:

- Password, access token, API key, secret, cookie xác thực, mã OTP.
- Số thẻ hoặc thông tin tài chính nhạy cảm.
- Thông tin cá nhân không cần thiết cho việc chẩn đoán (địa chỉ, email, số điện thoại, hồ sơ y tế).
- Payload/response body có thể chứa dữ liệu khách hàng hoặc bí mật.
- Dữ liệu binary, khóa mật mã, session state, đối tượng chứa đường dẫn/tài nguyên riêng tư.

Chỉ che mật khẩu bằng `"****"` là chưa đủ nếu các trường khác vẫn có thể lộ dữ liệu. Thiết kế một **allowlist** các trường an toàn để in thường tốt hơn liệt kê hết mọi field rồi chỉ chặn vài cái tên nhạy cảm.

Với record, IDE code generator hoặc Lombok, phải xem lại code `toString()` được sinh tự động: nó có thể đưa tất cả field/component vào kết quả, kể cả field mới thêm trong tương lai. Phần dành cho log nội bộ cũng có thể bị chuyển sang hệ thống tập trung hoặc xuất qua kênh hỗ trợ, vì vậy không nên phụ thuộc vào giả định “chỉ developer thấy”.

Nếu dữ liệu có xuống dòng hoặc ký tự điều khiển do người dùng nhập, ghi log dạng văn bản tự do có thể bị nhiễu bởi log injection. Sử dụng logging có cấu trúc, escaping và giới hạn độ dài theo chính sách hệ thống; đừng kỳ vọng `toString()` tự xử lý an toàn các giá trị tùy ý.

## 9. Những rủi ro về hiệu năng và side effect

`toString()` thường xuất hiện trên đường chẩn đoán, nhưng đôi khi được gọi rất nhiều trong quá trình chạy thực tế. Một phương thức hữu ích nên:

1. **Nhanh và có chi phí dự đoán được.** Không tải toàn bộ dữ liệu từ database, đọc file, gọi HTTP, tính toán phức tạp hoặc tuần tự hóa cả đồ thị đối tượng chỉ để tạo một dòng.
2. **Không gây side effect.** Tránh cập nhật trạng thái, tăng bộ đếm, xóa phần tử hoặc ghi log khác trong `toString()`; một hành động “chỉ quan sát” không nên làm thay đổi chương trình.
3. **Không gây đệ quy vô hạn.** Hai object tham chiếu qua lại có thể làm `toString()` của mỗi bên gọi ngược lại bên kia mãi.
4. **Không làm log bùng nổ.** Giới hạn độ dài text, số phần tử hoặc độ sâu của cấu trúc lồng nhau.
5. **Không phụ thuộc vào các tác vụ có thể thất bại.** Getter có tải dữ liệu lười (*lazy loading*) hoặc proxy ORM có thể thực hiện truy vấn ngoài ý muốn, ném exception hoặc tiếp xúc với trạng thái transaction.
6. **Dễ dùng khi xảy ra lỗi.** Tránh các chuỗi truy cập null không có kiểm tra, lỗi định dạng hoặc giả định object liên quan luôn được khởi tạo.

Ví dụ đồ thị hai chiều:

```java
// Minh họa nguy cơ, không phải lớp Java độc lập.
class Parent {
    Child child;
    @Override public String toString() { return "Parent{child=" + child + "}"; }
}
class Child {
    Parent parent;
    @Override public String toString() { return "Child{parent=" + parent + "}"; }
}
```

Nếu `parent.child.parent == parent`, gọi `parent.toString()` có thể lặp `Parent.toString() → Child.toString() → Parent.toString() → ...` và cuối cùng gây `StackOverflowError`. Cách xử lý thường là in mã tham chiếu ổn định của đối tượng liên quan, ví dụ `childId` thay vì toàn bộ `child.toString()`, hoặc giới hạn độ sâu có chủ đích.

Với collection, `Arrays.deepToString` giúp biểu diễn mảng lồng nhau, nhưng **không vì thế mà tự động phù hợp để in dữ liệu cực lớn hoặc nhạy cảm**. Quan sát cần có mục đích và phạm vi.

## 10. Dạng `toString()` tốt cho các loại object khác nhau

| Loại đối tượng | Nên cân nhắc thể hiện | Nên tránh |
| --- | --- | --- |
| Entity nghiệp vụ | ID ổn định, trạng thái cơ bản, thông tin chẩn đoán được phép công bố | Toàn bộ quan hệ ORM, token, danh sách bản ghi con |
| Value object nhỏ | Các thành phần làm nên giá trị, khi an toàn | Giá trị nhạy cảm không phục vụ chẩn đoán |
| DTO/record | Các trường an toàn, đủ để hiểu sự kiện | In tự động mọi field khi DTO chứa dữ liệu cá nhân |
| Cấu trúc dữ liệu | Kích thước, loại phần tử, vài phần tử mẫu | In hàng triệu phần tử hay binary payload |
| Tác vụ bất đồng bộ | ID tác vụ, trạng thái, số lần thử, thời gian đã biết | Gọi mạng hoặc thay đổi trạng thái bên trong `toString()` |
| Wrapper/proxy | Tên loại, ID và trạng thái proxy cần thiết | Tự động kích hoạt lazy loading sâu |
| Exception | Loại, message đã được kiểm soát, mã lỗi | Thông tin bí mật; coi `toString()` thay stack trace |

Không phải đối tượng nào cũng cần override `toString()`. Với lớp nội bộ không cần chẩn đoán bằng text, mặc định có thể đủ. Với dữ liệu chẩn đoán xuất hiện thường xuyên, viết một biểu diễn ngắn và ổn định về ý nghĩa thường giúp tiết kiệm nhiều thời gian điều tra.

## 11. Một số lỗi dễ gặp khi dùng `toString()`

### Lỗi 1: coi chuỗi là định danh

`object.toString()` không phải khóa đáng tin cậy cho database, HTTP endpoint, metrics label hoặc tracing. Nếu nhiều đối tượng có cùng text, việc phân biệt sai sẽ gây lỗi; nếu text đổi sau khi sửa status, khóa được sinh ra cũng đổi. Hãy dùng business ID, trace ID hoặc ID tác vụ chuyên dụng.

### Lỗi 2: dùng `toString()` để kiểm tra equality

Hai object có text giống nhau vẫn có thể là hai instance khác nhau. Khi cần kiểm tra cùng tham chiếu, dùng `==`; khi cần equality theo ngữ nghĩa, dùng `equals()` đã được thiết kế đúng.

### Lỗi 3: in trực tiếp array và tưởng đã in phần tử

`array.toString()` trên một mảng không cung cấp nội dung như mong đợi. Sử dụng `Arrays.toString` cho mảng một chiều và `Arrays.deepToString` cho mảng lồng nhau phù hợp.

### Lỗi 4: nhồi toàn bộ object graph

Ví dụ `Order.toString()` gọi `Customer.toString()`, rồi `Customer.toString()` lại gọi tất cả `Order`; chuỗi rất dài hoặc đệ quy vô hạn. Chỉ in mã định danh của quan hệ và vài trường trạng thái cần thiết.

### Lỗi 5: tin rằng mọi giá trị đều an toàn để log

Giá trị `record.toString()` sinh tự động có thể chứa field mới được thêm sau này. Với object dùng để ghi log, kiểm tra policy dữ liệu và chỉ cho xuất một tập trường đã được phép.

### Lỗi 6: để `toString()` ném lỗi phụ

Nếu đang chẩn đoán một exception khác mà `toString()` lại gọi phương thức có thể ném exception hoặc truy cập object chưa khởi tạo, thông tin chẩn đoán bị mất hoặc gây nhiễu. Nên ưu tiên dữ liệu đã có, kiểm tra giá trị có thể `null` và không gọi tài nguyên ngoài.

### Lỗi 7: test cứng định dạng khi không cần

Kiểm thử `toString()` là hợp lý nếu lớp cam kết định dạng text cụ thể cho công cụ vận hành. Nếu text chỉ phục vụ debug, test bám sát khoảng trắng/dấu phẩy/cách đặt ngoặc có thể làm code khó đổi. Hãy test những trường quan trọng và nguyên tắc không lộ bí mật; tránh dựa vào identity hash biến động.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Hai instance `Payment` khác nhau có cùng `toString()`. Có thể kết luận `a == b` hoặc `a.equals(b)` là `true` không?

**Đáp án:** Không. `toString()` chỉ biểu diễn các trường được chọn; nó không quyết định quan hệ tham chiếu hay equality.

**Câu 2.** Tên lớp theo sau dấu `@` trong `Object.toString()` có phải địa chỉ RAM không?

**Đáp án:** Không. `Object.toString()` dùng tên lớp và hash của đối tượng dưới dạng hex; hash không phải địa chỉ, cũng không bảo đảm duy nhất.

**Câu 3.** Với `int[] a = {1, 2}`, cách in nội dung dễ đọc là gì?

**Đáp án:** `Arrays.toString(a)` cho `[1, 2]`. Với `int[][]`, dùng `Arrays.deepToString(...)` để in các hàng lồng nhau.

**Câu 4.** `record Credentials(String user, String token) {}` có phù hợp để log trực tiếp không?

**Đáp án:** Thông thường không, vì `toString()` được sinh tự động sẽ thể hiện cả component `token`. Nên thiết kế biểu diễn an toàn hoặc chỉ log các field đã được phép công bố.

**Câu 5.** Một phương thức `toString()` gọi database để lấy danh sách chi tiết trước khi in. Rủi ro là gì?

**Đáp án:** Thêm độ trễ, truy vấn ngoài ý muốn, lỗi khi transaction không còn, tải lượng lớn và khả năng ném exception trong lúc đang chẩn đoán. Hãy giới hạn vào trạng thái sẵn có.

**Câu 6.** Khi phân tích lỗi, có thể dùng `exception.toString()` thay thế stack trace hoàn chỉnh không?

**Đáp án:** Không. Nó thường cho loại exception và message, trong khi stack trace chứa ngữ cảnh lời gọi; cần sử dụng cơ chế ghi exception/stack trace riêng.

**Câu 7.** Một lớp đã override `hashCode()` theo business ID nhưng giữ `Object.toString()`. Phần hex sau `@` phản ánh điều gì?

**Đáp án:** Nó là biểu diễn hex của kết quả `hashCode()` hiện tại, nên có thể phản ánh hash theo business ID đã override. Không nên coi đó là identity hash hay ID gốc.

---

**Ghi nhớ:** `toString()` giúp đối tượng **dễ quan sát và chẩn đoán**, đặc biệt ở debugger, log và khi in collection. Hãy chọn những trường an toàn, ngắn gọn, có ý nghĩa; tránh side effect, dữ liệu nhạy cảm, đệ quy và I/O. Giữ riêng vai trò của `toString()` với `equals()`, `hashCode()`, định danh nghiệp vụ, serialization và logging có cấu trúc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-002">Dạng mặc định tên lớp, ký hiệu @ và hash code dạng thập lục phân</a>

<details>
<summary>Xem chi tiết</summary>

# Dạng mặc định của `Object.toString()`: tên lớp, dấu `@` và mã băm thập lục phân

Khi in một object trong Java, ta có thể thấy chuỗi dạng `com.example.Book@5e2de80c`. Chuỗi này thường xuất hiện vì lớp **kế thừa triển khai mặc định của `Object.toString()`**. Nó **không phải nội dung các trường**, **không phải địa chỉ bộ nhớ được Java cam kết**, và **không phải định danh duy nhất** của object.

Bài học giải thích chính xác công thức tạo chuỗi, ý nghĩa từng phần, quan hệ với `hashCode()`, những trường hợp không còn dùng định dạng mặc định, cách thử bằng Java 17 và những lỗi cần tránh.

## 1. Hợp đồng mặc định và công thức

Trong `java.lang.Object`, triển khai `toString()` mặc định có thể biểu diễn tương đương bằng:

~~~java
public String toString() {
    return getClass().getName() + "@"
            + Integer.toHexString(hashCode());
}
~~~

Đây là **mã minh họa công thức của phương thức trong `Object`**, không phải đoạn cần chép vào mọi lớp. Ba thành phần được nối trực tiếp và **không có dấu cách**:

| Thành phần | Nguồn | Ý nghĩa |
|---|---|---|
| `getClass().getName()` | Runtime class của object | Tên lớp theo quy tắc binary name của Java |
| `"@"` | Ký tự cố định | Dấu phân tách giữa tên lớp và mã băm |
| `Integer.toHexString(hashCode())` | Giá trị `int` từ `hashCode()` | Chuyển mã băm thành chuỗi hệ **16**, dùng chữ `a`–`f` viết thường |

Với một lớp `Book` trong package `com.example` không ghi đè `toString()`, kết quả có **dạng**:

~~~text
com.example.Book@5e2de80c
~~~

Phần `5e2de80c` chỉ là **mã ví dụ minh họa**, không phải giá trị có thể tiên đoán cho một object bất kỳ. Cùng mã nguồn chạy vào lúc khác có thể in phần đuôi khác. Không dùng chuỗi đó làm dữ liệu kết quả bắt buộc của bài kiểm thử.

Khi một object không ghi đè `toString()`, JVM gọi phương thức kế thừa từ `Object`. Nếu lớp **ghi đè `toString()`**, Java sẽ dùng triển khai của lớp đó; định dạng `ClassName@hex` không còn được bảo đảm.

## 2. Tên lớp là tên runtime, không phải tên biến

Phần đứng trước `@` lấy từ `getClass().getName()`. Nó phụ thuộc vào **kiểu của object thực tế**, không phụ thuộc kiểu khai báo của biến:

~~~java
Object value = new StringBuilder("abc");
System.out.println(value.getClass().getName());
// java.lang.StringBuilder
~~~

Đây là snippet đặt trong `main`. Kiểu tĩnh của `value` là `Object`, nhưng lớp runtime là `StringBuilder`.

Phần tên có thể khác kỳ vọng:

- Lớp top-level thuộc package `com.example`: `com.example.Book`, không chỉ `Book`.
- Lớp thành viên `Demo.Item`: binary name thường là `Demo$Item`, **không** là `Demo.Item`.
- Lớp cục bộ hoặc anonymous có dạng tên do trình biên dịch tạo; không nên hardcode quy ước số trong tên vào kiểm thử.
- Với mảng nguyên thủy `int[]`, `getClass().getName()` trả `[I`; với mảng `String[]` trả `[Ljava.lang.String;`. Vì vậy `toString()` mặc định của array có tiền tố như `[I@`, thay vì chữ `int[]@`.

**`Class.getName()`, `getSimpleName()` và `getCanonicalName()` không phải lúc nào cho cùng kết quả.** `Object.toString()` dùng `getName()`. Nếu muốn tên lớp ngắn để hiển thị, đó là lựa chọn khác cần được viết rõ trong `toString()` tùy chỉnh.

## 3. Mã băm sau dấu @ thực sự là gì?

Phần sau `@` là kết quả của biểu thức:

~~~java
Integer.toHexString(hashCode())
~~~

- `hashCode()` trả một số nguyên Java kiểu `int` có dấu (32-bit).
- `Integer.toHexString(...)` biểu diễn các bit của số theo cơ số 16, **không thêm tiền tố `0x`**.
- Chữ cái trong phần hex được viết thường. Ví dụ mã băm thập phân `42` thành `2a`, `255` thành `ff`, `0` thành `0`, còn `-1` thành `ffffffff`.
- Giá trị này **không được cam kết là địa chỉ bộ nhớ**, số thứ tự object, UUID hay mã duy nhất trên toàn chương trình.
- Hai object khác nhau có thể **trùng hash**. Không được suy từ đuôi hex giống nhau rằng hai tham chiếu chỉ cùng một instance.

Với lớp **không ghi đè `hashCode()`**, phương thức mặc định của `Object` thường cấp một giá trị thích hợp cho identity hashing. Java không quy định phải tính nó từ địa chỉ vật lý. `System.identityHashCode(obj)` trả mã băm theo cơ chế identity của `Object` ngay cả khi lớp đã ghi đè `hashCode()`.

Một điểm đặc biệt quan trọng: **`Object.toString()` gọi `hashCode()` bằng dispatch động**. Nếu lớp ghi đè `hashCode()` nhưng không ghi đè `toString()`, phần hex phản ánh **hashCode đã ghi đè**, không bắt buộc là `System.identityHashCode()`. Đây là lý do không nên mô tả mặc định thành `ClassName@identityHash` trong mọi trường hợp.

## 4. Java 17 chạy được: kiểm chứng công thức và đầu ra xác định

Ví dụ sau có ba lớp lồng:

- `Plain`: giữ nguyên `Object.hashCode()` và `Object.toString()`, nên phần hex không thể dự đoán trước; chương trình kiểm tra công thức bằng boolean.
- `FixedHash`: **chỉ để minh họa**, ghi đè `hashCode()` trả `42`, kế thừa `toString()`; vì vậy phần hex xác định là `2a`.
- `NegativeHash`: trả `-1` để kiểm tra cách đổi thành `ffffffff`.

Lưu file **`ObjectToStringDemo.java`**, không khai báo package:

~~~java
public class ObjectToStringDemo {
    static final class Plain {}

    static final class FixedHash {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    static final class NegativeHash {
        @Override
        public int hashCode() {
            return -1;
        }
    }

    public static void main(String[] args) {
        Plain plain = new Plain();
        String raw = plain.toString();

        System.out.println("plain class: "
                + plain.getClass().getName());
        System.out.println("plain prefix: "
                + raw.startsWith(plain.getClass().getName() + "@"));
        System.out.println("plain formula: "
                + raw.equals(plain.getClass().getName() + "@"
                        + Integer.toHexString(plain.hashCode())));
        System.out.println("plain identity hash: "
                + (plain.hashCode()
                        == System.identityHashCode(plain)));

        FixedHash fixed = new FixedHash();
        System.out.println("fixed: " + fixed);
        System.out.println("fixed hex: "
                + Integer.toHexString(fixed.hashCode()));

        NegativeHash negative = new NegativeHash();
        System.out.println("negative: " + negative);
        System.out.println("negative hex: "
                + Integer.toHexString(negative.hashCode()));

        int[] numbers = {1, 2, 3};
        System.out.println("array class: "
                + numbers.getClass().getName());
        System.out.println("array prefix: "
                + numbers.toString().startsWith("[I@"));
        System.out.println("array content: "
                + java.util.Arrays.toString(numbers));

        String text = "hello";
        System.out.println("String toString: " + text.toString());
    }
}
~~~

Lệnh biên dịch và chạy trên JDK 17+:

~~~bash
javac ObjectToStringDemo.java
java ObjectToStringDemo
~~~

**Đầu ra có thể dự đoán** (không in chuỗi hash ngẫu nhiên của `Plain`):

~~~text
plain class: ObjectToStringDemo$Plain
plain prefix: true
plain formula: true
plain identity hash: true
fixed: ObjectToStringDemo$FixedHash@2a
fixed hex: 2a
negative: ObjectToStringDemo$NegativeHash@ffffffff
negative hex: ffffffff
array class: [I
array prefix: true
array content: [1, 2, 3]
String toString: hello
~~~

Giải thích:

1. `Plain` là lớp thành viên tĩnh trong `ObjectToStringDemo`, vì vậy `getName()` trả binary name `ObjectToStringDemo$Plain`. Kiểm tra tiền tố và công thức trả `true` mà không cần giả định một hash cụ thể.
2. `Plain` không ghi đè hash, nên `hashCode()` phù hợp với `System.identityHashCode()` của chính instance đó.
3. `FixedHash` kế thừa `Object.toString()` nhưng ghi đè `hashCode()`. Kết quả **chính xác** là `ObjectToStringDemo$FixedHash@2a`.
4. `NegativeHash` chứng minh `Integer.toHexString(-1)` cho `ffffffff`; phép đổi hệ 16 không đặt dấu trừ phía trước.
5. `int[]` là object có tên runtime `[I` và giữ `toString()` mặc định. Muốn hiển thị phần tử `[1, 2, 3]`, dùng `Arrays.toString()`.
6. `String` đã ghi đè `toString()` nên trả chính nội dung `hello`, không phải `java.lang.String@...`.

**Lưu ý thiết kế:** các lớp cố tình trả hash cố định trong ví dụ chỉ dùng để trình diễn dispatch và phép chuyển hex. Trong phần mềm thực, mã băm phải phù hợp với `equals()` và có phân phối hợp lý theo dữ liệu để giảm va chạm trong collection băm.

## 5. Điều gì xảy ra khi lớp ghi đè equals, hashCode hoặc toString?

Xét từng trường hợp độc lập:

| Lớp định nghĩa | Hình thức `toString()` nếu không ghi đè nó | Cần chú ý |
|---|---|---|
| Không ghi đè cả ba | `RuntimeClassName@hex(Object.hashCode())` | Phần hex không ổn định giữa các phiên chạy |
| Chỉ ghi đè `hashCode()` | `RuntimeClassName@hex(overriddenHashCode())` | Vẫn là **Object.toString** nhưng đuôi không mặc định identity hash |
| Chỉ ghi đè `equals()` | Vẫn dùng Object.toString và hash mặc định | **Có nguy cơ vi phạm hợp đồng** nếu hai instance được equals coi bằng nhau nhưng hash khác |
| Ghi đè cả `equals()` và `hashCode()` | Vẫn có dạng `RuntimeClassName@hex(hashCode())` | Hai object bằng nhau có cùng hash, có thể có chuỗi toString giống nhau |
| Ghi đè `toString()` | Phụ thuộc code ghi đè | Có thể là JSON-like, text tùy chỉnh, danh sách thuộc tính... |

Ghi đè `toString()` **không có nghĩa** tự thay đổi `equals()` hay `hashCode()`. Ngược lại, ghi đè `equals()`/`hashCode()` không tự sinh cách biểu diễn văn bản dễ đọc. Đây là những hợp đồng khác nhau.

### Ví dụ tùy chỉnh để in các trường

~~~java
final class Book {
    private final String isbn;
    private final String title;

    Book(String isbn, String title) {
        this.isbn = isbn;
        this.title = title;
    }

    @Override
    public String toString() {
        return "Book{isbn='" + isbn + "', title='" + title + "'}";
    }
}
~~~

Đây là khai báo lớp có thể biên dịch; muốn chạy cần thêm `main`. In `new Book("978-1", "Java")` sẽ cho:

~~~text
Book{isbn='978-1', title='Java'}
~~~

Không còn tên đầy đủ của lớp, `@` hay hex vì lớp đã tự quyết định chuỗi trả về. Nếu `Book` dùng làm **value object** trong `HashSet`/`HashMap`, vẫn phải định nghĩa equality/hash tương ứng khi cần; `toString()` không thay thế hai hợp đồng đó.

## 6. Các lớp chuẩn không nhất thiết in theo mẫu Object

Mọi lớp đều thừa hưởng `toString()`, nhưng nhiều lớp chuẩn **ghi đè**:

- `String.toString()` trả nội dung chuỗi. `StringBuilder.toString()` cũng tạo chuỗi biểu diễn các ký tự nó chứa.
- `Integer`, `BigDecimal` và các lớp số thường in giá trị theo quy tắc văn bản của chính kiểu.
- `List`, `Set`, `Map` trong các triển khai phổ biến in nội dung phần tử/mapping; thứ tự duyệt của `HashSet`/`HashMap` không được bảo đảm.
- `record` được Java sinh `toString()` phản ánh các component; nó không giữ nguyên mẫu `ClassName@hex`. Hợp đồng `record` có cách thể hiện riêng.
- **Array** lại là một trường hợp quen thuộc giữ cách in kiểu Object: `new int[]{1,2}.toString()` không in `[1, 2]`. Dùng `Arrays.toString` hoặc `Arrays.deepToString` cho mảng lồng tương ứng.

Vì vậy **không nên suy** từ một object hiển thị dạng `@hex` rằng tất cả object khác đều vậy. Cần kiểm tra có ghi đè `toString()` không; có thể xem Javadoc và cách lớp triển khai ghi đè.

## 7. Đâu là giới hạn và ý nghĩa của giá trị hex?

### Không phải địa chỉ bộ nhớ

Theo đặc tả Java, `hashCode()` là giá trị `int`; không có yêu cầu rằng số đó chứa địa chỉ bộ nhớ hoặc giữ nguyên địa chỉ vật lý. GC có thể di chuyển object, nhưng người dùng Java không cần suy luận địa chỉ vật lý từ `Object.toString()`.

### Không phải khóa duy nhất

Hash có không gian hữu hạn (32 bit). Collision có thể xảy ra, kể cả với hai object khác nhau. Nếu cần biết hai biến có cùng instance, dùng **`==` cho tham chiếu**; nếu cần hai giá trị có bằng nhau theo nghiệp vụ, dùng `equals()`; không lấy đuôi `@hex` làm bằng chứng.

### Không phải định dạng lưu trữ ổn định

Không lưu `Object.toString()` mặc định để sử dụng làm ID database, khóa API, liên kết ổn định, token xác thực hoặc dữ liệu cần parse qua nhiều lần chạy. Hợp đồng `Object.toString()` mô tả dạng chuỗi nhưng không cam kết hash cố định cho cùng ý nghĩa nghiệp vụ trong các tiến trình khác nhau.

### Không suy diễn rằng mọi hash đều là identity hash

Nếu `hashCode()` bị override, `Object.toString()` sẽ gọi hash mới. Ngay cả hai object bằng nhau theo `equals()` và có cùng hash cũng có thể in cùng phần đuôi, nhưng không vì vậy mà chúng là cùng instance. `System.identityHashCode()` là API riêng để lấy identity hash (vẫn **không** cam kết duy nhất).

## 8. Phân biệt toString, equals và hashCode

| Câu hỏi | Dùng | Điều không nên làm |
|---|---|---|
| Muốn trình bày đối tượng cho log/debug? | `toString()` phù hợp với nội dung muốn đọc | Không coi text là identity duy nhất |
| Hai tham chiếu có cùng object? | `a == b` | Không so chuỗi `a.toString().equals(b.toString())` |
| Hai đối tượng có giá trị bằng nhau? | `a.equals(b)` hoặc `Objects.equals(a,b)` | Không dựa vào kết quả `toString` |
| Cần tổ chức khóa trong `HashMap`/`HashSet`? | `equals()` và `hashCode()` đúng hợp đồng | Không dùng đuôi hex của `toString` thay cho khóa |
| Cần in nội dung mảng? | `Arrays.toString()`/`deepToString()` | Không dùng `array.toString()` để in từng phần tử |

Ví dụ: hai object có cùng `toString()` **chưa chắc equals**. Một `toString()` tự viết có thể chỉ in `name` và bỏ `id`, trong khi equality xét ID. Ngược lại, hai object có thể bằng nhau theo `equals()` nhưng có chuỗi `toString()` khác nếu code tự tùy chỉnh không phản ánh cùng các trường. Vì vậy không nên parse hoặc so text của `toString()` để suy ra quan hệ equality.

## 9. Các lỗi thường gặp

1. **Xem `@5e2de80c` là địa chỉ ô nhớ:** Java không bảo đảm điều đó. Đây là hex của hash code.
2. **Nghĩ ký tự sau @ luôn đến từ Object.hashCode:** khi lớp ghi đè `hashCode()`, Object.toString sẽ gọi phiên bản ghi đè.
3. **Cố định mã hex khi viết test:** giá trị hash mặc định không được cam kết giữa các lần chạy; hãy thử bằng công thức hoặc chỉ kiểm tra cấu trúc.
4. **Nhầm `getSimpleName()` với `getName()`:** lớp lồng/thuộc package có tên binary khác tên hiển thị ngắn.
5. **In mảng mong thấy phần tử:** `int[].toString()` cho dạng `[I@hex`; dùng `Arrays.toString`.
6. **Dùng `toString()` làm equality:** các phương thức có mục đích khác nhau; toString có thể thay đổi khi cải thiện log.
7. **Chỉ ghi đè equals mà quên hashCode:** có thể khiến hash collection hành xử không đúng; toString không giải quyết được lỗi này.
8. **Mặc định mọi lớp chuẩn đều in theo dạng @hex:** `String`, wrapper, collection và record có quy tắc khác.

## 10. Tự kiểm tra

**Câu 1.** Với một object không override `toString()`, công thức tạo chuỗi là gì?  
**Đáp án:** `getClass().getName() + "@" + Integer.toHexString(hashCode())`.

**Câu 2.** Lớp `FixedHash` chỉ override `hashCode()` trả `255` nhưng không override `toString()`. Phần sau `@` là gì?  
**Đáp án:** `ff`, vì `Integer.toHexString(255)` trả `"ff"`.

**Câu 3.** `Object.toString()` mặc định có bảo đảm mã hex là duy nhất và bằng địa chỉ vật lý không?  
**Đáp án:** Không. Hash có thể va chạm và API không cam kết đó là địa chỉ bộ nhớ.

**Câu 4.** Tại sao tên lớp lồng có thể chứa dấu `$`?  
**Đáp án:** Vì `getClass().getName()` trả binary name, không nhất thiết tên nguồn/đơn giản.

**Câu 5.** Nếu một lớp override `toString()` để in `Book{title='Java'}`, phần `@hex` còn bắt buộc xuất hiện không?  
**Đáp án:** Không. Chuỗi trả về do implementation `toString()` mới quyết định.

**Câu 6.** `new int[]{1,2}.toString()` có trả `[1, 2]` không?  
**Đáp án:** Không; mảng dùng dạng của Object. Muốn in nội dung cần `Arrays.toString(...)`.

**Ghi nhớ:** Dạng mặc định **`TênLớpRuntime@hex(hashCode())`** phản ánh tên lớp và mã băm được chuyển sang hệ 16; nó hữu ích khi nhìn log/debug, nhưng không phải định danh duy nhất hay cách so sánh giá trị. Luôn phân biệt định dạng kế thừa từ `Object` với `toString()` đã được lớp cụ thể ghi đè.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-003">Vì sao phần sau @ không được hiểu là địa chỉ bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phần sau @ không được hiểu là địa chỉ bộ nhớ

## 1. Hiện tượng thường gặp khi in một object

Khi in một đối tượng Java chưa tự định nghĩa cách biểu diễn chuỗi, ta thường thấy dạng:

```text
com.example.Person@5acf9800
```

Nhiều người nhìn phần `5acf9800` sau dấu `@` rồi gọi đó là “địa chỉ bộ nhớ” của đối tượng. Kết luận này **không được hợp đồng Java bảo đảm và vì thế không chính xác**. Ở cách biểu diễn mặc định từ `java.lang.Object.toString()`, phần sau dấu `@` là **giá trị `hashCode()` của đối tượng, được định dạng thành chuỗi thập lục phân (hex)**.

Hệ quả quan trọng:

- Không thể suy ra vị trí đối tượng trong RAM từ chuỗi `ClassName@hex`.
- Hai đối tượng **khác tham chiếu** vẫn có thể in ra **cùng** hậu tố sau `@`.
- Giá trị hash không phải ID duy nhất hoặc địa chỉ ổn định dùng để lưu lại giữa các lần chạy chương trình.
- Nếu lớp ghi đè `hashCode()`, hậu tố của `Object.toString()` có thể phản ánh quy tắc hash do lập trình viên định nghĩa, hoàn toàn không liên quan đến cơ chế cấp phát bộ nhớ.
- Nếu lớp ghi đè `toString()`, chuỗi in ra thậm chí **không cần có ký tự `@`**.

Bài này tập trung vào **cách hiểu chuỗi `toString()` mặc định**, nhất là nguồn gốc và giới hạn ý nghĩa của phần sau dấu `@`.

## 2. Công thức thực sự của Object.toString()

Theo hợp đồng của `java.lang.Object.toString()`, cách biểu diễn mặc định tương đương với:

```java
getClass().getName() + "@" + Integer.toHexString(hashCode())
```

Đây là **biểu thức minh họa cơ chế của phương thức**, không phải một chương trình Java độc lập. Mỗi thành phần có nhiệm vụ rõ ràng:

| Thành phần | Ý nghĩa |
| --- | --- |
| `getClass()` | Lấy lớp **thực tế lúc chạy** của đối tượng. |
| `getClass().getName()` | Trả tên lớp dạng nhị phân mà Java dùng cho `Class.getName()`, ví dụ `com.example.Person`. |
| `"@"` | Ký tự phân cách giữa tên lớp và hậu tố hash. |
| `hashCode()` | Lấy số nguyên `int` do phương thức `hashCode()` của đối tượng trả về. |
| `Integer.toHexString(...)` | Chuyển mẫu bit của số nguyên `int` thành biểu diễn hex không dấu, dùng các ký tự `0–9` và `a–f`. |

Giả sử `hashCode()` trả về số thập phân `42`:

```java
System.out.println(Integer.toHexString(42)); // 2a
```

Do đó, đối tượng của lớp `Person` đang dùng `Object.toString()` và có `hashCode() == 42` sẽ được hiển thị với hậu tố `@2a`. Số `2a` trong hệ 16 chỉ là một cách viết khác của `42` trong hệ 10. Nó không chứa thông tin bắt buộc về vùng nhớ.

Cần phân biệt **giá trị hash** với **kích thước/vị trí tham chiếu**. `hashCode()` trả về `int` 32 bit; địa chỉ vùng nhớ trong một tiến trình có thể có cách biểu diễn, kích thước và cơ chế quản lý hoàn toàn khác. Việc hậu tố có ký tự hex khiến nó trông giống địa chỉ chỉ là sự giống nhau về cách viết.

### Chú ý về tên lớp

Nếu đối tượng là instance của lớp lồng, `getClass().getName()` có thể chứa ký tự `$`. Nếu là một mảng nguyên thủy kiểu `int[]`, tên từ `getClass().getName()` là `[I`, nên đầu ra mặc định của đối tượng mảng có dạng `[I@...`. Các quy tắc tên lớp này cũng không nói gì về vị trí bộ nhớ.

Ví dụ:

```java
int[] values = {1, 2, 3};
System.out.println(values.getClass().getName()); // [I
System.out.println(java.util.Arrays.toString(values)); // [1, 2, 3]
```

*Đây là các lệnh minh họa trong một phương thức.* Muốn xem **nội dung** mảng, dùng `Arrays.toString` hoặc `Arrays.deepToString` khi phù hợp, thay vì cố giải mã hậu tố sau `@`.

## 3. Hash code không phải địa chỉ và cũng không phải danh tính duy nhất

`Object.hashCode()` trả về một số nguyên phục vụ hợp đồng băm của Java. Đối với `Object.hashCode()` mặc định, Java mô tả ý định tạo các mã khác nhau cho các object khác nhau “khi khả thi”, nhưng **không bảo đảm các mã luôn duy nhất**.

Một số điểm giúp tránh hiểu nhầm:

1. **Không có API địa chỉ bộ nhớ trong `hashCode()`.** Đặc tả Java không yêu cầu `Object.hashCode()` tính bằng cách lấy địa chỉ RAM.
2. **Va chạm hash là hợp lệ.** Hai object khác nhau có thể có cùng hash mà không vi phạm hợp đồng.
3. **Hash không xác định equality theo chiều ngược lại.** Nếu `a.equals(b)` là `true` thì chúng phải có hash bằng nhau; nhưng cùng hash không chứng minh `equals()` là `true`.
4. **Hậu tố không phải khóa lưu trữ.** Không dùng `@...` làm ID database, khóa phiên, định danh theo dõi liên tiến trình hoặc giá trị bí mật.
5. **Giá trị không có cam kết lặp lại qua nhiều lần chạy.** Với hash mặc định, chương trình khởi động lại không có nghĩa các object mới cùng loại sẽ có hậu tố cũ.
6. **JVM được tự do triển khai cơ chế của hash mặc định** miễn giữ hợp đồng ngôn ngữ/API. Một triển khai nào đó có thể dựa một phần vào thông tin liên quan đến đối tượng, nhưng không được suy luận từ đó rằng mọi JVM hay mọi lần chạy đều xuất địa chỉ bộ nhớ.

Đặc biệt, garbage collector có thể di chuyển đối tượng trong bộ nhớ trong một số cấu hình JVM. Java vẫn yêu cầu `hashCode()` của một object nhất quán trong cùng quá trình thực thi khi thông tin liên quan đến equality không đổi. Do đó, ngay cả trong môi trường có quản lý bộ nhớ di chuyển, chuỗi hash cũng không thể được hiểu như một bản chụp địa chỉ đang tồn tại ở mọi thời điểm.

## 4. Thí nghiệm quan trọng: hai object khác nhau cùng in @2a

Ta không cần đo đạc vùng nhớ để chứng minh phần sau `@` **không phải địa chỉ**. Chỉ cần tạo một lớp **giữ `toString()` mặc định** nhưng **ghi đè `hashCode()`** để trả một hằng số. Khi đó mọi instance của lớp sẽ có cùng hậu tố dù là các object riêng biệt.

Tạo tệp `ToStringAtDemo.java`:

```java
public class ToStringAtDemo {

    static final class FixedHash {
        private final String label;

        FixedHash(String label) {
            this.label = label;
        }

        @Override
        public int hashCode() {
            return 42;
        }
        // Không ghi đè toString() và equals().
    }

    static final class Described {
        private final String label;

        Described(String label) {
            this.label = label;
        }

        @Override
        public int hashCode() {
            return 255;
        }

        @Override
        public String toString() {
            return "Described(label=" + label + ")";
        }
    }

    public static void main(String[] args) {
        FixedHash first = new FixedHash("A");
        FixedHash second = new FixedHash("B");

        System.out.println(first);
        System.out.println(second);
        System.out.println(first == second);
        System.out.println(first.equals(second));
        System.out.println(first.toString().equals(second.toString()));

        System.out.println(first.getClass().getName());
        System.out.println(Integer.toHexString(first.hashCode()));
        System.out.println(first.getClass().getName() + "@"
                + Integer.toHexString(first.hashCode()));

        System.out.println(new Described("third"));
        System.out.println(String.valueOf((Object) null));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn, tại thư mục chứa tệp:

```bash
javac ToStringAtDemo.java
java ToStringAtDemo
```

Đầu ra chính xác trong package mặc định như mã trên:

```text
ToStringAtDemo$FixedHash@2a
ToStringAtDemo$FixedHash@2a
false
false
true
ToStringAtDemo$FixedHash
2a
ToStringAtDemo$FixedHash@2a
Described(label=third)
null
```

Giải thích từng bước:

1. `first` và `second` đều là `FixedHash`, được tạo bằng hai lần `new` nên `first == second` là `false`.
2. `FixedHash` không ghi đè `equals()`, vì vậy phép so tham chiếu mặc định cũng cho `first.equals(second) == false`.
3. `FixedHash` ghi đè `hashCode()` để luôn trả `42`; `Integer.toHexString(42)` là `2a`.
4. `FixedHash` không ghi đè `toString()`. Lời gọi `Object.toString()` của **mỗi** instance sẽ gọi `hashCode()` đã ghi đè. Bởi vậy cả hai object in ra cùng chuỗi `ToStringAtDemo$FixedHash@2a`.
5. Chuỗi `toString()` của chúng giống nhau (`true`) trong khi **hai object không cùng tham chiếu và không bằng nhau theo `equals()`**. Vậy phần sau `@` không thể được hiểu như địa chỉ duy nhất của mỗi object.
6. `Described` có ghi đè `toString()`, nên dù `hashCode() == 255`, chuỗi hiển thị chỉ là `Described(label=third)`. Công thức `ClassName@hex` chỉ áp dụng khi đang dùng cách biểu diễn mặc định.
7. Với giá trị `null`, `String.valueOf((Object)null)` trả chuỗi `"null"`; không có object thực sự để gọi `getClass()` hay `hashCode()`.

Tên `ToStringAtDemo$FixedHash` có dấu `$` vì đây là lớp lồng `static` và `Class.getName()` trả tên nhị phân của lớp. Nếu đưa lớp vào package khác, phần tên lớp in ra sẽ thêm tiền tố package, nhưng logic về hậu tố `@2a` không thay đổi.

### Điểm tinh tế: Object.toString() vẫn gọi hashCode() theo cơ chế ghi đè

Một lớp có thể **không** định nghĩa `toString()` nhưng **có** định nghĩa `hashCode()`. Khi `Object.toString()` thực hiện lời gọi `hashCode()` trên đối tượng, Java áp dụng dynamic dispatch để chạy phiên bản phù hợp với lớp runtime. Vì vậy, không nên nói rằng hậu tố sau `@` luôn là “identity hash” ngay cả khi `toString()` còn dùng mặc định.

Trong thí nghiệm, hậu tố được sinh trực tiếp từ phương thức `FixedHash.hashCode()` do lập trình viên viết. Đó là một số do chương trình quyết định, không phải thông tin bộ nhớ mà JVM tiết lộ.

## 5. Phân biệt hashCode() với System.identityHashCode()

Java còn có:

```java
int identityHash = System.identityHashCode(object);
```

*Đây là một đoạn lệnh minh họa trong phương thức, với `object` đã được khai báo.*

`System.identityHashCode(object)` trả về mã hash giống cách tính **`Object.hashCode()` mặc định** cho đối tượng ấy, **ngay cả khi lớp đã ghi đè `hashCode()`**. Điều này hữu ích khi cần quan sát theo identity trong công cụ debug. Tuy nhiên, **identity hash cũng không phải địa chỉ bộ nhớ** và **không được cam kết duy nhất**.

| Biểu thức | Nguồn giá trị | Có chắc là địa chỉ bộ nhớ? |
| --- | --- | --- |
| `object.hashCode()` | Phương thức `hashCode()` thực tế; có thể do lớp ghi đè. | Không. |
| `System.identityHashCode(object)` | Hash theo semantics mặc định của `Object`, bỏ qua override `hashCode()`. | Không. |
| `Integer.toHexString(object.hashCode())` | Cách biểu diễn hex của hash thông thường. | Không. |
| `Object.toString()` mặc định | Tên lớp + `@` + hex của `object.hashCode()`. | Không. |
| `object.toString()` nếu lớp ghi đè | Chuỗi do lớp tự định nghĩa. | Không thể suy đoán định dạng từ Object mặc định. |

Điều gì xảy ra trong ví dụ `FixedHash`?

- `first.hashCode() == 42` là giá trị chắc chắn vì lớp đã ghi đè phương thức.
- `System.identityHashCode(first)` do JVM cấp theo quy tắc identity hash, **không** bị ép thành `42`.
- Trị số identity hash thực tế không được bảo đảm và có thể tình cờ bằng hoặc khác `42`; vì vậy bài không ghi stdout giả định cho nó.

Ngay cả `System.identityHashCode(first) == System.identityHashCode(second)` cũng không đủ chứng minh hai biến đang trỏ cùng object: hai object khác nhau có thể xảy ra hash collision. Muốn hỏi có đúng cùng instance hay không, dùng `first == second`. Muốn hỏi bình đẳng logic theo lớp, dùng `first.equals(second)`.

## 6. Liên hệ với equals() và hashCode(): ba khái niệm khác nhau

Có ba câu hỏi cần giữ tách bạch:

| Câu hỏi | Cách kiểm tra | Ý nghĩa |
| --- | --- | --- |
| Hai biến có trỏ cùng instance không? | `a == b` | Đồng nhất tham chiếu. |
| Hai object có bằng nhau về giá trị logic không? | `a.equals(b)` | Theo hợp đồng và cách triển khai của lớp. |
| Hai object có cùng mã băm không? | `a.hashCode() == b.hashCode()` | So hai giá trị `int`, chỉ là thông tin hỗ trợ băm. |

Hợp đồng Java bắt buộc: **nếu hai object bằng nhau theo `equals()` thì hash của chúng phải bằng nhau**. Chiều ngược lại không đúng: hai object khác nhau có thể có cùng hash. Vì vậy việc các object in cùng hậu tố hex không chứng minh chúng bằng nhau.

Trong ví dụ `FixedHash`, tất cả các instance đều có hash `42` nhưng `equals()` mặc định vẫn chỉ công nhận cùng tham chiếu. Đây là lựa chọn hash chất lượng rất kém cho collection băm nếu có nhiều đối tượng, vì gây nhiều collision, nhưng **không vi phạm hợp đồng `equals()/hashCode()`**. Ta cố ý dùng nó để tách bạch hash với địa chỉ.

Một lỗi khác: lớp ghi đè `equals()` theo các trường nhưng quên ghi đè `hashCode()` phù hợp. Hai object có thể equals nhau mà hash khác nhau, vi phạm hợp đồng và khiến `HashMap`/`HashSet` xử lý tra cứu không đúng. Đừng lấy hậu tố `@...` để quyết định equality; hãy thiết kế `equals()/hashCode()` đúng vai trò.

## 7. Vì sao giá trị hậu tố không phù hợp làm mã định danh lâu dài?

Giả sử ứng dụng ghi một log:

```text
RequestContext@6d03e736 started
```

Một kỹ sư khác tìm chuỗi `6d03e736` và muốn dùng làm ID để gắn các bản ghi trong database hoặc theo dõi xuyên nhiều lần khởi động. Điều đó không an toàn vì:

- **Không đảm bảo không trùng:** một giá trị hash 32 bit có thể bị nhiều object sử dụng.
- **Không cam kết ổn định qua tiến trình hoặc lần chạy khác:** khởi động lại JVM không tạo ra phép ánh xạ định danh có hợp đồng duy trì.
- **Có thể bị ghi đè:** lớp thay đổi cách tính `hashCode()` sau khi cập nhật thư viện sẽ khiến hậu tố đổi ý nghĩa.
- **Không gắn với lifecycle:** không có lời hứa rằng hậu tố cho biết object còn sống, đã được GC thu gom hay đã bị dịch chuyển vùng nhớ.
- **Không mô tả nội dung:** người đọc không thể biết các trường quan trọng của một yêu cầu chỉ từ `@6d03e736`.

Nếu cần theo dõi yêu cầu qua log, hãy tạo request ID/correlation ID có quản lý, ví dụ một UUID theo từng request, rồi truyền nó qua các bước xử lý. Nếu cần ID nghiệp vụ lâu dài, dùng khóa nghiệp vụ hoặc định danh được hệ thống cấp. Nếu chỉ cần đọc trạng thái object để debug, ghi đè `toString()` theo những trường hữu ích và loại trừ thông tin nhạy cảm.

**Lưu ý bảo mật:** Tránh đưa mật khẩu, token, khóa truy cập hoặc dữ liệu cá nhân nhạy cảm vào `toString()`. Nhiều framework hoặc logger có thể gọi `toString()` khi trình bày đối tượng; một chuỗi dễ đọc không đáng để làm lộ dữ liệu.

## 8. Nếu lớp ghi đè toString(), còn dấu @ thì sao?

Công thức `ClassName@hex` là cách biểu diễn **mặc định của `Object.toString()`**, không phải cấu trúc bắt buộc của mọi chuỗi Java.

Một lớp có thể viết:

```java
@Override
public String toString() {
    return "User{id='" + id + "', status='" + status + "'}";
}
```

*Đây chỉ là phần thân của phương thức nằm trong một lớp có các trường `id` và `status`; không phải chương trình độc lập.*

Khi đó chuỗi in ra do phương thức ghi đè quyết định. Nó có thể không có dấu `@`, hoặc có dấu `@` như một phần giá trị bình thường, chẳng hạn địa chỉ email `a@example.com`. Vì vậy **đừng viết parser đoán địa chỉ hoặc mã băm dựa vào ký tự `@` trong mọi `toString()`**.

Ngay cả với đối tượng chưa ghi đè `toString()`, ký tự `@` chỉ phân cách tên lớp và biểu diễn hash; thông tin đó thường không giúp tái tạo object hay nội dung dữ liệu. Nếu hệ thống cần định dạng máy đọc được, dùng serializer/DTO với schema rõ ràng thay cho dựa vào chuỗi debug.

## 9. Một số hiểu nhầm và cách sửa

### Hiểu nhầm 1: “@ đi sau tên lớp nên chắc là địa chỉ”

Sự thật: `Object.toString()` kết hợp `getClass().getName()`, `@` và `Integer.toHexString(hashCode())`. Định dạng trông giống địa chỉ chỉ là cách trình bày hex.

### Hiểu nhầm 2: “Hai object khác nhau luôn có hậu tố khác nhau”

Sự thật: hash collision là hợp lệ. Ví dụ `FixedHash` đã chứng minh hai đối tượng mới khác nhau có thể cùng `@2a`.

### Hiểu nhầm 3: “Cùng hậu tố thì equals() bằng nhau”

Sự thật: cùng hash không suy ra `equals()`. Mặt khác, hai object `equals()` nhau bắt buộc có cùng `hashCode()` theo hợp đồng.

### Hiểu nhầm 4: “Nếu dùng System.identityHashCode() thì sẽ ra địa chỉ thật”

Sự thật: `identityHashCode` là mã băm theo semantics identity, nhưng API vẫn không hứa trả về địa chỉ bộ nhớ hoặc giá trị không bao giờ trùng.

### Hiểu nhầm 5: “Đổi toString() cũng thay hashCode()”

Sự thật: hai phương thức độc lập về trách nhiệm. Ghi đè `toString()` thay cách biểu diễn, không tự thay cách `equals()` hoặc `hashCode()` hoạt động. Ngược lại, ghi đè `hashCode()` có thể làm hậu tố của **Object.toString() mặc định** đổi theo.

### Hiểu nhầm 6: “Số hex cho biết thứ tự tạo object”

Sự thật: `hashCode()` không có hợp đồng tăng dần theo lần cấp phát. So sánh độ lớn của hai hậu tố không xác định object nào được tạo trước.

### Hiểu nhầm 7: “Có thể dùng chuỗi @hex làm key cho HashMap”

Về mặt cú pháp có thể đặt bất kỳ chuỗi nào làm key, nhưng dùng `toString()` mặc định làm định danh logic là thiết kế thiếu bảo đảm. Hãy chọn các trường identity/value thực sự, dùng kiểu khóa bất biến và triển khai `equals()/hashCode()` nhất quán.

### Hiểu nhầm 8: “Có thể nhìn một log @hex rồi xác định object ở địa chỉ nào”

Không. Định dạng `@hex` không cung cấp API tra cứu địa chỉ hay giải tham chiếu. Phân tích heap hoặc bộ nhớ thực tế cần công cụ chẩn đoán JVM thích hợp; ngay cả những công cụ ấy có khái niệm địa chỉ/ID phụ thuộc snapshot và runtime, không thể tự suy từ `Object.toString()`.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `Person@2a` với `Object.toString()` mặc định có nghĩa `Person` được lưu tại địa chỉ RAM `0x2a` không?  
**Đáp án:** Không. `2a` là biểu diễn hex của giá trị `hashCode()`, tức số thập phân `42`.

**Câu 2.** Hai object mới khác nhau có cùng chuỗi `SomeClass@2a` được không?  
**Đáp án:** Có. Nếu cùng tên lớp và cùng hash, `Object.toString()` mặc định tạo ra cùng chuỗi dù hai tham chiếu khác nhau.

**Câu 3.** Nếu chỉ ghi đè `hashCode()` thành `return 255;` nhưng vẫn dùng `Object.toString()`, hậu tố là gì?  
**Đáp án:** `@ff` vì `255` trong hệ 16 là `ff`.

**Câu 4.** `System.identityHashCode(obj)` có bảo đảm trả địa chỉ bộ nhớ hoặc một ID duy nhất không?  
**Đáp án:** Không. Nó bỏ qua override `hashCode()` để lấy identity hash, nhưng vẫn là hash chứ không phải API địa chỉ.

**Câu 5.** Hai object cùng hash có chắc bằng nhau theo `equals()`?  
**Đáp án:** Không. Cùng hash chỉ là điều kiện bắt buộc của các object bằng nhau; hash collision giữa các object không bằng nhau được phép.

**Câu 6.** Tại sao `Arrays.toString(array)` thích hợp hơn `array.toString()` để xem nội dung mảng một chiều?  
**Đáp án:** `array.toString()` dùng cách hiển thị mặc định kiểu `[I@...`, còn `Arrays.toString` hiển thị từng phần tử.

**Câu 7.** Một lớp tự ghi đè `toString()` thành `"User(email=a@example.com)"` thì ký tự `@` có còn mang nghĩa hash không?  
**Đáp án:** Không. Khi phương thức ghi đè tự quyết định định dạng, dấu `@` có thể chỉ là một ký tự văn bản thông thường.

**Câu 8.** Khi cần gắn ID xuyên nhiều request và nhiều lần khởi động JVM, có nên lưu phần sau `@` không?  
**Đáp án:** Không. Dùng định danh có hợp đồng rõ ràng, chẳng hạn request ID, UUID hoặc khóa nghiệp vụ phù hợp.

**Ghi nhớ:** Với `Object.toString()` mặc định, phần sau dấu `@` là **`Integer.toHexString(hashCode())`**, hoàn toàn không có cam kết đại diện cho địa chỉ RAM, thứ tự tạo object hay một định danh duy nhất. Muốn so object, dùng hợp đồng `equals()/hashCode()`; muốn kiểm tra cùng instance, dùng `==`; muốn quan sát dữ liệu, xây dựng `toString()` có ý nghĩa hoặc dùng công cụ chẩn đoán đúng mục đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-004">Thiết kế chuỗi ngắn gọn, có tên trường và đủ thông tin</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế `toString()` ngắn gọn, có tên trường và đủ thông tin

## 1. Mục tiêu của biểu diễn `toString()`

Trong Java, `Object.toString()` tạo ra biểu diễn dạng `String` của một đối tượng. Khi lớp **không ghi đè**, cách cài đặt mặc định thường cho chuỗi dạng `TênLớp@maBamHex`, chẳng hạn `Order@3e25a5`; phần mã băm không bảo đảm duy nhất và không có ý nghĩa nghiệp vụ rõ ràng. Đối với đối tượng chứa nhiều trạng thái, người đọc log khó biết đơn hàng nào, trạng thái gì, các thuộc tính quan trọng ra sao.

Một `toString()` tốt nên trả lời nhanh: **Đây là loại đối tượng gì? Những trường nào giúp nhận diện và hiểu trạng thái hiện tại?** Chuỗi nên:

- **Ngắn gọn:** tránh in mọi trường, toàn bộ đồ thị đối tượng hoặc dữ liệu quá dài.
- **Có tên trường:** thể hiện rõ `id=...`, `status=...`, `count=...` thay vì dãy giá trị không nhãn khó hiểu.
- **Đủ thông tin:** ưu tiên mã nhận diện, trạng thái, số lượng hoặc thuộc tính thiết yếu để hiểu đối tượng khi đọc log.
- **Dễ đọc và nhất quán:** cùng một lớp dùng cùng quy ước tên, dấu phân cách và thứ tự trường.
- **An toàn:** không làm lộ mật khẩu, token, dữ liệu cá nhân hoặc nội dung không được phép ghi log; không chạy đệ quy vô hạn, không phát sinh tác dụng phụ đáng kể.

Ví dụ so sánh:

~~~text
Khó đọc:
Order@5f12a23
Order(ORD-104, PAID, 2, 120.50)

Dễ đọc:
Order{orderId='ORD-104', status=PAID, itemCount=2, total=120.50}
~~~

Trong ví dụ tốt, `Order` nói rõ kiểu đối tượng, các tên trường làm rõ ý nghĩa của từng giá trị và có thể nhận biết nhanh trạng thái đơn hàng. Dấu `{}` hay `[]` là **quy ước trình bày**, không phải một chuẩn ngôn ngữ bắt buộc. Quan trọng là tính thống nhất và dễ hiểu.

## 2. Hợp đồng `Object.toString()` và ý nghĩa của ghi đè

Trong Java, mọi lớp đều có thể ghi đè phương thức:

~~~java
@Override
public String toString() {
    return "...";
}
~~~

Đây là **đoạn minh họa cú pháp**, chưa phải chương trình chạy độc lập. `toString()` là phương thức không nhận tham số, trả về `String` và thường được gọi **ngầm** trong các thao tác:

~~~java
System.out.println(order);     // println(Object) sử dụng biểu diễn String.
String text = String.valueOf(order);
String message = "order=" + order;
~~~

Các dòng trên chỉ minh họa trong phạm vi đã có biến `order`; chúng không phải tệp độc lập. Nếu `order` là `null`, `String.valueOf(order)` trả về chuỗi `"null"`. Tuy nhiên, gọi trực tiếp `order.toString()` sẽ ném `NullPointerException` trong trường hợp `order == null`.

Những điểm quan trọng:

- `toString()` **không phải** `equals()` hoặc `hashCode()`; thay đổi định dạng `toString()` không tự thay đổi ngữ nghĩa bằng nhau, mã băm hay danh tính đối tượng.
- Tài liệu `Object.toString()` khuyến nghị cung cấp chuỗi ngắn gọn nhưng giàu thông tin, giúp người dùng dễ đọc.
- Nếu ứng dụng cần dữ liệu cho máy xử lý, truyền qua mạng, lưu cơ sở dữ liệu, ký số hay kiểm tra tính tương thích phiên bản, hãy dùng **định dạng và API chuyên dụng**, ví dụ JSON serializer hoặc DTO phù hợp. Đừng biến kết quả `toString()` thành giao thức dữ liệu ổn định ngoài ý định thiết kế.
- Không có hợp đồng chung bắt buộc mọi lớp phải chọn chính xác một kiểu dấu ngoặc, thứ tự trường hay định dạng ngày giờ. Hãy chọn phong cách nhất quán trong module.

## 3. Chọn đúng trường để hiển thị

Không nên tự động đưa **tất cả** trường vào `toString()`. Hãy chia nhóm để quyết định:

| Nhóm trường | Ví dụ | Quyết định thường phù hợp |
| --- | --- | --- |
| Nhận diện hữu ích | `orderId`, `taskId`, mã loại | Thường nên có khi được phép hiển thị |
| Trạng thái chính | `status`, `active`, `phase` | Thường nên có |
| Số liệu tổng hợp | `itemCount`, `total`, `size` | Thường hữu ích hơn cả danh sách dài |
| Dữ liệu có thể rất lớn | `payload`, `items`, `stackFrames` | Hiển thị số lượng, giới hạn độ dài hoặc lược bỏ |
| Thông tin nhạy cảm | `password`, `accessToken`, `sessionSecret`, thông tin cá nhân | Không đưa giá trị thật vào chuỗi; có thể bỏ qua hoặc che |
| Tham chiếu lồng nhau | `parent`, `children`, `owner` | Chỉ in một thuộc tính tóm tắt hoặc mã định danh; tránh đệ quy |
| Thông tin kỹ thuật ít có ích | Cache nội bộ, cờ đồng bộ, đối tượng service | Bỏ qua nếu không giúp chẩn đoán |

**“Đủ thông tin” không đồng nghĩa “đủ mọi trường”.** Một thông tin nên có khi nó giúp phân biệt sự kiện hoặc hiểu trạng thái cần thiết của đối tượng. Nếu toàn bộ collection có hàng nghìn phần tử, `itemCount=1000` thường phù hợp cho log hơn danh sách toàn bộ.

Ví dụ mục tiêu đối với `Order`:

~~~text
Order{orderId='ORD-104', status=PAID, itemCount=2, total=120.50}
~~~

Nếu `customerEmail` là trường nhạy cảm, tránh in nó trong `toString()` chỉ vì có sẵn trong đối tượng. Ngay cả mã ID cũng có thể nhạy cảm tùy hệ thống và chính sách log; hãy lựa chọn theo dữ liệu thực tế.

## 4. Ví dụ Java 17+ chạy độc lập

Ví dụ dưới đây dùng `StringJoiner` để xây dựng chuỗi dễ đọc, in một vài thuộc tính thiết yếu và cố ý không đưa `customerEmail` vào biểu diễn. Nó cũng minh họa cách hiển thị mảng lồng nhau, `null`, record và đối tượng có tham chiếu vòng.

Tạo tệp **`ToStringDesignDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class ToStringDesignDemo {
    static final class Order {
        private final String orderId;
        private final String customerEmail; // Không ghi vào toString().
        private final String status;
        private final int itemCount;
        private final BigDecimal total;

        Order(String orderId, String customerEmail, String status,
              int itemCount, BigDecimal total) {
            this.orderId = Objects.requireNonNull(orderId);
            this.customerEmail = Objects.requireNonNull(customerEmail);
            this.status = Objects.requireNonNull(status);
            this.itemCount = itemCount;
            this.total = Objects.requireNonNull(total);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", "Order{", "}")
                    .add("orderId='" + orderId + "'")
                    .add("status=" + status)
                    .add("itemCount=" + itemCount)
                    .add("total=" + total.toPlainString())
                    .toString();
        }
    }

    static final class Node {
        private final String name;
        private Node next;

        Node(String name) {
            this.name = name;
        }

        void linkTo(Node next) {
            this.next = next;
        }

        @Override
        public String toString() {
            String nextName = next == null ? "null" : next.name;
            return "Node{name='" + name + "', nextName='"
                    + nextName + "'}";
        }
    }

    record Size(int width, int height) {}

    public static void main(String[] args) {
        Order order = new Order(
                "ORD-104",
                "example@private.invalid",
                "PAID",
                2,
                new BigDecimal("120.50"));

        System.out.println(order);

        Node first = new Node("A");
        Node second = new Node("B");
        first.linkTo(second);
        second.linkTo(first);
        System.out.println(first);

        Object[] nested = {"batch", new int[] {1, 2}};
        System.out.println("nested=" + Arrays.deepToString(nested));

        Size size = new Size(3, 5);
        System.out.println("record=" + size);

        String missing = null;
        System.out.println("optional="
                + Objects.toString(missing, "<none>"));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**:

~~~bash
javac ToStringDesignDemo.java
java ToStringDesignDemo
~~~

Đầu ra xác định:

~~~text
Order{orderId='ORD-104', status=PAID, itemCount=2, total=120.50}
Node{name='A', nextName='B'}
nested=[batch, [1, 2]]
record=Size[width=3, height=5]
optional=<none>
~~~

### Giải thích từng dòng

1. `Order.toString()` có **tên lớp, tên trường, dấu phân cách và thứ tự rõ ràng**. Trường `customerEmail` vẫn tồn tại trong đối tượng nhưng không xuất hiện trong chuỗi. `BigDecimal.toPlainString()` duy trì cách biểu diễn thập phân `120.50` của giá trị đã khởi tạo, không phát sinh dạng khoa học trong ví dụ.
2. Hai `Node` liên kết thành vòng `A -> B -> A`. `Node.toString()` chỉ đọc `next.name` chứ **không gọi `next.toString()`**, nên không đệ quy qua liên kết vòng. Đầu ra cho biết nút kế tiếp mà không phải in cả đồ thị.
3. `Arrays.deepToString(nested)` cho biểu diễn nội dung mảng lồng nhau; `Arrays.toString(nested)` thông thường sẽ không in sâu nội dung `int[]` theo cách tương tự. Với cấu trúc mảng có tham chiếu vòng, `deepToString` có quy tắc đánh dấu vòng của mảng; điều đó không đồng nghĩa mọi kiểu đối tượng lồng nhau đều được xử lý vòng tự động.
4. Java `record` tự tạo `toString()` theo các thành phần, nên `Size(3, 5)` được in dưới dạng `Size[width=3, height=5]`.
5. `Objects.toString(missing, "<none>")` tạo chuỗi dự phòng rõ nghĩa khi giá trị là `null`. Dạng trình bày `<none>` là lựa chọn của ví dụ, không phải giá trị bắt buộc của Java.

Chương trình **không in ra** mã băm danh tính, giá trị ngẫu nhiên, thời gian hiện tại hoặc thứ tự duyệt không xác định, vì vậy các dòng stdout trên không phụ thuộc vào lần chạy.

## 5. Cách định dạng ngắn gọn và nhất quán

Có nhiều cách hợp lệ để tạo biểu diễn, miễn dễ đọc và an toàn.

### A. Ghép chuỗi trực tiếp cho vài trường

~~~java
// Minh họa bên trong lớp Customer, có trường id và status.
@Override
public String toString() {
    return "Customer{id='" + id + "', status=" + status + "}";
}
~~~

Cách này ngắn và phù hợp với vài trường. Java biên dịch phép nối chuỗi theo cơ chế hiệu quả cho nhiều tình huống thường gặp; không cần mặc định viết `StringBuilder` chỉ để tối ưu một chuỗi nhỏ.

### B. `StringJoiner` khi cần nhiều trường

~~~java
// Minh họa bên trong lớp Job, có id, state và retries.
@Override
public String toString() {
    return new java.util.StringJoiner(", ", "Job{", "}")
            .add("id='" + id + "'")
            .add("state=" + state)
            .add("retries=" + retries)
            .toString();
}
~~~

`StringJoiner` giúp thống nhất dấu phẩy, tiền tố và hậu tố; thêm hoặc bỏ trường dễ hơn mà ít lỗi dấu phân cách. Ví dụ trong mục 4 dùng chính cơ chế này.

### C. `StringBuilder` khi có nhánh điều kiện hoặc vòng lặp cần kiểm soát

~~~java
// Minh họa phương thức trong lớp Response, với statusCode, requestId và warning.
@Override
public String toString() {
    StringBuilder result = new StringBuilder("Response{");
    result.append("statusCode=").append(statusCode);
    result.append(", requestId='").append(requestId).append("'");
    if (warning != null) {
        result.append(", warning='").append(warning).append("'");
    }
    return result.append('}').toString();
}
~~~

Cách này hữu ích khi cần thêm trường theo điều kiện. Tuy vậy, **việc ẩn trường có giá trị `null` nên là lựa chọn có chủ ý**. Nếu `warning=null` có ý nghĩa phân biệt “không có cảnh báo” với “không biết”, quyết định hiển thị cần nhất quán trong nhóm sử dụng.

### D. Record

~~~java
record Point(int x, int y) {}
~~~

Đây là **đoạn khai báo minh họa**; record đã có `toString()` sinh tự động và thường cho dạng `Point[x=1, y=2]` khi tạo `new Point(1, 2)`. Record có ích cho dữ liệu nhỏ, rõ nghĩa và không có thành phần nhạy cảm. Nhưng tự động hiển thị **mọi thành phần** có thể không phù hợp nếu record chứa bí mật hoặc object graph lớn; lúc đó cần ghi đè theo nhu cầu.

Các đoạn trong mục 5 là **mẫu thay thế**, không phải các phần phải dán liền nhau vào cùng một lớp.

## 6. Đặt tên trường, định dạng giá trị và xử lý `null`

### Dùng tên trường đúng ý nghĩa

So sánh:

~~~text
Không rõ: Transfer{A1, B9, 350}
Rõ hơn: Transfer{sourceAccount='A1', targetAccount='B9', amount=350}
~~~

Khi đọc log sau này, người vận hành có thể hiểu vai trò mỗi giá trị mà không phải tìm ngay khai báo lớp. Tránh các nhãn quá mơ hồ như `x`, `v1`, `data` nếu miền nghiệp vụ có tên rõ hơn.

### Xử lý `null` có chủ đích

Khi một trường có thể là `null`:

- `String.valueOf(value)` và `Objects.toString(value)` tạo chuỗi `"null"` cho đối tượng `null`.
- `Objects.toString(value, defaultText)` cho phép chọn chuỗi thay thế như `"<unset>"` hoặc `"<none>"`.
- Phép nối chuỗi `"field=" + value` không tự ném `NullPointerException` chỉ vì `value == null`; gọi `value.toString()` trực tiếp thì có.

Nếu `null` nghĩa là “chưa thiết lập”, nên viết `status=<unset>` hoặc `status=null` theo quy ước của nhóm, miễn người đọc hiểu được. Không biến `null` thành chuỗi rỗng khi điều đó che mất thông tin chẩn đoán quan trọng.

### Dùng biểu diễn số, ngày giờ và chuỗi nhất quán

- Với số tiền, chọn cách trình bày chính xác theo miền nghiệp vụ, ví dụ `BigDecimal.toPlainString()` cho một giá trị thập phân cần tránh ký hiệu khoa học.
- Với thời gian, nếu cần hiển thị thời điểm, nên ưu tiên dạng rõ múi giờ/offset như `Instant` hoặc `OffsetDateTime` thay vì một thời điểm mơ hồ.
- Trường `String` có thể thêm dấu nháy để phân biệt chuỗi với số hoặc giá trị `null`. Nếu dữ liệu có dấu nháy, xuống dòng hay ký tự điều khiển, cân nhắc **escape/truncate** để log không bị nhầm hoặc bị chèn dòng mới. Dấu nháy đơn quanh chuỗi trong ví dụ chỉ để người đọc dễ quan sát; ví dụ không triển khai thuật toán escape hoàn chỉnh.
- Với dữ liệu dài, chọn hiển thị `length=...`, `size=...` hoặc một bản tóm tắt có giới hạn, theo quy định bảo mật và vận hành.

`toString()` nhằm phục vụ con người; đừng kỳ vọng đầu ra luôn đúng chuẩn JSON hoặc có thể parse đáng tin từ dấu phân cách thủ công.

## 7. Mảng, collection và cấu trúc lồng nhau

Một lỗi dễ thấy là nối mảng trực tiếp:

~~~java
int[] numbers = {1, 2};
String notUseful = "numbers=" + numbers;
String useful = "numbers=" + java.util.Arrays.toString(numbers);
~~~

Đây là **đoạn minh họa**. `notUseful` có dạng tên kiểu mảng kèm mã băm theo danh tính, ví dụ `numbers=[I@...`; hậu tố không cố định. `useful` là `numbers=[1, 2]`. Với mảng tham chiếu lồng nhau không có vòng, `Arrays.deepToString(...)` giúp in nội dung sâu.

Với `List`, `Map` hoặc tập đối tượng lồng nhau, đừng mặc định gọi `toString()` của cả cấu trúc nếu kích thước lớn hoặc có tham chiếu vòng. Các chuỗi có thể quá dài và có thể kích hoạt các phương thức `toString()` của từng phần tử, gây lộ dữ liệu hoặc đệ quy ngoài dự kiến.

Ví dụ chọn mức tóm tắt:

~~~text
Ít hữu ích: Batch{items=[hàng nghìn đối tượng với nội dung dài ...]}
Phù hợp hơn: Batch{batchId='B-22', itemCount=3200, status=RUNNING}
~~~

**Về vòng tham chiếu:** Hai đối tượng `parent` và `child` có thể cùng tham chiếu nhau. Nếu mỗi `toString()` in toàn bộ đối tượng liên kết, lời gọi `parent.toString()` sẽ gọi `child.toString()` rồi quay lại `parent.toString()` và có thể dẫn đến `StackOverflowError`. Biện pháp đơn giản nhất thường là **chỉ in một mã hoặc tên tóm tắt của đối tượng được liên kết**, thay vì mở rộng toàn bộ đồ thị.

Nếu bài toán thực sự cần in đồ thị, hãy tạo **hàm debug riêng** sử dụng tập các đối tượng đã thăm theo danh tính, ví dụ `IdentityHashMap`, và đặt giới hạn số nút/độ sâu. Không bắt mọi `toString()` phải thực hiện duyệt đồ thị.

## 8. An toàn thông tin và khả năng vận hành

`toString()` có thể được gọi ngầm khi log đối tượng, in exception context hoặc gỡ lỗi. Vì vậy nó trở thành điểm **rò rỉ dữ liệu** nếu hiển thị bừa bãi:

- **Không in mật khẩu, secret, API key, access token, private key, session cookie** hoặc dữ liệu xác thực.
- Với thông tin cá nhân (email, số điện thoại, địa chỉ) và dữ liệu nghiệp vụ nhạy cảm, tuân thủ chính sách xử lý log; khi phù hợp chỉ hiển thị một ID được phép hoặc giá trị đã che hợp lệ.
- Không đưa toàn bộ request/response body, tập khách hàng hoặc file đính kèm vào `toString()` mặc định.
- Đừng phụ thuộc vào việc “log hiện tại chưa in đối tượng này”: mã ứng dụng khác có thể gọi `String.valueOf(obj)` hoặc nối `obj` vào chuỗi sau này.
- Cẩn thận với các trường có dữ liệu do người dùng nhập: xuống dòng, ký tự điều khiển hoặc chuỗi quá dài có thể gây nhiễu hoặc làm log khó phân tích. Escape/truncate theo một quy tắc rõ ràng.

Trong ví dụ `Order`, trường `customerEmail` được giữ bên trong nhưng `toString()` chỉ hiển thị `orderId`, `status`, `itemCount` và `total`. Đây là **quyết định thiết kế trong tình huống minh họa**; triển khai thực tế vẫn phải kiểm tra xem từng trường định in có được phép xuất hiện trong log không.

Ngoài ra, `toString()` nên tránh **I/O, truy vấn cơ sở dữ liệu, gọi mạng, tải lazy data, khóa đồng bộ kéo dài hoặc thay đổi trạng thái**. Một phương thức vốn được gọi chỉ để hiển thị không nên bất ngờ làm chậm hệ thống, phát sinh ngoại lệ nghiệp vụ hoặc thay đổi dữ liệu.

## 9. `toString()` không phải nơi định nghĩa tính bằng nhau

Một lỗi thiết kế khác là so sánh hai đối tượng bằng chuỗi:

~~~java
// Sai về mặt thiết kế trong đa số trường hợp:
boolean same = a.toString().equals(b.toString());
~~~

Đây là **đoạn minh họa**, giả định `a` và `b` khác `null`. Chuỗi có thể bỏ qua một số thuộc tính quan trọng, bị rút gọn, che dữ liệu hoặc thay đổi kiểu định dạng theo phiên bản. Hai đối tượng khác nhau có thể có chuỗi giống nhau; hai đối tượng bằng nhau theo nghiệp vụ cũng không nhất thiết có biểu diễn giống hệt nếu `toString()` chứa trường trạng thái ngoài định nghĩa `equals()`.

Để định nghĩa tính bằng nhau theo giá trị, hãy triển khai `equals()` và `hashCode()` theo đúng hợp đồng. Để sắp xếp, dùng `Comparable` hoặc `Comparator`. Để truyền dữ liệu qua API, dùng DTO và serializer phù hợp. `toString()` chỉ tập trung vào **biểu diễn dễ hiểu cho con người**.

Tương tự, không nên dùng `toString()` làm khóa ổn định trong `Map` hoặc làm ID lâu dài: thay đổi việc hiển thị (đổi tên trường, thêm dấu nháy, che dữ liệu) không nên làm thay đổi danh tính logic của một đối tượng.

## 10. Trước và sau khi cải tiến

Giả sử một lớp `Job` có ID, trạng thái, số lần thử, một payload dài, token bảo mật và liên kết đến Job cha.

**Thiết kế khó sử dụng:**

~~~text
Job@4317a9
Job(72, RUNNING, 3, [payload rất dài], token=..., parent=...)
~~~

Chuỗi đầu không có ngữ cảnh; chuỗi thứ hai không có tên trường, có thể dài vô hạn và nguy hiểm do token hoặc liên kết vòng.

**Thiết kế hướng đến đọc log:**

~~~text
Job{id='J-72', state=RUNNING, attempts=3, payloadBytes=8192, parentId='J-50'}
~~~

Giá trị có tên, đủ tóm tắt trạng thái công việc, dùng `payloadBytes` thay payload đầy đủ và dùng `parentId` thay việc gọi `parent.toString()`. Không hiển thị token. Người đọc cũng dễ so sánh hai lần log nếu thứ tự và tên trường ổn định.

Không bắt buộc phải hiển thị đúng 5 trường như ví dụ: thiết kế theo những câu hỏi chẩn đoán chính của chính đối tượng và độ nhạy cảm của từng thuộc tính.

## 11. Checklist khi tự triển khai và review

1. **Nhận diện:** chuỗi có tên kiểu hoặc ngữ cảnh đủ để biết đang nhìn đối tượng nào không?
2. **Tên trường:** các giá trị có nhãn dễ hiểu như `status`, `itemCount`, `orderId` hay chỉ có một dãy giá trị?
3. **Đủ dùng:** có những thuộc tính giúp chẩn đoán trạng thái quan trọng, nhưng không kéo theo dữ liệu thừa?
4. **Ngắn gọn:** collection, blob, text hoặc mảng lớn đã được giới hạn hoặc tóm tắt?
5. **Bảo mật:** chuỗi không chứa password, token hoặc dữ liệu cá nhân/trường nghiệp vụ cấm ghi log?
6. **Xử lý lồng nhau:** có nguy cơ `toString()` gọi lẫn nhau qua tham chiếu vòng không? Mảng có được định dạng phù hợp?
7. **`null`:** các trường tùy chọn được hiển thị nhất quán, không gây `NullPointerException` do gọi trực tiếp trên giá trị null?
8. **Độ ổn định:** tên trường và dấu phân cách nhất quán, không chứa thứ tự ngẫu nhiên hoặc mã băm vô nghĩa khi không cần?
9. **Tác dụng phụ:** không thay đổi đối tượng, không thực hiện I/O hoặc nghiệp vụ phức tạp?
10. **Đúng vai trò:** không bị lạm dụng như cơ chế serialize, ID lâu dài hay thay thế `equals()`/`hashCode()`?

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `Person{An, 25}` khó dùng hơn `Person{name='An', age=25}` khi đọc log?

**Đáp án:** Chuỗi thứ hai có tên trường, người đọc hiểu ngay ý nghĩa dữ liệu và không phải đoán vị trí tham số.

**Câu 2.** Có nên in mọi phần tử trong một `List<Order>` hàng trăm nghìn mục?

**Đáp án:** Thường không. Nên dùng số lượng hoặc tóm tắt có giới hạn, tránh chuỗi quá lớn, chi phí tạo chuỗi và khả năng rò rỉ dữ liệu.

**Câu 3.** Một đối tượng `Employee` có `manager` trỏ đến đối tượng `Employee` khác, và hai đối tượng có thể trỏ ngược nhau. `toString()` nên làm gì?

**Đáp án:** Chỉ in mã hoặc tên tóm tắt của `manager`, hoặc sử dụng một hàm debug có theo dõi danh tính và giới hạn độ sâu. Không đệ quy vô hạn qua `manager.toString()`.

**Câu 4.** `record` có bảo đảm không lộ dữ liệu qua `toString()` không?

**Đáp án:** Không. Record tự sinh `toString()` từ các thành phần; nếu chúng chứa dữ liệu nhạy cảm, cần thiết kế thành phần hoặc ghi đè biểu diễn cho phù hợp.

**Câu 5.** Có nên lưu `order.toString()` làm mã đơn hàng trong cơ sở dữ liệu?

**Đáp án:** Không. Chọn mã nghiệp vụ riêng như `orderId`; biểu diễn `toString()` có thể thay đổi khi cải tiến việc gỡ lỗi, thêm/bớt trường hoặc áp dụng quy tắc bảo mật.

**Ghi nhớ:** `toString()` tốt giúp **đọc nhanh — hiểu đúng — không lộ dữ liệu**. Chọn ít trường nhưng có ý nghĩa, đặt tên rõ ràng, thể hiện `null` và cấu trúc lồng nhau có chủ đích, tránh in sâu đồ thị hoặc dữ liệu nhạy cảm. Để `toString()` là công cụ biểu diễn cho con người, không biến nó thành hợp đồng dữ liệu hay danh tính đối tượng.

## Tham khảo

- [Java SE 17 — `Object.toString()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#toString())
- [Java SE 17 — `StringJoiner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/StringJoiner.html)
- [Java SE 17 — `Objects.toString()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-005">Không đưa mật khẩu, token hoặc dữ liệu nhạy cảm vào biểu diễn</a>

<details>
<summary>Xem chi tiết</summary>

# Không đưa mật khẩu, token hoặc dữ liệu nhạy cảm vào biểu diễn `toString()`

## 1. Vì sao `toString()` là điểm dễ rò rỉ thông tin?

`Object.toString()` trả về một chuỗi biểu diễn đối tượng. Các lớp thường ghi đè `toString()` để giúp lập trình viên đọc log, thông báo lỗi hoặc kết quả gỡ lỗi nhanh hơn. Tuy nhiên **chuỗi biểu diễn có thể rời khỏi phạm vi đang kiểm soát**: nó có thể được ghi vào log, chuyển tới dịch vụ quan sát hệ thống, lưu trong báo cáo lỗi, xuất hiện trong màn hình hỗ trợ hoặc được chia sẻ khi xử lý sự cố.

Ví dụ, đối tượng đăng nhập có thể có các trường `accountId`, `password` và `accessToken`. Một `toString()` liệt kê mọi trường sẽ vô tình tiết lộ thông tin xác thực khi ai đó chỉ định in đối tượng để theo dõi luồng xử lý. Mật khẩu và token có thể cho phép truy cập tài khoản hoặc hệ thống khi bị lộ; ngay cả log có giới hạn quyền cũng có thể bị sao chép, lưu lâu hoặc truy cập bởi nhiều thành phần hơn phạm vi xử lý bí mật ban đầu.

**Nguyên tắc mặc định:** `toString()` phục vụ **chẩn đoán** chứ không phải xuất toàn bộ trạng thái. Chỉ đưa vào những thuộc tính **đã được xác định là an toàn để ghi log**. Không bao giờ đưa mật khẩu, secret, khóa riêng hoặc giá trị token truy cập vào chuỗi biểu diễn.

Cách định nghĩa một biểu diễn tốt:

- Người đọc nhận ra **loại đối tượng** và **ngữ cảnh đủ để chẩn đoán**.
- Chuỗi không chứa thông tin xác thực hoặc dữ liệu cá nhân vượt quá mục đích được phép.
- Nếu lớp được bổ sung trường nhạy cảm về sau, biểu diễn không tự động lộ trường mới.
- Biểu diễn có định dạng ổn định, gọn, dễ hiểu và không chứa dữ liệu đầu vào chưa kiểm soát có thể phá cấu trúc log.

## 2. Những loại dữ liệu không nên in trong `toString()`

Tùy ứng dụng và phân loại dữ liệu, các nhóm sau thường cần được loại khỏi biểu diễn:

| Nhóm dữ liệu | Ví dụ | Rủi ro khi xuất vào biểu diễn |
| --- | --- | --- |
| Mật khẩu, PIN, OTP và mã khôi phục | `password`, `pin`, `oneTimeCode` | Có thể dùng để xác thực hoặc chiếm quyền truy cập |
| Token xác thực và phiên | Access token, refresh token, session cookie, bearer token | Có thể bị tái sử dụng để truy cập phiên hoặc API |
| Khóa và bí mật kỹ thuật | API key, client secret, private key, connection-string chứa mật khẩu | Cho phép truy cập dịch vụ, ký/giải mã hoặc kết nối trái phép |
| Header/query nhạy cảm | `Authorization`, `Cookie`, URL có token trong query string | Giá trị bí mật có thể lọt qua log HTTP hoặc exception |
| Thông tin cá nhân | Email, điện thoại, địa chỉ, giấy tờ, dữ liệu tài chính hoặc sức khỏe | Có thể xâm phạm riêng tư hoặc vượt quyền sử dụng dữ liệu |
| Nội dung yêu cầu/phản hồi | JSON chứa thông tin tài khoản, hồ sơ, thông tin thanh toán | Có thể vô tình kéo theo hàng loạt trường nhạy cảm |

Không phải mọi `id`, email hay số tham chiếu đều được phép xuất. Một mã định danh có thể là thông tin cá nhân hoặc bí mật trong ngữ cảnh cụ thể. Nếu cần kết nối các sự kiện, ưu tiên **correlation ID được thiết kế riêng cho việc quan sát hệ thống**, không mang khả năng truy cập và đã được duyệt để ghi log.

Đừng cho rằng chỉ mật khẩu thô mới là bí mật. Hash mật khẩu, mã xác minh, token đã hết hạn, khóa thử nghiệm, các giá trị “một phần” hoặc tài liệu nhận dạng có thể vẫn nhạy cảm. Quyết định chính sách phải dựa trên mục đích sử dụng và mức độ rủi ro, không dựa đơn thuần vào tên trường.

## 3. `toString()` có thể bị gọi gián tiếp

Một lập trình viên có thể không viết `object.toString()` nhưng vẫn làm biểu diễn được sử dụng:

~~~java
System.out.println(object);              // Gọi biểu diễn chuỗi đối tượng.
String message = "Request: " + object;    // Nối chuỗi sẽ dùng biểu diễn.
String text = String.format("%s", object);
String display = String.valueOf(object);
~~~

Đây là những **đoạn minh họa**, giả định biến `object` đã được khai báo. Với một số API logging có tham số, ví dụ lời gọi kiểu `logger.info("request={}", request)` của các thư viện phổ biến, formatter có thể chuyển đối số thành chuỗi khi ghi log. Việc trì hoãn định dạng hoặc kiểm tra mức log có thể ảnh hưởng thời điểm đánh giá, nhưng **không biến `request.toString()` thành an toàn** khi thông điệp thực sự được ghi.

Các luồng khác cũng có thể gián tiếp gọi biểu diễn: tạo chuỗi của exception, ghi giá trị vào thông báo assert, xuất một đối tượng trong trang quản trị hoặc thu thập snapshot phục vụ điều tra sự cố.

**Điều cần tránh là đưa cả đối tượng chứa bí mật vào thông điệp**, nhất là khi không kiểm soát được cách nó được chuyển sang chuỗi. Thay vì ghi `request`, hãy ghi các trường cho phép như loại hành động, kết quả xử lý và correlation ID đã được phân loại.

## 4. Bẫy của `record`: `toString()` được sinh sẵn và chứa mọi component

Từ Java 16, `record` có các phương thức tổng hợp `equals()`, `hashCode()` và `toString()` nếu bạn không tự khai báo. Phương thức `toString()` được sinh biểu diễn tên record cùng các **component** và giá trị tương ứng. Vì thế dùng record cho dữ liệu chứa bí mật vẫn có nguy cơ rò rỉ, dù bạn không viết một dòng `toString()` thủ công nào.

### Ví dụ chạy được: phát hiện nguy cơ nhưng không in giá trị bí mật

Tạo tệp `UnsafeRecordToStringDemo.java`:

~~~java
public class UnsafeRecordToStringDemo {
    record LoginData(String accountId, String password, String accessToken) {}

    public static void main(String[] args) {
        // Chỉ sử dụng chuỗi giả để kiểm tra hành vi, không đưa secret thật vào mã.
        LoginData login = new LoginData(
                "account-demo",
                "example-password",
                "example-token"
        );

        String rendered = login.toString();

        // Chỉ in kết quả kiểm tra, KHÔNG in rendered hoặc các secret.
        System.out.println("contains account: "
                + rendered.contains(login.accountId()));
        System.out.println("contains password: "
                + rendered.contains(login.password()));
        System.out.println("contains token: "
                + rendered.contains(login.accessToken()));
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 UnsafeRecordToStringDemo.java
java UnsafeRecordToStringDemo
~~~

Stdout:

~~~text
contains account: true
contains password: true
contains token: true
~~~

Ví dụ cố ý để record giữ `toString()` mặc định **nhằm phát hiện vấn đề**, nhưng không in ra các giá trị mật khẩu/token. Kết quả `true` cho thấy nếu có mã ghi `System.out.println(login)` hoặc ghép `"login=" + login`, chuỗi biểu diễn sẽ mang các giá trị đó vào đầu ra.

Ngoài `record`, IDE hoặc công cụ sinh mã có thể tạo `toString()` dựa trên toàn bộ field hoặc component. Khi thêm field, chạy lại generator hoặc bật chế độ sinh tự động, cần kiểm tra chính sách loại trừ trường nhạy cảm.

## 5. Chiến lược an toàn hơn: chọn rõ những trường được phép xuất

Cách an toàn khi viết `toString()` cho đối tượng có secret là **allowlist**: chỉ liệt kê thuộc tính được phép hiện ra, thay vì nối tất cả trường rồi cố lọc đi những trường bị cấm.

Ví dụ một phiên xử lý chứa request ID và hai secret. Nếu request ID đã được thiết kế là mã chẩn đoán không nhạy cảm, ta có thể in mã đó và một nhãn cố định mà không sử dụng giá trị secret.

Tạo tệp `SafeSessionToStringDemo.java`:

~~~java
public class SafeSessionToStringDemo {
    record SessionData(String requestId, String password, String accessToken) {
        @Override
        public String toString() {
            return "SessionData[requestId=" + requestId
                    + ", credentials=<redacted>]";
        }
    }

    public static void main(String[] args) {
        SessionData session = new SessionData(
                "req-42",
                "example-password",
                "example-token"
        );

        String representation = session.toString();
        System.out.println(representation);
        System.out.println("contains password: "
                + representation.contains(session.password()));
        System.out.println("contains token: "
                + representation.contains(session.accessToken()));
        System.out.println("same object identity: "
                + (session == session));
    }
}
~~~

~~~bash
javac -encoding UTF-8 SafeSessionToStringDemo.java
java SafeSessionToStringDemo
~~~

Stdout:

~~~text
SessionData[requestId=req-42, credentials=<redacted>]
contains password: false
contains token: false
same object identity: true
~~~

Đây là ví dụ hoàn chỉnh với dữ liệu giả, dùng nhãn `<redacted>` **cố định** để diễn đạt bí mật đã bị loại khỏi biểu diễn. Không xây dựng nhãn bằng cách nối hoặc lấy một phần thực tế của token rồi hiển thị.

Lợi ích của allowlist:

1. Người đọc biết chính xác những trường nào được đưa ra.
2. Các trường nhạy cảm `password` và `accessToken` không được truy cập trong thân `toString()`.
3. Khi thêm một secret mới vào record, `toString()` tùy chỉnh **không tự động xuất secret đó**.
4. Kết quả ngắn, có nghĩa và thuận tiện cho log, nếu `requestId` thực sự đã được phân loại là an toàn.

Trong môi trường thực tế, hãy kiểm tra rằng giá trị `requestId` cũng không chứa thông tin bí mật hoặc chuỗi do người dùng tự đưa vào. Nếu không chắc, hãy loại luôn trường đó hoặc dùng một correlation ID do hệ thống tạo.

**Lưu ý:** Override `toString()` chỉ thay đổi biểu diễn chuỗi. Nó không xóa password/token khỏi đối tượng, không ngăn accessor truy cập, không bảo vệ việc serialize JSON/XML và không tự giải quyết các quy tắc lưu trữ secret. Record mặc định vẫn sinh `equals/hashCode` dựa trên component, trừ khi các phương thức đó được tự cài đặt; chúng có mục đích khác với chính sách log.

## 6. Vì sao che bằng dấu sao chưa đủ nếu bí mật vẫn đi qua log?

Một số cách xử lý “mask” thông thường có thể vẫn để lộ dữ liệu:

~~~java
// Các đoạn sau là phản ví dụ về thiết kế; không nên dùng để ghi secret.
String partiallyVisible = token.substring(0, 6) + "***";
String suffix = "***" + token.substring(token.length() - 4);
String encoded = java.util.Base64.getEncoder()
        .encodeToString(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
~~~

Các đoạn là **fragment minh họa**, giả định `token` là chuỗi khác null, đủ dài ở hai cách cắt. Trong chương trình thật, các phép `substring` còn có thể ném lỗi với token quá ngắn; điều đáng chú ý là:

- Đầu/cuối token có thể vẫn nhạy cảm hoặc có thể hỗ trợ khớp một token cụ thể với hoạt động người dùng.
- Base64 là **mã hóa biểu diễn**, không phải mã hóa bảo mật hay xóa thông tin; có thể đảo ngược.
- Việc băm trực tiếp secret rồi đưa hash vào log vẫn tạo một giá trị tương quan cố định, có thể lộ mối liên hệ giữa sự kiện hoặc hỗ trợ suy đoán nếu không có mô hình đe dọa phù hợp.
- Thay thế mẫu chuỗi toàn cục sau khi secret đã bị ghép vào log rất dễ bỏ sót đường đi, định dạng mới hoặc giá trị nhạy cảm nằm sâu trong đối tượng.

Trong `toString()`, tốt nhất **không tham chiếu đến giá trị secret**. Nếu có yêu cầu rất cụ thể về cách hiển thị trạng thái (ví dụ `configured`/`absent`) thì chỉ làm khi đã được đánh giá là an toàn, bởi ngay cả sự có mặt của thông tin xác thực đôi khi cũng là thông tin nhạy cảm.

Không cần in prefix, suffix, chiều dài token hay trạng thái xác thực nếu mục tiêu chỉ là xác định đã xử lý request nào. Dùng mã chẩn đoán độc lập thay thế.

## 7. Rò rỉ qua object lồng nhau, collection và `Map.toString()`

Bỏ trường secret trực tiếp chưa đủ nếu `toString()` của lớp ngoài **gọi `toString()` của đối tượng con** mà đối tượng đó chứa secret.

Ví dụ:

~~~java
record Credential(String token) {}
record Request(String requestId, Credential credential) {}
~~~

Nếu cả hai giữ `toString()` mặc định, biểu diễn của `Request` chứa biểu diễn của `Credential`, và `Credential` chứa giá trị token. Tương tự, cách viết `return "Request{headers=" + headers + "}";` có thể gọi `Map.toString()` và in những entry như `Authorization` hoặc `Cookie`. `List.toString()` cũng có thể dẫn đến `toString()` của từng phần tử.

### Ví dụ chạy được: chứng minh rò rỉ gián tiếp

Tạo tệp `NestedToStringDemo.java`:

~~~java
public class NestedToStringDemo {
    record Credential(String token) {}
    record UnsafeRequest(String requestId, Credential credential) {}

    record SafeRequest(String requestId, Credential credential) {
        @Override
        public String toString() {
            return "SafeRequest[requestId=" + requestId + "]";
        }
    }

    public static void main(String[] args) {
        Credential credential = new Credential("example-token");
        UnsafeRequest unsafe = new UnsafeRequest("req-9", credential);
        SafeRequest safe = new SafeRequest("req-9", credential);

        // Chỉ in boolean kiểm tra, không xuất chuỗi unsafe.
        System.out.println("nested leak: "
                + unsafe.toString().contains(credential.token()));
        System.out.println("safe excludes nested token: "
                + !safe.toString().contains(credential.token()));
        System.out.println("safe representation: " + safe);
    }
}
~~~

~~~bash
javac -encoding UTF-8 NestedToStringDemo.java
java NestedToStringDemo
~~~

Stdout:

~~~text
nested leak: true
safe excludes nested token: true
safe representation: SafeRequest[requestId=req-9]
~~~

Khi thiết kế đối tượng lồng nhau, hãy kiểm tra mọi vị trí mà biểu diễn lớp ngoài **đưa cả object, collection, header map, URL hoặc payload** vào chuỗi. Một `toString()` an toàn không nên phụ thuộc vào việc mọi đối tượng con đều tự biết ẩn secret.

Nếu cần mô tả collection, có thể chỉ xuất số phần tử hoặc một thống kê được phép, chẳng hạn `itemsCount=3`, thay vì nối `items`. Nếu cần mô tả HTTP request, hãy dùng những metadata đã được chọn như phương thức, route chuẩn hóa hoặc correlation ID; tránh xuất nguyên header, query string và body khi chưa phân loại.

## 8. Ví dụ kiểu class thông thường: chỉ xuất metadata có kiểm soát

Record không phải trường hợp duy nhất. Một lớp POJO cũng nên override `toString()` thủ công nếu nó chứa dữ liệu nhạy cảm và có khả năng xuất hiện trong log.

Tạo tệp `SafeAccountToStringDemo.java`:

~~~java
public class SafeAccountToStringDemo {
    static final class Account {
        private final String correlationId;
        private final String passwordHash;
        private final String recoveryCode;

        Account(String correlationId, String passwordHash, String recoveryCode) {
            this.correlationId = correlationId;
            this.passwordHash = passwordHash;
            this.recoveryCode = recoveryCode;
        }

        @Override
        public String toString() {
            return "Account{correlationId='" + correlationId + "'}";
        }

        boolean representationContainsSecret() {
            String text = toString();
            return text.contains(passwordHash) || text.contains(recoveryCode);
        }
    }

    public static void main(String[] args) {
        Account account = new Account(
                "trace-100",
                "example-hash-value",
                "example-recovery-code"
        );

        System.out.println(account);
        System.out.println("contains secret: "
                + account.representationContainsSecret());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SafeAccountToStringDemo.java
java SafeAccountToStringDemo
~~~

Stdout:

~~~text
Account{correlationId='trace-100'}
contains secret: false
~~~

Lưu ý `passwordHash` vẫn có thể là dữ liệu nhạy cảm và không được xem là an toàn chỉ vì đã được băm. Trong ví dụ này, chuỗi đầu ra chứa **duy nhất** correlation ID giả đã được cho phép. Tên trường bí mật có thể xuất hiện trong mã nguồn để minh họa nhưng **không có mặt trong kết quả `toString()`**.

Cũng không nên dùng tên/ID do người dùng nhập một cách tùy tiện. Nếu dữ liệu đầu vào có thể mang xuống dòng hoặc ký tự điều khiển, hãy chuẩn hóa/escape hoặc thay bằng mã chẩn đoán do hệ thống sinh; xem phần tiếp theo.

## 9. Tránh đưa dữ liệu không tin cậy làm hỏng cấu trúc log

Ngay cả trường không chứa secret cũng có thể là dữ liệu **không tin cậy**. Nếu `toString()` nối thẳng chuỗi có ký tự xuống dòng, ký tự điều khiển hoặc văn bản rất dài, log có thể bị làm nhiễu, giả mạo dòng hoặc tăng chi phí lưu trữ.

Một cách giảm rủi ro khi thực sự cần in giá trị đầu vào đã được cho phép là giới hạn chiều dài và escape các ký tự đặc biệt. Chỉ thực hiện điều này **sau khi** đã xác định trường được phép ghi log; escape không biến secret thành dữ liệu an toàn.

Đoạn minh họa (không phải chương trình độc lập):

~~~java
static String safeShortText(String value) {
    if (value == null) return "null";
    String cleaned = value.replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    int max = 80;
    return cleaned.length() <= max
            ? cleaned
            : cleaned.substring(0, max) + "...";
}
~~~

Hàm trên minh họa cách hiển thị chuỗi ngắn với các ký tự CR/LF/tab đã được escape. Nó **không xử lý mọi ký tự điều khiển, Unicode hoặc chính sách giới hạn byte**, nên cần quy tắc phù hợp với định dạng log thực tế khi xây dựng thư viện dùng trong production. Trong trường hợp có thể, **không in giá trị đầu vào** vẫn dễ bảo đảm hơn.

## 10. Khi dùng logging, exception và công cụ sinh mã

### Logging

- Tránh `logger.info("login={}", login)` hoặc `logger.debug("request=" + request)` nếu `login`/`request` có thể chứa secret.
- Ghi sự kiện nghiệp vụ và metadata có mục đích rõ ràng, như tên thao tác, trạng thái, correlation ID được duyệt.
- Kiểm tra cấu hình log và chính sách retention/quyền truy cập, nhưng **đừng dựa vào việc log chỉ dành cho người quản trị để cho phép ghi secret**.
- Dùng logging có cấu trúc và field allowlist khi phù hợp. Cũng cần cấu hình serializer/mapper an toàn: bảo vệ `toString()` không tự động bảo vệ mọi cách serialize đối tượng.

### Exception và debug

Mẫu như `throw new IllegalArgumentException("Bad request: " + request)` có thể kéo theo `request.toString()` vào exception message. Exception sau đó có thể được log hoặc gửi qua API. Nên dùng thông báo mô tả lỗi, mã lỗi, hoặc correlation ID không nhạy cảm thay vì nguyên đối tượng.

Debugger, heap dump và tracing có thể quan sát giá trị theo cách khác `toString()`. Một `toString()` an toàn là **một lớp bảo vệ phạm vi đầu ra chuỗi**, không phải cam kết rằng secret không tồn tại hoặc không thể bị đọc ở nơi khác.

### IDE, record và công cụ sinh mã

- Record tự tạo `toString()` từ các component nếu không override. Rà soát record/DTO chứa dữ liệu xác thực trước khi log.
- IDE có tính năng sinh `toString()` từ các field. Không chấp nhận mặc định “chọn tất cả”.
- Thư viện sinh mã có thể có tùy chọn bỏ qua hoặc chỉ chọn field được phép; kiểm tra cấu hình thực tế và mã được sinh. Một annotation bỏ qua trường khi serialize JSON **không mặc nhiên thay đổi `toString()`**, và ngược lại.
- Với các kiểu mô hình có thể thêm trường mới, ưu tiên allowlist để phần biểu diễn không tự đổi theo cấu trúc nội bộ.

Nếu cần dùng object để hiển thị trong UI hoặc truyền qua mạng, nên có **DTO/response model riêng** đã được thiết kế để loại secret. Không dùng `toString()` làm giao thức trao đổi dữ liệu hoặc định dạng API.

## 11. Tự kiểm tra `toString()` bằng một bài test đơn giản

Không nên chỉ đọc bằng mắt đầu ra một lần. Khi thay đổi lớp hoặc thêm component, hãy kiểm tra rằng biểu diễn vẫn không chứa những dấu hiệu bí mật đã đặt trong đối tượng thử nghiệm.

Tạo tệp `ToStringSafetyCheck.java`:

~~~java
public class ToStringSafetyCheck {
    record SafeCredential(
            String operationId,
            String password,
            String accessToken
    ) {
        @Override
        public String toString() {
            return "SafeCredential[operationId=" + operationId + "]";
        }
    }

    private static void assertNotContained(String text, String secret) {
        if (text.contains(secret)) {
            throw new AssertionError("Sensitive value appears in representation");
        }
    }

    public static void main(String[] args) {
        SafeCredential value = new SafeCredential(
                "op-123",
                "synthetic-password-123",
                "synthetic-access-token-456"
        );

        String output = value.toString();
        assertNotContained(output, value.password());
        assertNotContained(output, value.accessToken());

        if (!output.contains("op-123")) {
            throw new AssertionError("Missing permitted diagnostic field");
        }

        System.out.println("toString safety check: PASS");
    }
}
~~~

~~~bash
javac -encoding UTF-8 ToStringSafetyCheck.java
java ToStringSafetyCheck
~~~

Stdout:

~~~text
toString safety check: PASS
~~~

Các giá trị trên đều là **dữ liệu giả**, tuyệt đối không đặt token thật vào mã thử nghiệm hoặc thông báo thất bại. Đây là kiểm tra tối thiểu, không thể chứng minh rằng tất cả kiểu secret chưa biết hoặc mọi đường serialize đều an toàn. Để mạnh hơn, bổ sung test khi thêm trường, thay đổi kiểu component lồng nhau hoặc thay cơ chế sinh `toString()`.

Khi viết test thất bại, đừng nhét chính secret vào thông báo assert hay log test. Mục đích là phát hiện rò rỉ, không tạo thêm một kênh lộ bí mật.

## 12. Những hiểu lầm thường gặp

| Hiểu lầm | Điều đúng |
| --- | --- |
| `toString()` chỉ dùng trong debugger nên có thể in toàn bộ field. | Nó còn có thể xuất hiện trong log, exception, công cụ giám sát và chuỗi nối. |
| Record không có `toString()` tự viết thì an toàn. | Record mặc định sinh biểu diễn gồm các component, có thể chứa mật khẩu/token. |
| Đổi token sang Base64 hoặc chỉ in vài ký tự là đã an toàn. | Không bảo đảm; vẫn có khả năng lộ thông tin và mối liên hệ về token. |
| Chỉ cần sửa `toString()` của lớp ngoài. | Nếu lớp ngoài nối nguyên đối tượng con/Map/List, biểu diễn con có thể chứa secret. |
| Hash mật khẩu luôn được phép xuất log. | Hash cũng có thể là dữ liệu nhạy cảm; nên loại khỏi biểu diễn. |
| Dùng `logger.debug` nghĩa là secret không bao giờ bị ghi. | Khi mức log được bật hoặc thông điệp được thu thập, dữ liệu vẫn có thể xuất hiện. |
| Có `@JsonIgnore` là `toString()` cũng tự bỏ trường. | Chính sách JSON serialization và `toString()` là hai cơ chế khác nhau. |
| Chuỗi `toString()` không lộ bí mật thì cả đối tượng an toàn khi serialize. | JSON/XML, debugger hoặc heap dump có đường lấy dữ liệu riêng. |
| Che hết secret bằng replace sau khi nối chuỗi là đủ. | Dễ bỏ sót trường mới, định dạng khác và object lồng nhau; allowlist vững hơn. |
| Mã định danh nào cũng là dữ liệu công khai. | ID có thể mang ý nghĩa nhạy cảm; cần phân loại trước khi đưa vào log. |

## 13. Checklist thiết kế biểu diễn an toàn

Trước khi hoàn thành `toString()` cho một lớp Java:

1. **Phân loại toàn bộ dữ liệu**: nhận diện mật khẩu, token, khóa, cookie, thông tin cá nhân và dữ liệu quan trọng trong cả field trực tiếp lẫn object lồng.
2. **Xác định mục tiêu chẩn đoán**: thật sự cần biết gì để xử lý sự cố? Thường chỉ cần loại đối tượng, trạng thái và correlation ID được thiết kế an toàn.
3. **Dùng allowlist**: chỉ nối các trường đã được duyệt, thay vì in hết rồi cố gắng xóa.
4. **Không dùng token làm mã tương quan**: không ghi token nguyên văn, prefix/suffix, Base64 hoặc hash token tùy tiện vào log.
5. **Kiểm tra object lồng**: không nối nguyên header map, DTO request, collection hoặc payload chứa dữ liệu chưa phân loại.
6. **Rà soát record và code generator**: `toString()` tự động sinh có thể hiển thị các trường được thêm sau này.
7. **Kiểm soát dữ liệu đầu vào được phép in**: escape ký tự xuống dòng/điều khiển, giới hạn kích thước hoặc chỉ log mã do hệ thống tạo.
8. **Giữ exception và logging nhất quán**: sử dụng những giá trị đã được cho phép, kể cả khi thông điệp chỉ dành cho debug.
9. **Kiểm thử với dữ liệu giả có dấu hiệu nhận diện**: xác nhận chuỗi không chứa bất kỳ secret thử nghiệm nào; không in các giá trị đó trong kết quả kiểm thử.
10. **Đánh giá riêng các kênh khác**: serialize JSON, tracing, event, heap dump và màn hình hỗ trợ phải có chính sách bảo vệ của chính chúng.

**Ghi nhớ:** `toString()` là bề mặt **quan sát** của đối tượng và có thể bị gọi gián tiếp rất nhiều lần. Một biểu diễn tốt cho debug phải **tối thiểu, có chủ đích và không mang thông tin xác thực**. Với lớp chứa mật khẩu, token hoặc dữ liệu nhạy cảm, ưu tiên allowlist và không bao giờ để secret đi vào chuỗi `toString()`, dù thông qua field trực tiếp, record sinh tự động hay object lồng nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-006">Phân biệt toString() với tuần tự hóa và định dạng trao đổi ổn định</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt toString() với tuần tự hóa và định dạng trao đổi ổn định

## 1. Cùng một đối tượng, ba mục đích khác nhau

Trong Java, việc biến một đối tượng thành chuỗi để **người đọc hiểu**, chuyển trạng thái đối tượng thành dữ liệu để **khôi phục**, và tạo một định dạng để **nhiều thành phần hệ thống trao đổi lâu dài** là ba nhu cầu khác nhau.

1. **`toString()`** tạo biểu diễn văn bản phục vụ quan sát, gỡ lỗi, ghi log hoặc trình bày giản lược. Mặc định, nội dung và cách trình bày là quyết định của lớp; hợp đồng `Object.toString()` không bắt buộc biểu diễn đó phải đọc ngược được.
2. **Tuần tự hóa (serialization)** chuyển thông tin đối tượng thành biểu diễn có thể lưu hoặc truyền và, nếu có quy trình tương ứng, dựng lại trạng thái. Java có cơ chế riêng là `ObjectOutputStream`/`ObjectInputStream`, nhưng đây chỉ là **một** kiểu serialization. JSON, XML, Protobuf và các định dạng nhị phân có schema cũng thuộc nhóm biểu diễn dữ liệu có cấu trúc.
3. **Định dạng trao đổi ổn định (stable interchange format)** cần một hợp đồng dữ liệu rõ ràng: trường nào tồn tại, kiểu dữ liệu gì, mã hóa ký tự ra sao, quy tắc `null`, thứ tự/độ chính xác, cách xử lý phiên bản, và mức tương thích khi nâng cấp. Một định dạng ổn định thường được tạo bởi bộ tuần tự hóa và bộ phân tích dữ liệu có cấu trúc, không lấy từ `toString()` một cách tùy tiện.

Hai trục này liên quan nhưng không đồng nghĩa: *serialization* mô tả hoạt động chuyển dữ liệu sang biểu diễn khác; *ổn định để trao đổi* là một thuộc tính được thiết kế và duy trì của **định dạng cùng hợp đồng xử lý nó**.

| Tiêu chí | `toString()` | Java object serialization | Định dạng trao đổi có hợp đồng |
| --- | --- | --- | --- |
| Mục đích chính | Hiển thị, chẩn đoán, gỡ lỗi | Ghi/đọc đồ thị đối tượng Java theo cơ chế JDK | Giao tiếp API, lưu bền vững, kết nối nhiều hệ thống |
| Định dạng bắt buộc | Không có schema chung | Luồng nhị phân theo cơ chế Java serialization | Schema, phiên bản, quy tắc mã hóa được công bố |
| Có thể đọc ngược mặc định | Không | Có trong điều kiện lớp tương thích | Có khi tồn tại decoder/parser tương ứng |
| Dùng với ngôn ngữ khác | Không bảo đảm | Không phải giao thức trung lập ngôn ngữ | Có thể, nếu dùng định dạng và schema được các bên hỗ trợ |
| Ổn định khi sửa code | Không được bảo đảm | Phụ thuộc quy tắc tương thích và phiên bản lớp | Phụ thuộc quản trị schema và cam kết tương thích |
| Dùng cho dữ liệu không tin cậy | Không nên coi là định dạng nhập liệu | Không nên giải tuần tự hóa luồng không tin cậy | Cần parser, kiểm tra schema, giới hạn kích thước và xác thực phù hợp |

**Nguyên tắc quan trọng:** Chuỗi nhìn “giống JSON” hay “giống một dòng dữ liệu” không tự biến `toString()` thành API serialization. Chính hợp đồng được công bố và kiểm chứng mới quyết định các hệ thống có thể dựa vào định dạng đó hay không.

## 2. toString() thực sự hứa điều gì?

`toString()` là một phương thức của `java.lang.Object`. Khi chưa ghi đè, cách biểu diễn thường gặp là tên lớp, dấu `@` và mã băm ở dạng hexadecimal. Vì `Object.toString()` dựa trên phương thức `hashCode()`, phần sau dấu `@` **không được xem là định danh vĩnh viễn, duy nhất hoặc địa chỉ bộ nhớ**. Lớp có thể ghi đè `hashCode()`; mã băm cũng có thể trùng nhau.

Một lớp có thể ghi đè `toString()` để người phát triển đọc được các thuộc tính hữu ích:

~~~java
@Override
public String toString() {
    return "Order{id=" + id + ", quantity=" + quantity + "}";
}
~~~

Đây là **đoạn phương thức minh họa đặt trong lớp có các trường tương ứng**, không phải một chương trình độc lập. Biểu diễn vừa đủ rõ cho người đọc, nhưng nó chưa nói có cơ chế escape nào khi `id` chứa ký tự `}`, dấu phẩy hoặc xuống dòng. Nó cũng chưa định nghĩa cách diễn giải `null`, số rất lớn, thời gian, Unicode hay phiên bản dữ liệu.

Các thay đổi hoàn toàn hợp lý đối với chuỗi gỡ lỗi, chẳng hạn đổi tên trường từ `quantity` thành `qty`, thêm dấu ngoặc kép, hoặc ẩn một trường nhạy cảm, có thể phá chương trình đang lén tách chuỗi `toString()` bằng biểu thức chính quy.

Dùng `toString()` đúng chỗ:

- Khi cần nhìn nhanh giá trị trong debugger, log hoặc thông báo chẩn đoán.
- Khi biểu diễn hữu ích cho con người mà **không cam kết khả năng parse**.
- Khi có thể thay đổi cách trình bày theo yêu cầu bảo mật, trải nghiệm hoặc hỗ trợ kỹ thuật.

Không nên in mặc định mật khẩu, token, khóa bí mật, số định danh nhạy cảm hoặc nội dung riêng tư trong `toString()`: đối tượng có thể bị đưa vào log tự động. Mặt khác, nếu chuỗi này tiếp nhận dữ liệu từ bên ngoài, ký tự xuống dòng và dấu điều khiển có thể làm nhiễu log; cân nhắc escape, lược bỏ hoặc che trường phù hợp.

Lớp `record` trong Java sinh sẵn `toString()`, nhưng **hình thức chuỗi sinh tự động cũng không phải schema trao đổi lâu dài**. Việc dựa vào mẫu chuỗi ấy để lưu trạng thái hoặc giao tiếp giữa dịch vụ khiến cấu trúc chương trình nội bộ vô tình trở thành hợp đồng công khai.

## 3. Tuần tự hóa Java có giống toString() không?

Không. Cơ chế **Java object serialization** sử dụng giao diện `Serializable` và các lớp như `ObjectOutputStream`/`ObjectInputStream` để ghi và đọc luồng dữ liệu của các đối tượng được hỗ trợ. Đây là luồng nhị phân có định dạng riêng, không phải kết quả của `toString()`.

Ví dụ một đối tượng có:

~~~text
id = "A|B"
quantity = 3
~~~

Biểu diễn cho người đọc có thể là:

~~~text
Order{id=A|B, quantity=3}
~~~

Trong khi luồng của `ObjectOutputStream` chứa các thông tin serialization khác để Java có thể tái lập đối tượng, và không có lý do gì phải giống chuỗi trên. Số byte cụ thể phụ thuộc loại đối tượng, cấu trúc và dạng luồng; không nên dựa vào việc in các byte ra văn bản.

### serialVersionUID và tương thích

Với các lớp `Serializable` thông thường, `serialVersionUID` giúp xác định phiên bản lớp liên quan tới việc đọc luồng cũ. Khai báo mã phiên bản phù hợp có thể hỗ trợ một số thay đổi lớp tương thích, nhưng **không bảo đảm mọi lần sửa lớp đều đọc lại được dữ liệu trước đó**. Các trường mới/bị xóa, thay đổi phân cấp, kiểu trường, cơ chế khởi tạo, bất biến và quy tắc tùy biến đều cần được xem xét. Serialization của record có một số quy tắc đặc thù, nhưng vẫn không thay thế việc thiết kế schema bên ngoài.

Java object serialization phù hợp với một số trường hợp nội bộ có kiểm soát và nhu cầu tương thích đã biết. Không nên chọn nó mặc định làm giao thức công khai giữa nhiều ngôn ngữ, lưu cấu trúc dữ liệu nghiệp vụ dài hạn hoặc xử lý luồng từ nguồn không đáng tin cậy.

**Lưu ý an toàn:** Giải tuần tự hóa đối tượng Java từ dữ liệu không tin cậy có thể nguy hiểm, do cơ chế dựng đồ thị đối tượng và hành vi của các lớp có trên classpath. Tránh `ObjectInputStream` trên đầu vào tùy ý của người dùng; đối với đầu vào có kiểm soát vẫn cần giới hạn lớp/độ sâu/kích thước thích hợp và thiết kế nguồn tin cậy. Không đánh đồng việc một tệp “đọc lại được” với việc nó là một giao thức trao đổi phù hợp.

## 4. Định dạng trao đổi ổn định cần quy tắc nào?

“Ổn định” không có nghĩa là không được thay đổi bao giờ. Nó nghĩa là người đọc và người ghi **biết chính xác những gì được cam kết**, và các thay đổi được đưa ra theo chính sách tương thích có thể kiểm thử.

Ít nhất cần quyết định:

1. **Schema và tên trường:** Thực thể, trường bắt buộc/tùy chọn, kiểu, miền giá trị và cách phân biệt dữ liệu không tồn tại với `null`.
2. **Phiên bản:** Một mã phiên bản trong nội dung hoặc thông tin giao thức để nhận diện định dạng, cách chấp nhận các phiên bản cũ và cách từ chối phiên bản không hỗ trợ.
3. **Mã hóa:** UTF-8 hay một encoding khác; ký tự phân cách, escape, nhị phân, Unicode, độ dài và giới hạn kích thước.
4. **Giá trị có cấu trúc:** Quy tắc với số nguyên, số thập phân, ký hiệu thời gian, timezone, enum, danh sách và cấu trúc lồng nhau. Ví dụ thời điểm thời gian tuyệt đối và ngày cục bộ có ngữ nghĩa khác nhau.
5. **Ổn định khi thay đổi ứng dụng:** Thêm trường tùy chọn, thay đổi tên, bỏ trường và đổi kiểu cần chính sách migration/đọc dữ liệu cũ.
6. **Xác thực và báo lỗi:** Parser phải từ chối dữ liệu hỏng hoặc vượt giới hạn, không ngầm chấp nhận trạng thái sai rồi tạo ra đối tượng không hợp lệ.
7. **Khả năng tương tác:** Các phía phải thống nhất quy tắc canonical nếu cần chữ ký, hash nội dung hoặc so sánh byte; chỉ dùng JSON không tự bảo đảm thứ tự thuộc tính hay cách biểu diễn số.
8. **Kiểm thử:** Giữ bộ dữ liệu mẫu (*golden files*), round-trip, kiểm thử đọc phiên bản cũ và trường hợp thiếu, sai kiểu hoặc chứa ký tự đặc biệt.

Đối với API thực tế, thường nên chọn định dạng có thư viện và tiêu chuẩn được dùng rộng rãi như JSON có schema và parser đáng tin, hoặc Protobuf khi có nhu cầu nhị phân và schema tiến hóa được. Việc tự nghĩ một chuỗi phân tách bằng dấu `|` chỉ phù hợp ví dụ nhỏ khi hai phía cùng kiểm soát đầy đủ định dạng; nếu không quy định escape thì dữ liệu đầu vào có ký tự `|` sẽ phá cách tách trường.

## 5. Ví dụ Java 17: cùng một Order ở ba biểu diễn

Chương trình dưới đây dùng một record `Order`, ghi đè `toString()` để dễ xem; định nghĩa **giao thức văn bản v1 minh họa** với định dạng `1|idBase64Url|quantity`; đồng thời cho thấy serialization nhị phân Java có thể dựng lại một đối tượng cùng giá trị từ luồng **nội bộ, đáng tin cậy**.

Vì `id` được encode bằng Base64 URL-safe không padding trên byte UTF-8, dữ liệu `"A|B"` không gây nhầm lẫn với dấu `|` ngăn các trường. `quantity` luôn được ghi ở dạng thập phân của `int`; parser kiểm tra phiên bản, số trường và quy tắc constructor. Giao thức v1 này là **ví dụ giảng dạy**, chưa phải chuẩn sẵn sàng dùng cho dữ liệu công khai nếu thiếu giới hạn kích thước, chiến lược tiến hóa và kiểm thử tương thích.

Tạo tệp **ToStringAndSerializationDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class ToStringAndSerializationDemo {
    record Order(String id, int quantity) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        Order {
            Objects.requireNonNull(id, "id");
            if (quantity < 0) {
                throw new IllegalArgumentException("quantity < 0");
            }
        }

        @Override
        public String toString() {
            return "Order{id=" + id + ", quantity=" + quantity + "}";
        }
    }

    static String encodeWireV1(Order order) {
        String encodedId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(order.id().getBytes(StandardCharsets.UTF_8));
        return "1|" + encodedId + "|" + order.quantity();
    }

    static Order decodeWireV1(String wire) {
        String[] parts = wire.split("[|]", -1);
        if (parts.length != 3 || !parts[0].equals("1")) {
            throw new IllegalArgumentException("Unsupported wire format");
        }

        byte[] idBytes = Base64.getUrlDecoder().decode(parts[1]);
        String id = new String(idBytes, StandardCharsets.UTF_8);
        int quantity = Integer.parseInt(parts[2]);
        return new Order(id, quantity);
    }

    static byte[] writeJavaObject(Order order) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output =
                     new ObjectOutputStream(bytes)) {
            output.writeObject(order);
        }
        return bytes.toByteArray();
    }

    static Order readTrustedJavaObject(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            return (Order) input.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Order original = new Order("A|B", 3);

        String display = original.toString();
        String wire = encodeWireV1(original);
        Order fromWire = decodeWireV1(wire);

        System.out.println("display: " + display);
        System.out.println("wire: " + wire);
        System.out.println("wire round-trip equal: "
                + original.equals(fromWire));
        System.out.println("display equals wire: "
                + display.equals(wire));

        byte[] javaBytes = writeJavaObject(original);
        Order fromJavaBytes = readTrustedJavaObject(javaBytes);
        System.out.println("Java stream has bytes: "
                + (javaBytes.length > 0));
        System.out.println("Java object round-trip equal: "
                + original.equals(fromJavaBytes));
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac ToStringAndSerializationDemo.java
java ToStringAndSerializationDemo
~~~

Kết quả dự kiến:

~~~text
display: Order{id=A|B, quantity=3}
wire: 1|QXxC|3
wire round-trip equal: true
display equals wire: false
Java stream has bytes: true
Java object round-trip equal: true
~~~

**Giải thích:**

- `display` phục vụ con người. Nếu ngày mai đổi thành `Order(A|B, 3)` thì ý nghĩa của đối tượng không đổi nhưng một trình phân tích phụ thuộc chuỗi cũ có thể hỏng.
- `wire` là một biểu diễn có cấu trúc được xác định tường minh. Chuỗi `A|B` được mã hóa thành `QXxC` trong Base64 URL-safe; khi đọc, parser có thể khôi phục chính xác `id` và `quantity` theo giao thức v1.
- `wire round-trip equal: true` xác nhận đối tượng được dựng lại có các thành phần tương đương với đối tượng ban đầu. **Round-trip đúng trong một phiên bản không tự chứng minh định dạng tương thích giữa các phiên bản khác nhau**.
- Java object stream tạo byte nhị phân và đọc lại một bản `Order` tương đương. Quá trình không gọi `toString()` để quyết định nội dung cần lưu.
- Việc record tự sinh `equals` theo các thành phần giúp kiểm tra nội dung sau round-trip. Biểu diễn `toString()`, định dạng wire và Java object stream là ba hợp đồng khác nhau.

Trong ví dụ, `readTrustedJavaObject` được đặt tên có chữ “Trusted” vì nó đọc lại byte do chính chương trình vừa tạo ra. **Không mở rộng phương thức đó thành API nhận byte tùy ý từ mạng hoặc người dùng.** Một ứng dụng thực tế cần kiểm soát nguồn dữ liệu, schema, validation, giới hạn tài nguyên và cách xử lý lỗi theo ranh giới bảo mật.

Ngoài ra, parser ví dụ biến byte Base64 thành `String` UTF-8 bằng constructor mặc định; trong dữ liệu sản xuất có yêu cầu nghiêm ngặt, cần quyết định rõ phải **từ chối** byte UTF-8 không hợp lệ thay vì chấp nhận chuỗi được thay ký tự, và tránh dữ liệu kích thước quá lớn. Trường hợp này càng cho thấy “định dạng ổn định” phải gồm cả quy tắc lỗi và giới hạn đầu vào.

## 6. Vì sao không nên lưu kết quả toString vào cơ sở dữ liệu như dữ liệu chính?

Giả sử phiên bản đầu lưu:

~~~text
Customer{id=42, name=Lan}
~~~

Một phiên bản sau đổi cách hiển thị:

~~~text
Customer #42 — Lan
~~~

Nếu dữ liệu được lưu chỉ để **đọc log**, khác biệt này là bình thường. Nhưng nếu một quy trình nhập dữ liệu vẫn tìm chuỗi `"id="` hoặc dấu phẩy để khôi phục `Customer`, quy trình đó sẽ không còn đọc được dữ liệu được viết bằng phiên bản mới.

Ngay cả khi không đổi format cố ý, cách biểu diễn chuỗi cũng có thể mơ hồ: tên khách hàng chứa dấu phẩy, dấu ngoặc, dấu bằng hoặc ký tự xuống dòng; trường `null` có thể bị in thành chữ `"null"` khiến không phân biệt được với chuỗi thật có giá trị đó. Với số và ngày tháng, khác biệt locale/timezone cũng có thể tạo ra nhiều văn bản cho cùng giá trị logic.

Đối với dữ liệu cần lưu/đọc sau nhiều lần triển khai ứng dụng, hãy lưu các cột có kiểu rõ ràng hoặc sử dụng định dạng cấu trúc với serializer/parser chính thức. Không nên dùng `toString()` như giao thức lưu trữ chỉ vì biểu diễn hiện tại dễ đọc.

## 7. “JSON từ toString()” có đủ để ổn định không?

Một cách triển khai dễ mắc lỗi:

~~~java
@Override
public String toString() {
    return "{\"id\":\"" + id + "\"}";
}
~~~

Đây là **đoạn minh họa lỗi**, không phải chương trình độc lập. Nếu `id` chứa dấu `"`, dấu `\`, newline hoặc ký tự điều khiển, kết quả có thể không còn là JSON hợp lệ hoặc mang ý nghĩa khác với giá trị ban đầu. Cách ghép chuỗi cũng dễ bỏ qua giá trị `null`, số thập phân và đối tượng lồng nhau.

Ngay cả khi có serializer JSON chuẩn và tạo được cú pháp JSON hợp lệ, tính **ổn định** vẫn phụ thuộc vào schema: trường nào được thêm, loại bỏ, đổi tên; cách mã hóa thời gian; khách hàng cũ xử lý trường không biết như thế nào; phiên bản API được quản lý ra sao. Nếu cần so sánh từng byte hoặc chữ ký số, còn cần quy định cách chuẩn hóa (canonicalization) thay vì giả định hai đối tượng JSON tương đương luôn được serialize ra chuỗi byte giống nhau.

Vì vậy, cần tách hai trách nhiệm: `toString()` phục vụ quan sát; **serializer và schema** tạo dữ liệu cho giao tiếp/lưu bền vững. Nếu muốn log một đoạn JSON, hãy coi đó là **structured logging** với thư viện hỗ trợ và chính sách bảo mật phù hợp, không phải lý do biến `toString()` thành định dạng truyền dữ liệu.

## 8. Lỗi phổ biến và cách tránh

### Nhầm 1: “toString đã chứa mọi trường, vậy có thể parse ngược”

Không có yêu cầu rằng `toString()` phải hiển thị **đủ** trường hoặc mã hóa chúng không mơ hồ. Một lớp có thể cố ý ẩn mật khẩu, cắt bớt danh sách dài, làm tròn số hoặc dịch tên thuộc tính cho dễ đọc. Ngay cả chuỗi đủ trường hôm nay cũng có thể đổi ngày mai.

### Nhầm 2: “Serializable tự biến object thành JSON”

`Serializable` là dấu hiệu tham gia cơ chế object serialization của Java; nó **không tự quy định JSON**. Để trao đổi JSON, thường cần thư viện và định nghĩa mapping/schema tương ứng.

### Nhầm 3: “Thêm serialVersionUID là đủ để tương thích mọi bản phát hành”

`serialVersionUID` liên quan đến kiểm tra tương thích phiên bản của Java serialization, nhưng không thay thế việc xem xét cách tiến hóa lớp và dữ liệu. Nó không biến byte của Java object stream thành định dạng trung lập ngôn ngữ, cũng không bảo đảm cấu trúc đã thay đổi vẫn giữ cùng ngữ nghĩa.

### Nhầm 4: “Có round-trip thành công là đã tạo format ổn định”

`decode(encode(x))` thành công chỉ kiểm chứng một phần tính đúng đắn hiện tại. Độ ổn định dài hạn đòi hỏi kiểm thử qua phiên bản, schema, dữ liệu biên, giới hạn kích thước và chính sách bỏ trường/tăng version.

### Nhầm 5: “StringBuilder ghép trường nhanh nên phù hợp truyền dữ liệu”

Hiệu năng ghép chuỗi không quyết định tính đúng đắn của định dạng. Nếu thiếu escape và versioning, một ký tự phân cách trong dữ liệu đầu vào đủ làm hỏng bộ phân tích. Một định dạng tiêu chuẩn có thư viện hỗ trợ thường đáng tin cậy hơn tối ưu thủ công không có hợp đồng.

### Nhầm 6: “In đầy đủ object là vô hại”

`toString()` có thể vô tình đưa thông tin nhạy cảm vào log, dữ liệu giám sát, thông báo lỗi hoặc hệ thống bên thứ ba. Chỉ đưa các trường cần thiết cho mục đích quan sát; tránh lộ secret, token và nội dung riêng tư.

## 9. Cách chọn giải pháp trong dự án Java

| Nhu cầu | Cách tiếp cận phù hợp |
| --- | --- |
| Xem đối tượng trong debugger và log | `toString()` ngắn, dễ hiểu, có che dữ liệu nhạy cảm |
| In giá trị một mảng phục vụ gỡ lỗi | `Arrays.toString`/`Arrays.deepToString`, chỉ dùng làm hiển thị |
| Lưu đối tượng theo schema để trao đổi API | JSON/Protobuf/XML hoặc format được quy định chính thức, với serializer và parser |
| Lưu dữ liệu nghiệp vụ trong CSDL | Cột có kiểu và ràng buộc, hoặc cột JSON có schema/versioning rõ ràng |
| Tạo thông điệp có thể tiêu thụ qua nhiều phiên bản | DTO/schema riêng, versioning, kiểm thử tương thích và validation |
| Lưu/đọc đồ thị đối tượng Java ở môi trường tin cậy và có kiểm soát | Chỉ cân nhắc Java serialization khi hiểu rõ tương thích và rủi ro; không dùng cho đầu vào không tin cậy |
| Dùng giá trị văn bản làm khóa, chữ ký hoặc cache có yêu cầu tái lập | Quy tắc canonical ổn định, không dựa vào `toString()` tùy ý |

Trước khi triển khai, hãy trả lời: ai là người tiêu thụ kết quả, họ có cần đọc ngược không, dữ liệu tồn tại bao lâu, có ngôn ngữ/hệ thống khác hay không, các phiên bản phần mềm có cần cùng đọc nó không, và mức tin cậy của đầu vào là gì. Từ đó chọn định dạng và phương thức thích hợp.

**Ghi nhớ:** `toString()` là **biểu diễn cho con người**, không phải mặc nhiên là serialization. Java serialization tạo **luồng đối tượng Java** có điều kiện tương thích và an toàn riêng. Một **định dạng trao đổi ổn định** cần schema, mã hóa, parser, versioning và chính sách tương thích rõ ràng. Hãy thiết kế đúng theo mục đích sử dụng, thay vì dùng lại chuỗi tiện xem làm hợp đồng dữ liệu lâu dài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-007">Không dùng toString() để quyết định equals() hoặc hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng `toString()` để quyết định `equals()` hoặc `hashCode()`

## 1. Ba phương thức, ba trách nhiệm khác nhau

Mọi lớp Java đều thừa kế trực tiếp hoặc gián tiếp từ `java.lang.Object`. Ba phương thức `toString()`, `equals(Object)` và `hashCode()` có thể cùng xuất hiện trong một lớp, nhưng **không cùng phục vụ một mục đích**:

| Phương thức | Câu hỏi nó trả lời | Nơi thường sử dụng |
| --- | --- | --- |
| `toString()` | Làm sao biểu diễn đối tượng thành chuỗi dễ đọc? | Log, gỡ lỗi, hiển thị, chẩn đoán |
| `equals(Object)` | Khi nào hai đối tượng được coi là bằng nhau về logic? | So sánh giá trị, `contains`, `HashSet`, `HashMap` |
| `hashCode()` | Mã băm nào nhất quán với quan hệ bằng nhau của đối tượng? | Cấu trúc băm, `HashMap`, `HashSet` |

**Nguyên tắc:** `toString()` là cách **trình bày** một đối tượng. `equals()` và `hashCode()` xác định **hợp đồng bình đẳng và băm**. Không nên suy ra bình đẳng hoặc mã băm từ chuỗi dùng để trình bày.

Ví dụ, một tài khoản có `id = "A-100"`, `displayName = "Minh"`. Nếu hệ thống coi `id` là định danh thì hai tài khoản cùng tên hiển thị nhưng khác `id` **không bằng nhau**. Ngược lại, hai bản ghi cùng `id` nhưng tên hiển thị khác có thể được coi là cùng tài khoản logic. Việc `toString()` in thông tin gì không nên thay đổi những quy tắc này.

## 2. Hợp đồng thật sự của từng phương thức

### `Object.toString()`: biểu diễn chuỗi, không phải định danh

`Object.toString()` mặc định trả chuỗi gồm tên lớp, ký tự `@` và biểu diễn hex của mã băm do `hashCode()` trả về. Chẳng hạn một chuỗi có dạng minh họa:

```text
com.example.Customer@5e2de80c
```

Dãy hex trên **chỉ là ví dụ về định dạng**, không phải kết quả cố định; không có yêu cầu rằng hai object khác nhau luôn có chuỗi mặc định khác nhau. Mã băm cũng **không phải địa chỉ bộ nhớ hoặc ID duy nhất**. Một lớp có thể ghi đè `toString()` để trả bất kỳ mô tả phù hợp nào, chẳng hạn tên hiển thị, trạng thái tóm tắt hoặc các trường thuận tiện cho log.

API Java **không** quy định `toString()` mặc nhiên là biểu diễn chuẩn, bất biến, duy nhất, có thể phân tích ngược thành đối tượng, hoặc luôn phù hợp làm khóa dữ liệu. Khi đổi phiên bản ứng dụng, người phát triển có thể hợp lý thay đổi định dạng `toString()` nhằm cải thiện log.

### `Object.equals(Object)`: quy tắc bình đẳng về logic

`Object.equals(Object)` mặc định chỉ xem hai tham chiếu bằng nhau nếu chúng trỏ tới cùng object. Một lớp giá trị có thể ghi đè để so sánh các thành phần có ý nghĩa nghiệp vụ. Ví dụ `String.equals()` so nội dung chuỗi, còn một lớp `Customer` có thể so mã khách hàng.

Hợp đồng `equals` yêu cầu:

1. **Phản xạ:** `x.equals(x)` là `true` với `x` khác `null`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
3. **Bắc cầu:** nếu `x` bằng `y` và `y` bằng `z`, thì `x` bằng `z`.
4. **Nhất quán:** khi các thông tin liên quan không thay đổi, kết quả không thay đổi.
5. **Không bằng null:** `x.equals(null)` là `false`.

Ghi đè đúng chữ ký `public boolean equals(Object obj)` và nên đặt `@Override`. `equals(MyType)` chỉ là một overload trong trường hợp thông thường, không thay thế hợp đồng của `Object.equals(Object)`.

### `Object.hashCode()`: phải khớp với equals

Quy tắc bắt buộc:

```text
x.equals(y) == true  =>  x.hashCode() == y.hashCode()
```

Chiều ngược lại không bắt buộc: hai đối tượng có cùng hash vẫn có thể không bằng nhau (va chạm hash). Với những trường dùng trong `equals` không đổi, `hashCode` cũng cần ổn định trong một lần chạy của ứng dụng.

**Không có quy tắc nào yêu cầu `toString()` của hai đối tượng bằng nhau phải giống nhau.** Việc đưa `toString()` vào `equals/hashCode` có nguy cơ vô tình biến thay đổi hiển thị thành thay đổi định danh.

## 3. Vì sao so sánh `toString()` dễ sai?

Giả sử `User.toString()` chỉ hiển thị tên người dùng. Hai tài khoản riêng biệt cùng tên `"An"` có thể cho **chuỗi giống hệt nhau**. Nếu viết:

```java
@Override
public boolean equals(Object obj) {
    return obj instanceof User other
            && toString().equals(other.toString());
}
```

Đây là **đoạn minh họa đặt trong lớp `User`**, không phải chương trình độc lập. Lỗi không nằm ở cú pháp so chuỗi: lỗi là **chuỗi hiển thị không thể hiện chính xác danh tính theo nghiệp vụ**.

Các nguyên nhân phổ biến:

- **Thiếu thông tin:** `toString()` chỉ in tên, một vài trường hoặc tóm tắt. Hai object khác nhau có thể có cùng chuỗi.
- **Thừa thông tin:** `toString()` in thời gian cập nhật, cache, debug flag hoặc trạng thái tạm thời dù những dữ liệu đó không tham gia bình đẳng.
- **Định dạng thay đổi:** thêm tiền tố, dấu phân cách, đổi thứ tự trường, bản địa hóa ngày/giờ hoặc che bớt dữ liệu nhạy cảm có thể đổi chuỗi mà không đổi giá trị logic.
- **Không có bảo đảm duy nhất:** hai object không bằng nhau không buộc phải có hai `toString()` khác nhau; hai object bằng nhau cũng không buộc phải có chuỗi giống nhau.
- **Tốn tài nguyên hoặc phát sinh side effect:** một implementation `toString()` có thể duyệt cấu trúc lớn, gọi phương thức khác, thậm chí bị ảnh hưởng bởi trạng thái bên ngoài. Mã băm cần được thiết kế theo trạng thái dùng làm equality, không theo công việc trình bày.

Ngay cả khi `toString()` tình cờ chứa đủ trường ở phiên bản hiện tại, lần refactor chỉ nhằm đổi cách log có thể phá logic tìm kiếm và so sánh. Đây là kiểu phụ thuộc khó nhận ra trong code review.

## 4. Ví dụ Java 17+: dùng toString dẫn đến nhầm tài khoản

Tạo tệp `BadToStringEqualityDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BadToStringEqualityDemo {
    static final class Account {
        private final String accountId;
        private final String displayName;

        Account(String accountId, String displayName) {
            this.accountId = accountId;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName; // Chỉ để hiển thị.
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Account other
                    && toString().equals(other.toString()); // Sai về nghiệp vụ.
        }

        @Override
        public int hashCode() {
            return toString().hashCode(); // Phụ thuộc chuỗi hiển thị.
        }
    }

    public static void main(String[] args) {
        Account a = new Account("A-100", "Minh");
        Account b = new Account("B-200", "Minh");
        Account c = new Account("A-100", "Minh mới");

        System.out.println(a.equals(b));
        System.out.println(a.equals(c));

        Set<Account> unique = new HashSet<>();
        unique.add(a);
        unique.add(b);
        unique.add(c);
        System.out.println(unique.size());

        Map<Account, String> balances = new HashMap<>();
        balances.put(a, "old");
        balances.put(b, "new");
        System.out.println(balances.size());
        System.out.println(balances.get(a));
    }
}
```

Biên dịch và chạy:

```bash
javac BadToStringEqualityDemo.java
java BadToStringEqualityDemo
```

Kết quả:

```text
true
false
2
1
new
```

**Giải thích:**

1. `a` và `b` khác mã tài khoản nhưng đều in `"Minh"`. `equals` sai trả `true`; `HashSet` xem chúng là một phần tử và `HashMap` xem là một khóa.
2. `a` và `c` cùng mã `"A-100"` nhưng tên hiển thị khác, nên `equals` sai trả `false`.
3. Set còn hai phần tử vì nó gộp `a` với `b` nhưng giữ `c`. Với map, `put(b, "new")` thay thế giá trị gắn với khóa logic `a`, vì thế `get(a)` trả `"new"`.

Lưu ý về tính chính xác: implementation này **vẫn có thể đáp ứng các định luật hình thức** của `equals` trong tình huống tên hiển thị bất biến, vì nó so `String` và băm theo `String`. Điều sai là **định nghĩa bình đẳng không khớp yêu cầu nghiệp vụ**: mã tài khoản bị bỏ qua. Không phải cứ `equals(toString())` là tự động vi phạm tính đối xứng hoặc bắc cầu; lỗi là chọn nhầm nguồn dữ liệu và làm hợp đồng phụ thuộc cách hiển thị.

## 5. Cách đúng: so sánh trường tạo nên danh tính và băm từ cùng trường đó

Nếu tài khoản được nhận dạng bằng `accountId`, hãy dùng chính trường này trong `equals` và `hashCode`. `toString` được phép hiển thị tên, mã, hoặc một tóm tắt khác độc lập với equality.

Tạo tệp `GoodAccountEqualityDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GoodAccountEqualityDemo {
    static final class Account {
        private final String accountId;
        private final String displayName;

        Account(String accountId, String displayName) {
            this.accountId = Objects.requireNonNull(accountId);
            this.displayName = Objects.requireNonNull(displayName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Account other)) {
                return false;
            }
            return accountId.equals(other.accountId);
        }

        @Override
        public int hashCode() {
            return accountId.hashCode();
        }

        @Override
        public String toString() {
            return "Account{id=" + accountId
                    + ", displayName=" + displayName + "}";
        }
    }

    public static void main(String[] args) {
        Account a = new Account("A-100", "Minh");
        Account b = new Account("B-200", "Minh");
        Account c = new Account("A-100", "Minh mới");

        System.out.println(a.equals(b));
        System.out.println(a.equals(c));
        System.out.println(a.toString().equals(c.toString()));

        Set<Account> unique = new HashSet<>();
        unique.add(a);
        unique.add(b);
        unique.add(c);
        System.out.println(unique.size());

        Map<Account, String> balances = new HashMap<>();
        balances.put(a, "old");
        balances.put(c, "updated");
        System.out.println(balances.size());
        System.out.println(balances.get(a));
    }
}
```

Lệnh chạy:

```bash
javac GoodAccountEqualityDemo.java
java GoodAccountEqualityDemo
```

Kết quả:

```text
false
true
false
2
1
updated
```

Ở phiên bản đúng:

- `a` và `b` cùng tên nhưng khác `accountId`, vì vậy `equals` trả `false`.
- `a` và `c` cùng `accountId` nên `equals` trả `true`, mặc dù `toString()` khác nhau vì tên hiển thị khác.
- `hashCode` chỉ dựa vào `accountId`, đúng với cách định nghĩa equality; hai object bằng nhau có cùng hash.
- `HashSet` giữ hai tài khoản logic `A-100` và `B-200`. `HashMap` dùng `a` và `c` như cùng khóa logic và cập nhật giá trị thành `"updated"`.

`Account` trong ví dụ là lớp `final`, `accountId` là `final`, và `String` bất biến. Điều này giúp mã băm ổn định khi object nằm trong một tập hợp hoặc làm khóa map. Việc chọn một trường duy nhất làm danh tính là **giả thiết nghiệp vụ của ví dụ**; trong ứng dụng thật, hãy xác định khóa tự nhiên, khóa tổng hợp hoặc toàn bộ trường giá trị phù hợp.

## 6. Một vòng phụ thuộc dễ gây StackOverflowError

Một rủi ro riêng xuất hiện khi lập trình viên dùng `toString()` để tính `hashCode()`, rồi lại dùng `hashCode()` trong `toString()`:

```java
@Override
public String toString() {
    return "Node#" + hashCode();
}

@Override
public int hashCode() {
    return toString().hashCode();
}
```

Đây là **đoạn mã lỗi minh họa trong một lớp `Node`**, không phải chương trình độc lập. `hashCode()` gọi `toString()`, rồi `toString()` lại gọi `hashCode()` không có điều kiện dừng. Khi được thực thi, đệ quy sẽ dẫn tới `StackOverflowError` thay vì mã băm có ích.

Ngay cả `Object.toString()` mặc định cũng liên quan `hashCode()` trong biểu diễn hex, nên không thể coi việc chuyển đổi object thành chuỗi là một thao tác trung lập để định nghĩa lại `hashCode`. Cần tách các phương thức theo chức năng ngay từ thiết kế.

## 7. Khi toString thay đổi hoặc trạng thái hiển thị có thể thay đổi

Giả sử một đối tượng giữ mã khách hàng bất biến nhưng tên hiển thị có thể được chỉnh sửa. Nếu `hashCode()` tính từ `toString()` có chứa tên, sau khi chỉnh tên:

1. Object đang nằm trong nhóm băm cũ ở `HashSet` hoặc `HashMap`.
2. Lời gọi `hashCode()` mới có thể trả số khác.
3. `contains`, `remove` hoặc `get` có thể tìm ở nhóm mới và không gặp object đã lưu.

Trường hợp này gây khó tìm lỗi vì object vẫn tồn tại trong collection nhưng tra cứu không hoạt động như kỳ vọng.

**Cách phòng tránh:** dùng trạng thái bất biến và ổn định cho các trường tham gia equality/hash. Nếu buộc phải thay khóa logic, xóa phần tử hoặc mục map **trước khi thay trạng thái**, rồi cập nhật và thêm lại; hoặc tạo một đối tượng khóa bất biến mới.

Một thay đổi hoàn toàn vô hại ở tầng trình bày, chẳng hạn chuyển `toString()` từ `"A-100"` sang `"Account A-100"`, không được phép làm thay đổi danh tính hoặc mã băm của object trong thiết kế đúng.

## 8. Còn trường hợp chuỗi là chính giá trị nghiệp vụ thì sao?

Không phải lúc nào việc dùng dữ liệu dạng chuỗi cũng sai. **Nếu định danh nghiệp vụ thực sự là một chuỗi**, ví dụ `accountId` hoặc một mã chuẩn hóa, hãy so sánh trực tiếp **trường chuỗi định danh**:

```java
return accountId.equals(other.accountId);
```

và dùng:

```java
return accountId.hashCode();
```

Hai đoạn trên minh họa thân phương thức trong lớp `Account` đã kiểm tra kiểu `other`, không phải chương trình độc lập.

Nếu thiết kế một kiểu bao bọc chuỗi đã chuẩn hóa, hãy biểu diễn quy tắc chuẩn hóa bằng một thành phần hoặc phương thức có tên rõ ràng (ví dụ `normalizedKey()`), và bảo đảm `equals/hashCode` luôn áp dụng cùng tiêu chí đó. Đừng sử dụng `toString()` làm một “đường tắt” bởi vì ý nghĩa thường thấy của nó chỉ là **hiển thị**.

Tương tự, chuỗi JSON, XML hoặc CSV dùng để trao đổi dữ liệu nên được tạo bởi một **serializer có định dạng và phiên bản được quy định**, thay vì coi `toString()` mặc nhiên là biểu diễn dữ liệu chuẩn. Nếu cần cache key ổn định, hãy tạo khóa từ dữ liệu đã chuẩn hóa theo schema rõ ràng. Những cân nhắc này giữ tách biệt với hợp đồng đối tượng mà `equals/hashCode` đang bảo đảm.

## 9. Java record giúp gì và cần lưu ý gì?

Với kiểu dữ liệu giá trị đơn giản, Java 17 `record` tự sinh `equals(Object)` và `hashCode()` theo các thành phần record.

```java
record Coordinate(int x, int y) {}
```

Đây là khai báo kiểu độc lập có thể đặt ở phạm vi top-level trong tệp `Coordinate.java`. Hai `Coordinate(1, 2)` có cùng giá trị thành phần sẽ bằng nhau và có mã băm tương thích, dù không phải cùng object. Java cũng cung cấp `toString()` mô tả thành phần, nhưng **không dùng chuỗi ấy làm cơ sở cho equality**.

Nếu một thành phần record là danh sách hoặc object mutable, record chỉ giữ **tham chiếu** ở trường `final`; nó không tự tạo bất biến sâu. Người dùng vẫn cần bảo đảm dữ liệu tham gia equality không bị sửa khi record đang làm khóa trong cấu trúc băm.

Một lưu ý khác: nếu danh tính nghiệp vụ chỉ dựa vào mã `id`, trong khi record còn chứa `displayName`, việc tự sinh equality theo **mọi thành phần** có thể không khớp. Khi đó hãy thiết kế lại mô hình dữ liệu, một value object chỉ chứa khóa định danh, hoặc tự định nghĩa equality theo hợp đồng nghiệp vụ.

## 10. Những nhầm lẫn cần tránh

| Nhầm lẫn | Sự thật và cách xử lý |
| --- | --- |
| “Hai `toString()` giống nhau thì hai object bằng nhau.” | Chuỗi hiển thị có thể bỏ qua thông tin phân biệt; dùng `equals` được định nghĩa đúng. |
| “Hai object bằng nhau phải có `toString()` giống nhau.” | Không có yêu cầu này trong hợp đồng `Object.equals`; có thể khác chi tiết hiển thị. |
| “Chỉ cần hash giống nhau là cùng object.” | Hash collision hợp lệ; `equals` mới quyết định equality. |
| “`Object.toString()` cho ID duy nhất của object.” | Biểu diễn mặc định dựa trên hash, không bảo đảm duy nhất hay ổn định ngoài hợp đồng. |
| “Dùng `toString()` làm hash dễ bảo trì.” | Thay đổi format hiển thị có thể làm thay đổi hash; băm từ trường tham gia `equals`. |
| “Có `equals(MyType)` là đủ để HashMap dùng.” | Đó thường là overload; phải override đúng `equals(Object)`. |
| “Record làm mọi dữ liệu bên trong bất biến.” | Record không tự làm bất biến sâu các object mutable được tham chiếu. |
| “Tên hiển thị bằng nhau tức là cùng thực thể.” | Điều này phụ thuộc định nghĩa danh tính nghiệp vụ; thường cần ID ổn định. |

## 11. Checklist khi thiết kế object contract

1. **Định nghĩa danh tính hoặc giá trị logic** của kiểu trước: trường nào quyết định hai object bằng nhau?
2. **Ghi đè `equals(Object)`** theo chính các trường đó; xử lý `null`, kiểu không phù hợp và các điều kiện đối xứng/bắc cầu.
3. **Ghi đè `hashCode()`** theo cùng trường tham gia `equals`; hai object bằng nhau phải cùng mã băm.
4. **Viết `toString()` cho người đọc**: đủ hữu ích để gỡ lỗi, tránh lộ bí mật, nhưng không dùng nó làm nguồn truth cho identity/equality.
5. **Ưu tiên trường bất biến** cho identity/hash, nhất là khi object là khóa của `HashMap` hoặc phần tử `HashSet`.
6. **Kiểm thử độc lập ba phương thức**: hai object cùng ID nhưng khác tên hiển thị; hai object khác ID nhưng cùng tên; thay đổi format `toString()` không được thay đổi `equals/hashCode`.
7. **Kiểm thử qua collection**: `HashSet.add`, `HashMap.put/get` phải khử trùng hoặc cập nhật theo danh tính nghiệp vụ, không theo chuỗi hiển thị.
8. **Tránh vòng gọi đệ quy** giữa `toString`, `hashCode` và các phương thức mà chúng phụ thuộc.
9. **Nếu cần biểu diễn chuẩn**, tạo serializer hoặc `canonicalKey()` có hợp đồng riêng và kiểm thử ổn định định dạng; không lẫn với phương thức `toString` phục vụ chẩn đoán.

**Tự kiểm tra:**

- Hai tài khoản khác ID nhưng cùng tên hiển thị có bắt buộc bằng nhau không? **Không.**
- Hai tài khoản cùng ID nhưng format `toString()` khác nhau có thể bằng nhau không? **Có**, nếu ID là tiêu chí equality.
- Việc đổi nội dung log trong `toString()` có được làm đổi `hashCode()` không? **Không**, khi những trường tham gia equality không thay đổi.
- Khi nào `hashCode()` phải giống nhau giữa hai object? **Khi `equals()` của chúng là `true`.**
- Muốn khóa chuỗi chuẩn cho cache thì dùng `toString()` tùy ý hay tạo `canonicalKey()` rõ hợp đồng? **Tạo khóa chuẩn có định nghĩa và kiểm thử riêng.**

**Ghi nhớ:** `toString()` trả lời “đối tượng trông như thế nào khi in ra”; `equals()` và `hashCode()` trả lời “đối tượng nào là cùng một giá trị logic”. Hãy xây equality/hash từ những thành phần nghiệp vụ ổn định, để thay đổi giao diện hoặc log không bao giờ âm thầm làm thay đổi hợp đồng của object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-008">Tránh đệ quy vô hạn trong quan hệ tham chiếu hai chiều</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh đệ quy vô hạn trong quan hệ tham chiếu hai chiều khi viết `toString()`

## 1. Vấn đề: hai đối tượng cùng tham chiếu ngược về nhau

Trong Java, quan hệ **hai chiều** xuất hiện khi một đối tượng giữ tham chiếu tới đối tượng khác và đối tượng kia cũng giữ tham chiếu quay lại. Ví dụ:

- `Department` có danh sách `Employee`;
- mỗi `Employee` lại có tham chiếu `department`;
- cả hai lớp đều override `toString()` để in toàn bộ những trường tham chiếu đó.

Một đối tượng `Department` có thể được xem là gốc của đồ thị:

```text
Department(ENG)
    |
    | employees
    v
Employee(An)
    |
    | department
    +-------------------> Department(ENG)  (chính đối tượng ban đầu)
```

Nếu `Department.toString()` in danh sách nhân viên bằng `employees.toString()`, danh sách lại in từng `Employee.toString()`, rồi `Employee.toString()` tiếp tục in cả `Department`, chương trình quay lại điểm xuất phát. Không có điều kiện dừng, chuỗi gọi sẽ lặp:

```text
Department.toString()
  -> List.toString()
     -> Employee.toString()
        -> Department.toString()
           -> List.toString()
              -> Employee.toString()
                 -> ...
```

Vấn đề xảy ra **dù dữ liệu mô hình hoàn toàn hợp lệ**. Quan hệ hai chiều không sai; việc duyệt tất cả tham chiếu trong `toString()` mà thiếu quy tắc dừng mới là nguyên nhân.

Khi độ sâu lời gọi tăng quá khả năng của stack, JVM thường ném `StackOverflowError`. Không nên dùng `catch (StackOverflowError)` như một chiến lược bình thường: vào lúc đó stack đã cạn và việc phục hồi/in tiếp có thể không đáng tin cậy.

## 2. Vì sao phép nối chuỗi cũng có thể kích hoạt lỗi?

Không nhất thiết phải gọi `obj.toString()` tường minh. Những cách thông dụng sau có thể dẫn tới lời gọi `toString()` trên đối tượng khác `null`:

- `System.out.println(obj)` và `String.valueOf(obj)`;
- `"data=" + obj` hoặc `StringBuilder.append(obj)`;
- phép định dạng chuỗi có đối số đối tượng, chẳng hạn `String.format("%s", obj)`;
- `Collection.toString()` hoặc `Map.toString()` khi chúng trình bày các phần tử, khóa hay giá trị;
- log/debugger/IDE khi chúng chọn hiển thị đối tượng thông qua `toString()`.

Việc log thông qua framework có thể đánh giá đối số theo nhiều thời điểm khác nhau, nhưng khi cuối cùng nó định dạng đối tượng bằng `toString()` thì vẫn tồn tại rủi ro. Không nên giả định một câu lệnh log vô hại chỉ vì người lập trình không gõ tên phương thức.

Đặc biệt, gọi `employees.toString()` không làm Java tự biết rằng trường `employee.department` cần bị bỏ qua. Một số cấu trúc chuẩn có cơ chế bảo vệ **tự chứa trực tiếp** trong cách in của riêng chúng, nhưng không thể suy rộng điều đó thành khả năng nhận biết mọi vòng tham chiếu qua nhiều đối tượng ứng dụng.

## 3. Mẫu cài đặt dẫn đến đệ quy vô hạn

Giả sử `Department` và `Employee` có các trường `employees` và `department`. Đây là **hai phương thức lỗi minh họa**, không phải chương trình Java độc lập:

```java
// Bên trong Department:
@Override
public String toString() {
    return "Department{employees=" + employees + "}";
}

// Bên trong Employee:
@Override
public String toString() {
    return "Employee{department=" + department + "}";
}
```

Diễn biến với một department có nhân viên thuộc chính department đó:

1. In `Department` làm `employees` được chuyển thành chuỗi.
2. Danh sách chuyển các phần tử thành chuỗi, gọi `Employee.toString()`.
3. `Employee.toString()` chuyển `department` thành chuỗi.
4. Đây chính là `Department` ở bước 1. Chuỗi gọi quay lại đầu và tiếp tục vô hạn.

Sự có mặt của `final` trên tham chiếu không loại bỏ vòng. Một đồ thị có thể bất biến về cấu trúc nhưng vẫn chứa chu trình; `toString()` duyệt toàn bộ các cạnh của chu trình sẽ tiếp tục lặp.

Cũng không thể giải quyết gốc rễ bằng cách “tăng stack size” để in thêm nhiều tầng: nó chỉ có thể trì hoãn thời điểm tràn, không đưa vào điều kiện dừng.

## 4. Cách đơn giản và thường nên dùng: chỉ in thuộc tính cần thiết

Phần lớn `toString()` dùng để hỗ trợ quan sát nhanh, debug hoặc log. Nó **không cần trình bày đầy đủ đồ thị đối tượng**.

Với ví dụ Department ↔ Employee, thiết kế gọn và dễ kiểm soát là:

- `Department.toString()` hiển thị `code` và số lượng nhân viên, thay vì in mỗi nhân viên.
- `Employee.toString()` hiển thị `name` và `department.code`, thay vì gọi `department.toString()`.
- Chọn giá trị hiển thị ổn định, ngắn và hữu ích; tránh vô tình kéo theo nhiều đối tượng hoặc dữ liệu nhạy cảm.

Quy tắc thực hành: ở quan hệ hai chiều, ít nhất **một hướng trình bày phải dừng ở thông tin nhận diện**, ví dụ ID, mã, số lượng, hoặc ký hiệu `<omitted>`.

Có thể chọn `departmentId` thay cho đối tượng `department` khi hiển thị nhân viên. Đây là lựa chọn biểu diễn trong `toString()`; không nhất thiết phải bỏ quan hệ hai chiều khỏi mô hình nghiệp vụ.

Nếu cần xuất cả cấu trúc sang JSON hoặc một định dạng trao đổi, hãy thiết kế **serialization/DTO** với chiến lược riêng cho tham chiếu ngược, danh tính, độ sâu và chu trình. `toString()` không nên được xem là serializer tổng quát.

## 5. Nếu bắt buộc in đồ thị: nhận diện đối tượng đã đi qua

Có tình huống chẩn đoán cần hiển thị nhiều tầng tham chiếu. Khi đó, `toString()` hoặc hàm hỗ trợ phải quản lý những đối tượng **đang nằm trên đường đệ quy hiện tại**.

Một cách hữu ích là `IdentityHashMap<Node, Boolean>`:

- `IdentityHashMap` so sánh khóa theo **danh tính tham chiếu** (`==`), không dựa vào `equals()`.
- Nếu đang duyệt một đối tượng rồi gặp lại **chính đối tượng ấy trên cùng đường gọi**, đã phát hiện chu trình.
- Thay vì gọi `toString()` lần nữa, hiển thị một ký hiệu ngắn như `Node(A, cycle)`.
- Gỡ đối tượng khỏi tập đường đi trong `finally` khi duyệt xong, kể cả nếu việc duyệt kết thúc bằng exception.

Tại sao cần so theo identity? Hai đối tượng khác nhau có thể `equals()` là `true` mà không tạo chu trình. Ngược lại, chính `equals()` hoặc `hashCode()` của đối tượng cũng có thể duyệt quan hệ hai chiều hoặc phụ thuộc dữ liệu mutable. Dùng `HashSet` làm tập phát hiện chu trình sẽ gọi các phương thức đó và có thể phát sinh lỗi khác.

Phân biệt hai chiến lược:

- **Active path (đường đệ quy hiện tại):** thêm khi đi vào, gỡ khi quay lui. Chỉ đánh dấu `cycle` khi gặp lại node trên đường đang duyệt. Một node được chia sẻ bởi hai nhánh khác nhau vẫn có thể xuất hiện ở cả hai nhánh.
- **Global visited (đã từng gặp ở bất kỳ nhánh nào):** đánh dấu lâu dài; thích hợp khi muốn không in lặp node, nhưng lần xuất hiện thứ hai của node chia sẻ chưa chắc tạo **chu trình**. Nếu dùng chiến lược này, hãy ghi rõ `already shown` thay vì tự động gọi đó là `cycle`.

Mỗi lần gọi `toString()` nên dùng một ngữ cảnh duyệt riêng; tránh một set dùng chung ở biến `static` cho mọi lời gọi, vì điều đó gây nhiễu giữa các lần in và giữa các thread.

## 6. Ví dụ Java 17 hoàn chỉnh: quan hệ hai chiều và đồ thị có chu trình

Chương trình dưới đây minh họa hai chiến lược an toàn:

1. `Department` và `Employee` chỉ trình bày ID/tóm tắt, nên không gọi `toString()` của nhau liên tục.
2. `Node` cho phép duyệt nhiều tầng liên kết; `IdentityHashMap` phát hiện node đang nằm trên đường duyệt để ngắt chu trình.

Tạo tệp **`ToStringCycleDemo.java`**:

```java
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class ToStringCycleDemo {
    static final class Department {
        private final String code;
        private final List<Employee> employees = new ArrayList<>();

        Department(String code) {
            this.code = Objects.requireNonNull(code);
        }

        void add(Employee employee) {
            employees.add(Objects.requireNonNull(employee));
            employee.department = this;
        }

        @Override
        public String toString() {
            return "Department{code='" + code + "', employeeCount="
                    + employees.size() + "}";
        }
    }

    static final class Employee {
        private final String name;
        private Department department;

        Employee(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public String toString() {
            String departmentCode = department == null ? null : department.code;
            return "Employee{name='" + name + "', departmentCode='"
                    + departmentCode + "'}";
        }
    }

    static final class Node {
        private final String id;
        private final List<Node> links = new ArrayList<>();

        Node(String id) {
            this.id = Objects.requireNonNull(id);
        }

        void link(Node target) {
            links.add(Objects.requireNonNull(target));
        }

        @Override
        public String toString() {
            return render(this, new IdentityHashMap<>());
        }

        private static String render(Node current,
                IdentityHashMap<Node, Boolean> activePath) {
            if (activePath.containsKey(current)) {
                return "Node(" + current.id + ", cycle)";
            }
            activePath.put(current, Boolean.TRUE);
            try {
                StringJoiner children = new StringJoiner(", ");
                for (Node next : current.links) {
                    children.add(render(next, activePath));
                }
                return "Node(" + current.id + " -> [" + children + "])";
            } finally {
                activePath.remove(current);
            }
        }
    }

    public static void main(String[] args) {
        Department department = new Department("ENG");
        Employee an = new Employee("An");
        Employee binh = new Employee("Binh");
        department.add(an);
        department.add(binh);
        System.out.println(department);
        System.out.println(an);

        Node a = new Node("A");
        Node b = new Node("B");
        a.link(b);
        b.link(a);
        System.out.println(a);

        Node root = new Node("R");
        Node shared = new Node("S");
        root.link(shared);
        root.link(shared);
        System.out.println(root);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 ToStringCycleDemo.java
java ToStringCycleDemo
```

Stdout đã kiểm chứng bằng Java 17:

```text
Department{code='ENG', employeeCount=2}
Employee{name='An', departmentCode='ENG'}
Node(A -> [Node(B -> [Node(A, cycle)])])
Node(R -> [Node(S -> []), Node(S -> [])])
```

**Giải thích kết quả:**

1. `Department` biết hai nhân viên và mỗi nhân viên biết department; mặc dù tham chiếu là hai chiều, `Department.toString()` **chỉ in số lượng** và `Employee.toString()` **chỉ in mã**. Không có chuỗi gọi vòng.
2. Ở đồ thị `A → B → A`, khi `render` gặp A lần hai, A vẫn nằm trong `activePath`. Hàm trả `Node(A, cycle)` thay vì đệ quy thêm.
3. `R` liên kết tới **cùng một S hai lần**, nhưng S không trỏ về R và không nằm trên đường hiện tại sau khi kết thúc nhánh đầu. Vì mã dùng active path và gỡ node trong `finally`, S được in ở cả hai nhánh. Đây là **tham chiếu chia sẻ**, không phải chu trình.
4. `Node.toString()` khởi tạo bản đồ mới cho mỗi lần gọi, nên trạng thái theo dõi đường đi không rò sang một lần gọi độc lập khác.

Ví dụ này duyệt tuần tự một đồ thị không bị sửa đồng thời. Nếu có thread khác thêm/xóa liên kết khi đang in, cần chính sách snapshot hoặc đồng bộ phù hợp; `IdentityHashMap` riêng cho mỗi lời gọi không tự làm danh sách liên kết an toàn trước cập nhật đồng thời.

## 7. Giới hạn của giải pháp phát hiện chu trình

Phát hiện chu trình **không đồng nghĩa với loại bỏ mọi rủi ro đệ quy sâu**.

### 7.1. Đồ thị không có chu trình vẫn có thể rất sâu

Một chuỗi dài `A → B → C → ...` với hàng trăm nghìn node có thể làm stack tràn dù không hề gặp lại node nào. Nếu in đồ thị lớn hoặc do bên ngoài cung cấp, nên giới hạn:

- **Độ sâu tối đa**, ví dụ chỉ in 3–5 cấp liên kết;
- **số node/cạnh tối đa** được phép trình bày;
- **độ dài chuỗi tối đa**, tránh log quá lớn;
- hoặc dùng **thuật toán duyệt lặp** (stack/queue tường minh) khi thực sự cần biểu diễn đồ thị lớn.

Khi chạm giới hạn, đưa ký hiệu ngắn như `<max-depth>` hoặc `<truncated>` để người đọc hiểu rằng output đã được giới hạn. Không nên cắt chuỗi một cách âm thầm khiến nó trông như dữ liệu đầy đủ.

### 7.2. Tham chiếu được in bởi formatter khác

Bộ theo dõi `activePath` chỉ giúp các node được duyệt **thông qua cùng hàm `render`**. Nếu trong quá trình render bạn tự nối `+ someOtherObject`, Java có thể gọi `someOtherObject.toString()` bên ngoài cơ chế theo dõi hiện tại và tái tạo vòng lặp.

Nếu cần hiển thị nhiều kiểu đối tượng trong cùng đồ thị, nên dùng **một formatter có ngữ cảnh duyệt chung** truyền vào các hàm hỗ trợ, hoặc chọn biểu diễn nhận diện rút gọn cho những cạnh không duyệt tiếp. Một biến toàn cục duy nhất không phải lối tắt an toàn cho thiết kế này.

### 7.3. Hiệu năng và dữ liệu nhạy cảm

Một `toString()` quá sâu hoặc in toàn bộ collection lớn có thể tốn CPU, bộ nhớ và tạo log kích thước rất lớn. In cả đối tượng liên quan còn có nguy cơ vô tình đưa password, token, email hoặc thông tin cá nhân vào log. Thiết kế output nên **ngắn, có chủ đích và tránh trường nhạy cảm**, dù dữ liệu không chứa chu trình.

Với framework ORM, việc `toString()` đọc quan hệ lazy có thể kích hoạt tải dữ liệu ngoài dự kiến hoặc không truy cập được quan hệ nếu persistence context đã đóng. Giữ `toString()` theo ID và trường scalar thường vừa tránh vòng, vừa tránh tải thêm đồ thị quan hệ.

## 8. Các công cụ sinh `toString()` có tự tránh vòng không?

### 8.1. IDE sinh `toString()`

IDE có thể tự đưa các trường vào output theo lựa chọn của người dùng. Nếu chọn cả `department.employees` và `employee.department`, mã được sinh vẫn có thể đệ quy vô hạn. Hãy kiểm tra nội dung được sinh, nhất là các trường quan hệ object và collection.

### 8.2. Lombok `@ToString`

Nếu dùng Lombok, tính năng `@ToString` có thể đưa các field vào chuỗi; có thể sử dụng `@ToString.Exclude` để loại trừ phía tham chiếu ngược, hoặc `@ToString(onlyExplicitlyIncluded = true)` cùng `@ToString.Include` để chỉ cho phép những thuộc tính định danh cần thiết.

Ví dụ **đoạn cấu hình minh họa** (cần dependency Lombok, không thuộc tệp demo Java thuần ở trên):

```java
@ToString
class Employee {
    private String id;
    @ToString.Exclude
    private Department department;
}
```

Phần này minh họa hướng loại trừ, không phải chương trình chạy độc lập. Nếu nhiều lớp cùng dùng Lombok, cần rà soát **mọi cạnh quan hệ hai chiều**, đặc biệt khi thay đổi field sau này.

### 8.3. Java record

Record tạo `toString()` tự động từ các component. Điều đó tiện cho record chứa giá trị đơn giản như `String` hoặc `int`; nhưng record không làm các đối tượng tham chiếu bên trong trở nên bất biến sâu hay không có chu trình.

Nếu component là một graph object mà `toString()` của nó quay về record hoặc một nhánh tham chiếu cũ, vẫn có rủi ro. Có thể override `toString()` của record để chỉ in thông tin định danh phù hợp.

### 8.4. Array và collection helper

`Arrays.deepToString` có cơ chế xử lý một số tình huống mảng chứa tham chiếu vòng tới chính mảng/nhánh mảng đang duyệt, nhưng **không phải trình in chu trình tổng quát cho mọi object graph**. Một phần tử là POJO với `toString()` đi vòng qua quan hệ riêng vẫn có thể gây đệ quy.

Vì vậy đừng dùng một helper có chữ `deep` rồi suy luận rằng mọi tham chiếu hai chiều đều an toàn. Phải xác định rõ kiểu dữ liệu và ranh giới traversal.

## 9. Cách kiểm tra một `toString()` an toàn

Với một mô hình có quan hệ hai chiều, nên thử ít nhất các trường hợp:

1. **Đối tượng đơn lẻ:** object không có liên kết; kết quả ngắn và dễ hiểu.
2. **Quan hệ hai chiều đơn giản:** `Department → Employee → Department`; không xảy ra đệ quy vô hạn.
3. **Chu trình dài:** `A → B → C → A`; bộ phát hiện chu trình cắt đúng chỗ.
4. **Tự tham chiếu:** `A → A`; điều kiện dừng vẫn hoạt động.
5. **Tham chiếu chia sẻ nhưng không có vòng:** `R → S` qua hai cạnh; nếu dùng active path, không đánh dấu nhầm S là cycle.
6. **Đồ thị rỗng, phần tử null nếu mô hình cho phép:** không ném ngoại lệ do thiếu tham chiếu.
7. **Đồ thị rất sâu/rộng:** có giới hạn độ sâu và độ dài khi cần; không vô tình sinh log khổng lồ.
8. **Không lộ dữ liệu nhạy cảm:** kiểm tra output dự kiến và các field mới thêm.
9. **Tác động ORM:** nếu dùng quan hệ lazy, việc gọi `toString()` không bất ngờ làm tăng số truy vấn hoặc phụ thuộc phiên làm việc.

Ví dụ một phép kiểm tra hồi quy đặt trong code test Java sau khi tạo đồ thị `A → B → A`:

```java
String output = a.toString();
if (!output.contains("cycle")) {
    throw new AssertionError("Chu trinh chua duoc ngan");
}
```

Đây là **mảnh kiểm thử minh họa**, giả định biến `a` đã được tạo trong test. Kiểm tra còn nên xác nhận output thực tế khớp cấu trúc kỳ vọng. Không cố ý kích hoạt `StackOverflowError` trong test để chứng minh mã sai; thay bằng test các thiết kế an toàn và review đường gọi.

## 10. Câu hỏi thường gặp

### “Tôi chỉ cần in đối tượng cha, không gọi `child.toString()`, sao vẫn lỗi?”

Vì nối một collection vào chuỗi có thể khiến collection gọi `toString()` của từng phần tử. Đường gọi gián tiếp vẫn quay lại cha.

### “Bỏ `toString()` ở một phía có đủ không?”

Thường **đủ để cắt một vòng cụ thể** nếu phía còn lại chỉ in dữ liệu an toàn. Nhưng một đồ thị có nhiều đường liên kết có thể còn chu trình khác. Xem toàn bộ các cạnh mà formatter sẽ đi qua.

### “Dùng `HashSet` để ghi các object đã thăm được không?”

Có thể rủi ro khi `equals/hashCode` của chúng đệ quy theo quan hệ hoặc thay đổi. Với mục tiêu nhận biết **cùng instance đã nằm trên đường duyệt**, `IdentityHashMap` cho ngữ nghĩa phù hợp và rõ ràng hơn.

### “Nếu `toString()` gặp `null` thì sao?”

`String.valueOf(null)` tạo chuỗi `"null"`; nhưng gọi trực tiếp `null.toString()` là không hợp lệ. Trong formatter, kiểm tra `null` trước khi truy cập field hoặc đi tiếp qua liên kết.

### “Có nên trả về JSON từ `toString()`?”

Không bắt buộc và thường không phù hợp. `toString()` dành cho biểu diễn dễ đọc, chẩn đoán; trao đổi dữ liệu có schema và graph cycles cần serializer/DTO và cấu hình rõ ràng. Hãy chọn công cụ theo mục đích thay vì dựa vào một phương thức in đồ thị vô hạn.

## 11. Ghi nhớ

- Quan hệ hai chiều `A → B → A` là bình thường trong mô hình; **in cả hai phía trong `toString()`** có thể tạo vòng gọi vô hạn.
- Giải pháp đơn giản nhất là **in ID, scalar hoặc số lượng** ở một hay cả hai phía, thay vì in toàn bộ object liên kết.
- Nếu phải in nhiều tầng, theo dõi đường duyệt bằng **identity**, dừng khi gặp lại node đang active; phân biệt chu trình thật với node được chia sẻ.
- Tạo ngữ cảnh duyệt riêng cho mỗi lần gọi, dọn trong `finally`, và giới hạn độ sâu/kích thước đối với đồ thị lớn.
- Rà soát `toString()` do IDE, Lombok, record hoặc framework sinh ra; đừng mặc định chúng tự xử lý mọi vòng tham chiếu.
- Không dùng `StackOverflowError`, tăng stack hay log suppression như giải pháp gốc. Thiết kế đường duyệt và điều kiện dừng rõ ràng mới làm `toString()` an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-009">Tránh tính toán nặng hoặc tác dụng phụ khi tạo biểu diễn</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tính toán nặng hoặc tác dụng phụ khi tạo biểu diễn `toString()`

`toString()` là phương thức của `java.lang.Object` trả về một chuỗi mô tả đối tượng. Lập trình viên thường override phương thức này để đọc log, debug, xem trạng thái trong IDE hoặc hiển thị nhanh một giá trị. Vì vậy, **`toString()` nên dễ gọi, nhanh, có giới hạn và không gây tác dụng phụ ngoài ý muốn**.

Một cách cài đặt có vấn đề là mỗi lần `toString()` lại truy vấn cơ sở dữ liệu, gọi HTTP, đọc tệp, tính toán trên hàng triệu phần tử, thay đổi bộ đếm, cập nhật cache hoặc sửa đối tượng. Một thao tác tưởng như vô hại — `System.out.println(obj)` hoặc ghi log — khi đó có thể làm chương trình chậm, tạo lỗi khó tìm hoặc thay đổi hành vi nghiệp vụ.

Mục tiêu thực tế là: **chuỗi biểu diễn chỉ mô tả trạng thái đã có hoặc một phần tóm tắt đủ rẻ để lấy**, còn nghiệp vụ tốn kém và các thao tác có tác dụng phụ cần được gọi qua phương thức có tên, mục đích và chi phí rõ ràng.

## 1. Vì sao phải quan tâm đến chi phí của `toString()`?

Java có thể gọi `toString()` trong nhiều ngữ cảnh:

- `System.out.println(object)` chuyển đối tượng thành chuỗi trước khi in.
- `"order = " + order`, `String.valueOf(order)` và `String.format("%s", order)` tạo biểu diễn dạng chuỗi.
- Khi một `List`, `Set`, `Map` được chuyển thành chuỗi, biểu diễn của các phần tử/key/value có thể được gọi tiếp.
- Công cụ debug, hệ thống log, trình kiểm tra lỗi hoặc mã thư viện có thể yêu cầu chuỗi biểu diễn để cung cấp ngữ cảnh.

Đó là lý do **không nên giả định chỉ có một nơi duy nhất gọi `toString()`**. Một đối tượng có thể được in nhiều lần trong một request, hay từ nhiều luồng vào thời điểm khó dự đoán. Những tác dụng phụ nằm trong `toString()` sẽ xảy ra chỉ vì có người yêu cầu xem thông tin.

Ví dụ một đoạn code tưởng đơn giản:

~~~java
System.out.println("Current order: " + order);
~~~

Đây là mã minh họa nằm trong một phương thức có biến `order`. Phép ghép chuỗi buộc Java chuyển `order` thành chuỗi. Nếu `Order.toString()` gọi dịch vụ bên ngoài, chính lệnh in này có thể gây I/O, timeout hoặc ném ngoại lệ.

**Bản chất của `toString()`:** nó là phương thức thông thường có thể chứa bất kỳ mã Java nào; JVM không tự ngăn việc đọc/ghi dữ liệu hay tính toán nặng. Quy tắc “nhẹ và không tác dụng phụ” là **nguyên tắc thiết kế**, giúp giảm rủi ro khi sử dụng phương thức thường xuyên.

## 2. Thế nào là “tính toán nặng” và “tác dụng phụ”?

**Tính toán nặng** là công việc có chi phí lớn hoặc khó dự đoán về thời gian, bộ nhớ, CPU hay I/O. **Tác dụng phụ** là việc lời gọi làm thay đổi trạng thái quan sát được bên ngoài kết quả trả về, hoặc kích hoạt hành động ngoài mục tiêu tạo chuỗi.

| Mẫu cài đặt trong `toString()` | Vấn đề tiềm ẩn | Cách tiếp cận phù hợp hơn |
| --- | --- | --- |
| Tính tổng/phân tích toàn bộ tập rất lớn ở mỗi lần gọi | CPU tăng theo số phần tử; lặp lại công việc | Dùng dữ liệu tổng hợp đã lưu sẵn nếu cần và luôn được cập nhật đúng; hoặc chỉ in kích thước/tóm tắt. |
| Gọi SQL/API/network, đọc tệp | Có I/O, timeout, lỗi hạ tầng, có thể tạo truy vấn bất ngờ | Tách sang `loadDetails()` hay phương thức nghiệp vụ tường minh. |
| Tự động truy cập quan hệ ORM tải chậm (lazy association) | Có thể tạo truy vấn, tải cả đồ thị, hoặc lỗi khi session đóng | In ID/trường scalar đã có; tránh duyệt quan hệ trong `toString()`. |
| `counter++`, thêm vào collection, thay trạng thái | Gọi để quan sát lại làm đổi dữ liệu | Dời thao tác cập nhật sang phương thức ghi có chủ đích. |
| Gọi `toString()` của mọi phần tử trong collection lớn | Chuỗi khổng lồ; phần tử có thể chứa tác dụng phụ/đệ quy | Giới hạn số phần tử; ưu tiên các mã định danh hay thuộc tính đơn giản. |
| Đi sâu vào quan hệ hai chiều hoặc đồ thị có vòng | Đệ quy vô hạn, `StackOverflowError` | Chỉ hiển thị ID/summary và giới hạn độ sâu. |
| Khóa lâu, chờ tương lai hoàn thành, sleep | Khó dự đoán độ trễ; nguy cơ chặn luồng log/debug | Đọc snapshot đã có, không chờ công việc bất đồng bộ. |
| In mật khẩu, token, dữ liệu cá nhân | Lộ thông tin qua log và công cụ quan sát | Chỉ in trường an toàn; che hoặc bỏ dữ liệu nhạy cảm. |

Không phải mọi phép tính trong `toString()` đều xấu. Ghép vài trường số/chuỗi, định dạng một mã ngắn hoặc đọc các trường đã có thường là hợp lý. **Vấn đề là chi phí mất kiểm soát, công việc bị lặp lại và hành vi ngoài việc mô tả đối tượng.**

## 3. Ví dụ chạy được: một `toString()` có tác dụng phụ

Tệp **`ToStringCostDemo.java`**, dùng Java 17+:

~~~java
import java.util.List;

public class ToStringCostDemo {
    static final class BadReport {
        private final List<Integer> amounts;
        private int renderCount;

        BadReport(List<Integer> amounts) {
            this.amounts = List.copyOf(amounts);
        }

        @Override
        public String toString() {
            renderCount++; // Tác dụng phụ chỉ vì tạo biểu diễn.
            int total = 0;
            for (int amount : amounts) {
                total += amount; // Quét toàn bộ danh sách mỗi lần.
            }
            return "Report(total=" + total + ", renderCount=" + renderCount + ")";
        }

        int renderCount() {
            return renderCount;
        }
    }

    static final class GoodReport {
        private final int id;
        private final int itemCount;
        private final int total;

        GoodReport(int id, List<Integer> amounts) {
            this.id = id;
            this.itemCount = amounts.size();
            int sum = 0;
            for (int amount : amounts) {
                sum = Math.addExact(sum, amount);
            }
            this.total = sum;
        }

        @Override
        public String toString() {
            return "Report(id=" + id
                + ", items=" + itemCount
                + ", total=" + total + ")";
        }
    }

    public static void main(String[] args) {
        List<Integer> amounts = List.of(4, 6, 10);
        BadReport bad = new BadReport(amounts);

        System.out.println(bad.toString());
        System.out.println(bad.toString());
        System.out.println("renderCount=" + bad.renderCount());

        GoodReport good = new GoodReport(12, amounts);
        System.out.println(good);
        System.out.println(String.valueOf(good));
        System.out.println(good.toString().equals(good.toString()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ToStringCostDemo.java
java ToStringCostDemo
~~~

Kết quả chuẩn:

~~~text
Report(total=20, renderCount=1)
Report(total=20, renderCount=2)
renderCount=2
Report(id=12, items=3, total=20)
Report(id=12, items=3, total=20)
true
~~~

Ở `BadReport`, **hai lần gọi** `toString()` làm `renderCount` tăng từ `0` lên `2`, dù người dùng chỉ muốn đọc chuỗi. Mỗi lần cũng quét toàn bộ `amounts`. Với danh sách lớn, việc ghi log lặp lại có thể tạo chi phí đáng kể.

Ở `GoodReport`, dữ liệu tổng hợp được **tính một lần khi tạo đối tượng** và lưu vào các trường `final`. `toString()` chỉ kết hợp vài giá trị đã có, không sửa trạng thái. Hai lời gọi cho cùng chuỗi khi trạng thái không thay đổi.

**Không nên hiểu ví dụ là luôn phải tính trước mọi phép tổng hợp.** Constructor của `GoodReport` vẫn cần thời gian tuyến tính theo kích thước danh sách và `Math.addExact` có thể ném `ArithmeticException` nếu tổng tràn `int`. Nếu tổng chưa cần thiết cho nghiệp vụ, thiết kế tốt hơn có thể **không tính tổng ở đâu cả khi tạo đối tượng**, và chỉ in `id` cùng `itemCount`. Việc tiền xử lý phải có lý do nghiệp vụ hoặc hiệu năng rõ ràng.

Ngoài ra, dữ liệu đầu vào của ví dụ là `List<Integer>` bất biến về các phần tử theo kiểu `Integer`. Với class mutable thực tế, dữ liệu tổng hợp lưu sẵn phải được cập nhật đúng mỗi khi trạng thái thay đổi; nếu không, biểu diễn có thể bị lỗi thời. Đối tượng bất biến thường giúp đơn giản hóa bảo đảm này.

## 4. Nguyên tắc: `toString()` nên quan sát, không điều khiển chương trình

Trong thực tế, hãy coi `toString()` là thao tác **quan sát trạng thái**, không phải điểm kích hoạt nghiệp vụ.

### Không thực hiện I/O hay tải dữ liệu

Tránh các ý tưởng như sau:

~~~java
@Override
public String toString() {
    // Phản ví dụ minh họa, KHÔNG phải class chạy độc lập.
    return "Customer(" + id + ", orders=" + orderRepository.findByCustomer(id) + ")";
}
~~~

Lệnh trên chỉ mang tính minh họa trong một class có các trường/biến phù hợp; `orderRepository` là phụ thuộc giả định. Nó tạo nguy cơ:

- Lời gọi log dẫn đến truy vấn dữ liệu.
- Thời gian tạo chuỗi phụ thuộc database/network.
- Một lần in danh sách khách hàng sinh nhiều truy vấn (vấn đề N+1).
- `toString()` thất bại do session đóng hoặc tài nguyên bên ngoài lỗi.
- Chuỗi có thể chứa một lượng lớn dữ liệu không cần thiết.

Thay bằng biểu diễn giới hạn trên các trường đã có, ví dụ `"Customer(id=" + id + ")"`. Khi cần chi tiết, hãy gọi phương thức `loadOrders()`, `fetchCustomerDetails()` hoặc một truy vấn rõ mục đích tại nơi chịu trách nhiệm nghiệp vụ.

### Không sửa dữ liệu, gửi sự kiện hay đo lường ẩn

Nếu `toString()` làm `updateStatus()`, `save()`, `publishEvent()`, `retry()`, `count++` hoặc cập nhật trạng thái toàn cục, chỉ riêng việc chuyển đối tượng thành chuỗi có thể làm thay đổi kết quả chương trình.

**Đừng biến việc debug thành một hành động nghiệp vụ.** Lời gọi `toString()` có thể xảy ra tự động khi framework ghi log hoặc môi trường phát triển hiển thị đối tượng. Khi phải ghi metrics hay audit, gọi thao tác tường minh ở đúng ranh giới nghiệp vụ; không gắn việc này với tạo biểu diễn.

### Giữ kết quả ổn định khi trạng thái ổn định

Không có quy định API bắt buộc `toString()` của mọi đối tượng phải trả một chuỗi duy nhất suốt vòng đời. Với đối tượng mutable, chuỗi có thể đổi khi dữ liệu đổi. Nhưng **hai lần gọi liên tiếp trên cùng trạng thái nên cho thông tin giống nhau**, trừ khi kiểu dữ liệu có yêu cầu khác được tài liệu hóa rõ.

Tránh đưa ngẫu nhiên, số đếm lần render, thời gian hiện tại hoặc chi phí ngoại vi vào chuỗi chỉ vì dễ thực hiện. Nếu cần thời điểm tạo bản ghi, hãy ghi thời điểm đó vào trường nghiệp vụ tại một hành động tường minh, sau đó `toString()` chỉ hiển thị trường đã lưu.

## 5. Giới hạn số phần tử và độ dài biểu diễn

Một `toString()` in hàng triệu phần tử không nhất thiết có tác dụng phụ, nhưng vẫn có thể quá chậm và chiếm nhiều bộ nhớ. `Collection.toString()` thường duyệt phần tử và tạo chuỗi biểu diễn tương ứng; mỗi phần tử còn có thể gọi `toString()` riêng.

Nên tóm tắt **số lượng và một mẫu nhỏ** thay vì in tất cả dữ liệu. Với giá trị dạng `String` đã được kiểm soát, ví dụ có thể làm như sau.

Tệp **`BoundedToStringDemo.java`**:

~~~java
import java.util.List;

public class BoundedToStringDemo {
    static final class TaskBatch {
        private final int id;
        private final List<String> taskIds;

        TaskBatch(int id, List<String> taskIds) {
            this.id = id;
            this.taskIds = List.copyOf(taskIds);
        }

        @Override
        public String toString() {
            int shown = Math.min(3, taskIds.size());
            return "TaskBatch(id=" + id
                + ", size=" + taskIds.size()
                + ", sample=" + taskIds.subList(0, shown)
                + (shown < taskIds.size() ? ", ..." : "")
                + ")";
        }
    }

    public static void main(String[] args) {
        TaskBatch shortBatch = new TaskBatch(1, List.of("T1", "T2"));
        TaskBatch longBatch = new TaskBatch(
            2, List.of("T1", "T2", "T3", "T4", "T5")
        );

        System.out.println(shortBatch);
        System.out.println(longBatch);
    }
}
~~~

Chạy:

~~~bash
javac BoundedToStringDemo.java
java BoundedToStringDemo
~~~

Kết quả:

~~~text
TaskBatch(id=1, size=2, sample=[T1, T2])
TaskBatch(id=2, size=5, sample=[T1, T2, T3], ...)
~~~

Ví dụ dùng danh sách ID dạng `String` để bảo đảm các phần tử được hiển thị ngắn và không có tác dụng phụ. Nếu `taskIds` chứa đối tượng có `toString()` phức tạp, việc lấy `subList` nhỏ **chưa tự bảo đảm an toàn**: vẫn cần chọn các trường ID an toàn hoặc chủ động kiểm soát cách định dạng từng phần tử.

`List.copyOf` trong constructor tạo một danh sách không cho phép sửa cấu trúc và không chấp nhận phần tử `null`; đây là lựa chọn thiết kế của ví dụ. Một dữ liệu rất lớn vẫn mất chi phí sao chép **khi tạo đối tượng**, nhưng mỗi lần `toString()` chỉ biểu diễn tối đa ba ID đầu tiên. Độ dài tổng thể vẫn phụ thuộc độ dài từng ID; nếu ID là văn bản dài không kiểm soát, phải bổ sung giới hạn độ dài chuỗi để có **trần output thực sự**.

## 6. Tránh đệ quy vô hạn với quan hệ đối tượng có vòng

Một kiểu đối tượng có tham chiếu ngược có thể gây lỗi khi `toString()` tự động đi sâu vào quan hệ:

~~~text
Parent -> Child -> Parent -> Child -> ...
~~~

Giả sử `Parent.toString()` in toàn bộ `children`, trong khi `Child.toString()` lại in toàn bộ `parent`. Chỉ một lần ghi log parent có thể dẫn tới chuỗi lời gọi lặp lại không kết thúc và gây `StackOverflowError`.

Đối với mô hình quan hệ hai chiều, đồ thị hoặc cây có thể có vòng, hãy chỉ mô tả các thuộc tính cục bộ, ID của đối tượng liên quan, số lượng phần tử hoặc một độ sâu có kiểm soát. Ví dụ:

~~~java
final class Node {
    private final int id;
    private Node next;

    Node(int id) {
        this.id = id;
    }

    void link(Node next) {
        this.next = next;
    }

    @Override
    public String toString() {
        return "Node(id=" + id
            + ", nextId=" + (next == null ? "null" : next.id)
            + ")";
    }
}
~~~

Đây là **một khai báo class minh họa**, có thể đặt cạnh các class khác trong cùng tệp Java. Nếu gọi `Node a = new Node(1)`, `Node b = new Node(2)`, rồi `a.link(b)` và `b.link(a)`, `a.toString()` sẽ trả `Node(id=1, nextId=2)`. Nó không gọi `next.toString()`, nên không lặp qua vòng liên kết.

Đừng suy luận rằng `StringBuilder` tự giải quyết được đệ quy: nếu nối trực tiếp một object khác vào builder, Java vẫn có thể gọi `toString()` của object đó.

## 7. Log và `toString()`: tránh chi phí ngay cả khi không cần log

Một ví dụ thường gặp với logging framework:

~~~java
// Minh họa trong phương thức có logger và order:
logger.debug("Order details: " + order);

// Một kiểu gọi logging theo tham số:
logger.debug("Order details: {}", order);
~~~

Dòng đầu **tạo chuỗi ngay khi thực thi phép ghép**, vì vậy `order.toString()` có thể bị gọi trước khi framework quyết định có ghi mức DEBUG hay không. Dòng thứ hai có thể **hoãn định dạng** tùy logging API và cấu hình, nên thường tránh được một phần công việc khi log bị tắt. Tuy nhiên, **không có bảo đảm chung rằng mọi framework, mọi mức log hay mọi cơ chế tham số hóa đều không gọi `toString()`**. Không được biến lời khuyên dùng placeholder thành lý do để đặt công việc nặng trong `toString()`.

Nếu việc tạo thông tin chi tiết tự nó rất tốn kém, hãy kiểm tra khả năng dùng logging API hỗ trợ lazy evaluation/supplier; hoặc điều kiện `logger.isDebugEnabled()` khi phù hợp, rồi mới xây dựng nội dung trong nhánh đó. Chọn giải pháp theo framework và nhu cầu thực tế; không phải lúc nào một lệnh kiểm tra mức log cũng cần thiết.

Ngoài chi phí, còn phải cân nhắc **tính an toàn của dữ liệu log**. Một `toString()` in token, mật khẩu, thông tin thanh toán hay dữ liệu định danh có thể làm lộ dữ liệu tới tập tin log hoặc công cụ quan sát. Các trường nhạy cảm nên bị bỏ qua hoặc che giá trị. Dùng chuỗi biểu diễn cho debug **không đồng nghĩa** với việc đã có quyền phát tán tất cả trạng thái của đối tượng.

## 8. Giữ biểu diễn ngắn, có ích và không phụ thuộc cơ sở dữ liệu

Một mẫu `toString()` thực dụng cho entity nghiệp vụ là in **loại đối tượng, ID và một số trạng thái cơ bản đã có**:

~~~java
@Override
public String toString() {
    return "Payment(id=" + id + ", status=" + status + ")";
}
~~~

Đây là đoạn mã trong một class có hai trường `id` và `status`. Nếu `status` là enum hoặc chuỗi ngắn, việc tạo biểu diễn thường đơn giản. Nếu `status` lại là một đối tượng phức tạp có `toString()` riêng, vẫn cần kiểm tra chi phí của phép gọi đó.

Tránh kiểu:

~~~java
@Override
public String toString() {
    // Phản ví dụ: phụ thuộc giả định trong class, KHÔNG chạy độc lập.
    return "Payment(" + paymentService.refresh(id) + ")";
}
~~~

Một phương thức tên `refresh` gợi ý rõ thao tác thay đổi hoặc tải lại trạng thái. Nó thuộc luồng nghiệp vụ hay đồng bộ dữ liệu riêng, không nên được kích hoạt mỗi khi cần in đối tượng.

Nếu một ứng dụng cần biểu diễn **chi tiết để xuất báo cáo, serialize JSON hoặc hiển thị UI**, hãy tạo hàm/DTO/serializer dành riêng cho mục tiêu đó. `toString()` chủ yếu là biểu diễn thuận tiện để hỗ trợ quan sát/debug; nó **không phải định dạng dữ liệu ổn định dành cho tích hợp giữa các hệ thống**.

### Có nên cache chuỗi `toString()`?

Không có đáp án chung cho mọi đối tượng.

- Với một value object **thực sự bất biến** và biểu diễn tốn một chút công sức, cache chuỗi sau lần tạo có thể là lựa chọn có ích nếu đã đo được lợi ích.
- Với đối tượng **mutable**, cache biểu diễn đòi hỏi cơ chế vô hiệu hóa chính xác khi trường liên quan thay đổi. Nếu không, `toString()` có thể trả dữ liệu lỗi thời.
- Cache cũng có chi phí bộ nhớ và các vấn đề đồng thời nếu tự cài đặt không phù hợp. Thường nên bắt đầu bằng **biểu diễn đơn giản và rẻ**, chỉ thêm cache sau khi có số liệu hiệu năng.

Một nguyên tắc an toàn: **đừng cài cache có tác dụng phụ lớn trong `toString()` chỉ vì mong kết quả nhanh hơn**. Việc thiết kế cache thuộc phần quản lý trạng thái, cần được quyết định rõ ràng.

## 9. `record`, code generator và phương thức được sinh sẵn

Java record sinh `toString()` từ các thành phần được khai báo. Điều này rất tiện với các record nhỏ chứa `int`, `String` hay enum đơn giản. Tuy nhiên, nếu thành phần là **đối tượng phức tạp, collection lớn hoặc giá trị nhạy cảm**, biểu diễn được sinh tự động có thể không phù hợp cho log.

~~~java
record AccountView(long id, String accessToken) {}

AccountView view = new AccountView(5L, "SECRET");
System.out.println(view); // Sẽ có trường accessToken trong biểu diễn.
~~~

Hai dòng cuối là mã minh họa trong một phương thức Java; nội dung `"SECRET"` chỉ là dữ liệu mẫu. **Không nên in token thật**. Nếu đối tượng chứa dữ liệu nhạy cảm, bạn có thể override `toString()` để chỉ hiển thị trường an toàn hoặc không log đối tượng đó.

Tương tự, nếu IDE hay thư viện sinh tự động `toString()` dựa trên **toàn bộ trường**, cần xem lại những trường nào là quan hệ ORM, dữ liệu lớn, tham chiếu ngược hoặc giá trị bí mật. Sinh tự động giúp giảm công viết mã nhưng **không thay thế quyết định về phạm vi và chi phí của biểu diễn**.

Cũng cần nhớ: `Object.toString()` mặc định thường cho biểu diễn dựa trên tên class và một giá trị hash dạng hex; không phải địa chỉ bộ nhớ hay khóa duy nhất đáng tin cậy. Nếu chưa cần thêm thông tin, không có nghĩa mọi class đều bắt buộc override `toString()`.

## 10. Kiểm thử hành vi `toString()`

Một bài test có giá trị cần kiểm tra cả **nội dung hiển thị**, **việc không thay đổi trạng thái**, và **giới hạn dữ liệu**. Ví dụ:

1. Gọi `toString()` hai lần liên tiếp trên cùng trạng thái; chuỗi có giống nhau không?
2. Ghi lại các trường có thể thay đổi trước lời gọi và đối chiếu sau; việc tạo chuỗi có làm thay đổi trạng thái nghiệp vụ không?
3. Với collection lớn, chuỗi có in ra số lượng giới hạn hay quét toàn bộ phần tử?
4. Với quan hệ hai chiều, lời gọi có đi vòng mãi giữa hai object không?
5. Với đối tượng có trường `null`, `toString()` có xử lý được mà không gây `NullPointerException` bất ngờ không?
6. Với entity có quan hệ tải chậm, việc chỉ in ID có tránh truy vấn dữ liệu ngoài dự kiến không?
7. Chuỗi có vô tình chứa token, mật khẩu, nội dung nhạy cảm hay thông tin quá chi tiết không?

Không nên viết test chỉ kiểm tra một lần `toString()` chứa đúng từ khóa rồi kết luận phương thức an toàn. Với các loại object dùng trong log, khả năng **không gây hành động ngoài ý muốn** thường quan trọng ngang với định dạng.

### Phân biệt điều có thể kiểm chứng và điều cần đo

Kiểm thử đơn vị có thể xác nhận bộ đếm không đổi, dịch vụ giả lập không bị gọi, hoặc chuỗi chỉ có tối đa một số phần tử nhất định. Nhưng độ trễ thực tế phụ thuộc dữ liệu, phần cứng, JVM và môi trường. Đừng khẳng định `toString()` “O(1)” nếu chuỗi chứa trường `String` dài không giới hạn; việc sao chép và xây dựng chuỗi vẫn cần thời gian theo số ký tự đầu ra. Có thể nói rõ ràng hơn rằng biểu diễn **không quét toàn bộ collection** và **không gọi I/O**.

## 11. Checklist triển khai

Trước khi chốt một `toString()`, kiểm tra:

- **Mục đích:** chuỗi mô tả giúp nhận biết object (loại, ID, trạng thái ngắn), không làm nhiệm vụ truy vấn hay serialize đầy đủ.
- **Chi phí:** chỉ đọc các trường sẵn có; tránh duyệt toàn bộ cấu trúc lớn hoặc tính toán lặp lại không cần thiết.
- **Tác dụng phụ:** không sửa field, không tăng bộ đếm, không gửi request, không ghi database, không tải dữ liệu hay kích hoạt lazy load.
- **Giới hạn:** tránh chuỗi khổng lồ, in toàn bộ đồ thị hoặc vòng tham chiếu; chọn giới hạn phần tử/độ sâu/độ dài phù hợp.
- **Bảo mật:** không đưa secret và thông tin riêng tư không cần thiết vào biểu diễn.
- **Tính ổn định:** cùng trạng thái thường tạo ra biểu diễn giống nhau; không phụ thuộc random hoặc đồng hồ nếu không có mục đích rõ.
- **Tương tác hệ thống:** xem xét nơi `toString()` bị gọi gián tiếp, đặc biệt khi logging, debug, ghép chuỗi và các collection.
- **Kiểm chứng:** kiểm tra output, trạng thái sau nhiều lần gọi và hành vi với dữ liệu lớn/quan hệ lồng.

## 12. Câu hỏi tự kiểm tra

1. Nếu `toString()` gọi database để lấy danh sách đơn hàng, vì sao chỉ một câu lệnh `println(customer)` lại có thể làm chương trình chậm hoặc lỗi?
2. Trong `BadReport`, sau hai lần gọi `toString()`, trường `renderCount` đổi thế nào? Điều đó thể hiện tác dụng phụ gì?
3. Một `toString()` chỉ đọc `id` và `status` đã có, so với một `toString()` duyệt hàng triệu phần tử, cách nào dễ kiểm soát chi phí hơn?
4. Với quan hệ `Parent ↔ Child`, vì sao gọi `toString()` của đối tượng liên quan ở cả hai phía có thể gây `StackOverflowError`?
5. `logger.debug("Order: " + order)` có thể gọi `order.toString()` ngay cả khi mức DEBUG bị tắt không? Vì sao?
6. Một record có trường `accessToken`: vì sao không nên mặc định log nguyên record? Bạn có thể thiết kế biểu diễn an toàn hơn như thế nào?
7. Nếu dùng cache cho chuỗi `toString()` của đối tượng mutable, cần điều kiện gì để không trả về dữ liệu cũ?
8. Vì sao ngay cả một `toString()` không có I/O cũng có thể tốn nhiều thời gian nếu chuỗi trả về quá dài?

**Ghi nhớ:** `toString()` là công cụ **mô tả**, không phải nơi **thực hiện nghiệp vụ**. Hãy giữ phương thức nhẹ, đọc trạng thái sẵn có, giới hạn kích thước và tránh tác dụng phụ, I/O, đệ quy vô hạn cũng như rò rỉ dữ liệu. Công việc tốn kém cần có API tường minh để người gọi hiểu lúc nào và vì sao nó được thực hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="to-string-representation-010">Arrays.toString(), deepToString() và biểu diễn thành phần lồng</a>

<details>
<summary>Xem chi tiết</summary>

# `Arrays.toString()`, `Arrays.deepToString()` và biểu diễn thành phần lồng

Khi ghi log hoặc hiển thị một đối tượng có trường mảng, nhiều người thử `System.out.println(array)` hoặc nối mảng với chuỗi và nhận kết quả dạng `[I@4e25154f` thay vì `[1, 2, 3]`. Đó là vì mảng trong Java là **đối tượng** và bản thân lớp mảng không ghi đè `Object.toString()` để trình bày từng phần tử. API `java.util.Arrays` giải quyết hai nhu cầu khác nhau: **`Arrays.toString(...)`** biểu diễn một cấp mảng, còn **`Arrays.deepToString(Object[])`** đi sâu qua các mảng con để biểu diễn dữ liệu lồng nhau.

Mục tiêu là chọn đúng API cho mảng một chiều, mảng nhiều chiều và mảng chứa các thành phần khác kiểu, đồng thời hiểu giới hạn của chuỗi hiển thị khi viết `toString()` cho lớp tự tạo.

## 1. Vì sao in trực tiếp mảng lại thấy `[I@...`?

Đối với một object không có `toString()` riêng, dạng biểu diễn mặc định của `Object.toString()` bao gồm tên lớp, ký tự `@` và giá trị `hashCode()` dưới dạng hex. Mảng Java cũng có `toString()` mặc định kiểu này, nên:

```java
// Đoạn minh họa bên trong một method, không phải chương trình độc lập:
int[] values = {1, 2, 3};
System.out.println(values);            // Có thể thấy dạng [I@<hex>
System.out.println("" + values);       // Cũng là biểu diễn object mặc định
System.out.println(Arrays.toString(values)); // [1, 2, 3]
```

Một số tên lớp nội bộ thường gặp:

| Kiểu mảng | Tên lớp mảng dạng JVM | Phần đầu thường thấy khi in trực tiếp |
| --- | --- | --- |
| `int[]` | `[I` | `[I@...` |
| `byte[]` | `[B` | `[B@...` |
| `String[]` | `[Ljava.lang.String;` | `[Ljava.lang.String;@...` |
| `int[][]` | `[[I` | `[[I@...` |

Phần hex sau dấu `@` **không phải địa chỉ bộ nhớ** và không có bảo đảm duy nhất giữa mọi object. Nó đến từ mã băm mặc định để hiển thị và có thể thay đổi giữa những lần chạy. Không nên lưu chuỗi dạng này làm ID hay dùng để kiểm tra mảng có cùng nội dung không.

Đối với mảng một chiều chứa các giá trị có ý nghĩa, gọi `Arrays.toString(array)` tạo biểu diễn dễ đọc hơn rất nhiều. Cần `import java.util.Arrays;`.

## 2. `Arrays.toString()`: biểu diễn một cấp mảng

Lớp `Arrays` có các overload `toString` dành cho từng kiểu mảng nguyên thủy (`int[]`, `long[]`, `boolean[]`, `char[]`, `float[]`, `double[]`, `byte[]`, `short[]`) và mảng tham chiếu `Object[]`.

Ví dụ:

```java
// Đoạn minh họa trong method:
Arrays.toString(new int[]{1, 2, 3});          // "[1, 2, 3]"
Arrays.toString(new boolean[]{true, false});  // "[true, false]"
Arrays.toString(new String[]{"A", "B"});      // "[A, B]"
Arrays.toString(new int[0]);                 // "[]"
Arrays.toString((int[]) null);               // "null"
```

Cú pháp hiển thị thường dùng dấu ngoặc vuông, ngăn phần tử bằng dấu phẩy và khoảng trắng. Nếu mảng tham chiếu có phần tử `null`, nó hiển thị thành chữ `null`. Với các phần tử là object thông thường, `Arrays.toString(Object[])` lấy chuỗi biểu diễn của **từng phần tử** (theo `String.valueOf`/`toString` phù hợp), nên một lớp tự định nghĩa `toString()` sẽ ảnh hưởng trực tiếp tới kết quả.

**Nhưng `Arrays.toString` không tự đi sâu qua các mảng con.** Ví dụ `int[][] matrix = {{1, 2}, {3, 4}}` thực chất là mảng ngoài chứa hai phần tử kiểu `int[]`. Khi truyền `matrix` vào `Arrays.toString`, overload `Object[]` được chọn, rồi mỗi hàng `int[]` được trình bày bằng `toString()` mặc định:

```java
// Đoạn minh họa, phần hash hex thay đổi theo lần chạy:
int[][] matrix = {{1, 2}, {3, 4}};
System.out.println(Arrays.toString(matrix));
// Ví dụ dạng hiển thị: [[I@<hash-1>, [I@<hash-2>]
```

Đây là lý do ta có thể nhìn thấy nhiều `[I@...` bên trong cặp ngoặc vuông. `Arrays.toString(matrix)` chỉ xử lý **mảng ngoài**, không diễn giải nội dung của các mảng hàng.

## 3. `Arrays.deepToString(Object[])`: đi sâu qua mảng lồng nhau

Với mảng mà **lớp ngoài cùng là mảng tham chiếu** (`Object[]`, `String[][]`, `int[][]`, v.v.), có thể dùng `Arrays.deepToString`. Phương thức này kiểm tra mỗi phần tử: nếu phần tử là mảng, nó hiển thị nội dung mảng con bằng quy tắc thích hợp; nếu là đối tượng thông thường, nó dùng biểu diễn `toString` của đối tượng đó.

```java
// Các biểu thức minh họa đặt trong method:
int[][] matrix = {{1, 2}, {3, 4}};
String[][] groups = {{"A", "B"}, {"C"}};

Arrays.deepToString(matrix); // "[[1, 2], [3, 4]]"
Arrays.deepToString(groups); // "[[A, B], [C]]"

Object[] mixed = {"row", new int[]{7, 8},
        new Object[]{new boolean[]{true, false}, "end"}};
Arrays.deepToString(mixed);
// "[row, [7, 8], [[true, false], end]]"
```

`int[][]` hợp lệ vì mảng ngoài có các phần tử kiểu `int[]` (tham chiếu), nên bản thân `int[][]` có thể truyền cho tham số `Object[]`. Ngược lại, `int[]` đơn lẻ **không phải `Object[]`**, nên `Arrays.deepToString(new int[]{1, 2})` không biên dịch. Với mảng nguyên thủy một chiều, dùng `Arrays.toString`.

Bảng chọn nhanh:

| Dữ liệu đang có | Cách hiển thị nội dung | Điều cần tránh |
| --- | --- | --- |
| `int[]`, `byte[]`, `double[]` | `Arrays.toString(array)` | `array.toString()` hoặc `Arrays.deepToString(array)` |
| `String[]` | `Arrays.toString(array)` | In trực tiếp mảng nếu muốn thấy các chuỗi |
| `int[][]`, `double[][]` | `Arrays.deepToString(array)` | `Arrays.toString(array)` nếu muốn đọc các phần tử mảng hàng |
| `String[][]`, `Object[][]` | `Arrays.deepToString(array)` | Cho rằng hiển thị một cấp đã đầy đủ |
| `Object[]` chứa lẫn chuỗi, object và mảng | `Arrays.deepToString(array)` khi cần đi sâu qua các phần tử là mảng | Cho rằng nó tự hiểu mọi kiểu container bất kỳ |

Với dữ liệu `null`, cả `Arrays.toString((Object[]) null)` và `Arrays.deepToString((Object[]) null)` đều trả chuỗi `"null"`. Nếu phần tử của mảng là `null`, phần tử đó cũng được hiển thị `null`. Đây là chuỗi biểu diễn, **không phải một tham chiếu Java `null` được trả về**.

## 4. Ví dụ Java 17+ đầy đủ và đầu ra xác định

Tạo tệp **`ArrayRenderingDemo.java`**:

```java
import java.util.Arrays;

public class ArrayRenderingDemo {
    record Part(String name, int quantity) {}

    public static void main(String[] args) {
        int[] scores = {8, 9, 10};
        String[] tags = {"java", null, "core"};
        int[][] matrix = {{1, 2}, {3, 4}};
        Object[] nested = {
                "header",
                new int[]{7, 8},
                new Object[]{"deep", new boolean[]{true, false}},
                null
        };

        System.out.println("scores: " + Arrays.toString(scores));
        System.out.println("tags: " + Arrays.toString(tags));
        System.out.println("empty: " + Arrays.toString(new int[0]));
        System.out.println("null array: "
                + Arrays.toString((int[]) null));

        System.out.println("matrix deep: "
                + Arrays.deepToString(matrix));
        System.out.println("nested deep: "
                + Arrays.deepToString(nested));
        System.out.println("matrix shallow contains row identity: "
                + Arrays.toString(matrix).contains("[I@"));
        System.out.println("nested shallow contains primitive identity: "
                + Arrays.toString(nested).contains("[I@"));

        Object[] parts = {
                new Part("bolt", 2),
                new int[]{5, 6}
        };
        System.out.println("record and array: "
                + Arrays.deepToString(parts));

        Object[] selfReference = new Object[1];
        selfReference[0] = selfReference;
        System.out.println("cycle has marker: "
                + Arrays.deepToString(selfReference).contains("..."));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac ArrayRenderingDemo.java
java ArrayRenderingDemo
```

Kết quả:

```text
scores: [8, 9, 10]
tags: [java, null, core]
empty: []
null array: null
matrix deep: [[1, 2], [3, 4]]
nested deep: [header, [7, 8], [deep, [true, false]], null]
matrix shallow contains row identity: true
nested shallow contains primitive identity: true
record and array: [Part[name=bolt, quantity=2], [5, 6]]
cycle has marker: true
```

Ý nghĩa:

1. `scores` và `tags` là mảng một chiều nên `Arrays.toString` đã đủ; phần tử `null` được hiển thị an toàn.
2. `matrix` là mảng hai chiều. `deepToString` hiển thị tất cả phần tử của từng hàng; `toString` một cấp vẫn để lộ dạng định danh mặc định của các hàng `int[]`. Chương trình chỉ in kết quả kiểm tra `contains("[I@")` để **không phụ thuộc những chữ số hash không ổn định**.
3. `nested` chứa nhiều loại phần tử, kể cả mảng nguyên thủy nằm trong một `Object[]` khác. `deepToString` diễn giải đúng cấu trúc các mảng lồng nhau.
4. `Part` là record, nên `toString` được Java sinh theo tên record và các component. Khi một phần tử không phải mảng, `deepToString` sử dụng cách phần tử đó tự biểu diễn; nó không tự phản chiếu từng trường của mọi lớp.
5. `selfReference` là mảng chứa tham chiếu về chính nó. `deepToString` nhận diện chu trình mảng và dùng dấu rút gọn (có `...`) thay vì đệ quy không ngừng. Đây là cơ chế dành cho cấu trúc mảng tham chiếu lồng nhau.

Đừng dùng chính chuỗi in ra làm bằng chứng về tính bằng nhau của hai đối tượng: hai đối tượng khác nhau có thể được `toString` biểu diễn giống nhau, và kết quả `toString` cũng có thể thay đổi khi trạng thái thay đổi.

## 5. Giới hạn khi thành phần lồng không phải mảng

`Arrays.deepToString` duyệt sâu **các cấp mảng**, chứ không phải mọi loại cấu trúc dữ liệu trong Java. Nếu một phần tử của `Object[]` là `List<int[]>`, `Map<String,int[]>` hoặc một POJO chứa mảng, `deepToString` sẽ gọi `toString` của `List`, `Map` hoặc POJO đó; nó **không tự duyệt xuyên các lớp này để đến mảng con**.

Ví dụ ý tưởng (không phải chương trình độc lập):

```java
Object[] data = {
        java.util.List.of(new int[]{1, 2})
};
String rendered = Arrays.deepToString(data);
// List.toString() được dùng ở cấp phần tử List;
// không thể mặc định kỳ vọng nội dung mảng bên trong List là "[1, 2]".
```

Vì vậy, khi xây dựng biểu diễn `toString` cho đối tượng nhiều cấp, hãy quyết định rõ **lớp nào sở hữu trách nhiệm trình bày**. Lớp chứa mảng có thể tự dùng `Arrays.toString`/`deepToString` trong `toString` của mình. Cấu trúc `Map` hay `List` có thể cần ánh xạ thành phần thành chuỗi rõ ràng trước khi hiển thị.

Với đối tượng nghiệp vụ:

```java
// Chỉ minh họa phần thân của phương thức toString trong một lớp có các field:
@Override
public String toString() {
    return "Report[scores=" + Arrays.toString(scores)
            + ", matrix=" + Arrays.deepToString(matrix) + "]";
}
```

Phần minh họa giả định trường `scores` có kiểu `int[]` và `matrix` có kiểu `int[][]`, không phải chương trình độc lập. Cách này tạo chuỗi dễ đọc hơn so với nối trực tiếp `scores` hoặc `matrix`.

Một lưu ý khác: một object thường có thể có `toString` tự triển khai, bao gồm hiệu ứng phụ, chi phí truy xuất lớn hoặc lỗi nếu trạng thái không hợp lệ. `deepToString` **không kiểm soát hành vi của các `toString` thành phần**. Log biểu diễn toàn bộ cấu trúc chỉ nên được dùng khi dữ liệu thực sự an toàn và chi phí có thể chấp nhận.

## 6. `deepToString` và mảng tự tham chiếu

Mảng tham chiếu `Object[]` có thể chứa chính nó hoặc tạo một chuỗi tham chiếu vòng:

```java
// Đoạn minh họa:
Object[] a = new Object[1];
a[0] = a;
String text = Arrays.deepToString(a);
```

Nếu một thuật toán đi sâu qua mảng mà không theo dõi mảng đã gặp trên nhánh duyệt, nó sẽ gọi lại chính mình mãi và gây `StackOverflowError`. `Arrays.deepToString` có cơ chế phát hiện chu trình trong các mảng đối tượng, dùng ký hiệu `[[...]]` cho mảng bị gặp lại trong chu trình, nhờ vậy tránh đệ quy vô hạn **đối với vòng được tạo qua các mảng**.

Tuy nhiên:

- Cơ chế này không có nghĩa `Arrays.deepToString` là một công cụ tuần tự hóa *object graph* tổng quát, bao gồm mọi loại chu trình qua POJO, collection và map.
- Chuỗi hiển thị có thể lặp lại nội dung của một mảng con được tham chiếu từ nhiều nơi nhưng **không tạo vòng đệ quy hiện tại**. Do đó, không thể đọc chuỗi như một sơ đồ bảo toàn danh tính tham chiếu.
- Với dữ liệu lồng sâu hoặc kích thước lớn, dựng cả chuỗi có thể tốn bộ nhớ và CPU; cần cân nhắc giới hạn độ sâu hoặc kích thước của log ở tầng ứng dụng.

Cơ chế xử lý vòng chủ yếu nhằm giúp biểu diễn mảng để chẩn đoán; nó không thay thế thiết kế dữ liệu và không phải định dạng trao đổi dữ liệu ổn định.

## 7. Chuỗi trình bày khác với so sánh nội dung và mã băm

`Arrays.toString`/`deepToString` chỉ tạo **chuỗi văn bản**. Chúng không quyết định đối tượng bằng nhau hay không và không cấp ID duy nhất:

| Mục đích | Mảng một chiều | Mảng lồng nhau |
| --- | --- | --- |
| **Biểu diễn để đọc** | `Arrays.toString(a)` | `Arrays.deepToString(a)` |
| **So sánh nội dung** | `Arrays.equals(a,b)` | `Arrays.deepEquals(a,b)` |
| **Băm theo nội dung** | `Arrays.hashCode(a)` | `Arrays.deepHashCode(a)` |

Ba dòng này thường đi cùng nhau trong một value object có trường mảng, nhưng có chức năng riêng biệt. Nếu lớp tự viết cài đặt `equals` theo nội dung mảng và `hashCode` tương ứng, `toString` có thể hiển thị bằng API cùng mức sâu để giúp debug. Dù vậy, chuỗi hiển thị giống nhau **không tạo ra bảo đảm hợp đồng `equals`**: `toString` của một lớp có thể cố ý bỏ thông tin hoặc rút gọn.

Ví dụ hai mảng sau có thể hiển thị giống nhau trong một ngữ cảnh nhưng không đại diện cùng cấu trúc có kiểu chính xác:

```java
// Ví dụ minh họa:
String[] withComma = {"a, b", "c"};
String[] separate = {"a", "b", "c"};
// Arrays.toString(withComma) -> "[a, b, c]"
// Arrays.toString(separate)  -> "[a, b, c]"
// Arrays.equals(withComma, separate) -> false
```

Đặc biệt, `Arrays.toString` không tự escape hoặc quote đầy đủ nội dung chuỗi, nên **không phải một serialization format để parse ngược đáng tin cậy**. Dấu phân cách xuất hiện bên trong giá trị có thể khiến bản hiển thị mơ hồ.

## 8. Khi nào cần JSON hoặc cấu trúc có schema?

Nếu cần gửi dữ liệu qua API, ghi tệp cấu hình, lưu cơ sở dữ liệu hay cần một định dạng mà máy khác parse ngược chính xác, hãy dùng một thư viện serialization được duy trì (ví dụ Jackson JSON trong ứng dụng phù hợp) cùng cấu trúc/schema rõ ràng. Không dùng `Arrays.deepToString` thay thế JSON hay giao thức truyền thông:

- Không có hệ thống escaping/quoting như JSON để bảo toàn mọi chuỗi.
- Không mang đủ thông tin kiểu Java, nullability và danh tính đối tượng cho mọi trường hợp.
- Không có hợp đồng định dạng ổn định cho các lớp tự viết `toString`.
- Không thiết kế để đại diện chu trình, chia sẻ tham chiếu hoặc các collection lồng nhau dưới dạng có thể giải mã ngược.
- Có thể lộ các trường nhạy cảm nếu cứ in cả object để ghi log.

Nếu mục tiêu chỉ là nhìn nhanh một mảng ở console hoặc trong log debug an toàn, `Arrays.toString` và `deepToString` là lựa chọn hợp lý vì gọn và sẵn có trong JDK. Nếu mục tiêu là **dữ liệu có cấu trúc, tái sử dụng được**, hãy thiết kế serialization riêng.

## 9. Những lỗi phổ biến và cách tự kiểm tra

1. **`System.out.println(intArray)` rồi tưởng thấy nội dung:** thực tế thường thấy `[I@...`; dùng `Arrays.toString(intArray)`.
2. **`Arrays.toString(intMatrix)` rồi tưởng in hết hàng:** mảng con `int[]` vẫn in mặc định; dùng `Arrays.deepToString(intMatrix)`.
3. **Dùng `Arrays.deepToString(intArray)` cho `int[]` một chiều:** sai kiểu, không biên dịch vì phương thức nhận `Object[]`; dùng `Arrays.toString`.
4. **Nghĩ `deepToString` đi sâu qua mọi `List`, `Map`, POJO:** nó chỉ đi sâu qua mảng. Cần xử lý biểu diễn các container khác bằng API/thiết kế tương ứng.
5. **Nghĩ biểu diễn `[I@<hex>` là địa chỉ RAM hoặc ID bất biến:** đó là biểu diễn `Object.toString` dựa trên hash, không bảo đảm như vậy.
6. **Dùng chuỗi `Arrays.toString` như dữ liệu máy có thể parse lại:** ký tự phân cách và kiểu phần tử có thể mơ hồ; dùng định dạng có schema như JSON nếu cần trao đổi hoặc lưu trữ.
7. **Ghi log mảng mật khẩu, token hoặc dữ liệu cá nhân:** nội dung của mảng có thể xuất hiện trọn vẹn; chọn lọc/che thông tin nhạy cảm trước khi ghi.
8. **Để `toString` đệ quy thủ công không kiểm soát trên object graph có vòng:** `Arrays.deepToString` có xử lý vòng qua mảng, nhưng không bảo vệ mọi chu trình do code `toString` tùy biến tạo ra.
9. **Gán ý nghĩa “bằng nhau” từ `toString`:** hai đối tượng in giống nhau vẫn có thể khác theo `equals`; dùng phép so sánh thích hợp.
10. **Dựng chuỗi cho một mảng rất lớn mỗi lần xử lý:** trong code hiệu năng cao, chỉ tạo biểu diễn khi cần chẩn đoán; cân nhắc giới hạn phần tử và tránh log ở mức quá chi tiết.

### Checklist

- Đối tượng cần in là **mảng nguyên thủy một chiều**, mảng tham chiếu một chiều hay mảng nhiều chiều?
- Đã chọn đúng overload `Arrays.toString` hoặc `Arrays.deepToString` chưa?
- Trong mảng có chứa các mảng con, `List`, `Map` hoặc POJO cần biểu diễn riêng không?
- Có khả năng vòng tham chiếu, dữ liệu quá sâu/quá lớn hoặc `toString` của phần tử có tác dụng phụ không?
- Chuỗi dùng để **debug cho người đọc** hay để **truyền/lưu dữ liệu có thể parse**?
- Nội dung có chứa bí mật hoặc dữ liệu nhạy cảm cần che trước khi ghi log không?

**Ghi nhớ:** dùng **`Arrays.toString` cho mảng một chiều**, dùng **`Arrays.deepToString` khi có các cấp mảng lồng nhau với mảng ngoài là mảng tham chiếu**. Hai API này giúp in dữ liệu dễ hiểu, nhưng không biến chuỗi hiển thị thành danh tính đối tượng, phép so sánh `equals` hay định dạng serialization đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)
