<a id="back-to-top"></a>

# Error và khởi tạo

## Menu
- [Khi nào Error được phát sinh và phạm vi xử lý phù hợp](#error-and-initialization-001)
- [AssertionError và câu lệnh assert](#error-and-initialization-002)
- [Không dùng assert thay kiểm tra đầu vào công khai](#error-and-initialization-003)
- [OutOfMemoryError và giới hạn phục hồi trong cùng tiến trình](#error-and-initialization-004)
- [StackOverflowError và đệ quy không kiểm soát](#error-and-initialization-005)
- [LinkageError và các vấn đề tương thích mã đã biên dịch](#error-and-initialization-006)
- [ExceptionInInitializerError khi khởi tạo static thất bại](#error-and-initialization-007)
- [NoClassDefFoundError trong lần sử dụng sau một lần khởi tạo thất bại](#error-and-initialization-008)
- [Vì sao bắt Throwable hoặc Error cần một ranh giới và mục đích rõ ràng](#error-and-initialization-009)

## <a id="error-and-initialization-001">Khi nào Error được phát sinh và phạm vi xử lý phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào `Error` được phát sinh và phạm vi xử lý phù hợp trong Java

Trong Java, `Error` là một nhánh của `Throwable`, thường biểu thị **tình trạng nghiêm trọng** mà phần lớn ứng dụng **không nên cố bắt và tiếp tục chạy như một lỗi nghiệp vụ thông thường**. Ví dụ: JVM không thể cấp phát thêm bộ nhớ (`OutOfMemoryError`), ngăn xếp lời gọi cạn (`StackOverflowError`), một lớp không thể liên kết với định nghĩa đã biên dịch (`LinkageError`) hoặc việc khởi tạo lớp tĩnh thất bại (`ExceptionInInitializerError`).

Tuy nhiên, cần tránh hai phát biểu cực đoan:

- **“Mọi `Error` đều do JVM tự ném và chắc chắn kết thúc toàn bộ tiến trình.”** Sai. Mã Java hoặc thư viện cũng có thể chủ động ném `AssertionError`; một `Error` không được bắt thường làm kết thúc **luồng đang thực thi**, nhưng các luồng khác và tiến trình có thể vẫn tiếp tục.
- **“Đã là `Throwable` thì có thể `catch` rồi retry/tiếp tục bình thường.”** Cũng sai. Nhiều `Error` báo rằng môi trường thực thi, liên kết lớp hoặc một bất biến quan trọng đang không đáng tin cậy. Việc nuốt lỗi và tiếp tục có thể làm hỏng dữ liệu hoặc gây lỗi khó điều tra hơn.

Cách tiếp cận phù hợp: **hiểu loại `Error`, nguồn phát sinh, trạng thái sau lỗi và ranh giới quản lý**. Trong nghiệp vụ thường chỉ xử lý `Exception` phù hợp; ở ranh giới rất hẹp như giám sát một tác vụ/worker, có thể **ghi nhận hoặc thực hiện cleanup tối thiểu rồi chuyển tiếp/để lỗi lan truyền**, thay vì hứa rằng hệ thống đã phục hồi.

Bài học Java 17+ này giải thích phân cấp `Error`, các nhóm lỗi, quan hệ với khởi tạo lớp, phạm vi `catch`, `finally`, `try-with-resources`, hành vi nhiều luồng và hướng xử lý an toàn. Tất cả ví dụ chạy được đều **mô phỏng bằng những lỗi an toàn như `AssertionError`**, không cố tạo `OutOfMemoryError` hoặc `StackOverflowError` thật.

## 1. Vị trí của `Error` trong hệ thống ngoại lệ

Phân cấp rút gọn:

~~~text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Exception
    │   ├── IOException                         checked
    │   ├── SQLException                        checked
    │   └── java.lang.RuntimeException          unchecked
    │       ├── IllegalArgumentException
    │       └── IllegalStateException
    └── java.lang.Error                         unchecked
        ├── java.lang.VirtualMachineError
        │   ├── OutOfMemoryError
        │   ├── StackOverflowError
        │   └── InternalError
        ├── java.lang.LinkageError
        │   ├── ExceptionInInitializerError
        │   ├── NoClassDefFoundError
        │   ├── NoSuchMethodError
        │   └── UnsupportedClassVersionError
        ├── AssertionError
        └── ThreadDeath
~~~

Những điểm cần nhớ:

1. `Exception` **không phải** superclass của `Error`; cả hai là các nhánh trực tiếp dưới `Throwable`.
2. `RuntimeException` cũng **không phải** superclass của `Error`.
3. `Error` và mọi lớp con được coi là **unchecked** trong kiểm tra ngoại lệ của compiler; không cần `throws Error` và không buộc caller phải `catch`.
4. `catch (Exception ex)` **không bắt được** một đối tượng `Error` thực sự.
5. `catch (Throwable t)` có thể bắt `Error` vì `Throwable` là superclass của cả `Error` và `Exception`, nhưng điều đó không làm việc bắt rộng trở thành giải pháp đúng.
6. Một `Error` vẫn là đối tượng `Throwable`: nó có `message`, `cause`, stack trace và có thể có `suppressed exceptions`, tùy loại và cấu hình đối tượng.

Theo tài liệu Java SE 17, `Error` mô tả những vấn đề nghiêm trọng mà ứng dụng hợp lý thông thường không nên cố bắt. Đây là **định hướng xử lý mặc định**, không phải luật JVM cấm viết `catch (Error)`.

## 2. Khi nào các loại `Error` có thể phát sinh?

| Nhóm/tên | Tình huống tiêu biểu | Phản ứng thường phù hợp |
| --- | --- | --- |
| `OutOfMemoryError` | JVM không thể đáp ứng một yêu cầu cấp phát do giới hạn bộ nhớ/tài nguyên liên quan | Xem đây là sự cố nghiêm trọng; ghi nhận bằng giám sát ngoài tiến trình, phân tích heap và giới hạn tài nguyên |
| `StackOverflowError` | Một luồng vượt quá khả năng ngăn xếp, thường do đệ quy quá sâu/vòng lặp đệ quy | Điều tra đệ quy, điều kiện dừng, cấu trúc dữ liệu chu kỳ và cấu hình stack; không đơn giản retry |
| `InternalError` | JVM báo sự cố nội bộ nghiêm trọng | Xem xét JVM, thư viện native và môi trường; không tự xử lý như validation |
| `LinkageError` | Các lớp/thành phần nhị phân không tương thích hoặc không thể liên kết | Sửa dependency, bytecode, version, classloader, quy trình build/deploy |
| `NoSuchMethodError` | Runtime không tìm thấy method tương thích mà bytecode đã tham chiếu | Kiểm tra sự lệch giữa phiên bản biên dịch và phiên bản chạy |
| `UnsupportedClassVersionError` | Runtime không hỗ trợ phiên bản class file được nạp | Chạy bằng JDK phù hợp hoặc biên dịch `--release` tương thích |
| `ExceptionInInitializerError` | Quá trình khởi tạo static của class/interface thất bại bằng một `Throwable` không thuộc `Error` | Kiểm tra `getCause()` và initializer/factory tĩnh; sửa cấu hình hoặc code |
| `NoClassDefFoundError` | JVM/ClassLoader không tìm/nạp được định nghĩa cần thiết, hoặc lớp đang ở trạng thái khởi tạo lỗi | Phân biệt thiếu class/dependency với lỗi khởi tạo lớp trước đó |
| `AssertionError` | Một câu `assert` được bật thất bại hoặc code chủ động `throw new AssertionError(...)` | Sửa bất biến/giả định sai; thường không coi là lỗi đầu vào có thể khôi phục |
| `ThreadDeath` | Tình huống legacy liên quan đến cơ chế dừng luồng | Không bắt/nuốt; tránh cơ chế dừng luồng không an toàn |

Đây là **tình huống phổ biến**, không phải bảng ánh xạ một-một cho mọi trường hợp. Chẳng hạn `NoClassDefFoundError` không chứng minh ngay rằng một file `.class` bị thiếu; trước đó một lớp có thể đã **thất bại khi khởi tạo**. Và `OutOfMemoryError` không luôn đơn giản là “heap đang đầy”; thông điệp và dữ liệu chẩn đoán cụ thể có thể chỉ ra vùng tài nguyên khác.

## 3. JVM `Error` khác với `Exception` thông thường ở điểm nào?

Một `IOException` từ thao tác đọc file là tình huống chương trình có thể dự kiến, xử lý, thông báo người dùng hoặc thử một đường đọc khác nếu hợp đồng cho phép. Một `IllegalArgumentException` từ đầu vào sai có thể được xử lý ở ranh giới validation.

Ngược lại, nếu hệ thống gặp `OutOfMemoryError` trong lúc xử lý:

- Ngay cả thao tác **ghi log** có thể cần cấp phát đối tượng/buffer mới và tiếp tục thất bại.
- Trạng thái của một thao tác đang thực hiện có thể chưa hoàn chỉnh; ứng dụng chưa biết giao dịch đã commit hay chưa.
- Việc trả một HTTP response thông thường có thể cần tài nguyên không còn sẵn.
- Retry lập tức có thể lặp lại lỗi hoặc tăng áp lực bộ nhớ.
- Một luồng bị ảnh hưởng không nhất thiết là toàn bộ tiến trình; nhưng các luồng khác còn chạy cũng **không tự chứng minh hệ thống khỏe mạnh**.

Do đó, không nên viết một quy tắc `catch (Throwable)` cho mọi method nghiệp vụ rồi tiếp tục như thể request thất bại đã được khôi phục. Hãy đặt **giới hạn phục hồi ở nơi thật sự có đủ thông tin về trạng thái và quyền sở hữu tài nguyên**.

## 4. `Error` là unchecked nhưng vẫn có thể xuất hiện ở runtime

Một method có thể ném `AssertionError` mà không ghi `throws`:

~~~java
static void verify() {
    throw new AssertionError("bất biến bị vi phạm");
}
~~~

Cả việc tạo đối tượng lẫn `throw` là hành vi Java hợp lệ. Caller không bị compiler buộc:

~~~java
try {
    verify();
} catch (AssertionError err) {
    System.out.println(err.getMessage());
}
~~~

**Không phải cứ compile được thì nên catch.** Khi `AssertionError` báo một bất biến lập trình bị phá vỡ, cách xử lý đáng tin thường là **ngừng thao tác không an toàn, lưu thông tin chẩn đoán và sửa bug**. Bắt lỗi để chứng minh cơ chế hoạt động có thể hợp lý trong chương trình học hoặc test, nhưng không nên là quy tắc mặc định của hệ thống sản phẩm.

`throws Error` trong chữ ký method cũng không làm compiler áp đặt catch-or-declare. Các công bố `throws` nên tập trung vào những checked exception mà caller có nghĩa vụ xử lý.

## 5. Ví dụ Java 17: `catch (Exception)` không bắt `Error`

Tệp `CatchErrorDemo.java`:

~~~java
public class CatchErrorDemo {
    static void validateInvariant() {
        throw new AssertionError("bất biến bị vi phạm");
    }

    public static void main(String[] args) {
        try {
            validateInvariant();
        } catch (Exception ex) {
            System.out.println("Bắt Exception");
        } catch (AssertionError err) {
            System.out.println("Bắt AssertionError: " + err.getMessage());
        }
        System.out.println("Chương trình minh họa tiếp tục");
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 -encoding UTF-8 CatchErrorDemo.java
java CatchErrorDemo
~~~

Stdout chính xác:

~~~text
Bắt AssertionError: bất biến bị vi phạm
Chương trình minh họa tiếp tục
~~~

Giải thích:

- `validateInvariant()` ném **đối tượng `AssertionError`**, không phải một `Exception`.
- `catch (Exception ex)` không khớp với loại runtime `AssertionError`.
- `catch (AssertionError err)` khớp, nên ví dụ in đúng thông báo.
- Sau `catch`, `main` tiếp tục vì **chương trình minh họa đã cố ý xử lý lỗi và không ném lại**.
- Không suy từ đó rằng production nên nuốt mọi `AssertionError`; ví dụ chỉ chứng minh **phân cấp và dispatch của `catch`**.

Nếu viết một `catch (Throwable)`, nó có thể bắt `AssertionError`, `OutOfMemoryError` và cả `Exception`. Nhưng nhánh quá rộng dễ làm lẫn lỗi khôi phục được với tình trạng nghiêm trọng; cần phân biệt mục đích **quan sát** và **phục hồi**.

## 6. `VirtualMachineError`: dấu hiệu điều kiện thực thi nghiêm trọng

`VirtualMachineError` là một nhánh `Error` dành cho những vấn đề cho thấy JVM không thể tiếp tục làm việc bình thường theo yêu cầu trong một số điều kiện.

Các ví dụ quan trọng:

- **`OutOfMemoryError`**: không cấp phát được do giới hạn tài nguyên. Có thể cần heap dump hoặc dữ liệu giám sát đã được cấu hình **trước khi lỗi xảy ra**.
- **`StackOverflowError`**: stack của một luồng bị cạn, thường do đệ quy quá sâu, điều kiện dừng sai hoặc dữ liệu chứa chu trình khi dùng đệ quy.
- **`InternalError`**: JVM phát hiện điều kiện lỗi nội bộ; cần xem thông điệp, phiên bản JVM, native code và các log chẩn đoán liên quan.

Không nên cố tình tạo các lỗi này bằng cách:

~~~java
// KHÔNG CHẠY: minh họa anti-pattern nguy hiểm.
// while (true) {
//     buffers.add(new byte[100_000_000]); // Cố làm cạn heap.
// }
~~~

Hoặc:

~~~java
// KHÔNG CHẠY: cố tình dùng đệ quy vô hạn.
// static void recurseForever() { recurseForever(); }
~~~

Những thử nghiệm này có thể làm chậm, treo hoặc cạn tài nguyên máy chạy và **không cần thiết** để học cách xử lý `Error`. Bài dùng `AssertionError` được tạo có chủ đích để minh họa cơ chế ném/bắt mà không gây hại tài nguyên.

Khi điều tra lỗi thực tế, nên thu thập phiên bản Java, cờ JVM, tài nguyên, metric, stack trace, dump thích hợp và thời điểm phát sinh. Việc tự động bắt và retry `OutOfMemoryError` trong mọi request thường không có cơ sở an toàn.

## 7. `LinkageError`: lỗi tương thích nhị phân, classpath và classloader

Một nhóm `Error` quan trọng khác xuất hiện **không phải do dữ liệu đầu vào sai**, mà do chương trình đã biên dịch và môi trường runtime không còn tương thích.

Ví dụ:

- `NoSuchMethodError`: code đã biên dịch tham chiếu tới một method không tìm thấy theo định nghĩa runtime đang được nạp.
- `NoSuchFieldError`: tương tự cho field.
- `IncompatibleClassChangeError`: phát hiện một dạng thay đổi class/interface không tương thích.
- `UnsupportedClassVersionError`: JVM hiện tại không hỗ trợ phiên bản class file.
- `VerifyError`: class file vi phạm điều kiện verification.
- `NoClassDefFoundError`: không tìm nạp được định nghĩa class cần thiết hoặc class đã rơi vào trạng thái khởi tạo lỗi.

Ví dụ tình huống thực tế:

~~~text
Biên dịch Module A với Library X phiên bản mới
                  ↓
Triển khai Module A với Library X phiên bản cũ
                  ↓
Lúc chạy gọi method chỉ có trong bản mới
                  ↓
             NoSuchMethodError
~~~

Hướng khắc phục thường là **sửa version dependency, classpath/module path, classloader hoặc build/deployment artifact**, rồi triển khai lại. Bắt `NoSuchMethodError` rồi trả dữ liệu mặc định có thể che một deployment lỗi và làm hệ thống vận hành ở trạng thái sai.

**Phân biệt** `ClassNotFoundException` (checked exception có thể được API nạp lớp/reflective lookup công bố) với `NoClassDefFoundError` (lỗi linkage/runtime). Dù thông điệp có vẻ gần nhau, chúng thuộc những nhánh và hoàn cảnh phát sinh khác nhau.

## 8. `ExceptionInInitializerError`: một `Error` gắn với khởi tạo lớp

Khi một class được **khởi tạo lần đầu**, Java chạy các **static field initializers** và **static initializer blocks** thích hợp. Nếu quá trình đó hoàn tất bất thường bằng một `Throwable` **không phải `Error`**, JVM thông thường tạo `ExceptionInInitializerError` bao nguyên nhân đó.

Ví dụ một static field gọi factory:

~~~java
static final String VALUE = initialize();

static String initialize() {
    throw new IllegalStateException("thiếu cấu hình");
}
~~~

Nếu phương thức này được dùng làm initializer của lớp, một lần truy cập kích hoạt khởi tạo sẽ làm `initialize()` ném `IllegalStateException`. JVM phản ánh lỗi khởi tạo class bằng `ExceptionInInitializerError`, có `getCause()` trỏ tới `IllegalStateException`.

**Hai lớp thông tin cần đọc:**

1. **Ngoại lệ ngoài cùng `ExceptionInInitializerError`**: thao tác khởi tạo lớp thất bại.
2. **Cause của nó**: lý do thực tế ở phần code đang khởi tạo, ví dụ `IllegalStateException("thiếu cấu hình")`.

Với phiên bản Java hiện đại, ưu tiên `getCause()`. API `ExceptionInInitializerError.getException()` tồn tại vì lịch sử và vẫn được hỗ trợ, nhưng không cần thiết cho hầu hết code mới.

Một điểm đặc biệt: nếu static initializer **trực tiếp ném một `Error`**, theo JLS, `Error` đó thường được truyền ra **mà không bị bọc trong `ExceptionInInitializerError`**. Cả hai đường thất bại đều có thể khiến lớp được đánh dấu không thể khởi tạo lại trong cùng ngữ cảnh classloader.

## 9. Java 17: lần khởi tạo đầu và lần dùng lớp tiếp theo

Tệp `ClassInitializationErrorDemo.java`:

~~~java
public class ClassInitializationErrorDemo {
    static final class BrokenConfig {
        static final String VALUE = initialize();

        static String initialize() {
            System.out.println("Bắt đầu khởi tạo lớp");
            throw new IllegalStateException("thiếu cấu hình");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(BrokenConfig.VALUE);
            } catch (ExceptionInInitializerError err) {
                System.out.println("Lần " + attempt + ": "
                        + err.getClass().getSimpleName());
                System.out.println("Nguyên nhân: "
                        + err.getCause().getClass().getSimpleName()
                        + " - " + err.getCause().getMessage());
            } catch (NoClassDefFoundError err) {
                System.out.println("Lần " + attempt + ": "
                        + err.getClass().getSimpleName());
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 ClassInitializationErrorDemo.java
java ClassInitializationErrorDemo
~~~

Stdout chính xác:

~~~text
Bắt đầu khởi tạo lớp
Lần 1: ExceptionInInitializerError
Nguyên nhân: IllegalStateException - thiếu cấu hình
Lần 2: NoClassDefFoundError
~~~

**Giải thích chi tiết:**

- `BrokenConfig.VALUE` **không phải compile-time constant**, vì nó lấy giá trị từ lời gọi method. Truy cập nó kích hoạt quá trình khởi tạo `BrokenConfig`.
- Lần đầu, `initialize()` in thông báo rồi ném `IllegalStateException`.
- JVM bọc lỗi này thành `ExceptionInInitializerError`; `getCause()` giữ lại lỗi thực tế.
- Lớp bị đánh dấu **erroneous** trong ngữ cảnh tải lớp đang xét.
- Lần thứ hai, JVM **không thử chạy lại initializer**; nó báo `NoClassDefFoundError` vì lớp đang ở trạng thái khởi tạo lỗi.
- Chỉ có một dòng `Bắt đầu khởi tạo lớp`, cho thấy initializer không được thực thi lại ở lượt tiếp theo.

**Đây là lý do không nên `catch(ExceptionInInitializerError)` rồi thử lại `BrokenConfig.VALUE` như một cơ chế phục hồi.** Việc sửa file cấu hình ngay trong tiến trình không tự đảo ngược trạng thái class initialization đã thất bại. Phục hồi có thể đòi hỏi tái thiết lập môi trường/lifecycle hoặc một classloader mới có kiểm soát; với ứng dụng thông thường, sửa lỗi và khởi động lại tiến trình là hướng an toàn hơn.

`NoClassDefFoundError` cũng có thể có nguyên nhân khác (ví dụ thiếu dependency), vì vậy không được kết luận mọi `NoClassDefFoundError` đều là “đã từng khởi tạo thất bại”. Hãy xem log và lỗi đầu tiên.

## 10. Nếu static initializer ném `Error` ngay từ đầu?

Tệp `DirectErrorInitializerDemo.java`:

~~~java
public class DirectErrorInitializerDemo {
    static final class BrokenPlugin {
        static final Object INSTANCE = create();

        static Object create() {
            System.out.println("Đang khởi tạo plugin");
            throw new AssertionError("hợp đồng plugin sai");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(BrokenPlugin.INSTANCE);
            } catch (AssertionError err) {
                System.out.println("Lần " + attempt + ": "
                        + err.getClass().getSimpleName());
            } catch (NoClassDefFoundError err) {
                System.out.println("Lần " + attempt + ": "
                        + err.getClass().getSimpleName());
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 DirectErrorInitializerDemo.java
java DirectErrorInitializerDemo
~~~

Stdout chính xác:

~~~text
Đang khởi tạo plugin
Lần 1: AssertionError
Lần 2: NoClassDefFoundError
~~~

So với ví dụ trước:

| Đường khởi tạo static thất bại | Lần dùng đầu | Lần dùng tiếp trong cùng ngữ cảnh |
| --- | --- | --- |
| Initializer ném `IllegalStateException` | `ExceptionInInitializerError` với cause là `IllegalStateException` | `NoClassDefFoundError` |
| Initializer ném `AssertionError` | Chính `AssertionError` được truyền ra | `NoClassDefFoundError` |

JLS có những chi tiết đặc biệt, chẳng hạn việc không cấp phát được đối tượng lỗi bọc trong điều kiện thiếu bộ nhớ. Bảng mô tả **đường thực thi bình thường được kiểm chứng trong ví dụ**, không phải bảo đảm tuyệt đối của mọi JVM trong mọi điều kiện sự cố.

Lưu ý phạm vi: tên `BrokenPlugin` trong ví dụ chỉ là tên class lồng để minh họa, không yêu cầu hệ thống plugin thật.

## 11. Khởi tạo lớp và khởi tạo đối tượng là hai quá trình khác nhau

Một constructor hoặc instance field initializer có thể ném `IllegalStateException` khi tạo đối tượng:

~~~java
class UserSession {
    UserSession() {
        throw new IllegalStateException("phiên không hợp lệ");
    }
}
~~~

Nếu `new UserSession()` thất bại, điều đó **không tự động** có nghĩa class `UserSession` ở trạng thái class initialization lỗi. Các lần gọi constructor tiếp theo có thể vẫn được phép và có thể lại thất bại vì cùng nguyên nhân.

Ngược lại, lỗi từ một **static initializer** có thể dẫn tới `ExceptionInInitializerError` hoặc truyền một `Error` ra ngoài, rồi class bị đánh dấu erroneous.

Bảng phân biệt:

| Nơi thất bại | Lỗi người gọi thường quan sát | Có tự làm lớp thành erroneous? |
| --- | --- | --- |
| Thân instance constructor | Ngoại lệ được ném từ constructor | Không, chỉ vì constructor thất bại |
| Instance field initializer | Ngoại lệ từ quá trình tạo object | Không, chỉ vì instance initializer thất bại |
| Static field initializer | Có thể là `ExceptionInInitializerError` hoặc một `Error` gốc | Có thể, nếu class initialization kết thúc bất thường |
| Static initializer block | Tương tự static field initializer | Có thể |
| Liên kết/nạp class thất bại | Một loại `LinkageError` phù hợp | Tùy loại lỗi và giai đoạn JVM đang thực hiện |

Một static initializer **không được để checked exception thoát trực tiếp** theo cách công bố `throws` trên constructor. Nếu nó gọi API ném `IOException`, phải xử lý hoặc chuyển thành một unchecked exception đúng hợp đồng; khi unchecked exception đó thoát trong class initialization, nó có thể được bọc thành `ExceptionInInitializerError`.

## 12. `AssertionError` và từ khóa `assert`

`AssertionError` là một lớp `Error` thường gắn với kiểm tra **bất biến lập trình**. Java có cú pháp:

~~~java
assert condition : "thông điệp kiểm tra";
~~~

Khi assertion **được bật** cho lớp tương ứng, nếu `condition` là `false`, Java ném `AssertionError`. Khi assertions bị tắt, câu lệnh `assert` không thực hiện kiểm tra đó.

Vì vậy **không dùng `assert` cho validation đầu vào bắt buộc**, ví dụ xác thực mật khẩu, quyền truy cập, số tiền hoặc ràng buộc dữ liệu bên ngoài. Những kiểm tra ấy phải hoạt động dù JVM có bật assertions hay không.

Với tiền điều kiện API, thường dùng:

~~~java
static void pay(int amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("amount phải dương");
    }
}
~~~

`IllegalArgumentException` là loại unchecked thuộc nhánh `RuntimeException` và phù hợp cho đầu vào sai. Một bug bất biến nội bộ có thể được biểu diễn bằng `AssertionError` hoặc ngoại lệ khác theo chính sách dự án, nhưng nó không thay thế validation.

Không đặt side effect bắt buộc trong biểu thức `assert` vì side effect có thể không xảy ra khi assertions bị tắt.

## 13. Java 17: so sánh `assert` bật và tắt

Tệp `AssertionsModeDemo.java`:

~~~java
public class AssertionsModeDemo {
    static void check(int value) {
        assert value >= 0 : "giá trị âm";
        System.out.println("Tiếp tục với: " + value);
    }

    public static void main(String[] args) {
        System.out.println("Assertions bật: "
                + AssertionsModeDemo.class.desiredAssertionStatus());
        try {
            check(-1);
        } catch (AssertionError err) {
            System.out.println("AssertionError: " + err.getMessage());
        }
    }
}
~~~

Biên dịch một lần:

~~~bash
javac --release 17 -encoding UTF-8 AssertionsModeDemo.java
~~~

Chạy với assertions **tắt**:

~~~bash
java -da AssertionsModeDemo
~~~

Stdout:

~~~text
Assertions bật: false
Tiếp tục với: -1
~~~

Chạy với assertions **bật**:

~~~bash
java -ea AssertionsModeDemo
~~~

Stdout:

~~~text
Assertions bật: true
AssertionError: giá trị âm
~~~

`-ea` là tùy chọn bật assertions, `-da` là tùy chọn tắt. Khi chạy trên môi trường khác, các cài đặt assertion có thể được cấu hình theo package/class; ví dụ dùng cờ minh bạch để giữ kết quả ổn định.

Phần quan trọng nhất là **cùng một mã nguồn có thể không chạy kiểm tra `assert` khi bị tắt**. Đó là lý do assertion chỉ nên dùng cho giả định/bất biến nội bộ thích hợp, không cho mọi điều kiện bắt buộc của hệ thống.

## 14. Phạm vi `catch` phù hợp: quan sát, thu dọn và khôi phục

Khi xử lý `Error`, cần tách ba mục tiêu:

| Mục tiêu | Có thể phù hợp? | Cảnh báo |
| --- | --- | --- |
| **Quan sát**: ghi nhận loại, stack trace, metric, trạng thái tác vụ | Có, ở ranh giới rất rõ; ưu tiên cơ chế giám sát đáng tin | Việc log có thể thất bại khi hết bộ nhớ hoặc tài nguyên |
| **Thu dọn**: giải phóng resource trong `finally` hoặc `try-with-resources` | Thường thích hợp trong luồng thoát Java thông thường | Không bảo đảm nếu JVM/process dừng cưỡng bức, `Runtime.halt` hoặc lỗi nghiêm trọng khiến tiến trình không thể tiếp tục |
| **Khôi phục**: nuốt `Error` rồi cho request/worker tiếp tục như không có gì | Thường không nên | Chỉ xem xét cho những lỗi được thiết kế có ranh giới, đã chứng minh trạng thái còn hợp lệ |

Đừng nhầm việc **có thể bắt một `Error`** với việc **đã khôi phục nguyên nhân gây lỗi**. Ví dụ một `NoSuchMethodError` do deployment lệch version không được khắc phục chỉ bằng một nhánh `catch (NoSuchMethodError)`.

Phần lớn code nghiệp vụ nên chỉ bắt các loại `Exception` cụ thể có thể xử lý. Ở một bộ điều phối tác vụ, nếu thực sự cần ghi lại lỗi nghiêm trọng, có thể có tầng giám sát ngoài luồng/ngoài tiến trình; việc phục hồi tài nguyên/chạy lại cần đánh giá riêng theo loại lỗi và trạng thái hệ thống.

## 15. `finally` và `try-with-resources` khi một `Error` được ném

Khi Java thực hiện đường thoát bất thường bình thường vì một `Error`, các cơ chế cleanup trong ngôn ngữ vẫn có thể được thực thi:

~~~java
try {
    throw new AssertionError("sự cố");
} finally {
    System.out.println("Cleanup trong finally");
}
~~~

Thông thường, dòng trong `finally` được thực hiện **trước khi `AssertionError` tiếp tục lan truyền**. Điều này không có nghĩa bạn nên catch Error.

Tương tự, `try-with-resources` gọi `close()` khi thoát, kể cả khi phần thân ném `Error`. Nếu `close()` còn ném ngoại lệ, ngoại lệ đóng có thể được đính kèm thành **suppressed** của `Error` chính.

Điều cần lưu ý khi soạn ví dụ: nếu `close()` công bố `throws IOException`, compiler vẫn xét **checked exception từ implicit call tới `close()`**. Dù phần thân ví dụ cố ý ném `AssertionError`, bạn vẫn cần bắt hoặc khai báo `IOException` có thể phát sinh từ cleanup.

## 16. Java 17: `Error` vẫn có thể nhận suppressed exception từ cleanup

Tệp `ErrorCleanupDemo.java`:

~~~java
import java.io.IOException;

public class ErrorCleanupDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đóng tài nguyên");
            throw new IOException("không đóng được");
        }
    }

    public static void main(String[] args) throws IOException {
        try (Resource ignored = new Resource()) {
            throw new AssertionError("lỗi kiểm tra");
        } catch (AssertionError err) {
            System.out.println("Error chính: " + err.getMessage());
            System.out.println("Số suppressed: " + err.getSuppressed().length);
            System.out.println("Suppressed: "
                    + err.getSuppressed()[0].getMessage());
        } finally {
            System.out.println("Finally chạy");
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 ErrorCleanupDemo.java
java ErrorCleanupDemo
~~~

Stdout chính xác:

~~~text
Đóng tài nguyên
Error chính: lỗi kiểm tra
Số suppressed: 1
Suppressed: không đóng được
Finally chạy
~~~

Giải thích:

1. Phần thân `try` ném `AssertionError`.
2. Java tự gọi `Resource.close()`; method in `Đóng tài nguyên` rồi ném `IOException`.
3. `AssertionError` vẫn là lỗi chính; `IOException` từ cleanup được thêm như **suppressed exception**.
4. `catch (AssertionError)` của ví dụ in lỗi chính và lỗi phụ, rồi `finally` in thông báo kết thúc.
5. `main` khai báo `throws IOException` để đáp ứng quy tắc kiểm tra checked exception của `close()`. Đường thực thi cụ thể này vẫn giữ `AssertionError` làm lỗi chính và không để `IOException` thoát độc lập.
6. Trong code tổng quát phải kiểm tra độ dài của `getSuppressed()` trước khi truy cập `[0]`; ví dụ cố định biết chính xác có một lỗi đóng.

Ví dụ chỉ mô phỏng một `AssertionError` chủ động ném. Nó **không chứng minh** mọi lỗi JVM thảm khốc đều để `finally` hoàn tất. Nếu JVM chết/tiến trình bị `kill` hoặc `Runtime.halt`, Java không hứa cleanup sẽ được thực thi.

## 17. `Error` trong một worker thread có nhất thiết làm chết cả JVM?

Không. Khi một `Throwable` không được bắt thoát khỏi entry point của **một luồng**, luồng đó có thể kết thúc và cơ chế **uncaught exception handling** có thể nhận thông tin lỗi. Những luồng khác có thể vẫn chạy, tùy kiến trúc chương trình.

Ví dụ minh họa đơn luồng worker, không gây lỗi JVM thật.

Tệp `WorkerThreadErrorDemo.java`:

~~~java
public class WorkerThreadErrorDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                () -> { throw new AssertionError("worker thất bại"); },
                "worker-demo");

        worker.setUncaughtExceptionHandler((thread, error) ->
                System.out.println("Uncaught tại " + thread.getName()
                        + ": " + error.getClass().getSimpleName()));

        worker.start();
        worker.join();
        System.out.println("Main tiếp tục");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 WorkerThreadErrorDemo.java
java WorkerThreadErrorDemo
~~~

Stdout chính xác:

~~~text
Uncaught tại worker-demo: AssertionError
Main tiếp tục
~~~

Điều này chỉ chứng minh: **một `AssertionError` không bắt trong một worker có thể chấm dứt luồng worker, gọi handler và vẫn để luồng main tiếp tục**.

Không được suy ra rằng nếu luồng main còn sống thì hệ thống luôn có thể phục hồi. Worker có thể đang giữ lock, đang xử lý trạng thái nửa chừng, hoặc có giao dịch chưa nhất quán; cần phân tích ranh giới tác vụ và trạng thái trước khi chạy lại công việc.

Cũng không nên dùng `UncaughtExceptionHandler` làm nơi nỗ lực phục hồi đầy đủ sau `OutOfMemoryError`. Handler nên tối giản, không phụ thuộc giả định rằng còn đủ tài nguyên để thực hiện các thao tác phức tạp.

## 18. Nguyên tắc `catch` trong code nghiệp vụ

Các ví dụ thường nên theo mẫu:

~~~java
// Minh họa trong phương thức có thể ném IOException.
try {
    readData();
} catch (java.io.IOException ex) {
    // Xử lý tình huống I/O đã được hợp đồng công bố.
}
~~~

Hoặc đối với một request boundary cần phân loại nhiều lỗi recoverable:

~~~java
try {
    processRequest();
} catch (IllegalArgumentException ex) {
    // Phản hồi lỗi đầu vào theo hợp đồng của API.
} catch (Exception ex) {
    // Ghi nhận thất bại không dự kiến trong nhánh Exception.
    // Quyết định có trả response hoặc kết thúc tác vụ dựa trên trạng thái.
}
~~~

Đây là **mã minh họa không độc lập**, cần định nghĩa `readData()` và `processRequest()`. Điểm đáng nhớ: nhánh `catch (Exception)` **không bao gồm** `Error`. Đây thường là một ưu điểm vì nó tránh nuốt các lỗi nghiêm trọng ngoài phạm vi nghiệp vụ thông thường.

Chỉ thêm `catch (Error)` hoặc `catch (Throwable)` khi có lý do đặc biệt: ví dụ một **ranh giới giám sát**, một test framework, hoặc một thành phần hạ tầng cần tối thiểu hóa mất thông tin và **phải có chính sách sau khi bắt**. Không để một bộ xử lý HTTP tùy tiện chuyển `OutOfMemoryError` thành mã lỗi bình thường rồi tiếp tục nhận yêu cầu mới.

## 19. Nếu buộc phải quan sát `Error` ở ranh giới hạ tầng

Một ý tưởng cho ranh giới rất hẹp:

~~~java
// MÃ MINH HỌA, không phải chính sách recovery tổng quát.
try {
    runWorker();
} catch (Error err) {
    recordCriticalFailureMinimal(err);
    throw err;
}
~~~

`runWorker()` và `recordCriticalFailureMinimal(...)` là các hàm phụ thuộc hệ thống. Logic trên **không nuốt Error**: sau ghi nhận, nó ném lại để cấp cao hơn biết rằng tác vụ đã kết thúc bất thường.

Cảnh báo:

- `recordCriticalFailureMinimal` vẫn có thể thất bại vì thiếu bộ nhớ hoặc lỗi tương tự.
- Nếu ghi nhận lỗi ném lỗi mới, bạn có thể vô tình che `Error` gốc. Cần thiết kế đường giám sát riêng và tối giản.
- Bắt và ném lại một `Error` không tự rollback giao dịch hoặc hoàn nguyên các side effect.
- Với JVM crash/termination hoặc một số lỗi native, code Java có thể không còn cơ hội thực thi handler.
- Nếu mục tiêu chỉ là thu dọn resource thông thường, dùng `finally` hoặc `try-with-resources` thay vì catch rộng.

Trong nhiều ứng dụng, **không viết `catch(Error)`** mà dùng giám sát tiến trình/container, cơ chế restart có kiểm soát và logs/dumps được cấu hình sẵn là lựa chọn thực tế và an toàn hơn.

## 20. Một `Error` có thể được bọc bởi framework không?

Có những framework hoặc API không đưa nguyên đối tượng ra ngoài ở mọi tầng. Chúng có thể bọc `Throwable` hoặc chứa nó trong kết quả async, tùy hợp đồng của API.

Ví dụ các API `Future`/`Executor` có cơ chế nhận lỗi của tác vụ thông qua result hoặc một `ExecutionException` khi gọi `Future.get()`; cause bên trong có thể là `Error` gốc. Điều này không biến `Error` thành một lỗi nghiệp vụ recoverable.

Khi phân tích:

1. Xem **loại ngoài cùng** đang được bắt.
2. Kiểm tra **`getCause()`** (và những cause sâu hơn).
3. Xem **`getSuppressed()`** nếu có cleanup/close.
4. Đối chiếu stack trace và mã xử lý tại boundary.
5. Không thay đổi chính sách chỉ vì có một wrapper thuộc nhánh `Exception`.

Ví dụ nếu có `ExecutionException` bao `OutOfMemoryError`, việc caller bắt được `ExecutionException` không đồng nghĩa việc có thể lặp lại tác vụ an toàn. Cần đánh giá **nguyên nhân thực tế** và trạng thái tài nguyên.

## 21. Bảng quyết định: khi nào nên bắt, khi nào nên để lan truyền?

| Ngữ cảnh | Hướng tiếp cận ưu tiên | Giải thích |
| --- | --- | --- |
| Validation request sai | Dùng/bắt exception nghiệp vụ hoặc `IllegalArgumentException` thích hợp | Có thể phản hồi rõ ràng cho caller |
| File/network thất bại | Xử lý/bọc `IOException` theo hợp đồng | Có đường khôi phục hoặc báo lỗi được công bố |
| `AssertionError` trong test | Test runner/framework có thể bắt để ghi test thất bại | Đây là ranh giới được thiết kế để quan sát lỗi test |
| `AssertionError` về bất biến sản phẩm | Thường xem là bug nghiêm trọng; không nuốt | Trạng thái nghiệp vụ có thể không đáng tin |
| `NoSuchMethodError` sau deploy | Dừng/đánh dấu deployment lỗi, sửa dependency | Retry cùng code/dependency không sửa được liên kết |
| `ExceptionInInitializerError` | Đọc cause, sửa initializer/cấu hình; không retry class mù quáng | Class có thể đã thành erroneous |
| `OutOfMemoryError` | Giám sát/heap dump/restart có kiểm soát; không catch đại trà | Khả năng phục hồi trong cùng tiến trình không đáng tin |
| `StackOverflowError` | Sửa thuật toán/đệ quy, xem dữ liệu chu kỳ hoặc stack limit | Retry cùng lời gọi có thể tái hiện ngay |
| Ranh giới quản lý worker | Có thể ghi nhận tối thiểu, bảo toàn/rethrow hoặc để uncaught handler nhận | Chỉ phục hồi khi chứng minh được ranh giới trạng thái |
| `finally`/`try-with-resources` | Dùng cho cleanup bình thường | Không hứa cleanup trong mọi kiểu dừng tiến trình |

Bảng là hướng dẫn thực hành, không phải bảo đảm rằng mọi `Error` đều khiến hệ thống không thể tiếp tục. Mức độ xử lý phụ thuộc loại lỗi, lớp phần mềm và khả năng cô lập tác vụ thực tế.

## 22. Các nhầm lẫn phổ biến và cách sửa

| Nhầm lẫn | Thực tế |
| --- | --- |
| `Error` là subclass của `Exception` | Không. Chúng là hai nhánh dưới `Throwable`. |
| `catch(Exception)` xử lý cả OOM/StackOverflow | Không bắt được `OutOfMemoryError`/`StackOverflowError`. |
| `Error` luôn khiến cả JVM dừng ngay | Một `Error` không được bắt có thể chỉ chấm dứt luồng; không suy ra an toàn toàn tiến trình. |
| `Error` không thể bị người lập trình tạo | Code có thể `throw new AssertionError(...)` hoặc tạo Error khác. |
| `Error` bắt buộc phải khai báo `throws` | Không; `Error` thuộc unchecked. |
| Bắt và bỏ qua `OutOfMemoryError` giúp ứng dụng khỏe lại | Không có bảo đảm đó; còn có thể làm lỗi dữ liệu/trạng thái. |
| `NoClassDefFoundError` luôn là mất file JAR | Có thể do class initialization đã thất bại trước đó hoặc nguyên nhân linkage khác. |
| `ExceptionInInitializerError` xuất hiện sau mọi lỗi constructor instance | Nó gắn với **static/class initialization**, không phải mọi constructor exception. |
| Static initializer ném `AssertionError` thì JVM luôn bọc `ExceptionInInitializerError` | Nếu đối tượng gốc đã là `Error`, nó thường được truyền trực tiếp. |
| Sau khi bắt `ExceptionInInitializerError` có thể thử lại class y hệt và thành công | Class có thể ở trạng thái erroneous; lượt sau thường là `NoClassDefFoundError`. |
| `finally` chắc chắn chạy kể cả JVM bị kill/halt | Không có bảo đảm trong trường hợp process dừng cưỡng bức hoặc không thể tiếp tục. |
| `try-with-resources` không chạy `close()` khi body ném Error | Theo ngữ nghĩa Java bình thường, resource đã mở vẫn được đóng. |
| Mọi `AssertionError` đều do từ khóa `assert` | Code có thể chủ động ném `new AssertionError` dù assertions bị tắt. |
| Từ khóa `assert` phù hợp để xác thực input bắt buộc | Sai vì assertion có thể bị tắt. |
| Ghi log `ex.getMessage()` là đủ | Có thể mất stack trace, cause và suppressed exceptions. |

## 23. Checklist điều tra một `Error` trong ứng dụng Java

1. **Nhận diện đúng class**: `OutOfMemoryError`, `StackOverflowError`, `LinkageError`, `AssertionError`, v.v.
2. **Ghi lại thông điệp và stack trace** nếu môi trường còn đủ khả năng; không parse message JVM thành hợp đồng ổn định.
3. **Tìm cause**: đặc biệt `ExceptionInInitializerError.getCause()` và các wrapper async/framework.
4. **Kiểm tra suppressed** nếu xảy ra lỗi trong `try-with-resources` hoặc cleanup.
5. **Xác định thời điểm**: lúc nạp/liên kết class, khởi tạo static, tạo object, xử lý request, đóng tài nguyên hay chạy background worker?
6. Với `LinkageError`, **đối chiếu classpath/module path, JDK runtime, JAR dependencies, version build và deploy**.
7. Với `StackOverflowError`, kiểm tra **đệ quy, điều kiện dừng, chu trình dữ liệu** trước khi tăng stack size.
8. Với `OutOfMemoryError`, kiểm tra **heap/native memory, số lượng object, leak, cache, request size và tài nguyên của container** bằng dữ liệu giám sát phù hợp.
9. Với lỗi static initialization, xem **lần thất bại đầu tiên** trước khi lớp chuyển sang trạng thái erroneous.
10. **Kiểm tra tính nhất quán của dữ liệu/tác vụ** trước bất kỳ quyết định retry nào.
11. **Phân biệt tiếp tục một luồng với hệ thống thật sự khỏe mạnh**; việc một thread sống không đủ chứng minh recovery.
12. Xây dựng **giới hạn bộ nhớ/tài nguyên, health check, restart và giám sát ngoài tiến trình** trước sự cố, thay vì kỳ vọng code trong `catch (OutOfMemoryError)` cứu mọi tình huống.

Không chủ động làm cạn heap hoặc chạy đệ quy vô hạn trên môi trường chung để “kiểm thử xử lý Error”. Hãy dùng unit test mô phỏng `AssertionError` và các công cụ chẩn đoán được thiết kế cho môi trường thử nghiệm cô lập nếu thật sự cần kiểm thử khả năng chịu lỗi.

## 24. Câu hỏi tự kiểm tra

**Câu 1.** `Error` có thuộc `Exception` không?

**Đáp án:** Không. Cả hai là nhánh của `Throwable`.

**Câu 2.** `catch (Exception)` có bắt `OutOfMemoryError` không?

**Đáp án:** Không.

**Câu 3.** `Error` là checked hay unchecked?

**Đáp án:** Unchecked; Java không buộc `throws Error`.

**Câu 4.** `OutOfMemoryError` có chắc chắn khiến toàn bộ JVM dừng ngay không?

**Đáp án:** Không thể kết luận như vậy từ riêng kiểu lỗi. Nhưng đây vẫn là tình trạng nghiêm trọng, không nên mặc định ứng dụng còn an toàn để tiếp tục.

**Câu 5.** Static initializer ném `IllegalStateException` có thể xuất hiện với lỗi ngoài cùng nào?

**Đáp án:** `ExceptionInInitializerError`, với `getCause()` giữ lỗi gốc trong trường hợp thông thường.

**Câu 6.** Vì sao lượt dùng lớp kế tiếp sau một lần khởi tạo thất bại có thể là `NoClassDefFoundError`?

**Đáp án:** JVM đánh dấu class đang ở trạng thái khởi tạo lỗi (erroneous) và không chạy lại initializer trong cùng ngữ cảnh đó.

**Câu 7.** Nếu static initializer ném trực tiếp `AssertionError`, có nhất thiết bị bọc thành `ExceptionInInitializerError` không?

**Đáp án:** Không. `Error` gốc thường được truyền ra trực tiếp ở lượt đầu.

**Câu 8.** Có thể dùng từ khóa `assert` để kiểm tra số tiền phải dương cho mọi request không?

**Đáp án:** Không. Assertions có thể bị tắt; phải dùng validation hoạt động độc lập với cờ `-ea`.

**Câu 9.** Khi `try-with-resources` có `AssertionError` từ body và `IOException` từ `close()`, lỗi nào là chính?

**Đáp án:** Thường `AssertionError` từ body là lỗi chính, còn `IOException` đóng tài nguyên được gắn dưới dạng suppressed.

**Câu 10.** Tại sao `main` trong ví dụ cleanup vẫn ghi `throws IOException`?

**Đáp án:** Vì compiler phải kiểm tra checked exception mà `close()` có thể ném từ lời gọi cleanup ngầm, dù đường runtime cố định trong ví dụ có `AssertionError` làm lỗi chính.

**Câu 11.** Worker thread ném `AssertionError` không bắt thì main thread nhất thiết dừng theo không?

**Đáp án:** Không. Worker có thể kết thúc, uncaught handler được gọi, còn main tiếp tục. Nhưng tính an toàn để chạy tiếp vẫn cần được đánh giá.

**Câu 12.** Vì sao `catch (Throwable)` quanh mọi nghiệp vụ là một anti-pattern?

**Đáp án:** Vì nó gom cả lỗi recoverable và lỗi nghiêm trọng mà tiến trình có thể không còn trạng thái đáng tin; dễ nuốt lỗi và tiếp tục sai.

## 25. Tài liệu tham khảo chính thức

- [Java SE 17 API — `Error`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Error.html): ý nghĩa của Error, phân cấp và trạng thái unchecked.
- [Java SE 17 API — `VirtualMachineError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VirtualMachineError.html), [`OutOfMemoryError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html), [`StackOverflowError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackOverflowError.html): những sự cố tài nguyên/VM.
- [Java SE 17 API — `LinkageError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html), [`NoClassDefFoundError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): lỗi liên kết và nạp lớp.
- [Java SE 17 API — `ExceptionInInitializerError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): lỗi phát sinh khi khởi tạo static.
- [Java SE 17 API — `AssertionError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AssertionError.html): lỗi kiểm tra assertion.
- [Java Language Specification 17 — §11.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): ngoại lệ checked và unchecked.
- [Java Language Specification 17 — §12.4.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): thủ tục khởi tạo class, `ExceptionInInitializerError` và trạng thái erroneous.
- [Java Language Specification 17 — §14.10](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.10): câu lệnh `assert`.
- [Java Language Specification 17 — §14.20](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): `try`, `catch`, `finally` và `try-with-resources`.
- [Java SE 17 API — `Thread.UncaughtExceptionHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html): quan sát lỗi không được bắt trong một luồng.

**Ghi nhớ:** `Error` là nhánh **unchecked** của `Throwable` và thường báo điều kiện nghiêm trọng ngoài phạm vi phục hồi của mã nghiệp vụ thông thường. Một số `Error` đến từ JVM, quá trình liên kết/khởi tạo lớp hoặc kiểm tra assertion; chúng không nhất thiết làm toàn bộ JVM dừng ngay, nhưng **bắt được không đồng nghĩa sửa được lỗi**. Ưu tiên xử lý các `Exception` có hợp đồng rõ, dùng cleanup chuẩn, giữ cause/suppressed, và chỉ quan sát `Error` ở những ranh giới hẹp với chính sách an toàn; không nuốt `Error` để tiếp tục như thể hệ thống vẫn bình thường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-002">AssertionError và câu lệnh assert</a>

<details>
<summary>Xem chi tiết</summary>

# `AssertionError` và câu lệnh `assert` trong Java

`assert` là một câu lệnh của Java dùng để kiểm tra **giả định nội bộ của chương trình** (*internal invariant*): một điều kiện mà lập trình viên tin rằng phải đúng nếu phần còn lại của mã hoạt động theo thiết kế. Khi các assertion được **bật** và điều kiện sai, Java ném `java.lang.AssertionError`. Khi assertion được **tắt**, Java **không đánh giá** điều kiện lẫn biểu thức thông báo đi kèm.

Đây là khác biệt quan trọng với validation thông thường: `assert` **mặc định bị tắt** trong một lần chạy JVM phổ biến, và có thể bật/tắt qua tùy chọn khi khởi chạy. Vì vậy, không dùng `assert` làm cơ chế duy nhất để xác minh tham số public API, dữ liệu người dùng, quyền truy cập, số dư hoặc điều kiện có ảnh hưởng đến tính đúng đắn khi chạy production.

Bài thuộc Java Core – Exception, ngữ cảnh **Error và khởi tạo**, tập trung vào cú pháp và cơ chế `assert`, `AssertionError` trong cây kế thừa, lệnh `-ea`/`-da`, khác biệt giữa assertion và exception hợp đồng, đánh giá biểu thức, side effect, liên hệ class initialization và ví dụ Java 17+ chạy độc lập.

## 1. Nhận diện `AssertionError`

Phân cấp rút gọn:

~~~text
java.lang.Object
    └── java.lang.Throwable
        ├── java.lang.Exception
        │   └── java.lang.RuntimeException
        │       ├── IllegalArgumentException
        │       ├── IllegalStateException
        │       └── NullPointerException
        └── java.lang.Error
            └── java.lang.AssertionError
~~~

Các hệ quả:

- `AssertionError` là **unchecked throwable** vì kế thừa `Error`.
- Trình biên dịch **không yêu cầu** phải viết `throws AssertionError`.
- `catch (Exception e)` **không bắt** `AssertionError`, vì `Error` không nằm trong nhánh `Exception`.
- `catch (AssertionError e)`, `catch (Error e)` hoặc `catch (Throwable e)` có thể bắt được nó về mặt kiểu, nhưng **không nên bắt rồi bỏ qua mặc định** trong code nghiệp vụ.
- `AssertionError` thường báo một **giả định lập trình hoặc phép kiểm tra nội bộ bị vi phạm**, không phải một cách biểu diễn mọi lỗi nhập liệu hoặc lỗi kinh doanh thông thường.

**Tên `Error` không có nghĩa mọi `AssertionError` đều do JVM tự phát sinh.** Code có thể tự viết `throw new AssertionError("...");`, hoặc sử dụng câu lệnh `assert` để JVM thực hiện phép kiểm tra tùy trạng thái bật/tắt assertion.

## 2. Cú pháp của câu lệnh `assert`

Java có hai dạng:

~~~java
assert condition;
~~~

và:

~~~java
assert condition : detailExpression;
~~~

`condition` phải có kiểu `boolean` hoặc `Boolean` (nếu là `Boolean`, cần unboxing thành giá trị boolean khi đánh giá). Biểu thức `detailExpression` là một biểu thức không được có kiểu `void`; nó giúp tạo thông điệp chi tiết khi assertion thất bại.

Ví dụ:

~~~java
int size = 5;
assert size >= 0;
assert size < 100 : "size vượt giới hạn nội bộ: " + size;
~~~

Các dòng này là **mảnh mã Java** trong một phương thức hoặc block. Nếu assertions đã bật:

- Khi điều kiện đúng, Java tiếp tục, không ném lỗi.
- Khi điều kiện sai, Java ném `AssertionError`.
- Nếu có `: detailExpression` và điều kiện sai, Java tính biểu thức detail để xây `AssertionError`.

Để chạy trong một chương trình, bạn không phải import `assert` hoặc `AssertionError`; `assert` là từ khóa của ngôn ngữ, còn `AssertionError` thuộc `java.lang`.

### `assert` là statement, không phải method

`assert condition;` không trả về một giá trị để dùng như một biểu thức:

~~~java
boolean ok = assert (5 > 0); // KHÔNG BIÊN DỊCH.
~~~

Đây là phản ví dụ. `assert` là một câu lệnh, không phải phép gọi method kiểu `check(...)` và cũng không phải toán tử có giá trị trả về.

## 3. Cơ chế khi assertion được bật

Giả sử:

~~~java
int count = -1;
assert count >= 0 : "count must not be negative";
System.out.println("continue");
~~~

Nếu assertions bật cho class đang chạy, điều kiện `count >= 0` là `false`. Java tạo và ném `AssertionError` với thông điệp được tính. Dòng `System.out.println("continue")` **không chạy**, trừ khi có khối xử lý thích hợp làm thay đổi luồng điều khiển.

Luồng logic:

~~~text
Đến câu lệnh assert
          |
          v
Assertions được bật cho class?
     |                 |
    Không             Có
     |                 |
 Bỏ qua toàn bộ    Đánh giá condition
 assert                 |
                    +---+---+
                    |       |
                  true     false
                    |       |
                Tiếp tục  Nếu có: tính detail
                            |
                            v
                       throw AssertionError
~~~

Nếu detail chứa số hay object, constructor thích hợp của `AssertionError` tạo phần mô tả lỗi theo ngữ nghĩa của API. **Không nên coi cách format thông điệp tự động là một giao thức máy đọc ổn định.**

### Điều kiện `true` thì detail không được đánh giá

~~~java
assert 2 + 2 == 4 : expensiveDiagnostic();
~~~

Nếu assert bật và điều kiện đúng, `expensiveDiagnostic()` **không chạy**. Nếu assert tắt, cả điều kiện lẫn `expensiveDiagnostic()` đều không chạy. Chỉ khi assert bật **và** điều kiện sai thì biểu thức detail mới được đánh giá.

Đây là lý do các detail expression nên đơn giản, không thay đổi trạng thái chương trình và không ném lỗi phụ gây che mất nguyên nhân gốc.

## 4. Cơ chế khi assertion bị tắt

Với mặc định thông thường khi không bật assertions qua tham số JVM, câu lệnh:

~~~java
assert calculateCondition() : createDetail();
~~~

được **bỏ qua** về mặt đánh giá runtime:

- `calculateCondition()` không được gọi.
- `createDetail()` không được gọi.
- Không có `AssertionError` do chính câu lệnh assert này ném.
- Những lệnh xung quanh vẫn chạy bình thường.

Điều này dễ gây hiểu nhầm khi thử chạy code trong IDE: một IDE có thể cấu hình `-ea` cho unit tests nhưng không bật cho một ứng dụng chạy bình thường. Cùng source code, nếu run configuration khác, một assertion có thể được kiểm tra ở môi trường này nhưng bị bỏ qua ở môi trường khác.

**Đừng suy ra rằng không thấy `AssertionError` nghĩa là invariant đã được xác minh**; hãy kiểm tra assertions đã được bật hay chưa.

### Assert không phải cơ chế bảo mật

Phản ví dụ nguy hiểm:

~~~java
assert currentUser.isAdmin() : "Permission denied";
deleteAllRecords();
~~~

Đây là mảnh mã minh họa, không phải mẫu bảo mật. Khi assertions tắt, không có kiểm tra quyền, và lệnh xóa vẫn chạy. Quyền truy cập phải được kiểm tra bằng cơ chế bảo mật/authorization luôn hoạt động theo thiết kế, không phải bằng `assert`.

## 5. Bật và tắt assertions bằng tùy chọn JVM

Các tùy chọn thường dùng:

| Tùy chọn | Ý nghĩa |
| --- | --- |
| `-ea` hoặc `-enableassertions` | Bật assertions cho các class không thuộc system classes theo phạm vi mặc định |
| `-da` hoặc `-disableassertions` | Tắt assertions theo phạm vi mặc định |
| `-ea:ten.lop` | Bật cho một class được nêu theo tên đủ điều kiện |
| `-ea:ten.package...` | Bật cho một package và các subpackage theo quy tắc tùy chọn |
| `-da:ten.lop` | Tắt cho một class cụ thể |
| `-esa` hoặc `-enablesystemassertions` | Bật assertions cho system classes |
| `-dsa` hoặc `-disablesystemassertions` | Tắt cho system classes |

Hai lệnh cho chương trình độc lập ở phần sau:

~~~bash
java -da AssertionLanguageDemo
java -ea AssertionLanguageDemo
~~~

Tham số `-ea`/`-da` phải được JVM nhận như **tùy chọn khởi chạy**, trước tên main class trong hai câu lệnh trên.

Dạng tên class/package bên dưới chỉ minh họa nếu các class tương ứng tồn tại trong classpath:

~~~bash
java -ea:com.example.orders.OrderService com.example.Main
java -ea:com.example.orders... com.example.Main
~~~

Đừng chép nguyên một package chưa tồn tại vào chương trình ví dụ chính và mong Java tự tạo class.

### Bật theo phạm vi không phải bật toàn bộ hệ thống một cách vô hạn

`-ea` không phải đồng nghĩa với “bật mọi assertion của mọi class trong toàn bộ JVM mà không ngoại lệ”. JVM có quy tắc riêng cho system classes và các tùy chọn `-esa`/`-dsa`. Khi chẩn đoán, cần biết class nào thực sự được bật.

Các môi trường build/test như Maven, Gradle hoặc IDE có thể truyền các JVM flags thông qua cấu hình runner. Chỉ nhìn câu lệnh `javac` là không đủ để biết assertions khi chạy có bật hay không.

## 6. `-ea` là tùy chọn runtime, không phải kiểm tra đặc biệt khi biên dịch

Biên dịch:

~~~bash
javac AssertionLanguageDemo.java
~~~

Chạy tắt/bật:

~~~bash
java -da AssertionLanguageDemo
java -ea AssertionLanguageDemo
~~~

**Không cần biên dịch hai lần.** Bytecode của cùng class có thể chạy với hai chế độ kiểm tra assertion khác nhau. Việc bật/tắt là lựa chọn của JVM/class loader khi class được khởi tạo theo quy tắc liên quan.

Đừng sửa cú pháp Java thành `assert enabled(...)` chỉ vì muốn bật assertions; `assert` là một từ khóa của ngôn ngữ, còn chính sách bật/tắt do JVM quản lý.

## 7. `Class.desiredAssertionStatus()` giúp kiểm tra cấu hình

Java cung cấp:

~~~java
System.out.println(MyClass.class.desiredAssertionStatus());
~~~

Trong mảnh này, `MyClass` là class giả định. Method `desiredAssertionStatus()` cho biết trạng thái assertion mong muốn cho class theo cài đặt class loader. Nó hữu ích để **chẩn đoán cấu hình** trong ví dụ đơn giản.

Tuy nhiên, không nên tự viết một hệ thống nghiệp vụ dựa vào việc liên tục gọi method đó thay vì dùng `assert` đúng cú pháp. Trong các trường hợp khởi tạo class phức tạp, trạng thái assertion còn liên quan đến quy tắc class loading/initialization; không dùng method này như một cơ chế tự động thay thế mọi kiểm tra runtime.

Trong chương trình độc lập sắp tới, một class bình thường khi chạy `-da` sẽ hiển thị `false`; khi chạy `-ea` cho class đó sẽ hiển thị `true`.

## 8. Không dùng `assert` cho điều kiện đầu vào public API

Ví dụ sai:

~~~java
static int dividePositive(int numerator, int denominator) {
    assert denominator > 0 : "denominator must be positive";
    return numerator / denominator;
}
~~~

Nếu assertions tắt, input `denominator = -2` vẫn lọt qua và phương thức trả một kết quả âm, trái với hợp đồng “denominator must be positive”. Với `denominator = 0`, toán tử `/` sẽ ném `ArithmeticException`, nhưng đó không đảm bảo mọi trường hợp vi phạm điều kiện đều bị phát hiện đúng loại.

Cách đúng cho một **tiền điều kiện bắt buộc**:

~~~java
static int dividePositive(int numerator, int denominator) {
    if (denominator <= 0) {
        throw new IllegalArgumentException(
                "denominator must be positive");
    }
    return numerator / denominator;
}
~~~

Điều kiện này **luôn được đánh giá** khi method chạy. Khi đầu vào sai, caller thấy `IllegalArgumentException` theo hợp đồng.

### Kiểm tra null cũng không dùng assert

Không nên:

~~~java
static String trimRequired(String input) {
    assert input != null;
    return input.trim();
}
~~~

Khi assertions tắt, `input == null` vẫn tiến tới `trim()` và gây `NullPointerException` ở vị trí sử dụng, thay vì kiểm tra rõ ràng tại ranh giới.

Nên:

~~~java
static String trimRequired(String input) {
    java.util.Objects.requireNonNull(input, "input");
    return input.trim();
}
~~~

Nếu input khác null nhưng có thể rỗng/blank, cần thêm `isBlank()` hoặc các quy tắc tương ứng. `requireNonNull` chỉ kiểm tra tham chiếu null, không kiểm tra mọi điều kiện đầu vào.

## 9. Khi nào nên dùng `assert`?

Các tình huống thường có giá trị:

1. **Bất biến nội bộ** vừa được thiết lập bởi code của chính module và cần kiểm tra trong môi trường debug/test.
2. **Hậu điều kiện** (*postcondition*) mà thuật toán hứa phải đúng nếu các bước trước chính xác.
3. **Quan hệ giữa biến nội bộ** mà logic đã bảo đảm, ví dụ chỉ số được tính phải nằm trong miền trước khi chuyển đến một API khác.
4. **Nhánh không được kỳ vọng tới** theo một phép chứng minh trên các trạng thái đã đóng, nhưng không thể dùng assert làm biện pháp an toàn duy nhất nếu việc tiếp tục sẽ gây hậu quả.
5. **Phát hiện lỗi lập trình khi bật debug assertions** mà không bắt buộc chạy tất cả phép kiểm tra tốn kém trong production.

Ví dụ sau minh họa hậu điều kiện đơn giản của thuật toán:

~~~java
static int clampToRange(int value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min > max");
    }
    int result = Math.min(max, Math.max(min, value));
    assert result >= min && result <= max
            : "clamp result is outside expected bounds";
    return result;
}
~~~

Đây là một phương thức Java hợp lệ. `min > max` là input sai và luôn bị từ chối; assertion kiểm tra **hậu điều kiện nội bộ** mà thuật toán vừa tính. Khi `-ea` được bật, nếu code có bug khiến hậu điều kiện sai, `AssertionError` giúp phát hiện.

### Nguyên tắc không thể bỏ qua

Một assertion đúng nghĩa nên có đặc tính:

> Nếu loại bỏ toàn bộ lệnh `assert`, chương trình vẫn thực hiện logic nghiệp vụ và giữ các tiền điều kiện/điều kiện an toàn cần thiết.

Nếu xóa `assert` mà chương trình bỏ qua validation, không tăng biến đếm, không ghi dữ liệu hoặc làm thay đổi kết quả chính, assertion đang bị dùng sai.

## 10. Side effect trong điều kiện assert là lỗi thiết kế

Mẫu nguy hiểm:

~~~java
int counter = 0;
assert ++counter > 0;
System.out.println(counter);
~~~

Nếu assertions bật, `counter` tăng thành `1`. Nếu assertions tắt, `counter` **vẫn là 0**. Cùng input nhưng trạng thái chương trình khác nhau.

Đoạn trên là phản ví dụ cố ý; chỉ dùng để chứng minh **đánh giá điều kiện phụ thuộc `-ea`**.

Cách tách side effect:

~~~java
int counter = 0;
counter++;
assert counter > 0;
System.out.println(counter);
~~~

Giờ phép tăng `counter++` luôn xảy ra, bất kể trạng thái assertions. Phần assert chỉ kiểm tra giả định.

### Side effect có thể ẩn trong method

~~~java
assert saveRecordAndReturnSuccess();
~~~

Nếu hàm `saveRecordAndReturnSuccess()` ghi dữ liệu, thì thao tác ghi chỉ chạy khi assertions bật. Đây là bug nghiêm trọng.

Nên:

~~~java
boolean saved = saveRecordAndReturnSuccess();
assert saved : "record was not saved";
~~~

Đây là mảnh minh họa có method giả định. Tuy nhiên, nếu việc lưu không thành công là một lỗi nghiệp vụ có thể dự kiến, phải xử lý `saved == false` **bằng logic thường hoặc exception hợp đồng**, không chỉ assert. Assert chỉ phù hợp nếu việc `saved == false` thật sự là một bất biến không thể xảy ra theo thiết kế.

## 11. Biểu thức detail cũng không nên có side effect

Phản ví dụ:

~~~java
assert balance >= 0 : chargePenaltyAndDescribe();
~~~

Nếu điều kiện đúng hoặc assert tắt, `chargePenaltyAndDescribe()` không chạy. Nếu assert bật và điều kiện sai, nó chạy **trong đường tạo lỗi**. Vậy việc thu phí lại phụ thuộc trạng thái assertions — điều không chấp nhận được.

Đúng hơn:

~~~java
assert balance >= 0
        : "negative internal balance=" + balance;
~~~

Message nên ngắn, dễ chẩn đoán và không chứa dữ liệu nhạy cảm. Với những biểu thức detail đắt, việc tính chúng chỉ khi thất bại có thể hữu ích, nhưng chúng **không được thực hiện các thay đổi trạng thái bắt buộc**.

### Detail expression không chạy nếu điều kiện true

~~~java
assert value != null : expensiveExplain(value);
~~~

Nếu `value != null` và assertion bật, Java **không gọi `expensiveExplain`**. Nhưng nếu điều kiện false, detail có thể tự ném một ngoại lệ khác. Khi đó ngoại lệ phát sinh trong lúc tính detail có thể truyền ra, thay vì một `AssertionError` như người đọc kỳ vọng.

Do đó, đừng đưa lời gọi phức tạp hoặc không an toàn vào expression sau dấu `:`.

## 12. `AssertionError` khác các exception runtime thông thường

Bảng phân biệt:

| Điều kiện | Cơ chế thường phù hợp | Tại sao? |
| --- | --- | --- |
| Tham số của public method bằng null nhưng không cho phép | `Objects.requireNonNull` → `NullPointerException` | Tiền điều kiện luôn được kiểm tra |
| Số lượng được truyền là số âm | `IllegalArgumentException` | Giá trị đối số không hợp lệ |
| Object hiện chưa ở trạng thái hoạt động | `IllegalStateException` | Trạng thái receiver không cho phép thao tác |
| Mất kết nối file/network | `IOException` hoặc exception tầng tương ứng | Lỗi hạ tầng cần được xử lý theo hợp đồng |
| Bất biến nội bộ lẽ ra không thể sai | `assert` để kiểm chứng khi bật | Phát hiện bug trong đường chạy/debug |
| Một lỗi thuật toán phải luôn ngăn việc tiếp tục ngay cả khi `-ea` tắt | `throw new IllegalStateException(...)` hoặc loại phù hợp | Không phụ thuộc cờ JVM |
| Kết quả nghiệp vụ `DECLINED` hợp lệ | Kết quả/enum/record | Không phải giả định nội bộ bị phá vỡ |

Điểm khác biệt lớn nhất là **câu lệnh `assert` có thể hoàn toàn không chạy**. Điều kiện hợp đồng nghiệp vụ bắt buộc không được đặt duy nhất ở nơi có thể bị loại bỏ như vậy.

### `AssertionError` không giống `IllegalStateException`

Hai loại đều có thể gợi ý trạng thái sai, nhưng:

- `IllegalStateException` là `RuntimeException`, nằm trong nhánh `Exception`, thích hợp cho lỗi trạng thái mà caller có thể nhận theo hợp đồng.
- `AssertionError` nằm dưới `Error`, chủ yếu báo giả định lập trình/invariant nội bộ bị phá vỡ.
- `catch (Exception)` bắt `IllegalStateException` nhưng **không** bắt `AssertionError`.
- `new AssertionError(...)` tự ném ra **không** bị điều khiển bởi `-ea`; chỉ **câu lệnh ngôn ngữ `assert`** mới được bật/tắt như vậy.

Không đổi một lỗi trạng thái bình thường thành `AssertionError` chỉ vì muốn báo lỗi “mạnh hơn”.

## 13. Câu lệnh `assert` và `throw new AssertionError`

Hai cách sau khác nhau:

~~~java
assert false : "internal invariant failed";
~~~

và:

~~~java
throw new AssertionError("internal invariant failed");
~~~

Với câu lệnh `assert`:

- Khi `-ea` tắt, Java bỏ qua câu lệnh.
- Khi `-ea` bật, nó ném `AssertionError` vì điều kiện false.

Với `throw new AssertionError(...)`:

- Lỗi **luôn được ném** nếu đường code đó được thực hiện, bất kể `-ea` hay `-da`.
- Không cần khai báo checked exception.
- Có thể hữu ích trong một số trường hợp chứng minh nhánh không thể tới, nhưng cần cân nhắc làm thế có đúng hợp đồng hay không.

### Dùng `assert false` cho nhánh “không thể xảy ra”?

~~~java
switch (status) {
    case "OPEN" -> processOpen();
    case "CLOSED" -> processClosed();
    default -> {
        assert false : "unexpected status: " + status;
    }
}
~~~

Đây là mảnh minh họa cần `status` và hai method phù hợp. Khi assertions tắt, nhánh `default` **không làm gì** ngoài việc hoàn tất block. Nếu trạng thái mới xuất hiện, chương trình có thể tiếp tục im lặng.

Nếu yêu cầu **luôn dừng** khi trạng thái không hỗ trợ:

~~~java
switch (status) {
    case "OPEN" -> processOpen();
    case "CLOSED" -> processClosed();
    default -> throw new IllegalStateException(
            "unexpected status: " + status);
}
~~~

Dạng này biểu đạt hợp đồng an toàn độc lập trạng thái assertions. Với enum và switch expression trên các phiên bản Java mới, còn có những quy tắc kiểm tra tính đầy đủ tại compile-time; không nên phụ thuộc vào `assert false` để thay thế toàn bộ thiết kế xử lý trạng thái.

## 14. Java 17+ — chương trình độc lập minh họa khi tắt và bật `assert`

Chương trình sau cho thấy:

- Khi assertions tắt, điều kiện và message của `assert` đều không chạy.
- Khi bật `-ea`, điều kiện `true` chạy nhưng message không chạy; điều kiện `false` làm `AssertionError` xuất hiện.
- Kiểm tra input bằng `Objects.requireNonNull` và `IllegalArgumentException` **luôn hoạt động** dù `-ea` tắt.
- `throw new AssertionError` hoạt động độc lập cờ assertion.
- Chương trình chỉ bắt `AssertionError` để quan sát trong ví dụ học tập, **không khuyến nghị bỏ qua Error trong production**.

Lưu thành tệp **`AssertionLanguageDemo.java`**:

~~~java
import java.util.Objects;

public class AssertionLanguageDemo {
    private static int conditionCalls;
    private static int detailCalls;

    public static void main(String[] args) {
        System.out.println("enabled="
                + AssertionLanguageDemo.class.desiredAssertionStatus());

        assert condition(true) : detail();
        System.out.println(
                "trueCheck conditionCalls=" + conditionCalls
                        + ", detailCalls=" + detailCalls);

        try {
            assert condition(false) : detail();
            System.out.println("falseCheck completed");
        } catch (AssertionError error) {
            System.out.println(
                    "falseCheck caught=" + error.getMessage());
        }
        System.out.println(
                "afterFalse conditionCalls=" + conditionCalls
                        + ", detailCalls=" + detailCalls);

        try {
            validateRequest(null, 2);
        } catch (NullPointerException error) {
            System.out.println(
                    "nullInput="
                            + error.getClass().getSimpleName()
                            + ":" + error.getMessage());
        }

        try {
            validateRequest("A-1", 0);
        } catch (IllegalArgumentException error) {
            System.out.println(
                    "badQuantity=" + error.getMessage());
        }

        try {
            throw new AssertionError("explicit failure");
        } catch (AssertionError error) {
            System.out.println(
                    "explicit=" + error.getMessage());
        }

        System.out.println("done");
    }

    static boolean condition(boolean result) {
        conditionCalls++;
        return result;
    }

    static String detail() {
        detailCalls++;
        return "invariant failed";
    }

    static void validateRequest(String orderId, int quantity) {
        Objects.requireNonNull(orderId, "orderId");

        if (orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "orderId must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
    }
}
~~~

Biên dịch:

~~~bash
javac AssertionLanguageDemo.java
~~~

### Chạy chế độ assertions tắt

~~~bash
java -da AssertionLanguageDemo
~~~

**Stdout chính xác trong chế độ `-da`:**

~~~text
enabled=false
trueCheck conditionCalls=0, detailCalls=0
falseCheck completed
afterFalse conditionCalls=0, detailCalls=0
nullInput=NullPointerException:orderId
badQuantity=quantity must be positive
explicit=explicit failure
done
~~~

**Giải thích:**

1. `-da` tắt assertions cho class, nên `desiredAssertionStatus()` trả `false`.
2. Cả hai điều kiện `condition(true)` và `condition(false)` **không được gọi**, biến `conditionCalls` giữ `0`.
3. `detail()` cũng không chạy, `detailCalls` giữ `0`.
4. Nhánh có điều kiện false **không ném** `AssertionError` do assert, nên in `falseCheck completed`.
5. Hai precondition `requireNonNull`/`quantity <= 0` vẫn được thực hiện và vẫn ném loại exception đúng.
6. Lệnh `throw new AssertionError("explicit failure")` vẫn ném Error, vì đó không phải câu lệnh `assert`.

### Chạy chế độ assertions bật

~~~bash
java -ea AssertionLanguageDemo
~~~

**Stdout chính xác trong chế độ `-ea`:**

~~~text
enabled=true
trueCheck conditionCalls=1, detailCalls=0
falseCheck caught=invariant failed
afterFalse conditionCalls=2, detailCalls=1
nullInput=NullPointerException:orderId
badQuantity=quantity must be positive
explicit=explicit failure
done
~~~

**Giải thích:**

1. `-ea` bật assertion cho class, nên `desiredAssertionStatus()` trả `true`.
2. Assert đầu gọi `condition(true)` và tăng counter thành `1`. Điều kiện đúng, `detail()` không được gọi.
3. Assert thứ hai gọi `condition(false)`, tăng counter thành `2`. Vì false, Java gọi `detail()` một lần rồi ném `AssertionError("invariant failed")`.
4. Handler `catch (AssertionError error)` chỉ dùng để minh họa nên in `falseCheck caught=invariant failed`.
5. Các phần validation vẫn cho cùng kết quả như chế độ `-da`.
6. Lệnh `throw new AssertionError` cho cùng kết quả ở cả hai chế độ.

### Vì sao chương trình có side effect trong `condition()`?

`condition()` tăng counter để **chứng minh thực nghiệm** rằng assert có thể không đánh giá biểu thức. Đây là một kỹ thuật demo được dùng có chủ ý, **không phải thiết kế nên làm trong code nghiệp vụ**. Logic ứng dụng thực phải đặt mọi side effect cần thiết ngoài câu lệnh `assert`.

### Có cần phụ thuộc thư viện test hay database không?

Không. Chương trình dùng JDK 17, `java.util.Objects` và lớp `java.lang.AssertionError` có sẵn. Chỉ cần tạo đúng một tệp Java, chạy `javac` rồi dùng hai lệnh `java` ở trên.

## 15. Tự ném `AssertionError` và constructor của nó

Java có những constructor như:

~~~java
new AssertionError()
new AssertionError("message")
new AssertionError(42)
new AssertionError(false)
new AssertionError((Object) someValue)
~~~

Đây là các mảnh tạo object minh họa với biến `someValue` phù hợp. `AssertionError` cung cấp overload cho nhiều kiểu primitive và `Object` để phục vụ biểu thức detail của `assert`.

Đặc biệt khi dùng constructor nhận `Object`:

~~~java
Throwable root =
        new IllegalStateException("source invariant failed");

AssertionError error =
        new AssertionError((Object) root);

System.out.println(error.getCause() == root);
// true
~~~

Constructor `AssertionError(Object detailMessage)` có quy tắc: nếu đối tượng detail là một `Throwable`, nó có thể được đặt làm **cause** của `AssertionError`. Đây là hành vi API riêng, không nên nhầm với mọi constructor của mọi lớp exception.

Nếu muốn một lỗi ứng dụng với message/cause theo hợp đồng thông thường, dùng subclass `Exception`/`RuntimeException` phù hợp thay vì mặc định chuyển tất cả thành `AssertionError`.

### `AssertionError` không thay thế logic xử lý checked exception

Giả sử code đọc tệp có thể ném `IOException`. Việc viết:

~~~java
try {
    loadConfiguration();
} catch (java.io.IOException cause) {
    throw new AssertionError(cause);
}
~~~

đây là một mảnh minh họa. Nó biến checked `IOException` thành `Error`, thường **không phù hợp** nếu cấu hình có thể thiếu hoặc kết nối có thể thất bại trong vận hành bình thường. Tầng gọi sẽ không nhận hợp đồng checked ban đầu và `catch(Exception)` cũng không bắt lỗi mới.

Nếu cần chuyển abstraction, nên cân nhắc custom `Exception` hoặc `RuntimeException` có constructor giữ cause, với lý do thiết kế rõ ràng. Chỉ dùng `AssertionError` nếu lỗi thực sự mô tả một giả định nội bộ bị vi phạm, không phải bất kỳ lỗi hạ tầng nào.

## 16. Assertion trong constructor và trạng thái đối tượng

Một constructor có thể dùng `assert` để kiểm tra một invariant nội bộ, nhưng không được dùng làm điều kiện duy nhất để chấp nhận dữ liệu của caller.

Sai:

~~~java
final class UserId {
    private final String value;

    UserId(String value) {
        assert value != null : "value must not be null";
        this.value = value;
    }
}
~~~

Khi assertions tắt, constructor cho phép tạo `UserId` với `value == null`, phá hợp đồng bất biến của đối tượng.

Nên:

~~~java
final class UserId {
    private final String value;

    UserId(String value) {
        java.util.Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value is blank");
        }
        this.value = value;
        assert !this.value.isBlank();
    }
}
~~~

Ở đây:

- Hai validation ban đầu **luôn chạy**, bảo vệ hợp đồng constructor.
- Assertion cuối chỉ là một phép kiểm tra bổ sung đối với invariant đã được thiết lập.
- Nếu assertions tắt, object vẫn không thể được tạo thành công bằng constructor này với null hoặc blank input.

Không phải constructor nào cũng cần thêm assert cuối. Nếu điều kiện đã được chứng minh bằng code đơn giản, có thể không thêm kiểm tra trùng lặp.

## 17. Assertion ở nhánh xử lý exception

Một lỗi thường gặp là cho rằng có thể dùng `assert false` làm mặc định cho mọi exception được bắt:

~~~java
try {
    doSomething();
} catch (java.io.IOException e) {
    assert false : e;
}
~~~

Đây là mảnh mã minh họa có `doSomething()`, không phải chương trình độc lập. Với `-da`, handler **bắt IOException rồi không làm gì**; luồng tiếp tục, khiến lỗi I/O bị nuốt hoàn toàn.

Nếu tầng này không biết phục hồi, thường nên:

~~~java
try {
    doSomething();
} catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "cannot complete the operation", cause);
}
~~~

Hoặc để checked `IOException` lan truyền bằng `throws IOException` theo hợp đồng. Khi bắt ngoại lệ, phải **xử lý, chuyển đổi có cause, hoặc ném tiếp** theo trách nhiệm của tầng — không dựa vào `assert false`.

## 18. `AssertionError` trong static initializer

Vì chủ đề nằm trong nhóm **Error và khởi tạo**, cần hiểu ảnh hưởng của assertion khi JVM đang khởi tạo một class.

Ví dụ:

~~~java
final class StaticInvariant {
    static final int SIZE = calculateSize();

    static {
        assert SIZE > 0 : "static size must be positive";
    }

    static int calculateSize() {
        return -1;
    }

    static int size() {
        return SIZE;
    }
}
~~~

Đây là một class Java hợp lệ. Hành vi phụ thuộc việc assertions được bật cho `StaticInvariant` khi class khởi tạo:

- **Assertions tắt:** khối `static` không kiểm tra `SIZE > 0`; class có thể khởi tạo thành công với `SIZE == -1`.
- **Assertions bật:** phép kiểm tra `SIZE > 0` sai và câu lệnh ném `AssertionError` trong lúc class initialization.
- `AssertionError` là subtype của `Error`. Theo cơ chế khởi tạo lớp, một lỗi thoát ra đã thuộc `Error` có thể được truyền trực tiếp cho lần thất bại đầu tiên, **không bắt buộc bọc thành `ExceptionInInitializerError`**.
- Sau khi quá trình khởi tạo class thất bại, định nghĩa class có thể bị đánh dấu **erroneous**; các lần active use tiếp theo thường nhận `NoClassDefFoundError` trong cùng class loader thay vì tự chạy lại static initializer.

Đây là lý do không nên xem assertion như biện pháp duy nhất để bảo vệ một **trạng thái tĩnh cần thiết cho vận hành**.

### Nếu bắt buộc không chấp nhận giá trị sai dù `-da`?

Thay assertion bằng kiểm tra luôn chạy:

~~~java
final class StaticValidated {
    static final int SIZE = calculateSize();

    static {
        if (SIZE <= 0) {
            throw new IllegalStateException(
                    "static size must be positive");
        }
    }

    static int calculateSize() {
        return -1;
    }
}
~~~

Đoạn này là một class minh họa được biên dịch hợp lệ (static block vẫn có khả năng hoàn tất bình thường theo phân tích compiler). Khi initializer ném `IllegalStateException` — một `RuntimeException`, không phải `Error` — JVM thường bọc thất bại khởi tạo thành `ExceptionInInitializerError`.

**Cả hai hướng vẫn có thể làm class erroneous.** Nếu lỗi là điều kiện vận hành có thể phục hồi (ví dụ đọc cấu hình hoặc kết nối mạng), thường tốt hơn chuyển công việc sang **factory/loader** có hợp đồng checked/unchecked và retry rõ ràng, không làm thất bại class initialization.

## 19. Assertion trong static initializer và thời điểm bật trạng thái

JVM quản lý việc bật/tắt assertion theo class loader và trạng thái áp dụng khi class khởi tạo. Vì thế, việc dùng câu lệnh `assert` trong quá trình khởi tạo class có sắc thái riêng.

Các quy tắc đặc biệt về việc class được sử dụng **trong khi chính class còn đang khởi tạo** không nên được giản lược thành “mọi assert luôn tuân theo một boolean tự viết ở đầu chương trình”. Với code thông thường, hãy dùng `-ea`/`-da` rõ ràng khi khởi chạy và tránh xây logic khởi tạo phụ thuộc vào khả năng bật assertions.

Điều cần nhớ trong thực hành:

1. `assert` là phép kiểm tra có thể bỏ qua; không thay thế validation bắt buộc.
2. Một `AssertionError` thoát ra trong class initialization có thể làm class không sử dụng được ở các lần active use tiếp theo.
3. Chuyển thao tác dễ thất bại sang phương thức bình thường/instance factory giúp hệ thống có đường phục hồi rõ ràng hơn.

## 20. `catch (Exception)`, `catch (Error)` và `catch (Throwable)`

Đoạn sau minh họa **kiểu bắt**, không phải lời khuyên production:

~~~java
try {
    throw new AssertionError("internal bug");
} catch (Exception e) {
    System.out.println("Exception branch");
} catch (AssertionError e) {
    System.out.println("AssertionError branch");
}
~~~

Kết quả:

~~~text
AssertionError branch
~~~

`catch(Exception)` không bắt subtype của `Error`. Đây không phải là lý do để mọi service đổi sang `catch(Throwable)`.

### Vì sao không nên bắt rồi bỏ qua `AssertionError`?

Một assertion thất bại thường nói rằng **một giả định trong code sai**. Nếu code bắt nó rồi tiếp tục như không có vấn đề:

- Trạng thái object có thể không còn như thuật toán mong đợi.
- Các bước tiếp theo có thể sinh dữ liệu sai khó nhận diện.
- Debugging mất điểm báo lỗi đầu tiên.
- Nếu assertion nằm trong quá trình khởi tạo, class có thể đã ở trạng thái khởi tạo thất bại.

Trong test runner hoặc một số framework cấp thấp, handler có thể bắt `AssertionError` để **ghi nhận một kết quả test thất bại**, nhưng đó là trách nhiệm chuyên biệt.

Nếu buộc phải ghi log rồi chuyển lỗi tiếp, cần chính sách rất rõ; **đừng viết `catch(Throwable)` để biến mọi `Error` thành kết quả kinh doanh bình thường**.

## 21. `assert` của Java khác assertion của JUnit

Hai thứ thường có tên giống nhau nhưng cơ chế khác nhau:

**Java language assertion:**

~~~java
assert actual == expected : "values differ";
~~~

**JUnit 5 assertion:**

~~~java
org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
~~~

Đoạn JUnit là **mảnh minh họa cần phụ thuộc JUnit 5**, không nằm trong chương trình standalone Java 17 phía trên.

Các điểm khác biệt:

| Tiêu chí | Java `assert` | JUnit 5 `assertEquals` |
| --- | --- | --- |
| Là gì? | Câu lệnh ngôn ngữ | Method của framework kiểm thử |
| Có cần thư viện ngoài JDK? | Không | Có |
| Phụ thuộc `-ea` để chạy phép kiểm tra? | **Có** | **Không**, method được gọi như code thường |
| Khi không thỏa? | `AssertionError` nếu assert bật | Framework ném lỗi kiểm thử, thường subtype `AssertionError` |
| Vai trò chính | Kiểm tra invariant nội bộ khi bật | Khẳng định kết quả mong đợi của một test |
| Khi assertions tắt | Bỏ qua cả condition/detail | Method test vẫn được thực thi |

Để kiểm thử kết quả một chức năng, thường nên dùng công cụ assertion của framework, ví dụ `assertEquals`, `assertThrows`, thay vì để toàn bộ test phụ thuộc tình trạng `-ea`.

### `AssertionError` trong test runner có ý nghĩa gì?

Test framework có thể dùng `AssertionError` hoặc subclass của nó để báo một test thất bại; runner bắt, ghi nhận và tiếp tục chạy test khác. Đây là **cơ chế chuyên biệt có chủ đích**, không phải tiền lệ cho ứng dụng nghiệp vụ bắt mọi `AssertionError` rồi tiếp tục.

## 22. Những nhầm lẫn phổ biến

### Sai lầm 1 — Tưởng `assert` luôn chạy

Không. Trong các lần chạy JVM thông thường, assertion có thể bị tắt. Cần bật `-ea` khi muốn kiểm tra.

### Sai lầm 2 — Dùng `assert` để kiểm tra input từ người dùng

Input có thể không hợp lệ theo thiết kế; cần validation luôn hoạt động với `IllegalArgumentException`, `NullPointerException` hoặc kết quả có cấu trúc.

### Sai lầm 3 — Cho rằng `catch (Exception)` bắt được `AssertionError`

Không. `AssertionError extends Error`, không phải `Exception`.

### Sai lầm 4 — Cho rằng `AssertionError` là checked exception

Không. Mọi subtype của `Error` là unchecked; không phải khai báo `throws`.

### Sai lầm 5 — Tưởng `throw new AssertionError` cũng bị tắt theo `-da`

Không. Tùy chọn assertions chỉ ảnh hưởng **câu lệnh `assert`**, không tự bỏ qua các lệnh `throw` viết trực tiếp.

### Sai lầm 6 — Đưa cập nhật biến đếm vào condition của assert

Khi assert tắt, cập nhật bị bỏ qua. Side effect phải nằm ở câu lệnh bình thường.

### Sai lầm 7 — Đưa thao tác mạng/ghi dữ liệu vào detail expression

Detail chỉ chạy khi assert bật và condition false, không thể làm đường xử lý nghiệp vụ bắt buộc.

### Sai lầm 8 — Dùng `assert false` để xử lý IOException

Với `-da`, code trong catch có thể không làm gì và vô tình nuốt ngoại lệ.

### Sai lầm 9 — Tưởng `assert false` luôn ngăn đi tiếp

Không nếu assertion tắt. Nếu phải luôn dừng, ném một exception/Error phù hợp theo thiết kế.

### Sai lầm 10 — Coi AssertionError là lỗi nghiêm trọng nhất nên dùng cho mọi failure

Mức độ thiệt hại nghiệp vụ không quyết định kế thừa `Error`. Một lỗi kết nối nghiêm trọng vẫn có thể cần `IOException` hoặc custom `Exception`.

### Sai lầm 11 — Dùng assert thay cho authorization

Bảo mật phải được kiểm tra độc lập trạng thái `-ea`/`-da`.

### Sai lầm 12 — Cho rằng mọi lỗi trong static block luôn bị bọc thành `ExceptionInInitializerError`

Nếu lỗi đã thuộc `Error`, JVM có thể truyền trực tiếp đối tượng đó ở lần khởi tạo thất bại đầu. `AssertionError` thuộc nhóm này.

### Sai lầm 13 — Nghĩ class tự khởi tạo lại sau `AssertionError` ở static block

Không. Một class initialization thất bại có thể khiến định nghĩa class erroneous; lần active use sau thường gặp `NoClassDefFoundError`.

### Sai lầm 14 — Tưởng JUnit `assertEquals` phụ thuộc `-ea`

Không. Đó là lời gọi method của framework, được chạy theo logic kiểm thử bình thường.

### Sai lầm 15 — Cho rằng biểu thức detail luôn được đánh giá

Không. Nó chỉ được đánh giá nếu assertion bật và condition false.

### Sai lầm 16 — Dùng `assert obj != null` rồi dereference `obj`

Khi assert tắt, null vẫn còn và việc dereference có thể ném NPE sâu hơn trong logic, không tạo một hợp đồng rõ ràng.

### Sai lầm 17 — Bắt `AssertionError` rồi dùng fallback mặc định

Nếu một invariant nội bộ sai, fallback có thể che bug và gây sai dữ liệu. Cần xác định mức độ phục hồi thật sự trước khi tiếp tục.

### Sai lầm 18 — Dùng assert để kiểm tra quy tắc giao dịch bất biến

Kiểm tra số dư/giới hạn/bảo mật phải luôn chạy và thường cần cơ chế đồng bộ/transaction; `assert` không giải quyết tính nhất quán của transaction.

### Sai lầm 19 — Coi message của AssertionError là mã lỗi máy

Message chủ yếu dành cho con người, có thể thay đổi. Dùng exception type hoặc error code riêng khi cần phân nhánh tự động.

### Sai lầm 20 — Đặt assert thay cho phép kiểm tra tính hợp lệ của dữ liệu cấu hình

Cấu hình là dữ liệu đầu vào từ môi trường và có thể sai. Hãy báo exception có ngữ nghĩa và đường phục hồi/khởi động lại rõ ràng, không phụ thuộc cờ assertions.

## 23. Checklist sử dụng `assert` đúng mục đích

Trước khi thêm một câu lệnh `assert`, hãy trả lời:

1. **Đây có phải một invariant nội bộ** mà logic trước đó đã có trách nhiệm bảo đảm không?
2. **Nếu assert bị tắt, code còn đúng và an toàn không?** Nếu không, hãy dùng `if`/`throw`.
3. **Condition có side effect không?** Không được dùng kết quả của condition để thay đổi trạng thái bắt buộc.
4. **Detail expression có side effect không?** Không nên.
5. **Dữ liệu đến từ public API/HTTP/file/database không?** Nếu có, validation phải luôn chạy.
6. **Có một exception hợp đồng phù hợp hơn không?** NPE/IAE/ISE/IOException thường rõ hơn với lỗi có thể dự kiến.
7. **Có cần thu thập lỗi người dùng để trả nhiều thông báo không?** Dùng cơ chế validation kết quả, không rely on assert.
8. **Môi trường test có bật `-ea` không?** Kiểm tra run configuration nếu bạn muốn assertion được thực thi.
9. **Có nên dùng JUnit assertions để kiểm thử kết quả không?** Thường có.
10. **Nếu assertion sai, chương trình có nên dừng thao tác không?** Đừng bắt và bỏ qua nếu chưa có chính sách chuyên biệt.
11. **Assertion có nằm trong static initializer không?** Có thể làm class không khởi tạo được khi bật.
12. **Thông điệp có chứa dữ liệu nhạy cảm không?** Không đưa token/secret hoặc toàn bộ dữ liệu người dùng vào message.

Một cách tự kiểm rất tốt: **tưởng tượng xóa hết `assert` khỏi source**. Nếu ứng dụng vẫn đáp ứng hợp đồng, chính sách an toàn và thứ tự side effect, bạn đang dùng assertion gần đúng mục đích.

## 24. Câu hỏi tự kiểm tra

1. `AssertionError` thuộc nhánh `Exception` hay `Error`?
2. Nó là checked hay unchecked? Có bắt buộc khai báo `throws` không?
3. Hai cú pháp của câu lệnh `assert` là gì?
4. `condition` trong assert phải có loại giá trị nào?
5. Khi assertions tắt, condition có được đánh giá không?
6. Khi assertions tắt, detail expression có được đánh giá không?
7. Khi assertions bật và condition true, detail expression có chạy không?
8. Khi assertions bật và condition false, chương trình ném gì?
9. `-ea` dùng để làm gì?
10. `-da` dùng để làm gì?
11. Có cần biên dịch lại class để đổi trạng thái assertions không?
12. `java -ea AssertionLanguageDemo` và `java -da AssertionLanguageDemo` có thể cho stdout khác nhau không?
13. Vì sao không được dùng `assert quantity > 0` làm validation duy nhất của public method?
14. `Objects.requireNonNull` khác `assert obj != null` ở điểm nào quan trọng nhất?
15. Có nên đặt `counter++` vào điều kiện assert không?
16. Nếu detail function ghi dữ liệu, có rủi ro gì?
17. `catch(Exception)` có bắt `AssertionError` không?
18. `throw new AssertionError("x")` có bị vô hiệu hóa bởi `-da` không?
19. Khi nào `assert` phù hợp cho hậu điều kiện của thuật toán?
20. Nếu code có `assert false` trong nhánh default, khi assertions tắt điều gì xảy ra?
21. Một `AssertionError` trong static initializer có thể được JVM ném trực tiếp ở lần khởi tạo thất bại đầu không?
22. Lần sử dụng sau một class đã khởi tạo thất bại thường gặp loại lỗi gì?
23. Vì sao không được dùng `assert` để kiểm tra quyền admin?
24. `AssertionError(Object detailMessage)` có quan hệ cause đặc biệt nào khi detail là `Throwable`?
25. JUnit 5 `assertEquals` có cần `-ea` không?
26. Vì sao không nên bắt và bỏ qua mọi `AssertionError` trong service?
27. Kiểm thử code ứng dụng nên dựa vào `assert` ngôn ngữ hay assertions của test framework khi có sẵn?
28. Nếu một method trong điều kiện assert ném exception khác, có bảo đảm luôn thu được `AssertionError` không?
29. Những thông điệp tạo bằng detail nên chứa dữ liệu gì?
30. Quy tắc một câu để quyết định có dùng `assert` hay không là gì?

### Đáp án gợi ý

1. Nhánh `Error`.
2. Unchecked; không cần khai báo `throws`.
3. `assert condition;` và `assert condition : detail;`.
4. `boolean` hoặc `Boolean` (có thể unbox khi đánh giá).
5. Không.
6. Không.
7. Không.
8. `AssertionError`, trừ khi quá trình đánh giá condition/detail tự ném lỗi trước đó.
9. Bật assertions trong phạm vi tương ứng.
10. Tắt assertions trong phạm vi tương ứng.
11. Không; cờ nằm ở runtime.
12. Có; điều kiện và detail có thể được bỏ qua khi disabled.
13. Tiền điều kiện input phải luôn được kiểm tra.
14. `requireNonNull` luôn chạy khi được gọi; `assert` có thể bị bỏ qua.
15. Không; side effect sẽ phụ thuộc `-ea`.
16. Chỉ chạy trên đường assertion thất bại; gây thay đổi trạng thái bất ngờ.
17. Không.
18. Không; lệnh `throw` vẫn chạy bình thường.
19. Khi đó là invariant nội bộ đã được thuật toán bảo đảm, chỉ cần kiểm chứng khi bật.
20. Nhánh đó có thể hoàn tất mà không ném lỗi.
21. Có; `AssertionError extends Error`.
22. Thường là `NoClassDefFoundError`.
23. Assertion có thể tắt; authorization phải luôn chạy.
24. Nếu detail là Throwable, constructor dạng `Object` có thể dùng nó làm cause.
25. Không.
26. Có thể che bug và khiến ứng dụng tiếp tục với invariant bị vi phạm.
27. Thường dùng assertions của framework để diễn đạt kết quả test.
28. Không; ngoại lệ phát sinh trong expression có thể lan truyền.
29. Mô tả invariant, ngữ cảnh kỹ thuật hữu ích, không chứa secret.
30. Nếu bỏ qua assertion mà chương trình **không còn đúng hoặc an toàn**, đừng dùng `assert` làm biện pháp duy nhất.

## 25. Tổng kết

**`assert` là công cụ kiểm tra giả định nội bộ có thể bật/tắt, còn `AssertionError` là một `Error` unchecked được ném khi assertion bật và điều kiện sai.** Không dùng `assert` để thay thế tiền điều kiện public API, bảo mật, kiểm tra dữ liệu bên ngoài hay các side effect bắt buộc, vì toàn bộ biểu thức assert có thể không được đánh giá. Dùng `-ea` khi muốn kiểm chứng invariant trong phát triển/test; dùng exception phù hợp như `IllegalArgumentException`, `NullPointerException` hoặc `IllegalStateException` cho hợp đồng luôn có hiệu lực.

Nếu `AssertionError` thoát khỏi static initializer, class initialization có thể thất bại và làm class không sử dụng được cho các lần active use sau. Cuối cùng, phân biệt rõ Java language assertion với các assertion method của test framework: **JUnit kiểm thử theo lời gọi method, không phụ thuộc vào việc bật `-ea` cho câu lệnh `assert`**.

### Tham khảo chuẩn Java 17

- Java Language Specification, `assert` statement: https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.10
- `java.lang.AssertionError`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AssertionError.html
- `java.lang.Class.desiredAssertionStatus()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#desiredAssertionStatus()
- `java` launcher, các tùy chọn assertion: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-003">Không dùng assert thay kiểm tra đầu vào công khai</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng `assert` thay kiểm tra đầu vào công khai trong Java

Trong Java, `assert` là công cụ chẩn đoán những giả định mà chương trình **đáng lẽ luôn bảo đảm đúng bằng logic nội bộ**. Nó **không phải** cơ chế kiểm tra dữ liệu người dùng, tham số của phương thức `public`, constructor công khai hoặc dữ liệu từ file, mạng và cơ sở dữ liệu. Lý do quan trọng nhất: **assertion có thể bị vô hiệu hóa khi chạy**, khiến toàn bộ câu lệnh `assert` không có tác dụng và **không đánh giá cả biểu thức điều kiện lẫn thông báo**.

Ví dụ không đúng:

~~~java
public void setAge(int age) {
    assert age >= 0 : "age must be non-negative";
    this.age = age;
}
~~~

Đây là đoạn **minh họa trong một lớp có trường `age`**. Nếu chương trình chạy không bật assertions, giá trị âm vẫn có thể được gán vào trường. Nếu bật assertions, lỗi phát sinh là `AssertionError` thuộc nhánh `Error`, thay vì ngoại lệ tham số không hợp lệ thông thường mà người gọi API có thể dự kiến.

Cách phù hợp cho một public API:

~~~java
public void setAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("age must be non-negative");
    }
    this.age = age;
}
~~~

Kiểm tra bằng `if` **luôn thực thi** khi phương thức được gọi, bất kể trạng thái bật/tắt assertions. Đây là cơ sở của hợp đồng API: đầu vào sai phải được từ chối một cách nhất quán ở mọi môi trường triển khai.

Bài học này trình bày quy tắc của JLS §14.10, cách chạy `-ea`/`-da`, phân biệt `AssertionError` và `IllegalArgumentException`/`NullPointerException`, cách kiểm tra public constructor, record, collection index, phương thức sửa trạng thái; khi nào `assert` vẫn hữu ích cho **internal invariant**; vì sao không đặt side effect trong assertions; và các ví dụ Java 17+ có stdout rõ ràng.

## 1. `assert` trong Java thực sự làm gì?

Java có hai dạng:

~~~java
assert condition;
assert condition : detailExpression;
~~~

Đây là hai **mẫu cú pháp minh họa**, với `condition` là biểu thức kiểu `boolean` hoặc `Boolean` và `detailExpression` là biểu thức phù hợp làm detail message.

Theo JLS §14.10:

1. Nếu assertion **bị tắt**, toàn bộ câu lệnh hoàn tất bình thường; **không đánh giá** điều kiện hoặc biểu thức sau dấu hai chấm.
2. Nếu assertion **được bật**, Java đánh giá điều kiện.
3. Nếu điều kiện trả `true`, câu lệnh hoàn tất bình thường; biểu thức detail sau dấu hai chấm **không được đánh giá**.
4. Nếu điều kiện trả `false`, Java đánh giá detail expression nếu có, tạo một đối tượng `AssertionError` rồi ném nó.
5. Nếu chính việc đánh giá điều kiện hoặc detail expression ném lỗi, câu lệnh có thể kết thúc bằng lỗi đó trước khi tạo `AssertionError` theo đường thông thường.

Vì thế, `assert` là một **khẳng định có thể tắt bằng cấu hình chạy**, không phải một điều kiện `if` bắt buộc.

| Cấu trúc | Khi điều kiện đúng | Khi điều kiện sai | Có thể bị JVM bỏ qua theo assertion status? |
| --- | --- | --- | --- |
| `assert x > 0;` | Tiếp tục | `AssertionError` nếu được bật | **Có** |
| `if (x <= 0) throw new IllegalArgumentException(...);` | Tiếp tục | `IllegalArgumentException` | **Không** |
| `Objects.requireNonNull(value);` | Trả lại chính tham chiếu khác `null` | `NullPointerException` | **Không** |
| `Objects.checkIndex(index, length);` | Trả chỉ số hợp lệ | `IndexOutOfBoundsException` | **Không** |

Một `assert` được biên dịch hợp lệ không đồng nghĩa nó được thực thi trong bản chạy thực tế. Ngược lại, một điều kiện `if` hoặc lời gọi `Objects.requireNonNull` là mã thực thi bình thường, không phụ thuộc vào cờ bật assertion.

## 2. Bật và tắt assertions bằng `java -ea` và `java -da`

Trong JVM thông thường, assertions của các lớp ứng dụng mặc định **không được bật**, trừ khi có cấu hình từ launcher, class loader hoặc API liên quan. Các lệnh quen thuộc:

~~~bash
javac -encoding UTF-8 MyProgram.java
java -da MyProgram
java -ea MyProgram
~~~

Ý nghĩa:

- `-ea` hoặc `-enableassertions`: bật assertions theo phạm vi được chỉ định; dạng không có hậu tố dùng làm mặc định cho các lớp ứng dụng được áp dụng.
- `-da` hoặc `-disableassertions`: tắt assertions trong phạm vi tương ứng.
- JVM có thể cấu hình **theo lớp hoặc package**, không nhất thiết mọi lớp đều có cùng trạng thái. Ngoài cờ launcher, class loader có các API thiết lập trạng thái mong muốn cho những lớp sẽ được khởi tạo.
- Cấu hình khẳng định thường được quyết định khi lớp được khởi tạo. Không thiết kế logic nghiệp vụ dựa trên việc thay đổi bật/tắt assertions giữa hai lời gọi của cùng một lớp.

**Cờ JVM phải đặt trước tên class**: `java -ea MyProgram`, không phải `java MyProgram -ea`. Trong lệnh thứ hai, `-ea` chỉ là phần tử của `String[] args` dành cho chương trình, không phải tùy chọn JVM.

Khi kiểm thử, chạy cả hai chế độ giúp phát hiện lỗi kiểu “có bật assertions thì từ chối dữ liệu, còn tắt thì âm thầm chấp nhận”. Tuy nhiên, test nên nhấn mạnh **cùng một hợp đồng đầu vào cho cả hai chế độ**, không coi `-ea` là một yêu cầu bắt buộc ở production để tránh lỗi.

## 3. Ví dụ 1: mã sai cho phép dữ liệu không hợp lệ khi assertions tắt

Lưu thành `BadPublicAssertDemo.java`:

~~~java
public class BadPublicAssertDemo {
    static String acceptAmount(int amount) {
        assert amount > 0 : "amount must be positive";
        return "accepted:" + amount;
    }

    public static void main(String[] args) {
        try {
            System.out.println(acceptAmount(-4));
        } catch (AssertionError ex) {
            System.out.println("error=" + ex.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy hai lần:

~~~bash
javac -encoding UTF-8 BadPublicAssertDemo.java
java -da BadPublicAssertDemo
java -ea BadPublicAssertDemo
~~~

Đầu ra của **lần chạy `-da`**:

~~~text
accepted:-4
~~~

Đầu ra của **lần chạy `-ea`**:

~~~text
error=AssertionError
~~~

Cùng mã nguồn và cùng tham số `-4` nhưng hai kết quả khác nhau:

1. Khi tắt assertion, kiểm tra bị bỏ qua và chương trình tạo kết quả **accepted:-4**, vi phạm tiền điều kiện dự kiến.
2. Khi bật assertion, điều kiện sai tạo `AssertionError`. Chương trình cố ý bắt loại này để minh họa đầu ra, **không khuyến nghị bắt AssertionError để xử lý nhập liệu thông thường**.
3. Nếu `acceptAmount` là API công khai, người gọi không nên phải biết chương trình của họ đang bật `-ea` hay không để dự đoán API có từ chối giá trị âm.

Đây là lý do không dùng `assert` cho tham số của `public` method hoặc public constructor, đặc biệt khi đầu vào đến từ một người gọi, module, thư viện hoặc dịch vụ khác.

## 4. Phải kiểm tra public precondition bằng luồng thực thi bắt buộc

Một public method có **tiền điều kiện** (*precondition*): ví dụ `quantity > 0`, `name != null`, `index >= 0`. Tiền điều kiện là phần của hợp đồng mà người gọi được biết. Nếu vi phạm, method phải đáp ứng chính sách đã công bố, chẳng hạn ném `IllegalArgumentException`.

Cách viết:

~~~java
public static int calculateCharge(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException(
                "quantity must be positive");
    }
    return quantity * 10;
}
~~~

Đây là phương thức minh họa đặt trong một lớp Java; số học chỉ nhằm cho thấy điều kiện. Nếu sản phẩm `quantity * 10` có thể overflow trong ứng dụng thực tế, cần kiểm tra thêm, ví dụ `Math.multiplyExact`.

**Điều kiện không phụ thuộc `-ea`** và được thực thi trước khi method trả kết quả. Có thể dùng helper để tái sử dụng, nhưng helper phải là mã bình thường, không giấu trong assert expression.

### Ví dụ 2: public guard ổn định ở mọi chế độ

Lưu thành `PublicGuardDemo.java`:

~~~java
import java.util.Objects;

public class PublicGuardDemo {
    static String register(String name, int age) {
        Objects.requireNonNull(name, "name");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name blank");
        }
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("age out of range");
        }
        return "registered:" + name + ":" + age;
    }

    static void show(String name, int age) {
        try {
            System.out.println(register(name, age));
        } catch (RuntimeException ex) {
            System.out.println("error="
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        show("Ada", 36);
        show("", 36);
        show("Ada", -1);
        show(null, 20);
    }
}
~~~

~~~bash
javac -encoding UTF-8 PublicGuardDemo.java
java -da PublicGuardDemo
java -ea PublicGuardDemo
~~~

**Cả hai lần chạy đều in cùng một kết quả**:

~~~text
registered:Ada:36
error=IllegalArgumentException:name blank
error=IllegalArgumentException:age out of range
error=NullPointerException:name
~~~

Ở đây:

- `Objects.requireNonNull(name, "name")` dùng để báo **tham chiếu bắt buộc không được null**.
- `name.isBlank()` kiểm tra yêu cầu chuỗi không rỗng/không chỉ chứa whitespace; việc đó khác với kiểm tra null.
- `age` được kiểm tra theo miền nghiệp vụ.
- `RuntimeException` được bắt trong **demo để in tên loại lỗi**, không phải khuyến nghị catch rộng cho một service thật.
- Dù assertions được bật hay tắt, kết quả validation không thay đổi vì mã bảo vệ là luồng điều khiển thông thường.

Đây là dạng hành vi ổn định mà một public API cần cung cấp.

## 5. `AssertionError` không phải `IllegalArgumentException`

Cả hai đều là hậu duệ của `Throwable`, nhưng thuộc **nhánh khác nhau**:

~~~text
Throwable
 ├── Error
 │    └── AssertionError
 └── Exception
      └── RuntimeException
           ├── IllegalArgumentException
           ├── IllegalStateException
           ├── NullPointerException
           └── IndexOutOfBoundsException
~~~

Ý nghĩa sử dụng:

| Loại | Ngữ cảnh thích hợp |
| --- | --- |
| `AssertionError` | Giả định nội bộ bị phá vỡ, thường phản ánh bug và cần sửa code |
| `IllegalArgumentException` | Người gọi truyền một giá trị tham số không hợp lệ |
| `NullPointerException` | Tham chiếu không được phép null; `Objects.requireNonNull` hỗ trợ rõ ràng |
| `IllegalStateException` | Đối tượng ở trạng thái không cho phép thực hiện thao tác |
| `IndexOutOfBoundsException` | Chỉ số hoặc khoảng chỉ số không hợp lệ |
| Custom domain exception | Khi API cần lỗi có ý nghĩa riêng, mã lỗi/thuộc tính hoặc chính sách checked/unchecked rõ |

**AssertionError thuộc Error**, không phải Exception. Vì vậy `catch (Exception ex)` không bắt `AssertionError`. Có thể viết `catch (Throwable ex)` hoặc `catch (AssertionError ex)`, nhưng bắt rộng như vậy để che lỗi khẳng định trong vận hành thường sai mục tiêu: cần sửa bug nội bộ hơn là giả vờ đầu vào đã được xử lý.

Không nên ném `AssertionError` bằng tay khi phát hiện lỗi người dùng. Nếu người dùng nhập số lượng âm, đó là dữ liệu vi phạm hợp đồng, không nhất thiết là một lỗi trong implementation của chương trình.

## 6. Cảnh báo: `assert` có thể bị bỏ qua cả biểu thức có side effect

Theo JLS §14.10, một assertion bị vô hiệu hóa **không đánh giá** biểu thức điều kiện. Nếu expression gọi phương thức có side effect, trạng thái chương trình có thể khác nhau giữa hai chế độ chạy.

### Ví dụ 3: side effect bị mất khi assertions tắt

Lưu thành `AssertSideEffectDemo.java`:

~~~java
public class AssertSideEffectDemo {
    static int calls = 0;

    static boolean incrementAndReportTrue() {
        calls++;
        return true;
    }

    public static void main(String[] args) {
        assert incrementAndReportTrue();
        System.out.println("calls=" + calls);
    }
}
~~~

~~~bash
javac -encoding UTF-8 AssertSideEffectDemo.java
java -da AssertSideEffectDemo
java -ea AssertSideEffectDemo
~~~

Đầu ra với `-da`:

~~~text
calls=0
~~~

Đầu ra với `-ea`:

~~~text
calls=1
~~~

Không có `AssertionError` ở cả hai trường hợp vì phương thức trả `true`, nhưng trạng thái `calls` khác nhau. Việc đặt thao tác ghi log, gán biến, đọc file hoặc gọi API có tác dụng phụ vào điều kiện `assert` có thể làm môi trường test và production đi theo hai đường điều khiển khác nhau.

**Nguyên tắc:** điều kiện của `assert` và biểu thức sau dấu hai chấm nên **không gây side effect có ý nghĩa với chương trình**. Nếu một thao tác cần chạy, hãy thực hiện nó bằng câu lệnh bình thường trước, rồi dùng `assert` để kiểm tra giả định trên kết quả.

Ví dụ đúng hơn:

~~~java
int updated = updateState();
assert updated >= 0 : "internal invariant";
~~~

Đây là đoạn minh họa trong một method có `updateState()` phù hợp. `updateState()` luôn chạy; câu `assert` chỉ quan sát kết quả đã có. Tuy nhiên, nếu `updated >= 0` là điều kiện bắt buộc cho dữ liệu công khai thì vẫn phải dùng kiểm tra không tắt được, không chỉ assert.

## 7. Biểu thức thông báo sau dấu hai chấm cũng có điều kiện thực thi

Dạng `assert condition : detailExpression;` không đánh giá detailExpression trong mọi lần:

- Assertions **tắt**: cả hai biểu thức đều không được đánh giá.
- Assertions **bật và condition đúng**: chỉ đánh giá condition; không đánh giá detailExpression.
- Assertions **bật và condition sai**: đánh giá cả hai, rồi thông thường tạo `AssertionError` với detail message.

Điều này hữu ích khi message chẩn đoán tốn chi phí tạo: thông tin chi tiết chỉ được tính khi invariant sai. Nhưng detail expression vẫn nên an toàn, không thay đổi state hoặc làm lỗi thêm rối rắm.

### Ví dụ 4: đánh giá detail lazily

Lưu thành `LazyAssertDetailDemo.java`:

~~~java
public class LazyAssertDetailDemo {
    static int descriptions = 0;

    static String describe(int value) {
        descriptions++;
        return "internal value=" + value;
    }

    public static void main(String[] args) {
        int x = -2;

        try {
            assert x >= 0 : describe(x);
            System.out.println("accepted=" + x);
        } catch (AssertionError ex) {
            System.out.println("error=" + ex.getMessage());
        }

        System.out.println("descriptions=" + descriptions);
    }
}
~~~

~~~bash
javac -encoding UTF-8 LazyAssertDetailDemo.java
java -da LazyAssertDetailDemo
java -ea LazyAssertDetailDemo
~~~

Kết quả khi `-da`:

~~~text
accepted=-2
descriptions=0
~~~

Kết quả khi `-ea`:

~~~text
error=internal value=-2
descriptions=1
~~~

Ví dụ có tăng bộ đếm chỉ để **quan sát chính xác việc đánh giá**. Trong mã production, tránh thiết kế `describe` gây thay đổi state; đó nên là chức năng chỉ tạo mô tả an toàn cho điều kiện chẩn đoán.

Đừng dùng biểu thức sau dấu hai chấm để sửa dữ liệu, đặt giao dịch, giải phóng tài nguyên hoặc phát tín hiệu nghiệp vụ. Mọi thao tác bắt buộc phải chạy độc lập với trạng thái bật/tắt assertions.

## 8. Public constructor cũng là ranh giới đầu vào

Constructor có thể nhận đầu vào không tin cậy từ module khác. Nếu chỉ viết:

~~~java
public Account(String id) {
    assert id != null;
    this.id = id;
}
~~~

Đây là đoạn minh họa trong lớp `Account` có trường `id`. Khi assertions tắt, đối tượng vẫn có thể được tạo với `id == null`, để rồi nhiều phương thức khác gặp NullPointerException muộn tại vị trí khó chẩn đoán.

Cách an toàn là kiểm tra ngay lúc khởi tạo:

~~~java
public Account(String id) {
    this.id = java.util.Objects.requireNonNull(id, "id");
}
~~~

Nếu còn điều kiện như không rỗng, không có dấu cách, hoặc ID phải đúng dạng thì thêm kiểm tra bằng `if`. Bảo đảm constructor **không trả một đối tượng ở trạng thái không hợp lệ** theo hợp đồng.

### Ví dụ 5: validation trong constructor

Lưu thành `ConstructorGuardDemo.java`:

~~~java
import java.util.Objects;

public class ConstructorGuardDemo {
    static final class Account {
        private final String id;

        Account(String id) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id blank");
            }
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    public static void main(String[] args) {
        System.out.println("ok=" + new Account("U-7").id());

        try {
            new Account(null);
        } catch (NullPointerException ex) {
            System.out.println("null=" + ex.getClass().getSimpleName());
        }

        try {
            new Account("   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("blank=" + ex.getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ConstructorGuardDemo.java
java -da ConstructorGuardDemo
java -ea ConstructorGuardDemo
~~~

Kết quả **giống nhau ở cả hai chế độ**:

~~~text
ok=U-7
null=NullPointerException
blank=IllegalArgumentException
~~~

Constructor từ chối input không phù hợp ngay nơi tạo đối tượng, thay vì chấp nhận một object không đạt invariant rồi để lỗi phát sinh về sau. Đây là khác biệt giữa **giữ invariant bằng validation bắt buộc** và chỉ **khẳng định rằng invariant đã đúng**.

Khi kế thừa, constructor Java 17 yêu cầu lời gọi explicit `super(...)` hoặc `this(...)` đặt đầu constructor theo quy tắc ngôn ngữ. Nếu cần xác thực tham số trước khi truyền vào constructor lớp cha, có thể dùng biểu thức helper trong tham số của `super(...)`, ví dụ `super(Objects.requireNonNull(value, "value"))`, thay vì cố đặt lệnh `if` trước một `super(...)` explicit trong Java 17.

## 9. Record và immutable object cũng phải kiểm tra trước khi lưu

Java 17 hỗ trợ `record` để mang dữ liệu bất biến ở mức các component. Nhưng từ khóa record **không tự xác thực** `null`, số âm, chuỗi rỗng hoặc giá trị vượt miền. Cần kiểm tra trong constructor khi hợp đồng yêu cầu.

### Ví dụ 6: compact constructor của record

Lưu thành `RecordGuardDemo.java`:

~~~java
import java.util.Objects;

public class RecordGuardDemo {
    record OrderLine(String sku, int quantity) {
        OrderLine {
            Objects.requireNonNull(sku, "sku");
            if (sku.isBlank()) {
                throw new IllegalArgumentException("sku blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }
        }
    }

    public static void main(String[] args) {
        OrderLine valid = new OrderLine("SKU-1", 3);
        System.out.println("valid="
                + valid.sku() + ":" + valid.quantity());

        try {
            new OrderLine("SKU-2", 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("quantity="
                    + ex.getClass().getSimpleName());
        }

        try {
            new OrderLine(null, 2);
        } catch (NullPointerException ex) {
            System.out.println("sku="
                    + ex.getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 RecordGuardDemo.java
java -da RecordGuardDemo
java -ea RecordGuardDemo
~~~

Kết quả cả hai chế độ:

~~~text
valid=SKU-1:3
quantity=IllegalArgumentException
sku=NullPointerException
~~~

Compact constructor kiểm tra tham số trước khi record lưu component. Không phải vì dữ liệu bất biến mà validation có thể bỏ qua; một giá trị không hợp lệ nếu được lưu sẽ **khó sửa sau khi đối tượng được tạo**.

Nếu record mang collection có thể thay đổi, còn cần cân nhắc defensive copy (ví dụ `List.copyOf`) và chính sách phần tử null; `assert` cũng không thay được những bước bảo vệ dữ liệu này.

## 10. Chọn ngoại lệ cho từng loại điều kiện công khai

Không phải mọi lỗi đầu vào đều nên ném một ngoại lệ giống nhau. Chọn theo **hợp đồng** của API và hoàn cảnh sử dụng:

| Điều kiện | Loại thường phù hợp | Ví dụ |
| --- | --- | --- |
| Tham số số học không đúng miền | `IllegalArgumentException` | amount <= 0 |
| Tham số tham chiếu bắt buộc không null | `NullPointerException` qua `Objects.requireNonNull` | customerId == null |
| Chỉ số ngoài kích thước danh sách | `IndexOutOfBoundsException` | index < 0 hoặc index >= size |
| Thao tác ở trạng thái đối tượng không cho phép | `IllegalStateException` | gọi mutate sau khi close |
| Văn bản số không thể chuyển sang int | `NumberFormatException` | parseInt("foo") |
| Đầu vào dạng ngày không parse được | `DateTimeParseException` | LocalDate.parse("bad") |
| Dữ liệu có lỗi nghiệp vụ cần phản hồi theo field/code | Custom exception hoặc validation result | INVALID_QUANTITY |
| Giả định nội bộ bị phá vỡ dù input public đã được kiểm tra | `AssertionError` khi assertion được bật | postcondition đáng lẽ luôn đúng |

Bảng này là **quy ước thiết kế thường dùng**, không phải danh sách duy nhất có thể áp dụng. Một API có thể chọn custom exception hoặc một kiểu `Result<T>` có thông tin lỗi. Quan trọng nhất: hành vi khi đầu vào công khai không hợp lệ phải **được thực thi nhất quán**, có ý nghĩa cho bên gọi và được tài liệu hóa.

Không tự động dùng `AssertionError` chỉ vì một điều kiện “không nên xảy ra”. Nếu điều kiện do **caller có thể gây ra bằng tham số công khai**, hãy dùng validation. Nếu điều kiện biểu diễn **lỗi của implementation** mà logic nội bộ đã cam kết ngăn, assertion có thể là công cụ chẩn đoán bổ trợ.

## 11. Kiểm tra collection index: không dùng `assert index >= 0`

Với public method truy cập danh sách, chỉ kiểm tra `index >= 0` là chưa đủ: chỉ số còn phải nhỏ hơn kích thước. Nếu dùng `assert`, khi assertion tắt có thể truy cập chỉ số sai rồi nhận lỗi sâu hơn hoặc thậm chí thực thi logic không mong đợi trước khi lỗi xuất hiện.

JDK cung cấp các helper:

- `Objects.checkIndex(index, length)` xác thực chỉ số đơn.
- `Objects.checkFromToIndex(from, to, length)` xác thực phạm vi dạng [from, to).
- `Objects.checkFromIndexSize(from, size, length)` xác thực phạm vi theo điểm đầu và độ dài.

Các API này ném `IndexOutOfBoundsException` khi tham số sai; không phụ thuộc `-ea`.

### Ví dụ 7: helper kiểm tra chỉ số

Lưu thành `IndexGuardDemo.java`:

~~~java
import java.util.List;
import java.util.Objects;

public class IndexGuardDemo {
    static String read(List<String> items, int index) {
        Objects.requireNonNull(items, "items");
        int checked = Objects.checkIndex(index, items.size());
        return items.get(checked);
    }

    public static void main(String[] args) {
        List<String> data = List.of("A", "B");

        System.out.println("ok=" + read(data, 1));

        try {
            read(data, 2);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("index="
                    + ex.getClass().getSimpleName());
        }

        try {
            read(null, 0);
        } catch (NullPointerException ex) {
            System.out.println("list="
                    + ex.getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 IndexGuardDemo.java
java -da IndexGuardDemo
java -ea IndexGuardDemo
~~~

Kết quả cả hai chế độ:

~~~text
ok=B
index=IndexOutOfBoundsException
list=NullPointerException
~~~

Lợi ích của validation gần ranh giới: caller nhận lỗi của **điều kiện hợp đồng sai**, thay vì chờ một thao tác sâu hơn vô tình ném lỗi có ít ngữ cảnh hơn.

## 12. Invalid input khác invalid state

Một phương thức có thể có cả **tiền điều kiện tham số** và **tiền điều kiện trạng thái đối tượng**. Cả hai phải được kiểm tra bằng code thực thi bình thường nếu vi phạm có thể phát sinh trong những tình huống dự kiến của API.

Ví dụ một tài khoản:

- `withdraw(amount)` với `amount <= 0`: tham số sai → `IllegalArgumentException`.
- Tài khoản đã đóng: đối tượng không ở trạng thái cho phép rút tiền → `IllegalStateException`.
- Sau khi kiểm tra và cập nhật, `balance >= 0` có thể là một **invariant nội bộ** để dùng thêm `assert` trong debug. Nhưng logic cần **bảo đảm** invariant ngay cả khi assertions tắt.

### Ví dụ 8: validation và invariant bổ trợ

Lưu thành `StateContractDemo.java`:

~~~java
public class StateContractDemo {
    static final class Account {
        private int balance;
        private boolean closed;

        Account(int initialBalance) {
            if (initialBalance < 0) {
                throw new IllegalArgumentException(
                        "initial balance negative");
            }
            balance = initialBalance;
        }

        void withdraw(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "amount must be positive");
            }
            if (closed) {
                throw new IllegalStateException("account closed");
            }
            if (amount > balance) {
                throw new IllegalStateException("insufficient balance");
            }

            balance -= amount;
            assert balance >= 0 : "internal negative balance";
        }

        void close() {
            closed = true;
        }

        int balance() {
            return balance;
        }
    }

    public static void main(String[] args) {
        Account account = new Account(10);
        account.withdraw(4);
        System.out.println("balance=" + account.balance());

        try {
            account.withdraw(-2);
        } catch (IllegalArgumentException ex) {
            System.out.println("argument="
                    + ex.getClass().getSimpleName());
        }

        account.close();
        try {
            account.withdraw(1);
        } catch (IllegalStateException ex) {
            System.out.println("state="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("final=" + account.balance());
    }
}
~~~

~~~bash
javac -encoding UTF-8 StateContractDemo.java
java -da StateContractDemo
java -ea StateContractDemo
~~~

Kết quả giống nhau ở cả hai chế độ:

~~~text
balance=6
argument=IllegalArgumentException
state=IllegalStateException
final=6
~~~

Ở đây, `assert balance >= 0` chỉ là **dấu hiệu chẩn đoán bổ sung** sau khi thuật toán đã kiểm tra amount và số dư để tránh giá trị âm. Dù tắt assertion, object vẫn không thể bị rút âm thông qua các đường API hợp lệ của ví dụ. Điều đó đúng với vai trò kiểm tra invariant nội bộ: **không dùng assert làm lớp bảo vệ duy nhất**.

Lưu ý trong hệ thống đa luồng, cần xem xét đồng bộ/trạng thái giao dịch nếu nhiều thread cùng sửa balance. Một `assert` không tự tạo atomicity hay thread safety.

## 13. Vị trí thích hợp của `assert`: invariant do code nội bộ kiểm soát

Một assertion phù hợp thường diễn đạt điều mà **implementation đã bảo đảm** chứ không đòi hỏi caller phải bảo đảm. Ví dụ, sau một thuật toán sắp xếp hoặc một nhánh xử lý enum nội bộ, ta có thể khẳng định một thuộc tính không thể sai nếu code không có bug.

Mẫu trong một phương thức:

~~~java
Result result = algorithm(validatedInput);
assert result != null : "algorithm violated its postcondition";
return result;
~~~

Đây là **mã minh họa không độc lập**: giả định `Result`, `algorithm` và `validatedInput` được định nghĩa. Nếu result != null là điều kiện không thể thiếu cho hành vi đúng, logic sản xuất vẫn phải có cách xử lý hoặc thiết kế để không tạo trạng thái không hợp lệ khi assertion bị tắt.

Các điểm có thể phù hợp cho assertion trong nội bộ:

- Kiểm tra một quan hệ toán học mà thuật toán luôn phải duy trì.
- Kiểm tra một trạng thái nhánh private không thể xảy ra nếu switch/dispatch đã xử lý toàn bộ trường hợp.
- Kiểm tra tính nhất quán của cấu trúc dữ liệu sau thao tác mà public API đã xác thực.
- Gắn thông báo debug có ngữ cảnh khi một invariant được vi phạm.

**Nhưng “private” không tự động làm mọi precondition thành invariant.** Một private method nếu nhận chuỗi thô từ network hoặc dữ liệu chưa được validate cũng không nên dựa vào `assert` cho điều kiện bắt buộc. Tiêu chí là **ai bảo đảm điều kiện và liệu nó có thể bị vi phạm bởi đầu vào hợp lệ của hệ thống**.

## 14. Đừng lấy `assert` làm điều kiện trước khi có side effect nguy hiểm

Một pattern rủi ro:

~~~java
public void transfer(int amount) {
    assert amount > 0;
    debit(amount);
    credit(amount);
}
~~~

Đây là ví dụ minh họa không độc lập, giả định `debit` và `credit` tồn tại. Nếu asserts bị tắt, mã có thể tiến hành thao tác bất hợp lệ trước khi phát hiện vấn đề hoặc có thể gây lỗi ở một bước giữa chừng. Đối với tiền, tồn kho, quyền truy cập hoặc tài nguyên, việc **kiểm tra đầu vào trước side effects** là một phần thiết yếu của thiết kế.

Mẫu đúng hơn:

~~~java
public void transfer(int amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException(
                "amount must be positive");
    }
    debit(amount);
    credit(amount);
}
~~~

Hai đoạn chỉ để so sánh validation; một thao tác chuyển tiền thật cần thêm các yêu cầu như kiểm tra quyền truy cập, số dư, tính nguyên tử/transaction và xử lý lỗi hệ thống. `if` ở đây là điều kiện cần, không khẳng định code minh họa là implementation tài chính đầy đủ.

Nếu nhận dữ liệu từ request, hãy parse và validate ở **ranh giới tin cậy** trước khi thực hiện tác dụng phụ. Assertions dùng cho debug ở các bước nội bộ không thể thay các rào chắn này.

## 15. Lỗi parse nhập liệu không nên được “kiểm tra bằng assert”

Một số giá trị đến từ chuỗi: số lượng trong URL, ID, ngày từ CSV. Ví dụ:

~~~java
int amount = Integer.parseInt(rawAmount);
assert amount > 0;
~~~

Đây là đoạn minh họa. Có hai loại lỗi khác nhau:

1. Nếu rawAmount không thể biểu diễn một số int, `Integer.parseInt` ném **NumberFormatException**.
2. Nếu rawAmount parse được nhưng giá trị không dương, `assert` có thể **không chạy**, khiến dữ liệu vượt qua tiền điều kiện.

Cách tốt hơn là biểu diễn cả hai bước rõ ràng:

### Ví dụ 9: parse và validation với cause

Lưu thành `InputBoundaryDemo.java`:

~~~java
import java.util.Objects;

public class InputBoundaryDemo {
    static int parseQuantity(String raw) {
        Objects.requireNonNull(raw, "quantity");

        final int quantity;
        try {
            quantity = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "quantity must be an integer", ex);
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        return quantity;
    }

    static void show(String raw) {
        try {
            System.out.println("quantity=" + parseQuantity(raw));
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getMessage());
            System.out.println("causeIsNFE="
                    + (ex.getCause() instanceof NumberFormatException));
        }
    }

    public static void main(String[] args) {
        show("3");
        show("abc");
        show("-5");
    }
}
~~~

~~~bash
javac -encoding UTF-8 InputBoundaryDemo.java
java -da InputBoundaryDemo
java -ea InputBoundaryDemo
~~~

Kết quả cả hai chế độ:

~~~text
quantity=3
invalid=quantity must be an integer
causeIsNFE=true
invalid=quantity must be positive
causeIsNFE=false
~~~

`InputBoundaryDemo` **không dựa vào assert**; dữ liệu rỗng/sai có thể được phân loại rõ. Với lỗi parse, wrapper chứa nguyên đối tượng `NumberFormatException` làm cause; với số âm, lỗi nghiệp vụ độc lập không cần cause giả.

Lưu ý: phương thức `show` không thử `null` vì đoạn demo này chỉ bắt `IllegalArgumentException`, còn `parseQuantity(null)` dùng `Objects.requireNonNull` và ném `NullPointerException`. Nếu API muốn chuyển mọi dữ liệu không hợp lệ thành một error response chung, hãy tạo tầng mapping rõ ràng chứ không mặc định `IllegalArgumentException` luôn bao trùm NPE.

## 16. Cách viết kiểm thử cho hợp đồng public API

Một unit test có thể dùng thư viện kiểm thử để kiểm tra rằng public method luôn ném một loại lỗi đã định khi được gọi với dữ liệu sai. Đó khác với việc đặt `assert` trong implementation. Với Java không cần framework, có thể viết một helper đơn giản để chứng minh hợp đồng.

### Ví dụ 10: kiểm tra ngoại lệ ở cả `-da` và `-ea`

Lưu thành `GuardContractTestDemo.java`:

~~~java
public class GuardContractTestDemo {
    static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value not positive");
        }
        return value;
    }

    static boolean rejects(int value) {
        try {
            requirePositive(value);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("positive=" + requirePositive(5));
        System.out.println("rejectZero=" + rejects(0));
        System.out.println("rejectNegative=" + rejects(-1));

        if (!rejects(0) || !rejects(-1)) {
            throw new AssertionError("broken public contract");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 GuardContractTestDemo.java
java -da GuardContractTestDemo
java -ea GuardContractTestDemo
~~~

Kết quả giống nhau:

~~~text
positive=5
rejectZero=true
rejectNegative=true
~~~

**Trong test**, câu lệnh `throw new AssertionError(...)` được viết tường minh bằng luồng thực thi bình thường, khác với từ khóa `assert` có thể bị bỏ qua. Nhiều framework unit test cũng dùng AssertionError để báo test fail; đó là vai trò của **kiểm thử**, không phải lý do dùng `assert` cho public preconditions trong code ứng dụng.

Khi dùng JUnit, có thể áp dụng `assertThrows(IllegalArgumentException.class, ...)` để chỉ rõ loại ngoại lệ mong đợi. Không nên chỉ viết `assert condition;` trong test rồi quên bật `-ea` nếu chính test dựa vào từ khóa assert của Java; các assertion methods của framework là API khác với từ khóa `assert` của ngôn ngữ.

## 17. `Objects.requireNonNull`, `checkIndex` và helper riêng: chọn cho đúng

Các helper trong `java.util.Objects` thường tiện cho tham số công khai:

~~~java
String validName =
        java.util.Objects.requireNonNull(name, "name");
int validIndex =
        java.util.Objects.checkIndex(index, count);
~~~

Đây là đoạn mã minh họa trong phương thức nơi `name`, `index` và `count` đã được định nghĩa. Hai thao tác luôn chạy, dù assertions bị tắt.

Phân biệt:

- `requireNonNull` chỉ kiểm tra null; nó **không tự kiểm tra** chuỗi rỗng, whitespace, pattern hoặc business rule.
- `checkIndex` kiểm tra miền chỉ số [0, length), không thay cho kiểm tra quyền truy cập hay ý nghĩa phần tử.
- Helper riêng như `requirePositive` nên ném lỗi có nghĩa và giữ việc xác thực **không thể bị tắt**.
- Với một giá trị từ người dùng, hệ thống có thể muốn trả **ValidationResult** thay vì exception vì đầu vào sai là tình huống thường xuyên; đó là lựa chọn thiết kế API, nhưng không dùng `assert` để thay thế.

Ví dụ helper có thể tái sử dụng:

~~~java
static int requirePositive(int value, String parameter) {
    if (value <= 0) {
        throw new IllegalArgumentException(
                parameter + " must be positive");
    }
    return value;
}
~~~

Đây là phương thức hợp lệ khi đặt trong lớp. Có thể sử dụng trong constructor hoặc method để giảm trùng code. Tránh đưa dữ liệu nhạy cảm vào thông báo exception; tên tham số và quy tắc thường đủ.

## 18. Những trường hợp không nên chỉ dùng `assert`

| Ngữ cảnh | Vì sao assert không phù hợp? | Hướng thay thế |
| --- | --- | --- |
| Public method nhận số lượng âm | Khi `-da`, dữ liệu sai được tiếp nhận | `if` + IllegalArgumentException |
| Constructor nhận null | Object có thể được tạo không hợp lệ | Objects.requireNonNull |
| Record có giá trị component sai | Bất biến không được kiểm soát | Compact constructor validation |
| REST API nhận ngày sai | Dữ liệu người dùng có thể sai bình thường | Parse + map lỗi validation |
| File CSV chứa dòng hỏng | Lỗi nhập liệu dự kiến | Trả lỗi theo dòng, exception/result phù hợp |
| Collection truy cập index | Sai index là sai hợp đồng | Objects.checkIndex hoặc API checked |
| Giao dịch có input vượt giới hạn | Không được phép thay đổi state khi input sai | Validate trước side effect |
| Trạng thái đối tượng closed | Thao tác không được phép theo API | IllegalStateException |
| Quy tắc quyền truy cập | Bảo vệ bắt buộc trong mọi chế độ | Kiểm tra authorization không thể tắt |
| Kiểm tra null để tránh dereference sâu | Khi tắt assert sẽ NPE muộn | Objects.requireNonNull |
| Assert gọi hàm thay đổi state | Bật/tắt đổi hành vi chương trình | Tách thao tác thành câu lệnh bình thường |
| Quy trình phục hồi lỗi dự kiến | AssertionError thuộc Error, không phải contract thông thường | Exception/domain error hoặc result type |

**Phân biệt mức độ “không nên”:** JLS không cấm cú pháp `assert x > 0` ngay đầu public method, nhưng cách đó **không bảo đảm hợp đồng** nếu assertions tắt. Vì vậy đây là **anti-pattern thiết kế**, không phải lỗi syntax Java.

## 19. Khi nào vẫn nên dùng `assert` trong code production?

Assertions có thể xuất hiện trong code production để hỗ trợ chẩn đoán một invariant nội bộ khi được bật có chủ đích. Ví dụ trong một thuật toán mà đầu vào đã được public API kiểm tra và mọi đường điều khiển đều bảo đảm một tính chất:

~~~java
int result = carefullyComputedValue();
assert result >= 0 : "internal algorithm invariant";
return result;
~~~

Đây là đoạn minh họa, giả định `carefullyComputedValue()` tồn tại và logic chương trình đã bảo đảm kết quả không âm. Khi assertions tắt, thuật toán vẫn phải có nghĩa và giữ được hợp đồng quan trọng.

Những nơi cần cân nhắc kỹ:

- **Security check:** tuyệt đối không thay bằng assert, vì bật/tắt làm khác mức bảo vệ.
- **Input validation:** không dùng assert vì caller được phép truyền dữ liệu sai và cần phản hồi ổn định.
- **Resource management:** không đặt close/commit/rollback trong assert expression.
- **Concurrency:** assert không cung cấp khóa, visibility, atomicity hay synchronization.
- **Debug-only invariant:** có thể dùng assert để phát hiện vi phạm giả định mà implementation vốn phải bảo đảm.
- **Expensive invariant check:** có thể dùng assert nếu điều kiện không gây side effect, việc chạy thêm chỉ nhằm debug, và không có hậu quả bắt buộc khi bị tắt.

Một lỗi invariant phát hiện bởi `AssertionError` thường cần điều tra và sửa logic, không phải trả thông điệp “tham số không hợp lệ” cho người dùng cuối.

## 20. Những nhầm lẫn thường gặp về bật/tắt assertions

**Nhầm lẫn 1 — “Java tự bật assert khi debug trong IDE.”**  
Không có bảo đảm như vậy. IDE/test runner có thể tự thêm `-ea` hoặc không. Hãy kiểm tra cấu hình JVM thật. Một chương trình được biên dịch bằng `javac` không tự làm assertions bật.

**Nhầm lẫn 2 — “Chạy `java MyProgram -ea` sẽ bật assert.”**  
Không. Tùy chọn JVM nằm trước tên main class: dùng `java -ea MyProgram`.

**Nhầm lẫn 3 — “Assert chỉ tắt phần `throw`, còn điều kiện vẫn chạy.”**  
Sai. Khi assert tắt, các biểu thức trong assert **không được đánh giá**.

**Nhầm lẫn 4 — “Chạy `-ea` ở dev thì production cũng được bảo vệ.”**  
Sai. Một môi trường khác có thể tắt assertion, đặc biệt khi thay đổi launcher/configuration.

**Nhầm lẫn 5 — “Một `catch(Exception)` xử lý được AssertionError.”**  
Sai. AssertionError thuộc `Error`, không nằm trong cây Exception.

**Nhầm lẫn 6 — “Mọi thứ `assert` kiểm tra đều là dữ liệu công khai.”**  
Không. Assert phù hợp nhất khi kiểm tra một invariant **được implementation kiểm soát**, tức sai lệch cho thấy bug thay vì dữ liệu người dùng nhập sai.

**Nhầm lẫn 7 — “Objects.requireNonNull kiểm tra mọi dạng String sai.”**  
Không. Chuỗi `""` vẫn khác null; cần kiểm tra thêm isBlank, định dạng và miền.

**Nhầm lẫn 8 — “JUnit assertEquals giống Java keyword assert.”**  
Không. Framework assertion là phương thức thư viện, không được JVM bỏ qua bằng cờ `-da` như câu lệnh assert của ngôn ngữ.

**Nhầm lẫn 9 — “Chỉ cần `assert balance >= 0` sau phép trừ là đủ.”**  
Không. Khi assert tắt, điều kiện không được kiểm tra. Phải có logic bình thường ngăn rút quá số dư trước khi cập nhật.

**Nhầm lẫn 10 — “Assert luôn gây AssertionError khi expression lỗi.”**  
Không hẳn. Khi điều kiện hoặc biểu thức detail tự ném một ngoại lệ/lỗi trong lúc đánh giá, câu lệnh có thể kết thúc bằng lỗi đó; JLS mô tả rõ hành vi này.

## 21. Quy trình review một public method hoặc constructor

Hãy rà soát theo thứ tự:

1. **Đầu vào từ đâu?** Caller ngoài module, HTTP, file, CLI, cấu hình hay một bước nội bộ đã kiểm tra?
2. **Tham số nào bắt buộc?** Null, rỗng, range, index, format, enum, capacity có điều kiện rõ ràng không?
3. **Có dùng `assert` làm hàng rào duy nhất không?** Nếu có, cần thay bằng `if`, Objects helper hoặc validation result.
4. **Mã có side effect trước khi kiểm tra không?** Hãy validate trước khi ghi DB, gửi message, thay đổi state hoặc phân bổ tài nguyên đắt đỏ.
5. **Ngoại lệ có đúng ý nghĩa không?** NullPointerException, IllegalArgumentException, IndexOutOfBoundsException, IllegalStateException hay custom domain failure?
6. **Thông điệp có an toàn không?** Không lộ token, password, dữ liệu nhạy cảm hoặc toàn bộ input lớn.
7. **Constructor có thể tạo object không hợp lệ không?** Kiểm tra trước khi lưu component/field.
8. **Invariant được bảo đảm ở đâu?** Validation và thuật toán phải bảo đảm; assert chỉ bổ sung chẩn đoán.
9. **Có biểu thức side effect trong assert không?** Nếu có, tách khỏi assert thành statement luôn chạy.
10. **Kiểm thử hai chế độ?** Chạy với `java -da` và `java -ea` để bảo đảm public contract không thay đổi.
11. **Kiểm thử đường lỗi?** Kiểm tra đúng loại exception, message hoặc mã lỗi, trạng thái sau khi từ chối.
12. **Ghi tài liệu API:** mô tả miền tham số và kiểu lỗi thay vì dựa vào việc người gọi đọc code assert.

Checklist này đặc biệt hữu ích khi di chuyển code từ script/debug utility sang thư viện công khai: những giả định chỉ đúng với dữ liệu test cần chuyển thành kiểm tra hợp đồng trước khi chịu đầu vào thực tế.

## 22. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao không dùng `assert amount > 0` làm kiểm tra duy nhất trong public method?  
Vì assertion có thể bị tắt và khi đó `amount > 0` không được đánh giá; input sai vẫn có thể đi tiếp.

**Câu 2.** Chạy `java -ea Program` khác `java -da Program` thế nào?  
Cờ đầu cho phép assertions thuộc phạm vi áp dụng, cờ sau vô hiệu hóa chúng; các validation viết bằng `if`/Objects vẫn chạy như nhau.

**Câu 3.** Khi `assert false : "bad";` được bật, lỗi thường là gì?  
**AssertionError**, thuộc nhánh Error.

**Câu 4.** `catch(Exception ex)` có bắt được AssertionError không?  
**Không**, vì AssertionError không kế thừa Exception.

**Câu 5.** Nếu `assert checkAndModify();` có side effect, khi `-da` side effect xảy ra không?  
**Không**, biểu thức điều kiện không được gọi.

**Câu 6.** Một constructor nhận null ở tham số bắt buộc nên xử lý bằng gì?  
Có thể dùng **Objects.requireNonNull**, ném NullPointerException theo hợp đồng rõ ràng.

**Câu 7.** Số lượng âm do caller truyền có phải invariant nội bộ bị phá vỡ không?  
Thông thường **không**; đó là tham số sai hợp đồng công khai, nên phản hồi qua validation.

**Câu 8.** Khi đối tượng đã đóng mà caller gọi mutate, loại ngoại lệ thường phù hợp là gì?  
**IllegalStateException** theo thiết kế API thường dùng.

**Câu 9.** Có nên dùng `assert index >= 0` để đảm bảo truy cập danh sách hợp lệ không?  
**Không**. Cần kiểm tra cả giới hạn trên bằng code bắt buộc, ví dụ Objects.checkIndex.

**Câu 10.** Có thể dùng assert để kiểm tra invariant nội bộ sau khi validation đầy đủ không?  
**Có thể**, miễn khi assertions tắt ứng dụng vẫn giữ hợp đồng và điều kiện không có side effect.

**Câu 11.** Một compact constructor của record có tự từ chối số lượng 0 không?  
**Không tự động**. Phải kiểm tra bằng `if` nếu đó là precondition.

**Câu 12.** Tại sao nên chạy test với cả `-ea` và `-da`?  
Để phát hiện code sai phụ thuộc vào việc assertions được bật; public API phải giữ hành vi validation nhất quán.

**Câu 13.** `assert condition : detailExpression` có luôn đánh giá detailExpression không?  
**Không**. Chỉ đánh giá khi assertion bật và điều kiện sai (trừ khi điều kiện kết thúc đột ngột).

**Câu 14.** Từ khóa `assert` và phương thức `assertThrows` trong test framework có phải giống nhau không?  
**Không**. `assertThrows` là phương thức kiểm thử; từ khóa assert có thể bị JVM vô hiệu hóa.

## Tài liệu tham khảo chuẩn

- [JLS Java SE 21 — §14.10: The assert Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.10) — quy tắc bật/tắt, đánh giá biểu thức, AssertionError và khuyến cáo không dùng assertions cho public argument checking.
- [Oracle — Programming With Assertions](https://docs.oracle.com/javase/8/docs/technotes/guides/language/assert.html) — lựa chọn đúng/sai khi sử dụng assert và side effects.
- [Java SE 21 — java.lang.AssertionError](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AssertionError.html).
- [Java SE 21 — java.util.Objects: requireNonNull, checkIndex](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html).
- [Java SE 21 — IllegalArgumentException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/IllegalArgumentException.html).
- [Java SE 21 — IllegalStateException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/IllegalStateException.html).
- [Java SE 21 — IndexOutOfBoundsException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/IndexOutOfBoundsException.html).
- [Java launcher — enableassertions / disableassertions](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).

**Ghi nhớ:** `assert` dành cho giả định nội bộ mà chương trình **đã bảo đảm đúng**, giúp phát hiện bug khi bật chẩn đoán. **Public method, constructor, record, API nhận dữ liệu bên ngoài và các thao tác có side effect phải xác thực tiền điều kiện bằng code luôn chạy**, bất kể `-ea`/`-da`. Hãy chọn ngoại lệ hợp đồng có ý nghĩa, giữ cause khi chuyển đổi lỗi, viết test cho dữ liệu sai và không để tính đúng đắn hay bảo mật phụ thuộc vào trạng thái assertions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-004">OutOfMemoryError và giới hạn phục hồi trong cùng tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# OutOfMemoryError và giới hạn phục hồi trong cùng tiến trình Java

## 1. Vì sao chủ đề này quan trọng?

`OutOfMemoryError`, thường gọi tắt **OOME**, báo rằng máy ảo Java không thể đáp ứng một nhu cầu cấp phát bộ nhớ hoặc tài nguyên tương ứng trong những điều kiện nhất định. Nó không chỉ xuất hiện khi `new byte[...]` làm đầy Java heap: ứng dụng còn có thể gặp thiếu native memory, metaspace, direct buffers, giới hạn tạo thread hoặc giới hạn kích thước mảng.

Sai lầm phổ biến nhất là xem OOME như một lỗi nghiệp vụ thông thường: viết `catch (OutOfMemoryError e)`, gọi `System.gc()`, xóa vài phần tử cache rồi cho toàn bộ dịch vụ tiếp tục như trước. **Bắt được OOME không chứng minh JVM đã đủ tài nguyên, dữ liệu còn nhất quán hoặc request tiếp theo an toàn.** Một số dạng lỗi không cho JVM cơ hội catch, chẳng hạn tiến trình bị hệ điều hành/container OOM killer chấm dứt.

Mục tiêu bài học:

- Xác định đúng cây kế thừa và hợp đồng **unchecked `Error`**.
- Phân biệt thiếu heap với metaspace, native thread, direct buffer và các giới hạn khác.
- Hiểu JVM có thể còn chạy sau OOME nhưng **không bảo đảm** khôi phục ứng dụng.
- Nhận biết giới hạn của `catch`, `finally`, `try-with-resources`, GC thủ công và bộ nhớ dự phòng.
- Giải thích OOME trong **static initialization** và tác động tới các lần sử dụng class sau.
- Thiết kế giới hạn memory input/cache/concurrency, streaming và backpressure để phòng tránh lỗi.
- Chẩn đoán qua heap dump, GC logs, JFR, process metrics, Native Memory Tracking và dữ liệu OOM kill.
- Xây dựng chính sách fail-fast, phát hiện sự cố và restart ngoài tiến trình, có kiểm tra trạng thái nghiệp vụ.
- Viết ví dụ Java 17+ an toàn **không cố làm cạn bộ nhớ thật**.

Tất cả ví dụ chạy được đều có tên file, lệnh `javac`/`java` và `stdout`. Những ví dụ ném `new OutOfMemoryError("simulated")` là **lỗi giả lập có chủ đích**, chỉ dùng để học ngữ nghĩa exception và class initialization, **không phải thử nghiệm khả năng phục hồi trong môi trường thực sự hết RAM**.

## 2. `OutOfMemoryError` nằm ở đâu trong cây `Throwable`?

```text
java.lang.Throwable
├── java.lang.Exception
│   ├── java.lang.RuntimeException [unchecked]
│   └── các checked exceptions
└── java.lang.Error                  [unchecked]
    └── java.lang.VirtualMachineError
        ├── java.lang.OutOfMemoryError
        ├── java.lang.StackOverflowError
        └── java.lang.InternalError
```

`OutOfMemoryError` là subclass của `VirtualMachineError` → `Error` → `Throwable`, **không** là subclass của `Exception`. Vì thế:

- `catch (Exception e)` và `catch (RuntimeException e)` không bắt OOME.
- `catch (OutOfMemoryError e)`, `catch (Error e)`, `catch (VirtualMachineError e)` và `catch (Throwable e)` có thể nhận OOME về mặt kiểu.
- Compiler **không buộc** method khai báo `throws OutOfMemoryError`.
- “Unchecked” chỉ là quy tắc compile-time, **không** đồng nghĩa ít nghiêm trọng hoặc có thể an toàn bỏ qua.

### Chương trình chạy được: kiểm tra quan hệ kiểu

Tạo file **`OomeHierarchyDemo.java`**:

```java
public class OomeHierarchyDemo {
    public static void main(String[] args) {
        Throwable sample = new OutOfMemoryError("simulated");

        System.out.println("type="
                + sample.getClass().getSimpleName());
        System.out.println("Throwable="
                + (sample instanceof Throwable));
        System.out.println("Error="
                + (sample instanceof Error));
        System.out.println("VirtualMachineError="
                + (sample instanceof VirtualMachineError));
        System.out.println("Exception="
                + (sample instanceof Exception));
    }
}
```

```bash
javac OomeHierarchyDemo.java
java OomeHierarchyDemo
```

`stdout`:

```text
type=OutOfMemoryError
Throwable=true
Error=true
VirtualMachineError=true
Exception=false
```

Chương trình chỉ tạo một đối tượng Throwable nhỏ, **không** cố cạn kiệt memory. Phép kiểm tra `instanceof` dùng biến có kiểu tĩnh `Throwable` để minh họa chính xác quan hệ kế thừa.

## 3. Những nguyên nhân OOME thường gặp

Thông điệp `OutOfMemoryError` gợi ý khu vực cần điều tra, nhưng không phải một bộ mã lỗi tiêu chuẩn bất biến giữa mọi JVM/JDK. Không thiết kế nghiệp vụ bằng cách so khớp duy nhất chuỗi `getMessage()`.

| Biểu hiện/thông điệp thường gặp | Hướng điều tra | Sai lầm cần tránh |
| --- | --- | --- |
| `Java heap space` | Giới hạn `-Xmx`, object còn sống, dữ liệu quá lớn, allocation spikes | Kết luận tất cả đều là leak |
| `GC overhead limit exceeded` | Thu gom rác rất tốn công mà giải phóng ít theo ngưỡng JVM | Gọi `System.gc()` vô hạn |
| `Metaspace` | Metadata class, class loading, class loader giữ lại, `MaxMetaspaceSize` | Chỉ tăng Java heap |
| `Requested array size exceeds VM limit` | Kích thước mảng bị giới hạn hoặc không biểu diễn được | Tin rằng cứ tăng RAM là tạo được |
| `unable to create native thread` | Native thread, stack, OS/process/container limits | Khẳng định heap chắc chắn đầy |
| Lỗi cấp direct-buffer memory | `ByteBuffer.allocateDirect`, budget native/direct buffers | Đọc `Runtime.freeMemory()` rồi kết luận đủ RAM |

### 3.1. Java heap

Các object Java được quản lý bởi GC thường nằm trên heap. Thiếu heap có thể do:

- **Leak logic**: cache không eviction, static collection, listeners, `ThreadLocal`, session hoặc queue vẫn giữ tham chiếu.
- **Live-set hợp lệ nhưng quá lớn**: yêu cầu nghiệp vụ giữ nhiều dữ liệu hơn cấu hình heap cho phép.
- **Allocation spike**: parse file lớn, decode ảnh, tạo nhiều bản sao chuỗi/array, batch processing và concurrent requests.
- **Một allocation riêng quá lớn**: buffer, mảng hoặc đối tượng trung gian không đáp ứng giới hạn.

GC không thể dọn object còn reachable chỉ vì ứng dụng cảm thấy chúng “không cần nữa”. Ngược lại, nhiều object còn sống là nghiệp vụ thực sự có thể không phải leak, mà cần thay đổi cấu trúc xử lý hoặc ngân sách memory.

### 3.2. Metaspace

Metaspace liên quan metadata class và thường sử dụng native memory. Framework tạo proxy/class động, nạp plugin qua nhiều class loader, hoặc giữ class loader cũ có thể gây áp lực. Điều tra class count, class loader lifetime, `-XX:MaxMetaspaceSize` và bộ nhớ native thay vì chỉ đọc `-Xmx`.

### 3.3. Native memory, direct buffers và threads

Direct buffers, thread stacks, JNI libraries, native allocator và một số thành phần JVM sử dụng tài nguyên không được đo đầy đủ chỉ bằng số liệu free heap. Trong container, **tổng RSS/process memory** cùng với cgroup limit quan trọng không kém `-Xmx`.

Tăng Java heap lên sát giới hạn RAM/container có thể làm **thiếu chỗ cho native memory**, khiến lỗi khác trầm trọng hơn. Thread pool không giới hạn hoặc quá nhiều platform threads có thể thất bại lúc tạo native thread dù heap vẫn còn.

### 3.4. OS OOM kill khác với Java OOME

Nếu hệ điều hành/container quyết định chấm dứt process vì vượt memory budget, Java **không nhất thiết nhận được một đối tượng `OutOfMemoryError` để catch**. Không có cơ hội bảo đảm chạy `finally`, shutdown hook hay ghi heap dump trước khi bị kill. Cần lấy dấu hiệu từ **supervisor, cgroup/container events và host logs**.

## 4. JVM có thể còn chạy nhưng trạng thái ứng dụng chưa chắc an toàn

Java không định nghĩa rằng mỗi `OutOfMemoryError` luôn lập tức tắt JVM. Một thread có thể ném OOME, unwind stack, chạy handler tương ứng và thread khác tiếp tục hoạt động. Đôi khi sau một request thất bại, một số object không còn reachable, GC có thể thu hồi memory.

Nhưng điều đó không cho phép kết luận:

- Bộ nhớ đã đủ cho request tiếp theo.
- Cache hoặc hàng đợi đã giảm tới ngưỡng an toàn.
- Transaction được rollback hoàn toàn.
- External API đã thực hiện hay chưa.
- Các bất biến của object đang được nhiều thread dùng còn nhất quán.
- Một class từng khởi tạo thất bại có thể init lại bình thường.
- Thao tác log, gửi metric hoặc tạo error response có thể chắc chắn thành công.

Ví dụ một request có thể cập nhật state trong heap, gửi yêu cầu ngoài hệ thống rồi OOME khi cần cấp phát buffer xử lý phản hồi. **Catch OOME không đảo ngược side effect**, cũng không cho biết request có an toàn để retry hay không.

Ba lớp kiểm tra cần phân biệt:

1. **Liveness của JVM**: process còn chạy?
2. **Readiness**: instance có đủ tài nguyên và trạng thái tốt để nhận workload mới?
3. **Consistency**: dữ liệu bền vững, giao dịch và trạng thái nghiệp vụ có khớp không?

Một lệnh `return true` từ health endpoint sau một `catch` không phải bằng chứng đáng tin cho cả ba lớp.

## 5. `catch (OutOfMemoryError)` đúng cú pháp, nhưng không phải recovery mặc định

Có thể viết:

```java
try {
    operation();
} catch (OutOfMemoryError failure) {
    // Chỉ thực hiện tín hiệu cực tối giản nếu thật sự cần.
    throw failure;
}
```

Đây là **mảnh minh họa**, cần định nghĩa `operation()`. Trong mã sản phẩm, **không nên** biến handler này thành một chuỗi công việc nặng: dựng JSON phản hồi, format stack trace đầy đủ, gửi HTTP, lấy snapshot lớn, cấp phát cache mới, tạo thread hoặc retry ngay cùng thao tác.

### Chương trình chạy được: bắt rồi ném tiếp lỗi được tạo có chủ đích

Tạo file **`ControlledOomePropagationDemo.java`**:

```java
public class ControlledOomePropagationDemo {
    static void run() {
        try {
            throw new OutOfMemoryError("simulated only");
        } catch (OutOfMemoryError failure) {
            System.out.println("boundary=observed");
            throw failure;
        } finally {
            System.out.println("finally=ran");
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (OutOfMemoryError failure) {
            System.out.println("caller=" + failure.getMessage());
        }
    }
}
```

```bash
javac ControlledOomePropagationDemo.java
java ControlledOomePropagationDemo
```

`stdout`:

```text
boundary=observed
finally=ran
caller=simulated only
```

Đây là **đường thực thi bình thường của Throwable được ném**, không phải một JVM đang thật sự cạn bộ nhớ. Dòng `finally=ran` chứng minh `finally` chạy trong ví dụ này, **không** chứng minh nó luôn chạy khi process bị OS kill hoặc JVM không còn tài nguyên.

### Vì sao handler cũng có thể thất bại?

Trong hoàn cảnh thiếu bộ nhớ, các thao tác sau vẫn có thể cần cấp phát:

- Nối chuỗi, tạo stack trace text, serialize JSON hoặc format một logging event.
- Enqueue một message cho logger bất đồng bộ.
- Khởi tạo class được dùng lần đầu trong handler.
- Tạo object exception mới để bọc lỗi.
- Mở socket, tạo thread, thực hiện I/O phức tạp.
- Tạo response HTTP và state cho framework.

Một handler chứa các bước trên **không được bảo đảm hoàn thành**. Việc catch trả về bình thường sau OOME có thể khiến các request khác tiếp tục vào một JVM đang thiếu bộ nhớ hoặc có state dở dang.

### Có nên dành sẵn một buffer “cấp cứu”?

Một số ứng dụng chuẩn bị một buffer từ sớm rồi bỏ tham chiếu khi có lỗi với hy vọng GC tạo không gian ghi tín hiệu ngắn. Kỹ thuật này chỉ là **best-effort**: có thể không giúp được native/metaspace/thread exhaustion; GC không bảo đảm giải phóng đủ ngay; thread khác vẫn cấp phát; state nghiệp vụ cũng không được khôi phục. **Không coi reserve là bảo đảm recovery trong cùng process**.

## 6. `finally` và `try-with-resources`: bảo đảm ngữ nghĩa nhưng không phải phép màu

Khi một Throwable được ném theo cơ chế Java thông thường, `finally` được thực hiện theo quy tắc điều khiển luồng. `try-with-resources` cố đóng những resource đã tạo thành công, và nếu phần thân cùng `close()` đều ném, lỗi close có thể được ghi là **suppressed exception** của lỗi chính.

Nhưng:

- Nếu process bị OS giết, `Runtime.halt`, mất điện hoặc JVM crash, `finally` không có bảo đảm chạy.
- `close()` có thể cần memory, I/O hoặc ném lỗi khác.
- `finally` tự ném một Throwable khác có thể che mất lỗi gốc khi viết cleanup thủ công sai.
- Monitor của `synchronized` được giải phóng khi thoát vì exception theo luồng Java thông thường, **không** rollback state từng bị sửa.
- Ngay cả khi transaction rollback được thiết kế đúng, vẫn cần kiểm tra giao dịch đã commit hay chưa ở hệ thống khác.

### Chương trình chạy được: resource đóng khi ném OOME giả lập

Tạo file **`ControlledOomeCleanupDemo.java`**:

```java
public class ControlledOomeCleanupDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close=called");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            throw new OutOfMemoryError("controlled failure");
        } catch (OutOfMemoryError failure) {
            System.out.println("caught=" + failure.getMessage());
        }
    }
}
```

```bash
javac ControlledOomeCleanupDemo.java
java ControlledOomeCleanupDemo
```

`stdout`:

```text
close=called
caught=controlled failure
```

Chương trình minh họa TWR **với error được tạo khi heap vẫn khỏe**. Nó không phải stress test, không chứng minh mọi resource vẫn đóng được khi native memory hoặc heap thật sự cạn.

**Thiết kế đúng:** đặt cleanup ở phạm vi sở hữu resource, dùng transaction và cơ chế phục hồi dữ liệu có thể hoạt động sau crash. Không để một JVM OOME chịu trách nhiệm duy nhất cho tính toàn vẹn của hệ thống.

## 7. OOME trong static initialization: trạng thái lỗi có thể tồn tại lâu

Một `OutOfMemoryError` xuất hiện trong quá trình khởi tạo static field/initializer của class có thể làm quá trình **class initialization thất bại**. JVM ghi nhận class đó ở trạng thái initialization error trong class loader tương ứng.

Khi Throwable thoát khỏi class initializer là một **Error** (như OOME), lỗi có thể được truyền ra **nguyên dạng** ở lần đầu. Khi truy cập lại class bị khởi tạo lỗi, JVM thường ném `NoClassDefFoundError` thay vì tự khởi chạy lại toàn bộ static initializer.

### Chương trình chạy được: class đã khởi tạo thất bại

Tạo file **`OomeClassInitializationDemo.java`**:

```java
public class OomeClassInitializationDemo {
    static class Broken {
        static final int VALUE = initialize();

        static int initialize() {
            throw new OutOfMemoryError("simulated during init");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(Broken.VALUE);
        } catch (OutOfMemoryError failure) {
            System.out.println("first="
                    + failure.getClass().getSimpleName());
        }

        try {
            System.out.println(Broken.VALUE);
        } catch (NoClassDefFoundError failure) {
            System.out.println("second="
                    + failure.getClass().getSimpleName());
        }
    }
}
```

```bash
javac OomeClassInitializationDemo.java
java OomeClassInitializationDemo
```

`stdout` trong Java 17:

```text
first=OutOfMemoryError
second=NoClassDefFoundError
```

Phân tích:

1. Truy cập `Broken.VALUE` lần đầu kích hoạt khởi tạo `Broken`.
2. `initialize()` ném OOME giả lập. Vì đó là `Error`, nó có thể truyền ra nguyên dạng, không cần bọc thành `ExceptionInInitializerError`.
3. `Broken` rơi vào trạng thái khởi tạo lỗi trong class loader.
4. Lần truy cập tiếp theo không phải một lần thử lại trong cùng class loader: JVM báo `NoClassDefFoundError`.
5. Không có bộ nhớ nào bị cố tình làm cạn trong bài test.

Trường hợp này cho thấy **khôi phục cục bộ bằng một catch không đủ**: có thể còn dấu vết lỗi kéo dài ngay trong trạng thái runtime. Khi static initializer ném Throwable **không phải Error**, quy tắc JLS có thể bọc thành `ExceptionInInitializerError`; đừng đồng nhất hai trường hợp.

## 8. `System.gc()` không phải nút sửa OOME

`System.gc()` là lời yêu cầu/hint với JVM về nỗ lực thu gom rác, không bảo đảm đúng thời điểm, loại GC hoặc lượng memory giải phóng; một số cấu hình có thể vô hiệu hóa explicit GC.

Ngay cả khi GC thực hiện:

- Object **còn reachable** từ static cache hoặc queue sẽ không tự mất.
- Yêu cầu array vượt giới hạn VM vẫn không hợp lệ.
- Native thread resource không tự hồi phục do full GC.
- Metaspace leak do class loader còn tham chiếu vẫn có thể tồn tại.
- OS OOM killer đã dừng process thì không có code Java nào để gọi GC.

Mẫu anti-pattern:

```java
try {
    doWork();
} catch (OutOfMemoryError failure) {
    System.gc();
    doWork(); // Retry ngay trong cùng tình trạng.
}
```

Đây là **mảnh code sai chính sách**, cần định nghĩa `doWork`. Nó có thể thất bại lặp lại và gây side effect trùng. Không dùng GC thủ công thay cho xác định nguyên nhân, giới hạn workload và quản lý vòng đời dữ liệu.

### `Runtime.freeMemory()` không phải “toàn bộ RAM còn lại”

`Runtime.getRuntime().freeMemory()` mô tả vùng trống trong heap **hiện tại** của JVM, không phải toàn bộ physical RAM, cgroup limit, direct memory hoặc số byte native thread còn có thể cấp phát. `Runtime.maxMemory()` chủ yếu là giới hạn heap; nó cũng không thay thế đo RSS và native memory.

Khi chẩn đoán, cần xem **used, committed, max heap**, non-heap, GC, direct/mapped buffers, số thread, class loaders, queue size, process RSS và giới hạn container cùng lúc.

## 9. Chặn yêu cầu cấp phát quá lớn trước khi chạm JVM limit

Đầu vào từ mạng, file, JSON, ảnh hoặc cấu hình có thể chứa các trường kích thước không tin cậy. Không nên viết:

```java
int count = Integer.parseInt(input);
byte[] buffer = new byte[count];
```

Đây là **mảnh minh họa anti-pattern**, vì `input` chưa có giới hạn, có thể âm hoặc quá lớn. Chương trình cần áp **max size theo nghiệp vụ**, kiểm tra toán học tránh integer overflow và ngân sách tổng cho các request đồng thời.

### Chương trình chạy được: từ chối trước khi cấp phát

Tạo file **`AllocationBudgetDemo.java`**:

```java
public class AllocationBudgetDemo {
    static byte[] allocateBounded(int count, int maxAllowed) {
        if (count < 0 || count > maxAllowed) {
            throw new IllegalArgumentException(
                    "allocation outside allowed budget");
        }
        return new byte[count];
    }

    public static void main(String[] args) {
        System.out.println("length="
                + allocateBounded(8, 16).length);

        try {
            allocateBounded(1000, 16);
        } catch (IllegalArgumentException failure) {
            System.out.println("rejected=" + failure.getMessage());
        }
    }
}
```

```bash
javac AllocationBudgetDemo.java
java AllocationBudgetDemo
```

`stdout`:

```text
length=8
rejected=allocation outside allowed budget
```

Cách này biến input không hợp lệ thành một lỗi **có kiểm soát** mà không cố gây OOME. Tuy vậy, một allocation dưới budget **không phải lời bảo đảm tuyệt đối** sẽ thành công trong mọi JVM: còn nhiều thread và object khác tranh memory.

Để tránh tràn số khi tính ngân sách cho `count * elementSize`, nên dùng kiểu `long` hoặc phép kiểm tra như `Math.multiplyExact` cùng giới hạn rõ ràng trước khi allocate.

## 10. Streaming có giới hạn thay cho đọc tất cả dữ liệu

`Files.readAllBytes(...)`, `Files.readAllLines(...)` hoặc gom tất cả entry vào `ArrayList` có thể phù hợp với input nhỏ. Khi kích thước không chắc chắn, chúng dễ tạo peak memory lớn; hơn nữa giải nén có thể làm dữ liệu **sau giải nén** phình ra nhiều lần.

Hướng xử lý:

- Đọc theo buffer/chunk vừa đủ.
- Giới hạn **tổng byte**, số record, độ dài mỗi token/line và độ sâu cấu trúc.
- Giải phóng resource bằng `try-with-resources` theo ownership.
- Không cache toàn bộ body request trong middleware rồi lại tạo bản sao ở parser.
- Khi concurrent, áp budget **tổng hợp**, không chỉ từng request.

### Chương trình chạy được: enforce giới hạn khi đọc luồng

Tạo file **`StreamingInputBudgetDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class StreamingInputBudgetDemo {
    static long consume(InputStream input, long maxBytes)
            throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("negative limit");
        }

        long total = 0;
        byte[] chunk = new byte[3];
        int count;

        while ((count = input.read(chunk)) != -1) {
            if (count > maxBytes - total) {
                throw new IOException(
                        "input exceeds " + maxBytes + " bytes");
            }
            total += count;
        }
        return total;
    }

    public static void main(String[] args) throws IOException {
        byte[] data = "abcde".getBytes(StandardCharsets.UTF_8);

        try (InputStream input = new ByteArrayInputStream(data)) {
            System.out.println("accepted=" + consume(input, 5));
        }

        try (InputStream input = new ByteArrayInputStream(data)) {
            consume(input, 4);
        } catch (IOException failure) {
            System.out.println("rejected=" + failure.getMessage());
        }
    }
}
```

```bash
javac StreamingInputBudgetDemo.java
java StreamingInputBudgetDemo
```

`stdout`:

```text
accepted=5
rejected=input exceeds 4 bytes
```

Ví dụ chỉ dùng buffer 3 byte, chặn dữ liệu khi vượt budget thay vì tạo mảng kích thước không hạn chế. Đây mới là **kiểm tra tổng byte**; một parser sản xuất còn phải giới hạn độ dài token và nesting. `BufferedReader.readLine()` vẫn có thể tạo một `String` khổng lồ nếu một dòng cực dài, dù tổng file được đọc theo từng dòng.

Đừng chỉ tin `Content-Length` do client cung cấp; phải thực sự **enforce số byte đã nhận**, đồng thời phân biệt kích thước dữ liệu nén và sau giải nén.

## 11. Bounded cache: GC không tự xóa object còn tham chiếu

Một bộ nhớ cache như:

```java
static final java.util.Map<String, byte[]> CACHE =
        new java.util.HashMap<>();

static void put(String key, byte[] value) {
    CACHE.put(key, value);
}
```

là **mảnh code nhận diện rủi ro**. Nếu key mới tăng mãi, static map vẫn tham chiếu payload; GC không tự thu hồi chúng theo ý định nghiệp vụ.

Chọn biện pháp như eviction, TTL, giới hạn **số entry**, tốt hơn nữa là giới hạn **trọng lượng byte** khi các object có kích thước rất khác nhau. Cần theo dõi cache hit ratio, tổng size, eviction count và vòng đời. `WeakHashMap` cũng **không phải bounded cache mặc định**: nó có điều kiện xóa theo tham chiếu yếu tới key và có nhiều hạn chế nếu value tham chiếu ngược.

### Chương trình chạy được: cache ba entry

Tạo file **`BoundedCacheDemo.java`**:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class BoundedCacheDemo {
    static class LimitedMap extends LinkedHashMap<Integer, String> {
        private final int capacity;

        LimitedMap(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(
                Map.Entry<Integer, String> eldest) {
            return size() > capacity;
        }
    }

    public static void main(String[] args) {
        LimitedMap cache = new LimitedMap(3);

        for (int i = 1; i <= 5; i++) {
            cache.put(i, "value-" + i);
        }

        System.out.println("size=" + cache.size());
        System.out.println("keys=" + cache.keySet());
    }
}
```

```bash
javac BoundedCacheDemo.java
java BoundedCacheDemo
```

`stdout`:

```text
size=3
keys=[3, 4, 5]
```

Do `LinkedHashMap` mặc định giữ insertion order, các entry cũ nhất bị loại khi vượt size 3. Đây là kỹ thuật học tập đơn giản, **chưa có synchronization** và **chưa giới hạn tổng byte payload**. Trong ứng dụng nhiều thread, cần cấu trúc cache thread-safe phù hợp và ngân sách toàn hệ thống.

## 12. Giới hạn thread pool **và** task queue

Giới hạn số worker chưa đủ nếu hàng đợi task không giới hạn. Một queue khổng lồ có thể giữ closure và các payload của hàng triệu request.

Ví dụ:

- `Executors.newFixedThreadPool(n)` có số worker cố định nhưng **queue mặc định không giới hạn**.
- `Executors.newCachedThreadPool()` có thể tạo thêm thread theo tải và cần chính sách giới hạn thực tế.
- `CompletableFuture` hoặc hàng đợi framework có thể giữ state chờ lâu.
- Retry vô hạn có thể tạo thêm task và dữ liệu khi hệ thống vốn đang quá tải.

Nên dùng bounded queue, admission control, semaphore, backpressure hoặc reject có kiểm soát; quyết định dựa trên yêu cầu độ trễ và idempotency.

### Chương trình chạy được: bounded executor

Tạo file **`BoundedExecutorDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedExecutorDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.CallerRunsPolicy());

        AtomicInteger done = new AtomicInteger();

        for (int i = 0; i < 6; i++) {
            pool.execute(done::incrementAndGet);
        }

        pool.shutdown();
        boolean terminated =
                pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("terminated=" + terminated);
        System.out.println("completed=" + done.get());
    }
}
```

```bash
javac BoundedExecutorDemo.java
java BoundedExecutorDemo
```

`stdout` trong một môi trường lập lịch bình thường:

```text
terminated=true
completed=6
```

Queue tối đa 2 task; một worker; khi bão hòa, `CallerRunsPolicy` có thể làm thread submit chạy task nhằm tạo backpressure. Sau khi submit hết sáu task, chương trình shutdown và chờ chúng hoàn thành, nên counter là 6 trong điều kiện thời gian chờ đủ.

**Giới hạn:** `CallerRunsPolicy` không phải giải pháp cho mọi ứng dụng; nó có thể tăng latency ở caller hoặc gây tác động trong request thread. Khi executor đã shutdown, handler này có thể bỏ task theo contract. Queue dù bounded vẫn không giới hạn mọi memory dùng bên ngoài nó. Trong môi trường quá tải thật, nên có timeout/rejection/observability.

## 13. Khi nào có thể phục hồi cục bộ?

Không có quy tắc Java tổng quát rằng **mọi OOME đều bắt buộc giết JVM**, nhưng cũng không có bảo đảm có thể tiếp tục phục vụ. Một phương án phục hồi cục bộ chỉ đáng xem xét nếu ứng dụng có **bằng chứng cụ thể**:

- Lỗi bị giới hạn trong một **phân vùng/tác vụ** có ownership và state rõ ràng.
- Resource có thể được thu hồi an toàn.
- Các bất biến dữ liệu và transaction đã được khôi phục hoặc có thể kiểm chứng.
- Không làm rò rỉ lỗi sang thread/shared cache/singleton/class initialization.
- Memory pressure đã giảm theo số liệu đáng tin.
- Thao tác retry nếu có là idempotent, có giới hạn và backoff.
- Người vận hành chấp nhận residual risk và có thể ngắt instance từ xa.

Với phần lớn dịch vụ backend, các điều kiện này **khó chứng minh khi JVM thực sự cạn tài nguyên**. Một chính sách đáng tin hơn là coi instance **không còn sẵn sàng phục vụ**, phát tín hiệu best-effort và dùng **supervisor ngoài process** để thay thế nó.

### Restart không sửa root cause

Restart dọn trạng thái tiến trình bị lỗi và tạo cơ hội phục vụ lại, nhưng nếu heap leak, class-loader leak, queue tăng vô hạn hoặc input thiếu giới hạn vẫn còn, instance mới có thể OOME lần nữa. Phải sửa **nguyên nhân thiết kế, cấu hình và workload** rồi kiểm chứng dưới load test.

## 14. Biên process: phát hiện, fail-fast và restart

Một mô hình thực tế:

```text
Ứng dụng có memory budget và health checks
                   |
                   v
             OOME hoặc OOM kill
                   |
                   v
        Instance không còn đáng tin
                   |
                   +--> Tín hiệu lỗi best-effort (nếu còn thực thi)
                   +--> Dừng/thoát theo chính sách JVM, service
                   |
                   v
  Service manager / container orchestrator (ngoài JVM)
                   |
                   +--> Thấy process chết, health check hỏng, mất heartbeat
                   +--> Áp dụng restart policy / backoff
                   v
             Khởi động instance mới
                   |
                   +--> Kiểm tra readiness
                   +--> Reconcile dữ liệu, task dở dang
                   v
             Nhận workload trở lại
```

Nếu process bị kill trực tiếp, code Java có thể **không ghi được log nào**. Vì thế công cụ ngoài process rất quan trọng.

Khi quyết định dừng process:

- `System.exit(...)` yêu cầu shutdown theo JVM và có thể chạy shutdown hooks. Hooks có thể cần memory, I/O hoặc lock; **không bảo đảm xử lý hoàn hảo khi OOME thật**.
- `Runtime.halt(...)` kết thúc quyết liệt hơn, bỏ shutdown hooks và có thể mất dữ liệu chưa flush. Không dùng tùy tiện như một cách sửa memory.
- JVM flags của HotSpot có thể hỗ trợ process exit/crash trên OOME, được cấu hình theo chính sách vận hành và tính hỗ trợ JVM.

**Không rải lệnh `System.exit` vào những method nghiệp vụ** chỉ vì catch OOME. Đây là quyết định ở tầng vận hành để bảo vệ toàn hệ thống.

### Bảo đảm dữ liệu khi restart

Dịch vụ cần tính nhất quán thường dùng transaction, idempotency keys, durable queues, outbox/inbox pattern, checkpoint và reconciliation. Nếu process chết giữa ghi dữ liệu, restart **không tự hoàn tác** mọi side effect; hệ thống phải biết bước nào đã commit và bước nào cần replay/compensate an toàn.

## 15. JVM options phục vụ chẩn đoán (HotSpot Java 17)

Các tùy chọn sau là **minh họa cấu hình**, không phải lệnh tạo lỗi:

```bash
java -Xms128m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./diagnostics -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar app.jar
```

`app.jar` do ứng dụng cung cấp; đường dẫn dump phải được chuẩn bị và có quyền ghi. Cấu hình memory 128/512 MiB chỉ là ví dụ, **không phải mức khuyến nghị** cho mọi workload.

| Tùy chọn | Ý nghĩa |
| --- | --- |
| `-Xms128m` | Thiết lập mức heap ban đầu theo HotSpot |
| `-Xmx512m` | Giới hạn tối đa của Java heap, không bao gồm toàn bộ native RSS |
| `-XX:+HeapDumpOnOutOfMemoryError` | Yêu cầu tạo heap dump khi JVM gặp OOME thích hợp, theo khả năng thực tế |
| `-XX:HeapDumpPath=...` | Chỉ định nơi lưu dump |
| `-Xlog:gc*...` | Ghi thông tin GC theo unified logging của JVM |

Heap dump có thể **rất lớn** và chứa secrets, token, PII, nội dung request và thông tin giao dịch. Bảo vệ quyền đọc, dung lượng đĩa và retention. Không bảo đảm dump luôn tạo được: thiếu disk, OOM kill ngoài JVM, native failure hoặc những điều kiện nghiêm trọng khác có thể ngăn thu thập.

### Fail-fast flags

Một số HotSpot JVM hỗ trợ:

```text
-XX:+ExitOnOutOfMemoryError
-XX:+CrashOnOutOfMemoryError
```

Đây là **cờ JVM theo implementation**, không phải cú pháp Java source hoặc bảo đảm của mọi JVM vendor. Chọn theo yêu cầu giữ bằng chứng, core dump, restart và supervisor. Cần kiểm thử trên JDK/JVM cụ thể trước khi dùng trong production.

### Nên thu thập gì?

- Heap usage: **used, committed, max**, tốc độ cấp phát và dữ liệu GC.
- Metaspace/class loaders, loaded class count.
- Direct/mapped buffer pools, native memory và thread count.
- Process RSS, cgroup/container memory, host OOM events.
- Queue lengths, active requests, cache entries, payload sizes.
- Thời điểm tăng memory so với deploy, workload và release mới.
- Heap dump và GC logs từ **môi trường kiểm soát**, tránh bật tracing nặng gây thêm áp lực.

NMT (`Native Memory Tracking`) trong HotSpot hữu ích để xem một số nhóm native allocations của JVM, nhưng cần cấu hình và overhead; không bảo đảm mô tả đầy đủ memory của mọi thư viện native/JNI.

## 16. Điều tra heap dump: giữ tham chiếu và retained size

Nếu đã xác nhận vấn đề thuộc Java heap và có heap dump phù hợp:

1. Mở dump bằng Eclipse Memory Analyzer, VisualVM hoặc công cụ JVM tương thích.
2. Xem histogram: class nào có nhiều instance, nhiều byte trực tiếp.
3. Xem **dominator tree** và retained size.
4. Theo **paths to GC roots** để biết object nào giữ dữ liệu còn sống.
5. Tập trung vào caches, static maps, `ThreadLocal`, listeners, request/session objects, queues, buffer arrays.
6. Phân biệt một **memory leak** với tập dữ liệu lớn còn cần thiết hoặc allocation spike hợp lệ.
7. So sánh snapshot theo thời gian nếu có cách thu thập an toàn.
8. Sửa vòng đời/giới hạn dữ liệu; load test để chứng minh memory trở lại ổn định.
9. Dọn và bảo vệ dump vì có thể chứa dữ liệu nhạy cảm.

**Shallow size** là memory trực tiếp của một object. **Retained size** là lượng heap được giải phóng gián tiếp nếu đối tượng không còn giữ phần đồ thị liên quan. Một `HashMap` nhỏ về shallow size vẫn có thể giữ hàng triệu payload phía sau.

Nếu OOME liên quan thread/native/metaspace, chỉ phân tích một heap dump **có thể không tìm được toàn bộ nguyên nhân**; hãy kiểm tra số liệu vùng memory tương ứng.

## 17. OOME khác `StackOverflowError` và lỗi nghiệp vụ thường gặp

| Loại | Nhánh | Ý nghĩa điển hình |
| --- | --- | --- |
| `OutOfMemoryError` | `VirtualMachineError` | Không thể đáp ứng cấp phát memory/resource |
| `StackOverflowError` | `VirtualMachineError` | Stack một thread vượt giới hạn, thường do recursion |
| `InternalError` | `VirtualMachineError` | Sự cố nội bộ runtime |
| `ArithmeticException` | `RuntimeException` | Ví dụ chia số nguyên cho 0 |
| `IOException` | Checked `Exception` | Lỗi input/output cần xử lý/khai báo |

Không đồng nhất `StackOverflowError` với OOME. Tăng `-Xmx` không sửa một recursion vô hạn theo cách trực tiếp. Một lỗi `IOException` hoặc `ArithmeticException` có thể có quy trình xử lý theo nghiệp vụ; OOME không nên được đẩy vào cùng handler rồi trả response bình thường.

## 18. Lỗi phổ biến cần tránh

**“OOME nào cũng là Java heap đầy.”** Sai. Phải xem metaspace, native buffers, thread resources, VM limits và OS/container.

**“Bắt được OOME thì service đã phục hồi.”** Sai. JVM còn chạy không chứng minh ứng dụng và dữ liệu nhất quán.

**“`catch (Exception)` luôn bắt mọi `Throwable`.”** Sai. Nó không bắt `Error`.

**“`catch (Throwable)` rồi trả response thành công là ổn.”** Nguy hiểm: có thể che những lỗi nghiêm trọng và làm lan truyền trạng thái không còn đáng tin.

**“`System.gc()` chắc chắn giải phóng đủ memory.”** Sai. Nó không dọn reachable objects, và GC theo yêu cầu không có bảo đảm như vậy.

**“`finally` nhất định chạy dù process bị kill.”** Sai.

**“TWR luôn đóng xong mọi resource dù JVM đã hết memory.”** Không có bảo đảm tuyệt đối trong sự cố thảm khốc.

**“Nếu OOME giả lập và catch chạy, recovery trong OOME thật cũng sẽ chạy.”** Sai. Ném một đối tượng Error nhỏ không làm cạn memory.

**“Có heap dump nghĩa là nó mô tả hết native memory.”** Sai. Heap dump chủ yếu cho trạng thái Java heap.

**“Không thấy heap dump tức là không có OOME.”** Sai. Process có thể bị kill trực tiếp hoặc không còn khả năng ghi dump.

**“`-Xmx` là giới hạn tổng RAM của process.”** Sai. Native, metaspace và thread stacks còn chiếm memory ngoài heap.

**“Tăng `-Xmx` luôn sửa OOME.”** Sai. Đôi khi làm container thiếu RAM native nặng hơn.

**“GC sẽ tự xóa static cache dù ứng dụng còn giữ references.”** Sai.

**“Fixed thread pool có queue giới hạn sẵn.”** Sai với cấu hình từ `Executors.newFixedThreadPool` thường gặp.

**“Streaming theo dòng chắc chắn không bao giờ có input lớn.”** Sai. Một dòng/token có thể dài bất thường.

**“Chỉ cần kiểm tra `Content-Length` là đã chặn mọi payload lớn.”** Sai. Input có thể khai báo thiếu/sai, và dữ liệu sau giải nén còn có thể lớn hơn.

**“Khởi tạo static class thất bại rồi truy cập lần nữa sẽ tự thử lại.”** Sai. Class có thể ở trạng thái erroneous.

**“Emergency buffer chắc chắn giúp ghi log và cứu JVM.”** Sai. Nó là best-effort, không xử lý nhiều nguồn OOME khác.

**“Restart là cách sửa memory leak.”** Sai. Restart là phục hồi instance; root cause vẫn phải sửa.

**“Heap dump có thể chia sẻ công khai để debug.”** Nguy hiểm. Dump có thể chứa dữ liệu nhạy cảm.

## 19. Bài tập tự kiểm tra

1. **OOME checked hay unchecked?** Unchecked, vì nằm dưới `Error`.
2. **`catch (Exception)` có bắt OOME?** Không.
3. **JVM có thể sống sau khi ném OOME?** Có thể, nhưng không bảo đảm phục vụ an toàn.
4. **OS OOM kill có cho Java catch OOME trước không?** Không nhất thiết; process có thể chết trực tiếp.
5. **Tăng `-Xmx` có chắc sửa direct/native OOME?** Không.
6. **`System.gc()` có dọn object còn tham chiếu từ static map không?** Không.
7. **Fixed thread pool thường có queue mặc định giới hạn không?** Không.
8. **Cache 3 entry có chắc dùng ít memory?** Không, payload mỗi entry có thể cực lớn.
9. **Làm sao tránh allocation từ một request tùy ý yêu cầu mảng 2 GB?** Validation kích thước, ngân sách trước cấp phát, streaming khi phù hợp.
10. **`try-with-resources` chạy trên OOME giả lập có bảo đảm chạy khi OS kill không?** Không.
11. **OOME trong static initialization có thể gây gì ở lần truy cập class tiếp theo?** `NoClassDefFoundError`.
12. **Tại sao OOME trong một request có thể ảnh hưởng request khác?** Heap/native resource và shared state là tài nguyên chung process.
13. **Bắt OOME rồi retry ngay có rủi ro gì?** Lặp memory pressure và lặp side effects chưa biết trạng thái.
14. **Tại sao không log toàn bộ JSON stack trace trong catch OOME?** Logging có thể cần cấp phát/resource không còn khả dụng.
15. **`Runtime.freeMemory()` có đại diện toàn bộ RAM vật lý trống không?** Không.
16. **Cần điều tra gì khi OOME thông báo Metaspace?** Class loaders, số class, metaspace limits và native usage.
17. **Có thể bảo đảm tạo được heap dump khi process bị OOM killer chấm dứt không?** Không.
18. **Retained size hữu ích cho điều tra leak ra sao?** Cho biết object nào giữ các object khác còn reachable.
19. **Nếu lỗi do array vượt VM limit, tăng GC có giúp chắc chắn?** Không.
20. **Tại sao cần supervisor ngoài JVM?** Để phát hiện và thay thế process khi code bên trong không còn chạy đáng tin.
21. **Restart có tự rollback giao dịch ngoài hệ thống không?** Không; cần transaction/idempotency/reconciliation.
22. **`OutOfMemoryError` và `StackOverflowError` có cùng class không?** Không, là hai subtype của `VirtualMachineError`.
23. **Nên ưu tiên giới hạn input/cache/queue hay chờ catch OOME?** Ưu tiên phòng tránh trước.
24. **Một `new OutOfMemoryError("simulated")` chứng minh điều gì?** Chỉ exception flow trong JVM bình thường, không phải recovery thật.

## 20. Tài liệu chính thức Java 17

- [Java SE 17 — OutOfMemoryError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html): ý nghĩa khi máy ảo không thể cấp phát bộ nhớ cần thiết.
- [Java SE 17 — VirtualMachineError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VirtualMachineError.html): cây phân loại lỗi máy ảo.
- [Java SE 17 — Error](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Error.html): các vấn đề nghiêm trọng mà ứng dụng thông thường không được kỳ vọng phục hồi.
- [Java SE 17 — Runtime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): `gc`, `maxMemory`, `freeMemory`.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): queue, giới hạn worker, rejection.
- [Java SE 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): `removeEldestEntry`.
- [Java SE 17 — MemoryMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html): heap và non-heap telemetry.
- [Java SE 17 — BufferPoolMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html): thông tin direct/mapped buffer pools.
- [Java Language Specification 17 — `11 Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): checked/unchecked và lan truyền Throwable.
- [Java Language Specification 17 — `12.4.2 Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): class erroneous sau initialization failure.
- [Java Language Specification 17 — `14.20.3 try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cleanup/suppressed exceptions.
- [Java SE 17 — java launcher options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xmx`, heap dumps, logging và HotSpot flags.
- [Java SE 17 — jcmd](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): JVM diagnostics.

**Ghi nhớ:** `OutOfMemoryError` là **unchecked `VirtualMachineError`**, không chỉ là “heap đầy”. Việc JVM còn chạy hoặc handler bắt được nó **không bảo đảm** bộ nhớ, trạng thái khởi tạo class, các tài nguyên và dữ liệu nghiệp vụ đã phục hồi. Thiết kế đúng tập trung vào **budget, streaming, cache eviction, bounded concurrency, giám sát, chẩn đoán đúng vùng memory, fail-fast và cơ chế thay thế instance bên ngoài tiến trình**. Các ví dụ ném OOME giả lập chỉ là bài học về ngữ nghĩa Java, không phải chứng cứ rằng một JVM đã cạn memory sẽ phục vụ an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-005">StackOverflowError và đệ quy không kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# StackOverflowError và đệ quy không kiểm soát trong Java

> **Phạm vi:** Java Core → Exception → Error và khởi tạo. `StackOverflowError` là một loại `Error` mà JVM thường phát sinh khi quá trình thực thi của **một thread** cần thêm không gian *call stack* nhưng không thể đáp ứng. Nguồn gây ra rất hay gặp là **đệ quy không có điểm dừng, không tiến gần điểm dừng, gọi qua lại vô hạn, hoặc đệ quy hữu hạn nhưng quá sâu**. Điều quan trọng không phải “bắt lỗi để tiếp tục” mà là thiết kế **điều kiện dừng, bước tiến, giới hạn độ sâu và thuật toán duyệt dữ liệu phù hợp**.

## 1. Nhận diện đúng `StackOverflowError`

Cây kế thừa rút gọn:

```text
java.lang.Throwable
├── java.lang.Exception
│   └── java.lang.RuntimeException
└── java.lang.Error
    └── java.lang.VirtualMachineError
        └── java.lang.StackOverflowError
```

`StackOverflowError` nằm **trong nhánh `Error`**, không phải `Exception`. Vì vậy:

- Nó là **unchecked throwable**: compiler không yêu cầu `catch` hoặc `throws`.
- `catch (Exception e)` **không bắt được** nó. `catch (Error e)` hay `catch (Throwable t)` có thể bắt theo hệ kế thừa, nhưng không nên biến việc bắt này thành cách vận hành bình thường.
- Nó mô tả thiếu khả năng mở rộng stack cho lời gọi/hoạt động tiếp theo, thường từ một đường gọi sâu, không phải một exception nghiệp vụ dự kiến.
- `VirtualMachineError` là nhánh cho các thất bại liên quan tới giới hạn hoặc điều kiện nghiêm trọng của máy ảo; các Error dạng này không nên xem tương đương lỗi dữ liệu đầu vào thông thường.
- Một exception lớp `IOException` hay `IllegalArgumentException` không “chuyển thành” `StackOverflowError` chỉ vì xuất hiện trong phương thức đệ quy. Đây là các loại failure khác nhau.

**Chốt ý:** `StackOverflowError` thường là **triệu chứng của một đường gọi vượt giới hạn stack**, chứ không tự cho biết điều kiện dừng nào sai. Phải lần theo code và dữ liệu đã tạo ra độ sâu ấy.

### 1.1. Vì sao từ “Overflow” dễ gây hiểu nhầm?

Đây là **stack overflow**, không phải **tràn số nguyên**:

```java
int x = Integer.MAX_VALUE;
int y = x + 1; // int overflow: số đổi dấu, thường không ném Error.
```

Và cũng không phải **heap memory đầy**: `OutOfMemoryError` là một nhánh khác của `VirtualMachineError`. Chúng có thể liên quan khi chương trình có lượng dữ liệu/lời gọi lớn, nhưng có cơ chế và chiến lược chẩn đoán khác nhau.

| Lỗi/hiện tượng | Điểm chính | Ví dụ |
| --- | --- | --- |
| `StackOverflowError` | Không đáp ứng thêm nhu cầu stack của thread | Đệ quy vô hạn hoặc quá sâu |
| `OutOfMemoryError` | Không đủ nguồn lực bộ nhớ cho yêu cầu cấp phát hoặc điều kiện VM tương ứng | Giữ một lượng lớn object trên heap |
| Tràn số `int` | Tính toán vượt miền biểu diễn số nguyên | `Integer.MAX_VALUE + 1` |
| `IllegalArgumentException` | Tham số vi phạm hợp đồng do API kiểm tra | Độ sâu âm bị từ chối |
| `NullPointerException` | Dereference `null` | `node.next.value` khi `next == null` |

Không khắc phục `StackOverflowError` bằng `Math.addExact`, và không kết luận “heap rất lớn thì đệ quy sâu bao nhiêu cũng được”.

## 2. Call stack là gì?

Mỗi thread Java có một **stack thực thi** phục vụ các lời gọi phương thức. Khi gọi method, JVM có thể tạo một **frame** chứa trạng thái cần thiết cho lần gọi ấy, chẳng hạn tham số, local variables, toán hạng trung gian và thông tin điều khiển theo mô hình JVM. Khi method trả về, frame của lần gọi có thể được loại khỏi stack.

Minh họa:

```text
main()
└── calculate(3)
    └── calculate(2)
        └── calculate(1)
            └── calculate(0)
                return
            return
        return
    return
main() tiếp tục
```

Các frame tồn tại đồng thời khi lời gọi con chưa kết thúc. Số frame được giữ phụ thuộc vào độ sâu chuỗi gọi.

**Không phải mọi lần gọi đều có kích thước frame như nhau.** Nhu cầu stack có thể phụ thuộc vào bytecode, dữ liệu local, trình biên dịch JIT, cơ chế tối ưu, kiến trúc CPU và cấu hình VM. Do đó không thể nói “một thread Java luôn gọi đệ quy tối đa đúng 10.000 lần”.

### 2.1. Stack khác heap

Khái quát:

- **Stack** gắn với luồng thực thi/phạm vi lời gọi; mỗi thread có stack riêng.
- **Heap** chứa phần lớn đối tượng và mảng do chương trình cấp phát; nhiều thread có thể tham chiếu tới object trên heap.
- Tham chiếu tới một object có thể nằm trong local variable của frame; **object được tham chiếu** không vì thế mà “nằm trên stack” theo quy tắc tổng quát.
- Cấp phát thêm object trên heap không trực tiếp kéo dài stack của một lời gọi, nhưng các thao tác tạo/duyệt object vẫn có thể dùng stack.
- JVM có thể tối ưu một số chi tiết vật lý; suy luận ở đây tập trung vào semantics của lời gọi và giới hạn tài nguyên.

**Hệ quả:** bạn có thể giữ một cây dữ liệu rất lớn trên heap nhưng duyệt bằng thuật toán lặp với stack chủ động, tránh phụ thuộc vào chuỗi lời gọi JVM quá sâu.

## 3. Những nguyên nhân phổ biến của đệ quy không kiểm soát

### 3.1. Không có *base case* (điều kiện dừng)

```java
// CỐ Ý SAI: minh họa phương thức gây đệ quy vô hạn.
static void repeat() {
    repeat();
}
```

Không có đường nào trả về. Một JVM Java thông thường sẽ tiếp tục mở rộng chuỗi lời gọi đến khi không còn đáp ứng được stack và ném `StackOverflowError`.

### 3.2. Có base case nhưng không tiến tới nó

```java
// CỐ Ý SAI: n không giảm nên n > 0 mãi đúng nếu khởi đầu n > 0.
static int countdown(int n) {
    if (n == 0) {
        return 0;
    }
    return countdown(n);
}
```

Điểm dừng có mặt nhưng **tham số của lần gọi sau không đổi**. Với n khởi đầu bằng 5, luồng không tiến gần `n == 0`.

### 3.3. Tiến sai chiều

```java
// CỐ Ý SAI: n bắt đầu dương lại tăng mãi.
static int countdown(int n) {
    if (n == 0) {
        return 0;
    }
    return countdown(n + 1);
}
```

Nếu n tăng, nó không tiến về 0. Với `int`, tràn số có thể khiến n đổi dấu, nhưng không biến thuật toán này thành đúng; đừng dựa vào overflow để tình cờ chạm điều kiện dừng.

### 3.4. Đệ quy hai chiều (*mutual recursion*)

```java
// CỐ Ý SAI khi không có điều kiện dừng thích hợp.
static void first() {
    second();
}

static void second() {
    first();
}
```

Một method nhìn riêng có thể chỉ gọi “method khác”, nhưng chuỗi `first → second → first → ...` là đệ quy vô hạn. Mô hình gọi qua nhiều service, callback, serializer, getter và `toString()` cũng có thể tạo chu trình tương tự.

### 3.5. Dữ liệu có chu trình

Duyệt linked structure hoặc graph theo lời gọi đệ quy mà không đánh dấu đã thăm có thể đi mãi theo:

```text
A.next → B
B.next → C
C.next → A
```

Ở đây điều kiện `node != null` **không đủ**: các liên kết không bao giờ tới `null`. Thuật toán cần nhận diện các node đã gặp hoặc có hợp đồng đầu vào bảo đảm cấu trúc không có chu trình.

### 3.6. Đệ quy kết thúc được nhưng sâu bất thường

Một danh sách liên kết dài hàng trăm nghìn phần tử có thể hoàn toàn **không có chu trình**. Duyệt bằng:

```java
static int size(Node node) {
    return node == null ? 0 : 1 + size(node.next);
}
```

có thể vẫn làm tràn stack khi số lời gọi lồng nhau quá lớn. **Đúng điều kiện dừng chưa đủ**; phải đánh giá **giới hạn độ sâu thực tế**.

## 4. Ba điều kiện của một đệ quy được thiết kế tốt

Hãy kiểm tra đủ ba điều:

1. **Base case:** có trạng thái mà ở đó method trả kết quả, không gọi tiếp?
2. **Progress:** ở mỗi lời gọi tiếp theo có một đại lượng đo tiến triển hướng tới base case (ví dụ n giảm về 0, số phần tử chưa duyệt giảm)?
3. **Depth bound:** có thể chứng minh/giới hạn độ sâu tối đa đủ nhỏ cho môi trường chạy?

Với đệ quy trên số nguyên n không âm:

```java
static int sumUpTo(int n) {
    if (n == 0) {
        return 0;
    }
    return n + sumUpTo(n - 1);
}
```

Đây là method **minh họa**, giả định n không âm và tổng không tràn `int`. Base case `n == 0` và progress `n - 1` giúp method dừng với n hợp lệ, nhưng depth khoảng n+1 nên vẫn không thích hợp cho n tùy ý cực lớn.

### 4.1. Java 17 — ví dụ an toàn: đệ quy có base case và bước tiến

Tạo **`BoundedRecursionDemo.java`**:

```java
public class BoundedRecursionDemo {
    static long factorial(int n) {
        if (n < 0 || n > 20) {
            throw new IllegalArgumentException(
                    "n phải thuộc [0, 20]");
        }
        if (n <= 1) {
            return 1L;
        }
        return Math.multiplyExact(n, factorial(n - 1));
    }

    public static void main(String[] args) {
        System.out.println("0! = " + factorial(0));
        System.out.println("5! = " + factorial(5));
        System.out.println("10! = " + factorial(10));

        try {
            factorial(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy JDK 17+:

```bash
javac -encoding UTF-8 BoundedRecursionDemo.java
java BoundedRecursionDemo
```

Stdout:

```text
0! = 1
5! = 120
10! = 3628800
Invalid: IllegalArgumentException
```

Giải thích:

- `factorial(0)` và `factorial(1)` trả `1`: **base case**.
- Mỗi bước dùng `n - 1`, nên `n` tiến về base case.
- Giới hạn `n <= 20` bảo đảm kết quả factorial vừa trong phạm vi `long`; `20!` phù hợp, còn `21!` vượt `long`.
- `Math.multiplyExact` kiểm tra tràn số, **không** phải cơ chế chống stack overflow. Giới hạn n mới đảm bảo số lời gọi nhỏ.
- Với n âm, method chủ động ném `IllegalArgumentException` thay vì cho phép đệ quy sai.
- Đây là ví dụ an toàn để học: không cố tạo `StackOverflowError` trên đường chạy bình thường.

## 5. Một ví dụ cố ý gây `StackOverflowError` — chỉ dùng để nhận diện

Nếu muốn quan sát đúng loại lỗi, có thể chạy trong tiến trình thử nghiệm độc lập với đoạn sau. **Không sử dụng đây làm cách tính toán hay cơ chế dừng vòng lặp trong ứng dụng thật.** Đừng phụ thuộc vào số lần gọi/độ dài stack trace.

Tạo **`IntentionalStackOverflowDemo.java`**:

```java
public class IntentionalStackOverflowDemo {
    static void infinite() {
        infinite(); // Cố ý không có base case.
    }

    public static void main(String[] args) {
        try {
            infinite();
        } catch (StackOverflowError error) {
            System.out.println("Caught: "
                    + error.getClass().getSimpleName());
        }
    }
}
```

Chạy trên JDK 17 tiêu chuẩn trong một tiến trình thí nghiệm:

```bash
javac -encoding UTF-8 IntentionalStackOverflowDemo.java
java IntentionalStackOverflowDemo
```

Thông thường stdout là:

```text
Caught: StackOverflowError
```

**Các giới hạn cần đọc trước khi thử:**

- Đây là ví dụ **cố ý dùng hết stack của thread**; không chạy trong server production, request handler hoặc môi trường có tài nguyên rất hạn chế.
- Việc VM xử lý yêu cầu stack lớn có phụ thuộc implementation và tài nguyên; trong thực tế `StackOverflowError` là kết quả điển hình, nhưng không nên lấy số depth, thời gian hay độ dài trace làm chuẩn portable.
- Trong một trạng thái thiếu stack nghiêm trọng, hoạt động logging/formatting/cleanup phức tạp sau khi bắt có thể gặp khó khăn hoặc phát sinh thêm lỗi. Đoạn này cố giữ handler rất đơn giản và không thử gọi lại `infinite()`.
- `catch(StackOverflowError)` chỉ phục vụ **giáo dục/chẩn đoán**. Sản phẩm thật phải sửa base case, progress hoặc giới hạn độ sâu thay vì dựa vào catch như một “lối thoát an toàn”.
- Trong một số môi trường, cấu hình thread stack/VM khiến biểu hiện khác hoặc tiến trình bị hệ điều hành/VM kết thúc trước khi người học xem được output; đừng kết luận chương trình đúng chỉ vì không in được dòng mẫu.

### 5.1. Tại sao lỗi có thể xuất hiện trước khi ghi log cuối?

Lời gọi `infinite` tiếp tục được lồng vào nhau. Ở thời điểm cần một frame/nguồn stack mà thread không thể cung cấp, VM phát sinh error. Chỉ khi control được unwind đủ để tới handler và handler có thể chạy, dòng `Caught` mới xuất hiện.

Nếu log một stack trace rất dài, lượng text và chi phí output có thể lớn. Không cần in toàn bộ trace chỉ để chứng minh class của error trong bài tập.

### 5.2. Vì sao không viết `catch(Exception)`?

Vì `StackOverflowError` không nằm dưới `Exception`; nó thuộc `Error`. Nếu thay handler bằng:

```java
// KHÔNG bắt được StackOverflowError:
catch (Exception e) {
    System.out.println("Handled");
}
```

đây chỉ là **mảnh code minh họa**, không phải một chương trình hoàn chỉnh. Handler sẽ không nhận `StackOverflowError` theo quan hệ kế thừa Java.

## 6. Tràn stack không đồng nghĩa với hết heap

Java sử dụng những vùng nhớ với vai trò khác nhau. Đệ quy thường làm tăng nhu cầu stack, còn một thuật toán lưu quá nhiều object vào collection có thể làm tăng áp lực heap.

Ví dụ:

```java
static void recurse() {
    recurse();
}
```

Method này tạo chuỗi lời gọi sâu, **không bắt buộc** giữ hàng triệu object trên heap.

Ngược lại:

```java
List<byte[]> all = new ArrayList<>();
while (true) {
    all.add(new byte[1024 * 1024]);
}
```

Đây là **phản ví dụ nguy hiểm, không chạy**. Nó liên tục giữ các mảng lớn trên heap và có thể gây `OutOfMemoryError`, không phải đệ quy.

Một chương trình có thể đồng thời gây áp lực lên cả stack lẫn heap (ví dụ mỗi lời gọi tạo object lớn), nhưng để chẩn đoán cần xem **loại Throwable**, stack trace, pattern cấp phát và nguyên nhân cụ thể.

## 7. Thông điệp và stack trace: đọc ra sao?

Khi gặp lỗi:

```text
Exception in thread "main" java.lang.StackOverflowError
    at example.Parser.read(Parser.java:41)
    at example.Parser.read(Parser.java:41)
    at example.Parser.read(Parser.java:41)
    ...
```

Đây là **sơ đồ trace minh họa**, không phải stderr cố định. Dòng lặp lại cùng method và số dòng thường gợi ý đệ quy trực tiếp. Nếu có các frame lặp kiểu:

```text
A.convert(...)
B.map(...)
A.convert(...)
B.map(...)
...
```

hãy nghi ngờ **đệ quy gián tiếp** hoặc chu trình gọi.

### 7.1. Các bước đọc trace có ý nghĩa

1. Xác nhận **loại Throwable là `StackOverflowError`**, không nhầm với OOME hay recursion-specific domain exception.
2. Tìm **khối frame lặp lại** gần phần sâu của trace. Có thể chỉ một method hoặc chuỗi nhiều method.
3. Tìm vị trí **base case** và điều kiện khiến input không bao giờ đạt tới nó.
4. Kiểm tra tham số qua các lần gọi: số có giảm đúng? offset có tiến? node đã được đánh dấu visited chưa?
5. Với dữ liệu có chu trình, tái hiện từ một graph nhỏ nhất.
6. Với đầu vào hợp lệ nhưng quá sâu, đo độ sâu của dữ liệu và lựa chọn thuật toán lặp.
7. Nếu tái hiện chỉ trong môi trường triển khai, xem cấu hình stack và khác biệt ở thread pool, JDK, architecture, tối ưu hóa.
8. Nếu code đã có ràng buộc input, xác minh nó được áp dụng **trước** khi recursion chạy.

### 7.2. Vì sao trace có thể không hiển thị toàn bộ depth?

Cách in `Throwable#printStackTrace()` và dữ liệu stack trace khả dụng có thể bị ảnh hưởng bởi VM và cấu hình. HotSpot có tùy chọn như `-XX:MaxJavaStackTraceDepth` ảnh hưởng độ dài trace lưu/hiển thị; công cụ logging hoặc APM cũng có thể rút gọn.

**Không dùng số frame in ra bằng `printStackTrace` như thước đo chính xác, portable của độ sâu đệ quy.** Trace được dùng trước hết để tìm **đường gọi** và vùng code có lỗi.

## 8. `-Xss`: kích thước stack thread và những điều không được suy diễn

Nhiều JVM cho phép điều chỉnh kích thước stack cho Java thread qua tùy chọn:

```bash
java -Xss512k MyProgram
java -Xss1m MyProgram
```

Đây là các **lệnh minh họa tùy cấu hình**, giả định `MyProgram.class` đã biên dịch. JVM có thể áp giới hạn tối thiểu/tối đa; tên tùy chọn và cách hiệu lực phụ thuộc VM. Kiểm tra tài liệu và phản hồi của chính JVM đang dùng.

### 8.1. Tăng `-Xss` có giải quyết lỗi không?

**Không phải giải pháp tổng quát.** Nếu recursion **vô hạn**, tăng stack chỉ hoãn thời điểm tràn. Nếu recursion **hữu hạn, có giới hạn đã biết**, điều chỉnh kích thước stack đôi khi cho phép workload hợp lệ chạy — nhưng cần xem chi phí tài nguyên, số thread và rủi ro ở môi trường khác.

Một stack lớn hơn trên mỗi thread có thể làm tăng tổng nhu cầu memory/virtual address space của ứng dụng có hàng nghìn thread. Không tự nâng lên giá trị rất lớn khi chưa phân tích thuật toán.

### 8.2. Số lần gọi tối đa có cố định không?

Không. Ngay cả với cùng `-Xss`:

- Method nhiều biến local, biểu thức và khung lớn có thể dùng stack khác method nhỏ.
- Biên dịch JIT, inline và cách VM tổ chức frame thay đổi theo implementation và trạng thái warm-up.
- Các phiên bản JDK, hệ điều hành và CPU architecture có thể khác.
- Độ sâu hiện có tại lúc recursion bắt đầu (ví dụ từ framework) cũng khác nhau.
- Các handler/loggers có thể cần stack để hoạt động.

Vì vậy không nên ghi “JDK 17 cho đúng 1.024 lần đệ quy” như một quy tắc.

### 8.3. Thread khác nhau có stack riêng

`main` chạy trên một Java thread; request worker thread, scheduled thread hoặc những thread tự tạo có stack riêng. Một quy trình chạy được trong `main` không tự bảo đảm cùng depth sẽ chạy trong một thread có cấu hình stack nhỏ hơn. Ngược lại, một `StackOverflowError` của một thread cũng không tự là bằng chứng heap toàn JVM đã hết.

## 9. Tail recursion có tránh được `StackOverflowError` trong Java không?

*Tail recursion* là trường hợp recursive call nằm ở vị trí cuối theo logic của method.

Ví dụ:

```java
static long sumTail(long n, long acc) {
    if (n == 0) {
        return acc;
    }
    return sumTail(n - 1, acc + n);
}
```

Đây là method minh họa cho n không âm và không tràn `long`. Nó có dạng tail-recursive, nhưng **Java không bảo đảm tail-call optimization (TCO)** theo hợp đồng ngôn ngữ/JVM thông thường. Không được giả định JVM sẽ luôn tái sử dụng frame và cho phép depth vô hạn.

So sánh:

```java
static long sumNonTail(long n) {
    if (n == 0) {
        return 0L;
    }
    return n + sumNonTail(n - 1);
}
```

Đây là non-tail recursion vì sau lời gọi con còn phép cộng. Cả hai cách đều cần base case và progress; cả hai có thể dùng nhiều stack nếu depth lớn.

**Quy tắc thực hành:** Nếu cần độ sâu rất lớn và ổn định trên các JVM Java thông dụng, chuyển thành **vòng lặp** hoặc **stack/queue do chương trình quản lý** thay vì trông chờ tối ưu tail recursion.

## 10. Java 17 — ví dụ đệ quy và vòng lặp tương đương

Tạo **`RecursiveVsIterativeDemo.java`**:

```java
public class RecursiveVsIterativeDemo {
    static int sumRecursive(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n âm");
        }
        if (n == 0) {
            return 0;
        }
        return Math.addExact(n, sumRecursive(n - 1));
    }

    static int sumIterative(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n âm");
        }
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = Math.addExact(sum, i);
        }
        return sum;
    }

    public static void main(String[] args) {
        int n = 5;
        System.out.println("Recursive: " + sumRecursive(n));
        System.out.println("Iterative: " + sumIterative(n));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 RecursiveVsIterativeDemo.java
java RecursiveVsIterativeDemo
```

Stdout:

```text
Recursive: 15
Iterative: 15
```

Giải thích:

- Hai method cùng cho kết quả `1+2+3+4+5 = 15`.
- Recursion giữ các lời gọi chờ nhau, nên stack depth tăng theo n trong mô hình đánh giá thông thường.
- Vòng lặp giữ trạng thái `sum` và `i` trong **một lần gọi method**, không tạo một lời gọi mới cho mỗi số.
- `Math.addExact` xử lý overflow số nguyên bằng `ArithmeticException`, không phải StackOverflowError.
- Khi n đủ lớn, tổng có thể tràn int; ví dụ cố chọn n nhỏ để đầu ra luôn xác định. Nếu muốn hỗ trợ số lớn, có thể dùng công thức với kiểm tra overflow hoặc `BigInteger`.
- Không có nghĩa vòng lặp luôn tối ưu nhất cho mọi recursion: duyệt cây/graph đôi khi diễn đạt tự nhiên bằng recursion nếu depth nhỏ và được kiểm soát.

## 11. Đệ quy gián tiếp trong các getter, listener và `toString()`

Một vài lỗi thực tế không có phương thức tên “recurse”:

```java
class A {
    B other;
    @Override public String toString() {
        return "A(" + other + ")";
    }
}

class B {
    A other;
    @Override public String toString() {
        return "B(" + other + ")";
    }
}
```

Đây là **khai báo minh họa**, không phải chương trình runnable độc lập. Nếu `a.other=b` và `b.other=a`, gọi `a.toString()` có thể lặp qua `A.toString → B.toString → A.toString` cho đến khi tràn stack.

Các tình huống tương tự:

- `equals` hoặc `hashCode` tự gọi vòng qua quan hệ object hai chiều.
- Getter của A gọi getter B, getter B gọi lại A.
- Event listener A kích hoạt B, B gọi lại A vì thiếu flag/reentrancy guard.
- Serializer duyệt graph object có chu trình nhưng không đánh dấu identity đã thăm.
- Traversal cây mà dữ liệu thực tế đã chứa một back-reference tới parent.
- Recursive parser không tiến index khi gặp một token đặc biệt.

**Đừng sửa bằng cách bắt StackOverflowError trong `toString()`.** Hãy giảm độ sâu, không đi vào back-reference, giới hạn output hoặc dùng một context đã thăm.

## 12. Chu trình trong object graph: cần đánh dấu theo identity

Một graph có thể có **chu trình** mà hoàn toàn hợp lệ về mặt dữ liệu. Nếu thuật toán duyệt không ghi nhận đã thăm, nó có thể lặp vô hạn hoặc xử lý lặp cùng node.

Ví dụ:

```text
Node A → Node B → Node C → Node A
```

Với graph tổng quát, dùng một `Set<Node> visited`. Nếu semantics là “đã gặp cùng **đối tượng** chưa?”, Java nên đánh dấu bằng **object identity**, không phải luôn dùng `equals`/`hashCode` có thể tự đệ quy hoặc so sánh theo nội dung.

Một cách tạo identity set:

```java
Set<Node> visited = Collections.newSetFromMap(
        new IdentityHashMap<Node, Boolean>());
```

Mảnh code này cần import `Set`, `Collections`, `IdentityHashMap` và định nghĩa `Node`. `IdentityHashMap` so khóa theo `==` thay vì `equals`.

### 12.1. Java 17 — ví dụ duyệt graph có chu trình an toàn

Tạo **`CyclicGraphTraversalDemo.java`**:

```java
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class CyclicGraphTraversalDemo {
    static final class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    static int countUnique(Node node, Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return 0;
        }
        return 1 + countUnique(node.next, visited);
    }

    public static void main(String[] args) {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");

        a.next = b;
        b.next = c;
        c.next = a;

        Set<Node> visited = Collections.newSetFromMap(
                new IdentityHashMap<Node, Boolean>());

        int count = countUnique(a, visited);
        System.out.println("Unique nodes: " + count);
        System.out.println("Visited: " + visited.size());
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 CyclicGraphTraversalDemo.java
java CyclicGraphTraversalDemo
```

Stdout:

```text
Unique nodes: 3
Visited: 3
```

Từng bước:

1. A được thêm vào `visited`; đệ quy tới B.
2. B được đánh dấu; đệ quy tới C.
3. C được đánh dấu; bước kế quay về A.
4. `visited.add(a)` trả `false` vì đó là **cùng object A**, nên base case thứ hai trả 0; chu trình kết thúc.
5. Hàm đếm ba node riêng biệt.

**Giới hạn quan trọng:** `visited` xử lý **chu trình** nhưng **không tự giới hạn độ sâu**. Nếu graph là chuỗi 200.000 node khác nhau không có cycle, `countUnique` vẫn có thể dùng quá nhiều stack. Khi dữ liệu có thể rất sâu, cần kết hợp depth limit hoặc duyệt **lặp**.

## 13. Java 17 — duyệt lặp bằng `Deque` để tránh call-stack sâu

Thay vì một lời gọi recursive cho mỗi node, dùng cấu trúc dữ liệu trên heap đại diện cho các node sẽ xử lý.

Tạo **`IterativeGraphTraversalDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

public class IterativeGraphTraversalDemo {
    static final class Node {
        Node next;
    }

    static int countUnique(Node start) {
        if (start == null) {
            return 0;
        }

        Set<Node> visited = Collections.newSetFromMap(
                new IdentityHashMap<Node, Boolean>());
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(start);

        while (!pending.isEmpty()) {
            Node current = pending.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.next != null) {
                pending.push(current.next);
            }
        }
        return visited.size();
    }

    public static void main(String[] args) {
        Node first = new Node();
        Node current = first;
        for (int i = 1; i < 50_000; i++) {
            current.next = new Node();
            current = current.next;
        }

        current.next = first; // Chu trình quay về đầu.
        System.out.println("Unique nodes: " + countUnique(first));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 IterativeGraphTraversalDemo.java
java IterativeGraphTraversalDemo
```

Stdout:

```text
Unique nodes: 50000
```

Ý nghĩa:

- Vòng `for` tạo 50.000 đối tượng `Node` và nối thành danh sách; cuối cùng tạo chu trình về `first`.
- `Deque` chứa các node cần xử lý và `visited` ngăn lặp qua chu trình.
- Hàm `countUnique` không gọi lại chính nó. Độ sâu call stack của **chính thuật toán traversal** không tăng tuyến tính với số node.
- Đổi lại, các object Node, `Deque` và `visited` cần bộ nhớ **heap**. Khi dữ liệu cực lớn, vẫn cần giới hạn bộ nhớ hoặc thuật toán phù hợp; tránh stack overflow không có nghĩa không thể gặp memory pressure.
- Với graph tổng quát có nhiều cạnh, cấu trúc pending có thể chứa nhiều phần tử. Phải đánh giá cả time complexity và memory complexity, không chỉ recursion depth.

### 13.1. Vì sao dùng `ArrayDeque` thay vì `Stack`?

`ArrayDeque` là lựa chọn hiện đại phổ biến khi cần ngữ nghĩa LIFO/FIFO trong Java. Có thể dùng `push`/`pop` làm stack thủ công. Nó không cho phần tử `null`, vì vậy ví dụ chỉ `push` node khác null.

Không nhầm **Java call stack** với **`Deque` object**: `Deque` là một cấu trúc dữ liệu do ứng dụng quản lý, thường trên heap, không tự tạo JVM frame cho mỗi phần tử.

## 14. Độ sâu an toàn cần được kiểm soát tại biên đầu vào

Ngay cả khi đầu vào không có cycle, một API nhận JSON/XML/cây biểu thức lồng nhau từ bên ngoài có thể gặp dữ liệu cực sâu. Nếu dùng recursion, có thể:

- Từ chối đầu vào vượt **độ sâu tối đa** được quy định.
- Chuyển sang parser/traversal dạng lặp khi dữ liệu cần hỗ trợ depth lớn.
- Giới hạn cả kích thước, số node, số cạnh và thời gian xử lý.
- Dùng cấu hình parser/framework có khả năng giới hạn nesting nếu thư viện hỗ trợ.
- Tách lỗi validation thành **exception nghiệp vụ phù hợp**, không chờ VM ném StackOverflowError.

Ví dụ: “Tối đa 64 cấp lồng nhau” là một **hợp đồng ứng dụng** được chọn có chủ đích, khác hoàn toàn “stack của JVM lúc này may mắn chịu được 64 cấp”.

### 14.1. Java 17 — giới hạn depth bằng lỗi có ý nghĩa

Tạo **`DepthLimitDemo.java`**:

```java
public class DepthLimitDemo {
    static final int MAX_DEPTH = 4;

    static int visit(int depth, int remaining) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Vượt giới hạn độ sâu " + MAX_DEPTH);
        }
        if (remaining == 0) {
            return depth;
        }
        return visit(depth + 1, remaining - 1);
    }

    public static void main(String[] args) {
        System.out.println("Safe result: " + visit(0, 3));
        try {
            visit(0, 5);
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 DepthLimitDemo.java
java DepthLimitDemo
```

Stdout:

```text
Safe result: 3
Rejected: Vượt giới hạn độ sâu 4
```

Diễn giải:

- `visit(0,3)` gọi với depth 0,1,2,3 rồi base case trả 3.
- `visit(0,5)` tiến đến depth 5; điều kiện `depth > 4` chủ động từ chối.
- Đây là **IllegalArgumentException do ứng dụng chọn**, không phải `StackOverflowError`.
- Giới hạn depth cần tính rõ từ 0 hay từ 1, và điều kiện xét `>` hay `>=` để không gặp off-by-one.
- Nếu sử dụng với dữ liệu không tin cậy, ngoài depth còn cần kiểm tra `remaining >= 0` và các invariant khác. Ví dụ cố dùng input hợp lệ về số bước để tập trung vào depth.
- Giá trị 4 trong chương trình chỉ là giới hạn minh họa, **không** khẳng định 4 là giới hạn JVM hay giới hạn an toàn chung.

### 14.2. Giới hạn depth và visited set giải quyết hai vấn đề khác nhau

| Cơ chế | Chống được điều gì? | Không tự giải quyết |
| --- | --- | --- |
| Base case + progress | Đệ quy không có đường dừng trên input hợp lệ | Dữ liệu có cycle bất ngờ, độ sâu cực lớn |
| Visited set | Chu trình/đi lại node đã gặp | Chuỗi node mới rất sâu, memory dùng cho set |
| Depth limit | Đệ quy quá sâu | Tính đúng của kết quả khi dữ liệu bị từ chối |
| Duyệt bằng `Deque` | Call stack quá sâu theo số node | Heap lớn, thuật toán có thể vô hạn nếu không có visited |
| Input validation | Trường hợp sai hợp đồng từ đầu | Bug ở recursion nội bộ nếu vẫn triển khai sai |
| `-Xss` lớn hơn | Có thể cho phép một số depth hữu hạn chạy được | Đệ quy vô hạn, side effect, cycle |

## 15. Đệ quy trong cây: độ sâu khác số node

Một cây cân bằng có thể có nhiều node nhưng độ sâu nhỏ. Một cây lệch hoàn toàn có thể có n node và độ sâu khoảng n.

```text
Cây cân bằng:                Cây lệch:
        A                     A
      /   \                    \
     B     C                    B
    / \   / \                    \
   D   E F   G                    C
                                   \
                                    D
```

Nếu recursion giữ một frame trên mỗi tầng:

- Cây cân bằng có độ sâu `O(log n)` trong trường hợp cấu trúc cân bằng lý tưởng.
- Cây lệch có depth `O(n)` và có thể gây tràn stack khi n lớn.
- Tổng số node nhiều không đủ để kết luận stack chắc tràn; phải xem **độ sâu đường đi dài nhất** và cách traversal gọi method.
- Thuật toán có thể tính toán qua nhiều nhánh nhưng **không giữ mọi nhánh trên call stack đồng thời** nếu duyệt tuần tự; tuy nhiên một nhánh rất sâu vẫn nguy hiểm.

### 15.1. Java 17 — minh họa trace vào/ra đệ quy

Tạo **`RecursionTraceDemo.java`**:

```java
public class RecursionTraceDemo {
    static void trace(int depth, int remaining) {
        System.out.println("Enter " + depth);
        if (remaining > 0) {
            trace(depth + 1, remaining - 1);
        }
        System.out.println("Exit " + depth);
    }

    public static void main(String[] args) {
        trace(0, 2);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 RecursionTraceDemo.java
java RecursionTraceDemo
```

Stdout:

```text
Enter 0
Enter 1
Enter 2
Exit 2
Exit 1
Exit 0
```

Với trace này, ba lời gọi `trace` tồn tại lồng nhau trước khi bắt đầu in `Exit`. Cách in như vậy giúp hình dung thứ tự **push** và **unwind** của call stack. Trong recursion không dừng, quá trình đi sâu không bao giờ tới các lệnh `Exit` theo đường bình thường.

## 16. Tại sao không dùng `try/catch(StackOverflowError)` làm điều kiện dừng?

Đây là một anti-pattern:

```java
// CỐ Ý SAI: không dùng stack exhaustion làm thuật toán.
static int count(Node node) {
    try {
        return 1 + count(node.next);
    } catch (StackOverflowError ignored) {
        return 0;
    }
}
```

Đoạn này **minh họa**, không phải chương trình độc lập. Vấn đề:

1. Với danh sách có chu trình, không có cách đếm đúng “số node phân biệt” bằng cách chờ tràn stack.
2. Với danh sách hữu hạn, depth giới hạn bởi tài nguyên VM, kết quả sẽ **phụ thuộc máy và thread**, không phải dữ liệu.
3. Khi gần cạn stack, xử lý error/cleanup phức tạp có thể gặp sự cố.
4. Catch có thể che bug, làm ứng dụng trả kết quả sai hoặc không nhất quán.
5. Trong recursion mà catch ở **mỗi frame**, khi unwind có thể xuất hiện nhiều điểm bắt với những phép tính tiếp theo, càng khó suy luận.
6. Stack exhaustion không đảm bảo tính nguyên tử; trước khi lỗi có thể đã có side effect.
7. Tăng `-Xss` làm đầu ra thuật toán thay đổi nếu nó dựa vào vị trí tràn.

**Giải pháp:** dừng theo dữ liệu/cấu trúc, dùng visited set, giới hạn depth, vòng lặp hoặc stack chủ động; không lấy loại Error làm “giá trị kết thúc”.

### 16.1. Có được bắt Error ở ranh giới kỹ thuật không?

Một số hệ thống hạ tầng có thể bắt `Throwable` trong **ranh giới rất hẹp** để ghi nhận/sandbox failure rồi tái ném hoặc kết thúc tác vụ. Nhưng đây là chính sách nâng cao, và không thể khẳng định VM/thread luôn ở trạng thái đủ an toàn để tiếp tục mọi việc.

Code ứng dụng thông thường nên:

- **Không** bắt `Error` rộng rồi trả response “thành công”.
- **Không** retry vô hạn cùng recursion trong handler.
- Giữ logic cleanup đơn giản, có kiểm soát, và không làm mất thông tin lỗi gốc.
- Xem `StackOverflowError` như một tín hiệu lỗi nghiêm trọng cần **sửa code hoặc giới hạn dữ liệu**.

## 17. Lỗi trong quá trình khởi tạo và `StackOverflowError`

Chủ đề này nằm trong nhóm “Error và khởi tạo”, nên cần phân biệt **đệ quy gây StackOverflowError** với **lỗi khởi tạo class**.

Trong Java, static initializer và việc khởi tạo static fields có thể chạy khi class được dùng lần đầu. Nếu logic khởi tạo vô tình kích hoạt recursion sâu (ví dụ gọi một method đệ quy không dừng), stack của thread cũng có thể bị tràn.

Tuy nhiên:

- `StackOverflowError` là lỗi liên quan đến stack, dù nó xảy ra **trong static initializer**, constructor hay method thường.
- Các quy tắc **class initialization failure** có thể tạo các biểu hiện khác như `ExceptionInInitializerError` hoặc `NoClassDefFoundError` ở những tình huống phù hợp; không được nói mọi lỗi trong static initializer đều chắc chắn bị bọc thành một lớp duy nhất.
- Một `Error` được ném trong lúc class initialization có những quy tắc lan truyền khác với exception thông thường từ initializer.
- Sau khi class khởi tạo thất bại, trạng thái class đối với các lần sử dụng tiếp theo có thể bị đánh dấu lỗi, nên việc retry trong cùng JVM không tự sửa được nguyên nhân.
- Constructor instance tự gọi lại theo **các method thông thường** có thể gây recursion; nhưng lời gọi constructor `this(...)`/`super(...)` có quy tắc compile-time riêng, và không nên đánh đồng với vô hạn method recursion.

**Điểm cần học:** xác định vị trí lỗi trên stack và **tầng phát sinh**. Nếu Error xuất hiện khi class được khởi tạo, ngoài recursion còn phải điều tra chuỗi khởi tạo và các giới hạn của JVM.

### 17.1. Đừng dựa vào retry class initialization thất bại

Nếu một static initializer đã thất bại, code dùng class sau đó không được giả định có thể “thử lại” khởi tạo như một method bình thường. Khi có lỗi do tài nguyên/đệ quy ở giai đoạn khởi tạo, thường cần sửa thiết kế khởi tạo và xử lý vòng đời class/tiến trình, chứ không chỉ bao `try/catch` và gọi lại.

### 17.2. Cẩn trọng với factory gọi qua lại

Một chuỗi như `ServiceHolder.get() → ServiceFactory.create() → ServiceHolder.get()` có thể tạo recursion gián tiếp ngay ở bước khởi tạo dependency. Đặc biệt chú ý vòng phụ thuộc giữa các constructor/factory hoặc lazy getters; việc dùng lazy initialization không tự loại bỏ vòng phụ thuộc.

## 18. Đệ quy và `equals`, `hashCode`, `toString`

Nhiều thư viện hoặc công cụ logging tự gọi `toString`; collection như `HashMap` có thể gọi `hashCode` và `equals`. Nếu hai object có liên kết hai chiều và các method ấy duyệt toàn bộ graph không có visited check, sự cố có thể bộc lộ ở chỗ **log một object**, không phải ở đoạn người viết nghĩ là đang dùng recursion.

Ví dụ:

```java
// Phản thiết kế khi graph có back-reference.
@Override
public String toString() {
    return "Parent{child=" + child + "}";
}
```

Nếu `child.toString()` lại in `parent`, đường gọi quay vòng. Cách sửa có thể là chỉ in ID, tên trường an toàn, hoặc giới hạn độ sâu biểu diễn.

Tương tự:

- `equals` không nên so sánh vô hạn qua back-reference nếu graph có cycle.
- `hashCode` nên dựa trên trường có semantics phù hợp, tránh đi hết graph hai chiều không có giới hạn.
- Auto-generated methods từ công cụ có thể không phù hợp với graph cyclic, dù nhìn như ít code.
- Java `record` có `toString/equals/hashCode` dựa trên components; nếu component graph liên kết theo vòng, vẫn phải xét recursion gián tiếp.
- Một `StackOverflowError` trong log formatter không chứng minh logger hỏng; nguồn có thể là `toString()` của object được truyền vào logger.

## 19. Depth guard có thể đặt ở đâu?

Các tầng phù hợp:

- **Parser:** giới hạn nesting level của JSON/XML/expression.
- **Graph traversal:** giới hạn depth hoặc dùng `visited`/iterative traversal.
- **Business rule:** giới hạn số cấp parent/child trong cấu trúc domain.
- **API boundary:** từ chối input quá sâu hoặc quá lớn trước khi chuyển xuống service.
- **Recursive algorithm:** chứng minh một measure giảm dần và có giới hạn.
- **Library integration:** đọc các giới hạn/cấu hình của thư viện và tránh tắt những biện pháp an toàn mà không có lý do.

Hãy chọn **cách đếm rõ ràng**. Ví dụ root có depth 0 hay depth 1? Depth 64 nghĩa là 64 edges hay 64 nodes? Một hợp đồng được ghi rõ giúp tránh lỗ hổng off-by-one.

### 19.1. Nếu chiều sâu không biết trước?

Khi người dùng có thể gửi dữ liệu rất sâu, không nên chỉ nâng một constant depth guard lên giá trị lớn vô hạn. Hãy:

1. Đánh giá liệu yêu cầu phải xử lý dữ liệu sâu đến đâu.
2. Nếu bắt buộc, chọn thuật toán lặp hoặc cấu trúc parser không dùng call stack tỷ lệ theo nesting.
3. Giới hạn số node/cạnh, thời gian chạy và bộ nhớ.
4. Nếu cần trả lỗi từ chối, dùng loại kết quả/exception nghiệp vụ có mã lỗi phù hợp.
5. Giữ monitoring cho các input biên nhưng tránh in/dump nguyên dữ liệu nhạy cảm hoặc quá lớn.

## 20. Java 17 — mutual recursion hữu hạn có điểm dừng

Không phải mọi cuộc gọi A ↔ B đều sai. Nếu có một **measure giảm** và base case, mutual recursion có thể hợp lệ.

Tạo **`MutualRecursionDemo.java`**:

```java
public class MutualRecursionDemo {
    static boolean isEven(int n) {
        if (n == 0) {
            return true;
        }
        return isOdd(n - 1);
    }

    static boolean isOdd(int n) {
        if (n == 0) {
            return false;
        }
        return isEven(n - 1);
    }

    public static void main(String[] args) {
        System.out.println("Even 4: " + isEven(4));
        System.out.println("Odd 5: " + isOdd(5));
        System.out.println("Even 0: " + isEven(0));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MutualRecursionDemo.java
java MutualRecursionDemo
```

Stdout:

```text
Even 4: true
Odd 5: true
Even 0: true
```

Chú ý:

- Ví dụ chỉ dùng **n không âm**; nếu n âm, measure giảm mãi xa 0 và có thể gây stack overflow.
- Trong ứng dụng thật, có thể kiểm tra `n >= 0` ở boundary rồi mới gọi method.
- Lời gọi qua lại không phải vấn đề tự thân; vấn đề là **đường gọi có khả năng dừng và depth có được kiểm soát không**.
- Nếu đầu vào là số nguyên rất lớn, dù recursion dừng về mặt toán học, vẫn có nguy cơ tràn stack do độ sâu quá lớn. Với bài toán chẵn/lẻ, `(n & 1) == 0` đơn giản và không cần recursion.

## 21. Duyệt cây/graph: cách lựa chọn theo dữ liệu

| Dữ liệu và yêu cầu | Lựa chọn hợp lý | Rủi ro còn lại |
| --- | --- | --- |
| Cây nhỏ, depth bị giới hạn | Đệ quy với base case rõ | Input vượt giới hạn nếu validation thiếu |
| Cây có thể rất lệch/sâu | Vòng lặp hoặc `Deque`/`Queue` | Heap dùng cho cấu trúc trung gian |
| Graph có cycle | Visited set, identity khi cần | Bộ nhớ và concurrency |
| Graph có cycle và rất sâu | Iterative + visited | Thời gian, số lượng node/cạnh lớn |
| Chỉ tính tổng dãy số | Vòng lặp/công thức với kiểm tra overflow | Tràn số độc lập với stack |
| JSON/XML sâu đến từ bên ngoài | Parser có depth policy; từ chối hoặc duyệt lặp | Chi phí parse/input lớn |
| Số tầng đã cố định, rất nhỏ | Recursive method đơn giản | Không tự tăng giới hạn nếu input mở rộng |
| Callback/event có khả năng gọi lại chính nó | Reentrancy guard hoặc queue công việc | Deadlock/ordering nếu guard thiết kế sai |

**Đừng đổi đệ quy sang lặp một cách máy móc rồi bỏ hết kiểm tra cycle.** Nếu input graph có chu trình, vòng lặp không có visited set vẫn có thể chạy vô hạn, chỉ là không tràn call stack theo cùng cách.

## 22. Kiểm thử đệ quy và đường lỗi

Các test cần bao phủ:

1. **Base case:** input rỗng/0/null (nếu hợp đồng cho phép).
2. **Một bước:** input rất nhỏ, kiểm tra progress.
3. **Đường bình thường:** giá trị kỳ vọng với depth thấp.
4. **Sai tiền điều kiện:** số âm, depth không hợp lệ.
5. **Cạnh giới hạn:** depth tối đa, ngay trên giới hạn.
6. **Cấu trúc có chu trình:** graph nhỏ A → B → A.
7. **Cấu trúc sâu không chu trình:** thuật toán lặp phải xử lý đúng.
8. **Không làm sai trạng thái:** nếu validation từ chối sớm, chưa phát sinh side effect không cần thiết.
9. **Concurrency (nếu có):** object graph có thể bị sửa trong lúc duyệt không?
10. **Thông báo lỗi:** failure rõ nghĩa và không lộ thông tin nhạy cảm.

### 22.1. Ví dụ JUnit 5 để kiểm tra hợp đồng

```java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RecursionContractTest {
    @Test
    void factorialHasWorkingBaseCases() {
        assertEquals(1L, BoundedRecursionDemo.factorial(0));
        assertEquals(120L, BoundedRecursionDemo.factorial(5));
    }

    @Test
    void negativeInputIsRejectedBeforeRecursion() {
        assertThrows(IllegalArgumentException.class,
                () -> BoundedRecursionDemo.factorial(-1));
    }

    @Test
    void depthGuardRejectsExcessiveInput() {
        assertEquals(3, DepthLimitDemo.visit(0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> DepthLimitDemo.visit(0, 5));
    }
}
```

**Đây là file test minh họa cần JUnit Jupiter và hai demo class nằm cùng package**, không phải một chương trình JDK-only độc lập. Test dựa trên **hành vi mong đợi** chứ không cố “chờ JVM tràn stack” để xác minh base case.

### 22.2. Không dùng số depth trước khi `StackOverflowError` làm assert

Số lần gọi chịu ảnh hưởng JVM, thread stack, JIT và kích thước frame. Một test kiểu “assert chắc chắn SOE sau đúng 10.000 lời gọi” sẽ giòn và khó di động. Nếu cần test chủ động gây lỗi VM, nên chạy riêng trong môi trường cô lập, có timeout/giới hạn tài nguyên và coi đó là test chẩn đoán, không phải test đơn vị cơ bản.

## 23. Những nhầm lẫn phổ biến và cách sửa

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`StackOverflowError` là `Exception`.” | Nó thuộc `Error → VirtualMachineError`. |
| “`catch(Exception)` bắt mọi Throwable.” | Không bắt `Error`. |
| “SOE luôn là do thiếu base case.” | Có thể do recursion hữu hạn quá sâu hoặc chu trình gọi phức tạp. |
| “Có base case thì không thể tràn stack.” | Depth hợp lệ vẫn có thể vượt giới hạn VM. |
| “`-Xss` lớn hơn sửa được recursion vô hạn.” | Chỉ hoãn tràn, không tạo điều kiện dừng. |
| “Mọi JVM cho cùng số lần gọi tối đa.” | Phụ thuộc VM, kiến trúc, frame, JIT, thread và cấu hình. |
| “Đệ quy tail-call trong Java luôn tối ưu thành vòng lặp.” | Java không bảo đảm TCO chung. |
| “Không có cycle thì recursion luôn an toàn.” | Đường đi không cycle vẫn có thể rất dài. |
| “Dùng visited set sẽ hết nguy cơ SOE.” | Giải quyết cycle, không tự giải quyết depth lớn. |
| “Đổi sang vòng lặp thì có thể bỏ visited.” | Graph có cycle vẫn có thể chạy mãi. |
| “Vòng lặp không thể hết bộ nhớ.” | `Deque`/`Set` và dữ liệu trên heap vẫn dùng memory. |
| “Cứ catch StackOverflowError rồi trả 0.” | Kết quả sai và có thể khiến trạng thái không đáng tin. |
| “`OutOfMemoryError` với `StackOverflowError` giống nhau.” | Khác tài nguyên và nguyên nhân. |
| “Tràn số int tạo `StackOverflowError`.” | Tràn số là hiện tượng toán học khác. |
| “Chỉ recuse() mới có SOE.” | ToString/getter/serializer/equals/callback có thể gọi qua lại. |
| “Lỗi log StackOverflowError luôn do logger.” | Có thể do `toString()` của dữ liệu truyền vào log. |
| “Một trace dài cho biết chính xác depth.” | Trace có thể rút gọn/cấu hình giới hạn. |
| “Chạy được trong main là chạy được mọi thread pool.” | Stack cấu hình giữa các thread/môi trường có thể khác. |
| “Static initializer tràn stack giống mọi lỗi exception khởi tạo.” | Cần xét riêng Error và quy tắc class initialization failure. |
| “Chỉ cần memory heap lớn hơn.” | Heap và call stack giải quyết nhu cầu khác nhau. |
| “Đệ quy không kiểm soát là vấn đề hiệu năng thôi.” | Có thể thành lỗi VM nghiêm trọng và mất tính sẵn sàng. |

## 24. Checklist trước khi dùng đệ quy trong Java

- [ ] Tôi đã nêu **base case** rõ cho mọi loại input hợp lệ chưa?
- [ ] Tôi có một measure chứng minh lời gọi con **tiến gần base case** chưa?
- [ ] Tôi đã kiểm tra input âm, null, rỗng và giá trị rất lớn chưa?
- [ ] Tôi đã đánh giá **độ sâu tối đa** chứ không chỉ số node/tổng công việc chưa?
- [ ] Nếu input do người dùng cung cấp, đã áp giới hạn depth/nodes/size chưa?
- [ ] Nếu graph có thể có cycle, tôi có visited set hoặc cơ chế phát hiện cycle chưa?
- [ ] Nếu cần xét object identity, tôi đã tránh dựa nhầm vào `equals/hashCode` chưa?
- [ ] Với dữ liệu rất sâu, tôi có cân nhắc `Deque`/`Queue` thay recursion chưa?
- [ ] Tôi hiểu `StackOverflowError` là `Error` và `catch(Exception)` không bắt nó chưa?
- [ ] Tôi không dùng `catch(StackOverflowError)` làm điều kiện dừng thuật toán chứ?
- [ ] Tôi không mặc định Java luôn tối ưu tail recursion chưa?
- [ ] Tôi không dùng `-Xss` như giải pháp chính cho recursion vô hạn chưa?
- [ ] Tôi phân biệt `StackOverflowError` với `OutOfMemoryError` và tràn số chưa?
- [ ] Tôi đã xét các đường recursion ẩn qua `toString`, `equals`, getter và serializer chưa?
- [ ] Khi gặp trace, tôi tìm pattern frame lặp và điều kiện dừng thay vì chỉ tăng stack chưa?
- [ ] Tôi đã thử trường hợp cây lệch, graph cycle, depth sát giới hạn chưa?
- [ ] Nếu lỗi xảy ra lúc khởi tạo class, tôi đã phân biệt failure class initialization chưa?
- [ ] Tôi đã đảm bảo lời giải còn đúng khi input/môi trường mở rộng chưa?

## 25. Tổng kết

`StackOverflowError` là một **`VirtualMachineError` unchecked** liên quan đến giới hạn call stack của thread. Đệ quy không có base case, không tiến tới base case, gọi vòng qua nhiều method hoặc duyệt graph có chu trình đều có thể gây lỗi; nhưng **đệ quy hữu hạn quá sâu** cũng có thể tràn stack. Để phòng tránh, cần **điều kiện dừng có thể chứng minh, bước tiến, giới hạn độ sâu**, và chọn **thuật toán lặp với stack/queue chủ động** khi dữ liệu có thể sâu. Visited set xử lý chu trình nhưng không loại bỏ mọi rủi ro depth. `-Xss` có thể thay đổi ngưỡng tài nguyên chứ không chữa recursion vô hạn, còn `catch(StackOverflowError)` không phải phương pháp khôi phục thông thường. Thiết kế thuật toán đúng và giới hạn đầu vào mới là cách bảo vệ chương trình đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-006">LinkageError và các vấn đề tương thích mã đã biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# `LinkageError` và các vấn đề tương thích mã đã biên dịch trong Java

Một chương trình Java có thể **biên dịch thành công** nhưng chỉ khi triển khai hoặc chạy đến một nhánh nhất định mới xuất hiện:

```text
java.lang.NoSuchMethodError
java.lang.NoSuchFieldError
java.lang.AbstractMethodError
java.lang.IncompatibleClassChangeError
java.lang.IllegalAccessError
java.lang.NoClassDefFoundError
java.lang.UnsupportedClassVersionError
```

Các lỗi này không đơn thuần có nghĩa “sai cú pháp Java”. Chúng thường chỉ ra rằng **bytecode được tạo trong một môi trường** đang được JVM **liên kết hoặc thực thi trong môi trường không còn đáp ứng các giả định về lớp, phương thức, trường, quyền truy cập hoặc phiên bản**.

`LinkageError` là một nhánh của `java.lang.Error`. Nó biểu diễn vấn đề trong **liên kết các lớp Java**: khi một lớp đã được biên dịch tham chiếu tới cấu trúc lớp khác, JVM cần tìm, xác minh, phân giải và sử dụng đúng cấu trúc đó tại runtime.

**Điểm cốt lõi:** *source compatibility*, *binary compatibility* và *runtime linkage* là ba khái niệm có liên quan nhưng **không đồng nghĩa**. Thay đổi source hợp lệ chưa chắc tương thích với client `.class` cũ; ngược lại, có thay đổi làm source cần sửa nhưng bytecode cũ vẫn chạy. Cần điều tra **artifact thực sự được chạy**, không chỉ mã nguồn mà IDE đang hiển thị.

## 1. `LinkageError` nằm ở đâu trong hệ ngoại lệ?

Sơ đồ rút gọn:

```text
java.lang.Throwable
├── java.lang.Exception
│   ├── java.lang.RuntimeException
│   └── java.lang.ReflectiveOperationException
│       └── java.lang.ClassNotFoundException
└── java.lang.Error
    └── java.lang.LinkageError
        ├── java.lang.NoClassDefFoundError
        ├── java.lang.ExceptionInInitializerError
        ├── java.lang.ClassFormatError
        │   └── java.lang.UnsupportedClassVersionError
        ├── java.lang.VerifyError
        ├── java.lang.BootstrapMethodError
        ├── java.lang.UnsatisfiedLinkError
        └── java.lang.IncompatibleClassChangeError
            ├── java.lang.NoSuchMethodError
            ├── java.lang.NoSuchFieldError
            ├── java.lang.AbstractMethodError
            ├── java.lang.IllegalAccessError
            └── java.lang.InstantiationError
```

Đây là sơ đồ lựa chọn một số lớp quan trọng, không liệt kê hết hệ phân cấp. Cần đọc quan hệ cha/con thật, đặc biệt: `NoSuchMethodError` và `NoSuchFieldError` **không phải các sibling trực tiếp của `IncompatibleClassChangeError`**; chúng là **subclass** của nó.

| Thuộc tính | `LinkageError` và subclass |
| --- | --- |
| Gốc kế thừa | `Throwable → Error → LinkageError` |
| Checked exception? | **Không**; `Error` là unchecked |
| Lúc phát sinh | Khi JVM nạp/kiểm tra/liên kết/khởi tạo hoặc thực thi tham chiếu liên quan, tùy loại |
| Có được compiler buộc `catch`/`throws`? | Không |
| Có thể xảy ra sau khi ứng dụng đã chạy thành công một thời gian? | Có, do nhiều tham chiếu được phân giải hoặc lớp được khởi tạo theo nhu cầu |
| Nên mặc định bắt và bỏ qua? | Không; thường là vấn đề build, runtime, loader, compatibility hoặc class initialization cần sửa |

Đừng nhầm `LinkageError` với `ClassNotFoundException`: lỗi sau là **checked exception** thuộc nhánh `ReflectiveOperationException`, thường từ API tìm lớp theo tên. `NoClassDefFoundError` lại thuộc nhánh `LinkageError`. Hai loại có thể liên quan cùng một class bị thiếu nhưng phát sinh qua cơ chế khác nhau.

## 2. Loading, linking, initialization: không phải một bước duy nhất

Để hiểu các lỗi linkage, nên biết ba chặng lớn trong vòng đời lớp:

1. **Loading:** một class loader tìm hoặc định nghĩa biểu diễn của lớp theo tên.
2. **Linking:** gồm **verification**, **preparation** và **resolution**. JVM kiểm tra class file, chuẩn bị cấu trúc lớp và phân giải symbolic references theo quy tắc JVM.
3. **Initialization:** thực thi những static initializer và khởi tạo static field tương ứng khi lớp được chủ động sử dụng.

`LinkageError` có thể phản ánh nhiều điểm thất bại khác nhau trong quá trình này. Ví dụ:

- Không tìm được lớp cần thiết khi liên kết/sử dụng: có thể `NoClassDefFoundError`.
- Bytecode không hợp lệ hoặc không tương thích định dạng: có thể `ClassFormatError`/`VerifyError`.
- Runtime class còn tồn tại nhưng method ký hiệu trong bytecode cũ không còn: `NoSuchMethodError`.
- Lớp có static initialization thất bại: lần đầu có thể `ExceptionInInitializerError`, lần sử dụng tiếp có thể `NoClassDefFoundError`.
- Runtime class file được build cho Java mới hơn JVM đang chạy: `UnsupportedClassVersionError`.

### Resolution không nhất thiết xảy ra tất cả ở startup

JVM được phép phân giải nhiều symbolic references khi cần. Một nhánh code chưa được gọi có thể chưa kích hoạt lỗi. Bởi vậy:

- App khởi động và health check trả OK **không chứng minh** mọi binary dependency đã tương thích.
- Một endpoint hiếm dùng có thể lần đầu ném `NoSuchMethodError` khi có request sau nhiều ngày.
- Chạy unit test trên classpath khác production có thể không lộ được lỗi.
- Phân tích phải xét **phép gọi làm lỗi xuất hiện** và runtime artifact thực, không chỉ timestamp khởi động.

## 3. Source compatibility khác binary compatibility

**Source compatibility** hỏi: nếu biên dịch lại mã nguồn client cũ với thư viện mới, mã còn được compiler chấp nhận không?

**Binary compatibility** hỏi: nếu giữ nguyên file `Client.class` đã biên dịch với thư viện cũ, rồi thay bằng **binary thư viện mới**, bytecode client cũ có tiếp tục liên kết/sử dụng được theo các bảo đảm tương thích không?

**Behavioral compatibility** hỏi thêm: dù việc liên kết vẫn thành công, chương trình có còn thực hiện đúng ý nghĩa đã cam kết không?

Ba trục này không trùng nhau.

Ví dụ:

```java
// Thư viện phiên bản 1
public class PriceApi {
    public static int calculate() {
        return 100;
    }
}
```

Client gọi `PriceApi.calculate()`. Nếu thư viện phiên bản 2 **xóa phương thức** ấy hoặc thay chữ ký nhị phân theo cách không còn phương thức tương ứng, client `.class` cũ có thể ném `NoSuchMethodError` khi gọi.

Ngược lại, đổi **nội dung thân phương thức** từ trả `100` sang trả `120` mà vẫn giữ binary signature có thể không gây linkage error, nhưng vẫn phá **hành vi** nếu client dựa vào giá cũ. Không thể dùng việc “không có `LinkageError`” làm bằng chứng ứng dụng đúng nghiệp vụ.

### Symbolic reference là gì?

Java bytecode lưu tham chiếu theo **tên lớp, tên thành viên và descriptor của kiểu/chữ ký**. Khi thực thi một thao tác như gọi phương thức, JVM phân giải tham chiếu đó so với class thực trong runtime.

Điều này quan trọng khi client được biên dịch với một version của `LibraryApi` còn runtime thực tế chứa version khác: **source đang được mở trong IDE không quyết định bytecode runtime được phân giải ra sao**.

## 4. `NoSuchMethodError`: client cũ gọi phương thức không còn khớp

`NoSuchMethodError` xảy ra khi JVM không tìm được phương thức mà một tham chiếu nhị phân cần, sau các quy tắc phân giải liên quan.

Các thay đổi có thể dẫn tới lỗi:

- Xóa hoặc đổi tên method đã được client dùng.
- Đổi danh sách tham số, ví dụ `find(String)` thành `find(long)`.
- Đổi kiểu trả về trong binary descriptor mà không còn method tương ứng cho client cũ.
- Chạy client với một thư viện cũ hơn thư viện dùng khi compile (method mới không tồn tại).
- Runtime nạp **bản JAR khác** do classpath trùng dependency/transitive version conflict.

Ví dụ:

```java
// Client cũ đã biên dịch với API:
String value = LibraryApi.greet();
```

Nếu runtime `LibraryApi` không còn cung cấp `greet():String` như client kỳ vọng, có thể ném `NoSuchMethodError` tại điểm gọi.

**Đừng nhầm `NoSuchMethodError` với `NoSuchMethodException`**:

| `NoSuchMethodError` | `NoSuchMethodException` |
| --- | --- |
| Subclass của `IncompatibleClassChangeError` | Checked subtype của `ReflectiveOperationException` |
| Lỗi resolution/linkage của tham chiếu binary | Một lời gọi reflection không tìm được method theo truy vấn |
| Không buộc catch | Phải bắt/khai báo ở API reflection thích hợp |
| Thường cần sửa version/binary/deployment | Có thể là kết quả kiểm tra method tùy chọn theo reflection |

Chúng đều có từ “NoSuchMethod” nhưng **khác cơ chế và chính sách xử lý**.

## 5. Thí nghiệm Java 17+ hoàn chỉnh: tái tạo `NoSuchMethodError` bằng cách thay binary

Đây là thí nghiệm có kiểm soát. Bạn sẽ **biên dịch client cùng phiên bản thư viện v1**, rồi **chỉ thay class của thư viện bằng v2** mà **không biên dịch lại client**.

Tạo cấu trúc tệp trong một thư mục thí nghiệm riêng:

```text
binary-lab/
├── v1/
│   └── LibraryApi.java
├── v2/
│   └── LibraryApi.java
├── BinaryClient.java
└── build/            # thư mục đầu ra, có thể tạo bằng mkdir
```

**Tệp `v1/LibraryApi.java`:**

```java
public class LibraryApi {
    public static String greet() {
        return "hello-v1";
    }
}
```

**Tệp `v2/LibraryApi.java`:**

```java
public class LibraryApi {
    public static String salute() {
        return "hello-v2";
    }
}
```

Hai tệp có **cùng tên class binary** nhưng API khác nhau. Chúng không được biên dịch đồng thời làm hai bản định nghĩa `LibraryApi` trong cùng output.

**Tệp `BinaryClient.java`:**

```java
public class BinaryClient {
    public static void main(String[] args) {
        try {
            System.out.println(
                    "greeting=" + LibraryApi.greet());
        } catch (NoSuchMethodError ex) {
            System.out.println(
                    "linkage=" + ex.getClass().getSimpleName());
        }
    }
}
```

Tại thư mục `binary-lab`, chạy:

```bash
mkdir build
javac -encoding UTF-8 -d build v1/LibraryApi.java BinaryClient.java
java -cp build BinaryClient
```

**Stdout khi runtime còn v1:**

```text
greeting=hello-v1
```

Giờ **chỉ biên dịch file v2 vào cùng thư mục output**, ghi đè `build/LibraryApi.class` mà giữ nguyên `build/BinaryClient.class`:

```bash
javac -encoding UTF-8 -d build v2/LibraryApi.java
java -cp build BinaryClient
```

**Stdout sau khi thay binary:**

```text
linkage=NoSuchMethodError
```

Lệnh `javac` và `java` hoạt động tương tự trên POSIX shell và PowerShell; bạn có thể dùng `New-Item -ItemType Directory build` để tạo thư mục trong PowerShell nếu muốn. `mkdir build` chỉ cần chạy lần đầu khi thư mục chưa có.

### Giải thích chi tiết

1. `BinaryClient.java` chỉ có thể biên dịch vì v1 khai báo `LibraryApi.greet():String`.
2. Trong `BinaryClient.class` có tham chiếu tới method tương ứng.
3. Khi biên dịch v2 **chỉ với file `LibraryApi.java`**, JVM bytecode của client **không thay đổi**.
4. Khi chạy process mới, JVM tìm thấy `LibraryApi.class` của v2 nhưng class này chỉ có `salute()`, không còn `greet()`.
5. Khi client thực thi lời gọi cũ, JVM báo `NoSuchMethodError`. Handler ở đây chỉ để minh họa tên lỗi; không biến client cũ thành tương thích.

### Nếu biên dịch lại cả client với v2 thì sao?

Khi `BinaryClient.java` vẫn gọi `LibraryApi.greet()` mà v2 không có method, trình biên dịch sẽ báo lỗi source (ví dụ `cannot find symbol`) thay vì tạo ra cùng bytecode để runtime ném `NoSuchMethodError`. Đó là lý do thí nghiệm **cố tình giữ bytecode client cũ**.

**Không chạy thao tác thay thế `.class` trên project sản xuất.** Sử dụng riêng thư mục thí nghiệm để tránh phá build hiện có.

## 6. `NoSuchFieldError` và bẫy constant inlining

`NoSuchFieldError` giống `NoSuchMethodError` ở chỗ client binary cần một field mà runtime class không còn đáp ứng. Ví dụ:

```java
// v1
public class ConfigApi {
    public static int level = 3;
}
```

Client đã biên dịch đọc `ConfigApi.level`. Nếu v2 xóa field hoặc thay đổi định nghĩa sao cho tham chiếu binary cũ không còn được thỏa mãn, JVM có thể ném `NoSuchFieldError` lúc đọc field.

### Cẩn thận với `static final` compile-time constants

Đoạn sau là **constant variable**:

```java
public static final String LABEL = "v1";
```

Trong nhiều chỗ dùng, `javac` có thể **inline** giá trị `"v1"` vào client bytecode. Nếu sau đó xóa `LABEL` khỏi library, client cũ có thể **không** truy cập field runtime ở thao tác đó, nên **không nhất thiết ném `NoSuchFieldError`** mà vẫn hiển thị giá trị cũ.

Do đó, để minh họa `NoSuchFieldError` một cách đáng tin, ví dụ nên dùng **field không phải compile-time constant**.

## 7. Thí nghiệm Java 17+ hoàn chỉnh: thay binary để tạo `NoSuchFieldError`

Tạo thư mục riêng hoặc dùng một thư mục thí nghiệm khác với phần trước:

```text
field-lab/
├── v1/
│   └── FieldApi.java
├── v2/
│   └── FieldApi.java
├── FieldClient.java
└── build/
```

**Tệp `v1/FieldApi.java`:**

```java
public class FieldApi {
    public static final String CODE = new String("V1");
}
```

Dùng `new String("V1")` ở đây **chỉ nhằm tránh biến `CODE` thành compile-time constant**. Đây không phải khuyến nghị tạo `String` kiểu này trong ứng dụng thông thường.

**Tệp `v2/FieldApi.java`:**

```java
public class FieldApi {
    public static String currentCode() {
        return "V2";
    }
}
```

**Tệp `FieldClient.java`:**

```java
public class FieldClient {
    public static void main(String[] args) {
        try {
            System.out.println("field=" + FieldApi.CODE);
        } catch (NoSuchFieldError ex) {
            System.out.println(
                    "linkage=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch client với v1:

```bash
mkdir build
javac -encoding UTF-8 -d build v1/FieldApi.java FieldClient.java
java -cp build FieldClient
```

**Stdout với v1:**

```text
field=V1
```

Thay riêng binary của `FieldApi`:

```bash
javac -encoding UTF-8 -d build v2/FieldApi.java
java -cp build FieldClient
```

**Stdout với v2:**

```text
linkage=NoSuchFieldError
```

Tương tự thí nghiệm method, `FieldClient.class` giữ tham chiếu đã được biên dịch tới `FieldApi.CODE:Ljava/lang/String;`, trong khi runtime v2 không còn field này.

### Bài học về constant inlining

Nếu bạn thay trường v1 thành:

```java
public static final String CODE = "V1";
```

thì thí nghiệm có thể cho kết quả khác: compiler có thể inline literal `"V1"` vào client. Đây không phải bằng chứng mọi thay đổi field đều tương thích; nó cho thấy **bytecode thực sự đã được tạo** mới quyết định có tham chiếu runtime tới field hay không.

Có thể dùng `javap -c -p FieldClient` để thấy sự khác biệt giữa lệnh lấy field (`getstatic`) và constant được đưa thẳng vào bytecode trong những trường hợp compiler inline.

## 8. `IncompatibleClassChangeError`: sự thay đổi về “hình dạng” binary

`IncompatibleClassChangeError` có nhiều subclass cụ thể. Bản thân tên lỗi có thể xuất hiện khi bytecode mong đợi một thành viên hoặc kiểu có **hình dạng khác** thực tế.

Một ví dụ điển hình là **thay phương thức instance thành static** hoặc ngược lại trong bản thư viện runtime, trong khi giữ client binary cũ:

```java
// v1
public class ModeApi {
    public String status() {
        return "ready";
    }
}
```

Client được biên dịch dùng:

```java
new ModeApi().status();
```

Nếu v2 đổi method thành:

```java
// v2
public class ModeApi {
    public static String status() {
        return "ready";
    }
}
```

client cũ có thể vẫn chứa instruction gọi **instance method** theo hợp đồng v1. Khi runtime chỉ có method static tương ứng, JVM báo **`IncompatibleClassChangeError`** theo quy tắc invoke không phù hợp. Đây **không phải** một lời khuyên nên đổi static/instance kiểu này trong thư viện public.

### Thay `class` thành `interface`

Các phép gọi/kiểu liên quan tới class và interface được mã hóa và kiểm tra khác nhau. Đổi một public type từ class thành interface (hoặc ngược lại) mà client binary cũ vẫn kỳ vọng hình dạng trước đó có thể tạo linkage failure thuộc nhóm `IncompatibleClassChangeError`.

**Không thử sửa bằng cách ép kiểu hoặc thêm `throws`**. Phải khôi phục binary contract hoặc build lại toàn bộ thành phần phụ thuộc theo API mới.

## 9. `AbstractMethodError`: dispatch không tìm thấy triển khai phù hợp

`AbstractMethodError` xảy ra khi runtime cần gọi một abstract method nhưng không tìm được implementation concrete đáp ứng các quy tắc tương ứng.

Một tình huống cần chú ý trong nâng cấp API:

- Interface v1 công bố `run()`.
- Một implementation được build theo v1 thực hiện `run()`.
- Interface v2 thêm một **abstract method mới** `stop()`.
- Client mới được build để gọi `stop()`; runtime lại sử dụng implementation binary cũ chưa có method này.
- Khi thực thi lời gọi trên đối tượng implementation cũ, JVM có thể báo `AbstractMethodError`.

Khác biệt với `NoSuchMethodError`:

- Trong trường hợp này, interface/class contract mới **có khai báo method**, nhưng implementation runtime không đáp ứng lời gọi như một method concrete.
- Với `NoSuchMethodError`, tham chiếu nhị phân không được phân giải thành method cần thiết.

### Vì sao thêm abstract method có thể nguy hiểm?

Thêm method abstract vào một interface thường có ý nghĩa **người implement mới phải cài đặt**. Với mã nguồn cũ được build lại, compiler có thể yêu cầu bổ sung method. Nhưng các binary implementation cũ đã deploy có thể không được build lại ngay, tạo lỗi khi method mới được gọi.

Một cách phát triển API cần cân nhắc là dùng `default method` nếu có một hành vi mặc định đúng nghĩa:

```java
public interface Job {
    void run();

    default void stop() {
        throw new UnsupportedOperationException(
                "stop not supported");
    }
}
```

Đây là interface minh họa. `default` có thể giúp giảm yêu cầu triển khai khi nâng cấp trong một số trường hợp, **nhưng không bảo đảm mọi thay đổi interface đều nhị phân tương thích tuyệt đối**: nhiều kế thừa/default method xung đột và chi tiết resolution vẫn phải được đánh giá. Ngoài ra, `UnsupportedOperationException` là quyết định hành vi, không tự làm chức năng `stop` được hỗ trợ.

## 10. `IllegalAccessError`: binary tham chiếu không còn được phép truy cập

`IllegalAccessError` là subclass của `IncompatibleClassChangeError`, khác với checked reflection `IllegalAccessException`.

Ví dụ:

- Client được biên dịch khi một method của thư viện là `public`.
- Phiên bản thư viện runtime thay method ấy thành `private` hoặc giảm quyền truy cập theo cách không còn cho phép client gọi.
- Client binary vẫn cố truy cập như trước, dẫn tới `IllegalAccessError` khi resolution/access checking phát hiện sự không hợp lệ.

Một hệ thống sử dụng module cũng có thể gặp các ràng buộc truy cập ở những giai đoạn khác nhau. **Đừng gộp mọi lỗi mở package/reflection thành `IllegalAccessError`**: reflection có thể báo `IllegalAccessException` hoặc `InaccessibleObjectException` tùy thao tác và quy tắc JPMS.

Bảng so sánh:

| Loại | Nhóm | Ngữ cảnh |
| --- | --- | --- |
| `IllegalAccessError` | `LinkageError` → unchecked | JVM phát hiện một binary access không hợp lệ |
| `IllegalAccessException` | `ReflectiveOperationException` → checked | API reflection cố truy cập member không được phép trong tình huống tương ứng |
| `InaccessibleObjectException` | `RuntimeException` → unchecked | Reflection không thể mở quyền truy cập theo ràng buộc module/accessible flag |

Nếu lỗi xuất hiện sau nâng cấp library, hãy so sánh **quyền truy cập thực của member trong JAR runtime**, không chỉ mã nguồn version mới.

## 11. `NoClassDefFoundError`: lớp được tham chiếu nhưng runtime không dùng được

Một client đã biên dịch có thể chứa tham chiếu tới `com.example.Helper`. Nếu runtime class loader không tìm được định nghĩa cần thiết của `Helper`, JVM có thể ném `NoClassDefFoundError` tại lần sử dụng.

Khác với:

```java
Class.forName("com.example.Helper");
```

lời gọi `Class.forName` công bố checked `ClassNotFoundException` khi tìm lớp theo tên. Còn:

```java
Helper.doWork();
```

là **lời gọi minh họa** được biên dịch với tham chiếu trực tiếp tới class; nếu dependency runtime bị thiếu, có thể gặp `NoClassDefFoundError`.

Những nguyên nhân điển hình:

1. Một JAR có trên compile classpath nhưng vắng trên runtime classpath.
2. Dependency gián tiếp bị loại khỏi package/container image.
3. Class loader hiện tại không nhìn thấy class dù JAR ở nơi khác.
4. Class/module không được cung cấp trong cấu hình runtime phù hợp.
5. Lớp đã **thất bại ở static initialization** và được dùng lại.

`NoClassDefFoundError` không có nghĩa “Java không thể biên dịch file” và cũng **không luôn có nghĩa thiếu tệp `.class` vật lý**. Cần xét cả lỗi khởi tạo tĩnh.

### Cùng class name nhưng khác class loader

Danh tính runtime type phụ thuộc vào **binary name và defining class loader**. Hai class loader có thể định nghĩa lớp cùng tên nhưng JVM coi đó là hai kiểu runtime khác nhau. Hệ thống plugin/app server có thể có lỗi visibility/version do một loader nhìn thấy dependency mà loader khác không thấy.

Đừng chỉ kiểm tra `jar tf` cho thấy class hiện diện ở đâu đó rồi kết luận mọi loader đều dùng được class đó.

## 12. `ExceptionInInitializerError` và lớp ở trạng thái khởi tạo lỗi

Khi JVM khởi tạo class, nó chạy các static initializers theo quy tắc Java. Nếu quá trình đó ném một ngoại lệ không phải `Error` và không khởi tạo thành công, JVM có thể bọc lỗi bằng `ExceptionInInitializerError`.

Ví dụ:

```java
static final class Broken {
    static final int VALUE = initialize();

    static int initialize() {
        throw new IllegalStateException("bad startup config");
    }
}
```

Đây là nested class minh họa. Khi `Broken.VALUE` được đọc, JVM cần khởi tạo `Broken`, và phép gán static field làm ném `IllegalStateException`.

Sau thất bại, class có thể được đánh dấu **erroneous** đối với defining loader. Lần dùng tiếp theo trong cùng loader có thể ném `NoClassDefFoundError` với ý nghĩa lớp không thể khởi tạo, **dù class file vẫn tồn tại**.

### Ví dụ Java 17+ hoàn chỉnh: lỗi lần đầu và lần kế tiếp

Lưu tệp **`InitializationLinkageDemo.java`**:

```java
public class InitializationLinkageDemo {

    static final class Broken {
        static final int VALUE = initialize();

        static int initialize() {
            System.out.println("initializer invoked");
            throw new IllegalStateException(
                    "required configuration unavailable");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(Broken.VALUE);
        } catch (ExceptionInInitializerError ex) {
            System.out.println(
                    "first=" + ex.getClass().getSimpleName());
            System.out.println(
                    "cause=" + ex.getCause().getClass().getSimpleName());
        }

        try {
            System.out.println(Broken.VALUE);
        } catch (NoClassDefFoundError ex) {
            System.out.println(
                    "second=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 InitializationLinkageDemo.java
java InitializationLinkageDemo
```

**Stdout dự kiến chính xác cho chương trình này:**

```text
initializer invoked
first=ExceptionInInitializerError
cause=IllegalStateException
second=NoClassDefFoundError
```

Phân tích:

- `Broken.VALUE` không phải compile-time constant vì giá trị đến từ lời gọi method.
- Lần truy cập đầu kích hoạt `initialize()`; method in một dòng rồi ném `IllegalStateException`.
- JVM bọc ngoại lệ đầu vào `ExceptionInInitializerError`; handler đọc `getCause()` là `IllegalStateException`.
- Lần đọc tiếp không chạy lại `initialize()`; JVM báo `NoClassDefFoundError` vì class đã khởi tạo thất bại trong cùng loader.

**Không sửa bằng cách retry không giới hạn.** Cần tìm lỗi khởi tạo **đầu tiên** trong log, sửa cấu hình/thứ tự startup, và dùng một process hoặc class loader mới theo cơ chế triển khai nếu phải chạy lại việc khởi tạo.

## 13. `UnsupportedClassVersionError`: phiên bản class file cao hơn runtime hỗ trợ

Một thư viện có thể được biên dịch bằng JDK mới hơn, trong khi production chạy JRE/JDK cũ hơn. JVM cũ không hiểu class-file major version của thư viện mới và có thể ném:

```text
java.lang.UnsupportedClassVersionError: ...
```

Đây là subclass của `ClassFormatError` → `LinkageError`.

Ví dụ người phát triển dùng JDK 21 để compile mặc định, nhưng máy chạy production chỉ có Java 17. File class tạo theo target mặc định của JDK 21 có thể không chạy trên Java 17.

Cách phòng:

```bash
javac --release 17 Example.java
```

Đây là **lệnh minh họa**, giả định `Example.java` tồn tại; `--release 17` yêu cầu compiler tạo class tương thích ngôn ngữ/API theo bản Java 17 được hỗ trợ bởi compiler.

**Không chỉ dùng `-source 17 -target 17` một cách tùy tiện nếu bạn cũng cần ràng buộc API thư viện chuẩn theo release đích**: `--release` được thiết kế để kiểm soát cả API public của Java platform theo target tương ứng.

### Class-file major version thường gặp

| Bản Java | Class-file major version |
| --- | --- |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Đây là các mốc ổn định. Một class file được biên dịch theo version cao hơn runtime có thể không dùng được; cũng cần xét preview features và các ràng buộc JVM tương ứng.

**Không đồng nhất `UnsupportedClassVersionError` với `NoClassDefFoundError`:** lỗi version có lớp/bytecode nhưng runtime **không hỗ trợ định dạng phiên bản**, không đơn giản là thiếu class.

## 14. `VerifyError` và `ClassFormatError`

`ClassFormatError` phát sinh khi file class không tuân thủ định dạng mà JVM hỗ trợ. Một số subclass biểu diễn vấn đề cụ thể, bao gồm `UnsupportedClassVersionError`.

`VerifyError` phát sinh khi JVM verifier phát hiện bytecode không thỏa các quy tắc xác minh cần thiết. Đây cũng là `LinkageError`.

Nguyên nhân có thể liên quan:

- Bytecode bị sửa/ghi sai bởi generator hoặc transformer.
- Tạo bytecode không hợp lệ theo quy tắc verifier.
- Agent/instrumentation hoặc build tooling tạo ra kết quả không phù hợp.
- Artifact bị hỏng hoặc kết hợp với công cụ bytecode không hỗ trợ phiên bản Java đang dùng.

Không kết luận rằng `VerifyError` luôn do một lỗi trong source code Java được `javac` biên dịch bình thường; phần lớn mã Java hợp lệ do compiler đáng tin tạo ra không chủ động sinh bytecode lỗi theo cách này.

Phân tích thực tế nên kiểm tra generator, bytecode instrumentation, phiên bản JVM và class file được nạp; nếu cần, tái tạo với build sạch và không có transform/agent để cô lập nguồn lỗi.

## 15. `BootstrapMethodError` và `invokedynamic`

Các tính năng JVM như `invokedynamic` sử dụng cơ chế bootstrap để liên kết các vị trí gọi động. Java lambda hoặc một số kiểu string concatenation có thể sử dụng những cơ chế bytecode liên quan.

`BootstrapMethodError` có thể phát sinh khi bootstrap method hoặc quá trình liên kết call site không hoàn tất đúng. Ví dụ nguồn lỗi có thể nằm ở:

- Một bootstrap method được tạo hoặc cấu hình sai.
- Sự không tương thích của bytecode được transform.
- Dependency runtime/lookup bị thay đổi làm bootstrap thất bại.
- Thao tác dynamic linkage phức tạp trong framework, compiler hoặc ngôn ngữ trên JVM khác.

Không nên suy ra **mọi lambda thất bại đều là `BootstrapMethodError`**. Lambda body ném `IOException`, `IllegalArgumentException` hoặc lỗi nghiệp vụ vẫn là ngoại lệ của logic tương ứng; bootstrap chỉ liên quan khi **liên kết call site** bị lỗi.

Nếu thấy `BootstrapMethodError`, hãy đọc cause, frame liên quan và dependency của công cụ sinh bytecode, thay vì chỉ sửa lambda body theo cảm tính.

## 16. `UnsatisfiedLinkError`: liên kết native library

`UnsatisfiedLinkError` cũng là subtype của `LinkageError`, nhưng liên quan **native code/JNI** hơn là một method Java bị xóa thông thường.

Các tình huống có thể gặp:

- `System.loadLibrary("native_component")` không tìm được native library thích hợp.
- Native binary sai kiến trúc hoặc không tương thích với hệ điều hành/ABI.
- Tên native method được khai báo ở Java không có implementation native tương thích.
- Thiếu dependency của chính native library.
- Đường tìm thư viện hoặc quyền truy cập native library không đúng.

Ví dụ:

```java
try {
    System.loadLibrary("definitely_missing_native_library");
} catch (UnsatisfiedLinkError ex) {
    System.out.println(
            "native=" + ex.getClass().getSimpleName());
}
```

Đây là **đoạn minh họa**, không đảm bảo cùng tên library sẽ thiếu ở mọi môi trường tùy chỉnh. Nó cho thấy lỗi native linkage không thuộc `ClassNotFoundException`.

Với hệ thống thật, kiểm tra kiến trúc CPU, `java.library.path`, loader của hệ điều hành, native dependencies, phiên bản JDK/JNI và cơ chế package native.

## 17. Các lỗi giống tên nhưng khác bản chất

| Lỗi | Khi nào thường gặp | Nhóm |
| --- | --- | --- |
| `NoSuchMethodException` | Reflection không tìm được method theo truy vấn | Checked exception |
| `NoSuchMethodError` | Binary method reference không còn được đáp ứng | `LinkageError` |
| `NoSuchFieldException` | Reflection không tìm được field theo truy vấn | Checked exception |
| `NoSuchFieldError` | Binary field reference không còn được đáp ứng | `LinkageError` |
| `ClassNotFoundException` | Nạp class theo tên qua API không thành công | Checked exception |
| `NoClassDefFoundError` | Runtime cần class definition không dùng được hoặc failed init | `LinkageError` |
| `IllegalAccessException` | Reflection access không được phép | Checked exception |
| `IllegalAccessError` | Binary access violation trong linkage | `LinkageError` |
| `ClassCastException` | Ép kiểu reference runtime sai | Unchecked `RuntimeException` |
| `IncompatibleClassChangeError` | Binary shape/dispatch không tương thích | `LinkageError` |
| `InvalidClassException` | Không tương thích Java object serialization theo hợp đồng | Checked `IOException` |

**Không phải mọi “lỗi version” đều là `LinkageError`.** Chẳng hạn `InvalidClassException` thường liên quan Java serialization và `serialVersionUID`/schema của object; đó là một checked I/O exception, không phải cơ chế JVM gọi method đã biên dịch không khớp.

## 18. Vì sao phụ thuộc cùng tên nhưng khác phiên bản gây linkage errors?

Một ứng dụng hiện đại có thể có:

- Mã ứng dụng.
- Nhiều thư viện trực tiếp.
- Thư viện bắc cầu (*transitive dependencies*).
- Plugin hoặc Java agents.
- Application server với thư viện dùng chung.
- Module path và classpath.
- JAR được đóng gói trong image/container.

Ví dụ mô hình:

```text
ServiceApp ───────► ReportingLibrary v3
     │                    │
     │                    └──► CommonApi v2
     │
     └────────────► LegacyConnector
                          └──► CommonApi v1
```

Nếu build compile một số client theo `CommonApi v2`, nhưng runtime class loader lại đưa `CommonApi v1` vào phạm vi trước, client có thể gọi method mới không tồn tại trong v1 → `NoSuchMethodError`.

Vấn đề không nhất thiết do một file `.class` “bị lỗi”; có thể là **version selection và class-loader visibility**.

### Không tin riêng file build khai báo

File cấu hình Maven/Gradle nói lên **ý định** dependency, không nhất thiết là toàn bộ artifact runtime. Cần xem:

- Dependency version **đã được resolve**.
- Phạm vi compile/runtime/test của từng dependency.
- Kết quả đóng gói (fat JAR, shaded JAR, WAR, distribution).
- Có JAR trùng class trong nhiều đường tìm không?
- Thư viện do container/app server cung cấp có đè phiên bản ứng dụng không?
- Class loader nào thật sự định nghĩa class lúc lỗi?

**Runtime truth = class bytes và class loader mà process thực đang sử dụng**, không chỉ dependency version được viết trong source configuration.

## 19. Dùng công cụ chuẩn để xem bytecode và classpath

### `java -version` và `javac -version`

```bash
java -version
javac -version
```

Kiểm tra JVM chạy thật và compiler đang dùng. Hai phiên bản có thể khác nhau trên cùng máy.

### `javap` để đọc signature và instruction

```bash
javap -classpath build -s -p LibraryApi
javap -classpath build -c -p BinaryClient
```

Trong thí nghiệm phần 5:

- `javap -s` có thể cho thấy descriptor của method runtime.
- `javap -c` có thể cho thấy bytecode của client còn tham chiếu tới `LibraryApi.greet`.
- Nếu đã thay `LibraryApi.class` bằng v2, công cụ có thể thấy class runtime không còn `greet` dù client binary vẫn gọi tên ấy.

Với phân tích phức tạp, `javap -v` cung cấp constant pool và dữ liệu class file chi tiết hơn.

### `jar tf` cho artifact thực

```bash
jar tf example-library.jar
```

Tìm entry như `com/example/LibraryApi.class`. Có tên class trong JAR mới chỉ chứng minh **artifact chứa entry**, không chứng minh app runtime nạp JAR đó, không chứng minh class phù hợp binary API.

### JVM unified logging cho class load

```bash
java -Xlog:class+load=info -cp build BinaryClient
```

Lệnh minh họa trên JVM hỗ trợ unified logging cho thấy nhiều sự kiện nạp lớp. Output dài, phụ thuộc JVM/version và môi trường, nên **không so khớp nguyên văn toàn bộ log** trong bài test.

### `jdeps` để xem dependencies

```bash
jdeps -verbose:class example-app.jar
```

`jdeps` giúp phân tích phụ thuộc tĩnh ở bytecode; nó không chứng minh mọi nhánh dynamic loading/reflection/plugin runtime đều đã được bao phủ.

### Maven/Gradle

Có thể dùng công cụ phân tích dependency graph của Maven/Gradle tương ứng với dự án đang dùng, rồi đối chiếu với JAR/thư mục thực của distribution. Cú pháp lệnh cụ thể phụ thuộc build tool và module configuration; không suy đoán một câu lệnh đúng cho mọi build.

## 20. Sửa lỗi bằng cách nào? Quy trình chẩn đoán tuần tự

### Bước 1 — Giữ nguyên loại lỗi thật

Nếu thấy `NoSuchMethodError`, đừng đổi thành một chuỗi lỗi chung “something failed”. Ghi lại:

- Lớp exception chính.
- Tên class và member liên quan nếu có.
- Chữ ký method/field mà client kỳ vọng khi có thể.
- Stack trace đầu tiên và `Caused by`, `Suppressed` nếu liên quan.
- Môi trường runtime: JDK, build ID, artifact và deployment version.

### Bước 2 — Xác định lời gọi gây lỗi

Frame nào trong mã ứng dụng đang gọi method/đọc field/kích hoạt class init?

- `NoSuchMethodError`: tìm method được gọi và binary descriptor.
- `NoSuchFieldError`: tìm field reference; xét compile-time constant inlining.
- `AbstractMethodError`: xem implementation concrete của interface/abstract class.
- `IllegalAccessError`: kiểm tra quyền truy cập member giữa các version.
- `NoClassDefFoundError`: kiểm tra class visibility, dependency hoặc failed initialization trước đó.

### Bước 3 — So sánh compile artifact với runtime artifact

Cần biết client `.class` được compile dựa trên phiên bản nào và JVM thật dùng JAR nào. Đọc dependency graph và class loading, không chỉ đoán từ tên package.

### Bước 4 — Kiểm tra phiên bản JVM

Nếu lỗi thuộc `UnsupportedClassVersionError`, kiểm tra class-file version, compiler target và runtime JDK. Đừng cố dùng `catch` để ép JVM hiểu bytecode mới hơn.

### Bước 5 — Phân biệt loader và module boundary

Với app server, plugin, JPMS hoặc custom `URLClassLoader`, xác định class được định nghĩa ở loader nào, liệu class có cùng binary name nhưng khác loader không, và module có nằm trong resolved graph không.

### Bước 6 — Cô lập bằng một build/runtime tối thiểu

Thí nghiệm v1/v2 ở trên là mô hình. Với dự án thật, tạo một integration test dùng **chính distribution** của hệ thống và các version đã chốt. Không tự sửa file `.class` trong production process đang chạy.

### Bước 7 — Áp dụng fix tại nguồn

Tùy nguyên nhân:

- Căn chỉnh dependency versions.
- Phục hồi public method/field binary contract hoặc cung cấp adapter tương thích.
- Build lại tất cả client nếu chủ động phá binary compatibility.
- Sửa runtime packaging/classpath/module path.
- Sửa static initializer và thứ tự cấu hình.
- Nâng đúng JVM hoặc compile target thấp hơn có hỗ trợ.
- Sửa agent/bytecode transformer nếu gây lỗi verify/bootstrap.

### Bước 8 — Kiểm chứng lại

Không chỉ kiểm tra app khởi động. Chạy **đúng code path từng gây lỗi**, và thêm regression/integration test cho bản artifact cuối cùng.

## 21. Khi nào nên giữ binary compatibility trong thư viện?

Nếu bạn phát hành một thư viện public, client có thể không được rebuild cùng lúc. Do đó một bản cập nhật nên có chiến lược:

- **Không xóa/đổi tên public method đang được sử dụng** nếu muốn giữ tương thích.
- Nếu API mới được thêm, cân nhắc **giữ method cũ** và delegation tới method mới trong giai đoạn chuyển tiếp.
- Không giảm access của public/protected member mà client cũ cần.
- Không đổi static/instance của field/method nếu client binary cũ chưa được cập nhật.
- Kiểm tra việc đổi class thành interface và ngược lại.
- Khi thêm abstract method vào interface, cân nhắc khả năng implementation cũ chưa rebuild.
- Xem xét binary/source tests với nhiều version của client và library.
- Có kế hoạch deprecation, migration và quản lý semantic versioning phù hợp.

### Bridge/delegation khi đổi API

Ví dụ thay vì xóa method cũ:

```java
public class LibraryApi {
    public static String greet() {
        return salute();
    }

    public static String salute() {
        return "hello-v2";
    }
}
```

Đây là **class minh họa**. Nếu các điều kiện class/loader khác vẫn tương thích, client binary cũ gọi `greet():String` có thể tiếp tục chạy, trong khi client mới dùng `salute()`.

Nhưng việc giữ tên **chưa đủ** nếu đã đổi return type, parameter types, static-ness, access hoặc các ràng buộc binary khác. Hãy kiểm tra descriptor và linkage thật.

## 22. “Rebuild tất cả” có phải cách sửa duy nhất?

Không phải lúc nào cũng cần rebuild toàn bộ. Có hai nhóm chiến lược:

**Giữ client binary cũ:**

- Phục hồi API tương thích trong library mới.
- Chọn đúng bản library mà client cần.
- Cài lại dependency thiếu/đúng version.
- Sửa class loader/deployment để JVM thấy artifact đúng.

**Chuyển sang API mới và cập nhật client:**

- Recompile/retest các client sau khi đổi signature.
- Phát hành artifact mới đồng bộ.
- Kiểm soát thời gian rollout nếu nhiều service/plugin cập nhật khác nhau.
- Có compatibility matrix giữa version producer/consumer.

Mục tiêu là **đảm bảo một runtime có bộ class và method phù hợp**, không chỉ tạo một bản build “không báo lỗi” trên môi trường phát triển.

### Cẩn thận với hot swap/hot reload

Việc thay JAR trên đĩa khi JVM đã chạy **không đảm bảo** các class được nạp trước sẽ tự đổi sang bytecode mới. Class đã được định nghĩa bởi loader thường giữ định nghĩa ấy trong vòng đời loader tương ứng; các cơ chế redefinition/reload có ràng buộc riêng.

Để kiểm chứng một phép nâng cấp binary, nên chạy **process sạch** hoặc dùng cơ chế plugin reload có kiểm soát, không đoán rằng thao tác thay file luôn có hiệu lực tức thì.

## 23. Liên hệ với checked exception và custom exceptions

Vì `LinkageError` là subclass của `Error`:

```java
try {
    runFeature();
} catch (Exception ex) {
    // Không bắt LinkageError.
}
```

Đây là mã minh họa với `runFeature()` được định nghĩa hợp lệ. `catch (Exception)` không bắt được `NoSuchMethodError`, `NoClassDefFoundError` hay các `LinkageError` khác.

Tương tự, thêm:

```java
void runFeature() throws NoSuchMethodError
```

là khai báo không cần thiết đối với compiler vì `NoSuchMethodError` là unchecked. Nó **không sửa** việc JVM không tìm được method.

### Khi nào có thể bắt một `LinkageError` cụ thể?

Trong ứng dụng thông thường, không nên bắt rồi coi tác vụ thành công. Nhưng một **plugin manager cách ly** có thể bắt một nhóm linkage errors ở ranh giới nạp plugin để:

1. Đánh dấu plugin không thể khởi động.
2. Báo lỗi có cấu trúc và giữ stack trace.
3. Không tiếp tục sử dụng đối tượng/class từ plugin lỗi.
4. Chọn plugin khác nếu hợp đồng cho phép và đã kiểm thử.
5. Không giấu lỗi của **core runtime dependency** như một lỗi plugin tùy chọn.

Đây là tình huống đặc thù, không phải quy tắc viết `catch (LinkageError ignored)` trên mọi API.

### Không phân loại theo `getMessage()`

Không nên:

```java
if (error.getMessage().contains("NoSuchMethod")) {
    // ...
}
```

Vì message có thể khác JVM/version và có thể `null`. Dùng **kiểu exception**, stack/cause và dữ liệu version/dependency được kiểm tra có cấu trúc. Ngay cả khi biết loại lỗi, **fix phải dựa vào binary mismatch thật**, không chỉ tên exception.

## 24. Lỗi khởi tạo khác lỗi missing method

Khi gặp:

```text
java.lang.NoClassDefFoundError: Could not initialize class ...
```

đừng chỉ tra xem `.class` ở JAR nào. Đây có thể là **hậu quả của một lần initialization failure trước đó**. Trình tự có thể như sau:

```text
Process khởi động
   |
   v
Lần đầu dùng ConfigHolder
   |
   +-- static initializer ném IllegalStateException
   |       -> ExceptionInInitializerError
   |
   v
Lần sau dùng ConfigHolder trong cùng loader
   |
   +-- NoClassDefFoundError
```

Nếu một công cụ giám sát chỉ giữ thông điệp lần sau mà bỏ log lần đầu, người vận hành có thể nhầm sang lỗi đóng gói classpath. Đây là lý do nên giữ **log theo sự kiện và cause chain**, không chỉ một message cuối.

## 25. Một số thay đổi API và rủi ro liên kết

| Thay đổi | Rủi ro điển hình với binary client cũ | Nhận xét |
| --- | --- | --- |
| Xóa public method đã dùng | `NoSuchMethodError` | Tham chiếu method cũ không còn |
| Đổi parameter/return descriptor | `NoSuchMethodError` hoặc linkage failure tương ứng | Binary descriptor là yếu tố then chốt |
| Xóa field đã dùng | `NoSuchFieldError` | Constant inlining có thể làm vài call site khác biệt |
| Instance method ↔ static method | `IncompatibleClassChangeError` | Instruction invoke và loại member không khớp |
| Giảm access của member | `IllegalAccessError` | Kiểm tra quyền JVM ở runtime |
| Thêm abstract interface method rồi gọi trên implementation cũ | `AbstractMethodError` | Implementation cũ không có method concrete |
| Loại class khỏi runtime dependency | `NoClassDefFoundError` | Không phải trường hợp duy nhất gây NCDFE |
| Static initializer thất bại | `ExceptionInInitializerError`, sau đó có thể `NoClassDefFoundError` | Tìm lỗi gốc lần đầu |
| Class file build cho JDK quá mới | `UnsupportedClassVersionError` | Fix target/runtime JDK |
| Bytecode generator tạo class lỗi định dạng | `ClassFormatError`/`VerifyError` | Cần sửa tooling/artifact |
| Bootstrap linkage không thành công | `BootstrapMethodError` | Điều tra bootstrap/cause |
| Native library không load/link được | `UnsatisfiedLinkError` | Không phải lỗi public method Java bị xóa |

Bảng thể hiện **rủi ro thường gặp**, không phải hàm ánh xạ một-một cho mọi biến thể của đặc tả. Có thay đổi tương thích hơn bạn tưởng, có thay đổi source-compatible nhưng không phù hợp binary, và có các trường hợp JVM chọn subclass cụ thể theo chặng lỗi.

## 26. Những hiểu lầm thường gặp

| Hiểu lầm | Thực tế | Cách sửa |
| --- | --- | --- |
| “Biên dịch thành công thì chắc chắn runtime có đủ method” | Runtime class có thể là bản khác | Kiểm tra artifact và version thực |
| “Mọi lỗi linkage đều do thiếu JAR” | Có method/field/access/initialization/bytecode version | Phân loại subtype |
| “`NoSuchMethodError` là checked exception” | Nó extends `Error` | Sửa binary contract, không thêm `throws` |
| “`NoSuchMethodException` và `NoSuchMethodError` như nhau” | Reflection vs binary linkage | Đọc đúng hierarchy |
| “Nếu `jar tf` thấy class thì JVM chắc dùng được” | Class loader, module, dependency, init còn ảnh hưởng | Kiểm tra actual class loader |
| “Có `NoClassDefFoundError` nghĩa là file class luôn bị xóa” | Failed static init cũng tạo lỗi này | Tìm `ExceptionInInitializerError` đầu tiên |
| “Catch `Exception` sẽ bắt mọi linkage error” | `Error` nằm ngoài nhánh `Exception` | Xem đúng cơ chế |
| “Catch `Throwable` và trả default sẽ phục hồi” | Dễ che deployment hỏng | Fail/report ở ranh giới phù hợp |
| “Xóa `static final String` luôn gây `NoSuchFieldError`” | Constant có thể bị inline | Xem bytecode thực |
| “Cứ rebuild client là đủ” | Nếu runtime vẫn sai JAR, lỗi vẫn có thể còn | Sửa package/deploy classpath |
| “`java -version` trùng máy build” | CI, IDE, container, service có thể khác runtime | Đọc process thực |
| “Thay JAR trên đĩa thay bytecode trong JVM” | Class đã nạp không tự thay theo file | Restart/reload đúng cơ chế |
| “Không có linkage error nghĩa là API tương thích hành vi” | Semantics có thể đổi dù binary hợp lệ | Test hành vi và contract |
| “`NoSuchFieldError` luôn xuất hiện ngay khi startup” | Resolution có thể lười | Test code path gây lỗi |
| “Class loader nào cũng nhìn cùng một class” | Visibility và type identity theo defining loader | Phân tích loader |
| “`UnsupportedClassVersionError` do method bị xóa” | Class-file format version không được JVM hỗ trợ | Sửa `--release`/runtime |

## 27. Bài tập tự kiểm tra

**Câu A.** `NoSuchMethodError` thuộc `Exception` hay `Error`?

**Đáp án:** thuộc `IncompatibleClassChangeError` → `LinkageError` → `Error`, là unchecked.

**Câu B.** Client được compile với thư viện v1 có `greet()`. Runtime v2 xóa `greet()`, client `.class` cũ vẫn chạy. Khi gọi method cũ có thể gặp gì?

**Đáp án:** `NoSuchMethodError`.

**Câu C.** Vì sao build lại client với v2 có thể báo lỗi compile-time thay vì `NoSuchMethodError` runtime?

**Đáp án:** source client gọi method không có trong API mới, nên `javac` không tạo ra bytecode hợp lệ với lời gọi ấy. Lỗi runtime tái hiện khi **giữ client bytecode cũ**.

**Câu D.** Đổi static method thành instance có liên quan gì đến `IncompatibleClassChangeError`?

**Đáp án:** bytecode cũ có thể sử dụng loại invocation dành cho static trong khi runtime member là instance, hoặc ngược lại. Đây là mismatch về binary shape.

**Câu E.** Vì sao không nên dùng `public static final String NAME = "x"` trong thí nghiệm chắc chắn tạo `NoSuchFieldError`?

**Đáp án:** biến là compile-time constant và giá trị có thể được inline vào bytecode client, khiến lần đọc không còn thực hiện `getstatic` ở runtime.

**Câu F.** Thêm abstract method vào interface v2 nhưng vẫn dùng implementation binary v1. Khi client mới gọi method đó có thể gặp gì?

**Đáp án:** `AbstractMethodError` nếu runtime dispatch không có implementation concrete phù hợp.

**Câu G.** Static initializer ném `IllegalStateException` lần đầu, sau đó cùng lớp được dùng lại. Hai lỗi nào thường xuất hiện?

**Đáp án:** lần đầu `ExceptionInInitializerError`, lần sau có thể `NoClassDefFoundError`.

**Câu H.** Java 21 compile mặc định, Java 17 chạy class file đó. Loại lỗi nào cần xem xét?

**Đáp án:** `UnsupportedClassVersionError` nếu class file target vượt khả năng JVM 17.

**Câu I.** Một JAR đúng tên chứa `SomeApi.class` nhưng runtime vẫn có lỗi method. Tại sao?

**Đáp án:** có thể class đã được nạp từ **JAR khác hoặc loader khác**, hoặc JAR chứa class nhưng binary API không có method descriptor mà client cần.

**Câu J.** Reflection gọi `getDeclaredMethod("missing")` và không tìm được method. Có bắt buộc là `NoSuchMethodError` không?

**Đáp án:** không; API reflection khai báo checked `NoSuchMethodException`.

**Câu K.** `catch (Exception)` có bắt `VerifyError` không?

**Đáp án:** không, `VerifyError` là `LinkageError`, không extends `Exception`.

**Câu L.** Thay đổi body method nhưng giữ cùng binary signature có nhất thiết tạo linkage error không?

**Đáp án:** không. Nó có thể tương thích binary nhưng thay đổi hành vi, nên vẫn phải test nghiệp vụ.

**Câu M.** Thay JAR trên filesystem sau khi class đã được JVM nạp có bảo đảm JVM dùng định nghĩa mới ngay không?

**Đáp án:** không. Cần hiểu vòng đời defining loader và cơ chế reload/redefinition đang dùng.

**Câu N.** `ClassNotFoundException` và `NoClassDefFoundError` khác nhau thế nào?

**Đáp án:** CNFE là checked exception thường từ API tìm lớp theo tên, còn NCDFE là unchecked linkage error khi JVM cần định nghĩa lớp runtime hoặc gặp class ở trạng thái failed initialization.

**Câu O.** Lúc service mới khởi động không có lỗi, một API ít dùng sau ba ngày ném `NoSuchMethodError`. Điều đó có mâu thuẫn không?

**Đáp án:** không. JVM có thể phân giải tham chiếu khi code path ấy chạy lần đầu. Cần kiểm tra runtime class bytes tại thời điểm lỗi.

## 28. Checklist phát hành thư viện và triển khai

Trước khi phát hành một API Java public:

1. Kiểm tra class/method/field **binary signatures** trước và sau.
2. Xác định những client không được rebuild cùng bản library.
3. Giữ các entrypoint public cũ hoặc cung cấp compatibility bridge nếu có thể.
4. Tránh thay đổi static/instance/access không tương thích.
5. Cân nhắc interface implementations cũ trước khi thêm abstract method.
6. Không coi compile-time constants là cơ chế versioning động đáng tin.
7. Kiểm tra tương thích nguồn, nhị phân và hành vi tách biệt.
8. Cài regression test chạy **client build với bản cũ + library runtime bản mới** khi cần hứa binary compatibility.

Trước khi triển khai ứng dụng:

1. Chốt dependency graph/resolved versions.
2. Xác nhận runtime distribution chứa dependency bắt buộc.
3. Kiểm tra classpath/module path và các JAR trùng tên class.
4. Xác nhận JDK version thực và compiler release target.
5. Xác định class loader/plugin boundaries.
6. Test artifact cuối cùng, không chỉ test trong IDE.
7. Tránh static initialization nặng, khó kiểm soát và phụ thuộc môi trường.
8. Có giám sát phát hiện `LinkageError` và lưu cause/log của lỗi đầu.
9. Khi lỗi xuất hiện, không tự retry vô hạn hoặc trả success giả.
10. Sau khi sửa dependency, restart/reload theo cơ chế runtime hợp lệ và chạy lại chính code path từng lỗi.

## Kết luận

**`LinkageError` là nhóm lỗi về việc runtime JVM không thể liên kết hoặc sử dụng một số cấu trúc đã được mã biên dịch giả định.** Những subclass như `NoSuchMethodError`, `NoSuchFieldError`, `AbstractMethodError` và `IllegalAccessError` thường liên quan **binary API không còn khớp**; `NoClassDefFoundError` có thể liên quan class thiếu hoặc khởi tạo thất bại; `UnsupportedClassVersionError`, `VerifyError`, `BootstrapMethodError` hay `UnsatisfiedLinkError` có cơ chế riêng cần điều tra đúng.

Để tránh và xử lý nhóm lỗi này, hãy phân biệt **compile-time source**, **client bytecode**, **library bytecode runtime**, **class loader/module graph** và **trạng thái khởi tạo lớp**. Khi nâng cấp thư viện, đừng chỉ hỏi “build có xanh không?” mà hãy hỏi **binary client cũ còn liên kết và chạy đúng hợp đồng với library mới không?**. Khi sự cố xảy ra, giải pháp bền vững thường là **căn chỉnh artifact, phiên bản JDK, classpath, binary contract hoặc cơ chế khởi tạo**, chứ không phải catch `Error` rồi cho qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-007">ExceptionInInitializerError khi khởi tạo static thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# `ExceptionInInitializerError` khi khởi tạo `static` thất bại trong Java

`ExceptionInInitializerError` là một lỗi thuộc nhánh `Error` dùng để báo rằng **quá trình khởi tạo lớp hoặc interface đã thất bại**. Trong thực tế, lỗi này thường xuất hiện khi một **static field initializer** hoặc **static initializer block** ném `RuntimeException` (hoặc một `Throwable` khác không thuộc `Error`) trong lúc JVM khởi tạo lớp.

Điều quan trọng nhất không phải chỉ là tên exception:

1. `ExceptionInInitializerError` thường là **lớp vỏ** báo lỗi ở giai đoạn JVM khởi tạo; nguyên nhân kỹ thuật thực tế nằm ở `getCause()` hoặc `getException()`.
2. **Không phải mọi lỗi phát sinh trong static initializer đều bị bọc**. Nếu ngoại lệ đang lan truyền đã là một `Error` hoặc subclass, JVM có thể truyền chính đối tượng `Error` đó tại lần thất bại đầu tiên.
3. **Sau khi một lớp khởi tạo thất bại**, cùng định nghĩa lớp đó thường được đánh dấu *erroneous* trong class loader hiện tại. Những lần *active use* sau sẽ gặp `NoClassDefFoundError`; Java **không tự chạy lại initializer**.
4. Không thể dùng `throws IOException` cho một khối `static`. Phải bắt checked exception bên trong quá trình khởi tạo hoặc chuyển công việc thất bại sang một phương thức bình thường.
5. Đối với I/O, kết nối, tải cấu hình hay tài nguyên cần retry, **static initializer thường không phải nơi thích hợp**.

Bài học thuộc Java Core – Exception, nhóm **Error và khởi tạo**, tập trung vào nguyên nhân, luật JVM, đọc stack trace/cause, hậu quả và thiết kế xử lý đúng, kèm ví dụ Java 17+ có stdout xác định.

## 1. `ExceptionInInitializerError` nằm đâu trong cây kế thừa?

Cây rút gọn:

~~~text
java.lang.Object
└── java.lang.Throwable
    ├── java.lang.Exception
    │   └── java.lang.RuntimeException
    │       └── java.lang.IllegalStateException
    └── java.lang.Error
        └── java.lang.LinkageError
            ├── java.lang.ExceptionInInitializerError
            └── java.lang.NoClassDefFoundError
~~~

Do kế thừa `LinkageError` → `Error`:

- `ExceptionInInitializerError` là **unchecked**, nên method không phải khai báo `throws ExceptionInInitializerError`.
- `catch (Exception e)` **không bắt được** nó.
- `catch (ExceptionInInitializerError e)` có thể bắt đúng loại, nhưng làm vậy **không khiến định nghĩa lớp bị lỗi trở lại trạng thái khởi tạo được**.
- `catch (Error)` và `catch (Throwable)` có thể nhận về mặt ngôn ngữ, nhưng không nên dùng mặc định rồi tiếp tục xử lý nghiệp vụ.
- `NoClassDefFoundError` ở các lần gọi sau cũng thuộc nhánh `Error`, không nằm trong `Exception`.

**Tên giống nhau không đồng nghĩa cùng vai trò:** `IllegalStateException` có thể là **nguyên nhân trong initializer**; `ExceptionInInitializerError` là **wrapper do cơ chế khởi tạo tạo ra**; `NoClassDefFoundError` có thể là **hậu quả về sau**.

## 2. Static field initializer và static block

Một lớp Java có thể khởi tạo các trường static và thực thi một hoặc nhiều khối static:

~~~java
class Settings {
    static int base = initializeBase();

    static {
        System.out.println("static block: base=" + base);
    }

    static int initializeBase() {
        System.out.println("field initializer");
        return 10;
    }
}
~~~

Đây là một lớp hợp lệ. Khi `Settings` được **khởi tạo lần đầu**, đầu ra từ phần initialization:

~~~text
field initializer
static block: base=10
~~~

Java thực hiện những static field initializers và static initializer blocks **theo thứ tự văn bản trong mã nguồn** khi khởi tạo lớp đó. Không hiểu nhầm rằng mọi trường static đều chạy trước tất cả khối static: **thứ tự khai báo** quyết định thứ tự thực hiện giữa hai loại.

Ví dụ:

~~~java
class Sequence {
    static int first = print("first", 1);

    static {
        System.out.println("middle");
    }

    static int second = print("second", 2);

    static int print(String label, int value) {
        System.out.println(label);
        return value;
    }
}
~~~

Thứ tự khi khởi tạo `Sequence` là:

~~~text
first
middle
second
~~~

**Constructor khác static initializer:** constructor chạy khi tạo một đối tượng; việc gọi method static hoặc đọc một trường static không phải constant cũng có thể khởi tạo lớp dù bạn không tạo đối tượng nào.

## 3. Tải, liên kết và khởi tạo là những bước khác nhau

Các khái niệm:

- **Loading:** JVM tìm hoặc tạo biểu diễn của một class/interface.
- **Linking:** kiểm tra, chuẩn bị các phần cần thiết, và có thể giải quyết các tham chiếu ký hiệu theo quy tắc JVM.
- **Initialization:** thực hiện phương thức khởi tạo lớp do JVM tổng hợp từ static field initializers và static blocks; trong bytecode thường được gọi là `<clinit>`.

`ExceptionInInitializerError` gắn với **giai đoạn initialization**, không nhất thiết có nghĩa file `.class` không được tìm thấy hoặc Java không parse được bytecode.

### Các kiểu sử dụng thường kích hoạt initialization

Một số tình huống *active use*:

- `new SomeClass()` khi lớp chưa được khởi tạo.
- Gọi một phương thức static được khai báo bởi class.
- Đọc hoặc ghi trường static không thuộc trường hằng (*constant variable*) được sử dụng theo cách đặc biệt.
- Một số hoạt động reflection, chẳng hạn `Class.forName("fully.qualified.ClassName")` với overload mặc định có yêu cầu initialization.

Ví dụ:

~~~java
class ActiveUse {
    static final int COMPILE_TIME_CONSTANT = 7;
    static int calculated = loadValue();

    static int loadValue() {
        System.out.println("ActiveUse initialized");
        return 42;
    }

    static void ping() {
        System.out.println("ping");
    }
}
~~~

Đọc `ActiveUse.calculated` hoặc gọi `ActiveUse.ping()` thường kích hoạt initialization nếu lớp chưa initialized.

### Class literal và compile-time constants

~~~java
Class<?> metadata = ActiveUse.class;
int number = ActiveUse.COMPILE_TIME_CONSTANT;
~~~

Đây là mảnh minh họa. Chỉ lấy class literal `ActiveUse.class` **không bắt buộc chạy initializer**. Đọc `COMPILE_TIME_CONSTANT` là một compile-time constant, có thể được biên dịch/inlined nên cũng **không yêu cầu class initialization** theo cơ chế đọc một static field bình thường.

Ngược lại:

~~~java
int value = ActiveUse.calculated;
~~~

là active use. Điều đó sẽ làm `loadValue()` chạy nếu lớp chưa được khởi tạo.

**Đừng suy diễn rằng cứ có `static final` thì bỏ khởi tạo.** Trường `static final` được tính bằng lời gọi method **không** phải compile-time constant:

~~~java
static final String CONFIG = readConfigUnchecked();
~~~

Nếu nằm trong một class chưa initialized, đọc `CONFIG` có thể kích hoạt công việc `readConfigUnchecked()` và dẫn đến initialization failure.

## 4. Luồng cơ bản khi initialization thành công hoặc thất bại

~~~text
Active use đầu tiên
        |
        v
Class đã initialized?
     |        |
     Có      Chưa
     |        |
Sử dụng      JVM thực hiện initialization
                 |
         +-------+-------+
         |               |
       Thành công       Thất bại
         |               |
Class initialized    Class erroneous
                         |
                  +------+------+
                  |             |
             Throwable       Throwable
             không là Error  thuộc Error
                  |             |
           EIIE bọc lại    Error có thể
           lỗi gốc        truyền trực tiếp
                         |
            Active use tiếp theo
                         |
                 NoClassDefFoundError
~~~

`EIIE` trong sơ đồ là viết tắt của `ExceptionInInitializerError`.

Một **sự thất bại ở chính quá trình khởi tạo** có hậu quả khác với một method static đã chạy **sau khi lớp initialized thành công** mà ném exception. Ví dụ một lớp hoàn toàn đã khởi tạo, rồi `static void query()` ném `IOException`/`IllegalStateException` trong một lần gọi thông thường, **không** vì thế bị JVM đánh dấu erroneous chỉ do exception của method.

## 5. RuntimeException thoát khỏi initializer thường bị bọc

Ví dụ:

~~~java
class FailingConfiguration {
    static final String VALUE = load();

    static String load() {
        throw new IllegalStateException("invalid configuration");
    }
}
~~~

Đây là class hợp lệ. Nếu một lần active use cần khởi tạo `FailingConfiguration`, method `load()` ném `IllegalStateException`. Ngoại lệ này là subtype của `RuntimeException`, **không thuộc `Error`**.

Theo quy tắc khởi tạo, JVM tạo một `ExceptionInInitializerError` để mang lỗi thất bại ban đầu. Caller có thể đọc:

~~~java
try {
    System.out.println(FailingConfiguration.VALUE);
} catch (ExceptionInInitializerError failure) {
    System.out.println(
            failure.getClass().getSimpleName());
    System.out.println(
            failure.getCause().getClass().getSimpleName());
}
~~~

Đầu ra mô tả hai tầng:

~~~text
ExceptionInInitializerError
IllegalStateException
~~~

**Đừng chỉ bắt `IllegalStateException` ở caller và nghĩ rằng nó sẽ nhận nguyên đối tượng ban đầu.** Trong trường hợp khởi tạo này, đối tượng được quan sát ở đường thất bại đầu tiên thường là `ExceptionInInitializerError`, với `IllegalStateException` làm cause.

### Nguyên nhân thật có thể sâu hơn

~~~java
static String load() {
    try {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("settings.txt"));
    } catch (java.io.IOException root) {
        throw new IllegalStateException(
                "cannot load settings", root);
    }
}
~~~

Đây là phương thức minh họa: nếu nó chạy trong static field initializer và đọc file thất bại, chain thường là:

~~~text
ExceptionInInitializerError
  └── cause -> IllegalStateException: cannot load settings
                  └── cause -> IOException: ...thất bại I/O...
~~~

Như vậy **`ExceptionInInitializerError` là ngữ cảnh về giai đoạn thất bại**, còn `IOException` sâu hơn có thể giải thích nguyên nhân vận hành cụ thể.

## 6. Nếu initializer ném `Error`, có bị bọc không?

**Không nhất thiết.** Quy tắc khởi tạo phân biệt lỗi đang truyền ra có thuộc `Error` hay không.

Ví dụ:

~~~java
class FailedAssertion {
    static {
        if (condition()) {
            throw new AssertionError("broken invariant");
        }
    }

    private static boolean condition() {
        return true;
    }

    static void touch() {
        System.out.println("unreachable");
    }
}
~~~

Class này biên dịch hợp lệ. Điều kiện `condition()` là lời gọi method, nên compiler vẫn nhìn thấy khả năng static block hoàn tất bình thường. Ở runtime, điều kiện trả `true` và một `AssertionError` được ném.

`AssertionError extends Error`, vì vậy lần đầu active use thường quan sát **chính `AssertionError`**; không bắt buộc có wrapper `ExceptionInInitializerError`.

**Không phải hướng thiết kế được khuyên cho lỗi cấu hình:** `AssertionError` mô tả một invariant nội bộ sai, không phù hợp để thay cho ngoại lệ checked/unchecked của những tình huống vận hành có thể phục hồi.

### Ví dụ trực tiếp `throw new ExceptionInInitializerError`

Code có thể tự ném:

~~~java
throw new ExceptionInInitializerError(
        new IllegalStateException("initialization failed"));
~~~

Đây là mảnh minh họa có thể đặt ở nơi `throw` hợp lệ. Đối tượng vốn đã thuộc `Error`, nên khi thoát static initializer, JVM không cần bọc thêm một EIIE nữa.

Tuy nhiên, thường **không cần tự tạo EIIE** trong code nghiệp vụ. Nếu code khởi tạo ném unchecked `IllegalStateException` với cause chính xác, JVM có cơ chế riêng báo lỗi class initialization. Đừng nhân đôi wrapper không cần thiết, trừ khi thiết kế rất đặc biệt cần điều đó.

## 7. Static initializer không có `throws` cho checked exception

Phương thức thông thường có thể khai báo:

~~~java
static String readFile() throws java.io.IOException {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
}
~~~

Nhưng nếu dùng trực tiếp trong static initializer mà không bắt:

~~~java
class InvalidStatic {
    static final String TEXT = readFile();

    static String readFile() throws java.io.IOException {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("config.txt"));
    }
}
~~~

**Class trên cố ý KHÔNG BIÊN DỊCH.** Biểu thức khởi tạo `TEXT` có thể ném checked `IOException` chưa được bắt.

Tương tự, không thể viết:

~~~java
static throws java.io.IOException {
    // Đây không phải cú pháp Java hợp lệ.
}
~~~

**Tại sao?** Static initializer không có chữ ký `throws` như method/constructor. Nếu việc khởi tạo lớp có khả năng ném checked exception, mã trong initializer phải **xử lý nó ngay tại đó** hoặc chuyển sang một kiến trúc không thực hiện công việc ấy trong initializer.

### Cách xử lý hợp lệ bằng try/catch

~~~java
class ConfigAtStartup {
    static final String TEXT;

    static {
        try {
            TEXT = java.nio.file.Files.readString(
                    java.nio.file.Path.of("config.txt"));
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(
                    "cannot initialize configuration", cause);
        }
    }
}
~~~

Class hợp lệ về mặt cú pháp/checked exceptions. Nếu file không đọc được:

1. `Files.readString` ném checked `IOException`.
2. `catch` bắt và chuyển thành unchecked `IllegalStateException` với cause gốc.
3. `IllegalStateException` thoát initializer.
4. JVM báo `ExceptionInInitializerError`, thường với `IllegalStateException` làm cause.
5. Định nghĩa `ConfigAtStartup` bị đánh dấu initialization failed, không sẵn sàng để tự khởi tạo lại ở active use tiếp theo.

**Đây là mẫu hợp lệ về Java nhưng có thể là thiết kế không phù hợp về vận hành**, nhất là khi lỗi đọc file có thể tạm thời và ứng dụng cần retry.

### Không dùng constructor `throws` để “sửa” static initializer

~~~java
class WrongMentalModel {
    WrongMentalModel() throws java.io.IOException {
        // Constructor có thể khai báo checked exception.
    }

    static {
        // Không vì constructor có throws mà code static
        // được phép thả IOException ra ngoài.
    }
}
~~~

Constructor chỉ quy định lời gọi tạo **instance**, không làm thay luật exception của quá trình initialization lớp.

## 8. Static initializer phải có khả năng hoàn tất bình thường

Java còn có một yêu cầu compile-time: một static initializer **phải có khả năng hoàn tất bình thường** (*able to complete normally*).

Đoạn sau **không biên dịch**:

~~~java
class AlwaysFails {
    static {
        throw new IllegalStateException("always fails");
    }
}
~~~

Dù `IllegalStateException` là unchecked, một khối `static` chỉ có lệnh `throw` vô điều kiện không thể hoàn tất bình thường theo phân tích luồng của compiler.

Để mô tả một lỗi runtime có điều kiện:

~~~java
class RuntimeFailure {
    static {
        if (shouldFail()) {
            throw new IllegalStateException("runtime failure");
        }
    }

    private static boolean shouldFail() {
        return true;
    }
}
~~~

Đoạn này **có thể biên dịch**: với trình biên dịch, `shouldFail()` không phải hằng số false/true được suy ra như một constant expression cho nhánh if; khối có đường có thể hoàn tất bình thường. Tuy nhiên, khi chạy nó vẫn luôn thất bại.

Đây chỉ là ví dụ **phân biệt compile-time flow analysis và runtime behavior**, không phải lời khuyên cố ý phá một static initializer.

## 9. Sau lỗi khởi tạo: lớp ở trạng thái erroneous

Đây là hậu quả làm `ExceptionInInitializerError` nguy hiểm đối với cấu hình và tài nguyên dễ thất bại.

Khi initialization của một **định nghĩa lớp trong một class loader** kết thúc bất thường, JVM đánh dấu nó là **erroneous**. Điều đó không bị xóa khi caller bắt được EIIE.

Ví dụ khái niệm:

~~~java
try {
    Unstable.value(); // Lần đầu: EIIE.
} catch (ExceptionInInitializerError e) {
    System.out.println("first initialization failed");
}

try {
    Unstable.value(); // Lần sau: NoClassDefFoundError.
} catch (NoClassDefFoundError e) {
    System.out.println("class could not be initialized");
}
~~~

Đoạn trên là minh họa với class `Unstable` chưa được định nghĩa ở đây. Các biến thể loại lỗi tùy nguyên nhân ban đầu; nếu ban đầu initializer ném `Error`, caller có thể thấy `Error` đó thay vì EIIE.

**Không tự động khởi tạo lại:** một file cấu hình vừa được sửa hoặc một dịch vụ mạng vừa hoạt động trở lại **không** làm initializer của class erroneous chạy lại.

### Phân biệt với lỗi trong method static sau initialization

~~~java
class RetryableOperation {
    static int calls;

    static String load(boolean ready) {
        calls++;
        if (!ready) {
            throw new IllegalStateException("temporarily unavailable");
        }
        return "ready";
    }
}
~~~

Ở đây initialization của `RetryableOperation` thành công (không có static initializer dễ lỗi). Một lần gọi `load(false)` ném exception **trong method thông thường**, không đánh dấu cả class erroneous. Có thể gọi lại `load(true)` và nhận kết quả thành công nếu hợp đồng cho phép.

**Khác biệt cốt lõi:** “Exception xảy ra trong một static method” **không giống** “Exception xảy ra trong quá trình class initialization”. Tên `static` xuất hiện trong cả hai trường hợp nhưng thời điểm và hậu quả JVM hoàn toàn khác nhau.

## 10. `NoClassDefFoundError` sau lần thất bại không hẳn do thiếu JAR

`NoClassDefFoundError` có thể xuất hiện khi JVM không thể tìm/định nghĩa một class cần thiết lúc runtime, chẳng hạn thiếu dependency. Nhưng nó cũng có thể xuất hiện vì:

- Class đã được load/linked thành công.
- Một lần initialization của class đó trước đây **thất bại**.
- Lần active use sau cố dùng định nghĩa class erroneous ấy.

Do đó, khi gặp log:

~~~text
java.lang.NoClassDefFoundError:
    Could not initialize class com.example.ConfigurationHolder
~~~

đừng tự động kết luận “thiếu `ConfigurationHolder.class`”. Hãy tìm trong log **lần đầu** lớp này được dùng: thường sẽ có `ExceptionInInitializerError` hoặc một `Error` ban đầu, kèm cause chain về nguyên nhân.

### Vì sao hai lần gọi cho loại lỗi khác nhau?

| Lần sử dụng | Trạng thái class | Kết quả |
| --- | --- | --- |
| Lần đầu cần initialization | Chưa initialized | Thử chạy initializers; có thể thành công hoặc thất bại |
| Đang chạy initializer và ném non-`Error` | Initialization thất bại | Thường báo `ExceptionInInitializerError` |
| Đang chạy initializer và ném `Error` | Initialization thất bại | Có thể báo nguyên đối tượng `Error` |
| Lần active use tiếp sau, cùng definition/class loader | Erroneous | Thường báo `NoClassDefFoundError` |
| Method static thất bại sau class initialized | Initialized thành công | Exception của method, **không tự làm class erroneous** |

Không dựa vào message `NoClassDefFoundError` như một chuỗi chính xác giữa các phiên bản JVM. **Phân loại dựa trên thời điểm, class name, cause và log lần đầu.**

## 11. Một ví dụ Java 17+ độc lập, không cần database hoặc file ngoài

Chương trình `InitializationFailureDemo.java` sau có năm mục tiêu:

1. `Good` khởi tạo thành công; static initializers chỉ chạy một lần.
2. Đọc compile-time constant hoặc class literal không tự làm `Good` chạy initializer.
3. `BrokenWithRuntime` có một `IOException` giả lập được bọc thành `IllegalStateException` ở initializer, rồi JVM báo `ExceptionInInitializerError`.
4. `BrokenWithError` ném `AssertionError` trong static block; lần đầu nhận `AssertionError` trực tiếp, lần sau gặp `NoClassDefFoundError`.
5. `RecoverableLoader` cho thấy I/O mô phỏng có thể thử lại trong **method thường**, không làm class erroneous.

Tạo tệp **`InitializationFailureDemo.java`**:

~~~java
import java.io.IOException;

public class InitializationFailureDemo {

    static final class Good {
        static final int MAGIC = 11;
        static int count = initializeCount();

        static {
            System.out.println("Good.staticBlock");
        }

        static int initializeCount() {
            System.out.println("Good.fieldInitializer");
            return 7;
        }

        static int count() {
            return count;
        }
    }

    static final class BrokenWithRuntime {
        static final String CONFIG = loadDuringInitialization();

        static String loadDuringInitialization() {
            System.out.println("Runtime.initializer");
            try {
                simulateFileLoad(false);
                return "ok";
            } catch (IOException cause) {
                throw new IllegalStateException(
                        "configuration unavailable", cause);
            }
        }

        static String config() {
            return CONFIG;
        }
    }

    static final class BrokenWithError {
        static {
            System.out.println("Error.staticBlock");
            if (shouldFail()) {
                throw new AssertionError("broken invariant");
            }
        }

        static boolean shouldFail() {
            return true;
        }

        static void ping() {
            System.out.println("Error.ping");
        }
    }

    static final class RecoverableLoader {
        static int calls;

        static String load(boolean available) throws IOException {
            calls++;
            simulateFileLoad(available);
            return "configuration ready";
        }
    }

    public static void main(String[] args) {
        System.out.println("== passive reference ==");
        System.out.println("literal="
                + Good.class.getSimpleName());
        System.out.println("constant=" + Good.MAGIC);

        System.out.println("== successful initialization ==");
        System.out.println("goodCount=" + Good.count());
        System.out.println("goodAgain=" + Good.count());

        System.out.println("== runtime failure first use ==");
        try {
            BrokenWithRuntime.config();
        } catch (ExceptionInInitializerError error) {
            System.out.println("first="
                    + error.getClass().getSimpleName());
            Throwable wrapped = error.getCause();
            System.out.println("cause="
                    + wrapped.getClass().getSimpleName()
                    + ": " + wrapped.getMessage());
            Throwable root = wrapped.getCause();
            System.out.println("root="
                    + root.getClass().getSimpleName()
                    + ": " + root.getMessage());
        }

        System.out.println("== runtime failure second use ==");
        try {
            BrokenWithRuntime.config();
        } catch (NoClassDefFoundError error) {
            System.out.println("second="
                    + error.getClass().getSimpleName());
        }

        System.out.println("== Error failure first use ==");
        try {
            BrokenWithError.ping();
        } catch (AssertionError error) {
            System.out.println("firstError="
                    + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }

        System.out.println("== Error failure second use ==");
        try {
            BrokenWithError.ping();
        } catch (NoClassDefFoundError error) {
            System.out.println("secondError="
                    + error.getClass().getSimpleName());
        }

        System.out.println("== regular method can retry ==");
        try {
            RecoverableLoader.load(false);
        } catch (IOException error) {
            System.out.println("loadFailed="
                    + error.getMessage());
        }
        try {
            System.out.println("loadSuccess="
                    + RecoverableLoader.load(true));
        } catch (IOException error) {
            System.out.println("unexpected retry failure");
        }
        System.out.println("loadCalls=" + RecoverableLoader.calls);
    }

    static void simulateFileLoad(boolean available)
            throws IOException {
        if (!available) {
            throw new IOException("file unavailable");
        }
    }
}
~~~

Biên dịch bằng JDK 17 trở lên:

~~~bash
javac InitializationFailureDemo.java
java InitializationFailureDemo
~~~

**Stdout chính xác:**

~~~text
== passive reference ==
literal=Good
constant=11
== successful initialization ==
Good.fieldInitializer
Good.staticBlock
goodCount=7
goodAgain=7
== runtime failure first use ==
Runtime.initializer
first=ExceptionInInitializerError
cause=IllegalStateException: configuration unavailable
root=IOException: file unavailable
== runtime failure second use ==
second=NoClassDefFoundError
== Error failure first use ==
Error.staticBlock
firstError=AssertionError: broken invariant
== Error failure second use ==
secondError=NoClassDefFoundError
== regular method can retry ==
loadFailed=file unavailable
loadSuccess=configuration ready
loadCalls=2
~~~

### Vì sao `Good.class` không in phần khởi tạo?

`Good.class` lấy class literal. Việc gọi `getSimpleName()` trên đối tượng `Class` để lấy tên không tự yêu cầu chạy `Good` static initialization. `MAGIC` lại là compile-time constant, nên việc đọc `Good.MAGIC` không yêu cầu khởi tạo lớp theo cách đọc một static field thông thường.

Lần đầu `Good.count()` mới yêu cầu initialization; `count = initializeCount()` chạy, sau đó static block chạy theo thứ tự văn bản. Lần gọi thứ hai chỉ thực hiện method, không khởi tạo lại.

### Vì sao `BrokenWithRuntime` cho thấy ba tầng?

`simulateFileLoad(false)` ném checked `IOException`. Nó được **bắt trong helper** rồi bọc thành `IllegalStateException` có cause là chính IOException. Khi helper chạy trong static field initializer của `BrokenWithRuntime`, unchecked `IllegalStateException` thoát ra. JVM báo `ExceptionInInitializerError` với:

~~~text
ExceptionInInitializerError
    └── IllegalStateException: configuration unavailable
          └── IOException: file unavailable
~~~

`getCause()` trên EIIE trả wrapper; đọc tiếp `getCause()` của wrapper trả IOException. Nếu bỏ constructor truyền cause, thông tin sâu nhất có thể bị mất.

### Vì sao lần gọi lại không in `Runtime.initializer` nữa?

Class đã initialization failed và ở trạng thái erroneous trong class loader ấy. JVM **không** chạy lại `loadDuringInitialization()`. Lần dùng sau báo `NoClassDefFoundError`.

### Vì sao `BrokenWithError` không có `ExceptionInInitializerError` ở lần đầu?

`AssertionError` đã thuộc nhánh `Error`. JVM có thể truyền trực tiếp nó khi initialization thất bại. Lần sau class vẫn erroneous, nên báo `NoClassDefFoundError`.

### Vì sao `RecoverableLoader` có thể thử lại?

`RecoverableLoader` khởi tạo class thành công; I/O xảy ra trong method `load()` sau đó. Lỗi `IOException` của một lần gọi không làm cả class erroneous. Caller có thể quyết định thử lại có giới hạn khi môi trường thay đổi. Ví dụ giả lập `available` để kết quả không phụ thuộc file hay mạng thật; **không phải chính sách retry chung cho mọi thao tác ghi dữ liệu**.

## 12. Constructor thất bại không giống class initialization thất bại

Xét:

~~~java
class CustomerConnection {
    CustomerConnection(boolean available)
            throws IOException {
        if (!available) {
            throw new IOException("connection not available");
        }
    }
}
~~~

Đây là class hợp lệ khi import `java.io.IOException`. Khi:

~~~java
new CustomerConnection(false);
~~~

ném `IOException`, điều đó không tự đánh dấu `CustomerConnection` là erroneous nếu quá trình initialization của class đã thành công. Có thể thử tạo một instance mới với input khác, tùy hợp đồng constructor.

Bảng so sánh:

| Khía cạnh | Static initializer thất bại | Constructor thất bại |
| --- | --- | --- |
| Thời điểm | JVM initialize class | Tạo instance sau khi class được initialize |
| Có thể khai báo `throws IOException` trực tiếp? | Không | Có |
| Có thể làm class erroneous? | Có | Không chỉ vì constructor ném lỗi |
| Gọi lại trong cùng class loader | Thường `NoClassDefFoundError` | Có thể thử `new` lại |
| Phạm vi trạng thái thất bại | Định nghĩa class | Lần tạo instance |
| Dùng để tải cấu hình cần retry | Thường kém phù hợp | Constructor/factory có thể phù hợp tùy thiết kế |

Có một sắc thái: nếu `new` lần đầu kích hoạt **class initialization thất bại trước khi vào constructor**, caller vẫn nhận lỗi initialization như đã học. Vì vậy không được suy ra từ cú pháp `new` rằng mọi lỗi quan sát được đều phát sinh bên trong constructor.

## 13. Factory/loader có checked exception là lựa chọn tốt khi cần phục hồi

Thay vì:

~~~java
class StaticConfig {
    static final String TEXT = readFileUnchecked();
}
~~~

có thể dùng API bình thường:

~~~java
final class Configuration {
    private final String text;

    private Configuration(String text) {
        this.text = text;
    }

    static Configuration load(java.nio.file.Path file)
            throws java.io.IOException {
        return new Configuration(
                java.nio.file.Files.readString(file));
    }

    String text() {
        return text;
    }
}
~~~

Lớp Java này biên dịch độc lập khi đặt trong một tệp Java thích hợp. `Configuration.load(path)` thực hiện I/O **trong lời gọi method**, có `throws IOException` để caller được trình biên dịch nhắc xử lý.

Lợi ích:

- Caller có thể báo lỗi, giới hạn retry, dùng fallback được phép hoặc yêu cầu người dùng sửa đường dẫn.
- Một lần đọc file lỗi không tự phá trạng thái khởi tạo của cả lớp.
- Có thể truyền cấu hình qua constructor/dependency injection và kiểm thử bằng data giả lập.
- Phân biệt rõ lỗi của lần **nạp dữ liệu** với lỗi của chính JVM **khởi tạo class**.
- Có thể xây chiến lược quản lý vòng đời và đóng tài nguyên có kiểm soát.

Nếu không muốn public API phụ thuộc vào `IOException`, có thể bọc ở ranh giới rõ ràng bằng custom checked exception nhận cause. Nhưng không nên đánh mất nguyên nhân gốc.

### Khi nào static initializer vẫn phù hợp?

Đối với hằng số, lookup table thuần túy và những cấu trúc:

- Không phụ thuộc trạng thái mạng/tệp dễ thay đổi.
- Có kết quả xác định từ dữ liệu nội bộ hợp lệ.
- Hoàn thành nhanh và ít side effect.
- Không có yêu cầu retry động.
- Không phụ thuộc vòng chờ các thread khác.

Ví dụ:

~~~java
class Constants {
    static final int DEFAULT_PORT = 8080;
    static final java.util.List<String> LEVELS =
            java.util.List.of("LOW", "MEDIUM", "HIGH");
}
~~~

Đây là những trạng thái tĩnh đơn giản hơn nhiều so với mở kết nối database lúc load class.

## 14. Lazy static holder có giải quyết retry không?

Mẫu *initialization-on-demand holder*:

~~~java
class SingletonService {
    private static class Holder {
        static final SingletonService INSTANCE =
                createSingleton();
    }

    static SingletonService instance() {
        return Holder.INSTANCE;
    }

    private static SingletonService createSingleton() {
        return new SingletonService();
    }
}
~~~

Class hợp lệ. `Holder` thường được khởi tạo khi `instance()` lần đầu cần `Holder.INSTANCE`. Đây là kỹ thuật lazy initialization hữu ích cho trạng thái tạo một lần an toàn.

**Nhưng không phải cơ chế retry.** Nếu `createSingleton()` ném exception trong lúc `Holder` khởi tạo, `Holder` có thể bị đánh dấu erroneous. Những lần đọc `Holder.INSTANCE` sau thường gặp `NoClassDefFoundError`.

Đừng hiểu “lazy” là “mỗi lần thất bại sẽ thử tạo lại”. Nếu cần kết nối lại hoặc refresh cấu hình, thiết kế lifecycle riêng với state machine, factory, cache invalidation hoặc provider có khả năng retry có giới hạn.

## 15. `getCause()` và `getException()` của `ExceptionInInitializerError`

`ExceptionInInitializerError` có các API:

~~~java
Throwable cause = error.getCause();
Throwable original = error.getException();
~~~

Đây là mảnh minh họa với `error` kiểu `ExceptionInInitializerError`.

`getException()` là một API lịch sử để truy xuất ngoại lệ được lưu khi tạo wrapper. Trong mã hiện đại, `getCause()` thường phù hợp hơn vì nhất quán với cách duyệt cause chain trên `Throwable`.

Ví dụ:

~~~java
IllegalStateException root =
        new IllegalStateException("configuration invalid");

ExceptionInInitializerError error =
        new ExceptionInInitializerError(root);

System.out.println(error.getCause() == root); // true
System.out.println(error.getException() == root); // true
~~~

**Không giả định EIIE bất kỳ luôn có cause khác null.** Code có thể tự tạo:

~~~java
ExceptionInInitializerError empty =
        new ExceptionInInitializerError();

System.out.println(empty.getCause() == null); // true
~~~

Một phương thức logger tổng quát cần kiểm tra null trước khi gọi `getClass()`/`getMessage()` của cause.

### Không bắt EIIE rồi thay bằng thông báo vô nghĩa

Không nên chỉ viết:

~~~java
catch (ExceptionInInitializerError e) {
    System.err.println("Cannot start");
}
~~~

ở một phạm vi lớn rồi bỏ qua. Lỗi gốc có thể là `NullPointerException` từ static field initializer, thiếu cấu hình, xung đột phiên bản hoặc thao tác I/O. Nếu chỉ in một thông báo chung, bạn mất dữ liệu để chẩn đoán.

Nếu ở ranh giới startup thực sự cần báo lỗi, hãy ghi lại cause chain an toàn, xác định chính sách dừng/khởi động lại và **không tiếp tục sử dụng lớp đã erroneous**.

## 16. `finally` và try-with-resources khi initialization thất bại

Một `Throwable` ném trong static initializer vẫn tuân theo quy tắc dọn dẹp của các cấu trúc Java khi chương trình còn thực thi luồng rời scope bình thường.

~~~java
class CleanupDuringInit {
    static {
        try {
            System.out.println("initializing");
            fail();
        } finally {
            System.out.println("cleanup");
        }
    }

    private static void fail() {
        throw new IllegalStateException("cannot initialize");
    }
}
~~~

Khi JVM chạy initializer của `CleanupDuringInit`:

~~~text
initializing
cleanup
~~~

được in trước khi lỗi thoát khỏi quá trình initialization và thường bị bọc thành `ExceptionInInitializerError`.

Dọn dẹp trong `finally` **không sửa trạng thái erroneous**. Một class có thể đã giải phóng mọi tài nguyên đang mở mà vẫn không được JVM khởi tạo lại sau thất bại.

### Suppressed exceptions trong try-with-resources

Ví dụ:

~~~java
class FailingResource implements AutoCloseable {
    @Override
    public void close() throws java.io.IOException {
        throw new java.io.IOException("close failed");
    }
}
~~~

Nếu code trong static initializer có một try-with-resources với `FailingResource` và thân `try` ném lỗi chính, Java có thể gắn lỗi `close()` thành **suppressed exception** trên lỗi chính. Nếu lỗi chính là `IllegalStateException` và JVM bọc bằng EIIE, suppressed của lỗi đóng nằm trên **lỗi chính** đó, không tự trở thành cause trực tiếp của EIIE.

Khi chẩn đoán, cần phân biệt:

- `getCause()`: chuỗi nguyên nhân từ wrapper đến lỗi gốc.
- `getSuppressed()`: những lỗi phụ khi dọn dẹp.
- `ExceptionInInitializerError`: ngữ cảnh class initialization thất bại.

**Không nên chỉ in EIIE message rồi bỏ qua suppressed/cause** nếu đang phân tích một lỗi khởi tạo tài nguyên phức tạp.

## 17. Đa luồng và class initialization lock

JVM có cơ chế đồng bộ hóa khởi tạo class để các thread không tự chạy cùng initializer độc lập như nhiều lần khởi tạo riêng.

Thông thường:

- Một thread bắt đầu initialization cho một class.
- Thread khác cần active use class đó có thể phải chờ kết quả initialization.
- Sau khi khởi tạo thành công, những thread cần class ấy có thể dùng trạng thái đã initialized theo các bảo đảm của JVM.
- Nếu khởi tạo thất bại, class trở thành erroneous; thread đang chờ hoặc thread sau đó không tự chạy lại initializer.
- Một static initializer mất nhiều thời gian hoặc chờ I/O có thể gây chậm startup, latency spike hoặc deadlock khi có vòng phụ thuộc.

### Vòng chờ khi initializer đợi thread khác

Giả sử static initializer bắt đầu một worker thread rồi `join()`, trong khi worker lại cần gọi method static của **chính class còn đang được khởi tạo**. Worker có thể phải chờ initialization hoàn tất, còn thread khởi tạo chờ worker hoàn tất — tạo deadlock.

Không cần một ví dụ đa luồng nguy hiểm chạy thực để hiểu quy tắc. Thực hành nên tránh khởi động workflow phức tạp, block chờ mạng hoặc lấy nhiều lock trong static initializer.

### Circular initialization có thể phơi giá trị mặc định

Với hai class phụ thuộc nhau qua các trường static, một quá trình initialization đệ quy có thể quan sát **giá trị mặc định đã được chuẩn bị** của một trường chưa chạy initializer của nó trong một số cấu trúc. Điều này **không nhất thiết ném EIIE** nhưng có thể gây bug khó hiểu như số `0` hoặc tham chiếu `null` bất ngờ.

Vì vậy, đừng xây một đồ thị static initializers phụ thuộc chéo phức tạp rồi chỉ dựa vào việc “JVM sẽ xử lý đúng”. Giảm vòng phụ thuộc, dùng factory rõ ràng và tránh side effect là những lựa chọn dễ kiểm thử hơn.

## 18. Đọc stack trace: tìm lỗi đầu tiên, không chỉ dòng cuối

Một bản log có thể trông như sau:

~~~text
java.lang.ExceptionInInitializerError
    at com.example.App.main(App.java:...)
Caused by: java.lang.IllegalStateException: cannot load configuration
    at com.example.ConfigHolder.load(ConfigHolder.java:...)
Caused by: java.io.IOException: file unavailable
    at ...
~~~

Đây là **mẫu cấu trúc log minh họa**, không phải stack trace byte-for-byte của một lần chạy cụ thể.

Sau đó, các request khác có thể ghi:

~~~text
java.lang.NoClassDefFoundError:
    Could not initialize class com.example.ConfigHolder
~~~

Nếu chỉ đọc log mới nhất, dễ chẩn đoán nhầm rằng không có class trên classpath. Việc cần làm là **tìm sự kiện đầu tiên** đưa class vào trạng thái erroneous.

### Quy trình điều tra thực tế

1. Ghi lại **class name** xuất hiện trong EIIE hoặc `NoClassDefFoundError`.
2. Tìm lần đầu class được active use, đặc biệt khi startup.
3. Mở stack trace đầu tiên để tìm `<clinit>`, static field initializer hoặc static block.
4. Duyệt `getCause()`, chú ý `NullPointerException`, `IllegalStateException`, `IOException`, lỗi parse và lỗi dependency.
5. Kiểm tra `getSuppressed()` của các throwable liên quan nếu dùng try-with-resources.
6. Kiểm tra các static fields/blocks **theo thứ tự khai báo**.
7. Xác định có phụ thuộc I/O, hệ thống bên ngoài, môi trường hoặc chu trình class initialization không.
8. Kiểm tra các thread đang chờ class initialization khi nghi deadlock.
9. Phân biệt class thực sự **thiếu lúc runtime** với class **có mặt nhưng initialization failed**.
10. Chọn cách sửa nguồn lỗi, không chỉ thêm `catch (Error)`.
11. Nếu muốn retry, chuyển thao tác dễ thất bại sang factory/provider được quản lý; không gọi lại một class erroneous.
12. Ghi log có cấu trúc và lọc dữ liệu nhạy cảm nếu cause/message có đường dẫn, token hay chuỗi kết nối.

Một lỗi như `ExceptionInInitializerError` **không tự cho biết lỗi nghiệp vụ nằm ở đâu**; nó là tín hiệu rằng cần xem quá trình initialization và lỗi gốc.

## 19. Vì sao không nên tự động catch `Error` để “cứu hệ thống”?

Có thể viết:

~~~java
try {
    CriticalConfig.initialize();
} catch (Error e) {
    System.err.println("initialization problem");
}
~~~

Đây là mảnh minh họa với `CriticalConfig` giả định. Nhưng handler chỉ in log không biến class erroneous thành class initialized. Những lời gọi khác vẫn có thể thất bại và hệ thống tiếp tục ở trạng thái thiếu dependency quan trọng.

`catch (Throwable)` còn rộng hơn, có thể nhận `OutOfMemoryError`, `StackOverflowError` và các lỗi mà ứng dụng không đủ điều kiện phục hồi an toàn. **Không dùng catch rộng làm cơ chế mặc định cho mọi lỗi initialization**.

Có những ranh giới chuyên biệt như bootstrap hoặc test runner muốn ghi nhận lỗi startup. Trong trường hợp đó, có thể log theo chính sách rồi dừng/quản lý lại vòng đời, chứ không tùy tiện báo thành công cho những thao tác cần class chưa initialized.

## 20. Kiểm thử static initialization failure

Kiểm thử loại lỗi này khó hơn kiểm thử một method bình thường vì **một class definition khởi tạo thất bại không tự reset** giữa các lần gọi trong cùng class loader.

### Tránh test dựa vào thứ tự ngẫu nhiên

Nếu hai test cùng dùng một class có static initializer ném lỗi:

- Test gọi trước có thể nhận `ExceptionInInitializerError` (hoặc một `Error` gốc).
- Test gọi sau có thể nhận `NoClassDefFoundError`.
- Kết quả dễ phụ thuộc thứ tự chạy và việc test runner tái sử dụng JVM/class loader.

Đừng viết hai test với giả định mỗi lần gọi cùng class đều “khởi tạo từ đầu”.

### Cách kiểm thử ổn định hơn

- Chạy các ca trong **JVM subprocess riêng** nếu bắt buộc kiểm chứng initialization failure một cách cách ly.
- Hoặc dùng **class loader riêng** khi đã nắm rõ class loading/parent delegation, nhưng đây là lựa chọn phức tạp.
- Tốt hơn trong thiết kế ứng dụng: chuyển thao tác dễ thất bại sang **factory/method có thể gọi lại**, rồi viết unit test thông thường cho nhánh success/failure.
- Test nguyên nhân ban đầu và chính sách fallback ở tầng sở hữu vòng đời, không chỉ test loại wrapper.

Ví dụ chương trình `InitializationFailureDemo.java` cố tình giữ thứ tự **first use → second use** trong một lần chạy duy nhất, nên stdout không phụ thuộc trạng thái của test trước.

## 21. Những nhầm lẫn thường gặp

### 21.1. “EIIE có nghĩa Java không tìm thấy file .class”

Không. EIIE phản ánh **khởi tạo thất bại**. Không tìm thấy class ở runtime là một lớp vấn đề khác có thể dẫn đến `NoClassDefFoundError`.

### 21.2. “Hễ method static ném RuntimeException thì lớp erroneous”

Không. Nếu class đã initialized, exception của method bình thường không tự phá trạng thái class.

### 21.3. “Mọi lỗi trong static đều bị bọc thành EIIE”

Không. `Error` hoặc subclass có thể được truyền trực tiếp ở lần đầu.

### 21.4. “Static initializer có thể ghi `throws IOException`”

Không. Cần bắt checked exception hoặc chuyển công việc sang method phù hợp.

### 21.5. “Constructor khai báo throws thay cho khối static”

Không. Constructor và class initialization thuộc các pha khác nhau.

### 21.6. “Một static block có một câu `throw` vô điều kiện vẫn biên dịch nếu unchecked”

Không. Static initializer còn phải **có khả năng hoàn tất bình thường** theo compile-time analysis.

### 21.7. “Bắt EIIE thì lần sau class tự initialized lại”

Không. Cùng class definition/class loader, class erroneous không được tự chạy initializer lần nữa.

### 21.8. “Sau EIIE, thiếu JAR là nguyên nhân chắc chắn”

Không. `NoClassDefFoundError` về sau có thể do initialization failed trước đó. Phải tìm log đầu tiên.

### 21.9. “`catch (Exception)` sẽ bắt cả EIIE”

Không. EIIE kế thừa `Error`.

### 21.10. “Chỉ cần in EIIE.getMessage() để có nguyên nhân”

Không. Cause chain thường là thông tin chính. Message có thể không đủ và không phải hợp đồng ổn định.

### 21.11. “EIIE.getCause() luôn khác null”

Không. Code có thể tự tạo EIIE không có cause. Viết logger null-safe.

### 21.12. “Lazy holder tự động hỗ trợ retry”

Không. Holder cũng là class chịu luật erroneous khi initialization failed.

### 21.13. “`static final` luôn không kích hoạt class initialization”

Không. Chỉ compile-time constant có ngữ nghĩa đặc biệt; static final object hoặc giá trị từ method vẫn có thể đòi khởi tạo.

### 21.14. “Class literal luôn làm chạy static block”

Không. `SomeClass.class` tự nó không đòi class initialization.

### 21.15. “`finally` chạy xong là class hết erroneous”

Không. Cleanup và trạng thái initialization là hai khái niệm riêng.

### 21.16. “Lỗi đóng tài nguyên là cause trực tiếp của EIIE”

Không nhất thiết. Nếu thân try đã ném lỗi chính, lỗi đóng thường được suppressed trên lỗi chính theo quy tắc try-with-resources.

### 21.17. “I/O trong static initializer là cách tối ưu để có singleton an toàn”

Không. Nó có thể gây lỗi khó retry, thời gian startup dài hoặc deadlock; dùng factory/lifecycle khi tài nguyên cần phục hồi.

### 21.18. “EIIE là checked vì có từ Exception trong tên”

Không. Phân cấp xác định tính checked/unchecked; EIIE thuộc `Error`.

### 21.19. “Static initialization failure và constructor failure giống nhau”

Không. Constructor ném exception không tự làm class erroneous nếu class đã initialized.

### 21.20. “Không cần xem log trước khi class lỗi”

Sai. Các lỗi tiếp theo thường chỉ còn `NoClassDefFoundError`; lỗi đầu tiên quan trọng nhất.

## 22. Checklist thiết kế tránh EIIE trong ứng dụng

Trước khi thêm công việc vào static initializer, tự hỏi:

1. Công việc có **thể thất bại tạm thời** (file, network, database, DNS) không?
2. Nếu thất bại, caller có cần **retry có giới hạn** không?
3. Có thể **đưa vào factory/loader** với `throws` thích hợp không?
4. Static field có thực sự là compile-time constant hoặc dữ liệu thuần túy không?
5. Mọi static initializer có chạy nhanh, không block và ít side effect không?
6. Có chu trình phụ thuộc giữa các static fields/classes không?
7. Có khả năng deadlock do chờ thread khác trong lúc class initialization không?
8. Nếu một lớp phụ thuộc không initialized được, ứng dụng nên dừng hoặc degrade theo chính sách nào?
9. Có log nguyên nhân gốc mà không lộ dữ liệu nhạy cảm không?
10. Có unit test kiểm tra cơ chế retry ở **method** thay vì cố reset trạng thái class không?
11. Có dùng try-with-resources đúng quyền sở hữu tài nguyên không?
12. Có hiểu khác biệt giữa `getCause()` và `getSuppressed()` trong một lỗi khởi tạo phức tạp không?
13. Có phân biệt `NoClassDefFoundError` do thiếu class với do erroneous initialization không?
14. Có tránh bắt `Error` rộng rồi im lặng tiếp tục không?

Một static initializer tốt thường khởi tạo trạng thái **đơn giản, có thể dự đoán**, không phải mở một kết nối sống phụ thuộc nhiều yếu tố bên ngoài.

## 23. Câu hỏi tự kiểm tra

1. `ExceptionInInitializerError` thuộc nhánh `Error` hay `Exception`?
2. Nó là checked hay unchecked?
3. Hai loại đoạn mã nào thường tham gia khởi tạo static?
4. Static field initializer và static block có chạy theo thứ tự khai báo không?
5. Mọi lần gọi constructor có chạy lại static initializer không?
6. Đọc một compile-time constant có nhất thiết khởi tạo class không?
7. Lấy `SomeClass.class` có tự chạy static block không?
8. Gọi phương thức static có thể kích hoạt initialization không?
9. Khi static initializer ném `IllegalStateException` ra ngoài, caller lần đầu thường thấy gì?
10. `getCause()` của EIIE có thể chứa đối tượng nào?
11. Nếu lỗi gốc của wrapper là `IOException`, nên giữ nó bằng constructor gì?
12. Khi static initializer ném trực tiếp `AssertionError`, có bắt buộc JVM bọc EIIE không?
13. Tại sao static initializer không thể khai báo `throws IOException`?
14. Một static block chỉ có `throw new RuntimeException()` vô điều kiện có biên dịch không?
15. Sau lần initialization failed, active use tiếp theo thường thấy loại lỗi nào?
16. Khi đã bắt EIIE, class có tự thử chạy initializer lại không?
17. `NoClassDefFoundError` có luôn có nghĩa thiếu dependency không?
18. Một method static ném `IOException` sau class initialized có làm class erroneous không?
19. Constructor ném exception có tự đánh dấu class erroneous không?
20. Khi cần đọc cấu hình có thể retry, nên chọn static initializer hay factory?
21. Lazy static holder có tự động retry khi holder initialization failed không?
22. Vì sao `catch (Exception)` không bắt được EIIE?
23. `getException()` trên EIIE có liên quan gì tới `getCause()`?
24. Có thể tồn tại EIIE không có cause không?
25. Lỗi `close()` trong try-with-resources thường nằm ở đâu nếu thân try đã ném lỗi chính?
26. `finally` thành công có sửa trạng thái erroneous không?
27. Tại sao nên tránh side effect/I/O lâu trong class initialization?
28. Cần tìm log nào khi các request sau chỉ ghi `NoClassDefFoundError`?
29. Trong cùng một class loader, chạy test thứ nhất làm class erroneous có thể ảnh hưởng test thứ hai không?
30. Phân biệt câu “static method thất bại” với “class initialization thất bại” bằng một ví dụ.

### Gợi ý đáp án

1. `Error`, qua `LinkageError`.
2. Unchecked.
3. Static field initializers và static initializer blocks.
4. Có, theo thứ tự văn bản khi JVM initialize class.
5. Không.
6. Không.
7. Không tự yêu cầu initialization.
8. Có, nếu là active use và class chưa initialized.
9. Thường `ExceptionInInitializerError`.
10. Ngoại lệ không phải `Error` ban đầu, ví dụ `IllegalStateException`.
11. `super(message, cause)` trong wrapper hoặc constructor thích hợp nhận cause.
12. Không; một `Error` có thể được truyền trực tiếp.
13. Không có cú pháp/khai báo checked `throws` cho static initializer.
14. Không, vì block phải có khả năng hoàn tất bình thường.
15. Thường `NoClassDefFoundError`.
16. Không.
17. Không; cũng có thể do class initialization đã failed trước đó.
18. Không; exception của lời gọi method thường không tự phá trạng thái class.
19. Không nếu class initialization thành công.
20. Factory/loader có `throws` và chính sách retry hợp lệ.
21. Không.
22. EIIE thuộc `Error`, không thuộc `Exception`.
23. Cả hai giúp truy xuất nguyên nhân đã được lưu khi wrapper được tạo phù hợp; `getCause()` là API hiện đại.
24. Có.
25. Trong `getSuppressed()` của lỗi chính.
26. Không.
27. Vì có thể kéo dài startup, gây deadlock và khó phục hồi sau initialization failed.
28. Lần đầu class xuất hiện EIIE hoặc một `Error` ban đầu, cùng cause chain.
29. Có; class erroneous không tự reset.
30. Static method gọi sau khi class initialized có thể ném lỗi rồi lần sau vẫn dùng class; lỗi trong static initializer có thể làm class erroneous.

## 24. Ghi nhớ

**`ExceptionInInitializerError` là lỗi báo một class/interface không khởi tạo thành công, thường do một exception không thuộc `Error` thoát khỏi static initialization.** Hãy đọc `getCause()` hoặc `getException()` để tìm nguyên nhân; nếu lỗi gốc đã là `Error`, JVM có thể truyền trực tiếp đối tượng ấy. Sau initialization failure, class definition có thể ở trạng thái erroneous, khiến active use sau gặp `NoClassDefFoundError` thay vì chạy lại static block. Không nhầm hiện tượng này với lỗi thiếu class trên classpath hoặc exception từ một static method bình thường. Đối với cấu hình, I/O và tài nguyên cần retry, nên chuyển công việc sang factory/method có quản lý vòng đời thay vì để nó thất bại trong quá trình khởi tạo class.

### Tham khảo chuẩn Java 17

- Java Language Specification, Initialization of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- Java Virtual Machine Specification, Initialization: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- `ExceptionInInitializerError`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html
- `NoClassDefFoundError`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-008">NoClassDefFoundError trong lần sử dụng sau một lần khởi tạo thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# NoClassDefFoundError trong lần sử dụng sau một lần khởi tạo thất bại

> **Java Core – Exception** · Nhóm: **Error và khởi tạo** · Java **17+**. Bài học tập trung vào một hiện tượng rất dễ chẩn đoán sai: lớp **đã được JVM tìm thấy**, nhưng khởi tạo tĩnh thất bại; ở lần sử dụng đầu tiên bạn có thể thấy `ExceptionInInitializerError` (hoặc một `Error` gốc), còn những lần sử dụng chủ động tiếp theo đối với **cùng lớp trong cùng cơ chế nạp lớp** lại nhận `NoClassDefFoundError`.

## 1. Hiện tượng thường gặp

Một chương trình đang chạy xuất hiện stack trace dạng:

~~~text
java.lang.ExceptionInInitializerError
    ...
Caused by: java.lang.IllegalStateException: invalid configuration
    ...
~~~

Sau đó, khi gọi lại chính class ấy:

~~~text
java.lang.NoClassDefFoundError: Could not initialize class com.example.Settings
    ...
~~~

Người đọc có thể lập tức nghĩ “file `Settings.class` đã biến mất khỏi classpath”. **Đó không phải kết luận đúng trong mọi trường hợp.** Một nguyên nhân khác rất quan trọng là: JVM đã tìm được class, nhưng **class initialization trước đó hoàn thành bất thường**; JVM đánh dấu class ấy ở trạng thái lỗi (*erroneous state*).

Khi mã tiếp tục thực hiện một thao tác cần class đã được khởi tạo, JVM **không chạy lại static initializer như thể chưa có gì xảy ra**. Với trạng thái erroneous, một lần sử dụng sau có thể dẫn tới `NoClassDefFoundError`, cho biết class này **không thể được khởi tạo thành công** trong bối cảnh đó.

### Ba khái niệm phải tách riêng

1. **Class loading (nạp lớp):** tìm và tạo biểu diễn runtime của class từ class file/nguồn do class loader cung cấp.
2. **Linking (liên kết):** verification, preparation và resolution theo quy tắc JVM.
3. **Initialization (khởi tạo):** chạy các phần khởi tạo biến tĩnh và khối `static { ... }` của class, sau khi đáp ứng những điều kiện liên quan tới superclass.

`NoClassDefFoundError` có thể phát sinh từ nhiều loại thất bại ở giai đoạn liên quan tới class. **Một trường hợp đặc biệt** của bài này là class **đã load/link đủ để đi vào initialization**, initialization hỏng, và các lần active use về sau thất bại.

**Ý chính:** Đừng chỉ nhìn exception ở cuối chuỗi. Hãy tìm **lỗi đầu tiên xảy ra trong quá trình initialization**.

## 2. Phân cấp exception/error liên quan

~~~text
java.lang.Throwable
├── java.lang.Error
│   └── java.lang.LinkageError
│       ├── java.lang.ExceptionInInitializerError
│       └── java.lang.NoClassDefFoundError
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── java.lang.IllegalStateException
~~~

Cần phân biệt:

| Kiểu | Nhóm | Vai trò trong tình huống này |
| --- | --- | --- |
| `IllegalStateException` | Unchecked `RuntimeException` | Lỗi gốc minh họa, được ném khi static initialization không hợp lệ |
| `ExceptionInInitializerError` | Unchecked `Error`, thuộc nhánh `LinkageError` | Có thể được JVM dùng để bọc exception **không thuộc Error** khiến class initialization thất bại |
| `NoClassDefFoundError` | Unchecked `Error`, thuộc nhánh `LinkageError` | Có thể báo việc class không thể sẵn sàng cho lần sử dụng sau do trạng thái khởi tạo lỗi; cũng có nguyên nhân khác như thiếu định nghĩa class |
| `ClassNotFoundException` | **Checked** `Exception` | API nạp lớp theo tên, ví dụ `Class.forName`, không tìm được định nghĩa class được yêu cầu |

`ExceptionInInitializerError` **không phải** subclass của `RuntimeException`. Nếu chỉ dùng `catch (RuntimeException)`, bạn **không bắt được** Error này. Tương tự, `catch (Exception)` không bắt các nhánh Error nói trên.

**Đừng biến bảng thành lời khuyên bắt mọi Error.** Trong ứng dụng thông thường, lỗi initialization là vấn đề nghiêm trọng về khả năng hoạt động của class; cách khắc phục thường là sửa cấu hình hoặc code, không phải nuốt Error rồi tiếp tục.

## 3. Vì sao lần đầu và lần sau lại ném hai loại khác nhau?

Xét:

~~~java
class BrokenSettings {
    static final String NAME = "app";
    static int VALUE = initialize();

    private static int initialize() {
        throw new IllegalStateException("configuration missing");
    }
}
~~~

Đây là khai báo Java hợp lệ để minh họa, nhưng bản thân chưa có `main`. Khi có một **active use** yêu cầu khởi tạo `BrokenSettings`, JVM thực hiện code khởi tạo static field `VALUE` bằng cách gọi `initialize()`.

Quy trình khái quát:

1. Một thread đầu tiên thực hiện thao tác yêu cầu class được khởi tạo.
2. JVM kiểm tra trạng thái initialization của class và các ràng buộc phụ thuộc.
3. JVM bắt đầu thực thi initializer tĩnh (và phần khởi tạo superclass phù hợp nếu cần).
4. `initialize()` ném `IllegalStateException`.
5. Vì Throwable gốc **không thuộc `Error`**, thuật toán khởi tạo của JVM cố tạo `ExceptionInInitializerError` với nguyên nhân đó (theo JLS 12.4.2).
6. Class bị đánh dấu là **erroneous** và lần sử dụng đang gây initialization kết thúc bất thường.
7. Một lần active use sau của class erroneous gặp `NoClassDefFoundError` thay vì chạy lại initializer.
8. Các lời gọi khác trong bối cảnh nạp lớp tương tự tiếp tục gặp vấn đề cho tới khi thay đổi bối cảnh class loading/chạy lại ứng dụng bằng giải pháp phù hợp; chỉ gọi lại method không làm trạng thái erroneous trở thành bình thường.

**Quan trọng:** Quy tắc dùng `ExceptionInInitializerError` ở bước 5 không áp dụng giống vậy nếu Throwable do initialization ném ra vốn là một subclass của `Error`. Trường hợp đó, `Error` có thể được truyền trực tiếp trong lần đầu, nhưng class vẫn bị đánh dấu erroneous nếu initialization thất bại.

### 3.1. Sơ đồ trạng thái

~~~text
Lớp chưa khởi tạo
      |
      | first active use
      v
Đang khởi tạo (initialization in progress)
      |
      +-- thành công --> Đã khởi tạo --> các lần active use tiếp tục bình thường
      |
      +-- ném Throwable
              |
              +-- không thuộc Error --> JVM bọc bằng ExceptionInInitializerError
              |
              +-- là Error ----------> có thể truyền Error gốc
              |
              v
       Lớp erroneous
              |
              | active use sau
              v
       NoClassDefFoundError
~~~

Đây là **mô hình rút gọn** tập trung vào thất bại initializer; thuật toán JVM thực tế còn xử lý khóa đồng bộ, recursive initialization, superclass/superinterface, trình tự báo lỗi và nhiều chi tiết khác.

### 3.2. Vì sao JVM không thử khởi tạo lại class ngay?

Class initialization thiết lập trạng thái dùng chung và có thể tạo **side effect**: đăng ký service, tạo singleton, kết nối tài nguyên, ghi log, mutate cấu trúc dữ liệu hoặc nạp native library. Nếu initializer chạy một phần rồi lỗi, JVM không có cơ chế tổng quát bảo đảm rollback mọi side effect ấy.

Việc nhớ rằng initialization đã thất bại giúp tránh giả định “gọi lại lần nữa là class tự hồi phục”. Theo [JLS §12.4.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2), một class được đánh dấu erroneous khi initialization không thành công và các yêu cầu khởi tạo tiếp theo không được thực hiện như một lần khởi tạo mới bình thường.

Đây là lý do **không nên đặt các tác vụ môi trường dễ lỗi, có khả năng cần retry, vào static initializer** nếu ứng dụng kỳ vọng khôi phục lúc runtime.

## 4. Chương trình Java 17 hoàn chỉnh: quan sát lần đầu và lần sau

Lưu tệp **`FailedInitializationDemo.java`**, giữ nguyên tên public class.

~~~java
public class FailedInitializationDemo {
    static class Broken {
        static final String LABEL = "constant";
        static int VALUE = initialize();

        private static int initialize() {
            System.out.println("broken-initializer");
            throw new IllegalStateException("invalid configuration");
        }

        static void ping() {
            System.out.println("broken-ping");
        }
    }

    static class BrokenByError {
        static int VALUE = initialize();

        private static int initialize() {
            System.out.println("error-initializer");
            throw new AssertionError("broken invariant");
        }
    }

    static class Healthy {
        static int VALUE = initialize();

        private static int initialize() {
            System.out.println("healthy-initializer");
            return 7;
        }
    }

    private static void attempt(String label, Runnable work) {
        System.out.println("== " + label + " ==");
        try {
            work.run();
        } catch (ExceptionInInitializerError ex) {
            System.out.println("type=ExceptionInInitializerError");
            Throwable cause = ex.getCause();
            System.out.println("cause=" + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        } catch (NoClassDefFoundError ex) {
            System.out.println("type=NoClassDefFoundError");
        } catch (AssertionError ex) {
            System.out.println("type=AssertionError: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("== non-initializing references ==");
        System.out.println("constant=" + Broken.LABEL);
        System.out.println("class-literal=" + Broken.class.getSimpleName());

        attempt("first broken use",
                () -> System.out.println("value=" + Broken.VALUE));
        attempt("second broken use", () -> Broken.ping());
        attempt("reflective broken use", () -> {
            try {
                Class.forName("FailedInitializationDemo$Broken", true,
                        FailedInitializationDemo.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new AssertionError(ex);
            }
        });

        attempt("first Error use",
                () -> System.out.println("value=" + BrokenByError.VALUE));
        attempt("second Error use",
                () -> System.out.println("value=" + BrokenByError.VALUE));

        attempt("healthy first use",
                () -> System.out.println("value=" + Healthy.VALUE));
        attempt("healthy second use",
                () -> System.out.println("value=" + Healthy.VALUE));
    }
}
~~~

### 4.1. Cách biên dịch và chạy

Sử dụng JDK 17 hoặc phiên bản mới hơn:

~~~bash
javac --release 17 -encoding UTF-8 FailedInitializationDemo.java
java FailedInitializationDemo
~~~

**Stdout chính xác được kiểm chứng bằng cách biên dịch `javac --release 17` và chạy chương trình:**

~~~text
== non-initializing references ==
constant=constant
class-literal=Broken
== first broken use ==
broken-initializer
type=ExceptionInInitializerError
cause=IllegalStateException: invalid configuration
== second broken use ==
type=NoClassDefFoundError
== reflective broken use ==
type=NoClassDefFoundError
== first Error use ==
error-initializer
type=AssertionError: broken invariant
== second Error use ==
type=NoClassDefFoundError
== healthy first use ==
healthy-initializer
value=7
== healthy second use ==
value=7
~~~

Chương trình chỉ sử dụng Java SE và cố ý không in message mặc định của `NoClassDefFoundError`, vì thông điệp do JVM cung cấp có thể khác giữa các phiên bản và implementation. Các nhánh đã được bắt **chỉ trong chương trình dạy học** để có thể trình diễn tiếp những trường hợp còn lại.

### 4.2. Vì sao hai dòng đầu chưa chạy initializer của Broken?

~~~java
System.out.println("constant=" + Broken.LABEL);
System.out.println("class-literal=" + Broken.class.getSimpleName());
~~~

`Broken.LABEL` là một **compile-time constant**: `static final String` có initializer là biểu thức hằng `"constant"`. Việc dùng giá trị hằng này từ source có thể được compiler inline và **không kích hoạt initialization của lớp Broken**.

`Broken.class` lấy đối tượng `Class<?>` đại diện class; riêng việc lấy class literal và gọi `getSimpleName()` trong ví dụ này **không kích hoạt static initializer của Broken**.

Vì thế stdout chưa có `broken-initializer` ở hai dòng đầu. Nó chỉ xuất hiện khi đọc field `Broken.VALUE` — một static field không phải constant variable theo JLS.

### 4.3. Lần đầu: IllegalStateException trở thành ExceptionInInitializerError

Lời gọi:

~~~java
System.out.println("value=" + Broken.VALUE);
~~~

yêu cầu khởi tạo Broken. Static field `VALUE` gọi `initialize()`, in `broken-initializer` rồi ném `IllegalStateException("invalid configuration")`.

Vì exception gốc là RuntimeException (không phải Error), quá trình khởi tạo theo JLS tạo `ExceptionInInitializerError`. Handler ở ví dụ đọc `getCause()` và thấy:

~~~text
type=ExceptionInInitializerError
cause=IllegalStateException: invalid configuration
~~~

**Tập trung vào nguyên nhân gốc:** Sửa cấu hình hoặc logic khiến initializer ném `IllegalStateException`. Nếu chỉ tìm class file vì thấy `NoClassDefFoundError` ở log đến sau, rất dễ đi sai hướng.

### 4.4. Lần hai: NoClassDefFoundError thay vì chạy lại initializer

Sau lần đầu, Broken đã ở trạng thái erroneous. Lời gọi:

~~~java
Broken.ping();
~~~

là một active use của class. Kết quả:

~~~text
== second broken use ==
type=NoClassDefFoundError
~~~

Dòng `broken-ping` **không xuất hiện**, vì method body không được thực thi. JVM phải đảm bảo class được initialized trước khi xử lý invocation này; quá trình kiểm tra gặp class đã erroneous.

Lưu ý: nếu bạn xem stack trace của NoClassDefFoundError, thông tin còn lại có thể trỏ đến lỗi initialization trước đó. **Không dựa vào chính xác cấu trúc cause/message của NCDFE trên mọi JVM**; log đầu tiên và cause của EIIE gốc vẫn là bằng chứng chẩn đoán quan trọng nhất.

### 4.5. Class.forName(..., true, loader) cũng không “thử lại” initializer

Phần phản chiếu:

~~~java
Class.forName("FailedInitializationDemo$Broken", true,
        FailedInitializationDemo.class.getClassLoader());
~~~

truyền `initialize = true`, yêu cầu initialization khi cần. Vì Broken đã erroneous nên lỗi là:

~~~text
== reflective broken use ==
type=NoClassDefFoundError
~~~

Lệnh `Class.forName` không reset trạng thái. Một lần gọi lại thông qua reflection không phải đường đi khác để khôi phục class đã hỏng.

### 4.6. Nếu initializer ném Error gốc thì sao?

Class `BrokenByError` ném `AssertionError("broken invariant")` từ initializer. Nó là subclass của Error. Theo thuật toán initialization, trong tình huống này Error có thể **được truyền trực tiếp** khi class thất bại lần đầu:

~~~text
== first Error use ==
error-initializer
type=AssertionError: broken invariant
~~~

Lần hai:

~~~text
== second Error use ==
type=NoClassDefFoundError
~~~

Vì vậy câu “lần đầu **luôn** là ExceptionInInitializerError” là **sai**. Quy tắc chính xác hơn: khi initialization kết thúc bất thường, Throwable **không thuộc Error** được JVM bọc bằng ExceptionInInitializerError theo JLS; nếu vốn là Error, lỗi có thể đi ra trực tiếp.

### 4.7. Khởi tạo thành công chỉ chạy một lần

Class Healthy có field `VALUE = initialize()`; method in `healthy-initializer` rồi trả số 7.

Truy cập lần đầu:

~~~text
== healthy first use ==
healthy-initializer
value=7
~~~

Truy cập lần sau:

~~~text
== healthy second use ==
value=7
~~~

Đây là trường hợp bình thường: initializer hoàn tất thành công, class ở trạng thái initialized và không cần chạy lại ở mỗi active use.

## 5. Những thao tác nào kích hoạt khởi tạo class?

Theo [JLS §12.4.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1), initialization được yêu cầu trước một số thao tác **active use**. Với class thông thường, cần chú ý:

- Tạo instance bằng `new SomeClass()`.
- Gọi một **static method** thực sự được khai báo bởi class.
- Gán giá trị vào **static field** được khai báo bởi class.
- Đọc **static field không phải constant variable** được khai báo bởi class.
- Một số thao tác reflection có yêu cầu initialization, trong đó có `Class.forName(name)` và `Class.forName(name, true, loader)` khi tìm class phù hợp.
- Khởi tạo một class con có thể yêu cầu khởi tạo superclass và một số superinterface theo quy tắc ngôn ngữ trước khi chạy initializer của class con.

Ngược lại, nhiều thao tác tham chiếu class **không tự kích hoạt initialization**:

| Biểu thức/tác vụ | Có buộc init class ví dụ không? | Lý do |
| --- | --- | --- |
| `Broken.class` | Không chỉ vì class literal | Nhận metadata Class |
| `Broken.LABEL` với compile-time constant | Không chỉ vì đọc hằng | Constant variable có thể inline |
| `Broken.VALUE` | Có | Đọc static field không phải compile-time constant |
| `Broken.ping()` | Có | Invokestatic active use |
| `new Broken()` | Có | Tạo instance yêu cầu class initialized |
| `Class.forName("...Broken", false, loader)` | Không yêu cầu khởi tạo do flag false | Chỉ nạp/liên kết theo API; các hoạt động khác không được tùy tiện suy diễn |
| `Class.forName("...Broken", true, loader)` | Có yêu cầu init khi cần | Flag true yêu cầu initialization |
| `obj instanceof Broken` | Không được xem là trigger init chỉ vì kiểm tra kiểu | Kiểm tra runtime type không nhất thiết chạy static initializer |

Bảng dựa trên trường hợp đơn giản và quy tắc ngôn ngữ. Những thao tác có thể gây **loading/linking** không đồng nghĩa chắc chắn **initialization**. Ngoài ra, trong một chương trình thực tế, class có thể đã được khởi tạo trước đó bởi một thao tác khác.

### 5.1. Constant variable không giống mọi static final field

~~~java
class Constants {
    static final int LIMIT = 10;
    static final String NAME = "demo";
    static final Integer BOXED = 10;
    static final int COMPUTED = Integer.parseInt("10");
}
~~~

Đây là class Java minh họa hợp lệ. Hai field đầu là constant variables theo điều kiện JLS: primitive hoặc String `final` được khởi tạo bằng constant expression.

`BOXED` không phải constant variable vì kiểu `Integer` là reference wrapper; `COMPUTED` dùng lời gọi method, không phải constant expression. Việc đọc `Constants.BOXED` hoặc `Constants.COMPUTED` có thể kích hoạt initialization nếu class chưa được khởi tạo.

Khi xử lý lỗi “tại sao truy cập field A không chạy static block nhưng field B có?”, đây là khác biệt quan trọng hơn việc cả hai cùng có từ khóa `static final`.

## 6. Những nguyên nhân thực tế khiến static initialization thất bại

Không chỉ có `throw new IllegalStateException(...)` trong một method tự viết. Lỗi có thể bắt đầu từ nhiều nơi:

1. **Static field initializer** chạy code đọc cấu hình không hợp lệ.
2. **Khối `static { ... }`** gọi một method rồi method ném unchecked exception.
3. **Dependency tĩnh** hoặc superclass cần khởi tạo trước bị lỗi.
4. **Nạp driver/native library** qua code static không thành công.
5. **Singleton static** dùng giá trị môi trường không tồn tại rồi dereference null.
6. **Cấu trúc dữ liệu tĩnh** được tạo với tham số không hợp lệ.
7. **Code initialization có side effect** đã chạy một phần trước khi ném Error/RuntimeException.
8. **Lỗi runtime khó phục hồi**, ví dụ một Error liên quan resource, có thể phát sinh trong quá trình initialization.

Ví dụ:

~~~java
class Config {
    static final int PORT = Integer.parseInt(
            System.getProperty("demo.port"));
}
~~~

Đây là class minh họa. Nếu property không được cung cấp, `System.getProperty("demo.port")` trả null; `Integer.parseInt(null)` ném `NumberFormatException` (subclass của IllegalArgumentException) trong quá trình khởi tạo tĩnh. Khi active use yêu cầu initialization lần đầu, JVM có thể báo `ExceptionInInitializerError`; lần sau class erroneous có thể cho `NoClassDefFoundError`.

Điểm quan trọng không phải luôn là NumberFormatException — tùy đoạn code gốc có thể là NPE, IllegalArgumentException hoặc một Error khác. Hãy đọc **cause gốc** chứ không đoán nguyên nhân từ riêng NCDFE.

## 7. Static initialization chạy theo thứ tự nào?

Đối với một class, các **static field initializers** và **static initializer blocks** được thực hiện theo **thứ tự văn bản** trong lúc khởi tạo, với các quy tắc JVM bổ sung về superclass/superinterfaces.

Ví dụ:

~~~java
class Order {
    static int a = first();

    static {
        System.out.println("static-block");
    }

    static int b = second();

    private static int first() {
        System.out.println("first");
        return 1;
    }

    private static int second() {
        System.out.println("second");
        return 2;
    }
}
~~~

Đây là một class minh họa hợp lệ. Nếu một active use khiến `Order` được khởi tạo thành công, phần code trong class in:

~~~text
first
static-block
second
~~~

Nếu `first()` ném exception, các bước phía sau **không được chạy** trong lần khởi tạo đó. Không có một cơ chế chung tự động hoàn tác những side effect mà `first()` có thể đã thực hiện trước khi ném lỗi.

Một hệ quả cần lưu ý: nếu static initializer đã ghi file hoặc đăng ký listener rồi ném exception, trạng thái ngoài JVM class có thể đã thay đổi. Việc “thử init lại” bằng cách thay đổi logic gọi không giải quyết nhất quán những side effect đó.

## 8. Không nhầm với ClassNotFoundException hoặc class file thực sự thiếu

Cả ba trường hợp có thể liên quan tới tên class, nhưng **khác nguyên nhân và đường phát sinh**.

### 8.1. ClassNotFoundException

Ví dụ:

~~~java
try {
    Class.forName("example.DoesNotExist");
} catch (ClassNotFoundException ex) {
    System.out.println("Class not found by lookup");
}
~~~

Đây là đoạn minh họa đặt trong method phù hợp. `Class.forName(String)` có thể ném checked `ClassNotFoundException` khi class loader **không tìm được định nghĩa có tên được yêu cầu**.

### 8.2. NoClassDefFoundError do thiếu định nghĩa trong lúc linking/loading

Một class được biên dịch có thể tham chiếu tới một class khác có mặt khi build nhưng **không còn có mặt ở runtime classpath/module path**. Khi JVM cần giải quyết tham chiếu này, nó có thể ném `NoClassDefFoundError`.

Đây là nhánh nguyên nhân **khác** với “class đã được tìm thấy nhưng initialization trước đó hỏng”.

### 8.3. NoClassDefFoundError do class erroneous

Trong bài này, `Broken.class` đã tồn tại, runtime nạp được class, initializer còn **in `broken-initializer`** trong lần đầu. Do đó không thể giải thích lần NoClassDefFoundError thứ hai đơn giản là “thiếu bytecode class”.

### 8.4. Bảng phân biệt nhanh

| Dấu hiệu | Nhóm nguyên nhân nên kiểm tra |
| --- | --- |
| `ClassNotFoundException` khi Class.forName theo tên | Class loader không tìm thấy định nghĩa được yêu cầu |
| `NoClassDefFoundError` kèm thông tin định nghĩa class thiếu | Classpath/module/dependency, tên class và lỗi linkage |
| `ExceptionInInitializerError` xuất hiện trước đó | Static initialization đã thất bại do Throwable không thuộc Error |
| `AssertionError`/Error từ initializer xuất hiện lần đầu | Static initialization cũng có thể lỗi mà **không qua EIIE** |
| `NoClassDefFoundError: Could not initialize class ...` trong lần gọi sau | Nghi class đã bị đánh dấu erroneous; tìm log lần lỗi đầu |
| Lỗi biến mất khi chạy trong class loader mới / process mới | Có thể phù hợp với trạng thái initialization gắn với identity của class ở loader cũ; vẫn phải sửa lỗi gốc |

**Không dựa tuyệt đối vào chuỗi `Could not initialize class`.** Message của LinkageError không được dùng như một format có thể parse ổn định giữa mọi JVM. Hãy xem stack trace, exception đầu tiên, runtime classpath và toàn bộ cause chain.

## 9. Class loader và định danh thực của một class

Trong JVM, khái niệm “cùng class” gắn với **tên nhị phân và defining class loader**. Hai loader khác nhau có thể định nghĩa hai lớp có tên giống nhau nhưng với identity khác nhau, nếu cơ chế loading/ủy quyền cho phép.

Điều đó giải thích vì sao:

- Một class đã erroneous trong **một class loader** không được làm như thể có thể reset bằng cách gọi `new` hoặc `Class.forName(..., true, sameLoader)`.
- Một class khác cùng tên được load/định nghĩa trong **một loader khác** có thể có trạng thái initialization riêng, nếu thực sự được định nghĩa mới ở loader đó.
- Tạo class loader mới **không bảo đảm** sẽ có định nghĩa mới nếu loader vẫn delegate cho loader cha vốn sở hữu class cũ.
- Trong ứng dụng thông thường, cách thực tế để khôi phục thường là **sửa nguyên nhân** rồi **khởi động lại ứng dụng/process hoặc thực hiện cơ chế reload đúng nghĩa** của nền tảng.

Không nên biến “đổi class loader để chạy lại initializer” thành mẹo chữa lỗi mặc định. Cơ chế plugin reload, app server redeploy hay hot-reload có thể liên quan thêm đến rò rỉ class loader, tham chiếu static và cấu trúc dependency.

## 10. Điều gì xảy ra khi nhiều thread cùng chạm vào một class?

Class initialization được JVM điều phối với cơ chế đồng bộ để không có hai thread độc lập đồng thời chạy toàn bộ initializer của **cùng class identity**.

Tình huống:

1. Thread A bắt đầu khởi tạo class.
2. Thread B cũng cần active use của class trong lúc A đang init.
3. B có thể phải đợi quá trình khởi tạo do A thực hiện.
4. Nếu A khởi tạo thành công, B có thể tiếp tục với class đã initialized.
5. Nếu A khởi tạo thất bại, class bị đánh dấu erroneous; B và các lần active use về sau có thể nhận `NoClassDefFoundError` theo thuật toán trạng thái.

Điều này lý giải một pattern trong log hệ thống đa luồng: **chỉ một thread thấy Error initialization gốc**, còn các thread khác sau đó thấy NoClassDefFoundError, khiến vấn đề trông như mất class đột ngột.

**Không viết code phụ thuộc thứ tự thread nào sẽ thấy EIIE hay NCDFE** trong một cuộc đua initialization. Đây là chi tiết scheduling; khi điều tra, cần tổng hợp log theo mốc thời gian và đối tượng class.

### Recursive initialization

Nếu một thread trong quá trình khởi tạo class lại tham chiếu tới chính class ấy theo cách Java cho phép, JVM có quy tắc riêng cho **recursive initialization bởi cùng thread**; không đơn giản chạy initializer vô hạn. Tuy nhiên, static field có thể còn ở trạng thái mặc định hoặc chưa được gán xong, tạo bug “partial initialization” khó nhận ra.

Ví dụ này nằm ngoài đường chính của bài, nhưng củng cố nguyên tắc **tránh thiết kế các static initializer phức tạp có phụ thuộc vòng**.

## 11. Superclass và phụ thuộc static hỏng có thể lan ra sao?

Khi JVM khởi tạo một class, nó cần bảo đảm superclass tương ứng được khởi tạo theo quy tắc. Nếu một superclass có static initializer thất bại, quá trình khởi tạo subclass cũng không thể tiếp tục bình thường.

Ví dụ minh họa:

~~~java
class Parent {
    static int value = load();

    static int load() {
        throw new IllegalStateException("parent init failed");
    }
}

class Child extends Parent {
    static int other = 10;
}
~~~

Đây là hai class Java hợp lệ nhưng không có main. Khi lần đầu active use đòi khởi tạo Child, JVM phải khởi tạo Parent trước. Nếu Parent ném lỗi, Child cũng không thể đi vào trạng thái initialized bình thường theo đường này.

**Cần kiểm tra đúng class gốc trong stack trace:** Một NoClassDefFoundError xuất hiện ở điểm gọi Child có thể bắt nguồn từ Parent hoặc một dependency tĩnh khác. Đừng sửa Child chỉ vì đó là tên class ở dòng business gọi cuối cùng.

### Tương tự với static holder/singleton

Pattern:

~~~java
class ServiceHolder {
    static final Service INSTANCE = new Service(readConfig());
}
~~~

Đây là đoạn minh họa, cần class Service và method readConfig phù hợp. Nếu constructor Service hoặc readConfig thất bại không được xử lý hợp lệ, initialization của ServiceHolder cũng thất bại. Mọi caller sử dụng `ServiceHolder.INSTANCE` về sau có thể thấy NCDFE ngay cả khi code caller không biết đến readConfig.

Một singleton holder cho **lazy initialization** vẫn dùng class initialization; “lazy” chỉ nói thời điểm khởi tạo, không có nghĩa tự hỗ trợ retry khi khởi tạo hỏng.

## 12. Kiểm tra lỗi gốc trước khi kết luận thiếu dependency

Checklist điều tra khi gặp NoClassDefFoundError:

1. **Xem exception đầy đủ**, không chỉ dòng đầu: message, stack trace, cause chain.
2. **Tìm trong log mốc thời gian trước đó** các `ExceptionInInitializerError`, `AssertionError`, `Error` khác xảy ra khi class được active use lần đầu.
3. **Xác định class đang erroneous**: nó có khối static/field initializer không? Có superclass hoặc dependency tĩnh nào bị lỗi?
4. **Kiểm tra root cause**: IllegalStateException, NumberFormatException, NPE, initializer truy cập cấu hình, driver, native library hoặc dữ liệu đầu vào.
5. Nếu không có dấu hiệu initialization hỏng, **kiểm tra classpath/module path**, packaging, dependency versions, loader isolation.
6. **Ghi nhận class loader** nếu môi trường plugin/container phức tạp; hai class cùng tên ở loader khác nhau không đồng nghĩa cùng identity.
7. **Tái hiện từ process sạch** để bắt được lỗi **lần đầu**, vì một process chạy lâu có thể chỉ còn log NCDFE.
8. **Sửa nguyên nhân trong initializer**, không chỉ catch NCDFE ở caller.
9. Kiểm tra các side effect đã xảy ra **trước thời điểm khởi tạo hỏng**.
10. **Kiểm thử lại fresh start**, bao gồm trường hợp cấu hình thiếu và cấu hình hợp lệ.

Nếu log chỉ có:

~~~text
java.lang.NoClassDefFoundError: Could not initialize class app.Config
~~~

hãy tìm một stack trace **sớm hơn** thay vì khẳng định rằng jar chứa `app.Config` bị xóa. Ngược lại, nếu package/class thực sự không được deploy, chạy lại static initializer không giải quyết được vấn đề classpath.

## 13. Lưu trữ và trình bày lỗi để không mất root cause

Khi debug, exception object quan trọng hơn message ngắn. Ví dụ minh họa ở tầng chẩn đoán:

~~~java
try {
    useComponent();
} catch (ExceptionInInitializerError ex) {
    logger.error("Component initialization failed", ex);
    throw ex;
}
~~~

Đây là đoạn minh họa cần `useComponent()` và logger thích hợp. Nó giữ nguyên Error sau khi log, không tạo kết quả thành công giả. Ở môi trường không có logging framework, có thể dùng `ex.printStackTrace()` để chẩn đoán cục bộ.

Với `ExceptionInInitializerError`:

- `getCause()` thường cung cấp Throwable đã khiến initializer thất bại.
- `getException()` là API lịch sử của lớp này, được giữ vì tương thích; dùng cause chain chung khi có thể.
- Nếu một lập trình viên tự tạo EIIE hoặc dùng constructor không có cause, không được giả định `getCause()` luôn khác null.
- Stack trace có thể cho thấy lần khởi tạo đầu tiên, sau đó là nhiều NoClassDefFoundError. **Giữ log theo thời gian** để liên kết đúng sự kiện.

Với `NoClassDefFoundError`, đừng dựa vào việc `getCause()` chắc chắn chứa bản sao của Error đầu tiên: việc tạo message/cause chi tiết có thể phụ thuộc implementation. Lỗi đầu trong log là nguồn đáng tin cậy để điều tra.

## 14. Tránh đặt kết nối và cấu hình dễ lỗi trong static initializer

Một anti-pattern:

~~~java
class DatabaseGateway {
    static final Connection CONNECTION = connect();

    private static Connection connect() {
        // Nếu kết nối thất bại bằng unchecked exception,
        // class có thể rơi vào trạng thái erroneous.
        throw new IllegalStateException("database unavailable");
    }
}
~~~

Đây là mã minh họa, cần `Connection` từ java.sql hoặc một kiểu dữ liệu tương ứng. Mục đích chỉ ra rằng **tạo tài nguyên có thể thất bại bởi môi trường** trong static initialization làm lớp không có đường retry tự nhiên.

Ngoài ra, một database connection tĩnh chia sẻ lâu dài có thể vi phạm chính sách thread safety, vòng đời kết nối và pooling. Đây không phải lựa chọn mặc định tốt cho production.

### 14.1. Giải pháp: khởi tạo tường minh ở lifecycle phù hợp

Thay vì để static class initialization mang toàn bộ lỗi môi trường, đưa việc tạo tài nguyên vào một component có lifecycle rõ:

~~~java
import java.io.IOException;

final class ConfigurableService {
    private String config;

    void initialize(String path) throws IOException {
        this.config = readConfig(path);
    }

    private String readConfig(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("configuration path missing");
        }
        return path;
    }

    String currentConfig() {
        if (config == null) {
            throw new IllegalStateException("service not initialized");
        }
        return config;
    }
}
~~~

Class trên là minh họa Java hợp lệ có thể đặt trong file phù hợp. Nó làm cho lỗi cấu hình xảy ra trong một **method có hợp đồng checked IOException**, không làm class bị erroneous chỉ vì một lần dùng.

Tất nhiên, service object vẫn cần thiết kế đồng bộ/lifecycle, không cho truy cập trước khi initialized, và xử lý trạng thái partial initialization. Nhưng nó cho phép ứng dụng **chủ động quyết định** có retry, sửa cấu hình, báo lỗi cho vận hành hay dừng khởi động.

### 14.2. Lazy initialization có kiểm soát

Nếu cần trì hoãn kết nối hoặc tải cấu hình cho tới lúc sử dụng:

- Có thể dùng method/factory với result hoặc checked exception rõ ràng.
- Cần định nghĩa policy cache thành công/thất bại, thời gian retry và concurrency.
- Tránh cache một thất bại vĩnh viễn nếu nghiệp vụ yêu cầu hồi phục; ngược lại, đừng retry vô hạn thao tác có thể gây side effect.
- Nếu vẫn dùng static holder, hãy nhớ **initializer failure vẫn làm holder class erroneous**.
- Dùng dependency injection/lifecycle hooks của framework nếu dự án có, nhưng kiểm tra cách framework báo và cache lỗi.

**Điểm then chốt:** chuyển lỗi môi trường dễ xảy ra ra khỏi class initialization khi cần một đường phục hồi hợp lệ; điều này giúp tránh vòng “lần đầu EIIE, về sau NCDFE”.

## 15. Có thể catch Error để phục hồi class đã lỗi không?

Một caller **có thể** viết:

~~~java
try {
    Broken.VALUE = 5;
} catch (ExceptionInInitializerError ex) {
    System.err.println("initialization failed");
}
~~~

Đây là minh họa cần class Broken có trường static phù hợp; trong ví dụ chính, không nên gán trường VALUE như một cách cố sửa class.

Việc catch Error ở caller **không reset trạng thái class**, không chạy lại static initialization và không chứng minh các side effect đã rollback. Những lần active use sau vẫn có thể gặp NCDFE.

**Vì sao không nên catch Error rộng?**

- Các Error như LinkageError, OutOfMemoryError hoặc lỗi khởi tạo có thể khiến ứng dụng không còn ở trạng thái vận hành đáng tin.
- Nuốt Error làm mất dấu lỗi gốc và khiến hệ thống tiếp tục ở trạng thái sai.
- Một số lỗi có nguyên nhân không thể khôi phục tại điểm gọi.

Có thể catch cụ thể ở **test/demo hoặc top-level diagnostic boundary** để ghi log và kết thúc/chuyển trạng thái, nhưng không dùng `catch (Error) { }` làm chính sách chạy tiếp mặc định.

## 16. Khi nào NoClassDefFoundError có thể xảy ra ngay lần đầu?

Cần tránh diễn giải quá mức một quy tắc của bài. `NoClassDefFoundError` không **chỉ** là “dấu hiệu lớp hỏng lần trước”. Nó còn là một LinkageError gắn với thất bại nạp/định nghĩa class, hoặc có thể xuất hiện khi một class cần được sử dụng nhưng đang ở trạng thái erroneous do một thread khác đã thử khởi tạo trước đó.

Ví dụ:

- Class A tham chiếu class B, nhưng định nghĩa B thiếu trong runtime environment → NCDFE có thể xuất hiện **ngay tại lần đầu** sử dụng đường mã đó.
- Thread A đang chạy initializer class X và thất bại; thread B là thread đầu tiên trong log riêng của nó được gọi X nhưng lúc đó X đã erroneous → thread B có thể thấy NCDFE mà không từng thấy EIIE.
- Một module/plugin đã init class trước khi request handler bắt đầu → request đầu tiên được ghi log có thể chỉ thấy NCDFE.

Vì vậy để xác định nguyên nhân phải xem **toàn bộ process/log và loader**, không chỉ thứ tự lỗi trong một thread.

## 17. Những hiểu nhầm thường gặp

1. **“NoClassDefFoundError luôn nghĩa là thiếu file .class.”** Sai; có thể class đã được tìm thấy nhưng initialization thất bại trước đó.
2. **“Nếu mã đã biên dịch thì static initialization chắc chắn thành công.”** Sai; khởi tạo có thể ném exception ở runtime.
3. **“Lần đầu hỏng static init luôn là NoClassDefFoundError.”** Sai; có thể là ExceptionInInitializerError hoặc Error gốc.
4. **“Lần đầu hỏng luôn là ExceptionInInitializerError.”** Sai; Error ném trực tiếp không buộc được bọc theo cùng quy tắc.
5. **“ExceptionInInitializerError là RuntimeException.”** Sai; nó là Error dưới nhánh LinkageError.
6. **“NoClassDefFoundError là checked exception.”** Sai; đó là Error unchecked.
7. **“Catch Exception sẽ bắt được EIIE/NCDFE.”** Sai vì các lớp này thuộc nhánh Error.
8. **“Chỉ cần gọi lại method static là initializer sẽ thử chạy lại.”** Sai trong cùng trạng thái erroneous.
9. **“Class.forName(..., true, sameLoader) là nút retry static initializer.”** Sai.
10. **“Truy cập static final nào cũng chắc chắn khởi tạo class.”** Sai với compile-time constant variables.
11. **“Lấy Broken.class luôn chạy static block.”** Sai; class literal tự nó không phải active use buộc init.
12. **“Class loading và initialization là cùng một hành động.”** Sai; các giai đoạn có quy tắc khác nhau.
13. **“Static initializer không thể có side effect trước khi thất bại.”** Sai; một phần code có thể đã chạy.
14. **“JVM tự rollback side effect khi initializer ném lỗi.”** Không có cơ chế rollback tổng quát như vậy.
15. **“Một class đã erroneous sẽ tự hoạt động trở lại khi cấu hình ngoài được sửa.”** Không chỉ nhờ sửa cấu hình; class cần bối cảnh nạp/chạy thích hợp mới có cơ hội init mới.
16. **“Class loader mới chắc chắn định nghĩa lại class.”** Không nếu vẫn delegate về loader đã định nghĩa class ấy.
17. **“Thread nào dùng class cũng nhìn thấy cùng loại Error đầu tiên.”** Sai; các thread khác có thể nhận NCDFE sau khi init thất bại.
18. **“Nếu static initializer ném AssertionError thì lần sau cũng AssertionError.”** Không; class erroneous làm active use sau có thể nhận NCDFE.
19. **“Message của NoClassDefFoundError luôn đủ để tìm root cause.”** Sai; thường cần log lần khởi tạo đầu tiên.
20. **“Cứ catch NCDFE, trả null và tiếp tục là an toàn.”** Sai; có thể che trạng thái không khởi tạo được của component.
21. **“Superclass init thất bại không ảnh hưởng subclass.”** Sai; khởi tạo subclass có phụ thuộc phù hợp.
22. **“Một singleton holder static luôn có thể retry nếu constructor thất bại.”** Sai; holder cũng có thể erroneous.
23. **“Lỗi classpath và lỗi initialization luôn phân biệt được chỉ từ tên class ở exception.”** Sai; phải xem nguyên nhân, thời điểm và cấu hình runtime.
24. **“Thông điệp ‘Could not initialize class’ là contract parse ổn định.”** Không nên; nội dung message có thể khác implementation.
25. **“Sửa code static initialization trong source khi process đang chạy sẽ tự phục hồi class lỗi.”** Không; process/loader còn sử dụng định nghĩa đã load, trừ khi có cơ chế reload chuyên biệt thực sự.

## 18. Câu hỏi và bài tập tự kiểm tra

**Câu 1.** Một class có field static khởi tạo bằng method ném `IllegalStateException`. Lần active use đầu thường nhận gì?

*Đáp án:* Nếu initialization thất bại do RuntimeException không thuộc Error, JVM bọc theo thuật toán JLS thành `ExceptionInInitializerError`.

**Câu 2.** Active use thứ hai của class đã erroneous sẽ nhận gì?

*Đáp án:* `NoClassDefFoundError` thay vì khởi tạo lại bình thường.

**Câu 3.** Static initializer trực tiếp ném `AssertionError`. Lần đầu có bắt buộc qua EIIE không?

*Đáp án:* Không. Error có thể được truyền trực tiếp; sau thất bại class bị erroneous và lần sau có thể nhận NCDFE.

**Câu 4.** Vì sao `Broken.LABEL` không chạy initializer trong chương trình chính?

*Đáp án:* LABEL là một compile-time constant variable và cách dùng đó không kích hoạt khởi tạo class.

**Câu 5.** `Broken.class.getSimpleName()` có chứng minh class đã initialized thành công không?

*Đáp án:* Không. Class literal/metadata access không buộc chạy static initializer như việc đọc non-constant static field.

**Câu 6.** `Class.forName(name, true, sameLoader)` có khởi tạo lại class erroneous không?

*Đáp án:* Không; nếu JVM đã đánh dấu class erroneous, active use sau nhận NCDFE.

**Câu 7.** Phân biệt ClassNotFoundException và NCDFE trong tình huống bài học?

*Đáp án:* ClassNotFoundException là checked exception thường do API lookup class theo tên không tìm được định nghĩa; NCDFE là LinkageError có thể do thiếu định nghĩa lúc runtime hoặc do class đã hỏng initialization.

**Câu 8.** Vì sao không nên đặt kết nối database bên trong static initializer nếu cần retry?

*Đáp án:* Một lỗi môi trường tạm thời có thể làm class erroneous cho cả các lần dùng sau, thay vì cho ứng dụng tái thử có kiểm soát.

**Câu 9.** Khi chỉ nhìn thấy NCDFE ở request thứ hai, bước debug quan trọng nhất là gì?

*Đáp án:* Tìm lỗi initialization đầu tiên trên toàn process/log, gồm EIIE hoặc Error gốc, và xem stack trace/cause.

**Câu 10.** Cùng một class binary name được define bởi hai loader khác nhau có phải luôn là cùng runtime class identity?

*Đáp án:* Không. Identity có liên quan tới defining loader. Nhưng class loader mới không bảo đảm định nghĩa mới nếu tiếp tục delegation về loader cha.

**Câu 11.** Một thread B có thể chỉ thấy NCDFE dù không thấy lỗi initialization ban đầu không?

*Đáp án:* Có, nếu thread A đã cố init class và thất bại, JVM đánh dấu class erroneous trước khi B có thể tiếp tục active use.

**Câu 12.** Catch Exception có đủ để bao cả EIIE và NCDFE không?

*Đáp án:* Không. Cả hai đều là Error, không phải subclass của Exception.

**Câu 13.** Nếu initializer thay đổi một registry chung rồi ném exception, JVM có tự rollback registry không?

*Đáp án:* Không có rollback tổng quát. Phải thiết kế initializer và lifecycle để tránh/kiểm soát partial side effects.

**Câu 14.** Đổi `static final int VALUE = 42;` thành `static final int VALUE = Integer.parseInt("42");` có thể thay đổi thời điểm class initialization không?

*Đáp án:* Có. Trường hợp đầu là constant variable; trường hợp thứ hai có lời gọi method, không phải compile-time constant expression.

**Câu 15.** Có nên catch NoClassDefFoundError rồi cố gán lại field static để cứu class?

*Đáp án:* Không. Class erroneous không thể trở thành initialized chỉ bằng thao tác gán field; cần sửa nguyên nhân và quản lý lifecycle/class loading phù hợp.

**Câu 16.** Một class Child khởi tạo lỗi vì Parent static initializer lỗi. Nên xem class nào trước?

*Đáp án:* Xem stack trace và cause gốc; nếu Parent là nơi initialization thất bại đầu tiên, xử lý lỗi ở Parent/dependency trước thay vì chỉ sửa nơi dùng Child.

## 19. Checklist phòng ngừa lỗi class initialization trong production

- **Giảm công việc trong static initializer:** ưu tiên hằng số thuần và cấu trúc dữ liệu ổn định.
- **Không đặt tác vụ cần retry trong static init** nếu ứng dụng có yêu cầu phục hồi runtime.
- **Kiểm tra cấu hình đầu vào** ở lifecycle rõ ràng thay vì tạo singleton ẩn mà khó đoán.
- **Tài liệu hóa và theo dõi lỗi startup**: log cause, stack trace, class/module/source, không chỉ message ngắn.
- **Phân biệt dependent class failure** với missing runtime dependency.
- **Xem cùng class loader** khi điều tra trạng thái erroneous và khả năng reload.
- **Test từ process sạch** để thấy exception đầu tiên, sau đó test active use thứ hai để xác nhận đường NCDFE.
- **Tránh side effect không idempotent** trong static initializer hoặc phải quản lý rollback tại nơi phù hợp.
- **Đừng catch Error rồi im lặng**; tại boundary chẩn đoán cần báo sự cố và dừng component nếu không đáng tin.
- **Đảm bảo classpath/module path đúng** nếu evidence cho thấy NoClassDefFoundError do thiếu định nghĩa thực sự.
- **Kiểm thử concurrency** khi nhiều thread có thể kích hoạt init gần như đồng thời; không dựa vào thứ tự thông báo Error tuyệt đối.
- **Kiểm thử các đường gọi không kích hoạt init** như compile-time constants và metadata để tránh kết luận sai khi tạo reproduce case.

## 20. Tài liệu chính thức Java 17

- [JLS Java SE 17 §12.4.1 — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): các thao tác gây initialization và ngoại lệ của compile-time constant.
- [JLS Java SE 17 §12.4.2 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): trạng thái initialization, synchronization, Error wrapping, erroneous state và các yêu cầu khởi tạo sau khi thất bại.
- [JVMS Java SE 17 §5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): khởi tạo trong JVM.
- [Java SE 17 API — ExceptionInInitializerError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): class Error báo lỗi trong static initializer và cách truy xuất exception gốc.
- [Java SE 17 API — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): lớp LinkageError có nhiều nguồn phát sinh, không chỉ lỗi initialization.
- [Java SE 17 API — LinkageError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html): nhóm vấn đề liên quan đến loading/linking/khả năng tương thích class.
- [Java SE 17 API — ClassNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html): checked exception của một số phương thức lookup class.
- [Java SE 17 API — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): lựa chọn initialize true/false khi lookup class.
- [JLS Java SE 17 §4.12.4 — final Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4): điều kiện xác định constant variable.
- [JLS Java SE 17 chương 11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): phân cấp checked/unchecked và xử lý Throwable.

**Ghi nhớ cuối:** Một class có thể **tồn tại trên classpath** nhưng bị JVM đánh dấu **erroneous** sau static initialization thất bại. **Lần đầu** thường thấy `ExceptionInInitializerError` khi nguyên nhân là RuntimeException, hoặc Error gốc khi initializer ném Error; **lần sử dụng chủ động sau** thấy `NoClassDefFoundError`. Không nhầm nó với thiếu class file nếu có log initialization failure sớm hơn. Hãy sửa lỗi gốc, tránh static initializer phụ thuộc môi trường dễ hỏng và đừng cho rằng gọi lại class sẽ tự hồi phục trong cùng class loader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-and-initialization-009">Vì sao bắt Throwable hoặc Error cần một ranh giới và mục đích rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao bắt `Throwable` hoặc `Error` cần một ranh giới và mục đích rõ ràng trong Java

Trong Java, **bắt được một ngoại lệ không có nghĩa là có thể khôi phục an toàn**. Sự khác biệt này đặc biệt quan trọng khi viết `catch (Throwable)` hoặc `catch (Error)`: hai lựa chọn ấy mở rộng phạm vi bắt tới những tình trạng nghiêm trọng như lỗi bộ nhớ, lỗi liên kết lớp và các lỗi bất biến, vốn thường **không nên được xử lý như một lỗi nghiệp vụ bình thường**.

Một ví dụ có rủi ro cao:

~~~java
// Anti-pattern: không áp dụng cho hệ thống thật.
try {
    processRequest();
} catch (Throwable failure) {
    return defaultResponse();
}
~~~

Đoạn trên chỉ là **mã minh họa**; cần có `processRequest()` và `defaultResponse()` trong ngữ cảnh thực tế. Vấn đề là nó gộp cả `IOException`, `NullPointerException`, `OutOfMemoryError`, `NoClassDefFoundError`, `AssertionError` và nhiều loại khác vào cùng một “kết quả dự phòng” rồi giả vờ hệ thống còn đáng tin. Một tác vụ có thể đã thực hiện được một phần, tài nguyên có thể bị cạn, mã chương trình có thể không tương thích với runtime. Trả về một kết quả bình thường trong trường hợp đó dễ che giấu lỗi hệ thống, làm lặp giao dịch hoặc gây mất dữ liệu.

Nguyên tắc thực hành:

> **Chỉ bắt ở nơi bạn xác định được ranh giới công việc, mục tiêu xử lý và điều kiện để hệ thống tiếp tục.** Với `Error` hoặc `Throwable` rộng, mục đích thường nên là quan sát tối thiểu, cleanup hoặc truyền lỗi lên ranh giới quản lý phù hợp — không phải nuốt lỗi và xem như thành công.

Bài này làm rõ khi nào có thể dùng `catch (Error)` hay `catch (Throwable)`, khi nào nên dùng `catch (Exception)` hoặc bắt loại cụ thể, cách thiết kế ranh giới cho worker/plugin/test, cách giữ lỗi gốc khi cleanup thất bại, và cách nhận diện những tình huống phải ngừng hoặc tái khởi động thành phần. Các chương trình minh họa sử dụng **Java 17+**, thử nghiệm bằng `AssertionError` có kiểm soát, **không cố tạo lỗi cạn bộ nhớ hoặc cạn stack thật**.

## 1. Vì sao phân cấp kiểu quyết định hành vi của `catch`?

Cấu trúc rút gọn:

~~~text
java.lang.Throwable
├── java.lang.Exception
│   ├── IOException
│   ├── SQLException
│   └── java.lang.RuntimeException
│       ├── IllegalArgumentException
│       ├── IllegalStateException
│       └── NullPointerException
└── java.lang.Error
    ├── VirtualMachineError
    │   ├── OutOfMemoryError
    │   └── StackOverflowError
    ├── LinkageError
    │   ├── NoClassDefFoundError
    │   ├── NoSuchMethodError
    │   └── ExceptionInInitializerError
    ├── AssertionError
    └── ThreadDeath
~~~

Các kiểu trong sơ đồ có **quan hệ kế thừa**, không phải mức độ ưu tiên theo hàng ngang. Một `catch` khớp khi loại runtime của đối tượng ném là lớp đó hoặc subtype thích hợp.

| Nhánh `catch` | Bắt được những loại nào? | Điểm cần nhớ |
| --- | --- | --- |
| `catch (IOException ex)` | `IOException` và subtype | Phù hợp nếu có chính sách khôi phục lỗi I/O |
| `catch (RuntimeException ex)` | Các subtype `RuntimeException` | Không bắt `Error` |
| `catch (Exception ex)` | `Exception` và subtype, gồm checked và runtime exceptions | Không bắt `Error` |
| `catch (Error err)` | `Error` và subtype | Bắt lỗi có thể nghiêm trọng ngoài phạm vi nghiệp vụ |
| `catch (Throwable t)` | Cả `Exception` lẫn `Error` | Phạm vi rộng nhất trong hai nhánh chính |
| Không bắt | Lỗi lan theo stack đến handler phù hợp hoặc cơ chế uncaught | Không tự đồng nghĩa với việc JVM chết ngay |

**`Error` là unchecked**: compiler không buộc phương thức ném nó phải khai báo `throws Error`. “Unchecked” chỉ nói về kiểm tra tại compile time, **không** có nghĩa nó nhẹ, không nguy hiểm hoặc có thể bỏ qua.

Điểm quan trọng thứ hai: `Error` **không phải subtype của `Exception`**, nên một `catch (Exception)` được viết ở ranh giới request thường chủ động để các tình trạng `Error` thoát khỏi cơ chế xử lý lỗi ứng dụng thông thường.

## 2. Phân biệt ba mục tiêu: xử lý, quan sát, và giới hạn hư hại

Từ “bắt lỗi” thường che đi ba hoạt động khác nhau.

### 2.1. Xử lý/phục hồi (handle/recover)

Bạn biết **nguyên nhân đủ cụ thể**, biết **trạng thái tác vụ sau lỗi**, và có **đường phục hồi đúng hợp đồng**. Ví dụ:

~~~java
// Đoạn minh họa trong một phương thức có readFromPrimary/Backup.
try {
    return readFromPrimary();
} catch (java.io.IOException ex) {
    return readFromBackup();
}
~~~

Đường phục hồi có ý nghĩa khi dữ liệu dự phòng hợp lệ và việc dùng dự phòng được cho phép. Đây không phải ví dụ biên dịch độc lập, vì hai phương thức đọc cần được định nghĩa. Nếu `readFromBackup()` cũng ném checked `IOException`, phương thức chứa đoạn mã phải xử lý/khai báo nó.

### 2.2. Quan sát/ghi nhận (observe)

Bạn **chưa chắc sửa được lỗi**, nhưng muốn ghi nhận nguyên nhân, tên tác vụ hoặc thông tin chẩn đoán. Một boundary quản lý tác vụ có thể ghi nhận `Throwable` rồi **ném lại**, cho hệ thống quản lý quyết định có tiếp tục không.

~~~java
// Minh họa ranh giới quan sát có chủ đích.
try {
    runTask();
} catch (Error err) {
    minimalFailureSignal(err);
    throw err;
}
~~~

Đoạn trên không độc lập; hai hàm phụ thuộc ứng dụng. Chính sách “ghi nhận tối thiểu rồi ném lại” **khác** với `return success`.

### 2.3. Giới hạn hư hại (contain/isolate)

Một hệ thống thử nghiệm plugin, test runner hoặc supervisor có thể cần giữ cho **đơn vị quản lý bên ngoài** còn vận hành khi một **đơn vị con đã được cô lập** thất bại. Tuy nhiên, điều này chỉ có căn cứ nếu có kiểm soát về **state, quyền sở hữu resource, side effect và loại lỗi**. Một `catch(Throwable)` đặt ở cùng heap/JVM **không tạo ra ranh giới an toàn trước `OutOfMemoryError`**.

Ví dụ một test runner có thể bắt `AssertionError` để đánh dấu một test thất bại và tiếp tục test kế tiếp; nhưng chính test runner đó không mặc định có khả năng phục hồi an toàn sau lỗi JVM nội bộ.

**Nguyên tắc:** mỗi nhánh `catch` phải trả lời rõ “tôi làm gì với lỗi này?” và “vì sao vẫn được phép chạy tiếp?”. Không trả lời được thì không nên dùng `catch` quá rộng.

## 3. Một `Error` không giống một lỗi nghiệp vụ có thể dự kiến

Lỗi nghiệp vụ thường có **quy tắc xác định** và **cách phản hồi hợp lệ**:

- Input số lượng âm → từ chối bằng `IllegalArgumentException` hoặc mã validation.
- Không tìm thấy file cấu hình → `IOException` hoặc một exception ở abstraction cấu hình.
- Người dùng không có quyền → lỗi quyền truy cập phù hợp.
- Tác vụ hết hạn chờ → chính sách timeout/retry có kiểm soát.

Trong khi đó:

- `OutOfMemoryError`: cấp phát không được đáp ứng. Thậm chí thao tác log hoặc tạo response có thể tiếp tục thất bại.
- `StackOverflowError`: stack của luồng có thể đã cạn; đường gọi phức tạp trong handler có thể không an toàn.
- `LinkageError`: bytecode/dependency/runtime có thể không tương thích. Retry cùng bản triển khai thường không sửa được.
- `AssertionError`: có thể chỉ ra một bất biến lập trình đã bị phá vỡ; dữ liệu và side effects của tác vụ cần được kiểm tra.

Những loại trên đều là **`Error`** nhưng nguồn phát sinh khác nhau. Đừng dùng một phán đoán tuyệt đối như “mọi `Error` đều chấm dứt JVM”, cũng đừng dùng một nhánh recovery chung cho tất cả.

## 4. Câu hỏi bắt buộc trước khi viết `catch (Error)` hoặc `catch (Throwable)`

Trước khi đặt một catch rộng, xác định:

1. **Ranh giới ở đâu?** Một phương thức nghiệp vụ, một request, một worker, một test case, hay cả tiến trình?
2. **Ai sở hữu trạng thái?** Có transaction, dữ liệu trung gian, lock, stream hay biến toàn cục chưa hoàn tất không?
3. **Tại sao cần bắt loại rộng?** Vì quan sát, cleanup, chuyển quyền điều phối, hay thực sự có một đường phục hồi?
4. **Có nên ném lại ngay không?** Nếu không, bằng chứng gì cho phép tiếp tục?
5. **Lỗi gốc được bảo toàn thế nào?** Stack trace, cause và suppressed có bị thay thế hoặc mất không?
6. **Nếu cả handler/logging cũng lỗi thì sao?** Đặc biệt khi thiếu bộ nhớ và stack.
7. **Có phân biệt `Error` với `Exception` không?** Không đánh đồng lỗi hệ thống với đầu vào sai.
8. **Ai ra quyết định restart/isolate?** Ứng dụng, supervisor, container orchestrator hay test framework?
9. **Có thể kiểm thử bằng tình huống an toàn không?** Dùng `AssertionError` mô phỏng thay vì cố cạn heap.
10. **Người gọi có bị nhận kết quả thành công giả không?** Đây thường là dấu hiệu design sai.

Một catch rộng không thỏa các câu hỏi này nên được thay bằng catch **những loại exception cụ thể** hoặc được chuyển tới ranh giới quản lý hợp lý.

## 5. `catch (Exception)` thường là lựa chọn hợp lý hơn ở nghiệp vụ

Một request handler có thể xử lý:

~~~java
// Đoạn minh họa, phụ thuộc processRequest và response classes.
try {
    return processRequest();
} catch (IllegalArgumentException ex) {
    return invalidRequest(ex.getMessage());
} catch (java.io.IOException ex) {
    return temporaryFailure();
} catch (Exception ex) {
    reportUnexpectedException(ex);
    return internalFailure();
}
~~~

Cấu trúc này vẫn đòi hỏi chính sách rõ cho mỗi loại, nhưng **`Error` không bị bắt bởi `catch (Exception)`**. Nó tránh biến những lỗi JVM/linkage thành response “lỗi 500 thông thường” rồi cứ tiếp tục phục vụ như không có gì.

Không phải `catch (Exception)` luôn phù hợp: nó vẫn rất rộng so với `IOException` hay `IllegalArgumentException` và có thể nuốt cả các lỗi lập trình thuộc `RuntimeException`. Trong thư viện thấp tầng, nên ưu tiên catch **loại cụ thể mà bạn có thể xử lý**.

Ngược lại, một controller hoặc boundary thực thi có thể cần bắt `Exception` để ghi log/tạo response cuối cùng, miễn là không hứa khôi phục trạng thái chưa kiểm chứng.

## 6. Trường hợp bắt `Error` để ghi nhận rồi ném lại

Hãy tách xử lý đầu vào thông thường với lỗi bất biến nghiêm trọng.

Chương trình Java 17 hoàn chỉnh `BoundaryRethrowDemo.java`:

~~~java
public class BoundaryRethrowDemo {
    static void runTask(String mode) {
        if ("invalid".equals(mode)) {
            throw new IllegalArgumentException("tham số sai");
        }
        if ("bug".equals(mode)) {
            throw new AssertionError("bất biến hỏng");
        }
        System.out.println("Tác vụ hoàn thành");
    }

    static void boundary(String mode) {
        try {
            runTask(mode);
        } catch (IllegalArgumentException ex) {
            System.out.println("Xử lý đầu vào: " + ex.getMessage());
        } catch (Error err) {
            System.out.println("Quan sát lỗi nghiêm trọng: "
                    + err.getClass().getSimpleName());
            throw err;
        }
    }

    public static void main(String[] args) {
        boundary("ok");
        boundary("invalid");
        try {
            boundary("bug");
        } catch (AssertionError err) {
            System.out.println("Tầng gọi nhận lại: " + err.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 BoundaryRethrowDemo.java
java BoundaryRethrowDemo
~~~

Stdout chính xác:

~~~text
Tác vụ hoàn thành
Xử lý đầu vào: tham số sai
Quan sát lỗi nghiêm trọng: AssertionError
Tầng gọi nhận lại: bất biến hỏng
~~~

Cách hiểu:

- `mode="ok"`: tác vụ kết thúc thành công.
- `mode="invalid"`: một `IllegalArgumentException` được bắt ở boundary và xử lý như trường hợp đầu vào sai.
- `mode="bug"`: tạo `AssertionError`; `catch (Error)` chỉ ghi nhận rồi dùng `throw err;`, **không đổi loại đối tượng** và không coi tác vụ đã thành công.
- `main` có handler rất cụ thể `catch (AssertionError)` để **minh họa** rằng lỗi đã được chuyển lên tầng gọi. Trong production, việc tầng ngoài làm gì còn phụ thuộc chính sách dừng tác vụ/tiến trình.

**Cảnh báo:** nhánh ghi log dùng `System.out.println` cho ví dụ dễ thấy; không nên suy rằng luôn có thể in log khi thực sự gặp `OutOfMemoryError`. Một ranh giới cấp production cần phương án giám sát tối giản và không được dựa vào khả năng cấp phát bộ nhớ không giới hạn.

## 7. `throw err;` khác với `throw new RuntimeException(err);`

Xét hai lựa chọn sau trong cùng một `catch(Error err)`:

~~~java
// Truyền chính đối tượng Error ban đầu ra ngoài.
throw err;
~~~

~~~java
// Tạo wrapper RuntimeException mới; thường không nên làm với Error nghiêm trọng.
throw new RuntimeException("lỗi tác vụ", err);
~~~

Khác biệt:

| Tiêu chí | `throw err` | `throw new RuntimeException(..., err)` |
| --- | --- | --- |
| Loại đối tượng ngoài cùng | Giữ loại `Error` gốc | `RuntimeException` mới |
| Danh tính đối tượng | Giữ nguyên đối tượng | Đối tượng wrapper mới |
| Cause | Không tạo liên kết mới | `Error` được giữ dưới `getCause()` nếu constructor đúng |
| `catch (Exception)` ở caller | Không bắt trực tiếp `Error` gốc | Có thể bắt wrapper `RuntimeException` |
| Nguy cơ | Lỗi tiếp tục lan đúng loại | Có thể làm caller xử lý như exception nghiệp vụ nếu không xem cause |

**Không bao bọc `OutOfMemoryError` hoặc `LinkageError` thành `RuntimeException` chỉ để chúng lọt vào một bộ xử lý “Exception chung”.** Điều đó thay đổi thông tin phân loại và có thể che một sự cố hệ thống nghiêm trọng. Nếu một framework có hợp đồng bao `Throwable`, người tích hợp cần tra nguyên nhân thực tế trước khi quyết định recovery.

Bản thân thao tác tạo wrapper cũng cấp phát một đối tượng mới — không đáng tin khi đang thiếu bộ nhớ nghiêm trọng.

## 8. Khi nào `catch (Throwable)` có thể có mục đích chính đáng?

`catch (Throwable)` bắt cả nhánh `Exception` lẫn `Error`. Một số ranh giới **có thể** cần quan sát mọi `Throwable`:

- Test runner thu thập thất bại của một test case để báo cáo toàn bộ suite.
- Framework điều phối tác vụ nhận một báo cáo lỗi cuối cùng từ worker.
- Một lớp quản lý lifecycle nhận thất bại của plugin sandbox đã được kiểm soát chặt.
- Một đoạn cleanup/giám sát cuối ở nơi cần ghi nhận lỗi trước khi chuyển quyền xử lý.
- Bộ thực thi task/future cần lưu trữ `Throwable` theo hợp đồng API.

Nhưng điều kiện áp dụng nghiêm ngặt: **nhận mọi `Throwable` không có nghĩa là phục hồi được mọi `Throwable`**. Có những lỗi fatal mà sau khi ghi nhận, process nên ngừng, để supervisor bên ngoài xử lý.

Nếu chỉ cần ghi nhận rồi truyền tiếp, mẫu:

~~~java
// Mã minh họa khi phương thức runTask() có thể ném Exception.
try {
    runTask();
} catch (Throwable failure) {
    observeMinimal(failure);
    throw failure;
}
~~~

Ở đây `observeMinimal` cần là phương thức giám sát đã được định nghĩa. Đoạn này **không độc lập**. Khi `runTask()` có checked exception, phương thức chứa phải thỏa catch-or-declare. Với `throw failure` của một tham số catch effectively final, Java có quy tắc **precise rethrow**, cho phép compiler phân tích các loại checked thực sự có thể thoát từ vùng `try`. Không được nghĩ rằng `catch(Throwable)` làm mọi checked exception biến mất.

## 9. Java 17: quan sát cả `Exception` và `Error` rồi truyền đúng loại

Tệp `ThrowableGuardDemo.java`:

~~~java
public class ThrowableGuardDemo {
    static void simulate(String mode) throws Exception {
        if ("checked".equals(mode)) {
            throw new Exception("lỗi kiểm tra");
        }
        if ("error".equals(mode)) {
            throw new AssertionError("lỗi bất biến");
        }
    }

    static void observeAndPropagate(String mode) throws Exception {
        try {
            simulate(mode);
        } catch (Throwable failure) {
            System.out.println("Quan sát: " + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    public static void main(String[] args) {
        for (String mode : new String[]{"checked", "error"}) {
            try {
                observeAndPropagate(mode);
            } catch (Exception ex) {
                System.out.println("Bên ngoài Exception: " + ex.getMessage());
            } catch (Error err) {
                System.out.println("Bên ngoài Error: " + err.getMessage());
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 ThrowableGuardDemo.java
java ThrowableGuardDemo
~~~

Stdout chính xác:

~~~text
Quan sát: Exception
Bên ngoài Exception: lỗi kiểm tra
Quan sát: AssertionError
Bên ngoài Error: lỗi bất biến
~~~

Phân tích:

1. `simulate()` có thể tạo `Exception` checked hoặc `AssertionError` unchecked.
2. `catch(Throwable)` ở `observeAndPropagate()` nhận cả hai trường hợp, in loại thực và **ném lại cùng đối tượng**.
3. Chữ ký `observeAndPropagate() throws Exception` đủ cho đường checked; không cần khai báo `throws Error`.
4. Caller `main()` bắt được từng loại theo **quan hệ runtime**: `Exception` đi vào `catch(Exception)`; `AssertionError` đi vào `catch(Error)`.
5. Ví dụ mô phỏng quan sát rồi chuyển tiếp, không chứng minh mọi `Error` trong ứng dụng nên được xử lý bằng cách bắt loại rộng.

Đừng lạm dụng `throws Throwable` trong public API chỉ vì đã dùng `catch(Throwable)`. Việc mở rộng checked contract tới `Throwable` có thể buộc caller xử lý một tập kiểu quá rộng và làm khó bảo trì.

## 10. Quy tắc thứ tự `catch` cũng quan trọng

Nếu cùng một `try` có cả handler cụ thể và handler bao quát, phải đặt handler **cụ thể trước handler rộng**:

~~~java
try {
    runTask();
} catch (IllegalArgumentException ex) {
    // Xử lý input sai.
} catch (Exception ex) {
    // Những Exception khác.
} catch (Error err) {
    // Quan sát Error nếu boundary thật sự cần.
}
~~~

Đây là đoạn minh họa, `runTask()` phải tồn tại. Có thể dùng `catch(Throwable)` cuối nếu API ranh giới có chính sách đặc biệt, nhưng đừng tự thêm vào mọi method.

Ví dụ không hợp lệ:

~~~java
// CỐ Ý SAI: nhánh cụ thể bị bao phủ bởi catch rộng trước đó.
try {
    runTask();
} catch (Throwable t) {
    // ...
} catch (Exception ex) {
    // Unreachable catch: Exception đã được Throwable bao phủ.
}
~~~

Compiler từ chối nhánh `catch(Exception)` vì không thể đến được. Bên cạnh yêu cầu ngôn ngữ, cách sắp xếp cụ thể → rộng cũng giúp code review nhìn rõ loại lỗi nào được chủ động xử lý và loại nào chỉ được quan sát.

## 11. `finally` và `try-with-resources` thường phù hợp hơn catch rộng để cleanup

Mục tiêu của `finally` và `try-with-resources` không phải quyết định một lỗi có recoverable hay không; chúng cung cấp đường **thu dọn tài nguyên** khi scope thoát bình thường hoặc bất thường theo ngữ nghĩa Java.

~~~java
// Đoạn minh họa.
try {
    doWork();
} finally {
    closeLocalResources();
}
~~~

Nếu `doWork()` ném `AssertionError`, JVM bình thường cố chạy `finally` trước khi truyền tiếp lỗi. Nhưng:

- `finally` có thể **không chạy** nếu tiến trình bị kill, `Runtime.halt()` xảy ra hoặc JVM không thể tiếp tục.
- Nếu code trong `finally` tự ném lỗi, lỗi mới **có thể che lỗi gốc** trừ khi có thiết kế bảo toàn.
- Một `closeLocalResources()` quá phức tạp có thể gây hỏng trạng thái phụ, làm việc điều tra khó hơn.
- Cleanup không tự chứng minh rằng mọi side effect hoặc transaction đã rollback.

Đối với tài nguyên implement `AutoCloseable`, `try-with-resources` có khả năng giữ ngoại lệ chính và gắn lỗi đóng tài nguyên dưới dạng **suppressed**. Đây thường là lựa chọn tốt để giảm rủi ro che nguyên nhân.

## 12. Java 17: `Error` chính và lỗi `close()` là suppressed

Tệp `CleanupErrorDemo.java`:

~~~java
import java.io.IOException;

public class CleanupErrorDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đã gọi close()");
            throw new IOException("đóng lỗi");
        }
    }

    public static void main(String[] args) throws IOException {
        try (Resource resource = new Resource()) {
            throw new AssertionError("lỗi chính");
        } catch (AssertionError err) {
            System.out.println("Chính: " + err.getMessage());
            System.out.println("Số suppressed: " + err.getSuppressed().length);
            System.out.println("Phụ: " + err.getSuppressed()[0].getMessage());
        } finally {
            System.out.println("finally kết thúc");
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 CleanupErrorDemo.java
java CleanupErrorDemo
~~~

Stdout chính xác:

~~~text
Đã gọi close()
Chính: lỗi chính
Số suppressed: 1
Phụ: đóng lỗi
finally kết thúc
~~~

Giải thích:

- Phần thân `try` ném `AssertionError`, là lỗi chính.
- Khi rời scope, `close()` được gọi và ném `IOException`.
- `IOException` được thêm vào **`getSuppressed()` của `AssertionError`**, chứ không tự động thay lỗi chính.
- `catch(AssertionError)` trong ví dụ chỉ đọc cấu trúc lỗi để giảng giải; trong ứng dụng thông thường, có thể để lỗi lan tiếp sau cleanup.
- `main throws IOException` vẫn cần có để compiler chấp nhận lời gọi `close()` ngầm có checked `IOException`. Đừng hiểu rằng một `Error` được ném trong body làm các nghĩa vụ checked của resource close biến mất.
- `getSuppressed()[0]` ở ví dụ an toàn vì chính `close()` cố định ném một ngoại lệ. Code tổng quát phải kiểm tra mảng trước khi truy cập.

Một `Error` có thể có suppressed exception giống các `Throwable` khác; tuy nhiên điều này không biến lỗi tài nguyên thành **cause** của Error. `getCause()` và `getSuppressed()` mô tả hai quan hệ khác nhau.

## 13. Vì sao không nên nuốt lỗi trong `finally`?

Đoạn sau gây rủi ro:

~~~java
// Anti-pattern: lỗi trong finally có thể che lỗi gốc.
try {
    throw new AssertionError("nguyên nhân chính");
} finally {
    throw new IllegalStateException("cleanup thất bại");
}
~~~

Khi `finally` ném `IllegalStateException`, lý do thoát sau cùng của biểu thức là lỗi mới, còn `AssertionError` trước đó **không tự động được gắn làm cause/suppressed**.

Thay vì dùng `return` hoặc `throw` tùy tiện trong `finally`, hãy ưu tiên `try-with-resources` khi phù hợp. Nếu bắt buộc cleanup thủ công, thiết kế để **bảo toàn lỗi chính** và xử lý lỗi cleanup đúng cách; việc dùng `Throwable.addSuppressed()` cũng cần thận trọng với vòng tham chiếu và trường hợp suppress bị vô hiệu hóa trong custom throwable.

**Không dùng `return` trong `finally` để “ổn định” chương trình.** Nó có thể thay thế cả kết quả và đường ngoại lệ, khiến lỗi nghiêm trọng bị nuốt.

## 14. Ranh giới thread: một `Error` không bắt có thể kết thúc chỉ một luồng

Java cho phép đặt `Thread.UncaughtExceptionHandler` để nhận `Throwable` khi một luồng kết thúc bất thường mà không có handler xử lý trước.

Chương trình `WorkerUncaughtDemo.java`:

~~~java
public class WorkerUncaughtDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                () -> { throw new AssertionError("worker lỗi"); }, "worker-196");

        worker.setUncaughtExceptionHandler((thread, failure) ->
                System.out.println("Uncaught " + thread.getName() + ": "
                        + failure.getClass().getSimpleName()));

        worker.start();
        worker.join();
        System.out.println("Main còn chạy");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 WorkerUncaughtDemo.java
java WorkerUncaughtDemo
~~~

Stdout chính xác:

~~~text
Uncaught worker-196: AssertionError
Main còn chạy
~~~

**Diễn giải đúng:**

- `worker` ném `AssertionError` không được bắt, nên kết thúc bất thường.
- Uncaught handler nhận lỗi của worker và in thông điệp.
- `join()` của main hoàn tất sau khi worker kết thúc; main tiếp tục.
- Sự thật “main còn chạy” **không chứng minh trạng thái chia sẻ còn nhất quán** hoặc có thể tự động chạy lại công việc đã thất bại.
- Handler là chỗ **ghi nhận thất bại của luồng**, không phải một phương thức có thể tiếp tục chạy chính luồng đó sau câu lệnh ném lỗi.

Đối với `ExecutorService` hoặc `Future`, lỗi của task có thể được ghi nhận theo hợp đồng executor/future và chỉ lộ ra khi truy xuất kết quả bằng những API như `Future.get()`. Không nên giả định mọi task failure đều đi qua cùng một uncaught handler như ví dụ `Thread` thuần.

## 15. Ranh giới test/plugin được cô lập: khi nào tiếp tục có lý do?

Một test runner có thể **chủ động thiết kế** để một test thất bại vì `AssertionError` nhưng suite vẫn tiếp tục. Điều đó khác với việc production nuốt bất kỳ `Error` nào trong core business logic.

Chương trình Java 17 `IsolatedPluginDemo.java` chỉ dùng một **fixture nội bộ**, không gọi mã plugin bên ngoài:

~~~java
public class IsolatedPluginDemo {
    interface Plugin {
        void execute();
    }

    static boolean runIsolatedFixture(Plugin plugin) {
        try {
            plugin.execute();
            return true;
        } catch (AssertionError err) {
            System.out.println("Plugin thử nghiệm thất bại: "
                    + err.getClass().getSimpleName());
            return false;
        }
    }

    public static void main(String[] args) {
        boolean first = runIsolatedFixture(
                () -> { throw new AssertionError("fixture"); });
        boolean second = runIsolatedFixture(
                () -> System.out.println("Plugin thử nghiệm OK"));
        System.out.println("Kết quả: " + first + ", " + second);
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 IsolatedPluginDemo.java
java IsolatedPluginDemo
~~~

Stdout:

~~~text
Plugin thử nghiệm thất bại: AssertionError
Plugin thử nghiệm OK
Kết quả: false, true
~~~

Ví dụ cho thấy: ở một boundary có **mục tiêu rõ là ghi nhận lỗi fixture**, catch **đúng `AssertionError`** và tiếp tục fixture sau có thể hợp lý. Nhưng tên `IsolatedPluginDemo` không có nghĩa cùng JVM tạo sandbox bảo mật, tách bộ nhớ hay cô lập side effects của plugin không đáng tin. **Đây không phải một sandbox thực sự**; nó chỉ cô lập *luồng điều khiển và biến cục bộ của demo*.

Với plugin không đáng tin, một `catch(Throwable)` trong cùng JVM **không đủ cách ly**: plugin có thể sửa trạng thái tĩnh, tạo thread, giữ tài nguyên, block vô hạn, làm cạn heap hoặc gọi API nguy hiểm. Cần kiểm soát quyền hạn và có thể cần **tách process/container** theo yêu cầu hệ thống, cùng chính sách timeout/kill/restart. Đừng quảng cáo `catch(Error)` như cơ chế cô lập lỗi đáng tin.

## 16. Phân loại lỗi sau khi `catch(Throwable)`: tránh một nhánh “thành công giả”

Nếu một framework buộc phải quan sát mọi `Throwable`, nó cần **phân loại theo khả năng xử lý** thay vì trả `true` bất kể lỗi.

Một logic gợi ý (không phải code framework hoàn chỉnh):

~~~java
// Mã minh họa ở boundary có chính sách riêng.
try {
    invokePlugin();
} catch (java.io.IOException ex) {
    reportRecoverableFailure(ex);
} catch (Error fatal) {
    reportCriticalFailureBestEffort(fatal);
    throw fatal;
} catch (Exception ex) {
    reportUnexpectedException(ex);
    throw ex;
}
~~~

Các hàm phụ thuộc ứng dụng; method bao ngoài có thể cần khai báo `throws Exception` nếu có checked exception thoát. Điều quan trọng là **tách bản chất lỗi**: nếu code phát hiện `Error`, nó không vội chuyển thành trạng thái “plugin thất bại bình thường, cứ dùng tiếp”.

Không nhất thiết phải catch toàn bộ `Error` để phân loại: một boundary có thể để Error lan ra và dùng giám sát ngoài process. Chiến lược ghi nhận `Error` trong process chỉ phù hợp nếu chi phí và rủi ro được hiểu rõ.

Trường hợp đặc biệt: `ThreadDeath` là subclass của `Error`. Tài liệu Java nhấn mạnh nếu ứng dụng bắt `ThreadDeath`, thường nên **ném lại** để cơ chế dừng luồng được tiếp tục; tránh chặn/nuốt lỗi này. Tương tự, đừng bắt `VirtualMachineError` chỉ để thử “phục hồi chung”.

## 17. Catch rộng và `InterruptedException`: đừng phá hỏng tín hiệu hủy

Một catch rất rộng có thể gây vấn đề ngay cả với loại **`Exception`**. `InterruptedException` là checked exception thường báo một luồng đang bị yêu cầu gián đoạn khi block trong những API như `sleep()` hoặc `wait()`.

Ví dụ trong một worker:

~~~java
try {
    Thread.sleep(10_000);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là đoạn minh họa nằm trong method `void` thích hợp. Việc **khôi phục interrupt flag** bằng `Thread.currentThread().interrupt()` trước khi rời scope là một lựa chọn phổ biến khi tầng hiện tại không thể khai báo truyền `InterruptedException` lên trên. Nếu hợp đồng cho phép, có thể `throw ex;` để caller xử lý đúng.

Một `catch(Exception ex)` hoặc `catch(Throwable t)` rồi bỏ qua có thể che tín hiệu hủy tác vụ. Dù đây không phải `Error`, nó minh họa lý do catch rộng phải có **mục đích và chính sách** thay vì xử lý mọi lỗi bằng một đường duy nhất.

**Không đồng nhất `InterruptedException` với `ThreadDeath`**: chúng là hai loại và cơ chế rất khác nhau; `Thread.stop()` gây `ThreadDeath` đã bị deprecated vì mất an toàn. Trong code mới, ưu tiên cơ chế hủy hợp tác như interrupt, flag hủy hoặc API task cancellation phù hợp.

## 18. Ranh giới giao dịch và side effects: “đã catch” chưa phải rollback

Giả sử một thao tác:

~~~text
Đọc yêu cầu
    ↓
Ghi một phần dữ liệu
    ↓
Gửi thông báo ngoài hệ thống
    ↓
Lỗi nghiêm trọng được ném
    ↓
catch(Throwable) { return success; }
~~~

Việc `catch` thành công **không hoàn tác** hai bước đã thực hiện. Nếu caller thấy thành công và thử gửi yêu cầu khác, trạng thái có thể còn rối hơn.

Khi cân nhắc recovery:

1. Giao dịch lưu trữ có thực sự rollback tự động không?
2. Side effect ngoài transaction (email, thanh toán, message queue) có thể đã xảy ra chưa?
3. Có định danh **idempotency** để tránh thực hiện trùng không?
4. Có resource/lock còn giữ không?
5. Có thể xác định thao tác đã hoàn tất hay chưa?
6. Nếu không biết, có cần chuyển sang trạng thái **unknown/pending investigation** thay vì thành công không?

Một boundary đáng tin có thể **dừng xử lý tác vụ**, ghi nhận thất bại và để quy trình đối soát/giám sát quyết định bước tiếp theo. Bắt `Throwable` rồi trả mặc định hiếm khi là giải pháp đúng cho công việc có side effects.

## 19. `OutOfMemoryError`: vì sao cleanup/logging trong `catch` cũng có thể thất bại?

Trong trường hợp cạn bộ nhớ, rất nhiều hành động thông thường cần cấp phát: tạo chuỗi, định dạng stack trace, serialize JSON, tạo exception wrapper mới hoặc ghi log bất đồng bộ.

Vì thế:

~~~java
// Anti-pattern: KHÔNG coi đây là chiến lược khôi phục OOM.
try {
    runHeavyTask();
} catch (OutOfMemoryError err) {
    logger.error("Thất bại: " + err.getMessage(), err);
    return new LargeFallbackResponse();
}
~~~

Đoạn minh họa này phụ thuộc `runHeavyTask`, `logger`, `LargeFallbackResponse` và không phải chương trình chạy. Nó không an toàn để coi là chính sách tổng quát: **chính xử lý bên trong catch có thể tiếp tục yêu cầu bộ nhớ**.

Thực hành tốt hơn: thiết lập **monitoring, resource limits, heap dump khi phù hợp, restart policy và health check** trước khi sự cố xảy ra. Đối với hệ thống cần chịu lỗi ở cấp tác vụ, cân nhắc cô lập workload trong process/container để supervisor ngoài tiến trình kiểm soát khi một instance không còn đáng tin.

Không thể khẳng định mọi `OutOfMemoryError` đều chắc chắn làm JVM chết; nhưng **không có bảo đảm an toàn để tiếp tục chỉ vì đã catch được nó**.

## 20. `LinkageError` và lỗi khởi tạo lớp: retry cùng một class không giải quyết vấn đề

Một `LinkageError` thường liên quan tới phiên bản dependency, trạng thái classloader hoặc bytecode. Ví dụ:

- `NoSuchMethodError` khi mã gọi method không tồn tại ở runtime version của library.
- `UnsupportedClassVersionError` khi JVM không hỗ trợ class file version.
- `NoClassDefFoundError` khi không có định nghĩa class phù hợp hoặc class đã khởi tạo thất bại.
- `ExceptionInInitializerError` khi static initialization kết thúc bất thường với nguyên nhân không phải Error.

Giả sử một static initializer đã ném `IllegalStateException`. JVM có thể báo `ExceptionInInitializerError` ở lần sử dụng đầu, rồi **`NoClassDefFoundError`** ở lần sử dụng tiếp trong cùng classloader vì class ở trạng thái erroneous.

Một `catch(Throwable)` rồi thử truy cập class đó lần nữa **không làm chạy lại static initializer bình thường**. Việc khôi phục thường phải sửa cấu hình/deployment/lifecycle, không phải thêm retry cục bộ mù quáng.

Nếu gặp `NoClassDefFoundError`, đừng mặc định “thiếu file .class”; cần kiểm tra lỗi đầu tiên, classpath/module path và log class initialization. Loại `Error` ngoài cùng không phải luôn cung cấp toàn bộ nguyên nhân.

## 21. Đọc `cause` và `suppressed` ở một boundary

Một `Throwable` cung cấp nhiều góc chẩn đoán:

- `getClass()`: loại đối tượng thực tế đang quan sát.
- `getMessage()`: thông điệp chẩn đoán; không nên parse như mã lỗi ổn định.
- `getCause()`: ngoại lệ/Throwable được gắn là nguyên nhân trực tiếp, nếu có.
- `getSuppressed()`: các lỗi phụ, thường gặp khi cleanup trong `try-with-resources`.
- Stack trace: các frame liên quan tới đường xảy ra lỗi.

Khi lỗi được bọc, outermost exception có thể thuộc nhánh `Exception` nhưng cause sâu hơn lại là `Error`. Do đó một boundary không nên quyết định “lỗi có thể retry” chỉ dựa trên việc **wrapper ngoài cùng** là `Exception`.

Ví dụ:

~~~java
// Minh họa bên trong một phương thức.
Throwable fatal = new AssertionError("bất biến hỏng");
Exception wrapper = new Exception("tác vụ thất bại", fatal);

System.out.println(wrapper instanceof Exception);  // true
System.out.println(wrapper.getCause() instanceof Error); // true
~~~

Hai dòng trên sẽ in `true` và `true`. Đây là **mã minh họa không có lớp/main đầy đủ**. Điều cần thấy là **wrapper thay đổi loại ngoài cùng nhưng không làm lỗi bên dưới trở nên recoverable**.

Nếu muốn duyệt toàn bộ cause chain và suppressed của một `Throwable` bất kỳ, nên có cơ chế tránh tham chiếu lặp/vòng bất thường; không dùng vòng lặp `getCause()` vô hạn đối với dữ liệu không kiểm soát.

## 22. `Thread.UncaughtExceptionHandler` không phải phương thức sửa lỗi tự động

Uncaught handler có thể nhận `Throwable` khi thread kết thúc bất thường. Nó phù hợp để **ghi nhận sự kiện** và cập nhật trạng thái supervisor. Nhưng:

- Handler không quay lại chính câu lệnh đã ném.
- Handler chạy theo cơ chế xử lý uncaught; nó không biến exception thành kết quả thành công của worker.
- Một uncaught handler có thể tự lỗi; đừng phụ thuộc vào các thao tác nặng hoặc cấp phát lớn.
- Nếu worker sở hữu dữ liệu dùng chung, các luồng khác có thể bị ảnh hưởng.
- Nếu một tác vụ do `ExecutorService` quản lý, lỗi có thể được bao trong `Future` thay vì luôn đi qua thread uncaught handler.

Vì vậy cần **thiết kế ranh giới theo cơ chế thực thi đang dùng** (Thread trực tiếp, executor, actor, job queue, test runner), không sao chép một khối `catch(Throwable)` chung tới mọi vị trí.

## 23. Cây quyết định khi xem xét một catch rộng

~~~text
Một thao tác có thể thất bại
        |
        v
Biết loại lỗi và có cách xử lý hợp đồng?
        |
        +-- Có --> Bắt loại cụ thể (IOException, domain exception...)
        |             |
        |             +--> Khôi phục/biến đổi/lỗi có cấu trúc, theo chính sách
        |
        +-- Không --> Có thật sự cần quan sát mọi Throwable ở boundary?
                      |
                      +-- Không --> Để lỗi lan truyền; dùng finally/TWR cleanup
                      |
                      +-- Có --> Ranh giới đã được xác định rõ?
                                    |
                                    +-- Không --> Thiết kế lại boundary
                                    |
                                    +-- Có --> Ghi nhận tối thiểu
                                                  |
                                                  +--> Lỗi Error nghiêm trọng?
                                                  |       |
                                                  |       +--> Thường rethrow/đánh dấu
                                                  |             thành phần không khỏe
                                                  |
                                                  +--> Exception có chính sách?
                                                          |
                                                          +--> Bắt/biến đổi theo contract
~~~

Sơ đồ là **công cụ tư duy**, không phải thuật toán tự động phân loại mọi exception. Một số `Error` như `AssertionError` được dùng có chủ đích trong test runner; một số lỗi `Exception` cũng có thể báo trạng thái không đáng tin và không nên retry. Các quyết định đều phải xét **ngữ cảnh**.

## 24. Những anti-pattern thường gặp

| Anti-pattern | Rủi ro | Cách sửa |
| --- | --- | --- |
| `catch(Throwable) { }` | Nuốt mọi lỗi, kể cả `Error` nghiêm trọng | Bắt cụ thể; nếu cần quan sát, rethrow hoặc báo trạng thái thất bại |
| `catch(Error) { return fallback; }` trong mọi request | Giả vờ hệ thống khỏe dù có thể hết bộ nhớ/linkage lỗi | Để Error lan truyền và dùng chính sách supervisor |
| `catch(Throwable)` rồi convert mọi thứ thành HTTP 200 | Che lỗi và side effects chưa hoàn tất | Trả lỗi có cấu trúc chỉ cho tình huống có hợp đồng hợp lệ |
| `catch(OutOfMemoryError)` rồi tạo hàng loạt đối tượng để log | Handler có thể lại cạn bộ nhớ | Giám sát/dump/restart được chuẩn bị sẵn; giảm xử lý trong process |
| Retry ngay cùng lời gọi sau `LinkageError` | Không sửa được dependency/classloader sai | Kiểm tra build/deploy, sửa version, restart phù hợp |
| Bọc `Error` thành `RuntimeException` vô điều kiện | Che loại nghiêm trọng khiến catch(Exception) xử lý như nghiệp vụ | Giữ nguyên loại/propagate hoặc dùng hợp đồng framework minh bạch |
| Dùng `return` trong `finally` | Có thể nuốt Throwable đang lan truyền | Dùng cleanup rõ, tránh return/throw đè lỗi gốc |
| Cleanup ném lỗi mới che lỗi chính | Mất dấu vết nguyên nhân | `try-with-resources`, suppressed hoặc chiến lược bảo toàn lỗi |
| Chỉ ghi `getMessage()` | Mất stack trace, cause, suppressed | Ghi nguyên Throwable tại boundary thích hợp |
| Thấy worker thread kết thúc nhưng main còn chạy, rồi coi hệ thống an toàn | State dùng chung có thể hỏng | Đánh giá transaction, lock, resource và consistency |
| Dùng `catch(Throwable)` như sandbox cho plugin không đáng tin | Không cô lập heap, threads và side effects | Quản lý quyền/tài nguyên và cân nhắc process isolation |
| Nuốt `InterruptedException` cùng các loại khác | Mất tín hiệu hủy hợp tác | Propagate hoặc khôi phục interrupt flag |
| Nuốt `ThreadDeath` | Có thể cản cơ chế dừng luồng legacy | Tránh `Thread.stop`; nếu bắt phải có lý do và thường rethrow |
| Cho rằng `Error` không thể ném thủ công | Đánh giá sai `AssertionError` trong test | Đọc loại thực tế và nguồn ném |
| Cho rằng catch được OOM nghĩa là bộ nhớ đã phục hồi | Không có bảo đảm tài nguyên khả dụng | Giám sát, giới hạn tài nguyên, quản lý lifecycle |

## 25. Checklist review code có `catch(Error)` hoặc `catch(Throwable)`

Trước khi phê duyệt:

1. Tên method/class có cho thấy đây là **boundary điều phối** (runner, supervisor, framework) hay chỉ là method nghiệp vụ?
2. Có **chính sách loại lỗi** rõ ràng không, đặc biệt cho `VirtualMachineError` và `LinkageError`?
3. Handler chỉ ghi nhận/cleanup hay trả **thành công giả**?
4. Nếu handler không ném lại, có **cách ly trạng thái thật sự** để cho phép tiếp tục không?
5. Cleanup có giữ lỗi chính nếu chính cleanup ném lỗi không?
6. Có đoạn nào `return` hoặc `throw` trong `finally` có thể che `Error` gốc không?
7. Khi cần log, có giữ **class, stack trace, cause và suppressed** không?
8. Có hạn chế chi phí log, tránh cấp phát lớn khi thiếu tài nguyên không?
9. Có tránh retry không kiểm soát sau `OutOfMemoryError`, `StackOverflowError`, `LinkageError` không?
10. Có khác biệt giữa thread thực tế, executor/future và plugin runner không?
11. `InterruptedException` và `ThreadDeath` có được bảo toàn đúng chính sách không?
12. Có kiểm thử cả đường **thành công, lỗi recoverable và lỗi mô phỏng nghiêm trọng** không?
13. Các kiểm thử có **không cố gây cạn heap/stack** trên máy dùng chung không?
14. Ai sở hữu quyết định **dừng, restart, đánh dấu unhealthy hoặc đối soát** khi boundary không thể bảo đảm an toàn?

Có thể xem một nhánh `catch(Throwable)` không có câu trả lời cho những câu hỏi trên là một **rủi ro thiết kế**, ngay cả khi code biên dịch và test đường thành công đều chạy.

## 26. Tự kiểm tra kiến thức

**Câu 1.** `catch(Exception)` có bắt `OutOfMemoryError` không?

**Đáp án:** Không. `Error` không nằm dưới `Exception`.

**Câu 2.** `catch(Throwable)` có bắt cả `Exception` lẫn `Error` không?

**Đáp án:** Có. Chính vì thế nó cần boundary và mục đích rõ ràng.

**Câu 3.** Bắt một `Error` có chứng minh ứng dụng có thể tiếp tục bình thường không?

**Đáp án:** Không. Cần xác định loại lỗi, tài nguyên và trạng thái sau thất bại.

**Câu 4.** Vì sao test runner có thể bắt `AssertionError` của một test case mà không phải mọi web request handler đều nên bắt `Error`?

**Đáp án:** Test runner có contract **ghi nhận test thất bại** và có ranh giới test; request handler không mặc định có khả năng phục hồi sau mọi lỗi JVM hoặc lỗi bất biến.

**Câu 5.** `throw err` từ `catch(Error err)` có tạo wrapper mới không?

**Đáp án:** Không. Nó ném lại chính đối tượng Error.

**Câu 6.** `throw new RuntimeException("x", err)` có thể làm `catch(Exception)` bên ngoài bắt wrapper không?

**Đáp án:** Có; nhưng một `Error` trong cause vẫn có thể báo tình trạng nghiêm trọng.

**Câu 7.** `finally` có chạy khi phần thân ném `AssertionError` trong đường thực thi Java bình thường không?

**Đáp án:** Thông thường có. Nhưng không bảo đảm khi JVM bị dừng cưỡng bức hoặc không còn khả năng tiếp tục.

**Câu 8.** Nếu `close()` ném `IOException` trong lúc phần thân `try-with-resources` ném `AssertionError`, lỗi đóng nằm ở đâu?

**Đáp án:** Thường ở `getSuppressed()` của lỗi chính `AssertionError`.

**Câu 9.** Worker thread ném `AssertionError` không bắt, main có thể tiếp tục không?

**Đáp án:** Có. Nhưng không có bảo đảm trạng thái chia sẻ và side effects vẫn hợp lệ.

**Câu 10.** `UncaughtExceptionHandler` có làm worker quay lại chỗ trước khi ném lỗi không?

**Đáp án:** Không. Nó nhận báo cáo lỗi khi luồng kết thúc bất thường.

**Câu 11.** Vì sao `catch(Throwable) { return success; }` nguy hiểm trong tác vụ có thanh toán hoặc ghi dữ liệu?

**Đáp án:** Lỗi có thể xảy ra sau các side effects không rollback; trả thành công giả làm caller hiểu sai trạng thái và có nguy cơ lặp thao tác.

**Câu 12.** Bắt `OutOfMemoryError` rồi tạo đối tượng log/response lớn có đáng tin không?

**Đáp án:** Không. Những bước đó cũng có thể cần cấp phát và thất bại thêm.

**Câu 13.** Khi một task chạy trong `ExecutorService`, có nên trông đợi mọi lỗi đều được uncaught handler thông báo như `Thread` trực tiếp không?

**Đáp án:** Không. Kết quả task và exception có thể được quản lý bởi `Future` hoặc cơ chế riêng của executor.

**Câu 14.** Nếu cần thu dọn resource mà không cần chuyển một `Error` thành kết quả thành công, nên dùng gì?

**Đáp án:** Ưu tiên `finally` hoặc `try-with-resources` đúng hợp đồng, đồng thời bảo toàn lỗi gốc.

**Câu 15.** Một plugin được gọi cùng JVM nhưng quanh nó có `catch(Throwable)` đã thành sandbox an toàn chưa?

**Đáp án:** Chưa. Nó vẫn chia sẻ tài nguyên và trạng thái của JVM; cần kiểm soát thêm, đôi khi phải tách process.

## 27. Tài liệu tham khảo Java 17

- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): phân cấp lỗi, `getCause()`, `getSuppressed()`, stack trace và `addSuppressed()`.
- [Java SE 17 API — `Error`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Error.html): ý nghĩa nhánh `Error` và định hướng ứng dụng không nên bắt đại trà.
- [Java SE 17 API — `Exception`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Exception.html): phân biệt nhánh `Exception`.
- [Java SE 17 API — `VirtualMachineError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/VirtualMachineError.html), [`OutOfMemoryError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/OutOfMemoryError.html), [`StackOverflowError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackOverflowError.html): lỗi tài nguyên JVM.
- [Java SE 17 API — `LinkageError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html), [`NoClassDefFoundError`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): sự cố liên kết và khởi tạo.
- [Java SE 17 API — `Thread.UncaughtExceptionHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html): xử lý uncaught `Throwable` ở mức luồng.
- [Java SE 17 API — `ThreadDeath`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadDeath.html): đặc điểm lịch sử của lỗi dừng luồng.
- [Java Language Specification 17 — §11](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): phân loại checked/unchecked và truyền ngoại lệ.
- [Java Language Specification 17 — §14.20](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): `try`, `catch`, `finally` và `try-with-resources`.
- [Java Language Specification 17 — §12.4.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): khởi tạo class và trạng thái erroneous.

**Kết luận:** `catch(Throwable)` hoặc `catch(Error)` chỉ có ý nghĩa tốt khi được đặt ở **ranh giới đã xác định**, với **mục tiêu cụ thể** như quan sát, cleanup, báo trạng thái thất bại hoặc cô lập một fixture đã được kiểm soát. Bắt được một `Error` không bảo đảm còn đủ tài nguyên hay dữ liệu nhất quán để tiếp tục. Trong code nghiệp vụ, ưu tiên **bắt loại `Exception` cụ thể có cách khôi phục rõ**; với lỗi hệ thống nghiêm trọng, thường để lan truyền, bảo toàn nguyên nhân và dùng giám sát/lifecycle bên ngoài thay vì nuốt lỗi rồi trả về thành công.


</details>

- [Quay lại đầu trang](#back-to-top)
