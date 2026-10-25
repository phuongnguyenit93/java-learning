<a id="back-to-top"></a>

# Thực hành và chất lượng

## Menu
- [Phân tích bài toán thành dữ liệu vào, xử lý và kết quả](#practice-and-quality-001)
- [Viết mã giả và bảng theo dõi trước khi lập trình](#practice-and-quality-002)
- [Đặt tên rõ nghĩa, thụt lề và tránh số không giải thích](#practice-and-quality-003)
- [Chú thích lý do và viết tài liệu cho phương thức khi cần](#practice-and-quality-004)
- [Gỡ lỗi bằng breakpoint, chạy từng bước và xem giá trị biến](#practice-and-quality-005)
- [Kiểm tra trường hợp rỗng, biên, số âm, dữ liệu sai và giá trị lớn](#practice-and-quality-006)
- [Thực hành chương trình tính tiền với điều kiện và vòng lặp](#practice-and-quality-007)
- [Thực hành thống kê mảng và tách thành phương thức](#practice-and-quality-008)
- [Thực hành menu console với kiểm tra đầu vào](#practice-and-quality-009)
- [Tổng hợp lỗi thường gặp và tiêu chí tự đánh giá nền tảng](#practice-and-quality-010)

## <a id="practice-and-quality-001">Phân tích bài toán thành dữ liệu vào, xử lý và kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Phân tích bài toán thành dữ liệu vào, xử lý và kết quả (Java 17+)

## 1. Mục tiêu: hiểu bài toán trước khi viết mã

Khi nhận một yêu cầu bằng ngôn ngữ tự nhiên, đừng bắt đầu bằng việc viết ngay `Scanner`, `if` hay vòng lặp. Trước hết hãy trả lời ba câu hỏi theo mô hình **Input – Process – Output (IPO)**:

1. **Input (dữ liệu vào):** Chương trình cần biết những giá trị nào, đến từ đâu, có kiểu dữ liệu, đơn vị, giới hạn và điều kiện hợp lệ gì?
2. **Process (xử lý):** Phải kiểm tra, biến đổi, tính toán và áp dụng quy tắc nghiệp vụ nào, theo thứ tự nào?
3. **Output (kết quả):** Chương trình phải trả lại hoặc hiển thị những gì, dưới dạng nào, làm sao biết kết quả đúng và phải phản hồi thế nào khi dữ liệu vào sai?

Phân tích IPO biến một đề bài chung chung thành một **hợp đồng có thể triển khai và kiểm thử**. Với Java, ba phần thường tương ứng với bước đọc tham số/input, một hoặc nhiều phương thức xử lý, rồi bước hiển thị hoặc trả kết quả.

```text
Yêu cầu người dùng
      |
      v
Input: dữ liệu + điều kiện hợp lệ
      |
      v
Process: kiểm tra -> tính toán -> quyết định
      |
      v
Output: kết quả thành công HOẶC thông báo lỗi
      |
      v
Đối chiếu với các trường hợp thử đã xác định
```

Mô hình này phù hợp với bài tập console, hàm tính toán, xử lý file, API và các chương trình lớn hơn. **IPO là cách phân tích**, không phải cú pháp hoặc một kiểu dữ liệu có sẵn trong Java.

## 2. Phân tích một đề bài cụ thể

**Đề bài mẫu:** *Nhập điểm của ba môn học. Mỗi điểm nằm trong khoảng từ 0 đến 10. Tính điểm trung bình cộng. Kết luận PASS nếu điểm trung bình ít nhất 5,0 **và** cả ba điểm đều từ 4,0 trở lên; ngược lại là FAIL. Nếu dữ liệu không hợp lệ, thông báo lỗi thay vì tính kết quả.*

Từ đề này, lập bảng:

| Thành phần | Phân tích | Quyết định khi cài đặt |
| --- | --- | --- |
| Input | Ba điểm số `score1`, `score2`, `score3` | Dùng `double` để chấp nhận điểm lẻ |
| Miền hợp lệ | Mỗi điểm hữu hạn và `0 <= score <= 10` | Kiểm tra trước khi tính |
| Process – công thức | `average = (score1 + score2 + score3) / 3.0` | Chia cho `3.0` để biểu đạt phép chia số thực |
| Process – điều kiện | `average >= 5.0` **và** ba điểm `>= 4.0` | Dùng `&&` giữa bốn điều kiện |
| Output thành công | Trung bình với hai chữ số thập phân và `PASS`/`FAIL` | `printf(Locale.ROOT, "%.2f")` và thông báo trạng thái |
| Output lỗi | Không có kết luận nếu thiếu/sai điểm | Thông báo cụ thể ở trường đầu tiên không hợp lệ |
| Quy tắc gần ngưỡng | So sánh điểm trung bình **chưa làm tròn** | Chỉ định dạng hai chữ số khi hiển thị |

Tại sao kiểm tra `Double.isFinite()`? `Double.parseDouble("NaN")` và `Double.parseDouble("Infinity")` có thể tạo giá trị `double` đặc biệt không phải điểm hợp lệ. Chỉ kiểm tra bằng các phép so sánh với `0` và `10` là chưa đủ đối với `NaN`, vì các so sánh thứ tự với `NaN` đều cho `false`.

### Làm rõ yêu cầu còn mơ hồ

Trong dự án thực tế, nên làm rõ những trường hợp đề bài chưa quy định:

- Cho phép điểm âm, điểm trên 10, giá trị rỗng, giá trị `NaN` không? Ví dụ này quy định **không**.
- Có giới hạn số chữ số thập phân đầu vào không? Ví dụ **không** giới hạn riêng, miễn số hợp lệ trong miền.
- Khi điểm trung bình là `4.999...`, nên so sánh trước hay sau làm tròn? Ví dụ so sánh **giá trị tính được**, làm tròn chỉ để in.
- Khi nhập sai một trường, yêu cầu nhập lại hay báo lỗi và dừng? Ví dụ **báo lỗi và kết thúc**.
- Nhập từ bàn phím, file, tham số `main`, hay API? Ví dụ console đọc **ba dòng**, lần lượt là ba điểm.

Những lựa chọn này thuộc **hợp đồng của bài mẫu**, không phải quy tắc mặc định của tất cả ứng dụng chấm điểm.

## 3. Tách dữ liệu vào khỏi phần xử lý

Thói quen tốt là phân biệt rõ **cách nhận dữ liệu** với **quy tắc tính toán**. Chẳng hạn cùng một phép tính trung bình có thể được gọi từ chương trình console, bài kiểm thử hay một API.

Phân ranh giới:

- **Tầng Input:** đọc ba chuỗi; kiểm tra có dữ liệu, parse số; từ chối giá trị không hợp lệ. `Scanner` và `Double.parseDouble` nằm ở đây.
- **Tầng Process:** chỉ nhận ba số đã hợp lệ; tính trung bình và quyết định PASS/FAIL. Tầng này không cần biết người dùng nhập bằng cách nào.
- **Tầng Output:** nhận kết quả xử lý rồi in ra theo định dạng đã thống nhất. Không tính lại công thức nghiệp vụ ở câu lệnh `println`.

Cách tách này giúp một lỗi không bị trộn lẫn: nếu `abc` không được chấp nhận thì xem Input; nếu điểm 4, 5, 6 bị đánh FAIL thì xem Process; nếu in `5,00` thay vì `5.00` thì xem Output/locale.

## 4. Chuyển IPO thành thuật toán

Trước khi code, có thể viết **giả mã** độc lập với Java:

```text
Đọc score1, score2, score3 (mỗi điểm từ một dòng).
Với từng điểm:
    Nếu không có dòng -> báo thiếu dữ liệu và dừng.
    Nếu không thể parse thành số thực -> báo sai định dạng và dừng.
    Nếu không hữu hạn hoặc ngoài [0, 10] -> báo ngoài phạm vi và dừng.

average = (score1 + score2 + score3) / 3.0
passed = average >= 5.0
         AND score1 >= 4.0
         AND score2 >= 4.0
         AND score3 >= 4.0

In điểm trung bình với 2 chữ số thập phân.
In PASS nếu passed, ngược lại in FAIL.
```

Giả mã giúp kiểm tra trình tự trước khi viết cú pháp: **xác thực → tính toán → quyết định → trình bày**. Nếu tính trước khi biết dữ liệu hợp lệ, chương trình có thể truyền giá trị vô nghĩa vào xử lý hoặc vô tình công bố một kết quả sai.

Điều kiện trong Java viết:

```java
boolean passed = average >= 5.0
        && score1 >= 4.0
        && score2 >= 4.0
        && score3 >= 4.0;
```

Đây là đoạn biểu thức minh họa, cần đặt nơi các biến đã được khai báo. Toán tử `&&` có nghĩa **và**, khác `||` (hoặc). Nếu thay nhầm bằng `||`, học sinh có điểm một môn thấp vẫn có thể được kết luận PASS.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **`GradeReport.java`**:

```java
import java.util.Locale;
import java.util.Scanner;

public class GradeReport {
    record Result(double average, boolean passed) {}

    static double readScore(Scanner scanner, String label) {
        if (!scanner.hasNextLine()) {
            throw new IllegalArgumentException("missing " + label);
        }

        String text = scanner.nextLine().trim();
        final double value;

        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid " + label);
        }

        if (!Double.isFinite(value) || value < 0 || value > 10) {
            throw new IllegalArgumentException("out of range " + label);
        }
        return value;
    }

    static Result evaluate(double first, double second, double third) {
        double average = (first + second + third) / 3.0;
        boolean passed = average >= 5.0
                && first >= 4.0
                && second >= 4.0
                && third >= 4.0;
        return new Result(average, passed);
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            try {
                System.out.println("Score 1:");
                double first = readScore(scanner, "score1");

                System.out.println("Score 2:");
                double second = readScore(scanner, "score2");

                System.out.println("Score 3:");
                double third = readScore(scanner, "score3");

                Result result = evaluate(first, second, third);
                System.out.printf(Locale.ROOT,
                        "Average: %.2f%n", result.average());
                System.out.println("Result: "
                        + (result.passed() ? "PASS" : "FAIL"));
            } catch (IllegalArgumentException ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }
}
```

`record Result` là cú pháp ổn định trong Java 17. Nó gom hai kết quả logic `average` và `passed`, tự cung cấp các accessor `average()` và `passed()`. Với bài đơn giản, có thể không cần record; tuy nhiên đối tượng kết quả giúp phân biệt dữ liệu và cách hiển thị.

**Vai trò từng phương thức:**

- `readScore(...)` thuộc **Input**. Nó đọc một dòng, parse `double`, kiểm tra số hữu hạn và miền điểm; đầu vào sai làm phát sinh `IllegalArgumentException`.
- `evaluate(...)` thuộc **Process**. Nó không đọc console và không in gì; với ba điểm hợp lệ, trả `Result`.
- `main(...)` điều phối các bước và thuộc **Output** ở hai câu lệnh cuối. Nếu bắt lỗi input, nó chỉ in `Error: ...` rồi kết thúc.

`Double.parseDouble` dùng dấu chấm thập phân theo cú pháp Java, **không parse theo locale hiển thị của máy**. `Locale.ROOT` chỉ được dùng tại lúc in kết quả để đảm bảo dấu chấm trong output mẫu.

### Biên dịch và chạy

Từ thư mục chứa `GradeReport.java`, tạo thư mục `out` nếu chưa có:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out GradeReport.java
java -cp out GradeReport
```

Có thể chạy trên PowerShell, CMD, bash với JDK 17+. Khi chương trình hỏi `Score 1:`, nhập `7.0`, nhấn Enter; tiếp theo `6.5`, rồi `8.0`, mỗi điểm một dòng.

**Dữ liệu nhập:**

```text
7.0
6.5
8.0
```

**Stdout chính xác do chương trình in** (không tính các ký tự bàn phím được terminal tự echo):

```text
Score 1:
Score 2:
Score 3:
Average: 7.17
Result: PASS
```

Trung bình là `(7.0 + 6.5 + 8.0) / 3 = 7.1666...`, được định dạng thành `7.17`. Cả ba điểm đều ít nhất `4.0`, và trung bình ít nhất `5.0`, nên kết luận PASS.

Nếu muốn chạy với input cố định, lưu ba dòng trên vào `sample.txt`, sau đó:

- Linux/macOS hoặc Windows CMD: `java -cp out GradeReport < sample.txt`.
- Windows PowerShell: `Get-Content -LiteralPath sample.txt | java -cp out GradeReport`.

Lệnh `javac` thành công thường không tạo stdout; nội dung dự kiến ở trên đến từ lệnh `java`. Nếu dùng PowerShell hoặc terminal có cài đặt encoding khác, ví dụ này dùng chuỗi ASCII cho các nhãn để tránh khác biệt hiển thị.

## 6. Dùng bảng vết để kiểm tra công thức và điều kiện

Một kỹ thuật đơn giản là viết **bảng trace**: với input cụ thể, tính từng giá trị trung gian trước khi chạy chương trình.

| Input `(s1, s2, s3)` | Trung bình chưa làm tròn | Mọi điểm `>= 4`? | Trung bình `>= 5`? | Kết luận |
| --- | ---: | --- | --- | --- |
| `(7.0, 6.5, 8.0)` | `7.1666...` | Có | Có | PASS |
| `(4, 5, 6)` | `5.0` | Có | Có | PASS |
| `(3.5, 9, 9)` | `7.1666...` | Không | Có | FAIL |
| `(0, 0, 0)` | `0.0` | Không | Không | FAIL |
| `(10, 10, 10)` | `10.0` | Có | Có | PASS |
| `(4, 4, 7)` | `5.0` | Có | Có | PASS |

Bảng này giúp phát hiện lỗi dùng `||` thay vì `&&`, quên chia cho 3 hoặc quên kiểm tra từng môn. Đặc biệt, trường hợp `(3.5, 9, 9)` chứng minh **trung bình cao vẫn có thể FAIL** nếu một môn dưới `4.0`.

### Kiểm tra biên và input không hợp lệ

Dữ liệu sai phải dừng trước phần xử lý:

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Điểm `0`, `4`, `5`, `10` | Đều là điểm riêng lẻ hợp lệ |
| Điểm `-0.1` hoặc `10.1` | `out of range` |
| `abc` hoặc dòng rỗng | `invalid` |
| `NaN` hoặc `Infinity` | `out of range` vì không hữu hạn |
| Thiếu dòng điểm tiếp theo khi gặp EOF | `missing` |
| Có nhiều hơn ba dòng | Chương trình mẫu chỉ dùng ba dòng theo hợp đồng; dữ liệu thừa chưa được xử lý |

Ví dụ chạy với **input**:

```text
3.5
9
9
```

**Stdout chính xác:**

```text
Score 1:
Score 2:
Score 3:
Average: 7.17
Result: FAIL
```

Ví dụ với **input** chỉ một dòng `abc`:

```text
abc
```

**Stdout chính xác:**

```text
Score 1:
Error: invalid score1
```

Đây là những đầu vào thử **khác nhau**, mỗi bộ được dùng trong một lần chạy riêng; đừng ghép tất cả vào cùng một lượt nhập nếu muốn so khớp output trên.

## 7. Tách phần tính toán để dễ kiểm thử

Lợi ích của hàm `evaluate` là có thể kiểm thử logic mà không cần giả lập bàn phím hay so khớp lời nhắc. Sau đây là chương trình độc lập khác có thể chạy trên Java 17+, tập trung vào kiểm tra **Process**.

Tạo tệp **`GradeLogicTest.java`**:

```java
public class GradeLogicTest {
    static void check(double a, double b, double c,
                      boolean expectedPass) {
        GradeReport.Result result = GradeReport.evaluate(a, b, c);

        if (result.passed() != expectedPass) {
            throw new AssertionError("wrong decision");
        }

        System.out.println("passed=" + result.passed());
    }

    public static void main(String[] args) {
        check(4, 5, 6, true);
        check(3.5, 9, 9, false);
        check(10, 10, 10, true);
    }
}
```

Lớp `GradeLogicTest` dùng phương thức `evaluate` và record `Result` từ `GradeReport` trong cùng **unnamed package**. Biên dịch đồng thời hai tệp:

```sh
javac -encoding UTF-8 --release 17 -d out GradeReport.java GradeLogicTest.java
java -cp out GradeLogicTest
```

**Stdout chính xác:**

```text
passed=true
passed=false
passed=true
```

Nếu một trường hợp cho quyết định sai, đoạn `throw new AssertionError(...)` làm chương trình kiểm tra thất bại ngay tại lần gọi đó. Đây là một ví dụ kiểm tra tối giản bằng Java core; trong dự án thực tế, có thể dùng JUnit để tổ chức nhiều bài kiểm thử.

**Giới hạn có chủ đích của thiết kế mẫu:** `evaluate` giả định ba điểm đã được `readScore` kiểm tra. Nếu dự án công bố `evaluate` như public API để caller khác có thể truyền số tùy ý, cần đưa validation vào chính hàm này hoặc dùng một kiểu dữ liệu đầu vào có quy tắc bất biến. Mỗi hàm phải nêu rõ tiền điều kiện của mình.

## 8. Phân biệt yêu cầu chức năng và phi chức năng

Ngoài việc đúng công thức, phân tích bài toán có thể cần thêm những ràng buộc:

| Nhóm yêu cầu | Ví dụ với bài tính điểm |
| --- | --- |
| **Chức năng** | Nhập ba điểm; tính trung bình; xác định PASS/FAIL; thông báo input sai |
| **Tính đúng đắn** | So sánh ngưỡng bằng trung bình chưa làm tròn; cả ba điểm phải `>= 4` |
| **Dễ dùng** | Ghi rõ đang yêu cầu nhập điểm số mấy; thông báo lỗi cụ thể |
| **Ổn định** | Kiểm tra EOF, giá trị vô hạn và `NaN`, không ném exception không được xử lý cho input sai thường gặp |
| **Khả năng kiểm thử** | Có `evaluate` riêng, kết quả xác định với cùng input |
| **Khả năng bảo trì** | Tên `readScore`, `evaluate`, `Result` thể hiện trách nhiệm; không lặp công thức ở nhiều nơi |
| **Hiệu năng** | Ba phép đọc và vài phép toán là đủ; chưa cần cấu trúc dữ liệu phức tạp |

Trong bài nhỏ, thời gian xử lý thuật toán là **O(1)** và bộ nhớ phụ trội cho phép tính là **O(1)**, nếu chỉ xét ba điểm cố định. Với dữ liệu nhiều môn, yêu cầu sẽ đổi thành đọc số lượng tùy ý, có thể cần mảng, danh sách hoặc vòng lặp; đó là **một bài toán khác** cần phân tích lại input, điều kiện và output.

## 9. Các lỗi thường gặp khi phân tích bài toán

**Nhầm dữ liệu vào với kết quả cần tính.** Điểm trung bình không phải input nếu đề chỉ cung cấp ba điểm môn học; nó là giá trị được tính trong Process.

**Bỏ qua đơn vị và miền hợp lệ.** Với điểm số, `-1` hay `15` không hợp lệ dù vẫn parse được thành `double`. Với bài tiền tệ hay thời gian, đơn vị còn quan trọng hơn: VND với USD, giây với phút, mét với centimet không thể dùng lẫn.

**Không phân biệt việc parse thành công với dữ liệu hợp lệ.** `Double.parseDouble("20")` thành công, nhưng `20` vẫn ngoài khoảng điểm cho phép.

**Thiếu trường hợp biên.** Một bài toán thường sai ở điểm đúng bằng ngưỡng (`4.0`, `5.0`), giá trị nhỏ nhất/lớn nhất, chuỗi rỗng, input thiếu hoặc số rất lớn.

**Tính toán ngay trong lệnh in.** Nếu viết toàn bộ công thức dài trong `System.out.println(...)`, khó kiểm tra giá trị trung gian và tái sử dụng logic. Tách ra biến hoặc phương thức có tên rõ.

**Chọn kiểu dữ liệu tùy tiện.** Với điểm lẻ cần số thực; với số lượng sản phẩm dùng số nguyên; với tiền tệ chính xác đến đơn vị nhỏ nhất nên cân nhắc số nguyên hoặc `BigDecimal` tùy hợp đồng, không dùng `double` theo thói quen.

**Làm tròn quá sớm.** Nếu chỉ định dạng `%.2f` khi in, quyết định PASS/FAIL vẫn dựa trên giá trị chưa làm tròn. Ví dụ gần ngưỡng có thể được in `5.00` nhưng kết luận FAIL khi giá trị thật còn dưới `5.0`. Nếu người dùng yêu cầu “làm tròn rồi xét”, phải đổi rõ quy tắc xử lý và kiểm thử tương ứng.

**Không mô tả đầu ra lỗi.** Chương trình cần hợp đồng cho cả đầu vào sai. Một stack trace khó hiểu không phải thông báo phù hợp cho mọi chương trình console nhập liệu.

**Trộn Input/Process/Output trong một khối quá dài.** Với bài nhỏ có thể chấp nhận, nhưng khi mở rộng sẽ khó thay terminal thành file/API, khó kiểm thử tính toán và dễ lặp validation.

## 10. Áp dụng IPO cho bài khác

Ví dụ yêu cầu: *Nhập chiều dài và chiều rộng hình chữ nhật; tính diện tích và chu vi.*

| Input | Process | Output |
| --- | --- | --- |
| Hai số dương `length`, `width`, cùng đơn vị (ví dụ mét) | `area = length * width`, `perimeter = 2 * (length + width)` | Diện tích (m²), chu vi (m), hoặc lỗi kích thước không hợp lệ |

Từ bảng này, có thể tạo những test: `(2, 3)` cho diện tích `6`, chu vi `10`; `(0, 3)` là input không hợp lệ nếu hợp đồng yêu cầu hai cạnh **lớn hơn 0**; `(-2, 3)` cũng không hợp lệ.

Nếu người dùng nhập kích thước bằng centimet nhưng muốn diện tích m², phải thêm bước **đổi đơn vị** trong Process. Chỉ đổi tên biến hoặc nối chữ `m²` ở Output sẽ không khiến kết quả tự đúng.

## 11. Checklist trước khi bắt đầu code

1. Tôi có thể mô tả **mỗi input** bằng tên, kiểu, đơn vị, nguồn và miền hợp lệ không?
2. Có quy tắc nào còn mơ hồ về làm tròn, ngưỡng, dữ liệu thiếu hoặc dữ liệu thừa không?
3. Tôi đã viết **công thức, điều kiện, thứ tự xử lý** thành giả mã hay sơ đồ rõ ràng chưa?
4. Tôi có chỉ rõ **output thành công và output khi lỗi** không?
5. Tôi đã thử tính tay ít nhất một trường hợp thông thường, một trường hợp sát ngưỡng và một trường hợp sai dữ liệu chưa?
6. Có thể tách **đọc**, **xử lý** và **in/return** thành các khối hoặc phương thức có trách nhiệm rõ ràng không?
7. Tôi đã biên dịch bằng `javac`, chạy bằng `java` và đối chiếu output thực với kết quả dự kiến chưa?

**Ghi nhớ:** Trước khi chọn cú pháp Java, hãy chuyển yêu cầu thành **Input có hợp đồng rõ ràng → Process có công thức và quy tắc kiểm tra → Output có thể kiểm chứng**. Phân tích tốt giúp code ngắn hơn, dễ sửa hơn và tránh nhiều lỗi logic ngay từ đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-002">Viết mã giả và bảng theo dõi trước khi lập trình</a>

<details>
<summary>Xem chi tiết</summary>

# Viết mã giả và bảng theo dõi trước khi lập trình

## 1. Vì sao nên thiết kế trước khi viết Java?

Khi giải một bài toán, viết ngay các câu lệnh `if`, `for` và `System.out.println` có thể khiến ta bỏ sót điều kiện, chọn sai biến hoặc không biết vì sao kết quả sai. **Mã giả** (*pseudocode*) và **bảng theo dõi** (*trace table/dry-run table*) giúp kiểm tra ý tưởng trước khi phụ thuộc vào cú pháp của một ngôn ngữ lập trình.

- **Mã giả** diễn tả trình tự thuật toán bằng ngôn ngữ tự nhiên có cấu trúc. Nó nêu **làm gì và theo thứ tự nào**, không nhất thiết tuân theo cú pháp Java.
- **Bảng theo dõi** ghi lại sự thay đổi của các biến sau từng bước quan trọng, đặc biệt khi chương trình có vòng lặp và nhánh điều kiện.
- **Chạy tay** (*dry run*) là mô phỏng thuật toán với một bộ dữ liệu cụ thể mà chưa cần chạy máy.
- **Triển khai** là chuyển thuật toán đã được kiểm tra thành Java rồi đối chiếu kết quả thực tế với kết quả mong đợi.

Quy trình hiệu quả:

```text
Đọc đề
   |
   v
Xác định input, output, quy tắc, trường hợp biên
   |
   v
Viết mã giả theo các bước rõ ràng
   |
   v
Chạy tay và lập bảng theo dõi
   |
   v
Sửa lỗi logic nếu có
   |
   v
Viết Java 17+, biên dịch và kiểm thử
   |
   v
So sánh kết quả với bảng theo dõi và yêu cầu
```

Không phải bài toán nhỏ nào cũng cần bảng dài. Mục tiêu là **đủ rõ để chứng minh cách giải hợp lý và tìm lỗi sớm**, không phải thêm thủ tục hình thức.

## 2. Xác định yêu cầu và hợp đồng đầu vào/đầu ra

Trước khi viết mã giả, trả lời bốn câu hỏi:

1. **Đầu vào là gì?** Một số nguyên, một mảng, danh sách, chuỗi hay dữ liệu đọc từ file?
2. **Đầu ra là gì?** Giá trị cần trả về, nội dung cần in, hoặc trạng thái cần cập nhật?
3. **Quy tắc tính toán và ràng buộc là gì?** Chẳng hạn số dương nghĩa là `x > 0` (không tính `0`), có thể có số âm, mảng có thể rỗng.
4. **Trường hợp biên và trường hợp sai là gì?** Mảng rỗng, không có phần tử đạt điều kiện, một phần tử, giá trị lớn, `null`...

Ví dụ xuyên suốt:

**Đề bài:** Cho một mảng số nguyên `int[]`. Hãy đếm số phần tử **dương**, tính **tổng** các số dương, xác định **số dương lớn nhất** và tính **trung bình** các số dương. Nếu không có số dương, phải báo rõ là không có số dương và **không thực hiện phép chia cho 0**.

Đầu vào mẫu:

```text
[4, -2, 7, 0, 3]
```

Kết quả cần suy ra:

- Số dương: `4`, `7`, `3`.
- Số lượng: `3`.
- Tổng: `4 + 7 + 3 = 14`.
- Giá trị lớn nhất: `7`.
- Trung bình: `14 / 3`, khi hiển thị hai chữ số thập phân là `4.67`.

Trong ví dụ này, **`0` không phải số dương**. Cũng cần quyết định trước: nếu không có số dương thì không có giá trị lớn nhất/trung bình hợp lệ; không nên tự coi giá trị lớn nhất là `0` như một kết quả nghiệp vụ.

Đối với một API tái sử dụng, phải quy định thêm cách xử lý tham chiếu `null`: từ chối bằng ngoại lệ hay chấp nhận như mảng rỗng. Trong chương trình mẫu bên dưới, phương thức yêu cầu mảng khác `null` và chủ động báo lỗi nếu vi phạm.

## 3. Cách viết mã giả dễ chuyển thành chương trình

Mã giả **không có một cú pháp duy nhất bắt buộc**. Hãy dùng tên biến rõ ràng và những cấu trúc phổ biến:

| Ý tưởng thuật toán | Cách ghi mã giả | Gợi ý khi chuyển sang Java |
| --- | --- | --- |
| Gán trạng thái ban đầu | `count ← 0` | `int count = 0;` |
| Rẽ nhánh | `IF value > 0 THEN ...` | `if (value > 0) { ... }` |
| Duyệt phần tử | `FOR EACH value IN numbers` | `for (int value : numbers) { ... }` |
| Cập nhật biến | `sum ← sum + value` | `sum += value;` |
| Xử lý trường hợp không có dữ liệu | `IF count = 0 THEN ...` | `if (count == 0) { ... }` |
| Xuất kết quả | `OUTPUT count, sum` | `System.out.println(...)` |

Dấu `←` là ký hiệu **gán** thường dùng trong mã giả, không phải toán tử Java. Mã giả dùng `=` để so sánh chỉ khi quy ước đã được làm rõ; trong Java, **`==`** kiểm tra bằng nhau đối với số nguyên còn **`=`** là phép gán.

Một mã giả tốt nên:

- Ghi rõ **điểm bắt đầu**, dữ liệu cần giữ và thứ tự các bước.
- Đặt tên biến có ý nghĩa, ví dụ `count` (số lượng), `sum` (tổng), `max` (giá trị lớn nhất).
- Diễn tả điều kiện biên bằng biểu thức chính xác, không dùng câu mơ hồ như “nếu giá trị thích hợp”.
- Làm rõ mỗi lần lặp **xử lý một phần tử** và biến nào được thay đổi.
- Tránh gắn chặt vào việc in ra màn hình nếu mục tiêu chính là thuật toán; in là một cách trình bày kết quả.
- Tránh nhảy ngay vào tối ưu hóa phức tạp trước khi chứng minh thuật toán đơn giản là đúng.

## 4. Viết mã giả cho bài toán đếm và thống kê số dương

Một phiên bản mã giả đủ chi tiết:

```text
INPUT numbers: mảng các số nguyên, không null

count ← 0
sum ← 0
max ← 0                  // Chưa được coi là kết quả cho tới khi count > 0

FOR EACH value IN numbers DO
    IF value > 0 THEN
        IF count = 0 OR value > max THEN
            max ← value
        END IF
        count ← count + 1
        sum ← sum + value
    END IF
END FOR

OUTPUT count
OUTPUT sum

IF count = 0 THEN
    OUTPUT "no positive values"
ELSE
    average ← sum / count  // Chia số thực, không chia nguyên
    OUTPUT max
    OUTPUT average với 2 chữ số sau dấu thập phân
END IF
```

Đây là **mã giả, không được biên dịch bởi `javac`**. Một số quyết định thiết kế quan trọng:

1. `count` và `sum` bắt đầu bằng `0` vì chưa xử lý phần tử nào.
2. `value > 0` lọc đúng các số dương; số âm và `0` không đổi bộ đếm, tổng và giá trị lớn nhất.
3. `count = 0` trong điều kiện cập nhật `max` nghĩa là **đang gặp số dương đầu tiên**. Nhờ vậy, giá trị khởi tạo `max ← 0` không phải một ứng viên lớn nhất thực sự.
4. Cần kiểm tra `count = 0` **trước khi tính trung bình** để không chia cho `0`.
5. Trung bình phải dùng phép chia **số thực**. Nếu dùng phép chia hai số nguyên, phần thập phân bị mất.
6. Trong Java, dùng `long sum` để mở rộng phạm vi tổng so với `int`, vì cộng nhiều số `int` dương có thể vượt `Integer.MAX_VALUE`. `long` vẫn có giới hạn, nên bài toán kích thước cực lớn cần ràng buộc đầu vào hoặc cách tính thích hợp hơn.

### Bất biến vòng lặp

Một cách tự giải thích tính đúng đắn là nêu điều gì phải luôn đúng sau khi xử lý mỗi phần tử:

- `count` = số phần tử dương đã gặp trong phần đầu của mảng.
- `sum` = tổng các phần tử dương đã gặp.
- Khi `count > 0`, `max` = số lớn nhất trong các phần tử dương đã gặp.

Ban đầu, với phần đầu của mảng có độ dài `0`, `count = 0`, `sum = 0`; điều kiện về `max` chưa áp dụng. Mỗi bước bỏ qua phần tử không dương hoặc cập nhật đúng ba biến cho phần tử dương, nên các điều kiện trên tiếp tục đúng. Sau vòng lặp, phần đầu đã xét là **toàn bộ mảng**, do đó các kết quả thu được đúng theo định nghĩa.

Không cần viết chứng minh hình thức quá dài cho mọi bài tập, nhưng việc nhận diện bất biến giúp kiểm tra thiết kế vòng lặp có bỏ sót hoặc đếm trùng dữ liệu hay không.

## 5. Lập bảng theo dõi (*trace table*) từng lần lặp

Bảng theo dõi nên có **dữ liệu đầu vào, vị trí đang xét, điều kiện rẽ nhánh, giá trị các biến sau khi cập nhật**. Với `numbers = [4, -2, 7, 0, 3]`, ta có:

| Bước | Chỉ số `i` | `value` | `value > 0` | `count` sau bước | `sum` sau bước | `max` hợp lệ sau bước |
| --- | ---: | ---: | --- | ---: | ---: | --- |
| Khởi tạo | — | — | — | 0 | 0 | Chưa có |
| 1 | 0 | 4 | Đúng | 1 | 4 | 4 |
| 2 | 1 | -2 | Sai | 1 | 4 | 4 |
| 3 | 2 | 7 | Đúng | 2 | 11 | 7 |
| 4 | 3 | 0 | Sai | 2 | 11 | 7 |
| 5 | 4 | 3 | Đúng | 3 | 14 | 7 |

Cách tính:

- **Bước 1:** gặp `4 > 0`; đây là số dương đầu tiên, nên `max = 4`, `count = 1`, `sum = 4`.
- **Bước 2:** `-2` không dương, giữ nguyên trạng thái.
- **Bước 3:** gặp `7`, tăng `count = 2`, `sum = 11`, cập nhật `max = 7`.
- **Bước 4:** `0` không dương, giữ nguyên trạng thái.
- **Bước 5:** gặp `3`, tăng `count = 3`, `sum = 14`; `max` vẫn là `7`.
- **Sau vòng lặp:** vì `count = 3`, trung bình bằng `(double) 14 / 3 ≈ 4.666...`, được in là `4.67` nếu dùng định dạng hai chữ số thập phân với `Locale.US`.

Cột `max` ghi **“Chưa có”** lúc khởi tạo để phân biệt giá trị lưu tạm trong biến với **kết quả đã có nghĩa**. Không nên in `max = 0` như số dương lớn nhất khi mảng không có số dương.

### Chọn nội dung cho bảng theo dõi

Một bảng theo dõi nên trả lời được: “Tại sao sau bước thứ ba lại có `sum = 11`?” Nếu chỉ ghi kết quả cuối, bảng không giúp tìm lỗi logic. Nhưng cũng không cần ghi tất cả biến không liên quan, vì bảng sẽ dài và khó đọc.

Cách đơn giản là **mỗi hàng tương ứng một lần lặp** và các cột là biến thay đổi hoặc điều kiện quyết định. Với vòng lặp lồng nhau, có thể thêm chỉ số vòng ngoài/vòng trong; với thuật toán tìm kiếm, có thể ghi điều kiện dừng và trạng thái “đã tìm thấy” hay chưa.

## 6. Thử trường hợp biên ngay trên mã giả

Chỉ kiểm tra một bộ dữ liệu “đẹp” là chưa đủ. Dùng mã giả và bảng trạng thái để thử những trường hợp khác:

| Đầu vào | `count` | `sum` | Kết luận |
| --- | ---: | ---: | --- |
| `[]` | 0 | 0 | Không có số dương; không tính `max`/`average` |
| `[-1, 0]` | 0 | 0 | Không có số dương |
| `[5]` | 1 | 5 | `max = 5`, `average = 5.00` |
| `[2, 2, 2]` | 3 | 6 | `max = 2`, `average = 2.00` |
| `[0, 3, -4, 8]` | 2 | 11 | `max = 8`, `average = 5.50` |

Những trường hợp này kiểm tra trực tiếp các sai sót phổ biến:

- Dùng `>= 0` thay vì `> 0` làm `0` bị đếm thành số dương.
- Không kiểm tra `count == 0` trước khi chia.
- Khởi tạo sai `max` khi mảng không có số dương.
- Chỉ kiểm tra phần tử đầu hoặc bỏ sót phần tử cuối.
- Chia nguyên khi cần trung bình có phần lẻ.

Mảng `null` không giống mảng rỗng. Nếu yêu cầu của phương thức không chấp nhận `null`, nên **từ chối rõ ràng** thay vì vô tình gây `NullPointerException` sâu trong vòng lặp.

## 7. Chuyển mã giả thành Java 17+ chạy được

Tạo tệp **`PseudocodeTraceDemo.java`**:

```java
import java.util.Locale;
import java.util.Objects;

public class PseudocodeTraceDemo {
    static void report(int[] numbers) {
        Objects.requireNonNull(numbers, "numbers");

        int count = 0;
        long sum = 0;
        int max = 0;

        for (int value : numbers) {
            if (value > 0) {
                if (count == 0 || value > max) {
                    max = value;
                }
                count++;
                sum += value;
            }
        }

        System.out.println("count=" + count);
        System.out.println("sum=" + sum);

        if (count == 0) {
            System.out.println("no positive values");
        } else {
            double average = (double) sum / count;
            System.out.println("max=" + max);
            System.out.printf(Locale.US, "average=%.2f%n", average);
        }
    }

    public static void main(String[] args) {
        System.out.println("Case A");
        report(new int[] {4, -2, 7, 0, 3});

        System.out.println("Case B");
        report(new int[] {-1, 0});

        System.out.println("Case C");
        report(new int[] {5});
    }
}
```

Trong thư mục chứa tệp, dùng **JDK 17 trở lên** trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 PseudocodeTraceDemo.java
java PseudocodeTraceDemo
```

**Stdout chính xác** (biên dịch thành công không in thông báo):

```text
Case A
count=3
sum=14
max=7
average=4.67
Case B
count=0
sum=0
no positive values
Case C
count=1
sum=5
max=5
average=5.00
```

### Đối chiếu từng bước với mã giả

| Mã giả | Java 17+ | Ý nghĩa |
| --- | --- | --- |
| `count ← 0` | `int count = 0;` | Trạng thái ban đầu |
| `sum ← 0` | `long sum = 0;` | Tổng, dùng `long` |
| `FOR EACH value IN numbers` | `for (int value : numbers)` | Duyệt lần lượt từng phần tử |
| `IF value > 0` | `if (value > 0)` | Lọc số dương |
| `count ← count + 1` | `count++;` | Tăng bộ đếm |
| `sum ← sum + value` | `sum += value;` | Cộng vào tổng |
| `IF count = 0` | `if (count == 0)` | Kiểm tra không có số dương |
| `average ← sum / count` | `(double) sum / count` | Chia thực, giữ phần lẻ |

Hai điểm cần kiểm tra sau khi chuyển:

1. **Đúng thứ tự:** ở Java, `max` được cập nhật **trước** khi tăng `count`, nên điều kiện `count == 0` nhận diện chính xác số dương đầu tiên. Nếu chuyển vị trí `count++` lên trước, điều kiện này không còn phát hiện lần đầu.
2. **Đúng kiểu:** `sum` là `long`, nhưng có phép ép rõ `(double) sum` khi chia để không tạo phép chia nguyên. `System.out.printf(Locale.US, ...)` đảm bảo dấu chấm thập phân trong đầu ra ví dụ.

`Objects.requireNonNull` ném `NullPointerException` có thông điệp tên tham số khi `numbers == null`. Ở đây đó là **tiền điều kiện của API**; mã hoàn chỉnh không truyền `null` nên stdout ổn định.

## 8. Các kiểu lỗi mà chạy tay giúp phát hiện

### Lỗi bỏ sót phần tử cuối (*off-by-one*)

Mã giả muốn duyệt mọi phần tử của mảng có `n` phần tử, chỉ số hợp lệ là `0` đến `n - 1`.

```java
// Minh họa trong phương thức:
for (int i = 0; i < numbers.length - 1; i++) {
    // Bị bỏ sót phần tử cuối nếu mục tiêu là duyệt cả mảng.
}
```

Bảng theo dõi buộc người viết ghi chỉ số `i`; nếu mảng có năm phần tử mà bảng chỉ có bốn bước, lỗi dễ được phát hiện. Cách thông thường để duyệt đủ chỉ số là `i < numbers.length`, hoặc dùng for-each khi không cần chỉ số.

### Lỗi cập nhật biến sai nhánh

```java
// Minh họa sai logic:
for (int value : numbers) {
    if (value > 0) {
        sum += value;
    }
    count++; // Sai: đếm cả số âm và 0.
}
```

Bảng theo dõi với `[-2, 4]` cho thấy sau bước xét `-2`, `count` tăng thành 1 mặc dù chưa gặp số dương. Cần đặt `count++` **bên trong** nhánh `value > 0`.

### Lỗi khởi tạo hoặc cập nhật cực trị

Nếu tìm giá trị lớn nhất trong **mọi số**, khởi tạo `max = 0` là sai đối với mảng chỉ chứa số âm. Bài xuyên suốt tìm số **dương** lớn nhất nên có điều kiện `count > 0` và cập nhật `max` khi nhận số dương đầu tiên. **Không được lấy quy tắc khởi tạo này áp dụng máy móc cho bài toán khác**.

### Lỗi chia nguyên

```java
int count = 3;
long sum = 14;
double wrong = sum / count;          // 4.0: chia nguyên trước, rồi đổi sang double.
double correct = (double) sum / count; // 4.6666...
```

Bảng theo dõi cho `sum = 14`, `count = 3`; khi lấy trung bình nếu chương trình in `4.00`, đó là dấu hiệu chọn sai kiểu phép chia. `printf("%.2f")` chỉ định dạng kết quả; **không khôi phục phần lẻ đã mất** vì phép chia nguyên.

## 9. Bảng theo dõi khác gì việc in debug?

**Bảng theo dõi trước khi chạy** là dự đoán được viết ra từ logic thuật toán; **dòng in debug** là trạng thái thực tế khi JVM thực thi. So sánh chúng giúp khoanh vùng dòng làm thuật toán đi sai.

Ví dụ, có thể tạm thêm sau mỗi vòng lặp trong `report`:

```java
System.out.println("value=" + value
        + " count=" + count
        + " sum=" + sum);
```

Đây là **dòng minh họa** để đặt trong thân vòng lặp, không thuộc chương trình hoàn chỉnh ở mục 7. Khi in debug, hãy đảm bảo vị trí in nằm **sau cập nhật trạng thái** nếu bảng cũng ghi trạng thái sau mỗi bước. Nếu in trước cập nhật mà đem so với bảng sau cập nhật, dễ tưởng rằng có lỗi dù chỉ là hai thời điểm quan sát khác nhau.

Trong chương trình thực tế, có thể dùng breakpoint/debugger hoặc unit test thay vì để nhiều `println` debug trong mã sản xuất.

Một bảng theo dõi không chứng minh thuật toán đúng cho **mọi** đầu vào. Để tăng tin cậy, hãy kết hợp:
- Bảng chạy tay cho trường hợp thông thường và trường hợp biên.
- Lập luận bất biến hoặc lý do điều kiện/vòng lặp đúng.
- Kiểm thử tự động với đầu ra kỳ vọng rõ ràng.
- Kiểm tra hành vi khi đầu vào không hợp lệ.

## 10. Mẫu bảng theo dõi có thể tái sử dụng

Khi thực hành những bài khác, hãy chuẩn bị trước bảng đơn giản:

| Bước | Dữ liệu đang xét | Điều kiện | Biến trước | Biến sau | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| Khởi tạo | Chưa xét | — | — | Trạng thái ban đầu | Đặt giá trị khởi tạo |
| 1 | Phần tử thứ nhất | Đúng/Sai | Trạng thái cũ | Trạng thái mới | Ghi nhánh đã chọn |
| 2 | Phần tử thứ hai | Đúng/Sai | Trạng thái cũ | Trạng thái mới | Xem có cập nhật đúng không |
| Kết thúc | Hết dữ liệu | Điều kiện dừng | Trạng thái cuối | Kết quả | Kiểm tra hậu điều kiện |

Bảng trên là **khuôn minh họa**, không phải bảng kết quả của ví dụ số dương. Với bài tìm kiếm, thay các cột bằng `index`, `target`, `found`; với bài đếm, theo dõi `count`; với bài tính tổng, theo dõi `sum`. Luôn ưu tiên các biến quyết định tính đúng của thuật toán.

### Checklist trước khi bắt đầu viết mã Java

1. Đã xác định **input, output** và ý nghĩa của từng kết quả chưa?
2. Các điều kiện như `>`, `>=`, `==` có đúng yêu cầu không?
3. Đã nêu rõ cách xử lý **mảng rỗng/không có dữ liệu hợp lệ** chưa?
4. Biến đếm, tổng, cực trị đã được khởi tạo hợp lý chưa?
5. Mỗi bước lặp cập nhật đúng biến, đúng nhánh và **không bỏ sót phần tử** chưa?
6. Bảng theo dõi có bao gồm trường hợp thường và ít nhất một **trường hợp biên** không?
7. Có phép chia cho 0, chia nguyên ngoài ý muốn, hoặc nguy cơ tràn số không?
8. Khi chuyển sang Java, kết quả chạy thực tế có **khớp dự đoán** không?

**Ghi nhớ:** viết mã giả để diễn đạt thuật toán trước cú pháp; dùng bảng theo dõi để quan sát những thay đổi của biến và phát hiện sai logic sớm; sau đó mới triển khai Java và kiểm thử. Một thuật toán được suy nghĩ, chạy tay và kiểm tra đầu vào biên trước thường dễ hiểu và dễ sửa hơn mã được viết bằng thử-sai ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-003">Đặt tên rõ nghĩa, thụt lề và tránh số không giải thích</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên rõ nghĩa, thụt lề và tránh số không giải thích trong Java

Mã dễ đọc giúp người khác hiểu **ý nghĩa và ý định** mà không phải đoán từ cách triển khai. Với Java 17+, ba thói quen có tác dụng trực tiếp là đặt tên thể hiện vai trò, định dạng khối lệnh nhất quán và thay các con số mang quy tắc nghiệp vụ bằng tên có nghĩa.

## 1. Đặt tên nói rõ dữ liệu và hành động

| Thành phần | Quy ước phổ biến trong Java | Ví dụ |
| --- | --- | --- |
| Lớp, interface, enum | `UpperCamelCase`, thường là danh từ/vai trò | `TicketPricing`, `PaymentService` |
| Phương thức | `lowerCamelCase`, thường bắt đầu bằng động từ | `calculateTicketPriceVnd()`, `sendNotification()` |
| Biến, tham số | `lowerCamelCase`, đặt theo ý nghĩa và phạm vi | `customerAge`, `orderTotalVnd` |
| Hằng số `static final` | `UPPER_SNAKE_CASE` | `MINIMUM_ADULT_AGE`, `MAX_RETRY_COUNT` |
| Tên package | Chữ thường, phân cấp theo miền chức năng | `com.example.tickets` |
| Biến `boolean` | Tên đọc được như câu hỏi đúng/sai | `isChild`, `hasPermission`, `canRetry` |

Nguyên tắc chọn tên:

- **Nói lên mục đích:** `customerAge` rõ hơn `a`; `calculateTicketPriceVnd` rõ hơn `calc()`. Tránh `data`, `result`, `temp`, `value` khi có thể nêu chính xác chúng chứa gì.
- **Ghi đơn vị nếu dễ nhầm:** `timeoutMillis`, `priceVnd`, `fileSizeBytes`. Một tên `timeout` không nói được là mili giây hay giây.
- **Phân biệt khái niệm gần nhau:** `totalPriceVnd` và `discountAmountVnd` khác `discountPercent`; một số tiền khác một tỷ lệ.
- **Đúng phạm vi:** biến vòng lặp rất ngắn có thể là `i` nếu rõ ràng, nhưng tên có ý nghĩa cần dùng cho biến nghiệp vụ hoặc biến sống qua nhiều bước.
- **Nhất quán từ vựng:** chọn một cách gọi, chẳng hạn `customer`, rồi dùng xuyên suốt thay vì xen kẽ `client`, `buyer`, `user` cho cùng một khái niệm.
- **Không lạm dụng viết tắt:** `numberOfItems` dễ hiểu hơn `nItm`. Tránh ký tự dễ nhầm như `l` (chữ L thường), `I` (i hoa) hoặc `O` (o hoa) đứng riêng.
- **Tên hợp lệ vẫn có thể khó đọc:** Java cho phép nhiều ký tự Unicode trong identifier nhưng mã nguồn thường dễ cộng tác hơn với tên tiếng Anh, ký tự ASCII và quy ước nhất quán. Không dùng từ khóa Java làm tên biến.

Tên nên phản ánh hành vi thực tế. Chẳng hạn, một hàm kiểm tra rồi ghi dữ liệu không nên gọi `validate()` nếu nó còn tạo tác dụng phụ. Khi API được dùng ở nhiều nơi, đổi tên nên thực hiện bằng chức năng **Rename/Refactor** của IDE để cập nhật tất cả chỗ tham chiếu.

## 2. Thụt lề và dấu ngoặc nhọn giúp nhìn đúng luồng điều khiển

Một phong cách thông dụng cho Java:

1. Thụt **4 dấu cách cho mỗi cấp khối**; thiết lập formatter thống nhất trong dự án, không trộn tab và dấu cách.
2. Luôn dùng `{` và `}` cho thân `if`, `else`, vòng lặp, kể cả thân chỉ có một lệnh; đặt dấu mở khối sau điều kiện, dấu đóng khối thẳng hàng với cấu trúc điều khiển.
3. Dùng khoảng trắng quanh toán tử, sau dấu phẩy; cách nhóm các phương thức và biến nhất quán; tách các biểu thức quá dài ở vị trí hợp lý.
4. Căn lề theo **cấu trúc cú pháp**, không căn cho đẹp bằng số lượng dấu cách tùy ý. Chạy công cụ formatter trước khi đưa mã vào review.

Ví dụ sau **chỉ là đoạn mã trong một phương thức** và dễ gây lỗi:

```java
if (isMember)
    discountPercent = 10;
    System.out.println("Đã áp dụng giảm giá");
```

Dòng `println` **luôn chạy**, dù `isMember` là `false`: Java xác định thân `if` theo dấu ngoặc hoặc đúng một statement, không dựa vào thụt lề. Sửa bằng khối lệnh rõ ràng:

```java
if (isMember) {
    discountPercent = 10;
    System.out.println("Đã áp dụng giảm giá");
}
```

Thụt lề tốt giúp đọc luồng, nhưng dấu ngoặc nhọn mới xác định ranh giới khối. Tương tự, đặt `else` sát khối `if` tương ứng để tránh hiểu sai các nhánh lồng nhau. Không nên sửa lỗi điều khiển chỉ bằng formatter: phải rà soát cấu trúc chương trình.

## 3. Tránh “magic numbers” (số không giải thích)

**Magic number** là số được viết trực tiếp trong biểu thức khi người đọc không biết ý nghĩa hoặc lý do chọn nó:

```java
int age = 10;
int price = 200000;
if (age < 12) {
    price = price * 75 / 100;
}
```

Đọc riêng đoạn này không rõ `12` là mốc tuổi nào, `200000` có đơn vị gì, `75` biểu thị mức giá hay giảm giá. Những quy tắc này dễ bị sửa thiếu ở một trong nhiều chỗ.

Cách cải thiện phụ thuộc vào **bản chất** của giá trị:

| Trường hợp | Cách diễn đạt nên dùng |
| --- | --- |
| Ngưỡng, giới hạn, phí hay tỷ lệ nghiệp vụ cố định trong phiên bản mã | Hằng `private static final` với tên giải thích chính sách, ví dụ `MAX_RETRY_COUNT` |
| Khoảng thời gian | `Duration.ofSeconds(5)`, `Duration.ofMinutes(2)` thay vì số mili giây không rõ đơn vị |
| Giá trị cấu hình thay đổi theo môi trường hoặc vận hành | Đưa vào cấu hình có tên và kiểm tra hợp lệ, không cố định mã nguồn |
| Tập giá trị trạng thái có ý nghĩa | `enum` hay kiểu riêng thay cho số `0`, `1`, `2` khó hiểu |
| Số mang ý nghĩa toán học rõ ràng hoặc chỉ số cơ bản | `0`, `1` có thể giữ nguyên nếu ngữ cảnh thật sự hiển nhiên |

Không cần tạo hằng số cho *mọi* số. Chỉ đặt tên khi nó giúp nêu quy tắc, giải thích đơn vị, tránh trùng lặp hoặc gom một điểm thay đổi. Đặt tên `NUMBER_12` không cải thiện gì: **tên phải giải thích ý nghĩa, không lặp lại giá trị**. Hằng số `static final` chỉ bất biến theo tham chiếu/giá trị trường; đối tượng mutable mà nó trỏ tới vẫn có thể thay đổi.

## 4. Chương trình Java 17+ chạy độc lập

Lưu đúng tên tệp **`TicketPricing.java`** (không có khai báo `package`) với nội dung sau:

```java
public final class TicketPricing {
    private static final int MINIMUM_ADULT_AGE = 12;
    private static final int STANDARD_TICKET_PRICE_VND = 200_000;
    private static final int CHILD_DISCOUNT_PERCENT = 25;
    private static final int PERCENT_BASE = 100;

    private TicketPricing() {
    }

    public static void main(String[] args) {
        int youngerCustomerAge = 10;
        int adultCustomerAge = 12;

        System.out.println(
                "Khách " + youngerCustomerAge + " tuổi: "
                        + calculateTicketPriceVnd(youngerCustomerAge) + " VND");
        System.out.println(
                "Khách " + adultCustomerAge + " tuổi: "
                        + calculateTicketPriceVnd(adultCustomerAge) + " VND");
    }

    private static int calculateTicketPriceVnd(int customerAge) {
        if (customerAge < 0) {
            throw new IllegalArgumentException("Tuổi phải không âm");
        }

        boolean isChild = customerAge < MINIMUM_ADULT_AGE;
        if (isChild) {
            return STANDARD_TICKET_PRICE_VND
                    * (PERCENT_BASE - CHILD_DISCOUNT_PERCENT)
                    / PERCENT_BASE;
        }

        return STANDARD_TICKET_PRICE_VND;
    }
}
```

Chạy trong thư mục chứa tệp bằng JDK 17 trở lên (PowerShell, CMD, macOS hoặc Linux):

```bash
javac -encoding UTF-8 TicketPricing.java
java TicketPricing
```

**Kết quả chuẩn trên stdout:**

```text
Khách 10 tuổi: 150000 VND
Khách 12 tuổi: 200000 VND
```

Giải thích: `MINIMUM_ADULT_AGE` làm rõ chính sách **dưới 12 tuổi** được giảm 25%; khách đúng 12 tuổi trả giá chuẩn. `STANDARD_TICKET_PRICE_VND` giải thích đơn vị, `PERCENT_BASE` làm rõ phép tính phần trăm, còn `calculateTicketPriceVnd` nói rõ kết quả trả về. Dấu `_` trong literal số `200_000` chỉ giúp đọc mã; giá trị vẫn là số nguyên `200000`. Ví dụ dùng số nguyên VND và mức giảm chia hết, nên không phát sinh vấn đề làm tròn; nghiệp vụ tiền tệ có phần lẻ thường cần `BigDecimal` hoặc số đơn vị nhỏ nhất cùng quy tắc làm tròn rõ ràng.

## 5. Áp dụng khi sửa mã có sẵn

1. **Đọc ý nghĩa trước khi đổi tên:** xác định biến đại diện cho dữ liệu nào, đơn vị và phạm vi sống; chọn tên phù hợp hành vi thực tế.
2. **Đặt tên cho quy tắc:** thay ngưỡng và tỷ lệ khó hiểu bằng hằng số hoặc cấu hình có tên; tránh để cùng một chính sách có nhiều giá trị sao chép.
3. **Chuẩn hóa khối điều khiển:** thêm dấu ngoặc nếu cần, rà soát `if/else` và vòng lặp, rồi áp dụng formatter thống nhất.
4. **Kiểm tra hành vi sau khi sửa:** biên dịch và thử tại ngưỡng, ngay dưới/trên ngưỡng; với ví dụ trên là tuổi `11`, `12`, `13` và trường hợp tuổi âm. Việc thay tên hay format đơn thuần không được làm đổi logic.
5. **Giữ thay đổi dễ review:** thay đổi API công khai có thể ảnh hưởng mã gọi, reflection, tên JSON hoặc cấu hình ràng buộc bên ngoài; cần rà soát trước khi đổi tên.

**Tự kiểm tra:** Người đọc có hiểu mỗi tên khi tách khỏi đoạn code không? Các đơn vị có rõ không? Có số nào chứa chính sách cần giải thích không? Các nhánh có ngoặc và thụt lề phản ánh đúng điều kiện chạy không? Nếu hai người cùng sửa file, formatter có tạo ra khác biệt không cần thiết không? Đó là những dấu hiệu thực tế của mã Java dễ bảo trì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-004">Chú thích lý do và viết tài liệu cho phương thức khi cần</a>

<details>
<summary>Xem chi tiết</summary>

# Chú thích lý do và viết tài liệu cho phương thức khi cần (Java 17+)

## 1. Mục tiêu của chú thích và tài liệu

Mã Java cần đủ rõ để người đọc hiểu **chương trình đang làm gì**; chú thích (`comment`) và tài liệu phương thức giúp giải thích những điều **khó suy ra chỉ từ mã**: lý do một quyết định được đưa ra, giả định nội bộ, giới hạn, hợp đồng với người gọi, tác dụng phụ và hành vi khi gặp lỗi.

Nguyên tắc thực hành:

- **Đặt tên biến, lớp và phương thức rõ ràng trước.** `calculateTotal()` dễ hiểu hơn `calc()`, `isEligible` dễ hiểu hơn `flag`.
- **Chú thích “vì sao” ở chỗ có quyết định không hiển nhiên**, như một ngưỡng theo nghiệp vụ hoặc lý do dùng khoảng nửa mở. Không chép lại từng dòng lệnh bằng văn xuôi.
- **Dùng Javadoc khi phương thức cần mô tả hợp đồng cho người gọi**, nhất là API public, hành vi có điều kiện, tham số có giới hạn, kết quả đặc biệt hoặc ngoại lệ cần dự đoán.
- **Giữ nội dung đúng với mã.** Chú thích sai còn nguy hiểm hơn không có chú thích: nó khiến người bảo trì tin vào hành vi không còn tồn tại.
- **Đừng thay kiểm tra và tên tốt bằng chú thích dài.** Chú thích không thực thi, không kiểm tra dữ liệu, không bảo đảm điều kiện luôn đúng.

Bài học này tập trung vào mã Java core và tài liệu ở cấp phương thức. Javadoc dùng để công bố *hợp đồng*, còn lời giải thích ở giữa thân phương thức dùng để bảo tồn *lý do thiết kế* khó nhận thấy.

## 2. Ba dạng chú thích Java

Java hỗ trợ các cú pháp chính:

| Loại | Cú pháp | Dùng khi |
| --- | --- | --- |
| Một dòng | `// nội dung` | Giải thích ngắn ngay trước một quyết định |
| Nhiều dòng | `/* nội dung */` | Ghi chú ngắn nhiều dòng; tránh dài và lặp mã |
| Javadoc | `/** nội dung */` | Viết tài liệu có cấu trúc cho lớp, phương thức, constructor hoặc thành viên thích hợp |

Ví dụ **minh họa bên trong phương thức**, chưa phải file có `main`:

```java
// Phần dư chưa đủ một bậc tích điểm không được quy đổi.
long basePoints = amountVnd / 10_000L;

/*
 * Với hợp đồng này, kết quả được giới hạn theo ngưỡng
 * đã được quyết định trước trong chính sách sản phẩm.
 */
long limitedPoints = Math.min(basePoints, 500L);
```

Trình biên dịch bỏ qua nội dung chú thích `//` và `/* ... */` khi phân tích chương trình. Tuy nhiên, trình tạo tài liệu **`javadoc`** có thể đọc các khối `/** ... */` và sinh tài liệu HTML.

Không đặt `//` trong chuỗi `"https://example.org"` rồi nghĩ nó sẽ bắt đầu chú thích: trong string literal, dấu `//` chỉ là ký tự văn bản. Không lồng `/* ... */` vào bên trong một `/* ... */` khác vì Java không hỗ trợ block comment lồng nhau.

## 3. Chú thích “vì sao”, không lặp “làm gì”

### Chú thích thừa

```java
count++;               // Tăng count lên một.
price = price * 2;     // Nhân giá với hai.
return price;          // Trả về price.
```

Các câu sau `//` chỉ diễn giải đúng cú pháp mà người đọc đã nhìn thấy. Nếu mọi dòng đều có chú thích như vậy, việc rà soát mã chậm hơn mà không tăng hiểu biết.

### Chú thích giữ lại lý do có giá trị

```java
// Chỉ tính điểm cho mỗi bậc 10.000 VND tròn;
// làm tròn lên sẽ cấp điểm cho cả phần tiền chưa đủ bậc.
long basePoints = amountVnd / 10_000L;
```

Ở đây, một phép chia nguyên có thể nhìn giống “vô tình làm mất phần thập phân”. Chú thích giải thích vì sao **phải** chia nguyên: đó là chính sách tính điểm, không phải lỗi làm tròn.

Một ví dụ khác:

```java
// Khoảng [start, end) tránh tính hai lần mốc chung
// khi ghép hai ca liên tiếp [9:00, 10:00) và [10:00, 11:00).
return minute >= startInclusive && minute < endExclusive;
```

Người đọc thấy ngay ý nghĩa biểu thức nhưng khó biết lý do dùng `< endExclusive` thay cho `<= endExclusive`. Chú thích lưu lại quyết định thiết kế để người sửa không vô tình tạo lỗi tại ranh giới.

**Kiểm tra chất lượng chú thích:** Nếu xóa chú thích mà người đọc vẫn hiểu đủ lý do từ tên hàm và biểu thức, có lẽ chú thích đó không cần thiết. Nếu thiếu chú thích khiến họ dễ sửa sai một quy tắc quan trọng, chú thích nên được giữ.

## 4. Javadoc là hợp đồng của phương thức

Javadoc bắt đầu bằng `/**`, đặt **ngay trước khai báo** muốn mô tả. Thông thường tài liệu phương thức gồm một câu mô tả ngắn, sau đó giải thích ràng buộc quan trọng, và các thẻ dành cho người gọi.

```java
/**
 * Tính điểm tích lũy theo giá trị đơn hàng.
 *
 * <p>Mỗi 10.000 VND tròn được một điểm cơ bản. Thành viên
 * nhận điểm gấp đôi khi đơn hàng đạt ít nhất 100.000 VND.
 * Kết quả không vượt 500 điểm.
 *
 * @param amountVnd giá trị đơn hàng tính bằng VND, không âm
 * @param member true nếu khách hàng là thành viên
 * @return số điểm được cấp, từ 0 đến 500
 * @throws IllegalArgumentException nếu amountVnd âm
 */
public static int calculatePoints(long amountVnd, boolean member) {
    // Phần thân phương thức được trình bày đầy đủ ở ví dụ bên dưới.
    return 0;
}
```

Đoạn trên **chỉ minh họa cấu trúc Javadoc**, phần thân trả `0` là mã minh họa hình dạng phương thức, chưa phải hiện thực của chính sách đã mô tả. Dùng phiên bản hoàn chỉnh ở phần 5 để biên dịch và chạy. Không đưa một phần thân giả như vậy vào mã sản phẩm.

Các thẻ hay dùng:

| Thẻ | Nội dung nên viết | Khi dùng |
| --- | --- | --- |
| Mô tả đầu `/** ... */` | Phương thức có ý nghĩa gì, điều kiện chính của hợp đồng | Khi phương thức cần tài liệu |
| `@param name` | Ý nghĩa, đơn vị, giới hạn của từng tham số | Khi tài liệu hóa tham số |
| `@return` | Ý nghĩa giá trị trả về, giới hạn, sentinel nếu có | Với phương thức có giá trị trả về |
| `@throws ExceptionType` | Loại ngoại lệ và điều kiện làm phát sinh | Khi caller cần biết điều kiện lỗi |
| `@see Type#method(...)` | Dẫn tới API liên quan | Khi liên kết bổ sung có ích |
| `@since` | Phiên bản API bắt đầu xuất hiện | Với API có quản lý phiên bản |
| `@deprecated` | Giải thích API cũ và lối thay thế | Khi API được ngừng khuyến nghị |

**Phân biệt `@throws` và `throws`:** `@throws` là **thẻ tài liệu** viết trong comment; `throws IOException` là **cú pháp Java** trong chữ ký để khai báo khả năng phát sinh checked exception. Viết `@throws IOException` không thay được phần `throws IOException` mà compiler yêu cầu.

Các `@param` phải khớp tên tham số thật. Nếu đổi `amountVnd` thành `orderValue`, cần sửa lại tên trong tài liệu. Với JavaDoc, các ký tự trông như HTML cần được viết cẩn thận: ưu tiên `{@code >=}`, `{@code <}` hoặc `&lt;` trong văn bản Javadoc để tránh lỗi phân tích markup. `{@code ...}` thường thích hợp để nhắc tới đoạn mã ngắn.

## 5. Chương trình Java 17+ đầy đủ: chính sách tích điểm

Tạo file **`LoyaltyPolicy.java`**:

```java
/**
 * Quy tắc tích điểm cho đơn hàng, dùng đơn vị tiền là VND.
 */
public final class LoyaltyPolicy {
    private LoyaltyPolicy() {
        // Không tạo đối tượng từ lớp tiện ích.
    }

    /**
     * Tính số điểm tích lũy được cấp cho một đơn hàng.
     *
     * <p>Mỗi 10.000 VND tròn mang lại một điểm cơ bản.
     * Thành viên nhận điểm gấp đôi từ mức 100.000 VND.
     * Tổng điểm tối đa cho một đơn là 500.
     *
     * @param amountVnd tổng tiền đơn hàng, đơn vị VND, phải không âm
     * @param member true nếu người mua là thành viên
     * @return số điểm của đơn hàng trong khoảng 0 đến 500
     * @throws IllegalArgumentException nếu amountVnd âm
     */
    public static int calculatePoints(long amountVnd, boolean member) {
        if (amountVnd < 0) {
            throw new IllegalArgumentException(
                    "amountVnd must be non-negative");
        }

        // Phần dư dưới 10.000 VND chưa đủ một bậc tích điểm.
        long basePoints = amountVnd / 10_000L;

        long awarded = member && amountVnd >= 100_000L
                ? basePoints * 2
                : basePoints;

        // Giữ trần để đơn giá trị lớn không được cấp điểm vô hạn.
        return (int) Math.min(awarded, 500L);
    }
}
```

Tạo file **`LoyaltyDemo.java`** cùng thư mục:

```java
public class LoyaltyDemo {
    public static void main(String[] args) {
        System.out.println("regular="
                + LoyaltyPolicy.calculatePoints(45_000L, false));
        System.out.println("member="
                + LoyaltyPolicy.calculatePoints(120_000L, true));
        System.out.println("capped="
                + LoyaltyPolicy.calculatePoints(3_000_000L, true));

        try {
            LoyaltyPolicy.calculatePoints(-1L, true);
        } catch (IllegalArgumentException ex) {
            System.out.println("error=" + ex.getMessage());
        }
    }
}
```

Chạy tại thư mục chứa hai file, bằng **JDK 17+** (PowerShell, CMD hoặc bash). Tạo thư mục `out` nếu chưa có:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out LoyaltyPolicy.java LoyaltyDemo.java
java -cp out LoyaltyDemo
```

**Stdout chính xác:**

```text
regular=4
member=24
capped=500
error=amountVnd must be non-negative
```

Đối chiếu với hợp đồng và chú thích:

- `45_000 / 10_000` cho `4` bậc nguyên; người mua thường nhận 4 điểm.
- `120_000 / 10_000` cho 12 điểm cơ bản; thành viên đạt ngưỡng 100.000 VND nhận 24 điểm.
- `3_000_000 / 10_000` cho 300 điểm cơ bản; nhân đôi thành 600 nhưng giới hạn 500.
- Giá trị âm bị từ chối bằng `IllegalArgumentException` đúng như thẻ `@throws`.

Phương thức `calculatePoints` **không cần khai báo `throws IllegalArgumentException`**, bởi đó là unchecked exception; thông tin ở `@throws` vẫn hữu ích vì caller biết điều kiện sai. Dùng `long` cho số tiền giúp tránh sự thiếu hụt miền giá trị của `int` trong ví dụ; phép nhân đôi ở đây không tràn `long` vì kết quả của phép chia `amountVnd / 10_000L` được chặn bởi miền của `long`.

Trong ví dụ có hai cấp tài liệu: **Javadoc** mô tả hợp đồng bên ngoài của phương thức; **chú thích trong thân** diễn giải lý do làm tròn xuống và lý do đặt trần. Không cần thêm các câu như `// kiểm tra nếu amountVnd nhỏ hơn 0` vì biểu thức `if` đã nói rõ điều đó.

## 6. Sinh tài liệu HTML từ Javadoc

Khi chương trình đã có một API cần người khác đọc, công cụ **`javadoc`** đi kèm JDK có thể tạo trang HTML từ các khối `/** ... */`:

```sh
javadoc -encoding UTF-8 -docencoding UTF-8 -d docs LoyaltyPolicy.java
```

Tùy hệ điều hành, shell và JDK, thông báo tiến trình của lệnh `javadoc` sẽ khác nhau; không có stdout cố định cần ghi nhớ. Sau khi chạy thành công, xem trang tài liệu tương ứng của `LoyaltyPolicy` trong thư mục `docs` (ví dụ `docs/LoyaltyPolicy.html`).

`-encoding UTF-8` áp dụng khi đọc source; `-docencoding UTF-8` chỉ định encoding của tài liệu được tạo. `javac` vẫn chịu trách nhiệm biên dịch mã, còn `javadoc` tạo tài liệu cho người đọc. Tài liệu HTML không bảo đảm logic đúng và không thay thế kiểm thử.

Có thể dùng `javadoc -Xdoclint:all ...` trong môi trường hỗ trợ để kiểm tra thêm nội dung và cấu trúc Javadoc. Đây là bước kiểm tra chất lượng tài liệu, không phải lời hứa rằng mọi điều kiện nghiệp vụ đã được thực thi đúng.

## 7. Tình huống cần giải thích lý do: khoảng thời gian nửa mở

Đối với bài toán ghép ca làm việc liên tiếp, dùng khoảng `[startInclusive, endExclusive)` rất hữu ích: mốc kết thúc của ca trước sẽ là mốc bắt đầu của ca tiếp theo, nhưng không bị tính vào cả hai ca.

Tạo file **`TimeWindowDemo.java`**:

```java
public class TimeWindowDemo {
    /**
     * Kiểm tra một phút có thuộc khoảng thời gian nửa mở hay không.
     *
     * <p>Khoảng có dạng [startInclusive, endExclusive), nghĩa là
     * phút đầu được tính nhưng phút cuối không được tính.
     *
     * @param minute phút cần kiểm tra, tính từ đầu ngày
     * @param startInclusive phút đầu được tính
     * @param endExclusive phút cuối không được tính
     * @return true nếu minute thuộc khoảng
     */
    static boolean contains(int minute, int startInclusive, int endExclusive) {
        // Loại trừ mốc cuối giúp hai ca giáp nhau không tính trùng.
        return minute >= startInclusive && minute < endExclusive;
    }

    public static void main(String[] args) {
        System.out.println("09:59=" + contains(599, 540, 600));
        System.out.println("10:00-before=" + contains(600, 540, 600));
        System.out.println("10:00-next=" + contains(600, 600, 660));
    }
}
```

Ở đây số phút tính từ đầu ngày: 09:00 là 540, 09:59 là 599, 10:00 là 600.

```sh
javac -encoding UTF-8 --release 17 -d out TimeWindowDemo.java
java -cp out TimeWindowDemo
```

Stdout:

```text
09:59=true
10:00-before=false
10:00-next=true
```

Chú thích có giá trị vì giúp người sửa hiểu lý do của `minute < endExclusive`. Chữ ký có tên `startInclusive`/`endExclusive` làm rõ phần “làm gì”; Javadoc bổ sung đơn vị đo và ý nghĩa kết quả.

**Giới hạn của ví dụ:** phương thức giả định các phút được truyền hợp lệ (`0`–`1439` cho `minute`; ranh giới hợp lý trong ngày cho `startInclusive` và `endExclusive`, `startInclusive <= endExclusive`). Nó chưa kiểm tra dữ liệu đầu vào. Nếu chuyển thành public API nhận dữ liệu người dùng, cần thiết kế và thực thi validation, đồng thời tài liệu hóa quy tắc lỗi; chỉ viết `@param` không tự kiểm tra giới hạn.

## 8. Khi nào cần Javadoc và khi nào có thể bỏ?

| Trường hợp | Gợi ý |
| --- | --- |
| Public API dùng bởi lớp/module/người khác | Tài liệu hóa mục đích, đơn vị, điều kiện, output, ngoại lệ và tác dụng phụ quan trọng |
| Phương thức có quy tắc nghiệp vụ khó đoán | Mô tả hợp đồng với Javadoc; ghi chú lý do ngay bên cạnh quyết định bất ngờ |
| Internal helper ngắn, tên và kiểu tự nói lên hành vi | Có thể bỏ chú thích để giảm nhiễu |
| Phương thức phức tạp có giả định hoặc thuật toán ít gặp | Ghi rõ bất biến, tiền/hậu điều kiện, lý do chọn hướng tiếp cận; thêm ví dụ khi cần |
| Override thực hiện đúng hợp đồng đã rõ ở cha/interface | Tránh sao chép tài liệu; có thể dùng `{@inheritDoc}` nếu phù hợp |
| API có tác dụng phụ, thay đổi trạng thái hay luồng thực thi | Nêu hành vi quan trọng để caller không sử dụng sai |
| API cũ đang bị thay thế | Ghi `@Deprecated` trong Java và Javadoc `@deprecated` kèm hướng chuyển đổi |
| Thử nghiệm một lần rất ngắn, tên biến rõ | Không cần viết tài liệu dài cho từng dòng |

Mục tiêu không phải đạt số dòng comment hay phần trăm bao phủ Javadoc cao nhất. Mục tiêu là người đọc **dự đoán được hợp đồng** và **không xóa mất quyết định thiết kế quan trọng** chỉ vì mã nhìn có vẻ có thể đơn giản hơn.

## 9. Viết Javadoc tốt: những điều nên nói rõ

Đối với phương thức cần tài liệu, hãy rà từng nhóm thông tin sau nếu chúng có ý nghĩa:

1. **Ý nghĩa kết quả:** ví dụ “điểm tích lũy”, không chỉ “trả về `int`” vì chữ ký đã nói kiểu.
2. **Đơn vị:** `amountVnd` là VND, `durationSeconds` là giây; không nên để người dùng tự suy ra.
3. **Miền hợp lệ:** số âm bị từ chối, `null` có được không, danh sách rỗng xử lý thế nào.
4. **Ranh giới:** khoảng đóng `[a,b]` hay nửa mở `[a,b)`, các ngưỡng `>=` và `>` khác nhau ra sao.
5. **Điều kiện lỗi:** chính xác loại ngoại lệ và tình huống xảy ra. `@throws` mô tả hành vi thực tế; không liệt kê bừa mọi ngoại lệ của Java.
6. **Tác dụng phụ:** cập nhật trạng thái, gọi I/O, sửa mutable argument, đóng tài nguyên, tác động đến thứ tự xử lý.
7. **Độ xác định:** cùng input cho cùng output không, có phụ thuộc giờ hiện tại, locale, cấu hình hay nguồn dữ liệu ngoài không.
8. **Quy tắc làm tròn hoặc sentinel:** ví dụ chia nguyên bỏ phần dư, `-1` biểu thị không tìm thấy, `Optional.empty()` biểu thị không có giá trị, nếu có.

Viết theo nhu cầu của caller. Javadoc của `calculatePoints` không cần tường thuật “khai báo biến `basePoints` rồi gọi `Math.min`”; đó là chi tiết triển khai mà người gọi không cần biết và có thể thay đổi.

## 10. Những lỗi chú thích và Javadoc thường gặp

**Chú thích lỗi thời.** Ví dụ:

```java
// Giảm giá 10%.
int discounted = price * 80 / 100;
```

Mã hiện đang giảm 20%, còn chú thích nói 10%. Khi thay đổi quy tắc, cần cập nhật cả mã, test và tài liệu; tốt hơn là đặt tên hằng như `DISCOUNT_PERCENT` để tránh nhiều nơi nêu lại giá trị.

**Chú thích mơ hồ:** `// fix bug`, `// logic khó`, `// xử lý đặc biệt` không giúp người khác. Nên giải thích điều kiện cụ thể và hệ quả nếu bỏ quyết định đó. Không dùng chú thích để giữ lại một khối mã cũ bị vô hiệu hóa; lịch sử Git phù hợp hơn.

**Javadoc trái với hành vi:** ghi `@return null if missing` trong khi mã ném ngoại lệ, hoặc ghi `@throws IOException` nhưng thực tế không có đường thực thi nào ném loại đó theo hợp đồng, gây hiểu lầm. Viết tài liệu sau khi đã thống nhất hành vi và kiểm tra lại sau sửa đổi.

**Lặp nghĩa của tên:** `/** Gets customer. */ Customer getCustomer()` hầu như không thêm thông tin. Nếu getter chỉ trả trường tương ứng, API nội bộ đơn giản có thể không cần giải thích riêng; nếu trả bản sao phòng vệ hoặc đọc dữ liệu từ xa thì nên nói rõ điều đó.

**Sai tên thẻ:** `@param amount` khi phương thức khai báo `amountVnd`; hoặc `@return` cho phương thức `void`. Chạy `javadoc`/doclint để phát hiện một số vấn đề cấu trúc.

**Lẫn Javadoc với chú thích bình thường:** `// @param value ...` không tạo thẻ `@param` trong tài liệu sinh ra; thẻ cần nằm trong khối Javadoc. Mã Java vẫn biên dịch nếu chú thích chưa hoàn chỉnh, nhưng tài liệu cho caller không vì thế mà tự đúng.

**Nhúng ký hiệu markup sai:** trong Javadoc, dấu `<`, `>`, `&` có thể liên quan cú pháp HTML. Dùng `{@code ...}` hoặc escape phù hợp khi muốn hiển thị mã và toán tử. Đừng đặt ví dụ Java nhiều dòng tùy tiện trong HTML khiến trang tài liệu bị vỡ; dùng cấu trúc hỗ trợ và kiểm tra trang kết quả.

**Viết quá dài cho hàm quá nhỏ:** nội dung thừa khó đồng bộ. Nếu một tên hàm rõ nghĩa cùng kiểu dữ liệu đủ truyền đạt hợp đồng, vài câu giải thích trọng yếu sẽ tốt hơn một trang lặp lại mã.

## 11. Quy trình bảo trì chú thích cùng mã

Khi thay đổi phương thức:

1. Đọc tên, chữ ký, Javadoc và các comment gần vùng mã cần sửa; xác định **hợp đồng hiện tại**.
2. Thay đổi code và kiểm thử theo yêu cầu; giữ rõ cái gì thay đổi ở đầu vào, tính toán, kết quả, ngoại lệ.
3. Cập nhật mô tả, các thẻ `@param`/`@return`/`@throws`, ngưỡng/đơn vị và comment “vì sao” nếu quyết định đã đổi.
4. Loại bỏ chú thích thừa hoặc lỗi thời; đừng để comment mâu thuẫn với biểu thức.
5. Chạy `javac`, kiểm tra stdout/test và sinh `javadoc` khi API/tài liệu liên quan bị sửa.
6. Nhờ người đọc khác nhìn tài liệu **trước khi xem phần thân**: họ có dự đoán đúng kết quả và lỗi không?

Một review tốt không chỉ hỏi “comment đã có chưa” mà hỏi “comment còn đúng không, và có giải thích lý do mà tên/mã chưa thể hiện không?”.

## 12. Tự kiểm tra

**1. Chú thích `// tăng i lên một` sau `i++` có cần không?** Thường không; nó lặp đúng thao tác đã hiển thị.

**2. Vì sao nên ghi chú khi dùng khoảng `[start, end)`?** Để giải thích tránh đếm trùng mốc chung giữa các khoảng liên tiếp, chứ không phải chỉ nhắc `<` nghĩa là “nhỏ hơn”.

**3. Nếu phương thức có tham số `amountVnd` nhưng Javadoc ghi `@param amount`, lỗi gì?** Tên trong tài liệu không khớp chữ ký; cập nhật thẻ, dùng `javadoc`/doclint để rà soát.

**4. `@throws IllegalArgumentException` có tự ném ngoại lệ khi số âm không?** Không. Nó chỉ mô tả hợp đồng; nhánh `throw new IllegalArgumentException(...)` trong mã mới thực sự báo lỗi.

**5. Khi API thay đổi ngưỡng từ 100.000 lên 200.000 VND, cần sửa gì?** Quy tắc ở mã, các bài test và mọi Javadoc/chú thích mô tả ngưỡng. Nếu không, tài liệu sẽ gây hiểu lầm.

**6. `javac` có tự sinh trang HTML từ `/** ... */` không?** Không. Công cụ `javadoc` được dùng để sinh tài liệu, còn `javac` biên dịch chương trình.

**Ghi nhớ:** Hãy để tên và cấu trúc mã giải thích *làm gì*; comment nên lưu lại *vì sao*, còn Javadoc nên cho người gọi biết *hợp đồng* — dữ liệu hợp lệ, kết quả, ngoại lệ và giới hạn. Viết khi có giá trị, rồi duy trì cùng mỗi lần thay đổi hành vi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-005">Gỡ lỗi bằng breakpoint, chạy từng bước và xem giá trị biến</a>

<details>
<summary>Xem chi tiết</summary>

# Gỡ lỗi bằng breakpoint, chạy từng bước và xem giá trị biến trong Java

## 1. Gỡ lỗi (debugging) là gì?

**Debugging** là quá trình xác định nguyên nhân chương trình hoạt động sai và kiểm tra cách sửa. Chương trình có thể **biên dịch thành công** nhưng vẫn tính sai, đi nhầm nhánh `if`, bỏ sót phần tử hoặc ném ngoại lệ khi chạy. Công cụ **debugger** cho phép quan sát trạng thái thực tế của chương trình tại từng thời điểm, thay vì chỉ đoán nguyên nhân từ kết quả cuối.

Ba kỹ năng nền tảng:

- **Đặt breakpoint:** yêu cầu debugger tạm dừng một luồng khi tới vị trí hoặc điều kiện đã chọn.
- **Chạy từng bước:** kiểm soát lệnh tiếp theo được thực hiện, có thể bước qua hoặc đi vào lời gọi phương thức.
- **Xem giá trị biến:** kiểm tra biến cục bộ, tham số, trường đối tượng, biểu thức theo dõi (*watch*) và ngăn xếp lời gọi (*call stack*) tại thời điểm chương trình đang dừng.

Debugger giúp bạn **quan sát lỗi**, không tự chứng minh thuật toán đúng. Sau khi sửa vẫn cần biên dịch, chạy test và kiểm tra các trường hợp biên.

## 2. Breakpoint là gì, đặt ở đâu?

**Breakpoint** là điểm dừng được cấu hình trong debugger. Khi thực thi đạt đến điểm đó, debugger thường **tạm dừng** trước khi thực thi dòng tương ứng và cho phép xem trạng thái chương trình tại vị trí hiện tại. Cách debugger hiển thị vị trí và thời điểm dừng có thể khác khi một dòng có nhiều biểu thức, mã được tối ưu hoặc không có thông tin debug phù hợp.

Cách đặt trong những IDE phổ biến như IntelliJ IDEA, Eclipse hoặc Visual Studio Code (với Java debugging extension):

1. Mở tệp `.java` chứa mã muốn kiểm tra.
2. Nhấp vào phần lề cạnh **dòng có câu lệnh thực thi** để bật breakpoint; biểu tượng thường là một chấm màu.
3. Chạy bằng **Debug**, không phải chỉ **Run**, để debugger quản lý các điểm dừng.
4. Khi dừng, xem dòng hiện tại, các biến trong scope và **Call Stack**.
5. Dùng các điều khiển như Continue/Resume, Step Over, Step Into hoặc Step Out để tiếp tục.

**Nên đặt ở nơi trạng thái có thể đổi hoặc điều kiện quyết định hành vi**, ví dụ:

- Ngay trước `if (value > 0)`: giá trị nào sẽ đi vào nhánh?
- Dòng `sum += value;`: `sum` trước và sau khi cộng là bao nhiêu?
- Dòng `count++;`: có đang đếm đúng loại phần tử không?
- Ngay trước `return`: các biến tổng hợp có đúng kết quả kỳ vọng?

Breakpoint chỉ quan sát được khi luồng thực thi thật sự tới vị trí đó. Nếu đặt trong một nhánh không bao giờ chạy với dữ liệu hiện tại, chương trình sẽ không dừng ở đó. Breakpoint thường cần được đặt trên **dòng có mã thực thi**; không thể kỳ vọng đặt breakpoint trên một dòng chú thích hoặc dòng trống sẽ luôn có tác dụng.

## 3. Các thao tác Step Over, Step Into, Step Out và Resume

| Điều khiển | Ý nghĩa khi đang dừng | Khi nên sử dụng |
| --- | --- | --- |
| **Step Over** | Thực thi bước hiện tại rồi dừng ở bước tiếp theo phù hợp trong ngữ cảnh hiện tại, **không đi sâu vào phương thức được gọi** | Muốn biết một lệnh làm thay đổi biến thế nào mà không cần xem nội bộ lời gọi |
| **Step Into** | Bước vào phần mã của phương thức được gọi khi debugger có thể truy cập được | Nghi ngờ lỗi nằm trong phương thức tự viết như `analyze(...)` |
| **Step Out** | Tiếp tục thực thi đến khi thoát khỏi phương thức hiện tại, quay lại ngữ cảnh gọi khi có thể | Đã hiểu logic bên trong phương thức, muốn xem kết quả ở nơi gọi |
| **Resume/Continue** | Tiếp tục chạy cho tới breakpoint tiếp theo, điểm dừng do lỗi/sự kiện khác hoặc kết thúc | Không cần quan sát từng lệnh |
| **Stop/Terminate** | Kết thúc phiên debug hoặc chương trình theo cách công cụ hỗ trợ | Muốn hủy phiên hiện tại; không giống Resume |

Ví dụ:

```java
int result = computeTotal(values);
System.out.println(result);
```

Đây là **đoạn minh họa**, giả sử `computeTotal` là phương thức do bạn định nghĩa. Nếu đang dừng ở lệnh gọi:

- **Step Into** có thể đưa bạn vào mã `computeTotal` để quan sát tính toán.
- **Step Over** chạy lời gọi rồi chuyển sang bước tiếp theo ở phương thức hiện tại, trong khi kết quả `result` đã được gán nếu lời gọi hoàn tất bình thường.
- Nếu đã vào `computeTotal`, **Step Out** cho phép trở lại nơi gọi sau khi phương thức kết thúc.

Đừng nhầm **Step Over** là “bỏ qua không chạy lệnh”: lệnh vẫn được thực thi. Tương tự, debugger có thể có **Step Into** nhiều biến thể như Smart Step Into; tên và phím tắt phụ thuộc IDE. Khi một bước có ngoại lệ hoặc breakpoint khác, điểm dừng tiếp theo có thể không giống đường đi bình thường.

## 4. Xem Locals, Watches và Call Stack

Khi debugger tạm dừng, một số khung thông tin quan trọng là:

| Khung / chức năng | Bạn xem được gì? | Ví dụ cần kiểm tra |
| --- | --- | --- |
| **Variables/Locals** | Tham số và biến cục bộ hiện nằm trong scope của khung gọi đang chọn | `i`, `value`, `count`, `sum`, `max` |
| **Watch/Expressions** | Giá trị biểu thức muốn theo dõi tại vị trí dừng | `value > 0`, `sum + value`, `count == 0` |
| **Call Stack** | Chuỗi lời gọi phương thức dẫn tới điểm dừng | `main → printReport → analyze` |
| **Breakpoints** | Danh sách điểm dừng, điều kiện hoặc trạng thái bật/tắt | Điểm dừng có đang được bật? |
| **Debug Console/Evaluate** | Đánh giá biểu thức trong bối cảnh đang dừng, nếu debugger hỗ trợ | `nums.length`, `sum`, `i + 1` |

**Giá trị biến gắn với thời điểm dừng.** Nếu debugger dừng **trước** câu `sum += value;`, `sum` thường chưa bao gồm `value` hiện tại. Sau khi Step Over dòng đó, `sum` mới phản ánh phép cộng. Vì vậy khi so với bảng theo dõi, hãy ghi rõ **trước hay sau lệnh**.

**Phạm vi biến (scope)** cũng quan trọng. Biến `value` được khai báo trong thân vòng lặp không nhất thiết có thể xem được khi đã rời vòng lặp. Biểu thức watch của một biến ngoài scope có thể báo lỗi hoặc không có giá trị, không có nghĩa bản thân biến đã bị đặt về `null`.

Trong debugger, việc Evaluate một lời gọi phương thức hoặc sửa trực tiếp giá trị biến **có thể làm thay đổi trạng thái thực thi**. Khi đang học, nên ưu tiên biểu thức chỉ đọc, ví dụ `i < nums.length` hoặc `sum + value`. Không dùng biểu thức có tác dụng phụ như `list.remove(0)` hoặc `i++` trong cửa sổ Evaluate nếu không chủ đích thay đổi chương trình.

## 5. Đầu vào chuẩn để đối chiếu khi debug

Bài thực hành dùng `int[] nums = {4, -2, 7, 0, 3}`: đếm, cộng và tìm giá trị lớn nhất **chỉ trong các phần tử dương**. `-2` và `0` phải đi qua nhánh bỏ qua; nếu không có số dương thì không tính trung bình hoặc in giá trị `max` giữ chỗ.

Kết quả chạy tay cần đối chiếu khi dừng bằng debugger: `count = 3`, `sum = 14`, `max = 7` và `average = 4.67` (định dạng hai chữ số). Bảng trace của bài mã giả giải thích cách tính các kết quả này; ở đây trọng tâm chuyển sang **quan sát trạng thái ngay trước và sau từng lệnh** để biết bước nào khiến kết quả lệch.

### Chương trình Java 17+ độc lập

Tạo tệp **`DebugStepDemo.java`**:

```java
import java.util.Locale;
import java.util.Objects;

public class DebugStepDemo {
    record Stats(int count, long sum, int max) {}

    static Stats analyze(int[] nums) {
        Objects.requireNonNull(nums, "nums");

        int count = 0;
        long sum = 0;
        int max = 0;

        for (int i = 0; i < nums.length; i++) {
            int value = nums[i]; // BREAKPOINT A: quan sát i và value

            if (value > 0) {
                if (count == 0 || value > max) {
                    max = value;
                }
                sum += value;     // BREAKPOINT B: xem sum trước/sau
                count++;         // BREAKPOINT C: xem count trước/sau
            }
        }

        return new Stats(count, sum, max); // BREAKPOINT D
    }

    static void printReport(String title, int[] values) {
        Stats result = analyze(values);    // BREAKPOINT E
        System.out.println(title);
        System.out.println("count=" + result.count());
        System.out.println("sum=" + result.sum());

        if (result.count() == 0) {
            System.out.println("no positive numbers");
        } else {
            System.out.println("max=" + result.max());
            double average = result.sum() / (double) result.count();
            System.out.printf(Locale.US, "average=%.2f%n", average);
        }
    }

    public static void main(String[] args) {
        printReport("Case A", new int[] {4, -2, 7, 0, 3});
        printReport("Case B", new int[] {0, -1});
    }
}
```

Dùng **JDK 17 trở lên**. Từ terminal tại thư mục có file:

```bash
javac -g -encoding UTF-8 DebugStepDemo.java
java DebugStepDemo
```

Tùy chọn **`-g`** yêu cầu `javac` sinh thông tin debug như dòng nguồn và biến cục bộ, giúp debugger quan sát tốt hơn; nó không thay đổi kết quả thuật toán.

**Stdout chính xác khi chạy bình thường:**

```text
Case A
count=3
sum=14
max=7
average=4.67
Case B
count=0
sum=0
no positive numbers
```

`record Stats` là cách gói kết quả ngắn gọn có sẵn trong Java 17; các accessor là `count()`, `sum()`, `max()`. Biến `max = 0` chỉ là giá trị giữ chỗ khi chưa gặp số dương, không được coi là kết quả có nghĩa nếu `count == 0`.

## 6. Thực hành một phiên debug theo trình tự

Mở `DebugStepDemo.java` trong IDE có hỗ trợ Java debugger, bảo đảm dự án dùng **JDK 17+** và chạy **Debug**.

### Bước 1 — Dừng tại nơi gọi phương thức

Đặt breakpoint E tại:

```java
Stats result = analyze(values);
```

Lần dừng đầu xảy ra trong lời gọi `printReport("Case A", ...)`. Trên cửa sổ Variables, có thể xem `title = "Case A"` và `values = [4, -2, 7, 0, 3]`. Biến `result` chưa có kết quả được gán tại thời điểm dừng trước lời gọi.

Bấm **Step Into** để vào `analyze(values)`. Khi đã vào, chọn khung gọi `analyze` sẽ thấy tham số `nums` và, sau khi thực hiện các lệnh khởi tạo, các biến `count`, `sum`, `max`. Ban đầu chúng lần lượt bằng `0`, `0`, `0`.

### Bước 2 — Dừng ở đầu mỗi lần lặp

Đặt breakpoint A tại `int value = nums[i];`. Ở lần lặp đầu tiên, `i = 0`; nếu debugger dừng **trước** câu lệnh này, `value` chưa được gán cho vòng lặp hiện tại. Step Over một lần để thực thi phép gán rồi xem `value = 4`.

Thêm các watch chỉ đọc:

```text
i
nums.length
value
value > 0
count
sum
max
```

Khi `value` đã ở trong scope, watch `value > 0` phải là `true` cho `4` và `7`, nhưng `false` cho `-2` và `0`.

### Bước 3 — Theo dõi cập nhật trạng thái

Khi `value = 4`, đi vào nhánh `if`. Trước `sum += value;`, `sum = 0`; sau khi Step Over, `sum = 4`. Trước `count++;`, `count = 0`; sau khi Step Over, `count = 1`. Vì `count == 0` khi xử lý số dương đầu tiên, `max` được gán thành `4`.

Nếu nhấn **Resume** và dừng lại ở breakpoint A ở lần lặp tiếp theo, `i = 1` và `nums[1] = -2`. Khi điều kiện `value > 0` sai, các biến tổng hợp không thay đổi: `count = 1`, `sum = 4`, `max = 4`.

### Bước 4 — Kiểm chứng trạng thái **trước và sau breakpoint**

Thay vì chỉ đọc bảng kết quả cuối mỗi lượt, hãy quan sát **thời điểm dừng của debugger**. Với `nums = {4, -2, 7, 0, 3}`, bảng dưới chọn những checkpoint có ý nghĩa trong chương trình `DebugStepDemo`:

| Vị trí dừng | Điều đang/đã xảy ra | Giá trị cần thấy |
| --- | --- | --- |
| A, `i=0`, trước `int value = nums[i]` | Phần tử đầu chưa được gán cho local `value` ở lượt này | `i=0`, `count=0`, `sum=0`; **không đánh giá** watch `value` trước khi gán |
| B, `i=0`, trước `sum += value` | `value=4`, `max` đã thành 4; phép cộng chưa chạy | `count=0`, `sum=0`, `max=4` |
| C, `i=0`, trước `count++` | Phép cộng vừa thực hiện; bộ đếm chưa tăng | `sum=4`, `count=0`; Step Over → `count=1` |
| A, `i=1`, sau gán `value=-2` | Điều kiện `value > 0` sai, bỏ qua B và C | Sau lượt này: `count=1`, `sum=4`, `max=4` |
| B, `i=2`, `value=7` | Đã cập nhật `max=7` nhưng chưa cộng 7 | Trước B: `sum=4`, `count=1`; sau B: `sum=11` |
| A, `i=3`, `value=0` | Không vào nhánh số dương | Sau lượt này: `sum=11`, `count=2`, `max=7` |
| B/C, `i=4`, `value=3` | `max` giữ 7; cộng rồi mới tăng đếm | Trước B: `sum=11`, `count=2`; sau C: `sum=14`, `count=3` |
| D, trước `return new Stats(...)` | Mọi lượt đã hoàn thành | `count=3`, `sum=14`, `max=7` |

**Lưu ý về thời điểm:** Nếu breakpoint A tạm dừng *trước* lệnh gán, local `value` có thể chưa được gán cho lượt hiện tại. Chỉ kiểm tra `value > 0` khi đã Step Over qua phép gán hoặc dừng ở lệnh `if`. Các điểm B/C không được chạm tới ở lượt `i=1` và `i=3`, vì điều kiện sai; đó là dấu hiệu đúng, không phải breakpoint bị lỗi.

Ở điểm D, dùng **Step Out** để quay về `printReport`, kiểm tra `result` sau khi `analyze` trả về và xác nhận trung bình `14 / 3.0` được in là `4.67`. Bảng này bổ sung góc nhìn **trạng thái trước/sau lệnh** cho bảng chạy tay; không thay thế việc kiểm thử toàn bộ đầu vào biên.

### Bước 5 — Quan sát trường hợp không có số dương

Resume cho tới lời gọi `printReport("Case B", new int[] {0, -1})`. Ở trường hợp này, `value > 0` luôn sai, các biến `count`, `sum` giữ `0`; chương trình đi vào nhánh `if (result.count() == 0)` và in `no positive numbers`, không thực hiện phép chia.

Cách kiểm tra trường hợp biên giúp xác nhận rằng chương trình không chỉ đúng với một mảng “đẹp”.

## 7. Dùng breakpoint có điều kiện để tránh dừng quá nhiều

Trong vòng lặp có hàng nghìn phần tử, breakpoint thông thường có thể làm chương trình dừng hàng nghìn lần. **Conditional breakpoint** chỉ yêu cầu tạm dừng khi một biểu thức điều kiện đúng.

Ví dụ với chương trình trên:

- Điều kiện `i == 2`: dừng khi chuẩn bị xử lý phần tử thứ ba.
- Điều kiện `value == 7`: dừng tại một vị trí mà biến `value` đã được gán và đang nằm trong scope.
- Điều kiện `count > 1`: tập trung vào trạng thái sau khi đã gặp nhiều số dương; cần đặt breakpoint tại dòng thích hợp để hiểu `count` trước/sau cập nhật.

**Cẩn thận với vị trí:** nếu đặt breakpoint ngay tại `int value = nums[i];`, biến `value` của vòng lặp hiện tại có thể **chưa có giá trị hợp lệ để dùng cho điều kiện**. Khi đó dùng điều kiện `i == 2` tại dòng này; hoặc đặt breakpoint ở dòng `if (value > 0)` để kiểm tra `value == 7` sau khi phép gán đã thực hiện.

Một số IDE hỗ trợ **logpoint/tracepoint**: ghi một thông báo mà không luôn dừng chương trình. Đây là lựa chọn khi cần quan sát nhiều lần lặp, nhưng nếu muốn nhìn kỹ trạng thái ở một thời điểm thì breakpoint tạm dừng vẫn trực quan hơn.

Trong chương trình nhiều luồng, breakpoint có thể tạm dừng **một luồng hoặc nhiều luồng** tùy cấu hình debugger. Đừng giả định các luồng khác cũng đã dừng; xem chế độ suspend và cửa sổ Threads của công cụ khi cần phân tích lỗi đồng thời.

## 8. Ví dụ lỗi logic: tìm dòng sai bằng debugger

Một phiên bản chưa đúng của vòng lặp:

```java
for (int i = 0; i < nums.length - 1; i++) {
    int value = nums[i];
    if (value > 0) {
        sum += value;
        count++;
    }
}
```

Đây là **đoạn minh họa lỗi**, không phải toàn bộ tệp Java. Với mảng `{4, -2, 7, 0, 3}`, điều kiện `i < nums.length - 1` chỉ cho chạy `i = 0, 1, 2, 3`; phần tử cuối ở `i = 4` bị bỏ sót. Nếu chỉ dựa vào câu `sum += value`, người mới học có thể tưởng lỗi nằm ở phép cộng.

Cách dùng debugger:

1. Đặt breakpoint trong vòng lặp, theo dõi `i`, `nums.length`, `value`, `sum`, `count`.
2. Resume từng lần lặp hoặc đặt breakpoint có điều kiện ở gần lần lặp cuối.
3. Nhận thấy giá trị `i` **không bao giờ đạt `4`**, dù `nums.length = 5`.
4. Kiểm tra điều kiện vòng lặp và đổi thành `i < nums.length`.
5. Chạy lại, xác nhận tổng là `14` và số phần tử dương là `3`.

Với lỗi cập nhật sai nhánh, ví dụ đặt `count++` **ngoài** `if (value > 0)`, breakpoint sau câu tăng sẽ cho thấy `count` thay đổi ngay cả với `value = -2` hoặc `0`. Kiểm tra bảng theo dõi sẽ chỉ ra bước đầu tiên khiến trạng thái sai.

**Bài học:** đặt breakpoint dựa trên **giả thuyết về nguyên nhân**, quan sát trạng thái **trước và sau** một lệnh, sau đó sửa lệnh gây sai thay vì che triệu chứng bằng một giá trị kết quả được gán cứng.

## 9. Call Stack giúp hiểu chương trình đang ở đâu

Ở breakpoint A/B/C nằm trong `analyze`, Call Stack của một lần chạy thông thường có thể cho thấy thứ tự lời gọi:

```text
DebugStepDemo.analyze(...)
DebugStepDemo.printReport(...)
DebugStepDemo.main(...)
```

Khung trên cùng là phương thức hiện tại. Chọn một khung khác có thể hiển thị các biến cục bộ ở **ngữ cảnh của phương thức đó**, không phải giá trị của cùng tên biến trong mọi phương thức. Khi đang bước trong `analyze`, `nums` là tham số của `analyze`; ở `printReport`, tham số tên `values`. Đó là hai tên biến cục bộ khác nhau đang tham chiếu tới cùng đối tượng mảng trong ví dụ.

Khi có exception, xem stack trace hoặc bật **exception breakpoint** để tạm dừng khi một loại ngoại lệ được ném (hoặc chưa được bắt, tùy tùy chọn IDE). Exception breakpoint hữu ích khi lỗi bị bắt ở tầng cao hơn và thông báo cuối cùng không cho biết nơi phát sinh đầu tiên.

Cần phân biệt **call stack** với nội dung mảng/collection: call stack cho biết chuỗi lời gọi; Variables cho biết dữ liệu hiện có ở từng khung.

## 10. Chạy từ terminal và kết nối debugger (tùy chọn)

Chạy bình thường bằng `java DebugStepDemo` sẽ **không tự dừng** tại breakpoint do IDE cấu hình. Muốn debug trong IDE, dùng chức năng **Debug** hoặc cấu hình debugger **Attach** đến JVM đã bật giao thức JDWP.

Ví dụ với **JDK 17+**, sau khi biên dịch bằng `javac -g`:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005 DebugStepDemo
```

Ý nghĩa:

- `transport=dt_socket`: dùng kết nối socket để debugger giao tiếp.
- `server=y`: JVM mở đầu nhận kết nối debugger.
- `suspend=y`: JVM tạm dừng khi khởi động để chờ debugger kết nối trước khi thực thi ứng dụng.
- `address=127.0.0.1:5005`: chỉ lắng nghe trên **loopback** của máy đó, tại cổng 5005.

Trong IDE, tạo cấu hình **Attach to Remote JVM/Remote Debug** kết nối `127.0.0.1:5005`, trỏ đến đúng mã nguồn và class đã biên dịch; sau khi attach và tiếp tục, debugger có thể dừng tại breakpoint. Chế độ này **không phải stdout thông thường của ví dụ** vì JVM có thể in một dòng thông báo đang lắng nghe và dừng chờ kết nối.

**An toàn:** cổng JDWP cho phép điều khiển sâu tiến trình Java; không mở công khai qua Internet hoặc địa chỉ mạng không tin cậy, đặc biệt không dùng `address=*:5005` nếu không có biện pháp bảo vệ phù hợp. Với người mới học, debug trực tiếp bằng IDE thường đơn giản hơn và tránh phải tự quản lý cổng.

## 11. Những điều debugger không thay thế được

- **Không có breakpoint nào được kích hoạt:** kiểm tra đã chạy chế độ Debug chưa, đặt tại dòng thực thi chưa, đường đi có tới dòng ấy không, breakpoint có bị tắt không, source và class đang chạy có khớp nhau không.
- **Không xem được biến cục bộ:** kiểm tra đúng Call Stack frame, phạm vi của biến, và thử biên dịch với `javac -g`. Một số mã/thư viện hoặc môi trường tối ưu có thể hạn chế thông tin.
- **Step Into đi vào mã thư viện thay vì mã tự viết:** dùng Step Over, Smart Step Into hoặc các bộ lọc bước (*step filters*) nếu IDE hỗ trợ.
- **Breakpoint trong loop dừng quá thường xuyên:** dùng conditional breakpoint hoặc logpoint.
- **Kết quả thay đổi khi dùng debugger:** debugger có thể làm thay đổi thời gian thực thi, nhất là trong code đa luồng; tránh kết luận một lỗi race condition đã biến mất chỉ vì chạy từng bước.
- **Watch hiển thị lỗi scope:** đặt biểu thức tại dòng/khung mà biến thực sự tồn tại.
- **Debugger cho thấy kết quả đúng ở một đầu vào:** vẫn phải thử mảng rỗng, chỉ số biên, đầu vào sai, số lượng lớn và các tình huống đặc biệt.
- **Debug Console Evaluate làm đổi trạng thái:** hạn chế biểu thức có tác dụng phụ hoặc can thiệp dữ liệu khi mục đích chỉ là quan sát.

### Checklist gỡ lỗi nhanh

1. Mô tả **kết quả mong đợi** và **kết quả đang xảy ra**.
2. Tạo dữ liệu đầu vào nhỏ **tái hiện được lỗi**.
3. Đặt breakpoint **trước vị trí nghi ngờ**, chạy Debug.
4. Xem Locals/Watch/Call Stack, phân biệt giá trị **trước và sau lệnh**.
5. Step Into khi cần xem nội bộ, Step Over khi chỉ cần kết quả, Step Out khi muốn quay về nơi gọi, Resume đến điểm quan trọng tiếp theo.
6. So với bảng theo dõi hoặc phép tính tay để xác định **bước đầu tiên bị lệch**.
7. Sửa **nguyên nhân**, chạy lại test bình thường và trường hợp biên.
8. Tắt hoặc bỏ những breakpoint không cần thiết sau khi hoàn tất.

**Ghi nhớ:** breakpoint giúp dừng đúng thời điểm; chạy từng bước giúp thấy lệnh nào thay đổi trạng thái; Variables/Watch/Call Stack giúp trả lời “giá trị nào đang sai, ở đâu và do đường gọi nào?”. Kết hợp quan sát bằng debugger với phép tính tay và kiểm thử sẽ giúp tìm lỗi Java chính xác hơn việc thêm nhiều dòng `println()` một cách ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-006">Kiểm tra trường hợp rỗng, biên, số âm, dữ liệu sai và giá trị lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra trường hợp rỗng, biên, số âm, dữ liệu sai và giá trị lớn trong Java

Kiểm thử không chỉ xác nhận đầu vào thông thường chạy đúng. Một chương trình Java đáng tin cậy còn phải có **hành vi được xác định rõ** khi không có dữ liệu, dữ liệu nằm sát giới hạn, xuất hiện số âm, sai định dạng hoặc vượt khả năng biểu diễn của kiểu số. Những ca này cần được thiết kế từ **hợp đồng của chương trình** (đầu vào được chấp nhận, giới hạn, kết quả và lỗi mong đợi), thay vì thử các con số tùy ý.

Bài này dùng Java 17+ và một ví dụ xử lý **số lượng sản phẩm** hợp lệ trong đoạn `0..1_000_000`. Quy tắc minh họa: giá trị `0` hợp lệ; giá trị âm và lớn hơn `1_000_000` bị từ chối; đầu vào phải là một chuỗi biểu diễn số nguyên `int` hợp lệ, không tự cắt khoảng trắng. Với nghiệp vụ khác, các giới hạn có thể khác; cần nêu rõ trước khi viết test.

## 1. Phân loại đầu vào và đặt kết quả mong đợi

| Nhóm | Ví dụ đầu vào | Cần xác nhận |
| --- | --- | --- |
| Thiếu dữ liệu | `null` đối với tham chiếu `String` | Báo lỗi thiếu giá trị; không gọi phương thức của `null` |
| Chuỗi rỗng | `""` | Báo lỗi rõ ràng, không cố phân tích thành số |
| Chỉ khoảng trắng | `"   "` | Phân biệt với chuỗi chứa số hợp lệ |
| Cận dưới và lân cận | `-1`, `0`, `1` | `-1` bị từ chối, `0` và `1` hợp lệ |
| Cận trên và lân cận | `999999`, `1000000`, `1000001` | Hai số đầu hợp lệ, số cuối vượt giới hạn |
| Sai định dạng | `"12x"`, `"3.5"`, `" 5 "` | Không âm thầm chuyển thành dữ liệu khác; thông báo nhất quán |
| Ngoài miền biểu diễn `int` | `"2147483648"` | `Integer.parseInt` báo `NumberFormatException`; chương trình phải xử lý |
| Tính toán giá trị lớn | Giá `50_000` × số lượng `1_000_000` | Kết quả `50_000_000_000` cần `long` hoặc kiểu phù hợp, không được tràn `int` |

**Rỗng không có một nghĩa duy nhất:** `null` là không có đối tượng; `""` là `String` có độ dài 0; `"   "` không rỗng theo `isEmpty()` nhưng `isBlank()` trả `true`. Một danh sách rỗng (`List.isEmpty()`) hay mảng có `length == 0` lại là dữ liệu có cấu trúc, cần quy tắc kiểm tra riêng. Với kiểu nguyên thủy `int`, bản thân biến không nhận `null`; vấn đề thiếu dữ liệu thường xuất hiện trước khi chuyển đổi từ chuỗi hoặc qua `Integer`.

Không nên tự coi giá trị `0` là “chưa nhập”. Nó có thể là số lượng hợp lệ. Nếu muốn cấm `0`, hãy ghi thành quy tắc `1..MAX` và đổi toàn bộ test tương ứng.

## 2. Kiểm thử biên có hệ thống

Nếu miền hợp lệ là **`MIN <= x <= MAX`**, các ca có giá trị học tập cao gồm:

- `MIN - 1` (ngoài miền), `MIN` (trúng cận), `MIN + 1` (sát trong miền).
- `MAX - 1` (sát trong miền), `MAX` (trúng cận), `MAX + 1` (ngoài miền).
- Một giá trị bình thường nằm giữa miền, ví dụ `42`, để xác nhận đường đi chính.

Trong ví dụ `MIN=0`, `MAX=1_000_000`, các giá trị là `-1, 0, 1, 999999, 1000000, 1000001`. Hãy ghi rõ cận **bao gồm** (`<=`) hay **không bao gồm** (`<`); một ký tự toán tử sai có thể đổi kết quả ở đúng vị trí biên. Với phép tính cận lớn như `Integer.MAX_VALUE + 1`, không dùng phép cộng `int` khiến chính giá trị test bị tràn; chuẩn bị dữ liệu bằng `long`, `String` hoặc `BigInteger` phù hợp.

Kiểm tra số âm chỉ có ý nghĩa khi đầu vào có thể biểu diễn số âm. Nếu dữ liệu là tuổi, số lượng hay thời gian không âm, hành vi có thể là từ chối với lỗi xác định; nếu dữ liệu là nhiệt độ Celsius, số âm lại có thể hợp lệ. Vì vậy, **yêu cầu nghiệp vụ quyết định kết quả mong đợi**.

## 3. Dữ liệu sai và ngoại lệ

Khi đầu vào dạng chữ cần thành `int`, `Integer.parseInt(raw)` ném `NumberFormatException` nếu không thể phân tích hoặc giá trị vượt miền `int` (`-2147483648` đến `2147483647`). Đây là ngoại lệ con của `IllegalArgumentException`.

Một thiết kế kiểm tra rõ ràng thường đi theo trình tự:

1. Kiểm tra tham chiếu `null` và dữ liệu rỗng/trắng.
2. Phân tích đúng định dạng. Quyết định **trước** việc có `trim()`/chuẩn hóa dấu cách hay không; ví dụ này cố ý từ chối `" 5 "`.
3. Kiểm tra quy tắc miền giá trị: âm, vượt giới hạn, cận đóng/mở.
4. Chỉ thực hiện nghiệp vụ khi đầu vào đã hợp lệ; báo lỗi đủ rõ để nơi gọi xử lý.

Phân biệt dữ liệu **không hợp lệ theo định dạng** (`"12x"`) và **hợp lệ về số học nhưng sai chính sách** (`"1000001"`). Không dùng `catch (Exception)` rồi trả giá trị mặc định `0`: cách đó dễ che mất lỗi và khiến kết quả sai trông như thành công. Với ứng dụng thật, có thể trả kết quả lỗi cấu trúc hoặc ném ngoại lệ rồi xử lý ở lớp giao diện/API.

Lưu ý: dấu gạch dưới trong literal Java `1_000_000` giúp đọc **mã nguồn**, nhưng chuỗi đầu vào `"1_000_000"` không được `Integer.parseInt` chấp nhận. `"3.5"` cũng không phải chuỗi số nguyên, và ép sang `int` sau khi đọc `double` là một hành vi khác cần được đặc tả riêng.

## 4. Chương trình kiểm tra tự động bằng Java 17+

Lưu trọn chương trình sau vào tệp **`InputBoundaryChecks.java`** (không khai báo `package`). Các hàm `expectValid` và `expectInvalid` kiểm tra **cả đầu ra lẫn thông báo lỗi**; nếu hành vi thực tế không đúng, chúng ném `AssertionError`. Không cần bật JVM `-ea`.

```java
import java.math.BigInteger;

public final class InputBoundaryChecks {
    private static final int MAX_COUNT = 1_000_000;

    private InputBoundaryChecks() {
    }

    public static void main(String[] args) {
        expectInvalid("null", null, "thiếu giá trị");
        expectInvalid("rỗng", "", "rỗng hoặc chỉ khoảng trắng");
        expectInvalid("toàn khoảng trắng", "   ", "rỗng hoặc chỉ khoảng trắng");

        expectInvalid("âm -1", "-1", "phải không âm");
        expectValid("min 0", "0", 0);
        expectValid("min+1 1", "1", 1);
        expectValid("thông thường 42", "42", 42);
        expectValid("max-1 999999", "999999", 999_999);
        expectValid("max 1000000", "1000000", MAX_COUNT);
        expectInvalid("max+1 1000001", "1000001", "vượt 1000000");

        expectInvalid("sai chữ", "12x", "không phải int hợp lệ");
        expectInvalid("số thập phân", "3.5", "không phải int hợp lệ");
        expectInvalid("có khoảng trắng", " 5 ", "không phải int hợp lệ");
        expectInvalid("vượt int", "2147483648", "không phải int hợp lệ");

        checkLargeNumbers();
    }

    private static int parseCount(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("thiếu giá trị");
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException("rỗng hoặc chỉ khoảng trắng");
        }

        final int count;
        try {
            count = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("không phải int hợp lệ", e);
        }

        if (count < 0) {
            throw new IllegalArgumentException("phải không âm");
        }
        if (count > MAX_COUNT) {
            throw new IllegalArgumentException("vượt " + MAX_COUNT);
        }
        return count;
    }

    private static void expectValid(String name, String raw, int expected) {
        int actual = parseCount(raw);
        if (actual != expected) {
            throw new AssertionError(name + ": cần " + expected + ", nhận " + actual);
        }
        System.out.println(name + ": PASS -> " + actual);
    }

    private static void expectInvalid(
            String name, String raw, String expectedMessage) {
        try {
            parseCount(raw);
            throw new AssertionError(name + ": đáng lẽ phải báo lỗi");
        } catch (IllegalArgumentException e) {
            if (!expectedMessage.equals(e.getMessage())) {
                throw new AssertionError(name + ": sai thông báo lỗi", e);
            }
            System.out.println(name + ": PASS (" + e.getMessage() + ")");
        }
    }

    private static void checkLargeNumbers() {
        int unitPriceVnd = 50_000;
        int count = 1_000_000;

        try {
            Math.multiplyExact(unitPriceVnd, count);
            throw new AssertionError("phép nhân int phải bị tràn");
        } catch (ArithmeticException expected) {
            System.out.println("nhân int: PASS (phát hiện tràn)");
        }

        long totalPriceVnd = (long) unitPriceVnd * count;
        if (totalPriceVnd != 50_000_000_000L) {
            throw new AssertionError("sai thành tiền");
        }
        System.out.println("nhân long: " + totalPriceVnd);

        BigInteger aboveLongMax = BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.ONE);
        System.out.println("BigInteger: " + aboveLongMax);
    }
}
```

Mở terminal ở thư mục chứa tệp, có JDK 17 trở lên:

```bash
javac -encoding UTF-8 InputBoundaryChecks.java
java InputBoundaryChecks
```

**Stdout chính xác khi tất cả ca kiểm tra đạt:**

```text
null: PASS (thiếu giá trị)
rỗng: PASS (rỗng hoặc chỉ khoảng trắng)
toàn khoảng trắng: PASS (rỗng hoặc chỉ khoảng trắng)
âm -1: PASS (phải không âm)
min 0: PASS -> 0
min+1 1: PASS -> 1
thông thường 42: PASS -> 42
max-1 999999: PASS -> 999999
max 1000000: PASS -> 1000000
max+1 1000001: PASS (vượt 1000000)
sai chữ: PASS (không phải int hợp lệ)
số thập phân: PASS (không phải int hợp lệ)
có khoảng trắng: PASS (không phải int hợp lệ)
vượt int: PASS (không phải int hợp lệ)
nhân int: PASS (phát hiện tràn)
nhân long: 50000000000
BigInteger: 9223372036854775808
```

Chương trình dùng `AssertionError` trực tiếp để mỗi ca sai làm chương trình thất bại ngay. `NumberFormatException` được bọc lại với thông báo theo hợp đồng của `parseCount`. `Math.multiplyExact(int, int)` phát hiện tràn bằng `ArithmeticException`; phép nhân `(long) unitPriceVnd * count` chuyển một toán hạng sang `long` **trước phép nhân**, nhận kết quả đúng `50_000_000_000`. `BigInteger` biểu diễn được giá trị lớn hơn `Long.MAX_VALUE`, nhưng trong ứng dụng vẫn cần giới hạn nghiệp vụ và bộ nhớ hợp lý.

## 5. Tràn số, kiểu dữ liệu và trường hợp đặc biệt

- `int` là số nguyên có dấu 32 bit, `long` là 64 bit. Phép cộng/nhân nguyên thường **không tự ném ngoại lệ khi tràn**; kết quả có thể bị cuốn vòng và sai. Dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.toIntExact` khi cần phát hiện tràn.
- Ép kiểu **sau** phép tính đã tràn không cứu được dữ liệu: `long total = price * count;` vẫn nhân hai `int` trước. Chuyển ít nhất một toán hạng thành `long` trước phép toán nếu kết quả nằm trong miền `long`.
- Nếu có nguy cơ vượt cả `long`, dùng `BigInteger` cho số nguyên tùy độ lớn. Với tiền tệ có phần thập phân hoặc yêu cầu làm tròn, cân nhắc `BigDecimal` và quy tắc `RoundingMode` rõ ràng.
- Khi phải làm việc với số thực `double`/`float`, kiểm tra `NaN`, `Infinity`, độ chính xác và sai số làm tròn theo hợp đồng; không so sánh bằng tuyệt đối một cách máy móc trong bài toán xấp xỉ.
- Giá trị lớn còn có thể gây **tốn CPU/bộ nhớ** dù kiểu số vẫn chứa được; nên kiểm tra giới hạn kích thước dữ liệu, độ dài chuỗi, số phần tử hoặc độ lớn của đầu vào trước khi cấp phát.

## 6. Quy trình kiểm tra cho bài thực hành

1. Viết hợp đồng: kiểu đầu vào, giá trị có thể thiếu, cận bao gồm/loại trừ, quy tắc chuẩn hóa và hình thức báo lỗi.
2. Dựng bảng test theo lớp tương đương: **hợp lệ**, **rỗng/thiếu**, **ngoài cận**, **sai định dạng**, **vượt miền kiểu dữ liệu**, **tràn khi tính toán**.
3. Thêm các ca ngay **dưới, đúng và trên mỗi cận**; phân biệt `0` với `null` nếu chúng có nghĩa khác nhau.
4. Xác nhận đầu ra và loại/thông báo lỗi; các bài tập nhỏ có thể dùng runner như trên, dự án lớn có thể đưa cùng ca kiểm tra sang JUnit để tự chạy khi sửa mã.
5. Chạy lại sau thay đổi, nhất là khi đổi kiểu `int` sang `long`, thay giới hạn hoặc quyết định tự động `trim()` đầu vào. Test nên thất bại khi hành vi lệch khỏi hợp đồng.

**Các lỗi dễ mắc:** chỉ thử dữ liệu đẹp; bỏ sót `MIN` và `MAX`; dùng `==` để so nội dung `String` thay vì `equals`; gọi phương thức trên `null`; coi chuỗi trắng là chuỗi rỗng theo `isEmpty`; để phép tính tràn trước khi ép kiểu; nuốt ngoại lệ rồi trả `0`; hoặc viết test không có giá trị mong đợi cụ thể. Một bộ test ngắn nhưng có chủ đích về rỗng, biên, dữ liệu sai và giá trị lớn sẽ phát hiện được nhiều lỗi hơn số lượng lớn ví dụ ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-007">Thực hành chương trình tính tiền với điều kiện và vòng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành chương trình tính tiền với điều kiện và vòng lặp (Java 17+)

## 1. Bài toán và mục tiêu

Xây dựng một chương trình Java chạy trên console để **tính tiền giỏ hàng có nhiều mặt hàng**. Người dùng lần lượt nhập **đơn giá** và **số lượng**. Chương trình cộng thành tiền từng mặt hàng vào tạm tính, tiếp tục hỏi mặt hàng kế tiếp và dừng khi người dùng nhập **đơn giá bằng `0`**. Cuối cùng, dùng điều kiện để tính chiết khấu và số tiền phải trả.

Đây là bài thực hành kết hợp các kiến thức Java core: biến và kiểu dữ liệu, phép toán, `if / else if / else`, vòng lặp `while`, `break`, `Scanner`, kiểm tra dữ liệu, phương thức và `System.out.println()`.

**Các quy tắc cụ thể của chương trình mẫu:**

- Đơn giá là **số nguyên VND**. Nhập `0` để kết thúc; còn lại phải trong khoảng **1–1.000.000 VND**.
- Số lượng mỗi mặt hàng là số nguyên từ **1 đến 100**.
- Cho phép tối đa **100 dòng mặt hàng** trong một hóa đơn. Một dòng có thể đại diện nhiều sản phẩm giống nhau thông qua số lượng.
- Thành tiền một dòng = đơn giá × số lượng. Tạm tính = tổng thành tiền các dòng.
- Nếu tạm tính **từ 500.000 VND trở lên**, giảm **10%**; nếu **từ 200.000 VND đến dưới 500.000 VND**, giảm **5%**; dưới 200.000 VND **không giảm**.
- Tiền giảm = `subtotal * discountPercent / 100` bằng phép toán số nguyên, tức **lấy phần nguyên VND**. Tiền thanh toán = tạm tính − tiền giảm.
- Dữ liệu sai, hết input bất ngờ hoặc vượt giới hạn dòng mặt hàng sẽ được báo lỗi và **không in một hóa đơn hoàn chỉnh**.

Các ngưỡng, giới hạn và cách làm tròn trên là **yêu cầu giả định của bài thực hành**, không phải quy tắc bán hàng chung. Khi làm dự án thật, cần thống nhất trước cách tính thuế, phí vận chuyển, mã giảm giá, làm tròn và chính sách hủy đơn.

## 2. Phân tích Input – Process – Output

| Thành phần | Dữ liệu hoặc công việc | Kiểu Java gợi ý |
| --- | --- | --- |
| **Input** | Đơn giá từng dòng; `0` là tín hiệu kết thúc | `long` |
| **Input** | Số lượng tương ứng từng dòng | `long` hoặc `int` |
| **Process** | Kiểm tra giá, số lượng, số dòng mặt hàng | `if` và phép so sánh |
| **Process** | Tính `lineTotal = unitPrice * quantity` | `long` |
| **Process** | Cộng dồn `subtotal += lineTotal` | `long` |
| **Process** | Chọn mức giảm 0%, 5% hoặc 10% | `if / else if / else` |
| **Process** | Tính `discount` và `total` | `long` |
| **Output** | Thành tiền từng dòng, tạm tính, mức giảm và tổng thanh toán | `println` |

Dùng `long` cho số tiền VND nguyên để tránh sai số nhị phân của `double` và có miền giá trị lớn hơn `int`. Trong giới hạn của bài này, hóa đơn tối đa là `100 × 1_000_000 × 100 = 10_000_000_000` VND, vượt giới hạn của `int` nhưng nằm an toàn trong `long`.

Vì mức giảm tối đa là 10%, phép nhân `subtotal * discountPercent` cũng không tràn `long` với những giới hạn đã đặt. Nếu bỏ giới hạn hoặc xử lý giao dịch lớn, phải thiết kế thêm kiểm tra tràn số hoặc dùng mô hình tiền tệ phù hợp.

## 3. Thuật toán trước khi viết Java

```text
subtotal = 0
itemCount = 0

LẶP:
    Đọc unitPrice
    NẾU unitPrice == 0:
        THOÁT vòng lặp
    NẾU unitPrice không trong [1, 1_000_000]:
        BÁO LỖI và dừng chương trình
    NẾU đã có 100 dòng mặt hàng:
        BÁO LỖI và dừng chương trình

    Đọc quantity
    NẾU quantity không trong [1, 100]:
        BÁO LỖI và dừng chương trình

    lineTotal = unitPrice * quantity
    subtotal = subtotal + lineTotal
    itemCount = itemCount + 1
    In thành tiền dòng này

NẾU subtotal >= 500_000:
    discountPercent = 10
NGƯỢC LẠI NẾU subtotal >= 200_000:
    discountPercent = 5
NGƯỢC LẠI:
    discountPercent = 0

discount = subtotal * discountPercent / 100
total = subtotal - discount

In itemCount, subtotal, discountPercent, discount và total
```

Tại đây, `while (true)` là lựa chọn hợp lý vì chưa biết trước có bao nhiêu mặt hàng. **`break` chỉ được thực hiện khi đơn giá bằng 0**. Các trường hợp input không hợp lệ không được hiểu là yêu cầu kết thúc mua hàng; chúng được xử lý bằng thông báo lỗi riêng.

Bất biến của vòng lặp sau mỗi lượt hợp lệ: `itemCount` bằng số dòng đã nhập thành công và `subtotal` bằng tổng `unitPrice * quantity` của chính các dòng đó. Đây là cơ sở để tự kiểm tra logic cộng dồn.

## 4. Chương trình đầy đủ: InvoiceApp.java

Lưu nguyên chương trình sau trong tệp **`InvoiceApp.java`**:

```java
import java.util.Scanner;

public class InvoiceApp {
    private static final long MAX_UNIT_PRICE = 1_000_000L;
    private static final long MAX_QUANTITY = 100L;
    private static final int MAX_LINE_ITEMS = 100;

    private static long readLong(Scanner scanner, String prompt) {
        System.out.println(prompt);

        if (!scanner.hasNextLine()) {
            throw new IllegalArgumentException("missing input");
        }

        String input = scanner.nextLine().strip();
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("expected an integer", ex);
        }
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            try {
                long subtotal = 0L;
                int itemCount = 0;

                while (true) {
                    long unitPrice = readLong(
                            scanner, "Unit price VND (0 to finish):");

                    if (unitPrice == 0L) {
                        break;
                    }
                    if (unitPrice < 0L || unitPrice > MAX_UNIT_PRICE) {
                        throw new IllegalArgumentException(
                                "unit price must be from 1 to 1000000");
                    }
                    if (itemCount >= MAX_LINE_ITEMS) {
                        throw new IllegalArgumentException(
                                "too many line items");
                    }

                    long quantity = readLong(scanner, "Quantity:");
                    if (quantity < 1L || quantity > MAX_QUANTITY) {
                        throw new IllegalArgumentException(
                                "quantity must be from 1 to 100");
                    }

                    long lineTotal = unitPrice * quantity;
                    subtotal += lineTotal;
                    itemCount++;

                    System.out.println("Item " + itemCount
                            + ": " + lineTotal + " VND");
                }

                int discountPercent;
                if (subtotal >= 500_000L) {
                    discountPercent = 10;
                } else if (subtotal >= 200_000L) {
                    discountPercent = 5;
                } else {
                    discountPercent = 0;
                }

                long discount = subtotal * discountPercent / 100L;
                long total = subtotal - discount;

                System.out.println("Items: " + itemCount);
                System.out.println("Subtotal: " + subtotal + " VND");
                System.out.println("Discount: " + discountPercent
                        + "% = " + discount + " VND");
                System.out.println("Total: " + total + " VND");
            } catch (IllegalArgumentException ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }
}
```

### Vai trò của từng khối

**`readLong(...)` — đọc và kiểm tra định dạng.** Phương thức in lời nhắc, đọc **cả dòng** bằng `nextLine()`, loại khoảng trắng ở hai đầu bằng `strip()`, rồi chuyển bằng `Long.parseLong`. Đọc theo dòng tránh lỗi `nextLine()` nhận phần xuống dòng còn lại sau `nextInt()`. Nếu đã EOF, phương thức báo `missing input`; nếu không parse được số nguyên, báo `expected an integer`.

**`while (true)` — duyệt các mặt hàng.** Vì không biết trước số lần lặp, chương trình tiếp tục cho tới khi thấy giá `0`. Câu `break` thoát **vòng lặp gần nhất**, rồi thực thi khối tính giảm giá ở phía sau. Không nhầm `break` với `return`: `return` khỏi `main` sẽ không chạy phần tổng kết.

**`if` — kiểm tra từng input.** Đơn giá và số lượng có giới hạn độc lập. Giá âm hoặc quá lớn, số lượng 0 hoặc quá lớn đều không được tính vào hóa đơn. Giá `0` được kiểm tra **trước** kiểm tra khoảng để làm tín hiệu kết thúc.

**Phép cộng dồn — giữ đúng trạng thái.** Tính thành tiền từng dòng, cộng vào `subtotal`, sau đó tăng `itemCount` đúng một lần cho mỗi dòng hợp lệ. Tránh khai báo `subtotal = 0` **bên trong** vòng lặp, vì như vậy tổng sẽ bị đặt lại ở mỗi lượt.

**`if / else if / else` — chọn đúng một mức giảm.** Mốc cao nhất được kiểm tra trước (`>= 500_000`), kế tiếp là `>= 200_000`, cuối cùng 0%. Nếu dùng hai `if` độc lập và áp dụng chiết khấu hai lần, đơn hàng đạt 500.000 VND có thể bị tính sai.

**Kiểu `long` — số tiền nguyên VND.** Chương trình không thêm dấu phân nhóm hàng nghìn để stdout nhất quán giữa các môi trường. Có thể dùng `printf` với `Locale` phù hợp nếu yêu cầu trình bày tiền có dấu phân cách.

## 5. Biên dịch và chạy với JDK 17+

Tại thư mục chứa `InvoiceApp.java`, tạo thư mục `out` nếu chưa tồn tại:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out InvoiceApp.java
java -cp out InvoiceApp
```

Lệnh `javac` tạo `out/InvoiceApp.class`. Lệnh `java` chạy chương trình và chờ người dùng nhập lần lượt. Thử nhập các dòng sau, nhấn Enter sau mỗi giá trị:

```text
100000
2
150000
1
0
```

**Stdout chính xác do Java in** (không tính phần terminal tự hiển thị lại phím người dùng gõ):

```text
Unit price VND (0 to finish):
Quantity:
Item 1: 200000 VND
Unit price VND (0 to finish):
Quantity:
Item 2: 150000 VND
Unit price VND (0 to finish):
Items: 2
Subtotal: 350000 VND
Discount: 5% = 17500 VND
Total: 332500 VND
```

Kiểm tra bằng tay:

- Mặt hàng 1: `100000 × 2 = 200000`.
- Mặt hàng 2: `150000 × 1 = 150000`.
- Tạm tính: `200000 + 150000 = 350000`.
- `350000` nằm trong khoảng từ 200.000 đến dưới 500.000 VND nên giảm `5%`.
- Tiền giảm: `350000 × 5 / 100 = 17500`.
- Tổng thanh toán: `350000 - 17500 = 332500`.

**Chạy từ file input để kiểm thử lặp lại:** tạo `sample.txt` chứa đúng năm dòng input ở trên, rồi chạy bằng cú pháp phù hợp:

- **Linux/macOS hoặc Windows CMD:** `java -cp out InvoiceApp < sample.txt`.
- **Windows PowerShell:** `Get-Content -LiteralPath sample.txt | java -cp out InvoiceApp`.

Không cần sửa Java để đổi từ nhập bàn phím sang đọc từ file/pipe: cả hai đều cung cấp dữ liệu qua `System.in`.

## 6. Theo dõi vòng lặp bằng bảng trace

Trước khi chạy, có thể lập bảng theo từng vòng lặp để chứng minh việc cộng dồn:

| Lượt | Đơn giá | Số lượng | Thành tiền dòng | Tạm tính sau lượt | Số dòng sau lượt |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 100.000 | 2 | 200.000 | 200.000 | 1 |
| 2 | 150.000 | 1 | 150.000 | 350.000 | 2 |
| 3 | 0 | Không hỏi | Không thêm | 350.000 | 2 |

Ở lượt 3, giá `0` khiến `break`, nên chương trình **không hỏi số lượng** và không thêm dòng mặt hàng. Đây cũng là cách phát hiện lỗi thường gặp: nếu code vẫn hỏi số lượng sau khi nhập `0`, câu `break` đặt sai vị trí.

Sau vòng lặp, các biến `subtotal`, `itemCount` vẫn giữ giá trị đã tích lũy. Biến `lineTotal` chỉ phục vụ lượt hiện tại; `subtotal` mới là tổng của mọi lượt.

## 7. Kiểm tra các mốc điều kiện và trường hợp biên

Với chính sách của bài thực hành, bảng sau thể hiện các mốc quan trọng:

| Tạm tính | Mức giảm | Tiền giảm theo phép chia nguyên | Phải trả |
| ---: | ---: | ---: | ---: |
| 0 VND | 0% | 0 | 0 |
| 199.999 VND | 0% | 0 | 199.999 |
| 200.000 VND | 5% | 10.000 | 190.000 |
| 499.999 VND | 5% | 24.999 | 475.000 |
| 500.000 VND | 10% | 50.000 | 450.000 |
| 1.000.000 VND | 10% | 100.000 | 900.000 |

Ở mốc 499.999 VND, `499999 * 5 / 100` bằng `24999` theo chia nguyên, **không** làm tròn lên thành 25.000. Cần phân biệt cách làm tròn với độ chính xác của phép tính; nếu thay quy tắc nghiệp vụ thành làm tròn tới VND gần nhất, phần Process và kiểm thử phải được thay đổi đồng bộ.

### Trường hợp không mua mặt hàng nào

Nếu giá đầu tiên là `0`, không có dòng mặt hàng:

```text
0
```

**Stdout chính xác:**

```text
Unit price VND (0 to finish):
Items: 0
Subtotal: 0 VND
Discount: 0% = 0 VND
Total: 0 VND
```

Đây là một quyết định trong hợp đồng mẫu: cho phép hóa đơn rỗng có tổng 0. Nếu nghiệp vụ không cho phép, hãy kiểm tra `itemCount == 0` sau vòng lặp và báo lỗi thay vì in hóa đơn.

### Trường hợp đạt đúng mốc 500.000 VND

Nhập:

```text
500000
1
0
```

**Stdout chính xác:**

```text
Unit price VND (0 to finish):
Quantity:
Item 1: 500000 VND
Unit price VND (0 to finish):
Items: 1
Subtotal: 500000 VND
Discount: 10% = 50000 VND
Total: 450000 VND
```

Trường hợp này kiểm tra dấu `>=` tại mốc giảm cao: nếu viết nhầm `subtotal > 500_000`, hóa đơn đúng 500.000 VND sẽ rơi vào mức 5%.

## 8. Input sai và phản hồi của chương trình

Dữ liệu nhập từ console không nên được giả định luôn hợp lệ. Trong chương trình mẫu:

| Input/tình huống | Hành vi |
| --- | --- |
| Gõ `abc` khi hỏi đơn giá hoặc số lượng | `Error: expected an integer` |
| Đơn giá `-1` hoặc `1000001` | `Error: unit price must be from 1 to 1000000` |
| Số lượng `0`, `-3`, `101` | `Error: quantity must be from 1 to 100` |
| EOF khi đang cần đọc giá hoặc số lượng | `Error: missing input` |
| Sau 100 dòng mặt hàng hợp lệ lại nhập một giá dương để thêm dòng thứ 101 | `Error: too many line items` |
| Đơn giá `0` | Kết thúc nhập mặt hàng, **không phải lỗi** |

Ví dụ, với input chỉ có một dòng `abc`:

```text
abc
```

**Stdout chính xác:**

```text
Unit price VND (0 to finish):
Error: expected an integer
```

Với input gồm đơn giá hợp lệ và số lượng `0`:

```text
100000
0
```

**Stdout chính xác:**

```text
Unit price VND (0 to finish):
Quantity:
Error: quantity must be from 1 to 100
```

Các lỗi input này được biểu diễn bằng `IllegalArgumentException`, sau đó `main` bắt và in thông báo dễ hiểu. `try-with-resources` đóng `Scanner` khi chương trình kết thúc; trong một ứng dụng lớn hơn có nhiều bộ phận còn cần `System.in`, nên giao trách nhiệm quản lý luồng nhập cho một nơi thay vì đóng scanner giữa chừng.

**Vì sao chương trình không hỏi nhập lại khi sai?** Đó là quyết định để ví dụ tập trung vào vòng lặp mặt hàng, điều kiện chiết khấu và kiểm tra lỗi. Có thể mở rộng thành vòng lặp nhập lại có giới hạn số lần, nhưng phải tránh để dữ liệu sai lặp vô tận hoặc thêm mặt hàng chưa được kiểm tra.

## 9. Các lỗi lập trình phổ biến

### 9.1. Đặt biến cộng dồn vào trong vòng lặp

**Sai về logic:**

```java
while (true) {
    long subtotal = 0L; // mỗi lần lặp lại về 0
    // ... đọc và cộng tiền ...
}
```

Đây chỉ là đoạn minh họa không hoàn chỉnh. Hãy khai báo `subtotal` **trước vòng lặp** để giữ lại tổng qua các lượt.

### 9.2. Dùng gán thay cho cộng dồn

```java
subtotal = lineTotal;  // chỉ giữ tiền của mặt hàng cuối
subtotal += lineTotal; // cộng thêm dòng mới vào tổng hiện có
```

Hai dòng là minh họa cách so sánh hai lựa chọn, **không** được thực hiện cả hai liên tiếp. Trong chương trình thật, dùng dòng thứ hai để cộng dồn.

### 9.3. Sai thứ tự điều kiện chiết khấu

Nếu kiểm tra `subtotal >= 200_000` **trước** trong cùng chuỗi `if / else if`, trường hợp từ 500.000 VND sẽ thỏa nhánh 5% và **không đến nhánh 10%**.

Đúng:

```java
if (subtotal >= 500_000L) {
    discountPercent = 10;
} else if (subtotal >= 200_000L) {
    discountPercent = 5;
} else {
    discountPercent = 0;
}
```

Đây là đoạn minh họa thuộc phương thức đã khai báo `subtotal` và `discountPercent`. Hãy kiểm tra mốc lớn hơn trước vì các điều kiện có tính bao hàm nhau.

### 9.4. Nhầm `break`, `continue` và `return`

- `break` thoát vòng lặp hiện tại, giúp chuyển sang bước tính tổng cuối.
- `continue` bỏ phần còn lại của **lượt hiện tại** rồi bắt đầu lượt mới; nếu dùng sai có thể bỏ kiểm tra hoặc bỏ việc cập nhật biến.
- `return` kết thúc phương thức đang chạy. Dùng `return` trong `main` tại giá `0` sẽ khiến bước in hóa đơn phía sau không chạy.

### 9.5. Dùng `double` và kỳ vọng mọi số tiền đều chính xác

`double` không biểu diễn chính xác mọi phân số thập phân. Với VND nguyên và giới hạn nhỏ, `long` là lựa chọn đơn giản. Nếu hóa đơn sử dụng đơn vị nhỏ nhất của tiền có phần thập phân hoặc cần áp dụng quy tắc thuế/phí chính xác, cân nhắc **`BigDecimal`** hay số nguyên đơn vị nhỏ nhất, cùng chính sách làm tròn rõ ràng.

### 9.6. Không kiểm tra giới hạn gây tràn số

Nếu vô hạn số lượng mặt hàng, hoặc cho nhập giá và số lượng rất lớn, biểu thức `unitPrice * quantity` hay `subtotal += lineTotal` có thể tràn `long`. Chương trình mẫu bảo vệ bằng cả ba giới hạn: đơn giá, số lượng và số dòng. Khi làm ứng dụng thật có giới hạn khác, cần tính lại cận trên, dùng `Math.multiplyExact()`/`Math.addExact()` hoặc kiểu phù hợp.

### 9.7. Lỗi luồng đọc khi trộn `nextLong()` và `nextLine()`

`Scanner.nextLong()` chỉ đọc một token số; `nextLine()` ngay sau đó có thể nhận phần còn lại của dòng cũ. Ví dụ chính dùng **`nextLine()` cho tất cả các lần đọc** và `Long.parseLong()` để chuyển đổi, nên tránh được lỗi này. Nếu đổi sang `nextLong()`, cần thiết kế lại cách xử lý dấu xuống dòng.

## 10. Mở rộng bài tập có kiểm soát

Sau khi chương trình mẫu chạy đúng, có thể mở rộng từng chức năng một, luôn xác định lại Input, Process, Output và trường hợp kiểm thử.

**Bài A — Giảm giá theo mã ưu đãi:** cho nhập mã `SAVE5` để giảm thêm theo quy định. Phải nêu rõ mã được áp dụng **trước hay sau** chiết khấu theo ngưỡng, mức giảm có thể cộng dồn không và tiền giảm có được vượt tạm tính không.

**Bài B — Phí giao hàng:** miễn phí nếu tổng sau giảm từ 300.000 VND; nếu không thu 20.000 VND. Quy định rõ phí tính theo số tiền **trước hay sau giảm**, rồi đưa phí vào Output thay vì tự cộng thẳng trong câu lệnh in.

**Bài C — Nhập lại khi số sai:** tổ chức vòng lặp riêng yêu cầu nhập đúng giá/số lượng, có thông báo và điều kiện dừng. Cần phân biệt `0` kết thúc giỏ hàng tại câu hỏi về **giá**, còn `0` khi hỏi **số lượng** vẫn là sai.

**Bài D — Số mặt hàng cố định:** nếu đầu vào cho biết trước chính xác `n` dòng, vòng lặp `for (int i = 0; i < n; i++)` thường phù hợp hơn `while (true)`. Vẫn phải kiểm tra miền của `n`.

**Bài E — Tách chức năng:** chuyển cách tính mức giảm thành `discountPercentFor(long subtotal)` và cách tính tiền thanh toán thành một phương thức thuần không đọc console. Điều này giúp thử các ngưỡng 199.999, 200.000, 499.999 và 500.000 VND mà không cần nhập lại cả giỏ hàng.

Các gợi ý trên là **bài mở rộng**, không phải phần của mã `InvoiceApp.java`; đầu ra đã cam kết ở các ví dụ trước không phụ thuộc những tính năng chưa triển khai.

## 11. Checklist trước khi kết thúc bài thực hành

- Tôi đã xác định input là **đơn giá và số lượng theo từng dòng**, cùng ký hiệu kết thúc rõ ràng chưa?
- Giá `0` có được kiểm tra **trước** khi hỏi số lượng không?
- `subtotal` và `itemCount` có nằm **ngoài vòng lặp** và tăng đúng mỗi dòng hợp lệ không?
- Có kiểm tra giá/số lượng âm, vượt giới hạn, chuỗi không phải số và EOF không?
- Tôi dùng `if / else if / else` với **mốc cao trước, mốc thấp sau** chưa?
- Có kiểm tra hóa đơn rỗng, đúng mốc 200.000, đúng mốc 500.000 và trường hợp tổng ngay dưới mốc không?
- Tôi đã thống nhất làm tròn tiền giảm theo phép chia nguyên VND chưa?
- Sau khi `javac` thành công, tôi có chạy `java` và đối chiếu **stdout thực** với bảng trace/đầu ra dự kiến không?

**Ghi nhớ:** vòng lặp giải quyết việc **có bao nhiêu mặt hàng**, điều kiện giải quyết việc **đầu vào hợp lệ và được giảm bao nhiêu**, còn biến cộng dồn giữ **tạm tính sau mỗi lượt**. Phân chia rõ ba trách nhiệm giúp chương trình tính tiền dễ kiểm thử và ít lỗi hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-008">Thực hành thống kê mảng và tách thành phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành thống kê mảng và tách thành phương thức trong Java

## 1. Mục tiêu và yêu cầu bài thực hành

Với một mảng số nguyên `int[]`, hãy xây dựng chương trình Java 17+ thống kê:

1. **Độ dài mảng**: tổng số phần tử.
2. **Số lượng số dương, số âm và số 0**: số dương thỏa `x > 0`, số âm thỏa `x < 0`, còn số 0 là `x == 0`.
3. **Tổng các phần tử**: dùng kiểu `long` để tránh tràn `int` ở những trường hợp cộng nhiều số nguyên.
4. **Giá trị nhỏ nhất (`min`) và lớn nhất (`max`)** khi mảng không rỗng.
5. **Trung bình cộng** khi mảng có ít nhất một phần tử; dùng phép chia số thực để không mất phần thập phân.

Yêu cầu chất lượng: **không dồn toàn bộ logic vào `main`**. Hãy tách phần tính toán, tổng hợp và trình bày thành các phương thức có tên rõ ràng. Phương thức thống kê không được sửa các phần tử của mảng đầu vào.

Với đầu vào mẫu:

```text
[8, -3, 0, 7, -2, 8]
```

kết quả mong đợi là:

| Chỉ tiêu | Giá trị | Lý do |
| --- | ---: | --- |
| Độ dài | 6 | Có sáu phần tử |
| Số dương | 3 | `8, 7, 8` |
| Số âm | 2 | `-3, -2` |
| Số 0 | 1 | Một số `0` |
| Tổng | 18 | `8 - 3 + 0 + 7 - 2 + 8 = 18` |
| Nhỏ nhất | -3 | Số nhỏ nhất trong toàn bộ mảng |
| Lớn nhất | 8 | Số lớn nhất trong toàn bộ mảng |
| Trung bình | 3.00 | `18 / 6 = 3.0` |

Lưu ý: **tổng, min, max và trung bình ở đây áp dụng cho toàn bộ mảng**, không chỉ các số dương. Bộ đếm dương/âm/0 là những thống kê bổ sung. Phải phân biệt rõ điều đó khi đọc yêu cầu để không tính ra giá trị sai.

## 2. Chia bài toán thành những phương thức nhỏ

Thay vì viết một `main` dài với nhiều vòng lặp, có thể thiết kế các phương thức độc lập:

| Phương thức | Đầu vào | Kết quả / trách nhiệm |
| --- | --- | --- |
| `sum(int[] values)` | Mảng số nguyên | Tổng dưới dạng `long` |
| `countPositive(int[] values)` | Mảng số nguyên | Số phần tử `> 0` |
| `countNegative(int[] values)` | Mảng số nguyên | Số phần tử `< 0` |
| `findMin(int[] values)` | Mảng số nguyên | `OptionalInt`: giá trị nhỏ nhất, hoặc rỗng |
| `findMax(int[] values)` | Mảng số nguyên | `OptionalInt`: giá trị lớn nhất, hoặc rỗng |
| `average(long sum, int count)` | Tổng và số lượng | `OptionalDouble`: trung bình, hoặc rỗng nếu không có phần tử |
| `analyze(int[] values)` | Mảng số nguyên | Ghép các kết quả trên thành một đối tượng `Stats` |
| `printReport(String title, int[] values)` | Tiêu đề và mảng | In kết quả rõ ràng, không tự tính lại công thức trong code in |
| `main(String[] args)` | Điểm vào ứng dụng | Chuẩn bị dữ liệu, gọi các phương thức, kiểm tra kết quả |

Cách phân chia này dựa trên **một trách nhiệm chính cho mỗi phương thức**:

- Các hàm `sum`, `countPositive`, `countNegative`, `findMin`, `findMax` tập trung vào phép tính, không in màn hình.
- `analyze` là nơi **phối hợp** các phép tính và xác nhận tiền điều kiện của đầu vào.
- `printReport` chỉ quyết định **cách hiển thị** các thống kê đã được tính.
- `main` giữ vai trò tổ chức tình huống chạy, thay vì chứa toàn bộ thuật toán.

Khi một bước hoạt động sai, biết chính xác phương thức nào cần kiểm tra. Các phương thức chỉ tính toán và trả về giá trị còn có thể được **kiểm thử riêng** mà không cần đọc văn bản in trên console.

### Hợp đồng (contract) của phương thức

Trước khi viết Java, xác định rõ:

- **Tiền điều kiện:** đầu vào `values` phải khác `null`. Mảng rỗng `new int[0]` **được phép**.
- **Hậu điều kiện:** `sum` là tổng mọi phần tử; `countPositive + countNegative + countZero == values.length`.
- **Không tác dụng phụ:** các phép tính không sửa `values[i]`, không in ra màn hình, không phụ thuộc biến toàn cục có thể thay đổi.
- **Đầu vào rỗng:** tổng và các bộ đếm bằng `0`; `min`, `max` và `average` **không có giá trị**, không được tự gán `0` làm kết quả cực trị/trung bình.
- **Đầu vào `null`:** phương thức tổng hợp `analyze` từ chối rõ ràng bằng `Objects.requireNonNull`, thay vì coi `null` như mảng rỗng.

Các helper nội bộ trong bài được gọi từ `analyze` sau bước kiểm tra `null`; nếu tái sử dụng chúng thành API công khai, cần xác định và thực thi tiền điều kiện riêng cho từng helper.

## 3. Mã giả trước khi triển khai

Đây là mô tả thuật toán; **không phải mã Java để biên dịch**.

```text
FUNCTION SUM(values):
    total ← 0 (dùng số nguyên 64-bit)
    FOR EACH x IN values:
        total ← total + x
    RETURN total

FUNCTION COUNT_POSITIVE(values):
    count ← 0
    FOR EACH x IN values:
        IF x > 0:
            count ← count + 1
    RETURN count

FUNCTION COUNT_NEGATIVE(values):
    count ← 0
    FOR EACH x IN values:
        IF x < 0:
            count ← count + 1
    RETURN count

FUNCTION MIN(values):
    IF values rỗng: RETURN không có giá trị
    result ← values[0]
    FOR EACH phần tử còn lại x:
        IF x < result: result ← x
    RETURN result

FUNCTION MAX(values):
    IF values rỗng: RETURN không có giá trị
    result ← values[0]
    FOR EACH phần tử còn lại x:
        IF x > result: result ← x
    RETURN result

FUNCTION AVERAGE(total, count):
    IF count = 0: RETURN không có giá trị
    RETURN (số thực) total / count

FUNCTION ANALYZE(values):
    IF values = null: báo lỗi đầu vào
    positives ← COUNT_POSITIVE(values)
    negatives ← COUNT_NEGATIVE(values)
    zeros ← LENGTH(values) - positives - negatives
    total ← SUM(values)
    minimum ← MIN(values)
    maximum ← MAX(values)
    mean ← AVERAGE(total, LENGTH(values))
    RETURN tất cả kết quả thống kê
```

**Vì sao đếm số 0 bằng phép trừ được?** Mỗi số nguyên thuộc đúng một trong ba nhóm `> 0`, `< 0`, `== 0`. Ba nhóm không chồng lấn và bao phủ toàn bộ mảng; do đó `zeros = length - positives - negatives` đúng.

**Vì sao `min` và `max` cần xử lý mảng rỗng?** Không thể truy cập `values[0]` khi `values.length == 0`. Cũng không thể nói giá trị nhỏ nhất của một mảng rỗng bằng `0`: `0` có thể là dữ liệu hợp lệ, nhưng mảng rỗng không có phần tử nào cả.

## 4. Dự đoán bằng bảng theo dõi

Với `[8, -3, 0, 7, -2, 8]`, có thể theo dõi bộ đếm và tổng trong một lượt **chạy tay theo từng phần tử** như sau:

| Bước | Phần tử `x` | Dương | Âm | Bằng 0 | Tổng sau bước |
| --- | ---: | ---: | ---: | ---: | ---: |
| Khởi tạo | — | 0 | 0 | 0 | 0 |
| 1 | 8 | 1 | 0 | 0 | 8 |
| 2 | -3 | 1 | 1 | 0 | 5 |
| 3 | 0 | 1 | 1 | 1 | 5 |
| 4 | 7 | 2 | 1 | 1 | 12 |
| 5 | -2 | 2 | 2 | 1 | 10 |
| 6 | 8 | 3 | 2 | 1 | 18 |

Bảng mô tả **trạng thái tổng hợp giả lập sau mỗi phần tử**, hữu ích để kiểm tra logic; chương trình tách hàm ở phần sau thực hiện các phép tính bằng **những lượt duyệt độc lập** và cuối cùng thu được cùng kết quả.

Khi tính `min` và `max`, bắt đầu từ phần tử đầu tiên `8`. Sau khi gặp `-3`, `min = -3`; không có phần tử nào nhỏ hơn, nên kết quả cuối là `-3`. `max` luôn là `8` với mảng này. Kết quả trung bình: `18 / 6 = 3.0`.

Một bảng như vậy giúp kiểm tra bước **đầu tiên** khiến tổng hoặc bộ đếm khác dự đoán. Khi thực hành, có thể đặt breakpoint trong từng phương thức và so sánh kết quả thực tế với bảng.

## 5. Java 17+: chương trình hoàn chỉnh được tách thành phương thức

Tạo đúng một tệp **`ArrayStatisticsPractice.java`**:

```java
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ArrayStatisticsPractice {
    record Stats(
            int length,
            int positive,
            int negative,
            int zero,
            long sum,
            OptionalInt min,
            OptionalInt max,
            OptionalDouble average) {}

    static long sum(int[] values) {
        long total = 0L;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    static int countPositive(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    static int countNegative(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value < 0) {
                count++;
            }
        }
        return count;
    }

    static OptionalInt findMin(int[] values) {
        if (values.length == 0) {
            return OptionalInt.empty();
        }
        int result = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] < result) {
                result = values[i];
            }
        }
        return OptionalInt.of(result);
    }

    static OptionalInt findMax(int[] values) {
        if (values.length == 0) {
            return OptionalInt.empty();
        }
        int result = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > result) {
                result = values[i];
            }
        }
        return OptionalInt.of(result);
    }

    static OptionalDouble average(long total, int count) {
        if (count == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) total / count);
    }

    static Stats analyze(int[] values) {
        Objects.requireNonNull(values, "values");

        int positive = countPositive(values);
        int negative = countNegative(values);
        int zero = values.length - positive - negative;
        long total = sum(values);

        return new Stats(
                values.length,
                positive,
                negative,
                zero,
                total,
                findMin(values),
                findMax(values),
                average(total, values.length));
    }

    static void printReport(String title, int[] values) {
        Stats stats = analyze(values);
        System.out.println(title);
        System.out.println("array=" + Arrays.toString(values));
        System.out.println("length=" + stats.length());
        System.out.println("positive=" + stats.positive());
        System.out.println("negative=" + stats.negative());
        System.out.println("zero=" + stats.zero());
        System.out.println("sum=" + stats.sum());

        System.out.println("min=" + (stats.min().isPresent()
                ? stats.min().getAsInt() : "N/A"));
        System.out.println("max=" + (stats.max().isPresent()
                ? stats.max().getAsInt() : "N/A"));

        if (stats.average().isPresent()) {
            System.out.printf(Locale.US, "average=%.2f%n",
                    stats.average().getAsDouble());
        } else {
            System.out.println("average=N/A");
        }
    }

    static void check(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
        System.out.println("test " + label + ": PASS");
    }

    public static void main(String[] args) {
        int[] sample = {8, -3, 0, 7, -2, 8};
        int[] empty = {};
        int[] negatives = {-5, -1, -5};
        int[] large = {Integer.MAX_VALUE, Integer.MAX_VALUE};

        printReport("Case A", sample);
        printReport("Case B", empty);
        printReport("Case C", negatives);
        printReport("Case D", large);

        Stats a = analyze(sample);
        Stats b = analyze(empty);
        Stats d = analyze(large);

        check("sample", a.length() == 6
                && a.positive() == 3 && a.negative() == 2
                && a.zero() == 1 && a.sum() == 18L
                && a.min().orElseThrow() == -3
                && a.max().orElseThrow() == 8);

        check("empty", b.length() == 0 && b.sum() == 0
                && b.min().isEmpty() && b.max().isEmpty()
                && b.average().isEmpty());

        check("large sum", d.sum() == 4294967294L);
    }
}
```

Chương trình không cần thư viện bên ngoài. Dùng **JDK 17 trở lên** và mở terminal tại thư mục chứa `ArrayStatisticsPractice.java`:

```bash
javac -encoding UTF-8 ArrayStatisticsPractice.java
java ArrayStatisticsPractice
```

Lệnh `javac` thành công không in thông báo. **Stdout chính xác** của `java`:

```text
Case A
array=[8, -3, 0, 7, -2, 8]
length=6
positive=3
negative=2
zero=1
sum=18
min=-3
max=8
average=3.00
Case B
array=[]
length=0
positive=0
negative=0
zero=0
sum=0
min=N/A
max=N/A
average=N/A
Case C
array=[-5, -1, -5]
length=3
positive=0
negative=3
zero=0
sum=-11
min=-5
max=-1
average=-3.67
Case D
array=[2147483647, 2147483647]
length=2
positive=2
negative=0
zero=0
sum=4294967294
min=2147483647
max=2147483647
average=2147483647.00
test sample: PASS
test empty: PASS
test large sum: PASS
```

### Giải thích bốn bộ dữ liệu

- **Case A**: đủ số dương, âm, số 0; trung bình của toàn mảng là `3.00`.
- **Case B**: mảng rỗng; bộ đếm và tổng bằng 0, còn `min`, `max` và `average` là `N/A`, tránh truy cập `values[0]` hoặc chia 0.
- **Case C**: chỉ có số âm; `max = -1` (không phải 0) và trung bình `-11 / 3` được in thành `-3.67`.
- **Case D**: hai giá trị `Integer.MAX_VALUE`. Tổng toán học bằng `4294967294` vượt phạm vi `int`, nhưng `long sum` giữ được kết quả chính xác trong trường hợp này.

Ba phép kiểm tra cuối dùng phương thức `check`: nếu điều kiện sai thì ném `AssertionError`, thay vì luôn in `PASS` dù thuật toán sai. Không phải test framework đầy đủ, nhưng đủ để thực hành cách kiểm tra đầu ra theo kỳ vọng.

## 6. Tại sao dùng `OptionalInt` và `OptionalDouble`?

Một sai lầm phổ biến là để `min = 0`, `max = 0` khi mảng rỗng rồi in ra như thể đó là thống kê hợp lệ.

Ví dụ đối với mảng `{-5, -1, -5}`, `max` thật sự là `-1`, **không phải 0**. Nếu khởi tạo `max = 0` và chỉ cập nhật khi phần tử lớn hơn `max`, thuật toán sẽ giữ kết quả sai là `0`.

Trong chương trình này:

- `OptionalInt.of(value)` biểu diễn **có một giá trị `int`**.
- `OptionalInt.empty()` biểu diễn **không tồn tại giá trị** để trả về.
- `OptionalDouble` có ý nghĩa tương tự cho trung bình số thực.

Phần in kết quả chỉ gọi `getAsInt()` sau khi `isPresent()` là `true`; chỉ gọi `getAsDouble()` khi `average` có giá trị. Nếu truy cập `getAsInt()` trên `OptionalInt.empty()`, Java ném `NoSuchElementException`.

`OptionalInt`/`OptionalDouble` phù hợp để **thể hiện một kết quả có thể vắng mặt**; đây không phải cách duy nhất. Với bài nhỏ, cũng có thể dùng điều kiện `values.length == 0` trước khi tính hoặc in min/max. Quan trọng nhất là **đừng lẫn “không có kết quả” với kết quả bằng 0**.

## 7. `record Stats`: gom kết quả mà không dồn logic tính toán

Java 17 hỗ trợ **`record`**, thuận tiện để biểu diễn một nhóm dữ liệu trả về cùng nhau:

```java
record Stats(int length, int positive, int negative) {}
```

Đây chỉ là **minh họa cú pháp**, còn chương trình đầy đủ đã định nghĩa một `Stats` với tám trường. Record tự cung cấp constructor, các phương thức truy cập tên như `length()`, `positive()` và các thành phần phù hợp khác.

Tách bước trả kết quả thành `Stats` giúp tránh dùng biến toàn cục hoặc những tham số truyền đi truyền lại chỉ để cập nhật kết quả. Vì phương thức `analyze` trả một giá trị tổng hợp, nhiều nơi khác nhau có thể sử dụng thống kê theo nhu cầu (in báo cáo, kiểm thử, lưu dữ liệu...) mà không phải chạy lại từng phương thức thủ công.

**Không nhầm record với bất biến tuyệt đối của mọi tham chiếu.** Record giữ trạng thái các thành phần dưới dạng field `final`, nhưng nếu chứa một đối tượng có thể sửa được thì đối tượng đó vẫn có thể thay đổi. Ở đây `Stats` giữ kiểu nguyên thủy và các đối tượng `OptionalInt`/`OptionalDouble` bất biến, nên không có mảng đầu vào nào được đặt vào bản ghi để bị chỉnh sửa qua kết quả.

## 8. Kiểm thử từng phương thức và trường hợp biên

Tách phương thức có ích nhất khi mỗi phương thức có **hợp đồng và ca kiểm tra** riêng:

| Phương thức | Ca kiểm tra | Kết quả mong đợi |
| --- | --- | --- |
| `sum` | `[8, -3, 0, 7, -2, 8]` | `18L` |
| `sum` | `[]` | `0L` |
| `sum` | `[Integer.MAX_VALUE, Integer.MAX_VALUE]` | `4294967294L` |
| `countPositive` | `[-1, 0, 4, 4]` | `2` |
| `countNegative` | `[-1, 0, 4, 4]` | `1` |
| `findMin` | `[-5, -1, -5]` | `OptionalInt.of(-5)` |
| `findMax` | `[-5, -1, -5]` | `OptionalInt.of(-1)` |
| `findMin` / `findMax` | `[]` | Cả hai rỗng |
| `average` | `(18L, 6)` | `OptionalDouble.of(3.0)` |
| `average` | `(0L, 0)` | `OptionalDouble.empty()` |

Các ca này là **kỳ vọng kiểm thử**, không phải thêm đoạn mã chạy độc lập. Có thể tự viết các lệnh `check(...)` hoặc dùng JUnit trong dự án có cấu hình thư viện kiểm thử.

Đặc biệt nên kiểm tra:

1. **Mảng rỗng** để tránh truy cập chỉ số 0 và chia cho 0.
2. **Chỉ số âm** và **chỉ số 0** để xác định điều kiện `> 0`, `< 0`, `== 0` rõ ràng.
3. **Mảng chỉ chứa số âm** để phát hiện cách khởi tạo max sai.
4. **Mảng nhiều phần tử bằng nhau** để kiểm tra cập nhật min/max.
5. **Tổng vượt phạm vi `int`** để xác nhận dùng `long`.
6. **`null`** để xác nhận hợp đồng đầu vào được thực thi nhất quán.
7. **Mảng một phần tử**: min và max phải đều là phần tử đó; average đúng bằng giá trị ấy.
8. **Không thay đổi đầu vào**: so sánh mảng trước và sau khi gọi `analyze` nếu cần kiểm chứng tính không tác dụng phụ.

Một phép kiểm thử không nên chỉ khẳng định `sum` đúng rồi cho rằng các hàm đếm hoặc tìm cực trị cũng chắc chắn đúng; mỗi trách nhiệm cần ít nhất một ca kiểm tra phù hợp.

## 9. Những lỗi thường gặp khi tách phương thức

### Lỗi 1: Tính trung bình bằng chia nguyên

```java
long sum = 14;
int count = 3;
double wrong = sum / count;         // 4.0: chia nguyên trước
double right = (double) sum / count; // khoảng 4.6667
```

Đây là đoạn minh họa bên trong một phương thức. Việc gán sang `double` **sau** khi chia nguyên không phục hồi phần lẻ đã mất. Dùng phép ép hoặc một toán hạng dạng số thực trước khi chia.

### Lỗi 2: Dùng `int` cho tổng có thể lớn

```java
int wrongSum = Integer.MAX_VALUE + Integer.MAX_VALUE;
// wrongSum == -2 do tràn int.
```

Biểu thức cộng hai giá trị `int` được tính bằng `int`, nên có thể tràn **trước khi gán**, kể cả nếu biến đích là `long`. Trong chương trình hoàn chỉnh, `total` được khai báo `long`, nên khi dùng `total += value`, phép cộng có ngữ cảnh phù hợp với kiểu `long`.

### Lỗi 3: Đếm luôn số 0 vào nhóm dương/âm

```java
if (value >= 0) {
    positive++;
}
```

Đoạn minh họa trên **sai** nếu yêu cầu “dương” nghĩa là `> 0`. Số `0` phải thuộc nhóm riêng. Một bất biến hữu ích để tự kiểm tra là `positive + negative + zero == length`.

### Lỗi 4: Hàm vừa tính vừa in vừa thay đổi dữ liệu

Ví dụ một `sum` mà vừa cộng, vừa sửa `values[i]`, vừa in kết quả sẽ khó tái sử dụng và khó test. Với bài này, helper tính toán **chỉ trả kết quả**. Tầng `printReport` chịu trách nhiệm xuất console; `main` chọn tình huống và kiểm tra.

### Lỗi 5: Dồn nhiều việc không liên quan vào một phương thức

Nếu `main` chứa tất cả các vòng lặp, định dạng số, xử lý mảng rỗng và kiểm thử, khi cần thay cách in bảng phải đọc lại cả thuật toán. Tách theo **trách nhiệm** tốt hơn tách chỉ để mỗi hàm có vài dòng.

### Lỗi 6: Tạo quá nhiều lượt duyệt mà không nhận ra đánh đổi

Thiết kế ở mục 5 dùng nhiều lượt duyệt: mỗi hàm như `sum`, `countPositive`, `countNegative`, `findMin`, `findMax` xử lý một phần mảng hoặc toàn mảng. Với một mảng độ dài `n`, tổng số phép duyệt vẫn tỷ lệ với `n` do số hàm là hằng số: **thời gian O(n)**, bộ nhớ bổ sung cho phần tính toán **O(1)**. Phần `Arrays.toString` tạo chuỗi để hiển thị nên có thêm chi phí bộ nhớ theo kích thước mảng.

Nếu ứng dụng cần hiệu năng hơn trên mảng cực lớn, có thể gom các phép cập nhật vào một vòng lặp duy nhất nhưng vẫn giữ phương thức `analyze` tách riêng khỏi `printReport` và `main`. Không nên tối ưu hóa quá sớm nếu đánh đổi làm bài học khó hiểu, nhưng cũng không nên hiểu “tách phương thức” đồng nghĩa với “bắt buộc duyệt dữ liệu nhiều lần”.

## 10. Bài tập mở rộng có định hướng

Sau khi chạy đúng chương trình:

1. Bổ sung `countEven(int[] values)` đếm số chẵn bằng `value % 2 == 0`, bao gồm `0` và số âm chẵn. Thêm trường tương ứng vào `Stats` và một ca kiểm thử.
2. Bổ sung tổng **chỉ các số dương** bằng helper độc lập `sumPositive`; đừng nhầm với tổng toàn bộ mảng hiện tại.
3. Viết thêm `findFirstIndexOfMax` trả chỉ số xuất hiện **đầu tiên** của giá trị lớn nhất; quy định hành vi cho mảng rỗng.
4. Thử phiên bản `analyze` chỉ duyệt mảng **một lần**, rồi so sánh kết quả với phiên bản nhiều helper trên cùng bộ test.
5. Viết một bộ kiểm thử gồm ít nhất mảng rỗng, một phần tử, toàn âm, toàn dương, lặp giá trị, số 0 và tổng vượt `int`.

Các yêu cầu mở rộng là bài luyện tập thêm, không làm thay đổi kết quả chạy chính của chương trình ở mục 5.

**Ghi nhớ:** Thống kê mảng không chỉ là viết vòng lặp, mà còn là xác định đúng nghĩa từng chỉ tiêu, xử lý trường hợp biên và chia trách nhiệm rõ ràng. Các phương thức nhỏ, không tác dụng phụ và có đầu ra xác định sẽ giúp Java code **dễ đọc, dễ gỡ lỗi, dễ kiểm thử và dễ tái sử dụng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-009">Thực hành menu console với kiểm tra đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành menu console với kiểm tra đầu vào (Java 17+)

## 1. Mục tiêu và yêu cầu bài toán

Một **menu console** là chương trình hiển thị danh sách lựa chọn, nhận thao tác của người dùng rồi thực hiện hành động tương ứng. Menu thường cần **lặp lại** cho đến khi người dùng chủ động chọn thoát. Nếu chỉ đọc lựa chọn bằng `nextInt()` rồi dùng `switch`, chương trình dễ gặp vấn đề khi người dùng nhập chữ, số ngoài phạm vi, chuỗi rỗng hoặc kết thúc luồng nhập (*EOF*).

Bài thực hành này xây dựng **menu quản lý tổng điểm** trên Java 17+, với các lựa chọn:

| Lựa chọn | Hành động | Dữ liệu thêm |
| --- | --- | --- |
| `1` | Thêm điểm vào tổng hiện tại | Nhập một số nguyên từ `1` đến `1000` |
| `2` | Xem tổng điểm hiện tại | Không |
| `3` | Đặt tổng điểm về `0` | Không |
| `0` | Thoát chương trình | Không |

**Hợp đồng đầu vào:**

- Lựa chọn menu phải là **số nguyên từ 0 đến 3**.
- Số điểm thêm vào phải là **số nguyên từ 1 đến 1000**; không nhận `0`, số âm hoặc phân số.
- Nếu nhập ký tự không phải số nguyên (`abc`, `1.5`, dòng rỗng, số nguyên vượt miền `int`), chương trình **báo lỗi và hỏi lại trường đang nhập**, không tự thoát hoặc đổi tổng.
- Nếu nhập số nguyên ngoài phạm vi, chương trình báo đúng khoảng hợp lệ và hỏi lại.
- Nếu luồng đầu vào kết thúc trước khi có lựa chọn hợp lệ, chương trình thông báo `Input ended.` rồi kết thúc có kiểm soát.
- Tổng điểm khởi đầu là `0` và chỉ tăng khi một lượt **thêm điểm hợp lệ** được chấp nhận.

Đây là menu minh họa để luyện vòng lặp, lựa chọn, kiểm tra dữ liệu và quản lý trạng thái, không phải hệ thống tích điểm thực tế.

## 2. Phân tích Input – Process – Output

| Phần | Nội dung | Cách dùng Java |
| --- | --- | --- |
| Input | Lựa chọn `0`–`3` | `Scanner.nextLine()`, `Integer.parseInt()` |
| Input | Số điểm thêm `1`–`1000` nếu chọn `1` | Cùng hàm đọc và xác thực đầu vào |
| Process | Lặp lại menu cho tới khi thoát | `while (true)` |
| Process | Chuyển lựa chọn thành hành động | `switch` với cú pháp `case ... ->` |
| Process | Cộng dồn hoặc đặt lại `total` | `long total` được khai báo **ngoài** vòng lặp |
| Output | Menu, lời nhắc, lỗi, điểm vừa thêm, tổng và lời chào thoát | `System.out.println()` |

Chọn `long` cho tổng vì nhiều lần cộng có thể vượt miền `int`; điểm mỗi lượt được lưu dưới dạng `int` vì giới hạn tối đa chỉ `1000`. Bài này không đặt giới hạn tổng số lượt; trong chương trình chạy cực lâu, vẫn cần xem xét giới hạn tổng hoặc `Math.addExact()` để ngăn tràn `long`. Với một phiên console học tập thông thường, đây không phải vấn đề thực tế.

### Sơ đồ logic

```text
Khởi tạo total = 0
        |
        v
Hiển thị menu
        |
        v
Đọc và kiểm tra lựa chọn
        |
        +-- EOF ------------------------> Thông báo và kết thúc
        |
        +-- Không đúng kiểu/khoảng ----> Báo lỗi, hỏi lại lựa chọn
        |
        v
switch(choice)
        |
        +-- 1: hỏi điểm -- sai --> báo lỗi, hỏi lại điểm
        |              |
        |              +-- hợp lệ --> cộng total, thông báo
        |              |
        |              +-- EOF -------> thông báo và kết thúc
        |
        +-- 2: in tổng hiện tại
        |
        +-- 3: đặt tổng về 0
        |
        +-- 0: chào và kết thúc
        |
        v
Quay lại menu sau hành động 1, 2 hoặc 3
```

Điểm then chốt là **vòng lặp nhập lại ở bên trong từng lời nhắc** và **vòng lặp menu ở bên ngoài** có hai trách nhiệm khác nhau. Dữ liệu sai ở lời nhắc điểm không nên tự đưa người dùng về menu, và dữ liệu sai ở lời nhắc menu không được làm thay đổi tổng.

## 3. Code Java 17+ đầy đủ: MenuConsoleApp.java

Tạo một tệp tên **`MenuConsoleApp.java`** với đúng nội dung sau:

```java
import java.util.Scanner;

public final class MenuConsoleApp {
    private MenuConsoleApp() {
    }

    /**
     * Đọc một số nguyên trong khoảng cho phép.
     * Trả về null nếu nguồn đầu vào đã kết thúc.
     */
    private static Integer readIntInRange(
            Scanner scanner, String prompt, int min, int max) {

        while (true) {
            System.out.println(prompt);

            if (!scanner.hasNextLine()) {
                return null;
            }

            String text = scanner.nextLine().strip();
            final int value;

            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                System.out.println("Error: enter an integer.");
                continue;
            }

            if (value < min || value > max) {
                System.out.println("Error: value must be from "
                        + min + " to " + max + ".");
                continue;
            }

            return value;
        }
    }

    public static void main(String[] args) {
        long total = 0L;

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("=== MENU ===");
                System.out.println("1. Add points");
                System.out.println("2. View total");
                System.out.println("3. Reset total");
                System.out.println("0. Exit");

                Integer choice = readIntInRange(
                        scanner, "Choice (0-3):", 0, 3);

                if (choice == null) {
                    System.out.println("Input ended.");
                    return;
                }

                switch (choice) {
                    case 1 -> {
                        Integer points = readIntInRange(
                                scanner, "Points (1-1000):", 1, 1000);

                        if (points == null) {
                            System.out.println("Input ended.");
                            return;
                        }

                        total += points;
                        System.out.println("Added: " + points);
                    }
                    case 2 -> System.out.println("Total: " + total);
                    case 3 -> {
                        total = 0L;
                        System.out.println("Total reset.");
                    }
                    case 0 -> {
                        System.out.println("Goodbye.");
                        return;
                    }
                    default -> throw new AssertionError(
                            "Unexpected validated menu choice");
                }
            }
        }
    }
}
```

### Giải thích theo từng phần

**`readIntInRange()` — một chỗ duy nhất để kiểm tra kiểu và phạm vi.** Phương thức in lời nhắc, đọc trọn một dòng với `nextLine()`, bỏ khoảng trắng đầu/cuối bằng `strip()`, rồi chuyển thành `int` với `Integer.parseInt()`. Nếu chuyển đổi thất bại, `catch (NumberFormatException)` báo lỗi và dùng `continue` để hỏi lại **chính câu hỏi đang nhập**. Nếu số nằm ngoài `[min, max]`, cũng báo lỗi và hỏi lại.

**`Integer` thay vì `int` cho kết quả đọc.** Phương thức cần biểu diễn hai tình trạng riêng biệt: đọc được một số hợp lệ, hoặc **EOF trước khi có số**. `Integer` có thể là `null` khi EOF; riêng lựa chọn `0` vẫn là một **số hợp lệ có ý nghĩa thoát**. Vì vậy không dùng `0` thay cho EOF. Sau khi xác nhận `choice != null`, `switch (choice)` có thể unbox sang `int` an toàn.

**`while (true)` ngoài cùng — tiếp tục hiển thị menu.** Sau lựa chọn 1, 2 hoặc 3, chương trình kết thúc nhánh `switch` rồi quay về đầu vòng lặp. Lựa chọn 0 gọi `return` để kết thúc `main`. Khối `try-with-resources` đóng scanner khi rời khối.

**`switch (choice)` — thực hiện đúng một hành động.** Cú pháp nhánh có mũi tên `case 1 ->` được Java 17 hỗ trợ và không có hiện tượng *fall-through* như dạng `case 1:` cũ nếu quên `break`. Với những nhánh có nhiều lệnh, dùng khối `{ ... }` để khai báo biến và xử lý.

**`total` ngoài vòng lặp — giữ trạng thái.** Nếu đặt `long total = 0L;` bên trong `while`, tổng sẽ trở về 0 mỗi khi menu hiện lại. Chỉ nhánh 1 (nhập điểm hợp lệ) tăng tổng; nhánh 3 chủ động đặt lại.

**`default` là chốt kiểm tra lập trình.** Vì `readIntInRange` đã giới hạn lựa chọn từ 0 đến 3, đây là nhánh không thể đến theo luồng bình thường. Nếu sau này ai đó sửa menu hoặc bộ kiểm tra không đồng bộ, `AssertionError` giúp phát hiện lỗi thiết kế thay vì im lặng bỏ qua.

## 4. Biên dịch và chạy bằng CLI

Dùng **JDK 17+** trong thư mục có file `MenuConsoleApp.java`. Tạo thư mục `out` một lần nếu chưa tồn tại:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out MenuConsoleApp.java
java -cp out MenuConsoleApp
```

