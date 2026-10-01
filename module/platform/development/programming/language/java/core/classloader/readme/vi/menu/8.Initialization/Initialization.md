# Điều kiện kích hoạt và lỗi khi khởi tạo class

Ở chương Lifecycle ta đã tách:

```text
loading
→ linking
→ initialization
```

Chương này tập trung vào khởi tạo (initialization) vì đây là nơi các tác dụng phụ thật sự dễ xuất hiện: bộ khởi tạo trường static, khối `static`, đăng ký, tạo bộ nhớ đệm hoặc lỗi trong lúc class khởi động.

Đối với hệ thống plugin, hiểu khởi tạo giúp khung phần mềm **nạp/kiểm tra kiểu plugin mà chưa chạy mã `static` của plugin**, rồi chỉ khởi tạo khi thật sự quyết định dùng.

## <a id="initialization-trigger">Thao tác sử dụng chủ động (active use) nào kích hoạt khởi tạo?</a>

Khởi tạo là lúc JVM thực thi logic khởi tạo `static` của class hoặc interface theo các quy tắc của Java.

Những thao tác sử dụng chủ động quan trọng gồm:

- tạo instance của một class bằng `new`;
- gọi một `static` method được khai báo bởi class hoặc interface đó;
- đọc hoặc ghi một `static` field được khai báo bởi class hoặc interface đó khi field không phải hằng số lúc biên dịch;
- một số thao tác reflection/runtime yêu cầu khởi tạo;
- class khởi động của ứng dụng được JVM khởi tạo trước khi gọi `main`.

Khi một **class** được khởi tạo, superclass của nó được khởi tạo trước và JVM cũng xử lý các superinterface cần thiết theo quy tắc về default method; điều này không có nghĩa mọi interface trong toàn bộ graph đều bị khởi tạo. Khi chính một **interface** được khởi tạo, các superinterface của nó cũng không tự động bị khởi tạo chỉ vì quan hệ kế thừa.

Ví dụ:

```java
final class PaymentPlugin {
    static {
        System.out.println("PaymentPlugin initialized");
    }

    static void register() {
        System.out.println("registered");
    }
}

public class Demo {
    public static void main(String[] args) {
        PaymentPlugin.register();
    }
}
```

Kết quả:

```text
PaymentPlugin initialized
registered
```

JVM phải initialize `PaymentPlugin` trước khi thực thi `register()`.

Điểm quan trọng là **nạp class không tự động đồng nghĩa với khởi tạo**. Đây là ranh giới cần thiết cho framework, reflection và việc tìm plugin vì ta có thể muốn quan sát một kiểu mà chưa muốn chạy tác dụng phụ `static` của nó.

## <a id="constant-no-init">Hằng số lúc biên dịch có thể được dùng mà không khởi tạo class</a>

Một bẫy phổ biến:

Trước khi nói về constant, hãy nhớ thêm hai **non-trigger** quan trọng:

```java
Class<?> type = PaymentPlugin.class;
```

Lấy class literal `PaymentPlugin.class` tạo/lấy `Class<?>` nhưng **không tự nó initialize `PaymentPlugin`**.

Ngoài ra, nếu source viết `SubType.SOME_STATIC_FIELD` nhưng field đó thật sự được khai báo ở `SuperType`, thì với một static-field access **thực sự kích hoạt initialization** — ví dụ đọc một static field không phải compile-time constant — type được initialize là **class/interface khai báo field**, không phải cứ type nào xuất hiện bên trái dấu `.` trong source cũng bị initialize. Nếu field là compile-time constant thì exception ngay bên dưới vẫn áp dụng: việc đọc có thể không initialize cả `SuperType` lẫn `SubType`.

```java
final class PluginDefaults {
    static {
        System.out.println("PluginDefaults initialized");
    }

    static final int TIMEOUT_SECONDS = 30;
}

public class Demo {
    public static void main(String[] args) {
        System.out.println(PluginDefaults.TIMEOUT_SECONDS);
    }
}
```

