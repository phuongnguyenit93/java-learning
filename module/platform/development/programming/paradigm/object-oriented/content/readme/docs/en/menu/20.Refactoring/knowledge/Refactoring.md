<a id="back-to-top"></a>

# Tái cấu trúc

## Menu
- [Nhận diện dấu hiệu cần cải thiện thiết kế](#refactoring-001)
- [Đổi tên để làm rõ ý định](#refactoring-002)
- [Tách phương thức và tách lớp](#refactoring-003)
- [Di chuyển hành vi về đối tượng chịu trách nhiệm](#refactoring-004)
- [Đóng gói trường dữ liệu và cấu trúc nội bộ](#refactoring-005)
- [Thay điều kiện theo loại bằng đa hình](#refactoring-006)
- [Thay kế thừa bằng kết hợp](#refactoring-007)
- [Gộp hoặc loại bỏ trừu tượng không cần thiết](#refactoring-008)
- [Cải thiện thiết kế từng bước với kiểm thử bảo vệ](#refactoring-009)

## <a id="refactoring-001">Nhận diện dấu hiệu cần cải thiện thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện dấu hiệu cần cải thiện thiết kế OOP

> **Chủ đề:** Tái cấu trúc (refactoring) — nhận ra lúc thiết kế bắt đầu gây khó khăn, xác định nguyên nhân và chọn bước cải thiện nhỏ, có kiểm chứng.

Một chương trình Java **chạy đúng** chưa chắc đã **dễ thay đổi**. Dấu hiệu cần cải thiện thường xuất hiện khi thêm một quy tắc nhỏ mà phải sửa nhiều nơi, một lớp phụ trách nhiều việc, cùng một điều kiện nghiệp vụ bị sao chép, hoặc kiểm thử một chức năng đơn giản lại cần khởi tạo hàng loạt phụ thuộc.

Các hiện tượng như vậy thường được gọi là **code smell**: tín hiệu gợi ý rằng cấu trúc mã có vấn đề đáng xem xét. **Code smell không tự động là lỗi** và không có quy tắc “cứ gặp là phải refactor”. Mục tiêu là đánh giá *tác động* của thiết kế đến sửa lỗi, mở rộng, hiểu mã và kiểm thử; sau đó cải thiện với chi phí hợp lý.

## 1. Nhìn vào khó khăn khi thay đổi, không chỉ độ dài của mã

Hãy đặt câu hỏi thực tế:

- Khi thêm một tính năng nhỏ, phải mở và chỉnh sửa bao nhiêu class? Các vị trí đó có cùng một lý do thay đổi không?
- Khi sửa lỗi, điều kiện hay công thức nghiệp vụ có bị lặp ở nhiều nơi?
- Có class nào vừa tính toán nghiệp vụ, vừa truy vấn dữ liệu, vừa định dạng giao diện, vừa gửi email không?
- Một hàm có dễ đọc từ tên và mức trừu tượng của các bước không, hay cần theo dõi nhiều cờ, nhánh lồng nhau và biến tạm?
- Test có thể tạo đối tượng một cách đơn giản, kiểm tra hành vi được không, hay phải mock nhiều chi tiết nội bộ?
- Sau khi sửa một class, có thường xuyên phát sinh lỗi ở các class tưởng như không liên quan?

Những câu trả lời này hữu ích hơn việc chỉ đếm số dòng: một phương thức dài nhưng ổn định, diễn đạt tuyến tính có thể ít rủi ro hơn một phương thức ngắn có nhiều tác dụng phụ khó thấy.

### Các dấu hiệu thường gặp

| Dấu hiệu (code smell) | Quan sát trong code Java | Nguy cơ thực tế | Hướng xem xét, chưa phải mệnh lệnh |
|---|---|---|---|
| **Long Method** | Một phương thức chứa nhiều bước, nhiều nhánh hoặc mức trừu tượng lẫn lộn | Khó đọc, khó test từng quy tắc | `Extract Method` để đặt tên cho các bước có ý nghĩa |
| **Large Class / God Object** | Class nắm nhiều trách nhiệm không liên quan, nhiều field và nhóm method | Mỗi thay đổi gây ảnh hưởng diện rộng | Tách theo lý do thay đổi, tăng tính kết dính |
| **Duplicate Code** | Cùng điều kiện/công thức nghiệp vụ xuất hiện ở nhiều nơi | Sửa thiếu một nơi dẫn đến sai lệch | Tập trung quy tắc chung; tránh trừu tượng hóa sự giống nhau tình cờ |
| **Shotgun Surgery** | Một yêu cầu nhỏ bắt buộc sửa nhiều class | Thay đổi phân tán, khó review | Đưa logic liên quan về một đầu mối phù hợp |
| **Divergent Change** | Một class liên tục sửa vì các lý do khác nhau | Phạm vi trách nhiệm mờ, rủi ro sửa chéo | Tách trách nhiệm theo các hướng biến đổi |
| **Feature Envy** | Method của A liên tục truy cập dữ liệu/chi tiết của B để ra quyết định | Quy tắc đặt sai chỗ, kết dính thấp | Cân nhắc `Move Method` hoặc API giàu ngữ nghĩa trên B |
| **Primitive Obsession** | `String`/`int`/`boolean` gánh nhiều quy tắc miền, “magic string” | Dễ nhầm đơn vị, trạng thái không hợp lệ | `enum`, record/value object hoặc kiểu dữ liệu miền |
| **Long Parameter List / Data Clumps** | Nhiều hàm lặp cùng cụm tham số, nhiều cờ boolean | Gọi nhầm thứ tự, tăng chi phí thay đổi | Gom thành parameter object nếu thực sự là một khái niệm |
| **Conditional Complexity** | `switch`/`if` nhiều nhánh theo loại đối tượng và lặp ở nhiều vị trí | Thêm loại mới phải sửa nhiều nơi | Polymorphism, Strategy, bảng quy tắc nếu phù hợp |
| **Inappropriate Intimacy / Tight Coupling** | Một class biết sâu về dữ liệu/nội bộ của class khác, khởi tạo trực tiếp nhiều dependency | Test khó, thay implementation khó | Giảm coupling, inject dependency ở ranh giới hợp lý |
| **Data Class / Anemic Model** | Object miền chỉ là getter/setter; quy tắc liên quan rải ở service | Bất biến khó được bảo vệ | Đưa *hành vi thuộc trách nhiệm miền* vào nơi phù hợp |
| **Speculative Generality** | Interface, inheritance, plugin point chưa có nhu cầu thực tế | Quá nhiều lớp, khó định hướng | Xóa tầng trừu tượng không đem lại lợi ích rõ ràng |
| **Comments as Deodorant** | Bình luận phải diễn giải các nhánh tối nghĩa | Ý định bị che bởi cấu trúc khó hiểu | Làm rõ tên gọi, phân rã logic; giữ comment về lý do |
| **Mutable Shared State** | Nhiều object sửa cùng danh sách/cache dùng chung | Bug theo thứ tự chạy, race condition | Bất biến dữ liệu, quản lý ownership và concurrency |

**Không dùng số dòng hoặc số class làm luật cứng.** Một class nhỏ nhưng chứa quy tắc mâu thuẫn vẫn có vấn đề. Ngược lại, chia một đoạn logic đơn giản thành 15 interface có thể khiến thiết kế tệ hơn.

## 2. Phân biệt những dấu hiệu dễ nhầm

**Divergent Change** và **Shotgun Surgery** đều gây đau khi sửa code nhưng khác góc nhìn:

- **Divergent Change:** *một class* phải sửa vì nhiều **lý do** khác nhau; ví dụ `OrderManager` cùng chịu ảnh hưởng của thay đổi chính sách giá, định dạng email và schema lưu trữ.
- **Shotgun Surgery:** *một lý do thay đổi* buộc sửa **nhiều class**; ví dụ đổi ngưỡng miễn phí vận chuyển phải cập nhật `Cart`, `Checkout`, `Invoice`, `OrderPreview`.

Tương tự, **Duplicate Code** không có nghĩa mọi đoạn giống nhau phải gom lại. Hai quy tắc hiện tại trông giống nhau nhưng thuộc hai ngữ cảnh có vòng đời thay đổi độc lập có thể nên giữ riêng. Khi trừu tượng hóa, cần chứng minh **cùng một tri thức/nguyên tắc**, không chỉ cùng cú pháp.

**Long Method** khác **Complex Method**: dài vì ánh xạ nhiều bước tuần tự không nhất thiết khó hiểu; ngắn nhưng có nhiều tương tác trạng thái và side effect vẫn có thể phức tạp.

### Từ quan sát đến giả thuyết thiết kế

| Quan sát thực tế | Giả thuyết cần kiểm tra | Bằng chứng nên tìm |
|---|---|---|
| Sửa một quy tắc phải sửa 5 file | Quy tắc bị phân tán | Diff của các thay đổi trước, vị trí công thức trùng lặp |
| Test một class phải mock 8 collaborator | Class gánh nhiều trách nhiệm/coupling cao | Constructor, lời gọi IO, setup trong test |
| Người mới mất nhiều thời gian hiểu một method | Mức trừu tượng hoặc tên chưa rõ | Các nhánh lồng nhau, nhiều biến tạm, diễn giải trong review |
| Có nhiều cờ `boolean` | Một hành vi bị điều khiển bởi nhiều chế độ | Những tổ hợp cờ, trường hợp bất hợp lệ và test thiếu |
| Bug lặp tại nhiều màn hình | Cùng quy tắc bị cài khác nhau | Input và output gây bug, bản sao của quy tắc |
| Tốc độ thay đổi giảm rõ rệt | Có điểm nóng thiết kế | Tần suất sửa, lỗi hồi quy, thời gian review, phạm vi diff |

Để tránh kết luận vội: nhìn vào **trách nhiệm**, **lịch sử thay đổi**, **lỗi thực tế** và **test hiện hữu**. Công cụ static analysis/cảnh báo complexity có thể giúp khoanh vùng; con số metric chỉ là tín hiệu bổ trợ.

## 3. Ví dụ: ba trách nhiệm bị trộn trong một class

Giả sử hệ thống báo giá có hợp đồng đã được thống nhất:

- `subtotalCents` là số tiền hàng theo **cent**, từ `0` đến `1_000_000`.
- Loại khách hàng hợp lệ: chuỗi phân biệt hoa/thường `"STANDARD"` hoặc `"VIP"`.
- Khách VIP được giảm `10%` tiền hàng (làm tròn xuống từng cent) và miễn phí vận chuyển.
- Khách thường không giảm giá; miễn phí vận chuyển khi `subtotalCents >= 100_000`, còn lại trả `15_000` cent.
- `total = subtotal - discount + shipping`.
- Dữ liệu không hợp lệ phải ném `IllegalArgumentException`.
- Chuỗi kết quả có định dạng `discount=...;shipping=...;total=...`.

Phiên bản ban đầu kết hợp **kiểm tra đầu vào**, **tính giá**, **định dạng đầu ra** trong một method. Khi thay quy tắc vận chuyển, đổi output format hoặc bổ sung loại khách hàng, nhóm code này cùng bị tác động. Đó là dấu hiệu **Divergent Change**, đi kèm **Primitive Obsession** vì giá trị `String` đại diện cho loại khách hàng.

Refactor có thể tách một **bộ quy tắc giá**, một **bộ định dạng**, và một **facade** giữ API cũ. Đây chỉ là *một* phương án hợp lý khi các phần có lý do thay đổi độc lập; với ứng dụng rất nhỏ, chi phí thêm lớp có thể không đáng.

### Chạy thử trước và sau refactor (Java 17+)

Tạo tệp `DesignSmellsDemo.java`, đặt toàn bộ đoạn sau vào tệp đó:

~~~java
import java.util.List;

public class DesignSmellsDemo {
    private static final long MAX_SUBTOTAL = 1_000_000L;
    private static final long FREE_SHIPPING_AT = 100_000L;
    private static final long SHIPPING_FEE = 15_000L;

    // API mà client sử dụng; cả hai phiên bản đều thực hiện.
    interface QuoteService {
        String quote(String customerType, long subtotalCents);
    }

    // BEFORE: validation, chính sách giá và định dạng nằm chung.
    static final class LegacyQuoteService implements QuoteService {
        @Override
        public String quote(String customerType, long subtotalCents) {
            if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
                throw new IllegalArgumentException("subtotal");
            }
            if (!"VIP".equals(customerType)
                    && !"STANDARD".equals(customerType)) {
                throw new IllegalArgumentException("customerType");
            }

            boolean vip = "VIP".equals(customerType);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                    ? 0 : SHIPPING_FEE;
            long total = subtotalCents - discount + shipping;

            return "discount=" + discount
                    + ";shipping=" + shipping
                    + ";total=" + total;
        }
    }

    // AFTER: biểu diễn loại khách hàng bằng một kiểu miền rõ ràng.
    enum CustomerType {
        STANDARD, VIP;

        static CustomerType parse(String text) {
            if ("STANDARD".equals(text)) return STANDARD;
            if ("VIP".equals(text)) return VIP;
            throw new IllegalArgumentException("customerType");
        }
    }

    // Value object bất biến; trường dữ liệu không bị trộn với format.
    record Price(long discount, long shipping, long total) {}

    static final class PricingRules {
        Price calculate(CustomerType type, long subtotalCents) {
            long discount = type == CustomerType.VIP ? subtotalCents / 10 : 0;
            long shipping =
                    (type == CustomerType.VIP || subtotalCents >= FREE_SHIPPING_AT)
                        ? 0 : SHIPPING_FEE;
            return new Price(discount, shipping,
                    subtotalCents - discount + shipping);
        }
    }

    static final class QuoteFormatter {
        String format(Price price) {
            return "discount=" + price.discount()
                    + ";shipping=" + price.shipping()
                    + ";total=" + price.total();
        }
    }

    // Giữ API cũ; phối hợp các trách nhiệm đã tách ra.
    static final class RefactoredQuoteService implements QuoteService {
        private final PricingRules pricing;
        private final QuoteFormatter formatter;

        RefactoredQuoteService(PricingRules pricing, QuoteFormatter formatter) {
            this.pricing = java.util.Objects.requireNonNull(pricing);
            this.formatter = java.util.Objects.requireNonNull(formatter);
        }

        @Override
        public String quote(String customerType, long subtotalCents) {
            if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
                throw new IllegalArgumentException("subtotal");
            }
            CustomerType type = CustomerType.parse(customerType);
            Price price = pricing.calculate(type, subtotalCents);
            return formatter.format(price);
        }
    }

    record Example(String type, long subtotal, String expected) {}

    // Expected values được viết theo yêu cầu, không tính bằng production code.
    private static final List<Example> EXAMPLES = List.of(
        new Example("STANDARD", 0,
                "discount=0;shipping=15000;total=15000"),
        new Example("STANDARD", 99_999,
                "discount=0;shipping=15000;total=114999"),
        new Example("STANDARD", 100_000,
                "discount=0;shipping=0;total=100000"),
        new Example("VIP", 100_000,
                "discount=10000;shipping=0;total=90000"),
        new Example("VIP", 250_001,
                "discount=25000;shipping=0;total=225001"),
        new Example("STANDARD", 1_000_000,
                "discount=0;shipping=0;total=1000000")
    );

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void verifyBehavior(String label, QuoteService service) {
        for (Example ex : EXAMPLES) {
            String actual = service.quote(ex.type(), ex.subtotal());
            require(ex.expected().equals(actual),
                    label + " mismatch at " + ex + "; actual=" + actual);
        }
        expectInvalid(() -> service.quote("VIP", -1));
        expectInvalid(() -> service.quote("STANDARD", 1_000_001));
        expectInvalid(() -> service.quote("UNKNOWN", 50_000));
        expectInvalid(() -> service.quote(null, 50_000));
    }

    interface Action {
        void run();
    }

    private static void expectInvalid(Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        QuoteService legacy = new LegacyQuoteService();
        QuoteService refactored =
                new RefactoredQuoteService(new PricingRules(), new QuoteFormatter());

        verifyBehavior("legacy", legacy);
        verifyBehavior("refactored", refactored);

        for (Example ex : EXAMPLES) {
            require(
                legacy.quote(ex.type(), ex.subtotal())
                    .equals(refactored.quote(ex.type(), ex.subtotal())),
                "Observable behavior changed at " + ex);
        }

        System.out.println("PASS: original business rules");
        System.out.println("PASS: refactored business rules");
        System.out.println("PASS: same public API and observable output");
        System.out.println(refactored.quote("VIP", 250_001));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DesignSmellsDemo.java
java DesignSmellsDemo
~~~

Đầu ra kỳ vọng:

~~~text
PASS: original business rules
PASS: refactored business rules
PASS: same public API and observable output
discount=25000;shipping=0;total=225001
~~~

Ở đây, **lợi ích thiết kế** nằm ở việc có thể kiểm thử và sửa `PricingRules` độc lập với `QuoteFormatter`, đồng thời giảm khả năng việc đổi cách trình bày vô tình tác động chính sách giá. `CustomerType` thay chuỗi tự do bằng enum ở tầng domain; API bên ngoài vẫn nhận chuỗi theo hợp đồng ban đầu.

**Nhận xét quan trọng:** Ví dụ cố ý nhỏ để có thể chạy độc lập. Không nên kết luận rằng mọi phương thức giống `LegacyQuoteService.quote` đều bắt buộc phải tách thành ba class. Hãy cân nhắc tần suất thay đổi và lợi ích kiểm thử trước khi tăng số lượng abstraction.

## 4. Nhận diện bằng luồng thay đổi: sửa ở đâu, tác động tới đâu?

Cùng quan sát một yêu cầu: “Tăng ngưỡng miễn phí vận chuyển từ 100.000 lên 120.000 cent”.

- Nếu chỉ thay một chính sách rõ ràng, có test cho các ngưỡng mới và không làm ảnh hưởng định dạng output, thiết kế có điểm tập trung trách nhiệm tốt.
- Nếu phải sửa cùng hằng số ở `CartController`, `CheckoutService`, `OrderConfirmationEmail` và `InvoiceExporter`, rất có thể tồn tại **Shotgun Surgery**. Cần kiểm tra chúng có dùng *cùng một quy tắc nghiệp vụ* hay không.
- Nếu phải sửa class vừa chứa công thức giá, logic lưu DB, gửi SMS và dựng HTML, có thể class đang chịu **Divergent Change**.
- Nếu thêm loại khách hàng luôn phải sửa các `switch` ở năm class, xem xét **polymorphism/Strategy** hoặc chuyển logic về một nơi thích hợp. Một `switch` duy nhất không nhất thiết là smell.

~~~mermaid
flowchart TD
    A["Phát hiện khó khăn khi sửa hoặc test"] --> B["Mô tả triệu chứng bằng ví dụ cụ thể"]
    B --> C["Xác định các lý do thay đổi và trách nhiệm"]
    C --> D{"Có bằng chứng về chi phí hoặc lỗi?"}
    D -->|Chưa| E["Theo dõi; tránh trừu tượng hóa vội"]
    D -->|Có| F["Đặt giả thuyết: lặp logic, coupling, cohesion..."]
    F --> G["Chọn phép refactor nhỏ nhất phù hợp"]
    G --> H["Chạy unit/contract/regression tests"]
    H --> I{"Hành vi còn đúng và code dễ thay đổi hơn?"}
    I -->|Không| J["Điều chỉnh hoặc hoàn tác bước vừa làm"]
    I -->|Có| K["Giữ thay đổi, ghi lại lý do thiết kế"]
~~~

## 5. Chọn phép tái cấu trúc tương ứng với nguyên nhân

| Vấn đề đã xác nhận | Kỹ thuật có thể phù hợp | Điểm cần cẩn thận |
|---|---|---|
| Một method gồm các nhóm thao tác rõ ý nghĩa | `Extract Method`, đổi tên theo ý định | Không tạo hàng loạt method chỉ gọi tiếp nhau mà không tăng độ rõ |
| Class trộn UI, nghiệp vụ, truy cập dữ liệu | `Extract Class`, phân lớp trách nhiệm | Giữ contract ở ranh giới, tránh tạo coupling vòng |
| Cùng quy tắc được sao chép | `Extract Function`, chuyển sang service/value object dùng chung | Chỉ hợp nhất khi thực sự cùng một quy tắc |
| Tham số miền chỉ là chuỗi/mã số dễ sai | `Replace Primitive with Object`, `enum`, `record` | Giữ khả năng serialize/deserialize và tương thích API |
| Nhánh phân loại lặp ở nhiều chỗ | `Replace Conditional with Polymorphism` hoặc Strategy | Đừng thêm hierarchy cho một điều kiện đơn giản, ổn định |
| Method thao tác nhiều vào dữ liệu class khác | `Move Method`, `Encapsulate Field` | Tránh phá encapsulation khi di chuyển |
| Ràng buộc tạo đối tượng gắn chặt với dependency | Constructor injection, interface ở ranh giới phù hợp | Không phải mọi class đều cần interface |
| Kế thừa chỉ để tái dùng vài dòng code | Chuyển sang composition hoặc delegate | Kiểm tra hành vi thay thế và compatibility trước khi bỏ kế thừa |
| Cờ boolean điều khiển hai hành vi khác nhau | `Replace Parameter with Explicit Methods` hoặc loại miền | Cân nhắc API dành cho client, không chỉ tên nội bộ |
| Nhiều getter/setter cho phép trạng thái sai | Đưa validation/bất biến vào entity/value object | Không biến object dữ liệu thuần thành class quá phức tạp |

**Cohesion (tính kết dính)**: các phần trong một class cùng phục vụ một trách nhiệm có ý nghĩa. **Coupling (sự phụ thuộc)**: mức độ module cần biết về nhau. Mục tiêu thường là tăng cohesion và giảm coupling không cần thiết, nhưng cũng phải cân bằng với độ phức tạp do thêm abstraction.

## 6. Bốn mức bằng chứng trước khi đề xuất refactor

### Mức 1: Dấu hiệu đọc code

Nhìn tên lớp, tên method, điều kiện lặp, field ít liên quan, số dependency; lập giả thuyết. Đây là bằng chứng ban đầu, **chưa đủ** để kết luận chi phí thiết kế.

### Mức 2: Thay đổi thật

Lấy một yêu cầu cụ thể và lần theo vùng ảnh hưởng: sửa bao nhiêu vị trí, lỗi phát sinh ở đâu, code review khó điều gì. Những vùng thường xuyên vừa **thay đổi** vừa **phức tạp** đáng ưu tiên xem xét hơn mã cũ ít khi đụng tới.

### Mức 3: Kiểm thử hành vi

Xác định input, expected output, loại ngoại lệ, trạng thái và side effect mà client quan sát. Viết test cho trường hợp thường, biên, lỗi và chuỗi thao tác. Có regression test rồi mới refactor khi thay đổi liên quan logic quan trọng.

### Mức 4: Đánh giá sau cải thiện

Một refactor có chất lượng khi những thay đổi tiếp theo dễ xác định vị trí hơn, test rõ hơn, logic ít trùng lặp hơn và không làm sai hợp đồng. Nếu kết quả chỉ là số class tăng lên, abstraction khó hiểu hơn, thì chưa có đủ cơ sở để coi đó là cải thiện.

Một vài metric có thể tham khảo: **cyclomatic complexity**, số dependency, mức lặp, các file thường sửa cùng nhau, thời gian sửa lỗi, số regression phát sinh. Không có một ngưỡng metric phổ quát áp dụng đúng cho mọi dự án.

## 7. Khi nào không nên refactor ngay?

- **Không có yêu cầu thay đổi, ít rủi ro và đoạn code đủ rõ:** ghi nhận, ưu tiên khu vực khác có tác động lớn hơn.
- **Code thiếu test và liên quan tiền bạc, dữ liệu hoặc quy trình quan trọng:** nên tạo characterization/contract tests trước, tránh refactor dựa trên phỏng đoán.
- **Chưa hiểu quy tắc nghiệp vụ:** hỏi người chịu trách nhiệm miền, làm rõ các trường hợp biên rồi mới chia class.
- **Đang trong một thay đổi sửa lỗi khẩn cấp:** có thể cô lập bản sửa và bổ sung regression test; refactor rộng cần scope riêng nếu làm tăng rủi ro.
- **Một abstraction chỉ có một implementation, ổn định và không đơn giản hóa test/đổi dependency:** cân nhắc dùng class cụ thể; interface không tự tạo ra thiết kế tốt.
- **Hai đoạn code giống nhau về cú pháp nhưng thay đổi độc lập:** gom lại có thể tạo coupling sai, khiến thay đổi ở miền A phá miền B.

**Technical debt** có thể dùng để diễn tả chi phí tương lai của thiết kế hiện tại; không nên mặc định mọi smell là nợ cần thanh toán ngay. Hãy xét tác động, xác suất phải thay đổi và chi phí sửa.

## 8. Checklist phân tích một class Java

Khi review một class, có thể đi theo trình tự:

1. **Mục đích:** Viết một câu mô tả trách nhiệm của class. Nếu cần nối nhiều cụm “và” không liên quan, xem lại cohesion.
2. **Ranh giới:** Client dùng những method nào? Có dựa vào chi tiết nội bộ, thứ tự lời gọi, trạng thái ẩn không?
3. **Thay đổi:** Nêu 2–3 yêu cầu gần đây sẽ buộc class sửa. Chúng có cùng một lý do không?
4. **Phụ thuộc:** Class tự tạo dependency và thao tác IO ở mọi method, hay dependency được quản lý theo ranh giới rõ?
5. **Biểu diễn miền:** Có thể truyền các giá trị không hợp lệ bằng `String`/`int`/nhiều boolean không? Nên dùng enum/value object không?
6. **Lặp:** Logic nghiệp vụ có xuất hiện nơi khác? Là quy tắc dùng chung hay chỉ tình cờ giống nhau?
7. **Kiểm thử:** Có thể nêu một input và expected output/exception rõ ràng? Test hiện có có bảo vệ thay đổi không?
8. **Quy mô sửa:** Phép refactor nhỏ nhất là gì, và làm sao chứng minh hành vi vẫn được giữ?

Một báo cáo code review hữu ích nên có dạng: **“Trong thay đổi X, chúng ta phải sửa cùng quy tắc ở A/B/C; đây là nguy cơ sai lệch. Đề nghị chuyển quy tắc chung vào Y và giữ các test ranh giới Z.”** Cách này cụ thể hơn nhận xét “code xấu, phải áp dụng SOLID”.

## 9. Sai lầm thường gặp

**Áp dụng SOLID như một danh sách phải hoàn thành.** SRP, OCP, LSP, ISP và DIP là nguyên tắc định hướng, không yêu cầu mỗi method hay mỗi class đều có interface. Hãy bắt đầu từ vấn đề thực tế rồi chọn nguyên tắc liên quan.

**Refactor và đổi nghiệp vụ cùng lúc nhưng không tách phạm vi.** Nếu yêu cầu giá mới làm expected output thay đổi, ghi rõ đó là sửa chức năng. Refactor thuần túy phải giữ nguyên hợp đồng quan sát được.

**Thay thế test kết quả bằng test chi tiết triển khai.** Test assert method private được gọi mấy lần sẽ hỏng khi chia class, dù đầu ra đúng. Ưu tiên public behavior; chỉ test tương tác khi bản thân nó là hợp đồng có ý nghĩa (ví dụ chỉ được gửi thông báo một lần).

**Chỉ đo complexity trước và sau.** Complexity giảm không đủ nếu API khó dùng hơn, thời gian xử lý tăng mạnh hoặc hệ thống phát sinh state không an toàn. Cần test và xem tác động lên công việc thật.

**Nhầm thêm lớp với tăng khả năng mở rộng.** Nhiều interface không có biến thể thực tế có thể làm logic bị rải rác. Trừu tượng tốt phải làm ý định và hướng thay đổi dễ hiểu hơn.

**Bỏ qua tính tương thích.** Khi chuyển `String` thành `enum` trong core, vẫn có thể cần adapter giữ dữ liệu JSON/API hoặc cơ sở dữ liệu hiện tại. Refactor nội bộ không mặc nhiên cho phép phá API công khai.

## 10. Bài tập tự đánh giá

**Bài 1 — Nhận dạng:** Class `ReportManager` có các method `loadFromDatabase()`, `calculateTax()`, `renderHtml()` và `sendEmail()`. Hãy nêu những lý do thay đổi độc lập, cho biết có thể là smell nào và đề xuất ranh giới class thích hợp. Đừng kết luận chỉ dựa vào tên; xét xem những method có luôn thay đổi cùng nhau không.

**Bài 2 — Shotgun Surgery:** Đổi mã VAT phải sửa cùng một bảng mã ở ba service. Xác định một nơi duy nhất quản lý quy tắc VAT, thiết kế test cho những trường hợp đang được sử dụng và giải thích vì sao thay đổi mới chỉ nên sửa ở đó.

**Bài 3 — Primitive Obsession:** Chữ ký `createAccount(String type, String status, int level, boolean active)` cho phép tổ hợp trạng thái mâu thuẫn. Đề xuất enum hoặc value object nhằm loại bỏ các trạng thái bất hợp lệ. Nêu một trường hợp mà không cần tạo thêm class.

**Bài 4 — Refactor an toàn:** Trong `DesignSmellsDemo`, đổi tên `PricingRules` và tách phương thức `shippingFor`. Chạy lại bài test để xác nhận output không đổi. Sau đó cố tình đổi điều kiện miễn phí từ `>=` sang `>` và chỉ ra ca test bắt được lỗi ở mức 100.000 cent.

**Bài 5 — Ưu tiên:** Có hai vùng mã: A dài 800 dòng nhưng 18 tháng không có thay đổi; B dài 150 dòng nhưng mỗi tuần được sửa và thường gây regression. Hãy chọn vùng cần đánh giá trước, mô tả dữ liệu nào cần thu thập và nêu khi nào lựa chọn có thể khác.

### Tiêu chí hoàn thành

Bạn có thể chỉ ra **một biểu hiện cụ thể**, giải thích **vì sao nó gây chi phí hoặc rủi ro**, tìm bằng chứng từ hành vi và lịch sử thay đổi, chọn một phép refactor có phạm vi rõ, và viết kiểm thử chứng minh hợp đồng Java vẫn đúng sau thay đổi. Nhận diện code smell là bước chẩn đoán; **cải thiện thiết kế phải được xác nhận bằng kết quả**, không chỉ bằng việc số dòng hoặc số class thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-002">Đổi tên để làm rõ ý định</a>

<details>
<summary>Xem chi tiết</summary>

# Đổi tên để làm rõ ý định (Rename for Intent) trong Java OOP

## Vì sao một cái tên có thể làm code khó hiểu?

Xét một lời gọi trong chương trình bán hàng:

~~~java
long total = Calc.f(items, true);
~~~

Người đọc chưa biết `f` làm gì, `true` bật điều kiện nào, và `total` đã bao gồm những khoản tiền gì. Nếu muốn sửa code, họ phải mở phần cài đặt, lần theo các biến và tự suy luận nghiệp vụ.

Đổi tên để làm rõ ý định là một thao tác **refactoring**: thay tên class, method, field, biến hoặc hằng số bằng tên thể hiện chính xác vai trò của nó, trong khi **giữ nguyên hành vi quan sát được của chương trình**.

Ví dụ cùng ý tưởng với tên rõ ràng hơn:

~~~java
long payableAmountVnd =
        orderPriceCalculator.calculatePayableAmountVnd(
                orderLines, includeDeliveryFee);
~~~

Tên mới giúp trả lời ngay ba câu hỏi: giá trị là **số tiền phải trả**, đơn vị là **VND**, tham số boolean quyết định **có cộng phí giao hàng hay không**.

> Một tên tốt tiết lộ ý định; nó không chỉ mô tả kiểu dữ liệu. `orderLines` hữu ích hơn `list`, `remainingStock` hữu ích hơn `number`, và `isPaymentApproved` hữu ích hơn `flag`.

## Những tên nên thay đổi

| Vị trí trong OOP | Tên khó hiểu | Tên diễn đạt ý định | Vì sao rõ hơn? |
|---|---|---|---|
| Class | `DataManager` | `OrderRepository` | Chỉ rõ trách nhiệm lưu/truy xuất đơn hàng |
| Method | `process()` | `confirmOrder()` | Thể hiện thao tác nghiệp vụ |
| Method tính toán | `getTotal()` | `calculatePayableAmountVnd()` | Nêu hành động tính và ý nghĩa kết quả |
| Field | `n` | `quantity` | Chỉ rõ con số đại diện điều gì |
| Collection | `data` | `unpaidInvoices` | Nêu phần tử và tiêu chí của tập dữ liệu |
| Boolean | `flag` | `includeDeliveryFee` | Nêu ý nghĩa của `true` |
| Boolean trạng thái | `status` | `isOrderCancelled` | Đọc được như một điều kiện đúng/sai |
| Hằng số | `LIMIT` | `MAX_LOGIN_ATTEMPTS` | Nêu giới hạn áp dụng cho nghiệp vụ nào |
| Đơn vị | `timeout` | `connectionTimeoutMillis` | Tránh hiểu nhầm mili giây với giây |
| Thời điểm | `time` | `paymentExpiresAt` | Phân biệt mốc thời gian với khoảng thời gian |

Bảng là ví dụ có điều kiện: **tên đúng phải được suy ra từ hành vi thật**. Không thể đổi `DataManager` thành `OrderRepository` nếu class thực tế phụ trách nhiều loại dữ liệu. Cũng không nên đổi `getTotal()` thành `calculatePayableAmountVnd()` nếu method trả về số lượng sản phẩm thay vì tiền.

## Minh họa refactor có thể chạy: trước và sau đổi tên

Chương trình sau mô phỏng tính tổng tiền các dòng hàng, tùy chọn cộng phí giao hàng cố định 30.000 VND. Hai cách viết `Before` và `After` được giữ song song **chỉ để kiểm chứng trong bài học**; ở code thực tế, sau khi refactor ta giữ một triển khai và xóa tên cũ nếu không còn tương thích API cần duy trì.

Lưu đoạn sau thành **RenameForIntentDemo.java** (Java 11 trở lên):

~~~java
import java.util.List;

public final class RenameForIntentDemo {
    private static final long DELIVERY_FEE_VND = 30_000L;

    static final class OrderLine {
        private final long unitPriceVnd;
        private final int quantity;

        OrderLine(long unitPriceVnd, int quantity) {
            if (unitPriceVnd < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid order line");
            }
            this.unitPriceVnd = unitPriceVnd;
            this.quantity = quantity;
        }

        long calculateLineAmountVnd() {
            return Math.multiplyExact(unitPriceVnd, (long) quantity);
        }
    }

    // BEFORE: phải đọc phần thân mới đoán được tên và biến có ý nghĩa gì.
    static final class Before {
        static long f(List<OrderLine> x, boolean y) {
            long z = 0L;
            for (OrderLine i : x) {
                z = Math.addExact(z, i.calculateLineAmountVnd());
            }
            if (y) {
                z = Math.addExact(z, DELIVERY_FEE_VND);
            }
            return z;
        }
    }

    // AFTER: chỉ đổi các định danh; điều kiện, phép tính và thứ tự giữ nguyên.
    static final class After {
        static long calculatePayableAmountVnd(
                List<OrderLine> orderLines,
                boolean includeDeliveryFee) {

            long payableAmountVnd = 0L;
            for (OrderLine orderLine : orderLines) {
                payableAmountVnd = Math.addExact(
                        payableAmountVnd,
                        orderLine.calculateLineAmountVnd());
            }
            if (includeDeliveryFee) {
                payableAmountVnd = Math.addExact(
                        payableAmountVnd, DELIVERY_FEE_VND);
            }
            return payableAmountVnd;
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(
                    "Expected " + expected + ", actual " + actual);
        }
    }

    public static void main(String[] args) {
        List<OrderLine> orderLines = List.of(
                new OrderLine(20_000L, 2),
                new OrderLine(55_000L, 1)
        );
        // Tiền hàng: 20.000 * 2 + 55.000 = 95.000 VND.

        long oldWithoutDelivery = Before.f(orderLines, false);
        long newWithoutDelivery =
                After.calculatePayableAmountVnd(orderLines, false);

        long oldWithDelivery = Before.f(orderLines, true);
        long newWithDelivery =
                After.calculatePayableAmountVnd(orderLines, true);

        assertEquals(95_000L, oldWithoutDelivery);
        assertEquals(oldWithoutDelivery, newWithoutDelivery);

        assertEquals(125_000L, oldWithDelivery);
        assertEquals(oldWithDelivery, newWithDelivery);

        // Kiểm tra một trường hợp biên: không có dòng hàng.
        assertEquals(Before.f(List.of(), false),
                After.calculatePayableAmountVnd(List.of(), false));

        System.out.println("Rename preserves behavior: PASS");
    }
}
~~~

Chạy:

~~~bash
javac RenameForIntentDemo.java
java RenameForIntentDemo
~~~

Kết quả:

~~~text
Rename preserves behavior: PASS
~~~

Các thay đổi chính trong thân phương thức:

| Trước | Sau | Vai trò |
|---|---|---|
| `f` | `calculatePayableAmountVnd` | Phép tính tiền phải trả |
| `x` | `orderLines` | Tập các dòng hàng |
| `y` | `includeDeliveryFee` | Có cộng phí giao hàng |
| `z` | `payableAmountVnd` | Giá trị tích lũy và kết quả |
| `i` | `orderLine` | Phần tử đang duyệt |

Bản `Before` và `After` đều dùng đúng một hằng phí giao hàng, cùng `Math.addExact` và `Math.multiplyExact`. Vì vậy sự khác nhau đang được khảo sát là **cách đặt tên**, không bị trộn với thay đổi công thức hay xử lý tràn số. Ví dụ kiểm tra một số trường hợp cụ thể; trong dự án thật cần chạy toàn bộ test và các trường hợp biên quan trọng.

## Đổi tên thế nào để thực sự phản ánh ý định?

### 1. Tên phương thức nên thể hiện hành động hoặc câu hỏi

~~~java
// Mơ hồ: tên không thể hiện kết quả.
void handle(Order order);

// Rõ hơn nếu nghiệp vụ thực sự là xác nhận đơn.
void confirmOrder(Order order);

// Boolean method có thể đọc như một câu hỏi.
boolean isEligibleForFreeShipping(Order order);
~~~

Một method có tên `validateOrder` cần có hợp đồng rõ: trả `boolean`, trả danh sách lỗi, hay ném exception? Nếu method **ném lỗi khi không hợp lệ**, `requireValidOrder` đôi khi diễn đạt hành vi chuẩn xác hơn; nếu nó trả `boolean`, `isOrderValid` có thể dễ hiểu hơn. Chọn theo hợp đồng có thật.

Với JavaBean, JPA, thư viện serialization hoặc framework dựa vào getter, các tiền tố `get`, `is`, `set` đôi khi là một phần của quy ước kỹ thuật. Đổi tên vẫn phải phù hợp hợp đồng/framework.

### 2. Tên dữ liệu nên thể hiện ý nghĩa nghiệp vụ và đơn vị

~~~java
long delay = 5_000;                 // 5.000 giây hay mili giây?
long retryDelayMillis = 5_000;      // Mili giây.

int count = 3;                      // Đếm cái gì?
int failedLoginAttempts = 3;        // Số lần đăng nhập thất bại.
~~~

Trong code mới, kiểu miền giúp tránh nhầm hơn nữa, ví dụ `Duration.ofSeconds(5)`. Tuy nhiên, **đổi từ `long` sang `Duration` là thay đổi kiểu dữ liệu**, không còn là thao tác chỉ đổi tên; cần tách thành bước refactor khác và có kiểm thử tương ứng.

### 3. Tên boolean phải tránh hai cách hiểu

~~~java
boolean active;              // Ai đang hoạt động?
boolean userAccountActive;   // Tài khoản người dùng đang hoạt động.

boolean send;                // Gửi thứ gì, lúc nào?
boolean sendConfirmationEmail;
~~~

Đừng dùng các tên phủ định kép như `isNotDisabled` nếu có thể diễn đạt trực tiếp bằng `isEnabled`. Tuy nhiên, **đảo nghĩa một cờ** (`disabled` sang `enabled` rồi đảo giá trị) không phải rename đơn thuần, bởi `true` đã mang nghĩa khác; phải thay toàn bộ logic sử dụng và kiểm thử thật kỹ.

### 4. Cân bằng giữa độ ngắn và đủ ngữ cảnh

`customerId` tốt hơn `c`, nhưng `customerIdOfTheCustomerCurrentlyBeingProcessed` lại quá dài. Trong vòng lặp nhỏ, `index` hoặc `item` có thể đủ; ở một method xử lý cả `orders`, `invoices` và `receipts`, tên `currentOrder` rõ hơn `item`.

Phạm vi càng rộng (public class, public API, field sống lâu), tên càng cần tự giải thích. Trong biểu thức rất cục bộ, một tên ngắn nhưng rõ ngữ cảnh vẫn chấp nhận được.

## Quy trình đổi tên an toàn trong dự án Java

~~~mermaid
flowchart TD
    A["Hiểu mục đích và hợp đồng hiện tại"] --> B["Chọn tên diễn đạt nghiệp vụ đúng"]
    B --> C["Dùng IDE Rename Symbol để cập nhật tham chiếu"]
    C --> D["Xem diff: chỉ các định danh mong muốn thay đổi?"]
    D --> E["Compile và chạy unit tests"]
    E --> F{"Có API / dữ liệu / cấu hình phụ thuộc tên?"}
    F -- "Có" --> G["Kiểm tra consumer, reflection, serialization, migration"]
    F -- "Không" --> H["Hoàn tất refactor"]
    G --> H
~~~

1. **Đọc chính method/class được đổi tên:** xác định ý nghĩa, điều kiện, kết quả và side effect. Không đoán qua tên cũ.
2. **Chọn tên mới:** thể hiện đúng hành động/đối tượng/đơn vị/trạng thái, giữ nhất quán với ngôn ngữ miền của dự án.
3. **Dùng refactor Rename của IDE:** IntelliJ IDEA thường dùng **Shift+F6**, VS Code với Java tooling thường dùng **F2**. Tính năng này tìm các tham chiếu theo symbol, an toàn hơn thao tác tìm-thay-chuỗi toàn file.
4. **Kiểm tra diff và các nơi gọi:** constructor, method reference, override/implements, test, annotation, cấu hình và tài liệu liên quan. Xem cả phạm vi nhiều module nếu symbol được dùng chéo module.
5. **Chạy compile và kiểm thử:** so sánh hành vi trước/sau. Với API public hoặc storage, kiểm chứng các consumer và dữ liệu thực tế.
6. **Giữ thay đổi nhỏ:** commit refactor đổi tên riêng với sửa logic nghiệp vụ sẽ dễ review, truy lỗi và revert hơn (chỉ thực hiện commit khi quy trình dự án yêu cầu).

Nếu chỉ đổi tên biến cục bộ bằng công cụ hỗ trợ, rủi ro thường thấp. Đổi tên method public, property hoặc tên lớp dùng bên ngoài có thể cần kế hoạch tương thích/migration.

## Ranh giới quan trọng: tên trong source chưa chắc là tên trong runtime

| Trường hợp | Điều có thể hỏng | Hướng xử lý |
|---|---|---|
| Đổi method trong interface công khai | Dự án khác biên dịch lỗi; client nhị phân có thể lỗi khi liên kết | Cập nhật các consumer, version API hoặc duy trì adapter/deprecated method theo chính sách |
| Đổi field trong JSON payload | Contract của API đổi, client đọc field cũ thất bại | Giữ tên wire bằng annotation phù hợp (ví dụ `@JsonProperty`) hoặc làm migration có version |
| Đổi tên property của JPA entity | Có thể tác động mapping, query theo property, hoặc schema nếu đang dựa quy ước đặt tên | Kiểm tra `@Column`, derived queries, JPQL, migrations và integration test |
| Đổi tên `@Bean`, `@Qualifier` hoặc component | Dependency injection dựa tên có thể không tìm đúng bean | Kiểm tra cấu hình bean và khởi động context test |
| Tên method/field được dùng trong reflection | String literal cũ không tự cập nhật khi Rename Symbol | Tìm tất cả lời gọi reflection, tên trong config/test, cập nhật và chạy thử |
| Đổi tên file/class/package public | Build, import, resource/config hoặc caller có thể lệch | Refactor bằng IDE, xem cả test, build scripts và external references |

Ví dụ rename tên field Java nhưng vẫn bảo vệ contract JSON công khai (khi dự án có Jackson):

~~~java
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PaymentResponse {
    private long payableAmountVnd;

    @JsonProperty("total")
    public long getPayableAmountVnd() {
        return payableAmountVnd;
    }
}
~~~

Trong ví dụ này, tên JSON `"total"` được gắn **trên getter** để bảo vệ tên field khi serialize. Đoạn trích chỉ minh họa chiều đọc ra; muốn deserialize đúng contract cần constructor/setter hoặc cấu hình creator phù hợp. Đây chỉ là minh họa mapping: nếu ứng dụng thực tế có setter, constructor, record, chiến lược serialization hoặc visibility rule khác, hãy kiểm chứng JSON input/output thực tế bằng test. Import Jackson ở đoạn này thuộc dự án có dependency tương ứng, **không nằm trong file Java chạy độc lập phía trên**.

Với Java, đổi tên method được `@Override` còn phải đồng bộ với hợp đồng của interface/superclass; nếu interface thuộc thư viện bên ngoài, không thể tùy tiện đổi signature mà vẫn bảo đảm implement đúng.

## Khi nào tên mới chưa đủ?

Có lúc vấn đề lớn hơn tên gọi. Ví dụ:

~~~java
void processOrder(Order order);
~~~

Nếu method vừa kiểm tra tồn kho, thanh toán, cập nhật đơn và gửi email, đổi thành `processValidatedOrderAndChargeAndSendEmail` vẫn không làm trách nhiệm rõ ràng. Khi đó nên cân nhắc **Extract Method**, **Extract Class** hoặc thiết kế collaborator theo trách nhiệm, mỗi bước có tên riêng. Đó là những refactoring khác, cần được đánh giá và kiểm thử độc lập.

Tương tự, `calculatePayableAmountVnd(orderLines, true)` vẫn khiến người đọc tại **call site** phải tự nhớ ý nghĩa của `true`. Đặt tên tham số tốt giúp phần khai báo, nhưng Java không hỗ trợ named argument. Nếu bài toán phức tạp, có thể cân nhắc method tường minh hơn hoặc enum cho lựa chọn nghiệp vụ. Đổi chữ ký/enum là bước thay đổi API riêng, không nên ngụy trang thành rename.

## Bài tập tự kiểm tra

**Bài 1 — Tìm tên ý định:** đề xuất tên đúng cho các đoạn sau, sau khi xác minh logic thật:

~~~java
boolean a = invoice.getDueDate().isBefore(today) && !invoice.isPaid();
int n = cart.getItems().size();
long t = priceCents * quantity;
~~~

Gợi ý: `isInvoiceOverdue` (nếu business rule cho rằng quá hạn và chưa thanh toán), `cartItemCount`, `lineAmountCents`.

**Bài 2 — Giữ nguyên hành vi:** trong file `RenameForIntentDemo.java`, đổi method `After.calculatePayableAmountVnd` thành `calculateOrderPayableAmountVnd` bằng Rename Symbol, kiểm tra lời gọi tại `main` được cập nhật, rồi chạy lại chương trình. Console vẫn cần báo `PASS`.

**Bài 3 — Nhận diện rủi ro:** có API trả JSON với field `"total"`. Đề xuất cách đổi tên Java field thành `payableAmountVnd` mà client hiện tại vẫn nhận `"total"`, và viết bài test serialization nếu dự án có Jackson.

**Tự đánh giá:** giải thích vì sao đổi `flag` thành `includeDeliveryFee` là rename, còn đổi `boolean includeDeliveryFee` thành `ShippingMethod shippingMethod` là thiết kế lại hợp đồng; nêu bài test nào chứng minh không thay đổi tổng tiền.

Đổi tên đạt chất lượng khi người đọc hiểu **đối tượng làm gì, dữ liệu có nghĩa gì, điều kiện đại diện cho điều gì** ngay tại vị trí sử dụng; mã vẫn chạy cùng hành vi mong đợi, và các hợp đồng ngoài source được xem xét đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-003">Tách phương thức và tách lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tách phương thức và tách lớp (Extract Method / Extract Class) trong Java OOP

## 1. Vì sao cần tách?

Khi một phương thức ngày càng dài, nó thường làm nhiều việc: kiểm tra dữ liệu, tính toán, cập nhật trạng thái, tạo thông báo, gọi dịch vụ ngoài. Khi một lớp chứa quá nhiều nhóm trách nhiệm khác nhau, chỉ một thay đổi nhỏ cũng buộc ta đọc, sửa và kiểm thử cả lớp.

**Tái cấu trúc (refactoring)** là cải thiện cấu trúc bên trong mà **giữ nguyên hành vi quan sát được**. Hai kỹ thuật cơ bản:

- **Extract Method (Tách phương thức):** chọn một nhóm câu lệnh có mục đích rõ ràng, đặt chúng vào phương thức có tên mô tả ý định, rồi thay nhóm câu lệnh cũ bằng lời gọi phương thức đó.
- **Extract Class (Tách lớp):** chuyển một nhóm dữ liệu và hành vi có trách nhiệm riêng sang lớp mới; lớp ban đầu giao việc cho lớp mới thông qua giao diện rõ ràng.

Hai kỹ thuật có thể kết hợp: đầu tiên tách các bước của một phương thức dài, sau đó phát hiện một số phương thức thuộc trách nhiệm của lớp khác và chuyển chúng sang lớp đó.

**Kết quả mong đợi:** code dễ đọc hơn, trách nhiệm rõ hơn, dễ tái sử dụng và kiểm thử; các giá trị trả về, exception, tác dụng phụ và quy tắc nghiệp vụ đã cam kết vẫn tương đương.

| Tiêu chí | Extract Method | Extract Class |
|---|---|---|
| Dấu hiệu | Một phương thức thực hiện nhiều bước độc lập về ý nghĩa | Một lớp chứa nhiều nhóm lý do phải thay đổi |
| Thao tác | Tách đoạn code sang method có tên rõ | Chuyển field/method liên quan sang object mới |
| Mức thay đổi | Chủ yếu tổ chức nội bộ của một lớp | Phân chia trách nhiệm giữa các đối tượng |
| Ví dụ | Tách calculateSubtotal, applyDiscount | Tách PricingPolicy và ReceiptFormatter khỏi CheckoutService |
| Lỗi hay gặp | Quá nhiều method một dòng, truyền quá nhiều tham số, đổi thứ tự side effect | Tạo lớp rỗng nghĩa, làm lộ dữ liệu, circular dependency, phân mảnh quá mức |

## 2. Dấu hiệu nên bắt đầu

Một số tín hiệu hữu ích (không phải quy tắc tuyệt đối):

1. Bạn phải đọc rất nhiều dòng mới hiểu một thao tác nghiệp vụ.
2. Trong cùng một hàm, các đoạn code có thể được mô tả bằng những tên mục đích khác nhau: *validate → calculate → format → persist*.
3. Một lớp đổi vì nhiều nguyên nhân không liên quan: thay quy tắc giá **hoặc** thay cách trình bày hóa đơn.
4. Nhiều phương thức cùng dùng một tập field và hầu như không cần phần còn lại của lớp; đó là một nhóm có độ gắn kết (cohesion) riêng.
5. Kiểm thử một quy tắc nhỏ buộc phải khởi tạo nhiều dependency không liên quan.

Độ dài chỉ là **tín hiệu**. Một method 40 dòng mô tả một thuật toán tuyến tính, rõ ràng có thể dễ bảo trì hơn 10 method vụn. Mục tiêu là tăng **cohesion**, giảm coupling, giữ ý nghĩa nghiệp vụ và đường dữ liệu rõ ràng.

## 3. Tách phương thức: cách làm từng bước

Giả sử hàm checkout đang chứa tất cả logic đơn hàng:

~~~java
// Phác thảo cấu trúc ban đầu:
CheckoutResult checkout(Order order) {
    // (1) Kiểm tra đơn hàng
    // (2) Cộng giá trị từng dòng
    // (3) Tính giảm giá VIP
    // (4) Tính phí vận chuyển
    // (5) Định dạng chuỗi biên nhận
    // (6) Trả kết quả
}
~~~

Ta có thể đặt tên cụ thể cho các bước thuần tính toán:

~~~java
// Phác thảo sau Extract Method:
CheckoutResult checkout(Order order) {
    long subtotal = calculateSubtotal(order);
    long discount = calculateDiscount(subtotal, order.vip);
    long payable = Math.subtractExact(subtotal, discount);
    long shipping = calculateShipping(payable);
    long total = Math.addExact(payable, shipping);
    String receipt = formatReceipt(subtotal, discount, shipping, total);
    return new CheckoutResult(
            subtotal, discount, shipping, total, receipt);
}
~~~

Các method như calculateDiscount hoặc calculateShipping có thể là private và có đầu vào/đầu ra rõ ràng. Nếu một đoạn cần dùng hoặc sửa quá nhiều biến cục bộ, hãy xác định chính xác dữ liệu cần truyền/giá trị cần trả về, thay vì biến chúng thành field chung để có thể truy cập từ mọi nơi.

Quy trình Extract Method an toàn:

1. Chạy test hoặc xác định một bộ trường hợp đầu vào/đầu ra hiện tại để làm mốc.
2. Chọn khối lệnh có mục đích độc lập; đặt tên theo **việc nó làm**, không chỉ theo thao tác kỹ thuật.
3. Phân tích biến được đọc, biến được cập nhật, giá trị trả về, exception và side effect.
4. Tạo method; chuyển chính xác khối lệnh, truyền dependency cần thiết bằng tham số; thay code cũ bằng lời gọi.
5. Biên dịch và chạy lại test ngay sau mỗi bước nhỏ.
6. Tiếp tục tách nếu mục đích mới rõ hơn, dừng nếu việc tách chỉ làm code khó lần theo.

Ví dụ: tên **calculateShipping** diễn đạt mục đích tốt hơn **processStep4**. Tránh truyền nguyên đối tượng lớn chỉ để dùng một field, nếu truyền một giá trị cụ thể khiến hợp đồng dễ hiểu hơn; nhưng cũng tránh danh sách 10 tham số bằng cách chọn một parameter object thật sự có ý nghĩa.

## 4. Tách lớp: phân biệt trách nhiệm

Sau khi tách các method, giả sử CheckoutService có hai nhóm:

- **Định giá:** subtotal, giảm giá VIP, phí vận chuyển, tổng tiền.
- **Trình bày:** định dạng biên nhận để hiển thị.

Chúng thay đổi vì các lý do khác nhau. Ta chuyển nhóm thứ nhất sang **PricingPolicy**, nhóm thứ hai sang **ReceiptFormatter**, giữ **CheckoutService** làm lớp điều phối.

~~~mermaid
flowchart TB
    subgraph BEFORE["Trước"]
        A["CheckoutService: kiểm tra + tính giá + định dạng"]
    end
    subgraph AFTER["Sau"]
        B["CheckoutService: điều phối"] --> C["PricingPolicy: quy tắc tiền"]
        B --> D["ReceiptFormatter: trình bày"]
    end
~~~

Đây là ứng dụng của **Single Responsibility Principle (SRP)**: mỗi lớp nên có một nhóm lý do thay đổi rõ ràng. SRP không có nghĩa mỗi lớp chỉ có đúng một method. Việc tách cũng khuyến khích phụ thuộc vào abstraction khi có nhu cầu thay thế implementation, nhưng không bắt buộc tạo interface cho mọi lớp.

## 5. Chương trình Java hoàn chỉnh: so sánh trước/sau refactor

Ví dụ giữ cùng yêu cầu nghiệp vụ cho cả hai phiên bản:

- Một đơn hàng có ít nhất một dòng. SKU không rỗng, số lượng dương, đơn giá không âm.
- **subtotal** là tổng của đơn giá nhân số lượng; dùng số nguyên VND để tránh sai lệch do float/double.
- Nếu khách VIP và subtotal từ **500.000 VND**, giảm **10% subtotal**, làm tròn xuống theo chia số nguyên.
- Phí vận chuyển là **0 VND** nếu số tiền sau giảm giá từ **700.000 VND**, ngược lại **30.000 VND**.
- **total = subtotal − discount + shipping**.
- Biên nhận có định dạng chuỗi cố định như ví dụ.
- Các phép toán dùng Math.addExact, multiplyExact, subtractExact để lỗi tràn số không âm thầm thay đổi kết quả.

Lưu toàn bộ vào **OrderRefactoringDemo.java**. Đây là chương trình tự chạy với **Java 11+**, không cần dependency ngoài. Phiên bản LegacyCheckout gom nghiệp vụ trong một method; phiên bản sau refactor chia thành PricingPolicy, ReceiptFormatter và CheckoutService.

~~~java
import java.util.List;
import java.util.Objects;

public class OrderRefactoringDemo {
    static final long VIP_MIN_SUBTOTAL = 500_000L;
    static final long FREE_SHIPPING_MIN = 700_000L;
    static final long SHIPPING_FEE = 30_000L;

    static final class Item {
        final String sku;
        final long unitPriceVnd;
        final int quantity;

        Item(String sku, long unitPriceVnd, int quantity) {
            this.sku = sku;
            this.unitPriceVnd = unitPriceVnd;
            this.quantity = quantity;
        }
    }

    static final class Order {
        final List<Item> items;
        final boolean vip;

        Order(List<Item> items, boolean vip) {
            this.items = items;
            this.vip = vip;
        }
    }

    static final class CheckoutResult {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;
        final String receipt;

        CheckoutResult(long subtotal, long discount, long shipping,
                       long total, String receipt) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
            this.receipt = receipt;
        }
    }

    // TRƯỚC: một phương thức phải xử lý nhiều trách nhiệm.
    static final class LegacyCheckout {
        CheckoutResult checkout(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.unitPriceVnd < 0 || item.quantity <= 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineTotal = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineTotal);
            }

            long discount = order.vip && subtotal >= VIP_MIN_SUBTOTAL
                    ? subtotal / 10 : 0L;
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = payable >= FREE_SHIPPING_MIN
                    ? 0L : SHIPPING_FEE;
            long total = Math.addExact(payable, shipping);

            String receipt = "subtotal=" + subtotal
                    + ",discount=" + discount
                    + ",shipping=" + shipping
                    + ",total=" + total;

            return new CheckoutResult(
                    subtotal, discount, shipping, total, receipt);
        }
    }

    // KẾT QUẢ TÍNH TOÁN: dữ liệu cho lớp trình bày.
    static final class Pricing {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;

        Pricing(long subtotal, long discount, long shipping, long total) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
        }
    }

    // Extract Class: gom các quy tắc định giá liên quan.
    static final class PricingPolicy {
        Pricing calculate(Order order) {
            long subtotal = calculateSubtotal(order);
            long discount = calculateDiscount(subtotal, order.vip);
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = calculateShipping(payable);
            long total = Math.addExact(payable, shipping);

            return new Pricing(subtotal, discount, shipping, total);
        }

        // Extract Method: đọc và kiểm tra dữ liệu, rồi cộng tiền.
        private long calculateSubtotal(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.unitPriceVnd < 0 || item.quantity <= 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineTotal = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineTotal);
            }
            return subtotal;
        }

        private long calculateDiscount(long subtotal, boolean vip) {
            return vip && subtotal >= VIP_MIN_SUBTOTAL
                    ? subtotal / 10 : 0L;
        }

        private long calculateShipping(long payable) {
            return payable >= FREE_SHIPPING_MIN ? 0L : SHIPPING_FEE;
        }
    }

    // Extract Class: cách trình bày thay đổi độc lập với quy tắc giá.
    static final class ReceiptFormatter {
        String format(Pricing pricing) {
            return "subtotal=" + pricing.subtotal
                    + ",discount=" + pricing.discount
                    + ",shipping=" + pricing.shipping
                    + ",total=" + pricing.total;
        }
    }

    // SUT sau refactor: đọc như một quy trình điều phối.
    static final class CheckoutService {
        private final PricingPolicy pricingPolicy;
        private final ReceiptFormatter formatter;

        CheckoutService(PricingPolicy pricingPolicy,
                        ReceiptFormatter formatter) {
            this.pricingPolicy = Objects.requireNonNull(pricingPolicy);
            this.formatter = Objects.requireNonNull(formatter);
        }

        CheckoutResult checkout(Order order) {
            Pricing pricing = pricingPolicy.calculate(order);
            return new CheckoutResult(
                    pricing.subtotal,
                    pricing.discount,
                    pricing.shipping,
                    pricing.total,
                    formatter.format(pricing));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEquivalent(
            LegacyCheckout oldVersion,
            CheckoutService newVersion,
            Order input,
            long expectedTotal) {
        CheckoutResult oldResult = oldVersion.checkout(input);
        CheckoutResult newResult = newVersion.checkout(input);

        check(oldResult.subtotal == newResult.subtotal, "subtotal changed");
        check(oldResult.discount == newResult.discount, "discount changed");
        check(oldResult.shipping == newResult.shipping, "shipping changed");
        check(oldResult.total == newResult.total, "total changed");
        check(oldResult.receipt.equals(newResult.receipt),
                "receipt changed");
        check(newResult.total == expectedTotal, "incorrect total");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        LegacyCheckout oldVersion = new LegacyCheckout();
        CheckoutService newVersion =
                new CheckoutService(new PricingPolicy(),
                                    new ReceiptFormatter());

        // subtotal = 600.000; VIP giảm 60.000;
        // sau giảm 540.000; phí 30.000 => 570.000.
        Order vipOrder = new Order(List.of(
                new Item("BOOK", 250_000L, 2),
                new Item("PEN", 100_000L, 1)), true);
        checkEquivalent(oldVersion, newVersion, vipOrder, 570_000L);

        // Không VIP: 100.000 + phí 30.000 = 130.000.
        Order normalOrder = new Order(
                List.of(new Item("PEN", 100_000L, 1)), false);
        checkEquivalent(oldVersion, newVersion, normalOrder, 130_000L);

        // VIP: subtotal 800.000, giảm 80.000,
        // sau giảm 720.000, miễn phí vận chuyển.
        Order freeShippingOrder = new Order(
                List.of(new Item("BOOK", 400_000L, 2)), true);
        checkEquivalent(oldVersion, newVersion,
                freeShippingOrder, 720_000L);

        // Cả hai phiên bản từ chối quantity không hợp lệ.
        Order invalid = new Order(
                List.of(new Item("BOOK", 100_000L, 0)), false);
        expectInvalid(() -> oldVersion.checkout(invalid));
        expectInvalid(() -> newVersion.checkout(invalid));

        System.out.println("3 equivalent cases + invalid input: PASS");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac OrderRefactoringDemo.java
java OrderRefactoringDemo
~~~

Đầu ra kỳ vọng:

~~~text
3 equivalent cases + invalid input: PASS
~~~

Ví dụ này **không chỉ so sánh tổng tiền**: nó so sánh subtotal, discount, shipping, total và cả chuỗi receipt; đồng thời xác minh hai phiên bản đều từ chối cùng một loại input sai. Khi refactor có tác dụng phụ hoặc làm việc với hệ thống ngoài, cần bổ sung kiểm thử những hiệu ứng quan sát được ở ranh giới tương ứng.

**Giới hạn ví dụ:** Chương trình mô phỏng việc tính giá thuần trong bộ nhớ, chưa có repository, transaction, gửi email hay số lượng lớn. Trong ứng dụng thật, kiểm thử thêm trạng thái database, thứ tự side effect có ý nghĩa, tính an toàn luồng và lỗi tích hợp trước khi kết luận hành vi được giữ nguyên.

## 6. Nhận diện trách nhiệm sau khi tách

| Thành phần | Trách nhiệm hiện tại | Lý do có thể thay đổi |
|---|---|---|
| Order / Item | Dữ liệu đơn hàng và dòng hàng | Hợp đồng dữ liệu |
| PricingPolicy | Kiểm tra đầu vào và tính giá, giảm giá, phí | Chính sách bán hàng |
| Pricing | Kết quả tính toán đã xác định | Cấu trúc dữ liệu giá |
| ReceiptFormatter | Chuyển kết quả định giá thành chuỗi | Yêu cầu hiển thị |
| CheckoutService | Gọi PricingPolicy rồi ReceiptFormatter | Luồng nghiệp vụ phối hợp |

Việc truyền PricingPolicy và ReceiptFormatter qua constructor là **dependency injection** đơn giản. Không bắt buộc phải dùng Spring. Khi cần nhiều biến thể giá/định dạng, có thể cân nhắc interface tương ứng; với một implementation duy nhất, class cụ thể đã đủ rõ.

**Khi nào giữ một method trong lớp cũ?** Nếu nó sử dụng phần lớn trạng thái và nằm sát trách nhiệm cốt lõi của lớp. Extract Class nên chuyển cả nhóm hành vi và dữ liệu thật sự gắn nhau, thay vì chỉ tạo một lớp "Utils" chứa nhiều static method không liên quan.

## 7. Những rủi ro cần kiểm tra trước khi gọi là refactor thành công

### Đổi thứ tự xử lý hoặc hiệu ứng ngoài

Trong ví dụ thuần tính toán, chuyển method có ít rủi ro. Nhưng đổi thứ tự *thanh toán → trừ kho → ghi log/gửi email* có thể làm thay đổi business rule. Nếu refactor dính transaction, lock, retry hoặc event, phải giữ nguyên các bất biến và ranh giới transaction đã cam kết.

### Đổi thời điểm ném exception

Khi chuyển validation sang lớp khác, lỗi có thể xuất hiện sớm hoặc muộn hơn. Ví dụ nếu dòng đầu gây tràn số và dòng sau không hợp lệ, đưa validation toàn giỏ hàng lên đầu sẽ đổi exception quan sát được. Với hệ thống có contract về exception hoặc side effect, giữ đúng thứ tự và viết test thích hợp trước khi di chuyển logic.

### Làm lộ chi tiết dữ liệu

Tránh biến toàn bộ field private thành public chỉ để lớp mới truy cập. Có thể dùng đối tượng giá trị bất biến, method truy vấn có ý nghĩa, hoặc chuyển ownership của trạng thái sang lớp chuyên trách.

### Tạo phụ thuộc vòng tròn

CheckoutService phụ thuộc PricingPolicy là hợp lý; nếu PricingPolicy lại phải gọi ngược CheckoutService, trách nhiệm đang bị chia thiếu rõ ràng. Thiết kế hướng phụ thuộc một chiều, tránh để các lớp nắm tham chiếu lẫn nhau chỉ để truy cập helper.

### Tách quá nhỏ

Một lớp có đúng một dòng chuyển tiếp sang lớp khác chưa chắc hữu ích. Nếu mỗi phép cộng là một lớp hoặc một method thì độ phân mảnh sẽ tăng. Tách theo ý nghĩa nghiệp vụ, cohesion và lý do thay đổi, không theo mục tiêu tối đa số file.

### Vô tình thay đổi hiệu năng hoặc luồng dữ liệu

Di chuyển method có thể tạo copy bộ sưu tập, cache khác vòng đời hoặc gọi dependency nhiều hơn. Nếu hiệu năng, identity, thứ tự, khả năng song song hoặc idempotency là phần hợp đồng, hãy có phép kiểm tra tương ứng. Chú ý khác biệt Java reference, đối tượng mutable và data race khi chuyển state sang lớp dùng chung.

## 8. Test trước và sau khi refactor

Một trình tự thực tế:

1. **Characterization tests:** ghi lại hành vi hiện tại của code cũ trong các kịch bản quan trọng, đặc biệt khi chưa có test.
2. **Extract Method nhỏ:** chuyển một khối lệnh và chạy lại các test liên quan.
3. **Extract Class theo trách nhiệm:** di chuyển phương thức/field, cập nhật dependency trong constructor và caller.
4. **Kiểm tra tương đương:** kết quả, exception, trạng thái và các side effect có ý nghĩa.
5. **Chạy unit + integration test:** những đường nối với database, API, transaction phải được kiểm chứng thật khi cần.
6. **Review phạm vi diff:** những thay đổi chính sách nghiệp vụ nên được tách khỏi refactor để dễ phát hiện regression.

Đối với code gốc có lỗi đã biết, đừng vô tình đổi hành vi khi tuyên bố "refactor thuần". Có thể tạo một thay đổi riêng để sửa lỗi với test mô tả hành vi mong đợi mới.

Ví dụ, nếu chính sách giá phải đổi từ giảm 10% thành 15%, hãy thực hiện việc đó **sau** một lần refactor bảo toàn hành vi, có test độc lập. Khi một commit vừa tách lớp vừa thay quy tắc, nếu test đỏ khó xác định nguyên nhân.

## 9. Bài tập thực hành

**Bài 1 — Extract Method:** Trong LegacyCheckout, tách riêng tính subtotal, giảm giá, phí vận chuyển và định dạng. Sau mỗi bước chạy lại cùng bộ test; giải thích đầu vào/đầu ra của từng method.

**Bài 2 — Extract Class:** Chuyển riêng trách nhiệm ReceiptFormatter và kiểm tra việc đổi định dạng không đòi sửa PricingPolicy. Phân tích vì sao đây là sự cải thiện cohesion.

**Bài 3 — Ca biên:** Bổ sung test subtotal đúng 500.000 cho VIP, payable đúng 700.000, đơn rỗng, giá âm, số lượng 0 và số tiền vượt giới hạn long. Chạy cả hai phiên bản để kiểm tra tương đương.

**Bài 4 — Phát hiện thay đổi hành vi:** Cố tình tính phí vận chuyển trên subtotal trước giảm giá thay vì payable sau giảm giá. Viết một input làm hai kết quả khác nhau để test bắt regression.

**Bài 5 — Thiết kế lại:** Nếu có chính sách giảm giá cho nhiều quốc gia hoặc nhiều loại khách, cân nhắc interface DiscountPolicy. Giải thích khi nào chiến lược thay thế (Strategy) tốt hơn việc tiếp tục thêm nhiều nhánh if trong PricingPolicy, và khi nào interface là quá sớm.

## 10. Kết luận

**Extract Method** đặt tên cho các bước có mục đích rõ ràng để method chính dễ đọc hơn. **Extract Class** gom dữ liệu và hành vi theo trách nhiệm, để mỗi phần thay đổi độc lập hơn. Cả hai là tái cấu trúc chỉ khi **hợp đồng quan sát được vẫn giữ nguyên**. Tách từng bước nhỏ, đặt tên theo nghiệp vụ, kiểm tra trước/sau và dừng khi sự phân chia giúp người đọc hiểu hệ thống tốt hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-004">Di chuyển hành vi về đối tượng chịu trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

# Di chuyển hành vi về đối tượng chịu trách nhiệm trong Java OOP

## 1. Bài toán: dữ liệu ở một lớp, phép xử lý lại nằm ở lớp khác

Trong lập trình hướng đối tượng, mỗi đối tượng nên chịu trách nhiệm về những hành vi gắn trực tiếp với trạng thái và quy tắc mà nó nắm giữ. Một dấu hiệu thiết kế chưa tốt xuất hiện khi lớp A thường xuyên lấy dữ liệu của lớp B, rồi tự thực hiện phần lớn phép tính hay quyết định vốn thuộc về B.

Ví dụ: dịch vụ thanh toán phải đi qua từng dòng hàng, lấy đơn giá và số lượng, tự nhân để ra thành tiền, cộng tổng rồi áp dụng giảm giá của đơn hàng. Dịch vụ này đang biết quá nhiều về cấu trúc bên trong của đơn hàng.

Mùi mã này thường được gọi là **Feature Envy** (một phương thức quá quan tâm đến dữ liệu của đối tượng khác). Cách tái cấu trúc phù hợp trong nhiều trường hợp là **Move Method — chuyển phương thức/hành vi tới đối tượng sở hữu thông tin và quy tắc liên quan**. Đôi khi cần chia một phương thức lớn thành nhiều hành vi nhỏ rồi chuyển chúng tới những đối tượng khác nhau.

Mục tiêu là:
- Đưa tính toán thành tiền về **OrderLine**, vì nó biết đơn giá và số lượng.
- Đưa tổng tiền và chính sách giảm giá cố định của đơn về **Order**, vì nó sở hữu các dòng hàng và thông tin ưu đãi.
- Giữ thao tác gọi cổng thanh toán trong **CheckoutService**, vì đây là trách nhiệm phối hợp với hệ thống bên ngoài.
- Duy trì kết quả, lỗi và hợp đồng được kiểm thử trong quá trình refactor.

**Không có quy tắc "mọi phương thức phải nằm trong lớp giữ dữ liệu".** Chọn nơi đặt hành vi dựa trên trách nhiệm nghiệp vụ, tính kết dính, mức phụ thuộc và khả năng thay đổi.

## 2. Nhìn vào thiết kế trước khi refactor

Đoạn mã minh họa dưới đây dùng hai lớp dữ liệu kiểu cũ. Phần tính tiền nằm hoàn toàn trong một dịch vụ:

~~~java
import java.util.List;

record LegacyLine(String sku, long priceVnd, int quantity) {}

record LegacyOrder(
        String id,
        boolean vip,
        List<LegacyLine> lines) {}

final class LegacyCheckoutService {
    long calculatePayable(LegacyOrder order) {
        long subtotal = 0;

        for (LegacyLine line : order.lines()) {
            subtotal += line.priceVnd() * line.quantity();
        }

        if (order.vip()) {
            return subtotal - subtotal / 10;
        }
        return subtotal;
    }
}
~~~

Đây là mã lược giản để thấy vị trí trách nhiệm, chưa có xác thực dữ liệu hoặc kiểm tra tràn số. Logic nghiệp vụ minh họa: khách VIP giảm **10% của tổng tiền**, phần giảm làm tròn xuống theo đơn vị VND.

Vấn đề không nằm ở cú pháp vòng lặp. Vấn đề là LegacyCheckoutService biết cách định giá từng dòng hàng, biết cách cộng đơn và biết quy tắc ưu đãi. Khi OrderLine thay đổi cách tính thành tiền hoặc đơn hàng thêm quy tắc mới, lớp dịch vụ phải sửa dù vai trò chính của nó chỉ là điều phối checkout.

Một dấu hiệu thường gặp là chuỗi truy vấn:

~~~text
service → order.lines() → line.priceVnd()
                        → line.quantity()
                        → tính toán ngoài đối tượng dữ liệu
~~~

Thiết kế mong muốn:

~~~text
CheckoutService → Order.payableVnd()
                      ├── Order.subtotalVnd()
                      │      └── OrderLine.subtotalVnd()
                      └── Quy tắc giảm giá của đơn
~~~

Điểm khác biệt: lớp gọi yêu cầu **đối tượng làm việc của nó**, thay vì lấy hết dữ liệu để tự làm thay. Tư tưởng này thường được diễn đạt bằng nguyên tắc *Tell, Don't Ask*.

## 3. Xác định đúng chủ sở hữu hành vi

| Hành vi | Đặt ở đâu? | Vì sao? |
| --- | --- | --- |
| Nhân đơn giá với số lượng | OrderLine | Cả hai dữ liệu và bất biến của dòng hàng nằm tại đây |
| Cộng các dòng hàng | Order | Order sở hữu danh sách OrderLine |
| Tính số tiền phải trả theo ưu đãi VIP cố định của đơn | Order | Quy tắc gắn với trạng thái VIP của đơn trong ví dụ |
| Gọi API thu tiền | CheckoutService / PaymentGateway | Có tác dụng phụ, liên quan quy trình và hạ tầng |
| In hóa đơn dạng PDF hoặc JSON | Formatter / Presenter / Serializer | Quy tắc biểu diễn bên ngoài, không phải nghiệp vụ tính tiền |
| Tính khuyến mãi thay đổi theo chiến dịch/coupon phức tạp | DiscountPolicy hoặc PricingService chuyên trách | Quy tắc có vòng đời và nguồn dữ liệu độc lập với Order |

**Câu hỏi quyết định:** Đối tượng nào phải biết ít nhất về bên ngoài nhưng vẫn có đủ dữ liệu và trách nhiệm để thực hiện hành vi đúng? Nếu hành vi cần dữ liệu của nhiều aggregate hay gọi I/O, một domain service/application service có thể hợp lý hơn phương thức của entity.

~~~mermaid
classDiagram
    class OrderLine {
        -String sku
        -long unitPriceVnd
        -int quantity
        +subtotalVnd() long
    }
    class Order {
        -String id
        -boolean vip
        -List~OrderLine~ lines
        +subtotalVnd() long
        +payableVnd() long
    }
    class CheckoutService {
        -PaymentGateway gateway
        +checkout(Order) boolean
    }
    class PaymentGateway {
        <<interface>>
        +charge(String, long) boolean
    }
    Order "1" *-- "1..*" OrderLine
    CheckoutService --> Order : yêu cầu số tiền
    CheckoutService --> PaymentGateway : yêu cầu thanh toán
~~~

Ở đây mối quan hệ Order–OrderLine là quan hệ sở hữu về mặt mô hình. Order giữ một bản sao bất biến của danh sách để bên ngoài không thay đổi nội dung thông qua danh sách đã truyền vào.

## 4. Ví dụ sau refactor: chương trình Java 17 chạy được

Ví dụ dùng JDK 17, Maven và JUnit 5. Mỗi lớp public nằm trong tệp tương ứng.

~~~text
move-behavior-oop/
├── pom.xml
└── src/
    ├── main/java/vn/example/
    │   ├── OrderLine.java
    │   ├── Order.java
    │   ├── PaymentGateway.java
    │   ├── CheckoutService.java
    │   └── Main.java
    └── test/java/vn/example/
        ├── OrderTest.java
        └── CheckoutServiceTest.java
~~~

### 4.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>vn.example</groupId>
    <artifactId>move-behavior-oop</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Đưa phép tính dòng hàng vào OrderLine

**src/main/java/vn/example/OrderLine.java**

~~~java
package vn.example;

/** Một dòng hàng có số lượng dương và đơn giá không âm. */
public record OrderLine(String sku, long unitPriceVnd, int quantity) {
    public OrderLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (unitPriceVnd < 0) {
            throw new IllegalArgumentException(
                    "unitPriceVnd must not be negative");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
    }

    /** Thành tiền theo VND; báo lỗi nếu vượt miền long. */
    public long subtotalVnd() {
        return Math.multiplyExact(unitPriceVnd, (long) quantity);
    }
}
~~~

Những chi tiết đáng chú ý:

- Record giữ trạng thái bất biến (các thành phần của record là final).
- Quy tắc quantity > 0, unitPriceVnd >= 0 được kiểm soát tại nơi tạo dòng hàng.
- OrderLine trả lời được câu hỏi "dòng hàng này có thành tiền bao nhiêu?" mà không cần lớp ngoài lấy hai trường rồi nhân.
- Math.multiplyExact ném ArithmeticException khi kết quả vượt miền long; toán tử nhân thông thường có thể tràn số âm/dương âm thầm.

Ta cho phép đơn giá 0 để biểu diễn hàng tặng; điều này là **một lựa chọn hợp đồng** của ví dụ, không phải quy tắc bắt buộc cho mọi hệ thống.

### 4.3. Đưa tổng tiền và quy tắc VIP về Order

**src/main/java/vn/example/Order.java**

~~~java
package vn.example;

import java.util.List;
import java.util.Objects;

public final class Order {
    private final String id;
    private final boolean vip;
    private final List<OrderLine> lines;

    public Order(String id, boolean vip, List<OrderLine> lines) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order must have lines");
        }

        this.id = id;
        this.vip = vip;
        // Tạo snapshot bất biến; List.copyOf cũng từ chối phần tử null.
        this.lines = List.copyOf(lines);
    }

    public String id() {
        return id;
    }

    public long subtotalVnd() {
        long result = 0L;
        for (OrderLine line : lines) {
            result = Math.addExact(result, line.subtotalVnd());
        }
        return result;
    }

    /** VIP giảm 10%; tiền giảm làm tròn xuống VND. */
    public long payableVnd() {
        long subtotal = subtotalVnd();
        long discount = vip ? subtotal / 10 : 0L;
        return subtotal - discount;
    }
}
~~~

Order chịu trách nhiệm phối hợp các dòng hàng để xác định giá trị của **chính đơn hàng đó**. Không cần đưa danh sách lines công khai để lớp CheckoutService duyệt. Cần xem xét trước khi thêm getter trả trạng thái nội bộ: một API truy vấn phục vụ hiển thị có thể hợp lệ, nhưng không nên dùng getter đó để lặp lại quy tắc tính tiền ở những lớp khác.

Trong phương thức payableVnd(), phép chia nguyên làm tròn xuống phần tiền giảm. Ví dụ subtotal 99 VND thì VIP giảm 9 VND, phải trả 90 VND. Khi nghiệp vụ đổi sang quy tắc làm tròn khác, thay đổi nên được kiểm thử rõ ràng.

### 4.4. Giữ hành vi điều phối tại CheckoutService

**src/main/java/vn/example/PaymentGateway.java**

~~~java
package vn.example;

@FunctionalInterface
public interface PaymentGateway {
    /** true nếu chấp nhận thanh toán, false nếu từ chối. */
    boolean charge(String orderId, long amountVnd);
}
~~~

**src/main/java/vn/example/CheckoutService.java**

~~~java
package vn.example;

import java.util.Objects;

public final class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public boolean checkout(Order order) {
        Objects.requireNonNull(order, "order");
        return gateway.charge(order.id(), order.payableVnd());
    }
}
~~~

CheckoutService vẫn có một trách nhiệm rõ ràng: tổ chức hành động thanh toán. Nó không giữ phép nhân, vòng lặp cộng tiền hoặc nhánh VIP. Khi cần thay cổng thanh toán thật bằng fake trong unit test, ta tiêm implementation PaymentGateway khác.

**src/main/java/vn/example/Main.java**

~~~java
package vn.example;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Order order = new Order(
                "DH-100",
                true,
                List.of(
                        new OrderLine("BOOK", 100_000, 2),
                        new OrderLine("PEN", 30_000, 1)));

        PaymentGateway demoGateway = (id, amount) -> {
            System.out.println(
                    "Payment request: " + id + " = " + amount + " VND");
            return true;
        };

        CheckoutService service = new CheckoutService(demoGateway);

        System.out.println("Subtotal: " + order.subtotalVnd());
        System.out.println("Payable: " + order.payableVnd());
        System.out.println("Accepted: " + service.checkout(order));
    }
}
~~~

Chạy từ thư mục dự án:

~~~bash
mvn test
mvn -q -DskipTests package
java -cp target/classes vn.example.Main
~~~

Kết quả mong đợi:

~~~text
Subtotal: 230000
Payable: 207000
Payment request: DH-100 = 207000 VND
Accepted: true
~~~

Ở bản demo, PaymentGateway chỉ in thông tin và trả true; đây không phải giao dịch thanh toán thật. Đối tượng Order có thể tự xác định tiền phải trả, còn lớp điều phối chỉ giao tiếp với cổng thanh toán.

## 5. Viết kiểm thử bảo vệ hành vi khi di chuyển phương thức

Refactor đúng nghĩa phải giữ nguyên hành vi bên ngoài theo **hợp đồng đã chọn**. Trước khi di chuyển mã trong một dự án đang hoạt động, nên có những test đặc trưng (characterization tests) xác định giá trị và nhánh hiện tại. Sau khi di chuyển, test ở cấp đối tượng và cấp luồng phải tiếp tục đạt.

**src/test/java/vn/example/OrderTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {
    @Test
    void subtotalComesFromOrderLines() {
        Order order = new Order("O-1", false, List.of(
                new OrderLine("A", 100_000, 2),
                new OrderLine("B", 30_000, 1)));

        assertEquals(230_000L, order.subtotalVnd());
        assertEquals(230_000L, order.payableVnd());
    }

    @Test
    void vipDiscountIsAppliedOnceAtOrderLevel() {
        Order order = new Order("O-2", true, List.of(
                new OrderLine("A", 100_000, 2),
                new OrderLine("B", 30_000, 1)));

        assertEquals(230_000L, order.subtotalVnd());
        assertEquals(207_000L, order.payableVnd());
    }

    @Test
    void fractionalDiscountIsRoundedDownInVnd() {
        Order order = new Order("O-3", true, List.of(
                new OrderLine("A", 99, 1)));

        assertEquals(99L, order.subtotalVnd());
        assertEquals(90L, order.payableVnd());
    }

    @Test
    void orderDoesNotChangeWhenInputListChanges() {
        List<OrderLine> input = new ArrayList<>();
        input.add(new OrderLine("A", 100, 1));

        Order order = new Order("O-4", false, input);
        input.add(new OrderLine("B", 200, 1));

        assertEquals(100L, order.payableVnd());
    }

    @Test
    void validatesItsOwnInvariants() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine("X", -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine("X", 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine(" ", 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("O-5", false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("", false,
                        List.of(new OrderLine("X", 1, 1))));
    }

    @Test
    void rejectsOverflowInsteadOfSilentlyWrapping() {
        OrderLine line = new OrderLine("X", Long.MAX_VALUE, 2);
        assertThrows(ArithmeticException.class, line::subtotalVnd);

        Order order = new Order("O-6", false, List.of(
                new OrderLine("A", Long.MAX_VALUE, 1),
                new OrderLine("B", 1, 1)));

        assertThrows(ArithmeticException.class, order::subtotalVnd);
    }
}
~~~

**src/test/java/vn/example/CheckoutServiceTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {
    @Test
    void passesComputedAmountToGatewayExactlyOnce() {
        List<String> calls = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            calls.add(id + ":" + amount);
            return true;
        };

        Order order = new Order("DH-100", true, List.of(
                new OrderLine("BOOK", 100_000, 2),
                new OrderLine("PEN", 30_000, 1)));

        CheckoutService service = new CheckoutService(gateway);

        assertTrue(service.checkout(order));
        assertEquals(List.of("DH-100:207000"), calls);
    }

    @Test
    void preservesPaymentRejection() {
        List<Long> charged = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            charged.add(amount);
            return false;
        };

        Order order = new Order("DH-101", false, List.of(
                new OrderLine("PEN", 30_000, 2)));

        assertFalse(new CheckoutService(gateway).checkout(order));
        assertEquals(List.of(60_000L), charged);
    }

    @Test
    void nullOrderIsRejectedBeforeCallingGateway() {
        List<String> calls = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            calls.add(id);
            return true;
        };

        CheckoutService service = new CheckoutService(gateway);

        assertThrows(NullPointerException.class,
                () -> service.checkout(null));
        assertTrue(calls.isEmpty());
    }
}
~~~

Đây là **9 phương thức @Test** (6 test Order và 3 test CheckoutService). Khi chạy đúng cấu trúc Maven trên, bộ test dự kiến có 9 lượt và không có failure/error.

Các test cấp Order chứng minh trách nhiệm đã được chuyển đúng nơi và phép tính vẫn chính xác. Các test CheckoutService chứng minh luồng bên ngoài tiếp tục dùng đúng số tiền, truyền đúng mã đơn và không gọi cổng thanh toán sai trường hợp. Test không cần truy cập trường private của Order.

| Kiểm thử | Trách nhiệm được kiểm tra | Sai sót có thể phát hiện |
| --- | --- | --- |
| subtotalComesFromOrderLines | Order tổng hợp các thành tiền từ OrderLine | Quên dòng hàng hoặc tính nhầm số lượng |
| vipDiscountIsAppliedOnceAtOrderLevel | Giảm 10% một lần trên tổng đơn | Giảm hai lần hoặc tính sai phạm vi |
| fractionalDiscountIsRoundedDownInVnd | Làm tròn theo hợp đồng | Sai 1 VND ở biên |
| orderDoesNotChangeWhenInputListChanges | Đóng gói và snapshot bất biến | Thay đổi đơn từ bên ngoài |
| validatesItsOwnInvariants | Bất biến ở chủ sở hữu dữ liệu | Cho dữ liệu sai đi sâu vào quy trình |
| rejectsOverflowInsteadOfSilentlyWrapping | Phép tính có kiểm tra tràn | Thành tiền âm vì overflow |
| passesComputedAmountToGatewayExactlyOnce | Điều phối vẫn đúng sau refactor | Gửi số tiền cũ, gọi gateway lặp |
| preservesPaymentRejection | Giữ ngữ nghĩa kết quả | Đổi false thành true |
| nullOrderIsRejectedBeforeCallingGateway | Không gây tác dụng phụ khi đầu vào null | Gọi phụ thuộc với dữ liệu không hợp lệ |

## 6. Các bước refactor an toàn trong hệ thống hiện có

Thực hiện trên một thay đổi nhỏ, có thể kiểm chứng:

1. **Khoanh vùng hành vi.** Xác định phương thức đang đọc nhiều trường của đối tượng khác. Hỏi nó thực sự tính/ra quyết định dựa trên dữ liệu nào, và có gây I/O không.
2. **Chốt hợp đồng cũ.** Viết hoặc giữ test cho các trường hợp phổ biến, biên, lỗi, làm tròn và các caller quan trọng. Với tính toán tiền, kiểm tra tràn số và đơn vị tiền tệ.
3. **Tạo phương thức tại đối tượng đích.** Bắt đầu với OrderLine.subtotalVnd(); cho phương thức cũ gọi phương thức mới để có thể kiểm chứng từng bước.
4. **Di chuyển phần tổng hợp.** Thêm Order.subtotalVnd() rồi Order.payableVnd(). Chuyển những chi tiết tính tiền ra khỏi lớp điều phối.
5. **Chỉnh caller.** CheckoutService hỏi order.payableVnd() thay vì duyệt order.lines() và kiểm tra trạng thái VIP.
6. **Chạy kiểm thử mỗi bước.** Đảm bảo kết quả, số lần tương tác, exception và quy tắc rounding không đổi khi chuyển.
7. **Dọn đường gọi cũ.** Nếu phương thức cũ là API công khai, cân nhắc giữ adapter ủy quyền trong giai đoạn chuyển đổi; khi caller đã chuyển hết và không còn yêu cầu tương thích, loại bỏ hàm tính trùng. Nếu là API nội bộ, có thể xóa ngay khi đã xác minh không còn caller.
8. **Đánh giá lại thiết kế.** Xem coupling, cohesion, vòng đời đối tượng, quyền thay đổi dữ liệu và liệu phương thức đích có thật sự hiểu quy tắc nghiệp vụ.

Có thể dùng trình tự nhỏ thay vì chuyển cả một khối mã lớn. Các IDE Java hỗ trợ thao tác *Move Instance Method*, nhưng công cụ tự động không hiểu đầy đủ ranh giới nghiệp vụ: vẫn phải kiểm thử và rà soát API.

~~~mermaid
flowchart TD
    A["Phát hiện Feature Envy"] --> B["Xác định dữ liệu và chủ sở hữu quy tắc"]
    B --> C["Chốt test hành vi hiện tại"]
    C --> D["Chuyển phép tính nhỏ vào OrderLine"]
    D --> E["Chuyển tổng hợp vào Order"]
    E --> F["Đổi CheckoutService thành điều phối"]
    F --> G["Chạy test và rà soát caller"]
    G --> H{"Giữ hợp đồng?"}
    H -->|Không| I["Sửa sai lệch, kiểm tra biên"]
    I --> G
    H -->|Có| J["Dọn phương thức trùng và kết thúc"]
~~~

## 7. Mối liên hệ với các nguyên tắc OOP

**Encapsulation (đóng gói).** Đối tượng có thể bảo vệ bất biến khi chính nó thực hiện thao tác trên dữ liệu mình sở hữu. Caller không cần biết danh sách dòng hàng được lưu bằng ArrayList, cấu trúc bất biến hay cơ chế lazy loading.

**High Cohesion (kết dính cao).** Phép tính thành tiền nằm cùng đơn giá và số lượng. Quy tắc tổng tiền nằm cùng toàn bộ đơn hàng. Mỗi thay đổi liên quan có xu hướng tập trung vào lớp thích hợp.

**Low Coupling (liên kết lỏng).** CheckoutService chỉ phụ thuộc vào giao diện nghiệp vụ gọn của Order: id() và payableVnd(), thay vì từng getter trên các dòng hàng và quy tắc VIP. Chuyển nhiều dòng code sang đối tượng khác nhưng vẫn yêu cầu caller đọc cùng lượng dữ liệu không thực sự cải thiện coupling.

**Single Responsibility Principle (SRP).** Lý do thay đổi liên quan giá trị dòng hàng thuộc OrderLine; lý do thay đổi quy tắc tính tổng cơ bản thuộc Order; thay đổi quy trình thu tiền thuộc CheckoutService. SRP không có nghĩa mỗi lớp chỉ được có một phương thức.

**Information Expert (nguyên tắc GRASP).** Giao trách nhiệm cho đối tượng có đủ thông tin để làm tốt nhiệm vụ. Đây là hướng dẫn hữu ích để chọn nơi đặt phương thức, nhưng phải cân bằng với ranh giới transaction, security, phân quyền, hiệu năng và domain.

**Tell, Don't Ask.** Hãy gọi một hành vi biểu đạt ý định như order.payableVnd() thay vì hỏi hết dữ liệu rồi tự suy luận. Tuy nhiên, đọc dữ liệu qua API truy vấn cho UI, báo cáo hoặc serialization là nhu cầu chính đáng; không phải getter nào cũng là mùi mã.

## 8. Khi nào KHÔNG nên chuyển phương thức?

| Tình huống | Vì sao không nên chuyển vội | Phương án cân nhắc |
| --- | --- | --- |
| Hành vi sử dụng nhiều domain object ngang vai trò | Không có một đối tượng tự nhiên chịu toàn bộ quy tắc | Domain service / policy riêng |
| Thuật toán phụ thuộc API mạng, database, gửi email | Entity sẽ mang phụ thuộc I/O và khó kiểm soát transaction | Application service hoặc adapter |
| Quy tắc khuyến mãi thường đổi, cần nhiều chiến lược | Nhồi toàn bộ điều kiện vào Order làm lớp phình to | DiscountPolicy / Strategy |
| Logic định dạng màn hình hoặc xuất file | Làm domain object phụ thuộc giao diện hiển thị | Presenter / formatter |
| Di chuyển tạo phụ thuộc vòng giữa các lớp | Kết dính có thể giảm, thiết kế khó hiểu hơn | Tách collaborator hoặc xem lại ranh giới |
| Phương thức đọc nhiều dữ liệu từ hai aggregate lớn | Có thể gây nạp dư dữ liệu hoặc phá transaction boundary | Query service, domain service hoặc DTO chuyên dụng |
| Mã logic thực sự dùng trạng thái của đối tượng nguồn | Chuyển sẽ làm đích phải phụ thuộc ngược lại nguồn | Giữ tại nguồn hoặc trích thành phần nhỏ |

Một nguyên tắc thực hành: **chuyển phần có cùng lý do thay đổi, không di chuyển máy móc chỉ vì thấy nhiều dấu chấm getter**.

Ví dụ, nếu giảm giá được tính theo mã chiến dịch từ nhiều hệ thống, việc đưa toàn bộ chiến dịch vào Order sẽ khiến Order phụ thuộc vào API coupon. Giải pháp hợp lý có thể là PricingPolicy tính mức giảm và Order quản lý các bất biến của đơn; CheckoutService phối hợp các bước cần thiết.

## 9. Bài tập mở rộng

**Bài 1 — Phát hiện hành vi đặt sai chỗ.** Giả sử ReportService duyệt danh sách OrderLine để tính số lượng sản phẩm. Hãy phân biệt hai nhu cầu:
- Nếu muốn biết tổng số lượng của **một đơn**, cân nhắc Order.totalQuantity().
- Nếu muốn tổng số lượng đã bán trong **một khoảng thời gian từ nhiều đơn**, báo cáo/query service có thể là chủ sở hữu hợp lý.

**Bài 2 — Refactor giỏ hàng.** Tạo CartItem(price, quantity) và Cart(items). Ban đầu CartService tính tổng; sau đó chuyển phép tính từng dòng vào CartItem và phép cộng vào Cart. Viết test bảo vệ trường hợp giỏ rỗng, số lượng không hợp lệ, tràn số và thay đổi danh sách đầu vào.

**Bài 3 — Chính sách giảm giá có thể thay đổi.** Thêm interface DiscountPolicy với phương thức tính số tiền giảm. Thử thiết kế để Order chịu trách nhiệm tổng hợp còn chính sách giảm giá do một đối tượng chuyên trách đảm nhiệm. Xác định rõ chính sách nào được cung cấp tại construction hoặc khi định giá, và tránh lưu giá không còn hợp lệ theo thời gian.

**Bài 4 — Xử lý migration API.** Nếu một module khác vẫn gọi PriceService.calculatePayable(order), hãy giữ tạm phương thức cũ ủy quyền cho order.payableVnd(), đánh dấu deprecated khi phù hợp, chuyển caller rồi xóa khi đã kiểm tra tác động tương thích.

**Tự đánh giá:** Sau refactor, một phương thức dùng dữ liệu của đối tượng nào thì trách nhiệm chủ yếu nằm tại đối tượng đó; các lớp điều phối hiểu ít hơn về chi tiết nội bộ; hành vi bên ngoài được test bảo vệ; và không phát sinh phụ thuộc vòng hay nhồi thêm I/O vào domain object.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-005">Đóng gói trường dữ liệu và cấu trúc nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng gói trường dữ liệu và cấu trúc nội bộ trong Java OOP

> **Phạm vi:** Tái cấu trúc — *Encapsulate Field*, *Encapsulate Collection* và *Hide Representation*. Mục tiêu là để mỗi đối tượng tự bảo vệ trạng thái hợp lệ và cho phép bên ngoài làm việc qua một API ổn định.

## 1. Vấn đề thực tế: field `private` chưa đủ

Trong lập trình hướng đối tượng, **đóng gói (encapsulation)** có nghĩa là đối tượng kiểm soát cách trạng thái của nó được đọc hoặc thay đổi. Client cần biết **đối tượng có thể làm gì**, thay vì biết **nó lưu dữ liệu như thế nào**.

Hai tình huống làm mất đóng gói:

~~~java
class Order {
    public final List<OrderLine> lines = new ArrayList<>();
}
~~~

`final` chỉ ngăn gán lại **tham chiếu** `lines`; nó **không** ngăn `order.lines.clear()`, `add()` hay sửa phần tử nằm trong danh sách.

~~~java
class Order {
    private final List<OrderLine> lines = new ArrayList<>();

    public List<OrderLine> getLines() {
        return lines; // Vẫn rò rỉ tham chiếu nội bộ!
    }
}
~~~

Dù field đã `private`, client vẫn sửa danh sách trực tiếp qua getter:

~~~java
order.getLines().clear();
~~~

Vấn đề này được gọi là **representation exposure**: cấu trúc dữ liệu dùng để biểu diễn trạng thái nội bộ bị lộ ra, cho phép mã khác thay đổi nó mà không đi qua quy tắc của đối tượng.

### Kết quả cần đạt

Sau bài này, bạn có thể:

- Nhận biết field bị lộ, mutable collection, mutable element và tham chiếu alias gây rò rỉ trạng thái.
- Áp dụng `Encapsulate Field` bằng việc giữ field `private` và cung cấp thao tác có ý nghĩa, thay vì tự động tạo getter/setter cho mọi field.
- Giấu `ArrayList`, `HashMap` hoặc mảng đằng sau một **hợp đồng hành vi** không phụ thuộc loại cấu trúc lưu trữ.
- Phân biệt **bản chụp bất biến**, **view không sửa được** và **deep copy**.
- Viết kiểm thử chứng minh client không sửa được trạng thái nội bộ qua tham chiếu được trả về.
- Tái cấu trúc có kiểm soát, giữ hành vi công khai đã cam kết và xử lý điểm tương thích API khi cần.

## 2. Tại sao cần đóng gói cấu trúc nội bộ?

Giả sử `ShoppingCart` có các quy tắc:

1. Mỗi SKU xuất hiện tối đa một lần.
2. SKU phải khác `null`, không phải chuỗi trống hay chỉ có khoảng trắng.
3. Đơn giá (cent) không âm, số lượng luôn dương khi dòng hàng tồn tại.
4. Thay đổi số lượng về `0` có nghĩa là xóa dòng hàng, không lưu dòng có số lượng `0`.
5. Tổng tiền bằng tổng `unitPriceCents × quantity` của các dòng; không được trả số âm hoặc kết quả sai vì tràn `long`.

Nếu client được `cart.lines.add(...)` hoặc `cart.lines.get(0).quantity = -5`, mọi quy tắc này có thể bị phá mà `ShoppingCart` không biết.

**Bất biến (invariant)** là điều kiện phải đúng với mọi trạng thái có thể quan sát được của đối tượng sau một thao tác công khai thành công. API của giỏ hàng nên cho phép `putLine`, `changeQuantity`, `removeLine` và `totalCents`, thay vì cho phép client tùy ý sửa `Map`/`List`.

| Cách thiết kế | Có bảo vệ bất biến? | Điểm cần nhớ |
|---|---|---|
| `public List<Line> lines` | Không | Client thay đổi trực tiếp collection |
| `private List<Line> lines` + getter trả tham chiếu gốc | Không | Chỉ thay cú pháp, chưa ẩn representation |
| Getter trả `Collections.unmodifiableList(lines)` | Chỉ chặn sửa qua view | View **sống**, vẫn phản ánh thay đổi từ nguồn; element mutable vẫn sửa được |
| Getter trả `List.copyOf(lines)` | Chặn sửa cấu trúc snapshot | Sao chép **nông**: element mutable vẫn có thể bị sửa |
| Snapshot của các **value object bất biến** | Thường phù hợp cho đọc an toàn | Client không thể sửa list hoặc phần tử qua reference được trả |
| API mutation có kiểm tra quy tắc | Có thể bảo vệ invariant | Mọi thay đổi hợp lệ đi qua phương thức kiểm soát |

**Encapsulation không đồng nghĩa bất biến tuyệt đối:** một giỏ hàng vẫn có thể thay đổi trạng thái thông qua các phương thức công khai **được phép**. Điều quan trọng là không có đường tắt vượt qua validation và business rules.

## 3. Ví dụ Java 17 chạy được: từ giỏ hàng rò rỉ dữ liệu sang API an toàn

Tạo tệp duy nhất `EncapsulationDemo.java`, dùng JDK 17 trở lên. Ví dụ có cả phiên bản **cố ý không an toàn** để minh họa hậu quả và phiên bản đã cải thiện để kiểm thử.

~~~java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class EncapsulationDemo {

    // BEFORE: client có thể sửa cả danh sách lẫn đối tượng nằm trong đó.
    static final class MutableLine {
        public String sku;
        public long unitPriceCents;
        public int quantity;

        MutableLine(String sku, long unitPriceCents, int quantity) {
            this.sku = sku;
            this.unitPriceCents = unitPriceCents;
            this.quantity = quantity;
        }
    }

    static final class LeakyCart {
        public final List<MutableLine> lines = new ArrayList<>();

        long totalCents() {
            long sum = 0;
            for (MutableLine line : lines) {
                sum += line.unitPriceCents * line.quantity;
            }
            return sum;
        }
    }

    // AFTER: value object bất biến, dùng để trả kết quả đọc.
    // String, long, int ở đây không để lộ mutable component.
    public record LineSnapshot(
            String sku, long unitPriceCents, int quantity) {
        public LineSnapshot {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must not be blank");
            }
            if (unitPriceCents < 0) {
                throw new IllegalArgumentException("unitPriceCents must be >= 0");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
        }
    }

    static final class ShoppingCart {
        // Có thể đổi Map thành cấu trúc khác mà API không đổi.
        // LinkedHashMap giữ thứ tự chèn cho ví dụ.
        private final Map<String, LineSnapshot> lines = new LinkedHashMap<>();

        // Upsert: SKU mới thì thêm; SKU cũ thì thay giá và số lượng.
        public void putLine(String sku, long unitPriceCents, int quantity) {
            LineSnapshot next = new LineSnapshot(sku, unitPriceCents, quantity);
            lines.put(next.sku(), next);
        }

        // Chỉ thay số lượng; quantity = 0 xóa dòng.
        public void changeQuantity(String sku, int quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("quantity must be >= 0");
            }
            LineSnapshot current = lines.get(sku);
            if (current == null) {
                throw new NoSuchElementException("Unknown SKU: " + sku);
            }
            if (quantity == 0) {
                lines.remove(sku);
                return;
            }
            lines.put(sku,
                new LineSnapshot(sku, current.unitPriceCents(), quantity));
        }

        public boolean removeLine(String sku) {
            return lines.remove(sku) != null;
        }

        // Snapshot tách khỏi Map nội bộ; các element đều bất biến.
        public List<LineSnapshot> lines() {
            return List.copyOf(lines.values());
        }

        public long totalCents() {
            long result = 0;
            for (LineSnapshot line : lines.values()) {
                long itemTotal = Math.multiplyExact(
                    line.unitPriceCents(), line.quantity());
                result = Math.addExact(result, itemTotal);
            }
            return result;
        }

        public int lineCount() {
            return lines.size();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException");
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        // Chứng minh lỗi: client sửa state mà không qua validation.
        LeakyCart old = new LeakyCart();
        MutableLine leaked = new MutableLine("BOOK", 100, 2);
        old.lines.add(leaked);
        check(old.totalCents() == 200, "initial old total");
        leaked.quantity = -3; // alias ngoài giỏ hàng làm hỏng invariant
        check(old.totalCents() == -300, "expected broken old total");
        System.out.println("LEAK: client changed old cart total to -300");

        ShoppingCart cart = new ShoppingCart();
        cart.putLine("BOOK", 100, 2);
        cart.putLine("PEN", 99, 1);
        check(cart.totalCents() == 299, "initial safe total");
        check(cart.lineCount() == 2, "initial line count");

        List<LineSnapshot> snapshot = cart.lines();
        expectUnsupported(snapshot::clear);
        check(snapshot.get(0).quantity() == 2, "snapshot initial quantity");

        // Thao tác hợp lệ đi qua API có kiểm soát.
        cart.changeQuantity("BOOK", 3);
        check(cart.totalCents() == 399, "safe updated total");
        check(snapshot.get(0).quantity() == 2,
            "old snapshot must not change after cart update");
        check(cart.lines().get(0).quantity() == 3,
            "new snapshot must reflect new state");

        // Input sai bị chặn và không làm thay đổi state.
        expectIllegalArgument(() -> cart.putLine("BAD", -1, 1));
        expectIllegalArgument(() -> cart.putLine("X", 100, 0));
        expectIllegalArgument(() -> cart.putLine("  ", 100, 1));
        expectIllegalArgument(() -> cart.changeQuantity("BOOK", -1));
        check(cart.lineCount() == 2 && cart.totalCents() == 399,
            "invalid operations must not corrupt cart");

        // Đổi số lượng về 0 sẽ xóa dòng, không tạo dòng không hợp lệ.
        cart.changeQuantity("BOOK", 0);
        check(cart.lineCount() == 1, "remove by quantity zero");
        check(cart.totalCents() == 99, "total after removal");

        System.out.println("PASS: cannot modify exposed snapshot");
        System.out.println("PASS: invariants enforced by public methods");
        System.out.println("PASS: old snapshot is independent");
        System.out.println("Final total (cents): " + cart.totalCents());
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac EncapsulationDemo.java
java EncapsulationDemo
~~~

Kết quả mong đợi:

~~~text
LEAK: client changed old cart total to -300
PASS: cannot modify exposed snapshot
PASS: invariants enforced by public methods
PASS: old snapshot is independent
Final total (cents): 99
~~~

**Tại sao phiên bản mới an toàn hơn?**

1. `lines` là `private`: client không thể thêm/xóa dòng bằng truy cập field.
2. `putLine` và `changeQuantity` là **cửa ngõ duy nhất** thay đổi các dòng trong mô hình này. Validation xảy ra **trước** khi cập nhật trạng thái.
3. `LineSnapshot` là record có component `String`, `long` và `int`; không có reference tới object có thể thay đổi. Khi cập nhật số lượng, giỏ tạo record mới.
4. `List.copyOf(lines.values())` tạo danh sách snapshot không sửa được. Snapshot cũ không thay đổi khi `ShoppingCart` cập nhật nội bộ.
5. `Math.multiplyExact` và `Math.addExact` phát hiện tràn số bằng `ArithmeticException`; không để `long` âm đi vì phép toán tràn rồi âm thầm trả số sai.

Ví dụ chưa xử lý **concurrency**: `LinkedHashMap` không thread-safe. Nếu nhiều thread cùng thay đổi giỏ, cần giới hạn ownership theo request/transaction hoặc đồng bộ tại đúng ranh giới; tính bất biến của snapshot không tự khiến toàn bộ `ShoppingCart` thread-safe.

## 4. Ba khái niệm hay bị nhầm: copy, view và immutable element

Xét `List<Person>` mà `Person` có setter:

~~~java
final class Person {
    private String name;
    Person(String name) { this.name = name; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}
~~~

| Câu lệnh | Có sửa được cấu trúc danh sách trả về? | Có thấy nguồn thay đổi sau khi lấy? | Có thể sửa `Person` qua reference? |
|---|---|---|---|
| `return people;` | Có | Có, cùng object | Có |
| `return Collections.unmodifiableList(people);` | Không qua view | **Có** (live view) | **Có** |
| `return new ArrayList<>(people);` | Có, nhưng chỉ bản sao list | Không thấy thêm/xóa của nguồn sau copy | **Có** |
| `return List.copyOf(people);` | Không | Thường là snapshot đối với thay đổi cấu trúc khi nguồn mutable | **Có** |
| `return people.stream().map(p -> new ImmutablePerson(p.getName())).toList();` | Không qua danh sách trả về | Không, nếu thành phần immutable | Không |

`List.copyOf` sao chép cấu trúc từ collection mutable, nhưng **không sao chép sâu** các phần tử. Nếu object trong list có thể thay đổi thì cả bản sao và nguồn vẫn nhìn thấy cùng object đó. `Collections.unmodifiableList` chỉ bọc list, còn chủ sở hữu list ban đầu vẫn có thể thay đổi nội dung, nên view có thể đổi theo.

`Stream.toList()` (Java 16+) tạo danh sách không hỗ trợ các thao tác cập nhật, nhưng **cũng không tự deep-copy element**.

### Khi có mutable element, dùng defensive copy thực sự

Nếu buộc giữ class `Person` mutable, có thể tạo bản sao tại **đầu ra**:

~~~java
public List<Person> peopleSnapshot() {
    return people.stream()
        .map(p -> new Person(p.getName()))
        .toList();
}
~~~

Và tạo bản sao tại **đầu vào**:

~~~java
public void addPerson(Person incoming) {
    java.util.Objects.requireNonNull(incoming);
    people.add(new Person(incoming.getName()));
}
~~~

Như vậy, việc caller sửa `incoming` sau `addPerson` hoặc sửa một `Person` nhận về từ `peopleSnapshot` không ảnh hưởng trạng thái lưu bên trong, **với điều kiện** tên là `String` immutable và class chỉ có field đang sao chép. Với object lồng nhau, mảng, collection hay mutable `Date`, cần quyết định sao chép sâu các thành phần sở hữu phù hợp. Không có một hàm clone vạn năng tự đảm bảo an toàn cho mọi object graph.

## 5. Đóng gói trường dữ liệu (Encapsulate Field) đúng cách

Ví dụ field public:

~~~java
class BankAccount {
    public long balanceCents;
}
~~~

Sửa cơ học thành `private` + setter là chưa đủ:

~~~java
class BankAccount {
    private long balanceCents;

    public long getBalanceCents() { return balanceCents; }
    public void setBalanceCents(long balanceCents) {
        this.balanceCents = balanceCents; // vẫn cho phép caller gán âm
    }
}
~~~

Nếu hợp đồng là “số dư không âm”, API nên thể hiện **hành động hợp lệ**:

~~~java
final class BankAccount {
    private long balanceCents;

    BankAccount(long openingBalanceCents) {
        if (openingBalanceCents < 0) {
            throw new IllegalArgumentException("Negative opening balance");
        }
        this.balanceCents = openingBalanceCents;
    }

    long balanceCents() {
        return balanceCents; // primitive: trả theo giá trị, an toàn
    }

    void deposit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Deposit must be positive");
        }
        balanceCents = Math.addExact(balanceCents, amountCents);
    }

    void withdraw(long amountCents) {
        if (amountCents <= 0 || amountCents > balanceCents) {
            throw new IllegalArgumentException("Invalid withdrawal");
        }
        balanceCents -= amountCents;
    }
}
~~~

Ưu điểm là client chỉ có thể tăng/giảm số dư trong điều kiện hợp lệ. Nếu cần chuyển tiền giữa hai tài khoản, phải thiết kế thao tác giao dịch thích hợp; chỉ hai lời gọi `withdraw` rồi `deposit` không tự bảo đảm tính nguyên tử nếu một bước thất bại.

### Chọn API theo bản chất state

- Field cấu hình chỉ đọc: dùng `private final` và getter an toàn hoặc truyền qua constructor.
- Giá trị nghiệp vụ có ràng buộc: ưu tiên `deposit`, `withdraw`, `changeQuantity`, `rename`, `activate`… thay cho `setAnything`.
- Danh sách thuộc quyền sở hữu object: cho xem snapshot; thay đổi qua `add/remove/update` có validation.
- Giá trị tính được: ưu tiên method `totalCents()` thay vì cho caller sửa một field `total` dễ mất đồng bộ.
- DTO chỉ chuyển dữ liệu giữa tầng/API: record hoặc JavaBean có setter có thể là lựa chọn hợp lý; không cần ép mọi DTO thành rich domain model.

**Điểm cốt lõi:** Không phải “càng ít getter/setter càng tốt”. Thiết kế API phải tương ứng với điều người dùng đối tượng được phép làm và những bất biến cần giữ.

## 6. Che giấu cấu trúc dữ liệu là giảm phụ thuộc vào implementation

Hãy so sánh hai API:

~~~java
// Client phụ thuộc vào cấu trúc lưu trữ.
Map<String, LineSnapshot> getInternalMap();

// Client phụ thuộc vào khả năng cần dùng.
void putLine(String sku, long priceCents, int quantity);
void changeQuantity(String sku, int quantity);
List<LineSnapshot> lines();     // snapshot đọc
long totalCents();
~~~

Với thiết kế thứ hai, implementation có thể chuyển từ `LinkedHashMap` sang cấu trúc phù hợp hơn, thêm chỉ mục theo SKU, cache tổng tiền hoặc lưu các bản ghi khác mà caller vẫn dựa vào hợp đồng công khai.

~~~mermaid
classDiagram
    class CartClient {
      +renderCart()
      +updateQuantity()
    }
    class ShoppingCart {
      -Map~String,LineSnapshot~ lines
      +putLine(String,long,int)
      +changeQuantity(String,int)
      +removeLine(String) boolean
      +lines() List~LineSnapshot~
      +totalCents() long
    }
    class LineSnapshot {
      <<record>>
      +sku() String
      +unitPriceCents() long
      +quantity() int
    }
    CartClient --> ShoppingCart : gọi API công khai
    ShoppingCart o--> LineSnapshot : chứa dữ liệu bất biến
~~~

**Che giấu implementation** không có nghĩa client không biết gì về dữ liệu. Client vẫn cần tên SKU, giá, số lượng nếu đó là mục đích nghiệp vụ; điều nó không cần biết là object lưu chúng bằng `ArrayList`, `LinkedHashMap` hay bảng DB.

## 7. Quy trình refactor an toàn trong dự án đang chạy

1. **Xác định ownership của dữ liệu:** Ai được quyền tạo, giữ, thay và hủy collection hoặc object? Tìm mọi đường sửa hiện có: field public, getter, setter, alias từ constructor, callback, serialization.
2. **Ghi hợp đồng trước thay đổi:** Field có cho phép null không, duplicate không, thứ tự có ý nghĩa không, kiểu ngoại lệ nào được công bố, hành động nào phải bảo toàn dữ liệu?
3. **Thêm characterization/regression tests:** Kiểm tra dữ liệu hợp lệ, input sai, giá trị biên và tác dụng phụ mà client thực sự trông đợi.
4. **Đưa thao tác cập nhật vào class sở hữu state:** Ví dụ `addLine`, `removeLine`, `changeQuantity`. Validation trước mutation; giữ trạng thái hợp lệ cả khi exception xảy ra.
5. **Thu hẹp quyền truy cập field:** Chuyển thành `private` khi có thể; thay client bằng API mới một cách có kiểm soát.
6. **Bảo vệ đầu vào/đầu ra:** Copy collection khi nhận vào nếu nhận ownership; trả snapshot hoặc view thích hợp; nếu element mutable cần bản sao an toàn hoặc value object.
7. **Dọn đường tắt cũ:** Ngừng trả tham chiếu mutable, tránh setter tùy ý phá invariant. Với public API đã phát hành, lên kế hoạch deprecation/migration hoặc adapter phù hợp.
8. **Chạy test và review diff:** Xác nhận invariant, concurrency contract nếu có, hiệu năng đối với collection lớn và serialization/API compatibility.

~~~mermaid
flowchart TD
    A["Quan sát alias / field rò rỉ"] --> B["Xác định invariant và contract hiện có"]
    B --> C["Viết test bảo vệ hành vi"]
    C --> D["Tạo API thao tác có kiểm soát"]
    D --> E["Ẩn field và defensive copy"]
    E --> F{"Test regression + bất biến PASS?"}
    F -->|Không| G["Sửa nguyên nhân rồi chạy lại"]
    G --> F
    F -->|Có| H["Đổi client, dọn API cũ, review"]
~~~

### Các điểm tương thích cần cân nhắc

- **API/source compatibility:** Đổi field `public` thành `private` khiến client đang gọi field không biên dịch. Nếu thư viện đã được xuất bản, cần migration plan và versioning.
- **Hợp đồng collection:** Nếu client được cam kết **thứ tự**, việc chuyển `LinkedHashMap` sang `HashMap` rồi trả list có thể làm thay đổi thứ tự quan sát được. Nếu thứ tự không phải hợp đồng, đừng thêm test buộc thứ tự.
- **ORM/JPA/serialization:** Framework có thể cần constructor, access strategy hoặc annotation phù hợp. Bảo đảm dùng được với framework thay vì mở field public chỉ vì tiện.
- **Hiệu năng:** `List.copyOf` tạo snapshot khi cần; trả snapshot của collection rất lớn với tần suất cao có chi phí. Có thể cung cấp truy vấn theo trang, iterator có kiểm soát hoặc immutable data structure thích hợp. Không được hy sinh invariant chỉ vì muốn tránh copy.
- **Thread safety:** Trả snapshot an toàn không tự làm thao tác cập nhật trên collection thread-safe. Cần chiến lược đồng bộ, immutable aggregate hoặc transaction tùy ngữ cảnh.
- **Biểu diễn JSON:** DTO có thể khác mô hình miền. Một adapter/mapper nên chịu trách nhiệm chuyển đổi, tránh lộ cấu trúc lưu trữ của domain ra payload công khai.

## 8. Kiểm thử đóng gói: chứng minh bên ngoài không phá được trạng thái

Các test nên xác nhận **hành vi**, không chỉ kiểm tra rằng field mang từ khóa `private`.

| Ca kiểm thử | Thao tác | Kết quả kỳ vọng |
|---|---|---|
| Không lộ list mutable | Nhận `lines()` rồi gọi `clear()` | Bị chặn; giỏ hàng không đổi |
| Snapshot độc lập | Lấy snapshot A, update giỏ, lấy B | A giữ trạng thái cũ; B phản ánh trạng thái mới |
| Không lộ mutable element | Thử thay đổi element đã nhận | Không có đường sửa trực tiếp; nếu DTO mutable thì phải copy |
| Validate trước ghi | Gọi `putLine("", 100, 1)` | Ném `IllegalArgumentException`; tổng và lineCount giữ nguyên |
| Bất biến số lượng | `changeQuantity("BOOK", 0)` | Xóa dòng BOOK, không lưu quantity = 0 |
| Thao tác sai | `changeQuantity("UNKNOWN", 2)` | Ném `NoSuchElementException`; dữ liệu hiện có giữ nguyên |
| Tràn phép tính | Dữ liệu đủ lớn gây overflow | Không trả tổng đã wrap; có lỗi được phát hiện rõ |
| Tính ổn định hợp đồng | Đổi internal `Map` thành triển khai khác | Các ca client hợp lệ vẫn có kết quả đúng |

Có thể dùng JUnit 5 (JUnit Jupiter), đặt test trong cùng package với `EncapsulationDemo`:

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingCartTest {
    @Test
    void returnedCollectionCannotMutateCart() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);

        List<EncapsulationDemo.LineSnapshot> before = cart.lines();
        assertThrows(UnsupportedOperationException.class, before::clear);

        cart.changeQuantity("BOOK", 3);
        assertEquals(2, before.get(0).quantity());
        assertEquals(3, cart.lines().get(0).quantity());
        assertEquals(300, cart.totalCents());
    }

    @Test
    void failedValidationDoesNotCorruptState() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);

        assertThrows(IllegalArgumentException.class,
            () -> cart.putLine("PEN", -1, 2));
        assertThrows(IllegalArgumentException.class,
            () -> cart.changeQuantity("BOOK", -4));

        assertEquals(1, cart.lineCount());
        assertEquals(200, cart.totalCents());
    }

    @Test
    void removingByZeroKeepsInvariant() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);
        cart.changeQuantity("BOOK", 0);

        assertTrue(cart.lines().isEmpty());
        assertEquals(0, cart.totalCents());
    }
}
~~~