Các lệnh `javac` và `java` dùng được trên Windows PowerShell, Windows CMD, Linux và macOS. Nếu biên dịch thành công, `javac` thường không in nội dung nào. `-d out` chỉ định thư mục chứa `MenuConsoleApp.class`; `-cp out` bảo JVM tìm lớp trong thư mục đó. Không cần viết `.class` khi gọi `java`.

Chương trình sẽ hiển thị menu và chờ người dùng nhập lựa chọn. Sau mỗi số, nhấn Enter. Bạn cũng có thể lưu dãy input vào `sample.txt` để tái hiện đúng stdout:

- **Linux/macOS hoặc Windows CMD:** `java -cp out MenuConsoleApp < sample.txt`.
- **Windows PowerShell:** `Get-Content -LiteralPath sample.txt | java -cp out MenuConsoleApp`.

Khi chạy tương tác, terminal có thể tự hiển thị lại các ký tự bàn phím người dùng gõ. Phần đó **không thuộc stdout** do các lệnh `println()` của Java tạo ra; ví dụ dưới đây chỉ liệt kê stdout của chương trình.

## 5. Kiểm thử luồng menu thông thường

Tạo `sample.txt` với sáu dòng **đầu vào**:

```text
1
7
2
3
2
0
```

Ý nghĩa: thêm 7 điểm, xem tổng, đặt lại tổng, xem tổng một lần nữa, thoát.

