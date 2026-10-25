<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Ngôn ngữ Java là gì và dùng để diễn đạt bài toán như thế nào](#language-overview-001)
- [Mối liên hệ giữa mã nguồn, trình biên dịch, bytecode và JVM](#language-overview-002)
- [Phân biệt Java, Java SE, JDK và môi trường chạy](#language-overview-003)
- [Dữ liệu, biểu thức, câu lệnh và luồng thực thi](#language-overview-004)
- [Kiểu dữ liệu tĩnh và những lỗi được phát hiện khi biên dịch](#language-overview-005)
- [Từ đầu vào tới xử lý và đầu ra của một chương trình](#language-overview-006)
- [Phân biệt cú pháp đúng với kết quả nghiệp vụ đúng](#language-overview-007)
- [Lộ trình học và chương trình ví dụ xuyên suốt](#language-overview-008)

## <a id="language-overview-001">Ngôn ngữ Java là gì và dùng để diễn đạt bài toán như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Ngôn ngữ Java là gì và dùng để diễn đạt bài toán như thế nào?

Khi mới học lập trình, nhiều người thấy chương trình chỉ là những dòng chữ có dấu `;`, dấu `{}` và từ khóa khó nhớ. Cách nhìn hữu ích hơn là: **Java là một ngôn ngữ để biến quy tắc giải quyết một bài toán thành các chỉ dẫn mà máy tính có thể kiểm tra và thực thi**. Ta cần hiểu bài toán trước, sau đó chọn dữ liệu, đặt tên, mô tả điều kiện và tính toán bằng Java.

Bài này dành cho người mới học Java core. Bạn sẽ xây dựng và chạy một chương trình nhỏ mà không cần thư viện ngoài hay framework.

## 1. Sau bài học, bạn làm được gì?

- Phân biệt **ngôn ngữ Java**, tệp mã nguồn `.java`, trình biên dịch `javac`, tệp bytecode `.class` và JVM.
- Chuyển một yêu cầu bằng tiếng Việt thành **đầu vào → quy tắc xử lý → đầu ra** có thể kiểm chứng.
- Nhận diện vai trò của biến, kiểu dữ liệu, biểu thức, điều kiện, phương thức và lời gọi trong một chương trình Java đơn giản.
- Tạo một lớp Java có `main`, biên dịch bằng `javac` và chạy bằng `java`.
- Thử các giá trị thường gặp, giá trị biên và dữ liệu sai; phân biệt lỗi cú pháp, lỗi khi chạy và lỗi logic.
- Hiểu giới hạn của một chương trình minh họa: quy tắc nghiệp vụ phải do bài toán xác định, Java không tự suy ra điều người dùng muốn.

## 2. Java là gì?

**Java** là một ngôn ngữ lập trình có cú pháp và quy tắc kiểu dữ liệu. Với Java, ta có thể viết ứng dụng dòng lệnh, hệ thống máy chủ, công cụ xử lý dữ liệu, ứng dụng doanh nghiệp và nhiều chương trình khác. Java còn là tên thường được dùng để nói về hệ sinh thái công cụ, thư viện và môi trường chạy liên quan.

Ví dụ, hai dòng sau đang **diễn đạt một ý định** bằng cú pháp Java:

~~~java
int numberOfBooks = 3;
System.out.println(numberOfBooks * 25_000);
~~~

Đọc bằng tiếng Việt: “Có 3 cuốn sách, mỗi cuốn giá 25.000 đồng; in tổng tiền.” Kết quả là `75000`. Máy không hiểu “cuốn sách” theo nghĩa đời thực; máy thao tác với **số 3**, **số 25.000** và phép nhân theo quy tắc ta viết.

Java không phải JavaScript. Tên giống nhau nhưng đó là hai ngôn ngữ khác nhau với hệ thống kiểu, cú pháp và môi trường thực thi khác nhau.

### Mã nguồn được chạy như thế nào?

~~~mermaid
flowchart LR
    A["Bài toán và quy tắc"] --> B["ShippingQuote.java<br/>mã nguồn"]
    B --> C["javac<br/>kiểm tra và biên dịch"]
    C --> D["ShippingQuote.class<br/>bytecode"]
    D --> E["JVM chạy chương trình"]
    E --> F["Kết quả hoặc lỗi khi chạy"]
~~~

- **`.java`:** tệp văn bản chứa mã nguồn mà người lập trình viết.
- **`javac`:** trình biên dịch Java trong JDK; phát hiện nhiều lỗi cú pháp và kiểu dữ liệu, sau đó tạo bytecode nếu biên dịch thành công.
- **`.class`:** tệp chứa bytecode cho JVM; không phải bản văn mã nguồn.
- **JVM (Java Virtual Machine):** môi trường thực thi bytecode. JVM còn kiểm tra và thực hiện nhiều hoạt động lúc chạy.
- **JDK (Java Development Kit):** bộ công cụ phát triển, gồm `javac`, công cụ `java` và các thành phần cần thiết để xây dựng/chạy ứng dụng.

Trên máy khác, mã đã biên dịch có thể chạy với JVM tương thích nếu phiên bản Java và các phụ thuộc phù hợp. Vì vậy, “viết một lần, chạy mọi nơi” là **mục tiêu khả chuyển**, không có nghĩa mọi chương trình Java đều chạy trên mọi máy mà không cần cấu hình.

## 3. Từ đề bài tiếng Việt đến mô hình xử lý

Giả sử cửa hàng giao hàng đặt ra **quy tắc minh họa** (không phải bảng giá của một đơn vị vận chuyển thật):

> Tính tổng số tiền khách phải trả. Nếu khách là thành viên hoặc giá trị đơn hàng từ **500.000 VND trở lên**, miễn phí giao. Nếu không, phí giao cơ bản **15.000 VND** cho tối đa 5 km; từ km thứ 6, cộng **2.000 VND** cho mỗi km vượt quá 5 km.

Chúng ta chưa nên mở trình soạn thảo và viết `if` ngay. Trước hết hãy xác định hợp đồng của bài toán.

| Thành phần | Tên trong Java | Ý nghĩa/giới hạn |
| --- | --- | --- |
| Đầu vào 1 | `orderValueVnd`: `long` | Tiền hàng, tính bằng **VND nguyên**, không âm |
| Đầu vào 2 | `distanceKm`: `int` | Số kilomet nguyên không âm |
| Đầu vào 3 | `member`: `boolean` | Khách có phải thành viên hay không |
| Trung gian | `shippingVnd`: `long` | Phí giao tính từ ba đầu vào |
| Đầu ra | `totalVnd`: `long` | Tiền hàng + phí giao |
| Lỗi dữ liệu | `IllegalArgumentException` | Ví dụ giá tiền hoặc quãng đường âm |

Cần công bố thêm những giả định để tránh hiểu sai:

- Khoảng cách được làm tròn/ghi nhận **theo số km nguyên** trước khi truyền vào chương trình; bài này chưa giải quyết số km thập phân.
- “Từ 500.000 trở lên” bao gồm **đúng 500.000**, nên dùng `>=`, không dùng `>`.
- Miễn phí giao chỉ thay đổi `shippingVnd`, không thay đổi giá trị hàng.
- Phép tính không bao gồm giảm giá, thuế, COD hay chi phí khác vì đề bài không yêu cầu.
- Dữ liệu sai phải được báo lỗi thay vì âm thầm đổi thành 0.

Những lựa chọn trên là **quy tắc nghiệp vụ**. Java chỉ cung cấp cách biểu diễn chúng chính xác.

### Viết thuật toán bằng lời trước khi viết Java

1. Nhận giá trị đơn, số km và trạng thái thành viên.
2. Từ chối giá trị đơn âm hoặc số km âm.
3. Nếu là thành viên **hoặc** giá trị đơn từ 500.000, phí giao bằng 0.
4. Nếu không, phí giao bằng 15.000 cộng 2.000 cho mỗi km vượt quá 5.
5. Lấy giá trị đơn cộng phí giao và in cả hai thành phần cùng tổng.

Chính chuỗi bước này mới là “lời giải”. Java giúp biến lời giải thành các câu lệnh có thể thực thi, thay vì tự nghĩ ra thuật toán cho chúng ta.

## 4. Java biểu đạt từng phần của bài toán bằng gì?

| Ý trong bài toán | Công cụ Java | Ví dụ ngắn |
| --- | --- | --- |
| Lưu một giá trị | Biến và kiểu | `long orderValueVnd = 300_000L;` |
| Biểu diễn có/không | `boolean` | `boolean member = false;` |
| Tính toán | Biểu thức | `15_000L + 3L * 2_000L` |
| So sánh với ngưỡng | Toán tử so sánh | `orderValueVnd >= 500_000L` |
| Kết hợp các điều kiện | Toán tử logic | `member || orderValueVnd >= 500_000L` |
| Chọn nhánh | `if` / `else` hoặc `return` sớm | Miễn phí hay tính phí |
| Đặt tên cho một khả năng | Phương thức | `calculateShipping(...)` |
| Nhận dữ liệu từ lệnh chạy | Tham số `String[] args` | `args[0]` chứa văn bản đầu tiên |
| Hiển thị kết quả | `System.out.println(...)` | In ra tổng tiền |

Dấu gạch dưới trong số như `300_000L` chỉ để dễ đọc: giá trị vẫn là ba trăm nghìn. Hậu tố `L` chỉ literal kiểu `long`. Phép `||` mang nghĩa **HOẶC**, chỉ cần một điều kiện đúng thì toàn biểu thức đúng.

Bạn không phải thuộc lòng mọi cú pháp trước khi hiểu bài toán; hãy liên hệ mỗi câu lệnh với **một điều cần làm** trong yêu cầu.

## 5. Chương trình Java hoàn chỉnh, có thể biên dịch và chạy

Tạo tệp tên chính xác **`ShippingQuote.java`** và chép toàn bộ mã sau. Ví dụ tương thích với JDK 17 trở lên, đồng thời không cần tính năng đặc thù Java 17.

~~~java
import java.util.Locale;

public class ShippingQuote {
    private static final long FREE_AT_VND = 500_000L;
    private static final long BASE_FEE_VND = 15_000L;
    private static final long EXTRA_KM_FEE_VND = 2_000L;

    static long calculateShipping(
            long orderValueVnd, int distanceKm, boolean member) {

        if (orderValueVnd < 0) {
            throw new IllegalArgumentException(
                    "Gia tri don khong duoc am");
        }
        if (distanceKm < 0) {
            throw new IllegalArgumentException(
                    "Khoang cach khong duoc am");
        }

        if (member || orderValueVnd >= FREE_AT_VND) {
            return 0L;
        }

        long extraKm = Math.max(0L, (long) distanceKm - 5L);
        long extraFee = Math.multiplyExact(
                extraKm, EXTRA_KM_FEE_VND);
        return Math.addExact(BASE_FEE_VND, extraFee);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println(
                    "Cach dung: java ShippingQuote "
                    + "<tienHangVnd> <khoangCachKm> <true|false>");
            return;
        }

        try {
            long orderValueVnd = Long.parseLong(args[0]);
            int distanceKm = Integer.parseInt(args[1]);

            String memberText = args[2].toLowerCase(Locale.ROOT);
            if (!memberText.equals("true")
                    && !memberText.equals("false")) {
                throw new IllegalArgumentException(
                        "Thanh vien phai la true hoac false");
            }
            boolean member = Boolean.parseBoolean(memberText);

            long shippingVnd = calculateShipping(
                    orderValueVnd, distanceKm, member);
            long totalVnd = Math.addExact(
                    orderValueVnd, shippingVnd);

            System.out.println(
                    "Tien hang: " + orderValueVnd + " VND");
            System.out.println(
                    "Phi giao: " + shippingVnd + " VND");
            System.out.println(
                    "Tong cong: " + totalVnd + " VND");
        } catch (NumberFormatException ex) {
            System.out.println(
                    "Loi dinh dang so: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.out.println(
                    "Loi du lieu: " + ex.getMessage());
        } catch (ArithmeticException ex) {
            System.out.println("Loi tran so: " + ex.getMessage());
        }
    }
}
~~~

**Giải thích các khối chính:**

- `public class ShippingQuote` định nghĩa lớp chứa chương trình. Với lớp `public` này, tên tệp phải là `ShippingQuote.java`.
- `main(String[] args)` là điểm bắt đầu thông thường khi chạy lớp bằng lệnh `java ShippingQuote ...`. Các đối số sau tên lớp đi vào mảng `args` dưới dạng **chuỗi**, chưa phải số.
- `Long.parseLong(...)` và `Integer.parseInt(...)` biến chuỗi có định dạng số hợp lệ thành giá trị số tương ứng; dữ liệu sai gây `NumberFormatException`.
- `calculateShipping` là một **phương thức** đặt tên cho thao tác tính phí. Đầu vào và giá trị trả về được khai báo ngay tại chữ ký; phương thức không tự đọc bàn phím hay in kết quả.
- `if` kiểm tra quy tắc, `return 0L` kết thúc phương thức sớm khi đủ điều kiện miễn phí.
- `Math.max(0L, (long) distanceKm - 5L)` cho số km **vượt quá 5**, không tạo số âm; ép sang `long` trước phép trừ giúp phép tính dùng kiểu đủ rộng.
- `Math.multiplyExact` và `Math.addExact` tính toán số nguyên và ném `ArithmeticException` nếu phép toán bị tràn trong kiểu `long`. Đây là cách kiểm soát rủi ro khi mở rộng bài toán.
- Ba nhánh `catch` báo ba nhóm lỗi khác nhau. `NumberFormatException` là một loại `IllegalArgumentException` nên phải bắt nhánh cụ thể trước nhánh tổng quát; `ArithmeticException` là loại ngoại lệ khác.

Đây là **một lớp Java thực thi được**, không cần tạo thêm `package` hoặc cài thêm thư viện ngoài JDK.

## 6. Biên dịch, chạy và đọc kết quả

Mở terminal tại đúng thư mục chứa `ShippingQuote.java`. Kiểm tra JDK:

~~~bash
java -version
javac -version
~~~

Tiếp theo:

~~~bash
javac -encoding UTF-8 ShippingQuote.java
java ShippingQuote 300000 8 false
~~~

Kết quả:

~~~text
Tien hang: 300000 VND
Phi giao: 21000 VND
Tong cong: 321000 VND
~~~

Vì sao phí giao là 21.000?

1. `member = false` và `300000 < 500000` nên **không** được miễn phí.
2. Khoảng cách 8 km vượt mức cơ bản 5 km đúng **3 km**.
3. Phí cộng thêm: `3 * 2000 = 6000`.
4. Phí giao: `15000 + 6000 = 21000`.
5. Tổng cộng: `300000 + 21000 = 321000`.

Thử tiếp giá trị **đúng ngưỡng**:

~~~bash
java ShippingQuote 500000 8 false
~~~

~~~text
Tien hang: 500000 VND
Phi giao: 0 VND
Tong cong: 500000 VND
~~~

Và khách **thành viên**:

~~~bash
java ShippingQuote 120000 8 true
~~~

~~~text
Tien hang: 120000 VND
Phi giao: 0 VND
Tong cong: 120000 VND
~~~

Cách chạy này là **chương trình dòng lệnh**. Thông tin nhập qua `args` do người dùng truyền lúc chạy, khác với việc đọc tương tác từng dòng từ bàn phím. Khi chỉ muốn học cấu trúc ngôn ngữ, chọn input qua tham số làm ví dụ gọn và dễ lặp lại.

## 7. Đọc một dòng Java theo nghĩa của nó

Xét:

~~~java
long extraKm = Math.max(0L, (long) distanceKm - 5L);
~~~

Ta có thể đọc từ phải sang trái:

1. `distanceKm` đang là số nguyên `int`.
2. `(long) distanceKm` chuyển giá trị sang kiểu `long`.
3. Trừ 5 để biết phần khoảng cách vượt ngưỡng.
4. `Math.max(0L, ...)` lấy giá trị lớn hơn, tránh phần vượt là số âm.
5. Gán kết quả cho biến có tên `extraKm` và kiểu `long`.
6. Dấu `;` kết thúc câu lệnh khai báo/gán giá trị này.

Đây là minh họa cho **biểu thức** (phần được tính thành một giá trị) nằm trong **câu lệnh** (một hành động của chương trình).

Ví dụ khác:

~~~java
if (member || orderValueVnd >= FREE_AT_VND) {
    return 0L;
}
~~~

- `if` hỏi điều kiện có đúng không.
- `()` chứa điều kiện.
- `||` ghép hai điều kiện. Java đánh giá ngắn mạch: nếu `member` đã đúng, không cần đánh giá vế sau.
- `{}` nhóm các câu lệnh thuộc nhánh.
- `return` trả lại kết quả và kết thúc lời gọi phương thức đó.

**Dấu `=` là phép gán, còn `==` là phép so sánh bằng nhau** (với kiểu phù hợp). Nhầm hai khái niệm này có thể gây lỗi biên dịch hoặc lỗi logic.

## 8. Kiểu dữ liệu phải phù hợp với ý nghĩa bài toán

Trong Java, kiểu dữ liệu không chỉ là phần trang trí bên cạnh tên biến. Kiểu quyết định dạng giá trị được lưu và những phép toán hợp lệ.

| Kiểu | Thể hiện điều gì trong bài này? | Điều cần tránh |
| --- | --- | --- |
| `int` | Quãng đường km nguyên, trong miền hợp lệ của `int` | Chuyển một chuỗi quá lớn thành `int` |
| `long` | Giá trị tiền VND nguyên và tổng tiền | Tính vượt giới hạn `long` |
| `boolean` | Có/không là thành viên | Hiểu một chuỗi bất kỳ là giá trị `true` |
| `String` | Các tham số dạng văn bản trong `args` | Mang chuỗi vào phép cộng số mà chưa chuyển kiểu |

Tiền có đơn vị **VND nguyên** nên dùng kiểu số nguyên. `double` là số chấm động hữu ích trong nhiều phép tính khoa học, nhưng không nên mặc nhiên dùng nó để biểu diễn tiền chính xác đến đơn vị nhỏ nhất vì biểu diễn nhị phân có thể gây sai số. Với tiền có phần thập phân hoặc quy tắc làm tròn phức tạp, có thể cần `BigDecimal` và quy tắc đơn vị/làm tròn riêng.

Cũng đừng dùng tên biến mơ hồ như `x`, `a`, `n` cho mọi thứ. `orderValueVnd` và `shippingVnd` dài hơn nhưng cho thấy cả **ý nghĩa** lẫn **đơn vị**, giảm khả năng cộng nhầm tiền với kilomet.

## 9. Trường hợp biên và lỗi cần nghĩ đến

Một chương trình chạy đúng với ví dụ 300.000 VND chưa đủ chứng minh thuật toán đúng. Hãy dùng các ca sau làm **bảng kiểm thử bằng tay**:

| Input: tiền, km, thành viên | Phí giao kỳ vọng | Lý do |
| --- | ---: | --- |
| `300000 0 false` | 15.000 | Chưa vượt 5 km |
| `300000 5 false` | 15.000 | Chính xác điểm kết thúc giá cơ bản |
| `300000 6 false` | 17.000 | Vượt 1 km |
| `300000 8 false` | 21.000 | Vượt 3 km |
| `499999 8 false` | 21.000 | Ngay dưới ngưỡng miễn phí |
| `500000 8 false` | 0 | Đúng ngưỡng miễn phí |
| `120000 8 true` | 0 | Thành viên |
| `0 8 false` | 21.000 | Tiền hàng bằng 0 vẫn hợp lệ theo giả định |
| `-1 8 false` | Không tính, báo lỗi | Tiền âm |
| `300000 -1 false` | Không tính, báo lỗi | Khoảng cách âm |
| `abc 8 false` | Không tính, báo lỗi | Không chuyển thành `long` được |
| `300000 8 yes` | Không tính, báo lỗi | Không thuộc `true/false` |
| `300000 8` | In hướng dẫn sử dụng | Thiếu đối số |

Một lỗi điển hình là viết `orderValueVnd > 500_000L` thay vì `>=`. Hầu hết các ví dụ khác vẫn qua, nhưng **đúng 500.000** sẽ bị tính phí sai. Đây là lỗi logic: chương trình có thể biên dịch và chạy, nhưng **kết quả trái yêu cầu**.

### Ba nhóm vấn đề khác nhau

**Lỗi biên dịch:** Viết sai cú pháp, tên biến hoặc kiểu không tương thích. Ví dụ:

~~~java
// Sai: thiếu dấu ; ở cuối câu lệnh.
long fee = 15_000L
~~~

`javac` báo lỗi và không tạo chương trình hợp lệ để chạy.

**Lỗi lúc chạy:** Mã đã biên dịch, nhưng đầu vào hoặc trạng thái gây ngoại lệ. Ví dụ, `Long.parseLong("abc")` ném `NumberFormatException`; chương trình có thể bắt và báo cho người dùng.

**Lỗi logic:** Mã biên dịch, chạy bình thường nhưng làm sai quy tắc. Ví dụ `distanceKm >= 5` tính phụ phí ngay tại km thứ 5 nếu thuật toán viết sai. Vì vậy, test phải dựa vào **đề bài**, không chỉ dựa vào việc “máy không báo lỗi”.

Các trường hợp như giá trị số quá lớn, thiếu tham số hoặc chuỗi `TRUE` cũng cần được quyết định bằng hợp đồng. Ví dụ này chấp nhận `TRUE` vì chủ động chuyển chữ thường trước khi kiểm tra.

## 10. Vì sao nên tách phương thức xử lý khỏi `main`?

`main` chịu trách nhiệm nhận và chuyển đổi **đầu vào của chương trình**, gọi xử lý và in kết quả. `calculateShipping` chịu trách nhiệm **quy tắc tính phí**.

Sự phân chia giúp:

- Đọc nhanh một hàm và biết nó trả về gì.
- Thử tính phí với nhiều tham số mà không phải giả lập terminal.
- Thay đổi cách lấy đầu vào (dòng lệnh, giao diện, API) mà không viết lại toàn bộ quy tắc.
- Tránh rải số `500_000`, `15_000` và `2_000` khắp nơi nhờ đặt thành hằng số có tên.

Không cần tạo nhiều lớp hay framework chỉ để tính phí vận chuyển. Đối với ví dụ nhỏ, **một lớp và một phương thức rõ ràng** là đủ.

Có thể tự kiểm tra vài lời gọi trong một chương trình thử nghiệm:

~~~java
System.out.println(ShippingQuote.calculateShipping(300_000, 8, false)); // 21000
System.out.println(ShippingQuote.calculateShipping(500_000, 8, false)); // 0
System.out.println(ShippingQuote.calculateShipping(300_000, 5, false)); // 15000
~~~

Ba câu lệnh trên chỉ là **đoạn minh họa đặt bên trong một phương thức** ở cùng thư mục/package mặc định; không phải một tệp Java độc lập. Một bước học tiếp theo là biến các kỳ vọng này thành kiểm thử tự động.

## 11. Những điều ngôn ngữ Java không tự làm thay bạn

Java có trình biên dịch kiểm tra nhiều quy tắc, nhưng **không tự biết đúng sai nghiệp vụ**.

Ví dụ:

- `javac` có thể kiểm tra `calculateShipping` trả về giá trị `long` theo các đường điều khiển hợp lệ.
- Nó không thể kết luận “phí cơ bản phải là 15.000” trừ khi bạn diễn đạt điều ấy trong mã và kiểm thử đối chiếu yêu cầu.
- Nó không tự quyết định nên thu phí theo số km thực, số km làm tròn hay vùng giao hàng.
- Nó không tự bảo đảm dữ liệu từ người dùng, cơ sở dữ liệu hoặc mạng luôn có giá trị hợp lệ.
- Một chương trình Java có thể chạy trên nhiều nền tảng phù hợp, nhưng đường dẫn, kết nối mạng, encoding và thư viện ngoài vẫn có thể ảnh hưởng khi triển khai.

**Chương trình đúng** cần cả hai: ngôn ngữ thực thi đúng mã được viết và **mã đó diễn đạt đúng bài toán**.

## 12. Tự luyện tập: biến một yêu cầu mới thành mã

**Bài 1 — Dự đoán trước khi chạy.** Giữ nguyên chương trình, chạy `java ShippingQuote 499999 5 false`. Phí giao và tổng cộng phải là bao nhiêu?

*Đáp án kiểm tra:* phí giao `15000`, tổng `514999`.

**Bài 2 — Đổi quy tắc.** Cửa hàng đổi ngưỡng miễn phí từ 500.000 lên 600.000 VND. Hãy chỉ ra dòng cần đổi, rồi thử cả `599999` và `600000` với khách không là thành viên.

*Gợi ý:* đổi `FREE_AT_VND` rồi kiểm tra phía dưới và đúng ngưỡng. Đừng sửa kết quả dự đoán trước khi đọc lại yêu cầu.

**Bài 3 — Truy vết.** Với `orderValueVnd = 200_000`, `distanceKm = 10` và `member = false`, đi lần lượt qua `if`, `extraKm`, `extraFee`, `shippingVnd` và `totalVnd`.

*Đáp án:* `extraKm = 5`, `extraFee = 10000`, `shippingVnd = 25000`, `totalVnd = 225000`.

**Bài 4 — Tìm lỗi logic.** Một bạn viết:

~~~java
long extraKm = Math.max(0L, (long) distanceKm - 4L);
~~~

Điều gì xảy ra với đơn không được miễn phí giao ở 5 km? Tại sao sai so với đặc tả?

*Đáp án:* sẽ thu thêm 2.000 VND ngay ở km thứ 5; đề bài nói chỉ thu thêm từ km thứ 6.

**Bài 5 — Tự thiết kế bài toán mới.** Viết bảng **đầu vào / đầu ra / giả định / quy tắc / trường hợp biên** cho một vấn đề nhỏ như đổi phút sang giờ, tính tiền vé theo độ tuổi hoặc tìm số lớn nhất trong ba số. Sau đó viết giả mã bằng tiếng Việt trước khi chuyển sang Java.

**Tiêu chí hoàn thành:** Bạn giải thích được một yêu cầu đời thực, ánh xạ từng thành phần sang dữ liệu và câu lệnh Java, biên dịch/chạy được chương trình, và tự chỉ ra ít nhất ba trường hợp khiến kết quả có thể sai nếu quy tắc viết thiếu chính xác.

## Ghi nhớ

Java là **ngôn ngữ diễn đạt thuật toán và quy tắc** theo một cú pháp có thể biên dịch, kiểm tra kiểu và thực thi trên JVM. Để dùng Java giải bài toán, hãy đi từ **yêu cầu rõ → dữ liệu và đơn vị rõ → điều kiện/tính toán rõ → mã chạy được → kiểm thử cả đầu vào thường và biên**. Khi biết mỗi biến, câu lệnh và phương thức đang đại diện cho điều gì trong bài toán, việc học các cấu trúc Java ở mức chi tiết hơn sẽ có ý nghĩa hơn việc chỉ ghi nhớ từ khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-002">Mối liên hệ giữa mã nguồn, trình biên dịch, bytecode và JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Mối liên hệ giữa mã nguồn, trình biên dịch, bytecode và JVM

## 1. Bức tranh tổng thể

Khi viết Java, bạn tạo **mã nguồn** (thường là tệp `.java`) để con người đọc và chỉnh sửa. Công cụ **`javac`** trong JDK biên dịch mã nguồn thành **bytecode** ở tệp `.class`. Khi chạy, chương trình **`java`** khởi động JVM, JVM tải những class cần thiết, kiểm tra và liên kết chúng, rồi thực thi bytecode bằng cơ chế thông dịch hoặc biên dịch JIT tùy JVM và quá trình chạy.

Mục tiêu của bài này là trả lời chính xác bốn câu hỏi:

1. `.java` và `.class` chứa những gì, chúng khác nhau ra sao?
2. `javac` làm việc lúc **biên dịch**, còn `java` và JVM làm việc lúc **chạy** như thế nào?
3. Vì sao bytecode Java thường dùng được trên nhiều hệ điều hành, và giới hạn của điều đó là gì?
4. Làm sao tự biên dịch, chạy, xem bytecode và chẩn đoán lỗi ở từng giai đoạn?

~~~mermaid
flowchart LR
    SRC["HelloPipeline.java<br/>Mã nguồn Java"] -->|"javac"| CLS["HelloPipeline.class<br/>Bytecode"]
    CLS -->|"java: khởi động JVM"| JVM["JVM<br/>Nạp, xác minh, liên kết,<br/>khởi tạo và thực thi class"]
    JVM -->|"Thông dịch hoặc JIT<br/>khi thích hợp"| NATIVE["Lệnh máy chạy trên<br/>CPU và hệ điều hành"]
~~~

Sơ đồ mô tả luồng phổ biến khi **biên dịch trước rồi chạy**. Nó không có nghĩa mọi bytecode đều được JIT biên dịch, cũng không có nghĩa JVM luôn nạp tất cả class của ứng dụng ngay khi khởi động.

## 2. Phân biệt bốn thành phần

| Thành phần | Vai trò | Thời điểm và kết quả |
|---|---|---|
| **Mã nguồn Java** | Biểu diễn lớp, phương thức, câu lệnh theo cú pháp Java | Người học tạo `HelloPipeline.java` |
| **Trình biên dịch `javac`** | Kiểm tra cú pháp, tên, kiểu và nhiều ràng buộc tĩnh; tạo bytecode | `javac HelloPipeline.java` tạo `HelloPipeline.class` nếu thành công |
| **Bytecode / tệp `.class`** | Chứa biểu diễn class đã biên dịch: metadata, constant pool, phương thức và chỉ thị bytecode | Là đầu vào cho JVM; **không phải** mã máy trực tiếp của Windows/Linux |
| **JVM** | Môi trường thực thi Java bytecode; quản lý tải class, bộ nhớ, exception và runtime | Một JVM thích hợp thực hiện lời gọi `main` cùng các class được sử dụng |

**JDK** (Java Development Kit) cung cấp các công cụ phát triển, tiêu biểu là `javac`, `java`, `javap`, cùng runtime Java. **JVM** là máy ảo thực thi bytecode, không phải tên khác của trình biên dịch. Trong cách phân phối Java hiện đại, thường cài **JDK** để vừa biên dịch vừa chạy; tài liệu cũ có thể gọi phần runtime riêng là **JRE**.

Phân biệt thêm: chương trình `java` là **launcher** (lệnh khởi động runtime); JVM là môi trường thực thi mà launcher khởi tạo. Nói ngắn gọn “chạy bằng Java” thường chỉ cả quá trình này, nhưng khi chẩn đoán lỗi, nên biết bước nào đã thất bại.

## 3. Ví dụ Java có thể tự chạy

Tạo tệp **`HelloPipeline.java`** (đúng chữ hoa/thường của public class) với nội dung:

~~~java
public class HelloPipeline {
    private static int twice(int value) {
        return value * 2;
    }

    public static void main(String[] args) {
        String name = args.length == 0 ? "Java" : args[0];

        System.out.println("Xin chao, " + name);
        System.out.println("2 * 3 = " + twice(3));
    }
}
~~~

Giả sử đã cài **JDK 17 hoặc mới hơn**. Mở terminal trong thư mục chứa tệp. Kiểm tra:

~~~bash
java -version
javac -version
~~~

Biên dịch và chạy (ví dụ cố định đích bytecode Java 17):

~~~bash
javac --release 17 -encoding UTF-8 HelloPipeline.java
java -cp . HelloPipeline
~~~

Kết quả dự kiến:

~~~text
Xin chao, Java
2 * 3 = 6
~~~

Thử truyền một đối số từ dòng lệnh:

~~~bash
java -cp . HelloPipeline Linh
~~~

Kết quả:

~~~text
Xin chao, Linh
2 * 3 = 6
~~~

Giải thích chính xác:

- `javac` đọc nội dung tệp nguồn, kiểm tra mã rồi tạo `HelloPipeline.class` trong thư mục hiện hành. Nếu biên dịch báo lỗi, bạn cần sửa source và biên dịch lại.
- `java -cp . HelloPipeline` yêu cầu launcher tìm **class tên `HelloPipeline` trên classpath** (dấu `.` là thư mục hiện hành), sau đó JVM thực hiện phương thức `public static void main(String[] args)` của class ấy.
- Khi chạy class đã biên dịch, tham số cho `java` là **tên class**, không phải tên tệp `.class`. Đúng: `java HelloPipeline`; sai trong chế độ này: `java HelloPipeline.class`.
- `args` chứa các đối số **sau** tên class. Trường hợp không có đối số đã được bảo vệ bằng `args.length == 0`, vì truy cập thẳng `args[0]` khi mảng trống sẽ gây `ArrayIndexOutOfBoundsException`.

**Lưu ý về `-encoding UTF-8`:** cờ này hướng dẫn `javac` đọc mã nguồn bằng UTF-8; nó **không tự thay đổi encoding của terminal**. Nếu chuỗi có dấu tiếng Việt in ra sai trên một số console, cần kiểm tra encoding của console và môi trường chạy riêng.

## 4. `javac` kiểm tra và tạo bytecode như thế nào?

Xét đoạn mã trong ví dụ:

~~~java
private static int twice(int value) {
    return value * 2;
}
~~~

Ở mức ý tưởng, trình biên dịch phải xác minh rằng:

1. Cú pháp khai báo lớp, phương thức và câu lệnh hợp lệ.
2. Biến `value` được khai báo, có kiểu `int` và phép nhân `value * 2` hợp lệ.
3. Giá trị trả về phù hợp với kiểu khai báo `int`.
4. Lời gọi `twice(3)` từ `main` có thể được giải quyết tới phương thức tương ứng.

Nếu viết `return "sai kiểu";`, `javac` sẽ từ chối bởi phương thức khai báo trả `int` nhưng biểu thức trả `String`. Lỗi này xảy ra **trước khi chương trình được chạy**.

Sau khi kiểm tra thành công, trình biên dịch tạo tệp `.class`. Một tệp class có thể chứa thông tin như tên class và superclass, các field/method, mô tả kiểu, constant pool và phần mã bytecode của method. Mức chi tiết phụ thuộc compiler, phiên bản Java và các tùy chọn biên dịch.

**Không nên đồng nhất việc “biên dịch thành công” với “chương trình đúng”.** `javac` không tự chứng minh mọi quy tắc nghiệp vụ, không chạy thử mọi nhánh, và thường không phát hiện lỗi như chia cho 0 ở một đường thực thi runtime, file không tồn tại hay `NullPointerException` phát sinh bởi dữ liệu thực.

## 5. Tự xem bytecode thay vì chỉ tưởng tượng

JDK có công cụ **`javap`** để xem cấu trúc một tệp `.class`. Sau khi biên dịch thành công, chạy:

~~~bash
javap -c -p HelloPipeline
~~~

Các phần có thể quan sát:

- Phương thức `twice(int)`, `main(java.lang.String[])` và constructor mặc định.
- Các chỉ thị thực hiện phép toán và lời gọi phương thức, chẳng hạn `iload`, `imul`, `ireturn` ở `twice`.
- Các thao tác truy cập `System.out` và gọi `println` trong `main`.
- Với phép nối chuỗi trên các JDK hiện đại, compiler có thể dùng `invokedynamic` thay vì một chuỗi lệnh nối `StringBuilder` dễ đoán.

Muốn xem metadata/class file version:

~~~bash
javap -verbose HelloPipeline
~~~

Nếu đã biên dịch bằng `--release 17`, phần class file thường hiển thị **`major version: 61`**. Đây là định dạng class file Java 17, không phải phiên bản hệ điều hành và cũng không phải số phiên bản của mã nguồn.

`javap` là công cụ **disassembler/inspector**: nó trình bày bytecode và metadata theo dạng người đọc được, không chạy ứng dụng. Không cần thuộc lòng từng opcode; hãy nhận ra **bytecode là lớp biểu diễn trung gian**, khác mã nguồn và khác mã máy của CPU.

### Kiểm chứng bằng một thay đổi có chủ đích

Đổi `return value * 2;` thành `return value * 3;`, **lưu tệp rồi chỉ chạy lại `java HelloPipeline` mà không biên dịch lại**. Kết quả vẫn đến từ `.class` cũ: `2 * 3 = 6`. Khi chạy lại `javac --release 17 -encoding UTF-8 HelloPipeline.java`, kết quả mới thành `2 * 3 = 9`. **Chú ý:** chuỗi nhãn `2 * 3 = ` trong `println` vẫn giữ nguyên, nhưng phép tính bên trong đã đổi thành nhân 3. Vì vậy, nhãn lúc này không mô tả đúng phép toán thực tế; đây là một ví dụ nhỏ về **kết quả trình bày sai ý nghĩa dù mã hợp lệ**. Hãy đổi nhãn nếu muốn thông tin hiển thị chính xác.

Đây là cách thực nghiệm rất rõ để phân biệt **source hiện tại** với **bytecode mà JVM đang thực thi**. Sau bài tập, hãy khôi phục `twice` về phép nhân 2 để các ví dụ còn lại nhất quán.

## 6. JVM làm gì sau lệnh `java`?

Với chế độ chạy class đã biên dịch, tiến trình diễn ra gần đúng như sau:

1. **Khởi động runtime:** launcher `java` xử lý tùy chọn, classpath/module path và yêu cầu class chính.
2. **Class loading:** JVM dùng các class loader để tìm, đọc và định nghĩa những class cần thiết. Nhiều class chỉ được tải khi được sử dụng.
3. **Linking:** JVM xác minh tính hợp lệ của class file (*verification*), chuẩn bị các cấu trúc/static storage cần thiết (*preparation*) và giải quyết symbolic references (*resolution*). Một số bước có thể được thực hiện trễ khi cần theo quy tắc JVM.
4. **Initialization:** trước khi dùng một class theo các trường hợp kích hoạt khởi tạo, JVM thực hiện static field initializer/static block của class ấy theo quy tắc ngôn ngữ.
5. **Execution:** JVM gọi `main`, thực hiện bytecode của chương trình, tạo object, gọi method, xử lý exception và quản lý bộ nhớ trong runtime.

JVM triển khai phổ biến có thể **thông dịch** bytecode ban đầu và dùng **JIT compiler** để tối ưu/biên dịch một số đoạn chạy nhiều lần thành native machine code. Điều đó thường cải thiện tốc độ, nhưng không bảo đảm mọi method đều được JIT. Các JVM khác nhau có thể chọn chiến lược thực thi khác nhau mà vẫn hỗ trợ cùng mô hình Java.

**Garbage collection (GC)** là dịch vụ runtime liên quan đến quản lý bộ nhớ object. Nó không thuộc nhiệm vụ `javac`, cũng không biến bytecode thành native code; GC và JIT là hai cơ chế khác nhau.

## 7. Vì sao Java được nói là “viết một lần, chạy nhiều nơi”?

Cùng một tệp `.class` hợp lệ có thể chạy trên các hệ điều hành khác nhau **nếu** ở đó có JVM tương thích với phiên bản class file và các API mà chương trình sử dụng.

| Thành phần | Có phụ thuộc hệ điều hành/kiến trúc không? |
|---|---|
| `HelloPipeline.java` | Mã nguồn thường có thể chia sẻ; môi trường build vẫn có thể khác |
| `HelloPipeline.class` | Bytecode được chuẩn hóa và thường có thể chuyển nền tảng |
| Bản cài JVM/JDK | Có bản build theo OS và CPU (Windows x64, Linux ARM, …) |
| Native library qua JNI, công cụ hệ thống, đường dẫn file | Có thể phụ thuộc nền tảng và cần xử lý riêng |

Vì vậy **portable bytecode** không có nghĩa “chạy được trên mọi máy mà không cài gì”. Máy chạy cần runtime phù hợp; ứng dụng có thể phụ thuộc vào thư viện, quyền truy cập file, cấu hình, native component hoặc phiên bản Java cụ thể.

Ví dụ, tệp class tạo bằng JDK mới có thể dùng **class file version cao hơn** JVM cũ hỗ trợ. Trường hợp đó xảy ra lỗi `UnsupportedClassVersionError` khi JVM cố nạp class, dù mã Java của bạn không có lỗi cú pháp.

## 8. Phiên bản JDK, `--release` và khả năng tương thích

Khi phát triển trên JDK mới nhưng cần chạy trên Java 17, hãy biên dịch với:

~~~bash
javac --release 17 -encoding UTF-8 HelloPipeline.java
~~~

`--release 17` yêu cầu `javac` dùng quy tắc ngôn ngữ, class file target và tập public API tiêu chuẩn tương ứng với Java 17. Nó thường đáng tin cậy hơn việc chỉ đổi class-file target mà vẫn vô tình gọi API Java mới.

Điều kiện: **trình biên dịch phải hỗ trợ release được chọn**. Một JDK cũ hơn không thể biên dịch cho ngôn ngữ/release mới mà nó chưa biết.

Không nhầm ba thứ:

- **JDK đang dùng để build**: kiểm tra bằng `javac -version`.
- **Mức tương thích của class file**: chịu ảnh hưởng bởi `--release`; xem bằng `javap -verbose`.
- **JVM thật sự chạy ứng dụng**: kiểm tra bằng `java -version`.

Trong môi trường cài nhiều JDK, `java` và `javac` có thể trỏ tới **hai phiên bản khác nhau** qua `PATH`/cấu hình công cụ. Chẩn đoán phiên bản phải kiểm tra **cả hai lệnh**, không dựa vào tên thư mục dự án.

## 9. Thư mục, package, classpath và tên class

Ví dụ đầu dùng **default package** để người mới chạy dễ dàng. Trong dự án, bạn thường đặt tên package. Giả sử có:

~~~text
src/
└── demo/
    └── Welcome.java
~~~

Tệp `src/demo/Welcome.java`:

~~~java
package demo;

public class Welcome {
    public static void main(String[] args) {
        System.out.println("Welcome from demo");
    }
}
~~~

Biên dịch vào thư mục output (tạo thư mục `out` trước bằng công cụ file/terminal của hệ điều hành):

~~~bash
javac --release 17 -d out src/demo/Welcome.java
java -cp out demo.Welcome
~~~

Kết quả:

~~~text
Welcome from demo
~~~

Khi có package `demo`, launcher cần **tên đủ điều kiện** `demo.Welcome`; classpath `out` phải trỏ tới **thư mục gốc chứa `demo/`**, không phải thư mục `out/demo`. `javac -d out` sẽ đặt class dưới cấu trúc package phù hợp.

Một số lỗi hay gặp:

- Gõ `java Welcome` dù class thuộc package `demo`.
- Đặt `-cp out/demo` thay vì `-cp out`.
- Chạy lệnh từ thư mục khác với nơi tưởng tượng, làm `.` hoặc `out` trỏ sai.
- Đưa đường dẫn đến `.java` hoặc `.class` thay vì tên class khi đang chạy ở chế độ class.

Classpath còn có thể chứa thư mục và các `.jar` phụ thuộc. Bài này chỉ cần hiểu: **compile cần tìm được các kiểu được tham chiếu; run cần tìm được các class ứng dụng thực sự sử dụng**. Đường dẫn compile và runtime không nhất thiết là một, nên thiếu phụ thuộc có thể chỉ lộ ra lúc chạy.

## 10. Phân loại lỗi theo giai đoạn

| Triệu chứng | Giai đoạn thường gặp | Cách kiểm tra đầu tiên |
|---|---|---|
| `';' expected`, `cannot find symbol`, `incompatible types` | Biên dịch | Kiểm tra tệp nguồn, cú pháp, tên/kiểu và thư viện có trên classpath biên dịch |
| `class HelloPipeline is public, should be declared in a file named HelloPipeline.java` | Biên dịch | Khớp tên public class và tên tệp, kể cả chữ hoa/thường |
| `javac: command not found` hoặc “not recognized” | Trước biên dịch | Kiểm tra cài JDK, `PATH`, terminal đang dùng |
| `Could not find or load main class` | Launcher/class loading | Kiểm tra classpath, package, vị trí file sau biên dịch và tên class |
| `Main method not found` | Khởi động chương trình | Kiểm tra class có phương thức entry point phù hợp, ví dụ `public static void main(String[] args)` |
| `UnsupportedClassVersionError` | Nạp class | So sánh class file target với `java -version` |
| `NoClassDefFoundError` khi gọi chức năng | Runtime/linking/loading | Kiểm tra class phụ thuộc có hiện diện và tương thích tại runtime |
| `NullPointerException`, chia cho 0, lỗi file | Trong lúc thực thi | Đọc stack trace, dữ liệu đầu vào và đường gọi thực tế |

**Phân biệt quan trọng:** `cannot find symbol` từ `javac` là lỗi về mã/tham chiếu ở thời điểm **compile**. `Could not find or load main class` là lỗi trong quá trình **launch/run**: một class có thể đã được biên dịch, nhưng launcher không tìm thấy nó trong classpath yêu cầu.

Nếu lỗi chỉ xuất hiện khi chạy, “biên dịch lại y nguyên” không nhất thiết giải quyết được. Hãy đọc tên exception, thông báo, vị trí trên stack trace và xác minh môi trường chạy.

## 11. Ngoại lệ thú vị: chạy trực tiếp tệp `.java`

Từ Java 11, với source-file launching, có thể chạy một chương trình một tệp đơn giản bằng:

~~~bash
java HelloPipeline.java
~~~

Trong **chế độ này**, launcher yêu cầu biên dịch nguồn phục vụ lần chạy rồi thực thi; bạn không cần tự gọi `javac` trước và không nhất thiết nhận được `.class` được lưu ra đĩa như lệnh `javac` truyền thống.

Vì vậy cần phân biệt:

- `javac HelloPipeline.java` rồi `java HelloPipeline`: **biên dịch trước, chạy class**.
- `java HelloPipeline.java`: **chạy từ tệp nguồn** qua source-file mode (khi môi trường/phiên bản hỗ trợ).

Điều này **không phủ định vai trò của bytecode/JVM**. Trong bài học về chuỗi build và quan sát class file, hãy sử dụng cặp lệnh `javac` + `java` và `javap` trước để thấy rõ các thành phần.

## 12. Edge cases cần biết từ sớm

**1. Sửa source nhưng không build lại.** Khi chạy class mode, JVM đọc bytecode có sẵn, không tự so sánh ngày sửa `.java` để biên dịch thay bạn. Dễ gây nhầm “tại sao chương trình vẫn in kết quả cũ?”.

**2. Compile thành công nhưng thiếu thư viện lúc chạy.** Một class có thể dùng kiểu từ thư viện lúc compile, nhưng khi chạy thiếu `.jar` chứa class cần thiết. Tùy thời điểm JVM cần class ấy, lỗi có thể chỉ xuất hiện khi đi tới chức năng liên quan.

**3. Classpath đúng tệp nhưng sai thư mục gốc.** Với `demo.Welcome`, đưa `out/demo` vào `-cp` là sai vị trí gốc; phải đưa `out`.

**4. Có nhiều class trong một source.** Một tệp Java có thể khai báo thêm các top-level class không public hoặc nested class; `javac` có thể tạo **nhiều tệp `.class`** từ một `.java`. Không suy ra lúc nào cũng có quan hệ một-một giữa file source và class file.

**5. Static initializer bị lỗi.** Một class có thể qua được compile nhưng lỗi lúc JVM khởi tạo class, ví dụ static initialization ném exception. Lỗi này khác hoàn toàn lỗi cú pháp ở `javac`.

**6. Console khác UTF-8.** Đọc source UTF-8 đúng chưa chắc console hiển thị ký tự Unicode đúng. Đây là sự khác biệt giữa bytecode, encoding source và encoding đầu ra.

**7. Chạy JDK quá cũ.** Một JVM không hỗ trợ class file version đã build có thể từ chối nạp file. Hãy chỉnh target/release phù hợp hoặc nâng runtime; đừng chỉnh nội dung bytecode bằng tay.

**8. Không có quyền hoặc thiếu tài nguyên.** Bytecode hợp lệ vẫn có thể thất bại khi truy cập file bị cấm, thiếu cấu hình, cổng mạng đã sử dụng hoặc bộ nhớ bị giới hạn. Compile-time checks không thay thế xử lý lỗi runtime.

## 13. Bài tập thực hành với đáp án kiểm chứng

### Bài A — xác định sản phẩm của từng lệnh

Cho ba lệnh:

~~~bash
javac --release 17 HelloPipeline.java
javap -c HelloPipeline
java -cp . HelloPipeline
~~~

Hãy ghép:

1. Tạo bytecode từ source.
2. Hiển thị thông tin/chỉ thị của bytecode.
3. Nạp và thực thi chương trình trên JVM.

**Đáp án:** 1 là `javac`, 2 là `javap`, 3 là `java`. Lệnh `javap` không thực hiện method `main`.

### Bài B — dự đoán lỗi

Bạn có `src/demo/Welcome.java` khai báo `package demo;`, đã compile `-d out`. Bạn chạy:

~~~bash
java -cp out Welcome
~~~

Điều gì có thể xảy ra? **Đáp án:** launcher không tìm được class có tên `Welcome` ở default package. Lệnh đúng là `java -cp out demo.Welcome`.

### Bài C — nhận diện giai đoạn

Bạn sửa tệp nguồn thành:

~~~java
int number = "sai";
~~~

Bạn nhận được thông báo lỗi ngay khi chạy `javac`. Lỗi xảy ra ở JVM hay compiler? **Đáp án:** trình biên dịch `javac` phát hiện không tương thích kiểu `String` → `int`; `main` không được thực thi vì compilation thất bại.

### Bài D — phiên bản class file

Bạn biên dịch bằng `--release 17` rồi xem `javap -verbose`. Tìm `major version: 61`. Hãy giải thích vì sao Java runtime cũ hơn mức hỗ trợ class file này có thể không chạy được, dù tệp `.class` không chứa đường dẫn Windows/Linux.

**Đáp án:** khả năng chuyển nền tảng của bytecode là một chuyện, khả năng **phiên bản runtime hiểu định dạng class file** là chuyện khác. JVM cần hỗ trợ class file version tương ứng.

### Bài E — chứng minh class file là đầu vào chạy

Sau khi biên dịch `HelloPipeline`, đổi một dòng thông báo trong source rồi chỉ chạy `java HelloPipeline`. Ghi kết quả. Biên dịch lại và ghi kết quả mới.

**Đáp án kỳ vọng:** lần chạy đầu tiếp tục lấy hành vi từ `.class` cũ; sau biên dịch, `.class` cập nhật nên kết quả phản ánh source mới. Đừng nhầm với `java HelloPipeline.java` vì đó là source-file mode.

## 14. Ghi nhớ và tiêu chí hoàn thành

- [ ] Giải thích được **`.java` → `javac` → `.class` → `java`/JVM** bằng lời của mình.
- [ ] Biên dịch và chạy thành công `HelloPipeline.java` trên JDK có hỗ trợ Java 17.
- [ ] Dùng `javap -c` để thấy bytecode mà không nhầm nó với native machine code.
- [ ] Biết khác biệt giữa compile-time error, class loading/launcher error và exception runtime.
- [ ] Hiểu classpath là nơi JVM tìm class, còn package quyết định tên class đầy đủ.
- [ ] Biết `--release` và `java -version` giải quyết hai câu hỏi về tương thích khác nhau.
- [ ] Không khẳng định mọi bytecode đều phải JIT biên dịch; không khẳng định ứng dụng Java chạy được khi không có runtime tương thích.

**Kết luận:** Mã Java không được CPU thực thi trực tiếp từ tệp `.java` trong quy trình build thông thường. `javac` biến mã nguồn thành bytecode chuẩn hóa; launcher `java` và JVM tải, kiểm tra, liên kết, khởi tạo và thực thi các class cần thiết. Nhờ lớp bytecode/JVM, một ứng dụng có thể chuyển nền tảng dễ hơn, nhưng vẫn phải đáp ứng đúng phiên bản runtime, thư viện và điều kiện môi trường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-003">Phân biệt Java, Java SE, JDK và môi trường chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Java, Java SE, JDK và môi trường chạy

> **Chủ đề:** Java core — language-basics / Tổng quan  
> **Mục tiêu:** Biết chính xác mình đang *viết bằng ngôn ngữ nào*, *dựa vào nền tảng nào*, *dùng bộ công cụ nào để xây dựng*, và *cần gì để chạy ứng dụng*. Sau bài học, bạn có thể dùng `javac` và `java` để tự biên dịch/chạy một chương trình đơn giản, xác định môi trường đang sử dụng và giải thích lỗi sai phiên bản phổ biến.

## 1. Vì sao bốn tên gọi này dễ bị nhầm?

Một bạn mới học thường nói: “Tôi đã cài Java nên có thể viết và chạy mọi chương trình Java”. Câu này chưa đủ rõ: máy có **công cụ biên dịch** không? Có **máy ảo Java** không? Chương trình được xây dựng cho **phiên bản nền tảng** nào? Lệnh `java` trong terminal thực sự trỏ đến bản cài đặt nào?

Một cách phân biệt hữu ích:

| Tên gọi | Nó là gì? | Dùng để làm gì? | Câu hỏi nhận diện |
| --- | --- | --- | --- |
| **Java (ngôn ngữ)** | Ngôn ngữ lập trình có cú pháp, kiểu dữ liệu, class, method và quy tắc ngữ nghĩa | Viết mã nguồn `.java` | “Đoạn mã này viết bằng ngôn ngữ gì?” |
| **Java SE** (*Java Platform, Standard Edition*) | Nền tảng chuẩn: các đặc tả liên quan cùng tập API cơ bản của Java | Xác định khả năng chuẩn mà chương trình Java thông thường dựa vào, ví dụ `java.lang`, `java.util`, `java.io` | “Ứng dụng dựa trên nền tảng/API Java chuẩn nào?” |
| **JDK** (*Java Development Kit*) | Bộ công cụ phát triển kèm khả năng chạy Java | Biên dịch, chạy, đóng gói, kiểm tra chương trình; có `javac`, `java`, `jar`… | “Tôi dùng công cụ nào để viết, biên dịch và xây dựng ứng dụng?” |
| **JVM** (*Java Virtual Machine*) | Máy ảo thực thi bytecode Java | Nạp, kiểm tra và thực thi mã `.class`, quản lý nhiều khía cạnh lúc chạy | “Ai thực sự thực thi bytecode?” |
| **JRE / môi trường chạy** | Khả năng chạy ứng dụng: máy ảo và thư viện/thành phần runtime cần thiết | Thực thi chương trình đã xây dựng mà không nhất thiết cần compiler | “Máy đích cần những thành phần gì để chạy?” |

**Đừng coi đây là năm sản phẩm cạnh tranh.** Các tên chỉ *những tầng khác nhau* của cùng hệ sinh thái. JDK thông thường bao gồm những thành phần cần để phát triển **và** chạy; một runtime tối giản có thể không chứa công cụ `javac`.

### Đặt từng khái niệm vào đúng vị trí

Trong quy trình thông thường, mã nguồn `.java` được viết theo **ngôn ngữ Java**; `javac` thuộc **JDK** tạo bytecode `.class`; lệnh `java` khởi động **JVM** để chạy class cùng những thư viện runtime cần thiết. **Java SE** xác định nền tảng và API chuẩn mà một bản triển khai tương thích cung cấp.

Bài này chú trọng **chọn và kiểm tra đúng môi trường**. Cơ chế nạp class, bytecode và các bước biên dịch được thực hành sâu ở bài về mã nguồn → trình biên dịch → bytecode → JVM.

## 2. Java: tên ngôn ngữ khác với tên nền tảng

Ở cấp ngôn ngữ, Java quy định những thứ như khai báo kiểu, biểu thức, class, interface, câu lệnh điều kiện, xử lý ngoại lệ và cách gọi method. Ví dụ:

~~~java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Xin chào Java!");
    }
}
~~~

Đây là **mã nguồn viết bằng ngôn ngữ Java**. Tệp có tên `Hello.java` vì chứa public class `Hello`.

Tuy nhiên, `System.out.println` không chỉ là cú pháp ngôn ngữ: `System` và `PrintStream` là các lớp được nền tảng cung cấp. Muốn chạy chương trình, bạn cần môi trường có những thành phần chuẩn tương ứng.

Cụm từ **“Java”** trong giao tiếp hằng ngày đôi khi ám chỉ cả ngôn ngữ lẫn nền tảng và công cụ. Khi cấu hình môi trường hoặc giải thích lỗi, nên gọi rõ **JDK**, **JVM**, **Java SE** hay **Java source** để tránh nhầm.

## 3. Java SE: nền tảng chuẩn, không phải tên lệnh cài đặt

**Java SE** là viết tắt của *Java Platform, Standard Edition*, tập trung vào những khả năng nền tảng thường dùng cho ứng dụng Java thông thường:

- Ngôn ngữ và các quy tắc tương thích với một phiên bản Java tương ứng.
- Máy ảo và đặc tả liên quan đến việc thực thi Java.
- Thư viện/API chuẩn, chẳng hạn các gói `java.lang` (kiểu cơ bản), `java.util` (collections), `java.time` (ngày giờ), `java.nio.file` (tệp/đường dẫn), `java.net` (mạng).
- Những tính năng cụ thể tùy theo **phiên bản Java SE**.

Java SE **không phải** một tệp `java-se.exe` hay một chương trình bạn chạy bằng lệnh `java-se`. Một bản phân phối JDK được tạo ra để triển khai nền tảng Java tương ứng, cung cấp compiler và các công cụ phát triển.

**Đặc tả, triển khai và bản phân phối khác nhau:**

| Khái niệm | Ý nghĩa |
| --- | --- |
| Đặc tả Java SE | Quy định các hành vi và API chuẩn cần đáp ứng |
| Triển khai | Mã phần mềm thực hiện các yêu cầu của nền tảng/VM |
| Bản phân phối JDK | Gói cài đặt thực tế từ một nhà cung cấp, có công cụ và runtime |

Chẳng hạn, **OpenJDK** là dự án triển khai Java mã nguồn mở; các tổ chức có thể phát hành những bản dựng JDK dựa trên OpenJDK. Đối với người mới học, quan trọng nhất là chọn một **JDK có phiên bản phù hợp** và kiểm tra đường dẫn công cụ; không phải tìm một chương trình riêng mang tên “Java SE”.

**Phạm vi:** Java SE là nền tảng lõi. Một ứng dụng web doanh nghiệp có thể còn dùng Jakarta EE, Spring hoặc thư viện khác; những thư viện/framework này **không tự động trở thành API chuẩn của Java SE**. Bài này không đi sâu vào các nền tảng đó.

## 4. JDK, JVM, JRE: ai biên dịch và ai chạy?

### 4.1. JDK — bộ đồ nghề phát triển

Một JDK thông thường có:

| Công cụ | Vai trò thường gặp |
| --- | --- |
| `javac` | Biên dịch mã nguồn `.java` thành bytecode `.class` |
| `java` | Khởi động chương trình Java và môi trường thực thi thích hợp |
| `jar` | Tạo, xem hoặc xử lý tệp JAR |
| `javadoc` | Sinh tài liệu từ chú thích JavaDoc |
| `jshell` | Thử các biểu thức/lệnh Java tương tác (các JDK hiện đại) |
| `jlink` | Tạo runtime image tùy biến từ các module cần thiết (JDK 9+) |

Khi học và xây dựng ứng dụng Java core, **cài JDK** là lựa chọn dễ nhất vì có cả compiler lẫn runtime. IDE như IntelliJ IDEA hoặc VS Code là trình soạn thảo/môi trường phát triển **khác với JDK**; IDE có thể tự tải/chọn JDK nhưng bản thân IDE không đồng nghĩa với JDK.

### 4.2. JVM — thành phần thực thi trong runtime

**JVM thực thi bytecode** và quản lý những khía cạnh như tải class, bộ nhớ, ngoại lệ; JVM hiện đại có thể kết hợp thông dịch và JIT. Nó không phải compiler `javac`. Lệnh `java` khởi động runtime có JVM cùng thư viện cần thiết. Bản JVM phải phù hợp hệ điều hành, CPU và phiên bản class file, còn bytecode thường có thể được chia sẻ giữa nhiều bản JVM tương thích.

Cài một JDK phổ biến thường đã có runtime và JVM để chạy ví dụ; người học không cần tải JVM riêng chỉ vì nhìn thấy các khái niệm JDK/JVM trong sơ đồ.

### 4.3. JRE — hiểu đúng theo bối cảnh phiên bản

**JRE** (*Java Runtime Environment*) là cách gọi quen thuộc cho bộ thành phần **chạy** ứng dụng: VM và các thư viện/tệp runtime cần thiết, nhưng không yêu cầu đầy đủ công cụ phát triển như `javac`.

- Ở nhiều bản phát hành Java cũ (đặc biệt Java 8), người học thường gặp **gói JRE riêng** hoặc thư mục `jre` nằm trong JDK.
- Từ thế hệ Java module hiện đại (Java 9+), cách đóng gói thay đổi. **Không nên mặc định** mọi JDK đều có một thư mục `jre` riêng hoặc có bộ cài JRE độc lập.
- Một số nhà cung cấp vẫn phân phối runtime-only; ứng dụng cũng có thể đóng gói runtime image được tạo bằng `jlink`. Vì vậy, nói tuyệt đối “Java mới không còn JRE” là **không chính xác**.

Phân biệt theo *khả năng*: máy có **`java` để chạy** chưa chắc có **`javac` để biên dịch**. Máy cài JDK đúng cách thì thông thường có cả hai.

## 5. Thực hành: kiểm tra, biên dịch và chạy chương trình

Ví dụ dùng **JDK 17 trở lên** để thống nhất các lệnh. Bạn có thể dùng JDK mới hơn, nhưng cần chọn phiên bản mà dự án/khóa học yêu cầu.

### Bước 1 — Kiểm tra công cụ trên máy

Mở terminal và chạy:

~~~bash
java -version
javac -version
~~~

Lệnh đầu xem runtime/launcher Java đang được tìm thấy; lệnh sau xem compiler Java đang được tìm thấy. Kết quả thực tế phụ thuộc bản JDK/bản phân phối bạn cài. **Hai lệnh không bắt buộc trỏ đến cùng một bản cài**, vì `PATH` có thể chứa nhiều JDK/JRE; do đó cần kiểm tra khi thấy phiên bản lệch nhau.

Bạn cũng có thể chạy:

~~~bash
java --version
javac --version
~~~

Cú pháp `--version` áp dụng cho các JDK hiện đại. Với môi trường Java cũ, dạng `-version` quen thuộc hơn.

**Nếu `java` có nhưng `javac` không tồn tại:** bạn có thể đang dùng runtime-only, hoặc `PATH` đang chỉ tới thư mục không chứa compiler. Không kết luận ngay là “Java hỏng”.

### Bước 2 — Viết một tệp nguồn tự kiểm tra runtime

Tạo tệp **JavaEnvironmentDemo.java** (đúng chữ hoa/thường với tên public class) và chép mã sau:

~~~java
public class JavaEnvironmentDemo {
    public static void main(String[] args) {
        String who = args.length == 0 ? "Java" : args[0];

        System.out.println("Xin chào, " + who + "!");
        System.out.println("Java version : "
                + System.getProperty("java.version"));
        System.out.println("Java vendor  : "
                + System.getProperty("java.vendor"));
        System.out.println("VM name      : "
                + System.getProperty("java.vm.name"));
        System.out.println("Runtime home : "
                + System.getProperty("java.home"));
        System.out.println("Số tham số   : " + args.length);
    }
}
~~~

Điểm cần hiểu:

- `main(String[] args)` là điểm bắt đầu của chương trình theo cách chạy class truyền thống.
- `System.getProperty("java.version")` mô tả **môi trường đang chạy chương trình**, không chứng minh compiler nào đã tạo tệp `.class`.
- `java.vm.name` cho biết tên VM hiện tại; `java.vendor` cho biết nhà cung cấp được runtime công bố.
- `java.home` là **đường dẫn runtime đang sử dụng**; không nhất thiết bằng biến môi trường `JAVA_HOME`.
- `args` chứa các đối số sau tên class khi gọi lệnh `java`.

### Bước 3 — Biên dịch rồi chạy

Trong thư mục chứa tệp nguồn:

~~~bash
mkdir out
javac -encoding UTF-8 -d out JavaEnvironmentDemo.java
java -cp out JavaEnvironmentDemo VietNam
~~~

Trước tiên, `mkdir out` tạo thư mục đích `out` (chỉ cần làm khi thư mục chưa có). Vì mã nguồn có chữ tiếng Việt, `-encoding UTF-8` giúp `javac` đọc đúng tệp UTF-8, kể cả trên máy mà encoding mặc định khác UTF-8; tùy chọn này **không** tự đổi encoding terminal. `javac -d out` ghi tệp `.class` vào thư mục đích đã tồn tại; khi có `package`, `javac` có thể tạo các thư mục con phù hợp. `java -cp out JavaEnvironmentDemo` tìm class trong đó rồi chạy phương thức `main`.

Đầu ra minh họa, phần phiên bản và đường dẫn **sẽ khác giữa các máy**:

~~~text
Xin chào, VietNam!
Java version : <phiên bản đang chạy>
Java vendor  : <nhà cung cấp runtime>
VM name      : <tên Java VM>
Runtime home : <đường dẫn runtime>
Số tham số   : 1
~~~

Các chuỗi trong dấu ngoặc nhọn ở ví dụ đầu ra chỉ là **chỗ giữ chỗ**, không phải văn bản Java thực sự in ra.

Chạy lại không truyền đối số:

~~~bash
java -cp out JavaEnvironmentDemo
~~~

Lúc này chương trình in `Xin chào, Java!` và `Số tham số   : 0`. Cùng tệp `.class` nhưng đầu ra khác do **đối số runtime**.

### Bước 4 — Hai cách khởi động khác nhau

Trên JDK 11+, bạn cũng có thể chạy `java JavaEnvironmentDemo.java VietNam` theo *source-file mode*. Launcher biên dịch nguồn phục vụ lần chạy; không cần lệnh `javac` riêng. Với `java -cp out JavaEnvironmentDemo VietNam`, launcher tìm **class đã biên dịch** trong `out`. Cả hai vẫn sử dụng runtime Java; chúng khác nhau ở **đầu vào dành cho launcher** và bước chuẩn bị bytecode.

### Bước 5 — Khi cần đóng gói JAR

Sau khi biên dịch vào thư mục `out`, có thể tạo JAR chạy được:

~~~bash
jar --create --file demo.jar --main-class JavaEnvironmentDemo -C out .
java -jar demo.jar VietNam
~~~

Lệnh `jar` nằm trong bộ công cụ JDK. Tham số `--main-class` tạo thông tin entry point trong manifest; nếu JAR không xác định main class hợp lệ, `java -jar` sẽ không biết bắt đầu ở đâu. Đây là JAR ứng dụng đơn giản **không cần thư viện ngoài**; với dự án có dependency, phải đóng gói hoặc cung cấp chúng bằng cách phù hợp.

## 6. Máy chạy ứng dụng cần gì?

**Một chương trình chỉ dùng API Java SE** cần runtime phù hợp với bytecode và API mà chương trình yêu cầu. Điều này **không bắt buộc** máy người dùng cuối phải có cả compiler hay IDE.

| Tình huống | Cần gì? | Không bắt buộc phải có |
| --- | --- | --- |
| Viết, biên dịch Java source | JDK thích hợp, editor/IDE nếu muốn | Spring, Maven cho ví dụ nhỏ |
| Chạy `.class` đã biên dịch | Runtime Java tương thích; đúng classpath và entry point | `javac` trên máy chạy |
| Chạy JAR ứng dụng | Runtime tương thích; JAR đúng manifest và đủ dependency | IDE hoặc mã nguồn |
| Chạy bằng runtime đóng gói kèm app | Runtime image chứa module/API app cần, cấu hình khởi chạy | JDK đầy đủ ở máy người dùng |

Một cách triển khai thực tế là **máy phát triển/CI dùng JDK để build**, còn máy production dùng runtime tương thích hoặc runtime image đóng gói. Ứng dụng có thư viện ngoài, native library, biến môi trường, tệp cấu hình, DB hoặc network còn phải đáp ứng các phụ thuộc ấy: câu “write once, run anywhere” **không** loại bỏ mọi khác biệt hệ điều hành và hạ tầng.

## 7. Phiên bản JDK và tương thích khi chạy

Khi kiểm tra một máy hoặc cấu hình CI, cần tách **JDK đang biên dịch**, **mức tương thích đã build** và **runtime thật sự chạy**. Lệnh `javac -version` và `java -version` có thể báo hai phiên bản khác nhau nếu `PATH` trỏ đến hai bản cài.

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| Build trên JDK mới để chạy Java 17 | Khi compiler hỗ trợ, dùng `javac --release 17 -encoding UTF-8 -d out JavaEnvironmentDemo.java` để nhắm cú pháp, class file và API chuẩn của Java 17 |
| Chạy class trên JVM cũ hơn đích biên dịch | Có thể gặp `UnsupportedClassVersionError`; kiểm tra phiên bản runtime và mức class file, không chữa bằng cách đổi tên tệp |
| Compiler và launcher đến từ hai bản JDK khác nhau | Xác nhận vị trí thực của `java`, `javac` và cấu hình build thay vì chỉ xem `JAVA_HOME` |
| Có thư viện ngoài hoặc thành phần native | `--release` không tự chứng minh mọi dependency và điều kiện môi trường chạy được trên runtime đích; cần chạy thử bản phân phối thực tế |

Phiên bản được chọn phải khớp yêu cầu của dự án, thư viện, CI và môi trường triển khai. Để xem **môi trường đang thực thi**, sử dụng `java -version` ngay trên máy đích hoặc các property `java.version`, `java.home` trong chương trình mẫu; không suy luận từ máy dùng để biên dịch.

## 8. JAVA_HOME, PATH và classpath: ba vai trò khác nhau

| Tên | Ý nghĩa | Lỗi thường gặp |
| --- | --- | --- |
| `JAVA_HOME` | Biến môi trường thường được công cụ build dùng để tìm thư mục JDK | Trỏ tới JDK khác với JDK mà terminal đang dùng |
| `PATH` | Danh sách thư mục hệ điều hành tìm kiếm lệnh `java`, `javac`… | `java` và `javac` nằm ở hai bộ cài khác nhau |
| **Classpath** (`-cp`/`--class-path`) | Nơi Java tìm class và một số resource của ứng dụng | Biên dịch xong nhưng runtime không tìm thấy main class hoặc dependency |

Một số kiểm tra vị trí lệnh:

**Windows PowerShell:**

~~~powershell
where.exe java
where.exe javac
$env:JAVA_HOME
~~~

**macOS / Linux (terminal shell phổ biến):**

~~~bash
command -v java
command -v javac
echo "$JAVA_HOME"
~~~

Bạn có thể cài nhiều JDK trên một máy. Các lệnh kiểm tra trên chỉ hỗ trợ chẩn đoán; **không tự sửa môi trường**. Đừng vội thay `PATH` cho toàn hệ thống khi dự án khác vẫn cần một phiên bản cũ.

Ví dụ phân biệt classpath và đường dẫn tệp: sau khi `javac -d out` tạo `out/JavaEnvironmentDemo.class`, ta chạy:

~~~bash
java -cp out JavaEnvironmentDemo
~~~

Không chạy bằng tên `JavaEnvironmentDemo.class` trong vị trí tên class. `java` cần **tên lớp** (hoặc tên lớp có package), không phải phần mở rộng `.class`.

Nếu source có `package demo;` và class được biên dịch thành `out/demo/Main.class` thì entry point là **`demo.Main`** và lệnh thường là `java -cp out demo.Main`. Đây là cơ chế tìm lớp, không phải một công cụ khác với JVM.

## 9. Khi gặp lỗi: chẩn đoán môi trường có thứ tự

Đầu tiên, chạy `java -version` và `javac -version`, sau đó tìm vị trí thật của hai lệnh bằng `where.exe` (Windows) hoặc `command -v` (macOS/Linux). So sánh kết quả với `JAVA_HOME` và JDK mà IDE/công cụ build chọn. Khi compiler và runtime đã được xác định, mới xem `-cp`, tên class và dependency.

| Hiện tượng | Câu hỏi chẩn đoán hữu ích |
| --- | --- |
| Có `java` nhưng không có `javac` | Máy chỉ có runtime hay `PATH` đang trỏ sai thư mục `bin` của JDK? |
| Terminal dùng Java A nhưng IDE build bằng Java B | IDE/Gradle chọn JDK nào? `JAVA_HOME` có bị ưu tiên khác với `PATH` không? |
| Build thành công, chạy báo `UnsupportedClassVersionError` | Class file cần phiên bản runtime nào; máy chạy đang dùng `java -version` nào? |
| `Could not find or load main class` | Lệnh `-cp` trỏ đến thư mục gốc chứa package chưa, và có dùng tên class đầy đủ thay vì `.class` không? |
| Chạy JAR nhưng thiếu class ở runtime | Dependency có thực sự được đóng gói hoặc đưa vào classpath/module path không? |

Một lỗi có thể xuất hiện ở bước khởi động hoặc tải class chứ không phải ở việc Java SE/JDK được “cài sai”. Hãy dựa vào thông báo cụ thể để xử lý đúng thành phần và giữ cấu hình các dự án khác trên máy.

## 10. Những hiểu lầm cần tránh

- **JDK không phải JVM.** JDK là bộ công cụ phát triển có runtime; JVM là thành phần thực thi bytecode.
- **Java SE không phải tên bộ cài của một hãng.** Nó là nền tảng/API chuẩn; một bản JDK cụ thể cung cấp triển khai.
- **Có lệnh `java` không bảo đảm có `javac`.** Hãy kiểm tra cả khả năng chạy lẫn biên dịch theo yêu cầu của máy.
- **“Java hiện đại hoàn toàn không còn JRE” là khẳng định quá rộng.** Có nhiều hình thức đóng gói runtime, gồm runtime-only và image tùy biến bằng `jlink`; không nên giả định phải có thư mục `jre` độc lập.
- **IDE và `JAVA_HOME` không tự quyết định kết quả của mọi terminal.** Kiểm tra đường dẫn `java`/`javac` được gọi, JDK của dự án và runtime đích.

## 11. Bài tập tự luyện có lời giải định hướng

**Bài 1 — Gọi đúng tên:** Một máy chỉ cần chạy `demo.jar` đã build. Máy đó bắt buộc phải cài full JDK không? Vì sao?

*Định hướng:* Không bắt buộc. Cần runtime tương thích, có đủ thư viện và dependency, cùng entry point đúng. Máy build mới cần compiler/JDK.

**Bài 2 — Giải thích hai lệnh:** So sánh `javac -encoding UTF-8 -d out JavaEnvironmentDemo.java` với `java -cp out JavaEnvironmentDemo`.

*Định hướng:* Lệnh một tạo bytecode `.class`; lệnh hai khởi động runtime để tìm và thực thi class đã biên dịch. Không dùng phần mở rộng `.class` trong tên entry point.

**Bài 3 — Phân tích môi trường:** `java -version` báo phiên bản A, `javac -version` báo phiên bản B. Có thể kết luận máy thiếu JDK không?

*Định hướng:* Chưa. Có thể cả hai được cài nhưng `PATH` ưu tiên hai vị trí khác nhau. Kiểm tra đường dẫn thực tế của cả hai lệnh và mục tiêu build/runtime.

**Bài 4 — Suy luận lỗi:** Bạn biên dịch bằng compiler mới, đưa tệp `.class` sang máy chạy Java cũ và nhận `UnsupportedClassVersionError`. Có nên đổi tên tệp `.class` để khắc phục?

*Định hướng:* Không. Đây là vấn đề định dạng/phiên bản bytecode. Chọn runtime tương thích hoặc biên dịch cho release thấp hơn mà code và thư viện thực sự hỗ trợ.

**Bài 5 — Thử nghiệm:** Chạy ví dụ `JavaEnvironmentDemo` bằng hai cách: (a) `javac` rồi `java -cp` và (b) `java JavaEnvironmentDemo.java`. Ghi lại điểm giống nhau về nội dung đầu ra và điểm khác nhau về cách launcher chuẩn bị mã chạy.

### Tự kiểm tra trước khi chuyển bài

Bạn đã nắm bài nếu có thể giải thích bằng lời của mình: **Java là ngôn ngữ; Java SE là nền tảng chuẩn; JDK giúp xây dựng và cũng chạy chương trình; JVM thực thi bytecode; runtime/JRE là tập thành phần cần để chạy**. Khi mở terminal, bạn biết kiểm tra `java`/`javac`, chạy ví dụ, nhận diện classpath và biết tại sao phiên bản build và runtime phải tương thích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-004">Dữ liệu, biểu thức, câu lệnh và luồng thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Dữ liệu, biểu thức, câu lệnh và luồng thực thi

## 1. Bốn khái niệm tạo thành một chương trình Java

Một chương trình Java nhận **dữ liệu**, dùng **biểu thức** để tính toán/so sánh, dùng **câu lệnh** để thực hiện công việc, và tuân theo **luồng thực thi** để biết phần mã nào được chạy và chạy bao nhiêu lần.

Ví dụ:

~~~java
int price = 80;
int quantity = 2;
int total = price * quantity;

if (total >= 100) {
    System.out.println("Đơn hàng lớn");
}
~~~

- `80` và `2` là các **giá trị literal**; `price`, `quantity`, `total` là biến có kiểu `int`.
- `price * quantity` là **biểu thức**: khi đánh giá, nó tạo ra giá trị `160`.
- `int total = price * quantity;` là **câu lệnh khai báo biến cục bộ** có biểu thức khởi tạo.
- `total >= 100` là biểu thức kiểu `boolean`, được `if` sử dụng để quyết định có chạy khối in thông báo hay không.
- Dấu `{ }` giới hạn **block**; những câu lệnh bên trong block chỉ được chạy khi điều kiện phù hợp.

Quan hệ chính có thể hình dung:

~~~mermaid
flowchart TD
    DATA["Dữ liệu và biến"] --> EXP["Đánh giá biểu thức"]
    EXP --> STMT["Thực hiện câu lệnh"]
    STMT --> DEC{"Điều kiện / vòng lặp?"}
    DEC -->|"Đúng hoặc còn lặp"| STMT
    DEC -->|"Không / hoàn tất"| END["Câu lệnh tiếp theo<br/>hoặc kết thúc phương thức"]
~~~

Sơ đồ là mô hình khái quát; một câu lệnh có thể chứa **nhiều** biểu thức, một biểu thức có thể có tác dụng phụ, và các cấu trúc `return`, `break`, `continue` hoặc exception có thể chuyển điều khiển sang vị trí khác.

## 2. Dữ liệu: giá trị, kiểu và biến

### Kiểu dữ liệu cơ bản

Java là ngôn ngữ có **kiểu tĩnh**: mỗi biến và biểu thức có một kiểu được trình biên dịch kiểm tra. Tám kiểu nguyên thủy (*primitive*) gồm:

| Kiểu | Ý nghĩa và ví dụ | Lưu ý |
|---|---|---|
| `byte`, `short`, `int`, `long` | Số nguyên: `int age = 20;`, `long id = 123L;` | Có miền giá trị giới hạn; số nguyên không tự vô hạn |
| `float`, `double` | Số dấu phẩy động: `double rate = 0.15;` | Có sai số biểu diễn và các giá trị đặc biệt |
| `char` | Một **đơn vị mã UTF-16**: `char mark = 'A';` | Không đồng nhất với mọi ký tự Unicode hoàn chỉnh |
| `boolean` | `true` hoặc `false` | Không tự đổi thành `0`/`1` như một số ngôn ngữ khác |

`String`, mảng như `int[]`, và các object khác là **kiểu tham chiếu**. Biến tham chiếu có thể giữ tham chiếu tới object hoặc `null`, không chứa nguyên cả object theo cách biến primitive giữ giá trị của nó:

~~~java
int count = 3;
String label = "Java";
int[] points = {10, 20, 30};
String missing = null;
~~~

`label.length()` hoạt động khi `label` không null, còn `missing.length()` ném `NullPointerException` tại runtime. Kiểu tĩnh `String` không tự bảo đảm tham chiếu luôn khác null.

### Literal, kiểu suy ra và phép gán

~~~java
int units = 10;
long population = 8_000_000_000L;
double ratio = 0.25;
boolean active = true;
char grade = 'A';
String city = "Ha Noi";

units = units + 5;
final int MAX_ITEMS = 20;
~~~

Các literal nguyên như `10` thường có kiểu `int`, trừ một số trường hợp/đuôi literal phù hợp; `L` chỉ literal `long`. Dấu gạch dưới trong `8_000_000_000L` giúp dễ đọc, không đổi giá trị. `final` trên biến cấm **gán lại biến** sau khi nó đã được gán, nhưng nếu đó là tham chiếu tới object mutable, không có nghĩa mọi trạng thái của object đều bất biến.

Trong Java 10+, biến cục bộ có thể dùng `var` khi có initializer phù hợp:

~~~java
var score = 10;     // Suy ra int ở thời điểm biên dịch
var message = "Hi"; // Suy ra String
~~~

`var` không biến Java thành ngôn ngữ kiểu động: kiểu suy ra vẫn cố định. Không thể viết biến cục bộ `var unknown = null;` vì compiler không suy ra kiểu phù hợp từ literal `null` đơn lẻ.

### Gán không giống so sánh

- `=` **gán** giá trị ở vế phải cho biến/vị trí lưu hợp lệ ở vế trái.
- `==` **so sánh** hai toán hạng; với primitive, thường so sánh giá trị; với hai tham chiếu object, so sánh **cùng tham chiếu** hay không.
- `"abc".equals(other)` kiểm tra nội dung theo hợp đồng của `String`; không nên dùng `==` để kiểm tra hai chuỗi có cùng ký tự.

~~~java
String a = new String("Java");
String b = new String("Java");
System.out.println(a == b);      // false: hai object khác nhau
System.out.println(a.equals(b)); // true: nội dung giống nhau
~~~

## 3. Biểu thức là gì?

**Biểu thức (expression)** được đánh giá để tạo **một giá trị hoặc kết quả** và có một kiểu. Biểu thức có thể là literal, tên biến, phép tính, lời gọi method có kiểu trả về, phép so sánh, phép gán, hoặc một biểu thức điều kiện.

| Biểu thức | Kiểu/kết quả (giả sử biến hợp lệ) | Công dụng |
|---|---|---|
| `42` | `int` / 42 | Literal |
| `price * quantity` | Số được tính từ hai toán hạng | Số học |
| `score >= 60` | `boolean` | Điều kiện |
| `ready && hasTicket` | `boolean` | Kết hợp điều kiện |
| `isMember ? 90 : 100` | Số được chọn theo điều kiện | Conditional expression |
| `"Hi".length()` | `int` / 2 | Gọi method |
| `x += 2` | Phép gán có giá trị kết quả | Cập nhật biến |

Không phải biểu thức nào cũng được phép đứng riêng thành **expression statement** trong Java. Hợp lệ, chẳng hạn:

~~~java
int x = 3;
x++;                     // increment statement
x = x + 2;               // assignment statement
System.out.println(x);   // method invocation statement
new StringBuilder();     // class instance creation statement
~~~

Nhưng một phép tính trần `x + 2;` **không phải câu lệnh hợp lệ**: giá trị vừa tính không được sử dụng, và Java không cho phép mọi expression đứng độc lập như statement.

Một lời gọi method trả `void`, chẳng hạn `System.out.println("Hi")`, được dùng trong câu lệnh gọi method nhưng không tạo ra một giá trị `void` có thể gán cho biến.

## 4. Toán tử, ưu tiên và thứ tự đánh giá

Các nhóm toán tử quan trọng:

| Nhóm (từ ưu tiên cao đến thấp, lược giản) | Ví dụ |
|---|---|
| Hậu tố, lời gọi và truy cập | `x++`, `arr[i]`, `obj.method()` |
| Một ngôi | `++x`, `-x`, `!valid` |
| Nhân/chia/chia dư | `*`, `/`, `%` |
| Cộng/trừ | `+`, `-` |
| So sánh thứ tự | `<`, `<=`, `>`, `>=` |
| So sánh bằng/khác | `==`, `!=` |
| AND logic ngắn mạch | `&&` |
| OR logic ngắn mạch | `||` |
| Điều kiện ba ngôi | `condition ? a : b` |
| Gán | `=`, `+=`, `-=`, ... |

Bảng cố ý bỏ bớt toán tử dịch bit, bitwise và một số nhóm trung gian. **Dùng ngoặc** khi muốn diễn đạt rõ ý, thay vì cố thuộc toàn bộ bảng ưu tiên.

~~~java
int a = 2 + 3 * 4;      // 14
int b = (2 + 3) * 4;    // 20
boolean ok = a > 10 && b == 20; // true
~~~

**Ba quy tắc không được nhầm lẫn:**

1. **Ưu tiên** quyết định cách nhóm toán tử: `2 + 3 * 4` là `2 + (3 * 4)`.
2. **Thứ tự đánh giá toán hạng** của Java thường là **từ trái sang phải** theo biểu thức, ngoại trừ việc có toán hạng bị bỏ qua do quy tắc ngắn mạch hoặc nhánh điều kiện. Đừng lấy bảng ưu tiên để suy đoán một lời gọi method bên phải luôn xảy ra trước bên trái.
3. **Tính ngắn mạch**: `&&` không đánh giá vế phải khi vế trái là `false`; `||` không đánh giá vế phải khi vế trái là `true`.

Ví dụ an toàn:

~~~java
String input = null;
boolean valid = input != null && !input.isBlank();
System.out.println(valid); // false; không gọi input.isBlank()
~~~

Nếu đổi `&&` thành `&` với hai biểu thức boolean, cả hai phía được đánh giá và ví dụ trên sẽ gây `NullPointerException`. Hai toán tử **có cùng giá trị logic trong nhiều trường hợp nhưng khác hành vi đánh giá**.

### Tác dụng phụ trong biểu thức

~~~java
int x = 5;
int old = x++; // old = 5, sau đó x = 6
int now = ++x; // tăng x thành 7, now = 7
~~~

`x++` trả giá trị **trước khi tăng**, còn `++x` trả giá trị **sau khi tăng**. Tránh trộn nhiều lần tăng hoặc gọi method có tác dụng phụ trong một biểu thức khó đọc; tách thành các câu lệnh đơn giản để dễ kiểm tra.

Ví dụ đánh giá theo trái sang phải:

~~~java
static int logAndReturn(String label, int value) {
    System.out.println(label);
    return value;
}

// Đặt lời gọi sau trong một method:
int sum = logAndReturn("trai", 2) + logAndReturn("phai", 3);
// In "trai", rồi "phai"; sum = 5
~~~

## 5. Số học: các edge case dễ gây lỗi

### Chia số nguyên và chia dư

~~~java
System.out.println(7 / 2);   // 3, vì cả hai là int
System.out.println(7.0 / 2); // 3.5
System.out.println(7 % 2);   // 1
System.out.println(-7 / 2);  // -3: làm tròn về 0
System.out.println(-7 % 2);  // -1
~~~

Nếu muốn phần thập phân, phải có toán hạng `double`/`float` hoặc ép kiểu thích hợp. Ép kiểu **sau** phép chia nguyên thì đã quá muộn: `(double) (7 / 2)` là `3.0`, còn `(double) 7 / 2` là `3.5`.

### Tràn số nguyên

~~~java
int max = Integer.MAX_VALUE;
System.out.println(max + 1); // -2147483648 (tràn int)
~~~

Cộng `int` thông thường có thể tràn theo quy tắc số học Java. Nếu nghiệp vụ **không cho phép tràn**, dùng `Math.addExact(a, b)` để nhận `ArithmeticException` khi kết quả vượt miền giá trị, hoặc chọn cách biểu diễn số lớn phù hợp.

### Chia cho 0 và dấu phẩy động

~~~java
// int broken = 10 / 0; // Chia số nguyên cho 0 gây ArithmeticException khi thực thi phép chia
System.out.println(1.0 / 0.0); // Infinity
System.out.println(0.0 / 0.0); // NaN
System.out.println(0.1 + 0.2); // Thường hiển thị 0.30000000000000004
~~~

Số dấu phẩy động `double` tuân theo biểu diễn nhị phân IEEE 754 với các giới hạn độ chính xác. Không dùng so sánh `==` thiếu suy xét cho kết quả tính toán thập phân; khi làm việc với tiền, cân nhắc **số nguyên theo đơn vị nhỏ nhất** hoặc `BigDecimal` với quy tắc làm tròn được định nghĩa.

## 6. Câu lệnh và block: chương trình thực sự làm gì?

**Câu lệnh (statement)** yêu cầu Java thực hiện một hành động, có thể sử dụng kết quả biểu thức để điều khiển bước tiếp theo. Những loại thường gặp:

- Khai báo biến cục bộ: `int total = 0;`.
- Câu lệnh biểu thức: `total += price;`, `printResult();`.
- Block: `{ ... }`.
- Rẽ nhánh: `if / else`, `switch`.
- Lặp: `for`, `while`, `do-while`.
- Thay đổi luồng: `break`, `continue`, `return`, `throw`.

Ví dụ block và phạm vi biến:

~~~java
int outside = 10;
if (outside > 0) {
    int inside = outside * 2;
    System.out.println(inside); // 20
}
// System.out.println(inside); // Lỗi compile: outside scope
~~~

Một biến cục bộ phải được **gán chắc chắn trước khi đọc** theo phân tích của compiler (*definite assignment*). Ví dụ:

~~~java
boolean passed = true;
String status;
if (passed) {
    status = "PASS";
} else {
    status = "FAIL";
}
System.out.println(status); // hợp lệ: hai nhánh đều gán
~~~

Nếu bỏ nhánh `else` mà biến `status` có thể chưa được gán, compiler sẽ từ chối đọc nó. Khác với **field instance** có giá trị mặc định khi object được tạo, biến cục bộ không tự được cấp một giá trị mặc định để đọc tùy ý.

## 7. Rẽ nhánh: `if`, `else` và `switch`

### `if / else if / else`

~~~java
int score = 75;
if (score >= 90) {
    System.out.println("Xuat sac");
} else if (score >= 60) {
    System.out.println("Dat");
} else {
    System.out.println("Chua dat");
}
// In "Dat"
~~~

Điều kiện `if` phải là `boolean` hoặc kiểu phù hợp được unbox; Java **không** coi `0`, `1` hoặc chuỗi rỗng tự động là `false`/`true`. Nên dùng dấu ngoặc nhọn kể cả cho một câu lệnh để tránh lỗi khi thêm nhánh hoặc chỉnh sửa mã.

### `switch` statement và `switch` expression

Java 17 hỗ trợ kiểu `switch` có nhãn arrow:

~~~java
String mode = "FAST";
int timeout = switch (mode) {
    case "FAST" -> 3;
    case "NORMAL" -> 10;
    default -> 20;
};
System.out.println(timeout); // 3
~~~

Đây là **switch expression** vì toàn bộ `switch` tạo một giá trị để gán cho `timeout`. Với `switch` statement, mục tiêu chính là lựa chọn và thực thi nhánh câu lệnh, không nhất thiết sinh ra giá trị.

So với dạng `case ...:` truyền thống, nhánh `->` không tự **fall through** xuống nhánh kế tiếp. Switch expression cần bao phủ mọi trường hợp hợp lệ; `default` trong ví dụ giúp rõ ràng khi chuỗi có giá trị ngoài hai trường hợp liệt kê.

`switch` không phải thay thế mọi `if`: dùng khi mô hình “chọn giữa nhiều trường hợp” hợp lý hơn nhiều điều kiện bất kỳ.

## 8. Vòng lặp: `for`, `while`, `do-while`

### `for` khi biết cách tiến qua chỉ số

~~~java
int total = 0;
for (int i = 1; i <= 3; i++) {
    total += i;
}
System.out.println(total); // 6
~~~

Trình tự: chạy phần khởi tạo `i = 1` một lần → kiểm tra `i <= 3` → chạy thân vòng lặp → chạy cập nhật `i++` → kiểm tra tiếp. Nếu điều kiện ban đầu sai, thân có thể **không chạy lần nào**.

### Enhanced `for` để duyệt phần tử

~~~java
int[] values = {2, 4, 6};
int sum = 0;
for (int value : values) {
    sum += value;
}
System.out.println(sum); // 12
~~~

Enhanced `for` đơn giản khi chỉ cần từng phần tử, nhưng không tự cho chỉ số. Đổi giá trị biến cục bộ `value` không phải cách để thay trực tiếp phần tử `int` trong mảng.

### `while` và `do-while`

~~~java
int n = 3;
while (n > 0) {
    System.out.print(n + " ");
    n--;
}
// In "3 2 1 "
~~~

`while` kiểm tra **trước** mỗi lượt, còn `do-while` kiểm tra **sau** thân, vì thế thân `do` luôn chạy ít nhất một lần nếu được tới câu lệnh đó:

~~~java
int attempts = 0;
do {
    attempts++;
} while (attempts < 1);
System.out.println(attempts); // 1
~~~

### `break` và `continue`

~~~java
for (int i = 1; i <= 5; i++) {
    if (i == 2) continue; // bỏ phần còn lại của lượt i=2
    if (i == 4) break;    // thoát vòng lặp
    System.out.print(i + " ");
}
// In "1 3 "
~~~

`continue` chuyển tới lượt tiếp theo của vòng lặp chứa nó; `break` rời khỏi vòng lặp/switch statement phù hợp. Với vòng lặp lồng nhau, dạng không nhãn tác động lên cấu trúc bao gần nhất theo quy tắc ngôn ngữ. Khi duyệt nhiều tầng, giữ code dễ đọc thay vì lạm dụng `break` có nhãn.

## 9. `return`, exception và luồng kết thúc sớm

`return` kết thúc **lời gọi method hiện tại**, đồng thời trả về một giá trị nếu method có kiểu trả về khác `void`:

~~~java
static int safePercent(int value, int max) {
    if (max <= 0) {
        throw new IllegalArgumentException("max phai duong");
    }
    return value * 100 / max;
}
~~~

Ở đây `throw` tạo luồng thực thi ngoại lệ. Khi `max <= 0`, phép chia không diễn ra; exception chuyển điều khiển tới nơi xử lý phù hợp hoặc làm lời gọi thất bại.

**Giới hạn kỹ thuật cần nhớ:** `value * 100` vẫn có thể tràn `int`, dù `max` dương. Với dữ liệu lớn, có thể chuyển phép tính sang `long` trước rồi kiểm tra miền kết quả. Một nhánh phòng lỗi không tự làm toàn bộ phép tính an toàn.

Cơ chế `try/catch/finally` cho phép xử lý và dọn tài nguyên, nhưng không nên dùng exception thay cho toàn bộ kiểm tra điều kiện thông thường. Về trình tự, khi có `return` hoặc exception, những câu lệnh sau nó trên đường đi hiện tại không được thực hiện như bình thường. Nếu có `finally`, Java thường thực thi nó khi rời `try`/`catch` trước khi chuyển điều khiển ra ngoài.

~~~java
static int example() {
    try {
        System.out.println("try");
        return 10;
    } finally {
        System.out.println("finally");
    }
}
// Khi gọi example(): in "try", rồi "finally", sau đó method trả 10.
~~~

Tránh đặt `return` trong `finally`, vì có thể che mất kết quả/exception đang xảy ra; với tài nguyên `AutoCloseable`, `try-with-resources` thường rõ trách nhiệm hơn.

## 10. Ví dụ tổng hợp chạy được: từ dữ liệu đến luồng thực thi

Tạo tệp tên chính xác **`FlowWalkthrough.java`** và lưu bằng UTF-8:

~~~java
public final class FlowWalkthrough {
    private FlowWalkthrough() {}

    static int payable(int total, boolean member) {
        if (total < 0) {
            throw new IllegalArgumentException("Tong phai khong am");
        }
        int discount = member && total >= 100 ? 15 : 0;
        return total - discount;
    }

    public static void main(String[] args) {
        int[] prices = {40, 70, 20};
        int total = 0;
        int processed = 0;

        for (int i = 0; i < prices.length; i++) {
            int price = prices[i];
            if (price <= 0) {
                continue;
            }
            total += price;
            processed++;
        }

        boolean member = args.length > 0
                && "member".equalsIgnoreCase(args[0]);

        int amount = payable(total, member);
        String group = switch (amount) {
            case 0 -> "MIEN_PHI";
            default -> amount >= 100 ? "LON" : "NHO";
        };

        System.out.println("So mon: " + processed);
        System.out.println("Tong: " + total);
        System.out.println("Thanh vien: " + member);
        System.out.println("Can tra: " + amount);
        System.out.println("Nhom: " + group);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 FlowWalkthrough.java
java FlowWalkthrough
java FlowWalkthrough member
~~~

**Chạy không có đối số**:

~~~text
So mon: 3
Tong: 130
Thanh vien: false
Can tra: 130
Nhom: LON
~~~

**Chạy với đối số `member`**:

~~~text
So mon: 3
Tong: 130
Thanh vien: true
Can tra: 115
Nhom: LON
~~~

### Trace lần chạy có đối số `member`

| Bước | Câu lệnh/biểu thức | Trạng thái sau bước |
|---|---|---|
| 1 | `prices = {40,70,20}` | Mảng có 3 phần tử |
| 2 | `total=0; processed=0` | Tổng 0; đã xử lý 0 |
| 3 | Lượt `i=0`, giá 40 | Tổng 40; đã xử lý 1 |
| 4 | Lượt `i=1`, giá 70 | Tổng 110; đã xử lý 2 |
| 5 | Lượt `i=2`, giá 20 | Tổng 130; đã xử lý 3 |
| 6 | `args.length > 0 && ...` | `member=true` |
| 7 | `member && total >= 100 ? 15 : 0` | `discount=15` |
| 8 | `return total - discount` | `amount=115` |
| 9 | `switch(amount)` | `group="LON"` |
| 10 | Các câu lệnh `println` | In 5 dòng như trên |

**Các chi tiết đáng chú ý:**

- `prices.length` dùng để giới hạn chỉ số, tránh truy cập vượt khỏi phần tử cuối.
- `continue` bỏ qua giá không dương; ví dụ hiện có toàn giá dương. Nếu thêm `-5` vào mảng, vòng lặp vẫn an toàn và không tính dòng đó.
- `"member".equalsIgnoreCase(args[0])` gọi trên literal khác null. Khi không có đối số, `&&` ngắn mạch nên không đọc `args[0]`.
- `payable` tách phép tính và điều kiện đầu vào khỏi phần in ra; có thể thử độc lập với các giá trị tổng khác nhau.
- `switch` ở đây tạo `String` theo số tiền, trong khi vòng `for` và `if` là các câu lệnh điều khiển luồng.

## 11. Dự đoán đường chạy trước khi bấm Run

Xét:

~~~java
int x = 1;
if (x > 1) {
    x += 10;
} else {
    x += 2;
}
while (x < 5) {
    x++;
}
System.out.println(x);
~~~

Dùng bảng trace:

| Mốc | `x` | Lý do |
|---|---:|---|
| Sau khai báo | 1 | Initializer |
| Sau `if/else` | 3 | `x > 1` sai, chạy `else` |
| Sau vòng `while` lần 1 | 4 | `4 < 5` sẽ còn đúng khi kiểm tra tiếp |
| Sau vòng `while` lần 2 | 5 | `x < 5` trở thành sai |
| Khi in | 5 | Vòng lặp kết thúc |

Thói quen tốt: ghi **giá trị trước/sau** của biến, điều kiện đang xét và nhánh được chọn. Nếu không có tiến triển làm điều kiện lặp sai hoặc không có `break`, hãy kiểm tra nguy cơ vòng lặp vô hạn.

## 12. Lỗi thường gặp và cách phân loại

| Mã/sự cố | Triệu chứng | Nguyên nhân |
|---|---|---|
| `if (x = 1)` với `x` là `int` | Lỗi biên dịch | Kết quả gán kiểu `int`, không phải `boolean` |
| `int z = 7 / 2;` rồi mong `3.5` | Sai kết quả mong đợi | Chia nguyên trả `3` |
| `input.length()` khi `input == null` | `NullPointerException` | Gọi method qua tham chiếu null |
| `values[values.length]` | `ArrayIndexOutOfBoundsException` | Chỉ số cuối hợp lệ là `length - 1` khi mảng không rỗng |
| `while (n > 0)` không đổi `n` | Vòng lặp không kết thúc | Không có tiến triển hoặc nhánh thoát |
| Dùng `if (a && b)` nhưng `b` không được gọi | Hành vi đúng theo ngôn ngữ | Ngắn mạch khi `a=false` |
| Viết `total + price;` một mình | Lỗi biên dịch | Không phải expression statement hợp lệ |
| Truy cập biến khai báo trong một block ở bên ngoài | Lỗi biên dịch | Sai phạm vi biến |
| `String s = null; s.equals("x")` | `NullPointerException` | Không kiểm tra null |
| `int x = Integer.MAX_VALUE + 1;` | Số âm do tràn | Kết quả vượt phạm vi `int` |

Đọc lỗi theo **giai đoạn**: lỗi cú pháp/kiểu/phạm vi thường bị `javac` bắt; các exception liên quan dữ liệu thực hoặc chỉ số xuất hiện lúc chạy; vòng lặp vô hạn có thể không ném exception mà khiến chương trình treo lâu. Compile thành công không chứng minh mọi đường chạy đều đúng.

## 13. Bài tập kèm đáp án

### Bài 1 — xác định expression và statement

Trong đoạn `int n = 4; n += 3; System.out.println(n > 5);`, hãy chỉ ra hai biểu thức và ba câu lệnh.

**Đáp án:** biểu thức `4`, `n += 3`, `n > 5` đều hợp lệ (còn có biểu thức con); cả ba dòng kết thúc `;` là câu lệnh hoặc câu lệnh khai báo. `n > 5` có kiểu `boolean` và in ra `true`.

### Bài 2 — dự đoán kết quả

~~~java
int a = 8;
int b = 3;
System.out.println(a / b);
System.out.println((double) a / b);
~~~

**Đáp án:** `2` rồi `2.6666666666666665` (cách hiển thị thập phân thực tế theo giá trị `double`). Phép chia nguyên xảy ra khi cả hai toán hạng là `int`; cast trước phép chia đổi loại phép toán.

### Bài 3 — ngắn mạch bảo vệ dữ liệu

~~~java
String text = null;
boolean canRead = text != null && text.length() > 2;
System.out.println(canRead);
~~~

**Đáp án:** in `false`, không ném exception vì vế phải của `&&` không được đánh giá. Nếu dùng `&` boolean thay `&&`, vế phải sẽ bị đánh giá và có thể ném `NullPointerException`.

### Bài 4 — `continue` và `break`

~~~java
int sum = 0;
for (int i = 1; i <= 6; i++) {
    if (i == 2) continue;
    if (i == 5) break;
    sum += i;
}
System.out.println(sum);
~~~

**Đáp án:** `8` (`1 + 3 + 4`). Bỏ lượt 2; đến lượt 5 thì thoát vòng trước khi cộng.

### Bài 5 — sửa lỗi definite assignment

~~~java
boolean paid = false;
String result;
if (paid) {
    result = "OK";
}
// System.out.println(result);
~~~

**Yêu cầu:** cho phép in kết quả ở mọi đường chạy. **Đáp án:** thêm `else { result = "PENDING"; }` trước lệnh in; hoặc gán giá trị khởi đầu có chủ đích cho `result`.

### Bài 6 — mở rộng chương trình chính

Sửa `FlowWalkthrough` để thêm giá `0` và `-10` vào cuối mảng `prices`. Dự đoán `processed`, `total`, `amount`.

**Đáp án:** `processed=3`, `total=130`, `amount=130` nếu không có đối số; `amount=115` khi đối số là `member`. Hai phần tử không dương được `continue` bỏ qua trước khi cập nhật tổng/số món.

### Bài 7 — phân biệt lỗi biên dịch và runtime

Xét hai trường hợp: (A) `int count = "three";`; (B) `int divisor = 0; int value = 12 / divisor;`. Lỗi ở giai đoạn nào?

**Đáp án:** (A) `javac` từ chối vì kiểu không tương thích; (B) phép chia nguyên với divisor 0 ném `ArithmeticException` khi biểu thức được đánh giá lúc chạy.

## 14. Checklist tự đánh giá

- [ ] Giải thích được quan hệ **dữ liệu → biểu thức → câu lệnh → luồng thực thi** bằng một đoạn Java.
- [ ] Phân biệt `=` với `==`, kiểu primitive với kiểu tham chiếu, và `null` với một giá trị object hợp lệ.
- [ ] Xác định kiểu/kết quả của phép số học và biểu thức `boolean`; nhận diện chia nguyên, tràn số và sai số dấu phẩy động.
- [ ] Hiểu ưu tiên toán tử, đánh giá toán hạng và ngắn mạch `&&`/`||`.
- [ ] Biết một biểu thức bất kỳ không phải lúc nào cũng là một câu lệnh Java hợp lệ.
- [ ] Viết được `if`, `switch`, `for`, `while`, `break`, `continue` đúng ý.
- [ ] Dự đoán được khi nào `return` kết thúc method và `throw` thay đổi luồng chạy.
- [ ] Biên dịch và chạy `FlowWalkthrough.java`; kết quả khớp ví dụ.
- [ ] Lập bảng trace biến để giải thích được đầu ra và phát hiện vòng lặp sai.
- [ ] Phân loại được lỗi compile-time, runtime exception và lỗi logic dù chạy không báo lỗi.

**Kết luận:** dữ liệu là thứ chương trình xử lý; biểu thức tạo giá trị (đôi khi có tác dụng phụ); câu lệnh tổ chức hành động; cấu trúc điều khiển quyết định những hành động nào được thực hiện và theo thứ tự nào. Nắm được bốn lớp này giúp bạn đọc code, dự đoán đầu ra và kiểm tra lỗi Java có phương pháp, trước khi xây dựng chương trình lớn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-005">Kiểu dữ liệu tĩnh và những lỗi được phát hiện khi biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu dữ liệu tĩnh và những lỗi được phát hiện khi biên dịch

Một chương trình có thể sai ngay cả khi **chưa chạy**. Ví dụ, bạn muốn lưu số lượng sản phẩm trong một biến số nguyên nhưng lại gán cho biến đó chuỗi `"ba"`. Java sẽ không đợi chương trình gặp dữ liệu này lúc chạy: trình biên dịch `javac` phát hiện rằng hai kiểu không tương thích.

Đây là ý nghĩa quan trọng của **hệ kiểu tĩnh (static typing)**: kiểu của biểu thức và biến được xác định và kiểm tra theo các quy tắc của ngôn ngữ **trong quá trình biên dịch**. Điều này giúp loại sớm nhiều lỗi, nhưng không bảo đảm chương trình không có ngoại lệ khi chạy hoặc luôn cho kết quả nghiệp vụ đúng.

Sau bài này, bạn có thể nhận biết lỗi về kiểu, tự sửa những lỗi biên dịch Java thường gặp, chọn phép chuyển kiểu phù hợp và dự đoán được những tình huống mà kiểm tra kiểu tĩnh chưa thể bảo vệ.

## 1. Kiểu dữ liệu tĩnh nghĩa là gì?

Trong Java, một biến hoặc một biểu thức có **kiểu**. Kiểu cho trình biên dịch biết giá trị có thể được dùng trong những phép toán nào và phương thức nào có thể được gọi.

~~~java
int quantity = 3;
long unitPriceVnd = 120_000L;
boolean available = true;
String productCode = "JAVA-01";
~~~

Ý nghĩa của từng khai báo:

| Thành phần | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `quantity` | `int` | Số lượng nguyên trong phạm vi của `int` |
| `unitPriceVnd` | `long` | Giá tiền theo VND nguyên, có miền giá trị rộng hơn `int` |
| `available` | `boolean` | Chỉ có `true` hoặc `false` |
| `productCode` | `String` | Chuỗi ký tự biểu diễn mã hàng |

Ví dụ:

~~~java
int count = 3;
count = 5;         // Hợp lệ: giá trị mới vẫn là int.
// count = "nam";  // Lỗi biên dịch: String không thể gán cho int.
~~~

Giá trị biến `count` thay đổi từ 3 thành 5, nhưng **kiểu khai báo của biến không đổi**. Hệ kiểu tĩnh không có nghĩa giá trị bất biến.

Đừng nhầm **static typing** với từ khóa **`static`**. `static` dùng khi khai báo thành viên thuộc về lớp; nó không phải từ khóa làm cho một ngôn ngữ trở thành ngôn ngữ có kiểu tĩnh. Một biến instance thông thường vẫn chịu kiểm tra kiểu tĩnh.

### Các nhóm kiểu quan trọng

Java có hai nhóm mà người mới thường cần phân biệt:

- **Primitive:** `byte`, `short`, `int`, `long`, `float`, `double`, `char` và `boolean`. Những biến này lưu giá trị primitive.
- **Reference type:** ví dụ `String`, một lớp tự định nghĩa, interface hoặc kiểu mảng như `int[]`. Một biến kiểu tham chiếu lưu tham chiếu tới đối tượng hoặc có thể là `null` nếu ngữ cảnh cho phép.

~~~java
int age = 20;          // Primitive int
String name = "An";    // Kiểu tham chiếu String
int[] scores = {7, 8}; // Mảng là đối tượng
~~~

`String` không phải một kiểu primitive, dù rất thường xuyên được sử dụng. `null` cũng không phải một chuỗi rỗng; gọi phương thức qua biến tham chiếu đang bằng `null` có thể gây lỗi khi chạy.

## 2. Từ mã nguồn đến kiểm tra kiểu của `javac`

~~~mermaid
flowchart TD
    A["Tệp .java"] --> B["javac: phân tích cú pháp và ngữ nghĩa"]
    B --> C{"Có lỗi biên dịch?"}
    C -->|Có| D["Báo lỗi và yêu cầu sửa mã"]
    C -->|Không| E["Tạo bytecode .class"]
    E --> F["JVM chạy chương trình"]
    F --> G["Kết quả, ngoại lệ lúc chạy hoặc lỗi logic"]
~~~

Một số việc quan trọng mà trình biên dịch có thể kiểm tra:

1. Câu lệnh có đúng ngữ pháp Java không: dấu `;`, ngoặc, từ khóa, cấu trúc lớp.
2. Tên biến, lớp hoặc phương thức được dùng có được khai báo và có thể truy cập từ vị trí đó không.
3. Biểu thức có kiểu phù hợp với phép toán, lời gọi phương thức, phép gán và giá trị trả về không.
4. Biến cục bộ đã chắc chắn được gán giá trị trước khi đọc chưa (*definite assignment*).
5. Đường điều khiển có đáp ứng một số quy tắc như trả về giá trị cho phương thức không có kiểu `void`, xử lý checked exception khi bắt buộc hay không.

Nếu biên dịch thất bại, hãy nhìn vị trí lỗi, đọc loại lỗi và **sửa nguyên nhân trong mã**. Thông báo có thể khác đôi chút giữa phiên bản JDK; đừng phụ thuộc vào một chuỗi thông báo duy nhất.

Cần nhớ: `javac` **không chạy thử tất cả đầu vào** hay tự hiểu yêu cầu nghiệp vụ. Nó có thể chấp nhận một chương trình tính thuế sai công thức nếu các phép toán vẫn đúng kiểu.

## 3. Chương trình Java chạy được: dùng kiểu đúng để mô hình hóa dữ liệu

Hãy tạo tệp chính xác tên **`StaticTypesDemo.java`** và chép mã sau. Ví dụ dùng JDK 17 trở lên, không yêu cầu thư viện ngoài JDK.

~~~java
import java.util.Locale;

public class StaticTypesDemo {
    static long calculateAmount(int quantity, long unitPriceVnd) {
        if (quantity < 0 || unitPriceVnd < 0) {
            throw new IllegalArgumentException(
                    "So luong va don gia phai khong am");
        }
        return Math.multiplyExact((long) quantity, unitPriceVnd);
    }

    static String classifyScore(int score) {
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException(
                    "Diem phai tu 0 den 10");
        }
        return score >= 5 ? "Dat" : "Chua dat";
    }

    public static void main(String[] args) {
        int quantity = 3;
        long unitPriceVnd = 120_000L;
        String productCode = "JAVA-01";
        int score = 8;

        long totalVnd = calculateAmount(quantity, unitPriceVnd);
        double average = (7 + 8 + 9) / 3.0;
        boolean canBuy = score >= 5 && quantity > 0;

        System.out.println("Tong tien: " + totalVnd + " VND");
        System.out.println("Ket qua: " + classifyScore(score));
        System.out.printf(
                Locale.ROOT, "Diem TB: %.1f%n", average);
        System.out.println("Duoc mua: " + canBuy);
        System.out.println("Ma hang: " + productCode);
        System.out.println(
                "Chuyen long sang int: " + Math.toIntExact(42L));

        // Thử bỏ chú thích từng dòng sau để xem lỗi javac:
        // quantity = "ba";
        // boolean wrong = 1;
        // int narrowed = 3.5;
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 StaticTypesDemo.java
java StaticTypesDemo
~~~

Kết quả dự kiến:

~~~text
Tong tien: 360000 VND
Ket qua: Dat
Diem TB: 8.0
Duoc mua: true
Ma hang: JAVA-01
Chuyen long sang int: 42
~~~

Diễn giải theo dữ liệu:

- `quantity` có kiểu `int` và `unitPriceVnd` có kiểu `long`. Phương thức `calculateAmount` khai báo đầu vào rõ ràng và trả về `long`.
- `(long) quantity` chuyển số lượng sang `long` trước phép nhân. `Math.multiplyExact` giúp phát hiện nếu **kết quả kiểu long** bị tràn; phép nhân số nguyên thông thường không nhất thiết tự báo lỗi tràn.
- `classifyScore` trả `String`; cả hai nhánh của toán tử `?:` đều trả chuỗi.
- `average` có kiểu `double`. Viết `3.0` để phép chia ra kết quả chấm động, khác với chia hai số nguyên.
- `canBuy` là `boolean` được tạo từ các biểu thức so sánh và toán tử logic `&&`.
- `printf(Locale.ROOT, ...)` cố định dấu thập phân thành `.` để đầu ra ví dụ dễ kiểm chứng trên nhiều cấu hình vùng.

Đọc kiểu trả về là cách rất nhanh để dự đoán phương thức được phép dùng như thế nào:

~~~java
long amount = StaticTypesDemo.calculateAmount(2, 20_000L);
String result = StaticTypesDemo.classifyScore(7);
// boolean result = StaticTypesDemo.classifyScore(7); // Không biên dịch.
~~~

Đoạn cuối chỉ là minh họa khi được đặt trong một phương thức khác; dòng sai đã được chú thích.

## 4. Những lỗi kiểu dữ liệu `javac` phát hiện sớm

Mỗi ví dụ trong bảng dưới đây được cố ý viết sai. Hãy **chỉ thử một lỗi mỗi lần** để không bị nhiều thông báo che mất nguyên nhân.

| Dòng mã không hợp lệ | Vì sao không biên dịch? | Hướng sửa |
| --- | --- | --- |
| `int count = "3";` | `String` không tự chuyển thành `int` | `Integer.parseInt("3")` khi dữ liệu đến từ chuỗi |
| `String label = 3;` | Không có phép gán tự động từ `int` thành `String` | `String.valueOf(3)` hoặc `"3"` |
| `boolean active = 1;` | Java không xem số 1 là `true` | Dùng `true` hoặc một biểu thức điều kiện |
| `int amount = 3.5;` | Literal chấm động không thể thu hẹp tự động thành `int` | Chọn `double` hoặc làm tròn/chuyển kiểu có chủ đích |
| `char symbol = "A";` | `"A"` là `String`, còn `char` là một đơn vị mã UTF-16 | Dùng `'A'` khi muốn `char` |
| `int number = 3_000_000_000;` | Literal số nguyên không hậu tố vượt miền `int` | Dùng `long number = 3_000_000_000L;` |
| `unknownName++;` | Không có khai báo tên phù hợp trong phạm vi | Khai báo đúng tên/kiểm tra chính tả |
| `int x = Math.toIntExact("5");` | Tham số yêu cầu `long`, không phải `String` | Chuyển chuỗi thành số bằng API thích hợp trước |
| `return "text";` trong method khai báo trả `int` | Kiểu trả về không tương thích | Trả `int` hoặc đổi hợp đồng method |

Không phải lúc nào thông báo lỗi nằm chính xác ở dòng gây ra nguyên nhân. Một dấu ngoặc thiếu phía trước có thể làm `javac` báo lỗi ở nhiều dòng sau. Hãy sửa lỗi đầu tiên hợp lý, biên dịch lại rồi mới xem tiếp.

### Thử một lỗi thật bằng tệp ngắn

Tạo riêng `WrongType.java`:

~~~java
public class WrongType {
    public static void main(String[] args) {
        int quantity = "ba";
        System.out.println(quantity);
    }
}
~~~

Chạy:

~~~bash
javac WrongType.java
~~~

Biên dịch **thất bại** vì không thể chuyển `String` thành `int`. Không cần học thuộc từng từ của thông báo; trọng tâm là xác định vế nhận cần `int` và vế gán lại là `String`. Sửa:

~~~java
int quantity = Integer.parseInt("3");
~~~

Dòng sửa biên dịch được, nhưng `Integer.parseInt("ba")` vẫn ném `NumberFormatException` **khi chạy**. Như vậy, kiểu tĩnh ngăn việc gán sai loại dữ liệu nhưng không bảo đảm chuỗi đầu vào chứa số hợp lệ.

## 5. Chuyển kiểu: mở rộng, thu hẹp và mất dữ liệu

Java cho phép một số phép chuyển kiểu **mở rộng tự động** (*widening conversion*), nhưng nhiều phép chuyển thu hẹp đòi hỏi ép kiểu hoặc một phương thức kiểm tra.

~~~java
int small = 42;
long larger = small; // Hợp lệ: int -> long, giữ nguyên giá trị.

// int narrowed = larger; // Lỗi biên dịch: long -> int.
// int narrowed = (int) larger; // Có thể biên dịch nhưng có nguy cơ mất dữ liệu.

int checked = Math.toIntExact(larger); // 42; ném lỗi nếu ngoài miền int.
~~~

Khi ép kiểu rõ ràng, trình biên dịch biết bạn **chủ ý cho phép phép chuyển**, nhưng không chứng minh giá trị vẫn chính xác. Ví dụ:

~~~java
long big = 3_000_000_000L;
int unsafe = (int) big; // Biên dịch, nhưng giá trị int không còn là 3000000000.
// Math.toIntExact(big); // Ném ArithmeticException vì quá miền int.
~~~

### Hai phép chia trông gần giống nhau

~~~java
int a = 10;
int b = 4;

double first = a / b;          // 2.0: chia int trước, rồi đổi sang double.
double second = (double) a / b; // 2.5: chuyển a sang double trước khi chia.
~~~

Kiểu biểu thức quyết định phép chia được thực hiện theo quy tắc nào. Việc biến kết quả khai báo `double` không thể làm phần thập phân đã bị mất ở phép chia số nguyên tự xuất hiện trở lại.

Đặc biệt, hằng số kiểu `int` có thể được Java cho phép gán vào `byte`, `short` hoặc `char` khi compiler **biết giá trị nằm trong miền hợp lệ**, nhưng điều đó không có nghĩa mọi biến `int` đều thu hẹp tự động. Ví dụ:

~~~java
byte smallConstant = 100; // Hợp lệ: hằng int trong miền byte.
int generalNumber = 100;
// byte copy = generalNumber; // Không biên dịch: giá trị biến có thể thay đổi.
~~~

Không nên ép kiểu chỉ để “hết lỗi đỏ”. Trước khi ép, hãy hỏi **miền giá trị, độ chính xác và ý nghĩa nghiệp vụ** của dữ liệu.

## 6. Biến chưa được gán và từ khóa `final`

Với **biến cục bộ**, Java yêu cầu biến đã được **gán chắc chắn** trước khi đọc. Ví dụ:

~~~java
public class DefiniteAssignmentDemo {
    public static void main(String[] args) {
        int score;
        boolean hasExam = true;

        if (hasExam) {
            score = 8;
        }

        // System.out.println(score); // Không biên dịch:
        // nhánh hasExam == false có thể khiến score chưa được gán.

        int safeScore = hasExam ? 8 : 0;
        System.out.println(safeScore); // 8
    }
}
~~~

Một biến field được khởi tạo mặc định theo quy tắc Java, nhưng không vì thế mà biến cục bộ không được gán lại tự có giá trị 0. Đây là hai tình huống khác nhau.

`final` ngăn **gán lại biến** sau khi đã được gán theo quy tắc definite assignment:

~~~java
final int passMark = 5;
// passMark = 6; // Không biên dịch: không được gán lại.

final StringBuilder text = new StringBuilder("Java");
text.append(" core"); // Hợp lệ: nội dung đối tượng vẫn có thể đổi.
// text = new StringBuilder(); // Không biên dịch: gán lại tham chiếu final.
~~~

**`final` không tự biến đối tượng thành bất biến.** Với kiểu tham chiếu, nó bảo vệ *giá trị tham chiếu của biến*, không cấm mọi thay đổi bên trong đối tượng.

## 7. Kiểu của biến tham chiếu và khả năng gọi phương thức

Cùng một đối tượng có thể được giữ qua các biến có **kiểu khai báo khác nhau**. Trình biên dịch kiểm tra lời gọi dựa trên **kiểu của biểu thức ở vị trí gọi**, không chỉ dựa trên lớp runtime của đối tượng.

~~~java
Object value = "Hello";

// System.out.println(value.length()); // Không biên dịch:
// Object không công bố method length().

String text = (String) value;
System.out.println(text.length()); // 5
~~~

Dòng ép kiểu làm chương trình biên dịch, nhưng không đảm bảo luôn an toàn với mọi đối tượng:

~~~java
Object value = Integer.valueOf(42);
// String text = (String) value; // Biên dịch được nếu bỏ comment,
// nhưng khi chạy sẽ ném ClassCastException.
~~~

Trong một phương thức thật, nên tránh ép kiểu không có cơ sở. Nếu cần kiểm tra, dùng `instanceof` để xác định một đối tượng có kiểu tương thích trước khi sử dụng:

~~~java
Object item = "Java";
if (item instanceof String) {
    String text = (String) item;
    System.out.println(text.length()); // 4
}
~~~

Tính đa hình còn khiến **triển khai nào của một instance method được thực thi** phụ thuộc vào đối tượng runtime. Kiểm tra kiểu của lời gọi khi biên dịch và lựa chọn phần triển khai lúc chạy là hai lớp cơ chế khác nhau, không mâu thuẫn với nhau.

### Generic giúp bắt lỗi tập hợp sai kiểu

~~~java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("An");
// names.add(42); // Không biên dịch: danh sách khai báo nhận String.

String first = names.get(0); // Kiểu kết quả được kiểm tra là String.
~~~

Generics tăng khả năng kiểm tra tĩnh, nhưng Java sử dụng *type erasure* ở nhiều khía cạnh của việc thực thi generic. Dùng raw type, ép kiểu không an toàn hoặc dữ liệu ngoài chương trình có thể khiến lỗi xuất hiện khi chạy. Vì vậy, **generic không phải bằng chứng chương trình miễn nhiễm với mọi lỗi kiểu**.

## 8. `var` vẫn là kiểu tĩnh, không phải biến kiểu động

Từ Java 10, trong một số khai báo biến cục bộ, có thể để trình biên dịch **suy luận kiểu** từ biểu thức khởi tạo:

~~~java
var count = 3;         // Suy luận int.
var title = "Java";    // Suy luận String.

count = 5;             // Hợp lệ.
// count = "five";     // Không biên dịch: count đã được suy luận là int.
~~~

`var` không có nghĩa biến sẽ tự đổi kiểu qua từng phép gán. Nó cũng không thay thế được kiểu khai báo ở mọi nơi:

~~~java
// var withoutValue; // Không hợp lệ: thiếu giá trị khởi tạo.
// var empty = null;  // Không thể suy luận kiểu từ null.
// void test(var x) {} // Không dùng var như kiểu tham số method thường.
~~~

Dùng `var` khi kiểu đã hiển nhiên và mã dễ đọc hơn; dùng tên kiểu tường minh khi điều đó giúp người học hoặc người bảo trì nhìn rõ hợp đồng của dữ liệu.

## 9. Những lỗi không được hệ kiểu tĩnh loại bỏ hoàn toàn

So sánh ba tình huống:

| Loại vấn đề | Ví dụ | Java phát hiện khi nào? |
| --- | --- | --- |
| Sai kiểu hoặc cú pháp | `int score = "8";` | Khi biên dịch |
| Ngoại lệ lúc chạy | `10 / zero` với `zero = 0` ở runtime | Khi chạy đến phép toán |
| Sai logic | Dùng `>` thay vì `>=` cho ngưỡng đạt | Có thể chạy bình thường; cần kiểm thử theo yêu cầu |

Ví dụ những đoạn **có thể biên dịch** nhưng vẫn gặp vấn đề:

~~~java
int zero = 0;
// System.out.println(10 / zero); // Ném ArithmeticException khi chạy.

int[] values = {2, 4};
// System.out.println(values[2]); // Ném ArrayIndexOutOfBoundsException.

String message = null;
// System.out.println(message.length()); // Ném NullPointerException.

int tooLarge = Integer.MAX_VALUE;
tooLarge++;
System.out.println(tooLarge); // -2147483648: int tràn và quay vòng.
~~~

Ở ví dụ cuối, Java không tự ném lỗi khi tăng `int` thông thường vượt miền. Khi cần phát hiện tràn trong phép cộng số nguyên, có thể dùng `Math.addExact(...)` hoặc một phương pháp kiểm tra miền giá trị phù hợp.

### Checked exception cũng được kiểm tra lúc biên dịch

Một số API có thể khai báo **checked exception**. Java yêu cầu caller **bắt hoặc khai báo tiếp** các loại checked exception cần xử lý. Ví dụ:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ReadTextDemo {
    static String readText(Path path) throws IOException {
        return Files.readString(path);
    }
}
~~~

Ở đây, chữ ký `throws IOException` thừa nhận thao tác đọc có thể thất bại khi chạy. Trình biên dịch giúp kiểm tra sự hiện diện của cơ chế **bắt hoặc khai báo**, nhưng không dự đoán tệp có tồn tại, quyền đọc có hợp lệ hay ổ đĩa có gặp lỗi hay không.

Ngược lại, các lỗi runtime phổ biến như `NullPointerException` thường là *unchecked exception*; Java không bắt buộc method phải ghi `throws NullPointerException` hoặc caller phải bắt nó.

## 10. Cách đọc và sửa thông báo lỗi `javac`

Khi thấy thông báo lỗi, hãy làm theo quy trình:

1. **Xác định tệp và dòng lỗi.** Đọc biểu thức ngay tại dòng đó và vài dòng trước nếu cần.
2. **Tìm kiểu được yêu cầu và kiểu đang có.** Ví dụ vế trái cần `int` nhưng biểu thức bên phải là `String`.
3. **Xem dữ liệu có đúng ý nghĩa không.** Số lượng nên là số, mã hàng thường là chuỗi; đừng ép kiểu sai bản chất.
4. **Chọn cách sửa an toàn:** sửa khai báo, chuyển chuỗi bằng API có kiểm tra, bổ sung gán giá trị, hoặc thay lời gọi phương thức đúng.
5. **Biên dịch lại** sau mỗi nhóm sửa nhỏ, sau đó **chạy và kiểm thử** vì hết lỗi compile chưa đồng nghĩa đúng yêu cầu.

Một số dạng thông báo thường gặp:

| Dấu hiệu | Hướng điều tra |
| --- | --- |
| `incompatible types` | Sai kiểu khi gán, trả về hoặc truyền tham số |
| `cannot find symbol` | Sai tên, thiếu khai báo hoặc thiếu import |
| `possible lossy conversion` | Chuyển thu hẹp có nguy cơ mất dữ liệu |
| `variable ... might not have been initialized` | Biến cục bộ chưa được gán chắc chắn |
| `method ... cannot be applied to given types` | Sai số lượng hoặc kiểu đối số |
| `unreported exception ...` | Checked exception chưa bắt/khai báo |
| `';' expected` | Có thể thiếu dấu kết thúc câu lệnh hoặc cú pháp trước đó sai |

Đừng sửa lỗi bằng cách đổi mọi thứ sang `String` hoặc chèn ép kiểu hàng loạt. Như vậy có thể làm chương trình biên dịch được nhưng mất kiểm soát dữ liệu và vẫn sai khi chạy.

## 11. Các trường hợp biên đáng kiểm tra

Bảng sau nhấn mạnh rằng kiểm tra kiểu và kiểm tra **giá trị hợp lệ** là hai việc bổ sung cho nhau.

| Trường hợp | `javac` có chặn không? | Điều gì cần xảy ra? |
| --- | --- | --- |
| `int age = "20";` | Có | Sửa kiểu/giao tiếp chuyển chuỗi |
| `int age = -1;` | Không | Nếu tuổi không được âm, tự kiểm tra quy tắc |
| `Long.parseLong("abc")` | Không | Khi chạy có thể ném `NumberFormatException` |
| `int x = Integer.MAX_VALUE; x++;` | Không | Tràn theo ngữ nghĩa `int`; cần kiểm tra nếu không được phép |
| `long big = 3_000_000_000;` | Có | Literal cần hậu tố `L` |
| `byte x = 100;` | Không | Hằng nằm trong giới hạn `byte` |
| `byte x = 200;` | Có | Hằng vượt miền `byte` |
| `Object x = "Hi"; x.length();` | Có | Kiểu `Object` không công bố `length()` |
| `Object x = 42; (String) x;` | Không đối với cast hợp lệ về mặt kiểu | Có thể `ClassCastException` khi chạy |
| `String s = null; s.length();` | Thường không | Có thể `NullPointerException` khi chạy |
| `double x = 10 / 4;` | Không | Kết quả là `2.0`, cần đổi cách chia nếu muốn `2.5` |
| `int[] a = {1}; a[1]` | Không | Có thể `ArrayIndexOutOfBoundsException` |

Lưu ý về dòng ép kiểu ở bảng: dạng `(String) x` là một **biểu thức**; trong chương trình Java cần đặt nó vào một câu lệnh hợp lệ, ví dụ `String text = (String) x;`. Mục đích ở đây là minh họa khả năng kiểm tra kiểu, không phải tệp mã độc lập.

## 12. Bài tập tự luyện và đáp án

**Bài 1 — Tìm lỗi biên dịch.** Đọc đoạn sau và chỉ ra vì sao từng dòng bị chú thích sẽ sai nếu bỏ chú thích:

~~~java
int tickets = 2;
// tickets = "two";
// boolean paid = 1;
// char grade = "A";
~~~

*Đáp án:* `tickets` đã có kiểu `int`, chuỗi không gán được trực tiếp; `boolean` không dùng 1 thay cho `true`; `char` cần một literal `'A'` thay vì `String` `"A"`.

**Bài 2 — Sửa phép chia.**

~~~java
int sum = 7 + 8 + 9;
double average = sum / 3;
System.out.println(average);
~~~

*Dự đoán:* In `8.0` vì chia nguyên cho ra 8. Để diễn đạt đúng ý định nếu tổng không chia hết, viết `(double) sum / 3` hoặc `sum / 3.0`. Với tổng này, cả hai cách vẫn cho 8.0; thử `sum = 25` để thấy khác biệt (chia nguyên thành 8, chia chấm động thành khoảng 8.333...).

**Bài 3 — Lỗi runtime hay compile?** Với `Object anything = "Java";`, vì sao `anything.length()` không được biên dịch, nhưng `((String) anything).length()` lại hợp lệ? Nếu thay đối tượng thực thành `Integer.valueOf(7)` thì chuyện gì xảy ra?

*Đáp án:* Kiểu khai báo `Object` không có `length()`; cast sang `String` mở quyền gọi ở mức biên dịch. Nếu runtime là `Integer`, ép sang `String` gây `ClassCastException`.

**Bài 4 — Giá trị lớn.** Tại sao `long money = 3_000_000_000;` không biên dịch? Viết đúng và giải thích vì sao chỉ đổi kiểu biến ở vế trái chưa đủ.

*Đáp án:* Literal không hậu tố được xử lý theo quy tắc literal `int` và quá miền; dùng `3_000_000_000L`, hậu tố `L` cho literal kiểu `long`.

**Bài 5 — Thiết kế ca kiểm thử.** Viết phương thức `static int positiveCount(String raw)` nhận chuỗi số nguyên và từ chối số âm. Phân biệt các lỗi cần xử lý cho ba input `"5"`, `"-2"` và `"abc"`.

*Gợi ý một lời giải:*

~~~java
static int positiveCount(String raw) {
    int count = Integer.parseInt(raw);
    if (count < 0) {
        throw new IllegalArgumentException("Khong duoc am");
    }
    return count;
}
~~~

Kết quả: `"5"` trả 5; `"-2"` ném `IllegalArgumentException` theo quy tắc; `"abc"` ném `NumberFormatException` khi parse (là lớp con của `IllegalArgumentException`). Dù mã hợp lệ về kiểu, phải **kiểm tra dữ liệu** lúc chạy.

**Bài 6 — Phân biệt `final` và kiểu tĩnh.** Cho `final StringBuilder sb = new StringBuilder("A");`. Vì sao `sb.append("B")` chạy được nhưng `sb = new StringBuilder("C")` lại không biên dịch?

*Đáp án:* `final` cấm gán lại biến sau khi đã gán; đối tượng `StringBuilder` vẫn mutable. Kiểu của `sb` không đổi trong cả hai trường hợp.

## 13. Checklist trước khi nộp một chương trình Java

- [ ] Tệp `.java` và lớp `public` khớp tên.
- [ ] Tên biến có ý nghĩa và kiểu phù hợp với đơn vị, miền giá trị.
- [ ] Không gán nhầm `String` cho kiểu số hoặc `int` cho `boolean`.
- [ ] Hiểu chuyển kiểu mở rộng/thu hẹp và nguy cơ mất dữ liệu.
- [ ] Biến cục bộ đã được gán chắc chắn trước khi dùng.
- [ ] Tham số truyền vào đúng kiểu và số lượng phương thức yêu cầu.
- [ ] Không dùng cast chỉ để né lỗi kiểu; có kiểm tra khi cần.
- [ ] Giá trị đầu vào được validate theo quy tắc nghiệp vụ.
- [ ] Đã biên dịch bằng `javac`, chạy bằng `java` và thử trường hợp biên.
- [ ] Phân biệt **hết lỗi biên dịch** với **không còn lỗi khi chạy và kết quả đúng**.

## Ghi nhớ

**Kiểu dữ liệu tĩnh là một hàng rào phát hiện sớm**, giúp Java từ chối nhiều cách dùng dữ liệu sai trước khi chạy. Khi compiler báo lỗi, hãy hỏi “biểu thức này có kiểu gì?” và “vị trí sử dụng đòi hỏi kiểu gì?”. Nhưng khi compiler không báo lỗi, bạn vẫn phải hỏi “giá trị có hợp lệ không?” và “thuật toán có đúng yêu cầu không?”. Hiểu cả ba lớp — **kiểm tra kiểu, kiểm tra dữ liệu, kiểm tra logic** — là nền tảng để viết Java đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-006">Từ đầu vào tới xử lý và đầu ra của một chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Từ đầu vào tới xử lý và đầu ra của một chương trình Java

> **Java core — language-basics / Tổng quan**  
> **Mục tiêu:** Sau bài này, bạn giải thích được đường đi **Input → Processing → Output (IPO)**, biết chương trình Java nhận dữ liệu ở đâu, kiểm tra và biến đổi thế nào, in kết quả hoặc báo lỗi ra sao. Bạn cũng có thể tự biên dịch/chạy một ứng dụng console đơn giản, thử dữ liệu bình thường và các trường hợp biên.

## 1. Một chương trình làm gì với dữ liệu?

Hãy tưởng tượng người dùng muốn tính tiền mua bút. Họ nhập tên món hàng, đơn giá và số lượng. Chương trình kiểm tra dữ liệu, tính thành tiền, rồi hiển thị kết quả.

| Giai đoạn | Câu hỏi cần trả lời | Ví dụ mua hàng |
| --- | --- | --- |
| **Input — Đầu vào** | Dữ liệu được lấy từ đâu? Hình dạng ban đầu là gì? | Bàn phím cung cấp `"Bút bi"`, `"12000"`, `"3"` dưới dạng những dòng ký tự |
| **Processing — Xử lý** | Dữ liệu có hợp lệ không? Cần tính hay quyết định điều gì? | Chuyển giá thành `long`, số lượng thành `int`, kiểm tra giá không âm, số lượng dương; tính `12000 × 3` |
| **Output — Đầu ra** | Kết quả hoặc lỗi được gửi tới đâu? | In `Tổng tiền: 36000 VND` hoặc lý do đầu vào không hợp lệ |

Đừng nhầm **đầu vào** với “dữ liệu đã được xử lý”. Người dùng gõ `12000` trên bàn phím; chương trình trước tiên nhận được **chuỗi ký tự** `"12000"`, sau đó mới chuyển sang số `12000L`. Việc nhập thành công **không** đồng nghĩa dữ liệu hợp lệ.

~~~mermaid
flowchart TD
    A["Người dùng / nguồn dữ liệu"] --> B["Đọc input: dòng ký tự"]
    B --> C["Phân tích cú pháp + kiểm tra"]
    C --> D{"Hợp lệ?"}
    D -->|Không| E["Thông báo lỗi qua stderr"]
    D -->|Có| F["Xử lý nghiệp vụ: tính toán"]
    F --> G{"Phép toán hợp lệ?"}
    G -->|Không| E
    G -->|Có| H["In kết quả qua stdout"]
~~~

Quy trình IPO không chỉ áp dụng cho console. Với một ứng dụng web, đầu vào có thể là HTTP request, xử lý là kiểm tra và tính toán, đầu ra là HTTP response. Với ứng dụng đọc tệp, đầu vào là nội dung tệp và đầu ra có thể là tệp kết quả. **Ba bước tư duy không thay đổi**, còn cách kết nối nguồn/đích thì khác.

## 2. Đầu vào trong Java: dữ liệu đến từ đâu?

Một chương trình Java thông thường có nhiều nguồn đầu vào:

| Nguồn | Java API/cách dùng điển hình | Khi nào phù hợp? |
| --- | --- | --- |
| **Đối số dòng lệnh** | Mảng `String[] args` trong `main` | Tham số được truyền **khi khởi động** chương trình |
| **Bàn phím/standard input** | `System.in`, thường được bọc bằng `Scanner` hoặc `BufferedReader` | Hội thoại nhập liệu tại terminal; nhận dữ liệu đã chuyển hướng từ tệp/pipe |
| **Tệp** | `Files.readString`, `Files.newBufferedReader`, v.v. | Đọc dữ liệu đã được lưu |
| **Lời gọi từ chương trình khác** | Tham số phương thức, object, API/network | Dữ liệu đến từ các thành phần khác của hệ thống |

Ví dụ với đối số dòng lệnh:

~~~java
public class ArgumentsDemo {
    public static void main(String[] args) {
        System.out.println("So doi so: " + args.length);
        if (args.length > 0) {
            System.out.println("Doi so dau: " + args[0]);
        }
    }
}
~~~

Biên dịch và chạy (Java 11+):

~~~bash
javac ArgumentsDemo.java
java ArgumentsDemo xin-chao
~~~

Kết quả:

~~~text
So doi so: 1
Doi so dau: xin-chao
~~~

Nếu chạy `java ArgumentsDemo`, `args.length` bằng `0`. Cố truy cập `args[0]` khi mảng rỗng sẽ gây `ArrayIndexOutOfBoundsException`; vì vậy mã trên kiểm tra độ dài trước.

**Phân biệt:** `args` là dữ liệu đã truyền vào lúc khởi động. `System.in` là luồng để đọc **trong thời gian chương trình đang chạy**; cả hai đều có thể cung cấp chuỗi nhưng vòng đời và cách sử dụng khác nhau.

## 3. Standard input, standard output và standard error

Một ứng dụng console thường dùng ba luồng chuẩn:

| Luồng | Trong Java | Mục đích |
| --- | --- | --- |
| **stdin** (*standard input*) | `System.in` | Nhận các byte đầu vào, thường từ terminal |
| **stdout** (*standard output*) | `System.out` | In kết quả thông thường |
| **stderr** (*standard error*) | `System.err` | In thông báo lỗi/chẩn đoán |

Ví dụ:

~~~java
System.out.println("Tong tien: 36000 VND");
System.err.println("Loi: so luong phai lon hon 0");
~~~

Tại terminal, **cả hai dòng có thể cùng xuất hiện trên màn hình**, nên rất dễ tưởng stdout và stderr là một. Nhưng khi chuyển hướng luồng, chúng có thể được xử lý riêng. Điều này hữu ích khi một chương trình được tự động hóa: chương trình khác có thể đọc kết quả từ stdout mà không nhầm với lời báo lỗi.

`System.in` là luồng **byte**. Khi cần đọc văn bản, Java thường bọc nó trong một `InputStreamReader` để giải mã byte thành ký tự, rồi `BufferedReader` để đọc từng dòng:

~~~java
BufferedReader input = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));
String line = input.readLine();
~~~

Cần import `java.io.BufferedReader`, `java.io.InputStreamReader` và `java.nio.charset.StandardCharsets`. `readLine()` trả một `String`, bỏ ký tự kết thúc dòng; **trả `null` khi đã hết dữ liệu**, chứ không trả một chuỗi rỗng. Đây là khác biệt quan trọng khi đầu vào đến từ tệp hoặc pipe.

`Scanner` là lựa chọn đơn giản cho nhiều bài học nhập số, nhưng `Scanner.nextInt()` phụ thuộc cách tách token và locale, dễ phát sinh bất ngờ khi dùng xen kẽ `nextInt()` với `nextLine()`. Ví dụ chính của bài dùng `BufferedReader.readLine()`, sau đó chủ động chuyển kiểu và kiểm tra để thấy rõ từng bước IPO.

## 4. Một chương trình chạy được từ đầu đến cuối

Bài toán: người dùng nhập **tên một mặt hàng**, **đơn giá VND** và **số lượng**. Chương trình tính tổng tiền của đúng mặt hàng đó.

**Hợp đồng đầu vào:**

- Tên món hàng không được trống hoặc chỉ chứa khoảng trắng.
- Đơn giá là **số nguyên từ 0 tới `Long.MAX_VALUE`**; giá 0 được phép, ví dụ hàng tặng.
- Số lượng là **số nguyên dương từ 1 tới `Integer.MAX_VALUE`**.
- Tổng tiền `đơn giá × số lượng` phải nằm trong phạm vi `long`.
- Chương trình đọc chính xác **ba dòng** từ stdin theo đúng thứ tự.
- Khi hợp lệ, chương trình in tên hàng và tổng tiền ra stdout. Khi lỗi, in thông báo ra stderr và thoát bằng trạng thái khác 0.

Ví dụ chọn `long` cho tiền nguyên VND, thay vì `float`/`double` để tránh sai số nhị phân trong ví dụ tiền tệ. Một phần mềm tài chính thực có thể dùng `BigDecimal` và cần quy định tiền tệ/đơn vị/làm tròn chi tiết hơn; bài này chỉ minh họa luồng xử lý và một hợp đồng số nguyên rõ ràng.

### 4.1. Mã nguồn: PurchaseFlowDemo.java

Tạo tệp **PurchaseFlowDemo.java** và chép toàn bộ mã sau. Chương trình dùng Java 11 trở lên, không cần thư viện ngoài JDK.

~~~java
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class PurchaseFlowDemo {

    // Sau khi kiểm tra, chương trình giữ giá trị đã có đúng kiểu.
    static final class Purchase {
        final String productName;
        final long unitPriceVnd;
        final int quantity;

        Purchase(String productName, long unitPriceVnd, int quantity) {
            this.productName = productName;
            this.unitPriceVnd = unitPriceVnd;
            this.quantity = quantity;
        }
    }

    // INPUT: đọc đúng một dòng văn bản, không giả định luôn có dữ liệu.
    private static String requestLine(
            BufferedReader reader, String prompt) throws IOException {

        System.out.print(prompt);
        System.out.flush();

        String line = reader.readLine();
        if (line == null) {
            throw new EOFException("Thieu du lieu tai: " + prompt);
        }
        return line.strip();
    }

    // INPUT + VALIDATION: đổi String thành kiểu dữ liệu phù hợp.
    private static Purchase readPurchase(
            BufferedReader reader) throws IOException {

        String name = requestLine(reader, "Ten san pham: ");
        String rawPrice = requestLine(reader, "Don gia (VND): ");
        String rawQuantity = requestLine(reader, "So luong: ");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Ten san pham khong duoc rong");
        }

        long price;
        int quantity;
        try {
            price = Long.parseLong(rawPrice);
            quantity = Integer.parseInt(rawQuantity);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Don gia va so luong phai la so nguyen hop le", error);
        }

        if (price < 0) {
            throw new IllegalArgumentException("Don gia khong duoc am");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("So luong phai lon hon 0");
        }
        return new Purchase(name, price, quantity);
    }

    // PROCESSING: chỉ tính toán, không đọc từ bàn phím hay in ra màn hình.
    private static long calculateTotal(Purchase purchase) {
        return Math.multiplyExact(
                purchase.unitPriceVnd, (long) purchase.quantity);
    }

    // OUTPUT: trình bày kết quả đã xử lý.
    private static void printReceipt(Purchase purchase, long total) {
        System.out.println("Mat hang : " + purchase.productName);
        System.out.println("Tong tien: " + total + " VND");
    }

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        try {
            Purchase purchase = readPurchase(reader);
            long total = calculateTotal(purchase);
            printReceipt(purchase, total);
        } catch (EOFException error) {
            System.err.println("Loi dau vao: " + error.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException | ArithmeticException error) {
            System.err.println("Loi du lieu/tinh toan: " + error.getMessage());
            System.exit(1);
        } catch (IOException error) {
            System.err.println("Loi doc stdin: " + error.getMessage());
            System.exit(1);
        }
    }
}
~~~

Có bốn nhóm trách nhiệm dễ nhìn thấy:

| Phương thức | Giai đoạn | Điều nó làm | Điều nó **không** làm |
| --- | --- | --- | --- |
| `requestLine` | Input | Nhắc nhập, đọc một dòng, phát hiện hết dữ liệu | Không nhân giá và số lượng |
| `readPurchase` | Input / Validation | Gom ba dòng, chuyển sang `long`/`int`, từ chối đầu vào sai | Không in hóa đơn |
| `calculateTotal` | Processing | Tính tổng với kiểm tra tràn số | Không tự đọc từ `System.in` |
| `printReceipt` | Output | Định dạng và in kết quả hợp lệ | Không tự sửa quy tắc tính tiền |

Cách tách này giúp thấy **dòng dữ liệu** thay vì chỉ nhìn một khối code có lẫn đọc, tính, in. Với chương trình nhỏ hơn, bạn hoàn toàn có thể viết tất cả vào `main`; việc tách method ở đây phục vụ mục đích giáo dục, **không phải quy tắc buộc mọi chương trình phải có bốn phương thức**.

### 4.2. Biên dịch bằng javac, chạy bằng java

Mở terminal tại thư mục chứa tệp `PurchaseFlowDemo.java`:

~~~bash
javac -encoding UTF-8 PurchaseFlowDemo.java
java PurchaseFlowDemo
~~~

Khi chương trình hỏi, lần lượt nhập:

~~~text
Ten san pham: But bi
Don gia (VND): 12000
So luong: 3
Mat hang : But bi
Tong tien: 36000 VND
~~~

Ba dòng bắt đầu bằng lời nhắc là **phần tương tác khi bạn gõ và nhấn Enter**; hai dòng `Mat hang` và `Tong tien` là kết quả được chương trình in. Compiler tạo `PurchaseFlowDemo.class`, còn lệnh `java PurchaseFlowDemo` khởi động lớp đã biên dịch. **Không** thêm đuôi `.class` khi truyền tên lớp cho `java`.

Thử nhập `0` cho đơn giá và `5` cho số lượng, kết quả sẽ là `0 VND`. Thử nhập khoảng trắng đầu/cuối tên món hàng: `strip()` cắt khoảng trắng hai đầu. Cách hiển thị ký tự có dấu tại terminal phụ thuộc cấu hình mã hóa/console; trong mã có khai báo UTF-8 cho **đầu vào**.

## 5. Dữ liệu chảy qua chương trình như thế nào?

Với đầu vào:

~~~text
But bi
12000
3
~~~

Bảng sau mô phỏng từng trạng thái:

| Bước | Giá trị trong chương trình | Kiểu dữ liệu | Ý nghĩa |
| --- | --- | --- | --- |
| Đọc tên | `"But bi"` | `String` | Chưa kiểm tra tên rỗng |
| Đọc giá | `"12000"` | `String` | Vẫn là văn bản |
| Đọc số lượng | `"3"` | `String` | Vẫn là văn bản |
| Chuyển và kiểm tra giá | `12000L` | `long` | Số nguyên không âm |
| Chuyển và kiểm tra lượng | `3` | `int` | Số nguyên dương |
| Tính toán | `36000L` | `long` | `12000 × 3` |
| In kết quả | `"Tong tien: 36000 VND"` | Chuỗi xuất ra stdout | Người dùng thấy kết quả |

Dữ liệu được **chuyển từ biểu diễn bên ngoài** (chuỗi) thành **giá trị Java có kiểu**, sau đó được kiểm tra theo quy tắc nghiệp vụ. Không có bước xử lý hợp lệ nếu số lượng là `"ba"`: gọi `Integer.parseInt("ba")` sẽ ném `NumberFormatException`; trong ví dụ, lỗi được biến thành một thông báo dễ hiểu hơn cho người dùng.

Phân biệt hai kiểu câu hỏi:

- **Hợp lệ về cú pháp?** `"1000"` parse được thành số; `"1,000"` hoặc `"1 000"` không parse trực tiếp bằng `Long.parseLong`.
- **Hợp lệ về nghiệp vụ?** `"-100"` parse được thành số, nhưng **giá âm** bị từ chối; `"0"` parse được thành số, nhưng **số lượng 0** không hợp lệ theo hợp đồng.

Mã tính tiền dùng `Math.multiplyExact` thay cho phép nhân thông thường để **không trả một số đã tràn** nếu giá × lượng vượt phạm vi `long`.

## 6. Edge cases: đầu vào hỏng và cách chương trình phản hồi

| Dữ liệu nhập | Điều xảy ra | Vì sao? |
| --- | --- | --- |
| Tên là chuỗi rỗng hoặc toàn khoảng trắng | In lỗi tên rỗng | `strip()` và `isEmpty()` phát hiện tên không có nội dung |
| Giá là `-1` | In lỗi giá âm | Parse thành công nhưng sai nghiệp vụ |
| Giá là `0`, số lượng là `2` | In tổng `0 VND` | Giá 0 được phép theo hợp đồng |
| Số lượng là `0` | In lỗi số lượng không dương | Quy tắc yêu cầu ít nhất một món |
| Số lượng là `2.5` hoặc `hai` | In lỗi số nguyên | `Integer.parseInt` không nhận số thực/chữ |
| Giá là `1_000` | In lỗi số nguyên | Dấu gạch dưới hợp lệ trong **literal source Java**, không phải cú pháp mà `parseLong` nhận từ người dùng |
| Giá vượt `Long.MAX_VALUE` | In lỗi số nguyên | Số không biểu diễn được bằng `long` |
| Giá = `Long.MAX_VALUE`, lượng = `2` | In lỗi tính toán | `Math.multiplyExact` phát hiện tràn `long` |
| Chỉ đưa hai dòng rồi kết thúc stdin | In lỗi thiếu dữ liệu | `readLine()` trả `null` tại EOF |
| Có dòng trống giữa hai giá trị | Dòng trống được đọc như input thật | `readLine()` không tự bỏ qua dòng trống |
| Có thêm dòng thứ tư | Chương trình bỏ qua dòng thứ tư | Hợp đồng ví dụ chỉ đọc ba dòng |

Một lỗi không có nghĩa là “chương trình in tổng tiền bằng 0”. **Không nên tự chuyển input sai thành 0 để code khỏi báo lỗi**, vì khi đó người dùng không phân biệt được dữ liệu sai với giá trị hợp lệ. Chương trình dừng với mã trạng thái 1 và ghi lỗi lên stderr, thay vì tạo một hóa đơn trông có vẻ thành công.

Một điểm nhỏ nhưng có ý nghĩa: ở ví dụ này, `readPurchase` **đọc hết ba dòng rồi mới kiểm tra tên**. Nếu tên rỗng, người dùng vẫn được hỏi giá và số lượng trước khi thấy thông báo lỗi. Đó là lựa chọn thứ tự xử lý của ví dụ, **không** phải hành vi mặc định Java. Bạn có thể cải thiện UX bằng kiểm tra ngay sau khi đọc từng trường, nhưng hãy nhận biết sự khác biệt về thứ tự lời nhắc.

## 7. Thử tự động và chuyển hướng luồng

Vì chương trình đọc từ **stdin**, bạn không bắt buộc phải gõ tay. Trong shell có hỗ trợ pipe, một chương trình khác có thể cấp dữ liệu cho nó.

**macOS/Linux — terminal shell:**

~~~bash
printf 'But bi\n12000\n3\n' | java PurchaseFlowDemo
~~~

**Windows PowerShell:**

~~~powershell
@('But bi', '12000', '3') | java PurchaseFlowDemo
~~~

Đầu ra có cùng kết quả tính tiền; khi stdin được pipe từ lệnh khác, các lời nhắc có thể hiện liền nhau vì bản thân dữ liệu nhập **không nhất thiết được terminal echo lại**. Đừng so nguyên hình thức lời nhắc tương tác với transcript gõ tay.

Để nhận biết stderr khác stdout (ví dụ trên macOS/Linux hoặc shell hỗ trợ cú pháp tương đương):

~~~bash
printf 'But bi\n12000\n0\n' | java PurchaseFlowDemo > result.txt 2> errors.txt
~~~

- `> result.txt` chuyển **stdout** sang tệp `result.txt`.
- `2> errors.txt` chuyển **stderr** sang `errors.txt`.
- Trong trường hợp sai số lượng, `errors.txt` có thông báo lỗi; `result.txt` có thể chứa các lời nhắc nhập mà chương trình đã in trước khi lỗi.
- Mã thoát khác 0 báo chương trình không hoàn tất thành công. Cách kiểm tra mã thoát khác nhau giữa shell, ví dụ `echo $?` trong Bash sau lệnh.

**Chú ý:** Lệnh chuyển hướng trên tác động tới luồng của **tiến trình**; `System.out` và `System.err` vẫn là các API Java. Java không cần biết dữ liệu đang đến từ bàn phím, tệp hay pipe để đọc từng dòng bằng `BufferedReader`.

## 8. Gỡ lỗi bằng cách đi ngược dòng dữ liệu

Khi kết quả không đúng, lần theo đúng từng bước:

1. **Có nhận được input không?** `readLine()` trả `null` hay chuỗi? Có đang chờ Enter không?
2. **Chuỗi đọc được là gì?** Có khoảng trắng đầu/cuối, dòng rỗng, dấu phân cách lạ không? Tránh in mật khẩu/thông tin nhạy cảm khi debug.
3. **Phân tích cú pháp ra sao?** `parseInt/parseLong` chấp nhận chuỗi đó không? Kiểu số có đủ phạm vi không?
4. **Đã kiểm tra nghiệp vụ chưa?** Giá âm, số lượng bằng 0 hoặc vượt ngưỡng hợp lệ có bị chặn không?
5. **Phép tính có an toàn không?** Có phép cộng/nhân vượt `int`/`long` không? Nếu cần, dùng các phương thức `Math.*Exact` tương ứng.
6. **Đã xuất kết quả nào?** Đầu ra thành công có chỉ xuất khi mọi bước trước hợp lệ không? Thông báo lỗi có đi vào stderr không?

Một số lỗi hay gặp:

| Triệu chứng | Khả năng | Cách xem xét |
| --- | --- | --- |
| Chương trình “đứng yên” sau lời nhắc | Đang chờ dữ liệu từ stdin | Nhập một dòng và Enter hoặc cung cấp đủ input qua pipe |
| `NumberFormatException` | Chuỗi không phải số nguyên được hỗ trợ | Kiểm tra ký tự lạ, dấu phẩy, số quá lớn |
| `NullPointerException` khi gọi method trên `readLine()` | Không xử lý EOF trước | Kiểm tra `null` như `requestLine` |
| Giá đúng nhưng tổng âm bất thường | Tràn kiểu số với phép nhân thường | Dùng `Math.multiplyExact` và xử lý lỗi |
| Máy hiển thị chữ tiếng Việt sai dấu | Mã hóa console không khớp | Kiểm tra terminal, charset nguồn/input/output |
| Không tìm được main class | Sai thư mục chạy/classpath/tên lớp | Kiểm tra biên dịch thành công, chạy `java PurchaseFlowDemo` |

Đừng mặc định mọi lỗi nhập liệu đều là `IOException`: chuyển số sai thường gây `NumberFormatException` (một `IllegalArgumentException`), còn đọc nguồn lỗi thật mới là trường hợp `IOException`. Việc phân biệt giúp thông báo lỗi và cách khắc phục chính xác hơn.

## 9. Từ ví dụ console đến thiết kế dễ kiểm thử

Đầu vào/đầu ra phụ thuộc **môi trường**; phần tính toán có thể viết như một hàm thuần. Đây là lý do ta tách:

~~~java
private static long calculateTotal(Purchase purchase) {
    return Math.multiplyExact(
            purchase.unitPriceVnd, (long) purchase.quantity);
}
~~~

Nếu đơn giá là `12000` và số lượng là `3`, hàm cho kết quả `36000` mà không cần bàn phím. Nếu đơn giá là `Long.MAX_VALUE` và lượng là `2`, hàm ném `ArithmeticException` mà không in kết quả. Hai hành vi đó có thể kiểm tra trực tiếp bằng unit test khi nâng cấp dự án.

Trong chương trình thực, nguồn vào có thể đổi từ terminal sang HTTP request hoặc file CSV. Nếu phần xử lý không tự đọc input và không tự in output, ta có thể **tái sử dụng quy tắc** mà không phải mang theo phụ thuộc vào console. Tuy vậy, tách lớp và kiến trúc sâu hơn không phải mục tiêu bắt buộc của bài nhập môn: trước tiên hãy nắm cách dữ liệu đi qua từng giai đoạn.

### Kiểm thử thủ công theo hợp đồng

Hãy thử ít nhất các trường hợp sau, ghi lại input/kỳ vọng trước khi chạy:

| Trường hợp | Input ba dòng | Kỳ vọng |
| --- | --- | --- |
| Bình thường | `But bi / 12000 / 3` | `Tong tien: 36000 VND` |
| Hàng miễn phí | `Qua tang / 0 / 2` | `Tong tien: 0 VND` |
| Tên trống | `(trống) / 12000 / 3` | Thông báo tên không được rỗng |
| Số lượng sai | `But bi / 12000 / 0` | Thông báo lượng phải dương |
| Không phải số | `But bi / abc / 3` | Thông báo yêu cầu số nguyên |
| Tràn số | `But bi / 9223372036854775807 / 2` | Lỗi tràn, không có dòng tổng tiền |

Một bộ test hữu ích luôn có **ca bình thường + ca biên + ca lỗi**, không chỉ có một ảnh chụp màn hình chứng minh chương trình in được một phép nhân.

## 10. Bài tập thực hành

**Bài 1 — Vẽ luồng IPO:** Với ứng dụng tính điểm trung bình từ ba bài kiểm tra, hãy xác định đầu vào, kiểu dữ liệu nội bộ, công thức xử lý, cách hiển thị kết quả và những dữ liệu không hợp lệ. Nếu điểm hợp lệ là `0..10`, bạn kiểm tra ở giai đoạn nào?

**Bài 2 — Thêm giảm giá:** Thêm một dòng đầu vào “Phần trăm giảm giá” (số nguyên `0..100`) rồi tính tiền cuối. Chỉ rõ thứ tự nhân/chia và cách làm tròn bằng phép tính số nguyên. Viết trước các ca 0%, 100%, 50% và input ngoài phạm vi. Đây là **mở rộng tính năng** chứ không còn là bản gốc ba dòng.

**Bài 3 — Cải thiện thông báo nhập lỗi:** Trong `readPurchase`, kiểm tra tên ngay sau khi đọc và giá ngay sau khi parse; để input sai được báo sớm. Quan sát thay đổi về số lời nhắc nhưng vẫn giữ công thức thành tiền.

**Bài 4 — Phân biệt stdout/stderr:** Cấp số lượng 0 bằng pipe, chuyển hướng hai luồng ra hai tệp. Dòng nào là kết quả, dòng nào là lỗi? Vì sao một công cụ tự động nên dựa cả vào mã thoát?

**Bài 5 — Đổi nguồn input:** Thay bàn phím bằng dữ liệu từ một tệp văn bản có ba dòng. Dùng `<` của shell nơi hỗ trợ hoặc `Files.newBufferedReader`. Chỉ ra phần nào của chương trình phải thay, phần nào có thể giữ nguyên.

### Tự đánh giá

Bạn hiểu bài khi có thể: **(1)** mô tả đường đi Input → Validation → Processing → Output; **(2)** phân biệt `String` đọc vào với giá trị số đã parse; **(3)** chỉ rõ vai trò `System.in`, `System.out`, `System.err`; **(4)** giải thích vì sao dữ liệu sai hoặc tràn số **không được in như kết quả thành công**; và **(5)** tự chạy/chẩn đoán ví dụ bằng `javac`/`java`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-007">Phân biệt cú pháp đúng với kết quả nghiệp vụ đúng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cú pháp đúng với kết quả nghiệp vụ đúng

## 1. Vì sao một chương trình chạy được vẫn có thể sai?

Xét hai đoạn Java sau:

~~~java
int quantity = 3;
int unitPrice = 100_000;
int subtotal = quantity * unitPrice;
System.out.println(subtotal); // 300000
~~~

~~~java
int quantity = 3;
int unitPrice = 100_000;
int subtotal = quantity + unitPrice;
System.out.println(subtotal); // 100003
~~~

Cả hai đoạn đều **đúng cú pháp**, hợp lệ về kiểu và có thể biên dịch. Nhưng nếu yêu cầu là **thành tiền = số lượng × đơn giá**, chỉ đoạn đầu thực hiện đúng nghiệp vụ.

Điều quan trọng cần nhớ: **`javac` có thể kiểm tra nhiều quy tắc của ngôn ngữ Java, nhưng không tự biết người dùng muốn tính gì**. Một chương trình có thể biên dịch thành công, không ném exception, vẫn trả kết quả sai, tính sai tiền hoặc vi phạm điều kiện của bài toán.

Bài này tập trung vào cách đi từ **yêu cầu bằng lời** đến **quy tắc kiểm chứng được**, viết mã Java đúng và kiểm thử kết quả. Không cần framework hay cơ sở dữ liệu để thực hành.

## 2. Bốn lớp kiểm tra tính đúng

| Lớp kiểm tra | Câu hỏi | Ví dụ vấn đề | Cách phát hiện |
|---|---|---|---|
| **Cú pháp (syntax)** | Mã có tuân ngữ pháp Java? | Thiếu dấu `;`, ngoặc chưa đóng | `javac` báo lỗi biên dịch |
| **Ngữ nghĩa tĩnh / kiểu (static semantics)** | Biến, phép toán, kiểu trả về có hợp lệ? | `int n = "ba";` | Trình biên dịch kiểm tra kiểu |
| **Hành vi lúc chạy (runtime)** | Khi chạy có exception hoặc sự cố? | Chia số nguyên cho 0, truy cập mảng quá chỉ số | Chạy thử, test, log, stack trace |
| **Tính đúng nghiệp vụ (business correctness)** | Kết quả và hành vi có đúng quy tắc đã thống nhất? | Giảm giá sai đối tượng, miễn phí ship sai ngưỡng | Acceptance tests, ví dụ kỳ vọng, đối chiếu yêu cầu |

Cả ba điều kiện đầu đều cần thiết trong nhiều ứng dụng, nhưng **chưa đủ để bảo đảm điều kiện thứ tư**.

~~~mermaid
flowchart TD
    A["Yêu cầu nghiệp vụ"] --> B["Quy tắc có thể kiểm chứng"]
    B --> C["Viết mã Java"]
    C --> D{"Biên dịch thành công?"}
    D -->|"Không"| E["Sửa cú pháp / kiểu"]
    E --> C
    D -->|"Có"| F["Thực thi và kiểm thử"]
    F --> G{"Kết quả khớp quy tắc?"}
    G -->|"Không"| H["Tìm lỗi logic / giả định / dữ liệu"]
    H --> B
    G -->|"Có"| I["Đạt các ca đã kiểm; tiếp tục review và kiểm thử thêm"]
~~~

Ngay cả khi một bộ test đều xanh, điều đó chỉ chứng minh chương trình **đạt các trường hợp đã kiểm**, không chứng minh mọi đầu vào và mọi môi trường đều đúng.

## 3. Biến yêu cầu bằng lời thành một hợp đồng rõ ràng

Giả sử cửa hàng bán hàng hóa theo **đồng Việt Nam nguyên đơn vị** và đưa ra chính sách sau:

- **BR-01 – Dữ liệu hợp lệ:** đơn giá phải **lớn hơn 0**; số lượng phải **lớn hơn 0**.
- **BR-02 – Tiền hàng:** `subtotal = unitPrice × quantity`.
- **BR-03 – Giảm giá:** khách là **thành viên đang hoạt động** và tiền hàng ban đầu **từ 200.000 đồng trở lên** được giảm **10% tiền hàng**. Các trường hợp khác không giảm.
- **BR-04 – Làm tròn:** phần tiền giảm được **lấy phần nguyên theo đơn vị đồng** (làm tròn xuống); đầu vào và kết quả đều không âm.
- **BR-05 – Vận chuyển:** nếu **tiền hàng sau giảm giá** từ **300.000 đồng trở lên**, phí vận chuyển bằng 0; nếu thấp hơn, phí là **30.000 đồng**.
- **BR-06 – Tổng thanh toán:** `payable = subtotal − discount + shipping`.
- **BR-07 – Quá giới hạn số:** nếu phép tính vượt miền `long`, phải báo lỗi thay vì âm thầm tạo số tiền sai.

**Điểm dễ nhầm:** ngưỡng giảm giá xét trên `subtotal` **trước giảm**, nhưng ngưỡng miễn phí vận chuyển xét trên số tiền **sau giảm**. Cả hai ngưỡng đều dùng “**từ ... trở lên**”, nghĩa là dùng `>=`, không phải `>`.

Quy tắc làm tròn là một phần của yêu cầu: với giảm giá 10% và tiền nguyên đồng không âm, `subtotal / 10` (chia nguyên) cho phần tiền giảm đã làm tròn xuống. Nếu chính sách đổi sang 7,5% hoặc tính thuế, công thức và quy tắc làm tròn phải được xác nhận lại.

### Bảng dự đoán trước khi viết mã

| Tiền hàng | Thành viên hoạt động? | Giảm giá | Sau giảm | Ship | Tổng trả |
|---:|---|---:|---:|---:|---:|
| 199.999 | Có | 0 | 199.999 | 30.000 | 229.999 |
| 200.000 | Có | 20.000 | 180.000 | 30.000 | 210.000 |
| 300.000 | Có | 30.000 | 270.000 | 30.000 | 300.000 |
| 300.000 | Không | 0 | 300.000 | 0 | 300.000 |
| 400.000 | Có | 40.000 | 360.000 | 0 | 360.000 |

Bảng này là **test oracle**: kết quả mong đợi được suy ra từ quy tắc trước khi xem chương trình trả về gì.

## 4. Một đoạn Java hợp lệ nhưng vi phạm nghiệp vụ

Đoạn sau có cú pháp hợp lệ, nhưng tính phí vận chuyển theo tiền hàng **trước** giảm:

~~~java
long subtotal = 300_000L;
boolean activeMember = true;

long discount = activeMember && subtotal >= 200_000L
        ? subtotal / 10
        : 0L;

long shipping = subtotal >= 300_000L ? 0L : 30_000L; // SAI QUY TẮC
long payable = subtotal - discount + shipping;

System.out.println(payable); // 270000
~~~

Theo BR-05, đúng ra phải xét `subtotal - discount = 270000`, vì vậy phí vận chuyển là `30000` và tổng đúng là **300000**. `javac` không thể biết quy tắc BR-05 từ một đoạn mã.

Sửa tối thiểu:

~~~java
long afterDiscount = subtotal - discount;
long shipping = afterDiscount >= 300_000L ? 0L : 30_000L;
long payable = afterDiscount + shipping;
System.out.println(payable); // 300000
~~~

**Cảnh báo:** các snippet trên tập trung minh họa bug logic; khi triển khai hoàn chỉnh vẫn cần bảo vệ đầu vào, kiểm tra tràn số và kiểm thử ngưỡng.

## 5. Chương trình Java 17 độc lập và có thể chạy

Tạo tệp chính xác tên **`BusinessCorrectnessDemo.java`**:

~~~java
public final class BusinessCorrectnessDemo {
    private static final long DISCOUNT_MIN = 200_000L;
    private static final long FREE_SHIPPING_MIN = 300_000L;
    private static final long SHIPPING_FEE = 30_000L;

    private BusinessCorrectnessDemo() {}

    // Record chỉ lưu kết quả; toàn bộ số tiền tính theo đồng nguyên.
    record Quote(long subtotal, long discount,
                 long shipping, long payable) {}

    static Quote calculate(long unitPrice, int quantity,
                           boolean activeMember) {
        if (unitPrice <= 0) {
            throw new IllegalArgumentException("Don gia phai duong");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("So luong phai duong");
        }

        // Phát hiện tràn long trước khi có số tiền hàng.
        long subtotal = Math.multiplyExact(unitPrice, (long) quantity);

        // 10% và làm tròn xuống đồng, chỉ khi đủ cả hai điều kiện.
        long discount = activeMember && subtotal >= DISCOUNT_MIN
                ? subtotal / 10
                : 0L;

        long afterDiscount = Math.subtractExact(subtotal, discount);
        long shipping = afterDiscount >= FREE_SHIPPING_MIN
                ? 0L
                : SHIPPING_FEE;
        long payable = Math.addExact(afterDiscount, shipping);

        return new Quote(subtotal, discount, shipping, payable);
    }

    static void expect(String label, long unitPrice, int quantity,
                       boolean member, long expectedSubtotal,
                       long expectedDiscount, long expectedShipping,
                       long expectedPayable) {
        Quote q = calculate(unitPrice, quantity, member);
        if (q.subtotal() != expectedSubtotal
                || q.discount() != expectedDiscount
                || q.shipping() != expectedShipping
                || q.payable() != expectedPayable) {
            throw new AssertionError(
                    label + ": ket qua khong dung, actual=" + q);
        }
        System.out.println(label + " -> " + q.payable() + " VND");
    }

    static void checkInvalidInput() {
        boolean invalidPrice = false;
        boolean invalidQuantity = false;
        try {
            calculate(0, 1, false);
        } catch (IllegalArgumentException expected) {
            invalidPrice = true;
        }
        try {
            calculate(100, 0, true);
        } catch (IllegalArgumentException expected) {
            invalidQuantity = true;
        }
        if (!invalidPrice || !invalidQuantity) {
            throw new AssertionError("Input invalid was accepted");
        }
        System.out.println("Invalid input: PASS");
    }

    static void checkOverflow() {
        boolean overflowDetected = false;
        try {
            calculate(Long.MAX_VALUE, 2, true);
        } catch (ArithmeticException expected) {
            overflowDetected = true;
        }
        if (!overflowDetected) {
            throw new AssertionError("Overflow was not detected");
        }
        System.out.println("Overflow: PASS");
    }

    public static void main(String[] args) {
        expect("A", 100_000, 3, true,
                300_000, 30_000, 30_000, 300_000);
        expect("B", 100_000, 3, false,
                300_000, 0, 0, 300_000);
        expect("C", 199_999, 1, true,
                199_999, 0, 30_000, 229_999);
        expect("D", 200_000, 1, true,
                200_000, 20_000, 30_000, 210_000);
        expect("E", 100_000, 4, true,
                400_000, 40_000, 0, 360_000);
        checkInvalidInput();
        checkOverflow();
        System.out.println("ALL CASES PASS");
    }
}
~~~

Chương trình cần **JDK 17 trở lên** do dùng `record`. Mở terminal trong thư mục chứa tệp và chạy:

~~~bash
javac --release 17 -encoding UTF-8 BusinessCorrectnessDemo.java
java BusinessCorrectnessDemo
~~~

**Đầu ra kỳ vọng:**

~~~text
A -> 300000 VND
B -> 300000 VND
C -> 229999 VND
D -> 210000 VND
E -> 360000 VND
Invalid input: PASS
Overflow: PASS
ALL CASES PASS
~~~

Mã dùng `if`, `&&`, toán tử điều kiện `?:`, các phép toán số nguyên và method để kiểm chứng quy tắc. Các câu lệnh `throw` ở đầu vào chỉ từ chối dữ liệu sai; chúng không thay cho việc kiểm thử công thức tiền hàng.

**Lưu ý về kiểm thử:** phương thức `expect` tự ném `AssertionError` khi phát hiện sai, nên không phụ thuộc vào cờ `-ea`. Các kết quả trong bài là **kỳ vọng để người học tự chạy đối chiếu**, không phải báo cáo đã thực thi code trên máy của người học.

## 6. Đọc mã đúng theo thứ tự nghiệp vụ

Hãy theo dấu trường hợp `unitPrice = 100000`, `quantity = 3`, `activeMember = true`:

| Bước | Phép tính/điều kiện | Kết quả |
|---|---|---:|
| 1 | Kiểm tra đơn giá và số lượng dương | Hợp lệ |
| 2 | `100000 × 3` | `subtotal = 300000` |
| 3 | Thành viên active và `300000 >= 200000` | Đủ điều kiện |
| 4 | `300000 / 10` | `discount = 30000` |
| 5 | `300000 - 30000` | `afterDiscount = 270000` |
| 6 | `270000 >= 300000` | Sai |
| 7 | Cộng phí ship `30000` | `payable = 300000` |

Ở bước 6, nếu ai đó vô tình dùng `subtotal` thay vì `afterDiscount`, chương trình vẫn đúng cú pháp và đúng kiểu, nhưng sai BR-05.

**Nguyên tắc quan trọng:** đặt tên biến thể hiện đúng **ý nghĩa và thời điểm** của dữ liệu. `subtotal` và `afterDiscount` đều là `long`, nhưng không thể thay thế nhau trong mọi công thức chỉ vì chúng cùng kiểu Java.

## 7. Kiểm thử ngưỡng và edge cases

Các lỗi nghiệp vụ thường xuất hiện tại **ranh giới điều kiện**, trong trường hợp dữ liệu không hợp lệ hoặc lúc các điều kiện kết hợp.

| Tình huống | Dữ liệu kiểm thử | Kết quả cần xác nhận |
|---|---|---|
| Ngay dưới ngưỡng giảm giá | `subtotal = 199999`, member=true | Không giảm |
| Đúng ngưỡng giảm giá | `subtotal = 200000`, member=true | Có giảm 10% |
| Cùng mức tiền nhưng không là thành viên | `subtotal = 200000`, member=false | Không giảm |
| Sau giảm ngay dưới ngưỡng ship | `subtotal = 300000`, member=true | Sau giảm 270000, phí 30000 |
| Đúng ngưỡng ship | `subtotal = 300000`, member=false | Ship 0 |
| Tổng sau giảm vượt ngưỡng ship | `subtotal = 400000`, member=true | Ship 0 |
| Số lượng 0 hoặc âm | `quantity <= 0` | Từ chối đầu vào |
| Đơn giá 0 hoặc âm | `unitPrice <= 0` | Từ chối đầu vào |
| Tràn phép nhân | `unitPrice = Long.MAX_VALUE`, `quantity = 2` | `ArithmeticException` |
| Tiền hàng 200.001 và member=true | `subtotal = 200001` | Giảm 20000 đồng (lấy phần nguyên) |

Khi kiểm thử ngưỡng `T`, thường chọn **`T - 1`, `T`, `T + 1`** theo đơn vị có ý nghĩa. Với BR-05, nhớ tạo dữ liệu sao cho **số tiền sau giảm** nằm ở những điểm này; chỉ kiểm thử số tiền trước giảm có thể bỏ lọt bug.

### Thuộc tính có thể kiểm thử thêm

Với đầu vào hợp lệ và không tràn số, mọi kết quả phải thỏa:

- `subtotal > 0`.
- `0 <= discount <= subtotal`.
- `shipping == 0` hoặc `shipping == 30000`.
- `payable == subtotal - discount + shipping`.
- Nếu `activeMember == false` thì `discount == 0`.
- Nếu `afterDiscount >= 300000` thì `shipping == 0`, nếu nhỏ hơn thì `shipping == 30000`.

Các thuộc tính này rất hữu ích để rà soát nhiều ca; nhưng một implementation sai có thể vẫn giữ vài thuộc tính, nên phải kết hợp với **ví dụ kết quả cụ thể được xác nhận từ nghiệp vụ**.

## 8. Những bẫy Java khiến kết quả nghiệp vụ sai

### 8.1. Chia nguyên và làm tròn không rõ ràng

~~~java
int percent = 15;
int amount = 101;
int discount = amount * percent / 100; // 15, phần lẻ bị bỏ
~~~

Công thức có thể đúng **hoặc sai** tùy quy tắc làm tròn được yêu cầu. Với tiền, phải thống nhất đơn vị, phép chia, thứ tự làm tròn và chính sách rounding. `double` có thể không biểu diễn chính xác nhiều số thập phân; số nguyên theo đơn vị nhỏ nhất hoặc `BigDecimal` thường dễ kiểm soát hơn.

### 8.2. Tràn số vẫn đúng kiểu `long`

~~~java
long price = Long.MAX_VALUE;
long total = price * 2; // Tràn long nếu nhân thông thường
~~~

Java không tự ném exception khi mọi phép toán nguyên thông thường bị tràn. Dùng `Math.multiplyExact`/`Math.addExact` hoặc giới hạn miền dữ liệu rõ ràng khi tràn không được phép.

### 8.3. Nhầm `>` với `>=`

~~~java
boolean eligible = subtotal > 200_000;  // Sai khi subtotal đúng 200000
boolean correct = subtotal >= 200_000; // Đúng với "từ 200000"
~~~

Một ký tự khác nhau có thể làm sai hàng nghìn giao dịch sát ngưỡng.

### 8.4. `==` không kiểm tra nội dung hai chuỗi

~~~java
String role = new String("MEMBER");
System.out.println(role == "MEMBER");      // false
System.out.println("MEMBER".equals(role));  // true
~~~

Nếu điều kiện giảm giá dựa trên role lấy từ input, dùng `==` với `String` có thể làm sai logic dù biên dịch và chạy bình thường. Gọi `"MEMBER".equals(role)` cũng an toàn khi `role == null`.

### 8.5. `&&` và `&` không hoàn toàn như nhau

~~~java
String memberId = null;
boolean valid = memberId != null && !memberId.isBlank(); // false
~~~

`&&` ngắn mạch: khi vế trái sai, vế phải không được đánh giá. Đổi sang `&` trên boolean sẽ đánh giá cả hai vế, ở đây gây `NullPointerException`.

### 8.6. Điều kiện đúng nhưng kiểm tra sai thời điểm

Quy tắc “ship miễn phí từ 300.000 đồng **sau giảm**” cần dùng `afterDiscount`. Dù `subtotal`, `afterDiscount` và `payable` cùng kiểu `long`, chúng đại diện cho các giai đoạn khác nhau. Tên biến và thứ tự phép tính phải phản ánh chính sách.

### 8.7. Kiểm tra đầu vào không thay cho kiểm tra kết quả

Đơn giá và số lượng đều dương **không bảo đảm** phép nhân an toàn khỏi tràn. Ngược lại, có tổng tiền tính được cũng không chứng minh người mua đủ điều kiện giảm giá. Cần kiểm tra **cả điều kiện đầu vào, ranh giới số học và kết quả theo nghiệp vụ**.

## 9. Quy trình tìm lỗi khi test thất bại

Khi kết quả không khớp yêu cầu, đừng sửa ngẫu nhiên cho tới khi test xanh. Thử lần lượt:

1. **Đọc lại câu yêu cầu**: xác định ngưỡng, dấu bằng, đối tượng được áp dụng, thời điểm tính, đơn vị và cách làm tròn.
2. **Viết expected result bằng tay** từ quy tắc. Với bài toán có nhiều điều kiện, lập bảng quyết định.
3. **Tái hiện bằng ca nhỏ nhất**: ví dụ `subtotal=300000`, member=true đủ để phát hiện nhầm ngưỡng phí ship.
4. **Theo dõi giá trị trung gian**: `subtotal`, `discount`, `afterDiscount`, `shipping`, `payable`.
5. **Kiểm tra toán tử, kiểu và thứ tự tính**: đặc biệt `>=`, `&&`, chia nguyên và overflow.
6. **Sửa đúng quy tắc bị vi phạm**, thêm test hồi quy cho ca vừa tìm được và chạy lại các ca cũ.

Một lỗi có thể đến từ chính **đặc tả chưa rõ**. Nếu yêu cầu không nói giảm giá làm tròn như thế nào, người lập trình không nên tự giả định rồi xem kết quả là đúng; cần làm rõ với người phụ trách nghiệp vụ.

## 10. Bộ công cụ kiểm chứng: mỗi công cụ trả lời một câu hỏi

| Hoạt động | Xác minh được | Không tự bảo đảm |
|---|---|---|
| `javac` | Cú pháp, kiểu, tên, điều kiện biên dịch | Công thức tiền đã đúng |
| Chạy chương trình | Quan sát hành vi ở dữ liệu đã chạy | Đúng ở mọi dữ liệu khác |
| Unit test / assertion rõ ràng | Một quy tắc cụ thể với input và expected | Bao phủ toàn bộ nghiệp vụ |
| Boundary testing | Các trường hợp ngay dưới, đúng và trên ngưỡng | Mọi quy tắc tổ hợp |
| Code review đối chiếu BR | Rà lại đơn vị, công thức, nhánh, ngoại lệ | Tự thay cho chạy test |
| Static analysis / lint | Một số mẫu lỗi và vấn đề chất lượng | Hiểu hết quy định kinh doanh |
| Integration/acceptance test | Các thành phần phối hợp và kịch bản thực tế | Bao phủ vô hạn trạng thái hệ thống |

Dùng `assert` của ngôn ngữ Java trong chương trình thông thường cần nhớ assertions có thể **bị vô hiệu hóa** nếu không bật `-ea`. Vì vậy ví dụ dùng `AssertionError` trực tiếp trong phương thức kiểm tra để luôn phát hiện kết quả không khớp khi chạy demo. Trong dự án thực tế, nên dùng framework kiểm thử với assertions được quản lý rõ ràng.

## 11. Bài tập thực hành kèm đáp án

### Bài A — tìm lỗi nghiệp vụ trong mã hợp lệ

~~~java
long subtotal = 300_000;
long discount = 30_000;
long shipping = subtotal >= 300_000 ? 0 : 30_000;
long payable = subtotal - discount + shipping;
~~~

**Hỏi:** mã có biên dịch được không (khi nằm trong method hợp lệ)? Kết quả có đúng BR-05 không?

**Đáp án:** mã đúng cú pháp/kiểu; `payable=270000`, nhưng BR-05 yêu cầu phí ship dựa trên `270000` sau giảm, nên tổng đúng là `300000`. Sửa điều kiện thành `subtotal - discount >= 300_000` hoặc đặt tên `afterDiscount` rồi dùng biến ấy.

### Bài B — xét ranh giới

Cùng một khách active, hãy tính kết quả với tiền hàng `199999`, `200000`, `200001`.

**Đáp án:** lần lượt:

- 199999: discount 0, ship 30000, payable **229999**.
- 200000: discount 20000, sau giảm 180000, ship 30000, payable **210000**.
- 200001: discount 20000 sau làm tròn xuống, sau giảm 180001, ship 30000, payable **210001**.

### Bài C — phát hiện kiểm thử thiếu

Nhóm chỉ thử `subtotal=400000` và thấy thành viên được giảm giá, miễn ship, tổng đúng. Liệu đã chứng minh BR-05 chưa?

**Đáp án:** chưa. Cần ca `subtotal=300000, activeMember=true`: sau giảm còn 270000, do đó **không được miễn ship**. Đây là ca bắt lỗi “ship tính trước giảm”.

### Bài D — thử dữ liệu sai và tràn số

Chạy `calculate(0,1,false)`, `calculate(100,0,true)`, `calculate(Long.MAX_VALUE,2,true)`.

**Đáp án:** hai ca đầu ném `IllegalArgumentException` vì vi phạm BR-01; ca cuối ném `ArithmeticException` do `Math.multiplyExact` phát hiện tràn. Không được thay lỗi bằng giá âm rồi tiếp tục tính phí.

### Bài E — thay đổi chính sách

Chủ cửa hàng đổi điều kiện: “miễn phí vận chuyển từ 300.000 đồng **trước giảm**”. Cần sửa gì?

**Đáp án:** trong BR-05 mới, ngưỡng dùng `subtotal` thay cho `afterDiscount`. Phải sửa code, expected ở các ca chịu ảnh hưởng (đặc biệt tiền hàng 300000 có giảm giá), và lưu sự thay đổi của đặc tả. Một bản sửa theo BR mới có thể **cố tình khác** kết quả của bộ test cũ: test cũng phải được cập nhật có căn cứ, không sửa ngầm để làm xanh.

## 12. Checklist nghiệm thu và ghi nhớ

- [ ] Phân biệt được **compile-time error**, **runtime error** và **lỗi logic nghiệp vụ**.
- [ ] Viết được quy tắc kinh doanh thành điều kiện đầu vào, công thức và kết quả kỳ vọng.
- [ ] Biết xác định giá trị nào là trước/sau một bước xử lý.
- [ ] Kiểm tra đúng các ngưỡng `<`, `<=`, `>`, `>=` và tổ hợp điều kiện.
- [ ] Dùng loại số và đơn vị tiền phù hợp; xác định quy tắc làm tròn.
- [ ] Bảo vệ đầu vào sai, overflow và nhánh có thể gây exception.
- [ ] Biên dịch và chạy `BusinessCorrectnessDemo.java`, đối chiếu đầu ra.
- [ ] Có các ca **dưới ngưỡng, đúng ngưỡng, trên ngưỡng**, trường hợp thành viên và không thành viên.
- [ ] Có test hồi quy chứng minh một bug nghiệp vụ đã sửa không xuất hiện trở lại.
- [ ] Không xem “`javac` không báo lỗi” hoặc “chạy không crash” là bằng chứng đủ về tính đúng nghiệp vụ.

**Kết luận:** Java quyết định **mã có hợp lệ theo ngôn ngữ hay không**; còn các quy tắc nghiệp vụ xác định **kết quả nào mới là đúng**. Muốn có phần mềm đáng tin cậy, phải nối hai phía bằng ví dụ đầu vào/đầu ra, phép tính có ý nghĩa rõ ràng, xử lý edge cases và kiểm thử đối chiếu yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="language-overview-008">Lộ trình học và chương trình ví dụ xuyên suốt</a>

<details>
<summary>Xem chi tiết</summary>

# Lộ trình học Java core và chương trình ví dụ xuyên suốt

Học Java dễ bị rời rạc nếu hôm nay viết một phép cộng, ngày mai học `if`, hôm sau học vòng lặp mà không thấy chúng phối hợp giải quyết vấn đề như thế nào. Một lộ trình hiệu quả nên có **đích đến cụ thể**: sau mỗi phần kiến thức, bạn bổ sung được một khả năng vào **cùng một chương trình**, tự chạy và đối chiếu kết quả.

Bài này đề xuất các chặng học nền tảng của Java core và một ví dụ xuyên suốt: **theo dõi số phút tự học mỗi ngày**. Chương trình nhận mục tiêu phút/ngày cùng thời lượng thực tế của nhiều ngày, tính tổng, trung bình và số ngày đạt mục tiêu. Đây là bài thực hành dòng lệnh, không cần framework, cơ sở dữ liệu hay kiến thức lập trình hướng đối tượng nâng cao.

## 1. Học theo khả năng làm được, không chỉ theo số bài đã đọc

| Chặng | Kiến thức cần nắm | Khả năng kiểm chứng | Phần bổ sung vào ví dụ |
| --- | --- | --- | --- |
| 1. Chạy một chương trình | JDK, tệp `.java`, `main`, `javac`, `java`, `println` | Biên dịch và in được thông điệp | In tiêu đề “Theo dõi tự học” |
| 2. Biểu diễn dữ liệu | `int`, `long`, `double`, `boolean`, `String`, biến và toán tử | Giải thích đúng kiểu và đơn vị của mỗi biến | Lưu mục tiêu, số phút, tính tổng và trung bình |
| 3. Ra quyết định | So sánh, `if`, `else`, `&&`, `||` | Dự đoán nhánh chạy ở giá trị biên | Quyết định ngày nào đạt mục tiêu |
| 4. Xử lý nhiều giá trị | `for`, mảng, chỉ số, `args` | Duyệt đúng từng phần tử, không lệch chỉ số | Tổng hợp nhiều ngày thay vì một ngày |
| 5. Tổ chức mã | Phương thức, tham số, kiểu trả về, phạm vi biến | Tách được xử lý khỏi phần nhập/xuất | Viết phương thức kiểm tra số phút hợp lệ |
| 6. Xử lý đầu vào và kiểm tra | `parseInt`, ngoại lệ, test thường và test biên | Phân biệt lỗi biên dịch, lỗi dữ liệu và lỗi logic | Từ chối số âm, chuỗi không phải số, thiếu ngày |

Đây là **trình tự gợi ý**, không phải số ngày bắt buộc phải hoàn thành. Chỉ chuyển sang chặng kế khi bạn có thể tự giải thích vì sao chương trình cho kết quả hiện tại và tự sửa một lỗi nhỏ mà không phải chép lại toàn bộ lời giải.

Không cần học generics, luồng, mạng hay framework để chạy được ví dụ đầu tiên. Những chủ đề đó có thể học khi bài toán xuất hiện nhu cầu thực sự.

## 2. Xác định yêu cầu trước khi lập trình

Giả sử bạn muốn xem mình có học đủ **60 phút mỗi ngày** trong bốn ngày gần đây không.

Đầu vào mẫu:

- Mục tiêu: `60` phút/ngày.
- Thời gian thực tế lần lượt: `45`, `60`, `20`, `80` phút.

Yêu cầu đầu ra:

| Giá trị | Kết quả tính tay |
| --- | ---: |
| Số ngày | 4 |
| Tổng phút | 45 + 60 + 20 + 80 = 205 |
| Số ngày đạt mục tiêu | 2 (ngày 2 và ngày 4) |
| Trung bình mỗi ngày | 205 / 4 = 51,25 phút |

**Quy ước của chương trình:**

- Mục tiêu phải là số nguyên **lớn hơn 0**.
- Số phút của từng ngày là số nguyên **không âm**; ngày 0 phút vẫn được tính là một ngày.
- Phải có ít nhất một ngày để tránh chia cho 0.
- Ngày có thời gian **bằng** mục tiêu được tính là đạt (`>=`).
- Mỗi đối số dòng lệnh phải biểu diễn một số nguyên trong miền `int`; giá trị `10.5` không hợp lệ ở phiên bản này.
- Trung bình có thể có phần thập phân. Chương trình in tới hai chữ số sau dấu thập phân, **không làm tròn số phút đầu vào**.

Việc nêu các quy ước trước khi code quan trọng hơn việc chọn ngay vòng lặp nào: chương trình phải đúng yêu cầu đã xác định.

## 3. Chuyển yêu cầu thành các bước xử lý

Viết thuật toán bằng lời:

1. Đọc đối số đầu tiên làm mục tiêu; những đối số tiếp theo là số phút của từng ngày.
2. Nếu thiếu mục tiêu hoặc thiếu toàn bộ dữ liệu ngày, in hướng dẫn sử dụng.
3. Chuyển văn bản thành số nguyên và từ chối dữ liệu không hợp lệ.
4. Đặt `total` (tổng phút) và `daysMet` (số ngày đạt) bằng 0.
5. Duyệt từng ngày: cộng phút vào tổng; nếu phút `>=` mục tiêu thì tăng `daysMet`.
6. Lấy tổng chia số ngày theo phép chia chấm động để tính trung bình.
7. In mục tiêu, số ngày, tổng, số ngày đạt và trung bình.

Điểm dễ nhầm là **mục tiêu không phải một ngày**. Khi xử lý mảng đối số, phần tử `args[0]` là mục tiêu, còn các ngày bắt đầu từ `args[1]`. Nếu vô tình tính cả `args[0]` như một ngày, toàn bộ thống kê sẽ sai dù chương trình vẫn biên dịch được.

## 4. Chương trình Java hoàn chỉnh

Tạo một tệp tên **`StudyTracker.java`** với nội dung sau (phù hợp JDK 17 trở lên):

~~~java
import java.util.Locale;

public class StudyTracker {
    static int parseMinutes(String raw) {
        int minutes = Integer.parseInt(raw);
        if (minutes < 0) {
            throw new IllegalArgumentException(
                    "So phut khong duoc am");
        }
        return minutes;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println(
                    "Cach dung: java StudyTracker "
                    + "<mucTieuPhut> <ngay1> [ngay2] ...");
            return;
        }

        try {
            int goal = parseMinutes(args[0]);
            if (goal == 0) {
                throw new IllegalArgumentException(
                        "Muc tieu phai lon hon 0");
            }

            long total = 0L;
            int daysMet = 0;

            for (int i = 1; i < args.length; i++) {
                int minutes = parseMinutes(args[i]);
                total += minutes;

                if (minutes >= goal) {
                    daysMet++;
                }
            }

            int days = args.length - 1;
            double average = (double) total / days;

            System.out.println(
                    "Muc tieu: " + goal + " phut/ngay");
            System.out.println("So ngay: " + days);
            System.out.println("Tong phut: " + total);
            System.out.println(
                    "Ngay dat muc tieu: " + daysMet);
            System.out.printf(
                    Locale.ROOT,
                    "Trung binh: %.2f phut/ngay%n",
                    average);
        } catch (NumberFormatException ex) {
            System.out.println(
                    "Loi: Moi tham so phai la so nguyen hop le");
        } catch (IllegalArgumentException ex) {
            System.out.println("Loi: " + ex.getMessage());
        }
    }
}
~~~

### Giải thích theo từng chặng

**Biến và kiểu:** `goal`, `minutes`, `daysMet` là `int`; `total` là `long` để tổng nhiều ngày có miền giá trị rộng hơn mỗi số phút riêng lẻ; `average` là `double` vì trung bình có thể không nguyên. Những tên này biểu thị ý nghĩa dữ liệu thay vì chỉ là `a`, `b`, `c`.

**Điều kiện:** `if (minutes >= goal)` kiểm tra từng ngày. Ký hiệu `>=` gồm cả trường hợp bằng nhau, nên 60 phút đạt mục tiêu 60 phút.

**Vòng lặp và mảng:** `args` là `String[]`. Vòng `for` bắt đầu ở chỉ số 1, chạy khi `i < args.length`, và tăng `i` sau mỗi lượt. Java đánh chỉ số mảng từ 0.

**Phương thức:** `parseMinutes(String raw)` nhận chuỗi đầu vào, chuyển thành `int` và từ chối số âm. Việc đặt tên phương thức làm quy tắc kiểm tra có thể dùng lại cho mục tiêu và cho từng ngày.

**Ngoại lệ:** `Integer.parseInt` có thể ném `NumberFormatException` nếu chuỗi không đúng định dạng hoặc ngoài miền `int`. Đây là một lớp con của `IllegalArgumentException`, nên chương trình bắt `NumberFormatException` trước để có thông báo cụ thể.

**Phép chia:** `(double) total / days` yêu cầu phép chia chấm động. Nếu viết `total / days`, phép chia số nguyên sẽ bỏ phần lẻ **trước khi** gán vào biến `double`.

**Giới hạn:** Đây là ứng dụng nhỏ cho một lần chạy. Nó không lưu lịch sử vào tệp, không đọc thời gian hệ thống và không xác định xem người dùng đã thực sự học hay chưa; các số phút do người dùng cung cấp.

## 5. Biên dịch, chạy và đối chiếu kết quả

Mở terminal tại thư mục chứa `StudyTracker.java`. Trước hết kiểm tra công cụ:

~~~bash
java -version
javac -version
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StudyTracker.java
java StudyTracker 60 45 60 20 80
~~~

Kết quả dự kiến:

~~~text
Muc tieu: 60 phut/ngay
So ngay: 4
Tong phut: 205
Ngay dat muc tieu: 2
Trung binh: 51.25 phut/ngay
~~~

Thử truy vết bằng tay:

- `total` đi qua các giá trị 0 → 45 → 105 → 125 → 205.
- `daysMet` chỉ tăng ở số phút 60 và 80, nên kết quả là 2.
- `days = args.length - 1 = 5 - 1 = 4`.
- `average = 205.0 / 4 = 51.25`.

Chương trình này là một ví dụ **đủ nhỏ để truy vết toàn bộ** nhưng có đủ dữ liệu, điều kiện, lặp, phương thức và xử lý lỗi để thực hành nhiều chặng học.

## 6. Test các trường hợp biên

Không nên chỉ chạy dữ liệu mẫu. Hãy thử các trường hợp sau:

| Lệnh chạy | Kết quả cần kiểm tra | Kiến thức liên quan |
| --- | --- | --- |
| `java StudyTracker 60 60` | Một ngày, một ngày đạt, trung bình 60.00 | Ngưỡng bằng nhau |
| `java StudyTracker 60 0 120` | Hai ngày, một ngày đạt, trung bình 60.00 | Giá trị 0 vẫn hợp lệ |
| `java StudyTracker 60 59` | Không ngày nào đạt | Giá trị sát dưới ngưỡng |
| `java StudyTracker 60` | In hướng dẫn sử dụng | Thiếu dữ liệu ngày |
| `java StudyTracker 0 20` | Báo mục tiêu phải > 0 | Kiểm tra hợp lệ |
| `java StudyTracker -1 20` | Báo số phút không được âm | Kiểm tra đầu vào |
| `java StudyTracker 60 20 -5` | Báo lỗi số âm | Giá trị sai ở giữa dãy |
| `java StudyTracker 60 abc` | Báo chuỗi không phải số nguyên | Parse và ngoại lệ |
| `java StudyTracker 60 2.5` | Báo chuỗi không phải số nguyên | Quy ước chỉ nhận số nguyên |
| `java StudyTracker 60 2147483648` | Báo số ngoài miền `int` | Giới hạn kiểu dữ liệu |

Với dữ liệu hợp lệ, số ngày đạt luôn nằm trong khoảng từ 0 tới số ngày. Tổng phút phải không âm. Đây là các **bất biến đơn giản để tự kiểm tra kết quả**.

Lưu ý: `total` là `long` nhưng về nguyên tắc vẫn có giới hạn. Với số lượng đối số khả thi của chương trình dòng lệnh thông thường, tràn `long` khó xảy ra; nếu phát triển thành công cụ xử lý dữ liệu cực lớn, cần thêm kiểm tra tràn (ví dụ `Math.addExact`) và quy tắc lưu trữ phù hợp.

## 7. Mỗi chặng học thêm một bài tập vào cùng chương trình

Không cần viết lại một ứng dụng hoàn toàn khác sau mỗi bài học. Bạn có thể dùng cùng `StudyTracker` và nâng cấp từng bước:

| Chặng nâng cấp | Bài tập cụ thể | Dấu hiệu làm đúng |
| --- | --- | --- |
| Biến và biểu thức | In số phút còn thiếu để đạt mục tiêu của từng ngày | Không báo thiếu âm cho ngày đã đạt |
| Điều kiện | Phân loại mỗi ngày: 0 phút, dưới mục tiêu, đạt mục tiêu | Đúng các ca 0, goal−1, goal, goal+1 |
| Vòng lặp | In số thứ tự ngày cùng số phút của ngày đó | Không đưa mục tiêu vào danh sách ngày |
| Phương thức | Tạo `static boolean metGoal(int minutes, int goal)` | Từng kết quả giống với điều kiện cũ |
| Mảng | Chuyển các số phút hợp lệ vào `int[]` trước khi tổng hợp | Số phần tử bằng số ngày nhập |
| Kiểm thử | Viết một bảng input/expected output riêng | Kiểm tra cả ca thường, biên và sai |
| Nâng cao sau nền tảng | Lưu dữ liệu bằng `List` hoặc tệp, bổ sung test tự động | Không thay đổi nghĩa thống kê gốc |

Một nâng cấp nhỏ minh họa:

~~~java
static boolean metGoal(int minutes, int goal) {
    return minutes >= goal;
}
~~~

Nếu thêm phương thức này **vào trong lớp `StudyTracker`**, bạn có thể thay điều kiện `if (minutes >= goal)` trong `main` bằng `if (metGoal(minutes, goal))`. Kết quả phải giữ nguyên. Nếu thay đổi kết quả, đó là dấu hiệu refactor đã làm lệch hành vi.

Đừng làm tất cả bài tập trong một lần. Mỗi bước chỉ cần một thay đổi, biên dịch, chạy lại các trường hợp mẫu và so sánh kết quả kỳ vọng.

## 8. Cách tự học và tự kiểm tra tiến độ

Dùng một vòng học nhỏ lặp lại ở mỗi chặng:

1. **Đọc có mục đích:** nắm từ khóa, ý nghĩa và ví dụ tối thiểu.
2. **Dự đoán trước:** viết ra đầu ra kỳ vọng trước khi chạy.
3. **Tự gõ và chạy:** dùng `javac` và `java`, sửa lỗi dựa trên thông báo thật.
4. **Đổi một điều kiện:** thử giá trị biên để nhìn thấy vì sao cú pháp hay kiểu dữ liệu quan trọng.
5. **Giải thích lại:** trả lời bằng lời “vì sao dòng này ở đây?”.
6. **Ghi lại một lỗi đã gặp:** lỗi compile, lỗi runtime hay lỗi logic và cách tìm nguyên nhân.

Bạn có thể xem một chặng là đạt khi tự làm được **ba việc**: giải thích code, dự đoán đầu ra với đầu vào mới, và sửa một thay đổi nhỏ mà không làm mất hành vi đúng trước đó.

### Những bẫy học tập nên tránh

- **Chép mã nhưng không hiểu:** chạy ra đúng một lần không chứng minh bạn nắm các bước xử lý.
- **Học cú pháp tách khỏi bài toán:** đọc vòng lặp xong cần lập tức dùng nó với dữ liệu cụ thể.
- **Nâng cấp quá sớm:** không cần tạo nhiều class, database hay giao diện đồ họa để đếm phút.
- **Bỏ qua trường hợp biên:** dấu `>=` và `>` có thể chỉ khác nhau ở một giá trị nhưng thay đổi kết quả.
- **Nhầm “biên dịch được” với “đúng”:** `javac` kiểm tra cú pháp và kiểu, không tự hiểu thế nào là ngày “đạt mục tiêu”.
- **Đọc thông báo lỗi như thất bại:** lỗi là bằng chứng giúp khoanh vùng điều kiện chưa đúng.

## 9. Tự đánh giá cuối bài

Hãy trả lời mà chưa chạy lại chương trình:

**1.** Với `java StudyTracker 30 10 30 40`, tổng phút, số ngày đạt và trung bình là bao nhiêu?

*Đáp án:* Tổng 80 phút, 2 ngày đạt, trung bình `80 / 3 ≈ 26.67` phút/ngày.

**2.** Nếu vòng lặp bắt đầu từ `i = 0` thay vì `i = 1`, vấn đề gì xảy ra?

*Đáp án:* Mục tiêu bị tính nhầm thành dữ liệu của một ngày; tổng, số ngày đạt và số ngày thống kê không còn cùng ý nghĩa.

**3.** Vì sao `days` phải được tính là `args.length - 1`?

*Đáp án:* Có một đối số dùng để mô tả mục tiêu; các đối số còn lại mới là từng ngày.

**4.** Vì sao một chuỗi `"20"` có thể chuyển thành phút, còn `"hai mươi"` không được chấp nhận trong phiên bản hiện tại?

*Đáp án:* `Integer.parseInt` chỉ đọc chuỗi ở định dạng số nguyên hợp lệ theo quy tắc API, không hiểu số viết bằng chữ tiếng Việt.

**5.** Nếu muốn cho phép số phút lẻ như `45.5`, cần xem lại những gì?

*Gợi ý:* Phải thay đổi yêu cầu đầu vào, kiểu dữ liệu và cách parse, quy tắc kiểm tra, phép so sánh, hiển thị và các test. Đừng chỉ đổi `int` sang `double` mà bỏ qua cách xử lý dữ liệu đặc biệt và ý nghĩa số phút.

## Ghi nhớ

**Lộ trình học Java hiệu quả là chuỗi năng lực được kiểm chứng bằng một chương trình ngày càng hoàn thiện.** Học biến để biểu diễn dữ liệu, điều kiện để thể hiện quy tắc, vòng lặp để xử lý nhiều giá trị, phương thức để tổ chức công việc và kiểm thử để xác nhận chương trình làm đúng yêu cầu. Với `StudyTracker`, mỗi kiến thức mới đều có nơi để áp dụng và kết quả có thể tự kiểm tra — đó là cầu nối từ hiểu cú pháp đến tự giải quyết bài toán bằng Java.


</details>

- [Quay lại đầu trang](#back-to-top)