Ví dụ test trên dựa vào class demo cùng package (các nested class không khai báo `public`). Dự án thực tế nên đặt mỗi kiểu vào tệp thích hợp và tổ chức access modifier theo API của package/module.

Cũng cần kiểm thử ở **ranh giới đối tượng đầu vào**. Nếu constructor nhận `List<MutableLine>` rồi lưu tham chiếu trực tiếp, người gọi vẫn có thể sửa `MutableLine` hoặc list sau khi constructor trả về, dù getter đã defensive copy.

## 9. Những lỗi refactor dễ mắc

**Lỗi 1 — “Private rồi là xong”.** Getter trả `List`/`Map` gốc vẫn lộ dữ liệu. Cần xem toàn bộ **đường tham chiếu** vào và ra object.

**Lỗi 2 — Tạo setter cho mọi field.** Setter tự do cho `balance`, `items`, `status` có thể cho phép trạng thái bất hợp lệ. Nên chọn những thao tác hợp lệ của miền.

**Lỗi 3 — Nghĩ `final` làm object bất biến.** `private final List<T>` vẫn bị thay đổi nội dung khi lớp sở hữu gọi `add/remove`; điều này hoàn toàn hợp lệ nếu có kiểm soát. Nếu reference bị lộ, bên ngoài cũng có thể thay đổi nó.

