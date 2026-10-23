<a id="back-to-top"></a>

# Cấu trúc chương trình

## Menu
- [Tệp nguồn, lớp và phương thức trong chương trình tối thiểu](#program-structure-001)
- [Vai trò của public static void main(String&#91;&#93; args)](#program-structure-002)
- [Quy tắc tên tệp với lớp public](#program-structure-003)
- [Dấu ngoặc nhọn, dấu chấm phẩy và khối lệnh](#program-structure-004)
- [Khai báo khác với câu lệnh thực thi](#program-structure-005)
- [Câu lệnh biểu thức và câu lệnh rỗng](#program-structure-006)
- [Cách chương trình bắt đầu, gọi phương thức và kết thúc](#program-structure-007)
- [Thụt lề để thể hiện cấu trúc và tránh hiểu nhầm phạm vi](#program-structure-008)

## <a id="program-structure-001">Tệp nguồn, lớp và phương thức trong chương trình tối thiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Tệp nguồn, lớp và phương thức trong chương trình Java tối thiểu

> **Java core — language-basics / Cấu trúc chương trình**  
> **Mốc thực hành:** JDK 17 trở lên, sử dụng cú pháp chương trình truyền thống tương thích Java 17.  
> **Sau bài học, bạn có thể:** tạo tệp `.java` đúng tên, phân biệt tệp nguồn với lớp và phương thức, viết `main`, tự biên dịch bằng `javac`, chạy bằng `java`, đọc kết quả và nhận diện những lỗi cấu trúc thường gặp.

## 1. Một chương trình Java tối thiểu có những gì?

Trước khi học các phần phức tạp, hãy quan sát một tệp duy nhất tên **HelloWorld.java**:

~~~java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Xin chào, Java 17!");
    }
}
~~~

Đây là một chương trình **hoàn chỉnh** dùng cú pháp Java 17. Nó có ba tầng dễ nhận ra:

| Thành phần | Trong ví dụ | Vai trò |
| --- | --- | --- |
| **Tệp nguồn (source file)** | `HelloWorld.java` | Văn bản chứa mã nguồn Java để compiler đọc |
| **Lớp (class)** | `public class HelloWorld` | Nhóm khai báo dữ liệu và hành vi có tên; đây là lớp chứa chương trình |
| **Phương thức (method)** | `main(String[] args)` | Nhóm câu lệnh có tên; trong ví dụ là điểm bắt đầu thực thi |
| **Câu lệnh (statement)** | `System.out.println(...);` | Chỉ thị thực hiện một thao tác, ở đây in một dòng |

Java không chạy “tệp văn bản” giống một bản ghi chú. Trong quy trình quen thuộc, **compiler biên dịch** `.java` thành bytecode `.class`; **Java launcher/JVM** nạp lớp có điểm vào và thực thi các câu lệnh trong phương thức `main`.

~~~mermaid
flowchart LR
    S["HelloWorld.java: mã nguồn"] --> C["javac: biên dịch"]
    C --> B["HelloWorld.class: bytecode"]
    B --> J["java: khởi chạy lớp"]
    J --> M["main(): thực thi câu lệnh"]
    M --> O["Console: Xin chào, Java 17!"]
~~~

**Một tệp không đồng nghĩa một lớp trong mọi trường hợp.** Một tệp `.java` có thể khai báo nhiều top-level class với giới hạn quyền truy cập và tên tệp; ví dụ nhập môn này cố ý chỉ có **một `public` class** để người học tập trung vào cấu trúc cơ bản.

## 2. Tạo tệp, biên dịch và chạy bằng JDK 17+

### Bước 1 — Tạo tệp nguồn

Dùng trình soạn thảo văn bản hoặc IDE tạo file **HelloWorld.java**, dán nguyên đoạn mã ở mục 1, lưu dưới dạng UTF-8. Chú ý:

- Tên `HelloWorld.java` khớp đúng chữ hoa/chữ thường với `public class HelloWorld`.
- Tệp không phải `HelloWorld.java.txt` (một số hệ điều hành ẩn phần mở rộng).
- Không thêm ký hiệu Markdown như `~~~java` vào **nội dung file**: chỉ chép mã nằm giữa hai hàng rào code.

### Bước 2 — Xác minh công cụ

Trong terminal, vào thư mục chứa file rồi kiểm tra:

~~~bash
java -version
javac -version
~~~

Bạn cần một **JDK** để có compiler `javac`. Các bản JDK tương thích Java 17 hoặc mới hơn có thể thực hành bài này; nếu cả hai lệnh không được tìm thấy, cần kiểm tra cài đặt JDK và cấu hình đường dẫn trước khi chạy tiếp.

### Bước 3 — Biên dịch

Trong thư mục chứa `HelloWorld.java`:

~~~bash
javac -encoding UTF-8 HelloWorld.java
~~~

Nếu mã hợp lệ, lệnh thường **không in thông báo thành công** và tạo tệp `HelloWorld.class` cạnh mã nguồn (với ví dụ không có `package` và không chỉ định `-d`).

### Bước 4 — Chạy lớp

~~~bash
java HelloWorld
~~~

Đầu ra chính xác:

~~~text
Xin chào, Java 17!
~~~

Sau `java` là **tên lớp** `HelloWorld`, **không** phải `HelloWorld.class`. Tên file `.class` chỉ giúp bạn quan sát sản phẩm compiler; cú pháp của launcher khi chạy class là tên lớp.

### Cách khác: chạy thẳng tệp nguồn

Java launcher từ **JDK 11** hỗ trợ source-file mode:

~~~bash
java HelloWorld.java
~~~

Với ví dụ này, kết quả vẫn là:

~~~text
Xin chào, Java 17!
~~~

Launcher thực hiện công đoạn biên dịch cần thiết rồi chạy chương trình; không cần bạn gọi `javac` riêng. Đừng suy luận rằng JVM trực tiếp thực thi từng dòng mã nguồn. Hai lệnh `java HelloWorld` và `java HelloWorld.java` có **chế độ xử lý khác nhau**: một bên khởi chạy class đã biên dịch, một bên nhận đường dẫn file nguồn.

## 3. Đọc cú pháp từng dòng

Hãy chia mã ví dụ thành những lớp ý nghĩa:

~~~java
public class HelloWorld {                 // Khai báo lớp
    public static void main(String[] args) {  // Khai báo phương thức
        System.out.println("Xin chào, Java 17!"); // Câu lệnh
    }                                       // Hết thân phương thức
}                                           // Hết thân lớp
~~~

### `public class HelloWorld`

- `class`: khai báo một lớp.
- `HelloWorld`: tên lớp, phân biệt chữ hoa/chữ thường.
- `public`: lớp công khai; trong ví dụ top-level public class, tên file nguồn phải là `HelloWorld.java`.
- `{ ... }`: thân lớp. Trong thân có thể khai báo **field**, **constructor**, **method** và những thành phần khác; ví dụ này chỉ cần một method.

### `public static void main(String[] args)`

Ở cú pháp điểm vào thông dụng của **Java 17**:

| Thành phần | Ý nghĩa trong phương thức `main` |
| --- | --- |
| `public` | Launcher có thể truy cập điểm vào công khai |
| `static` | Gọi method qua lớp; không cần tạo đối tượng `HelloWorld` trước |
| `void` | Phương thức không trả về một giá trị |
| `main` | Tên phương thức điểm vào được Java launcher nhận biết |
| `String[] args` | Mảng các đối số dạng chuỗi được truyền từ dòng lệnh |
| `{ ... }` | Các câu lệnh chạy khi `main` được gọi |

Tên biến `args` có thể đổi thành tên khác; quan trọng là **chữ ký/phạm vi phù hợp** với cách launcher Java 17 tìm entry point. Dạng `String... args` cũng tương đương với một tham số mảng khi khai báo phương thức Java, nhưng người mới nên bắt đầu bằng `String[] args`.

**`main` không phải “lệnh đặc biệt đặt ở bất cứ đâu”.** Nó là một phương thức Java phải nằm trong một lớp theo cấu trúc chương trình truyền thống của Java 17. Các JDK mới hơn có thể hỗ trợ dạng chương trình đơn giản hóa khác; đừng nhầm những tính năng đó với quy tắc thực hành Java 17 của bài này.

### `System.out.println("...");`

- `System`: lớp API chuẩn Java.
- `out`: luồng xuất chuẩn để in kết quả.
- `println(...)`: method được gọi; trong ví dụ nó in chuỗi và xuống dòng.
- Dấu `;`: kết thúc câu lệnh.
- Chuỗi Java dùng dấu nháy kép `"..."`; dấu nháy đơn thường dành cho một ký tự `char`.

Trong Java, **chữ hoa/chữ thường có ý nghĩa**: `System` không phải `system`; `println` không phải `printLn`; `HelloWorld` không phải `Helloworld`.

## 4. Lớp là gì, phương thức là gì?

Một **lớp** mô tả một loại đối tượng hoặc gom các khả năng liên quan. Một **phương thức** là một hành vi có tên, có thể nhận tham số, thực hiện công việc và trả kết quả. Không phải mỗi phương thức đều phải in ra màn hình.

Ví dụ hình dung:

~~~java
class Calculator {
    static int add(int left, int right) {
        return left + right;
    }
}
~~~

- `Calculator` là lớp.
- `add` là phương thức.
- `int left` và `int right` là hai **tham số** đầu vào.
- `return left + right;` trả về giá trị kiểu `int`.
- `static` cho phép gọi `Calculator.add(2, 3)` mà không cần tạo đối tượng.

Đây là đoạn **minh họa thành phần**, chưa phải chương trình tự khởi chạy vì nó không khai báo điểm vào `main`. Để biên dịch/chạy thành chương trình hoàn chỉnh, hãy xem ví dụ tiếp theo.

### Phân biệt các thuật ngữ gần nhau

| Thuật ngữ | Ví dụ | Ghi nhớ |
| --- | --- | --- |
| Khai báo method | `static int add(int a, int b) { ... }` | Xác định tên, tham số, kiểu trả về và thân |
| Gọi method | `add(2, 3)` | Yêu cầu thực thi method với các đối số cụ thể |
| Tham số (parameter) | `a`, `b` trong khai báo | Biến cục bộ nhận dữ liệu khi method được gọi |
| Đối số (argument) | `2`, `3` khi gọi | Giá trị truyền vào method |
| Kiểu trả về | `int` hoặc `void` | Có trả giá trị thuộc kiểu ấy hay không |
| Method `static` | `Calculator.add(2, 3)` | Gắn với lớp, không bắt buộc có object |
| Method instance | `lamp.turnOn()` | Gọi thông qua một đối tượng cụ thể |

Đừng nhầm `return` với `System.out.println`: `return` đưa giá trị **về nơi gọi**, còn `println` tạo **đầu ra console**. Bạn có thể tính toán và trả kết quả mà không in gì.

## 5. Ví dụ chạy được: một lớp chứa nhiều phương thức

Tạo file **MethodDemo.java**:

~~~java
public class MethodDemo {

    private static String makeGreeting(String name) {
        if (name == null || name.isBlank()) {
            return "Xin chào, khách!";
        }
        return "Xin chào, " + name.strip() + "!";
    }

    private static int add(int left, int right) {
        return Math.addExact(left, right);
    }

    public static void main(String[] args) {
        String guest = args.length == 0 ? "bạn" : args[0];

        String greeting = makeGreeting(guest);
        int total = add(7, 5);

        System.out.println(greeting);
        System.out.println("7 + 5 = " + total);
        System.out.println("Số đối số: " + args.length);
    }
}
~~~

Biên dịch, chạy không có đối số:

~~~bash
javac -encoding UTF-8 MethodDemo.java
java MethodDemo
~~~

Kết quả:

~~~text
Xin chào, bạn!
7 + 5 = 12
Số đối số: 0
~~~

Chạy với một đối số chứa khoảng trắng (trong Bash hoặc PowerShell):

~~~bash
java MethodDemo "Lan Anh"
~~~

Kết quả:

~~~text
Xin chào, Lan Anh!
7 + 5 = 12
Số đối số: 1
~~~

**Luồng thực thi:**

1. Launcher gọi `main`.
2. `main` xem có đối số dòng lệnh không. Nếu không, dùng chuỗi mặc định `"bạn"`.
3. `main` gọi `makeGreeting(guest)`. Method này kiểm tra tên và **trả một chuỗi**, chứ không in.
4. `main` gọi `add(7, 5)`. Method tính rồi **trả 12**.
5. Chỉ `main` gọi `println` để in ba dòng ra console.

`private` ở hai method phụ nghĩa là chỉ code trong chính lớp `MethodDemo` gọi được chúng trực tiếp. Cả hai đều `static` để `main` (cũng `static`) có thể gọi mà không tạo một `MethodDemo`.

### Điều gì xảy ra với đối số đặc biệt?

| Cách chạy | Kết quả đáng chú ý | Vì sao |
| --- | --- | --- |
| `java MethodDemo` | `Xin chào, bạn!` | `args.length == 0` |
| `java MethodDemo "Lan Anh"` | `Xin chào, Lan Anh!` | Một đối số có dấu cách nhờ quote |
| `java MethodDemo ""` | `Xin chào, khách!` | `args[0]` là chuỗi rỗng; `isBlank()` đúng |
| `java MethodDemo An Bình` | Chào `An`; số đối số là 2 | Shell tách thành hai đối số; ví dụ chỉ dùng `args[0]` |

Bài này chỉ xử lý đối số **đầu tiên** làm tên; đó là hợp đồng của ví dụ, không có nghĩa Java tự bỏ mọi đối số khác. Luôn kiểm tra `args.length` trước khi truy cập `args[0]` để tránh `ArrayIndexOutOfBoundsException`.

### Vì sao dùng `Math.addExact`?

Trong ví dụ, `7 + 5` không tràn số. Tuy nhiên, phép cộng `int` thông thường có thể tràn miền số mà không cảnh báo. `Math.addExact(left, right)` ném `ArithmeticException` khi tràn số. Chọn hàm này nhằm minh họa rằng **hành vi method** không chỉ là phép tính: nó có thể có hợp đồng lỗi. Với chương trình thực, cần kiểm thử ca biên nếu dữ liệu đến từ người dùng.

## 6. Phương thức `static` và phương thức của đối tượng

Ở `MethodDemo`, các method đều `static`. Một lớp cũng có thể tạo đối tượng mang trạng thái riêng và có method **không static**. Tạo file **LampDemo.java**:

~~~java
public class LampDemo {
    private boolean on;

    public void turnOn() {
        on = true;
    }

    public String status() {
        return on ? "BẬT" : "TẮT";
    }

    public static void main(String[] args) {
        LampDemo lamp = new LampDemo();

        System.out.println(lamp.status());
        lamp.turnOn();
        System.out.println(lamp.status());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 LampDemo.java
java LampDemo
~~~

Kết quả:

~~~text
TẮT
BẬT
~~~

- `LampDemo lamp = new LampDemo();` tạo một **đối tượng**.
- `lamp.status()` và `lamp.turnOn()` là lời gọi method **instance** thông qua object `lamp`.
- `on` là **field** của đối tượng, có giá trị mặc định `false` đối với kiểu `boolean` khi tạo object.
- `main` là `static` để launcher khởi chạy; bên trong `main`, ta chủ động tạo object để gọi các method không static.
- `turnOn()` có kiểu trả về `void`: nó thay đổi trạng thái của `lamp`, không trả một giá trị.

**Sai lầm thường gặp:**

~~~java
// Trong một phương thức static của LampDemo, lệnh sau không hợp lệ:
turnOn(); // Không có object nào được chỉ ra để gọi method instance.
~~~

Trong `main` của ví dụ, bạn phải có đối tượng thích hợp rồi gọi `lamp.turnOn()`. Đây là khác biệt cơ bản giữa **hành vi của lớp** và **hành vi trên một đối tượng**, không phải lý do để biến mọi method thành `static`.

## 7. Tệp nguồn có thể chứa bao nhiêu lớp?

Một tệp nguồn có thể có nhiều **top-level class**, nhưng thông thường chỉ được có **tối đa một top-level public class**. Nếu có top-level public class, tên tệp phải khớp tên class ấy.

Ví dụ lưu trong **MainWithHelper.java**:

~~~java
public class MainWithHelper {
    public static void main(String[] args) {
        Helper.sayHello();
    }
}

class Helper {
    static void sayHello() {
        System.out.println("Helper đã chạy!");
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 MainWithHelper.java
java MainWithHelper
~~~

Kết quả:

~~~text
Helper đã chạy!
~~~

Compiler thông thường tạo **`MainWithHelper.class`** và **`Helper.class`**. Hai lớp nằm trong **một tệp nguồn** vì `Helper` không phải top-level public class. Tuy vậy, khi project lớn lên, thường nên tách những lớp có trách nhiệm riêng ra các file phù hợp để dễ tìm, kiểm thử và bảo trì; đây là **quy ước tổ chức có ích**, không phải luật “mỗi tệp Java chỉ được chứa một class”.

Nếu tạo hai **top-level public class** khác tên trong cùng `MainWithHelper.java`, compiler sẽ báo lỗi về yêu cầu tên tệp của public class. Đừng nhầm với **lớp lồng nhau** (nested class), vốn là một trường hợp cú pháp khác.

## 8. Khi có `package`, tên lớp chạy sẽ thay đổi

Các ví dụ trước không khai báo `package` để bước đầu ngắn gọn. Ở một project thật, các lớp thường được đặt trong một package có tên, ví dụ `demo`.

Cây project tối thiểu:

~~~text
project/
└── src/
    └── demo/
        └── PackageHello.java
~~~

Nội dung `src/demo/PackageHello.java`:

~~~java
package demo;

public class PackageHello {
    public static void main(String[] args) {
        System.out.println("Xin chào từ package demo!");
    }
}
~~~

Từ thư mục `project`, biên dịch và chạy:

**Bash (macOS/Linux):**

~~~bash
mkdir -p out
javac -encoding UTF-8 -d out src/demo/PackageHello.java
java -cp out demo.PackageHello
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
javac -encoding UTF-8 -d out src/demo/PackageHello.java
java -cp out demo.PackageHello
~~~

Đầu ra:

~~~text
Xin chào từ package demo!
~~~

Compiler tạo `out/demo/PackageHello.class`. Khi chạy, bạn truyền **tên lớp đầy đủ** `demo.PackageHello` và **gốc classpath** `out`. Không chạy `java PackageHello` ở đây vì class thực sự thuộc package `demo`.

**Đây là ví dụ phụ để giải thích cấu trúc tệp/lớp, không phải hướng dẫn cấu hình project nhiều module.** Bạn không cần Maven hay Gradle để hiểu một chương trình tối thiểu; các build tool đó hữu ích khi project có nhiều file và phụ thuộc hơn.

## 9. Những lỗi thường gặp khi viết chương trình đầu tiên

| Triệu chứng/lỗi | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| `class HelloWorld is public, should be declared in a file named HelloWorld.java` | Tên file không khớp top-level public class | Đặt đúng `HelloWorld.java` |
| `cannot find symbol` với `System`, `String` hoặc tên method | Viết sai hoa/thường hoặc định danh | So lại `System`, `String`, `println` và tên method |
| `';' expected` | Thiếu dấu chấm phẩy sau statement cần có `;` | Kiểm tra dòng gọi `println`/gán biến |
| `reached end of file while parsing` | Thiếu dấu `}` hoặc cú pháp chưa hoàn chỉnh | Đối chiếu dấu ngoặc mở/đóng của class và method |
| `non-static method ... cannot be referenced from a static context` | Gọi instance method trong `main` mà không có object | Tạo object rồi gọi `object.method()` |
| `Main method not found in class ...` | Không có entry point Java 17 phù hợp | Kiểm tra `public static void main(String[] args)` |
| `Could not find or load main class` | Sai tên lớp/package hoặc classpath | Kiểm tra `.class`, tên đầy đủ và `-cp` |
| `ArrayIndexOutOfBoundsException` với `args[0]` | Không truyền đối số mà vẫn đọc phần tử đầu | Kiểm tra `args.length` |
| `java HelloWorld.class` thất bại | Truyền tên file thay vì tên lớp | Dùng `java HelloWorld` khi chạy bytecode |

**Biên dịch và chạy là hai giai đoạn khác nhau.** Nếu `javac` báo lỗi cú pháp, chương trình chưa tạo được bytecode mới hợp lệ. Nếu `javac` thành công nhưng `java` báo không tìm thấy main class, hãy kiểm tra **tên lớp và classpath**, không nhất thiết sửa logic in ra màn hình.

### Ba kiểm tra nhanh

1. **Tên file:** top-level `public class Example` phải nằm trong `Example.java`.
2. **Chữ ký entry point:** với cấu trúc tiêu chuẩn Java 17, dùng `public static void main(String[] args)`.
3. **Lệnh chạy:** không có package thì `java Example`; có `package demo;` và classpath `out` thì `java -cp out demo.Example`.

## 10. Bài tập thực hành

**Bài 1 — Đổi nội dung in:** Trong `HelloWorld.java`, sửa chuỗi thành `"Tôi đang học Java!"` rồi biên dịch và chạy lại. Quan sát vì sao chỉ sửa tệp `.java` mà chạy tệp `.class` cũ **chưa chắc** đã hiển thị nội dung mới, nếu không biên dịch lại.

**Bài 2 — Viết method trả kết quả:** Trong `MethodDemo`, thêm method `private static int square(int number)`, trả về bình phương. Gọi từ `main` với `4`, kỳ vọng in `16`. Thử suy nghĩ về tràn `int` với đầu vào rất lớn.

**Bài 3 — Hiểu tham số:** Chạy `java MethodDemo "Ngọc Lan"` và `java MethodDemo Ngọc Lan`. So sánh `args.length` và chuỗi chào để thấy dấu nháy ở **shell** ảnh hưởng cách truyền đối số thế nào.

**Bài 4 — Instance method:** Thêm method `turnOff()` vào `LampDemo`, gọi theo thứ tự: `turnOn()`, `turnOff()` và in trạng thái sau mỗi lượt. Kết quả nên phản ánh thay đổi trạng thái trên **cùng một object**.

**Bài 5 — Tìm lỗi cấu trúc:** Đoạn sau không tạo được chương trình chạy chuẩn trong Java 17. Hãy chỉ ra lý do và sửa:

~~~java
public class Sample {
    public void main(String[] args) {
        System.out.println("Hello");
    }
}
~~~

*Gợi ý:* Trong cấu trúc đang học, `main` phải là `static` để launcher gọi được mà không cần tạo object; đổi thành `public static void main(String[] args)`.

## 11. Tự kiểm tra kiến thức

Bạn đã nắm bài nếu trả lời được:

1. `HelloWorld.java` khác `HelloWorld.class` như thế nào?
2. Vì sao file chứa `public class HelloWorld` cần tên `HelloWorld.java`?
3. `main` là class hay method? Vì sao `main` trong Java 17 thường có `static`?
4. `return` và `System.out.println` khác nhau thế nào?
5. Method `static` và instance method được gọi theo hai cách nào?
6. Vì sao `java -cp out demo.PackageHello` khác `java PackageHello`?
7. Với input không có đối số, vì sao nên kiểm tra `args.length` trước `args[0]`?

**Ghi nhớ:** **Tệp nguồn** là nơi viết mã; **lớp** tổ chức các thành phần của chương trình; **phương thức** định nghĩa hành vi; **`main`** là điểm khởi chạy truyền thống của chương trình Java 17. Từ một file `.java` và vài dòng `javac`/`java`, bạn đã có thể tạo, chạy và giải thích cấu trúc chương trình Java cơ bản.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-002">Vai trò của public static void main(String&#91;&#93; args)</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `public static void main(String[] args)` trong Java

Khi chạy một ứng dụng Java đơn giản từ terminal, JVM cần biết **chương trình bắt đầu thực thi từ đâu**. Với cách khởi chạy một lớp thông thường trên **JDK 17**, Java launcher tìm một phương thức có chữ ký phù hợp, điển hình là:

~~~java
public static void main(String[] args)
~~~

Phương thức `main` là **điểm vào (entry point)** của chương trình. Những câu lệnh trong phần thân `main` được gọi khi người dùng chạy lớp đó bằng lệnh `java`. Nó không phải toàn bộ ứng dụng: từ `main`, bạn có thể gọi những phương thức và lớp khác để thực hiện công việc.

Bài này tập trung vào chữ ký trên, cách chạy lớp chứa `main`, vai trò của đối số `args` và những lỗi người mới thường gặp. Các ví dụ dùng **JDK 17 trở lên** và cú pháp `main` truyền thống tương thích Java 17.

## 1. Đọc từng phần của chữ ký `main`

| Thành phần | Ý nghĩa | Tại sao cần trong cách chạy lớp trên Java 17? |
| --- | --- | --- |
| `public` | Phương thức có khả năng truy cập công khai | Java launcher cần có thể gọi điểm vào |
| `static` | Phương thức thuộc lớp, không cần tạo đối tượng để gọi | Launcher có thể gọi `main` mà không phải `new` lớp chứa nó |
| `void` | Phương thức không trả về một giá trị Java | Chữ ký entry point truyền thống yêu cầu trả về `void` |
| `main` | Tên phương thức điểm vào | Launcher tìm phương thức có tên này |
| `String[]` | Kiểu **mảng các chuỗi** | Chứa đối số người dùng truyền khi chạy chương trình |
| `args` | Tên tham số của mảng `String[]` | Tên có thể đổi; `args` chỉ là quy ước dễ hiểu |

Từ khóa `public`, `static` và `void` không phải ba từ viết cho đẹp: bỏ hoặc thay sai có thể khiến phương thức **không còn đáp ứng yêu cầu điểm vào**, dù lớp vẫn biên dịch.

Ví dụ, một phương thức thông thường cũng có thể có tên `main` nhưng không nhất thiết được Java launcher dùng làm điểm vào:

~~~java
static void main(int number) {
    System.out.println(number);
}
~~~

Đó là một phương thức có tham số `int`, **không** phải chữ ký `main(String[] args)` mà ví dụ chạy lớp dưới đây yêu cầu.

### Vì sao `main` có `static`?

Một phương thức `static` có thể được gọi theo tên lớp mà không cần tạo instance. Khi khởi động `GreetingMain`, launcher không cần biết cách tạo `new GreetingMain()`; nó dùng phương thức `main` phù hợp để bắt đầu luồng thực thi.

Từ khóa `static` ở đây **không có nghĩa** toàn bộ dữ liệu của ứng dụng phải là biến `static`. Bạn vẫn có thể tạo đối tượng, gọi phương thức instance và tổ chức chương trình thành nhiều lớp từ `main`.

## 2. Một chương trình Java 17+ hoàn chỉnh

Tạo tệp **`GreetingMain.java`** với nội dung sau:

~~~java
public class GreetingMain {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println(
                    "Cach dung: java GreetingMain <ten> <soLan>");
            return;
        }

        String name = args[0];
        if (name.isBlank()) {
            System.out.println("Loi: ten khong duoc trong");
            return;
        }

        final int times;
        try {
            times = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            System.out.println("Loi: soLan phai la so nguyen");
            return;
        }

        if (times < 1 || times > 5) {
            System.out.println("Loi: soLan phai tu 1 den 5");
            return;
        }

        for (int i = 1; i <= times; i++) {
            System.out.println(
                    "Lan " + i + ": Xin chao, " + name + "!");
        }
    }
}
~~~

Ví dụ này cố ý chỉ có **một lớp công khai** chứa một điểm vào.

- `public class GreetingMain`: tên lớp phải khớp với tên tệp `GreetingMain.java`.
- `main`: nhận hai đối số, kiểm tra dữ liệu và quyết định cần làm gì.
- `args.length`: số **chuỗi đối số**, không bao gồm từ `java` hay tên lớp.
- `args[0]`: tên người được chào; `args[1]`: văn bản cần chuyển thành số lần lặp.
- `Integer.parseInt(args[1])`: chuyển chuỗi số nguyên; dữ liệu như `abc` hoặc số vượt miền `int` gây `NumberFormatException`.
- `return;`: kết thúc lời gọi `main` hiện tại, không trả về giá trị vì `main` có kiểu `void`.
- Vòng `for` thực hiện in đúng số lần, sau khi dữ liệu được xác nhận trong khoảng 1–5.

Chữ ký phương thức **đúng** chưa đủ để chương trình đúng nghiệp vụ. Việc kiểm tra số đối số, tên trống và số lần hợp lệ là trách nhiệm của mã trong `main`.

## 3. Biên dịch và chạy bằng terminal

Mở PowerShell trên Windows hoặc Bash trên Linux, đi tới thư mục có `GreetingMain.java`.

### Kiểm tra công cụ

~~~bash
java -version
javac -version
~~~

Dùng JDK 17 trở lên có `java` và `javac`. Tạo thư mục riêng cho bytecode:

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
~~~

**Linux/Bash:**

~~~bash
mkdir -p out
~~~

Sau đó, trên **cả hai hệ điều hành**:

~~~bash
javac -encoding UTF-8 -d out GreetingMain.java
java -cp out GreetingMain Mai 3
~~~

**Kết quả:**

~~~text
Lan 1: Xin chao, Mai!
Lan 2: Xin chao, Mai!
Lan 3: Xin chao, Mai!
~~~

`javac` tạo `out/GreetingMain.class` khi mã hợp lệ; `-d out` yêu cầu output vào thư mục đó. Lệnh `java -cp out GreetingMain Mai 3` dùng Java launcher để tìm lớp `GreetingMain` trong classpath `out` rồi gọi phương thức `main` tương ứng.

Với lệnh trên, launcher truyền vào `main`:

~~~text
args.length == 2
args[0] == "Mai"
args[1] == "3"
~~~

Điều đó có nghĩa **`args[1]` vẫn là `String`** `"3"`, dù khi gõ lệnh trông giống một con số. Chỉ khi gọi `Integer.parseInt`, chương trình mới thu được giá trị `int` là 3.

### Tên chứa dấu cách vẫn là một đối số

~~~bash
java -cp out GreetingMain "Lan Anh" 2
~~~

~~~text
Lan 1: Xin chao, Lan Anh!
Lan 2: Xin chao, Lan Anh!
~~~

PowerShell và Bash đều hiểu dấu nháy kép ở đây để giữ `Lan Anh` thành **một đối số**. Nếu bỏ dấu nháy, shell thường tách thành hai đối số `Lan` và `Anh`, làm `args.length` thay đổi.

## 4. Dòng thực thi đi qua `main` như thế nào?

~~~mermaid
flowchart TD
    A["java -cp out GreetingMain Mai 3"] --> B["Java launcher tìm GreetingMain"]
    B --> C["Tìm main(String[] args) phù hợp"]
    C --> D["Gọi main với 2 chuỗi đối số"]
    D --> E{"Dữ liệu hợp lệ?"}
    E -->|Không| F["In lỗi hoặc hướng dẫn, return"]
    E -->|Có| G["Chạy vòng for, in lời chào"]
    G --> H["Kết thúc lời gọi main"]
~~~

Một ứng dụng lớn hơn vẫn dùng `main` làm nơi **khởi động và kết nối các thành phần**; không nên dồn mọi phép tính và mọi thao tác I/O vào một phương thức rất dài chỉ vì `main` là điểm vào.

`main` thường được gọi lúc bắt đầu luồng chính trong ví dụ chạy lớp, nhưng **không nhất thiết là phương thức duy nhất được thực thi**. Nó có thể gọi các phương thức khác. Nếu chương trình tạo thêm luồng còn hoạt động, việc `main` trả về **không tự bảo đảm JVM dừng ngay lập tức**.

## 5. `args` là gì và không phải là gì?

### `args` chỉ là tên biến tham số

Hai chữ ký sau cùng mô tả tham số `String[]`:

~~~java
public static void main(String[] args)
~~~

~~~java
public static void main(String[] arguments)
~~~

Tên `args` được sử dụng rộng rãi, nhưng **không phải từ khóa**. Nếu đổi tên thành `arguments`, bạn phải đổi các chỗ sử dụng bên trong phương thức.

Java còn chấp nhận cú pháp tham số biến độ dài (*varargs*) cho kiểu chuỗi:

~~~java
public static void main(String... args)
~~~

Ở bên trong, `args` vẫn sử dụng được như một mảng `String[]`. Với người mới học, cách viết `String[] args` thường rõ ràng hơn.

### Giá trị ban đầu của `args`

Khi khởi chạy bình thường qua Java launcher, chương trình nhận mảng chứa những đối số sau tên lớp. Không cung cấp đối số nghĩa là mảng có **độ dài 0**:

~~~bash
java -cp out GreetingMain
~~~

Kết quả từ chương trình ví dụ:

~~~text
Cach dung: java GreetingMain <ten> <soLan>
~~~

**Không đọc `args[0]` trước khi kiểm tra `args.length`.** Nếu mảng không có phần tử, truy cập chỉ số 0 sẽ gây `ArrayIndexOutOfBoundsException` khi chạy.

Một phương thức Java có thể được gọi thủ công trong mã khác bằng `GreetingMain.main(null)`, nhưng đó là lời gọi khác với việc khởi động thông thường. Vì vậy, không nên suy luận rằng mọi lời gọi trực tiếp tới `main` đều tự có mảng hợp lệ.

## 6. Phân biệt lỗi chữ ký điểm vào với lỗi dữ liệu trong `main`

| Vấn đề | Biên dịch `javac`? | Khi chạy `java`? |
| --- | --- | --- |
| `public static void main(String[] args)` | Hợp lệ | Launcher dùng làm điểm vào |
| Chỉ có `public void main(String[] args)` | Lớp vẫn có thể biên dịch | Trên Java 17, không phải entry point phù hợp vì thiếu `static` |
| Chỉ có `private static void main(String[] args)` | Lớp vẫn có thể biên dịch | Trên Java 17, không phải entry point phù hợp vì không `public` |
| Chỉ có `public static int main(String[] args)` | Có thể biên dịch như method thường | Không đúng kiểu trả về `void` của entry point Java 17 |
| Chỉ có `public static void main(int[] args)` | Có thể biên dịch như method thường | Không khớp tham số mảng `String` |
| `args[0]` khi không có đối số | Vẫn có thể biên dịch | Có thể ném `ArrayIndexOutOfBoundsException` |
| `Integer.parseInt("abc")` | Hợp lệ về kiểu | Ném `NumberFormatException` lúc chạy |

**Quan trọng:** `javac` không yêu cầu **mọi lớp** đều phải có `main`. Một lớp thư viện hoặc lớp dữ liệu có thể biên dịch bình thường mà không thể chạy trực tiếp bằng `java TenLop` theo cách thông thường.

Thông báo từ Java launcher có thể khác giữa các bản JDK, nhưng nguyên tắc là: **biên dịch hợp lệ** và **có điểm vào hợp lệ để chạy lớp** là hai kiểm tra khác nhau.

Một chữ ký hợp lệ chỉ định **cách bắt đầu**, không kiểm tra số lượng hay kiểu số học của đối số: các chuỗi đầu vào vẫn cần được phân tích trong ứng dụng.

## 7. Những ca cần thử với chương trình mẫu

| Lệnh sau khi biên dịch | Kết quả cần kiểm chứng |
| --- | --- |
| `java -cp out GreetingMain Mai 1` | In một lời chào |
| `java -cp out GreetingMain Mai 5` | In năm lời chào |
| `java -cp out GreetingMain Mai 0` | `Loi: soLan phai tu 1 den 5` |
| `java -cp out GreetingMain Mai 6` | `Loi: soLan phai tu 1 den 5` |
| `java -cp out GreetingMain Mai abc` | `Loi: soLan phai la so nguyen` |
| `java -cp out GreetingMain Mai 2147483648` | `Loi: soLan phai la so nguyen` vì ngoài miền `int` |
| `java -cp out GreetingMain Mai` | In hướng dẫn vì thiếu đối số |
| `java -cp out GreetingMain "Lan Anh" 2` | Hai lời chào với đúng tên có dấu cách |
| `java -cp out GreetingMain "" 2` | `Loi: ten khong duoc trong` |

Một tình huống khác: nếu truyền `" 2 "` làm chuỗi số lần, `Integer.parseInt` không mặc nhiên loại bỏ khoảng trắng. Khi muốn chấp nhận khoảng trắng, ứng dụng phải có quy tắc xử lý như `trim()` rõ ràng. Đừng sửa mã chỉ để thông qua một ca kiểm thử mà chưa quyết định hành vi đầu vào được mong đợi.

### Tại sao `return;` không giống `System.exit(...)`?

Trong mã ví dụ, `return;` chỉ **kết thúc phương thức `main`**. Với chương trình đơn luồng đơn giản, không còn tác vụ khác nên chương trình thường kết thúc ngay sau đó.

Ngược lại, `System.exit(2)` yêu cầu JVM kết thúc tiến trình với mã trạng thái 2; điều này thường hữu ích cho công cụ CLI hoặc hệ thống tự động hóa khi cần phân biệt thành công và lỗi. Nó có tác động lên **toàn tiến trình**, nên không cần dùng khi bài học chỉ đang trình bày vai trò cơ bản của `main`.

Vì ví dụ hiện tại in lỗi rồi `return;`, nó **không tự đặt mã thoát khác 0**. Đừng giả định cứ in chữ `Loi` thì shell sẽ thấy tiến trình thất bại.

## 8. Bài tập tự luyện

**Bài 1 — Giải thích chữ ký.** Không nhìn bảng, hãy nói một câu cho mỗi thành phần `public`, `static`, `void`, `main`, `String[]` và `args`.

**Bài 2 — Dự đoán kết quả.** Chạy `java -cp out GreetingMain "Minh Chau" 2`. Có bao nhiêu phần tử trong `args`? Hai phần tử chứa gì và có bao nhiêu dòng được in?

*Đáp án:* Hai phần tử, `"Minh Chau"` và `"2"`; hai lời chào.

**Bài 3 — Tìm lỗi điểm vào.** Hãy thử đổi `public static void main(String[] args)` thành `public void main(String[] args)`. Tại sao `javac` có thể không báo lỗi nhưng `java -cp out GreetingMain Mai 2` không chạy theo ý định trên Java 17?

*Đáp án:* Bạn tạo một phương thức instance, không còn phương thức `main` `public static` đúng chữ ký mà launcher Java 17 tìm.

**Bài 4 — Xử lý input.** Sửa mã để chấp nhận số lần từ 1 đến 10, rồi chạy ca đúng ngưỡng 10 và ca sai 11. Xác nhận chỉ thay đổi quy tắc kiểm tra số lần, không làm thay đổi ý nghĩa `main`.

**Bài 5 — Tách công việc khỏi `main`.** Thêm phương thức sau **bên trong lớp `GreetingMain`**:

~~~java
static void greet(String name, int sequence) {
    System.out.println(
            "Lan " + sequence + ": Xin chao, " + name + "!");
}
~~~

Sau đó thay câu `println` trong vòng `for` bằng `greet(name, i);`. Kết quả có thay đổi không? Giải thích vì sao đây là một bước tổ chức mã tốt dù `main` vẫn là điểm vào.

## Checklist ghi nhớ

- [ ] Biết rằng `main` là điểm vào khi Java launcher chạy lớp theo cách truyền thống trên JDK 17.
- [ ] Giải thích được `public`, `static`, `void`, `main`, `String[]`, `args`.
- [ ] Tạo đúng tệp chứa `public class` và biên dịch bằng `javac`.
- [ ] Chạy được `java -cp out GreetingMain Mai 3` và dự đoán được kết quả.
- [ ] Biết đối số CLI ban đầu đều là `String` và tên lớp không nằm trong `args[]`.
- [ ] Kiểm tra `args.length` trước khi truy cập phần tử.
- [ ] Phân biệt lỗi không tìm thấy entry point với lỗi dữ liệu trong phương thức `main`.
- [ ] Hiểu rằng kết thúc `main` không phải lúc nào cũng buộc mọi luồng/JVM dừng tức thì.

**Tóm lại:** `public static void main(String[] args)` là lời khai báo điểm bắt đầu quen thuộc của ứng dụng Java 17. Java launcher sử dụng chữ ký này để gọi mã của bạn; còn chương trình dùng `args` để nhận chuỗi đầu vào. Hiểu ranh giới ấy giúp bạn tạo chương trình chạy được, phân tích lỗi đúng chỗ và tổ chức logic ngoài `main` khi ứng dụng lớn dần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-003">Quy tắc tên tệp với lớp public</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc tên tệp với lớp public trong Java

## 1. Quy tắc cần nhớ

Với quy trình biên dịch tệp nguồn thông thường bằng **`javac`** (JDK 17+), một **lớp top-level được khai báo `public`** phải nằm trong tệp `.java` có **tên cơ sở trùng với tên lớp**, đúng cả chữ hoa/chữ thường.

~~~java
// Tệp Greeting.java
public class Greeting {
    public static void main(String[] args) {
        System.out.println("Xin chao Java");
    }
}
~~~

Tên class là `Greeting`, do đó tên tệp phải là **`Greeting.java`**. Không đặt tên `Hello.java`, `greeting.java` hoặc `Greeting.class`.

Nếu lưu cùng mã vào `Hello.java` rồi chạy:

~~~bash
javac --release 17 Hello.java
~~~

Bạn sẽ gặp lỗi gần giống:

~~~text
Hello.java:2: error: class Greeting is public, should be declared in a file named Greeting.java
public class Greeting {
       ^
1 error
~~~

**Cách sửa:** đổi tên tệp thành `Greeting.java` **hoặc** đổi khai báo `public class Greeting` thành `public class Hello` và cập nhật các chỗ tham chiếu tên lớp.

Quy tắc này liên quan đến **tên tệp nguồn và khai báo top-level public type lúc biên dịch**. Nó khác với lỗi không tìm thấy class lúc chạy và không được sửa bằng cách đổi tên file `.class` thủ công.

## 2. Top-level, public và package-private

**Top-level class** là class khai báo trực tiếp trong đơn vị mã nguồn, không nằm bên trong một class hay interface khác.

~~~java
// Tệp Catalog.java
public class Catalog {   // top-level public class
}

class CatalogItem {       // top-level package-private class
}
~~~

Trong ví dụ này, `Catalog.java` hợp lệ vì có **một** top-level public class tên `Catalog`; class `CatalogItem` không `public`, nên không cần tệp riêng chỉ vì tên nó khác tên source.

| Trường hợp | Hợp lệ khi biên dịch bằng javac? | Lý do |
|---|---|---|
| `public class A` trong `A.java` | Có | Khớp tên |
| `public class A` trong `B.java` | Không | Tên top-level public class khác tên tệp |
| `class A` trong `B.java` | Có | Top-level class không public |
| `public class A` và `class B` trong `A.java` | Có | Chỉ `A` public |
| `public class A` và `public class B` trong `A.java` | Không | Hai top-level public type không thể cùng đáp ứng quy tắc đặt tên |

Bỏ `public` ở mức top-level tạo quyền truy cập **package-private**, tức là các mã phù hợp ở cùng package có thể tham chiếu. Điều đó không tự ngăn class có phương thức `main` hoặc tạo ra file bytecode.

Một class **lồng bên trong** class khác (*nested class*) không phải top-level class, nên không bị quy tắc “một tên tệp riêng” này áp đặt theo cách tương tự.

## 3. Biên dịch và chạy một public class đúng tên

Lưu đoạn ví dụ đầu trong `Greeting.java`, mở terminal tại thư mục chứa tệp. Kiểm tra JDK:

~~~bash
java -version
javac -version
~~~

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 Greeting.java
java -cp . Greeting
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\Greeting.java
java -cp . Greeting
~~~

Đầu ra:

~~~text
Xin chao Java
~~~

Sau khi biên dịch, thư mục thường có:

~~~text
Greeting.java
Greeting.class
~~~

- `javac` nhận **tên tệp nguồn** `Greeting.java`.
- `java` trong **class mode** nhận **tên lớp** `Greeting`, không phải `Greeting.java` hay `Greeting.class`.
- `-cp .` chỉ định thư mục hiện tại là classpath root.
- `--release 17` yêu cầu trình biên dịch tạo mã tương thích với Java 17 trong khả năng JDK đang dùng; cần compiler hỗ trợ release đó.

Hãy thử đặt nội dung `Greeting.java` vào `Welcome.java` nhưng giữ `public class Greeting`. Compiler sẽ từ chối. Nếu thay khai báo thành `public class Welcome`, bạn có thể dùng:

~~~bash
javac --release 17 Welcome.java
java -cp . Welcome
~~~

và nhận cùng dòng đầu ra. **Tên class và tên tệp phải được thay đồng bộ.**

## 4. Tệp không có public class: tên nguồn không nhất thiết là tên class chạy

Tạo tệp **`Examples.java`**:

~~~java
class Printer {
    static void show(String text) {
        System.out.println(text);
    }
}

class Runner {
    public static void main(String[] args) {
        Printer.show("Da chay Runner");
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 Examples.java
java -cp . Runner
~~~

Kết quả:

~~~text
Da chay Runner
~~~

Thông thường `javac` tạo ra **`Printer.class`** và **`Runner.class`**, dù source tên **`Examples.java`**. Không có class `Examples` được khai báo, nên `java Examples` không phải lệnh chạy đúng.

Kết luận từ ví dụ:

1. Một source có thể chứa nhiều top-level class không `public`.
2. Một source có thể tạo ra nhiều file `.class`.
3. Phương thức `main` có thể nằm trong class top-level không public; class ấy vẫn có thể được chọn để launch trong Java 17.
4. Dù quy tắc cho phép tên tệp khác tên class package-private, dự án thường dễ bảo trì hơn nếu tổ chức file theo tên class chính.

## 5. Không viết hai top-level public class trong cùng một tệp

Ví dụ **không hợp lệ** trong `Alpha.java`:

~~~java
public class Alpha {
}

public class Beta {
}
~~~

`javac Alpha.java` sẽ báo lỗi vì `public class Beta` yêu cầu tệp **`Beta.java`**.

Hướng sửa chuẩn là tách thành hai tệp:

**`Alpha.java`:**

~~~java
public class Alpha {
    public static void main(String[] args) {
        Beta.hello();
    }
}
~~~

**`Beta.java`:**

~~~java
public class Beta {
    public static void hello() {
        System.out.println("Hello from Beta");
    }
}
~~~

Từ thư mục chứa hai tệp:

~~~bash
javac --release 17 Alpha.java Beta.java
java -cp . Alpha
~~~

Đầu ra:

~~~text
Hello from Beta
~~~

Không phải class nào cũng cần `main`: `Alpha` làm điểm vào, `Beta` là class cộng tác. Quy tắc tên file vẫn áp dụng cho cả hai vì cả hai đều là **top-level public class**.

## 6. Có package thì tên tệp vẫn khớp tên class cơ sở

Tạo dự án:

~~~text
project/
└── src/
    └── demo/
        └── Launcher.java
~~~

**`src/demo/Launcher.java`:**

~~~java
package demo;

public class Launcher {
    public static void main(String[] args) {
        System.out.println("Package demo OK");
    }
}
~~~

Ở đây:

- Tên tệp nguồn: **`Launcher.java`**.
- Tên lớp đơn giản: **`Launcher`**.
- Tên lớp đầy đủ: **`demo.Launcher`**.
- `demo` là tên package; không đặt tên tệp là `demo.Launcher.java`.

Từ thư mục `project`:

**Bash:**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out src/demo/Launcher.java
java -cp out demo.Launcher
~~~

**PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -d out .\src\demo\Launcher.java
java -cp out demo.Launcher
~~~

Đầu ra:

~~~text
Package demo OK
~~~

Sau biên dịch, file bytecode nằm ở **`out/demo/Launcher.class`**. Lệnh chạy dùng `demo.Launcher`; classpath phải trỏ tới **`out`**, không phải `out/demo`.

Cấu trúc thư mục source phản ánh package là quy ước tốt và thuận tiện cho build tools. Nhưng **quy tắc tên cơ sở của file public class** vẫn là `Launcher.java`: package không được ghép thêm vào tên file.

## 7. Public nested class không cần file riêng

Tạo **`Outer.java`**:

~~~java
public class Outer {
    public static class Inner {
        public static String greeting() {
            return "Inner";
        }
    }

    public static void main(String[] args) {
        System.out.println(Inner.greeting());
    }
}
~~~

Chạy:

~~~bash
javac --release 17 Outer.java
java -cp . Outer
~~~

Kết quả:

~~~text
Inner
~~~

`Outer` là top-level public class nên tệp có tên `Outer.java`. `Inner` là **nested public class** nên không cần tệp `Inner.java`. Compiler thường tạo các class file `Outer.class` và `Outer$Inner.class`.

Nếu đưa `public class Inner` ra **ngoài** thân của `Outer`, nó trở thành top-level class; khi ấy hai public class cần hai tệp riêng.

## 8. Quy tắc cũng áp dụng cho các top-level public type khác

Với Java 17, không chỉ `class` mới bị yêu cầu khớp tên:

| Khai báo top-level | Tên tệp |
|---|---|
| `public class Account` | `Account.java` |
| `public interface Payment` | `Payment.java` |
| `public enum Status` | `Status.java` |
| `public record Point(int x, int y)` | `Point.java` |
| `public @interface Tracked` | `Tracked.java` |

Ví dụ **`Point.java`**:

~~~java
public record Point(int x, int y) {
}
~~~

Chạy `javac --release 17 Point.java` để biên dịch. Nếu giữ nguyên `public record Point` mà đổi tên source thành `Coordinate.java`, lỗi đặt tên sẽ xuất hiện.

Không suy ra mọi interface, enum hay record phải là `public`. Một top-level type thiếu modifier `public` có thể dùng quyền truy cập package-private như ví dụ class thông thường.

## 9. Phân biệt lỗi compile và lỗi launch

| Triệu chứng | Giai đoạn | Điều cần sửa |
|---|---|---|
| `class X is public, should be declared in a file named X.java` | `javac` | Đổi tên source hoặc tên top-level public type |
| Có hai top-level public type trong một source | `javac` | Tách thành hai source files |
| `Could not find or load main class` | `java` tìm class | Kiểm tra tên đầy đủ, classpath root và file bytecode |
| `Main method not found` | `java` tìm entry point | Bổ sung main hợp lệ vào class định chạy |
| Chạy ra phiên bản kết quả cũ sau khi sửa source | Runtime | Lưu source, biên dịch lại và kiểm tra đúng classpath |
| `UnsupportedClassVersionError` | JVM nạp bytecode | Kiểm tra runtime và mức release build; không phải lỗi đặt tên source |

**Ghi nhớ:** lỗi đặt tên tệp xảy ra **trước khi chạy**, trong bước biên dịch của `javac`. Đổi classpath hoặc thêm `-cp` không giải quyết lỗi này.

Ngược lại, class public có tên đúng, compile tốt vẫn có thể không launch được nếu package/classpath sai hoặc không có `main`.

## 10. Các trường hợp dễ nhầm

**Chữ hoa/chữ thường.** `public class Order` yêu cầu `Order.java`. Trên các hệ thống phân biệt hoa/thường, `order.java` có thể là file khác. Luôn dùng đúng ký tự để tránh lỗi khi chuyển dự án sang Linux.

**Tên file và tên package là hai phần riêng.** `public class App` trong `package demo;` vẫn ở `App.java`; khi chạy class mode dùng tên `demo.App`.

**Class có main nhưng không public.** Điều này không buộc source file phải mang tên class. Ví dụ `Runner` trong `Examples.java` vẫn được chạy nếu có `main` phù hợp và classpath đúng.

**Public class không có main.** Biên dịch vẫn hợp lệ; một class làm thư viện không nhất thiết có entry point riêng.

**Một source có nhiều bytecode file.** Top-level khác nhau hoặc nested class đều có thể khiến `javac` tạo nhiều `.class`. Đừng tự đổi tên output bytecode để sửa lỗi tên public class.

**`java File.java` khác `java ClassName`.** Java 11+ hỗ trợ source-file launching với quy tắc riêng. Trong bài này, để kiểm chứng tên file của top-level public type, hãy dùng `javac File.java` trước, sau đó `java ClassName`. Đừng lấy hành vi của source-file mode làm bằng chứng phủ định quy tắc biên dịch tệp nguồn bằng `javac`.

## 11. Bài tập có đáp án

### Bài A — chọn tên tệp

~~~java
public class PaymentService {
}
~~~

**Đáp án:** đặt trong `PaymentService.java`, đúng hoa/thường. Không dùng `paymentservice.java`.

### Bài B — một public và một package-private class

Tệp **`Demo.java`**:

~~~java
class Helper {
    static int twice(int x) { return x * 2; }
}

public class Demo {
    public static void main(String[] args) {
        System.out.println(Helper.twice(6));
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 Demo.java
java -cp . Demo
~~~

**Đáp án:** hợp lệ; in **`12`**. `Demo` là top-level public class khớp tên tệp, `Helper` không public nên được giữ chung file.

### Bài C — phân biệt hai vấn đề

Tệp **`Hello.java`** chứa:

~~~java
package demo;
public class Welcome {
    public static void main(String[] args) {
        System.out.println("Hi");
    }
}
~~~

**Đáp án:** `javac Hello.java` báo lỗi đặt tên vì phải là **`Welcome.java`**. Sau khi sửa tên nguồn, compile với `-d out`, lệnh chạy cần dùng **`java -cp out demo.Welcome`** vì class thuộc package `demo`.

### Bài D — hai top-level public class

Tệp `A.java` có cả `public class A` và `public class B`. Hãy nêu cách sửa.

**Đáp án:** tách ra `A.java` và `B.java`, mỗi file giữ public class tương ứng. Nếu `B` chỉ cần sử dụng nội bộ package và thiết kế cho phép, một lựa chọn khác là bỏ `public` khỏi `B`; không tự đổi API public khi còn mã phụ thuộc.

### Bài E — nested class

Có `public class Outer` chứa `public static class Inner`. Có cần tạo `Inner.java` không?

**Đáp án:** **không**. `Inner` không phải top-level type; `Outer.java` là tệp cần khớp tên của `Outer`.

## 12. Checklist ghi nhớ

- [ ] Giải thích được `public class X` ở top-level cần tệp `X.java`.
- [ ] Phân biệt top-level class, package-private class và nested public class.
- [ ] Biết khi nào có thể có nhiều class trong một tệp, khi nào phải tách.
- [ ] Không nhầm tên tệp `.java` với tên class dùng ở lệnh `java`.
- [ ] Chạy được ví dụ `Greeting.java` và `demo.Launcher` trên JDK 17+.
- [ ] Nắm quy tắc với top-level `public interface`, `public enum`, `public record`, `public @interface`.
- [ ] Biết đọc lỗi `should be declared in a file named...` và sửa đúng nguyên nhân.
- [ ] Phân biệt lỗi tên source khi compile với lỗi classpath/main lúc chạy.

**Kết luận:** Quy tắc trung tâm khi biên dịch bằng `javac` là **tên tệp nguồn `.java` phải khớp tên top-level type `public` khai báo bên trong**. Những class top-level không public và class lồng nhau không phải áp dụng theo cách này. Khi chạy class đã biên dịch, `java` nhận tên class (kèm package nếu có), nên cần phân biệt tên source, tên class và vị trí bytecode.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-004">Dấu ngoặc nhọn, dấu chấm phẩy và khối lệnh</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu ngoặc nhọn, dấu chấm phẩy và khối lệnh trong Java

> **Java core — language-basics / Cấu trúc chương trình**  
> **Yêu cầu thực hành:** JDK 17 trở lên; ví dụ sử dụng cú pháp hợp lệ trong Java 17.  
> **Mục tiêu:** Phân biệt chính xác `{ }` (dấu ngoặc nhọn), `;` (dấu chấm phẩy) và *block* (khối lệnh); biết nơi phải có hoặc không nên đặt `;`; hiểu phạm vi biến và luồng thực thi khi dùng `if`, `for`, `while`, `do...while`. Tự biên dịch, chạy và dự đoán đầu ra của một chương trình Java tối thiểu.

## 1. Ba ý niệm nền tảng

Trong Java, **cú pháp** quyết định đoạn mã có tạo thành câu lệnh hợp lệ hay không; **cấu trúc khối** quyết định nhóm câu lệnh nào thuộc về một nhánh hoặc một vòng lặp.

| Ký hiệu/khái niệm | Vai trò chính | Ví dụ | Điều không có nghĩa |
| --- | --- | --- | --- |
| `{` và `}` | Đánh dấu giới hạn của thân lớp, thân phương thức, khối câu lệnh; cũng có các cách dùng khác như khởi tạo mảng | `if (ok) { run(); }` | Không phải mọi cặp `{ }` đều là một block statement |
| `;` | Kết thúc nhiều loại statement/declaration hoặc là dấu phân tách trong header `for` | `int age = 20;` | Không có nghĩa mọi dòng Java đều phải kết thúc bằng `;` |
| **Block** (khối lệnh) | Một statement chứa **không hoặc nhiều** statement/khai báo cục bộ nằm giữa `{ }` | `{ int x = 1; print(x); }` | Xuống dòng hoặc thụt lề không tự tạo block |

Cần nhớ hai quy tắc độc lập:

- Dấu `;` thường **kết thúc một câu lệnh**, còn `{ }` tạo ranh giới cấu trúc cho một nhóm câu lệnh.
- Trong Java, **thụt lề giúp đọc code**, nhưng dấu ngoặc nhọn và ngữ pháp — không phải số khoảng trắng — quyết định nhánh `if` hoặc vòng lặp chứa những gì.

~~~mermaid
flowchart TD
    A["Câu lệnh trong main"] --> B["for (...) { ... }"]
    B --> C["if (điều kiện) { ... }"]
    C --> D["total += number;"]
    B --> E["Sau vòng for"]
    E --> F["System.out.println(...);"]
~~~

## 2. Chương trình Java 17+ tối thiểu có đủ ba thành phần

Tạo file **BlockSyntaxDemo.java**; tên tệp trùng với `public class BlockSyntaxDemo`:

~~~java
public final class BlockSyntaxDemo {

    private static int sumEvenUpTo(int limit) {
        int total = 0;

        for (int number = 1; number <= limit; number++) {
            if (number % 2 == 0) {
                total += number;
            }
        }

        return total;
    }

    public static void main(String[] args) {
        int limit = 6;
        int result = sumEvenUpTo(limit);

        System.out.println("Tong so chan tu 1 den " + limit + ": " + result);

        if (result > 0) {
            System.out.println("Da tim thay so chan");
        } else {
            System.out.println("Khong co so chan");
        }
    }
}
~~~

**Chạy trong terminal ở thư mục chứa tệp:**

~~~bash
java -version
javac -version
javac -encoding UTF-8 BlockSyntaxDemo.java
java BlockSyntaxDemo
~~~

Hai lệnh đầu giúp xác minh công cụ, không phải đầu ra của chương trình. Nếu biên dịch thành công, `javac` thường không in gì và tạo `BlockSyntaxDemo.class`. Lệnh cuối in **đúng hai dòng**:

~~~text
Tong so chan tu 1 den 6: 12
Da tim thay so chan
~~~

Vì các số chẵn trong khoảng từ 1 đến 6 là 2, 4, 6 nên tổng là 12.

### Quan sát từng kiểu dấu trong ví dụ

| Đoạn mã | Tại sao viết như vậy? |
| --- | --- |
| `public final class BlockSyntaxDemo { ... }` | Dấu ngoặc nhọn bao quanh **thân lớp**; không có `;` bắt buộc sau dấu `}` cuối của khai báo lớp |
| `private static int sumEvenUpTo(int limit) { ... }` | Dấu ngoặc nhọn bao quanh **thân phương thức** |
| `int total = 0;` | Một câu lệnh khai báo biến cục bộ kết thúc bằng `;` |
| `for (...; ...; ...) { ... }` | Hai dấu `;` trong header phân tách ba phần của vòng `for` truyền thống |
| `if (number % 2 == 0) { ... }` | Block thuộc nhánh `if`; không thêm `;` giữa `)` và `{` |
| `total += number;` | Một expression statement thay đổi biến, kết thúc bằng `;` |
| `return total;` | Câu lệnh trả kết quả, kết thúc bằng `;` |
| `System.out.println(...);` | Lời gọi phương thức dùng như statement, kết thúc bằng `;` |

**Lưu ý về tên:** Dùng `java BlockSyntaxDemo` để khởi chạy class đã biên dịch, không dùng `java BlockSyntaxDemo.class`. Trong file nguồn không có khai báo `package`, nên tên class chạy ở ví dụ này chỉ là `BlockSyntaxDemo`.

## 3. `{ }` tạo khối lệnh như thế nào?

Một block statement có hình dạng:

~~~java
{
    // Có thể là 0, 1 hoặc nhiều câu lệnh.
    int first = 2;
    int second = 3;
    System.out.println(first + second);
}
~~~

Block trên **hợp lệ khi nằm trong thân một phương thức hoặc vị trí cho phép block statement**, nhưng đoạn riêng lẻ này **không phải tệp Java hoàn chỉnh** để gọi `java` trực tiếp.

Trong một phương thức, có thể lồng nhiều block:

~~~java
int outer = 10;
{
    int inner = 5;
    System.out.println(outer + inner); // 15
}
System.out.println(outer);             // 10
// System.out.println(inner);          // Sai: inner đã ra khỏi phạm vi.
~~~

**Phạm vi (scope):** biến cục bộ `inner` chỉ dùng được trong block khai báo nó, kể từ chỗ khai báo trở đi. Biến `outer` được khai báo bên ngoài nên block lồng bên trong có thể tham chiếu tới nó. Khi kết thúc block, tên biến cục bộ chỉ tồn tại trong block đó không còn truy cập được.

Đây cũng là điểm khác biệt với **field** của lớp:

~~~java
class Counter {
    private int total;  // Field của mỗi đối tượng Counter.

    void addOne() {
        int delta = 1;  // Biến cục bộ của phương thức.
        total += delta;
    }
}
~~~

`total` là field của object; `delta` là biến cục bộ chỉ có phạm vi trong phương thức. Không dùng thời điểm kết thúc một block để suy luận rằng object chứa field chắc chắn đã bị thu gom bộ nhớ; đó là hai khái niệm khác nhau.

### Ranh giới khai báo và che khuất tên

Trong các phạm vi cục bộ chồng lấp nhau, Java không cho khai báo lại một **biến cục bộ trùng tên** như cách làm bên dưới:

~~~java
void example() {
    int value = 1;
    {
        // int value = 2; // Lỗi biên dịch: trùng tên biến cục bộ còn trong phạm vi.
        System.out.println(value);
    }
}
~~~

Ngược lại, có thể có tên field và biến cục bộ trùng nhau; khi đó cú pháp như `this.value` giúp chỉ rõ field của đối tượng. Chỉ nên giới thiệu quy tắc này để tránh suy luận rằng “cứ thêm `{ }` là có thể tái sử dụng mọi tên”.

## 4. Khi nào cần dấu `;`?

Có nhiều statement kết thúc bằng dấu chấm phẩy, nhưng **không phải tất cả**. Bảng dưới tóm tắt những dạng thường gặp trong Java 17:

| Dạng | Ví dụ | Có `;`? |
| --- | --- | --- |
| Khai báo biến cục bộ | `int count = 0;` | Có |
| Gán và gọi method | `count++;`, `System.out.println(count);` | Có |
| Trả về | `return count;` hoặc `return;` trong method `void` | Có |
| Thoát/bỏ qua vòng lặp | `break;`, `continue;` | Có |
| Khai báo `package` và `import` | `package demo;`, `import java.util.List;` | Có |
| `if` có block | `if (ready) { work(); }` | **Không** đặt `;` sau header `if (...)` |
| `for` truyền thống | `for (int i = 0; i < 3; i++) { ... }` | Có **hai** `;` bên trong header |
| `while` có block | `while (ready) { work(); }` | Không có `;` giữa `)` và `{` |
| `do...while` | `do { work(); } while (ready);` | Có `;` ở cuối |
| Khai báo phương thức có thân | `void work() { ... }` | Không bắt buộc `;` sau `}` |
| Khai báo lớp có thân | `class Example { ... }` | Không cần `;` sau `}` |

**Chính xác hơn:** dấu `;` là một token ngữ pháp, không chỉ đơn giản “chốt dòng code”. Java cho phép nhiều statement cùng một dòng hoặc một statement trải qua nhiều dòng:

~~~java
int a = 1; int b = 2;
System.out.println(
        a + b
); // Kết thúc statement ở đây, không phải ở cuối dòng đầu.
~~~

Hai dạng sau không tương đương:

~~~java
int amount = 10;  // Statement hoàn chỉnh.
int amount = 10   // Nếu đây là một statement độc lập: thiếu dấu ;, lỗi biên dịch.
~~~

Dòng thứ hai chỉ là **mảnh minh họa cú pháp sai**, không phải code để đặt chung với dòng đầu vào một chương trình chạy được.


## 5. Bẫy cú pháp: dấu `;` trở thành thân của `if`

Một dấu `;` đứng độc lập là **empty statement**. Khi đặt ngay sau `if (condition)`, nó chính là toàn bộ thân của nhánh, còn block phía sau chạy độc lập:

~~~java
boolean ready = false;
if (ready); // Thân if rỗng.
{
    System.out.println("Khoi nay chay doc lap");
}
~~~

Dù `ready` là `false`, đoạn mã trên (nếu đặt trong một phương thức) vẫn in `Khoi nay chay doc lap`. Muốn chỉ in khi điều kiện đúng, hãy bỏ dấu `;` sau điều kiện và viết `if (ready) { ... }`.

Đây là ví dụ ngắn về tác động của token `;` lên cấu trúc khối. Bài **Câu lệnh biểu thức và câu lệnh rỗng** giải thích sâu hơn khi nào `;` là một statement riêng và hành vi của nó trong vòng lặp. Với các nhánh `if` lồng nhau thiếu ngoặc, `else` ghép theo cú pháp với `if` gần nhất có thể nhận nó, bất kể thụt lề.

## 6. Khối lệnh trong `for`, `while` và `do...while`

### 6.1. `for` truyền thống có hai dấu phân tách `;`

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

Kết quả:

~~~text
0
1
2
~~~

Ba phần của header là:

1. `int i = 0` — khởi tạo, chạy một lần trước vòng.
2. `i < 3` — điều kiện kiểm tra trước mỗi lượt lặp.
3. `i++` — cập nhật sau mỗi lượt thực thi thân vòng.

Hai dấu `;` ở giữa là **bắt buộc đối với cấu trúc này**, ngay cả khi một số phần bỏ trống. Ví dụ `for (;;) { break; }` hợp lệ: header có hai dấu phân tách nhưng ba phần đều rỗng, vòng dừng nhờ `break;`.

### 6.2. Enhanced `for` không dùng hai dấu `;`

~~~java
int[] values = {2, 4, 6};
for (int value : values) {
    System.out.println(value);
}
~~~

Dấu `:` là cú pháp của enhanced for; đừng nhầm với `for (init; condition; update)`. Mảng `values` có khởi tạo bằng dấu ngoặc nhọn nhưng `{2, 4, 6}` ở đây là **array initializer**, không phải block statement.

### 6.3. `while` không có `;` sau điều kiện

~~~java
int remaining = 2;
while (remaining > 0) {
    System.out.println(remaining);
    remaining--;
}
~~~

Đầu ra là `2` rồi `1`. Dấu `;` xuất hiện ở **hai statement bên trong** block, không ở giữa `while (...)` và block. Nếu viết `while (remaining > 0);` với `remaining` không thay đổi ở header, bạn có thể tạo vòng lặp rỗng không kết thúc.

### 6.4. `do...while` cần dấu `;` cuối

~~~java
int remaining = 2;
do {
    System.out.println(remaining);
    remaining--;
} while (remaining > 0);
~~~

Dòng đóng `} while (...);` có dấu chấm phẩy **bắt buộc**. Điểm khác với `while` là thân `do` thực thi ít nhất một lần trước khi kiểm tra điều kiện.

## 7. Block quyết định cả phạm vi biến và tính hợp lệ của code

### Biến chỉ tồn tại trong nhánh khai báo

Đoạn sau minh họa lỗi phạm vi:

~~~java
if (args.length > 0) {
    String first = args[0];
    System.out.println(first);
}
// System.out.println(first); // Lỗi: first chỉ được khai báo trong block if.
~~~

Nó dùng biến `args` có sẵn **bên trong `main(String[] args)`**. Việc bình thường hóa khoảng trắng hay thêm dấu `;` **không** giải quyết lỗi tên biến nằm ngoài phạm vi.

### Khác biệt giữa “đã khai báo” và “đã gán giá trị”

Một biến cục bộ phải **được gán chắc chắn** (*definitely assigned*) trước khi đọc giá trị:

~~~java
int result;
if (args.length > 0) {
    result = args.length;
} else {
    result = 0;
}
System.out.println(result);  // Hợp lệ: mọi nhánh đều gán result.
~~~

Nếu bỏ nhánh `else`, compiler có thể báo `variable result might not have been initialized`. Java **không** tự khởi tạo biến cục bộ thành 0 chỉ vì kiểu là `int`; quy tắc mặc định giá trị của field khác với quy tắc definite assignment cho biến cục bộ.

Đây là ví dụ tốt để thấy `{ }` không chỉ “để code đẹp”: chúng thể hiện nhánh kiểm tra, phạm vi và đường đi điều khiển mà compiler phân tích.

## 8. Không phải mọi `{ }` đều là block statement

Dấu ngoặc nhọn cũng xuất hiện trong các cấu trúc Java 17 khác:

### 8.1. Khởi tạo mảng

~~~java
int[] scores = {8, 9, 10};
System.out.println(scores.length); // 3
~~~

`{8, 9, 10}` là **array initializer**. Dấu `;` cuối là dấu kết thúc **khai báo biến**, không phải dấu bắt buộc sau mọi `}`.

### 8.2. Lambda có thân là block

~~~java
Runnable task = () -> {
    System.out.println("Lam viec");
};
task.run();
~~~

Lời gọi `println` bên trong kết thúc bằng `;`; dấu `;` sau `}` kết thúc **statement gán lambda cho biến `task`**. Không suy ra “mọi block `{ }` đều cần `;` ở cuối”.

### 8.3. `switch` expression trong Java 17

~~~java
int day = 2;
String label = switch (day) {
    case 1 -> "Mon";
    case 2 -> "Tue";
    default -> "Other";
};
System.out.println(label);
~~~

Kết quả: `Tue`. Ở đây dấu `;` sau `}` kết thúc **khai báo biến `label`** dùng một biểu thức `switch`. Khi một nhánh `case` có nhiều statement, nó có thể dùng block và `yield` để trả giá trị của switch expression:

~~~java
int n = 4;
String category = switch (n) {
    case 4 -> {
        System.out.println("Dang xet so 4");
        yield "Chan";
    }
    default -> "Khac";
};
~~~

Đây là cú pháp hỗ trợ trong Java 17; `yield "Chan";` dùng để tạo giá trị cho switch expression, không đồng nghĩa với `return` thoát khỏi phương thức.

**Tóm lại:** cần xác định dấu `{ }` đang đóng **khối lệnh, thân khai báo hay initializer** trước khi quyết định đặt dấu `;`.

## 9. Lỗi cú pháp: xem lỗi theo ngữ cảnh thay vì đoán

| Thông báo/triệu chứng | Nguyên nhân khả dĩ | Cách sửa phù hợp |
| --- | --- | --- |
| `';' expected` | Thiếu dấu `;` sau khai báo biến hoặc expression statement | Xác định statement cần kết thúc ở đâu |
| `reached end of file while parsing` | Thiếu `}` hoặc một phần cú pháp chưa đóng | Đối chiếu các cặp `{ }`, dấu `( )` |
| `'else' without 'if'` | Có statement hoặc `;` làm thay đổi cấu trúc nhánh | Kiểm tra dấu `;` sau `if` và các block |
| `variable ... is already defined` | Khai báo lại biến cục bộ trùng tên trong phạm vi chồng lấp | Đổi tên hoặc sửa phạm vi đúng mục đích |
| `cannot find symbol` với biến trong block trước | Biến nằm ngoài phạm vi nhìn thấy | Khai báo ở phạm vi phù hợp, truyền qua tham số hoặc trả kết quả |
| `variable ... might not have been initialized` | Có đường đi tới chỗ đọc nhưng chưa chắc chắn gán giá trị | Bảo đảm gán trước khi dùng trên mọi nhánh cần thiết |
| Vòng lặp `while` không dừng | Vô tình dùng `while (condition);` với thân rỗng | Bỏ dấu `;` sai và kiểm tra điều kiện cập nhật |
| `if` luôn “in” dù điều kiện false | Có `;` sau `if (...)`, block sau đó độc lập | Ghép đúng `if (condition) { ... }` |

**Điểm quan trọng:** thông báo compiler có thể chỉ tới một dòng **sau** chỗ đặt sai dấu `{`, `}` hoặc `;`, vì trình phân tích cú pháp chỉ phát hiện mâu thuẫn ở vị trí tiếp theo. Khi thấy lỗi, đọc cả vài dòng trước đó.

## 10. Quy tắc trình bày code giúp ngăn lỗi

- Mỗi cặp `{`/`}` nên được thụt lề nhất quán; đóng ngoặc đúng cấp khối.
- Ưu tiên dùng `{ }` cho thân `if`/`else`/`for`/`while` dù ngữ pháp Java có thể cho một statement không cần ngoặc. Điều này giảm rủi ro khi bổ sung câu lệnh.
- Đừng thêm `;` ngay sau phần điều kiện `if (...)`, `for (...)` hoặc `while (...)` nếu bạn định chạy một block.
- Với `do { ... } while (...);`, đừng quên dấu `;` cuối.
- Không chỉ đếm dấu ngoặc trên **văn bản**: ký tự `{`, `}`, `;` xuất hiện bên trong **chuỗi** hoặc **comment** không tự biến thành cú pháp điều khiển.

Ví dụ:

~~~java
String text = "{ ; }"; // Các ký tự bên trong chuỗi không tạo block.
System.out.println(text); // In đúng: { ; }
~~~

Đừng cố sửa bằng cách xóa mọi dấu chấm phẩy sau dấu `}`, vì trường hợp khai báo biến với mảng, lambda hoặc switch expression **cần** dấu đó để kết thúc statement.

## 11. Bài tập tự luyện

**Bài 1 — Đoán đầu ra của chương trình chính.** Với `limit = 6`, những lần nào `total += number;` thực sự chạy? Thử đổi `limit` thành 5 và dự đoán tổng; sau đó biên dịch lại để kiểm tra.

**Bài 2 — Tìm dấu sai.** Trong phương thức `main`, đoạn sau in mấy dòng và vì sao?

~~~java
boolean pass = false;
if (pass);
{
    System.out.println("PASS");
}
~~~

*Định hướng:* Một dòng `PASS`. `if (pass)` đang điều khiển empty statement; block riêng vẫn chạy.

**Bài 3 — Tính phạm vi.** Đoạn sau có biên dịch được không? Nếu không, sửa để in được giá trị:

~~~java
{
    int count = 3;
}
System.out.println(count);
~~~

*Định hướng:* Không. `count` được khai báo trong block đã kết thúc; có thể khai báo `count` ở phạm vi bên ngoài nếu cần sử dụng sau đó.

**Bài 4 — Điền dấu phân tách.** Viết vòng `for` in 1, 2, 3. Chỉ ra hai dấu `;` trong header và dấu `;` kết thúc lời gọi `println` bên trong.

**Bài 5 — So sánh hai loại `while`.** Viết một vòng `while` và một vòng `do...while` với điều kiện ban đầu `false`, quan sát số lần thực thi thân vòng và vị trí dấu chấm phẩy.

**Bài 6 — Nhận diện kiểu `{ }`.** Trong `int[] a = {1, 2};`, `if (ok) { work(); }` và `Runnable r = () -> { work(); };`, dấu ngoặc nhọn thuộc loại nào? Dấu `;` ngoài cùng đang kết thúc cấu trúc nào?

## 12. Tự kiểm tra cuối bài

Bạn hiểu chủ đề nếu có thể giải thích chính xác:

1. **Block** bắt đầu và kết thúc ở đâu, vì sao thụt lề không quyết định cấu trúc.
2. Dấu `;` nào kết thúc statement, dấu nào phân tách ba phần của `for`.
3. Tại sao `if (condition); { ... }` nguy hiểm dù thường không phải lỗi biên dịch.
4. Tại sao `do...while` cần `;` cuối nhưng `while (...) { ... }` không.
5. Khi nào biến cục bộ nằm ngoài phạm vi, và tại sao nó còn có thể bị lỗi *definite assignment*.
6. Vì sao `int[] values = {2, 4};` hoặc `String s = switch (...) { ... };` cần dấu chấm phẩy cuối dù vừa có dấu `}`.

**Ghi nhớ:** Trong Java, **`{ }` là ranh giới cấu trúc**, còn **`;` là token ngữ pháp** để kết thúc hoặc phân tách theo từng dạng câu lệnh. Hãy nhìn **loại cấu trúc đang viết**, không chỉ nhìn vị trí xuống dòng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-005">Khai báo khác với câu lệnh thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo khác với câu lệnh thực thi trong Java

Trong một tệp Java, có những dòng **giới thiệu cấu trúc hoặc tên** cho trình biên dịch, và có những dòng **tạo ra hành động khi chương trình chạy**. Phân biệt hai vai trò này giúp bạn giải thích vì sao một phương thức đã viết nhưng chưa in gì, hoặc vì sao khai báo biến có dấu `=` lại tạo hiệu ứng khi chương trình thực thi.

Ví dụ:

~~~java
int score;                  // Khai báo biến cục bộ.
score = 8;                  // Gán giá trị khi câu lệnh được thực thi.
System.out.println(score); // Thực thi lệnh gọi phương thức: in 8.
~~~

Ba dòng trên phải đặt **bên trong phương thức, constructor hoặc khối phù hợp**. Khai báo `score` xác định một tên và kiểu; phép gán đặt giá trị; `println` in dữ liệu.

Điểm cần nhớ từ đầu: **“khai báo” và “câu lệnh” không phải lúc nào cũng là hai loại cú pháp loại trừ nhau**. `int score = 8;` vừa là một *local variable declaration statement* theo ngữ pháp Java, vừa chứa biểu thức khởi tạo `8`, được đánh giá khi luồng thực thi tới dòng này.

## 1. Nhìn một tệp Java: phần nào khai báo, phần nào chạy?

Xem chương trình rút gọn:

~~~java
public class Example {
    static int limit = 10;           // Khai báo field kèm khởi tạo.

    static void greet() {            // Khai báo phương thức.
        System.out.println("Hello"); // Lệnh trong phần thân.
    }

    public static void main(String[] args) {
        int count = 2;               // Khai báo biến cục bộ có khởi tạo.
        if (count > 0) {             // Câu lệnh rẽ nhánh.
            greet();                 // Lệnh gọi phương thức.
        }
    }
}
~~~

Khi chạy `Example`, trình biên dịch đã biết có lớp `Example` và phương thức `greet`. Nhưng thân `greet` chỉ chạy vì `main` **gọi** `greet()`.

| Cấu trúc | Vai trò chính | Có tự in/chạy phần thân ngay chỉ vì được khai báo? |
| --- | --- | --- |
| `class Example { ... }` | Khai báo kiểu/lớp | Không; mã khởi tạo lớp là chuyện riêng |
| `static void greet() { ... }` | Khai báo phương thức và thân có thể được gọi | Không; phải có lời gọi hoặc cơ chế phù hợp kích hoạt |
| `int count;` trong phương thức | Khai báo biến cục bộ | Không có biểu thức khởi tạo để tính tại dòng đó |
| `int count = calculate();` | Khai báo biến cục bộ **kèm khởi tạo** | Có; `calculate()` được gọi khi tới dòng |
| `count = 5;` | Câu lệnh biểu thức gán | Có; gán giá trị khi tới dòng |
| `System.out.println(count);` | Câu lệnh gọi phương thức | Có; gọi phương thức khi tới dòng |
| `if`, `for`, `while` | Câu lệnh điều khiển | Có; đánh giá điều kiện và quyết định phần mã nào chạy |
| `import java.util.List;` | Khai báo nhập tên kiểu cho mã nguồn | Không tự tải dữ liệu hay gọi `List` |

Trong Java 17, nội dung ở cấp thân lớp không được tùy ý đặt câu lệnh như `System.out.println(...)` đứng một mình. Câu lệnh thông thường thuộc thân phương thức, constructor hoặc **khối khởi tạo** hợp lệ.

## 2. Chương trình đầy đủ: xem thứ tự thực thi

Tạo tệp **`DeclarationDemo.java`** đúng tên sau:

~~~java
public class DeclarationDemo {
    // Khai báo static field, có biểu thức khởi tạo.
    static int classValue = trace("1. static field", 10);

    static {
        System.out.println("2. static initializer");
    }

    // Khai báo instance field, khởi tạo khi tạo đối tượng.
    int objectValue = trace("5. instance field", 20);

    {
        System.out.println("6. instance initializer");
    }

    DeclarationDemo() {
        System.out.println("7. constructor");
    }

    // Chỉ khai báo phương thức không làm nó tự chạy.
    static int trace(String label, int value) {
        System.out.println(label);
        return value;
    }

    void greet() {
        System.out.println("8. greet method");
    }

    static void neverCalled() {
        System.out.println("KHONG DUOC IN");
    }

    public static void main(String[] args) {
        System.out.println("3. main begins");

        int count; // Biến cục bộ được khai báo nhưng chưa có giá trị.
        count = 2; // Lệnh gán thực thi tại đây.
        System.out.println("4. count=" + count);

        DeclarationDemo demo = new DeclarationDemo();
        demo.greet();
    }
}
~~~

**Lệnh chạy trên Windows PowerShell hoặc Linux/Bash** (terminal đặt trong thư mục chứa tệp):

~~~bash
javac -encoding UTF-8 DeclarationDemo.java
java DeclarationDemo
~~~

Kết quả mong đợi:

~~~text
1. static field
2. static initializer
3. main begins
4. count=2
5. instance field
6. instance initializer
7. constructor
8. greet method
~~~

Không có dòng `KHONG DUOC IN` vì phương thức `neverCalled` **được khai báo nhưng không được gọi**.

### Phân tích thứ tự in

1. Trước khi gọi `main`, JVM khởi tạo lớp `DeclarationDemo`. Bộ khởi tạo static field `classValue` gọi `trace` và in dòng 1.
2. Khối `static { ... }` chạy sau đó, in dòng 2. Các phần khởi tạo static được xử lý theo **thứ tự văn bản** trong lớp.
3. Launcher gọi `main`; dòng 3 được in.
4. Khai báo `int count;` **chưa gán giá trị**. Dòng `count = 2;` gán 2, sau đó in dòng 4.
5. Biểu thức `new DeclarationDemo()` tạo đối tượng mới. Field khởi tạo cho từng instance gọi `trace` và in dòng 5.
6. Khối khởi tạo instance in dòng 6, rồi phần thân constructor in dòng 7.
7. Lời gọi `demo.greet()` thực thi phương thức `greet` và in dòng 8.

**Giới hạn của ví dụ:** thứ tự trên áp dụng cho một lớp trực tiếp kế thừa `Object` như `DeclarationDemo`. Với lớp con, còn có bước khởi tạo của superclass; không nên suy rộng thứ tự trên thành mô tả đầy đủ mọi hệ phân cấp lớp.

Có thể chạy trực tiếp nguồn bằng JDK 17+:

~~~bash
java DeclarationDemo.java
~~~

Kết quả tương tự. Với chế độ chạy nguồn trực tiếp, launcher thực hiện biên dịch trong bộ nhớ; nếu dùng `javac` trước, tệp `DeclarationDemo.class` được tạo trên đĩa. Hai cách chạy này đều phù hợp để kiểm tra bài.

## 3. Khai báo biến có hoặc không có biểu thức khởi tạo

So sánh:

~~~java
int a;              // Khai báo biến cục bộ, chưa có giá trị để đọc.
a = 5;              // Gán giá trị.
int b = a * 2;      // Khai báo + đánh giá biểu thức khởi tạo.
System.out.println(b); // In 10.
~~~

Một cách đọc hữu ích:

- `int a;`: “Từ đây tồn tại tên `a` có kiểu `int` trong phạm vi phù hợp.”
- `a = 5;`: “Đặt giá trị của `a` thành 5.”
- `int b = a * 2;`: “Tạo tên `b` có kiểu `int`, **tính `a * 2` ở thời điểm thực thi dòng này** rồi gán kết quả.”
- `println`: “Gọi một phương thức thực hiện xuất dữ liệu.”

Lệnh `int b = a * 2;` không được trình biên dịch “chạy trước” để làm sẵn kết quả trong mọi hoàn cảnh. Nếu `a` thay đổi trước khi tới dòng này, kết quả khởi tạo `b` cũng thay đổi tương ứng.

### Khai báo không đồng nghĩa biến đã sẵn sàng được đọc

Đoạn sau **không biên dịch**:

~~~java
public class UnassignedLocal {
    public static void main(String[] args) {
        int amount;
        // System.out.println(amount); // Nếu bỏ // sẽ lỗi.
        amount = 10;
        System.out.println(amount);
    }
}
~~~

Java áp dụng quy tắc **definite assignment**: biến cục bộ phải được gán chắc chắn trước khi đọc. Việc có một khai báo tên biến **không tự gán mặc định 0** cho biến cục bộ. Trái lại, field `int` không được khởi tạo tường minh sẽ nhận giá trị mặc định 0 khi đối tượng/lớp được khởi tạo theo quy tắc Java.

### Biểu thức trong khai báo có thể gây tác dụng phụ

~~~java
static int nextValue() {
    System.out.println("Tinh gia tri");
    return 7;
}

// Đặt các câu lệnh sau bên trong một phương thức:
int first = nextValue();  // In "Tinh gia tri" khi chạy đến đây.
int second = 9;           // Gán giá trị, không tự in gì.
~~~

Lời gọi `nextValue()` là một **biểu thức** nằm trong khai báo biến; khi tới dòng khởi tạo, biểu thức được đánh giá, kể cả tác dụng phụ của nó. Vì vậy, đừng kết luận “dòng có `int` chỉ là khai báo nên không thực thi gì”.

## 4. Phương thức được khai báo không có nghĩa được gọi

Ví dụ:

~~~java
public class MethodDeclaration {
    static void sayHello() {
        System.out.println("Xin chao");
    }

    public static void main(String[] args) {
        System.out.println("Bat dau");
        // sayHello();
        System.out.println("Ket thuc");
    }
}
~~~

Chạy:

~~~bash
javac MethodDeclaration.java
java MethodDeclaration
~~~

Đầu ra:

~~~text
Bat dau
Ket thuc
~~~

Nếu bỏ dấu chú thích ở `sayHello();`, dòng `Xin chao` xuất hiện **giữa** hai dòng trên. Trong Java, **tên phương thức + chữ ký + phần thân** định nghĩa nó sẽ làm gì *khi được gọi*, không khiến nó tự gọi trong quá trình biên dịch.

Đây cũng là lý do có thể viết nhiều helper method trong một lớp nhưng chỉ một số helper được sử dụng ở một lần chạy cụ thể.

## 5. Vị trí được phép khai báo và vị trí thực thi

| Vị trí trong tệp/lớp Java | Có thể chứa gì? | Chú ý |
| --- | --- | --- |
| Cấp tệp nguồn | `package`, `import` và khai báo kiểu top-level | Không đặt `System.out.println(...)` tùy ý trực tiếp ở đây |
| Thân lớp | Field, constructor, phương thức, nested type, khối khởi tạo | Không đặt một assignment statement hay method call đứng lẻ ngoài khối |
| Thân phương thức | Local variable declaration statements, biểu thức statement, `if`, `for`, `return`... | Thực thi theo luồng điều khiển |
| Constructor | Câu lệnh và các khai báo cục bộ hợp lệ | Chạy khi tạo đối tượng, trong trình tự khởi tạo |
| Khối `static { ... }` | Các câu lệnh khởi tạo lớp | Chạy khi lớp được khởi tạo, không phải mỗi lần tạo đối tượng |
| Khối `{ ... }` trong thân lớp | Các câu lệnh khởi tạo instance | Chạy khi tạo mỗi instance trong trình tự thích hợp |

**Lỗi sai vị trí điển hình:**

~~~java
public class WrongPlacement {
    // System.out.println("Hello"); // Sai nếu bỏ //:
    // câu lệnh gọi phương thức không được đứng lẻ trong thân lớp.

    public static void main(String[] args) {
        System.out.println("Hello"); // Đúng: nằm trong phương thức.
    }
}
~~~

Đừng nhầm dấu ngoặc `{}` trong khai báo lớp với khối phương thức. Không phải mọi câu lệnh hợp lệ bên trong `main` đều được phép chuyển ra trực tiếp cấp lớp.

## 6. Một biểu thức chưa chắc là câu lệnh hợp lệ

Ở Java, các dạng như phép gán, tăng/giảm, lời gọi phương thức và tạo đối tượng có thể xuất hiện như **expression statement**:

~~~java
int n = 2;
n = n + 1;
n++;
System.out.println(n); // 4
new StringBuilder("text");
~~~

Dòng `new StringBuilder("text");` tạo một đối tượng rồi bỏ tham chiếu; hợp lệ về cú pháp dù ít giá trị sử dụng. Ngược lại, không phải mọi biểu thức tính ra giá trị đều được viết một mình thành câu lệnh:

~~~java
// n + 1; // Sai: không phải statement expression hợp lệ.
~~~

Khi mới học, một câu hỏi hữu ích là: “Dòng này **khai báo gì**, **tính/gán gì**, **gọi gì**, hoặc **điều khiển luồng** thế nào?” Điều đó chính xác hơn việc chỉ nhìn dấu `;` và kết luận mọi dòng giống nhau.

## 7. Những lỗi thường gặp và cách phân biệt

| Mã/tình huống | Kết quả | Giải thích |
| --- | --- | --- |
| `int n; System.out.println(n);` trong phương thức | Lỗi biên dịch | Biến cục bộ chưa được gán chắc chắn |
| Khai báo `void work()` nhưng không gọi | Không in gì từ thân method | Method chỉ được định nghĩa, chưa thực thi |
| `int n = read();` | Gọi `read()` khi tới dòng | Initializer là biểu thức chạy |
| Đặt `System.out.println("Hi");` trực tiếp trong thân lớp | Lỗi biên dịch | Không phải khai báo thành viên/khối khởi tạo phù hợp |
| `static int x = trace(...);` | Có thể gây xuất dữ liệu trong khởi tạo lớp | Field initializer được đánh giá theo trình tự khởi tạo |
| `int n = 2;` ở trong `if` không được chọn | Không thực thi initializer đó | Luồng chạy không đi vào nhánh |
| `n + 1;` đứng một mình | Lỗi biên dịch | Không phải expression statement hợp lệ |

**Câu hỏi “khi nào chạy?” phụ thuộc vào vị trí và luồng điều khiển**. Một khối phương thức không tự chạy do tồn tại trong mã nguồn; một field initializer có thể chạy trong khởi tạo đối tượng/lớp; một local initializer chỉ chạy khi luồng tới nó.

## 8. Bài tập tự kiểm tra

**Bài 1 — Dự đoán kết quả.** Đoạn sau in gì?

~~~java
public class OrderDemo {
    static void later() {
        System.out.println("B");
    }

    public static void main(String[] args) {
        System.out.println("A");
        later();
        System.out.println("C");
    }
}
~~~

*Đáp án:* `A`, `B`, `C` trên ba dòng. Thân `later` chạy tại lời gọi giữa hai câu in.

**Bài 2 — Tìm chỗ sai.** Cho:

~~~java
public class ScopeDemo {
    public static void main(String[] args) {
        int score;
        // System.out.println(score);
    }
}
~~~

Tại sao bỏ `//` trước `println` sẽ làm `javac` báo lỗi? Hãy sửa bằng cách gán `score = 10;` trước lời gọi. Sau khi sửa, dự đoán giá trị in ra.

*Đáp án:* Biến cục bộ chưa được gán chắc chắn; sau khi gán, in `10`.

**Bài 3 — Một dòng vừa khai báo vừa thực thi.**

~~~java
int value = Integer.parseInt("21");
~~~

Đâu là phần khai báo? Phần nào được thực thi khi dòng này được chạy? Nếu đổi chuỗi thành `"hai muoi mot"` thì điều gì xảy ra?

*Đáp án:* `int value` khai báo tên/kiểu. `Integer.parseInt(...)` được gọi lúc chạy; chuỗi không hợp lệ gây `NumberFormatException`.

**Bài 4 — Khối nào chạy?** Trong chương trình `DeclarationDemo`, thử bỏ lời gọi `demo.greet();`. Dòng in số 8 còn xuất hiện không? Các dòng khởi tạo instance 5–7 còn xuất hiện không?

*Đáp án:* Không còn số 8; các dòng 5–7 vẫn in vì `new DeclarationDemo()` còn được thực thi.

**Bài 5 — Luyện nhận diện.** Hãy phân loại trong ví dụ sau: khai báo kiểu, khai báo phương thức, khai báo biến có khởi tạo, phép gán và câu lệnh điều kiện.

~~~java
class Sample {
    static boolean enough(int n) {
        int limit = 5;
        n = n + 1;
        if (n >= limit) {
            return true;
        }
        return false;
    }
}
~~~

*Gợi ý:* `class Sample` khai báo kiểu; `enough` khai báo phương thức; `int limit = 5;` vừa khai báo local vừa khởi tạo; `n = n + 1;` là phép gán; `if` chọn nhánh.

## Checklist ghi nhớ

- [ ] Phân biệt khai báo lớp, field và phương thức với hành động khi thực thi.
- [ ] Hiểu rằng local variable declaration **là một dạng statement** trong Java, và initializer của nó được tính khi luồng tới dòng.
- [ ] Không đọc biến cục bộ trước khi chắc chắn đã gán.
- [ ] Dự đoán thứ tự static initialization, instance initialization, constructor, `main` và lời gọi helper qua ví dụ cụ thể.
- [ ] Biết phương thức đã khai báo nhưng không gọi sẽ không tự in output.
- [ ] Không đặt method invocation đứng lẻ ở thân lớp.
- [ ] Biết không phải mọi expression đều có thể đứng một mình thành statement.
- [ ] Chạy được `javac DeclarationDemo.java` và `java DeclarationDemo`, đối chiếu đủ tám dòng kết quả.

**Ghi nhớ:** Trong Java, **khai báo** giúp chương trình định nghĩa tên, kiểu và cấu trúc; **thực thi** đánh giá biểu thức và câu lệnh theo luồng chạy. Một khai báo có thể chứa initializer có hiệu ứng khi chạy, nên hai khái niệm liên quan nhưng **không hoàn toàn đối lập**. Hãy hỏi: “Dòng này khai báo điều gì, và phần nào thực sự được đánh giá khi chương trình tới đó?”


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-006">Câu lệnh biểu thức và câu lệnh rỗng</a>

<details>
<summary>Xem chi tiết</summary>

# Câu lệnh biểu thức và câu lệnh rỗng trong Java

> **Java core — language-basics / Cấu trúc chương trình**  
> **Môi trường:** JDK 17 trở lên, không cần thư viện ngoài.  
> **Kết quả học tập:** Nhận biết expression statement và empty statement, phân biệt biểu thức hợp lệ/không hợp lệ khi đứng riêng, đọc đúng tác dụng của dấu `;`, dự đoán hành vi khi có câu lệnh rỗng trong `if` hoặc vòng lặp, và tự chạy một ví dụ Java hoàn chỉnh.

## 1. Biểu thức, câu lệnh và dấu chấm phẩy

**Biểu thức** (*expression*) là cấu trúc được đánh giá để tạo một giá trị hoặc kết quả kiểu `void`, đôi khi có tác dụng phụ. Ví dụ `2 + 3` tạo giá trị `5`; `number++` vừa tạo một giá trị vừa thay đổi `number`; `System.out.println("Hi")` có tác dụng in một dòng.

**Câu lệnh** (*statement*) là đơn vị hành động/điều khiển được Java cho phép ở một vị trí cụ thể. **Câu lệnh biểu thức** (*expression statement*) có hình thức một **statement expression** được phép, kết thúc bằng dấu `;`.

~~~java
int number = 1;             // Khai báo biến cục bộ: không phải expression statement.
number = number + 2;        // Expression statement: phép gán.
number++;                   // Expression statement: tăng hậu tố.
System.out.println(number); // Expression statement: gọi phương thức.
~~~

Kết quả in là `4`. Điểm quan trọng: **không phải mọi biểu thức đều được phép viết thành một statement chỉ bằng cách thêm dấu `;`**.

| Khái niệm | Ví dụ | Nhận xét |
| --- | --- | --- |
| Biểu thức có giá trị | `number + 2` | Tính ra giá trị nhưng không phải loại được phép đứng riêng thành statement |
| Câu lệnh biểu thức | `number += 2;` | Cú pháp hợp lệ vì thuộc nhóm phép gán |
| Khai báo biến cục bộ | `int number = 1;` | Statement khai báo, không phải expression statement |
| Câu lệnh rỗng | `;` | Statement hoàn chỉnh, không có công việc bên trong |
| Khối lệnh rỗng | `{}` | Một block chứa không statement; **khác cú pháp** với `;` |

## 2. Những expression statement hợp lệ

Java 17 quy định những loại statement expression sau:

| Loại | Ví dụ trong ngữ cảnh phù hợp | Tác dụng |
| --- | --- | --- |
| Gán (*assignment*) | `x = 10;`, `x += 2;` | Gán/thay đổi trạng thái của biến, field hoặc phần tử |
| Tăng, giảm tiền tố | `++x;`, `--x;` | Thay đổi giá trị; biểu thức tạo giá trị sau cập nhật |
| Tăng, giảm hậu tố | `x++;`, `x--;` | Thay đổi giá trị; biểu thức tạo giá trị trước cập nhật |
| Gọi phương thức | `System.out.println(x);`, `Math.abs(-5);` | Gọi method, có thể bỏ qua giá trị trả về |
| Tạo đối tượng bằng `new` | `new StringBuilder("abc");` | Tạo đối tượng, có thể không giữ tham chiếu |

Điều cần chú ý:

- `Math.abs(-5);` là statement hợp lệ nhưng **không in 5**. Kết quả được tính ra rồi bỏ đi. Muốn nhìn thấy nó, dùng `System.out.println(Math.abs(-5));`.
- `new StringBuilder("abc");` hợp lệ, nhưng khi không giữ hoặc sử dụng đối tượng, câu lệnh thường không phục vụ mục đích rõ ràng.
- `x++;` và `++x;` đều tăng `x` một đơn vị khi đứng riêng; giá trị biểu thức của chúng khác nhau **nếu dùng trong một biểu thức lớn hơn**.
- Một lời gọi phương thức không phải lúc nào cũng tạo tác dụng phụ, còn một phép gán hoặc tăng/giảm thường làm thay đổi trạng thái. Đừng định nghĩa expression statement đơn giản là “lệnh có tác dụng phụ”.

**Giới hạn rõ ràng:** `new int[3];` là **biểu thức tạo mảng**, không thuộc nhóm *class instance creation expression* được phép đứng riêng như expression statement. Cần gán mảng cho một biến hoặc dùng nó ở ngữ cảnh biểu thức phù hợp.

## 3. Chương trình mẫu hoàn chỉnh trên Java 17+

Lưu mã dưới tên **StatementKindsDemo.java**:

~~~java
public final class StatementKindsDemo {
    private static int calls = 0;

    private static void mark() {
        calls++;  // Post-increment expression statement.
    }

    public static void main(String[] args) {
        int number = 1;               // Khai báo biến.
        number = number + 2;          // Phép gán.
        number++;                     // Tăng hậu tố.
        mark();                       // Gọi phương thức.
        new StringBuilder("unused");  // Tạo object, bỏ tham chiếu.
        ;                             // Câu lệnh rỗng.

        int checks = 0;
        while (++checks < 3) {
            // Đây là một block rỗng, không phải dấu ; đứng riêng.
        }

        System.out.println("number = " + number);
        System.out.println("calls = " + calls);
        System.out.println("checks = " + checks);

        boolean enabled = false;
        if (enabled)
            ;  // Chỉ empty statement thuộc nhánh if.

        {  // Khối này độc lập với if, vì if đã kết thúc.
            System.out.println("Khoi doc lap van chay");
        }
    }
}
~~~

Mở terminal ở thư mục chứa `StatementKindsDemo.java` rồi chạy:

~~~bash
java -version
javac -version
javac -encoding UTF-8 StatementKindsDemo.java
java StatementKindsDemo
~~~

Hai lệnh `-version` dùng để kiểm tra môi trường; đầu ra riêng của chương trình sau lệnh `java StatementKindsDemo` là:

~~~text
number = 4
calls = 1
checks = 3
Khoi doc lap van chay
~~~

**Vì sao ra những số đó?** `number` chuyển từ 1 sang 3 bằng phép gán, rồi sang 4 bởi `number++`. `mark()` tăng `calls` một lần. Với điều kiện `++checks < 3`, giá trị được so lần lượt là 1, 2, 3; hai lần đầu đúng, lần thứ ba sai, và `checks` cuối cùng bằng **3**. Câu lệnh `new StringBuilder(...)` và `;` đứng riêng không in gì.

`enabled` bằng `false` nên empty statement là thân của `if` không chạy. Tuy nhiên, block `{ ... }` tiếp theo **không nằm trong `if`**, vì thế chương trình vẫn in dòng cuối.

~~~mermaid
flowchart TD
    A["main bắt đầu"] --> B["Gán / tăng / gọi mark / tạo object"]
    B --> C["Empty statement: ;"]
    C --> D["while: kiểm tra ++checks < 3"]
    D --> E["In number, calls, checks"]
    E --> F{"enabled?"}
    F -->|"true"| G["Thực hiện ; rỗng"]
    F -->|"false"| H["Bỏ qua ; rỗng"]
    G --> I["Block độc lập: in dòng cuối"]
    H --> I
~~~

## 4. Biểu thức nào không thể đứng riêng?

Các dòng sau được **comment vì cố ý không hợp lệ**. Không bỏ comment và kỳ vọng biên dịch thành công:

~~~java
int x = 5;

// x + 1;               // Lỗi: phép cộng đơn thuần không phải statement expression.
// x > 0;               // Lỗi: phép so sánh đơn thuần.
// "Hello";             // Lỗi: literal đứng riêng.
// (x == 0) ? 1 : 2;    // Lỗi: conditional expression đứng riêng.
// new int[3];          // Lỗi: array creation đứng riêng.
~~~

Với compiler, các dạng không hợp lệ thường bị báo `not a statement` hoặc một lỗi cú pháp liên quan. Vấn đề không nằm ở chỗ quên dấu `;`; các dòng này **đã có `;` nhưng vẫn không thuộc nhóm Java cho phép**.

Để sửa, hãy **dùng giá trị** vào một hoạt động có ý nghĩa:

~~~java
int x = 5;
int next = x + 1;
boolean positive = x > 0;

System.out.println(next);
System.out.println(positive);
System.out.println(x == 0 ? "Bang 0" : "Khac 0");
~~~

Kết quả:

~~~text
6
true
Khac 0
~~~

`int next = x + 1;` là **khai báo biến cục bộ có biểu thức khởi tạo**; `System.out.println(...)` là **method invocation expression statement**. Biểu thức điều kiện `?:` được đặt **bên trong đối số** của một lời gọi hợp lệ, không được viết thành statement độc lập.

## 5. Empty statement: khi toàn bộ statement chỉ là `;`

Về ngữ pháp, một **câu lệnh rỗng** chỉ gồm dấu chấm phẩy:

~~~java
;
~~~

Nó không tự thay đổi giá trị, không gọi phương thức và không in gì. Nhưng vẫn là **một statement hợp lệ** ở nơi Java chấp nhận statement.

~~~java
int total = 0;
;        // Empty statement thứ nhất.
;        // Empty statement thứ hai.
total++; // Expression statement: tăng total.
System.out.println(total); // 1.
~~~

**Ba cấu trúc dễ lẫn nhau:**

| Cách viết | Bản chất |
| --- | --- |
| `;` | Một empty statement |
| `{}` | Một block rỗng, chứa 0 statement |
| `int total = 0;` | Một local variable declaration statement kết thúc bằng `;` |

Một dấu `;` kết thúc khai báo **không đồng thời tạo thêm empty statement**. Tương tự, dấu `;` dùng để phân tách các phần trong header `for` có vai trò ngữ pháp riêng.

## 6. Empty statement và block rỗng: giống hành vi, khác cú pháp

Cả `;` và `{}` đều có thể biểu thị **không có công việc cần làm**, nhưng chúng là **hai loại statement khác nhau**. Với nhánh `if`, Java có thể nhận một empty statement làm thân:

~~~java
boolean allowed = false;
if (allowed)
    ;
else
    System.out.println("Bi tu choi");
~~~

Trong đoạn mã **bên trong một phương thức**, kết quả là `Bi tu choi`. Dấu `;` là toàn bộ thân nhánh `if`, còn `else` vẫn thuộc chính `if` này. Viết block rỗng có thể diễn đạt ý định rõ hơn:

~~~java
if (allowed) {
    // Chưa có việc cần làm khi được phép.
} else {
    System.out.println("Bi tu choi");
}
~~~

Hai đoạn có cùng hành vi với giá trị `allowed` nêu trên, nhưng block giúp người đọc nhận ra ranh giới nhánh và thuận tiện thêm statement sau này. Một dấu `;` **vô tình** giữa `if (allowed)` và một block chứa `println` sẽ khiến block đó độc lập và chạy bất kể điều kiện; đó là một lỗi logic có thể biên dịch thành công.

Với ví dụ thực tế, thường nên sửa ý định thiếu thành một điều kiện và block rõ nghĩa thay vì giữ những nhánh rỗng khó hiểu. Phần tiếp theo xem trường hợp empty statement nằm trong **vòng lặp**, nơi nó còn ảnh hưởng tới số lượt đánh giá điều kiện.


## 7. Câu lệnh rỗng bên trong vòng lặp

### `while` với empty statement

~~~java
int attempts = 0;
while (++attempts < 3)
    ;
System.out.println(attempts); // 3
~~~

Điều kiện tự tăng `attempts`. Nó được kiểm tra lần lượt với 1, 2, 3 và dừng tại 3. Trong ví dụ này, thân `while` chỉ là dấu `;`.

Ngược lại, dạng sau nếu bỏ comment có nguy cơ lặp vô hạn:

~~~java
// int attempts = 0;
// while (attempts < 3);
// Điều kiện không làm thay đổi attempts, thân vòng cũng không cập nhật nó.
~~~

Đừng dùng vòng **busy-wait** rỗng để chờ một sự kiện trong chương trình thực: ngoài nguy cơ không dừng, nó có thể làm tiêu tốn CPU và gây vấn đề về giao tiếp giữa các thread.

### `for`: hai dấu phân tách ở header không phải empty statement

~~~java
int seen = 0;
for (int i = 0; i < 3; i++)
    ;
seen++;
System.out.println(seen); // 1
~~~

Trong `for (int i = 0; i < 3; i++)`, hai dấu `;` chia ba phần **khởi tạo / điều kiện / cập nhật**. Dấu `;` **sau dấu `)`** mới là empty statement làm thân vòng. `seen++` nằm **sau** vòng, vì vậy chỉ thực hiện một lần.

Muốn tăng `seen` sau mỗi lượt:

~~~java
int seen = 0;
for (int i = 0; i < 3; i++) {
    seen++;
}
System.out.println(seen); // 3
~~~

`for (;;) ;` hợp lệ về cú pháp: hai dấu `;` trong header, còn dấu cuối là thân rỗng. Nhưng **không có điều kiện dừng hoặc break**, nên đây là vòng lặp vô hạn; **không nên chạy để thử**.

### `do...while`: dấu chấm phẩy cuối là bắt buộc

~~~java
int count = 0;
do {
    count++;
} while (count < 2);
System.out.println(count); // 2
~~~

Dấu `;` sau `while (count < 2)` là **bộ phận bắt buộc của cú pháp do-while**, không phải một empty statement thêm vào. Dạng `do ; while (false);` cũng hợp lệ: dấu `;` đầu là thân rỗng, dấu thứ hai kết thúc câu lệnh do-while.

## 8. Khi expression statement xuất hiện trong `for`

Trong phần cập nhật của `for`, Java cho phép một **danh sách statement expressions** được phân tách bằng dấu phẩy:

~~~java
int sum = 0;
for (int i = 1; i <= 3; i++, sum += i) {
    // Block rỗng.
}
System.out.println(sum); // 9
~~~

**Tại sao là 9 mà không phải 6?** Sau mỗi lượt, Java đánh giá `i++` **trước** `sum += i`, tức `sum` lần lượt nhận thêm 2, 3, 4. Tổng là 9. Nếu đổi thứ tự thành `sum += i, i++` thì kết quả là 6.

Lưu ý: dấu phẩy ở đây là cú pháp của danh sách biểu thức trong `for`; Java **không** có một toán tử “comma expression” dùng tự do như trong một số ngôn ngữ khác.

Với `for (int i = 0; i < 3; i++);`, dấu `;` cuối (nằm ngoài dấu ngoặc tròn) có thể khiến vòng lặp chạy không có nội dung mà lập trình viên không cố ý.

## 9. Kết quả của method có thể bị bỏ qua

Một expression statement không nhất thiết in thông báo hoặc cập nhật biến. Ví dụ:

~~~java
String label = "java";
label.toUpperCase();
System.out.println(label);
~~~

Kết quả:

~~~text
java
~~~

`String` là immutable. Phương thức `toUpperCase()` trả về một chuỗi mới; gọi xong mà không dùng kết quả thì `label` vẫn giữ `"java"`. Nếu muốn lưu:

~~~java
label = label.toUpperCase();
System.out.println(label); // JAVA
~~~

Điều này giải thích vì sao **Java cho phép** câu lệnh gọi phương thức nhưng **việc bỏ kết quả** đôi khi là lỗi logic. Ngược lại, lời gọi `System.out.println(...)` hữu ích chính vì tác dụng phụ là in ra stdout.

## 10. Lỗi và cách kiểm tra

| Hiện tượng | Nguyên nhân thường gặp | Khắc phục |
| --- | --- | --- |
| `not a statement` | Biểu thức đứng riêng không thuộc nhóm statement expression | Dùng kết quả trong phép gán, khai báo hoặc lời gọi |
| `';' expected` | Thiếu dấu kết thúc statement/declaration cần dấu `;` | Kiểm tra dạng ngữ pháp đang viết |
| `if` in nội dung dù điều kiện false | `;` đứng sau `if (...)`; block phía sau độc lập | Bỏ `;` sai và dùng `if (...) { ... }` |
| `while` hoặc `for` không kết thúc | Thân rỗng và điều kiện không tiến về false | Thêm cập nhật/điều kiện dừng hợp lý |
| Vòng chạy ba lần nhưng biến chỉ tăng một lần | Câu tăng đứng **sau** vòng, không nằm trong thân | Chuyển câu tăng vào block của vòng |
| Gọi `toUpperCase()` nhưng chuỗi không đổi | Đã bỏ giá trị trả về | Gán lại hoặc sử dụng giá trị trả về |
| Tạo object nhưng không dùng | `new SomeClass();` có kết quả bị bỏ qua | Cân nhắc lưu tham chiếu hoặc loại bỏ lệnh |

Có những lỗi logic **compiler không nhất thiết cảnh báo**: đặc biệt `if (condition);` có thể hợp lệ. IDE/linter có thể giúp phát hiện nhưng không thay thế việc đọc code để xác định **statement nào thật sự nằm trong nhánh hoặc vòng lặp**.

### Ba câu hỏi chẩn đoán dấu `;`

1. `;` này đang **kết thúc** một expression/declaration statement?
2. `;` này đang **phân tách** các phần bên trong `for (...)` hoặc kết thúc cú pháp `do...while`?
3. `;` này đang **tự nó tạo thành empty statement**, có thể làm sai cấu trúc `if`/vòng lặp?

## 11. Bài tập thực hành

**Bài 1 — Phân loại.** Giả sử `int x = 1;` đã có. Với `x += 3;`, `x > 0;`, `x++;`, `;`, `new Object();`, `"hello";`, `System.out.println(x);`, hãy phân loại thành expression statement, empty statement hoặc không hợp lệ.

*Đáp án định hướng:* `x > 0;` và `"hello";` không hợp lệ. `;` là empty statement. Các trường hợp còn lại là expression statements.

**Bài 2 — Dự đoán đầu ra.** Khi `boolean ready = false; if (ready); { System.out.println("OK"); }` được chạy trong method, có in `OK` không? Vì sao?

*Đáp án:* Có; `if` chỉ quản lý empty statement, block tiếp theo độc lập.

**Bài 3 — Cập nhật điều kiện.** Với `int n = 0; while (++n < 5) ;`, sau vòng `n` bằng bao nhiêu? Đổi `++n` thành `n++` làm khác hành vi kiểm tra ra sao?

*Định hướng:* Với `++n`, n cuối cùng là 5. Với `n++`, so sánh dùng giá trị trước tăng, và n cuối cùng là 6.

**Bài 4 — Sửa statement sai.** Chuyển `int x = 4; x * 2;` thành một phương án in `8` và một phương án lưu kết quả trong biến `doubleX`.

**Bài 5 — Thử source chạy được.** Trong `StatementKindsDemo`, thay `if (enabled) ;` và block độc lập thành `if (enabled) { ... }`. Chạy với `enabled` lần lượt false và true, so sánh số dòng output.

**Bài 6 — Thứ tự side effect.** Thay `i++, sum += i` trong header `for` bằng `sum += i, i++`, giải thích vì sao tổng thay đổi từ 9 sang 6.

## 12. Tự đánh giá

Bạn nắm bài nếu không còn nhầm:

- **`x++;`** — statement biểu thức hợp lệ;
- **`x + 1;`** — không phải statement hợp lệ;
- **`;`** — empty statement;
- **`{ }`** — block rỗng, **không đồng nhất** với empty statement;
- **`if (condition); { ... }`** — block sau `if` chạy độc lập;
- **`for (a; b; c)`** — hai `;` chia ba phần header, không phải hai empty statement.

**Kết luận:** Java chỉ cho một số dạng biểu thức nhất định đứng riêng thành câu lệnh. Dấu `;` có thể **kết thúc** một câu lệnh, **phân tách** phần header, hoặc **tự nó là một câu lệnh rỗng**. Hãy xác định vai trò theo **ngữ pháp xung quanh**, không theo chỗ xuống dòng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-007">Cách chương trình bắt đầu, gọi phương thức và kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Cách chương trình bắt đầu, gọi phương thức và kết thúc

## 1. Từ lệnh java đến phương thức main

Với **Java 17+** chạy ứng dụng ở chế độ class đã biên dịch, lệnh `java TenLop` chọn **lớp điểm vào**, khởi động JVM và gọi phương thức `main` phù hợp. Trước khi chạy thân `main`, JVM cần tải, liên kết và khởi tạo những lớp cần thiết. Các bộ khởi tạo `static` có thể in thông báo **trước** câu lệnh đầu tiên trong `main`.

Điểm vào thông dụng trên Java 17:

~~~java
public static void main(String[] args) {
    // Chương trình bắt đầu xử lý từ đây
}
~~~

Một lần chạy đơn giản diễn ra theo thứ tự: JVM khởi tạo lớp → gọi `main` → `main` gọi các method → các method trả về → `main` hoàn tất. Nếu có ngoại lệ hoặc các luồng đang chạy, đường đi có thể khác.

~~~mermaid
flowchart TD
    A["java FlowDemo"] --> B["Khởi tạo lớp cần thiết"]
    B --> C["main(String[] args)"]
    C --> D["Gọi phương thức"]
    D --> E{"Phương thức kết thúc?"}
    E -->|"return"| F["Quay lại bên gọi"]
    E -->|"throw"| G["Tìm catch trên chuỗi lời gọi"]
    F --> H["main tiếp tục"]
    G --> H
    G --> I["Không được xử lý: luồng kết thúc bất thường"]
    H --> J["main hoàn tất"]
    J --> K["JVM kiểm tra các luồng non-daemon còn sống"]
    I --> K
~~~

Sơ đồ mô tả luồng khái quát, không khẳng định mọi lỗi đều được bắt hoặc mọi tiến trình JVM đều kết thúc ngay sau `main`.

## 2. Chương trình thực hành có đầu ra dự đoán được

Tạo tệp đúng tên **`FlowDemo.java`**:

~~~java
public final class FlowDemo {
    static {
        System.out.println("0. Khoi tao lop");
    }

    private FlowDemo() {}

    static int twice(int number) {
        System.out.println("3. Vao twice(" + number + ")");
        if (number < 0) {
            throw new IllegalArgumentException("number must be >= 0");
        }
        int value = Math.multiplyExact(number, 2);
        System.out.println("4. Tra ve " + value);
        return value;
    }

    static void display(int result) {
        System.out.println("6. Ket qua: " + result);
    }

    public static void main(String[] args) {
        System.out.println("1. Bat dau main");

        int input = args.length == 0 ? 3 : Integer.parseInt(args[0]);
        System.out.println("2. Input: " + input);

        int answer = twice(input);
        System.out.println("5. Quay lai main");

        display(answer);
        System.out.println("7. Het main");
    }
}
~~~

Tại thư mục chứa file, kiểm tra JDK rồi biên dịch/chạy.

**Bash (Linux/macOS):**

~~~bash
java -version
javac -version
javac --release 17 -encoding UTF-8 FlowDemo.java
java -cp . FlowDemo
java -cp . FlowDemo 5
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
javac --release 17 -encoding UTF-8 .\FlowDemo.java
java -cp . FlowDemo
java -cp . FlowDemo 5
~~~

**Đầu ra lần không có đối số:**

~~~text
0. Khoi tao lop
1. Bat dau main
2. Input: 3
3. Vao twice(3)
4. Tra ve 6
5. Quay lai main
6. Ket qua: 6
7. Het main
~~~

**Đầu ra khi truyền `5`:**

~~~text
0. Khoi tao lop
1. Bat dau main
2. Input: 5
3. Vao twice(5)
4. Tra ve 10
5. Quay lai main
6. Ket qua: 10
7. Het main
~~~

Mỗi lệnh `java` trong ví dụ khởi động một JVM mới nên phần `static` in dòng `0` trong **mỗi lần chạy**.


## 3. Đối số của `main` tham gia luồng thực thi thế nào?

Với `java -cp . FlowDemo 5`, launcher chọn lớp `FlowDemo` và truyền chuỗi `"5"` vào `args[0]`. Phương thức `main` dùng `Integer.parseInt(args[0])` để chuyển chuỗi thành `int` trước khi gọi `twice`. Nếu không có đối số, biểu thức `args.length == 0 ? 3 : ...` chọn số mặc định `3`.

Trên Java 17, điểm vào truyền thống là `public static void main(String[] args)`: launcher gọi phương thức `static` đó mà không tạo đối tượng; `args` là mảng các chuỗi đối số ứng dụng. Phân tích từng thành phần chữ ký và quy tắc kiểm tra `args.length` nằm ở bài **Vai trò của public static void main(String[] args)**. Tại đây, hãy quan sát **thời điểm** chuyển dữ liệu: truyền `abc` khiến `Integer.parseInt` ném `NumberFormatException` ngay trong `main`, trước cả khi `twice` được gọi.

Lệnh `java -cp . FlowDemo.class` không hợp lệ trong class mode; launcher nhận tên lớp `FlowDemo`. Nếu lớp nằm trong package, cần tên lớp đầy đủ và classpath root phù hợp, chẳng hạn `java -cp out demo.Entry`.

## 4. Cơ chế gọi method: ai chạy trước và ai nhận kết quả?

Trong ví dụ `FlowDemo`, `main` gặp biểu thức:

~~~java
int answer = twice(input);
~~~

Lúc đó:

1. Java đánh giá đối số `input` rồi thực hiện lời gọi `twice`.
2. Thân `twice` chạy từ đầu cho tới `return` hoặc khi ném exception.
3. `return value;` làm lời gọi `twice` **hoàn tất bình thường** và chuyển giá trị `int` về biểu thức ở bên gọi.
4. `main` gán giá trị đó vào `answer` rồi mới in dòng `5`.
5. `main` gọi `display(answer)`. Vì `display` trả `void`, nó không gửi lại một giá trị có thể gán.
6. Khi `display` kết thúc, `main` chạy tiếp dòng `7`.

| Dòng in | Method đang hoạt động | Vì sao xuất hiện lúc đó? |
|---|---|---|
| 0 | Bộ khởi tạo static | JVM khởi tạo lớp trước khi gọi main |
| 1, 2 | `main` | Các lệnh đầu trong main |
| 3, 4 | `twice` | `main` đã gọi twice và đang chờ kết quả |
| 5 | `main` | twice đã return |
| 6 | `display` | main gọi display |
| 7 | `main` | display hoàn tất, main tiếp tục |

**Khai báo một method phía trên `main` trong file không khiến nó chạy trước `main`.** Method chạy vì có lời gọi, không vì vị trí được viết trong source.

### return chỉ thoát phương thức hiện tại

~~~java
static void printIfPositive(int value) {
    if (value <= 0) {
        return;
    }
    System.out.println(value);
}

// Đặt trong một main:
printIfPositive(0);
System.out.println("main van tiep tuc");
~~~

Kết quả chỉ là `main van tiep tuc`. `return;` ở `printIfPositive` **không** kết thúc `main`, càng không phải lệnh tắt JVM.

## 5. Call stack: đường đi xuống và quay trở lại

Mỗi luồng Java có **call stack** gồm các frame của những lời gọi method đang thực thi. Khi gọi method mới, ngữ cảnh của bên gọi được giữ lại để có thể tiếp tục sau khi method con trả về.

~~~text
Khi main đang chạy:           Khi main gọi twice:
+-----------------------+     +-----------------------+
| main(args)            |     | twice(number=3)       |  <-- đang chạy
+-----------------------+     +-----------------------+
                              | main(args)            |
                              +-----------------------+
Sau return: chỉ còn main tiếp tục tại câu lệnh sau lời gọi.
~~~

Đây là mô hình giải thích, không phải sơ đồ địa chỉ bộ nhớ thực tế của mọi JVM.

Thử thêm chương trình **`CallChain.java`**:

~~~java
public class CallChain {
    static int inner(int x) {
        System.out.println("inner");
        return x * 2;
    }

    static int middle(int x) {
        System.out.println("middle");
        return inner(x) + 1;
    }

    static void outer() {
        System.out.println("outer");
        int value = middle(3);
        System.out.println("value = " + value);
    }

    public static void main(String[] args) {
        System.out.println("main starts");
        outer();
        System.out.println("main ends");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 CallChain.java
java -cp . CallChain
~~~

Đầu ra:

~~~text
main starts
outer
middle
inner
value = 7
main ends
~~~

Thứ tự lời gọi đi xuống là `main → outer → middle → inner`. Kết quả đi lên là `inner` trả `6`, `middle` trả `7`, rồi `outer` in `7`. Vì vậy `main ends` chỉ xuất hiện sau khi `outer` kết thúc.

## 6. Tham số: Java luôn truyền theo giá trị

Khi gọi method, Java **sao chép giá trị đối số** vào tham số. Với primitive, đó là giá trị như `int`. Với đối tượng, đó là **giá trị tham chiếu**, nên cả bên gọi và bên được gọi có thể trỏ đến cùng object.

~~~java
static void change(int n, StringBuilder box) {
    n = 99;
    box.append("!");
    box = new StringBuilder("Khac");
}

// Trong main:
int count = 3;
StringBuilder text = new StringBuilder("Hi");
change(count, text);
System.out.println(count); // 3
System.out.println(text);  // Hi!
~~~

Gán `n = 99` không đổi biến `count` bên gọi. `box.append("!")` thay đổi **trạng thái object dùng chung**, nên `text` thấy `Hi!`. Gán lại `box` không làm `text` trỏ sang đối tượng mới.

Không diễn giải Java là “pass-by-reference” chỉ vì một object mutable có thể bị thay đổi qua tham số.

## 7. Ném exception khác với return

Trong `twice`, số âm sẽ bị từ chối:

~~~java
if (number < 0) {
    throw new IllegalArgumentException("number must be >= 0");
}
~~~

Thử:

~~~bash
java -cp . FlowDemo -1
~~~

Trước stack trace, chương trình in:

~~~text
0. Khoi tao lop
1. Bat dau main
2. Input: -1
3. Vao twice(-1)
~~~

Sau đó `IllegalArgumentException` lan từ `twice` lên `main` do không có `catch` phù hợp. Stack trace thường chứa thông tin dạng:

~~~text
Exception in thread "main" java.lang.IllegalArgumentException: number must be >= 0
    at FlowDemo.twice(FlowDemo.java:...)
    at FlowDemo.main(FlowDemo.java:...)
~~~

Số dòng trong dấu `...` tùy file. Các dòng `4` đến `7` không in vì lời gọi không hoàn tất theo đường bình thường.

Với:

~~~bash
java -cp . FlowDemo abc
~~~

`Integer.parseInt("abc")` ném `NumberFormatException` khi tính `input`, **trước** dòng `2` và trước lời gọi `twice`. Chỉ dòng `0`, `1` xuất hiện trước thông báo lỗi.

Để phục hồi từ một exception dự kiến, bên gọi có thể xử lý:

~~~java
try {
    int answer = twice(-1);
    System.out.println(answer);
} catch (IllegalArgumentException ex) {
    System.out.println("Input sai: " + ex.getMessage());
}
System.out.println("Tiep tuc");
~~~

Khi đặt snippet trong một method hợp lệ, nó in `3. Vao twice(-1)`, rồi `Input sai: number must be >= 0`, sau đó `Tiep tuc`. Câu lệnh `System.out.println(answer)` bên trong `try` không chạy vì exception đã chuyển luồng đến `catch`.

Exception chưa xử lý có thể làm **luồng main** kết thúc bất thường; điều đó không phải lời gọi `System.exit` và không bảo đảm không còn các luồng khác.

## 8. Khi nào một ứng dụng Java thật sự dừng?

Phân biệt ba tình huống:

| Trường hợp | Hệ quả |
|---|---|
| `main` chạy tới hết hoặc `return;` | Luồng main hoàn thành |
| `main` thoát vì exception chưa xử lý | Luồng main kết thúc bất thường; thường có stack trace |
| `System.exit(status)` | Yêu cầu JVM thực hiện trình tự shutdown với mã trạng thái |

Trong ứng dụng đơn luồng như `FlowDemo`, `main` kết thúc bình thường, JVM thường dừng với **exit code 0**. Nhưng nếu còn **luồng non-daemon** sống, JVM thông thường vẫn tiếp tục chạy sau khi `main` hoàn tất.

Thử **`ThreadExitDemo.java`**:

~~~java
public class ThreadExitDemo {
    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("worker finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();
        System.out.println("main finished");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ThreadExitDemo.java
java -cp . ThreadExitDemo
~~~

Bạn sẽ thấy cả `main finished` và `worker finished`. Thứ tự in là kết quả của lịch chạy các luồng, không nên xem là hợp đồng cố định; JVM vẫn chờ worker non-daemon hoàn tất trước khi shutdown bình thường.

`System.exit(2)` yêu cầu dừng **cả JVM** với status 2 (thường quy ước khác 0 nghĩa là lỗi). Nó không tương đương `return;`, và **không bảo đảm các block `finally` của luồng đang bị dừng chạy như khi rời method bình thường**. Tránh gọi `System.exit` trong các method thư viện; để tầng khởi chạy quyết định cách báo lỗi và dừng ứng dụng.

## 9. finally trong đường return

Method có `return` trong `try` vẫn thực hiện `finally` trước khi trả quyền điều khiển về bên gọi, nếu JVM rời block theo cơ chế bình thường:

~~~java
static int compute() {
    try {
        System.out.println("A. try");
        return 10;
    } finally {
        System.out.println("B. finally");
    }
}

// Gọi trong main:
int value = compute();
System.out.println("C. value = " + value);
~~~

Kết quả:

~~~text
A. try
B. finally
C. value = 10
~~~

Không đặt `return` trong `finally` để tránh che mất exception hoặc thay đổi kết quả của `try`. Nếu quản lý tài nguyên như file/stream, dùng `try-with-resources` khi phù hợp thay vì trông chờ JVM sắp kết thúc.

## 10. Bảng chẩn đoán thường gặp

| Hiện tượng | Nguyên nhân có thể có | Bước kiểm tra |
|---|---|---|
| `Main method not found` | Class không có entry point theo Java 17 | Chữ ký `public static void main(String[] args)` |
| `Could not find or load main class` | Sai package, classpath hoặc tên class | Classpath root, file `.class`, tên đầy đủ |
| Không in dòng sau lời gọi method | `throw`, `return` sớm hoặc nhánh không chạy | Trace lời gọi và stack trace |
| `NumberFormatException` | `args[0]` không phải số nguyên hợp lệ | Xác thực và xử lý input |
| `ArithmeticException` tại `multiplyExact` | Kết quả nhân vượt miền `int` | Kiểm tra miền giá trị/kiểu dữ liệu |
| Chạy xong main mà terminal chưa trả dấu nhắc | Vẫn còn worker non-daemon hoặc tác vụ chờ | Quan sát trạng thái threads, blocking I/O |
| Dòng log không theo thứ tự đoán giữa hai threads | Lịch chạy không cố định | Đồng bộ khi cần thứ tự, đừng suy luận từ log ngẫu nhiên |

## 11. Bài tập với đáp án

### Bài A — lần theo lời gọi

~~~java
static int addOne(int x) {
    System.out.println("B");
    return x + 1;
}
// Trong main:
System.out.println("A");
int y = addOne(4);
System.out.println("C:" + y);
~~~

**Đáp án:**

~~~text
A
B
C:5
~~~

Vì bên gọi chỉ in `C:5` khi `addOne` đã hoàn thành và trả về `5`.

### Bài B — return có tắt JVM không?

Nếu `validate()` gặp `return;` nhưng `main` còn `System.out.println("done");` phía sau lời gọi, điều gì xảy ra?

**Đáp án:** `validate` kết thúc; `main` vẫn in `done` nếu không có exception hoặc đường rẽ khác ngăn cản.

### Bài C — dự đoán đầu ra khi nhập sai

Với `FlowDemo`, chạy:

~~~bash
java -cp . FlowDemo -1
~~~

**Đáp án:** chương trình in các dòng `0`, `1`, `2`, `3` rồi báo `IllegalArgumentException`; không in `4` đến `7`.

### Bài D — tràn số ở phương thức con

~~~bash
java -cp . FlowDemo 2147483647
~~~

**Đáp án:** `Math.multiplyExact` ném `ArithmeticException` do vượt miền `int`; không có giá trị trả về hợp lệ để `main` gán cho `answer`.

### Bài E — main đã hết nhưng worker còn chạy

Ứng dụng còn một thread non-daemon đang xử lý. JVM có buộc phải dừng ngay khi `main` return không?

**Đáp án:** Không. Kết thúc luồng main khác với kết thúc toàn bộ tiến trình JVM; bình thường JVM còn đợi worker non-daemon.

## 12. Checklist

- [ ] Biết chữ ký điểm vào `main` thông dụng trên Java 17.
- [ ] Chạy đúng lệnh `javac` và `java`, hiểu `args[0]`.
- [ ] Giải thích vì sao static initializer có thể chạy trước thân main.
- [ ] Theo dõi được `main → method → return → main`.
- [ ] Hiểu frame của call stack và truyền tham số theo giá trị.
- [ ] Phân biệt `return`, `throw` và `System.exit`.
- [ ] Biết đọc stack trace và vị trí phát sinh lỗi.
- [ ] Nhận diện được trường hợp `main` kết thúc nhưng JVM còn chạy.
- [ ] Hiểu `finally` trong đường `return` và rủi ro dừng JVM cưỡng bức.

**Kết luận:** Launcher chọn class có điểm vào; JVM khởi tạo lớp cần thiết rồi gọi `main`. Lời gọi method chuyển điều khiển vào thân phương thức, `return` đưa kết quả về caller, còn exception có thể đi theo đường khác. `main` kết thúc không đồng nghĩa JVM luôn dừng ngay: vòng đời tiến trình còn phụ thuộc các luồng non-daemon và cơ chế shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-structure-008">Thụt lề để thể hiện cấu trúc và tránh hiểu nhầm phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Thụt lề để thể hiện cấu trúc và tránh hiểu nhầm phạm vi trong Java

Khi đọc mã Java, **thụt lề (indentation)** giúp mắt người nhìn ra câu lệnh nào thuộc lớp, phương thức, nhánh `if` hoặc vòng lặp. Nhưng khác với một số ngôn ngữ lập trình, Java **không dùng độ thụt lề để quyết định phạm vi khối lệnh**. Dấu ngoặc nhọn `{ }` và cấu trúc cú pháp mới là căn cứ để trình biên dịch hiểu chương trình.

Vì vậy, một đoạn Java **có thể thụt lề rất đẹp mà vẫn làm sai ý định** nếu bạn bỏ dấu ngoặc nhọn. Bài học này giúp bạn đọc và viết các khối mã rõ ràng, hiểu phạm vi biến và phòng ngừa lỗi logic do bố cục gây hiểu nhầm. Ví dụ tương thích JDK 17 trở lên.

## 1. Quy tắc cốt lõi: thụt lề dành cho người đọc, ngoặc dành cho cú pháp

So sánh hai đoạn:

~~~java
int score = 4;

if (score >= 5) {
    System.out.println("Dat");
    System.out.println("Cap chung chi");
}
~~~

Với `score = 4`, **không dòng nào được in**: cả hai câu lệnh đều nằm trong khối `if`, điều kiện sai nên khối bị bỏ qua.

Nhưng nếu bỏ ngoặc nhọn:

~~~java
int score = 4;

if (score >= 5)
    System.out.println("Dat");
    System.out.println("Cap chung chi");
~~~

Kết quả:

~~~text
Cap chung chi
~~~

Java hiểu đoạn thứ hai tương đương:

~~~java
if (score >= 5) {
    System.out.println("Dat");
}
System.out.println("Cap chung chi");
~~~

Chỉ **một statement ngay sau `if`** được điều khiển khi không có khối `{ }`. Dòng `println` thứ hai luôn chạy, **bất kể nó được thụt vào bao nhiêu dấu cách**.

> Quy ước nên áp dụng: **luôn dùng `{ }` với `if`, `else`, `for` và `while`**, kể cả khi nhánh chỉ có một câu lệnh. Đây là quy ước giúp tránh lỗi, không phải yêu cầu cú pháp trong mọi trường hợp.

## 2. Cấu trúc lớp, phương thức và khối lệnh

Những mức thụt lề thường gặp khi đọc một tệp Java:

~~~java
public class LayoutExample {                  // Mức 0: lớp
    public static void main(String[] args) {  // Mức 1: phương thức
        int score = 8;                        // Mức 2: trong phương thức

        if (score >= 5) {                     // Mức 2: câu lệnh if
            System.out.println("Dat");        // Mức 3: trong khối if
        }

        System.out.println("Hoan tat");        // Mức 2: ngoài khối if
    }
}
~~~

Trên JDK 17, lưu đoạn trên vào `LayoutExample.java`, biên dịch và chạy:

~~~bash
javac -encoding UTF-8 LayoutExample.java
java LayoutExample
~~~

Kết quả:

~~~text
Dat
Hoan tat
~~~

Hãy đọc dấu ngoặc trước khi đọc khoảng trắng:

| Dấu hiệu | Vai trò trong Java | Điều dễ nhầm |
| --- | --- | --- |
| `{` | Bắt đầu một khối, thân lớp hoặc thân phương thức tùy vị trí | Chỉ dựa vào cột thụt lề |
| `}` | Kết thúc khối tương ứng | Không nhận ra biến đã ra khỏi phạm vi |
| `;` | Kết thúc một số loại câu lệnh/khai báo | Đặt `;` sai ngay sau `if` |
| Khoảng trắng đầu dòng | Trình bày mã cho dễ đọc | Tưởng rằng tự tạo khối lệnh |
| `if` / `else` | Quyết định nhánh thực thi | Tưởng `else` luôn ghép với `if` có cùng độ thụt lề |
| `for` / `while` | Lặp một statement hoặc một khối | Tưởng tất cả dòng thụt vào đều lặp |

Trong mã nguồn, `{ }` phải ghép cặp đúng. Với editor hỗ trợ Java, đặt con trỏ lên một dấu ngoặc thường giúp nhìn thấy dấu còn lại.

## 3. Chương trình thực hành: thụt lề rõ để nhìn thấy phạm vi

Tạo tệp **`IndentScopeDemo.java`**:

~~~java
public class IndentScopeDemo {
    static String classify(int score) {
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException(
                    "Diem phai nam trong khoang 0..10");
        }

        if (score >= 8) {
            return "Gioi";
        }

        if (score >= 5) {
            return "Dat";
        }

        return "Can co gang";
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println(
                    "Cach dung: java IndentScopeDemo <diem>");
            return;
        }

        try {
            int score = Integer.parseInt(args[0]);
            String result = classify(score);

            System.out.println("Diem: " + score);
            System.out.println("Xep loai: " + result);
            System.out.println("Hoan tat");
        } catch (NumberFormatException ex) {
            System.out.println("Loi: diem phai la so nguyen");
        } catch (IllegalArgumentException ex) {
            System.out.println("Loi: " + ex.getMessage());
        }
    }
}
~~~

### Chạy trên Windows PowerShell hoặc Linux

Tại thư mục chứa `IndentScopeDemo.java`, cả hai môi trường đều dùng được:

~~~bash
java -version
javac -version
javac -encoding UTF-8 IndentScopeDemo.java
java IndentScopeDemo 8
~~~

Kết quả:

~~~text
Diem: 8
Xep loai: Gioi
Hoan tat
~~~

Một trường hợp khác:

~~~bash
java IndentScopeDemo 4
~~~

~~~text
Diem: 4
Xep loai: Can co gang
Hoan tat
~~~

**Điểm cần chú ý:** câu lệnh `System.out.println("Hoan tat");` nằm sau khối phân loại và sau khi dữ liệu hợp lệ. Nó chạy với mọi điểm hợp lệ, không phụ thuộc vào nhánh `score >= 8` hoặc `score >= 5`.

Các `return` trong `classify` làm phương thức kết thúc ngay tại nhánh phù hợp. Vì thế, sau `return "Gioi";`, chương trình không tiếp tục kiểm tra `if (score >= 5)` trong **cùng lời gọi `classify`**.

**Bảng kiểm tra đầu vào:**

| Lệnh | Kết quả chính |
| --- | --- |
| `java IndentScopeDemo 10` | `Xep loai: Gioi` |
| `java IndentScopeDemo 8` | `Xep loai: Gioi` |
| `java IndentScopeDemo 7` | `Xep loai: Dat` |
| `java IndentScopeDemo 5` | `Xep loai: Dat` |
| `java IndentScopeDemo 4` | `Xep loai: Can co gang` |
| `java IndentScopeDemo 0` | `Xep loai: Can co gang` |
| `java IndentScopeDemo -1` | `Loi: Diem phai nam trong khoang 0..10` |
| `java IndentScopeDemo 11` | `Loi: Diem phai nam trong khoang 0..10` |
| `java IndentScopeDemo abc` | `Loi: diem phai la so nguyen` |
| `java IndentScopeDemo` | In hướng dẫn sử dụng |

Hai khối `catch` giúp tách lỗi **chuyển chuỗi sang số** khỏi lỗi **số ngoài khoảng hợp lệ**. `NumberFormatException` là lớp con của `IllegalArgumentException`, vì vậy nhánh bắt cụ thể phải đứng trước nhánh tổng quát.

## 4. Lỗi khó thấy: `else` thuộc về `if` nào?

Xem đoạn không dùng ngoặc:

~~~java
boolean registered = false;
int score = 3;

if (registered)
    if (score >= 5)
        System.out.println("Du dieu kien");
    else
        System.out.println("Chua dat diem");
~~~

Dù người viết có thể muốn `else` ứng với `if (registered)`, trong Java **`else` ghép với `if` gần nhất mà nó có thể ghép về mặt cú pháp**. Ở đây, `else` thuộc `if (score >= 5)`, và toàn bộ nhánh bên trong không chạy vì `registered` là `false`.

**Kết quả: không in dòng nào.**

Viết lại để ý định rõ ràng:

~~~java
boolean registered = false;
int score = 3;

if (registered) {
    if (score >= 5) {
        System.out.println("Du dieu kien");
    } else {
        System.out.println("Chua dat diem");
    }
} else {
    System.out.println("Chua dang ky");
}
~~~

**Kết quả:**

~~~text
Chua dang ky
~~~

Ngoặc nhọn không chỉ giúp đẹp mã: chúng **quy định chính xác các nhánh** mà lập trình viên muốn gắn với nhau.

## 5. Phạm vi biến (scope) không phụ thuộc vào độ thụt lề

Một biến cục bộ được khai báo trong khối thường chỉ dùng được ở phạm vi tương ứng:

~~~java
int score = 7;

if (score >= 5) {
    String message = "Dat";
    System.out.println(message); // Hợp lệ.
}

// System.out.println(message); // Không biên dịch:
// message đã ra khỏi phạm vi ở dấu } của if.
~~~

Muốn dùng kết quả ngoài khối, khai báo biến ở phạm vi phù hợp và gán giá trị bên trong:

~~~java
int score = 7;
String message;

if (score >= 5) {
    message = "Dat";
} else {
    message = "Can co gang";
}

System.out.println(message); // Dat
~~~

Compiler kiểm tra biến `message` đã được gán chắc chắn trước khi dùng (*definite assignment*). Việc tạo khoảng trắng hoặc thụt dòng `println` sang phải **không thể mở rộng phạm vi biến**.

Các dấu `{ }` có thể tạo khối độc lập bên trong phương thức:

~~~java
{
    int temporary = 5;
    System.out.println(temporary); // 5
}
// System.out.println(temporary); // Không thấy tên temporary ở đây.
~~~

Vì vậy, khi gặp lỗi `cannot find symbol` đối với biến cục bộ, hãy kiểm tra **cặp ngoặc và nơi khai báo biến**, không chỉ chỉnh số dấu cách.

## 6. Vòng lặp thiếu ngoặc cũng gây nhầm lẫn

Ví dụ:

~~~java
int i;
for (i = 0; i < 3; i++)
    System.out.println("Lan " + i);
    System.out.println("Ket thuc");
~~~

Dù hai dòng `println` có cùng độ thụt lề, vòng `for` chỉ điều khiển **lời gọi đầu tiên**. Kết quả:

~~~text
Lan 0
Lan 1
Lan 2
Ket thuc
~~~

Nếu muốn cả hai lời gọi chạy trong mỗi vòng lặp, đặt chúng trong khối:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println("Lan " + i);
    System.out.println("Ket thuc");
}
~~~

Khi đó `Ket thuc` được in **ba lần**, mỗi lần ngay sau `Lan i`. Đây là thay đổi logic do **ngoặc nhọn**, không phải do thêm khoảng trắng.

Tương tự với `while`, không có ngoặc thì chỉ statement ngay sau vòng lặp được lặp. Nếu statement đó không làm điều kiện tiến gần điểm dừng, chương trình có thể lặp vô hạn. Viết khối rõ ràng giúp nhìn thấy cả **điều kiện** lẫn **cập nhật biến điều khiển**.


## 7. Formatter làm lộ cấu trúc thật, nhưng không biết ý định của bạn

Formatter của IDE căn lề theo **cú pháp Java hiện có**. Nếu nó đưa một câu lệnh ra cấp ngoài của `if` hoặc `for`, đó là dấu hiệu nên đọc lại vị trí của `{` và `}`; formatter không tự biết bạn muốn câu lệnh ấy chỉ chạy trong nhánh hay chạy mọi lúc.

Khi review mã đã định dạng, hãy kiểm tra theo thứ tự:

1. **So các cặp ngoặc với ý định:** câu lệnh nào nằm trong block của lớp, phương thức, nhánh và vòng lặp?
2. **Đọc token quanh điều kiện:** một dấu `;` sau `if (...)` hoặc `while (...)` có thể tự tạo thân rỗng dù trình bày trông hợp lý.
3. **Thử hai đường đi:** chạy ca điều kiện đúng và sai, hoặc vòng lặp 0 và nhiều lần, rồi đối chiếu hành vi với mong đợi.

Ví dụ một câu `println` được formatter căn về cùng cấp với `if` sẽ vẫn nằm **ngoài** nhánh nếu ngoặc đóng trước nó. Muốn đổi hành vi phải sửa **vị trí ngoặc hoặc câu lệnh**, rồi kiểm thử lại; chỉ chỉnh khoảng trắng không đủ.

## 8. Quy tắc trình bày thực tế

Để mã Java 17 dễ đọc trong học tập và cộng tác:

1. **Thụt một mức thống nhất**, thường chọn **4 dấu cách** cho mỗi cấp khối; tránh trộn tab và spaces tùy tiện trong cùng tệp.
2. **Luôn dùng ngoặc nhọn** với câu lệnh điều kiện và vòng lặp, kể cả khối chỉ có một dòng.
3. Đặt ngoặc mở `{` cùng dòng khai báo `class`, phương thức hoặc `if` theo một phong cách nhất quán; đặt `}` ở cột tương ứng.
4. Không dùng thụt lề để “vẽ” phạm vi mà thiếu ngoặc thật.
5. Mỗi lần thêm hoặc chuyển một dấu ngoặc, biên dịch lại và chạy ca bình thường **và ca biên**.
6. Dùng trình định dạng Java tự động khi có, nhưng **đọc lại logic**: formatter không thể tự biết ý định nghiệp vụ của bạn.

Ví dụ bố cục dễ đọc:

~~~java
if (score >= 5) {
    System.out.println("Dat");
} else {
    System.out.println("Chua dat");
}
System.out.println("Hoan tat");
~~~

Đây là **quy ước trình bày**, không phải cú pháp thụt lề bắt buộc. Máy đọc cấu trúc nhờ từ khóa, dấu phân cách và khối; người đọc hưởng lợi từ cách trình bày phản ánh trung thực cấu trúc đó.

## 9. Bài tập tự kiểm tra

**Bài 1 — Dự đoán:** Với `score = 4`, đoạn `if (score >= 5) println("Dat"); println("Xong");` (hai statement riêng) sẽ in gì nếu dòng thứ hai được thụt vào dưới `if` nhưng không có ngoặc?

*Đáp án:* `Xong`. Chỉ statement thứ nhất phụ thuộc điều kiện.

**Bài 2 — Sửa nhánh:** Lấy ví dụ `registered` ở mục 4, thay thành `registered = true` và `score = 3`. Sau bản sửa có ngoặc, kết quả nào được in?

*Đáp án:* `Chua dat diem`.

**Bài 3 — Phạm vi:** Tại sao đặt `System.out.println(message);` sau khối `if` ở mục 5 lại không biên dịch? Sửa để chương trình in được message cho cả hai trường hợp điểm đạt và chưa đạt.

*Đáp án:* Biến khai báo trong khối đã ra khỏi phạm vi; khai báo trước `if` và gán ở cả `if` lẫn `else`.

**Bài 4 — Lặp:** Một bạn muốn in `Lan 0 - Xong`, `Lan 1 - Xong`, `Lan 2 - Xong`, mỗi lượt hai phần trên hai dòng. Hãy đặt lại `{ }` cho vòng `for` và dự đoán số lần in `Xong`.

*Đáp án:* Đặt cả hai `println` vào khối `for`; `Xong` được in ba lần.

**Bài 5 — Debug:** Chạy `java IndentScopeDemo 5`, rồi thử sửa điều kiện từ `score >= 5` thành `score > 5`. Điều gì thay đổi? Đó là lỗi thụt lề hay lỗi điều kiện?

*Đáp án:* Điểm 5 chuyển từ `Dat` thành `Can co gang`; đây là lỗi **logic điều kiện**, cho thấy mã thụt lề đúng vẫn có thể sai nghiệp vụ.

### Checklist cuối bài

- [ ] Giải thích được Java dựa vào `{ }` và cấu trúc cú pháp, không dựa vào độ sâu indentation để hiểu khối.
- [ ] Luôn dùng ngoặc nhọn khi viết `if`/`else` và vòng lặp.
- [ ] Nhận ra câu lệnh thứ hai không được điều khiển nếu `if` thiếu khối.
- [ ] Hiểu `else` ghép với `if` gần nhất đủ điều kiện về mặt cú pháp.
- [ ] Xác định được nơi biến cục bộ bắt đầu/kết thúc phạm vi qua dấu ngoặc.
- [ ] Phát hiện được `if (condition);` tạo nhánh rỗng ngoài ý muốn.
- [ ] Biên dịch và chạy được `IndentScopeDemo.java` trên JDK 17+ với các giá trị biên.

**Ghi nhớ:** Thụt lề là **bản đồ dành cho người đọc**, còn ngoặc nhọn `{ }` và quy tắc cú pháp là **cấu trúc mà Java thực sự thực thi**. Khi hai thứ không khớp nhau, máy vẫn chạy theo cú pháp, không theo ý định thể hiện bằng khoảng trắng.


</details>

- [Quay lại đầu trang](#back-to-top)
