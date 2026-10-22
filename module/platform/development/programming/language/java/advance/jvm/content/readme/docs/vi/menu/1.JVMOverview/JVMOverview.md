<a id="back-to-top"></a>

# Bức tranh JVM

## Menu
- [JVM là gì và vì sao Java cần một máy thực thi trung gian](#jvm-overview-001)
- [Phân biệt ngôn ngữ Java, Java SE, JVM, JDK và môi trường chạy](#jvm-overview-002)
- [Đường đi của chương trình: mã nguồn → javac → class → JVM → mã máy](#jvm-overview-003)
- [Bytecode độc lập nền tảng và những giới hạn của tính khả chuyển](#jvm-overview-004)
- [Phân biệt đặc tả JVMS, dự án OpenJDK và cách triển khai HotSpot](#jvm-overview-005)
- [Vai trò của bộ nạp lớp, bộ thực thi, bộ nhớ và bộ thu gom rác](#jvm-overview-006)
- [Các ngôn ngữ khác trên JVM và hợp đồng chung của tệp class](#jvm-overview-007)
- [Cách đọc tài liệu theo phiên bản JDK và tách quy tắc khỏi chi tiết triển khai](#jvm-overview-008)

## <a id="jvm-overview-001">JVM là gì và vì sao Java cần một máy thực thi trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# JVM là gì và vì sao Java cần một máy thực thi trung gian?

## 1. Vấn đề Java cần giải quyết

Máy tính cuối cùng thực thi **lệnh máy** phù hợp với kiến trúc CPU và hệ điều hành. Chương trình viết bằng Java lại được biểu diễn dưới dạng mã nguồn (`.java`), một dạng con người có thể đọc và biên dịch. Nếu biên dịch mã nguồn thành lệnh máy riêng cho từng hệ thống, nhà phát triển phải tạo và phân phối những bản thực thi khác nhau cho nhiều môi trường.

Java chọn một mô hình có **lớp thực thi trung gian**:

```text
Mã nguồn Java (.java)
          |
          | javac (trình biên dịch của JDK)
          v
Bytecode Java (.class, có thể đóng gói trong .jar)
          |
          | JVM tương thích: nạp lớp → kiểm tra → thực thi
          v
Lệnh máy chạy trên CPU và dịch vụ của hệ điều hành
```

Chương trình được biên dịch một lần thành **bytecode**, tức tập lệnh dành cho *máy ảo Java* thay vì trực tiếp cho một CPU cụ thể. Mỗi nền tảng cần một bản triển khai JVM phù hợp; bản JVM đó đảm nhận việc thực thi bytecode trên nền tảng của mình. Đây là cơ sở của ý tưởng **“write once, run anywhere”** (viết một lần, chạy trên nhiều môi trường tương thích).

Điểm cốt lõi: JVM giúp tách **mã chương trình đã biên dịch** khỏi **chi tiết thực thi của từng hệ thống**. Tính di động này có điều kiện: môi trường đích phải cung cấp JVM tương thích với phiên bản bytecode và các thư viện mà ứng dụng cần.

## 2. JVM thực chất là gì?

**JVM (Java Virtual Machine)** là mô hình máy tính trừu tượng được đặc tả để nạp, kiểm tra và thực thi tệp lớp Java (`.class`). Một **bản triển khai JVM** là phần mềm hiện thực hóa mô hình đó, làm việc cùng hệ điều hành và phần cứng thật. Ví dụ, **HotSpot** là một bản triển khai JVM phổ biến trong các bản phân phối OpenJDK; không phải mọi JVM đều sử dụng cùng một trình biên dịch JIT hay cùng thuật toán quản lý bộ nhớ.

Cần phân biệt ba lớp:

| Thành phần | Vai trò | Ví dụ |
| --- | --- | --- |
| **Ngôn ngữ Java** | Quy tắc viết mã nguồn: kiểu, cú pháp, lớp, biểu thức… | `class`, `interface`, `try` |
| **Bytecode / định dạng class** | Dạng mã biên dịch cho JVM và định dạng mang thông tin kiểu, phương thức, hằng số… | `Hello.class` |
| **JVM** | Môi trường thực thi các lớp đã biên dịch theo đặc tả JVM | HotSpot, OpenJ9 |

JVM **không phải** trình biên dịch nguồn `javac`, **không phải** bản thân hệ điều hành, và **không phải** toàn bộ hệ sinh thái Java. Nó là một thành phần runtime trung tâm. Cũng vì bytecode là giao diện thực thi, một số ngôn ngữ khác như Kotlin hoặc Scala có thể biên dịch thành mã chạy trên JVM.

Một mô tả cụ thể hơn: khi chạy `java TenLop`, công cụ khởi chạy Java thiết lập môi trường và khởi tạo JVM phù hợp, JVM nạp những lớp cần thiết rồi thực thi điểm vào chương trình (thường là phương thức `main`). Một tiến trình Java thông thường có một JVM đang chạy, nhưng không nên hiểu rằng “mỗi đối tượng Java là một máy ảo” hay mọi hệ thống đều chỉ có duy nhất một JVM.

## 3. Vì sao cần máy thực thi trung gian?

### 3.1. Tính di động giữa các nền tảng

Một tệp `.class` hợp lệ chứa lệnh bytecode, không chứa trực tiếp bộ lệnh x86-64 hay ARM64 cho CPU cụ thể. JVM trên Windows, Linux hay macOS có thể hiểu cùng định dạng đó khi đáp ứng yêu cầu về phiên bản và thư viện. Nhà phát triển thường không phải biên dịch lại mã Java cho mỗi hệ điều hành chỉ vì CPU khác nhau.

**Nhưng “chạy mọi nơi” không phải lời hứa tuyệt đối.** Chương trình vẫn có thể phụ thuộc vào đường dẫn tệp, quyền truy cập, biến môi trường, thư viện native qua JNI, giao diện đồ họa, cấu hình thời gian/vùng miền hoặc các API chỉ có ở một hệ điều hành. Một class biên dịch cho phiên bản Java mới có thể không chạy trên JVM cũ.

### 3.2. Một hợp đồng thực thi thống nhất

Đặc tả JVM quy định định dạng tệp class, tập lệnh bytecode, các vùng dữ liệu lúc chạy, cơ chế nạp và liên kết lớp, cùng nhiều quy tắc thực thi. Nhờ đó, trình biên dịch và runtime có một hợp đồng rõ ràng: trình biên dịch tạo bytecode hợp lệ, JVM triển khai ý nghĩa của bytecode.

Lớp trung gian cũng giúp các công cụ phân tích bytecode, kiểm thử, đo hiệu năng, theo dõi lỗi và các ngôn ngữ JVM khác làm việc trên một nền tảng chung.

### 3.3. Tối ưu trong lúc chương trình chạy

Một JVM hiện đại có thể bắt đầu thực thi bytecode bằng **trình thông dịch (interpreter)** rồi dùng **JIT compiler** (*Just-In-Time*) để chuyển các phần mã thường xuyên thực thi thành mã máy phù hợp CPU hiện tại. Runtime có thể thu thập thông tin khi chương trình chạy để quyết định tối ưu hóa, ví dụ inline lời gọi phương thức trong những điều kiện thích hợp.

Tùy JVM và cách cấu hình, chiến lược thực thi có thể khác nhau; **không phải mọi bytecode đều bắt buộc được JIT biên dịch**. Quyết định tối ưu có thể được thay đổi hoặc hủy khi các giả định runtime không còn đúng (*deoptimization*).

### 3.4. Quản lý runtime chung

JVM cung cấp những cơ chế nền tảng để ứng dụng có thể hoạt động thống nhất hơn:

- **Nạp và liên kết lớp:** tìm, kiểm tra, chuẩn bị và sử dụng các định nghĩa lớp khi cần.
- **Quản lý bộ nhớ:** cấp phát đối tượng và phối hợp thu gom rác (*garbage collection*) trong các JVM thông dụng.
- **Thực thi phương thức, ngoại lệ và luồng:** duy trì ngữ cảnh chạy, ngăn xếp lời gọi, xử lý ngoại lệ và phối hợp với hệ điều hành.
- **Kiểm tra bytecode:** giúp ngăn nhiều loại mã class sai cấu trúc hoặc vi phạm ràng buộc kiểu được thực thi.

Các cơ chế này không đồng nghĩa với việc JVM tự động ngăn mọi lỗi, rò rỉ tài nguyên native hoặc lỗ hổng bảo mật trong ứng dụng.

## 4. JVM tham gia vào vòng đời chương trình như thế nào?

Có thể chia quá trình thành hai thời điểm khác nhau.

**Thời điểm biên dịch** (trước khi chạy):

1. Lập trình viên viết tệp `.java`.
2. Trình biên dịch `javac` phân tích mã nguồn, kiểm tra kiểu theo ngôn ngữ Java và tạo tệp `.class`.
3. Class có thể được đóng gói trong JAR để phân phối. JAR về cơ bản là tệp lưu trữ; **nó không phải JVM**.

**Thời điểm thực thi**:

1. Trình khởi chạy `java` thiết lập đường dẫn lớp/module và bắt đầu runtime.
2. **Class loader** nạp các lớp cần thiết. Không nhất thiết mọi lớp trong ứng dụng đều được nạp ngay khi khởi động.
3. JVM **liên kết** lớp: xác minh bytecode (`verification`), chuẩn bị cấu trúc dữ liệu (`preparation`) và giải quyết tham chiếu ký hiệu (`resolution`, có thể được trì hoãn trong giới hạn đặc tả).
4. JVM **khởi tạo** lớp khi các điều kiện tương ứng xảy ra, gồm việc thực hiện những phần khởi tạo tĩnh cần thiết.
5. Runtime thực thi bytecode bằng cơ chế phù hợp của bản JVM (thông dịch, biên dịch JIT hoặc kết hợp), gọi dịch vụ hệ điều hành khi cần.
6. Trong quá trình chạy, runtime quản lý đối tượng, ngăn xếp lời gọi, ngoại lệ và phối hợp với hệ điều hành. Khi chương trình kết thúc, các tài nguyên của tiến trình được giải phóng theo cơ chế của hệ điều hành.

Mô hình đơn giản:

```text
                    +----------------------------------+
.class  ----------> | JVM đang chạy                     |
                    |  Nạp lớp và kiểm tra bytecode    |
                    |  Runtime data areas              |
                    |  Interpreter / JIT (tùy JVM)     |
                    |  Garbage collector (tùy JVM)     |
                    +----------------+-----------------+
                                     |
                                     v
                        Hệ điều hành + CPU thật
```

Sơ đồ trên là mô hình khái niệm. Nó không có nghĩa class loader, JIT và bộ thu gom rác luôn chạy theo một chuỗi tuần tự cố định; nhiều hoạt động được thực hiện theo nhu cầu hoặc đồng thời.

## 5. Ví dụ Java 17+: cùng chương trình, JVM thực thi bytecode

Tạo tệp **`BytecodeDemo.java`**:

```java
public class BytecodeDemo {
    public static int sum(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        System.out.println(sum(2, 3));
    }
}
```

Trong terminal có JDK 17 trở lên, đứng tại thư mục chứa tệp rồi chạy:

```bash
javac BytecodeDemo.java
java BytecodeDemo
```

**Kết quả chuẩn của lệnh `java BytecodeDemo`:**

```text
5
```

Lệnh `javac` tạo **`BytecodeDemo.class`**. Lệnh `java` mới khởi chạy chương trình trên JVM; không cần biên dịch lại từ `.java` ở mỗi lần chạy nếu class vẫn phù hợp.

Dùng công cụ `javap` của JDK để quan sát bytecode:

```bash
javap -c BytecodeDemo
```

Trong phần phương thức `sum(int, int)` sẽ có đoạn tương đương sau (lược bỏ phần khác của kết quả `javap`):

```text
public static int sum(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: ireturn
```

Ý nghĩa:

- `iload_0` và `iload_1`: đưa hai đối số kiểu `int` lên ngăn xếp toán hạng.
- `iadd`: cộng hai số nguyên `int` trên ngăn xếp.
- `ireturn`: trả kết quả kiểu `int` về phương thức gọi.

Đây là **lệnh bytecode của JVM**, không phải câu lệnh hợp ngữ riêng cho CPU x86 hay ARM. Các lệnh bytecode khác trong `main` thực hiện gọi `sum` và in kết quả. Một JVM tương thích sẽ gán ý nghĩa nhất quán cho các opcode này, dù cách triển khai thành lệnh máy ở từng môi trường có thể khác.

**Tự quan sát phiên bản runtime:**

```bash
java -version
javac -version
```

Đầu ra phụ thuộc vào bản JDK/JVM cài đặt, nên không có một chuỗi phiên bản cố định áp dụng cho mọi máy. Nếu máy chỉ có môi trường chạy nhưng thiếu công cụ biên dịch, có thể chạy tệp class đã được biên dịch phù hợp mà không có `javac` tại môi trường đích.

### Không nhầm ba hình thức thực thi

| Cách chạy | Đầu vào chính | Điều diễn ra |
| --- | --- | --- |
| `javac BytecodeDemo.java` | Mã nguồn Java | Biên dịch ra bytecode `BytecodeDemo.class`; không chạy `main` |
| `java BytecodeDemo` | Class có sẵn trên classpath | Khởi chạy JVM và thực thi chương trình |
| `javap -c BytecodeDemo` | Class đã biên dịch | Phân tích/hiển thị bytecode; không thực thi `main` |

Một số phiên bản Java còn hỗ trợ chạy trực tiếp tệp nguồn bằng lệnh `java File.java`. Đây là tiện ích của trình khởi chạy; về bản chất vẫn có bước biên dịch và cơ chế thực thi bytecode thích hợp, không phủ nhận vai trò của JVM.

## 6. Điều gì diễn ra trong bộ nhớ JVM?

Đặc tả JVM mô tả những **runtime data areas** chính; cách cấp phát vật lý và tối ưu hóa thuộc về từng bản triển khai.

| Vùng dữ liệu | Ý nghĩa cơ bản |
| --- | --- |
| **Heap** | Chứa đối tượng và mảng; thường được chia sẻ giữa các luồng của cùng JVM, và là đối tượng quản lý của bộ thu gom rác |
| **Java Virtual Machine Stack** | Mỗi luồng có ngăn xếp gồm các *frame* cho từng lời gọi phương thức Java, gồm biến cục bộ, ngăn xếp toán hạng và dữ liệu thực thi liên quan |
| **PC register** | Mỗi luồng có bộ đếm chương trình gắn với lệnh JVM đang thực thi (với phương thức native, trạng thái được đặc tả khác) |
| **Method area** | Vùng logic dùng chung cho cấu trúc lớp như dữ liệu phương thức và thông tin runtime; cách hiện thực phụ thuộc JVM |
| **Native method stack** | Hỗ trợ lời gọi phương thức native nếu bản JVM sử dụng cơ chế ngăn xếp này |

Trong HotSpot, **Metaspace** là một cơ chế triển khai quan trọng cho metadata lớp, nhưng không nên đồng nhất toàn bộ khái niệm *method area* trong đặc tả với mọi chi tiết của Metaspace.

Ví dụ, khi `main` gọi `sum(2, 3)`:

1. JVM tạo/ngữ cảnh hóa frame tương ứng với lời gọi `sum` trên ngăn xếp của luồng.
2. Hai tham số `a` và `b` được thao tác qua biến cục bộ và ngăn xếp toán hạng của frame.
3. Lệnh `iadd` tính kết quả; `ireturn` kết thúc lời gọi, trả giá trị về bên gọi.
4. Nếu một phương thức tạo đối tượng bằng `new`, đối tượng thường nằm trên heap; runtime có thể tối ưu hóa cách cấp phát theo những điều kiện cụ thể.

**Garbage collection** giúp thu hồi bộ nhớ heap của đối tượng không còn được truy cập theo cơ chế tương ứng; nó **không thay thế** `close()` đối với tệp, kết nối mạng, khóa hay các tài nguyên cần đóng một cách xác định. Vì vậy ứng dụng vẫn cần `try-with-resources` khi dùng loại tài nguyên phù hợp.

## 7. JVM, môi trường chạy và JDK ở mức tổng quan

Khi nói “cần cài Java để chạy chương trình”, phải xác định **cần công cụ phát triển hay chỉ cần runtime**. Phân biệt ngắn gọn:

| Thành phần | Vai trò trong ví dụ `BytecodeDemo` |
| --- | --- |
| **JVM** | Nạp, kiểm tra và thực thi `BytecodeDemo.class` |
| **Runtime environment / JRE** | JVM cùng các thư viện/module cần thiết để chạy; không nhất thiết kèm trình biên dịch |
| **JDK** | Cung cấp runtime cùng `javac`, `java`, `javap`, `jar` và các công cụ phát triển |

Ở Java hiện đại, ứng dụng có thể được triển khai bằng JDK đầy đủ hoặc **runtime image** thu gọn bằng `jlink`; nhiều vendor không cung cấp một bộ cài JRE riêng như thời Java 8. Sự khác biệt này ảnh hưởng lựa chọn bộ cài và công cụ, **không thay định nghĩa JVM**. Bài kế tiếp tập trung hệ thống hóa các thuật ngữ **ngôn ngữ Java, Java SE, JVM, JDK và môi trường chạy**.

## 8. Tính tương thích và giới hạn quan trọng

**Tương thích phiên bản:** class file có phiên bản định dạng. Ví dụ, biên dịch bằng JDK mới với mức mặc định có thể tạo class không chạy trên Java cũ, dẫn đến lỗi như `UnsupportedClassVersionError`. Khi phải hỗ trợ Java 17 và đang dùng JDK có hỗ trợ mục tiêu này, có thể biên dịch:

```bash
javac --release 17 BytecodeDemo.java
```

Tùy mã nguồn và thư viện sử dụng, tùy chọn `--release 17` giúp đặt mức ngôn ngữ, định dạng class và bề mặt API chuẩn theo Java 17. Nó **không** tự bảo đảm mọi phụ thuộc bên thứ ba hay thư viện native đều chạy được trên môi trường đích; vẫn cần kiểm thử thực tế.

**Tính độc lập nền tảng không bảo đảm hành vi môi trường giống hệt:** hệ điều hành, hệ thống tệp, timezone, locale, quyền truy cập, tài nguyên máy và thư viện native có thể làm kết quả ứng dụng khác nhau.

**Hiệu năng có đánh đổi:** JVM cung cấp khả năng tối ưu động và cơ chế runtime phong phú nhưng thường cần thời gian khởi động, thời gian “warm-up” và bộ nhớ cho chính runtime. Trình thông dịch và JIT có thể phối hợp để cân bằng tốc độ khởi động với hiệu năng lâu dài. Không thể kết luận “Java luôn chậm” hoặc “JIT luôn nhanh hơn mọi cách biên dịch” nếu chưa có workload và phép đo cụ thể.

**Không đánh đồng với mọi cách chạy chương trình Java:** một số công cụ có thể tạo *native executable* bằng biên dịch ahead-of-time. Đó là mô hình triển khai khác so với trường hợp chạy class file trên JVM thông thường; không vì thế mà định nghĩa “JVM là gì” thay đổi.

**Không hiểu nhầm cơ chế bảo vệ:** bytecode verifier kiểm tra nhiều quy tắc cấu trúc và kiểu của class file, nhưng không thể bảo đảm ứng dụng không có lỗ hổng logic, truy cập dữ liệu sai quyền hay lệ thuộc vào thư viện thiếu an toàn.

## 9. Những nhầm lẫn thường gặp

1. **“JVM đọc trực tiếp mã `.java`.”** Trong luồng phổ biến, `javac` tạo `.class` trước; JVM thực thi bytecode. Chế độ chạy nguồn một bước có thể ẩn bước biên dịch.
2. **“Bytecode là mã máy của CPU.”** Bytecode định nghĩa lệnh cho JVM; runtime phải diễn giải hoặc chuyển đổi chúng bằng cơ chế phù hợp của bản triển khai.
3. **“Một class chạy được trên mọi JVM.”** Cần đúng phiên bản class file, API, thư viện và các điều kiện môi trường.
4. **“JVM chỉ là JIT.”** JIT là một kỹ thuật thực thi/tối ưu; JVM còn có nạp lớp, kiểm tra, quản lý runtime, bộ nhớ, ngoại lệ…
5. **“Có GC thì không thể rò rỉ tài nguyên.”** GC không giải phóng mọi tài nguyên bên ngoài heap theo thời điểm ứng dụng cần; các tham chiếu giữ quá lâu cũng có thể gây tình trạng chiếm bộ nhớ không mong muốn.
6. **“JDK và JVM là một.”** JDK bao gồm các công cụ phát triển và runtime; JVM là thành phần thực thi.
7. **“JVM bảo đảm hiệu năng và hành vi đồng nhất trên mọi máy.”** Đặc tả tạo hợp đồng ngữ nghĩa, còn hiệu năng và một số yếu tố môi trường phụ thuộc triển khai/nền tảng.

## 10. Tự kiểm tra kiến thức

**Câu 1.** Vì sao cùng một `BytecodeDemo.class` có thể chạy trên nhiều CPU khác nhau?

Vì class chứa bytecode theo đặc tả JVM; mỗi nền tảng có một bản JVM tương thích chịu trách nhiệm thực thi chúng trên CPU của nền tảng đó. Còn phải đáp ứng phiên bản bytecode và các phụ thuộc của ứng dụng.

**Câu 2.** `javac` và JIT compiler làm cùng một công việc không?

Không. `javac` chuyển mã nguồn Java sang bytecode trước thời điểm chạy; JIT của một số JVM chuyển một phần bytecode sang mã máy khi chương trình đang thực thi, dựa trên chiến lược runtime.

**Câu 3.** Nếu đã có garbage collector, có thể bỏ `try-with-resources` khi đọc tệp không?

Không. Bộ thu gom rác quản lý bộ nhớ đối tượng, trong khi tệp và những tài nguyên hệ điều hành cần được đóng đúng lúc bằng cơ chế như `try-with-resources`.

**Câu 4.** JVM có phải toàn bộ “Java” không?

Không. Java gồm ngôn ngữ, thư viện, công cụ phát triển, chuẩn class file và runtime. JVM là tầng thực thi bytecode ở trung tâm mô hình đó.

## Kết luận

JVM là **máy thực thi trung gian** nằm giữa bytecode Java và nền tảng thật. Trình biên dịch tạo class file theo một hợp đồng chung; JVM tương thích nạp, kiểm tra, thực thi và quản lý các hoạt động runtime trên từng hệ thống. Chính sự tách biệt này tạo nền tảng cho tính di động của Java, đồng thời cho phép tối ưu trong lúc chạy và cung cấp một môi trường thực thi thống nhất. Khi thiết kế hoặc triển khai ứng dụng, luôn xét thêm **tương thích phiên bản, thư viện, môi trường và đo đạc hiệu năng thực tế**.

### Đọc thêm

- [The Java Virtual Machine Specification (Java SE 21)](https://docs.oracle.com/javase/specs/jvms/se21/html/index.html) — mô hình class file, tập lệnh, runtime data areas và vòng đời nạp/liên kết/khởi tạo lớp.
- [Java `javac` documentation (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html) — công cụ biên dịch Java, trong đó có tùy chọn `--release`.
- [Java `javap` documentation (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html) — kiểm tra thông tin và bytecode trong tệp class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-002">Phân biệt ngôn ngữ Java, Java SE, JVM, JDK và môi trường chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngôn ngữ Java, Java SE, JVM, JDK và môi trường chạy

## Mục tiêu

Sau bài này, bạn có thể phân biệt rõ năm khái niệm thường bị gọi chung là “Java”, giải thích từ mã nguồn đến bytecode và tiến trình chạy, xác định bộ công cụ cần dùng để phát triển/triển khai, và chẩn đoán lỗi do phiên bản hoặc môi trường không tương thích.

## 1. Bản đồ năm khái niệm

| Thành phần | Là gì? | Vai trò chính | Ví dụ |
| --- | --- | --- | --- |
| **Ngôn ngữ Java** | Ngôn ngữ lập trình, có cú pháp, hệ kiểu và ngữ nghĩa được quy định trong Java Language Specification (JLS) | Quy định cách *viết* và hiểu mã nguồn | `class`, `interface`, `record`, `if`, lambda |
| **Java SE (Java Platform, Standard Edition)** | Nền tảng Java tiêu chuẩn với các đặc tả ngôn ngữ, JVM và thư viện API chuẩn theo phiên bản | Quy định hợp đồng của *nền tảng/API tiêu chuẩn* | `java.lang`, `java.util`, `java.time`, `java.io` |
| **JVM (Java Virtual Machine)** | Máy ảo được đặc tả để nạp, xác minh và thực thi mã theo định dạng class file | *Thực thi* bytecode | HotSpot VM, Eclipse OpenJ9 |
| **JDK (Java Development Kit)** | Bộ công cụ phát triển và các thành phần cần thiết để chạy Java | *Biên dịch, chạy, đóng gói và phân tích* | `javac`, `java`, `jar`, `javap`, `jlink` |
| **Môi trường chạy (runtime environment)** | Java runtime thực tế kết hợp với thư viện, hệ điều hành, kiến trúc máy và cấu hình triển khai | Quyết định ứng dụng có *chạy được trong thực tế* hay không | JVM + Java SE modules + dependencies + OS/CPU + cấu hình |

**Quan hệ:** lập trình viên **viết bằng ngôn ngữ Java**, có thể **gọi API Java SE**, dùng **JDK** để biên dịch thành class file, rồi một **JVM** thuộc **môi trường chạy** thực thi bytecode. Không thể dùng năm thuật ngữ này để thay thế lẫn nhau.

## 2. Ngôn ngữ Java: quy tắc của mã nguồn

Ngôn ngữ Java xác định quy tắc khai báo lớp/giao diện, kiểu dữ liệu, tên, biểu thức, câu lệnh, ngoại lệ, generic, lambda và nhiều cấu trúc khác. Tài liệu **Java Language Specification (JLS)** mô tả cú pháp và ngữ nghĩa theo từng phiên bản.

Ví dụ `int x = 2;` phù hợp quy tắc ngôn ngữ; gán `String text = 123;` là lỗi kiểu trong trường hợp thông thường. Cú pháp mới có thể đòi phiên bản ngôn ngữ mới: `record` là tính năng chính thức từ Java 16, nên không thể dùng khi biên dịch với mức ngôn ngữ Java 11.

**Ngôn ngữ không đồng nghĩa với thư viện hoặc máy ảo.** Mã Java có thể gọi thư viện ngoài Java SE như Spring; ngược lại, JVM có thể chạy bytecode được biên dịch từ Kotlin hoặc Scala. Dù có chữ “Java”, Spring không phải cú pháp của ngôn ngữ và không thuộc tập API Java SE tiêu chuẩn.

## 3. Java SE: nền tảng và API chuẩn

**Java SE** là tên nền tảng Java tiêu chuẩn. Nền tảng này bao gồm các đặc tả ngôn ngữ, JVM và thư viện/API tiêu chuẩn tương ứng với phiên bản phát hành. Khi nhấn mạnh “Java SE API”, ta đang nói đến tập API công khai chuẩn hóa, ví dụ:

- `java.lang`: `Object`, `String`, `System`, `Thread` (gói được import ngầm).
- `java.util`: `List`, `Map`, `Optional` và các tiện ích.
- `java.time`: các kiểu ngày/giờ hiện đại.
- `java.io`, `java.nio.file`: nhập/xuất và hệ thống tệp.
- `java.net`, `java.net.http`: mạng và HTTP client.

Từ Java 9, các thành phần nền tảng được tổ chức qua **hệ thống module**; mọi module đều đọc ngầm module cốt lõi `java.base`. Một số API tồn tại ở bản Java SE mới nhưng không có ở bản cũ, vì thế cần kiểm tra phiên bản nền tảng đích.

**Java SE không phải một executable hay một bộ cài duy nhất.** JDK mang đến công cụ và bản triển khai nền tảng. Java SE cũng không đồng nghĩa toàn bộ hệ sinh thái: **Jakarta EE**, **Spring**, nhiều JDBC driver và những thư viện bên thứ ba cần được cung cấp riêng; JavaFX không còn được đóng kèm như một phần của JDK chuẩn kể từ Java 11.

## 4. JVM: tầng thực thi, không phải ngôn ngữ hay bộ công cụ

**JVM** theo *Java Virtual Machine Specification (JVMS)* là máy trừu tượng có hợp đồng về định dạng `.class`, bytecode, loading/linking/initialization và thực thi. **HotSpot** và **OpenJ9** là những cách hiện thực máy ảo ấy. Chúng có thể chọn interpreter, JIT và GC khác nhau trong khi phải giữ ngữ nghĩa được đặc tả.

Khi chạy `java HelloJvm`, launcher của bộ runtime/JDK khởi động JVM; VM nạp và thực thi class file, còn các lời gọi `List.of` và `System.out.println` sử dụng thư viện Java SE. Vì thế **JVM không phải `javac`, không phải toàn bộ Java SE và không phải máy ảo mô phỏng một hệ điều hành**. Các bước và vùng nhớ bên trong JVM được phân tích ở bài về cấu trúc thực thi, thay vì lặp chi tiết loading và JIT tại đây.

## 5. JDK: bộ công cụ phát triển

**Java Development Kit** gồm các công cụ giúp lập trình, biên dịch, kiểm tra, đóng gói, chạy và phân tích ứng dụng Java; đồng thời cung cấp các thành phần runtime.

| Công cụ JDK | Dùng để |
| --- | --- |
| `javac` | Biên dịch nguồn `.java` thành class file `.class` |
| `java` | Khởi động JVM và chạy class, JAR hoặc module |
| `jar` | Tạo/xem/cập nhật tệp JAR |
| `javadoc` | Sinh tài liệu API |
| `javap` | Xem thông tin class file; `javap -c` cho biết lệnh bytecode |
| `jshell` | Thử nhanh biểu thức/đoạn Java trong REPL |
| `jdeps` | Phân tích phụ thuộc của class/JAR |
| `jlink` | Tạo runtime image tùy biến từ các module |

Ví dụ, **`javac` không phải JVM**: `javac` thông thường phân tích mã nguồn, kiểm tra kiểu và sinh bytecode; JVM đọc class file và thực thi bytecode đó, có thể tối ưu/JIT khi chạy. Cũng không nên gọi Java SE là JDK: Java SE mô tả nền tảng, còn JDK là bộ công cụ gắn với một bản triển khai cụ thể.

JDK có các mức phát hành như **17**, **21** và nhiều bản phân phối từ các vendor. Cần phân biệt **phiên bản Java/JDK**, **vendor/bản build** và **triển khai JVM**; chúng ảnh hưởng đến API được cung cấp, mức class file, bản vá, cấu hình và đôi khi đặc tính runtime.

## 6. “Môi trường chạy”, JRE và runtime image

Trong cách gọi truyền thống, **JRE (Java Runtime Environment)** là một gói có JVM và thư viện/tài nguyên cần để chạy Java nhưng không có trọn bộ công cụ phát triển như `javac`. Thời Java 8, bộ cài JRE và JDK thường được phân phối riêng.

Từ Java 9, hệ thống module và runtime image thay đổi cách phân phối; nhiều vendor Java hiện đại không phát hành một bộ cài mang tên “JRE” tách biệt theo kiểu Java 8. Trong thực tế, Java 17+ thường chạy bằng:
- **JDK đầy đủ**, có cả công cụ và runtime.
- **Runtime image** do nhà cung cấp đóng gói, có thể không kèm compiler.
- **Runtime image do `jlink` tạo**, chỉ bao gồm những module được chọn và phụ thuộc module cần thiết.

Môi trường chạy còn rộng hơn Java runtime: bao gồm **OS/CPU**, native libraries, JAR dependencies, classpath/module path, quyền hệ thống, biến môi trường, file cấu hình, bộ nhớ/tài nguyên và kết nối bên ngoài. Ví dụ, một JVM hoạt động tốt vẫn không thể tự cung cấp JAR của framework đang bị thiếu; một thư viện native cho Linux/x86-64 cũng không tự chạy trên Windows/ARM64.

**Phân biệt rõ:**
- **JVM** là thành phần thực thi bytecode.
- **JRE/runtime image** là tập thành phần được đóng gói cho nhu cầu chạy chương trình.
- **JDK** bao gồm công cụ phát triển và khả năng chạy.
- **Môi trường chạy thực tế** còn gồm tất cả điều kiện deployment kể trên.
- Một máy có thể có một JDK nhưng chạy nhiều **tiến trình JVM** khác nhau.

## 7. Gắn năm khái niệm vào một chương trình cụ thể

```text
Nguồn Java (cú pháp/ngữ nghĩa) + Java SE API (List, String, System)
          |
          | javac: công cụ trong JDK
          v
       HelloJvm.class
          |
          | java launcher → JVM
          v
Thư viện Java SE + dependencies + OS/CPU + cấu hình
          |
          v
      Chương trình chạy
```

Lưu tệp **`HelloJvm.java`**:

```java
import java.util.List;

public class HelloJvm {
    public static void main(String[] args) {
        List<String> concepts = List.of(
                "Ngôn ngữ Java",
                "Java SE",
                "JVM");
        System.out.println("Ba lớp: " + String.join(" | ", concepts));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac --release 17 HelloJvm.java
java HelloJvm
```

**Stdout chính xác**:

```text
Ba lớp: Ngôn ngữ Java | Java SE | JVM
```

Trong ví dụ, khai báo lớp, generic và lời gọi phương thức thuộc **ngôn ngữ Java**; `List`, `String.join` và `System.out` thuộc **API Java SE**; `javac` và `java` nằm trong **JDK**; **JVM** thực thi class file; **runtime environment** bao gồm cả thư viện, phụ thuộc và OS. `--release 17` chọn ngôn ngữ, class-file version và Java SE API mục tiêu, **không tự kiểm chứng** dependency hay thư viện native ở máy đích.

Để theo dõi **chính xác các bước biên dịch và opcode**, dùng `javap -c` cùng bài tiếp theo về đường đi `.java → .class → mã máy`. Phân loại năm khái niệm ở đây không đòi người học thuộc thuật toán class loading hay chi tiết JIT.

## 8. Kiểm tra phiên bản và tương thích

~~~bash
java -version
javac -version
~~~

Lệnh thứ nhất chỉ runtime/launcher thực tế được gọi; lệnh thứ hai chỉ compiler thực tế được gọi. **Hai lệnh có thể chọn hai bản cài khác nhau** vì `PATH`, `JAVA_HOME`, IDE, Maven/Gradle toolchains hoặc container; `JAVA_HOME` là quy ước nhiều công cụ sử dụng, còn executable đang chạy mới quyết định JVM thực tế.

Khi xác minh một ứng dụng, đặt bốn câu hỏi độc lập:

1. **Ngôn ngữ:** compiler có hiểu cú pháp và mức `--release`/`--source` yêu cầu không?
2. **Bytecode:** runtime JVM có hỗ trợ class file tạo ra không? Ví dụ Java 17 ứng với class file major **61**, Java 21 ứng với major **65**. JVM 17 không hiểu class file major 65.
3. **API và thư viện:** runtime có các API Java SE, module, JAR bên thứ ba tương ứng không?
4. **Nền tảng:** OS, CPU, native binary, cấu hình, quyền và tài nguyên có phù hợp không?

**Trường hợp A — `UnsupportedClassVersionError`:** thường class file được tạo cho bản JVM mới hơn runtime đang chạy. Kiểm tra phiên bản, biên dịch lại với target phù hợp hoặc nâng JVM.

**Trường hợp B — có `java` nhưng không có `javac`:** có thể đang dùng runtime image hoặc đường dẫn chỉ trỏ tới runtime. Chạy class file thích hợp có thể được, nhưng cần compiler/JDK nếu muốn biên dịch thông thường.

**Trường hợp C — `ClassNotFoundException`/`NoClassDefFoundError`:** thường phải kiểm tra JAR/module/classpath, lỗi khởi tạo lớp và chuỗi nguyên nhân trong stack trace. Đây không tự động là lỗi của cú pháp Java.

**Trường hợp D — chạy máy cá nhân nhưng lỗi trên server:** so sánh phiên bản, các module cắt gọn bằng `jlink`, native libraries, OS/CPU và cấu hình triển khai. **“Write once, run anywhere”** diễn đạt mục tiêu tính di động; không có nghĩa mọi phụ thuộc bên ngoài bytecode tự tương thích.

## 9. Những khẳng định sai thường gặp

| Hiểu sai | Cách nói chính xác |
| --- | --- |
| “Java = JVM.” | Java là ngôn ngữ, JVM là máy ảo; JVM có thể chạy bytecode từ nhiều ngôn ngữ. |
| “Java SE chính là JDK.” | Java SE là nền tảng/đặc tả; JDK là bộ công cụ và một bản triển khai các thành phần cần thiết. |
| “`javac` sinh trực tiếp mã máy.” | `javac` thông thường sinh JVM bytecode; VM thực thi và có thể JIT biên dịch mã máy. |
| “JVM là một máy ảo chứa hệ điều hành riêng.” | JVM thực thi bytecode và dựa vào OS bên dưới. |
| “Cài Java runtime là tự động có compiler.” | Runtime image có thể không chứa `javac`. |
| “Không có gói JRE riêng thì không chạy được Java.” | JDK hoặc runtime image phù hợp đã có thể chạy ứng dụng. |
| “JDK mới luôn chạy được trên JVM cũ.” | Bytecode phải nhắm đúng mức JVM; API và phụ thuộc cũng cần tương thích. |
| “JAR là máy ảo.” | JAR là định dạng đóng gói class files và tài nguyên. |

## 10. Tự kiểm tra và đáp án

**Câu 1.** Trong `List.of("A", "B")`, cú pháp gọi phương thức và API `List.of` thuộc khái niệm nào?

**Đáp án:** cú pháp thuộc **ngôn ngữ Java**; `List.of` là **API Java SE** (được bổ sung từ Java 9).

**Câu 2.** Có lệnh `java` nhưng không có `javac`. Có thể chạy chương trình Java không?

**Đáp án:** **Có thể**, nếu runtime và tất cả dependency tương thích. Nhưng không thể khẳng định máy có compiler; cần JDK nếu muốn dùng `javac` để biên dịch nguồn theo cách thông thường.

**Câu 3.** JDK 21 biên dịch bằng `javac --release 17`; chương trình có chắc chạy được trên JVM 17 không?

**Đáp án:** tùy chọn nhắm đến ngôn ngữ, bytecode và Java SE API của Java 17 nên giúp đạt tương thích ở những lớp này, nhưng **chưa đủ bảo đảm**: phải kiểm tra thư viện ngoài Java SE, các dependency, native code và môi trường thực tế.

**Câu 4.** Kotlin tạo bytecode để chạy trên JVM. Có phải Kotlin trở thành ngôn ngữ Java không?

**Đáp án:** **Không.** Kotlin là ngôn ngữ riêng; có thể dùng JVM làm môi trường thực thi bytecode.

**Câu 5.** Vai trò riêng của Java SE và JVM trong việc dùng `java.util.List` là gì?

**Đáp án:** **Java SE** đặc tả API `java.util.List`; bản triển khai nền tảng cung cấp thư viện tương ứng; **JVM** thực thi bytecode gọi API đó.

## 11. Mô hình nhớ nhanh

- **Language** → viết mã bằng cú pháp/ngữ nghĩa Java.
- **Java SE** → hợp đồng nền tảng và API chuẩn.
- **JDK** → biên dịch, đóng gói, chạy, phân tích.
- **JVM** → nạp và thực thi class file/bytecode.
- **Runtime environment** → JVM, thư viện, OS, cấu hình và mọi điều kiện để chương trình chạy thật.

Khi gặp lỗi, hãy xác định nó nằm ở lớp nào: **nguồn/cú pháp → compiler/tool → class file/JVM → API/dependency → cấu hình/máy chạy**. Phân biệt đúng các lớp giúp chọn đúng bước xử lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-003">Đường đi của chương trình: mã nguồn → javac → class → JVM → mã máy</a>

<details>
<summary>Xem chi tiết</summary>

# Đường đi của chương trình Java: mã nguồn → javac → .class → JVM → mã máy

Khi viết một chương trình Java, CPU **không trực tiếp chạy tệp `.java`**. Với quy trình thông thường, trình biên dịch `javac` chuyển mã nguồn thành **Java bytecode** trong tệp `.class`; khi khởi chạy, JVM nạp và kiểm tra các lớp, rồi thực thi bytecode bằng bộ thông dịch và/hoặc biên dịch JIT thành **lệnh máy của CPU đang dùng**. Đây là lý do cùng một tệp `.class` có thể chạy trên những hệ điều hành và bộ xử lý khác nhau, miễn là có JVM tương thích và các phụ thuộc phù hợp.

```text
Hello.java (mã nguồn Java)
       │ javac: phân tích + kiểm tra kiểu + sinh bytecode
       ▼
Hello.class (bytecode JVM, chưa phải mã máy x86/ARM)
       │ java: khởi động JVM và tìm lớp main
       ▼
JVM: Class Loading → Verification/Linking → Initialization khi cần
       │
       ├── Interpreter: đọc và thực thi từng lệnh bytecode
       └── JIT: biên dịch mã được chọn thành mã máy, có thể tối ưu
                    │
                    ▼
       Mã máy + thư viện/runtime của JVM
                    │
                    ▼
           Hệ điều hành / CPU thực thi
```

Sơ đồ là **mô hình tư duy**, không có nghĩa mọi lớp đều được nạp ngay lúc khởi động, mọi bytecode luôn được thông dịch trước khi JIT, hoặc mọi phương thức đều được JIT biên dịch. Chiến lược cụ thể tùy phiên bản và bản triển khai JVM.

## 1. Phân biệt mã nguồn, bytecode và mã máy

| Tầng | Dạng biểu diễn | Ai xử lý? | Ý nghĩa |
| --- | --- | --- | --- |
| Mã nguồn | `.java`, cú pháp Java con người đọc được | `javac` hoặc công cụ biên dịch khác | Định nghĩa lớp, phương thức và quy tắc chương trình |
| Bytecode | `.class` theo định dạng JVM Class File | JVM (class loader, verifier, interpreter/JIT) | Tập lệnh trừu tượng, gần như độc lập kiến trúc CPU |
| Mã máy | Lệnh native phù hợp x86-64, AArch64, ... | CPU, với sự hỗ trợ của hệ điều hành/runtime | Lệnh thực sự được bộ xử lý thực thi |

- `.class` **không phải** bản dịch máy cuối cùng cho một hệ điều hành/CPU. Một tệp class chứa constant pool, metadata lớp/phương thức/trường và các phần `Code` bytecode cho những phương thức có mã; phương thức `abstract` hoặc `native` không có phần bytecode như phương thức Java thông thường.
- JVM được xây dựng riêng cho nền tảng đích và bản thân JVM là chương trình native. Ngay cả khi chạy bytecode bằng interpreter, CPU vẫn đang chạy **mã máy của interpreter** để diễn giải các lệnh bytecode.
- Khả năng “write once, run anywhere” phụ thuộc vào **phiên bản class file, API/runtime sẵn có, thư viện bên ngoài và hành vi phụ thuộc nền tảng**. Mã dùng native library hoặc giả định đường dẫn/tính năng hệ điều hành vẫn có thể không portable.

### JDK, JVM và lệnh `java`

**JDK** (Java Development Kit) cung cấp bộ công cụ phát triển như `javac`, `java`, `javap`, công cụ đóng gói và một runtime Java. **JVM** là máy ảo thực thi class file; runtime còn có các thư viện chuẩn Java. `javac` và `java` là **hai công cụ khác vai trò**: `javac` tạo bytecode, còn trình khởi chạy `java` thiết lập runtime/JVM và thực thi chương trình.

```bash
javac --version
java --version
```

Hai lệnh nên cùng một môi trường JDK dự định sử dụng. Nếu thiết lập `PATH` trỏ tới hai bản khác nhau, có thể xuất hiện lỗi tương thích dù mã nguồn đã biên dịch thành công.

## 2. Ví dụ chạy được: đi từ `.java` tới `.class`

Tạo tệp **`Hello.java`** (tên tệp phải khớp với tên `public class Hello`):

```java
public class Hello {
    public static void main(String[] args) {
        int left = 20;
        int right = 22;
        int sum = left + right;
        System.out.println("Kết quả: " + sum);
    }
}
```

Trong terminal có JDK 17 trở lên, đứng tại thư mục chứa `Hello.java`:

```bash
mkdir out
javac -encoding UTF-8 -d out Hello.java
java -cp out Hello
```

Kết quả chuẩn:

```text
Kết quả: 42
```

Giải thích từng phần:

1. `mkdir out` tạo thư mục đặt sản phẩm biên dịch; tùy hệ điều hành/lệnh shell có thể dùng cách tạo thư mục tương đương.
2. `javac -encoding UTF-8 -d out Hello.java` đọc mã nguồn, phân tích cú pháp, kiểm tra các quy tắc ngôn ngữ/kiểu và tạo **`out/Hello.class`**. Tùy mã nguồn, có thể sinh thêm class file cho các lớp khác; ví dụ này chỉ cần `Hello.class`.
3. `java -cp out Hello` đặt `out` lên classpath, tìm tên lớp **`Hello`**, khởi động runtime và gọi phương thức `public static void main(String[] args)`. Tham số `Hello` là **tên lớp**, không phải `Hello.class` hay đường dẫn đến tệp `.java`.
4. JVM và các thư viện chuẩn thực thi `main`; `System.out.println` ghi dòng kết quả ra standard output. Việc in ra màn hình cuối cùng đi qua các chức năng của runtime/hệ điều hành.

Nếu khai báo `package com.example;`, tệp thường đặt tương ứng với package, sản phẩm nằm tại `out/com/example/Hello.class` và lệnh chạy là `java -cp out com.example.Hello`. Đây là **fully qualified class name**; thư mục classpath là **gốc của cây package**, không phải thư mục chứa `Hello.class` sau cùng.

### Nhìn vào bytecode thật bằng `javap`

```bash
javap -classpath out -c Hello
```

Công cụ `javap` phân rã thông tin class file và hiển thị bytecode. Trong phương thức `main`, ví dụ này thường có các thao tác tương đương: đưa số nguyên lên operand stack, lưu/đọc local variables, dùng **`iadd`** để cộng, lấy `System.out` và gọi `println`. Java 17 có thể biểu diễn phép nối chuỗi qua **`invokedynamic`**. Số offset, constant-pool index và cách biên dịch từng biểu thức là **chi tiết đầu ra của compiler**: hãy xem dữ liệu thật từ `javap`, không thuộc lòng một listing giả định.

`javap -c` cho thấy **bytecode**, không chứng minh lệnh máy JIT sẽ có hình dạng nào; JIT được phép tối ưu hoặc loại bỏ những thao tác trung gian miễn là giữ ngữ nghĩa cần thiết.

## 3. `javac` làm gì ở thời điểm biên dịch?

Nói đơn giản, trình biên dịch thực hiện chuỗi công việc sau:

1. **Đọc và phân tích cú pháp:** biến chuỗi ký tự/tokens thành cấu trúc chương trình, kiểm tra cú pháp hợp lệ.
2. **Phân giải tên và kiểm tra ngữ nghĩa/kiểu:** xác định lớp, biến, overload, quyền truy cập, kiểu trả về, phép gán và các luật ngôn ngữ. Nếu sai, `javac` báo lỗi và không tạo bản class file hợp lệ cho phần bị lỗi.
3. **Biến đổi các cấu trúc mức cao:** nhiều tính năng Java như vòng `for-each`, generic (type erasure), lambda hoặc nối chuỗi được biểu diễn lại bằng bytecode và metadata phù hợp. Một số tính năng có thể sử dụng `invokedynamic` thay vì mô phỏng mọi thứ bằng chuỗi lệnh đơn giản.
4. **Sinh class file:** xuất constant pool, descriptor, bytecode và thuộc tính cần cho runtime. File format có **major version** để JVM biết class file có tương thích hay không.

Ví dụ, `javac` phát hiện được lỗi kiểu như gán `String` cho `int` nhưng không dự đoán được mọi lỗi khi chương trình chạy: chia cho 0, truy cập phần tử ngoài mảng, lỗi mạng hay thiếu một lớp ở runtime có thể chỉ lộ ra sau đó.

**Lưu ý:** kiểm tra của `javac` và **bytecode verification của JVM** là hai bước khác nhau. `javac` xác minh chương trình nguồn, còn JVM kiểm tra tính hợp lệ/an toàn cấu trúc bytecode khi nạp/liên kết lớp, kể cả class file không được tạo bởi `javac`.

## 4. Khi chạy `java -cp out Hello`, JVM làm gì?

### 4.1. Khởi động và nạp lớp (loading)

Trình `java` xác định cách khởi chạy (lớp main, JAR, module hoặc source-file mode), cấu hình JVM, classpath/module path và các tham số. JVM khởi tạo runtime thiết yếu và sử dụng hệ thống class loader để tìm định nghĩa lớp khi cần.

- **Bootstrap class loader** chịu trách nhiệm cho các lớp nền tảng cốt lõi.
- **Platform class loader** và **application/system class loader** phục vụ các nhóm lớp nền tảng khác và lớp ứng dụng, tùy cấu hình/module.
- Việc ủy quyền class loading thường giúp ưu tiên định nghĩa được phép ở loader cha; có thể có custom class loader cho plugin, container hoặc framework.

Loading đưa **định nghĩa lớp** từ class file hoặc nguồn khác vào runtime. Chỉ đặt tệp `.class` trên đĩa chưa có nghĩa JVM đã nạp nó. Nạp/lazy linking có thể xảy ra trong lúc chương trình đang chạy.

### 4.2. Linking: verification, preparation, resolution

Theo mô hình vòng đời lớp trong JVM:

- **Verification:** kiểm tra class file/bytecode có tuân thủ các ràng buộc cấu trúc và kiểu cần thiết; lỗi có thể dẫn đến `VerifyError`.
- **Preparation:** cấp phát và gán **giá trị mặc định** cho các static fields của lớp (chẳng hạn `0` cho `int`, `null` cho reference). Đây chưa phải lúc thực thi tất cả câu lệnh khởi tạo static trong mã Java.
- **Resolution:** biến các symbolic reference trong constant pool thành tham chiếu runtime cụ thể khi cần; việc resolution có thể được thực hiện muộn, không bắt buộc xử lý toàn bộ ngay trước `main`.

Các khái niệm loading/linking/initialization khác nhau; đừng gộp tất cả thành “JVM đọc class rồi chạy luôn”.

### 4.3. Initialization và lời gọi `main`

Khi một lớp được sử dụng chủ động trong những trường hợp luật JVM quy định, JVM thực hiện **class initialization**: các static field initializers và `static { ... }` chạy theo thứ tự tương ứng trong lớp, thông qua phương thức khởi tạo lớp ở mức bytecode (`<clinit>`, khi có). Chỉ sau các điều kiện khởi tạo cần thiết, runtime mới thực thi phần chương trình liên quan.

Trong ví dụ `Hello`, entry point là `main(String[] args)`. JVM thiết lập luồng thực thi và các stack frames để chạy phương thức; có thể gọi tiếp các phương thức trong thư viện chuẩn và mã của ứng dụng.

## 5. Từ bytecode đến lệnh máy: interpreter và JIT

Bytecode bao gồm các lệnh như `iload`, `istore`, `iadd`, `invokevirtual`, `return`. Chúng vận hành trên **mô hình stack** của JVM (operand stack, local variables, frame), không phải trực tiếp là lệnh thanh ghi của một CPU cụ thể.

**Interpreter:** runtime có thể đọc lần lượt lệnh bytecode của một phương thức và thực hiện hành vi tương ứng. Đây là cách có thể bắt đầu thực thi mã nhanh mà chưa cần biên dịch native riêng cho phương thức ấy, đổi lại một số chi phí diễn giải lệnh.

**JIT (Just-In-Time compiler):** khi runtime nhận thấy những đoạn mã có lợi để tối ưu, JIT có thể chuyển bytecode sang **mã máy cho CPU hiện tại**, lưu trong bộ nhớ mã và tái sử dụng cho các lần thực thi sau. HotSpot thường dùng cơ chế **tiered compilation** kết hợp thu thập thông tin thực thi với các trình biên dịch như C1/C2; đây là chi tiết của một JVM phổ biến, **không phải cam kết chung của mọi JVM**.

Ví dụ cơ chế có thể diễn ra:

```text
main() gọi calculate() nhiều lần
        │
        ├── Ban đầu: interpreter và/hoặc tầng mã nhanh
        ├── JVM quan sát số lần gọi, nhánh hay gặp, kiểu đối tượng thực tế
        ├── JIT tạo mã máy tối ưu cho calculate()
        └── Những lần gọi tiếp theo có thể chạy mã máy đã biên dịch
```

JIT có thể **inline** lời gọi, tối ưu vòng lặp, bỏ kiểm tra dư thừa khi chứng minh được an toàn, và tận dụng profiling. Nếu một giả định tối ưu không còn đúng, runtime có thể **deoptimize** để quay về trạng thái thực thi phù hợp. Không được suy ra rằng “phương thức chạy lần đầu = interpreter, lần thứ hai = JIT”; ngưỡng và chiến lược phụ thuộc JVM, tùy chọn và đặc điểm chương trình.

CPU cuối cùng chạy cả mã native của JVM/interpreter, mã do JIT sinh (nếu có), mã runtime và thư viện native. **Mã máy không được ghi thành tệp `.exe` chỉ vì chạy `java Hello`**; JIT thường biên dịch tại runtime trong bộ nhớ tiến trình. Các cơ chế AOT/native image là con đường khác, nằm ngoài luồng `javac → .class → JVM` cơ bản.

### Bytecode portable, hiệu năng vẫn phụ thuộc môi trường

Cùng bytecode có thể được các JVM khác nhau chạy trên x86-64 hoặc ARM64; runtime chọn cơ chế thực thi tương thích với CPU. Nhưng hiệu năng phụ thuộc JIT, bộ nhớ/GC, profile tải, môi trường hệ điều hành và tài nguyên. Kết luận rằng “Java luôn chậm vì phải thông dịch” là không đúng với các runtime có JIT; kết luận “Java luôn nhanh như native” cũng không thể khái quát.

## 6. Các vùng dữ liệu xuất hiện trong lúc thực thi

Dù bài học tập trung vào đường đi biên dịch, cần phân biệt một số nơi runtime sử dụng:

| Khái niệm | Vai trò trong lúc chương trình chạy |
| --- | --- |
| **Heap** | Chứa các đối tượng/mảng được cấp phát; garbage collector quản lý thu hồi bộ nhớ phù hợp |
| **JVM stack của từng thread** | Chứa stack frames cho các lời gọi phương thức Java; frame có local variables, operand stack và thông tin liên kết |
| **Method area / metadata lớp** | Lưu cấu trúc runtime liên quan đến lớp, phương thức và constant pool; HotSpot thường dùng **Metaspace** cho nhiều metadata lớp |
| **PC register của từng thread** | Theo dõi vị trí lệnh JVM liên quan đến luồng thực thi hiện tại |
| **Native method stack / bộ nhớ native** | Hỗ trợ mã native và runtime; chi tiết phụ thuộc JVM và nền tảng |
| **Code cache (ở HotSpot)** | Chứa mã máy do JIT biên dịch và một số mã được runtime tạo |

`javac` tạo **class file** trên đĩa; JVM sau đó tạo nhiều cấu trúc **trong bộ nhớ** để nạp lớp và chạy. Đừng nhầm **`Hello.class` trên đĩa**, **metadata lớp trong runtime** và **đối tượng `new Hello()` trên heap** thành cùng một thứ.

## 7. Phiên bản Java quyết định khả năng chạy class file

JVM cần hỗ trợ **class-file major version** của bytecode được nạp. Ví dụ phổ biến:

| Mục tiêu biên dịch | Class-file major version |
| --- | ---: |
| Java 17 | 61 |
| Java 21 | 65 |

Chạy class file được tạo với target Java 21 trên runtime chỉ hỗ trợ Java 17 sẽ phát sinh `UnsupportedClassVersionError`. Trên JDK 21, có thể biên dịch cho Java 17 bằng:

```bash
javac --release 17 -encoding UTF-8 -d out Hello.java
```

`--release 17` chọn ngữ nghĩa ngôn ngữ, định dạng class file và API nền tảng tương ứng với release đích (trong khả năng công cụ hỗ trợ). **Không đảm bảo** các thư viện ngoài hoặc native dependencies chạy được chỉ nhờ thay `--release`. Ngược lại, bytecode target 17 thường có thể chạy trên JVM mới hơn nếu không có bất tương thích từ thư viện/cấu hình.

Có thể xem major version bằng:

```bash
javap -classpath out -verbose Hello
```

Tìm dòng `major version` trong kết quả. Không cần mở tệp nhị phân bằng trình soạn thảo văn bản.

## 8. Các cách khởi chạy và nơi chứa bytecode

- **Lớp trên classpath:** `java -cp out Hello` tải `Hello` qua classpath; với package, dùng tên đầy đủ của lớp.
- **JAR:** `jar --create --file app.jar --main-class Hello -C out .` đóng gói các class; `java -jar app.jar` chạy entry point từ manifest. JAR là tệp lưu trữ chứa bytecode/tài nguyên, bản thân JAR không phải mã máy.
- **Module:** ứng dụng Java module dùng module path và `java --module-path ... -m module.name/com.example.Main` theo cấu trúc module; nó thay đổi cách tổ chức/resolve module, không thay bản chất bytecode → JVM.
- **Source-file mode (JDK 11+):** `java Hello.java` có thể biên dịch và chạy tệp nguồn ở một lệnh. Nó vẫn dùng compiler/runtime bên dưới; đừng hiểu nhầm rằng CPU trực tiếp chạy cú pháp `.java`.

Với ví dụ `Hello.java`, quy trình tách `javac` rồi `java` vẫn hữu ích nhất để quan sát được **tệp class trung gian**.

## 9. Cách xác định lỗi xảy ra ở tầng nào

| Triệu chứng | Giai đoạn cần kiểm tra | Hướng kiểm tra |
| --- | --- | --- |
| `javac` báo `';' expected`, `cannot find symbol`, `incompatible types` | Biên dịch nguồn | Cú pháp, import, kiểu và dependency của compiler |
| `Could not find or load main class Hello` | Launch / class loading | Tên lớp, package, vị trí `Hello.class`, `-cp out` |
| `UnsupportedClassVersionError` | Nạp class trên JVM | So sánh `java --version`, `javap -verbose`, cấu hình `--release` |
| `NoClassDefFoundError` | Liên kết/nạp hoặc sử dụng lớp tại runtime | Dependency runtime, classpath/module path, lỗi khởi tạo trước đó; đọc nguyên nhân kèm theo |
| `VerifyError` | Bytecode verification | Class file không hợp lệ, bytecode tool/instrumentation hoặc xung đột phiên bản |
| `ExceptionInInitializerError` | Class initialization | Static initializer hoặc static field initialization ném exception |
| `NullPointerException`, `ArithmeticException` | Thực thi | Logic và giá trị runtime; biên dịch thành công không loại bỏ lỗi này |

Không gán mọi lỗi “không chạy được” cho JVM. Ví dụ `cannot find symbol` của `javac` là lỗi **compile-time**; lỗi thiếu lớp khi `java` nạp dependency là vấn đề **runtime**. Khi có lỗi, hãy giữ nguyên **stack trace** và kiểm tra chính xác giai đoạn.

## 10. Tự kiểm chứng đường đi của một chương trình

Có thể thực hiện chuỗi thao tác theo thứ tự:

1. Chạy `javac --version` và `java --version` để biết compiler/runtime đang dùng.
2. Tạo `Hello.java`, chạy `javac -encoding UTF-8 -d out Hello.java` rồi xác nhận có tệp `out/Hello.class`.
3. Chạy `javap -classpath out -c Hello` để quan sát các lệnh bytecode, trong đó có hành vi cộng `20 + 22`.
4. Chạy `java -cp out Hello` và xác nhận chỉ có dòng `Kết quả: 42` từ chương trình ví dụ.
5. Thử đổi `int right = 22;` thành `int right = "22";`: lỗi xuất hiện ở **`javac`**, chương trình mới không biên dịch thành công.
6. Phục hồi tệp, biên dịch lại và thử chạy bằng tên lớp sai hoặc classpath sai: lúc này lỗi xuất hiện ở **bước `java` tìm/nạp lớp**.

### Câu hỏi tự kiểm tra

- `javac` có tạo lệnh máy x86-64 trực tiếp không? **Không**; thông thường nó tạo bytecode trong `.class`.
- Nếu JVM sử dụng interpreter, CPU có thực thi bytecode trực tiếp như mã native không? **Không**; CPU thực thi mã máy của interpreter.
- Tại sao có thể chạy cùng `.class` trên CPU khác? **JVM của từng nền tảng** hiểu cùng định dạng bytecode và tạo/thực thi hành vi thích hợp.
- JIT có bắt buộc biên dịch mọi phương thức không? **Không**; tùy JVM và hành vi chương trình.
- Java biên dịch thành công có đồng nghĩa mọi lỗi đã bị loại bỏ không? **Không**; class loading, initialization và logic runtime vẫn có thể lỗi.

Khi nhìn vào toàn bộ đường đi, hãy phân biệt chính xác **`javac` biến mã nguồn thành bytecode**, **JVM xác minh/nạp/khởi tạo lớp**, và **runtime diễn giải hoặc JIT biên dịch bytecode để CPU có thể thực thi các lệnh native**. Các lớp trung gian giải thích đồng thời khả năng portable, cơ chế tối ưu runtime và nguồn gốc của từng nhóm lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-004">Bytecode độc lập nền tảng và những giới hạn của tính khả chuyển</a>

<details>
<summary>Xem chi tiết</summary>

# Bytecode độc lập nền tảng và những giới hạn của tính khả chuyển

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao một tệp Java bytecode có thể chạy trên nhiều hệ điều hành và kiến trúc CPU.
- Phân biệt tính độc lập nền tảng của **định dạng bytecode** với tính khả chuyển của **một ứng dụng Java hoàn chỉnh**.
- Hiểu vai trò của trình biên dịch, class loader, bộ xác minh bytecode, interpreter và JIT compiler.
- Nhận biết các giới hạn thực tế: phiên bản JDK/JVM, API, thư viện ngoài, native code, hệ điều hành, cấu hình và dữ liệu môi trường.
- Biên dịch một chương trình với phiên bản Java mục tiêu, kiểm tra bytecode và xây dựng cách thử nghiệm tính khả chuyển.

## 2. JVM biến một định dạng chung thành việc thực thi trên từng máy

Trong mô hình Java thông thường, **javac** biên dịch mã nguồn **.java** thành các tệp **.class** chứa bytecode theo định dạng Java Virtual Machine Class File. Một tệp **.class** mô tả lớp, phương thức, constant pool, thuộc tính và các lệnh cho máy ảo Java. Nó không chứa mã máy x86-64 hoặc ARM64 được tạo riêng cho một hệ điều hành cụ thể.

Dòng xử lý khái quát:

~~~text
Mã nguồn Java (.java)
          |
          v
  javac / trình biên dịch
          |
          v
 Java bytecode (.class)  ---> có thể đóng gói trong .jar
          |
          v
  JVM tương thích trên hệ điều hành và CPU đích
          |
          +--> class loading, verification, linking, initialization
          |
          +--> interpreter và/hoặc JIT compiler
          |
          v
 Mã máy và các lời gọi hệ thống phù hợp với máy đích
~~~

JVM dành cho Windows/x86-64 và JVM dành cho Linux/ARM64 có phần triển khai gắn với nền tảng khác nhau, nhưng cùng hiểu các lệnh bytecode và phải tuân theo ngữ nghĩa mà đặc tả Java yêu cầu. Ví dụ, phép cộng hai số nguyên kiểu **int** có cùng quy tắc tính toán Java; JVM có thể thực hiện bằng interpreter hoặc chuyển thành lệnh CPU bằng JIT.

**Từ khóa cần phân biệt:**

- **Độc lập nền tảng của bytecode:** định dạng lệnh và quy ước class file không được thiết kế riêng cho CPU/OS.
- **Tương thích runtime:** máy đích có JVM hỗ trợ phiên bản class file, các tính năng và API mà chương trình yêu cầu.
- **Khả chuyển ứng dụng:** mã, thư viện, tài nguyên, cấu hình và tương tác môi trường vẫn hoạt động đúng trên các nền tảng mục tiêu.
- **Tính tái lập:** cùng một đầu vào cho cùng kết quả quan sát được; đây là mục tiêu riêng, không tự động được bảo đảm chỉ vì bytecode có thể chạy ở nhiều nơi.

Khẩu hiệu “write once, run anywhere” nói về lợi thế của mô hình Java khi có runtime tương thích. Nó **không có nghĩa** mọi ứng dụng Java đều chạy được trên mọi máy chỉ bằng cách sao chép tệp JAR.

## 3. Những gì bytecode chung giúp bảo đảm

Một class file hợp lệ, không phụ thuộc native code và dùng các tính năng được JVM đích hỗ trợ có thể được nạp trên nhiều hệ điều hành mà không phải biên dịch lại từ nguồn. JVM kiểm tra cấu trúc, tính an toàn kiểu và các ràng buộc liên quan trong quá trình nạp/liên kết trước khi hoặc khi các phần mã được sử dụng.

Các ví dụ thường có tính khả chuyển tốt gồm thuật toán thuần Java, phép toán nguyên, cấu trúc dữ liệu, thao tác chuỗi theo API chuẩn và logic nghiệp vụ không ngầm giả định đặc điểm hệ thống. API thư viện chuẩn giúp che đi nhiều khác biệt nền tảng, chẳng hạn **Path**, **Files**, **Socket**, nhưng không xóa bỏ các khác biệt thật về quyền truy cập, hệ thống tệp hay mạng.

**Không nên nhầm:** “Bytecode có thể được hiểu bởi JVM khác” với “hành vi mọi chương trình luôn giống nhau”. Đặc tả quy định nhiều ngữ nghĩa ngôn ngữ, nhưng vẫn có các giá trị phụ thuộc môi trường, thời điểm, tài nguyên hoặc cách hệ điều hành lập lịch.

## 4. Ví dụ Java 17+: một tệp class, nhiều môi trường

Tạo tệp **PortabilityDemo.java** với nội dung đầy đủ:

~~~java
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class PortabilityDemo {
    static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        String greeting = "Chào Java";
        byte[] bytes = greeting.getBytes(StandardCharsets.UTF_8);
        String restored = new String(bytes, StandardCharsets.UTF_8);

        Path relativePath = Path.of("data", "report.txt");

        System.out.println("sum=" + add(2, 3));
        System.out.println("utf8RoundTrip=" + greeting.equals(restored));
        System.out.println("segments=" + relativePath.getNameCount());
    }
}
~~~

Với JDK 17 trở lên, chạy các lệnh sau trong thư mục chứa tệp nguồn (thư mục **out** cần được tạo trước):

~~~bash
mkdir out
javac -encoding UTF-8 --release 17 -d out PortabilityDemo.java
java -cp out PortabilityDemo
javap -classpath out -c PortabilityDemo
~~~

Kết quả của lệnh **java**:

~~~text
sum=5
utf8RoundTrip=true
segments=2
~~~

Giải thích:

1. **add(2, 3)** dùng phép cộng số nguyên theo ngữ nghĩa Java và cho kết quả 5.
2. Chuỗi được mã hóa và giải mã bằng **UTF-8** chỉ định tường minh; kiểm tra round-trip không phụ thuộc vào bảng mã mặc định của tiến trình.
3. **Path.of("data", "report.txt")** tạo đường dẫn từ hai thành phần và để provider hệ thống tệp chọn cách thể hiện dấu phân cách phù hợp. Vì không in chuỗi đường dẫn, ví dụ tránh phụ thuộc vào ký tự phân cách của OS.
4. **javap -c** hiển thị các lệnh bytecode phục vụ học tập; chi tiết disassembly có thể thay đổi theo compiler hay cách biên dịch, nên không dùng toàn bộ văn bản **javap** làm “stdout chuẩn” của chương trình.
5. Sao chép **out/PortabilityDemo.class** đến máy khác và chạy bằng một JVM tương thích có thể cho cùng ba dòng kết quả trên; vẫn cần máy đó có Java runtime và khả năng đọc tệp class.

**Lưu ý về công cụ:** tùy môi trường, lệnh tạo thư mục có thể cần điều chỉnh nếu **out** đã tồn tại. Các lệnh **javac**, **java** và **javap** thuộc bộ công cụ JDK; một runtime thu gọn có thể không kèm **javac** hoặc **javap**.

## 5. Phiên bản class file: “không phụ thuộc OS” không đồng nghĩa “không phụ thuộc phiên bản”

Định dạng **.class** có số phiên bản, nổi bật là **major version**. JVM quá cũ không có nghĩa vụ hiểu class file do compiler mới hơn tạo ra.

| Mã bytecode mục tiêu | Class file major version | Ví dụ JVM tương thích theo phiên bản |
| --- | ---: | --- |
| Java 17 | 61 | Java 17, Java 21 (khi không vướng điều kiện khác) |
| Java 21 | 65 | Java 21 hoặc JVM mới hơn hỗ trợ định dạng này |

Chẳng hạn, class file major 65 biên dịch cho Java 21 thường không chạy trên Java 17; một lỗi có thể thấy là **UnsupportedClassVersionError**. Hướng tương thích ngược của một runtime mới dành cho class file cũ là nguyên tắc rất hữu ích, nhưng không cứu được ứng dụng đang phụ thuộc API hoặc hành vi đã thay đổi.

Để tạo artifact chạy với phiên bản Java mục tiêu, khi compiler hỗ trợ, dùng:

~~~bash
javac --release 17 -encoding UTF-8 -d out PortabilityDemo.java
~~~

Tùy chọn **--release 17** chọn quy tắc ngôn ngữ, phiên bản bytecode và tập API Java SE công khai dành cho bản phát hành mục tiêu. Đối với JDK 21 biên dịch để chạy trên JDK 17, nó an toàn hơn việc chỉ đặt **-source 17 -target 17**, bởi cặp tùy chọn sau không tự ngăn bạn tham chiếu các API mới hơn của JDK đang biên dịch.

Tuy nhiên, **--release** chỉ kiểm soát phần code do compiler xử lý trong phạm vi tương ứng; nó không tự hạ cấp mọi dependency JAR, không làm thư viện native trở nên đa nền tảng, cũng không xác nhận rằng ứng dụng chạy tốt trên hệ điều hành đích. Nếu sử dụng tính năng **preview**, runtime có thể cần đúng phiên bản Java tương ứng và cờ **--enable-preview**; đây không phải lựa chọn tốt cho artifact yêu cầu tương thích rộng.

Khi phân phối ứng dụng, cần nêu rõ **Java tối thiểu** và kiểm tra phiên bản thật của tất cả dependency, plugin và công cụ tạo bytecode.

## 6. Các giới hạn của tính khả chuyển trong thực tế

| Lớp phụ thuộc | Biểu hiện có thể gặp | Cách giảm phụ thuộc |
| --- | --- | --- |
| **JVM/JDK và API** | Class version không hỗ trợ; **NoSuchMethodError** khi runtime/dependency không cung cấp method cần dùng | Đặt bản Java tối thiểu; dùng **--release**; kiểm tra dependency và thử chạy với runtime thấp nhất được hỗ trợ |
| **Classpath/module path** | **ClassNotFoundException**, **NoClassDefFoundError**, lỗi module hoặc cấu hình reflection | Đóng gói đủ thư viện; khai báo version; thử đúng cơ chế classpath/module path trên máy đích |
| **Native code/JNI/FFM** | **UnsatisfiedLinkError**, không tải được DLL/SO/DYLIB, ABI hoặc CPU không phù hợp | Phân phối native binary theo từng OS/CPU/ABI hoặc thay bằng Java thuần khi hợp lý |
| **Hệ thống tệp** | Khác chữ hoa/chữ thường, dấu phân cách, quyền ghi, tên file hợp lệ, liên kết tượng trưng | Dùng **Path**/**Files**; không ghép đường dẫn bằng ký tự cố định; thử quyền và tên file trên OS đích |
| **Mã hóa, locale, múi giờ** | Văn bản hiển thị sai; ngày/giờ/số khác cách diễn giải | Chỉ định charset như UTF-8, **Locale**, **ZoneId** khi nghiệp vụ cần giá trị cố định |
| **Hệ điều hành và tiến trình** | Lệnh shell, path executable, signal, biến môi trường hoặc permission khác | Tránh phụ thuộc lệnh OS; trừu tượng hóa hoặc có adapter riêng cho từng nền tảng |
| **Mạng và hạ tầng** | DNS, firewall, proxy, TLS trust store, địa chỉ bind, kết nối DB khác | Đưa cấu hình ra ngoài; kiểm thử kết nối và chứng chỉ ở môi trường triển khai |
| **Tài nguyên/runtime** | Hết RAM, giới hạn file descriptor, quyền container, GC/tốc độ khác | Đặt giới hạn và quan sát tài nguyên; đo trên máy đích; xử lý lỗi tài nguyên |
| **Đồng thời và thời gian** | Thứ tự thread, độ trễ và thời điểm hoàn tất khác nhau | Đồng bộ đúng; tránh phụ thuộc lịch thực thi; viết test concurrency có kiểm soát |

Những lỗi này có nguyên nhân và cách sửa khác nhau. Đặc biệt, **ClassNotFoundException** là ngoại lệ thường phát sinh khi chủ động tải class bằng tên; **NoClassDefFoundError** là lỗi liên kết/khởi tạo class trong các tình huống khác. Không nên coi mọi lỗi thiếu class là lỗi phiên bản JVM.

### 6.1. Đường dẫn không mang cùng ý nghĩa trên mọi OS

Mẫu ghép đường dẫn kiểu “cứng” dễ dẫn đến lỗi:

~~~java
// Đoạn minh họa, không phải một chương trình độc lập.
Path path = Path.of("data\\report.txt");
~~~

Chuỗi nguồn này chứa dấu backslash: trên Windows nó thường được hiểu là dấu phân cách; trên hệ thống tệp Unix thông thường, dấu backslash có thể là một phần của tên tệp. Hai môi trường có thể hiểu tên đường dẫn khác nhau.

Dùng các thành phần đường dẫn:

~~~java
// Cần import java.nio.file.Path trong mã đầy đủ.
Path path = Path.of("data", "report.txt");
~~~

Ngay cả khi tạo đường dẫn đúng, ứng dụng vẫn có thể thất bại vì thư mục không tồn tại, không đủ quyền, tên không được phép, khác quy tắc chữ hoa/chữ thường hoặc không hỗ trợ thao tác tệp dự kiến. API Java giúp diễn đạt ý định rõ hơn; nó không loại bỏ điều kiện của file system.

### 6.2. Dữ liệu môi trường có thể khiến output khác nhau

Chương trình sau độc lập với chương trình ở mục 4; lưu thành **EnvironmentDemo.java**:

~~~java
import java.io.File;
import java.time.ZoneId;

public class EnvironmentDemo {
    public static void main(String[] args) {
        System.out.println("separator=" + File.separator);
        System.out.println("timeZone=" + ZoneId.systemDefault());
        System.out.println("os=" + System.getProperty("os.name"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac EnvironmentDemo.java
java EnvironmentDemo
~~~

Các giá trị in ra **phụ thuộc hệ thống** nên không có một output cố định để áp dụng cho tất cả máy. Bytecode vẫn có thể chạy hợp lệ trên nhiều nền tảng trong khi dấu phân cách, múi giờ và tên OS khác nhau. Với dữ liệu lưu trữ/giao tiếp, hãy quyết định rõ khi nào cần dùng giá trị môi trường và khi nào cần chỉ định một chuẩn cố định.

### 6.3. Phụ thuộc vào native library

Một ứng dụng Java có thể gọi mã native thông qua **JNI**, Foreign Function & Memory API (ở những phiên bản Java hỗ trợ), hoặc thư viện bên thứ ba gói mã native. Tệp **.class** của phần gọi có thể được dùng lại, nhưng tệp thư viện bên dưới thường phải biên dịch cho đúng hệ điều hành, kiến trúc CPU, ABI và các thư viện hệ thống. Việc đổi từ **.dll** trên Windows sang **.so** trên Linux không thể giải quyết chỉ bằng cách đổi đuôi file.

Một JAR có chứa native binary cho một nền tảng vì vậy chưa chắc mang đi chạy được nơi khác. Cần kiểm tra artifact native thực tế và cơ chế lựa chọn binary cho từng hệ điều hành.

### 6.4. Thư viện và module cũng là điều kiện chạy

Nếu bytecode chỉ tương thích Java 17 nhưng thư viện ứng dụng cần Java 21, artifact hoàn chỉnh vẫn không chạy trên Java 17. Lỗi thiếu dependency có thể xuất hiện lúc khởi động hoặc chỉ khi đi qua một luồng chức năng hiếm dùng. Các thư viện truy cập API nội bộ JDK hay reflection có thể gặp ràng buộc đóng gói module và yêu cầu cấu hình đặc thù; việc thêm cờ mở package chỉ nên là quyết định có hiểu biết, không phải phương án sửa mặc định.

**JAR** chỉ là hình thức đóng gói: có thể chứa class, resources và metadata; nó không tự bảo đảm rằng JVM, thư viện và hạ tầng đã sẵn sàng. Một JAR đa phiên bản (**multi-release JAR**) còn có thể chọn các phiên bản class khác nhau theo runtime; cần đánh giá đúng nội dung thực tế sẽ được nạp.

## 7. Tính đúng đắn theo đặc tả và khác biệt khi vận hành

Sự khác nhau về tốc độ khởi động, chiến lược JIT, garbage collector hay lượng bộ nhớ tiêu thụ giữa các JVM không nhất thiết là vi phạm tính khả chuyển: chúng là khác biệt triển khai/vận hành nếu vẫn giữ ngữ nghĩa bắt buộc.

Ngược lại, chương trình không đồng bộ đúng có thể cho output khác nhau theo thứ tự chạy của thread, ngay cả **trên cùng một máy**. Các nguồn biến thiên khác gồm thời gian thực, dữ liệu ngẫu nhiên, I/O, thứ tự duyệt của cấu trúc không bảo đảm thứ tự, phiên bản cơ sở dữ liệu múi giờ và dữ liệu locale. Muốn kết quả có thể tái lập, cần kiểm soát các nguồn đó riêng.

Cũng cần tách sự khác nhau về giá trị số thực khỏi tuyên bố mơ hồ rằng “mọi phép tính floating point trên mỗi CPU đều tùy ý”: Java quy định ngữ nghĩa floating point chặt chẽ, đặc biệt từ Java 17; nhưng tính tái lập toàn hệ thống còn phụ thuộc loại phép toán, hàm thư viện, dữ liệu đầu vào và các thành phần ngoài JVM.

## 8. Một quy trình kiểm tra tính khả chuyển có thể thực hiện

**Bước 1 — Xác định ma trận hỗ trợ.** Ghi rõ Java thấp nhất, các OS (Windows/Linux/macOS), kiến trúc CPU (x86-64/ARM64), kiểu triển khai (server, desktop, container) và các dependency/native libraries. Không cần thử mọi hệ điều hành nếu sản phẩm chỉ cam kết vài môi trường cụ thể.

**Bước 2 — Biên dịch theo Java tối thiểu.** Dùng **--release** nếu áp dụng được, cố định dependency, ghi lại phiên bản build tool, compiler và runtime. Kiểm tra metadata của class/JAR bằng **javap -verbose** nếu nghi ngờ phiên bản bytecode; ví dụ:

~~~bash
javap -classpath out -verbose PortabilityDemo
~~~

Trong kết quả hãy tìm **major version: 61** nếu build đã dùng **--release 17**. Đừng dựa vào tên file JAR để suy ra class file version.

**Bước 3 — Chạy thật trên từng nền tảng đã cam kết.** Trước tiên dùng JDK/JRE thấp nhất mà ứng dụng tuyên bố hỗ trợ. Sau đó thử thêm một phiên bản mới hơn để phát hiện vấn đề tương thích tiến. Không coi việc build thành công trên máy lập trình là bằng chứng cho tất cả OS.

**Bước 4 — Kiểm tra cả luồng tương tác môi trường.** Thử đọc/ghi tệp, Unicode/UTF-8, đường dẫn có khoảng trắng, quyền truy cập, locale/time zone, kết nối mạng, TLS, database driver, native loading và tài nguyên. Các bài test thuần tính toán nên đi cùng integration test theo môi trường.

**Bước 5 — So sánh theo hợp đồng hành vi.** Đối chiếu output phải cố định bằng assertion chính xác; với dữ liệu vốn phụ thuộc OS (ví dụ tên hệ điều hành), kiểm tra định dạng/tính hợp lệ theo hợp đồng thay vì ép một chuỗi duy nhất trên mọi máy.

**Bước 6 — Đóng gói và công bố điều kiện chạy.** Tài liệu phát hành nên ghi rõ phiên bản Java tối thiểu, dependency, native binary nếu có, cấu hình thiết yếu, quyền hệ thống và nền tảng đã kiểm thử. Container hoặc runtime image riêng giúp kiểm soát nhiều biến số, nhưng image vẫn phải phù hợp CPU/OS kernel và quyền chạy.

## 9. Các nhầm lẫn thường gặp

1. **“Một JAR chạy mọi nơi.”** Chỉ đúng khi JVM phù hợp và mọi phụ thuộc/hành vi môi trường được đáp ứng.
2. **“Biên dịch bằng JDK 21 thì luôn chạy trên JDK 17.”** Không đúng: cần chọn target và API phù hợp (chẳng hạn **--release 17**) và kiểm tra dependency.
3. **“Đã có --release thì không cần thử trên OS khác.”** Sai: tùy chọn compiler không kiểm tra đường dẫn, quyền file, native binaries, mạng hay timing.
4. **“Không dùng JNI thì ứng dụng hoàn toàn không phụ thuộc nền tảng.”** Vẫn có thể phụ thuộc charset, locale, filesystem, process, quyền, mạng và hạ tầng.
5. **“Output hơi khác chứng tỏ bytecode không portable.”** Có thể chỉ là giá trị môi trường hoặc thứ tự thực thi vốn không được quy định cố định.
6. **“Có JVM bất kỳ là đủ.”** Cần JVM hỗ trợ đúng class file, API và tính năng được sử dụng.

## 10. Tự kiểm tra hiểu biết

**Câu 1.** Cùng một **PortabilityDemo.class** major version 61 được chuyển từ Linux/x86-64 sang Windows/ARM64. Điều kiện chủ yếu để chạy là gì?

**Đáp án:** Máy đích có JVM tương thích Java 17+, đủ class/dependency và tài nguyên; đoạn code không phụ thuộc các thành phần riêng nền tảng. Không cần biên dịch lại chỉ vì đổi CPU/OS.

**Câu 2.** Vì sao code do JDK 21 biên dịch có thể thất bại trên JDK 17 dù dùng **-target 17**?

**Đáp án:** Có thể đã tham chiếu API chỉ tồn tại ở Java mới hơn; cũng có thể dependency yêu cầu class version mới hơn. **--release 17** kiểm tra cả tập API Java SE của mục tiêu cho mã biên dịch, nhưng vẫn cần đánh giá dependency.

**Câu 3.** Hai máy chạy cùng class file nhưng in **timeZone** khác nhau. Có kết luận JVM vi phạm tính khả chuyển không?

**Đáp án:** Không. **ZoneId.systemDefault()** lấy cấu hình môi trường, nên giá trị khác nhau là bình thường. Nếu nghiệp vụ yêu cầu một múi giờ, hãy chỉ định nó tường minh.

**Câu 4.** Vì sao một thư viện Java gọi native code có thể chạy trên Windows nhưng hỏng trên Linux?

**Đáp án:** Bytecode lớp Java có thể tương thích, trong khi thư viện native được build cho OS/CPU/ABI riêng, hoặc thiếu thư viện hệ thống cần thiết trên Linux.

## 11. Ghi nhớ

**Bytecode độc lập nền tảng là nền móng cho tính khả chuyển, không phải lời bảo đảm đầy đủ cho ứng dụng.** JVM tương thích có thể thực thi cùng định dạng class file trên nhiều kiến trúc; nhưng phiên bản Java, API, dependency, native code, dữ liệu và điều kiện OS vẫn quyết định ứng dụng có chạy đúng hay không. Biên dịch với **--release** thích hợp, tránh giả định nền tảng và thử nghiệm trên ma trận môi trường được hỗ trợ là ba việc cần làm cùng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-005">Phân biệt đặc tả JVMS, dự án OpenJDK và cách triển khai HotSpot</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đặc tả JVMS, dự án OpenJDK và cách triển khai HotSpot

## Mục tiêu và bức tranh tổng thể

Khi nói “JVM chạy chương trình Java”, cần phân biệt **ba lớp có vai trò khác nhau**:

1. **JVMS (Java Virtual Machine Specification)** là **đặc tả**: mô tả máy ảo trừu tượng, định dạng class file, tập lệnh bytecode, các vùng dữ liệu và quy tắc thực thi mà một JVM tương thích phải tuân theo.
2. **OpenJDK** là **dự án/mã nguồn mở** phát triển một bộ JDK: có trình biên dịch Java (`javac`), công cụ (`java`, `javap`, `jcmd`...), thư viện Java và một cách triển khai JVM thường đi kèm là HotSpot.
3. **HotSpot** là **một cách triển khai JVM cụ thể**, nổi bật trong các bản dựng JDK dựa trên OpenJDK. Nó hiện thực các hành vi do JVMS yêu cầu, đồng thời tự chọn cơ chế thực thi và tối ưu, chẳng hạn thông dịch, JIT, bộ thu gom rác và quản lý bộ nhớ.

Hình dung theo luồng:

~~~text
Quy định về ngữ nghĩa JVM:
  Java Virtual Machine Specification (JVMS)
             |
             | Đặt ra hợp đồng tương thích
             v
Dự án mã nguồn triển khai JDK:
  OpenJDK: javac + java launcher + thư viện + HotSpot + công cụ...
             |
             | Đóng gói thành bản dựng JDK của một nhà cung cấp
             v
Ứng dụng:
  HelloJvm.java --(javac)--> HelloJvm.class --(HotSpot JVM)--> Kết quả

Lưu ý: một triển khai JVM khác cũng có thể tuân thủ JVMS.
~~~

**Phân biệt quan trọng:** JVMS không phải chương trình có thể cài đặt; OpenJDK không phải tên của một thuật toán chạy bytecode; HotSpot không phải tên chính thức của đặc tả JVM.

## 1. JVMS: bản hợp đồng của máy ảo

### JVMS quy định những gì?

JVMS mô tả **máy ảo trừu tượng**, không ấn định một cấu trúc phần mềm hay phần cứng duy nhất. Các nhóm quy tắc cốt lõi gồm:

- **Định dạng `.class`:** magic number `0xCAFEBABE`, phiên bản class file, constant pool, thông tin lớp, phương thức, trường, thuộc tính và mã lệnh. JVM phải nhận diện và diễn giải class file phù hợp phiên bản nó hỗ trợ.
- **Hệ kiểu và tập bytecode:** kiểu nguyên thủy, tham chiếu, các thao tác như `iload`, `iadd`, `invokevirtual`, `new`, `return`; trạng thái operand stack/local variables và tác động của lệnh.
- **Nạp, liên kết, khởi tạo lớp:** các quy tắc về loading, verification, preparation, resolution, initialization và lỗi liên quan. Thời điểm resolution có thể linh hoạt trong khuôn khổ đặc tả.
- **Run-time data areas:** những khái niệm như **heap**, **method area**, **run-time constant pool**, **JVM stack**, **PC register** và **native method stack**. Một số vùng chung cho các thread; một số thuộc mỗi thread.
- **Thực thi phương thức, ngoại lệ và đồng bộ:** cách frame hình thành/kết thúc, gọi và trả về từ phương thức, ném và xử lý ngoại lệ, ngữ nghĩa của `monitorenter`/`monitorexit`.
- **Các ràng buộc tương thích:** điều gì là hành vi quan sát được mà một JVM hợp lệ phải bảo đảm; điều gì có thể dẫn đến lỗi xác minh hoặc liên kết.

Ví dụ: JVMS mô tả việc `iadd` lấy hai giá trị `int` trên operand stack, cộng theo quy tắc số nguyên Java rồi đẩy kết quả. JVMS **không bắt buộc** nhà phát triển JVM phải viết đoạn xử lý bằng C++, bằng assembly hay tạo một lệnh máy tương ứng cho mỗi `iadd`.

### JVMS không quy định những gì?

Nhiều chi tiết vận hành là lựa chọn của nhà triển khai:

- JVM có **thông dịch**, **JIT** hay kết hợp các kỹ thuật hay không; mức tối ưu mã máy và cách bố trí trình biên dịch JIT.
- Bộ thu gom rác là G1, ZGC hay thuật toán khác; chính sách pause, số luồng GC và bố cục vùng nhớ cụ thể.
- Đối tượng được đặt trong RAM thế nào, header đối tượng dài bao nhiêu, con trỏ có nén không, method area được ánh xạ lên vùng nhớ vật lý nào.
- Các cờ triển khai như `-XX:+PrintCompilation`, `-XX:MaxRAMPercentage=...`, cấu trúc tệp log riêng, phần lớn giao diện chẩn đoán.
- Định dạng gói cài JDK, hãng phát hành, lịch cập nhật, hệ điều hành/CPU được hỗ trợ.

Nói **“JVMS yêu cầu có heap”** là đúng; nói **“JVMS yêu cầu heap được chia thành những vùng giống G1”** là sai. JVMS yêu cầu cơ chế thu hồi bộ nhớ tự động cho đối tượng, nhưng không ấn định thuật toán GC. Tương tự, **method area** là khái niệm do JVMS quy định, còn **Metaspace** là chi tiết triển khai quen thuộc của HotSpot, không phải một vùng nhớ được JVMS đặt tên bắt buộc.

### JVMS khác JLS và Java SE API ở đâu?

| Tài liệu/khái niệm | Trả lời câu hỏi | Ví dụ |
| --- | --- | --- |
| **JLS – Java Language Specification** | Chương trình nguồn Java hợp lệ có cú pháp, kiểu và ngữ nghĩa thế nào? | `class`, overload, generics, cách tính biểu thức trong Java |
| **JVMS – Java Virtual Machine Specification** | Class file và bytecode được biểu diễn, xác minh và thực thi thế nào? | `constant_pool`, `iload`, `invokevirtual`, stack frame |
| **Java SE API specification** | Các thư viện chuẩn hứa cung cấp API/hành vi gì? | `java.lang.String`, `java.util.List`, `System.out.println` |
| **JDK implementation** | Ai viết compiler, thư viện và VM để thực hiện các hợp đồng đó? | Mã nguồn OpenJDK và các bản dựng dựa trên nó |

`System.out.println` là lời gọi **thư viện Java** có đặc tả API; bytecode `getstatic`/`invokevirtual` có ngữ nghĩa theo JVMS; việc thực sự viết ký tự ra console còn phụ thuộc thư viện chuẩn và môi trường I/O. Không nên gán toàn bộ hành vi Java SE cho riêng JVMS.

## 2. OpenJDK: dự án xây dựng bộ công cụ Java

### OpenJDK là gì?

**OpenJDK** là hệ sinh thái phát triển mã nguồn mở cho các triển khai nền tảng Java SE và các thành phần liên quan. Dự án JDK tập hợp nhiều thành phần để tạo nên **JDK (Java Development Kit)**, ví dụ:

- **`javac`**: biên dịch mã nguồn `.java` thành bytecode/class file.
- **`java`**: launcher khởi động chương trình, thiết lập môi trường thực thi và sử dụng VM có trong bản dựng.
- **HotSpot VM**: triển khai máy ảo trong phần lớn bản dựng OpenJDK thông dụng.
- **Class libraries/modules**: ví dụ `java.base`, `java.logging`, `java.net.http`.
- **Công cụ phát triển/chẩn đoán**: `javap`, `jar`, `javadoc`, `jcmd`, `jstack`, `jfr` (phụ thuộc bản dựng và phiên bản).

Dự án không đồng nghĩa với **một bản cài duy nhất trên máy**. Nhiều nhà cung cấp tạo các bản dựng từ mã OpenJDK, có thể khác nhau về bản vá, lịch hỗ trợ, chứng nhận tương thích, giấy phép/điều khoản phân phối, cấu hình và nền tảng hỗ trợ. Vì thế, **OpenJDK** có thể chỉ dự án nguồn, hoặc được dùng trong tên một bản dựng JDK; phải nhìn ngữ cảnh.

Một bản dựng “dựa trên OpenJDK” không tự động chứng minh rằng mọi cấu hình, extension hoặc hiệu năng của nó giống hệt tất cả bản dựng khác. Cũng không nên suy ra “Oracle JDK” và “OpenJDK” là hai đặc tả ngôn ngữ/máy ảo khác nhau: chúng là những cách phân phối/sản phẩm trong hệ sinh thái Java và có lịch sử, điều khoản cụ thể theo phiên bản.

### Khi dùng OpenJDK, thành phần nào làm việc?

Với một ứng dụng Java bình thường:

1. `javac` phân tích mã nguồn theo quy tắc ngôn ngữ Java và phát sinh class file tuân theo JVMS.
2. Công cụ `java` khởi chạy runtime và VM được đóng gói trong bản JDK đang dùng.
3. VM nạp/xác minh/liên kết/khởi tạo các lớp phù hợp, thực hiện bytecode; thư viện Java cung cấp hành vi của các API được chương trình gọi.
4. Khi chạy, VM có thể thông dịch và tối ưu code; có thể gọi mã native, sử dụng dịch vụ hệ điều hành và quản lý tài nguyên.

Có thể lập trình **Kotlin, Scala** hoặc ngôn ngữ khác tạo class file cho JVM; ở bước thực thi, ranh giới chính là **JVMS**, dù trình biên dịch nguồn ban đầu không phải `javac`.

### Mã nguồn OpenJDK nằm ở đâu?

Repository phát triển JDK chính: [github.com/openjdk/jdk](https://github.com/openjdk/jdk). Các thư mục hữu ích để tra cứu:

- `src/hotspot/`: phần VM HotSpot, gồm runtime, class file handling, JIT, GC, hỗ trợ CPU/hệ điều hành.
- `src/java.base/`: nhiều lớp nền tảng và thư viện native hỗ trợ.
- `src/jdk.compiler/`: các thành phần của compiler Java.
- `src/jdk.jcmd/`: công cụ phục vụ chẩn đoán.

Đây là cấu trúc mã nguồn triển khai, **không phải** cấu trúc được JVMS bắt buộc. Thư mục/cơ chế có thể thay đổi theo nhánh và phiên bản.

## 3. HotSpot: JVM hoạt động thực tế ra sao?

**HotSpot** là tên một triển khai JVM được phát triển trong OpenJDK. Trong một bản dựng sử dụng HotSpot, VM bảo đảm hành vi được JVMS yêu cầu và cung cấp thêm các cơ chế tối ưu, quản trị tài nguyên, chẩn đoán.

### Các thành phần chính

**Nạp và chuẩn bị thực thi lớp.** HotSpot tham gia vào tải class file, kiểm tra tính hợp lệ bytecode, liên kết và khởi tạo theo quy tắc JVMS. Những lớp có thể đến từ file, module, JAR, bộ nạp tùy biến hay nguồn sinh động; không nên hiểu JVM chỉ chạy những file `.class` được lưu sẵn trên ổ cứng.

**Bộ máy thực thi.** HotSpot có **interpreter** để thực thi bytecode và các **JIT compiler** nhằm biên dịch những vùng mã thích hợp thành machine code. Trong nhiều cấu hình server, HotSpot dùng **tiered compilation** (thường gồm C1/C2) để cân bằng thời gian khởi động, thu thập profile và hiệu năng ổn định. Chi tiết compiler/tầng tối ưu có thể khác theo phiên bản/cấu hình; JVMS không yêu cầu JIT.

**Quản lý bộ nhớ và garbage collection.** HotSpot cấp phát đối tượng, theo dõi các tham chiếu cần thiết cho GC và cung cấp những collector như **G1** và **ZGC** trên các phiên bản/nền tảng hỗ trợ. Các cấu trúc như **Metaspace**, code cache, TLAB, card table hoặc region đều là chi tiết của một cách triển khai; không nên coi chúng là tên bắt buộc trong JVMS.

**Luồng, đồng bộ và tương tác hệ điều hành.** HotSpot triển khai cơ chế thread, monitor, JNI, điều phối runtime, giao tiếp OS và các điểm phối hợp cần cho tác vụ VM như GC. Kiểu ánh xạ thread và thuật toán khóa có thể tiến hóa mà vẫn phải giữ ngữ nghĩa JVM/Java đúng quy định.

**Quan sát và cấu hình.** HotSpot có công cụ, flag, log GC/JIT và cơ chế diagnostic; các cờ `-X`/`-XX` là giao diện vận hành theo triển khai, có thể thay đổi, bị giới hạn hoặc không xuất hiện trên JVM khác.

### Ví dụ: cùng một phép cộng, nhiều con đường chạy

Giả sử bytecode có `iload_1`, `iload_2`, `iadd` và `ireturn`:

- JVMS định nghĩa các bước trừu tượng và giá trị kết quả của phép cộng `int`.
- HotSpot có thể chạy bằng interpreter ở thời điểm đầu.
- Nếu phương thức phù hợp để tối ưu, HotSpot có thể biên dịch thành mã máy và chạy mã đó trong những lần sau; cách dịch không nhất thiết tương ứng từng bytecode với một lệnh CPU.
- Dù chọn đường nào, chương trình phải quan sát được ngữ nghĩa mà đặc tả đòi hỏi (trừ các hành vi được đặc tả cho phép linh hoạt).

HotSpot có thể **deoptimization** (quay về dạng thực thi tổng quát khi một giả định tối ưu không còn đúng). Đây là chiến lược nội bộ để tối ưu hiệu năng nhưng vẫn giữ ngữ nghĩa, không phải câu lệnh bytecode mới do JVMS bắt buộc.

## 4. Bảng so sánh ba khái niệm

| Tiêu chí | JVMS | OpenJDK | HotSpot |
| --- | --- | --- | --- |
| Bản chất | Tài liệu đặc tả chuẩn của máy ảo | Dự án/cơ sở mã nguồn xây JDK | Một triển khai JVM cụ thể |
| Câu hỏi chính | “VM phải làm gì và bảo đảm điều gì?” | “Mã nguồn/công cụ/thư viện để xây JDK ở đâu?” | “VM cụ thể thực hiện và tối ưu thế nào?” |
| Có thể chạy trực tiếp? | Không | “Dự án” không chạy; **bản dựng JDK** chứa chương trình/công cụ chạy được | Có, khi được đóng gói trong runtime |
| Phạm vi | Class file, bytecode, các quy tắc VM | Compiler, thư viện, launcher, VM, công cụ, quy trình dự án | Runtime VM, JIT, GC, memory, threads, diagnostics |
| Ví dụ | Heap và operand stack là khái niệm bắt buộc | `javac`, `java.base`, repository `openjdk/jdk` | C1/C2, G1/ZGC, Metaspace, các cờ `-XX` |
| Thay thế được bằng gì? | Phiên bản đặc tả khác tùy release; phải xét tương thích | Một dự án/bản phân phối Java SE khác | Một JVM tương thích khác, ví dụ Eclipse OpenJ9 |
| Cách tra cứu | Chương định nghĩa trong JVMS đúng phiên bản | Tài liệu dự án, mã nguồn, release notes | Tài liệu HotSpot, flag/help, nguồn `src/hotspot`, log |

Mối quan hệ cần nhớ: **HotSpot là một thành phần triển khai được dùng trong OpenJDK; OpenJDK cung cấp nguồn và công cụ của một JDK; JVMS đặt ra hợp đồng cho JVM.** Ba tên thuộc ba loại đối tượng khác nhau, không phải ba sản phẩm chạy song song và cũng không phải ba bước “biên dịch” tuần tự.

## 5. Thực hành: từ Java source tới JVM đang chạy

Ví dụ dùng Java 17+; tạo file `HelloJvm.java` (tên file trùng `public class`):

~~~java
public class HelloJvm {
    public static void main(String[] args) {
        int total = 3 + 4;
        System.out.println("Tong = " + total);
        System.out.println("VM = " + System.getProperty("java.vm.name"));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac HelloJvm.java
javap -c HelloJvm
java HelloJvm
java -version
~~~

Đầu ra chương trình **chắc chắn** có dòng đầu:

~~~text
Tong = 7
~~~

Dòng thứ hai bắt đầu bằng `VM = `, nhưng **giá trị phụ thuộc bản JDK/VM**. Ví dụ ở nhiều bản dựng HotSpot Server VM, dòng đó chứa tên như `OpenJDK 64-Bit Server VM`; đừng sao chép tên này thành kết quả chung cho mọi máy. Lệnh `java -version` cho biết thông tin phiên bản/runtime/VM do bản dựng công bố; định dạng và tên hãng cũng có thể khác.

Lệnh `javap -c HelloJvm` cho phép xem bytecode của class file. Với đoạn in chuỗi và gọi API, bạn có thể thấy các lệnh như `getstatic` (đọc `System.out`), `invokevirtual` (gọi `println`), `ldc` hoặc `invokedynamic` (tùy cách compiler tạo nối chuỗi), cùng các lệnh trả về. **Không** coi số thứ tự constant pool hoặc toàn bộ disassembly là cố định qua mọi bản compiler; JVM chỉ phải tuân theo cấu trúc/ngữ nghĩa class file mà nó chấp nhận.

### Liên hệ từng hành động với từng lớp

| Bước nhìn thấy | Lớp liên quan | Vì sao? |
| --- | --- | --- |
| `javac HelloJvm.java` tạo `HelloJvm.class` | OpenJDK/JDK tool + JLS/JVMS | Compiler là chương trình thực; bytecode xuất ra phải theo class-file contract |
| `javap -c` in lệnh bytecode | JDK tool + JVMS | Lệnh mang ý nghĩa theo tài liệu JVMS |
| `java HelloJvm` chạy chương trình | Launcher trong JDK + HotSpot hoặc VM được đóng gói | JVM cụ thể thực thi class, thư viện cung cấp `System.out` |
| `java.vm.name` | Runtime đang sử dụng | Quan sát tên VM được nhà triển khai khai báo, không phải cách xác minh toàn bộ tính tương thích |
| G1, JIT, log GC, `-XX` | Chủ yếu HotSpot | Chiến lược/cấu hình thực thi, không phải bytecode chuẩn |

### Một so sánh thêm trên HotSpot

Trong môi trường dùng HotSpot có hỗ trợ, bạn có thể thử:

~~~bash
java -Xint HelloJvm
~~~

Cờ `-Xint` yêu cầu HotSpot chạy theo chế độ thông dịch, thường hữu ích để đối chiếu hành vi/chẩn đoán. Kết quả chức năng vẫn là phép tính `3 + 4 = 7` và việc in VM name; tốc độ không nên suy ra từ chương trình quá nhỏ này. `-Xint` là tùy chọn của runtime cụ thể, **không phải câu lệnh hay yêu cầu trong JVMS**; VM khác có thể không hỗ trợ.

Nếu một tùy chọn chẩn đoán bị từ chối, trước hết hãy kiểm tra bản dựng, phiên bản và VM (`java -version`, tài liệu chính thức của hãng). Đừng suy luận rằng JVM đã vi phạm JVMS chỉ vì nó không biết một cờ `-XX` của HotSpot.

## 6. Chuẩn so với lựa chọn triển khai: cách phân loại tình huống

**Tình huống A — class file sai định dạng:** Khi gặp class file có cấu trúc không hợp lệ hoặc phiên bản không được hỗ trợ, hãy tra **JVMS – Class File Format/Verification** và thông báo lỗi cụ thể. Trước khi kết luận VM lỗi, cần đối chiếu phiên bản JDK và bytecode đang chạy.

**Tình huống B — ứng dụng bị `OutOfMemoryError`:** JVMS có quy tắc liên quan đến các vùng dữ liệu và `OutOfMemoryError`, nhưng để phân tích mức sử dụng bộ nhớ, heap dump, Metaspace và cách GC hoạt động trên máy thực tế, cần tra **HotSpot/runtime cụ thể**, cấu hình và log. Không đồng nhất kích thước heap với toàn bộ bộ nhớ process.

**Tình huống C — đổi collector G1 sang ZGC:** Đây là lựa chọn **của HotSpot/bản dựng runtime**. Chương trình Java không phải đổi bytecode vì JVMS không khóa ứng dụng vào một collector nhất định. Tuy vậy, hiệu năng/pause có thể thay đổi đáng kể.

**Tình huống D — thay JDK vendor:** So sánh release, bản vá bảo mật, nền tảng CPU/OS, API hỗ trợ, giấy phép, hỗ trợ thương mại và **VM đi kèm**. Không suy ra rằng cùng chữ “OpenJDK” bảo đảm mọi option JIT/GC giống nhau.

**Tình huống E — JVM khác chạy bytecode Java:** JVM tương thích có thể chọn cách quản lý bộ nhớ và tạo mã khác HotSpot. **Khả năng thực thi class file đúng hợp đồng** là trọng tâm của tính tương thích với JVMS; đặc trưng HotSpot không tự trở thành đặc tả.

## 7. Những hiểu nhầm phổ biến

1. **“OpenJDK chính là HotSpot.”** Chưa chính xác: HotSpot là một thành phần VM trong hệ mã nguồn JDK; OpenJDK còn có compiler, thư viện, launcher và nhiều công cụ.
2. **“JVMS quy định bắt buộc dùng JIT hoặc G1.”** Sai: JVMS quy định hành vi máy ảo, để nhà triển khai chọn cơ chế tối ưu và thuật toán quản lý bộ nhớ.
3. **“Metaspace = method area theo tên chính thức của đặc tả.”** Sai: method area là khái niệm JVMS; HotSpot có chi tiết lưu metadata như Metaspace.
4. **“Mọi bản OpenJDK đều có đúng những dòng `java -version` như nhau.”** Sai: build, vendor, phiên bản, OS/CPU và VM có thể khác nhau.
5. **“Mọi đặc tính của HotSpot đều dùng được ở JVM khác.”** Sai: cờ `-XX`, log, công cụ và tối ưu của HotSpot thường không phải API/ABI portable.
6. **“Có bytecode thì VM phải chạy được bất kể phiên bản.”** Sai: class file có version/feature, runtime cần hỗ trợ những phần tương ứng; lỗi `UnsupportedClassVersionError` thường phản ánh Java runtime cũ hơn class file.
7. **“JVM chính là Java.”** Sai: ngôn ngữ Java, nền tảng thư viện Java SE và máy ảo JVM liên quan chặt chẽ nhưng có đặc tả/phạm vi khác nhau.

## 8. Tự kiểm tra

**Câu 1.** Một tài liệu định nghĩa bytecode `invokevirtual`, frame và constant pool. Bạn đang đọc về khái niệm nào?  
**Đáp án:** JVMS, vì đây là các quy tắc về máy ảo và định dạng class file.

**Câu 2.** Bạn muốn sửa thuật toán GC hoặc tìm mã nguồn C++ chạy trong VM của bản dựng OpenJDK phổ biến. Nên bắt đầu ở đâu?  
**Đáp án:** Thành phần HotSpot, thường từ `src/hotspot`; tra đúng nhánh JDK đang dùng.

**Câu 3.** Bạn muốn xem code của `javac` và thư viện `java.base`. JVMS có cung cấp code đó không?  
**Đáp án:** Không. Đọc dự án OpenJDK/JDK source; JVMS là tài liệu quy định hành vi VM.

**Câu 4.** Một JVM xử lý bytecode đúng nhưng không hỗ trợ `-XX:+PrintCompilation`. Có đủ bằng chứng khẳng định vi phạm JVMS không?  
**Đáp án:** Không. `-XX` là tùy chọn triển khai; kiểm tra yêu cầu chuẩn liên quan trước khi đánh giá tính tương thích.

**Câu 5.** Khi chuyển từ HotSpot sang JVM khác, phần nào cần xem lại trước tiên?  
**Đáp án:** Các tùy chọn, công cụ, log, giả định hiệu năng/GC phụ thuộc HotSpot. Hành vi ứng dụng dựa trên Java SE/JVMS hợp lệ có tính di động cao hơn nhưng vẫn cần kiểm thử theo nền tảng và phiên bản.

## 9. Tài liệu đọc tiếp

- [Java Language and Virtual Machine Specifications](https://docs.oracle.com/javase/specs/) — chọn đúng phiên bản Java SE khi tra JVMS/JLS.
- [JVMS – The Structure of the Java Virtual Machine](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-2.html) — máy ảo trừu tượng, run-time data areas, hướng dẫn phân biệt hợp đồng với triển khai.
- [JVMS – The Class File Format](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html) — cấu trúc class file và quy tắc định dạng; tra phiên bản tương ứng với runtime của bạn.
- [OpenJDK](https://openjdk.org/) và [OpenJDK JDK Project](https://openjdk.org/projects/jdk/) — dự án và hướng dẫn phát triển JDK.
- [OpenJDK JDK source](https://github.com/openjdk/jdk) — xem mã nguồn `src/hotspot`, `src/jdk.compiler`, `src/java.base`.

**Ghi nhớ khi đọc tiếp về JVM:** đặt câu hỏi “**đây là hợp đồng trong JVMS, mã nguồn từ dự án OpenJDK, hay một quyết định triển khai của HotSpot?**”. Phân loại đúng ngay từ đầu sẽ giúp bạn tra đúng tài liệu và tránh nhầm đặc tính của một VM với yêu cầu bắt buộc cho mọi JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-006">Vai trò của bộ nạp lớp, bộ thực thi, bộ nhớ và bộ thu gom rác</a>

<details>
<summary>Xem chi tiết</summary>

# JVM vận hành chương trình Java: bộ nạp lớp, bộ thực thi, bộ nhớ và bộ thu gom rác

Khi chạy `java Main`, JVM không chỉ “đọc file `.class` rồi chạy”. Nó phải **tìm và tạo biểu diễn của lớp**, **thực thi các lệnh bytecode**, **quản lý dữ liệu trong những vùng nhớ có vai trò khác nhau** và **thu hồi bộ nhớ heap không còn cần thiết**. Bốn phần này phối hợp trong suốt vòng đời chương trình.

Bài học tập trung vào vai trò, ranh giới trách nhiệm và quan hệ giữa **Class Loader**, **Execution Engine**, **Runtime Data Areas** và **Garbage Collector (GC)**. Các mô tả về Metaspace, JIT nhiều tầng và các cờ chẩn đoán sử dụng **HotSpot trên JDK 17+** làm ví dụ; một triển khai JVM khác có thể tổ chức nội bộ khác.

## 1. Bức tranh toàn cảnh

```text
Mã nguồn .java
    │ javac (trình biên dịch của JDK)
    ▼
Bytecode .class
    │
    ▼
[Class Loader] ── nạp / liên kết / khởi tạo lớp ──► [Thông tin lớp tại runtime]
    │                                                    │
    └──────────────────────┬─────────────────────────────┘
                           ▼
                  [Execution Engine]
                thông dịch / JIT bytecode
                           │
            đọc, tạo, cập nhật dữ liệu
                           ▼
                    [Bộ nhớ JVM]
             heap, stack, method area, PC...
                           ▲
                           │ theo dõi tham chiếu còn tới được
                    [Garbage Collector]
               thu hồi đối tượng heap không còn tới được
```

Sơ đồ là **mô hình khái niệm**, không hàm ý mọi mũi tên là một bước tuần tự hoặc GC chạy sau mỗi câu lệnh. Trong lúc JVM thực thi phương thức, nó có thể tiếp tục nạp lớp khác, cấp phát đối tượng mới, tối ưu mã đã chạy nhiều lần và thực hiện các pha GC.

| Thành phần | Câu hỏi nó giải quyết | Vai trò chính |
| --- | --- | --- |
| Class Loader | “Lớp này là lớp nào, mã và metadata của nó ở đâu?” | Tìm/nạp định nghĩa lớp, phối hợp liên kết và khởi tạo |
| Execution Engine | “Các lệnh trong phương thức được thực hiện thế nào?” | Chạy bytecode bằng thông dịch, biên dịch JIT và mã máy |
| Runtime Data Areas | “Dữ liệu và trạng thái thực thi nằm ở đâu?” | Cung cấp heap, stack và các vùng runtime khác |
| Garbage Collector | “Đối tượng heap nào không còn cần giữ?” | Xác định khả năng truy cập và thu hồi bộ nhớ tự động |

`javac` thuộc **JDK**; JVM là môi trường thực thi bytecode. **GC là một dịch vụ quản lý bộ nhớ bên trong JVM**, không phải trình nạp lớp hay bộ thực thi.

## 2. Class Loader: đưa định nghĩa lớp vào JVM

### 2.1. Tại sao phải nạp lớp?

Một file `.class` chứa bytecode, constant pool và mô tả trường, phương thức. JVM cần một biểu diễn runtime để xác định kiểu, tra cứu phương thức, kiểm tra truy cập và thực thi lệnh. Việc nạp lớp thường xảy ra **khi cần**, chứ không phải mọi class trên classpath đều được nạp khi chương trình khởi động.

Trong JVM, định danh của một lớp gắn với **tên nhị phân của lớp và defining class loader**. Hai class loader khác nhau định nghĩa cùng tên `com.example.Plugin` có thể tạo ra hai kiểu khác nhau; vì vậy ép kiểu hoặc giao tiếp qua plugin có thể gặp `ClassCastException` hoặc `LinkageError` dù tên lớp trông giống nhau.

### 2.2. Ba giai đoạn thường được nhắc đến

1. **Loading (nạp):** class loader tìm bytes của định nghĩa lớp và JVM tạo biểu diễn tương ứng.
2. **Linking (liên kết):** gồm **verification** (kiểm tra ràng buộc bytecode), **preparation** (cấp phát/đặt giá trị mặc định cho các trường `static`) và **resolution** (biến tham chiếu ký hiệu trong constant pool thành tham chiếu thực tế; có thể làm lười tùy JVM).
3. **Initialization (khởi tạo):** thực thi logic khởi tạo trường `static` và khối `static { ... }` theo quy tắc ngôn ngữ, thường do lần **sử dụng chủ động** đầu tiên kích hoạt.

Đừng đánh đồng **đã nạp** với **đã khởi tạo**. Ví dụ, việc lấy literal `SomeType.class` không nhất thiết chạy khối khởi tạo `static`. Ngược lại, đọc một trường `static` không phải hằng số biên dịch thường yêu cầu khởi tạo lớp khai báo trường đó. Việc tham chiếu một hằng số `static final` kiểu nguyên thủy/`String` có giá trị hằng số ngay lúc biên dịch có thể được inline và **không** kích hoạt khởi tạo lớp.

### 2.3. Các bộ nạp thông dụng trong HotSpot

| Loader | Trách nhiệm thường gặp |
| --- | --- |
| Bootstrap | Nạp các lớp nền tảng cốt lõi của Java; với `Class.getClassLoader()` có thể được biểu diễn là `null` |
| Platform | Nạp các lớp nền tảng do platform class loader phụ trách |
| Application/System | Nạp phần lớn lớp ứng dụng từ classpath/module path thích hợp |
| Custom | Nạp lớp theo nhu cầu riêng, chẳng hạn plugin, container hoặc cơ chế cô lập |

Các loader dựng sẵn thường dùng **parent delegation**: hỏi loader cha trước khi tự tìm định nghĩa. Cơ chế tùy biến có ngoại lệ; không được coi “cha luôn nạp trước” là luật áp dụng cho mọi custom loader.

**Nhiệm vụ của Class Loader kết thúc ở định nghĩa/chu kỳ lớp; nó không tự chạy toàn bộ thuật toán trong `main`.** Sau khi mã của phương thức được xác định, execution engine đảm nhận thực thi.

## 3. Execution Engine: thực hiện bytecode

Bytecode mô tả các thao tác như nạp biến, tính toán, gọi phương thức, tạo đối tượng, rẽ nhánh và trả về. Execution engine khiến những thao tác này có hiệu lực trong runtime.

- **Interpreter (trình thông dịch):** thực hiện bytecode khi chương trình chạy, cho phép phương thức được thực thi mà chưa cần có mã máy tối ưu hóa.
- **Just-in-Time compiler (JIT):** dựa trên thông tin thực thi (profiling), biên dịch các đoạn/phương thức thích hợp thành **mã máy native** để những lần chạy sau thường nhanh hơn. HotSpot có thể dùng tiered compilation, inlining và nhiều tối ưu hóa khác.
- **Deoptimization:** khi giả định dùng để tối ưu không còn đúng, HotSpot có thể quay về trạng thái thực thi ít tối ưu hơn để vẫn giữ nguyên ngữ nghĩa chương trình.

`JIT` không phải `javac`: `javac` biến mã nguồn thành bytecode **trước khi chạy**; JIT biên dịch bytecode thành mã máy **trong lúc chạy**. Không có cam kết rằng mọi phương thức đều được JIT, hay mọi lần gọi đều chạy đúng cùng một đường thực thi. Mã máy đã biên dịch có thể nằm trong **code cache** của HotSpot.

Ví dụ, với lời gọi `sum(2, 3)`, JVM tạo/ngắt một frame phương thức phù hợp, thực hiện các thao tác tính toán và đặt kết quả vào ngữ cảnh của bên gọi. Frame và dữ liệu dùng trong thao tác liên quan đến các vùng nhớ ở phần tiếp theo.

## 4. Runtime Data Areas: dữ liệu nằm ở đâu?

**Phân biệt quan trọng:** Java Virtual Machine Specification mô tả các vùng dữ liệu **theo chức năng**; tên và cách cấp phát thực tế ở HotSpot không luôn tương ứng 1–1 với cách vẽ trong tài liệu.

| Vùng nhớ | Chia sẻ hay riêng? | Chứa gì và dùng để làm gì? |
| --- | --- | --- |
| **Heap** | Chung cho các thread | Đối tượng và mảng được cấp phát tại runtime; vùng được GC quản lý chủ yếu |
| **Java Virtual Machine Stack** | Mỗi thread một stack | Các **frame** cho lời gọi phương thức Java: biến cục bộ, operand stack, thông tin liên kết/trở về |
| **PC Register** | Mỗi thread | Vị trí lệnh JVM đang thực thi trong phương thức Java hiện hành; nội dung với native method được quy định khác |
| **Method Area** | Chung | Mô tả lớp/kiểu, cấu trúc phương thức, runtime constant pool và dữ liệu mức lớp theo mô hình đặc tả |
| **Native Method Stack** | Gắn với thực thi native, tùy triển khai | Trạng thái hỗ trợ các phương thức native khi JVM sử dụng loại stack này |

Trong **HotSpot hiện đại**, phần lớn class metadata được lưu ở vùng native memory gọi là **Metaspace** (một cách hiện thực hóa phần thông tin của method area); **code cache** chứa mã máy đã biên dịch. Vì vậy **method area không đồng nghĩa giản đơn với “heap”**, và `-Xmx` không phải giới hạn tổng bộ nhớ của tiến trình.

### 4.1. Stack frame không phải heap object

Mỗi lần gọi phương thức Java tạo một ngữ cảnh frame với tham số, biến cục bộ và vùng tính toán toán hạng. Lời gọi kết thúc thì frame không còn hoạt động. Khi hàm chứa biến `Person p`, giá trị biến đó có thể là **tham chiếu đến đối tượng**, còn đối tượng thuộc mô hình **heap**, không phải “toàn bộ đối tượng nằm trong stack”.

Đây là mô hình lập trình/đặc tả. JIT có thể đặt giá trị ở thanh ghi hoặc loại bỏ cấp phát đối tượng nhờ escape analysis và scalar replacement; không nên suy luận vị trí vật lý chính xác từ một dòng mã nguồn.

### 4.2. Phạm vi sống của stack và heap khác nhau

Một biến cục bộ hết phạm vi không đồng nghĩa GC đã chạy. Khi **không còn đường tham chiếu mạnh nào từ GC roots tới đối tượng**, đối tượng có thể được thu hồi trong một đợt GC sau đó. Nếu đối tượng vẫn được lưu trong trường `static`, collection, thread-local hoặc đối tượng sống lâu khác, nó có thể tiếp tục được giữ dù phương thức tạo nó đã kết thúc.

### 4.3. Bộ nhớ ngoài heap cần phân biệt

Direct buffers, native allocations, metadata và thread stacks có thể dùng native memory. Garbage Collector của Java không thay thế nghĩa vụ đóng **file, socket, database connection** hoặc giải phóng các tài nguyên có vòng đời tường minh. Ưu tiên `try-with-resources` cho tài nguyên triển khai `AutoCloseable`.

## 5. Garbage Collector: thu hồi những đối tượng không còn tới được

GC tự động quản lý phần lớn vòng đời **bộ nhớ heap**. Ý tưởng cốt lõi là **reachability (khả năng truy cập)**, không phải “biến bị gán `null` thì lập tức xóa đối tượng”.

### 5.1. GC roots và đồ thị tham chiếu

GC bắt đầu từ các **roots** thích hợp với trạng thái runtime (ví dụ tham chiếu đang hoạt động trong thread, các tham chiếu mức lớp, JNI handles), lần theo quan hệ tham chiếu tới đối tượng có thể truy cập. Đối tượng không còn đường tới từ roots có thể bị thu hồi. Hai đối tượng chỉ trỏ qua lại với nhau nhưng không nối tới roots **vẫn có thể được thu hồi**.

```text
GC root ──► A ──► B           C ◄──► D
             │
             └────► E

A, B, E: còn tới được từ root.
C, D: chỉ trỏ cho nhau, không tới được từ root → có thể thu hồi.
```

Thuật toán và thời điểm cụ thể phụ thuộc collector: có thể đánh dấu, sao chép, di chuyển/compact, hoặc làm việc đồng thời với application threads. Một số hoạt động cần **safepoint/stop-the-world pause**; không nên khẳng định “GC chạy hoàn toàn nền” hoặc “GC luôn dừng toàn bộ ứng dụng như nhau”.

### 5.2. GC có thể và không thể bảo đảm điều gì?

- **Có:** tìm và xử lý các đối tượng không còn tới được theo chính sách collector, giúp tái sử dụng heap; các collector hiện đại cũng có thể phục vụ class unloading khi điều kiện phù hợp.
- **Không:** bảo đảm một đối tượng cụ thể được thu hồi ngay sau `obj = null`, bảo đảm độ trễ bằng 0, hoặc tự đóng mọi tài nguyên hệ điều hành.
- **`System.gc()`:** về cơ bản là một yêu cầu/gợi ý với runtime; có thể bị bỏ qua hoặc xử lý khác tùy cờ và triển khai, không phải phép xác nhận đối tượng đã được thu hồi.
- **Bộ nhớ rò rỉ trong Java:** vẫn có thể xảy ra theo nghĩa đối tượng **còn reachable nhưng không còn hữu ích** (ví dụ cache không giới hạn, listener không gỡ đăng ký).

Các collector như G1, ZGC hoặc Parallel GC tối ưu các mục tiêu throughput, pause và footprint khác nhau. Không có một thuật toán GC duy nhất được JVM Specification bắt buộc cho mọi JVM.

## 6. Lần theo một ví dụ Java 17 có thể chạy

Tạo file **`JvmFlowDemo.java`**:

```java
public class JvmFlowDemo {

    static class Config {
        static {
            System.out.println("Config initialized");
        }

        static int factor = 7; // Không phải hằng số compile-time.
    }

    static int twice(int number) {
        int result = number * 2;
        return result;
    }

    public static void main(String[] args) {
        System.out.println("main begins");

        int output = twice(Config.factor);
        System.out.println("result = " + output);

        Object sample = new Object();
        System.out.println("object type: " + sample.getClass().getSimpleName());
        sample = null; // Không chứng minh GC sẽ chạy ngay.

        System.out.println("main ends");
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac JvmFlowDemo.java
java JvmFlowDemo
```

**Stdout của chương trình:**

```text
main begins
Config initialized
result = 14
object type: Object
main ends
```

Diễn giải từng điểm:

1. `javac` tạo `JvmFlowDemo.class` và `JvmFlowDemo$Config.class` từ mã nguồn (tên file class lồng là quy ước biên dịch của `javac`).
2. Khi khởi động, JVM nạp/liên kết/khởi tạo những lớp cần thiết để chạy `main`. Việc tải các lớp phụ thuộc khác có thể tiếp diễn sau đó.
3. Lệnh đầu của `main` in `main begins`. Biểu thức đọc trường `Config.factor` kích hoạt khởi tạo `Config` nếu chưa khởi tạo, vì đây là **trường `static` không phải hằng số compile-time**. Do đó dòng `Config initialized` xuất hiện trước `result = 14`.
4. Lời gọi `twice` cần ngữ cảnh thực thi của phương thức: tham số `number`, biến cục bộ `result`, phép nhân và giá trị trả về. Interpreter hoặc mã do JIT sinh ra thực hiện các thao tác với cùng kết quả quan sát được.
5. `new Object()` tạo đối tượng theo ngữ nghĩa Java; biến `sample` giữ tham chiếu đến nó. JVM thực thi `getClass().getSimpleName()` và in `Object`.
6. Sau khi gán `sample = null`, chương trình không còn cố ý giữ tham chiếu qua biến này; nếu không còn đường tham chiếu mạnh khác thì đối tượng **có thể đủ điều kiện** để GC thu hồi. Ví dụ **không hề chứng minh thời điểm GC**, và output không phụ thuộc vào việc GC chạy.

> Lưu ý: JVM/JIT được phép tối ưu hóa việc sử dụng và cấp phát đối tượng nếu giữ đúng hành vi quan sát được. Mô tả “đối tượng trên heap, tham chiếu trong frame” là mô hình học hữu ích, không phải bản đồ chính xác của từng ô nhớ sau tối ưu hóa.

## 7. Phối hợp bốn thành phần trong vòng đời ứng dụng

| Sự kiện | Class Loader | Execution Engine | Runtime Data Areas | GC |
| --- | --- | --- | --- | --- |
| JVM chuẩn bị gọi `main` | Nạp/liên kết các lớp cần thiết | Chuẩn bị thực thi bytecode | Metadata lớp, trạng thái thread/frame | Chưa nhất thiết có hoạt động nào |
| Chạm tới một lớp mới | Nạp/liên kết, khởi tạo khi điều kiện kích hoạt | Thực thi mã khởi tạo nếu cần | Cập nhật dữ liệu mức lớp | Có thể hoạt động độc lập |
| Gọi phương thức | Có thể cần nạp thêm kiểu phụ thuộc | Thông dịch/JIT, thực hiện lệnh | Frame, PC, đối tượng được truy cập | Có thể chạy xen kẽ với ứng dụng |
| Tạo đối tượng | Cần kiểu đã được xử lý thích hợp | Thực hiện `new` và constructor | Cấp phát/logical object, quản lý tham chiếu | Có thể được kích hoạt do áp lực cấp phát |
| Mất đường tham chiếu | Không quyết định object liveness | Tiếp tục thực thi chương trình | Root graph không còn giữ đối tượng | Có thể thu hồi ở một đợt GC thích hợp |
| Kết thúc tiến trình | Định nghĩa lớp hết vòng đời theo JVM | Ngừng thực thi | Bộ nhớ tiến trình được hệ điều hành thu hồi | Không cần đợi GC từng đối tượng |

Class Loader và Execution Engine **không thay thế nhau**: loader giải quyết **kiểu và định nghĩa**, engine thực hiện **hành vi**; bộ nhớ lưu trạng thái để cả hai làm việc; GC giúp quản lý sự sống của đối tượng heap.

## 8. Quan sát và chẩn đoán với HotSpot trên JDK 17+

Những lệnh sau là ví dụ công cụ quan sát, **không phải hành vi bắt buộc của mọi JVM**.

```bash
# Hiển thị hoạt động nạp lớp (nhiều log; Java 17+ HotSpot)
java -Xlog:class+load=info JvmFlowDemo

# Hiển thị sự kiện GC (ví dụ này có thể không phát sinh GC)
java -Xlog:gc* JvmFlowDemo

# Ví dụ thiết lập heap cho ứng dụng thực tế; không phải tổng RAM tiến trình
java -Xms256m -Xmx512m JvmFlowDemo
```

Với ứng dụng JVM đang chạy, dùng `jcmd <pid> GC.heap_info` để xem thông tin heap mà triển khai hỗ trợ; `jcmd <pid> Thread.print` giúp xem stack/thread, còn các công cụ profiling/JFR giúp phân tích allocation, pause và CPU. Cần chọn đúng PID và đọc log theo **thời gian/collector thực tế**; đừng dùng một lần chạy ngắn không phát sinh GC để kết luận GC đã bị tắt.

Các dấu hiệu thường gặp cần định vị đúng thành phần:

| Triệu chứng | Hướng điều tra đầu tiên |
| --- | --- |
| `ClassNotFoundException` / `NoClassDefFoundError` | Classpath/module path, class loader, và khác biệt giữa không tìm thấy định nghĩa với lỗi khi liên kết/khởi tạo |
| `ClassCastException` giữa các plugin dù tên kiểu giống nhau | Class identity theo defining loader; dependency/shared API |
| `StackOverflowError` | Đệ quy sâu hoặc vòng gọi lặp, dùng JVM stack |
| `OutOfMemoryError: Java heap space` | Giới hạn/nhu cầu heap, allocation rate, retention graph |
| `OutOfMemoryError: Metaspace` | Class metadata, loader retention, giới hạn Metaspace |
| Pause dài hoặc throughput giảm | GC logs, heap pressure, phân bổ/cấu hình collector; đồng thời kiểm tra tải ứng dụng |
| CPU tăng ở phương thức chạy nhiều | Profiling, hotspot methods, JIT, khóa cạnh tranh; không tự kết luận JIT gây lỗi |

`OutOfMemoryError` có nhiều nguyên nhân ngoài heap; việc tăng `-Xmx` không tự động xử lý class-loader leak, stack overflow, native memory exhaustion hay thread/resource leaks.

## 9. Những nhầm lẫn dễ gặp

1. **“Loader đọc class là chạy constructor ngay.”** Không đúng. Nạp, liên kết và khởi tạo lớp khác với gọi constructor của một **object instance**.
2. **“Mọi đối tượng đều nằm trên stack vì biến cục bộ nằm trong stack.”** Không đúng về mô hình JVM: đối tượng có ngữ nghĩa heap; biến cục bộ có thể chứa tham chiếu. JIT còn có thể tối ưu placement.
3. **“Đặt biến bằng `null` sẽ buộc GC xóa đối tượng.”** Không có cam kết như vậy. Thu hồi dựa trên reachability và collector.
4. **“Không có `free()` nghĩa là không thể rò rỉ bộ nhớ.”** Đối tượng có thể bị giữ bởi cache, listener, queue, `static` hoặc `ThreadLocal` dù không còn cần dùng.
5. **“GC thay cho `close()`.”** GC quản lý chủ yếu bộ nhớ heap; file, socket và connection nên được đóng đúng vòng đời.
6. **“Java được chạy hoàn toàn bằng thông dịch.”** HotSpot có JIT; chiến lược thực thi có thể biến đổi khi chương trình chạy.
7. **“`-Xmx` là toàn bộ giới hạn RAM của JVM.”** Nó chỉ kiểm soát maximum Java heap; còn thread stacks, Metaspace, code cache và native allocations.
8. **“Method area luôn chính là một vùng Java heap tên PermGen.”** Sai với HotSpot hiện đại: PermGen đã được thay bằng Metaspace từ Java 8; JVM spec không bắt buộc chi tiết tổ chức HotSpot.
9. **“Cứ xảy ra GC là toàn bộ thread bị dừng một khoảng giống nhau.”** Phụ thuộc thuật toán và pha GC; có collector làm nhiều việc đồng thời và có những điểm phải tạm dừng ứng dụng.

## 10. Tự kiểm tra khả năng giải thích

- Tại sao in `Config initialized` xảy ra sau `main begins` trong ví dụ, dù `Config` đã được biên dịch cùng chương trình?
- Khi gọi `twice(7)`, frame/biến cục bộ đóng vai trò gì và execution engine làm gì?
- Nếu một cache `static` luôn giữ danh sách đối tượng, vì sao GC chưa thể thu hồi các đối tượng đó?
- Vì sao hai plugin nạp cùng tên lớp nhưng bởi hai defining loader có thể không ép kiểu được cho nhau?
- Khi ứng dụng báo lỗi heap, Metaspace hay stack, vì sao cách xử lý không nên giống hệt nhau?

**Ghi nhớ:** loader xử lý **định nghĩa lớp**; execution engine xử lý **lệnh**; runtime memory giữ **trạng thái và đối tượng**; GC xử lý **đối tượng heap không còn reachable**. Từng thành phần có nhiệm vụ riêng nhưng chỉ có ý nghĩa khi nhìn cùng dòng thực thi.

### Tài liệu chuẩn để tra cứu thêm

- [Java Virtual Machine Specification 17 — Runtime Data Areas (§2.5)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5)
- [Java Virtual Machine Specification 17 — Loading, Linking, and Initializing (Chapter 5)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)
- [Java Language Specification 17 — Initialization of Classes and Interfaces (§12.4)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-007">Các ngôn ngữ khác trên JVM và hợp đồng chung của tệp class</a>

<details>
<summary>Xem chi tiết</summary>

# Các ngôn ngữ khác trên JVM và hợp đồng chung của tệp class

## 1. JVM chạy bytecode, không chỉ chạy mã nguồn Java

Java Virtual Machine (JVM) là một máy ảo có đặc tả cho **class file**, bytecode, tải lớp, liên kết, kiểu dữ liệu, lời gọi phương thức và thực thi. JVM không yêu cầu chương trình ban đầu được viết bằng Java: bất kỳ công cụ nào sinh ra tệp `.class` phù hợp với đặc tả, cùng những thư viện runtime cần thiết, đều có thể chạy trên JVM tương thích.

~~~text
Java (.java) ──────── javac ──────────────┐
Kotlin/JVM (.kt) ──── kotlinc ────────────┤
Scala (.scala) ────── scalac ─────────────┼─→ .class (có thể gói trong .jar)
Groovy (.groovy) ──── compiler/runtime ───┤                    │
Clojure (.clj) ────── compiler/runtime ───┘                    ▼
                                           class loader → verify/link → JVM
~~~

Các ngôn ngữ chia sẻ **đích thực thi**, không nhất thiết chia sẻ cú pháp, hệ kiểu hay cách tổ chức chương trình. Kotlin có nullability ở cấp compiler; Scala có các cách biểu diễn kiểu/hàm khác Java; Clojure có namespace, dữ liệu bất biến và mô hình động; Groovy hỗ trợ metaprogramming. Compiler/runtime của từng ngôn ngữ ánh xạ các đặc trưng ấy thành các cấu trúc JVM: class, method, field, instruction, metadata và thư viện hỗ trợ.

**Phạm vi:** ở đây “Kotlin” nghĩa là **Kotlin/JVM**, không phải Kotlin/JS hay Kotlin/Native. Những triển khai khác của cùng một ngôn ngữ có thể sử dụng nền tảng khác. Các tên JRuby, Jython, v.v. chỉ những triển khai cụ thể chạy trên JVM, không phải bằng chứng rằng mọi triển khai Ruby/Python tự động chạy được Java bytecode.

## 2. Những ngôn ngữ JVM tiêu biểu và ranh giới tương tác

| Ngôn ngữ | Mô hình chạy phổ biến | Khi giao tiếp với Java |
| --- | --- | --- |
| Java | `javac` biên dịch sang class file | Java API và ABI là chuẩn liên kết quen thuộc |
| Kotlin/JVM | `kotlinc` sinh class, dùng Kotlin standard library | Getter, hàm top-level, tham số mặc định và nullability có ánh xạ riêng |
| Scala | `scalac` sinh class, dùng scala-library | Trait, companion object, hàm và tên member có thể sinh bytecode khác biểu đạt nguồn |
| Groovy | Compiler/runtime trên JVM, có cơ chế gọi động | Một phần thao tác phụ thuộc Groovy runtime và call site |
| Clojure | Compiler/runtime tạo hoặc nạp class JVM | Function/namespace và dữ liệu Clojure không tự thành JavaBean thông thường |
| JRuby và ngôn ngữ khác | Interpreter, sinh bytecode hay JIT tùy triển khai | Interop tùy API bridge và runtime của triển khai đó |

Một JAR là một kho lưu trữ ZIP thường chứa `.class`, resources và metadata, **không phải một thứ bytecode thứ hai**. JAR có thể thiếu dependency runtime; chạy thành công cần classpath/module path và phiên bản thư viện thích hợp. Một class hợp lệ cũng không có nghĩa mọi phương thức của ngôn ngữ nguồn đều có một public Java method dễ gọi.

## 3. Hợp đồng nhị phân chung: class-file format

Ở mức cấu trúc, định dạng `ClassFile` gồm:

~~~text
magic (0xCAFEBABE)
minor_version, major_version
constant_pool_count, constant_pool
access_flags
this_class, super_class
interfaces_count, interfaces
fields_count, fields
methods_count, methods
attributes_count, attributes
~~~

- **Magic** nhận diện class file; **major/minor version** cho biết mức định dạng mà JVM phải hỗ trợ.
- **Constant pool** chứa thông tin hằng, tên, descriptor, tham chiếu kiểu/thành viên và dữ liệu liên quan để bytecode tham chiếu.
- **Access flags** mô tả đặc điểm class/interface; **this_class**, **super_class**, **interfaces** mô tả quan hệ kiểu ở JVM-level.
- **Fields/methods** chứa tên, quyền truy cập, kiểu theo **descriptor** và thuộc tính liên quan. Với method có bytecode, thuộc tính `Code` chứa instruction và thông tin phục vụ thực thi.
- **Attributes** cho phép ghi `Code`, `StackMapTable`, `Signature`, exceptions, annotations, thông tin debug và metadata riêng của compiler/ngôn ngữ.

Tên nội bộ kiểu JVM dùng dấu `/` như `java/lang/String`; Java hiển thị tên nhị phân dạng `java.lang.String`. Descriptor là một phần quan trọng của ABI:

| Descriptor JVM | Kiểu/ý nghĩa |
| --- | --- |
| `I`, `J`, `Z` | `int`, `long`, `boolean` |
| `V` | `void`, dùng cho return type |
| `Ljava/lang/String;` | tham chiếu `java.lang.String` |
| `[I` | mảng `int[]` |
| `(I)Ljava/lang/String;` | method nhận `int`, trả `String` |
| `(Ljava/lang/String;)V` | method nhận `String`, trả `void` |

Lời gọi JVM liên kết dựa trên class/interface sở hữu member, tên, descriptor, loại lời gọi và quy tắc truy cập. Một phương thức được Java mô tả là `String greet(String)` có descriptor `(Ljava/lang/String;)Ljava/lang/String;`. Kiểu generic ở mã nguồn có thể đi qua **type erasure** và được biểu diễn bổ sung trong `Signature`. Descriptor bytecode không tự chứa mọi ràng buộc cấp nguồn như Kotlin nullable/non-nullable hoặc mọi chi tiết generic Scala.

### Phiên bản tệp class

Ví dụ, Java 17 phát hành class file major **61**; Java 21 dùng major **65**. JVM chỉ đọc được những mức class-file version được đặc tả cho bản runtime ấy. Nếu mang class xây bằng mục tiêu mới sang JVM cũ không hỗ trợ, có thể gặp `UnsupportedClassVersionError`.

Với mã Java, `javac --release 17` trên JDK hỗ trợ tùy chọn đó đặt mức bytecode và giới hạn các API Java chuẩn theo Java 17. Với Kotlin/Scala hoặc các compiler khác, tùy chọn JVM target cần được kiểm tra riêng: đặt target bytecode **không tự chứng minh** mọi dependency và API được gọi đều có thể chạy trên JVM cũ. Các tính năng preview còn có quy tắc class-file riêng; tránh suy đoán chỉ qua số major nếu chương trình sử dụng preview.

## 4. JVM tải, kiểm chứng, liên kết và thực thi class như thế nào?

1. **Loading:** class loader tìm hoặc nhận bytes và định nghĩa kiểu. Định danh runtime của một class phụ thuộc **tên nhị phân và defining class loader**; hai defining loader khác nhau có thể tạo hai kiểu riêng dù tên trùng nhau.
2. **Linking:** JVM **verify** ràng buộc class/bytecode, **prepare** dữ liệu static và **resolve** tham chiếu biểu tượng theo quy tắc; việc resolve có thể diễn ra từng phần khi cần.
3. **Initialization:** tại các điểm kích hoạt được đặc tả, JVM khởi tạo class/interface và thực hiện các static initializer tương ứng.
4. **Execution:** instruction hoạt động với local variables, operand stack, đối tượng/heap và cơ chế runtime khác. JVM cụ thể có thể thông dịch rồi JIT-compile mã nóng; điều này không đòi hỏi nguồn ban đầu là Java.

Các instruction gọi như `invokevirtual`, `invokestatic`, `invokeinterface`, `invokespecial`, `invokedynamic` thực hiện cơ chế gọi khác nhau. Một compiler có thể tạo lambda, bridge method, synthetic class, state machine hoặc bootstrap method để chuyển đặc trưng ngôn ngữ thành thao tác JVM. Việc nhìn thấy `invokedynamic` cũng không đồng nghĩa nguồn chắc chắn là ngôn ngữ động: Java cũng có các trường hợp dùng instruction ấy.

**Verifier** kiểm tra tính hợp lệ của bytecode theo đặc tả, không chứng minh chương trình đúng nghiệp vụ, không áp đặt mọi quy tắc nullability của compiler Kotlin và không phải cơ chế sandbox bảo mật toàn diện.

## 5. Ví dụ Java 17+: tự tạo và xem class file

Tạo tệp `src/demo/ContractDemo.java` với nội dung **đầy đủ**:

~~~java
package demo;

interface Message {
    String text();
}

final class Greeting implements Message {
    private final String name;

    Greeting(String name) {
        this.name = name;
    }

    @Override
    public String text() {
        return "Xin chào, " + name;
    }
}

public class ContractDemo {
    public static void main(String[] args) {
        Class<?> type = Greeting.class;
        System.out.println(type.getName());
        System.out.println(type.getSuperclass().getName());
        System.out.println(type.getInterfaces()[0].getName());
        System.out.println(new Greeting("JVM").text());
    }
}
~~~

Từ thư mục chứa `src`, dùng JDK 17+:

~~~bash
javac --release 17 -d out src/demo/ContractDemo.java
java -cp out demo.ContractDemo
~~~

Stdout chính xác:

~~~text
demo.Greeting
java.lang.Object
demo.Message
Xin chào, JVM
~~~

Mặc dù cùng khai báo trong một source file, trình biên dịch tạo các class file riêng cho `ContractDemo`, `Greeting` và `Message`. Công cụ `javap` của JDK cho phép đọc hợp đồng nhị phân thực tế:

~~~bash
javap -classpath out -p -s demo.Greeting
javap -classpath out -c -p demo.Greeting
javap -classpath out -v -p demo.Greeting
~~~

`-s` hiện descriptor, chẳng hạn `text()` trả `java.lang.String` có descriptor `()Ljava/lang/String;`; `-c` hiện instruction, `-v` hiện constant pool, version và attribute; `-p` xem cả member không public. Vì lệnh biên dịch dùng `--release 17`, class file có major version **61**. Chi tiết instruction nối chuỗi có thể khác giữa compiler, nên không ép toàn bộ output `javap` giống hệt mọi bản JDK.

Quan sát trên giúp phân biệt ba cấp: **source Java** (cú pháp bạn viết), **class-file API/bytecode** (compiler tạo) và **hành vi runtime** (JVM thực thi). Ngôn ngữ JVM khác vẫn phải qua ranh giới class file nhưng có thể chọn cách sinh code khác.

## 6. Ví dụ Kotlin/JVM sinh class để Java gọi

Ví dụ này yêu cầu **JDK 17+ và Kotlin command-line compiler/runtime**. Tạo `src/demo/Greeter.kt`:

~~~kotlin
package demo

class Greeter(private val who: String) {
    fun greet(): String = "Hello, $who"
}
~~~

Tạo `src/demo/JavaCaller.java`:

~~~java
package demo;

public class JavaCaller {
    public static void main(String[] args) {
        Greeter greeter = new Greeter("JVM");
        System.out.println(greeter.greet());
    }
}
~~~

Các lệnh dưới đây giả định `kotlinc`, `javac` và `kotlin` nằm trong PATH:

~~~bash
kotlinc -jvm-target 17 src/demo/Greeter.kt -d out
javac --release 17 -cp out -d out src/demo/JavaCaller.java
kotlin -cp out demo.JavaCaller
~~~

Stdout:

~~~text
Hello, JVM
~~~

Vì `kotlinc` đã tạo `demo/Greeter.class`, `javac` có thể biên dịch `JavaCaller` nhờ classpath `out`. Java gọi constructor và public method `greet()` dựa trên ABI JVM, không cần hiểu cú pháp Kotlin. Lệnh `kotlin` cung cấp Kotlin standard library khi chạy. Nếu thay bằng `java -cp out demo.JavaCaller` mà không bổ sung Kotlin runtime, chương trình có thể lỗi thiếu class runtime; dự án thực tế dùng Gradle/Maven để khai báo và đóng gói dependency.

Kiểm tra trực tiếp ABI bằng:

~~~bash
javap -classpath out -p -s demo.Greeter
~~~

Kết quả sẽ cho thấy JVM constructor, private field và method được compiler tạo ra; không cần suy luận member JVM chỉ từ hình dạng cú pháp Kotlin.

## 7. Vì sao interop đa ngôn ngữ không hoàn toàn trong suốt?

### 7.1. API nhìn từ nguồn khác API nhìn từ JVM

Với Kotlin/JVM, thuộc tính `val name: String` thường có getter `getName()` để Java gọi thay vì mặc định là public field. Hàm top-level được sinh trong class có tên theo file hoặc tên được tùy biến. Tham số mặc định **không đương nhiên** tạo mọi overload Java; `@JvmOverloads` hỗ trợ một số trường hợp. `@JvmName`, `@JvmStatic`, `@JvmField` điều chỉnh cách trình bày API JVM khi thích hợp.

`suspend` function có thể dùng continuation và state machine; method JVM trong `javap` có thể khác chữ ký Kotlin đọc ở source. Scala có companion object, trait và các cấu trúc biên dịch riêng; Groovy/Clojure có chức năng được gọi qua runtime động. Muốn caller Java sử dụng ổn định, hãy thiết kế và kiểm tra **public JVM API**, không đoán dựa trên tính giống nhau của tên hàm nguồn.

### 7.2. Nullability, generic và kiểu nâng cao

JVM phân biệt primitive/reference và các descriptor, nhưng không có một chế độ type system chung cho mọi ngôn ngữ nguồn. Ví dụ Kotlin `String` và `String?` có thể đều dùng descriptor tham chiếu `Ljava/lang/String;`; compiler, annotation, metadata và các kiểm tra runtime bổ sung diễn đạt khác biệt. Code Java vẫn có thể truyền `null`, nên ranh giới Java/Kotlin cần hợp đồng nullability rõ ràng.

Với Java generic thông thường, `List<String>` và `List<Integer>` cùng dùng một kiểu JVM sau erasure; thông tin generic nguồn có thể xuất hiện ở `Signature` và không tương đương với hai runtime class riêng. Bridge method, cast hoặc runtime type token có thể cần thiết khi ánh xạ những abstraction khác nhau.

### 7.3. Exception và thư viện runtime

Checked exception là quy tắc kiểm tra ở compiler Java, **không phải quy tắc verifier ép mọi caller JVM phải catch**. Kotlin không áp dụng kiểm tra checked exception giống Java; việc công bố thông tin `throws` cho Java cần cơ chế compiler/annotation tương ứng khi cần.

Code biên dịch xong không có nghĩa đã độc lập với runtime ngôn ngữ. Kotlin có Kotlin standard library; Scala có scala-library; Groovy/Clojure có runtime tương ứng. Phải kiểm tra cả **compile classpath** (để biên dịch caller), **runtime classpath/module path** (để nạp và chạy), phiên bản các dependency và cách đóng gói JAR. “Có một file .jar” chưa chứng minh nó là executable fat JAR tự đủ thư viện.

### 7.4. Class loader và ranh giới kiểu runtime

Nếu hai defining class loader nạp hai bản cùng tên `example.Greeter`, JVM xem chúng là hai kiểu; cast có thể ném `ClassCastException` dù tên hiển thị giống nhau. Tình huống thường gặp ở plugin, container ứng dụng và hot reload. Hợp đồng class file không tự loại bỏ vấn đề định danh kiểu bởi loader.

## 8. Source compatibility, binary compatibility và hành vi

Có ba tầng tương thích cần xét độc lập:

1. **Source compatibility:** caller giữ nguyên source có còn biên dịch được với API mới?
2. **Binary compatibility (ABI):** caller đã biên dịch có còn liên kết/chạy với thư viện mới, không cần biên dịch lại?
3. **Behavioral compatibility:** API vẫn chạy nhưng kết quả, side effect và quy ước có còn đúng?

Xóa method/constructor, đổi tên JVM member, đổi descriptor, thay static thành instance hoặc thay quan hệ kế thừa có thể gây lỗi liên kết của caller cũ. Ngược lại, giữ ABI chưa chắc giữ hành vi. Version compiler và runtime của Kotlin/Scala cũng có cam kết binary compatibility riêng: cùng là bytecode JVM không chứng minh mọi phiên bản của chúng hoán đổi cho nhau.

| Lỗi/hiện tượng | Hướng điều tra |
| --- | --- |
| `UnsupportedClassVersionError` | Class-file version cao hơn mức JVM hiện chạy hỗ trợ |
| `ClassNotFoundException` | Cơ chế tìm class theo tên không tìm được class phù hợp |
| `NoClassDefFoundError` | Class cần lúc thực thi không nạp/định nghĩa được, có thể do dependency hoặc lỗi khởi tạo trước đó |
| `NoSuchMethodError` | Caller đòi method name/descriptor không tồn tại trong library runtime |
| `AbstractMethodError`, `IncompatibleClassChangeError` | Quan hệ class/method lúc chạy lệch với cách caller đã liên kết |
| `VerifyError` | Bytecode/class file không vượt qua kiểm chứng |
| `ClassCastException` khi tên kiểu giống nhau | Kiểm tra quan hệ kế thừa và defining class loader |

Không kết luận nguyên nhân chỉ dựa vào tên exception; cần stack trace, dependency tree, classpath/module path, class loader và class file thật đã tải.

## 9. Quy trình khám phá một thư viện JVM lạ

Khi muốn gọi thư viện biên dịch từ Java hoặc ngôn ngữ JVM khác, đi theo các bước thực tế:

1. **Xem các class và resources:** `jar tf library.jar`.
2. **Xác minh API công khai:** `javap -classpath library.jar -public -s ten.package.TenClass`, chú ý member name và descriptor.
3. **Xem metadata và version khi cần:** `javap -classpath library.jar -p -v ten.package.TenClass`; kiểm tra class-file major, `Signature`, annotation, synthetic/bridge method.
4. **Đối chiếu môi trường runtime:** JDK version, compiler target, Kotlin/Scala/Groovy/Clojure runtime, dependency versions, module path hoặc classpath.
5. **Biên dịch một Java caller tối thiểu:** chạy thử với đúng runtime/dependency để kiểm tra linkage thay vì chỉ nhìn được source.
6. **Khi nâng phiên bản:** so sánh ABI đã sinh và chính sách tương thích của thư viện/compiler, nhất là khi caller không được biên dịch lại.

Cùng chạy trên JVM **không bảo đảm hiệu năng giống nhau**. Allocation, boxing, lời gọi động, reflection, coroutine scheduling, JIT warm-up và implementation thư viện đều ảnh hưởng. Muốn so sánh công bằng phải đo bằng workload/benchmark phù hợp.

## 10. Sai lầm thường gặp

- “JVM chỉ chạy Java”: sai, nó chạy code theo hợp đồng JVM class-file/runtime.
- “Tất cả class file chạy trên mọi JVM”: sai, phải xét bytecode version, runtime, dependency và môi trường.
- “Cùng JVM nên cùng nullability/generic/exception rule”: sai, nhiều ràng buộc do compiler nguồn hoặc runtime riêng đảm nhận.
- “Mã Kotlin/Scala hợp lệ thì Java gọi mọi hàm bằng cùng tên”: sai, cần kiểm tra JVM ABI thực tế.
- “JAR đã có mọi dependency”: sai, chỉ đúng với những kiểu đóng gói đã bao gồm chúng.
- “Verifier chứng minh bytecode đúng và an toàn”: sai, nó xác minh các ràng buộc JVM, không thay logic testing và security review.

## 11. Câu hỏi tự kiểm tra

1. Vì sao `javap` đọc được `Greeter.class` do Kotlin compiler sinh? Vì `.class` tuân thủ cấu trúc JVM class-file format.
2. Phân biệt `(Ljava/lang/String;)V` với `()Ljava/lang/String;`? Cái đầu nhận `String` và không trả kết quả; cái sau không nhận tham số và trả `String`.
3. Hai class cùng tên nhưng do hai defining loader khác nhau định nghĩa có phải một kiểu runtime? Không.
4. Khi Java gọi Kotlin library và xuất hiện lỗi thiếu `kotlin.*`, cần kiểm tra gì trước? Runtime classpath/dependency và Kotlin standard library.
5. Khi cập nhật JAR gây `NoSuchMethodError`, công cụ đầu tiên nào giúp kiểm tra? `javap -s` trên class của JAR runtime để so method name và descriptor với ABI caller mong đợi.

**Điểm cốt lõi:** JVM class file là hợp đồng chung để tải, kiểm chứng, liên kết và thực thi nhiều ngôn ngữ. Khả năng hợp tác thực tế phụ thuộc thêm vào **ABI được compiler tạo**, version và runtime dependency, class loader và ngữ nghĩa bổ sung của từng ngôn ngữ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-overview-008">Cách đọc tài liệu theo phiên bản JDK và tách quy tắc khỏi chi tiết triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Cách đọc tài liệu theo phiên bản JDK và tách quy tắc khỏi chi tiết triển khai

Khi học JVM, cùng một câu mô tả có thể là **quy tắc bắt buộc của Java**, **hợp đồng của một API**, **khả năng của một phiên bản JDK**, hoặc chỉ là **cách một JVM cụ thể đang cài đặt**. Nhầm bốn loại này dẫn tới kết luận sai khi đổi Java 17 sang 21, nâng JDK của nhà cung cấp khác, hoặc điều chỉnh cờ chạy chương trình. Mục tiêu của bài là tìm đúng tài liệu cho đúng phiên bản và biết chính xác điều gì được bảo đảm.

## 1. Phân biệt lớp tài liệu và đối tượng được mô tả

| Câu hỏi | Nguồn nên đọc trước | Loại kết luận có thể rút ra |
| --- | --- | --- |
| Cú pháp Java, kiểu dữ liệu, overload, class initialization, Java Memory Model? | **JLS** (Java Language Specification) của Java SE tương ứng | Quy tắc ngôn ngữ Java, ví dụ khi nào phép gán hợp lệ và ràng buộc happens-before |
| Bytecode, cấu trúc `.class`, loading/linking, runtime data areas, lệnh JVM? | **JVMS** (Java Virtual Machine Specification) đúng phiên bản | Ràng buộc đối với JVM phù hợp đặc tả, không mặc định áp đặt thuật toán triển khai |
| `HashMap`, `LinkedHashMap`, `Thread`, `String` bảo đảm điều gì? | **Java SE API Specification/Javadoc** đúng phiên bản | Hợp đồng public API, ngoại lệ, tính thứ tự, điều kiện sử dụng |
| `java`, `javac`, `jcmd` có những tùy chọn nào? | **JDK tool guides/man pages** của JDK đang dùng | Cách gọi công cụ và phạm vi hỗ trợ cho phiên bản/phân phối ấy |
| Khi nào xuất hiện một tính năng, được chuẩn hóa hay còn preview? | **Release notes**, tài liệu tính năng và **JEP**, đối chiếu đặc tả cuối cùng | Lịch sử, động cơ, trạng thái thay đổi; JEP không thay thế hợp đồng API/đặc tả đã phát hành |
| HotSpot dùng collector, JIT, layout đối tượng, ngưỡng tối ưu hóa nào? | **Tài liệu VM cụ thể**, option/help, source code và phép đo trên đúng bản build | Hành vi triển khai trong điều kiện xác định; không tự suy rộng sang mọi JVM |

**JDK** là bộ công cụ/phân phối dùng phát triển và chạy ứng dụng; **Java SE** là nền tảng có các đặc tả và API; **JVM** là máy ảo thực thi class files; **HotSpot** là một triển khai JVM. Một JDK thường chứa HotSpot nhưng không được đồng nhất HotSpot với chính JVMS. Nói “JVM luôn dùng C2/G1”, “mọi đối tượng luôn có header 12 byte” là sai về phạm vi cam kết.

Một đoạn trong tài liệu cần được ghi kèm **phiên bản, loại tài liệu và phạm vi phát biểu**. “Java 17” phải làm rõ là Java SE 17 specification, JDK 17 compiler, hay một bản build runtime 17 cụ thể.

## 2. Khóa đúng phiên bản trước khi đọc

Trên máy biên dịch và máy chạy chương trình, kiểm tra độc lập:

```text
java -version
javac -version
java -XshowSettings:properties -version
```

- `java -version`: runtime được PATH hoặc đường dẫn hiện tại chọn; thường hiển thị version, vendor/build và VM.
- `javac -version`: compiler được chọn; có thể thuộc một JDK khác với lệnh `java`.
- Trong kết quả properties, xem `java.version`, `java.vendor`, `java.vm.name`, `java.home`; giá trị thực tế tùy môi trường. Nếu dùng IDE, Gradle, Maven, container hay CI, kiểm tra **JDK thực sự** mà công cụ ấy gọi.
- Ghi thêm hệ điều hành, kiến trúc, vendor, build/patch và các VM flags nếu đang đối chiếu hành vi hiệu năng hoặc GC.

Các con số cần phân biệt:

1. **Phiên bản ngôn ngữ**: cú pháp mà compiler chấp nhận, ví dụ Java 17.
2. **Phiên bản public API**: phương thức/lớp được phép gọi trên nền tảng đích.
3. **Phiên bản class file**: định dạng bytecode mà JVM có thể nạp.
4. **Phiên bản runtime thực tế**: JVM và thư viện chạy production.

Ví dụ class-file major tương ứng: Java 8 → 52, Java 11 → 55, Java 17 → 61, Java 21 → 65, Java 25 → 69, Java 27 → 71. Đây là phiên bản định dạng class file được giới thiệu, **không phải** chứng cứ chắc chắn file được tạo bằng JDK nào: compiler JDK mới có thể phát sinh bytecode cho đích cũ.

Khi biên dịch cho runtime Java 17 bằng một `javac` có hỗ trợ release 17, thường dùng:

```bash
javac --release 17 ReadContracts.java
javap -verbose ReadContracts
java ReadContracts
```

Trong `javap -verbose`, tìm dòng `major version: 61`. Tùy chọn `--release 17` giới hạn phiên bản ngôn ngữ, định dạng class file **và API nền tảng được tài liệu hóa** mà compiler cho phép sử dụng. Chỉ đặt `-source 17 -target 17` chưa đủ để bảo đảm bạn không vô tình tham chiếu API chỉ có trên JDK mới. `--release` cũng không chứng minh mọi thư viện ngoài, native library và hành vi môi trường đều tương thích Java 17; cần build/test với runtime đích.

Để chạy class file cần hỗ trợ major/minor tương ứng. Lỗi `UnsupportedClassVersionError` thường báo runtime không đọc được phiên bản class file; trước khi kết luận, xem dòng major, runtime thực dùng và khả năng dự án có sử dụng **preview feature** (class file preview có quy tắc riêng, có thể đòi hỏi `--enable-preview` và đúng release).

## 3. Tìm tài liệu theo một “đường đi” có thể kiểm chứng

**Bước 1 — Xác định câu hỏi thuộc lớp nào.** Ví dụ: “JVM có bắt buộc lưu object trên heap?” liên quan JVMS/JLS; “GC nào mặc định?” liên quan VM options của build; “`Map` có thứ tự duyệt ổn định?” phải đọc hợp đồng API của implementation `Map` cụ thể.

**Bước 2 — Chọn edition, không chỉ chọn kết quả tìm kiếm đầu tiên.** Trang đặc tả thường có `se17`, `se21`, `se27`; Javadoc/man pages thường nằm dưới `/en/java/javase/17/`, `/21/`, v.v. Không ghép một trích dẫn JLS 17 với một đoạn API 27 rồi coi là cùng hợp đồng. Khi bài toán liên quan *hai* runtime, mở **cả hai** bản tài liệu và so các điều khoản tương ứng.

**Bước 3 — Đọc phần quy định nguyên văn trong ngữ cảnh.** Kiểm tra heading, precondition, ngoại lệ, “must/shall”, “may”, “typically”, “implementation note”, “since”, “deprecated”, “preview”, “unspecified”, “implementation-specific”. Đừng biến các từ “thường”, “có thể”, “ví dụ” thành “luôn”, “bắt buộc”. Ví dụ code trong tài liệu giúp hiểu quy tắc nhưng không nhất thiết mô tả mọi trường hợp.

**Bước 4 — Đối chiếu việc thay đổi phiên bản.** Từ yêu cầu thiết kế hoặc tài liệu feature đi tới đặc tả/Javadoc cuối cùng của chính release đó; dùng release notes để xác định sửa đổi, loại bỏ hoặc thay đổi mặc định. Nếu có JEP, kiểm tra trạng thái và release mục tiêu, đặc biệt với preview/incubator.

**Bước 5 — Kiểm chứng hành vi thực tế khi câu hỏi nói về hiệu năng.** Ghi JVM vendor/build và cờ khởi động, tái hiện benchmark/profile trên đúng máy. Một lần quan sát không thể “nâng” chi tiết HotSpot thành quy tắc Java.

### Độ mạnh của chứng cứ

Có thể diễn đạt kết luận theo mẫu: “**Theo [tài liệu + edition + mục], trong điều kiện [điều kiện], hành vi [X] được bảo đảm/không được bảo đảm; quan sát [Y] là của [runtime/build]**”.

| Dấu hiệu trong tài liệu | Cách diễn giải an toàn |
| --- | --- |
| Quy định của JLS/JVMS hoặc yêu cầu bắt buộc trong API | Điều mọi implementation phù hợp phạm vi ấy phải đáp ứng |
| Javadoc mô tả order/exception/case cụ thể | Hợp đồng cho class/method và điều kiện đã ghi; không suy rộng sang class khác |
| `Implementation Note` hoặc “in this implementation” | Thông tin cách cài đặt hiện tại, có thể đổi khi đổi JDK/build |
| “Typically”, “usually”, “may” | Mô tả trường hợp thường gặp/khả năng; không phải bảo đảm mọi lần |
| “No guarantee”, “unspecified” | Chương trình **không được phụ thuộc** vào giá trị/thứ tự đó, dù quan sát thấy nó lặp lại |
| JEP, bug ticket, bài blog, benchmark | Giải thích lịch sử hoặc quan sát; đối chiếu tài liệu chuẩn và phiên bản phát hành |

“Unspecified” không đồng nghĩa lỗi ngẫu nhiên: implementation có thể cho cùng kết quả rất lâu, nhưng hợp đồng công khai không hứa hẹn kết quả ấy. Ngược lại, “implementation-dependent” không cho phép ta tùy ý nói mọi hành vi đều đúng; implementation vẫn phải đáp ứng các ràng buộc bắt buộc khác.

## 4. Ví dụ 1 — Một kết quả đúng ở máy tôi chưa phải hợp đồng API

Javadoc Java 17 của `HashMap` nói rõ **không bảo đảm** thứ tự các phần tử và không hứa thứ tự giữ nguyên theo thời gian. Javadoc `LinkedHashMap` mô tả thứ tự duyệt có thể dự đoán được; với constructor mặc định, đó là thứ tự chèn khóa.

Tạo file **`ReadContracts.java`**:

```java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReadContracts {
    public static void main(String[] args) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        ordered.put("beta", 2);
        ordered.put("alpha", 1);
        System.out.println(ordered.keySet());

        Map<String, Integer> unordered = new HashMap<>();
        unordered.put("beta", 2);
        unordered.put("alpha", 1);
        System.out.println(unordered.containsKey("alpha"));
    }
}
```

Biên dịch/chạy trên JDK 17 trở lên có hỗ trợ `--release 17`:

```bash
javac --release 17 ReadContracts.java
java ReadContracts
```

**Stdout**:

```text
[beta, alpha]
true
```

- Dòng đầu ổn định theo hợp đồng của `LinkedHashMap` dùng chế độ insertion-order. Nếu chọn constructor `accessOrder=true`, quy tắc thứ tự khác đi; luôn đọc đúng constructor.
- Dòng hai chỉ hỏi `HashMap` có chứa khóa hay không. Đừng lấy thứ tự `unordered.keySet()` in được trên một máy để viết test hoặc business logic.
- Class file được tạo bằng `--release 17` có major 61; nguồn code chạy đúng trên một JDK 27 không có nghĩa hợp đồng này đã được đọc từ “Javadoc 27”. Điều đang chứng minh ở đây dựa vào API Java 17.

Cách sửa code phụ thuộc sai hợp đồng: nếu giao diện/JSON/report cần thứ tự chèn, chọn cấu trúc có bảo đảm thứ tự và viết test theo hợp đồng; nếu chỉ cần tra cứu khóa, không ràng buộc test vào thứ tự `HashMap`.

## 5. Ví dụ 2 — Phân biệt vùng nhớ chuẩn với cách HotSpot lưu trữ

JVMS mô tả các **run-time data areas** như heap, Java Virtual Machine stacks, method area, run-time constant pool và các ràng buộc liên quan. Đặc tả nói rõ JVM có quyền chọn nhiều cách tổ chức lưu trữ để đáp ứng yêu cầu quan sát được.

Hãy tách hai phát biểu:

- **Mức đặc tả:** JVM cung cấp heap là vùng bộ nhớ dùng chung giữa các thread để cấp phát class instances và arrays; vùng đó được quản lý tự động, và JVM không bắt buộc chọn một thuật toán thu gom rác cụ thể.
- **Mức triển khai:** trên một bản HotSpot, đối tượng có object header kích thước nhất định, JVM dùng compressed references hay collector cụ thể, hoặc JIT biên dịch phương thức sau một số lần gọi. Đây là các lựa chọn gắn với build/cấu hình/kiến trúc.

Vì thế, từ sơ đồ heap trong JVMS **không suy ra** mọi object có số byte giống nhau, có địa chỉ quan sát được ổn định, phải dùng G1, phải JIT, hoặc được biểu diễn bằng một layout cố định. Nếu câu hỏi là “Tại sao ứng dụng chiếm X MB?”, dùng profiler/GC logs/tài liệu HotSpot và cấu hình cụ thể, không chỉ dẫn định nghĩa heap từ đặc tả.

Tương tự, từ việc một GC là **mặc định** ở một JDK không suy ra chuẩn Java bắt buộc JVM khác chọn GC ấy. Các mặc định và cờ VM có thể thay đổi theo phiên bản, nền tảng hoặc nhà cung cấp.

## 6. Ví dụ 3 — Phân biệt xuất hiện tính năng và khả năng dùng trên runtime đích

Giả sử nhóm phát triển muốn dùng virtual threads. Hãy hỏi tuần tự:

1. Trong release nào tính năng là chính thức, hay còn preview? Đọc lịch sử JEP, rồi xác nhận API trong Javadoc của release mục tiêu.
2. Compiler, build tool và runtime production có cùng mức release không?
3. Code dùng API ấy có qua `javac --release <phiên-bản-production>` không? Ví dụ API virtual threads phổ biến được chuẩn hóa ở Java 21; mã dùng `Thread.ofVirtual()` không nằm trong Java SE 17 API.
4. Ràng buộc hành vi nào là public API, và điều nào chỉ là scheduling, pinning hay lựa chọn hiện tại của một VM? Đọc tài liệu tương ứng thay vì dự đoán từ một ảnh chụp thread dump.

Đừng hiểu JEP như một hợp đồng bất biến kể từ lúc đề xuất: tính năng có thể đi qua các vòng preview rồi thay đổi trước khi thành chính thức. Nếu ứng dụng chỉ hỗ trợ Java 17, việc máy developer cài JDK 21/27 không tự mở rộng API của production.

## 7. Tách “chạy được” khỏi “được bảo đảm” khi nâng JDK

Các tương thích cần xem **riêng**, tránh gom thành một câu “Java tương thích ngược”:

| Khía cạnh | Câu hỏi kiểm tra | Bằng chứng cần lấy |
| --- | --- | --- |
| Source compatibility | Mã nguồn cũ có còn biên dịch? Từ khóa/ngôn ngữ/preview thay đổi không? | JLS và tài liệu `javac` của release đích |
| Binary compatibility | Class file cũ có nạp và liên kết được? API signature có thay đổi? | JVMS class-file/loading và JLS binary compatibility, release notes |
| API compatibility | Method/class/module công khai đang dùng còn hiện diện, có thay đổi hợp đồng? | Javadoc đúng version, `@since`, deprecation/removal notes |
| Runtime behavior | Chương trình còn đúng trước khác biệt về thứ tự không được bảo đảm, timing, GC, locale...? | Đặc tả + test trên runtime đích |
| Operational compatibility | Cờ JVM, logging, monitoring, container limits, vendor/build có khác? | Man pages, VM guide và kiểm thử vận hành |

Có thể chạy ổn trên nhiều phiên bản là **bằng chứng thực nghiệm có ích** nhưng yếu hơn một bảo đảm được tài liệu hóa. Ngược lại, nếu một tình huống phụ thuộc vào chi tiết không được bảo đảm, chương trình vẫn có thể “pass” nhiều năm trước khi hỏng do thay JDK hoặc khối lượng dữ liệu.

## 8. Cách ghi chú kiến thức JVM có thể kiểm toán

Với mỗi phát biểu kỹ thuật, lưu một dòng có thể truy ngược:

```text
Claim: LinkedHashMap mặc định duyệt theo thứ tự chèn.
Scope: Java SE 17 API; java.util.LinkedHashMap; constructor mặc định.
Source: Javadoc Java SE 17, phần mô tả class và constructor.
Guarantee: Thứ tự chèn cho trường hợp này; không suy ra HashMap có cùng thứ tự.
Verification: ReadContracts.java trên JDK 17 và runtime đích.
Implementation detail: Cấu trúc linked list được mô tả, nhưng không cần
                     phụ thuộc trực tiếp vào node layout hay kích thước byte.
```

Đối với một claim như “HotSpot bản X, kiến trúc Y, bật Z dùng object header N byte”, ghi rõ lệnh nhận diện VM, build, cấu hình và công cụ đo; không chuyển nó thành câu “JVM object luôn N byte”. Khi tài liệu các phiên bản khác nhau, trình bày **trước/sau** và dẫn từng bản. Nếu chưa có nguồn bảo đảm, đánh dấu đó là quan sát hoặc giả thuyết cần xác nhận.

## 9. Những lỗi đọc tài liệu hay gặp

- **Nhầm “JVM” với “HotSpot”:** lấy tài liệu GC/JIT của HotSpot làm luật JVMS.
- **Nhầm JDK đang cài với nền tảng hỗ trợ:** máy build mới hơn máy production; thiếu `--release` và CI chạy trên runtime đích.
- **Dùng Javadoc sai version:** dựa vào API mới khi ứng dụng vẫn chạy Java cũ, hoặc áp đặt chi tiết Java cũ cho phiên bản mới.
- **Biến ví dụ thành quy tắc:** suy từ một output, bytecode compiler cụ thể, ảnh profiler hoặc một benchmark rằng mọi JVM phải giống như vậy.
- **Bỏ điều kiện của hợp đồng:** `LinkedHashMap` insertion-order khác access-order; cấu hình, đồng thời, mutability, null và ngoại lệ có thể đổi ý nghĩa của kết luận.
- **Đọc tiêu đề JEP mà không xem status:** nhầm proposed/preview/incubator với tính năng chính thức của release đang dùng.
- **Trộn “thực hiện được” với “ổn định lâu dài”:** relying on internal APIs, undocumented `-XX` flags, timing và memory layout có thể gây lỗi khi nâng cấp.

## 10. Checklist cho một phát biểu về Java/JVM

1. Tôi đã xác định **JDK/compiler/runtime version, vendor, build** thật sự chưa?
2. Câu hỏi nói về **ngôn ngữ, class-file/JVM, API, công cụ hay VM implementation**?
3. Tôi đang xem **edition nào**, và điều khoản nào quy định hành vi ấy?
4. Câu tôi trích là **must/guaranteed** hay chỉ “may/typically/implementation note”?
5. Có giới hạn nào về constructor, option, OS, kiến trúc, concurrency hoặc preview không?
6. Nếu nâng từ release A sang B, tôi đã đối chiếu **cả hai** tài liệu và test trên B chưa?
7. Tôi có đang dùng kết quả quan sát riêng như một bảo đảm chung không?

Nếu trả lời rõ bảy câu này, bạn sẽ phân biệt được **quy tắc có thể dựa vào khi viết code** và **đặc điểm cần đo/kiểm tra lại khi thay JVM**.

## Tài liệu gốc để tra cứu theo phiên bản

- [Java SE Language và JVM Specifications — danh mục các edition](https://docs.oracle.com/javase/specs/): chọn edition theo Java SE đang nghiên cứu.
- [JLS Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/): quy tắc ngôn ngữ, chương về threads/memory model và binary compatibility.
- [JVMS Java SE 17](https://docs.oracle.com/javase/specs/jvms/se17/html/): cấu trúc JVM, class file, loading/linking, các runtime data areas.
- [Java SE 17 API Specification](https://docs.oracle.com/en/java/javase/17/docs/api/index.html): đọc contract của từng class/method; đối chiếu trực tiếp [HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html) với [LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).
- [Tài liệu lệnh javac của Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): `--release`, `-source`, `-target`.
- [Danh mục JEP của OpenJDK](https://openjdk.org/jeps/0): lịch sử và tình trạng đề xuất; luôn đối chiếu đặc tả/API ở release phát hành.

**Nguyên tắc đọc:** trích đúng nguồn, đúng edition, đúng điều kiện, rồi mới quyết định có thể phụ thuộc vào hành vi ấy hay chỉ nên coi nó là chi tiết của một triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)