**Lỗi 4 — Nhầm unmodifiable view với snapshot.** `Collections.unmodifiableList(source)` không cho sửa qua wrapper nhưng vẫn thấy sửa đổi từ `source`; `List.copyOf(source)` tạo snapshot cấu trúc (nhưng phần tử vẫn được chia sẻ).

**Lỗi 5 — Sao chép list mà quên element.** `new ArrayList<>(people)` không deep-copy `Person`. Sửa `peopleCopy.get(0).setName("X")` có thể sửa dữ liệu nội bộ nếu phần tử cùng tham chiếu.

**Lỗi 6 — Dựa hoàn toàn vào record để khẳng định deep immutable.** Record với `List`, `Date` hoặc mảng chỉ có field cuối cùng; phần tử/đối tượng lồng nhau có thể mutable. Phải defensive copy tại constructor và accessor khi cần.

**Lỗi 7 — Bỏ quên tính nguyên tử khi mutate.** Nếu update state trước rồi mới validate hoặc gọi logic có thể ném lỗi, object có thể bị dở dang. Tính trước, kiểm tra trước, rồi mới commit; với nhiều resource có thể cần transaction.

**Lỗi 8 — Phá giao diện công khai của thư viện mà không có kế hoạch.** Không nên xem thay `public field` thành private là refactor hoàn toàn tương thích ở ranh giới người dùng thư viện. Cần kiểm thử tương thích API và hướng dẫn migration.