**Stdout chính xác**:

```text
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Points (1-1000):
Added: 7
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Total: 7
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Total reset.
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Total: 0
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Goodbye.
```

Hãy đối chiếu từng lượt:

1. Lựa chọn `1` khiến chương trình hỏi thêm `Points (1-1000):`, nhận `7`, cộng `total = 0 + 7`.
2. Lựa chọn `2` **không hỏi điểm**; chỉ in `Total: 7`.
3. Lựa chọn `3` đặt `total = 0`, xác nhận bằng `Total reset.`.
4. Lựa chọn `2` lần nữa in `Total: 0`, chứng minh giá trị đã được đặt lại.
5. Lựa chọn `0` in `Goodbye.`, sau đó rời chương trình; không hiển thị menu lần thứ sáu.

## 6. Kiểm thử đầu vào không hợp lệ

Tạo một file đầu vào khác với ba dòng:

```text
abc
9
0
```

**Stdout chính xác**:

```text
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Error: enter an integer.
Choice (0-3):
Error: value must be from 0 to 3.
Choice (0-3):
Goodbye.
```

Giải thích:

- `abc` không thể chuyển thành `int`; `Integer.parseInt("abc")` ném `NumberFormatException`. Chương trình **bắt lỗi trong helper** và hỏi lại lựa chọn.
- `9` là `int` hợp lệ về kiểu nhưng nằm ngoài menu 0–3; chương trình báo lỗi **phạm vi**, không coi đó là lựa chọn bí mật.
- `0` hợp lệ và dẫn tới nhánh thoát.
- Trong hai lần nhập sai, **menu không hiển thị lại toàn bộ** vì lỗi được xử lý ở vòng lặp nhập lựa chọn bên trong `readIntInRange()`, đúng với mục đích giữ người dùng ở cùng trường nhập.