`TIMEOUT_SECONDS` là constant variable với compile-time constant expression. Compiler có thể inline giá trị `30` vào mã gọi, nên việc đọc nó **không nhất thiết kích hoạt initialization của `PluginDefaults`**.

So sánh:

```java
final class PluginDefaults {
    static {
        System.out.println("initialized");
    }

    static final int A = 30;                    // compile-time constant
    static final Integer B = 30;                // không phải primitive/String constant variable
    static final String C = new String("prod"); // không phải constant expression
}
```

```text
đọc A
→ value có thể đã inline
→ không trigger initialization

đọc B hoặc C
→ getstatic của non-constant field
→ trigger initialization nếu class chưa initialized
```

Compile-time constant còn tạo ra một hệ quả khi triển khai: nếu thư viện đổi giá trị constant nhưng mã gọi cũ không được biên dịch lại, mã đó có thể tiếp tục dùng giá trị đã được inline trước đó.

## <a id="clinit-model">Mô hình tư duy của &lt;clinit&gt;</a>

Khi class hoặc interface có logic khởi tạo `static` cần thực thi, mô hình class-file/JVM dùng một method khởi tạo đặc biệt tên `<clinit>`.

Ví dụ mã nguồn:

```java
final class PaymentPlugin {
    static int retryCount = loadRetryCount();

    static {
        System.out.println("register metrics");
    }

    private static int loadRetryCount() {
        return 3;
    }
}
```

Mô hình tư duy:

```text
static field initializer cần chạy
        +
static initializer block
        ↓
class initialization logic
        ↓
<clinit> ở bytecode model khi cần
```

Các static field initializer và static block có hiệu lực theo thứ tự xuất hiện của declaration trong class, sau khi JVM đã thực hiện các quy tắc initialization tiên quyết như initialize superclass thích hợp.

`<clinit>` không phải method để ứng dụng gọi:

```java
// Không có API source-level kiểu này:
// PaymentPlugin.<clinit>();
```

JVM tự quyết định khi nào initialize theo JLS/JVM rules.

### Class và interface có quy tắc khác nhau

Quy tắc thứ tự đã nêu ở phần kích hoạt vẫn áp dụng: khi khởi tạo một class, JVM khởi tạo superclass trước và xử lý các superinterface cần thiết theo quy tắc về default method; không phải toàn bộ cây interface đều bị khởi tạo. Việc khởi tạo chính một interface cũng không đồng nghĩa mọi superinterface của nó bị khởi tạo theo.

Điểm người học cần giữ ở đây: khởi tạo là **một quy trình của JVM**, không phải “JVM chạy tất cả khối `static` của mọi kiểu có liên quan”.

## <a id="initialization-once">Mỗi class do một ClassLoader định nghĩa chỉ được JVM khởi tạo một lần</a>

Với một `Class<?>` cụ thể khi chạy, JVM đảm bảo quy trình khởi tạo chỉ hoàn thành một lần.

```java
final class PluginRegistry {
    static {
        System.out.println("INIT");
    }

    static void touch() {
    }
}

public class Demo {
    public static void main(String[] args) {
        PluginRegistry.touch();
        PluginRegistry.touch();
        PluginRegistry.touch();
    }
}
```

Kết quả:

```text
INIT
```

nhưng `touch()` được gọi ba lần.

Kết nối với phần Định danh class:

```text
PaymentPlugin do loaderA định nghĩa
→ đối tượng Class A
→ trạng thái khởi tạo A

PaymentPlugin do loaderB định nghĩa
→ đối tượng Class B
→ trạng thái khởi tạo B
```

Vì hai ClassLoader định nghĩa tạo ra hai định danh class khác nhau khi chạy, mỗi định danh có trạng thái khởi tạo riêng. “Static singleton” vì vậy chỉ mang tính singleton **trong phạm vi một định danh class khi chạy**, không phải singleton toàn JVM khi có nhiều ClassLoader.