## 10. Bài tập áp dụng

**Bài 1 — Chỉ ra lỗ hổng:**

~~~java
class Library {
    private final List<StringBuilder> titles = new ArrayList<>();

    void add(StringBuilder title) { titles.add(title); }
    List<StringBuilder> getTitles() { return List.copyOf(titles); }
}
~~~

Hãy mô tả **hai đường** làm thay đổi tiêu đề nội bộ ngoài ý muốn: sửa `StringBuilder` sau khi truyền vào `add`, và sửa một `StringBuilder` nhận được từ `getTitles`. Thử sửa thiết kế bằng `List<String>` (với `String` immutable) hoặc copy giá trị khi vào/ra.

**Bài 2 — Thay API setter:** Một `Employee` có `setSalaryCents(long)` công khai. Hãy xác định invariant tiền lương và đề xuất method chuyên biệt (`adjustSalary` hoặc `changeSalary` với kiểm tra, xác thực quyền theo ngữ cảnh). Viết test cho số âm và số vượt giới hạn nghiệp vụ.

**Bài 3 — Danh sách đơn hàng:** Đổi `ShoppingCart.lines()` sang trả live unmodifiable view thay vì snapshot. Viết test để phân biệt **A có giữ nguyên sau update hay không**, rồi quyết định hành vi nào phù hợp contract.

