<a id="back-to-top"></a>

# Nạp và định danh lớp

## Menu
- [Nạp lớp là gì và vì sao JVM không nạp toàn bộ chương trình ngay từ đầu](#class-loading-and-identity-001)
- [Bootstrap, platform, application class loader và cơ chế ủy quyền](#class-loading-and-identity-002)
- [Định danh lớp bằng tên nhị phân kết hợp bộ nạp định nghĩa](#class-loading-and-identity-003)
- [Bộ nạp khởi xướng, bộ nạp định nghĩa và ràng buộc giữa các bộ nạp](#class-loading-and-identity-004)
- [Class path, module path và ảnh hưởng của module đến truy cập lớp](#class-loading-and-identity-005)
- [Class object, lớp mảng và cách JVM tạo biểu diễn kiểu khi chạy](#class-loading-and-identity-006)
- [Nạp lớp theo yêu cầu qua Class.forName và ClassLoader.loadClass](#class-loading-and-identity-007)
- [Khi nào lớp có thể được dỡ bỏ và liên hệ với vòng đời class loader](#class-loading-and-identity-008)

## <a id="class-loading-and-identity-001">Nạp lớp là gì và vì sao JVM không nạp toàn bộ chương trình ngay từ đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp lớp là gì và vì sao JVM không nạp toàn bộ chương trình ngay từ đầu?

## 1. Câu trả lời ngắn: JVM xây dựng chương trình đang chạy từ các kiểu cần thiết

Một chương trình Java có thể chứa hàng nghìn tệp `.class` trên classpath/module path, cùng rất nhiều lớp trong thư viện JDK. **Không có quy tắc “khởi động là nạp và khởi tạo toàn bộ các lớp đó”.** JVM khởi động từ lớp ban đầu (thông thường là lớp chứa `main`), rồi tải, liên kết và khởi tạo các lớp/interface khác theo nhu cầu thực thi và những yêu cầu liên quan.

Đối với **lớp hoặc interface không phải mảng**, *class loading* là quá trình tìm **biểu diễn nhị phân** của một kiểu theo tên (thường là các byte của `.class` trong thư mục, JAR, module hoặc nguồn do class loader cung cấp), rồi tạo **biểu diễn kiểu tại runtime** để JVM có thể quản lý kiểu đó. Đây là một công việc khác với việc **chạy khối `static {}`** hoặc tạo **instance** bằng `new`.

Ví dụ: JAR của một ứng dụng có lớp `ReportExporter` nhưng người dùng không bao giờ bấm nút Export. Không có nghĩa vụ phải **khởi tạo** `ReportExporter` ngay ở startup. Tuy nhiên, không được biến nhận xét này thành khẳng định cứng rằng `ReportExporter` chắc chắn chưa từng được **load**: JVM có thể sớm resolve, verify hoặc các framework có thể chủ động tìm/nạp kiểu ấy.

Một cách nói chính xác hơn:

> JVM hỗ trợ **dynamic loading** và có thể trì hoãn nhiều công việc tới khi cần; thời điểm loading/linking cụ thể có một phần do JVM implementation quyết định, còn các sự kiện kích hoạt **initialization** phải tuân thủ quy tắc ngôn ngữ/JVM.

### Những câu hỏi cần phân biệt

- **Class có trong JAR?** Điều đó chỉ nói mã nhị phân có sẵn trên đường tìm kiếm; chưa nói class đã được JVM load.
- **Class đã loaded?** JVM có representation của kiểu đó; chưa chắc class đã initialized.
- **Class đã linked?** Việc verification/preparation đã hoàn tất và symbolic references được resolve theo mức cần thiết; chưa chắc `<clinit>` đã chạy.
- **Class đã initialized?** Các static initializer/khởi tạo static field không phải constant variable đã được thực thi theo quy tắc class initialization.
- **Đã có object?** Một instance đã được tạo; một class có thể initialized mà chưa có instance nào.
- **Đã có method chạy?** Có thể là static method không cần instance, instance method gọi qua object, hoặc một method khác dẫn đến yêu cầu tải kiểu.

## 2. Ba giai đoạn loading, linking, initialization

Trong kiến trúc JVM, ba pha lớn có quan hệ thứ tự ở mức ngữ nghĩa:

~~~text
Lớp được yêu cầu/đề cập bởi hoạt động phù hợp
             |
             v
        LOADING
  tìm bytes / define Class
             |
             v
        LINKING
  verification -> preparation -> resolution (*)
             |
             v
      INITIALIZATION
  chạy logic khởi tạo class/interface
             |
             v
       Sử dụng kiểu theo ngữ nghĩa Java

(*) Resolution có thể diễn ra sớm hoặc trì hoãn; không phải mọi
    symbolic reference bắt buộc resolve cùng một thời điểm.
~~~

### 2.1. Loading — tìm/định nghĩa kiểu

Với một kiểu thông thường, class loader có thể:

1. Nhận yêu cầu về **binary name** như `com.example.service.ReportExporter`.
2. Tìm một định nghĩa thích hợp từ nguồn của loader (JAR, thư mục, module, bytes sinh động, ...).
3. Cung cấp class bytes cho JVM để **define** kiểu; JVM xây cấu trúc nội bộ cho class/interface và một đối tượng `java.lang.Class` biểu diễn kiểu ấy.
4. Ghi nhận quan hệ **defining class loader**, vì class identity ở runtime không chỉ dựa vào tên.

`ClassLoader.loadClass(...)` là một API cấp Java thường được dùng để đề nghị tải lớp, nhưng các chi tiết loader ủy quyền và nguồn bytes phụ thuộc triển khai.

**Không nhầm:** tìm thấy file `.class` ở ổ đĩa chưa đồng nghĩa phương thức static initializer của kiểu đó đã chạy. Ngược lại, một kiểu runtime không phải lúc nào cũng đến từ một file `.class` riêng đọc trực tiếp từ ổ đĩa: runtime có thể sinh bytecode hoặc định nghĩa lớp từ nguồn khác.

### 2.2. Linking — chuẩn bị lớp để dùng đúng và an toàn

*Linking* có ba nhóm hoạt động chính:

| Công đoạn | Mục đích |
| --- | --- |
| **Verification** | Kiểm tra ràng buộc của class file/bytecode, độ hợp lệ của stack type, control flow, quyền truy cập và những quy tắc kiểm chứng liên quan |
| **Preparation** | Cấp/chuẩn bị bộ nhớ và cấu trúc cho static fields; đặt giá trị ban đầu mặc định theo quy tắc, xử lý những hằng được khai báo qua `ConstantValue` |
| **Resolution** | Biến symbolic references tới class, field, method... thành tham chiếu runtime phù hợp, kiểm tra khả năng liên kết và truy cập |

**Preparation không đồng nghĩa initialization.** Ví dụ `static int count = 5;` nhận giá trị mặc định `0` ở giai đoạn chuẩn bị, rồi logic khởi tạo static field gán `5` trong initialization. Với `static final int LIMIT = 10;` là compile-time constant variable, class file có thể có `ConstantValue`; các quy tắc gán hằng trong preparation khác với thực thi một biểu thức runtime.

Một implementation có thể **resolve sớm** nhiều symbolic reference, hoặc trì hoãn cho đến khi thực tế sử dụng. Đặc tả yêu cầu bảo toàn các điều kiện như class được load trước khi link, được verify/prepare trước khi initialize và lỗi liên kết được phát sinh ở thời điểm chương trình có hành động có thể yêu cầu quá trình liên kết tương ứng. Vì thế **lazy loading không có nghĩa “mỗi lớp luôn được load đúng tại dòng Java đầu tiên đề cập tên nó”**.

### 2.3. Initialization — thực thi `<clinit>` nếu cần

Initialization xử lý static field initializers và `static {}` theo đúng thứ tự nguồn của các thành phần liên quan, thông thường được compiler gom thành class/interface initialization method tên JVM **`<clinit>`** khi cần.

~~~java
class Settings {
    static int port = loadPort();
    static {
        System.out.println("SETTINGS_INITIALIZED");
    }
    static int loadPort() {
        return 8080;
    }
}
~~~

Đây là **mảnh mã minh họa**, chưa phải chương trình tự chạy vì không có `main`. Khi `Settings` được initialized, `loadPort()` được gọi và static block in thông báo; nhưng chỉ có việc `Settings.class` xuất hiện trong một expression **không tự động kích hoạt** static block.

Initialization thường xảy ra **một lần cho mỗi runtime class được định nghĩa bởi loader tương ứng**, với cơ chế đồng bộ giữa các thread. Nếu một thread đang initialize class và thread khác cần class ấy, JVM áp dụng quy tắc phối hợp initialization; không chạy `<clinit>` tùy tiện đồng thời nhiều lần cho cùng class.

Class có thể không cần `<clinit>` riêng nếu không có logic khởi tạo động; vẫn có trạng thái “đã initialized” của kiểu. Cũng đừng dùng cụm “`new` mới là load”: một class có thể đã loaded và initialized từ một static call, không hề có instance nào.

## 3. Vì sao không nạp hết mọi lớp lúc startup?

### 3.1. Tránh công việc có thể không bao giờ cần

Giả sử hệ thống hỗ trợ bốn tính năng:

~~~text
app.jar
├── Main.class                 khởi động ứng dụng
├── core/RequestHandler.class thường dùng
├── export/PdfExporter.class  chỉ khi export PDF
├── billing/InvoiceJob.class  chỉ khi có lịch tính phí
└── admin/MigrationTool.class chỉ khi chạy migration
~~~

Nếu request hiện tại chỉ dùng `RequestHandler`, việc bắt buộc load và link mọi class của export, billing, admin ngay lập tức có thể làm phát sinh I/O, kiểm chứng, phân bổ metadata và phụ thuộc không cần thiết. Mô hình dynamic loading cho phép JVM **không làm tất cả những việc ấy từ đầu**.

Ví dụ cây trên là hình dung về **artifact chứa class**, không phải bản ghi cho biết lớp nào chính xác đã được loaded trong JVM cụ thể. Các class loader, framework và JVM có thể chủ động tải thêm lớp do quét annotation, discovery, proxy, verification, resolution hoặc warm-up.

### 3.2. Cân bằng khởi động, bộ nhớ và độ trễ lần dùng đầu

Lợi ích và chi phí thường gặp:

| Lựa chọn | Có thể cải thiện | Cái giá cần cân nhắc |
| --- | --- | --- |
| Load/link theo nhu cầu | Startup ít việc hơn, tránh xử lý nhiều class không được dùng, có thể giảm metadata resident | Lần đầu dùng tính năng có thể xuất hiện độ trễ load, verify, resolve và initialization |
| Eager initialization/preloading được kiểm soát | Dời công việc khỏi request đầu tiên, phát hiện lỗi sớm hơn, warm up | Startup lâu hơn, giữ thêm metadata/object và có thể khởi tạo phần không cần |
| Quét classpath/module path bằng framework | Tự đăng ký component, dependency injection, plugin discovery | Nhiều hoạt động class-loading hoặc reflective access trước khi request thật xuất hiện |

Không suy ra rằng **loading chiếm toàn bộ chi phí startup**. Một ứng dụng Java còn có JVM startup, GC/JIT runtime setup, khởi tạo framework, I/O, network connection, config, database migration... Và class loading không có quan hệ tuyến tính đơn giản “càng ít class thì chương trình luôn nhanh hơn” trong mọi workload.

### 3.3. Hỗ trợ plugin và định nghĩa lớp động

Khi hệ thống chỉ biết plugin sau khi khởi động, ví dụ plugin được lắp vào thư mục/module layer hoặc được tải từ một nguồn tùy biến, cơ chế class loader cho phép nạp định nghĩa mới khi có yêu cầu. Tuy nhiên, không phải cứ bỏ thêm JAR vào thư mục khi JVM đang chạy là ứng dụng **tự** nhận plugin; hệ thống cần logic discovery, loader/module layer, versioning và quản lý vòng đời.

Nhiều bytecode generation framework tạo proxy hoặc helper class ở runtime. Những kiểu này có thể không tồn tại dưới dạng file nguồn Java tương ứng trong source tree.

### 3.4. Không phải cơ chế “không tải dependency lỗi”

Dynamic loading có thể khiến một tính năng ít dùng chỉ bộc lộ lỗi missing class/method khi đường code đó được thực thi. Đây là **rủi ro trì hoãn lỗi**, không phải sự bảo đảm rằng JVM “sẽ bỏ qua vĩnh viễn” mọi dependency sai.

Một JVM có quyền link/resolve sớm trong phạm vi đặc tả; thư viện/framework cũng có thể load/initialize sớm. Nên triển khai smoke tests cho những đường plugin/feature quan trọng, thay vì coi “main đã chạy được” là bằng chứng mọi classpath dependency đều tốt.

## 4. JVM biết tới lớp khác bằng những con đường nào?

Một class file thường chứa **constant pool** với nhiều **symbolic references**, ví dụ tên kiểu `java/lang/String`, `com/example/Feature`, hoặc method descriptor chứa kiểu đối số. Những tên ấy giúp JVM tìm lớp khi cần resolve/liên kết.

~~~text
Main.class
  constant_pool:
    class Main
    class com/example/Feature
    Methodref com/example/Feature.run:()V
    class java/lang/String
    ...
~~~

Đây là sơ đồ mang tính khái niệm, không phải `javap -v` chính xác cho file thực tế.

**Có tên trong constant pool KHÔNG đồng nghĩa:** JVM đã đọc toàn bộ class bytes của tất cả class được nhắc đến, đã khởi tạo mọi superclass/interface liên quan hoặc đã thực thi static initializer của bất kỳ lớp nào. Một số kiểu có thể được load/resolve vì verification hoặc linking; việc lựa chọn thời điểm có giới hạn linh hoạt theo đặc tả.

Những hoạt động thường dẫn tới việc cần biết một lớp ở runtime gồm:

- `new SomeClass()` khi tạo object.
- Truy cập static field không phải compile-time constant hoặc gọi static method.
- Gọi instance method, kiểm tra cast/`instanceof`, truy cập field trên object (với class/reference cần thiết).
- Phản chiếu `Class.forName`, truy vấn method/field, `ClassLoader.loadClass` hoặc framework scanning.
- Method handles, `invokedynamic`, generated proxies, serialization, service loading khi tính năng sử dụng chúng.
- Việc link/verify/resolve lớp khác mà JVM quyết định cần xử lý.

**Không phải mọi hoạt động trên đều lập tức làm chạy static initializer của mọi kiểu liên quan.** Cần phân biệt *class được tải/resolve để hiểu kiểu* với *class phải initialization do hành vi sử dụng chủ động*.

## 5. Những trường hợp thường kích hoạt initialization

Theo JLS/JVMS (trong bối cảnh Java 17), những trường hợp đáng nhớ gồm:

1. **Tạo một instance** của class bằng `new` (trước khi constructor thực thi, class cần được initialized đúng quy tắc).
2. **Gọi static method** được class/interface tương ứng khai báo, ví dụ `Feature.run()`.
3. **Đọc static field không phải constant variable**, ví dụ `Settings.port`.
4. **Gán static field**, ví dụ `Settings.port = 9090`.
5. Một số yêu cầu từ **reflection** và API runtime tương ứng, như `Class.forName(String)`, cũng có thể trực tiếp yêu cầu initialization.
6. **Khởi động JVM:** lớp khởi đầu cần được load/link/initialize trước khi chạy `main`.
7. Khi initialized class, các **superclass** của nó được initialized trước, cùng những **superinterface khai báo default methods** theo quy tắc hiện đại nếu chưa được initialized.

Những quy tắc nói về class/interface **thực sự khai báo field hoặc method được resolve**, không chỉ tên tĩnh ở bề ngoài của một expression.

### 5.1. Class literal không tự khiến `<clinit>` chạy

~~~java
Class<?> type = Feature.class;
~~~

Biểu thức này tạo hoặc lấy một đối tượng `Class` biểu thị kiểu `Feature` cho chương trình, và JVM có thể load/link đủ thông tin cần thiết. Nhưng **không kích hoạt initialization của Feature chỉ vì class literal**. Đây là một ví dụ điển hình tại sao quan sát “chưa in static block” **không chứng minh class chưa loaded**.

### 5.2. Compile-time constant có thể được inline

~~~java
class Constants {
    static {
        System.out.println("INIT_CONSTANTS");
    }
    static final int LIMIT = 10;
    static int computed = Integer.parseInt("10");
}
~~~

Nếu caller tham chiếu `Constants.LIMIT`, Java compiler có thể nhúng literal `10` vào bytecode của caller; chỉ đọc compile-time constant variable này không kích hoạt initialization của `Constants`. Trái lại, truy cập `Constants.computed` thường dẫn đến `getstatic` và initialization nếu chưa xảy ra.

Điều này cũng có hậu quả ABI: đổi giá trị compile-time constant rồi chỉ thay JAR có thể không làm những caller đã biên dịch cũ nhận giá trị mới; cần rebuild caller khi phù hợp.

### 5.3. Tạo mảng không tự khởi tạo lớp phần tử

~~~java
Feature[] slots = new Feature[3];
~~~

Thao tác tạo object mảng có thể khiến runtime cần kiểu mảng/thành phần, nhưng **không tự gọi `Feature.<clinit>`** chỉ vì các phần tử có kiểu `Feature`. Các phần tử reference mặc định là `null`. Nếu sau đó thực sự viết `new Feature()`, class sẽ cần được initialized.

Các kiểu mảng do JVM tạo theo quy tắc riêng, **không có một file `[LFeature;.class` thông thường** cho mỗi kiểu mảng. Với `int[]`, các lớp thành phần primitive và việc JVM cung cấp `Class` object cho primitive cũng khác cơ chế đọc bytes của user-defined class.

### 5.4. `Class.forName` có chế độ initialization

| API / biểu thức | Thường cần load/resolve? | Có yêu cầu initialization không? |
| --- | --- | --- |
| `SomeType.class` | Có thể khiến runtime cần thông tin biểu diễn kiểu | **Không chỉ vì class literal** |
| `Class.forName("pkg.SomeType")` | Tìm/resolve kiểu theo API | **Có**, với overload một tham số cho class thông thường |
| `Class.forName(name, false, loader)` | Load/link theo hợp đồng API | **Không yêu cầu initialization** |
| `loader.loadClass(name)` | Yêu cầu loader tìm lớp | **Thông thường không** initialize, nhưng lớp khác có thể có side effect riêng từ custom loader |
| `new SomeType()` | Phải có class để tạo instance | **Có**, trước khi constructor chạy |
| `SomeType.nonConstantStatic` | Phải resolve field phù hợp | **Có**, nếu truy cập static field không phải constant variable |
| `SomeType.COMPILE_TIME_CONSTANT` | Caller có thể dùng literal đã inline | **Không chỉ do đọc constant variable** |

Đặc biệt, `Class.forName(name, false, loader)` vẫn có thể load và link, gây lỗi class loading/linking dù không initialize; **`initialize=false` không đồng nghĩa “đừng nạp class”**.

Không dùng bảng này làm lời hứa cứng về log class-loading: JVM có thể nạp kiểu sớm do linking/verification, và custom loader có thể có hành vi phụ.

## 6. Chương trình Java 17 hoàn chỉnh: minh họa “có Class nhưng chưa initialized”

Tạo đúng một file **`LazyLoadingDemo.java`**:

~~~java
public final class LazyLoadingDemo {
    static final class OnlyMentioned {
        static {
            System.out.println("INIT_ONLY_MENTIONED");
        }
    }

    static final class Feature {
        static {
            System.out.println("INIT_FEATURE");
        }

        static String run() {
            return "READY";
        }
    }

    static final class Config {
        static {
            System.out.println("INIT_CONFIG");
        }

        static final int LIMIT = 10;
        static int dynamic = compute();

        static int compute() {
            return 99;
        }
    }

    static final class Untouched {
        static {
            System.out.println("INIT_UNTOUCHED");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("MAIN");

        Class<?> description = OnlyMentioned.class;
        System.out.println("CLASS=" + description.getSimpleName());

        Class.forName(Feature.class.getName(), false,
                LazyLoadingDemo.class.getClassLoader());
        System.out.println("FOUND_FEATURE");

        System.out.println("CONST=" + Config.LIMIT);
        System.out.println("BEFORE_RUN");
        System.out.println("RESULT=" + Feature.run());

        System.out.println("BEFORE_CONFIG");
        System.out.println("DYNAMIC=" + Config.dynamic);

        Class.forName(OnlyMentioned.class.getName());
        System.out.println("FOR_NAME_DONE");
        System.out.println("END");
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac --release 17 LazyLoadingDemo.java
java LazyLoadingDemo
~~~

**Stdout dự kiến và có thể kiểm chứng bằng chương trình:**

~~~text
MAIN
CLASS=OnlyMentioned
FOUND_FEATURE
CONST=10
BEFORE_RUN
INIT_FEATURE
RESULT=READY
BEFORE_CONFIG
INIT_CONFIG
DYNAMIC=99
INIT_ONLY_MENTIONED
FOR_NAME_DONE
END
~~~

### Đọc kết quả từng mốc

1. `MAIN`: lớp `LazyLoadingDemo` đã sẵn sàng để thực thi method `main`.
2. `OnlyMentioned.class` cho một đối tượng `Class`. `getSimpleName()` được gọi nhưng **chưa thấy `INIT_ONLY_MENTIONED`**; điều này minh họa **class literal không yêu cầu initialization**, **không** chứng minh class chưa loaded.
3. `Class.forName(..., false, ...)` yêu cầu biết lớp `Feature` mà **chưa initialize** nó; `FOUND_FEATURE` xuất hiện trước `INIT_FEATURE`.
4. `Config.LIMIT` là compile-time constant `10`; dòng `CONST=10` không làm in `INIT_CONFIG`. Đây không phải dấu hiệu rằng file `Config.class` chưa từng được load.
5. Lần đầu gọi `Feature.run()`, JVM bảo đảm `Feature` được initialized trước khi thực thi method: `INIT_FEATURE` xuất hiện trước `RESULT=READY`.
6. Lần đọc `Config.dynamic` kích hoạt initialization của `Config`: `INIT_CONFIG` xuất hiện trước `DYNAMIC=99`.
7. Lời gọi `Class.forName(OnlyMentioned.class.getName())` ở overload một tham số yêu cầu initialization: `INIT_ONLY_MENTIONED` xuất hiện trước `FOR_NAME_DONE`.
8. `Untouched` không bao giờ được chủ động sử dụng trong chương trình. Không thấy `INIT_UNTOUCHED` nghĩa là **không thấy class initialization**, nhưng **không đủ chứng minh runtime chưa load/verify class đó**.

Điểm quan trọng nhất của ví dụ không phải đoán số lượng class đã load từ stdout, mà là **dùng static initializer để quan sát riêng sự kiện initialization**. Muốn quan sát loading, sử dụng log công cụ ở mục 7.

### Nếu thay `false` thành `true`?

Đoạn:

~~~java
Class.forName(Feature.class.getName(), true,
        LazyLoadingDemo.class.getClassLoader());
~~~

là **biến thể để thử riêng, không phải code nguyên bản của bài thực hành**. Khi `initialize=true`, `INIT_FEATURE` sẽ xuất hiện ngay ở lần `Class.forName` đó, trước `FOUND_FEATURE`. Đến lần gọi `Feature.run()` sau này, static initializer sẽ **không chạy lại** với cùng runtime class. Tương tự, chỉ khởi tạo một class khác có cùng binary name nhưng thuộc defining loader khác mới là một identity class khác cần xét riêng.

## 7. Quan sát class thực sự được load bằng công cụ JDK

### 7.1. `-Xlog:class+load=info` trên HotSpot / JDK 17

Với OpenJDK/HotSpot hỗ trợ Unified Logging:

~~~bash
java -Xlog:class+load=info LazyLoadingDemo
~~~

Bạn sẽ thấy nhiều log event dạng class loading, ngoài stdout của ví dụ. Số dòng log, tên module/JAR/loader và thứ tự có thể thay đổi theo JDK, classpath, cấu hình hoặc cách implementation thực hiện resolution; vì vậy **không đưa “stdout chính xác” cho chế độ bật logging**.

Ghi class-load log riêng vào file:

~~~bash
java -Xlog:class+load=info:file=class-load.log:uptime,level,tags LazyLoadingDemo
~~~

Sau đó lọc các dòng chứa tên app:

~~~bash
# Bash trên Linux/macOS
grep 'LazyLoadingDemo' class-load.log

# PowerShell trên Windows
Select-String -Path class-load.log -Pattern 'LazyLoadingDemo'
~~~

Lưu ý: các class trong JDK như `java.lang.Object`, `java.lang.String`, class loader, I/O và nhiều thành phần runtime có thể được tải trước khi `main` in dòng đầu tiên. Một JVM startup chỉ tải mỗi class `Main` theo nghĩa đen là mô tả **không chính xác**.

Dạng cũ `-verbose:class` cũng có thể dùng với nhiều Java launcher để xem thông tin class loading. Đối với hệ thống đang chạy production, bật class-load log mức cao có thể tăng lượng log và chi phí I/O; hãy thử trong môi trường kiểm thử.

### 7.2. `javap` không phải class-load log

~~~bash
javap -v LazyLoadingDemo
javap -c -p LazyLoadingDemo
~~~

`javap -v` cho biết **cấu trúc class file** và constant-pool references, còn `javap -c` hiển thị bytecode. Đây là phân tích **tĩnh của class file**, không tự nói thứ tự runtime đã load các lớp khác.

Ví dụ `javap` có thể cho thấy tham chiếu tới `Feature.run()` và `Config.dynamic`. Nhưng **việc comment `// Method ...` xuất hiện trong disassembly không chứng minh method hay `<clinit>` đã thực thi**.

### 7.3. JAR chứa class không có nghĩa JVM đã load tất cả

~~~bash
jar tf app.jar
~~~

Lệnh trên (nếu có `app.jar`) chỉ liệt kê file bên trong archive. Nó không khởi chạy ứng dụng và không phải thống kê lớp đã được JVM load. Đây là khác biệt giữa **có trên classpath**, **đã load**, **đã link**, và **đã initialize**.

## 8. ClassLoader: lớp do ai định nghĩa cũng quan trọng

Một lớp Java được định danh tại runtime bằng **binary name kết hợp với defining class loader** (đối với các kiểu không ẩn và mô hình thông thường). Hai loader khác nhau có thể định nghĩa hai kiểu có **cùng binary name** nhưng JVM vẫn coi chúng là **hai runtime class khác nhau**.

Ví dụ tư duy:

~~~text
Loader A định nghĩa com.acme.Plugin  ---> Class A
Loader B định nghĩa com.acme.Plugin  ---> Class B

Tên binary giống nhau; defining loader khác nhau;
vì thế Class A != Class B tại runtime.
~~~

Hậu quả:

- Cast giữa hai kiểu không tương thích có thể gây `ClassCastException`, dù cả hai được gọi là `com.acme.Plugin` ở log.
- Các `static` field và quá trình initialization thuộc về **mỗi runtime class** riêng, không gom chung chỉ dựa vào tên.
- Trong môi trường app server, plugin và test runner, lỗi `ClassCastException: Foo cannot be cast to Foo` có thể liên quan nhiều class loader, không nhất thiết source class sai.

### 8.1. Bootstrap, platform, application loader

Trong JDK hiện đại thường thấy:

- **Bootstrap class loader:** cơ chế nền của VM, tải nhiều class nền tảng như `java.lang.Object`; trong API `Class.getClassLoader()`, lớp bootstrap thường trả **`null`** theo hợp đồng.
- **Platform class loader:** tải nhiều module/platform classes theo cơ chế triển khai JDK.
- **Application/system class loader:** thường tải ứng dụng từ classpath/module path do launcher định nghĩa.

Mối quan hệ loader thực tế có thể phức tạp, nhất là module layers, application servers, plugin frameworks. **Parent-first delegation** là mô hình mặc định phổ biến của `ClassLoader.loadClass`: hỏi loader cha trước rồi mới tìm class của mình; **không phải định luật cấm mọi loader tùy biến làm child-first** trong phạm vi được phép.

Phân biệt:

- **Initiating loader:** loader nhận/khởi đầu yêu cầu nạp class.
- **Defining loader:** loader thực sự định nghĩa class đó. Hai khái niệm này có thể khác nhau do delegation.
- **Runtime package** cũng chịu ảnh hưởng của defining loader; hai class có package name cùng chuỗi nhưng loader khác không tự động ở cùng runtime package đối với access checks.

### 8.2. ClassLoader context và module system

Nhiều framework đọc **thread context class loader** khi discovery plugin/resource để không bị giới hạn vào loader của code thư viện. Với Java Platform Module System (JPMS), module graph và readability/export rules còn tác động khả năng resolve/access.

Tuy nhiên, class loader **không tự xử lý** hoàn chỉnh dependency injection, discovery hoặc quản lý module lifecycle. Nó là tầng nạp/định danh lớp; nhiều công việc cấp ứng dụng được thực hiện bởi framework.

## 9. Loading, initialization và `new` không đồng nghĩa nhau

| Tình huống | Điều thực sự diễn ra |
| --- | --- |
| `obj = new Service()` | Cần class `Service` được initialized; tạo instance, gọi constructor theo quy tắc |
| `Service.runStatic()` | Có thể initialize `Service` nhưng **không tạo instance Service** |
| `Service.class` | Có thể tạo/nhận `Class<Service>`; không yêu cầu chạy static initializer |
| `new Service[5]` | Tạo **mảng reference Service** với phần tử null; không tạo 5 Service instance |
| `Class.forName("Service", false, loader)` | Có thể load/link Service nhưng không yêu cầu initialize |
| `Object o = new Object()` | Không chứng minh JVM đã load mọi class kế thừa Object ở toàn bộ classpath |
| `main()` bắt đầu | Lớp chạy main đã được initialize, cùng các dependency bắt buộc; **không phải tất cả class toàn chương trình** |

Đây còn là lý do cụm “JVM đọc file Java khi chạy” không đúng với cơ chế chuẩn: launcher làm việc với class/module artifacts; class loader đưa binary representation phù hợp cho JVM. Source `.java` không phải đầu vào mặc định của mọi bytecode load, dù Java launcher có hỗ trợ chế độ thực thi source-file ở mức công cụ phát triển.

## 10. Những lỗi bộc lộ khác thời điểm vì nạp/liên kết động

### 10.1. `ClassNotFoundException` khác `NoClassDefFoundError`

| Triệu chứng | Ngữ cảnh điển hình | Hướng kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` | API phản chiếu/loader được yêu cầu tìm class theo tên nhưng không tìm được, chẳng hạn `Class.forName("missing.Plugin")` | Tên binary, module/classpath, JAR, case-sensitive path, loader được truyền |
| `NoClassDefFoundError` | JVM cần một class tại runtime nhưng định nghĩa không có/không dùng được, hoặc một class trước đó đã lỗi initialization | Nguyên nhân gốc (thường trong `Caused by`), classpath/version, class initialization trước đó |
| `ExceptionInInitializerError` | Exception thoát bất thường khỏi code initialization của class (theo những quy tắc bao bọc Throwable) | Static fields/blocks, cấu hình, I/O, recursive initialization |
| `UnsupportedClassVersionError` | Class file được biên dịch cho phiên bản JVM cao hơn phiên bản runtime hỗ trợ | Bytecode major version, JDK chạy thực tế và `--release` |
| `ClassFormatError` / `VerifyError` | Binary class file hoặc quy tắc bytecode/verification không hợp lệ | Class file build/transform/obfuscation, dependency và version |
| `NoSuchMethodError` / `NoSuchFieldError` | Caller tham chiếu member với tên/descriptor không còn khớp binary ở runtime | Jar hell, API/ABI thay đổi, transitive dependency |

**Quan trọng:** `NoClassDefFoundError` không phải lúc nào cũng có nghĩa **tệp `.class` không tồn tại**. Ví dụ `<clinit>` của class lỗi ở lần khởi tạo đầu; lần sử dụng sau có thể nhận `NoClassDefFoundError: Could not initialize class ...`. JVM không tự “chạy lại static initializer” một cách tùy ý để sửa trạng thái lỗi của cùng class.

### 10.2. Lỗi có thể xuất hiện muộn

Ví dụ một class `PdfExporter` phụ thuộc thư viện chỉ dùng lúc export. Nếu startup không cần resolve hoặc initialize đường export, ứng dụng có thể chạy được bình thường cho tới khi người dùng bấm Export. Khi đó JVM hoặc framework mới cần class liên quan và lỗi dependency xuất hiện.

Nhưng đừng cam kết “lỗi chắc chắn chỉ xảy ra lúc bấm Export”: JVM được phép thực hiện một số linking sớm; framework cũng có thể preload/scan. **Thời điểm lỗi chính xác** cần căn cứ stack trace và cấu hình runtime.

### 10.3. Quy trình chẩn đoán thực tế

1. Xác định **class nào** lỗi và **operation nào** kích hoạt: load bằng tên, call method, access field, create instance, reflection hoặc plugin discovery.
2. Kiểm tra artifact chứa class, classpath/module path và version JDK: `java -version`, build target, JAR thực có trong runtime.
3. Tìm **caused by đầu tiên** trong stack trace; đừng chỉ đọc thông báo `NoClassDefFoundError` ở cuối.
4. Bật class-load logging có chọn lọc trong môi trường thử nghiệm, rồi đối chiếu **class được load từ nguồn nào**.
5. Kiểm tra loader identity khi app server/plugin có nhiều loader. Dùng `someObject.getClass().getClassLoader()` để quan sát loader của object khi phù hợp (bootstrap có thể null).
6. Nếu lỗi ở static initialization, kiểm tra thứ tự init, side effects, recursive dependency và cấu hình cần thiết **trước lần active use đầu tiên**.
7. Kiểm tra đường feature ít dùng bằng smoke test thay vì chỉ thử startup.

## 11. Class unloading: không phải class nào đã nạp cũng tồn tại vĩnh viễn

JVM có cơ chế **class unloading** dưới những điều kiện phù hợp. Với class do user-defined loader định nghĩa, khả năng giải phóng class metadata thường liên quan tới việc **defining loader** và các cấu trúc liên quan không còn reachable, cùng quy tắc GC/implementation.

Không có lời hứa rằng `System.gc()` lập tức unload một class. Một class được load nhiều lần bởi **cùng loader** theo cách bình thường không có nghĩa mỗi lần tạo một định nghĩa class mới; loader phải tuân thủ các ràng buộc về định danh/định nghĩa, và JVM quản lý trạng thái loaded/linked/initialized của runtime class.

**Class loader leak** có thể khiến class metadata và object liên quan không được giải phóng: ví dụ giữ reference tới plugin loader trong static cache, thread hoặc thread context class loader sau khi plugin đã tắt. Khi thiết kế hot-reload, phải quản lý resources, threads, caches và các static references, không chỉ xóa file JAR.

Trong HotSpot thường có vùng **Metaspace** để lưu nhiều loại class metadata, nhưng tên vùng, chính sách cấp phát và GC là chi tiết triển khai, không phải yêu cầu rằng mọi JVM bắt buộc dùng layout giống hệt.

## 12. Các ngộ nhận quan trọng cần bỏ

1. **“Ứng dụng có 10.000 class thì JVM nạp cả 10.000 trước main.”** Không có quy tắc như vậy.
2. **“Có tên trong constant pool là class đã nạp.”** Không; symbolic reference có thể còn chờ resolution.
3. **“Lazy load nghĩa là class được load đúng ở câu lệnh đầu tiên dùng nó.”** Không; verification/resolution có thể làm load sớm, loader/framework cũng có thể preload.
4. **“Class được loaded tức là static block đã chạy.”** Sai; initialization là pha riêng.
5. **“`Class.forName(..., false, ...)` không load class.”** Sai; `false` điều khiển initialization, không tắt loading.
6. **“`SomeType.class` chắc chắn chạy `<clinit>`.”** Sai; class literal không tự initialize.
7. **“Đọc `static final int` luôn initialize class.”** Sai nếu đây là compile-time constant variable được dùng theo quy tắc Java.
8. **“`new Widget[10]` tạo 10 object Widget.”** Sai; đó là một object mảng chứa các phần tử reference null.
9. **“`ClassNotFoundException` và `NoClassDefFoundError` là một.”** Sai; API/ngữ cảnh và nguyên nhân khác nhau.
10. **“Nếu app chạy tới main, mọi dependency đều không lỗi.”** Sai; đường code chưa dùng có thể lỗi sau.
11. **“Hai class cùng full name luôn là cùng class.”** Sai; defining class loader thuộc identity.
12. **“Class loading càng lazy thì luôn càng nhanh.”** Sai; có trade-off startup so với first-use latency và chi phí động.
13. **“Mỗi class chỉ initialize một lần trong cả hệ điều hành.”** Sai; trạng thái thuộc từng runtime class/loader trong tiến trình JVM tương ứng.
14. **“Khi class initialized thất bại, lần truy cập sau tự chạy lại initializer.”** Không có cơ chế retry tùy ý như vậy cho cùng class đã vào trạng thái lỗi.
15. **“`javap` chứng minh một class đã loaded trong process.”** Sai; `javap` đọc class file tĩnh, còn class-load log quan sát runtime.

## 13. Câu hỏi tự kiểm tra

1. Class loading gồm điều gì? **Tìm binary representation phù hợp và tạo kiểu runtime**, không phải tự động chạy `static {}`.
2. Linking có các phần nào? **Verification, preparation và resolution**, với thời điểm resolution có thể linh hoạt trong giới hạn đặc tả.
3. Điều kiện cần trước khi initialization? **Class đã hoàn tất loading, verification và preparation**, và các quy tắc phụ thuộc khởi tạo được tôn trọng.
4. Tại sao không được dùng “không thấy INIT” để chứng minh “chưa loaded”? Vì **static initializer chỉ phản ánh initialization**, class có thể đã load/link trước.
5. Đọc `Config.LIMIT` là compile-time constant có làm `INIT_CONFIG` chạy không? **Không chỉ vì lần đọc này**.
6. `Class.forName(name)` và `Class.forName(name,false,loader)` khác nhau điểm chính nào? **Overload một tham số yêu cầu initialization; overload với `false` không yêu cầu initialization**.
7. `new Feature[3]` có gọi constructor của ba Feature không? **Không**.
8. Tại sao một class trong JAR có thể chưa được load? Vì JAR là **nguồn bytes sẵn có**, không đồng nghĩa mọi lớp được dùng/liên kết ở startup.
9. Hai loader định nghĩa cùng binary name có tạo cùng runtime class không? **Không**.
10. Vì sao lỗi dependency có thể chỉ xảy ra ở request thứ 100? Vì đường code đó có thể chỉ bắt đầu **load/link/initialize** dependency khi request ấy dùng feature; dù cũng có thể được phát hiện sớm.
11. `-Xlog:class+load=info` quan sát gì? **Những sự kiện class loading do JVM có cơ chế logging ấy ghi lại**; không phải danh sách static initializer đã chạy.
12. Có nên preload toàn bộ class để tránh mọi lỗi không? **Không mặc định**; chọn chiến lược startup/warmup/smoke test dựa trên latency, memory và độ tin cậy.
13. Class unloading có bảo đảm ngay khi gán reference bằng null không? **Không**; GC, class loader reachability và implementation quyết định.
14. `NoClassDefFoundError` có thể đến từ lỗi initialization trước đó không? **Có**, không chỉ do thiếu class file.

## 14. Tài liệu chuẩn và định hướng đào sâu

- JVM Specification Java SE 17, chương **Loading, Linking, and Initializing**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- JVM Specification, mục **Creation and Loading**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JVM Specification, mục **Linking**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- JVM Specification, mục **Initialization**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- Java Language Specification Java SE 17, chương **Execution**, các mục 12.2–12.4: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- `java.lang.Class` Java 17 API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- `java.lang.ClassLoader` Java 17 API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html

**Kết luận:** class loading là quá trình JVM nhận và định nghĩa **kiểu runtime**, không phải đọc mọi tệp của chương trình vào RAM ngay lập tức và cũng không phải chạy `static {}`. JVM hỗ trợ nạp và liên kết động để giảm công việc không cần thiết và phục vụ các hệ thống plugin/runtime linh hoạt, nhưng được phép thực hiện một số resolution sớm. Phân biệt thật rõ **loading → linking → initialization**, **lớp có trong JAR → lớp được load → lớp được initialized**, cùng **class identity = binary name + defining loader** là nền tảng để hiểu startup, first-use latency và lỗi classpath trong ứng dụng Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-002">Bootstrap, platform, application class loader và cơ chế ủy quyền</a>

<details>
<summary>Xem chi tiết</summary>

# Bootstrap, platform, application class loader và cơ chế ủy quyền

## 1. Mục tiêu học tập

Một chương trình Java có thể sử dụng hàng nghìn lớp nhưng chúng không nhất thiết được tìm và định nghĩa bởi cùng một thành phần. JVM dùng **class loader** để tạo lớp từ biểu diễn nhị phân, giải quyết tham chiếu và tổ chức không gian tên. Từ Java 9, hệ thống class loader tích hợp thường được giải thích qua ba thành phần: **bootstrap**, **platform** và **application/system**.

Sau bài học, bạn có thể:

- Phân biệt trách nhiệm của ba loader tích hợp, cách lấy chúng qua Java API và vai trò của classpath/module path.
- Đọc chính xác thuật toán ủy quyền **parent-first** thông thường, các hàm **loadClass**, **findLoadedClass**, **findClass**, **defineClass** và **resolveClass**.
- Hiểu **defining loader** so với **initiating loader**, danh tính lớp dựa trên tên nhị phân **và** loader định nghĩa.
- Giải thích tại sao **String.class.getClassLoader()** thường là **null**, nhưng **null** không có nghĩa là không có lớp.
- Phân biệt **loading**, **linking** (verification, preparation, resolution) và **initialization**.
- Nhận biết trường hợp loader tùy biến, context class loader, Java modules, ServiceLoader, nhiều phiên bản dependency và các lỗi thường gặp.
- Thực hành Java 17+ để quan sát loader, chứng minh hai class cùng binary name vẫn khác kiểu khi có hai defining loaders và phân biệt nạp lớp với khởi tạo lớp.

Phạm vi: các quy tắc nền tảng của Java SE 17+ và mô hình JVM; chi tiết tên lớp triển khai loader, đường dẫn, output chẩn đoán có thể khác nhau giữa các JDK.

## 2. Class loader thực hiện điều gì?

**Class loader** là cơ chế đưa định nghĩa lớp/interface đến JVM. Với lớp thông thường, một loader có thể lấy các byte của class từ classpath, module path, JAR, mạng, bộ nhớ hoặc nguồn do ứng dụng cung cấp, rồi dùng cơ chế JVM để định nghĩa lớp.

Phân biệt:

- **Tìm class bytes:** tìm nơi chứa dữ liệu, ví dụ entry **com/example/Order.class** trong JAR.
- **Định nghĩa class (define):** JVM tạo lớp runtime từ dữ liệu class hợp lệ, gắn danh tính lớp với một **defining loader**.
- **Nạp class (load):** lời yêu cầu nạp có thể được một loader giải quyết bằng cách ủy quyền cho loader khác; loader nhận yêu cầu không nhất thiết là loader trực tiếp định nghĩa.
- **Liên kết (link):** thực hiện các công việc xác minh, chuẩn bị và giải quyết tham chiếu theo quy tắc JVM.
- **Khởi tạo (initialize):** chạy công việc khởi tạo lớp, ví dụ static field initializers và static initializer blocks, vào thời điểm được Java quy định.

Vì vậy, câu “loader đã tìm thấy class” không tự động có nghĩa **static initializer đã chạy** hoặc mọi tham chiếu của lớp đã được resolve xong.

Một **Class<?>** là đối tượng đại diện cho một kiểu tại runtime; **Class.getClassLoader()** cung cấp loader đã định nghĩa lớp thông thường đó. Với array class và primitive, API có quy tắc riêng sẽ đề cập bên dưới.

## 3. Ba loader tích hợp trong Java hiện đại

Một mô hình tổng quát cho JDK 17 với hệ thống loader mặc định:

~~~text
                    JVM / Java runtime
                          |
                          v
                  Bootstrap loader
                          ^
                          | delegation
                  Platform loader
                          ^
                          | delegation
             Application / System loader
                          ^
                          | delegation
             Class loader riêng của ứng dụng
~~~

Mũi tên biểu thị đường **ủy quyền thông thường từ loader con lên parent**. Đây là sơ đồ tư duy, không phải lời khẳng định mọi lookup Java modules luôn đi theo một cây cứng nhắc hoặc mọi loader tùy biến đều dùng parent-first.

| Loader | Nhiệm vụ khái quát | API/thông tin quan sát |
| --- | --- | --- |
| **Bootstrap** | Nạp/định nghĩa các lớp lõi mà VM/runtime cung cấp, ví dụ phần lõi **java.base** | Là loader tích hợp trong JVM; với **Class.getClassLoader()**, lớp được bootstrap định nghĩa thường trả **null** |
| **Platform** | Nạp nhiều platform classes/modules Java SE, JDK và thành phần runtime theo phân công của JDK | **ClassLoader.getPlatformClassLoader()** |
| **Application / System** | Loader hệ thống dùng cho ứng dụng trên application classpath, application module path và các phạm vi thích hợp | **ClassLoader.getSystemClassLoader()**; thường là loader của main class trong lệnh chạy classpath thông thường |

Trước Java 9, nhiều tài liệu dùng thuật ngữ **extension class loader**. Từ Java 9, cơ chế extension cũ đã thay đổi và **platform class loader** là khái niệm phù hợp cho JDK 17+. Không thiết kế chương trình Java 17 dựa vào thư mục **jre/lib/ext** như cơ chế hiện hành.

### 3.1. Bootstrap class loader

Bootstrap là thành phần nền tảng được JVM cung cấp, không bắt buộc tồn tại dưới dạng một đối tượng Java **ClassLoader** bình thường. Khi hỏi:

~~~java
String.class.getClassLoader()
~~~

kết quả trên JVM Java SE thông dụng là **null**, mang ý nghĩa lớp **String** do bootstrap định nghĩa.

Điều này **không có nghĩa lớp String không có class loader**; nó có defining loader là bootstrap theo cơ chế JVM. Giá trị **null** là quy ước ở Java API cho loader bootstrap trong những trường hợp tương ứng.

Bootstrap không có parent. Những lớp thiết yếu như **java.lang.Object**, **java.lang.String** thường do bootstrap định nghĩa. Không nên khẳng định mọi lớp bắt đầu bằng **java.** đều do bootstrap định nghĩa: nhiều loại platform classes được phân công cho loader khác.

### 3.2. Platform class loader

Platform loader chịu trách nhiệm về nhiều lớp nền tảng Java/JDK không thuộc tập do bootstrap định nghĩa. Java cung cấp:

~~~java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
~~~

Bạn có thể dùng platform loader làm parent của custom class loader khi chỉ muốn custom loader có phạm vi kế thừa class nền tảng mà không mặc nhiên được nhìn thấy các class ứng dụng.

**Ngoại lệ quan trọng đối với sơ đồ cây:** từ Java 9, module system ảnh hưởng khả năng nhìn thấy lớp. Tài liệu ClassLoader Java 17 ghi rõ platform loader **có thể phải ủy quyền tới loader khác, ví dụ application loader**, để xử lý các mô-đun được nâng cấp/ghi đè mà modules tương ứng có quan hệ đọc. Vì thế không nên tuyên bố “platform loader tuyệt đối không thể nhìn thấy lớp của application loader trong mọi trường hợp”.

### 3.3. Application/system class loader

Application loader còn được gọi là **system class loader**:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
~~~

Trong cách chạy điển hình:

~~~bash
java -cp out com.example.Main
~~~

main class trong **out** thường được định nghĩa bởi system loader mặc định. Loader này nhìn thấy các lớp ứng dụng theo classpath/module configuration và có thể ủy quyền để sử dụng các lớp nền tảng.

Trong JDK hiện đại, implementation class của system loader là chi tiết nội bộ. **Không cast system loader thành URLClassLoader** và không giả định nó luôn có hàm **addURL** hoặc cấu trúc URL classpath giống JDK 8. Nếu Java được chạy với cấu hình system loader tùy biến, một số phép so sánh phụ thuộc môi trường có thể khác.

**Đường kế thừa:** theo API, platform loader là **parent hoặc ancestor** của system loader. Với hệ mặc định thường trực tiếp là parent; các system class loader tùy biến có thể thêm tầng trung gian.

## 4. Parent không phải quan hệ kế thừa lớp Java

Trong:

~~~java
ClassLoader child = new ClassLoader(parent) {};
~~~

**parent** là loader để **ủy quyền tìm lớp**. Nó không có nghĩa child có quan hệ **extends parent.getClass()** hay child tự động sao chép toàn bộ những class đã được parent định nghĩa.

Cần phân biệt ba quan hệ:

| Quan hệ | Câu hỏi |
| --- | --- |
| **ClassLoader parent** | Khi loader này được yêu cầu một class, nó có thể hỏi loader nào trước? |
| **Class extends/interface implements** | Một kiểu Java kế thừa/hiện thực kiểu nào? |
| **Module reads/exports/opens** | Một module được phép tham chiếu/truy cập các package/class nào? |

Ba quan hệ tương tác nhưng không thay thế cho nhau. Parent delegation không tự mở quyền reflective access đến package bị module đóng; việc một class nạp thành công cũng không làm mọi thành viên của nó trở nên truy cập được.

## 5. Thuật toán ủy quyền parent-first thông thường

Cài đặt mặc định của **ClassLoader.loadClass(String, boolean)**, khi không bị override, hoạt động theo tinh thần:

~~~text
loadClass(binaryName, resolve)
    |
    v
1. findLoadedClass(binaryName)
   Đã được loader này ghi nhận là nạp? -> sử dụng lại
    |
    v chưa tìm thấy
2. Hỏi parent.loadClass(binaryName)
   Nếu parent == null: thử bootstrap loader của JVM
    |
    +-- thành công -> dùng class do parent/bootstrap cung cấp
    |
    v không tìm được
3. Gọi findClass(binaryName) của chính loader
    |
    +-- tìm được bytes / định nghĩa class -> trả Class
    |
    v không tìm được
4. Ném ClassNotFoundException
    |
    v nếu tìm được class
5. Nếu resolve=true: resolveClass(theClass)
6. Trả Class<?>
~~~

Điểm cần nhớ:

- **findLoadedClass** dùng để tránh định nghĩa trùng một class trong cùng loader.
- **Parent-first** nghĩa là ưu tiên hỏi parent rồi mới đến **findClass** của con. Nó giúp lớp lõi Java và các dependency chung được chia sẻ nhất quán.
- **findClass** là hook thích hợp khi viết loader tùy biến vẫn giữ cơ chế ủy quyền mặc định.
- **defineClass(name, bytes, off, len)** thực sự đưa định nghĩa mới đến JVM, nếu dữ liệu hợp lệ.
- **resolveClass** liên quan tới bước linking; nó **không đồng nghĩa khởi tạo static fields**.
- **ClassNotFoundException** là kết quả khi một yêu cầu nạp lớp theo tên không tìm thấy định nghĩa phù hợp; lỗi linkage có thể xảy ra ở giai đoạn khác.

Thuật toán trên là cài đặt mặc định của **ClassLoader**, không phải hợp đồng bắt buộc mọi loader custom phải dùng đúng thứ tự. Loader có thể override **loadClass** để dùng **child-first** hoặc cơ chế module-specific, nhưng phải tôn trọng ràng buộc của JVM về danh tính lớp, kiểu, quyền và tính nhất quán.

### 5.1. Ví dụ parent-first tránh một bản sao lớp lõi

Giả sử application loader được yêu cầu **java.lang.String**:

~~~text
Application loader -> Platform loader -> Bootstrap loader
                                     -> định nghĩa java.lang.String
                 <- trả về cùng Class
        <- trả về cùng Class
~~~

Các loader con **có thể initiate loading** bằng ủy quyền; class runtime được bootstrap định nghĩa. Mặc định, application loader không tạo bản sao tùy ý của **java.lang.String**.

Custom loader không nên cố định nghĩa các lớp thuộc **java.*** bằng **defineClass**; Java API đặt ràng buộc an ninh với tên package này, bên cạnh các điều kiện về module, package và class file.

### 5.2. Mẫu code override findClass

Mã sau chỉ minh họa cấu trúc **findClass**; không phải chương trình độc lập vì phần lấy dữ liệu byte chưa được cung cấp:

~~~java
class ResourceClassLoader extends ClassLoader {
    ResourceClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String binaryName)
            throws ClassNotFoundException {
        byte[] bytes = readClassBytes(binaryName);
        if (bytes == null) {
            throw new ClassNotFoundException(binaryName);
        }
        return defineClass(binaryName, bytes, 0, bytes.length);
    }

    private byte[] readClassBytes(String binaryName) {
        // Cơ chế lấy class bytes từ một nguồn riêng sẽ được cài đặt ở đây.
        return null;
    }
}
~~~

Vì mẫu này trả null nên nó **chưa thực sự tải được class mới**; nó chỉ cho thấy nơi cài đặt logic. Ví dụ chạy được ở mục 9 sẽ thực hiện việc định nghĩa từ mảng byte hoàn chỉnh, không cần cơ chế tải dữ liệu ngoài.

Lưu ý **binaryName** ở API **loadClass/findClass** thường có dạng **com.example.MyType**, không phải **com/example/MyType**. Khi tìm entry trong JAR, loader tự chuyển thành đường dẫn **com/example/MyType.class** nếu cơ chế nguồn của nó cần như vậy.

## 6. Defining loader, initiating loader và danh tính lớp

Khi loader A nhận yêu cầu nạp **com.example.Service** rồi ủy quyền thành công cho loader B, cần gọi đúng tên:

- **B là defining loader** nếu B thực sự định nghĩa class từ bytes.
- **A là initiating loader** theo quá trình nạp mà JVM ghi nhận, dù A không trực tiếp định nghĩa.
- Class runtime đó có **danh tính gắn với binary name và defining loader**.

Về mặt khái niệm, một class/runtime interface được định danh bằng cặp:

~~~text
(binary name, defining loader)
~~~

Do đó, hai lớp **plugin.Task** do hai loader khác nhau trực tiếp định nghĩa là **hai kiểu runtime khác nhau**, mặc dù:

- cùng binary name **plugin.Task**;
- cùng class bytes;
- cùng method signatures;
- cùng superclass **java.lang.Object**.

Với hai kiểu này:

~~~text
("plugin.Task", LoaderA) != ("plugin.Task", LoaderB)
~~~

**Class<?>** không bằng nhau; instance của lớp từ LoaderA không tự được cast thành kiểu từ LoaderB. Các bài toán plugin isolation, application server redeployment và nhiều phiên bản thư viện cần hiểu đúng tính chất này.

### 6.1. Điều gì xảy ra khi loader con chỉ ủy quyền?

Nếu cả **LoaderA** và **LoaderB** đều parent-first đến cùng parent đã định nghĩa **shared.Api**, hai yêu cầu có thể trả **cùng một Class object** của **shared.Api**. **LoaderA != LoaderB** không bắt buộc class cuối cùng phải khác nhau: cần kiểm tra **ai thực sự định nghĩa lớp**.

### 6.2. Runtime package cũng xét loader

Không đủ để hai lớp có cùng chuỗi package **com.example**. JVM xem runtime package dựa trên **package name và defining loader** (cùng các quy định module liên quan). Hai class cùng package name nhưng đến từ các defining loaders khác nhau **không tự được quyền truy cập package-private** của nhau.

Điều này lý giải những lỗi IllegalAccessError khó hiểu khi class sinh bằng bytecode cùng package string với class gốc nhưng được định nghĩa bởi loader khác.

### 6.3. Loading constraints khi giao tiếp giữa loaders

Các phương thức/field có descriptor tham chiếu kiểu được định nghĩa bởi loader khác nhau có thể chịu **loading constraints** để bảo đảm type safety khi linking. Không thể chỉ tạo hai class có cùng binary name rồi cho hệ thống coi chúng là một kiểu khi truyền object qua ranh giới loader.

Các giải pháp thực tế gồm: đưa API dùng chung lên loader cha, truyền dữ liệu qua format ổn định hoặc dùng interface mà cả hai phía nhìn thấy **cùng Class object** của interface đó.

## 7. Phân biệt loading, linking và initialization

Sơ đồ vòng đời rút gọn:

~~~text
Nguồn class bytes
      |
      v
Loading / define class
      |
      v
Linking
  +--> Verification: class file/bytecode và ràng buộc kiểu
  +--> Preparation: chuẩn bị static fields với giá trị mặc định thích hợp
  +--> Resolution: giải quyết các symbolic references (có thể lazy)
      |
      v
Initialization
  +--> static initializers và static field initializers của lớp
      |
      v
Lớp sẵn sàng cho các cách sử dụng cần khởi tạo
~~~

Quá trình chuẩn bị và giải quyết có các quy tắc thứ tự và có thể xảy ra từng phần. **Resolution** không nhất thiết được thực hiện đồng loạt cho tất cả tham chiếu ngay khi gọi loadClass. Không đồng nhất linking với chạy constructor đối tượng.

Ví dụ các hành động thường có tác dụng khác nhau:

| Hành động | Có bắt buộc khởi tạo lớp mục tiêu ngay không? |
| --- | --- |
| **loader.loadClass("demo.Widget")** | Thông thường **không**; mặc định tương đương loadClass(name, false) |
| **Class.forName("demo.Widget", false, loader)** | **Không yêu cầu initialization** |
| **Class.forName("demo.Widget")** | Có yêu cầu load/link và **initialize** theo contract overload một tham số |
| **Class.forName("demo.Widget", true, loader)** | Yêu cầu initialization |
| **new Widget()** | Là active use, cần lớp được khởi tạo theo quy tắc |
| Truy cập static field/method không phải ngoại lệ constant variables | Có thể kích hoạt initialization theo quy tắc JLS/JVMS |
| Tham chiếu **Widget.class** | Không tự khởi tạo Widget chỉ vì lấy class literal |

Không xem **Class.forName** như một API “chỉ kiểm tra tên” mà quên tham số **initialize**. Việc khởi tạo có thể chạy static code với side effects và lỗi.

## 8. Ví dụ runnable 1: quan sát ba loader

Tạo tệp **LoaderBasics.java**:

~~~java
public class LoaderBasics {
    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader app = LoaderBasics.class.getClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        System.out.println("mainIsSystem=" + (app == system));
        System.out.println("platformIsDifferent=" + (platform != system));
        System.out.println("stringBootstrap="
                + (String.class.getClassLoader() == null));
        System.out.println("primitiveArrayBootstrap="
                + (int[].class.getClassLoader() == null));
        System.out.println("ownArrayUsesApp="
                + (LoaderBasics[].class.getClassLoader() == app));
        System.out.println("forNameIsString="
                + (Class.forName("java.lang.String", false, app)
                        == String.class));
    }
}
~~~

Với JDK 17 và system loader mặc định, thực hiện trong thư mục chứa tệp:

~~~bash
javac --release 17 -encoding UTF-8 LoaderBasics.java
java -cp . LoaderBasics
~~~

**Stdout trong môi trường Java 17 điển hình dùng system loader mặc định:**

~~~text
mainIsSystem=true
platformIsDifferent=true
stringBootstrap=true
primitiveArrayBootstrap=true
ownArrayUsesApp=true
forNameIsString=true
~~~

Diễn giải:

1. Main class trên application classpath do system loader mặc định định nghĩa.
2. Platform loader là thành phần riêng so với system loader.
3. **String** là lớp lõi được bootstrap định nghĩa và biểu diễn loader bằng null.
4. **int[]** được JVM tạo cho primitive array, nên **getClassLoader()** cũng trả null.
5. Array class có component reference type do app loader định nghĩa có loader tương ứng với component.
6. **Class.forName** được yêu cầu tìm String qua app loader; nhờ ủy quyền nó nhận đúng cùng Class object **String.class** do bootstrap cung cấp.

Nếu chạy trong application server hoặc cấu hình system loader tùy biến, dòng **mainIsSystem** có thể khác. Bài thực hành giả định đúng lệnh **java -cp . LoaderBasics** không dùng custom launcher.

### 8.1. Tại sao array cũng cần quy tắc riêng?

Array classes được tạo bởi JVM khi cần, không phải bằng cách đọc tệp **[L...;.class** thông thường. **Class.getClassLoader()** của reference array bằng loader của component reference type; primitive array có loader biểu diễn bằng null.

Ví dụ:

~~~java
// Mã minh họa trong một phương thức:
ClassLoader a = String[].class.getClassLoader();   // Thông thường null
ClassLoader b = LoaderBasics[].class.getClassLoader(); // Loader của LoaderBasics
ClassLoader c = int[][].class.getClassLoader();     // null
~~~

Không suy từ tên array trông như descriptor rằng file JAR chứa entry có tên **[Ljava/lang/String;.class**.

## 9. Ví dụ runnable 2: cùng tên, hai defining loaders, hai kiểu khác nhau

Bài này dùng **hai tệp Java độc lập** và hai instance của một custom class loader. Nó minh họa class identity mà không phụ thuộc plugin framework.

Cấu trúc thư mục:

~~~text
workspace/
  ClassIdentityDemo.java
  plugin/
    Task.java
  out/                     (tạo trước khi biên dịch)
~~~

Tệp **plugin/Task.java**:

~~~java
package plugin;

public final class Task {
    public int run() {
        return 7;
    }
}
~~~

Tệp **ClassIdentityDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassIdentityDemo {
    static final class ByteArrayLoader extends ClassLoader {
        private final byte[] bytecode;

        ByteArrayLoader(byte[] bytecode) {
            super((ClassLoader) null); // Ủy quyền chỉ tới bootstrap
            this.bytecode = bytecode.clone();
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("plugin.Task")) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("out", "plugin", "Task.class"));

        ClassLoader first = new ByteArrayLoader(bytes);
        ClassLoader second = new ByteArrayLoader(bytes);

        Class<?> one = Class.forName("plugin.Task", false, first);
        Class<?> two = Class.forName("plugin.Task", false, second);

        System.out.println("sameName="
                + one.getName().equals(two.getName()));
        System.out.println("sameClass=" + (one == two));
        System.out.println("sameLoader="
                + (one.getClassLoader() == two.getClassLoader()));
        System.out.println("eachOwns="
                + (one.getClassLoader() == first
                        && two.getClassLoader() == second));
        System.out.println("assignable="
                + one.isAssignableFrom(two));
        System.out.println("sharedObject="
                + (one.getSuperclass() == two.getSuperclass()));
    }
}
~~~

Từ thư mục **workspace**, với **out** đã tồn tại (nếu chưa, tạo thư mục):

~~~bash
mkdir out
javac --release 17 -encoding UTF-8 -d out plugin/Task.java ClassIdentityDemo.java
java -cp out ClassIdentityDemo
~~~

Nếu **out** đã tồn tại, bỏ qua lệnh **mkdir out**. **Stdout**:

~~~text
sameName=true
sameClass=false
sameLoader=false
eachOwns=true
assignable=false
sharedObject=true
~~~

Lý do:

- **first** và **second** đều nhận cùng byte array từ **Task.class**, nhưng là **hai ClassLoader instances độc lập**.
- Mỗi loader có parent null theo quy ước ủy quyền tới bootstrap; bootstrap không cung cấp **plugin.Task**. Sau khi parent không tìm thấy lớp, **findClass** của từng loader định nghĩa một bản riêng.
- Hai lớp mang cùng **binary name = plugin.Task** nên **sameName=true**.
- **Defining loader** khác nhau khiến **Class object** khác nhau, nên **sameClass=false**, **assignable=false**.
- Cả hai lớp mặc định kế thừa cùng **java.lang.Object** được bootstrap định nghĩa, nên superclass Class object của chúng là một: **sharedObject=true**.

Chương trình không truyền instance giữa hai lớp này; nếu thử cast instance từ loại một sang loại hai, Java sẽ từ chối theo kiểm tra kiểu runtime.

**Giới hạn ví dụ:** Loader chỉ phục vụ đúng tên **plugin.Task**, và Task không có dependency plugin tùy chỉnh. Trong hệ thống thật cần quản lý nhiều class, resources, package sealing, module, native library, bảo mật dữ liệu đầu vào, concurrency và vòng đời loader. Dùng cùng tên class từ nguồn không tin cậy với **defineClass** cần ranh giới bảo mật rõ ràng.

### 9.1. Vì sao không dùng parent=system trong ví dụ này?

Nếu loader có parent là system loader, và system đã thấy **plugin.Task** trên classpath **out**, thuật toán parent-first mặc định sẽ hỏi system trước, rồi cả hai loader con có thể nhận **cùng lớp do system định nghĩa**. Khi đó ví dụ không còn chứng minh hai defining loaders khác nhau. Đặt parent null là quyết định có chủ đích để cô lập lớp **plugin.Task** trong thí nghiệm.

Trong ứng dụng thật, thường nên dùng parent chia sẻ các API chung (ít nhất platform loader hoặc một API loader riêng), thay vì giả định bootstrap parent thích hợp cho mọi plugin.

## 10. Ví dụ runnable 3: loading không tự chạy static initializer

Tạo tệp **InitializationDemo.java**:

~~~java
public class InitializationDemo {
    static class Target {
        static {
            System.out.println("init:Target");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = InitializationDemo.class.getClassLoader();

        Class<?> type =
                Class.forName("InitializationDemo$Target", false, loader);
        System.out.println("loaded=" + (type == Target.class));

        Class.forName("InitializationDemo$Target", true, loader);
        System.out.println("initialized");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 InitializationDemo.java
java InitializationDemo
~~~

**Stdout:**

~~~text
loaded=true
init:Target
initialized
~~~

**Tại sao:** Lệnh **Class.forName(..., false, loader)** cung cấp **Class object** nhưng không yêu cầu khởi tạo Target. Tham chiếu **Target.class** chỉ lấy class literal, không kích hoạt static initializer. Lệnh **Class.forName(..., true, loader)** sau đó yêu cầu initialization và in **init:Target** một lần; cuối cùng main in **initialized**.

Nếu Target từng được khởi tạo trước đó trong cùng JVM, static block không tự chạy lại. Trong chương trình độc lập này, nó chưa từng được khởi tạo trước bước gọi với **true**.

## 11. Context class loader: vì sao có loader “ngữ cảnh”?

**Thread context class loader (TCCL)** là một tham chiếu loader gắn với thread:

~~~java
ClassLoader context = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(context);
~~~

Nó **khác về khái niệm** so với parent loader hoặc defining loader của một class:

| Loại | Được xác định bởi | Công dụng |
| --- | --- | --- |
| Defining loader của class | Loader thực sự định nghĩa class | Danh tính của kiểu runtime |
| Parent của loader | Cấu hình ủy quyền loader | Nơi hỏi trước để tìm class/resource |
| Context loader của thread | Trạng thái ngữ cảnh do thread/framework đặt | Giúp thư viện hạ tầng tìm provider/component trong phạm vi ứng dụng gọi nó |

Một thư viện dùng chung được nạp bởi bootstrap/platform có thể cần tìm extension được đóng gói trong ứng dụng. Nếu chỉ dùng loader định nghĩa thư viện, nó không nhất thiết thấy provider trong application classpath. TCCL cung cấp một cách truyền phạm vi lookup đến thư viện theo convention.

Ví dụ **ServiceLoader.load(MyService.class)** (overload nhận service class) theo Java API thường sử dụng context class loader của thread để tìm providers. Một số framework, JDBC, logging, SPI và application server dùng TCCL hoặc cơ chế lookup tương tự.

**Không được nhầm:** đặt context loader mới bằng **setContextClassLoader** không thay defining loader của những lớp đã nạp, và không thay đổi parent chain của system loader.

### 11.1. Thiết lập TCCL đúng vòng đời

Một pattern hữu ích khi tạm đổi ngữ cảnh:

~~~java
// Mã minh họa bên trong một phương thức:
Thread thread = Thread.currentThread();
ClassLoader old = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Thực hiện lời gọi framework/SPI cần pluginLoader.
} finally {
    thread.setContextClassLoader(old);
}
~~~

Đây là đoạn minh họa, với **pluginLoader** được cung cấp từ bên ngoài. Khôi phục loader cũ trong **finally** để tránh rò rỉ ngữ cảnh sang tác vụ kế tiếp của cùng thread, nhất là trong thread pool.

## 12. Java Platform Module System làm sơ đồ nạp lớp tinh tế hơn

Từ Java 9, JDK dùng module system. **Module path** và **classpath** có tính chất khác nhau:

- Classpath thường chứa các class thuộc **unnamed module** của loader tương ứng.
- Module path cho phép runtime giải quyết **named modules** theo mô tả module, exports, reads và các cấu hình module.
- Một module có thể do bootstrap, platform hoặc application loader định nghĩa tùy JDK/module graph.
- Loader có thể tìm lớp dựa vào mapping module/package, và một số yêu cầu lookup có thể đi tới loader khác do module readability/configuration.

Một ví dụ đơn giản:

~~~bash
java -cp out com.example.Main
~~~

Đây là cách gọi main class qua classpath; khác với lệnh khởi chạy modular application bằng:

~~~bash
java --module-path mods --module com.example.app/com.example.Main
~~~

Dòng modular là **cú pháp minh họa**, yêu cầu thực tế phải có module descriptor hợp lệ và artifact trong **mods**. Đừng giả định chỉ đổi **-cp** thành **--module-path** là đủ.

**Module encapsulation** cũng là lớp kiểm soát riêng: dù một loader tìm được class, reflective access vào thành viên không được mở vẫn có thể bị từ chối, ví dụ **InaccessibleObjectException** trong các tình huống phù hợp.

### 12.1. Tránh các giả định JDK 8 đã lỗi thời

- **Extension class loader** không còn là tên chính thức của tầng hiện hành trong JDK 17.
- **rt.jar** và mô hình JRE/extension directory cũ không còn là cách chắc chắn để xác định nguồn lớp Java SE.
- Built-in app loader không bắt buộc là **URLClassLoader**.
- Không dựa vào tên triển khai nội bộ như **jdk.internal.loader.ClassLoaders$AppClassLoader** để viết cast hoặc API production.

Dựa vào **ClassLoader.getPlatformClassLoader()**, **getSystemClassLoader()**, **getParent()**, **getResource(...)** và contracts công khai khi thiết kế code.

## 13. Custom loader và child-first: khi nào có ích, khi nào có rủi ro?

**Parent-first** hữu ích để chia sẻ các lớp nền tảng và API ổn định. Tuy nhiên plugin isolation hoặc các container có thể cố ý dùng **child-first** cho một tập package riêng, để hai module chạy các phiên bản thư viện khác nhau.

Một chính sách khả thi theo từng package:

~~~text
Yêu cầu nạp java.*, javax.* hoặc API chung:
  ủy quyền parent trước

Yêu cầu nạp plugin.internal.*:
  ưu tiên bản trong plugin

Các class khác:
  ủy quyền/lookup theo chính sách loader
~~~

Đây là mô tả ý tưởng, **không phải một thuật toán chuẩn áp dụng cho mọi plugin system**. Các rủi ro:

1. **Class identity conflict:** lớp API chung bị định nghĩa hai lần nên cast, MethodHandle hoặc reflection không còn khớp.
2. **LinkageError/loader constraint violation:** descriptors giữa các loader đòi cùng kiểu nhưng lại ánh xạ tới các class khác nhau.
3. **Package-private access sai:** hai class có cùng tên package nhưng khác loader không nằm trong cùng runtime package.
4. **Deadlock trong loading:** các loader khóa theo thứ tự không phù hợp khi nhiều thread nạp các lớp phụ thuộc lẫn nhau.
5. **Resource/service discovery lệch:** class được tìm từ một loader, resource/provider lại được tìm qua loader khác.
6. **Giới hạn module/bảo mật:** class có mặt không có nghĩa mọi package đều mở hoặc có thể define tùy ý.
7. **Rò rỉ loader:** thread, ThreadLocal, static caches, timers, MBean, callback hay registry giữ strong reference đến Class/ClassLoader khiến plugin không được giải phóng.

Khi cần loader tùy biến, nên giữ **API/interface chung** trong một parent ổn định, ưu tiên tái dùng thư viện tiêu chuẩn và framework đã xử lý resource/module/class identity, chỉ override **loadClass** khi chính sách thực sự cần thay đổi.

### 13.1. Đồng thời và việc định nghĩa lặp

**ClassLoader.loadClass** mặc định dùng cơ chế khóa **getClassLoadingLock(name)**, kèm bước **findLoadedClass**. Khi viết loader tùy biến, phải tránh hai thread cùng định nghĩa một binary name trong cùng loader; điều đó có thể gây lỗi linkage.

Class loader muốn nạp song song các tên khác nhau có thể đăng ký **parallel-capable** theo quy tắc Java API. Việc đăng ký có yêu cầu thực hiện trong giai đoạn khởi tạo lớp loader và cách phối hợp lớp cha; không đủ chỉ thêm synchronized ở vài chỗ là mọi chính sách song song đều đúng.

## 14. Classpath, resource lookup và delegation

Class loader cũng tìm **resources**. Tên resource thường là đường dẫn dạng **/**, ví dụ:

~~~text
config/defaults.properties
images/logo.png
META-INF/services/com.example.MyService
~~~

Trong khi tên binary class thường dùng dấu chấm:

~~~text
com.example.MyService
~~~

Sự khác biệt này dẫn đến lỗi phổ biến khi gọi:

~~~java
// Minh họa trong phương thức:
ClassLoader loader = Thread.currentThread().getContextClassLoader();
var resource = loader.getResource("config/defaults.properties");
~~~

Nếu gọi **getResource("com.example.MyService")** để tìm tệp class theo mặc định, bạn đang dùng sai định dạng tên resource. Với class file, resource path thường sẽ là **com/example/MyService.class**.

**Resource delegation** thường đi qua parent, nhưng có các quy tắc resource lookup riêng và Java modules có encapsulation. Không nên kết luận từ việc **getResource(...) == null** rằng lớp Java chắc chắn không thể được nạp trong mọi tình huống; có thể class được tạo từ bytes động, package chưa được mở, path khác hoặc loader khác.

## 15. Chẩn đoán các lỗi class loader hay gặp

| Triệu chứng/lỗi | Ý nghĩa thường gặp | Cần kiểm tra |
| --- | --- | --- |
| **ClassNotFoundException** | Một yêu cầu tìm class bằng tên không tìm được | Binary name, caller loader, classpath/module path, custom findClass, TCCL |
| **NoClassDefFoundError** | JVM cần một class khi linkage/use nhưng không lấy được định nghĩa cần thiết; cũng có thể xuất hiện sau lỗi khởi tạo | Root cause, dependency, class bytes, initialization failure trước đó |
| **ExceptionInInitializerError** | Một static initializer thất bại và lỗi được bọc theo quy tắc | Exception gốc trong static initializer |
| **ClassCastException** dù tên lớp giống nhau | Hai Class objects khác defining loader hoặc interface/version không khớp | In binary name và **getClassLoader()** của cả hai bên |
| **LinkageError** (các dạng khác nhau) | Vi phạm ràng buộc liên kết/định nghĩa, ví dụ duplicate definition hay loader constraints | Loader nào define, descriptors, class versions và log load |
| **IllegalAccessError** | Quy tắc truy cập JVM bị vi phạm khi linking/runtime | Runtime package, defining loader, module access |
| **InaccessibleObjectException** | Reflective access bị module encapsulation chặn | Opens/exports, module graph, tránh reflection vào API nội bộ |
| **ServiceConfigurationError** | Provider SPI không tìm được/tạo được/không assignable | TCCL, META-INF/services, provider module, type identity |
| Plugin không unload được | Tham chiếu mạnh vẫn giữ loader, class hoặc thread của plugin | ThreadLocals, executors, caches, callbacks, service registries |

Một lỗi hiển thị **ClassNotFoundException: com.example.Foo** không đủ chứng minh file Foo.class thiếu khỏi toàn bộ máy: có thể class ở một loader khác không nằm trong đường delegation, hoặc tên module/cấu hình path khác.

### 15.1. Hướng điều tra từng bước

1. Xác định **class nào đang được yêu cầu** và binary name chính xác.
2. Xác định **điểm phát sinh yêu cầu**: reflection, application main, framework, ServiceLoader, loader tùy biến.
3. In **Class.getClassLoader()** của lớp đã nạp liên quan và đường parent bằng **getParent()**; lưu ý bootstrap thường hiển thị null.
4. Kiểm tra **ClassLoader.getSystemClassLoader()**, **getPlatformClassLoader()** và TCCL của thread hiện hành khi có SPI/plugin.
5. Kiểm tra classpath/module path thực sự của tiến trình, dependency version và JAR entries.
6. Với custom loader, xem thứ tự **findLoadedClass → parent → findClass**, đồng bộ hóa, và liệu loader có define trùng một tên hay không.
7. Với lỗi cast/linkage, so sánh **Class object identity** và defining loader, không chỉ so tên kiểu.
8. Với lỗi khởi tạo, tìm exception gốc trong static initializer trước khi đổi classpath.
9. Nếu dùng JPMS, kiểm tra reads, exports, opens và package thuộc module nào.
10. Sửa đúng ranh giới; chạy lại integration test trên đúng cách khởi động ứng dụng.

### 15.2. Công cụ quan sát

Với HotSpot/JDK 17 có thể sử dụng:

~~~bash
java -Xlog:class+load=info -cp . LoaderBasics
~~~

Tùy cấu hình, thông tin nạp lớp được ghi vào log, có thể gồm nhiều dòng ở output streams; **không** dùng tổng output của lệnh này làm stdout chuẩn của ví dụ vì nó phụ thuộc runtime.

Một tùy chọn tương thích trong nhiều phiên bản Java là:

~~~bash
java -verbose:class -cp . LoaderBasics
~~~

Với tiến trình đang chạy và **jcmd** được hỗ trợ, có thể xem danh sách diagnostic commands:

~~~bash
jcmd <PID> help
~~~

Rồi dùng các lệnh về class loader như **VM.classloaders** hoặc thống kê loader nếu có trong JVM cụ thể. Chuỗi **<PID>** là placeholder của lệnh terminal, không phải ký tự nên gõ nguyên văn; thay bằng PID của tiến trình thực. Cần quyền phù hợp để attach/chẩn đoán.

Khi log ghi một class “được load từ nơi X”, đừng chỉ nhìn nguồn JAR để kết luận class identity; hai loaders vẫn có thể đọc cùng JAR mà định nghĩa hai class khác nhau.

## 16. Bài tập tự kiểm tra

**Câu 1.** Trong JDK 17 mặc định, **String.class.getClassLoader()** thường trả giá trị gì? Có phải String không được nạp?

**Đáp án:** Trả **null**, đại diện bootstrap loader. String vẫn là class runtime đã được JVM nạp/định nghĩa.

**Câu 2.** Application loader gọi **loadClass("java.lang.String")**, rồi parent/bootstrap tìm được class. Ai là defining loader?

**Đáp án:** Bootstrap; application loader có thể là initiating loader theo yêu cầu, nhưng không tạo định nghĩa mới cho String.

**Câu 3.** Hai loader custom riêng cùng define **com.example.Widget** từ cùng class bytes. Hai class runtime bằng nhau không?

**Đáp án:** Không. Danh tính class là cặp **(binary name, defining loader)**; nếu defining loader khác, Class objects khác.

**Câu 4.** Nếu hai loader child khác nhau đều delegate tới cùng parent đã define **shared.Api**, hai kết quả có thể là cùng Class object không?

**Đáp án:** Có. Parent-first trả định nghĩa từ parent; child không tự động define bản sao.

**Câu 5.** Thứ tự mặc định trong **ClassLoader.loadClass(name, resolve)** là gì?

**Đáp án:** Kiểm tra **findLoadedClass**, ủy quyền parent/bootstrap, nếu không tìm được thì **findClass** của chính loader; khi cần có bước **resolveClass**. Nếu không tìm được thì báo **ClassNotFoundException**.

**Câu 6.** **loadClass** có bắt buộc chạy static initializer ngay không?

**Đáp án:** Không. Nạp lớp và initialization là hai giai đoạn khác; **Class.forName(name, true, loader)** chủ động yêu cầu initialization.

**Câu 7.** Thay TCCL có khiến một class đã do system loader định nghĩa được “chuyển” sang plugin loader không?

**Đáp án:** Không. TCCL thay ngữ cảnh lookup cho những API/framework sử dụng nó, không thay defining loader của class đã tạo.

**Câu 8.** Vì sao ServiceLoader có thể không tìm thấy provider dù JAR có trên hệ thống?

**Đáp án:** Tùy overload và môi trường, nó có thể đang dùng TCCL hoặc module/configuration khác, provider không nằm trong phạm vi loader được dùng, thiếu metadata hoặc provider không tương thích kiểu API chung.

**Câu 9.** Khi thấy **ClassCastException** với hai tên kiểu giống nhau, nên kiểm tra những gì?

**Đáp án:** So sánh chính **Class objects**, defining loaders, loader parent paths và các bản sao dependency/API. Cùng tên binary không đủ chứng minh cùng kiểu runtime.

**Câu 10.** Platform loader có tuyệt đối không thể delegate sang application loader trên JDK 17 không?

**Đáp án:** Không. Tài liệu Java 17 lưu ý những trường hợp module visibility/upgrading có thể khiến platform loader delegate sang loader khác, kể cả application loader.

**Câu 11.** Array class của kiểu primitive có tên dạng **[I** có file **[I.class** để loader đọc hay không?

**Đáp án:** Không theo mô hình Java class file thông thường. JVM tạo array class theo nhu cầu; primitive array có loader hiển thị **null**. Reference array dùng defining loader của component reference type khi hỏi Class.getClassLoader().

**Câu 12.** Vì sao không nên ép **ClassLoader.getSystemClassLoader()** sang **URLClassLoader** trên Java 17?

**Đáp án:** Built-in system loader là implementation detail và không được API bảo đảm thuộc URLClassLoader. Dùng public APIs hay cơ chế build/deployment thích hợp.

## 17. Checklist áp dụng khi thiết kế hệ thống

- **Classpath app đơn giản:** dựa vào system loader mặc định; không cần tự tạo ClassLoader.
- **Chạy plugin tùy biến:** xác định parent, namespace của plugin, API chung và chính sách delegation theo package.
- **Đăng ký SPI:** hiểu TCCL, ServiceLoader, provider metadata và module configuration.
- **Tách phiên bản dependency:** kiểm tra class identity qua defining loader, tránh sao chép API/interface chung vào từng loader con.
- **Dùng Reflection:** chọn loader rõ ràng khi cần; cân nhắc **Class.forName(name, false, loader)** nếu chỉ muốn tìm/kiểm tra loại mà chưa muốn initialize.
- **Quản lý lifecycle:** dừng thread/timer, giải phóng registry/cache và TCCL khi plugin ngừng dùng để loader có thể được thu gom khi không còn reachable.
- **Module system:** kiểm tra cả loader visibility và quan hệ reads/exports/opens.
- **Chẩn đoán lỗi:** ghi tên class, defining loader, nơi load, error cause và trạng thái thread context phù hợp; tránh dựa duy nhất vào tên package hay JAR.
- **Bytecode instrumentation:** đảm bảo class bytes, package, module, class loader identity và linking constraints cùng phù hợp; tránh define trùng tên trong một loader.

### Tài liệu chính thức

- **Java 17 API — java.lang.ClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java 17 API — java.lang.Class**, các phương thức **getClassLoader**, **forName**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java 17 API — java.lang.Thread**, **getContextClassLoader/setContextClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- **Java 17 API — java.util.ServiceLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **JVMS Java SE 17, §5.3 — Creation and Loading**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS Java SE 17, §5.4 — Linking**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JVMS Java SE 17, §5.5 — Initialization**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5

**Ghi nhớ:** Trên Java 17+, **bootstrap** cung cấp nền tảng lõi, **platform** xử lý nhiều platform classes, và **application/system** thường nạp lớp ứng dụng. ClassLoader mặc định **ưu tiên ủy quyền parent trước rồi mới findClass**, nhưng custom loader và cơ chế module có thể phức tạp hơn sơ đồ cây. **Cùng binary name + khác defining loader = khác class runtime**. **Loading không đồng nghĩa initialization**; **TCCL không phải defining loader**. Khi xử lý class loading, hãy luôn chỉ ra **loader nào nhận yêu cầu, loader nào định nghĩa, và lớp đang ở giai đoạn nào của vòng đời JVM**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-003">Định danh lớp bằng tên nhị phân kết hợp bộ nạp định nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Định danh lớp bằng tên nhị phân kết hợp bộ nạp định nghĩa trong JVM

## 1. Ý tưởng trung tâm: tên giống nhau chưa chắc cùng một kiểu

Trong Java source, khi nhìn thấy `com.example.Service`, chúng ta thường nghĩ đây là một lớp duy nhất. Nhưng JVM có thể chạy nhiều ứng dụng, plugin, module hoặc môi trường cách ly, mỗi môi trường có cách nạp các class file riêng. Vì vậy JVM không thể nhận diện một **class/interface runtime** chỉ bằng chuỗi tên.

Theo **Java Virtual Machine Specification (JVMS §5.3)**, một class hoặc interface tại thời điểm chạy được xác định bởi **cặp**:

> **(Tên nhị phân — binary name, bộ nạp định nghĩa — defining class loader)**

Có thể ký hiệu:

```text
Runtime type identity = (N, Ld)

N  = binary name của class/interface
Ld = defining class loader đã định nghĩa class/interface ấy
```

Hệ quả cực kỳ quan trọng:

- **Cùng binary name + cùng defining loader** → cùng class/interface runtime được đại diện bởi cùng một đối tượng `Class` trong một JVM, theo những quy tắc class loading hợp lệ.
- **Cùng binary name + defining loader khác** → có thể là **hai class/interface runtime khác nhau**, dù bytecode `.class` được đọc từ cùng một tệp và các khai báo nguồn hoàn toàn giống nhau.
- **Binary name khác** → là hai định danh class/interface khác nhau, dù có dùng cùng một loader.
- Việc một loader **yêu cầu nạp** một class không nhất thiết nghĩa là chính loader đó đã **định nghĩa** class, vì yêu cầu có thể được **ủy quyền (delegation)** cho loader khác.

Ví dụ logic:

```text
("com.example.Plugin", loader A) ≠ ("com.example.Plugin", loader B)

("com.example.Plugin", loader A) = ("com.example.Plugin", loader A)
```

Đây là **định danh kiểu** trong JVM, không phải phép so sánh chuỗi, không phải `equals()` của các instance, và không phụ thuộc việc hai tệp class có nội dung giống hệt nhau hay không.

**Mục tiêu học tập:** phân biệt binary name với các dạng tên khác, defining loader với initiating loader, giải thích ClassCastException “lớp X không thể cast về lớp X”, biết cách cô lập plugin và chia sẻ API đúng cách, hiểu ảnh hưởng tới package, array types, reflection và chẩn đoán class loading.

## 2. Binary name chính xác là gì?

**Binary name** là tên của class/interface theo quy tắc Java dùng trong class file và liên kết nhị phân, không phải mọi cách hiển thị tên thân thiện của Java source. Với top-level class, tên thường gồm tên package và tên class nối bằng dấu chấm:

```java
package com.example;
public class Account { }
```

Binary name: `com.example.Account`.

Với member class lồng nhau:

```java
package com.example;

public class Outer {
    public static class Inner { }
}
```

Binary name của member class: **`com.example.Outer$Inner`**. Trong Java source, chúng ta thường viết `Outer.Inner`; dấu `$` trong binary name phản ánh quy tắc tên nhị phân, không phải một package mới.

### 2.1. Các dạng tên cần phân biệt

| Dạng tên | Ví dụ | Dùng trong hoàn cảnh |
| --- | --- | --- |
| Source/canonical name | `com.example.Outer.Inner` | Cú pháp tên kiểu dễ đọc trong Java source; `Class.getCanonicalName()` khi có canonical name |
| **Binary name** | `com.example.Outer$Inner` | Nạp class theo tên, định danh runtime của nonarray class/interface |
| Internal form trong class file | `com/example/Outer$Inner` | Cấu trúc constant pool và nhiều descriptor JVM |
| Đường dẫn class resource | `com/example/Outer$Inner.class` | Tìm bytecode class theo quy ước resource của classpath/JAR |
| Reference field descriptor | `Lcom/example/Outer$Inner;` | Descriptor kiểu reference trong JVM |
| Array class name | `[Lcom.example.Outer$Inner;` | Dạng `Class.getName()` của mảng reference |

**Không gõ `com.example.Outer.Inner` vào `ClassLoader.loadClass` khi đang muốn member class `Outer.Inner`** nếu binary name của nó là `com.example.Outer$Inner`. Đối với `Class.forName` và `ClassLoader.loadClass`, hãy dùng dạng tên phù hợp với hợp đồng API.

Một số class cục bộ (local) và anonymous class có binary name do compiler tạo theo quy tắc liên quan số thứ tự và dấu `$`. Đừng xây API dựa trên giả định mọi tên dạng `$1` sẽ ổn định khi đổi mã nguồn hoặc phiên bản compiler.

### 2.2. Đừng lấy tên file thay cho định danh runtime

Một tệp `Plugin.class` không có nghĩa nó là một class tên `Plugin` trong default package. Binary name có thể là `com.example.Plugin`, và class loader phải tìm/định nghĩa class đúng tên mà JVM kỳ vọng. Nếu dùng `defineClass(name, bytes,...)` mà tên truyền vào không khớp binary name được khai báo trong bytecode, có thể xảy ra lỗi liên quan tên/định dạng class.

Tương tự, **cùng binary name không chứng minh cùng class**: loader cũng là một phần của định danh.

## 3. Defining loader và initiating loader: hai vai trò khác nhau

Một class loader có thể:

1. **Tự đọc bytecode rồi định nghĩa class**, thông qua các cơ chế như `defineClass`.
2. **Ủy quyền cho loader khác** để tìm và định nghĩa class.
3. Tham gia chuỗi yêu cầu nạp mà class thực tế cuối cùng được định nghĩa bởi một loader khác.

JVMS dùng hai khái niệm:

- **Defining class loader (`Ld`):** loader trực tiếp định nghĩa class/interface runtime từ biểu diễn nhị phân phù hợp, hoặc loader mà đặc tả gắn với định nghĩa array class theo quy tắc riêng.
- **Initiating class loader (`Li`):** loader bắt đầu/khởi xướng một hoạt động loading cho tên class/interface và được JVM ghi nhận theo các quy tắc loading, kể cả khi việc định nghĩa được ủy quyền cho loader khác.

Một class có **một defining loader** nhưng có thể có **nhiều initiating loaders**.

### 3.1. Ví dụ delegation

```text
Caller yêu cầu loader PluginLoader nạp java.lang.String
                       |
                       v
                PluginLoader
                       |
                  delegate
                       v
               Parent / Platform ...
                       |
                       v
                Bootstrap loader
                       |
                       v
             định nghĩa java.lang.String
```

Khi yêu cầu thành công, `PluginLoader` đã tham gia **initiating loading**, nhưng **bootstrap loader** mới là defining loader của `java.lang.String`. Nếu cùng `PluginLoader` yêu cầu lại `java.lang.String`, việc nạp không tạo ra một class String mới của PluginLoader.

**Đừng lấy loader được truyền vào `loadClass` hoặc `Class.forName` rồi mặc định đó là defining loader**. Loader ấy có thể ủy quyền.

### 3.2. `Class.getClassLoader()` trả về gì?

Với một nonarray class thông thường, `Class.getClassLoader()` cung cấp **defining class loader** của class đó. Nếu class được định nghĩa bởi bootstrap loader, API thông thường trả về **`null`** để biểu thị bootstrap (không phải “class chưa được nạp”).

```java
System.out.println(String.class.getClassLoader() == null); // true trên JDK chuẩn
```

Dòng trên là ví dụ quan sát bootstrap definition của `String` trên JDK phổ biến. Không so sánh mọi loader bằng tên in ra từ `toString()`; để biết có phải cùng loader object, kiểm tra identity của đối tượng loader.

### 3.3. Parent-first là quy ước của loader thường dùng, không phải định nghĩa của class identity

`ClassLoader.loadClass` mặc định dùng mô hình ưu tiên ủy quyền cho parent (*parent-first delegation*) đối với những class phù hợp. Điều này giúp ứng dụng sử dụng chung các lớp nền tảng Java và tránh tạo nhiều bản không cần thiết.

Nhưng **parent-first không phải một định luật bắt buộc cho mọi custom loader**. Có các thiết kế child-first hoặc chọn lọc theo tên để cô lập plugin. Dù chiến lược nạp nào được dùng, JVM vẫn xác định class runtime theo **binary name và defining loader thực tế**.

Không nên tùy tiện nạp lại các lớp nền tảng hoặc class thuộc package `java.*` bằng loader tùy biến; Java có các ràng buộc bảo vệ package, liên kết kiểu và bảo mật. Thí nghiệm bên dưới **chỉ tự định nghĩa lại một lớp do ứng dụng sở hữu**, còn các lớp nền tảng được ủy quyền.

## 4. Ví dụ trực quan: hai loader định nghĩa cùng bytecode

Xét một class file có binary name `com.example.Plugin`.

```text
                      Plugin.class (cùng bytecode)
                               |
             +-----------------+-----------------+
             |                                   |
        ByteArrayLoader A                    ByteArrayLoader B
             |                                   |
       defineClass(...)                    defineClass(...)
             |                                   |
             v                                   v
    Class A: (Plugin, A)                 Class B: (Plugin, B)
             |                                   |
             +-----------------X-----------------+
                      Không cùng kiểu runtime
```

Hai bản có thể:

- Có cùng `Class.getName()` là `com.example.Plugin`.
- Có cùng method signatures, field names và bytecode.
- Vẫn **khác `Class` object**: `classA == classB` là false.
- Không thể ép một instance `classA` sang `classB` bằng cast thông thường chỉ vì tên giống nhau.
- Có thể gây `ClassCastException` lúc runtime, hoặc lỗi liên kết khác khi các kiểu của hai loader xung đột.

Đặc điểm này được dùng trong plugin isolation, application servers, hot deployment, test runners và các hệ thống cho phép nhiều phiên bản thư viện cùng tồn tại.

**Tách biệt không đồng nghĩa an toàn tuyệt đối:** các loader vẫn sống trong cùng một tiến trình JVM và có thể truy cập tài nguyên dùng chung nếu API, quyền và cơ chế bảo vệ cho phép. ClassLoader là công cụ tách **namespace/type identity**, không tự tạo sandbox bảo mật hoàn chỉnh.

## 5. Thí nghiệm Java 17+ hoàn chỉnh: cùng tên, khác defining loader

Ví dụ này dùng **một tệp nguồn** để dễ chạy. Chúng ta sẽ dùng hai instance custom `ClassLoader` đọc **cùng một tệp class của nested class**, sau đó gọi `defineClass` riêng trong mỗi loader. Cả hai loader vẫn ủy quyền các class khác cho parent, để không gây xung đột với `java.lang.*`.

Tạo tệp **`ClassIdentityDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;

public class ClassIdentityDemo {
    public static class Payload {
        public Payload() {}

        public String say() {
            return "payload";
        }
    }

    static final class SelectiveLoader extends ClassLoader {
        private final String target;

        SelectiveLoader(ClassLoader parent, String target) {
            super(parent);
            this.target = target;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);

                if (result == null) {
                    if (name.equals(target)) {
                        String resource = name.replace('.', '/') + ".class";

                        try (InputStream in =
                                getParent().getResourceAsStream(resource)) {
                            if (in == null) {
                                throw new ClassNotFoundException(name);
                            }

                            byte[] bytes = in.readAllBytes();
                            result = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    } else {
                        result = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String binaryName = Payload.class.getName();
        ClassLoader parent = ClassIdentityDemo.class.getClassLoader();

        SelectiveLoader one = new SelectiveLoader(parent, binaryName);
        SelectiveLoader two = new SelectiveLoader(parent, binaryName);

        Class<?> first = one.loadClass(binaryName);
        Class<?> second = two.loadClass(binaryName);
        Object value = first.getDeclaredConstructor().newInstance();

        System.out.println("same binary name: "
                + first.getName().equals(second.getName()));
        System.out.println("same Class object: " + (first == second));
        System.out.println("loader one cache: "
                + (one.loadClass(binaryName) == first));
        System.out.println("defining loader one: "
                + (first.getClassLoader() == one));
        System.out.println("defining loader two: "
                + (second.getClassLoader() == two));

        System.out.println("first.isInstance(value): "
                + first.isInstance(value));
        System.out.println("second.isInstance(value): "
                + second.isInstance(value));
        System.out.println("app Payload.isInstance(value): "
                + Payload.class.isInstance(value));

        System.out.println("shared String by delegation: "
                + (one.loadClass("java.lang.String")
                    == two.loadClass("java.lang.String")));

        Class<?> arrayOne = Array.newInstance(first, 0).getClass();
        Class<?> arrayTwo = Array.newInstance(second, 0).getClass();

        System.out.println("array binary names equal: "
                + arrayOne.getName().equals(arrayTwo.getName()));
        System.out.println("array runtime types equal: "
                + (arrayOne == arrayTwo));
        System.out.println("array defining loader one: "
                + (arrayOne.getClassLoader() == one));

        try {
            Payload.class.cast(value);
            System.out.println("cast succeeded");
        } catch (ClassCastException exception) {
            System.out.println("cross-loader cast: ClassCastException");
        }
    }
}
```

### 5.1. Biên dịch và chạy

Đặt tệp tại thư mục làm việc, sau đó chạy với **JDK 17 hoặc mới hơn**:

```bash
javac --release 17 ClassIdentityDemo.java
java ClassIdentityDemo
```

Lệnh `javac` thông thường tạo các tệp class, gồm:

```text
ClassIdentityDemo.class
ClassIdentityDemo$Payload.class
ClassIdentityDemo$SelectiveLoader.class
```

Các tên nested class ở trên là binary names theo dạng dấu `$`. Đừng xóa tệp `ClassIdentityDemo$Payload.class` vì loader cần đọc resource bytes tương ứng.

### 5.2. Stdout chính xác

```text
same binary name: true
same Class object: false
loader one cache: true
defining loader one: true
defining loader two: true
first.isInstance(value): true
second.isInstance(value): false
app Payload.isInstance(value): false
shared String by delegation: true
array binary names equal: true
array runtime types equal: false
array defining loader one: true
cross-loader cast: ClassCastException
```

Các dòng trên không in PID, identity hash code hay tên implementation loader, nên kết quả **không phụ thuộc số thứ tự cấp phát của loader**. Ví dụ đã chọn chỉ một class đích để tự định nghĩa lại; những class khác vẫn đi theo delegation.

### 5.3. Giải thích cách loader hoạt động

`SelectiveLoader` ghi đè `loadClass(String, boolean)`. Với đúng tên `Payload`:

1. `getClassLoadingLock(name)` đồng bộ hoạt động nạp class theo quy tắc khóa của loader.
2. `findLoadedClass(name)` kiểm tra bản đã nạp để **không định nghĩa trùng cùng tên trong cùng loader**.
3. `getParent().getResourceAsStream(resource)` đọc các byte `Payload.class` từ classpath mà parent có thể truy cập.
4. `defineClass(name, bytes, 0, bytes.length)` tạo một **định nghĩa runtime mới** thuộc loader đang gọi.
5. `resolveClass(result)` được gọi nếu flag `resolve` yêu cầu.

Với mọi tên khác `Payload`, loader dùng `super.loadClass(name, false)`, tức cơ chế tìm class của parent. Do đó cả hai loader vẫn chia sẻ `java.lang.String` từ runtime nền tảng.

**Thí nghiệm có chủ đích chọn child-definition cho một tên duy nhất.** Đây là công cụ học tập, không phải một class loader tổng quát đủ an toàn cho mọi plugin, thư viện, module, resource và vòng đời vận hành.

### 5.4. Giải thích từng nhóm kết quả

**`same binary name: true`:** cả hai đối tượng `Class` trả cùng chuỗi tên `ClassIdentityDemo$Payload`.

**`same Class object: false`:** `first` và `second` có **defining loader khác**, nên khác class identity.

**`loader one cache: true`:** khi loader một yêu cầu lại cùng binary name, nó không định nghĩa một bản mới; `findLoadedClass` trả bản đã có.

**`defining loader one/two: true`:** `Class.getClassLoader()` trả loader object đã thực sự định nghĩa class. Hai loader khác nhau chứ không chỉ là hai chuỗi tên.

**`first.isInstance(value): true`:** `value` là object instance của class do loader một định nghĩa.

**`second.isInstance(value): false`:** dù class của loader hai có cùng tên, object không thuộc runtime class đó.

**`app Payload.isInstance(value): false`:** thậm chí `Payload.class` mà bản thân ứng dụng biết qua classpath cũng là **class thứ ba** cùng binary name nhưng do parent/app loader định nghĩa; nó không cùng class với bản do loader một tạo.

**`shared String by delegation: true`:** hai custom loader ủy quyền nạp `java.lang.String` và được cùng class chuẩn trả về.

**Hai dòng array:** array class của reference component mang identity gắn với component type và defining loader; mỗi array class ở đây cũng là một runtime class khác nhau.

**`cross-loader cast: ClassCastException`:** ép instance của loader một sang `Payload` của app loader là sai kiểu runtime. Lệnh `Class.cast` phát hiện và ném exception, chương trình bắt lại để in thông báo ổn định.

## 6. Tại sao hai class có cùng bytecode vẫn không cast được?

Java có kiểm tra kiểu ở **compile-time** và ở **runtime**. Compiler xác minh thao tác theo các kiểu mà mã đang tham chiếu, nhưng JVM khi thực thi cần biết **đối tượng thật thuộc runtime class nào**.

Ví dụ:

```text
classA = ("demo.Plugin", loaderA)
classB = ("demo.Plugin", loaderB)

Object x = new instance of classA

classA.isInstance(x) → true
classB.isInstance(x) → false
```

Không có quy tắc “nếu hai class định nghĩa cùng field/method thì JVM xem như cùng type” trong class identity. Java không áp dụng **structural typing** tự động kiểu như một số ngôn ngữ khác. Interface/superclass compatibility vẫn dựa vào các quan hệ kiểu được nạp và liên kết hợp lệ.

Trong thực tế, stack trace đôi khi gây bối rối:

```text
java.lang.ClassCastException:
  class demo.Plugin cannot be cast to class demo.Plugin
```

Đây chỉ là một **mẫu thông báo rút gọn**, không phải stdout của chương trình ví dụ. Các JVM hiện đại có thể kèm module, loader, class identity trong thông báo. Đừng kết luận runtime “nhầm lẫn”: **hai lần xuất hiện của `demo.Plugin` có thể là hai class khác nhau theo defining loader**.

### 6.1. `Class.getName()` không phải kiểm tra identity

Không nên dùng:

```java
if (a.getClass().getName().equals(b.getClass().getName())) {
    // kết luận a và b cùng runtime type
}
```

Đây là **mã minh họa có thể cho kết luận sai**. Chuỗi tên bằng nhau chỉ so sánh **một nửa** định danh. Khi muốn biết cùng runtime class chính xác, so sánh:

```java
boolean sameRuntimeClass = a.getClass() == b.getClass();
```

Nếu muốn biết một object có tương thích với kiểu được đại diện bởi `Class<?> clazz`, dùng:

```java
boolean compatible = clazz.isInstance(a);
```

Nếu `a` là null, `getClass()` gây `NullPointerException`; `clazz.isInstance(null)` trả false theo hợp đồng API. Dùng đúng phép kiểm tra theo ý nghĩa nghiệp vụ.

### 6.2. Vì sao `instanceof` cũng có thể false?

Một biểu thức như:

```java
boolean ok = value instanceof SomeType;
```

được kiểm tra theo **runtime type** và quan hệ assignability với `SomeType` mà lớp thực hiện câu lệnh đang nhìn thấy. Nếu `SomeType` qua loader khác với runtime class của `value`, kiểm tra có thể false dù `getName()` cùng tên.

## 7. Initiating loader không tự biến thành defining loader

Giả sử `L1` gọi `loadClass("java.util.List")` rồi delegation đi đến loader chuẩn `L0`. Lúc này:

```text
Yêu cầu từ L1
    |
    +→ parent/delegation
             |
             v
         L0 định nghĩa java.util.List
```

`L1` có thể được ghi nhận là **initiating loader** cho class `java.util.List`, nhưng `List.class.getClassLoader()` phản ánh defining loader thực tế, không nhất thiết là `L1`.

**Một class có thể có nhiều initiating loaders**, còn defining loader là một thành phần cố định của identity.

### 7.1. Khi nào sự phân biệt ảnh hưởng thiết kế?

- **Plugin system:** loader plugin hỏi parent về interface chung; plugin loader **khởi xướng** nạp interface nhưng **parent định nghĩa** nó, nhờ vậy host và plugin thống nhất API.
- **Web application server:** nhiều ứng dụng có loader riêng nhưng cùng dùng class Java nền tảng từ parent.
- **Dependency isolation:** plugin loader tự định nghĩa implementation/dependency nội bộ để tránh xung đột phiên bản với plugin khác.

Không yêu cầu mỗi loader tự định nghĩa mọi class nó nhìn thấy. Nếu làm vậy, nó có thể vô tình tách đôi cả các **API cần được chia sẻ**, khiến object không thể giao tiếp qua interface như dự kiến.

### 7.2. Class initialization không xác định defining loader

Khái niệm class loading/definition khác với **class initialization** (chạy `static initializer`). Một class có thể được load và link trước khi nó được khởi tạo. Điều đó không thay đổi cặp định danh đã có.

Ví dụ:

```java
Class<?> type = Class.forName("java.lang.String", false,
        ClassIdentityDemo.class.getClassLoader());
```

Đây là **đoạn mã minh họa bên trong một phương thức**, dùng overload của `Class.forName` với cờ `initialize=false`. Cờ này ảnh hưởng yêu cầu initialization, không thay đổi danh tính của class. Loader truyền vào có thể tiếp tục delegate tới defining loader khác.

## 8. Các loader tích hợp và tác động tới định danh lớp

Trên HotSpot/JDK 17 thông thường có **bootstrap** (API `getClassLoader()` thường biểu diễn bằng `null`), **platform** và **application/system** loader. Custom loader có thể tiếp tục ủy quyền hoặc tự định nghĩa bytecode; không có bảo đảm mọi loader đều dùng parent-first.

Điểm cần nhớ **riêng với class identity**: một custom loader là một **object**. Hai instance `PluginLoader` cùng Java class vẫn là hai defining loaders khác nhau nếu mỗi instance tự `defineClass` cùng binary name; còn hai loader chỉ delegate về một parent có thể cùng trả đúng một `Class<?>`. Đừng dùng tên implementation của loader thay cho phép so sánh object identity.

## 9. Array classes: ngoại lệ cần hiểu cho đúng

Đặc tả JVM phân biệt việc tạo **nonarray class/interface** và việc tạo **array class**:

- Nonarray class/interface được tạo bằng cơ chế nạp binary representation qua class loader.
- **Array class được JVM tạo trực tiếp**, không có class file `.class` thông thường tương ứng với mỗi array type.
- Với component type là **reference type**, defining loader của array class là **defining loader của component type**.
- Với component type là **primitive**, defining loader của array class là **bootstrap loader**.

Ví dụ:

```java
String[] strings = new String[2];
int[] numbers = new int[2];
```

- `String[]` là array type gắn với `java.lang.String`; trên JDK thông thường component class do bootstrap định nghĩa.
- `int[]` có primitive component `int`; array class do JVM tạo, được gắn với bootstrap loader.
- `Plugin[]` có array class gắn với defining loader của component class `Plugin`.

### 9.1. Minh họa trong chương trình đã chạy

```java
Class<?> arrayOne = Array.newInstance(first, 0).getClass();
Class<?> arrayTwo = Array.newInstance(second, 0).getClass();
```

Hai array classes này có:

- Cùng `Class.getName()` vì component classes có cùng binary name.
- **Khác `Class` object** vì component classes thuộc hai defining loaders khác nhau.
- Mỗi array class có defining loader tương ứng với component class.

Vì vậy **định danh array class cũng bị ảnh hưởng bởi component type identity**, dù array class không được định nghĩa từ một tệp `.class` riêng.

### 9.2. Tên của array class trông lạ nhưng có quy luật

Ví dụ:

| Kiểu Java | `Class.getName()` |
| --- | --- |
| `int[]` | `[I` |
| `String[]` | `[Ljava.lang.String;` |
| `String[][]` | `[[Ljava.lang.String;` |
| `Payload[]` của nested class | `[LClassIdentityDemo$Payload;` |

`Class.getName()` của array class dùng dạng bắt đầu bằng `[` theo descriptor quy định, không phải tên kiểu nguồn `java.lang.String[]`. Đừng nhầm chuỗi tên array này với đường dẫn class file. Việc nạp array type bằng API reflection có hợp đồng riêng.

## 10. Runtime package và kiểm tra quyền truy cập

Ở cấp Java source, package thường được nhìn như chuỗi tên:

```java
package com.example.plugins;
```

Nhưng tại runtime, theo JVMS, **runtime package** của nonarray class/interface được xác định bởi **package name và defining loader**.

Do đó:

```text
com.example.plugins.PluginA    do loader A định nghĩa
com.example.plugins.PluginB    do loader B định nghĩa
```

Dù hai class có cùng tên package dạng chuỗi **`com.example.plugins`**, chúng **không đương nhiên thuộc cùng runtime package**. Điều này ảnh hưởng những quyền truy cập theo package trong kiểm tra JVM, chẳng hạn package-private members và một số ràng buộc liên quan class accessibility.

**Sai lầm phổ biến:** tạo hai loader độc lập, định nghĩa hai class “cùng package” rồi kỳ vọng có thể gọi package-private method của nhau như khi chúng được nạp bằng một loader duy nhất.

### 10.1. Package trong tên không tự tạo quan hệ tin cậy

Một plugin không thể chỉ đặt class trong `com.host.internal` rồi nghiễm nhiên có mọi quyền truy cập package-private vào class của host nếu plugin class do defining loader khác tạo. Loader identity là một phần quan trọng của runtime package boundary.

Trong Java 9+ còn có **module system (JPMS)**, bổ sung các quy tắc module readability, exports và opens. Do vậy khi điều tra `IllegalAccessError` hoặc reflective access failure, có thể cần kiểm tra **cả defining loader, runtime package và module**. Không quy hết mọi lỗi truy cập cho class loader.

## 11. Shared API, isolated implementation: thiết kế plugin thực tế

Một kiến trúc plugin thường cần **cô lập implementation** nhưng **chia sẻ hợp đồng** với host:

```text
              Host / Parent loader
               định nghĩa api.Task
                       |
        +--------------+--------------+
        |                             |
   Plugin loader A                Plugin loader B
   định nghĩa impl.A              định nghĩa impl.B
   delegate api.Task              delegate api.Task
        |                             |
        +-------- thực thi qua -------+
                cùng api.Task
```

Nếu parent định nghĩa `api.Task` và cả plugin A lẫn plugin B ủy quyền nạp nó, JVM nhìn thấy **một interface runtime chung**. Các implementation có thể được dùng qua interface ở host nếu các phương thức/kiểu liên quan liên kết thành công.

Ngược lại, nếu mỗi plugin tự định nghĩa một bản `api.Task`:

```text
("api.Task", loader A) ≠ ("api.Task", loader B)
("api.Task", loader A) ≠ ("api.Task", host loader)
```

thì một object “implements api.Task” theo loader A **không tự trở thành instance của api.Task theo host**. Dù tên interface giống nhau, kiểu runtime khác nhau.

### 11.1. Ví dụ hợp đồng shared API

```java
// Ví dụ phân tách sang module/thư viện chung:
package api;

public interface Task {
    String run();
}
```

Class nguồn minh họa này **không phải chương trình độc lập**; cần đặt tại `api/Task.java` và build với một classpath/module path phù hợp. Quan trọng là package `api` và class/interface `Task` nên được **định nghĩa bởi loader được chia sẻ**, rồi implementation của từng plugin chỉ tham chiếu đến nó.

Khi API nhận/trả giá trị, cần xem cả các kiểu trong **method descriptors**. Nếu hai loader nhìn một kiểu tham số/kiểu trả về theo các class identity khác nhau, liên kết qua các loader có thể tạo lỗi hoặc mismatch.

### 11.2. Không biến mọi thư viện thành shared dependency

Cô lập dependency riêng có thể có ích nếu hai plugin cần hai phiên bản thư viện không tương thích. Nhưng nếu library type xuất hiện trong **chữ ký API giao tiếp qua boundary**, các bên phải có thiết kế thống nhất kiểu. Những kiểu dùng chung trong interface là điểm quan trọng để xác định **nên delegate** hay **nên isolate**.

Một quy tắc thực hành:

- **Share** các API contract, DTO công khai hoặc class dùng để trao đổi qua boundary khi muốn type identity thống nhất.
- **Isolate** implementation/internal dependency không cần trao đổi trực tiếp nếu có lý do về phiên bản hoặc lifecycle.
- Thiết kế cơ chế serialization/message passing khi muốn giao tiếp giữa những vùng có namespace khác nhau mà không thể chia sẻ cùng kiểu runtime.

Đừng áp dụng “child-first toàn bộ mọi package” chỉ để giải quyết một trùng tên dependency rồi tạo ra lỗi cast cho interface chung.

## 12. Loading constraints và an toàn kiểu khi liên kết

JVMS §5.3.4 quy định **loading constraints** để bảo đảm tính an toàn kiểu khi các class từ những defining loaders khác nhau có symbolic references tới trường/phương thức mà kiểu xuất hiện trong descriptor.

Ví dụ khái niệm:

```text
Host method:    api.Result process(api.Request request)
Plugin method:  api.Result process(api.Request request)
```

Nếu host và plugin cần liên kết một quan hệ phương thức/kiểu qua boundary, runtime không được tùy tiện xem hai `api.Request` là tương đương khi chúng thực chất được định nghĩa bởi hai loaders khác nhau.

Do đó:

- Một số mismatch đơn giản được thấy dưới dạng `ClassCastException` hoặc `instanceof` false.
- Những vi phạm **loading constraint** nhất định có thể làm quá trình linking thất bại với `LinkageError`.
- Không có một exception duy nhất bắt buộc cho mọi tình huống class-loader mismatch; phải xem đang cast, access, resolve hay override/interface method.

**Không tự sửa bằng cách vô hiệu hóa verifier hay bỏ qua kiểm tra kiểu.** Nếu class identity thực sự xung đột, phải thiết kế lại delegation, API boundary và dependency visibility.

### 12.1. Vì sao `LinkageError` khác `ClassNotFoundException`?

`ClassNotFoundException` thường phát sinh khi API nạp class theo tên (ví dụ `loadClass` hoặc `Class.forName`) không tìm được binary representation phù hợp. Các lỗi liên kết khác có thể xuất hiện khi class đã được tìm nhưng **không thể kiểm tra, resolve, định nghĩa hoặc liên kết hợp lệ** với những class đã có.

`NoClassDefFoundError` có thể xuất hiện khi runtime cần một class nhưng không thể xác định định nghĩa hoặc class initialization trước đó thất bại. Không nên gộp tất cả thành “JVM không tìm thấy JAR”.

## 13. Những vấn đề dễ mắc với custom class loaders

### 13.1. Định nghĩa cùng binary name hai lần trong cùng loader

Một loader được thiết kế đúng nên trả lại class đã định nghĩa cho cùng tên khi nạp lại. Nếu gọi `defineClass` trùng một binary name lần thứ hai trong cùng một loader, có thể gặp `LinkageError` liên quan duplicate class definition.

Trong ví dụ, `findLoadedClass(name)` ngăn lỗi này cho loader một khi gọi lại `loadClass(name)`.

### 13.2. Không đồng bộ khi nhiều thread nạp cùng tên

Hai thread cùng yêu cầu một class có thể đua nhau tới đoạn `defineClass` nếu loader không xử lý locking/caching đúng. Sử dụng API/khóa theo hợp đồng class loading là cần thiết. Ví dụ dùng `getClassLoadingLock(name)`; mọi custom loader thực tế còn cần tính toán rõ chiến lược nạp đồng thời, nhất là khi chọn parallel-capable loaders.

### 13.3. Bỏ qua parent khiến các API chuẩn bị nhân bản

Tự định nghĩa lại quá nhiều class có thể phá vỡ object compatibility. Parent delegation giúp chia sẻ các class cốt lõi và hợp đồng API theo thiết kế, nhưng vẫn cần phân biệt **class được yêu cầu** với **class được định nghĩa**.

### 13.4. Truyền `ClassLoader` mà không biết nó có delegate hay không

`Class.forName(name, false, loader)` và `loader.loadClass(name)` không có nghĩa class nhận được chắc chắn do loader truyền vào định nghĩa. Kiểm tra thêm `result.getClassLoader()` để biết defining loader.

### 13.5. Tên loader object đẹp không làm hai class tương đương

Hai loader cùng loại và cùng `toString()` vẫn có thể là hai loader khác nhau. Nếu cần kiểm tra chính xác, dùng identity của loader object và class object; đừng dựa vào friendly name trong log.

### 13.6. Dùng classpath hoặc JAR đúng nhưng vẫn cast lỗi

Một class file nằm đúng JAR trên disk không giải thích đủ runtime identity: **loader nào đã dùng bytecode để định nghĩa class** mới là vấn đề. Cùng JAR được mở bởi hai URLClassLoader độc lập có thể tạo hai class runtime cho cùng binary name nếu cả hai tự định nghĩa chúng.

### 13.7. Giữ class loader cũ khiến không unload được class

Class unloading phụ thuộc reachability và hành vi GC/runtime. Nếu host vô tình giữ references tới loader/plugin classes/objects hoặc những thành phần như thread/context class loader, static caches, listener, ThreadLocal, v.v., class loader cũ có thể còn reachable và class metadata không được thu hồi như mong đợi.

Không giả định “hủy JAR file hoặc đóng URLClassLoader là mọi class lập tức được unload”. Vòng đời plugin phải giải phóng các reference liên quan; việc GC có/không xảy ra lúc nào là chuyện khác.

## 14. Thread context class loader không phải defining loader

Java có API `Thread.currentThread().getContextClassLoader()`, thường được các framework hoặc service-loading mechanism sử dụng để tìm resource/class qua một loader phù hợp ngữ cảnh.

Ví dụ:

```java
ClassLoader contextLoader =
        Thread.currentThread().getContextClassLoader();
```

Đây là **đoạn mã minh họa trong một phương thức**. Context loader không phải thuộc tính định danh trực tiếp của mọi class đang chạy; nó có thể thay đổi theo thread hoặc framework. Không nên cho rằng `contextLoader == SomeType.class.getClassLoader()` luôn đúng.

Ba câu hỏi cần tách khi debug:

1. **Ai bắt đầu yêu cầu nạp?** Có thể là class/module code, framework hoặc context loader.
2. **Loader nào cuối cùng định nghĩa class?** Kiểm tra `Class.getClassLoader()`.
3. **Class nào đang được dùng ở boundary?** So sánh exact `Class` objects, method parameter types và interface identity.

Một thread context loader chỉ là **đầu mối tìm kiếm/ủy quyền theo ngữ cảnh**; không thay đổi defining loader của một class đã tồn tại.

## 15. Module system và class-loader identity

Từ Java 9, **JPMS** bổ sung module graph, readability, exports/opens và module layers. Nó ảnh hưởng việc tìm, đóng gói và truy cập class. Nhưng không thay đổi kết luận chủ đạo: **class/interface runtime vẫn được xác định bằng binary name và defining loader**, đồng thời còn phải thỏa các quy tắc module liên quan.

Không đồng nhất:

- **Module name** như `com.example.app`.
- **Package name** như `com.example.service`.
- **Binary class name** như `com.example.service.Worker`.
- **Defining class loader object** của một runtime class.

Ví dụ hai module layers có thể dẫn tới cấu trúc class loaders khác nhau. Cùng một nội dung artifact không chứng minh hai runtime classes được chia sẻ, nếu cách bố trí class loader tạo ra những định nghĩa độc lập.

Để debug JPMS nên xem cả `Class.getModule()` và `Class.getClassLoader()`; hai API trả thông tin khác nhau. Đừng dùng module name làm thay thế loader identity.

## 16. Công cụ kiểm tra và cách đọc log

### 16.1. In tên và defining loader có kiểm soát

```java
static void describe(Class<?> type) {
    System.out.println("name: " + type.getName());
    System.out.println("loader: " + type.getClassLoader());
    System.out.println("module: " + type.getModule().getName());
}
```

Đây là **mã minh họa phương thức**, không phải chương trình độc lập. `getClassLoader() == null` thường tương ứng bootstrap; `getModule().getName()` có thể trả null với unnamed module.

Tên loader chỉ hỗ trợ đọc log; muốn biết hai loader có phải cùng đối tượng, so sánh `==`. Trong những môi trường có nhiều bản triển khai `toString()` khác nhau, không nên diễn giải output của loader như một ID ổn định toàn hệ thống.

### 16.2. Truy nguyên lý do cast sai

Khi thấy `ClassCastException` lạ:

1. Lấy **Class object** của đối tượng thật bằng `value.getClass()` (nếu value khác null).
2. Lấy **Class object** của kiểu API đích bằng `Target.class` hoặc reflection thích hợp.
3. So sánh **tên nhị phân** của hai `Class`.
4. So sánh **defining loaders** qua `getClassLoader()`, rồi so sánh chính các `Class` objects.
5. Kiểm tra loader delegation policy và dependency JAR/version mapping.
6. Nếu boundary đi qua interface, kiểm tra interface `Class` mà host và plugin đang sử dụng có cùng identity không.

`value.getClass().getName().equals(Target.class.getName())` cho kết quả true **không kết thúc quá trình điều tra**; cần xem loader.

### 16.3. Ghi log class loading khi cần

Trong nhiều JDK hiện đại có Unified Logging:

```bash
java -Xlog:class+load=info ClassIdentityDemo
```

Cú pháp và chi tiết output log có thể thay đổi theo JVM/phiên bản. Lệnh minh họa phù hợp với HotSpot/JDK thường dùng; không coi mọi tùy chọn `-Xlog` là một phần của mọi Java implementation.

Log có thể cho biết tên class và nguồn bytecode, nhưng để xác định identity trong mã, **`Class` object và defining loader** là bằng chứng trực tiếp hơn. Nếu class loading log quá nhiều, dùng mức/tag phù hợp với JVM đích.

### 16.4. Xem class file không cho biết toàn bộ runtime loader identity

`javap -v` cho biết binary name, constant pool, descriptors và nội dung class file, nhưng file trên đĩa **không tự có một defining loader runtime duy nhất**. Tệp bytecode có thể được đọc bởi nhiều loader và tạo nhiều class runtime.

Đây là lý do không thể chỉ dùng `javap` hoặc hash SHA-256 của `.class` để kết luận “đó là cùng `Class` trong JVM”. Cần kết hợp dữ liệu class file với thông tin class loading runtime.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Binary name đủ để định danh class.” | Phải xét **(binary name, defining loader)**. |
| “Hai tệp `.class` giống byte-for-byte thì là cùng kiểu.” | Hai loaders định nghĩa riêng vẫn có thể tạo hai kiểu runtime khác nhau. |
| “Loader nào gọi `loadClass` là defining loader.” | Nó có thể **delegate** và chỉ là initiating loader. |
| “Hai loader cùng class `URLClassLoader` nên cùng identity.” | Mỗi loader **instance** là một đối tượng riêng. |
| “`Class.getName()` giống nhau nên có thể cast.” | Cast phải kiểm tra runtime class/assignability, không chỉ chuỗi tên. |
| “Package cùng tên là cùng runtime package.” | Còn phụ thuộc **defining loader**. |
| “Array class có tệp `.class` riêng do loader định nghĩa.” | Array class được JVM tạo; defining loader theo component type. |
| “`int[]` do custom loader định nghĩa nếu code plugin tạo nó.” | Primitive array type gắn với bootstrap, không do loader plugin tự định nghĩa. |
| “Class loading cũng là chạy `static`.” | Loading/linking và initialization là các giai đoạn khác nhau. |
| “Context class loader luôn là defining loader.” | Chúng phục vụ các mục đích khác nhau. |
| “Đóng loader/JAR sẽ unload class ngay.” | Class unloading tùy reachability/GC, không có thời điểm tức thì bảo đảm. |
| “Bỏ parent-first hoàn toàn sẽ làm plugin độc lập an toàn hơn.” | Có thể nhân bản API, gây ClassCastException/LinkageError và không tạo sandbox bảo mật. |
| “JPMS thay thế hoàn toàn class loader.” | Modules và loaders cùng tham gia tổ chức/kiểm tra runtime; không thay định danh cặp theo JVMS. |

## 18. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** Hai loader riêng `A` và `B` cùng đọc một tệp `Task.class` có binary name `app.Task`. Nếu cả hai trực tiếp gọi `defineClass` thành công, hai class có cùng identity không?

**Đáp án:** Không. Cặp `(app.Task, A)` và `(app.Task, B)` khác nhau, dù bytes giống hệt.

**Câu 2.** Loader plugin `P` delegate nạp `java.lang.String` cho bootstrap. Defining loader của String là ai?

**Đáp án:** Bootstrap loader theo JDK tiêu chuẩn. `P` có thể là initiating loader, không phải defining loader của String.

**Câu 3.** Vì sao `Class.getName()` không đủ chứng minh object cast được?

**Đáp án:** Nó không chứa defining loader identity. Phải xét runtime class và quan hệ assignability.

**Câu 4.** Binary name của member class `Outer.Inner` có thể là gì?

**Đáp án:** Thông thường `Outer$Inner` (kèm package tương ứng). Dấu chấm source giữa Outer và Inner không phải dấu chấm của binary name.

**Câu 5.** Hai custom loader cùng class và cùng parent có thể định nghĩa hai bản `Plugin` khác nhau không?

**Đáp án:** Có, nếu mỗi **loader instance** tự định nghĩa cùng binary name. Cùng parent không buộc phải chia sẻ mọi class.

**Câu 6.** Cùng tên package, hai class do hai defining loaders khác nhau tạo, có mặc nhiên cùng runtime package?

**Đáp án:** Không. Runtime package liên quan **package name và defining loader**.

**Câu 7.** Hai array types `Plugin[]` có thể khác identity khi component classes cùng binary name không?

**Đáp án:** Có. Nếu component classes có defining loaders khác nhau, array classes tương ứng cũng khác identity.

**Câu 8.** `int[].class.getClassLoader()` là custom loader của class đã viết `new int[3]`?

**Đáp án:** Không. Primitive array class do JVM tạo và gắn với bootstrap loader.

**Câu 9.** Nếu host và plugin cần dùng chung `api.Task`, thường nên nạp API như thế nào?

**Đáp án:** Để một loader chung (thường là parent/host loader) định nghĩa `api.Task`, plugin loaders delegate kiểu API đó. Implementation vẫn có thể được định nghĩa riêng.

**Câu 10.** `Class.forName(name, false, loader)` có làm loader đã truyền thành defining loader không?

**Đáp án:** Không bắt buộc. Loader có thể delegate; flag `false` nói về việc không yêu cầu initialization, không thay defining loader.

**Câu 11.** `ClassCastException` và `LinkageError` trong hệ nhiều loader có cùng nguyên nhân duy nhất không?

**Đáp án:** Không. Cast mismatch và loading/linking constraint failure là những vấn đề khác nhau, dù đôi khi cùng liên quan các kiểu trùng binary name nhưng khác loader.

**Câu 12.** Hai plugin dùng cùng class file nhưng đều được định nghĩa độc lập. Một plugin có thể gọi trực tiếp package-private member của plugin kia chỉ vì chuỗi package giống không?

**Đáp án:** Không thể mặc định như vậy. Runtime package có cả defining loader, và JVM còn kiểm tra quyền truy cập/module khi thích hợp.

## 19. Tổng kết

```text
                     Binary name
                         +
                Defining class loader
                         |
                         v
                Runtime class identity
                         |
              +----------+----------+
              |                     |
         Class checks           Linkage checks
      cast / instanceof      method / field / API
              |                     |
              +----------+----------+
                         |
              Chính sách class loading
             (delegation, isolation, APIs)
```

Điểm cần ghi nhớ:

1. **Không định danh Java runtime class chỉ bằng tên.** Cặp **(binary name, defining loader)** mới quyết định class/interface identity.
2. **Initiating loader khác defining loader.** Delegation có thể khiến nhiều loaders cùng sử dụng một định nghĩa runtime.
3. **Cùng bytecode không đồng nghĩa cùng kiểu.** Hai loaders độc lập có thể tạo hai class `Class` khác nhau và khiến cast thất bại.
4. **Array classes có quy tắc đặc biệt:** JVM trực tiếp tạo array class, còn defining loader của array class được xác định từ component type.
5. **Runtime package và liên kết cũng phụ thuộc loader.** Thiết kế plugin cần chia sẻ API contract phù hợp và cô lập implementation có kiểm soát.
6. **Debug phải nhìn cả name, Class object, loader và module khi cần.** Đây là cơ sở để giải quyết những lỗi type mismatch vốn nhìn bề ngoài rất khó hiểu.

### Tài liệu đặc tả và API chính thức

- [JVMS §5.3 — Creation and Loading (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): cặp binary name + defining loader, initiating/defining loaders, array classes.
- [JVMS §5.3.3 — Creating Array Classes](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.3): quy tắc array class/defining loader theo component type.
- [JVMS §5.3.4 — Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.4): những ràng buộc an toàn kiểu trong liên kết.
- [JVMS §4.2.1 — Binary Class and Interface Names](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2.1): dạng tên nhị phân của class/interface.
- [JLS §13.1 — The Form of a Binary (Java SE 25)](https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.1): binary names và liên kết nhị phân.
- [ClassLoader API — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, `defineClass`, delegation.
- [Class API — Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html): `getName`, `getClassLoader`, `isInstance`, `cast`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-004">Bộ nạp khởi xướng, bộ nạp định nghĩa và ràng buộc giữa các bộ nạp</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ nạp khởi xướng, bộ nạp định nghĩa và ràng buộc giữa các bộ nạp

## 1. Mục tiêu

Một lỗi khó hiểu trong ứng dụng Java dạng plugin là `demo.Plugin cannot be cast to demo.Plugin`: hai tên lớp nhìn giống hệt nhau nhưng JVM lại coi chúng là **hai kiểu khác nhau**. Lý do nằm ở cách JVM xác định lớp runtime: **tên nhị phân và bộ nạp đã định nghĩa lớp**. Ngoài ra, lớp có thể được yêu cầu tải qua một loader nhưng do **loader khác** định nghĩa sau khi ủy quyền.

Sau bài học này, bạn cần phân biệt chính xác:

- **Initiating loader** (bộ nạp khởi xướng) và **defining loader** (bộ nạp định nghĩa), theo **JVMS §5.3**.
- **Loading** (tạo lớp từ định nghĩa), **linking/resolution** (liên kết tham chiếu) và **initialization** (chạy khởi tạo `static`).
- **Class identity**: cặp (`binary name`, `defining loader`), không chỉ là tên hoặc nội dung của tệp `.class`.
- **Parent delegation** mặc định của `ClassLoader` và giới hạn của nhận định “mọi loader đều parent-first”.
- **Loading constraints** giữa các bộ nạp, ký hiệu `N^L1 = N^L2`, nhằm bảo đảm an toàn kiểu cho các lời gọi và trường xuyên ranh giới class loader.
- Khác biệt giữa **`ClassCastException`**, **`LinkageError` do loader constraint violation**, `ClassNotFoundException` và một số lỗi linking khác.

Ví dụ thực hành dùng Java **17+**, một class plugin, hai `URLClassLoader` riêng, và xác nhận class names giống nhau không bảo đảm `Class<?>` identity giống nhau.

## 2. Hai vai trò loader trong JVMS

Đặc tả **JVMS §5.3, Creation and Loading** giải thích: với class/interface không phải array, một class loader `L` có thể trực tiếp tìm biểu diễn bytecode và đề nghị JVM **define** class, hoặc gián tiếp **delegate** việc tải cho loader khác.

**Defining loader** của một class `C` là bộ nạp trực tiếp khiến JVM tạo định nghĩa runtime của `C`, điển hình bằng `ClassLoader.defineClass`. **Initiating loader** là loader mà JVM ghi nhận đã khởi xướng việc tải `C`, dù nó trực tiếp định nghĩa hay được trả về class từ quá trình ủy quyền.

~~~text
                Cần tạo class tên N
                        |
                 loader Child (Lc)
                        |
           +------------+------------+
           |                         |
     Tự find/define              Delegate sang Parent (Lp)
           |                         |
           v                         v
  Class C = (N, Lc)            Parent định nghĩa C
           |                         |
  Lc: defining + initiating          v
                              Class C = (N, Lp)
                                    |
                          Lp: defining + initiating
                          Lc: initiating
~~~

Đây là **hai nhánh có thể có**, không phải một lời gọi sẽ cùng đi cả hai. **Một runtime class có một defining loader** nhưng **có thể có nhiều initiating loaders**. Khi chuỗi ủy quyền dài, không được mặc định tất cả loader trung gian đều là initiating loader; JVMS có quy tắc ghi nhận cụ thể cho các loader tham gia.

| Tiêu chí | Initiating loader | Defining loader |
| --- | --- | --- |
| Mô tả | Loader đứng ra khởi xướng tải `C`, trực tiếp hoặc gián tiếp | Loader thực sự định nghĩa `C` trong VM |
| Có thể nhiều loader cho cùng `C`? | Có | Không, một class runtime có một defining loader |
| Có cần tự đọc bytecode `.class`? | Không bắt buộc; có thể delegate | Phải chịu trách nhiệm trực tiếp cho quá trình định nghĩa class (bootstrap có cơ chế riêng) |
| Quyết định identity của `C`? | Không tự nó quyết định | **Có**, cùng với binary name |
| `Class.getClassLoader()` quan sát được? | Không cho danh sách các initiating loaders | Trả defining loader, hoặc `null` với bootstrap |

### Bootstrap và một yêu cầu tải qua loader con

Trên JDK thông thường, `java.lang.String` do **bootstrap class loader** định nghĩa. Giả sử loader con nhận yêu cầu:

~~~java
Class<?> c = childLoader.loadClass("java.lang.String");
System.out.println(c == String.class);
System.out.println(c.getClassLoader() == null);
~~~

Đây là đoạn **minh họa cần đặt trong method đã khai báo biến `childLoader`**. Với cơ chế delegation thông thường, hai dòng in `true`. `childLoader` có thể là initiating loader nhưng **không phải defining loader** của String.

Giá trị `null` do `String.class.getClassLoader()` trả về là biểu diễn API cho **bootstrap**, không có nghĩa String “không được bộ nạp nào tải”. Tên class và defining loader cần được quan sát cùng nhau khi chẩn đoán.

## 3. Initiating loader không tự nhân đôi runtime class identity

Đối với class/interface thông thường, JVM định danh một kiểu bằng **(`binary name`, `defining loader`)**. Hai loader cùng yêu cầu `api.Service` có thể trả đúng **một** `Class<?>` khi đều ủy quyền tới một parent; trong trường hợp đó, **initiating loaders khác nhau không làm type identity khác đi**.

Ngược lại, nếu mỗi plugin loader tự định nghĩa `demo.Plugin` từ cùng bytes, chúng tạo hai runtime types riêng. `classA.getName().equals(classB.getName())` có thể `true` trong khi `classA == classB` là `false`, và `classA.cast(objectB)` có thể ném `ClassCastException`.

**Điểm nối sang mục 6:** hai type trùng binary name có thể cùng tồn tại hợp lệ. Chỉ khi một liên kết qua field/method descriptor **đòi thống nhất ý nghĩa kiểu** thì JVM mới áp loading constraints, không phải cứ có hai defining loaders là lỗi.

## 4. Delegation là đường đi tới defining loader, không phải identity

Cơ chế `ClassLoader.loadClass(name, resolve)` mặc định theo hướng **findLoadedClass → parent/bootstrap → findClass → resolveClass nếu yêu cầu**. Parent-first là hành vi mặc định hữu ích để chia sẻ API; custom loaders có thể chủ động dùng child-first cho implementation classes khi vẫn tuân thủ các ràng buộc JVM.

Khi debug, tách ba câu hỏi: (1) **ai được gọi** `loadClass`? (2) **ai thật sự** `defineClass`? (3) **ai sở hữu API type** nằm trong method/field descriptor qua hai loaders? `Class.getClassLoader()` phản ánh defining loader (bootstrap được biểu diễn `null`), **không** trả danh sách initiating loaders.

Những lần `loadClass` hoặc `Class.forName(name, false, loader)` không tự yêu cầu chạy `<clinit>`; khởi tạo lớp là vấn đề riêng. Mục này chỉ đặt ngữ cảnh để đi sâu vào **constraint hình thức** tại mục 6, thay vì lặp lại toàn bộ thuật toán và API của bài về bootstrap/platform/application delegation.

## 5. Thực hành Java 17+: hai `URLClassLoader` cùng tải một class

Thực hành này đủ để chạy trên JDK 17+ **không dùng thư viện ngoài**. Nó chủ động giữ tệp plugin trong thư mục riêng để hai loader có thể định nghĩa hai bản độc lập.

### 5.1. Cấu trúc files

~~~text
loader-lab/
  demo/
    Plugin.java
  LoaderLab.java
~~~

Nội dung `demo/Plugin.java`:

~~~java
package demo;

public class Plugin {
    public String message() {
        return "plugin";
    }
}
~~~

Nội dung `LoaderLab.java` tại thư mục gốc `loader-lab`:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderLab {
    public static void main(String[] args) throws Exception {
        Path compiled = Path.of("plugin-classes").toAbsolutePath();
        URL[] urls = { compiled.toUri().toURL() };

        // Platform loader KHÔNG phải application loader của LoaderLab.
        // Vì vậy demo.Plugin không bị app classpath chiếm trước.
        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader loaderA = new URLClassLoader(urls, parent);
             URLClassLoader loaderB = new URLClassLoader(urls, parent)) {

            Class<?> classA = loaderA.loadClass("demo.Plugin");
            Class<?> classB = loaderB.loadClass("demo.Plugin");

            Object objectA = classA.getDeclaredConstructor().newInstance();
            Object objectB = classB.getDeclaredConstructor().newInstance();

            System.out.println("same name="
                    + classA.getName().equals(classB.getName()));
            System.out.println("same class=" + (classA == classB));
            System.out.println("loader A defines="
                    + (classA.getClassLoader() == loaderA));
            System.out.println("loader B defines="
                    + (classB.getClassLoader() == loaderB));
            System.out.println("cross-instance="
                    + classA.isInstance(objectB));
            System.out.println("same within A="
                    + (objectA.getClass() == classA));

            try {
                classA.cast(objectB);
                System.out.println("unexpected cast success");
            } catch (ClassCastException expected) {
                System.out.println("cast=ClassCastException");
            }

            Class<?> stringViaA = loaderA.loadClass("java.lang.String");
            System.out.println("delegated String="
                    + (stringViaA == String.class));
            System.out.println("String bootstrap="
                    + (String.class.getClassLoader() == null));
        }
    }
}
~~~

### 5.2. Biên dịch và chạy

Tại thư mục `loader-lab` trong terminal:

~~~bash
mkdir plugin-classes
javac -d plugin-classes demo/Plugin.java
javac LoaderLab.java
java LoaderLab
~~~

Với PowerShell cũng có thể tạo thư mục trước bằng `New-Item -ItemType Directory -Force plugin-classes`. Đừng thêm `plugin-classes` vào parent **platform** loader path; ví dụ cần mỗi `URLClassLoader` tự tìm được `demo.Plugin` từ URL đầu vào. Nếu thư mục đã tồn tại, bỏ bước tạo lại.

**Stdout chính xác khi chạy trong bố cục chuẩn:**

~~~text
same name=true
same class=false
loader A defines=true
loader B defines=true
cross-instance=false
same within A=true
cast=ClassCastException
delegated String=true
String bootstrap=true
~~~

### 5.3. Vì sao từng dòng đúng?

- **`same name=true`**: tên nhị phân của hai class là `demo.Plugin`.
- **`same class=false`**: LoaderA/LoaderB là hai defining loaders riêng, nên runtime class identity khác nhau.
- **`loader A defines=true`** và **`loader B defines=true`**: hai class thực sự do đúng loader riêng định nghĩa.
- **`cross-instance=false`**: object của classB không phải instance của classA.
- **`same within A=true`**: objectA được tạo từ classA.
- **`cast=ClassCastException`**: phép cast dựa trên runtime types, không dựa trên tên hoặc bytecode giống nhau.
- **`delegated String=true`**: class String trả về khi loaderA yêu cầu vẫn là `String.class` của bootstrap.
- **`String bootstrap=true`**: Java API đại diện bootstrap loader bằng `null`.

**Quan trọng:** demo tạo **`ClassCastException` khi cố cast**, **không chủ ý tạo loading constraint violation**. Hai class trùng tên nhưng tách biệt có thể tồn tại hợp lệ. `LinkageError` liên quan loading constraints xảy ra khi **một quy tắc liên kết cụ thể** đòi hai cách hiểu tên kiểu phải khớp mà không thể khớp.

### 5.4. Nếu muốn dùng một parent chung?

Thử thay đổi kiến trúc để cả hai child ủy quyền cho **một parent đã định nghĩa `demo.Plugin`**. Khi parent trả class chung, có thể:

~~~text
classA == classB  -> true
classA.getClassLoader() == parent -> true
~~~

Đây là **kịch bản so sánh**, không phải lời khuyên tùy tiện sửa ví dụ bằng cách đổi đúng một tham số: parent cần được thiết lập có khả năng tải Plugin và việc ủy quyền phải thực sự parent-first. Mục tiêu là thấy **initiating loader có thể khác defining loader**.

## 6. Loading constraints: vì sao JVM phải ràng buộc loader với nhau?

Cho hai class đã được định nghĩa bởi các loader khác nhau, khi lớp này tham chiếu method/field của lớp kia, JVM cần thống nhất ý nghĩa của các **reference types xuất hiện trong descriptors**. Nếu không, một kiểu có tên giống nhau nhưng identity khác nhau có thể phá vỡ type safety khi chuyển giá trị qua biên giới class.

**JVMS §5.3.4** biểu diễn ràng buộc bằng dạng:

~~~text
N^L1 = N^L2

Tên class/interface N, khi được tải qua loader L1 hoặc L2,
phải cùng chỉ tới MỘT runtime class/interface.
~~~

Đây là **ràng buộc kết quả tải của một tên kiểu `N`**, không phải ràng buộc `L1 == L2`. Nếu L1 và L2 delegate tên `N` về cùng một defining loader chung, constraint được đáp ứng.

### Ví dụ phương thức đi qua API boundary

Giả sử hệ thống host định nghĩa contract:

~~~java
package api;

public interface Service {
    Message convert(Message input);
}
~~~

và `api.Message` là một class độc lập, có runtime identity do **shared host/API loader** định nghĩa. Plugin triển khai:

~~~java
package plugin;

public final class ServiceImpl implements api.Service {
    @Override
    public api.Message convert(api.Message input) {
        return input;
    }
}
~~~

**Hai đoạn trên là mã minh họa trong các file khác nhau và còn cần class `api.Message` cùng cấu hình build/classpath thích hợp; không phải một file runnable độc lập.**

Trong method descriptor `(Lapi/Message;)Lapi/Message;`, cả tham số và kết quả đều nhắc tới tên `api/Message`. Khi JVM kiểm tra khả năng override và resolution xuyên loaders, nó có thể áp loading constraint:

~~~text
api/Message ^ L_host  =  api/Message ^ L_plugin
~~~

Nếu host loader và plugin loader đều dẫn tới cùng `api.Message` do parent định nghĩa, hai vế thống nhất. Nếu plugin loader cố định nghĩa `api.Message` riêng khiến nó khác host `api.Message`, ràng buộc liên kết có thể **không thể thỏa**, gây **`LinkageError` do loader constraint violation**.

~~~text
         HOST                             PLUGIN
      loader H                           loader P
         |                                  |
  api.Service.convert(Message)   ServiceImpl.convert(Message)
         |                                  |
         +---- type N = api/Message --------+
              constraint N^H = N^P

Trường hợp đúng:
  H và P tải api.Message thành cùng runtime class từ shared loader.

Trường hợp vi phạm:
  H và P đã ghi nhận hai runtime api.Message khác nhau,
  nhưng liên kết lại yêu cầu đồng nhất type ở chữ ký.
~~~

**Không** phải cứ hai loaders có class tên trùng là VM ném lỗi. Loader constraints được áp dụng ở những mối quan hệ **liên kết cụ thể**. Hai class `demo.Plugin` riêng được dùng nội bộ, không có ràng buộc đồng nhất giữa chúng, có thể cùng tồn tại bình thường.

### Các thời điểm JVM áp loading constraints

Theo JVMS, những nơi quan trọng gồm:

1. **Preparation (§5.4.2)** của class/interface: khi method instance có thể override method của superclass/superinterface, cần bảo đảm những tên class/interface trong method descriptor không mang nghĩa mâu thuẫn giữa defining loaders.
2. **Field resolution (§5.4.3.2)**: các reference type names trong field descriptor phải được hiểu nhất quán giữa referencing class và field owner khi VM áp constraint.
3. **Method/interface method resolution (`5.4.3.3–`5.4.3.4)**: các names trong method descriptors có thể bị ràng buộc xuyên loader.
4. **Khi ghi nhận initiating loader**: VM phải kiểm tra ngay các constraints đã có; nếu bị vi phạm, bỏ thao tác ghi nhận và ném `LinkageError` theo đặc tả.

**Resolution có thể diễn ra sớm hoặc trì hoãn** theo quy tắc JVM, do đó thời điểm ném lỗi có thể không trùng dòng source nơi bạn “tạo ra” cấu hình loader. Không nên kết luận “đã chạy được `main` thì mọi method signatures xuyên plugin đã link thành công”.

### Không nhầm loading constraints với dynamic dispatch

- **Resolution** xác định kết quả symbolic reference trong constant pool.
- **Loading constraint** giữ ý nghĩa các kiểu trong descriptor nhất quán qua loaders khi liên kết.
- **Dynamic dispatch** quyết định method implementation nào chạy khi gọi ảo theo runtime object type.
- **`invokedynamic`** có cơ chế call-site/bootstrap riêng.

Các khái niệm cùng tham gia execution/linking nhưng **không thể dùng thay thế cho nhau**.

## 7. So sánh lỗi loading, linking và cast

| Triệu chứng | Loại | Ý nghĩa và hướng kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` | Checked exception | Lời gọi tải class explicit không tìm thấy tên; xem loader path, binary name, JAR/module |
| `NoClassDefFoundError` | `LinkageError` subclass | Class được tham chiếu trong execution/linking không tìm/khởi tạo được trong tình huống tương ứng; xem cause và lỗi trước |
| `ClassCastException` | Runtime exception | Object không tương thích runtime type đích; kiểm tra cả `getName` lẫn `getClassLoader` |
| `LinkageError: loader constraint violation` | `LinkageError` | Type names trong descriptors qua loader phải đồng nhất nhưng lại không thể đồng nhất |
| Duplicate class definition | `LinkageError` trong tình huống thích hợp | Cùng một defining loader cố tạo lại class có tên đã định nghĩa |
| `NoSuchMethodError` hoặc `NoSuchFieldError` | `LinkageError` subclass | Tham chiếu symbolic không phù hợp API thực được nạp; kiểm tra dependency versions |
| `IllegalAccessError` | `LinkageError` subclass | Runtime access check không cho phép; xem package identity, visibility, module exports |
| `ExceptionInInitializerError` | `LinkageError` subclass | Initialization `static` thất bại; tìm cause thay vì mặc định lỗi delegation |

**Quy tắc chẩn đoán:** không gọi mọi `LinkageError` là loader constraint violation. Ngoài ra, `ClassCastException` trong ví dụ LoaderLab **khác** `LinkageError`: tải hai class thành công, chỉ đến lúc cast mới thấy không tương thích.

### Lỗi do duplicate API jar

Một lỗi phổ biến: host đã có `api.Message`, nhưng plugin JAR cũng bundle riêng `api.Message` và custom loader là **child-first**. Việc trùng tên có thể không gây lỗi ngay. Khi plugin implement shared interface `api.Service` và method descriptor liên quan tới `api.Message`, JVM có thể cần buộc hai loader đồng nhất type đó. Cách khắc phục là **chia sẻ API definition qua loader chung**, không cố ép cast giữa hai class khác identity.

Trong Maven/Gradle hoặc hệ build tương đương, dependency chỉ dành cho compile nhưng do host cung cấp tại runtime có thể được đánh dấu `provided` hoặc `compileOnly` theo mục đích. Tuy nhiên vẫn cần kiểm tra packaging thực tế và cơ chế loader của framework, không suy rằng chỉ đổi dependency scope là tự sửa mọi lỗi.

## 8. Array classes và bootstrap: một ngoại lệ cần biết

Array classes **được JVM tạo trực tiếp** theo **JVMS §5.3.3**, không có một file `[I.class` để người viết custom loader tự tìm qua `findClass` như class Java thông thường.

- Nếu component type là **reference type**, array class có defining loader của component type.
- Nếu component là **primitive type**, array class có bootstrap defining loader.
- JVM ghi nhận initiating loader cho array class theo thủ tục của JVMS.

Ví dụ chạy được Java 17+:

~~~java
public class ArrayLoaderCheck {
    public static void main(String[] args) {
        System.out.println("String[] bootstrap="
                + (String[].class.getClassLoader() == null));
        System.out.println("int[] bootstrap="
                + (int[].class.getClassLoader() == null));
    }
}
~~~

~~~bash
javac ArrayLoaderCheck.java
java ArrayLoaderCheck
~~~

**Stdout:**

~~~text
String[] bootstrap=true
int[] bootstrap=true
~~~

Nếu `demo.Plugin` được LoaderA và LoaderB định nghĩa độc lập, các array classes `demo.Plugin[]` tương ứng cũng **khác identity** do component types khác nhau. Đừng lẫn array class identity với việc hai mảng chỉ có các phần tử cùng tên class.

## 9. Thiết kế plugin và shared API hợp lý

Một sơ đồ hữu ích:

~~~text
Host / Shared API loader
   |
   +-- api.Service
   +-- api.Message
   +-- api.PluginContext
   |
   +-- Plugin A loader
   |      pluginA.ServiceImpl
   |      pluginA.InternalCache
   |      (api.* -> delegate shared loader)
   |
   +-- Plugin B loader
          pluginB.ServiceImpl
          pluginB.InternalCache
          (api.* -> delegate shared loader)
~~~

### Quy tắc thực hành

1. **Chia sẻ API types qua một defining loader chung.** Interfaces/DTOs/classes xuất hiện trong boundary method/field descriptors nên được quản lý nhất quán.
2. **Cô lập implementation classes** ở từng plugin loader khi cần. Class nội bộ có thể cùng tên ở hai plugin nếu không ảnh hưởng API boundaries.
3. **Không đóng gói trùng API JAR vào plugin** khi custom loader có thể tự định nghĩa lại API classes. Xem xét dependency scope và runtime packaging.
4. **Log actual defining loader**, không chỉ `Class.getName()`. `getModule()` và JAR source cũng hữu ích.
5. **Cẩn trọng hot reload**: thế hệ plugin cũ và mới có thể dùng hai loaders khác nhau; instances cũ không tự chuyển sang type mới.
6. **Giải phóng references** khi unload: host singleton, thread context class loader, static maps, threads hoặc callback có thể giữ loader cũ reachable.
7. **Không coi class loader là sandbox bảo mật tuyệt đối.** Việc nạp code không tin cậy đòi ranh giới bảo mật riêng; JVM không bảo đảm isolation ở mức process chỉ nhờ hai class loaders.

### Thread context class loader (TCCL)

`Thread.currentThread().getContextClassLoader()` cho biết **context class loader** của thread. Một số API/framework, ví dụ cơ chế tìm provider, có thể sử dụng nó. Nhưng TCCL **không thay đổi defining loader** của class đã được JVM tạo trước đó.

Một dòng debug:

~~~java
System.out.println(Thread.currentThread().getContextClassLoader());
~~~

không thay thế:

~~~java
System.out.println(obj.getClass().getClassLoader());
~~~

Đoạn thứ hai cần có `obj` không `null` trong context. Nếu TCCL giữ tham chiếu tới plugin loader cũ trên thread còn sống, đây cũng có thể là nguyên nhân loader không được thu hồi khi hot reload.

## 10. Debugging: cách truy vết class identity và loader constraints

Khi gặp lỗi có tên lớp lặp lại hoặc keyword `loader constraint violation`, nên điều tra theo trình tự:

1. **Xác định loại lỗi gốc** và cause chain: `ClassCastException`, `LinkageError`, `NoClassDefFoundError`, `IllegalAccessError`?
2. **Ghi tên class và defining loaders** của cả hai bên: `Class.getName()`, `Class.getClassLoader()`, `Class.getModule()`; so loader objects bằng `==`.
3. **Đọc field/method descriptors** của API boundaries bằng `javap -v -p` hoặc `javap -s -p`. Chú ý DTO/shared interface types trong dạng `Lapi/Message;`.
4. **Kiểm tra JAR trùng lặp**: một type name có trong cả host classpath và plugin JAR không?
5. **Kiểm tra delegation policy** thực tế, nhất là classloader child-first và package allow/deny lists.
6. **Kiểm tra runtime package và module boundaries** nếu là lỗi truy cập, không chỉ import trong source.
7. **Khảo sát thread context loader** và references cũ khi hot reload.
8. **Sửa shared API ownership**, đóng gói dependency hoặc loader graph có kiểm chứng, rồi chạy lại integration tests.

Các lệnh:

~~~bash
java -version
jar tf plugin.jar
javap -s -p -classpath plugin.jar plugin.ServiceImpl
javap -v -p -classpath plugin.jar plugin.ServiceImpl
java -Xlog:class+load=info -jar host.jar
~~~

Đây là **mẫu thao tác**, thay tên JAR/class bằng file thực. `-Xlog:class+load=info` là logging trên **HotSpot hiện đại**, không bảo đảm mọi JVM hỗ trợ y hệt. Dòng log loading cho biết các lớp được load/nguồn class, nhưng để xác nhận defining loader object nên kết hợp thông tin runtime. Xem `java -Xlog:help` trên HotSpot tương ứng.

Đoạn method kiểm tra hai object (cần đặt trong một class, và cả hai đối số khác `null`):

~~~java
static void inspect(Object a, Object b) {
    Class<?> c1 = a.getClass();
    Class<?> c2 = b.getClass();

    System.out.println("name1=" + c1.getName());
    System.out.println("name2=" + c2.getName());
    System.out.println("same class=" + (c1 == c2));
    System.out.println("loader1=" + c1.getClassLoader());
    System.out.println("loader2=" + c2.getClassLoader());
    System.out.println("1 accepts 2=" + c1.isInstance(b));
}
~~~

**Không** chỉ so tên loader bằng `toString()`, vì các ID/hash có thể khác nhau giữa các lần chạy. Hãy dùng object identity khi so loader hoặc `Class<?>` thực tế.

## 11. Những nhầm lẫn cần tránh

| Hiểu nhầm | Phân biệt đúng |
| --- | --- |
| “Loader mà mình gọi `loadClass` chính là defining loader.” | Loader được gọi có thể **delegate**; `getClassLoader()` cho biết defining loader. |
| “Một class luôn chỉ có một initiating loader.” | Có thể có **nhiều** initiating loaders và một defining loader. |
| “Cùng fully qualified name nghĩa cùng Java class.” | Cần cùng **defining loader** và binary name. |
| “Cùng nội dung class bytes thì chắc chắn có thể cast qua loader.” | Khác defining loader có thể khác runtime class identity. |
| “Parent-first là luật cứng của mọi custom loader.” | Là default của `ClassLoader`, custom loader có thể có chính sách hợp lệ khác. |
| “Chỉ cần đổi tên package cho giống nhau là mở được package-private access.” | Runtime package và module rules vẫn quan trọng. |
| “Bất kỳ class trùng tên qua hai loaders sẽ ném `LinkageError`.” | Nhiều định nghĩa có thể cùng tồn tại; lỗi constraint cần quan hệ liên kết bắt buộc. |
| “`ClassCastException` và loader constraint violation là cùng lỗi.” | Một là cast runtime; một là vấn đề an toàn kiểu khi linking/preparation. |
| “Loading constraint buộc hai loader objects phải bằng nhau.” | Buộc **class được tải qua chúng** cho tên ràng buộc phải đồng nhất. |
| “Tải class tức chạy `static {}`.” | Loading khác initialization. |
| “Class loader hiện trong `getClassLoader()` là toàn bộ initiating loader history.” | API trả defining loader, không phải danh sách lịch sử. |
| “`Class.forName(name, false, loader)` đảm bảo không liên kết class.” | Nó tránh yêu cầu initialization; linking có thể xảy ra. |
| “Loader của `int[]` phải tìm bytecode `[I.class`.” | Array class được VM tạo trực tiếp. |
| “TCCL quyết định defining loader cho mọi class của thread.” | TCCL phục vụ lookup theo context cho một số API/framework; không sửa class identity đã tạo. |
| “Đóng `URLClassLoader` khiến mọi class và object nó từng định nghĩa biến mất ngay.” | Class unloading phụ thuộc reachability và GC; đóng loader không ép mọi object biến mất. |

## 12. Câu hỏi tự kiểm tra

**1.** `child.loadClass("java.lang.String")` trả `String.class`. Loader nào định nghĩa class?  
**Đáp án:** Bootstrap (trên JDK chuẩn). Child có thể là initiating loader nhưng không phải defining loader.

**2.** Vì sao hai `URLClassLoader` khác nhau đều đọc cùng bytes mà `classA != classB`?  
**Đáp án:** Runtime identity là cặp (`binary name`, `defining loader`), và defining loaders khác nhau.

**3.** `classA.getName().equals(classB.getName())` là `true` thì `classA.isInstance(objectB)` tự đúng không?  
**Đáp án:** Không. Kiểm tra kiểu dùng runtime identity, không chỉ tên.

**4.** Ràng buộc `api/Message^L1 = api/Message^L2` có ý nghĩa gì?  
**Đáp án:** Với tên `api/Message`, kết quả tải qua hai loaders phải chỉ cùng runtime class/interface, không yêu cầu loader objects là một.

**5.** Tại sao host/plugin có thể gặp loader constraint violation trong `Service.convert(Message)`?  
**Đáp án:** Nếu cùng type name `api.Message` trong descriptor lại được diễn giải thành hai classes khác nhau, JVM không thể bảo đảm hợp đồng method xuyên loader khi constraint yêu cầu đồng nhất.

**6.** Nếu hai class `demo.Plugin` chỉ được dùng riêng trong hai plugin không có ràng buộc giữa chúng, có buộc `LinkageError` không?  
**Đáp án:** Không. Chúng có thể cùng tồn tại hợp lệ.

**7.** Loader nào là defining loader khi code của nó gọi `defineClass` để tạo một class thành công?  
**Đáp án:** Chính loader đó.

**8.** `Class.getClassLoader()` có cung cấp danh sách tất cả initiating loaders không?  
**Đáp án:** Không. Nó phản ánh defining loader.

**9.** `Class.forName(name, false, loader)` có yêu cầu chạy static initializer không?  
**Đáp án:** Không chủ động yêu cầu initialization; class vẫn có thể được load/link.

**10.** `String[].class` có defining loader riêng do `URLClassLoader` đọc từ file `[Ljava/lang/String;.class` không?  
**Đáp án:** Không. Array class do VM tạo, defining loader gắn với component `String` theo quy tắc array classes.

## 13. Nguồn chính thức

- [JVMS Java SE 25 — §5.3 Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): định nghĩa initiating loader, defining loader và class identity.
- [JVMS Java SE 25 — §5.3.1–5.3.3](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.1): tải bằng bootstrap/user-defined loader và array classes.
- [JVMS Java SE 25 — §5.3.4 Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.4): `N^L1 = N^L2`, nơi bắt buộc kiểm tra và `LinkageError` khi vi phạm.
- [JVMS Java SE 25 — §5.4.2 Preparation](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.2): ràng buộc cho method overriding.
- [JVMS Java SE 25 — §5.4.3 Resolution](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3): field/method references xuyên loader.
- [JDK 25 — ClassLoader API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `findClass`, `defineClass`.
- [JDK 25 — URLClassLoader API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): tải classes/resources qua URLs và đóng loader.
- [JDK 25 — Class API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html): `getClassLoader`, `getName`, `isInstance`, `cast`, `forName`.
- [JLS Java SE 25 — §12.2 Loading](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2): loading classes/interfaces và tính nhất quán giữa class loader environments.

**Ghi nhớ cuối cùng:** Muốn biết “ai yêu cầu tải?” hãy xét **initiating loader**. Muốn biết “kiểu nào trong JVM?” phải xét **binary name + defining loader**. Muốn biết “hai loader có được dùng hai cách hiểu khác nhau về một kiểu trong API boundary không?” hãy kiểm tra **loading constraints**, thay vì đồng nhất mọi lỗi cast hoặc class loading với `LinkageError`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-005">Class path, module path và ảnh hưởng của module đến truy cập lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Class path, module path và ảnh hưởng của module đến truy cập lớp trong JVM

## Mục tiêu bài học

Từ Java 9, hệ thống module của Java Platform (*JPMS — Java Platform Module System*) bổ sung một tầng mô tả **module phụ thuộc vào module nào và package nào được phép truy cập**. Vì vậy, một tệp `.class` hoặc JAR tồn tại trên ổ đĩa chưa đủ để kết luận mã nguồn có thể import kiểu của nó, JVM có thể tìm thấy nó hoặc reflection có thể đọc được private member bên trong.

Sau bài học, bạn có thể:
- Phân biệt **class path**, **module path** và những cách tìm class/interface khi chạy Java.
- Hiểu **unnamed module**, **named module** (explicit và automatic) và vai trò của `module-info.java`/`module-info.class`.
- Giải thích các khái niệm **observability**, **module resolution**, **readability** và **accessibility** mà không đánh đồng chúng.
- Áp dụng `requires`, `exports`, `opens`, `requires transitive` và các tham số dòng lệnh liên quan.
- Biên dịch/chạy ví dụ Java 17+ bằng class path và module path; biết vì sao hai ví dụ dùng cách cấu hình khác nhau.
- Chẩn đoán lỗi “module not found”, “package is not visible”, `ClassNotFoundException`, `NoClassDefFoundError` và `InaccessibleObjectException`.
- Nhớ rằng định danh class runtime vẫn phụ thuộc **tên nhị phân và defining class loader**, không đơn thuần một path hoặc tên module.

## 1. Class path và module path giải quyết những vấn đề gì?

**Class path** là danh sách địa điểm JVM/công cụ Java tìm các lớp ứng dụng theo cơ chế truyền thống: thư mục chứa cấu trúc package và các JAR. Code ứng dụng được đặt trên class path của Java launcher thường thuộc **unnamed module** của class loader tương ứng.

**Module path** là danh sách địa điểm để hệ thống module phát hiện **module**. Một module thường là một JAR có `module-info.class` (*modular JAR*) hoặc một thư mục module đã biên dịch (*exploded module*). Các JAR thông thường không có module descriptor vẫn có thể trở thành **automatic modules** khi đặt trên module path, tùy quy tắc định danh.

Hai khái niệm này không thay thế nhau hoàn toàn. Một chương trình có thể dùng cả hai, nhưng **đặt JAR ở nơi khác có thể thay đổi cách JVM nhóm mã vào module và cách kiểm tra truy cập**.

| Tiêu chí | Class path | Module path |
| --- | --- | --- |
| Đầu vào thông dụng | Thư mục các `.class`, JAR | Exploded modules, modular JAR, một số JAR thường làm automatic modules |
| Module của mã đặt tại đó | Thường là **unnamed module** | **Named module** sau khi được phát hiện và resolve |
| Cấu hình ở `java` | `-cp`, `-classpath`, `--class-path` | `-p`, `--module-path` |
| Cấu hình ở `javac` | `-cp` / `--class-path` | `--module-path` |
| Quan hệ phụ thuộc | Không khai báo dependency graph bằng `module-info` | `requires` và quá trình module resolution/readability |
| Kiểm soát package công khai | Chủ yếu dựa access modifier Java | Thêm **`exports`** và **`opens`**, tùy module đọc được |
| Chạy main | `java -cp out pkg.Main` | `java --module-path mods -m name/pkg.Main` |
| Vấn đề thường gặp | Sai classpath, thiếu JAR, bản phụ thuộc trùng | Module chưa resolve, không đọc được module, package không export/open, split package |

**Không được nhầm:**
- Class path **không phải** package name. Package `com.example.api` tương ứng đường dẫn `com/example/api` **bên dưới root classpath**, không phải nguyên root phải trỏ vào thư mục cuối `api`.
- Module path **không phải** đường dẫn trực tiếp đến mọi class trong module. Nó chứa nơi có thể tìm module, còn class được xác định thông qua module và class loader.
- Đưa một file JAR vào module path **không bảo đảm module đó đã được resolve**. Việc resolution còn phụ thuộc root modules, `requires` và tùy chọn launcher.
- Module path **không phải giải pháp tự động khắc phục ClassNotFoundException**. Việc tìm/đọc/truy cập lớp là nhiều bước khác nhau.

## 2. Định danh class ở runtime không chỉ là tên hiển thị

Trong mã nguồn Java, ta viết `com.example.api.Service`; JVM dùng *binary name* và class loader để xác định một class/interface runtime. Theo mô hình JVMS, class identity dựa trên **cặp (binary name, defining class loader)**.

Ví dụ:

~~~text
Tên nguồn / binary:   com.example.api.Service
JVM internal name:    com/example/api/Service
Class file:           com/example/api/Service.class

Class loader L1 defines Service -> class identity A
Class loader L2 defines Service -> class identity B
~~~

Hai class cùng tên nhưng do **hai defining class loader khác nhau** định nghĩa có thể là **hai kiểu khác nhau**: cast từ A sang B có thể thất bại. Ngược lại, hai đường dẫn chứa bản sao cùng tên không chứng minh rằng JVM sẽ nạp cả hai: chiến lược delegation, classpath search order, module graph và custom loaders quyết định class nào được tìm/định nghĩa.

Module là **một tầng tổ chức và kiểm soát truy cập** bổ sung. Không thể giản lược “class identity = module name + class name”; class loader vẫn là nhân tố cốt lõi. Để quan sát thực tế, Java cung cấp:
- `SomeType.class.getClassLoader()`: defining loader, có thể trả `null` đối với lớp do bootstrap loader định nghĩa.
- `SomeType.class.getModule()`: module sở hữu class runtime.
- `SomeType.class.getModule().isNamed()`: phân biệt named và unnamed module.
- `SomeType.class.getModule().getName()`: tên module nếu là named; có thể trả `null` với unnamed module.

Một module named đã được resolve thường gắn với class loader và module layer thích hợp; loader tùy triển khai có thể định nghĩa nhiều module. **Module layers** cho phép ứng dụng tạo thêm module configuration riêng, nhưng chúng không tự thay đổi quy tắc class identity nêu trên.

## 3. Ví dụ class path: ứng dụng không dùng module descriptor

Tạo tệp **`ClassPathDemo.java`** ở thư mục đang làm việc:

~~~java
public class ClassPathDemo {
    public static void main(String[] args) {
        Module module = ClassPathDemo.class.getModule();

        System.out.println("named=" + module.isNamed());
        System.out.println("moduleName=" + module.getName());
        System.out.println("message=classpath works");
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac --release 17 -d out ClassPathDemo.java
java -cp out ClassPathDemo
~~~

**Stdout chính xác trong cấu hình classpath này:**

~~~text
named=false
moduleName=null
message=classpath works
~~~

Giải thích:
1. `javac -d out` đặt `ClassPathDemo.class` vào thư mục `out`.
2. `java -cp out ClassPathDemo` dùng `out` như **classpath root**.
3. Lớp ứng dụng ở unnamed module, do đó `isNamed() == false` và `getName() == null`.
4. Không cần `module-info.java` khi chạy ví dụ trên.

Với mã trong package, ví dụ `package demo; public class Main { ... }`, file sau biên dịch nằm trong cấu trúc `out/demo/Main.class` và launcher phải dùng **tên đủ package**:

~~~bash
java -cp out demo.Main
~~~

**Không chạy** `java -cp out/demo Main` như cách cấu hình chuẩn để sử dụng một class có package `demo`; Java launcher tìm theo binary name với root classpath phù hợp. Ví dụ package ở trên là **đoạn mô tả**, không phải thêm một file đã tồn tại từ chương trình độc lập trước đó.

### Cách viết nhiều phần tử classpath

Hình thức:

~~~text
java -cp <classpath> pkg.Main
~~~

Trong đó phần tử classpath có thể là thư mục hoặc JAR, phân cách:
- Trên **Windows**: dấu chấm phẩy `;`.
- Trên **Linux/macOS**: dấu hai chấm `:`.

Ví dụ minh họa (giả sử các đường dẫn thật sự tồn tại):

~~~text
Windows:
java -cp "out;libs/api.jar;libs/impl.jar" demo.Main

Linux/macOS:
java -cp "out:libs/api.jar:libs/impl.jar" demo.Main
~~~

Cú pháp `libs/*` ở classpath cho phép Java launcher mở rộng thành các JAR trực tiếp trong thư mục đó theo quy tắc wildcard; nó **không đồng nghĩa quét đệ quy toàn bộ thư mục con**. Nên quote đường dẫn khi có dấu cách hoặc ký tự đặc biệt. Cách tổ chức build trong Maven/Gradle có thể thiết lập classpath hộ bạn, nhưng JVM rốt cuộc vẫn cần một tập đường dẫn đúng khi khởi động.

### Classpath order và phụ thuộc trùng

Nếu cùng binary name xuất hiện nhiều lần trong classpath, thứ tự tìm kiếm và hành vi loader có thể khiến **một bản được nạp thay vì bản còn lại**. Việc một file có mặt không bảo đảm phiên bản đó được dùng. Khi điều tra:
- Ghi lại toàn bộ lệnh chạy JVM và `java.class.path` khi cần.
- Kiểm tra classpath/module path thực tế chứ không chỉ dependency declaration.
- Dùng logging class loading của JVM theo cấu hình thích hợp (ví dụ `-Xlog:class+load=info` của HotSpot) để hỗ trợ phân tích lớp được tải từ đâu.
- Không dựa vào thứ tự wildcard JAR để chọn bản dependency mong muốn; xử lý xung đột trong build/deployment.

## 4. JPMS: named module, module descriptor và module graph

Một **explicit named module** có khai báo trong **`module-info.java`** và được biên dịch thành `module-info.class`. Khai báo module thể hiện **phụ thuộc trực tiếp**, **package được export** và **package mở cho reflection sâu**, cùng cấu hình dịch vụ khi cần.

Ví dụ mô tả (đoạn code module descriptor):

~~~java
module com.acme.app {
    requires com.acme.lib;
}
~~~

Một module thư viện:

~~~java
module com.acme.lib {
    exports com.acme.lib.api;
}
~~~

Ý nghĩa:
- Module `com.acme.app` khai báo **`requires com.acme.lib`** để có quan hệ đọc giữa các module.
- Module `com.acme.lib` chỉ export **package `com.acme.lib.api`** theo khai báo này. Các package khác dù chứa `public class` vẫn không tự được API truy cập trực tiếp bởi code ở module khác.
- Mọi named module có **readability tới `java.base`** theo quy tắc ngầm định; không cần ghi `requires java.base;` cho phần lớn ứng dụng.

Không phải một thư viện “đã nằm trên module path” thì mọi named module khác đương nhiên đọc được thư viện đó. Có bước **module resolution** hình thành configuration từ các **root modules** và các ràng buộc phụ thuộc `requires`, sau đó hệ thống áp dụng readability và exports để xét truy cập.

### Từ path đến một kiểu có thể dùng: bốn điều kiện

Để mã trong named module `A` dùng bình thường một kiểu `public` ở package `p` của named module `B`:

1. **Observable**: module `B` phải được công cụ/hệ thống nhận diện trên module path, system modules hoặc nguồn thích hợp.
2. **Resolved**: module `B` phải có mặt trong module graph được giải quyết cho ứng dụng.
3. **Readable**: module `A` phải đọc được `B` (thường qua `requires B;` hoặc quan hệ hợp lệ khác).
4. **Accessible**: `B` phải **export** package `p` đến `A`, và class/member còn phải thỏa access modifier Java như `public`/`protected`.

Các điều kiện này **không đồng nghĩa**. Một JAR có mặt trên module path có thể observable nhưng chưa resolved; một module đã resolved có thể không readable từ `A`; một module đã readable vẫn có thể không export package chứa kiểu.

## 5. Ví dụ Java 17+ chạy bằng module path: hai module thực

Cấu trúc thư mục mong muốn:

~~~text
src/
  com.acme.lib/
    module-info.java
    com/acme/lib/api/Greeter.java
  com.acme.app/
    module-info.java
    com/acme/app/Main.java
~~~

Tệp **`src/com.acme.lib/module-info.java`**:

~~~java
module com.acme.lib {
    exports com.acme.lib.api;
}
~~~

Tệp **`src/com.acme.lib/com/acme/lib/api/Greeter.java`**:

~~~java
package com.acme.lib.api;

public final class Greeter {
    private Greeter() {
    }

    public static String hello() {
        return "Hello from library";
    }
}
~~~

Tệp **`src/com.acme.app/module-info.java`**:

~~~java
module com.acme.app {
    requires com.acme.lib;
}
~~~

Tệp **`src/com.acme.app/com/acme/app/Main.java`**:

~~~java
package com.acme.app;

import com.acme.lib.api.Greeter;

public class Main {
    public static void main(String[] args) {
        System.out.println(Greeter.hello());
        System.out.println("module=" + Main.class.getModule().getName());
    }
}
~~~

Từ thư mục chứa `src`, biên dịch bằng **JDK 17+**:

~~~bash
javac --release 17 --module-source-path src -d mods -m com.acme.lib,com.acme.app
java --module-path mods -m com.acme.app/com.acme.app.Main
~~~

**Stdout chính xác:**

~~~text
Hello from library
module=com.acme.app
~~~

Vì sao các lệnh hoạt động?
1. `--module-source-path src` khai báo root của **cấu trúc mã nguồn nhiều module**, mỗi thư mục module có tên module và chứa `module-info.java`.
2. `-m com.acme.lib,com.acme.app` ở lệnh **`javac`** chỉ rõ module nguồn cần biên dịch (khác ngữ cảnh `-m` của `java`).
3. `-d mods` tạo các thư mục exploded module như `mods/com.acme.lib`, `mods/com.acme.app`.
4. `java --module-path mods -m com.acme.app/com.acme.app.Main` lấy `com.acme.app` làm root module; module graph resolve `com.acme.lib` vì khai báo `requires`; launcher chạy main class.
5. `Greeter` được export bởi thư viện, nên import và gọi từ module `com.acme.app` là hợp lệ.

**Chỉ có một kết quả mong đợi với ví dụ đầy đủ này**; nếu tạo đúng đường dẫn và sử dụng JDK hỗ trợ `--release 17`, chương trình không cần JAR bên ngoài.

### Điều gì xảy ra nếu bỏ `exports`?

Thay descriptor thư viện thành:

~~~java
module com.acme.lib {
    // Không exports com.acme.lib.api
}
~~~

Đây là **phương án minh họa**, không được áp dụng nếu muốn chạy bản ví dụ thành công ở trên. `Greeter` vẫn là một **public class**, và `com.acme.app` vẫn khai báo `requires com.acme.lib`. Tuy nhiên package chứa `Greeter` **không được export** sang module ứng dụng, nên compiler có thể báo package **không visible** / không được export. Thay lại `exports com.acme.lib.api;` để biên dịch được.

Điều này minh họa chính xác khác biệt giữa **access modifier `public`** và **JPMS package exports**. Chỉ có cả hai điều kiện phù hợp mới đủ cho truy cập thông thường từ module khác.

## 6. Readability: `requires` và `requires transitive`

**Readability** là quan hệ ở cấp module: module này có thể đọc module kia theo module graph. Một explicit named module thông thường khai báo phụ thuộc bằng `requires`.

Ví dụ:

~~~text
module A requires B
module B requires C
~~~

Không được suy luận từ hai dòng trên rằng **A chắc chắn có quyền đọc trực tiếp C**. Nếu module B muốn các module phụ thuộc vào B cũng đọc được C theo tính chất transitive, B có thể khai báo:

~~~java
module com.acme.facade {
    requires transitive com.acme.api;
}
~~~

Nếu `com.acme.app` có `requires com.acme.facade;`, quan hệ transitive đó khiến ứng dụng đọc được `com.acme.api` theo quy tắc module readability. **Dù vậy, package muốn được dùng vẫn phải export thích hợp**, chẳng hạn `com.acme.api` phải export package chứa kiểu API.

### Một số khai báo quan trọng

| Cú pháp | Vai trò |
| --- | --- |
| `requires other.module;` | Khai báo module hiện tại phụ thuộc vào module kia; module graph cho phép đọc nó. |
| `requires transitive other.module;` | Module nào đọc module hiện tại theo quan hệ tương ứng cũng được thiết lập readability tới module kia theo quy tắc transitivity. |
| `requires static other.module;` | Biểu thị phụ thuộc cần thiết lúc compile, có thể vắng lúc runtime nếu code không thực sự cần dùng đến; cần thiết kế optional dependency cẩn thận. |
| `exports com.acme.api;` | Export package cho các module đọc module này. |
| `exports com.acme.internal to com.acme.friend;` | **Qualified exports:** chỉ export package cho các module được nêu. |
| `opens com.acme.model;` | Mở package cho reflective access sâu ở runtime, không tự tạo quyền import bình thường ở compile time. |
| `opens com.acme.model to com.acme.json;` | **Qualified opens:** chỉ mở package cho module được nêu. |
| `open module com.acme.framework { ... }` | Mở tất cả package của module cho reflection sâu, nhưng không export tất cả package cho ordinary compile-time access. |

Một số quy tắc phụ thuộc đặc thù như `requires static transitive` cũng được JPMS hỗ trợ, nhưng hãy xác định rõ ý đồ API và optional dependency trước khi dùng.

**Không lạm dụng `requires transitive`**: nó thể hiện phụ thuộc cấu thành API của module, không chỉ là một “mẹo” để code chỗ khác thấy thêm class. Với phụ thuộc implementation thuần nội bộ, `requires` thông thường thường rõ ràng hơn.

## 7. Exports khác Opens: truy cập bình thường và reflection

Đây là điểm cốt lõi về **ảnh hưởng của module đến truy cập lớp**.

### Exports

`exports com.acme.api;` giúp code ở module khác **đọc được module hiện tại** truy cập công khai thông thường đến các kiểu `public` trong package đó, kèm quy tắc modifiers của Java. **Exports không tự mở private fields** cho reflection sâu.

Ví dụ một package được export:

~~~java
module com.acme.model {
    exports com.acme.model.api;
}
~~~

Code ở module đọc `com.acme.model` có thể dùng `import com.acme.model.api.Customer;` nếu class/members công khai và class tồn tại. Nhưng reflection với `setAccessible(true)` đối với private field của `Customer` từ module khác **không được bảo đảm** chỉ nhờ exports.

### Opens

`opens com.acme.model.internal;` mở package cho **deep reflection** ở runtime, cho phép framework có quyền reflective access phù hợp đến private/package-private/protected members theo quy tắc API và các hạn chế khác. Tuy nhiên **opens không tương đương exports ở compile time**: source trong module khác không tự được phép import ordinary code từ package chỉ được opens.

Ví dụ descriptor:

~~~java
module com.acme.domain {
    exports com.acme.domain.api;
    opens com.acme.domain.model to com.acme.json;
}
~~~

Ý nghĩa:
- Package `com.acme.domain.api` là API công khai, cho phép các module đọc được sử dụng trực tiếp.
- Package `com.acme.domain.model` được mở **riêng cho module `com.acme.json`** để framework trong module đó dùng reflection sâu.
- Những module khác không được suy luận rằng package model đã export hoặc đã mở đối với chúng.

Một **open module** mở tất cả package để reflection nhưng vẫn có tập exports riêng. Không nên dùng `open module` vô điều kiện nếu chỉ một hoặc vài package thực sự cần deep reflection.

### Bảng nhanh

| Cấu hình package | Import class public từ module khác | Reflection sâu vào private member |
| --- | --- | --- |
| Không `exports`, không `opens` | Không, theo ordinary compile-time access | Thường bị chặn qua ranh giới module |
| `exports p;` | Có, nếu readable và class/member công khai | Không chỉ nhờ `exports` |
| `opens p;` | Không chỉ nhờ opens ở compile time | Có thể được phép cho các module khác theo quy tắc reflection |
| `exports p; opens p;` | Có | Có thể được phép |
| `exports p to A;` | Module A được export, module khác không | Không chỉ nhờ exports |
| `opens p to A;` | Không chỉ nhờ opens | Chỉ module A được mở |
| Package thuộc unnamed module | Theo cơ chế tương thích, được export và open | Dễ tương tác reflection hơn, vẫn phải xét API và chính sách bảo mật khác |

**Caveat quan trọng:** “Mở package” không cho phép chạy tùy ý mã native hoặc bỏ qua mọi giới hạn JVM; còn có access rules của reflection API, class loader, security và code logic. Phân biệt quyền từ JPMS với quyền của hệ thống, dữ liệu và ứng dụng.

## 8. Unnamed module: classpath trong thế giới JPMS

Từ Java 9, code không có module declaration được gán vào **unnamed module** của class loader thích hợp. Thiết kế này hỗ trợ ứng dụng Java cũ.

Các đặc điểm:
- Một unnamed module **đọc mọi observable module** theo mô hình module system; tuy nhiên named modules trên module path vẫn cần được **resolve thích hợp** để có mặt trong configuration mà ứng dụng dùng.
- Unnamed module **exports và opens mọi package** của nó theo quy tắc JPMS.
- Named module **không thể dùng khai báo `requires` để phụ thuộc trực tiếp vào unnamed module**, vì unnamed module không có tên để khai báo trong descriptor.
- Có thể có **nhiều unnamed modules** gắn với các class loader khác nhau; không nên mặc định chỉ có đúng một unnamed module vật lý trên toàn JVM.
- Không nên lẫn unnamed **module** với **unnamed package** (default package); một class có `package com.example;` vẫn có thể thuộc unnamed module khi nằm trên classpath.

### Classpath cần dùng một named module trên module path

Giả sử thư viện `com.acme.lib` ở module path còn chương trình truyền thống nằm trên classpath. Lệnh minh họa:

~~~bash
java --module-path mods --add-modules com.acme.lib -cp classes com.acme.app.Main
~~~

Lệnh này có ý nghĩa **chỉ nếu `classes` chứa class `com.acme.app.Main` đã biên dịch theo cấu hình classpath và mô-đun `com.acme.lib` có trên module path**. `--add-modules com.acme.lib` bổ sung module thư viện làm root khi cần, khiến nó được resolve. Nhưng **không thay thế exports**: chương trình classpath chỉ được ordinary access đến package mà thư viện export phù hợp.

Nếu named module cần đọc classpath/unnamed module, chỉ `requires` trong `module-info.java` là **không đủ/không hợp lệ**. Có những tùy chọn đặc biệt như `--add-reads my.module=ALL-UNNAMED` ở các tình huống chuyển tiếp; nhưng nên ưu tiên module hóa thư viện hoặc đưa phụ thuộc vào dạng module rõ ràng hơn thay vì kéo dài cấu hình đặc biệt không cần thiết.

## 9. Automatic module: JAR thường được đặt trên module path

Một JAR **không chứa `module-info.class`** có thể được hệ thống module coi là **automatic module** khi đặt trên module path.

Đặc điểm chính:
- Có **tên module** được lấy từ manifest attribute `Automatic-Module-Name` nếu có, hoặc suy ra từ tên file JAR theo quy tắc.
- Một automatic module **exports mọi package** và **opens mọi package** của nó, giúp tương thích với thư viện truyền thống.
- Automatic modules có cơ chế readability rộng, gồm khả năng đọc các module cần cho quá trình chuyển tiếp, kể cả nội dung thuộc unnamed modules theo quy tắc module system.
- Chúng **không cần `module-info.class`** trong JAR; dù vậy chúng vẫn là **named modules** khi được resolve trên module path.
- Tên được suy ra từ filename có thể thay đổi khi đổi tên JAR/phiên bản; nên sử dụng manifest `Automatic-Module-Name` ổn định nếu cần tương thích trong quá trình chuyển đổi.

**Cẩn thận khi dùng automatic module:** module này có thể giúp chương trình named module tạm thời phụ thuộc vào JAR cũ, nhưng **không cung cấp strong encapsulation mặc định** vì tất cả package đều được export/open. Khi thư viện bổ sung module descriptor tường minh trong bản sau, accessibility có thể thay đổi; cần rà soát các package đang được sử dụng.

### Ba cách đặt cùng một JAR

~~~text
lib/legacy.jar trên CLASS PATH
  -> code thuộc unnamed module
  -> không có tên module dùng được trong "requires"

lib/legacy.jar trên MODULE PATH (không module-info)
  -> có thể trở thành automatic named module
  -> cần được resolve; tên suy ra/manifest

lib/modern.jar trên MODULE PATH (có module-info)
  -> explicit named module
  -> requires/exports/opens theo descriptor
~~~

Không nên kết luận cứ file đuôi `.jar` thì tự biết classpath hay module path; **cách launcher/compiler được cấu hình** mới quyết định vai trò của JAR.

## 10. Module resolution, root modules và module graph

Một module tồn tại trên module path là **observable**, nhưng nó có được đưa vào configuration đang chạy hay không còn phụ thuộc **root modules** và resolution.

Khi chạy:

~~~bash
java --module-path mods -m com.acme.app/com.acme.app.Main
~~~

`com.acme.app` là **root module**. Resolver đi qua các phụ thuộc `requires` và các ràng buộc module để xây dựng graph phù hợp; nhờ `requires com.acme.lib;`, module thư viện xuất hiện trong configuration.

Với chương trình khởi chạy từ classpath, đôi khi cần thêm:

~~~bash
java --module-path mods --add-modules com.acme.lib -cp classes demo.Main
~~~

Đây là cú pháp minh họa yêu cầu một classpath main class có thật. `--add-modules` bổ sung module cần làm root, **không tự export các package bị đóng** và **không tự làm một named module có quyền truy cập vào unnamed module**.

Một số tùy chọn hữu ích:

| Tùy chọn | Vai trò |
| --- | --- |
| `--module-path PATH`, `-p PATH` | Vị trí tìm application modules. |
| `--module NAME[/MAINCLASS]`, `-m NAME[/MAINCLASS]` | Chạy main class trong named module đã resolve. |
| `--add-modules M1,M2` | Bổ sung root modules; có những giá trị đặc biệt theo launcher, ví dụ `ALL-MODULE-PATH`. |
| `--limit-modules M1,M2` | Giới hạn tập module observable đối với quá trình resolution theo quy tắc JVM. |
| `--show-module-resolution` | In thông tin resolution để chẩn đoán graph. |
| `--list-modules` | Liệt kê các module có thể quan sát trong ngữ cảnh launcher và version của chúng. |
| `--describe-module NAME` | Hiển thị thông tin một named module. |
| `jar --describe-module --file FILE.jar` | Xem thông tin module của JAR, gồm automatic module khi phù hợp. |

Ví dụ kiểm tra:

~~~bash
java --list-modules
java --module-path mods --describe-module com.acme.lib
java --show-module-resolution --module-path mods -m com.acme.app/com.acme.app.Main
~~~

Không cung cấp “stdout chính xác” cho các lệnh chẩn đoán này vì kết quả phụ thuộc JDK, tập module, classpath/module path thực tế và platform. Đối với ví dụ hai module hoàn chỉnh ở mục 5, stdout ứng dụng được xác định riêng.

### Split package và module conflict

**Split package** là tình huống cùng tên package xuất hiện trong nhiều module. JPMS hạn chế trường hợp một module đọc những module có package trùng nhau gây mơ hồ/vi phạm constraints; điều này có thể khiến biên dịch hoặc resolution thất bại ngay cả khi trước đó cách ghép JAR trên classpath vẫn chạy.

Thay vì dựa vào classpath order để né lỗi, hãy:
- Xác định JAR nào chứa package trùng.
- Cập nhật/thay thế dependency hoặc di chuyển/đổi package ownership có chủ đích.
- Kiểm tra module graph và class loader, tránh copy package nội bộ vào nhiều named modules.

Không phải mọi xung đột package đều có cùng thông báo lỗi; lỗi chính xác phụ thuộc các module và bối cảnh compile/runtime.

## 11. Tùy chọn phá encapsulation: dùng có chủ đích

JPMS cung cấp tùy chọn chuyển tiếp/migration, nhưng chúng không nên là thiết kế mặc định.

### `--add-reads`

Cú pháp khái quát:

~~~text
--add-reads SOURCE_MODULE=TARGET_MODULE
--add-reads SOURCE_MODULE=ALL-UNNAMED
~~~

Tùy chọn này bổ sung **readability edge** của source module tới target module ở cấu hình thích hợp. Nó **không tự khiến mọi package private/public đều truy cập được**; vẫn cần exports và access modifiers. Dùng để xử lý trường hợp named module cần nhìn class ở unnamed module trong migration, khi không thể đơn giản dùng `requires`.

### `--add-exports`

Cú pháp:

~~~text
--add-exports SOURCE_MODULE/PACKAGE=TARGET_MODULE
--add-exports SOURCE_MODULE/PACKAGE=ALL-UNNAMED
~~~

Cho phép package của source module được **export thêm đến target**, để code trong target module đã có readability phù hợp truy cập **public API**. Có thể đặt ở compiler hoặc runtime khi cần tương ứng; cấu hình chỉ ở compile time không tự bảo đảm runtime có cùng quyền.

Ví dụ minh họa cú pháp:

~~~bash
java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -cp classes demo.Main
~~~

Lệnh trên **không phải khuyến nghị dựa vào JDK internal API** và chỉ có ý nghĩa nếu có class `demo.Main` cần quyền đó; các internal API có thể thay đổi hoặc biến mất. Không dùng `--add-exports` chỉ vì một framework cần mở private field cho reflection; tình huống đó dùng `opens`/`--add-opens` thích hợp.

### `--add-opens`

Cú pháp:

~~~text
--add-opens SOURCE_MODULE/PACKAGE=TARGET_MODULE
--add-opens SOURCE_MODULE/PACKAGE=ALL-UNNAMED
~~~

Mở package cho **deep reflection tại runtime** đối với target được chỉ định. Nó không phải lệnh “export package để source import ở compile time”. Ví dụ:

~~~bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp classes demo.Main
~~~

Đây là ví dụ về **cú pháp tùy chọn migration**, không phải chương trình độc lập, không có stdout cố định. **Java 17 strongly encapsulates JDK internals**; việc reflection gọi `setAccessible(true)` vào private member của package chưa mở có thể ném `InaccessibleObjectException`.

### Ưu tiên phương án bền vững

Thứ tự xử lý thường hợp lý:
1. **Dùng API công khai và cấu hình module descriptor đúng**.
2. Nếu framework cần reflection, **mở đúng package cho đúng module** bằng `opens ... to ...;`.
3. Nếu buộc chuyển tiếp, dùng `--add-reads/--add-exports/--add-opens` với phạm vi nhỏ, ghi lại lý do và kế hoạch bỏ flag.
4. Không mở toàn bộ module hoặc toàn JDK chỉ để chữa một lỗi thiếu quyền chưa được phân tích.

## 12. Truy cập class qua reflection, Class.forName và ClassLoader

Đừng đồng nhất:
- **Tìm/nạp class** từ class loader.
- **Resolve module graph**.
- **Truy cập type/member** theo JLS/JPMS.
- **Bypass Java access checks** qua reflective APIs khi package đã được mở phù hợp.

Ví dụ gọi:

~~~java
Class<?> type = Class.forName("com.acme.lib.api.Greeter");
System.out.println(type.getModule().getName());
~~~

Đây là **đoạn minh họa** bên trong phương thức Java, có thể ném `ClassNotFoundException` và kết quả còn tùy loader/cấu hình runtime. `Class.forName` không phải “bùa” làm mọi module trở nên readable hay mọi private field có thể truy cập.

**Chỉ có reference `Class<?>` không chứng minh member public/private ở trong đó truy cập được.** Với reflection, `getDeclaredField` có thể phát hiện thành viên nhưng `setAccessible(true)` và đọc/ghi thực tế còn bị giới hạn bởi module opens/accessibility theo API. Ngược lại, một package chỉ mở cho reflection không có nghĩa compiler Java có thể import ordinary type từ package đó.

### Resource lookup cũng có quy tắc riêng

`ClassLoader.getResource`/`Module.getResourceAsStream` được thiết kế để tìm **tài nguyên**, không phải cơ chế thay thế module exports/opens cho class access. Named module có thể có quy tắc đóng gói non-class resources liên quan đến opened packages; khi resource không được tìm thấy, không nên tự động kết luận file JAR không chứa nó. Xem contract của API resource được dùng, package resource và module mở package nào.

## 13. Kiểm tra module runtime bằng API Java

Ví dụ standalone **`ModuleInfoDemo.java`** có thể chạy trên classpath:

~~~java
public class ModuleInfoDemo {
    public static void main(String[] args) {
        Module app = ModuleInfoDemo.class.getModule();
        Module base = String.class.getModule();

        System.out.println("appNamed=" + app.isNamed());
        System.out.println("baseName=" + base.getName());
        System.out.println("appReadsBase=" + app.canRead(base));
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 ModuleInfoDemo.java
java -cp . ModuleInfoDemo
~~~

**Stdout chính xác trên JDK 17+ chuẩn với classpath launch:**

~~~text
appNamed=false
baseName=java.base
appReadsBase=true
~~~

Giải thích:
- Lớp trong classpath thuộc unnamed module.
- `String` thuộc named platform module `java.base`.
- Unnamed module đọc module `java.base`; điều này **không tự mở private members** của `java.lang.String` cho reflection.

### Kiểm tra readable và exported bằng API

Module API còn có các phương thức như `Module.canRead(Module)`, `Module.isExported(String, Module)` và `Module.isOpen(String, Module)`. Chúng hỗ trợ kiểm tra trạng thái runtime module graph nhưng không được dùng để đoán mọi access check của Java:

~~~java
Module base = String.class.getModule();
Module caller = ModuleInfoDemo.class.getModule();

System.out.println(base.isExported("java.lang", caller));
System.out.println(base.isOpen("java.lang", caller));
~~~

Đoạn trên là **minh họa trong phương thức của class ModuleInfoDemo**, không được ghép vào ví dụ trước khi biên dịch mà không sửa source phù hợp. Các giá trị có thể khác nếu runtime được bật các flag `--add-opens`/`--add-exports` hoặc module configuration tùy chỉnh.

## 14. Lỗi thường gặp và cách xử lý

| Biểu hiện | Nguyên nhân khả dĩ | Hướng điều tra |
| --- | --- | --- |
| `Error: Could not find or load main class ...` | Root classpath sai, binary name sai, thiếu main class hoặc loader không tìm được | Kiểm tra `-cp` trỏ tới root package, tên đầy đủ và file class thật. |
| `ClassNotFoundException` | Class loader/reflective lookup không tìm thấy binary class được yêu cầu | Kiểm tra classpath, module graph, loader được sử dụng, tên binary. |
| `NoClassDefFoundError` | VM không thể load/define class cần dùng hoặc initialization của class từng thất bại | Xem cause và version/dependency thực tế; không chỉ dò filename. |
| `module not found: com.acme.lib` | Module không observable với path/định danh đang dùng, hoặc không thỏa resolution | Kiểm tra `--module-path`, tên module, `module-info`/manifest. |
| `package ... is not visible` | Module caller không đọc target hoặc package không export sang caller | Kiểm tra cả `requires` lẫn `exports` và qualified exports. |
| Public class ở package không export bị từ chối | `public` không đủ qua ranh giới named modules | Export đúng package, không tự sửa access bằng reflection. |
| `InaccessibleObjectException` khi gọi `setAccessible(true)` | Module sở hữu package không opens tới module gọi reflection | Thêm `opens p to framework.module;` hoặc `--add-opens` tạm thời, không dùng `--add-exports` thay. |
| `IllegalAccessError` tại runtime | Binary thực thi vi phạm access checks khi class/module configuration khác lúc build | So module graph, exports, build/runtime artifacts, JAR versions. |
| Named module không import được class ở classpath | Named module không có phụ thuộc `requires` tới unnamed module | Module hóa dependency hoặc migration flag `--add-reads` phù hợp. |
| Có JAR trên module path nhưng chương trình không tìm thấy class | Module chưa resolve, tên package/module khác, loader/context không đúng | Kiểm tra root modules, `--add-modules`, descriptor và exports. |
| “Modules have split package” hoặc resolution conflict | Cùng package nằm trong nhiều modules gây xung đột | Rà soát dependency, bỏ trùng/đổi ownership; không dựa classpath order. |
| JAR đổi tên khiến `requires X` hỏng | Tên automatic module được suy ra từ filename | Dùng tên module ổn định qua manifest hoặc explicit module-info. |
| Trước Java 9 reflection vào JDK private chạy, Java 17 thất bại | Strong encapsulation của JDK internals | Nâng framework/API; mở package giới hạn nếu bắt buộc chuyển tiếp. |
| Có `requires transitive` nhưng vẫn không import type | Package của dependency không export tới module dùng hoặc class/member không public | Kiểm tra readability **và** exports, không chỉ requires chain. |

### Checklist điều tra thực tế

1. **Đang compile hay run?** Lỗi khi compile có thể khác lỗi linking/access của bytecode đã được tạo.
2. **Class nằm ở đâu?** Kiểm tra đường dẫn `.class`, JAR, tên binary và cấu trúc package.
3. **JAR được đưa vào classpath hay module path?** Đừng kết luận từ build.gradle/pom.xml nếu runtime thực tế khác.
4. **Module thuộc loại nào?** Explicit named, automatic named hay unnamed?
5. **Đã resolved chưa?** Xem root modules, `requires`, `--add-modules` và output `--show-module-resolution`.
6. **Caller đọc được module target không?** Kiểm tra requires, transitive edges, `Module.canRead` nếu cần.
7. **Package được export sang caller không?** Kiểm tra `exports`/qualified exports, class/member modifiers.
8. **Có deep reflection không?** Kiểm tra `opens`/qualified opens thay vì chỉ `exports`.
9. **Class loader nào định nghĩa class?** Đừng bỏ qua custom loader, module layers hoặc bản JAR trùng.
10. **So build và runtime artifact**: module descriptor, manifest `Automatic-Module-Name`, phiên bản JVM, tùy chọn launcher và thư viện thực tế.

## 15. Bài tập tự kiểm tra

**Câu 1.** Một JAR có trong `--module-path` thì class của nó chắc chắn truy cập được từ mọi named module khác?

**Đáp án:** Không. Module cần observable/resolved, caller cần readability, package cần exports phù hợp, kiểu/member còn phải thỏa access modifiers.

**Câu 2.** Vì sao `public class Internal` trong một package không export vẫn không import được từ named module khác?

**Đáp án:** `public` chỉ là điều kiện access trong Java; JPMS yêu cầu package được export đến module caller bên cạnh readability.

**Câu 3.** `requires B;` có tự làm module A đọc được module C nếu B requires C không?

**Đáp án:** Không nhất thiết. Quan hệ transitive phải được khai báo/thiết lập theo quy tắc, ví dụ B có `requires transitive C;`.

**Câu 4.** Khác nhau giữa `exports p;` và `opens p;` là gì?

**Đáp án:** `exports` cho phép ordinary compile-time/runtime access tới public/protected API từ module đọc được; `opens` cho phép deep reflection runtime và không tương đương việc export package để import ordinary source ở compile time.

**Câu 5.** Một chương trình nằm trên classpath có thể hỏi tên module của chính mình không?

**Đáp án:** Có. `SomeClass.class.getModule()` trả một unnamed module; `isNamed()` false và `getName()` null.

**Câu 6.** Named module `A` muốn dùng JAR cũ trên classpath, có thể viết `requires unnamed;` trong module-info không?

**Đáp án:** Không. Unnamed module không có module name để khai báo requires; nên module hóa/thay dependency hoặc dùng các cơ chế migration phù hợp.

**Câu 7.** Tại sao `Class.forName(...)` không bảo đảm `setAccessible(true)` với private field thành công?

**Đáp án:** Loading/lookup class khác với module access. Deep reflection qua ranh giới named module có thể cần package được opens phù hợp.

**Câu 8.** Một JAR không có module-info khi nằm trên module path gọi là gì?

**Đáp án:** Có thể được suy ra thành **automatic module**, có tên lấy từ manifest `Automatic-Module-Name` hoặc filename, với quy tắc compatibility riêng.

**Câu 9.** Có thể định danh class runtime chỉ bằng tên module + tên class?

**Đáp án:** Không chính xác. Theo JVMS, định danh class/interface runtime dựa vào **binary name và defining class loader**; module membership là thông tin tổ chức/access bổ sung.

**Câu 10.** Tại sao module path có thể gây lỗi split package mà trước đó classpath chạy được?

**Đáp án:** Named modules có constraints về module graph, package ownership và visibility, không cho phép cấu hình đọc các module có package trùng gây mơ hồ. Classpath truyền thống có mô hình tìm class khác, nên cùng tập JAR có thể gặp lỗi khác khi chuyển sang JPMS.

**Câu 11.** Bật `--add-opens` có giúp Java source import một internal package chưa export không?

**Đáp án:** Không. `--add-opens` dành cho reflective access ở runtime; ordinary source access thường cần exports và readability thích hợp.

**Câu 12.** Vì sao chỉ đổi tên JAR lại có thể khiến module cần `requires` không tìm thấy?

**Đáp án:** Nếu dùng **automatic module** với tên suy ra từ filename, tên module có thể thay đổi. Dùng explicit module descriptor hoặc manifest name ổn định.

## 16. Bản đồ tư duy

~~~text
                CÙNG MỘT CLASS / JAR
                         |
                  đặt ở đâu?
                /                \
         CLASS PATH           MODULE PATH
             |                     |
         unnamed module       named module
             |                 /          \
       exports/opens all  explicit     automatic
             |           module-info   inferred name
             |              |          exports/opens all
             |         requires / exports / opens
             |              |
             +--------------+-----------------+
                            |
                     Tìm và dùng class
                            |
                   1. Observable?
                   2. Resolved?
                   3. Readable?
                   4. Package exported?
                   5. Java modifiers OK?
                            |
                          ACCESS

Deep reflection: ngoài điều kiện API cụ thể, xét "opens"
Runtime class identity: (binary name, defining class loader)
~~~

**Điểm cốt lõi:** **class path quyết định nơi tìm class theo cơ chế truyền thống**, còn **module path hỗ trợ khám phá named modules và giải quyết phụ thuộc JPMS**. Đối với named module, **readability không thay thế exports**, và **exports không thay thế opens cho deep reflection**. Khi điều tra lớp “không thấy” hay “không truy cập được”, luôn tách vấn đề tìm/nạp lớp, resolution/readability, package accessibility, reflection và class loader.

## Tài liệu tham khảo chính thức

- [JLS Java SE 17 — §7.7 Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7).
- [JLS Java SE 17 — §7.7.2 Exported and Opened Packages](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.2).
- [JLS Java SE 17 — §7.7.5 Unnamed Modules](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.5).
- [JVMS Java SE 17 — §5.3 Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3).
- [Java SE 17 — The java Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [Java SE 17 — The javac Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html).
- [Java SE 17 — Migrating From JDK 8 to Later JDK Releases](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html).
- [Java SE 17 API — java.lang.Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-006">Class object, lớp mảng và cách JVM tạo biểu diễn kiểu khi chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Class object, lớp mảng và cách JVM tạo biểu diễn kiểu khi chạy

Khi một chương trình Java hoạt động, JVM không chỉ quản lý **các instance**. Nó còn quản lý **kiểu tại runtime**: class, interface, array type, primitive type và kiểu `void` dùng trong mô hình reflection. Đối tượng `java.lang.Class<?>` là **điểm truy cập bằng Java tới biểu diễn của một kiểu được JVM nhận biết**; còn **array class** là một loại class được JVM tạo theo quy tắc riêng, không cần một file `String[].class` do `javac` xuất ra.

Ba ý chính của bài:

1. **`Class` object khác instance:** nhiều instance cùng runtime class thường nhận cùng một `Class` object qua `getClass()`; đối tượng `Class` mô tả **kiểu**, không phải một instance của kiểu đó.
2. **Định danh kiểu gắn với class loader:** hai định nghĩa class có **cùng binary name** nhưng được định nghĩa bởi hai defining class loaders khác nhau là **hai runtime types khác nhau**. Điều này giải thích các lỗi loader và cast trong ứng dụng plugin/container.
3. **JVM tạo array classes khi cần:** ví dụ `int[].class`, `String[].class`, `String[][].class` là các đối tượng `Class` hợp lệ dù không tồn tại mã nguồn `class String[]`. Một array object còn giữ runtime component type để JVM kiểm tra việc lưu phần tử.

Phạm vi của bài là **nạp/định danh lớp và biểu diễn kiểu khi chạy**, không phải cấu trúc toàn bộ heap hay thuật toán GC. Ví dụ chạy được với **Java 17+**.

## 1. “Class object” nghĩa là gì?

Trong Java, `java.lang.Class<T>` là lớp thư viện đại diện cho thông tin runtime của một kiểu. Đối tượng của lớp này thường được gọi là **class object**, **Class mirror** hay đơn giản là `Class` object.

```java
String first = new String("A");
String second = new String("B");

Class<?> mirror1 = first.getClass();
Class<?> mirror2 = second.getClass();
Class<String> literal = String.class;

System.out.println(mirror1 == mirror2); // true
System.out.println(mirror1 == literal); // true
System.out.println(first == second);    // false
```

Đây là **đoạn Java đặt trong method**, không phải source file độc lập. Hai `String` instance được tạo riêng, nhưng cả hai có cùng runtime class nên hai lệnh `getClass()` trả về cùng `Class` object. Dòng `String.class` cung cấp class literal tương ứng.

**Không nhầm các đối tượng:**

```text
             String instance A                 String instance B
           ┌─────────────────┐               ┌─────────────────┐
first ───► │ nội dung "A"     │   second ───► │ nội dung "B"     │
           └─────────────────┘               └─────────────────┘
                    │                                  │
                    └───────── runtime class ──────────┘
                                     │
                                     ▼
                              Class<String>
                           mirror của String
```

Sơ đồ trình bày **quan hệ logic**, không phải bố cục RAM thực tế. Từ một `Class` object, reflection có thể tra tên, superclass, interface, field, method, constructor, annotation, component type của mảng… nhưng `Class` object không tự lưu tất cả giá trị instance fields của từng object.

### Có những cách nào lấy Class object?

| Cách | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Class literal | `String.class` | Nhắc tới một kiểu ở compile time |
| Class literal của primitive | `int.class` | Lấy Class mirror cho kiểu primitive |
| Class literal của array | `String[][].class` | Lấy Class mirror cho array type |
| Instance runtime | `obj.getClass()` | Kiểu thực tế của **object**, không chỉ kiểu tĩnh của biến |
| Lookup bằng tên | `Class.forName("java.lang.String")` | Tìm/nạp kiểu theo tên nhị phân qua loader phù hợp, với quy tắc initialization của overload |
| Reflection tạo mảng | `Array.newInstance(component, 3).getClass()` | JVM tạo array object/array Class theo component type runtime |

`obj.getClass()` cần `obj != null`; gọi trên `null` gây `NullPointerException`. Ngược lại `String.class` không yêu cầu tạo instance `String`.

**`Class<?>` không phải `Class<Object>`:** dấu `?` là wildcard, phù hợp khi chương trình chỉ muốn mô tả một kiểu runtime mà không biết tham số `T` cụ thể. `Class<String>` mô tả kiểu `String`, nhưng không có nghĩa một `Class<String>` là một **String instance**.

## 2. Runtime class khác kiểu khai báo tĩnh của biến

```java
Object value = "hello";
System.out.println(value.getClass() == String.class); // true
```

Biến `value` có **declared/static type** là `Object`, nhưng object được tham chiếu thuộc **runtime class** `String`. `value.getClass()` phản ánh object thật đang tồn tại, không phải từ khóa `Object` trong khai báo.

Điều này liên quan trực tiếp đến:

- **Dynamic dispatch** của các instance methods có thể override.
- **`instanceof` và reference casts** được quyết định từ runtime type cùng các quan hệ subtype phù hợp.
- **Reflection**: `getDeclaredMethods()` trả method khai báo ở class mô tả; `getMethods()` bao gồm các public methods theo quy tắc API.
- **Serialization/framework mapping**: có thể cần biết runtime class để đọc metadata và tạo/biến đổi dữ liệu.

Class object và **class metadata nội bộ** không hoàn toàn là cùng một khái niệm API. JVM có thể lưu cấu trúc lớp theo cách triển khai riêng và cung cấp `java.lang.Class` làm mirror quan sát được từ Java. Đừng suy ra từ `Class<?> c` rằng toàn bộ class-file bytes hoặc metadata phải nằm bên trong object `c` theo một layout xác định.

## 3. Định danh runtime type: binary name + defining class loader

Một class hay interface không được xác định chỉ bằng chuỗi tên.

Theo mô hình JVM, **danh tính một class/interface được quyết định bởi cặp (binary name, defining class loader)**. Định nghĩa có cùng tên nhưng do loader khác nhau định nghĩa tạo ra các runtime classes **không đồng nhất**. Bởi vậy:

```text
(name = "example.Plugin", loader = Loader-A)  ──► Class A
(name = "example.Plugin", loader = Loader-B)  ──► Class B

A.getName().equals(B.getName())   → true
A == B                            → false
A.isAssignableFrom(B)             → false (nếu không có quan hệ subtype)
```

Sơ đồ giả định hai loader thực sự **định nghĩa** class cùng tên một cách độc lập. Nếu loader B **ủy quyền cho loader A/parent** và trả về cùng định nghĩa đã được nạp, class object sẽ giống nhau. **Initiating loader** (loader được yêu cầu bắt đầu việc nạp) và **defining loader** (loader thực sự định nghĩa class) không nhất thiết là một; phải phân biệt hai vai trò.

### 3.1. Parent delegation và class loader namespaces

Cách nạp class phổ biến dùng parent delegation: loader thử ủy quyền lên parent trước khi tự tìm định nghĩa. Cơ chế này giúp nhiều code dùng chung các lớp nền tảng. Tuy nhiên custom class loader có thể dùng các chiến lược riêng được JVM cho phép, vì vậy không nên nói “mọi class có cùng tên trong một JVM đều tự động là một class”.

Khi hai thư viện/plugin được nạp bởi loader khác nhau:

- Có thể thấy thông báo khó hiểu kiểu `X cannot be cast to X`: tên hiển thị giống nhau nhưng **định danh runtime khác nhau**.
- Một class có thể dùng static fields của định nghĩa A, class còn lại dùng static fields của định nghĩa B.
- Một reference có runtime class của loader A không tự tương thích với class cùng tên thuộc loader B.
- Array types `X[]` tương ứng cũng khác nhau khi component `X` khác runtime type.

**Quan trọng:** class loader quyết định namespace và nạp định nghĩa; nó không làm mọi cast đều thất bại. Nếu hai phần code dùng **cùng `Class` object** qua delegation, identity và các phép cast theo quan hệ kiểu vẫn bình thường.

### 3.2. `getClassLoader()` và bootstrap classes

`SomeClass.class.getClassLoader()` trả defining loader từ góc nhìn Java reflection. Với các kiểu được **bootstrap loader** định nghĩa, API thường trả **`null`**; cũng có các trường hợp như primitive/void và mảng primitive không có defining loader là object `ClassLoader` bình thường. Vì vậy `null` **không có nghĩa class chưa được nạp**.

Một ví dụ không phụ thuộc địa chỉ hay tên triển khai:

```java
System.out.println(int.class.getClassLoader() == null);    // true
System.out.println(int[].class.getClassLoader() == null);  // true
```

Các lớp do application loader định nghĩa thường có `getClassLoader() != null`, nhưng đừng hard-code tên lớp implementation của loader vì các JDK/runtime khác nhau có thể tổ chức khác nhau.

## 4. Primitive, `void` và reference types đều có Class mirror

`Class` API bao gồm cả kiểu không thể có instance object riêng giống `String`:

| Biểu thức | Điều biểu diễn | Ghi chú |
| --- | --- | --- |
| `int.class` | Primitive type `int` | `int.class == Integer.TYPE` |
| `boolean.class` | Primitive type `boolean` | Không tương đương `Boolean.class` |
| `void.class` | Kiểu `void` trong reflection | `void.class == Void.TYPE`; không thể `new void` |
| `String.class` | Runtime class `String` | Reference type |
| `java.util.List.class` | Runtime interface `List` | Không phải một object `List` cụ thể |
| `int[].class` | Array class của `int[]` | Reference type dù element type là primitive |
| `String[][].class` | Array class hai chiều | Component type là `String[]` |

`Class.isPrimitive()` dùng để hỏi về primitive-like Class objects theo API Java (bao gồm trường hợp `void.class`), nhưng **`void` không phải kiểu biến chứa giá trị thông thường** trong Java source. Với wrapper types, `Integer.class.isPrimitive()` là `false`.

Đối với generic code, phân biệt:

```java
Class<?> primitive = int.class;
Class<?> wrapper = Integer.class;
System.out.println(primitive == wrapper); // false
```

Autoboxing/unboxing giữa `int` và `Integer` là chuyển đổi ngôn ngữ, **không** khiến hai kiểu có chung Class mirror.

## 5. JVM tạo lớp mảng khác lớp thường ở điểm nào?

### 5.1. Array class là runtime type thực sự

Mỗi kiểu mảng có runtime class riêng:

```text
int[]           → Class mirror: int[].class
int[][]         → Class mirror: int[][].class
String[]        → Class mirror: String[].class
String[][]      → Class mirror: String[][].class
Plugin[]        → Class mirror tương ứng Plugin[] của đúng defining loader
```

JVM **không yêu cầu một tệp ClassFile tên `int[].class` hoặc `java.lang.String[].class`**. Định dạng và ngữ nghĩa của array class được JVM xử lý đặc biệt. Các array classes **được JVM tạo khi cần**, không phải nạp trực tiếp từ một ClassFile khai báo mảng như class thông thường.

Đối với **array of reference type**, defining loader của array class được xác định từ defining loader của component/reference type. Với mảng primitive, không có defining class loader do người dùng cung cấp. Đó là lý do hai class `Plugin` cùng binary name nhưng khác loader kéo theo hai kiểu `Plugin[]` khác nhau.

### 5.2. `getComponentType()` và `isArray()`

```java
Class<?> t = String[][].class;
System.out.println(t.isArray());                                  // true
System.out.println(t.getComponentType() == String[].class);        // true
System.out.println(t.getComponentType().getComponentType()
        == String.class);                                         // true
```

Đây là đoạn minh họa trong method; `getComponentType()` **bỏ đúng một chiều**. Đối với `String[][]`, component trực tiếp là **`String[]`**, không phải `String`. Với `int[].class`, component là `int.class`.

Với Class object không mô tả mảng, `getComponentType()` trả **`null`**. `isArray()` trả `false` cho primitive, `void`, class và interface thường.

### 5.3. Array superclass và implemented interfaces

Mọi array object trong Java là reference object. JVM đặt các quan hệ kiểu:

- Superclass trực tiếp của array class là **`java.lang.Object`**.
- Array classes implement **`java.lang.Cloneable`** và **`java.io.Serializable`**.
- Array object có **`length`** cố định và có thể **clone** tạo array object mới.
- Không có source `class MyArray extends String[]`; chương trình Java không tự định nghĩa subclass của array class.

Những điều này cũng đúng với `int[]`: `int[]` là object, dù các phần tử bên trong là `int`.

```java
System.out.println(int[].class.getSuperclass() == Object.class); // true
System.out.println(Cloneable.class.isAssignableFrom(int[].class)); // true
System.out.println(java.io.Serializable.class.isAssignableFrom(int[].class)); // true
```

Không nhầm `getSuperclass()` với `getInterfaces()`: array class có superclass `Object` và hai interfaces chuẩn, chứ không kế thừa từ `Object[]` trong mọi trường hợp. **`int[]` không phải subtype của `Object[]`**, nhưng là subtype của `Object`.

### 5.4. Tính covariance và kiểm tra runtime

Với element type là reference, Java arrays có tính **covariance**:

```java
String[] texts = new String[1];
Object[] values = texts;        // hợp lệ
values[0] = "hello";           // hợp lệ
values[0] = new Object();      // ArrayStoreException khi chạy
```

Đây là đoạn minh họa, dòng cuối cố ý gây exception. Kiểu **tĩnh** của `values` là `Object[]`, nhưng runtime class vẫn là **`String[].class`**. Instruction `aastore` kiểm tra giá trị được gán có tương thích với runtime component type hay không. Mảng vẫn là cùng object; phép gán `values = texts` **không chuyển mảng thành một `Object[]` mới**.

Ngược lại, primitive arrays **không covariant theo cách đó**:

```java
Object object = new int[2]; // hợp lệ: int[] là một Object
// Object[] wrong = new int[2]; // lỗi biên dịch
```

Ngay cả khi kiểu array là subtype hợp lệ, **`aastore` kiểm tra lúc runtime** vì compiler có thể chỉ biết biến được khai báo kiểu rộng hơn.

## 6. Binary name và ký hiệu tên array class trong reflection

`Class.getName()` với class thông thường thường trả binary name, còn với array class dùng ký hiệu gần giống **field descriptor**:

| Runtime type | `Class.getName()` | `Class.getTypeName()` |
| --- | --- | --- |
| `int` | `int` | `int` |
| `String` | `java.lang.String` | `java.lang.String` |
| `int[]` | `[I` | `int[]` |
| `long[][]` | `[[J` | `long[][]` |
| `String[]` | `[Ljava.lang.String;` | `java.lang.String[]` |
| `String[][]` | `[[Ljava.lang.String;` | `java.lang.String[][]` |

Giải mã:

- Mỗi ký tự **`[`** biểu thị một chiều mảng.
- `I` biểu thị `int`, `J` biểu thị `long`, `Z` biểu thị `boolean`, `D` biểu thị `double`.
- `L...;` biểu thị reference component type. Trong `Class.getName()`, tên class bên trong dùng dấu **`.`** (`java.lang.String`), không phải dấu `/` mà bạn có thể thấy trong ClassFile descriptor (`java/lang/String`).
- Với nested class, binary name của thành phần có thể chứa ký tự **`$`**, ví dụ `Outer$Inner`.

Lưu ý hai bối cảnh: tên từ **`Class.getName()`** và **field descriptor lưu trong ClassFile** rất giống đối với array nhưng **không đồng nhất cách viết tên class tham chiếu**. Không lấy chuỗi `Ljava/lang/String;` đưa thẳng vào `Class.forName` mà không xét quy tắc tên.

### `Class.forName` với array name

```java
Class<?> ints = Class.forName("[I");
Class<?> texts = Class.forName("[Ljava.lang.String;");

System.out.println(ints == int[].class);        // true
System.out.println(texts == String[].class);    // true
```

Đây là đoạn trong method **`throws ClassNotFoundException`** hoặc có xử lý exception. Tên `java.lang.String[]` **không phải** ký hiệu nhị phân phù hợp cho array lookup theo overload trên; với primitive source name `"int"`, `Class.forName("int")` cũng không cung cấp primitive Class object. Dùng `int.class` hoặc `Integer.TYPE` cho primitive.

Khi dùng `Class.forName(String)`, overload đơn giản mặc định có hành vi yêu cầu initialize class thông thường khi phù hợp. Có overload **`Class.forName(String name, boolean initialize, ClassLoader loader)`** để chọn rõ việc initialize. Khi tên chỉ **array class**, nó không buộc component class phải initialize chỉ để tạo/tra array Class object.

## 7. Các instruction liên quan tới tạo mảng

Compiler/JVM có instruction riêng cho array object; nó không dùng `new` với một tên class file `"int[]"` được khai báo ở Java.

| Instruction | Mục đích | Ví dụ Java source |
| --- | --- | --- |
| `newarray` | Tạo mảng **primitive** một chiều | `new int[3]`, `new byte[2]` |
| `anewarray` | Tạo mảng **reference component** một chiều | `new String[3]`, `new int[2][]` |
| `multianewarray` | Cấp phát các chiều được chỉ định của mảng nhiều chiều | `new int[2][3]` |
| `arraylength` | Lấy độ dài array object từ array reference | `numbers.length` |
| `iaload`, `aaload` và nhóm load | Đọc phần tử theo loại mảng | `numbers[i]`, `names[i]` |
| `iastore`, `aastore` và nhóm store | Ghi phần tử; `aastore` kiểm tra loại runtime khi ghi reference | `numbers[i]=1`, `names[i]="x"` |

Đây là những **mẫu bytecode điển hình**, không có cam kết mỗi biểu thức source luôn có một instruction tương ứng duy nhất. Compiler có thể thay đổi cấu trúc lệnh phù hợp ngữ nghĩa.

### 7.1. Mảng nhiều chiều là mảng chứa mảng

```java
int[][] a = new int[2][3];
int[][] b = new int[2][];

System.out.println(a.length);           // 2
System.out.println(a[0].length);        // 3
System.out.println(b.length);           // 2
System.out.println(b[0] == null);       // true
```

Với `a`, JVM tạo outer array và các inner arrays đủ cho hai chiều đã chỉ định; với `b`, chỉ outer array được tạo và các vị trí component reference ban đầu là `null`. Vì vậy **cùng có runtime type `int[][]`**, nhưng đồ thị các array instances đã cấp phát khác nhau.

**Độ dài là thuộc tính của array instance**, không phải thuộc tính cố định của array Class: `new int[2]` và `new int[7]` có cùng `Class` object `int[].class`, nhưng lần lượt có length 2 và 7. Không suy ra `Class` mô tả `int[2]` là một runtime type khác `int[7]`.

### 7.2. Dữ liệu và lỗi lúc tạo/truy cập

- `new int[-1]` gây **`NegativeArraySizeException`**.
- Cấp phát array có thể gây **`OutOfMemoryError`** nếu thiếu tài nguyên.
- Lấy `length` hoặc đọc/ghi phần tử qua null array reference có thể gây **`NullPointerException`**.
- Chỉ số âm hoặc `>= length` gây **`ArrayIndexOutOfBoundsException`** khi truy cập.
- Ghi object không hợp component type thực sự vào mảng reference có thể gây **`ArrayStoreException`**.
- Không phải mọi phép xử lý `Object[]` đều hợp lệ với `int[]`; kiểu phần tử phải đúng.

Các lỗi trên liên quan **array instances** và giá trị runtime. Bytecode verifier còn kiểm tra những ràng buộc về loại instruction, operand stack và tính hợp lệ của class file; đó là tầng kiểm tra khác.

## 8. Ví dụ chạy Java 17+: Class object cho class, primitive và array

Tạo **`ClassMirrorDemo.java`** trong một thư mục trống:

```java
import java.io.Serializable;
import java.lang.reflect.Array;

public class ClassMirrorDemo {
    interface Marker {}

    static class Item implements Marker {}

    static class Other {}

    public static void main(String[] args) throws ClassNotFoundException {
        Item first = new Item();
        Item second = new Item();

        Class<?> mirror = first.getClass();
        System.out.println("same literal: " + (mirror == Item.class));
        System.out.println("same runtime type: "
                + (mirror == second.getClass()));
        System.out.println("different instances: " + (first != second));

        System.out.println("class name: " + mirror.getName());
        System.out.println("simple name: " + mirror.getSimpleName());
        System.out.println("superclass: "
                + mirror.getSuperclass().getName());
        System.out.println("interface: "
                + mirror.getInterfaces()[0].getSimpleName());

        System.out.println("int primitive: " + int.class.isPrimitive());
        System.out.println("wrapper primitive: "
                + Integer.class.isPrimitive());
        System.out.println("void mirror: " + (void.class == Void.TYPE));

        System.out.println("int array name: " + int[].class.getName());
        System.out.println("String 2D name: "
                + String[][].class.getName());
        System.out.println("component 2D: "
                + (String[][].class.getComponentType()
                        == String[].class));
        System.out.println("component primitive: "
                + int[].class.getComponentType().isPrimitive());

        System.out.println("array superclass: "
                + (int[].class.getSuperclass() == Object.class));
        System.out.println("array Cloneable: "
                + Cloneable.class.isAssignableFrom(int[].class));
        System.out.println("array Serializable: "
                + Serializable.class.isAssignableFrom(int[].class));
        System.out.println("array loader: "
                + (Item[].class.getClassLoader()
                        == Item.class.getClassLoader()));

        int[] numbers = {1, 2, 3};
        System.out.println("same array class: "
                + (numbers.getClass() == int[].class));
        System.out.println("array length: " + Array.getLength(numbers));

        System.out.println("lookup [I: "
                + (Class.forName("[I") == int[].class));
        System.out.println("lookup String[]: "
                + (Class.forName("[Ljava.lang.String;")
                        == String[].class));

        Object[] items = new Item[1];
        items[0] = new Item();
        System.out.println("runtime component: "
                + items.getClass().getComponentType().getSimpleName());
        try {
            items[0] = new Other();
        } catch (ArrayStoreException e) {
            System.out.println("bad store: ArrayStoreException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ClassMirrorDemo.java
java ClassMirrorDemo
```

**Standard output:**

```text
same literal: true
same runtime type: true
different instances: true
class name: ClassMirrorDemo$Item
simple name: Item
superclass: java.lang.Object
interface: Marker
int primitive: true
wrapper primitive: false
void mirror: true
int array name: [I
String 2D name: [[Ljava.lang.String;
component 2D: true
component primitive: true
array superclass: true
array Cloneable: true
array Serializable: true
array loader: true
same array class: true
array length: 3
lookup [I: true
lookup String[]: true
runtime component: Item
bad store: ArrayStoreException
```

### Giải thích quan sát được

1. Hai `new Item()` tạo **hai instance**, nhưng `getClass()` và `Item.class` dẫn tới cùng Class mirror của kiểu `Item` đã được định nghĩa bởi cùng loader.
2. `ClassMirrorDemo$Item` là **binary name của nested class**; `getSimpleName()` trả phần `Item`.
3. `int.class` là primitive; `Integer.class` là wrapper reference class. `void.class` đại diện kiểu `void` trong reflection.
4. `[I` biểu diễn `int[]`; `[[Ljava.lang.String;` biểu diễn `String[][]`.
5. Mảng `int[]` có superclass `Object`, là `Cloneable` và `Serializable`. Một array class của `Item[]` có defining loader tương ứng với `Item`.
6. `Array.getLength` đọc length của **array instance**, không đọc một thuộc tính số phần tử cố định của `int[].class`.
7. `Class.forName("[I")` và tên array nhị phân `[Ljava.lang.String;` trả array Class mirror tương ứng.
8. `Object[] items = new Item[1]` không làm runtime component thành `Object`. Gán `Other` qua `Object[]` phải bị `aastore` bác vì object array thực tế chỉ chứa được `Item` hoặc subtype thích hợp.

Chương trình bắt `ArrayStoreException` và tự in chuỗi cố định, tránh phụ thuộc thông báo chi tiết giữa các phiên bản JVM.

## 9. Class literal và array creation **không** đồng nghĩa initialize component class

Class loading, linking, **class initialization** và **array class creation** là các sự kiện cần phân biệt. Lấy `Target.class` hoặc tạo `Target[]` không phải luôn kích hoạt `Target` static initializer.

Tạo file **`ArrayInitializationDemo.java`**:

```java
public class ArrayInitializationDemo {
    static class Target {
        static {
            System.out.println("Target static initializer");
        }

        Target() {
            System.out.println("Target constructor");
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        System.out.println("begin");

        Class<?> type = Target.class;
        System.out.println("literal: " + type.getSimpleName());

        Target[] array = new Target[2];
        System.out.println("array length: " + array.length);
        System.out.println("component: "
                + array.getClass().getComponentType().getSimpleName());

        Class.forName(Target.class.getName(),
                false, Target.class.getClassLoader());
        System.out.println("after lookup without initialization");

        Class.forName(Target.class.getName(),
                true, Target.class.getClassLoader());
        System.out.println("after lookup with initialization");

        new Target();
        System.out.println("end");
    }
}
```

Chạy trên Java 17+:

```bash
javac -encoding UTF-8 ArrayInitializationDemo.java
java ArrayInitializationDemo
```

**Standard output chính xác:**

```text
begin
literal: Target
array length: 2
component: Target
after lookup without initialization
Target static initializer
after lookup with initialization
Target constructor
end
```

### Phân tích

- `Target.class` cung cấp Class mirror của nested class `Target`; thao tác này **không tự yêu cầu thực thi static initializer**.
- `new Target[2]` tạo một **array object có hai vị trí tham chiếu**; hai phần tử ban đầu là `null`. Nó **không tạo hai object `Target`**, vì vậy không in constructor; chỉ tạo mảng cũng không tự khởi tạo tĩnh của component class.
- `array.getClass().getComponentType()` trả Class mirror của `Target`, không buộc phải thực hiện `Target.<clinit>`.
- `Class.forName(name, false, loader)` nạp/tra và liên kết lớp nếu cần nhưng yêu cầu **không initialize class** thông qua lời gọi này.
- `Class.forName(name, true, loader)` yêu cầu initialization của class chưa được initialized, nên in đúng **một** dòng `Target static initializer`.
- Sau khi static initialization thành công, `new Target()` tạo instance và gọi constructor; không chạy lại static initializer.

**Ghi nhớ:** array class có thể được JVM tạo hoặc giải quyết mà không khởi tạo component class. Nếu **sau đó** code tạo `new Target()`, gọi static method của `Target` hoặc active-use tương ứng, initialization mới phải xảy ra theo quy tắc JVM/JLS.

Đối với reference array, tạo outer array và **đưa các reference mặc định `null` vào phần tử** khác hoàn toàn việc tạo các instances của component type. Với primitive array, các phần tử có giá trị mặc định của primitive type như `0`, `false` hoặc `0.0`.

## 10. Thử nghiệm định danh lớp với hai defining loaders

Đây là ví dụ minh họa trực tiếp rằng **cùng binary name không đồng nghĩa cùng class object**. Không thay classloader của JVM đang chạy toàn hệ thống; ví dụ dùng hai custom class loaders chỉ để định nghĩa độc lập một class nhỏ từ **cùng bytecode**.

Tạo **`LoaderTarget.java`**:

```java
public class LoaderTarget {
}
```

Tạo **`LoaderIdentityDemo.java`**:

```java
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoaderIdentityDemo {
    static final class ByteLoader extends ClassLoader {
        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("LoaderTarget.class"));

        ByteLoader loaderA = new ByteLoader();
        ByteLoader loaderB = new ByteLoader();

        Class<?> a = loaderA.define("LoaderTarget", bytes);
        Class<?> b = loaderB.define("LoaderTarget", bytes);

        System.out.println("same binary name: "
                + a.getName().equals(b.getName()));
        System.out.println("same Class object: " + (a == b));
        System.out.println("same defining loader: "
                + (a.getClassLoader() == b.getClassLoader()));

        Class<?> arrayA = Array.newInstance(a, 0).getClass();
        Class<?> arrayB = Array.newInstance(b, 0).getClass();
        System.out.println("same array class: " + (arrayA == arrayB));
        System.out.println("array A component: "
                + (arrayA.getComponentType() == a));
        System.out.println("array B component: "
                + (arrayB.getComponentType() == b));
    }
}
```

Biên dịch cả hai file trong cùng thư mục và chạy ở đó:

```bash
javac -encoding UTF-8 LoaderTarget.java LoaderIdentityDemo.java
java LoaderIdentityDemo
```

**Standard output chính xác:**

```text
same binary name: true
same Class object: false
same defining loader: false
same array class: false
array A component: true
array B component: true
```

### Vì sao ví dụ này hợp lệ?

- `LoaderTarget.class` đã được `javac` tạo; chương trình chỉ **đọc byte của chính file đó**.
- `ByteLoader.define` gọi **`ClassLoader.defineClass`**. Mỗi `ByteLoader` object định nghĩa riêng class cùng binary name `LoaderTarget`.
- `a.getName()` và `b.getName()` giống nhau; nhưng `a != b` và defining class loaders khác nhau.
- `Array.newInstance(a, 0)` và `Array.newInstance(b, 0)` tạo **hai array objects rỗng**, đồng thời cho phép JVM nhận diện array classes tương ứng `LoaderTarget[]` theo hai component types độc lập.
- Vì `a` và `b` khác runtime types, `arrayA` và `arrayB` cũng khác runtime types, dù khi chỉ in tên của chúng có thể trông giống nhau.
- Code cố tình dùng `defineClass` trực tiếp để minh họa **defining loader**; một custom loader hoàn chỉnh phục vụ production còn cần chính sách nạp, đồng bộ, delegation và kiểm tra bảo mật/nguồn bytes phù hợp.

**Giới hạn mô phỏng:** ví dụ không yêu cầu instantiate `LoaderTarget` của loader A hoặc B. Nó chỉ cần định nghĩa class và tạo array classes; vì thế không phụ thuộc việc gọi constructor hoặc class initialization của `LoaderTarget`. Từ việc có `Class` object không suy ra static initializer đã chạy.

## 11. Reflection: thao tác trên runtime types thay vì instance

Một số API `Class` và `java.lang.reflect.Array` giúp quan sát/điều khiển thông tin kiểu:

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `obj.getClass()` | Runtime class của instance | NPE nếu `obj == null` |
| `Class.getName()` | Tên runtime/binary name hoặc dạng array name | Nested class có thể có `$`, array có `[` |
| `Class.getTypeName()` | Tên kiểu dễ đọc hơn cho array | Không phải lời bảo đảm luôn trả source literal |
| `Class.isArray()` | Class mirror có mô tả array type? | `int[].class.isArray()` là `true` |
| `Class.getComponentType()` | Kiểu thành phần của một chiều | Trả `null` nếu không phải array |
| `Class.isPrimitive()` | Primitive/void marker theo API | `Integer.class` là wrapper, không primitive |
| `Class.isInterface()` | Runtime interface type? | Mảng không phải interface |
| `Class.getSuperclass()` | Superclass của class phù hợp | `Object`, primitive, interface có quy tắc khác; array là `Object` |
| `Class.getClassLoader()` | Loader định nghĩa class theo reflection | `null` có thể biểu thị bootstrap/primitive |
| `Class.isInstance(value)` | Kiểm tra object có là instance của runtime type? | Với `null` luôn trả `false` |
| `Class.isAssignableFrom(other)` | Kiểm tra quan hệ gán kiểu từ `other` vào receiver | **Chiều của phép kiểm tra quan trọng** |
| `Array.newInstance(component, n)` | Tạo array instance runtime từ một component `Class` | Kích thước âm gây `NegativeArraySizeException` |
| `Array.getLength(array)` | Lấy độ dài của array object | Đối số phải là array object hợp lệ |

Phân biệt `isInstance` và `isAssignableFrom`:

```java
System.out.println(Number.class.isInstance(Integer.valueOf(3))); // true
System.out.println(Number.class.isAssignableFrom(Integer.class)); // true
System.out.println(Integer.class.isAssignableFrom(Number.class)); // false
```

`isInstance` xét **object đang tồn tại**, còn `isAssignableFrom` xét **quan hệ giữa runtime types**. Cả hai chịu ảnh hưởng identity của defining loader; không chỉ xét chuỗi name.

### Reflection và generics

`Class<List>` không mã hóa riêng một runtime class cho `List<String>` và `List<Integer>` theo generic type arguments. Java generics thông thường dùng **type erasure**; metadata generic có thể được giữ trong `Signature` và đọc qua API như `Field.getGenericType()`, nhưng **không tạo một `Class` object mới cho mỗi tham số kiểu**.

```java
java.util.List<String> words = java.util.List.of("a");
java.util.List<Integer> numbers = java.util.List.of(1);
System.out.println(words.getClass() == numbers.getClass());
```

Trong ví dụ dùng `List.of`, runtime factory implementation cụ thể có thể thay đổi theo JDK; do đó **không nên dùng hai instance trả về từ factory để khẳng định mọi `List<T>` đều có cùng implementation class**. Quy tắc cần ghi nhớ là generic type arguments thông thường **không tách thành runtime classes riêng theo tham số `T`**.

Khi cần tạo mảng generic bằng reflection, nhận một `Class<T>` component rõ ràng và cân nhắc cast kiểu có kiểm tra; không cố gọi `new T[10]` trong generic method nếu Java type system không cho phép.

## 12. Quan sát bytecode và mối liên hệ với ClassFile

Sau khi biên dịch `ClassMirrorDemo.java`, dùng:

```bash
javap -c -p ClassMirrorDemo
javap -verbose ClassMirrorDemo
```

Những mẫu hữu ích để tìm:

| Java source | Bytecode thường thấy | Ý nghĩa |
| --- | --- | --- |
| `Item.class` | `ldc` tham chiếu mục kiểu trong constant pool | Lấy runtime `Class` mirror của kiểu |
| `first.getClass()` | `invokevirtual java/lang/Object.getClass:()Ljava/lang/Class;` | Lấy runtime class của object |
| `new Item()` | `new`, `dup`, `invokespecial <init>` | Tạo object và thực thi constructor |
| `new int[3]` | `newarray int` | Tạo array object primitive |
| `new Item[1]` | `anewarray` | Tạo array object reference |
| `numbers.length` | `arraylength` | Đẩy độ dài `int` lên operand stack |
| `values[index]` / `values[index] = ...` | `*aload` / `*astore` thích hợp | Đọc/ghi phần tử và kiểm tra runtime liên quan |
| `String[][].class` | `ldc` tham chiếu array class | Lấy mirror của runtime array type |

`ldc` với class literal dùng symbolic reference trong constant pool, nhưng **nó không đồng nghĩa gọi constructor `java.lang.Class()` từ mã Java**. JVM quản lý việc lấy/duy trì Class mirror cho runtime type theo các quy tắc nền tảng của VM.

Bytecode `newarray`, `anewarray`, `multianewarray` thực hiện cấp phát array instances và tương tác với array classes; chúng **không cần bytecode constructor `<init>` của class array** giống một class do lập trình viên khai báo. Array có các quy tắc đặc biệt của VM.

**`javap -c` in bytecode lưu trong class file**, không phải mã máy JIT của CPU. Các instruction, offset và thứ tự constant pool có thể khác giữa các compiler/version mà vẫn tuân thủ cùng ngữ nghĩa.

## 13. Các lỗi và cách xác định đúng tầng

| Dấu hiệu | Nguyên nhân cần kiểm tra |
| --- | --- |
| `ClassNotFoundException` khi `Class.forName(...)` | Tên/binary name sai, classpath/module path/loader không tìm thấy định nghĩa |
| `NoClassDefFoundError` | Runtime không thể nạp định nghĩa cần thiết, hoặc class đã lỗi initialization trước đó; không tự gộp mọi nguyên nhân thành thiếu file |
| `LinkageError` hoặc loader constraint violation | Định danh/quan hệ giữa các class và loaders không nhất quán, hoặc class metadata không tương thích |
| `ClassCastException` dù hai tên class in ra giống nhau | Kiểm tra **defining class loader**, không chỉ `getName()` |
| `ArrayStoreException` khi ghi qua `Object[]` | Runtime component type của mảng có thể hẹp hơn kiểu tĩnh `Object` |
| `NegativeArraySizeException` | Kích thước array tạo ra âm |
| `ArrayIndexOutOfBoundsException` | Chỉ số truy cập không thuộc `0 .. length-1` |
| `NullPointerException` khi dùng `getClass()` hoặc `array.length` | Reference là `null` |
| Lấy `Class` được nhưng không thấy log static initializer | Class loading/lấy mirror **không** luôn gây class initialization |
| `Class.forName("java.lang.String[]")` không tìm thấy array type | Dùng array name theo dạng `[Ljava.lang.String;` hoặc class literal `String[].class` |

Đừng dùng `==` giữa **hai instances** để thay cho `==` giữa **hai Class mirrors**; chúng trả lời hai câu hỏi khác nhau. Tương tự, định danh object và định danh class đều khác với so sánh hai chuỗi tên.

## 14. Bài tập và câu hỏi tự kiểm tra

**Bài 1 — Một runtime type, nhiều instances.**  
Cho `String a = new String("x"); String b = new String("y");`. `a == b` và `a.getClass() == b.getClass()` trả gì?  
**Đáp án:** `false` và `true`. Hai instances khác nhau nhưng cùng runtime class.

**Bài 2 — Mảng của mảng.**  
`String[][].class.getComponentType()` có bằng `String.class` không?  
**Đáp án:** **Không**; bằng `String[].class`. Mỗi `getComponentType()` bỏ **một chiều**.

**Bài 3 — Mảng có chiều dài khác nhau.**  
`new int[2].getClass() == new int[9].getClass()` có đúng không?  
**Đáp án:** **Đúng** (nếu viết trong biểu thức Java hợp lệ với dấu ngoặc thích hợp); cả hai cùng kiểu `int[]`; độ dài thuộc từng instance, không thuộc runtime class.

**Bài 4 — Class loader identity.**  
Vì sao `Class<?> a` và `Class<?> b` có cùng `getName()` mà `a == b` lại `false`?  
**Đáp án:** Có thể là **hai định nghĩa cùng binary name từ defining loaders khác nhau**. So sánh `getClassLoader()`, xem đường nạp và quan hệ delegation.

**Bài 5 — Array initialization.**  
`new Target[5]` có gọi constructor `Target()` 5 lần không?  
**Đáp án:** **Không lần nào**; nó tạo mảng chứa 5 reference mặc định `null`. Tạo object `Target` cần hoạt động riêng như `new Target()`.

**Bài 6 — Active use.**  
`Target.class` và `Class.forName("Target", true, loader)` có hiệu ứng initialization như nhau?  
**Đáp án:** **Không**; class literal không tự bắt buộc chạy static initializer; `Class.forName` với `initialize=true` yêu cầu initialization theo quy tắc tương ứng.

**Bài 7 — Primitive Class mirror.**  
`int.class == Integer.class` có đúng không?  
**Đáp án:** **Sai**. Primitive `int` và wrapper class `Integer` là hai kiểu khác nhau.

**Bài 8 — Kế thừa của array.**  
`Object.class.isAssignableFrom(int[].class)` và `Object[].class.isAssignableFrom(int[].class)` lần lượt là gì?  
**Đáp án:** `true` và `false`. Mọi array là Object, nhưng primitive array không là Object[].

**Bài 9 — Runtime store check.**  
Tại sao `Object[] a = new String[1]; a[0] = new Object();` biên dịch nhưng thất bại?  
**Đáp án:** Kiểu tĩnh `Object[]` cho phép gán Object; runtime array object có component type `String`, nên `aastore` ném `ArrayStoreException`.

**Bài 10 — ClassFile.**  
Có bắt buộc phải tìm thấy tệp `int[].class` trên disk để `int[].class` hoạt động không?  
**Đáp án:** **Không**. JVM tạo/nhận diện array class theo quy tắc riêng. Tệp ClassFile thông thường mô tả class/interface được định nghĩa, không phải class array do JVM tạo.

**Bài 11 — `Class.forName` tên array.**  
Tên runtime thích hợp khi tìm `String[]` là `java.lang.String[]` hay `[Ljava.lang.String;`?  
**Đáp án:** Theo cú pháp array name của `Class.getName()`, dùng **`[Ljava.lang.String;`**.

**Bài 12 — Hai lớp cùng tên trong plugin.**  
Nếu loader A và loader B thật sự định nghĩa hai class `com.example.Plugin` độc lập, thì `com.example.Plugin[]` tương ứng có chắc là một array class không?  
**Đáp án:** **Không**. Array classes được định danh qua component type tương ứng; component runtime types khác nhau tạo các array types khác nhau.

### Kết luận

Khi debug Java class loading, luôn phân biệt **object instance**, **Class mirror**, **runtime type identity**, **defining loader** và **array class**. `Class<?> ` là cổng quan sát kiểu runtime, không phải một bản sao object. Kiểu class/interface mang định danh gắn với tên và defining loader. Array classes do JVM tạo theo component type; array objects có length và dữ liệu riêng, trong khi array Class object mô tả **kiểu**, không mô tả độ dài của một instance cụ thể. Class literal và tạo array cũng không buộc component class phải chạy static initializer.

Để đọc quy tắc chi tiết, đối chiếu *The Java Virtual Machine Specification*, mục **§5.3 (Creation of Classes and Interfaces, trong đó §5.3.3 là Creation of Array Classes)**, và *Java Language Specification* mục **§10 (Arrays)**, **§12.4 (Initialization)**. API `java.lang.Class` và `java.lang.reflect.Array` cung cấp phép thử trực tiếp cho những khái niệm trong bài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-007">Nạp lớp theo yêu cầu qua Class.forName và ClassLoader.loadClass</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp lớp theo yêu cầu qua `Class.forName` và `ClassLoader.loadClass`

Java cho phép chương trình biết **tên lớp dưới dạng chuỗi** rồi yêu cầu JVM tìm và nạp lớp đó khi cần. Hai API rất phổ biến là `Class.forName(...)` và `ClassLoader.loadClass(...)`. Chúng có thể trả về cùng một đối tượng `Class<?>`, nhưng **không đồng nghĩa về thời điểm khởi tạo lớp (initialization)**, nguồn class loader mặc định, cách xử lý lỗi và mức kiểm soát vòng đời.

Đây là kiến thức nền cho reflection, driver JDBC, cơ chế plugin, Dependency Injection container, dynamic discovery và điều tra các lỗi như `ClassNotFoundException`, `NoClassDefFoundError` hoặc `ExceptionInInitializerError`. Bài lấy **Java SE 17** làm mốc, phân biệt hành vi được API/JLS/JVMS quy định với quyết định riêng của compiler/JVM/class loader.

## 1. Ba mốc khác nhau trước khi dùng một lớp

Hãy đọc `Class<?>` như **runtime representation của kiểu**, không phải dấu hiệu `static {}` đã chạy. JVM phân biệt **loading** (tạo class representation), **linking** (verification, preparation, resolution, trong đó một phần resolution có thể trì hoãn) và **initialization** (thực hiện static field initializers/`<clinit>` theo quy tắc).

Đối với bài này, câu hỏi không phải mô tả lại toàn bộ JVM lifecycle mà là **hai API lấy `Class<?>` có tự yêu cầu initialization hay không**. `Class.forName(name)` thường yêu cầu initialization; `Class.forName(name, false, loader)` và `loader.loadClass(name)` không chủ động làm điều đó. Dù `initialize=false`, lỗi loading/linking vẫn có thể xảy ra; chỉ có `Class<?>` chưa đồng nghĩa đã tạo instance hay chạy plugin lifecycle.

## 2. Hai API: giống tên lớp, khác ý nghĩa mặc định

### `Class.forName(String className)`

```java
Class<?> type = Class.forName("com.example.Plugin");
```

Đây là **đoạn minh họa**; lớp `com.example.Plugin` giả định có trong classpath/module path phù hợp. Overload một tham số:

- Tìm lớp/interface theo **binary name** được cung cấp.
- Dùng **defining class loader của lớp gọi `Class.forName`** theo quy tắc caller-sensitive, không mặc định lấy `Thread.currentThread().getContextClassLoader()`.
- **Yêu cầu initialization** nếu kiểu chưa được khởi tạo và là lớp/interface phù hợp.
- Có thể ném **`ClassNotFoundException`** (checked) khi lớp không tìm thấy, cũng như các lỗi liên kết hoặc khởi tạo thích hợp.

Nếu chỉ muốn lấy metadata mà **không chủ động khởi tạo**, overload một tham số không phải lựa chọn tốt. Dùng overload có cờ `initialize=false` khi thích hợp.

### `Class.forName(String name, boolean initialize, ClassLoader loader)`

```java
ClassLoader loader = SomeCaller.class.getClassLoader();
Class<?> metadataOnly = Class.forName("com.example.Plugin", false, loader);
Class<?> ready = Class.forName("com.example.Plugin", true, loader);
```

Đây cũng là **đoạn minh họa**, không phải chương trình độc lập; `SomeCaller` và `Plugin` là tên giả định.

- `name`: binary name của lớp/interface cần tìm, hoặc dạng tên mảng mà API cho phép.
- `initialize=false`: yêu cầu nạp và liên kết nhưng **không chủ động khởi tạo** lớp.
- `initialize=true`: bảo đảm initialization được thực hiện nếu cần theo quy tắc VM.
- `loader`: chọn loader cụ thể; `null` biểu thị **bootstrap class loader** cho overload này.
- Class đã được khởi tạo trước đó sẽ **không chạy lại static initializers** chỉ vì gọi `forName` lần nữa.

Lưu ý: một lời gọi `forName(name, false, loader)` không “khóa” lớp ở trạng thái chưa khởi tạo. **Một active use khác** trong ứng dụng có thể làm lớp khởi tạo trước khi bạn kiểm tra tiếp. Cờ `false` chỉ nói rằng lời gọi hiện tại **không yêu cầu** initialization.

### `ClassLoader.loadClass(String name)`

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> type = loader.loadClass("com.example.Plugin");
```

**Đoạn minh họa:** giả sử context class loader không phải `null` và biết tìm lớp đang cần.

- API này yêu cầu **load** một lớp có binary name đã cho qua chính loader được chọn.
- Phần triển khai `ClassLoader.loadClass(name)` mặc định tương ứng cơ chế `loadClass(name, false)` của class loader: **không yêu cầu resolve/link toàn bộ ngay**, và **không yêu cầu initialization**.
- Loader đã được tùy biến có thể quyết định nguồn bytecode và chính sách delegation; logic nạp có thể khác nhau giữa các loader.
- Việc trả `Class<?>` không tự chạy static block của target class chỉ vì code sau đó định tạo instance hoặc dùng reflection.

Có overload protected `loadClass(String name, boolean resolve)` dùng trong cách cài đặt class loader. **`resolve=true` không có nghĩa `initialize=true`**. Resolution/linking khác initialization; hơn nữa overload đó là **protected**, không phải cờ mà caller thông thường đưa vào public `loadClass(String)`.

### Bảng đối chiếu nhanh

| API | Loader dùng mặc định | Yêu cầu khởi tạo? | Chọn loader tường minh? | Kết quả |
| --- | --- | --- | --- | --- |
| `Class.forName(name)` | Defining loader của caller | **Có** | Không trong overload này | `Class<?>` |
| `Class.forName(name, true, loader)` | Loader truyền vào | **Có** | Có | `Class<?>` |
| `Class.forName(name, false, loader)` | Loader truyền vào | **Không** | Có | `Class<?>` |
| `loader.loadClass(name)` | Chính `loader` được gọi | **Không** theo đường mặc định | Có, qua instance loader | `Class<?>` |

**Quy tắc thực hành:** Nếu phải **chắc chắn lớp được initialize**, dùng `Class.forName(name, true, loader)` hoặc một active use được đặc tả; nếu chỉ cần `Class` để đọc metadata, chọn `Class.forName(name, false, loader)` hoặc `loader.loadClass(name)` tùy nhu cầu linking/loader và xử lý tác dụng phụ.

## 3. Chương trình Java 17: nhìn chính xác thứ tự initialization

Tạo file **`LazyLoadLab.java`**:

```java
public class LazyLoadLab {

    public static class Passive {
        static {
            System.out.println("Passive.<clinit>");
        }

        public static void ping() {
            System.out.println("Passive.ping");
        }
    }

    public static class Active {
        static {
            System.out.println("Active.<clinit>");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = LazyLoadLab.class.getClassLoader();
        String passiveName = LazyLoadLab.class.getName() + "$Passive";
        String activeName = LazyLoadLab.class.getName() + "$Active";

        System.out.println("1: loadClass");
        Class<?> passive = loader.loadClass(passiveName);

        System.out.println("2: forName(false)");
        Class<?> same = Class.forName(passiveName, false, loader);
        System.out.println("same=" + (passive == same));

        System.out.println("3: invoke static method");
        passive.getDeclaredMethod("ping").invoke(null);

        System.out.println("4: forName(true) again");
        Class.forName(passiveName, true, loader);

        System.out.println("5: forName(true) on another class");
        Class.forName(activeName, true, loader);

        System.out.println("6: done");
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac --release 17 LazyLoadLab.java
java LazyLoadLab
```

**Stdout chính xác trong chương trình này**:

```text
1: loadClass
2: forName(false)
same=true
3: invoke static method
Passive.<clinit>
Passive.ping
4: forName(true) again
5: forName(true) on another class
Active.<clinit>
6: done
```

**Diễn giải:**

- `loader.loadClass(passiveName)` tìm lớp và trả đối tượng `Class` mà **không in `Passive.<clinit>`**.
- `Class.forName(passiveName, false, loader)` không yêu cầu khởi tạo; trong cùng loader, hai lời gọi xác định cùng một lớp, nên `passive == same` là `true`.
- Lấy method bằng `getDeclaredMethod` để chuẩn bị reflection **chưa phải lời gọi method**; nhưng `Method.invoke(null)` đối với method static là active use và kích hoạt initialization của `Passive` trước khi chạy `ping`.
- `Class.forName(passiveName, true, loader)` tiếp theo không chạy initializer lần hai vì class đã được khởi tạo.
- `Class.forName(activeName, true, loader)` làm `Active` khởi tạo và in đúng một dòng từ static block.

Dấu `<clinit>` trong output là chuỗi do ví dụ chủ động in, không phải tên một method Java có thể viết bằng source và gọi trực tiếp. Compiler có thể tạo method JVM `<clinit>` chứa static initializer code.

### Tại sao không dùng `new Passive()` để kiểm tra loading?

`new Passive()` là một **active use** khiến `Passive` phải được initialize nếu chưa được khởi tạo. Nó không thể hiện rõ sự khác nhau giữa `loadClass` và `forName(false)`. Với phép thử về loading, tránh vô tình tạo instance, truy cập static field không phải compile-time constant hoặc gọi static method trước thời điểm quan sát.

## 4. Khi nào JVM khởi tạo lớp?

JLS §12.4.1 mô tả những thời điểm class/interface phải được initialize trước lần active use tương ứng. Những trường hợp hay gặp:

- Tạo instance của lớp bằng `new`.
- Gọi một **static method** được khai báo ở lớp/interface có liên quan.
- Đọc hoặc ghi một static field **không phải constant variable** của lớp/interface theo quy tắc Java.
- Một số hoạt động reflection yêu cầu initialization.
- Gọi `Class.forName(name, true, loader)`.

Ví dụ **không nhất thiết** kích hoạt initialization của lớp thành phần:

- Lấy class literal bằng `SomeType.class`.
- Gọi `ClassLoader.loadClass(name)`.
- Gọi `Class.forName(name, false, loader)`.
- Tạo mảng `new SomeType[10]` (không khởi tạo component class chỉ vì tạo array).
- Đọc một `static final` **constant variable** thích hợp đã được compiler đưa vào nơi sử dụng.

### Ví dụ kiểm chứng constant và class literal

File **`InitializationTriggers.java`**:

```java
public class InitializationTriggers {

    static class Values {
        static final int MAGIC = 42;
        static final int COMPUTED = compute();

        static int compute() {
            System.out.println("Values.<clinit>");
            return 99;
        }
    }

    public static void main(String[] args) {
        System.out.println("constant=" + Values.MAGIC);

        Class<?> type = Values.class;
        System.out.println("literal=" + type.getSimpleName());

        System.out.println("computed=" + Values.COMPUTED);
    }
}
```

```bash
javac --release 17 InitializationTriggers.java
java InitializationTriggers
```

**Stdout**:

```text
constant=42
literal=Values
Values.<clinit>
computed=99
```

`MAGIC` là `static final int` được khởi tạo bằng một constant expression; việc đọc nó từ caller thường được compiler inlined và **không làm `Values` initialize**. Biểu thức `Values.class` trả `Class<?>` cũng không khởi tạo lớp. `COMPUTED` không phải constant variable vì gọi một method, nên truy cập `Values.COMPUTED` kích hoạt initialization; `compute()` in dòng giữa và trả về `99`.

Đừng tổng quát hóa thành “mọi `static final` đều không khởi tạo class”: ví dụ `static final Object O = new Object();` hoặc `static final int N = compute();` không phải compile-time constant variable.

### Khởi tạo có thể liên quan tới superclass và interface

- Khi khởi tạo một class, JVM bảo đảm các **superclass** cần thiết được initialize trước; còn có các quy tắc đặc biệt đối với superinterfaces khai báo default methods.
- Nạp một subclass không tự đồng nghĩa toàn bộ hierarchy đã chạy static initializers.
- Khởi tạo một interface không có nghĩa mọi superinterface của nó đều được initialize một cách đệ quy giống class.
- Initialization có cơ chế đồng bộ theo class/interface để tránh hai thread cùng thực thi `<clinit>`; những thread khác có thể phải đợi khi một thread đang khởi tạo.

Điểm quan trọng trong debug: thứ tự log static initializer có thể đến từ quy tắc phụ thuộc kiểu, **không chỉ** từ dòng `forName` nằm ở đâu.

## 5. Binary name, canonical name và dạng tên mảng

Các API nhận tên lớp bằng chuỗi thường cần **binary name** theo quy tắc của JLS, **không đơn giản tên hiển thị ở source**:

| Kiểu | Tên đáng dùng để nạp theo string | Lưu ý |
| --- | --- | --- |
| Top-level class `java.lang.String` | `java.lang.String` | Tên package/class với dấu chấm |
| Nested class `Outer.Inner` | `Outer$Inner` | Binary name có ký tự `$` cho nested class |
| Một lớp trong package `demo.` | `demo.Helper` | Package phải khớp tên binary trong class file |
| Mảng String | `[Ljava.lang.String;` | Tên theo `Class.getName()`/descriptor-like syntax của array |
| Mảng int | `[I` | Dạng tên array của primitive component |
| Primitive `int` | Không dùng `Class.forName("int")` như cách lấy primitive class | Dùng `int.class` hoặc `Integer.TYPE` |

Với `Class.forName`, tên `java.lang.String[]` theo dạng source không phải tên array đúng; `[Ljava.lang.String;` mới là dạng hỗ trợ. Bản thân mảng là một class loại array; **tạo hoặc nạp array class không mặc nhiên initialize component class**.

Đừng ghép tên nested class tùy ý bằng cách thay mọi dấu chấm thành `$`. Dùng `SomeNested.class.getName()` nếu đã có class literal, hoặc tài liệu về binary name của chính kiểu đó.

### Chương trình quan sát tên và dạng mảng

Tạo file **`NameForms.java`**:

```java
public class NameForms {
    public static class Nested {
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = NameForms.class.getClassLoader();
        Class<?> nested = loader.loadClass(NameForms.class.getName() + "$Nested");

        System.out.println("nested=" + nested.getName());
        System.out.println("simple=" + nested.getSimpleName());
        System.out.println("array=" + Class.forName("[Ljava.lang.String;").isArray());
        System.out.println("intArray=" + Class.forName("[I").isArray());
        System.out.println("primitive=" + int.class.getName());
    }
}
```

```bash
javac --release 17 NameForms.java
java NameForms
```

**Stdout**:

```text
nested=NameForms$Nested
simple=Nested
array=true
intArray=true
primitive=int
```

Ví dụ để class ở **default package** nên binary name của nested class có dạng `NameForms$Nested`. Nếu đặt vào package `demo`, phần prefix sẽ là `demo.NameForms$Nested`.

## 6. Định danh lớp: cùng binary name chưa chắc là cùng một lớp JVM

Một class/interface runtime được định danh bởi **binary name kết hợp với defining class loader**. Hệ quả:

- Hai đối tượng `Class<?>` của một lớp do **cùng defining loader** định nghĩa sẽ biểu diễn cùng runtime class; gọi lại `loadClass`/`forName` không tự sinh “phiên bản mới”.
- Hai defining class loader **khác nhau** có thể định nghĩa hai lớp có **cùng binary name** nhưng JVM xem đó là **hai kiểu khác nhau**.
- Vì vậy hai object trông “đều là `plugin.Api`” vẫn có thể không cast được cho nhau; method lookup, interface compatibility và identity của class không dựa vào tên chuỗi riêng lẻ.
- Một loader **initiating** việc nạp có thể nhận class thực sự do loader cha **defining**; không được đồng nhất “loader tôi gọi `loadClass` trên đó” với “loader thực sự định nghĩa lớp” trong mọi trường hợp.

Có thể quan sát lớp đã trả về bằng:

```java
Class<?> type = Class.forName(name, false, loader);
System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Đây là **đoạn minh họa** với `name` và `loader` giả định đã được xác định. `type.getClassLoader()` trả về defining loader; **`null`** thường đại diện cho bootstrap loader. Tránh suy rằng một lớp thuộc module JDK bắt buộc có loader là `null`, vì Java 9+ còn có platform class loader và app class loader.

### Parent delegation là mô hình phổ biến, không phải nguyên lý “chỉ tồn tại một loader”

`ClassLoader.loadClass` theo triển khai mặc định thường:

1. Kiểm tra class đã được loader nạp trước đó chưa (`findLoadedClass`).
2. Nếu chưa, hỏi parent loader trước (hoặc bootstrap loader nếu không có parent).
3. Nếu parent không tìm thấy, dùng `findClass` của loader hiện tại.
4. Nếu caller yêu cầu resolve ở overload tương ứng, có thể gọi `resolveClass`.

Đây là **parent-first delegation** phổ biến. Tuy nhiên các class loader tùy biến có thể thay đổi đường tìm kiếm, áp dụng child-first cho một số package hoặc giới hạn không gian tên. Việc có thể viết loader tùy biến không có nghĩa được tùy tiện định nghĩa trùng các package bị hệ thống bảo vệ.

Ví dụ loader riêng có thể dùng để tách hai phiên bản thư viện/plugin. Cần quyết định rõ lớp API chia sẻ nào phải được parent load để plugin object có thể cast sang interface chung; nếu cả plugin và host cùng tải một bản sao interface từ hai loader khác nhau, cast có thể thất bại dù tên binary trùng.

## 7. Ví dụ về lỗi loader identity thường gặp trong plugin

Giả sử host có interface:

```java
package api;

public interface Plugin {
    String name();
}
```

Và plugin class:

```java
package impl;

public class PluginImpl implements api.Plugin {
    @Override
    public String name() {
        return "demo";
    }
}
```

**Hai snippet là các file Java riêng biệt theo package**, không phải một file tự chạy. Để host cast được `PluginImpl` thành `api.Plugin`, interface `api.Plugin` mà plugin implements phải tương ứng **cùng runtime class** với `api.Plugin` phía host. Cách triển khai phổ biến là đặt API chung trong loader cha và cho plugin loader delegate việc nạp API ấy.

Nếu plugin loader tự định nghĩa một bản `api.Plugin` khác, hai interface đều có binary name `api.Plugin` nhưng thuộc hai defining loader: về mặt runtime **không cùng kiểu**. Khi đó `ClassCastException` hoặc lỗi liên kết/loader constraints có thể xuất hiện; không sửa bằng cách đổi ép kiểu trong Java source.

### Không nhầm `Class<?>` với object plugin

`Class.forName("impl.PluginImpl", true, loader)` trả về **đối tượng `Class`**, không phải object `PluginImpl` đã được tạo. Để tạo instance, có thể dùng reflection với constructor phù hợp và kiểm tra type:

```java
Class<?> candidate = Class.forName("impl.PluginImpl", true, loader);
Object instance = candidate.getDeclaredConstructor().newInstance();
api.Plugin plugin = api.Plugin.class.cast(instance);
```

Đây là **đoạn minh họa**; giả định có public no-arg constructor, class được truy cập hợp lệ theo module/access rules, loader nhất quán cho API chung và exception đã được xử lý. Không mặc định `newInstance()` tạo được object nếu class abstract, không có constructor phù hợp hoặc module không cho reflection.

**`Class.forName` không phải API dependency injection**: nó chỉ tìm/nạp/initialize class; việc chọn constructor, quản lý lifecycle, inject dependencies, startup/shutdown là trách nhiệm tầng trên.

## 8. Context ClassLoader trong ứng dụng plugin và framework

`Thread.currentThread().getContextClassLoader()` (TCCL) cung cấp một **context loader** mà thư viện/framework có thể chủ động dùng để tìm plugin/providers trong bối cảnh thread hiện tại.

Ví dụ snippet:

```java
ClassLoader context = Thread.currentThread().getContextClassLoader();
if (context == null) {
    throw new IllegalStateException("No context class loader");
}
Class<?> pluginClass = Class.forName("com.example.Plugin", true, context);
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; tên plugin giả định.

Phải phân biệt:

- `Class.forName(name)` **không tự động tra TCCL**; overload một tham số dùng loader liên quan tới **caller**.
- `Class.forName(name, initialize, context)` **đang chủ động chọn** TCCL.
- `context.loadClass(name)` dùng loader context được lấy từ thread, nhưng **không yêu cầu initialize**.
- Các framework như `ServiceLoader` có quy tắc chọn loader riêng theo overload/API; ví dụ `ServiceLoader.load(Service.class)` theo API thường sử dụng TCCL. Phải đọc contract của từng framework, không suy từ `Class.forName`.

TCCL là **mutable per-thread context**. Trong thread pool, một task có thể chạy với context loader do môi trường đặt từ trước; task cần thay đổi nó nên lưu lại loader cũ và phục hồi trong `finally`, tránh rò rỉ ảnh hưởng các task khác. Việc có quyền đặt TCCL hoặc nạp class còn phụ thuộc môi trường bảo mật và policies của ứng dụng.

### Ví dụ khung khôi phục TCCL

```java
Thread current = Thread.currentThread();
ClassLoader original = current.getContextClassLoader();
try {
    current.setContextClassLoader(pluginLoader);
    // Gọi framework sử dụng TCCL để tra provider.
    runDiscovery();
} finally {
    current.setContextClassLoader(original);
}
```

Đây là **fragment** nằm trong method nơi `pluginLoader` và `runDiscovery()` đã tồn tại, không phải chương trình chạy độc lập. Đoạn này không thay đổi defining loader của các class đã load; nó chỉ thay context mà một số framework dùng để tìm class tiếp theo.

## 9. Lỗi và ngoại lệ: không đồng nhất `ClassNotFoundException` với `NoClassDefFoundError`

| Tình huống | Biểu hiện thường gặp | Hướng điều tra |
| --- | --- | --- |
| `forName`/`loadClass` nhận tên lớp không tìm được qua loader đã chọn | **`ClassNotFoundException`** (checked) | Binary name, package, classpath/module path, jar, loader |
| Đã có class tham chiếu đến dependency nhưng JVM không thể định nghĩa/tải dependency khi cần | **`NoClassDefFoundError`** | Xem cause, jar/runtime dependency, name và loader |
| Class initialization chạy static initializer rồi thất bại do exception thích hợp | Có thể là **`ExceptionInInitializerError`** | Tìm exception gốc trong static init, không chỉ đổi loader |
| Class đã bị đánh dấu erroneous sau initialization failure; lần dùng sau | Có thể là **`NoClassDefFoundError`** “Could not initialize class ...” | Tìm **lỗi khởi tạo lần đầu**, không nhầm với file .class biến mất |
| Class file không hợp lệ hoặc bytecode sai | **`ClassFormatError`**, **`VerifyError`** | Kiểm tra format, class major version và instrumentation |
| Runtime JVM quá cũ so với class-file major version | **`UnsupportedClassVersionError`** | Kiểm tra version của class đã compile và runtime |
| Truy cập method/field không còn hợp lệ do binary mismatch | `NoSuchMethodError`, `IllegalAccessError`, `IncompatibleClassChangeError`... | Kiểm tra binary compatibility, loader, package/module |

`ClassNotFoundException` là một checked exception thường được các API nạp lớp thông báo khi **không tìm thấy tên được yêu cầu**. `NoClassDefFoundError` là một `LinkageError`; ngoài thiếu dependency lúc linking, nó còn có thể xảy ra sau **initialization failure**. Không nên tự động coi hai loại là cùng một lỗi rồi bắt/nuốt cả hai.

### Ví dụ minh họa class initialization thất bại

```java
public class InitFailure {
    static class Broken {
        static final int VALUE = compute();

        static int compute() {
            throw new IllegalStateException("startup failed");
        }
    }

    public static void main(String[] args) {
        try {
            Class.forName(InitFailure.class.getName() + "$Broken");
        } catch (ExceptionInInitializerError err) {
            System.out.println("initialization failed");
        }

        try {
            Class.forName(InitFailure.class.getName() + "$Broken");
        } catch (NoClassDefFoundError err) {
            System.out.println("class is erroneous");
        } catch (ClassNotFoundException err) {
            System.out.println("class not found");
        }
    }
}
```

**Đây là file Java 17 chạy độc lập** để minh họa đường lỗi. Biên dịch/chạy:

```bash
javac --release 17 InitFailure.java
java InitFailure
```

**Stdout trong một JVM chạy chương trình độc lập:**

```text
initialization failed
class is erroneous
```

Lần đầu `Class.forName` yêu cầu initialize `Broken`; static initializer thất bại, VM báo `ExceptionInInitializerError` vì lỗi nguồn là một `RuntimeException`. Sau đó cùng class/loader ở trạng thái erroneous, lần khởi tạo tiếp theo có thể báo `NoClassDefFoundError` vì nó không thể được khởi tạo thành công nữa.

Đối với ứng dụng thật, **không nên “retry” bằng cách gọi `forName` lần nữa trong cùng loader** rồi mong initializer chạy lại. Nếu cần recovery, thiết kế lifecycle và xử lý lỗi chủ động; nạp lại bằng một loader hoàn toàn khác cũng có những hệ quả về định danh kiểu và tài nguyên, không phải bản vá mặc định cho initialization bug.

## 10. Reflection: lấy metadata, khởi tạo class và tạo object là ba việc

Phân biệt các thao tác:

| Thao tác | Có yêu cầu tạo instance? | Có yêu cầu initialization? |
| --- | --- | --- |
| `Class.forName(name, false, loader)` | Không | Không |
| `loader.loadClass(name)` | Không | Không theo cơ chế thông thường |
| `type.getName()`, `type.getDeclaredMethods()` | Không | Không chỉ vì tra metadata; việc phân giải kiểu phụ thuộc có thể xảy ra |
| `Class.forName(name, true, loader)` | Không | **Có**, nếu cần |
| `type.getDeclaredConstructor().newInstance()` | **Có** | **Có**, trước khi instance được tạo/constructor chạy |
| `type.getDeclaredMethod("run").invoke(null)` với static method | Không | **Có** trước khi gọi method nếu cần |
| `field.get(null)` với static field thường | Không | Thường là active use và yêu cầu initialization |
| `SomeClass.class` | Không | Không tự khởi tạo |

Reflection không đảm bảo bypass kiểm tra truy cập. Java 9+ module boundaries có thể từ chối truy cập private internals qua `setAccessible(true)` bằng `InaccessibleObjectException`, ngay cả khi class đã tìm thấy. Và `Constructor.newInstance` có thể bao lỗi do constructor thành `InvocationTargetException`; cần xem **cause** để biết nguyên nhân thật.

### Nạp class không đồng nghĩa object “đã sẵn sàng”

Class initialization chạy một lần không có nghĩa mọi service/resource của ứng dụng đã sẵn sàng. Plugin có thể cần constructor injection, cấu hình runtime, kết nối DB hoặc lifecycle callback khác. Ngược lại, dùng `Class.forName(name, true, loader)` chỉ để kiểm tra **lớp có tồn tại hay không** có thể vô tình kích hoạt static initialization tốn thời gian hoặc nguy hiểm.

## 11. Class loader trong Java 9+ modules và các overload khác

Java 9 đưa vào hệ thống module và thêm `Class.forName(Module module, String name)`. Overload này khác với `forName(String, boolean, ClassLoader)`:

- Tìm class theo **tên** trong **module cụ thể** và có thể trả về `null` nếu không tìm thấy lớp thuộc module ấy.
- **Không khởi tạo** lớp chỉ vì lời gọi overload theo `Module`; contract của nó cũng khác về liên kết và kiểm tra visibility.
- `module` không tự động thay thế chính sách class loader do hệ thống module áp dụng; class cũng có defining loader.
- Các module có thể áp dụng **readability/exports/opens** khiến lớp có thể được tìm thấy nhưng những thao tác truy cập cụ thể vẫn bị hạn chế.

Ví dụ dùng API module (Java 17):

```java
public class ModuleLookup {
    public static void main(String[] args) {
        Module module = String.class.getModule();
        Class<?> found = Class.forName(module, "java.lang.String");
        Class<?> missing = Class.forName(module, "java.lang.DoesNotExist");

        System.out.println("found=" + (found != null));
        System.out.println("missing=" + (missing == null));
    }
}
```

```bash
javac --release 17 ModuleLookup.java
java ModuleLookup
```

**Stdout**:

```text
found=true
missing=true
```

Dùng overload này khi mục tiêu của bạn thật sự là **tìm lớp trong một module**, không đơn thuần yêu cầu initialization bằng tên class. Đừng thay thế máy móc `Class.forName(name, true, loader)` bằng `Class.forName(module, name)` rồi mong static initializer vẫn chạy.

## 12. Khi nào nên dùng API nào?

| Mục tiêu | Lựa chọn hay dùng | Lý do |
| --- | --- | --- |
| Kiểm tra/tra một class bằng tên với semantics caller loader và muốn initialization | `Class.forName(name)` | Ngắn, yêu cầu initialization |
| Quét plugin class bằng loader đã biết nhưng **chưa muốn side effect** | `Class.forName(name, false, pluginLoader)` | Có thể kiểm tra metadata trước khi initialize |
| Cần class qua custom class loader đang nắm giữ | `pluginLoader.loadClass(name)` | Dùng cơ chế loader ấy; không initialize mặc định |
| Muốn rõ ràng initialize class qua custom loader | `Class.forName(name, true, pluginLoader)` | Chọn loader và bật initialization một cách tường minh |
| Tạo instance plugin sau khi xác thực class type | `type.getDeclaredConstructor().newInstance()` | Tạo object và kích hoạt initialization nếu cần |
| Framework lookup qua TCCL | `Class.forName(name, ..., tccl)` hoặc API framework thích hợp | Tránh lệ thuộc defining loader của chính framework |
| Tìm lớp trong một Java module cụ thể | `Class.forName(module, name)` | Nhắm đúng module; không tự initialize |
| Chỉ cần class literal tại compile time | `SomeType.class` | Không cần lookup bằng chuỗi; compiler kiểm tra tên kiểu |

**Ưu tiên type-safe code** khi class đã biết ở compile time. Nạp theo tên string thích hợp cho cấu hình và plugin, nhưng làm mất kiểm tra tên/lớp ở compile time, dễ phát sinh vấn đề loader, version và lỗi runtime.

## 13. Những điểm thiết kế plugin thực tế

### Tách việc khám phá và khởi chạy

Một quy trình hợp lý:

1. Đọc cấu hình hoặc danh sách tên class plugin.
2. Chọn **đúng loader** theo kiến trúc của host, không lấy ngẫu nhiên system/app loader.
3. Nạp **chưa khởi tạo** nếu cần quét metadata: `Class.forName(name, false, pluginLoader)`.
4. Kiểm tra class implement/extends API được host dùng: `Plugin.class.isAssignableFrom(type)`.
5. Kiểm tra điều kiện khởi tạo như modifier, constructor và quyền truy cập.
6. Chủ động khởi tạo/tạo object khi ứng dụng sẵn sàng; xử lý lỗi trong phạm vi lifecycle.
7. Khởi động plugin và giải phóng resources bằng hook/framework lifecycle rõ ràng.
8. Khi unload/reload, quản lý class loader, thread, static references, TCCL và caches để tránh class loader leak.

Một mẫu thiết kế tối thiểu:

```java
Class<?> type = Class.forName(className, false, pluginLoader);
if (!Plugin.class.isAssignableFrom(type)) {
    throw new IllegalArgumentException("Not a Plugin: " + className);
}
Class<? extends Plugin> pluginType = type.asSubclass(Plugin.class);
Plugin plugin = pluginType.getDeclaredConstructor().newInstance();
plugin.start();
```

**Đây là fragment minh họa**, giả sử `Plugin` là API của host, `className`/`pluginLoader` hợp lệ, plugin có public no-arg constructor và `start()` được định nghĩa. Trong code thật cần xử lý checked exceptions, trạng thái khởi tạo thất bại, shutdown và module/access policies. `asSubclass` kiểm tra quan hệ kiểu runtime; nó không thể cứu tình huống API `Plugin` đã bị các loader khác nhau định nghĩa thành hai kiểu khác nhau.

### Đừng lạm dụng static initializer để đăng ký mọi plugin

Legacy code đôi khi gọi `Class.forName("...Driver")` chỉ để static initializer tự đăng ký provider. Cách này giải thích một số mẫu JDBC cũ, nhưng không phải cơ chế plugin phổ quát. Java hiện đại có `ServiceLoader` và các cơ chế framework riêng; chọn chúng khi cần discovery, dependency management và lifecycle chuẩn. Không dùng `Class.forName` như một “lệnh cài plugin” bất kể thư viện.

## 14. Các lỗi hiểu sai thường gặp

| Nhầm lẫn | Hiểu đúng |
| --- | --- |
| “`loadClass` đồng nghĩa static block chạy” | `loadClass(name)` không yêu cầu initialization |
| “`Class.forName(name)` chỉ load, không làm gì thêm” | Overload một tham số **yêu cầu initialization** |
| “Cờ `resolve=true` là cờ `initialize=true`” | Linking/resolution khác initialization |
| “`Class.forName(name)` luôn dùng TCCL” | Nó dùng loader của caller theo semantics caller-sensitive; TCCL phải được chọn tường minh nếu cần |
| “Hai class cùng tên nghĩa là cùng kiểu” | JVM còn xét **defining class loader** |
| “`Class.forName` trả về instance plugin” | Nó trả `Class<?>`; tạo object là bước riêng |
| “`SomeClass.class` luôn khởi tạo class” | Class literal không tự yêu cầu initialization |
| “Đọc mọi `static final` đều không khởi tạo class” | Chỉ những trường hợp constant variable phù hợp được xử lý theo quy tắc compile-time constant |
| “Lần nạp đầu tiên sẽ chạy `<clinit>`” | Loading và initialization là hai giai đoạn |
| “Nếu class init lỗi, `forName` lần tiếp sẽ thử lại nguyên vẹn” | Class có thể trở thành erroneous trong cùng loader; lần sau báo `NoClassDefFoundError` |
| “`ClassNotFoundException` và `NoClassDefFoundError` là cùng nguyên nhân” | Một là checked exception cho lookup, một là linkage error với nhiều nguyên nhân, kể cả prior init failure |
| “`Class.forName("int")` lấy được primitive class” | Dùng `int.class`/`Integer.TYPE`; tên primitive không được xử lý như binary class name thường |
| “`Outer.Inner` luôn là chuỗi dùng cho nested class” | Binary name thường có `Outer$Inner` |
| “`Class.forName(module, name)` luôn khởi tạo class” | Overload dùng `Module` **không** yêu cầu initialization |
| “Đã chọn một class loader thì chắc chắn nó định nghĩa class” | Parent delegation có thể trả class do loader khác định nghĩa |

## 15. Quy trình debug nạp lớp thất bại

Khi lỗi xảy ra, lần lượt kiểm tra:

1. **Tên**: giá trị string truyền vào là binary name chính xác chưa? Nested class dùng `$`? Array dùng đúng tên? Không vô tình truyền source-style `String[]`?
2. **API và cờ**: code dùng `forName(name)`, `forName(name, false, loader)` hay `loader.loadClass(name)`? Có kích hoạt initialization ngoài ý muốn không?
3. **Loader thực**: in `type.getClassLoader()` nếu đã nạp được; phân biệt app/platform/bootstrap/plugin loader và TCCL.
4. **Classpath/module path**: jar thực sự nằm đâu? Có bản duplicate? Manifest, class path, module exports/opens và launch mode có phù hợp?
5. **Thời điểm failure**: lỗi xảy ra ở loading, verification/linking, initialization hay reflection/constructor?
6. **Nested cause**: với `ExceptionInInitializerError` xem cause; với `NoClassDefFoundError` kiểm tra lỗi initialization trước đó và dependency missing.
7. **Class identity**: host và plugin có đang dùng cùng định nghĩa API interface không? Xem defining loader của cả hai.
8. **Concurrency/lifecycle**: một thread khác có đang initialize class? Static initializer có block, nắm lock, gọi I/O hoặc tạo vòng chờ?
9. **JDK version**: class-file major version có phù hợp runtime không? Ví dụ Java 17 thường là **major 61**, Java 8 là **major 52**.
10. **Sự cần thiết của reflection**: nếu có thể dùng `ServiceLoader` hoặc API type-safe, cân nhắc thay cơ chế lookup thủ công.

## 16. Câu hỏi tự kiểm tra

**Câu 1:** `ClassLoader.loadClass("demo.Plugin")` trả `Class<?>`. Chắc chắn `Plugin` đã chạy static block?

**Không.** Public `loadClass(name)` không yêu cầu initialization; việc khởi tạo là giai đoạn riêng.

**Câu 2:** `Class.forName("demo.Plugin")` có tự dùng TCCL của thread hiện tại?

**Không theo mặc định.** Nó dùng loader liên quan đến caller. Muốn dùng TCCL thì lấy và truyền vào overload ba tham số.

**Câu 3:** Hai loader định nghĩa hai class đều tên `demo.Plugin`; có thể cast object của loader này sang kiểu của loader kia chỉ dựa vào trùng tên không?

**Không.** Định danh runtime còn phụ thuộc defining loader, nên đây có thể là hai kiểu khác nhau.

**Câu 4:** `Class.forName(name, false, loader)` trả về; sau đó `getDeclaredMethod("run").invoke(null)` gọi static method. Lúc nào initialization xảy ra?

**Khi invocation yêu cầu**, nếu lớp chưa được khởi tạo trước đó; `forName(false)` không tự kích hoạt.

**Câu 5:** Đọc `SomeClass.CONSTANT` có luôn làm `SomeClass` initialize?

**Không.** Nếu trường là constant variable được truy cập theo quy tắc compile-time constant, compiler có thể inline và class chưa cần initialize.

**Câu 6:** Vì sao gọi `forName` lần hai sau một lỗi `<clinit>` có thể nhận `NoClassDefFoundError`?

**Vì class đã ở trạng thái erroneous** đối với loader ấy; JVM không bắt đầu lại initialization như mới.

**Câu 7:** `Class.forName(module, "java.lang.String")` và `Class.forName("java.lang.String")` có cùng contract initialization không?

**Không.** Overload theo module tìm trong module và không yêu cầu initialization; overload một chuỗi yêu cầu initialization.

## Tài liệu chuẩn để đối chiếu

- [JLS Java SE 17 §12.2 — Loading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2): ngữ nghĩa loading và class loaders.
- [JLS Java SE 17 §12.3 — Linking of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3): verification, preparation, resolution.
- [JLS Java SE 17 §12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): trigger và thứ tự khởi tạo.
- [JVMS Java SE 17 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): loading, defining/initiating loader.
- [JVMS Java SE 17 §5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): quy tắc linking.
- [JVMS Java SE 17 §5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): initialization procedure và lỗi.
- [`Class` API — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): các overload `forName`, `getName`, `getClassLoader`.
- [`ClassLoader` API — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `resolveClass` và delegation model.
- [`ServiceLoader` API — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery và class loader.
- [`ClassNotFoundException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html), [`NoClassDefFoundError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html), [`ExceptionInInitializerError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): nhận diện các đường lỗi.

**Ghi nhớ:** `Class.forName(name)` thường là “**tìm + yêu cầu khởi tạo**”; `Class.forName(name, false, loader)` là “**tìm nhưng chưa khởi tạo**”; `loader.loadClass(name)` là “**nhờ loader nạp nhưng chưa chủ động khởi tạo**”. Khi thiết kế plugin, **định danh lớp còn gồm defining class loader**, và việc có `Class<?>` không đồng nghĩa đã tạo instance hay chạy lifecycle của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-and-identity-008">Khi nào lớp có thể được dỡ bỏ và liên hệ với vòng đời class loader</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào lớp có thể được dỡ bỏ và liên hệ với vòng đời class loader

## 1. Class unloading là gì?

JVM **nạp (load)** một lớp hoặc interface khi cần có biểu diễn kiểu ở runtime. Nó giữ thông tin về kiểu, method, field, constant pool và những cấu trúc nội bộ phục vụ liên kết, kiểm chứng và thực thi. **Class unloading** là quá trình JVM có thể thu hồi một định nghĩa kiểu runtime không còn cần thiết cùng tài nguyên metadata liên quan theo chính sách quản lý bộ nhớ.

Class unloading **không đồng nghĩa** với:

- **Object garbage collection:** thu gom instance của một class. Có thể không còn instance nào nhưng class vẫn sống vì class loader còn sống.
- **Đóng class loader:** `URLClassLoader.close()` đóng resource loader quản lý; không ép JVM unload những class đã định nghĩa.
- **Xóa JAR hoặc `.class` trên đĩa:** không ngay lập tức xóa class runtime đã nạp.
- **Gán một reference bằng `null`:** chỉ thay một reference, không đảm bảo mọi đường tham chiếu tới loader đã mất.
- **Chạy lại static initializer:** class unload và một class mới được định nghĩa ở một loader mới là hai runtime class khác nhau, không phải `<clinit>` của chính class cũ được “reset”.

Theo **JVMS §5.3**, với những **named class/interface thông thường**, một class/interface có thể được unload **khi và chỉ khi defining class loader của nó có thể được garbage collector thu hồi**. Đây là điều kiện eligibility, **không phải bảo đảm thời điểm thực thi**. JVM, garbage collector và cấu hình quyết định khi nào collection/unloading diễn ra, hoặc liệu nó có xảy ra trong thời gian chương trình còn chạy.

> **Mấu chốt:** giải phóng object của plugin mới chỉ là một phần. Muốn các named classes do plugin loader định nghĩa có cơ hội unload, cần để **defining class loader** và mọi đối tượng giữ loader ấy không còn đường tham chiếu mạnh từ những GC roots.

Có một ngoại lệ cần hiểu khi học JVM nâng cao: **hidden classes** được định nghĩa qua các API đặc biệt có thể dùng cơ chế reachability khác; không áp dụng nguyên xi quy tắc named-class/plugin-loader cho mọi class do JVM sinh động.

## 2. Class identity gắn với defining loader

Đối với named class/interface, cùng **binary name** chưa đủ để kết luận là cùng kiểu JVM; **defining class loader** cũng góp phần xác định runtime identity.

~~~text
URLClassLoader A
    └── define demo.plugin.Greeter ──> Class A

URLClassLoader B
    └── define demo.plugin.Greeter ──> Class B

Tên giống nhau, defining loader khác nhau:
Class A != Class B.
~~~

Điều này có hệ quả thực tế:

- Hai class trên có `Class<?>` object riêng, class metadata riêng và **static fields** riêng.
- Mỗi class có lifecycle **initialization** riêng: static initializer có thể chạy một lần với A và một lần với B.
- Object `Greeter` của A không tự nhiên là instance của `Greeter` của B. Cast chéo có thể ném `ClassCastException`, dù tên class in ra giống nhau.
- Đối với class do loader cha define và loader con chỉ ủy quyền (delegation), loader con không tạo một class identity mới.

Cần phân biệt **initiating loader** (loader yêu cầu/bắt đầu nạp) với **defining loader** (loader thực sự định nghĩa class). Parent-first delegation thường tìm ở parent trước. Nếu parent đã define class, một lời gọi `child.loadClass(name)` có thể nhận class của parent. **Không suy luận identity từ loader ban đầu mà code gọi**; hãy quan sát defining loader bằng `type.getClassLoader()`.

Các runtime package và access rules cũng chịu ảnh hưởng loader. Hai class có cùng chuỗi tên package nhưng defining loader khác nhau không tự trở thành cùng một runtime package để được truy cập package-private.

## 3. Vòng đời plugin loader: tách bước chủ động và GC

Một hệ plugin/hot-reload điển hình có hai phần vòng đời:

~~~text
Phần ứng dụng có thể kiểm soát:

[Khởi tạo plugin loader phiên v1]
      |
[Load / link / initialize các class khi cần]
      |
[Chạy plugin: requests, jobs, listeners]
      |
[Stop nhận việc mới / draining]
      |
[Hủy đăng ký, dừng thread, đóng I/O]
      |
[Đóng URLClassLoader, bỏ host references]
      v

Phần JVM/GC quyết định:

[Loader không còn reachable từ GC roots]
      |
[Eligible cho collection và class unloading]
      |
[GC thực thi khi phù hợp]
      |
[Class metadata có thể được thu hồi]
~~~

**Không tồn tại API Java tiêu chuẩn `Class.unload()`** cho named class thông thường. Ứng dụng quản lý tài nguyên và ownership; unloading xảy ra sau đó **nếu** điều kiện và chính sách runtime cho phép.

Không nên thiết kế một API `stopPlugin()` trả về với lời hứa “mọi class thuộc phiên plugin đã bị JVM unload”. Lời hứa hợp lý hơn là: plugin đã ngừng xử lý công việc, dọn sạch đăng ký/tài nguyên mà hệ thống sở hữu và loại bỏ các strong references mà host có thể kiểm soát.

### Loading, linking, initialization và unloading không phải một trạng thái

| Giai đoạn | Điều JVM thực hiện | Không được suy ra |
| --- | --- | --- |
| Loading | Tìm binary representation, tạo runtime class/interface | Static initializer đã chạy |
| Linking | Verification, preparation, resolution theo quy tắc JVM | Mọi symbolic reference luôn resolve ngay một lần |
| Initialization | Thực thi logic `<clinit>` khi được yêu cầu | Đã tạo một instance |
| Usage | Tạo object, chạy method, reflection, tasks... | Class được tự động unload khi một method kết thúc |
| Eligible for unloading | Defining loader không còn cần được giữ sống (named classes thông thường) | JVM đã unload tức thời |
| Actual unloading | JVM thu hồi class metadata tương ứng | File JAR hoặc tài nguyên ứng dụng đã tự đóng |

Ví dụ một class tiện ích chỉ có static method có thể được initialize và dùng nhiều lần mà không hề có instance. Ngược lại, thu gom hết instance không làm class phải unload.

## 4. Vì sao một loader vẫn sống? GC roots và reachability

GC không hoạt động bằng cách “đếm bao nhiêu reference trong một vòng tròn”. Nó tìm những object còn được truy cập qua **strong references** từ các root như thread stacks, một số static references, native/JNI handles và cấu trúc runtime phù hợp.

Hãy hỏi về plugin v1:

1. Host vẫn giữ `ClassLoader` trong một `Map`, registry hoặc biến field không?
2. Có **plugin instance** trong cache, listener, message queue, scheduler hoặc callback của host?
3. Có `Class<?>`, `Method`, `Field`, `Constructor`, `MethodHandle`, proxy, lambda hoặc framework cache giữ class plugin?
4. Có thread đang chạy code plugin hoặc một executor chưa shutdown, hay còn queued `Runnable`/`Callable` tạo từ class plugin?
5. Có thread host sống lâu mà **thread context class loader (TCCL)** vẫn trỏ tới loader plugin?
6. Có `ThreadLocal` value trên thread pool hoặc request context không được clear?
7. Có MBean, driver, logging handler, event bus listener, shutdown hook, resource manager hoặc native global ref chưa hủy đăng ký?
8. Có framework/module layer hoặc loader cha tùy biến đang giữ reference tới loader con?

Đây là **những nơi cần đo retaining paths**, không phải khẳng định rằng mọi API vừa kể đều chắc chắn leak trong mọi tình huống.

### Ví dụ đường giữ sống

~~~text
GC ROOT: host worker thread
       |
       +── contextClassLoader
                   |
                   v
             PluginLoader v1
                   |
                   +── define PluginClass / Helpers / ...
~~~

Dù UI đã báo “plugin stopped”, một thread host vẫn có thể giữ v1 qua `contextClassLoader`. Loại bỏ đường đó cùng những strong paths khác giúp loader có thể trở thành unreachable.

### Một chu trình thuần túy không phải leak

~~~text
PluginLoader -> PluginClass -> PluginSingleton
     ^                             |
     +-----------------------------+
~~~

Nếu cụm object trên **không có đường tới từ GC root**, tracing GC có thể thu hồi chu trình ấy. Vì thế không kết luận rằng class static field trỏ về loader thì loader mãi mãi không thể unload; **đường root thực tế** mới là vấn đề.

Ngược lại, một **host static cache** ở class do application loader sống lâu định nghĩa có thể là root path:

~~~text
HostRegistry.staticMap
        |
        v
   PluginObject
        |
        v
  Runtime Class
        |
        v
  Defining Loader v1
~~~

Gán một biến cục bộ `plugin = null` mà quên `HostRegistry.staticMap.remove(key)` không giải quyết retaining path này.

## 5. “Có thể unload” và “đã unload” không đồng nghĩa

| Quan sát | Kết luận hợp lệ |
| --- | --- |
| Không còn instance trực tiếp của Plugin | **Chưa đủ**; loader/class vẫn có thể reachable |
| `plugin = null` | Chỉ xóa một reference; có thể còn nhiều reference khác |
| `loader = null` | Chưa biết root path nào khác giữ loader |
| `URLClassLoader.close()` trả về | Đã xử lý resource theo contract của `close`; **không xác nhận unload** |
| `System.gc()` được gọi | JVM nhận một yêu cầu/hint GC; **không có deadline hoặc bảo đảm unload** |
| `WeakReference<ClassLoader>.get() == null` | Loader được weak reference theo dõi đã được GC clear; đây là tín hiệu rõ về reachability tại lúc quan sát |
| Log HotSpot `class+unload` ghi event class X | Có bằng chứng JVM đã unload X trong lần chạy đó |
| `Metaspace committed` chưa giảm | **Không đủ** để nói class chưa unload; JVM có thể giữ capacity để dùng lại |
| `Metaspace used` tăng | Không tự chứng minh leak; có thể nhiều class mới được load hoặc workload tăng |

`System.gc()` còn có thể bị vô hiệu hóa hoặc triển khai theo cơ chế khác tùy VM flags/collector. Không viết test kiểu “gọi GC ba lần rồi tất cả class v1 phải unload”.

**Các class do bootstrap, platform hoặc application loader sống lâu định nghĩa** thường có vòng đời tương ứng JVM hoặc ứng dụng. Chúng không phải một cơ chế unload từng feature tùy ý. Nếu một loader thông thường vẫn sống, không có API “unload riêng named class đã define” như cách xóa một entry khỏi map.

## 6. Ví dụ Java 17 chạy độc lập: cùng tên, hai class loader

Ví dụ sau minh họa **hai runtime class riêng biệt** và hoạt động `close()` của loader mà **không khẳng định việc class unloading sẽ xảy ra** ngay lập tức. Tạo hai source file trong hai cây thư mục khác nhau.

### 6.1. File plugin: `plugin-src/demo/plugin/Greeter.java`

~~~java
package demo.plugin;

public class Greeter {
    static {
        System.out.println("PLUGIN_INIT");
    }

    public String hello() {
        return "HELLO";
    }
}
~~~

### 6.2. File host: `app-src/LoaderCycleDemo.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderCycleDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LoaderCycleDemo plugin-out");
        }

        URL root = Path.of(args[0]).toAbsolutePath().toUri().toURL();

        try (URLClassLoader first = new URLClassLoader(
                     new URL[] { root },
                     ClassLoader.getPlatformClassLoader());
             URLClassLoader second = new URLClassLoader(
                     new URL[] { root },
                     ClassLoader.getPlatformClassLoader())) {

            Class<?> typeA = Class.forName(
                    "demo.plugin.Greeter", true, first);
            Class<?> typeB = Class.forName(
                    "demo.plugin.Greeter", true, second);

            System.out.println("SAME_NAME=" +
                    typeA.getName().equals(typeB.getName()));
            System.out.println("SAME_CLASS=" + (typeA == typeB));
            System.out.println("SAME_LOADER=" +
                    (typeA.getClassLoader() == typeB.getClassLoader()));

            Object objectA = typeA.getConstructor().newInstance();
            System.out.println("HELLO=" +
                    typeA.getMethod("hello").invoke(objectA));

            try {
                typeB.cast(objectA);
                System.out.println("CROSS_CAST=OK");
            } catch (ClassCastException expected) {
                System.out.println("CROSS_CAST=FAIL");
            }
        }

        System.out.println("LOADERS_CLOSED");
    }
}
~~~

Ở đây **host không import `demo.plugin.Greeter`**: host chỉ dùng reflection và tên nhị phân. Parent được đặt thành **platform class loader**, để hai `URLClassLoader` tải class plugin từ cùng thư mục và mỗi loader tự define `Greeter`. Không thêm `plugin-out` vào `java -cp` của host.

### 6.3. Biên dịch và chạy

Từ thư mục chứa `plugin-src` và `app-src`:

~~~bash
javac --release 17 -d plugin-out plugin-src/demo/plugin/Greeter.java
javac --release 17 -d app-out app-src/LoaderCycleDemo.java
java -cp app-out LoaderCycleDemo plugin-out
~~~

**Stdout xác định bởi chương trình:**

~~~text
PLUGIN_INIT
PLUGIN_INIT
SAME_NAME=true
SAME_CLASS=false
SAME_LOADER=false
HELLO=HELLO
CROSS_CAST=FAIL
LOADERS_CLOSED
~~~

Giải thích:

1. `Class.forName("demo.plugin.Greeter", true, first)` nạp và **initialize** class mà loader `first` define, in `PLUGIN_INIT` lần đầu.
2. Lời gọi với `second` tạo một runtime class khác cùng binary name, in `PLUGIN_INIT` lần thứ hai.
3. `typeA.getName().equals(typeB.getName())` đúng, nhưng `typeA == typeB` sai vì defining loader khác nhau. Loader objects cũng khác nhau.
4. Reflection gọi `hello()` trên instance của typeA thành công.
5. `typeB.cast(objectA)` thất bại: object của typeA không là instance của typeB.
6. `try-with-resources` gọi `close()` với cả hai URLClassLoader và in `LOADERS_CLOSED` sau đó. **Dòng này không khẳng định class hoặc loader đã được GC/unload.**

Ví dụ có tính quyết định về identity và dispatch trong điều kiện Java 17+ chuẩn. Nó **không** dùng chu kỳ GC, heap size hay thời điểm JIT làm điều kiện output. Với một JVM agent/instrumentation tùy biến, môi trường có thể có tác động thêm không thuộc bài thực hành.

## 7. Đóng loader không giống đóng plugin và không giống unload

`URLClassLoader.close()` giải phóng tài nguyên mở mà loader quản lý, chẳng hạn những `JarFile` theo hợp đồng API. Điều quan trọng:

- Class **đã define** không biến mất chỉ vì `close()`.
- Method đang chạy từ class plugin không tự bị JVM cắt ngang.
- Object đã tạo vẫn có thể tồn tại và dùng những class đã load.
- Loader object đã đóng vẫn có thể được host map, thread hoặc reflection cache giữ sống.
- Sau khi đóng, loader không còn được kỳ vọng tải thêm class/resource mới theo cách cũ; ứng dụng không nên tiếp tục sử dụng nó như loader còn hoạt động.
- GC và class unloading có thể diễn ra muộn hoặc không diễn ra trước khi process kết thúc.

Vì thế bạn nên thiết kế ba lớp vòng đời tách bạch:

~~~text
Vòng đời nghiệp vụ:
   stop job -> unregister -> hoàn tất request -> close resource

Vòng đời class loader:
   tạo loader -> define class -> phục vụ -> close loader ->
   bỏ references do host sở hữu

Vòng đời bộ nhớ JVM:
   GC phân tích reachability -> eligible -> có thể unload ->
   metadata có thể thu hồi / tái sử dụng
~~~

Không đợi unload làm nhiệm vụ đóng `Socket`, transaction, logging handler, thread pool hoặc file: Java không cung cấp một class destructor đáng tin để tự làm việc này.

## 8. Dùng WeakReference để quan sát, không để ép unload

`WeakReference<ClassLoader>` có thể giúp quan sát liệu một loader đã được GC xử lý. Nhưng bản thân việc dùng weak reference không khiến class unload đúng hạn.

~~~java
import java.lang.ref.WeakReference;

// Đoạn minh họa: makeLoaderAndReleaseStrongRefs()
// phải được viết để không trả lại strong reference
// và phải stop/close mọi tài nguyên của plugin.
WeakReference<ClassLoader> probe = makeLoaderAndReleaseStrongRefs();

System.gc(); // chỉ là đề nghị, có thể không GC/unload
ClassLoader result = probe.get();

System.out.println(result == null
        ? "LOADER_CLEARED"
        : "NOT_CLEARED_YET");
~~~

**Đoạn trên không phải chương trình Java độc lập**: method minh họa `makeLoaderAndReleaseStrongRefs()` chưa được định nghĩa. Nó giải thích cách đọc phép đo:

- `LOADER_CLEARED` nghĩa là weak reference không còn nhận được loader object; đây là tín hiệu loader đã được xử lý bởi GC.
- `NOT_CLEARED_YET` **không phải chứng minh class-loader leak**: có thể còn strong reference hoặc collector chưa chạy theo hướng cần thiết.
- Biến `result` khi khác `null` lại chính là **strong reference mới** trong scope đang chạy; test code có thể vô tình giữ sống object mà nó đang chờ thu gom.

**Tránh `finalize()`:** overriding `finalize()` của một loader hoặc plugin object không phải hook của class unloading. Finalization là cơ chế cũ, deprecated/không đáng tin và có thể bị vô hiệu hóa. Dùng explicit close, Reference API đúng ngữ nghĩa hoặc công cụ JVM để đo, không coi log từ finalizer là bằng chứng class đã unload.

## 9. Theo dõi class loading/unloading bằng HotSpot

Với JVM HotSpot/JDK 17+ hỗ trợ **Unified Logging**, dùng:

~~~bash
java -Xlog:class+load=info,class+unload=info -cp app-out LoaderCycleDemo plugin-out
~~~

Lệnh này ghi cả sự kiện class load và **class unload nếu có**. Dữ liệu output log phụ thuộc JVM/GC/environment nên không đưa “stdout cố định” khi bật logging.

Có thể ghi log JVM ra file (Bash/Linux/macOS):

~~~bash
java '-Xlog:class+load=info,class+unload=info:file=classes.log:uptime,level,tags' -cp app-out LoaderCycleDemo plugin-out
~~~

Trong PowerShell, truyền option chứa ký tự đặc biệt bằng chuỗi quoted:

~~~powershell
java '-Xlog:class+load=info,class+unload=info' -cp app-out LoaderCycleDemo plugin-out
~~~

**Lưu ý:** ví dụ ở mục 6 chạy nhanh, không ép collector, vì vậy hoàn toàn có thể có log load của cả hai `Greeter` **mà không có log unload nào** trước khi tiến trình kết thúc. Đó không phải một lỗi; mục đích ví dụ là chứng minh **class identity**, không tạo workload để đo GC.

Các cách quan sát bổ sung trên JVM tương thích:

- `-Xlog:gc*` hiển thị hoạt động GC, nhưng không tự giải thích vì sao một loader bị giữ sống.
- `jcmd <pid> VM.classloader_stats` có thể cung cấp thống kê class loaders/class metadata trên các bản HotSpot hỗ trợ lệnh này; dùng `jcmd <pid> help` kiểm tra thực trạng của JVM đích.
- Heap dump/Java Flight Recorder hoặc profiler có thể hỗ trợ tìm những **retaining paths** từ GC roots tới `ClassLoader`, thread, `Class<?>`, listener hoặc cache.
- Khi so sánh các lần reload, ghi lại plugin version, ID của loader, thời điểm stop/close và số lượng loader còn sống.

Một heap dump có thể chứa mật khẩu, token và dữ liệu người dùng trong memory. Chỉ thu thập khi được phép, lưu ở nơi kiểm soát quyền truy cập và không tải công khai.

### Metaspace không đồng nghĩa class count

HotSpot thường dùng **Metaspace** để quản lý nhiều dạng class metadata. Nhưng:

- `used`, `committed` và `reserved` là các đại lượng khác nhau.
- JVM có thể giữ committed capacity để **tái sử dụng** dù có metadata đã được thu hồi.
- Workload đồng thời có thể load lớp mới khi lớp cũ unload, khiến tổng `used` không giảm.
- Memory của object instance, class metadata, code cache, native buffers và file-mapped pages là những nguồn tiêu thụ khác nhau.

Vì vậy “RSS không giảm” hoặc “Metaspace committed không giảm ngay” **không đủ** kết luận rằng class unloading thất bại. Hãy kết hợp unload events, loader stats và heap retaining paths.

## 10. Những nguyên nhân class loader leak thường gặp

### 10.1. Thread do plugin tạo chưa được dừng

Plugin tạo background thread, executor, scheduler hoặc job queue. Sau khi host xóa plugin khỏi UI, worker vẫn chạy code plugin hoặc giữ callback/plugin instance. Worker có thể giữ runtime class/loader của plugin hoặc TCCL.

Cách phòng ngừa:

1. Thiết kế lifecycle `start()`, `stop()`/`close()` có quyền sở hữu rõ ràng.
2. Ngừng nhận task mới.
3. Gọi shutdown/cancel theo giao thức, dùng interruption đúng cách.
4. Chờ bounded timeout và ghi log task chưa hoàn tất.
5. Không dùng `Thread.stop()` để ép dừng.

Đừng viết plugin worker là busy loop không bao giờ dừng rồi hy vọng GC tự giải quyết.

### 10.2. Thread context class loader không được restore

Một thread host sống lâu có thể thay đổi context loader để chạy dịch vụ plugin:

~~~java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

try {
    current.setContextClassLoader(pluginLoader);
    // Gọi plugin/service discovery phù hợp
} finally {
    current.setContextClassLoader(previous);
}
~~~

Đây là **mảnh minh họa**: `pluginLoader` được tạo ở nơi khác. Nếu không restore trong `finally`, thread host có thể giữ loader phiên cũ rất lâu.

Tuy nhiên, restore TCCL **chưa đủ** nếu host vẫn giữ `Class<?>`, plugin instance, proxy hoặc registry entry. Đây chỉ là một nhánh trong checklist.

### 10.3. ThreadLocal trong thread pool host

Thread pool thường tái sử dụng worker lâu dài. Nếu một `ThreadLocal` trên host worker giữ object thuộc class plugin và plugin không gọi `remove()`, loader có thể còn reachable từ thread pool.

Ngay cả khi implementation dùng **weak keys** cho `ThreadLocalMap`, value vẫn có thể bị giữ tới lúc map dọn các stale entries. Không dùng “ThreadLocal key weak” như lời hứa garbage collector tự giải phóng object plugin ngay.

Thực hành tốt: set context trong một scope với `try/finally`, gọi `remove()` khi context không còn cần, và không để queued tasks hoặc MDC/logging context giữ object plugin qua nhiều request.

### 10.4. Cache, service registry, listener, MBean, driver

Nguồn root phổ biến:

- `static Map<String,Object>` do host class giữ plugin object hoặc class metadata.
- Event bus giữ listener được tạo từ class plugin.
- Service locator/DI container giữ singleton plugin sau khi disable.
- Executor queue giữ `Runnable` hoặc lambda capture của plugin.
- Logging handler hoặc callback vẫn được đăng ký.
- MBeanServer giữ MBean do plugin đăng ký.
- JDBC/service registry hoặc thư viện native giữ driver/handle theo cơ chế riêng.
- Một cache reflection giữ `Class<?>`, `Method`, `Field`, `Constructor` của thế hệ plugin cũ.
- JNI global reference hoặc framework runtime có reference đến class/plugin object.

Không xóa bừa tất cả cache: tìm **owner và registration API**, rồi unregister từng resource tại thời điểm stop. Cùng một class loader có thể bị giữ từ nhiều nhánh độc lập; cần xử lý **tất cả retaining paths** có ý nghĩa.

### 10.5. Static field và chu trình tham chiếu

Static field trong một class **do plugin loader định nghĩa** có thể trỏ tới object plugin hoặc loader, tạo cycle. Nếu toàn bộ cycle không còn reachable từ roots, tracing GC vẫn có thể thu hồi.

Ngược lại, `HostRegistry.staticMap -> PluginObject -> PluginClass -> PluginLoader` xuất phát từ một class host sống lâu là retention thật. Khi reload, phải dọn **bản đăng ký của phiên cũ**, không chỉ gán null cho một biến cục bộ.

## 11. Thiết kế hot-reload theo từng phiên loader

Với plugin có thể nâng cấp khi ứng dụng đang chạy, một cấu trúc phổ biến là:

~~~text
App loader / shared API loader
  ├── Plugin (interface)
  ├── PluginContext
  └── PluginManager

PluginLoader v1
  ├── PluginImplementation v1
  └── dependencies riêng v1

PluginLoader v2
  ├── PluginImplementation v2
  └── dependencies riêng v2
~~~

**Vì sao API phải ở loader chung?** Nếu host và plugin đều tự define interface `Plugin` bằng hai defining loaders khác nhau, chúng là hai runtime classes khác nhau dù tên giống; `instanceof Plugin` hoặc cast có thể thất bại.

### Quy trình thay phiên an toàn

1. Tạo v2 loader và chuẩn bị binary/dependencies.
2. Xác nhận v2 có thể khởi tạo và trả lời các health checks cần thiết.
3. Ngăn request mới tới v1; để v1 **drain** các task/request đã nhận.
4. Khi v1 không còn phục vụ, gọi lifecycle `stop/close`, hủy scheduler/timer, đóng network/database/file resources.
5. Unregister mọi callback/listener/MBean/registry/cache có giữ v1.
6. Dọn TCCL, ThreadLocals, queues, retained class/method/instance references trong host.
7. Đóng loader v1 nếu nó là `URLClassLoader` để đóng các JAR/resource tương ứng.
8. Bỏ những reference cuối cùng do host sở hữu tới v1.
9. Theo dõi loader version và class-unload events **sau này**; không khẳng định `stop()` đã làm class unload đồng bộ.

Cần giữ nguyên tính nhất quán nghiệp vụ: đừng close loader v1 khi request v1 vẫn cần load class/resource mới. Đóng file handle sớm không nhất thiết cắt method đang chạy, nhưng có thể làm những thao tác tải dependency/resource sau đó bị lỗi.

### Không define lại cùng named class trong cùng loader

Một loader bình thường không phải “bộ nhớ từ điển” cho phép tùy tiện ghi đè definition của named class đã define bằng bytecode khác. Định nghĩa lại cùng binary name trong cùng loader bằng đường define thông thường có thể dẫn tới `LinkageError`. Java instrumentation/hotswap là cơ chế riêng với những giới hạn nhất định.

Để hot-reload thế hệ plugin, dùng **new defining loader** và quản lý đồng thời các object phiên cũ/mới trong giai đoạn draining.

## 12. Một số ngoại lệ và mở rộng JVM nâng cao

### Hidden classes

`MethodHandles.Lookup.defineHiddenClass` cho phép định nghĩa kiểu runtime chuyên dụng cho framework/lambda/proxy. Hidden classes có những quy tắc đặc biệt về định danh, khả năng tìm theo tên và **strong/weak reachability**.

Hidden class với cơ chế weak reachability có thể đủ điều kiện unload theo reachability riêng của class đó, **không nhất thiết phải chờ defining loader có thể được thu gom giống named class thông thường**. Vì thế câu “mọi class trong JVM chỉ được unload khi cả loader chết” là quá rộng nếu bao gồm hidden classes.

### Lambda và bytecode sinh động

Lambda hoặc framework-generated code không nhất thiết tương ứng một tệp `.class` có trong JAR. Một số JVM dùng hidden classes hoặc phương thức runtime khác. Không dùng số file `.class` trên đĩa để tính chính xác số runtime classes đã load/unload.

### Native handles và reflection

Một `Class<?>` hoặc `java.lang.reflect.Method` có thể giữ class/loader theo quan hệ runtime; `MethodHandle`, proxy, JNI refs có thể thêm retaining paths. Các cấu trúc JVM nội bộ không nhất thiết hiển thị hoàn toàn bằng một Java heap object thông thường, nên khi cần chẩn đoán sâu phải phối hợp tool của JVM với heap analysis.

## 13. Kiểm thử unload đúng cách

Một bài test integration cho hot reload nên đo **nhiều chu kỳ** và **sự hội tụ** thay vì một deadline dựa trên một lần `System.gc()`.

### Các bước

1. Tạo loader **mới cho mỗi phiên** và ghi ID/version của loader.
2. Load, chạy một tập request nhỏ và xác nhận đầu ra.
3. Shutdown plugin theo lifecycle, unregister, đóng I/O, dừng thread pool.
4. Bỏ mọi references do test harness hoặc host giữ tới loader, class và plugin objects.
5. Theo dõi `WeakReference<ClassLoader>`/`ReferenceQueue` mà không vô tình giữ một strong reference trong code test.
6. Nếu cần, dùng GC pressure ở môi trường kiểm thử với hạn mức an toàn; không dùng vòng lặp `System.gc()` như lời hứa quyết định.
7. Đối chiếu class-unload logging, class-loader stats và heap retaining paths.
8. So sánh Metaspace/class count và các chỉ số memory **qua nhiều chu kỳ**, không từ một snapshot đơn lẻ.

**Phân loại kết quả:**

- **Observed unloading:** runtime log thực sự ghi những class version cũ đã unload.
- **Loader retained:** có đường strong reference từ root tới loader hoặc đối tượng dẫn tới nó sau khi plugin đã stop.
- **Inconclusive:** `WeakReference.get()` vẫn khác null hoặc committed memory chưa giảm sau một lần GC. Đây là quan sát tạm thời, không đủ khẳng định leak.

Dù có chứng cứ unload, bài test vẫn phải xác nhận **resources đã đóng** và không còn background work trái phép. Unloading không thay thế nghĩa vụ cleanup của plugin.

## 14. Các tình huống chẩn đoán thường gặp

### Tình huống A: Reload 20 lần, Metaspace tăng liên tục

Các khả năng gồm: đúng là có nhiều class generation mới; loader cũ bị host static cache giữ; thread plugin chưa dừng; JVM chưa unload; hoặc committed capacity chưa trả OS. Hãy tìm retaining path và log unload trước khi đổi collector/tăng heap.

### Tình huống B: Không còn instance, nhưng loader vẫn sống

Kiểm tra `Class<?>`, reflection caches, thread context loader, global registry, lambda/callback, scheduler queue và thread-local values. Class không cần instance để còn sống.

### Tình huống C: `ClassCastException: Foo cannot be cast to Foo`

Có thể có **hai runtime Foo khác nhau** do hai defining loaders. Quan sát `obj.getClass()` và `obj.getClass().getClassLoader()`, kiểm tra shared API loader, không so bằng class name đơn thuần.

### Tình huống D: Loader đã `close()` mà method plugin vẫn chạy

Đúng: close không phải interrupt hay unload. Host phải dừng task theo lifecycle riêng trước khi đóng loader và bỏ references.

### Tình huống E: `System.gc()` không sinh class-unload event

Chưa kết luận được: loader có thể còn root path hoặc JVM chưa làm class unloading. Kiểm tra log và heap. Đừng dùng `finalize()` để cố quan sát class unloading.

### Tình huống F: Một ứng dụng mới chỉ gọi static method mà class đã initialized

Static method có thể kích hoạt class initialization mà không tạo instance. Ngược lại, class có thể đã loaded mà chưa initialized. **Loading, initialization, object GC, class unloading** là bốn khái niệm riêng.

## 15. Checklist vận hành plugin unload-safe

- **Ownership:** mỗi plugin version có loader/context và danh sách resource/callback do nó sở hữu.
- **Start:** ghi version, binary path, class-loader identity và dependency boundary.
- **Stop:** chặn việc mới, drain các tác vụ cũ có giới hạn, đóng network/file/DB/executor.
- **Unregister:** xóa plugin khỏi host registries, services, MBeans, listeners, caches và scheduling.
- **Context:** restore TCCL, remove ThreadLocal, clear queued callbacks.
- **Loader:** đóng `URLClassLoader`/resource quản lý theo API đúng thời điểm.
- **References:** đảm bảo host không giữ Plugin object, Class, Method, loader version cũ.
- **Verification:** theo dõi class load/unload log và retained loader references; đánh giá memory trên nhiều chu kỳ.
- **Failure handling:** nếu loader còn sống, đọc heap retaining paths từ root; không chỉ thêm `System.gc()`.

Quy trình này giúp ứng dụng quản lý tốt **resource và an toàn nghiệp vụ** ngay cả khi JVM quyết định trì hoãn class unloading.

## 16. Câu hỏi tự kiểm tra

1. Không còn instance của `Plugin` có bảo đảm class unload ngay không? **Không**, defining loader có thể còn reachable.
2. Với named classes bình thường, điều kiện trung tâm để class có thể unload là gì? **Defining class loader có thể được GC thu hồi**.
3. `URLClassLoader.close()` là lệnh unload tất cả class nó define đúng không? **Sai**.
4. Hai class cùng binary name do hai loader define là một kiểu JVM đúng không? **Sai**.
5. Một app static cache giữ plugin object cũ có thể giữ loader cũ không? **Có**.
6. `System.gc()` là deadline đảm bảo class unload không? **Không**.
7. Class-loader leak thường chứng minh bằng gì? **Strong retaining path từ GC root**.
8. Vì sao TCCL phải được restore trên thread host? **Tránh giữ loader phiên cũ ngoài ý muốn**.
9. Một reference cycle bên trong plugin chắc chắn leak không? **Không**, nếu không reachable từ root.
10. `WeakReference<ClassLoader>.get() != null` sau GC có chứng minh leak không? **Không**.
11. Vì sao Metaspace committed không giảm không đủ kết luận class không unload? **JVM có thể giữ capacity để tái sử dụng**.
12. Class do bootstrap loader define có vòng đời như plugin child loader không? **Không**.
13. Nên hot-reload nhiều phiên cùng một named class trong cùng loader không? **Không phải chiến lược thông thường; dùng loader theo phiên**.
14. Có nên để GC tự dọn socket, executor, DB connections của plugin không? **Không**, đóng chủ động.
15. Hidden classes có thể có quy tắc unload đặc biệt không? **Có**, không áp dụng mọi mệnh đề của named class.
16. Nếu `Foo cannot be cast to Foo`, kiểm tra gì? **Defining loaders và runtime class identity**.

## 17. Tài liệu chuẩn

- **JVMS Java SE 17, §5.3 Creation and Loading**, class identity và unloading: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS §5.4 Linking**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JVMS §5.5 Initialization**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JLS Java SE 17, Chapter 12 Execution**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **`ClassLoader` Java SE 17 API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **`URLClassLoader` Java SE 17 API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **`WeakReference` Java SE 17 API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html
- **`MethodHandles.Lookup.defineHiddenClass` Java SE 17 API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html

**Kết luận kỹ thuật:** class unloading không phải lời gọi trực tiếp mà là **hệ quả có thể xảy ra của GC và chính sách JVM**. Với named classes thông thường, defining loader phải đủ điều kiện được GC thu hồi. Trong hệ plugin/hot-reload, việc quan trọng nhất là **quản lý lifecycle có chủ đích: stop công việc → unregister → đóng resources → bỏ strong references → để JVM quyết định GC/unload**. Quan sát bằng class-unload log và retaining paths; không coi `System.gc()` hoặc `URLClassLoader.close()` là cam kết unload tức thời.


</details>

- [Quay lại đầu trang](#back-to-top)