Tương tự, nếu chọn 1 nhưng nhập `0`, `-1`, `1001`, `1.5`, chuỗi rỗng hoặc `999999999999999999999` vào lời nhắc điểm, chương trình tiếp tục hỏi lại cho đến khi nhận số nguyên từ 1 đến 1000 hoặc gặp EOF. Những trường hợp này **không cộng vào tổng**.

**Quan trọng:** lỗi định dạng và lỗi phạm vi khác nhau. `"1.5"` là văn bản không parse được bằng `Integer.parseInt`, trong khi `1001` parse thành công nhưng vi phạm điều kiện bài toán.

## 7. Xử lý EOF thay vì chờ hoặc phát sinh lỗi

EOF (*end of file*) có thể xảy ra khi file chuyển vào chương trình đã hết dòng, pipe bị đóng hoặc người dùng kết thúc nhập theo cách terminal hỗ trợ. EOF **khác** lựa chọn 0:

- **Chọn 0:** người dùng yêu cầu thoát; chương trình in `Goodbye.`.
- **Gặp EOF:** không còn dữ liệu để đọc; helper trả `null`, chương trình in `Input ended.`.

Ví dụ đầu vào trống (file `empty.txt` không có dòng nào) cho **stdout chính xác**:

```text
=== MENU ===
1. Add points
2. View total
3. Reset total
0. Exit
Choice (0-3):
Input ended.
```