**Bài 4 — Đổi cấu trúc bên trong:** Thay `LinkedHashMap` bằng một cấu trúc khác nhưng giữ `putLine`, `changeQuantity`, `removeLine` và `totalCents`. Nếu thứ tự trả về chưa được công bố, không cần buộc client phụ thuộc vào thứ tự nội bộ.

**Bài 5 — Xử lý null, race và overflow:** Mở rộng bộ test với null SKU, SKU không tồn tại, số tiền lớn và gọi đồng thời. Nêu rõ đâu là lỗi validation và đâu là yêu cầu thread-safety cần bổ sung vào hợp đồng.

### Tiêu chí hoàn thành

Bạn có thể nhận ra đường rò rỉ trạng thái qua field, getter, constructor hoặc phần tử mutable; thiết kế API mutation giữ invariant; chọn **snapshot/view/copy** phù hợp; và chứng minh bằng Java test rằng mã client không thể tùy ý làm hỏng cấu trúc nội bộ. Khi refactor thư viện hoặc hệ thống đang chạy, bạn cũng biết kiểm tra tương thích giao diện công khai, framework và concurrency.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-006">Thay điều kiện theo loại bằng đa hình</a>

<details>
<summary>Xem chi tiết</summary>

# Thay điều kiện theo loại bằng đa hình (Replace Conditional with Polymorphism) — Java OOP

## 1. Bài toán: vì sao nhiều nhánh theo loại gây khó bảo trì?

Giả sử ứng dụng bán hàng hỗ trợ ba hình thức nhận hàng: STANDARD (giao thường), EXPRESS (giao nhanh), PICKUP (khách tự nhận). Mỗi loại có quy tắc tính phí và thời gian giao khác nhau.

Cách viết ban đầu thường kiểm tra loại bằng switch hoặc chuỗi if/else:

~~~java
switch (request.type) {
    case STANDARD: /* tính phí giao thường */ break;
    case EXPRESS:  /* tính phí giao nhanh */ break;
    case PICKUP:   /* tự nhận hàng */ break;
}
~~~

Một khối như vậy không tự động là lỗi. Vấn đề xuất hiện khi **cùng một điều kiện theo loại** lặp lại ở nhiều nghiệp vụ (tính phí, xác định thời gian giao, xử lý yêu cầu, xuất thông báo), khiến bổ sung loại mới phải sửa nhiều chỗ. Những nhánh có liên quan dễ bị bỏ sót, và một lớp dần gánh nhiều hành vi của các loại đối tượng khác nhau.

**Thay điều kiện theo loại bằng đa hình** là kỹ thuật refactoring: đưa hành vi thay đổi theo loại vào các implementation của một hợp đồng chung. Đoạn gọi chỉ yêu cầu đối tượng thực hiện hành vi; Java chọn phương thức tương ứng qua **dynamic dispatch**.

> Mục tiêu là thay đổi cách tổ chức code **mà vẫn giữ nguyên hợp đồng quan sát được**: đầu vào hợp lệ, kết quả, exception và side effect đã được cam kết. Không phải cứ thấy switch là phải tạo hệ phân cấp lớp.

## 2. Phân biệt cấu trúc trước và sau

| Góc nhìn | Điều kiện theo loại | Đa hình |
|---|---|---|
| Chọn hành vi | Caller dùng if/else, switch, instanceof | Caller gọi phương thức chung trên interface/lớp cha |
| Vị trí quy tắc | Nhiều nhánh trong một lớp lớn | Mỗi implementation nắm quy tắc riêng |
| Bổ sung loại | Sửa từng switch có liên quan | Thêm implementation và đăng ký ở ranh giới tạo/chọn đối tượng |
| Kiểm thử | Một lớp ôm nhiều nhánh | Test hợp đồng chung và từng biến thể |
| Khả năng mở rộng | Dễ bỏ sót nhánh | Phụ thuộc cách tổ chức đăng ký và cấu trúc loại |
| Chi phí | Ít lớp, đơn giản cho vài nhánh | Nhiều đối tượng và lớp hơn, cần thiết kế interface hợp lý |

Kỹ thuật này thường liên quan **Open/Closed Principle**: mở rộng hành vi bằng implementation mới, hạn chế sửa code nghiệp vụ đã ổn định. Nó không loại bỏ mọi bước chọn loại: dữ liệu đầu vào vẫn có thể chứa mã loại, và tại một ranh giới vẫn cần ánh xạ mã đó sang đối tượng phù hợp.

~~~mermaid
flowchart LR
    C["Client"] --> B["DeliveryService"]
    B --> P["DeliveryPolicy interface"]
    S["StandardPolicy"] -.-> P
    E["ExpressPolicy"] -.-> P
    K["PickupPolicy"] -.-> P
~~~

Trong mô hình này, DeliveryService không biết công thức phí của từng loại. Nó chỉ dùng interface DeliveryPolicy.

## 3. Ví dụ Java 11 chạy được: chuyển từ switch sang đa hình

### Hợp đồng nghiệp vụ

Đơn hàng có giá trị tiền hàng (VND) không âm và trọng lượng nguyên dương (kg). Ta giả định trọng lượng tính theo kg nguyên để tập trung vào refactor, không phải bài toán đo lường thực tế.

| Loại | Quy tắc phí vận chuyển | Số ngày giao dự kiến |
|---|---|---:|
| STANDARD | Nếu giá trị đơn từ 500.000 VND: 0; nếu thấp hơn: 20.000 + 5.000 × kg | 5 |
| EXPRESS | 45.000 + 10.000 × kg | 1 |
| PICKUP | 0 VND | 0 (khách tự đến lấy) |

Kết quả của dịch vụ là DeliveryQuote gồm feeVnd và etaDays. Bản cũ và bản sau refactor phải cho **cùng kết quả** đối với toàn bộ đầu vào hợp lệ, đồng thời không thay đổi quy tắc xác thực đã cam kết.

Lưu toàn bộ chương trình dưới đây trong file **DeliveryRefactoringDemo.java**. Mã sử dụng Java 11 trở lên, không cần Maven, Spring hoặc thư viện kiểm thử.

~~~java
import java.util.Map;
import java.util.Objects;

public class DeliveryRefactoringDemo {
    enum DeliveryType {
        STANDARD, EXPRESS, PICKUP
    }

    static final class DeliveryRequest {
        final DeliveryType type;
        final long orderValueVnd;
        final int weightKg;

        DeliveryRequest(DeliveryType type,
                        long orderValueVnd,
                        int weightKg) {
            if (type == null || orderValueVnd < 0 || weightKg <= 0) {
                throw new IllegalArgumentException("Invalid delivery request");
            }
            this.type = type;
            this.orderValueVnd = orderValueVnd;
            this.weightKg = weightKg;
        }
    }

    static final class DeliveryQuote {
        final long feeVnd;
        final int etaDays;

        DeliveryQuote(long feeVnd, int etaDays) {
            this.feeVnd = feeVnd;
            this.etaDays = etaDays;
        }
    }

    // TRƯỚC: mọi quy tắc tính phí và thời gian ở một switch.
    static final class LegacyDeliveryService {
        DeliveryQuote quote(DeliveryRequest request) {
            Objects.requireNonNull(request, "request");

            switch (request.type) {
                case STANDARD:
                    long standardFee =
                            request.orderValueVnd >= 500_000L
                            ? 0L
                            : Math.addExact(20_000L,
                                  Math.multiplyExact(
                                      5_000L, (long) request.weightKg));
                    return new DeliveryQuote(standardFee, 5);

                case EXPRESS:
                    long expressFee = Math.addExact(45_000L,
                            Math.multiplyExact(
                                10_000L, (long) request.weightKg));
                    return new DeliveryQuote(expressFee, 1);

                case PICKUP:
                    return new DeliveryQuote(0L, 0);

                default:
                    throw new IllegalStateException("Unsupported type");
            }
        }
    }

    // SAU: một hợp đồng chung cho mọi loại giao hàng.
    interface DeliveryPolicy {
        DeliveryQuote quote(DeliveryRequest request);
    }

    static final class StandardPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            long fee = request.orderValueVnd >= 500_000L
                    ? 0L
                    : Math.addExact(20_000L,
                          Math.multiplyExact(
                              5_000L, (long) request.weightKg));
            return new DeliveryQuote(fee, 5);
        }
    }

    static final class ExpressPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            long fee = Math.addExact(45_000L,
                    Math.multiplyExact(
                        10_000L, (long) request.weightKg));
            return new DeliveryQuote(fee, 1);
        }
    }

    static final class PickupPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            return new DeliveryQuote(0L, 0);
        }
    }

    // SAU: service điều phối, không chứa công thức theo loại.
    static final class DeliveryService {
        private final Map<DeliveryType, DeliveryPolicy> policies;

        DeliveryService(Map<DeliveryType, DeliveryPolicy> policies) {
            Objects.requireNonNull(policies, "policies");
            this.policies = Map.copyOf(policies);
            for (DeliveryType type : DeliveryType.values()) {
                if (!this.policies.containsKey(type)) {
                    throw new IllegalArgumentException(
                            "Missing policy for " + type);
                }
            }
        }

        DeliveryQuote quote(DeliveryRequest request) {
            Objects.requireNonNull(request, "request");
            // Đây là tra cứu chính sách, không phải các nhánh tính phí.
            DeliveryPolicy policy = policies.get(request.type);
            return policy.quote(request); // Dynamic dispatch
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static void assertScenario(
            LegacyDeliveryService before,
            DeliveryService after,
            DeliveryRequest request,
            long expectedFee,
            int expectedDays) {

        DeliveryQuote oldQuote = before.quote(request);
        DeliveryQuote newQuote = after.quote(request);

        // Kiểm tra yêu cầu nghiệp vụ bằng kỳ vọng độc lập.
        check(oldQuote.feeVnd == expectedFee,
                "Old fee differs from requirement");
        check(oldQuote.etaDays == expectedDays,
                "Old ETA differs from requirement");
        check(newQuote.feeVnd == expectedFee,
                "New fee differs from requirement");
        check(newQuote.etaDays == expectedDays,
                "New ETA differs from requirement");

        // Đồng thời xác nhận hành vi cũ/sau refactor tương đương.
        check(oldQuote.feeVnd == newQuote.feeVnd,
                "Fee changed after refactoring");
        check(oldQuote.etaDays == newQuote.etaDays,
                "ETA changed after refactoring");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        LegacyDeliveryService before = new LegacyDeliveryService();

        DeliveryService after = new DeliveryService(Map.of(
                DeliveryType.STANDARD, new StandardPolicy(),
                DeliveryType.EXPRESS, new ExpressPolicy(),
                DeliveryType.PICKUP, new PickupPolicy()
        ));

        // Dưới ngưỡng: 20.000 + 5.000 × 2 = 30.000.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.STANDARD,
                                    499_999L, 2),
                30_000L, 5);

        // Đúng ngưỡng miễn phí.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.STANDARD,
                                    500_000L, 2),
                0L, 5);

        // Giao nhanh: 45.000 + 10.000 × 3 = 75.000.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.EXPRESS,
                                    100_000L, 3),
                75_000L, 1);

        // Tự đến lấy: không tính phí và không có ngày giao.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.PICKUP,
                                    0L, 1),
                0L, 0);

        // Các quy tắc dữ liệu không hợp lệ vẫn được giữ.
        expectInvalid(() ->
                new DeliveryRequest(DeliveryType.STANDARD, -1L, 1));
        expectInvalid(() ->
                new DeliveryRequest(DeliveryType.EXPRESS, 100L, 0));
        expectInvalid(() ->
                new DeliveryRequest(null, 100L, 1));

        System.out.println("4 scenarios + 3 invalid inputs: PASS");
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac DeliveryRefactoringDemo.java
java DeliveryRefactoringDemo
~~~

Khi các phép kiểm tra đều đạt:

~~~text
4 scenarios + 3 invalid inputs: PASS
~~~

Chương trình cố tình viết kiểm chứng **hai chiều**: so sánh giá trị trước/sau refactor, đồng thời so sánh với kỳ vọng nghiệp vụ độc lập. Chỉ so hai phiên bản với nhau là chưa đủ, vì cả hai hoàn toàn có thể cùng thực hiện sai một công thức.

### Điều gì đã thay đổi và điều gì giữ nguyên?

| Nội dung | Trước refactor | Sau refactor |
|---|---|---|
| Công thức giao thường | Nhánh STANDARD trong LegacyDeliveryService | StandardPolicy.quote |
| Công thức giao nhanh | Nhánh EXPRESS | ExpressPolicy.quote |
| Tự đến lấy | Nhánh PICKUP | PickupPolicy.quote |
| Chọn loại phù hợp | switch theo enum | Map ánh xạ enum → DeliveryPolicy |
| Chạy hành vi theo loại | Thực thi nhánh đã chọn | Gọi policy.quote; JVM chọn implementation |
| Điều kiện miễn phí tại 500.000 | Giữ nguyên | Giữ nguyên |
| Kết quả và quy tắc kiểm tra dữ liệu | Hợp đồng gốc | Giữ nguyên |

### Vì sao đây là đa hình?

Biến policy có kiểu **DeliveryPolicy**, nhưng đối tượng thực tế có thể là StandardPolicy, ExpressPolicy hoặc PickupPolicy. Khi gọi policy.quote(request), JVM thực thi phương thức override trên class thực tế. Đoạn DeliveryService không cần biết công thức nào được chạy.

Đây là **đa hình lúc chạy (runtime polymorphism)** qua interface và method overriding, không chỉ là việc đổi switch thành ba phương thức private. Nếu ba công thức vẫn nằm trong một class với điều kiện rẽ nhánh theo enum, hành vi theo loại chưa được phân phối cho từng đối tượng.

## 4. Các bước refactor có thể áp dụng trong dự án

1. **Khoanh vùng điều kiện theo loại.** Tìm các nhánh đang kiểm tra cùng một type/discriminator như enum, mã chuỗi hoặc instanceof. Xác định nhánh nào thật sự thuộc cùng một hành vi.
2. **Viết characterization tests.** Ghi nhận kết quả hiện tại của các input quan trọng: loại phổ biến, loại ít dùng, ngưỡng giá, trường hợp lỗi và side effect có ý nghĩa.
3. **Rút ra hợp đồng chung.** Ví dụ DeliveryPolicy.quote(request). Chọn method theo trách nhiệm nghiệp vụ, không tạo interface chỉ để che giấu một switch.
4. **Tách implementation từng loại.** Di chuyển lần lượt công thức và logic đặc thù từ nhánh sang StandardPolicy, ExpressPolicy và PickupPolicy. Mỗi lần di chuyển đều biên dịch/test lại.
5. **Giới hạn nơi lựa chọn implementation.** Dùng factory, dependency injection container hoặc registry Map ở ranh giới tạo/chọn đối tượng. Code nghiệp vụ chỉ nhận hoặc tra về DeliveryPolicy.
6. **Xóa điều kiện cũ khi không còn cần.** Tránh duy trì song song switch cũ và hệ policy mới quá lâu vì rất dễ lệch hành vi.
7. **Chạy test hồi quy và integration test.** Xác nhận đầu ra, exception, hợp đồng collaborator, transaction và tài nguyên bên ngoài nếu ứng dụng thực có sử dụng.

~~~mermaid
sequenceDiagram
    participant C as Client
    participant S as DeliveryService
    participant R as Registry
    participant P as DeliveryPolicy cụ thể
    C->>S: quote(request)
    S->>R: get(request.type)
    R-->>S: policy
    S->>P: quote(request)
    P-->>S: DeliveryQuote
    S-->>C: DeliveryQuote
~~~

## 5. Chọn interface, lớp trừu tượng, subclass hay Strategy?

**Interface + implementation** như ví dụ phù hợp khi các biến thể chia sẻ hợp đồng nhưng công thức khác nhau, không có nhiều trạng thái/chức năng chung cần kế thừa.

**Abstract class + subclass** phù hợp nếu có bất biến và một số bước xử lý mặc định thật sự dùng chung, ví dụ mọi phương thức đều cần một cách kiểm tra dữ liệu thống nhất. Tránh dồn quá nhiều logic điều kiện ngược trở lại lớp cha.

**Strategy pattern (ưu tiên composition)** phù hợp nếu hành vi được chọn theo cấu hình hoặc thay đổi lúc chạy, không đồng nhất với kiểu của thực thể miền. Trong ví dụ, STANDARD/EXPRESS/PICKUP là chính sách giao hàng được gán theo yêu cầu, không nhất thiết cần tạo các lớp con của Order.

**Enum có method riêng cho từng hằng** cũng là một hình thức đa hình trong Java:

~~~java
enum SimpleDelivery {
    STANDARD {
        @Override long fee(int kg) {
            return 20_000L + 5_000L * kg;
        }
    },
    EXPRESS {
        @Override long fee(int kg) {
            return 45_000L + 10_000L * kg;
        }
    };

    abstract long fee(int kg);
}
~~~

Đây là ví dụ ngắn **minh họa cú pháp**, không thay thế hợp đồng tính phí đầy đủ ở mục 3 (chưa có miễn phí theo giá trị đơn, kiểm tra input hoặc xử lý tràn số). Enum-specific method thích hợp với tập biến thể cố định, logic ngắn. Nếu các chính sách phức tạp, cần dependency ngoài hoặc muốn đăng ký implementation mới theo module, các lớp Strategy độc lập thường linh hoạt và dễ kiểm thử hơn.

## 6. Những sai lầm thường gặp

| Sai lầm | Vì sao không đạt mục tiêu? | Hướng xử lý |
|---|---|---|
| Tạo interface nhưng service vẫn switch theo type để gọi các class cụ thể | Caller vẫn nắm chi tiết và phải sửa nhiều nhánh | Đưa lựa chọn sang factory/registry, gọi một hợp đồng |
| Dùng instanceof rồi ép kiểu cho mọi biến thể | Phụ thuộc subtype còn nguyên | Gọi phương thức đa hình; type-check chỉ giữ nếu thật sự cần ở boundary |
| Mọi policy lại tự kiểm tra request.type rồi quyết định xử lý | Điều kiện bị sao chép sang nhiều class | Registry chọn đúng policy; policy chỉ xử lý quy tắc của nó |
| Thêm superclass khổng lồ để chia sẻ nhiều chi tiết không liên quan | Coupling cao, thay lớp cha ảnh hưởng mọi loại | Tách abstraction gọn, ưu tiên composition |
| Di chuyển code đồng thời sửa công thức nghiệp vụ | Khó chứng minh refactor bảo toàn hành vi | Giữ refactor và thay đổi chính sách ở những bước có test riêng |
| Thêm subtype mà quên đăng ký trong registry | Lỗi runtime khi gặp loại mới | Kiểm tra tính đầy đủ đăng ký, fail fast khi khởi tạo |
| Coi mọi switch là code smell | Tạo nhiều lớp cho một điều kiện đơn giản, ổn định | Giữ switch khi dễ hiểu và ít thay đổi |
| Test chỉ xác minh phương thức policy được gọi một lần | Không chứng minh phí giao hàng đúng | Assert feeVnd, etaDays và các ca biên độc lập |

### Bổ sung loại mới có hoàn toàn không cần sửa code cũ?

Không tuyệt đối. Trong kiến trúc dùng enum và Map ở ví dụ, khi thêm SAME_DAY, ta vẫn phải khai báo hằng enum, tạo SameDayPolicy và đăng ký vào Map. Điều quan trọng là **DeliveryService.quote không cần thêm công thức hoặc nhánh mới**. Nếu yêu cầu cho phép plugin đăng ký động, có thể đưa quá trình khám phá implementation vào DI container/factory; đổi lại sẽ có chi phí cấu hình, kiểm tra trùng và xử lý thiếu policy.

### Có nên đưa các field đặc thù vào interface chung?

Không nên bắt mọi loại chứa dữ liệu không dùng tới chỉ để đồng nhất hình dạng. Nếu mỗi loại có đầu vào hoàn toàn khác, cân nhắc request chuyên biệt, abstraction theo hành vi, hoặc ranh giới xác thực rõ ràng. Interface nên thể hiện khả năng chung thật sự, không phải một tập method buộc nhiều lớp override rồi ném UnsupportedOperationException.

### Vấn đề với đối tượng có trạng thái

Các policy trong ví dụ là stateless nên có thể dùng chung an toàn. Nếu một implementation ghi vào field mutable khi phục vụ nhiều request, phải xét vòng đời object, thread safety và đồng bộ. Refactor không được vô tình biến trạng thái riêng của mỗi request thành shared singleton state.

## 7. Kiểm thử để chứng minh không đổi hành vi

Việc thay switch bằng đa hình không được làm mất trường hợp biên. Nên kiểm thử ở hai mức:

- **Contract tests cho DeliveryService:** mỗi loại trả đúng phí và ETA, STANDARD đúng ngưỡng 499.999/500.000, input không hợp lệ bị từ chối, loại mới được đăng ký đầy đủ.
- **Unit tests từng policy:** tập trung vào công thức đặc thù và tràn số. Ví dụ ExpressPolicy.quote với trọng lượng lớn phải dùng phép toán long chính xác hoặc ném ArithmeticException khi tràn.
- **Integration tests khi có hệ thống ngoài:** kiểm tra việc factory/DI tạo đúng policy, thực thi thanh toán/gửi yêu cầu giao hàng với adapter thật trong môi trường kiểm thử.

Ví dụ kiểm thử JUnit 5 riêng cho biên giao thường (giả định file chương trình ở trên nằm cùng package):

~~~java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardPolicyTest {
    @Test
    void freeShippingAppliesAtThreshold() {
        DeliveryRefactoringDemo.DeliveryPolicy policy =
                new DeliveryRefactoringDemo.StandardPolicy();

        var below = policy.quote(
                new DeliveryRefactoringDemo.DeliveryRequest(
                    DeliveryRefactoringDemo.DeliveryType.STANDARD,
                    499_999L, 2));

        var atThreshold = policy.quote(
                new DeliveryRefactoringDemo.DeliveryRequest(
                    DeliveryRefactoringDemo.DeliveryType.STANDARD,
                    500_000L, 2));

        assertEquals(30_000L, below.feeVnd);
        assertEquals(5, below.etaDays);
        assertEquals(0L, atThreshold.feeVnd);
        assertEquals(5, atThreshold.etaDays);
    }
}
~~~

Dùng JUnit Jupiter trong test scope để chạy ví dụ trên. Lưu ý policy.quote nhận DeliveryRequest có type để minh họa contract với hệ thống hiện có, nhưng bản thân StandardPolicy không dựa vào trường type; DeliveryService là nơi quyết định đúng policy từ type. Trong thiết kế trưởng thành, có thể truyền riêng dữ liệu cần thiết cho policy để tránh gây hiểu nhầm rằng policy luôn phải kiểm tra enum.

## 8. Khi nào KHÔNG nên áp dụng?

Một switch ngắn, đứng ở **một nơi**, phân loại mã trạng thái để log hoặc ánh xạ đơn giản thường dễ hiểu hơn cả một cây class. Nếu tập loại là cố định và các hành vi hiếm khi thay đổi, chi phí interface, factory và nhiều file có thể lớn hơn lợi ích.

Cũng không nên thay một phép kiểm tra điều kiện theo **giá trị hoặc trạng thái** bằng subtype chỉ vì có if. Ví dụ ngưỡng miễn phí vận chuyển (orderValueVnd >= 500.000) là quy tắc **bên trong StandardPolicy**, không phải một loại giao hàng mới. Đa hình lựa chọn *họ hành vi* theo loại; các điều kiện nghiệp vụ bên trong mỗi biến thể có thể và nên tiếp tục tồn tại.

Trước khi refactor, đặt ba câu hỏi:

1. Điều kiện có lặp lại ở nhiều hành vi và thường phải sửa đồng thời không?
2. Các biến thể có một hợp đồng chung đủ rõ để caller sử dụng mà không biết class cụ thể không?
3. Bài test hiện có có thể bảo vệ đầu ra, lỗi và side effect khi chuyển code sang implementation khác không?

Nếu cả ba câu trả lời đều có, Replace Conditional with Polymorphism thường đem lại lợi ích rõ rệt.

## 9. Bài tập

**Bài 1 — Thêm SAME_DAY:** Giả sử phí giao trong ngày là 80.000 + 12.000 × kg, ETA = 0 ngày (trong ngày). Thêm DeliveryType.SAME_DAY, SameDayPolicy và đăng ký policy. Viết test độc lập xác minh chính xác công thức, không thêm nhánh vào DeliveryService.quote.

**Bài 2 — Phát hiện regression:** Cố tình đổi ngưỡng miễn phí STANDARD thành lớn hơn 500.000 (thay vì lớn hơn hoặc bằng). Chạy chương trình; case bằng đúng 500.000 phải fail.

**Bài 3 — Nhận diện trùng điều kiện:** Nếu một class khác cũng dùng switch theo DeliveryType để format mô tả dịch vụ, cân nhắc đưa tên hiển thị vào DeliveryPolicy. Giải thích trường hợp việc đưa trách nhiệm định dạng vào policy lại làm giảm cohesion, và đề xuất một collaborator riêng nếu cần.

**Bài 4 — Kiểm tra đăng ký:** Bỏ một policy trong Map; hãy kiểm tra constructor của DeliveryService từ chối cấu hình thiếu bằng IllegalArgumentException trước khi tiếp nhận request.

**Bài 5 — Lựa chọn kỹ thuật:** Với bốn mã trạng thái chỉ đổi màu nhãn trên UI, so sánh một switch ngắn với bốn class. Chọn phương án có độ phức tạp thấp hơn và giải thích vì sao đa hình không phải lúc nào cũng tốt.

## 10. Ghi nhớ

