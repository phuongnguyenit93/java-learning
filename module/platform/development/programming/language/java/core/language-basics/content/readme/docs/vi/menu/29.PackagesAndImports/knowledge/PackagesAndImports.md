<a id="back-to-top"></a>

# Package và import

## Menu
- [Package tổ chức tên kiểu và tránh trùng tên](#packages-and-imports-001)
- [Khai báo package và cấu trúc thư mục tương ứng](#packages-and-imports-002)
- [Tên đơn giản và tên đầy đủ của lớp](#packages-and-imports-003)
- [Import một kiểu và import theo yêu cầu bằng dấu *](#packages-and-imports-004)
- [Static import cho thành viên static](#packages-and-imports-005)
- [java.lang được import ngầm định](#packages-and-imports-006)
- [Import không tự động bao gồm package con](#packages-and-imports-007)
- [Xử lý hai lớp trùng tên và giới hạn của import](#packages-and-imports-008)
- [Phân biệt package, classpath và module ở mức định hướng](#packages-and-imports-009)

## <a id="packages-and-imports-001">Package tổ chức tên kiểu và tránh trùng tên</a>

<details>
<summary>Xem chi tiết</summary>

# Package tổ chức tên kiểu và tránh trùng tên — Java 17+

## 1. Package là không gian tên của các kiểu Java

**Package** là một **không gian tên (namespace)** và một cách nhóm các kiểu có liên quan trong ứng dụng Java: class, interface, enum, record. Khi dự án có nhiều lớp, package giúp người đọc biết lớp thuộc chức năng nào và giúp các kiểu có **tên đơn giản trùng nhau** vẫn tồn tại hợp lệ nếu chúng nằm ở những package khác nhau.

Ví dụ:

~~~text
com.example.sales.Report
com.example.support.Report
~~~

Cả hai đều là lớp có tên đơn giản `Report`, nhưng **tên đầy đủ có package khác nhau**. Vì vậy, Java phân biệt được báo cáo bán hàng với báo cáo hỗ trợ, không cần ép tất cả các class phải có tên đơn giản duy nhất trên toàn dự án.

| Khái niệm | Ví dụ | Ý nghĩa |
|---|---|---|
| Package name | `com.example.sales` | Không gian tên chứa kiểu |
| Simple name | `Report` | Tên lớp tại nơi khai báo |
| Qualified type name | `com.example.sales.Report` | Chỉ chính xác kiểu cùng package của nó |
| Another qualified name | `com.example.support.Report` | Một kiểu khác, dù simple name giống nhau |

Tên package được khai báo **bằng mã Java**, không phải tự động lấy từ tên thư mục. Các thư mục nguồn thường được sắp cho khớp namespace để xây dựng và quản lý thuận tiện.

## 2. Namespace xác định danh tính kiểu

Hai kiểu `com.example.sales.Report` và `com.example.support.Report` có cùng **simple name** `Report` nhưng tên đầy đủ khác nhau, nên là **hai kiểu riêng biệt**. Với mã nguồn:

~~~java
package com.example.sales;

public class Report {
    public String kind() { return "sales"; }
}
~~~

`package` xác định namespace, nằm trước khai báo `import` và top-level type. Theo quy ước, package được đặt tên chữ thường theo lĩnh vực hoặc tên miền đảo ngược; cấu trúc nguồn và cú pháp đầy đủ được thực hành ở bài tiếp theo.

## 3. Cây nguồn tương ứng với tên package

~~~text
project/
└── src/
    └── com/example/
        ├── sales/Report.java
        ├── support/Report.java
        └── app/Main.java
~~~

Cả hai `Report.java` cùng tồn tại nhờ hai namespace khác nhau. Tên package được quyết định bởi khai báo trong mã; **thư mục khớp package** là cách tổ chức thực tế giúp công cụ tìm nguồn. `javac -d out` tạo cây `.class` theo package trong `out`; `out` là classpath root khi chạy.

## 4. Hai kiểu cùng simple name vẫn không trùng nhau

Trong `src/com/example/support/Report.java` có thể khai báo một `public class Report` khác:

~~~java
package com.example.support;

public class Report {
    public String kind() { return "support"; }
}
~~~

Điều kiện tránh va chạm là **tên kiểu đầy đủ khác nhau**. Nếu hai top-level type có cùng tên đầy đủ trong cùng môi trường biên dịch thì không thể xem chúng là hai class riêng mang cùng tên. Hai kiểu trong ví dụ còn mang hành vi riêng: `kind()` lần lượt trả `"sales"` và `"support"`.

## 5. Dùng cả hai lớp trong mã nguồn thứ ba

~~~java
package com.example.app;

import com.example.sales.Report;

class Main {
    Report sales = new Report();
    com.example.support.Report support =
            new com.example.support.Report();
}
~~~

Import cho phép rút gọn tên **một kiểu cụ thể**; lớp còn lại dùng qualified name. Import không đổi danh tính lớp, không sao chép code, không tạo object và không thêm thư viện vào classpath. Java không hỗ trợ `import ... as ...`.

## 6. Chung tiền tố package vẫn là package khác

`com.example`, `com.example.sales` và `com.example.sales.audit` không phải cùng package chỉ vì tên lồng tiền tố. Do đó `import com.example.*;` không tự nhập `com.example.sales.Report`. Package có quan hệ tên nhưng không tự kế thừa quyền package-private. Bài về wildcard import và package con sẽ phân tích cụ thể giới hạn này.

## 7. Tổ chức nhóm kiểu theo trách nhiệm

`sales` và `support` thể hiện vùng chức năng, giúp người đọc tìm đúng kiểu. Một package không thay thế thiết kế API hay module; nó chỉ mang lại không gian tên và tham gia một số quy tắc truy cập. Chọn package theo trách nhiệm liên quan và giữ tên nhất quán thay vì dồn mọi class vào cùng một package lớn.

## 8. Package và mức truy cập có quan hệ nhưng khác nhiệm vụ

Package chủ yếu tổ chức **namespace**. Java còn dùng khái niệm **cùng package** khi kiểm tra một số quyền truy cập.

| Khai báo | Ý nghĩa cơ bản |
|---|---|
| `public class Report` | Có thể được tham chiếu từ package khác nếu đáp ứng các điều kiện truy cập/classpath/module |
| `class Helper` (không modifier, top-level) | **Package-private**: chỉ các kiểu trong cùng package được phép truy cập |
| `public void print()` | Public method khi nơi gọi có quyền truy cập tới lớp và method |
| `void internal()` (không modifier) | Package-private method |
| `private int count` | Private member; lớp top-level khác cùng package không tự được truy cập |

Ví dụ, một class `com.example.sales.InternalHelper` không khai báo `public` ở mức top-level sẽ **không tự truy cập được** từ `com.example.support`. Các tên `com.example.sales` và `com.example.sales.audit` cũng không phải **cùng package** theo quy tắc package-private.

Package không thay thế `private`, `public`, `protected` hoặc các quy tắc Java module. Đồng thời, cùng namespace không có nghĩa là “đã có cơ chế bảo mật tuyệt đối”: đây là quy tắc tổ chức kiểu và kiểm tra quyền truy cập của ngôn ngữ.

## 9. Ví dụ Java 17+ hoàn chỉnh, chạy được

Tạo **đúng ba file** theo cấu trúc thư mục ở mục 3. Mỗi lớp có package và public class riêng; hai file `Report.java` không xung đột vì tên đầy đủ khác nhau.

**File 1 — `src/com/example/sales/Report.java`:**

~~~java
package com.example.sales;

public final class Report {
    private final String title;

    public Report(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("blank sales title");
        }
        this.title = title.strip();
    }

    public String describe() {
        return "sales:" + title;
    }
}
~~~

**File 2 — `src/com/example/support/Report.java`:**

~~~java
package com.example.support;

public final class Report {
    private final String ticket;

    public Report(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("blank ticket");
        }
        this.ticket = ticket.strip();
    }

    public String describe() {
        return "support:" + ticket;
    }
}
~~~

**File 3 — `src/com/example/app/Main.java`:**

~~~java
package com.example.app;

import com.example.sales.Report;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Report sales = new Report(" Q1 ");
        com.example.support.Report support =
                new com.example.support.Report("Ticket-7");

        System.out.println("sales=" + sales.describe());
        System.out.println("support=" + support.describe());
        System.out.println("sales type=" + sales.getClass().getName());
        System.out.println("support type=" + support.getClass().getName());
        System.out.println("simple names="
                + sales.getClass().getSimpleName() + ","
                + support.getClass().getSimpleName());
        System.out.println("same class="
                + (sales.getClass() == support.getClass()));
        System.out.println("sales package="
                + sales.getClass().getPackageName());
        System.out.println("support package="
                + support.getClass().getPackageName());
    }
}
~~~

Đoạn `Main` dùng `import com.example.sales.Report;` cho sales và **fully qualified type name** cho support. Hai biến giữ hai đối tượng khác kiểu, dù hai class đều có simple name `Report`.

### Biên dịch và chạy bằng CLI

**Yêu cầu:** JDK 17 trở lên. Đứng tại thư mục dự án chứa thư mục `src`. Dùng `-d out` để biên dịch file `.class` vào package path trong `out`. Không cần Maven/Gradle, thư viện ngoài hay preview features.

**Bash (Linux/macOS):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out src/com/example/sales/Report.java src/com/example/support/Report.java src/com/example/app/Main.java
java -cp out com.example.app.Main
~~~

**PowerShell (Windows):**

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -d out .\src\com\example\sales\Report.java .\src\com\example\support\Report.java .\src\com\example\app\Main.java
java -cp out com.example.app.Main
~~~

Các file biên dịch nằm ở:

~~~text
out/
└── com/
    └── example/
        ├── sales/Report.class
        ├── support/Report.class
        └── app/Main.class
~~~

`out` là **classpath root**. Khi chạy, hãy dùng `com.example.app.Main` (tên lớp có package), không dùng `java Main` hoặc `java -cp out Main`.

### Exact expected stdout

~~~text
sales=sales:Q1
support=support:Ticket-7
sales type=com.example.sales.Report
support type=com.example.support.Report
simple names=Report,Report
same class=false
sales package=com.example.sales
support package=com.example.support
~~~

**Giải thích:** Hai kiểu có simple name giống nhau, nhưng `getClass().getName()` cho ra tên đầy đủ khác nhau, `same class=false`. `getClass().getPackageName()` xác nhận hai namespace. Mỗi lớp thực thi hành vi `describe()` của chính nó.

## 10. Các lỗi thường gặp

| Sai lầm | Vì sao sai? | Cách xử lý |
|---|---|---|
| Coi hai `Report` thuộc hai package là một kiểu | Package khác nên kiểu khác | Dùng tên đầy đủ để phân biệt |
| Tạo hai top-level `Report` trong cùng package | Trùng tên kiểu trong cùng namespace | Đổi tên kiểu hoặc chuyển package phù hợp |
| Đưa hai single-type import cho hai `Report` vào cùng file | Cạnh tranh simple name, lỗi biên dịch | Một import, kiểu kia viết tên đầy đủ |
| Nghĩ `import com.example.*;` bao gồm `com.example.sales` | Không import đệ quy các package có cùng tiền tố | Import đúng package |
| Đặt `package` sau `import` hoặc trong class | Vi phạm cú pháp compilation unit | Đặt package ở đầu file |
| Tên file `Foo.java` nhưng khai báo `public class Bar` | Sai quy tắc tên file public top-level type | Tên file `Bar.java` |
| Dùng `java Main` trong khi class là `com.example.app.Main` | Launcher không tìm đúng tên lớp | `java -cp out com.example.app.Main` |
| Đặt classpath là `out/com/example/app` | Sai classpath root của package path | Dùng `-cp out` |
| Tin rằng thư mục quyết định package bất chấp khai báo nguồn | Namespace lấy từ khai báo Java | Đồng bộ đường dẫn và tên package |
| Coi `com.example.sales` và `com.example.sales.audit` là cùng package | Tên có tiền tố chung không đồng nghĩa cùng package | Xác định đúng tên package |
| Nghĩ `import` làm JVM chạy constructor ngay | Import là cơ chế tra cứu tên lúc biên dịch | Chỉ tạo object bằng hành vi khởi tạo tương ứng |

## 11. Bài tập và đáp án

**A.** Hai kiểu `org.acme.account.User` và `org.acme.audit.User` có được tồn tại cùng một chương trình không?  
**Đáp án:** **Có**, vì thuộc các package khác nhau và có tên kiểu đầy đủ khác nhau.

**B.** Với `com.example.sales.Report`, đâu là tên đơn giản và package?  
**Đáp án:** simple name là `Report`; package là `com.example.sales`.

**C.** Một file đã `import com.example.sales.Report;` thì làm sao tạo `Report` của support?  
**Đáp án:**

~~~java
com.example.support.Report support =
        new com.example.support.Report("Ticket-7");
~~~

**D.** `import com.example.*;` có tự nhập lớp `com.example.sales.Report` không?  
**Đáp án:** **Không**. Cần import trực tiếp `com.example.sales.Report` hoặc sử dụng tên đầy đủ.

**E.** Sau khi biên dịch `com.example.app.Main` vào `out/com/example/app/Main.class`, cần chạy lệnh nào từ thư mục dự án?  
**Đáp án:**

~~~bash
java -cp out com.example.app.Main
~~~

**F.** Trong package `com.example.sales`, một top-level `class Helper` không có `public` có thể được gọi từ `com.example.support` không?  
**Đáp án:** **Không**, vì `Helper` có quyền truy cập package-private và hai package khác nhau.

## 12. Checklist và kết luận

- [ ] Hiểu package tạo **không gian tên** và nhóm các kiểu liên quan.
- [ ] Phân biệt **simple name** với **tên kiểu đầy đủ có package**.
- [ ] Giải thích được vì sao hai lớp `Report` ở hai package không xung đột.
- [ ] Đặt khai báo `package ...;` đúng vị trí và đúng cú pháp.
- [ ] Tổ chức thư mục nguồn phù hợp với tên package.
- [ ] Dùng `import` hoặc tên đầy đủ khi cần sử dụng kiểu từ package khác.
- [ ] Biết Java không có `import ... as ...` và không có quy tắc “import cuối thắng”.
- [ ] Hiểu `package.*` không bao gồm những package khác có cùng tiền tố.
- [ ] Nhận diện package-private access và phân biệt với nhiệm vụ namespace.
- [ ] Biên dịch bằng `javac -d out` và chạy bằng tên main class đầy đủ.
- [ ] Chạy cả ba file Java 17+ và đối chiếu đúng stdout.

**Kết luận:** Package tổ chức mã thành **những không gian tên riêng biệt**. `com.example.sales.Report` và `com.example.support.Report` không trùng tên kiểu đầy đủ dù cùng được gọi ngắn là `Report`. Để sử dụng đúng lớp, hãy thống nhất tên package, đường dẫn nguồn, `import`/qualified name và classpath khi biên dịch, chạy chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-002">Khai báo package và cấu trúc thư mục tương ứng</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo `package` và cấu trúc thư mục tương ứng trong Java

> **Java core — language-basics / Package và import**  
> **Yêu cầu:** JDK 17+, không dùng thư viện ngoài.  
> **Mục tiêu:** Khai báo `package` đúng vị trí trong file Java; tổ chức các tệp `.java` theo thư mục tương ứng; biên dịch bằng `javac -d`, chạy bằng tên lớp đầy đủ và classpath; phân biệt package với thư mục, import và quyền truy cập.

## 1. `package` xác định không gian tên của lớp

Một *package* nhóm các kiểu Java có liên quan và tạo không gian tên, giúp tránh trùng tên giữa các lớp của ứng dụng. Ví dụ:

~~~java
package vn.learn.demo;

public final class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
~~~

Ở đây tên lớp đầy đủ (*fully qualified name*, FQN) là `vn.learn.demo.Main` chứ không chỉ `Main`. Khai báo `package vn.learn.demo;` có dấu chấm giữa các thành phần và **kết thúc bằng dấu chấm phẩy**.

Mỗi tệp `.java` có tối đa **một** khai báo `package`. Nếu không có khai báo này, các top-level type trong tệp nằm trong *unnamed package* (thường gọi là *default package*). Điều này tiện cho thử nghiệm rất nhỏ nhưng không được khuyến khích cho chương trình nhiều tệp hoặc cần tái sử dụng từ các package có tên.

**Tên package phân biệt chữ hoa/chữ thường.** Theo thông lệ đặt tên bằng chữ thường, ví dụ `vn.learn.demo` hoặc theo tên miền đảo ngược `com.example.app`. Không được sử dụng từ khóa Java làm một thành phần tên package.

## 2. Thứ tự `package`, `import` và khai báo kiểu

Trong tệp Java có package, bố cục phổ biến như sau:

~~~java
// Có thể viết comment và khoảng trắng ở đây.
package vn.learn.demo;

import java.util.Arrays;
import vn.learn.demo.tools.Counter;

public final class Main {
    public static void main(String[] args) {
        System.out.println(Arrays.toString(new int[] {1, 2}));
    }
}
~~~

Quy tắc cần nhớ:

1. `package ...;` là khai báo package của compilation unit, đặt **trước mọi khai báo `import` và top-level type**; comment/khoảng trắng có thể xuất hiện trước.
2. Nếu cần `import`, chúng đặt **sau `package`** và **trước các khai báo lớp/interface/enum/record**.
3. Một tệp có thể chứa các top-level type phù hợp, nhưng khi có `public class Main` trong cách tổ chức thông thường, lưu tệp thành `Main.java`.
4. `import` không đổi package chứa `Main`; nó chỉ giúp dùng tên ngắn của kiểu hoặc thành viên static được nhập.
5. `java.lang` được nhập tự động cho các kiểu quen thuộc như `String` và `System`.

**Sai thứ tự:**

~~~java
// SAI — không thể đặt import trước khai báo package:
// import java.util.List;
// package vn.learn.demo;
~~~

Tên trong `package` không phải chuỗi ký tự có dấu ngoặc kép. Viết `package "vn.learn.demo";` sẽ không biên dịch.

## 3. Cấu trúc thư mục nguồn tương ứng

Thói quen chuẩn là đổi các dấu chấm của tên package thành dấu phân cách thư mục. Với `vn.learn.demo`, tệp `Main.java` nên nằm ở:

`src/vn/learn/demo/Main.java`

Ví dụ cấu trúc nguồn và đầu ra biên dịch:

~~~text
project/
├── src/
│   └── vn/
│       └── learn/
│           └── demo/
│               ├── Main.java
│               ├── Greeter.java
│               └── tools/
│                   └── Counter.java
└── out/                      (thư mục chứa .class sau biên dịch)
~~~

| Khai báo trong tệp | Đường dẫn nguồn được khuyến nghị |
| --- | --- |
| `package vn.learn.demo;` | `src/vn/learn/demo/Main.java` |
| `package vn.learn.demo;` | `src/vn/learn/demo/Greeter.java` |
| `package vn.learn.demo.tools;` | `src/vn/learn/demo/tools/Counter.java` |

`src` là **thư mục gốc mã nguồn** trong ví dụ, không phải thành phần của tên package. `Main.java` khai báo `vn.learn.demo` chứ không phải `src.vn.learn.demo`.

**Đây là quy ước tổ chức quan trọng của công cụ**, không phải quy tắc cú pháp bắt buộc khiến mọi tệp Java ở sai thư mục đều không thể được `javac` biên dịch khi chỉ rõ đường dẫn tệp. Tuy vậy, tuân theo cấu trúc này giúp trình biên dịch, IDE và build tool tìm đúng mã nguồn, tránh nhầm lẫn khi dự án lớn.

## 4. Ví dụ Java 17+ với ba tệp thuộc hai package

Tạo đúng ba tệp sau dưới thư mục `project`. Đây là **một ví dụ runnable thống nhất**, không cần bất kỳ thư viện ngoài nào.

**Tệp 1 — `src/vn/learn/demo/Greeter.java`**

~~~java
package vn.learn.demo;

// Không ghi public: lớp top-level chỉ truy cập trực tiếp trong package.
final class Greeter {
    private Greeter() {
    }

    static String hello(String name) {
        return "Xin chào, " + name;
    }
}
~~~

**Tệp 2 — `src/vn/learn/demo/tools/Counter.java`**

~~~java
package vn.learn.demo.tools;

public final class Counter {
    private int value;

    public Counter(int initial) {
        this.value = initial;
    }

    public int value() {
        return value;
    }

    public void add(int amount) {
        value += amount;
    }
}
~~~

**Tệp 3 — `src/vn/learn/demo/Main.java`**

~~~java
package vn.learn.demo;

import vn.learn.demo.tools.Counter;

public final class Main {
    public static void main(String[] args) {
        System.out.println("mainPackage=" + Main.class.getPackageName());
        System.out.println("greeting=" + Greeter.hello("Lan"));

        Counter counter = new Counter(2);
        System.out.println("start=" + counter.value());
        counter.add(3);
        System.out.println("afterAdd=" + counter.value());

        System.out.println("toolsPackage=" + Counter.class.getPackageName());
        System.out.println("counterClass=" + Counter.class.getName());
    }
}
~~~

Tại sao ví dụ hoạt động?

- `Main` và `Greeter` khai báo **cùng** `package vn.learn.demo;` nên `Main` dùng được lớp package-private `Greeter` mà không cần import.
- `Counter` ở package **khác** là `vn.learn.demo.tools`, nên `Main` phải sử dụng tên đầy đủ hoặc `import` hợp lệ. `Counter` và constructor/method dùng từ ngoài package đều được khai báo `public`.
- `getPackageName()` cho thấy tên package tại runtime; `Counter.class.getName()` cho tên lớp đầy đủ.
- `private int value` của `Counter` không được truy cập trực tiếp từ `Main`; `value()` và `add(...)` cung cấp API.

## 5. Biên dịch đúng thư mục đầu ra và chạy bằng classpath

Chạy lệnh từ thư mục **`project`** chứa `src`. Trước hết tạo thư mục đầu ra `out` nếu chưa tồn tại.

Với **Bash/Linux/macOS**:

~~~bash
mkdir -p out
javac -encoding UTF-8 -d out src/vn/learn/demo/Greeter.java src/vn/learn/demo/tools/Counter.java src/vn/learn/demo/Main.java
java -cp out vn.learn.demo.Main
~~~

Với **Windows PowerShell**:

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out src/vn/learn/demo/Greeter.java src/vn/learn/demo/tools/Counter.java src/vn/learn/demo/Main.java
java -cp out vn.learn.demo.Main
~~~

**Expected stdout của lệnh `java -cp out vn.learn.demo.Main` (chính xác):**

~~~text
mainPackage=vn.learn.demo
greeting=Xin chào, Lan
start=2
afterAdd=5
toolsPackage=vn.learn.demo.tools
counterClass=vn.learn.demo.tools.Counter
~~~

Lệnh `javac` biên dịch thành các tệp `.class` dưới `out`:

~~~text
out/
└── vn/
    └── learn/
        └── demo/
            ├── Main.class
            ├── Greeter.class
            └── tools/
                └── Counter.class
~~~

**Ý nghĩa lệnh:**

| Thành phần | Công dụng |
| --- | --- |
| `javac` | Biên dịch nguồn Java thành bytecode `.class` |
| `-encoding UTF-8` | Đọc nguồn có tiếng Việt đúng bộ mã |
| `-d out` | Chọn **gốc thư mục đầu ra**, nơi `javac` tạo nhánh package tương ứng |
| `java -cp out` | Đưa gốc thư mục lớp biên dịch vào classpath |
| `vn.learn.demo.Main` | Tên lớp đầy đủ chứa `main`, không phải đường dẫn tệp |

Với `javac -d out`, thư mục `out` cần tồn tại; trình biên dịch tạo các **thư mục package bên trong** khi cần. Khi chạy, classpath phải trỏ đến **gốc phía trên `vn`** (`out`), không trỏ trực tiếp đến `out/vn/learn/demo`. Khi dùng `java -cp out ...`, không truyền đuôi `.class`.

## 6. Package con không tự thuộc cùng package về quyền truy cập

Tên `vn.learn.demo.tools` có vẻ nằm “dưới” `vn.learn.demo`, nhưng xét quyền truy cập, Java xem chúng là **hai package khác nhau**.

~~~java
// Giả sử đặt trong package vn.learn.demo:
class InternalHelper {
    static String code() {
        return "internal";
    }
}
~~~

Lớp ở `vn.learn.demo.tools` **không được tự động** gọi `InternalHelper.code()` vì `InternalHelper` là lớp top-level package-private của một package khác.

| Trường hợp | Kết quả |
| --- | --- |
| Hai lớp khai báo `package vn.learn.demo;` | Cùng package, có thể truy cập thành viên package-private phù hợp |
| Một lớp `vn.learn.demo`, một lớp `vn.learn.demo.tools` | Khác package, không tự có quyền package-private |
| `import vn.learn.demo.tools.Counter;` | Cho phép dùng tên ngắn `Counter` nếu kiểu và thành viên được truy cập hợp lệ |
| `import vn.learn.demo.*;` | Không tự nhập các kiểu trong `vn.learn.demo.tools` |
| Field `private` của `Counter` | Không thể truy cập trực tiếp từ lớp ngoài không có quyền |

**Import không cấp thêm quyền truy cập.** Ví dụ `import vn.learn.demo.tools.Counter;` không cho phép `Main` đọc `counter.value` vì field vẫn `private`.

Một lớp `public` và constructor `public` có thể được dùng xuyên package khi môi trường/module cho phép. Với các chương trình nhập môn không có `module-info.java` như ví dụ trên, classpath là cơ chế đơn giản nhất.

## 7. Lỗi phổ biến về package, đường dẫn và lệnh chạy

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `Could not find or load main class Main` | `Main` thuộc package có tên, không phải unnamed package | Dùng `java -cp out vn.learn.demo.Main` |
| `ClassNotFoundException: vn.learn.demo.Main` | Classpath sai gốc hoặc chưa biên dịch lớp | Kiểm tra `out/vn/learn/demo/Main.class`, dùng `-cp out` |
| `package ... does not exist` khi biên dịch | Chưa đưa mã nguồn/dependency hoặc classpath phù hợp vào `javac` | Biên dịch đủ các tệp, kiểm tra tên package |
| `cannot find symbol` cho `Counter` | Thiếu import hoặc dùng sai tên kiểu | `import vn.learn.demo.tools.Counter;` hoặc tên đầy đủ |
| `... is not public; cannot be accessed from outside package` | Lớp hoặc thành viên package-private được gọi khác package | Thiết kế API public hợp lệ hoặc đặt mã nội bộ cùng package |
| `class Main is public, should be declared in a file named Main.java` | Tên file không khớp public top-level class | Đặt đúng `Main.java` |
| Chạy `java -cp out/vn/learn/demo Main` | Chọn nhầm **nhánh package** làm classpath root | Chọn gốc `out` và tên lớp đầy đủ |
| `mkdir out` chưa chạy, `javac -d out` lỗi | Thư mục đầu ra chưa tồn tại | Tạo `out` trước khi biên dịch |
| Chuyển `Main.java` sang package khác nhưng giữ `java ...vn.learn.demo.Main` | Tên package trong nguồn, thư mục output và FQN không thống nhất | Cập nhật khai báo, vị trí nguồn và lệnh chạy |

Java phân biệt chữ hoa/chữ thường trong định danh tên package và class. Đặc biệt trên hệ điều hành có filesystem phân biệt hoa/thường, `Demo` và `demo` là tên khác nhau.

**Đừng sửa bằng cách xóa bừa khai báo `package`.** Khi lỗi nằm ở classpath hoặc cây thư mục, việc bỏ `package` thay đổi tên đầy đủ và cấu trúc của cả chương trình.

## 8. Checklist thực hành và bài tập

**Checklist chạy chương trình nhiều package:**

1. Mỗi tệp khai báo đúng `package ...;` trước `import` và lớp.
2. Cây `src` phản ánh tên package theo từng phần, ví dụ `vn/learn/demo`.
3. File `Main.java` chứa `public class Main` và phương thức `main` hợp lệ.
4. Các kiểu dùng xuyên package có quyền truy cập phù hợp; `import` không thay thế `public`.
5. Tạo thư mục `out`, dùng `javac -d out` biên dịch đầy đủ mã nguồn.
6. Chạy `java -cp out vn.learn.demo.Main` từ gốc project và kiểm tra đúng stdout.

**Bài 1.** Tạo `src/com/example/hello/Main.java` có `package com.example.hello;`. Biên dịch bằng `javac -d out` và chạy bằng FQN tương ứng.

**Bài 2.** Tạo thêm `src/com/example/hello/Message.java` cùng package; cho lớp `Message` không ghi `public` và gọi phương thức package-private từ `Main`.

**Bài 3.** Chuyển `Message` sang `com.example.hello.util`. Giải thích vì sao giờ `Main` cần kiểu có thể truy cập từ package khác và có thể cần `import`.

**Bài 4.** Cố chạy `java -cp out Main` sau khi có khai báo package. Đọc lỗi, rồi sửa thành tên lớp đầy đủ.

**Bài 5.** Đổi `package vn.learn.demo.tools;` trong `Counter.java` thành một package khác. Cập nhật đường dẫn nguồn, `import` và lệnh chạy tương ứng; dự đoán cây output `.class`.

### Ghi nhớ

**`package` xác định tên đầy đủ của kiểu; cấu trúc nguồn `src/a/b/C.java` nên khớp `package a.b;`.** Dùng `javac -d out` để tổ chức các `.class` theo package, rồi chạy bằng `java -cp out a.b.C`. `import` chỉ giúp dùng tên ngắn, không thay đổi package hoặc quyền truy cập; package con vẫn là một package khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-003">Tên đơn giản và tên đầy đủ của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tên đơn giản và tên đầy đủ của lớp trong Java

## 1. Tên đơn giản (*simple name*) và tên đầy đủ (*fully qualified name*)

Java tổ chức các lớp, interface, enum, record và những kiểu khác trong các **package**. Với một kiểu top-level, có hai cách gọi tên phổ biến:

- **Tên đơn giản** (*simple name*): tên khai báo ngay sau `class`, `interface`, `enum`, hoặc `record`; ví dụ `ArrayList`, `String`, `Student`.
- **Tên đầy đủ** (*fully qualified name*, trong ngữ cảnh bài này là tên có package): tên package cộng dấu chấm và tên kiểu; ví dụ `java.util.ArrayList`, `java.lang.String`, `com.example.model.Student`.

```java
// Tên đơn giản:
ArrayList<String> names;

// Tên đầy đủ:
java.util.ArrayList<String> others;
```

Hai cách có thể chỉ **cùng một kiểu**, nếu tên đơn giản `ArrayList` được Java phân giải đúng thành `java.util.ArrayList` (thường nhờ `import`).

Một **package** không phải đối tượng và cũng không phải nơi giữ trạng thái runtime; nó tạo không gian tên để tổ chức các kiểu và tránh va chạm tên. Theo quy ước Java, tên package thường là chữ thường, chẳng hạn `com.example.app`, còn tên lớp là PascalCase như `OrderService`.

**Phân biệt:** tên đầy đủ theo dấu chấm trong mã nguồn Java khác tên tệp `.java` hoặc tên lớp dạng nhị phân (*binary name*) cho kiểu lồng nhau. Với `java.util.Map.Entry`, tên canonical trong Java là `java.util.Map.Entry`, trong khi `Class.getName()` của kiểu này trả `java.util.Map$Entry`.

## 2. Package, khai báo lớp và vị trí tệp

Ví dụ:

```java
package com.example.model;

public class Student {
    private final String name;

    public Student(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
```

Đoạn trên là nội dung đầy đủ của **`Student.java`**. Tên đơn giản của lớp là `Student`; tên đầy đủ là `com.example.model.Student`. Trong một source file thông thường, `package` (nếu có) đứng đầu sau các nhận xét, trước các `import` và khai báo kiểu top-level.

Quy tắc cơ bản:

- Một kiểu top-level `public class Student` phải được khai báo trong source file tên **`Student.java`**.
- `package com.example.model;` xác định package theo **khai báo trong mã nguồn**, không tự được quyết định bởi tên thư mục.
- Bố cục thư mục tương ứng với package, như `src/com/example/model/Student.java`, là quy ước và cách tổ chức thông thường giúp công cụ biên dịch, build và classpath tìm thấy lớp đúng vị trí.
- Biên dịch bằng `javac -d out ...` đặt file lớp vào cấu trúc thư mục theo package, chẳng hạn `out/com/example/model/Student.class`.
- Chạy một lớp có `main` trong package bằng **tên đầy đủ của lớp** như `java -cp out com.example.app.Main`; `java -cp out Main` sẽ không tìm đúng tên lớp đó.

Không nên nhầm *tên đơn giản* của lớp `Student` với *tên biến* `student` khi tạo `Student student = new Student("An");`.

## 3. `import` cho phép viết tên đơn giản

Nếu tệp nguồn nằm ở package khác với lớp muốn dùng, ta thường thêm một hoặc vài khai báo `import`:

```java
package com.example.app;

import java.util.ArrayList;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        words.add("Java");
        System.out.println(words); // [Java]
    }
}
```

Trong ví dụ này, `List` là tên đơn giản đại diện cho `java.util.List`; `ArrayList` đại diện cho `java.util.ArrayList`.

**`import` không tạo đối tượng, không sao chép mã và không nạp lớp theo nghĩa chạy chương trình.** Nó là khai báo giúp trình biên dịch nhận diện tên ngắn tại nơi có hiệu lực. Không cần `import` nếu viết tên đầy đủ ngay trong mã:

```java
java.util.List<String> words = new java.util.ArrayList<>();
words.add("Java");
```

Cả hai cách viết đều dùng đúng các kiểu từ `java.util`.

Các dạng hay gặp:

| Cú pháp | Ý nghĩa |
| --- | --- |
| `import java.util.List;` | Import một kiểu cụ thể; cho phép dùng `List` như tên đơn giản khi không bị xung đột |
| `import java.util.*;` | Import theo yêu cầu các kiểu truy cập được trực tiếp thuộc package `java.util` |
| `import static java.lang.Math.max;` | Cho phép gọi thành viên static `max` bằng tên ngắn |
| `import static java.lang.Math.*;` | Static import theo yêu cầu các thành viên static truy cập được của `Math` |
| `java.sql.Date` | Dùng tên đầy đủ tại vị trí cần; không phụ thuộc import `Date` của package nào |

`import java.util.*;` **không import các package con** của `java.util`, chẳng hạn `java.util.concurrent`. Ký hiệu `*` cũng không có nghĩa tự import toàn bộ thư viện Java.

Các kiểu trong `java.lang` như `String`, `Object`, `System`, `Math` được import **ngầm định theo yêu cầu**; các kiểu thuộc **cùng package** cũng có thể được gọi theo tên đơn giản khi không có xung đột. Vì vậy không cần viết `import java.lang.String;` trong chương trình thông thường.

## 4. Khi tên đơn giản không đủ xác định kiểu

`java.util.Date` và `java.sql.Date` có simple name `Date` giống nhau nhưng là hai kiểu khác nhau. Trong một compilation unit, có thể import một kiểu và viết tên đầy đủ cho kiểu còn lại:

~~~java
import java.util.Date;

class Dates {
    Date general = new Date(0L);
    java.sql.Date database = new java.sql.Date(0L);
}
~~~

Hai **single-type imports** cho hai kiểu khác nhau cùng simple name gây lỗi; hai wildcard imports có thể khiến tên ngắn mơ hồ ở nơi dùng. Bài về xung đột import phân tích sâu các trường hợp này. Ở đây chỉ cần nhớ tên đầy đủ giúp xác định kiểu chính xác.

## 5. Quy tắc tra cứu tên: vài tình huống quan trọng

Trình biên dịch phân giải một tên kiểu đơn giản dựa trên **ngữ cảnh cú pháp và các khai báo/import đang có**, không đơn giản tìm một lớp ngẫu nhiên trùng tên trong toàn bộ classpath.

Các tình huống thường gặp:

1. **Kiểu đã được khai báo hoặc nằm trong phạm vi phù hợp** có thể được nhắc bằng tên đơn giản theo quy tắc scope và khả năng truy cập.
2. **Kiểu trong cùng package** có thể được dùng không cần import.
3. **Single-type import** như `import java.util.List;` cung cấp tên ngắn cho một kiểu cụ thể; import trùng tên có thể gây lỗi biên dịch.
4. **On-demand import** như `import java.util.*;` cung cấp các kiểu khi cần; nếu có nhiều ứng viên với cùng tên đơn giản, có thể phải chỉ rõ tên đầy đủ.
5. **`java.lang`** được import ngầm; tuy nhiên vẫn có thể có những tên trùng với kiểu do chương trình tự khai báo trong ngữ cảnh thích hợp.

Đây là cách đọc thực dụng, **không thay thế toàn bộ thứ tự tra cứu tên** trong Java Language Specification. Khi một tên đơn giản đang trỏ tới kiểu không mong muốn, hãy kiểm tra package hiện tại, các khai báo cùng scope, import cụ thể và wildcard imports; sau đó dùng tên đầy đủ để xác nhận ý định.

**Phân biệt quyền truy cập với việc import:** một lớp `package-private` không tự trở thành truy cập được từ package khác chỉ vì có `import`. Import không vượt qua giới hạn `public`, `protected`, package access hay `private`.

## 6. Ví dụ Java 17+ chạy độc lập, chứng minh cả hai cách gọi

Tạo thư mục và tệp **`src/study/NameResolutionDemo.java`** theo nội dung:

```java
package study;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import static java.lang.Math.max;
import static java.lang.Math.PI;

public class NameResolutionDemo {
    public static void main(String[] args) {
        // List và ArrayList là tên đơn giản nhờ import.
        List<String> names = new ArrayList<>();
        names.add("An");
        names.add("Binh");
        System.out.println("Names: " + names);

        // java.util.ArrayList là tên đầy đủ cho cùng kiểu.
        java.util.List<String> other = new java.util.ArrayList<>();
        other.add("Java");
        System.out.println("Other: " + other);

        // Hai lớp Date khác package, tránh trùng tên đơn giản.
        Date utilDate = new Date(0L);
        java.sql.Date sqlDate = new java.sql.Date(0L);
        System.out.println("Util Date class: " + utilDate.getClass().getName());
        System.out.println("SQL Date class: " + sqlDate.getClass().getName());

        // Kiểu lồng nhau: Map.Entry có tên canonical nhiều thành phần.
        Map.Entry<String, Integer> entry = Map.entry("apple", 2);
        System.out.println("Entry: " + entry.getKey() + "=" + entry.getValue());

        // java.lang tự có import ngầm; max và PI từ static import.
        System.out.println("Max: " + max(3, 5));
        System.out.println("PI > 3: " + (PI > 3.0));
        System.out.println("Simple class: " + names.getClass().getSimpleName());
        System.out.println("Full class: " + names.getClass().getName());
    }
}
```

**Biên dịch và chạy** (JDK 17+, đứng ở thư mục gốc của ví dụ, có tệp `src/study/NameResolutionDemo.java`):

**Tạo sẵn thư mục gốc `out` trước lệnh `javac -d out` nếu chưa tồn tại**: Bash/Linux/macOS chạy `mkdir -p out`; Windows PowerShell chạy `New-Item -ItemType Directory -Force out | Out-Null`. `javac` có thể tạo các thư mục *bên trong* `out` theo package nhưng không tự tạo thư mục đích `out` còn thiếu.

```bash
javac -encoding UTF-8 -d out src/study/NameResolutionDemo.java
java -cp out study.NameResolutionDemo
```

Hai dòng lệnh giống nhau trong PowerShell, CMD, bash hoặc zsh. Thư mục `out` là nơi `javac` tạo cây `study/NameResolutionDemo.class`; có thể để trình biên dịch tự tạo cấu trúc thư mục bên trong `out`. Dùng `study.NameResolutionDemo` vì lớp `NameResolutionDemo` nằm trong package `study`.

**Stdout chính xác** (khi biên dịch thành công, `javac` không in gì):

```text
Names: [An, Binh]
Other: [Java]
Util Date class: java.util.Date
SQL Date class: java.sql.Date
Entry: apple=2
Max: 5
PI > 3: true
Simple class: ArrayList
Full class: java.util.ArrayList
```

Các kết quả `getClass().getSimpleName()` và `getClass().getName()` ở đây đọc từ đối tượng `ArrayList` thực tế. Đừng dùng `getSimpleName()` làm cơ chế xác minh duy nhất khi có hai lớp khác package cùng tên, vì thông tin package bị lược bỏ.

### Tên của kiểu lồng nhau và tên nhị phân

Ví dụ `Map.Entry` là một interface lồng nhau có tên đơn giản `Entry`; trong mã Java, có thể viết `java.util.Map.Entry` mà không phải trực tiếp nhắc tới ký tự `$`.

```java
System.out.println(java.util.Map.Entry.class.getCanonicalName());
// java.util.Map.Entry

System.out.println(java.util.Map.Entry.class.getName());
// java.util.Map$Entry
```

Đoạn này minh họa trong phương thức; chương trình hoàn chỉnh ở trên. `getCanonicalName()` và `getName()` khác nhau khi xử lý tên một số kiểu lồng nhau. Nếu dùng các API reflection như `Class.forName`, cần hiểu định dạng tên **nhị phân** mà API yêu cầu.

## 7. Không nên nhầm `import` với `classpath` hoặc module

`import` chỉ giải quyết cách **viết tên kiểu** trong file source. Muốn biên dịch/chạy được, trình biên dịch và JVM còn phải tìm thấy định nghĩa lớp trên classpath hoặc module path tương ứng.

- **`package`** xác định không gian tên của kiểu; chỉ khai báo `package study;` không tự cài đặt thư viện hoặc tải một `.jar` bên ngoài.
- **`import`** giúp viết tên đơn giản; không chỉ định nơi tìm `.class`, không thêm thư viện vào classpath.
- **`classpath`**, ví dụ `javac -cp libs/example.jar ...`, chỉ vị trí phụ thuộc khi biên dịch. `java -cp ...` chỉ vị trí lớp khi chạy.
- Trong ứng dụng dùng **Java Platform Module System** (JPMS), khả năng sử dụng kiểu còn chịu quy tắc module như `requires` và `exports`; import không thay thế khai báo module.
- Các kiểu trong **unnamed package** (không khai báo `package`) không thể được mã trong named package import theo cách thông thường; dự án thực tế nên dùng package có tên.

Nếu báo lỗi `cannot find symbol`, `package ... does not exist` hoặc `ClassNotFoundException`, hãy phân biệt vấn đề **phân giải tên trong mã** với vấn đề **thiếu lớp/thư viện khi biên dịch hoặc chạy**.

## 8. Các nhầm lẫn phổ biến

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| `List` và `java.util.List` luôn là hai kiểu khác nhau | Nếu `List` được phân giải đến `java.util.List`, chúng là cùng một kiểu |
| Phải import để dùng một lớp ngoài package | Có thể viết tên đầy đủ mà không import |
| `import java.util.*;` tự import luôn `java.util.concurrent.*` | Wildcard import chỉ áp dụng cho một package/kiểu theo quy tắc cụ thể, không mở rộng vào subpackage |
| Hai lớp tên `Date` của hai package tự động tương đương | `java.util.Date` và `java.sql.Date` là hai lớp khác nhau |
| Import giúp dùng lớp `private` hoặc package-private từ bất cứ đâu | Quy tắc truy cập vẫn có hiệu lực |
| Đã import nghĩa là JVM chắc chắn tìm thấy lớp lúc chạy | Dependency phải có trên classpath/module path tương ứng |
| `Class.getName()` luôn bằng tên canonical viết trong Java | Kiểu lồng nhau có thể dùng `$` trong binary name |
| Chạy lớp trong package bằng tên đơn giản sau `java` | Dùng tên đầy đủ như `java -cp out study.NameResolutionDemo` |

**Ghi nhớ:** tên đơn giản giúp mã gọn; tên đầy đủ giúp xác định chính xác một kiểu. `package` tổ chức không gian tên, `import` hỗ trợ phân giải tên ngắn, còn classpath/module path quyết định nơi tìm định nghĩa lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-004">Import một kiểu và import theo yêu cầu bằng dấu *</a>

<details>
<summary>Xem chi tiết</summary>

# Import một kiểu và import theo yêu cầu bằng dấu `*` — Java 17+

## 1. `import` giải quyết vấn đề gì?

Mỗi kiểu Java thuộc một package (trừ kiểu ở *unnamed package*). Khi sử dụng kiểu nằm ngoài package hiện tại, ta có thể viết **tên đầy đủ** (*fully qualified name*) hoặc dùng `import` để viết **tên ngắn** trong mã nguồn.

~~~java
java.util.List<String> names = java.util.List.of("An", "Binh");
~~~

Tương đương khi khai báo import phù hợp:

~~~java
import java.util.List;

// Trong một lớp:
List<String> names = List.of("An", "Binh");
~~~

`import` giúp **tra cứu tên lúc biên dịch**, không tạo object, không tự chạy mã của lớp được import và không sao chép mã nguồn thư viện vào file.

## 2. Hai cú pháp cần nhận diện

| Cú pháp | Tên gọi | Ý nghĩa |
|---|---|---|
| `import java.util.List;` | **Single-type-import declaration** | Cho phép dùng tên ngắn `List` cho **một kiểu cụ thể** |
| `import java.util.*;` | **Type-import-on-demand declaration** | Cho phép dùng tên ngắn của **các kiểu truy cập được** trong package `java.util` khi cần phân giải tên |
| `java.util.List.of(...)` | Tên kiểu đầy đủ, không phải import | Không cần khai báo import cho `List` |
| `import static java.lang.Math.PI;` | Single-static-import | Import **thành viên static**; khác với import kiểu |
| `import static java.lang.Math.*;` | Static-import-on-demand | Import thành viên static theo yêu cầu; không phải `import java.lang.Math.*;` |

Dấu `*` trong `import java.util.*;` **không có nghĩa** sao chép hay khởi tạo toàn bộ các lớp trong package. Trình biên dịch chỉ sử dụng các khai báo import để phân giải những tên xuất hiện trong mã.

Ở Java 17, chỉ có ký hiệu `*` **nguyên vẹn** ở cuối khai báo import-on-demand: không có cú pháp kiểu `import java.util.L*;` để lọc tiền tố.

## 3. Dấu `*` không tìm kiếm đệ quy package con

`import java.util.*;` cho phép dùng các kiểu truy cập được trực tiếp từ `java.util` như `List` và `Map`, nhưng không tự đưa `java.util.concurrent.TimeUnit` vào phạm vi tên ngắn:

~~~java
long seconds = java.util.concurrent.TimeUnit.MINUTES.toSeconds(1); // 60
~~~

Muốn dùng `TimeUnit` không có tiền tố, hãy khai báo `import java.util.concurrent.TimeUnit;`. Bài về package con phân tích thêm các kiểu `AtomicInteger`, `Method` và sự khác biệt giữa package con với kiểu lồng nhau.

## 4. Khi nào dùng tên ngắn và tên đầy đủ?

~~~java
import java.util.List;

List<String> a = List.of("A");
java.util.List<String> b = java.util.List.of("B");
~~~

Cả hai đều sử dụng kiểu `java.util.List`.

- Dùng **single-type import** khi chỉ cần một vài kiểu: dễ nhận biết chính xác dependency.
- Dùng **import-on-demand** khi làm việc với nhiều kiểu trong cùng package: ít dòng import hơn nhưng khó thấy ngay tất cả kiểu đang dùng.
- Dùng **tên đầy đủ** khi cần nói rõ kiểu nào giữa các kiểu trùng tên, hoặc ở chỗ chỉ xuất hiện một lần.
- IDE thường tự thêm, gộp hoặc sắp xếp import; đây là lựa chọn trình bày mã, không làm thay đổi hành vi bản thân kiểu được sử dụng.

**Cả hai dạng import đều không làm thay đổi tính năng của `List` hoặc cấu trúc của object**. Khác biệt chủ yếu ở việc phân giải tên.

## 5. Khi hai package có kiểu cùng tên

~~~java
import java.util.List;
import java.awt.*;

// Trong lớp:
List<String> names = List.of("An"); // java.util.List
~~~

Single-type import cụ thể xác định `List` dù wildcard còn có `java.awt.List`. Với hai wildcard `java.util.*` và `java.awt.*`, tên `List` có thể trở nên mơ hồ; hai single-type imports khác kiểu cùng tên đơn giản thì gây lỗi ngay tại khai báo import. Java không có quy tắc “import cuối thắng”. Dùng qualified name khi phải nói rõ kiểu; bài xử lý hai lớp trùng tên đi sâu vào mức ưu tiên.

## 6. Import kiểu lồng nhau và `java.lang`

Một kiểu lồng (*member type*) có thể import trực tiếp:

~~~java
import java.util.Map.Entry;

// Trong lớp:
Entry<String, Integer> item =
        java.util.Map.entry("An", 10);
~~~

`Entry` là kiểu lồng của `Map`; ở đây `Map.entry(...)` được viết bằng tên đầy đủ của `Map` vì ví dụ chỉ import `Entry`.

Một số kiểu thông dụng như `String`, `System`, `Integer`, `Math` thuộc `java.lang`, và các kiểu truy cập được trong `java.lang` được Java **import-on-demand ngầm định**; thường không phải viết `import java.lang.String;`.

Kiểu được khai báo trong **cùng package** cũng có thể dùng tên ngắn khi không bị che khuất/xung đột theo quy tắc của Java.

## 7. Vị trí đặt `package` và `import`

Thứ tự thường thấy của một file `.java`:

~~~java
package demo;

import java.util.List;
import java.time.*;

public class Example {
    // ...
}
~~~

1. Khai báo `package` (nếu có), sau các chú thích và khoảng trắng.
2. Các khai báo `import`, trước mọi top-level type declaration.
3. Các khai báo `class`, `record`, `interface`, `enum`... của file.

Không đặt `import` bên trong thân một phương thức hoặc sau một khai báo top-level class. Dấu `;` là bắt buộc cuối mỗi khai báo `package`/`import`.

Một file chỉ được có **tối đa một khai báo package**. Tên package không phải là đường dẫn file tự động do `import` tạo; lúc chạy, classpath và tên lớp đầy đủ vẫn phải phù hợp với file `.class` được biên dịch.

## 8. Chương trình Java 17+ chạy độc lập

Tạo một file tên **`ImportKindsDemo.java`** trong thư mục làm việc, với toàn bộ nội dung:

~~~java
package demo;

import java.util.List;       // Import đúng một kiểu.
import java.util.*;          // Các kiểu khác trong java.util khi cần.
import java.time.*;          // Package khác, import-on-demand riêng.
import java.util.Map.Entry;  // Kiểu lồng cụ thể.

public final class ImportKindsDemo {
    private ImportKindsDemo() {}

    public static void main(String[] args) {
        List<String> names = List.of("An", "Binh");
        ArrayList<String> copy = new ArrayList<>(names);
        copy.add("Chi");

        LocalDate date = LocalDate.of(2026, 10, 16);
        Map<String, Integer> scores = Map.of(
                "An", 10, "Binh", 20);
        Entry<String, Integer> pair = Map.entry("Chi", 30);

        System.out.println("names=" + names);
        System.out.println("copy=" + copy);
        System.out.println("date=" + date);
        System.out.println("score An=" + scores.get("An"));
        System.out.println("entry=" + pair.getKey() + ":" + pair.getValue());

        System.out.println("max=" + Collections.max(copy));
        System.out.println("length=" + "Java".length());
        System.out.println("qualified="
                + java.util.List.of("X", "Y").size());
        System.out.println("time unit="
                + java.util.concurrent.TimeUnit.MINUTES.toSeconds(2));
    }
}
~~~

Cách đọc các dòng import: `List` được chọn qua khai báo cụ thể; `ArrayList`, `Map`, `Collections` qua `java.util.*`; `LocalDate` qua `java.time.*`; `Entry` qua import kiểu lồng; `String` là `java.lang.String` được biết ngầm định; `TimeUnit` được dùng bằng **tên đầy đủ**, không cần import `java.util.concurrent`.

**Biên dịch** từ thư mục chứa `ImportKindsDemo.java`. Tùy chọn `-d .` đặt file `.class` vào thư mục package phù hợp (`demo/`); không bắt buộc sắp sẵn thư mục nguồn `demo` để chạy ví dụ đơn file này.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 -d . ImportKindsDemo.java
java -cp . demo.ImportKindsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 -d . .\ImportKindsDemo.java
java -cp . demo.ImportKindsDemo
~~~

### Exact expected stdout

~~~text
names=[An, Binh]
copy=[An, Binh, Chi]
date=2026-10-16
score An=10
entry=Chi:30
max=Chi
length=4
qualified=2
time unit=120
~~~

Đầu ra không phụ thuộc thứ tự duyệt `Map.of`, vì chương trình **chỉ lấy phần tử theo khóa** `"An"`, không in trực tiếp toàn bộ map. Không cần `--enable-preview`.

## 9. Lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| `import java.util.*;` rồi nghĩ đã có `TimeUnit` | Không bao gồm subpackage `concurrent` | Import riêng hoặc dùng tên đầy đủ |
| `import java.util.L*;` | Không phải cú pháp Java | `import java.util.List;` hoặc `import java.util.*;` |
| Import cả `java.util.*` và `java.awt.*`, rồi dùng `List` | Hai kiểu trùng tên gây mơ hồ | Single-type import hoặc qualified name |
| Hai single-type imports cho hai `List` khác nhau | Xung đột khai báo | Chỉ import một, kiểu kia dùng tên đầy đủ |
| Đặt `import` trong `main` | Import chỉ nằm ở đầu compilation unit | Đặt sau `package`, trước class |
| Nghĩ wildcard import class vào runtime ngay | Import là cơ chế phân giải tên lúc biên dịch | Không suy luận tải lớp từ cú pháp import |
| Nghĩ `java.lang.String` luôn phải import | Có import-on-demand ngầm định cho `java.lang` | Dùng `String` trực tiếp |
| Gọi `java Demo` khi class ở `package demo` | Thiếu tên nhị phân đầy đủ | `java -cp . demo.ImportKindsDemo` |
| Nhầm `import static` với import kiểu | Một bên import thành viên static | Chọn đúng cú pháp theo thứ được sử dụng |

## 10. Bài tập có đáp án

**A.** Viết import chỉ dành cho `ArrayList` thuộc package `java.util`.  
**Đáp án:** `import java.util.ArrayList;`

**B.** Có `import java.time.*;`, có dùng trực tiếp `LocalDate` được không?  
**Đáp án:** **Có**, vì `LocalDate` thuộc package `java.time`.

**C.** Có `import java.util.*;`, có dùng trực tiếp `TimeUnit` không?  
**Đáp án:** **Không**, vì `TimeUnit` thuộc `java.util.concurrent`; thêm `import java.util.concurrent.TimeUnit;` hoặc ghi tên đầy đủ.

**D.** Với `import java.util.List;` và `import java.awt.*;`, `List` không định danh đầy đủ chọn kiểu nào?  
**Đáp án:** `java.util.List`, theo khai báo single-type import cụ thể.

**E.** Không import `List`, có khai báo `java.util.List<String> names;` được không?  
**Đáp án:** **Có**, dùng tên đầy đủ nên không cần import.

**F.** Import `java.util.*` có khiến Java nạp hết lớp ở package đó khi khởi động không?  
**Đáp án:** **Không**; wildcard import tham gia phân giải tên trong compilation unit, không ra lệnh nạp tất cả lớp.

## 11. Checklist và kết luận

- [ ] Phân biệt single-type import `import a.b.Type;` với type-import-on-demand `import a.b.*;`.
- [ ] Biết wildcard `*` không bao gồm package con, không lọc theo tiền tố.
- [ ] Biết dùng fully qualified type name khi không muốn import.
- [ ] Nhận diện trường hợp trùng simple name và import mơ hồ.
- [ ] Không coi thứ tự các dòng import là thứ tự ưu tiên.
- [ ] Phân biệt import kiểu và `import static`.
- [ ] Hiểu vị trí `package`, `import`, top-level type trong file.
- [ ] Hiểu `java.lang` được import-on-demand ngầm định.
- [ ] Biết `javac -d .` và tên `package.Class` khi chạy chương trình đóng gói.
- [ ] Biên dịch, chạy `ImportKindsDemo.java` trên JDK 17+, kiểm tra exact stdout.

**Kết luận:** `import package.Type;` đưa **một kiểu cụ thể** vào khả năng dùng tên ngắn; `import package.*;` cho phép phân giải **các kiểu truy cập được trong đúng package ấy** khi cần. Dấu `*` không import đệ quy package con và không tự nạp tất cả lớp; khi trùng tên, dùng single-type import rõ ràng hoặc tên kiểu đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-005">Static import cho thành viên static</a>

<details>
<summary>Xem chi tiết</summary>

# Static import cho thành viên static (Java 17+)

## 1. Static import là gì?

`static import` cho phép dùng **tên ngắn** của một thành viên `static` có thể truy cập từ lớp hoặc interface khác, thay vì luôn viết `TênKiểu.tênThànhViên`. Đây là cú pháp phân giải tên ở **thời điểm biên dịch**, không tạo bản sao thành viên, không biến thành viên thành biến cục bộ và không thay đổi hành vi hay hiệu năng của phương thức.

Ví dụ, thay `Math.PI`, `Math.pow(2, 3)` bằng `PI`, `pow(2, 3)` nếu có các khai báo import tương ứng. Cả hai cách vẫn truy cập đúng thành viên của `java.lang.Math`.

## 2. Cú pháp và vị trí

Khai báo `import static` nằm **sau dòng `package` (nếu có), trước các khai báo kiểu** và ở cùng cấp với `import` thông thường:

```java
package lesson;

import java.util.List;              // import tên kiểu List
import static java.lang.Math.PI;    // một trường static
import static java.lang.Math.pow;   // một phương thức static (các overload cùng tên)
import static java.lang.Math.*;     // toàn bộ thành viên static có thể truy cập, theo nhu cầu
```

Hai dòng `import static java.lang.Math.PI;` và `import static java.lang.Math.*;` ở đây được đặt cạnh nhau để minh họa cú pháp; trong mã thực tế chỉ cần dùng các khai báo phù hợp, tránh import thừa.

Có hai dạng chính:

| Dạng | Cú pháp | Ý nghĩa |
| --- | --- | --- |
| Import rõ tên | `import static ten.package.TenKieu.TEN;` | Đưa tên của một thành viên static vào phạm vi tra cứu tên ngắn |
| Import theo nhu cầu | `import static ten.package.TenKieu.*;` | Cho phép tra cứu các thành viên static có thể truy cập của kiểu đó bằng tên ngắn |

Dấu `*` không nhập package con, không tự nhập thành viên của tất cả lớp trong package và không nhập thành viên instance. `static import` không thay thế `package`, `classpath`, `module path` hoặc việc biên dịch lớp chứa thành viên.

## 3. Ví dụ đầy đủ với Math

Tạo tệp `src/lesson/CircleDemo.java`:

```java
package lesson;

import static java.lang.Math.PI;
import static java.lang.Math.pow;
import static java.lang.Math.max;

public class CircleDemo {
    public static void main(String[] args) {
        double radius = 2.0;
        double area = PI * pow(radius, 2);
        System.out.printf(java.util.Locale.ROOT, "area=%.2f%n", area);
        System.out.println("max=" + max(3, 7));
    }
}
```

Chạy tại thư mục chứa `src` (Java 17+). **Trước lần đầu dùng `javac -d out`, cần tạo sẵn thư mục `out`**: Bash/Linux/macOS dùng `mkdir -p out`; PowerShell dùng `New-Item -ItemType Directory -Force out | Out-Null` (CMD dùng `if not exist out mkdir out`). Sau đó biên dịch và chạy:

```sh
javac -d out src/lesson/CircleDemo.java
java -cp out lesson.CircleDemo
```

Kết quả chính xác:

```text
area=12.57
max=7
```

`PI` là trường `public static final`, `pow` và `max` là các phương thức `public static`. Phép tính tương đương `Math.PI * Math.pow(radius, 2)`; `max(3, 7)` tương đương `Math.max(3, 7)`. `Locale.ROOT` giúp dấu thập phân trong output là dấu chấm, không lệ thuộc locale hệ thống.

## 4. Static import cho thành viên tự định nghĩa và kiểu lồng nhau

Không chỉ API JDK: ta có thể import một trường static, một phương thức static và một kiểu thành viên static từ lớp do mình viết.

Tạo `src/lesson/util/Toolbox.java`:

```java
package lesson.util;

public final class Toolbox {
    private Toolbox() {}

    public static final int LIMIT = 3;

    public static String label(String name) {
        return "Hi, " + name;
    }

    public static class Token {
        public String text() {
            return "token";
        }
    }
}
```

Tạo `src/lesson/ToolboxDemo.java`:

```java
package lesson;

import static lesson.util.Toolbox.LIMIT;
import static lesson.util.Toolbox.label;
import static lesson.util.Toolbox.Token;

public class ToolboxDemo {
    public static void main(String[] args) {
        System.out.println("limit=" + LIMIT);
        System.out.println(label("Java"));
        System.out.println(new Token().text());
    }
}
```

Biên dịch cả hai tệp và chạy:

```sh
javac -d out src/lesson/util/Toolbox.java src/lesson/ToolboxDemo.java
java -cp out lesson.ToolboxDemo
```

Output:

```text
limit=3
Hi, Java
token
```

`LIMIT`, `label` và `Token` đều là thành viên static của `Toolbox`; `Token` là **kiểu lớp lồng static**, nên có thể dùng như tên kiểu sau static import. Nếu muốn, ba import này có thể được thay bằng `import static lesson.util.Toolbox.*;`; cách import từng tên thường dễ đọc và kiểm soát xung đột hơn.

## 5. Thành viên nào được phép import?

- **Trường static**: hằng `PI`, `MAX_VALUE`, trường `public static` khác. `final` không phải điều kiện bắt buộc.
- **Phương thức static**: `Math.max`, `Math.pow`, phương thức static của interface như `List.of`. Import theo tên phương thức cho phép trình biên dịch cân nhắc các overload thích hợp.
- **Kiểu thành viên static có thể truy cập**: ví dụ `Toolbox.Token` là nested static class; các kiểu thành viên của interface cũng là static theo quy tắc ngôn ngữ.

Quy tắc truy cập vẫn giữ nguyên: thành viên `private` ở lớp khác, thành viên `package-private` ngoài package hay thành viên `protected` không đủ điều kiện truy cập từ ngữ cảnh hiện tại sẽ không hợp lệ chỉ vì thêm static import. Không thể static import phương thức hoặc trường **instance**; chẳng hạn `import static java.lang.String.length;` là sai vì `length()` là phương thức đối tượng. Muốn dùng phải gọi `"abc".length()`.

## 6. Phân giải tên, trùng tên và bẫy thường gặp

**Trùng tên:** Khi nhiều import theo nhu cầu đưa vào các phương thức hoặc trường cùng tên có thể áp dụng, sử dụng tên ngắn có thể mơ hồ. Ví dụ, nếu vừa dùng `import static java.lang.Math.*;` vừa dùng `import static java.lang.StrictMath.*;`, gọi `sqrt(9.0)` bằng tên ngắn sẽ gặp xung đột; có thể viết rõ `Math.sqrt(9.0)` hoặc chỉ import một `sqrt` cần dùng. Phương thức cùng tên vẫn chịu cơ chế chọn overload của Java.

**Tên trong phạm vi gần:** Khai báo cùng tên ở lớp hoặc biến cục bộ có thể che khuất trường được static import; phương thức thành viên cũng có thể được ưu tiên khi phân giải lời gọi. Khi đọc code mà tên ngắn khó truy nguồn gốc, viết rõ `TênLớp.tên` sẽ an toàn hơn.

**Thứ tự import:** `import static` là khai báo cấp tệp; đặt trong thân class hoặc phương thức sẽ lỗi cú pháp. `import java.lang.Math;` chỉ đưa tên kiểu `Math` vào phạm vi và vẫn phải viết `Math.PI`; muốn dùng `PI` riêng phải viết `import static java.lang.Math.PI;`. Java tự nhận diện nhiều kiểu trong `java.lang`, nhưng không tự static import các thành viên của chúng.

**Wildcard:** `import static some.Type.*;` không có nghĩa mọi tên được chép thành biến cục bộ, không nhập thành viên instance và không phải cơ chế tải lớp ngay lúc chạy. Dùng wildcard quá rộng ở nhiều lớp tiện ích có thể khiến code khó đọc và tăng nguy cơ tên mơ hồ.

**Phụ thuộc:** Static import không tạo quyền truy cập đặc biệt; kiểu được import phải có thể biên dịch/giải quyết từ mã nguồn hoặc thư viện sẵn có trên classpath/module path. Đổi tên hoặc xóa thành viên static ở thư viện có thể làm mã dùng import không biên dịch.

## 7. Khi nào nên dùng?

Nên cân nhắc static import khi một nhóm thao tác thực sự xuất hiện liên tục và nguồn của chúng vẫn rõ ràng: phép toán `PI`/`pow`, các hàm xác nhận trong mã kiểm thử, hoặc hằng enum rất dễ nhận ra ngữ cảnh. Ưu tiên import **từng tên** và tránh dùng hai nguồn cho cùng một tên.

Nên giữ tiền tố `Math.`, `Collections.`, `Objects.` hoặc `TênLớp.` nếu việc chỉ rõ nguồn giúp người đọc nhanh chóng hiểu ý nghĩa, nhất là trong code nghiệp vụ. Static import là lựa chọn về **độ dễ đọc** chứ không phải yêu cầu để gọi thành viên static.

## 8. Tự kiểm tra

1. `import java.lang.Math;` có đủ để viết `sqrt(4)` không? **Không**; viết `Math.sqrt(4)` hoặc `import static java.lang.Math.sqrt;`.
2. Có thể `import static` một phương thức đối tượng không? **Không**; đối tượng phải cung cấp receiver để gọi.
3. `import static lesson.util.Toolbox.Token;` có hợp lệ khi `Token` là `public static class` không? **Có**, nếu `Toolbox` truy cập được và có trong đường dẫn biên dịch.
4. Khi trùng tên do static import, cách sửa dễ hiểu nhất là gì? **Gọi đầy đủ với tên kiểu**, hoặc thu hẹp các import.

Ghi nhớ: `static import` rút gọn tên thành viên static có thể truy cập; quy tắc phạm vi, quyền truy cập, overload và biên dịch của Java vẫn được áp dụng bình thường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-006">java.lang được import ngầm định</a>

<details>
<summary>Xem chi tiết</summary>

# `java.lang` được import ngầm định trong Java

## 1. Ý nghĩa của import ngầm định

Trong Java, mỗi đơn vị biên dịch (*compilation unit*, thông thường là một tệp `.java`) được ngôn ngữ cung cấp cơ chế **import theo yêu cầu** (*on-demand import*) các kiểu truy cập được từ package **`java.lang`**. Vì vậy, lập trình viên thường dùng các tên lớp quen thuộc như `String`, `Object`, `System`, `Math`, `Integer`, `Thread` mà **không cần tự viết** `import java.lang.String;` hay `import java.lang.*;`.

Ví dụ, chương trình sau không có khai báo `import` nào:

```java
public class Hello {
    public static void main(String[] args) {
        String message = "Hello, Java!";
        System.out.println(message);
        System.out.println(Math.max(7, 12));
    }
}
```

Lưu mã vào tệp **`Hello.java`** rồi chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 Hello.java
java Hello
```

Stdout chính xác:

```text
Hello, Java!
12
```

Cả `String`, `System`, `Math` đều thuộc `java.lang`. `javac` phân giải những tên đơn giản này thành các kiểu tương ứng khi không có tên nào khác che khuất hoặc gây xung đột theo quy tắc tra cứu tên của Java.

**Quan trọng:** "import ngầm định" là **quy tắc phân giải tên lúc biên dịch**, không có nghĩa JVM sẽ khởi tạo hoặc tải tất cả lớp trong `java.lang` ngay khi chương trình bắt đầu. Nó cũng không thêm bất kỳ thư viện bên ngoài nào vào classpath.

## 2. `java.lang` chứa những kiểu nào thường gặp?

Package này cung cấp nhiều kiểu cơ bản của ngôn ngữ và runtime Java.

| Kiểu (viết tên đơn giản) | Tên đầy đủ | Vai trò điển hình |
| --- | --- | --- |
| `Object` | `java.lang.Object` | Lớp gốc của các lớp thông thường; cung cấp `toString()`, `equals()`, `hashCode()` |
| `String` | `java.lang.String` | Chuỗi ký tự bất biến |
| `StringBuilder` | `java.lang.StringBuilder` | Xây dựng/chỉnh sửa nội dung chuỗi tuần tự |
| `System` | `java.lang.System` | `System.out`, `System.err`, `System.currentTimeMillis()` và các tiện ích hệ thống |
| `Math` | `java.lang.Math` | Các phép toán tĩnh như `max`, `abs`, `sqrt`, hằng `PI` |
| `Integer`, `Long`, `Double`, `Boolean`, ... | `java.lang.Integer`, ... | Lớp bao (*wrapper*) cho giá trị nguyên thủy; hỗ trợ chuyển đổi/boxing |
| `Throwable`, `Exception`, `RuntimeException`, `Error` | `java.lang.*` tương ứng | Cấu trúc cơ bản của exception và error |
| `Thread`, `Runnable` | `java.lang.Thread`, `java.lang.Runnable` | Khả năng chạy tác vụ/concurrent cơ bản |
| `Enum`, `Record` | `java.lang.Enum`, `java.lang.Record` | Các lớp nền trong hệ thống kiểu của enum và record |

Một số ví dụ cú pháp ngắn, đặt trong phương thức:

```java
String text = "java";
String upper = text.toUpperCase(); // "JAVA"

int max = Math.max(3, 8); // 8
double root = Math.sqrt(81); // 9.0

Integer number = Integer.valueOf("42"); // 42
StringBuilder builder = new StringBuilder("Java");
builder.append(" 17"); // "Java 17"
```

Tất cả tên kiểu ở trên đều có thể dùng trực tiếp. Tuy nhiên **phương thức và trường static** vẫn thường cần tên lớp phía trước, ví dụ `Math.sqrt(81)` hoặc `System.out.println(...)`. Import ngầm định của **kiểu `Math`** không tự đưa tên phương thức `sqrt` vào scope để gọi chỉ bằng `sqrt(81)`.

## 3. Phân biệt `java.lang` với các package khác

Các package khác trong thư viện tiêu chuẩn **không được import ngầm định chỉ vì thuộc JDK**. Ví dụ:

```java
// java.util.List: phải import khi cần dùng tên đơn giản List.
import java.util.List;
import java.util.ArrayList;
```

Sau khi import:

```java
List<String> names = new ArrayList<>();
names.add("An");
```

Hai dòng sau có cùng ý nghĩa kiểu mà không cần lệnh import:

```java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("An");
```

| Tên kiểu | Tự dùng tên đơn giản thông thường? | Cách xử lý |
| --- | --- | --- |
| `String` | Có, qua `java.lang` ngầm định | `String text = "hi";` |
| `Math` | Có, qua `java.lang` ngầm định | `Math.max(1, 2)` |
| `List`, `ArrayList` | Không do `java.lang` | `import java.util.List;`, `import java.util.ArrayList;` |
| `LocalDate` | Không do `java.lang` | `import java.time.LocalDate;` |
| `Files`, `Path` | Không do `java.lang` | `import java.nio.file.Files;`, `import java.nio.file.Path;` |
| `Method` (reflection) | Không do `java.lang` | `import java.lang.reflect.Method;` |
| `MethodHandles` | Không do `java.lang` | `import java.lang.invoke.MethodHandles;` |

**Package con không được import theo package cha.** `java.lang.reflect`, `java.lang.invoke`, `java.lang.management` là các package khác `java.lang`, mặc dù tên bắt đầu bằng `java.lang.`. Do đó:

```java
// Không tự có tên Method từ import ngầm java.lang:
java.lang.reflect.Method[] methods = String.class.getMethods();
System.out.println(methods.length > 0); // true
```

Nếu muốn gọi tên ngắn `Method`, cần `import java.lang.reflect.Method;`. Đoạn trên là ví dụ đặt bên trong một phương thức, không phải tệp chạy độc lập.

## 4. Viết tên đơn giản và tên đầy đủ cho cùng một kiểu

Do import ngầm định, hai khai báo sau thường cùng một kiểu:

```java
String left = "Java";
java.lang.String right = "Java";
System.out.println(left.equals(right)); // true
```

- `String` là **tên đơn giản** của `java.lang.String`.
- `java.lang.String` là **tên đầy đủ** (*fully qualified name*) của kiểu.
- Không có hai loại `String` khác nhau trong đoạn trên; import chỉ giúp tên ngắn được phân giải.
- Viết `import java.lang.String;` là hợp lệ trong tình huống thông thường nhưng **thừa**, vì Java đã cung cấp import theo yêu cầu cho package này.

**Phân giải tên không chỉ phụ thuộc import.** Kiểu được khai báo trong cùng package, kiểu lồng nhau trong scope, hoặc khai báo import cụ thể có thể ảnh hưởng đến ý nghĩa của một tên ngắn. Vì vậy, nếu một dự án tự định nghĩa kiểu `String` hoặc `System`, việc đọc tên không đủ để kết luận chúng là kiểu `java.lang`.

Ví dụ này minh họa một trường hợp **kiểu tự định nghĩa cùng tên** nằm trong package hiện tại:

```java
package demo;

class String {
    // Chỉ để minh họa xung đột tên; không nên dùng tên này trong dự án thực.
}

class Example {
    java.lang.String message = "Hello"; // Chỉ rõ String tiêu chuẩn
}
```

Đây là **một tệp minh họa riêng**, không ghép vào các chương trình bên dưới. Với tên cùng package, `String` không còn có thể mặc nhiên hiểu là `java.lang.String` tại những vị trí bị che khuất. Trong chương trình thực tế, đặt tên kiểu tự định nghĩa sao cho rõ nghĩa và tránh đụng tên các kiểu nền tảng.

Nếu cố import hai kiểu top-level cùng tên đơn giản bằng hai lệnh single-type import khác nhau, trình biên dịch có thể báo xung đột. Khi phải dùng cả hai kiểu cùng tên, dùng tên đầy đủ để làm rõ.

## 5. `import java.lang.*` không đồng nghĩa `import static java.lang.Math.*`

Đây là hai cơ chế khác nhau:

```java
import java.lang.*; // Thừa trong mã thông thường; import các kiểu từ package.
```

```java
import static java.lang.Math.max; // Cho phép gọi max(...) trực tiếp
```

Ví dụ tệp độc lập:

```java
import static java.lang.Math.max;

public class StaticImportDemo {
    public static void main(String[] args) {
        System.out.println(max(5, 9));      // Từ static import
        System.out.println(Math.min(5, 9)); // Math có sẵn qua java.lang
    }
}
```

Lưu thành **`StaticImportDemo.java`**, chạy:

```bash
javac -encoding UTF-8 StaticImportDemo.java
java StaticImportDemo
```

Kết quả:

```text
9
5
```

`import java.lang.*` **không phải** lệnh import mọi phương thức hay field của mọi lớp trong `java.lang`. `import static` làm cho các thành viên static đáp ứng điều kiện truy cập có thể được nhắc tên trực tiếp, nhưng vẫn phải tránh xung đột tên và lạm dụng làm giảm tính rõ ràng.

## 6. Chương trình Java 17+ trong package có tên

Tạo tệp **`src/lesson/LangAutoImportDemo.java`**:

```java
package lesson;

import java.util.ArrayList;
import java.util.List;

public class LangAutoImportDemo {
    public static void main(String[] args) {
        // java.lang: không cần import.
        String text = "Java";
        StringBuilder builder = new StringBuilder(text);
        builder.append(" 17");
        System.out.println("Text: " + builder);

        System.out.println("Max: " + Math.max(4, 9));
        System.out.println("Abs: " + Math.abs(-7));
        System.out.println("PI > 3: " + (Math.PI > 3.0));

        Integer value = Integer.valueOf("42");
        System.out.println("Parsed: " + value);

        Object marker = new Object();
        System.out.println("Object: " + marker.getClass().getSimpleName());

        // java.util: phải import để dùng tên ngắn trong file này.
        List<String> items = new ArrayList<>();
        items.add("One");
        items.add("Two");
        System.out.println("List: " + items);

        // Tên đầy đủ hoạt động mà không cần import kiểu.
        java.lang.String fullName = "ready";
        System.out.println("Full-name String: " + fullName);
        System.out.println("Runtime String: " + String.class.getName());
    }
}
```

Đứng tại thư mục gốc chứa `src`, dùng JDK 17 trở lên. **Tạo thư mục `out` nếu chưa tồn tại** trước khi dùng `-d out`: Bash/Linux/macOS chạy `mkdir -p out`; Windows PowerShell chạy `New-Item -ItemType Directory -Force out | Out-Null` (CMD: `if not exist out mkdir out`). Sau đó dùng lệnh biên dịch/chạy sau trên cả Windows và Linux/macOS:

```bash
javac -encoding UTF-8 -d out src/lesson/LangAutoImportDemo.java
java -cp out lesson.LangAutoImportDemo
```

`-d out` cho phép `javac` tạo cây thư mục theo tên package trong thư mục `out`. `lesson.LangAutoImportDemo` là tên đầy đủ của lớp có `main` khi gọi lệnh `java`.

**Stdout chính xác** (biên dịch thành công không in thông báo):

```text
Text: Java 17
Max: 9
Abs: 7
PI > 3: true
Parsed: 42
Object: Object
List: [One, Two]
Full-name String: ready
Runtime String: java.lang.String
```

Đoạn mã dùng tên đơn giản `String`, `StringBuilder`, `System`, `Math`, `Integer`, `Object` mà không có import riêng. Ngược lại, `List` và `ArrayList` thuộc `java.util`, nên tệp chủ động import các kiểu này.

## 7. Quan hệ với module `java.base`, classpath và biên dịch

Trong Java 9+ (bao gồm Java 17), package `java.lang` thuộc module nền tảng **`java.base`**. Mỗi module Java đều đọc `java.base` theo cơ chế **ngầm định** của hệ thống module. Đây là một cơ chế **khác** với việc import tên kiểu trong source:

- **Package import ngầm định:** cho phép viết `String` thay vì `java.lang.String`.
- **Khả năng đọc module `java.base`:** giúp mã module có thể sử dụng các API công khai được export từ module nền tảng mà không cần viết `requires java.base;` trong `module-info.java`.
- **Classpath/module path:** quyết định khả năng tìm thấy các lớp và module ngoài phạm vi nền tảng. `import` tự nó không tải về dependency hay chỉ đường cho trình biên dịch.
- **Runtime:** kiểu được nạp/khởi tạo theo các quy tắc runtime, không do một dòng import tự thực hiện sẵn.

Những chi tiết module không cần đưa vào mọi bài Java cơ bản, nhưng hữu ích để tránh nhầm `java.lang` tự động có mặt với các cơ chế tìm lớp/thực thi.

## 8. Những lỗi và ngộ nhận thường gặp

| Nhận định/lỗi | Giải thích đúng |
| --- | --- |
| “Phải viết `import java.lang.String;` trước khi dùng `String`.” | Không cần, `java.lang` được import theo yêu cầu ngầm định. |
| “Không cần import bất cứ kiểu nào vì chúng đều nằm trong JDK.” | Sai; `java.util.List`, `java.time.LocalDate`... cần import nếu dùng tên đơn giản (trừ ngữ cảnh đã có sẵn tên phù hợp). |
| “`java.lang.reflect.Method` tự thành `Method`.” | Sai; package con khác package cha, phải import riêng hoặc dùng tên đầy đủ. |
| “Nhờ import `Math`, gọi thẳng `sqrt(9)` được.” | Sai; thường dùng `Math.sqrt(9)` hoặc static import. |
| “`import java.lang.*;` làm JVM nạp hết các lớp.” | Sai; import chỉ ảnh hưởng cách phân giải tên lúc biên dịch. |
| “Tên `String` trong mọi file đều bắt buộc chỉ `java.lang.String`.” | Sai; tên đơn giản còn chịu quy tắc scope và xung đột/che khuất. |
| “`int` và `boolean` là lớp trong `java.lang`.” | Sai; chúng là **kiểu nguyên thủy** của ngôn ngữ, không phải lớp được import. |
| “`java.lang` được import vì chương trình luôn ở package mặc định.” | Sai; import ngầm định vẫn áp dụng cho tệp có `package lesson;` và cho các package được đặt tên khác. |

**Ghi nhớ:** Java tự cho phép dùng tên ngắn của các kiểu phù hợp trong **`java.lang`**, nhưng không import ngầm các package khác hoặc package con. `import` giải quyết **tên trong mã nguồn**; để hiểu chính xác lớp đang dùng, cần xét package, scope và kiểu được phân giải khi biên dịch.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-007">Import không tự động bao gồm package con</a>

<details>
<summary>Xem chi tiết</summary>

# Import không tự động bao gồm package con trong Java

## 1. Quy tắc chính: mỗi package là một không gian tên riêng

Trong Java, **package** dùng để nhóm các kiểu có liên quan và tạo không gian tên. Dấu chấm trong tên package biểu diễn cấu trúc đặt tên, nhưng **không tạo ra quan hệ kế thừa hoặc quan hệ “import lan truyền”** giữa package cha và package con.

Ví dụ:

- `java.util` và `java.util.concurrent` là **hai package khác nhau**.
- `java.util.concurrent` và `java.util.concurrent.atomic` cũng là **hai package khác nhau**.
- `java.lang` và `java.lang.reflect` là **hai package khác nhau**.

Khi viết:

```java
import java.util.*;
```

trình biên dịch có thể phân giải tên đơn giản của các **kiểu truy cập được được khai báo trực tiếp trong package `java.util`**, chẳng hạn `List`, `ArrayList`, `Map` và `Scanner`. Nhưng câu lệnh đó **không tự động import các kiểu trong package con `java.util.concurrent`** như `TimeUnit` hay `ExecutorService`, và cũng không import `AtomicInteger` từ `java.util.concurrent.atomic`.

**Quy tắc cần nhớ:** `import p.*;` không có nghĩa “import `p` và tất cả package con”. Ký tự `*` ở đây **không phải dấu wildcard tìm kiếm thư mục đệ quy**.

## 2. Ví dụ: `java.util.*` không tự đưa `TimeUnit` vào scope

Xét mã nguồn:

```java
import java.util.*;

class Example {
    void demo() {
        List<String> words = new ArrayList<>(); // Hợp lệ
        // TimeUnit.SECONDS.toMillis(2);       // LỖI: thiếu import cho TimeUnit
    }
}
```

`List` và `ArrayList` thuộc chính package `java.util`. Còn `TimeUnit` thuộc `java.util.concurrent`, nên lệnh `import java.util.*;` không giúp tên `TimeUnit` được nhận diện.

Có hai cách xử lý đúng.

**Cách 1 — Import kiểu trực tiếp:**

```java
import java.util.*;
import java.util.concurrent.TimeUnit;

// Trong phương thức:
long millis = TimeUnit.SECONDS.toMillis(2); // 2000
```

**Cách 2 — Dùng tên đầy đủ, không cần thêm import:**

```java
import java.util.*;

// Trong phương thức:
long millis = java.util.concurrent.TimeUnit.SECONDS.toMillis(2); // 2000
```

Các đoạn này là **mã minh họa**, chưa phải chương trình độc lập. Nếu cần nhiều kiểu trong cùng package con, có thể dùng:

```java
import java.util.concurrent.*;
```

Lệnh đó có thể giúp dùng `TimeUnit` và `ExecutorService` bằng tên ngắn, nhưng **vẫn không tự import `java.util.concurrent.atomic.AtomicInteger`** hoặc các kiểu trong những package con khác của `java.util.concurrent`.

## 3. Một số ví dụ phổ biến khác

| Đã import | Kiểu dùng được bằng tên ngắn nhờ import đó | Kiểu **không** được tự import |
| --- | --- | --- |
| `import java.util.*;` | `List`, `ArrayList`, `Map` | `java.util.concurrent.TimeUnit` |
| `import java.util.concurrent.*;` | `TimeUnit`, `ExecutorService` | `java.util.concurrent.atomic.AtomicInteger` |
| `import java.security.*;` | `MessageDigest`, `SecureRandom`, `KeyPair` | `java.security.cert.Certificate` |
| `import java.nio.file.*;` | `Path`, `Files` | `java.nio.file.attribute.BasicFileAttributes` |
| Import ngầm `java.lang.*` | `String`, `System`, `Math`, `Integer` | `java.lang.reflect.Method`, `java.lang.invoke.MethodHandles` |

Ví dụ cụ thể với reflection:

```java
// String dùng trực tiếp do thuộc java.lang:
String text = "Java";

// Method thuộc package java.lang.reflect, không tự thành tên ngắn:
// Method m = String.class.getMethod("length"); // LỖI nếu chưa import Method

java.lang.reflect.Method m = String.class.getMethod("length");
```

Đoạn minh họa cuối cần nằm trong phương thức có khai báo `throws NoSuchMethodException` hoặc xử lý ngoại lệ thích hợp. Có thể thay tên đầy đủ bằng khai báo `import java.lang.reflect.Method;` ở đầu file. Điểm mấu chốt: **import ngầm `java.lang` cũng không lan xuống package con**.

Các package chuẩn này có thể nằm trong cùng một module (chẳng hạn `java.base`), nhưng cùng module **không khiến import tự lan sang các package khác**.

## 4. Chương trình Java 17+ hoàn chỉnh

Ví dụ dưới đây minh họa đồng thời **on-demand import ở package cha**, **import tường minh từ package con**, và **tên đầy đủ thay cho import**.

Tạo tệp **`src/lesson/ImportSubpackageDemo.java`** với nội dung:

```java
package lesson;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

public class ImportSubpackageDemo {
    public static void main(String[] args)
            throws NoSuchMethodException {

        // ArrayList, List và Map trực tiếp thuộc java.util.
        List<String> words = new ArrayList<>();
        words.add("Java");
        words.add("core");
        System.out.println("Words: " + words);

        // TimeUnit thuộc java.util.concurrent:
        // cần import riêng dù đã có java.util.*.
        long millis = TimeUnit.SECONDS.toMillis(3);
        System.out.println("Millis: " + millis);

        // AtomicInteger ở package con khác,
        // ở đây dùng tên đầy đủ thay vì import.
        java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger(4);
        System.out.println("Counter: " + counter.addAndGet(2));

        // String thuộc java.lang, được import ngầm định.
        String name = "Java";
        System.out.println("Name: " + name);

        // Method thuộc java.lang.reflect;
        // cần import riêng ở đầu tệp.
        Method method = String.class.getMethod("substring", int.class);
        System.out.println("Method: " + method.getName());

        // Entry là kiểu thành viên lồng trong Map, KHÔNG phải package con.
        Map.Entry<String, Integer> entry = Map.entry("id", 7);
        System.out.println("Entry: "
                + entry.getKey() + "=" + entry.getValue());
    }
}
```

Đứng ở thư mục chứa `src`, với **JDK 17 trở lên**. **Trước lần đầu chạy lệnh `javac -d out`, phải tạo thư mục `out`**: Bash/Linux/macOS chạy `mkdir -p out`; PowerShell chạy `New-Item -ItemType Directory -Force out | Out-Null` (CMD: `if not exist out mkdir out`). Khi `out` đã tồn tại, biên dịch và chạy trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 -d out src/lesson/ImportSubpackageDemo.java
java -cp out lesson.ImportSubpackageDemo
```

`-d out` để `javac` sinh cấu trúc thư mục cho các file `.class` theo package; `lesson.ImportSubpackageDemo` là tên đầy đủ của lớp chứa `main`.

**Stdout chính xác** (lệnh `javac` thành công không in dòng nào):

```text
Words: [Java, core]
Millis: 3000
Counter: 6
Name: Java
Method: substring
Entry: id=7
```

**Giải thích kết quả:**

1. `List`, `ArrayList`, `Map` nhận diện được nhờ `import java.util.*;`.
2. `TimeUnit` được nhận diện nhờ `import java.util.concurrent.TimeUnit;`, không phải nhờ wildcard ở `java.util`.
3. `AtomicInteger` dùng **tên đầy đủ** `java.util.concurrent.atomic.AtomicInteger`, nên không cần import riêng.
4. `String` được dùng nhờ import ngầm của `java.lang`, còn `Method` cần import riêng từ `java.lang.reflect`.
5. `Map.Entry` là **kiểu lồng nhau** (member type) bên trong `Map`, không phải lớp thuộc một package tên `java.util.Map`.

Không cần thêm `import java.util.concurrent.*;` vào ví dụ vì một kiểu `TimeUnit` đã được import cụ thể, còn `AtomicInteger` đang được gọi bằng tên đầy đủ. Cách trình bày đó giúp nhìn thấy chính xác sự khác nhau giữa ba cơ chế.

## 5. Khác nhau giữa package con và kiểu lồng nhau

Dấu chấm trong tên Java có thể xuất hiện ở **tên package** hoặc ở **tên của kiểu thành viên**. Đó là hai cấu trúc khác nhau.

**Package con:**

```java
java.util.concurrent.TimeUnit
```

Ở đây `java.util.concurrent` là package chứa lớp `TimeUnit`. Nó không được import bởi `import java.util.*;`.

**Kiểu lồng nhau:**

```java
java.util.Map.Entry
```

Ở đây `Map` là interface thuộc `java.util`, còn `Entry` là một interface thành viên lồng trong `Map`. Có thể viết:

```java
import java.util.Map;

// Trong phương thức:
Map.Entry<String, Integer> item = Map.entry("key", 10);
```

Hoặc import trực tiếp kiểu thành viên:

```java
import java.util.Map.Entry;

// Trong phương thức:
Entry<String, Integer> item = java.util.Map.entry("key", 10);
```

Đặc biệt, `import java.util.Map.*;` là một dạng **type-import-on-demand** cho các kiểu thành viên truy cập được của `Map` — hoàn toàn khác `import java.util.*;`, vốn chọn các kiểu top-level từ package `java.util`. Không nên nhầm dấu chấm giữa `Map` và `Entry` với một package con của `java.util`.

Khi đọc một tên nhiều thành phần, hãy xác định rõ mỗi thành phần là **package, kiểu top-level hay kiểu thành viên**, thay vì phỏng đoán mọi đoạn giữa các dấu chấm là cấp thư mục.

## 6. Không có kế thừa quyền truy cập giữa package cha và package con

Quan hệ tên `com.example` và `com.example.feature` **không tạo quan hệ truy cập nội bộ**. Một thành viên hoặc lớp top-level có quyền truy cập **package-private** (không ghi access modifier) chỉ truy cập được từ code thuộc **đúng cùng package**, không tự mở cho mã ở package có cùng tiền tố.

Ví dụ hai tệp minh họa:

**`src/com/example/Helper.java`**

```java
package com.example;

class Helper {
    static void ping() {
        System.out.println("ping");
    }
}
```

**`src/com/example/feature/Feature.java`**

```java
package com.example.feature;

// import com.example.Helper; // LỖI: Helper không public

public class Feature {
    public static void main(String[] args) {
        // com.example.Helper.ping(); // LỖI truy cập
        System.out.println("Feature");
    }
}
```

Đây là ví dụ **giải thích quyền truy cập**, tách biệt chương trình ở mục 4. Kể cả khi viết tên đầy đủ `com.example.Helper`, quy tắc package-private vẫn ngăn lớp `Feature` truy cập. `import` hay việc đặt thư mục bên trong `com/example` **không thay đổi access modifiers**.

Nếu cần sử dụng từ package khác, kiểu được công khai theo thiết kế phù hợp (thường `public`) và các thành viên cần gọi cũng phải có khả năng truy cập tương ứng.

## 7. Cách tự kiểm tra khi trình biên dịch báo không nhận tên

Một tình huống phổ biến là đã viết `import java.util.*;` nhưng `TimeUnit` vẫn bị báo không tìm thấy. Kiểm tra theo thứ tự:

1. **Xác định tên đầy đủ thật sự của kiểu:** `TimeUnit` là `java.util.concurrent.TimeUnit`, không phải `java.util.TimeUnit`.
2. **Kiểm tra import đúng package:** viết `import java.util.concurrent.TimeUnit;` hoặc `import java.util.concurrent.*;`, không chỉ `import java.util.*;`.
3. **Thử tên đầy đủ tại nơi dùng:** `java.util.concurrent.TimeUnit.SECONDS.toMillis(3)`. Nếu hợp lệ, vấn đề có thể là tra cứu tên/import.
4. **Xem quyền truy cập:** lớp/thành viên có truy cập được từ package hiện tại không? Import không bỏ qua quy tắc truy cập.
5. **Kiểm tra classpath hoặc module path nếu là thư viện ngoài:** import chỉ giải quyết tên, không tải thư viện về hay bổ sung đường dẫn. Với hệ thống module, cần các quan hệ đọc/export thích hợp.
6. **Không thêm import chỉ để che giấu xung đột:** khi có hai kiểu tên đơn giản trùng nhau từ các package khác nhau, dùng single-type import phù hợp hoặc tên đầy đủ.

**Ví dụ lỗi có chủ ý** (không phải chương trình độc lập để chạy):

```java
import java.util.*;

// Trong phương thức:
// TimeUnit.SECONDS.toMillis(1); // Thiếu import java.util.concurrent.TimeUnit
// AtomicInteger n = new AtomicInteger(0); // Thiếu import java.util.concurrent.atomic.AtomicInteger
```

Không cần ghi nhớ tên mọi package, nhưng cần tra cứu đúng **package khai báo trực tiếp kiểu** thay vì suy diễn từ tiền tố.

## 8. Các lỗi và ngộ nhận thường gặp

| Ngộ nhận | Kết luận đúng |
| --- | --- |
| “`import a.b.*;` tự import luôn `a.b.c.*;`.” | **Sai.** Mỗi package là không gian tên riêng; wildcard không đệ quy. |
| “`java.lang` được import ngầm nên `java.lang.reflect.Method` cũng được import.” | **Sai.** Package con không được tự import. |
| “`import java.util.concurrent.*;` nhận luôn `AtomicInteger`.” | **Sai.** `AtomicInteger` ở `java.util.concurrent.atomic`. |
| “Viết tên đầy đủ vẫn cần thêm `import`.” | **Sai.** Tên đầy đủ giúp tham chiếu trực tiếp tới kiểu mà không cần import kiểu đó. |
| “`Map.Entry` là package con của `Map`.” | **Sai.** `Entry` là kiểu thành viên lồng trong `Map`. |
| “Package `com.example.sub` truy cập được mọi thành viên package-private trong `com.example`.” | **Sai.** Hai package là khác nhau. |
| “`import` đưa tất cả lớp vào bộ nhớ trước khi chạy.” | **Sai.** Import là cơ chế phân giải tên lúc biên dịch, không tự khởi tạo toàn bộ lớp. |
| “Có `import` là không cần dependency/classpath nữa.” | **Sai.** Trình biên dịch/JVM vẫn phải tìm được định nghĩa kiểu có thể truy cập. |

**Ghi nhớ:** `import package.*` chỉ liên quan tới các kiểu thích hợp **trực tiếp trong package đó**; nó không tự bao gồm các package con có cùng tiền tố. Muốn dùng kiểu thuộc package con, hãy **import đúng package con/kiểu** hoặc **viết tên đầy đủ của kiểu**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-008">Xử lý hai lớp trùng tên và giới hạn của import</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý hai lớp trùng tên và giới hạn của `import` trong Java

## 1. Vì sao hai lớp có thể trùng tên?

Trong Java, **tên đơn giản** (*simple name*) là phần cuối của tên lớp, chẳng hạn `Result`. **Tên đầy đủ** (*fully qualified name*) gắn tên package với tên kiểu, chẳng hạn `com.example.payment.Result` và `com.example.network.Result`.

Hai lớp cùng tên đơn giản có thể tồn tại trong hai package khác nhau. Vấn đề chỉ xuất hiện khi mã nguồn cần phân giải tên ngắn `Result` nhưng có nhiều ứng viên. `import` giúp sử dụng tên ngắn của kiểu có thể truy cập; nó **không đổi tên lớp, không tạo alias, không sao chép mã**, cũng không bắt JVM nạp lớp ngay tại thời điểm viết import.

Ví dụ các kiểu hợp lệ và khác nhau:

```text
com.example.payment.Result
com.example.network.Result
java.util.Date
java.sql.Date
```

Khi gặp trùng tên, cách chắc chắn nhất là dùng **tên đầy đủ** tại nơi sử dụng.

## 2. Phân biệt xung đột từ import cụ thể và import theo yêu cầu

~~~java
package com.example.app;
import java.util.List;            // Import một kiểu
import java.util.*;               // Import nhiều kiểu theo yêu cầu
import static java.lang.Math.PI;  // Import thành viên static
~~~

Các khai báo import chỉ giúp **phân giải tên trong source**, không tạo alias, không vượt qua quyền truy cập và không làm Java nạp lớp lúc chạy. Dấu `*` không tự bao gồm package con.

Bài này tập trung vào khác biệt giữa **hai single-type imports** cho hai kiểu khác nhau cùng simple name (lỗi tại dòng import) và **hai wildcard imports** cung cấp hai ứng viên cùng tên (mơ hồ khi dùng tên đó). Để dùng cả hai kiểu, hãy import cụ thể tối đa một kiểu rồi viết qualified name cho kiểu còn lại, hoặc viết qualified name cho cả hai.

## 3. Ví dụ chạy được: hai lớp Result khác package

Tạo cấu trúc thư mục:

```text
src/
└── com/
    └── example/
        ├── payment/
        │   └── Result.java
        ├── network/
        │   └── Result.java
        └── app/
            └── Main.java
```

**Tệp `src/com/example/payment/Result.java`:**

```java
package com.example.payment;

public class Result {
    public String origin() {
        return "payment";
    }
}
```

**Tệp `src/com/example/network/Result.java`:**

```java
package com.example.network;

public class Result {
    public String origin() {
        return "network";
    }
}
```

**Tệp `src/com/example/app/Main.java`:**

```java
package com.example.app;

import com.example.payment.Result;
import com.example.network.*;

public class Main {
    public static void main(String[] args) {
        Result payment = new Result();
        com.example.network.Result network = new com.example.network.Result();

        System.out.println("simple=" + payment.origin());
        System.out.println("qualified=" + network.origin());
    }
}
```

Ở đây, `import com.example.payment.Result;` đưa **một kiểu cụ thể** vào phạm vi tên ngắn. `import com.example.network.*;` chỉ nhập theo nhu cầu, nên `Result` không trở thành mơ hồ: khai báo import một kiểu cụ thể được ưu tiên hơn import theo nhu cầu. Lớp `network.Result` vẫn được dùng bình thường khi ghi tên đầy đủ. Nếu chỉ dùng tên đầy đủ cho kiểu network, có thể bỏ hẳn `import com.example.network.*;`.

**Biên dịch và chạy với JDK 17+** tại thư mục có `src` (các đường dẫn dùng `/` được Java chấp nhận trên Windows, Linux và macOS). Tạo thư mục `out` một lần bằng `mkdir out`, sau đó:

```sh
javac -encoding UTF-8 -d out src/com/example/payment/Result.java src/com/example/network/Result.java src/com/example/app/Main.java
java -cp out com.example.app.Main
```

Kết quả của lệnh `java`, chính xác:

```text
simple=payment
qualified=network
```

Tên dùng trong `java -cp out com.example.app.Main` là **tên đầy đủ của lớp chứa `main`**. `-d out` đặt các file `.class` vào thư mục tương ứng với package. Việc import không thay thế biên dịch lớp phụ thuộc hoặc cấu hình classpath.

## 4. Những trường hợp trùng tên và cách Java xử lý

| Tình huống | Dùng tên ngắn `Result` | Cách xử lý |
| --- | --- | --- |
| `import com.example.payment.Result;` cùng `import com.example.network.*;` | Chọn kiểu `payment.Result` vì import tường minh | Ghi `com.example.network.Result` khi cần kiểu còn lại |
| `import com.example.payment.*;` và `import com.example.network.*;` | **Mơ hồ** nếu tham chiếu `Result` khi cả hai kiểu cùng truy cập được | Chọn một import tường minh hoặc dùng tên đầy đủ |
| Hai `import` tường minh cho **hai kiểu khác nhau** cùng tên đơn giản | **Lỗi biên dịch ngay tại các khai báo import** | Giữ tối đa một import tường minh cho tên đó |
| Kiểu cùng package và kiểu từ wildcard ở package khác cùng tên | Kiểu cùng package được chọn trước kiểu chỉ đến từ import theo nhu cầu | Ghi tên đầy đủ cho kiểu thuộc package khác |
| Kiểu thành viên/lớp lồng nhau hay tên kiểu được khai báo tại phạm vi gần | Có thể che khuất tên được import tùy vị trí và loại khai báo | Kiểm tra phạm vi hoặc dùng tên kiểu đủ rõ ràng |

### 4.1. Hai import tường minh: lỗi dù chưa tạo đối tượng

Ví dụ sau **cố ý không biên dịch được**, với giả định hai `Result` trong ví dụ trên đều tồn tại:

```java
package com.example.app;

import com.example.payment.Result;
import com.example.network.Result; // lỗi: hai single-type-import khác kiểu, cùng tên đơn giản

public class InvalidImports {
}
```

Đây là xung đột tại *khai báo import*, không phải đợi đến khi gọi `new Result()`. Cần xóa một import và dùng tên đầy đủ tại chỗ cần kiểu thứ hai.

### 4.2. Hai wildcard: lỗi chỉ khi tên ngắn bị mơ hồ

Đoạn sau cũng **cố ý không biên dịch được**:

```java
package com.example.app;

import com.example.payment.*;
import com.example.network.*;

public class AmbiguousUse {
    Result value = new Result(); // cả hai package đều có Result
}
```

Hai khai báo wildcard tự chúng được phép tồn tại. Lỗi phát sinh ở tham chiếu `Result` vì cả hai kiểu cùng phù hợp. Nếu đổi thành `com.example.payment.Result value = new com.example.payment.Result();` thì không còn mơ hồ.

### 4.3. Ví dụ thực tế: java.util.Date và java.sql.Date

`java.util.Date` và `java.sql.Date` là hai kiểu khác nhau, dù cùng tên đơn giản. Có thể viết:

```java
import java.util.Date;

class Example {
    Date utilDate;
    java.sql.Date sqlDate;
}
```

Đây là đoạn minh họa khai báo kiểu, không phải chương trình có `main`. Không thể import cả `java.util.Date` và `java.sql.Date` bằng hai dòng single-type-import rồi kỳ vọng trình biên dịch tự phân biệt theo ý nghĩa nghiệp vụ. Trong code mới, khi biểu diễn ngày không kèm thời gian, thường nên cân nhắc `java.time.LocalDate`; `java.sql.Date` vẫn có vai trò trong API JDBC.

## 5. Phạm vi ưu tiên tên: cần lưu ý điều gì?

Có thể dùng quy tắc thực hành sau để đọc mã:

1. **Tìm tên đã được khai báo trong phạm vi gần** (ví dụ kiểu thành viên, tham số kiểu), vì có thể che khuất các tên đến từ import.
2. **Kiểm tra import một kiểu cụ thể**, như `import pkg1.Result;`, trước các import theo nhu cầu.
3. **Các kiểu trong package hiện tại** không cần import và được ưu tiên so với kiểu chỉ có từ wildcard ở package khác.
4. **Các import theo nhu cầu** và `java.lang` cung cấp ứng viên khi chưa có khai báo gần hơn; nếu nhiều kiểu cùng tên vẫn phù hợp thì tham chiếu tên ngắn có thể mơ hồ.
5. Khi không chắc, dùng **tên đầy đủ**. Cần lưu ý đây là hướng dẫn thực hành; quy tắc chính xác của ngôn ngữ còn phụ thuộc loại tên, vị trí khai báo và shadowing.

Đặc biệt, một **single-type-import** có thể che khuất kiểu cùng tên được khai báo ở **tệp khác của cùng package**, trong khi khai báo một kiểu top-level cùng tên **ngay trong cùng tệp biên dịch** với single-type-import đó là tình huống xung đột. Vì vậy đừng dựa vào nhận định tuyệt đối rằng “cùng package luôn thắng mọi import”.

## 6. Những giới hạn khác của import

**(a) Import không mở quyền truy cập.** Một kiểu top-level `public` ở package khác có thể được import khi package đó khả dụng. Kiểu top-level không có `public` chỉ được truy cập từ package của nó; thêm `import` không biến nó thành `public`. Thành viên `private`, `protected`, package-private vẫn tuân theo quy tắc truy cập riêng. Có thể import được tên lớp nhưng vẫn không được gọi constructor hoặc method không phù hợp quyền truy cập.

**(b) Package con là package riêng.** `com.example` và `com.example.internal` không có quan hệ “kế thừa” quyền truy cập mặc định. `import com.example.*;` không nhập `com.example.internal.Helper`.

**(c) Import không thay thế classpath hay module path.** Nếu thư viện không nằm trên đường biên dịch, lỗi không thể giải quyết chỉ bằng thêm import. Trong dự án dùng Java Platform Module System, module phải cho phép đọc module chứa kiểu và package liên quan phải được export theo luật module; import không tự thêm `requires` hay `exports`.

**(d) Không import kiểu từ unnamed package vào named package.** Nếu một tệp có `package com.example.app;`, nó không thể dùng import để truy cập lớp nằm trong unnamed/default package. Nên tổ chức các lớp thành named package rõ ràng.

**(e) Import không kế thừa qua các tệp.** Import khai báo trong `Main.java` chỉ có hiệu lực trong đơn vị biên dịch đó; `Another.java` phải tự khai báo import cần thiết. Một import không làm chương trình chạy nhanh hơn hay chậm hơn theo cách có ý nghĩa chỉ do đổi tên ngắn thành tên đầy đủ.

**(f) Import kiểu khác static import.** `import java.lang.Math;` cho phép dùng tên kiểu `Math` nhưng vẫn phải viết `Math.PI`. `import static java.lang.Math.PI;` cho phép tham chiếu `PI` trực tiếp. Static import cũng có quy tắc xung đột tên và khả năng truy cập riêng; nó không giải quyết việc hai lớp top-level khác package có cùng tên.

## 7. Cách xử lý khi gặp lỗi trong dự án

Nếu `javac` báo tên kiểu *ambiguous*, xem những import `*` nào đưa các kiểu cùng tên vào; thay bằng một import cụ thể hoặc dùng tên đầy đủ. Nếu báo xung đột tại dòng `import`, kiểm tra hai import cụ thể cùng tên đơn giản; xóa một dòng. Nếu báo `cannot find symbol` hoặc `package ... does not exist`, kiểm tra chính tả package/class, vị trí source, JAR, classpath hoặc module path trước khi sửa import. Nếu báo kiểu không truy cập được, cần kiểm tra `public`/phạm vi package/module chứ không thêm wildcard.

Trong IDE, chức năng **Organize Imports** có thể bỏ import thừa và chọn kiểu phù hợp, nhưng vẫn nên đọc lại vì IDE không thể tự quyết định lớp nào đúng về mặt nghiệp vụ. Đối với tên phổ biến như `Date`, `List`, `Logger`, `Result`, `Exception`, dùng tên đầy đủ ở vài dòng thường rõ hơn đặt hai bộ import wildcard.

## 8. Ghi nhớ và tự kiểm tra

- Hai lớp `a.Result` và `b.Result` có được tồn tại đồng thời không? **Có**, vì tên đầy đủ khác nhau.
- Hai single-type-import `a.Result` và `b.Result` có cùng nằm trong một tệp Java được không? **Không**, nếu đó là hai kiểu khác nhau.
- Hai wildcard `a.*` và `b.*` có được khai báo cùng nhau không? **Có**; nhưng `Result` bằng tên ngắn sẽ mơ hồ khi cả hai lớp tương ứng đều có thể truy cập.
- `import a.*;` có nhập `a.sub.Helper` không? **Không**.
- `import a.Secret;` có làm lớp package-private `Secret` trở thành công khai không? **Không**.
- Muốn dùng hai kiểu cùng tên trong một tệp, nên viết thế nào? **Import tường minh tối đa một kiểu cùng tên và dùng fully qualified name cho kiểu còn lại**, hoặc viết fully qualified name cho cả hai.

**Quy tắc hữu ích nhất:** import chỉ giảm số lần phải gõ tên package; **tên đầy đủ, khả năng truy cập và cấu hình biên dịch** mới quyết định kiểu nào thực sự được tham chiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-imports-009">Phân biệt package, classpath và module ở mức định hướng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt package, classpath và module ở mức định hướng — Java 17+

## 1. Ba khái niệm giải quyết ba câu hỏi khác nhau

Một dự án Java có nhiều lớp và thư viện cần tổ chức tên, tìm được mã đã biên dịch, đồng thời có thể xác định những phần nào được phép phụ thuộc vào nhau. Ba khái niệm thường gặp là:

| Khái niệm | Câu hỏi được giải quyết | Minh họa |
|---|---|---|
| **Package** | Kiểu này **mang tên gì**, thuộc nhóm tên nào? | `package com.example.library;`, kiểu `com.example.library.Greeter` |
| **Classpath** | JVM/trình biên dịch tìm các lớp và JAR kiểu truyền thống **ở đâu**? | `java -cp out-cp com.example.app.Main` |
| **Module** | Một đơn vị mã được đặt tên **phụ thuộc vào module nào và công khai package nào**? | `module com.example.app { requires com.example.library; }` |

**Tóm lại:** package là **không gian tên**; classpath là **đường dẫn tìm lớp/thư viện**; module là **đơn vị tổ chức phụ thuộc và ranh giới API** trong Java Platform Module System (JPMS, có từ Java 9).

Chúng không thay thế nhau: một chương trình dùng classpath vẫn có package; một named module cũng chứa các package; và file `.class` của module vẫn phải được JVM tìm thấy qua cơ chế thích hợp (thường là **module path**).

## 2. Package: tổ chức tên kiểu

~~~java
package com.example.library;

public final class Greeter {
    private Greeter() {}

    public static String hello(String name) {
        return "Hello, " + name;
    }
}
~~~

- `Greeter` là tên đơn giản của lớp.
- `com.example.library` là package.
- `com.example.library.Greeter` là **tên đầy đủ** của top-level class.
- Một lớp khác trong package `com.example.other` vẫn có thể có tên đơn giản `Greeter` mà không cùng tên đầy đủ.

Trong một file ở package khác, có thể dùng:

~~~java
import com.example.library.Greeter;

// Sau đó:
String message = Greeter.hello("Lan");
~~~

Hoặc không import mà viết `com.example.library.Greeter.hello("Lan")`.

**Import chỉ giúp tra cứu tên trong mã nguồn**. Dòng `import` không nói JVM phải tìm thư viện ở ổ đĩa nào, không làm classpath xuất hiện và không tự tạo object. Trình biên dịch vẫn cần tìm được lớp bằng những đường dẫn và cấu hình build phù hợp.

Package cũng liên quan đến quyền truy cập `package-private`: hai tên `com.example` và `com.example.library` **không phải cùng một package**, dù nhìn như quan hệ cha/con. `import com.example.*;` không tự import kiểu của `com.example.library`.

## 3. Classpath: chỉ nơi tìm lớp và JAR

Sau khi biên dịch, Java tạo ra file `.class`. Khi sử dụng cách chạy **không modular**, classpath mô tả các **thư mục gốc** và/hoặc **JAR** để công cụ tìm những lớp đó.

Ví dụ bố cục kết quả biên dịch:

~~~text
out-cp/
└── com/
    └── example/
        ├── library/
        │   └── Greeter.class
        └── app/
            └── Main.class
~~~

Lệnh:

~~~bash
java -cp out-cp com.example.app.Main
~~~

được hiểu là:

1. Tìm lớp chính có tên đầy đủ `com.example.app.Main`.
2. Tra từ **classpath root `out-cp`**, ghép các thành phần package thành đường dẫn `com/example/app/Main.class`.
3. Khi cần `Greeter`, tìm lớp `com/example/library/Greeter.class` từ classpath thích hợp.

**Không** chọn `-cp out-cp/com/example/app` cho một main class có package `com.example.app`: đó không phải root tương ứng với tên lớp đầy đủ.

Classpath có thể gồm nhiều mục. Dấu phân tách giữa các mục khác nhau theo hệ điều hành:

| Môi trường | Ví dụ classpath gồm thư mục và JAR | Dấu phân tách |
|---|---|---|
| Linux/macOS | `out-cp:lib/tools.jar` | `:` |
| Windows | `out-cp;lib\\tools.jar` | `;` |

Các mục trên chỉ minh họa cú pháp: `lib/tools.jar` không phải thư viện bắt buộc hay file được tạo trong bài này. Đối với ví dụ thực hành chỉ dùng `-cp out-cp`.

**Phân biệt:** `classpath` không phải tên package, và đặt một JAR vào classpath không tự khai báo một named module trong mã ứng dụng.

## 4. Classpath và unnamed module

Trong hệ thống module Java 9+, các lớp được nạp bằng application class loader từ classpath theo kiểu truyền thống thuộc **unnamed module** của class loader đó.

Đối với một class như `Main` chạy bằng `java -cp out-cp ...`, biểu thức:

~~~java
Main.class.getModule().getName()
~~~

trả về `null` (tên module của unnamed module không có giá trị). Điều này **không có nghĩa lớp không có package**: `Main.class.getPackageName()` vẫn có thể là `com.example.app`.

| Biểu thức | Trong bản chạy classpath của ví dụ |
|---|---|
| `Main.class.getName()` | `com.example.app.Main` |
| `Main.class.getPackageName()` | `com.example.app` |
| `Main.class.getModule().getName()` | `null` |

Một chương trình đơn giản **không bắt buộc tạo module-info.java**. Package và classpath vẫn hoạt động với Java 17+ mà không cần biến toàn bộ dự án thành named modules.

## 5. Module: đơn vị có tên, phụ thuộc và ranh giới API

Java Platform Module System (JPMS) cho phép tạo một **named module**, mô tả trong file `module-info.java`.

~~~java
module com.example.library {
    exports com.example.library;
}
~~~

và module sử dụng:

~~~java
module com.example.app {
    requires com.example.library;
}
~~~

Ý nghĩa:

- `module com.example.library` đặt **tên module** là `com.example.library`. Tên module có thể giống tiền tố package, nhưng **module name và package name là hai khái niệm riêng**.
- `exports com.example.library;` cho các module đọc được module thư viện **truy cập những public types và public members được phép** trong package API đó.
- `requires com.example.library;` khai báo module ứng dụng **phụ thuộc vào/đọc được** module thư viện.
- `module-info.java` là **module declaration**, không phải class `Main` và cũng không thay cho khai báo `package` trong các file lớp.

Một module có thể chứa **nhiều package**. Mỗi package không buộc phải trở thành một module riêng. Một module chỉ công khai các package đã `exports` (hoặc công khai có điều kiện bằng cú pháp khác), nên **`public class` trong package không được export chưa đủ để module khác truy cập theo quy tắc JPMS**.

**Lưu ý:** `exports` là công khai API ở mức Java; `opens` phục vụ truy cập phản chiếu sâu trong các trường hợp thích hợp, không tương đương `exports`. Bài nhập môn này chỉ dùng `requires` và `exports`.

## 6. Module path khác classpath ra sao?

**Module path** chứa các vị trí để hệ thống module tìm **named modules**, ví dụ các thư mục module đã biên dịch hoặc modular JAR.

~~~bash
java --module-path out-mod --module com.example.app/com.example.app.Main
~~~

- `out-mod`: vị trí các named modules được biên dịch.
- `com.example.app` trước dấu `/`: **tên module** chứa điểm vào.
- `com.example.app.Main` sau dấu `/`: **tên lớp main**, vẫn có package riêng.
- JPMS dùng các module descriptors để kiểm tra quan hệ `requires` và `exports`.

Bảng so sánh:

| Khía cạnh | Classpath | Module path |
|---|---|---|
| Mục đích cơ bản | Tìm class/JAR của ứng dụng kiểu truyền thống | Tìm named modules |
| Lệnh chạy | `java -cp out-cp com.example.app.Main` | `java --module-path out-mod --module com.example.app/com.example.app.Main` |
| File mô tả module bắt buộc trong ví dụ? | Không; bỏ qua `module-info.java` khi biên dịch các lớp | Có `module-info.java` cho hai named modules |
| Kiểm tra `requires`/`exports` | Không dùng ranh giới named module cho các lớp trên classpath | Có, trong JPMS |
| Package tồn tại không? | Có | Có |

Module path **không chỉ là cách viết khác của classpath**: đặt mã trên module path với descriptor hợp lệ có thể làm thay đổi khả năng đọc và truy cập giữa các module.

## 7. Cấu trúc nguồn dùng chung cho hai cách chạy

Để phân biệt chính xác, ta sử dụng **cùng một bộ file nguồn**, rồi biên dịch theo hai chế độ. Cấu trúc:

~~~text
project/
└── src/
    ├── com.example.library/
    │   ├── module-info.java
    │   └── com/
    │       └── example/
    │           └── library/
    │               └── Greeter.java
    └── com.example.app/
        ├── module-info.java
        └── com/
            └── example/
                └── app/
                    └── Main.java
~~~

Trong ví dụ:

- Có **hai named modules** để thử chế độ module.
- Có **hai packages** chứa class: `com.example.library` và `com.example.app`.
- Khi chạy chế độ classpath, ta chỉ biên dịch hai file `Greeter.java` và `Main.java`, **không biên dịch hai file `module-info.java`**.
- Khi chạy chế độ module path, ta biên dịch cả descriptors theo `--module-source-path src`.

Điều này giúp thấy rõ package vẫn tồn tại trong cả hai trường hợp; sự khác nhau thể hiện ở **cách khai báo/giải quyết module** và **cách gọi chương trình**.

## 8. Cú pháp mã nguồn Java 17+ đầy đủ

Tạo đúng **bốn file** theo cấu trúc tại mục 7.

**File 1 — `src/com.example.library/module-info.java`:**

~~~java
module com.example.library {
    exports com.example.library;
}
~~~

**File 2 — `src/com.example.library/com/example/library/Greeter.java`:**

~~~java
package com.example.library;

public final class Greeter {
    private Greeter() {}

    public static String hello(String name) {
        return "Hello, " + name;
    }
}
~~~

**File 3 — `src/com.example.app/module-info.java`:**

~~~java
module com.example.app {
    requires com.example.library;
}
~~~

**File 4 — `src/com.example.app/com/example/app/Main.java`:**

~~~java
package com.example.app;

import com.example.library.Greeter;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.out.println("main class=" + Main.class.getName());
        System.out.println("library class=" + Greeter.class.getName());
        System.out.println("message=" + Greeter.hello("Lan"));
        System.out.println("app module=" + Main.class.getModule().getName());
        System.out.println("library module=" + Greeter.class.getModule().getName());
    }
}
~~~

Ở cả hai chế độ chạy, hai dòng tên class vẫn giữ package như nhau. Hai dòng `module=...` giúp nhận diện sự khác biệt: **unnamed module** khi chạy classpath và **named modules** khi chạy module path.

## 9. Cách 1 — biên dịch/chạy theo classpath

Đứng trong thư mục `project` có chứa `src`. Dùng **JDK 17 trở lên**, không cần Maven/Gradle, thư viện ngoài hoặc preview features.

**Bash (Linux/macOS):**

~~~bash
mkdir -p out-cp
javac --release 17 -encoding UTF-8 -d out-cp src/com.example.library/com/example/library/Greeter.java src/com.example.app/com/example/app/Main.java
java -cp out-cp com.example.app.Main
~~~

**PowerShell (Windows):**

~~~powershell
New-Item -ItemType Directory -Force -Path out-cp | Out-Null
javac --release 17 -encoding UTF-8 -d out-cp .\src\com.example.library\com\example\library\Greeter.java .\src\com.example.app\com\example\app\Main.java
java -cp out-cp com.example.app.Main
~~~

Lệnh `javac` chỉ nêu **hai source files có class**, không nêu bất kỳ `module-info.java` nào. `out-cp` là thư mục đích chứa các class theo tên package và là **classpath root** lúc chạy.

### Exact expected stdout — classpath

~~~text
main class=com.example.app.Main
library class=com.example.library.Greeter
message=Hello, Lan
app module=null
library module=null
~~~

`Main.class.getModule()` và `Greeter.class.getModule()` đều trả module descriptor runtime của **unnamed module** tương ứng; `getName()` trả `null`. Hai class vẫn có package và tên đầy đủ như in ở hai dòng đầu.

## 10. Cách 2 — biên dịch/chạy theo named modules

Vẫn đứng trong thư mục `project`, dùng nguyên bốn file nguồn phía trên.

**Bash (Linux/macOS):**

~~~bash
mkdir -p out-mod
javac --release 17 -encoding UTF-8 --module-source-path src -d out-mod -m com.example.library,com.example.app
java --module-path out-mod --module com.example.app/com.example.app.Main
~~~

**PowerShell (Windows):**

~~~powershell
New-Item -ItemType Directory -Force -Path out-mod | Out-Null
javac --release 17 -encoding UTF-8 --module-source-path src -d out-mod -m com.example.library,com.example.app
java --module-path out-mod --module com.example.app/com.example.app.Main
~~~

`--module-source-path src` chỉ đường dẫn nguồn chứa các thư mục tên module; `-m` chọn những module cần biên dịch; `-d out-mod` xuất các class đã biên dịch theo các module.

Kết quả có dạng:

~~~text
out-mod/
├── com.example.library/
│   ├── module-info.class
│   └── com/example/library/Greeter.class
└── com.example.app/
    ├── module-info.class
    └── com/example/app/Main.class
~~~

JPMS giải quyết `requires com.example.library` từ app và kiểm tra `exports com.example.library` từ thư viện.

### Exact expected stdout — module path

~~~text
main class=com.example.app.Main
library class=com.example.library.Greeter
message=Hello, Lan
app module=com.example.app
library module=com.example.library
~~~

So với cách 1, **ba dòng đầu giữ nguyên**, chỉ khác thông tin module của các class. Điều này minh họa rằng cùng source code với package có thể biên dịch và chạy theo hai mô hình triển khai khác nhau.

## 11. Những hiểu lầm thường gặp

| Nhầm lẫn | Hiểu đúng |
|---|---|
| Package chính là thư mục | Package là namespace; thư mục là cách tổ chức file thường khớp tên package |
| Chỉ khai báo `package` là JVM tự tìm được class | Phải biên dịch và cung cấp vị trí class/JAR thích hợp |
| `import` thay thế classpath | Import giải quyết tên tại compile time, không cung cấp file thư viện |
| `-cp out-cp/com/example/app` với `com.example.app.Main` | Classpath phải là root chứa đường dẫn `com/example/app`, tức `out-cp` |
| Module là một class | Module là đơn vị có tên bao gồm các package và descriptor |
| Module bắt buộc có đúng một package | Một module có thể chứa nhiều package |
| Package tên `com.example.app` thì module cũng phải tên ấy | Hai tên là hai khái niệm độc lập; ví dụ trùng nhau chỉ để dễ hiểu |
| `public class` ở một package trong named module là luôn nhìn thấy từ mọi module | Module còn cần export package và điều kiện readability |
| `requires` tự import class vào mã nguồn | Vẫn cần import hoặc tên đầy đủ khi dùng kiểu trong package khác |
| `exports` biến package thành classpath directory | Exports khai báo API của module, không thay thế đường dẫn tìm module |
| Chạy classpath nhưng vẫn mong `getModule().getName()` in tên module khai báo trong các file `module-info.java` bị bỏ qua | Các class được biên dịch/đặt trên classpath trong ví dụ thuộc unnamed module |
| Nhầm `--module-path` với `--module-source-path` | Module path dùng tìm named modules đã có; module source path định vị mã nguồn modules cho compiler |

## 12. Chọn cách nào khi bắt đầu học Java?

- Bài Java nhỏ và ứng dụng chưa chia module: **dùng package rõ ràng và classpath**, học `javac -d out`, `java -cp out package.Main` trước.
- Khi đọc dự án có `module-info.java`: nhận diện **tên module**, những `requires` và `exports` cơ bản; biết cách tìm module path và điểm vào.
- Khi dùng IDE/Maven/Gradle: build tool có thể cấu hình classpath/module path tự động, nhưng ba khái niệm vẫn **khác nhau**.
- Khi gặp lỗi “cannot find symbol”, “ClassNotFoundException”, “NoClassDefFoundError” hoặc lỗi truy cập module: kiểm tra lần lượt **tên package/import**, việc biên dịch/tìm class, và nếu có JPMS thì **module descriptor/readability/exports**. Không kết luận chỉ từ một thông báo lỗi rằng package, classpath và module là cùng một thứ.

**Hạn chế của bài:** JPMS còn có `opens`, `uses`, `provides ... with`, automatic modules, strong encapsulation và nhiều tình huống kết hợp classpath/module path. Ở mức định hướng, cần hiểu vai trò chính của `requires` và `exports`, chưa cần tự xây hệ thống module phức tạp.

## 13. Bài tập có đáp án

**A.** `com.example.data.User` gồm simple name và package gì?  
**Đáp án:** simple name `User`, package `com.example.data`.

**B.** Có `Greeter.class` ở `out-cp/com/example/library/Greeter.class`, classpath root nên là gì?  
**Đáp án:** `out-cp`, không phải `out-cp/com/example/library`.

**C.** Hai package `com.example.app` và `com.example.library` nhất thiết phải nằm trong hai module riêng không?  
**Đáp án:** **Không**; một module có thể chứa nhiều package. Ví dụ này chọn hai module để minh họa `requires` và `exports`.

**D.** File `module-info.java` nào khai báo module ứng dụng phụ thuộc thư viện?

~~~java
module com.example.app {
    requires com.example.library;
}
~~~

**E.** Nếu thư viện bỏ dòng `exports com.example.library;` mà app vẫn cần tham chiếu công khai `Greeter` theo mô hình named modules, có vấn đề gì?  
**Đáp án:** **Có**. App không được truy cập trực tiếp API thuộc package không được export theo quy tắc JPMS, dù lớp `Greeter` có khai báo `public`.

**F.** Trong cách chạy classpath của ví dụ, `Main.class.getModule().getName()` trả gì?  
**Đáp án:** `null`, vì class thuộc unnamed module.

**G.** Vì sao `import com.example.library.Greeter;` không tự thay thế `requires com.example.library;` trong named module `com.example.app`?  
**Đáp án:** `import` giúp viết tên ngắn trong mã nguồn; `requires` khai báo quan hệ đọc/phụ thuộc ở mức module. Hai nhiệm vụ khác nhau.

## 14. Checklist và kết luận

- [ ] Phân biệt `package`, `classpath` và `module` theo chức năng.
- [ ] Hiểu tên kiểu đầy đủ gồm package + simple name.
- [ ] Biết classpath root của class đã biên dịch theo cấu trúc package.
- [ ] Biết `import` không tự cung cấp class/JAR còn thiếu.
- [ ] Nhận diện `module-info.java`, `module`, `requires` và `exports`.
- [ ] Hiểu named module có thể bao gồm nhiều package.
- [ ] Phân biệt unnamed module của classpath với named module chạy qua module path.
- [ ] Biết công dụng của `--module-source-path` khi biên dịch nguồn module.
- [ ] Biết điểm vào `java --module-path ... --module name/package.Main`.
- [ ] Chạy được cùng hai source classes ở chế độ classpath và bộ bốn file ở chế độ module path, đối chiếu stdout.
- [ ] Không coi package, classpath và module là ba từ gọi cho một tính năng duy nhất.

**Kết luận:** **Package đặt tên và nhóm kiểu**, **classpath chỉ nơi tìm lớp/JAR**, còn **module khai báo đơn vị phụ thuộc và các package API được công khai**. Trong Java 17+, có thể bắt đầu từ package + classpath rồi học `module-info.java` khi gặp ứng dụng modular; hiểu đúng trách nhiệm từng khái niệm giúp đọc lỗi biên dịch/khởi chạy và tổ chức dự án rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)