Nếu input chỉ có dòng `1` và sau đó EOF, chương trình in menu, nhận lựa chọn 1, in `Points (1-1000):`, rồi `Input ended.`. Nó không cộng điểm và không tiếp tục lặp vô hạn.

Phương thức `hasNextLine()` kiểm tra **còn dòng để đọc**, không kiểm tra dòng tiếp theo có phải số nguyên hay không. Đó là lý do phải thực hiện cả hai bước: `hasNextLine()` cho EOF và `Integer.parseInt()` cho tính hợp lệ của số.

Trong console tương tác, `hasNextLine()` có thể **chờ input** khi luồng chưa gửi dòng và chưa kết thúc. Việc chương trình đang chờ không đồng nghĩa với vòng lặp vô hạn; hãy phân biệt nó với trường hợp code đọc đi đọc lại cùng một token sai mà không tiêu thụ.

## 8. Vì sao đọc cả dòng rồi parse thay vì gọi `nextInt()`?

Một cách viết dễ gặp là:

```java
int choice = scanner.nextInt();
```

Nếu người dùng nhập `abc`, lời gọi này có thể ném `InputMismatchException`. Ngoài ra, khi trộn `nextInt()` với `nextLine()`, ký tự kết thúc dòng còn lại có thể khiến `nextLine()` kế tiếp trả chuỗi rỗng.

