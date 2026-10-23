<a id="back-to-top"></a>

# Nhập xuất cơ bản

## Menu
- [System.out.print(), println() và printf()](#basic-io-001)
- [Định dạng số, chuỗi, xuống dòng và ký hiệu % trong printf()](#basic-io-002)
- [System.err cho thông báo chẩn đoán](#basic-io-003)
- [Đọc đối số chương trình từ String&#91;&#93; args](#basic-io-004)
- [Đọc dữ liệu console bằng Scanner](#basic-io-005)
- [Phân biệt next(), nextInt() và nextLine()](#basic-io-006)
- [Xử lý phần xuống dòng còn lại sau khi đọc số](#basic-io-007)
- [Kiểm tra đầu vào và chuyển chuỗi thành số](#basic-io-008)
- [Locale, dấu phân cách thập phân và trách nhiệm đóng nguồn nhập](#basic-io-009)

## <a id="basic-io-001">System.out.print(), println() và printf()</a>

<details>
<summary>Xem chi tiết</summary>

# `System.out.print()`, `println()` và `printf()` trong Java

## 1. Ba cách xuất dữ liệu ra màn hình console

Trong Java, `System.out` là một đối tượng `java.io.PrintStream` đại diện cho **luồng xuất chuẩn** (*standard output*, thường gọi là stdout). Ba nhóm phương thức quen thuộc nhất là:

- **`System.out.print(value)`**: in giá trị nhưng **không chủ động thêm ký tự xuống dòng**.
- **`System.out.println(value)`**: in giá trị rồi **kết thúc dòng**; `println()` không đối số chỉ in một dấu kết thúc dòng, thường dùng tạo dòng trống.
- **`System.out.printf(format, args...)`**: in một chuỗi **theo mẫu định dạng**, sử dụng những ký hiệu như `%s`, `%d`, `%.2f`. `printf()` **không tự thêm dấu kết thúc dòng** nếu chuỗi định dạng không yêu cầu.

Ví dụ đặt trong phương thức:

```java
System.out.print("Xin ");
System.out.print("chao");
System.out.println(" Java");
System.out.println("Dong moi");
System.out.println();
System.out.println("Sau mot dong trong");
```

Kết quả:

```text
Xin chao Java
Dong moi

Sau mot dong trong
```

Hai lần `print` đầu tiên viết tiếp trên **cùng dòng**. `println(" Java")` bổ sung nội dung và kết thúc dòng. `println()` không có đối số tạo một **dòng trống** trước nội dung tiếp theo.

**Đừng nhầm `print()` với `println()`:** `print("A")` không tự xuống dòng, còn `println("A")` có xuống dòng. Tương tự, `printf("A")` không tự thêm xuống dòng.

## 2. `print()`: in tiếp mà không tự xuống dòng

`print` hữu ích để ghép nội dung của một dòng từ nhiều phần nhỏ, chẳng hạn nhãn và giá trị.

```java
int count = 4;
System.out.print("So luong: ");
System.out.print(count);
System.out.println(" san pham");
```

Stdout:

```text
So luong: 4 san pham
```

Nếu chỉ gọi `print` mà không có ký tự xuống dòng trong giá trị truyền vào, lần in tiếp theo sẽ tiếp tục tại dòng hiện tại. Ví dụ `print("A\nB")` **có thể tạo xuống dòng** vì chính chuỗi chứa ký tự `\n`; việc không *tự động* thêm newline không có nghĩa giá trị được in không thể chứa newline.

`print` có các overload cho những kiểu như `boolean`, `char`, `char[]`, `int`, `long`, `float`, `double`, `String` và `Object`. Với giá trị nguyên thủy, Java chuyển thành dạng văn bản phù hợp. Với nhiều đối tượng, việc in sử dụng biểu diễn dạng chuỗi (`String.valueOf`/`toString()`). Cần nhớ `toString()` của đối tượng tự định nghĩa có thể không diễn tả các trường theo cách mình muốn.

Ví dụ:

```java
System.out.print(25);
System.out.print(" tuoi");
System.out.println();
```

Kết quả là một dòng `25 tuoi`.

## 3. `println()`: in và kết thúc dòng

`println` phù hợp với thông báo, ghi giá trị theo từng dòng và in kết quả sau mỗi bước.

```java
System.out.println("Java");
System.out.println(17);
System.out.println(true);
```

Stdout:

```text
Java
17
true
```

`println()` không đối số chỉ kết thúc dòng hiện tại. Khác với một dấu cách, ký tự kết thúc dòng ảnh hưởng cách terminal hiển thị dữ liệu.

### Nối chuỗi bằng toán tử `+`

Khi một toán hạng là `String`, toán tử `+` có thể dùng để nối chuỗi:

```java
int a = 2, b = 3;
System.out.println("Tong: " + (a + b)); // Tong: 5
System.out.println("Ket qua: " + a + b); // Ket qua: 23
System.out.println(a + b + " la tong"); // 5 la tong
```

**Vì sao khác nhau?** Trong `"Ket qua: " + a + b`, phép toán được đánh giá từ trái sang phải: sau khi nối chuỗi với `a`, phép `+ b` tiếp tục nối văn bản. Muốn tính toán trước khi nối, đặt phép tính trong ngoặc `(a + b)`.

### In mảng và đối tượng

```java
int[] numbers = {4, 6};
System.out.println(java.util.Arrays.toString(numbers)); // [4, 6]
```

Với mảng, **đừng kỳ vọng** `System.out.println(numbers)` tự in mọi phần tử. Mảng không ghi đè `toString()` theo dạng danh sách phần tử; kết quả thường có dạng thông tin kiểu và mã định danh, không phải `[4, 6]`. Dùng `Arrays.toString` cho mảng một chiều hoặc `Arrays.deepToString` cho mảng lồng thích hợp.

## 4. `printf()`: in theo mẫu thay vì nối chuỗi thủ công

`printf(format, args...)` nhận chuỗi mẫu có các ký hiệu bắt đầu bằng `%` và những đối số tương ứng. Ví dụ `System.out.printf("Ten: %s; Tuoi: %d%n", "An", 18);` in `Ten: An; Tuoi: 18` rồi xuống dòng. Khác `println()`, `printf()` **không tự thêm** ký tự kết thúc dòng.

| Ký hiệu | Ý nghĩa trong ví dụ cơ bản |
| --- | --- |
| `%s` / `%d` | Văn bản / số nguyên thập phân |
| `%.2f` | Số thực với hai chữ số thập phân |
| `%04d` / `%-6s` | Đệm số bằng 0 đến độ rộng 4 / căn trái chuỗi trong vùng rộng 6 |
| `%%` / `%n` | Một dấu phần trăm / ký tự kết thúc dòng của nền tảng |

Định dạng số thực chịu ảnh hưởng của `Locale`. Các cờ, độ rộng, precision, chỉ số đối số và ngoại lệ của `Formatter` được thực hành chi tiết ở bài về định dạng `printf()`; ví dụ chạy độc lập ngay dưới đây minh họa sự khác biệt giữa ba phương thức in.

## 5. Chọn `Locale` để đầu ra có thể đối chiếu

`System.out.printf(Locale.US, "Gia: %.2f%n", 12.5);` in `Gia: 12.50` rồi kết thúc dòng. Nếu bỏ `Locale.US`, dấu thập phân của `%.2f` có thể theo locale mặc định và khác giữa các máy. Chọn locale phù hợp với người đọc hoặc định dạng trao đổi; `Locale.US` chỉ cố định ví dụ đang dùng dấu chấm.

`%n` yêu cầu dấu kết thúc dòng của nền tảng, còn escape `\n` trong Java string biểu diễn ký tự LF. Bài định dạng `printf()` giải thích thêm sự khác biệt này khi chạy đa hệ điều hành.

## 6. Ví dụ Java 17+ chạy độc lập và kết quả chính xác

Tạo tệp **`ConsoleOutputDemo.java`**:

```java
import java.util.Arrays;
import java.util.Locale;

public class ConsoleOutputDemo {
    public static void main(String[] args) {
        System.out.print("Xin ");
        System.out.print("chao");
        System.out.println(" Java");
        System.out.println("Dong trong");
        System.out.println();

        System.out.println(2 + 3);
        System.out.println("Tong: " + (2 + 3));

        System.out.printf(Locale.US, "So luong: %d%n", 7);
        System.out.printf(Locale.US, "Gia: %.2f%n", 12.5);
        System.out.printf(Locale.US, "Bang: |%-6s|%04d|%n", "A", 9);
        System.out.printf(Locale.US, "Ty le: %.1f%%%n", 87.4);
        System.out.printf(Locale.US, "Hex: %x, ky tu: %c, dung: %b%n",
                          255, 'Z', true);

        String saved = String.format(Locale.US, "Ma=%03d", 5);
        System.out.println("Chuoi: " + saved);

        int[] values = {4, 6};
        System.out.println("Mang: " + Arrays.toString(values));
    }
}
```

Mở terminal tại thư mục chứa tệp, dùng **JDK 17 trở lên** trên Windows (PowerShell/CMD) hoặc Linux/macOS:

```bash
javac -encoding UTF-8 ConsoleOutputDemo.java
java ConsoleOutputDemo
```

Stdout chính xác (có **một dòng trống** sau `Dong trong`; lệnh `javac` thành công không in gì):

```text
Xin chao Java
Dong trong

5
Tong: 5
So luong: 7
Gia: 12.50
Bang: |A     |0009|
Ty le: 87.4%
Hex: ff, ky tu: Z, dung: true
Chuoi: Ma=005
Mang: [4, 6]
```

Phân tích:

1. Hai `print()` đầu nối tiếp trên cùng dòng; `println(" Java")` kết thúc dòng ấy.
2. `println()` không đối số tạo dòng trắng.
3. `println(2 + 3)` in kết quả phép cộng số học là `5`.
4. `printf` xuất đúng kiểu và độ rộng của `%d`, `%.2f`, `%-6s`, `%04d`, `%.1f`, `%x`, `%c`, `%b`; `%%` là dấu phần trăm.
5. `String.format` **trả về `String`**, không tự in; sau đó `println` mới ghi chuỗi ấy ra console.
6. `Arrays.toString` biến nội dung mảng thành chuỗi dễ đọc.

## 7. Khi `printf()` báo lỗi hoặc dính dòng

Các định dạng sai có thể **biên dịch được nhưng ném ngoại lệ lúc chạy**: `printf("%d", "7")` dùng `String` thay cho số nguyên và gây `IllegalFormatConversionException`; `printf("%d %s", 3)` thiếu một đối số và gây `MissingFormatArgumentException`.

`%f` không tự chuyển `String` thành số thực; khi cần, hãy parse và validate dữ liệu trước. `printf("Ti le: %d%%%n", 80);` in `Ti le: 80%` rồi xuống dòng: `%%` in phần trăm, `%n` kết thúc dòng. Đừng viết một dấu `%` trần trong mẫu, và đừng trông chờ `printf()` tự xuống dòng. Bài định dạng chuyên sâu có bảng đầy đủ các lỗi về conversion, flag và precision.

## 8. Khi nào nên chọn phương thức nào?

| Nhu cầu | Phương thức phù hợp |
| --- | --- |
| In một thông báo đơn giản và xuống dòng | `println("Da xong")` |
| In nhiều phần của cùng một dòng | `print(...)`, rồi `println(...)` khi muốn kết thúc |
| In nhiều giá trị cần độ rộng, số chữ số thập phân, padding | `printf(...)` |
| Tạo chuỗi được định dạng để lưu hoặc trả về, **không in ngay** | `String.format(...)` |
| Xuất lỗi/chẩn đoán riêng luồng stdout | `System.err.println(...)` nếu phù hợp |

`System.out` là **standard output**; `System.err` là **standard error**, thường dùng cho thông báo lỗi. Terminal có thể hiển thị cả hai trên cùng màn hình, nhưng chúng là hai luồng riêng và có thể được chuyển hướng riêng.

Ví dụ:

```bash
java ConsoleOutputDemo > output.txt
```

Lệnh này chuyển hướng stdout vào tệp `output.txt` theo cú pháp shell thường gặp. Cách mã hóa tệp từ việc chuyển hướng có thể khác theo môi trường shell; khi ứng dụng cần định dạng/ký tự đầu ra chuẩn xác trong tệp, nên dùng API ghi file với charset rõ ràng thay vì phụ thuộc ngầm vào cấu hình terminal.

## 9. Kiểm tra nhanh trước khi chạy

- Nếu các phần bị dính nhau: xem có dùng nhầm `print` thay vì `println` hoặc quên `%n` trong `printf` không.
- Nếu kết quả toán học bị nối thành chuỗi: thêm ngoặc quanh phép tính trước khi dùng `+` với `String`.
- Nếu hiển thị số thập phân không như mong muốn: kiểm tra `%.nf`, kiểu đối số và `Locale`.
- Nếu định dạng ném ngoại lệ: so khớp từng ký hiệu `%d`, `%f`, `%s` với số lượng và kiểu đối số thực tế.
- Nếu mảng in ra dạng khó đọc: dùng `Arrays.toString` hoặc `Arrays.deepToString` khi phù hợp.
- Nếu thấy thông báo lỗi cùng chỗ với kết quả: nhớ phân biệt `System.err` và `System.out`; các luồng có thể được hiển thị cùng một terminal.

**Ghi nhớ:** `print` in tiếp, `println` in rồi xuống dòng, còn `printf` in theo mẫu và chỉ xuống dòng khi mẫu có ký tự kết thúc dòng. Chọn đúng phương thức giúp kết quả console dễ đọc và có thể kiểm tra chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-002">Định dạng số, chuỗi, xuống dòng và ký hiệu % trong printf()</a>

<details>
<summary>Xem chi tiết</summary>

# Định dạng số, chuỗi, xuống dòng và ký hiệu % trong `printf()` (Java 17+)

## 1. `printf()` dùng để làm gì?

Trong Java, `System.out.printf(...)` ghi văn bản đã định dạng ra luồng chuẩn `stdout`. Thay vì tự nối nhiều chuỗi, ta viết **format string** chứa các chỉ thị bắt đầu bằng `%`, rồi đưa các giá trị tương ứng vào.

```java
System.out.printf("Name: %s, age: %d%n", "An", 20);
```

Dòng in ra là `Name: An, age: 20` rồi xuống dòng. `%s` định dạng chuỗi hoặc đối tượng, `%d` định dạng số nguyên hệ thập phân, còn `%n` là dấu xuống dòng phụ thuộc nền tảng.

So sánh các phương thức:

| Phương thức | Vai trò | Tự thêm xuống dòng? |
| --- | --- | --- |
| `System.out.print(value)` | In giá trị trực tiếp | Không |
| `System.out.println(value)` | In giá trị rồi xuống dòng | Có |
| `System.out.printf(format, args...)` | In theo mẫu với các conversion/flags/width/precision | **Không**, trừ khi mẫu có `%n` hay ký tự xuống dòng |
| `String.format(format, args...)` | Tạo và **trả về** chuỗi đã định dạng để dùng tiếp | Không tự in ra console |

`printf()` trả về chính `PrintStream` để có thể gọi nối tiếp; trong bài nhập xuất cơ bản, thông thường chỉ cần quan tâm nội dung nó in ra.

## 2. Cấu trúc một chỉ thị định dạng

Dạng tổng quát phổ biến:

```text
%[argument_index$][flags][width][.precision]conversion
```

Các phần trong ngoặc vuông là tùy chọn; đây là mô tả cú pháp, không phải biểu thức Java để dán và chạy.

- `argument_index$`: lấy tham số ở vị trí cụ thể, đếm từ 1. Ví dụ `%2$s` chọn đối số thứ hai và định dạng bằng `%s`.
- `flags`: quy định căn lề, dấu, thêm số 0, phân nhóm chữ số... Một số cờ chỉ hợp lệ với những loại chuyển đổi nhất định.
- `width`: **độ rộng tối thiểu** của vùng hiển thị, tính bằng ký tự theo quy tắc Formatter. Nếu giá trị dài hơn, nó không tự bị cắt.
- `.precision`: ý nghĩa tùy loại: `%.2f` là hai chữ số sau dấu thập phân; `%.4s` giữ tối đa bốn ký tự trong chuỗi theo cách Formatter xử lý.
- `conversion`: ký hiệu loại giá trị cần in: `d`, `f`, `s`, `x`, `c`, v.v.

Ví dụ `%8.2f`: in số thực với hai chữ số thập phân, vùng rộng tối thiểu tám ký tự và căn phải theo mặc định. `%-8.2f`: cùng độ rộng nhưng căn trái. `%08.2f`: đệm bằng `0` ở phía trái (khi cờ `0` hợp lệ cho loại định dạng đó).

## 3. Các ký hiệu cơ bản cần nhớ

| Ký hiệu | Dùng với | Ví dụ giá trị | Kết quả |
| --- | --- | --- | --- |
| `%d` | Số nguyên thập phân (`byte`, `short`, `int`, `long`, `BigInteger` tương thích) | `42` | `42` |
| `%x` / `%X` | Số nguyên dạng hệ 16, chữ thường/chữ hoa | `26` | `1a` / `1A` |
| `%o` | Số nguyên dạng hệ 8 | `26` | `32` |
| `%f` | Số thực dạng thập phân (`float`, `double`, `BigDecimal`) | `12.5` | `12.500000` khi dùng `Locale.ROOT` |
| `%.2f` | Số thực, hai chữ số sau dấu thập phân | `12.5` | `12.50` |
| `%e` | Số thực dạng khoa học | `12.5` | `1.250000e+01` |
| `%s` | Chuỗi/đối tượng; dùng cách biểu diễn chuỗi thích hợp | `"Java"` | `Java` |
| `%S` | Chuỗi dạng chữ hoa theo locale thích hợp | `"java"` | `JAVA` |
| `%c` | Ký tự hoặc mã Unicode hợp lệ ở dạng số nguyên phù hợp | `'A'` | `A` |
| `%b` | Giá trị boolean hoặc các đối tượng khác | `true` | `true` |
| `%%` | Dấu phần trăm **nguyên văn** | Không cần tham số | `%` |
| `%n` | Dấu xuống dòng theo hệ điều hành | Không cần tham số | Xuống dòng |

**Lưu ý kiểu:** `%d` không tự chuyển chuỗi `"42"` thành số, và `%f` không tự chấp nhận mọi kiểu số nguyên. Đặt đúng loại dữ liệu tương ứng. Nếu muốn in một `int` dưới dạng số thực hai chữ số thập phân, chuyển đổi có chủ đích, ví dụ `System.out.printf("%.2f%n", (double) 42);`.

Với `%b`, kiểu `Boolean` dùng giá trị `true/false`, tham số `null` cho `false`, và đối tượng không phải `Boolean` khác `null` có thể được in thành `true`. Vì thế `%b` **không** có nghĩa Java tự biến mọi dữ liệu thành phép kiểm tra đúng/sai theo ý nghiệp vụ.

## 4. Ví dụ chạy được: số, chuỗi và dấu %

Tạo **`PrintfDemo.java`**:

```java
import java.util.Locale;

public class PrintfDemo {
    public static void main(String[] args) {
        System.out.printf(Locale.ROOT,
                "so=%d, hex=%x, oct=%o%n", 26, 26, 26);
        System.out.printf(Locale.ROOT,
                "thap-phan=%.2f, khoa-hoc=%.2e%n", 12.5, 12.5);
        System.out.printf(Locale.ROOT,
                "can-phai=[%6d], can-trai=[%-6d]%n", 42, 42);
        System.out.printf(Locale.ROOT,
                "them-zero=[%05d], them-dau=[%+d]%n", 42, 42);
        System.out.printf(Locale.ROOT,
                "nhom=[%,d]%n", 1234567);
        System.out.printf(Locale.ROOT,
                "chuoi=[%s], rut-gon=[%.4s], viet-hoa=[%S]%n",
                "Java", "language", "java");
        System.out.printf(Locale.ROOT,
                "ti-le=%.1f%%%n", 12.5);
        System.out.printf(Locale.ROOT,
                "doi-cho: %2$s (%1$d)%n", 7, "items");
    }
}
```

Tại thư mục chứa tệp, dùng **JDK 17+** (PowerShell, CMD hoặc bash). Tạo thư mục `out` nếu chưa có:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out PrintfDemo.java
java -cp out PrintfDemo
```

**Stdout chính xác** (các khoảng trắng trong dấu ngoặc vuông là thành phần có chủ đích của output):

```text
so=26, hex=1a, oct=32
thap-phan=12.50, khoa-hoc=1.25e+01
can-phai=[    42], can-trai=[42    ]
them-zero=[00042], them-dau=[+42]
nhom=[1,234,567]
chuoi=[Java], rut-gon=[lang], viet-hoa=[JAVA]
ti-le=12.5%
doi-cho: items (7)
```

Giải thích:

1. `%d`, `%x`, `%o` cùng nhận giá trị `26` nhưng thể hiện lần lượt theo hệ 10, 16, 8.
2. `%.2f` giữ hai chữ số sau dấu thập phân; `%.2e` dùng dạng khoa học với hai chữ số sau dấu thập phân của phần định trị.
3. `%6d` tạo vùng rộng ít nhất sáu ký tự cho `42`: bốn khoảng trắng ở **trước**. `%-6d` thêm bốn khoảng trắng ở **sau**.
4. `%05d` thêm số 0 để đạt độ rộng năm; `%+d` hiển thị `+` cho số dương. `%,d` bật phân nhóm hàng nghìn theo `Locale.ROOT`.
5. `%.4s` rút `language` thành `lang`; `%S` in `java` thành `JAVA`.
6. `%.1f%%%n` gồm **ba chỉ thị theo thứ tự**: `%.1f` tiêu thụ `12.5`, `%%` in một dấu `%`, `%n` xuống dòng. Chúng không phải một chỉ thị `%%%`.
7. `%2$s` lấy đối số thứ hai (`"items"`), `%1$d` lấy đối số thứ nhất (`7`). Đây là **chỉ số đối số từ 1**, khác chỉ số mảng Java từ 0.

Việc chỉ định `Locale.ROOT` giúp các ví dụ trên có dấu thập phân `.` và quy tắc phân nhóm nhất quán. Nếu không truyền locale, `printf()` sử dụng locale mặc định của môi trường Java, có thể hiển thị `,` làm dấu thập phân ở một số nơi.

## 5. `%n`, `\n`, `print()` và `println()`

Một điểm quan trọng: **`printf()` không tự xuống dòng** sau khi in. Hãy thêm `%n` vào format string khi muốn kết thúc dòng.

- `%n` là một **conversion của Formatter**, đại diện dấu kết thúc dòng theo nền tảng (`System.lineSeparator()` về mặt ý nghĩa sử dụng).
- `\n` là **ký tự escape trong Java string literal**, biểu thị ký tự LF. Trên Windows, dấu kết thúc dòng của nền tảng thường là CRLF, vì thế `%n` và `\n` không nhất thiết cho cùng các byte.
- `println()` tự thêm dấu kết thúc dòng theo `PrintStream`; `print()` thì không.
- Trong một format string, `%%` và `%n` đều **không tiêu thụ tham số**.

Tạo **`NewlineDemo.java`**:

```java
public class NewlineDemo {
    public static void main(String[] args) {
        System.out.print("A");
        System.out.println("B");
        System.out.printf("C%n");
        System.out.print("D\nE\n");
        System.out.printf("progress=100%%%n");
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out NewlineDemo.java
java -cp out NewlineDemo
```

**Nội dung stdout theo từng dòng, chính xác** (ký tự kết thúc dòng có thể là LF hoặc CRLF tùy nền tảng):

```text
AB
C
D
E
progress=100%
```

`print("A")` chưa xuống dòng, nên `println("B")` ghép `B` ngay sau `A`. Với `System.out.printf("C%n")`, phải có `%n` thì `D` mới bắt đầu ở dòng mới. `"D\nE\n"` chứa hai ký tự LF do escape `\n`. `"progress=100%%%n"` gồm văn bản `100`, chỉ thị `%%` in một dấu `%`, rồi `%n` kết thúc dòng.

**Lỗi thường gặp:** viết `System.out.printf("100% done%n");` sẽ để một dấu `%` trần mở đầu chỉ thị định dạng không hợp lệ. Sửa thành `System.out.printf("100%% done%n");`, hoặc dùng `println("100% done")` nếu không cần Formatter.

## 6. Độ rộng, căn lề và độ chính xác

### Độ rộng tối thiểu

Với chuỗi `"Cat"`:

```java
System.out.printf("[%8s]%n", "Cat");
System.out.printf("[%-8s]%n", "Cat");
```

Output:

```text
[     Cat]
[Cat     ]
```

Trong `%8s`, `Cat` dài ba ký tự nên có năm dấu cách ở trước; `%-8s` có năm dấu cách ở sau. `width` không giới hạn độ dài tối đa: nếu chuỗi dài hơn tám ký tự, Formatter vẫn in toàn bộ (trừ khi dùng precision để giới hạn).

### Độ chính xác của số thực và chuỗi

```java
System.out.printf(java.util.Locale.ROOT, "[%8.2f]%n", 12.5);
System.out.printf(java.util.Locale.ROOT, "[%.3s]%n", "abcdef");
```

Output:

```text
[   12.50]
[abc]
```

`%8.2f` tạo `12.50` dài năm ký tự, nên được thêm ba dấu cách bên trái. `%.3s` giữ tối đa ba ký tự trong `abcdef`.

Với `%f`, precision kiểm soát số chữ số phần thập phân và có thể cần làm tròn. Nếu muốn biểu diễn **tiền tệ chính xác**, nên dùng `BigDecimal` với quy tắc làm tròn nghiệp vụ rõ ràng, không mặc định rằng `double` có thể biểu diễn chính xác mọi số thập phân.

### Các cờ định dạng thông dụng

| Cờ | Ví dụ | Kết quả với `Locale.ROOT` | Ghi chú |
| --- | --- | --- | --- |
| `-` | `%-6d`, số `42` | `42    ` | Căn trái trong vùng rộng sáu |
| `+` | `%+d`, số `42` | `+42` | Luôn thể hiện dấu số |
| `0` | `%05d`, số `42` | `00042` | Đệm bằng chữ số 0, không phải khoảng trắng |
| `,` | `%,d`, số `1234567` | `1,234,567` | Phân nhóm chữ số theo locale |
| `(` | `%(d`, số `-42` | `(42)` | Dùng ngoặc cho giá trị âm |
| `#` | `%#x`, số `26` | `0x1a` | Dạng thay thế, ý nghĩa tùy conversion |

Không thể dùng mọi cờ với mọi conversion. Ví dụ cờ `0` dùng được với nhiều định dạng số, nhưng không hợp lệ với `%s` thông thường.

## 7. `Locale` quyết định cách hiện số

Khi dùng `System.out.printf("%.2f%n", 1234.5)`, kết quả phụ thuộc locale mặc định. Để chỉ định rõ cách hiển thị:

```java
import java.util.Locale;

public class LocaleDemo {
    public static void main(String[] args) {
        System.out.printf(Locale.US, "US=[%,.2f]%n", 1234.5);
        System.out.printf(Locale.GERMANY, "DE=[%,.2f]%n", 1234.5);
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out LocaleDemo.java
java -cp out LocaleDemo
```

Stdout:

```text
US=[1,234.50]
DE=[1.234,50]
```

Trong `Locale.US`, dấu phẩy dùng để phân nhóm, dấu chấm phân tách phần thập phân. Trong `Locale.GERMANY`, hai dấu này đổi vai trò. Khi format để **người dùng đọc**, chọn locale theo người dùng; khi cần output học tập hoặc máy xử lý có tính ổn định, chọn locale thích hợp một cách tường minh.

`Locale.ROOT` được dùng trong ví dụ chính để tránh phụ thuộc thiết lập hệ thống; nó không có nghĩa mọi quốc gia đều dùng cùng quy ước.

## 8. Dùng `String.format()` khi cần giữ kết quả

`System.out.printf(...)` in ngay ra stdout. Nếu cần lắp chuỗi trước, dùng `String.format(Locale, format, args...)`:

```java
import java.util.Locale;

public class FormatStringDemo {
    public static void main(String[] args) {
        String label = String.format(Locale.ROOT, "ID=%03d; value=%.1f%%", 7, 12.5);
        System.out.println(label);
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out FormatStringDemo.java
java -cp out FormatStringDemo
```

Output:

```text
ID=007; value=12.5%
```

`%03d` in `7` với độ rộng ba và hai số 0 ở trước. `%.1f%%` in `12.5%` trong `Locale.ROOT`. Dấu `%` trong chuỗi format vẫn phải được escape thành `%%` dù ta dùng `String.format` thay vì `printf`.

## 9. Lỗi thường gặp và cách khắc phục

Các lỗi định dạng chủ yếu là **ngoại lệ khi chạy** thuộc nhóm `java.util.IllegalFormatException`, không nhất thiết bị `javac` phát hiện:

| Sai lầm | Hậu quả có thể gặp | Cách sửa |
| --- | --- | --- |
| `printf("%d", "42")` | `IllegalFormatConversionException` vì `String` không phù hợp `%d` | Parse thành số nguyên hoặc dùng `%s` |
| `printf("%f", 42)` | Kiểu `Integer` không phù hợp `%f` | Dùng `42.0`, `(double) 42` hoặc `%d` |
| `printf("%s %d", "An")` | `MissingFormatArgumentException` do thiếu đối số cho `%d` | Cung cấp đủ đối số |
| `printf("%.2d", 7)` | `IllegalFormatPrecisionException` | Với số nguyên, dùng width hoặc cờ `0`, như `%03d` |
| `printf("rate=10%")` | Dấu `%` đơn lẻ tạo format sai | `printf("rate=10%%")` |
| `printf("%05s", "A")` | Cờ `0` không phù hợp `%s` | Dùng `%5s` hoặc `%-5s` |
| Thiếu `%n` cuối định dạng | Các lần in nối cùng một dòng | Thêm `%n` hoặc dùng `println` |
| Output khác dấu thập phân mong đợi | Locale mặc định khác | Truyền `Locale.ROOT`, `Locale.US` hoặc locale đích |

Không phải mọi chuỗi bắt đầu bằng `%` đều cần đối số. `%%` in dấu `%` và `%n` xuống dòng **không chiếm vị trí đối số**. Nếu muốn hiển thị một định dạng giống `%d` nguyên văn, hãy viết `%%d` trong format string.

Ngoài ra:

- Thứ tự đối số quan trọng: `printf("%s: %d", name, age)` khác `printf("%s: %d", age, name)` và lời gọi sau có thể sai kiểu.
- `%d` in giá trị số nguyên theo quy tắc định dạng, không thêm đơn vị. Với tiền hoặc tỉ lệ phần trăm, hãy ghi đơn vị rõ ràng.
- `%f` mặc định sáu chữ số sau dấu thập phân. Đừng nhầm **precision** với **width**.
- `%s` là lựa chọn đơn giản khi cần thể hiện nhiều loại đối tượng, nhưng biểu diễn phụ thuộc `toString()` hoặc cách định dạng tương ứng; không tự coi nó là dạng có thể parse ngược cho mọi đối tượng.
- Độ rộng và precision không phải công cụ chuẩn hóa bảng Unicode khi có emoji, dấu kết hợp hoặc ký tự có độ rộng hiển thị khác một cột terminal. Với bảng căn cột, nên kiểm tra trên terminal thực tế.

## 10. Bài luyện tập có đáp án

**Bài 1.** In số `5` dưới dạng `005` rồi xuống dòng.

```java
System.out.printf("%03d%n", 5);
```

Kết quả: `005` trên một dòng.

**Bài 2.** In số `7.5` với hai chữ số sau dấu thập phân và một dấu phần trăm.

```java
System.out.printf(java.util.Locale.ROOT, "%.2f%%%n", 7.5);
```

Kết quả: `7.50%` rồi xuống dòng.

**Bài 3.** Căn trái chuỗi `"Hi"` trong vùng sáu ký tự, có ngoặc vuông để nhìn thấy dấu cách.

```java
System.out.printf("[%-6s]%n", "Hi");
```

Kết quả: `[Hi    ]`.

**Bài 4.** In lần lượt `"second"` và `"first"` khi đối số truyền vào theo thứ tự `"first", "second"`.

```java
System.out.printf("%2$s, %1$s%n", "first", "second");
```

Kết quả: `second, first`.

Các đoạn bài luyện tập có thể đặt trong `main` của một lớp Java, hoặc thực thi trong JShell; chúng là các statement riêng lẻ, không phải tệp Java độc lập.

**Ghi nhớ:** Với `printf()`, chọn đúng conversion cho kiểu dữ liệu, dùng `width`/ `precision` khi cần căn cột hoặc kiểm soát chữ số, dùng `%%` để in dấu phần trăm và `%n` để xuống dòng phù hợp hệ thống. Chỉ định `Locale` khi cần đầu ra ổn định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-003">System.err cho thông báo chẩn đoán</a>

<details>
<summary>Xem chi tiết</summary>

# `System.err` cho thông báo chẩn đoán trong Java

## 1. `System.err` là gì?

Java cung cấp sẵn hai luồng xuất thường dùng trên console:

- **`System.out`**: luồng xuất chuẩn (*standard output*, **stdout**). Thường dùng để in **kết quả chính** mà người dùng hoặc chương trình khác cần nhận.
- **`System.err`**: luồng lỗi chuẩn (*standard error*, **stderr**). Thường dùng để in **thông báo lỗi, cảnh báo và thông tin chẩn đoán** về những vấn đề đã xảy ra khi xử lý.

Cả hai đều là đối tượng **`java.io.PrintStream`**, nên có các phương thức quen thuộc như `print(...)`, `println(...)` và `printf(...)`. Ví dụ:

```java
System.out.println("Ket qua: 42");
System.err.println("Canh bao: cau hinh thieu");
```

Nếu chạy trên terminal bình thường, bạn có thể nhìn thấy cả hai dòng trên **cùng một màn hình**. Điều đó không biến chúng thành một luồng: về mặt kỹ thuật, stdout và stderr vẫn được phân biệt. Trình gọi, terminal hoặc công cụ shell có thể chuyển hướng chúng tới hai nơi riêng biệt.

**Quy tắc thực hành:** gửi **dữ liệu kết quả** tới stdout; gửi **thông tin về vấn đề hoặc chẩn đoán** tới stderr. Nhờ đó một chương trình khác đọc stdout không phải lọc những câu cảnh báo chen giữa dữ liệu hợp lệ.

## 2. `System.err` có các phương thức in tương tự `System.out`

```java
System.err.print("WARN: ");
System.err.println("Gia tri khong hop le");
System.err.printf("ERROR: ma=%d%n", 400);
```

- `print()` **không tự thêm** dấu xuống dòng.
- `println()` in nội dung rồi thêm dấu kết thúc dòng.
- `printf()` in theo **mẫu định dạng**; muốn xuống dòng dùng `%n` hoặc ký tự newline thích hợp trong format.
- `%s`, `%d`, `%.2f` và các ký hiệu định dạng khác hoạt động theo API `PrintStream` như khi dùng `System.out.printf`.

Nếu định dạng số phụ thuộc vùng miền, có thể chỉ định `Locale`:

```java
System.err.printf(java.util.Locale.US,
        "WARN: he so=%.2f%n", 12.5);
```

Kết quả được gửi vào **stderr** là `WARN: he so=12.50` rồi xuống dòng. Chỉ định `Locale.US` giúp ví dụ dùng dấu chấm thập phân nhất quán, không phụ thuộc locale mặc định của máy.

### `System.err` có tự ném ngoại lệ hay dừng chương trình không?

**Không.** Chỉ in bằng `System.err.println(...)` không phải hành động `throw`, cũng không tự đổi exit code của tiến trình thành mã thất bại.

```java
System.err.println("Loi du lieu");
System.out.println("Chuong trinh van chay");
```

Nếu không có lệnh hoặc lỗi khác khiến chương trình kết thúc, cả hai dòng vẫn có thể được in. Muốn thông báo lỗi trở thành một tình huống được xử lý theo luồng điều khiển, hãy dùng cơ chế ngoại lệ hoặc kiểm tra điều kiện thích hợp. Nếu là lỗi nghiêm trọng không thể tiếp tục, điểm vào chương trình cần quyết định cách kết thúc và **exit status** phù hợp; không nên cho rằng việc viết stderr tự động báo thất bại cho script gọi chương trình.

## 3. So sánh `System.out` và `System.err`

| Đặc điểm | `System.out` | `System.err` |
| --- | --- | --- |
| Tên luồng | Standard output | Standard error |
| Mục tiêu thường gặp | Kết quả, báo cáo, dữ liệu chính | Lỗi, cảnh báo, chẩn đoán |
| Kiểu Java | `PrintStream` | `PrintStream` |
| Phương thức in | `print`, `println`, `printf` | `print`, `println`, `printf` |
| Chuyển hướng thông dụng trong shell | `>` hoặc `1>` | `2>` |
| Chỉ in vào luồng có khiến chương trình thất bại? | Không | Không |

**Lưu ý:** số hiệu **1** cho stdout và **2** cho stderr là cách shell/hệ điều hành thường biểu diễn các luồng chuẩn. Đây **không phải chỉ số mảng Java** và cũng không phải tên file do `System` tự tạo.

Ví dụ một tiện ích có nhiệm vụ trả về danh sách số hợp lệ sẽ hữu ích hơn nếu in danh sách đó trên stdout và in lỗi đầu vào trên stderr. Một chương trình khác có thể dùng dữ liệu stdout mà không bị lẫn thông báo như `WARN` hoặc `ERROR`.

## 4. Ví dụ Java 17+ hoàn chỉnh: dữ liệu hợp lệ và cảnh báo

Tạo tệp **`StdErrDemo.java`**:

```java
import java.util.Locale;

public class StdErrDemo {
    static int parsePositive(String input) {
        final int value;
        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("not an integer", ex);
        }

        if (value <= 0) {
            throw new IllegalArgumentException("must be positive");
        }
        return value;
    }

    public static void main(String[] args) {
        System.out.println("START");

        String[] inputs = {"12", "abc", "-3"};
        for (String input : inputs) {
            try {
                int value = parsePositive(input);
                System.out.printf(Locale.ROOT,
                        "accepted=%d%n", value);
            } catch (IllegalArgumentException ex) {
                System.err.printf(Locale.ROOT,
                        "WARN input=%s reason=%s%n",
                        input, ex.getMessage());
            }
        }

        System.out.println("END");
    }
}
```

Dùng **JDK 17 trở lên**, mở terminal ở thư mục chứa `StdErrDemo.java`:

```bash
javac -encoding UTF-8 StdErrDemo.java
java StdErrDemo
```

Lệnh `javac` thành công sẽ không in thông báo. Trong lần chạy bình thường, cả stdout và stderr có thể cùng hiển thị trên console; **không nên dựa vào thứ tự trộn giữa hai luồng** để kiểm thử. Để xác minh chính xác, hãy xem kết quả của từng luồng riêng biệt:

**Nội dung stdout:**

```text
START
accepted=12
END
```

**Nội dung stderr:**

```text
WARN input=abc reason=not an integer
WARN input=-3 reason=must be positive
```

Giải thích:

1. Chương trình in `START` vào stdout.
2. `"12"` chuyển được thành `12` và dương, nên in `accepted=12` vào stdout.
3. `"abc"` không chuyển được thành số nguyên, nên phương thức `parsePositive` ném `IllegalArgumentException` với thông điệp `not an integer`; `catch` in cảnh báo vào stderr.
4. `"-3"` là số nguyên nhưng không dương, nên in cảnh báo `must be positive` vào stderr.
5. Các lỗi đã được `catch` xử lý; vòng lặp tiếp tục và `END` được in vào stdout.

Vì lỗi ở đây được xem là các **đầu vào bị bỏ qua**, chương trình không chủ động đặt exit code khác 0. Nếu đặc tả yêu cầu **bất kỳ đầu vào sai nào cũng phải làm toàn bộ công việc thất bại**, cần thiết kế cách báo trạng thái thất bại cho bên gọi, không chỉ in `WARN`.

## 5. Tách stdout và stderr bằng chuyển hướng của shell

Các shell phổ biến cho phép lưu hai luồng ra hai tệp. Sau khi biên dịch chương trình ở mục 4, chạy:

```bash
java StdErrDemo 1> normal.txt 2> diagnostics.txt
```

Cách chuyển hướng này thông dụng trong Windows CMD/PowerShell và shell như bash/zsh. Hai tệp sẽ nhận riêng nội dung tương ứng (chi tiết mã hóa văn bản và cách thể hiện newline trên đĩa có thể phụ thuộc shell/hệ điều hành).

**Tệp `normal.txt`** có nội dung các dòng stdout:

```text
START
accepted=12
END
```

**Tệp `diagnostics.txt`** có nội dung các dòng stderr:

```text
WARN input=abc reason=not an integer
WARN input=-3 reason=must be positive
```

Dấu `>` thường là dạng ngắn của `1>`, nghĩa là chuyển hướng stdout. Dấu `2>` chuyển hướng stderr. Không cần sửa bất kỳ dòng Java nào khi chuyển hướng các luồng bằng shell.

### Chỉ chuyển hướng stdout

```bash
java StdErrDemo > normal.txt
```

Đầu ra chuẩn đi vào `normal.txt`; stderr vẫn có thể hiển thị trực tiếp trên terminal. Đây là lý do quan trọng để viết cảnh báo vào **đúng luồng** thay vì in tất cả bằng `System.out`.

### Chuyển hướng cả hai luồng vào cùng một tệp

Trên những shell hỗ trợ cú pháp chuyển hướng file descriptor tương ứng, có thể dùng:

```bash
java StdErrDemo > combined.txt 2>&1
```

Cú pháp `2>&1` làm stderr đi theo đích của stdout trong cách viết này. **Không coi thứ tự dòng trong file gộp là cam kết về thứ tự thời gian của các thao tác trên hai luồng độc lập**: buffering và môi trường chạy có thể ảnh hưởng trình tự quan sát. Khi cần một chuỗi sự kiện có thứ tự nhất quán, nên thiết kế một cơ chế logging hoặc một đích ghi phù hợp thay vì ghép stdout/stderr tùy tiện.

Trên PowerShell, hành vi biểu diễn/chuyển hướng native stderr có thể có khác biệt theo phiên bản. Lệnh tách riêng `1>` và `2>` là cách thực hành thông dụng để quan sát hai luồng, nhưng việc đối chiếu byte/encoding chính xác của tệp cần xem phiên bản shell đang dùng.

## 6. Khi nào nên ghi thông tin gì vào stderr?

Các thông báo chẩn đoán có ích khi chúng giúp người dùng hoặc người vận hành **hiểu nguyên nhân và lựa chọn bước tiếp theo**.

Những trường hợp thường phù hợp:

- Dữ liệu đầu vào không hợp lệ: thiếu tham số, định dạng số sai, giá trị ngoài khoảng cho phép.
- Tệp cấu hình không tồn tại hoặc không đọc được.
- Một thao tác bị từ chối, thất bại hoặc được bỏ qua có chủ đích.
- Cảnh báo về hành vi dự phòng (*fallback*) mà người dùng nên biết.
- Thông điệp debug ngắn khi đang thử nghiệm chương trình console.

Ví dụ:

```java
String filename = "";
if (filename.isBlank()) {
    System.err.println("ERROR: file path is required");
}
```

Đoạn này chỉ **báo lỗi**; muốn thật sự ngừng thao tác hoặc yêu cầu nhập lại, cần thêm logic điều khiển thích hợp.

Thông báo nên rõ **điều gì xảy ra**, **đầu vào nào liên quan** (nếu an toàn) và **người dùng có thể làm gì**. Tránh in thông tin bí mật như mật khẩu, access token hoặc toàn bộ dữ liệu cá nhân vào stderr; log lỗi vẫn có thể bị lưu, thu thập hoặc chia sẻ.

## 7. Ngoại lệ, `printStackTrace` và `System.err`

Một ngoại lệ (*exception*) có thể được xử lý bằng `try-catch`. Khi muốn chẩn đoán tại sao xảy ra lỗi, có thể in thông điệp ngắn hoặc stack trace.

```java
try {
    int number = Integer.parseInt("invalid");
    System.out.println(number);
} catch (NumberFormatException ex) {
    System.err.println("ERROR: input is not a valid integer");
}
```

Đoạn mã này minh họa đặt trong một phương thức. Khi cần thông tin chi tiết hơn:

```java
try {
    Integer.parseInt("invalid");
} catch (NumberFormatException ex) {
    ex.printStackTrace(System.err);
}
```

`printStackTrace(System.err)` in tên ngoại lệ, thông điệp và các dòng vị trí gọi hàm ra stderr. **`ex.printStackTrace()` không đối số cũng ghi vào luồng lỗi chuẩn** theo API thông thường. Stack trace phụ thuộc JDK, tên phương thức và số dòng mã thực tế, nên **không nên mô tả nó bằng một stdout cố định**.

Việc in stack trace là thao tác **xuất thông tin**; nó không làm ngoại lệ tự được xử lý đúng nghiệp vụ. Nếu ứng dụng không thể khôi phục, cần báo lỗi cho tầng gọi hoặc cho chương trình kết thúc theo chính sách lỗi. Không nên lạm dụng `printStackTrace()` trong sản phẩm lớn: dùng công cụ logging thích hợp để có mức độ, nguồn gốc sự kiện và cách thu thập thống nhất.

### In ra stderr không tương đương với ném ngoại lệ

```java
System.err.println("ERROR: invalid input");
```

Dòng này chỉ in nội dung chẩn đoán. Ngược lại:

```java
throw new IllegalArgumentException("invalid input");
```

Dòng thứ hai **thay đổi luồng điều khiển** bằng cách ném ngoại lệ. Nếu ngoại lệ không được xử lý, Java runtime thường báo stack trace và chương trình có thể kết thúc không thành công. Hai thao tác phục vụ hai mục đích khác nhau; chúng có thể đi cùng nhau khi thiết kế xử lý lỗi, nhưng không thay thế nhau.

## 8. Exit code và thông báo lỗi là hai tín hiệu riêng

Khi một tiện ích dòng lệnh hoàn thành, script gọi nó thường xem **exit code** để quyết định công việc thành công hay thất bại.

- Mã **0** thường biểu thị thành công.
- Mã **khác 0** thường biểu thị thất bại theo quy ước của ứng dụng.
- `System.err.println(...)` **không tự làm exit code khác 0**.
- `System.exit(1)` có thể chủ động kết thúc JVM với mã 1, nhưng không nên dùng bừa bãi trong mã thư viện hoặc giữa quy trình cần dọn dẹp tài nguyên.

Ví dụ chương trình tối giản dành cho tiện ích CLI, đặt trong phương thức `main`:

```java
if (args.length == 0) {
    System.err.println("Usage: java Tool <name>");
    System.exit(2);
}
```

Đây là đoạn minh họa, **không phải chương trình độc lập**. Nó báo cách dùng trên stderr và chỉ rõ mã thoát khi thiếu đối số. Trong một ứng dụng thực tế, cũng có thể tổ chức hàm `run` trả về mã lỗi cho tầng `main` để tách logic nghiệp vụ khỏi việc kết thúc JVM.

Lưu ý: khi một ngoại lệ không được bắt làm JVM báo lỗi, stack trace mặc định thường xuất hiện trên stderr. Nhưng không nên chỉ dựa vào việc có/không có chữ trên stderr để suy ra exit code: một cảnh báo hợp lệ có thể được in khi chương trình vẫn thành công.

## 9. `System.err` và công cụ logging

`System.err` phù hợp cho **bài học nhập xuất, chương trình console nhỏ hoặc lỗi khởi động đơn giản**. Với ứng dụng lớn cần theo dõi lâu dài, một giải pháp logging có cấu trúc thường thích hợp hơn:

- Phân mức: DEBUG, INFO, WARN, ERROR.
- Ghi thời điểm và thành phần phát sinh sự kiện.
- Kiểm soát đầu ra, luân chuyển tệp và bộ lọc.
- Tránh trùng thông báo hoặc in đi in lại stack trace ở nhiều tầng.
- Giảm nguy cơ rò rỉ dữ liệu bí mật trong chẩn đoán.

Java có API chuẩn `java.util.logging`; nhiều dự án còn sử dụng các facade/framework logging phù hợp. **Logging không thay thế việc xử lý ngoại lệ** và cũng không làm dữ liệu sai thành hợp lệ. `System.err` là luồng thấp hơn; không tự cung cấp mức log, timestamp hoặc lưu trữ có cấu trúc.

## 10. Các lỗi thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Gọi `System.err.println` là chương trình thất bại.” | Chỉ ghi vào stderr; kết quả thực thi/exit code là chuyện khác. |
| “`System.err` không có `printf`.” | Có, vì cũng là `PrintStream`. |
| “Khi nhìn thấy hai loại dòng trên console, chúng là cùng một luồng.” | Console có thể hiển thị cả stdout và stderr nhưng hai luồng vẫn riêng. |
| “Cứ dùng `System.out` cho mọi thứ cũng được.” | Dữ liệu kết quả sẽ bị trộn cảnh báo; bất tiện khi script chuyển hướng stdout. |
| “Dòng in ở stdout luôn xuất hiện trước dòng in ở stderr nếu gọi trước.” | Không nên phụ thuộc thứ tự quan sát của hai luồng độc lập khi gộp/hiển thị. |
| “`printStackTrace()` in một chuỗi cố định cho mọi Java.” | Thông tin phụ thuộc ngữ cảnh chạy, phiên bản JDK và vị trí mã. |
| “Thông báo stderr có thể tùy tiện chứa thông tin nhạy cảm.” | Nên hạn chế dữ liệu chẩn đoán; stderr có thể được thu thập và lưu lại. |
| “Dùng stderr thay `throw` hoặc `catch`.” | Xuất thông tin và điều khiển lỗi là hai nhiệm vụ khác nhau. |

**Ghi nhớ:** **`System.out` dành cho kết quả chính, `System.err` dành cho thông báo chẩn đoán.** Cả hai đều có `print`, `println` và `printf`, nhưng có thể chuyển hướng riêng. In ra stderr giúp làm rõ lỗi và giữ stdout sạch, còn việc phục hồi, ném ngoại lệ hay đặt exit code vẫn cần được thiết kế riêng theo yêu cầu chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-004">Đọc đối số chương trình từ String&#91;&#93; args</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc đối số chương trình từ `String[] args` — Java 17+

## 1. `String[] args` là gì?

Một ứng dụng Java có thể nhận dữ liệu ngay khi được gọi từ dòng lệnh (*command line*). Điểm vào quen thuộc là:

~~~java
public static void main(String[] args) {
    // Đọc và xử lý các đối số mà người chạy chương trình truyền vào.
}
~~~

- `main` là phương thức điểm vào của chương trình Java theo cách chạy truyền thống.
- `String[] args` là **tham số hình thức** có kiểu **mảng chuỗi**. Mỗi phần tử `args[i]` chứa một **đối số ứng dụng** mà người dùng truyền sau tên lớp chính.
- `args.length` cho biết số lượng phần tử thực sự được truyền.
- Chỉ số bắt đầu từ `0` và kết thúc ở `args.length - 1`.
- Dữ liệu trong `args` ban đầu là **String**, kể cả khi người dùng nhập `123` hoặc `true`. Chương trình phải tự chuyển sang kiểu khác nếu cần.

Ví dụ:

~~~bash
java -cp . CommandLineArgsDemo Lan 3 --upper
~~~

Ở đây, mảng trong `main` có:

~~~text
args.length = 3
args[0]     = "Lan"
args[1]     = "3"
args[2]     = "--upper"
~~~

Các dấu ngoặc kép ở khối mô tả trên chỉ minh họa chuỗi, **không phải ký tự Java tự thêm vào giá trị**. Trong ví dụ hoàn chỉnh ở mục 9, chương trình nhận tên người dùng, số lần chào và một tùy chọn viết hoa.

## 2. Phân biệt tùy chọn của `java` với đối số của ứng dụng

Xét lệnh:

~~~bash
java -cp . CommandLineArgsDemo "Lan Anh" 2
~~~

| Phần trong lệnh | Vai trò |
|---|---|
| `java` | Trình khởi chạy Java |
| `-cp .` | Tùy chọn của **launcher/JVM**: đặt classpath root là thư mục hiện tại |
| `CommandLineArgsDemo` | Tên lớp chứa `main` |
| `"Lan Anh"` | **Đối số ứng dụng thứ nhất** (index 0); dấu nháy của shell nhóm hai từ |
| `2` | **Đối số ứng dụng thứ hai** (index 1), trong Java là chuỗi `"2"` |

**Tên lớp chính không nằm trong `args`.** Tương tự, tùy chọn `-cp` đứng trước tên lớp không phải một phần tử của `args`. Lệnh minh họa trên tạo `args.length == 2`.

Các tham số **sau tên lớp chính** được chuyển cho ứng dụng. Nếu gõ `--upper` sau tên lớp, Java không tự hiểu đây là cờ nghiệp vụ: chính **mã ứng dụng** phải kiểm tra `args[i]` và quyết định nó có ý nghĩa gì.

Các tùy chọn JVM như `-ea` hoặc `-Dkey=value` thường phải được đặt **trước tên lớp chính** khi muốn JVM xử lý. Nếu đặt sau tên lớp, chúng có thể trở thành chuỗi trong `args` của chương trình, tùy cách khởi chạy.

## 3. Duyệt mảng và kiểm tra độ dài trước khi đọc

Cách duyệt giữ được **thứ tự và chỉ số**:

~~~java
for (int i = 0; i < args.length; i++) {
    System.out.println("args[" + i + "]=" + args[i]);
}
~~~

Cách duyệt chỉ cần giá trị:

~~~java
for (String argument : args) {
    System.out.println(argument);
}
~~~

Khi muốn đọc một vị trí bắt buộc, phải đảm bảo vị trí ấy tồn tại:

~~~java
if (args.length == 0) {
    System.out.println("No arguments");
    return;
}
String first = args[0];
System.out.println(first);
~~~

Nếu người dùng chạy mà **không truyền đối số nào**, `args.length == 0` và `args[0]` sẽ ném `ArrayIndexOutOfBoundsException` nếu bị truy cập. Đây là lỗi phải phòng tránh bằng kiểm tra điều kiện, không phải dấu hiệu Java tự cung cấp một chuỗi rỗng thay thế.

Với Java launcher thông thường, `main` được gọi với một mảng `String[]` chứa các đối số ứng dụng (có thể rỗng). Nếu code khác gọi trực tiếp `main(null)` thì đó là một tình huống riêng do mã gọi tạo ra; không phải hành vi chạy CLI tiêu chuẩn cần dựa vào.

## 4. Mỗi phần tử ban đầu đều là chuỗi

Giả sử:

~~~bash
java -cp . CommandLineArgsDemo Lan 3
~~~

`args[1]` có kiểu `String` và giá trị `"3"`, không phải số nguyên `3`. Không viết phép nhân số học với `args[1]` khi chưa chuyển đổi.

~~~java
int times;
try {
    times = Integer.parseInt(args[1]);
} catch (NumberFormatException ex) {
    System.out.println("Invalid integer");
    return;
}
~~~

- `Integer.parseInt("3")` trả `3`, một `int`.
- `Integer.parseInt("abc")` ném `NumberFormatException`.
- `Integer.parseInt("3.5")` cũng không chuyển được thành `int` theo cú pháp số nguyên thập phân.
- `Integer.parseInt(" 3 ")` không tự bỏ khoảng trắng hai bên; cần quy tắc xử lý rõ ràng, ví dụ `args[1].strip()` trước khi parse nếu hợp đồng cho phép.
- Nếu văn bản biểu diễn số nằm ngoài phạm vi `int`, `parseInt` cũng có thể ném `NumberFormatException`.

Chuyển đổi thành công **chưa đủ** để xác nhận đầu vào hợp lệ. Nếu tham số `times` của ứng dụng chỉ được phép trong `1..5`, còn phải kiểm tra:

~~~java
if (times < 1 || times > 5) {
    System.out.println("times must be 1..5");
    return;
}
~~~

Việc validate miền giá trị của dữ liệu đầu vào **luôn phải chạy**, không thay bằng câu lệnh `assert` có thể bị vô hiệu khi khởi động JVM.

## 5. Đối số bắt buộc, đối số tùy chọn và thứ tự

Một chương trình CLI nên công bố **hợp đồng đầu vào** rõ ràng. Ví dụ bài này quy ước:

~~~text
java -cp . CommandLineArgsDemo <name> <times> [--upper]
~~~

| Vị trí | Bắt buộc? | Ý nghĩa | Ví dụ |
|---|---|---|---|
| `args[0]` | Có | Tên người được chào, không được toàn khoảng trắng | `Lan`, `"Lan Anh"` |
| `args[1]` | Có | Số lần chào, số nguyên thập phân từ 1 đến 5 | `3` |
| `args[2]` | Không | Cờ duy nhất được hỗ trợ: `--upper` | `--upper` |

Vì có hai đối số bắt buộc và một đối số tùy chọn, số lượng hợp lệ là **2 hoặc 3**. Cách kiểm tra:

~~~java
if (args.length < 2 || args.length > 3) {
    // Thông báo cách dùng và từ chối yêu cầu.
}
~~~

Vị trí có ý nghĩa: `CommandLineArgsDemo 3 Lan` **không tự được hoán đổi** thành tên Lan và ba lần chào. Chương trình có thể gặp lỗi chuyển `Lan` thành `int` vì vị trí thứ hai phải là số.

`[--upper]` trong phần hướng dẫn sử dụng (*usage*) là **ký hiệu tài liệu** rằng tùy chọn có thể bỏ qua; người dùng **không gõ dấu ngoặc vuông** trừ khi ứng dụng quy định khác.

Tùy chọn `--upper` do chương trình tự định nghĩa. Java launcher không tự tạo cơ chế phân tích `--name`, `--times` hoặc thứ tự cờ linh hoạt. Muốn hỗ trợ các dạng phức tạp hơn, phải tự viết parser hoặc sử dụng thư viện CLI phù hợp.

## 6. Dấu nháy của shell, khoảng trắng và Unicode

Khi muốn chuyển một tên có khoảng trắng thành **một đối số**, cần dùng cú pháp trích dẫn phù hợp với shell:

**Bash (Linux/macOS):**

~~~bash
java -cp . CommandLineArgsDemo "Lan Anh" 2
~~~

**PowerShell (Windows):**

~~~powershell
java -cp . CommandLineArgsDemo "Lan Anh" 2
~~~

Trong cả hai lệnh trên, `args[0]` là chuỗi `Lan Anh` có một dấu cách ở giữa. Dấu `"` giúp shell nhóm chữ, bình thường không trở thành ký tự nằm trong giá trị của `args[0]`.

Nếu bỏ dấu nháy:

~~~bash
java -cp . CommandLineArgsDemo Lan Anh 2
~~~

thì ứng dụng nhận **ba đối số** `"Lan"`, `"Anh"`, `"2"`. Theo hợp đồng của bài, chương trình sẽ cố parse `"Anh"` như số lần chào và từ chối.

Các ký tự đặc biệt như dấu cách, `*`, `$`, dấu nháy và dấu gạch chéo ngược có quy tắc shell khác nhau. Không dựa vào một chuỗi escape của Bash để kết luận nó hoạt động y hệt PowerShell. Với ví dụ phổ thông, dấu nháy kép cho tên có khoảng trắng là đủ.

Java hỗ trợ chuỗi Unicode trong `args`. Khi nhập tiếng Việt, console và cấu hình mã hóa font/terminal phải hỗ trợ Unicode để hiện chữ chính xác. Chương trình hoàn chỉnh dùng dữ liệu ASCII cho những khối stdout được yêu cầu khớp tuyệt đối.

## 7. Chuẩn hóa, xác thực và từ chối đầu vào không hợp lệ

Cần phân biệt **đọc được đối số** với **đầu vào hợp lệ**:

~~~java
String name = args[0].strip();

if (name.isEmpty()) {
    System.out.println("name must not be blank");
    return;
}
~~~

`String.strip()` (có từ Java 11) loại bỏ một số khoảng trắng Unicode theo `Character.isWhitespace`. Nếu tên chỉ chứa các khoảng trắng mà `strip` nhận diện, chuỗi còn lại rỗng và bị từ chối.

Đối với `times`, cần xác thực hai tầng: (1) chuyển được sang `int`; (2) giá trị thuộc miền được phép. Đối với cờ:

~~~java
boolean upper = false;
if (args.length == 3) {
    if (!"--upper".equals(args[2])) {
        System.out.println("unknown option");
        return;
    }
    upper = true;
}
~~~

So sánh chuỗi bằng `String.equals` thay vì `==`: `==` kiểm tra hai tham chiếu có trỏ cùng một đối tượng hay không, không phải so nội dung chuỗi.

**Không ghi mật khẩu/token trực tiếp trong đối số CLI** nếu môi trường có thể hiển thị danh sách tiến trình, lịch sử shell hoặc log lệnh. Trong ứng dụng thật, không tự động echo toàn bộ `args` khi chúng có thể mang thông tin nhạy cảm. Bài demo chỉ in những đối số không nhạy cảm được cung cấp để học cú pháp.

## 8. Truyền kết quả về hệ điều hành: mã thoát

Ứng dụng dòng lệnh thường cần phân biệt thành công với cú pháp/đầu vào sai. Một quy ước phổ biến:

- **Exit status 0**: xử lý thành công.
- **Exit status khác 0**, ví dụ 2: đầu vào/lời gọi không hợp lệ theo hợp đồng của chương trình.

~~~java
public static void main(String[] args) {
    int status = run(args);
    if (status != 0) {
        System.exit(status);
    }
}
~~~

`System.exit(2)` kết thúc JVM với mã 2; đây là lời gọi ở ranh giới ứng dụng, **không nên đưa vào một method tiện ích thư viện** khiến caller không có cơ hội xử lý.

Chương trình demo bên dưới cố ý in thông báo cách dùng lên **stdout** để có thể đối chiếu stdout chính xác ở các tình huống học tập. Trong CLI thực tế, lỗi/usage thường được in trên **stderr** bằng `System.err`, còn output dữ liệu thành công in trên `System.out`; khi áp dụng cần định nghĩa hợp đồng rõ ràng. Cũng lưu ý: dấu nhắc lệnh và exit code không phải một phần nội dung stdout.

## 9. Chương trình Java 17+ hoàn chỉnh

Tạo file đúng tên **`CommandLineArgsDemo.java`**:

~~~java
import java.util.Locale;

public final class CommandLineArgsDemo {
    private CommandLineArgsDemo() {}

    static int run(String[] args) {
        // Minh họa vị trí và nội dung từng phần tử đầu vào.
        System.out.println("args.length=" + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("args[" + i + "]=" + args[i]);
        }

        // Hai đối số bắt buộc, một tùy chọn.
        if (args.length < 2 || args.length > 3) {
            System.out.println("error: expected 2 or 3 arguments");
            printUsage();
            return 2;
        }

        String name = args[0].strip();
        if (name.isEmpty()) {
            System.out.println("error: name must not be blank");
            return 2;
        }

        int times;
        try {
            times = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            System.out.println("error: times must be an integer");
            return 2;
        }

        if (times < 1 || times > 5) {
            System.out.println("error: times must be 1..5");
            return 2;
        }

        boolean upper = false;
        if (args.length == 3) {
            if (!"--upper".equals(args[2])) {
                System.out.println("error: unknown option " + args[2]);
                return 2;
            }
            upper = true;
        }

        System.out.println("name=" + name);
        System.out.println("times=" + times);
        System.out.println("upper=" + upper);

        String message = "Hello, " + name + "!";
        if (upper) {
            message = message.toUpperCase(Locale.ROOT);
        }
        for (int i = 0; i < times; i++) {
            System.out.println(message);
        }
        return 0;
    }

    private static void printUsage() {
        System.out.println(
                "usage: java -cp . CommandLineArgsDemo <name> <times> [--upper]");
    }

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, không cần Maven, Gradle, thư viện ngoài hoặc preview features. Lưu bằng UTF-8. `run` làm phần xử lý đầu vào, còn `main` là điểm vào và trả mã thoát khi lỗi. Chương trình **không tự đoán** tên và số lần chào nếu thiếu đối số.

### Biên dịch

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 CommandLineArgsDemo.java
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\CommandLineArgsDemo.java
~~~

Biên dịch tạo `CommandLineArgsDemo.class` trong thư mục hiện tại. Dùng `-cp .` khi chạy từ thư mục chứa file `.class`.

### Chạy hợp lệ — tên có dấu cách

~~~bash
java -cp . CommandLineArgsDemo "Lan Anh" 2
~~~

Cùng cú pháp trên sử dụng được trong PowerShell với các đối số minh họa này.

**Exact expected stdout:**

~~~text
args.length=2
args[0]=Lan Anh
args[1]=2
name=Lan Anh
times=2
upper=false
Hello, Lan Anh!
Hello, Lan Anh!
~~~

### Chạy hợp lệ — dùng cờ `--upper`

~~~bash
java -cp . CommandLineArgsDemo Lan 3 --upper
~~~

**Exact expected stdout:**

~~~text
args.length=3
args[0]=Lan
args[1]=3
args[2]=--upper
name=Lan
times=3
upper=true
HELLO, LAN!
HELLO, LAN!
HELLO, LAN!
~~~

`Locale.ROOT` giúp chuyển chữ hoa ổn định cho thông điệp ví dụ, tránh những khác biệt chuyển đổi chữ do locale mặc định của máy. Cả hai lệnh hợp lệ trả **exit status 0**.

### Chạy thiếu đối số

~~~bash
java -cp . CommandLineArgsDemo
~~~

**Exact expected stdout:**

~~~text
args.length=0
error: expected 2 or 3 arguments
usage: java -cp . CommandLineArgsDemo <name> <times> [--upper]
~~~

Vì không đọc `args[0]` khi chưa đủ phần tử, chương trình không gặp `ArrayIndexOutOfBoundsException`. Nó in hướng dẫn và kết thúc với **exit status 2**.

### Chạy với số không hợp lệ

~~~bash
java -cp . CommandLineArgsDemo Lan abc
~~~

**Exact expected stdout:**

~~~text
args.length=2
args[0]=Lan
args[1]=abc
error: times must be an integer
~~~

`Integer.parseInt("abc")` phát sinh `NumberFormatException`. Chương trình bắt ngoại lệ đúng nơi để giải thích điều kiện đầu vào và trả **exit status 2**.

### Chạy với số ngoài phạm vi

~~~bash
java -cp . CommandLineArgsDemo Lan 0
~~~

**Exact expected stdout:**

~~~text
args.length=2
args[0]=Lan
args[1]=0
error: times must be 1..5
~~~

Giá trị `0` parse thành công nhưng không hợp lệ theo quy tắc số lần chào `1..5`. Vì thế vẫn trả **exit status 2**. Đây là ví dụ rõ ràng cho việc **chuyển kiểu đúng chưa đồng nghĩa dữ liệu hợp lệ**.

## 10. Đọc nhiều đối số và lựa chọn cách thiết kế CLI

Nếu một ứng dụng nhận **danh sách nhiều tên** không cố định số lượng, có thể dùng một vòng lặp:

~~~java
for (String name : args) {
    System.out.println("Hello, " + name + "!");
}
~~~

Đây là mã minh họa trong một `main` phù hợp, khác hợp đồng của `CommandLineArgsDemo` trong mục 9. Với danh sách biến độ dài, vòng lặp enhanced `for` phù hợp khi chỉ cần giá trị; vòng `for` có chỉ số phù hợp khi cần biết vị trí.

Trong dự án thật, nên xác định rõ phong cách đầu vào:

| Dạng CLI | Ví dụ | Điểm cần tự xử lý |
|---|---|---|
| Positional arguments | `tool input.txt 3` | Số lượng, vị trí, chuyển kiểu |
| Optional flag | `tool input.txt --verbose` | Cờ được hỗ trợ, giá trị mặc định |
| Key-value option | `tool --count=3` | Phân tích khóa, giá trị và trùng khóa |
| Nhiều mục | `tool Alice Bob Carol` | Số lượng không cố định, có cần trích dẫn tên |
| Input tương tác | `Scanner(System.in)` | Đọc từ stdin trong lúc chạy, **không phải `args`** |

`String[] args` phục vụ dữ liệu **được truyền khi khởi chạy**, trong khi `System.in`/`Scanner` phục vụ dữ liệu **được đọc trong lúc chương trình chạy**. Chúng là hai nguồn input khác nhau, có thể kết hợp nhưng không tự thay thế cho nhau.

Nếu một CLI phức tạp nhiều subcommand, cờ có giá trị, trợ giúp hoặc định dạng chuẩn, thư viện parse dòng lệnh có thể hữu ích; trước hết cần hiểu chính xác `args` là một mảng chuỗi.

## 11. Các lỗi phổ biến

| Sai lầm | Nguyên nhân | Cách xử lý |
|---|---|---|
| Đọc `args[0]` ngay, không kiểm tra `length` | Không có đối số gây `ArrayIndexOutOfBoundsException` | Kiểm tra đủ số phần tử |
| Cho rằng `args[1]` đã là `int` | Phần tử luôn có kiểu `String` | Gọi `Integer.parseInt` và bắt lỗi |
| Parse được số liền xem là hợp lệ | Giá trị có thể âm, 0 hoặc vượt giới hạn | Kiểm tra thêm miền giá trị |
| Nhầm tên class hoặc `-cp` là một đối số | Chúng là phần của lệnh launcher | Đối số ứng dụng bắt đầu **sau tên lớp** |
| Gõ tên nhiều từ không có trích dẫn | Shell tách thành nhiều phần tử | Dùng dấu nháy theo cú pháp shell |
| Dùng `args[0] == "--upper"` | `==` so tham chiếu | `"--upper".equals(args[0])` |
| Nghĩ Java tự hiểu mọi cờ `--flag` | `args` chỉ là mảng các chuỗi | Viết parser cho các cờ được hỗ trợ |
| Lặng lẽ chấp nhận cờ không biết | Lỗi đánh máy của người dùng bị bỏ qua | Từ chối và thông báo |
| Đặt `-ea` sau tên lớp rồi mong JVM bật assertions | Sau tên lớp thường được truyền cho `args` | Đặt tùy chọn JVM trước tên lớp |
| Đưa mật khẩu vào tham số CLI và echo toàn bộ args | Có thể lộ trong lịch sử shell/log/process list | Tránh truyền/ghi thông tin nhạy cảm như vậy |
| Dùng `assert` để kiểm tra input bắt buộc | Assertion có thể bị tắt | Dùng `if`, ngoại lệ, thông báo lỗi luôn chạy |
| In lỗi không cho người dùng biết cú pháp cần nhập | Khó sửa lời gọi | Ghi thông điệp cụ thể và usage thích hợp |

## 12. Bài tập có đáp án

**A — Xác định mảng.** Với lệnh:

~~~bash
java -cp . CommandLineArgsDemo "Mai Anh" 2 --upper
~~~

`args.length` bằng bao nhiêu, `args[0]` là gì?

**Đáp án:** `args.length == 3`; `args[0]` là `"Mai Anh"`, `args[1]` là `"2"`, `args[2]` là `"--upper"`.

**B — Đối số thứ nhất.** Với `java -cp . CommandLineArgsDemo`, thao tác nào gây lỗi nếu thực hiện ngay?

**Đáp án:** đọc `args[0]`, vì mảng không có phần tử. Phải kiểm tra `args.length` trước.

**C — Chuyển kiểu.** Vì sao `Integer.parseInt(args[1])` có thể thất bại?

**Đáp án:** vì người dùng có thể truyền chuỗi không đại diện cho một `int` hợp lệ, ví dụ `"abc"` hoặc giá trị vượt miền `int`. Phải bắt `NumberFormatException` hoặc xử lý theo hợp đồng.

**D — Đối số sai thứ tự.** Lệnh `java -cp . CommandLineArgsDemo 3 Lan` có hợp lệ theo giao diện bài không?

**Đáp án:** **Không**. `args[0]` bị hiểu là tên `3`; `args[1]` là `Lan`, không parse thành `int` được.

**E — Hai shell.** Muốn đưa tên `Lan Anh` thành **một phần tử**, phải làm gì?

**Đáp án:** dùng trích dẫn phù hợp như `"Lan Anh"` trong các lệnh Bash/PowerShell minh họa.

**F — Flag tùy chọn.** Với lệnh `java -cp . CommandLineArgsDemo Lan 2 --upper`, ai quyết định ý nghĩa của `--upper`?

**Đáp án:** mã ứng dụng. Java launcher chuyển chuỗi này tới `args[2]`; trong `run`, code kiểm tra `"--upper".equals(args[2])`.

**G — Phân biệt dòng lệnh và stdin.** `Scanner(System.in)` có tự đọc lại phần tử `args[0]` không?

**Đáp án:** **Không**. `System.in` là luồng input trong khi chạy; `args` là mảng các đối số đã được truyền khi khởi động.

## 13. Checklist và kết luận

- [ ] Biết `public static void main(String[] args)` nhận mảng chuỗi đối số ứng dụng.
- [ ] Đọc đúng `args.length` và phần tử `args[i]`.
- [ ] Không nhầm `-cp`, tên lớp chính với nội dung `args`.
- [ ] Kiểm tra số lượng đối số trước khi đọc theo chỉ số.
- [ ] Biết mọi `args[i]` ban đầu đều là `String`.
- [ ] Biết chuyển `String` sang `int` và bắt `NumberFormatException`.
- [ ] Xác thực miền giá trị sau khi parse.
- [ ] Hiểu cách dấu nháy của shell nhóm đối số có khoảng trắng.
- [ ] Phân biệt đối số bắt buộc và cờ tùy chọn do ứng dụng tự định nghĩa.
- [ ] Dùng `String.equals` để so cờ, không dùng `==`.
- [ ] Biết xử lý lỗi và mã thoát không bằng 0 khi lời gọi không hợp lệ.
- [ ] Tránh in bí mật từ command line trong log/console.
- [ ] Phân biệt đối số CLI với input đọc lúc chạy từ `System.in`.
- [ ] Biên dịch `CommandLineArgsDemo.java` với JDK 17+, chạy đủ trường hợp tốt/xấu và đối chiếu exact stdout.

**Kết luận:** `String[] args` là **mảng chuỗi đối số do người chạy cung cấp cho ứng dụng Java**. Đọc đúng phải bắt đầu từ `args.length`, hiểu **thứ tự và vị trí**, chuyển kiểu khi cần và **validate** dữ liệu trước khi xử lý. Dấu nháy shell, tùy chọn JVM, cờ riêng ứng dụng và input từ `System.in` thuộc các lớp xử lý khác nhau; phân biệt rõ chúng giúp viết CLI Java dễ sử dụng và an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-005">Đọc dữ liệu console bằng Scanner</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc dữ liệu console bằng `Scanner` (Java 17+)

## 1. `Scanner` là gì và dùng khi nào?

`java.util.Scanner` là lớp tiện ích đọc đầu vào dưới dạng **token** hoặc **dòng**, có thể phân tích dữ liệu thành các kiểu như `int`, `long`, `double` và `boolean`. Trong bài nhập xuất console, ta thường gắn một `Scanner` với `System.in`:

```java
import java.util.Scanner;

Scanner scanner = new Scanner(System.in);
String name = scanner.nextLine();
int age = scanner.nextInt();
```

Đây là những câu lệnh minh họa cần đặt trong phương thức hay khối mã thích hợp. `System.in` là luồng đầu vào chuẩn; thường nhận ký tự từ bàn phím khi chương trình chạy trong terminal, nhưng cũng có thể lấy dữ liệu từ một file hoặc pipe. `Scanner` biến luồng này thành các giá trị có cấu trúc.

`Scanner` thích hợp với chương trình console nhỏ, bài tập và công cụ dòng lệnh đơn giản. Đối với khối lượng dữ liệu văn bản rất lớn, có thể xem xét `BufferedReader` để chủ động đọc từng dòng; `Scanner` thuận tiện hơn khi cần tách và parse token.

## 2. Các phương thức quan trọng

| Phương thức | Dữ liệu đọc | Đặc điểm |
| --- | --- | --- |
| `next()` | Token kế tiếp, trả `String` | Mặc định bỏ qua dấu phân cách khoảng trắng; chỉ lấy một token |
| `nextLine()` | Phần còn lại của dòng hiện tại, trả `String` | Có thể gồm khoảng trắng; bỏ dấu kết thúc dòng khỏi giá trị trả về |
| `nextInt()` / `nextLong()` | Token số nguyên | Parse theo radix/locale tương ứng; báo lỗi nếu token không hợp lệ hoặc ngoài miền giá trị |
| `nextDouble()` / `nextFloat()` | Token số thực | Quy tắc dấu thập phân và phân nhóm chịu ảnh hưởng của `Locale` |
| `nextBoolean()` | Token boolean | Nhận `true`/`false` theo cách so khớp không phân biệt hoa thường |
| `hasNext()` / `hasNextLine()` | `boolean` | Kiểm tra còn token/dòng hay không, hữu ích ở cuối input |
| `hasNextInt()` / `hasNextDouble()` | `boolean` | Kiểm tra token tiếp theo có parse được theo kiểu đó không |
| `useLocale(locale)` | Cấu hình Scanner | Quy định cú pháp số theo locale |
| `useDelimiter(regex)` | Cấu hình Scanner | Thay mẫu phân cách token mặc định bằng biểu thức chính quy |

**Token khác dòng:** với đầu vào `Nguyen Van An`, `next()` chỉ lấy `Nguyen`, còn `nextLine()` có thể lấy toàn bộ `Nguyen Van An` (nếu đang ở đầu dòng).

Mặc định, dấu phân cách token của `Scanner` là các ký tự khoảng trắng (space, tab, xuống dòng...). Những phương thức `nextInt()`, `nextDouble()`, `next()` đọc theo **token**; `nextLine()` đọc theo **ranh giới dòng**. Trộn hai cách đọc này cần cẩn thận.

## 3. Ví dụ đầy đủ: đọc tên, tuổi, điểm và ghi chú

Tạo file **`ScannerDemo.java`**:

```java
import java.util.Locale;
import java.util.Scanner;

public class ScannerDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.useLocale(Locale.US);

            System.out.println("Enter full name:");
            String name = scanner.nextLine();

            System.out.println("Enter age:");
            int age = scanner.nextInt();

            System.out.println("Enter score:");
            double score = scanner.nextDouble();

            scanner.nextLine(); // consume the remainder of the score line
            System.out.println("Enter note:");
            String note = scanner.nextLine();

            System.out.printf(Locale.US,
                    "Result: %s | age=%d | score=%.1f | note=%s%n",
                    name, age, score, note);
        }
    }
}
```

**Đầu vào** (gõ mỗi giá trị rồi nhấn Enter, hoặc tạo file `sample.txt` có chính xác bốn dòng này):

```text
Nguyen Van An
20
8.5
Likes Java
```

Biên dịch tại thư mục chứa `ScannerDemo.java` bằng **JDK 17+**:

```sh
javac -encoding UTF-8 --release 17 -d out ScannerDemo.java
java -cp out ScannerDemo
```

Tạo sẵn thư mục `out` bằng `mkdir out` trước khi biên dịch nếu nó chưa tồn tại. Trong lần chạy tương tác, terminal có thể hiển thị lại các ký tự người dùng vừa gõ; các ký tự được terminal echo **không thuộc stdout do Java in**.

Để kiểm tra output ổn định với `sample.txt`:

- **Linux/macOS hoặc Windows CMD:** `java -cp out ScannerDemo < sample.txt`
- **Windows PowerShell 5.1+:** `Get-Content -LiteralPath sample.txt | java -cp out ScannerDemo`

**Stdout chính xác của chương trình** với đầu vào đã nêu (không tính phần input được terminal tự echo):

```text
Enter full name:
Enter age:
Enter score:
Enter note:
Result: Nguyen Van An | age=20 | score=8.5 | note=Likes Java
```

Cần hiểu các bước sau:

1. `nextLine()` lấy toàn bộ tên có dấu cách.
2. `nextInt()` đọc token `20`; `nextDouble()` đọc token `8.5`.
3. `scanner.useLocale(Locale.US)` quy định dấu chấm `.` làm dấu thập phân. Locale mặc định của máy có thể khác, vì vậy `8.5` không phải lúc nào cũng được đọc như mong đợi nếu bỏ cấu hình.
4. `scanner.nextLine()` ngay sau `nextDouble()` chủ động đọc và bỏ phần còn lại của dòng điểm, để `nextLine()` kế tiếp nhận dòng ghi chú.
5. `System.out.printf(Locale.US, ...)` cố định cách định dạng dấu thập phân trong kết quả.

Mã ví dụ giả định tuổi và điểm **đúng định dạng**. Nếu đầu vào sai hoặc bị cắt ngắn, các lệnh `nextInt()`, `nextDouble()`, `nextLine()` có thể ném ngoại lệ; phần 6–7 hướng dẫn xử lý.

## 4. Chú ý khi chuyển từ đọc token sang đọc dòng

`nextInt()` và `nextDouble()` đọc một **token số**; `nextLine()` đọc **phần còn lại của dòng hiện tại**. Với input `21\nHello Java\n`, sau `nextInt()` lần `nextLine()` kế tiếp nhận `""` vì dòng số đã hết. Nếu còn văn bản sau số trên cùng dòng, `nextLine()` sẽ trả phần văn bản đó thay vì chuỗi rỗng.

Khi hợp đồng yêu cầu **số ở một dòng, ghi chú ở dòng kế tiếp**, có thể gọi một `nextLine()` để tiêu thụ phần dư rồi gọi lần nữa để lấy ghi chú. Khi muốn nhập từng trường một dòng và dễ kiểm tra lỗi, dùng `Integer.parseInt(scanner.nextLine().strip())` sau khi kiểm tra còn dòng là lựa chọn khác. Đừng tự bỏ phần dư nếu nó chứa dữ liệu cần giữ.

Ví dụ `ScannerDemo` phía trên áp dụng cách đầu; bài xử lý phần xuống dòng còn lại trình bày tình huống có/không có văn bản cùng dòng, EOF và các phương án xử lý bằng chương trình tái hiện độc lập.

## 5. Đọc token, dòng và dấu phân cách theo nhu cầu

Một token được xác định bởi **delimiter** hiện tại. Ví dụ đọc những từ phân cách bằng khoảng trắng:

```java
import java.util.Scanner;

public class TokenDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("red green\nblue")) {
            while (scanner.hasNext()) {
                System.out.println("token=" + scanner.next());
            }
        }
    }
}
```

```sh
javac -encoding UTF-8 --release 17 -d out TokenDemo.java
java -cp out TokenDemo
```

Output:

```text
token=red
token=green
token=blue
```

Nếu muốn tách theo dấu phẩy, có thể thay delimiter:

```java
scanner.useDelimiter(",");
```

Đây là statement minh họa cần đặt trong khối có biến `scanner` phù hợp. `useDelimiter` nhận **regex**, không phải chuỗi văn bản đơn thuần cho mọi ký hiệu. Ví dụ muốn tách cả dấu phẩy lẫn khoảng trắng, sử dụng `scanner.useDelimiter("[,\\s]+");` trong Java source; Java biến `\\` trong string literal thành `\`, để biểu thức chính quy nhận `\s` là lớp ký tự trắng.

Dùng `nextLine()` khi cần giữ khoảng trắng giữa các từ (họ tên, câu nhập từ người dùng), và dùng `next()`/`nextInt()` khi input thực sự được thiết kế như một chuỗi token.

## 6. Kiểm tra dữ liệu trước khi parse

Nếu `nextInt()` gặp `abc`, `Scanner` thường ném `InputMismatchException`. Nếu không còn token, `nextInt()` có thể ném `NoSuchElementException`. `hasNextInt()` giúp kiểm tra token tiếp theo trước khi đọc:

```java
if (scanner.hasNextInt()) {
    int value = scanner.nextInt();
    System.out.println("value=" + value);
} else {
    System.out.println("Not a valid int token");
}
```

Đoạn trên là minh họa phụ thuộc vào biến `scanner`. Khi `hasNextInt()` trả `false` do token sai, scanner **chưa tự loại token không hợp lệ**; nếu lặp mà không tiêu thụ hay bỏ token đó, vòng lặp có thể không tiến triển. Khi kiểm tra input theo dòng, nên đọc `nextLine()`, parse và phản hồi lại người dùng.

### Ví dụ đầy đủ: đọc dòng, xử lý sai số nguyên và EOF

Tạo **`ValidatedScannerDemo.java`**:

```java
import java.util.Scanner;

public class ValidatedScannerDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Enter an integer from 0 to 100:");

            if (!scanner.hasNextLine()) {
                System.out.println("Error: no input");
                return;
            }

            String text = scanner.nextLine().trim();
            try {
                int number = Integer.parseInt(text);
                if (number < 0 || number > 100) {
                    System.out.println("Error: out of range");
                } else {
                    System.out.println("Accepted: " + number);
                }
            } catch (NumberFormatException ex) {
                System.out.println("Error: not an integer");
            }
        }
    }
}
```

Biên dịch:

```sh
javac -encoding UTF-8 --release 17 -d out ValidatedScannerDemo.java
```

Chạy:

```sh
java -cp out ValidatedScannerDemo
```

Với mỗi lần chạy, người dùng cung cấp **một dòng duy nhất**. Đầu ra tương ứng:

| Input | Stdout từ chương trình |
| --- | --- |
| `42` | `Enter an integer from 0 to 100:` rồi `Accepted: 42` |
| `abc` | Dòng nhắc nhập rồi `Error: not an integer` |
| `150` | Dòng nhắc nhập rồi `Error: out of range` |
| EOF ngay lập tức | Dòng nhắc nhập rồi `Error: no input` |

Với input `42`, **stdout chính xác** là:

```text
Enter an integer from 0 to 100:
Accepted: 42
```

Ví dụ đọc toàn bộ dòng nên xử lý được giá trị có khoảng trắng ở hai đầu thông qua `trim()`, không gặp lỗi newline sau `nextInt()`. Phép parse `Integer.parseInt` ở đây là API của `Integer`, nên lỗi định dạng là `NumberFormatException`, khác với `InputMismatchException` của `Scanner.nextInt()`.

**Khác biệt giữa kiểm tra và xử lý:** `hasNextLine()` giúp tránh gọi `nextLine()` khi đã EOF, nhưng nó không xác minh định dạng số. Ngược lại, `hasNextInt()` xác minh token số nguyên theo `Scanner` nhưng không thay thế kiểm tra miền giá trị nghiệp vụ, ví dụ 0 đến 100.

## 7. Các ngoại lệ và vấn đề cần biết

| Tình huống | Hậu quả có thể gặp | Cách phòng tránh |
| --- | --- | --- |
| `nextInt()` đọc `abc` hoặc số ngoài miền `int` | `InputMismatchException` | `hasNextInt()`, thông báo cho người dùng, bỏ token sai hoặc đọc cả dòng để parse |
| `nextLine()` hoặc `next()` lúc không còn dòng/token | `NoSuchElementException` | Dùng `hasNextLine()` hoặc `hasNext()` phù hợp trước khi đọc |
| Dùng scanner sau `scanner.close()` | `IllegalStateException` | Không tiếp tục sử dụng đối tượng đã đóng |
| Quên dòng còn dư sau `nextInt()` | `nextLine()` trả chuỗi rỗng hoặc phần dư của dòng cũ | Dùng `nextLine()` để bỏ phần dư khi phù hợp, hoặc chỉ đọc cả dòng rồi parse |
| `nextDouble()` không chấp nhận `8.5` như mong đợi | Locale số đang dùng khác | Đặt `useLocale(Locale.US)` nếu yêu cầu dấu thập phân là `.` |
| Gõ số ngoài khoảng nghiệp vụ | Có thể parse thành công nhưng dữ liệu vẫn sai | Kiểm tra miền giá trị sau khi parse |
| Dùng nhiều `Scanner(System.in)` cùng lúc | Đọc trước/buffer làm các scanner cạnh tranh và gây lỗi khó hiểu | Duy trì một scanner dùng chung cho input console |

Ngoài những lỗi trên, `Scanner` có thể phải chờ khi chương trình đang yêu cầu dữ liệu mà đầu vào chưa cung cấp token/dòng hoàn chỉnh. Với bài console tương tác, hãy nhìn dòng nhắc nhập và xác nhận mình đã nhấn Enter hoặc đã kết thúc input.

## 8. `Locale` và mã hóa của nguồn nhập

`scanner.useLocale(Locale.US)` giúp `nextDouble()` đọc dạng như `8.5` với dấu chấm thập phân. Locale khác có thể dùng dấu phẩy, vì vậy ví dụ cần đầu ra xác định nên chỉ rõ locale. `Scanner.nextLine()` trả chuỗi chưa parse; dùng `Double.parseDouble` với chuỗi dấu chấm là một hợp đồng khác với `Scanner.nextDouble()` theo locale.

Với nguồn byte cần giải mã UTF-8, có thể khởi tạo `new Scanner(System.in, StandardCharsets.UTF_8)` (sau khi import tương ứng). `javac -encoding UTF-8` chỉ điều khiển việc đọc **mã nguồn**, không cấu hình mặc định cho dữ liệu của `System.in`. Bài Locale và sở hữu nguồn nhập phân tích chi tiết từng trường hợp.

## 9. Quyền sở hữu và việc đóng `Scanner`

`Scanner` triển khai `AutoCloseable`; khi đóng, nó cũng đóng nguồn bọc bên dưới nếu nguồn là `Closeable`. Vì vậy `try (Scanner s = new Scanner(System.in)) { ... }` phù hợp với chương trình nhỏ đã kết thúc mọi thao tác đọc khi rời khối. Nhưng `System.in` có thể được chia sẻ, nên không đóng scanner mượn nguồn này giữa chừng nếu phần khác còn cần đọc.

Nguồn do chính mã mở (như file) cần được đóng đúng lúc; scanner từ `String` không làm ảnh hưởng `System.in`. Hãy có một nơi chịu trách nhiệm vòng đời input và tránh tạo nhiều scanner tranh nhau đọc luồng console chung. Bài cuối chương có ví dụ riêng về đóng nguồn và cấu hình locale.

## 10. Thói quen tốt khi viết chương trình console

1. **Xác định input được tổ chức theo token hay theo dòng.** Họ tên/câu mô tả nên dùng `nextLine()`; danh sách số phân cách bởi khoảng trắng có thể phù hợp với `nextInt()`/`nextDouble()`.
2. **Giữ nhất quán cách đọc.** Dùng đọc theo dòng rồi parse khi muốn validation và thông báo lỗi rõ ràng; khi trộn token với dòng, chú ý phần còn lại của dòng.
3. **Kiểm tra EOF trước khi đọc** bằng các `hasNext...()` phù hợp nếu nguồn input có thể hết sớm.
4. **Kiểm tra kiểu và miền giá trị độc lập.** `42` có thể là `int` hợp lệ nhưng không phải tuổi hợp lệ theo hợp đồng ứng dụng.
5. **Đặt `Locale` rõ ràng** cho số thực và thống nhất quy tắc định dạng input/output.
6. **Không dùng nhiều scanner trên cùng `System.in`** nếu không thật sự cần; truyền một scanner qua các phương thức.
7. **Không hiển thị hoặc log dữ liệu bí mật** như mật khẩu nhập từ terminal. `Scanner(System.in)` không tắt tính năng echo của terminal nên không phù hợp để yêu cầu nhập mật khẩu mà cần che ký tự; xem `System.console().readPassword()` trong môi trường có console phù hợp.

## 11. Tự kiểm tra

**1. Với input `Tran Van Binh`, `next()` và `nextLine()` khác nhau thế nào?** `next()` đọc token `Tran`; `nextLine()` đọc phần còn lại của dòng hiện tại, tùy vị trí đọc. Nếu đang ở đầu dòng, nó lấy `Tran Van Binh`.

**2. Vì sao `nextLine()` ngay sau `nextInt()` thường trả chuỗi rỗng?** Vì `nextInt()` chỉ tiêu thụ token số, để lại dấu xuống dòng; `nextLine()` đọc phần còn lại của dòng đó.

**3. Đọc `abc` bằng `nextInt()` có trả về 0 không?** Không. Thường nó ném `InputMismatchException`. Dùng kiểm tra định dạng và xử lý lỗi.

**4. `hasNextInt()` trả `false` có tự bỏ token sai không?** Không. Nếu muốn tiếp tục, cần đọc/bỏ token không hợp lệ để tiến tới input kế tiếp.

**5. `nextDouble()` có luôn hiểu `8.5` như số thực không?** Không; phụ thuộc `Locale`. Có thể dùng `scanner.useLocale(Locale.US)`.

**6. Đóng `Scanner(System.in)` giữa chương trình có vô hại không?** Không. Nó có thể đóng luôn `System.in` khiến các bước nhập sau không hoạt động.

**Ghi nhớ:** `Scanner` giúp đọc và parse dữ liệu console rất tiện, nhưng bạn phải lựa chọn ranh giới **token hay dòng**, quản lý **locale**, xử lý **dữ liệu không hợp lệ/EOF** và điều khiển vòng đời `System.in` có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-006">Phân biệt next(), nextInt() và nextLine()</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `next()`, `nextInt()` và `nextLine()` trong Java

## 1. Ba phương thức của `Scanner` đọc dữ liệu khác nhau

Lớp `java.util.Scanner` giúp đọc và phân tích dữ liệu từ bàn phím (`System.in`), chuỗi hoặc nguồn dữ liệu khác. Ba phương thức phổ biến là:

- **`next()`**: đọc **một token** và trả về `String`.
- **`nextInt()`**: đọc **một token biểu diễn số nguyên hợp lệ** và trả về giá trị `int`.
- **`nextLine()`**: đọc **phần còn lại của dòng hiện tại** và trả về `String`, bỏ ký tự kết thúc dòng ra khỏi kết quả.

Hai phương thức `next()` và `nextInt()` làm việc theo **token**; `nextLine()` làm việc theo **ranh giới dòng**. Sự khác biệt này là nguyên nhân thường gặp của lỗi “`nextLine()` bị bỏ qua” khi dùng chung với `nextInt()`.

```java
import java.util.Scanner;

// Đặt trong một phương thức:
Scanner sc = new Scanner(System.in);
String word = sc.next();
int age = sc.nextInt();
String text = sc.nextLine();
```

Đoạn trên chỉ minh họa cú pháp và **không phải chương trình độc lập**. Kết quả phụ thuộc dữ liệu được nhập và vị trí hiện tại của `Scanner` trong luồng.

## 2. Bảng so sánh nhanh

| Đặc điểm | `next()` | `nextInt()` | `nextLine()` |
| --- | --- | --- | --- |
| Trả về | `String` | `int` | `String` |
| Đơn vị đọc | Token | Token số nguyên | Phần còn lại của dòng |
| Khoảng trắng mặc định | Bỏ qua dấu phân cách trước token; dừng ở dấu phân cách sau token | Tương tự `next()`, đồng thời phân tích token theo quy tắc số nguyên | Giữ khoảng trắng trong nội dung dòng, không đưa ký tự kết thúc dòng vào kết quả |
| Dữ liệu `"Java Core"` | Lần đầu trả `"Java"` | Ném lỗi vì token đầu không phải số nguyên | Trả `"Java Core"` khi bắt đầu từ đầu dòng |
| Đọc số `42` | Trả chuỗi `"42"` | Trả số `42` | Trả chuỗi `"42"` nếu dòng chỉ có `42` |
| Nếu token đầu sai định dạng số | Vẫn có thể đọc token đó thành chuỗi | Có thể ném `InputMismatchException` | Không phân tích số, chỉ đọc nội dung dòng |
| Sau khi đọc | Thường để vị trí tại ranh giới sau token | Thường để vị trí tại ranh giới sau token | Tiêu thụ cả ký tự kết thúc dòng nếu có |

Với thiết lập mặc định, `Scanner` xem một hoặc nhiều ký tự **khoảng trắng theo định nghĩa Java** là dấu phân cách token (*delimiter*): dấu cách, tab, xuống dòng và nhiều ký tự whitespace khác. Có thể cấu hình delimiter khác bằng `useDelimiter(...)`, nên đừng khẳng định `next()` luôn tách theo dấu cách trong mọi chương trình.

**Lưu ý về `nextLine()`:** phương thức bắt đầu từ **vị trí đọc hiện tại**, không nhất thiết là đầu dòng. Nếu trước đó đã dùng `next()` hay `nextInt()`, phần còn lại của dòng có thể chỉ là chuỗi rỗng hoặc các dấu cách chưa tiêu thụ.

## 3. `next()`: đọc một token dạng chuỗi

Khi nhập:

```text
Nguyen Van An
```

và gọi:

```java
String first = sc.next();   // "Nguyen"
String second = sc.next();  // "Van"
String third = sc.next();   // "An"
```

`next()` sẽ bỏ qua dấu phân cách trước mỗi token và lấy token tiếp theo. Nó không tự ghép ba từ thành một tên đầy đủ.

Một trường hợp đáng chú ý:

```java
// Nếu đầu vào bắt đầu bằng: "Java 17"
String text = sc.next(); // "Java"
```

Dù `17` là số, `next()` vẫn chỉ trả về một `String`. Muốn xử lý con số, có thể gọi `nextInt()` ở lượt tiếp theo nếu token phù hợp, hoặc chuyển đổi chuỗi đã nhận bằng phương thức như `Integer.parseInt` sau khi kiểm tra dữ liệu.

Vì đọc token chứ không đọc dòng, `next()` không phù hợp để nhập **họ tên, địa chỉ, câu mô tả chứa nhiều từ** nếu cần giữ nguyên nội dung cả dòng.

## 4. `nextInt()`: đọc và phân tích một token số nguyên

Giả sử người dùng nhập:

```text
18 25
```

```java
int a = sc.nextInt(); // 18
int b = sc.nextInt(); // 25
```

`nextInt()` đọc từng token và chuyển sang `int`. Cần phân biệt:

- `next()` với token `18` trả về chuỗi `"18"`.
- `nextInt()` với token `18` trả về giá trị nguyên `18`.
- Token `"abc"` không phải `int` hợp lệ, nên `nextInt()` có thể ném `InputMismatchException`.
- Token biểu diễn số nằm **ngoài phạm vi của `int`** cũng có thể gây `InputMismatchException`.
- Nếu không còn token nào, phương thức có thể ném `NoSuchElementException`; nếu `Scanner` đã đóng thì các thao tác đọc có thể ném `IllegalStateException`.

Theo mặc định, `nextInt()` dùng radix 10 và phân tích số theo **locale** của `Scanner`. Có thể chỉ định radix bằng `nextInt(radix)` hoặc `useRadix`; locale cũng có thể ảnh hưởng cách đọc một số định dạng số. Đối với đầu vào đơn giản `18` hoặc `-7`, hành vi thông dụng là đọc được những giá trị tương ứng.

Nếu đang kiểm tra nhập số từ người dùng, thường nên kiểm tra trước bằng `hasNextInt()`:

```java
if (sc.hasNextInt()) {
    int age = sc.nextInt();
    System.out.println("Tuoi: " + age);
} else if (sc.hasNext()) {
    String invalid = sc.next(); // Tiêu thụ token không hợp lệ
    System.out.println("Khong phai so nguyen: " + invalid);
}
```

Đoạn này minh họa bên trong một phương thức. `hasNextInt()` chỉ **kiểm tra** token kế tiếp, không tự tiêu thụ token đó. Nếu nhận `false` rồi lại gọi `nextInt()` mà không thay đổi dữ liệu hoặc xử lý lỗi, bạn sẽ không khắc phục được vấn đề.

## 5. `nextLine()`: lấy phần còn lại của dòng

Khi bắt đầu đọc tại đầu dòng:

```text
Nguyen Van An
```

```java
String fullName = sc.nextLine(); // "Nguyen Van An"
```

`nextLine()` giữ các dấu cách giữa các từ trong chuỗi; nó trả về nội dung dòng **không chứa ký tự kết thúc dòng**. Phương thức này phù hợp để nhập tên đầy đủ, câu văn hoặc địa chỉ cần giữ khoảng trắng.

Nếu vị trí hiện tại đã ở giữa dòng, nó chỉ đọc **phần còn lại**:

```text
Java Core
```

```java
String first = sc.next();    // "Java"
String rest = sc.nextLine(); // " Core" (có dấu cách đầu chuỗi)
```

Hành vi trên có ý nghĩa quan trọng: `nextLine()` không tự bỏ khoảng trắng ở đầu nội dung như `next()`. Gọi `trim()` nếu muốn xóa dấu cách ở hai đầu, nhưng **chỉ làm thế khi yêu cầu nhập liệu thật sự cho phép** vì có thể làm thay đổi nội dung mà người dùng muốn giữ.

Nếu tới hết đầu vào và không còn dòng nào để đọc, `nextLine()` có thể ném `NoSuchElementException`. Khi đọc từ file/chuỗi, có thể dùng `hasNextLine()` trước khi gọi `nextLine()` để xử lý trường hợp hết dữ liệu.

## 6. Trộn token với dòng: kiểm tra phần dư trước khi bỏ

Với đầu vào hai dòng `20\nNguyen Van An\n`, `nextInt()` tiêu thụ token `20` nhưng `nextLine()` kế tiếp trả `""` vì nó chỉ lấy phần còn lại của dòng số. Muốn đọc dòng tên tiếp theo, bạn có thể **đọc bỏ phần dư** bằng một lần `nextLine()` rồi mới đọc lần nữa.

Cách này chỉ đúng khi tên thực sự ở dòng riêng. Với `20 Nguyen Van An\n`, phần dư của dòng số là `" Nguyen Van An"` và cần được **giữ lại**. Nếu chương trình quy định mỗi trường một dòng, có thể đọc `nextLine()` nhất quán rồi chuyển số bằng `Integer.parseInt`, xử lý `NumberFormatException`.

Chương trình chạy độc lập tiếp theo kiểm tra cả trường hợp phần dư rỗng, phần dư chứa số và token sai; bài chuyên về **phần xuống dòng còn lại** hướng dẫn chi tiết hơn về EOF, nhiều trường trên một dòng và cách phòng mất dữ liệu.

## 7. Chương trình Java 17+ chạy độc lập để quan sát chính xác

Để đầu ra xác định trên mọi máy mà không cần nhập tay, ví dụ dùng `Scanner` đọc từ **chuỗi đầu vào cố định**. Đây là cùng các phương thức áp dụng khi nguồn dữ liệu là `System.in`.

Tạo tệp **`ScannerReadDemo.java`**:

```java
import java.util.Scanner;

public class ScannerReadDemo {
    static void tokensAndLines() {
        String input = "Lan Nguyen\n27\nJava Core\n";
        try (Scanner scanner = new Scanner(input)) {
            System.out.println("next1=[" + scanner.next() + "]");
            System.out.println("next2=[" + scanner.next() + "]");
            System.out.println("nextInt=" + scanner.nextInt());
            System.out.println("afterInt=[" + scanner.nextLine() + "]");
            System.out.println("fullLine=[" + scanner.nextLine() + "]");
        }
    }

    static void remainingSameLine() {
        try (Scanner scanner = new Scanner("42  7\nHello world\n")) {
            System.out.println("number=" + scanner.nextInt());
            System.out.println("rest=[" + scanner.nextLine() + "]");
            System.out.println("line=[" + scanner.nextLine() + "]");
        }
    }

    static void recoverInvalidToken() {
        try (Scanner scanner = new Scanner("abc 15\n")) {
            if (!scanner.hasNextInt() && scanner.hasNext()) {
                System.out.println("bad=[" + scanner.next() + "]");
            }
            System.out.println("valid=" + scanner.nextInt());
        }
    }

    static void parseWholeLine() {
        try (Scanner scanner = new Scanner("20\nNguyen Van An\n")) {
            int age = Integer.parseInt(scanner.nextLine().trim());
            String name = scanner.nextLine();
            System.out.println("age=" + age);
            System.out.println("name=[" + name + "]");
        }
    }

    public static void main(String[] args) {
        tokensAndLines();
        remainingSameLine();
        recoverInvalidToken();
        parseWholeLine();
    }
}
```

Mở terminal tại thư mục chứa file, với **JDK 17 trở lên**, chạy giống nhau trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 ScannerReadDemo.java
java ScannerReadDemo
```

Kết quả **stdout chính xác** (biên dịch thành công không in gì):

```text
next1=[Lan]
next2=[Nguyen]
nextInt=27
afterInt=[]
fullLine=[Java Core]
number=42
rest=[  7]
line=[Hello world]
bad=[abc]
valid=15
age=20
name=[Nguyen Van An]
```

Giải thích:

1. Hai lần `next()` đọc `Lan` và `Nguyen`. `nextInt()` bỏ qua dấu xuống dòng và đọc số `27`.
2. `afterInt=[]` chứng minh lần gọi `nextLine()` sau `nextInt()` gặp ngay dấu xuống dòng của dòng `27`, nên nhận chuỗi rỗng; lần tiếp theo mới đọc `Java Core`.
3. Trong `"42  7\n"`, sau khi `nextInt()` lấy `42`, `nextLine()` trả về **hai dấu cách rồi `7`**; phần dư **không phải lúc nào cũng rỗng**.
4. `hasNextInt()` gặp token `abc` nên trả `false`, nhưng không tiêu thụ token; `next()` loại bỏ token đó, rồi `nextInt()` đọc `15`.
5. `parseWholeLine()` cho thấy cách đọc tuổi và họ tên theo từng dòng, chuyển chuỗi tuổi thành số bằng `Integer.parseInt`.

Trong ví dụ này, mỗi `Scanner` được tạo từ `String` rồi đóng bằng **try-with-resources**, không ảnh hưởng tới `System.in`. Đó là lý do chương trình tự chạy được và không cần tương tác bàn phím.

## 8. Dùng `Scanner(System.in)` trong chương trình có nhập bàn phím

Một mẫu đơn giản khi người dùng nhập tuổi và họ tên ở **hai dòng riêng**:

```java
import java.util.Scanner;

public class KeyboardInput {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Nhap tuoi: ");
        int age = sc.nextInt();
        sc.nextLine(); // Tiêu thụ phần dư trên dòng tuổi
        System.out.print("Nhap ho ten: ");
        String name = sc.nextLine();
        System.out.println("Tuoi=" + age);
        System.out.println("Ten=" + name);
    }
}
```

Lưu thành **`KeyboardInput.java`**, biên dịch và chạy:

```bash
javac -encoding UTF-8 KeyboardInput.java
java KeyboardInput
```

Ví dụ nhập:

```text
20
Nguyen Van An
```

Khi đó hai dòng kết quả cuối chương trình là:

```text
Tuoi=20
Ten=Nguyen Van An
```

Đây là ví dụ **tương tác** nên các prompt `Nhap tuoi:` và `Nhap ho ten:` có thể xuất hiện chung dòng với nội dung người dùng gõ; đừng coi bản ghi màn hình terminal (có cả echo bàn phím) là stdout thuần cố định.

Một lưu ý về đóng tài nguyên: `Scanner.close()` sẽ đóng cả nguồn đầu vào bên dưới nếu nguồn đó triển khai `Closeable`. Khi dùng `new Scanner(System.in)`, đóng scanner có thể đóng luôn luồng **`System.in`** dùng chung, khiến phần khác của chương trình không còn đọc được. Với chương trình console nhỏ chỉ đọc một lần rồi kết thúc, có thể cân nhắc quyền sở hữu nguồn; với ứng dụng lớn, **không tạo rồi đóng nhiều scanner riêng trên cùng `System.in`**. Trong ví dụ bàn phím trên, scanner được giữ cho toàn bộ thời gian cần đọc và không đóng thủ công giữa chừng.

## 9. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân / cách xử lý |
| --- | --- |
| `nextLine()` nhận chuỗi rỗng sau `nextInt()` | Dấu kết thúc dòng còn lại; nếu hai trường ở hai dòng, gọi một lần `nextLine()` để tiêu thụ phần dư |
| `next()` chỉ nhận một từ của họ tên | Nó đọc token, không đọc cả dòng; dùng `nextLine()` nếu cần đủ nội dung |
| `nextInt()` ném `InputMismatchException` | Token hiện tại không phải số nguyên phù hợp hoặc ngoài phạm vi `int`; kiểm tra với `hasNextInt()` và có chiến lược loại bỏ token lỗi |
| Chương trình cứ gặp cùng một token lỗi | `hasNextInt()` không tiêu thụ token; cần `next()`, đọc lại dòng hoặc thiết kế luồng nhập |
| `nextLine()` bị mất dữ liệu trên dòng số | Lệnh tiêu thụ phần dư đã loại cả nội dung sau số; thiết kế rõ dữ liệu cùng dòng hay khác dòng |
| Đọc tới hết dữ liệu gây `NoSuchElementException` | Kiểm tra `hasNext()` / `hasNextLine()` thích hợp trước khi đọc |
| Đọc sau khi đóng `Scanner` gây `IllegalStateException` | Không dùng scanner đã đóng; quản lý vòng đời nguồn vào |
| Có hai `Scanner` dùng cùng `System.in` gây hành vi khó hiểu | Dùng một scanner ở phạm vi hợp lý; đừng tự đóng nguồn còn dùng chung |
| Đọc chuỗi số bằng `nextLine()` nhưng `parseInt` thất bại | Xử lý `NumberFormatException`, quy định rõ chuỗi trống/khoảng trắng/phạm vi số |
| Tưởng `nextInt()` luôn lấy cả dòng | Nó chỉ đọc token số nguyên, không tự tiêu thụ dấu xuống dòng sau token |

## 10. Checklist quyết định cách đọc

1. Muốn nhập **một từ hoặc mã không có dấu phân cách**? Chọn `next()`.
2. Muốn nhập **một số nguyên từ token kế tiếp**? Chọn `nextInt()`, có kiểm tra dữ liệu sai khi cần.
3. Muốn nhập **cả tên/câu/dòng**? Chọn `nextLine()`.
4. Vừa đọc bằng phương thức token rồi muốn đọc dòng khác? Xem **có phần dư sau token** hay không; xử lý ranh giới dòng một cách có chủ đích.
5. Cần dữ liệu theo **từng dòng cố định**? Thường nên dùng `nextLine()` nhất quán rồi chuyển đổi giá trị, để mỗi lần đọc tiêu thụ một dòng.
6. Đọc file/chuỗi có thể hết bất ngờ? Dùng `hasNext()`, `hasNextInt()` hoặc `hasNextLine()` theo đúng phương thức định gọi.

**Ghi nhớ:** `next()` trả **một token dạng chuỗi**; `nextInt()` trả **một token số nguyên**; `nextLine()` trả **phần còn lại của dòng**, không chỉ token. Khi trộn đọc token với đọc dòng, phải hiểu vị trí quét và xử lý dấu xuống dòng còn lại để tránh mất dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-007">Xử lý phần xuống dòng còn lại sau khi đọc số</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý phần xuống dòng còn lại sau khi đọc số (Java 17+)

## 1. Hiện tượng thường gặp

Khi dùng `Scanner` đọc dữ liệu console, một lỗi rất hay gặp là gọi `nextInt()` hoặc `nextDouble()` rồi gọi ngay `nextLine()` để đọc họ tên, ghi chú hoặc câu văn. Kết quả đầu tiên của `nextLine()` có thể là **chuỗi rỗng** hoặc chỉ gồm dấu cách, dù người dùng đã dự định nhập một dòng văn bản khác.

Ví dụ, đầu vào:

```text
21
Nguyen Van An
```

Nếu code là:

```java
int age = scanner.nextInt();
String name = scanner.nextLine();
```

thì `age` là `21`, nhưng `name` là `""`, vì `nextLine()` đang đọc **phần còn lại của dòng chứa số 21**, không phải dòng `Nguyen Van An`.

Vấn đề không phải Java tự tạo một dòng rỗng. Nó xuất phát từ việc kết hợp **đọc token** với **đọc dòng** trên cùng đối tượng `Scanner`.

## 2. Cơ chế: `nextInt()` và `nextLine()` đọc khác nhau

`Scanner` có hai kiểu đọc thường dùng:

| Phương thức | Đơn vị đọc | Điều xảy ra với ký tự kết thúc dòng |
| --- | --- | --- |
| `next()` | Một token, mặc định được phân cách bằng khoảng trắng | Không đọc toàn bộ phần còn lại của dòng |
| `nextInt()`, `nextLong()` | Một token số nguyên | Parse token, không tự tiêu thụ phần còn lại của dòng |
| `nextDouble()`, `nextFloat()` | Một token số thực | Parse token, không tự tiêu thụ phần còn lại của dòng |
| `nextLine()` | Phần còn lại của dòng hiện tại | Đọc tới hết dòng, bỏ ký tự kết thúc dòng khỏi chuỗi trả về |
| `hasNextLine()` | Kiểm tra có thể đọc dòng tiếp hay không | Không tiêu thụ dòng |

Mặc định, `Scanner` nhận khoảng trắng, tab, ký tự xuống dòng là các dấu phân cách token. Nhưng việc **tách token** không đồng nghĩa với việc **đã đi qua hết dòng**.

Với input `21\nNguyen Van An\n` (trong đó `\n` đại diện cho một ký tự xuống dòng), có thể hình dung:

```text
Ban đầu:
21↵
Nguyen Van An↵
^

Sau nextInt():
21↵
Nguyen Van An↵
  ^
  (phần kết thúc dòng 21 vẫn cần được xử lý)

Sau nextLine():
21↵
Nguyen Van An↵
   ^
   (đã chuyển sang dòng kế tiếp, nhưng kết quả vừa trả là "")

Sau nextLine() tiếp:
21↵
Nguyen Van An↵
             ^
             (lấy được "Nguyen Van An")
```

Sơ đồ thể hiện trình tự logic, không phải vị trí byte chính xác của bộ đệm nội bộ. Đặc biệt, nếu input là `21   \n` có **ba dấu cách** sau số, `nextLine()` đầu tiên có thể trả về `"   "`, chứ không phải `""`. Vì vậy cách diễn đạt chính xác là **đọc phần dư của dòng**, đôi khi phần dư đó rỗng.

## 3. Chương trình tái hiện lỗi với kết quả cố định

Tạo file **`LeftoverDemo.java`**:

```java
import java.util.Scanner;

public class LeftoverDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("21\nNguyen Van An\n")) {
            int age = scanner.nextInt();
            String firstLine = scanner.nextLine();
            String secondLine = scanner.nextLine();

            System.out.println("age=" + age);
            System.out.println("firstLine=[" + firstLine + "]");
            System.out.println("secondLine=[" + secondLine + "]");
        }
    }
}
```

Để biên dịch và chạy bằng **JDK 17+** trong thư mục chứa file (PowerShell, CMD, bash), tạo thư mục output nếu chưa có:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out LeftoverDemo.java
java -cp out LeftoverDemo
```

**Stdout chính xác:**

```text
age=21
firstLine=[]
secondLine=[Nguyen Van An]
```

`new Scanner(String)` được dùng để mô phỏng console, giúp tái hiện đầu vào không phụ thuộc người đang gõ. Khi thay bằng `new Scanner(System.in)`, quy tắc đọc token và dòng vẫn như vậy.

Nếu chương trình chỉ gọi một lần `nextLine()` sau `nextInt()`, nó sẽ nhận `firstLine=[]` và dễ gây hiểu nhầm rằng Java “bỏ qua” họ tên. Thực tế, họ tên vẫn nằm ở dòng kế tiếp, đang chờ được đọc.

## 4. Cách sửa A: tiêu thụ phần dư của dòng sau khi đọc số

Khi định dạng đầu vào quy định **số ở một dòng riêng**, tiếp theo là **văn bản ở dòng riêng**, có thể thêm một lần `nextLine()` để **bỏ phần dư** của dòng chứa số:

```java
int age = scanner.nextInt();
scanner.nextLine();             // bỏ phần còn lại của dòng chứa tuổi
String name = scanner.nextLine();
```

Dòng `scanner.nextLine();` ở giữa không phải để nhận tên; nó chỉ hoàn tất dòng đã chứa số.

### Chương trình hoàn chỉnh đọc từ console

Tạo file **`ConsumeRemainderDemo.java`**:

```java
import java.util.Scanner;

public class ConsumeRemainderDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Enter age:");

            if (!scanner.hasNextInt()) {
                System.out.println("Error: expected an integer age");
                return;
            }

            int age = scanner.nextInt();

            if (!scanner.hasNextLine()) {
                System.out.println("Error: expected a separate name line");
                return;
            }
            scanner.nextLine(); // consume rest of age line

            System.out.println("Enter full name:");
            if (!scanner.hasNextLine()) {
                System.out.println("Error: name line missing");
                return;
            }
            String name = scanner.nextLine();

            System.out.println("age=" + age);
            System.out.println("name=" + name);
        }
    }
}
```

Biên dịch:

```sh
javac -encoding UTF-8 --release 17 -d out ConsumeRemainderDemo.java
java -cp out ConsumeRemainderDemo
```

Với đầu vào **hai dòng riêng**:

```text
21
Nguyen Van An
```

**Stdout của chính chương trình** (không tính ký tự terminal tự echo khi gõ phím):

```text
Enter age:
Enter full name:
age=21
name=Nguyen Van An
```

Để chạy lặp lại với đầu vào cố định, lưu đúng hai dòng trên vào `sample.txt`, rồi dùng:

- Bash hoặc Windows CMD: `java -cp out ConsumeRemainderDemo < sample.txt`.
- Windows PowerShell: `Get-Content -LiteralPath sample.txt | java -cp out ConsumeRemainderDemo`.

Ví dụ giả định tuổi hợp lệ về mặt số nguyên. Để kiểm tra giới hạn tuổi (ví dụ 0–120), cần một bước validation nghiệp vụ riêng sau khi đọc số.

**Điểm quan trọng:** Không thêm `nextLine()` bỏ phần dư một cách máy móc nếu thông tin bạn cần vẫn **nằm trên chính dòng chứa số**. Việc bỏ phần dư khi đó sẽ làm mất dữ liệu thật.

## 5. Khi số và văn bản ở cùng một dòng: giữ lại phần dư

Giả sử định dạng là:

```text
21 Nguyen Van An
```

Ở đây, sau `nextInt()`, phần dư của dòng là `" Nguyen Van An"`, tức chính là họ tên bạn cần. Nếu gọi `scanner.nextLine();` chỉ để vứt kết quả, bạn sẽ mất tên.

Thay vào đó, hãy **lưu phần dư** rồi xử lý dấu cách đầu dòng.

Tạo **`SameLineDemo.java`**:

```java
import java.util.Scanner;

public class SameLineDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("21 Nguyen Van An\n")) {
            int age = scanner.nextInt();
            String name = scanner.nextLine().stripLeading();

            System.out.println("age=" + age);
            System.out.println("name=" + name);
        }
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out SameLineDemo.java
java -cp out SameLineDemo
```

**Stdout chính xác:**

```text
age=21
name=Nguyen Van An
```

`stripLeading()` (có từ Java 11) bỏ khoảng trắng ở đầu chuỗi nhưng giữ nguyên phần còn lại, phù hợp khi dấu cách đầu là ranh giới sau số. `trim()` hoặc `strip()` có thể dùng khi đầu vào được phép xóa khoảng trắng **cả hai đầu**. Cần chọn theo hợp đồng input, nhất là nếu khoảng trắng cuối dòng có ý nghĩa.

Nếu không có tên sau số, `name` có thể rỗng; khi đó cần xử lý lỗi dữ liệu thiếu, không tự giả định rằng còn một dòng kế tiếp.

## 6. Cách sửa B: chỉ dùng `nextLine()`, rồi parse số

Đối với chương trình console nhập **mỗi trường trên một dòng**, cách nhất quán và thường dễ kiểm soát nhất là đọc toàn bộ từng dòng, sau đó tự chuyển sang kiểu số:

```java
String ageText = scanner.nextLine();
int age = Integer.parseInt(ageText.strip());
String name = scanner.nextLine();
```

Cách này không trộn đọc token với đọc dòng nên không còn lỗi phần dư do `nextInt()`/ `nextLine()`. Đổi lại, `Integer.parseInt` ném `NumberFormatException` nếu dòng không biểu diễn số nguyên hợp lệ.

### Ví dụ đầy đủ: xử lý input thiếu hoặc sai

Tạo file **`LineFirstDemo.java`**:

```java
import java.util.Scanner;

public class LineFirstDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Enter age:");
            if (!scanner.hasNextLine()) {
                System.out.println("Error: age line missing");
                return;
            }

            String ageLine = scanner.nextLine();
            final int age;
            try {
                age = Integer.parseInt(ageLine.strip());
            } catch (NumberFormatException ex) {
                System.out.println("Error: age must be an integer");
                return;
            }

            if (age < 0 || age > 120) {
                System.out.println("Error: age out of range");
                return;
            }

            System.out.println("Enter full name:");
            if (!scanner.hasNextLine()) {
                System.out.println("Error: name line missing");
                return;
            }

            String name = scanner.nextLine().strip();
            if (name.isEmpty()) {
                System.out.println("Error: name is empty");
                return;
            }

            System.out.println("age=" + age);
            System.out.println("name=" + name);
        }
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out LineFirstDemo.java
java -cp out LineFirstDemo
```

Với input:

```text
21
Nguyen Van An
```

**Stdout chính xác:**

```text
Enter age:
Enter full name:
age=21
name=Nguyen Van An
```

Với dòng tuổi là `abc`, chương trình in:

```text
Enter age:
Error: age must be an integer
```

Nếu dòng tuổi hợp lệ nhưng chưa có dòng tên vì input đã hết (EOF), chương trình in `Error: name line missing` sau khi in hai lời nhắc. `hasNextLine()` giúp tránh `NoSuchElementException` khi nguồn input không còn dòng để đọc.

Cách dùng `nextLine()` rồi `Integer.parseInt` đặc biệt hữu ích khi cần kiểm tra độ dài, đầu vào rỗng, thông báo lỗi dễ hiểu, số nguyên ngoài phạm vi hoặc yêu cầu nhập lại. Ngoài ra, với số thực có dấu chấm thập phân cố định, có thể đọc cả dòng rồi gọi `Double.parseDouble(line.strip())`; cách này parse theo cú pháp của `Double`, **khác** quy tắc locale của `Scanner.nextDouble()`.

## 7. `nextDouble()` cũng có vấn đề tương tự

Vấn đề không chỉ xảy ra với `nextInt()`. Các phương thức đọc token như `nextLong()`, `nextFloat()`, `nextDouble()` cũng không tự hoàn tất dòng.

Tạo **`DoubleRemainderDemo.java`**:

```java
import java.util.Locale;
import java.util.Scanner;

public class DoubleRemainderDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("8.5\nGood progress\n")) {
            scanner.useLocale(Locale.US);

            double score = scanner.nextDouble();
            scanner.nextLine(); // consume remainder of score line
            String note = scanner.nextLine();

            System.out.printf(Locale.US, "score=%.1f%n", score);
            System.out.println("note=" + note);
        }
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out DoubleRemainderDemo.java
java -cp out DoubleRemainderDemo
```

**Stdout chính xác:**

```text
score=8.5
note=Good progress
```

`Locale.US` khiến scanner hiểu token `8.5` theo quy tắc dấu chấm thập phân, đồng thời `printf(Locale.US, ...)` in dấu chấm trong kết quả. Nếu chương trình dùng `nextDouble()` theo locale mặc định, việc parse `8.5` có thể không đúng như mong đợi trên máy dùng quy ước dấu thập phân khác.

## 8. Những trường hợp cần xét trước khi bỏ phần dư

| Dữ liệu đầu vào | Sau khi gọi `nextInt()` | Cách xử lý phù hợp |
| --- | --- | --- |
| `21\nAn\n` | `nextLine()` đầu tiên trả `""` | Có thể bỏ phần dư rồi đọc dòng tên |
| `21   \nAn\n` | Phần dư của dòng có ba dấu cách | Có thể bỏ phần dư rồi đọc dòng tên |
| `21 An\n` | Phần dư là `" An"` | Giữ `nextLine()` và lấy tên từ phần dư |
| `21 3\n` | Còn token `3` trên cùng dòng | Tiếp tục đọc token theo cấu trúc input, đừng bỏ dòng tùy tiện |
| `21\n\nAn\n` | Sau khi bỏ dòng tuổi, dòng tiếp theo thật sự rỗng | Quyết định có cho phép dòng rỗng không; không tự skip hết các dòng rỗng |
| Chỉ có `21` rồi EOF | Không có dòng tên | Kiểm tra `hasNextLine()`, báo input thiếu thay vì gọi vô điều kiện |
| Nhập chữ `abc` khi đang gọi `nextInt()` | Số chưa được đọc thành công; `InputMismatchException` có thể xảy ra | Xử lý lỗi token, không áp dụng bước bỏ phần dư như thể đã đọc số hợp lệ |

Cần lưu ý `nextLine()` không luôn trả về rỗng; chính xác hơn, nó trả **phần còn lại của dòng hiện tại**, loại bỏ dấu kết thúc dòng. Nếu sau token là khoảng trắng, văn bản hoặc tab, chúng có thể nằm trong phần dư.

## 9. Xuống dòng trên Windows, Linux và macOS

Các dòng văn bản có thể kết thúc bởi ký tự LF (`\n`) hoặc cặp CRLF (`\r\n`). `Scanner.nextLine()` nhận diện ranh giới dòng theo quy tắc của Scanner và trả nội dung dòng **không kèm dấu kết thúc dòng**.

Vì thế trong trường hợp thông thường, bạn không phải kiểm tra chuỗi có chứa `\r` cuối dòng để sửa lỗi `nextInt()` rồi `nextLine()`; nguyên nhân chính vẫn là ranh giới **token và dòng**.

Nếu dùng `System.out.printf(...)`, có thể kết thúc dòng bằng `%n` để phù hợp nền tảng; ký hiệu này dành cho **đầu ra**, không thay đổi cách `Scanner` đọc input.

Mã nguồn đặt trong string literal như `"21\nAn\n"` chứa các ký tự escape được Java giải mã thành LF lúc chương trình chạy. Khi nhập từ terminal thực, cách terminal echo và gửi ký tự xuống dòng có thể khác, nhưng quy tắc mức cao của `nextLine()` vẫn như trên.

## 10. Khi nào không nên gọi `nextLine()` “bỏ newline”?

Không nên viết thành thói quen cố định rằng sau **mọi** lời gọi `nextInt()` phải gọi thêm `nextLine()`. Điều đó sẽ làm mất dữ liệu nếu một dòng chứa nhiều token.

Ví dụ input:

```text
3 8 12
```

Nếu mục tiêu là đọc ba số, cách đúng là:

```java
int a = scanner.nextInt();
int b = scanner.nextInt();
int c = scanner.nextInt();
```

Không có lý do thêm `scanner.nextLine();` sau số đầu tiên, vì làm thế sẽ bỏ `8 12`. Đoạn trên cần một `Scanner` đã khởi tạo; nó minh họa chiến lược đọc token, không phải chương trình độc lập.

Tương tự, nếu nhập `21 Nguyen Van An` trên một dòng, `scanner.nextLine()` sau `nextInt()` chính là cơ hội lấy **họ tên còn lại**. Phải **lưu kết quả** thay vì bỏ đi.

Để thiết kế chương trình rõ ràng, hãy quyết định trước:

1. **Mỗi giá trị trên một dòng**: chọn `nextLine()` cho toàn bộ các trường, sau đó parse số; dễ xác thực và báo lỗi.
2. **Nhiều token số trong một dòng**: tiếp tục đọc bằng `nextInt()`/`nextDouble()` hoặc `next()` theo thứ tự.
3. **Token đầu là số, phần còn lại là văn bản cùng dòng**: đọc số bằng `nextInt()`, rồi **giữ phần dư** qua `nextLine()`.
4. **Số ở dòng riêng, văn bản ở dòng kế tiếp**: sau `nextInt()` có thể gọi `nextLine()` một lần để bỏ phần dư, rồi mới đọc dòng kế; cần kiểm tra EOF.

## 11. Lỗi thường gặp và phương án khắc phục

**Lỗi 1 — cho rằng `nextInt()` tiêu thụ luôn Enter.** Thực tế nó phân tích token số, không tự chuyển sang dòng sau. Sửa bằng cách chọn chiến lược đọc nhất quán hoặc xử lý phần dư.

**Lỗi 2 — dùng `nextLine()` rồi tiếp tục chờ tên mà không kiểm tra kết quả.** Hãy in kết quả vào dấu ngoặc vuông lúc debug (`"[" + name + "]"`) để thấy tên rỗng hoặc chỉ chứa khoảng trắng.

**Lỗi 3 — gọi bỏ dòng trước khi chắc chắn đọc số thành công.** Nếu `nextInt()` gặp token sai, nó có thể ném `InputMismatchException`, nên luồng xử lý thông thường sẽ không đến bước kế tiếp. Dùng `hasNextInt()` hoặc `try/catch` phù hợp.

**Lỗi 4 — vô tình làm mất dữ liệu cùng dòng.** `scanner.nextLine();` sẽ bỏ toàn bộ phần còn lại của dòng, không chỉ “xóa một ký tự newline”. Trước khi bỏ, phải biết định dạng input bảo đảm phần dư không có dữ liệu cần dùng.

**Lỗi 5 — giả định lúc nào cũng còn dòng tiếp theo.** Với nguồn đọc từ file, pipe hoặc EOF, có thể không còn dòng. Dùng `hasNextLine()` khi cần và xử lý thiếu dữ liệu.

**Lỗi 6 — dùng nhiều Scanner trên cùng `System.in`.** Các scanner có bộ đệm riêng, có thể cạnh tranh dữ liệu khi cùng đọc một luồng. Nên dùng một scanner chung, truyền vào những phương thức cần đọc và quản lý vòng đời phù hợp.

**Lỗi 7 — nhầm với sai locale.** `nextDouble()` không parse được `8.5` có thể do thiết lập `Locale`, không phải newline. Kiểm tra lỗi định dạng số trước, rồi mới xử lý ranh giới dòng.

**Lỗi 8 — đóng `Scanner(System.in)` quá sớm.** Đóng scanner thường đóng luôn luồng `System.in` bên dưới. Trong chương trình đơn giản kết thúc sau nhập liệu, `try-with-resources` như ví dụ là phù hợp; trong ứng dụng lớn, cần một nơi sở hữu vòng đời luồng console.

## 12. Bài tập tự kiểm tra

**Câu 1.** Với input `10\nHello\n`, sau `scanner.nextInt();`, lần `scanner.nextLine()` kế tiếp trả gì?

**Đáp án:** Chuỗi rỗng `""`; dòng `Hello` chưa được đọc.

**Câu 2.** Với input `10 World\n`, có nên bỏ kết quả của `nextLine()` sau khi đọc số nếu cần `World` không?

**Đáp án:** Không. Phần dư `" World"` chứa dữ liệu cần dùng; lưu lại và loại dấu cách đầu nếu quy tắc input cho phép.

**Câu 3.** `scanner.nextInt(); scanner.nextLine(); scanner.nextLine();` có luôn hợp lệ không?

**Đáp án:** Không. Phải biết cấu trúc input, còn đủ dòng và các lời gọi trước đã thành công hay chưa. Cách gọi này còn có thể bỏ nhầm dữ liệu trên dòng số.

**Câu 4.** Có thể tránh trộn hai phương thức bằng cách nào?

**Đáp án:** Đọc từng trường qua `nextLine()`, sau đó `Integer.parseInt` hoặc `Double.parseDouble` và xử lý ngoại lệ/giới hạn tương ứng.

**Câu 5.** Vì sao `nextLine()` đầu tiên sau một số không nhất thiết rỗng?

**Đáp án:** Vì nó lấy toàn bộ **phần dư của dòng**, có thể gồm khoảng trắng hoặc văn bản sau token số.

**Ghi nhớ:** Phần xuống dòng “còn lại” sau `nextInt()`/`nextDouble()` là hệ quả của việc đọc **token**, trong khi `nextLine()` đọc **phần còn lại của dòng**. Cách sửa đúng phụ thuộc dữ liệu nằm ở dòng tiếp theo hay cùng dòng với số. Đọc theo dòng rồi parse thường là lựa chọn rõ ràng nhất cho form console.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-008">Kiểm tra đầu vào và chuyển chuỗi thành số</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra đầu vào và chuyển chuỗi thành số — Java 17+

## 1. Vì sao cần kiểm tra trước và sau khi chuyển kiểu?

Dữ liệu mà chương trình nhận từ bàn phím, file hoặc dòng lệnh ban đầu thường là **chuỗi**. Ngay cả khi người dùng gõ `25`, giá trị đọc qua `BufferedReader.readLine()` hay `Scanner.nextLine()` vẫn có kiểu `String`. Muốn cộng, nhân, so sánh giới hạn, ta cần **chuyển chuỗi thành kiểu số** và **xác thực dữ liệu**.

Ba bước không được nhầm với nhau:

1. **Đọc dữ liệu:** lấy chuỗi người dùng nhập, ví dụ `"12"`.
2. **Chuyển kiểu (parse):** dùng `Integer.parseInt("12")` để nhận số nguyên `12`; bước này có thể thất bại nếu chuỗi không có định dạng hợp lệ hoặc vượt giới hạn kiểu.
3. **Kiểm tra ràng buộc (validation):** xác nhận giá trị thuộc miền nghiệp vụ, ví dụ số lượng `1..100`. Chuyển được `"0"` thành số `0` không có nghĩa số lượng 0 được phép.

~~~text
Chuỗi đầu vào
    |
    v
Kiểm tra thiếu/rỗng, chuẩn hóa hợp lệ
    |
    v
Chuyển thành số (parse)
    |             \
    |              \ lỗi định dạng / vượt phạm vi kiểu
    v               v
Kiểm tra miền       Thông báo lỗi phù hợp
nghiệp vụ
    |             \
    v              \ sai miền
Dùng số hợp lệ       Thông báo lỗi phù hợp
~~~

Mục tiêu là **không để dữ liệu xấu đi sâu vào phép tính**, đồng thời đưa ra thông báo cụ thể để người dùng sửa đầu vào.

## 2. Đọc một dòng từ stdin

Với Java 17, có thể dùng `BufferedReader` để nhận nguyên văn một dòng:

~~~java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));
String line = reader.readLine();
~~~

- `System.in` là luồng input chuẩn (stdin).
- `InputStreamReader` chuyển byte thành ký tự; chỉ định `StandardCharsets.UTF_8` giúp thống nhất mã hóa.
- `BufferedReader.readLine()` đọc một dòng, bỏ ký tự kết thúc dòng và trả về `String`.
- Nếu **hết dữ liệu** trước khi đọc được dòng, `readLine()` trả về **`null`**.
- Thao tác I/O có thể ném checked `IOException`, nên method phải catch hoặc declare `throws IOException`.

Khi người dùng **chỉ nhấn Enter**, `readLine()` trả `""` (chuỗi rỗng), **khác** với `null` lúc EOF. Đầu vào chỉ chứa khoảng trắng như `"   "` lại là một chuỗi không rỗng về độ dài nhưng có thể bị xem là “không có nội dung” sau khi chuẩn hóa.

~~~java
if (line == null) {
    System.out.println("Missing line (EOF)");
} else if (line.isBlank()) {
    System.out.println("Empty or whitespace-only line");
}
~~~

Đây là mã minh họa đặt sau khi đã đọc `line`; chương trình chạy độc lập nằm ở mục 9.

## 3. Chuẩn hóa chuỗi trước khi parse

`String.strip()` (Java 11+) loại bỏ các ký tự khoảng trắng ở hai đầu theo quy tắc Unicode mà API hỗ trợ. Nếu hợp đồng cho phép khoảng trắng đầu/cuối:

~~~java
String raw = " 42 ";
String normalized = raw.strip();
int number = Integer.parseInt(normalized);
System.out.println(number);
~~~

**Exact stdout:**

~~~text
42
~~~

Phải kiểm tra `null` trước khi gọi `strip()`. Một chuỗi rỗng `""` hoặc chỉ có khoảng trắng sẽ không chuyển thành số bằng `Integer.parseInt`; nên thông báo “bắt buộc nhập” trước để người dùng hiểu, thay vì đưa ra một thông báo kỹ thuật khó hiểu.

**Không tùy tiện chỉnh sửa mọi chuỗi:** ví dụ yêu cầu tài chính hoặc mã định danh có thể quy định rất rõ có được bỏ khoảng trắng hay chấp nhận dấu cộng đầu chuỗi không. Việc chuẩn hóa phải tuân theo **hợp đồng đầu vào**.

## 4. Chuyển chuỗi thành `int` và `long`

Các API thường dùng:

~~~java
int age = Integer.parseInt("21");
long population = Long.parseLong("9000000000");

System.out.println(age);
System.out.println(population);
~~~

**Exact stdout:**

~~~text
21
9000000000
~~~

`Integer.parseInt(String)` nhận chuỗi biểu diễn số nguyên trong phạm vi **`-2147483648..2147483647`**; `Long.parseLong` nhận số nguyên kiểu `long` với phạm vi lớn hơn. Cả hai có thể ném **unchecked `NumberFormatException`** khi chuỗi không biểu diễn được số đúng kiểu.

Ví dụ:

| Chuỗi truyền vào `Integer.parseInt` | Kết quả |
|---|---|
| `"15"` | `15` |
| `"-8"` | `-8` |
| `"+9"` | `9` |
| `"0012"` | `12` |
| `"3.5"` | `NumberFormatException` |
| `"ten"` | `NumberFormatException` |
| `"2147483648"` | `NumberFormatException` do vượt giới hạn `int` |
| `" 12 "` | Không parse trực tiếp; cần `strip()` nếu hợp đồng cho phép |

Nếu cần **chỉ chấp nhận chữ số ASCII**, dấu hoặc định dạng đặc biệt phải được quy định và kiểm tra thêm. Chuyển số tự động không thay thế bước kiểm tra format của hệ thống.

Mẫu xử lý ngoại lệ:

~~~java
try {
    int quantity = Integer.parseInt(text.strip());
    System.out.println("quantity=" + quantity);
} catch (NumberFormatException ex) {
    System.out.println("quantity must be an integer");
}
~~~

Đoạn này giả sử `text` khác `null`; ví dụ hoàn chỉnh sẽ xử lý EOF/blank trước khi parse.

## 5. Chuyển số thực: `double` và `BigDecimal`

Chuyển sang số thực dấu phẩy động:

~~~java
double rate = Double.parseDouble("2.5");
System.out.println(rate);
~~~

**Exact stdout:**

~~~text
2.5
~~~

`Double.parseDouble` có thể hiểu dạng khoa học như `"1e3"`, đồng thời chấp nhận các giá trị đặc biệt như `"NaN"` hoặc `"Infinity"`. Nếu hợp đồng chỉ chấp nhận số thực **hữu hạn**, nên kiểm tra thêm:

~~~java
double value = Double.parseDouble(text.strip());
if (!Double.isFinite(value)) {
    throw new IllegalArgumentException("value must be finite");
}
~~~

Cũng cần kiểm tra miền cho phép, ví dụ `0.0 <= value && value <= 1.0`.

### Khi cần số thập phân có ý nghĩa chính xác

Đối với **giá tiền, tổng tiền hoặc đơn giá** cần biểu diễn thập phân nhất quán, `BigDecimal` thường phù hợp hơn `double`:

~~~java
import java.math.BigDecimal;

BigDecimal price = new BigDecimal("12.50");
BigDecimal total = price.multiply(BigDecimal.valueOf(3));
System.out.println(total.toPlainString());
~~~

**Exact stdout:**

~~~text
37.50
~~~

Tránh `new BigDecimal(0.1)` khi muốn biểu diễn đúng giá trị thập phân `0.1` vì `double` không lưu chính xác số đó theo hệ nhị phân. Dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` tùy mục đích.

**Quan trọng:** `BigDecimal` xử lý chính xác số thập phân **đã parse**, nhưng không tự xác nhận “giá hợp lệ”. Vẫn cần giới hạn miền, số chữ số thập phân, đơn vị và luật làm tròn. Chương trình mẫu chỉ cho giá có tối đa **hai chữ số sau dấu chấm**, không âm và không vượt ngưỡng.

## 6. Định dạng số và dấu phân cách

Người dùng có thể nhập:

~~~text
12.50
12,50
1 000
1,000
1e3
~~~

Các chuỗi này **không tương đương một cách tự động**. Ví dụ `new BigDecimal("12,50")` không parse trực tiếp được. Trong ví dụ mục 9, giá có hợp đồng cố định:

- Chỉ dùng **chữ số ASCII `0..9`** và **dấu chấm `.`** cho phần thập phân.
- Cho phép số nguyên như `"12"` hoặc số có 1–2 chữ số thập phân như `"12.5"` và `"12.50"`.
- Không chấp nhận dấu phẩy, khoảng trắng ở giữa, ký hiệu tiền tệ, số mũ hoặc số âm.
- Khoảng trắng **hai đầu** được bỏ bằng `strip()`.
- Sau khi parse phải lớn hơn `0` và không vượt `1_000_000`.

Biểu thức kiểm tra định dạng Java:

~~~java
boolean validFormat = normalized.matches("[0-9]+(\\.[0-9]{1,2})?");
~~~

Trong Java string literal, `\\.` đại diện cho ký tự dấu chấm trong regex; dấu chấm được escape để không có nghĩa “bất kỳ ký tự nào”. Dùng một quy tắc format thống nhất giúp mã không phải đoán `12,50` là mười hai phẩy năm hay một danh sách hai giá trị.

Nếu ứng dụng hỗ trợ nhiều locale, hãy chọn một parser theo `Locale` xác định (ví dụ `NumberFormat`/`DecimalFormat`) với hợp đồng rõ và bảo đảm đã **đọc hết chuỗi hợp lệ**. Không giả định cách nhập dấu phẩy/thập phân giống nhau trên mọi hệ điều hành.

## 7. Phân biệt lỗi cú pháp, vượt kiểu và sai miền nghiệp vụ

Ba lỗi sau cần xử lý khác nhau:

| Nhóm lỗi | Ví dụ | Cách ứng xử |
|---|---|---|
| Thiếu dữ liệu | EOF trước khi có số lượng; chuỗi rỗng | Thông báo trường bắt buộc |
| Không chuyển được thành kiểu số | `"abc"`, `"2.5"` cho `int`, số quá lớn cho `int` | Bắt `NumberFormatException` hoặc kiểm tra cú pháp trước |
| Số hợp lệ về cú pháp nhưng sai miền | `0` cho số lượng phải `1..100`; giá `0.00` | Ném hoặc trả lỗi validation có thông điệp riêng |

Đừng dùng `assert quantity > 0` để kiểm tra đầu vào bắt buộc: câu lệnh `assert` có thể bị vô hiệu khi chạy JVM. Dùng điều kiện `if` chạy bình thường.

~~~java
static int validateQuantity(String input) {
    if (input == null || input.isBlank()) {
        throw new IllegalArgumentException("quantity is required");
    }

    final int quantity;
    try {
        quantity = Integer.parseInt(input.strip());
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "quantity must be an integer", ex);
    }

    if (quantity < 1 || quantity > 100) {
        throw new IllegalArgumentException("quantity must be 1..100");
    }
    return quantity;
}
~~~

Với `"abc"`, lỗi đến từ parse; với `"0"`, parse thành công nhưng validation miền thất bại. Khi phát hiện sai dữ liệu, nên **ngừng bước tính toán hiện tại**, thay vì tiếp tục dùng giá trị giả định hoặc “sửa” dữ liệu bí mật.

## 8. Chọn cách đọc input nhất quán

Khi bài toán cần xác thực **mỗi trường trên một dòng**, `Scanner.nextLine()` kết hợp `Integer.parseInt` hoặc `BigDecimal(String)` giúp tách ba trách nhiệm: đọc văn bản, chuyển kiểu và kiểm tra miền. Với nguồn có thể kết thúc sớm, hãy kiểm tra `hasNextLine()` trước khi đọc.

`nextInt()` đọc theo token, nên sau đó gọi `nextLine()` có thể nhận phần dư dòng số; nó cũng dùng cú pháp/locale của `Scanner` và có thể ném `InputMismatchException` thay vì `NumberFormatException` của `Integer.parseInt`. Vì chương này đang xây dựng validator chặt chẽ, ví dụ hoàn chỉnh chọn `BufferedReader.readLine()` và xử lý EOF rõ ràng.

## 9. Chương trình Java 17+ hoàn chỉnh: nhập số lượng và đơn giá

Ví dụ nhận **hai dòng từ stdin**:

1. Số lượng nguyên, từ **1 tới 100**.
2. Đơn giá thập phân, từ **lớn hơn 0 tới 1_000_000**, dùng dấu `.`, tối đa 2 chữ số thập phân.

Chương trình tính tổng `quantity × unitPrice` bằng `BigDecimal`, chuẩn hóa hiển thị đúng hai chữ số thập phân, từ chối input thiếu/sai và trả mã thoát **2** khi không hợp lệ.

Lưu file **`InputNumberValidationDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

public final class InputNumberValidationDemo {
    private InputNumberValidationDemo() {}

    static int parseQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("quantity is required");
        }

        final int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "quantity must be an integer", ex);
        }

        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("quantity must be 1..100");
        }
        return value;
    }

    static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("price is required");
        }

        String normalized = raw.strip();
        if (!normalized.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException(
                    "price must use digits and optional dot with 1-2 decimals");
        }

        final BigDecimal price;
        try {
            price = new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "price must be a decimal number", ex);
        }

        if (price.compareTo(BigDecimal.ZERO) <= 0
                || price.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException(
                    "price must be > 0 and <= 1000000");
        }

        return price.setScale(2, RoundingMode.UNNECESSARY);
    }

    static int run(BufferedReader reader) throws IOException {
        System.out.println("quantity?");
        String quantityLine = reader.readLine();
        if (quantityLine == null) {
            System.out.println("error: quantity is missing");
            return 2;
        }

        final int quantity;
        try {
            quantity = parseQuantity(quantityLine);
        } catch (IllegalArgumentException ex) {
            System.out.println("error: " + ex.getMessage());
            return 2;
        }

        System.out.println("price?");
        String priceLine = reader.readLine();
        if (priceLine == null) {
            System.out.println("error: price is missing");
            return 2;
        }

        final BigDecimal unitPrice;
        try {
            unitPrice = parsePrice(priceLine);
        } catch (IllegalArgumentException ex) {
            System.out.println("error: " + ex.getMessage());
            return 2;
        }

        BigDecimal total = unitPrice.multiply(
                BigDecimal.valueOf(quantity));
        System.out.println("quantity=" + quantity);
        System.out.println("unitPrice=" + unitPrice.toPlainString());
        System.out.println("total=" + total.toPlainString());
        System.out.println("status=OK");
        return 0;
    }

    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        System.in, StandardCharsets.UTF_8));

        int status;
        try {
            status = run(reader);
        } catch (IOException ex) {
            System.out.println("error: input unavailable");
            status = 2;
        }

        if (status != 0) {
            System.exit(status);
        }
    }
}
~~~

**Giải thích kỹ thuật:**

- Hai câu lệnh `readLine()` đọc chuỗi; chương trình kiểm tra EOF riêng để phân biệt với dòng trống.
- `parseQuantity` bắt `NumberFormatException` từ `Integer.parseInt` và chuyển thành thông điệp phù hợp; đồng thời kiểm tra miền `1..100`.
- `parsePrice` kiểm tra format trước, tạo `BigDecimal` từ chuỗi, so giá bằng `compareTo` và dùng `setScale(2, RoundingMode.UNNECESSARY)`. Vì input chỉ có tối đa hai chữ số thập phân nên bước đặt scale không phải làm tròn số; nó chuẩn hóa đầu ra, ví dụ `12.5` thành `12.50`.
- Nhánh lỗi kết thúc `run` với mã `2`, không tính tổng khi đầu vào không hợp lệ.
- `System.exit(2)` thuộc **ranh giới ứng dụng**. Không nên gọi `System.exit` trong hàm tiện ích parsing; thư viện nên cho caller quyết định lỗi nào được xử lý.
- Các thông báo của ví dụ in ra stdout để so khớp dễ dàng. CLI production thường in lỗi qua `System.err`.

### Biên dịch

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 InputNumberValidationDemo.java
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\InputNumberValidationDemo.java
~~~

Không cần Maven/Gradle, thư viện ngoài hoặc preview features. Chạy trong thư mục chứa file `.class` được biên dịch.

### Trường hợp 1 — dữ liệu hợp lệ

**Bash:**

~~~bash
printf '3\n12.50\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
@("3", "12.50") | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
price?
quantity=3
unitPrice=12.50
total=37.50
status=OK
~~~

Trường hợp này trả **exit status 0**.

### Trường hợp 2 — số lượng không phải số nguyên

**Bash:**

~~~bash
printf 'abc\n12.50\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
@("abc", "12.50") | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
error: quantity must be an integer
~~~

Chương trình không đọc tiếp đơn giá sau khi từ chối số lượng; **exit status 2**.

### Trường hợp 3 — chuyển được số nhưng ngoài miền

**Bash:**

~~~bash
printf '0\n12.50\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
@("0", "12.50") | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
error: quantity must be 1..100
~~~

`"0"` parse thành số nguyên thành công, nhưng không thỏa miền nghiệp vụ `1..100`. **Exit status 2**.

### Trường hợp 4 — dùng dấu phẩy sai định dạng

**Bash:**

~~~bash
printf '2\n12,50\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
@("2", "12,50") | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
price?
error: price must use digits and optional dot with 1-2 decimals
~~~

Format giá được quy định sử dụng `.`, không chấp nhận `,`. **Exit status 2**.

### Trường hợp 5 — giá bằng 0

**Bash:**

~~~bash
printf '2\n0.00\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
@("2", "0.00") | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
price?
error: price must be > 0 and <= 1000000
~~~

`"0.00"` đúng format và parse được thành `BigDecimal`, nhưng sai quy tắc **giá phải lớn hơn 0**; **exit status 2**.

### Trường hợp 6 — hết dữ liệu trước khi nhập giá

**Bash:**

~~~bash
printf '2\n' | java -cp . InputNumberValidationDemo
~~~

**PowerShell:**

~~~powershell
"2" | java -cp . InputNumberValidationDemo
~~~

**Exact expected stdout:**

~~~text
quantity?
price?
error: price is missing
~~~

Hết input khiến `readLine()` thứ hai trả `null`; chương trình thông báo thiếu trường, không tự suy ra `0`. **Exit status 2**.

## 10. Những lỗi thường gặp

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| Tưởng `readLine()` trả số đã chuyển | API trả `String` hoặc `null` | Gọi parse sau khi kiểm tra đầu vào |
| Đọc `null.strip()` khi EOF | `NullPointerException` | Kiểm tra `null` trước |
| Xử lý chuỗi rỗng như số 0 | Dễ tạo dữ liệu giả | Phân biệt thiếu dữ liệu với số 0 thực sự |
| Parse `"abc"` không bắt lỗi | `NumberFormatException` | Catch đúng loại hoặc thông báo lỗi |
| Parse `"2147483648"` bằng `int` rồi coi là số hợp lệ | Ngoài phạm vi `int` | Dùng kiểu phù hợp và kiểm tra miền |
| `int n = Integer.parseInt(s);` rồi không kiểm tra `n` | `n` có thể là 0, âm hoặc quá giới hạn nghiệp vụ | Validate miền sau parse |
| Dùng `double` để cộng tiền và kỳ vọng chính xác thập phân tuyệt đối | Dấu phẩy động có thể làm tròn nhị phân | `BigDecimal` và quy tắc scale/rounding rõ |
| Tưởng `Double.parseDouble("NaN")` là số hữu hạn hợp lệ | NaN là giá trị đặc biệt | `Double.isFinite` nếu hợp đồng cần |
| Tưởng `12,50` luôn parse giống `12.50` | Parser/locale khác nhau | Chọn format rõ và kiểm tra |
| Dùng `==` để so chuỗi nhập | `==` so identity | Dùng `equals` hoặc parse số |
| Trộn `Scanner.nextInt()` với `nextLine()` không xử lý phần cuối dòng | Có thể đọc chuỗi rỗng ngoài ý muốn | Dùng `nextLine()` + parse hoặc tiêu thụ hết dòng đúng cách |
| Dùng `assert` để validate input bắt buộc | Assertion có thể bị tắt | Dùng `if`/throw/validation luôn chạy |
| Nuốt `NumberFormatException` rồi tự động dùng 0 | Che giấu dữ liệu sai | Thông báo hoặc yêu cầu nhập lại theo hợp đồng |
| Nhầm lỗi cú pháp với lỗi miền | Hai vấn đề cần xử lý riêng | Parse trước, validate sau |

## 11. Bài tập và đáp án

**A.** Với `String s = " 18 ";`, cách chuyển thành `int` nếu cho phép khoảng trắng hai đầu?

**Đáp án:**

~~~java
int n = Integer.parseInt(s.strip()); // 18
~~~

**B.** `Integer.parseInt("2.5")` trả gì?

**Đáp án:** ném `NumberFormatException` vì `2.5` không phải biểu diễn một số nguyên `int` hợp lệ.

**C.** Nếu quantity = `0` nhưng `Integer.parseInt("0")` thành công thì dữ liệu hợp lệ chưa?

**Đáp án:** **Chưa**. Hợp đồng ví dụ yêu cầu `1..100`, nên số lượng 0 phải bị từ chối bởi validation.

**D.** Nếu muốn dữ liệu thập phân chính xác để tính giá tiền, dùng `new BigDecimal("12.50")` hay `new BigDecimal(12.50)`?

**Đáp án:** ưu tiên `new BigDecimal("12.50")` khi đầu vào là chuỗi số thập phân đã được kiểm tra; tránh tạo từ `double` vì độ chính xác nhị phân.

**E.** `BufferedReader.readLine()` trả `null` khi nào?

**Đáp án:** khi đạt EOF trước khi đọc một dòng, khác với `""` khi đọc dòng rỗng.

**F.** Vì sao `Double.parseDouble("NaN")` chưa đủ để xác thực một phép đo bắt buộc hữu hạn?

**Đáp án:** `NaN` là giá trị `double` đặc biệt; kiểm tra `Double.isFinite` và miền giá trị thích hợp.

**G.** `12,50` bị ví dụ từ chối do lý do nào?

**Đáp án:** lỗi **định dạng**: hợp đồng cho đơn giá yêu cầu chữ số ASCII và dấu thập phân `.`, không phải dấu phẩy.

**H.** Với `2` và `12.50`, tổng tiền theo ví dụ là bao nhiêu?

**Đáp án:** `25.00` vì `2 × 12.50 = 25.00`.

## 12. Checklist và kết luận

- [ ] Biết input từ `readLine()`/`nextLine()` là chuỗi cần chuyển đổi.
- [ ] Phân biệt EOF (`null`), chuỗi rỗng và chuỗi toàn khoảng trắng.
- [ ] Kiểm tra `null` trước khi chuẩn hóa `strip()`.
- [ ] Sử dụng `Integer.parseInt`, `Long.parseLong` cho số nguyên.
- [ ] Hiểu `NumberFormatException` và giới hạn phạm vi kiểu số.
- [ ] Biết `Double.parseDouble` và kiểm tra giá trị hữu hạn khi cần.
- [ ] Biết dùng `BigDecimal` từ `String` để xử lý thập phân chính xác.
- [ ] Chọn quy tắc dấu thập phân, khoảng trắng, số chữ số theo hợp đồng.
- [ ] Kiểm tra miền nghiệp vụ sau bước parse.
- [ ] Không dùng `assert` hoặc giá trị mặc định tùy tiện để thay validation.
- [ ] Hiểu bẫy trộn `Scanner.nextInt()` và `nextLine()`.
- [ ] Đọc và hiểu thông báo lỗi, exit status trong CLI.
- [ ] Chạy `InputNumberValidationDemo.java` trên JDK 17+ cho cả trường hợp hợp lệ và sai, đối chiếu stdout.

**Kết luận:** **Đọc chuỗi, chuyển thành số và kiểm tra dữ liệu hợp lệ** là ba bước khác nhau nhưng liên tiếp. Chương trình Java vững chắc phải xử lý được input thiếu, sai cú pháp hoặc ngoài phạm vi, đồng thời dùng kiểu số phù hợp với nghiệp vụ. Chỉ tính toán sau khi tất cả các bước kiểm tra bắt buộc đều thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-io-009">Locale, dấu phân cách thập phân và trách nhiệm đóng nguồn nhập</a>

<details>
<summary>Xem chi tiết</summary>

# Locale, dấu phân cách thập phân và trách nhiệm đóng nguồn nhập trong Java

## 1. Vì sao nhập số có thể khác nhau giữa các máy?

Khi người dùng nhập một số thực, cách viết dấu phân cách thập phân và dấu phân nhóm hàng nghìn tùy thuộc **quy ước vùng miền** (*locale*). Ví dụ:

| Locale | Số một nghìn hai trăm ba mươi tư phẩy năm | Ý nghĩa |
| --- | --- | --- |
| `Locale.US` | `1,234.50` | Dấu phẩy nhóm hàng nghìn, dấu chấm thập phân |
| `Locale.GERMANY` | `1.234,50` | Dấu chấm nhóm hàng nghìn, dấu phẩy thập phân |

Cả hai cách viết biểu diễn **cùng giá trị số**, nhưng văn bản đầu vào khác nhau. Nếu dùng `java.util.Scanner` để đọc một số, trình phân tích token của `Scanner` có thể chịu ảnh hưởng của locale. Nếu dùng `System.out.printf` hoặc `String.format` để hiển thị số, ký tự thập phân và phân nhóm cũng có thể thay đổi theo locale.

**Hai việc cần tách bạch:**

1. **Đọc/parsing:** chuyển văn bản như `"1.234,50"` thành giá trị `double`.
2. **Hiển thị/formatting:** chuyển giá trị `1234.5` thành văn bản theo quy ước cần dùng.

Không nên coi dấu phẩy và dấu chấm là những ký tự có ý nghĩa giống nhau trong mọi locale. Đồng thời không nên mặc định thiết lập locale trên máy học của mình sẽ giống hệ thống chấm bài hay máy người dùng.

## 2. `Scanner` đọc số theo locale

`Scanner` là lớp trong package `java.util`. Nó đọc đầu vào theo token và có các phương thức như `nextInt()`, `nextDouble()`, `next()` và `nextLine()`.

```java
import java.util.Locale;
import java.util.Scanner;

// Mã trong phương thức:
try (Scanner scanner = new Scanner("12.75")) {
    scanner.useLocale(Locale.US);
    double value = scanner.nextDouble(); // 12.75
}
```

- `scanner.useLocale(Locale.US)` đặt locale dùng khi `Scanner` phân tích số.
- `nextDouble()` lấy token kế tiếp và phân tích thành giá trị `double` theo locale đã chọn.
- `nextInt()` cũng dùng các quy tắc số nguyên liên quan đến locale và radix hiện tại.
- `next()` chỉ đọc một token dạng `String`, không tự chuyển thành số.
- `nextLine()` đọc phần còn lại của dòng thành `String`, không tự phân tích nội dung dòng thành số.

Nếu không gọi `useLocale`, `Scanner` dùng locale mặc định phù hợp của môi trường khi khởi tạo. Không nên viết test phụ thuộc vào điều đó. Trong ví dụ có kết quả dự kiến chính xác, hãy **chỉ rõ locale**.

### Cùng giá trị, hai cách nhập

```java
try (Scanner us = new Scanner("1,234.50");
     Scanner de = new Scanner("1.234,50")) {
    us.useLocale(Locale.US);
    de.useLocale(Locale.GERMANY);

    double a = us.nextDouble(); // 1234.5
    double b = de.nextDouble(); // 1234.5
}
```

Các chuỗi được dùng ở đây có định dạng nhóm nghìn/thập phân đúng theo hai locale đã chọn. Kết quả trong biến `a` và `b` là **giá trị số**, không còn mang theo cách viết dấu phẩy hay dấu chấm ở nguồn ban đầu.

Nếu token không hợp lệ theo locale hoặc nằm ngoài các dạng mà `Scanner` chấp nhận, `nextDouble()` có thể ném `InputMismatchException`. Nếu nguồn đã hết, có thể gặp `NoSuchElementException`; nếu `Scanner` đã đóng, thao tác đọc có thể ném `IllegalStateException`.

## 3. Kiểm tra với `hasNextDouble()` và xử lý dữ liệu sai

`hasNextDouble()` kiểm tra token kế tiếp có thể được `Scanner` phân tích thành `double` theo các quy tắc hiện tại hay không. Nó **không tiêu thụ** token.

```java
import java.util.Locale;
import java.util.Scanner;

// Đặt trong phương thức:
try (Scanner scanner = new Scanner("abc 12.5")) {
    scanner.useLocale(Locale.US);

    if (!scanner.hasNextDouble() && scanner.hasNext()) {
        String invalid = scanner.next(); // Tiêu thụ "abc"
        System.out.println("Invalid: " + invalid);
    }

    if (scanner.hasNextDouble()) {
        double number = scanner.nextDouble(); // 12.5
        System.out.println("Number: " + number);
    }
}
```

Điểm quan trọng: nếu `hasNextDouble()` trả `false` mà vòng lặp cứ kiểm tra cùng token, chương trình có thể lặp mãi. Cần **loại bỏ token không hợp lệ**, yêu cầu nhập lại hoặc dừng theo chính sách lỗi đã chọn.

`hasNextDouble()` kiểm tra **cú pháp đọc được thành số thực**, không kiểm tra điều kiện nghiệp vụ như “giá trị phải lớn hơn 0” hoặc “phải là số hữu hạn”. Nếu dữ liệu số cần hữu hạn, kiểm tra thêm `Double.isFinite(value)`. Không phải mọi giá trị kiểu `double` đều phù hợp với miền dữ liệu ứng dụng.

Đừng dùng `catch (Exception e) { }` rỗng để che dữ liệu nhập sai. Với chương trình console, thông báo lỗi rõ ràng và vòng lặp nhập lại thường có ích hơn.

## 4. `Double.parseDouble()` không phân tích theo locale

`Double.parseDouble(String)` xử lý các biểu diễn số thực theo cú pháp được Java quy định, **không dùng locale mặc định của người dùng** để diễn giải dấu phân cách thập phân. Ví dụ:

```java
double a = Double.parseDouble("12.5"); // 12.5

// Double.parseDouble("12,5");
// Ném NumberFormatException: dấu phẩy không được hiểu là dấu thập phân.
```

Nếu muốn đọc số thực từ một **dòng văn bản cố định** với cú pháp dấu chấm thập phân, có thể dùng `Scanner.nextLine()`, sau đó `Double.parseDouble(text.trim())`. Cách này tránh vấn đề trộn `nextDouble()` với `nextLine()` và giúp cú pháp nhập rõ ràng.

Ngược lại, nếu muốn chấp nhận văn bản theo định dạng địa phương như `"1.234,50"`, dùng `Scanner` với `Locale.GERMANY` hoặc một parser phù hợp như `NumberFormat` với locale và quy tắc kiểm tra dữ liệu mong muốn. Không nên thay mọi dấu phẩy bằng dấu chấm một cách máy móc: trong một số locale, dấu phẩy là dấu thập phân, trong locale khác lại là dấu nhóm nghìn. Việc thay thế không đúng có thể làm **đổi giá trị** của số.

| API | Có áp dụng locale khi đọc số không? | Ví dụ |
| --- | --- | --- |
| `Scanner.useLocale(...)` + `nextDouble()` | Có | `"1.234,50"` với `Locale.GERMANY` |
| `Scanner.useLocale(...)` + `nextInt()` | Có, theo quy tắc số nguyên/radix | `"123"` với `Locale.US` |
| `Double.parseDouble("12.5")` | Không | Nhận dấu chấm theo cú pháp Java |
| `Integer.parseInt("123")` | Không | Nhận cú pháp số nguyên Java, không phải parser nhóm nghìn theo locale |
| `Scanner.nextLine()` | Không tự phân tích số | Đọc dòng văn bản, sau đó tự chọn quy tắc parse |

## 5. Locale cũng ảnh hưởng đến `printf()`

Khi in số thực, `System.out.printf` có overload nhận `Locale`:

```java
double price = 1234.5;

System.out.printf(Locale.US, "US: %,.2f%n", price);
System.out.printf(Locale.GERMANY, "DE: %,.2f%n", price);
```

Đoạn này cần `import java.util.Locale;` và đặt trong phương thức. Stdout tương ứng:

```text
US: 1,234.50
DE: 1.234,50
```

Trong `%,.2f`:

- `%f` định dạng số thực ở dạng thập phân.
- `.2` chọn hai chữ số sau dấu phân cách thập phân.
- Dấu `,` ngay trong **mẫu định dạng** bật tính năng nhóm chữ số; dấu nhóm thực tế trong kết quả phụ thuộc locale.
- `%n` tạo ký tự kết thúc dòng theo nền tảng.

Nếu bỏ đối số `Locale`, cách hiển thị có thể thay đổi theo locale mặc định của môi trường. `String.format(Locale.US, ...)` cũng tạo một chuỗi định dạng theo quy tắc tương ứng nhưng **không tự in** ra console.

**Phân biệt hai loại dấu phẩy:** dấu `,` trong chuỗi format `"%,.2f"` là ký hiệu **bật grouping**, không phải cam kết luôn in dấu phẩy ra kết quả. Với `Locale.GERMANY`, dấu grouping hiển thị là dấu chấm, còn dấu thập phân là dấu phẩy.

## 6. `Scanner.close()` đóng cái gì?

`Scanner` triển khai `java.io.Closeable` và cung cấp phương thức `close()`. Khi đóng, nó đánh dấu scanner đã đóng; nếu nguồn đầu vào bên dưới triển khai `Closeable`, scanner **cũng gọi `close()` trên nguồn đó**.

Vì thế, phải hỏi một câu quan trọng: **mã này có sở hữu nguồn nhập hay không?**

### Nguồn do chương trình tự mở: nên đóng

Ví dụ dùng `Scanner` để đọc một tệp do chính phương thức mở:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

class FileReaderExample {
    static void showFirstToken(Path file) throws IOException {
        try (Scanner scanner =
                new Scanner(file, StandardCharsets.UTF_8)) {
            if (scanner.hasNext()) {
                System.out.println(scanner.next());
            }
        } // Scanner được tự đóng tại đây.
    }
}
```

Đây là **lớp minh họa** không có `main`. Với constructor `Scanner(Path, Charset)` trên Java 17, ta có thể chỉ rõ charset cho tệp. `try-with-resources` tự gọi `close()` khi rời khối, kể cả khi có `return` hoặc ngoại lệ phát sinh trong thân khối.

Đóng đúng lúc đặc biệt quan trọng với những nguồn như file hoặc stream do chương trình sở hữu. Không nên trông chờ Garbage Collector tự giải phóng mọi tài nguyên bên ngoài.

### `Scanner(String)`: phù hợp cho ví dụ/test, có thể đóng tại cuối phạm vi

```java
try (Scanner scanner = new Scanner("Java 17")) {
    System.out.println(scanner.next());
}
```

Nguồn này là một chuỗi đã có trong bộ nhớ; ví dụ không mở một tệp hay đóng `System.in`. Đóng scanner vẫn đúng với nguyên tắc không dùng lại một đối tượng đọc sau khi kết thúc phạm vi.

### `Scanner(System.in)`: chú ý nguồn chung

`System.in` là **luồng nhập chuẩn dùng chung của tiến trình Java**. Khi viết:

```java
Scanner console = new Scanner(System.in);
```

scanner sử dụng `System.in` làm nguồn. Nếu gọi `console.close()`, `Scanner` cũng có thể đóng `System.in` bên dưới. Điều đó ảnh hưởng những phần khác của chương trình muốn tiếp tục đọc từ `System.in`. **Không nên đóng tùy tiện một scanner bọc `System.in` khi nguồn nhập còn được các thành phần khác dùng.**

Trong ứng dụng console đơn giản, có thể tạo **một scanner dùng chung** và giữ nó tới khi toàn bộ quá trình nhập kết thúc, thay vì tạo và đóng nhiều scanner trên `System.in`. Ở ranh giới kết thúc ứng dụng, chiến lược đóng tài nguyên phụ thuộc **ai được giao quyền sở hữu** luồng. Nếu phương thức chỉ **mượn** scanner do nơi khác cung cấp, phương thức ấy thường không nên tự đóng nó.

Không nên hiểu sai rằng `Scanner` không bao giờ phải đóng: quy tắc chính là **người sở hữu nguồn quyết định vòng đời**, chứ không phải “luôn đóng” hoặc “không bao giờ đóng” mọi scanner.

## 7. Chương trình Java 17+ chạy độc lập, không phụ thuộc locale máy

Tạo tệp **`LocaleInputDemo.java`**:

```java
import java.io.StringReader;
import java.util.Locale;
import java.util.Scanner;

public class LocaleInputDemo {
    static double read(String text, Locale locale) {
        try (Scanner scanner = new Scanner(text)) {
            scanner.useLocale(locale);
            if (!scanner.hasNextDouble()) {
                throw new IllegalArgumentException("invalid number");
            }
            double result = scanner.nextDouble();
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("non-finite number");
            }
            return result;
        }
    }

    static class TrackedReader extends StringReader {
        boolean closed;

        TrackedReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            super.close();
            closed = true;
        }
    }

    public static void main(String[] args) {
        double us = read("1,234.50", Locale.US);
        double de = read("1.234,50", Locale.GERMANY);

        System.out.printf(Locale.US, "US input: %.2f%n", us);
        System.out.printf(Locale.US, "DE input: %.2f%n", de);

        System.out.printf(Locale.US, "US output: %,.2f%n", us);
        System.out.printf(Locale.GERMANY, "DE output: %,.2f%n", de);

        double plain = Double.parseDouble("12.5");
        System.out.println("parseDouble: " + plain);

        try {
            Double.parseDouble("12,5");
        } catch (NumberFormatException ex) {
            System.out.println("parseDouble rejects comma");
        }

        try (Scanner scanner = new Scanner("abc 7.5")) {
            scanner.useLocale(Locale.US);
            if (!scanner.hasNextDouble() && scanner.hasNext()) {
                System.out.println("bad token: " + scanner.next());
            }
            System.out.printf(Locale.US, "next: %.1f%n",
                    scanner.nextDouble());
        }

        TrackedReader source = new TrackedReader("hello");
        try (Scanner scanner = new Scanner(source)) {
            System.out.println("owned input: " + scanner.next());
        }
        System.out.println("source closed: " + source.closed);
    }
}
```

Chạy tại thư mục có tệp với **JDK 17 trở lên**. Các lệnh hoạt động trên PowerShell/CMD (Windows) và bash/zsh (Linux/macOS):

```bash
javac -encoding UTF-8 LocaleInputDemo.java
java LocaleInputDemo
```

**Stdout chính xác** (lệnh biên dịch thành công không in gì):

```text
US input: 1234.50
DE input: 1234.50
US output: 1,234.50
DE output: 1.234,50
parseDouble: 12.5
parseDouble rejects comma
bad token: abc
next: 7.5
owned input: hello
source closed: true
```

### Giải thích ví dụ

1. Hai hàm `read` sử dụng `Scanner.useLocale` riêng cho từng chuỗi, nên `"1,234.50"` và `"1.234,50"` cùng được đọc thành giá trị `1234.5`.
2. Khi định dạng bằng `Locale.US`, giá trị hiển thị là `1,234.50`; khi dùng `Locale.GERMANY`, là `1.234,50`. Giá trị số **không thay đổi**, chỉ cách hiển thị khác.
3. `Double.parseDouble("12.5")` hợp lệ, còn `Double.parseDouble("12,5")` ném `NumberFormatException`, vì API đó không tự dùng dấu phẩy theo locale.
4. `hasNextDouble()` phát hiện token đầu `abc` không hợp lệ, `next()` loại bỏ nó rồi `nextDouble()` đọc `7.5`.
5. `TrackedReader` là một `StringReader` tự định nghĩa để theo dõi trạng thái đóng nguồn. Khi rời `try (Scanner ...)`, scanner đóng nguồn `source` và cờ `closed` thành `true`.

Bằng cách dùng các chuỗi đầu vào và chỉ định `Locale`, chương trình **không cần người dùng nhập tay** và không phụ thuộc locale mặc định của máy đang chạy.

## 8. Mẫu đọc bàn phím: quyết định nơi đóng `Scanner`

Ví dụ nhập một dòng số thực bằng dấu chấm thập phân, đồng thời có thông báo lỗi:

```java
import java.util.Scanner;

public class KeyboardNumber {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.print("Enter number with a dot: ");

        if (input.hasNextLine()) {
            String text = input.nextLine().trim();
            try {
                double value = Double.parseDouble(text);
                if (!Double.isFinite(value)) {
                    System.err.println("Number must be finite.");
                } else {
                    System.out.println("Value: " + value);
                }
            } catch (NumberFormatException ex) {
                System.err.println("Invalid number.");
            }
        } else {
            System.err.println("No input.");
        }

        // Không đóng input ở đây: ví dụ xem System.in là nguồn dùng chung.
    }
}
```

Tệp tên **`KeyboardNumber.java`**, chạy:

```bash
javac -encoding UTF-8 KeyboardNumber.java
java KeyboardNumber
```

Đây là **chương trình có tương tác**: người dùng nhập dữ liệu rồi nhấn Enter; kết quả phụ thuộc chuỗi được nhập. Ví dụ nếu nhập `12.5`, stdout sau prompt chứa `Value: 12.5`. Vì `Double.parseDouble()` dùng cú pháp độc lập locale, ví dụ **chủ động quy định** nhập dấu chấm. Nếu yêu cầu của ứng dụng là nhập số theo locale người dùng, thay đổi cơ chế parse cho phù hợp thay vì ghi lời hướng dẫn mâu thuẫn.

Lưu ý về trình kiểm tra resource leak: với mã mượn `System.in`, một số công cụ phân tích tĩnh có thể nhắc scanner chưa được đóng. Không nên giải quyết bằng cách **đóng luôn nguồn chuẩn dùng chung** nếu yêu cầu chương trình là tiếp tục dùng nguồn đó ở nơi khác; thay vào đó, xác định ranh giới sở hữu và vòng đời rõ ràng.

## 9. Những lỗi thường gặp

| Lỗi hoặc giả định | Giải thích/cách sửa |
| --- | --- |
| `Scanner.nextDouble()` đọc sai ở máy có locale khác | Chỉ định `useLocale(Locale.US)` hoặc locale phù hợp định dạng dữ liệu |
| Nghĩ `Double.parseDouble("1,5")` đọc được số thực theo locale dùng dấu phẩy | Không; `parseDouble` không dùng locale; dùng parser phù hợp hoặc thay đổi cú pháp đầu vào có kiểm soát |
| Chỉ đổi dấu phẩy thành dấu chấm trên mọi chuỗi số | Có thể thay đổi ý nghĩa dấu nhóm/thập phân, nhất là với giá trị như `1,234` |
| Quên locale khi viết `printf("%.2f", value)` trong bài có stdout cần chính xác | Chỉ định `printf(Locale.US, ...)` khi cần kết quả theo dấu chấm |
| Tưởng `hasNextDouble()` đã đọc mất token | Chưa; nó chỉ kiểm tra, phải gọi `nextDouble()` hoặc tiêu thụ token theo logic |
| Bắt ngoại lệ rồi tiếp tục thử lại trên cùng token sai mà không loại bỏ | Vòng lặp có thể gặp lại lỗi; dùng `next()` hoặc đọc lại dòng với quy tắc cụ thể |
| Luôn đóng mọi `Scanner(System.in)` ngay sau một phương thức | Có thể đóng luôn `System.in` mà phần khác còn cần |
| Không bao giờ đóng `Scanner` đọc từ file | Làm tài nguyên file có thể còn mở; dùng `try-with-resources` với nguồn mình sở hữu |
| Tạo nhiều scanner độc lập trên cùng `System.in` | Dễ dẫn đến khó kiểm soát buffer và vòng đời; dùng một scanner có phạm vi dùng chung hợp lý |
| Nhầm đóng scanner với việc dọn bộ nhớ của GC | `close()` xử lý vòng đời tài nguyên, không chỉ việc thu gom đối tượng Java |

## 10. Checklist trước khi xây dựng luồng nhập

1. **Định dạng dữ liệu đến từ đâu?** Nếu là giao thức/file quy định dấu chấm thì parser phải theo cú pháp đó; nếu là nhập liệu người dùng theo locale, chọn parser theo locale.
2. **Đọc token hay đọc dòng?** Khi dùng `nextDouble()` rồi `nextLine()`, nhớ xử lý ký tự kết thúc dòng còn lại. Nếu dữ liệu phân tách theo dòng, cân nhắc dùng `nextLine()` nhất quán rồi parse.
3. **Cần số hợp lệ tới mức nào?** Sau khi parse, kiểm tra phạm vi, điều kiện dương/không âm, `Double.isFinite` theo miền nghiệp vụ.
4. **Ai sở hữu nguồn?** Tệp/stream tự mở thì đóng đúng lúc, ưu tiên `try-with-resources`; nguồn chuẩn `System.in` dùng chung thì không tự đóng giữa chừng.
5. **Output có cần tái lập giống nhau?** Dùng `printf(Locale.US, ...)` hoặc locale được chỉ định thay vì phụ thuộc thiết lập máy.

**Ghi nhớ:** `Locale` quyết định cách nhiều API như `Scanner.nextDouble()` nhận diện dấu phân cách thập phân và cách `printf()` trình bày số. `Double.parseDouble()` lại dùng cú pháp không phụ thuộc locale. Với `Scanner.close()`, hãy quản lý theo **quyền sở hữu nguồn nhập**: đóng các nguồn mình tạo và không vô tình đóng `System.in` khi chương trình còn cần nó.

</details>

- [Quay lại đầu trang](#back-to-top)
