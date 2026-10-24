<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Trừu tượng hóa là gì và vấn đề cần giải quyết](#overview-001)
- [Phân biệt trừu tượng hóa với đóng gói và kế thừa](#overview-002)
- [Hợp đồng, cách triển khai và mã sử dụng hợp đồng](#overview-003)
- [Vai trò của lớp trừu tượng và interface trong Java](#overview-004)
- [Quan hệ giữa kiểu, đối tượng, trạng thái và hành vi](#overview-005)
- [Một ví dụ xuyên suốt: nhiều cách xử lý cùng một yêu cầu](#overview-006)
- [Kiến thức tiên quyết và trình tự học đề xuất](#overview-007)
- [Phạm vi Java Core và cách đối chiếu phiên bản JDK](#overview-008)

## <a id="overview-001">Trừu tượng hóa là gì và vấn đề cần giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

# Trừu tượng hóa trong Java: là gì và giải quyết vấn đề nào?

## 1. Khái niệm cốt lõi

**Trừu tượng hóa (abstraction)** là cách mô tả một đối tượng hoặc một khả năng bằng những đặc điểm, hành vi **cần thiết đối với người sử dụng**, trong khi để các chi tiết thực hiện nằm ở lớp hiện thực phía sau. Khi gọi một hành vi, mã sử dụng cần biết **có thể yêu cầu điều gì** và **kết quả/hợp đồng là gì**; nó không nhất thiết phải biết thuật toán, dữ liệu nội bộ hoặc hệ thống bên ngoài được dùng để hoàn thành yêu cầu.

Ví dụ đời thường: người dùng nhấn nút “Thanh toán”, quan tâm số tiền, trạng thái thành công/thất bại và chứng từ; họ không cần biết giao dịch được chuyển qua ngân hàng hay ví điện tử, cách tạo yêu cầu mạng hoặc cách đối soát nội bộ.

Trong Java, có thể thể hiện ranh giới trừu tượng bằng **interface**, **abstract class**, và cả những lớp thông thường có API công khai rõ ràng. Trừu tượng hóa là **nguyên tắc thiết kế**; `interface` và `abstract` là **công cụ ngôn ngữ** hỗ trợ nguyên tắc ấy. Không phải cứ tạo interface là đã tạo ra một trừu tượng tốt.

Hãy nhớ ba câu hỏi:

- **Cái gì?** Người gọi có thể yêu cầu hành vi nào?
- **Cam kết nào?** Đầu vào hợp lệ, đầu ra, lỗi và tác dụng phụ được hiểu ra sao?
- **Như thế nào?** Ai thực hiện và bằng cơ chế gì? Đây là phần có thể ẩn sau ranh giới trừu tượng.

## 2. Vì sao cần trừu tượng hóa?

Giả sử một dịch vụ bán hàng thanh toán trực tiếp bằng một lớp chuyển khoản ngân hàng cụ thể:

```java
// Minh họa sự phụ thuộc trực tiếp; không phải chương trình độc lập.
final class CheckoutService {
    private final BankTransferGateway gateway = new BankTransferGateway();

    Receipt checkout(long totalVnd) {
        return gateway.pay(totalVnd);
    }
}
```

Đoạn này có thể đủ cho một bài toán nhỏ, nhưng khi nhu cầu mở rộng sẽ xuất hiện các vấn đề:

1. **Phụ thuộc chặt vào cách thực hiện:** `CheckoutService` tự quyết định dùng `BankTransferGateway`. Nếu đổi sang ví điện tử, có thể phải sửa lớp xử lý nghiệp vụ.
2. **Khó thay thế để kiểm thử:** kiểm tra quy tắc checkout có thể vô tình gọi tích hợp thanh toán thật hoặc phải dựng môi trường ngân hàng giả lập.
3. **Chi tiết kỹ thuật lan vào nghiệp vụ:** nếu dịch vụ biết tên endpoint, cách ký yêu cầu hay cấu trúc phản hồi của từng nhà cung cấp, logic nghiệp vụ ngày càng khó đọc và khó bảo trì.
4. **Thay đổi dây chuyền:** nhiều nơi cùng biết lớp cụ thể sẽ cùng bị tác động khi thư viện hoặc nhà cung cấp thay đổi.

Ý tưởng: cho mã nghiệp vụ phụ thuộc vào một **khả năng ổn định** mang tên `PaymentGateway`; từng nhà cung cấp triển khai khả năng đó. Khi ấy “thanh toán” là khái niệm nghiệp vụ được công bố, còn “gửi yêu cầu đến đâu và làm thế nào” là chi tiết phía sau.

Mối quan hệ được mong muốn:

```text
CheckoutService --> PaymentGateway (hợp đồng)
                         ^
                         |
             +-----------+-----------+
             |                       |
   BankTransferGateway          WalletGateway
   (cách làm thứ nhất)          (cách làm thứ hai)
```

Sơ đồ thể hiện hướng phụ thuộc về **kiểu dữ liệu** của mã sử dụng. Việc tạo và chọn một hiện thực vẫn cần xảy ra ở đâu đó, chẳng hạn tại `main` hoặc trong phần cấu hình ứng dụng.

## 3. Ví dụ hoàn chỉnh, chạy được với Java 17+

Tạo tệp **`AbstractionDemo.java`** với nội dung sau. Để tập trung vào abstraction, các gateway chỉ trả về chứng từ giả lập có giá trị xác định; ví dụ **không kết nối hay thực hiện giao dịch thanh toán thật**.

```java
import java.util.Objects;

interface PaymentGateway {
    Receipt pay(long amountVnd);
}

record Receipt(String provider, long amountVnd, String reference) {
}

final class BankTransferGateway implements PaymentGateway {
    @Override
    public Receipt pay(long amountVnd) {
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        return new Receipt("BankTransfer", amountVnd, "BANK-001");
    }
}

final class WalletGateway implements PaymentGateway {
    @Override
    public Receipt pay(long amountVnd) {
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        return new Receipt("Wallet", amountVnd, "WALLET-001");
    }
}

final class CheckoutService {
    private final PaymentGateway gateway;

    CheckoutService(PaymentGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    Receipt checkout(long totalVnd) {
        if (totalVnd <= 0) {
            throw new IllegalArgumentException("totalVnd must be positive");
        }
        return gateway.pay(totalVnd);
    }
}

public class AbstractionDemo {
    public static void main(String[] args) {
        CheckoutService bankCheckout =
                new CheckoutService(new BankTransferGateway());
        CheckoutService walletCheckout =
                new CheckoutService(new WalletGateway());

        System.out.println(bankCheckout.checkout(120_000));
        System.out.println(walletCheckout.checkout(120_000));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac AbstractionDemo.java
java AbstractionDemo
```

Kết quả chính xác:

```text
Receipt[provider=BankTransfer, amountVnd=120000, reference=BANK-001]
Receipt[provider=Wallet, amountVnd=120000, reference=WALLET-001]
```

**Phân tích từng vai trò:**

- `PaymentGateway` nêu ra **khả năng thanh toán** qua phương thức `pay(long)`. Người sử dụng làm việc qua tên kiểu và phương thức này.
- `BankTransferGateway` và `WalletGateway` là **hai hiện thực thay thế được** của cùng hợp đồng. Cả hai phải cung cấp `pay(long)`.
- `Receipt` là dữ liệu kết quả chung, giúp mã gọi không phải hiểu hai cấu trúc phản hồi khác nhau.
- `CheckoutService` giữ một trường thuộc kiểu `PaymentGateway`, nhận hiện thực qua constructor và chỉ gọi phương thức hợp đồng. Đây là **dependency injection bằng constructor** ở mức Java thuần.
- `main` chọn hiện thực cụ thể khi ghép các đối tượng. Việc lựa chọn được đặt ở nơi tạo ứng dụng, tách khỏi logic `checkout`.
- Khi gọi `gateway.pay(totalVnd)`, Java chọn phương thức của đối tượng thực tế đang được tham chiếu (đa hình lúc chạy). **Trừu tượng hóa** xác định giao diện chung; **đa hình** giúp nhiều hiện thực đáp ứng lời gọi qua giao diện đó.

Điểm quan trọng: thay `new BankTransferGateway()` bằng `new WalletGateway()` ở nơi khởi tạo là có thể đổi cách thanh toán mà không sửa mã nguồn của `CheckoutService`. Điều này hữu ích khi nghiệp vụ thật sự cần nhiều hiện thực hoặc cần thay thế thành phần trong kiểm thử.

## 4. “Ẩn chi tiết” nghĩa là gì, và không có nghĩa là gì?

Trừu tượng hóa **không nhất thiết giấu mọi thứ**. Một hợp đồng tốt phải công bố đủ thông tin để người gọi dùng đúng: đơn vị tiền tệ, điều kiện số tiền dương, kiểu dữ liệu trả về, điều kiện lỗi, tính đồng bộ/bất đồng bộ, tác dụng phụ và các bảo đảm có ý nghĩa. Trong ví dụ, `amountVnd` cho thấy giá trị được tính bằng VND, `long` thể hiện số nguyên, `Receipt` thống nhất đầu ra và `IllegalArgumentException` được dùng cho số tiền không hợp lệ.

Điều được che khỏi `CheckoutService` là các quyết định **không cần thiết đối với nghiệp vụ checkout**: giao thức gọi API, cách xác thực với nhà cung cấp, phương thức ký số, retry, hoặc chuyển đổi JSON thành đối tượng Java. Khi triển khai thật, những chi tiết này phải được hiện thực và kiểm thử riêng.

Một interface quá mơ hồ như `execute(Object value)` không phải là trừu tượng tốt chỉ vì nó tổng quát. Người dùng khó biết loại dữ liệu nào hợp lệ và kết quả ra sao. Ngược lại, tên `pay(long amountVnd)` cùng một hợp đồng rõ ràng truyền đạt đúng khả năng của thành phần.

## 5. Interface, abstract class và lớp thường

| Công cụ Java | Vai trò thường gặp trong trừu tượng hóa | Điều cần hiểu |
| --- | --- | --- |
| `interface` | Định nghĩa **khả năng/hợp đồng** chung cho những hiện thực có thể khác nhau. | Một lớp có thể `implements` nhiều interface; interface cũng có thể khai báo phương thức `default` và `static`. |
| `abstract class` | Định nghĩa một **loại cơ sở** có thể giữ trạng thái, constructor, hành vi dùng chung và phương thức abstract để lớp con hoàn thiện. | Không thể `new` trực tiếp abstract class; một lớp Java chỉ `extends` một lớp cha. |
| Lớp thường với API công khai | Đưa ra các thao tác dễ dùng và giấu chi tiết trong phương thức/private fields. | Không cần tạo interface riêng nếu chưa có lý do thay thế hoặc ranh giới phụ thuộc hữu ích. |

Ví dụ ngắn về `abstract class` (đoạn minh họa thành phần, không chứa `main`):

```java
abstract class Shape {
    private final String color;

    protected Shape(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }

    public abstract double area();
}

final class Circle extends Shape {
    private final double radius;

    Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}
```

Kiểu `Shape` công bố “mọi hình có màu và tính được diện tích”, đồng thời chia sẻ trạng thái `color`. Lớp `Circle` quyết định công thức cụ thể. Trong bài toán gateway, `interface` phù hợp vì ta chủ yếu cần một khả năng chung; không nhất thiết có trạng thái hay cấu trúc kế thừa chung giữa các nhà cung cấp.

**Cần phân biệt:** `abstract class` và `interface` không đồng nghĩa với toàn bộ khái niệm “abstraction”. Chúng giúp khai báo ranh giới; chất lượng của ranh giới vẫn phụ thuộc vào bài toán và hợp đồng được thiết kế.

## 6. Trừu tượng hóa khác gì đóng gói và đa hình?

Ba khái niệm liên quan nhưng trả lời các câu hỏi khác nhau:

- **Trừu tượng hóa (abstraction):** người sử dụng **cần biết và có thể yêu cầu điều gì?** Ví dụ `PaymentGateway.pay` là khả năng chung.
- **Đóng gói (encapsulation):** **ai được phép truy cập hoặc thay đổi trạng thái/chi tiết nội bộ?** Ví dụ dùng trường `private`, cung cấp phương thức công khai phù hợp, giữ bất biến của đối tượng.
- **Đa hình (polymorphism):** **cùng một lời gọi có thể được thực hiện bởi những kiểu đối tượng nào?** Ví dụ `PaymentGateway gateway` có thể trỏ đến gateway ngân hàng hoặc ví.

Một lớp với các trường `private` có thể đóng gói tốt nhưng API công khai vẫn kém trừu tượng nếu buộc mã gọi phải biết quá nhiều chi tiết kỹ thuật. Ngược lại, việc khai báo interface cũng không tự bảo đảm dữ liệu bên trong các hiện thực được đóng gói đúng.

## 7. Kiểm thử trở nên đơn giản hơn thế nào?

Một trong các lợi ích rõ nhất là dùng **test double**: cung cấp một hiện thực thay thế có kiểm soát để kiểm tra riêng logic `CheckoutService`. Vì `PaymentGateway` chỉ có một phương thức abstract, có thể viết một lambda như sau trong chương trình Java có các kiểu ở ví dụ trên:

```java
PaymentGateway fakeGateway =
        amount -> new Receipt("Fake", amount, "TEST-001");
CheckoutService service = new CheckoutService(fakeGateway);

Receipt receipt = service.checkout(500);
System.out.println(receipt.provider()); // Fake
```

Đây là **đoạn minh họa**, không phải tệp độc lập: đặt bên trong một phương thức khi các khai báo `PaymentGateway`, `Receipt` và `CheckoutService` đã tồn tại. Mục tiêu là kiểm tra luồng checkout mà không cần giao tiếp với hệ thống thanh toán bên ngoài. Lưu ý rằng fake này không thay thế được các kiểm thử tích hợp nhằm xác minh gateway thật hoạt động đúng.

Khi nhiều hiện thực cùng dựa vào một hợp đồng, nên kiểm tra những hành vi chung của chúng: xử lý số tiền không hợp lệ, đơn vị và ý nghĩa của số tiền, tính nhất quán của dữ liệu kết quả và quy tắc lỗi. Nếu hai gateway trả về giá trị trái ngược với hợp đồng, tính thay thế sẽ mất dù chúng đều biên dịch thành công.

## 8. Khi nào nên tạo trừu tượng? Khi nào chưa cần?

**Có cơ sở tốt để tạo trừu tượng** khi một khả năng có từ hai cách thực hiện hợp lý; phần nghiệp vụ cần tách khỏi thư viện, nhà cung cấp hoặc hạ tầng; cần thay thế phụ thuộc khi kiểm thử; hoặc có một hợp đồng ổn định được nhiều thành phần sử dụng. Một interface tại ranh giới “thanh toán”, “gửi thông báo” hay “đọc dữ liệu” thường dễ giải thích vì nó gắn với khả năng rõ ràng.

**Chưa chắc cần tạo interface** nếu chương trình rất nhỏ, chỉ có một hiện thực, chưa có nhu cầu tách phụ thuộc hoặc tên hợp đồng chỉ lặp nguyên tên lớp cụ thể mà không tạo ra ý nghĩa mới. Tạo hàng chục interface chỉ để mỗi lớp có một `I...Service` tương ứng có thể tăng số tệp, số bước tra cứu và chi phí bảo trì.

Tránh những lỗi thường gặp:

1. **Trừu tượng quá sớm:** đoán trước nhiều biến thể chưa tồn tại, thiết kế phương thức chung quá tổng quát rồi phải sửa liên tục.
2. **Lộ chi tiết nhà cung cấp:** interface trả về đối tượng SDK cụ thể, buộc mọi caller vẫn phụ thuộc vào SDK đó.
3. **Hợp đồng mơ hồ:** không nói rõ đơn vị dữ liệu, giá trị hợp lệ, lỗi, `null`, tác dụng phụ hoặc điều kiện tiền/hậu.
4. **Ép các hiện thực không tương thích vào cùng interface:** mỗi lớp hiểu `pay` theo cách khác nhau khiến caller phải dùng `instanceof` hoặc `if` để phân biệt.
5. **Hiểu nhầm thay thế được là giống hệt nhau:** các hiện thực có thể khác cách làm và hiệu năng, nhưng vẫn phải tôn trọng những cam kết chung có thể quan sát từ phía caller.
6. **Nhầm abstraction với bảo mật:** ẩn chi tiết API hoặc dùng `private` không thay thế kiểm soát quyền, xác thực, mã hóa và quản lý bí mật.

Một phép thử thiết kế hữu ích: **nếu thay hiện thực A bằng B, phần gọi có tiếp tục đúng mà không cần biết B là ai không?** Nếu câu trả lời là “có” trong phạm vi các cam kết đã công bố, ranh giới trừu tượng đang có ích.

## 9. Quy trình áp dụng vào bài toán thực tế

1. **Xác định nghiệp vụ cần làm gì.** Ví dụ checkout cần yêu cầu một khoản thanh toán và nhận kết quả thống nhất.
2. **Viết hợp đồng nhỏ, rõ và ổn định.** Xác định tên phương thức, tham số, đơn vị, giá trị trả về, lỗi và các bảo đảm cần thiết; không bê nguyên giao diện của SDK.
3. **Để mã sử dụng phụ thuộc vào hợp đồng.** Nhận `PaymentGateway` qua constructor, thay vì tự tạo gateway bên trong nghiệp vụ.
4. **Cài đặt hiện thực cụ thể.** Mỗi gateway ánh xạ hợp đồng vào công nghệ hoặc nhà cung cấp tương ứng và tôn trọng cam kết chung.
5. **Ghép đối tượng tại điểm cấu hình.** Ở Java thuần có thể dùng `new` tại `main`; một framework DI có thể tự quản lý việc tạo/lắp ghép này khi cần.
6. **Thử khả năng thay thế.** Chạy cùng logic với hai hiện thực, test double và các trường hợp đầu vào/lỗi đã định nghĩa.

**Tự kiểm tra hiểu bài:**

- Nếu `CheckoutService` vẫn phải kiểm tra `if (gateway instanceof WalletGateway)` để quyết định luồng xử lý, ranh giới trừu tượng có thể đang thiếu hoặc sai điều gì?
- Nếu một hệ thống chỉ cần lưu một chuỗi cố định, việc tạo `StringProvider`, `DefaultStringProvider` và factory riêng mang lại giá trị thực tế nào?
- Nếu API `PaymentGateway` trả về `BankSdkResponse`, phần nghiệp vụ đã thực sự tách khỏi nhà cung cấp chưa?

**Kết luận:** trừu tượng hóa cho phép lập trình theo **khả năng và hợp đồng cần thiết**, để mỗi bên có thể thay đổi chi tiết thực hiện trong phạm vi cam kết của mình. Mục tiêu không phải thêm nhiều lớp/interface, mà là tạo ra một ranh giới khiến phần mã sử dụng dễ hiểu, dễ kiểm thử và ít phải thay đổi khi cách thực hiện thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Phân biệt trừu tượng hóa với đóng gói và kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt trừu tượng hóa với đóng gói và kế thừa trong Java

## Mục tiêu

Sau bài này, bạn có thể giải thích chính xác ba nguyên lý thường xuất hiện cùng nhau trong lập trình hướng đối tượng (OOP):

- **Trừu tượng hóa (abstraction):** xác định *đối tượng cung cấp khả năng gì* và che bớt chi tiết hiện thực mà người dùng không cần biết.
- **Đóng gói (encapsulation):** quản lý *ai được truy cập hoặc thay đổi trạng thái nội bộ, bằng cách nào* để bảo vệ các quy tắc của đối tượng.
- **Kế thừa (inheritance):** xây dựng một kiểu dựa trên kiểu khác, nhận các thành viên được phép kế thừa và hình thành quan hệ kiểu con–kiểu cha.

Chúng có thể phối hợp trong cùng một thiết kế, nhưng giải quyết ba vấn đề khác nhau. Java có `interface`, `abstract class`, các mức truy cập và `extends`/`implements` để hiện thực chúng; không nên đồng nhất một từ khóa với toàn bộ một nguyên lý.

## 1. So sánh nhanh

| Khía cạnh | Trừu tượng hóa | Đóng gói | Kế thừa |
|---|---|---|---|
| Câu hỏi chính | «Dùng đối tượng này để làm gì?» | «Trạng thái và quy tắc bên trong được bảo vệ ra sao?» | «Kiểu này có quan hệ gì với kiểu khác?» |
| Tập trung vào | Hợp đồng/khả năng quan sát được, bỏ qua chi tiết không thiết yếu | Ranh giới truy cập, kiểm soát thay đổi và bất biến dữ liệu | Quan hệ *is-a*, tái sử dụng hành vi, khả năng thay thế kiểu |
| Công cụ Java thường gặp | `interface`, `abstract class`, phương thức public có chủ đích | `private`, API kiểm soát trạng thái, bất biến, defensive copy | `extends`, `implements`, `super`, `@Override` |
| Ví dụ | `PaymentMethod.pay(amount)` mô tả thao tác thanh toán | `Wallet` giữ `balance` ở `private` và chỉ trừ tiền qua kiểm tra hợp lệ | `GiftWallet extends Wallet` |
| Không bảo đảm điều gì? | Có hợp đồng tốt chưa chắc trạng thái được bảo vệ tốt | Dữ liệu được bảo vệ chưa chắc API đủ đơn giản hoặc có kiểu cha | Có kiểu cha chưa chắc API trừu tượng tốt hoặc đóng gói an toàn |

**Cách nhớ:** trừu tượng hóa chọn điều *cần lộ ra*; đóng gói kiểm soát điều *được phép tác động*; kế thừa xác lập mối quan hệ *kiểu con với kiểu cha*. “Che giấu chi tiết” có mặt ở cả trừu tượng hóa và đóng gói, nhưng mục đích khác nhau.

## 2. Trừu tượng hóa (abstraction): làm việc qua hợp đồng

Giả sử một hệ thống thanh toán có ví quà tặng, thẻ ngân hàng và cổng thanh toán bên ngoài. Phần xử lý đơn hàng chỉ cần biết rằng phương thức thanh toán nhận số tiền và trả kết quả. Nó không cần biết cách gọi API ngân hàng, cách kiểm tra hạn mức hay cách lưu số dư.

```java
interface PaymentMethod {
    String pay(int amount);
}
```

`PaymentMethod` là một **hợp đồng hành vi**: bên gọi phụ thuộc vào `pay` thay vì một lớp hiện thực cụ thể. Các lớp khác nhau có thể hiện thực hợp đồng theo những cách riêng; phía gọi không cần sửa chỉ vì thuật toán thanh toán thay đổi.

Điểm cốt lõi của trừu tượng hóa **không phải cứ có `interface` là đủ**. Một lớp thông thường với API ít, rõ ràng cũng có thể cung cấp một trừu tượng hữu ích. Ngược lại, một `interface` phơi bày hàng chục chi tiết nội bộ vẫn có thể là thiết kế trừu tượng kém.

Trong Java, thường lựa chọn:

- **`interface`:** diễn tả vai trò/khả năng để nhiều lớp không chung một lớp cha trực tiếp cùng tuân thủ. Ngoài phương thức trừu tượng, Java hiện đại cho phép `default`, `static` và một số phương thức `private` trong interface; trường khai báo trong interface là hằng số `public static final`.
- **`abstract class`:** diễn tả lớp cơ sở chưa hoàn chỉnh, có thể vừa quy định phương thức trừu tượng vừa chia sẻ trạng thái, constructor và cài đặt chung. Không thể khởi tạo trực tiếp lớp `abstract`.

Khi thiết kế trừu tượng tốt, hãy nêu rõ ý nghĩa tham số, kết quả, trường hợp lỗi và quy tắc người gọi có thể trông đợi. Chỉ khai báo tên phương thức mà không xác định hành vi mong đợi thì hợp đồng vẫn mơ hồ.

## 3. Đóng gói (encapsulation): bảo vệ trạng thái và quy tắc

Một ví có số dư không âm. Nếu khai báo `public int balance`, bất kỳ mã nào cũng có thể viết `wallet.balance = -999;` và phá vỡ quy tắc đó. Đóng gói đặt trạng thái sau một ranh giới truy cập, rồi chỉ cho phép thay đổi qua thao tác có kiểm tra.

```java
private int balance;

public int getBalance() {
    return balance;
}

protected boolean deduct(int amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("Số tiền phải dương");
    }
    if (amount > balance) {
        return false;
    }
    balance -= amount;
    return true;
}
```

*Đây là đoạn minh họa các thành viên nằm trong một lớp, chưa phải chương trình Java độc lập.* Ví dụ chạy hoàn chỉnh ở phần 5 có các thành viên tương tự.

Điều quan trọng không đơn thuần là viết `private` và tự động tạo getter/setter cho mọi trường:

- Nếu có `setBalance(int value)` công khai và cho phép giá trị tùy ý, trạng thái vẫn có thể bị sửa sai dù trường là `private`.
- Không cần đưa mọi trường ra thành getter. Chỉ cung cấp các thao tác mà miền nghiệp vụ thực sự cần, ví dụ `deposit` hoặc `withdraw`.
- Với đối tượng mutable như `List` hoặc `Date`, trả trực tiếp tham chiếu nội bộ qua getter có thể làm rò rỉ quyền sửa; cân nhắc bản sao phòng vệ hoặc chế độ xem không sửa được, tùy hợp đồng.
- Trạng thái bất biến (immutable) thường giúp giảm bề mặt lỗi, nhưng cần bảo đảm cả các đối tượng được tham chiếu bên trong không bị bên ngoài sửa ngoài ý muốn.

Đóng gói bảo vệ **bất biến (invariant)**: điều luôn phải đúng đối với đối tượng tại những thời điểm quan sát hợp lệ, chẳng hạn `balance >= 0`. Nó cũng giúp đổi cách lưu trữ nội bộ mà không buộc mã sử dụng phải thay đổi khi API được giữ ổn định.

## 4. Kế thừa (inheritance): quan hệ kiểu con–kiểu cha

Trong Java, `class GiftWallet extends Wallet` nói rằng `GiftWallet` là một dạng `Wallet`. Kiểu con có thể sử dụng các thành viên được phép truy cập từ lớp cha và ghi đè các phương thức có thể override để chuyên biệt hóa hành vi.

- `extends` giữa các lớp tạo quan hệ kế thừa lớp; một lớp chỉ có **một lớp cha trực tiếp**.
- `implements` cho phép lớp thực hiện **nhiều interface**, hình thành quan hệ kiểu con theo hợp đồng interface. Một interface cũng có thể `extends` nhiều interface khác.
- `super(...)` gọi constructor lớp cha. Constructor không được kế thừa như phương thức; `private` của lớp cha không trở thành thành viên có thể truy cập trực tiếp từ lớp con.
- `@Override` giúp trình biên dịch kiểm tra việc ghi đè đúng chữ ký. Phương thức `final` không thể bị override; phương thức `static` có thể bị che tên (*hiding*), không tham gia override đa hình như phương thức instance.

Kế thừa phù hợp khi quan hệ **is-a** thực sự đúng và đối tượng kiểu con có thể được dùng ở nơi đòi hỏi kiểu cha mà vẫn đáp ứng hợp đồng đã công bố. Đây là ý của nguyên tắc thay thế Liskov (LSP). Ví dụ một lớp con làm `pay` tự nhiên từ chối mọi số tiền hợp lệ trái với cam kết của kiểu cha thì có thể vi phạm hợp đồng, dù cú pháp Java vẫn hợp lệ.

**Không nên chọn kế thừa chỉ để dùng lại vài dòng code.** Nếu quan hệ đúng là **has-a** (có hoặc sử dụng một thành phần), hãy cân nhắc composition: một `CheckoutService` *giữ tham chiếu đến* `PaymentMethod` để dùng chức năng thanh toán, chứ không `extends PaymentMethod`. Composition thường giảm phụ thuộc vào chi tiết lớp cha và giúp thay thế cộng tác viên dễ hơn.

## 5. Ví dụ Java 17 hoàn chỉnh: cả ba nguyên lý trong một chương trình

Lưu toàn bộ đoạn sau vào tệp **`ConceptsDemo.java`**. Đoạn mã độc lập, sử dụng số nguyên làm đơn vị tiền giả định để tập trung vào OOP; hệ thống tiền tệ thực tế cần mô hình chính xác hơn, chính sách giao dịch và xử lý đồng thời phù hợp.

```java
interface PaymentMethod {
    String pay(int amount);
}

abstract class Wallet implements PaymentMethod {
    private int balance;

    protected Wallet(int initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Số dư đầu phải không âm");
        }
        this.balance = initialBalance;
    }

    public final int getBalance() {
        return balance;
    }

    protected final boolean deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền phải dương");
        }
        if (amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }
}

final class GiftWallet extends Wallet {
    GiftWallet(int initialBalance) {
        super(initialBalance);
    }

    @Override
    public String pay(int amount) {
        return deduct(amount)
                ? "Đã thanh toán " + amount
                : "Không đủ số dư";
    }
}

public class ConceptsDemo {
    public static void main(String[] args) {
        GiftWallet wallet = new GiftWallet(100);
        PaymentMethod method = wallet;

        System.out.println(method.pay(40));
        System.out.println("Số dư còn lại: " + wallet.getBalance());
        System.out.println(method.pay(100));
        System.out.println("Số dư còn lại: " + wallet.getBalance());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -encoding UTF-8 ConceptsDemo.java
java ConceptsDemo
```

Kết quả chuẩn trên stdout:

```text
Đã thanh toán 40
Số dư còn lại: 60
Không đủ số dư
Số dư còn lại: 60
```

**Giải mã thiết kế:**

1. **Trừu tượng hóa:** `PaymentMethod` quy định khả năng `pay` mà người gọi sử dụng. Biến `PaymentMethod method` không cần biết thuật toán trừ tiền bên trong. Lời gọi `method.pay(40)` được điều phối đến hiện thực của `GiftWallet` khi chạy.
2. **Đóng gói:** `Wallet.balance` là `private`; bên ngoài không thể gán trực tiếp. Constructor từ chối số dư âm; `deduct` từ chối số tiền không dương và không làm số dư giảm dưới 0. `getBalance` chỉ cho phép đọc. `protected final deduct` cho phép lớp con sử dụng thao tác có kiểm tra mà không tự sửa trực tiếp trường riêng.
3. **Kế thừa:** `GiftWallet extends Wallet` nhận hành vi quản lý số dư được phép dùng từ lớp cha, gọi `super(initialBalance)` và hiện thực `pay`. Vì `Wallet implements PaymentMethod`, đối tượng `GiftWallet` cũng dùng được tại vị trí cần `PaymentMethod`.
4. **Đa hình (polymorphism), khái niệm liên quan:** biến khai báo kiểu interface `PaymentMethod` tham chiếu đến đối tượng thực `GiftWallet`; Java chọn phương thức instance override tương ứng khi chạy. Đa hình thường được tạo điều kiện bởi trừu tượng hóa và quan hệ kiểu con, nhưng không đồng nghĩa với cả ba nguyên lý trên.

Dòng `method.pay(100)` trả về «Không đủ số dư» vì chỉ còn 60. Sau lời gọi thất bại, số dư vẫn là 60. Đây là một minh chứng kiểm soát thay đổi trạng thái bằng đóng gói. Mẫu ví dụ không xử lý nhiều luồng và không nhằm làm mô hình ví điện tử hoàn chỉnh.

## 6. Ba nguyên lý độc lập nhưng có thể phối hợp

**Có trừu tượng hóa mà thiếu đóng gói:** một lớp triển khai `PaymentMethod` có thể vẫn lộ `public int balance`. Người gọi có hợp đồng thanh toán, nhưng vẫn được phép làm hỏng trạng thái. Sự tồn tại của interface không tự khóa các trường nội bộ.

**Có đóng gói mà không cần kế thừa:** một lớp `final Counter` giữ `private int count` và cung cấp `increment()`, `getCount()` có thể đóng gói tốt mà không cần tự định nghĩa lớp con nào. Mọi lớp Java đều có quan hệ với `Object`, nhưng thiết kế đóng gói này không phụ thuộc vào việc tạo một hệ phân cấp kế thừa nghiệp vụ.

**Có kế thừa mà trừu tượng hóa yếu:** một lớp con có thể `extends` lớp cha phơi bày quá nhiều thao tác không liên quan. Tái sử dụng mã không tự biến API thành một hợp đồng rõ ràng.

**Cả ba kết hợp:** interface mô tả điều người dùng cần; lớp cơ sở giữ và kiểm soát trạng thái; lớp chuyên biệt mở rộng hành vi của lớp cơ sở. Đây là một cách tổ chức hợp lý *khi* thực sự tồn tại quan hệ kiểu con. Không nhất thiết bài toán nào cũng cần đủ ba kỹ thuật.

## 7. Cách quyết định khi thiết kế

| Tình huống | Ưu tiên suy nghĩ về | Vì sao |
|---|---|---|
| Nhiều cách gửi thông báo nhưng bên gọi chỉ cần `send(message)` | Trừu tượng hóa bằng hợp đồng thích hợp | Bên gọi không phụ thuộc SMTP, HTTP hoặc cơ chế cụ thể |
| Số dư, tuổi hoặc trạng thái đơn hàng phải tuân thủ quy tắc | Đóng gói và API chuyển trạng thái | Ngăn mã ngoài đưa đối tượng vào trạng thái không hợp lệ |
| `SavingsAccount` thật sự là một `Account` theo hợp đồng đã nêu | Kế thừa có kiểm tra LSP | Có thể dùng kiểu con tại vị trí đòi hỏi kiểu cha |
| `Car` dùng `Engine` | Composition | Xe **có** động cơ, không **là** động cơ |
| Nhiều lớp chỉ trùng vài hàm tiện ích | Composition hoặc dịch vụ hỗ trợ | Tránh ép thành quan hệ kiểu con giả tạo |

Một quy trình thực hành hiệu quả: (1) xác định khả năng bên ngoài cần dùng; (2) xác định trạng thái, quy tắc và dữ liệu nào phải được bảo vệ; (3) cuối cùng mới quyết định có quan hệ kiểu con thật sự hay nên kết hợp các đối tượng. Các bước này phục vụ thiết kế, không phải một luật bắt buộc áp dụng máy móc cho mọi chương trình.

## 8. Lỗi nhầm lẫn thường gặp

**“Abstraction = `abstract class`, encapsulation = `private`, inheritance = `extends`.”** Đây chỉ là một số công cụ cú pháp. Trừu tượng hóa có thể đến từ interface hoặc public API của lớp thường; đóng gói đòi hỏi kiểm soát hành vi và bất biến; quan hệ kiểu con còn có qua `implements`.

**“Abstraction và encapsulation đều ẩn chi tiết, vậy là một.”** Trừu tượng hóa quyết định *cách người dùng nhìn và sử dụng khả năng*; đóng gói kiểm soát *quyền truy cập, cách trạng thái được sửa và cách bất biến được giữ*. Một API có thể đơn giản nhưng làm lộ mutable state, hoặc bảo vệ state kỹ nhưng vẫn có API rườm rà.

**“Trường `private` + getter/setter cho hết là encapsulation tốt.”** Setter công khai không kiểm tra có thể triệt tiêu lợi ích. Hãy ưu tiên những hành động có ý nghĩa nghiệp vụ và kiểm soát điều kiện trước khi thay đổi.

**“Kế thừa luôn tốt hơn composition vì tái sử dụng code.”** Kế thừa ràng buộc kiểu con vào hợp đồng và hành vi của kiểu cha. Khi chỉ cần dùng chức năng của đối tượng khác, composition thường hợp lý hơn.

**“Override chỉ là viết lại phương thức giống tên.”** Override cần đúng chữ ký và các ràng buộc của Java; quan trọng hơn, hành vi mới phải giữ hợp đồng mà người dùng kiểu cha được quyền kỳ vọng. Nên dùng `@Override` để phát hiện nhầm lẫn lúc biên dịch.

**“Interface chỉ có phương thức trừu tượng, abstract class chỉ có phương thức trừu tượng.”** Sai trong Java hiện đại: interface có thể có phương thức `default`/`static` và phương thức phụ trợ `private`; abstract class có thể chứa dữ liệu, constructor và phương thức đã cài đặt.

## 9. Tự kiểm tra

1. Một `BankAccount` có `private balance` nhưng cho phép `setBalance(-500)`. Có đủ đóng gói tốt chưa? **Chưa**: trạng thái không được bảo vệ bằng quy tắc hợp lệ.
2. `OrderService` nhận tham số `PaymentMethod` thay vì `GiftWallet`. Nguyên lý nào nổi bật? **Trừu tượng hóa**: phụ thuộc hợp đồng chung; đa hình cho phép nhiều hiện thực.
3. `Cat extends Animal` để dùng lại và chuyên biệt hóa hành vi `makeSound()`. Quan hệ nào đang dùng? **Kế thừa**; cần kiểm tra mọi `Cat` có đáp ứng hợp đồng `Animal` hay không.
4. `Car extends Engine` chỉ để gọi `start()` có hợp lý không? **Không**: quan hệ thực tế là *has-a*, nên cân nhắc `Car` giữ một đối tượng `Engine`.
5. Có thể thiết kế lớp đóng gói tốt mà không tạo interface hoặc abstract class riêng không? **Có**: giữ trạng thái riêng, cung cấp thao tác có kiểm soát và duy trì bất biến.

**Ghi nhớ khi đọc code Java:** nhìn vào **API/hợp đồng** để nhận diện trừu tượng hóa; nhìn vào **trạng thái, quyền truy cập và kiểm tra bất biến** để đánh giá đóng gói; nhìn vào **quan hệ `extends`/`implements` và khả năng thay thế** để đánh giá kế thừa/kiểu con.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Hợp đồng, cách triển khai và mã sử dụng hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng, cách triển khai và mã sử dụng hợp đồng trong Java

Trong Java, thiết kế theo **hợp đồng** giúp một phần chương trình mô tả *điều có thể yêu cầu* mà không buộc bên sử dụng phải biết *công việc được thực hiện bằng cách nào*. Ba vai trò cần phân biệt là **hợp đồng (contract)**, **lớp triển khai (implementation)** và **mã sử dụng hợp đồng (consumer/client)**. Đây là nền tảng của đa hình, thay thế implementation, kiểm thử độc lập và giảm phụ thuộc giữa các thành phần.

Sau bài này, bạn cần giải thích được ba vai trò, đọc được luồng gọi phương thức, tự thiết kế một interface có ý nghĩa, hoán đổi implementation mà không sửa consumer, và nhận diện được các trường hợp “đúng chữ ký nhưng sai hành vi”.

## 1. Ba vai trò trong cùng một thiết kế

| Vai trò | Trách nhiệm chính | Biết điều gì? | Ví dụ |
| --- | --- | --- | --- |
| **Hợp đồng** | Công bố các thao tác được phép dùng và quy tắc hành vi mong đợi | Kiểu tham số, kiểu trả về, ngoại lệ, ý nghĩa của thao tác | Interface MessageSender |
| **Implementation** | Thực hiện các thao tác của hợp đồng bằng một cơ chế cụ thể | Cách gửi email, ghi log, gọi API hoặc lưu dữ liệu | EmailSender, ConsoleSender |
| **Consumer** | Gọi thao tác để giải quyết nghiệp vụ | Chỉ cần năng lực do hợp đồng cung cấp | AlertService |

Quan hệ thông thường:

~~~text
                 sử dụng qua kiểu MessageSender
AlertService ------------------------------------------> MessageSender
                                                              ^
                                                              |
                                                 implements   |   implements
                                                EmailSender   |   ConsoleSender
~~~

Mũi tên từ consumer đến interface biểu diễn **phụ thuộc ở mức mã nguồn**: consumer khai báo trường, tham số hoặc biến bằng kiểu hợp đồng. Ở thời điểm chạy, một đối tượng triển khai cụ thể được truyền vào consumer, và phương thức thực thi của đối tượng đó được gọi.

Ví dụ, AlertService cần khả năng “gửi thông báo”, không cần biết gửi qua SMTP, REST, bộ nhớ hay màn hình console. Nếu AlertService tự tạo EmailSender và gọi các phương thức riêng của EmailSender, hai thành phần bị gắn chặt vào nhau; việc thay phương thức gửi hoặc kiểm thử sẽ khó hơn.

**Phân biệt hai lớp nghĩa của từ “hợp đồng”:**

- **Hợp đồng cấu trúc**: những thành phần Java kiểm tra được tại thời điểm biên dịch, như tên phương thức, tham số, kiểu trả về, khả năng truy cập và quy tắc override.
- **Hợp đồng hành vi**: ý nghĩa của phương thức, điều kiện đầu vào, kết quả, lỗi có thể phát sinh và tác động phụ mà người viết và người dùng thống nhất. Trình biên dịch Java **không tự chứng minh** một implementation tuân thủ đầy đủ những quy tắc này.

Ví dụ, interface khai báo phương thức send(Message) không đảm bảo phương thức thật sự gửi, xử lý đúng lỗi hay tránh gửi trùng. Các điều đó cần mô tả và kiểm thử.

## 2. Ví dụ hoàn chỉnh: một hợp đồng, hai cách triển khai, một consumer

Tạo tệp **ContractDemo.java** với nội dung dưới đây. Mọi kiểu ở ví dụ cùng nằm trong một tệp; chỉ ContractDemo là lớp public. Hai sender chỉ **mô phỏng gửi thông báo qua console**, không kết nối mạng hoặc gửi email thật.

~~~java
import java.util.Objects;

// Dữ liệu truyền qua ranh giới hợp đồng.
record Message(String recipient, String body) {
    Message {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(body, "body");
        if (recipient.isBlank() || body.isBlank()) {
            throw new IllegalArgumentException("recipient/body khong duoc trong");
        }
    }
}

// (1) HOP DONG: consumer chi can biet phuong thuc nay.
interface MessageSender {
    void send(Message message);
}

// (2a) IMPLEMENTATION thu nhat.
final class EmailSender implements MessageSender {
    @Override
    public void send(Message message) {
        System.out.println("EMAIL (mo phong) -> "
                + message.recipient() + ": " + message.body());
    }
}

// (2b) IMPLEMENTATION thu hai.
final class ConsoleSender implements MessageSender {
    @Override
    public void send(Message message) {
        System.out.println("CONSOLE -> "
                + message.recipient() + ": " + message.body());
    }
}

// (3) CONSUMER: chi phu thuoc MessageSender.
final class AlertService {
    private final MessageSender sender;

    AlertService(MessageSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    void notifyUser(String recipient, String body) {
        sender.send(new Message(recipient, body));
    }
}

public class ContractDemo {
    public static void main(String[] args) {
        AlertService productionLike =
                new AlertService(new EmailSender());
        productionLike.notifyUser(
                "an@example.com", "Don hang da duoc duyet");

        AlertService consoleMode =
                new AlertService(new ConsoleSender());
        consoleMode.notifyUser("tester", "Da gui thong bao");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ContractDemo.java
java ContractDemo
~~~

Kết quả chuẩn:

~~~text
EMAIL (mo phong) -> an@example.com: Don hang da duoc duyet
CONSOLE -> tester: Da gui thong bao
~~~

Đọc ví dụ theo trình tự:

1. **MessageSender** nói rằng một sender nhận một Message và thực hiện send; nó không quyết định cách in hoặc gửi.
2. **EmailSender** và **ConsoleSender** cùng tuyên bố implements MessageSender và cung cấp send(Message). Từ khóa **@Override** giúp trình biên dịch phát hiện một số lỗi viết sai phương thức.
3. **AlertService** nhận MessageSender qua constructor, lưu ở trường cùng kiểu và chỉ gọi send. Cả hai implementation đều có thể được truyền vào mà không cần sửa mã AlertService.
4. Trong main, biểu thức new EmailSender() và new ConsoleSender() quyết định **đối tượng cụ thể** tại thời điểm tạo consumer. Đó là cách “kết nối” các thành phần (wiring/composition).
5. **Message** là record lưu dữ liệu và kiểm tra đầu vào rỗng. Quy tắc này giảm khả năng consumer vô tình gửi yêu cầu có nội dung trống; nó không bảo đảm một dịch vụ bên ngoài đã gửi thành công.

Điểm cần nhớ: consumer vẫn *cần một đối tượng thực hiện công việc*; “phụ thuộc vào interface” không có nghĩa là có thể chạy chương trình mà không cung cấp implementation.

## 3. Kiểu tham chiếu và dispatch động

Trong Java, kiểu khai báo của một biến quyết định **những phương thức có thể gọi ở lúc biên dịch**. Với phương thức instance được override, lớp thực tế của đối tượng quyết định **implementation được thực thi tại runtime**.

~~~java
MessageSender sender = new EmailSender();
sender.send(new Message("an@example.com", "Xin chao"));
~~~

Đoạn trên là minh họa đặt trong một phương thức của tệp ví dụ trước. Biến sender có kiểu tĩnh MessageSender, còn đối tượng thực là EmailSender. Trình biên dịch chấp nhận lời gọi vì MessageSender khai báo send; khi chạy, phương thức EmailSender.send được gọi.

Nếu EmailSender có thêm phương thức riêng connectSmtp(), biểu thức sender.connectSmtp() **không biên dịch**, vì connectSmtp() không thuộc API của MessageSender. Việc ép kiểu xuống EmailSender có thể làm code chạy với một implementation nhưng phá khả năng thay thế; chỉ dùng khi thật sự cần logic đặc thù và đã kiểm tra kiểu.

Không nhầm **đa hình qua phương thức override** với **overload**: overload chọn chữ ký thích hợp chủ yếu dựa trên kiểu tham số tại lúc biên dịch; override của phương thức instance được phân phối dựa trên đối tượng runtime. Static method không được override theo kiểu dispatch này.

## 4. Thiết kế một hợp đồng tốt

Hãy bắt đầu từ việc **consumer cần làm gì**, sau đó xác định tên thao tác, dữ liệu vào/ra, quy tắc lỗi và tác động phụ. Tránh đưa chi tiết kỹ thuật riêng của một implementation vào interface dùng chung.

Với MessageSender, một thỏa thuận hành vi thực tế có thể ghi rõ:

| Câu hỏi hợp đồng | Quy tắc cần định nghĩa |
| --- | --- |
| Điều kiện đầu vào | Message không null; recipient và body không rỗng |
| Thành công có nghĩa gì? | send hoàn tất sau khi bàn giao cho bộ gửi, hay sau khi hệ thống nhận xác nhận? Phải chọn một nghĩa |
| Khi gửi thất bại? | Ném ngoại lệ, trả về kết quả thất bại hoặc thử lại? Quy định nhất quán |
| Có gửi trùng không? | Nếu retry, cần hay không cần mã idempotency? |
| Có side effect nào? | Gửi mạng, ghi log, thay đổi trạng thái, tính phí... |
| Thread-safe không? | Có cho phép nhiều luồng gọi chung một sender? Nếu có, bảo đảm ở mức nào? |

Trong ví dụ chạy được ở trên, send chỉ in ra console. Vì vậy không thể suy ra **đã giao email thành công**, cơ chế retry hay thread safety của một hệ thống gửi thực. Những bảo đảm đó chỉ được thêm vào hợp đồng và implementation khi thực sự cần.

**Nguyên tắc khi thay thế implementation:** consumer không nên phải đổi logic hay chịu kết quả trái với những bảo đảm đã công bố. Ví dụ, nếu hợp đồng hứa rằng một lệnh gửi lỗi sẽ báo thất bại, implementation âm thầm bỏ qua lỗi là sai **hợp đồng hành vi** dù vẫn biên dịch. Đây là tinh thần của khả năng thay thế (Liskov substitution): implementation không được làm suy yếu những điều kiện mà consumer có quyền kỳ vọng.

Đặt tên theo **năng lực** thường giúp hợp đồng rõ hơn tên theo công nghệ: MessageSender diễn tả “gửi thông báo”; SmtpMessageSender hoặc EmailSender mô tả một cách thực hiện. Tuy nhiên, đừng tạo interface một phương thức vô nghĩa cho mọi class; hợp đồng có giá trị khi nó biểu diễn một ranh giới thật sự được dùng hoặc có khả năng thay thế/kiểm thử.

## 5. Interface, abstract class và lớp cụ thể

Java cung cấp nhiều cách biểu diễn hợp đồng. Chọn dựa trên nhu cầu chia sẻ API, trạng thái và logic.

| Đặc điểm | Interface | Abstract class |
| --- | --- | --- |
| Mục đích nổi bật | Công bố năng lực để nhiều kiểu cùng triển khai | Cung cấp khuôn mẫu và phần triển khai dùng chung cho các lớp có quan hệ kế thừa |
| Quan hệ từ lớp cụ thể | implements nhiều interface | extends tối đa một lớp cha |
| Trạng thái instance | Không có trường instance; trường khai báo trong interface ngầm là public static final | Có trường instance, constructor và trạng thái được bảo vệ |
| Phương thức | Có abstract, default, static và private (theo phiên bản Java phù hợp) | Có abstract và phương thức có thân; có thể dùng protected |
| Constructor | Không có constructor | Có constructor để khởi tạo phần trạng thái của lớp cha |

Một interface có thể khai báo phương thức default để chia sẻ hành vi mặc định, nhưng **default method không biến interface thành nơi chứa trạng thái instance**. Từ Java 9, interface còn có private method để tái sử dụng logic nội bộ giữa các default/static method.

Abstract class thích hợp khi những lớp con thật sự chia sẻ quy trình hoặc invariant. Ví dụ minh họa (không phải chương trình độc lập):

~~~java
abstract class BaseSender implements MessageSender {
    protected void validate(Message message) {
        Objects.requireNonNull(message, "message");
    }
}

final class LoggingSender extends BaseSender {
    @Override
    public void send(Message message) {
        validate(message);
        System.out.println("Logging: " + message.body());
    }
}
~~~

Đoạn này sử dụng import java.util.Objects và record/interface Message, MessageSender đã khai báo ở ví dụ trước. Nó cho thấy một abstract class có thể **triển khai interface** và cung cấp logic dùng chung. Việc có sẵn logic validate chưa đủ để kết luận bắt buộc dùng kế thừa; nhiều trường hợp một đối tượng Validator riêng được truyền vào theo **composition** sẽ linh hoạt hơn.

Một số quy tắc biên dịch quan trọng:

- Khi class triển khai một interface, phương thức thực hiện phương thức public của interface phải có mức truy cập **public** (không thể giảm xuống package-private hay private).
- Kiểu trả về của phương thức override phải phù hợp, có thể là kiểu con theo quy tắc covariant return của Java.
- Implementation không được khai báo thêm checked exception rộng hơn những gì phương thức ở hợp đồng cho phép. Unchecked exception vẫn có thể phát sinh nhưng cần mô tả ý nghĩa hợp đồng.
- Một lớp cụ thể phải cài đặt đầy đủ các phương thức abstract được thừa kế, nếu không nó cũng phải được khai báo abstract.
- Nếu hai interface cung cấp default method trùng chữ ký mà không có cách phân giải ưu tiên hợp lệ, lớp triển khai phải override để giải quyết xung đột.
- Interface không tự động làm một đối tượng bất biến. Tính bất biến và thread safety là đặc tính của implementation và dữ liệu liên quan.

## 6. Ai tạo implementation, ai sử dụng?

Thiết kế có hai loại công việc khác nhau:

- **Composition/wiring**: quyết định tạo đối tượng cụ thể nào và truyền nó vào đâu. Trong ví dụ, main làm việc này bằng new EmailSender() hoặc new ConsoleSender().
- **Business behavior**: consumer gọi hợp đồng để thực hiện nghiệp vụ. AlertService không xây sender; nó nhận sender qua constructor.

Truyền phụ thuộc qua constructor (constructor injection) có ba lợi ích: mã consumer trình bày rõ thứ nó cần, đối tượng sau khi tạo có trạng thái phụ thuộc hợp lệ, và có thể thay implementation ở vị trí wiring. Một framework DI như Spring có thể hỗ trợ tự tạo và nối đối tượng, nhưng nguyên tắc thiết kế đã áp dụng đầy đủ trong **Java thuần** và không yêu cầu framework.

So sánh:

~~~java
// Gan chat vao cach trien khai cu the:
final class TightlyCoupledAlertService {
    private final EmailSender sender = new EmailSender();
}

// Linh hoat qua hop dong:
final class LooselyCoupledAlertService {
    private final MessageSender sender;

    LooselyCoupledAlertService(MessageSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }
}
~~~

Đây là các đoạn minh họa dùng lại EmailSender/MessageSender và import java.util.Objects của ví dụ đầu, không phải tệp độc lập. Khác biệt chính không nằm ở việc có dùng new ở bất kỳ đâu hay không: new vẫn xuất hiện ở **vùng wiring**; điều cần tránh là consumer tự chọn và phụ thuộc vào chi tiết bên gửi.

Cũng cần tránh cấu trúc quá mức: nếu một chức năng chỉ có một implementation, không có ranh giới cần kiểm thử/hoán đổi và API đơn giản, một lớp cụ thể trực tiếp có thể là lựa chọn tốt. Đánh giá theo nhu cầu sử dụng thực tế.

## 7. Kiểm thử consumer bằng implementation giả

Một lợi ích cụ thể của hợp đồng là có thể kiểm tra **AlertService đã gửi đúng thông điệp** mà không cần mạng và không phải nhìn output console. Tạo tệp thứ hai **ContractDemoTest.java** cạnh ContractDemo.java:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ContractDemoTest {
    static final class RecordingSender implements MessageSender {
        final List<Message> sent = new ArrayList<>();

        @Override
        public void send(Message message) {
            sent.add(message);
        }
    }

    public static void main(String[] args) {
        RecordingSender fake = new RecordingSender();
        AlertService service = new AlertService(fake);

        service.notifyUser("tester", "Da gui thong bao");

        Message expected = new Message("tester", "Da gui thong bao");
        if (fake.sent.size() != 1 || !fake.sent.get(0).equals(expected)) {
            throw new AssertionError("Sai so lan gui hoac noi dung");
        }
        System.out.println("PASS: 1 message");
    }
}
~~~

Biên dịch và chạy (hai tệp cùng thư mục, không khai báo package):

~~~bash
javac -encoding UTF-8 ContractDemo.java ContractDemoTest.java
java ContractDemoTest
~~~

Kết quả:

~~~text
PASS: 1 message
~~~

RecordingSender là một **test double/fake**: nó tuân thủ cùng interface, nhưng thay việc gửi thực bằng ghi nhận lời gọi. Kiểm thử trên chứng minh consumer tạo và chuyển đúng thông điệp; nó **không chứng minh** EmailSender kết nối được SMTP hoặc nhà cung cấp email hoạt động. Các implementation thật cần kiểm thử riêng cho phần tích hợp bên ngoài và các quy tắc hành vi của chúng.

## 8. Thay đổi hợp đồng và tương thích

Hợp đồng là điểm kết nối giữa nhiều phần mã nên thay đổi nó có thể lan đến cả consumer lẫn implementation:

1. **Thêm một phương thức abstract vào interface**: các lớp implement hiện hữu thường phải bổ sung phương thức, nếu không sẽ có lỗi biên dịch khi build lại.
2. **Thêm một default method**: có thể giữ được tương thích nguồn cho nhiều implementation, nhưng vẫn cần xét xung đột default method, ý nghĩa mặc định và các loại tương thích (source/binary/behavior).
3. **Đổi kiểu tham số, tên phương thức hoặc kiểu trả về**: có thể buộc consumer và implementation sửa code; phải đánh giá tác động thay vì coi là thay đổi nội bộ.
4. **Đổi hành vi dù chữ ký không đổi**: cũng là thay đổi hợp đồng. Ví dụ chuyển từ “ném lỗi khi gửi thất bại” sang “bỏ qua lỗi” có thể làm consumer xử lý sai, mặc dù mã vẫn biên dịch.

Cách mở rộng an toàn là mô tả trước kỳ vọng, xem xét mọi implementation, chạy kiểm thử contract và consumer, rồi mới đưa API mới vào sử dụng. Nếu cần một năng lực riêng cho số ít implementation, cân nhắc tạo một hợp đồng khác thay vì ép toàn bộ lớp hiện hữu gánh phương thức không hợp lệ với chúng. Đây là ý tưởng phân tách interface theo nhu cầu thực của bên dùng.

Đối với thư viện đã phát hành, mức độ tương thích cần đánh giá cả mã đã biên dịch và các client ngoài repository; không nên kết luận một thay đổi “tương thích” chỉ vì một project Java nội bộ vẫn build được.

## 9. Các lỗi hay gặp và cách tự kiểm tra

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Consumer khai báo trường bằng implementation cụ thể và tự new | Gắn chặt vào một công nghệ; khó thay thế | Nhận tham số mang kiểu hợp đồng ở constructor hoặc phương thức |
| Interface mang tên chung nhưng chứa quá nhiều phương thức không liên quan | Lớp triển khai buộc có các thao tác vô nghĩa | Tách hợp đồng theo nhóm consumer/năng lực |
| Cho rằng implements bảo đảm đúng nghiệp vụ | Implementation có thể trả giá trị sai, nuốt lỗi, gây side effect bất ngờ | Ghi hợp đồng hành vi và kiểm thử từng implementation |
| Ép kiểu từ interface sang class cụ thể để gọi API riêng | Thay implementation có thể gây ClassCastException hoặc hỏng logic | Đưa năng lực thực sự chung vào interface phù hợp hoặc tách luồng riêng |
| Dùng abstract class chỉ để tái sử dụng vài dòng tiện ích | Bị khóa bởi một superclass; kế thừa gượng ép | Xem xét composition hoặc utility phù hợp |
| Dùng default method làm nơi giấu hành vi nguy hiểm | Implementation cũ âm thầm nhận hành vi không mong muốn | Chỉ dùng khi mặc định có ý nghĩa hợp đồng và được kiểm thử |
| Chỉ kiểm tra fake rồi tuyên bố đã tích hợp thành công | Bỏ sót lỗi hạ tầng/IO và khác biệt implementation | Tách unit test consumer và integration/contract test implementation |

Khi đọc một đoạn Java có interface, hãy tự trả lời:

1. **Ai là contract?** Phương thức nào consumer được phép gọi và quy tắc hành vi nào đã được cam kết?
2. **Ai là implementation?** Lớp nào khai báo implements hoặc kế thừa, phần nào chứa xử lý thật?
3. **Ai là consumer?** Nó khai báo phụ thuộc bằng kiểu gì? Nó có biết chi tiết implementation không?
4. **Ai wiring?** Đối tượng cụ thể được tạo ở đâu và được đưa đến consumer bằng cách nào?
5. **Có thể thay implementation?** Nếu thay EmailSender bằng RecordingSender hoặc ConsoleSender, business logic có chạy đúng kỳ vọng mà không sửa không?
6. **Bằng chứng nào xác nhận?** Lệnh biên dịch, output minh họa, unit test consumer và kiểm thử hành vi/tích hợp của implementation có trả lời đúng câu hỏi riêng của chúng không?

## 10. Tổng kết tư duy áp dụng

Một thiết kế tốt bắt đầu từ **nhu cầu của consumer**: định nghĩa một API nhỏ, dễ hiểu cùng bảo đảm hành vi cần thiết; cung cấp một hoặc nhiều implementation; rồi đưa implementation vào consumer qua hợp đồng. **Interface mô tả năng lực, implementation cung cấp cơ chế, consumer giải quyết nghiệp vụ, còn vùng wiring chọn đối tượng thực tế.**

Cấu trúc này phát huy tác dụng khi cần đa hình, thay thế chiến lược, cấu hình môi trường, kiểm thử độc lập và quản lý phụ thuộc. Nó không thay thế việc đặc tả hành vi, kiểm thử thực tế hay chọn ranh giới module hợp lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Vai trò của lớp trừu tượng và interface trong Java</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của lớp trừu tượng (abstract class) và interface trong Java

## 1. Bài toán mà hai cơ chế giải quyết

Khi nhiều đối tượng có điểm chung, Java cho phép mô tả **phần ổn định** ở một kiểu trừu tượng và để từng kiểu cụ thể quyết định **phần thay đổi**. Nhờ đó, mã sử dụng có thể làm việc với kiểu chung thay vì biết từng lớp triển khai.

- **Lớp trừu tượng (abstract class)** phù hợp khi các lớp con thực sự thuộc cùng một họ, có trạng thái hoặc cách xử lý chung, nhưng vẫn cần một số bước do lớp con cung cấp.
- **Interface** mô tả một **hợp đồng năng lực/hành vi** mà nhiều lớp, kể cả các lớp không cùng họ kế thừa, có thể thực hiện.
- Cả hai đều hỗ trợ **đa hình**: biến có kiểu cha/interface có thể tham chiếu tới đối tượng thuộc lớp triển khai; lời gọi phương thức instance sẽ chạy phiên bản phù hợp với đối tượng thực tế.

Ví dụ về ý tưởng: nhiều loại thuê bao đều có người đăng ký và quy trình báo phí giống nhau, nên có thể dùng lớp trừu tượng `Subscription`. Việc gửi thông báo có thể thực hiện qua email, console hoặc hệ thống khác, nên có thể mô tả bằng interface `Channel`. Đây là **hai trục thay đổi độc lập**.

## 2. Lớp trừu tượng: chia sẻ nền tảng và quy định khung xử lý

Khai báo một lớp bằng từ khóa `abstract`:

```java
abstract class Vehicle {
    private final String id;

    protected Vehicle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public abstract int maxSpeed();
}
```

Đoạn trên là khai báo kiểu, không phải chương trình độc lập có `main`. Nó minh họa rằng lớp trừu tượng có thể chứa cả **dữ liệu instance**, **constructor**, **phương thức đã triển khai** và **phương thức trừu tượng**.

Các đặc điểm cần hiểu:

1. Không thể khởi tạo trực tiếp `new Vehicle("V1")`. Phải tạo đối tượng của một lớp con cụ thể.
2. Constructor của lớp trừu tượng vẫn được gọi như một phần của chuỗi khởi tạo lớp con; nó có thể thiết lập và kiểm tra các bất biến chung.
3. Phương thức `abstract` chỉ khai báo chữ ký, không có thân; lớp con cụ thể phải triển khai những phương thức trừu tượng còn thiếu. Một lớp con chưa triển khai đủ phải tiếp tục là `abstract`.
4. Lớp trừu tượng cũng có thể không khai báo phương thức `abstract` nào: từ khóa `abstract` vẫn ngăn khởi tạo trực tiếp và biểu đạt rằng đây là lớp nền.
5. Có thể dùng `private`, `protected`, `public`, `static`, `final`, phương thức cụ thể và trường instance theo các quy tắc thông thường của lớp Java. Một phương thức không thể đồng thời là `abstract` và `final` vì một bên đòi lớp con triển khai, bên kia cấm ghi đè.
6. Lớp Java chỉ có **một lớp cha trực tiếp** (`extends` một class); lớp trừu tượng không làm thay đổi giới hạn này.

Một cách dùng quan trọng là **Template Method**: lớp cha giữ thứ tự các bước chung, các phương thức trừu tượng tạo điểm mở rộng. Khi quy trình phải thống nhất, có thể đánh dấu phương thức điều phối là `final` và chỉ cho lớp con thay đổi những bước được định nghĩa rõ.

## 3. Interface: hợp đồng để các lớp cộng tác

Ví dụ:

```java
interface Payable {
    int amount();

    default boolean isFree() {
        return amount() == 0;
    }
}
```

`Payable` yêu cầu lớp triển khai cung cấp `amount()`; phương thức `isFree()` là hành vi mặc định dùng chung.

Những quy tắc quan trọng trong Java 17+:

- Phương thức thông thường khai báo trong interface là `public abstract` theo ngầm định. Một lớp triển khai phải dùng phạm vi `public` cho phương thức ghi đè; không được giảm xuống package-private hoặc `protected`.
- Interface có thể có phương thức `default` (có thân và được thừa hưởng), `static` (gọi qua tên interface) và `private`/`private static` (hỗ trợ tái sử dụng logic bên trong interface). Không phải mọi phương thức interface đều trừu tượng.
- Trường khai báo trong interface luôn mang tính `public static final`, tức hằng số; interface **không có trường instance hoặc constructor** để giữ trạng thái riêng của từng đối tượng.
- Một class có thể `implements` **nhiều interface**, trong khi một interface có thể `extends` nhiều interface. Điều này cho phép kết hợp các năng lực độc lập mà không cần đa kế thừa class.
- `default` không biến interface thành nơi lý tưởng để chứa mọi xử lý chung hoặc trạng thái. Nó hữu ích để cung cấp hành vi mặc định có liên quan chặt tới hợp đồng; lớp triển khai vẫn có thể ghi đè.
- Interface có thể là kiểu của tham số, biến, trường hoặc kiểu trả về. Nhờ đó, thành phần nhận `Payable` chỉ cần biết `amount()`, không cần biết lớp cụ thể nào cung cấp.

Ví dụ một lớp có hai năng lực:

```java
interface Printable {
    void print();
}

interface Exportable {
    void export();
}

final class Report implements Printable, Exportable {
    @Override
    public void print() {
        System.out.println("Print report");
    }

    @Override
    public void export() {
        System.out.println("Export report");
    }
}
```

Các khai báo này biên dịch được như một nhóm kiểu, nhưng không có `main` để chạy độc lập. `Report` nhận hai hợp đồng hành vi mà không cần kế thừa hai lớp cha.

## 4. So sánh và quan hệ giữa hai cơ chế

| Tiêu chí | Abstract class | Interface |
| --- | --- | --- |
| Câu hỏi thiết kế | “Các lớp này cùng là một loại gì, và chia sẻ nền tảng nào?” | “Đối tượng này có thể thực hiện năng lực gì?” |
| Quan hệ thường dùng | `extends` một lớp cha | `implements` một hoặc nhiều interface |
| Trạng thái instance | Có thể chứa trường instance và constructor | Không có trường instance hoặc constructor; trường là hằng số |
| Mã xử lý chung | Phương thức cụ thể, `protected`, `final`, template method | `default`, `static`, `private` theo quy tắc interface |
| Điều bắt buộc ở lớp con | Triển khai phương thức trừu tượng còn thiếu | Triển khai phương thức trừu tượng còn thiếu từ các interface |
| Tính linh hoạt | Thích hợp với họ lớp có cấu trúc chung | Hợp tác giữa các họ lớp khác nhau, dễ thay thế implementation |
| Số lượng quan hệ trực tiếp | Một class cha | Nhiều interface |

**Hai cơ chế có thể kết hợp:** một lớp trừu tượng có thể `implements` một hay nhiều interface và để các lớp con hoàn tất hợp đồng. Một lớp cụ thể cũng có thể vừa `extends` một abstract class, vừa `implements` nhiều interface.

Điểm mấu chốt là **chọn theo quan hệ thiết kế**, không dựa vào việc interface “mạnh hơn” hay abstract class “cũ hơn”. Nếu chỉ cần công bố khả năng để người gọi phụ thuộc vào kiểu chung, interface thường là lựa chọn đơn giản. Nếu thực sự cần chia sẻ trạng thái hợp lệ và điều phối vòng đời/hành vi giữa các lớp cùng họ, abstract class có giá trị rõ ràng.

## 5. Ví dụ Java 17+ chạy được: dùng đồng thời abstract class và interface

Lưu mã sau vào **`Demo.java`** (không khai báo `package`):

```java
interface Channel {
    void send(String message);

    default void welcome() {
        send("Welcome!");
    }
}

abstract class Subscription {
    private final String subscriber;

    protected Subscription(String subscriber) {
        if (subscriber == null || subscriber.isBlank()) {
            throw new IllegalArgumentException("Subscriber is required");
        }
        this.subscriber = subscriber;
    }

    public final void report(Channel channel) {
        channel.send(subscriber + ": " + monthlyPrice() + " USD/month");
    }

    protected abstract int monthlyPrice();
}

final class FreeSubscription extends Subscription {
    FreeSubscription(String subscriber) {
        super(subscriber);
    }

    @Override
    protected int monthlyPrice() {
        return 0;
    }
}

final class PremiumSubscription extends Subscription {
    PremiumSubscription(String subscriber) {
        super(subscriber);
    }

    @Override
    protected int monthlyPrice() {
        return 15;
    }
}

final class ConsoleChannel implements Channel {
    @Override
    public void send(String message) {
        System.out.println(message);
    }
}

public class Demo {
    public static void main(String[] args) {
        Channel channel = new ConsoleChannel();
        Subscription[] plans = {
            new FreeSubscription("Lan"),
            new PremiumSubscription("Minh")
        };

        for (Subscription plan : plans) {
            plan.report(channel);
        }
        channel.welcome();
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp (JDK 17 trở lên):

```bash
javac Demo.java
java Demo
```

Kết quả chuẩn trên stdout:

```text
Lan: 0 USD/month
Minh: 15 USD/month
Welcome!
```

Đọc luồng chạy theo từng bước:

1. `new FreeSubscription("Lan")` và `new PremiumSubscription("Minh")` đều đi qua constructor `Subscription`, nên phép kiểm tra tên đăng ký dùng chung không bị lặp ở từng lớp con.
2. Mảng `Subscription[]` giữ nhiều lớp triển khai. Phương thức `report()` là cùng một quy trình chung; lời gọi `monthlyPrice()` được phân phối theo **kiểu thực tế** của phần tử: `0` đối với `FreeSubscription` và `15` đối với `PremiumSubscription`.
3. `report(Channel channel)` chỉ phụ thuộc vào hợp đồng gửi thông báo. Vì vậy có thể thay `ConsoleChannel` bằng implementation khác mà không sửa `Subscription` hoặc hai lớp con.
4. `channel.welcome()` sử dụng phương thức `default` của interface, sau đó gọi `send()` của implementation thực tế để in dòng cuối.
5. `final` trên `report()` giữ nguyên thứ tự xử lý mà lớp nền đã thiết kế; các lớp con chỉ quyết định mức phí qua điểm mở rộng `monthlyPrice()`.

Đây là ví dụ của **đa hình + kế thừa có kiểm soát + lập trình hướng hợp đồng**. Phần phụ thuộc vào cấu trúc chung dùng abstract class; phần giao tiếp với dịch vụ bên ngoài dùng interface.

## 6. Khi nào nên chọn cách nào?

**Chọn abstract class khi:**

- Các lớp con có quan hệ “is-a” rõ ràng, ví dụ các biến thể của một đối tượng miền nghiệp vụ.
- Chúng cần cùng một trạng thái instance được bảo vệ bởi constructor và những bất biến chung.
- Một thuật toán có khung bước ổn định, còn một vài bước có thể được lớp con triển khai khác nhau.
- Bạn có lý do vững chắc để duy trì một lớp nền duy nhất và chấp nhận sự ràng buộc do kế thừa gây ra.

**Chọn interface khi:**

- Cần định nghĩa hợp đồng cho nhiều implementation không cùng một họ lớp, ví dụ `Comparable`, `Runnable` hoặc một interface ứng dụng như `Channel`.
- Mã phía người dùng cần đổi implementation thông qua tham số, dependency injection hoặc kiểm thử với đối tượng giả.
- Một class phải kết hợp nhiều năng lực độc lập (`Printable`, `Exportable`, …).
- Hợp đồng nhỏ, tập trung vào hành vi; không có nhu cầu chia sẻ trạng thái instance.

**Cân nhắc composition (chứa và ủy quyền) khi:** chỉ muốn tái sử dụng cách xử lý, nhưng quan hệ “is-a” không đúng. Ví dụ `OrderService` *có một* `Channel` để gửi sự kiện, không nhất thiết *là một* `Channel`. Kế thừa lớp nền quá sớm khiến việc thay đổi phụ thuộc và phân chia trách nhiệm về sau khó hơn.

Một cách tổ chức hay gặp là **interface làm API cho người dùng, abstract class cung cấp implementation nền tùy chọn**. Ví dụ, có thể có `interface Storage` và `abstract class BaseStorage implements Storage`; một implementation được quyền kế thừa `BaseStorage` hoặc triển khai trực tiếp `Storage`. Lớp nền là tiện ích tùy chọn, không trở thành yêu cầu của hợp đồng.

## 7. Các bẫy hay gặp

**Nhầm `extends` với `implements`.** Class kế thừa class khác bằng `extends`; class thực hiện interface bằng `implements`. Interface mở rộng interface khác bằng `extends`.

**Nghĩ interface luôn hoàn toàn không có implementation.** Kể từ Java 8 có `default`/`static`, từ Java 9 có phương thức `private`. Tuy vậy interface vẫn không có state instance hoặc constructor.

**Nghĩ abstract class không có constructor hoặc mọi phương thức phải abstract.** Cả hai đều sai. Abstract class có thể chứa constructor, trường, phương thức cụ thể và thậm chí không có phương thức abstract nào.

**Thu hẹp visibility khi implement interface.** Nếu `interface Task { void run(); }` thì phương thức tương ứng ở lớp triển khai phải là `public void run()`. Viết `void run()` (package-private) sẽ lỗi biên dịch.

**Dùng kế thừa chỉ để lấy lại vài dòng code.** Nếu hai loại không thật sự thay thế nhau được, quan hệ cha–con có thể vi phạm nguyên tắc thay thế Liskov. Hãy cân nhắc tách logic chung thành một đối tượng cộng tác hoặc một utility phù hợp.

**Tạo interface quá lớn.** Interface buộc mọi implementation gánh các phương thức không liên quan làm hợp đồng khó dùng và khó kiểm thử. Ưu tiên hợp đồng nhỏ, có trách nhiệm rõ ràng (Interface Segregation).

**Hai interface có `default` trùng chữ ký.** Nếu một lớp triển khai cả hai interface cùng đưa ra phương thức mặc định xung đột, lớp đó phải tự override để giải quyết. Chẳng hạn:

```java
interface A {
    default String name() {
        return "A";
    }
}

interface B {
    default String name() {
        return "B";
    }
}

final class C implements A, B {
    @Override
    public String name() {
        return A.super.name() + B.super.name();
    }
}
```

Đoạn này là ví dụ khai báo kiểu, không phải chương trình có `main`. Kết quả gọi `new C().name()` là chuỗi `"AB"`. Không thể chỉ dựa vào thứ tự liệt kê `implements A, B` để Java tự chọn một phương thức mặc định.

**Đưa dữ liệu thay đổi vào field của interface.** Mọi field interface là `public static final`, không phải dữ liệu của mỗi instance. Dữ liệu thuộc riêng từng đối tượng nên đặt trong class hoặc record thích hợp.

## 8. Câu hỏi tự kiểm tra

1. Vì sao `Subscription` cần constructor dù không thể tạo trực tiếp đối tượng `Subscription`?
2. Khi thêm `EmailChannel implements Channel`, có cần sửa `Subscription.report()` không? Tại sao?
3. Nếu `FreeSubscription` không triển khai `monthlyPrice()`, lớp đó phải khai báo như thế nào?
4. Lúc nào việc thêm một abstract class cha chỉ để tái sử dụng code sẽ làm thiết kế kém linh hoạt?
5. Vì sao lớp `C implements A, B` ở ví dụ cuối bắt buộc phải override `name()`?

**Tự đối chiếu:** (1) để thiết lập/kiểm tra trạng thái chung khi lớp con được khởi tạo; (2) không, vì `report()` nhận `Channel`; (3) phải là `abstract` hoặc vẫn sẽ lỗi biên dịch nếu là lớp cụ thể; (4) khi các lớp không có quan hệ is-a/thay thế lẫn nhau hợp lệ; (5) vì hai default method cùng chữ ký gây xung đột.

Nhớ quy tắc chọn thiết kế: **abstract class để chia sẻ nền tảng của một họ lớp; interface để công bố năng lực và làm điểm kết nối giữa các implementation**. Có thể phối hợp cả hai khi mỗi cơ chế thực sự giải quyết một nhu cầu riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Quan hệ giữa kiểu, đối tượng, trạng thái và hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa kiểu, đối tượng, trạng thái và hành vi trong Java

## 1. Bức tranh tổng thể

Trong lập trình hướng đối tượng, bốn khái niệm **kiểu (type)**, **đối tượng (object)**, **trạng thái (state)** và **hành vi (behavior)** liên hệ với nhau nhưng không đồng nghĩa:

| Khái niệm | Câu hỏi trả lời | Ý nghĩa trong Java |
| --- | --- | --- |
| **Kiểu** | Một giá trị/tham chiếu được phép sử dụng như thế nào? | Lớp (`class`), lớp trừu tượng (`abstract class`), giao diện (`interface`), `record`, `enum`… xác định các thành viên có thể truy cập, ràng buộc kiểu và hợp đồng thao tác. |
| **Đối tượng** | Thực thể nào tồn tại khi chương trình chạy? | Một thể hiện được tạo từ lớp cụ thể; có danh tính riêng, thuộc tính của thể hiện và khả năng thực thi các phương thức. |
| **Trạng thái** | Thực thể đang ở tình trạng nào? | Các giá trị hiện tại của **trường thể hiện** (instance fields) và những dữ liệu mà đối tượng quản lý. |
| **Hành vi** | Thực thể có thể làm gì? | Các thao tác do phương thức thể hiện cung cấp; có thể đọc, kiểm tra hoặc thay đổi trạng thái và tương tác với đối tượng khác. |

Mối quan hệ quan trọng:

- **Kiểu mô tả khả năng và giới hạn sử dụng đối tượng**. Một đối tượng có thể được nhìn qua nhiều kiểu tương thích.
- **Đối tượng cụ thể sở hữu trạng thái thể hiện**. Mỗi đối tượng thường có các trường thể hiện riêng; hai tham chiếu trỏ vào *cùng* đối tượng thì nhìn thấy cùng trạng thái.
- **Hành vi là cách đối tượng bộc lộ hoặc thay đổi trạng thái**. Đóng gói giúp tránh sửa trạng thái tùy tiện.
- **Kiểu của biến tham chiếu được biết khi biên dịch; lớp thực tế của đối tượng được biết khi chạy**. Hai thông tin này có thể khác nhau.

Sơ đồ đọc theo chiều quan hệ:

```text
Kiểu / hợp đồng
   | cho biết được gọi thao tác nào
   v
Biến tham chiếu ----trỏ tới----> Đối tượng
                                  | giữ
                                  v
                              Trạng thái
                                  ^
                                  | được đọc / cập nhật
                                  |
                              Hành vi
```

Lưu ý: kiểu dữ liệu nguyên thủy như `int` hay `boolean` cũng là kiểu, nhưng giá trị nguyên thủy không phải đối tượng. Phần tiếp theo tập trung vào các **kiểu tham chiếu** và đối tượng.

## 2. Kiểu không phải đối tượng

Hãy phân biệt **khai báo biến**, **tạo đối tượng** và **gán tham chiếu**:

```java
Vehicle vehicle = new Bicycle("City");
```

- `Vehicle` là **kiểu khai báo của biến** `vehicle`; trình biên dịch dựa vào kiểu này để quyết định các thành viên có thể gọi trực tiếp.
- `new Bicycle("City")` tạo **một đối tượng** thuộc lớp cụ thể `Bicycle`, chạy constructor để thiết lập trạng thái ban đầu.
- `vehicle` lưu **tham chiếu** đến đối tượng. Phép gán tham chiếu không sao chép toàn bộ đối tượng.
- Nếu `Bicycle extends Vehicle`, một đối tượng `Bicycle` có thể được dùng ở vị trí yêu cầu `Vehicle`. Quan hệ này là đa hình theo kiểu con (subtype polymorphism).

Ví dụ: biến kiểu `Vehicle` có thể gọi phương thức được công bố bởi `Vehicle`, kể cả khi đối tượng đang trỏ đến thực sự là `Bicycle`. Muốn gọi thao tác chỉ có ở giao diện `Movable`, cần một tham chiếu có kiểu cho phép thao tác đó hoặc kiểm tra/ép kiểu hợp lệ.

**Đừng nhầm quan hệ “là một” với “có một”:** `Bicycle extends Vehicle` mô tả quan hệ kiểu con; còn `Car` có trường `Engine engine` là quan hệ hợp thành/liên kết đối tượng (has-a), không khiến `Car` trở thành kiểu con của `Engine`.

## 3. Ví dụ đầy đủ: một đối tượng, nhiều kiểu nhìn, một trạng thái

Tạo tệp `TypeStateDemo.java` với nội dung sau. Ví dụ dùng Java 17+, hoàn chỉnh và có thể biên dịch độc lập:

```java
public class TypeStateDemo {
    interface Movable {
        void move(int steps);
    }

    static abstract class Vehicle {
        private final String name;
        private int distance;

        protected Vehicle(String name) {
            this.name = name;
        }

        public final String name() {
            return name;
        }

        public final int distance() {
            return distance;
        }

        protected final void addDistance(int steps) {
            if (steps < 0) {
                throw new IllegalArgumentException("steps must be >= 0");
            }
            distance = Math.addExact(distance, steps);
        }

        public abstract String kind();
    }

    static final class Bicycle extends Vehicle implements Movable {
        Bicycle(String name) {
            super(name);
        }

        @Override
        public void move(int steps) {
            addDistance(steps);
        }

        @Override
        public String kind() {
            return "bicycle";
        }
    }

    public static void main(String[] args) {
        Bicycle bike = new Bicycle("City");
        Vehicle asVehicle = bike;
        Movable asMovable = bike;

        asMovable.move(3);
        System.out.println(asVehicle.name()
                + " / " + asVehicle.kind()
                + " / " + asVehicle.distance());

        asMovable.move(2);
        System.out.println(bike.distance());
        System.out.println(bike == asVehicle);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac TypeStateDemo.java
java TypeStateDemo
```

Kết quả chuẩn:

```text
City / bicycle / 3
5
true
```

Phân tích theo bốn khái niệm:

1. **Kiểu**: `Vehicle` là lớp trừu tượng có trạng thái chung và hợp đồng `kind()`; `Movable` là giao diện khai báo khả năng `move(int)`; `Bicycle` là lớp cụ thể kế thừa và hiện thực các hợp đồng ấy.
2. **Đối tượng**: chỉ có **một** lần `new Bicycle("City")`. Các biến `bike`, `asVehicle`, `asMovable` cùng trỏ tới **một đối tượng**.
3. **Trạng thái**: `name` được khởi tạo là `"City"` và không thể gán lại sau khởi tạo vì là trường `final`; `distance` bắt đầu bằng `0` và lần lượt trở thành `3`, `5`.
4. **Hành vi**: `move(3)` và `move(2)` làm thay đổi `distance`; `distance()` đọc trạng thái; `kind()` trả về thông tin loại xe.
5. **Đa hình**: gọi `asVehicle.kind()` thực thi triển khai `Bicycle.kind()` tại thời điểm chạy. Kết quả `bike == asVehicle` là `true` vì hai tham chiếu trỏ cùng đối tượng.

Đóng gói xuất hiện ở trường `private int distance`: mã bên ngoài không thể trực tiếp gán `distance = -100`. Mọi cập nhật qua `move()` phải tuân thủ kiểm tra số bước không âm. `Math.addExact` còn phát hiện tràn số nguyên và ném `ArithmeticException`, thay vì âm thầm quay vòng giá trị.

Lưu ý về khả năng gọi tại thời điểm biên dịch: `asVehicle.move(1)` **không biên dịch** vì `Vehicle` không khai báo `move`; trong khi `asMovable.move(1)` hợp lệ. Điều này không mâu thuẫn với việc cả hai trỏ cùng đối tượng.

## 4. Phân biệt ràng buộc lúc biên dịch và lúc chạy

Java kiểm tra thao tác hợp lệ theo **kiểu tĩnh** của biểu thức, rồi dùng cơ chế phân phối động cho các **phương thức thể hiện có thể ghi đè** (virtual instance methods).

| Tình huống | Quyết định bởi | Kết quả |
| --- | --- | --- |
| Có được viết `reference.someMethod()` không? | Kiểu tĩnh và mức truy cập khi biên dịch | Gọi phương thức không có trong hợp đồng kiểu sẽ lỗi biên dịch. |
| Triển khai nào của phương thức **đã ghi đè** được chạy? | Lớp thực tế của đối tượng khi chạy | Hỗ trợ đa hình động. |
| Chọn phiên bản **nạp chồng** (overload) nào? | Kiểu tham số/biểu thức tại thời điểm biên dịch | Không tự chọn lại overload vì lớp thực tế của đối tượng. |
| Truy cập trường trùng tên do **che khuất** (field hiding)? | Kiểu tĩnh của biểu thức | Trường không được phân phối động như phương thức ghi đè. |
| Gọi phương thức `static` hoặc `private`? | Quy tắc của lớp/khả năng truy cập, không phải ghi đè động | `static` thuộc lớp; `private` không bị ghi đè. |

Ví dụ minh họa **trường bị che khuất** và **phương thức bị ghi đè**. Lưu vào `DispatchDemo.java`:

```java
class Parent {
    String label = "Parent.field";

    String label() {
        return "Parent.method";
    }
}

class Child extends Parent {
    String label = "Child.field";

    @Override
    String label() {
        return "Child.method";
    }
}

public class DispatchDemo {
    public static void main(String[] args) {
        Parent value = new Child();
        System.out.println(value.label);
        System.out.println(value.label());
    }
}
```

```bash
javac DispatchDemo.java
java DispatchDemo
```

```text
Parent.field
Child.method
```

`value.label` đọc trường được xác định theo kiểu `Parent`, còn `value.label()` gọi phương thức ghi đè của `Child`. Trong thiết kế thực tế, nên ưu tiên `private` fields và truy cập qua phương thức thay vì khai báo hai trường trùng tên như ví dụ.

Với **overloading**, nếu có `print(Object)` và `print(String)`, biểu thức biến có kiểu khai báo `Object text = "hi";` sẽ chọn `print(Object)` khi viết `print(text)`; chỉ nhìn đối tượng là `String` lúc chạy không làm đổi overload được chọn.

## 5. Class, abstract class và interface diễn tả quan hệ này thế nào?

### Class cụ thể

- Có thể tạo đối tượng bằng `new` (nếu constructor và điều kiện truy cập cho phép).
- Khai báo trường thể hiện, constructor và các phương thức thao tác trên dữ liệu; có thể hiện thực giao diện và kế thừa một lớp cha.
- Chọn phạm vi truy cập phù hợp (`private`, `protected`, `public`) để bảo vệ bất biến của đối tượng.

### Abstract class

- **Không thể** `new` trực tiếp một lớp `abstract`, nhưng phần trạng thái và hành vi của nó vẫn trở thành phần của đối tượng thuộc lớp con cụ thể.
- Có thể có trường thể hiện, constructor, phương thức đã triển khai, phương thức `abstract` cần lớp con cụ thể hiện thực.
- Hữu ích khi các kiểu con có **trạng thái chung và logic dùng chung**, như `Vehicle.name`, `Vehicle.distance`, `Vehicle.addDistance()`.
- Java chỉ cho phép một lớp cha trực tiếp (kể cả nếu lớp cha trừu tượng), nên tránh dùng kế thừa chỉ để tái sử dụng vài dòng code.

### Interface

- Mô tả một **hợp đồng khả năng**: đối tượng hỗ trợ những thao tác gì, ví dụ `Movable.move(int)`.
- Không có **trường thể hiện** hay constructor để lưu trạng thái riêng. Các trường khai báo trong interface mặc định là `public static final` (hằng số thuộc interface), **không phải trạng thái của mỗi đối tượng**.
- Có thể có phương thức `abstract`, `default`, `static`; Java 9+ còn hỗ trợ phương thức `private` phục vụ triển khai trong interface. Phương thức `default` có thân hàm nhưng vẫn không tạo ra trường thể hiện cho interface.
- Một lớp có thể `implements` nhiều interface. Một interface có thể `extends` nhiều interface.
- Hữu ích khi nhiều lớp khác họ cùng sở hữu một khả năng chung, ví dụ `Bicycle`, `Robot` cùng `implements Movable` nhưng quản lý trạng thái bằng các cấu trúc khác nhau.

**Điểm then chốt:** interface quy định cách **quan sát/gọi hành vi**; lớp cụ thể quyết định **lưu trạng thái thế nào và thực hiện hành vi ra sao**. Tuy vậy, interface không nhất thiết hoàn toàn “không có triển khai”: `default` method có thể chia sẻ logic dựa trên các thao tác của hợp đồng.

## 6. Trạng thái, bất biến và vòng đời của đối tượng

### Trạng thái nằm ở đâu?

- **Instance field** thuộc từng đối tượng: hai lần `new Bicycle("City")` tạo hai đối tượng có trường `distance` độc lập.
- **Static field** thuộc về lớp, được dùng chung theo ngữ cảnh lớp nạp, nên không đại diện cho trạng thái riêng của từng đối tượng.
- **Biến cục bộ/tham số** tồn tại trong phạm vi lần gọi và không tự động trở thành trạng thái lâu dài của đối tượng.
- Trường tham chiếu cũng là một phần trạng thái: ví dụ `private Engine engine;` lưu tham chiếu tới đối tượng khác. Trạng thái có thể gián tiếp phụ thuộc vào đồ thị các đối tượng liên quan.

### Hành vi bảo vệ bất biến (invariant)

**Bất biến** là điều kiện phải luôn đúng ở các thời điểm quan sát hợp lệ của đối tượng. Ví dụ với đồng hồ quãng đường: `distance >= 0`. Thiết kế tốt duy trì bất biến bằng cách:

1. Thiết lập trạng thái khởi đầu hợp lệ trong constructor.
2. Giữ trường nhạy cảm ở mức `private`.
3. Kiểm tra đầu vào trước khi cập nhật và xử lý lỗi rõ ràng.
4. Tập trung thay đổi qua phương thức có tên phản ánh nghiệp vụ, như `move()` hay `deposit()`.
5. Không trả ra một đối tượng mutable nội bộ theo cách cho phép người gọi sửa trạng thái vượt qua kiểm tra (nếu cần, trả về bản sao hoặc kiểu chỉ đọc).

Một phương thức không nhất thiết thay đổi trạng thái: `distance()` chỉ đọc; `kind()` cung cấp thông tin. Ngược lại, phương thức có thể gây tác dụng bên ngoài (ghi tệp, gọi mạng) mà không sửa trường thể hiện; vì vậy **hành vi rộng hơn việc thay đổi state**.

`final` cần được hiểu đúng: trường nguyên thủy `final int x` không thể gán lại; nhưng `final List<String> items` chỉ cố định **tham chiếu**, đối tượng danh sách vẫn có thể thay đổi nếu nó mutable.

### Hai tham chiếu và hai đối tượng

```java
Bicycle first = new Bicycle("A");
Bicycle alias = first;
Bicycle second = new Bicycle("A");
```

Đoạn trên sử dụng lớp `Bicycle` đã định nghĩa trong ví dụ trước, **không phải chương trình độc lập**.

- `first == alias` là `true`: cùng danh tính đối tượng; cập nhật qua một biến được quan sát qua biến còn lại.
- `first == second` là `false`: hai lần `new` tạo hai đối tượng riêng dù cùng tên và cùng quãng đường lúc đầu.
- `==` giữa hai biến tham chiếu so sánh **danh tính tham chiếu**; `equals()` so sánh theo hợp đồng của lớp. Nếu lớp không ghi đè `equals()`, hành vi mặc định từ `Object` vẫn so sánh danh tính.
- Gán `alias = second` chỉ đổi đối tượng mà biến `alias` trỏ tới; không tự thay đổi `first` hoặc `second`.

## 7. Từ mô hình bài toán đến thiết kế Java

Ví dụ hệ thống đặt hàng có thể phân chia trách nhiệm như sau:

| Góc nhìn | Ví dụ | Quyết định thiết kế |
| --- | --- | --- |
| Kiểu nghiệp vụ | `Order`, `PaymentMethod` | Xác định các thao tác hợp lệ và các hợp đồng rõ ràng. |
| Đối tượng thực tế | Một đơn hàng cụ thể, một đối tượng cổng thanh toán cụ thể | Mỗi thực thể có vòng đời và danh tính phù hợp. |
| Trạng thái | Mã đơn, các dòng hàng, trạng thái `CREATED/PAID/CANCELLED` | Đóng gói dữ liệu, hạn chế trạng thái không hợp lệ. |
| Hành vi | `addItem()`, `pay()`, `cancel()` | Kiểm tra chuyển trạng thái hợp lệ và phối hợp đối tượng khác. |

Chẳng hạn, một đơn hàng đã hủy không được thanh toán là một **quy tắc bất biến nghiệp vụ**. Nó nên được thực thi trong hành vi như `pay()`, thay vì cho mã bên ngoài gán trực tiếp `order.status = PAID`.

Khi quyết định dùng **abstract class** hay **interface**, hãy hỏi:

- Các kiểu có chia sẻ *state thực sự* và cách duy trì bất biến giống nhau không? Nếu có, một lớp cha trừu tượng có thể phù hợp.
- Các kiểu chỉ chia sẻ *khả năng* mà cách lưu dữ liệu khác nhau? Interface thường diễn đạt hợp đồng tốt hơn.
- Có thể dùng **composition** (chứa/ủy quyền một đối tượng) thay kế thừa để tránh phụ thuộc chặt vào cấu trúc lớp cha không?
- Đối tượng có cần mutable không? Nếu không, xem xét đối tượng bất biến: thiết lập đầy đủ state trong constructor, không có phương thức làm đổi state; `record` thuận tiện cho mô hình dữ liệu đơn giản nhưng **không tự làm mọi thành phần tham chiếu trở nên bất biến sâu**.

## 8. Các ngộ nhận và lỗi thường gặp

1. **“Biến có kiểu gì thì đối tượng bắt buộc thuộc đúng lớp đó.”** Sai: biến `Vehicle` có thể trỏ tới đối tượng `Bicycle`; điều kiện là tương thích kiểu.
2. **“Gán biến tham chiếu tạo bản sao đối tượng.”** Sai: gán thông thường chỉ sao chép giá trị tham chiếu; muốn sao chép trạng thái phải có chiến lược sao chép cụ thể.
3. **“Interface không liên quan đến trạng thái.”** Chưa đủ: interface không chứa trường thể hiện, nhưng hành vi do interface quy định hoàn toàn có thể đọc/sửa trạng thái của đối tượng hiện thực nó.
4. **“Abstract class không thể có constructor hoặc field.”** Sai: lớp trừu tượng có thể khởi tạo phần trạng thái được thừa hưởng; chỉ không thể tạo trực tiếp thể hiện của chính lớp trừu tượng.
5. **“Mọi thành viên đều đa hình lúc chạy.”** Sai: trường bị che khuất, phương thức `static`, `private` và nạp chồng có quy tắc khác ghi đè phương thức thể hiện.
6. **“Getter/setter cho mọi field là đã đóng gói tốt.”** Không nhất thiết: setter tự do có thể phá bất biến. Hành vi nghiệp vụ như `withdraw(amount)` diễn đạt và kiểm soát quy tắc rõ hơn `setBalance(value)`.
7. **“`final` reference nghĩa là object bất biến.”** Sai: tham chiếu không thể gán lại, nhưng trạng thái đối tượng được trỏ tới có thể thay đổi.
8. **“Object mutable tự động an toàn đa luồng.”** Sai: dùng chung một đối tượng mutable giữa các thread cần thiết kế đồng bộ, bất biến hoặc cơ chế thread-safe thích hợp. Ví dụ `Bicycle` phía trên **không** cam kết an toàn đa luồng.

## 9. Tự kiểm tra

**Câu 1.** Với `Vehicle v = new Bicycle("X");`, kiểu tĩnh và lớp runtime là gì?

**Đáp án:** kiểu tĩnh là `Vehicle`, lớp runtime là `Bicycle`. Chỉ gọi trực tiếp các thành viên hiển thị qua `Vehicle`, nhưng phương thức thể hiện được `Bicycle` ghi đè sẽ chạy phiên bản của `Bicycle`.

**Câu 2.** Nếu hai biến cùng trỏ một `Bicycle` và gọi `move(4)` qua biến thứ nhất, biến thứ hai đọc `distance()` thấy gì?

**Đáp án:** trạng thái đã tăng thêm 4 so với trước (nếu lời gọi thành công), vì chỉ có một đối tượng được chia sẻ.

**Câu 3.** Vì sao `Movable` không trực tiếp lưu `distance` riêng cho mỗi đối tượng?

**Đáp án:** interface không có instance fields; lớp hiện thực như `Bicycle` hoặc lớp cha `Vehicle` phải lưu trạng thái.

**Câu 4.** Nếu muốn thêm `Robot` có thể `move()` nhưng không phải phương tiện giao thông, nên dùng gì?

**Đáp án:** cho `Robot implements Movable` và quản lý trạng thái riêng. Không nên ép `Robot extends Vehicle` chỉ để lấy phương thức di chuyển.

**Ghi nhớ:** **kiểu** là hợp đồng sử dụng, **đối tượng** là thực thể mang danh tính, **trạng thái** là dữ liệu hiện tại của thực thể, và **hành vi** là các thao tác tác động lên hoặc quan sát thực thể đó. Tách bốn vai trò này giúp hiểu đa hình, đóng gói và lựa chọn giữa `class`, `abstract class`, `interface` khi thiết kế Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Một ví dụ xuyên suốt: nhiều cách xử lý cùng một yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Một ví dụ xuyên suốt: nhiều cách xử lý cùng một yêu cầu

Trong chủ đề **abstract class, interface và đa hình của Java**, cách dễ nhất để thấy sự khác biệt giữa các công cụ là giữ nguyên một yêu cầu nghiệp vụ rồi thay đổi cách xử lý. Ví dụ dưới đây dùng yêu cầu: **“Thông báo cho khách hàng KH-42 rằng đơn hàng đã được xác nhận”**. Cùng một thông điệp có thể được xử lý bằng email, SMS hoặc thông báo trong ứng dụng.

Mục tiêu của bài:

- Nhận ra phần **ổn định** của yêu cầu (mã khách hàng, nội dung, thao tác gửi và kết quả) và phần **thay đổi** (cách gửi theo từng kênh).
- Hiểu cách `interface` biểu diễn hợp đồng chung, `abstract class` tái sử dụng quy trình chung và **đa hình** chọn triển khai lúc chạy.
- Biết khi nào nên dùng lớp trừu tượng, khi nào nên dùng interface, và vì sao nên kết hợp chúng bằng thành phần gọi chung.
- Đọc, biên dịch và giải thích một chương trình Java 17 hoàn chỉnh; thấy rõ giới hạn giữa ví dụ mô phỏng và hệ thống gửi thông báo thực.

## 1. Xác định một yêu cầu, nhiều cách đáp ứng

Hãy hình dung dịch vụ nhận một đối tượng yêu cầu:

| Thông tin | Giá trị ví dụ | Có thay đổi theo kênh không? |
| --- | --- | --- |
| Mã khách hàng | `KH-42` | Không |
| Nội dung | `Đơn hàng đã được xác nhận` | Không |
| Hành động | Gửi thông báo | Không |
| Cách thực hiện | Email, SMS, thông báo trong ứng dụng | **Có** |
| Kết quả | Kênh xử lý và trạng thái | Cùng một cấu trúc, giá trị kênh khác nhau |

Nếu viết toàn bộ logic trong một khối `if/else` hoặc `switch` phân nhánh theo tên kênh, mỗi kênh mới lại buộc ta mở khối phân nhánh. Những trường hợp khác nhau còn dễ chia sẻ sai dữ liệu, trùng mã kiểm tra và khiến kiểm thử khó tách biệt. Với vài nhánh ngắn, lựa chọn đó vẫn có thể đủ đơn giản; vấn đề xuất hiện khi số cách xử lý và quy tắc tăng lên.

Ta tách ba vai trò:

1. **Yêu cầu** (`NotificationRequest`): dữ liệu bất biến mà mọi kênh nhận.
2. **Hợp đồng** (`NotificationChannel`): mọi kênh đều có `name()` và `send(request)`.
3. **Bộ điều phối** (`NotificationService`): sử dụng danh sách các kênh qua cùng kiểu `NotificationChannel`, không cần biết chi tiết email, SMS hoặc thông báo trong ứng dụng.

## 2. Interface: thống nhất cách gọi, cho phép triển khai khác nhau

`interface NotificationChannel` chỉ công bố thao tác mà bên gọi được phép dựa vào:

```java
interface NotificationChannel {
    String name();
    SendResult send(NotificationRequest request);
}
```

`EmailChannel`, `SmsChannel` và `InAppChannel` đều có thể được xem là `NotificationChannel`. Khi `NotificationService` gọi `channel.send(request)`, Java chọn phiên bản phương thức của **đối tượng thực tế** tại thời điểm chạy (dynamic dispatch). Kiểu biến là interface; hành vi thực thi đến từ lớp triển khai.

Hợp đồng ở đây cần được hiểu cả về **ý nghĩa**, không chỉ chữ ký: nhận một yêu cầu hợp lệ, thực hiện thao tác của kênh và trả `SendResult`; nếu xảy ra lỗi thì phải có chính sách báo lỗi rõ ràng. Việc khai báo interface **không tự động** bảo đảm mọi lớp có cùng chất lượng xử lý, validation hay tính an toàn khi gửi nhiều lần.

Một interface còn có thể khai báo `default`, `static` và phương thức `private` hỗ trợ triển khai. Tuy nhiên interface không có constructor và không giữ trạng thái instance như một lớp cơ sở. Các trường khai báo trên interface là hằng `public static final`; tránh đặt dữ liệu thay đổi của từng kênh ở đây.

## 3. Abstract class: chia sẻ quy trình chung khi các lớp thực sự có quan hệ

Email và SMS trong ví dụ cùng có các bước: kiểm tra đối tượng yêu cầu, ghi nhận bước kiểm tra, thực hiện gửi rồi tạo kết quả. Ta có thể gom phần **quy trình giống nhau** vào lớp trừu tượng:

```java
abstract class AbstractNotificationChannel implements NotificationChannel {
    @Override
    public final SendResult send(NotificationRequest request) {
        // Kiểm tra chung -> thực hiện riêng -> tạo kết quả chung.
        ...
    }

    protected abstract void deliver(NotificationRequest request);
}
```

*Đây là đoạn minh họa cấu trúc, chưa phải một chương trình độc lập; mã chạy đầy đủ nằm ở mục 4.*

- `abstract class` **không thể khởi tạo trực tiếp** bằng `new AbstractNotificationChannel()`, nhưng có thể có constructor, thuộc tính instance, phương thức cụ thể và phương thức abstract.
- `send()` đã được triển khai và đánh dấu `final`: lớp con không thể bỏ qua thứ tự xử lý đã ấn định.
- `deliver()` là “điểm mở rộng” do lớp con hiện thực. Đây là dạng đơn giản của **Template Method**: lớp cha giữ khung thuật toán, lớp con cung cấp bước thay đổi.
- Email và SMS kế thừa `AbstractNotificationChannel`; chúng cũng thỏa interface `NotificationChannel` vì lớp cha đã `implements` interface ấy.
- Một lớp Java chỉ `extends` **một** lớp trực tiếp, nhưng có thể `implements` nhiều interface. Do đó không ép mọi kênh vào cùng lớp cha nếu chúng không chia sẻ được một quy trình có ý nghĩa.

`InAppChannel` sẽ **implements interface trực tiếp** để chứng minh đây là hợp đồng độc lập với hệ kế thừa. Nó không cần thực hiện bước ghi nhận kiểm tra mà lớp cha đặt ra.

## 4. Chương trình Java 17 hoàn chỉnh

Lưu mã sau vào **`Main.java`**. Toàn bộ kiểu phụ đều là thành phần lồng trong `Main`, nên có thể biên dịch bằng một tệp. Đây là **mô phỏng gửi thông báo trên console**: không gọi SMTP, nhà cung cấp SMS hay API đẩy thông báo; `SENT` là kết quả mô phỏng.

```java
import java.util.List;
import java.util.Objects;

public class Main {
    record NotificationRequest(String customerId, String message) {
        NotificationRequest {
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(message, "message");
            if (customerId.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("Thông tin không được để trống");
            }
        }
    }

    record SendResult(String channel, String status) {}

    interface NotificationChannel {
        String name();
        SendResult send(NotificationRequest request);
    }

    static abstract class AbstractNotificationChannel
            implements NotificationChannel {
        @Override
        public final SendResult send(NotificationRequest request) {
            Objects.requireNonNull(request, "request");
            System.out.println("Kiểm tra yêu cầu: " + request.customerId());
            deliver(request);
            return new SendResult(name(), "SENT");
        }

        protected abstract void deliver(NotificationRequest request);
    }

    static final class EmailChannel extends AbstractNotificationChannel {
        @Override
        public String name() {
            return "EMAIL";
        }

        @Override
        protected void deliver(NotificationRequest request) {
            System.out.println("EMAIL -> " + request.customerId()
                    + ": " + request.message());
        }
    }

    static final class SmsChannel extends AbstractNotificationChannel {
        @Override
        public String name() {
            return "SMS";
        }

        @Override
        protected void deliver(NotificationRequest request) {
            System.out.println("SMS -> " + request.customerId()
                    + ": " + request.message());
        }
    }

    static final class InAppChannel implements NotificationChannel {
        @Override
        public String name() {
            return "IN_APP";
        }

        @Override
        public SendResult send(NotificationRequest request) {
            Objects.requireNonNull(request, "request");
            System.out.println("IN_APP -> " + request.customerId()
                    + ": " + request.message());
            return new SendResult(name(), "SENT");
        }
    }

    static final class NotificationService {
        private final List<NotificationChannel> channels;

        NotificationService(List<NotificationChannel> channels) {
            this.channels = List.copyOf(channels);
        }

        void sendAll(NotificationRequest request) {
            for (NotificationChannel channel : channels) {
                SendResult result = channel.send(request);
                System.out.println("Kết quả " + result.channel()
                        + ": " + result.status());
            }
        }
    }

    public static void main(String[] args) {
        NotificationRequest request = new NotificationRequest(
                "KH-42", "Đơn hàng đã được xác nhận");

        NotificationService service = new NotificationService(List.of(
                new EmailChannel(),
                new SmsChannel(),
                new InAppChannel()));

        service.sendAll(request);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac Main.java
java Main
```

Kết quả chuẩn (UTF-8 khi terminal hỗ trợ tiếng Việt):

```text
Kiểm tra yêu cầu: KH-42
EMAIL -> KH-42: Đơn hàng đã được xác nhận
Kết quả EMAIL: SENT
Kiểm tra yêu cầu: KH-42
SMS -> KH-42: Đơn hàng đã được xác nhận
Kết quả SMS: SENT
IN_APP -> KH-42: Đơn hàng đã được xác nhận
Kết quả IN_APP: SENT
```

**Theo dõi luồng xử lý:** `main()` tạo **một** `NotificationRequest`, sau đó đưa đúng đối tượng đó lần lượt vào ba triển khai. `NotificationService` chỉ thấy ba phần tử kiểu `NotificationChannel`. Hai phần tử đầu chạy `send()` dùng chung của `AbstractNotificationChannel`, rồi lần lượt gọi `EmailChannel.deliver()` và `SmsChannel.deliver()`; phần tử cuối chạy `InAppChannel.send()` trực tiếp. Vì thế hai kênh đầu in thêm dòng “Kiểm tra yêu cầu”, còn kênh thứ ba thì không.

`record` cung cấp đối tượng mang dữ liệu với các accessor `customerId()`, `message()`. Constructor rút gọn kiểm tra `null` và chuỗi chỉ gồm khoảng trắng, nên ví dụ có điểm kiểm tra tính hợp lệ thống nhất khi tạo yêu cầu. `List.copyOf(channels)` tạo danh sách không thể thêm/xóa phần tử qua biến `channels`; nó **không** biến các đối tượng kênh thành bất biến.

## 5. Điều gì thực sự mang lại đa hình?

Quan sát dòng gọi trong vòng lặp:

```java
for (NotificationChannel channel : channels) {
    SendResult result = channel.send(request);
    // ...
}
```

Lệnh gọi giống nhau nhưng hành vi được chọn theo đối tượng:

| Đối tượng runtime | Kiểu tham chiếu trong vòng lặp | Phần triển khai `send()` chạy | Bước riêng |
| --- | --- | --- | --- |
| `EmailChannel` | `NotificationChannel` | `AbstractNotificationChannel.send()` | `EmailChannel.deliver()` |
| `SmsChannel` | `NotificationChannel` | `AbstractNotificationChannel.send()` | `SmsChannel.deliver()` |
| `InAppChannel` | `NotificationChannel` | `InAppChannel.send()` | Không sử dụng lớp cha trừu tượng |

Hợp đồng interface là **đa hình theo kiểu**; việc override phương thức trong hệ kế thừa là một cách cung cấp **đa hình hành vi**. Một tham chiếu có kiểu interface chỉ gọi trực tiếp được những phương thức mà interface công bố, dù lớp cụ thể có thêm phương thức riêng.

Trong ví dụ, chương trình có *cả* hai ý tưởng: `NotificationService` phụ thuộc vào **interface**, còn một số lớp triển khai chọn tái sử dụng **abstract class**. Đây là lựa chọn độc lập và có thể phối hợp.

## 6. Mở rộng một cách xử lý mới mà không sửa bộ điều phối

Giả sử cần thêm kênh `WebhookChannel`. Ta có thể `implements NotificationChannel` trực tiếp, tương tự `InAppChannel`, rồi thêm một đối tượng vào danh sách tại nơi cấu hình:

```java
// Ví dụ về điểm cấu hình; đoạn này không phải tệp Java độc lập.
NotificationService service = new NotificationService(List.of(
        new EmailChannel(),
        new SmsChannel(),
        new InAppChannel(),
        new WebhookChannel()));
```

`WebhookChannel` phải được định nghĩa để ví dụ mở rộng ở trên biên dịch. Điểm cần thấy là **mã của `NotificationService.sendAll()` không cần sửa**. Nơi lắp ghép đối tượng (ở đây là `main()`) vẫn phải được cập nhật để biết kênh mới. Trong ứng dụng lớn, có thể cấu hình danh sách thông qua dependency injection; interface tự nó không tự phát hiện lớp triển khai.

Khi nào chọn `extends AbstractNotificationChannel`? Khi kênh mới cần chính quy trình kiểm tra → giao việc → tạo kết quả, với thứ tự không được thay đổi. Khi nào `implements NotificationChannel` trực tiếp? Khi kênh dùng quy trình khác nhưng vẫn đáp ứng hợp đồng chung. Nếu hai lớp chỉ trùng vài dòng nhỏ, một **thành phần dùng chung được truyền vào** (composition) đôi khi phù hợp hơn một lớp cha.

## 7. Giới hạn của ví dụ và những quyết định khi áp dụng thực tế

| Vấn đề | Ví dụ hiện tại | Khi triển khai hệ thống thật |
| --- | --- | --- |
| Gửi dữ liệu | `System.out.println()` | Adapter tích hợp SMTP/SMS/push; credentials và cấu hình tách riêng |
| Trạng thái | Trả `SENT` sau khi in | Phân biệt chấp nhận xử lý, gửi đến provider, giao thành công và thất bại |
| Kiểm tra yêu cầu | `record` không cho null/rỗng | Kiểm tra thêm định dạng, quyền truy cập, độ dài, định tuyến đích |
| Khi một kênh lỗi | Ngoại lệ dừng `sendAll()` | Quyết định fail-fast, tiếp tục kênh khác, retry, timeout và idempotency |
| Gọi tuần tự | Thực hiện theo thứ tự danh sách | Cân nhắc bất đồng bộ, đồng thời và giới hạn tài nguyên |
| Lưu dấu vết | In console | Log có ngữ cảnh, metrics, tracing; không ghi lộ dữ liệu nhạy cảm |

Không nên mặc định rằng “gọi `send()` trả `SENT`” đồng nghĩa khách đã nhận được thông báo. Trong hệ thống phân tán, giao thành công có thể chỉ được xác nhận bằng callback hoặc sự kiện từ provider. Hãy định nghĩa nghĩa của `SendResult` phù hợp với hệ thống thực và chính sách lỗi trước khi đổi mã mô phỏng thành mã sản xuất.

## 8. Những nhầm lẫn thường gặp

- **“Có interface thì khỏi cần lớp trừu tượng.”** Interface thống nhất hợp đồng; abstract class có thể lưu state và quy trình dùng chung. Có thể dùng cả hai như chương trình trên.
- **“Abstract class bắt buộc chỉ chứa abstract methods.”** Sai: nó có thể chứa constructor, field, phương thức cụ thể, `final` và phương thức abstract; thậm chí một lớp `abstract` có thể không khai báo abstract method nào.
- **“Mọi subclass đều phải override `send()`.”** Sai: hai lớp kế thừa `final send()` từ lớp cha và chỉ hiện thực `deliver()`. Override phương thức `final` là lỗi biên dịch.
- **“Thêm interface là tự động có tính mở rộng.”** Cần giữ hợp đồng ổn định, chọn nơi khởi tạo/đăng ký implementation và tránh đưa `instanceof` hoặc `switch` trên từng lớp cụ thể trở lại `NotificationService`.
- **“Kế thừa chỉ để tránh trùng mã.”** Nếu các lớp không chia sẻ cùng bản chất/quy trình, kế thừa có thể tạo ràng buộc sai. Ưu tiên composition khi chỉ muốn dùng chung một phụ thuộc hoặc chính sách.
- **“Đa hình là overload.”** Overload chọn chữ ký phù hợp theo tham số ở thời điểm biên dịch; ví dụ này dùng override/triển khai interface và dynamic dispatch lúc chạy.
- **“Kiểm tra một lần thì hệ thống chắc chắn an toàn.”** Validation khi tạo `NotificationRequest` bảo vệ dữ liệu của record; vẫn cần xử lý request `null`, lỗi provider và các điều kiện nghiệp vụ riêng.

## 9. Tự kiểm tra hiểu bài

1. Vì sao `NotificationService` vẫn biên dịch nếu một kênh mới được viết bằng cách `implements NotificationChannel` mà không `extends AbstractNotificationChannel`?
2. Nếu xóa từ khóa `final` khỏi `AbstractNotificationChannel.send()`, lớp con có thể làm gì khiến quy trình kiểm tra chung không còn được thực thi?
3. Vì sao chỉ email và SMS in dòng “Kiểm tra yêu cầu”, dù cả ba cùng nhận một `NotificationRequest`?
4. Thử thêm `ConsoleChannel` implements `NotificationChannel` và đưa vào danh sách. Kiểm tra rằng `NotificationService.sendAll()` không thay đổi.
5. Thử làm `EmailChannel.deliver()` ném `IllegalStateException`. Dự đoán các kênh còn lại có chạy hay không trong bản hiện tại; sau đó thiết kế chính sách xử lý lỗi phù hợp với yêu cầu nghiệp vụ.

**Kết luận học tập:** Khi **một yêu cầu có nhiều cách xử lý**, mô hình hữu ích là một dữ liệu đầu vào chung + một interface thể hiện khả năng + các lớp triển khai độc lập. Chỉ thêm abstract class ở nơi thật sự có quy trình chung cần tái sử dụng. Đa hình cho phép bên điều phối gửi cùng một lệnh qua hợp đồng đó, còn quyết định cụ thể được thực hiện bởi đối tượng tại runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Kiến thức tiên quyết và trình tự học đề xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức tiên quyết và trình tự học đề xuất — Abstract Class & Interface (Java)

## 1. Mục tiêu học tập

Đây là lộ trình để chuẩn bị và học chủ đề `abstract class` và `interface` một cách có thứ tự. Trọng tâm là **học gì trước, học gì sau, vì sao cần kiến thức đó và khi nào đủ vững để chuyển bước**. Người mới chưa cần hiểu sẵn hai khái niệm này.

Sau lộ trình, bạn cần tự giải thích được mối liên hệ giữa kế thừa, ghi đè, đa hình, lớp trừu tượng và giao diện; viết được ví dụ hoạt động trên JDK 17+; và có căn cứ khi chọn dùng một lớp cha, một interface hay kết hợp chúng.

## 2. Kiến thức tiên quyết

| Chủ điểm | Cần biết trước | Cách kiểm tra |
| --- | --- | --- |
| Cú pháp Java và công cụ | Khai báo biến, kiểu dữ liệu, `if`, phương thức, tên tệp trùng tên `public class`, dùng `javac`/`java`. | Tạo, biên dịch, chạy một chương trình `Hello`. |
| Class và object | `class`, `new`, field, method, constructor, `this`; phân biệt định nghĩa kiểu và đối tượng ở runtime. | Viết `Student` có tên, constructor và phương thức đọc tên. |
| Đóng gói | `private`, `protected`, `public`, package-private; trách nhiệm quản lý trạng thái. | Ẩn một field với `private`, cung cấp phương thức truy cập hợp lý. |
| Kế thừa lớp | `extends`, quan hệ “là một” (is-a), `super(...)`, phần thành viên được kế thừa. | Viết `Dog extends Animal`, gọi constructor của `Animal`. |
| Ghi đè phương thức | `@Override`, chữ ký phương thức; phân biệt overriding với overloading. | Ghi đè `sound()` ở `Dog`; giải thích vì sao tạo phương thức `sound(int)` là nạp chồng. |
| Đa hình | `Animal a = new Dog();`; kiểu tham chiếu chi phối API nhìn thấy lúc biên dịch, đối tượng thực quyết định phương thức instance ghi đè được chạy. | Dự đoán kết quả `a.sound()` và chạy để xác nhận. |

**Thứ tự ôn tập tối thiểu:** cú pháp Java → class/object → constructor và đóng gói → kế thừa → override → đa hình. Nếu không giải thích được `Animal a = new Dog();`, nên củng cố phần đó trước khi học abstract class hoặc interface.

**Chưa bắt buộc:** generics, collection nâng cao, stream, reflection, Spring, dependency injection, design pattern. Những kiến thức này hữu ích về sau nhưng không phải điều kiện khởi đầu.

### Ba ý nền tảng cần phân biệt

- **Lớp/kiểu và đối tượng:** `Animal a` là biến tham chiếu kiểu `Animal`; `new Dog()` tạo đối tượng kiểu `Dog`. Một đối tượng có thể được tham chiếu qua các kiểu cha/giao diện mà nó đáp ứng.
- **Override và overload:** override cung cấp triển khai mới cho phương thức instance được kế thừa với chữ ký tương ứng; overload là những phương thức cùng tên nhưng danh sách tham số khác nhau.
- **Trừu tượng hóa và đa hình:** trừu tượng hóa xác định những khả năng cần có, còn đa hình giúp cùng một lời gọi có cách thực hiện khác nhau tùy đối tượng cụ thể. Cần hiểu cả hai, thay vì chỉ thuộc cú pháp `abstract` và `interface`.

## 3. Trình tự học đề xuất

| Giai đoạn | Nội dung | Dấu hiệu có thể chuyển bước |
| --- | --- | --- |
| 0. Công cụ và OOP cơ bản | Chạy Java 17+, class, object, constructor, field, method, đóng gói. | Tự biên dịch một ví dụ nhỏ không cần sao chép lệnh lỗi. |
| 1. Kế thừa và đa hình | `extends`, `super`, `@Override`, kiểu tham chiếu cha, dispatch ở runtime. | Giải thích chính xác phương thức nào được gọi qua `Animal a = new Dog();`. |
| 2. Nhu cầu về hợp đồng | Xác định hành vi chung mà mỗi kiểu cụ thể phải thực hiện khác nhau. | Mô tả được “mọi phương tiện di chuyển, nhưng cách di chuyển tùy phương tiện”. |
| 3. Abstract class | `abstract class`, phương thức abstract, constructor, trạng thái và phương thức có sẵn. | Tạo lớp con cụ thể hiện thực đủ các phương thức abstract. |
| 4. Interface | `interface`, `implements`, một lớp hiện thực nhiều interface, biến và tham số kiểu interface. | Cho hai lớp độc lập cùng đáp ứng một hợp đồng. |
| 5. Kết hợp và lựa chọn | Một lớp `extends` một class và `implements` nhiều interface; so sánh với composition. | Giải thích được vì sao chọn mỗi cơ chế trong một thiết kế cụ thể. |
| 6. Tính năng Java hiện đại | Interface `default`, `static`, `private`; xung đột default; functional interface khi học lambda. | Đọc được code hiện đại mà không nhầm hợp đồng với trạng thái instance. |
| 7. Thực hành tích hợp | Thiết kế ví dụ từ đầu, dự đoán output, sửa lỗi biên dịch, đánh giá thiết kế. | Viết/chạy/giải thích được lời giải của chính mình. |

Đây là **trình tự phụ thuộc kiến thức**, không cần chia thời lượng bằng nhau. Có thể học vài bước trong một buổi nếu đã chắc OOP; khi chưa đạt dấu hiệu ở cột cuối, nên luyện thêm trước khi sang bước tiếp theo.

### Giai đoạn 1–2: Tại sao học đa hình trước?

Bắt đầu bằng hai lớp thông thường `Animal` và `Dog`; cho `Dog` ghi đè `sound()`. Với `Animal a = new Dog();`, lời gọi `a.sound()` sử dụng triển khai ghi đè của `Dog`. Trình biên dịch kiểm tra việc gọi dựa trên kiểu `Animal`; khi chạy, JVM chọn phiên bản phương thức instance phù hợp với đối tượng thực.

Từ đó đặt câu hỏi: nếu mọi phương tiện đều phải có `move()` nhưng không có cách di chuyển chung hợp lý, làm sao khai báo yêu cầu đó mà không viết một thân phương thức giả? Đây là lý do học phương thức abstract.

### Giai đoạn 3: Học abstract class

Một **lớp trừu tượng** phù hợp khi các loại đối tượng thuộc cùng họ có khung chung (trạng thái, constructor, phương thức dùng chung), nhưng vẫn có phần hành vi phải để lớp con quyết định.

Nắm chắc các điểm sau:

1. `abstract class Vehicle` có thể chứa field, constructor, phương thức có thân và phương thức `abstract` không có thân.
2. Không thể khởi tạo trực tiếp bằng `new Vehicle(...)`, nhưng có thể khai báo biến kiểu `Vehicle` tham chiếu một đối tượng lớp con.
3. Constructor lớp trừu tượng được gọi trong quá trình xây dựng đối tượng lớp con; `super(...)` cho phép khởi tạo phần chung.
4. Lớp con **không abstract** phải hiện thực các phương thức abstract được thừa kế mà còn thiếu.
5. `abstract` và `final` không dùng đồng thời cho một class: class final không cho phép kế thừa.

**Mốc đạt:** tự viết `abstract class Shape` với phương thức `abstract double area()` rồi tạo `Rectangle` và `Circle` triển khai diện tích. Giải thích được vì sao `Shape s = new Rectangle(...)` hợp lệ dù `new Shape()` không hợp lệ.

### Giai đoạn 4: Học interface

Một **interface** biểu diễn hợp đồng khả năng của nhiều loại đối tượng. Ví dụ, phương tiện, kiện hàng hay thiết bị đều có thể có mã định danh mà không cần chung một lớp cha nghiệp vụ. `Identifiable` với phương thức `id()` mô tả khả năng đó.

Các quy tắc cần nhớ:

- Class dùng `implements` để hiện thực interface. Một class Java chỉ trực tiếp `extends` tối đa một class, nhưng có thể `implements` nhiều interface. Interface có thể `extends` nhiều interface.
- Phương thức abstract thông thường của interface là `public abstract` ngầm định; lớp hiện thực không được giảm mức truy cập của nó.
- Field khai báo trong interface là `public static final` ngầm định (hằng số), không phải field riêng có thể thay đổi của từng instance.
- Interface không có constructor như class để trực tiếp tạo đối tượng; biến kiểu interface tham chiếu đối tượng của lớp hiện thực.
- Interface có thể có phương thức `default`, `static` (Java 8+) và `private` (Java 9+). Học các dạng này **sau** khi hiểu `implements` và đa hình cơ bản.

**Mốc đạt:** viết `interface Printable { void print(); }`, cho `Invoice` và `Report` cùng hiện thực, rồi truyền mỗi đối tượng vào phương thức nhận tham số `Printable`. Người gọi chỉ cần biết hợp đồng `print()`, không cần biết lớp cụ thể.

### Giai đoạn 5: Kết hợp trong ví dụ Java 17 chạy được

Tạo tệp **`LearningPathDemo.java`** và lưu UTF-8:

```java
interface Identifiable {
    String id();

    default String label() {
        return "ID-" + id();
    }
}

abstract class Vehicle {
    private final String name;

    protected Vehicle(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract String move();
}

final class Bicycle extends Vehicle implements Identifiable {
    private final String id;

    Bicycle(String name, String id) {
        super(name);
        this.id = id;
    }

    @Override
    public String move() {
        return name() + " đang chạy bằng sức người";
    }

    @Override
    public String id() {
        return id;
    }
}

public class LearningPathDemo {
    public static void main(String[] args) {
        Bicycle bicycle = new Bicycle("Xe đạp", "B01");
        Vehicle vehicle = bicycle;
        Identifiable identifiable = bicycle;

        System.out.println(vehicle.move());
        System.out.println(identifiable.label());
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac -encoding UTF-8 LearningPathDemo.java
java LearningPathDemo
```

Output:

```text
Xe đạp đang chạy bằng sức người
ID-B01
```

Giải thích từng vai trò:

- `Vehicle` giữ trạng thái tên và hợp đồng `move()` cho cùng một họ phương tiện. `Bicycle` gọi constructor lớp cha qua `super(name)` và hiện thực `move()`.
- `Identifiable` mô tả khả năng có mã nhận diện, độc lập với họ phương tiện. Phương thức `label()` được triển khai sẵn bằng `default`.
- `Bicycle` đồng thời là một `Vehicle` và một `Identifiable`. Các biến `bicycle`, `vehicle`, `identifiable` đều tham chiếu **cùng một đối tượng**.
- Lời gọi `vehicle.move()` thực thi triển khai của `Bicycle` nhờ ghi đè và đa hình. `identifiable.label()` chạy phương thức mặc định, rồi gọi `id()` trên đối tượng `Bicycle`.
- Ví dụ này cố ý có cả abstract class và interface để luyện tập. Thiết kế thực tế có thể chỉ cần một interface hoặc chỉ cần class thông thường.

Nếu sửa code và có lỗi, đọc thông báo đầu tiên của `javac` trước: những lỗi thường gặp là thiếu `public` khi hiện thực phương thức interface, thiếu `@Override` đúng chữ ký, chưa hiện thực đủ phương thức abstract hoặc sai tên tệp `public class`.

### Giai đoạn 6: Mở rộng theo thứ tự hợp lý

1. **`default` method:** có thân và cung cấp triển khai mặc định cho instance; lớp hiện thực có thể ghi đè.
2. **`static` method:** gắn với interface và gọi qua tên interface, không phải phương thức instance kế thừa theo cách thông thường.
3. **`private` method:** dùng tái sử dụng logic nội bộ giữa các phương thức có thân trong interface; không phải phần hợp đồng công khai.
4. **Xung đột default:** hai interface cung cấp default cùng chữ ký thường buộc lớp hiện thực tự ghi đè để phân giải. Một phương thức instance cụ thể phù hợp từ superclass được ưu tiên hơn default từ interface.
5. **Functional interface và lambda:** một interface có đúng một phương thức trừu tượng phù hợp với lambda; `@FunctionalInterface` giúp kiểm tra ý định đó lúc biên dịch. Đây là kiến thức tiếp nối, chưa cần để hiểu interface cơ bản.
6. **Sau cùng**, tìm hiểu `sealed` class/interface (Java 17), thiết kế các hợp đồng nhỏ và các nguyên lý như SOLID nếu phạm vi bài học yêu cầu.

## 4. Nguyên tắc lựa chọn khi thực hành

| Tình huống | Hướng cân nhắc |
| --- | --- |
| Các lớp cùng một họ có trạng thái, điều kiện hợp lệ và xử lý chung. | `abstract class` có thể cung cấp khung chung và điểm mở rộng. |
| Các đối tượng khác họ cần công khai cùng một khả năng. | `interface` biểu diễn hợp đồng độc lập. |
| Một lớp cần phần dùng chung từ cha, đồng thời đáp ứng nhiều khả năng khác nhau. | `extends` một class và `implements` nhiều interface. |
| Chỉ cần tái sử dụng một chức năng, không có quan hệ is-a thật sự. | Cân nhắc **composition/delegation** thay vì ép kế thừa. |
| Code sử dụng chỉ cần vài thao tác được đảm bảo, không quan tâm lớp hiện thực. | Dùng tham số hoặc biến theo interface khi phù hợp. |

Không có quy tắc tuyệt đối “abstract class luôn tốt hơn” hoặc “interface luôn tốt hơn”. Hãy trả lời: **cái gì là bản chất chung của họ đối tượng, cái gì chỉ là khả năng, và việc nào thật sự cần chia sẻ triển khai/trạng thái?** Việc interface hỗ trợ `default` không biến field interface thành trạng thái riêng của instance.

## 5. Bài tập tăng dần và tiêu chí hoàn thành

**Bài 1 — Ôn đa hình:** Viết `Animal` có `sound()`, hai lớp `Cat` và `Dog` ghi đè. Cho biến `Animal` lần lượt tham chiếu hai đối tượng, dự đoán và kiểm chứng output. Đạt khi giải thích được runtime dispatch.

**Bài 2 — Lớp trừu tượng:** Viết `Shape`, `Rectangle`, `Circle` như trên. Thêm constructor lớp cha và một phương thức có thân dùng chung. Đạt khi giải thích được phần nào dùng chung, phần nào mỗi lớp con phải hiện thực.

**Bài 3 — Giao diện:** Viết `Printable`, `Invoice`, `Report` và hàm `runPrint(Printable item)`. Đạt khi thêm loại tài liệu mới mà không cần sửa phần gọi `runPrint`.

**Bài 4 — Kết hợp:** Thêm `Car extends Vehicle implements Identifiable` vào ví dụ `LearningPathDemo`. Nếu cần biểu diễn khả năng sạc, tạo `Rechargeable` chỉ cho lớp phù hợp. Đạt khi giải thích tại sao không phải mọi `Vehicle` đều `Rechargeable`.

**Bài 5 — Bài toán thiết kế:** Với (a) phương tiện có chung luật kiểm tra vận tốc, (b) hóa đơn và báo cáo cùng có khả năng xuất PDF, (c) một đối tượng cần chức năng ghi log, hãy chọn abstract class, interface hoặc composition và nêu lý do dựa trên miền nghiệp vụ.

## 6. Các nhầm lẫn cần phát hiện sớm

- Gọi interface chỉ là “class không có phương thức có thân”: interface hiện đại có `default`, `static`, `private`; giá trị chính vẫn là hợp đồng kiểu.
- Dùng `extends` cho interface được class hiện thực: class **`implements` interface**; interface `extends` interface.
- Cho rằng abstract class vô dụng vì không thể `new`: lớp con vẫn sử dụng constructor, state và phương thức cụ thể từ nó.
- Viết triển khai interface với mức truy cập mặc định: phương thức công khai của interface phải được hiện thực bằng phương thức `public`.
- Nhầm kiểu tham chiếu với kiểu đối tượng thực: kiểu tham chiếu giới hạn API lúc biên dịch; lời gọi phương thức instance ghi đè phụ thuộc đối tượng thực.
- Cố biến hằng số trong interface thành field riêng của đối tượng: field interface luôn là `public static final`.
- Dùng kế thừa chỉ để tiết kiệm vài dòng code: dễ tạo quan hệ is-a sai; cân nhắc composition.
- Học ngay default conflict, lambda hoặc sealed types trước khi hiểu `implements` và đa hình: dễ bị chi tiết nâng cao che mất nền tảng.

## 7. Tự đánh giá trước khi sang bài tiếp theo

Hãy tự trả lời (và chứng minh bằng code nếu cần):

1. Vì sao một abstract class có constructor nhưng không khởi tạo trực tiếp được?
2. Một lớp concrete phải làm gì khi nhận phương thức abstract chưa được hiện thực?
3. Vì sao một đối tượng `Bicycle` gán được vào biến `Vehicle` và `Identifiable`?
4. `extends` một class khác với `implements` interface ở ý nghĩa nào?
5. Khi nào cần hợp đồng độc lập, khi nào thật sự cần chia sẻ trạng thái của lớp cha?
6. `default` method có thay đổi quy tắc field interface không?
7. Bạn có thể dự đoán đúng output, biên dịch và giải thích ví dụ `LearningPathDemo` không?

Nếu chưa trả lời được, quay lại đúng mắt xích gần nhất trong chuỗi **class/object → kế thừa/đa hình → abstract class → interface → kết hợp**. Nếu trả lời được, bạn đã sẵn sàng học sâu cú pháp, giới hạn kế thừa, xung đột phương thức và thiết kế API bằng abstraction.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Phạm vi Java Core và cách đối chiếu phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi Java Core và cách đối chiếu phiên bản JDK khi học abstraction/interface

## 1. Đọc đúng phạm vi của bài

Trong chủ đề **Java Core – abstract/interface**, “Java Core” được hiểu là các **khái niệm ngôn ngữ Java và API nền tảng Java SE** cần để thiết kế, cài đặt và sử dụng trừu tượng hóa. Bài tập trung vào cách Java định nghĩa kiểu và hợp đồng, cách lớp triển khai chúng, cách quy tắc ngôn ngữ thay đổi giữa các phiên bản JDK và cách tự kiểm chứng khi tài liệu hoặc IDE cho kết quả khác nhau.

Những nội dung **nằm trong phạm vi chính**:

- **Cơ chế ngôn ngữ:** `abstract class`, phương thức `abstract`, `interface`, `extends`, `implements`, override, phạm vi truy cập, kiểu tham chiếu, tính đa hình và phương thức `default`/`static`/`private` trong interface theo phiên bản.
- **Hợp đồng và thiết kế:** ranh giới giữa điều mã sử dụng được biết và điều hiện thực phải làm; tính thay thế giữa các implementation; khi chọn interface hoặc abstract class.
- **Java SE API làm ví dụ:** `Runnable`, `Comparable`, `Comparator`, `List`, `AbstractList`, `AutoCloseable` và cách đọc API docs để xác định hợp đồng chính xác.
- **Tương thích phiên bản:** cú pháp nào xuất hiện từ bản Java nào, cách kiểm tra compiler/runtime và cách biên dịch nhắm đến một Java release.

Những thứ **không cần để hiểu nền tảng này**: Spring DI/AOP, Hibernate, Gradle plugin, SDK của nhà cung cấp, giao thức của framework hoặc kiến trúc hệ thống lớn. Chúng có thể **dùng** interface và abstract class, nhưng quy tắc của các cấu trúc đó do ngôn ngữ Java quy định. Ví dụ, annotation `@Component` của Spring không quyết định xem `private` method trong interface có hợp lệ ở Java 8 hay không.

“Java Core” là tên gọi học tập phổ biến, **không phải tên một bản phát hành chính thức độc lập với Java SE**. Khi cần kết luận chính xác, phải gắn phát biểu với một **Java SE/JDK release cụ thể**, ví dụ Java 8, 17 hoặc 21.

## 2. Phân biệt Java SE, JDK, JVM, thư viện và framework

| Khái niệm | Dùng để làm gì? | Liên quan đến bài học |
| --- | --- | --- |
| Java language | Quy định cú pháp và ý nghĩa của `interface`, `abstract`, `sealed`, access modifier, override… | **Nguồn chuẩn** để xác minh cấu trúc nào được phép viết. |
| Java SE platform | Định nghĩa các API tiêu chuẩn và phần nền tảng cần thiết cho ứng dụng Java nói chung. | Cho ví dụ hợp đồng như `java.lang.Runnable`, `java.util.List`. |
| JDK (Java Development Kit) | Bộ công cụ phát triển, gồm compiler `javac`, launcher `java`, Javadoc, các công cụ khác cùng một bản triển khai nền tảng. | Dùng để **biên dịch/chạy/kiểm chứng** tính tương thích. |
| JVM | Thực thi bytecode và cung cấp cơ chế runtime, gồm nạp class và gọi phương thức động. | Giải thích vì sao lời gọi qua interface có thể chọn hiện thực lúc chạy. |
| Thư viện/framework bên ngoài | Cung cấp API bổ sung, ví dụ Spring, Jackson, JUnit. | Chỉ là **ngữ cảnh áp dụng**, không quyết định luật cú pháp Java Core. |

Cần tách **sự có mặt của một tính năng ngôn ngữ** khỏi **sự có mặt của một phương thức thư viện**. Chẳng hạn, từ Java 8 có cú pháp default method trong interface; nhưng một phương thức cụ thể của `List` hoặc `Comparator` phải được kiểm tra riêng ở trang Javadoc của **phiên bản API** đang dùng.

JDK và Java SE cũng không hoàn toàn là một: tài liệu API JDK còn có các công cụ/gói đặc thù cho JDK. Một API nằm trong một bản JDK không tự động đồng nghĩa nó thuộc hợp đồng API Java SE có thể dùng trên mọi triển khai nền tảng.

## 3. Các mốc phiên bản thực sự liên quan tới interface và abstraction

Bảng này liệt kê **mốc tính năng được chuẩn hóa**, thay vì cho rằng mọi Java đều có cú pháp như nhau.

| Java release | Thay đổi đáng chú ý | Tác động thực hành |
| --- | --- | --- |
| Java 7 trở về trước | Interface cơ bản có hợp đồng phương thức trừu tượng và hằng số; chưa có default/static method với thân trong interface. | Lớp triển khai phải cung cấp implementation cho phương thức abstract mà nó chưa thừa kế từ đâu. |
| **Java 8** | Interface hỗ trợ `default` và `static` methods; lambda và functional interface là phần quan trọng của lập trình Java 8. | Có thể cung cấp hành vi mặc định mà vẫn giữ hợp đồng; có thể truyền lambda nơi giao diện có đúng một phương thức abstract. |
| **Java 9** | Interface được phép có `private` instance/static methods. | Các default/static methods có thể chia sẻ logic nội bộ mà không công bố nó như API public. |
| Java 16 | `record` trở thành tính năng chính thức. | Dễ mô tả dữ liệu bất biến dạng shallow với thành phần khai báo; record có thể `implements` interface. Đây không phải thay thế cho mọi abstract class. |
| **Java 17** | `sealed class` và `sealed interface` trở thành tính năng chính thức. | Giới hạn có chủ đích những kiểu được trực tiếp `extends`/`implements` một abstraction. |

Những gì **ổn định xuyên suốt**: interface thể hiện một kiểu/hợp đồng; abstract class có thể chứa trạng thái và hành vi chung; lớp Java chỉ kế thừa trực tiếp một superclass nhưng có thể implement nhiều interface; phương thức override phải tuân thủ hợp đồng ngôn ngữ.

Khi học một API, **“Since: 8” ở trang Javadoc** cho biết mốc API đó xuất hiện; còn **JEP/trang Java Language Changes** cho biết trạng thái của một tính năng ngôn ngữ. Hai loại thông tin trả lời hai câu hỏi khác nhau.

### 3.1. Java 8: default và static methods trong interface

Tạo tệp **`CoreVersionDemo.java`**:

```java
public class CoreVersionDemo {
    interface Greeter {
        String name(); // public abstract ngầm định

        default String greet() {
            return "Hello, " + name();
        }

        static String topic() {
            return "Java Core";
        }
    }

    static final class Student implements Greeter {
        @Override
        public String name() {
            return "Lan";
        }
    }

    public static void main(String[] args) {
        Greeter greeter = new Student();
        System.out.println(greeter.greet());
        System.out.println(Greeter.topic());
    }
}
```

Nếu đang dùng **JDK 17** làm compiler:

```bash
javac --release 8 CoreVersionDemo.java
java CoreVersionDemo
```

Kết quả:

```text
Hello, Lan
Java Core
```

Giải thích: `name()` là hợp đồng abstract, `Student` phải triển khai nó; `greet()` là method mặc định dùng được qua một đối tượng `Greeter`; `topic()` được gọi qua **tên interface**. Với interface, `static` method không được kế thừa như instance method. `default` **không** có nghĩa “Java sẽ tự chọn cách xử lý mọi xung đột đa kế thừa”: khi hai interface không liên quan cung cấp default method cùng chữ ký, lớp triển khai thường phải tự giải quyết xung đột bằng override.

Vì mục tiêu của ví dụ là Java 8, không dùng `var`, `record`, `sealed` hay method private của interface trong cùng tệp.

### 3.2. Java 9: private helper method trong interface

Tạo tệp riêng **`InterfacePrivateDemo.java`**:

```java
import java.util.Locale;

public class InterfacePrivateDemo {
    interface Formatter {
        default String title(String value) {
            return normalized(value).toUpperCase(Locale.ROOT);
        }

        private String normalized(String value) {
            return value.trim();
        }
    }

    static final class BasicFormatter implements Formatter {
    }

    public static void main(String[] args) {
        Formatter formatter = new BasicFormatter();
        System.out.println(formatter.title("  java core  "));
    }
}
```

Biên dịch bằng JDK 17 với release 9 rồi chạy:

```bash
javac --release 9 InterfacePrivateDemo.java
java InterfacePrivateDemo
```

Kết quả:

```text
JAVA CORE
```

Nếu đổi thành `javac --release 8 InterfacePrivateDemo.java`, compiler **phải từ chối** vì Java 8 chưa hỗ trợ private method trong interface. Không phụ thuộc vào việc JDK 17 đang chạy có hiểu cú pháp mới: `--release 8` buộc việc kiểm tra theo tập quy tắc được hỗ trợ cho đích 8. Nội dung thông báo lỗi có thể khác giữa các phiên bản compiler.

Lưu ý: `private` trong interface dùng cho helper có thân, không phải cách khai báo một phương thức abstract để lớp triển khai bên ngoài override.

### 3.3. Java 17: giới hạn các implementation trực tiếp bằng sealed interface

Tạo tệp **`SealedDemo.java`**:

```java
public class SealedDemo {
    sealed interface Result permits Ok, Failed {
    }

    record Ok(String value) implements Result {
    }

    record Failed(String reason) implements Result {
    }

    public static void main(String[] args) {
        Result result = new Ok("done");
        System.out.println(result instanceof Ok);
    }
}
```

Biên dịch và chạy bằng JDK 17+ có hỗ trợ target 17:

```bash
javac --release 17 SealedDemo.java
java SealedDemo
```

Kết quả:

```text
true
```

Trong ví dụ này, `Result` giới hạn các **kiểu được trực tiếp implement** nó vào `Ok` và `Failed`. Hai `record` vốn là final nên đáp ứng quy tắc cho các nhánh được phép. Một class khác không thể tùy ý `implements Result`. Cần phân biệt phạm vi của `sealed`: nó kiểm soát quan hệ mở rộng kiểu, **không tự bảo đảm** validation dữ liệu, bảo mật hay tính bất biến sâu của mọi đối tượng.

Code `record` và `sealed` không biên dịch khi target là `--release 8` hoặc `--release 11`. Nếu gặp ví dụ dùng `sealed` trong JDK 15/16, đó là giai đoạn **preview**, cần xem đúng trạng thái của bản phát hành đó; ở Java 17, `sealed` đã là tính năng chuẩn, không cần `--enable-preview`.

## 4. Chọn tài liệu nào để đối chiếu?

Khi có mâu thuẫn giữa bài blog, IDE và đoạn code, nên tra cứu theo thứ tự câu hỏi cần giải:

| Câu hỏi | Nguồn nên mở | Cách đọc |
| --- | --- | --- |
| “Cú pháp này hợp lệ từ Java mấy?” | **Java Language Changes** và **Java Language Specification (JLS)** theo release | Xác định từ khóa, modifier, giới hạn ngôn ngữ, trạng thái final/preview. |
| “`List` hoặc `Comparator` có method này từ khi nào?” | **Javadoc API theo đúng phiên bản**, trang class/interface cụ thể | Kiểm tra chữ ký, `Since`, `default`/`static`, điều kiện lỗi và `@Deprecated`. |
| “Tính năng từng preview hay đã ổn định?” | **JEP** liên quan và release notes | So sánh trạng thái đề xuất, preview/incubator, final và thay đổi qua mỗi lần phát hành. |
| “Bản JDK nào có thể compile/chạy code?” | Tài liệu `javac`, `java`, và output thực tế của công cụ | Kiểm tra JDK đang chọn, `--release`, classpath/module path và runtime. |
| “Nên chọn JDK nào cho dự án dài hạn?” | Release/support roadmap của đúng nhà phân phối JDK | Phân biệt bản feature release với LTS, thời hạn hỗ trợ và giấy phép riêng của nhà cung cấp. |

Các địa chỉ **chính thức** hữu ích:

1. [Java Language Changes – Java 17](https://docs.oracle.com/en/java/javase/17/language/java-language-changes-release.html) — mốc ngôn ngữ; có các phần `sealed` và `record`.
2. [JLS Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html) — quy tắc ngôn ngữ chính thức; tra mục về Interfaces, Classes, Method Declarations.
3. [Java SE 17 & JDK 17 API Specification](https://docs.oracle.com/en/java/javase/17/docs/api/index.html) — tra chữ ký từng API, package/module, `Since`, `@Deprecated`.
4. [`javac` JDK 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html) — các lựa chọn `--release`, `--source`, `--target`.
5. [JEP 409: Sealed Classes](https://openjdk.org/jeps/409) — lịch sử và trạng thái của sealed types.
6. [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) — lịch phát hành và phân loại LTS.
7. [JDK 27 Release Notes (15/09/2026)](https://www.oracle.com/java/technologies/javase/27all-relnotes.html) — ví dụ về trang ghi nhận thay đổi ở một release mới hơn Java 17.

Mỗi tài liệu có **phiên bản** riêng. Đừng đọc Javadoc của Java 21 rồi khẳng định API đó tồn tại trong Java 8, hoặc dùng JLS Java 17 để suy ra cú pháp Java 8. Đối với tutorial cũ của Oracle, đọc cảnh báo phiên bản nếu tài liệu nói nó viết cho JDK 8.

**Mốc thời gian cần ghi rõ khi so sánh “mới nhất”:** tính đến **23/10/2026**, JDK 27 là feature release đã phát hành tháng 9/2026, còn JDK 25 là bản LTS mới nhất của Oracle trong danh sách công bố. `LTS` là chính sách hỗ trợ phát hành, **không** là cú pháp hay hệ loại `interface`. Khi đọc bài học ở thời điểm sau đó, hãy kiểm tra lại roadmap thay vì mặc định con số này còn đúng.

## 5. Cách xác minh Java/JDK thực tế trên máy

### Bước 1: xem compiler và runtime đang được gọi

Mở terminal trong thư mục dự án và chạy:

```bash
java -version
javac -version
```

Hai lệnh cần được kiểm tra **riêng**, vì hệ thống có thể trỏ `java` và `javac` đến hai bản JDK khác nhau tùy cấu hình `PATH`, `JAVA_HOME`, IDE hoặc toolchain của build. `java -version` hiển thị runtime; `javac -version` hiển thị compiler. Không thể suy ra JDK build thực tế chỉ từ giao diện “Project SDK” nếu build dùng toolchain khác.

Nếu `javac` không tồn tại nhưng `java` chạy được, có thể môi trường đang chỉ có runtime hoặc `PATH` chưa trỏ đúng thư mục công cụ của JDK. Hãy kiểm tra cài đặt JDK và cấu hình shell.

### Bước 2: chọn phiên bản đích, không mặc định lấy phiên bản compiler

Với một JDK hỗ trợ Java 8/9/17, dùng các lệnh:

```bash
javac --release 8 CoreVersionDemo.java
javac --release 9 InterfacePrivateDemo.java
javac --release 17 SealedDemo.java
```

**`--release N`** hướng compiler kiểm tra cú pháp theo Java N, tạo class file cho phiên bản đó và biên dịch với tập API nền tảng mà bản N công bố. Đây là lựa chọn nên ưu tiên khi muốn kiểm tra khả năng dùng Java N.

Đừng nhầm:

- `--source N` giới hạn **cú pháp ngôn ngữ**, nhưng đứng một mình không chứng minh rằng code chỉ dùng API có trong Java N.
- `--target N` chọn **định dạng bytecode đích**, cũng không đủ để hạn chế API theo Java N nếu cấu hình sai.
- `--release N` kết hợp các mặt trên với kiểm tra API nền tảng của release N. Không ghép `--release` với `--source` hoặc `--target` trong cùng một lệnh.

`javac` chỉ hỗ trợ một **tập hữu hạn các release đích**; `javac --help` của chính bản JDK đang dùng cho biết danh sách được hỗ trợ. JDK mới **không nhất thiết** còn hỗ trợ biên dịch cho mọi release rất cũ.

### Bước 3: kiểm tra runtime đích

Biên dịch thành công bằng `--release 17` **không** đồng nghĩa có thể chạy file `.class` bằng Java 8. Runtime phải hỗ trợ định dạng class file và các tính năng/API mà chương trình cần; hướng chạy thông thường là dùng một runtime cùng hoặc mới hơn bản đích và kiểm tra phụ thuộc thực tế.

Ví dụ, khi `SealedDemo.java` được biên dịch với `--release 17`, hãy chạy bằng runtime Java 17+:

```bash
java SealedDemo
```

Nếu gặp `UnsupportedClassVersionError`, kiểm tra lại output của `java -version`, phiên bản biên dịch và vị trí class file đang được nạp. Nếu gặp `NoSuchMethodError` hoặc `ClassNotFoundException`, còn phải xem sự khác biệt API và dependency/runtime classpath; chỉ đổi `--target` không đủ để bảo đảm tương thích.

### Bước 4: với công cụ build, đối chiếu phiên bản ở cả ba nơi

Trong dự án dùng Maven/Gradle/IDE, hãy xác định:

1. **JDK chạy build/compiler**: bản `javac` hoặc Java toolchain được chọn.
2. **Release biên dịch đích**: cấu hình `release` của compiler; với dự án Gradle có thể là `options.release`, với Maven là `maven.compiler.release`.
3. **JDK chạy ứng dụng**: bản `java` tại môi trường chạy hoặc container.

Một project có thể chạy build trên JDK 21 nhưng xuất bytecode hướng Java 17 nếu compiler hỗ trợ `--release 17`. Điều đó không làm cú pháp Java 21 tự nhiên hợp lệ trong các tệp được biên dịch cho Java 17.

## 6. Quy trình đối chiếu một khẳng định cụ thể

Giả sử thấy câu: “Trong Java, interface có thể khai báo private method”. Cách kiểm chứng chặt chẽ là:

1. **Chuyển thành câu hỏi có phiên bản:** “Trong **Java 8** có thể khai báo private instance method trong interface không? Còn **Java 9**?”
2. **Tra nguồn ngôn ngữ:** trang *Java Language Changes* ghi mốc private interface methods là Java 9.
3. **Viết mẫu nhỏ nhất có thể biên dịch:** dùng `InterfacePrivateDemo.java` ở trên, tách biệt với framework.
4. **Biên dịch hai mục tiêu bằng cùng một JDK phù hợp:** `--release 8` sẽ bị từ chối; `--release 9` hợp lệ.
5. **Kết luận đúng phạm vi:** “Private helper method trong interface được hỗ trợ từ Java 9”; không mở rộng thành “mọi interface method có thể là private abstract” hoặc “Java 8 cũng hỗ trợ vì đang cài JDK 17”.

Tương tự, với phát biểu “interface có thể cài sẵn method”, cần hỏi method loại nào: `default`/ `static` từ Java 8; `private` helper từ Java 9. Với “`sealed interface` có dùng được không?”, phải hỏi Java 15/16 đang preview hay Java 17 trở lên là tính năng chuẩn.

Khi nguồn bất đồng, hãy ghi lại đủ **phiên bản Java**, **đường dẫn tài liệu**, **lệnh javac**, **đầu vào tối thiểu** và **kết quả quan sát được**. Một video hoặc blog không nêu phiên bản không đủ để bác bỏ compiler và đặc tả đúng phiên bản.

## 7. Giới hạn quan trọng và lỗi thường gặp

- **Không nhầm từ khóa với thư viện.** `abstract`/`interface` là ngôn ngữ; `List`/`Comparator` là API; Spring là framework. Mỗi loại cần nguồn đối chiếu phù hợp.
- **Không dùng JDK mới để suy ra JDK cũ.** Chương trình chạy trên Java 17 với cú pháp record/sealed không chứng minh nó chạy hoặc biên dịch cho Java 8.
- **Không đồng nhất JDK và Java SE spec.** Một số công cụ và API cụ thể thuộc JDK, không phải cam kết Java SE chung. Đừng phụ thuộc vào API nội bộ chỉ vì compiler trên máy tình cờ thấy được chúng.
- **Không hiểu sai `default` method là “không cần implementation”.** `default` có implementation ngay trong interface nhưng vẫn chịu quy tắc override, phân giải xung đột và quyền truy cập.
- **Không đồng nhất LTS và độ mới tính năng.** LTS liên quan tới chính sách hỗ trợ; một non-LTS mới hơn có thể bổ sung tính năng nhưng không thích hợp với mọi chiến lược vận hành.
- **Không bỏ qua preview feature.** Một tính năng ở trạng thái preview có thể cần cờ compile/run cụ thể và có thể đổi trước khi thành chính thức. Không lấy ví dụ preview để trình bày như cú pháp phổ quát của Java SE.
- **Không để API docs của phiên bản mới che mất `Since` và `Deprecated`.** Một method có thể chưa tồn tại, đổi trạng thái hoặc bị loại bỏ ở bản mục tiêu.
- **Không gán kết quả thử nghiệm cho mọi môi trường.** Một ví dụ độc lập kiểm tra cú pháp; build thực tế còn phụ thuộc module path, classpath, annotation processor, công cụ build và runtime đích.

## 8. Checklist áp dụng cho từng bài abstract/interface về sau

Trước khi đọc hoặc viết một ví dụ, tự xác nhận:

- Đây là **quy tắc ngôn ngữ Java** hay **API của một thư viện**?
- Bài dùng Java **phiên bản nào**; tính năng xuất hiện từ release nào; đang final hay preview?
- Compiler có đang dùng **đúng `--release`**; bản JDK có hỗ trợ release đó?
- Runtime thực thi có cùng khả năng với binary đã biên dịch và API cần dùng?
- Ví dụ minh họa abstraction có yêu cầu **thư viện bên ngoài** không? Nếu có, cần tách quy tắc Java Core khỏi hành vi thư viện đó.
- Kết luận đã dựa trên **JLS/Javadoc/JEP/release notes** đúng phiên bản và một ví dụ tối thiểu tái hiện được chưa?

**Tự luyện:** Hãy thử biên dịch ba tệp `CoreVersionDemo.java`, `InterfacePrivateDemo.java`, `SealedDemo.java` bằng JDK 17 với `--release 8`, `--release 9` và `--release 17` tương ứng. Sau đó cố tình giảm `--release` của hai tệp sau và giải thích vì sao compiler từ chối. Đối chiếu kết luận với Java Language Changes, không dựa vào việc IDE có tô màu cú pháp hay không.

**Ghi nhớ:** trong Java Core, cú pháp và hợp đồng abstraction/interface do **phiên bản ngôn ngữ** quyết định; API nền tảng do **phiên bản Java SE** quy định; `javac --release` cùng môi trường runtime giúp biến lời khẳng định thành điều có thể kiểm chứng. Luôn nói rõ phiên bản trước khi kết luận một tính năng “có” hoặc “không”.


</details>

- [Quay lại đầu trang](#back-to-top)