Cách của bài này là:

```java
String text = scanner.nextLine().strip();
int choice = Integer.parseInt(text);
```

Đoạn trên chỉ minh họa **hai câu lệnh**, với giả định biến `scanner` đã tồn tại và input đúng định dạng; trong ví dụ đầy đủ, thao tác parse nằm trong `try/catch` để không làm chương trình dừng khi sai.

Ưu điểm của đọc cả dòng:

- Mỗi lượt nhập tương ứng **một dòng rõ ràng**, không để lại newline của cùng dòng.
- Dễ phân biệt **EOF**, chuỗi rỗng, dữ liệu chứa khoảng trắng, dữ liệu không phải số và giá trị ngoài miền.
- Có thể in thông báo và hỏi lại một cách ổn định.
- Không cần tạo nhiều đối tượng `Scanner` cho cùng một `System.in`.

Nếu định dạng đầu vào thật sự là nhiều số trên cùng một dòng, đọc từng token có thể thuận tiện hơn. Không có quy tắc rằng `nextInt()` luôn sai; vấn đề là chọn cách đọc phù hợp với cấu trúc dữ liệu và xử lý lỗi đúng.

## 9. Phân biệt các câu lệnh điều khiển vòng lặp

Trong menu, rất dễ nhầm `continue`, `break` và `return`.

