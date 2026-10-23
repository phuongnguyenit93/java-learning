<a id="back-to-top"></a>

# Lỗi và ngoại lệ

## Menu
- [Lỗi biên dịch, lỗi khi chạy và lỗi logic](#errors-and-exceptions-001)
- [Đọc thông báo javac và sửa từ lỗi gốc](#errors-and-exceptions-002)
- [Đọc stack trace và tìm vị trí phát sinh lỗi](#errors-and-exceptions-003)
- [try-catch để xử lý tình huống có thể thất bại](#errors-and-exceptions-004)
- [throw để báo lỗi và throws để khai báo khả năng phát sinh](#errors-and-exceptions-005)
- [Nhận diện checked exception và unchecked exception](#errors-and-exceptions-006)
- [finally và giới thiệu try-with-resources](#errors-and-exceptions-007)
- [assert để kiểm tra giả định nội bộ và bật bằng -ea](#errors-and-exceptions-008)
- [Không dùng assert thay kiểm tra dữ liệu đầu vào bắt buộc](#errors-and-exceptions-009)

## <a id="errors-and-exceptions-001">Lỗi biên dịch, lỗi khi chạy và lỗi logic</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi biên dịch, lỗi khi chạy và lỗi logic trong Java

## 1. Ba loại lỗi khác nhau ở thời điểm và cách phát hiện

Khi viết chương trình Java, không phải mọi lỗi đều được phát hiện ở cùng một giai đoạn.

| Loại | Thời điểm thường phát hiện | Dấu hiệu đặc trưng | Ví dụ |
| --- | --- | --- | --- |
| **Lỗi biên dịch** (*compile-time error*) | Khi `javac` kiểm tra mã nguồn | Biên dịch thất bại; mã nguồn vi phạm quy tắc cú pháp, tên hoặc kiểu của Java | `int x = "abc";` |
| **Lỗi khi chạy** (*runtime error/failure*) | Khi chương trình bắt đầu hoặc đang thực thi | Chương trình có thể ném ngoại lệ, gặp lỗi JVM hoặc kết thúc bất thường | Chia số nguyên cho 0, truy cập mảng vượt chỉ số |
| **Lỗi logic** (*logic error*) | Khi kiểm tra hành vi, chạy test hoặc đối chiếu yêu cầu | Chương trình có thể biên dịch và chạy không báo lỗi nhưng **kết quả sai** | Tính trung bình `a + b / 2` thay vì `(a + b) / 2.0` |

Đây là một cách **phân loại phục vụ học tập**, không có nghĩa mọi lỗi chỉ thuộc một nhóm. Chẳng hạn lỗi logic có thể dẫn tới ngoại lệ khi chạy ở một đầu vào đặc biệt; một lỗi runtime về thư viện thiếu có thể chỉ xuất hiện khi JVM cố tải lớp liên quan.

Quy trình cơ bản với một tệp Java:

```text
Mã nguồn (.java)
    |
    v
javac biên dịch và kiểm tra
    |--- Không hợp lệ ---> Lỗi biên dịch (cần sửa mã)
    |
    v
Mã bytecode (.class)
    |
    v
JVM tải lớp, khởi chạy, thực thi
    |--- Gặp ngoại lệ/lỗi ---> Thất bại khi chạy
    |
    v
Kết quả chương trình
    |--- Sai yêu cầu ----> Lỗi logic (test/đối chiếu)
    |
    v
Kết quả đáp ứng yêu cầu đã kiểm tra
```

Không có thông báo lỗi **không chứng minh** thuật toán đúng. Cần kiểm tra cả đầu ra, trạng thái dữ liệu và các trường hợp biên.

## 2. Lỗi biên dịch: `javac` không chấp nhận mã nguồn

Lỗi biên dịch xảy ra khi chương trình vi phạm các quy tắc mà trình biên dịch Java phải kiểm tra. Các nhóm thường gặp:

1. **Cú pháp:** thiếu dấu `;`, thiếu ngoặc hoặc dùng từ khóa sai vị trí.
2. **Tên và phạm vi:** gọi biến chưa khai báo, dùng biến ngoài scope, gọi phương thức không tồn tại.
3. **Kiểu dữ liệu:** gán `String` cho `int`, truyền sai kiểu đối số, trả về sai kiểu.
4. **Gán chắc chắn trước khi đọc:** biến cục bộ chưa được gán (*definite assignment*).
5. **Ngoại lệ có kiểm tra:** không bắt hoặc khai báo `throws` cho checked exception có thể phát sinh.
6. **Quy tắc lớp/tệp/quyền truy cập:** tên tệp không khớp tên kiểu top-level `public`, truy cập thành viên không được phép.

Ví dụ dưới đây là **các dòng cố tình sai; không gộp vào chương trình chạy ở mục 5**:

```java
// Sai kiểu:
int age = "18"; // incompatible types: String cannot be converted to int

// Sai tên:
System.out.println(missingValue); // cannot find symbol nếu chưa khai báo tên

// Đọc biến cục bộ chưa được gán:
int score;
System.out.println(score); // variable score might not have been initialized

// Sai cú pháp:
// int count = 5 // thiếu dấu chấm phẩy
```

Cách sửa tùy ý định:

```java
int age = 18; // Dùng literal int

int missingValue = 7;
System.out.println(missingValue);

int score = 0;
System.out.println(score);
```

Các đoạn trên minh họa trong thân phương thức, không phải tệp chạy độc lập. Lưu ý: `Integer.parseInt("18")` là một cách chuyển chuỗi thành số nếu dữ liệu thật là văn bản, nhưng nếu nội dung không phải số hợp lệ thì phương thức có thể ném `NumberFormatException` **khi chạy**. Việc chuyển chuỗi sang số vì thế đòi hỏi quy tắc kiểm tra dữ liệu.

### Trường hợp checked exception

Nếu gọi API khai báo checked exception, trình biên dịch buộc mã xử lý ngoại lệ hoặc khai báo để chuyển trách nhiệm cho bên gọi:

```java
import java.nio.file.Files;
import java.nio.file.Path;

class FileExample {
    static String readText() throws java.io.IOException {
        return Files.readString(Path.of("missing.txt"));
    }
}
```

`throws java.io.IOException` khiến ví dụ **biên dịch hợp lệ**. Tuy vậy, nếu tệp không tồn tại, lời gọi `Files.readString` vẫn có thể ném `NoSuchFileException` khi chương trình chạy. **Trình biên dịch kiểm tra cách xử lý ngoại lệ, không bảo đảm môi trường runtime có đủ tệp hoặc mạng hoạt động.**

### Cách đọc lỗi biên dịch

Chạy `javac -encoding UTF-8 Example.java` rồi đọc chẩn đoán trên luồng lỗi (stderr). Thông tin thường gồm tên tệp, số dòng, vị trí và diễn giải như `cannot find symbol`, `incompatible types` hoặc `';' expected`. Nội dung chẩn đoán có thể khác theo phiên bản JDK, nên không nên coi mọi thông báo lỗi biên dịch là chuỗi stdout cố định. Sửa **lỗi đầu tiên có ý nghĩa** rồi biên dịch lại: một dấu ngoặc hoặc khai báo sai có thể dẫn đến nhiều lỗi phụ ở những dòng sau.

## 3. Lỗi khi chạy: chương trình biên dịch được nhưng không thực thi như mong đợi

Nhiều lỗi chỉ xuất hiện khi JVM gặp một **giá trị cụ thể** hoặc **điều kiện môi trường cụ thể**.

| Tình huống | Hành vi Java thường gặp |
| --- | --- |
| `12 / 0` với phép chia nguyên | `ArithmeticException` khi thực thi phép chia |
| Đọc `arr[arr.length]` | `ArrayIndexOutOfBoundsException` |
| Gọi phương thức qua tham chiếu `null` | `NullPointerException` |
| `Integer.parseInt("abc")` | `NumberFormatException` |
| Mở tệp không tồn tại | Thường là `NoSuchFileException` khi dùng `Files.readString` |
| Ép một đối tượng sang kiểu tham chiếu không tương thích | `ClassCastException` |
| Hết bộ nhớ có thể cấp phát | Có thể ném `OutOfMemoryError` |

**Ngoại lệ** (*exception*) là đối tượng mô tả một tình trạng bất thường trong lúc thực thi; nhiều ngoại lệ là các kiểu con của `Exception` hoặc `RuntimeException`. Các lỗi nghiêm trọng hơn của JVM thường được biểu diễn bởi các kiểu con của `Error`, ví dụ `OutOfMemoryError`. Cả hai nhánh này đều thuộc `Throwable`, nhưng **không nên bắt `Error` để tiếp tục hoạt động bình thường** như thể đó là dữ liệu đầu vào sai đơn giản.

Phân biệt **checked exception** và **unchecked exception**:

- `IOException` là checked exception: trình biên dịch thường yêu cầu `try/catch` hoặc `throws`.
- `ArithmeticException`, `NullPointerException`, `NumberFormatException` là unchecked exceptions thuộc `RuntimeException`: không phải khai báo `throws` mới biên dịch được, nhưng vẫn có thể phát sinh khi chạy.
- `Error` và các lớp con cũng là unchecked theo cơ chế kiểm tra ngoại lệ, nhưng thường biểu diễn tình huống nặng hơn và không phải loại lỗi nghiệp vụ để xử lý bằng `catch` thông thường.

Một số thất bại lúc khởi chạy không nhất thiết là lỗi ném ra từ thân `main`. Ví dụ gọi `java MissingClass` trong khi không có lớp phù hợp trên classpath có thể khiến trình khởi chạy báo không tìm thấy hoặc không tải được lớp chính. Khi chẩn đoán phải phân biệt vấn đề **biên dịch mã** với **cấu hình classpath/module path lúc chạy**.

### Khi ngoại lệ không được bắt

Khi một exception không được xử lý ở đường gọi hiện tại, nó lan ngược qua các lời gọi. Nếu đi đến luồng thực thi mà không được xử lý, JVM thường ghi thông tin ngoại lệ và **stack trace** lên stderr. Stack trace giúp xác định kiểu ngoại lệ, nơi ném và chuỗi phương thức gọi. Đừng nuốt ngoại lệ bằng khối `catch` rỗng: việc đó có thể che mất nguyên nhân.

Việc dùng `try/catch` **không sửa sai logic**; nó chỉ xác định cách xử lý một tình huống bất thường. Ví dụ truy cập sai chỉ số nên sửa điều kiện vòng lặp, không dùng `catch (ArrayIndexOutOfBoundsException)` để che lỗi lập trình.

## 4. Lỗi logic: mã hợp lệ, chạy được nhưng cho đáp án sai

Đây thường là loại lỗi khó thấy nhất vì chương trình có thể kết thúc bình thường.

### Sai thứ tự ưu tiên toán tử

```java
int a = 8;
int b = 4;
int wrong = a + b / 2;         // 10 do phép chia làm trước
double correct = (a + b) / 2.0; // 6.0
```

`wrong` có giá trị `10`, không phải trung bình `6`. Lỗi không phát sinh ngoại lệ và cũng không bị `javac` từ chối.

### Chia nguyên làm mất phần thập phân

```java
int total = 5;
int count = 2;
double wrong = total / count;   // 2.0: phép chia int thực hiện trước
double correct = total / 2.0;   // 2.5: phép chia kiểu double
```

Dù biến đích là `double`, phép `total / count` vẫn là phép chia nguyên khi hai toán hạng đều có kiểu `int`. Nếu muốn phần lẻ, cần làm cho ít nhất một toán hạng mang kiểu số thực thích hợp.

### Điều kiện biên và điều kiện sai

```java
int[] values = {10, 20, 30};
int sum = 0;
for (int i = 0; i < values.length - 1; i++) {
    sum += values[i];
}
// sum == 30, trong khi tổng đủ ba phần tử phải là 60.
```

Vòng lặp trên chạy hợp lệ nhưng bỏ sót phần tử cuối. Một điều kiện khác `i <= values.length` có thể dẫn tới **runtime exception** vì cố truy cập phần tử ở chỉ số `values.length` (không hợp lệ).

Với `boolean`, câu lệnh `if (enabled = true)` có thể **biên dịch hợp lệ** vì phép gán trả về giá trị `boolean`, nhưng vừa sửa trạng thái `enabled` vừa làm điều kiện thành `true`. Muốn kiểm tra giá trị, dùng `if (enabled)` hoặc phép so sánh phù hợp.

Lỗi logic còn có thể đến từ hiểu nhầm yêu cầu (ví dụ “ít nhất 18 tuổi” lại dùng `age > 18`), sai đơn vị đo, tràn số nguyên, sắp xếp sai chiều hoặc giả định dữ liệu luôn không rỗng.

## 5. Chương trình Java 17+ độc lập để quan sát hai nhóm lỗi khi chạy và lỗi logic

Tạo một tệp **`ErrorKindsDemo.java`**:

```java
public class ErrorKindsDemo {
    static int wrongAverage(int a, int b) {
        return a + b / 2; // Sai logic: không đặt ngoặc
    }

    static double correctAverage(int a, int b) {
        return (a + b) / 2.0;
    }

    public static void main(String[] args) {
        int a = 8;
        int b = 4;

        int wrong = wrongAverage(a, b);
        double correct = correctAverage(a, b);
        System.out.println("Trung binh sai: " + wrong);
        System.out.println("Trung binh dung: " + correct);

        int divisor = 0;
        try {
            int quotient = 12 / divisor;
            System.out.println(quotient);
        } catch (ArithmeticException ex) {
            System.out.println("Loi chia: " + ex.getClass().getSimpleName());
        }

        int[] values = {3, 5};
        try {
            System.out.println(values[2]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("Loi chi so: " + ex.getClass().getSimpleName());
        }

        try {
            int parsed = Integer.parseInt("abc");
            System.out.println(parsed);
        } catch (NumberFormatException ex) {
            System.out.println("Loi chuyen so: " + ex.getClass().getSimpleName());
        }

        int total = 5;
        int count = 2;
        System.out.println("Chia nguyen sai: " + (double) (total / count));
        System.out.println("Chia thuc dung: " + (total / (double) count));

        System.out.println("Chuong trinh tiep tuc.");
    }
}
```

Mở terminal trong thư mục chứa file, dùng **JDK 17 trở lên**. Trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 ErrorKindsDemo.java
java ErrorKindsDemo
```

**Stdout chính xác** (biên dịch thành công không in thông báo):

```text
Trung binh sai: 10
Trung binh dung: 6.0
Loi chia: ArithmeticException
Loi chi so: ArrayIndexOutOfBoundsException
Loi chuyen so: NumberFormatException
Chia nguyen sai: 2.0
Chia thuc dung: 2.5
Chuong trinh tiep tuc.
```

Đoạn này **cố ý không đưa mã có lỗi biên dịch vào file**, để chương trình có thể chạy độc lập. Ba ngoại lệ ở giữa được bắt ngay trong `main` nên không in stack trace mặc định, và chương trình tiếp tục. Nếu bỏ một khối `catch`, ngoại lệ tương ứng có thể làm đường thực thi `main` kết thúc sớm. Hai phép tính trung bình và chia nguyên minh họa lỗi logic, vì JVM không thể tự biết kết quả nghiệp vụ nào là đúng.

Không nên đánh giá mã này là “không có lỗi” chỉ vì stdout xuất hiện bình thường: mục đích ví dụ là quan sát sự khác nhau giữa **kết quả sai im lặng** và **ngoại lệ khi chạy đã được bắt**.

## 6. Quy trình xác định và xử lý lỗi

### Bước 1 — Xác định giai đoạn

- **`javac` thất bại:** tập trung lỗi cú pháp, tên, kiểu, quyền truy cập, biến chưa khởi tạo hoặc checked exception chưa xử lý.
- **Biên dịch được nhưng chạy báo lỗi/ngoại lệ:** đọc stack trace, dòng lỗi, điều kiện đầu vào và trạng thái môi trường.
- **Chạy xong nhưng output sai:** tái hiện đầu vào, tính tay kết quả mong đợi và kiểm tra các bước thuật toán.

### Bước 2 — Thu nhỏ ví dụ

Tách đoạn gây lỗi thành chương trình nhỏ nhất có thể tái hiện vấn đề. Dùng các giá trị đầu vào đơn giản và kết quả dễ kiểm tra. Trong Java, thêm `System.out.println` để quan sát trạng thái khi học, hoặc dùng debugger của IDE để đặt breakpoint, xem biến và đi từng dòng.

### Bước 3 — Kiểm thử cả biên và trường hợp bất thường

Các trường hợp nên thử phụ thuộc bài toán: `0`, số âm, mảng rỗng, mảng một phần tử, `null`, chuỗi không hợp lệ, giá trị rất lớn, tài nguyên không tồn tại. Với phương thức tính trung bình, nên kiểm tra `(8,4)`, `(5,2)`, hai số âm và các giá trị có thể gây tràn khi cộng nếu dùng `int`.

### Bước 4 — Viết test có kết quả kỳ vọng cụ thể

Ví dụ, một phép kiểm thử thủ công trong `main`:

```java
double actual = (8 + 4) / 2.0;
double expected = 6.0;
if (Double.compare(actual, expected) != 0) {
    throw new AssertionError("Average mismatch");
}
```

Nếu phép tính bị đổi thành `8 + 4 / 2.0`, điều kiện sẽ phát hiện kết quả `10.0` không khớp `6.0`. Với tính toán số thực tổng quát, thường nên so sánh theo **dung sai** phù hợp thay vì chỉ so sánh `double` tuyệt đối bằng nhau; ví dụ số học đơn giản này cho kết quả biểu diễn chính xác.

Có thể tổ chức test bằng JUnit trong dự án thật. Test không thể chứng minh tuyệt đối không còn lỗi với mọi đầu vào, nhưng giúp phát hiện hồi quy khi sửa mã.

### Bước 5 — Sửa nguyên nhân, không chỉ che triệu chứng

Nếu vòng lặp bỏ sót phần tử, sửa điều kiện; nếu dữ liệu có thể `null`, quyết định có cho phép `null` hay cần kiểm tra/khước từ; nếu chia cho 0, xác định yêu cầu xử lý mẫu số 0. Tránh `catch (Exception ex) { }` rỗng hoặc trả giá trị mặc định tùy tiện, vì làm lỗi khó chẩn đoán hơn.

## 7. Công cụ và thông báo nên biết

| Công cụ/cách làm | Mục đích |
| --- | --- |
| `javac -encoding UTF-8 File.java` | Biên dịch file Java đúng mã hóa nguồn |
| `javac -Xlint:all File.java` | Bật thêm các cảnh báo biên dịch được hỗ trợ |
| `java ClassName` | Thực thi lớp có phương thức `main` phù hợp |
| Breakpoint và debugger | Xem giá trị biến, stack và các bước thực thi |
| Stack trace | Xác định vị trí và chuỗi lời gọi dẫn đến ngoại lệ |
| Unit test | Kiểm tra đầu ra/trạng thái với dữ liệu kỳ vọng |
| Log có ngữ cảnh | Ghi lại thông tin hữu ích mà không che lỗi |

**Cảnh báo biên dịch không giống lỗi biên dịch**: mặc định nhiều cảnh báo vẫn cho phép sinh `.class`. Cũng không thể khẳng định `javac -Xlint:all` sẽ tìm ra mọi lỗi logic. Phân biệt stdout (kết quả chương trình) với stderr (thường chứa chẩn đoán), và lưu ý thông điệp ngoại lệ có thể khác theo phiên bản JDK hoặc môi trường.

## 8. Tổng kết bằng ba câu hỏi

1. **Chưa biên dịch được?** Xem lỗi compile-time và sửa mã theo cú pháp/quy tắc kiểu của Java.
2. **Biên dịch được nhưng khi chạy thất bại?** Xem exception, stack trace, dữ liệu đầu vào, phụ thuộc và môi trường.
3. **Chạy bình thường nhưng kết quả sai?** Xem logic, đặc tả, thứ tự toán tử, vòng lặp, trường hợp biên và kiểm thử.

**Ghi nhớ:** lỗi biên dịch bị chặn trước khi thực thi; lỗi khi chạy xuất hiện trong quá trình khởi chạy/thực thi; lỗi logic có thể im lặng dù chương trình hoàn tất. Một chương trình chất lượng cần **biên dịch đúng, chạy ổn định và đáp ứng kết quả mong đợi**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-002">Đọc thông báo javac và sửa từ lỗi gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thông báo `javac` và sửa từ lỗi gốc (Java 17+)

## 1. Mục tiêu

Khi biên dịch Java bằng `javac`, một lỗi trong mã nguồn đôi khi tạo ra nhiều thông báo lỗi ở những dòng khác nhau. Kỹ năng cần có là **đọc được thông báo, xác định nguyên nhân ban đầu, sửa nguyên nhân ấy, rồi biên dịch lại**; không sửa máy móc mọi dòng được đánh dấu nếu chúng chỉ là hậu quả.

Sau bài này, bạn có thể đọc vị trí lỗi, thông điệp và các chi tiết `symbol`/`location`; phân biệt lỗi cú pháp, định danh, kiểu, truy cập, dependency với ngoại lệ lúc chạy; dùng `javac` để tái hiện và kiểm tra bản sửa trên JDK 17+.

## 2. Một thông báo `javac` cho biết gì?

Ví dụ mã nguồn **có lỗi chủ đích** trong tệp `SumDemo.java`:

```java
public class SumDemo {
    public static void main(String[] args) {
        int left = 3;
        int right = 4;
        int sum = left + right;
        System.out.println(smu);
    }
}
```

Biên dịch bằng:

```sh
javac -encoding UTF-8 SumDemo.java
```

Trình biên dịch sẽ báo không tìm thấy biến `smu` vì chương trình khai báo tên `sum`. Một mẫu thông báo minh họa (dấu cách, dấu `^`, số dòng, từ ngữ chi tiết có thể thay đổi theo JDK):

```text
SumDemo.java:6: error: cannot find symbol
        System.out.println(smu);
                           ^
  symbol:   variable smu
  location: class SumDemo
1 error
```

Các thành phần cần đọc:

| Thành phần | Cách hiểu |
| --- | --- |
| `SumDemo.java:6` | Tệp và dòng liên quan đến vị trí trình biên dịch phát hiện lỗi |
| `error` | Mức độ chẩn đoán: lỗi cản trở biên dịch thành công |
| `cannot find symbol` | Không phân giải được tên trong ngữ cảnh đó |
| Dòng mã và dấu `^` | Vùng/token mà `javac` đang chỉ ra; không bảo đảm đó là chỗ nguyên nhân bắt đầu |
| `symbol: variable smu` | Loại thành viên hoặc định danh đang được tìm |
| `location: class SumDemo` | Ngữ cảnh mà trình biên dịch đang tra cứu |
| `1 error` | Số lỗi được liệt kê ở cuối đợt biên dịch này |

Với lỗi khác, `javac` có thể cung cấp `required`/`found`, chữ ký phương thức, overload ứng viên, lý do không áp dụng được hoặc đường dẫn source khác. Đây là dữ liệu giúp so sánh **mã hiện tại** với **điều compiler yêu cầu**.

Lưu ý: số dòng thường là nơi trình biên dịch *nhận ra* sự cố, không nhất thiết là dòng cần sửa. Chẳng hạn thiếu `;` ở cuối dòng trước có thể khiến token đầu dòng sau bị đánh dấu.

## 3. Quy trình 6 bước sửa từ lỗi gốc

1. **Tái hiện lỗi trên một lệnh biên dịch rõ ràng.** Xác nhận đúng JDK, đúng thư mục và đúng tập tệp được biên dịch. Giữ lại thông báo đầy đủ, không chỉ sao chép một dòng `error`.
2. **Đọc nhóm lỗi đầu tiên trong ngữ cảnh.** Bắt đầu từ lỗi cú pháp hoặc lỗi định danh xuất hiện sớm; xem vài dòng trước và sau vị trí `^`, dấu ngoặc, dấu nháy, tên khai báo, `package` và `import`.
3. **Tìm nguyên nhân chung nhỏ nhất.** Một dấu `}` thiếu có thể khiến nhiều khai báo bị hiểu sai; một tên kiểu chưa biên dịch hoặc một import sai có thể kéo theo nhiều lỗi sử dụng kiểu ấy.
4. **Sửa một nguyên nhân thực sự**, giữ các thay đổi tối thiểu. Tránh đổi hàng loạt tên hoặc thêm ép kiểu/import theo phỏng đoán chỉ để giảm số lượng thông báo.
5. **Chạy lại đúng lệnh `javac`.** Đọc tập thông báo mới; lỗi kéo theo có thể tự biến mất. Nếu còn lỗi độc lập, xử lý lần lượt.
6. **Chạy bằng `java` sau khi biên dịch thành công**, kiểm tra output/hành vi. `javac` biên dịch được không có nghĩa chương trình không gặp ngoại lệ hay đúng logic.

Đây là phương pháp ưu tiên, không phải quy luật rằng *lỗi được in đầu tiên luôn là lỗi gốc*: việc compiler phục hồi sau lỗi cú pháp, thứ tự biên dịch nhiều tệp và lỗi ở dependency có thể làm chẩn đoán xuất hiện tại nơi hậu quả. Hãy dựa vào bằng chứng và thử một bản sửa nhỏ rồi biên dịch lại.

## 4. Sửa ví dụ `cannot find symbol` cho đúng

Trong `SumDemo.java` ở trên, đổi `smu` thành `sum`. Toàn bộ chương trình hợp lệ:

```java
public class SumDemo {
    public static void main(String[] args) {
        int left = 3;
        int right = 4;
        int sum = left + right;
        System.out.println("sum=" + sum);
    }
}
```

Tạo thư mục `out` một lần rồi biên dịch, chạy từ thư mục chứa `SumDemo.java`. Các lệnh sau dùng được trong PowerShell, CMD, bash:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out SumDemo.java
java -cp out SumDemo
```

**Output chính xác của lệnh `java`:**

```text
sum=7
```

- `-encoding UTF-8`: đọc nguồn với mã hóa UTF-8, tránh lỗi khi có ký tự tiếng Việt.
- `--release 17`: biên dịch theo ngôn ngữ và API Java 17 (khi trình biên dịch hỗ trợ bản đích đó). Dùng JDK 17+ để chạy các lệnh trong bài.
- `-d out`: đặt file `.class` vào thư mục đầu ra; thư mục này phải tồn tại trước.
- `-cp out`: thêm thư mục chứa lớp đã biên dịch vào classpath lúc chạy.

`javac` biên dịch thành công thường **không in gì**. Chuỗi `sum=7` là stdout của chương trình, không phải thông báo của compiler. Trong quy trình sửa lỗi, chỉ chạy `java` sau khi lệnh `javac` thành công để tránh vô tình chạy file `.class` cũ.

## 5. Các nhóm lỗi thường gặp và cách tìm gốc

| Dấu hiệu trong diagnostic | Gốc cần kiểm tra | Hướng sửa đúng |
| --- | --- | --- |
| `';' expected`, `')' expected`, `illegal start of expression` | Thiếu dấu `;`, `)`, `}`, cấu trúc cú pháp sai hoặc dấu nháy chưa đóng | Đọc ngược vài dòng, cân cặp dấu ngoặc, sửa cú pháp đầu tiên |
| `reached end of file while parsing` | Thường thiếu dấu đóng `}` hoặc cấu trúc chưa hoàn tất | So sánh các khối `class`, method, `if`, vòng lặp |
| `cannot find symbol` | Tên sai chính tả/chữ hoa-thường, chưa khai báo, ngoài scope, thiếu kiểu/thư viện | Kiểm tra khai báo, phạm vi, tên đầy đủ, package và classpath |
| `package ... does not exist` | Sai `import`, thiếu dependency/source trong lệnh biên dịch, module chưa khả dụng | Kiểm tra package thực, JAR/source, classpath/module path |
| `incompatible types` | Gán/return/truyền tham số không tương thích | So sánh kiểu nguồn và kiểu đích, sửa dữ liệu hoặc API |
| `method ... cannot be applied`, `no suitable method found` | Số lượng, thứ tự, kiểu tham số hoặc overload không phù hợp | Đối chiếu chữ ký phương thức với lời gọi |
| `has private access`, `is not public ... cannot be accessed` | Vi phạm mức truy cập `private`, package-private, `protected` hoặc kiểu không public | Sửa thiết kế API/phạm vi đúng chủ đích, không chỉnh access tùy tiện |
| `class ... is public, should be declared in a file named ...` | Tên file không khớp tên kiểu top-level public | Đổi tên file cho khớp lớp public |
| `duplicate class` hoặc định nghĩa trùng | Nhiều nguồn biên dịch cùng tên kiểu đầy đủ hoặc khai báo trùng | Kiểm tra các file/class và cấu hình build |
| `bad class file`, `class file has wrong version` | Thư viện được biên dịch bằng Java mới hơn phiên bản compiler/runtime đang dùng hoặc file không phù hợp | Kiểm tra JDK thực tế, phiên bản dependency, build output |

Các câu ở cột đầu là **dạng thông báo thường gặp**, không phải chuỗi bảo đảm đúng từng ký tự trên mọi JDK. Đừng xác định cách sửa chỉ bằng từ khóa: đọc cả kiểu, tham số, scope và vị trí.

### 5.1. Thiếu dấu khiến lỗi xuất hiện ở dòng sau

Ví dụ **cố ý sai, không chạy được**:

```java
public class SyntaxDemo {
    public static void main(String[] args) {
        int count = 2
        System.out.println(count);
    }
}
```

Gốc là thiếu `;` sau `2`, dù `javac` có thể trỏ đến phần đầu lệnh `System.out.println` bên dưới. Thêm `;`, không xóa lệnh `println` chỉ vì nó bị đánh dấu.

Phiên bản sửa:

```java
public class SyntaxDemo {
    public static void main(String[] args) {
        int count = 2;
        System.out.println("count=" + count);
    }
}
```

Lưu trong `SyntaxDemo.java`:

```sh
javac -encoding UTF-8 --release 17 -d out SyntaxDemo.java
java -cp out SyntaxDemo
```

Output:

```text
count=2
```

### 5.2. Sai kiểu và lời gọi phương thức

Đoạn **cố ý sai** sau làm phát sinh lỗi kiểu:

```java
String quantity = "5";
int next = quantity + 1; // String + int tạo String, không gán trực tiếp cho int
```

Nếu `quantity` biểu diễn số nguyên hợp lệ, sửa ở nguồn dữ liệu: `int quantity = Integer.parseInt("5"); int next = quantity + 1;`. Nếu mục tiêu là nối chuỗi, biến kết quả phải có kiểu `String`. Cách sửa tùy **ý nghĩa** dữ liệu; ép kiểu bừa bãi không giải quyết được khác biệt giữa số và chuỗi.

Với lời gọi `Math.max(2, "3")`, một đối số `String` không đáp ứng overload dành cho số. Lỗi gốc là truyền sai kiểu, không phải thiếu `import java.lang.Math` (kiểu `Math` đã thuộc `java.lang`). Thay `"3"` bằng số `3` khi muốn tính số lớn hơn.

### 5.3. Mã nguồn nhiều tệp và lỗi dây chuyền

Giả sử `src/lesson/Calculator.java` có khai báo:

```java
package lesson;

public class Calculator {
    public static int doubleOf(int n) {
        return n * 2;
    }
}
```

Và `src/lesson/App.java`:

```java
package lesson;

public class App {
    public static void main(String[] args) {
        System.out.println("value=" + Calculator.doubleOf(3));
    }
}
```

Biên dịch hai tệp vào cùng output, rồi chạy:

```sh
javac -encoding UTF-8 --release 17 -d out src/lesson/Calculator.java src/lesson/App.java
java -cp out lesson.App
```

Output:

```text
value=6
```

Nếu nhầm `package lesson;` thành `package lessons;` ở một tệp, hoặc loại `Calculator.java` khỏi nguồn biên dịch mà không cung cấp lớp tương ứng trên classpath, các lỗi kiểu/định danh trong `App.java` có thể là hậu quả. Kiểm tra **package thực tế, tên đầy đủ của kiểu, nguồn/JAR và cấu hình lệnh** trước khi sửa từng lần sử dụng `Calculator`.

Khi đang sửa dependency, hãy biên dịch lại toàn bộ phần có liên quan để tránh dùng `.class` cũ. Với dự án lớn, để Maven/Gradle quản lý classpath và source set, nhưng vẫn cần đọc lỗi `javac` ở bước compile thật sự.

## 6. Dùng tùy chọn `javac` để thu hẹp chẩn đoán

Một số tùy chọn hữu ích với Java 17+:

```sh
javac -encoding UTF-8 --release 17 -Xdiags:verbose -Xmaxerrs 20 -d out SumDemo.java
```

- `-Xdiags:verbose`: yêu cầu mức diagnostic chi tiết hơn; hữu ích với suy luận generic hoặc overload. Đây là tùy chọn bổ sung của `javac`.
- `-Xmaxerrs 20`: giới hạn số lỗi được in trong một lần gọi; **không** sửa lỗi và có thể ẩn thông báo sau ngưỡng.
- `-Xlint:all`: bật nhiều cảnh báo hữu ích (unchecked, deprecation, raw types, v.v. tùy phiên bản); cảnh báo vẫn khác lỗi.
- `-Werror`: khiến compile thất bại khi có cảnh báo; chỉ bật khi dự án muốn chính sách nghiêm ngặt.
- `javac -version`: xem compiler nào đang chạy; `java -version`: xem runtime. Hai lệnh cần được kiểm tra nếu sử dụng nhiều JDK.

Có thể chạy riêng `javac -X` hoặc `javac --help` để xem các tùy chọn được JDK đang dùng hỗ trợ. Dùng `-Xmaxerrs` lớn hơn để khám phá những lỗi độc lập sau khi sửa lỗi cú pháp gốc, nhưng đừng dựa vào việc tăng số lượng diagnostic như một cách sửa lỗi.

**Kiểm tra trạng thái lệnh biên dịch:** Trong PowerShell, `$LASTEXITCODE` là exit code của lệnh `javac` vừa chạy; `0` biểu thị thành công. Trong bash, chạy `echo $?` ngay sau `javac` để xem exit code. Đừng chạy lệnh khác ở giữa rồi mới kiểm tra.

## 7. Phân biệt compile error, warning và lỗi khi chạy

| Giai đoạn | Dấu hiệu | Nên điều tra |
| --- | --- | --- |
| **Compile error** (`javac`) | `error:`, chương trình không biên dịch thành công | Cú pháp, kiểu, phân giải tên, access, dependency, cấu hình |
| **Warning** (`javac`) | `warning:`, compile có thể vẫn thành công | Hành vi dễ sai, API cũ, generic/raw type, `-Xlint` |
| **Runtime exception/error** (`java`) | `Exception in thread "main"`, stack trace hoặc thông báo launcher | Dữ liệu, điều kiện chạy, call stack, classpath runtime, logic |
| **Sai logic** | Chạy không lỗi nhưng in kết quả sai | Kiểm thử và phân tích thuật toán/dữ liệu, không chỉ đọc `javac` |

Ví dụ **biên dịch hợp lệ nhưng lỗi lúc chạy**:

```java
public class RuntimeDemo {
    public static void main(String[] args) {
        String message = null;
        System.out.println(message.length());
    }
}
```

Biên dịch với `javac -d out RuntimeDemo.java` rồi chạy `java -cp out RuntimeDemo` sẽ phát sinh `NullPointerException`; đây không phải chẩn đoán `javac`. Nội dung chi tiết của stack trace có thể khác giữa các bản JDK, nên không coi văn bản stack trace là output cố định. Lúc này sửa logic null và kiểm tra đầu vào, không tiếp tục thêm import.

Đừng hiểu câu `error: could not find or load main class` của **launcher `java`** là lỗi do parser `javac` phát hiện. Hãy xem tên đầy đủ lớp `main`, đường dẫn `-cp` và các file `.class` được sinh ra.

## 8. Sai lầm phổ biến khi sửa lỗi

- **Chỉ nhìn dấu `^`:** vị trí chỉ là nơi compiler nhận diện triệu chứng; luôn đọc cú pháp trước đó.
- **Sửa hàng loạt lỗi cùng lúc:** dễ đưa thêm lỗi và khó biết thay đổi nào giải quyết nguyên nhân. Ưu tiên thay đổi nhỏ, compile lại.
- **Viết sai tên do Java phân biệt chữ hoa-thường:** `total`, `Total`, `TOTAL` là ba tên khác nhau.
- **Thêm import không liên quan:** `cannot find symbol` cũng xảy ra khi typo tên biến cục bộ hoặc biến ngoài scope; import không thể tạo biến đó.
- **Sửa classpath thay cho lỗi cú pháp, hoặc ngược lại:** chẩn đoán về thư viện phải kiểm tra đúng dependency; diagnostic `';' expected` thường là cú pháp.
- **Quên tên file public class:** `public class SumDemo` phải nằm trong `SumDemo.java` (phân biệt chữ hoa-thường trên hệ thống tệp tương ứng).
- **Chạy file `.class` cũ sau compile thất bại:** chỉ chạy khi bước biên dịch hiện tại thực sự thành công.
- **Ẩn lỗi bằng cách xóa dòng:** chương trình compile được nhưng thiếu chức năng. Kiểm tra lại yêu cầu và output mong muốn.

## 9. Checklist thực hành

Khi có hàng loạt thông báo, hãy tự hỏi:

1. Lệnh `javac`, phiên bản JDK, package, tệp nguồn, đường dẫn nguồn và thư viện đã đúng chưa?
2. Diagnostic đầu tiên cho biết tệp, dòng, loại lỗi, tên nào và ngữ cảnh nào?
3. Có dấu ngoặc, dấu chấm phẩy, tên khai báo hoặc `import` sai ở dòng trước không?
4. Nhiều lỗi có cùng nhắc đến một kiểu/biến/phương thức bị thiếu không? Nếu có, đâu là lỗi khởi nguồn?
5. Có thể sửa **một nguyên nhân** rồi biên dịch lại để kiểm chứng không?
6. Sau khi hết compile error, chương trình đã chạy và cho output đúng chưa?

**Ghi nhớ:** `javac` báo nơi compiler không thể tiếp tục phân tích hoặc kiểm tra mã theo quy tắc Java; bạn cần lần về cấu trúc, khai báo hoặc cấu hình làm phát sinh lỗi đó. Sửa nguyên nhân gốc, biên dịch lại và xác minh bằng chạy thử đáng tin cậy hơn việc đếm số thông báo còn lại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-003">Đọc stack trace và tìm vị trí phát sinh lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc stack trace và tìm vị trí phát sinh lỗi — Java 17+

## 1. Stack trace giúp trả lời điều gì?

Khi một lỗi hoặc ngoại lệ (exception) xảy ra trong Java, **stack trace** ghi lại chuỗi lời gọi phương thức đang hoạt động tại thời điểm lỗi được tạo hoặc khi stack trace được thu thập. Nhìn vào đó, lập trình viên có thể xác định:

- **Loại lỗi:** `NumberFormatException`, `NullPointerException`, `ArrayIndexOutOfBoundsException`, `IllegalStateException`...
- **Thông điệp:** cung cấp thêm ngữ cảnh, có thể chứa đầu vào hoặc mô tả điều kiện sai.
- **Vị trí trong mã:** lớp, phương thức, file `.java` và số dòng khi thông tin có sẵn.
- **Đường đi của lời gọi:** phương thức nào gọi phương thức nào dẫn đến lỗi.
- **Nguyên nhân lồng nhau:** dòng `Caused by:` cho thấy ngoại lệ nguyên nhân khi một ngoại lệ bị bọc trong ngoại lệ khác.

**Mục tiêu:** đọc trace từ lỗi thực tế để tìm đoạn mã cần kiểm tra và **nguyên nhân gốc**, thay vì chỉ xóa một dòng khiến ứng dụng báo lỗi.

Stack trace là **ảnh chụp call stack**, không phải toàn bộ lịch sử hoạt động của chương trình; nó không tự ghi mọi giá trị biến hoặc mọi sự kiện đã diễn ra.

## 2. Cấu trúc một stack trace thường thấy

Một trace minh họa (số dòng trong ví dụ là **minh họa**, thay đổi theo bản mã và bản JDK):

~~~text
Exception in thread "main" java.lang.NumberFormatException: For input string: "bad"
    at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
    at java.base/java.lang.Integer.parseInt(Integer.java:668)
    at StackTraceReadingDemo.parsePart(StackTraceReadingDemo.java:9)
    at StackTraceReadingDemo.calculate(StackTraceReadingDemo.java:13)
    at StackTraceReadingDemo.load(StackTraceReadingDemo.java:18)
    at StackTraceReadingDemo.main(StackTraceReadingDemo.java:31)
~~~

Cách đọc:

| Thành phần | Ý nghĩa |
|---|---|
| `Exception in thread "main"` | Có ngoại lệ chưa được xử lý trên luồng `main` |
| `java.lang.NumberFormatException` | **Kiểu ngoại lệ** (không chuyển đổi được chuỗi sang số) |
| `For input string: "bad"` | Thông điệp giúp xác định đầu vào không hợp lệ |
| `at java.base/java.lang.Integer.parseInt(...)` | Frame thuộc thư viện chuẩn Java trong module `java.base` |
| `at StackTraceReadingDemo.parsePart(...:9)` | Frame của **mã ứng dụng** tại `parsePart`, dòng minh họa 9 |
| `at ...calculate(...)`, `...load(...)`, `...main(...)` | Chuỗi các phương thức gọi dẫn tới `parsePart` |

Dòng `at Class.method(File.java:line)` thường mô tả:

- `Class`: tên lớp, có thể gồm package.
- `method`: tên phương thức/constructor (constructor thường hiện là `<init>`).
- `File.java`: tên file nguồn khi có thông tin.
- `line`: số dòng được gắn với frame khi có debug line information.

Dấu `at` không có nghĩa là mỗi dòng ấy đều **ném một ngoại lệ khác nhau**: chúng thường là những frame trong **cùng một chuỗi lời gọi**.

## 3. Đọc từ trên xuống và tìm frame ứng dụng đầu tiên

Với trace thông thường, các frame liệt kê từ **nơi gần điểm tạo lỗi nhất** xuống những hàm gọi ở phía ngoài:

~~~text
Integer.parseInt -> parsePart -> calculate -> load -> main
         ^              ^            ^         ^       ^
    API thư viện     mã ứng dụng đang dẫn tới lỗi
~~~

Ở ví dụ trên, `Integer.parseInt` trong JDK phát hiện chuỗi không phải số. Nhưng vị trí **mã ứng dụng đầu tiên** cần kiểm tra là lời gọi `Integer.parseInt(...)` bên trong `parsePart`.

Quy trình:

1. Đọc **kiểu ngoại lệ và thông điệp**, chưa vội sửa code.
2. Tìm các frame mang tên package/lớp của **dự án**; mở **file và số dòng** được nêu.
3. Xem câu lệnh ở dòng ấy, đặc biệt đối số truyền vào method có thể gây lỗi.
4. Theo các frame tiếp theo để biết **ai cung cấp dữ liệu** và vì sao chuỗi `"bad"` tới được `parseInt`.
5. Dựng lại đầu vào, thêm kiểm tra/validation hoặc điều chỉnh logic tại nơi phù hợp, rồi chạy lại test.

**Không mặc định frame đầu tiên là dòng cần sửa**: nếu ngoại lệ được ném trong JDK/framework, frame đầu tiên ở thư viện. Cũng không mặc định **frame ứng dụng đầu tiên** luôn là nguyên nhân nghiệp vụ: có thể dữ liệu đã sai từ lớp gọi bên ngoài. Nó là một **điểm bắt đầu để điều tra**.

## 4. Từ file và số dòng quay về mã nguồn

Giả sử một dòng trace có dạng:

~~~text
at com.example.service.UserService.load(UserService.java:42)
~~~

Hãy tìm lớp `com.example.service.UserService`, mở file `UserService.java` và xem **dòng 42** của **đúng phiên bản mã đã biên dịch**. Nếu mã đang mở đã khác bản chạy, số dòng có thể không còn khớp.

Một số trường hợp đặc biệt:

| Dạng trong trace | Điều cần hiểu |
|---|---|
| `File.java:42` | Có thông tin tên file/số dòng |
| `Unknown Source` | Thiếu thông tin nguồn/dòng thích hợp |
| `Native Method` | Frame đi qua phương thức native |
| `Class$$Lambda...` hoặc các frame framework | Có thể do lambda, reflection, proxy hay thư viện trung gian |
| `at ...main(Main.java:...)` | Entry point thường nằm gần đáy chuỗi lời gọi đồng bộ |

Biên dịch bằng `javac` thông thường giữ thông tin dòng hữu ích. Nếu file `.class` được biên dịch/biến đổi mà không còn debug line info, không nên tự khẳng định số dòng. Với `javac -g` có thể yêu cầu thông tin debug đầy đủ hơn; `-g:none` bỏ thông tin debug tương ứng.

Thao tác quan trọng là **đối chiếu trace với artifact/version đang chạy**, không chỉ với bản nguồn mới nhất trên máy.

## 5. Phân biệt nơi phát sinh và nơi bắt lỗi

~~~java
static int parseNumber(String text) {
    return Integer.parseInt(text);
}

static int readValue() {
    return parseNumber("oops");
}

public static void main(String[] args) {
    try {
        System.out.println(readValue());
    } catch (NumberFormatException ex) {
        System.out.println("Cannot parse input");
    }
}
~~~

**Exact stdout** khi đặt các phương thức đúng trong một lớp chạy được:

~~~text
Cannot parse input
~~~

- Ngoại lệ **phát sinh** ở lời gọi `Integer.parseInt("oops")` trong `parseNumber`.
- `readValue` gọi `parseNumber` và không xử lý ngoại lệ.
- `main` **bắt ngoại lệ** nên chương trình có thể tiếp tục.
- Nếu chỉ in `"Cannot parse input"` và **không ghi log cause/stack**, thông tin vị trí lỗi bị thiếu; việc bắt lỗi không làm cho đoạn chuyển đổi tự thành công.

Với trường hợp đầu vào người dùng, bắt `NumberFormatException` và thông báo dữ liệu sai có thể hợp lý. Với bug không mong đợi, cần log đủ bối cảnh để điều tra, không “nuốt” lỗi một cách im lặng.

## 6. `Caused by`: lần theo ngoại lệ gốc trong chuỗi bọc

Một ứng dụng đôi khi bọc ngoại lệ thấp tầng bằng một ngoại lệ có ý nghĩa nghiệp vụ cao hơn:

~~~java
static void loadConfig() {
    try {
        Integer.parseInt("not-a-number");
    } catch (NumberFormatException ex) {
        throw new IllegalStateException("invalid configuration", ex);
    }
}
~~~

Nếu không có `catch` ở lớp ngoài, stack trace thường có hai phần:

~~~text
java.lang.IllegalStateException: invalid configuration
    at StackTraceReadingDemo.loadConfig(StackTraceReadingDemo.java:45)
    at StackTraceReadingDemo.main(StackTraceReadingDemo.java:70)
Caused by: java.lang.NumberFormatException: For input string: "not-a-number"
    at java.base/java.lang.Integer.parseInt(Integer.java:668)
    at StackTraceReadingDemo.loadConfig(StackTraceReadingDemo.java:43)
    ... 1 more
~~~

**Đây là trace minh họa**, không phải stdout được cam kết chính xác cho chương trình ở mục thực hành; số dòng của JVM và mã ứng dụng có thể khác.

- **Ngoại lệ ngoài:** `IllegalStateException`, mang ngữ cảnh “invalid configuration”.
- **Ngoại lệ nguyên nhân:** `NumberFormatException`, do chuỗi sai định dạng số.
- `getCause()` cho phép đọc nguyên nhân nếu có.
- Phần `Caused by` nằm sâu hơn trong chuỗi nguyên nhân thường là điểm hữu ích để hiểu lỗi bắt nguồn từ đâu.

Dòng `... 1 more` có nghĩa là một số frame cuối **trùng với phần trace trước đó và đã được rút gọn khi in**, không có nghĩa “thêm một ngoại lệ mới”. Tương tự, `Suppressed:` có thể liệt kê ngoại lệ bị giữ lại (thường gặp khi đóng tài nguyên trong try-with-resources), và **không đồng nghĩa với `Caused by`**.

Khi bọc một ngoại lệ để thêm ngữ cảnh, truyền **cause** vào constructor (ví dụ `new IllegalStateException("...", ex)`) để không đánh mất trace của lỗi ban đầu.

## 7. `printStackTrace`, logger và luồng stdout/stderr

Java hỗ trợ:

~~~java
try {
    Integer.parseInt("NaN");
} catch (NumberFormatException ex) {
    ex.printStackTrace(); // Mặc định in vào System.err.
}
~~~

`printStackTrace()` thường in **kiểu ngoại lệ, thông điệp, frames và chuỗi causes**. Theo mặc định nó viết lên **stderr** (`System.err`), khác với `System.out.println` vốn viết vào **stdout**. Khi chạy từ terminal hoặc hệ thống log, stdout và stderr có thể được thu thập/hiển thị theo cơ chế khác nhau, không nên dùng thứ tự xen kẽ hai luồng làm cam kết chính xác.

Trong ứng dụng lớn, nên dùng hệ thống log với thông điệp có bối cảnh và đối tượng ngoại lệ (logger có thể in stack trace tùy API/cấu hình). Đừng ghi những dữ liệu nhạy cảm như mật khẩu, token hoặc thông tin cá nhân vào lỗi/log.

**Để minh họa stdout ổn định**, chương trình ở mục 9 **không dùng `printStackTrace()`**; thay vào đó đọc dữ liệu có cấu trúc bằng `getStackTrace()`, chỉ in tên những phương thức thuộc class demo. Trong thực tế, đừng thay toàn bộ stack trace bằng tên method khi cần điều tra: giữ file/line và cause trong hệ thống chẩn đoán thích hợp.

## 8. `StackTraceElement` và cách lọc frame thuộc dự án

Mỗi phần tử trả về bởi:

~~~java
StackTraceElement[] frames = error.getStackTrace();
~~~

cung cấp metadata:

| API của `StackTraceElement` | Trả về |
|---|---|
| `getClassName()` | Tên lớp chứa frame |
| `getMethodName()` | Tên method/constructor |
| `getFileName()` | Tên file nguồn, có thể `null` |
| `getLineNumber()` | Số dòng nếu biết, có thể âm nếu không biết |
| `isNativeMethod()` | Frame có thuộc native method không |

Một vòng lặp lọc những frame của chương trình:

~~~java
for (StackTraceElement frame : error.getStackTrace()) {
    if (frame.getClassName().equals(StackTraceReadingDemo.class.getName())) {
        System.out.println("at=" + frame.getMethodName());
    }
}
~~~

Với lỗi từ `parsePart` thông qua `calculate` và `load`, các frame ứng dụng dự kiến theo thứ tự **parsePart → calculate → load → main**. Đây là thứ tự call stack tại điểm lỗi; vòng lặp dùng tên class để tránh phụ thuộc vào số dòng hoặc frame thư viện Java.

**Đừng chỉ lọc class name trong một dự án lớn**: đây là cách trình bày có chủ đích cho chương trình demo đơn file. Với hệ thống nhiều lớp, lọc dựa trên các package dự án và đọc cả các frame liên quan từ thư viện.

## 9. Chương trình Java 17+ chạy độc lập

Lưu đúng file **`StackTraceReadingDemo.java`**:

~~~java
public final class StackTraceReadingDemo {
    private StackTraceReadingDemo() {}

    static int parsePart(String[] parts, int index) {
        return Integer.parseInt(parts[index]);
    }

    static int calculate(String[] parts) {
        return parsePart(parts, 1) + 2;
    }

    static void load() {
        String[] fields = {"10", "bad"};
        System.out.println("value=" + calculate(fields));
    }

    static void wrapCause() {
        try {
            Integer.parseInt("not-a-number");
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid setting", ex);
        }
    }

    static String firstAppMethod(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(
                    StackTraceReadingDemo.class.getName())) {
                return frame.getMethodName();
            }
        }
        return "(none)";
    }

    static void printAppFrames(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(
                    StackTraceReadingDemo.class.getName())) {
                System.out.println("at=" + frame.getMethodName());
            }
        }
    }

    public static void main(String[] args) {
        try {
            load();
        } catch (NumberFormatException ex) {
            System.out.println("caught=" + ex.getClass().getSimpleName());
            System.out.println("first app=" + firstAppMethod(ex));
            printAppFrames(ex);
        }

        try {
            wrapCause();
        } catch (IllegalStateException ex) {
            System.out.println("outer=" + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
            Throwable cause = ex.getCause();
            System.out.println("cause=" + cause.getClass().getSimpleName());
            System.out.println("cause first app=" + firstAppMethod(cause));
        }

        try {
            int[] values = {1, 2};
            System.out.println(values[3]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("bounds=" + ex.getClass().getSimpleName());
            System.out.println("bounds first app=" + firstAppMethod(ex));
        }

        System.out.println("recovered=true");
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 StackTraceReadingDemo.java
java -cp . StackTraceReadingDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\StackTraceReadingDemo.java
java -cp . StackTraceReadingDemo
~~~

Không cần Maven/Gradle, thư viện ngoài JDK hoặc preview features. Chương trình **bắt các lỗi có chủ đích**, tránh in stack trace thô phụ thuộc số dòng/version và in thông tin đã chuẩn hóa để đối chiếu.

### Exact expected stdout

~~~text
caught=NumberFormatException
first app=parsePart
at=parsePart
at=calculate
at=load
at=main
outer=IllegalStateException:invalid setting
cause=NumberFormatException
cause first app=wrapCause
bounds=ArrayIndexOutOfBoundsException
bounds first app=main
recovered=true
~~~

**Giải thích:**

1. `Integer.parseInt("bad")` ném `NumberFormatException`. Trong trace của nó, các frame do chương trình tự định nghĩa lần lượt là `parsePart`, `calculate`, `load`, `main`.
2. `wrapCause()` bắt lỗi chuyển chuỗi sang số, bọc bằng `IllegalStateException("invalid setting", ex)`. Ta đọc được kiểu của ngoại lệ ngoài và **nguyên nhân** qua `getCause()`.
3. `values[3]` vượt giới hạn mảng hai phần tử nên `ArrayIndexOutOfBoundsException` xuất hiện trực tiếp trong `main`.
4. Vì cả ba lỗi đều được bắt, cuối cùng chương trình in `recovered=true`.

Để xem trace thật trong lúc học, có thể tạm thêm `ex.printStackTrace();` trong từng khối `catch` rồi quan sát **stderr**; tuy nhiên khi làm vậy, output sẽ có thêm dòng chứa tên file, số dòng, frame JDK và có thể khác giữa môi trường.

## 10. Quy trình chẩn đoán theo một stack trace thật

1. **Giữ nguyên toàn bộ lỗi** gồm kiểu, thông điệp, dòng `at` và các phần `Caused by`. Nếu có nhiều lỗi liên tiếp, xác định lỗi ứng với lần chạy/thời điểm nào.
2. **Đọc loại lỗi**: `NullPointerException` là truy cập qua tham chiếu null; `ArrayIndexOutOfBoundsException` là chỉ số ngoài phạm vi; `NumberFormatException` là chuyển chuỗi sang số không hợp lệ. Đây là manh mối, chưa phải lời giải tự động.
3. **Tìm ứng dụng trong frames**: xác định class/package của chính dự án, mở đúng bản file nguồn cùng version với mã đang chạy.
4. **Xem đúng dòng và các giá trị liên quan**: đối số, biến có thể null, độ dài mảng, điều kiện trước đó. Dùng debugger, log an toàn và test tái hiện để kiểm chứng.
5. **Đi ngược các frame gọi** để tìm nơi đầu vào được tạo/chuyển đổi. Đọc phần `Caused by` khi có lỗi được bọc.
6. **Sửa nguyên nhân hợp lý**: xác thực đầu vào, sửa điều kiện chỉ số, giữ invariant, chuyển đổi kiểu đúng hoặc xử lý ngoại lệ tại tầng phù hợp. Đừng chỉ bắt rộng mọi `Exception` rồi bỏ qua.
7. **Chạy lại kiểm thử**: kiểm tra trường hợp gây lỗi và cả các trường hợp hợp lệ; đối chiếu trace/log mới.

Nếu một trace nói lỗi ở `UserService.java:42` nhưng file hiện tại không có dòng tương ứng, cần kiểm tra mã đã biên dịch, mapping, phiên bản triển khai và cờ debug thay vì sửa tùy đoán.

## 11. Một số trường hợp dễ gây hiểu sai

| Tình huống | Cách hiểu/giải quyết |
|---|---|
| Frame đầu thuộc thư viện chuẩn Java | Xem frame ứng dụng gọi vào thư viện và dữ liệu đầu vào |
| Trace có nhiều `Caused by` | Theo chuỗi nguyên nhân và bối cảnh; nguyên nhân sâu nhất thường quan trọng nhưng chưa chắc đủ |
| Có `... 4 more` | Một số frame trùng bị rút gọn, không phải 4 lỗi nữa |
| Có `Suppressed:` | Ngoại lệ phụ được giữ lại, khác với cause |
| Không có số dòng hoặc hiện `Unknown Source` | Kiểm tra thông tin debug, mã chạy, JAR, source tương ứng |
| `catch` chỉ in một câu chung chung | Cần giữ lại exception/cause để có thể điều tra |
| Trace rất dài, qua proxy/lambda/framework | Tìm các frame ứng dụng và đường gọi liên quan; không loại bỏ ngữ cảnh hữu ích |
| Lỗi xuất hiện từ worker thread/future | Trace tại nơi throw và nơi lấy kết quả có thể khác; đọc cause và bối cảnh thread |
| Có `Error` như `StackOverflowError` | Không mặc định đây là ngoại lệ nghiệp vụ có thể phục hồi; xem lỗi logic/tài nguyên |
| Tự sửa số dòng theo file đang mở | Số dòng phải khớp phiên bản nguồn thực sự đã chạy |
| Dùng `System.out` và `System.err` xen kẽ | Thứ tự hiển thị giữa hai stream không luôn được cam kết |
| In token/mật khẩu trong log lỗi | Rò rỉ dữ liệu; che hoặc loại bỏ nội dung nhạy cảm |

**Lưu ý:** lỗi **biên dịch** (ví dụ “cannot find symbol”) không phải runtime exception; nó thường có dạng thông báo `javac` về file, dòng và vị trí lỗi, không có runtime call stack. Đọc đúng loại thông báo trước khi áp dụng quy trình stack trace.

## 12. Bài tập có đáp án

**A.** Với dòng `at com.shop.OrderService.place(OrderService.java:57)`, cần mở file nào và kiểm tra đâu?  
**Đáp án:** mở `OrderService.java` đúng phiên bản đang chạy, xem khoảng **dòng 57** trong phương thức `place`.

**B.** Với `NumberFormatException` ở `Integer.parseInt`, phải chỉ sửa mã JDK không?  
**Đáp án:** **Không**. Tìm frame ứng dụng gọi `parseInt`, kiểm tra chuỗi đầu vào và nơi tạo chuỗi ấy.

**C.** Nếu một ngoại lệ `IllegalStateException` có `Caused by: NumberFormatException`, hai loại đó là hai dòng `at` liên tiếp của một trace giống hệt nhau không?  
**Đáp án:** **Không**. Đó là **chuỗi ngoại lệ có nguyên nhân**, mỗi ngoại lệ có thể mang stack trace riêng.

**D.** Dòng `... 2 more` cho biết có hai exception khác nữa không?  
**Đáp án:** **Không**. Đây là số frame đuôi trùng bị rút gọn khi trình bày nested trace.

**E.** Vì sao một đoạn `catch (Exception ex) { }` rỗng gây khó tìm lỗi?  
**Đáp án:** nó nuốt thông tin kiểu lỗi, thông điệp, vị trí và nguyên nhân; cần xử lý hợp lý hoặc ghi log phù hợp mà không lộ dữ liệu nhạy cảm.

**F.** Trong chương trình ở mục 9, frame ứng dụng đầu tiên của lỗi `NumberFormatException` là phương thức nào?  
**Đáp án:** `parsePart`. Các frame tiếp theo `calculate`, `load`, `main` cho biết chuỗi gọi.

## 13. Checklist và kết luận

- [ ] Biết stack trace mô tả call stack tại thời điểm lỗi/thu thập trace.
- [ ] Đọc được kiểu ngoại lệ và thông điệp ở đầu trace.
- [ ] Phân tích `at Class.method(File.java:line)` và truy về file/dòng đúng version.
- [ ] Biết frame JDK không nhất thiết là nơi cần sửa mã dự án.
- [ ] Đọc thứ tự các frame để hiểu chuỗi phương thức gọi.
- [ ] Nhận diện `Caused by`, `Suppressed:` và ý nghĩa `... N more`.
- [ ] Biết `printStackTrace()` mặc định viết stderr, khác stdout.
- [ ] Dùng được `getStackTrace()`/`StackTraceElement` khi cần xử lý có cấu trúc.
- [ ] Không “nuốt” ngoại lệ hoặc đưa dữ liệu nhạy cảm vào log.
- [ ] Biết số dòng và frame có thể khác giữa bản biên dịch/JDK.
- [ ] Chạy `StackTraceReadingDemo.java` trên JDK 17+, đối chiếu exact stdout.
- [ ] Tái hiện lỗi, xác thực nguyên nhân, sửa đúng chỗ và kiểm thử lại.

**Kết luận:** Đọc stack trace bắt đầu từ **loại lỗi và thông điệp**, sau đó tìm **frame của ứng dụng** gần nơi lỗi nhất, lần theo **chuỗi gọi** và các phần **`Caused by`** để hiểu bối cảnh. Đường dẫn file và số dòng giúp định vị mã, nhưng cần đúng phiên bản đang chạy; mục tiêu là sửa nguyên nhân dữ liệu/logic chứ không đơn thuần che giấu thông báo lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-004">try-catch để xử lý tình huống có thể thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# `try-catch` để xử lý tình huống có thể thất bại trong Java

## 1. Vì sao cần `try-catch`?

Khi chạy một chương trình Java, có những thao tác có thể thất bại: chuyển chuỗi không hợp lệ thành số, đọc tệp, sử dụng một chỉ số mảng không đúng hoặc gọi dịch vụ bên ngoài. Java biểu diễn nhiều tình huống như vậy bằng **ngoại lệ** (*exception*). Thay vì để ngoại lệ không được xử lý làm luồng thực thi hiện tại kết thúc bất thường, ta có thể dùng `try-catch` để **phản ứng có chủ đích**.

Cấu trúc cơ bản:

```java
try {
    // Những lệnh có thể ném ngoại lệ.
} catch (SomeException ex) {
    // Xử lý đúng loại ngoại lệ đã bắt được.
}
```

- Khối **`try`** chứa thao tác cần bảo vệ.
- Khi một câu lệnh ném ngoại lệ, các câu lệnh còn lại trong phần `try` **không tiếp tục thực hiện theo đường thông thường**.
- Java tìm một `catch` có kiểu phù hợp với đối tượng ngoại lệ, xét theo thứ tự các `catch`.
- **`ex`** là tham số tham chiếu đến đối tượng ngoại lệ, cho phép truy cập thông tin như `getMessage()` hoặc dùng `printStackTrace()`.
- Sau một `catch` xử lý xong và không ném tiếp/return, chương trình tiếp tục ở câu lệnh phía sau cấu trúc `try-catch`.
- Nếu **không có `catch` phù hợp**, ngoại lệ lan tới nơi gọi. Nếu không ai xử lý, nó có thể kết thúc luồng với stack trace.

`try-catch` không tự sửa phép tính sai hay làm dữ liệu đầu vào trở nên hợp lệ; nó chỉ xác định **cách ứng xử khi xảy ra thất bại**.

## 2. Ví dụ tối thiểu: chuyển chuỗi thành số

`Integer.parseInt(String)` chuyển văn bản thành `int`, nhưng chuỗi không biểu diễn số nguyên hợp lệ gây `NumberFormatException`.

```java
String input = "abc";

try {
    int value = Integer.parseInt(input);
    System.out.println("Gia tri: " + value);
} catch (NumberFormatException ex) {
    System.out.println("Du lieu khong phai so nguyen hop le");
}

System.out.println("Tiep tuc chuong trinh");
```

Đây là **đoạn minh họa đặt trong một phương thức**, không phải tệp chạy độc lập. Khi `input = "abc"`, dòng in giá trị không chạy, nhánh `catch` in thông báo, rồi lệnh bên ngoài `try-catch` được thực hiện.

Với `input = "123"`, nhánh `catch` không chạy, biến `value` bằng `123`. Biến `value` khai báo **bên trong `try`** không nằm trong scope của câu lệnh sau khối, nên nếu cần dùng kết quả về sau, có thể trả về từ một phương thức hoặc thiết kế trạng thái phù hợp.

Đừng coi mọi lỗi chuyển đổi thành số đều có thể sửa bằng cách thay giá trị tùy ý. Tùy yêu cầu, lựa chọn đúng có thể là báo cho người dùng nhập lại, từ chối dữ liệu, ghi log rồi chuyển tiếp ngoại lệ hoặc trả về một kết quả thể hiện thất bại.

## 3. Bắt loại ngoại lệ cụ thể trước

Mỗi `catch` bắt một loại ngoại lệ được khai báo hoặc các kiểu con của nó. Vì thế, nên **bắt loại cụ thể trước**, loại tổng quát sau:

```java
try {
    // Xử lý đầu vào, tính toán.
} catch (NumberFormatException ex) {
    System.out.println("Sai dinh dang so");
} catch (IllegalArgumentException ex) {
    System.out.println("Doi so khong hop le");
}
```

`NumberFormatException` là kiểu con của `IllegalArgumentException`. Nếu đặt `catch (IllegalArgumentException ...)` **lên trước**, khối bắt `NumberFormatException` sau đó sẽ trở nên không thể tới được, gây **lỗi biên dịch**. Trong ví dụ trên, thứ tự được viết đúng.

Đừng bắt `Exception` hoặc `Throwable` thật rộng ở mọi tầng của chương trình:

- Bắt quá rộng khiến lỗi lập trình không mong đợi bị xử lý như lỗi dữ liệu thông thường.
- `Throwable` còn bao gồm nhánh `Error` như `OutOfMemoryError` mà chương trình ứng dụng thông thường không nên cố “nuốt”.
- `catch` rỗng làm mất tín hiệu thất bại, khiến việc gỡ lỗi khó khăn.
- Khi không thể khôi phục tại chỗ, có thể **để ngoại lệ lan lên**, hoặc bọc nguyên nhân vào ngoại lệ mang ý nghĩa cấp cao hơn.

Ví dụ một cách chuyển tiếp có nguyên nhân rõ ràng:

```java
static int parseAge(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Age must be a whole number", ex);
    }
}
```

Đây là **phương thức minh họa đặt trong lớp**. Constructor `IllegalArgumentException(String, Throwable)` giữ lại ngoại lệ gốc ở `getCause()`; hữu ích khi xử lý lỗi ở tầng có đủ ngữ cảnh. Không nên bọc ngoại lệ một cách máy móc khi nó không bổ sung ý nghĩa.

## 4. Nhiều `catch` và multi-catch

Nếu các loại ngoại lệ cần cách xử lý riêng, dùng nhiều `catch`. Nếu chúng cần **cùng cách xử lý**, có thể dùng multi-catch (Java 7+):

```java
try {
    String text = args[0];
    int value = Integer.parseInt(text);
    System.out.println(value);
} catch (ArrayIndexOutOfBoundsException | NumberFormatException ex) {
    System.out.println("Can mot doi so nguyen hop le");
}
```

Đây là đoạn minh họa bên trong phương thức có biến `args` thích hợp. Hai ngoại lệ cùng cần phản hồi “không có đầu vào hợp lệ”. Các kiểu trong cùng một multi-catch **không được có quan hệ kiểu con/kiểu cha trực tiếp**; ví dụ `catch (NumberFormatException | IllegalArgumentException ex)` là lỗi biên dịch do một nhánh đã bao hàm nhánh kia.

Khi thiết kế chương trình thật, tốt hơn nên kiểm tra `args.length` để nhận diện thiếu đối số, rồi dùng `catch (NumberFormatException)` cho lỗi chuyển đổi. Không nên dùng ngoại lệ để thay cho mọi nhánh điều kiện thông thường.

**Khi ngoại lệ đã được bắt**, chỉ đúng một `catch` phù hợp đầu tiên được chạy, không tự “rơi tiếp” sang `catch` bên dưới. Một `catch` có thể ném ngoại lệ mới; ngoại lệ mới đó không được các `catch` khác của **cùng cấu trúc `try`** xử lý, mà phải được xử lý ở phạm vi bao ngoài phù hợp.

## 5. Xử lý checked/unchecked theo hợp đồng của thao tác

Nếu một API có thể ném checked `IOException`, như `Files.readString`, caller phải **catch** hoặc khai báo `throws` theo luật Java. Ngược lại, `Integer.parseInt("abc")` ném unchecked `NumberFormatException`, không bắt buộc khai báo nhưng vẫn có thể `catch` khi dữ liệu không hợp lệ là tình huống dự kiến.

~~~java
static String readText(java.nio.file.Path file)
        throws java.io.IOException {
    return java.nio.file.Files.readString(file);
}
~~~

`throws` chỉ cho phép ngoại lệ checked thoát khỏi phương thức; nó không phải handler. `Error` như `OutOfMemoryError` cũng unchecked nhưng thường không phù hợp để bắt và tiếp tục nghiệp vụ. Cây phân loại `Throwable`, quy tắc compiler và việc tự ném được giải thích sâu ở hai bài tiếp theo.

## 6. `finally` vẫn thực hiện phần dọn dẹp thông thường

~~~java
try {
    System.out.println("start");
    throw new IllegalStateException("bad");
} catch (IllegalStateException ex) {
    System.out.println("caught");
} finally {
    System.out.println("cleanup");
}
~~~

Ba dòng trên in lần lượt `start`, `caught`, `cleanup`. `finally` thông thường chạy khi có exception hoặc `return` làm rời khối, nhưng **không cam kết khi JVM/process bị chấm dứt đột ngột**. Tránh `return` hay ném lỗi mới trong `finally` nếu có thể che kết quả/ngoại lệ trước đó.

## 7. Đóng tài nguyên bằng `try-with-resources`

~~~java
static String firstLine(java.nio.file.Path file)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 java.nio.file.Files.newBufferedReader(file)) {
        return reader.readLine();
    }
}
~~~

Tài nguyên phải hỗ trợ `AutoCloseable`; Java tự đóng khi rời khối theo đường bình thường hoặc ngoại lệ. Nếu nhiều tài nguyên, chúng được **đóng ngược thứ tự khởi tạo**. Khi thân và thao tác đóng cùng thất bại, ngoại lệ đóng có thể nằm trong `getSuppressed()`; I/O checked exception **vẫn phải bắt/khai báo**, không tự biến thành thành công.

Ở bài `try-catch`, phần cần nắm là **xử lý thất bại hợp lý**. Bài riêng về `finally`/try-with-resources giải thích chuyên sâu return, masking, thứ tự đóng và suppressed exception.


## 8. Chương trình Java 17+ độc lập với kết quả xác định

Tạo tệp **`TryCatchDemo.java`**:

```java
import java.io.IOException;

public class TryCatchDemo {
    static class TrackedResource implements AutoCloseable {
        void use() {
            System.out.println("Dang su dung tai nguyen");
        }

        @Override
        public void close() {
            System.out.println("Da dong tai nguyen");
        }
    }

    static String checkValue(int value) throws IOException {
        if (value < 0) {
            throw new IOException("Gia tri am");
        }
        return "OK";
    }

    public static void main(String[] args) {
        try {
            int number = Integer.parseInt("abc");
            System.out.println("So: " + number);
        } catch (NumberFormatException ex) {
            System.out.println("Sai dinh dang: " + ex.getClass().getSimpleName());
        }

        int divisor = 0;
        try {
            int result = 12 / divisor;
            System.out.println(result);
        } catch (ArithmeticException ex) {
            System.out.println("Khong the chia cho 0");
        } finally {
            System.out.println("Finally da chay");
        }

        try {
            String value = checkValue(-1);
            System.out.println(value);
        } catch (IOException ex) {
            System.out.println("Checked: " + ex.getMessage());
        }

        try (TrackedResource resource = new TrackedResource()) {
            resource.use();
        }

        int[] numbers = {3, 5};
        try {
            System.out.println(numbers[2]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("Chi so vuot gioi han");
        }

        System.out.println("Chuong trinh van tiep tuc");
    }
}
```

Dùng JDK 17 trở lên. Trong thư mục chứa `TryCatchDemo.java`, chạy giống nhau trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 TryCatchDemo.java
java TryCatchDemo
```

**Stdout chính xác** (khi biên dịch thành công, `javac` không in gì):

```text
Sai dinh dang: NumberFormatException
Khong the chia cho 0
Finally da chay
Checked: Gia tri am
Dang su dung tai nguyen
Da dong tai nguyen
Chi so vuot gioi han
Chuong trinh van tiep tuc
```

Giải thích theo trình tự:

1. `Integer.parseInt("abc")` ném `NumberFormatException`; dòng `So:` bị bỏ qua và `catch` phù hợp in tên ngoại lệ.
2. `12 / divisor` với `divisor == 0` gây `ArithmeticException`; `catch` in thông báo và `finally` luôn chạy trong đường thực thi bình thường này.
3. `checkValue(-1)` chủ động ném `IOException` (checked exception), được `catch` xử lý với thông điệp `Gia tri am`.
4. `TrackedResource` được tạo trong try-with-resources; `resource.use()` in một dòng rồi `close()` in dòng kế tiếp khi ra khỏi khối.
5. Truy cập `numbers[2]` trên mảng có hai phần tử gây `ArrayIndexOutOfBoundsException`, được xử lý; chương trình tiếp tục tới dòng in cuối.

Việc bắt các ngoại lệ ở đây nhằm **minh họa**, không có nghĩa trong ứng dụng thật nên bắt lỗi chỉ số mảng để che đi việc dùng chỉ số sai. Hãy sửa điều kiện truy cập mảng và dùng `catch` cho thất bại mà ứng dụng có cách xử lý hợp lý.

## 9. Chọn cách xử lý thay vì “nuốt” ngoại lệ

Có vài chiến lược tùy yêu cầu nghiệp vụ.

| Tình huống | Chiến lược thường phù hợp |
| --- | --- |
| Người dùng nhập chuỗi không phải số | Bắt `NumberFormatException`, thông báo rõ và yêu cầu nhập lại |
| Tệp đầu vào không tồn tại hoặc không đọc được | Bắt `IOException` tại tầng biết nên thử lại, báo lỗi hay hủy thao tác |
| Đối số vi phạm điều kiện trước khi xử lý | Kiểm tra trước và ném `IllegalArgumentException` khi đó là lỗi sử dụng API |
| Thành phần tầng thấp thất bại nhưng tầng trên cần ngữ cảnh | Ném một exception thích hợp có **cause** gốc |
| Không có cách khôi phục ở tầng hiện tại | Để ngoại lệ lan lên hoặc khai báo `throws` phù hợp |
| Tài nguyên phải được đóng | `try-with-resources` nếu tài nguyên hỗ trợ |

Ví dụ **không nên viết**:

```java
try {
    doWork();
} catch (Exception ex) {
    // Bo qua tat ca loi
}
```

Đây chỉ là minh họa với phương thức `doWork()` chưa định nghĩa. Cách bắt quá rộng và im lặng như vậy có thể khiến chương trình tưởng rằng công việc đã thành công dù dữ liệu chưa được lưu hoặc tài nguyên chưa được xử lý đúng.

Thay vào đó, hãy lựa chọn rõ **hậu điều kiện**: nếu chuyển đổi thất bại thì không trả giá trị sai như thể hợp lệ; nếu ghi tệp thất bại thì không thông báo “đã lưu”; nếu một request thất bại thì phản hồi trạng thái tương ứng thay vì tiếp tục với dữ liệu thiếu.

## 10. Lỗi thường gặp khi dùng `try-catch`

1. **Đặt `try` quanh quá nhiều mã:** khó biết dòng nào có thể thất bại và vô tình bắt lỗi không mong muốn. Đặt phạm vi `try` đủ nhỏ để xử lý rõ ràng.
2. **Bắt lớp cha trước lớp con:** có thể gây unreachable catch và lỗi biên dịch; bắt cụ thể trước, tổng quát sau.
3. **Dùng multi-catch cho kiểu có quan hệ kế thừa:** `catch (NumberFormatException | IllegalArgumentException ex)` không hợp lệ.
4. **Bắt `Exception` rồi bỏ qua:** che lỗi, phá khả năng chẩn đoán, có thể để chương trình trong trạng thái không hợp lệ.
5. **Nhầm `throws` với `catch`:** `throws` khai báo khả năng lan ngoại lệ; `catch` thực hiện xử lý tại chỗ.
6. **Cho rằng `finally` bảo đảm chạy cả khi JVM chết/tiến trình bị giết:** không có bảo đảm như vậy.
7. **Đóng tài nguyên thủ công thiếu an toàn:** dùng try-with-resources khi có thể.
8. **Bắt `Error` hoặc `Throwable` tùy tiện:** thường không thể khôi phục tình trạng lỗi hệ thống như ứng dụng mong muốn.
9. **Tạo ngoại lệ mới mà không giữ cause:** làm mất nguồn gốc lỗi; khi bọc cần cân nhắc `new SomeException(message, cause)`.
10. **Dùng ngoại lệ thay cho kiểm tra điều kiện thông thường:** kiểm tra `args.length`, chỉ số mảng, tiền điều kiện trước; chỉ dùng `catch` khi thao tác thực sự có thể thất bại.

**Ghi nhớ:** `try` xác định thao tác có thể thất bại; `catch` xử lý đúng **loại ngoại lệ** khi có cách ứng xử thích hợp; `finally` cho các bước cần thực hiện khi rời khối, còn `try-with-resources` giúp đóng tài nguyên. Mục tiêu là giữ hành vi chương trình **rõ ràng, đúng yêu cầu và dễ chẩn đoán**, không phải loại bỏ mọi thông báo lỗi bằng mọi giá.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-005">throw để báo lỗi và throws để khai báo khả năng phát sinh</a>

<details>
<summary>Xem chi tiết</summary>

# `throw` để báo lỗi và `throws` để khai báo khả năng phát sinh (Java 17+)

## 1. Phân biệt ngay từ đầu

Java dùng hai từ khóa dễ nhầm:

- **`throw`** là một **câu lệnh** thực sự ném một đối tượng ngoại lệ tại vị trí đang thực thi, thường khi phát hiện dữ liệu hoặc trạng thái không hợp lệ.
- **`throws`** nằm trong **khai báo phương thức hoặc constructor**, liệt kê các loại ngoại lệ có thể được truyền ra cho người gọi. Nó không tự ném, bắt hoặc xử lý ngoại lệ.

Ví dụ cú pháp:

```java
static int requirePositive(int value) throws IllegalArgumentException {
    if (value <= 0) {
        throw new IllegalArgumentException("value must be positive");
    }
    return value;
}
```

Ở đây `throw new IllegalArgumentException(...)` tạo và ném ngoại lệ khi điều kiện đúng. Phần `throws IllegalArgumentException` chỉ khai báo khả năng xảy ra; vì đây là **unchecked exception**, khai báo `throws` là **tùy chọn**. Trong dự án, thường bỏ `throws` cho unchecked exception trừ khi nó giúp diễn đạt hợp đồng API. Ví dụ trên là một phương thức minh họa, chưa phải chương trình độc lập có `main`.

| Tiêu chí | `throw` | `throws` |
| --- | --- | --- |
| Vị trí | Trong thân phương thức, constructor hoặc khối có thể chứa câu lệnh | Sau danh sách tham số của phương thức/constructor, trước phần thân hoặc `;` |
| Vai trò | Ném một đối tượng thuộc `Throwable` | Khai báo một hay nhiều **loại** ngoại lệ có thể phát sinh |
| Dạng thường gặp | `throw new IOException("...");` | `void read() throws IOException` |
| Ảnh hưởng luồng chạy | Dừng luồng thực thi thông thường ở điểm ném, tìm handler phù hợp | Không tạo ngoại lệ và không tự đổi luồng chạy |
| Liên quan checked exception | Ném checked exception phải được bắt hoặc khai báo hợp lệ ở ngữ cảnh bao quanh | Giúp phương thức truyền checked exception cho caller |

Không viết `throws new IOException(...)` hoặc `throw IOException` để ném một instance mới: phải dùng `throw new IOException(...)`. Không dùng `throws` thay thế `catch` nếu yêu cầu nghiệp vụ là xử lý lỗi ngay tại phương thức.

## 2. Ngoại lệ được ném như thế nào?

Khi thực thi gặp `throw exceptionObject;`:

1. Biểu thức được đánh giá; đối tượng được ném phải thuộc lớp con của `java.lang.Throwable`.
2. Luồng thực thi thông thường của đoạn mã hiện tại bị ngắt; các câu lệnh phía sau trên nhánh đó không chạy.
3. Java tìm khối `catch` tương thích trong các `try` đang bao quanh. Nếu không có, ngoại lệ truyền ngược lên phương thức gọi theo call stack.
4. Các khối `finally` thích hợp và cơ chế đóng tài nguyên `try-with-resources` vẫn được thực hiện theo quy tắc ngôn ngữ khi rời phạm vi.
5. Nếu ngoại lệ không được xử lý đến đầu luồng thực thi, trình xử lý ngoại lệ không bắt được của luồng có thể in stack trace; chương trình hoặc luồng liên quan kết thúc bất thường.

Ví dụ:

```java
if (balance < amount) {
    throw new IllegalStateException("insufficient balance");
}
```

Dòng này **thực thi được** nếu nhánh `if` đúng. Nó không đơn thuần ghi chú rằng lỗi có thể xảy ra. `IllegalStateException` là unchecked exception; luật Java không bắt buộc đặt `throws IllegalStateException` trên chữ ký phương thức.

Câu lệnh `throw null;` có thể biên dịch trong ngữ cảnh phù hợp nhưng khi thực thi sẽ phát sinh `NullPointerException`; không nên dùng. Không ném các giá trị không thuộc `Throwable` như chuỗi `"error"` hoặc số `123`.

## 3. Vì sao `throws` bắt buộc với checked exception?

`throws` có vai trò quan trọng nhất khi phương thức có thể cho **checked exception** thoát ra: ví dụ `IOException` hoặc ngoại lệ tự tạo `extends Exception`. Nếu không xử lý bằng `catch`, phương thức phải khai báo `throws` phù hợp. Với `RuntimeException` và `Error` cùng các lớp con (**unchecked**), trình biên dịch không bắt buộc `throws`.

| Ví dụ | Nhóm | Nghĩa vụ khi ngoại lệ có thể thoát ra |
| --- | --- | --- |
| `IOException`, `InvalidScoreException extends Exception` | Checked | Catch hoặc khai báo throws |
| `IllegalArgumentException`, `NumberFormatException` | Unchecked | Có thể throw mà không khai báo |
| `StackOverflowError` | Unchecked Error | Không cần khai báo; không dùng như lỗi nghiệp vụ |

Điều này không có nghĩa checked luôn xảy ra hoặc unchecked luôn là lỗi lập trình. Bài sau phân tích cây `Throwable`, các lớp cụ thể và cách chọn hợp đồng. Trong bài này, hãy phân biệt rõ **`throw` ném một object tại runtime** với **`throws` liệt kê kiểu ngoại lệ ở chữ ký**.


## 4. Ví dụ đầy đủ: tự ném checked exception và khai báo `throws`

Ví dụ nhận điểm từ chuỗi. Nếu không phải số nguyên hoặc ngoài khoảng 0 đến 100, phương thức sẽ **`throw`** ngoại lệ tự định nghĩa. Vì `InvalidScoreException extends Exception`, phương thức phải **`throws InvalidScoreException`**, và `main` sẽ `catch` để xử lý từng đầu vào.

Tạo một tệp tên **`ThrowThrowsDemo.java`**:

```java
public class ThrowThrowsDemo {
    static int parseScore(String text) throws InvalidScoreException {
        final int score;
        try {
            score = Integer.parseInt(text);
        } catch (NumberFormatException cause) {
            throw new InvalidScoreException("invalid integer: " + text, cause);
        }

        if (score < 0 || score > 100) {
            throw new InvalidScoreException("score out of range: " + score);
        }
        return score;
    }

    public static void main(String[] args) {
        String[] inputs = {"95", "150", "abc"};

        for (String input : inputs) {
            try {
                System.out.println(input + " => " + parseScore(input));
            } catch (InvalidScoreException ex) {
                System.out.println(input + " => " + ex.getMessage());
            }
        }
    }
}

class InvalidScoreException extends Exception {
    InvalidScoreException(String message) {
        super(message);
    }

    InvalidScoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Chạy tại thư mục chứa tệp (JDK 17+, PowerShell, CMD, bash). Tạo thư mục `out` nếu chưa có:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out ThrowThrowsDemo.java
java -cp out ThrowThrowsDemo
```

**Stdout chính xác của chương trình:**

```text
95 => 95
150 => score out of range: 150
abc => invalid integer: abc
```

Giải thích theo luồng:

- Với `95`, `parseScore` trả về `95`, không ném ngoại lệ.
- Với `150`, điều kiện kiểm tra khoảng điểm đúng; `throw new InvalidScoreException(...)` chuyển điều khiển đến `catch` trong `main`. `return score;` không được thực hiện cho lần gọi đó.
- Với `abc`, `Integer.parseInt` ném `NumberFormatException` (unchecked); `catch` chuyển nó thành `InvalidScoreException` (checked) và truyền **nguyên nhân gốc** qua `super(message, cause)`.
- `throws InvalidScoreException` trong chữ ký giúp caller biết nghĩa vụ bắt hoặc khai báo ngoại lệ checked. Chính các câu lệnh `throw` mới làm ngoại lệ xuất hiện.
- Vòng `for` tiếp tục sau từng `catch`, nên cả ba dòng được in.

Kiểu `InvalidScoreException` là lớp top-level không `public` cùng tệp, hợp lệ vì tệp chỉ có một kiểu top-level `public` là `ThrowThrowsDemo`. Nếu chuyển ngoại lệ thành `public class InvalidScoreException`, cần đặt riêng trong `InvalidScoreException.java`.

## 5. Unchecked exception: có `throw`, không cần `throws`

Với tiền điều kiện của API, `IllegalArgumentException` thường phù hợp. Tạo **`UncheckedDemo.java`**:

```java
public class UncheckedDemo {
    static int percentage(int part, int total) {
        if (total <= 0) {
            throw new IllegalArgumentException("total must be positive");
        }
        if (part < 0 || part > total) {
            throw new IllegalArgumentException("part out of range");
        }
        return part * 100 / total;
    }

    public static void main(String[] args) {
        System.out.println("percent=" + percentage(3, 4));

        try {
            System.out.println(percentage(2, 0));
        } catch (IllegalArgumentException ex) {
            System.out.println("error=" + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out UncheckedDemo.java
java -cp out UncheckedDemo
```

Output:

```text
percent=75
error=total must be positive
```

Không có `throws IllegalArgumentException` trong `percentage` nhưng chương trình vẫn hợp lệ. Caller có thể bắt ngoại lệ này khi có phương án xử lý hữu ích; việc bắt không phải điều kiện biên dịch. Trong code sản xuất, hãy cân nhắc phép tính phần trăm khi dùng số nguyên lớn vì `part * 100` có thể tràn `int`; ví dụ ở đây dùng số nhỏ để minh họa cơ chế ngoại lệ.

## 6. Caller bắt hoặc khai báo để truyền tiếp checked exception

Giả sử một phương thức thực thi `throw new java.io.IOException(...)`. Đối với checked exception, có hai cách hợp lệ ở caller:

**Cách A — bắt để xử lý tại chỗ:**

```java
try {
    readData();
} catch (java.io.IOException ex) {
    System.out.println("Read failed: " + ex.getMessage());
}
```

**Cách B — khai báo `throws` để trách nhiệm xử lý được chuyển lên caller tiếp theo:**

```java
static void relay() throws java.io.IOException {
    readData();
}
```

Hai đoạn trên chỉ minh họa phần thân phương thức; để chạy độc lập cần định nghĩa `readData()` và ngữ cảnh bao quanh. `throws` không tự bọc ngoại lệ bằng `try/catch`, nên nếu quá trình gọi không có handler, ngoại lệ vẫn có thể truyền lên.

Một ví dụ **đầy đủ, chạy được** chứng minh lan truyền: tạo `PropagationDemo.java`:

```java
import java.io.IOException;

public class PropagationDemo {
    static String readData() throws IOException {
        throw new IOException("sample read failure");
    }

    static String relay() throws IOException {
        return readData();
    }

    public static void main(String[] args) {
        try {
            System.out.println(relay());
        } catch (IOException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
```

Chạy:

```sh
javac -encoding UTF-8 --release 17 -d out PropagationDemo.java
java -cp out PropagationDemo
```

Output:

```text
caught=sample read failure
```

Chuỗi gọi là `main → relay → readData`. `readData` ném `IOException`; `relay` khai báo để truyền tiếp; `main` bắt và in thông báo. Đây là ngoại lệ được tạo có chủ đích để minh họa, không có thao tác đọc tệp thực tế.

## 7. Lỗi biên dịch phổ biến khi dùng `throw`/`throws`

### 7.1. Ném checked exception mà không bắt/khai báo

Đoạn dưới **cố ý không biên dịch được**:

```java
import java.io.IOException;

class InvalidChecked {
    static void load() {
        throw new IOException("cannot read");
    }
}
```

`javac` thường báo ngoại lệ `IOException` cần được bắt hoặc khai báo. Có thể sửa chữ ký thành:

```java
static void load() throws java.io.IOException {
    throw new java.io.IOException("cannot read");
}
```

Đây là phương thức minh họa; caller sau đó vẫn phải bắt hoặc khai báo checked exception thích hợp.

### 7.2. Caller không xử lý ngoại lệ được khai báo

Đoạn dưới cũng **cố ý sai**:

```java
static void caller() {
    relay(); // relay() khai báo throws IOException
}
```

Nếu phương thức `relay()` được định nghĩa như trong `PropagationDemo`, cần đổi thành `static void caller() throws IOException { relay(); }` (khi đã import `IOException`) hoặc đặt lời gọi trong `try/catch(IOException ex)`.

### 7.3. Khai báo rộng hơn mức cần thiết

`throws Exception` có thể hợp lệ nhưng làm giảm thông tin: caller không biết nhóm lỗi được dự kiến. Ưu tiên ngoại lệ cụ thể như `throws IOException` hoặc `throws InvalidScoreException`. Nếu cần nhiều checked exception độc lập, có thể liệt kê bằng dấu phẩy:

```java
void work() throws java.io.IOException, java.text.ParseException
```

Dòng này chỉ minh họa **chữ ký**; một khai báo phương thức đầy đủ cần có thân `{ ... }` hoặc dạng khai báo hợp lệ trong interface/abstract type.

### 7.4. Hiểu sai ý nghĩa của `throws`

Khai báo `throws IOException` **không bảo đảm** phương thức sẽ ném `IOException` mỗi lần gọi. Nó chỉ thể hiện ngoại lệ đó được phép truyền qua chữ ký. Ngược lại, chỉ đọc chữ ký mà không thấy `throws RuntimeException` không có nghĩa mã sẽ không gặp unchecked exception.

## 8. Giữ nguyên nguyên nhân và ngữ cảnh lỗi

Nếu chuyển một ngoại lệ thấp tầng thành ngoại lệ mang ý nghĩa nghiệp vụ, hãy bảo tồn nguyên nhân ban đầu:

```java
try {
    return Integer.parseInt(text);
} catch (NumberFormatException cause) {
    throw new InvalidScoreException("invalid integer: " + text, cause);
}
```

Đây là đoạn lấy từ ví dụ `ThrowThrowsDemo`; nó phụ thuộc vào lớp `InvalidScoreException` đã định nghĩa. Chuyển nguyên nhân vào constructor làm `ex.getCause()` còn giữ được `NumberFormatException`, phục vụ log/debug. Chỉ `throw new InvalidScoreException("...")` mà bỏ `cause` sẽ làm mất liên kết nguyên nhân. `throw cause;` trong khối `catch` lại là ném chính đối tượng đã bắt (rethrow), không phải chuyển sang một loại checked exception mới.

Đừng che giấu lỗi bằng `catch (Exception ex) { }` rỗng hoặc trả về giá trị mặc định không có căn cứ. Nếu handler không thể xử lý đúng, truyền tiếp ngoại lệ có thông điệp/nguồn gốc phù hợp thường tốt hơn.

Khi ném ngoại lệ sau khi đã mở tài nguyên, dùng `try-with-resources` với loại tài nguyên thực thi `AutoCloseable` để đóng tài nguyên kể cả khi có ngoại lệ. Hạn chế `return`/`throw` từ `finally` vì có thể làm khó nhận biết ngoại lệ trước đó; trong trường hợp đặc biệt, một ngoại lệ từ `finally` có thể thay thế ngoại lệ đang được truyền ra.

## 9. Lưu ý về phương thức ghi đè và constructor

- Phương thức **override** không được bổ sung hoặc mở rộng checked exception vượt những gì chữ ký phương thức được ghi đè cho phép. Ví dụ, nếu method cha không khai báo checked exception thì method override không được tự thêm `throws IOException`. Ngoại lệ unchecked không chịu giới hạn cùng kiểu này.
- Constructor có thể dùng `throws` để công bố checked exception khi khởi tạo. Caller tạo đối tượng phải bắt hoặc khai báo.
- `main(String[] args)` cũng có thể khai báo `throws Exception`; cách đó thuận tiện trong thử nghiệm nhỏ nhưng khi ngoại lệ không được bắt chương trình có thể kết thúc với stack trace. Ứng dụng thường cần xử lý lỗi tại ranh giới thích hợp.

Ví dụ minh họa một constructor có khai báo checked exception (chưa phải chương trình độc lập):

```java
class ConnectionConfig {
    ConnectionConfig(String address) throws java.io.IOException {
        if (address == null || address.isBlank()) {
            throw new java.io.IOException("missing address");
        }
    }
}
```

Khi gọi `new ConnectionConfig("")`, ngoại lệ phát sinh từ `throw`; `throws` ở constructor buộc caller xử lý hoặc truyền tiếp.

## 10. Quy tắc chọn cách viết trong thực tế

1. **Phát hiện trạng thái sai tại đúng chỗ:** dùng `throw new ...` với loại ngoại lệ, message và cause thích hợp.
2. **Ngoại lệ checked có thể truyền ra:** dùng `throws LoaiNgoaiLe` trong chữ ký nếu không xử lý tại chỗ; để caller quyết định chính sách phục hồi.
3. **Xử lý được ngay tại nơi gọi:** dùng `try/catch` có hành động hữu ích; kiểm thử cả nhánh thành công và thất bại.
4. **Không dùng exception làm nhánh điều khiển bình thường** cho dữ liệu dự kiến có thể kiểm tra đơn giản; kiểm tra đầu vào và dùng API báo kết quả thích hợp khi hợp đồng yêu cầu.
5. **Kiểm tra đầy đủ:** lỗi biên dịch (`unreported exception`), đường truyền ngoại lệ, thông điệp, cause và cách tài nguyên được đóng.

### Tự kiểm tra

- `throw` và `throws` khác nhau ở đâu? **`throw` ném đối tượng trong lúc chạy; `throws` khai báo loại ngoại lệ trong chữ ký.**
- Ném `IOException` khỏi một phương thức không có `try/catch` thì cần gì? **Khai báo `throws IOException` (hoặc loại supertype thích hợp) để compile hợp lệ.**
- Ném `IllegalArgumentException` có bắt buộc viết `throws` không? **Không**, vì unchecked.
- Chỉ thêm `throws IOException` có làm ngoại lệ biến mất không? **Không**. Nếu nó thực sự phát sinh, ngoại lệ sẽ truyền cho caller cho đến khi được xử lý hoặc không được bắt.
- Vì sao lưu `cause` khi ném ngoại lệ khác loại? **Để giữ bằng chứng của lỗi gốc** và giúp truy vết.

**Ghi nhớ:** `throw` là hành động báo lỗi; `throws` là cam kết về các checked exception có thể truyền ra theo hợp đồng phương thức. Viết đúng cả hai giúp xử lý lỗi có chủ đích và giữ thông tin chẩn đoán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-006">Nhận diện checked exception và unchecked exception</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện checked exception và unchecked exception — Java 17+

## 1. Hai nhóm ngoại lệ khác nhau ở quy tắc biên dịch

Trong Java, ngoại lệ (*exception*) biểu diễn tình huống bất thường trong khi thực thi: dữ liệu không hợp lệ, tài nguyên không sẵn có, trạng thái không cho phép thực hiện thao tác... Java chia các kiểu có thể ném thành **checked** và **unchecked** dựa trên **hệ phân cấp lớp**, chứ không dựa vào mức độ “nghiêm trọng” hoặc nội dung thông điệp.

- **Checked exception:** khi một lệnh có thể phát sinh kiểu checked, mã gọi phải **bắt (catch)** hoặc **khai báo chuyển tiếp (throws)** ngoại lệ theo quy tắc của trình biên dịch.
- **Unchecked exception:** trình biên dịch **không bắt buộc** người gọi phải catch hoặc khai báo throws; tuy nhiên ngoại lệ vẫn có thể được ném khi chạy, có stack trace và có thể được xử lý khi phù hợp.

~~~java
// IOException là checked: phải xử lý hoặc khai báo.
static String load(String key) throws java.io.IOException {
    if (key.isBlank()) {
        throw new java.io.IOException("empty key");
    }
    return key;
}

// IllegalArgumentException là unchecked.
static int requirePositive(int n) {
    if (n <= 0) {
        throw new IllegalArgumentException("n must be positive");
    }
    return n;
}
~~~

**Lưu ý:** `throws` không làm phương thức tự động ném ngoại lệ. Nó **khai báo khả năng ngoại lệ thoát ra** từ phương thức; `throw` mới thực hiện thao tác ném một đối tượng ngoại lệ cụ thể.

## 2. Nhận diện từ cây kế thừa

Trong Java 17, các kiểu có thể được ném bằng `throw` phải thuộc `Throwable` (hoặc lớp con).

~~~text
java.lang.Throwable
├── java.lang.Error                  [unchecked]
│   ├── VirtualMachineError         [unchecked]
│   │   ├── StackOverflowError      [unchecked]
│   │   └── OutOfMemoryError        [unchecked]
│   └── AssertionError              [unchecked]
└── java.lang.Exception
    ├── IOException                 [checked]
    ├── SQLException                [checked]
    ├── InterruptedException        [checked]
    ├── ClassNotFoundException      [checked]
    └── RuntimeException            [unchecked]
        ├── IllegalArgumentException
        │   └── NumberFormatException
        ├── IllegalStateException
        ├── NullPointerException
        ├── ArithmeticException
        └── IndexOutOfBoundsException
            └── ArrayIndexOutOfBoundsException
~~~

Quy tắc phân loại theo lớp gốc:

| Kiểu kế thừa từ | Nhóm | Nghĩa vụ compiler |
|---|---|---|
| `Exception` nhưng **không** nằm dưới `RuntimeException` | **Checked exception** | Phải catch hoặc declare (`throws`) nếu ngoại lệ có thể thoát ra |
| `RuntimeException` và mọi lớp con | **Unchecked exception** | Không bắt buộc catch/declare |
| `Error` và mọi lớp con | **Unchecked exception classes** | Không bắt buộc catch/declare, nhưng thường không nên xem là lỗi nghiệp vụ để phục hồi |
| `Throwable` trực tiếp hoặc các lớp con không thuộc nhánh `Error`/`RuntimeException` | **Checked** theo quy tắc Java | Catch hoặc declare nếu có thể thoát ra |

**Phân biệt quan trọng:** từ “unchecked exception classes” trong đặc tả Java bao gồm cả nhánh `Error`. Trong trao đổi thực tế, người ta thường gọi nhánh `RuntimeException` là “unchecked exceptions” và nói riêng “Errors”. `Error` **không phải** là lớp con của `RuntimeException`.

Một lớp tự định nghĩa kế thừa `Exception` (không qua `RuntimeException`) là checked; kế thừa `RuntimeException` là unchecked.

## 3. Các ví dụ nhận diện thường gặp

| Kiểu ngoại lệ | Nhóm | Tình huống thường gặp |
|---|---|---|
| `java.io.IOException` | Checked | Đọc/ghi dữ liệu vào I/O thất bại |
| `java.sql.SQLException` | Checked | Thao tác JDBC gặp lỗi |
| `java.lang.ClassNotFoundException` | Checked | Tải lớp theo tên không tìm được lớp |
| `java.lang.InterruptedException` | Checked | Luồng bị ngắt khi chờ/ngủ |
| `java.text.ParseException` | Checked | Một API phân tích chuỗi khai báo lỗi parse |
| `java.lang.NumberFormatException` | Unchecked | Chuỗi không chuyển được thành số qua `Integer.parseInt` |
| `java.lang.NullPointerException` | Unchecked | Dùng tham chiếu `null` ở thao tác yêu cầu object |
| `java.lang.IllegalArgumentException` | Unchecked | Đối số vi phạm quy tắc API |
| `java.lang.IllegalStateException` | Unchecked | Trạng thái object không cho phép hành vi |
| `java.lang.ArrayIndexOutOfBoundsException` | Unchecked | Truy cập chỉ số ngoài phạm vi mảng |
| `java.lang.StackOverflowError` | Unchecked (Error) | Stack không đủ cho các lời gọi lồng nhau |

Tên lớp có hậu tố `Exception` **không bảo đảm checked**: `NumberFormatException` vẫn unchecked. Ngược lại, một số lỗi như `OutOfMemoryError` không phải ngoại lệ nghiệp vụ dù không cần khai báo `throws`.

Không phải mọi kiểu “parse dữ liệu” đều ném checked exception: `Integer.parseInt("abc")` ném **unchecked `NumberFormatException`**. Hãy xem **chính xác chữ ký API và lớp ngoại lệ**, không đoán theo tình huống.

## 4. Checked: catch hoặc declare (`throws`)

Giả sử `Files.readString(Path.of("settings.txt"))` có thể ném `IOException` (checked). Phương thức sau là **mã cố ý sai để minh họa lỗi biên dịch**:

~~~java
// KHÔNG BIÊN DỊCH nếu giữ nguyên:
static String readWrong() {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("settings.txt"));
}
~~~

Vì checked `IOException` có thể thoát ra, compiler yêu cầu xử lý. Có hai hướng chính:

**A. Khai báo chuyển tiếp:**

~~~java
static String readFile() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("settings.txt"));
}
~~~

Caller của `readFile()` phải tiếp tục **catch hoặc declare**, cho đến một ranh giới xử lý thích hợp. `throws IOException` **không đọc file hộ**, không tự catch và không bảo đảm thao tác sẽ lỗi khi chạy.

**B. Bắt ngoại lệ tại vị trí cần xử lý:**

~~~java
static String readOrDefault() {
    try {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("settings.txt"));
    } catch (java.io.IOException ex) {
        return "DEFAULT";
    }
}
~~~

Khi file không tồn tại/không đọc được, nhánh catch trả giá trị dự phòng. Đoạn trên là minh họa API **có tương tác filesystem**, không phải ví dụ xác định stdout ở mục 9. Trong chương trình hoàn chỉnh, ta chủ động tạo ngoại lệ để kết quả giống nhau trên các máy.

**Không nên trả DEFAULT vô điều kiện trong mọi ứng dụng**: nếu không có mặc định hợp lệ, hãy thêm bối cảnh rồi chuyển tiếp lỗi hoặc xử lý phù hợp. Không nuốt lỗi để che sai sót nghiệp vụ.

## 5. Unchecked: compiler không ép catch/declare

~~~java
static int parseAge(String text) {
    int age = Integer.parseInt(text); // Có thể NumberFormatException.
    if (age < 0) {
        throw new IllegalArgumentException("age cannot be negative");
    }
    return age;
}
~~~

Phương thức trên **không cần** ghi `throws NumberFormatException` hoặc `throws IllegalArgumentException`. Tuy nhiên:

- `parseAge("20")` trả `20`.
- `parseAge("abc")` ném `NumberFormatException`.
- `parseAge("-2")` ném `IllegalArgumentException` do kiểm tra quy tắc.

Caller vẫn có thể catch ngoại lệ unchecked nếu lỗi là một kết quả dự kiến từ dữ liệu không đáng tin:

~~~java
try {
    int age = parseAge("abc");
    System.out.println(age);
} catch (NumberFormatException ex) {
    System.out.println("invalid number");
}
~~~

**Exact stdout:**

~~~text
invalid number
~~~

**Không có nghĩa unchecked = an toàn để bỏ qua.** Một lỗi unchecked không được bắt cũng có thể làm kết thúc luồng đang chạy và được handler mặc định thông báo bằng stack trace.

Một phương thức **được phép khai báo** `throws IllegalArgumentException` nhằm tài liệu hóa hợp đồng, nhưng đây không phải yêu cầu của compiler. Với các bug như `NullPointerException`, thường nên sửa nguyên nhân thay vì chỉ thêm catch chung chung.

## 6. Phân biệt `throw`, `throws`, `try` và `catch`

| Cú pháp | Vai trò | Ví dụ |
|---|---|---|
| `throw` | Ném một đối tượng `Throwable` cụ thể | `throw new IOException("failed");` |
| `throws` | Khai báo những ngoại lệ có thể thoát khỏi method/constructor | `void load() throws IOException` |
| `try` | Đánh dấu khối thực thi có thể phát sinh lỗi | `try { load(); }` |
| `catch` | Xử lý kiểu ngoại lệ khớp nhánh | `catch (IOException ex) { ... }` |
| `finally` | Khối dọn dẹp thường chạy khi rời try/catch | `finally { ... }` |

Chú ý mã Java phải có các import hoặc dùng tên đầy đủ cho `IOException`. Một ví dụ tự chứa về cú pháp:

~~~java
import java.io.IOException;

class Reader {
    static void run() throws IOException {
        throw new IOException("failed");
    }
}
~~~

Với checked exception, compiler sẽ kiểm tra việc **bắt hoặc khai báo** trên luồng điều khiển có thể thoát. Nó không yêu cầu mỗi method trong dự án phải có `throws`, cũng không bắt buộc catch tất cả nhánh unchecked.

Trong phương thức `main`, cũng có thể viết `public static void main(String[] args) throws IOException` để chuyển tiếp lỗi. Đây là cách biên dịch được nhưng không tự đem lại chính sách phục hồi hay thông báo thân thiện cho người dùng.

## 7. `catch` phải đúng quan hệ kế thừa và thứ tự

`NumberFormatException` là lớp con của `IllegalArgumentException`. Khi bắt nhiều loại liên quan, đưa loại **cụ thể trước**, loại **tổng quát sau**:

~~~java
try {
    Integer.parseInt("bad");
} catch (NumberFormatException ex) {
    System.out.println("invalid digits");
} catch (IllegalArgumentException ex) {
    System.out.println("other illegal argument");
}
~~~

**Exact stdout:**

~~~text
invalid digits
~~~

Đảo hai catch sẽ khiến nhánh `NumberFormatException` trở nên **không thể tới được**, do `IllegalArgumentException` đã bắt tất cả lớp con ấy. Compiler báo lỗi.

`catch (Exception ex)` có thể bắt checked/unchecked exceptions thuộc nhánh `Exception`, nhưng **không bắt `Error`**. `catch (Throwable ex)` bắt rộng hơn, thường không phù hợp cho xử lý nghiệp vụ thông thường. Chỉ dùng catch rộng tại ranh giới có chính sách rõ ràng (ghi log, chuyển đổi, đảm bảo cleanup), không che giấu những lỗi hệ thống khó phục hồi.

Đôi khi Java báo “exception is never thrown in body of corresponding try statement” khi catch một checked exception không thể phát sinh từ khối try theo phân tích của compiler. Không tạo catch checked “dự phòng” ở mọi nơi mà không có API phát sinh loại đó.

## 8. Checked không đồng nghĩa với luôn phục hồi được

Một exception được phân loại theo **kiểu lớp**, không theo việc xử lý có dễ hay không.

- **Checked `IOException`:** có thể do file không tồn tại, hết quyền truy cập hoặc sự cố I/O. Có thể retry/fallback hoặc chuyển tiếp tùy tình huống.
- **Unchecked `IllegalArgumentException`:** có thể được catch ở ranh giới nhập liệu để báo người dùng giá trị sai.
- **Unchecked `NullPointerException`:** thường biểu thị lỗi logic/hợp đồng ở một đoạn code; xử lý hiệu quả có thể là ngăn null từ sớm.
- **`Error`:** nhiều trường hợp biểu thị vấn đề VM/tài nguyên (như `StackOverflowError` và `OutOfMemoryError`) nên không coi `catch (Error)` là giải pháp mặc định.

Khi thiết kế API:

- Chọn **checked** cho điều kiện mà caller được yêu cầu xử lý hoặc quyết định chuyển tiếp.
- Chọn **unchecked** cho vi phạm hợp đồng, lập trình sai hoặc tình huống được API thiết kế trả về dưới dạng runtime exception.
- Không phải mọi thư viện đều chọn giống nhau; luôn đọc **chữ ký method, Javadoc và tài liệu hợp đồng**.

Một method khai báo `throws IOException` không chắc chắn **sẽ ném** exception mỗi lần gọi; chỉ nói nó **có khả năng thoát ra** theo hợp đồng và compiler sẽ kiểm tra.

## 9. Ví dụ Java 17+ chạy độc lập

Tạo file đúng tên **`CheckedUncheckedDemo.java`**:

~~~java
import java.io.IOException;

public final class CheckedUncheckedDemo {
    private CheckedUncheckedDemo() {}

    static String loadFromSource(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("missing configuration key");
        }
        return "value-" + key.strip();
    }

    // Khai báo chuyển tiếp IOException checked.
    static String prepare(String key) throws IOException {
        return loadFromSource(key) + "!";
    }

    // Bắt checked exception ở một ranh giới có fallback phù hợp.
    static String loadOrDefault(String key) {
        try {
            return prepare(key);
        } catch (IOException ex) {
            return "DEFAULT";
        }
    }

    // Unchecked: không bắt buộc khai báo throws.
    static int parseAge(String raw) {
        int age = Integer.parseInt(raw);
        if (age < 0) {
            throw new IllegalArgumentException("age cannot be negative");
        }
        return age;
    }

    static void assertReady(boolean ready) {
        if (!ready) {
            throw new IllegalStateException("service not ready");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("checked success=" + prepare(" config "));
        } catch (IOException ex) {
            System.out.println("checked unexpected failure");
        }

        System.out.println("checked fallback=" + loadOrDefault(""));

        try {
            prepare("");
        } catch (IOException ex) {
            System.out.println("checked caught="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        System.out.println("unchecked success=" + parseAge("20"));
        try {
            parseAge("abc");
        } catch (NumberFormatException ex) {
            System.out.println("unchecked parse="
                    + ex.getClass().getSimpleName());
        }

        try {
            parseAge("-2");
        } catch (IllegalArgumentException ex) {
            System.out.println("unchecked validation="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        try {
            assertReady(false);
        } catch (IllegalStateException ex) {
            System.out.println("unchecked state="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        System.out.println("checked parent="
                + IOException.class.getSuperclass().getSimpleName());
        System.out.println("unchecked parent="
                + IllegalStateException.class.getSuperclass().getSimpleName());
        System.out.println("program continues=true");
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 CheckedUncheckedDemo.java
java -cp . CheckedUncheckedDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\CheckedUncheckedDemo.java
java -cp . CheckedUncheckedDemo
~~~

Ví dụ **không truy cập filesystem**, mạng hoặc cơ sở dữ liệu; checked `IOException` được ném trực tiếp có kiểm soát. Không cần Maven/Gradle, thư viện ngoài hoặc preview features.

### Exact expected stdout

~~~text
checked success=value-config!
checked fallback=DEFAULT
checked caught=IOException:missing configuration key
unchecked success=20
unchecked parse=NumberFormatException
unchecked validation=IllegalArgumentException:age cannot be negative
unchecked state=IllegalStateException:service not ready
checked parent=Exception
unchecked parent=RuntimeException
program continues=true
~~~

**Đọc kết quả:**

1. Với key hợp lệ, `prepare(...)` trả chuỗi bình thường, dù method **có khai báo** `throws IOException`.
2. Với key rỗng, `loadFromSource(...)` ném checked `IOException`; `loadOrDefault(...)` bắt để trả `DEFAULT`. Một lời gọi khác bắt lỗi và in loại + thông điệp.
3. `parseAge("abc")` tạo unchecked `NumberFormatException`; `parseAge("-2")` ném unchecked `IllegalArgumentException`. Hai loại vẫn có thể catch bình thường.
4. `assertReady(false)` ném unchecked `IllegalStateException`.
5. Hai dòng `parent` cho thấy `IOException` kế thừa trực tiếp `Exception` và `IllegalStateException` kế thừa trực tiếp `RuntimeException`.
6. Vì các ngoại lệ đều được xử lý có chủ đích, chương trình chạy đến dòng `program continues=true`.

## 10. Tự định nghĩa checked và unchecked exception

Một class tùy chỉnh có thể phân loại qua **lớp cha**, không cần đăng ký với JVM.

~~~java
// Minh họa độc lập trong phạm vi cùng package:
class MissingRecordException extends Exception {
    MissingRecordException(String message) {
        super(message);
    }
}

class InvalidRecordStateException extends RuntimeException {
    InvalidRecordStateException(String message) {
        super(message);
    }
}
~~~

- `MissingRecordException extends Exception` → checked. Phương thức có thể ném kiểu này phải tuân thủ quy tắc catch/declare.
- `InvalidRecordStateException extends RuntimeException` → unchecked. Compiler không ép caller catch/declare.
- Cả hai vẫn là `Throwable`; cả hai có thể đi kèm thông điệp/cause, được in stack trace và được catch tương ứng.

Khi bọc lỗi để giữ nguyên nguyên nhân, có thể gọi constructor như `new IOException("cannot load config", cause)`. Đừng làm mất `cause` khi chuyển đổi exception nếu hệ thống cần chẩn đoán nguồn gốc lỗi.

## 11. `try-with-resources` không thay quy tắc checked

Nhiều API I/O yêu cầu đóng resource, ví dụ:

~~~java
static String firstLine(java.nio.file.Path path)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 java.nio.file.Files.newBufferedReader(path)) {
        return reader.readLine();
    }
}
~~~

Đây là **mã minh họa method**, không chạy trong demo không phụ thuộc môi trường ở mục 9.

`try-with-resources` bảo đảm tài nguyên được quản lý đóng khi rời khối, kể cả khi có lỗi, nhưng hoạt động đọc/đóng **vẫn có thể ném checked `IOException`**. Vì vậy, ví dụ khai báo `throws IOException` thay vì giả định try-with-resources đã “xử lý mọi lỗi”.

Nếu dùng `InterruptedException` từ `Thread.sleep(...)`, không nên nuốt tín hiệu ngắt. Tùy hợp đồng, có thể chuyển tiếp `throws InterruptedException` hoặc khi bắt để phục hồi trạng thái thì khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` theo yêu cầu thiết kế.

## 12. Những nhầm lẫn phổ biến

| Nhầm lẫn | Hiểu đúng |
|---|---|
| Tên kết thúc `Exception` chắc chắn là checked | `RuntimeException` và các lớp con như `NumberFormatException` là unchecked |
| Unchecked nghĩa là không bao giờ ném khi chạy | Vẫn được ném và có thể làm dừng luồng nếu không được xử lý |
| Checked nghĩa là lúc chạy luôn có lỗi | Compiler yêu cầu xử lý/khai báo khả năng; đường chạy hợp lệ có thể không ném |
| `throws` tự ném ngoại lệ | `throws` chỉ khai báo; `throw` ném |
| Catch unchecked là không hợp lệ | Catch được khi phù hợp, chỉ là compiler không bắt buộc |
| Thấy `Error` liền coi là checked vì khác `RuntimeException` | Các lớp con `Error` cũng unchecked theo phân loại Java |
| `catch (Exception)` bắt luôn `OutOfMemoryError` | `Error` không kế thừa `Exception` |
| Thêm `catch (IOException)` quanh mọi lệnh cho chắc | Compiler có thể từ chối catch checked không thể phát sinh |
| Catch `IllegalArgumentException` rồi đến `NumberFormatException` | Nhánh con phía sau không thể tới được; đảo thứ tự |
| `throws Exception` luôn là thiết kế tốt | Quá rộng, che mất hợp đồng ngoại lệ cụ thể; dùng kiểu phù hợp |
| Catch và bỏ trống để ứng dụng khỏi crash | Có thể che giấu lỗi; xử lý, log hoặc chuyển tiếp có chủ đích |
| `finally` tự biến ngoại lệ thành thành công | Cleanup không đồng nghĩa với xử lý nguyên nhân |

## 13. Bài tập có đáp án

**A.** Phân loại `IOException`, `SQLException`, `IllegalStateException`, `NumberFormatException`, `StackOverflowError`.

**Đáp án:** `IOException` và `SQLException` **checked**; `IllegalStateException` và `NumberFormatException` **unchecked RuntimeException**; `StackOverflowError` là **unchecked Error**.

**B.** Vì sao lời gọi `Files.readString(Path.of("a.txt"))` trong method không catch/không throws báo lỗi compiler?

**Đáp án:** vì API có thể ném checked `IOException`. Caller phải catch hoặc thêm `throws IOException` (hoặc siêu kiểu checked hợp lệ) trong khai báo.

**C.** Khi gọi `Integer.parseInt("bad")`, có cần khai báo `throws NumberFormatException` không?

**Đáp án:** **Không**, `NumberFormatException` nằm dưới `RuntimeException`. Caller có thể catch nếu đầu vào không hợp lệ là trường hợp dự kiến.

**D.** Một class `class MyFailure extends Exception` là checked hay unchecked? Nếu `extends RuntimeException` thì sao?

**Đáp án:** `extends Exception` trực tiếp là **checked**, còn `extends RuntimeException` là **unchecked**.

**E.** Tại sao phải catch `NumberFormatException` trước `IllegalArgumentException` nếu cả hai đều có catch riêng?

**Đáp án:** vì `NumberFormatException` là lớp con; bắt cha trước sẽ khiến bắt con sau **không thể tới được**.

**F.** `catch (Exception ex)` có bắt được `StackOverflowError` không?

**Đáp án:** **Không**, `StackOverflowError` kế thừa `Error`, không phải `Exception`.

**G.** Khi hàm khai báo `throws IOException` nhưng nhận dữ liệu hợp lệ và không có lỗi nào, có nhất định ném IOException không?

**Đáp án:** **Không**. `throws` chỉ là khai báo một khả năng; ví dụ `prepare(" config ")` trả `value-config!`.

## 14. Checklist và kết luận

- [ ] Xác định checked/unchecked bằng **hệ phân cấp `Throwable`**, không dựa vào tên/độ nghiêm trọng.
- [ ] Biết `Exception` (không nằm dưới `RuntimeException`) thường là checked.
- [ ] Biết nhánh `RuntimeException` và nhánh `Error` đều unchecked theo compiler.
- [ ] Đọc được `throws IOException` trong chữ ký phương thức.
- [ ] Phân biệt `throws` khai báo với `throw` ném.
- [ ] Biết hai cách xử lý checked: `try-catch` hoặc tiếp tục khai báo `throws`.
- [ ] Hiểu unchecked vẫn có thể bắt nhưng không bị compiler buộc bắt.
- [ ] Sắp xếp các `catch` theo thứ tự kiểu cụ thể trước kiểu tổng quát.
- [ ] Không nhầm `catch (Exception)` với bắt tất cả `Throwable`.
- [ ] Giữ cause/stack trace khi chuyển tiếp hoặc bọc lỗi cần chẩn đoán.
- [ ] Không dùng try-with-resources như lý do bỏ qua checked I/O exceptions.
- [ ] Biên dịch, chạy `CheckedUncheckedDemo.java` trên JDK 17+ và kiểm tra exact stdout.

**Kết luận:** **Checked exception** buộc phía gọi **bắt hoặc khai báo** khả năng ngoại lệ theo quy tắc biên dịch Java. **Unchecked** thuộc nhánh `RuntimeException` hoặc `Error` và không có nghĩa vụ compile-time ấy, nhưng vẫn cần thiết kế xử lý lỗi hợp lý. Hãy nhìn **kiểu kế thừa**, **chữ ký API** và **hợp đồng ứng dụng** trước khi quyết định catch, throws hay sửa logic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-007">finally và giới thiệu try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# `finally` và giới thiệu `try-with-resources` trong Java

## 1. Vì sao cần dọn dẹp khi thao tác có thể thất bại?

Một chương trình Java có thể mở tệp, kết nối mạng, sử dụng stream hoặc tài nguyên khác. Các thao tác ấy có thể hoàn tất bình thường, ném ngoại lệ hoặc thoát sớm bằng `return`. Nếu việc dọn dẹp chỉ đặt ở cuối nhánh thành công, một nhánh thất bại có thể bỏ qua nó.

Java cung cấp hai cơ chế liên quan:

- **`finally`**: khối mã có thể được dùng để thực hiện công việc khi rời cấu trúc `try`/`catch`, kể cả khi `try` gặp ngoại lệ hoặc `return`.
- **`try-with-resources`**: dạng `try` chuyên quản lý tài nguyên triển khai `AutoCloseable`; Java tự gọi `close()` khi rời khối, xử lý cả tình huống có ngoại lệ.

Đừng đồng nhất chúng: `finally` là cơ chế tổng quát; `try-with-resources` là lựa chọn nên ưu tiên để **đóng tài nguyên** có API `close()`.

## 2. Cấu trúc và luồng chạy của `finally`

Cú pháp:

```java
try {
    // Thao tác có thể thất bại
} catch (SomeException ex) {
    // Xử lý ngoại lệ phù hợp
} finally {
    // Công việc khi rời try/catch
}
```

Có thể viết `try-finally` không có `catch`:

```java
try {
    doWork();
} finally {
    cleanUp();
}
```

Hai tên `doWork()` và `cleanUp()` ở đoạn trên chỉ là **phương thức minh họa**, không phải chương trình độc lập. Với dạng `try` thông thường (không có khai báo tài nguyên), Java yêu cầu ít nhất một `catch` hoặc `finally`.

### Những đường đi phổ biến

| Điều xảy ra trong `try` | `catch` | `finally` |
| --- | --- | --- |
| Hoàn tất bình thường | Bỏ qua `catch` | Chạy trước khi đi tiếp |
| Ném ngoại lệ được `catch` phù hợp xử lý | Chạy khối `catch` đó | Chạy sau `catch` |
| Ném ngoại lệ không được bắt tại đây | Không có khối phù hợp | Thông thường vẫn chạy trước khi ngoại lệ lan ra ngoài |
| `return`, `break` hoặc `continue` làm rời cấu trúc | Tùy tình huống | Thông thường chạy trước khi thực hiện việc rời khối |

Ví dụ có `return`:

```java
static int calculate() {
    try {
        System.out.println("Trong try");
        return 7;
    } finally {
        System.out.println("Trong finally");
    }
}
```

Khi gọi `calculate()`, chương trình in `Trong try`, sau đó `Trong finally`, rồi mới trả giá trị `7` về nơi gọi. Trong `finally`, ta vẫn có thể dùng biến đang nằm trong scope; tuy nhiên **không nên đổi luồng điều khiển bằng `return` ở `finally`**.

### `finally` không phải bảo đảm tuyệt đối

Trong điều kiện thực thi Java thông thường, `finally` được thực hiện khi rời `try` hoặc `catch`. Nhưng không thể cam kết nó chạy trong **mọi** trường hợp: ví dụ khi tiến trình/JVM bị chấm dứt đột ngột, có lời gọi `System.exit(...)` dẫn tới chấm dứt VM, hoặc luồng không bao giờ rời khỏi đoạn mã đang chạy.

Vì vậy, không nên dùng `finally` để bảo đảm độ bền dữ liệu khi tiến trình có thể mất nguồn điện hay bị kill. Tùy bài toán, cần thiết kế lưu trữ, transaction và cơ chế khôi phục riêng.

## 3. `finally` có thể che mất nguyên nhân lỗi nếu viết sai

Đây là lỗi đáng chú ý:

```java
static int misleading() {
    try {
        return 10;
    } finally {
        return 99; // KHÔNG NÊN: che giá trị return trước
    }
}
```

Dù `try` có `return 10`, kết quả khi gọi `misleading()` lại là `99`. Nếu `try` ném ngoại lệ mà `finally` thực hiện `return`, ngoại lệ ban đầu cũng có thể bị che mất. Tương tự, nếu `finally` ném một ngoại lệ mới, ngoại lệ đó có thể thay thế ngoại lệ đang lan từ `try`.

Khi cần đóng một tài nguyên và cả thao tác chính lẫn `close()` đều có thể thất bại, tự gọi `close()` trong `finally` dễ làm khó việc giữ lại **ngoại lệ gốc**. Đây là một lý do quan trọng để dùng `try-with-resources`.

## 4. `try-with-resources` là gì?

Từ Java 7, có thể khai báo các tài nguyên trong phần ngoặc của `try`:

```java
try (Resource resource = acquireResource()) {
    use(resource);
}
```

Các tên `Resource`, `acquireResource()` và `use(...)` ở đây biểu diễn **mẫu cú pháp**, không phải API có sẵn. Trong mã chạy thực tế, kiểu tài nguyên phải triển khai `java.lang.AutoCloseable` (hoặc interface con phù hợp như `java.io.Closeable`).

Khi rời khối, Java sẽ gọi `close()` trên tài nguyên đã được khởi tạo thành công và không phải `null`, ngay cả khi:

- Khối `try` thực thi thành công.
- Khối `try` ném ngoại lệ.
- Luồng điều khiển dùng `return` hoặc đi ra ngoài khối theo cơ chế bình thường của ngôn ngữ.

Ví dụ với API chuẩn:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FirstLineReader {
    static String readFirstLine(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.readLine();
        }
    }
}
```

Đây là **lớp minh họa** không có `main`. `BufferedReader` triển khai `Closeable`, nên được tự đóng sau khi đọc, kể cả lúc `readLine()` ném `IOException` hoặc có `return`. Phương thức vẫn khai báo `throws IOException`: tự đóng tài nguyên **không có nghĩa lỗi I/O được tự động xử lý và biến mất**.

Không nên nhầm cơ chế đóng này với Garbage Collector. GC quản lý đối tượng Java có thể thu gom, nhưng thường không phải giải pháp để đóng file, socket hoặc kết nối đúng thời điểm.

## 5. Nhiều tài nguyên: đóng theo thứ tự ngược

Có thể khai báo nhiều tài nguyên, ngăn cách bằng dấu chấm phẩy:

```java
try (First first = new First();
     Second second = new Second()) {
    // Sử dụng cả hai tài nguyên
}
```

Các lớp `First` và `Second` trong ví dụ trên chưa được định nghĩa; đây là cú pháp minh họa. Nếu cả hai tài nguyên được tạo thành công, chúng được **đóng ngược với thứ tự khởi tạo**: `second.close()` trước, `first.close()` sau. Nếu tài nguyên thứ hai khởi tạo thất bại, các tài nguyên đã được tạo trước đó vẫn được đóng theo quy tắc try-with-resources.

Thứ tự này tương tự cách dọn dẹp một ngăn xếp tài nguyên. Nó hữu ích khi tài nguyên tạo sau phụ thuộc tài nguyên tạo trước.

Từ **Java 9**, có thể đưa một biến tài nguyên đã tồn tại vào phần ngoặc nếu đó là biến `final` hoặc *effectively final*:

```java
BufferedReader reader = Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
```

Đây là đoạn minh họa trong phương thức có thể xử lý/khai báo `IOException` và có các import tương ứng. `reader` không được gán lại sau khi khởi tạo trong trường hợp này. Việc đưa biến vào `try (reader)` không tự tạo bản sao tài nguyên hay một đối tượng reader mới: nó quản lý chính đối tượng đó để đóng khi rời khối.

## 6. Ngoại lệ phát sinh lúc đóng và `getSuppressed()`

`AutoCloseable.close()` **có thể ném ngoại lệ**. Có hai tình huống cần phân biệt.

### Chỉ thao tác đóng thất bại

Nếu thân `try` hoàn thành bình thường nhưng `close()` ném ngoại lệ, ngoại lệ từ việc đóng có thể lan ra ngoài hoặc được bắt bởi `catch` tương ứng.

### Cả thân `try` lẫn thao tác đóng đều thất bại

Java ưu tiên giữ **ngoại lệ chính từ thân `try`** làm ngoại lệ được ném ra. Ngoại lệ xảy ra khi tự động đóng tài nguyên được gắn vào ngoại lệ chính dưới dạng **suppressed exception**, có thể đọc bằng:

```java
Throwable[] otherFailures = ex.getSuppressed();
```

Điều này tránh tình huống ngoại lệ phát sinh khi `close()` vô tình xóa mất nguyên nhân ban đầu. Với nhiều tài nguyên, có thể có nhiều suppressed exceptions; số lượng và thứ tự phụ thuộc những tài nguyên nào thực sự ném lỗi lúc đóng.

Lưu ý: `getSuppressed()` trả về mảng và có thể **rỗng**; không truy cập `getSuppressed()[0]` nếu chưa kiểm tra độ dài. Chương trình ở mục tiếp theo dùng tình huống cố định để biết chắc có một ngoại lệ phụ.

## 7. Ví dụ Java 17+ độc lập: `finally`, thứ tự đóng và suppressed exception

Tạo tệp **`FinallyResourcesDemo.java`**:

```java
public class FinallyResourcesDemo {
    static class Tracked implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Tracked(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("open " + name);
        }

        void work() {
            System.out.println("work " + name);
        }

        @Override
        public void close() throws Exception {
            System.out.println("close " + name);
            if (failOnClose) {
                throw new Exception("close failed " + name);
            }
        }
    }

    static int returnWithFinally() {
        try {
            System.out.println("try return");
            return 7;
        } finally {
            System.out.println("finally return");
        }
    }

    static void reverseCloseOrder() throws Exception {
        try (Tracked a = new Tracked("A", false);
             Tracked b = new Tracked("B", false)) {
            System.out.println("work pair");
        }
    }

    static void demonstrateSuppressed() {
        try (Tracked c = new Tracked("C", true)) {
            c.work();
            throw new IllegalStateException("body failed");
        } catch (Exception ex) {
            System.out.println("caught: " + ex.getMessage());
            System.out.println("suppressed: " + ex.getSuppressed().length);
            if (ex.getSuppressed().length > 0) {
                System.out.println("detail: "
                        + ex.getSuppressed()[0].getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("try normal");
        } finally {
            System.out.println("finally normal");
        }

        System.out.println("result: " + returnWithFinally());
        reverseCloseOrder();
        demonstrateSuppressed();

        try (Tracked d = new Tracked("D", true)) {
            d.work();
        } catch (Exception ex) {
            System.out.println("caught close: " + ex.getMessage());
        }

        System.out.println("done");
    }
}
```

Mở terminal tại thư mục chứa tệp, dùng **JDK 17 trở lên**. Chạy trên PowerShell/CMD (Windows) hoặc bash/zsh (Linux/macOS):

```bash
javac -encoding UTF-8 FinallyResourcesDemo.java
java FinallyResourcesDemo
```

**Stdout chính xác** (lệnh `javac` biên dịch thành công không in thông báo):

```text
try normal
finally normal
try return
finally return
result: 7
open A
open B
work pair
close B
close A
open C
work C
close C
caught: body failed
suppressed: 1
detail: close failed C
open D
work D
close D
caught close: close failed D
done
```

**Giải thích theo trình tự:**

1. `try normal` hoàn tất bình thường nên `finally normal` chạy tiếp.
2. `returnWithFinally()` có `return 7` trong `try`; `finally` in thông báo trước khi `result: 7` xuất hiện ở nơi gọi.
3. Hai tài nguyên `A` và `B` được mở theo thứ tự `A`, `B`, nhưng đóng ngược lại: `B`, `A`.
4. Tài nguyên `C` ném lỗi trong `close()`; đồng thời thân `try` ném `IllegalStateException("body failed")`. Ngoại lệ chính được bắt với thông điệp `body failed`; ngoại lệ `close failed C` được lưu là một suppressed exception.
5. Với tài nguyên `D`, thân `try` bình thường; **chỉ `close()` thất bại**, nên `catch` nhận trực tiếp ngoại lệ `close failed D`.
6. Chương trình vẫn in `done` vì cả hai tình huống thất bại có `catch` xử lý; không có `catch` rỗng hoặc việc bỏ qua lỗi một cách âm thầm.

Với tài nguyên `Tracked`, chữ ký `close() throws Exception` là hợp lệ vì `AutoCloseable.close()` cho phép khai báo `Exception`. Do vậy `reverseCloseOrder()` khai báo `throws Exception`, còn `main` cũng khai báo `throws Exception` để chuyển tiếp nếu có thất bại không được xử lý ở đường đi đó. Trong ví dụ, `A` và `B` không ném lỗi nên stdout đã nêu là xác định.

## 8. `try-with-resources` và `catch`/`finally` có thể kết hợp

Cú pháp hợp lệ:

```java
try (Resource resource = open()) {
    use(resource);
} catch (SomeException ex) {
    report(ex);
} finally {
    finish();
}
```

Đây là sơ đồ minh họa với các tên giả `Resource`, `open`, `use`, `report`, `finish`, không phải chương trình độc lập.

Thứ tự về bản chất là:

1. Khởi tạo các tài nguyên từ trái sang phải.
2. Thực thi phần thân `try`.
3. Tự động đóng tài nguyên đã khởi tạo theo thứ tự ngược.
4. Nếu có ngoại lệ cần xử lý, thực hiện `catch` phù hợp gắn với cấu trúc đó.
5. Thực hiện `finally` nếu có, theo quy tắc ngôn ngữ.

Như vậy, **tài nguyên đã được tự đóng trước khi code trong `catch` và `finally` gắn với `try-with-resources` thực thi**. Nếu muốn dùng tài nguyên để ghi thêm dữ liệu trong `catch`, phải thiết kế lại phạm vi/lifecycle; không thể giả định tài nguyên của `try (...)` vẫn đang mở.

Một `try-with-resources` có thể đứng riêng không cần `catch` hay `finally`. Ngược lại, `try` thông thường (không có resource specification) phải có ít nhất `catch` hoặc `finally`.

## 9. Khi nào nên dùng cơ chế nào?

| Nhu cầu | Cách ưu tiên |
| --- | --- |
| Đọc/ghi file qua `BufferedReader`, `InputStream`, `Writer` | `try-with-resources` vì các tài nguyên hỗ trợ đóng |
| Quản lý `java.sql.Connection`, `Statement`, `ResultSet` | `try-with-resources` nếu cần đóng đúng phạm vi |
| Giải phóng tài nguyên tự định nghĩa có `close()` | Triển khai `AutoCloseable` cẩn thận rồi dùng `try-with-resources` |
| Ghi log/điểm đo sau một nhánh `try`/`catch` | Cân nhắc `finally` hoặc cấu trúc rõ ràng tương đương |
| Cần kiểm tra và xử lý lỗi đầu vào | `catch` đúng loại ngoại lệ; `finally` không thay thế `catch` |
| Cần trả kết quả từ phương thức | Dùng `return` đúng chỗ; không đặt `return` trong `finally` để che đường đi khác |

`AutoCloseable` chỉ cung cấp hợp đồng có phương thức `close()`; nó **không mặc nhiên bảo đảm** tài nguyên an toàn khi chia sẻ giữa nhiều luồng hoặc có thể sử dụng lại sau khi đóng. Những điều đó phụ thuộc API thực tế.

## 10. Lỗi thường gặp và cách tự kiểm tra

1. **Cho rằng `finally` sẽ bắt ngoại lệ.** `finally` chỉ chạy công việc khi rời cấu trúc; muốn xử lý ngoại lệ phải có `catch` phù hợp hoặc cho lan đến tầng khác.
2. **Đặt `return` trong `finally`.** Có thể ghi đè giá trị trả về hoặc che ngoại lệ; tránh cách viết này.
3. **Tự đóng tài nguyên trong `finally` nhưng bỏ qua ngoại lệ ở `close()`.** Có thể mất thông tin lỗi gốc; dùng `try-with-resources` khi được hỗ trợ.
4. **Cho rằng `try-with-resources` tự nuốt `IOException`.** Ngoại lệ vẫn cần bắt hoặc khai báo `throws` thích hợp.
5. **Dùng đối tượng không triển khai `AutoCloseable` trong `try (...)`.** Đây là lỗi biên dịch; cần kiểu tài nguyên hợp lệ.
6. **Đọc `getSuppressed()[0]` mà không kiểm tra độ dài.** Có thể gây `ArrayIndexOutOfBoundsException` nếu không có ngoại lệ phụ.
7. **Giả định đóng tài nguyên theo thứ tự khai báo.** Với nhiều tài nguyên, thứ tự đóng là **ngược lại**.
8. **Dùng tài nguyên đã đóng sau khi rời `try-with-resources`.** Nhiều API sẽ ném ngoại lệ hoặc không còn cho phép thao tác; cần tránh phụ thuộc vào trạng thái đã đóng.
9. **Dựa vào `finally` khi VM bị kill hoặc máy tắt.** Không có bảo đảm dọn dẹp trong các tình huống mất khả năng thực thi Java.
10. **Nhầm tài nguyên hệ điều hành với bộ nhớ Java.** Garbage Collector không phải một cơ chế đóng file/socket đúng thời điểm.

**Ghi nhớ:** `finally` dành cho các bước phải thực hiện khi rời đường thực thi bình thường của `try`/`catch`; `try-with-resources` là công cụ chuyên biệt để đóng `AutoCloseable` an toàn hơn, kể cả khi `try` ném ngoại lệ. Khi cả thao tác chính và `close()` thất bại, xem `getSuppressed()` để không bỏ sót lỗi phụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-008">assert để kiểm tra giả định nội bộ và bật bằng -ea</a>

<details>
<summary>Xem chi tiết</summary>

# `assert` để kiểm tra giả định nội bộ và bật bằng `-ea` (Java 17+)

## 1. `assert` là gì?

`assert` là câu lệnh Java dùng để kiểm tra một **giả định mà lập trình viên tin phải luôn đúng** nếu chương trình hoạt động đúng. Nó đặc biệt hữu ích để phát hiện vi phạm bất biến (*invariant*), hậu điều kiện của thuật toán hoặc trạng thái nội bộ không thể xảy ra theo thiết kế.

Nếu kiểm tra được **bật** và điều kiện sai, Java ném `java.lang.AssertionError`. Nếu điều kiện đúng, chương trình đi tiếp. Với phần lớn ứng dụng Java thông thường, assertions **mặc định tắt khi chạy**, vì vậy câu lệnh `assert` bị bỏ qua trừ khi bật bằng tùy chọn JVM như `-ea` (*enable assertions*).

Ví dụ ý tưởng:

```java
assert index >= 0 : "negative internal index: " + index;
```

Câu trên phù hợp nếu `index` là giá trị **nội bộ** được suy ra từ thuật toán vốn phải không âm. Nó **không phù hợp** làm lớp kiểm tra đầu vào duy nhất cho tham số `index` do người dùng gửi đến, vì khi assertions tắt thì phép kiểm tra không chạy.

**Phân biệt:** `assert` là câu lệnh thuộc ngôn ngữ Java, không phải phương thức `assertEquals` của JUnit và cũng không phải `throw new IllegalArgumentException(...)`. Các phương thức assertion trong framework kiểm thử hoạt động theo cơ chế riêng của framework.

## 2. Cú pháp và cơ chế đánh giá

Java có hai dạng cú pháp:

```java
assert booleanExpression;
assert booleanExpression : detailExpression;
```

- `booleanExpression` phải cho kết quả kiểu `boolean` hoặc `Boolean` (có thể được unbox). Chẳng hạn `assert count >= 0;` hợp lệ, còn `assert count;` sai nếu `count` là `int`.
- `detailExpression` là biểu thức trả về một giá trị (không được là lời gọi phương thức có kiểu kết quả `void`); giá trị đó giúp xây dựng thông tin của `AssertionError`. Không có nghĩa là câu lệnh `println` luôn được thực thi.
- Khi assertions **được bật**: Java đánh giá điều kiện; nếu `false`, nó đánh giá thêm biểu thức sau `:` (nếu có) rồi ném `AssertionError` với thông tin tương ứng.
- Khi assertions **tắt**: cả điều kiện lẫn biểu thức thông tin đều **không được đánh giá**. Do đó không nên đặt tác dụng phụ (*side effect*) trong assertion.
- Nếu điều kiện là `Boolean` có giá trị `null`, việc unbox trong lúc assertion được đánh giá có thể phát sinh `NullPointerException`. Tránh dùng một giá trị nullable làm điều kiện trực tiếp.

Trong Java 17, `assert` là từ khóa, không thể dùng để đặt tên phương thức hoặc biến. Câu lệnh được kiểm tra cú pháp và kiểu **khi biên dịch** kể cả khi assertions sẽ bị tắt ở lúc chạy.

Ví dụ khi assertion được bật:

```java
int count = 2;
assert count >= 0;                        // đúng: tiếp tục
assert count % 2 != 0 : "count is even"; // sai: ném AssertionError với thông điệp
```

Đây là **đoạn minh họa**, không phải tệp chương trình độc lập; ví dụ chạy hoàn chỉnh ở phần tiếp theo.

## 3. Ví dụ đầy đủ: nhìn thấy khác biệt khi có và không có `-ea`

Tạo tệp **`AssertDemo.java`**:

```java
public class AssertDemo {
    private static int halfEven(int number) {
        assert number % 2 == 0 : "even input required: " + number;
        return number / 2;
    }

    public static void main(String[] args) {
        System.out.println("enabled=" + AssertDemo.class.desiredAssertionStatus());
        System.out.println("half(8)=" + halfEven(8));

        try {
            System.out.println("half(5)=" + halfEven(5));
        } catch (AssertionError error) {
            System.out.println("caught=" + error.getClass().getSimpleName());
            System.out.println("detail=" + error.getMessage());
        }
    }
}
```

Từ thư mục chứa tệp (JDK 17+; chạy được trên PowerShell, CMD, bash), tạo thư mục `out` nếu chưa có, rồi biên dịch:

```sh
mkdir out
javac -encoding UTF-8 --release 17 -d out AssertDemo.java
```

### Chạy bình thường: assertions tắt

```sh
java -cp out AssertDemo
```

**Stdout chính xác** khi không có cấu hình bật assertion bổ sung:

```text
enabled=false
half(8)=4
half(5)=2
```

`halfEven(5)` trả `2` do phép chia số nguyên, vì dòng `assert` không được thực thi. Ví dụ cố tình dùng số lẻ để chỉ ra lý do **không được lấy assert làm kiểm tra dữ liệu bắt buộc**.

### Chạy với `-ea`: assertions bật

```sh
java -ea -cp out AssertDemo
```

**Stdout chính xác:**

```text
enabled=true
half(8)=4
caught=AssertionError
detail=even input required: 5
```

Lần gọi với `8` thỏa điều kiện và trả `4`. Lần gọi với `5` ném `AssertionError` nên phần `System.out.println("half(5)=" + ...)` không in dòng giá trị. Khối `catch (AssertionError)` ở đây chỉ dùng **để biểu diễn kết quả cố định cho người học**; trong ứng dụng thật, assertion thất bại thường báo một lỗi lập trình cần được sửa, không nên bị bắt để giả vờ tiếp tục bình thường.

`AssertDemo.class.desiredAssertionStatus()` báo cấu hình mong muốn cho lớp; trong ví dụ chạy bằng launcher Java chuẩn, nó khớp với trạng thái assertions thực tế. Trong chương trình có class loader tự điều chỉnh assertion status, không nên xem phương thức này là cơ chế kiểm tra lỗi nghiệp vụ.

**Chú ý lệnh:** `-ea` là tùy chọn của **`java` lúc chạy**, không phải tùy chọn của `javac`. Không cần biên dịch lại giữa hai lần chạy; một file `.class` có thể cho hai hành vi vì thiết lập JVM khác nhau.

## 4. Bật/tắt assertions theo lớp và package

Các tùy chọn thường dùng với `java`:

| Tùy chọn | Tác dụng |
| --- | --- |
| `-ea` hoặc `-enableassertions` | Bật assertions cho các lớp ứng dụng thông thường theo phạm vi mặc định của JVM |
| `-da` hoặc `-disableassertions` | Tắt assertions cho các lớp theo phạm vi tương ứng |
| `-ea:lesson.AssertDemo` | Bật cho một lớp có tên đầy đủ `lesson.AssertDemo` |
| `-ea:lesson...` | Bật cho package `lesson` và các package con của nó |
| `-da:lesson.Legacy` | Tắt cho một lớp cụ thể |
| `-esa` hoặc `-enablesystemassertions` | Bật assertions trong các lớp hệ thống; thường không cần cho chương trình mới học |

Tên lớp và package trong tham số phải khớp **tên đầy đủ thực tế**. Ví dụ `AssertDemo` ở phần 3 thuộc unnamed package, vì vậy bật riêng bằng `-ea:AssertDemo` thay vì `-ea:lesson.AssertDemo`.

Các tùy chọn enable/disable có thể được phối hợp để cấu hình theo class/package; khi làm vậy, hãy dùng quy tắc của Java launcher và kiểm tra trên chính chương trình được khởi chạy. Tùy chọn đặt **trước tên lớp chứa `main`** hoặc trước `-jar` nếu chạy JAR:

```sh
java -ea -cp out AssertDemo
java -ea:AssertDemo -cp out AssertDemo
java -da -cp out AssertDemo
```

Các lệnh trên dùng tệp `AssertDemo.class` đã biên dịch ở phần 3. Đừng viết `java -cp out AssertDemo -ea` rồi cho rằng `-ea` được JVM xử lý: tại vị trí sau tên lớp, nó chỉ là đối số truyền cho `main(String[] args)`.

### Ví dụ package riêng

Tạo **`src/lesson/InvariantDemo.java`**:

```java
package lesson;

import java.util.Arrays;

public class InvariantDemo {
    private static boolean isSorted(int[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] > values[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        int[] values = {3, 1, 2};
        Arrays.sort(values);
        assert isSorted(values) : "sorting postcondition failed";

        System.out.println("enabled=" + InvariantDemo.class.desiredAssertionStatus());
        System.out.println("sorted=" + Arrays.toString(values));
    }
}
```

Biên dịch và chạy:

```sh
javac -encoding UTF-8 --release 17 -d out src/lesson/InvariantDemo.java
java -cp out lesson.InvariantDemo
java -ea:lesson.InvariantDemo -cp out lesson.InvariantDemo
```

**Stdout của lần chạy đầu (không bật assertions):**

```text
enabled=false
sorted=[1, 2, 3]
```

**Stdout của lần chạy thứ hai (bật cho đúng lớp):**

```text
enabled=true
sorted=[1, 2, 3]
```

Ví dụ này thể hiện vị trí phù hợp của `assert`: kiểm tra **hậu điều kiện nội bộ** sau khi sắp xếp. Không cần dùng assertion để quyết định có sắp xếp hay không, vì thuật toán vẫn phải chạy đúng khi assertions tắt.

## 5. Không đặt tác dụng phụ trong assertion

Đoạn code sau biên dịch hợp lệ nhưng **là kiểu viết cần tránh**:

```java
assert ++counter > 0;
```

Nếu assertions tắt, `counter` không tăng; nếu bật, `counter` tăng. Chương trình có thể thay đổi trạng thái chỉ do thêm `-ea`.

Để thấy sự khác biệt, tạo tệp **`SideEffectDemo.java`**:

```java
public class SideEffectDemo {
    public static void main(String[] args) {
        int counter = 0;
        assert ++counter == 1 : "unexpected counter";
        System.out.println("counter=" + counter);
    }
}
```

```sh
javac -encoding UTF-8 --release 17 -d out SideEffectDemo.java
java -cp out SideEffectDemo
java -ea -cp out SideEffectDemo
```

Hai lần chạy có output lần lượt:

Không có `-ea`:

```text
counter=0
```

Có `-ea`:

```text
counter=1
```

**Cách viết đúng:** thực hiện các phép cập nhật quan trọng ngoài `assert`, rồi chỉ kiểm tra một biểu thức **không làm thay đổi trạng thái**:

```java
counter++;
assert counter == 1 : "unexpected counter";
```

Đoạn sửa là minh họa tại phạm vi có biến `counter`. Ngay cả trong biểu thức `detailExpression` sau dấu `:`, cũng không nên đặt thao tác ghi file, cập nhật dữ liệu hoặc hàm có tác dụng phụ: biểu thức đó chỉ được đánh giá khi assertions bật **và điều kiện sai**.

## 6. Khi nào dùng `assert`, khi nào dùng `if` và `throw`?

| Mục đích | Cách làm |
| --- | --- |
| Bảo vệ tiền điều kiện của **public API**, tham số truyền từ caller có thể sai | `if` + `throw new IllegalArgumentException(...)` hoặc `Objects.requireNonNull(...)` |
| Kiểm tra dữ liệu ngoài: request, file, DB, CLI, network | Kiểm tra luôn chạy, có xử lý lỗi rõ ràng; không dùng `assert` thay thế |
| Kiểm tra bất biến nội bộ vốn phải đúng nếu code không có bug | `assert condition : detail;` |
| Kiểm tra hậu điều kiện của thuật toán nội bộ để tìm bug trong lúc phát triển | `assert postcondition;` |
| Kiểm tra kết quả bằng JUnit trong automated tests | Dùng assertion của JUnit/test framework theo hợp đồng kiểm thử |

**Minh họa phân biệt** (các phương thức dưới đây cần đặt trong một class thích hợp; không phải tệp chạy độc lập):

```java
// Input bên ngoài: kiểm tra phải hoạt động khi assertions tắt.
public static int positive(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException("value must be positive");
    }
    return value;
}

// Giá trị suy ra bên trong thuật toán: bảo vệ giả định lập trình.
private static int internalHalf(int provenEven) {
    assert provenEven % 2 == 0 : "algorithm produced an odd value";
    return provenEven / 2;
}
```

Nếu `provenEven` thực tế do người dùng đưa vào mà chưa kiểm tra, assertion không đủ bảo vệ hợp đồng. Vấn đề là **nguồn gốc và ý nghĩa của dữ liệu**, không chỉ mức truy cập `private`/ `public` của phương thức.

Dùng `assert false : "unreachable state";` để đánh dấu nhánh logic đáng lẽ không thể tới được là một kỹ thuật debug. Nhưng khi assertions tắt, câu lệnh đó không làm gì; vì vậy **không dùng `assert false` thay `throw`** trong trường hợp ứng dụng phải từ chối trạng thái đó ở mọi môi trường.

## 7. `AssertionError` không phải ngoại lệ nghiệp vụ thông thường

`AssertionError` kế thừa `Error`, và `Error` kế thừa `Throwable`. Nó **không** kế thừa `Exception`. Do đó:

```java
try {
    assert false : "broken assumption";
} catch (Exception ex) {
    System.out.println("not caught");
}
```

Đoạn minh họa trên không bắt được `AssertionError` khi bật assertions, vì `catch (Exception)` không nhận `Error`. Khi assertions tắt, câu lệnh `assert` bị bỏ qua và phần `catch` không chạy. Đây không phải ví dụ chương trình đầy đủ; dùng phần 3 để xem bản chạy có bắt `AssertionError` nhằm minh họa.

Không nên dùng `catch (Throwable)` hoặc `catch (AssertionError)` rộng khắp để biến lỗi lập trình thành kết quả mặc định. Nếu xuất hiện `AssertionError`, điều cần làm thường là điều tra assumption bị phá vỡ, input dẫn tới trạng thái đó và đường đi của thuật toán.

Thông báo/stack trace nếu `AssertionError` không được bắt sẽ do JVM in ra luồng lỗi chuẩn (*stderr*); nội dung stack trace chứa tên tệp/số dòng và có thể thay đổi. Vì vậy ví dụ chính bắt lỗi có chủ đích để người học nhìn thấy **stdout chính xác**, không phụ thuộc vào chi tiết stack trace.

## 8. Sai lầm phổ biến

1. **Quên `-ea`:** thấy chương trình không báo gì rồi tin assertion đã chạy. Hãy kiểm tra dòng lệnh JVM hoặc cấu hình Run/Debug trong IDE.
2. **Dùng `assert` thay validation:** khi triển khai với assertions tắt, input không hợp lệ có thể đi qua; phải dùng `if`/`throw` hoặc API validation phù hợp.
3. **Có tác dụng phụ trong biểu thức:** cập nhật biến, xóa file, gọi API tạo thay đổi có thể chỉ xảy ra trong một chế độ JVM.
4. **Nhầm `-ea` là tham số biên dịch:** `javac` vẫn phân tích và biên dịch câu lệnh; `java -ea` quyết định việc kiểm tra lúc chạy.
5. **Đặt `-ea` sau tên main class:** launcher chuyển nó cho `main` như tham số chương trình.
6. **Dùng `assert` để báo lỗi có thể dự kiến:** file không tồn tại, JSON không hợp lệ, người dùng nhập sai là tình huống vận hành thông thường, không nhất thiết biểu thị bug nội bộ.
7. **Lẫn với kiểm thử JUnit:** lời gọi `Assertions.assertEquals` là API của thư viện, không cần bật `-ea` để thực hiện logic kiểm thử của JUnit.
8. **Giả định mọi Java class đều bật chỉ nhờ `-ea`:** cấu hình có phạm vi class/package, class loader và nhóm system classes; kiểm tra lớp nào thực sự chịu tác dụng khi dùng cấu hình chọn lọc.

## 9. Checklist thực hành

Trước khi thêm một assertion, tự hỏi:

- Điều kiện này có **luôn đúng nếu thuật toán không có lỗi** hay đây là dữ liệu ngoài có thể sai hợp lệ?
- Biểu thức kiểm tra và biểu thức message có **không gây tác dụng phụ** không?
- Khi `-ea` tắt, chương trình có vẫn giữ nguyên hành vi cần thiết không?
- Nếu điều kiện sai, message có cho biết giá trị/biến quan trọng để tìm bug không?
- Khi chạy local/test, tôi đã bật assertions cho lớp hoặc package cần kiểm tra chưa?
- Sau khi sửa bug, chương trình đã chạy thử ở cả chế độ **có** và **không có `-ea`** chưa?

### Tự kiểm tra

**1. `assert value > 0;` có chạy mặc định trong hầu hết ứng dụng Java không?** Không. Cần bật assertion bằng `java -ea ...` hoặc cấu hình tương đương.

**2. Khi assertion sai và được bật, Java ném gì?** `AssertionError`, thuộc nhánh `Error`, không phải `Exception`.

**3. `assert condition : detail;` có luôn đánh giá `detail` không?** Không. Chỉ đánh giá khi assertion được bật và điều kiện sai.

**4. Có thể dùng `assert` làm kiểm tra bắt buộc cho số tiền nhập vào không?** Không. Dùng validation luôn thực thi và ném loại exception phù hợp hoặc trả kết quả lỗi theo hợp đồng API.

**5. Có cần đổi lệnh `javac` hoặc biên dịch lại để bật assertions không?** Không. Chỉ cần chạy chương trình bằng `java -ea ...` và bảo đảm lớp cần kiểm tra được bật.

**Ghi nhớ:** `assert` là công cụ phát hiện **vi phạm giả định nội bộ**; `-ea` bật việc kiểm tra ở **thời điểm chạy**. Chương trình phải giữ được hành vi đúng ngay cả khi assertion bị tắt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-and-exceptions-009">Không dùng assert thay kiểm tra dữ liệu đầu vào bắt buộc</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng `assert` thay kiểm tra dữ liệu đầu vào bắt buộc — Java 17+

## 1. Quy tắc cốt lõi: `assert` không phải validation

Trong Java, **assertion** là một phép kiểm tra giả định nội bộ dành cho quá trình phát triển và chẩn đoán. Cú pháp `assert` có thể bị **tắt khi chương trình chạy**. Vì thế, **không dùng `assert` để thay những kiểm tra dữ liệu đầu vào bắt buộc** của phương thức, constructor, API công khai hay hệ thống nghiệp vụ.

~~~java
// SAI: điều kiện có thể không được kiểm tra khi assertions bị tắt.
static void registerAgeWrong(int age) {
    assert age >= 0 : "age must not be negative";
    // Tiếp tục xử lý age ngay cả khi age âm nếu assertion bị tắt.
}
~~~

~~~java
// ĐÚNG: kiểm tra bắt buộc chạy độc lập với tùy chọn -ea/-da.
static void registerAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("age must not be negative");
    }
    // Chỉ xử lý sau khi age hợp lệ.
}
~~~

**Câu hỏi quyết định:** điều kiện này có phải luôn được kiểm tra trong ứng dụng thật, kể cả khi không bật assertions? Nếu **có**, phải dùng kiểm tra chạy bình thường (`if`, validation API, `Objects.requireNonNull`, v.v.) chứ **không chỉ dùng `assert`**.

## 2. Hợp đồng API phải hoạt động khi assertions tắt

Với `-da`, JVM bỏ qua điều kiện và thông điệp trong câu lệnh `assert`, nên không thể dựa vào assertion để chặn dữ liệu sai. Cờ `-ea` hoặc `-da` thuộc lệnh **`java` lúc chạy**, đặt trước tên lớp `main`; chúng không làm thay đổi nghĩa vụ validation của ứng dụng.

Ví dụ ở các phần dưới cho thấy `IllegalArgumentException` và `Objects.requireNonNull` hoạt động giống nhau ở cả hai chế độ. Phần cú pháp, cấu hình assertions theo lớp/package và tác dụng phụ của biểu thức `assert` đã được giải thích trong bài trước.

## 3. Vì sao kiểm tra dữ liệu bên ngoài phải là bắt buộc?

Dữ liệu đầu vào có thể đến từ đối số phương thức công khai, constructor, người dùng, file, mạng hoặc các hệ thống tích hợp. Những dữ liệu này **không phải giả định nội bộ** mà cần có **hợp đồng hợp lệ** được thực thi mọi lần.

Ví dụ sai:

~~~java
static int calculateDiscountWrong(int percent) {
    assert percent >= 0 && percent <= 100;
    return 100 - percent;
}
~~~

Ở chế độ assertions tắt, `calculateDiscountWrong(150)` vẫn chạy và trả `-50`. Đây là lỗi nghiệp vụ ngay cả khi phép toán vẫn biên dịch, không phải dấu hiệu chương trình đúng.

Bản đúng:

~~~java
static int calculateDiscount(int percent) {
    if (percent < 0 || percent > 100) {
        throw new IllegalArgumentException("percent must be 0..100");
    }
    return 100 - percent;
}
~~~

Dù JVM bật hay tắt assertions:

- `calculateDiscount(20)` trả `80`.
- `calculateDiscount(150)` ném `IllegalArgumentException`.
- Đầu vào xấu **không được phép lọt qua kiểm tra**.

Validation cũng quan trọng với những ràng buộc không biểu diễn bằng một số: chuỗi không rỗng, ngày đúng miền, tệp được phép, trạng thái tài khoản, số lượng vật phẩm không âm, mã định danh hợp lệ, giới hạn tài nguyên.

## 4. Chọn ngoại lệ phù hợp khi validation thất bại

| Tình huống | Cách xử lý Java thường phù hợp |
|---|---|
| Đối số số học ngoài miền hợp lệ | `IllegalArgumentException` |
| Tham chiếu đối số bắt buộc khác null | `Objects.requireNonNull(value, "message")` hoặc `NullPointerException` có chủ đích |
| Đối số chuỗi rỗng/blank | Kiểm tra và ném `IllegalArgumentException` |
| Giá trị chuỗi không phân tích được thành số | Xử lý/để lan truyền `NumberFormatException` theo hợp đồng |
| Thao tác không thể làm trong trạng thái hiện tại | `IllegalStateException` |
| API trả checked exception, như đọc file | Catch hoặc declare `throws` theo hợp đồng API |
| Giả định chỉ có thể sai khi có bug **nội bộ** | Có thể dùng `assert` để phát hiện lỗi trong lúc phát triển, không thay validation |

~~~java
static String normalizeUser(String username) {
    java.util.Objects.requireNonNull(username, "username is required");
    if (username.isBlank()) {
        throw new IllegalArgumentException("username must not be blank");
    }
    return username.strip();
}
~~~

- `normalizeUser(null)` → ném `NullPointerException` với thông điệp `username is required`.
- `normalizeUser("   ")` → ném `IllegalArgumentException` với thông điệp `username must not be blank`.
- `normalizeUser(" Lan ")` → trả `"Lan"`.

Cả ba hành vi **không phụ thuộc** vào `-ea`. Dùng đúng loại ngoại lệ còn giúp caller biết ranh giới nào cần kiểm tra dữ liệu, ranh giới nào xử lý trạng thái và ranh giới nào báo lỗi cho người dùng.

## 5. Assertion hợp lý cho bất biến nội bộ

Một **bất biến (invariant)** là điều thuật toán phải duy trì khi dữ liệu đầu vào và các bước đã được xử lý đúng. Assertion có thể hỗ trợ phát hiện vi phạm bất biến nội bộ trong quá trình phát triển:

~~~java
static int roundDownToEven(int value) {
    if (value < 0) {
        throw new IllegalArgumentException("value must not be negative");
    }

    int even = 2 * (value / 2);
    assert even >= 0 : "even must not be negative";
    assert even % 2 == 0 : "even must be divisible by two";
    assert even <= value : "even must not exceed original";
    return even;
}
~~~

Phân biệt hai nhóm kiểm tra:

1. `if (value < 0)` là **validation bắt buộc** vì `value` là đối số do caller cung cấp.
2. Những `assert` còn lại kiểm tra **kết quả nội bộ** do code tạo ra sau validation, để phát hiện khi giả định của lập trình viên bị vi phạm trong phát triển.

Nếu `assert` bị tắt, giá trị trả về vẫn đúng theo logic đã được thiết kế. Ngược lại, nếu bỏ `if` thì kết quả với đầu vào âm có thể không đúng hợp đồng, dù các assertions được bật ở máy phát triển.

Nếu tính đúng đắn hoặc an toàn của ứng dụng đòi hỏi kiểm tra một bất biến ở **production**, hãy dùng kiểm tra **luôn chạy** thay vì trông chờ `assert` được bật.

## 6. Tác dụng phụ không được nằm trong một kiểm tra có thể tắt

~~~java
int processed = 0;
// Không dùng: assert ++processed == 1;
processed++;              // Thao tác nghiệp vụ luôn chạy.
assert processed == 1;   // Chỉ kiểm tra trạng thái nội bộ.
~~~

`assert operation();` sẽ bỏ qua **cả lời gọi `operation()`** khi assertions tắt. Không được đưa ghi file, kiểm tra quyền, thay dữ liệu hoặc trừ tiền chỉ vào biểu thức assertion. Biểu thức sau dấu `:` cũng chỉ được đánh giá khi assertion bật **và sai**, nên không được dùng để tạo tác dụng phụ bắt buộc.

## 7. Từ chối input khác với báo lỗi giả định nội bộ

`AssertionError` kế thừa `Error`, **không** kế thừa `Exception`; `catch (Exception)` không bắt nó. Thất bại assertion thường chỉ ra bug về bất biến thuật toán, không phải dữ liệu mà người dùng được phép nhập sai.

Với null hoặc phạm vi dữ liệu, dùng `Objects.requireNonNull`, `IllegalArgumentException` hoặc `IllegalStateException` theo ý nghĩa hợp đồng; mọi kiểm tra ấy phải làm việc dưới cả `-da` lẫn `-ea`. Bài runnable tiếp theo cố ý bắt `AssertionError` **chỉ để minh họa stdout ổn định**, không phải chính sách xử lý `Error` trong hệ thống thật.


## 8. Chương trình Java 17+ hoàn chỉnh và hai chế độ chạy

Lưu đúng tên file **`AssertVsValidationDemo.java`**:

~~~java
import java.util.Objects;

public final class AssertVsValidationDemo {
    private AssertVsValidationDemo() {}

    static String normalizeUser(String username) {
        Objects.requireNonNull(username, "username is required");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return username.strip();
    }

    static int requirePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be 0..100");
        }
        return percent;
    }

    static int roundDownToEven(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        int even = 2 * (value / 2);
        assert even >= 0 : "even must not be negative";
        assert even % 2 == 0 : "even must be divisible by two";
        assert even <= value : "even must not exceed original";
        return even;
    }

    static final class Wallet {
        private int balance;

        Wallet(int openingBalance) {
            if (openingBalance < 0) {
                throw new IllegalArgumentException("opening balance negative");
            }
            this.balance = openingBalance;
        }

        void withdraw(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            if (amount > balance) {
                throw new IllegalStateException("insufficient funds");
            }
            balance -= amount;
            assert balance >= 0 : "internal balance invariant broken";
        }

        int getBalance() {
            return balance;
        }
    }

    // CỐ Ý CÓ BUG: chỉ dùng để so sánh -da với -ea, không dùng thực tế.
    private static int buggyInternalCalculation() {
        int computed = -1;
        assert computed >= 0 : "internal computed negative";
        return computed;
    }

    public static void main(String[] args) {
        System.out.println("assertions enabled="
                + AssertVsValidationDemo.class.desiredAssertionStatus());

        System.out.println("user=" + normalizeUser(" Lan "));
        System.out.println("percent=" + requirePercent(80));

        Wallet wallet = new Wallet(100);
        wallet.withdraw(30);
        System.out.println("balance=" + wallet.getBalance());
        System.out.println("even=" + roundDownToEven(7));

        try {
            normalizeUser(null);
        } catch (NullPointerException ex) {
            System.out.println("null user=" + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
        }

        try {
            normalizeUser("   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("blank user=" + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
        }

        try {
            requirePercent(150);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid percent="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        try {
            wallet.withdraw(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid withdraw="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        try {
            wallet.withdraw(100);
        } catch (IllegalStateException ex) {
            System.out.println("insufficient="
                    + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        System.out.println("balance after rejection=" + wallet.getBalance());

        // Khối bắt AssertionError CHỈ dành cho bài học minh họa.
        try {
            System.out.println("buggy internal="
                    + buggyInternalCalculation());
        } catch (AssertionError ex) {
            System.out.println("internal assertion=" + ex.getMessage());
        }

        System.out.println("finished=true");
    }
}
~~~

**Biên dịch một lần** bằng JDK 17+, không cần Maven/Gradle, thư viện ngoài hay preview features.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 AssertVsValidationDemo.java
java -da -cp . AssertVsValidationDemo
java -ea -cp . AssertVsValidationDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\AssertVsValidationDemo.java
java -da -cp . AssertVsValidationDemo
java -ea -cp . AssertVsValidationDemo
~~~

### Exact stdout với `-da` (assertions tắt)

~~~text
assertions enabled=false
user=Lan
percent=80
balance=70
even=6
null user=NullPointerException:username is required
blank user=IllegalArgumentException:username must not be blank
invalid percent=IllegalArgumentException:percent must be 0..100
invalid withdraw=IllegalArgumentException:amount must be positive
insufficient=IllegalStateException:insufficient funds
balance after rejection=70
buggy internal=-1
finished=true
~~~

### Exact stdout với `-ea` (assertions bật)

~~~text
assertions enabled=true
user=Lan
percent=80
balance=70
even=6
null user=NullPointerException:username is required
blank user=IllegalArgumentException:username must not be blank
invalid percent=IllegalArgumentException:percent must be 0..100
invalid withdraw=IllegalArgumentException:amount must be positive
insufficient=IllegalStateException:insufficient funds
balance after rejection=70
internal assertion=internal computed negative
finished=true
~~~

**Phân tích kết quả:**

1. Dòng `assertions enabled=...` cho biết trạng thái assertion của **chính lớp demo**.
2. Người dùng và phần trăm hợp lệ chạy bình thường. Sau khi rút 30 từ ví có 100, số dư là 70.
3. Đầu vào `null`, chuỗi chỉ có khoảng trắng, phần trăm `150`, lệnh rút `0` và lệnh rút vượt số dư **đều bị từ chối y hệt** ở cả hai chế độ.
4. `roundDownToEven(7)` trả 6; các `assert` nội bộ đều đúng khi bật.
5. `buggyInternalCalculation()` có lỗi giả định có chủ đích. Với `-da`, câu lệnh `assert` không kiểm tra gì nên trả `-1`; với `-ea`, nó ném `AssertionError` và nhánh demo in thông điệp.
6. **Bài học quan trọng:** validation bắt buộc không đổi theo `-da/-ea`; chỉ kiểm tra assertion chẩn đoán nội bộ phụ thuộc vào cờ này.

## 9. Viết kiểm thử đúng cho API

Nếu một phương thức tuyên bố chỉ chấp nhận `percent` trong `0..100`, hãy kiểm thử cả dữ liệu hợp lệ lẫn dữ liệu sai **ngay cả khi assertions đang bị tắt**.

Ví dụ ý tưởng kiểm thử, không phụ thuộc framework:

~~~java
int accepted = requirePercent(50);     // Phải là 50.
try {
    requirePercent(150);
    throw new IllegalStateException("expected validation to reject input");
} catch (IllegalArgumentException expected) {
    // Giá trị sai đã bị từ chối đúng hợp đồng.
}
~~~

Chú ý đây là **mã minh họa** dùng phương thức `requirePercent` của mục 8; kiểm thử thực tế cần đặt trong lớp kiểm thử/phạm vi truy cập hợp lệ và dùng assertions của framework hoặc cơ chế test thích hợp. Khi kiểm thử exception, không chỉ dựa vào ngôn ngữ `assert` bị vô hiệu trong runtime.

Với JUnit, các phương thức kiểm thử như `assertThrows` của thư viện test không tương đương câu lệnh ngôn ngữ `assert`. Chúng chạy theo cơ chế thư viện kiểm thử, không biến thành câu lệnh `assert` bị bỏ qua theo `-da`.

**Test hai chế độ:** cùng một bộ validation phải đạt khi chạy `-da` và `-ea`. Tùy chính sách, dùng `-ea` trong quá trình phát triển/test để sớm phát hiện bug nội bộ, nhưng tính đúng của API không được phụ thuộc vào việc người triển khai bật cờ ấy.

## 10. Khi nào có thể dùng `assert`?

Những chỗ thường hợp lý (chỉ cho các giả định **nội bộ** không bị ảnh hưởng bởi dữ liệu không tin cậy chưa qua kiểm tra):

- Kiểm tra kết quả trung gian sau một phép biến đổi mà thuật toán bảo đảm bất biến.
- Đánh dấu nhánh nội bộ được thiết kế là không thể tới theo các điều kiện đã được chứng minh.
- Xác minh điều kiện hậu trạng thái khi code xử lý đã hoàn tất và tính đúng vẫn được đảm bảo độc lập với assertion.
- Hỗ trợ phát hiện lỗi lập trình khi chạy môi trường phát triển/test có bật `-ea`.

Ngược lại, **không dùng `assert`** làm phương thức duy nhất để:

- Kiểm tra input của public method, REST handler, constructor, CLI argument.
- Kiểm tra null/blank/giới hạn dữ liệu cần đảm bảo trong sản phẩm.
- Phân quyền truy cập, xác thực và bảo đảm an toàn giao dịch.
- Kiểm tra trạng thái bắt buộc (ví dụ đủ số dư để rút tiền).
- Thực hiện công việc có tác dụng phụ, gọi API, sửa object hoặc cấp quyền.

Đừng cố “giải quyết” một kiểm tra nghiệp vụ thiếu bằng cách bắt người dùng chạy ứng dụng với `-ea`. **Chính mã validation phải luôn thực thi**.

## 11. Các lỗi thường gặp

| Sai lầm | Rủi ro | Cách sửa |
|---|---|---|
| `assert age >= 0;` là điều kiện đầu vào duy nhất | Dữ liệu âm lọt qua nếu assertions tắt | `if (age < 0) throw new IllegalArgumentException(...)` |
| `assert username != null;` trong API công khai | Caller null không bị từ chối ngay khi tắt | `Objects.requireNonNull(username, ...)` |
| Cho rằng `-ea` luôn bật trong production | JVM có thể chạy mặc định không bật | Validation không phụ thuộc cờ assertion |
| `assert ++counter > 0;` | Khi tắt, `counter` không tăng | Tách thao tác bắt buộc ra khỏi assertion |
| `assert authorize(user);` | Khi tắt, việc kiểm tra quyền bị bỏ qua | Gọi logic phân quyền bắt buộc và xử lý kết quả bằng điều kiện thường |
| Bắt `Exception` rồi mong xử lý `AssertionError` | `AssertionError` không kế thừa `Exception` | Sửa bug nội bộ; không dùng Error như validation |
| Viết `assert condition : logAndReturn();` | Detail cũng không chạy khi tắt/điều kiện đúng | Không dựa vào tác dụng phụ trong assert |
| Dùng assert để báo “hết tiền” | Đó là trạng thái nghiệp vụ dự kiến | Kiểm tra và báo `IllegalStateException`/kết quả hợp đồng |
| Viết test validation chỉ chạy khi `-ea` | Không chứng minh an toàn khi tắt | Chạy kiểm thử với `-da` và `-ea` |
| Bắt lỗi validation bằng `catch (AssertionError)` | Biến lỗi input thành Error ngoài hợp đồng | Ném ngoại lệ đầu vào có loại/thông điệp rõ |
| Nghĩ `assert` được chạy ở compile time | Assertion statement được kiểm tra cú pháp khi compile, điều kiện được đánh giá khi chạy nếu bật | Phân biệt compiler check với runtime assertion |
| Dùng một `assert` cho mọi vấn đề bất thường | Không rõ loại lỗi và không bảo đảm kiểm tra | Phân loại input, state, internal invariant và dùng cơ chế phù hợp |

## 12. Bài tập có đáp án

**A — Nhận diện lỗi thiết kế.** Có phương thức:

~~~java
static double divide(double a, double b) {
    assert b != 0;
    return a / b;
}
~~~

Nếu hợp đồng yêu cầu **từ chối mẫu số 0** mọi lúc, đoạn trên có đủ không?

**Đáp án:** Không. Khi assertions tắt, với số thực `double`, phép chia cho 0 có thể tạo `Infinity` hoặc `NaN` thay vì bị từ chối. Thêm:

~~~java
if (b == 0.0) {
    throw new IllegalArgumentException("divisor must not be zero");
}
~~~

**B — Bật/tắt assertion.** Lệnh nào bật assertion của lớp ví dụ?

**Đáp án:** `java -ea -cp . AssertVsValidationDemo`. Để tắt rõ ràng: `java -da -cp . AssertVsValidationDemo`.

**C — Vì sao `assert ++count > 0;` nguy hiểm?**

**Đáp án:** Khi `-da`, biểu thức điều kiện không được đánh giá, nên `count` **không tăng**. Không đưa cập nhật dữ liệu bắt buộc vào assertion.

**D — Loại lỗi.** `AssertionError` nằm dưới `Exception` hay `Error`?

**Đáp án:** Dưới `Error`; `catch (Exception)` không bắt `AssertionError`.

**E — Phân loại điều kiện.** `user != null` của một API công khai và `computed >= 0` sau thuật toán đã xác minh giá trị đầu vào: điều kiện nào phải là validation?

**Đáp án:** `user != null` là **validation bắt buộc**; `computed >= 0` có thể là assertion nội bộ nếu đó là bất biến được đảm bảo theo thuật toán và việc bỏ qua assert không làm sai hành vi production.

**F — So sánh hai lần chạy.** `requirePercent(150)` sẽ thế nào dưới `-da` và `-ea`?

**Đáp án:** Cả hai đều ném `IllegalArgumentException`. Chỉ `buggyInternalCalculation()` biểu lộ sự khác biệt do câu lệnh assertion.

## 13. Checklist và kết luận

- [ ] Hiểu `assert` kiểm tra giả định nội bộ, có thể bị tắt khi chạy.
- [ ] Biết `assert condition;` và `assert condition : detail;`.
- [ ] Biết `-ea` bật, `-da` tắt assertion của ứng dụng theo cấu hình JVM.
- [ ] Không dùng `assert` thay validation dữ liệu đầu vào bắt buộc.
- [ ] Dùng `if`, `throw IllegalArgumentException` và `Objects.requireNonNull` phù hợp hợp đồng.
- [ ] Dùng `IllegalStateException` cho thao tác sai trạng thái khi phù hợp.
- [ ] Biết `AssertionError` là nhánh `Error`, không phải ngoại lệ đầu vào thông thường.
- [ ] Không đưa tác dụng phụ, phân quyền hoặc thay đổi dữ liệu vào biểu thức assertion.
- [ ] Nhận diện khác nhau giữa internal invariant và ràng buộc của input bên ngoài.
- [ ] Kiểm thử validation ngay cả khi `-da`.
- [ ] Chạy `AssertVsValidationDemo.java` trên JDK 17+ với cả `-da` và `-ea`, đối chiếu hai stdout.

**Kết luận:** `assert` giúp tìm **lỗi giả định nội bộ** khi được bật, nhưng **không bảo đảm được thực thi trong mọi lần chạy**. Bất kỳ điều kiện đầu vào hay quy tắc nghiệp vụ nào bắt buộc phải được kiểm tra đều cần **validation luôn chạy**, ném ngoại lệ hoặc trả kết quả theo hợp đồng API; tuyệt đối không chỉ dựa vào `assert`.

</details>

- [Quay lại đầu trang](#back-to-top)
