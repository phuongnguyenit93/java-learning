# Khởi tạo Class

Ở chương Lifecycle ta đã tách:

```text
loading
→ linking
→ initialization
```

Chương này tập trung vào initialization vì đây là nơi các tác dụng phụ thật sự dễ xuất hiện: static field initializer, static block, đăng ký, tạo cache hoặc lỗi trong lúc class khởi động.

Đối với hệ thống plugin, hiểu initialization giúp framework **nạp/kiểm tra type plugin mà chưa chạy mã `static` của plugin**, rồi chỉ initialize khi thật sự quyết định dùng.

## <a id="clinit-model">Mô hình tư duy của &lt;clinit&gt;</a>

Khi class có executable static initialization, mô hình compiler/JVM dùng một method khởi tạo class đặc biệt tên `<clinit>`.

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

Khi initialize một class, superclass cần thiết được initialize trước. Initialization của interface có ngữ nghĩa khác; việc initialize một class không đơn giản là initialize toàn bộ interface graph của nó. Các superinterface khai báo default method có vai trò trong initialization prerequisites của class theo JLS.

Điểm người học cần giữ ở đây: initialization là **một quy trình của runtime**, không phải “JVM chạy tất cả static block của mọi type liên quan”.

## <a id="initialization-once">Initialization diễn ra một lần cho mỗi runtime Class identity</a>

Với một runtime `Class` cụ thể, JVM đảm bảo quy trình initialization chỉ hoàn thành một lần.

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

Kết nối với Class Identity:

```text
PaymentPlugin defined by loaderA
→ Class object A
→ initialization state A

PaymentPlugin defined by loaderB
→ Class object B
→ initialization state B
```

Vì hai defining loaders tạo hai runtime identities, mỗi identity có initialization state riêng. “Static singleton” vì vậy chỉ singleton **trong phạm vi một runtime class identity**, không phải singleton toàn JVM khi có nhiều loader.

## <a id="initialization-locking">JVM đồng bộ initialization khi nhiều thread chạm cùng class</a>

Giả sử hai thread cùng active-use một class chưa initialize:

```text
Thread A ─┐
          ├→ PaymentPlugin chưa initialized
Thread B ─┘
```

Quy trình initialization của JVM dùng cơ chế đồng bộ riêng cho mỗi `Class`/interface initialization state. Một thread thực hiện initialization; thread khác cần initialization của cùng type sẽ chờ quy trình đó hoàn tất hoặc thất bại.

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

Hai thread đọc `SlowPlugin.CONFIG` cùng lúc không được phép làm initializer chạy hai lần cho cùng `SlowPlugin.class`.

### Mã initialization vẫn có thể gây deadlock ở cấp ứng dụng

JVM bảo đảm quy trình initialization, nhưng static initialization của ta vẫn có thể gọi code khác, lấy lock khác hoặc trigger initialization của type khác.

```text
Thread A initializes A
→ cần B

Thread B initializes B
→ cần A
```

Nếu dependency/locking tạo cycle xấu, ứng dụng có thể treo. Vì vậy static initializer nên ngắn, có tính xác định và tránh I/O/network/lock graph phức tạp.

## <a id="initialization-failure">Lỗi initialization đưa class vào trạng thái erroneous</a>

Một static initializer có thể thất bại:

```java
final class BrokenPlugin {
    static final int PORT = loadPort();

    private static int loadPort() {
        throw new IllegalStateException("invalid plugin config");
    }
}
```

Lần active use đầu:

```java
System.out.println(BrokenPlugin.PORT);
```

`IllegalStateException` phát sinh trong initialization không phải `Error`, nên quy trình initialization thường bọc nó bằng `ExceptionInInitializerError` cho bên gọi đầu tiên.

Sau khi thất bại:

```text
BrokenPlugin runtime Class
→ initialization failed
→ marked erroneous
```

Active use sau đó của **cùng runtime class identity** không chạy initializer lại như retry. Bên gọi thường thấy `NoClassDefFoundError` báo class không thể initialize.

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

Nếu initializer trực tiếp ném một `Error`, quy trình initialization không cần bọc nó thành `ExceptionInInitializerError`; class vẫn đi vào erroneous state.

Đây là lý do không nên dùng static initializer như một cơ chế thử lại. Nếu cấu hình plugin có thể lỗi tạm thời và cần thử lại, hãy thiết kế một vòng đời runtime rõ ràng thay vì nhét logic vào class initialization.

## <a id="constant-no-init">Compile-time constant có thể được dùng mà không initialize class</a>

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

Kết thúc chapter, ta đã thấy initialization state thuộc về runtime class identity. Chương cuối nối identity với GC: **khi plugin dừng, điều kiện nào cho phép cả Class và defining ClassLoader được thu hồi?**
