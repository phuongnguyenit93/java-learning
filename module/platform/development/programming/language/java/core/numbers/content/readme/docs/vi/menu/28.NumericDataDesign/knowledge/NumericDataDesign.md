<a id="back-to-top"></a>

# Thiết kế dữ liệu số

## Menu
- [Ghi rõ miền giá trị, đơn vị và giới hạn](#numeric-data-design-001)
- [Chọn kiểu tính toán trung gian riêng với kiểu lưu trữ](#numeric-data-design-002)
- [Số đếm, đại lượng đo và giá trị thập phân có quy tắc làm tròn](#numeric-data-design-003)
- [Quy ước xử lý null, NaN, vô cực và giá trị thiếu](#numeric-data-design-004)
- [Kiểm tra đầu vào trước chuyển đổi có thể mất dữ liệu](#numeric-data-design-005)
- [Đưa precision, scale và rounding vào hợp đồng](#numeric-data-design-006)
- [Tránh dùng double làm trung gian cho dữ liệu thập phân chính xác](#numeric-data-design-007)
- [Tách định dạng hiển thị khỏi tính toán và trao đổi](#numeric-data-design-008)
- [Không suy ra quy tắc làm tròn nghiệp vụ chỉ từ kiểu dữ liệu](#numeric-data-design-009)
- [Giới hạn tài nguyên cho đầu vào BigInteger hoặc BigDecimal rất lớn](#numeric-data-design-010)

## <a id="numeric-data-design-001">Ghi rõ miền giá trị, đơn vị và giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi rõ miền giá trị, đơn vị và giới hạn khi thiết kế dữ liệu số trong Java

Một trường số như `amount`, `distance`, `quantity` hay `rate` không được mô tả đầy đủ chỉ bằng kiểu `int`, `long`, `double` hoặc `BigDecimal`. Cùng một giá trị `100` có thể là 100 đồng, 100 cent, 100 mét, 100 mili giây hoặc 100 phần trăm. Tương tự, kiểu `long` có thể lưu một số âm rất lớn, nhưng một số lượng hàng hóa trong miền nghiệp vụ lại chỉ được phép từ 0 đến 10.000. Sử dụng kiểu Java phù hợp là cần thiết; **hợp đồng dữ liệu số phải ghi rõ miền giá trị, đơn vị, độ chính xác và cách xử lý khi vượt giới hạn**.

Bài này tập trung vào cách mô tả và thực thi hợp đồng số tại ranh giới API, DTO, model, lưu trữ và phép tính. Kết quả mong muốn: hai người phát triển độc lập có thể đọc đặc tả và hiểu giống nhau về ý nghĩa của một con số, thay vì tự suy đoán từ tên biến hay kiểu primitive.

## 1. Phân biệt phạm vi của kiểu Java và phạm vi nghiệp vụ

**Miền biểu diễn của kiểu** là mọi giá trị mà kiểu dữ liệu lưu được. **Miền hợp lệ của nghiệp vụ** là tập con được hệ thống cho phép.

| Dữ liệu | Kiểu Java khả dĩ | Miền nghiệp vụ ví dụ | Đơn vị |
| --- | --- | --- | --- |
| Số lượng mặt hàng | `int` | 0 đến 10.000 | sản phẩm |
| Thời hạn yêu cầu | `long` | 1 đến 120.000 | mili giây |
| Quãng đường | `long` | 0 đến 10.000.000 | milimét |
| Giá không âm | `long` | 0 đến 100.000.000 | đơn vị tiền tệ nhỏ nhất |
| Tỷ lệ chiết khấu | `int` | 0 đến 10.000 | basis point, 1 bp = 0,01% |
| Khối lượng | `BigDecimal` | 0 đến 1.000, độ phân giải 0,001 | kilôgam |

**Các con số trong bảng là ví dụ về một hợp đồng cụ thể**, không phải giới hạn mặc định do Java hoặc từng ngành nghề áp đặt. Mỗi sản phẩm phải tự quyết định phạm vi của mình.

Về kỹ thuật:

- `byte`: từ -128 đến 127.
- `short`: từ -32.768 đến 32.767.
- `int`: từ -2.147.483.648 đến 2.147.483.647.
- `long`: từ -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807.
- `BigInteger`: biểu diễn số nguyên độ lớn thay đổi, không bị giới hạn ở 32/64 bit nhưng vẫn chịu giới hạn tài nguyên.
- `BigDecimal`: biểu diễn số thập phân chính xác theo phần nguyên không tỉ lệ (`unscaled value`) và `scale`; vẫn cần xác định miền, độ phân giải và quy tắc làm tròn phù hợp.
- `float` và `double`: số dấu phẩy động nhị phân, có `NaN`, vô cực dương/âm và sai số biểu diễn; chỉ nên chọn nếu các đặc tính đó phù hợp miền dữ liệu.

Chẳng hạn, nếu `quantity` là `int`, Java không ngăn người gọi truyền `-5` hoặc `2_000_000`. Điều kiện “0 đến 10.000” phải được **nêu trong hợp đồng và kiểm tra trong chương trình**, không được suy ra từ kiểu `int`.

Đặc tả cần nói rõ hai đầu biên là **bao gồm** hay **loại trừ**. Ví dụ `0 <= count && count <= 10_000` khác với `0 < count && count < 10_000`. Một trường cho phép 0 cũng khác trường luôn phải dương.

## 2. Ghi rõ đơn vị và ý nghĩa của mỗi trường

Các con số cùng kiểu Java vẫn có thể là những đại lượng không tương thích về đơn vị. Đoạn mã dưới đây biên dịch nhưng rất dễ sai:

```java
long timeout = 5;       // 5 giây hay 5 mili giây?
long distance = 1200;   // 1200 mét hay 1200 milimét?
long price = 1999;      // 1999 đồng hay 19,99 đơn vị tiền?
```

Không đủ thông tin để biết các giá trị trên có chính xác không. Hãy ghi rõ ý nghĩa và, khi hợp lý, đưa đơn vị vào tên biến/API:

```java
long timeoutMillis = 5_000L;
long distanceMillimeters = 1_200L;
long priceMinorUnits = 1_999L;
```

Hợp đồng cho một trường số nên mô tả ít nhất:

1. **Tên đại lượng:** số lượng, thời gian, chiều dài, giá, tỷ lệ hay mã định danh.
2. **Đơn vị gốc (canonical unit):** mili giây, milimét, cent, basis point, byte...
3. **Miền hợp lệ:** min/max, có bao gồm biên không, có cho phép số âm và số 0 không.
4. **Độ phân giải và độ chính xác:** bước tăng nhỏ nhất, số chữ số thập phân được chấp nhận.
5. **Chính sách với giá trị thiếu:** `null` có hợp lệ hay không; 0 có mang nghĩa “chưa biết” hay không.
6. **Chính sách chuyển đổi và làm tròn:** chuyển đơn vị theo hệ số nào; loại bỏ hay làm tròn phần dư; nếu làm tròn thì theo quy tắc nào.
7. **Hành vi khi vi phạm:** từ chối với ngoại lệ/kết quả lỗi, hay xử lý theo quy định nghiệp vụ.

Ví dụ, trường thời gian có thể được đặc tả: “`timeoutMillis`: số nguyên mili giây; từ 1 đến 120.000 tính cả hai đầu; bắt buộc có; giá trị 0 và âm không hợp lệ; giá trị vượt giới hạn bị từ chối.” Đây là hợp đồng có thể dùng để triển khai và kiểm thử.

### Tránh phép cộng trộn đơn vị

```java
long durationSeconds = 3;
long durationMillis = 500;

// Sai nghĩa: 3 giây + 500 mili giây không phải là 503 mili giây.
long wrong = durationSeconds + durationMillis;

// Đúng: cùng đơn vị mili giây trước khi cộng.
long correct = Math.addExact(
        Math.multiplyExact(durationSeconds, 1_000L), durationMillis);
// correct = 3500
```

`Math.multiplyExact` và `Math.addExact` giúp phát hiện tràn trong chuyển đổi/cộng nguyên. Đây là mảnh mã minh họa đặt trong một phương thức. Nếu đầu vào từ các hệ thống khác nhau, hãy chuyển tất cả về cùng đơn vị **trước khi tính**, đồng thời nêu rõ nơi phép chuyển đổi được thực hiện.

Java có các kiểu làm rõ một số miền dữ liệu, ví dụ `java.time.Duration` cho khoảng thời gian. Khi dùng `Duration`, không nên coi hai tham số `long` tùy ý mang cùng ý nghĩa. Các API như `Duration.ofSeconds(3)` và `Duration.ofMillis(500)` nói rõ đơn vị ngay ở chỗ tạo dữ liệu.

## 3. Độ phân giải và chính sách decimal thuộc hợp đồng trường

Ngay cả khi biết **đơn vị** của một số, API vẫn phải quy định bước nhỏ nhất, ví dụ 0,001 kg hoặc 0,01 đơn vị tiền. `BigDecimal.scale()` là bậc lũy thừa 10 của biểu diễn, còn `precision()` là số chữ số có nghĩa của hệ số. Chúng không tự định nghĩa đơn vị hoặc quy tắc nghiệp vụ.

```java
BigDecimal value = new BigDecimal("12.30");
System.out.println(value.scale());     // 2
System.out.println(value.precision()); // 4
BigDecimal accepted = value.setScale(2, RoundingMode.UNNECESSARY);
```

Với yêu cầu **không được làm tròn**, `setScale(2, RoundingMode.UNNECESSARY)` chấp nhận `12.30` nhưng từ chối `12.345`. Điều này kiểm tra khả năng biểu diễn ở độ phân giải 0,01, không buộc token gốc có đúng hai ký tự sau dấu thập phân. Nếu hệ thống cho phép làm tròn, phải ghi rõ thời điểm và `RoundingMode` (không mặc định một mode cho mọi đồng tiền). Bài `Đưa precision, scale và rounding vào hợp đồng` triển khai đầy đủ chính sách và các phép chia.

Đối với tiền, số nguyên của **đơn vị nhỏ nhất** có thể thích hợp nếu đồng tiền và tỉ lệ giữa hai đơn vị được đặc tả. Không tự giả định mọi đồng tiền có đúng hai chữ số thập phân hoặc có thể cộng số tiền khác đơn vị.

## 4. Giới hạn phải bao phủ cả phép tính trung gian

Miền của mỗi trường có thể nhỏ nhưng **tổng hoặc tích** vượt kiểu primitive của toán hạng. Nếu số lượng tối đa là 50.000 và đơn giá tối đa là 100.000 đơn vị nhỏ, tích có thể đến 5.000.000.000, vượt `int`:

```java
int quantity = 50_000;
int unitPriceMinor = 100_000;
long correct = (long) quantity * unitPriceMinor; // 5000000000
```

Thăng hạng phải xảy ra **trước khi nhân**, không đợi tới biến nhận kết quả. Sử dụng `Math.multiplyExact`, `Math.addExact`, `Math.toIntExact` hoặc kiểu `BigInteger` theo giới hạn của nghiệp vụ. Nếu kết quả cuối nhỏ nhưng bước trung gian tràn, phải xác định hợp đồng từ chối trung gian hay tính với kiểu rộng rồi xét kết quả cuối.

Bài `Chọn kiểu tính toán trung gian riêng với kiểu lưu trữ` phân tích đầy đủ `IntStream`, các phép toán trung gian, thu hẹp và ranh giới lưu trữ.

## 5. Cùng hợp đồng phải có hiệu lực từ Java đến hệ thống nhận

Hợp đồng dữ liệu số cần khớp giữa model Java, DTO/API, JSON, cơ sở dữ liệu và biểu diễn trong file. Một `long` hợp lệ tại backend có thể vượt `Number.MAX_SAFE_INTEGER` của client JavaScript; nếu cần số nguyên chính xác hai chiều, phải thống nhất schema, ví dụ truyền chuỗi decimal với parser tương ứng. Một cột `DECIMAL(p,s)` vẫn cần quy tắc nghiệp vụ về miền, dấu, precision, scale.

Các giao diện có thể nhập **mét** nhưng model lưu **milimét**; phải chuyển đơn vị ở ranh giới rõ ràng, kiểm tra tràn và xuất đúng đơn vị được công bố. Không được tự dùng chuỗi đã phân nhóm hoặc làm tròn cho người đọc làm payload chuẩn. Bài `Tách định dạng hiển thị khỏi tính toán và trao đổi` mở rộng phần giao thức và locale.

## 6. Từ trường primitive đến kiểu đại diện miền dữ liệu

Trường `long` có thể phù hợp khi code nhỏ và mọi nơi cùng hiểu hợp đồng. Với API quan trọng, có thể bọc giá trị trong một **value object** để kiểm tra miền ngay lúc tạo:

```java
record DistanceMillimeters(long value) {
    static final long MAX_VALUE = 10_000_000L; // Ví dụ giới hạn 10 km.

    DistanceMillimeters {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("distance out of range");
        }
    }
}
```

Đây là một `record` Java 17 và là **mảnh mã minh họa**. Constructor compact được gọi khi tạo record và từ chối mọi giá trị ngoài miền. `DistanceMillimeters` giúp hạn chế việc truyền nhầm milli giây cho tham số quãng đường; compiler chỉ nhìn `long` sẽ không có khả năng phân biệt hai đại lượng.

Có thể mở rộng value object để nhận đầu vào theo mét rồi chuyển sang milimét:

```java
static DistanceMillimeters fromMeters(String meters) {
    long mm = new BigDecimal(meters)
            .movePointRight(3)
            .longValueExact();
    return new DistanceMillimeters(mm);
}
```

Phương thức này nằm **bên trong** record `DistanceMillimeters` và cần import `java.math.BigDecimal`. `movePointRight(3)` chuyển mét sang milimét; `longValueExact()` từ chối nếu có phần lẻ nhỏ hơn 1 milimét (mà không thể chuyển chính xác sang số nguyên milimét) hoặc nếu số ngoài miền `long`. Constructor tiếp tục từ chối giá trị âm hoặc vượt 10.000.000 milimét.

Ví dụ `fromMeters("1.250")` lưu 1.250 mm; `fromMeters("0.0005")` bị từ chối vì tương đương 0,5 mm và hợp đồng này **không cho phép làm tròn**. Nếu sản phẩm muốn cho phép làm tròn nửa milimét, phải mô tả `RoundingMode` và quyết định đó trước khi triển khai.

Các đơn vị vật lý có thể có cả giá trị âm (ví dụ nhiệt độ Celsius) hoặc không (ví dụ độ dài). Không nên áp quy tắc “mọi đại lượng không được âm” một cách mặc định; đặt giới hạn đúng với từng miền.

## 7. Chương trình Java 17+ hoàn chỉnh: ràng buộc đơn vị, miền và độ phân giải

Ví dụ sau giả định một miền giá có **hai chữ số thập phân**, cùng một đồng tiền, chỉ nhận giá **không âm** và không vượt 1.000.000 đơn vị tiền lớn. Quãng đường được nhập bằng **mét**, lưu dưới dạng **milimét**, không âm và không vượt 10 km. Các giới hạn được chọn để minh họa cách viết hợp đồng, không phải quy tắc chung của thương mại hoặc đo lường.

Lưu tệp **`NumericContractDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Objects;

public final class NumericContractDemo {
    private NumericContractDemo() {}

    record PriceCents(long value) {
        static final long MAX_CENTS = 100_000_000L;

        PriceCents {
            if (value < 0 || value > MAX_CENTS) {
                throw new IllegalArgumentException("price out of range");
            }
        }

        static PriceCents fromDecimal(String text) {
            Objects.requireNonNull(text, "price text");
            long cents = new BigDecimal(text)
                    .movePointRight(2)
                    .longValueExact();
            return new PriceCents(cents);
        }

        PriceCents plus(PriceCents other) {
            Objects.requireNonNull(other, "other");
            return new PriceCents(Math.addExact(value, other.value()));
        }

        String asDecimal() {
            return BigDecimal.valueOf(value, 2).toPlainString();
        }
    }

    record DistanceMillimeters(long value) {
        static final long MAX_MM = 10_000_000L;

        DistanceMillimeters {
            if (value < 0 || value > MAX_MM) {
                throw new IllegalArgumentException("distance out of range");
            }
        }

        static DistanceMillimeters fromMeters(String text) {
            Objects.requireNonNull(text, "meters text");
            long mm = new BigDecimal(text)
                    .movePointRight(3)
                    .longValueExact();
            return new DistanceMillimeters(mm);
        }

        String asMeters() {
            return BigDecimal.valueOf(value, 3).toPlainString();
        }
    }

    public static void main(String[] args) {
        PriceCents a = PriceCents.fromDecimal("12.34");
        PriceCents b = PriceCents.fromDecimal("2.66");
        System.out.println("total price = " + a.plus(b).asDecimal());

        DistanceMillimeters distance =
                DistanceMillimeters.fromMeters("1.250");
        System.out.println("distance m = " + distance.asMeters());

        try {
            PriceCents.fromDecimal("1.234");
        } catch (ArithmeticException ex) {
            System.out.println("price precision = rejected");
        }

        try {
            DistanceMillimeters.fromMeters("10000.001");
        } catch (IllegalArgumentException ex) {
            System.out.println("distance limit = rejected");
        }

        try {
            PriceCents.fromDecimal("-1.00");
        } catch (IllegalArgumentException ex) {
            System.out.println("negative price = rejected");
        }

        long widened = (long) Integer.MAX_VALUE + 1;
        System.out.println("int widened sum = " + widened);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 NumericContractDemo.java
java NumericContractDemo
```

Stdout kỳ vọng:

```text
total price = 15.00
distance m = 1.250
price precision = rejected
distance limit = rejected
negative price = rejected
int widened sum = 2147483648
```

**Giải thích:**

- `"12.34"` và `"2.66"` được nhân chính xác với 100 và lưu dưới dạng `long` cent. Tổng 1.500 cent hiển thị thành `15.00`.
- `"1.250"` mét chuyển chính xác thành 1.250 milimét. Khi hiển thị bằng `BigDecimal.valueOf(value, 3)`, nó trở về `1.250`.
- `"1.234"` ở đơn vị tiền lớn tương ứng 123,4 cent; `longValueExact()` từ chối vì phần nhỏ hơn 1 cent. Hợp đồng ví dụ **không làm tròn**.
- `"10000.001"` mét tương ứng 10.000.001 mm, vượt giới hạn 10.000.000 mm nên bị từ chối.
- `"-1.00"` bị từ chối do miền không cho phép giá âm, mặc dù `long` hoàn toàn biểu diễn được -100.
- Biểu thức `(long) Integer.MAX_VALUE + 1` chuyển sang `long` **trước phép cộng**, vì vậy không tràn `int`.

Chương trình này dùng `record` có từ Java 16, tương thích Java 17+. Nó cố ý không chứa mã tiền tệ hoặc quy tắc quy đổi tỷ giá vì hợp đồng ví dụ giả định **một loại tiền đã được thống nhất**. Nếu ứng dụng xử lý nhiều tiền tệ, kiểu giá phải gắn rõ mã tiền tệ và không cho phép cộng trực tiếp khác mã khi chưa có quy tắc đổi.

## 8. Mô tả trường số trong API và tài liệu

Một đặc tả rõ ràng có thể được viết thành bảng để frontend, backend, kiểm thử và lưu trữ cùng sử dụng:

| Trường | Kiểu truyền | Ý nghĩa/đơn vị | Miền/độ phân giải | Khi không hợp lệ |
| --- | --- | --- | --- | --- |
| `quantity` | JSON integer | sản phẩm | 0–10.000, bước 1 | Trả lỗi miền |
| `timeoutMillis` | JSON integer | mili giây | 1–120.000, bước 1 | Trả lỗi miền |
| `distanceMillimeters` | JSON integer | milimét | 0–10.000.000, bước 1 | Trả lỗi miền |
| `priceMinorUnits` | JSON integer hoặc chuỗi số nếu client cần độ chính xác 64 bit | đơn vị nhỏ của một đồng tiền đã xác định | 0–100.000.000, bước 1 | Trả lỗi miền |
| `discountBasisPoints` | JSON integer | basis point | 0–10.000, bước 1 (= 0,01%) | Trả lỗi miền |
| `massKg` | chuỗi số decimal | kilôgam | 0–1.000, bước 0,001 kg | Từ chối số quá chi tiết |

Mọi miền ví dụ đều **bao gồm hai đầu biên**. Nếu chọn JSON string để bảo toàn số nguyên lớn, tài liệu API phải ghi **kiểu dữ liệu thực tế** là chuỗi và định nghĩa định dạng hợp lệ; không được ghi JSON integer rồi âm thầm trả chuỗi.

Ví dụ JSON minh họa một đối tượng số lượng và thời gian:

```json
{
  "quantity": 12,
  "timeoutMillis": 5000,
  "distanceMillimeters": 1250
}
```

Tài liệu phải nói rõ `distanceMillimeters: 1250` là 1,25 mét, không phải 1.250 mét. Trường có hậu tố đơn vị thường giảm nhầm lẫn đáng kể khi tích hợp.

Nếu có trường nullable, cần ghi rõ cách diễn giải: `null` là “không biết” hay “không áp dụng”, nó có bị từ chối ở request hay được giữ lại trong database, và **không** dùng `0` hoặc `-1` làm sentinel tùy tiện nếu những giá trị này có thể hợp lệ. Một trường hợp khác là **giá trị không đo được**; có thể cần kiểu trạng thái riêng để phân biệt `NOT_MEASURED`, `UNAVAILABLE` và giá trị số thực.

## 9. Kiểm tra giá trị ở đúng ranh giới

Ràng buộc dữ liệu nên xuất hiện ở nơi tạo/nhận giá trị từ bên ngoài: khi parse request, tạo value object, chuyển đổi đơn vị hoặc ghi xuống kho dữ liệu.

Một chuỗi xử lý thường hợp lý:

1. **Nhận đầu vào:** xác định loại dữ liệu, đơn vị, locale và quy tắc thiếu giá trị.
2. **Parse không làm mất chính xác:** ưu tiên `BigDecimal(String)` cho dữ liệu decimal; tránh đi qua `double` nếu cần decimal chính xác.
3. **Chuyển về đơn vị chuẩn:** nhân/chia theo hệ số xác định bằng arithmetic phù hợp; kiểm tra độ phân giải và làm tròn nếu hợp đồng có cho phép.
4. **Kiểm tra miền:** min/max và các ràng buộc của nghiệp vụ; thất bại rõ khi không hợp lệ.
5. **Lưu trữ và xử lý:** giữ nguyên đơn vị chuẩn; không lặp đi lặp lại chuyển đổi bằng những quy tắc khác nhau.
6. **Trả dữ liệu:** phản ánh đúng đơn vị và độ chính xác; cập nhật API schema tương ứng.

`Objects.requireNonNull` dùng khi trường bắt buộc; với đầu vào không hợp lệ từ người dùng, API có thể chuyển lỗi parse/miền sang một mã lỗi có cấu trúc như `INVALID_FORMAT`, `OUT_OF_RANGE`, `INVALID_PRECISION`, `UNSUPPORTED_UNIT`. Không dựa vào thông báo nguyên văn của JDK làm hợp đồng công khai vì thông báo có thể thay đổi.

Đối với đối tượng bất biến, đưa kiểm tra vào constructor/factory giúp tránh tạo trạng thái không hợp lệ. Nhưng vẫn nên có xác thực ở API boundary để trả về thông báo dễ hiểu thay vì để lỗi lan sâu.

## 10. Những quyết định dễ bị bỏ sót

### Không dựa vào tên kiểu nguyên thủy để suy ra đại lượng

`long` không có nghĩa là “mili giây”, cũng không có nghĩa là “giá tiền lớn”. Nếu nhiều tầng dùng cùng một `long` cho quãng đường, thời gian và số tiền, cần tên trường, value object hoặc schema làm rõ sự khác biệt.

### Không tự xem `null` là 0

`0` có thể là dữ liệu hợp lệ; `null` có thể là thiếu dữ liệu. Một model có thể quy định giá tối thiểu là 0, đồng thời một trường giá nhập chưa được cung cấp là không hợp lệ hoặc chưa xác định. Tài liệu cần nói rõ.

### Không đồng nhất “hai số như nhau” với “hai chuỗi biểu diễn như nhau”

Với `BigDecimal`, `"1.0"` và `"1.00"` có cùng giá trị số nhưng khác `scale`. `compareTo` trả 0, còn `equals` trả `false`. Nếu scale hoặc số chữ số thập phân là một phần của dữ liệu, quy tắc equality phải được mô tả rõ; nếu chỉ cần số học, tránh vô tình dùng `equals` để quyết định hai số tiền khác nhau.

### Không làm tròn mà không có hợp đồng

Một API chấp nhận giá `"1.234"` rồi âm thầm biến thành `"1.23"` có thể khác với yêu cầu từ chối độ phân giải không hỗ trợ. Quy tắc làm tròn cần định nghĩa cả rounding mode, thời điểm làm tròn và đơn vị đích.

### Không chấp nhận mọi giá trị `double` theo mặc định

`Double.NaN`, `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` có thể truyền qua kiểu `double`, dù một đại lượng nghiệp vụ yêu cầu số hữu hạn. Có thể dùng `Double.isFinite(value)` khi hợp đồng chỉ nhận số hữu hạn, đồng thời kiểm tra min/max phù hợp. Với tiền tệ decimal chính xác, dùng kiểu dữ liệu decimal thích hợp.

### Không trông chờ database tự thay thế quy tắc nghiệp vụ

Kiểu SQL xác định miền biểu diễn, nhưng giới hạn số lượng, đơn vị hoặc “tỷ lệ không vượt 100%” cần được xác minh trong ứng dụng và/hoặc ràng buộc database. Không nên để frontend, backend và database mỗi nơi chọn một biên khác nhau.

## 11. Bộ kiểm thử hợp đồng số

Một bộ kiểm thử có giá trị cần thử **tại biên và ngoài biên**, thay vì chỉ dùng các giá trị bình thường. Với trường `quantity` từ 0 đến 10.000:

| Giá trị | Kỳ vọng |
| --- | --- |
| `0` | Hợp lệ nếu biên dưới được bao gồm |
| `1` | Hợp lệ |
| `10_000` | Hợp lệ nếu biên trên được bao gồm |
| `-1` | Bị từ chối |
| `10_001` | Bị từ chối |
| `null` | Theo hợp đồng bắt buộc/nullable |
| `"1.5"` | Bị từ chối nếu chỉ nhận số nguyên |

Với trường có đơn vị và độ phân giải, thử thêm: `1.250 m` → 1.250 mm; `0.0005 m` → từ chối nếu không được làm tròn; 10.000 m là biên tối đa và 10.000,001 m vượt giới hạn. Với tiền 2 chữ số decimal: thử `"0"`, `"0.01"`, `"1000000.00"`, `"1000000.01"`, `"-0.01"` và `"1.234"`. Kiểm tra cả phép tính trung gian (cộng/nhân vượt `int`, `long`), chuyển sang kiểu nhỏ hơn và serialize đến client JavaScript.

Nếu yêu cầu “tổng cuối phải nằm trong phạm vi” khác với “không phép cộng trung gian nào được vượt phạm vi”, cần kiểm thử trường hợp các số dương và âm triệt tiêu nhau. Điều này giúp khẳng định chính xác hợp đồng thay vì chỉ dựa vào tình cờ của cách triển khai.

## 12. Checklist dùng cho mọi trường số mới

Trước khi phát hành một trường số trong API hay lưu trữ, trả lời được:

- **Nó đo/cố định đại lượng gì?** Đây là số lượng, thời gian, tiền hay mã định danh (mã định danh dạng số chưa chắc thích hợp cho arithmetic)?
- **Đơn vị là gì?** Tên trường và tài liệu có thể hiện cùng một đơn vị chuẩn không? Nếu có chuyển đổi, quy tắc là gì?
- **Giá trị nhỏ nhất/lớn nhất hợp lệ là bao nhiêu?** Biên đóng hay mở, có cho phép âm hoặc 0 không?
- **Độ phân giải và scale là gì?** Có được làm tròn không và dùng quy tắc nào?
- **Giá trị thiếu có nghĩa gì?** `null`, trường vắng mặt, số 0 và sentinel có bị lẫn nhau không?
- **Phép tính trung gian có an toàn không?** Nâng kiểu trước cộng/nhân; dùng kiểm tra exact hoặc `BigInteger` khi cần.
- **Các lớp có hiểu giống nhau không?** Java, JSON/client, SQL, CSV và tài liệu có thống nhất đơn vị, biên, precision và cách xử lý lỗi không?
- **Kiểm thử biên đã có chưa?** Gồm biên, ngoài biên, chuyển đổi có phần dư, overflow và serializing giá trị lớn.

**Thông điệp chính:** Kiểu dữ liệu nói “máy tính lưu giá trị như thế nào”; hợp đồng số còn phải nói **giá trị nào hợp lệ, nó mang đơn vị gì, chính xác tới đâu, và khi vượt giới hạn thì hệ thống làm gì**. Ghi rõ các điều đó từ đầu giúp tránh lỗi âm thầm trong tính toán, API và lưu trữ.

### Tài liệu tham khảo Java SE 21

- [`Integer` và giới hạn kiểu `int`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html)
- [`Long` và giới hạn kiểu `long`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html)
- [`BigDecimal`: scale, precision, conversion exact](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [`Math`: addExact, multiplyExact, toIntExact](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [`Duration`: biểu diễn và chuyển đổi thời lượng](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Duration.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-002">Chọn kiểu tính toán trung gian riêng với kiểu lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kiểu tính toán trung gian riêng với kiểu lưu trữ trong Java

## 1. Vì sao không nên dùng một kiểu số cho mọi giai đoạn?

Khi thiết kế dữ liệu số, cần phân biệt **kiểu của giá trị được lưu** với **kiểu dùng trong phép tính trung gian**. Một thuộc tính có thể vừa vặn trong `int` hoặc `long` ở trạng thái cuối, nhưng phép tính tạo ra nó có thể cần phạm vi rộng hơn, độ chính xác khác hoặc một cách biểu diễn khác.

Ví dụ số lượng và đơn giá đều là `int`:

```java
int quantity = 50_000;
int unitPrice = 50_000;
long total = quantity * unitPrice; // Sai: phép nhân int đã tràn trước khi gán.
```

Giá trị toán học mong muốn là `2_500_000_000`, lớn hơn `Integer.MAX_VALUE` (`2_147_483_647`). Do cả hai toán hạng là `int`, Java **nhân bằng `int` trước**, kết quả tràn thành `-1_794_967_296`, rồi mới mở rộng sang `long`. Việc gán vào biến `long` không sửa được phép tính đã tràn.

Cách đúng khi miền giá trị tổng nằm trong `long`:

```java
long total = (long) quantity * unitPrice; // 2_500_000_000
```

**Quy tắc nền tảng:** xác định giới hạn và độ chính xác ở *từng bước tính*, rồi chọn kiểu thực hiện phép toán; chỉ ở ranh giới lưu trữ hoặc xuất dữ liệu mới kiểm tra và chuyển về kiểu đích. Không giả định kiểu của biến nhận kết quả sẽ quyết định kiểu của phép tính.

## 2. Hai hợp đồng cần xác định

### Hợp đồng của phép tính

Cần trả lời:

- Phạm vi cực đại, cực tiểu của **các kết quả trung gian** là bao nhiêu?
- Có phép cộng, nhân, tổng hợp hàng triệu bản ghi, bình phương, tỷ lệ hoặc chia tạo phần thập phân không?
- Phép toán cần chính xác tuyệt đối, hay chấp nhận sai số dấu phẩy động?
- Nếu vượt phạm vi, chương trình phải ném lỗi, đổi sang kiểu lớn hơn hay có quy tắc làm tròn?
- Có đơn vị nào phải giữ nhất quán, chẳng hạn **đồng** so với **xu**, **mét** so với **milimét**, **giây** so với **nano giây**?

### Hợp đồng của lưu trữ

Cần trả lời:

- Trường là `int`/`long` của Java, `INTEGER`/`BIGINT`/`DECIMAL(p,s)` của cơ sở dữ liệu, hay chuỗi số trong giao diện JSON?
- Giá trị cuối cần giữ đến chữ số thập phân nào, có thể âm không, và có cần bảo toàn scale không?
- Có yêu cầu tương thích schema, index, truy vấn, kích thước dữ liệu hay giao tiếp với hệ thống khác không?
- Chuyển đổi thu hẹp có được phép làm tròn không? Dữ liệu ngoài phạm vi phải được từ chối như thế nào?

Chỉ sau khi nêu rõ hai hợp đồng mới chọn được kiến trúc. Có thể dùng `int` để lưu năm sinh, `long` để lưu tổng số lượt; dùng `long` lưu tiền ở đơn vị nhỏ nhất nhưng tính thuế bằng `BigDecimal`; hoặc dùng `BigInteger` để cộng các giá trị cực lớn trước khi xác nhận kết quả cuối có thể lưu bằng `long`.

## 3. Miền giá trị của những kiểu số thông dụng

| Kiểu Java | Miền và đặc tính | Vai trò thường gặp |
| --- | --- | --- |
| `byte` | -128 đến 127 | Dữ liệu nhị phân hoặc giao thức, ít khi là kiểu phép tính tổng hợp |
| `short` | -32.768 đến 32.767 | Lưu các trường có miền nhỏ; phép toán số học thường được nâng thành `int` |
| `int` | -2³¹ đến 2³¹-1 | Đếm hoặc thuộc tính nhỏ, số nguyên thông dụng |
| `long` | -2⁶³ đến 2⁶³-1 | Bộ đếm lớn, tổng và tích vượt phạm vi `int` |
| `float` | IEEE 754 32-bit; độ chính xác hữu hạn | Số đo xấp xỉ khi có yêu cầu tài nguyên cụ thể |
| `double` | IEEE 754 64-bit; độ chính xác xấp xỉ 15–17 chữ số thập phân có nghĩa | Thống kê, khoa học, tỷ lệ chấp nhận sai số |
| `BigInteger` | Số nguyên có độ lớn giới hạn chủ yếu bởi tài nguyên | Phép toán nguyên vượt `long` |
| `BigDecimal` | Thập phân với số nguyên không chia tỷ lệ và `scale`; độ chính xác theo phép toán/`MathContext` | Tài chính, decimal chính xác và rounding có kiểm soát |

`float` và `double` có phạm vi độ lớn rất rộng nhưng **không biểu diễn chính xác mọi số nguyên hoặc số thập phân** trong phạm vi đó. Chọn `double` chỉ vì nó chứa được số “rất lớn” có thể làm mất chữ số nguyên thấp. Ngược lại, dùng `BigDecimal` cho một phép đo vật lý vốn có sai số đầu vào không tự tạo ra độ chính xác chưa từng tồn tại.

Không chọn kiểu chỉ dựa vào giá trị hiện tại. Cần tính cả **biên của phép toán trung gian**, số lượng dữ liệu tương lai, dấu âm, các lần nhân hệ số và giới hạn khi gộp dữ liệu.

## 4. Cơ chế thăng hạng kiểu và tràn số trước khi gán

Trong Java, kết quả của phép cộng, trừ, nhân trên `byte`, `short`, `char` thường được thăng hạng thành `int`. Khi hai toán hạng là `int`, phép toán vẫn có kiểu `int`. Có một toán hạng `long` trong phép toán số nguyên thì phép tính được thực hiện bằng `long`, miễn không có phép toán con nào đã tràn trước đó.

```java
int x = 50_000;
int y = 50_000;

long wrong = x * y;          // -1_794_967_296: int đã tràn
long right = (long) x * y;   //  2_500_000_000: nhân bằng long
long also = 1L * x * y;      //  2_500_000_000: literal long
long stillWrong = (long) (x * y); // tràn xảy ra trong dấu ngoặc
```

Ép kiểu **sau** phép tính không thể phục hồi phần dữ liệu đã mất.

Với tổng:

```java
int a = 2_000_000_000;
int b = 1_000_000_000;

long wrongSum = a + b;          // -1_294_967_296
long rightSum = (long) a + b;   //  3_000_000_000
```

Phép chia cũng chịu kiểu của toán hạng:

```java
int sum = 5;
int count = 2;

double wrongAverage = sum / count;         // 2.0: đã chia nguyên
double rightAverage = (double) sum / count; // 2.5
```

Đối với một biểu thức phức tạp, mở rộng kiểu tại **đúng phép toán cần miền rộng**. Ví dụ `(long) quantity * unitPrice` mở rộng ngay phép nhân; còn `(long) (quantity * unitPrice)` mở rộng quá muộn.

### Java không tự báo lỗi mọi phép toán tràn

Các phép cộng/trừ/nhân nguyên `int` và `long` thông thường có thể tràn và cho kết quả theo số học mô-đun 2^32 hoặc 2^64, thay vì ném ngoại lệ. Để phát hiện tràn khi điều đó là lỗi, dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.incrementExact`, `Math.negateExact` hoặc `Math.toIntExact` theo kiểu phù hợp.

```java
long valid = Math.multiplyExact((long) 50_000, 50_000); // 2_500_000_000
int invalid = Math.multiplyExact(50_000, 50_000);       // ArithmeticException
```

Dòng thứ hai cố ý minh họa ngoại lệ; nếu thực thi nối tiếp mà không `try/catch`, chương trình dừng ở đó. `multiplyExact(int,int)` phát hiện tràn `int`, còn `multiplyExact(long,long)` phát hiện tràn `long`. Việc mở rộng lên `long` không phải bảo đảm không thể tràn trong mọi bài toán.

## 5. Chương trình Java 17+ minh họa lỗi và cách xử lý

Lưu tệp `IntermediateIntegerDemo.java`:

```java
import java.util.stream.IntStream;

public class IntermediateIntegerDemo {
    public static void main(String[] args) {
        int quantity = 50_000;
        int unitPrice = 50_000;

        long overflowed = quantity * unitPrice;
        long widened = (long) quantity * unitPrice;
        long exact = Math.multiplyExact((long) quantity, unitPrice);

        System.out.println("int multiplication: " + overflowed);
        System.out.println("long multiplication: " + widened);
        System.out.println("checked long product: " + exact);

        try {
            Math.multiplyExact(quantity, unitPrice);
        } catch (ArithmeticException e) {
            System.out.println("int overflow detected");
        }

        try {
            Math.toIntExact(exact);
        } catch (ArithmeticException e) {
            System.out.println("cannot store in int");
        }

        int intSum = IntStream.of(2_000_000_000, 1_000_000_000).sum();
        long longSum = IntStream.of(2_000_000_000, 1_000_000_000)
                .asLongStream().sum();

        System.out.println("IntStream sum: " + intSum);
        System.out.println("long stream sum: " + longSum);
    }
}
```

Chạy:

```bash
javac IntermediateIntegerDemo.java
java IntermediateIntegerDemo
```

Stdout chính xác:

```text
int multiplication: -1794967296
long multiplication: 2500000000
checked long product: 2500000000
int overflow detected
cannot store in int
IntStream sum: -1294967296
long stream sum: 3000000000
```

Ví dụ chỉ minh họa miền trung gian và tràn số; những giá trị trên không được diễn giải là “Java trả số ngẫu nhiên”. Kết quả âm đầu tiên và kết quả âm của `IntStream.sum()` là hậu quả xác định của phép toán nguyên 32-bit bị tràn.

Để tổng hợp số lượng lớn hơn phạm vi `long`, chuyển hẳn sang `BigInteger` hoặc tính trong miền đã được chứng minh an toàn. `LongStream.sum()` dùng `long` nhưng không tự bảo đảm chống tràn số.

## 6. Kiểu lưu trữ nhỏ không bắt buộc phép tính trung gian nhỏ

Giả sử một hệ thống lưu số điểm là `short` vì mỗi điểm từ 0 đến 100. Trung bình cộng cần một tổng lớn hơn nhiều khi có hàng triệu bản ghi; tính trực tiếp trong `short` là sai thiết kế ngay cả khi **kết quả trung bình cuối cùng** vẫn nằm trong 0..100.

Một ví dụ khác là số lượng từng dòng chỉ vừa trong `int`, nhưng tổng đơn hàng phải là `long`. Ta có thể lưu lại số lượng từng dòng bằng `int` và trả tổng bằng `long`. Không cần đổi tất cả cột dữ liệu sang `long` chỉ vì một phép cộng gộp có thể lớn.

Mặt khác, nếu phép tính trung gian dùng `long` và giá trị cuối cần lưu trong `int`, phải kiểm tra **miền đích ngay khi chuyển**:

```java
long total = 2_500_000_000L;
int unsafe = (int) total; // -1_794_967_296, thu hẹp bị mất dữ liệu

// Ném ArithmeticException thay vì lặng lẽ biến đổi:
int checked = Math.toIntExact(total);
```

Dòng `Math.toIntExact` sẽ ném ngoại lệ trong tình huống minh họa; những dòng này không phải chương trình đầy đủ chạy nối tiếp.

Với kiểu `short` và `byte` không có `Math.toShortExact`/`Math.toByteExact` chuẩn tương tự; có thể kiểm tra phạm vi trước:

```java
static short toShortExact(int value) {
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
        throw new ArithmeticException("Out of short range");
    }
    return (short) value;
}
```

Đoạn này là một phương thức có thể đặt bên trong class; không có `main` riêng. Nó giúp diễn đạt rõ **quy tắc thu hẹp**. Không dùng ép kiểu thu hẹp trực tiếp khi việc mất dữ liệu bị cấm.

## 7. Trường hợp cần BigInteger làm kiểu tính trung gian

`long` có giới hạn `Long.MAX_VALUE = 9_223_372_036_854_775_807`. Nếu đầu vào hoặc tổng/tích có thể vượt giới hạn đó, `BigInteger` cho phép phép tính nguyên không bị tràn theo kiểu cố định:

```java
import java.math.BigInteger;

public class BigIntegerIntermediateDemo {
    public static void main(String[] args) {
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE);

        System.out.println("Exact total: " + total);
        System.out.println("Beyond long: "
                + (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0));

        try {
            total.longValueExact();
        } catch (ArithmeticException e) {
            System.out.println("Cannot fit long");
        }
    }
}
```

Chạy:

```bash
javac BigIntegerIntermediateDemo.java
java BigIntegerIntermediateDemo
```

Stdout:

```text
Exact total: 9223372036854775808
Beyond long: true
Cannot fit long
```

`longValue()` cắt bớt các bit cao nếu giá trị nằm ngoài `long`, dẫn tới một kết quả sai nghĩa. `longValueExact()` mới là lựa chọn khi muốn kiểm tra phép chuyển về kiểu lưu trữ. Tương tự, `BigInteger.intValueExact()` giúp xác nhận giá trị vừa với `int`.

Không lạm dụng `BigInteger` cho mọi biến đếm: nó cấp phát đối tượng và có chi phí cao hơn phép tính primitive. Chọn `BigInteger` khi các phép tính thực sự có khả năng vượt `long` hoặc hợp đồng miền giá trị không thể đặt giới hạn an toàn.

## 8. double có thể rộng hơn int/long nhưng không giữ đúng mọi chữ số

`double` thường được dùng làm kiểu trung gian cho thống kê, tỷ lệ, vị trí, đo lường hoặc thuật toán xấp xỉ. Nó có dải độ lớn rộng nhưng chỉ **53 bit độ chính xác nhị phân** (bao gồm bit dẫn), nên chỉ biểu diễn chính xác mọi số nguyên trong một miền liên tiếp tới 2^53. Những số nguyên lớn hơn có thể bị làm tròn khi đổi thành `double`.

Ví dụ:

```java
long exactCount = 9_007_199_254_740_993L; // 2^53 + 1
double approximate = exactCount;
System.out.println(approximate == 9_007_199_254_740_992.0); // true
```

`approximate` không lưu được chữ số đơn vị của `exactCount`. Vì vậy, không nên đổi mọi tổng hoặc ID số nguyên lớn sang `double` chỉ để “tránh overflow”. Đặc biệt ID, số đơn vị nhỏ nhất và số lượng cần chính xác nên ở `long`/`BigInteger` khi phù hợp.

Thập phân cũng có vấn đề biểu diễn:

```java
double binary = 0.1 + 0.2;
System.out.println(binary); // 0.30000000000000004
```

Đây không phải phép tính lỗi của Java mà là đặc điểm IEEE 754: `0.1` và `0.2` không thể biểu diễn chính xác bằng hữu hạn bit nhị phân. Dùng `double` khi **sai số chấp nhận được và có chính sách kiểm thử**, còn khi cần decimal chính xác, dùng `BigDecimal` với đầu vào thích hợp.

Một vài điểm cần nhớ khi chuyển từ `double` sang số nguyên:

- `(int) doubleValue` có thể mất phần thập phân, bão hòa ở biên của kiểu đích khi vượt phạm vi và chuyển `NaN` thành 0; không phải một phép chuyển kiểm tra chính xác.
- `Math.round` áp quy tắc làm tròn riêng và vẫn cần xét giới hạn, không phải tương đương với chuyển đổi exact.
- Khi không chấp nhận phần lẻ hoặc giá trị không hữu hạn, hãy kiểm tra đầu vào và miền trước khi thu hẹp, hoặc dùng biểu diễn `BigDecimal` thích hợp nếu domain yêu cầu số thập phân.

## 9. BigDecimal cho phép tính thập phân, kiểu lưu trữ có thể khác

Một cách thiết kế phổ biến trong hệ thống tài chính là lưu **đơn vị nhỏ nhất** bằng `long` (ví dụ cents) nhưng thực hiện tính thuế, chiết khấu, lãi, tỷ lệ và làm tròn bằng `BigDecimal`.

Lý do:

1. `long` lưu được số nguyên đơn vị nhỏ nhất chính xác và phù hợp cho truy vấn/đối chiếu khi schema đặt miền hợp lệ.
2. Tính phần trăm hoặc chia tỷ lệ có thể tạo số với nhiều chữ số thập phân, dù giá trị lưu cuối chỉ có hai chữ số.
3. Chỉ làm tròn tại **ranh giới nghiệp vụ được định nghĩa**, rồi chuyển về đơn vị lưu trữ với kiểm tra exact và phạm vi.

Chẳng hạn: số tiền trước thuế là 37.02, thuế suất 7.5%; tiền thuế chính xác trước làm tròn là 2.77650, và làm tròn đến hai chữ số theo `HALF_UP` cho 2.78. Lưu trực tiếp từng bước vào `long` cents trước khi có chính sách rounding có thể làm mất chữ số cần cho tính toán.

### Chương trình Java 17+ hoàn chỉnh

Lưu tệp `MoneyIntermediateDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyIntermediateDemo {
    static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    static long toCentsExact(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    public static void main(String[] args) {
        long unitPriceCents = 1_234L;
        long quantity = 3L;

        long subtotalCents = Math.multiplyExact(unitPriceCents, quantity);
        BigDecimal subtotal = fromCents(subtotalCents);

        BigDecimal exactTax = subtotal.multiply(new BigDecimal("0.075"));
        BigDecimal bookedTax = exactTax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(bookedTax);
        long storedTotalCents = toCentsExact(total);

        System.out.println("Subtotal: " + subtotal);
        System.out.println("Tax before rounding: " + exactTax);
        System.out.println("Tax booked: " + bookedTax);
        System.out.println("Total: " + total);
        System.out.println("Stored cents: " + storedTotalCents);

        try {
            toCentsExact(new BigDecimal("1.005"));
        } catch (ArithmeticException e) {
            System.out.println("Fractional cent rejected");
        }
    }
}
```

Chạy:

```bash
javac MoneyIntermediateDemo.java
java MoneyIntermediateDemo
```

Stdout:

```text
Subtotal: 37.02
Tax before rounding: 2.77650
Tax booked: 2.78
Total: 39.80
Stored cents: 3980
Fractional cent rejected
```

Giải thích:

- `Math.multiplyExact` phát hiện tràn `long` khi nhân giá với số lượng.
- `BigDecimal.valueOf(cents, 2)` biểu diễn chính xác số tiền với scale 2.
- Nhân `BigDecimal` với chuỗi `"0.075"` giữ chính xác giá trị thập phân của thuế suất.
- `setScale(2, HALF_UP)` là **quy tắc làm tròn của ví dụ**, không phải quy tắc mặc định cho mọi hệ thống hay mọi đồng tiền.
- `movePointRight(2).longValueExact()` chỉ chuyển sang cents nếu giá trị là một số nguyên cents chính xác và trong phạm vi `long`. Giá trị `1.005` cents hóa thành `100.5`, nên bị từ chối thay vì lặng lẽ làm tròn.

Đây là ví dụ về ranh giới chuyển đổi, không phải phần mềm tính thuế hoàn chỉnh. Hệ thống thực tế phải xác định đơn vị tiền tệ, thời điểm làm tròn, chính sách thuế, cách phân bổ phần dư và biểu diễn lưu trữ hợp lệ.

## 10. Tính trung gian với precision và scale khác kiểu lưu

`BigDecimal` có `scale` (số chữ số sau dấu thập phân đối với scale không âm) và `precision` (số chữ số của số nguyên không chia tỷ lệ). Đầu vào, kết quả trung gian và đầu ra có thể có những scale khác nhau mà vẫn cùng giá trị toán học.

Ví dụ:

```java
BigDecimal amount = new BigDecimal("12.34");
BigDecimal ratio = new BigDecimal("0.075");
BigDecimal product = amount.multiply(ratio); // 0.92550

BigDecimal finalValue = product.setScale(2, RoundingMode.HALF_EVEN);
// 0.93
```

Đây là minh họa trong một phương thức với `BigDecimal`, `RoundingMode` đã import. Tính trung gian giữ thêm chữ số, sau đó chuyển sang scale lưu trữ ở **đúng điểm làm tròn**. Nếu mỗi bước đều `setScale(2, ...)`, sai số có thể tích lũy theo chuỗi phép tính và khiến kết quả cuối khác phép tính chỉ làm tròn ở cuối.

Cũng cần nhớ:

- `new BigDecimal("0.1")` biểu diễn đúng decimal 0.1; `new BigDecimal(0.1)` nhận một giá trị `double` nhị phân gần đúng.
- `divide(divisor)` yêu cầu thương thập phân hữu hạn chính xác; nếu thương như 1/3 không hữu hạn, Java ném `ArithmeticException`. Cần định nghĩa `scale`, `MathContext` hoặc `RoundingMode` phù hợp.
- `RoundingMode.UNNECESSARY` hữu ích khi hợp đồng cấm mọi mất chữ số có giá trị: nếu việc đổi scale/precision cần rounding thật thì phép chuyển sẽ ném lỗi.
- Lưu `BigDecimal` dưới dạng `DECIMAL(p,s)` trong database cần đối chiếu giới hạn **precision/scale** của cột và kiểm tra phía ứng dụng. Không nên giả định database sẽ luôn tự báo lỗi hoặc làm tròn theo chính sách Java.
- Khi nhận và xuất giá trị, phải thống nhất format, scale, đơn vị và chính sách xử lý số quá lớn hoặc quá nhiều chữ số.

## 11. Thiết kế miền số và ranh giới chuyển đổi

Thay vì cho phép mọi tầng tùy ý ép kiểu, nên mô tả các ranh giới rõ ràng:

```text
Dữ liệu lưu/nhập
    |
    | parse + kiểm tra miền, đơn vị, scale
    v
Kiểu tính toán trung gian đủ rộng/chính xác
    |
    | phép tính với kiểm tra overflow/rounding
    v
Kết quả nghiệp vụ
    |
    | kiểm tra giới hạn và chuyển kiểu exact
    v
Kiểu lưu trữ/DTO/schema đầu ra
```

Ví dụ với kho hàng:

- `quantityPerLine` có thể là `int` khi giới hạn mỗi dòng nhỏ và được validate.
- `totalQuantity` có thể là `long` nếu cộng nhiều dòng.
- `grandTotal` có thể dùng `BigDecimal` nếu áp giá, thuế, chiết khấu decimal.
- Dữ liệu lưu tổng có thể là `long` cents hoặc cột `DECIMAL(p,s)`, tùy schema/domain.
- Trước khi lưu, gọi phép chuyển exact hoặc validation; nếu vượt khả năng lưu, trả lỗi có ngữ cảnh, không cast và để sai dữ liệu.

Có thể đặt các phương thức `fromStorage(...)` và `toStorageExact(...)` trong kiểu domain hoặc adapter. Chúng chịu trách nhiệm thống nhất chính sách chuyển đổi, giúp tầng tính toán không phải biết DB dùng `BIGINT` hay `DECIMAL(18,2)`. Nếu một kết quả hợp lệ về nghiệp vụ nhưng không vừa kiểu lưu trữ, đó là dấu hiệu cần đánh giá lại **schema**, không mặc nhiên xóa phần dư để ép vừa.

### Đơn vị là một phần của hợp đồng

Hai giá trị kiểu `long` vẫn có thể hoàn toàn khác nghĩa:

```java
long amountCents = 1_234L;  // 12.34 đơn vị tiền lớn
long amountUnits = 1_234L;  // 1234 đơn vị tiền lớn
```

Cùng một kiểu primitive nhưng không thể cộng hoặc so sánh như thể cùng đơn vị. Với miền phức tạp, cân nhắc tạo `record`/class có tên và kiểm tra, chẳng hạn `MoneyCents`, `DurationMillis`, `Quantity`, thay vì chuyển đơn vị rải rác. Việc chuyển đơn vị có thể nhân gây overflow và phải sử dụng `Math.multiplyExact` hoặc kiểu rộng trước.

## 12. Cơ sở dữ liệu và giao diện trao đổi dữ liệu

Chọn kiểu lưu trữ cần xét đến các tầng bên ngoài Java.

| Nhu cầu | Kiểu tính toán có thể phù hợp | Kiểu lưu trữ có thể phù hợp | Rủi ro cần kiểm tra |
| --- | --- | --- | --- |
| Đếm sự kiện của từng ngày | `long` hoặc checked `BigInteger` nếu rất lớn | `BIGINT` | Tổng vượt `int` hoặc `long` |
| Tổng hợp số lượng từ nhiều dòng `int` | `long` | `INTEGER`/`BIGINT` tùy giới hạn tổng cuối | Cast thu hẹp làm sai số |
| Số tiền đơn vị nhỏ nhất | `long` cho cộng/nhân nguyên có kiểm tra; `BigDecimal` cho tỷ lệ | `BIGINT` cents | Mismatch đơn vị, overflow, rounding |
| Thuế/lãi/phí thập phân | `BigDecimal` | `DECIMAL(p,s)` hoặc đơn vị nhỏ nhất sau rounding | Mất scale, sai chính sách làm tròn |
| Thống kê xấp xỉ | `double` và thuật toán cộng phù hợp | `DOUBLE PRECISION` hoặc trường có quy ước precision | Sai số IEEE 754, NaN/Infinity |
| Số nguyên cực lớn | `BigInteger` | `DECIMAL(p,0)` đủ lớn hoặc chuỗi theo hợp đồng | Vượt precision/giới hạn schema |

`DECIMAL(12,2)` thông thường cho 12 chữ số precision, trong đó 2 chữ số thập phân, để lại tối đa 10 chữ số phần nguyên; cần xác nhận giới hạn cụ thể của database. Trước khi insert/update, kiểm tra kiểu lưu có đủ miền, scale và quy tắc rounding yêu cầu.

**JSON/JavaScript:** JavaScript `Number` cũng là số dấu phẩy động nhị phân (thường dùng IEEE 754 double). Nó không thể biểu diễn chính xác mọi số nguyên `long`. Với ID hoặc số nguyên vượt `Number.MAX_SAFE_INTEGER`, cân nhắc truyền chuỗi decimal hoặc encoding/schema có kiểu số nguyên lớn rõ ràng. Không giả định việc Java serialize một `long` thành JSON number giúp mọi client giữ được đầy đủ chữ số.

Tương tự, đổi từ `BigDecimal` sang `double` trước khi trả API có thể làm mất tính chính xác dù dữ liệu trong Java và DB vẫn đúng. Ranh giới giao tiếp cần hợp đồng riêng, không chỉ phụ thuộc vào kiểu nội bộ.

## 13. Tối ưu dung lượng và hiệu năng một cách có căn cứ

Kiểu lưu trữ nhỏ có thể giảm kích thước hoặc phù hợp schema, nhưng không nên tạo rủi ro tràn số trong xử lý chỉ để tiết kiệm bộ nhớ tạm.

Những câu hỏi đáng cân nhắc:

1. **Miền giá trị:** `int`, `long` hoặc `BigInteger` có đủ phạm vi cho phép tính lớn nhất?
2. **Độ chính xác:** phép tính cần exact decimal hay xấp xỉ nhị phân?
3. **Số bước làm tròn:** có thể trì hoãn rounding đến một ranh giới được xác định để giảm sai lệch?
4. **Tính bất biến/chi phí cấp phát:** `BigInteger` và `BigDecimal` là object immutable; phép toán tạo đối tượng mới và có chi phí so với primitive.
5. **Kiểu của collection/stream:** `IntStream.sum()` trả `int`; nếu cần `long` phải đổi bằng `mapToLong(...)` hoặc thuật toán tổng hợp phù hợp từ đầu.
6. **Khả năng quan sát lỗi:** dùng checked arithmetic và chuyển kiểu exact để phát hiện dữ liệu ngoài miền thay vì âm thầm sai.

Không tự tối ưu bằng cách giữ phép tính trong `int` nếu nghiệp vụ không thể bảo đảm miền. Nếu hiệu năng của `BigDecimal` là vấn đề đã đo được, có thể dùng đơn vị nguyên có scale cố định và checked arithmetic trong các bước phù hợp, đồng thời đảm bảo mọi phép áp tỷ lệ đều tuân theo rounding của domain.

## 14. Lỗi thường gặp

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| `long total = intA * intB` | Tràn `int` trước khi gán | `(long) intA * intB`, hoặc `Math.multiplyExact` với kiểu rộng |
| `(long) (intA * intB)` | Ép sau khi mất dữ liệu | Thăng hạng **trước** phép nhân |
| `double mean = intSum / intCount` | Chia nguyên, mất phần lẻ | `(double) intSum / intCount` và kiểm tra count |
| `int result = (int) longResult` | Có thể đổi thành giá trị không liên quan | `Math.toIntExact(longResult)` |
| Dùng `double` cho tất cả tổng số nguyên lớn | Mất chữ số nguyên ngoài miền chính xác | Giữ `long`/`BigInteger` |
| Tính tiền bằng `double` rồi lưu cents | Sai số nhị phân và rounding khó kiểm soát | Dùng `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất với hợp đồng rõ |
| Làm tròn tiền sau từng phép tính trung gian bất kể nghiệp vụ | Sai lệch tích lũy | Làm tròn tại ranh giới đã quy định |
| Dùng `BigDecimal.longValue()` để lưu không kiểm tra | Có thể bỏ phần lẻ/tràn | `longValueExact()` sau chuẩn hóa |
| Cho rằng `long` không thể tràn | Tổng/tích rất lớn có thể tràn | `Math.addExact`/`multiplyExact` hoặc `BigInteger` |
| Lưu `BigDecimal` vào `DECIMAL(p,s)` mà không validate | Rounding hoặc từ chối ngoài ý muốn | Kiểm tra precision/scale theo schema |
| Dùng cùng `long` cho cents và đơn vị lớn | Sai hệ số 100 | Kiểu domain/tên trường và chuyển đổi rõ ràng |
| Xuất `long` lớn qua JSON number và JavaScript `Number` | Client mất chữ số | Hợp đồng chuỗi decimal hoặc kiểu số lớn phù hợp |

## 15. Hướng dẫn chọn kiểu theo từng tình huống

**Tổng hợp số lượng:** nếu từng giá trị là `int` nhưng tổng có thể vượt `int`, tính bằng `long` ngay từ phép cộng; dùng checked addition nếu có nguy cơ vượt `long`. Chỉ lưu vào `int` sau khi `Math.toIntExact` thành công.

**Nhân số lượng với đơn giá:** xác định phạm vi lớn nhất của tích; nếu cả hai là `int` mà tích vượt `int`, ép một toán hạng sang `long` trước khi nhân. Với tích vượt `long`, dùng `BigInteger`. Kiểu lưu đơn giá có thể giữ nguyên nếu miền riêng vẫn phù hợp.

**Tỷ lệ hoặc trung bình:** nếu bài toán chỉ cần xấp xỉ, `double` và kiểm tra chia 0, NaN/Infinity là đủ trong nhiều trường hợp. Nếu cần chính xác decimal, dùng `BigDecimal`, nêu `scale` và `RoundingMode`, hoặc giữ tử/mẫu nguyên riêng cho đến khi cần làm tròn.

**Tiền tệ:** xác định đơn vị nhỏ nhất, đồng tiền, chính sách thuế/chiết khấu và rounding. Có thể lưu cents bằng `long` và dùng `BigDecimal` trung gian. Việc đổi về cents phải là phép kiểm tra exact; không giả định tất cả đồng tiền có hai chữ số.

**Giới hạn giao diện/API:** một giá trị Java lưu đúng trong `long`/`BigInteger` có thể vượt kiểu số của hệ thống nhận. Kiểm tra *toàn bộ đường đi*, không chỉ tính đúng trong Java.

**Giá trị có độ lớn biến thiên cực rộng:** tránh tràn trong biến trung gian khi tính bình phương hoặc lũy thừa. Có thể cần `BigInteger`/`BigDecimal`, hoặc một thuật toán khác tránh tạo kết quả trung gian quá lớn nếu kết quả cuối chỉ là phép so sánh.

## 16. Tự kiểm tra

1. **Vì sao `long x = 50_000 * 50_000` có thể sai?** Vì hai literal mặc định là `int`, phép nhân tràn trước khi gán. Dùng `50_000L * 50_000`.
2. **Ép `(long) (a * b)` có sửa được tràn `int` không?** Không. Phải mở rộng kiểu một toán hạng trước phép nhân.
3. **Nếu cần lưu kết quả `long` vào `int` mà không chấp nhận mất dữ liệu?** Dùng `Math.toIntExact` hoặc kiểm tra miền đầy đủ.
4. **Khi nào dùng `BigInteger`?** Khi phép tính nguyên cần chính xác và có thể vượt giới hạn `long`.
5. **Dùng `double` để chứa mọi số nguyên lớn có an toàn không?** Không; `double` không biểu diễn chính xác mọi số nguyên vượt 2^53.
6. **Vì sao tổng tiền có thể lưu cents bằng `long`, nhưng thuế được tính bằng `BigDecimal`?** Vì áp tỷ lệ tạo phần thập phân trung gian cần chính sách rounding; lưu cents chỉ phù hợp sau khi đã xử lý phần dư.
7. **`BigDecimal.longValueExact()` kiểm tra điều gì?** Giá trị phải là số nguyên chính xác và nằm trong miền `long`; không được lặng lẽ mất phần lẻ hoặc overflow.
8. **`IntStream.sum()` có tự đổi sang `long` khi tổng lớn không?** Không; nó trả `int`. Phải chọn phép tổng hợp `long` trước.
9. **Tại sao cần kiểm tra kiểu ở ranh giới JSON?** Vì số nguyên chính xác trong Java có thể không còn chính xác khi client dùng `Number`.
10. **Có nhất thiết mọi kiểu lưu trữ phải trùng với kiểu trung gian không?** Không; kiểu trung gian phục vụ độ đúng của phép tính, kiểu lưu phục vụ hợp đồng dữ liệu. Phải có phép chuyển đổi kiểm tra giữa hai tầng.

## 17. Ghi nhớ

**Chọn kiểu tính toán trung gian theo miền giá trị và độ chính xác lớn nhất của phép tính; chọn kiểu lưu trữ theo miền và biểu diễn của dữ liệu đã được chốt.** Kiểu của biến nhận không thay đổi kiểu đã dùng để đánh giá biểu thức. Muốn tránh tràn `int` phải thăng hạng trước phép toán; muốn phát hiện tràn hãy dùng checked arithmetic; muốn thu hẹp phải kiểm tra exact.

Đối với số nguyên lớn, dùng `long` hoặc `BigInteger` theo miền; đối với phép tính decimal chính xác, dùng `BigDecimal` và chính sách rounding rõ ràng; đối với số đo xấp xỉ, dùng `double` khi sai số đã được chấp nhận. Mọi chuyển đổi về schema lưu trữ, đơn vị nhỏ nhất và định dạng API phải được xem là một bước kiểm tra hợp đồng, tránh lưu dữ liệu “vừa kiểu” nhưng sai nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-003">Số đếm, đại lượng đo và giá trị thập phân có quy tắc làm tròn</a>

<details>
<summary>Xem chi tiết</summary>

# Số đếm, đại lượng đo và giá trị thập phân có quy tắc làm tròn

## 1. Vấn đề thiết kế: cùng là số, nhưng không cùng ý nghĩa

Trong Java, `int`, `long`, `double` và `BigDecimal` đều lưu được những dạng dữ liệu số. Tuy nhiên, chọn kiểu chỉ theo “giá trị này có dấu phẩy không?” thường dẫn đến lỗi nghiệp vụ. Hãy phân biệt ba loại thông tin:

1. **Số đếm (*count*)**: số lượng phần tử rời rạc, thường là số nguyên không âm, chẳng hạn 12 học viên, 7 lần thử, 300 lượt truy cập.
2. **Đại lượng đo (*measurement*)**: độ dài, khối lượng, thời gian, nhiệt độ, tốc độ… luôn có **đơn vị**, độ phân giải và đôi khi có sai số đo. Ví dụ `2.5 m` khác `2.5 cm`.
3. **Giá trị thập phân có quy tắc làm tròn (*rounded decimal value*)**: tiền, thuế, lãi, tỷ lệ được tính toán chính xác ở mức cần thiết rồi **chuẩn hóa scale và rounding mode theo quy tắc nghiệp vụ**.

Chúng có thể cùng hiển thị là `"2"` nhưng phải có quy tắc khác nhau:

| Dữ liệu | Ý nghĩa | Đại diện Java thường phù hợp | Bất biến/cách sử dụng |
| --- | --- | --- | --- |
| 2 sản phẩm trong giỏ | Số đếm | `int`, `long`, `BigInteger` | Không âm, nguyên; tránh tràn khi cộng. |
| 2 mét chiều dài | Đại lượng đo | `BigDecimal` hoặc `double` **kèm đơn vị** | Không được cộng sai đơn vị; xác định độ chính xác và sai số đo. |
| 2.00 USD tiền thanh toán | Giá trị tiền theo thỏa thuận | `BigDecimal` **kèm loại tiền và rounding policy** | Không trộn tiền tệ; làm tròn đúng thời điểm và đúng chế độ. |
| 2.5 sản phẩm tính theo khối lượng | Số lượng bán đo được | `BigDecimal` kèm `kg`, `lít`… | Không ép mọi “quantity” thành số đếm nguyên. |
| Tỷ lệ 2% | Tỷ lệ, không phải 2 đơn vị | `BigDecimal` hoặc `double` tùy bài toán | Chọn rõ biểu diễn 2% là `0.02` hay `2`, không trộn. |

**Mô hình dữ liệu phải nói lên ý nghĩa của số**, giới hạn giá trị, đơn vị, quy tắc toán học và thời điểm làm tròn. Kiểu số chỉ là một phần của thiết kế.

## 2. Số đếm: miền nguyên, ràng buộc và nguy cơ tràn

### 2.1. Vì sao số đếm thường dùng int hoặc long?

Số đếm các thực thể không chia nhỏ được thường thuộc tập `0, 1, 2, ...`. Các lựa chọn:

- `int`: số nguyên 32 bit có dấu, miền từ `-2^31` đến `2^31 - 1`. Hợp lý cho vòng lặp, số lượng nhỏ hoặc phạm vi đã biết.
- `long`: số nguyên 64 bit có dấu, từ `-2^63` đến `2^63 - 1`. Dùng cho lượt sự kiện lớn, bộ đếm dài hạn, tổng từ nhiều nguồn.
- `BigInteger`: số nguyên độ chính xác tùy ý khi yêu cầu vượt miền `long` hoặc kết quả toán học bắt buộc không tràn.

Một `long` cho phép biểu diễn số âm nhưng **miền nghiệp vụ “số mặt hàng” có thể không cho phép âm**. Kiểu và validation cùng chịu trách nhiệm:

```java
record ItemCount(long value) {
    ItemCount {
        if (value < 0) {
            throw new IllegalArgumentException("Count must be non-negative");
        }
    }
}
```

Đây là đoạn record minh họa, đặt trong lớp hoặc source hợp lệ. Bởi Java `record` là value carrier, validation trong compact constructor giúp mọi lần tạo `ItemCount` tuân thủ invariant. Một phép *delta* như `-3` lần điều chỉnh số lượng có thể cần **kiểu riêng `CountDelta`**, không ép nó vào `ItemCount` vốn chỉ cho giá trị không âm.

### 2.2. Tràn số không phải một phép cộng đúng theo nghiệp vụ

`int` và `long` có giới hạn. Phép cộng primitive thông thường khi tràn không ném exception; kết quả tràn theo quy tắc Java:

```java
int max = Integer.MAX_VALUE;
System.out.println(max + 1); // -2147483648
```

Trong nghiệp vụ số lượng, “từ số dương thành âm” thường là lỗi. `Math.addExact` phát hiện tràn:

```java
long result = Math.addExact(1_000_000L, 2_000_000L);
System.out.println(result); // 3000000
// Math.addExact(Long.MAX_VALUE, 1L) ném ArithmeticException.
```

Với dữ liệu có thể vượt `long`, dùng `BigInteger.add` hoặc áp đặt giới hạn miền hợp lệ theo hợp đồng. Không dùng `double` để “chứa được mọi số đếm rất lớn”: `double` có khoảng giá trị rất rộng nhưng không giữ chính xác mọi số nguyên vượt `2^53`.

### 2.3. Không cộng các count có ý nghĩa khác nhau

Tổng `3 người` và `5 lần đăng nhập` không phải `8 đơn vị` có ý nghĩa. Dù cùng là `long`, chúng nên được đặt tên rõ, phân lớp hoặc dùng record domain khi cần:

```java
record UserCount(long value) {}
record LoginAttemptCount(long value) {}
```

Đây chỉ là ví dụ biểu diễn loại dữ liệu, chưa có validation. Nếu thiết kế đầy đủ, kiểm tra số không âm và tránh vô tình cộng chéo kiểu. Một đối tượng `Count` tổng quát phù hợp khi các số đếm có cùng đơn vị/ý nghĩa; các số đếm khác miền không nên tự cộng chỉ vì Java cho phép toán tử `+`.

## 3. Đại lượng đo: trị số phải đi cùng đơn vị và ngữ cảnh độ chính xác

“`10`” không nói lên điều gì nếu nó có thể là `10 mét`, `10 inch` hoặc `10 miligiây`. Một đại lượng đo cần ít nhất:

- **Loại đại lượng**: độ dài, khối lượng, thời lượng, nhiệt độ, thể tích...
- **Đơn vị**: `m`, `cm`, `kg`, `ms`… theo quy ước nhất quán.
- **Giá trị** trong đơn vị đó.
- **Độ phân giải/sai số đo** khi có ý nghĩa: thước chia mm và cảm biến có sai số ±0.1 °C không cùng độ chính xác thực nghiệm.

Phép cộng chỉ hợp lệ khi các giá trị có đơn vị tương thích, sau khi chuyển đổi nếu cần:

```text
1.25 m + 75 cm
= 1.25 m + 0.75 m
= 2.00 m
```

Không thể cộng trực tiếp `1.25 + 75` rồi gắn hậu tố `m`. Tương tự, nhiệt độ Celsius là một ví dụ cho thấy **không phải mọi loại đại lượng có thể cộng/trừ đơn giản như độ dài**: nhiệt độ tuyệt đối và độ chênh nhiệt độ cần mô hình phân biệt trong nghiệp vụ.

### 3.1. Hai hướng biểu diễn measurement

**Hướng A — Lưu số nguyên theo đơn vị cơ sở đủ nhỏ.** Ví dụ thời lượng chính xác đến mili giây có thể lưu bằng `long milliseconds`; khoảng cách chính xác đến mm có thể lưu bằng `long millimeters`. Ưu điểm: các phép cộng đơn vị rời rạc chính xác và không bị lỗi dấu phẩy động. Đổi đơn vị có thể cần làm tròn; phải xác định quy tắc. Ngoài ra cần tránh overflow.

**Hướng B — Lưu BigDecimal hoặc double cùng đơn vị.** Ví dụ độ dài ghi tới `0.001 m` có thể biểu diễn bằng `BigDecimal("1.235")`; số đo vật lý từ cảm biến đôi khi hợp lý hơn với `double` do bản chất xấp xỉ, chi phí tính toán và thuật toán xử lý tín hiệu. Chọn `BigDecimal` **không làm số đo vật lý chính xác hơn thiết bị đo**: nó chỉ giúp biểu diễn và tính chính xác các chữ số decimal đã cung cấp.

Hãy cố định **đơn vị nội bộ thống nhất**, ví dụ luôn lưu mét và chỉ chuyển sang cm/inch ở giao diện. Khi chuyển đổi có hệ số chính xác trong hệ thập phân như `cm → m = chia 100`, `BigDecimal.movePointLeft(2)` thực hiện phép đổi chính xác:

```java
BigDecimal centimeters = new BigDecimal("125.0");
BigDecimal meters = centimeters.movePointLeft(2);
System.out.println(meters.toPlainString()); // 1.250
```

Giá trị kết quả có scale 3, phản ánh việc dịch dấu thập phân trên biểu diễn hiện tại. Đây không phải cam kết rằng phép đo có ba chữ số thập phân mang ý nghĩa thực nghiệm; ý nghĩa đo đạc phải được mô tả riêng.

### 3.2. Số đo có bất định, không chỉ có nhiều chữ số

Giả sử cảm biến ghi `1.234 m` nhưng sai số đo khoảng ±`0.01 m`. Nếu in `1.234000000 m`, số chữ số mới chỉ là *trình bày*, không làm sai số giảm. Trong báo cáo khoa học, nên ghi độ không chắc chắn và mức chữ số phù hợp tiêu chuẩn ngành, ví dụ `1.23 m ± 0.01 m` nếu đó là quy tắc trình bày được chọn.

`setScale`, `DecimalFormat` và `MathContext` thay đổi **biểu diễn/kết quả số học**, không đánh giá hay giảm sai số thực nghiệm.

## 4. Giá trị thập phân và BigDecimal: scale khác precision

`BigDecimal` xác định một số bằng `unscaledValue × 10^(-scale)`. Với `new BigDecimal("12.3400")`:

- `unscaledValue()` là `123400`.
- `scale()` là `4` (bốn chữ số sau dấu chấm trong biểu diễn này).
- `precision()` là `6` (sáu chữ số có nghĩa của unscaled value).

`scale` khác `precision`. `setScale(2)` hướng tới **hai chữ số sau dấu chấm**, không phải hai chữ số có nghĩa. `MathContext(2, mode)` giới hạn **hai chữ số có nghĩa** khi áp dụng vào phép toán. Chọn sai khái niệm có thể làm tròn sai bậc độ lớn.

```java
BigDecimal x = new BigDecimal("123.456");
System.out.println(x.setScale(2, RoundingMode.HALF_UP)); // 123.46
System.out.println(x.round(new MathContext(2, RoundingMode.HALF_UP))); // 1.2E+2
```

Đoạn mã rời trên cần `import java.math.BigDecimal;`, `import java.math.MathContext;` và `import java.math.RoundingMode;`.

### 4.1. Khởi tạo từ String thay vì double khi decimal là dữ liệu chính xác

`new BigDecimal(0.1)` lưu chính xác giá trị nhị phân xấp xỉ của literal `double`, vốn khác số thập phân `0.1`. Để biểu diễn `0.1` decimal một cách chủ ý:

```java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
System.out.println(exactDecimal); // 0.1
System.out.println(fromValueOf);   // 0.1
```

`BigDecimal.valueOf(double)` chuyển từ biểu diễn decimal của `double`, thường hợp lý cho literal đơn giản, nhưng **không khôi phục được các chữ số đã mất từ một chuỗi phép tính `double` trước đó**. Với input từ API hoặc DB, nên sử dụng định dạng decimal chính xác của nguồn thay vì đi qua `double`.

### 4.2. BigDecimal bất biến

`add`, `multiply`, `divide` (khi đủ thông tin làm tròn), `setScale` đều trả về một `BigDecimal` kết quả; không sửa instance gốc:

```java
BigDecimal raw = new BigDecimal("10.005");
BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);
System.out.println(raw);     // 10.005
System.out.println(rounded); // 10.01
```

Nếu chỉ gọi `raw.setScale(2, mode);` mà bỏ kết quả, biến `raw` vẫn giữ nguyên. Tính bất biến giúp mô hình giá trị dễ suy luận hơn, nhưng **bản thân BigDecimal không chứa currency/unit hay quy tắc làm tròn nghiệp vụ**.

## 5. Quy tắc làm tròn phải là một phần hợp đồng dữ liệu

Một giá trị raw `10.005` có thể thành `10.00` hoặc `10.01` tùy quy tắc làm tròn. Khi thiết kế giá trị thập phân, hãy xác định rõ:

1. **Scale đầu vào/tính toán**: giữ bao nhiêu chữ số để không đánh mất thông tin quan trọng trước thời điểm quyết toán.
2. **Scale kết quả**: ví dụ hai vị trí decimal cho một định dạng ví dụ, chứ không phải mọi loại tiền tệ.
3. **RoundingMode**: `HALF_UP`, `HALF_EVEN`, `DOWN`, `CEILING`… theo hợp đồng.
4. **Ranh giới làm tròn**: sau từng dòng, sau tính thuế, sau tổng cuối, khi lưu DB, hay chỉ khi xuất cho người dùng.
5. **Áp dụng với số âm**: hoàn tiền/điều chỉnh; `CEILING` hướng về dương vô cực, `FLOOR` về âm vô cực.
6. **Khi không cho mất chính xác**: `RoundingMode.UNNECESSARY` để phát hiện giá trị chưa tuân thủ scale.

Ví dụ `HALF_UP` về scale 2:

```java
System.out.println(new BigDecimal("10.004").setScale(2, RoundingMode.HALF_UP)); // 10.00
System.out.println(new BigDecimal("10.005").setScale(2, RoundingMode.HALF_UP)); // 10.01
System.out.println(new BigDecimal("-10.005").setScale(2, RoundingMode.HALF_UP)); // -10.01
```

`HALF_EVEN` chọn chữ số cuối chẵn khi hai kết quả đích cách đều giá trị gốc. Đây là quy tắc có thể giảm thiên lệch thống kê trong nhiều phép làm tròn, nhưng không được mặc định là chính sách đúng cho mọi giao dịch. Ví dụ:

```java
System.out.println(new BigDecimal("1.245").setScale(2, RoundingMode.HALF_EVEN)); // 1.24
System.out.println(new BigDecimal("1.255").setScale(2, RoundingMode.HALF_EVEN)); // 1.26
```

**Không làm tròn sớm chỉ để “dữ liệu đẹp”.** Nếu tổng được làm tròn sau khi cộng, phải giữ các hạng mục đủ chính xác để cộng trước; nếu luật hạch toán yêu cầu làm tròn mỗi dòng, đó là một quy trình khác cần được triển khai chủ ý.

### 5.1. Làm tròn từng phần khác làm tròn tổng

Giả sử hai khoản tiền decimal chính xác là `10.005` và `0.005`, cuối cùng trả về scale 2 và `HALF_UP`:

```text
Làm tròn từng khoản:
round2(10.005) + round2(0.005)
= 10.01 + 0.01
= 10.02

Cộng chính xác rồi làm tròn:
round2(10.005 + 0.005)
= round2(10.010)
= 10.01
```

Cả hai kết quả có thể xuất hiện trong hệ thống đúng về mặt kỹ thuật. Hợp đồng quyết định **kết quả nào được phép**. Thiết kế tốt đặt chính sách này ở một lớp hoặc hàm có tên rõ, thay vì rải `setScale(2)` ở khắp các bước tính toán.

### 5.2. Không tùy tiện làm tròn hai lần

Với `1.245`, `HALF_UP`:

```text
round1(round2(1.245)) = round1(1.25) = 1.3
round1(1.245) = 1.2
```

Kết quả khác vì round trung gian làm mất chữ số cần thiết để đánh giá ngưỡng ở bước cuối. Khi cần làm tròn về một scale đích, hãy làm trực tiếp từ giá trị có đủ độ chính xác, trừ khi một bước làm tròn trung gian được quy định rõ là sự kiện nghiệp vụ.

## 6. Mô hình hóa bằng value object thay vì truyền các primitive không rõ nghĩa

Một hàm như `calculate(long a, BigDecimal b, BigDecimal c)` không tiết lộ `a` đếm gì, `b` có đơn vị gì hay `c` là tiền theo đồng nào. Value object/record có thể đưa invariant vào kiểu dữ liệu.

Ví dụ về hợp đồng:

| Value object | Dữ liệu nội bộ | Quy tắc ở ranh giới |
| --- | --- | --- |
| `ItemCount` | `long value` | Không âm; cộng bằng `Math.addExact`; không cộng với số đo. |
| `Length` | `BigDecimal meters` | Đổi đơn vị về mét trước khi cộng; không tạo chiều dài âm trong miền ví dụ này. |
| `Money` | `String currency`, `BigDecimal amount` | Không cộng khác currency; cho phép khoản âm nếu nghiệp vụ có hoàn tiền; làm tròn theo chính sách explicit. |

`record` từ Java 16+ phù hợp làm value carrier với thành phần bất biến như `long`, `String` và `BigDecimal`. Có thể định nghĩa constructor chuẩn hóa/validate, phương thức tạo từ đơn vị có ý nghĩa và phương thức nghiệp vụ thay vì để caller tự tổ hợp các phép cộng trần.

Đừng nhầm **“dữ liệu hợp lệ”** với “đúng nghiệp vụ mọi nơi”. Ví dụ count âm có thể là hợp lệ với *delta* nhưng không hợp lệ với tồn kho; length âm có thể không hợp lệ cho chiều dài, nhưng độ dời có hướng có thể âm. Chọn tên và invariant đúng miền.

## 7. Chương trình Java 17+ hoàn chỉnh

Ví dụ dưới đây cố ý giữ riêng ba value object: `ItemCount`, `Length` và `Money`. Nó minh họa validation, kiểm soát overflow, đổi cm sang m, làm tròn tiền tại ranh giới và giữ nguyên kết quả raw.

Lưu mã thành **`NumericDomainModelDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class NumericDomainModelDemo {

    record ItemCount(long value) {
        ItemCount {
            if (value < 0) {
                throw new IllegalArgumentException("Negative item count");
            }
        }

        ItemCount plus(ItemCount other) {
            Objects.requireNonNull(other, "other");
            return new ItemCount(Math.addExact(value, other.value));
        }
    }

    record Length(BigDecimal meters) {
        Length {
            Objects.requireNonNull(meters, "meters");
            if (meters.signum() < 0) {
                throw new IllegalArgumentException("Negative length");
            }
        }

        static Length fromCentimeters(String centimeters) {
            return new Length(
                    new BigDecimal(centimeters).movePointLeft(2));
        }

        Length plus(Length other) {
            Objects.requireNonNull(other, "other");
            return new Length(meters.add(other.meters));
        }

        String formatMeters(int scale, RoundingMode mode) {
            return meters.setScale(scale, mode).toPlainString() + " m";
        }
    }

    record Money(String currency, BigDecimal amount) {
        Money {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(amount, "amount");
            if (currency.isBlank()) {
                throw new IllegalArgumentException("Blank currency");
            }
        }

        Money plus(Money other) {
            Objects.requireNonNull(other, "other");
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return new Money(currency, amount.add(other.amount));
        }

        Money rounded(int scale, RoundingMode mode) {
            return new Money(currency, amount.setScale(scale, mode));
        }

        String display() {
            return currency + " " + amount.toPlainString();
        }
    }

    public static void main(String[] args) {
        ItemCount count = new ItemCount(4).plus(new ItemCount(7));
        System.out.println("items = " + count.value());

        Length distance = Length.fromCentimeters("12.5")
                .plus(Length.fromCentimeters("7.5"));
        System.out.println("meters exact = "
                + distance.meters().toPlainString());
        System.out.println("meters display = "
                + distance.formatMeters(2, RoundingMode.HALF_UP));

        Money first = new Money("USD", new BigDecimal("10.005"));
        Money second = new Money("USD", new BigDecimal("0.005"));
        Money rawTotal = first.plus(second);

        Money roundedTotal = rawTotal.rounded(2, RoundingMode.HALF_UP);
        Money roundedLines = first.rounded(2, RoundingMode.HALF_UP)
                .plus(second.rounded(2, RoundingMode.HALF_UP));

        System.out.println("raw total = " + rawTotal.display());
        System.out.println("rounded total = " + roundedTotal.display());
        System.out.println("sum rounded lines = " + roundedLines.display());

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("same numeric value = "
                + (a.compareTo(b) == 0));
        System.out.println("same representation = " + a.equals(b));

        try {
            new ItemCount(Long.MAX_VALUE).plus(new ItemCount(1));
        } catch (ArithmeticException overflow) {
            System.out.println("count overflow = detected");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+ trong thư mục chứa file:

```bash
javac NumericDomainModelDemo.java
java NumericDomainModelDemo
```

Đầu ra chính xác:

```text
items = 11
meters exact = 0.200
meters display = 0.20 m
raw total = USD 10.010
rounded total = USD 10.01
sum rounded lines = USD 10.02
same numeric value = true
same representation = false
count overflow = detected
```

**Giải thích từng kết quả:**

- `ItemCount` cộng hai số nguyên không âm bằng `Math.addExact`, tạo count 11 và phát hiện overflow ở tình huống cuối.
- `12.5 cm = 0.125 m`; `7.5 cm = 0.075 m`. Cộng decimal chính xác cho `0.200 m`. `formatMeters(2, HALF_UP)` xuất `0.20 m` mà không biến đổi đối tượng `Length` nguồn.
- Hai khoản tiền có tổng raw `10.010`. Rounding tổng sau phép cộng là `10.01`, còn rounding từng khoản là `10.01 + 0.01 = 10.02`. Chương trình không tự nhận chính sách nào đúng ngoài giả định.
- `BigDecimal.compareTo` so sánh theo giá trị số học; `equals` xét cả scale. `1.0` và `1.00` bằng về trị số nhưng không có cùng biểu diễn.
- `Money.plus` kiểm tra currency để không vô tình cộng USD và EUR. Ví dụ vẫn đơn giản hóa nghiệp vụ thực tế: không tự mã hóa toàn bộ chính sách ISO currency, tỷ giá, phân số đơn vị tiền hoặc pháp lý.

Các `record` và thao tác đã dùng là API hợp lệ từ Java 17. Code không sử dụng thư viện bên ngoài. Trong hệ thống thật, có thể nâng cấp sang các lớp/domain module chuyên biệt, dùng kiểu currency rõ và áp dụng giới hạn độ dài, miền giá trị, loại đơn vị theo hợp đồng ứng dụng.

## 8. Khi nào dùng double, khi nào dùng BigDecimal?

Không có quy tắc “mọi số đo dùng `double`” hoặc “mọi số thập phân dùng `BigDecimal`”. Hãy dựa trên **nghĩa của dữ liệu và độ chính xác cần bảo toàn**.

| Tiêu chí | `double` | `BigDecimal` |
| --- | --- | --- |
| Biểu diễn | Nhị phân IEEE 754, độ chính xác hữu hạn. | Thập phân theo unscaled value và scale. |
| Decimal `0.1` | Không thể biểu diễn đúng chính xác bằng nhị phân hữu hạn. | `new BigDecimal("0.1")` biểu diễn đúng decimal 0.1. |
| Khối lượng xử lý lớn, phép toán khoa học | Thường hiệu quả, có hỗ trợ mạnh từ thư viện số học. | Chi phí tạo đối tượng và số học cao hơn, cần đánh giá. |
| Tiền/thuế đòi decimal chính xác và rounding policy | Thường không phù hợp để làm nguồn tính toán nghiệp vụ. | Thường phù hợp khi quản lý đúng precision, scale và rounding. |
| Số đo vật lý có sai số và phép tính liên tục | Có thể phù hợp, khi độ chính xác binary đủ mục tiêu. | Có thể phù hợp khi dữ liệu/đầu ra quy định decimal; không xóa sai số đo. |
| So sánh bằng nhau | `==` có những trường hợp cần chú ý rounding, `NaN`, `-0.0`. | `equals()` xét cả scale; `compareTo()` xét giá trị số học. |

Với `double`, tránh so sánh kết quả tính toán phức tạp chỉ bằng `==` nếu sai số làm tròn được chấp nhận. Với `BigDecimal`, tránh ngộ nhận rằng precision tùy ý đồng nghĩa phép chia luôn cho kết quả hữu hạn: `1 / 3` cần rounding policy hoặc cách biểu diễn khác; overload `divide` không cung cấp rounding thích hợp có thể ném `ArithmeticException`.

Một giá trị bán hàng `0.5 kg` là measurement hoặc *measured quantity*; nếu nghiệp vụ đòi lưu chính xác tới gram, có thể lưu `long grams = 500`, và hiển thị `0.500 kg` ở ranh giới. Một thiết bị đo công suất hoạt động theo tín hiệu nhiễu có thể dùng `double` vì độ không chắc chắn thực nghiệm vượt nhiều so với sai số số học nhị phân. Sự lựa chọn phải được tài liệu hóa.

## 9. Ranh giới chuyển đổi và định dạng

Mô hình số tốt xác định các **bước chuyển đổi** riêng biệt:

```text
Input (chuỗi / API / DB)
    ↓ Parse và kiểm tra phạm vi
Value object (Count / Length / Money)
    ↓ Chuyển về đơn vị nội bộ thống nhất
Tính toán (giữ độ chính xác phù hợp)
    ↓ Áp dụng rounding policy đúng ranh giới
Giá trị giao tiếp / lưu trữ được chuẩn hóa
    ↓ Format theo locale hoặc giao thức
Chuỗi hiển thị / payload trao đổi
```

Điểm cần tránh: gộp cả parse, chuyển đơn vị, tính toán, làm tròn và format trong một lời gọi không rõ nghĩa.

### 9.1. Chuyển từ BigDecimal sang số nguyên phải kiểm soát

`longValue()` có thể làm mất phần thập phân hoặc tràn theo cách chuyển primitive. Nếu hợp đồng yêu cầu giá trị **nguyên và vừa miền long**, dùng `longValueExact()`:

```java
System.out.println(new BigDecimal("3.00").longValueExact()); // 3
// new BigDecimal("3.5").longValueExact() -> ArithmeticException.
// new BigDecimal("999999999999999999999999").longValueExact()
// -> ArithmeticException vì vượt miền long.
```

Dùng `BigDecimal.toBigIntegerExact()` nếu phải giữ một số nguyên không giới hạn mà từ chối phần thập phân. Những phương thức exact nên nằm trong bước validation/conversion, không dùng cast im lặng.

### 9.2. Chuỗi không có số mũ, còn scale là câu chuyện riêng

`toPlainString()` tạo chuỗi decimal không ký pháp khoa học. Muốn đúng 2 chữ số sau dấu thập phân theo `HALF_UP`:

```java
BigDecimal amount = new BigDecimal("3.5");
String text = amount.setScale(2, RoundingMode.HALF_UP)
        .toPlainString();
System.out.println(text); // 3.50
```

Nhưng `toPlainString()` không thêm dấu phân nhóm, dấu thập phân theo vùng miền hoặc đơn vị tiền. Dùng formatter như `NumberFormat` với `Locale` khi trình bày cho người đọc. Rounding trong formatter là một quyết định riêng: đồng bộ với chính sách nghiệp vụ để tránh khác biệt giữa giá trị thực và giá trị hiển thị.

Lưu ý việc `stripTrailingZeros()` có thể thay đổi scale; với `BigDecimal` có scale âm, `toPlainString()` rồi parse lại **có thể không giữ scale gốc** dù trị số không thay đổi. Nếu cần bảo toàn cặp unscaledValue/scale khi truyền dữ liệu, dùng biểu diễn canonical `toString()` hoặc lưu các thành phần rõ ràng theo hợp đồng.

## 10. Thiết kế database, API và dữ liệu đa đơn vị

Nếu database lưu tiền và decimal, kiểu `DECIMAL(p, s)`/`NUMERIC(p, s)` thường hợp lý hơn kiểu floating-point cho giá trị nghiệp vụ cần decimal exact; tuy nhiên, khả năng, giới hạn precision/scale, mặc định và cách xử lý overflow/rounding **phụ thuộc hệ quản trị CSDL**. Phải cấu hình schema theo ràng buộc thực tế, không tự giả định CSDL luôn “âm thầm” làm tròn đúng cách.

Ví dụ nguyên tắc thiết kế (không phải lệnh SQL dành cho mọi CSDL):

```sql
CREATE TABLE invoice_line (
    id BIGINT PRIMARY KEY,
    quantity BIGINT NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    currency_code CHAR(3) NOT NULL
);
```

Bảng minh họa lưu `quantity` như số nguyên, đơn giá decimal với scale 4, và currency code riêng. Nếu doanh nghiệp bán hàng theo kg với số lẻ, `quantity BIGINT` **không đủ**; cần thêm `quantity_value DECIMAL(...)` và `unit_code` hoặc một số nguyên theo đơn vị nhỏ nhất được quy định. Tương tự, loại tiền khác nhau có quy tắc số lẻ khác nhau, cần xác định policy khi kết toán.

Ở API, nên nêu rõ ý nghĩa từng trường:

```json
{
  "quantity": 3,
  "unitPrice": "12.3450",
  "currency": "USD",
  "amountRounding": "HALF_UP",
  "settlementScale": 2
}
```

JSON này chỉ **minh họa một hợp đồng có thể thiết kế**, chưa phải schema bắt buộc. Chuỗi `"12.3450"` giúp giữ decimal text/scale mong muốn qua những hệ thống không xử lý JSON Number với precision tương thích; cần parse và validate nó thành `BigDecimal` ở phía Java. Nếu dùng JSON number hoặc decimal string phụ thuộc vào giao thức đã thống nhất với client. **Không tin trực tiếp roundingMode và scale gửi từ client nếu đó là quy tắc do server/nghiệp vụ quản lý**; server phải áp dụng policy đã được phê duyệt.

Với measurement API, đặt `value` và `unit` tách nhau hoặc dùng một đơn vị nội bộ được tài liệu hóa rõ. Ví dụ:

```json
{
  "length": "125.0",
  "unit": "cm"
}
```

Server chuyển thành `1.250 m` theo quy tắc đơn vị. Nếu client gửi `125.0` nhưng không ghi đơn vị, việc suy diễn một đơn vị mặc định dễ dẫn tới lỗi đổi đơn vị nghiêm trọng.

## 11. Những lỗi thường gặp khi thiết kế dữ liệu số

1. **Dùng `double` cho mọi số decimal cần tiền/thuế chính xác.** Dấu phẩy động nhị phân có sai số biểu diễn; dùng `BigDecimal` hoặc số nguyên theo đơn vị tiền nhỏ nhất khi phù hợp hợp đồng.
2. **Dùng `BigDecimal` cho mọi dữ liệu số mà không cần thiết.** Nếu số đếm luôn nguyên và trong giới hạn, `int`/`long` dễ hiểu và hiệu quả hơn; nếu measurement có sai số vật lý, precision decimal vô hạn không tự hữu ích.
3. **Không lưu đơn vị cho measurement.** `10` không phân biệt được mét với cm; thêm đơn vị hoặc một kiểu bảo đảm đơn vị cơ sở.
4. **Cho phép count âm ngoài miền nghiệp vụ.** Dùng constructor validation; phân biệt count tuyệt đối và count delta.
5. **Bỏ qua overflow khi cộng số đếm.** `Math.addExact` hoặc `BigInteger` giúp phát hiện/tránh kết quả sai.
6. **Cộng các đơn vị/loại tiền khác nhau.** Kiểm tra compatibility trước khi tính; chuyển đổi đơn vị chính xác hoặc tỷ giá theo policy.
7. **Dùng `new BigDecimal(0.1)` để biểu diễn 0.1 decimal chính xác.** Nên tạo từ chuỗi hoặc nguồn decimal chính xác.
8. **Nhầm precision với scale.** `MathContext(2)` và `setScale(2)` trả lời hai yêu cầu khác nhau.
9. **Làm tròn tùy ý sau mỗi phép toán.** Rounding ở trung gian có thể đổi tổng; xác định rõ ranh giới nghiệp vụ.
10. **Dùng `setScale(2)` với số phải làm tròn mà không chỉ định mode.** Overload một tham số ném `ArithmeticException` khi phải bỏ chữ số có giá trị khác 0; chọn mode hoặc dùng `UNNECESSARY` để chủ động kiểm tra.
11. **So sánh BigDecimal sai.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false` vì scale khác; `compareTo() == 0` khi chỉ cần bằng nhau về trị số.
12. **Cho rằng dữ liệu đo nhiều chữ số là chính xác tuyệt đối.** Bảo toàn sai số đo và độ phân giải trong mô hình/báo cáo khi cần.
13. **Trộn quy tắc format với quy tắc tiền tệ.** Ký hiệu tiền và dấu phân cách theo locale là presentation; rounding hợp đồng là quyết định nghiệp vụ.
14. **Không kiểm soát ranh giới dữ liệu bên ngoài.** Input âm khi không được phép, đơn vị lạ, chuỗi quá dài, NaN/Infinity của double hoặc decimal vượt precision DB phải được xử lý phù hợp.
15. **Biến scale hiển thị thành scale lưu trữ quá sớm.** Nếu lưu `amount` đã làm tròn trước khi tính thuế/tổng, không thể khôi phục những chữ số bị mất sau đó.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Một ứng dụng đếm lượt xem đến hàng tỷ, và số có thể tăng liên tục. Chọn `int` hay `long`?

**Đáp án:** `long` khi giới hạn `int` có thể bị vượt; nếu tổng có thể vượt cả `long` thì `BigInteger` hoặc chính sách miền hợp lệ rõ ràng. Cần dùng `Math.addExact` khi muốn phát hiện tràn thay vì wraparound.

**Câu 2.** Vì sao `2.5 kg` không nên lưu bằng `ItemCount(2)`?

**Đáp án:** Đó là đại lượng đo theo kg, không phải số mặt hàng rời rạc nguyên. Cần giá trị thập phân hoặc số nguyên theo đơn vị cơ sở như gram, cùng đơn vị.

**Câu 3.** `125.0 cm` đổi ra mét bằng decimal exact là gì?

**Đáp án:** `1.250 m` với `new BigDecimal("125.0").movePointLeft(2)`. Các chữ số được trình bày phản ánh biểu diễn, không tự khẳng định thêm độ chính xác thực nghiệm.

**Câu 4.** Vì sao `new BigDecimal("12.3400").scale()` và `precision()` khác nhau?

**Đáp án:** Scale là 4 vị trí sau dấu chấm; precision là 6 chữ số có nghĩa trong unscaled value. Chúng đo hai thuộc tính khác nhau.

**Câu 5.** Hai khoản `10.005` và `0.005`, `HALF_UP` về scale 2, tổng rounded sau cùng so với rounded từng khoản?

**Đáp án:** Round tổng là `10.01`; cộng từng khoản đã rounded là `10.02`. Chính sách hợp đồng quyết định cách thực hiện.

**Câu 6.** Nếu muốn từ chối một decimal có phần lẻ khi chuyển sang `long`, chọn phương thức nào?

**Đáp án:** `longValueExact()`; phương thức ném `ArithmeticException` nếu có phần thập phân khác 0 hoặc vượt miền `long`.

**Câu 7.** Vì sao không được cộng `Money("USD", x)` với `Money("EUR", y)` trực tiếp?

**Đáp án:** Currency là một phần của ý nghĩa giá trị. Cần quy trình chuyển đổi tỷ giá có ngày hiệu lực/quy tắc làm tròn cụ thể, hoặc từ chối phép cộng.

**Câu 8.** Khi nào `double` vẫn là lựa chọn hợp lý cho measurement?

**Đáp án:** Khi dữ liệu bản chất xấp xỉ, chi phí xử lý/thuật toán quan trọng, sai số dấu phẩy động nằm trong mức cho phép, và hợp đồng không đòi decimal exact.

**Câu 9.** Nếu đo chiều dài `1.234 m` với sai số ±`0.01 m`, hiển thị `1.234000 m` có chính xác hơn không?

**Đáp án:** Không. Dấu 0 hiển thị thêm không giảm độ không chắc chắn của phép đo; báo cáo phải theo quy tắc chữ số có nghĩa và sai số đo.

**Câu 10.** Vì sao value object/record hữu ích cho thiết kế dữ liệu số?

**Đáp án:** Nó đặt tên miền dữ liệu, kiểm tra invariant, hạn chế phép toán không hợp lệ như cộng chéo đơn vị/currency, đồng thời gom logic chuyển đổi và rounding vào nơi có nghĩa rõ ràng.

## 13. Tài liệu Java chính thức

- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): decimal, precision, scale, immutable operations và round.
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): định nghĩa các chế độ làm tròn.
- [Java SE 17 — Math.addExact](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#addExact(long,long)): phát hiện overflow kiểu nguyên.
- [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): số nguyên độ chính xác tùy ý.
- [Java SE 17 — NumberFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html): định dạng số theo locale, tách khỏi phép tính nghiệp vụ.

**Ghi nhớ:** **Số đếm** đòi quy tắc về tính nguyên và phạm vi; **đại lượng đo** đòi giá trị đi cùng đơn vị và ý nghĩa sai số; **giá trị decimal có làm tròn** đòi precision, scale, rounding mode và ranh giới áp dụng rõ ràng. Thiết kế đúng mô hình trước, rồi chọn `int`/`long`/`BigInteger`/`double`/`BigDecimal` phù hợp với nghĩa của dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-004">Quy ước xử lý null, NaN, vô cực và giá trị thiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Quy ước xử lý null, NaN, vô cực và giá trị thiếu trong thiết kế dữ liệu số Java

## 1. Một con số không phải lúc nào cũng chỉ là một giá trị

Dữ liệu số đi qua API, form nhập liệu, cơ sở dữ liệu, tệp CSV, JSON, phép đo hoặc phép tính trong Java có thể ở nhiều trạng thái khác nhau. Trước khi chọn `int`, `double` hay `BigDecimal`, cần thống nhất **mỗi trạng thái mang ý nghĩa gì** và **ứng dụng phải xử lý ra sao**.

Ví dụ một cảm biến gửi dữ liệu nhiệt độ:

- `0.0`: thiết bị thực sự đo được 0 độ.
- `null`: không có giá trị được cung cấp trong một trường cho phép null.
- Trường bị **thiếu** trong JSON: phía gửi không đưa trường đó vào; không nhất thiết cùng nghĩa với trường được gửi rõ ràng là `null`.
- `"N/A"`: nguồn gửi chuỗi không có số; có thể là một marker "không áp dụng" hoặc dữ liệu sai, tùy hợp đồng.
- `Double.NaN`: giá trị số chấm động thuộc loại *Not a Number*; không đồng nghĩa với null.
- `Double.POSITIVE_INFINITY` hoặc `Double.NEGATIVE_INFINITY`: giá trị chấm động dương/âm vô cực, có thể xuất hiện từ một số phép toán hay quá trình parse.
- `-0.0`: số 0 âm theo IEEE 754; trong nhiều phép so sánh số học bằng `0.0` nhưng vẫn có khác biệt về biểu diễn và một số phép toán.

**Không có quy tắc phổ quát rằng null = NaN = Infinity = 0 = giá trị thiếu.** Chúng là những trạng thái khác nhau, và quyết định gộp chúng phải xuất phát từ nghiệp vụ.

Mục tiêu của bài này là thiết kế *hợp đồng dữ liệu số* (*numeric data contract*): quy tắc tiếp nhận, biểu diễn, kiểm tra hợp lệ, tính toán, tổng hợp, lưu trữ và xuất dữ liệu khi gặp giá trị đặc biệt.

## 2. Phân biệt các trạng thái quan trọng

| Trạng thái | Ví dụ | Trong Java | Ý nghĩa cần xác định |
|---|---|---|---|
| Số hữu hạn hợp lệ (*finite*) | `12.5`, `0.0`, `-0.0` | `double`, `Double`, `BigDecimal` | Giá trị đo/tính có thể dùng theo đơn vị và precision quy định |
| Thiếu giá trị (*missing*) | Không có trường, `null` theo contract | Wrapper `Double` có thể null; `OptionalDouble.empty()` | Chưa nhận dữ liệu hay không áp dụng? |
| Đầu vào không hợp lệ (*invalid*) | `"abc"` khi bắt buộc là số | Parse ném `NumberFormatException` | Báo lỗi, từ chối hoặc cách ly dữ liệu |
| NaN | `0.0 / 0.0` | `Double.NaN`, `Float.NaN` | Kết quả đặc biệt của số thực, **không** phải missing mặc định |
| Vô cực dương | `1.0 / 0.0` | `Double.POSITIVE_INFINITY` | Có cho phép theo miền bài toán không? |
| Vô cực âm | `-1.0 / 0.0` | `Double.NEGATIVE_INFINITY` | Có cho phép theo miền bài toán không? |
| Giá trị sentinel | `-9999` để chỉ dữ liệu thiếu | `int`/`long`/`double` | Chỉ an toàn nếu sentinel ngoài miền hợp lệ và contract rõ |
| Giá trị mặc định | `0` khi chưa nhập | Do ứng dụng tự tạo | Có phân biệt được "chưa nhập" với "thực sự bằng 0" không? |

Một ứng dụng có thể phân chia khác bảng này, nhưng **phải công bố và thực thi nhất quán**. Ví dụ trong đo lường khoa học, vô cực đôi khi được giữ để phân tích; trong nghiệp vụ tiền tệ, cả NaN lẫn Infinity phải bị từ chối.

### Trường thiếu và trường có giá trị null có giống nhau không?

Không nhất thiết. Xét hai payload minh họa:

~~~json
{"sensorId":"T-01"}
~~~

~~~json
{"sensorId":"T-01","temperature":null}
~~~

Payload thứ nhất không có khóa `temperature`; payload thứ hai có khóa nhưng giá trị là JSON `null`. Một thư viện deserialize vào DTO có thể biến cả hai thành trường Java `null`, vì vậy **mất khả năng phân biệt trạng thái** nếu không lưu thông tin về sự hiện diện của trường. Nếu API phân biệt "không cập nhật trường" và "xóa giá trị trường" (ví dụ partial update), lớp DTO hoặc bộ đọc phải giữ được sự khác biệt đó.

Tương tự, chuỗi rỗng `""`, chuỗi toàn khoảng trắng, marker `"NA"` và JSON `null` không tự động tương đương. Hãy viết quy tắc chuẩn hóa đầu vào cụ thể, có kiểm thử.

## 3. null và wrapper: có thể lưu, nhưng không thể unbox như một số

Các kiểu primitive `int`, `long`, `double` không thể nhận `null`. Nếu đầu vào thật sự có thể thiếu, lớp wrapper `Integer`, `Long`, `Double`, `BigDecimal` hoặc một kiểu mô hình hóa trạng thái phù hợp hơn.

~~~java
Double measurement = null; // Hợp lệ: chưa có giá trị.
double actual = 0.0;       // Hợp lệ: có giá trị số 0.
~~~

Điểm nguy hiểm là **unboxing tự động**:

~~~java
Double measurement = null;

// Có thể biên dịch, nhưng khi chạy sẽ ném NullPointerException:
// double x = measurement;
// double y = measurement + 1.0;
// boolean ok = measurement > 0.0;
~~~

Unboxing tương đương gọi `measurement.doubleValue()`. Vì receiver là null, chương trình không thể lấy giá trị primitive.

Phải chọn chính sách:

~~~java
Double measurement = null;

// Cách A: thiếu dữ liệu được hiểu là 0 (chỉ dùng khi nghiệp vụ cho phép).
double valueOrZero = measurement == null ? 0.0 : measurement;

// Cách B: bắt buộc có dữ liệu, thiếu thì từ chối.
double required = java.util.Objects.requireNonNull(
        measurement, "measurement is required");
~~~

Trong cách B, `requireNonNull` ném `NullPointerException` có thông điệp rõ khi dữ liệu null. Nếu API cần lỗi hợp đồng, có thể kiểm tra và ném exception nghiệp vụ (ví dụ `IllegalArgumentException`) ở ranh giới xử lý.

**Không nên thay mọi null bằng 0 theo thói quen**: thao tác đó khiến báo cáo "có 10 phép đo, một lần bằng 0" không thể phân biệt với "có 9 phép đo, một lần thiếu".

## 4. IEEE 754: NaN và hai dạng vô cực trong double/float

`double` và `float` trong Java theo mô hình số thực chấm động IEEE 754, nên có các giá trị đặc biệt mà `int`/`long` không có.

~~~java
double nan = 0.0 / 0.0;
double positiveInfinity = 1.0 / 0.0;
double negativeInfinity = -1.0 / 0.0;

System.out.println(nan);              // NaN
System.out.println(positiveInfinity); // Infinity
System.out.println(negativeInfinity); // -Infinity
~~~

Đây là phép chia **số thực** vì các toán hạng là `double`. Phân biệt với phép chia số nguyên:

~~~java
// int invalid = 1 / 0; // Biểu thức hằng bị compiler từ chối trong một số ngữ cảnh;
// với phép chia số nguyên tại runtime, chia cho 0 gây ArithmeticException.
~~~

Với các biến số nguyên và mẫu số bằng 0 ở runtime, phép chia ném `ArithmeticException`. Đừng kỳ vọng nó tạo ra `Infinity` như số thực.

### 4.1. Kiểm tra bằng API chính thức

~~~java
double v = Double.NaN;

System.out.println(Double.isNaN(v));      // true
System.out.println(Double.isInfinite(v)); // false
System.out.println(Double.isFinite(v));   // false
~~~

Quy tắc phân loại:

- `Double.isNaN(x)` đúng nếu x là NaN.
- `Double.isInfinite(x)` đúng nếu x là vô cực dương hoặc âm.
- `Double.isFinite(x)` đúng nếu x **không phải NaN và không phải vô cực**.
- `Float` có các phương thức kiểm tra tương tự cho `float`.

Một phép xác thực đầu vào `double` thường bắt đầu bằng:

~~~java
static double requireFinite(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Value must be finite");
    }
    return value;
}
~~~

Nếu miền hợp lệ còn có giới hạn (ví dụ phần trăm từ 0 đến 100), phải kiểm tra cả **độ hữu hạn** và **range**:

~~~java
static double requirePercent(double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
        throw new IllegalArgumentException("percent out of range");
    }
    return value;
}
~~~

### 4.2. NaN không so sánh như số thông thường

~~~java
double x = Double.NaN;

System.out.println(x == x);      // false
System.out.println(x != x);      // true
System.out.println(x < 100);     // false
System.out.println(x > 100);     // false
System.out.println(x >= 100);    // false
~~~

So sánh quan hệ `<`, `>`, `<=`, `>=` với NaN trả false; so sánh bằng `==` cũng false, kể cả khi so NaN với chính nó. Do đó **không** sử dụng `x == Double.NaN` để phát hiện NaN:

~~~java
double x = Double.NaN;
boolean wrong = x == Double.NaN; // false
boolean right = Double.isNaN(x); // true
~~~

Quy tắc đặc biệt này áp dụng cho phép so sánh primitive. `Double.equals` và `Double.compare` dùng những quy tắc phục vụ object equality/sắp xếp, không hoàn toàn giống `==` với primitive.

### 4.3. Vô cực không tự động nghĩa là lỗi parse

~~~java
double huge = Double.parseDouble("1e400");
System.out.println(huge);                 // Infinity
System.out.println(Double.isInfinite(huge)); // true
~~~

Chuỗi có cú pháp hợp lệ nhưng nằm ngoài phạm vi hữu hạn của `double` có thể được parse thành vô cực. Vì vậy `try/catch NumberFormatException` **chưa đủ** để bảo đảm bạn có một số hữu hạn hợp lệ. Sau parse, vẫn phải gọi `Double.isFinite` nếu nghiệp vụ không cho phép Infinity/NaN.

Java còn nhận chuỗi đặc biệt `"NaN"`, `"Infinity"` và `"-Infinity"` trong `Double.parseDouble`. Có cho phép các chuỗi này xuất hiện ở API của bạn hay không là một quyết định thiết kế, không phải mặc định rằng "parse thành công thì hợp lệ".

## 5. -0.0 và +0.0: gần giống về toán học, vẫn khác trong vài trường hợp

IEEE 754 có dấu cho số 0:

~~~java
double positiveZero = 0.0;
double negativeZero = -0.0;

System.out.println(positiveZero == negativeZero); // true
System.out.println(1.0 / positiveZero);           // Infinity
System.out.println(1.0 / negativeZero);           // -Infinity
System.out.println(Double.compare(-0.0, 0.0));    // -1
~~~

- Phép so sánh primitive `==` coi hai dạng 0 bằng nhau.
- Phép lấy nghịch đảo phân biệt dấu nên cho hai vô cực khác nhau.
- `Double.compare` định nghĩa thứ tự có thể phân biệt `-0.0` và `+0.0`, phục vụ sắp xếp.
- `Double.valueOf(-0.0).equals(Double.valueOf(0.0))` trả false.

Trong nhiều ứng dụng kinh doanh, có thể chuẩn hóa `-0.0` về `+0.0` sau khi xác định rằng dấu của 0 **không có ý nghĩa nghiệp vụ**. Trong tính toán số hoặc khoa học, việc giữ dấu 0 có thể quan trọng.

Ví dụ chuẩn hóa tự nguyện:

~~~java
static double normalizeZero(double x) {
    return x == 0.0 ? 0.0 : x;
}
~~~

Hàm trên giữ mọi giá trị khác 0, bao gồm NaN/Infinity, nên **không** phải bộ lọc số hữu hạn; cần kiểm tra riêng `Double.isFinite` khi cần.

## 6. BigDecimal, BigInteger không có giá trị NaN hoặc Infinity

`BigDecimal` và `BigInteger` mô hình hóa số chính xác theo miền của chúng; chúng **không định nghĩa hằng NaN hay ±Infinity**. Giá trị thiếu vẫn có thể được biểu diễn ở mức **tham chiếu `null`**, nhưng không phải là một giá trị số bên trong đối tượng.

~~~java
import java.math.BigDecimal;

BigDecimal valid = new BigDecimal("123.45");

// Hai dòng sau gây NumberFormatException nếu được thực thi:
// BigDecimal invalidNaN = BigDecimal.valueOf(Double.NaN);
// BigDecimal invalidInfinity = BigDecimal.valueOf(Double.POSITIVE_INFINITY);
~~~

Khi chuyển từ `double` sang `BigDecimal`, nên kiểm tra đầu vào:

~~~java
static BigDecimal fromFiniteDouble(double source) {
    if (!Double.isFinite(source)) {
        throw new IllegalArgumentException("non-finite source");
    }
    return BigDecimal.valueOf(source);
}
~~~

Phương thức `BigDecimal.valueOf(double)` dùng biểu diễn thập phân từ `Double.toString(double)` và chỉ thích hợp khi yêu cầu chấp nhận ý nghĩa của giá trị `double` ban đầu. Đối với số tiền hoặc dữ liệu cần chính xác thập phân từ đầu, parse trực tiếp từ chuỗi là lựa chọn rõ ràng hơn:

~~~java
BigDecimal price = new BigDecimal("0.10");
~~~

Dùng `new BigDecimal(0.1)` sẽ lưu chính xác giá trị nhị phân của `double 0.1`, không phải chính xác 0,1 theo ý nghĩa thập phân. Đây là vấn đề về độ chính xác, **độc lập** với xử lý null/NaN/Infinity.

### Thiết kế phép chia

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal zero = BigDecimal.ZERO;

// one.divide(zero) -> ArithmeticException, không sinh Infinity.
~~~

`BigDecimal.divide` có thể ném `ArithmeticException` khi chia cho 0 hoặc khi yêu cầu biểu diễn thập phân chính xác của phân số vô hạn mà không cung cấp chế độ làm tròn. Không nên tự biến lỗi này thành `null` hoặc NaN nếu đặc tả không yêu cầu.

## 7. Giá trị thiếu: không nên dùng sentinel nếu có mô hình rõ hơn

Một hệ thống cũ có thể lưu số đo thiếu bằng `-1`, `-9999`, `Double.NaN` hoặc chuỗi `"N/A"`. Đó là **sentinel** — giá trị đặc biệt được quy ước thay cho trạng thái.

Rủi ro:

- Sentinel `-1` có thể trở thành giá trị hợp lệ khi miền dữ liệu thay đổi.
- Sentinel `0` trùng với số 0 thật.
- Sentinel NaN bị bỏ qua ở một số phép so sánh nhưng lan truyền sang các phép số học, dễ làm hỏng toàn bộ kết quả.
- Khi trộn nhiều nguồn dữ liệu, các sentinel khác nhau dễ bị hiểu sai.
- Sentinel có thể bị ghi qua hệ thống khác mà không còn metadata mô tả.

Nếu cần biểu diễn "có hoặc không có một số", Java có:

~~~java
import java.util.OptionalDouble;

OptionalDouble missing = OptionalDouble.empty();
OptionalDouble present = OptionalDouble.of(0.0);

System.out.println(missing.isPresent()); // false
System.out.println(present.isPresent()); // true
~~~

`OptionalDouble` biểu diễn **có hoặc không có** giá trị primitive; `OptionalDouble.of(Double.NaN)` vẫn là **có một giá trị NaN**, không phải rỗng:

~~~java
OptionalDouble nanPresent = OptionalDouble.of(Double.NaN);
System.out.println(nanPresent.isPresent()); // true
System.out.println(Double.isNaN(nanPresent.orElseThrow())); // true
~~~

Vì vậy, `OptionalDouble` **không thay thế chính sách kiểm soát NaN/Infinity**. Nó chỉ tách trạng thái vắng mặt khỏi trạng thái hiện diện. Với API hoặc DTO phức tạp, một kiểu `record`/sealed type/enum status có thể biểu đạt nhiều trạng thái hơn.

## 8. Đề xuất hợp đồng dữ liệu số nhất quán

Một quy tắc thiết kế hiệu quả là phân biệt rõ ít nhất bốn nhóm:

1. **FINITE**: đã parse thành công, là số hữu hạn, nằm trong miền cho phép và có đơn vị/precision hợp lệ.
2. **MISSING**: không nhận dữ liệu, theo đúng các marker mà hợp đồng quy định.
3. **INVALID**: có dữ liệu nhưng cú pháp hoặc giá trị không hợp lệ; cần báo lỗi hoặc cách ly.
4. **NON_FINITE**: đã parse thành `double` nhưng là NaN hoặc Infinity; xử lý theo chính sách riêng.

Nếu ứng dụng có nhu cầu phân tích kỹ thuật, nhóm NON_FINITE có thể được tách thành `NAN`, `POS_INFINITY` và `NEG_INFINITY`.

Điều này tránh sai lầm "mọi thứ parse không thành công hoặc không finite thì đổi thành 0".

### Ma trận chính sách cần quyết định

| Trường hợp | Ví dụ | Chính sách đề xuất khi chỉ chấp nhận số hữu hạn |
|---|---|---|
| Thiếu trường | JSON không có khóa | MISSING; báo dữ liệu thiếu, hoặc giữ trạng thái nếu optional |
| JSON null | `"temperature": null` | MISSING hoặc yêu cầu khác được công bố |
| Chuỗi trống | `""`, `" "` | Có thể MISSING nếu hợp đồng chấp nhận, không ngầm định |
| Chuỗi không parse được | `"hello"` | INVALID, thông báo lỗi |
| NaN | `"NaN"`, `0.0/0.0` | Từ chối hoặc lưu trạng thái NAN |
| Vô cực | `"Infinity"`, `1e400` | Từ chối hoặc lưu trạng thái vô cực |
| Số 0 | `"0"` | FINITE; không coi là missing |
| Số âm | `"-7.5"` | FINITE nếu miền cho phép; INVALID nếu đặc tả không cho âm |
| Số ngoài range | `250` cho phần trăm | INVALID, không âm thầm ép về 100 |
| Số hợp lệ nhưng sai đơn vị | `1000` mA khi API yêu cầu A | Chuẩn hóa bằng quy tắc đổi đơn vị hoặc từ chối |

Các ô "đề xuất" chỉ là ví dụ cho một hệ thống **chỉ nhận số hữu hạn**; ứng dụng thật phải ghi rõ dữ liệu nào được phép và trạng thái được giữ ở tầng nào. Đặc biệt với thao tác cập nhật một phần, phân biệt "không gửi trường" và "gửi null để xóa" có thể là yêu cầu bắt buộc.

### Định nghĩa rõ tại ranh giới

Hợp đồng nên trả lời:

- Chuỗi có được `trim()`, cho phép dấu cộng, ký hiệu mũ `E` hoặc khoảng trắng không?
- Có chấp nhận chuỗi `"NaN"`/`"Infinity"` không?
- Số ngoài miền hữu hạn thì từ chối, đánh dấu hay clamp? Không tự clamp nếu chưa có quy định.
- Null và field missing có khác nhau không?
- Giá trị thiếu có tham gia mẫu số của phép tính trung bình không?
- Lỗi parse có làm hỏng cả batch hay chỉ một bản ghi?
- Kết quả sẽ xuất qua JSON, cơ sở dữ liệu, CSV, giao thức nhị phân bằng biểu diễn nào?
- Nếu số tiền, precision/scale và RoundingMode là gì?

Một câu trả lời nhất quán cho các câu hỏi này quan trọng hơn lựa chọn một sentinel "tiện tay".

## 9. Ví dụ Java 17+: phân loại đầu vào mà không đánh đồng các trạng thái

Ví dụ sau mô phỏng một chính sách được định nghĩa cụ thể: `null` hoặc chuỗi chỉ chứa khoảng trắng được coi là `MISSING`; đầu vào không parse được là `INVALID`; NaN và mỗi loại vô cực được **giữ trạng thái riêng**; số hữu hạn mới được đưa vào phép tổng hợp.

Tạo tệp `NumericPolicyDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public class NumericPolicyDemo {

    enum State {
        FINITE, MISSING, INVALID, NAN, POS_INFINITY, NEG_INFINITY
    }

    record Reading(State state, Double value) {}

    static Reading parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Reading(State.MISSING, null);
        }

        try {
            double v = Double.parseDouble(raw.trim());

            if (Double.isNaN(v)) {
                return new Reading(State.NAN, null);
            }
            if (v == Double.POSITIVE_INFINITY) {
                return new Reading(State.POS_INFINITY, null);
            }
            if (v == Double.NEGATIVE_INFINITY) {
                return new Reading(State.NEG_INFINITY, null);
            }
            return new Reading(State.FINITE, v);
        } catch (NumberFormatException ex) {
            return new Reading(State.INVALID, null);
        }
    }

    public static void main(String[] args) {
        String[] input = {
            "12.5", null, "NaN", "Infinity", "-Infinity",
            "0", "-0.0", "oops", " "
        };

        EnumMap<State, Integer> counts = new EnumMap<>(State.class);
        List<Reading> valid = new ArrayList<>();

        for (String raw : input) {
            Reading reading = parse(raw);
            counts.merge(reading.state(), 1, Integer::sum);

            if (reading.state() == State.FINITE) {
                valid.add(reading);
            }

            String label = raw == null ? "<null>" : "\"" + raw + "\"";
            String detail = reading.state() == State.FINITE
                    ? "(" + reading.value() + ")"
                    : "";
            System.out.println(label + " => " + reading.state() + detail);
        }

        DoubleSummaryStatistics stats = valid.stream()
                .mapToDouble(r -> r.value())
                .summaryStatistics();

        System.out.printf(Locale.ROOT,
                "finite count=%d sum=%.1f average=%.2f%n",
                stats.getCount(), stats.getSum(), stats.getAverage());
        System.out.println("missing count="
                + counts.getOrDefault(State.MISSING, 0));
        System.out.println("invalid count="
                + counts.getOrDefault(State.INVALID, 0));
        System.out.println("nan count="
                + counts.getOrDefault(State.NAN, 0));
        System.out.println("infinite count="
                + (counts.getOrDefault(State.POS_INFINITY, 0)
                + counts.getOrDefault(State.NEG_INFINITY, 0)));

        System.out.println("NaN == NaN: " + (Double.NaN == Double.NaN));
        System.out.println("Double.compare(-0.0, 0.0): "
                + Double.compare(-0.0, 0.0));
        System.out.println("1.0/0.0: " + (1.0 / 0.0));
        System.out.println("0.0/0.0: " + (0.0 / 0.0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NumericPolicyDemo.java
java NumericPolicyDemo
~~~

Kết quả:

~~~text
"12.5" => FINITE(12.5)
<null> => MISSING
"NaN" => NAN
"Infinity" => POS_INFINITY
"-Infinity" => NEG_INFINITY
"0" => FINITE(0.0)
"-0.0" => FINITE(-0.0)
"oops" => INVALID
" " => MISSING
finite count=3 sum=12.5 average=4.17
missing count=2
invalid count=1
nan count=1
infinite count=2
NaN == NaN: false
Double.compare(-0.0, 0.0): -1
1.0/0.0: Infinity
0.0/0.0: NaN
~~~

**Giải thích thiết kế:**

- `Reading.state` nói rõ trạng thái thay vì sử dụng một giá trị sentinel.
- `Reading.value` chỉ khác null đối với trạng thái `FINITE` theo quy tắc của hàm `parse`. Đây là invariant nội bộ của ví dụ; khi dùng trong production, có thể kiểm tra invariant trong constructor hoặc dùng các kiểu dữ liệu riêng biệt để không thể tạo tổ hợp sai như `FINITE + null`.
- `Double.parseDouble` chấp nhận cú pháp `NaN` và `Infinity`; vì vậy code kiểm tra ngay sau parse chứ không chỉ bắt exception.
- `"oops"` là `INVALID`, còn `null`/`" "` là `MISSING` bởi **chính sách của ví dụ**, không phải quy luật Java.
- Ba giá trị hữu hạn là 12.5, 0.0 và -0.0; trung bình là 12.5 / 3, hiển thị với hai chữ số thập phân thành 4.17. Số lần thiếu/không hợp lệ/không hữu hạn được **báo riêng**.
- Lambda `r -> r.value()` có unboxing từ `Double` sang `double`. Trong ví dụ, dữ liệu đã được lọc và do `parse` tạo ra nên chỉ các bản ghi `FINITE` có giá trị khác null mới được đưa vào. Nếu cho phép tạo `Reading` tùy ý ngoài bộ phân loại, cần cưỡng chế invariant để tránh NPE.

Đây là một mô hình minh họa, không phải chuẩn bắt buộc cho mọi dự án. Với nghiệp vụ chỉ chấp nhận số hữu hạn, có thể đơn giản hóa `State` còn `FINITE`, `MISSING`, `INVALID` rồi từ chối Non-finite tại ranh giới theo quy tắc được công bố.

## 10. Tổng hợp số: quyết định số nào được tính vào mẫu số

Giả sử nhận bốn bản ghi: `10.0`, `null`, `20.0`, `30.0`.

- Nếu bỏ qua bản ghi thiếu: trung bình là `(10 + 20 + 30) / 3 = 20`.
- Nếu thay null bằng 0: trung bình là `(10 + 0 + 20 + 30) / 4 = 15`.
- Nếu một bản ghi thiếu phải làm phép tính không hợp lệ: không xuất trung bình mà trả trạng thái lỗi.

Ba kết quả là ba **quy tắc nghiệp vụ khác nhau**. Không có một quy tắc mặc định đúng cho mọi báo cáo.

Một pipeline chỉ tổng hợp số hữu hạn:

~~~java
import java.util.List;

List<Double> readings = java.util.Arrays.asList(
        10.0, null, 20.0, Double.NaN, 30.0, Double.POSITIVE_INFINITY);

var stats = readings.stream()
        .filter(java.util.Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .filter(Double::isFinite)
        .summaryStatistics();

System.out.println(stats.getCount());   // 3
System.out.println(stats.getSum());     // 60.0
System.out.println(stats.getAverage()); // 20.0
~~~

Phép lọc `Objects::nonNull` ngăn NPE khi unbox. Phép lọc `Double::isFinite` loại NaN/Infinity khỏi **phép tính thống kê**. Nhưng nếu chỉ có thống kê cuối, ta không còn biết bao nhiêu bản ghi bị loại và vì lý do gì. Nếu số lượng thiếu/không hợp lệ quan trọng, phải đếm và lưu lý do riêng trước khi tính.

Đừng gọi `getAverage()` rồi khẳng định luôn có giá trị trung bình: với `DoubleSummaryStatistics`, hãy kiểm tra `getCount()` khi cần phân biệt tập rỗng. Với `DoubleStream.average()`, kết quả là `OptionalDouble`; stream rỗng cho `OptionalDouble.empty()`.

### Nếu không lọc NaN thì sao?

~~~java
double sum = java.util.stream.DoubleStream.of(
        10.0, Double.NaN, 20.0).sum();

System.out.println(Double.isNaN(sum)); // true
~~~

NaN có thể lan truyền khiến tổng/trung bình trở thành NaN. Đây không phải cơ chế đáng tin cậy để "tự bỏ qua dữ liệu thiếu": nó khiến kết quả bị nhiễm NaN, và nếu phía sau dùng phép so sánh thông thường, có thể sinh thêm lỗi logic.

Tương tự, có cả vô cực dương và vô cực âm trong một phép tổng sẽ tạo tình huống kết quả NaN. Một hệ thống nên quyết định trước rằng kết quả không hữu hạn được xem là lỗi, được lưu như trạng thái đặc biệt, hay được phép trong một loại phép tính khoa học.

## 11. Sắp xếp và so sánh: Double.compare không giống ==

Một số thư viện dùng `Double.compare`, comparator hoặc `Double.equals` thay vì toán tử so sánh primitive. Đây là khác biệt rất quan trọng trong thiết kế dữ liệu.

~~~java
double a = Double.NaN;
double b = Double.NaN;

System.out.println(a == b);                // false
System.out.println(Double.compare(a, b));  // 0
System.out.println(Double.valueOf(a).equals(Double.valueOf(b))); // true
~~~

`Double.compare` định nghĩa thứ tự tổng quát để phục vụ so sánh/sắp xếp; nó coi NaN bằng nhau trong phép compare và phân biệt hai dấu của số 0. NaN được xếp lớn hơn các giá trị `double` còn lại theo thứ tự này. Đây là **quy tắc sắp xếp của API**, không phải một mệnh đề rằng NaN bằng NaN trong số học IEEE 754.

Các hệ quả:

- Không dùng `x == Double.NaN` để lọc NaN; dùng `Double.isNaN(x)`.
- Không dựa vào `Double.compare` như phép so sánh số học thông thường khi dữ liệu chứa NaN và dấu của 0.
- Khi sắp xếp dữ liệu, hãy xác định liệu dữ liệu thiếu/NaN phải lên đầu, xuống cuối hay bị tách riêng; nếu cần thứ tự theo nghiệp vụ, viết comparator dựa trên trạng thái thay vì dựa hoàn toàn vào thứ tự mặc định.
- `DoubleStream.min()`/`max()` có xử lý NaN theo API; nếu đầu vào có NaN và muốn cực trị của số **hữu hạn**, lọc `Double.isFinite` trước.

Với `null`, `Comparator.naturalOrder()` không tự xử lý mọi trường hợp an toàn; có thể dùng `Comparator.nullsFirst(...)`/`nullsLast(...)` khi danh sách wrapper thật sự cho phép null và contract đã xác định vị trí của null. Tuy nhiên, một đối tượng có trạng thái riêng thường rõ hơn nếu cần phân biệt null, invalid và nhiều loại non-finite.

## 12. Lưu trữ và truyền qua JSON, CSV, cơ sở dữ liệu

### 12.1. JSON: null hợp lệ, NaN/Infinity không phải số JSON chuẩn

Cú pháp JSON chuẩn hỗ trợ `null` và các số hữu hạn theo cú pháp JSON; các token `NaN`, `Infinity`, `-Infinity` **không phải literal số hợp lệ** trong JSON chuẩn. Ví dụ:

~~~json
{"temperature": null}
~~~

là JSON hợp lệ. Nhưng chuỗi ký tự không đặt dấu nháy như:

~~~text
{"temperature": NaN}
~~~

không phải JSON chuẩn. Một thư viện có thể có tùy chọn không chuẩn hoặc chuyển các giá trị đặc biệt thành chuỗi, nhưng không nên dựa vào hành vi đó giữa các hệ thống.

Nếu ứng dụng cần truyền trạng thái không hữu hạn, cân nhắc mô hình có nhãn:

~~~json
{"temperature":{"state":"NAN","value":null}}
~~~

hoặc nếu hợp đồng chỉ cho phép số hữu hạn thì **từ chối trước khi serialize**. Tránh gửi `"NaN"` như một số rồi kỳ vọng client tự hiểu giống server; chuỗi `"NaN"` là một **string**, không phải JSON number.

### 12.2. CSV: cần quy ước từng cột

CSV không có kiểu null bẩm sinh. Một ô rỗng, `NA`, `NaN` hay `-9999` chỉ mang nghĩa khi có schema:

~~~text
timestamp,reading,status
2026-01-01T00:00:00Z,12.5,FINITE
2026-01-01T00:01:00Z,,MISSING
2026-01-01T00:02:00Z,,NAN
~~~

Tách cột `status` khỏi cột `reading` giúp phân biệt "giá trị thực sự là 0" với "không có giá trị" và "phép đo không hợp lệ". Với batch ingestion, cũng nên lưu dòng/số thứ tự lỗi để có thể truy vết.

### 12.3. Cơ sở dữ liệu: NULL không đồng nghĩa NaN

SQL `NULL` thường biểu diễn trạng thái unknown/absent theo ngữ nghĩa SQL, không phải số 0 hay NaN. Hành vi lưu NaN/Infinity phụ thuộc hệ quản trị cơ sở dữ liệu, driver JDBC và kiểu cột. Không giả định mọi hệ đều lưu được mọi giá trị IEEE 754.

Ví dụ khi đọc một cột nullable bằng JDBC `ResultSet.getDouble(...)`: nếu cột là SQL NULL, giá trị primitive trả về có thể là 0.0, và cần gọi `wasNull()` ngay sau phép đọc liên quan để phân biệt 0 thật với NULL. Có thể dùng API đọc đối tượng phù hợp driver và kiểm tra null tường minh.

Trong hệ thống có bản ghi trạng thái, nên quyết định schema ngay từ đầu: một cột số hữu hạn nullable, thêm cột trạng thái, hoặc một bảng lỗi riêng. Các quyết định này giúp tránh sentinel không được tài liệu hóa lan giữa các dịch vụ.

## 13. Null, NaN và missing trong API: thiết kế kết quả rõ ràng

Đối với một API đo lường, các câu hỏi thiết kế quan trọng gồm:

1. Trường số có **bắt buộc** không? Khi không có giá trị, server trả trạng thái riêng, `null`, bỏ trường hay lỗi?
2. Nếu nhận `NaN`/Infinity, server trả lỗi `400`/mã nghiệp vụ, lưu trạng thái đặc biệt hay chấp nhận?
3. Có cần phân biệt input sai cú pháp với input thiếu?
4. Nếu giá trị vượt range nhưng vẫn hữu hạn, trả INVALID hay có quy tắc điều chỉnh?
5. Khi tính trung bình, tổng, min/max, mẫu số và số bản ghi bị bỏ qua được công bố ra sao?
6. Việc làm tròn và định dạng thập phân diễn ra ở tầng nào, và có làm mất khác biệt giữa missing và 0 không?

Một kiểu miền rõ ràng có thể tách trạng thái khỏi giá trị, chẳng hạn `Reading(State status, Double finiteValue)`. Đối với ứng dụng cần bảo đảm **không thể có trạng thái FINITE với null**, nên áp dụng kiểm tra trong constructor hoặc dùng mô hình kiểu phân biệt với các lớp/record riêng cho mỗi trạng thái. Ví dụ Java 17 có `sealed interface` để mô hình hóa các trạng thái dữ liệu; không cần bắt mọi trường hợp dùng duy nhất `Double` nullable.

### Ghi lại lý do thay vì chỉ loại bản ghi

Nếu ingest 1.000 bản ghi và chỉ có 960 bản ghi hữu hạn, thống kê phải có khả năng nói 40 bản ghi còn lại thuộc nhóm thiếu, sai cú pháp, NaN hay vượt miền. Nếu chỉ làm `filter(Double::isFinite)` rồi trả một số trung bình, hệ thống có thể che khuất sự cố chất lượng dữ liệu.

Các chỉ số hữu ích: tổng số bản ghi, số hữu hạn, số missing, số invalid, số NaN, số vô cực, số ngoài range, tỷ lệ các nhóm theo nguồn. Không nên tự gộp chúng nếu các trạng thái có quy trình khắc phục khác nhau.

## 14. Kiểm thử ranh giới bắt buộc

Một tập test tối thiểu cho parser số hữu hạn có thể gồm:

| Đầu vào | Kỳ vọng nếu chọn chính sách ở mục 9 |
|---|---|
| `null` | MISSING |
| `""` và `" "` | MISSING |
| `"0"` | FINITE(0.0) |
| `"-0.0"` | FINITE(-0.0), giữ dấu nếu contract yêu cầu |
| `"12.5"` | FINITE(12.5) |
| `"NaN"` | NAN |
| `"Infinity"` | POS_INFINITY |
| `"-Infinity"` | NEG_INFINITY |
| `"1e400"` | POS_INFINITY do `double` overflow hữu hạn |
| `"abc"` | INVALID |
| `"-3.5"` | FINITE nếu miền nghiệp vụ cho phép âm |
| `"101"` ở trường phần trăm 0..100 | INVALID theo bước validation miền |
| JSON bỏ trường | MISSING hoặc khác `null` theo hợp đồng API |
| JSON trường `null` | MISSING hoặc yêu cầu riêng |

Các test phải đối chiếu **trạng thái và dữ liệu**, không chỉ tránh ném exception. Thêm các test round-trip qua JSON/CSV/database khi những tầng đó tham gia đường đi của dữ liệu.

Ví dụ JUnit (mã minh họa phụ thuộc JUnit 5, không biên dịch độc lập chỉ bằng JDK):

~~~java
// assertEquals(State.MISSING, NumericPolicyDemo.parse(null).state());
// assertEquals(State.NAN, NumericPolicyDemo.parse("NaN").state());
// assertEquals(State.INVALID, NumericPolicyDemo.parse("abc").state());
// assertEquals(State.POS_INFINITY, NumericPolicyDemo.parse("1e400").state());
~~~

Khi test số thực hữu hạn có làm tròn, phải chọn dung sai phù hợp thay vì dùng phép `==` cho mọi kết quả tính toán. Khi phân biệt `-0.0` và `+0.0` là yêu cầu, cần test theo bit hoặc `Double.compare`, không dùng duy nhất `==`.

## 15. Lỗi thiết kế phổ biến

| Sai lầm | Vì sao nguy hiểm | Cách xử lý |
|---|---|---|
| "Null bằng 0" | Mất thông tin thiếu dữ liệu | Chọn default có chủ đích hoặc giữ trạng thái |
| "NaN chính là missing" | NaN là giá trị chấm động đặc biệt, có thể sinh bởi phép toán lỗi | Phân biệt NAN và MISSING khi cần |
| "Parse double thành công thì số hữu hạn" | `"NaN"`, `"Infinity"` và `"1e400"` vẫn parse được | Gọi `Double.isFinite` |
| Kiểm tra `x == Double.NaN` | Luôn false | `Double.isNaN(x)` |
| Gọi `mapToDouble(Double::doubleValue)` trên list có null | Auto-unboxing gây NPE | Kiểm tra/lọc null đúng chính sách |
| Coi `OptionalDouble.of(NaN)` là empty | Presence và tính hữu hạn khác nhau | Kiểm tra cả `isPresent` và `Double.isFinite` |
| Bỏ missing trong trung bình mà không nói rõ | Người đọc không biết mẫu số | Công bố công thức và số bản ghi được tính |
| Dùng sentinel có thể là số hợp lệ | Không phân biệt thực và thiếu | Status/nullable type được mô tả rõ |
| Gửi NaN/Infinity làm JSON number | Không hợp lệ theo JSON chuẩn | Chuyển thành trạng thái, chuỗi được đặc tả, hoặc từ chối |
| Cho BigDecimal nhận NaN | BigDecimal không hỗ trợ non-finite | Validate trước khi chuyển |
| Giả sử mọi database hỗ trợ NaN giống nhau | Driver/kiểu cột/hệ quản trị khác nhau | Xác minh contract của storage |
| Cho rằng `-0.0` hoàn toàn không khác `0.0` | Có thể khác khi chia và sắp xếp | Giữ hoặc chuẩn hóa dấu 0 có chủ đích |
| Chỉ kiểm tra giá trị cuối, không lưu lý do lỗi | Khó phát hiện suy giảm chất lượng nguồn | Thống kê missing/invalid/non-finite riêng |
| Dùng toán tử điều kiện với wrapper null và primitive | Có thể auto-unbox null gây NPE | Kiểm tra null và kiểu biểu thức |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `Double.NaN == Double.NaN` trả gì? Kiểm tra NaN đúng cách ra sao?

**Đáp án:** Trả `false`. Dùng `Double.isNaN(value)`.

**Câu 2.** `Double.parseDouble("1e400")` có nhất thiết ném `NumberFormatException` không?

**Đáp án:** Không. Nó có thể cho `Infinity` vì giá trị vượt phạm vi hữu hạn của `double`. Cần kiểm tra `Double.isFinite` sau parse.

**Câu 3.** `OptionalDouble.of(Double.NaN).isPresent()` là true hay false?

**Đáp án:** `true`. Optional có chứa một giá trị primitive NaN; NaN không đồng nghĩa với missing.

**Câu 4.** Có nên dùng `new BigDecimal("0")` để thay mọi `null` của cột số tiền không?

**Đáp án:** Chỉ khi hợp đồng xác nhận thiếu tiền được hiểu là 0. Thông thường phải phân biệt "không có dữ liệu" với "số tiền đúng bằng 0", rồi quyết định chính sách rõ ràng.

**Câu 5.** Trong bốn phép đo `10.0`, `null`, `20.0`, `30.0`, trung bình là 20 hay 15?

**Đáp án:** Nếu bỏ qua missing, trung bình là 20 trên ba phép đo hợp lệ. Nếu thay missing bằng 0, trung bình là 15 trên bốn bản ghi. Chính sách nghiệp vụ quyết định kết quả; không có mặc định đúng phổ quát.

**Câu 6.** Vì sao `Double.compare(-0.0, 0.0)` trả giá trị âm dù `-0.0 == 0.0` là true?

**Đáp án:** `Double.compare` sử dụng thứ tự cho giá trị `double` có phân biệt dấu của 0. Toán tử `==` tuân theo so sánh số học IEEE 754.

**Câu 7.** `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` trả BigDecimal mang Infinity không?

**Đáp án:** Không. `BigDecimal` không có giá trị vô cực; chuyển non-finite vào `BigDecimal` không hợp lệ và gây `NumberFormatException`.

**Câu 8.** Khi serialize JSON, được ghi `{"x":NaN}` như một JSON number chuẩn không?

**Đáp án:** Không. JSON chuẩn không có literal số NaN/Infinity. Cần định nghĩa cách chuyển trạng thái hoặc từ chối theo contract.

**Câu 9.** Vì sao `List<Double>` chứa null, sau đó `mapToDouble(Double::doubleValue)` có thể ném NPE?

**Đáp án:** Mapping cần `double` primitive, nên cố unbox phần tử null. Phải xử lý null trước theo quy tắc nhập dữ liệu.

**Câu 10.** Tại sao chỉ trả giá trị trung bình sau khi lọc số không hữu hạn là chưa đủ cho giám sát chất lượng dữ liệu?

**Đáp án:** Không biết bao nhiêu giá trị bị loại và lý do nào. Nên trả/báo thêm số bản ghi missing, invalid, NaN, Infinity cùng mẫu số thực được dùng cho thống kê.

---

**Ghi nhớ:** Trong Java, `null`/missing mô tả sự hiện diện của dữ liệu; `NaN` và ±Infinity là các giá trị đặc biệt của `float`/`double`; `0` là một giá trị hợp lệ; `BigDecimal`/`BigInteger` chỉ đại diện cho số hữu hạn trong miền của chúng. **Thiết kế chính sách dữ liệu trước khi tính toán**: phân biệt trạng thái, kiểm tra finite/range, định nghĩa ý nghĩa của thiếu dữ liệu, kiểm soát phép tổng hợp và công bố cách serialize. Đừng để unboxing, phép lọc hay một giá trị sentinel tự quyết định nghiệp vụ thay bạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-005">Kiểm tra đầu vào trước chuyển đổi có thể mất dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra đầu vào trước chuyển đổi có thể mất dữ liệu trong Java

## 1. Vấn đề: chuyển đổi thành công vẫn có thể tạo ra dữ liệu sai

Trong Java, một phép chuyển đổi số có thể biên dịch và chạy thành công nhưng **không bảo toàn giá trị đầu vào**. Ví dụ:

~~~java
int truncated = (int) 12.9;          // 12: mất phần lẻ
int overflowed = (int) 2147483648L; // -2147483648: mất các bit cao
int invalid = (int) Double.NaN;      // 0: đầu vào không phải số
~~~

Nếu chỉ kiểm tra kết quả sau khi ép kiểu, thông tin cần để phát hiện lỗi đã biến mất. Đây là lý do phải kiểm tra đầu vào **trước** những phép chuyển đổi có khả năng cắt phần lẻ, tràn miền, underflow hoặc làm tròn.

Trước một phép chuyển đổi, cần trả lời: miền giá trị hợp lệ là gì, kiểu đích có biểu diễn được giá trị đó không, có bắt buộc giữ nguyên độ chính xác không, và nếu không giữ được thì **từ chối** hay **làm tròn theo chính sách rõ ràng**?

Bài học tập trung vào dữ liệu số của Java Core: `int`, `long`, `float`, `double`, `BigInteger`, `BigDecimal` và chuỗi số. Mục tiêu là giữ dữ liệu đúng theo hợp đồng ứng dụng, không đơn thuần tránh exception.

## 2. Phân loại những mất mát thường gặp

| Chuyển đổi | Điều gì có thể xảy ra? | Ví dụ | Cách phòng ngừa |
| --- | --- | --- | --- |
| `long → int` | Mất bit cao, đổi dấu | `2147483648L → -2147483648` | `Math.toIntExact` |
| `double → int` | Cắt về 0 phần thập phân | `12.9 → 12` | Kiểm tra số nguyên rồi mới ép |
| `double → int` | NaN thành 0, quá miền bị bão hòa ở cực trị | `NaN → 0` | `Double.isFinite`, kiểm tra biên |
| `int → float` | Mất chữ số có nghĩa | `16777217 → 16777216` | Kiểm tra biểu diễn chính xác |
| `long → double` | Làm tròn số nguyên lớn | `9007199254740993 → 9007199254740992` | Giữ `long` hoặc dùng kiểu chính xác |
| `double → float` | Giảm độ chính xác, có thể vô cực/zero | `0.1d → float` | Kiểm tra hữu hạn và round-trip |
| `BigDecimal → int/long` | Mất phần thập phân hoặc tràn miền | `12.75.intValue() → 12` | `intValueExact`, `longValueExact` |
| `BigDecimal → double` | Xấp xỉ nhị phân, overflow/underflow | `0.1`, `1E+400` | Giữ `BigDecimal` nếu phải chính xác |
| Chuỗi → số | Cú pháp sai hoặc giá trị ngoài miền nghiệp vụ | `"NaN"`, `"-5"`, `"1e100"` | Parse và validate nhiều lớp |

Cần phân biệt **phạm vi (range)**, **độ chính xác (precision)** và **quy tắc làm tròn (rounding)**. Một giá trị nằm trong phạm vi của kiểu đích vẫn có thể không được biểu diễn chính xác. Ngược lại, số `12.000` có ba chữ số sau dấu thập phân nhưng có thể chuyển thành số nguyên 12 **không mất giá trị**.

## 3. Từ long sang int: dùng Math.toIntExact

`int` là số nguyên có dấu 32 bit, từ `-2147483648` đến `2147483647`; `long` là số nguyên có dấu 64 bit.

~~~java
long source = 2_147_483_648L;
int wrong = (int) source; // -2147483648: vẫn chạy được
~~~

Java không ném exception khi ép hẹp kiểu nguyên thủy từ long sang int. Để bảo đảm chính xác:

~~~java
int safe = Math.toIntExact(source);
// ArithmeticException vì source vượt Integer.MAX_VALUE
~~~

`Math.toIntExact(long)` trả int nếu giữ nguyên được giá trị số, nếu không ném `ArithmeticException`.

Kiểm tra thủ công hợp lệ khi cần một thông báo lỗi cụ thể:

~~~java
static int toIntChecked(long value) {
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Ngoài phạm vi int");
    }
    return (int) value;
}
~~~

Đoạn trên là **mảnh phương thức**; đặt trong một lớp Java để chạy. Việc ép ở dòng cuối chỉ diễn ra sau khi kiểm tra đủ hai biên.

**Chú ý:** `Math.toIntExact` không sửa được một phép tính đã tràn từ trước. Nếu `int a + int b` đã overflow, chuyển tổng sai ấy sang long cũng không khôi phục được giá trị ban đầu.

## 4. Từ double sang int: hữu hạn, trong miền và không có phần lẻ

Với phép ép `(int) doubleValue`, Java cắt phần lẻ **về phía 0**. Vì vậy `12.9 → 12`, `-12.9 → -12`. Ngoài ra:

- `Double.NaN` bị chuyển thành 0.
- Giá trị quá lớn hoặc `Double.POSITIVE_INFINITY` bị bão hòa về `Integer.MAX_VALUE`; quá nhỏ hoặc âm vô cực về `Integer.MIN_VALUE`.
- `-0.0` trở thành số nguyên 0, không giữ dấu của zero.

Nếu yêu cầu là một **giá trị số nguyên chính xác**, hãy kiểm tra trước:

~~~java
static int doubleToIntChecked(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Không phải số hữu hạn");
    }
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Ngoài phạm vi int");
    }
    if (value != Math.rint(value)) {
        throw new IllegalArgumentException("Có phần lẻ");
    }
    return (int) value;
}
~~~

`Math.rint(value)` được dùng để phát hiện giá trị có là số nguyên hay không; đoạn code **không dùng kết quả rint để âm thầm làm tròn**. Hàm này chấp nhận `-0.0` như giá trị số 0. Nếu bài toán cần bảo toàn dấu âm zero theo bit, không thể lưu đủ thông tin đó trong int; phải cấm hoặc ghi riêng.

Nếu nguồn là số thập phân người dùng nhập, **đừng parse qua double trước** rồi mới kiểm tra tính nguyên, vì giá trị lớn hoặc nhiều chữ số đã có thể bị làm tròn. Dùng `BigDecimal` từ chuỗi và `intValueExact()` khi muốn giữ giá trị thập phân chính xác.

## 5. Vì sao số nguyên chuyển sang float/double vẫn có thể mất độ chính xác?

Dù `float` hoặc `double` có thể chứa các giá trị rất lớn, chúng có số bit dành cho **chữ số có nghĩa** hữu hạn.

- `float` theo IEEE 754 binary32 có 24 bit độ chính xác nhị phân. Mọi số nguyên trong khoảng `[-2^24, 2^24]` được biểu diễn chính xác, nhưng không phải mọi số ngoài khoảng.
- `double` theo IEEE 754 binary64 có 53 bit độ chính xác nhị phân. Mọi số nguyên trong khoảng `[-2^53, 2^53]` được biểu diễn chính xác, nhưng không phải mọi số ngoài khoảng.

Ví dụ có thể chạy trong một phương thức:

~~~java
int number = 16_777_217;
float asFloat = (float) number;

System.out.println((int) asFloat); // 16777216
// Mất 1 so với number=16777217.
~~~

Với long:

~~~java
long number = 9_007_199_254_740_993L;
double asDouble = (double) number;

System.out.println((long) asDouble); // 9007199254740992
// Mất 1 so với long ban đầu.
~~~

Một **cái bẫy khi kiểm tra lại**:

~~~java
long original = 9_007_199_254_740_993L;
double converted = (double) original;
boolean looksEqual = converted == original; // Có thể true!
~~~

Trong phép so sánh `double == long`, Java **chuyển cả toán hạng long sang double**. Vì cả hai phía có thể bị làm tròn như nhau, `looksEqual` có thể true dù số nguyên gốc đã mất độ chính xác. Tương tự khi so `float` và `int`.

Nếu mục tiêu là bảo đảm mọi giá trị trong một miền đều giữ nguyên, thiết kế miền vào khoảng đủ chính xác. Nếu cần xác minh một `long` cụ thể ngoài miền đó, có thể so sánh thông qua biểu diễn chính xác của giá trị double thực tế:

~~~java
static boolean exactlyRepresentableAsDouble(long value) {
    double asDouble = (double) value;
    return java.math.BigDecimal.valueOf(value)
            .compareTo(new java.math.BigDecimal(asDouble)) == 0;
}
~~~

Trong phương thức này, `new BigDecimal(asDouble)` được dùng **có chủ đích** để tái tạo chính xác giá trị nhị phân đã được biểu diễn trong `double`; không nhầm với `BigDecimal.valueOf(double)`, vốn dùng chuỗi thập phân chuẩn của double. Có thể dùng `BigInteger` để kiểm tra giá trị nguyên theo một thiết kế tương đương.

## 6. Từ double sang float: phép chuyển hẹp có thể làm tròn

Một số `double` hữu hạn có thể không biểu diễn được đúng ở `float`; một số lớn thành vô cực hoặc số rất nhỏ khác 0 thành 0.

Để yêu cầu **không mất thêm giá trị so với double hiện tại**:

~~~java
static float doubleToFloatChecked(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Không hữu hạn");
    }
    float result = (float) value;
    if (!Float.isFinite(result) || (double) result != value) {
        throw new IllegalArgumentException("Mất độ chính xác khi ép float");
    }
    return result;
}
~~~

Mỗi giá trị float hữu hạn khi chuyển lên double đều được biểu diễn chính xác. Vì thế, so giá trị float sau ép và chuyển ngược với double trước ép giúp kiểm tra **mất mát mới tại bước chuyển**. Không thể dùng phép thử này để khôi phục giá trị thập phân gốc đã bị làm tròn trước đó khi parse hoặc tính bằng double.

Đây là phép kiểm tra giá trị số. Nếu phải bảo toàn cả chi tiết biểu diễn như dấu của zero, cần kiểm tra/ghi nhận bit dấu riêng.

## 7. BigDecimal: các phép chuyển exact

Đối với giá trị chính xác theo thập phân, `BigDecimal` cung cấp hai nhóm API chuyển đổi:

| Phương thức | Chính sách |
| --- | --- |
| `intValue()`, `longValue()` | Có thể cắt phần lẻ và/hoặc bỏ bit cao |
| `intValueExact()`, `longValueExact()` | Ném `ArithmeticException` nếu phần lẻ khác 0 hoặc vượt phạm vi |
| `toBigInteger()` | Cắt phần lẻ theo hướng 0 |
| `toBigIntegerExact()` | Ném `ArithmeticException` nếu có phần lẻ khác 0 |

Ví dụ:

~~~java
BigDecimal a = new BigDecimal("12.000");
BigDecimal b = new BigDecimal("12.001");

System.out.println(a.intValueExact()); // 12
// b.intValueExact();                   // ArithmeticException
~~~

`12.000` vẫn là số nguyên về giá trị; `BigDecimal.scale() == 3` không có nghĩa phép chuyển sang int chắc chắn mất dữ liệu. Kiểm tra exact theo **giá trị**, thay vì mặc định cấm mọi scale dương.

Với `BigDecimal.doubleValue()`, bạn không có bảo đảm exact tương tự. `0.1` thập phân không được biểu diễn chính xác bằng binary64; giá trị quá lớn như `1E+400` có thể thành vô cực. Nếu cần giữ tiền hoặc dữ liệu decimal chính xác, tiếp tục tính bằng `BigDecimal` và đừng đi vòng qua double.

`BigDecimal` không tự động kiểm tra miền nghiệp vụ. Giá trị có thể chuyển exact sang long nhưng vẫn vi phạm quy tắc “số tiền không âm” hoặc “tối đa 1.000 đơn vị”. Cần kiểm tra cả **ý nghĩa** lẫn **khả năng biểu diễn**.

## 8. Quy trình kiểm tra đầu vào chuỗi số

Khi dữ liệu đến từ JSON, biểu mẫu, CSV hoặc request HTTP, nên giữ **chuỗi gốc** nếu cần kiểm soát định dạng và chưa chắc kiểu số đích. Một quy trình có thể gồm:

1. **Sự hiện diện và độ dài:** null, trống, chuỗi quá dài.
2. **Cú pháp cho phép:** dấu âm, dấu cộng, khoảng trắng, dấu chấm thập phân, số mũ, dấu phân cách hàng nghìn, ký tự đặc biệt.
3. **Parse bằng kiểu đủ chính xác:** ví dụ `BigDecimal(String)` cho số thập phân; `BigInteger(String)` cho số nguyên rất lớn.
4. **Miền nghiệp vụ:** số không âm, khoảng cho phép, đơn vị đo, số lượng nguyên, precision/scale.
5. **Khả năng biểu diễn ở kiểu đích:** exact method, giới hạn min/max và kiểm tra mất chính xác nếu cần.
6. **Chính sách khi không exact:** từ chối hoặc làm tròn theo `RoundingMode` được quy định.
7. **Chỉ lưu/sử dụng kết quả đã qua validation.**

Không được coi “parse thành công” là “dữ liệu hợp lệ”. Chẳng hạn `Double.parseDouble("NaN")` và `Double.parseDouble("Infinity")` có thể tạo giá trị đặc biệt, nên khi chỉ chấp nhận số hữu hạn vẫn phải kiểm tra `Double.isFinite`.

`new BigDecimal("1E+3")` có thể parse thành 1000, nhưng nếu định dạng đầu vào không cho phép số mũ, vẫn phải từ chối ở **tầng cú pháp**. Tương tự, `"-0.00"` có giá trị số bằng 0 trong `BigDecimal`; nếu hợp đồng cấm ký tự dấu trừ ở đầu chuỗi dù giá trị bằng 0, phải kiểm tra chuỗi trước khi parse.

## 9. Ví dụ nghiệp vụ: đổi tiền thập phân sang đơn vị nhỏ nhất

Giả sử đầu vào là **số tiền dạng chuỗi**, lưu thành `long` biểu thị cents. Quy tắc:

- Giá trị phải không âm.
- Chỉ chấp nhận giá trị có thể chuyển chính xác sang số nguyên cents. `12.340` được phép vì chữ số cuối bị loại là 0.
- Số cents phải nằm trong phạm vi `long`.
- Dữ liệu sai cú pháp, âm, quá chính xác hoặc quá lớn bị từ chối.

Triển khai:

~~~java
static long toCents(String raw) {
    if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("Thiếu số tiền");
    }
    BigDecimal amount = new BigDecimal(raw.strip());
    if (amount.signum() < 0) {
        throw new IllegalArgumentException("Số tiền âm");
    }
    return amount.movePointRight(2).longValueExact();
}
~~~

`movePointRight(2)` nhân giá trị decimal lên 100 mà không làm tròn; `longValueExact()` yêu cầu giá trị sau nhân 100 là số nguyên và còn nằm trong miền long. Như vậy chuyển `12.34 → 1234`, `12.340 → 1234`; từ chối `12.345` vì tương ứng 1234.5 cents.

Đặc tả trên kiểm tra **giá trị được giữ nguyên**. Nếu quy tắc bắt buộc biểu diễn đầu vào có **tối đa hai ký tự chữ số sau dấu chấm**, thì ngay cả `12.340` cũng phải bị loại bằng kiểm tra cú pháp/scale bổ sung. Hai quy tắc này khác nhau.

Đây là ví dụ kỹ thuật, không khẳng định mọi loại tiền tệ có đơn vị nhỏ nhất bằng 1/100; đơn vị và chính sách làm tròn phải lấy từ nghiệp vụ.

## 10. Chương trình Java 17 hoàn chỉnh và kết quả chính xác

Tạo file `CheckedConversionDemo.java`:

~~~java
import java.math.BigDecimal;

public class CheckedConversionDemo {
    static long toCents(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing amount");
        }
        BigDecimal amount = new BigDecimal(raw.strip());
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        return amount.movePointRight(2).longValueExact();
    }

    static void showCents(String raw) {
        try {
            System.out.println(raw + " -> " + toCents(raw) + " cents");
        } catch (IllegalArgumentException | ArithmeticException ex) {
            System.out.println(raw + " -> REJECTED");
        }
    }

    static int toIntChecked(double value) {
        if (!Double.isFinite(value)
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("Not exact int");
        }
        return (int) value;
    }

    public static void main(String[] args) {
        showCents("12.34");
        showCents("12.340");
        showCents("12.345");
        showCents("-1.00");
        showCents("92233720368547758.08");

        System.out.println("long 42 -> " + Math.toIntExact(42L));
        try {
            Math.toIntExact(2_147_483_648L);
        } catch (ArithmeticException ex) {
            System.out.println("int overflow -> REJECTED");
        }

        System.out.println("unsafe 42.9 -> " + (int) 42.9);
        System.out.println("checked 42.0 -> " + toIntChecked(42.0));
        try {
            toIntChecked(42.9);
        } catch (IllegalArgumentException ex) {
            System.out.println("fraction -> REJECTED");
        }
        System.out.println("NaN finite -> " + Double.isFinite(Double.NaN));
        System.out.println("NaN cast -> " + (int) Double.NaN);
        System.out.println("exact decimal int -> "
                + new BigDecimal("12.000").intValueExact());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac CheckedConversionDemo.java
java CheckedConversionDemo
~~~

Kết quả:

~~~text
12.34 -> 1234 cents
12.340 -> 1234 cents
12.345 -> REJECTED
-1.00 -> REJECTED
92233720368547758.08 -> REJECTED
long 42 -> 42
int overflow -> REJECTED
unsafe 42.9 -> 42
checked 42.0 -> 42
fraction -> REJECTED
NaN finite -> false
NaN cast -> 0
exact decimal int -> 12
~~~

Diễn giải những điểm then chốt:

- `12.345 × 100 = 1234.5`, không thể đổi thành long chính xác.
- `92233720368547758.08 × 100 = 9223372036854775808`, lớn hơn `Long.MAX_VALUE` đúng một đơn vị.
- `Math.toIntExact(2_147_483_648L)` từ chối thay vì âm thầm trở thành số âm.
- `42.9` bị ép thẳng thành 42; `toIntChecked(42.9)` từ chối vì phần lẻ.
- `Double.NaN` không phải số hữu hạn, nhưng phép ép thẳng sang int lại cho 0.
- `new BigDecimal("12.000").intValueExact()` thành công vì các chữ số thập phân đều bằng 0.

## 11. Khi làm tròn được phép: nói rõ thời điểm và chế độ

“Không mất dữ liệu” và “làm tròn có kiểm soát” là **hai hợp đồng khác nhau**.

Khi phải giữ chính xác hai chữ số sau dấu thập phân hoặc từ chối:

~~~java
BigDecimal value = new BigDecimal("12.345");
BigDecimal exact = value.setScale(
        2, java.math.RoundingMode.UNNECESSARY);
// ArithmeticException: cần làm tròn, không được phép.
~~~

Khi chính sách cho phép làm tròn:

~~~java
BigDecimal value = new BigDecimal("12.345");
BigDecimal rounded = value.setScale(
        2, java.math.RoundingMode.HALF_EVEN);
// 12.34: trường hợp đúng nửa bước, chữ số cuối giữ là 4 (chẵn).
~~~

Đây là **các mảnh minh họa** bên trong lớp có import `BigDecimal`. Chọn `HALF_UP`, `HALF_EVEN`, `UP`, `DOWN` hoặc mode khác theo quy tắc thực tế, không dựa vào tên gọi.

`setScale(2, UNNECESSARY)` vẫn cho phép `12.340` thành `12.34`, vì việc bỏ số 0 cuối không đổi giá trị số. Nếu cần giữ hoặc kiểm tra **định dạng nguyên gốc** thì phải lưu/validate chuỗi trước khi chuẩn hóa.

Không tự làm tròn sau từng phép tính trung gian. Hai cách “làm tròn từng hạng tử rồi cộng” và “cộng chính xác rồi làm tròn” có thể cho kết quả khác nhau. Đặc tả phải nêu rõ **thời điểm** làm tròn.

## 12. Mất dữ liệu trước chuyển đổi: tràn phép tính và chia số nguyên

Đôi khi kiểu đích đủ rộng, nhưng phép tính **đã bị sai trước khi gán**:

~~~java
int a = 2_000_000_000;
int b = 2_000_000_000;

long wrong = a + b;       // phép cộng int đã tràn trước
long correct = (long) a + b; // 4000000000
~~~

`long wrong` không chứa tổng toán học chính xác vì hai toán hạng đã cộng trong miền int. Nếu kết quả bắt buộc ở miền int và không được overflow, dùng `Math.addExact(a, b)`, `Math.multiplyExact`, `Math.subtractExact` hoặc API phù hợp.

Tương tự:

~~~java
int numerator = 5;
int denominator = 2;

double wrong = numerator / denominator;          // 2.0
double approximate = (double) numerator / denominator; // 2.5
~~~

Biểu thức `numerator / denominator` thực hiện chia nguyên trước, nên khi gán sang double đã mất phần lẻ. Nếu muốn tính thập phân **chính xác** hoặc làm tròn theo quy tắc rõ ràng, dùng `BigDecimal.divide(..., scale, roundingMode)`; nếu đầu vào và đầu ra cho phép xấp xỉ, chuyển sang double trước khi chia có thể phù hợp.

Kiểm tra `Math.toIntExact` **sau** một phép tính int bị tràn hoặc chuyển sang `BigDecimal` **sau** phép chia nguyên không thể tự khôi phục giá trị đã mất.

## 13. Kiểm tra miền giá trị và định dạng: không được đánh đồng

Đầu vào `"12.000"` có scale 3 nhưng giá trị là số nguyên 12. Do đó:

- Muốn bảo đảm giá trị chuyển sang int không thay đổi: dùng `intValueExact()`.
- Muốn bảo đảm giá trị tiền đổi thành cents không mất phần lẻ: dùng `movePointRight(2).longValueExact()`.
- Muốn quy định **chuỗi có tối đa hai chữ số sau dấu thập phân**: kiểm tra cấu trúc chuỗi đầu vào hoặc scale theo cú pháp được phép.
- Muốn giới hạn miền như `0 <= amount <= 1000`: so sánh `BigDecimal.compareTo` với các biên.
- Muốn giới hạn số chữ số có nghĩa: xác định cách xử lý precision, dấu 0 cuối và biểu diễn khoa học, sau đó kiểm tra riêng.

`BigDecimal.scale()` không đồng nghĩa số chữ số có nghĩa. Ví dụ `new BigDecimal("12.000")` có scale 3 và precision 5. Một chuỗi dùng số mũ có thể tạo `BigDecimal` có scale âm, nên không suy ra quy tắc lexical của input từ mỗi con số scale.

Đặc biệt, `new BigDecimal("-0.00").signum() == 0`; nếu chuỗi đầu vào có dấu trừ là bất hợp lệ **ngay cả khi bằng 0**, validation phải xét chuỗi gốc trước khi chuyển thành BigDecimal.

## 14. Lỗi thường gặp

**1. Ép rồi mới so biên.** Sau `(int) longValue`, bit cao đã biến mất. Hãy kiểm tra trên `long` hoặc dùng `Math.toIntExact`.

**2. Dùng `BigDecimal.intValue()` khi yêu cầu exact.** Nó có thể cắt phần lẻ hoặc tràn; dùng `intValueExact()`.

**3. Giả định parse thành công là hợp lệ.** `Double.parseDouble("NaN")` có thể trả NaN; một `BigDecimal` âm vẫn parse thành công dù nghiệp vụ cấm giá trị âm.

**4. Dùng `value == Double.NaN` để kiểm tra NaN.** Theo quy tắc IEEE 754, NaN không bằng chính nó. Dùng `Double.isNaN`; nếu muốn cả NaN và vô cực bị loại, dùng `Double.isFinite`.

**5. Cho rằng số được biểu diễn bằng double/float thì phải chính xác.** Các số nguyên rất lớn và decimal như `0.1` có thể bị làm tròn. Miền giá trị rộng không đồng nghĩa precision vô hạn.

**6. Chuyển lại rồi so bằng `==` mà không xét quy tắc promotion.** `doubleValue == longValue` có thể chuyển cả hai về double, che khuất mất dữ liệu.

**7. Tạo `new BigDecimal(0.1)` rồi mong nó mang giá trị thập phân 0.1 chính xác.** Literal `0.1` đã là double xấp xỉ. Với input thập phân chính xác, dùng `new BigDecimal("0.1")`; khi nhận double sẵn có, phải hiểu giới hạn thông tin nguồn.

**8. Làm tròn mặc định không có chính sách.** `HALF_UP` hay `DOWN` đều là quyết định nghiệp vụ. Nếu yêu cầu không được mất giá trị, dùng exact hoặc `RoundingMode.UNNECESSARY`.

**9. Nhầm quy tắc giá trị với quy tắc biểu diễn.** `12.340` có thể đổi exact sang 1234 cents nhưng không đáp ứng chính sách chuỗi tối đa hai chữ số sau dấu chấm.

**10. Chỉ kiểm tra kết quả cuối mà bỏ phép tính trung gian.** Overflow của phép cộng int hoặc phần lẻ đã bị chia nguyên cắt không thể được khôi phục nhờ kiểu đích rộng hơn.

**11. Tự động thay lỗi parse bằng 0.** Dữ liệu thiếu hoặc sai không nhất thiết có ý nghĩa số học bằng 0. Chỉ áp dụng giá trị mặc định nếu hợp đồng quy định rõ.

**12. Bỏ qua test ở biên.** Giá trị nằm đúng cực trị, vượt cực trị một đơn vị, gần 0, phần lẻ rất nhỏ, NaN, vô cực và dạng khoa học đều có thể phát hiện lỗi mà ví dụ số bình thường không bộc lộ.

## 15. Bài tập và đáp án

### Bài 1: Tìm dữ liệu mất ở đâu

~~~java
long original = 2_147_483_648L;
System.out.println((int) original);

double fraction = -12.9;
System.out.println((int) fraction);

BigDecimal price = new BigDecimal("8.010");
System.out.println(price.movePointRight(2).longValueExact());
~~~

Kết quả khi đặt trong chương trình có import BigDecimal:

~~~text
-2147483648
-12
801
~~~

Hai trường hợp đầu mất dữ liệu: long → int vượt miền và double → int cắt phần lẻ. Trường hợp cuối bảo toàn giá trị: `8.010 × 100 = 801.0`, có thể chuyển exact thành long.

### Bài 2: Chọn đầu vào hợp lệ cho toCents

| Chuỗi | Kết quả theo `toCents` ở mục 9 | Giải thích |
| --- | --- | --- |
| `"0"` | `0` | Giá trị zero hợp lệ |
| `"9.9"` | `990` | Chuyển chính xác |
| `"9.999"` | Từ chối | `999.9` cents còn phần lẻ |
| `"9.990"` | `999` | Bỏ số 0 cuối không đổi giá trị |
| `"-0.00"` | `0` | BigDecimal biểu diễn zero, `signum == 0` |
| `"-0.01"` | Từ chối | Vi phạm điều kiện không âm |
| `"abc"` | Từ chối | Không parse được |

Nếu API cấm ký tự `-` ở đầu, kể cả số 0, phải bổ sung kiểm tra chuỗi đầu vào.

### Bài 3: Vì sao so sánh có thể sai?

~~~java
long original = 9_007_199_254_740_993L;
double narrowed = (double) original;
boolean same = narrowed == original;
~~~

`same` có thể true vì `original` được đưa sang double ngay trong phép so sánh. Nó **không** chứng minh chuyển đổi không mất dữ liệu. Hãy giữ miền đầu vào trong `[-2^53, 2^53]` khi muốn một bảo đảm đơn giản cho mọi số nguyên, hoặc kiểm tra bằng biểu diễn chính xác của từng giá trị.

### Bài 4: Thiết kế kiểm tra nhiệt độ

API nhận chuỗi nhiệt độ Celsius trong `[-100.00,100.00]`, lưu thành `int` số phần trăm độ. Thiết kế hợp lý: kiểm tra định dạng, parse `BigDecimal`, so sánh miền với hai giá trị biên, gọi `movePointRight(2).intValueExact()`, trả lỗi nếu không thể chuyển chính xác. Nếu chính sách giới hạn chữ số **trong chuỗi**, thêm một kiểm tra lexical trước đó.

### Bài 5: Chọn API phù hợp

- `long → int` không cho phép tràn: `Math.toIntExact`.
- `BigDecimal → long` không được mất phần lẻ: `longValueExact()`.
- `BigDecimal → BigInteger` không được cắt lẻ: `toBigIntegerExact()`.
- Kiểm tra `double` hữu hạn: `Double.isFinite`.
- Thu gọn scale nhưng cấm làm tròn thay đổi giá trị: `setScale(target, RoundingMode.UNNECESSARY)`.
- Tính số nguyên mà cấm overflow: các phương thức `Math.*Exact`.

## 16. Checklist trước khi chấp nhận chuyển đổi

1. Dữ liệu gốc còn được giữ đủ chính xác để kiểm tra không?
2. Đã xác thực cú pháp, miền, đơn vị và chính sách về dấu, số 0, chữ số và exponent chưa?
3. Đã kiểm tra phạm vi kiểu đích và khả năng biểu diễn chính xác chưa?
4. Nếu nguồn là dấu phẩy động, đã loại NaN/vô cực và xét phần lẻ, underflow, overflow chưa?
5. Nếu dùng BigDecimal, đã chọn `*ValueExact` hoặc chính sách `RoundingMode` rõ ràng chưa?
6. Các phép toán trước khi chuyển đổi có thể đã mất dữ liệu hay không?
7. Bài kiểm thử có đủ giá trị biên, ngay ngoài biên, số không hợp lệ và các trường hợp làm tròn không?

**Ghi nhớ:** Hãy kiểm tra **trước khi dữ liệu bị biến đổi**. Một kết quả mang kiểu `int`, `long`, `float` hay `double` hợp lệ không chứng minh nó bằng giá trị nguồn. Dùng các API exact, kiểm tra hữu hạn/range/precision, rồi mới chuyển; nếu chủ động làm tròn, hãy ghi rõ chính sách và thời điểm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-006">Đưa precision, scale và rounding vào hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Đưa precision, scale và rounding vào hợp đồng thiết kế dữ liệu số trong Java

## 1. Tại sao đây phải là hợp đồng, không chỉ là lựa chọn định dạng?

Một trường số như `amount`, `price`, `rate`, `distance` hoặc `quantity` không được định nghĩa đầy đủ chỉ bằng câu “kiểu `BigDecimal`”. Hai hệ thống đều dùng `BigDecimal` nhưng có thể bất đồng về số chữ số cho phép, ngưỡng lớn nhất và cách làm tròn. Kết quả: lỗi nhập dữ liệu, chênh lệch một đơn vị tiền nhỏ nhất khi đối soát, thay đổi ngoài ý muốn lúc ghi database, hoặc khác biệt giữa các endpoint.

Khi thiết kế dữ liệu số, cần coi ba yếu tố sau là **một phần hợp đồng công khai**:

- **Precision (độ chính xác chữ số):** tổng số chữ số có nghĩa mà giá trị được phép chứa.
- **Scale (vị trí thập phân):** số chữ số bên phải dấu thập phân trong biểu diễn `BigDecimal` (scale có thể âm).
- **Rounding (chính sách làm tròn):** điều gì xảy ra nếu phép tính hay dữ liệu đầu vào không thể biểu diễn tại precision/scale yêu cầu.

Ngoài ra hợp đồng thực tế còn phải nêu **đơn vị, miền giá trị, cách xử lý null, quy tắc parse/serialize, thứ tự thao tác và thời điểm làm tròn**. Không thể chỉ nhìn một câu `setScale(2)` mà suy ra tất cả các điều trên.

Ví dụ hợp đồng cụ thể:

> `totalAmount`: tiền theo một đơn vị tiền tệ đã xác định, không âm, tối đa 10 chữ số phần nguyên và 2 chữ số phần thập phân; dữ liệu phải biểu diễn chính xác tới đơn vị 0.01; không âm thầm làm tròn khi nhận input. Khi tính thuế, chỉ làm tròn đến 2 chữ số **ở bước tính thuế của dòng hóa đơn**, sử dụng `HALF_EVEN`. Khi xuất API, chuỗi decimal phải giữ chính xác 2 chữ số thập phân.

Chính sách này có thể diễn đạt bằng `DECIMAL(12,2)` ở cơ sở dữ liệu và `BigDecimal` trong Java, **nhưng hai khai báo đó chưa đủ để định nghĩa toàn bộ quy trình tính**. Phải bổ sung validation, thời điểm rounding và định dạng truyền dữ liệu.

## 2. Precision, scale và unscaled value của `BigDecimal`

Một đối tượng `BigDecimal` được mô tả về mặt giá trị bởi:

```text
value = unscaledValue × 10^(-scale)
```

Ví dụ:

| Chuỗi dùng để khởi tạo | `unscaledValue()` | `scale()` | `precision()` | Giá trị số học |
|---|---:|---:|---:|---:|
| `"123.45"` | `12345` | `2` | `5` | 123.45 |
| `"123.450"` | `123450` | `3` | `6` | 123.45 |
| `"0.00"` | `0` | `2` | `1` | 0 |
| `"1E+3"` | `1` | `-3` | `1` | 1000 |
| `"0.001"` | `1` | `3` | `1` | 0.001 |

`precision()` là số chữ số của phần *unscaled value* (theo quy tắc của `BigDecimal`), **không phải số chữ số sau dấu chấm**. `scale()` là độ dịch thập phân, **không phải số chữ số có nghĩa**. Precision của zero bằng 1.

Cùng giá trị số học có thể có precision và scale khác nhau:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
```

`compareTo` so sánh giá trị số học. `equals` của `BigDecimal` còn đòi **cùng scale**. Vì vậy, khi hợp đồng cần bảo toàn cách biểu diễn (ví dụ input phải có đúng 2 chữ số thập phân), phép so sánh `compareTo` không đủ để kiểm tra chi tiết đó.

**Ghi nhớ:** `DECIMAL(12,2)` trong database thường thể hiện tối đa 12 chữ số theo precision và scale 2 (tương ứng tối đa 10 chữ số phần nguyên). Nó không đồng nghĩa “12 chữ số bên trái dấu chấm và 2 chữ số bên phải”. Hành vi lưu/round khi vượt scale có thể khác nhau giữa các hệ CSDL và cấu hình; ứng dụng phải xác nhận rõ thay vì phó mặc driver/database quyết định.

## 3. `setScale` và `MathContext`: hai loại giới hạn khác nhau

### 3.1. `setScale(newScale, mode)`: điều chỉnh số chữ số thập phân

`setScale` tạo một `BigDecimal` với scale yêu cầu. Nếu tăng scale, Java bổ sung số 0 mà không thay đổi giá trị số học; nếu giảm scale, có thể phải làm tròn:

```java
new BigDecimal("12.3").setScale(2); // 12.30
new BigDecimal("12.345").setScale(2, RoundingMode.HALF_UP); // 12.35
```

Nếu giảm scale mà sẽ mất chữ số có nghĩa và không truyền rounding mode phù hợp, phép thay đổi chính xác không thể thực hiện. `setScale(2, RoundingMode.UNNECESSARY)` là cách **xác thực giá trị có thể biểu diễn đến 2 chữ số thập phân mà không làm tròn**.

Điểm khác nhau quan trọng:

- `new BigDecimal("12.300").setScale(2, UNNECESSARY)` thành công: kết quả `12.30`, phần bị bỏ chỉ là zero.
- `new BigDecimal("12.301").setScale(2, UNNECESSARY)` ném `ArithmeticException`: việc bỏ chữ số 1 sẽ mất giá trị.
- `new BigDecimal("12.3").setScale(2, UNNECESSARY)` thành công: thêm zero thành `12.30`.

Do đó, `UNNECESSARY` kiểm tra **khả năng biểu diễn chính xác**, không kiểm tra input có **chính xác** 2 chữ số trên văn bản hay không. Hai hợp đồng này phải phân biệt.

### 3.2. `MathContext(precision, mode)`: giới hạn tổng chữ số có nghĩa

`MathContext` đưa một precision áp dụng cho kết quả thao tác `BigDecimal`, cùng rounding mode. Ví dụ:

```java
BigDecimal x = new BigDecimal("123.456");

System.out.println(x.setScale(2, RoundingMode.HALF_EVEN)); // 123.46
System.out.println(x.round(new MathContext(4, RoundingMode.HALF_EVEN))); // 123.5
```

`setScale(2)` giữ **2 chữ số bên phải dấu chấm**; `MathContext(4)` giữ **4 chữ số có nghĩa trên toàn giá trị**. Đây là hai mục tiêu khác nhau, không thể dùng thay thế nhau.

`MathContext.UNLIMITED` có precision bằng 0, nghĩa là không giới hạn precision của phép toán mà nó áp dụng; nó **không có nghĩa** phép chia không kết thúc như `1/3` có thể biểu diễn chính xác hữu hạn. Đối với `BigDecimal.ONE.divide(new BigDecimal("3"))` không có rounding/scale, Java ném `ArithmeticException` vì thương không có biểu diễn thập phân hữu hạn.

### 3.3. Những kết quả dễ hiểu sai

| Phép toán | Kết quả / hành vi |
|---|---|
| `new BigDecimal("123.450").precision()` | `6` |
| `new BigDecimal("123.450").scale()` | `3` |
| `new BigDecimal("2.3").setScale(2)` | `2.30` |
| `new BigDecimal("2.345").setScale(2, HALF_UP)` | `2.35` |
| `new BigDecimal("2.345").setScale(2, HALF_EVEN)` | `2.34` |
| `new BigDecimal("2.355").setScale(2, HALF_EVEN)` | `2.36` |
| `new BigDecimal("123.456").round(MathContext(4, HALF_EVEN))` | `123.5` |

Chỉ sử dụng `MathContext` nếu hợp đồng nêu rõ precision trên **kết quả phép toán**; chỉ sử dụng `setScale` nếu muốn điều chỉnh **số chữ số thập phân**. Nhiều ứng dụng tiền tệ cần cả miền precision/scale của **giá trị lưu trữ**, nhưng ở mỗi phép tính trung gian không nhất thiết được làm tròn đến precision đó.

## 4. RoundingMode: quy tắc nghiệp vụ bắt buộc phải đặt tên

`java.math.RoundingMode` gồm nhiều chế độ. Khi không thể giữ nguyên số chữ số theo precision/scale mong muốn, cách làm tròn quyết định kết quả:

| Mode | Ý nghĩa | `2.345` → 2 chữ số | `-2.345` → 2 chữ số |
|---|---|---:|---:|
| `UP` | Ra xa 0 | 2.35 | -2.35 |
| `DOWN` | Về 0 | 2.34 | -2.34 |
| `CEILING` | Về +∞ | 2.35 | -2.34 |
| `FLOOR` | Về -∞ | 2.34 | -2.35 |
| `HALF_UP` | Gần nhất; ở đúng giữa thì xa 0 | 2.35 | -2.35 |
| `HALF_DOWN` | Gần nhất; ở đúng giữa thì về 0 | 2.34 | -2.34 |
| `HALF_EVEN` | Gần nhất; ở đúng giữa thì chữ số giữ lại cuối là chẵn | 2.34 | -2.34 |
| `UNNECESSARY` | Không cho phép làm tròn | Ngoại lệ | Ngoại lệ |

Lưu ý các cột kết quả trên giả định **đúng giá trị thập phân được tạo từ chuỗi** `"2.345"` và `"-2.345"`. Với `2.355`, mode `HALF_EVEN` cho `2.36`, vì chữ số hàng phần trăm giữ lại là 6 (chẵn). `HALF_EVEN` thường gọi là *banker's rounding*, nhưng việc phù hợp hay không còn tùy luật, chuẩn kế toán hoặc quy tắc sản phẩm.

Cách viết **không có rounding mode** thường là thiếu hợp đồng. Đặc biệt cần xác định:

- Khi nào **từ chối** đầu vào có nhiều chữ số hơn cho phép?
- Khi nào **được phép làm tròn**?
- Làm tròn ở bước nào: từng đơn giá, từng dòng, trước/sau thuế, hay chỉ cuối hóa đơn?
- Dùng mode nào, do ai quy định và có thay đổi theo loại phí/đơn vị tiền tệ không?

**Tránh** dùng `RoundingMode.DOWN` chỉ vì từ “down” nghe giống làm tròn xuống: với số âm, `DOWN` là **về 0**, trong khi `FLOOR` là về âm vô cực.

## 5. Hợp đồng số phải mô tả cả đơn vị, miền và cách biểu diễn

Một ví dụ về đặc tả dành cho API `POST /payments`:

| Thuộc tính hợp đồng | Ví dụ được quy định |
|---|---|
| Tên trường | `amount` |
| Đơn vị | Một loại tiền tệ được xác định, không suy đoán theo locale |
| Miền hợp lệ | `0.00 ≤ amount ≤ 9,999,999,999.99` |
| Giới hạn lưu trữ | `precision ≤ 12`, `scale = 2` sau chuẩn hóa |
| Quy tắc input | Chấp nhận nếu **chính xác** đến 0.01; `19.900` có thể chuẩn hóa thành `19.90`, `19.999` bị từ chối |
| Rounding khi nhận input | `UNNECESSARY` (cấm mất chữ số có nghĩa) |
| Rounding khi tính thuế | `HALF_EVEN` ở bước tính thuế theo dòng đã định |
| Cách xuất | Chuỗi thập phân `"19.90"`; không ép về `double` |
| Null / dữ liệu thiếu | Bị từ chối nếu `amount` bắt buộc |
| Overflow | Bị từ chối nếu tổng cuối vượt miền hỗ trợ |

Đây là **một ví dụ chính sách**, không phải luật chung cho mọi tiền tệ. Những đơn vị không có phần thập phân hoặc có số chữ số thập phân khác phải đặt scale theo cấu hình riêng. Miền hợp lệ, xử lý hoàn tiền (số âm), số học thuế và cách báo lỗi cũng cần tuân thủ yêu cầu thực tế.

**Hợp đồng ngữ nghĩa** và **hợp đồng định dạng** có thể khác nhau:

- Hợp đồng ngữ nghĩa “giá trị là bội số chính xác của 0.01” có thể chấp nhận `"19.900"` và chuẩn hóa về `19.90`.
- Hợp đồng văn bản “client **phải gửi chính xác 2 chữ số** sau dấu chấm” sẽ từ chối `"19.900"`, dù giá trị số học tương đương.

Muốn áp dụng hợp đồng định dạng, kiểm tra chuỗi đầu vào theo format đã công bố trước khi parse; không thể chỉ dựa vào `setScale(2, UNNECESSARY)` để xác nhận số chữ số có mặt trong văn bản gốc.

## 6. Ví dụ Java 17+ hoàn chỉnh: validate theo hợp đồng rồi chuẩn hóa

Bên dưới là ví dụ chính sách **số tiền không âm, tối đa 12 chữ số với scale 2**. Đầu vào có thể chứa nhiều zero ở cuối nhưng tuyệt đối không bị bỏ chữ số có nghĩa để vừa scale.

**Tệp `MoneyContractDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class MoneyContractDemo {
    private static final int SCALE = 2;
    private static final int PRECISION = 12;

    public static BigDecimal parseAmount(String input) {
        Objects.requireNonNull(input, "amount");

        // Giới hạn độ dài input trước khi parse để tránh dữ liệu quá lớn.
        if (input.length() > 100) {
            throw new IllegalArgumentException("Amount is too long");
        }

        BigDecimal parsed;
        try {
            parsed = new BigDecimal(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal", ex);
        }

        BigDecimal normalized;
        try {
            normalized = parsed.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Fractional cent", ex);
        }

        if (normalized.signum() < 0 || normalized.precision() > PRECISION) {
            throw new IllegalArgumentException("Amount outside contract");
        }

        return normalized;
    }

    private static void test(String input) {
        try {
            System.out.println(input + " -> " + parseAmount(input));
        } catch (IllegalArgumentException ex) {
            System.out.println(input + " -> REJECTED");
        }
    }

    public static void main(String[] args) {
        test("19.90");
        test("19.900");
        test("0");
        test("19.999");
        test("10000000000.00");
        test("-1.00");
        test("9999999999.99");
    }
}
```

Biên dịch và chạy:

```bash
javac MoneyContractDemo.java
java MoneyContractDemo
```

Kết quả:

```text
19.90 -> 19.90
19.900 -> 19.90
0 -> 0.00
19.999 -> REJECTED
10000000000.00 -> REJECTED
-1.00 -> REJECTED
9999999999.99 -> 9999999999.99
```

Giải thích:

1. **Parse bằng `new BigDecimal(String)`** để giữ giá trị thập phân mà client gửi. Không parse thành `double` trước.
2. **`setScale(2, UNNECESSARY)`** kiểm tra có mất phần có nghĩa hay không. `19.900` chuyển thành `19.90` mà không mất giá trị; `19.999` bị từ chối.
3. **`signum()`** từ chối số âm theo miền đã chọn.
4. **`precision() <= 12` sau khi scale đã được chuẩn hóa**: kết quả không có quá 12 chữ số có nghĩa, phù hợp miền `DECIMAL(12,2)` cho những giá trị không âm ở đây. Với `10000000000.00`, precision là 13 nên bị từ chối.
5. **Không auto-round đầu vào**: thay vì biến `19.999` thành `20.00` một cách âm thầm, hệ thống trả lỗi rõ ràng.

Trong ứng dụng thật, còn cần giới hạn kích thước HTTP/JSON, số ký tự và **mũ/scientific notation** tùy chính sách. Constructor `BigDecimal(String)` chấp nhận dạng số mũ như `"1E+3"`; nếu API chỉ cho phép dạng thập phân không có số mũ, hãy dùng validation cú pháp trước khi gọi constructor. `Objects.requireNonNull` ở ví dụ ném `NullPointerException` cho input null; tầng API có thể chuyển thành lỗi validation có cấu trúc theo quy ước ứng dụng.

### Nếu bắt buộc đúng hai chữ số trong văn bản đầu vào

Đây là **hợp đồng khác**, cần kiểm tra định dạng, ví dụ:

```java
// Mảnh mã minh họa trong phương thức nhận String text:
if (!text.matches("(0|[1-9][0-9]*)\\.[0-9]{2}")) {
    throw new IllegalArgumentException("Expected decimal with 2 places");
}
```

Biểu thức trên không chấp nhận dấu âm, số mũ, zero đứng đầu không cần thiết hay chuỗi thiếu phần thập phân; đó là lựa chọn format riêng. Trong tệp Java thực, chuỗi regex dùng `\\.` để khớp dấu chấm literal. Quy định về dấu `+`, khoảng trắng, locale, ký tự Unicode và độ dài cũng phải công bố rõ ràng nếu có.

## 7. Hợp đồng rounding ở bước tính toán: thứ tự có thể đổi kết quả

Một lỗi thiết kế kinh điển là ghi “làm tròn 2 chữ số” nhưng không chỉ rõ **làm tròn trước hay sau khi nhân**.

Giả sử một đơn giá chính xác nội bộ là `0.335` và số lượng là `3`; chính sách `HALF_UP` với 2 chữ số:

- **Làm tròn đơn giá trước**: `0.335 → 0.34`, rồi `0.34 × 3 = 1.02`.
- **Nhân trước, làm tròn cả dòng sau**: `0.335 × 3 = 1.005 → 1.01`.

Cả hai dùng **cùng rounding mode và scale**, nhưng cho **kết quả khác**. Một hợp đồng đủ rõ phải chọn một trong hai mô hình.

**Tệp `InvoiceRoundingDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceRoundingDemo {
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode POLICY = RoundingMode.HALF_UP;

    public static void main(String[] args) {
        BigDecimal rawUnitPrice = new BigDecimal("0.335");
        BigDecimal quantity = new BigDecimal("3");

        BigDecimal unitFirst = rawUnitPrice
                .setScale(MONEY_SCALE, POLICY)
                .multiply(quantity);

        BigDecimal lineFirst = rawUnitPrice
                .multiply(quantity)
                .setScale(MONEY_SCALE, POLICY);

        System.out.println("unitFirst=" + unitFirst);
        System.out.println("lineFirst=" + lineFirst);

        // Ví dụ khác: tính thuế từ giá trị gốc rồi làm tròn riêng thuế.
        BigDecimal net = new BigDecimal("19.99");
        BigDecimal rate = new BigDecimal("0.075");
        BigDecimal tax = net.multiply(rate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal gross = net.add(tax);

        System.out.println("tax=" + tax);
        System.out.println("gross=" + gross);
    }
}
```

```bash
javac InvoiceRoundingDemo.java
java InvoiceRoundingDemo
```

```text
unitFirst=1.02
lineFirst=1.01
tax=1.50
gross=21.49
```

Đây là **hai ví dụ chính sách độc lập**, không phải quy tắc thuế/pháp lý mặc định. Trong dự án thật phải làm rõ giá đưa vào có gồm thuế không, thuế tính theo dòng hay cả đơn, có bao nhiêu loại thuế, quy tắc phân bổ chênh lệch, loại tiền tệ, xử lý hoàn tiền và rounding mode bắt buộc.

**Nguyên tắc triển khai:** giữ precision thích hợp trong các bước trung gian và làm tròn **tại điểm được hợp đồng quy định**, không gọi `setScale(2, ...)` bừa bãi sau mọi phép cộng/nhân. Nếu phải kiểm soát precision trong phép tính dài, dùng `MathContext` theo thiết kế thuật toán, đồng thời ý thức rằng làm tròn trung gian có thể ảnh hưởng kết quả cuối.

## 8. Chia số và tỷ lệ: xác định precision/scale ngay trong phép toán

Phép chia là nguồn lỗi phổ biến khi dùng `BigDecimal`:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// one.divide(three) ném ArithmeticException vì 1/3 vô hạn thập phân.
BigDecimal rounded = one.divide(three, 2, RoundingMode.HALF_UP);
System.out.println(rounded); // 0.33
```

Đây là mảnh mã minh họa trong một phương thức, cần import `BigDecimal` và `RoundingMode`.

Quyết định đúng tùy hợp đồng:

- “Lưu tỷ lệ có 4 chữ số sau dấu chấm” → cân nhắc `divide(divisor, 4, mode)`.
- “Giữ 8 chữ số có nghĩa cho tính toán khoa học” → cân nhắc `divide(divisor, new MathContext(8, mode))`.
- “Tỷ lệ phần trăm hiển thị 2 số sau dấu chấm” → xác định giá trị lưu là `0.075` hay `7.5%`, và làm tròn ở bước **hiển thị** hay tính toán.
- “Phải chia chính xác, không làm tròn” → chấp nhận ngoại lệ nếu thương không có biểu diễn thập phân hữu hạn, hoặc dùng biểu diễn phân số riêng.

**Không** viết `divide` không có mode rồi ngạc nhiên với `ArithmeticException` ở `1/3`. Ngược lại, cũng không mặc định `HALF_UP` và scale 2 cho mọi phép chia: hệ thống có thể cần thêm chữ số trung gian để không làm mất thông tin quan trọng.

## 9. Hợp đồng qua các tầng: Java, JSON, database và báo cáo

### 9.1. Java domain model

Một thiết kế tốt đặt tên cho cấu hình số, tránh “magic number” 2 xuất hiện ở nhiều nơi:

```java
public final class MoneyPolicy {
    public static final int SCALE = 2;
    public static final int PRECISION = 12;
    public static final java.math.RoundingMode TAX_ROUNDING =
            java.math.RoundingMode.HALF_EVEN;

    private MoneyPolicy() {
    }
}
```

Đây là lớp hoàn chỉnh có thể lưu ở `MoneyPolicy.java` trong Java 17+. Trong kiến trúc thực, đặt chính sách ở domain hoặc module cấu hình phù hợp; nếu nhiều loại tiền tệ có scale khác nhau, không dùng một hằng `SCALE=2` toàn cục cho mọi đối tượng.

Đừng che giấu rounding trong setter không rõ hợp đồng: `setAmount(input)` âm thầm làm tròn có thể khiến phần còn lại của chương trình tưởng rằng dữ liệu gốc chưa bị đổi. API rõ ý định hơn có thể là `parseExactAmount`, `calculateRoundedTax`, `serializeAmount`.

### 9.2. API JSON và định dạng

JSON hỗ trợ số nhưng client có thể parse thành `double`, JavaScript `Number`, hoặc đưa dữ liệu qua một hệ thống không giữ nguyên số chữ số sau dấu chấm. Vì vậy, với lượng tiền cần bảo toàn biểu diễn, **decimal string** thường là lựa chọn dễ quy định hơn:

```json
{
  "currency": "USD",
  "amount": "19.90"
}
```

Hợp đồng cần nêu chuỗi `amount` theo quy tắc nào: dấu chấm thập phân, có cho số mũ không, bao nhiêu chữ số, có cho dấu âm/plus không, có khoảng trắng không; lỗi trả về mã gì. Không phụ thuộc locale người dùng để parse chuỗi giao tiếp máy với máy.

Nếu API sử dụng JSON number, cũng cần nêu rõ hai bên phải dùng kiểu decimal chính xác và round-trip an toàn. JSON number **không bảo đảm** mọi client gìn giữ scale như `"19.90"` và tránh biến về IEEE 754.

### 9.3. Database `DECIMAL(P,S)`

Với một ví dụ SQL schema:

```sql
amount DECIMAL(12, 2) NOT NULL
```

Thông thường, `P=12` là tổng số chữ số, `S=2` là số chữ số thập phân được lưu. Một giá trị `9999999999.99` vừa giới hạn này; `10000000000.00` vượt số chữ số phần nguyên cho phép.

**Không dựa vào database tự quyết rounding**. Quy tắc ép/làm tròn khi insert giá trị có quá nhiều chữ số có thể thay đổi theo sản phẩm database, phiên bản hoặc mode. Để không bị lệch giữa môi trường dev/staging/production, validate trong domain trước và test tích hợp với schema thật. Database còn là lớp bảo vệ cuối giúp từ chối giá trị ngoài phạm vi; không thay thế validation nghiệp vụ.

Nếu cần lưu cả số chữ số đầu vào gốc, ví dụ “2.00” khác “2.0” về bằng chứng đo lường, phải thiết kế **metadata biểu diễn** tương ứng; `DECIMAL(P,S)` cố định có thể chuẩn hóa scale và không bảo toàn chuỗi người dùng đã nhập.

### 9.4. Báo cáo và CSV

Sử dụng `BigDecimal.toPlainString()` khi cần xuất dạng thập phân không dùng ký pháp khoa học, đồng thời **đặt scale theo hợp đồng** trước khi xuất. `stripTrailingZeros()` có thể khiến `new BigDecimal("1000.00")` thành `1E+3` (scale âm), nên không được coi là hàm định dạng hiển thị tiền.

Nếu báo cáo có nhiều đơn vị tiền tệ, mỗi giá trị cần có đơn vị cụ thể. Hai giá trị có cùng số “10.00” nhưng khác currency không thể cộng nếu chưa có quy tắc quy đổi, nguồn tỷ giá, thời điểm và precision/rounding liên quan.

## 10. Test như thế nào để đảm bảo hợp đồng số?

Viết test từ **hợp đồng**, không chỉ test khi thấy bug:

| Nhóm | Ca thử nên có | Điều cần xác nhận |
|---|---|---|
| Giá trị hợp lệ | `0`, `19.90`, `9999999999.99` | Chuẩn hóa chính xác, đúng miền |
| Trailing zero | `19.900` | Chấp nhận nếu hợp đồng là giá trị chính xác tới cent |
| Không hợp lệ do scale | `19.999` | Từ chối, không âm thầm `20.00` |
| Không hợp lệ do precision | `10000000000.00` | Từ chối vượt miền |
| Giá trị âm | `-1.00` | Từ chối hoặc chấp nhận theo quy tắc hoàn tiền riêng |
| Không phải số | `"abc"`, `""` | Lỗi parse/validation có cấu trúc |
| Cú pháp ngoài format | `"1E+3"`, `"+1.00"` | Cho phép/từ chối theo hợp đồng input |
| Rounding tie | `2.345`, `2.355`, `-2.345` | Đúng `HALF_EVEN`/`HALF_UP` |
| Thứ tự làm tròn | `0.335 × 3` | Cùng chính sách, kết quả 1.01 hoặc 1.02 tùy điểm làm tròn |
| Chia không kết thúc | `1 / 3` | Bắt buộc mode/scale hoặc ngoại lệ có chủ ý |
| Round-trip | Java → JSON → Java → DB → Java | Không đổi giá trị và biểu diễn theo phần hợp đồng cần giữ |

### Ví dụ test Java không cần framework

**Tệp `MoneyContractChecks.java`**; dùng lớp `MoneyContractDemo` từ mục 6 đã biên dịch:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyContractChecks {
    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(String input) {
        try {
            MoneyContractDemo.parseAmount(input);
            throw new AssertionError("Expected rejection: " + input);
        } catch (IllegalArgumentException expected) {
            // Đúng: đầu vào bị từ chối theo hợp đồng.
        }
    }

    public static void main(String[] args) {
        check(MoneyContractDemo.parseAmount("19.900")
                .equals(new BigDecimal("19.90")),
                "trailing zero accepted and normalized");

        check(MoneyContractDemo.parseAmount("0")
                .equals(new BigDecimal("0.00")),
                "zero has required scale");

        rejects("19.999");
        rejects("10000000000.00");
        rejects("-1.00");

        check(new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_EVEN)
                .equals(new BigDecimal("2.34")),
                "tie-to-even lower");

        check(new BigDecimal("2.355")
                .setScale(2, RoundingMode.HALF_EVEN)
                .equals(new BigDecimal("2.36")),
                "tie-to-even higher");

        System.out.println("Contract checks passed");
    }
}
```

```bash
javac MoneyContractDemo.java MoneyContractChecks.java
java MoneyContractChecks
```

```text
Contract checks passed
```

Chú ý `rejects()` bắt `IllegalArgumentException` mà `parseAmount` phát sinh, còn `AssertionError` là một loại `Error` riêng nên không bị nuốt bởi catch này. Nếu muốn test exception subclass cụ thể, JUnit 5 có `assertThrows`; nhưng bài học này không cần thêm thư viện.

Khi kiểm thử giá trị money đã được chuẩn hóa và **scale là hợp đồng**, `BigDecimal.equals` là phù hợp vì kiểm tra cả giá trị và scale. Khi chỉ yêu cầu giá trị số học (ví dụ so sánh 2.0 với 2.00), `compareTo == 0` phù hợp hơn. Chọn assertion đúng ý định, không sử dụng một kiểu so sánh cho mọi trường hợp.

## 11. Các sai lầm thiết kế thường gặp

### 11.1. Lẫn precision với scale

Ghi “precision = 2” khi muốn “2 chữ số sau dấu chấm” sẽ dẫn đến tính toán sai. Với `123.456`, `MathContext(2)` chỉ giữ 2 chữ số có nghĩa (kết quả gần `120`), còn `setScale(2)` giữ 2 chữ số thập phân (`123.46` với `HALF_EVEN`).

### 11.2. Dùng `double` làm trung gian cho tiền

`double` không biểu diễn chính xác nhiều số thập phân như `0.1`. `new BigDecimal(0.1)` tạo `BigDecimal` chứa chính xác giá trị nhị phân của `double`, không phải đúng giá trị thập phân `0.1` mong muốn. Hãy tạo từ chuỗi `new BigDecimal("0.1")` hoặc từ một nguồn decimal chính xác. `BigDecimal.valueOf(double)` sử dụng chuỗi biểu diễn chuẩn của `double`, nhưng không thể khôi phục chữ số gốc đã mất từ trước.

### 11.3. Làm tròn khi parse, không thông báo

Một client gửi `19.999` có thể vô tình bị đổi thành `20.00`; đây là thay đổi giá trị nghiệp vụ. Nếu hợp đồng đòi nhập chính xác đến cent, dùng `UNNECESSARY` và **từ chối** thay vì ngầm sửa dữ liệu.

### 11.4. Áp `MathContext` quá sớm

Dùng `MathContext(4)` cho mọi phép cộng/nhân có thể làm mất chữ số ở từng bước. Kết quả cuối có thể khác cách giữ precision trung gian lớn hơn rồi rounding một lần ở điểm được hợp đồng quy định. Chọn chính sách có căn cứ, không “đặt cho an toàn” tùy tiện.

### 11.5. `stripTrailingZeros()` cho mục tiêu hiển thị

`stripTrailingZeros()` có thể tạo scale âm và ký pháp khoa học, ví dụ `1000.00 → 1E+3`. Nó hữu ích khi cần canonical hóa giá trị theo một chính sách rõ ràng, nhưng không thay thế `setScale(2)`/định dạng phù hợp khi xuất `1000.00`.

### 11.6. Không công bố đơn vị và mức làm tròn

“Dùng 2 chữ số thập phân” chưa trả lời currency nào, đơn vị giá nào, có làm tròn từng dòng không hay một lần ở toàn bộ đơn. Các hệ thống khác nhau có thể đều “đúng 2 chữ số” nhưng khác kết quả. Cần viết ví dụ số cụ thể trong hợp đồng.

### 11.7. Không giới hạn miền và đầu vào quá lớn

`BigDecimal` hỗ trợ độ chính xác tùy ý nhưng không miễn phí. Chuỗi rất dài, số mũ cực lớn và chuyển scale có thể làm hệ thống dùng nhiều CPU/bộ nhớ. Giới hạn kích thước payload, độ dài chuỗi, số mũ và miền giá trị phù hợp trước khi cấp phát lớn hoặc chuẩn hóa dữ liệu.

### 11.8. Phó mặc database hoặc serialize tự làm tròn

Rounding ở tầng persistence có thể âm thầm đổi giá trị so với domain; round-trip giữa Java/JSON/DB có thể không giữ scale đúng như mong đợi. Thiết kế nên quy định một **nguồn sự thật** cho chính sách số và kiểm tra bằng test tích hợp.

## 12. Checklist viết một hợp đồng dữ liệu số

Trước khi chốt schema hoặc API, nên ghi thành văn bản:

1. **Ý nghĩa & đơn vị** của số: tiền, phần trăm, tỷ lệ 0..1, đo lường hoặc số lượng.
2. **Kiểu số**: `BigDecimal`, integer minor units, `BigInteger` hay `double` (nêu lý do).
3. **Miền hợp lệ**: min, max, có nhận số âm/0/giá trị đặc biệt không.
4. **Precision** tối đa theo từng tầng và cách xử lý khi vượt giới hạn.
5. **Scale** đầu vào, trong tính toán, tại lưu trữ, tại xuất dữ liệu.
6. **RoundingMode** và **điểm làm tròn**: theo đơn giá, dòng, nhóm, thuế hay tổng.
7. **Chính sách dữ liệu không hợp lệ**: từ chối, trả lỗi nào, có cho làm tròn có chủ ý không.
8. **Định dạng wire**: decimal string hay JSON number, cú pháp được phép, locale, số mũ, trailing zeros.
9. **Persistence**: `DECIMAL(P,S)` và đối chiếu giới hạn/rounding của database thật.
10. **Kiểm thử**: ca biên, tie, negative, zero, precision overflow, chia không kết thúc, round-trip.
11. **Tương thích phiên bản**: thay precision, scale hoặc rounding là thay đổi hợp đồng có thể ảnh hưởng dữ liệu lưu, API clients và kết quả kế toán; cần kế hoạch migration.
12. **Quyền thay đổi chính sách**: ai duyệt rounding mode, cách lưu lịch sử version chính sách và tái tính khi cần đối soát.

Đối với các trường mà số chữ số mang ý nghĩa đo lường (ví dụ `2.0` và `2.00` có thể báo độ phân giải đo khác nhau), lưu riêng metadata về độ phân giải hoặc văn bản gốc nếu nghiệp vụ đòi. `BigDecimal` giữ scale khi khởi tạo từ chuỗi nhưng nhiều phép toán, serialize hoặc lưu DB có thể chuẩn hóa cách biểu diễn.

## 13. Bài tập tự kiểm tra, có lời giải

1. **`new BigDecimal("123.450").precision()` và `scale()` là bao nhiêu?**  
   Lần lượt 6 và 3. Hai giá trị này không cùng nghĩa.

2. **Vì sao `new BigDecimal("19.900").setScale(2, UNNECESSARY)` không ném ngoại lệ?**  
   Vì việc bỏ số 0 thập phân cuối không đổi giá trị số học, kết quả là `19.90`.

3. **Vì sao `new BigDecimal("19.999").setScale(2, UNNECESSARY)` ném ngoại lệ?**  
   Vì không thể giữ giá trị chính xác ở scale 2: cần bỏ chữ số 9 khác 0.

4. **`2.345` làm tròn 2 chữ số theo `HALF_UP` và `HALF_EVEN` được gì?**  
   Lần lượt `2.35` và `2.34`, với đầu vào `BigDecimal` từ chuỗi.

5. **`0.335 × 3` nếu làm tròn đơn giá trước hoặc sau khi nhân theo `HALF_UP` được gì?**  
   Lần lượt `1.02` và `1.01`; điểm làm tròn là một phần của hợp đồng.

6. **`BigDecimal.ONE.divide(new BigDecimal("3"))` có trả một số thập phân tùy ý dài vô hạn không?**  
   Không; nếu không chỉ định rounding/scale/MathContext và thương không kết thúc, Java ném `ArithmeticException`.

7. **`DECIMAL(12,2)` có cho 12 chữ số phần nguyên không?**  
   Không theo ý nghĩa SQL phổ biến: precision tổng 12, scale 2, nên tối đa 10 chữ số phần nguyên.

8. **Nếu API đòi đúng ký tự `"12.30"` thay vì `"12.300"`, chỉ dùng `UNNECESSARY` đủ không?**  
   Không. `UNNECESSARY` kiểm tra giá trị có biểu diễn chính xác được sau chuẩn hóa, không đếm chữ số của chuỗi đầu vào; cần kiểm tra định dạng văn bản riêng.

9. **Dùng `equals` hay `compareTo` cho hai giá trị tiền đã chuẩn hóa theo scale 2?**  
   `equals` phù hợp khi yêu cầu **cả scale** thuộc hợp đồng; `compareTo == 0` phù hợp khi chỉ yêu cầu cùng giá trị số học.

10. **Vì sao một thay đổi rounding mode có thể là breaking change dù API field vẫn tên `amount`?**  
    Vì cùng input có thể cho output khác, ảnh hưởng dữ liệu đã lưu, đối soát, hóa đơn và client; cần version/chính sách migration nếu thay đổi.

## 14. Tổng kết và tài liệu chính thức

**Precision, scale và rounding là phần của hợp đồng dữ liệu số, không phải ba tham số kỹ thuật tùy ý.** `precision` giới hạn chữ số có nghĩa; `scale` xác định vị trí thập phân; `RoundingMode` quy định cách xử lý khi buộc giảm độ chính xác. Một hệ thống đúng phải thống nhất cách parse, tính toán, lưu trữ, trao đổi và kiểm thử—kèm miền hợp lệ, đơn vị và điểm làm tròn rõ ràng.

Tài liệu tham khảo:

- [Java 21 — `BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Java 21 — `MathContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html)
- [Java 21 — `RoundingMode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-007">Tránh dùng double làm trung gian cho dữ liệu thập phân chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh dùng `double` làm trung gian cho dữ liệu thập phân chính xác trong Java

Khi đầu vào đại diện cho tiền, đơn giá, thuế suất, tỷ lệ đã quy định theo chữ số thập phân hoặc số liệu cần đối soát chính xác, việc sử dụng `BigDecimal` **ở bước cuối** chưa đủ. Chỉ một lần chuyển **chuỗi thập phân → `double` → `BigDecimal`** có thể làm mất chữ số và khiến giá trị khác dữ liệu gốc. Tương tự, một phép nhân/cộng bằng `double` trước khi tạo `BigDecimal` đã mang theo các sai số nhị phân.

Nguyên tắc thiết kế: **Dữ liệu cần chính xác theo hệ thập phân phải đi qua một pipeline thập phân chính xác từ lúc tiếp nhận đến khi tính toán, kiểm tra, lưu và trả về.** Chuyển trực tiếp từ chuỗi decimal, số nguyên hoặc nguồn decimal có bảo đảm sang `BigDecimal`; tránh `double` trung gian trừ khi sai số xấp xỉ được hợp đồng cho phép.

Bài học phân tích lý do, chỉ ra điểm mất thông tin đầu tiên, so sánh constructor `BigDecimal`, đề xuất kiến trúc DTO/API và minh họa bằng Java 17+ có thể chạy.

## 1. `double` không giữ nguyên mọi số thập phân

Kiểu `double` trong Java là số dấu phẩy động nhị phân IEEE 754 binary64. Nó có độ chính xác hữu hạn theo cơ số 2. Các phân số như 1/2 và 1/4 có biểu diễn nhị phân hữu hạn, nhưng 1/10 không có. Vì vậy literal `0.1` được làm tròn sang một giá trị nhị phân gần nhất.

```java
double x = 0.1;
double y = 0.2;
System.out.println(x + y);
```

Stdout:

```text
0.30000000000000004
```

Đây không phải một lỗi ngẫu nhiên: các số `double` đầu vào và phép cộng được làm tròn theo quy tắc floating-point. Trong mô phỏng, xử lý ảnh hoặc đo đạc chấp nhận sai số, `double` hoàn toàn có ích. Nhưng nếu yêu cầu là giữ nguyên số decimal như `"0.1"` hoặc `"19.99"`, phải tách **mô hình số thực xấp xỉ** khỏi **mô hình decimal chính xác**.

`BigDecimal` biểu diễn giá trị dạng số nguyên không tỉ lệ (`unscaled value`) nhân với 10 mũ âm của `scale`. Khi được tạo trực tiếp từ chuỗi decimal, nó bảo toàn giá trị số mà chuỗi mô tả, không đi qua sự xấp xỉ binary64.

Ba loại giá trị thường bị nhầm:

- **Nguồn decimal:** ví dụ người dùng nhập chuỗi `"0.10000000000000001"`.
- **Số `double` sau parse:** một giá trị binary64 gần số đã nhập, không mang theo chuỗi nguồn.
- **`BigDecimal` được tạo sau bước parse:** decimal của chính số `double` đã có; không nhất thiết là decimal gốc.

Một khi hai chuỗi nguồn khác nhau đã trở thành cùng một giá trị `double`, thông tin phân biệt chúng mất vĩnh viễn trong nhánh xử lý đó.

## 2. Phản ví dụ: chuyển qua `double` làm mất giá trị decimal

Sai khi giá trị decimal đầu vào phải được giữ đúng:

```java
String input = "19.99";
double intermediate = Double.parseDouble(input);
BigDecimal amount = new BigDecimal(intermediate);
```

`Double.parseDouble` có thể làm tròn một số thập phân vào miền binary64. Constructor `new BigDecimal(double)` tạo ra decimal **chính xác của số binary64 đã được làm tròn**. Điều này khác với decimal do người gửi mô tả.

Ví dụ rõ nhất:

```java
System.out.println(new BigDecimal(0.1).toPlainString());
```

Stdout:

```text
0.1000000000000000055511151231257827021181583404541015625
```

Đây là giá trị thập phân chính xác của `double` gần số 0,1, **không phải** giá trị decimal 1/10. Constructor làm đúng chức năng của nó; sai lầm nằm ở việc dùng `double` làm trung gian không phù hợp.

Còn nghiêm trọng hơn, hai chuỗi decimal khác nhau có thể bị làm tròn thành cùng `double`:

```java
String source = "0.10000000000000001";
BigDecimal correct = new BigDecimal(source);
BigDecimal wrong = BigDecimal.valueOf(Double.parseDouble(source));

System.out.println(correct.toPlainString());
System.out.println(wrong.toPlainString());
```

Stdout:

```text
0.10000000000000001
0.1
```

Dù `wrong` có vẻ gọn và đẹp, nó **không còn bằng** số decimal ban đầu. Không có thuật toán nào có thể khôi phục chuỗi nguồn chỉ từ `double` trong mọi trường hợp, vì nhiều chuỗi decimal cùng ánh xạ về một giá trị floating-point.

Nếu đã nhận chuỗi đầu vào, hãy tạo trực tiếp:

```java
BigDecimal correct = new BigDecimal(source);
```

Không nên parse chuỗi decimal thành `double` với lý do “kiểm tra xem có phải số không”: nếu cần validation, có thể parse bằng `BigDecimal` rồi bắt `NumberFormatException` hoặc áp quy tắc định dạng/miền tương ứng.

## 3. Phân biệt `new BigDecimal(double)` và `BigDecimal.valueOf(double)`

| Cách tạo | Nguồn | Điều được bảo toàn | Dùng trong tình huống nào? |
| --- | --- | --- | --- |
| `new BigDecimal("0.1")` | Chuỗi thập phân | Giá trị decimal đúng như chuỗi, scale phù hợp với biểu diễn | Dữ liệu decimal cần chính xác |
| `new BigDecimal(0.1)` | `double` | Giá trị binary64 chính xác đang lưu | Phân tích biểu diễn binary64, không phải khôi phục nguồn |
| `BigDecimal.valueOf(0.1)` | `double` | Decimal theo `Double.toString(double)` | Chỉ còn giá trị `double` và cần biểu diễn decimal ngắn theo nghĩa đó |
| `BigDecimal.valueOf(100L)` | `long` | Số nguyên 100 chính xác | Số nguyên nguồn |
| `new BigDecimal(BigInteger)` | Số nguyên lớn | Số nguyên lớn chính xác | Khi `long` không đủ |

`BigDecimal.valueOf(double)` là lựa chọn thường hợp lý hơn constructor nhận `double` khi nguồn **thực sự chỉ có `double`** và ta muốn decimal dựa trên chuỗi round-trip chuẩn của `Double.toString`. Ví dụ:

```java
System.out.println(BigDecimal.valueOf(0.1).toPlainString()); // 0.1
```

Tuy vậy, việc kết quả trông giống literal decimal không chứng minh nó khớp **dữ liệu thập phân ban đầu**. Ví dụ `"0.10000000000000001"` trở thành `"0.1"` sau đường đi `Double.parseDouble` rồi `BigDecimal.valueOf`.

`valueOf` cũng không sửa những phép tính đã thực hiện trước đó bằng `double`:

```java
double calculated = 0.1 + 0.2;
BigDecimal late = BigDecimal.valueOf(calculated);
System.out.println(late.toPlainString());
```

Stdout:

```text
0.30000000000000004
```

Hãy chuyển **các toán hạng** sang `BigDecimal` từ nguồn decimal **trước phép cộng**:

```java
BigDecimal exact = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));
System.out.println(exact.toPlainString()); // 0.3
```

`BigDecimal.valueOf(long)` là overload **khác**: nó nhận số nguyên nguyên vẹn và không trải qua binary64, nên phù hợp với tiền được biểu diễn bằng số nguyên đơn vị nhỏ.

## 4. Xây dựng pipeline giữ decimal chính xác

Để một field tiền/thuế giữ đúng decimal, cần kiểm soát **mọi ranh giới chuyển kiểu**, không chỉ class ở tầng domain.

Đường đi dễ làm mất dữ liệu:

```text
JSON/CSV input chứa số decimal
  → parser chuyển sang double
  → DTO hoặc entity chứa double
  → cộng/nhân/chia bằng double
  → BigDecimal.valueOf(double)
  → database DECIMAL
```

Database có thể lưu chính xác một `BigDecimal`, nhưng đó là **giá trị của kết quả xấp xỉ đã xảy ra**, không phải dữ liệu decimal nguồn.

Đường đi đúng khi hợp đồng yêu cầu decimal chính xác:

```text
JSON token decimal hoặc chuỗi decimal gốc
  → parse trực tiếp sang BigDecimal
  → validate miền, đơn vị và độ phân giải
  → tính toán bằng BigDecimal
  → làm tròn tại thời điểm được quy định
  → lưu SQL DECIMAL/NUMERIC hoặc đơn vị nhỏ nguyên theo chuyển đổi exact
  → serialize về decimal theo schema
```

Có thể dùng một value object để bảo vệ invariant, ví dụ `Money` lưu cả mã tiền tệ và `BigDecimal`, hoặc lưu số nguyên đơn vị nhỏ (`long`) khi nghiệp vụ cho phép và miền biểu diễn được chứng minh đủ rộng. Trong cả hai cách, **không cần dùng `double` làm cầu nối**.

### JSON và frontend

JSON hỗ trợ ký pháp số decimal, nhưng cách parser và DTO ánh xạ quyết định giá trị có đi qua floating-point hay không. Nếu phần mềm deserialization đưa token vào `double` rồi mới dựng `BigDecimal`, có nguy cơ mất chính xác. Hãy dùng kiểu trường `BigDecimal` và cấu hình parse trực tiếp tương ứng với thư viện.

Client JavaScript thường dùng `Number` dựa trên binary64. Nếu input nhập tay `"19.99"` đã được chuyển thành `Number` rồi thực hiện nhiều phép tính, backend không thể khôi phục toàn bộ ý nghĩa decimal bằng cách nhận `BigDecimal`. Khi cần giữ chuỗi decimal nguyên vẹn, frontend có thể truyền chuỗi decimal qua một field có schema rõ ràng hoặc dùng thư viện decimal chính xác thích hợp. **Không tự đổi kiểu JSON number thành string mà không đồng bộ hợp đồng API.**

Số nguyên lớn cũng có rủi ro riêng: JavaScript `Number` không thể biểu diễn chính xác mọi số nguyên vượt `2^53 - 1`. Nếu API chuyển số tiền đơn vị nhỏ dạng `long` qua client JavaScript, cần xác định định dạng truyền và kiểm thử round-trip, không chỉ kiểm thử backend.

### JDBC, ORM và database

Cột `DECIMAL(p,s)` hoặc `NUMERIC(p,s)` phù hợp cho decimal chính xác trong phạm vi cấu hình. Khi đọc, ưu tiên mapping/getter decimal tương ứng như `BigDecimal` thay vì lấy `getDouble()` rồi chuyển kiểu tiếp. Nếu một cột vốn lưu floating-point, đọc nó dưới dạng `BigDecimal` sau này không giúp khôi phục số decimal trước khi dữ liệu đã bị xấp xỉ.

Kiểu SQL, precision, scale, đơn vị và miền số hợp lệ phải khớp với schema/API. Cột hỗ trợ 12 chữ số không đồng nghĩa nghiệp vụ cho phép mọi giá trị có 12 chữ số.

### CSV, cấu hình và bảng tính

CSV giữ văn bản của trường; nếu giá trị decimal được lưu nguyên văn, có thể parse trực tiếp bằng `BigDecimal(String)`. Tránh chế độ tự suy đoán kiểu thành `double` trong phần đọc import. Với bảng tính, giá trị hiển thị `19.99` chưa chứng minh giá trị nội bộ không được tính bằng floating-point; nếu nguồn đã mất chính xác từ trước, parse chuỗi hiển thị không cho phép khẳng định đã phục hồi nguyên bản dữ liệu.

Tất cả nơi nhập decimal cần thống nhất **dấu thập phân**, dấu phân nhóm, locale, ký hiệu tiền tệ và có chấp nhận biểu diễn dạng lũy thừa hay không. Constructor `BigDecimal(String)` không tự hiểu `"1,25"` là 1,25.

## 5. Tính toán thập phân đúng từ toán hạng đầu tiên

Giả sử có đơn giá `"19.90"`, số lượng 3 và thuế suất `"0.075"` (7,5%). Đừng viết:

```java
double unitPrice = 19.90;
double taxRate = 0.075;
double subtotal = unitPrice * 3;
BigDecimal total = BigDecimal.valueOf(subtotal * (1 + taxRate));
```

Dù kết quả cuối là `BigDecimal`, phép tính vẫn đã thực hiện bằng `double`. Giá trị trung gian và thời điểm làm tròn không được kiểm soát theo hợp đồng decimal.

Ví dụ đúng:

```java
BigDecimal unitPrice = new BigDecimal("19.90");
BigDecimal rate = new BigDecimal("0.075");
BigDecimal quantity = BigDecimal.valueOf(3L);

BigDecimal subtotal = unitPrice.multiply(quantity);
BigDecimal tax = subtotal.multiply(rate)
        .setScale(2, RoundingMode.HALF_UP);
BigDecimal total = subtotal.add(tax);

System.out.println(subtotal.toPlainString()); // 59.70
System.out.println(tax.toPlainString());      // 4.48
System.out.println(total.toPlainString());    // 64.18
```

Đây là đoạn minh họa cần import `java.math.BigDecimal` và `java.math.RoundingMode`, và đặt trong một phương thức. Phép nhân 59,70 × 0,075 = 4,47750; làm tròn thuế thành 4,48 theo **chính sách minh họa** `HALF_UP`.

Không có quy tắc làm tròn duy nhất phù hợp cho mọi lĩnh vực. Có sản phẩm làm tròn mỗi dòng hóa đơn, có sản phẩm làm tròn sau khi cộng các dòng. Hai chính sách đều có thể dùng `BigDecimal` nhưng cho kết quả khác nhau vì thời điểm làm tròn.

### Chia số và `MathContext`

`BigDecimal.add`, `subtract` và `multiply` không nhận `MathContext` có thể thực hiện chính xác theo decimal. Phép chia đôi khi có kết quả thập phân vô hạn, ví dụ 1/3:

```java
BigDecimal third = BigDecimal.ONE.divide(
        new BigDecimal("3"), 4, RoundingMode.HALF_UP);
// 0.3333
```

Nếu gọi `BigDecimal.ONE.divide(new BigDecimal("3"))` không chỉ định cách làm tròn, Java ném `ArithmeticException` vì kết quả decimal không kết thúc. `MathContext` cũng có thể dùng để quản lý precision và rounding theo hợp đồng, nhưng **không thể phục hồi chữ số nguồn đã mất ở một `double` trước đó**.

Đặc tả nghiệp vụ nên ghi rõ độ phân giải, precision, `RoundingMode` và thời điểm áp dụng.

## 6. Scale, biểu diễn và độ phân giải không phải một khái niệm

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
System.out.println(b.scale());           // 2
```

Hai giá trị cùng số học nhưng khác `scale`. `compareTo` chỉ so giá trị số; `equals` còn kiểm tra scale. Nếu một trường có hợp đồng giữ nguyên số chữ số thập phân người dùng nhập, việc đưa giá trị qua `double` có thể phá cả thông tin biểu diễn đó: từ `"1.000"` qua `double` bạn không thể biết nguồn ban đầu có mấy số 0.

Cần phân biệt:

1. **Đúng giá trị decimal:** `"1.0"` và `"1.00"` bằng nhau theo số học.
2. **Độ phân giải/scale nghiệp vụ:** ví dụ chấp nhận giá trị biểu diễn được chính xác tới 0,01.
3. **Đúng định dạng văn bản:** có cần giữ nguyên chính chuỗi `"1.00"` hay không?

Để kiểm tra số tiền có thể biểu diễn không mất mát ở scale 2:

```java
BigDecimal accepted = new BigDecimal("1.230")
        .setScale(2, RoundingMode.UNNECESSARY);
// accepted.toPlainString() là "1.23".
```

Giá trị `"1.234"` không thể giảm scale về 2 mà không thay đổi giá trị số, nên `RoundingMode.UNNECESSARY` ném `ArithmeticException`. Nếu hợp đồng **cho phép** rounding, hãy chỉ định mode chính xác thay vì âm thầm giảm chữ số.

`BigDecimal` có thể giữ scale âm; vì vậy “scale” không đơn giản là đếm ký tự sau dấu chấm của một chuỗi bất kỳ. Khi hợp đồng cần kiểm soát biểu diễn đầu vào, xác thực cả cú pháp chuỗi và giá trị numeric nếu cần.

## 7. Khi có thể lưu thành số nguyên đơn vị nhỏ

Một cách tránh floating-point cho giá trị decimal có độ phân giải cố định là chuyển **trực tiếp từ decimal nguồn** sang số nguyên đếm đơn vị nhỏ. Ví dụ giả định một đơn vị lớn bằng 100 đơn vị nhỏ:

```java
BigDecimal amount = new BigDecimal("12.30")
        .setScale(2, RoundingMode.UNNECESSARY);

long minorUnits = amount.movePointRight(2).longValueExact();
System.out.println(minorUnits); // 1230
```

`movePointRight(2)` nhân 100 theo decimal; `longValueExact()` từ chối khi có phần lẻ còn lại hoặc vượt giới hạn `long`. Cách này khác hoàn toàn với:

```java
double amount = Double.parseDouble("12.30");
long minorUnits = (long) (amount * 100); // Không nên dùng khi cần exact.
```

Phép nhân floating-point có thể tạo giá trị hơi thấp/hơi cao so với số nguyên dự kiến; ép `(long)` có thể bỏ phần lẻ âm thầm.

Hệ số 100 **không phải quy tắc chung cho mọi đồng tiền**. Nếu phần mềm đa tiền tệ, phải gắn mã tiền tệ và thang đơn vị tương ứng; không cộng các số nguyên minor units của các đồng tiền khác nhau nếu chưa có bước quy đổi có chính sách rõ ràng.

Trước khi lưu sang `long`, cũng cần kiểm tra miền min/max của nghiệp vụ: `longValueExact()` chỉ bảo đảm **chuyển đổi nguyên chính xác**, không tự biết giá âm có được phép hay số tiền có vượt hạn mức giao dịch không.

## 8. Chương trình Java 17+ chạy đầy đủ

Chương trình sau minh họa cách mất dữ liệu decimal khi qua `double`, phép tính chính xác khi tạo toán hạng đúng lúc và việc từ chối giá sai độ phân giải. Chính sách ví dụ: đơn giá không âm, tối đa 1.000.000 đơn vị lớn, biểu diễn được theo bước 0,01; thuế suất từ 0 đến 1 và thuế được làm tròn `HALF_UP` tới hai chữ số decimal.

Lưu tệp **`ExactDecimalPipelineDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ExactDecimalPipelineDemo {
    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("1000000.00");

    private ExactDecimalPipelineDemo() {}

    static BigDecimal parseAmount(String text) {
        Objects.requireNonNull(text, "amount text");
        BigDecimal amount = new BigDecimal(text)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount out of range");
        }
        return amount;
    }

    static BigDecimal parseRate(String text) {
        Objects.requireNonNull(text, "rate text");
        BigDecimal rate = new BigDecimal(text);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate out of range");
        }
        return rate;
    }

    public static void main(String[] args) {
        String raw = "0.10000000000000001";
        BigDecimal original = new BigDecimal(raw);
        BigDecimal afterDouble =
                BigDecimal.valueOf(Double.parseDouble(raw));

        System.out.println("raw decimal = " + original.toPlainString());
        System.out.println("after double = " + afterDouble.toPlainString());
        System.out.println("value changed = "
                + (original.compareTo(afterDouble) != 0));

        System.out.println("double constructor = "
                + new BigDecimal(0.1).toPlainString());
        System.out.println("valueOf double = "
                + BigDecimal.valueOf(0.1).toPlainString());

        double binarySum = 0.1 + 0.2;
        BigDecimal decimalSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("binary sum converted = "
                + BigDecimal.valueOf(binarySum).toPlainString());
        System.out.println("decimal sum = "
                + decimalSum.toPlainString());

        BigDecimal unitPrice = parseAmount("19.90");
        BigDecimal subtotal =
                unitPrice.multiply(BigDecimal.valueOf(3L));
        BigDecimal rate = parseRate("0.075");
        BigDecimal tax = subtotal.multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        System.out.println("subtotal = " + subtotal.toPlainString());
        System.out.println("tax = " + tax.toPlainString());
        System.out.println("total = " + total.toPlainString());
        System.out.println("minor units = "
                + total.movePointRight(2).longValueExact());

        System.out.println("input scale = "
                + new BigDecimal("1.2300").scale());
        System.out.println("normalized scale = "
                + parseAmount("1.2300").scale());

        try {
            parseAmount("19.999");
            System.out.println("extra precision = accepted");
        } catch (ArithmeticException ex) {
            System.out.println("extra precision = rejected");
        }

        try {
            parseAmount("-0.01");
            System.out.println("negative amount = accepted");
        } catch (IllegalArgumentException ex) {
            System.out.println("negative amount = rejected");
        }

        BigDecimal largeExact =
                new BigDecimal("9007199254740993");
        BigDecimal largeViaDouble = BigDecimal.valueOf(
                Double.parseDouble("9007199254740993"));
        System.out.println("large decimal changed = "
                + (largeExact.compareTo(largeViaDouble) != 0));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ExactDecimalPipelineDemo.java
java ExactDecimalPipelineDemo
```

Stdout chính xác:

```text
raw decimal = 0.10000000000000001
after double = 0.1
value changed = true
double constructor = 0.1000000000000000055511151231257827021181583404541015625
valueOf double = 0.1
binary sum converted = 0.30000000000000004
decimal sum = 0.3
subtotal = 59.70
tax = 4.48
total = 64.18
minor units = 6418
input scale = 4
normalized scale = 2
extra precision = rejected
negative amount = rejected
large decimal changed = true
```

Giải thích kết quả:

- Decimal nguồn `"0.10000000000000001"` còn nguyên khi parse trực tiếp nhưng đổi thành `0.1` sau `Double.parseDouble` rồi `BigDecimal.valueOf`.
- Constructor `new BigDecimal(0.1)` hiển thị dãy decimal đầy đủ của binary64; `valueOf(0.1)` rút về `0.1`, nhưng không phải công cụ khôi phục chuỗi nguồn.
- Chuyển `double` sau khi cộng `0.1 + 0.2` vẫn thu được `0.30000000000000004`; cộng hai `BigDecimal` tạo trực tiếp từ chuỗi cho `0.3`.
- Giá 19,90 nhân 3 bằng 59,70; thuế 7,5% là 4,47750, làm tròn theo chính sách ví dụ thành 4,48; tổng 64,18 và chuyển chính xác thành 6.418 đơn vị nhỏ.
- Chuỗi `"1.2300"` có scale 4; có thể chuẩn hóa thành scale 2 mà không đổi giá trị số. Chuỗi `"19.999"` không thể chuẩn hóa chính xác sang hai chữ số nên bị từ chối.
- Số `9007199254740993` lớn hơn miền số nguyên liên tiếp biểu diễn chính xác của binary64, vì thế nếu parse qua `double`, `BigDecimal` sau đó không thể lấy lại đúng giá trị.

## 9. Kiểm thử dữ liệu từ nguồn đến đích

Khi viết test cho yêu cầu decimal chính xác, **giá trị expected phải xuất phát từ chuỗi decimal gốc** hoặc một phép tính decimal độc lập, không xuất phát từ chính biểu thức `double` cần kiểm thử.

| Đầu vào/thao tác | Mong đợi |
| --- | --- |
| `"0.1"` → `new BigDecimal(text)` | Chính xác 0,1 |
| `"0.1"` → `new BigDecimal(Double.parseDouble(text))` | Decimal của binary64 gần 0,1, không bằng số gốc |
| `"0.10000000000000001"` qua `double` | Có thể thành decimal `0.1`; khác nguồn |
| `BigDecimal("0.1") + BigDecimal("0.2")` | `0.3` |
| `BigDecimal.valueOf(0.1 + 0.2)` | `0.30000000000000004` |
| `"1.2300"` | Giữ scale gốc hoặc chuẩn hóa rõ theo hợp đồng |
| `"19.999"` với bước 0,01 | Từ chối, hoặc làm tròn theo chính sách được quy định |
| `"-0.01"` ở field không âm | Từ chối theo miền nghiệp vụ |
| `"9007199254740993"` qua `double` | Mất khả năng giữ số nguyên chính xác |
| Dữ liệu `DECIMAL` lấy qua `getDouble()` | Có thể làm tròn decimal gốc |
| Giá từ JSON qua JavaScript `Number` | Kiểm tra nguy cơ chuyển đổi trước khi gửi |
| Một giá trị decimal chuyển sang minor units | Dùng phép decimal và chuyển kiểu exact, không qua `double` |

Kiểm thử tích hợp nên bao phủ cả đường đi **request → DTO → domain → phép tính → database → response**. Khi có khác biệt, tìm bước đầu tiên sử dụng `double`, `float`, `doubleValue()`, `Double.parseDouble()`, `getDouble()`, cột floating, phép tính nhị phân hoặc parser tự chọn `Number`.

Đối với dữ liệu cũ đã sai do floating-point, `setScale(2, ...)` có thể làm kết quả trông hợp lý nhưng **không chứng minh được số nguồn ban đầu**. Nếu phải hiệu chỉnh dữ liệu lịch sử, đối chiếu nguồn có thẩm quyền hoặc quy tắc nghiệp vụ riêng; không tự giả định mọi giá sau rounding bằng số người dùng đã gửi.

### Chẩn đoán nhanh

Trước khi sửa, hãy hỏi:

1. Dữ liệu decimal bắt đầu từ **chuỗi**, từ `BigDecimal`, từ số nguyên hay từ `double`?
2. Có chuỗi → `Double.parseDouble` → `BigDecimal` ở bất kỳ chỗ nào không?
3. Phép cộng, nhân, chia có dùng `double` trước khi lưu kết quả?
4. `BigDecimal.valueOf(double)` chỉ đang chuyển một `double` có sẵn hay đang được xem nhầm như cách khôi phục decimal?
5. Đơn vị, miền, scale, rounding mode và thời điểm rounding đã được ghi trong hợp đồng chưa?
6. JSON/JavaScript và SQL/ORM có thay đổi kiểu thành floating-point trung gian không?
7. Có kiểm thử số khó như 0,1; 0,1 + 0,2; decimal nhiều chữ số; giá có số 0 cuối; số nguyên vượt \(2^{53}\) chưa?

## 10. Những lỗi phổ biến và cách tránh

1. **“`double` chỉ để parse, sau đó dùng `BigDecimal` là đủ.”** Không đúng: parse đã có thể làm mất chữ số.
2. **“`new BigDecimal(0.1)` có nghĩa decimal 0,1.”** Nó biểu diễn decimal của một số binary64 xấp xỉ 0,1.
3. **“`BigDecimal.valueOf(d)` luôn khôi phục decimal gốc.”** Nó dùng `Double.toString(d)` và không đảo được phép làm tròn từ nhiều nguồn.
4. **“Cộng bằng `double` trước, chuyển sang `BigDecimal` sau sẽ chính xác.”** Kết quả `0.30000000000000004` chứng minh điều ngược lại.
5. **“Một biến DTO kiểu `double` không ảnh hưởng vì domain dùng `BigDecimal`.”** Thông tin có thể đã mất ngay tại DTO.
6. **“Gọi `setScale(2)` sẽ sửa mọi lỗi decimal.”** Rounding/normalization có thể thay đổi số hoặc che giấu sai sót, không khôi phục nguồn.
7. **“`BigDecimal.equals` và `compareTo == 0` giống nhau.”** `equals` xét thêm scale.
8. **“Dùng `(long)(doubleAmount*100)` là cách an toàn lấy cent.”** Có thể mất chính xác và cast bỏ phần lẻ không báo lỗi.
9. **“JSON number luôn giữ toàn bộ chữ số decimal.”** Điều đó phụ thuộc parser và kiểu dữ liệu từng client/server.
10. **“Không bao giờ được dùng `double`.”** Nếu miền nghiệp vụ chấp nhận đo lường xấp xỉ, `double` có thể là lựa chọn đúng. Vấn đề là dùng nó **trong pipeline đòi hỏi decimal chính xác**.

## 11. Checklist áp dụng trước khi phát hành

- Xác định nguồn có cần **giá trị thập phân chính xác** hay chỉ là phép đo xấp xỉ.
- Nhận/parsing trực tiếp thành `BigDecimal` từ chuỗi hoặc kiểu decimal chính xác, không qua `double`.
- Kiểm tra toàn bộ DTO, serializer, mapper, ORM, JDBC, CSV và client để loại bỏ conversion floating-point không chủ đích.
- Thực hiện cộng, trừ, nhân, chia bằng `BigDecimal` khi hợp đồng cần decimal chính xác.
- Ghi rõ scale/độ phân giải, miền min/max, đơn vị, rounding mode và **thời điểm** làm tròn.
- Nếu chuyển thành số nguyên đơn vị nhỏ, dùng `movePointRight` và `longValueExact()`/`intValueExact()`.
- Kiểm thử trường hợp decimal không biểu diễn được bằng binary64, số nguyên lớn, nhiều chữ số sau dấu, dữ liệu vượt miền và round-trip qua client/database.
- Nếu phát hiện sai số, tìm **bước đầu tiên** mất thông tin, không cố sửa ở bước cuối bằng một phép cast hoặc rounding tùy ý.

**Kết luận:** Việc chọn `BigDecimal` chỉ có ý nghĩa đầy đủ khi **dữ liệu không bị chuyển qua `double` trên đường tới nó**. Giữ decimal từ nguồn, tính trong decimal, kiểm tra miền/độ phân giải rõ ràng và chỉ làm tròn khi hợp đồng yêu cầu.

### Tài liệu Java chính thức

- [`BigDecimal`: constructor, `valueOf`, phép toán và scale](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [`Double`: `parseDouble`, `toString`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html)
- [`RoundingMode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)
- [JLS §4.2.3: Floating-point types and values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-008">Tách định dạng hiển thị khỏi tính toán và trao đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Tách định dạng hiển thị khỏi tính toán và trao đổi dữ liệu số trong Java

## 1. Một giá trị số, ba trách nhiệm khác nhau

Một số tiền có giá trị toán học là 12348.17 có thể xuất hiện với nhiều cách viết:

- **Trong tính toán:** đối tượng `BigDecimal` giữ giá trị thập phân để cộng, trừ, nhân, chia theo quy tắc nghiệp vụ.
- **Trên giao diện cho người dùng:** `12.348,17` ở một số locale sử dụng dấu phẩy thập phân, hoặc `12,348.17` ở locale dùng dấu chấm thập phân.
- **Khi trao đổi giữa hệ thống:** `12348.17` là một biểu diễn trung lập về locale nếu hợp đồng API chọn chuỗi thập phân có dấu chấm và không có phân nhóm.

Ba cách biểu diễn có mục đích khác nhau. **Giá trị dùng để tính toán không nên thay đổi chỉ vì locale hoặc kiểu trình bày thay đổi.** Chuỗi đã thêm dấu phân nhóm, dấu tiền tệ hay chữ viết tắt để hiển thị không mặc nhiên hợp lệ để đưa trở lại công thức hoặc truyền qua API.

Một mô hình đơn giản:

~~~text
Chuỗi nhập từ người dùng
    │ kiểm tra định dạng theo locale đã chọn
    ▼
Giá trị miền dữ liệu (BigDecimal + đơn vị/tiền tệ)
    │ tính toán theo chính sách precision, scale và rounding
    ▼
Giá trị kết quả BigDecimal
    ├──> Format bằng locale/currency --> Giao diện người dùng
    └──> Serialize bằng hợp đồng ổn định --> JSON/CSV/API/lưu trữ
~~~

Nếu chuỗi vào vốn là một giá trị chuẩn từ hệ thống khác, parse theo **hợp đồng trao đổi** thay vì parse theo locale của người đang đăng nhập. Nếu chuỗi vào đến từ form người dùng, parse theo định dạng mà form yêu cầu. **Điều kiện nhận đầu vào** khác với **định dạng hiển thị đầu ra** và khác với **hợp đồng trao đổi**.

## 2. Lớp tính toán: giữ số, không ghép ký tự định dạng

Khi dữ liệu là số thập phân nghiệp vụ cần chính xác, `BigDecimal` thường phù hợp hơn `double`. Hãy dựng đối tượng từ nguồn thập phân như chuỗi:

~~~java
BigDecimal price = new BigDecimal("12345.67");
BigDecimal fee = new BigDecimal("2.50");
BigDecimal total = price.add(fee);

System.out.println(total.toPlainString()); // 12348.17
~~~

Đây là **đoạn mã minh họa**, cần import `java.math.BigDecimal` và đặt trong phương thức hoặc chương trình Java thích hợp. Cả `price`, `fee` và `total` đều là dữ liệu số, chưa mang dấu phân cách hàng nghìn hay ký hiệu tiền tệ.

Điểm cần phân biệt:

- `new BigDecimal("0.1")` biểu diễn chính xác số thập phân 0.1.
- `new BigDecimal(0.1)` nhận giá trị nhị phân `double` gần với 0.1, nên có thể chứa một biểu diễn thập phân dài không mong muốn.
- `BigDecimal.valueOf(0.1)` dựa vào dạng chuỗi thập phân của `double`; thường hữu dụng nếu dữ liệu đã ở dạng `double`, nhưng không khôi phục được văn bản thập phân ban đầu chưa biết.
- `BigDecimal` giữ giá trị, scale và precision theo quy tắc kiểu số; **không giữ locale, ký hiệu tiền tệ hay dấu phân nhóm của văn bản nguồn**.

Với số tiền, đơn vị là một phần của mô hình nghiệp vụ. Giá trị `100.00` USD và `100.00` EUR không tự động là cùng một lượng tiền có thể cộng. Giữ **mã tiền tệ hoặc đơn vị** trong thuộc tính miền dữ liệu riêng và xác nhận hai đại lượng có cùng đơn vị trước khi cộng.

Một thiết kế minh họa:

~~~java
record Amount(BigDecimal value, String currencyCode) {}
~~~

`record` trên chỉ minh họa ranh giới kiểu dữ liệu, chưa triển khai kiểm tra giá trị hợp lệ hoặc quy tắc chuyển đổi tiền tệ. Để dùng thực tế, cần xác thực mã tiền tệ, null, miền giá trị và các phép toán tương thích đơn vị.

## 3. Lớp hiển thị: locale, dấu phân nhóm và độ dài chữ số

Cùng một `BigDecimal` có thể hiển thị khác nhau theo khu vực:

| Giá trị `BigDecimal` | Hiển thị kiểu US | Hiển thị kiểu Đức |
| --- | --- | --- |
| 12348.17 | `12,348.17` | `12.348,17` |
| 1000.50 | `1,000.50` | `1.000,50` |
| 0.50 | `0.50` | `0,50` |

Bảng sử dụng quy tắc định dạng **cụ thể đã chọn**: có dấu nhóm hàng nghìn và đúng hai chữ số sau dấu thập phân. Đó không phải quy tắc bắt buộc cho mọi ứng dụng, mọi kiểu số và mọi locale.

Java cung cấp `NumberFormat` và `DecimalFormat` trong `java.text`. Ví dụ:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

BigDecimal total = new BigDecimal("12348.17");
DecimalFormat german = new DecimalFormat(
        "#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
german.setRoundingMode(RoundingMode.HALF_UP);

System.out.println(german.format(total)); // 12.348,17
~~~

Pattern `#,##0.00` trong ví dụ quy định dấu phân nhóm và hai chữ số thập phân. `DecimalFormatSymbols` cung cấp dấu phân nhóm và dấu thập phân theo `Locale.GERMANY`. Đây là đoạn minh họa sử dụng bên trong phương thức.

**Format trả về `String`, không biến đối tượng `BigDecimal` đầu vào thành giá trị mới.** Hai người dùng có thể nhìn thấy cách trình bày khác nhau mà vẫn tính trên cùng một số.

### 3.1. Chính sách làm tròn khi hiển thị

Một `DecimalFormat` có thể giới hạn số chữ số được in ra. Nếu độ chính xác nguồn lớn hơn, chuỗi hiển thị có thể bị làm tròn:

~~~java
BigDecimal precise = new BigDecimal("1.005");
DecimalFormat display = new DecimalFormat(
        "#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
display.setRoundingMode(RoundingMode.HALF_UP);

System.out.println(display.format(precise)); // 1,01
System.out.println(precise.toPlainString()); // 1.005
~~~

`display.format` chỉ quyết định người dùng **nhìn thấy** gì; giá trị `precise` vẫn chính xác là 1.005. Vì vậy **chuỗi hiển thị đã bị làm tròn không được dùng thay cho giá trị tính toán nếu chính sách nghiệp vụ chưa cho phép làm tròn tại đó**.

Ví dụ hai khoản `1.005`:

| Cách xử lý | Kết quả |
| --- | --- |
| Hiển thị từng khoản với hai chữ số và `HALF_UP` | `1,01` và `1,01` |
| Tổng chính xác `1.005 + 1.005` | `2.010` |
| Hiển thị tổng chính xác ở hai chữ số | `2,01` |
| Cộng hai chuỗi hiển thị đã làm tròn như hai số `1.01` | `2.02` |

Đây **không tự động là lỗi phần mềm**: nếu hóa đơn quy định phải làm tròn từng dòng trước khi cộng, `2.02` có thể là kết quả hợp đồng. Nếu quy định cộng các số chính xác rồi làm tròn tổng, `2.01` mới đúng. Điều quan trọng là **tách chính sách tính toán ra khỏi cách hiển thị**, không để thao tác format âm thầm quyết định thuật toán cộng.

### 3.2. Tiền tệ không chỉ là dấu tiền

`NumberFormat.getCurrencyInstance(locale)` có thể dùng cho giao diện trình bày tiền tệ, nhưng một `Locale` **không thay thế thuộc tính `Currency` của giá trị miền**. Locale mô tả thói quen hiển thị; đơn vị tiền là dữ liệu nghiệp vụ. Người dùng ở Việt Nam vẫn có thể xem một khoản USD. Hãy truyền currency của khoản tiền vào formatter thích hợp, không mặc định suy ra đơn vị từ vị trí người xem.

Số chữ số thập phân “mặc định” của một tiền tệ cũng không thay thế các yêu cầu cụ thể về quy tắc làm tròn của giao dịch, thuế, phí hoặc thanh toán.

### 3.3. Formatter là đối tượng có trạng thái

`NumberFormat`/`DecimalFormat` có các thuộc tính có thể thay đổi, như maximum fraction digits, grouping và rounding mode. Chúng **không bảo đảm thread-safe khi chia sẻ và dùng đồng thời**. Trong ứng dụng nhiều luồng, thường tạo formatter theo từng lần dùng/yêu cầu hoặc bảo đảm cơ chế sử dụng có kiểm soát. Không chia sẻ một `static DecimalFormat` rồi thay `Locale`/pattern từ nhiều request mà không có thiết kế đồng bộ.

## 4. Lớp trao đổi: hợp đồng ổn định và độc lập với người xem

Chuỗi ở giao diện ví dụ `12.348,17` có nghĩa ở locale Đức, nhưng hệ thống khác có thể hiểu dấu chấm là dấu thập phân và dấu phẩy là dấu nhóm. **Không đưa trực tiếp chuỗi hiển thị vào JSON, CSV hoặc API nếu hợp đồng nhận không định nghĩa cách parse chuỗi đó.**

Một hợp đồng trao đổi dữ liệu số thập phân có thể quy định:

- Giá trị là **chuỗi thập phân**, dấu thập phân là `.`, không dùng dấu phân nhóm; ví dụ `"12348.17"`.
- Dấu âm là `-`; đơn vị hoặc mã tiền tệ là trường riêng; scale/precision/miền giá trị được mô tả rõ.
- Phía nhận dùng `BigDecimal` hoặc kiểu decimal tương thích để parse, thay vì tự ép qua `double`.
- Các thay đổi làm tròn phải xảy ra theo chính sách nghiệp vụ, không do locale của máy gửi hoặc máy nhận.

Ví dụ một **hợp đồng JSON do ứng dụng tự thiết kế** cho số tiền:

~~~json
{
  "amount": "12348.17",
  "currency": "EUR"
}
~~~

Ở đây `amount` cố ý là **JSON string** để phía nhận có thể tái dựng `BigDecimal` từ chuỗi mà không phải đi qua `double`. Đây là một lựa chọn thiết kế, không phải yêu cầu rằng JSON phải luôn mã hóa số thập phân thành string. JSON cũng có cú pháp number `12348.17`, nhưng một số thư viện/consumer có thể đưa JSON number vào kiểu nhị phân `double`; nếu hợp đồng cần độ chính xác thập phân, phải quy định kiểu xử lý ở cả hai phía.

### 4.1. Biểu diễn chuẩn từ `BigDecimal`

~~~java
BigDecimal total = new BigDecimal("12348.17");

String transportValue = total.toPlainString(); // "12348.17"
BigDecimal received = new BigDecimal(transportValue);
~~~

`toPlainString()` trả biểu diễn không dùng ký pháp mũ, phù hợp khi hợp đồng chọn **chuỗi thập phân dạng thường**. Ví dụ `new BigDecimal("1E+3").toPlainString()` trả `"1000"`. Tuy nhiên, `toPlainString()` không giữ nguyên mọi thông tin về scale trong các trường hợp như scale âm; một phép round-trip qua nó có thể giữ **giá trị** nhưng không giữ **biểu diễn scale ban đầu**. Nếu scale là dữ liệu bắt buộc, hãy định nghĩa thêm trường scale hoặc quy định biểu diễn có thể round-trip theo nhu cầu.

`BigDecimal.toString()` cũng độc lập locale nhưng có thể dùng ký pháp mũ, ví dụ `1E+3`. Nếu hợp đồng API cho phép ký pháp mũ, nó có thể phù hợp; nếu cấm, phải dùng hoặc xây dựng biểu diễn theo hợp đồng. Đừng thay `toPlainString()` cho `toString()` chỉ vì sở thích nếu hợp đồng và chi phí biểu diễn có ràng buộc đặc biệt.

### 4.2. Không format theo locale rồi parse trở lại để tính

Tránh chuỗi thao tác:

~~~text
BigDecimal -> DecimalFormat.format -> String hiển thị
           -> parser theo locale khác -> tính toán hoặc trao đổi
~~~

Rủi ro gồm mất chữ số do format, hiểu sai dấu nhóm/dấu thập phân, xử lý không thống nhất của ký hiệu tiền, và lỗi do locale máy chủ thay đổi. Khi cần làm tròn số trước khi lưu hoặc truyền, gọi **phép toán số** có quy tắc tường minh:

~~~java
BigDecimal accepted = total.setScale(2, RoundingMode.HALF_EVEN);
String transport = accepted.toPlainString();
~~~

Đây là việc **thay đổi giá trị theo hợp đồng**, khác với format giao diện thuần túy. Nếu cấm làm tròn, dùng `setScale(2, RoundingMode.UNNECESSARY)` để báo `ArithmeticException` khi giá trị không biểu diễn chính xác ở scale 2.

## 5. Nhận dữ liệu: chuỗi người dùng và chuỗi giao thức có cách parse khác nhau

### 5.1. Chuỗi chuẩn do API gửi

Nếu hợp đồng trao đổi gửi `"12348.17"` theo dạng thập phân dấu chấm, dùng:

~~~java
BigDecimal amount = new BigDecimal("12348.17");
~~~

Constructor nhận `String` dùng cú pháp số thập phân của `BigDecimal`, **không đọc locale người dùng**. Chuỗi `"12.348,17"` hoặc `"12,348.17"` có dấu nhóm không phải chuỗi đầu vào hợp lệ cho constructor này.

Đừng gọi `Double.parseDouble` trước rồi tạo `BigDecimal` từ `double` nếu mục tiêu là giữ chính xác giá trị thập phân nguồn.

### 5.2. Chuỗi gõ theo locale của giao diện

Khi một người dùng nhập `12.348,17` với quy ước Đức, formatter/parser theo `Locale.GERMANY` mới có ý nghĩa. Ví dụ parser trả kết quả số thập phân:

~~~java
DecimalFormat parser = new DecimalFormat(
        "#,##0.################",
        DecimalFormatSymbols.getInstance(Locale.GERMANY));
parser.setParseBigDecimal(true);

String input = "12.348,17";
ParsePosition pos = new ParsePosition(0);
Number parsed = parser.parse(input, pos);

if (parsed == null || pos.getIndex() != input.length()) {
    throw new IllegalArgumentException("Sai dinh dang so");
}
BigDecimal value = (BigDecimal) parsed;
// value = 12348.17
~~~

`setParseBigDecimal(true)` yêu cầu `DecimalFormat.parse` trả `BigDecimal` trong trường hợp số thông thường, tránh đưa giá trị thập phân qua `double`. `ParsePosition` giúp kiểm tra parser đã đọc **hết chuỗi**, bởi nhiều parser có thể đọc thành công chỉ một tiền tố hợp lệ.

**Giới hạn:** Kiểm tra đã tiêu thụ hết chuỗi không tự bảo đảm mọi nhóm chữ số đều theo định dạng chặt mà ứng dụng yêu cầu. `DecimalFormat` có thể linh hoạt với vị trí dấu nhóm ở một số trường hợp. Nếu dữ liệu nhập phải tuân thủ chuẩn nghiêm ngặt, hãy bổ sung bước xác minh pattern/nhóm/ký tự được phép, khoảng trắng và miền giá trị trước khi chấp nhận. Không dùng việc parse thành công để ngầm hợp thức hóa mọi chuỗi.

### 5.3. Tránh suy diễn locale từ chuỗi

Chuỗi `1,234` có thể là một nghìn hai trăm ba mươi bốn theo một quy ước hoặc là một phẩy hai ba bốn theo quy ước khác. Nếu không biết locale, **không thể luôn tự động xác định ý định người nhập**.

Hệ thống nên chọn một trong các cách: form quy định locale rõ ràng; hiển thị ví dụ ngay cạnh ô nhập; dùng input tách phần nguyên/phần lẻ; hoặc bắt người dùng xác nhận khi giá trị không rõ ràng. Với API giữa các dịch vụ, tốt nhất công bố cú pháp duy nhất trong schema và từ chối chuỗi trái hợp đồng.

## 6. Chương trình hoàn chỉnh với Java 17+

Tạo tệp **NumericBoundariesDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class NumericBoundariesDemo {
    private static DecimalFormat formatter(Locale locale) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.00",
                DecimalFormatSymbols.getInstance(locale));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static BigDecimal parseGerman(String input) {
        DecimalFormat parser = new DecimalFormat(
                "#,##0.################",
                DecimalFormatSymbols.getInstance(Locale.GERMANY));
        parser.setParseBigDecimal(true);

        ParsePosition position = new ParsePosition(0);
        Number parsed = parser.parse(input, position);
        if (parsed == null || position.getIndex() != input.length()) {
            throw new IllegalArgumentException("Invalid localized amount");
        }
        return (BigDecimal) parsed;
    }

    public static void main(String[] args) {
        BigDecimal price = new BigDecimal("12345.67");
        BigDecimal fee = new BigDecimal("2.50");
        BigDecimal total = price.add(fee);

        DecimalFormat german = formatter(Locale.GERMANY);
        DecimalFormat us = formatter(Locale.US);

        System.out.println("calculation: " + total.toPlainString());
        System.out.println("display de: " + german.format(total));
        System.out.println("display us: " + us.format(total));

        BigDecimal item = new BigDecimal("1.005");
        System.out.println("single display de: " + german.format(item));
        System.out.println("exact two-item sum: "
                + item.add(item).toPlainString());
        System.out.println("sum display de: "
                + german.format(item.add(item)));

        System.out.println("localized input: "
                + parseGerman("12.348,17").toPlainString());

        String wire = total.toPlainString();
        System.out.println("wire decimal: " + wire);
        System.out.println("roundtrip exact: "
                + (new BigDecimal(wire).compareTo(total) == 0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NumericBoundariesDemo.java
java NumericBoundariesDemo
~~~

Đầu ra:

~~~text
calculation: 12348.17
display de: 12.348,17
display us: 12,348.17
single display de: 1,01
exact two-item sum: 2.010
sum display de: 2,01
localized input: 12348.17
wire decimal: 12348.17
roundtrip exact: true
~~~

Ba điểm quan trọng được chương trình chứng minh:

1. **Một tổng chính xác** `BigDecimal` có thể có **nhiều chuỗi hiển thị** khác nhau theo locale, trong khi dữ liệu tính toán không đổi.
2. **Format có làm tròn**: chuỗi hiển thị `1,01` của từng khoản `1.005` không phải giá trị dùng để cộng; tổng chính xác vẫn là `2.010`.
3. **Trao đổi dùng hợp đồng riêng**: chuỗi `12348.17` được đọc lại bởi `new BigDecimal(wire)` cho giá trị toán học giống nguồn, không phụ thuộc locale.

Đây là một chương trình Java thuần chỉ dùng Java standard library, không yêu cầu Spring Boot hay thư viện ngoài.

## 7. Tách ranh giới trong thiết kế ứng dụng

Một tổ chức mã dễ kiểm soát thường có ba lớp trách nhiệm:

| Lớp | Nhận dữ liệu | Trả dữ liệu | Có nên dùng locale? |
| --- | --- | --- | --- |
| Domain/tính toán | `BigDecimal`, đơn vị, quy tắc số học | `BigDecimal` kết quả | Thường không |
| UI/hiển thị | Giá trị domain + `Locale` + tùy chọn hiển thị | Chuỗi để hiển thị | Có |
| API/serialization | Giá trị domain + schema/hợp đồng | JSON/CSV/protocol chuẩn | Theo hợp đồng; tránh dựa vào locale của người dùng |

Gợi ý giao diện phương thức theo trách nhiệm:

~~~java
BigDecimal calculateTotal(List<BigDecimal> amounts);
String formatForUser(BigDecimal value, Locale locale);
String serializeDecimal(BigDecimal value);
BigDecimal parseWireDecimal(String wireValue);
BigDecimal parseLocalizedDecimal(String text, Locale inputLocale);
~~~

Đây chỉ là **chữ ký gợi ý để đặt vào một API thiết kế riêng**, không phải chương trình runnable độc lập. Trong hiện thực, cần xác định các chính sách null, rounding, validation, giới hạn đầu vào và đơn vị tiền tệ. Nên viết test riêng cho các ranh giới để phát hiện lỗi do locale hoặc serialization.

### 7.1. Dữ liệu qua DTO và JSON

Nếu DTO sử dụng `BigDecimal`, thư viện JSON có thể hỗ trợ serialize/deserialize thành số JSON hoặc chuỗi tùy cấu hình. **Không mặc định rằng consumer khác ngôn ngữ sẽ giữ độ chính xác tương đương**. Một consumer đọc number vào `double` có thể mất chữ số dù phía gửi dùng `BigDecimal`. Cần xác định rõ contract JSON và kiểm thử cả producer lẫn consumer.

Ví dụ contract dùng string:

~~~json
{
  "unitPrice": "1.005",
  "quantity": 2,
  "total": "2.010",
  "currency": "EUR"
}
~~~

Nếu có nghiệp vụ làm tròn theo dòng hoặc theo tổng, cần quy định số `total` đã được tính thế nào. Hiển thị thành `2,01` chỉ là một phép format; **không đủ để xác định số tiền đã được làm tròn theo dòng hay theo tổng**.

### 7.2. Dữ liệu trong cơ sở dữ liệu

Với số thập phân cần chính xác, thường dùng cột `DECIMAL`/`NUMERIC` có precision và scale phù hợp. Java đọc/ghi qua `BigDecimal` để tránh chuyển trung gian sang `float`/`double`. Thao tác database có thể áp ràng buộc scale, precision và quy tắc làm tròn riêng; cần kiểm tra schema và hành vi của database/driver cụ thể.

Không lưu những chuỗi như `"12.348,17 EUR"` vào cột giá trị số khi cần truy vấn tổng, lọc và so sánh số. Ký hiệu tiền, nhóm hàng nghìn, dấu thập phân locale là thông tin trình bày; lưu giá trị số và đơn vị theo schema.

### 7.3. Báo cáo và CSV

Một tập tin CSV có thể dùng dấu phẩy phân cột, nên chuỗi hiển thị `"12,348.17"` có dấu phẩy cũng có thể đòi hỏi quoting/escaping. Cách an toàn hơn cho trao đổi máy là thống nhất **delimiter, dấu thập phân, encoding, quy tắc quoting và locale** trong hợp đồng xuất dữ liệu. Không giả định một CSV “dễ đọc bằng Excel ở máy mình” luôn được dịch vụ khác hiểu như nhau.

Với báo cáo dành cho con người, có thể tạo một bản CSV tương thích locale cụ thể; với dữ liệu trao đổi, hãy đưa ra một định dạng có thể parse nhất quán và tài liệu hóa.

## 8. Kiểm tra và thử nghiệm bắt buộc theo ranh giới

Các tình huống nên có trong kiểm thử:

- **Cùng giá trị, nhiều locale:** 12348.17 hiển thị ở US và Đức khác chuỗi nhưng kết quả domain không đổi.
- **Làm tròn hiển thị:** 1.005 format còn 2 chữ số khác với giá trị domain ban đầu.
- **Cộng trước hoặc sau làm tròn:** hai lần 1.005 cho kết quả khác nếu cộng hai chuỗi đã làm tròn; so với hợp đồng nghiệp vụ.
- **Đọc chuỗi giao thức:** `"12348.17"` parse chính xác; từ chối `"12,348.17"` nếu contract chỉ cho phép dạng chuẩn không phân nhóm.
- **Đọc chuỗi locale:** parse `"12.348,17"` với locale phù hợp; phát hiện phần dư không được parser tiêu thụ, kiểm tra group nếu yêu cầu nghiêm ngặt.
- **Scale, precision và số âm:** giá trị `0.10`, `1000`, `1E+3`, số âm, số rất lớn, hoặc nhiều chữ số sau dấu thập phân.
- **Round-trip:** gửi và nhận có bảo toàn **giá trị toán học** không? Nếu cần bảo toàn **scale**, phải kiểm tra thêm scale hoặc sử dụng contract hỗ trợ.

Không dùng phép so sánh chuỗi hiển thị làm phép kiểm tra bằng nhau của số. So sánh giá trị `BigDecimal` bằng `compareTo` nếu chỉ xét giá trị toán học; dùng `equals` nếu còn yêu cầu scale giống nhau.

## 9. Lỗi phổ biến và cách tránh

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| API gửi `"12.348,17"` rồi hệ thống nhận parse lỗi | Chuỗi hiển thị gắn với locale | Gửi dạng chuẩn `"12348.17"` theo contract |
| Số tiền thay đổi khi đổi ngôn ngữ UI | Code dùng chuỗi đã format làm nguồn tính | Giữ `BigDecimal` domain, format chỉ ở lớp trình bày |
| Hai dòng `1.005` hiển thị `1,01`, tổng hiển thị `2,01` | Tổng được cộng chính xác rồi làm tròn một lần | Xác định chính sách làm tròn theo dòng hoặc theo tổng; giải thích nhất quán |
| `Double.parseDouble("1,25")` thất bại | Parser chuẩn không đọc dấu phẩy decimal theo locale | Parse input UI với `DecimalFormat` cấu hình locale |
| `NumberFormat.parse` chỉ đọc được một phần | Parser có thể chấp nhận tiền tố hợp lệ | Dùng `ParsePosition` và kiểm tra cuối chuỗi; xác minh định dạng chặt nếu cần |
| Giá trị từ JSON mất chữ số | Consumer trung gian đọc vào `double` | Định nghĩa schema và kiểu decimal tương thích; dùng chuỗi khi contract yêu cầu |
| Số thập phân được nhân/chia rồi ghi dạng chuỗi theo UI | Chính sách làm tròn bị trộn với format | Áp `setScale`/`MathContext` khi tính theo nghiệp vụ, serialize độc lập |
| Code dùng formatter `static` chia sẻ giữa các request | `DecimalFormat` có trạng thái và không được bảo đảm thread-safe | Tạo formatter theo phạm vi request hoặc quản lý concurrency tường minh |
| Bản ghi chỉ còn `"100.00"` nhưng mất loại tiền | Đơn vị bị xử lý như trang trí | Lưu currency/unit trong domain và contract |
| Chuỗi `"1,234"` được tự đoán tùy máy | Locale không rõ | Chỉ định locale/form input rõ hoặc dùng format chuẩn máy |

## 10. Tự kiểm tra

1. **Khi đổi locale từ Mỹ sang Đức, nên thay đổi số đang tính không?** Không. Chỉ chuỗi hiển thị thay đổi, giá trị `BigDecimal` phải giữ nguyên.
2. **Tại sao `"12.348,17"` không nên là mặc định của một API machine-to-machine?** Nó phụ thuộc locale và có thể bị parse khác nhau. Chọn một contract xác định như chuỗi thập phân dấu chấm, không dấu nhóm.
3. **Vì sao không cộng trực tiếp `"1,01" + "1,01"` khi hai chuỗi vốn là biểu diễn của `1.005`?** Vì formatter đã làm tròn mất thông tin; nghiệp vụ phải chỉ rõ cộng trước hay làm tròn từng khoản.
4. **`BigDecimal.toPlainString()` có luôn giữ scale ban đầu không?** Không trong mọi trường hợp, đặc biệt với scale âm; nó bảo toàn giá trị thập phân để đọc lại, còn scale cần contract riêng nếu quan trọng.
5. **Nếu form nhập theo locale Đức nhưng API nhận dấu chấm thì có mâu thuẫn không?** Không. Hai ranh giới có trách nhiệm khác nhau: parse locale ở UI, rồi serialize chuẩn ở tầng API.
6. **Vì sao `setParseBigDecimal(true)` hữu ích với `DecimalFormat`?** Nó giúp lấy `BigDecimal` trực tiếp từ số thập phân được parse, tránh trung gian `double` đối với trường hợp số thông thường.
7. **Một số được format với `HALF_UP` có làm thay đổi `BigDecimal` gốc không?** Không. `format` tạo chuỗi; thay đổi giá trị để lưu phải là thao tác tính toán được quy định rõ.
8. **Phải ghi gì trong hợp đồng trao đổi số thập phân?** Kiểu biểu diễn (JSON number hay string), dấu thập phân, nhóm chữ số, kiểu nhận ở hai phía, precision/scale, đơn vị và quy tắc làm tròn.

**Ghi nhớ:** **Tính trên số; hiển thị theo locale; trao đổi theo contract.** `BigDecimal` là giá trị miền, `NumberFormat`/`DecimalFormat` tạo chuỗi cho người dùng, còn `toPlainString` hoặc serializer được cấu hình theo schema tạo dạng trao đổi. Không đưa kết quả format hiển thị trở lại phép tính hay mạng dữ liệu nếu chưa có bước parse và chính sách nghiệp vụ phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-009">Không suy ra quy tắc làm tròn nghiệp vụ chỉ từ kiểu dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy ra quy tắc làm tròn nghiệp vụ chỉ từ kiểu dữ liệu

Một trường được khai báo là `int`, `double`, `BigDecimal`, `DECIMAL(18, 2)` hay “kiểu tiền tệ” **không tự trả lời được** câu hỏi nghiệp vụ: *làm tròn lúc nào, đến mức nào, theo hướng nào và trên đơn vị tính nào?*

Kiểu dữ liệu mô tả **cách một giá trị được biểu diễn và giới hạn xử lý của nền tảng**. Quy tắc làm tròn thuộc **hợp đồng nghiệp vụ**, cần được xác định độc lập, diễn đạt rõ ràng, kiểm thử và duy trì nhất quán trong toàn bộ luồng xử lý.

Ví dụ, cùng giá trị thập phân chính xác `1.005`:

```java
BigDecimal amount = new BigDecimal("1.005");

System.out.println(amount.setScale(2, RoundingMode.HALF_UP));   // 1.01
System.out.println(amount.setScale(2, RoundingMode.HALF_EVEN)); // 1.00
```

Cả hai kết quả đều được tạo từ cùng **một kiểu `BigDecimal`**, cùng **scale đích bằng 2**, nhưng khác **chế độ làm tròn**. Muốn biết kết quả nào đúng phải xem yêu cầu của nghiệp vụ cụ thể; Java không thể suy ra điều đó từ tên kiểu.

## 1. Tách ba lớp quyết định: biểu diễn, phép tính và nghiệp vụ

| Lớp quyết định | Câu hỏi cần trả lời | Ví dụ |
|---|---|---|
| **Biểu diễn dữ liệu** | Lưu giá trị nào, chính xác đến đâu, giới hạn ra sao? | `BigDecimal`, `long` số đơn vị nhỏ nhất, `DECIMAL(18, 4)` |
| **Phép tính số học** | Thực hiện phép cộng/nhân/chia thế nào, có cần giới hạn precision không? | `multiply` chính xác; `divide` cần scale/mode nếu thương không kết thúc |
| **Quy tắc nghiệp vụ** | Khi nào làm tròn, làm tròn gì, bao nhiêu chữ số, hướng nào, xử lý phần dư ra sao? | Làm tròn thuế trên từng dòng hay toàn hóa đơn; giảm giá trước hay sau thuế |

Một quyết định ở lớp biểu diễn **không thay thế** quyết định ở lớp nghiệp vụ:

- Chọn `BigDecimal` giúp biểu diễn số thập phân và tính toán theo cơ chế decimal, nhưng **không chỉ định** `HALF_UP`, `HALF_EVEN` hoặc scale đích.
- Chọn `double` không hàm ý được làm tròn theo quy tắc ngân hàng. Nó dùng số dấu phẩy động nhị phân có sai số biểu diễn đối với nhiều phân số thập phân.
- Chọn `long` lưu “đơn vị nhỏ nhất” có thể tránh một số vấn đề số thập phân ở bước lưu trữ, nhưng **việc đổi từ giá trị chia nhỏ sang đơn vị nguyên vẫn cần chính sách làm tròn**.
- Khai báo `DECIMAL(p, s)` trong cơ sở dữ liệu mô tả precision và scale giới hạn của cột. Hành vi tự chuyển đổi, làm tròn hoặc từ chối dữ liệu vượt scale/phạm vi tùy hệ quản trị và cấu hình; cột `DECIMAL(18, 2)` **không xác định được** liệu một khoản phí phải làm tròn trên từng dòng hay cuối kỳ.

Nếu quy tắc nghiệp vụ không được nêu, **không được tự lấy số chữ số sau dấu trong kiểu lưu trữ làm chính sách**.

## 2. Precision, scale và RoundingMode là công cụ chứ không phải chính sách

`BigDecimal` có `unscaledValue`, `scale()` và `precision()`; giá trị bằng `unscaledValue × 10^(-scale)`. `setScale(2, mode)` chọn hai chữ số thập phân, còn `MathContext(4, mode)` giới hạn bốn chữ số có nghĩa của toàn bộ số. Cả hai đều là lựa chọn của người lập trình, chưa nói hệ thống **nên** làm tròn ở đâu.

```java
BigDecimal value = new BigDecimal("123.456");
System.out.println(value.setScale(2, RoundingMode.HALF_UP)); // 123.46
System.out.println(value.round(new MathContext(4, RoundingMode.HALF_UP))); // 123.5
```

Bài `Đưa precision, scale và rounding vào hợp đồng` trình bày định nghĩa, kiểm soát scale, precision và quy tắc chia. Ở đây ta xét **quyết định chính sách nghiệp vụ** cần áp những phép ấy tại bước nào.

## 3. RoundingMode không tự chọn thời điểm làm tròn

Các mode thông dụng gồm `HALF_UP` (hòa ra xa 0), `HALF_EVEN` (hòa về chữ số chẵn), `DOWN` (về 0), `UP` (xa 0), `FLOOR` (về âm vô cực), `CEILING` (về dương vô cực); `HALF_DOWN` và `UNNECESSARY` xử lý hòa/cấm làm tròn theo hợp đồng riêng.

```java
System.out.println(new BigDecimal("-1.235").setScale(2, RoundingMode.FLOOR)); // -1.24
System.out.println(new BigDecimal("-1.235").setScale(2, RoundingMode.DOWN));  // -1.23
```

Sự khác nhau của hai dòng trên phản ánh hướng làm tròn với số âm. Song ngay cả khi cùng mode và cùng scale, làm tròn ở **từng dòng**, **từng phép tính** hay **cuối tổng** có thể cho kết quả khác nhau. Bài này đặt trọng tâm ở quy tắc kết toán và thứ tự thao tác, không suy chúng ra từ Java type.

## 4. Cùng mode, cùng scale nhưng khác thời điểm làm tròn sẽ khác kết quả

Đây là một trong những lý do mạnh nhất khiến ta không thể suy quy tắc làm tròn từ kiểu dữ liệu.

Giả sử ba dòng độc lập, mỗi dòng có giá trị chính xác là `0.335`. Theo quy tắc `HALF_UP` và kết quả ở scale 2, có ít nhất hai lựa chọn:

**Lựa chọn A: làm tròn từng dòng, sau đó cộng**

```text
0.335 → 0.34
0.335 → 0.34
0.335 → 0.34
Tổng = 1.02
```

**Lựa chọn B: cộng giá trị chưa làm tròn, sau đó làm tròn tổng**

```text
0.335 + 0.335 + 0.335 = 1.005
1.005 → 1.01
```

Chênh lệch ở đây là **0.01** dù:

- Cùng dùng `BigDecimal`.
- Cùng đích scale 2.
- Cùng mode `RoundingMode.HALF_UP`.
- Không có lỗi biểu diễn nhị phân như `double`.

Vì vậy một quy tắc đầy đủ phải nêu **đơn vị làm tròn**: từng đơn giá, từng lượng nhân đơn giá, từng khoản thuế dòng, tổng hóa đơn, từng giao dịch, cuối tháng hay một mốc khác. Số liệu lưu trữ cũng cần phân biệt **giá trị chưa làm tròn** với **giá trị đã kết toán** nếu nghiệp vụ cần đối soát.

Tương tự, thứ tự của chiết khấu, thuế, phân bổ và các khoản điều chỉnh có thể thay đổi cơ sở tính. Không nên tự quyết định thứ tự hoặc mặc định rằng cách thực hiện đầu tiên trong mã là yêu cầu chuẩn.

## 5. Ví dụ Java 17+ chạy độc lập

Lưu tệp `BusinessRoundingDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class BusinessRoundingDemo {
    record RoundingPolicy(int scale, RoundingMode mode) {
        RoundingPolicy {
            if (scale < 0) {
                throw new IllegalArgumentException("scale must be nonnegative");
            }
            Objects.requireNonNull(mode, "mode");
        }

        BigDecimal apply(BigDecimal value) {
            return Objects.requireNonNull(value, "value")
                    .setScale(scale, mode);
        }
    }

    static BigDecimal roundEachLine(
            List<BigDecimal> lines, RoundingPolicy policy) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal line : lines) {
            total = total.add(policy.apply(line));
        }
        return total;
    }

    static BigDecimal roundOnlyTotal(
            List<BigDecimal> lines, RoundingPolicy policy) {
        BigDecimal rawTotal = BigDecimal.ZERO;
        for (BigDecimal line : lines) {
            rawTotal = rawTotal.add(line);
        }
        return policy.apply(rawTotal);
    }

    public static void main(String[] args) {
        BigDecimal midpoint = new BigDecimal("1.005");

        RoundingPolicy halfUp =
                new RoundingPolicy(2, RoundingMode.HALF_UP);
        RoundingPolicy halfEven =
                new RoundingPolicy(2, RoundingMode.HALF_EVEN);

        System.out.println("HALF_UP 1.005 = "
                + halfUp.apply(midpoint));
        System.out.println("HALF_EVEN 1.005 = "
                + halfEven.apply(midpoint));
        System.out.println("negative HALF_UP = "
                + halfUp.apply(new BigDecimal("-1.005")));

        List<BigDecimal> lines = List.of(
                new BigDecimal("0.335"),
                new BigDecimal("0.335"),
                new BigDecimal("0.335"));

        System.out.println("round each line = "
                + roundEachLine(lines, halfUp));
        System.out.println("round only total = "
                + roundOnlyTotal(lines, halfUp));

        BigDecimal base = new BigDecimal("19.99");
        BigDecimal rate = new BigDecimal("0.075");
        BigDecimal exactProduct = base.multiply(rate);
        System.out.println("unrounded product = " + exactProduct);
        System.out.println("product rounded = "
                + halfUp.apply(exactProduct));

        BigDecimal thirds = BigDecimal.ONE.divide(
                new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        System.out.println("1 / 3 to scale 2 = " + thirds);

        BigDecimal value = new BigDecimal("1234.567");
        System.out.println("scale 2 = " + halfUp.apply(value));
        System.out.println("precision 3 = "
                + value.round(new MathContext(3, RoundingMode.HALF_UP)));

        try {
            new BigDecimal("3.1415")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("UNNECESSARY rejected rounding");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BusinessRoundingDemo.java
java BusinessRoundingDemo
```

**Stdout:**

```text
HALF_UP 1.005 = 1.01
HALF_EVEN 1.005 = 1.00
negative HALF_UP = -1.01
round each line = 1.02
round only total = 1.01
unrounded product = 1.49925
product rounded = 1.50
1 / 3 to scale 2 = 0.33
scale 2 = 1234.57
precision 3 = 1.23E+3
UNNECESSARY rejected rounding
```

Chương trình cố ý nhận `RoundingPolicy` như một **tham số nghiệp vụ tường minh**. `RoundingPolicy` mô tả scale và mode, trong khi hai hàm `roundEachLine` và `roundOnlyTotal` đại diện cho **hai thời điểm làm tròn khác nhau**. Một chính sách hoàn chỉnh trong ứng dụng thật còn phải mô tả đơn vị, giai đoạn tính toán, phiên bản và nguồn quy định.

Hàm `roundOnlyTotal` dùng phép cộng chính xác của `BigDecimal` trước khi đặt scale đích. Trong các phép chia mà kết quả thập phân vô hạn, ví dụ `1 / 3`, chương trình chỉ rõ scale và mode ngay tại phép chia. Không thể yêu cầu `BigDecimal` biểu diễn chính xác một dãy thập phân vô hạn trong một giá trị có số chữ số hữu hạn.

Phần `MathContext(3, ...)` cho thấy độ chính xác theo **chữ số có nghĩa** khác với làm tròn theo **số chữ số sau dấu**; việc chọn sai công cụ cũng có thể làm số liệu khác đáng kể.

## 6. Thiết kế chính sách làm tròn có thể kiểm soát và kiểm thử

Nếu hệ thống có nhiều nghiệp vụ, việc viết `setScale(2, RoundingMode.HALF_UP)` rải rác trong controller, service, mapper và đoạn xuất báo cáo rất khó bảo đảm nhất quán. Nên mô hình hóa một chính sách có thể được viện dẫn ở đúng **ranh giới nghiệp vụ**.

Một hợp đồng làm tròn rõ nghĩa thường trả lời các câu hỏi:

1. **Đại lượng là gì?** Tiền, khối lượng, số lượng, tỷ lệ, lãi suất, điểm thưởng, số đo hay chỉ số thống kê?
2. **Đơn vị và đơn vị nhỏ nhất là gì?** Ví dụ đơn vị tiền tệ, phần nghìn, gram, microsecond. Đừng mặc định “mọi đơn vị tiền đều có 2 chữ số thập phân”.
3. **Giữ bao nhiêu chữ số?** Chữ số sau dấu (scale), chữ số có nghĩa (precision), hoặc làm tròn đến bước cụ thể như 0,05.
4. **Mode làm tròn nào?** `HALF_UP`, `HALF_EVEN`, `FLOOR`, quy tắc riêng cho số âm, hay từ chối mọi giá trị cần làm tròn?
5. **Thực hiện ở bước nào?** Khi nhận dữ liệu, nhân đơn giá, tính thuế, chia phân bổ, kết toán từng dòng, tính tổng hay khi hiển thị?
6. **Có thể làm tròn nhiều lần không?** Làm tròn trước rồi dùng kết quả đó ở bước sau, hay giữ bản tính toán đầy đủ đến một ranh giới duy nhất?
7. **Dư số xử lý thế nào?** Phân bổ phần chênh lệch cho một dòng, tài khoản điều chỉnh, bên nhận đầu tiên, hay giữ thành khoản dư có truy vết?
8. **Ai/điều gì quyết định?** Hợp đồng với khách hàng, quy định pháp lý hiện hành, tài liệu nghiệp vụ đã được duyệt hay tiêu chuẩn nội bộ?
9. **Chính sách áp dụng theo phiên bản/thời gian nào?** Phải có cách giải thích lại số liệu lịch sử khi quy tắc về sau thay đổi.
10. **Kết quả nào cần được lưu?** Giá trị gốc, các thành phần đã lượng hóa, tổng đã làm tròn và dữ liệu để đối soát.

Không phải ứng dụng nào cũng cần mười trường trong một đối tượng Java. Nhưng các quyết định có ảnh hưởng đến kết quả **phải được xác định** thay vì ngầm dựa vào kiểu dữ liệu hoặc hành vi mặc định.

### Ví dụ mô tả bằng lời trước khi viết mã

> Giá thành của từng dòng được tính từ số lượng × đơn giá trong `BigDecimal`. Sau phép nhân, áp dụng scale 2 và `HALF_UP` cho **từng dòng**. Tổng tiền dòng bằng tổng các kết quả đã làm tròn. Thuế (nếu có) được tính và làm tròn theo chính sách riêng được hệ thống công bố. Kết quả cuối được lưu kèm đơn vị tiền, thời điểm áp dụng chính sách và mã phiên bản.

Đây chỉ là **một chính sách minh họa**, không phải quy tắc thuế/kế toán chung. Một hệ thống khác có thể yêu cầu cộng trước rồi mới làm tròn, giữ bốn chữ số nội bộ hoặc xử lý các bước chiết khấu khác nhau.

## 7. Phân biệt phép làm tròn trong tính toán và định dạng hiển thị

Định dạng như `DecimalFormat`, `NumberFormat` hay hiển thị “2 chữ số thập phân” phục vụ **giao diện người dùng**. Nó không tự tuyên bố cách kết toán chính thức.

Ví dụ, số gốc là `1.005`. Một UI có thể hiển thị `1.01` theo mode do UI đặt trong khi hệ thống kế toán giữ `1.005` chưa lượng hóa để tính một tổng sau đó. Nếu dùng chuỗi đã định dạng như **giá trị tính toán mới** mà không có quy tắc rõ ràng, bạn đã chuyển lựa chọn trình bày thành lựa chọn nghiệp vụ.

Cần tránh hai nhầm lẫn:

- **Hiển thị 2 chữ số** khác **lưu chính thức giá trị đã làm tròn đến 2 chữ số**.
- **Scale hiện tại là 2** khác **đã làm tròn đúng thời điểm và đúng mode được nghiệp vụ yêu cầu**.

Không nên tự động gọi `setScale(2, ...)` trong mọi mapper/serializer chỉ vì UI đang hiển thị hai chữ số. Khi dữ liệu đi qua API, cần nêu rõ trường nào là giá trị tính toán đầy đủ, trường nào là số đã được lượng hóa, trường nào chỉ phục vụ trình bày.

## 8. Lưu trữ và ranh giới giữa các hệ thống

### BigDecimal, double và dữ liệu đầu vào

Khi nghiệp vụ cần phép tính thập phân có thể kiểm soát, nên ưu tiên nguồn decimal rõ ràng:

```java
BigDecimal fromString = new BigDecimal("0.1");
BigDecimal fromDoubleValue = BigDecimal.valueOf(0.1);
BigDecimal binaryExact = new BigDecimal(0.1);
```

`new BigDecimal("0.1")` biểu diễn đúng giá trị thập phân 0,1. `BigDecimal.valueOf(0.1)` dùng biểu diễn thập phân từ `Double.toString(0.1)` nên thường phù hợp hơn constructor từ `double` khi đầu vào bắt buộc là `double`; còn `new BigDecimal(0.1)` biểu diễn chính xác giá trị nhị phân gần 0,1 đang nằm trong `double`, có thể tạo dãy chữ số dài ngoài mong đợi.

Dùng `BigDecimal` không tự giải quyết **nghiệp vụ**. Kể cả khi không có lỗi biểu diễn nhị phân, việc dùng sai mode hoặc làm tròn sai bước vẫn cho kết quả sai.

### Cơ sở dữ liệu và API

Một cột `DECIMAL(18, 2)` có thể bảo toàn hai vị trí thập phân trong phạm vi hỗ trợ, nhưng nó không ghi lại **lý do** dữ liệu có scale 2. Khi ghi một giá trị có nhiều chữ số hơn, các hệ CSDL/driver có thể có quy tắc làm tròn, chuyển đổi hoặc từ chối khác nhau. Phải kiểm tra hợp đồng của hệ thống đang dùng, đặc biệt tại ranh giới đọc/ghi và di trú dữ liệu.

Thiết kế nên tránh để việc ghi dữ liệu tình cờ trở thành “lần làm tròn nghiệp vụ” mà không có xác nhận. Hãy quyết định chính sách trước khi lưu, rồi kiểm thử việc ghi/đọc không vô tình thay đổi giá trị đã được chấp thuận.

Ví dụ các thông tin có thể hữu ích để lưu hay suy ra từ chứng từ: đơn vị, giá trị gốc, giá trị tính toán, giá trị đã lượng hóa, mã quy tắc và phiên bản áp dụng. Mức độ lưu trữ phụ thuộc yêu cầu audit/đối soát; không có một schema mặc định đúng cho mọi hệ thống.

### Quy tắc không nhất thiết “2 chữ số thập phân”

Có miền cần số nguyên, có miền dùng ba chữ số, có nơi phải làm tròn theo bước như 0,05 hay 5 đơn vị. Với yêu cầu “bội số gần nhất của một bước”, chỉ đặt `scale` là chưa đủ; còn phải làm phép lượng hóa theo **bước**.

Ví dụ công thức toán học cho giá trị `v`, bước dương `s` và mode `m`:

```text
rounded = round(v / s, mode) × s
```

Trong Java, với `BigDecimal`, cần sử dụng một phép chia có quy tắc làm tròn cụ thể, thường là thương nguyên được làm tròn về scale 0:

```java
static BigDecimal roundToStep(
        BigDecimal value, BigDecimal step, RoundingMode mode) {
    if (step.signum() <= 0) {
        throw new IllegalArgumentException("step must be positive");
    }
    BigDecimal count = value.divide(step, 0, mode);
    return count.multiply(step);
}
```

Hàm này là **đoạn minh họa** đặt trong một lớp cùng các import thích hợp. Với `value = 1.03`, `step = 0.05` và `HALF_UP`, kết quả là `1.05`; một yêu cầu “scale 2” thuần túy lại giữ `1.03`. Do đó ngay cả đơn vị làm tròn cũng là quy tắc nghiệp vụ cần nêu.

## 9. Kiểm thử quy tắc nghiệp vụ thay vì chỉ kiểm thử kiểu

Các bài test nên dựa vào **đầu ra được duyệt của chính sách** và có ví dụ ranh giới:

| Tình huống | Dữ liệu thử | Câu hỏi cần kiểm chứng |
|---|---|---|
| Điểm giữa | `1.005` với scale 2 | `HALF_UP` hay `HALF_EVEN`? |
| Điểm giữa âm | `-1.005` với scale 2 | Hướng xử lý giá trị âm đúng chưa? |
| Không ở điểm giữa | `1.004`, `1.006` | Làm tròn theo lân cận đúng chưa? |
| Tổng nhiều dòng | `0.335` lặp 3 lần | Làm tròn từng dòng hay toàn bộ? |
| Thuế và chiết khấu | Số tiền × tỷ lệ | Đúng thứ tự tính, cơ sở tính và điểm làm tròn? |
| Chia phân bổ | Chia 1 đơn vị cho 3 đối tượng | Phần dư có được xử lý/đối soát rõ không? |
| Không cho phép mất thông tin | `3.1415` → scale 2 | Có từ chối như yêu cầu không? |
| Ranh giới hệ thống | JSON/DB/UI | Giá trị chính thức có được bảo toàn? |
| Đổi phiên bản quy tắc | Giao dịch trước/sau mốc áp dụng | Có tái hiện đúng kết quả lịch sử không? |

Trong kiểm thử `BigDecimal`, cần chú ý mục tiêu so sánh:

- `assertEquals(new BigDecimal("1.00"), actual)` kiểm tra bằng nhau theo `BigDecimal.equals()`, gồm cả **scale**. Phù hợp khi hợp đồng đòi kết quả có scale 2.
- `expected.compareTo(actual) == 0` chỉ so sánh **giá trị số học**, bỏ qua khác biệt scale. Phù hợp khi hợp đồng không yêu cầu dạng biểu diễn.

Không thay mọi so sánh bằng `compareTo()==0` hoặc mọi so sánh bằng `equals()` mà không xét xem scale có phải một phần của hợp đồng kết quả hay không.

## 10. Những lỗi thiết kế dễ gặp

1. **“Là BigDecimal nên đã xử lý làm tròn đúng.”** `BigDecimal` chỉ cho công cụ; mode, scale, thời điểm làm tròn và thứ tự nghiệp vụ vẫn phải khai báo.
2. **“Cột database scale 2, vậy chính sách là HALF_UP hai chữ số.”** Kiểu cột không tự xác lập mode, đơn vị hay thời điểm nghiệp vụ.
3. **“Mọi số tiền luôn có hai chữ số.”** Không đúng với mọi tiền tệ, đơn vị và quy tắc tính; ngay trong cùng miền cũng có thể cần độ chính xác tính toán lớn hơn độ chính xác hiển thị.
4. **Làm tròn mỗi khi đi qua một tầng ứng dụng.** Điều này gây làm tròn lặp (double rounding), mất thông tin và lệch đối soát. Chỉ làm tròn tại ranh giới đã được xác định.
5. **Dùng `MathContext(2)` để yêu cầu “hai chữ số thập phân”.** `MathContext` giới hạn chữ số có nghĩa; `setScale(2, mode)` điều chỉnh scale.
6. **Dùng `RoundingMode.HALF_EVEN` vì nghe giống “quy tắc ngân hàng”.** Tên gọi không đủ để chọn chính sách. Cần yêu cầu chính thức.
7. **Không kiểm thử số âm, giá trị sát điểm giữa và phần dư phân bổ.** Những trường hợp này dễ làm lộ sai hướng làm tròn hoặc sai thứ tự tính.
8. **Tự chuyển sang `double` ở giữa phép tính decimal.** Có thể đưa vào sai số nhị phân mà không ai dự định.
9. **Đồng nhất định dạng UI với kết quả đã kết toán.** Chuỗi hiển thị có thể không phải giá trị cần đưa trở lại công thức.
10. **Không lưu căn cứ hoặc phiên bản quy tắc.** Khi chính sách thay đổi, khó giải thích vì sao hai giao dịch có số gốc giống nhau nhưng kết quả khác nhau.

## 11. Tự kiểm tra

**Câu 1.** Chỉ biết một trường có kiểu `BigDecimal`, có suy ra được nó phải dùng `HALF_UP` hay `HALF_EVEN` không?  
**Đáp án:** Không. Kiểu dữ liệu không quyết định quy tắc nghiệp vụ.

**Câu 2.** Hệ thống dùng `DECIMAL(18, 2)`. Có đủ thông tin để chọn làm tròn từng dòng hay làm tròn tổng không?  
**Đáp án:** Không. Scale lưu trữ không chỉ định thời điểm và phạm vi làm tròn.

**Câu 3.** Với ba giá trị `0.335` và `HALF_UP` scale 2, vì sao có thể ra `1.02` hoặc `1.01`?  
**Đáp án:** Làm tròn từng dòng rồi cộng cho `1.02`; cộng chính xác trước rồi làm tròn một lần cho `1.01`.

**Câu 4.** `new BigDecimal("1.005").setScale(2, HALF_EVEN)` trả gì?  
**Đáp án:** `1.00`, bởi tại điểm giữa lựa chọn chữ số giữ lại chẵn. Với `HALF_UP` kết quả là `1.01`.

**Câu 5.** Cần làm tròn về bội số của `0.05`. Chỉ `setScale(2)` có đủ không?  
**Đáp án:** Không. Scale 2 chỉ bảo đảm hai vị trí thập phân; cần quy tắc lượng hóa theo bước 0,05.

**Câu 6.** Nếu ứng dụng cho phép hiển thị hai chữ số nhưng giữ dữ liệu nội bộ chưa lượng hóa cho lần tính tiếp theo, nên dùng chuỗi UI làm dữ liệu tính tiếp không?  
**Đáp án:** Không, trừ khi hợp đồng nghiệp vụ có chủ ý chọn số đã lượng hóa. Phải tách giá trị tính toán, kết quả kết toán và định dạng trình bày.

**Câu 7.** Khi nào `RoundingMode.UNNECESSARY` hữu ích?  
**Đáp án:** Khi muốn **phát hiện và từ chối** trường hợp chuyển scale hoặc phép tính sẽ phải làm tròn mất thông tin.

**Ghi nhớ:** Kiểu dữ liệu trả lời *giá trị được biểu diễn ra sao*; quy tắc nghiệp vụ trả lời *làm tròn thứ gì, ở bước nào, theo mode nào và đến đơn vị nào*. Hai quyết định phải được thiết kế, công bố và kiểm thử **riêng nhưng nhất quán**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-data-design-010">Giới hạn tài nguyên cho đầu vào BigInteger hoặc BigDecimal rất lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tài nguyên cho đầu vào BigInteger hoặc BigDecimal rất lớn

## 1. Vì sao số “chính xác, không giới hạn” vẫn cần giới hạn?

`java.math.BigInteger` và `java.math.BigDecimal` khắc phục giới hạn miền giá trị hoặc độ chính xác của các kiểu số nguyên/số thực nguyên thủy trong nhiều trường hợp. Tuy vậy, **độ lớn tùy ý** (*arbitrary precision*) không có nghĩa bộ nhớ, CPU, thời gian xử lý hoặc độ dài đầu ra đều vô hạn.

Khi nhận chuỗi số từ người dùng, API, file hoặc message queue, ứng dụng có thể đối mặt với ba loại đầu vào:

1. **Dãy chữ số rất dài**: ví dụ một số nguyên chứa hàng triệu chữ số. Chỉ riêng việc nhận, lưu, kiểm tra và phân tích chuỗi đã tốn tài nguyên; tạo `BigInteger` còn phải xây dựng biểu diễn số nguyên lớn.
2. **Chuỗi ngắn nhưng số mũ rất lớn**: ví dụ `"1E+1000000000"`. Số ký tự đầu vào rất ít nhưng giá trị `BigDecimal` có scale âm rất lớn. Nếu cố xuất dạng thông thường bằng `toPlainString()` có thể phải tạo một chuỗi khổng lồ.
3. **Giá trị đầu vào có vẻ vừa phải nhưng phép toán phình to**: ví dụ dịch trái một `BigInteger` hàng triệu bit hoặc lũy thừa với số mũ rất lớn. Kết quả trung gian có thể lớn hơn hẳn đầu vào.

Hậu quả có thể gồm sử dụng quá nhiều RAM, áp lực GC, CPU quá tải, độ trễ lớn, lỗi `OutOfMemoryError`, quá thời gian xử lý, cạn quota của dịch vụ hoặc từ chối dịch vụ (*denial of service*). Không nên giả định constructor sẽ tự áp dụng hạn mức phù hợp với nghiệp vụ.

**Mục tiêu thiết kế:** đặt ngân sách tài nguyên từ trước khi parse, trước phép toán có khả năng mở rộng kích thước và trước khi chuyển số thành chuỗi đầu ra. Mọi giới hạn cần được xác định theo **ngữ cảnh nghiệp vụ**, không lấy các hằng số ví dụ trong bài làm giá trị mặc định cho sản phẩm.

## 2. Hiểu kích thước dữ liệu và chi phí

### BigInteger: dãy chữ số và số bit

`BigInteger` biểu diễn số nguyên độ lớn tùy ý. Chuỗi thập phân dài `d` chữ số thường cần khoảng \(d \times \log_2(10)\), tức khoảng \(3.322d\) bit để biểu diễn độ lớn (ước lượng, không bao gồm overhead object, mảng và bộ nhớ tạm).

Ví dụ một triệu chữ số thập phân tương đương khoảng 3,32 triệu bit, tức hàng trăm KiB chỉ riêng dữ liệu bit ở mức lý thuyết. Nhưng quy trình thực tế còn phải giữ chuỗi đầu vào, mảng, đối tượng, cấu trúc tạm và tài nguyên của thuật toán parse/tính toán. **Không dùng ước lượng số bit làm cam kết về RAM thực tế.**

`BigInteger.bitLength()` cho biết độ dài bit tối thiểu theo hợp đồng API (cách xử lý số âm có quy tắc riêng); `toString().length()` là độ dài biểu diễn thập phân, có thể đòi hỏi chuyển đổi đáng kể. Khi đầu vào chưa được tin cậy, **đếm số chữ số trên chuỗi trước**, rồi mới dựng `BigInteger`.

Các phép toán có thể làm kết quả tăng rất nhanh:

- `a.multiply(b)`: số bit kết quả xấp xỉ tổng số bit của hai toán hạng.
- `a.shiftLeft(n)`: số bit có thể tăng khoảng `n` với `n` rất lớn.
- `a.pow(n)`: số chữ số/bit kết quả có thể tăng theo `n`, ngay cả khi `a` ngắn.
- `modPow`, chia, `gcd`, kiểm tra nguyên tố: chi phí thời gian phụ thuộc kích thước toán hạng và thuật toán; chỉ giới hạn chiều dài đầu vào là chưa đủ.

### BigDecimal: precision, scale và exponent

`BigDecimal` gồm một giá trị nguyên không chia tỷ lệ (*unscaled value*) và một số `scale` kiểu `int`:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

`precision()` là số chữ số của unscaled value (trường hợp số 0 có precision 1). `scale()` có thể âm, dương hoặc bằng 0:

| Đầu vào | Precision | Scale | Ý nghĩa |
|---|---:|---:|---|
| `"123.4500"` | 7 | 4 | Giá trị 123.4500, giữ 4 chữ số sau dấu thập phân |
| `"1E+6"` | 1 | -6 | Giá trị một triệu |
| `"1E-7"` | 1 | 7 | Giá trị một phần mười triệu |
| `"0.00"` | 1 | 2 | Giá trị 0 với scale 2 |

**Precision nhỏ không đồng nghĩa đầu ra nhỏ.** `new BigDecimal("1E+1000000")` có unscaled value `1` và scale `-1000000` nhưng `toPlainString()` phải trình bày số 1 và một triệu số 0. Trong khi đó `toString()` có thể giữ dạng khoa học rất ngắn. Vì vậy cần chính sách riêng cho **scale/exponent** và **độ dài chuỗi kết quả**.

Ngay cả nếu chuỗi exponent vượt miền mà Java cho phép và constructor ném `NumberFormatException`, ứng dụng vẫn không nên giao việc phát hiện cho constructor sau khi đã nhận một payload không có hạn mức. Giới hạn sớm giúp kết quả kiểm soát được và giảm rủi ro trước khi tạo đối tượng lớn.

## 3. Lớp phòng vệ thứ nhất: giới hạn đầu vào trước parse

Một chính sách đầu vào số cần nêu rõ:

| Hạn mức | Bảo vệ điều gì? | Cách áp dụng |
|---|---|---|
| Tổng kích thước request/file/message | Bộ nhớ và I/O của toàn payload | Hạn mức ở HTTP gateway, message consumer, file reader |
| `maxTextChars` hoặc `maxBytes` | Bộ nhớ chuỗi và chi phí quét | Kiểm tra trước khi dựng số, giới hạn byte từ lớp nhận dữ liệu |
| Số chữ số mantissa/số nguyên | Chi phí parse và độ lớn unscaled value | Đếm ký tự `0..9` trước constructor |
| Số chữ số exponent | Không nhận exponent dài bất thường | Kiểm tra phần sau `E`/`e` |
| Độ lớn tuyệt đối của exponent | Tránh scale cực đoan và chuỗi đầu ra phình to | Parse exponent theo số nguyên có kiểm tra tràn |
| Độ lớn tuyệt đối của scale | Giới hạn dạng thập phân/scientific và các phép toán | Xác minh `scale()` sau parse, có ngân sách parse từ trước |
| Độ dài chuỗi cần xuất | Tránh `toPlainString()` phát sinh chuỗi quá dài | Ước tính trước khi gọi hàm tạo chuỗi |
| Ngân sách bit và phép toán | Kết quả trung gian lớn, CPU cao | Dự báo độ lớn và từ chối phép toán vượt quota |

**Không chỉ dùng `text.length()`.** Chuỗi `"1E+1000000"` chỉ có ít ký tự nhưng việc xuất số ở dạng thập phân có thể rất lớn. Ngược lại, chuỗi có hàng triệu số 0 cần chặn ngay cả khi giá trị toán học bằng 0.

Đối với dữ liệu không tin cậy, ưu tiên một **ngữ pháp đơn giản đã thống nhất**: chỉ ký tự ASCII, dấu `+`/`-` tùy chính sách, đúng một dấu `.` nếu là số thập phân, phần exponent tùy chọn với `E`/`e`, và cấm khoảng trắng/ký tự lạ. Điều này cũng tránh nhầm dấu phân cách theo locale: parser `BigDecimal(String)` dùng cú pháp số của Java, không phải cú pháp tùy tiện của giao diện vùng miền.

Nếu dữ liệu đến trong JSON, cân nhắc nhận giá trị thập phân chính xác dưới dạng **chuỗi** khi cần bảo toàn văn bản/precision; đặt giới hạn body và giới hạn chiều dài giá trị **trước** khi các tầng chuyển đổi tự dựng `BigInteger`/`BigDecimal`. Tránh parse một số tùy ý thành `double` trước rồi mới chuyển sang `BigDecimal` vì có thể làm mất độ chính xác.

## 4. Chương trình Java 17+ kiểm tra trước khi dựng số và trước khi xuất

Đây là ví dụ có **chính sách minh họa**, không phải bộ thông số khuyến nghị cho mọi hệ thống: chuỗi tối đa 80 ký tự UTF-16, tối đa 20 chữ số trong phần số, exponent tối đa 3 ký tự và độ lớn không quá 30, `|scale| <= 30`, dạng plain tối đa 32 ký tự, kết quả dịch trái tối đa 128 bit. Trong ví dụ, toàn bộ dữ liệu đầu vào chỉ cho phép ký tự ASCII nên `String.length()` trùng số ký tự đầu vào; giới hạn byte của request vẫn cần xử lý ở lớp vận chuyển.

Lưu file **`SafeBigNumbers.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class SafeBigNumbers {
    private static final int MAX_TEXT_CHARS = 80;
    private static final int MAX_DIGITS = 20;
    private static final int MAX_EXPONENT_DIGITS = 3;
    private static final int MAX_ABS_EXPONENT = 30;
    private static final int MAX_ABS_SCALE = 30;
    private static final int MAX_PLAIN_CHARS = 32;
    private static final int MAX_RESULT_BITS = 128;

    private static boolean decimalDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static BigInteger parseInteger(String text) {
        if (text == null || text.isEmpty()
                || text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("invalid integer length");
        }

        int i = (text.charAt(0) == '-' || text.charAt(0) == '+')
                ? 1 : 0;
        int digits = 0;

        for (; i < text.length(); i++) {
            if (!decimalDigit(text.charAt(i))) {
                throw new IllegalArgumentException("integer has non-digit");
            }
            if (++digits > MAX_DIGITS) {
                throw new IllegalArgumentException("too many integer digits");
            }
        }

        if (digits == 0) {
            throw new IllegalArgumentException("missing digits");
        }
        return new BigInteger(text);
    }

    public static BigDecimal parseDecimal(String text) {
        if (text == null || text.isEmpty()
                || text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("invalid decimal length");
        }

        int i = (text.charAt(0) == '-' || text.charAt(0) == '+')
                ? 1 : 0;
        int digits = 0;
        boolean pointSeen = false;

        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == 'e' || c == 'E') {
                break;
            }

            if (c == '.') {
                if (pointSeen) {
                    throw new IllegalArgumentException("two decimal points");
                }
                pointSeen = true;
            } else if (decimalDigit(c)) {
                if (++digits > MAX_DIGITS) {
                    throw new IllegalArgumentException("too many decimal digits");
                }
            } else {
                throw new IllegalArgumentException(
                        "invalid decimal character");
            }
            i++;
        }

        if (digits == 0) {
            throw new IllegalArgumentException(
                    "missing mantissa digits");
        }

        if (i < text.length()) {
            i++; // Bỏ qua E/e.
            if (i < text.length()
                    && (text.charAt(i) == '+'
                            || text.charAt(i) == '-')) {
                i++;
            }

            int exponentDigits = 0;
            int magnitude = 0;

            for (; i < text.length(); i++) {
                char c = text.charAt(i);
                if (!decimalDigit(c)) {
                    throw new IllegalArgumentException(
                            "invalid exponent character");
                }
                if (++exponentDigits > MAX_EXPONENT_DIGITS) {
                    throw new IllegalArgumentException(
                            "exponent text too long");
                }

                int digit = c - '0';
                if (magnitude > (MAX_ABS_EXPONENT - digit) / 10) {
                    throw new IllegalArgumentException(
                            "exponent too large");
                }
                magnitude = magnitude * 10 + digit;
            }

            if (exponentDigits == 0) {
                throw new IllegalArgumentException(
                        "missing exponent digits");
            }
        }

        BigDecimal value = new BigDecimal(text);
        // Cast sang long trước khi dùng abs để an toàn với int MIN_VALUE.
        if (Math.abs((long) value.scale()) > MAX_ABS_SCALE) {
            throw new IllegalArgumentException("scale too large");
        }
        if (plainLength(value) > MAX_PLAIN_CHARS) {
            throw new IllegalArgumentException(
                    "plain representation too long");
        }
        return value;
    }

    // Độ dài số ký tự toPlainString() dự kiến, không tạo chuỗi lớn.
    private static long plainLength(BigDecimal n) {
        long p = n.precision();
        long scale = n.scale();
        long length;

        if (n.signum() == 0 && scale <= 0) {
            length = 1;
        } else if (scale <= 0) {
            length = p - scale;
        } else if (p > scale) {
            length = p + 1;   // Bao gồm dấu thập phân.
        } else {
            length = scale + 2; // "0." và các chữ số thập phân.
        }

        return length + (n.signum() < 0 ? 1 : 0);
    }

    public static BigInteger shiftLeftBounded(
            BigInteger value, int bits) {
        if (bits < 0
                || (long) value.bitLength() + bits > MAX_RESULT_BITS) {
            throw new IllegalArgumentException(
                    "shift would exceed bit limit");
        }
        return value.shiftLeft(bits);
    }

    private static void expectRejected(
            String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": NOT REJECTED");
        } catch (IllegalArgumentException ok) {
            System.out.println(label + ": rejected");
        }
    }

    public static void main(String[] args) {
        BigInteger integer = parseInteger("12345678901234567890");
        BigDecimal decimal = parseDecimal("1.25E+2");

        System.out.println("integer digits: "
                + integer.toString().length());
        System.out.println("decimal plain: " + decimal.toPlainString());
        System.out.println("bounded shift: "
                + shiftLeftBounded(BigInteger.valueOf(3), 10));
        System.out.println("scientific plain length: "
                + plainLength(parseDecimal("1E+30")));

        expectRejected("too many digits",
                () -> parseInteger("123456789012345678901"));
        expectRejected("large exponent",
                () -> parseDecimal("1E+999"));
        expectRejected("large plain output",
                () -> parseDecimal("100.0E+30"));
        expectRejected("oversize shift",
                () -> shiftLeftBounded(BigInteger.ONE, 128));
    }
}
```

Biên dịch và chạy:

```bash
javac SafeBigNumbers.java
java SafeBigNumbers
```

Kết quả:

```text
integer digits: 20
decimal plain: 125
bounded shift: 3072
scientific plain length: 31
too many digits: rejected
large exponent: rejected
large plain output: rejected
oversize shift: rejected
```

### Đọc hiểu từng lớp kiểm tra

**`parseInteger`:** kiểm tra null/rỗng và tổng độ dài; chỉ cho phép một dấu ở đầu và tối đa 20 chữ số `0..9`, sau đó mới gọi `new BigInteger(text)`. Ký tự lạ hoặc dấu lặp bị từ chối. Các số 0 đầu vẫn **được đếm** vào hạn mức vì chúng vẫn làm dài đầu vào và gây chi phí parse.

**`parseDecimal`:** quét phần mantissa, đếm mọi chữ số và không chấp nhận hai dấu chấm. Với exponent, chương trình kiểm soát **cả độ dài lẫn giá trị** trước khi dựng `BigDecimal`. Phép kiểm tra `magnitude > (MAX_ABS_EXPONENT - digit) / 10` phát hiện vượt giới hạn trước phép nhân 10/cộng chữ số, tránh tự làm tràn biến `int` trong quá trình parse exponent. Chính sách này chấp nhận các dạng như `1.25E+2` và `.5` nhưng không nhận khoảng trắng, ký tự phân nhóm hàng nghìn hoặc biểu diễn số theo locale.

**Kiểm soát scale:** sau khi tạo giá trị đã được giới hạn textual input, kiểm tra `Math.abs((long) value.scale())`. Việc chuyển sang `long` trước `abs` tránh lỗi đặc biệt với `Integer.MIN_VALUE`. Đây là kiểm tra **bổ sung sau parse**, không thay thế giới hạn textual input/exponent trước constructor.

**`plainLength`:** chỉ ước tính độ dài chuỗi thập phân thuần, không thực hiện `toPlainString()`. Công thức dùng `precision()` và `scale()`; có một trường hợp riêng cho số 0 có scale không dương và một ký tự dấu khi số âm. Trong ví dụ, `1E+30` có độ dài plain 31 nên được phép; `100.0E+30` có plain dài 33 nên bị từ chối theo ngân sách 32.

**`shiftLeftBounded`:** dùng tổng `bitLength + bits` làm **cận trên bảo thủ** trước `shiftLeft`. Với miền nhập và chính sách này, kiểm tra chặn các yêu cầu có thể vượt 128 bit; đôi khi nó cũng từ chối một kết quả thực tế có thể nhỏ hơn cận, điều có thể chấp nhận trong chính sách an toàn tài nguyên. Với phép dịch phải, mũ lũy thừa, nhân/chia hoặc modPow, cần xây dựng giới hạn riêng trước mỗi phép toán.

**`expectRejected`:** minh họa các trường hợp bị từ chối bằng `IllegalArgumentException`; trong ứng dụng thực, chuyển các lỗi kiểm tra đó thành phản hồi validation có cấu trúc và **không trả dữ liệu đầu vào quá dài vào log/error response**.

Các hằng số trong chương trình chỉ phục vụ ví dụ. Một hệ thống kế toán, tính toán khoa học hay xử lý mật mã có ngân sách và đặc trưng dữ liệu rất khác nhau. Hãy đặt giới hạn theo yêu cầu của sản phẩm và tài nguyên triển khai.

## 5. Dự đoán kích thước đầu ra trước khi chuyển chuỗi

Rất dễ bỏ sót vấn đề đầu ra. Xét:

```java
BigDecimal value = new BigDecimal("1E+1000000");

String compact = value.toString();     // Dạng khoa học, ngắn.
// String huge = value.toPlainString(); // Có thể sinh > 1 triệu ký tự.
```

`BigDecimal.toString()` chọn dạng khoa học khi phù hợp với scale/adjusted exponent. `toPlainString()` **bắt buộc không dùng E** và có thể thêm vô số chữ số 0 khi scale rất âm hoặc rất dương. `toEngineeringString()` cũng có cách trình bày khác, nhưng không nên dùng như một cơ chế giới hạn kích thước tổng quát.

Một cách dự báo độ dài plain cho phần lớn các số khác 0:

- `scale <= 0`: số chữ số thường là `precision - scale`.
- `scale > 0` và `precision > scale`: cần `precision + 1` ký tự (bao gồm dấu chấm).
- `scale > 0` và `precision <= scale`: cần `scale + 2` ký tự, gồm `0.` và phần thập phân.
- Nếu âm, cộng một ký tự cho dấu trừ. Với số 0 có scale không dương, `toPlainString()` là `"0"`.

Các phép tính ước lượng cần dùng `long` hoặc phép toán kiểm tra tràn, không cộng/trừ `int` bừa bãi khi scale có thể nằm gần giới hạn kiểu `int`. Khi đã có một hạn mức nhỏ, **so sánh chiều dài dự kiến trước khi gọi phương thức tạo chuỗi**.

Ngoài `toPlainString()`, kiểm tra đường đi dữ liệu qua JSON serialization, ghi log, sinh báo cáo, CSV, HTML, các API nhận chuỗi, hoặc trường cơ sở dữ liệu. Một giá trị dưới dạng khoa học ngắn ở tầng nhận có thể trở thành đầu ra rất lớn sau một phép chuẩn hóa định dạng.

## 6. Giới hạn tài nguyên trước phép toán

Kể cả nếu hai toán hạng qua kiểm tra đầu vào, phép toán tiếp theo vẫn có thể tạo số vượt ngân sách:

| Phép toán | Rủi ro | Giới hạn nên có |
|---|---|---|
| `BigInteger.multiply` | Kích thước kết quả xấp xỉ tổng kích thước bit của toán hạng | Ước lượng cận bit kết quả; chặn giá trị vượt ngân sách |
| `BigInteger.shiftLeft` | `n` có thể rất lớn dù toán hạng ngắn | Giới hạn cả `n` và bit kết quả dự kiến |
| `BigInteger.pow` | Số chữ số kết quả tăng theo số mũ | Giới hạn số mũ và ngân sách bit/precision |
| `BigInteger.modPow` | Tốn thời gian với modulus/số mũ lớn | Giới hạn bitLength và số phép/độ trễ |
| `BigInteger.gcd`, chia/mod | Chi phí phụ thuộc kích thước toán hạng | Giới hạn bitLength đầu vào và quota xử lý |
| `BigDecimal.multiply` | Unscaled value và scale có thể tăng | Giới hạn precision/scale dự kiến và kết quả |
| `BigDecimal.divide` | Có thể có khai triển thập phân vô hạn hoặc sinh nhiều chữ số | Chỉ định precision/scale và `RoundingMode` theo nghiệp vụ |
| `BigDecimal.pow` | Precision, exponent, scale và chi phí trung gian có thể lớn | Giới hạn số mũ, precision, scale và chi phí |
| `toPlainString` | Chuỗi đầu ra có thể lớn dù unscaled value nhỏ | Tính trước độ dài plain, chặn trước khi tạo |

Ví dụ về dự đoán:

- Với phép nhân hai số nguyên có độ dài bit `a` và `b`, bitLength kết quả không vượt quá xấp xỉ `a + b`; dùng tổng này làm cận trên bảo thủ trước khi nhân.
- Với `shiftLeft(n)`, cộng `n` vào bitLength hiện có làm cận trên đơn giản; từ chối `n` âm hoặc lớn quá giới hạn.
- Với `pow(exponent)`, sản phẩm `bitLength × exponent` có thể quá lớn. Dùng kiểu rộng hoặc phép kiểm tra tràn trước khi ước lượng, đặc biệt khi exponent do người dùng kiểm soát.

`MathContext` giới hạn **precision của kết quả làm tròn** trong các API `BigDecimal` hỗ trợ, nhưng **không phải lá chắn tổng quát chống tiêu tốn bộ nhớ/CPU**. Một thuật toán có thể vẫn cần tạo intermediate lớn hoặc xử lý toán hạng khổng lồ trước khi làm tròn. `new MathContext(20)` không biến việc parse chuỗi hàng triệu ký tự hoặc tính toán lũy thừa lớn thành an toàn tài nguyên.

Cũng cần nhớ rằng một số phép chia `BigDecimal.divide(BigDecimal)` không có scale/rounding context có thể ném `ArithmeticException` nếu thương không biểu diễn hữu hạn; điều đó là một vấn đề **khác** với việc dùng quá nhiều tài nguyên. Hãy giới hạn đầu vào trước và chỉ định cách chia thích hợp.

## 7. Ngân sách ở cấp hệ thống, không chỉ trong một hàm parse

Một validator tốt không đủ nếu các tầng trước nó đã đọc toàn bộ request khổng lồ vào bộ nhớ. Hãy phân lớp:

1. **Cổng tiếp nhận dữ liệu:** giới hạn byte của HTTP request, body JSON, upload, message và kích thước batch; tránh dựng object lớn khi payload đã vượt hạn mức.
2. **Phân tích cú pháp:** quét input trong thời gian tuyến tính, hạn chế chiều dài, số chữ số, exponent và scale trước khi dựng đối tượng lớn; không dùng regex thiếu kiểm soát trên chuỗi tùy ý.
3. **Mô hình dữ liệu:** mô tả giới hạn nghiệp vụ theo trường, ví dụ số chữ số tối đa, scale hợp lệ, miền giá trị và cách xử lý dấu/số 0.
4. **Tính toán:** ước lượng độ phình của kết quả và số vòng lặp trước phép nhân, lũy thừa, shift, chuyển đổi định dạng; giới hạn tổng số tác vụ song song và thời lượng xử lý.
5. **Đầu ra và lưu trữ:** giới hạn chuỗi xuất, số dòng/trường, độ dài log, kích thước database column; không vô tình chuyển số khoa học ngắn thành plain dài.
6. **Khả năng phục hồi:** dùng timeout, quota theo request/người dùng, giới hạn concurrency/bộ nhớ, cơ chế từ chối sớm và đo lường tác vụ vượt hạn mức.

Không nên coi `try/catch (OutOfMemoryError)` là cơ chế kiểm soát đầu vào. Khi lỗi này xảy ra, JVM có thể đã ở trạng thái thiếu tài nguyên nghiêm trọng; phòng tránh bằng giới hạn trước khi cấp phát và kiểm soát tài nguyên ở mức quy trình/hạ tầng đáng tin cậy hơn.

Nếu thực sự cần hỗ trợ tính toán số cực lớn, hãy **thiết kế kiến trúc cho workload đó**: job bất đồng bộ có quota rõ ràng, bộ nhớ/CPU giới hạn, lưu kết quả theo định dạng phù hợp, có thể hủy/giới hạn tiến trình và theo dõi tài nguyên. Không nới tất cả giới hạn trong API đồng bộ dành cho request thông thường.

## 8. Các lỗi thiết kế thường gặp

### “Đã giới hạn số chữ số nên chắc chắn an toàn”

Giới hạn mantissa 20 chữ số vẫn có thể chấp nhận `1E+1000000000` nếu không kiểm soát exponent. Giá trị có thể biểu diễn gọn nhưng `toPlainString()` cực lớn. Ngược lại, `BigInteger.ONE.shiftLeft(100000000)` có đầu vào chỉ một bit mà kết quả rất lớn. Phải kiểm tra cả **mô tả số**, **phép toán** và **cách xuất**.

### “parse xong rồi gọi precision() và bitLength() kiểm tra”

Kiểm tra sau constructor có thể hữu ích nhưng đã quá muộn để chặn tài nguyên dành cho parse một chuỗi không giới hạn. Với dữ liệu không tin cậy, luôn đặt giới hạn thô và đếm chữ số **trước** khi tạo `BigInteger`/`BigDecimal`.

### “Precision thấp thì toPlainString() ngắn”

`new BigDecimal("1E+1000000")` có precision 1 nhưng chuỗi plain dài hơn một triệu ký tự. **Precision** mô tả số chữ số của unscaled value, còn **scale** quyết định vị trí dấu chấm và số lượng số 0 cần trình bày.

### “stripTrailingZeros() làm mọi thứ nhỏ lại”

`stripTrailingZeros()` có thể giảm số chữ số của unscaled value và thay đổi scale, đôi khi tạo scale âm, khiến `toString()` dùng dạng khoa học. Nó không phải giải pháp kiểm soát kích thước đầu ra; sau đó gọi `toPlainString()` vẫn có thể tạo chuỗi dài. Không dùng phương thức này như thay thế cho giới hạn tài nguyên hoặc chính sách precision/scale.

### “Dùng BigDecimal với MathContext nhỏ là đủ”

Một `MathContext` nhỏ ràng buộc precision của kết quả ở những phép toán có sử dụng nó; không chặn chuỗi nhập khổng lồ, exponent ngoài chính sách, số lần gọi phép toán, hoặc mọi bộ nhớ tạm. Giới hạn tài nguyên phải xuất hiện **trước** các bước có thể tốn nhiều tài nguyên.

### “Dùng double để giảm chi phí mà không ảnh hưởng dữ liệu”

`double` có miền và độ chính xác hữu hạn theo nhị phân. Chuyển số tài chính hoặc dữ liệu thập phân chính xác sang `double` có thể làm mất chữ số hoặc đổi giá trị. Nếu cần giới hạn dữ liệu `BigDecimal`, hãy **từ chối các giá trị ngoài chính sách** hoặc định nghĩa phép làm tròn tường minh, thay vì âm thầm đổi kiểu.

### “Chặn lỗi rồi log nguyên đầu vào”

Khi phản hồi hoặc ghi log in nguyên chuỗi có hàng triệu chữ số, chính bước báo lỗi lại gây áp lực bộ nhớ/I/O. Log chỉ thông tin an toàn như mã lỗi, tên field, độ dài đã giới hạn và request ID; tránh phản chiếu cả payload vào thông báo.

## 9. Định nghĩa chính sách theo miền nghiệp vụ

Không có một hạn mức đúng cho mọi ứng dụng:

- **Tiền tệ:** thường cần giới hạn precision, scale, miền giá trị, phép làm tròn và định dạng theo đồng tiền hoặc hợp đồng. Giá trị âm, dấu cộng, số 0 cuối và số mũ có thể được cho phép hoặc cấm theo đặc tả API.
- **Số lượng sản phẩm / bộ đếm:** nên giới hạn miền số nguyên (và không âm nếu nghiệp vụ yêu cầu); có thể chỉ cần `long` nếu miền cho phép. `BigInteger` chỉ hữu ích khi yêu cầu thật sự vượt miền kiểu nguyên thủy.
- **Tính toán khoa học:** có thể cần exponent lớn, độ chính xác cao và dạng khoa học; nên đặt ngân sách theo độ dài unscaled value, precision, scale, số thao tác và chi phí đầu ra.
- **Số học mật mã:** `BigInteger` có thể dùng để biểu diễn số lớn, nhưng việc xử lý bí mật cần xem xét thời gian thực thi, hạn mức, API bảo mật và các thuộc tính phụ kênh; không mặc định mọi phép toán BigInteger phù hợp cho xử lý bí mật.
- **API công cộng:** thêm quota theo người dùng, giới hạn kích thước request, timeout và phản hồi validation rõ ràng, vì đầu vào có thể không đáng tin.

Ví dụ một schema có thể quy định “tối đa 30 chữ số, scale 0–4, trị tuyệt đối dưới ngưỡng X, không nhận E”; schema khác có thể cho phép `E` nhưng giới hạn exponent và chuỗi plain. Khi triển khai, cần **diễn đạt giới hạn bằng điều kiện kiểm thử được**, không chỉ ghi chú chung “số không được quá lớn”.

## 10. Checklist kiểm thử

Kiểm thử không chỉ các giá trị hợp lệ thông thường mà cả biên tài nguyên:

- Chuỗi rỗng, chỉ dấu `+`/`-`, ký tự lạ, khoảng trắng, nhiều dấu chấm, exponent thiếu chữ số.
- Giá trị đúng bằng hạn mức và vượt hạn mức một ký tự/chữ số.
- Chuỗi dài toàn số 0, giá trị bằng 0 nhưng mantissa vẫn quá dài.
- Exponent có nhiều chữ số, mang dấu âm/dương, vượt hạn mức hoặc có thể gây tràn nếu cộng dồn sai.
- Scale dương rất lớn (`1E-1000000`) và scale âm rất lớn (`1E+1000000`).
- Đầu vào ngắn dẫn tới phép `pow` hoặc `shiftLeft` mở rộng ngoài ngân sách.
- `toString()` ngắn nhưng `toPlainString()` rất dài.
- Nhiều request/tác vụ song song cùng sử dụng ngân sách gần ngưỡng.
- Đầu ra báo lỗi/log không vô tình sao chép lại toàn bộ số lớn.

**Tự kiểm tra 1:** Vì sao giới hạn “tối đa 50 ký tự đầu vào” chưa đủ để bảo vệ `toPlainString()`? Vì dạng khoa học như `1E+1000000` rất ngắn nhưng tạo hàng triệu ký tự ở dạng thập phân đầy đủ.

**Tự kiểm tra 2:** Vì sao kiểm tra `BigInteger.bitLength()` sau `new BigInteger(text)` không thay thế việc giới hạn chuỗi? Vì đã phải nhận và parse chuỗi, có thể gây chi phí lớn trước khi kiểm tra.

**Tự kiểm tra 3:** `new BigDecimal("1E+30")` có precision 1; liệu có thể kết luận phép nhân và đầu ra đều rẻ? **Không.** Precision không chứa đủ thông tin về scale, định dạng hoặc chi phí của các phép toán tiếp theo.

**Ghi nhớ:** `BigInteger` và `BigDecimal` cho phép biểu diễn độ chính xác lớn, nhưng an toàn tài nguyên là trách nhiệm của ứng dụng. Hãy giới hạn **đầu vào trước parse**, **scale/exponent**, **độ lớn kết quả trước phép toán**, **độ dài chuỗi trước xuất** và **quota thực thi cấp hệ thống**.


</details>

- [Quay lại đầu trang](#back-to-top)