| Câu lệnh | Ảnh hưởng | Áp dụng trong bài |
| --- | --- | --- |
| `continue` | Bỏ phần còn lại của **lượt lặp hiện tại**, chuyển sang lượt kế tiếp của vòng lặp gần nhất | Khi nhập sai trong `readIntInRange()`, tiếp tục **hỏi lại trường đó** |
| `break` | Thoát khỏi vòng lặp hoặc `switch` gần nhất theo dạng cú pháp tương ứng | Có thể dùng cho vòng menu, nhưng ví dụ dùng `return` khi chọn 0 |
| `return` | Kết thúc phương thức đang thực thi | Trong `main`, thoát hẳn chương trình; trong helper, trả giá trị hoặc `null` |
| `case ... ->` | Nhánh `switch` có cú pháp mũi tên, không chạy xuyên sang nhánh khác | Hành động 1, 2, 3, 0 độc lập |

**Lưu ý:** Nếu dùng dạng `switch (choice) { case 0: break; ... }`, `break` trong `case 0:` chỉ thoát `switch`, **không tự thoát vòng `while` bao ngoài**. Chương trình có thể hiển thị menu lại thay vì kết thúc. Cần cờ điều khiển vòng, `break` có nhãn trong trường hợp phù hợp, hoặc `return` như bài mẫu.

Trong Java 17, `switch` dạng arrow `case 1 -> ...` có thể dùng như một switch statement. Khi có nhiều lệnh trong nhánh, đặt vào `{ ... }`. Không cần `break` cuối mỗi nhánh dạng arrow.

## 10. Các trường hợp kiểm thử nên có

| Input/đường đi | Kết quả cần xác minh |
| --- | --- |
| `0` ngay tại menu đầu tiên | In `Goodbye.`, không hỏi số điểm |
| `1, 7, 2, 0` (mỗi giá trị một dòng) | In `Added: 7`, sau đó `Total: 7`, rồi thoát |
| `1, 7, 3, 2, 0` | Tổng cuối là 0 sau khi reset |
| `abc, 0` | Báo lỗi định dạng, hỏi lại, sau đó thoát |
| `-1, 4, 0` | Báo lỗi phạm vi hai lần rồi thoát |
| `1, abc, 5, 2, 0` | Không thêm `abc`; thêm 5 điểm và xem `Total: 5` |
| `1, 1001, 1000, 2, 0` | Báo lỗi cho 1001, chấp nhận biên 1000, tổng 1000 |
| `1, 0, 1, 2, 0` | Điểm 0 sai; sau đó thêm điểm 1 |
| Một file rỗng | In `Input ended.` và kết thúc |
| Chỉ nhập `1` rồi EOF | Đã vào nhánh thêm điểm nhưng kết thúc an toàn vì không có điểm |
| Dòng có khoảng trắng ` 2 ` | `strip()` giúp parse thành lựa chọn 2 |
| Số ngoài miền `int` | `NumberFormatException` được bắt, hỏi lại mà không crash |