**Replace Conditional with Polymorphism** giúp đưa hành vi biến đổi theo loại vào đúng đối tượng chịu trách nhiệm. Caller gọi hợp đồng chung, Java tự dispatch đến implementation tương ứng. Đây là một kỹ thuật **tái cấu trúc**, nên cần bảo toàn hành vi trong quá trình thực hiện; không nhầm việc thay đổi kiến trúc với việc thay đổi chính sách nghiệp vụ. Chọn đa hình khi nó thực sự giảm nhánh lặp, tăng cohesion, giảm coupling và giúp bổ sung biến thể một cách kiểm soát được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-007">Thay kế thừa bằng kết hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Thay kế thừa bằng kết hợp (Replace Inheritance with Composition) trong Java OOP

## 1. Vấn đề cần giải quyết

**Kế thừa** cho phép một lớp mở rộng lớp khác bằng cú pháp `extends`. Đây là cơ chế hữu ích khi quan hệ phân loại giữa hai lớp thực sự phù hợp với quy tắc **is-a**: đối tượng lớp con có thể được dùng ở mọi nơi chấp nhận lớp cha mà vẫn giữ đúng hợp đồng hành vi.

Tuy nhiên, trong thực tế có trường hợp một lớp kế thừa chỉ vì muốn **tận dụng vài phương thức** của lớp khác. Khi đó lớp con bị ràng buộc vào API, trạng thái và thay đổi của lớp cha. Một thay đổi trong lớp cha có thể ảnh hưởng ngoài ý muốn tới lớp con; lớp con cũng dễ phải ghi đè phương thức không liên quan đến trách nhiệm của mình.

**Thay kế thừa bằng kết hợp (Replace Inheritance with Composition)** là cách tái cấu trúc từ thiết kế "lớp A extends lớp B để mượn hành vi" sang thiết kế "lớp A **chứa một đối tượng** cung cấp hành vi cần dùng và **ủy quyền** công việc cho đối tượng đó".

Mục đích:
- Tách **trách nhiệm nghiệp vụ** khỏi **cơ chế thực thi**.
- Giảm sự phụ thuộc vào chi tiết của lớp cha.
- Cho phép cấu hình các cách thực hiện qua interface.
- Giúp viết unit test bằng fake/stub mà không phải kích hoạt hệ thống thật.
- Giữ nguyên hành vi quan sát được khi refactor, trừ những thay đổi API đã được lên kế hoạch.

Đây **không phải quy tắc cấm kế thừa**. Kế thừa vẫn đúng khi có quan hệ phân loại ổn định và lớp con thỏa hợp đồng của lớp cha. Vấn đề là chọn cơ chế phù hợp với trách nhiệm của đối tượng.

## 2. Phân biệt kế thừa và kết hợp

| Góc nhìn | Kế thừa | Kết hợp (composition) |
| --- | --- | --- |
| Quan hệ mô hình | **is-a**: một loại của lớp cha | **has-a/uses-a**: sở hữu hoặc sử dụng cộng tác viên |
| Java | `class Child extends Parent` | Trường tham chiếu: `private final Capability capability;` |
| Tái sử dụng | Nhận phương thức và cơ chế của lớp cha | Gọi phương thức trên đối tượng được tiêm vào |
| Mức phụ thuộc | Thường gắn chặt vào cấu trúc/hợp đồng lớp cha | Có thể phụ thuộc vào interface nhỏ |
| Thay cách thực hiện | Thường cần lớp con khác hoặc sửa cấu trúc kế thừa | Có thể cung cấp implementation khác khi tạo đối tượng |
| Kiểm thử | Dễ bị kéo theo hành vi của lớp cha | Tiêm fake/mock ở ranh giới cộng tác |
| Chi phí | Ít đối tượng ủy quyền hơn; dễ hiểu nếu is-a thực sự đúng | Thêm field, constructor và mã ủy quyền; cần thiết kế vòng đời |

Lưu ý: trong Java, **kế thừa lớp** bằng `extends` và **thực thi interface** bằng `implements` không đồng nghĩa về mức độ ràng buộc. Một đối tượng vẫn có thể `implements MessageChannel` để tuân thủ hợp đồng, trong khi một đối tượng khác sử dụng `MessageChannel` qua kết hợp.

## 3. Ví dụ trước khi refactor: lớp cảnh báo kế thừa bộ gửi email

Giả sử một hệ thống cần gửi cảnh báo nghiêm trọng cho nhân viên vận hành. Phiên bản đầu dùng email:

~~~java
class EmailSender {
    public void send(String address, String text) {
        System.out.println("EMAIL -> " + address + ": " + text);
    }
}

// Kế thừa chỉ để tận dụng phương thức send().
class CriticalAlert extends EmailSender {
    public void notifyOperator(String address, String incident) {
        send(address, "[CRITICAL] " + incident);
    }
}
~~~

Mã hoạt động khi chỉ có email, nhưng quan hệ `CriticalAlert extends EmailSender` không mô tả đúng nghiệp vụ: **cảnh báo không phải là một loại bộ gửi email**. Cảnh báo chỉ *sử dụng* phương tiện gửi tin.

Khi bổ sung SMS, webhook hoặc kênh thông báo nội bộ, thiết kế này dễ dẫn tới nhân bản các lớp cảnh báo theo từng loại kênh. Lớp cảnh báo cũng thừa hưởng toàn bộ API public/protected của EmailSender, kể cả những phương thức có thể được thêm sau này nhưng không phù hợp với vai trò cảnh báo.

Nếu một lớp con phải ghi đè phương thức cha để ném `UnsupportedOperationException` hoặc thay đổi hậu điều kiện mà caller của lớp cha đang trông đợi, còn có nguy cơ vi phạm **Liskov Substitution Principle (LSP)**. Không phải mọi quan hệ kế thừa đều vi phạm LSP; vấn đề xuất hiện khi lớp con không thể thay thế lớp cha theo hợp đồng.

Có thể hình dung trước và sau:

~~~mermaid
classDiagram
    class EmailSender {
        +send(address, text)
    }
    class CriticalAlertOld {
        +notifyOperator(address, incident)
    }
    EmailSender <|-- CriticalAlertOld : kế thừa để dùng send

    class AlertService {
        -MessageChannel channel
        +sendCritical(recipient, incident)
    }
    class MessageChannel {
        <<interface>>
        +send(recipient, body)
    }
    class EmailChannel {
        +send(recipient, body)
    }
    class SmsChannel {
        +send(recipient, body)
    }
    class RetryingChannel {
        -MessageChannel delegate
        +send(recipient, body)
    }
    AlertService o--> MessageChannel : chứa và ủy quyền
    MessageChannel <|.. EmailChannel
    MessageChannel <|.. SmsChannel
    MessageChannel <|.. RetryingChannel
    RetryingChannel o--> MessageChannel : bọc
~~~

## 4. Thiết kế sau refactor: kết hợp qua interface

Ta tách hai trách nhiệm:
- `AlertService` hiểu nghiệp vụ: thế nào là cảnh báo nghiêm trọng và cách đặt nội dung cảnh báo.
- `MessageChannel` hiểu việc gửi thông điệp. Email, SMS, và kênh có retry là các cách thực hiện khác nhau.

`AlertService` **có một** `MessageChannel`, chứ không **là một** `MessageChannel`.

Quy trình được ủy quyền:

~~~mermaid
sequenceDiagram
    actor Operator as Ứng dụng
    participant A as AlertService
    participant C as MessageChannel
    Operator->>A: sendCritical(recipient, incident)
    A->>A: Kiểm tra dữ liệu và tạo nội dung
    A->>C: send(recipient, "[CRITICAL] ...")
    C-->>A: Trả về hoặc ném lỗi
    A-->>Operator: Hoàn thành hoặc truyền lỗi
~~~

### 4.1. Cấu trúc dự án

Ví dụ dưới đây dùng **Java 17, Maven và JUnit Jupiter 5**. Các lớp production được viết ở từng tệp Java để có thể sao chép và chạy được.

~~~text
composition-demo/
├── pom.xml
└── src/
    ├── main/java/vn/example/
    │   ├── MessageChannel.java
    │   ├── AlertService.java
    │   ├── EmailChannel.java
    │   ├── SmsChannel.java
    │   ├── DeliveryException.java
    │   ├── RetryingChannel.java
    │   └── Main.java
    └── test/java/vn/example/
        └── AlertServiceTest.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>vn.example</groupId>
    <artifactId>composition-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Định nghĩa hợp đồng gửi thông điệp

**src/main/java/vn/example/MessageChannel.java**

~~~java
package vn.example;

@FunctionalInterface
public interface MessageChannel {
    /**
     * Gửi nội dung tới người nhận. Có thể ném RuntimeException nếu lỗi.
     * Trả về bình thường nghĩa là kênh đã xử lý yêu cầu theo hợp đồng.
     */
    void send(String recipient, String body);
}
~~~

Một interface nhỏ đủ cho nhu cầu của AlertService. Nó không ép lớp cảnh báo biết SMTP, HTTP endpoint hay định dạng SMS. Trong dự án thực tế, hợp đồng cần nói rõ `send` đồng nghĩa với đã giao tới nhà cung cấp, đã xếp vào hàng đợi hay đã đến thiết bị nhận; các mức bảo đảm này không giống nhau.

### 4.3. Đối tượng nghiệp vụ chỉ ủy quyền cho cộng tác viên

**src/main/java/vn/example/AlertService.java**

~~~java
package vn.example;

import java.util.Objects;

public final class AlertService {
    private final MessageChannel channel;

    public AlertService(MessageChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public void sendCritical(String recipient, String incident) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is blank");
        }
        if (incident == null || incident.isBlank()) {
            throw new IllegalArgumentException("incident is blank");
        }

        channel.send(recipient, "[CRITICAL] " + incident);
    }
}
~~~

Điểm quan trọng:
1. Constructor injection cho phép cung cấp đối tượng phụ thuộc tại nơi dựng ứng dụng.
2. `final` giữ cộng tác viên cố định trong vòng đời AlertService; không bị thay bất ngờ trong lúc xử lý.
3. Phương thức public biểu đạt **hành vi nghiệp vụ**, không phơi bày toàn bộ API của kênh gửi.
4. Việc validate xảy ra **trước khi gọi kênh**, nên input không hợp lệ không gây tác dụng phụ.
5. AlertService không biết implementation cụ thể; chỉ yêu cầu kênh thực thi hợp đồng `send`.

### 4.4. Hai implementation email và SMS

Để chương trình có thể chạy trực tiếp mà không cần tài khoản bên ngoài, hai lớp dưới đây **in ra console**. Chúng đại diện cho adapter minh họa, không gửi email/SMS thật.

**src/main/java/vn/example/EmailChannel.java**

~~~java
package vn.example;

public final class EmailChannel implements MessageChannel {
    @Override
    public void send(String recipient, String body) {
        System.out.println("EMAIL -> " + recipient + ": " + body);
    }
}
~~~

**src/main/java/vn/example/SmsChannel.java**

~~~java
package vn.example;

public final class SmsChannel implements MessageChannel {
    @Override
    public void send(String recipient, String body) {
        System.out.println("SMS -> " + recipient + ": " + body);
    }
}
~~~

Cả hai tuân thủ cùng interface và không phải là lớp cha của AlertService. Do đó, khi cần phương tiện mới, ta thêm implementation, thay vì cho lớp nghiệp vụ kế thừa một loại phương tiện mới.

### 4.5. Kết hợp nhiều khả năng: wrapper thử lại khi gặp lỗi đã phân loại

Giả sử muốn thử gửi lại khi xảy ra lỗi tạm thời. Nếu dùng kế thừa, người ta dễ tạo các lớp như `RetryingEmailChannel`, `RetryingSmsChannel`, `LoggingRetryingEmailChannel`... gây nhiều tổ hợp lớp.

Với kết hợp, ta tạo một đối tượng **bọc (decorator)** implement cùng interface và chứa một `MessageChannel` khác. Chỉ khi delegate ném `DeliveryException` thì wrapper mới retry.

**src/main/java/vn/example/DeliveryException.java**

~~~java
package vn.example;

/** Trong ví dụ, biểu thị lỗi có thể thử lại. */
public final class DeliveryException extends RuntimeException {
    public DeliveryException(String message) {
        super(message);
    }
}
~~~

**src/main/java/vn/example/RetryingChannel.java**

~~~java
package vn.example;

import java.util.Objects;

public final class RetryingChannel implements MessageChannel {
    private final MessageChannel delegate;
    private final int maxAttempts;

    public RetryingChannel(MessageChannel delegate, int maxAttempts) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void send(String recipient, String body) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                delegate.send(recipient, body);
                return;
            } catch (DeliveryException failure) {
                if (attempt == maxAttempts) {
                    throw failure;
                }
            }
        }
    }
}
~~~

Ta có thể kết hợp tùy ý:
- `new RetryingChannel(new EmailChannel(), 3)`
- `new RetryingChannel(new SmsChannel(), 3)`
- `new AlertService(new EmailChannel())`
- `new AlertService(new RetryingChannel(new SmsChannel(), 3))`

**Đếm đúng số lượt:** `maxAttempts = 3` nghĩa là nhiều nhất **3 lần gửi tổng cộng**, không phải một lượt ban đầu cộng ba lượt retry. Wrapper này không có backoff, timeout hoặc chính sách idempotency; nó chỉ phục vụ việc học cách kết hợp hành vi.

### 4.6. Composition root: chỗ chọn implementation

**src/main/java/vn/example/Main.java**

~~~java
package vn.example;

public final class Main {
    public static void main(String[] args) {
        MessageChannel reliableEmail =
                new RetryingChannel(new EmailChannel(), 3);
        AlertService emailAlerts = new AlertService(reliableEmail);

        AlertService smsAlerts =
                new AlertService(new SmsChannel());

        emailAlerts.sendCritical(
                "ops@example.com", "Database is unavailable");
        smsAlerts.sendCritical(
                "+84900000000", "API latency is high");
    }
}
~~~

Chạy tại thư mục chứa `pom.xml`:

~~~bash
mvn test
mvn -q -DskipTests package
java -cp target/classes vn.example.Main
~~~

Đầu ra demo dự kiến:

~~~text
EMAIL -> ops@example.com: [CRITICAL] Database is unavailable
SMS -> +84900000000: [CRITICAL] API latency is high
~~~

Các lựa chọn cụ thể nằm tại **composition root** — nơi dựng đồ thị đối tượng lúc khởi động ứng dụng. AlertService không có nhánh kiểu `if (channelType.equals("SMS"))` và cũng không tạo `new EmailChannel()` bên trong phương thức nghiệp vụ.

## 5. Unit test: xác minh sự thay thế được và quy tắc retry

Ta kiểm tra chức năng bằng các implementation giả trong bộ nhớ, không cần kết nối email hay SMS thật.