## <a id="initialization-locking">JVM đồng bộ việc khởi tạo khi nhiều luồng cùng sử dụng class</a>

Giả sử hai luồng cùng sử dụng chủ động một class chưa được khởi tạo:

```text
Luồng A ─┐
         ├→ PaymentPlugin chưa được khởi tạo
Luồng B ─┘
```

Quy trình khởi tạo của JVM dùng cơ chế đồng bộ riêng cho trạng thái khởi tạo của mỗi `Class`/interface. Một luồng thực hiện khởi tạo; luồng khác cần khởi tạo cùng kiểu sẽ chờ quy trình đó hoàn tất hoặc thất bại.

Ví dụ:

```java
final class SlowPlugin {
    static final Object CONFIG = loadConfig();

    private static Object loadConfig() {
        System.out.println(
                "init by " + Thread.currentThread().getName()
        );
        return new Object();
    }
}
```

Hai luồng đọc `SlowPlugin.CONFIG` cùng lúc không được phép làm bộ khởi tạo chạy hai lần cho cùng `SlowPlugin.class`.

### Mã khởi tạo vẫn có thể gây bế tắc (deadlock) ở cấp ứng dụng

JVM bảo đảm quy trình khởi tạo, nhưng mã khởi tạo `static` của ta vẫn có thể gọi mã khác, lấy khóa khác hoặc kích hoạt khởi tạo của kiểu khác.

```text
Thread A initializes A
→ cần B

Thread B initializes B
→ cần A
```

Nếu phụ thuộc/cơ chế khóa tạo thành vòng lặp xấu, ứng dụng có thể treo. Vì vậy bộ khởi tạo `static` nên ngắn, có tính xác định và tránh I/O, mạng hoặc đồ thị khóa phức tạp.

## <a id="initialization-failure">Lỗi khởi tạo đưa class vào trạng thái lỗi (erroneous)</a>

Một bộ khởi tạo `static` có thể thất bại:

```java
final class BrokenPlugin {
    static final int PORT = loadPort();

    private static int loadPort() {
        throw new IllegalStateException("invalid plugin config");
    }
}
```

Lần sử dụng chủ động (active use) đầu tiên:

```java
System.out.println(BrokenPlugin.PORT);
```

`IllegalStateException` phát sinh trong quá trình khởi tạo không phải `Error`, nên JVM thường bọc nó bằng `ExceptionInInitializerError` cho bên gọi đầu tiên.

Sau khi thất bại:

```text
BrokenPlugin Class khi chạy
→ khởi tạo thất bại
→ bị đánh dấu ở trạng thái lỗi (erroneous)
```

Lần sử dụng chủ động sau đó của **cùng định danh class khi chạy** không chạy lại bộ khởi tạo như một lần thử lại. Bên gọi thường thấy `NoClassDefFoundError` báo class không thể khởi tạo.

Ví dụ quan sát:

```java
for (int i = 0; i < 2; i++) {
    try {
        System.out.println(BrokenPlugin.PORT);
    } catch (Throwable error) {
        System.out.println(error.getClass().getName());
    }
}
```

Dạng điển hình:

```text
java.lang.ExceptionInInitializerError
java.lang.NoClassDefFoundError
```

Nếu bộ khởi tạo trực tiếp ném một `Error`, quy trình khởi tạo không cần bọc nó thành `ExceptionInInitializerError`; class vẫn đi vào trạng thái lỗi (erroneous).

Đây là lý do không nên dùng bộ khởi tạo `static` như một cơ chế thử lại. Nếu cấu hình plugin có thể lỗi tạm thời và cần thử lại, hãy thiết kế một vòng đời khi chạy rõ ràng thay vì nhét logic vào quá trình khởi tạo class.

Kết thúc chương, ta đã thấy trạng thái khởi tạo thuộc về định danh class khi chạy. Chương cuối nối định danh đó với GC: **khi plugin dừng, điều kiện nào cho phép cả `Class` và ClassLoader định nghĩa được thu hồi?**