**Nguyên tắc kiểm thử:** Mỗi kịch bản dùng **một lần chạy riêng** và đưa các mục trong cột input thành **các dòng liên tiếp**, không phải gõ dấu phẩy giữa các số. Nếu một trường hợp có lỗi, phải kiểm tra cả việc trạng thái không bị thay đổi sai, không chỉ kiểm tra có thông báo lỗi hay không.

## 11. Những lỗi lập trình phổ biến và cách tránh

**(a) Đặt `total` trong vòng lặp menu.** Nếu mỗi lần hiện menu đều chạy `long total = 0;`, điểm sẽ bị mất. Khởi tạo tổng **một lần** trước vòng lặp.

**(b) Không tiêu thụ dữ liệu sai.** Với `Scanner.nextInt()`, một token sai có thể vẫn còn trong luồng; nếu cứ `hasNextInt()` mà không bỏ token, vòng lặp sẽ đứng tại dữ liệu lỗi. Cách đọc `nextLine()` của bài mẫu **tiêu thụ cả dòng** trước khi parse nên mỗi lượt kiểm tra tiến tới dòng tiếp theo.

**(c) Dùng `if` liên tiếp mà thiếu trường hợp thoát.** Menu càng nhiều lựa chọn càng khó kiểm soát nếu mọi hành động chỉ là các `if` độc lập. `switch` làm rõ mỗi lựa chọn tương ứng một hành động, còn vòng ngoài quản lý việc hiển thị lại.

**(d) Bắt mọi `Exception` rồi bỏ qua.** Việc che mọi lỗi làm khó phân biệt dữ liệu sai với bug lập trình. `readIntInRange()` chỉ bắt `NumberFormatException`, tức lỗi được dự kiến khi parse đầu vào.

**(e) Không kiểm tra EOF.** Gọi `scanner.nextLine()` khi không còn dòng có thể ném `NoSuchElementException`. Dùng `hasNextLine()` rồi cho caller biết EOF bằng giá trị trả về có ý nghĩa.

**(f) Nhầm số nguyên với số thực.** `1.0` có giá trị số học bằng 1 nhưng không phải chuỗi số nguyên theo cú pháp `Integer.parseInt()`; muốn chấp nhận số thực phải có quy tắc chuyển kiểu riêng, không âm thầm làm tròn.

**(g) Sai ranh giới.** Lựa chọn `0` và `3` đều hợp lệ; `-1` và `4` không hợp lệ. Số điểm `1` và `1000` hợp lệ; `0` và `1001` không. Kiểm thử đúng các mốc này tránh lỗi dùng `<` thay vì `<=`.

**(h) Tạo nhiều Scanner cho `System.in`.** Các scanner có bộ đệm riêng và có thể cạnh tranh cùng luồng nhập; nên dùng một scanner xuyên suốt phiên tương tác. Việc đóng `Scanner(System.in)` sẽ đóng cả luồng đầu vào bên dưới; ví dụ đóng khi kết thúc `main`, phù hợp với bài thực hành đơn lẻ.

**(i) Để menu lặp mãi mà không có đường thoát.** `while (true)` chỉ phù hợp khi có một hoặc nhiều con đường thoát xác định: lựa chọn 0, EOF hoặc lỗi nghiêm trọng được xử lý bằng cách kết thúc an toàn.

## 12. Hướng cải tiến sau khi chạy đúng

Khi muốn mở rộng menu, nên giữ nguyên các nguyên tắc trước:

1. **Tách từng hành động thành một phương thức** nếu nhánh bắt đầu dài. Ví dụ `showTotal(total)` hoặc `addPoints(scanner, total)` cần hợp đồng rõ: có thay đổi trạng thái không, xử lý EOF thế nào.
2. **Giữ logic và nhập xuất tách biệt** khi ứng dụng lớn lên. Phép cộng điểm có thể trở thành hàm thuần để kiểm thử mà không cần Scanner.
3. **Thêm tùy chọn xem lịch sử cộng điểm** nếu bài yêu cầu lưu các lần thay đổi; khi đó cần danh sách hoặc kiểu bản ghi, không chỉ biến tổng.
4. **Áp dụng giới hạn tổng hoặc `Math.addExact()`** nếu số lượt cộng không được kiểm soát và sự chính xác của số lớn là yêu cầu nghiêm ngặt.
5. **Xác định ngôn ngữ/định dạng thông báo** và tài liệu cho người dùng nếu menu phục vụ nhiều đối tượng.
6. **Viết test cho xử lý input** với các chuỗi cố định qua `new Scanner(String)` hoặc chạy chương trình với file/pipe, so khớp stdout. Với trường hợp lớn, có thể dùng JUnit nhưng không bắt buộc cho Java core.

Đừng mở rộng quá sớm: đầu tiên hãy bảo đảm menu đơn giản **hiển thị lại đúng, kiểm tra input đúng, không làm thay đổi tổng khi nhập sai và thoát có kiểm soát**.

## 13. Checklist tổng kết

Trước khi coi chương trình hoàn thành, hãy tự xác nhận:

- Menu có các lựa chọn rõ ràng `0`–`3`, và chỉ số trong `switch` khớp lời nhắc?
- `while` có đường thoát `0` và EOF?
- Nhập chuỗi, dòng rỗng, số vượt phạm vi hoặc số vượt kiểu `int` không làm chương trình crash?
- Dữ liệu sai có dẫn tới **hỏi lại đúng trường**, không tự thêm điểm hay đổi tổng?
- Sau lựa chọn 1, 2, 3, menu có được hiển thị lại?
- `total` có giữ nguyên qua các lượt, trừ khi được cộng hoặc đặt lại có chủ đích?
- `0` để thoát có khác `null` do EOF?
- Chương trình dùng một `Scanner` có vòng đời phù hợp, tránh trộn `nextInt()` và `nextLine()` gây đọc nhầm newline?
- Đã chạy `javac`/ `java` và đối chiếu kịch bản hợp lệ, dữ liệu sai và EOF chưa?

**Ghi nhớ:** Một menu console chắc chắn cần **vòng lặp để duy trì tương tác, `switch` để phân nhánh hành động, kiểm tra đầu vào ở ngay ranh giới nhập liệu và đường thoát rõ ràng**. Viết đúng bốn phần này giúp bài tập hoạt động dự đoán được ngay cả khi người dùng nhập sai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practice-and-quality-010">Tổng hợp lỗi thường gặp và tiêu chí tự đánh giá nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Tổng hợp lỗi thường gặp và tiêu chí tự đánh giá nền tảng Java Core

Biết cú pháp Java chỉ là bước đầu. Năng lực nền tảng còn thể hiện ở việc dự đoán được chương trình chạy như thế nào, phát hiện lỗi biên dịch, lỗi khi chạy và lỗi logic, giải thích nguyên nhân rồi kiểm chứng cách sửa. Bài này hệ thống hóa những sai sót phổ biến khi học **Java 17+ (language-basics)** và đưa ra tiêu chí tự đánh giá cụ thể, có thể thực hành độc lập.

## 1. Phân biệt ba loại lỗi trước khi sửa

| Loại | Dấu hiệu | Ví dụ | Cách tiếp cận |
| --- | --- | --- | --- |
| **Lỗi biên dịch (compile-time)** | `javac` báo lỗi, chưa có chương trình hợp lệ để chạy | Thiếu `;`, sai kiểu, gọi phương thức không tồn tại, dùng biến chưa khai báo | Đọc dòng/cột và thông báo đầu tiên; sửa nguyên nhân, biên dịch lại |
| **Lỗi khi chạy (runtime)** | Đã biên dịch nhưng một luồng thực thi ném ngoại lệ hoặc kết thúc bất thường | `NullPointerException`, `ArrayIndexOutOfBoundsException`, `NumberFormatException` | Đọc loại ngoại lệ và stack trace; kiểm tra dữ liệu thực tế, xác định đường chạy |
| **Lỗi logic** | Chương trình chạy xong nhưng kết quả sai | Chia nguyên khi cần thập phân, dùng `<` thay `<=`, tính tổng bị tràn `int` | Viết đầu ra mong đợi, thử giá trị biên, so sánh kết quả và lần theo biến |

Ngoài ra còn có **lỗi thiết kế/khả năng bảo trì**: tên mơ hồ, phương thức quá dài, lặp quy tắc, trộn trách nhiệm, hoặc xử lý lỗi không nhất quán. Chúng có thể chưa gây ra kết quả sai ngay nhưng làm tăng rủi ro khi sửa đổi.

Khi gặp lỗi, hãy xác định **đầu vào → điều kiện/biểu thức → kết quả thực tế → kết quả mong đợi**. Tránh sửa nhiều chỗ cùng lúc rồi không biết thay đổi nào có tác dụng.

## 2. Các lỗi phổ biến theo nhóm kiến thức

### 2.1. JDK, tệp mã nguồn và cách chạy

- Nhầm **JDK** với **JRE**: muốn biên dịch cần `javac` từ JDK; `java` dùng chạy chương trình. Kiểm tra phiên bản của cả hai.
- Không khớp tên tệp với lớp `public` cấp cao: `public class Hello` phải nằm trong `Hello.java`; Java phân biệt hoa/thường trong định danh.
- Sai điểm khởi động: với chương trình cơ bản, phương thức chạy là `public static void main(String[] args)`; thiếu `static` hoặc sai chữ ký khiến JVM không nhận đúng entry point.
- Gọi `java Hello.java` và `java Hello` là **hai cơ chế khác nhau**. Lệnh `java Hello` chạy lớp đã biên dịch; lệnh `java Hello.java` có thể chạy trực tiếp tệp nguồn ở JDK hỗ trợ source-file mode.
- Sai `package` hoặc classpath: nếu tệp khai báo `package demo;`, biên dịch tại thư mục gốc nguồn bằng `javac -d out demo/Hello.java` rồi chạy `java -cp out demo.Hello`, không chạy `java Hello`.

Ví dụ độc lập (không khai báo `package`) lưu ở `Hello.java`:

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello Java");
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 Hello.java
java Hello
```

Stdout:

```text
Hello Java
```

Nếu báo lỗi “cannot find symbol”, không nên chỉ thay đường dẫn chạy: lỗi này thường là **biên dịch** vì một tên/kiểu không được nhận ra. Nếu “Could not find or load main class”, kiểm tra tên lớp, thư mục hiện tại, package và classpath.

### 2.2. Kiểu dữ liệu, biểu thức và chuyển đổi

| Sai sót | Điều gì thực sự xảy ra | Cách sửa hoặc kiểm tra |
| --- | --- | --- |
| `5 / 2` được kỳ vọng là `2.5` | Hai `int` chia nguyên, kết quả `2` | `5 / 2.0` hoặc `(double) 5 / 2` |
| `double ratio = a / b;` với `a,b` là `int` | Chia nguyên **trước khi** gán sang `double` | Ép kiểu trước phép chia: `(double) a / b` |
| Ép `long` sau phép nhân hai `int` | Tích có thể đã bị tràn `int` | `(long) a * b` hoặc `Math.multiplyExact` để phát hiện tràn |
| Nhầm `=` và `==` | `=` gán, `==` so sánh giá trị với primitive | Viết điều kiện rõ ràng; tránh gán trong điều kiện |
| Đánh giá sai thứ tự `&&`/`||` | `&&` có độ ưu tiên cao hơn `||` | Thêm ngoặc thể hiện ý định và kiểm thử nhánh |
| Dùng `&` thay `&&` với boolean | `&` đánh giá cả hai vế; `&&` có short-circuit | Với kiểm tra null, dùng `s != null && ...` |
| Tin `final` làm toàn bộ đối tượng bất biến | `final` ngăn gán lại biến/tham chiếu, không khóa trạng thái đối tượng | Phân biệt final với immutable object |
| Hiểu `var` là kiểu động | `var` suy luận kiểu tĩnh cho biến cục bộ có initializer | Giá trị gán tiếp vẫn phải hợp kiểu đã suy luận |

Chẳng hạn, `(long) (a * b)` vẫn có thể sai vì tích `int` được tính trong ngoặc trước khi ép kiểu. Với tính toán tiền tệ có số lẻ, cần quy tắc về đơn vị và làm tròn; `double` không mặc nhiên phù hợp với kết quả thập phân chính xác.

### 2.3. Rẽ nhánh, vòng lặp và phạm vi

- **Sai một đơn vị (off-by-one):** mảng dài `n` có các chỉ số `0` đến `n - 1`. Dùng `i < array.length` khi đi hết mảng, tránh `i <= array.length`.
- **Dấu ngoặc nhọn thiếu:** thụt lề không quyết định khối lệnh; sau `if` không có `{}`, chỉ một statement thuộc nhánh đó.
- **Điều kiện vòng lặp không tiến tới dừng:** quên tăng/giảm biến đếm, hoặc thay đổi nhầm biến điều khiển.
- **Đổi `if` riêng lẻ thành `if/else if` mà không xét ý nghĩa:** các `if` độc lập có thể cùng đúng, chuỗi `else if` chỉ chọn một nhánh.
- **Quên `break` trong `switch` kiểu cũ:** thực thi có thể rơi xuống các `case` tiếp theo; dạng `case X ->` trong Java 17 tránh kiểu fall-through này.
- **Đóng/mở biên sai:** `score >= 50` khác `score > 50`; bắt buộc thử điểm `49`, `50`, `51`.

Đoạn dưới **là mã minh họa trong một phương thức**, không phải chương trình độc lập:

```java
for (int i = 0; i < numbers.length; i++) {
    System.out.println(numbers[i]);
}

if (score >= 50) {
    System.out.println("PASS");
} else {
    System.out.println("FAIL");
}
```

Khi đọc điều kiện, xác định rõ giá trị ở ranh giới và xem có nhánh nào không thể chạy vì thứ tự điều kiện đã bao phủ toàn bộ miền giá trị hay không.

### 2.4. Chuỗi, mảng, null và tham chiếu

- `==` so sánh **giá trị primitive** hoặc **cùng đối tượng tham chiếu**; để so nội dung `String`, dùng `equals`. `"Java" == new String("Java")` thường không cho kết quả muốn tìm dù hai chuỗi có cùng ký tự.
- `String` bất biến: `text.trim()`, `text.strip()`, `text.replace(...)` trả **chuỗi mới**; muốn giữ thay đổi phải gán lại hoặc chuyển giá trị trả về.
- `null`, chuỗi rỗng `""`, và chuỗi trắng `"   "` không đồng nghĩa. `isEmpty()` kiểm tra độ dài bằng 0; `isBlank()` kiểm tra rỗng hoặc chỉ chứa ký tự khoảng trắng theo quy tắc Java.
- Kiểm tra null trước khi gọi phương thức: `name != null && !name.isBlank()` an toàn nhờ short-circuit; đảo thứ tự có thể gây `NullPointerException`.
- Mảng dùng thuộc tính `length`; `String` dùng phương thức `length()`. Độ dài `String` đếm đơn vị mã UTF-16; không đồng nhất với số ký tự người dùng nhìn thấy.
- Gán `int[] copy = original;` tạo **bí danh tham chiếu**, không sao chép phần tử. Dùng `original.clone()` hoặc `Arrays.copyOf` nếu muốn bản sao mảng một chiều độc lập.
- Java luôn truyền tham số **theo giá trị**; đối với đối tượng, giá trị được truyền là bản sao của tham chiếu. Do đó phương thức có thể làm đổi trạng thái đối tượng được trỏ tới, nhưng gán lại biến tham số không đổi biến tham chiếu của người gọi.

Một bẫy khi nối chuỗi là tính từ trái sang phải; trong ngữ cảnh phương thức:

```java
System.out.println(1 + 2 + "3");  // 33
System.out.println("3" + 1 + 2);  // 312
```

Trước khi dự đoán stdout, hãy xét kiểu và thứ tự của **từng toán hạng**, không xem toàn biểu thức như một phép cộng số.

### 2.5. Phương thức, ngoại lệ và chất lượng mã

- Không phân biệt tham số (parameter) với đối số (argument), hoặc bỏ quên giá trị trả về của phương thức không làm thay đổi đối tượng gốc.
- Nhầm `static` với thành viên của từng đối tượng. Phương thức `static` không có tham chiếu `this` và không truy cập trực tiếp trường instance nếu thiếu đối tượng.
- Không kiểm tra dữ liệu trước `Integer.parseInt`, đọc chỉ số mảng, hoặc chia nguyên cho `0`. Ngoại lệ không phải kết quả nghiệp vụ hợp lệ.
- Bắt `Exception` rồi bỏ qua lỗi hoặc trả `0` khiến sai dữ liệu giống như thành công; phân biệt lỗi nhập liệu với lỗi lập trình, thông báo rõ và để nơi gọi xử lý phù hợp.
- Đặt tên `a`, `tmp`, `x` cho biến nghiệp vụ, lặp số “ma thuật” như `86400000`, hoặc viết phương thức quá dài làm người khác khó xác định quy tắc. Ưu tiên `timeoutMillis`, `MILLIS_PER_DAY`, các phương thức nhỏ có một nhiệm vụ.
- Viết test chỉ cho dữ liệu thuận lợi. Cần có `null`, chuỗi rỗng, số âm, hai phía giới hạn, dữ liệu sai định dạng, giá trị lớn và các nhánh lỗi liên quan.

## 3. Chương trình Java 17+ tự kiểm tra các lỗi nền tảng

Chương trình sau tạo 9 phép kiểm tra độc lập có tên rõ ràng. Nếu bất cứ kiểm tra nào sai, `AssertionError` làm chương trình thất bại; vì vậy **không cần bật `-ea`**. Lưu tệp `FoundationSelfCheck.java`, không khai báo `package`:

```java
public final class FoundationSelfCheck {
    private static final int PASS_SCORE = 50;
    private static final int MAX_SCORE = 100;
    private static int passed;

    private FoundationSelfCheck() {
    }

    public static void main(String[] args) {
        check("integer division", 5 / 2 == 2 && 5 / 2.0 == 2.5);

        String separateObject = new String("Java");
        check("string equality", "Java".equals(separateObject));

        String originalText = " Java ";
        String strippedText = originalText.strip();
        check("string immutability",
                originalText.equals(" Java ") && strippedText.equals("Java"));

        int[] numbers = {1, 2, 3};
        int sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            sum += numbers[i];
        }
        check("loop bounds", sum == 6);

        check("boundary scores",
                grade(49).equals("FAIL")
                        && grade(50).equals("PASS")
                        && grade(100).equals("PASS"));

        check("empty and null",
                !hasName(null) && !hasName("") && !hasName("   ")
                        && hasName(" Linh "));

        int[] originalArray = {3};
        int[] alias = originalArray;
        alias[0] = 4;
        check("reference alias", originalArray[0] == 4);

        check("integer overflow", detectsOverflow());
        check("invalid score", rejectsScore(-1) && rejectsScore(101));

        System.out.println("Summary: " + passed + "/9 PASS");
    }

    private static String grade(int score) {
        if (score < 0 || score > MAX_SCORE) {
            throw new IllegalArgumentException("score out of range");
        }
        return score >= PASS_SCORE ? "PASS" : "FAIL";
    }

    private static boolean hasName(String name) {
        return name != null && !name.isBlank();
    }

    private static boolean detectsOverflow() {
        try {
            Math.multiplyExact(Integer.MAX_VALUE, 2);
            return false;
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private static boolean rejectsScore(int score) {
        try {
            grade(score);
            return false;
        } catch (IllegalArgumentException exception) {
            return "score out of range".equals(exception.getMessage());
        }
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError(name + ": FAIL");
        }
        passed++;
        System.out.println(name + ": PASS");
    }
}
```

Từ thư mục có tệp `FoundationSelfCheck.java`, chạy trên Windows PowerShell/CMD, macOS hoặc Linux với JDK 17+:

```bash
javac -encoding UTF-8 FoundationSelfCheck.java
java FoundationSelfCheck
```

Stdout **chính xác**:

```text
integer division: PASS
string equality: PASS
string immutability: PASS
loop bounds: PASS
boundary scores: PASS
empty and null: PASS
reference alias: PASS
integer overflow: PASS
invalid score: PASS
Summary: 9/9 PASS
```

Chương trình chứng minh một số điểm rất dễ nhầm: `"Java".equals(...)` so nội dung, `strip()` trả chuỗi mới, điều kiện vòng lặp không truy cập quá chỉ số, `null` được kiểm tra an toàn, hai biến mảng có thể cùng trỏ một đối tượng, và `Math.multiplyExact` ném `ArithmeticException` khi phép nhân `int` bị tràn.

Để tự kiểm tra kỹ hơn, hãy **thử thay đổi có chủ đích** trong một bản sao: đổi `i < numbers.length` thành `i <= numbers.length`, đổi `grade(50)` sang điều kiện `score > PASS_SCORE`, hoặc bỏ `name != null &&`. Quan sát ngoại lệ hay thông báo thất bại và giải thích vì sao. Sau đó khôi phục mã đúng. Không nên dùng những phiên bản cố ý sai này trong ứng dụng thực tế.

## 4. Bộ tiêu chí tự đánh giá nền tảng

Chấm mỗi tiêu chí theo cùng một thang: **0 điểm** = chưa làm được hoặc chỉ đoán; **1 điểm** = làm được với tài liệu/gợi ý nhưng chưa giải thích được đầy đủ; **2 điểm** = tự giải thích, viết và kiểm tra ví dụ đúng không cần gợi ý. Tổng tối đa **24 điểm**.

| STT | Tiêu chí | Bằng chứng thực hành để tự chấm |
| --- | --- | --- |
| 1 | JDK và vòng đời chạy chương trình | Biên dịch/chạy bằng `javac` và `java`; giải thích tệp `.java`/`.class`, tên lớp, main, package |
| 2 | Cú pháp, quy ước tên và phạm vi biến | Tạo lớp và phương thức Java hợp lệ; phân biệt biến cục bộ, field và `static`; đặt tên có nghĩa |
| 3 | Kiểu primitive, reference và chuyển kiểu | Giải thích `int`/`long`/`double`/`boolean`/`String`; nhận ra widening/narrowing và nguy cơ mất dữ liệu |
| 4 | Toán tử và thứ tự tính | Tự dự đoán kết quả chia nguyên, nối chuỗi, `&&`, `||`, tăng giảm; dùng ngoặc cho ý định rõ |
| 5 | Điều kiện và xử lý biên | Viết `if/else`, `switch`, kiểm tra mốc ngay dưới/đúng/trên ngưỡng |
| 6 | Vòng lặp và mảng | Duyệt mảng không vượt cận, tính tổng chính xác, giải thích `break`/`continue` |
| 7 | Phương thức và truyền tham số | Tách hàm nhỏ, dùng tham số/return đúng; giải thích Java pass-by-value |
| 8 | String, null và tham chiếu | Dùng `equals`, `isBlank`, tránh NPE, phân biệt alias với bản sao và String immutable |
| 9 | Xử lý đầu vào không hợp lệ | Phân biệt định dạng sai và miền giá trị sai; kiểm tra ngoại lệ có chủ đích |
| 10 | Số lớn và giới hạn kiểu | Giải thích tràn `int`, dùng `long` trước phép toán, `Math.multiplyExact` khi cần |
| 11 | Đọc lỗi và sửa lỗi có căn cứ | Phân loại compile/runtime/logic, dựa vào lỗi và ca tái hiện thay vì đoán |
| 12 | Chất lượng mã và kiểm thử cơ bản | Format nhất quán, tránh magic numbers, dự đoán stdout và kiểm tra ít nhất một ca biên |

**Cách đọc điểm tham khảo:**

- **0–11/24:** cần củng cố cú pháp và khả năng tự biên dịch/chạy các bài nhỏ. Chọn những tiêu chí 0 điểm làm lại từ đầu.
- **12–19/24:** đã có nền tảng nhưng còn lỗ hổng. Tập trung vào tiêu chí 0–1 điểm; sau mỗi lần sửa phải thêm một test thể hiện lỗi cũ.
- **20–24/24:** có cơ sở để tiếp tục bài thực hành phức tạp hơn. Vẫn cần xác nhận năng lực bằng mã tự viết và xử lý dữ liệu bất thường, không chỉ điểm tự chấm.

Thang trên là **rubric học tập**, không phải chứng chỉ hay bảo đảm về năng lực nghề nghiệp. Điểm chỉ có ý nghĩa khi dựa trên bài chạy được và lời giải thích độc lập.

## 5. Bài thực hành tổng hợp để chứng minh mức độ nắm vững

**Đề tự làm:** Viết một chương trình Java đọc (hoặc mô phỏng) danh sách điểm số nguyên từ `0` đến `100`, phân loại đạt khi điểm từ `50` trở lên, tính số lượng đạt và tỷ lệ đạt. Chương trình cần xử lý danh sách rỗng và dữ liệu sai.

**Điều kiện hoàn thành có thể kiểm chứng:**

1. Khi có `[49, 50, 100]`, số lượng đạt phải là `2`, tỷ lệ đạt là `2 / 3.0` (khoảng `66.67%` nếu format hai chữ số thập phân). Không để phép chia nguyên tạo `0` trước khi chuyển thành `double`.
2. Khi danh sách rỗng, không chia cho `0`; trả về `0` theo hợp đồng đã nêu hoặc trạng thái “không có dữ liệu”.
3. Khi gặp `-1` hoặc `101`, chỉ rõ cách từ chối/ghi nhận lỗi; không tự ý sửa thành `0` hoặc `100`.
4. Nếu nhập từ chuỗi, kiểm tra `null`, `""`, `"abc"` và số vượt giới hạn `int`.
5. Vòng lặp đúng cận, tên biến có nghĩa, chương trình compile/run sạch; ít nhất 6 ca test gồm đầu vào thường, rỗng, sát cận, đúng cận, ngoài cận và sai định dạng.

Đối chiếu sau khi làm:

- Có giải thích được **vì sao** từng nhánh và phép toán cho kết quả đó không?
- Có phân biệt được lỗi phát sinh khi biên dịch, khi chạy và sai kết quả không?
- Có thử đầu vào bất thường và tự sửa được bằng thông báo lỗi/stack trace không?
- Có thể chạy lại tất cả test sau khi đổi một dòng mà không phải kiểm tra hoàn toàn bằng mắt không?

Nếu chưa làm được một mục, hãy biến mục đó thành ca kiểm tra nhỏ, có **đầu vào và kết quả mong đợi rõ ràng**. Nắm vững Java Core bắt đầu từ khả năng giải thích chính xác và kiểm chứng được hành vi của những chương trình nhỏ trước khi tăng độ phức tạp.


</details>

- [Quay lại đầu trang](#back-to-top)