**src/test/java/vn/example/AlertServiceTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AlertServiceTest {
    record Sent(String recipient, String body) {}

    private static final class RecordingChannel implements MessageChannel {
        final List<Sent> messages = new ArrayList<>();

        @Override
        public void send(String recipient, String body) {
            messages.add(new Sent(recipient, body));
        }
    }

    private static final class FlakyChannel implements MessageChannel {
        final int failuresBeforeSuccess;
        int calls;
        int successfulSends;

        FlakyChannel(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public void send(String recipient, String body) {
            calls++;
            if (calls <= failuresBeforeSuccess) {
                throw new DeliveryException("Temporary error");
            }
            successfulSends++;
        }
    }

    @Test
    void sendsCriticalMessageThroughInjectedChannel() {
        RecordingChannel channel = new RecordingChannel();
        AlertService alerts = new AlertService(channel);

        alerts.sendCritical("ops@example.com", "Disk full");

        assertEquals(
                List.of(new Sent("ops@example.com", "[CRITICAL] Disk full")),
                channel.messages);
    }

    @Test
    void invalidInputDoesNotTouchTheChannel() {
        RecordingChannel channel = new RecordingChannel();
        AlertService alerts = new AlertService(channel);

        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical(" ", "Disk full"));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", ""));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical(null, "Disk full"));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", null));

        assertTrue(channel.messages.isEmpty());
    }

    @Test
    void retryingChannelEventuallySucceeds() {
        FlakyChannel flaky = new FlakyChannel(2);
        AlertService alerts = new AlertService(
                new RetryingChannel(flaky, 3));

        alerts.sendCritical("ops@example.com", "Database down");

        assertEquals(3, flaky.calls);
        assertEquals(1, flaky.successfulSends);
    }

    @Test
    void retryingChannelStopsAfterAttemptLimit() {
        FlakyChannel flaky = new FlakyChannel(10);
        AlertService alerts = new AlertService(
                new RetryingChannel(flaky, 2));

        assertThrows(DeliveryException.class,
                () -> alerts.sendCritical(
                        "ops@example.com", "Database down"));

        assertEquals(2, flaky.calls);
        assertEquals(0, flaky.successfulSends);
    }

    @Test
    void unexpectedExceptionIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        MessageChannel broken = (recipient, body) -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("Bad configuration");
        };

        AlertService alerts =
                new AlertService(new RetryingChannel(broken, 5));

        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", "Disk full"));
        assertEquals(1, calls.get());
    }

    @Test
    void dependenciesAndRetryLimitMustBeValid() {
        assertThrows(NullPointerException.class,
                () -> new AlertService(null));
        assertThrows(NullPointerException.class,
                () -> new RetryingChannel(null, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryingChannel(new RecordingChannel(), 0));
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Với đúng các tệp ở trên, JUnit có **6 phương thức `@Test`**. Các ca kiểm tra không cần môi trường mạng, có thể chủ động dựng tình huống thành công, lỗi tạm thời và lỗi không được retry.

| Kiểm thử | Điều được xác minh | Giá trị của thiết kế kết hợp |
| --- | --- | --- |
| sendsCriticalMessageThroughInjectedChannel | Nội dung và người nhận được chuyển đúng tới cộng tác viên | Dùng fake thay email thật mà không thay đổi AlertService |
| invalidInputDoesNotTouchTheChannel | Input sai bị chặn trước tác dụng phụ | Quy tắc nghiệp vụ độc lập implementation |
| retryingChannelEventuallySucceeds | Thất bại 2 lần, thành công lần 3 | Wrapper có thể bọc một implementation bất kỳ |
| retryingChannelStopsAfterAttemptLimit | Giới hạn tối đa 2 lần gọi được tuân thủ | Chính sách retry có thể kiểm thử riêng |
| unexpectedExceptionIsNotRetried | Lỗi không thuộc loại retry không bị gọi lặp | Ranh giới chính sách lỗi rõ ràng |
| dependenciesAndRetryLimitMustBeValid | Không tạo object ở trạng thái thiếu phụ thuộc | Constructor xác lập bất biến |

Vì AlertService nhận một `MessageChannel`, việc kiểm thử cả các trường hợp gửi email, SMS hoặc fake dùng chung phần nghiệp vụ trở nên đơn giản. Các adapter giao tiếp dịch vụ ngoài vẫn phải có **integration test riêng** để xác minh API, cấu hình và lỗi thực tế.

## 6. Các bước tái cấu trúc từ kế thừa sang kết hợp

Không nên xóa `extends` ngay khi chưa hiểu các caller và hợp đồng public. Trình tự an toàn:

1. **Xác nhận quan hệ hiện tại.** Hỏi "AlertService có thực sự là EmailSender không?" và "nó kế thừa vì quan hệ phân loại hay chỉ muốn dùng `send`?".
2. **Chốt hành vi đang sử dụng.** Viết test cho thông điệp, tham số, lỗi và số lần gửi. Đặc biệt kiểm tra code hiện tại có phụ thuộc vào việc lớp con được dùng như lớp cha hay không.
3. **Tìm phụ thuộc tối thiểu.** Nếu lớp con chỉ cần `send(recipient, body)`, tạo interface `MessageChannel` thay vì kéo toàn bộ API của EmailSender vào đối tượng mới.
4. **Tạo trường kết hợp.** Thêm `private final MessageChannel channel` và constructor nhận interface. Nếu chuyển đổi theo từng bước, giữ API cũ bằng phương thức ủy quyền ngắn trong giai đoạn chuyển tiếp.
5. **Đổi các lời gọi kế thừa.** Thay `send(...)` bằng `channel.send(...)`. Test hồi quy phải tiếp tục đạt.
6. **Chuyển các caller.** Nơi trước đây tạo `new CriticalAlert()` nay cung cấp implementation phù hợp. Nếu có API nhận `EmailSender` và truyền `CriticalAlert`, cần phân tích thiết kế hoặc adapter chuyển tiếp; bỏ `extends` sẽ là **thay đổi tương thích kiểu**.
7. **Loại bỏ kế thừa dư thừa.** Sau khi rà soát các caller và điều kiện tương thích, xóa `extends` và các override chỉ tồn tại vì lớp cha.
8. **Kiểm chứng cả thiết kế lẫn hành vi.** Biên dịch, unit test, integration test cần thiết, kiểm tra API public và khả năng chọn implementation theo cấu hình.

~~~mermaid
flowchart TD
    A["Lớp con extends lớp cha chỉ để dùng một chức năng"] --> B["Khoanh vùng hợp đồng và các caller"]
    B --> C["Viết test hồi quy"]
    C --> D["Tạo interface cho chức năng cần dùng"]
    D --> E["Tiêm đối tượng qua constructor"]
    E --> F["Đổi lời gọi kế thừa thành ủy quyền"]
    F --> G["Chuyển caller và kiểm tra tính tương thích"]
    G --> H{"Test và hợp đồng đạt?"}
    H -->|Không| I["Chỉnh sai lệch hoặc adapter chuyển tiếp"]
    I --> G
    H -->|Có| J["Bỏ extends dư thừa, rà soát thiết kế"]
~~~

### 6.1. Khi nào cần giữ adapter tương thích?

Ví dụ, nếu một thư viện công khai cung cấp `CriticalAlert extends EmailSender`, code bên ngoài có thể viết:

~~~java
// Ví dụ từ API cũ:
EmailSender sender = new CriticalAlert();
~~~

Sau khi đổi CriticalAlert thành lớp có trường MessageChannel, dòng trên **không còn biên dịch**. Đây không phải lỗi thuật toán nhưng là breaking change đối với caller. Cần xác định yêu cầu tương thích nhị phân/nguồn, lên kế hoạch version hoặc dùng một lớp adapter công khai trong thời gian chuyển đổi.

Trong một codebase nội bộ có toàn quyền sửa caller, có thể di chuyển gọn hơn. Với API đã phát hành, thay đổi quan hệ kế thừa cần đánh giá kỹ hơn việc thay phương thức private.

## 7. Strategy, Decorator và Dependency Injection liên quan thế nào?

**Composition** là quan hệ một đối tượng chứa/sử dụng đối tượng khác. Nó là cơ chế nền.

**Strategy pattern** sử dụng các implementation có thể thay thế nhau cho cùng một interface. Trong bài, `EmailChannel` và `SmsChannel` là các chiến lược gửi khác nhau; chọn chiến lược tại lúc tạo AlertService.

**Decorator pattern** bọc một implementation bằng một implementation cùng interface để bổ sung hành vi. `RetryingChannel` vừa implement `MessageChannel` vừa chứa `MessageChannel`, thêm chính sách retry nhưng không buộc sửa EmailChannel/SmsChannel.

**Dependency Injection (DI)** là cách cung cấp collaborator từ bên ngoài. `new AlertService(new SmsChannel())` đã là constructor injection; không cần framework DI mới dùng được kết hợp.

| Câu hỏi | Khái niệm phù hợp |
| --- | --- |
| Đối tượng A sử dụng năng lực của B như thế nào? | Composition + delegation |
| Chọn email hay SMS cho cùng một nhu cầu? | Strategy |
| Thêm retry hoặc logging mà không sửa kênh cơ sở? | Decorator |
| Ai khởi tạo và đưa kênh vào AlertService? | Dependency injection / composition root |

Các mẫu này **có thể phối hợp**, không loại trừ nhau. `AlertService` dùng Strategy được tiêm qua constructor; Strategy ấy có thể là Decorator bọc một adapter khác.

## 8. Khi nào kế thừa vẫn là lựa chọn hợp lý?

Kế thừa có giá trị khi lớp con là một dạng chuyên biệt đúng nghĩa của lớp cha, dựa trên hợp đồng ổn định và không thay đổi tiền/hậu điều kiện làm caller bị bất ngờ. Ví dụ một hierarchy nội bộ có các hook được thiết kế công khai để mở rộng hoặc một base class ổn định trong framework. Dù vậy, vẫn cần test hợp đồng của lớp cha trên các lớp con khi điều này có ý nghĩa.

Ngược lại, nếu lớp con ghi đè nhiều phương thức để làm rỗng, ném lỗi, hoặc cần biết trường `protected` của lớp cha để giữ trạng thái nhất quán, đó là tín hiệu nên xét lại thiết kế.

Một quyết định thực tế:

| Dấu hiệu | Hướng ưu tiên |
| --- | --- |
| "A là một B" và có thể thay B theo đúng hợp đồng | Có thể dùng kế thừa |
| "A có một B", "A dùng dịch vụ của B" | Kết hợp |
| Cần chọn nhiều cách thực hiện theo môi trường | Kết hợp qua interface |
| Cần ghép logging, retry, metrics theo lớp | Kết hợp/Decorator |
| Các lớp con phình ra theo mọi tổ hợp tính năng | Tách chức năng độc lập, kết hợp |
| Thay lớp cha khiến nhiều lớp con hỏng ngoài dự kiến | Giảm ràng buộc với lớp cha, xem lại kết hợp |
| Chỉ có một thuật toán nhỏ, không có lý do thay thế | Có thể để trực tiếp trong lớp, tránh abstraction thừa |

**Đừng tạo interface cho mọi lớp một cách máy móc.** Nếu một phép tính thuần túy đơn giản không có nhiều implementation hợp lý hoặc ranh giới cần cô lập, thêm interface và đối tượng delegate chỉ làm tăng công việc đọc code.

## 9. Các lỗi thường gặp khi chuyển đổi

### 9.1. Chuyển extends thành field nhưng vẫn lộ toàn bộ API

~~~java
// Ví dụ cách chuyển chưa tốt:
class PoorAlertService {
    private final EmailChannel email = new EmailChannel();

    void sendRaw(String recipient, String body) {
        email.send(recipient, body);
    }
}
~~~

Nó đã dùng kết hợp về cú pháp, nhưng vẫn tự khóa vào `EmailChannel` cụ thể và public API còn phản ánh hoạt động gửi thô thay vì nghiệp vụ cảnh báo. Nếu nhu cầu thực sự có nhiều kênh, nhận `MessageChannel` qua constructor sẽ tách trách nhiệm tốt hơn.

### 9.2. Biến một service thành "God Object"

Dùng kết hợp không có nghĩa một lớp được giữ hàng chục dependency và quyết định mọi thứ. Nếu AlertService vừa tính tiền, vừa phân quyền, vừa lập lịch, vừa gửi email, vấn đề chính vẫn là trách nhiệm quá lớn. Hãy tách use case, policy và adapter theo nhu cầu.

### 9.3. Retry bừa các thao tác không idempotent

Ví dụ retry chỉ định nghĩa cho `DeliveryException` của kênh. Trong dịch vụ thật, request đầu tiên có thể đã được nhà cung cấp tiếp nhận nhưng phản hồi bị mất; gửi lại có thể tạo hai email/SMS. Với các hành động quan trọng như thanh toán, cần idempotency key, lưu trạng thái bền vững, truy vấn/đối soát, backoff và quy tắc retry cụ thể. Composition giúp **đặt** chính sách tại lớp thích hợp, không tự bảo đảm chính sách ấy an toàn.

### 9.4. Nhầm lẫn kết hợp với kế thừa interface

`EmailChannel implements MessageChannel` là sử dụng interface để khai báo hợp đồng. `AlertService` kết hợp `MessageChannel` bởi nó có trường `channel`. Hai quan hệ cùng xuất hiện là điều bình thường và có mục đích khác nhau.

### 9.5. Bỏ qua vòng đời và thread-safety

Nếu nhiều thread dùng chung một collaborator có trạng thái thay đổi, cần xác định collaborator có an toàn đồng thời hay không. `private final` chỉ cố định **tham chiếu**, không bảo đảm đối tượng được tham chiếu là immutable hoặc thread-safe.

## 10. Thực hành có hướng dẫn

**Bài tập 1 — Thêm kênh Webhook.** Tạo `WebhookChannel implements MessageChannel` với implementation demo chỉ ghi URL và thông điệp vào danh sách. Dùng `new AlertService(new WebhookChannel(...))` và kiểm tra nội dung cảnh báo, không sửa AlertService.

**Bài tập 2 — Thêm LoggingChannel.** Viết một decorator bọc `MessageChannel` để ghi số lần gọi. Thử `new LoggingChannel(new RetryingChannel(new EmailChannel(), 3))` và `new RetryingChannel(new LoggingChannel(new EmailChannel()), 3)`. Hai cách sắp thứ tự wrapper có thể ghi các số liệu khác nhau; giải thích vì sao.

**Bài tập 3 — Đánh giá test retry.** Viết fake thất bại lần 1 rồi thành công lần 2. Kiểm tra maxAttempts = 1 thì lỗi được truyền ra, maxAttempts = 2 thì thành công. Đảm bảo không retry `IllegalArgumentException`.

**Bài tập 4 — Nhận diện quan hệ.** Cho các câu: "Car dùng Engine", "SavingsAccount là một Account", "OrderService dùng EmailSender", "CSV exporter là một Formatter". Với mỗi câu, chỉ ra đâu là quan hệ dữ liệu/nghiệp vụ thật, đâu là khả năng có thể thay thế; giải thích khi nào `extends`, `implements` hoặc field kết hợp là hợp lý.

**Bài tập 5 — Đánh giá API public.** Tìm những caller đang sử dụng đối tượng lớp con dưới kiểu lớp cha trước refactor. Ghi lại các lời gọi phụ thuộc quan hệ kế thừa và đề xuất adapter/đường chuyển đổi trước khi bỏ extends.

### Checklist sau refactor

- [ ] Vai trò của mỗi lớp có thể giải thích bằng một trách nhiệm rõ ràng.
- [ ] Lớp sử dụng phụ thuộc vào interface nhỏ, không biết cấu trúc của adapter cụ thể khi không cần thiết.
- [ ] Không để lộ các phương thức kế thừa không phù hợp với ngữ nghĩa lớp sử dụng.
- [ ] Các trường hợp thành công, lỗi, validation và giới hạn retry có test.
- [ ] Các caller và hợp đồng public được kiểm tra về tính tương thích.
- [ ] Các tác dụng phụ thực tế được kiểm thử tích hợp ở ranh giới hạ tầng.
- [ ] Kết hợp được dùng vì làm thiết kế rõ hơn, không chỉ để tránh từ khóa `extends`.

**Kết quả cần đạt:** Bạn có thể thay phương tiện gửi cảnh báo hoặc bổ sung retry bằng cách **lắp ghép đối tượng**, không sửa lớp nghiệp vụ và không tạo thêm phân cấp lớp con cho mỗi tổ hợp tính năng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-008">Gộp hoặc loại bỏ trừu tượng không cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Gộp hoặc loại bỏ trừu tượng không cần thiết trong OOP

> **Chủ đề:** Tái cấu trúc (Refactoring) — làm thiết kế Java đơn giản hơn bằng cách gộp những lớp, interface hoặc phương thức trung gian không còn mang trách nhiệm rõ ràng, đồng thời giữ nguyên hành vi mà client đang sử dụng.

Một thiết kế OOP tốt không được đánh giá bằng **số lượng interface, class hay pattern**. Trừu tượng hóa chỉ hữu ích khi nó mô tả một khái niệm đáng đặt tên, che giấu sự biến đổi, bảo vệ ranh giới trách nhiệm hoặc tạo ra khả năng thay thế có nhu cầu thực tế.

Khi một lớp chỉ chuyển tiếp mọi lời gọi sang lớp khác, một interface chỉ lặp lại chính xác API của implementation mà không phục vụ hợp đồng riêng, hoặc một hierarchy tồn tại do dự đoán tính năng chưa xảy ra, hệ thống có thể trở nên khó hiểu hơn. Việc loại bỏ những tầng này thường được gọi là **Inline Class**, **Remove Middle Man**, **Collapse Hierarchy** hoặc **Inline Method**, tùy cấu trúc đang được đơn giản hóa.

**Điểm quan trọng:** Không phải interface có một implementation nào cũng thừa. Một interface đơn lẻ vẫn có thể định nghĩa **ranh giới IO**, hợp đồng thư viện, cơ chế plugin, hoặc một phép thay thế cần thiết trong kiến trúc. Hãy dựa vào lý do tồn tại và chi phí thay đổi thực tế trước khi quyết định xóa.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **trừu tượng hữu ích** với **tầng chuyển tiếp không thêm giá trị**.
- Nhận ra các dấu hiệu như `Middle Man`, `Speculative Generality`, hierarchy thừa, wrapper chỉ forward, method được tách quá mức.
- Chọn đúng kỹ thuật: `Inline Class`, `Remove Middle Man`, `Collapse Hierarchy`, `Inline Method` hoặc đơn giản hóa một tham số/adapter.
- Refactor theo từng bước, xác nhận kết quả bằng **regression/contract tests**.
- Cân nhắc rủi ro khi API đã được công bố, DI container, framework, serialization hoặc khả năng mở rộng thật sự phụ thuộc abstraction.
- Biết khi nào **giữ nguyên interface**, dù code hiện tại mới có một implementation.

## 2. Một abstraction hữu ích đang giải quyết việc gì?

Ví dụ một hệ thống thanh toán có interface:

~~~java
interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
}
~~~

Ứng dụng có thể chỉ chạy với một cổng thanh toán lúc này, nhưng `PaymentGateway` vẫn đáng giữ nếu nó tách nghiệp vụ khỏi nhà cung cấp dịch vụ, định nghĩa hợp đồng lỗi, tạo ranh giới network IO hoặc cho phép chạy sandbox trong môi trường kiểm thử.

Ngược lại:

~~~java
interface GreetingPort {
    String greeting(String name);
}

final class GreetingPortImpl implements GreetingPort {
    private final GreetingEngine engine = new GreetingEngine();

    @Override
    public String greeting(String name) {
        return engine.greeting(name); // không có logic, chính sách hoặc hợp đồng riêng
    }
}

final class GreetingEngine {
    String greeting(String name) {
        return "Xin chào, " + name;
    }
}
~~~

Nếu `GreetingPort` và `GreetingPortImpl` chỉ được dùng nội bộ tại một nơi, không phải API ổn định, không có thay thế thực tế và không đóng vai trò ranh giới kiến trúc, có thể đơn giản thành:

~~~java
final class Greeter {
    String greeting(String name) {
        return "Xin chào, " + name;
    }
}
~~~

Đây là **ví dụ có điều kiện**. Nếu `GreetingPort` đang được một thư viện công khai, DI module, plugin hay client bên ngoài sử dụng, xóa nó có thể gây lỗi biên dịch hoặc phá tương thích. Trước tiên phải kiểm tra người dùng thực tế của API.

| Câu hỏi | Dấu hiệu nên giữ | Dấu hiệu có thể gộp |
|---|---|---|
| Có **nhiều hành vi hợp lệ** cần thay thế không? | Strategy/plugin/adapter có nhiều lựa chọn thật | Chỉ một đường đi, không có biến thể được yêu cầu |
| Có **ranh giới hạ tầng** không? | API, DB, network, message broker, thời gian, file system | Wrapper thuần logic chuyển tiếp trong cùng module |
| Abstraction có **hợp đồng độc lập** không? | Bảo đảm hành vi, lỗi, quyền hạn và invariants riêng | Chữ ký và ý nghĩa hoàn toàn trùng lớp được bọc |
| Có giúp **giảm phụ thuộc** không? | Domain không cần biết về framework/vendor | Người đọc phải đi qua nhiều file mà phụ thuộc không giảm |
| Đã có **client bên ngoài** chưa? | Được phát hành/serialize/reflect/DI theo tên type | Chỉ private/internal call site, có thể chỉnh đồng bộ |
| Có **lý do thay đổi độc lập** không? | Các lớp thường phát triển khác nhau | Cả hai luôn thay đổi cùng lúc và không tạo ranh giới có ý nghĩa |
| Có **lợi ích test thật** không? | Tách external side effect, kiểm thử hợp đồng độc lập | Mock một lớp chỉ forward và test lặp lại cùng logic |

**Không dùng các luật cứng** kiểu “một implementation thì phải xóa interface”, “class dưới 20 dòng thì gộp” hay “mọi delegate đều thừa”. Giá trị của abstraction nằm ở trách nhiệm và tính ổn định của hợp đồng.

## 3. Dấu hiệu phổ biến của sự trừu tượng hóa quá mức

### Middle Man — lớp trung gian gần như chỉ ủy quyền

~~~java
final class OrderFacade {
    private final OrderService service;

    OrderFacade(OrderService service) {
        this.service = service;
    }

    long total(Order order) {
        return service.total(order);
    }
}
~~~

Nếu facade không thêm ý nghĩa cho API, không kiểm tra quyền, không điều phối nhiều collaborator, không dịch ngoại lệ và không ổn định hóa ranh giới, client có thể gọi `OrderService` trực tiếp. Nhưng facade là cần thiết nếu nó đại diện một **use case** hoặc che giấu sự phức tạp thật.

### Speculative Generality — chuẩn bị trước cho tương lai chưa có

Ví dụ tạo `AbstractReportFactoryBuilderBase`, `ReportFactoryProvider` và ba tầng interface chỉ vì “sau này có thể thêm định dạng”. Nếu hiện tại chỉ một loại báo cáo và chưa có yêu cầu thay đổi, các tầng đó có thể làm onboarding và debugging khó hơn.

### Collapsed responsibility — nhiều lớp chia nhau một trách nhiệm nhỏ

Một chuỗi `TaxInputWrapper → TaxCalculatorFacade → TaxCalculatorImpl` có thể chỉ truyền cùng hai tham số và trả lại một số nguyên. Nếu không có lý do tách biệt, đưa logic về class thể hiện rõ trách nhiệm sẽ làm mã ngắn đường đi hơn.

### Hierarchy không tạo đa hình có ý nghĩa

~~~java
class BaseOrderPrinter {
    String format(Order order) {
        return order.toString();
    }
}
final class DefaultOrderPrinter extends BaseOrderPrinter { }
~~~

Nếu subclass không có hành vi riêng, không có contract cho extension và inheritance chỉ tồn tại để thêm tên class, có thể bỏ cấp kế thừa sau khi kiểm tra các client phụ thuộc.

### Tiny Method không làm rõ ý định

~~~java
long add(long a, long b) { return a + b; }
long sum(long a, long b) { return add(a, b); }
~~~

Một phương thức chỉ có tác dụng gọi một phương thức khác đôi khi đáng inline. Tuy nhiên, các tên như `taxableAmount`, `validateAccess` có thể đại diện **ngữ nghĩa miền** hoặc một điểm kiểm soát tập trung; chúng đáng giữ dù rất ngắn.

## 4. Ví dụ Java chạy được: bỏ hai tầng forwarding dư thừa

**Bối cảnh:** Dịch vụ checkout tính phí vận chuyển và giảm giá. Mã cũ có ba tầng:

1. `CheckoutFacade` chỉ ủy quyền `quote`.
2. `PricingAdapter` implement `PricingPort` nhưng chỉ gọi `PricingEngine`.
3. `PricingEngine` thực sự kiểm tra đầu vào và tính giá.

Không có network/database/plugin hay chính sách bổ sung ở hai tầng trung gian trong ví dụ. Hệ thống nội bộ chỉ có một client sử dụng API `Checkout`. Ta giữ abstraction này là **hợp đồng mà client thấy**, nhưng thay chuỗi forward nội bộ bằng `SimplifiedCheckout` trực tiếp thực hiện nghiệp vụ.

### Hợp đồng cần giữ nguyên

- `subtotalCents` là số cent từ `0` đến `1_000_000`, bao gồm hai biên.
- Khách VIP được giảm **10%** (`subtotal / 10`, làm tròn xuống) và miễn phí vận chuyển.
- Khách thường không được giảm giá. Đơn từ **100.000 cent trở lên** miễn phí vận chuyển; nhỏ hơn thì phí **15.000 cent**.
- `totalCents = subtotalCents - discountCents + shippingCents`.
- Đầu vào ngoài miền hợp lệ ném `IllegalArgumentException`.
- Hàm báo giá không có side effect và cho kết quả ổn định với cùng đầu vào.

Tạo tệp `AbstractionRemovalDemo.java` bằng JDK **17 trở lên**:

~~~java
import java.util.List;
import java.util.Random;

public class AbstractionRemovalDemo {
    private static final long MAX_SUBTOTAL = 1_000_000L;
    private static final long FREE_SHIPPING_AT = 100_000L;
    private static final long SHIPPING_FEE = 15_000L;

    // Hợp đồng công khai với client trong ví dụ.
    interface Checkout {
        Invoice quote(long subtotalCents, boolean vip);
    }

    // Record: so sánh giá trị bằng equals(), không cần setter.
    record Invoice(long discountCents, long shippingCents, long totalCents) {}

    private static void validate(long subtotalCents) {
        if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
            throw new IllegalArgumentException("Invalid subtotalCents");
        }
    }

    // BEFORE: một interface nội bộ không có biến thể thực tế.
    interface PricingPort {
        Invoice calculate(long subtotalCents, boolean vip);
    }

    // Lớp chứa logic nghiệp vụ ở thiết kế cũ.
    static final class PricingEngine {
        Invoice calculate(long subtotalCents, boolean vip) {
            validate(subtotalCents);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                ? 0 : SHIPPING_FEE;
            return new Invoice(
                discount, shipping, subtotalCents - discount + shipping
            );
        }
    }

    // Chỉ chuyển tiếp, không chuyển đổi dữ liệu/hợp đồng/ngoại lệ.
    static final class PricingAdapter implements PricingPort {
        private final PricingEngine engine = new PricingEngine();

        @Override
        public Invoice calculate(long subtotalCents, boolean vip) {
            return engine.calculate(subtotalCents, vip);
        }
    }

    // Facade này cũng chỉ forward vào adapter.
    static final class LegacyCheckout implements Checkout {
        private final PricingPort port = new PricingAdapter();

        @Override
        public Invoice quote(long subtotalCents, boolean vip) {
            return port.calculate(subtotalCents, vip);
        }
    }

    // AFTER: gộp logic nội bộ vào class thực hiện use case.
    // Hợp đồng Checkout mà client sử dụng không đổi.
    static final class SimplifiedCheckout implements Checkout {
        @Override
        public Invoice quote(long subtotalCents, boolean vip) {
            validate(subtotalCents);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                ? 0 : SHIPPING_FEE;
            return new Invoice(
                discount, shipping, subtotalCents - discount + shipping
            );
        }
    }

    record Case(long subtotal, boolean vip, Invoice expected) {}

    // Oracle độc lập: expected value được ghi rõ theo yêu cầu.
    private static final List<Case> CASES = List.of(
        new Case(0, false, new Invoice(0, 15_000, 15_000)),
        new Case(0, true, new Invoice(0, 0, 0)),
        new Case(99_999, false, new Invoice(0, 15_000, 114_999)),
        new Case(100_000, false, new Invoice(0, 0, 100_000)),
        new Case(100_001, false, new Invoice(0, 0, 100_001)),
        new Case(250_001, true, new Invoice(25_000, 0, 225_001)),
        new Case(1_000_000, true, new Invoice(100_000, 0, 900_000))
    );

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // Bộ regression test áp cho bất kỳ implementation của Checkout.
    static void verifyContract(String label, Checkout service) {
        for (Case c : CASES) {
            Invoice actual = service.quote(c.subtotal(), c.vip());
            require(c.expected().equals(actual),
                label + " differs at " + c + "; actual=" + actual);

            require(actual.discountCents() >= 0,
                label + ": negative discount");
            require(actual.totalCents() ==
                    c.subtotal() - actual.discountCents()
                        + actual.shippingCents(),
                label + ": invalid total");
        }

        expectInvalid(() -> service.quote(-1, false));
        expectInvalid(() -> service.quote(MAX_SUBTOTAL + 1, true));
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    // Differential test dùng nhiều input cố định seed.
    static void compareBeforeAfter(Checkout before, Checkout after) {
        Random random = new Random(219L);
        for (int i = 0; i < 1_000; i++) {
            long subtotal = random.nextInt(1_000_001);
            boolean vip = random.nextBoolean();
            Invoice oldResult = before.quote(subtotal, vip);
            Invoice newResult = after.quote(subtotal, vip);
            require(oldResult.equals(newResult),
                "Behavior changed for subtotal=" + subtotal + ", vip=" + vip);
        }
    }

    public static void main(String[] args) {
        Checkout oldDesign = new LegacyCheckout();
        Checkout newDesign = new SimplifiedCheckout();

        verifyContract("legacy", oldDesign);
        verifyContract("simplified", newDesign);
        compareBeforeAfter(oldDesign, newDesign);

        System.out.println("PASS: legacy contract");
        System.out.println("PASS: simplified contract");
        System.out.println("PASS: before/after comparison");
        System.out.println(newDesign.quote(250_001, true));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AbstractionRemovalDemo.java
java AbstractionRemovalDemo
~~~

Kết quả kỳ vọng:

~~~text
PASS: legacy contract
PASS: simplified contract
PASS: before/after comparison
Invoice[discountCents=25000, shippingCents=0, totalCents=225001]
~~~

Các lớp cũ và mới cùng tồn tại **chỉ để minh họa và so sánh trong tệp demo**. Khi áp dụng refactor cho dự án thật, sau khi kiểm chứng và chuyển toàn bộ call site, ta nên xóa các tầng `PricingPort`, `PricingAdapter`, `PricingEngine` và `LegacyCheckout` không còn được dùng. Không duy trì song song hai đường đi sản xuất nếu không có lý do tương thích hoặc chuyển đổi cụ thể.

### Trước và sau refactor

~~~mermaid
flowchart LR
    subgraph Before["Trước: bốn lớp/interface trên đường gọi"]
      A["Client"] --> B["Checkout"]
      B --> C["LegacyCheckout"]
      C --> D["PricingPort"]
      D --> E["PricingAdapter"]
      E --> F["PricingEngine"]
    end
    subgraph After["Sau: một hợp đồng và một implementation"]
      G["Client"] --> H["Checkout"]
      H --> I["SimplifiedCheckout"]
    end
~~~

Điều được cải thiện là **đường đi của hành vi rõ hơn**: không còn phải mở nhiều lớp chỉ để tìm nơi thực sự tính tiền. Điều không thay đổi là lời gọi qua `Checkout`, kiểu `Invoice` trả về và quy tắc input/output.

## 5. Chọn kỹ thuật gộp/loại bỏ đúng vấn đề

| Kỹ thuật | Khi nào dùng | Ví dụ | Rủi ro cần kiểm tra |
|---|---|---|---|
| **Inline Method** | Method chỉ gọi một method khác, tên không truyền thêm ý nghĩa | `int calc() { return addTax(); }` không thêm contract | Tên method có thể là ngôn ngữ miền hữu ích |
| **Inline Class** | Class làm quá ít việc, không có trách nhiệm độc lập | `AddressFormatterHelper` dùng riêng trong `AddressFormatter` | Class được framework/client bên ngoài tham chiếu |
| **Remove Middle Man** | Class phần lớn là chuỗi delegate đơn thuần | `Facade → Adapter → Engine` thuần forward | Delegate có thể là boundary bảo mật, transaction hay IO |
| **Collapse Hierarchy** | Superclass/subclass không khác biệt đáng kể về hành vi/trách nhiệm | `DefaultPrinter extends BasePrinter` mà không bổ sung logic | Hành vi đa hình, binary/source compatibility, override |
| **Remove Unused Abstraction** | Interface/abstract class/factory không còn caller hợp lệ | Interface chỉ tồn tại cho tính năng đã bỏ | Plugin, reflection, DI configuration, client bên ngoài |
| **Simplify Parameter Object** | Object chỉ bọc một giá trị, không có invariant/ý nghĩa riêng | `NameWrapper` không có chính sách và không được tái sử dụng | Có thể là value object cần bảo vệ unit/validation |
| **Replace Hierarchy with Composition** | Kế thừa phức tạp, dùng chỉ để tái sử dụng vài hành vi | Nhiều subclass vì phối hợp hai chiều biến đổi | Không phải lúc nào cũng **giảm** abstraction; đánh giá riêng |

**Đừng gộp trách nhiệm độc lập chỉ để giảm số file.** Nếu `PricingEngine` được nhiều use case dùng hoặc chịu trách nhiệm riêng, giữ nó và chỉ bỏ adapter dư thừa. Chọn mức đơn giản hóa sao cho *cohesion* tăng, *coupling* không xấu đi và test vẫn dễ viết.

## 6. Inline Class và Collapse Hierarchy khác nhau thế nào?

### Inline Class: đưa trách nhiệm nhỏ về nơi nó thực sự thuộc về

Giả sử:

~~~java
final class NameJoiner {
    String fullName(String first, String last) {
        return first + " " + last;
    }
}

final class CustomerLabel {
    private final NameJoiner joiner = new NameJoiner();

    String label(String first, String last) {
        return joiner.fullName(first, last);
    }
}
~~~

Nếu `NameJoiner` chỉ có một caller này, không tái sử dụng, không phụ thuộc IO, không encapsulate luật định dạng chung, có thể inline:

~~~java
final class CustomerLabel {
    String label(String first, String last) {
        return first + " " + last;
    }
}
~~~

Nhưng nếu toàn hệ thống phải tuân theo quy tắc hiển thị tên có địa phương hóa, định dạng độc lập là một trách nhiệm quan trọng. Khi đó nên giữ một dịch vụ/kiểu dữ liệu thể hiện đúng contract của nó.

### Collapse Hierarchy: bỏ mức kế thừa không có khác biệt

Trước:

~~~java
class BaseFormatter {
    String format(int value) {
        return Integer.toString(value);
    }
}

final class DefaultFormatter extends BaseFormatter {
    // Không override, không thêm trạng thái hoặc invariant.
}
~~~

Nếu không có consumer nào cần phân biệt `BaseFormatter` và `DefaultFormatter`, có thể thay bằng một class:

~~~java
final class Formatter {
    String format(int value) {
        return Integer.toString(value);
    }
}
~~~

Trường hợp này không có nghĩa mọi subclass rỗng đều phải xóa: class con có thể được dùng làm **marker type**, được framework tìm qua annotation, đại diện cho một hợp đồng tương thích hoặc sắp nhận extension đã có yêu cầu cụ thể. Phải kiểm tra client, annotation và cấu hình trước khi thay hierarchy.

## 7. Nhận diện ranh giới thật sự trước khi xóa abstraction

Cùng là một tầng gọi chuyển tiếp, nhưng ý nghĩa có thể rất khác:

~~~java
interface Clock {
    java.time.Instant now();
}
~~~

Đây có thể là ranh giới hữu ích để kiểm thử hành vi phụ thuộc thời gian mà không phải chờ đồng hồ thật. Nếu `Clock` giúp domain không phụ thuộc system clock, bỏ nó có thể làm test khó hơn.

~~~java
interface CustomerRepository {
    Customer findById(long id);
}
~~~

Có thể là một ranh giới giữa **domain/application** và **database**, ngay cả khi chỉ một implementation dùng JDBC. Loại bỏ interface có thể kéo SQL/hạ tầng vào nơi không nên phụ thuộc.

Ngược lại:

~~~java
final class IdEchoAdapter {
    long pass(long id) {
        return id;
    }
}
~~~

Nếu đây chỉ là một lớp vô nghĩa, không validation, transformation, boundary hay semantics, không có lý do kiến trúc rõ để giữ.

**Bảng quyết định nhanh:**

| Loại ranh giới | Nên giữ khi… | Cân nhắc gộp khi… |
|---|---|---|
| Domain ↔ database/network | Ẩn side effect, cho phép thay hạ tầng và test ổn định | Lớp trung gian chỉ copy tên, contract đã được định nghĩa ở nơi khác |
| Use case ↔ domain | Điều phối transaction, authorization, nhiều collaborator | Use case chỉ forward không có semantic riêng |
| API ↔ DTO/domain | Validation, versioning, mapping, serialization | Wrapper không chuyển đổi gì và không phải API công khai |
| Strategy/Plugin | Có biến thể thực tế hoặc hợp đồng mở rộng | Chỉ là dự đoán tương lai, không có người sử dụng |
| Value object | Đảm bảo đơn vị, ràng buộc, ý nghĩa miền | Wrapper chỉ đổi tên primitive, không thêm semantic |
| Module facade | Che giấu nhiều subsystem, làm API ổn định | Facade chỉ chuyển tiếp 100% và không che chi tiết nào |

## 8. Quy trình refactor an toàn: bỏ thừa mà vẫn giữ hợp đồng

1. **Xác định đường đi hiện tại.** Vẽ hoặc đọc chuỗi gọi từ public API tới nơi có nghiệp vụ. Đánh dấu mọi class/interface chỉ chuyển tiếp; ghi nhận chúng phục vụ client nào.
2. **Tìm lý do tồn tại.** Kiểm tra dependency, test, DI configuration, annotation, visibility, framework integration và các điểm extension. Đánh dấu boundary cần giữ.
3. **Chốt hành vi công khai.** Viết test từ specification cho input hợp lệ, biên, exception, output và side effect; lấy thêm các trường hợp đặc biệt từ bug thực tế.
4. **Chọn phép biến đổi nhỏ.** Có thể bỏ một lớp delegate trước khi xét gộp engine; không cần xóa cả hierarchy trong một commit lớn.
5. **Chuyển call site một cách nhất quán.** Gọi trực tiếp abstraction còn lại; giữ tên API cần ổn định; tránh để đường đi cũ và mới vô tình xử lý khác nhau.
6. **Chạy compiler, unit, regression và integration tests.** Với DI/reflection, cần kiểm tra khởi động ứng dụng, cấu hình bean, serialization và runtime route.
7. **Đo tác động thực tế.** Xem số bước tìm logic, số class phải chỉnh khi thêm yêu cầu, khả năng test, sự rõ ràng trong review. Loại bỏ tầng thừa có thể đáng giá hơn việc đếm giảm bao nhiêu dòng.
8. **Dọn code chết.** Sau khi chắc chắn không còn consumer, bỏ import, class, interface, file cấu hình và test chi tiết triển khai lỗi thời; giữ các test hành vi.

~~~mermaid
flowchart TD
    A["Tìm các tầng chỉ forward hoặc hierarchy thừa"] --> B["Xác định client và vai trò contract"]
    B --> C{"Có ranh giới/biến thể/lợi ích thật?"}
    C -->|Có| D["Giữ abstraction, cải thiện naming/API nếu cần"]
    C -->|Không| E["Chốt test hành vi và điểm tương thích"]
    E --> F["Inline/gộp một tầng"]
    F --> G["Chạy compile + regression/integration tests"]
    G --> H{"Hợp đồng và client còn đúng?"}
    H -->|Không| I["Sửa hoặc hoàn tác bước refactor"]
    I --> G
    H -->|Có| J["Xóa đường đi dư, review thiết kế"]
~~~

### Thứ tự thao tác khi public API đã có người dùng

Nếu `PricingPort` được công khai trong một thư viện, thay lời gọi client ngay lập tức có thể là **breaking change**. Một hướng an toàn hơn:

- Giữ **adapter tương thích** trong thời gian chuyển đổi và đánh dấu `@Deprecated` nếu phù hợp.
- Công bố API mới, hướng dẫn migration, di chuyển call site đã biết.
- Kiểm tra source/binary compatibility và phiên bản phát hành; chỉ xóa API cũ trong lần phát hành cho phép breaking change.
- Xác minh cả cấu hình DI, plugin, reflection và dữ liệu serialize có dùng tên type cũ không.

Ở một ứng dụng nội bộ được build cùng lúc, việc di chuyển mọi call site có thể thực hiện trực tiếp hơn, nhưng vẫn phải chạy test và kiểm tra runtime.

## 9. Kiểm thử tính đúng đắn của refactor

Khi **mục tiêu là đổi thiết kế, không đổi chức năng**, các test nên kiểm tra *kết quả client quan sát* hơn là số lớp hoặc số method.

| Nhóm test | Ví dụ | Điều được bảo vệ |
|---|---|---|
| Specification test | `quote(100_000, false)` miễn phí vận chuyển | Kết quả nghiệp vụ đúng độc lập với mã cũ |
| Boundary test | 0; 99.999; 100.000; 100.001; 1.000.000 | Không mất nhánh/điều kiện khi inline |
| Negative test | -1; 1.000.001 | Validation và loại lỗi giữ đúng |
| Differential test | 1.000 input random với seed cố định | Phát hiện khác biệt giữa hai đường đi |
| Side-effect test | Không phát sinh thao tác IO/charge ngoài hợp đồng | Bỏ tầng trung gian không tạo tác dụng phụ |
| Integration test | Khởi động DI, gọi API qua transport | Xóa type không phá wiring/runtime |
| Client compilation | Biên dịch module phụ thuộc, nếu có | Không phá chữ ký công khai vô ý |
| Performance check | Đo luồng bị ảnh hưởng nếu có SLA | Không tạo tính toán/IO thừa khi gộp |

### Ví dụ JUnit 5

Nếu dự án đã khai báo `org.junit.jupiter:junit-jupiter` và test cùng package với class demo, thêm tệp `CheckoutRefactoringTest.java`:

~~~java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CheckoutRefactoringTest {
    @Test
    void simplifiedImplementationKeepsBusinessContract() {
        var checkout = new AbstractionRemovalDemo.SimplifiedCheckout();

        assertEquals(
            new AbstractionRemovalDemo.Invoice(0, 0, 100_000),
            checkout.quote(100_000, false)
        );
        assertEquals(
            new AbstractionRemovalDemo.Invoice(25_000, 0, 225_001),
            checkout.quote(250_001, true)
        );
    }

    @Test
    void invalidInputsStillFail() {
        var checkout = new AbstractionRemovalDemo.SimplifiedCheckout();

        assertThrows(IllegalArgumentException.class,
            () -> checkout.quote(-1, false));
        assertThrows(IllegalArgumentException.class,
            () -> checkout.quote(1_000_001, true));
    }

    @Test
    void oldAndNewMatchOnRepresentativeCases() {
        AbstractionRemovalDemo.Checkout oldService =
            new AbstractionRemovalDemo.LegacyCheckout();
        AbstractionRemovalDemo.Checkout newService =
            new AbstractionRemovalDemo.SimplifiedCheckout();

        AbstractionRemovalDemo.verifyContract("old", oldService);
        AbstractionRemovalDemo.verifyContract("new", newService);
        AbstractionRemovalDemo.compareBeforeAfter(oldService, newService);
    }
}
~~~

Trong demo các nested type có quyền truy cập package-private; đặt test cùng package. Ở code production nên tổ chức mỗi type và visibility đúng với kiến trúc của ứng dụng.

**Không chỉ dựa vào phép so sánh phiên bản cũ/mới.** Nếu phiên bản cũ sai từ trước, việc copy đúng lỗi cũ vẫn pass differential test. Các `Invoice` kỳ vọng trong demo được viết **trực tiếp theo yêu cầu**, đóng vai trò oracle độc lập.

## 10. Những sai lầm thường gặp

**Xóa interface chỉ vì có một implementation.** Số implementation không phản ánh đầy đủ giá trị. Một abstraction ở ranh giới DB, IO, clock hay payment gateway có thể là yếu tố bảo vệ domain quan trọng.

**Gộp class theo số dòng.** Hai class mỗi class 10 dòng vẫn nên tách nếu chúng có **lý do thay đổi độc lập**. Ngược lại, một chuỗi class 100 dòng chỉ chuyển tiếp có thể cần đơn giản hóa.

**Chuyển hết logic vào một “God Class”.** Mục đích không phải giảm class bằng mọi giá. Nếu sau gộp class chịu nhiều trách nhiệm không liên quan, khả năng bảo trì sẽ xấu đi.

**Refactor và đổi nghiệp vụ cùng lúc.** Nếu tổng tiền thay đổi do chính sách giá mới, đó là thay đổi chức năng phải được mô tả riêng. Refactor chỉ được coi là giữ hành vi khi contract được xác minh không đổi.

**Không kiểm tra reflection, DI hoặc API ngoài module.** Code có thể compile nhưng ứng dụng lỗi lúc runtime vì bean name, annotation, constructor injection hoặc cấu hình dựa vào class đã xóa.

**Chỉ test chi tiết cấu trúc.** Một test xác nhận `PricingAdapter.calculate()` đã được gọi một lần trở nên vô nghĩa khi `PricingAdapter` bị bỏ. Thay bằng test của `Checkout.quote()` và những side effect thật sự có ý nghĩa.

**Inline phương thức lặp logic ở nhiều nơi.** Bỏ một method chung rồi copy nội dung sang nhiều client có thể tạo `Duplicate Code` và tăng chi phí thay đổi. Chỉ inline khi logic thực sự thuộc về caller và không làm phân tán một quy tắc chung.

**Không cân nhắc ràng buộc kiểu/visibility.** Chuyển method từ class này sang class khác có thể làm lộ field private, mở rộng quyền truy cập hoặc thay đổi overload/generics. Hãy dùng compiler, test và review access modifiers.

## 11. Bài tập thực hành

**Bài 1 — Phân loại:** Với các kiểu `UserRepository` (giao tiếp DB), `UserRepositoryImpl` (SQL thật), `UserRepositoryProxy` (chỉ forward), `UserService` (nghiệp vụ), hãy xác định lớp nào có thể là middle man. Nêu **bằng chứng cần kiểm tra** trước khi bỏ.

**Bài 2 — Inline Class:** Một `NamePrinter` chỉ được một `GreetingService` dùng để nối họ tên và không có định dạng chung. Viết lại để bỏ lớp phụ. Tạo test cho input thường, input rỗng và `null` theo hợp đồng hiện hành.

**Bài 3 — Collapse Hierarchy:** `DefaultDiscountCalculator extends DiscountCalculator` nhưng không override gì và không có client nào dùng kiểu con như một marker. Thiết kế lại thành một class, dự đoán ảnh hưởng đến constructor và các `import`.

**Bài 4 — Refactor có giới hạn:** Trong demo `AbstractionRemovalDemo`, thử chỉ xóa `PricingAdapter` và giữ `PricingEngine` nếu engine được một use case khác tái sử dụng. Đánh giá xem lớp còn lại có trách nhiệm rõ hơn không.

**Bài 5 — Lỗi ở ranh giới:** Cố tình sửa `subtotalCents >= FREE_SHIPPING_AT` thành `subtotalCents > FREE_SHIPPING_AT` ở `SimplifiedCheckout`. Chạy regression tests, giải thích chính xác case nào phát hiện lỗi. Khôi phục điều kiện đúng.

**Bài 6 — Review thật:** Tìm một chuỗi lời gọi dài trong ứng dụng. Vẽ đường gọi, đánh dấu mỗi tầng cung cấp contract, validation, transaction, auth, IO, mapping hay chỉ forward. Đề xuất loại bỏ **ít nhất một tầng có bằng chứng thừa**, viết test hành vi trước và ghi lại thay đổi sau khi chạy test.

## 12. Tiêu chí hoàn thành

Bạn có thể trả lời được ba câu trước khi loại bỏ một abstraction:

1. **Nó đang bảo vệ điều gì?** Hợp đồng, ranh giới phụ thuộc, biến thể có thật hay chỉ là một lời gọi chuyển tiếp?
2. **Việc gộp giúp ích gì?** Giảm số bước tìm logic, giảm chỗ phải sửa, làm rõ trách nhiệm hay đơn giản hóa kiểm thử mà không tạo coupling mới?
3. **Làm sao biết refactor an toàn?** Có test đặc tả, test biên, test ngoại lệ và kiểm tra các client/API chịu ảnh hưởng; kết quả public behavior vẫn đúng.

Khi cả ba câu đều có bằng chứng, việc **gộp hoặc loại bỏ trừu tượng không cần thiết** làm thiết kế OOP bớt vòng vo mà không đánh đổi sự rõ ràng và khả năng mở rộng thực sự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="refactoring-009">Cải thiện thiết kế từng bước với kiểm thử bảo vệ</a>

<details>
<summary>Xem chi tiết</summary>

# Cải thiện thiết kế từng bước với kiểm thử bảo vệ — Java OOP

## 1. Mục tiêu của bài học

**Refactoring (tái cấu trúc)** là thay đổi cách tổ chức bên trong của mã nguồn để code dễ hiểu, dễ mở rộng, dễ kiểm thử hơn, trong khi **giữ nguyên hành vi quan sát được đã cam kết**. Nó có thể bao gồm tách phương thức, chuyển trách nhiệm sang lớp phù hợp, làm rõ tên, xóa lặp và giảm phụ thuộc.

Đổi nhiều thứ cùng lúc khiến ta khó biết lỗi bắt đầu ở đâu. Cách an toàn hơn là áp dụng chu trình ngắn:

**Xác định hành vi → viết kiểm thử bảo vệ → thay đổi thiết kế nhỏ → chạy kiểm thử → xem lại code → lặp tiếp.**

Một **kiểm thử bảo vệ** (safety-net test) là bài test giúp phát hiện thay đổi ngoài ý muốn trong hành vi hiện có. Nó không chứng minh toàn bộ hệ thống hoàn toàn đúng, nhưng tạo một hàng rào hồi quy khi ta sửa cấu trúc.

Đừng nhầm ba loại thay đổi:

| Loại thay đổi | Ý định | Cần kiểm chứng |
|---|---|---|
| Refactoring | Đổi cấu trúc, giữ hợp đồng | Các test hành vi trước/sau vẫn đạt |
| Bug fix | Sửa hành vi sai thành đúng | Test tái hiện bug thất bại trước khi sửa, đạt sau khi sửa |
| Thay đổi tính năng | Thay hoặc mở rộng hợp đồng | Test mới cho yêu cầu mới và test hồi quy cũ |

**Quy tắc thực tế:** Khi một bước refactor bất ngờ làm test đỏ, cần tìm nguyên nhân và xử lý trước khi chuyển sang bước khác. Không xóa assertion chỉ để test xanh trở lại.

## 2. Kiểm thử bảo vệ điều gì?

Hợp đồng của một lớp OOP có thể gồm:

- Giá trị trả về: tổng tiền, trạng thái xử lý, kết quả sắp xếp.
- Dữ liệu bị thay đổi: tồn kho, trạng thái đơn hàng, bộ đếm đã công bố.
- Exception hoặc tín hiệu lỗi được quy định: input rỗng, số lượng âm, dữ liệu trùng.
- Tác dụng phụ có ý nghĩa: gửi thông báo, ghi repository, gọi hệ thống thanh toán.
- Quy tắc biên: đúng ngưỡng giảm giá, thời hạn, 0, 1, rỗng, giới hạn số.

Ngược lại, một test ràng buộc số lần gọi private helper, tên private field, cấu trúc vòng lặp hoặc loại collection tùy ý thường **quá phụ thuộc implementation**. Những chi tiết ấy có thể thay đổi hợp lệ khi refactor.

**Một câu hỏi để review test:** Nếu đổi hoàn toàn thuật toán nhưng client vẫn nhận cùng kết quả và hiệu ứng đã cam kết, bài test có nên tiếp tục PASS không? Với unit test nghiệp vụ thông thường, câu trả lời thường là có.

## 3. Khi code cũ chưa có test: characterization test

Đôi khi ta cần cải thiện lớp cũ nhưng không có tài liệu đầy đủ. **Characterization test** ghi lại hành vi đang thực sự diễn ra, kể cả chi tiết bất ngờ, để nhận biết thay đổi vô tình.

Ví dụ một phương thức tính phí giao hàng hiện dùng điều kiện **từ 700.000 VND trở lên** để miễn phí. Trước khi tách phương thức, ghi lại các case 699.999 và 700.000 VND. Nếu refactor biến dấu >= thành >, test sẽ báo lỗi ở đúng ngưỡng.

Lưu ý: hành vi hiện tại không phải lúc nào cũng là hành vi **mong muốn**. Nếu phát hiện bug cũ, ghi nhận nó, làm rõ yêu cầu, rồi sửa bug trong một bước riêng có test biểu diễn kỳ vọng đúng. Không đóng băng bug chỉ vì characterization test đang xanh.

Cần ưu tiên vài kịch bản có giá trị cao trước: luồng thường dùng, ngưỡng quan trọng, lỗi input, tác dụng phụ, số tiền hoặc tài nguyên có nguy cơ lớn. Không cần viết hàng trăm test trước khi thay một dòng, nhưng cũng không nên refactor vùng rủi ro mà không có kiểm chứng.

## 4. Ví dụ xuyên suốt: cải thiện lớp thanh toán qua nhiều bước

Ta dùng cùng một bài toán để quan sát cấu trúc **trước và sau**:

- Một đơn hàng có danh sách dòng hàng. Mỗi dòng có mã sản phẩm, số lượng dương và đơn giá không âm, tính bằng **VND nguyên**.
- **Subtotal** là tổng của số lượng × đơn giá; một đơn phải có ít nhất một dòng.
- Khách VIP được giảm **10% subtotal**, làm tròn xuống theo chia số nguyên, nếu subtotal **từ 500.000 VND**.
- **Payable** = subtotal − discount.
- **Shipping** = 0 VND nếu payable **từ 700.000 VND**; nếu thấp hơn thì 30.000 VND.
- **Total** = payable + shipping.
- Biên nhận dùng một định dạng văn bản ổn định trong phạm vi bài học.

Phiên bản ban đầu gom toàn bộ validation, tính tiền và format receipt vào một phương thức. Phiên bản sau refactor tách tính giá thành **PricingPolicy**, tạo **ReceiptFormatter** để định dạng, và để **CheckoutService** điều phối hai collaborator.

~~~mermaid
flowchart LR
    subgraph Before["Trước"]
        L["LegacyCheckout: validate + calculate + format"]
    end
    subgraph After["Sau"]
        C["CheckoutService"] --> P["PricingPolicy: validate + calculate"]
        C --> F["ReceiptFormatter: format"]
    end
    T["Bộ kiểm thử hợp đồng"] --> L
    T --> C
~~~

Các bước cải thiện được thực hiện theo thứ tự:
1. Khóa lại các kết quả đầu ra và trường hợp lỗi bằng test.
2. Tách phần tính giá ra phương thức có tên rõ ràng.
3. Nhóm các phương thức tính giá liên quan vào một đối tượng PricingPolicy.
4. Tách trách nhiệm định dạng sang ReceiptFormatter.
5. Cho CheckoutService nhận collaborator qua constructor để luồng chính dễ đọc và dễ thay thế.
6. Sau **mỗi bước**, chạy lại cùng test. Chỉ loại bỏ code cũ khi đã kiểm chứng tương đương.

### Chương trình Java 11 tự chạy

Lưu toàn bộ trong file **SafeRefactoringDemo.java**; ví dụ không cần thư viện bên ngoài. Bản cũ và bản mới cùng triển khai interface Checkout. Một bộ test chạy cho cả hai và so sánh với kết quả **độc lập từ yêu cầu**, tránh tình huống hai implementation cùng sai mà vẫn được coi là tương đương.

~~~java
import java.util.List;
import java.util.Objects;

public class SafeRefactoringDemo {
    static final long VIP_THRESHOLD = 500_000L;
    static final long FREE_SHIPPING_THRESHOLD = 700_000L;
    static final long SHIPPING_FEE = 30_000L;

    static final class Item {
        final String sku;
        final int quantity;
        final long unitPriceVnd;

        Item(String sku, int quantity, long unitPriceVnd) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPriceVnd = unitPriceVnd;
        }
    }

    static final class Order {
        final List<Item> items;
        final boolean vip;

        Order(List<Item> items, boolean vip) {
            this.items = items;
            this.vip = vip;
        }
    }

    static final class Quote {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;
        final String receipt;

        Quote(long subtotal, long discount,
              long shipping, long total, String receipt) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
            this.receipt = receipt;
        }
    }

    interface Checkout {
        Quote quote(Order order);
    }

    // TRƯỚC: một method phải kiểm tra, tính toán, định dạng.
    static final class LegacyCheckout implements Checkout {
        @Override
        public Quote quote(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.quantity <= 0
                        || item.unitPriceVnd < 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineAmount = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineAmount);
            }

            long discount = order.vip && subtotal >= VIP_THRESHOLD
                    ? subtotal / 10L : 0L;
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = payable >= FREE_SHIPPING_THRESHOLD
                    ? 0L : SHIPPING_FEE;
            long total = Math.addExact(payable, shipping);

            String receipt = "subtotal=" + subtotal
                    + ";discount=" + discount
                    + ";shipping=" + shipping
                    + ";total=" + total;

            return new Quote(
                    subtotal, discount, shipping, total, receipt);
        }
    }

    // Đối tượng giá trị trung gian: kết quả tính tiền đã hoàn tất.
    static final class Pricing {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;

        Pricing(long subtotal, long discount,
                long shipping, long total) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
        }
    }

    // SAU bước tách: quy tắc giá được gom vào một lớp.
    static final class PricingPolicy {
        Pricing calculate(Order order) {
            long subtotal = calculateSubtotal(order);
            long discount = calculateDiscount(subtotal, order.vip);
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = calculateShipping(payable);
            long total = Math.addExact(payable, shipping);

            return new Pricing(subtotal, discount, shipping, total);
        }

        private long calculateSubtotal(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.quantity <= 0
                        || item.unitPriceVnd < 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineAmount = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineAmount);
            }
            return subtotal;
        }

        private long calculateDiscount(long subtotal, boolean vip) {
            return vip && subtotal >= VIP_THRESHOLD
                    ? subtotal / 10L : 0L;
        }

        private long calculateShipping(long payable) {
            return payable >= FREE_SHIPPING_THRESHOLD
                    ? 0L : SHIPPING_FEE;
        }
    }

    // SAU bước tách: định dạng là một trách nhiệm độc lập.
    static final class ReceiptFormatter {
        String format(Pricing pricing) {
            return "subtotal=" + pricing.subtotal
                    + ";discount=" + pricing.discount
                    + ";shipping=" + pricing.shipping
                    + ";total=" + pricing.total;
        }
    }

    // SAU: class điều phối sử dụng composition và constructor injection.
    static final class CheckoutService implements Checkout {
        private final PricingPolicy pricing;
        private final ReceiptFormatter formatter;

        CheckoutService(PricingPolicy pricing,
                        ReceiptFormatter formatter) {
            this.pricing = Objects.requireNonNull(pricing);
            this.formatter = Objects.requireNonNull(formatter);
        }

        @Override
        public Quote quote(Order order) {
            Pricing result = pricing.calculate(order);
            return new Quote(
                    result.subtotal,
                    result.discount,
                    result.shipping,
                    result.total,
                    formatter.format(result));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // Kỳ vọng nghiệp vụ độc lập với cả hai implementation.
    private static void expectQuote(
            Checkout checkout, Order order,
            long subtotal, long discount,
            long shipping, long total) {
        Quote actual = checkout.quote(order);
        String expectedReceipt = "subtotal=" + subtotal
                + ";discount=" + discount
                + ";shipping=" + shipping
                + ";total=" + total;

        check(actual.subtotal == subtotal, "Unexpected subtotal");
        check(actual.discount == discount, "Unexpected discount");
        check(actual.shipping == shipping, "Unexpected shipping");
        check(actual.total == total, "Unexpected total");
        check(expectedReceipt.equals(actual.receipt),
                "Unexpected receipt text");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void expectOverflow(Runnable action) {
        try {
            action.run();
        } catch (ArithmeticException expected) {
            return;
        }
        throw new AssertionError("Expected ArithmeticException");
    }

    // CÙNG bộ test bảo vệ được chạy cho cả code cũ và code mới.
    private static void contractTests(Checkout checkout) {
        // Khách thường: 100.000 + 30.000 shipping = 130.000.
        expectQuote(checkout,
                new Order(List.of(new Item("PEN", 1, 100_000L)), false),
                100_000L, 0L, 30_000L, 130_000L);

        // VIP đúng ngưỡng giảm giá:
        // subtotal 500.000, discount 50.000,
        // payable 450.000, shipping 30.000 -> total 480.000.
        expectQuote(checkout,
                new Order(List.of(new Item("BOOK", 2, 250_000L)), true),
                500_000L, 50_000L, 30_000L, 480_000L);

        // VIP subtotal 800.000, discount 80.000,
        // payable 720.000 nên miễn phí shipping.
        expectQuote(checkout,
                new Order(List.of(new Item("BOOK", 2, 400_000L)), true),
                800_000L, 80_000L, 0L, 720_000L);

        // Khách thường, payable 699.999 vẫn chịu phí.
        expectQuote(checkout,
                new Order(List.of(new Item("A", 1, 699_999L)), false),
                699_999L, 0L, 30_000L, 729_999L);

        // Đúng ngưỡng 700.000: miễn phí, bắt lỗi đổi >= thành >.
        expectQuote(checkout,
                new Order(List.of(new Item("A", 1, 700_000L)), false),
                700_000L, 0L, 0L, 700_000L);

        expectInvalid(() -> checkout.quote(null));
        expectInvalid(() -> checkout.quote(new Order(List.of(), false)));
        expectInvalid(() -> checkout.quote(new Order(
                List.of(new Item("A", 0, 100L)), false)));
        expectInvalid(() -> checkout.quote(new Order(
                List.of(new Item("A", 1, -10L)), false)));

        // Math.addExact phải phát hiện tổng vượt phạm vi long.
        expectOverflow(() -> checkout.quote(new Order(List.of(
                new Item("A", 1, Long.MAX_VALUE),
                new Item("B", 1, 1L)), false)));
    }

    public static void main(String[] args) {
        Checkout legacy = new LegacyCheckout();
        Checkout refactored = new CheckoutService(
                new PricingPolicy(), new ReceiptFormatter());

        contractTests(legacy);
        contractTests(refactored);

        System.out.println(
                "PASS: same 5 business cases and 5 error cases in both versions");
    }
}
~~~

Chạy bằng terminal trong thư mục chứa file:

~~~bash
javac SafeRefactoringDemo.java
java SafeRefactoringDemo
~~~

Kết quả nếu các assertion đều đạt:

~~~text
PASS: same 4 business cases and 5 error cases in both versions
~~~

Chương trình có **5 kịch bản tính tiền và 5 kịch bản lỗi** cho mỗi implementation: 4 trường hợp dữ liệu sai và 1 trường hợp tràn số. Kỳ vọng được ghi trực tiếp trong test chứ không được lấy từ code cũ; nhờ vậy test không chỉ xác nhận hai phiên bản tương đương mà còn bảo vệ những quy tắc đã nêu.

Trong code này, Math.multiplyExact, Math.addExact và Math.subtractExact giúp lỗi tràn số được phát hiện rõ. Với tiền tệ có phần thập phân, hãy dùng kiểu biểu diễn phù hợp như BigDecimal và chính sách làm tròn được công bố; ví dụ cố tình dùng VND nguyên để đơn giản và nhất quán.

## 5. Trình tự cải thiện cụ thể

| Vòng | Thay đổi nhỏ | Điều nên kiểm tra ngay |
|---|---|---|
| 0 | Chạy lại code cũ với bộ test bảo vệ | Test pass ổn định, các case quan trọng có kỳ vọng xác định |
| 1 | Đặt tên rõ cho biến subtotal, discount, payable | Kết quả/exception không đổi |
| 2 | Tách calculateSubtotal từ phương thức lớn | Case dữ liệu sai, phép cộng tiền, overflow giữ nguyên |
| 3 | Tách calculateDiscount và calculateShipping | Các ngưỡng 500.000 và 700.000 giữ nguyên |
| 4 | Gom các method giá liên quan vào PricingPolicy | Không thay đổi giá trị và thứ tự lỗi đã cam kết |
| 5 | Tách ReceiptFormatter | Định dạng receipt không bị thay đổi ngoài ý muốn |
| 6 | Cho CheckoutService nhận dependency qua constructor | Caller vẫn nhận cùng Quote theo hợp đồng |
| 7 | Xóa implementation dư, tối giản tên/visibility | Chạy lại kiểm thử, review diff và integration nếu có |

~~~mermaid
flowchart TD
    A["Xác định hợp đồng và rủi ro"] --> B["Viết hoặc cập nhật test bảo vệ"]
    B --> C["Chọn một cải thiện thiết kế nhỏ"]
    C --> D["Biên dịch và chạy test"]
    D --> E{"Mọi test liên quan đều đạt?"}
    E -- "Không" --> F["Điều tra thay đổi hành vi hoặc test quá chặt"]
    F --> C
    E -- "Có" --> G["Review diff, đặt tên và trách nhiệm"]
    G --> H{"Còn cải thiện có ích?"}
    H -- "Có" --> C
    H -- "Không" --> I["Kết thúc refactor và kiểm thử tích hợp phù hợp"]
~~~

Chú ý: vòng kiểm thử có thể không bảo vệ mọi nhánh chưa được đưa vào test. Coverage giúp nhận biết chỗ chưa chạy, nhưng **100% line coverage không bảo đảm mọi assertion đúng hoặc tất cả quy tắc nghiệp vụ được kiểm tra**.

## 6. Cách thiết kế test để refactor không làm test vỡ vô lý

**Kiểm tra kết quả và hiệu ứng công khai.** Với lớp định giá, ưu tiên kiểm tra số tiền. Với lớp gửi biên nhận, kiểm tra rằng yêu cầu gửi đến đúng người, đúng nội dung, đúng thời điểm theo hợp đồng.

**Đặt tên test theo hành vi.** Ví dụ vipDiscountStartsAtThreshold hoặc failedPaymentDoesNotSendReceipt dễ hiểu hơn testCallsCalculateDiscountTwice.

**Không đóng băng thứ tự helper không liên quan.** Việc đổi từ for sang Stream hoặc thay HashMap bằng cấu trúc khác không nên làm test nghiệp vụ thất bại, nếu các kết quả và cam kết không thay đổi.

**Dùng test double đúng vai trò.** Fake repository giúp kiểm tra logic với dữ liệu trong bộ nhớ; stub điều khiển kết quả cổng thanh toán; mock phù hợp khi chính lời gọi bên ngoài là một phần yêu cầu. Chúng không thay thế hoàn toàn integration test với DB, queue hoặc API thật.

**Giữ test dễ đọc, độc lập, lặp lại được.** Tránh dùng đồng hồ hệ thống, randomness hoặc network thực cho unit test nếu các yếu tố ấy không phải mục tiêu. Có thể inject java.time.Clock, random generator và collaborator để kiểm soát input.

**Đừng chỉ so output của bản cũ với bản mới.** Nếu bản cũ chứa bug, cả hai có thể trả cùng kết quả sai. Kết hợp phép so sánh trước/sau với **expected values độc lập** lấy từ yêu cầu và ví dụ kiểm chứng.

## 7. Refactor có side effect cần cẩn thận hơn

Ví dụ tính giá ở trên gần như thuần chức năng. Nhưng nhiều phương thức ngoài thực tế còn mở transaction, ghi DB, gửi email hoặc gọi payment gateway.

Nếu trước đây thứ tự là:
1. Xác thực và kiểm tra tồn kho.
2. Thu tiền.
3. Ghi đơn hàng.
4. Yêu cầu gửi email xác nhận.

Việc tách method/class **không được vô tình** thay đổi quy tắc như "giao dịch thất bại thì không gửi email", hoặc đổi ranh giới transaction. Khi đó cần kiểm thử:
- Trạng thái lưu trữ và tính nhất quán sau thành công/thất bại.
- Những lời gọi ngoài hệ thống mà nghiệp vụ quy định phải xảy ra hoặc không xảy ra.
- Thứ tự thực sự mang ý nghĩa, idempotency và việc thử lại khi cần.
- Hợp đồng giữa application service với adapter thật qua integration test.

Ví dụ ý tưởng với Mockito + JUnit 5, giả định lớp CheckoutService và các interface này đã được định nghĩa trong một ứng dụng thực:

~~~java
when(gateway.charge(orderId, amountVnd)).thenReturn(false);

CheckoutResult result = checkoutService.checkout(orderId);

assertEquals(CheckoutResult.PAYMENT_DECLINED, result);
verifyNoInteractions(receiptSender);
~~~

Đây là **interaction verification có ý nghĩa** vì "không gửi biên nhận khi thanh toán lỗi" là hợp đồng. Nó khác với việc xác minh chính xác số lần private helper chạy trong code.

## 8. Sai lầm phổ biến khi cải thiện thiết kế

| Sai lầm | Hậu quả | Cách xử lý |
|---|---|---|
| Đổi công thức nghiệp vụ trong cùng bước tách lớp | Khó tách regression khỏi thay đổi tính năng | Chia thành hai thay đổi riêng, mỗi thay đổi có test |
| Di chuyển hàng trăm dòng một lần | Khó khoanh vùng lỗi | Thực hiện từng phép biến đổi nhỏ có kiểm chứng |
| Xóa test đỏ mà không điều tra | Có thể bỏ lỡ regression | Xác định test bảo vệ hợp đồng hay chỉ bám implementation |
| Chỉ đo line coverage | Dễ tưởng đã kiểm tra đủ | Thêm test biên, test lỗi, assertion cụ thể |
| Tạo quá nhiều class/method một dòng | Tăng phân mảnh và coupling | Tách theo trách nhiệm và lý do thay đổi thực tế |
| Biến private field thành public để refactor cho nhanh | Phá encapsulation | Truyền dữ liệu qua constructor, method, value object phù hợp |
| Thay đổi thứ tự validation | Thay đổi loại exception hoặc tác dụng phụ | Ghi nhận và kiểm tra thứ tự quan sát được nếu là hợp đồng |
| Tin rằng fake mô phỏng hoàn hảo DB | Thiếu phát hiện lỗi constraint, transaction, concurrency | Bổ sung integration test |
| Test phụ thuộc ngày hiện tại hoặc ID ngẫu nhiên | Test không ổn định | Inject Clock/ID generator, dùng giá trị cố định |

**Lưu ý về thứ tự và lỗi:** Nếu dữ liệu chứa hai lỗi khác nhau, đổi thứ tự validation có thể đổi exception xuất hiện đầu tiên. Điều này có thể là thay đổi quan sát được; hãy xác định yêu cầu trước khi kết luận việc đổi thứ tự là an toàn.

## 9. Cách tổ chức công việc khi refactor code thật

Với một phần hệ thống đang chạy, có thể thực hiện theo quy trình sau:

**Bước A — Khoanh phạm vi và lập baseline.** Chọn một trách nhiệm rõ ràng, kiểm tra caller, ghi nhận test hiện có và xác nhận hành vi cần giữ. Nếu code quá cũ, viết characterization tests dựa trên input/output hoặc side effect hiện tại. Xác nhận các test chạy ổn định trước khi sửa.

**Bước B — Phân tích điểm thay đổi.** Một lớp phình to vì validation, pricing, persistence và presentation thường có nhiều lý do thay đổi. Xác định đường dữ liệu, vòng đời đối tượng và ranh giới transaction. Chọn điểm tách nhỏ nhất có tác dụng làm code rõ hơn.

**Bước C — Thay đổi từng lần.** Rename biến, extract method, move method hoặc extract class. Thực hiện một phép biến đổi hoàn chỉnh, biên dịch/test, rồi mới tiếp tục. Dùng công cụ refactor của IDE khi thuận tiện, nhưng vẫn đọc diff vì IDE không thể xác nhận mọi quy tắc nghiệp vụ.

**Bước D — Tối ưu kiểm thử khi kiến trúc đã rõ.** Test theo hành vi công khai, không khóa quá chặt vào helper mới. Chia unit/integration test theo ranh giới chịu trách nhiệm. Không nhân bản toàn bộ bộ test chỉ để test từng class sau khi tách.

**Bước E — Kiểm chứng cuối.** Kiểm tra rằng không còn đường code cũ được sử dụng ngầm, public API không đổi ngoài chủ đích, test pass, và các tác dụng phụ đã được xác minh. Nếu cần thay đổi hành vi, lập một thay đổi riêng có test kỳ vọng mới.

Không cần chờ toàn bộ hệ thống "sạch" mới tiếp tục phát triển. Refactor hiệu quả là những **bước nhỏ, có mục đích, có chứng cứ** được thực hiện gần phần code đang cần thay đổi.

## 10. Bài thực hành

**Bài 1 — Bắt lỗi tại ngưỡng:** Trong PricingPolicy, đổi điều kiện miễn phí shipping từ lớn hơn hoặc bằng 700.000 thành lớn hơn 700.000. Viết thêm case khách thường subtotal bằng đúng 700.000; kỳ vọng shipping = 0. Test phải phát hiện lỗi.

**Bài 2 — Bắt lỗi giảm giá:** Đổi ngưỡng VIP từ lớn hơn hoặc bằng 500.000 thành lớn hơn 500.000. Case VIP subtotal 500.000 trong bộ test phải FAIL.

**Bài 3 — Extract Method:** Từ LegacyCheckout, tự tách validate, subtotal, discount, shipping, rồi chạy lại contractTests sau mỗi phép tách. Ghi lại việc các lỗi và kết quả giữ nguyên.

**Bài 4 — Extract Class:** Chuyển phần format receipt sang class riêng. Thử đổi riêng yêu cầu định dạng biên nhận và giải thích tại sao PricingPolicy không cần sửa. Đây là thay đổi tính năng riêng, không còn là refactor bảo toàn hành vi.

**Bài 5 — Characterization test cho lỗi cũ:** Giả sử hệ thống cũ tính sai phí của một trường hợp đặc biệt. Viết test tái hiện hành vi hiện tại để bảo vệ quá trình tách, sau đó viết test kỳ vọng mới cho bug fix và sửa ở bước riêng.

**Bài 6 — Ranh giới tích hợp:** Phác thảo fake PaymentGateway và mock ReceiptSender; chứng minh thanh toán bị từ chối thì không gửi biên nhận. Nêu thêm integration test cần có để biết adapter thanh toán thật xử lý mã lỗi đúng.

## 11. Tự kiểm tra kiến thức

1. Tại sao kiểm thử so sánh bản cũ với bản mới chưa đủ nếu bản cũ có bug?
2. Tại sao test về số lần gọi private helper có thể ngăn refactor đúng?
3. Khi nào thứ tự tương tác với collaborator là một phần hợp đồng?
4. Sau khi tách PricingPolicy và ReceiptFormatter, vì sao CheckoutService nên giữ vai trò điều phối?
5. Nếu một thay đổi khiến test đỏ, làm thế nào phân biệt lỗi nghiệp vụ với test quá phụ thuộc implementation?

**Điều cần ghi nhớ:** Kiểm thử bảo vệ giúp bạn cải thiện cấu trúc với phản hồi nhanh, nhưng chất lượng của hàng rào phụ thuộc vào **những hành vi nó thực sự xác minh**. Refactor từng bước nhỏ, giữ rõ ranh giới trách nhiệm OOP, kiểm tra ngay sau mỗi bước và bảo vệ cả trường hợp bình thường, ca biên, lỗi và side effect có ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)
