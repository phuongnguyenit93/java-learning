<a id="back-to-top"></a>

# Chẩn đoán và ghi log

## Menu
- [Đọc stack trace từ loại lỗi và điểm phát sinh](#exception-diagnostics-and-logging-001)
- [Đọc Caused by, Suppressed và các frame được rút gọn](#exception-diagnostics-and-logging-002)
- [Tìm frame thuộc mã ứng dụng và đối chiếu dòng nguồn](#exception-diagnostics-and-logging-003)
- [Thông điệp NullPointerException chi tiết trong JDK hiện đại](#exception-diagnostics-and-logging-004)
- [Ghi cả đối tượng ngoại lệ thay vì chỉ ghi getMessage](#exception-diagnostics-and-logging-005)
- [Thêm ngữ cảnh thao tác và định danh có ích cho điều tra](#exception-diagnostics-and-logging-006)
- [Chọn tầng chịu trách nhiệm ghi log để tránh lặp cùng một lỗi](#exception-diagnostics-and-logging-007)
- [Tránh ghi thông tin bí mật hoặc dữ liệu cá nhân vào message và log](#exception-diagnostics-and-logging-008)
- [Phân biệt thông tin chẩn đoán nội bộ với thông báo dành cho người dùng](#exception-diagnostics-and-logging-009)
- [Dùng debugger với exception breakpoint cho lỗi đã bắt hoặc chưa bắt](#exception-diagnostics-and-logging-010)
- [Đối chiếu bytecode, phiên bản thư viện và mã nguồn khi trace không khớp](#exception-diagnostics-and-logging-011)

## <a id="exception-diagnostics-and-logging-001">Đọc stack trace từ loại lỗi và điểm phát sinh</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc stack trace từ loại lỗi và điểm phát sinh trong Java

## 1. Stack trace trả lời những câu hỏi nào?

Khi một ứng dụng Java gặp exception, thông báo “xảy ra lỗi” thường chưa đủ để sửa được nguyên nhân. Stack trace (dấu vết ngăn xếp lời gọi) cho biết **đối tượng lỗi nào được báo**, **những phương thức nào xuất hiện trong dấu vết của lỗi**, **vị trí mã nguồn tương ứng** và, nếu có, **chuỗi nguyên nhân (cause)** hoặc **lỗi phụ (suppressed exceptions)**.

Trong phạm vi chẩn đoán Java Core, đọc stack trace là quá trình nối bốn loại thông tin:

1. **Loại exception**: NullPointerException, IllegalArgumentException, IOException, SQLException, CompletionException, ...
2. **Thông báo lỗi (message)**: đầu vào, điều kiện bất biến hoặc thao tác cụ thể bị thất bại, nếu exception có message.
3. **Các frame**: class, method, file, số dòng và thứ tự lời gọi ghi nhận trong stack trace.
4. **Cây lỗi**: exception ngoài cùng, các nguyên nhân **Caused by**, và những lỗi cleanup **Suppressed**.

Một lỗi có thể được phát hiện ở thư viện JDK nhưng được kích hoạt bởi dữ liệu sai hoặc logic sai trong mã ứng dụng. Vì vậy, không nên chỉ đọc dòng đầu tiên rồi kết luận “JDK bị lỗi”. Ngược lại, cũng không nên chỉ tìm một dòng có tên package dự án rồi bỏ qua thông tin ở các frame thư viện.

**Mục tiêu:** Từ stack trace, xác định *điểm có bằng chứng lỗi xuất hiện*, *đường gọi dẫn đến điểm đó*, và *nguyên nhân gốc phù hợp nhất để điều tra*. Đây là cách ưu tiên giả thuyết sửa lỗi, **không phải bằng chứng tuyệt đối rằng một dòng mã duy nhất là lỗi thiết kế**.

## 2. Cấu trúc chung của một Java stack trace

Một đoạn stack trace điển hình có hình thức:

~~~text
java.lang.IllegalArgumentException: invalid input
    at com.example.app.ProfileService.parse(ProfileService.java:42)
    at com.example.app.ProfileService.load(ProfileService.java:28)
    at com.example.app.Main.main(Main.java:11)
Caused by: java.lang.NumberFormatException: For input string: "abc"
    at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:...)
    at java.base/java.lang.Integer.parseInt(Integer.java:...)
    at com.example.app.ProfileService.parse(ProfileService.java:40)
    ... 2 more
~~~

Đây là **stack trace minh họa**, không phải đầu ra chính xác của chương trình ở mục 6. Tên package, số dòng, prefix module và số frame được rút gọn để làm rõ cấu trúc; dấu **...** trong ví dụ không mang nghĩa số dòng cố định.

Có thể chia thành các phần:

| Phần nhìn thấy | Ý nghĩa chính |
| --- | --- |
| **java.lang.IllegalArgumentException: invalid input** | Kiểu exception đang được in và message của nó |
| **at com.example...parse(ProfileService.java:42)** | Một frame: class/method và thông tin vị trí nguồn |
| Các dòng **at** tiếp theo | Những lời gọi ở cấp trên trong stack được chụp khi Throwable thu nhận trace |
| **Caused by: ...** | Throwable khác được khai báo là nguyên nhân của Throwable đang in |
| **Suppressed: ...** | Throwable phụ được lưu trên một Throwable, thường từ cleanup |
| **... N more** | Một số frame cuối trùng với trace bao ngoài được lược in, không có nghĩa phần đó bị mất trong đối tượng Throwable |

Một stack trace chuẩn của **Throwable.printStackTrace()** có thể hiển thị thêm các lớp lồng, phần bị lược, hoặc cấu trúc riêng của logger. Dù định dạng và mức chi tiết thay đổi theo môi trường, ý tưởng đọc **loại lỗi → frame → cause → suppressed** vẫn hữu ích.

## 3. Bắt đầu bằng loại exception và thông điệp

Dòng đầu tiên thường có dạng:

~~~text
package.ExceptionType: message
~~~

Ví dụ:

~~~text
java.lang.NumberFormatException: For input string: "abc"
java.lang.NullPointerException: Cannot invoke ... because ... is null
java.io.FileNotFoundException: config.txt (The system cannot find the file specified)
java.lang.IllegalStateException: order is already closed
~~~

Đây là **những dạng thông báo minh họa**; chính tả, nội dung chi tiết và mức giải thích của JVM/JDK có thể khác nhau.

**Loại exception** cho biết cơ chế nhận diện lỗi:

- **NumberFormatException**: một chuỗi được chuyển thành giá trị số theo cú pháp không hợp lệ.
- **NullPointerException**: mã cố thao tác trên reference null trong một tình huống mà Java không cho phép.
- **IndexOutOfBoundsException**: truy cập vị trí không hợp lệ trong cấu trúc có chỉ số.
- **ClassCastException**: phép ép kiểu reference không tương thích.
- **IllegalArgumentException**: API từ chối một đối số vì vi phạm hợp đồng.
- **IllegalStateException**: thao tác không được phép với trạng thái hiện thời của đối tượng.
- **IOException**: lỗi ở tầng thao tác nhập/xuất.
- **CompletionException** hoặc **ExecutionException**: có thể là wrapper cho một lỗi nằm ở **cause**.

**Message** bổ sung ngữ cảnh, không phải danh tính của lỗi. Message có thể bằng **null**, có thể do lập trình viên viết, và có thể khác giữa các phiên bản JDK. Không nên xây logic nghiệp vụ dựa trên so sánh nguyên văn message của JDK.

Ví dụ nếu nhận **NumberFormatException: For input string: "abc"**, việc cần điều tra không chỉ là nơi Java ném NumberFormatException, mà còn là nơi ứng dụng cung cấp chuỗi **"abc"**, cách kiểm tra dữ liệu và quy tắc cho phép những định dạng nào.

## 4. Đọc từng stack frame để tìm đường gọi

Một frame thường được hiển thị như:

~~~text
at package.ClassName.methodName(FileName.java:lineNumber)
~~~

Các thành phần:

- **package.ClassName**: class chứa phương thức theo thông tin stack.
- **methodName**: phương thức đang nằm trên call stack khi trace được ghi nhận.
- **FileName.java**: tên file nguồn nếu metadata tương ứng khả dụng.
- **lineNumber**: dòng nguồn nếu JDK và class file có thông tin dòng phù hợp.

Thông thường, **frame đầu tiên** của một Throwable phản ánh vị trí gần nhất với nơi trace của Throwable được chụp (thường lúc nó được tạo ra). Các frame phía dưới phản ánh những caller tại thời điểm đó. Có thể đọc theo chiều từ **trên xuống dưới** để biết đường lỗi được ghi nhận:

~~~text
at app.Parser.parse(Parser.java:18)        <- nơi lỗi được ghi nhận gần nhất trong ứng dụng
at app.Repository.load(Repository.java:35) <- hàm gọi Parser.parse
at app.Service.handle(Service.java:64)    <- hàm gọi Repository.load
at app.Main.main(Main.java:10)            <- entry point
~~~

Đây là **chuỗi minh họa**. Thứ tự frame là thông tin rất quan trọng: một frame nằm phía dưới thường gọi vào frame phía trên, không phải ngược lại.

**Cẩn thận:** Trong một stack trace có wrapper, frame đầu của **wrapper** và frame đầu của **cause** có thể là hai vị trí khác nhau. Frame đầu của wrapper chỉ ra nơi mã tạo/ghi nhận wrapper; nó chưa chắc là nơi lỗi nghiệp vụ ban đầu xảy ra. Vì vậy phải xem cả **Caused by** trước khi chốt vị trí điều tra.

Ngoài ra, stack trace cho thấy đường thực thi đã được thu thập, nhưng không bảo đảm mọi bước của ứng dụng đều nằm trong một trace duy nhất. Bất đồng bộ, callback, remote calls và thread boundaries có thể tách một luồng nghiệp vụ thành nhiều trace khác nhau.

## 5. “Điểm phát sinh” không phải lúc nào cũng là dòng đầu tiên

Khi Java tự tạo exception trong thư viện, các frame đầu có thể thuộc **java.base**, **java.util** hoặc một framework. Ví dụ với **Integer.parseInt("abc")**, những frame trên cùng có thể là phương thức nội bộ của NumberFormatException/Integer, trong khi frame đầu tiên thuộc ứng dụng là nơi gọi parseInt.

Điều tra đúng thường gồm hai câu hỏi tách biệt:

1. **Throwable này ghi nhận lỗi ở đâu?** Xem những frame đầu của chính Throwable.
2. **Ứng dụng đã đưa điều kiện nào dẫn đến lỗi ấy?** Xem frame ứng dụng có liên quan và dữ liệu truyền vào.

Ví dụ một lỗi **IndexOutOfBoundsException** ném bởi **ArrayList.get** không có nghĩa bản thân ArrayList hỏng. Caller có thể đã tính chỉ số sai. Tương tự, **NullPointerException** ở một thao tác JDK có thể được kích hoạt bởi input null do ứng dụng truyền vào.

Một frame đầu tiên thuộc package dự án cũng không tự động là bug. Có thể nó chỉ là nơi **throw new IllegalArgumentException(...)** để báo một input từ client không hợp lệ theo thiết kế. Cần đối chiếu hợp đồng API, request, dữ liệu thực tế, thao tác trước đó và log cùng correlation ID.

**Quy tắc thực hành:** Đọc nguyên nhân sâu hơn trước, tìm frame ứng dụng liên quan đến nguyên nhân đó, rồi đi ngược đường gọi để tìm nguồn dữ liệu hoặc quyết định nghiệp vụ gây ra tình trạng lỗi.

## 6. Ví dụ Java 17+: loại lỗi, vị trí ứng dụng, cause và suppressed

Ví dụ hoàn chỉnh sau tạo một đường gọi:

**main → service → repository → parseAge → Integer.parseInt**.

Chuỗi đầu vào không thể chuyển thành int. Một tài nguyên trong **try-with-resources** cũng ném IOException khi đóng, nên Java giữ lỗi parse là primary và gắn lỗi close thành suppressed. **service** chủ động bọc lỗi parse bằng một IllegalArgumentException mang thông điệp nghiệp vụ.

Ví dụ tự duyệt **StackTraceElement** để in các trường quan trọng theo cách ổn định, tránh phụ thuộc số dòng nội bộ của thư viện giữa các bản JDK.

Lưu thành file **StackTraceReadingDemo.java**:

~~~java
import java.io.IOException;

public class StackTraceReadingDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static int parseAge(String value) {
        return Integer.parseInt(value);
    }

    static int repository(String value) {
        return parseAge(value);
    }

    static void service(String value) throws IOException {
        try (FailingResource resource = new FailingResource()) {
            repository(value);
        } catch (NumberFormatException original) {
            throw new IllegalArgumentException("invalid age input", original);
        }
    }

    static StackTraceElement firstApplicationFrame(Throwable error) {
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().equals(StackTraceReadingDemo.class.getName())) {
                return frame;
            }
        }
        throw new IllegalStateException("No application frame");
    }

    static String applicationChain(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().equals(StackTraceReadingDemo.class.getName())) {
                if (result.length() != 0) {
                    result.append(" -> ");
                }
                result.append(frame.getMethodName());
            }
        }
        return result.toString();
    }

    public static void main(String[] args) throws IOException {
        try {
            service("not-a-number");
        } catch (IllegalArgumentException wrapper) {
            Throwable cause = wrapper.getCause();
            StackTraceElement origin = firstApplicationFrame(cause);
            System.out.println("outer type=" + wrapper.getClass().getSimpleName());
            System.out.println("outer message=" + wrapper.getMessage());
            System.out.println("outer application frame="
                    + firstApplicationFrame(wrapper).getMethodName());
            System.out.println("cause type=" + cause.getClass().getSimpleName());
            System.out.println("cause first application frame=" + origin.getMethodName());
            System.out.println("cause application chain=" + applicationChain(cause));
            System.out.println("cause file=" + origin.getFileName());
            System.out.println("cause line available=" + (origin.getLineNumber() > 0));
            System.out.println("cause suppressed count=" + cause.getSuppressed().length);
            System.out.println("suppressed type="
                    + cause.getSuppressed()[0].getClass().getSimpleName());
            System.out.println("suppressed message=" + cause.getSuppressed()[0].getMessage());
        }
    }
}
~~~

Biên dịch bằng JDK 17+ và chạy:

~~~bash
javac --release 17 StackTraceReadingDemo.java
java StackTraceReadingDemo
~~~

Stdout đã được kiểm chứng bằng biên dịch và chạy:

~~~text
outer type=IllegalArgumentException
outer message=invalid age input
outer application frame=service
cause type=NumberFormatException
cause first application frame=parseAge
cause application chain=parseAge -> repository -> service -> main
cause file=StackTraceReadingDemo.java
cause line available=true
cause suppressed count=1
suppressed type=IOException
suppressed message=close failed
~~~

Chương trình này biên dịch với cấu hình mặc định của **javac --release 17**, nên metadata dòng nguồn có sẵn. Nếu build dùng cấu hình bỏ debug line information, số dòng có thể không khả dụng; kết quả **cause line available** khi đó có thể khác. Ví dụ không in số dòng cụ thể vì số dòng phụ thuộc bố cục file nguồn.

### Đọc output theo từng lớp

- **outer type = IllegalArgumentException:** exception ứng dụng công bố ra bên ngoài được tạo tại **service**.
- **outer message = invalid age input:** service cung cấp ngữ cảnh nghiệp vụ, không thay thế kiểu lỗi thấp.
- **outer application frame = service:** stack trace của wrapper được ghi nhận khi tạo wrapper trong service.
- **cause type = NumberFormatException:** lỗi parse thực sự vẫn tồn tại và truy cập qua **getCause()**.
- **cause first application frame = parseAge:** dù một số frame nội bộ JDK đứng trên, **parseAge** là frame đầu tiên thuộc class ứng dụng ở cause.
- **cause application chain = parseAge → repository → service → main:** các phương thức gọi nhau tại thời điểm NumberFormatException được tạo.
- **cause suppressed count = 1:** close của FailingResource ném IOException trong lúc thân try đã thất bại. Try-with-resources giữ nó như một lỗi phụ **trên NumberFormatException**, không phải tự động chuyển nó sang danh sách suppressed của IllegalArgumentException wrapper.
- **suppressed message = close failed:** có thêm thông tin cleanup thất bại; chỉ đọc outer message sẽ bỏ lỡ chi tiết quan trọng này.

Trong ví dụ, **service() throws IOException** vì FailingResource.close() có khai báo checked IOException và một đường thực thi khác có thể khiến lỗi đóng là primary. Việc parse lỗi ở lần chạy này làm NumberFormatException trở thành primary và lỗi đóng thành suppressed, nên catch(NumberFormatException) xử lý và wrap nó. Không nên xóa khai báo IOException một cách tùy ý chỉ vì đường chạy thử không ném nó trực tiếp.

## 7. Đọc Caused by để không sửa nhầm wrapper

Khi một tầng thêm ngữ cảnh bằng exception mới:

~~~java
// Mã minh họa bên trong một catch hợp lệ.
catch (java.io.IOException original) {
    throw new IllegalStateException("Cannot initialize configuration", original);
}
~~~

Throwable mới là một **wrapper**, còn **original** được đặt trong **cause**. Trong log, lỗi wrapper thường xuất hiện ở dòng đầu tiên và có stack frames trỏ tới nơi thực hiện **throw new**. Dưới phần **Caused by** là Throwable thấp hơn và các frame tương ứng.

Ví dụ khái niệm:

~~~text
java.lang.IllegalStateException: Cannot initialize configuration
    at app.ConfigurationService.start(ConfigurationService.java:58)
Caused by: java.io.FileNotFoundException: config.properties
    at app.ConfigReader.read(ConfigReader.java:21)
    at app.ConfigurationService.start(ConfigurationService.java:54)
    ... 1 more
~~~

Đây là ví dụ mô phỏng, không phải stdout từ code phần 6.

Cách đọc hợp lý:

1. Ghi nhận **mục đích mà wrapper muốn diễn đạt**: không thể khởi tạo cấu hình.
2. Theo **Caused by** đến FileNotFoundException.
3. Tìm frame ứng dụng trong cause: ConfigReader.read.
4. Kiểm tra path cấu hình, thư mục làm việc, quyền truy cập, cách lấy đường dẫn và cấu hình môi trường.
5. Từ **ConfigurationService.start** xác định ai yêu cầu load cấu hình và ứng dụng nên phản ứng thế nào.

Không phải lúc nào cause sâu nhất cũng là nguyên nhân nghiệp vụ đủ để sửa lỗi. Một **ConnectException** sâu nhất cho biết không kết nối được, nhưng vấn đề thực sự có thể là endpoint sai, DNS, credential hoặc dịch vụ chưa sẵn sàng. Stack trace là bằng chứng về *đường lỗi*, không thay thế quan sát hệ thống bên ngoài.

### getCause() và các tầng lồng

**Throwable.getCause()** có thể trả về null. Nhiều framework có thể wrap lỗi qua nhiều tầng; phải duyệt có kiểm soát:

~~~java
// Mã minh họa trong một phương thức nhận Throwable.
Throwable current = failure;
while (current.getCause() != null) {
    current = current.getCause();
}
// current là cause sâu nhất theo chuỗi getCause() này.
~~~

Không viết cố định **failure.getCause().getCause()** khi chưa kiểm tra null. Hơn nữa, chọn duy nhất cause cuối có thể làm mất thông tin ngữ cảnh của các wrapper phía trên. Trong báo cáo chẩn đoán, tốt hơn giữ cả **cây lỗi** rồi tập trung vào tầng phù hợp cho hành động sửa.

## 8. Đọc Suppressed để nhận ra lỗi cleanup không che lỗi gốc

**Suppressed exception** là Throwable phụ gắn vào một Throwable khác bằng **addSuppressed()**. Try-with-resources sử dụng cơ chế này khi thân try đang ném exception và lệnh close của tài nguyên cũng ném lỗi.

Ví dụ dạng log:

~~~text
java.lang.NumberFormatException: For input string: "not-a-number"
    at java.base/java.lang.Integer.parseInt(Integer.java:...)
    at app.StackTraceReadingDemo.parseAge(StackTraceReadingDemo.java:...)
    Suppressed: java.io.IOException: close failed
        at app.StackTraceReadingDemo$FailingResource.close(StackTraceReadingDemo.java:...)
        ... 3 more
~~~

Đây là cấu trúc minh họa, có rút gọn số dòng và frame. **Suppressed** không có nghĩa lỗi close gây ra NumberFormatException. Quan hệ là: khi exception chính đã xảy ra, một lỗi thứ hai cũng xuất hiện lúc dọn dẹp. Nếu đảo quan hệ nhân quả, ta có thể sửa nhầm close thay vì dữ liệu parse.

Với chương trình ở mục 6, wrapper IllegalArgumentException có **cause là NumberFormatException**; chính NumberFormatException mới mang **suppressed IOException**. Do đó, khi log chỉ hiển thị exception wrapper hoặc khi helper chỉ hỏi **wrapper.getSuppressed()**, lỗi close có thể không xuất hiện. Cần duyệt cause và suppressed đúng vị trí.

Phân biệt nhanh:

| Quan hệ | API | Câu hỏi cần hỏi |
| --- | --- | --- |
| **Cause** | **getCause()** | Lỗi nào dẫn đến exception này theo cách mã đã wrap? |
| **Suppressed** | **getSuppressed()** | Khi xử lý/đóng tài nguyên có lỗi phụ nào được giữ lại? |
| **Stack frame** | **getStackTrace()** | Tại thời điểm trace được ghi nhận, những phương thức nào đang trên call stack? |

Nếu **close()** thất bại khi thân try **không lỗi**, lỗi close có thể là exception chính được ném ra. Không mặc định rằng mọi IOException từ close chỉ là lỗi phụ.

## 9. Ý nghĩa của dòng ... N more

Khi Throwable.printStackTrace in một exception có cause hoặc suppressed, nó có thể rút gọn những frame cuối **giống nhau** giữa trace hiện tại và trace của exception bao quanh. Khi thấy:

~~~text
Caused by: java.io.IOException: read failed
    at app.Reader.read(Reader.java:17)
    ... 3 more
~~~

Điều đó không có nghĩa Java mất ba frame vì exception bị “cắt ngắn” ngẫu nhiên. **... 3 more** thường biểu thị ba frame cuối trùng với các frame của trace đang bao ngoài và không cần in lại.

Khi đọc:

1. Xem frame riêng của cause nằm ngay trên **... N more**.
2. Đối chiếu những frame tương ứng ở trace bên ngoài để khôi phục đường caller chung.
3. Tập trung frame riêng có giá trị chẩn đoán: thường là điểm exception sâu hơn phát sinh.
4. Đừng nhầm số **N** với số exception, số retry, số lần lỗi xuất hiện hoặc số dòng mã bị bỏ qua.

Nếu sử dụng **getStackTrace()** trên object Throwable trong chương trình, bạn nhận một mảng **StackTraceElement** của chính Throwable; dấu **... N more** là **cách in rút gọn**, không phải phần tử đặc biệt nằm trong mảng.

## 10. Wrapper, throw e, throw new và thời điểm thu thập trace

Một vấn đề dễ hiểu nhầm là stack trace không nhất thiết phản ánh nơi một Throwable **được ném gần đây nhất**. **Throwable** thường thu nhận stack trace khi nó được khởi tạo (hoặc khi **fillInStackTrace()** được gọi theo thiết kế của class).

Ví dụ:

~~~java
// Mã minh họa, bên trong một phương thức có khai báo phù hợp.
try {
    riskyOperation();
} catch (IllegalArgumentException original) {
    throw original; // Giữ cùng đối tượng Throwable.
}
~~~

Thông thường **throw original** không tạo một đối tượng lỗi mới và không tự thay toàn bộ stack trace bằng địa điểm rethrow. Khi điều tra, điều này giúp giữ lại nơi exception ban đầu được ghi nhận.

Ngược lại:

~~~java
// Mã minh họa.
try {
    riskyOperation();
} catch (IllegalArgumentException original) {
    throw new IllegalStateException("Processing failed", original);
}
~~~

**new IllegalStateException(...)** tạo wrapper với stack trace riêng tại chỗ tạo; lỗi cũ tồn tại qua **getCause()**. Nếu chỉ xem phần trace ngoài cùng, bạn có thể thấy dòng tại chỗ wrap mà không thấy điểm lỗi thấp hơn.

Mã như sau làm mất dấu gốc:

~~~java
// Phản mẫu minh họa.
catch (IllegalArgumentException original) {
    throw new IllegalStateException(original.getMessage());
}
~~~

Lỗi mới không giữ original làm cause. Dù message giống nhau, stack trace gốc và các suppressed exceptions nằm trên original đã bị tách khỏi wrapper; hệ thống logging chỉ nhận wrapper khó truy vết nguyên nhân.

Cũng cần tránh **tái sử dụng một instance exception** cho các lần thất bại ở thời điểm khác nhau: trace của instance cũ có thể không phản ánh lần gọi mới. Các API như **setStackTrace()**, **fillInStackTrace()**, **Throwable** tùy biến vô hiệu hóa writable stack trace, hoặc một số tối ưu JVM đều có thể làm dấu vết khác cấu trúc thông thường.

**Kết luận:** Frame đầu ghi nhận nơi trace của **Throwable cụ thể** được xây dựng/thu thập, không phải chứng cứ phổ quát rằng mọi lần throw của cùng object đều diễn ra đúng tại dòng đó.

## 11. Các frame thư viện và frame ứng dụng

Một stack trace có thể chứa những frame thuộc nhiều lớp hệ thống:

- **java.base/java.lang...**, **java.base/java.util...**: phương thức của JDK.
- **org.springframework...**, **org.hibernate...**, **com.fasterxml...** hoặc package framework khác: những tầng framework/library.
- **com.example...**: mã ứng dụng của bạn.
- Những tên chứa **lambda$...**, **$$Lambda**, proxy hoặc reflection: các cách JVM/framework triển khai callback và gọi động.

Đọc stack trace không nên chỉ lọc mọi frame thư viện rồi bỏ đi. Ví dụ:

- **Integer.parseInt** giúp biết lỗi là chuyển chuỗi sang số.
- **Objects.requireNonNull** giúp biết có validation không-null.
- **ArrayList.get** giúp biết thao tác truy cập theo chỉ số.
- **CompletableFuture** hoặc **FutureTask** giúp biết lỗi đi qua boundary bất đồng bộ.
- **Method.invoke** giúp nhận biết lời gọi reflection.

Sau đó tìm frame ứng dụng **gần vùng lỗi nhất và phù hợp nhất**, chứ không mặc định frame ứng dụng đầu tiên là bug. Nếu lỗi do framework gọi ngược vào mã của bạn qua callback, frame ứng dụng quan trọng có thể nằm ở nhiều vị trí.

### Những cách hiển thị vị trí khác

Có thể gặp:

~~~text
at java.base/java.util.ArrayList.get(ArrayList.java:427)
at app.NativeBridge.nativeMethod(Native Method)
at app.GeneratedProxy.call(Unknown Source)
at app.Main.lambda$main$0(Main.java:23)
~~~

Đây là minh họa định dạng, không khẳng định số dòng cố định của phiên bản JDK. **Native Method** và **Unknown Source** cho biết không có vị trí Java source line tương ứng trong frame theo cách hiển thị; **lambda$...** có thể là phương thức tổng hợp cho lambda, tên có thể thay đổi theo compiler. Phần prefix module, ví dụ **java.base/**, không phải một phần tên class trả về từ **StackTraceElement.getClassName()**.

Nếu class được biên dịch không có **LineNumberTable** hoặc mã đang chạy khác bản nguồn mở trong IDE, số dòng có thể không hữu ích. Đừng sửa đúng dòng trong bản nguồn cũ rồi khẳng định đã giải quyết: trước hết xác minh artifact, commit và môi trường chạy tương ứng.

## 12. Java 17: đọc StackTraceElement bằng mã

Một Throwable có thể cung cấp:

~~~java
// Mã minh họa trong phương thức nhận Throwable.
StackTraceElement[] frames = failure.getStackTrace();
for (StackTraceElement frame : frames) {
    String className = frame.getClassName();
    String method = frame.getMethodName();
    String fileName = frame.getFileName();
    int line = frame.getLineNumber();
    // Dùng các giá trị để phân tích hoặc hiển thị.
}
~~~

Các API hữu ích:

| API | Ý nghĩa và giới hạn |
| --- | --- |
| **getClassName()** | Tên nhị phân của class theo frame, không nhất thiết là một file Java mà bạn có thể sửa |
| **getMethodName()** | Tên phương thức hoặc tên synthetic do compiler tạo |
| **getFileName()** | Tên file nguồn nếu biết; có thể null |
| **getLineNumber()** | Số dòng nếu có; giá trị đặc biệt biểu thị không biết hoặc native |
| **isNativeMethod()** | Frame đại diện một phương thức native |
| **toString()** | Biểu diễn frame gần với cú pháp dòng **at ...** |

Đừng giả định **getStackTrace()[0]** luôn tồn tại: Throwable có thể có trace rỗng, đặc biệt khi writable stack trace bị vô hiệu hóa hoặc đối tượng do người viết class cấu hình. Với mã chẩn đoán dùng trong production, hãy kiểm tra độ dài trước khi lấy index.

Ví dụ phần 6 dùng một helper **firstApplicationFrame** lọc theo class của chính ví dụ. Nó **cố tình ném IllegalStateException** nếu không có frame phù hợp để bắt lỗi giả định trong demo. Một tool chẩn đoán tổng quát nên trả Optional hoặc tín hiệu “không tìm thấy” thay vì cho rằng mọi trace đều có frame thuộc package ứng dụng.

Java 9+ còn cung cấp **StackWalker** để duyệt stack của thread đang thực thi. Tuy nhiên, StackWalker chụp **stack hiện tại khi gọi**, còn **Throwable.getStackTrace()** đọc dấu vết đã được lưu trên một Throwable. Chúng không tự thay thế nhau: muốn tìm nơi một exception cũ phát sinh, thường cần trace của exception đó, không phải stack của logger đang chạy.

## 13. Phân biệt trace của main thread với lỗi trong task bất đồng bộ

Với **ExecutorService**, **Future** hoặc **CompletableFuture**, một tác vụ có thể thất bại ở thread worker nhưng kết quả lỗi được đọc ở thread caller. Khi đó dễ xuất hiện hai tầng dấu vết:

1. **Cause của task**: frame từ worker, nơi exception được tạo/phát sinh.
2. **Wrapper khi nhận kết quả**: frame từ caller đang gọi **get()** hoặc **join()**, hoặc frame của cơ chế completion.

Ví dụ khái niệm:

~~~text
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: worker failed
    at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:...)
    at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:...)
    at app.Main.main(Main.java:...)
Caused by: java.lang.IllegalStateException: worker failed
    at app.Worker.compute(Worker.java:...)
    at app.Worker.call(Worker.java:...)
    ... more ...
~~~

Đây là cấu trúc minh họa, không phải stdout chính xác. Phần đầu mô tả nơi **caller thu nhận kết quả lỗi**, còn **Caused by** mô tả nơi **worker tạo lỗi**.

Với **CompletableFuture**, **get()** thường ném checked **ExecutionException** khi Future hoàn tất bất thường vì lỗi tác vụ; **join()** thường ném unchecked **CompletionException**. Hai wrapper này có thể giữ cùng lỗi gốc trong cause. Với Future bị hủy, **CancellationException** có thể ném trực tiếp. **get(timeout)** có thể ném **TimeoutException** vì lần chờ hết hạn, chứ không nhất thiết vì worker tạo một exception.

Khi điều tra, phải phân biệt:

- **InterruptedException ở caller.get()**: chính caller có thể bị interrupt khi chờ.
- **InterruptedException từ Callable worker**: có thể là cause bên trong ExecutionException.
- **TimeoutException trực tiếp từ get(timeout)**: caller chưa thấy kết quả trong thời hạn.
- **TimeoutException là cause sau orTimeout của CompletableFuture**: Future đã được hoàn tất bất thường bởi chính sách timeout.

Nếu chỉ nhìn wrapper của caller rồi tìm bug tại dòng **future.get()**, rất có thể bạn đang bỏ qua lỗi thực sự bên trong task hoặc một chính sách timeout ở tầng khác.

## 14. Đọc trace với try-with-resources, finally và cleanup

Tài nguyên được giải phóng khi rời khối, kể cả khi thân try đã gặp exception. Nhưng **close()** cũng có thể thất bại.

Với **try-with-resources**:

~~~java
// Minh họa với resource có AutoCloseable.close().
try (var resource = openResource()) {
    performOperation(resource);
}
~~~

Nếu **performOperation** ném lỗi A và **resource.close()** ném lỗi B, A được giữ là primary còn B được thêm vào **A.getSuppressed()**. Vì vậy khi đọc log, hãy tìm cả **Suppressed** để biết cleanup có lỗi mà không nhầm B thành nguyên nhân gây A.

Nếu dùng **finally** để cleanup rồi close ném exception mới không được xử lý, B có thể che khuất A. Đây là lỗi lập trình làm mất stack trace của failure ban đầu. Không chỉ so hai thông điệp rồi kết luận “close là lỗi duy nhất”.

Một trường hợp khác: thân try thành công nhưng close thất bại. Lúc ấy không có lỗi A từ thân; B có thể là exception chính được truyền ra. Bởi vậy **suppressed** không đồng nghĩa với **mọi exception phát sinh trong close**.

Nếu lỗi cleanup nằm sâu trong cause của một wrapper như ví dụ chính, tìm **Suppressed** ở chính cause phù hợp. Chỉ in **wrapper.getSuppressed()** có thể trả mảng rỗng dù **wrapper.getCause().getSuppressed()** chứa lỗi rất quan trọng.

## 15. Những nguồn gây hiểu nhầm khi so log với mã nguồn

Một stack trace được lấy từ hệ thống đang chạy phải được đặt cạnh **đúng phiên bản mã nguồn**:

- Đã deploy đúng JAR, classpath, container image hay chưa?
- File **.class** có được biên dịch từ commit đúng không?
- Số dòng có thay đổi vì mã nguồn đã chỉnh nhưng chương trình chưa build/deploy lại không?
- Một exception có bị wrap tại boundary của framework nên frame đầu nhìn khác kỳ vọng không?
- Có lambda, proxy, reflection hoặc generated code khiến tên class/method khó đối chiếu không?
- Nếu xảy ra ở task bất đồng bộ, có đang xem stack trace của thread consumer thay vì lỗi ở producer không?
- Log aggregator có lược bỏ phần **Caused by**, **Suppressed** hoặc giới hạn độ dài stack trace không?

Một log có thông báo **NullPointerException** nhưng không có frame vì cấu hình logger chỉ ghi message không phải bằng chứng JVM không có stack trace. Cần kiểm tra cách Throwable được truyền vào API logging.

Tương tự, một trace chứa **Unknown Source** không có nghĩa lỗi “không thể debug”. Hãy dùng class/method, các frame lân cận, bytecode/artifact phù hợp và các dữ kiện vận hành để khoanh vùng; đồng thời bật metadata line number cho các build cần hỗ trợ chẩn đoán.

## 16. Ghi log sao cho còn thông tin traceback

Java cung cấp **Throwable.printStackTrace()**, phù hợp cho ví dụ hoặc chẩn đoán đơn giản:

~~~java
// Mã minh họa trong catch hợp lệ.
catch (Exception failure) {
    failure.printStackTrace();
}
~~~

Trong ứng dụng production, thường nên dùng logging framework hoặc **java.util.logging** theo chính sách hệ thống. Ví dụ với **java.util.logging.Logger** của JDK:

~~~java
import java.util.logging.Level;
import java.util.logging.Logger;

// Minh họa trong một class có Logger logger và Throwable failure.
logger.log(Level.SEVERE, "Unable to process request", failure);
~~~

Đây là snippet minh họa theo ngữ cảnh, không phải class độc lập; **logger** phải được khởi tạo và **failure** phải tồn tại.

Đối với logger có chữ ký dạng **error(String, Throwable)**:

~~~java
// Minh họa API logger tương ứng, không phải Java SE độc lập.
logger.error("Unable to process request", failure);
~~~

Truyền **Throwable** giúp formatter có cơ hội hiển thị stack trace, cause và suppressed. Ngược lại:

~~~java
// Phản mẫu: thường chỉ ghi thông báo.
logger.error("Unable to process request: " + failure.getMessage());
~~~

có thể đánh mất dấu vết. Cần kiểm tra chữ ký logger và pipeline log cụ thể; đừng giả định mọi hệ logging đều in đầy đủ cây exception chỉ vì đã truyền đối tượng.

### Ghi log một lần ở ranh giới phù hợp

Một lỗi đi qua nhiều tầng service/controller có thể bị log ở mọi catch rồi ném lại. Kết quả là nhiều stack trace giống nhau trong cùng request, dễ bị đếm nhầm là nhiều sự cố. Mẫu thường tốt hơn:

- Tầng thấp **throw/wrap với cause phù hợp** và giữ context cần thiết.
- Tầng sở hữu ranh giới (controller/job scheduler/message consumer) ghi log đầy đủ một lần ở mức phù hợp.
- Dùng request ID/trace ID hoặc correlation ID để nối sự kiện qua thread và dịch vụ.
- Chỉ log lại giữa các tầng nếu có thông tin độc lập thực sự hữu ích.

Khi tạo log ở hệ thống thật, tránh ghi password, token, dữ liệu cá nhân, dữ liệu thẻ hoặc raw request chứa thông tin bí mật vào exception message. Stack trace chẩn đoán nên đầy đủ nhưng vẫn phải tuân thủ chính sách bảo mật và lưu giữ dữ liệu.

## 17. Quy trình đọc một stack trace để tìm bug

Một quy trình thực hành hiệu quả:

### Bước 1: Lấy đủ cây Throwable

Không chỉ chụp dòng exception đầu tiên. Thu thập phần **at**, **Caused by**, **Suppressed**, thông điệp trước/sau của cùng request và metadata về phiên bản ứng dụng. Nếu log bị cắt, tìm bản đầy đủ trước khi suy luận.

### Bước 2: Nhận diện wrapper hay lỗi trực tiếp

Nếu dòng đầu là **CompletionException**, **ExecutionException**, **InvocationTargetException** hoặc exception do framework dùng để thêm ngữ cảnh, truy xuống **getCause()**. Đừng nhầm điểm wrap là điểm lỗi nghiệp vụ ban đầu.

### Bước 3: Đọc loại lỗi sâu phù hợp

Xem lỗi thuộc loại input, trạng thái, parse, quyền truy cập, concurrency, I/O hay hệ ngoài. Chỉ dùng loại exception như dấu hiệu định hướng, không thay thế việc xem logic thực tế.

### Bước 4: Xem frame ứng dụng gần nguyên nhân

Tìm frame code của hệ thống xuất hiện trong trace của Throwable liên quan. Mở chính file và dòng nguồn của artifact đang chạy. Xác định biểu thức có thể thất bại ở dòng đó, không chỉ đọc tên phương thức.

### Bước 5: Đi xuống caller để hiểu dữ liệu đến từ đâu

Các frame ở dưới cho biết chuỗi gọi. Hỏi caller nhận input từ đâu, đã validate ở đâu, có state nào thay đổi, có điều kiện race hay dữ liệu trễ không.

### Bước 6: Xem cause/suppressed bổ sung

Nếu có **Caused by**, xác định quan hệ nguyên nhân. Nếu có **Suppressed**, xem cleanup có hỏng và có gây hệ quả trạng thái nào không. Không gộp hai loại thành “cùng một lỗi”.

### Bước 7: Đối chiếu ngữ cảnh vận hành

Kiểm tra timestamp, dependency status, thread name, endpoint, request ID, phiên bản deploy, feature flag, cấu hình, tải hệ thống và thao tác ngay trước khi lỗi xảy ra. Một stack trace thường là điểm bắt đầu tốt nhưng không phải toàn bộ thông tin.

### Bước 8: Tạo test tái hiện

Tạo unit test hoặc integration test với dữ liệu và điều kiện gây lỗi. Kiểm tra không chỉ “throw đúng exception” mà còn nguyên nhân, thông điệp có ích và bảo toàn trạng thái khi thất bại.

### Bước 9: Sửa nguyên nhân, không chỉ ẩn triệu chứng

Không sửa bằng **catch(Exception) { return null; }** hoặc **try/catch** nuốt lỗi chỉ để log biến mất. Nếu lỗi là input sai, sửa validation/handling; nếu race, sửa synchronization/ownership; nếu resource thiếu, sửa cấu hình/vòng đời tài nguyên.

### Bước 10: Xác minh kết quả và quan sát sau deploy

Test cả đường thành công, lỗi tương tự và lỗi cleanup; sau deploy xác nhận log giảm tương ứng, không có biến dạng trạng thái hoặc lỗi mới do thay đổi.

## 18. Ví dụ phân tích các tình huống phổ biến

### NullPointerException

~~~text
java.lang.NullPointerException
    at app.OrderService.calculate(OrderService.java:81)
    at app.OrderController.create(OrderController.java:33)
~~~

Cần xem dòng **calculate:81** dereference biến nào có thể null, dữ liệu được khởi tạo/validate ở đâu, caller **create** đưa đối tượng nào vào. Đừng tự kết luận mọi NullPointerException phải sửa bằng một **if (x != null)**; có thể hợp đồng yêu cầu đối tượng không được null, nên caller cần được sửa hoặc dữ liệu cần validation rõ ràng hơn.

### NumberFormatException

~~~text
java.lang.NumberFormatException: For input string: "x"
    at java.base/java.lang.Integer.parseInt(Integer.java:...)
    at app.ConfigParser.parsePort(ConfigParser.java:19)
    at app.Bootstrap.start(Bootstrap.java:8)
~~~

**Integer.parseInt** là nơi lỗi được tạo trong JDK, nhưng **parsePort** là frame cần xem dữ liệu cấu hình. Kiểm tra port được đọc từ đâu, có khoảng trắng, định dạng không hợp lệ hay giới hạn phạm vi nào.

### IndexOutOfBoundsException

~~~text
java.lang.IndexOutOfBoundsException: Index 4 out of bounds for length 3
    at java.base/java.util.ArrayList.get(ArrayList.java:...)
    at app.ReportService.column(ReportService.java:47)
~~~

Xem cách ReportService tính index so với kích thước dữ liệu, khả năng collection bị sửa đồng thời, ranh giới vòng lặp hoặc dữ liệu thiếu. Không quy trách nhiệm cho ArrayList khi caller đưa index sai.

### IOException có cause sâu

~~~text
java.io.IOException: failed to load feed
    at app.FeedReader.read(FeedReader.java:56)
Caused by: java.net.ConnectException: Connection refused
    at ... network stack ...
~~~

Lớp ngoài nói công việc load feed thất bại. Cause cho biết kết nối bị từ chối. Cần đối chiếu host/port, service đích, network policy, trạng thái dependency. Stack trace không thể tự khẳng định đó là lỗi firewall hay service down nếu chưa có dữ kiện ngoài.

### CompletionException từ bất đồng bộ

~~~text
java.util.concurrent.CompletionException: java.lang.IllegalStateException: worker failed
    at ... completion machinery ...
Caused by: java.lang.IllegalStateException: worker failed
    at app.Worker.process(Worker.java:72)
~~~

Ưu tiên xem **Worker.process:72** và input task. Dòng consumer gọi **join()** không nhất thiết là nguồn lỗi, dù đó là nơi exception được quan sát.

Các đoạn trên đều là ví dụ minh họa định dạng, không phải kết quả chạy chính xác của chương trình mục 6.

## 19. Kiểm thử hành vi exception và chất lượng trace

Khi tạo class và API có thể lỗi, nên xem chất lượng exception như một phần của hợp đồng:

| Tình huống cần test | Điều cần xác minh |
| --- | --- |
| Input không hợp lệ | Loại exception đúng, message đủ ngữ cảnh nhưng không lộ dữ liệu nhạy cảm |
| Lỗi thấp được wrap | Wrapper có cause là lỗi gốc |
| Throwable ném lại | Không vô ý làm mất object gốc, cause hoặc suppressed |
| Try-with-resources có lỗi chính và lỗi close | Lỗi chính còn nguyên; lỗi close nằm trong suppressed |
| Async task thất bại | Consumer nhìn đúng wrapper; cause phản ánh lỗi task |
| Logger nhận Throwable | Test/integration kiểm tra formatter có giữ stack trace/cause/suppressed |
| Nhiều phiên bản artifact | File/dòng trong trace khớp version đang chạy |
| Lỗi được recovery có chủ đích | Không coi fallback là xử lý thành công nếu business contract yêu cầu báo thất bại |

Có thể assert **same instance** cho cause, thay vì chỉ so sánh message. Ví dụ ở phần 6, nếu tách hàm trả về wrapper để test, nên kiểm tra **wrapper.getCause()** vẫn là chính NumberFormatException đã bắt, và **getSuppressed()** trên cause có IOException từ close.

**Không nên cố định số dòng source trong test thông thường**, trừ khi chính contract của công cụ chẩn đoán đòi hỏi mapping dòng. Số dòng thay đổi theo việc chỉnh mã, còn trace có thể khác giữa build modes. Test có thể kiểm tra tên class/method hoặc đường quan hệ cause/suppressed ổn định hơn.

Nếu dùng JUnit, một test đơn giản có thể assertThrows đúng kiểu rồi kiểm tra cause/suppressed. Tuy nhiên, đoạn test thực tế phải phù hợp phiên bản JUnit và setup dự án; không nhầm mã phụ thuộc framework với mã Java SE có thể biên dịch độc lập.

## 20. Lỗi thường gặp khi đọc hoặc ghi stack trace

**Chỉ đọc dòng đầu tiên.** Dễ bỏ qua cause sâu chứa lỗi thực sự, đặc biệt với CompletionException/ExecutionException hoặc wrapper nghiệp vụ.

**Cho rằng frame đầu của wrapper là nguồn lỗi thấp nhất.** Frame đầu có thể là nơi tạo wrapper; cần xem trace của cause.

**Chỉ tìm frame thuộc package dự án rồi coi đó là bug.** Frame ứng dụng có thể báo lỗi theo hợp đồng và nguyên nhân nằm ở cấu hình, input, dữ liệu từ ngoài hoặc dependency.

**Bỏ qua Suppressed.** Có thể không nhận ra close/rollback thất bại và trạng thái hệ thống chưa được làm sạch.

**Nhầm ... N more với N lỗi mới.** Đó là cách rút gọn frame trùng khi in cây exception.

**Bắt lỗi rồi tạo RuntimeException mới chỉ từ getMessage.** Làm đứt chuỗi cause và mất trace gốc, khiến debug khó hơn.

**Chỉ log getMessage().** Log thiếu class/method/source line, cause và suppressed. Nên truyền Throwable cho logger.

**Bật log ở mọi tầng.** Tạo bản sao cùng một sự cố, nhiễu cảnh báo và tốn tài nguyên. Chọn boundary quan sát có trách nhiệm.

**Tin tuyệt đối vào số dòng khi mã và deploy khác phiên bản.** Có thể đọc nhầm source cũ. Xác minh artifact/hash/commit và thông tin debug lines.

**Dùng exception message làm khóa phân nhánh logic.** Message có thể thay đổi theo JDK hoặc ngữ cảnh; ưu tiên type, cause, mã lỗi cấu trúc hoặc hợp đồng API.

**Coi trace ở caller.get()/join() là toàn bộ lịch sử bất đồng bộ.** Worker có thể có trace riêng trong cause; cần đi theo pipeline và correlation ID.

**Để lộ secrets qua log.** Exception và stack trace vẫn là dữ liệu vận hành có thể chứa tên file, URI, input, cấu hình; áp dụng masking và chính sách truy cập/retention.

## 21. Bảng kiểm đọc stack trace trong thực tế

Trước khi kết luận nguyên nhân, hãy trả lời:

1. **Loại exception ngoài cùng** là gì và message có cung cấp ngữ cảnh nào?
2. Có **Caused by** không? Cause sâu nào thực sự liên quan đến thao tác thất bại?
3. Có **Suppressed** không? Cleanup đã gặp lỗi gì?
4. **Frame đầu của Throwable đang phân tích** nằm ở đâu? Nó có phải frame thư viện, wrapper hoặc frame ứng dụng không?
5. Frame ứng dụng nào gần điểm lỗi nhất, và **dòng đó làm gì** với dữ liệu hiện có?
6. Chuỗi frame phía dưới phản ánh những caller nào và dữ liệu đi qua chúng ra sao?
7. Có tên **lambda/proxy/reflection**, module prefix, Native Method hoặc Unknown Source nào cần giải thích không?
8. Lỗi có xảy ra qua **Future/CompletableFuture** khiến wrapper được tạo ở thread consumer không?
9. Stack trace có tương ứng chính xác với **mã đã deploy** và metadata dòng nguồn của build đang chạy không?
10. Log có đủ Throwable tree, context, trace ID, nhưng không làm lộ dữ liệu nhạy cảm không?
11. Đã tạo được test tái hiện và sửa **nguyên nhân** thay vì chỉ nuốt exception chưa?

**Kết luận:** Đọc stack trace hiệu quả bắt đầu bằng **loại exception và message**, sau đó lần theo **stack frame**, phân biệt **điểm ghi nhận exception** với **nơi ứng dụng tạo điều kiện lỗi**, và luôn xem **Caused by** cùng **Suppressed**. Với wrapper hoặc tác vụ bất đồng bộ, lỗi ngoài cùng không nhất thiết là lỗi nghiệp vụ ban đầu. Giữ nguyên chuỗi Throwable khi ghi log và đối chiếu đúng mã nguồn đang chạy là cơ sở để xác định nguyên nhân một cách đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-002">Đọc Caused by, Suppressed và các frame được rút gọn</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc `Caused by`, `Suppressed` và các frame được rút gọn trong Java stack trace

Khi một chương trình Java báo lỗi, stack trace thường chứa nhiều tầng ngoài dòng ngoại lệ đầu tiên: `Caused by`, `Suppressed` và dấu `... N more`. Đọc nhầm các thành phần này dễ dẫn đến sửa sai vị trí, bỏ qua lỗi gốc hoặc hiểu nhầm rằng log đã bị cắt. Bài học thuộc nhóm **Java Core – Exception / Chẩn đoán và ghi log**, sử dụng Java 17+.

Mục tiêu: đọc được cấu trúc của stack trace chuẩn do `Throwable.printStackTrace(...)` tạo ra, lần theo **chuỗi nguyên nhân**, hiểu **ngoại lệ bị suppressed** (đặc biệt trong `try-with-resources`), khôi phục ý nghĩa các **frame chung đã rút gọn**, rồi áp dụng một quy trình chẩn đoán và ghi log hữu ích.

## 1. Nhìn một stack trace như một đồ thị lỗi có ngữ cảnh

Một exception trong Java lưu nhiều thông tin khác nhau:

- **Loại ngoại lệ**: chẳng hạn `java.lang.IllegalStateException`.
- **Thông điệp**: đoạn mô tả sau dấu `:`; có thể là `null`.
- **Stack trace**: chuỗi các `StackTraceElement`, biểu thị ngăn xếp tại thời điểm stack trace được ghi nhận; thường có tên lớp, phương thức, tệp và dòng.
- **Cause**: nguyên nhân được nối bằng `Throwable.getCause()`, thông thường là một ngoại lệ bên dưới được bọc thành ngoại lệ có ngữ cảnh cao hơn.
- **Suppressed exceptions**: tập các ngoại lệ phụ lấy bằng `Throwable.getSuppressed()`. Chúng không thay thế cause và thường xuất hiện khi dọn dẹp tài nguyên lại thất bại.

Một bản in điển hình có cấu trúc sau. Đây là **stack trace minh họa**, tên file/số dòng phục vụ giải thích, không phải output được cam kết từ một chương trình cụ thể:

```text
java.lang.IllegalStateException: Không thể xử lý yêu cầu
    at app.OrderService.placeOrder(OrderService.java:52)
    at app.OrderController.submit(OrderController.java:31)
    Suppressed: java.io.IOException: Lỗi đóng bộ ghi
        at app.AuditWriter.close(AuditWriter.java:18)
        at app.OrderService.placeOrder(OrderService.java:52)
        ... 1 more
Caused by: java.lang.IllegalArgumentException: Mã đơn hàng rỗng
    at app.OrderParser.parse(OrderParser.java:14)
    at app.OrderService.placeOrder(OrderService.java:49)
    ... 1 more
```

Đọc theo các lớp ý nghĩa:

1. Dòng đầu cho biết **ngoại lệ đang được in**: `IllegalStateException` với thông điệp “Không thể xử lý yêu cầu”.
2. Các dòng `at ...` ngay sau đó là **stack frame của ngoại lệ ấy**. Trong ví dụ, luồng xử lý đi qua `OrderController.submit` rồi `OrderService.placeOrder`.
3. Nhánh thụt vào `Suppressed:` ghi một **lỗi phụ** trong quá trình đóng bộ ghi. Lỗi này không nhất thiết giải thích nguyên nhân chính của yêu cầu thất bại.
4. `Caused by:` nối tới **ngoại lệ nguyên nhân**: `IllegalArgumentException` về mã đơn hàng. Đây thường là nơi bắt đầu truy tìm điều kiện đầu vào sai.
5. `... 1 more` cho biết **một frame ở cuối stack của ngoại lệ con trùng với phần cuối stack của ngoại lệ bao ngoài trong cùng nhánh in** và đã được rút gọn; đây không phải “một lỗi nữa”.

Không nên mặc định rằng dòng ngoại lệ đầu tiên là **root cause**. Ngoại lệ ngoài cùng thường biểu diễn thao tác ứng dụng đang thất bại, còn các tầng nguyên nhân cho biết vì sao thao tác không thể hoàn tất.

## 2. Một stack frame chứa gì?

Một frame thường có dạng:

```text
at package.ClassName.methodName(FileName.java:123)
```

Ý nghĩa các phần:

| Thành phần | Vai trò |
|---|---|
| `package.ClassName` | Tên lớp có phương thức nằm trong stack |
| `methodName` | Phương thức của frame; có thể là `lambda$...` hoặc tên được sinh bởi compiler |
| `FileName.java` | Tên tệp nguồn nếu có thông tin debug tương ứng |
| `:123` | Dòng nguồn tương ứng nếu JVM có metadata dòng |
| Thứ tự các dòng `at` | Frame trên cùng gần vị trí ghi nhận stack nhất, các frame dưới là ngữ cảnh gọi phía ngoài |

Ví dụ:

```text
at com.example.repo.UserRepository.find(UserRepository.java:88)
at com.example.service.UserService.load(UserService.java:41)
at com.example.web.UserController.show(UserController.java:27)
```

Hướng đọc từ **trên xuống** là từ nơi ghi nhận lỗi ra phía các caller; đường gọi thực tế trước lỗi thường theo hướng ngược lại: `UserController.show` → `UserService.load` → `UserRepository.find`.

Một số dạng đặc biệt:

- `(Native Method)`: frame của phương thức native, thường không có số dòng Java.
- `(Unknown Source)`: không có tệp/dòng nguồn phù hợp để in.
- `lambda$...`, `invoke`, `run`, `execute`: các frame có thể do lambda, reflection, proxy hoặc framework tạo ra. Không phải tất cả đều là vị trí nên sửa.
- Một stack frame chỉ cho thấy **nơi điều khiển đi qua**, không chứng minh chính phương thức đó là nguyên nhân nghiệp vụ.
- Số dòng phụ thuộc phiên bản mã, tối ưu hóa, thông tin debug và stack được in; đừng áp số dòng từ một bản build lên phiên bản triển khai khác.

`Throwable.getStackTrace()` trả một mảng `StackTraceElement[]`. Mỗi phần tử có các phương thức như `getClassName()`, `getMethodName()`, `getFileName()`, `getLineNumber()` và `isNativeMethod()`. Tên module/class loader có thể được bổ sung ở bản in stack trace hiện đại; cấu trúc hiển thị có thể khác đôi chút theo phiên bản JDK và cơ chế logging.

### Ví dụ chạy độc lập: đọc frame bằng API, không phụ thuộc số dòng

Tạo tệp `TraceFramesDemo.java`:

```java
public class TraceFramesDemo {
    static void leaf() {
        throw new IllegalStateException("dữ liệu hỏng");
    }

    static void middle() {
        leaf();
    }

    public static void main(String[] args) {
        try {
            middle();
        } catch (IllegalStateException e) {
            System.out.println("type = "
                    + e.getClass().getSimpleName());
            System.out.println("message = " + e.getMessage());

            StackTraceElement[] frames = e.getStackTrace();
            for (int i = 0; i < Math.min(3, frames.length); i++) {
                System.out.println("frame " + i + " = "
                        + frames[i].getMethodName());
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac TraceFramesDemo.java
java TraceFramesDemo
```

Kết quả:

```text
type = IllegalStateException
message = dữ liệu hỏng
frame 0 = leaf
frame 1 = middle
frame 2 = main
```

Đầu ra được viết theo tên phương thức vì số dòng nguồn trong stack trace có thể thay đổi khi sửa ví dụ. `frames[0]` là frame gần nhất với vị trí exception được tạo trong `leaf`. Một `Throwable` được **tạo trước rồi ném lại sau** có thể giữ stack trace ở thời điểm khởi tạo thay vì vị trí ném lại; nếu cần ghi lại tại vị trí khác có API `fillInStackTrace()`, nhưng việc sửa stack trace làm thay đổi thông tin chẩn đoán và không nên làm tùy tiện.

## 3. `Caused by`: một ngoại lệ bọc ngữ cảnh cho ngoại lệ trước đó

Khi một tầng thấp ném lỗi, tầng cao có thể bọc nó vào loại exception có ý nghĩa với tầng đó:

```java
try {
    repository.load();
} catch (java.io.IOException cause) {
    throw new IllegalStateException("Không đọc được hồ sơ", cause);
}
```

Đây là **mảnh mã minh họa**: giả định `repository.load()` có thể ném `IOException`. Constructor có đối số `cause` tạo liên hệ tới lỗi ban đầu. Người đọc có thể lần theo:

```text
IllegalStateException: Không đọc được hồ sơ
Caused by: IOException: ...
```

`Caused by` là **quan hệ nguyên nhân được tác giả mã hoặc thư viện ghi nhận**, không phải JVM tự suy luận quan hệ logic giữa mọi lỗi. Nếu tầng trên tạo ngoại lệ mới mà **không truyền cause**, chuỗi có thể bị đứt dù trên thực tế có lỗi trước đó.

### Ví dụ độc lập: tạo và lần theo ba tầng `Caused by`

Tệp `CauseChainDemo.java`:

```java
public class CauseChainDemo {
    static void parse() {
        throw new IllegalArgumentException("id phải dương");
    }

    static void service() {
        try {
            parse();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "không tải được người dùng", e);
        }
    }

    static void controller() {
        try {
            service();
        } catch (IllegalStateException e) {
            throw new RuntimeException(
                    "request thất bại", e);
        }
    }

    public static void main(String[] args) {
        try {
            controller();
        } catch (RuntimeException e) {
            int level = 0;
            for (Throwable current = e;
                    current != null;
                    current = current.getCause()) {
                System.out.println("level " + level + ": "
                        + current.getClass().getSimpleName()
                        + ": " + current.getMessage());
                level++;
            }
        }
    }
}
```

Chạy:

```bash
javac CauseChainDemo.java
java CauseChainDemo
```

Kết quả:

```text
level 0: RuntimeException: request thất bại
level 1: IllegalStateException: không tải được người dùng
level 2: IllegalArgumentException: id phải dương
```

Từ API, `e.getCause()` đi **một cấp** vào trong; lặp đến `null` tìm ngoại lệ sâu nhất trong *chuỗi cause*. `printStackTrace()` sẽ in cùng quan hệ bằng nhiều nhánh `Caused by:` và thường rút gọn các frame chung.

Không nhầm “ngoại lệ sâu nhất” với “vị trí duy nhất cần sửa” trong mọi hệ thống. Một `SocketTimeoutException` ở tầng sâu có thể là triệu chứng của sự cố mạng, thiếu tài nguyên hoặc deadline quá ngắn; phải xem cả ngữ cảnh request, thời gian chờ, các lần retry và log phụ cận. Tương tự, lỗi validate đầu vào có thể được phát hiện ở tầng thấp nhưng cần sửa dữ liệu gửi vào từ tầng cao.

### Cách giữ cause khi chuyển loại lỗi

Nên dùng constructor chấp nhận cause:

```java
throw new IllegalStateException("Không hoàn tất nghiệp vụ", original);
```

Thay vì:

```java
throw new IllegalStateException("Không hoàn tất nghiệp vụ");
```

Trong ví dụ, `original` là một `Throwable` đã bắt trong `catch`. Cách thứ hai làm mất liên kết nguyên nhân. Nếu thư viện cung cấp loại exception riêng, chọn constructor hoặc factory có khả năng bảo toàn cause. Các lệnh chỉ làm `throw original;` trong phạm vi thích hợp có thể giữ stack trace đang có, nhưng không thêm ngữ cảnh cao hơn.

Nếu dùng `initCause(...)`, chỉ có thể đặt cause trong trường hợp hợp lệ khi cause chưa được khởi tạo; thường nên ưu tiên constructor tường minh để tránh lỗi thiết lập hai lần.

## 4. `Suppressed`: những ngoại lệ phụ không làm mất lỗi chính

Một `Throwable` có thể chứa nhiều ngoại lệ **suppressed**, truy cập qua `getSuppressed()`. Quan hệ này khác `getCause()`:

- **Cause**: mô tả vì sao ngoại lệ hiện tại xảy ra; thông thường là chuỗi nguyên nhân.
- **Suppressed**: các lỗi bổ sung xảy ra trong khi đang xử lý, dọn dẹp hoặc đóng tài nguyên; chúng được gắn vào một exception hiện có để **bảo toàn cả lỗi chính lẫn lỗi phụ**.
- Một exception có thể đồng thời có **một cause** và **nhiều suppressed**.
- Các suppressed exception cũng có thể có cause/suppressed riêng; thông tin lỗi vì vậy là một **đồ thị có nhánh**, không chỉ danh sách tuyến tính.

Trường hợp phổ biến nhất là `try-with-resources`. Khi thân `try` đã ném exception, một hay nhiều lời gọi `close()` có thể cũng thất bại. Java giữ ngoại lệ của **thân `try` là primary exception** và đính kèm ngoại lệ từ `close()` vào mảng suppressed của primary.

### Ví dụ chạy độc lập: đóng hai tài nguyên đều thất bại

Tạo tệp `SuppressedResourcesDemo.java`:

```java
import java.io.IOException;

public class SuppressedResourcesDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("đóng " + name + " thất bại");
        }
    }

    static void execute() throws Exception {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {
            throw new IllegalStateException("xử lý thất bại");
        }
    }

    public static void main(String[] args) {
        try {
            execute();
        } catch (Exception e) {
            System.out.println("primary = "
                    + e.getClass().getSimpleName()
                    + ": " + e.getMessage());

            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("suppressed = "
                        + suppressed.getClass().getSimpleName()
                        + ": " + suppressed.getMessage());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SuppressedResourcesDemo.java
java SuppressedResourcesDemo
```

Kết quả chính xác:

```text
primary = IllegalStateException: xử lý thất bại
suppressed = IOException: đóng second thất bại
suppressed = IOException: đóng first thất bại
```

Giải thích:

1. `try` khai báo `first` rồi `second`.
2. Thân `try` ném `IllegalStateException`; đây là primary exception.
3. Các tài nguyên được đóng **theo thứ tự ngược với thứ tự khai báo**: `second` trước, `first` sau.
4. Cả hai lần `close()` ném `IOException`, nhưng exception chính được giữ; lỗi đóng lần lượt được gắn bằng suppressed.
5. `getSuppressed()` trả về mảng theo thứ tự được thêm vào, nên output trong ví dụ thể hiện `second` trước `first`.

Điều này rất quan trọng trong xử lý I/O, JDBC, file/stream và các loại `AutoCloseable` khác. Nếu chỉ nhìn dòng đầu “xử lý thất bại” và bỏ toàn bộ `Suppressed`, có thể bỏ sót việc đóng tài nguyên không thành công. Ngược lại, nếu chỉ chăm chú vào suppressed, có thể bỏ qua lỗi thân `try` là nguyên nhân chính.

### Khi `close()` mới là lỗi đầu tiên

Nếu thân `try` **hoàn thành bình thường**, nhưng `close()` ném exception, lỗi từ thao tác đóng đầu tiên theo thứ tự đóng ngược có thể trở thành **primary**. Nếu các lần đóng tiếp theo cũng lỗi, chúng có thể bị suppressed vào primary này. Vì thế không thể suy rằng mọi `Suppressed` đều có ngoại lệ từ thân `try`; hãy xem thứ tự thực thi và ngoại lệ ngoài cùng.

Ví dụ minh họa:

```java
try (Resource first = new Resource("first");
     Resource second = new Resource("second")) {
    System.out.println("thân try thành công");
}
```

Giả sử `Resource` giống class trong ví dụ trên, hai lần `close()` đều ném `IOException`. Thứ tự đóng là `second` rồi `first`; lỗi `close()` của `second` trở thành primary, còn lỗi `close()` của `first` thành suppressed. Đây là **mảnh mã sử dụng lại Resource**, không phải chương trình độc lập.

### `addSuppressed(...)` để bảo toàn lỗi thủ công

Java cho phép gắn ngoại lệ phụ:

```java
try {
    doWork();
} catch (Exception primary) {
    try {
        cleanup();
    } catch (Exception cleanupFailure) {
        primary.addSuppressed(cleanupFailure);
    }
    throw primary;
}
```

Đây là **mảnh mã minh họa** trong một phương thức có `throws Exception`; `doWork()` và `cleanup()` là các hàm đã được ứng dụng định nghĩa. `Throwable.addSuppressed(...)` không cho phép tự suppress chính đối tượng (`self-suppression`) và không nhận `null`. Thông thường, hãy dùng `try-with-resources` thay vì viết lại logic quản lý close phức tạp.

**Đừng chuyển suppressed thành cause một cách máy móc.** Ngoại lệ đóng tài nguyên thường chỉ là **lỗi bổ sung xảy ra trong dọn dẹp**, không phải nguyên nhân của ngoại lệ ban đầu.

## 5. Cách đọc `... N more` chính xác

Trong stack trace do `Throwable.printStackTrace()` in, Java tìm những frame ở **cuối stack trace của một throwable con** (cause hoặc suppressed) giống với phần cuối stack trace của throwable **bao ngoài trực tiếp đang được in**. Thay vì lặp lại các frame giống nhau, trình in ghi:

```text
... 3 more
```

Ý nghĩa chính xác: **ba stack frame ở cuối nhánh con trùng với ba frame cuối của stack cha** và đã được lược bỏ khỏi phần in của nhánh con.

Đây là **rút gọn frame trùng nhau**, không phải:

- Ba ngoại lệ khác nữa.
- Ba dòng log bị mất vì vượt giới hạn.
- Ba vòng retry.
- Ba phần của root cause chưa được tìm thấy.
- Một lời khẳng định rằng exception không còn stack frame nào khác.

### Ví dụ sơ đồ minh họa

Giả sử stack trace của exception ngoài cùng:

```text
java.lang.IllegalStateException: service lỗi
    at demo.Service.load(Service.java:30)
    at demo.Controller.handle(Controller.java:20)
    at demo.Main.main(Main.java:10)
```

Stack đầy đủ của cause bên trong:

```text
java.lang.IllegalArgumentException: id sai
    at demo.Parser.parse(Parser.java:12)
    at demo.Service.load(Service.java:26)
    at demo.Controller.handle(Controller.java:20)
    at demo.Main.main(Main.java:10)
```

Hai frame cuối `Controller.handle(...)` và `Main.main(...)` là **suffix chung**, nên phần cause có thể được in như:

```text
Caused by: java.lang.IllegalArgumentException: id sai
    at demo.Parser.parse(Parser.java:12)
    at demo.Service.load(Service.java:26)
    ... 2 more
```

Để tái tạo **ý nghĩa** của nhánh cause, hãy đọc `... 2 more` bằng cách nối vào hai frame ở cuối stack cha tương ứng. Con số là **độ dài suffix chung** giữa hai stack trace trong cây in, không phải số frame phía trên bị rút tùy ý.

### Suppressed cũng có `... N more`

Dưới `Suppressed:`, quy tắc frame chung tương tự nhưng nhánh suppressed được thụt lề sâu hơn và so với stack enclosing của **nhánh cha đang in**. Đừng luôn so với frame của exception gốc cấp cao nhất nếu suppressed nằm sâu trong một cause.

Ví dụ cấu trúc:

```text
java.lang.IllegalStateException: xử lý lỗi
    at demo.Service.run(Service.java:27)
    at demo.Main.main(Main.java:7)
    Suppressed: java.io.IOException: đóng stream lỗi
        at demo.Stream.close(Stream.java:16)
        at demo.Service.run(Service.java:27)
        ... 1 more
Caused by: java.lang.IllegalArgumentException: đầu vào lỗi
    at demo.Parser.parse(Parser.java:9)
    at demo.Service.run(Service.java:21)
    ... 1 more
```

Ở đây `... 1 more` trong hai nhánh đều ám chỉ phần frame đuôi trùng với stack của exception bao ngoài tương ứng. Mỗi nhánh được rút gọn **độc lập**.

### Rút gọn mặc định khác với log bị truncate

`Throwable.printStackTrace(...)` **có quy tắc rút gọn frame trùng**. Một hệ thống logging có thể thêm cơ chế khác: giới hạn số dòng, chỉ in vài nguyên nhân cuối, che dữ liệu, cấu hình pattern `%ex{short}` hoặc cắt sự kiện log dài. Khi thấy stack trace mất đoạn **không có dạng `... N more` chuẩn**, hãy kiểm tra cấu hình logger/appender, giới hạn ingest, dịch vụ tập trung log và kích thước sự kiện.

Đối với `... N more` chuẩn, các frame ấy có thể suy ra từ stack cha trong **cùng bản in đầy đủ**; nếu bản in cha đã bị hệ thống log cắt, việc suy ra có thể không còn thực hiện được.

## 6. Thứ tự đọc stack trace thực tế

Một quy trình điều tra hiệu quả:

1. **Lấy đủ sự kiện log liên quan**: timestamp, tên dịch vụ, request/correlation ID, thread, phiên bản build, vị trí triển khai. Một stack trace đơn lẻ thường thiếu ngữ cảnh xảy ra lỗi.
2. **Đọc exception ngoài cùng**: thao tác cấp ứng dụng nào báo thất bại? Message mô tả điều kiện gì? Có dấu hiệu bọc lỗi giữa các layer không?
3. **Quét các nhánh `Caused by`**: xác định ngoại lệ ở tầng sâu có thông tin cụ thể nhất như SQL constraint, connection timeout, parse error, validation error hay I/O.
4. **Tìm frame do mình sở hữu** gần vị trí phát sinh lỗi: ưu tiên class/package ứng dụng so với frame wrapper chung trong framework, nhưng vẫn đọc ngữ cảnh gọi.
5. **Kiểm tra `Suppressed`**: có lỗi đóng tài nguyên, cleanup hoặc hoạt động phụ nào thất bại không? Chúng có làm vấn đề trầm trọng hơn không?
6. **Giải nghĩa `... N more`**: frame đuôi của từng nhánh có thể đã in ở stack cha; không bỏ qua các frame chung khi dựng lại đường gọi.
7. **Đối chiếu với mã và build đúng phiên bản**: số dòng trên production phải khớp artifact hoặc commit đang chạy.
8. **Kiểm tra dòng thời gian và tác động**: yêu cầu bị từ chối, tác vụ retry, tài nguyên chưa đóng, transaction rollback, dữ liệu đã ghi một phần hay không.
9. **Xác nhận giả thuyết bằng quan sát hoặc bài test có mục tiêu**: không sửa theo lớp exception duy nhất nếu điều kiện gây lỗi chưa được tái hiện.

Đọc cause sâu nhất trước **là mẹo hữu ích**, không phải quy tắc tuyệt đối. Một lỗi `NullPointerException` sâu có thể là bug cục bộ; một lỗi `IOException` sâu có thể là hệ quả của mất kết nối ở tầng mạng; một lỗi suppressed khác có thể báo dọn dẹp cũng bị lỗi. Cần xét cả cấu trúc lẫn bối cảnh.

## 7. In và duyệt nguyên nhân, suppressed bằng Java

`Throwable` hỗ trợ các API:

| API | Trả về/tác động | Lưu ý |
|---|---|---|
| `getMessage()` | Thông điệp | Có thể `null` |
| `getLocalizedMessage()` | Thông điệp cục bộ hóa nếu lớp triển khai hỗ trợ | Mặc định thường giống `getMessage()` |
| `getCause()` | Throwable nguyên nhân hoặc `null` | Theo chuỗi cause |
| `getSuppressed()` | Mảng các Throwable bị suppressed | Có thể rỗng |
| `getStackTrace()` | Mảng `StackTraceElement` | Là bản sao, có thể rỗng hoặc được thay đổi bằng API |
| `printStackTrace()` | In exception, frames, suppressed và cause | Mặc định dùng `System.err` |
| `printStackTrace(PrintStream)` | In vào stream được chỉ định | Ví dụ dùng `System.out` khi cần trình diễn |
| `addSuppressed(Throwable)` | Gắn lỗi phụ vào exception chính | Không tự gắn chính nó hoặc `null` |
| `setStackTrace(StackTraceElement[])` | Thay mảng stack trace lưu trong throwable | Chỉ khi có lý do đặc biệt, tránh làm mất thông tin thực |

Duyệt cause bằng `for (Throwable t = e; t != null; t = t.getCause())` đủ cho hầu hết exception Java thông thường. Nếu xây một **trình duyệt lỗi tổng quát** đọc các Throwable do plugin hoặc thư viện lạ tạo, nên có tập identity để tránh lặp vô hạn khi đồ thị ngoại lệ bất thường, và phân biệt cause/suppressed.

### Ví dụ độc lập: báo cáo từng nhánh bằng API

Tệp `ThrowableTreeDemo.java`:

```java
import java.io.IOException;

public class ThrowableTreeDemo {
    public static void main(String[] args) {
        IOException root = new IOException("không đọc được tệp");
        IllegalStateException primary =
                new IllegalStateException("không tải được dữ liệu", root);

        primary.addSuppressed(
                new IOException("đóng stream thất bại"));
        primary.addSuppressed(
                new IllegalArgumentException("định dạng log không hợp lệ"));

        System.out.println("primary = "
                + primary.getClass().getSimpleName());

        Throwable cause = primary.getCause();
        System.out.println("cause = "
                + cause.getClass().getSimpleName()
                + ": " + cause.getMessage());

        for (Throwable suppressed : primary.getSuppressed()) {
            System.out.println("suppressed = "
                    + suppressed.getClass().getSimpleName()
                    + ": " + suppressed.getMessage());
        }
    }
}
```

Chạy:

```bash
javac ThrowableTreeDemo.java
java ThrowableTreeDemo
```

Output:

```text
primary = IllegalStateException
cause = IOException: không đọc được tệp
suppressed = IOException: đóng stream thất bại
suppressed = IllegalArgumentException: định dạng log không hợp lệ
```

Ví dụ chứng minh **cause và suppressed cùng tồn tại** trên một exception. Khi in bằng `primary.printStackTrace(System.out)`, Java sẽ hiển thị thông tin chi tiết của cả hai nhánh cùng các frame. Nếu thêm `printStackTrace(...)` vào bài này, số dòng nguồn trong output phụ thuộc vị trí mã; vì vậy ví dụ trên dùng API để có kết quả chính xác và dễ so sánh.

## 8. Khi nào `Caused by` không xuất hiện?

Không phải stack trace nào cũng có `Caused by`:

- Ngoại lệ được tạo mà không có cause.
- Mã bọc exception nhưng quên `cause` trong constructor.
- Framework/logger đã cấu hình chỉ in outer exception hoặc chỉ in root cause.
- Log bị cắt, phân đoạn hoặc chỉ ghi `e.getMessage()`.
- Throwable được tạo/biến đổi khác thường và cause không còn được bảo toàn.
- Một exception chỉ mang suppressed mà không có cause.

Ví dụ ghi log không đủ:

```java
System.err.println(e.getMessage());
```

Dòng này chỉ in thông điệp và **không cung cấp stack trace, cause hay suppressed**. Kể cả khi message diễn giải rất hay, người đọc vẫn khó xác định code path và các nhánh lỗi đi kèm.

Nếu dùng logging framework, truyền **đối tượng Throwable** đúng overload của API để giữ toàn bộ stack. Ví dụ minh họa giả định `logger` có API SLF4J tương thích:

```java
logger.error("Không xử lý được yêu cầu", e);
```

Đây là **mảnh mã tích hợp**, không phải chương trình độc lập vì `logger` và thư viện chưa được khai báo. Tùy API logging, vị trí throwable trong tham số và cấu hình trình in khác nhau; hãy xem tài liệu logger đang dùng. Tránh chỉ ghi `logger.error("{}", e.getMessage())` nếu mục đích là chẩn đoán đầy đủ.

## 9. `printStackTrace()` và framework logging khác nhau ở đâu?

`Throwable.printStackTrace()` cung cấp bản in trực tiếp từ đối tượng ngoại lệ; mặc định là `System.err`. Nó hữu ích khi học, trong ví dụ nhỏ, hoặc khi khắc phục sự cố cục bộ.

Trong dịch vụ thực tế, logger thường cần cung cấp:

- **Ngữ cảnh có cấu trúc**: module, operation, request ID, trace ID, tên component, tình trạng retry, phiên bản triển khai.
- **Thời điểm và mức độ nghiêm trọng**: lỗi do dữ liệu không hợp lệ có thể khác lỗi mất DB nghiêm trọng.
- **Throwable đầy đủ**: cho phép cấu hình in cause, suppressed, frames.
- **Bảo vệ dữ liệu**: không ghi token, mật khẩu, key, thông tin nhạy cảm hay body request không được phép.
- **Giới hạn lặp và thu thập**: không ghi cùng một exception ở mọi layer tạo ra log trùng và làm khó tìm lỗi thực.

Một chiến lược hay dùng: ở tầng thấp, **ném lại với cause** và ngữ cảnh cần thiết; ở ranh giới ứng dụng chịu trách nhiệm xử lý thất bại, **ghi log một lần đủ thông tin**. Nếu tầng thấp thực sự xử lý phục hồi và không đẩy lỗi đi tiếp, có thể ghi log ở tầng đó, nhưng tránh nhân bản những log cùng sự kiện.

Stack trace có thể bộc lộ đường dẫn tệp, tên lớp, SQL, tham số hoặc giá trị trong message. Không xuất toàn bộ trace ra phản hồi công khai cho người dùng cuối; có thể gửi mã lỗi/correlation ID và lưu chi tiết trong hệ thống log được kiểm soát.

## 10. `getStackTrace()` không phải “nguyên nhân” và không thay `getCause()`

Một `StackTraceElement[]` chỉ mô tả một ngăn xếp gọi được ghi nhận. Nó không tự chỉ ra mọi ngoại lệ đã xảy ra. Ví dụ một wrapper được tạo trong `service()` sẽ có frame đầu ở `service()`, còn nguyên nhân được tạo trong `repository()` có frame đầu của riêng nó. Vì thế muốn biết **tại sao** wrapper xuất hiện cần `getCause()` chứ không chỉ frame đầu wrapper.

```text
Outer exception: IllegalStateException
  stack[0] = Service.load(...)
  cause    = IOException
                 stack[0] = Repository.read(...)
```

Khi gặp suppressed, tình huống tương tự:

```text
Primary exception: IllegalStateException
  cause         → IOException("đọc dữ liệu lỗi")
  suppressed[0] → IOException("đóng dữ liệu lỗi")
```

Hai nhánh `cause` và `suppressed` mang **những đường thực thi, thời điểm và trách nhiệm khác nhau**. Chúng có thể dùng chung các frame ở cuối do cùng được gọi từ một luồng xử lý.

Cũng không nên giả định nguyên nhân sâu nhất luôn có nhiều frame nhất. Mỗi Throwable có mảng stack khác nhau; Java có thể rút gọn frame khi in, nhưng đó chỉ là cách trình bày.

## 11. Phân tích stack trace dài có framework, proxy và bất đồng bộ

Stack trace trong ứng dụng Java hiện đại có thể chứa nhiều frame do framework hoặc thư viện:

```text
at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(...)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(...)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(...)
at java.base/java.lang.Thread.run(...)
```

Đây là **đoạn ví dụ dạng frame có thể gặp**, không bảo đảm mọi bản JDK có cùng lớp nội bộ, dòng nguồn hoặc thứ tự. Khi điều tra:

1. Tìm exception message và nguyên nhân cụ thể nhất.
2. Xác định frame đầu thuộc package ứng dụng **trong đúng nhánh Throwable đang đọc**.
3. Nhìn các frame gần đó để hiểu đường đi từ scheduler, executor, HTTP handler hoặc service.
4. Với async, lưu ý stack trace ghi tại thời điểm Throwable được tạo/điền stack, **không thể tự khôi phục đầy đủ chuỗi caller đã submit task trước đó**.
5. Nếu cần lần theo công việc xuyên thread, bổ sung request ID, trace/span ID, context propagation và logging có cấu trúc.

Một `CompletionException` hoặc `ExecutionException` thường là **wrapper** báo rằng một tác vụ bất đồng bộ đã hoàn thành thất bại:

```text
java.util.concurrent.CompletionException: java.io.IOException: ...
Caused by: java.io.IOException: ...
```

Khi `CompletableFuture.join()` gặp exceptional completion thường có `CompletionException` ở phía quan sát; với `Future.get()` hoặc `CompletableFuture.get()`, caller có thể thấy `ExecutionException`. Đừng sửa code chỉ vì thấy wrapper; hãy kiểm tra cause để biết lỗi bên trong worker hoặc pipeline. Các trường hợp cancellation và interrupt ở caller cần phân biệt riêng với lỗi của worker.

Trong async, exception được bọc thêm một tầng không làm mất chuỗi nguyên nhân nếu cause được giữ đúng. Nhưng stack của từng Throwable có thể xuất hiện ở thread khác nhau; không nên ghép tất cả frame thành một lời gọi đồng bộ liên tục.

## 12. Một số tình huống điều tra cụ thể

### Tình huống A: request báo lỗi service, root cause là dữ liệu đầu vào

```text
java.lang.IllegalStateException: Không tạo được hóa đơn
    at app.InvoiceService.create(InvoiceService.java:70)
Caused by: java.lang.NumberFormatException: For input string: "abc"
    at java.base/java.lang.Integer.parseInt(Integer.java:...)
    at app.InvoiceParser.parseId(InvoiceParser.java:24)
    ... 1 more
```

Đây là **log minh họa**, dấu `...` trong số dòng của lớp JDK không phải output literal chuẩn. Nếu lỗi xảy ra khi parse `"abc"` thành số, chỗ sửa có thể là xác thực đầu vào, thông báo lỗi cho caller hoặc quy tắc mapping. Cần đối chiếu `InvoiceParser.parseId` và dữ liệu thực, không suy từ `IllegalStateException` ngoài cùng rằng state của JVM hỏng.

### Tình huống B: thao tác đã lỗi, đóng tài nguyên cũng lỗi

```text
java.lang.IllegalStateException: Ghi báo cáo thất bại
    at app.ReportService.write(ReportService.java:55)
    Suppressed: java.io.IOException: Đóng output thất bại
        at app.ReportFile.close(ReportFile.java:20)
        ... 1 more
Caused by: java.io.IOException: Ghi tệp thất bại
    at app.ReportFile.write(ReportFile.java:15)
    ... 1 more
```

Cần kiểm tra **hai vấn đề**: vì sao ghi thất bại (cause), và liệu cleanup/close thất bại có để lại tài nguyên hoặc dữ liệu dở dang hay không (suppressed). Nếu chỉ sửa `close()`, lỗi ghi ban đầu có thể vẫn xảy ra.

### Tình huống C: `... N more` làm người đọc tưởng thiếu tầng lỗi

```text
Caused by: java.lang.IllegalArgumentException: id rỗng
    at app.Parser.readId(Parser.java:18)
    ... 4 more
```

Trong bản in chuẩn, các frame chung đã có ở **stack cha** của nhánh ấy. Nếu còn đủ bản in, đọc bốn frame cuối của parent để dựng bối cảnh. Nếu hệ thống logging chỉ thu dòng `Caused by` này mà không có parent, cần lấy **sự kiện log đầy đủ**, không thể đoán chắc bốn frame từ riêng dòng rút gọn.

### Tình huống D: logger chỉ in thông điệp

```text
Không xử lý được request: connection refused
```

Không đủ dữ liệu để phân biệt lỗi socket, lỗi wrapper, vị trí gọi và suppressed. Nếu có stack trace gốc, hãy dùng logging API nhận Throwable và bổ sung correlation ID; tránh vội sửa `catch` bằng cách nuốt lỗi.

## 13. Những lỗi phổ biến khi đọc hoặc ghi log

### Chỉ đọc dòng đầu

Dòng đầu có thể là wrapper như `CompletionException` hoặc `IllegalStateException` của service. Nên lần theo `Caused by`, đọc frame của ngoại lệ gốc và ngữ cảnh request. Tuy nhiên root cause sâu nhất chưa chắc là kết luận cuối cùng.

### Đọc `Suppressed` như root cause

Suppressed là **nhánh lỗi phụ**; trong `try-with-resources`, nó thường là lỗi `close()` xảy ra **sau khi** thân `try` thất bại. Cần điều tra cả primary lẫn cleanup, không đảo quan hệ bằng suy đoán.

### Đọc `... 2 more` thành “còn hai lỗi nữa”

Nó nói có **hai frame ở đuôi trùng với frame đuôi của enclosing stack trace**, được rút gọn. Các exception khác chỉ thể hiện bằng những nhánh Throwable như `Caused by` hoặc `Suppressed`.

### Giữ message nhưng quên cause khi bọc exception

Sai:

```java
throw new IllegalStateException("Tải dữ liệu thất bại");
```

Nếu đang bọc một ngoại lệ vừa bắt, hãy dùng constructor có `cause`. Chỉ tạo message mới thường làm đứt nguồn gốc lỗi và khiến log nhìn như exception bắt đầu ở service.

### Ghi log ở mọi `catch` rồi ném lại

Mỗi tầng ghi cùng stack gây log storm, tăng phí thu thập và khó nhóm lỗi. Nên gắn ngữ cảnh, giữ cause và để tầng chịu trách nhiệm xử lý ghi log một lần khi có thể.

### Đổ lỗi cho frame của framework

Stack có thể có nhiều frame của Spring, servlet, executor hoặc reflection. Chúng cho biết ngữ cảnh chạy, không tự động chứng minh framework có bug. Chọn frame ứng dụng phù hợp và đọc nguyên nhân.

### Tin tuyệt đối số dòng khi build đã thay đổi

Dòng trong exception phản ánh class được biên dịch và debug metadata thực tế. Nếu CI/CD thay đổi source, so với sai commit dễ sửa nhầm chỗ. Giữ version/commit ID của artifact trong log.

### Bỏ qua suppressed khi close hoặc rollback thất bại

Cleanup lỗi có thể làm rò tài nguyên hoặc để dữ liệu ở trạng thái không mong muốn. Đừng biến mọi suppressed thành cảnh báo vô hại; đánh giá theo tính chất tài nguyên.

### Tự cắt stack trace bằng xử lý chuỗi

Cắt `stackTraceString` theo số dòng có thể mất cause hoặc suppressed. Dùng logger/appender có cấu hình phù hợp, giữ event đầy đủ ở nơi đủ quyền và chỉ rút gọn có kiểm soát ở bề mặt hiển thị.

### Ghi nguyên stack trace cho người dùng cuối

Stack có thể tiết lộ package nội bộ, tên file, câu truy vấn, endpoint, metadata và dữ liệu trong thông điệp. Tách thông báo công khai ra khỏi chi tiết log nội bộ, dùng mã lỗi để hỗ trợ đối chiếu.

## 14. Bài tập đọc stack trace và đáp án

Xem bản in minh họa:

```text
java.lang.IllegalStateException: Không xuất được CSV
    at export.CsvService.export(CsvService.java:40)
    at export.Main.main(Main.java:10)
    Suppressed: java.io.IOException: Lỗi đóng writer
        at export.CsvWriter.close(CsvWriter.java:21)
        at export.CsvService.export(CsvService.java:40)
        ... 1 more
Caused by: java.lang.IllegalArgumentException: Thiếu tên cột
    at export.CsvValidator.check(CsvValidator.java:15)
    at export.CsvService.export(CsvService.java:36)
    ... 1 more
```

Các câu hỏi và câu trả lời:

1. **Exception ngoài cùng?** `IllegalStateException`, thông điệp “Không xuất được CSV”.
2. **Nguyên nhân được khai báo?** `IllegalArgumentException` về tên cột, được nối bằng `Caused by`.
3. **Lỗi phụ?** `IOException` khi đóng writer, được gắn qua `Suppressed`.
4. **Có mấy tầng cause được in?** Một cause trực tiếp trong ví dụ; không đếm suppressed là cause.
5. **`... 1 more` có nghĩa gì?** Mỗi nhánh con rút gọn một frame đuôi trùng với stack enclosing của nó.
6. **Nên kiểm tra code nào?** `CsvValidator.check`, luồng gọi `CsvService.export`, và `CsvWriter.close`; ngoài ra kiểm tra đầu vào CSV và chính sách đóng writer.
7. **Có chứng cứ lỗi close là nguyên nhân thiếu tên cột?** Không. Hai nhánh thể hiện hai lỗi khác nhau trong luồng xử lý.

## 15. Checklist chẩn đoán và ghi log

Trước khi khép lại một incident Java, kiểm tra:

- Đã đọc **exception type + message** của outer exception và mọi `Caused by` quan trọng chưa?
- Đã tìm **frame ứng dụng** và đối chiếu mã nguồn của đúng bản build chưa?
- Đã đọc `Suppressed` và xác định lỗi cleanup có hậu quả riêng không?
- Đã giải nghĩa `... N more` là **frame chung được rút gọn** chưa?
- Log có đầy đủ Throwable, thời gian, thread, request/trace ID và ngữ cảnh cần thiết không?
- Có thể phân biệt exception trong worker với wrapper `ExecutionException`/`CompletionException` ở caller không?
- Có vô tình mất cause khi chuyển exception, hoặc in log trùng tại nhiều tầng không?
- Có dữ liệu nhạy cảm nào trong message, stack trace hoặc ngữ cảnh log cần che đi không?
- Đã kiểm tra tác động thực tế (giao dịch, tệp, tài nguyên, retry) ngoài thông điệp lỗi chưa?

**Ghi nhớ:** `Caused by` nối **nguyên nhân**, `Suppressed` giữ **những lỗi phụ**, còn `... N more` rút gọn **các stack frame trùng ở đuôi**. Đọc cả ba thành phần trong đúng nhánh Throwable rồi đối chiếu với mã nguồn, bản build và ngữ cảnh sự kiện mới có thể đưa ra kết luận chẩn đoán đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-003">Tìm frame thuộc mã ứng dụng và đối chiếu dòng nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm frame thuộc mã ứng dụng và đối chiếu dòng nguồn

## 1. Mục tiêu: đi từ stack trace đến đúng câu lệnh trong mã Java

Khi Java ném một exception, thông tin hữu ích nhất thường không phải chỉ là thông báo lỗi mà là **stack trace**: danh sách các vị trí thực thi (*stack frames*) gắn với exception. Mỗi frame có thể chỉ ra lớp, phương thức, tên file nguồn và số dòng. Nhiệm vụ chẩn đoán là **tìm frame liên quan đến mã ứng dụng**, mở đúng phiên bản mã đã chạy, rồi đối chiếu dòng đó với ngữ cảnh và nguyên nhân thực sự.

Ví dụ một trace rút gọn:

~~~text
java.lang.IllegalArgumentException: quantity must be positive
    at com.acme.orders.QuantityValidator.check(QuantityValidator.java:18)
    at com.acme.orders.OrderService.create(OrderService.java:47)
    at com.acme.orders.OrderController.submit(OrderController.java:29)
    at java.base/java.lang.Thread.run(Thread.java:840)
~~~

Đây là **trace minh họa**, không phải output của chương trình runnable ở phần sau. Giả sử các lớp `com.acme.orders.*` thuộc chính ứng dụng đang điều tra:

- Dòng `QuantityValidator.java:18` là điểm đầu của trace, thường gần lệnh ném exception nhất.
- `OrderService.java:47` là call site gọi validator; nó giúp hiểu đầu vào sai từ đâu.
- `OrderController.java:29` là điểm đi vào nghiệp vụ qua lớp controller.
- Frame trong `java.base` thuộc thư viện nền tảng; thường chỉ là phần khung thực thi.

Không phải lúc nào “frame ứng dụng đầu tiên” cũng là nơi **cần sửa**. Nếu lỗi xảy ra trong một thư viện vì ứng dụng cung cấp tham số sai, frame thư viện có thể ở đầu, còn frame ứng dụng đầu tiên chính là call site truyền tham số cần được kiểm tra. Ngược lại, nếu exception bị bọc qua nhiều tầng, trace ở ngoài chỉ cho thấy vị trí bọc; muốn tìm lỗi gốc phải đọc `Caused by` và trace riêng của từng cause.

Bài học này tập trung vào một quy trình thực hành: **đọc cấu trúc frame → phân biệt mã ứng dụng với framework → đi theo chuỗi cause/suppressed → đối chiếu đúng class/file/line với artifact đã triển khai → kiểm chứng giả thuyết bằng ngữ cảnh và tái hiện**.

## 2. Một stack frame chứa những gì?

Dạng hiển thị quen thuộc là:

~~~text
at package.Class.method(FileName.java:123)
~~~

Các thành phần:

| Thành phần | Cách hiểu | Câu hỏi cần hỏi |
| --- | --- | --- |
| `package.Class` | Tên nhị phân của lớp chứa frame | Lớp thuộc module/package ứng dụng, thư viện hay JDK? |
| `method` | Tên phương thức đang thực thi; có thể là `<init>`, `<clinit>` hoặc tên tổng hợp | Đây là constructor, static initialization, lambda hay phương thức bình thường? |
| `FileName.java` | Tên file nguồn gắn với class khi biên dịch, nếu có metadata | File này ở phiên bản source nào? |
| `123` | Số dòng tương ứng với vị trí code theo thông tin debug của class | Dòng đó là lệnh ném lỗi hay lệnh gọi hàm? |
| `at` và phần đầu frame | Quy ước định dạng khi in stack trace | Có module/class loader hoặc thông tin khác ở trước tên class không? |

Từ Java 9, trace có thể hiển thị tiền tố module hoặc class loader, ví dụ:

~~~text
at java.base/java.util.Objects.requireNonNull(Objects.java:209)
at my.module/com.example.app.PaymentService.pay(PaymentService.java:41)
~~~

Các số dòng ở đây **chỉ minh họa**, không phải lời khẳng định về số dòng của mọi phiên bản JDK. `java.base/` là tên module, không phải tên package. Để phân loại frame, cần nhìn vào tên class/package thực sự thay vì cắt chuỗi từ `at` theo một định dạng cố định.

### 2.1. Tên và số dòng có thể không hiện ra

Một số cách hiển thị:

~~~text
at com.example.app.Job.run(Job.java:57)
at com.example.app.GeneratedProxy.invoke(Unknown Source)
at java.base/java.lang.Object.wait(Native Method)
~~~

`Unknown Source` thường nghĩa là không có thông tin file/line đủ dùng cho frame đó. `Native Method` chỉ một phương thức native. Tùy class file và Java runtime, tên file vẫn có thể hiện mà số dòng không có, hoặc cả hai đều vắng.

**Không suy ra rằng chương trình không chạy mã ứng dụng** chỉ vì trace không in số dòng. Hãy kiểm tra class file thực đang chạy, tùy chọn biên dịch, cách sinh bytecode và thông tin debug.

## 3. Thứ tự frame: nơi bắt đầu điều tra và chuỗi lời gọi

Một trace của `Throwable` thường được in theo thứ tự **trên cùng là frame gần vị trí thu thập stack nhất**, tiếp theo là các caller theo hướng đi ngược call stack. Với exception được tạo và ném tại cùng vị trí thông thường, frame đầu thường chỉ đến dòng `throw` hoặc lệnh tạo exception.

Sơ đồ:

~~~text
HTTP endpoint()
    -> OrderService.create()
        -> Validator.check()
            -> throw IllegalArgumentException

Trace khi ném:
    Validator.check(Validator.java:18)          <-- gần điểm ném
    OrderService.create(OrderService.java:47)   <-- chỗ gọi validator
    OrderController.endpoint(Controller.java:29)
    ... framework / thread entry ...
~~~

Những điểm cần nhớ:

1. **Trên cùng không có nghĩa luôn là nguyên nhân cuối cùng**: exception có thể là wrapper như `CompletionException`, `ExecutionException` hoặc `InvocationTargetException`.
2. **Frame ứng dụng đầu tiên không luôn là lỗi lập trình của frame đó**: nó có thể chỉ nhận dữ liệu lỗi từ tầng khác; kiểm tra invariant và đầu vào tại các caller.
3. **Frame của `catch` không nhất thiết là nơi exception bắt đầu**: nếu code chỉ `catch (Exception e) { log(e); }`, trace của `e` vẫn mô tả đường đi đã được ghi lại, không biến thành trace mới tại chỗ log.
4. **Đừng chỉ xem frame cuối hoặc exception cuối cùng được in**: ở một trace nhiều lớp, phần `Caused by` có trace riêng, thường gần nơi lỗi sâu nhất.

### 3.1. Exception được tạo khác thời điểm nó được ném

Stack trace gắn với trạng thái của `Throwable` khi thông tin stack được lấy (thông thường vào lúc đối tượng Throwable được tạo). Ví dụ:

~~~java
IllegalStateException error =
        new IllegalStateException("delayed failure");

doSomethingElse();

throw error;
~~~

Đây là **fragment minh họa**, `doSomethingElse` giả định đã được khai báo. Exception được tạo trước rồi ném sau; trace có thể chỉ tới **dòng tạo đối tượng** thay vì đúng dòng `throw error`. Gọi lại `fillInStackTrace()` có thể thay đổi thông tin stack, nhưng không nên dùng điều đó chỉ để khiến log “đẹp”; nó làm thay đổi nguồn chứng cứ. Một exception cũng có thể được tạo bằng constructor không bật writable stack trace hoặc bị can thiệp `setStackTrace()`, khiến trace không phản ánh đường thực thi thường thấy.

Vì thế đừng khẳng định “line 12 chắc chắn chính là câu lệnh throw” khi bằng chứng chỉ là một StackTraceElement. Hãy đọc code quanh dòng và hiểu thời điểm tạo/ném exception.

## 4. Quy trình bảy bước tìm đúng frame ứng dụng

Khi một log stack trace xuất hiện trong production, có thể làm theo trình tự sau:

1. **Giữ toàn bộ sự kiện:** tên exception, message, stack trace đầy đủ, `Caused by`, `Suppressed`, timestamp, request ID/correlation ID, deployment/build ID và thread name.
2. **Xác định exception ngoài cùng và cause gốc:** wrapper xuất phát từ async/framework hay lỗi thực ở tầng sâu? Đọc từng khối trace thay vì chỉ nhìn dòng đầu.
3. **Khoanh package/module ứng dụng:** dựa trên namespace thực, ví dụ `com.example.billing`, `vn.company.report`, hoặc module ứng dụng đã biết; không chỉ tìm chuỗi tên công ty vì có thể có SDK cùng tên.
4. **Chọn frame liên quan:** trong stack của cause thích hợp, tìm frame ứng dụng gần điểm lỗi nhất; rồi xem các frame ứng dụng phía dưới để hiểu đầu vào và luồng gọi.
5. **Xác thực phiên bản code:** xác định jar/image/build/commit chính xác đã chạy. Mở file nguồn tương ứng, không tự động dùng bản mới nhất trên nhánh phát triển.
6. **Đọc ngữ cảnh quanh dòng:** xem câu lệnh, tham số, trạng thái trước đó, catch/rethrow, side effect, đường dữ liệu từ caller; có thể cần đọc thêm các frame trên/dưới.
7. **Tái hiện và sửa tại mức invariant:** thêm test chứng minh nguyên nhân, ghi nhận lỗi hợp lý và bảo đảm không chỉ thay đổi dòng in log; với lỗi race/async, cần kiểm tra thread và thời điểm completion.

Quy trình có tính lặp: sau khi đọc source, có thể thấy frame đang xét là một wrapper, từ đó quay lại cause sâu hơn hoặc một frame gọi phía dưới.

### 4.1. Chọn frame không phải là lọc bỏ thư viện

Nếu trace là:

~~~text
java.lang.IllegalArgumentException: negative length
    at java.base/java.util.Arrays.copyOf(Arrays.java:...)
    at com.example.app.DocumentService.copy(DocumentService.java:64)
    at com.example.app.RequestHandler.handle(RequestHandler.java:31)
~~~

Các dấu `...` là **chỗ minh họa không chứa số dòng thật**. Frame thư viện ở trên cho biết lỗi được phát hiện tại `Arrays.copyOf`; frame ứng dụng `DocumentService.java:64` có thể là nơi **truyền độ dài âm** vào thư viện. Cần kiểm tra chính logic tính toán kích thước/giá trị đầu vào tại đây, chứ không “sửa JDK” chỉ vì frame trên cùng thuộc `java.base`.

Ngược lại, nếu package đứng đầu là một thư viện bên thứ ba và lỗi nằm sâu trong nội bộ thư viện, có thể cần kiểm tra input/phiên bản dependency, mở source dependency đúng release, xem release notes hoặc tối giản case để báo lỗi thư viện. Đừng tự quy hết cho mã ứng dụng.

## 5. Ví dụ Java 17: trích frame ứng dụng và đối chiếu chính xác dòng

Ví dụ đầy đủ dưới đây ném `IllegalArgumentException` trong phương thức `parse`, qua các caller `load` và `endpoint`. `main` bắt exception, gọi `getStackTrace()` và chỉ in các frame của class ứng dụng.

Lưu chính xác thành file **`ApplicationFrameTraceDemo.java`**, bắt đầu từ dòng 1 của code block; không thêm dòng trống trước khai báo class nếu muốn số dòng trong output khớp:

~~~java
public class ApplicationFrameTraceDemo {
    static void parse(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    static void load() {
        parse(" ");
    }

    static void endpoint() {
        load();
    }

    public static void main(String[] args) {
        try {
            endpoint();
        } catch (IllegalArgumentException error) {
            System.out.println(error);
            for (StackTraceElement frame : error.getStackTrace()) {
                if (frame.getClassName().equals(ApplicationFrameTraceDemo.class.getName())) {
                    System.out.printf("%s(%s:%d)%n",
                            frame.getMethodName(), frame.getFileName(), frame.getLineNumber());
                }
            }
        }
    }
}
~~~

Từ thư mục chứa file, dùng JDK 17 trở lên:

~~~bash
javac --release 17 ApplicationFrameTraceDemo.java
java ApplicationFrameTraceDemo
~~~

Stdout chính xác của phiên bản source ở trên khi biên dịch với metadata dòng mặc định của `javac`:

~~~text
java.lang.IllegalArgumentException: name must not be blank
parse(ApplicationFrameTraceDemo.java:4)
load(ApplicationFrameTraceDemo.java:9)
endpoint(ApplicationFrameTraceDemo.java:13)
main(ApplicationFrameTraceDemo.java:18)
~~~

**Đối chiếu mã nguồn:**
- Dòng **4** là `throw new IllegalArgumentException(...)` trong `parse`. Đây là vị trí gây ra failure trong ví dụ.
- Dòng **9** là `parse(" ")` ở `load`, nơi đưa một chuỗi chỉ có khoảng trắng vào validation.
- Dòng **13** là `load()` trong `endpoint`.
- Dòng **18** là `endpoint()` bên trong `try` của `main`.

Số dòng phản ánh file được biên dịch, không phải số dòng của trang tài liệu hay cách trình bày được bọc thêm trong IDE. Nếu copy chương trình mà thêm import/comment/dòng trống ở phía trên, số dòng sẽ thay đổi tương ứng. Điều đó là chính xác; hãy mở nguồn **đúng trạng thái đã biên dịch**, không cố giữ số dòng của ví dụ bằng mọi giá.

Trong source này, mọi frame do ta in đều thuộc class `ApplicationFrameTraceDemo`. Trong ứng dụng thật có nhiều class trong cùng namespace, có thể lọc theo một hoặc nhiều prefix package, nhưng nên tránh chỉ lọc mỗi một class để không bỏ mất frame liên quan.

### 5.1. Từ trace sang hành động sửa

Chỉ nhìn vào dòng 4 thấy validation ném exception; đó là **hành vi đúng** nếu hàm yêu cầu tên không được rỗng. Nơi sửa có thể là:
- `load` truyền chuỗi `" "` không hợp lệ;
- một controller không kiểm tra/chuẩn hóa đầu vào;
- hoặc tài liệu API chưa nói rõ quy tắc giá trị hợp lệ.

Không nên xóa validation chỉ để hết stack trace. Cần hiểu invariant và đường dữ liệu qua các frame `load`, `endpoint`. Đây là điểm phân biệt **xác định chỗ exception xuất hiện** với **xác định nguyên nhân và sửa đúng lỗi**.

## 6. `-g:none`: class chạy được nhưng không còn thông tin dòng nguồn

Dùng chính file `ApplicationFrameTraceDemo.java` ở trên, biên dịch lại với:

~~~bash
javac --release 17 -g:none ApplicationFrameTraceDemo.java
java ApplicationFrameTraceDemo
~~~

Stdout từ môi trường Java 17 với chương trình này:

~~~text
java.lang.IllegalArgumentException: name must not be blank
parse(null:-1)
load(null:-1)
endpoint(null:-1)
main(null:-1)
~~~

Khi tắt thông tin debug bằng `-g:none`, phương thức `getFileName()` có thể trả `null` và `getLineNumber()` trả một giá trị âm (trong thí nghiệm này là `-1`). JVM vẫn biết tên class và method, nhưng **không thể đối chiếu trực tiếp số dòng** từ những frame đó.

Các tùy chọn cần biết:

| Tùy chọn biên dịch | Ý nghĩa chính |
| --- | --- |
| `javac Foo.java` | Mặc định thường có thông tin source và line cho stack trace |
| `javac -g Foo.java` | Sinh tất cả loại debug information được hỗ trợ, gồm line/source/local variables |
| `javac -g:lines,source Foo.java` | Yêu cầu metadata số dòng và file nguồn |
| `javac -g:none Foo.java` | Không sinh debug information |
| `javac --release 17 Foo.java` | Biên dịch theo API/target tương thích Java 17; không tự đảm bảo source hiện tại đúng với bản deploy |

Chỉ chứa tên class/method vẫn có thể hữu ích. Bạn có thể dùng `javap` để xem bytecode/line table nếu class có metadata; nếu artifact đã bị strip thông tin dòng thì không có lệnh thần kỳ nào khôi phục đúng line table từ một class chỉ còn tên method. Phải xác định artifact source/build hoặc tái hiện ở bản có debug info.

### 6.1. `javap -l` và `javap -c -l`

Với class được biên dịch trong thư mục hiện tại:

~~~bash
javap -l ApplicationFrameTraceDemo
javap -c -l ApplicationFrameTraceDemo
~~~

`javap -l` liệt kê line number table/local variable table khi có; `-c` giúp đối chiếu bytecode với code location. Output phụ thuộc phiên bản biên dịch và bytecode cụ thể, nên không có một stdout cố định cho mọi class ở đây.

Với jar, chỉ định đúng classpath và tên class fully-qualified:

~~~bash
javap -classpath path/to/app.jar -c -l com.example.app.Service
~~~

Lệnh này chỉ là ví dụ cấu trúc đường dẫn; đổi `path/to/app.jar` và tên class theo artifact thật. Nếu class do build/repackaging khác cung cấp, dùng đúng jar/image được runtime nạp chứ không dùng một thư viện khác có cùng FQCN.

Thông tin line table cho biết ánh xạ **bytecode offset → source line**; nó không lưu toàn bộ mã nguồn để bạn xem nội dung câu lệnh. Muốn biết code ở dòng 123, vẫn cần file source đúng phiên bản.

## 7. `StackTraceElement`: đọc frame bằng API thay vì phân tích chuỗi

Trong Java, `Throwable.getStackTrace()` trả mảng `StackTraceElement[]`. Mỗi phần tử cung cấp các trường đáng chú ý:

| Phương thức | Giá trị | Lưu ý |
| --- | --- | --- |
| `getClassName()` | Tên class khai báo frame | Có thể chứa `$` với nested/anonymous/generated class |
| `getMethodName()` | Tên phương thức | Có thể là `<init>`, `<clinit>`, `lambda$...` |
| `getFileName()` | Tên file nguồn hoặc `null` | Không phải đường dẫn tuyệt đối |
| `getLineNumber()` | Số dòng hoặc giá trị đặc biệt | Số âm không đại diện cho một dòng Java hợp lệ |
| `isNativeMethod()` | Frame có đại diện cho native method không | Không có một dòng Java tương ứng đơn giản |
| `getModuleName()` | Tên module nếu có | Có thể `null` |
| `getModuleVersion()` | Version module nếu có | Có thể `null` |
| `getClassLoaderName()` | Tên class loader nếu có | Có thể `null` |

Không nên parse chuỗi `"at "...` để tách file/dòng khi bạn **đang có `Throwable`**: dùng trực tiếp `StackTraceElement` chính xác hơn và không phụ thuộc khoảng trắng/prefix module. Khi chỉ có một file log text, bạn có thể dùng parser stacktrace phù hợp định dạng JVM, nhưng cần giữ nguyên cause/suppressed và các dòng `... N more`.

### 7.1. Chọn frame ứng dụng bằng predicate, không bằng vị trí cố định

Đây là **fragment minh họa**, có thể dùng bên trong một class đã import `java.util.Arrays`, `java.util.Optional` và có một `Throwable failure`:

~~~java
Optional<StackTraceElement> firstAppFrame =
        Arrays.stream(failure.getStackTrace())
                .filter(frame ->
                        frame.getClassName().startsWith("com.example.app."))
                .findFirst();
~~~

`firstAppFrame` chứa frame gần điểm exception nhất trong namespace đó, nếu có. Nhưng predicate này chỉ là **gợi ý lọc**:
- Ứng dụng có thể có nhiều package root.
- Một thư viện nội bộ dùng cùng prefix có thể không thuộc module chịu trách nhiệm.
- Framework/generator có thể tạo class đặt trong package ứng dụng.
- Exception có thể bắt nguồn ở `cause` khác, không phải `failure` ngoài cùng.

Cần xem đầy đủ trace, chọn đúng cause và module theo thông tin triển khai. Đừng xem `findFirst()` là thuật toán tự động xác định bug.

### 7.2. Lặp qua nguyên nhân, nhưng đừng viết vòng lặp không phòng vệ

Một exception có thể có nhiều lớp bao:

~~~text
CompletionException
    -> IllegalStateException
        -> IOException
~~~

Muốn xác định nguyên nhân cuối có thể đi theo `getCause()`, nhưng đừng quên:
- Một wrapper có thể mang ngữ cảnh nghiệp vụ quan trọng dù cause sâu là lỗi I/O.
- Không phải mọi `Throwable` bắt buộc có cause.
- Có thể có exception cố ý không lưu writable stack trace.
- Nếu code bên ngoài tạo chain bất thường, thuật toán tự động nên giới hạn sâu/chống lặp theo identity.

Fragment đơn giản:

~~~java
Throwable cursor = failure;
while (cursor.getCause() != null) {
    cursor = cursor.getCause();
}
System.out.println(cursor.getClass().getName());
~~~

Đây là fragment giả định `failure` đã tồn tại; nó mô tả trường hợp cause chain thông thường không có vòng. Khi làm công cụ phân tích log tổng quát, dùng một tập đối tượng đã duyệt theo **identity** hoặc giới hạn độ sâu để chống chain bất thường, thay vì giả định mọi đối tượng Throwable đều đến từ code chuẩn.

## 8. `Caused by`: frame nào là vị trí bọc, frame nào là lỗi gốc?

Ví dụ:

~~~text
java.lang.IllegalStateException: cannot start service
    at com.example.app.Bootstrap.start(Bootstrap.java:68)
    at com.example.app.Main.main(Main.java:21)
Caused by: java.io.IOException: config unreadable
    at com.example.app.ConfigReader.load(ConfigReader.java:43)
    at com.example.app.Bootstrap.start(Bootstrap.java:64)
    ... 1 more
~~~

Đây là trace minh họa. Phần **ngoài cùng** cho thấy `Bootstrap.start:68` bọc lỗi. `Caused by` cho thấy `ConfigReader.load:43` là nơi exception gốc được ghi nhận. Nếu chỉ đọc `Bootstrap.start:68` rồi bỏ qua cause, bạn dễ sửa nhầm tầng.

Cách đọc đúng:
1. Xác định exception ngoài cùng và tại sao nó được tạo: ví dụ `IllegalStateException("cannot start service")`.
2. Đi vào `Caused by` và xét loại lỗi gốc: `IOException`.
3. Tìm frame ứng dụng trong **trace của cause**.
4. Quay lại frame của wrapper để hiểu exception được chuyển đổi ở ranh giới nào và thông tin ngữ cảnh được thêm vào.
5. Dựa trên cả hai khối để ra quyết định: sửa đường dẫn/configuration, cải thiện message, xử lý retry hay báo lỗi.

### 8.1. `... N more` không phải N frame bị mất ngẫu nhiên

Khi `Throwable.printStackTrace()` in nhiều exception có chung phần cuối của stack, Java có thể rút gọn phần frame chung thành `... N more`. Đây là **ký pháp nén các frame trùng với stack của exception bao quanh**, không phải số dòng source bị ẩn, cũng không phải N exception chưa đọc.

Nếu cần xem đầy đủ object stack của từng cause trong code, `cause.getStackTrace()` vẫn cho mảng của cause đó (trừ khi stack trace được tùy biến/không ghi). Khi chỉ có log text rút gọn, hãy ghép các frame chung theo vị trí thích hợp hoặc dùng IDE/parser hỗ trợ. Không kết luận đường đi giữa hai lớp bị đứt chỉ vì xuất hiện `... 1 more`.

### 8.2. `throw e` và `throw new ...(..., e)` khác nhau

~~~java
try {
    callRepository();
} catch (IOException e) {
    throw e;
}
~~~

Đây là fragment giả định phương thức bao quanh cho phép `throws IOException`. Nó **ném lại cùng object exception** `e`; thông tin stack trace thường vẫn phản ánh vị trí nguồn đã thu thập, không được thay bằng một stack hoàn toàn mới chỉ vì rethrow.

Ngược lại:

~~~java
try {
    callRepository();
} catch (IOException e) {
    throw new IllegalStateException("loading report failed", e);
}
~~~

Đây là fragment; exception **mới** có stack riêng tại nơi bọc, và `e` trở thành cause với trace riêng. Cách bọc phù hợp khi muốn chuyển exception qua một ranh giới API, nhưng cần giữ `cause`. Nếu chỉ viết `throw new IllegalStateException(e.getMessage())`, bạn đã làm mất đường cause tự nhiên và khiến việc tìm frame gốc khó hơn.

## 9. `Suppressed`: lỗi khi cleanup cũng có stack riêng

Với `try-with-resources`, nếu body ném exception A và `close()` cũng ném exception B, B thường được đính kèm vào A dưới dạng **suppressed exception**, thay vì thay thế A làm exception chính.

Log có thể có:

~~~text
java.io.IOException: read failed
    at com.example.app.ConfigReader.read(ConfigReader.java:...)
    Suppressed: java.io.IOException: close failed
        at com.example.app.Resource.close(Resource.java:...)
    ...
~~~

Dấu `...` trong ví dụ là ký hiệu minh họa; khi chạy thật sẽ có file/line hoặc thông tin stack cụ thể.

Không coi `Suppressed` là vô nghĩa. Lỗi cleanup có thể báo mất khả năng flush dữ liệu, connection hỏng, khóa chưa được giải phóng hoặc một vấn đề hạ tầng quan trọng. Nhưng thường cần điều tra **lỗi chính A trước**, sau đó đánh giá B là hậu quả hay vấn đề độc lập.

### 9.1. Ví dụ Java 17 hoàn chỉnh cho cause và suppressed

Lưu file **`CauseAndSuppressedDemo.java`** đúng nội dung sau, bắt đầu tại dòng 1:

~~~java
import java.io.IOException;

public class CauseAndSuppressedDemo {
    static final class FailingResource implements AutoCloseable {
        @Override public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void readConfig() throws IOException {
        try (FailingResource ignored = new FailingResource()) {
            throw new IOException("read failed");
        }
    }

    static void start() {
        try {
            readConfig();
        } catch (IOException cause) {
            throw new IllegalStateException("startup failed", cause);
        }
    }

    public static void main(String[] args) {
        try {
            start();
        } catch (IllegalStateException outer) {
            Throwable cause = outer.getCause();
            System.out.println("outer: " + outer.getMessage());
            System.out.println("cause: " + cause.getMessage());
            System.out.println("suppressed: "
                    + cause.getSuppressed()[0].getMessage());
            StackTraceElement first = cause.getStackTrace()[0];
            System.out.println("origin: " + first.getMethodName()
                    + "(" + first.getFileName() + ":" + first.getLineNumber() + ")");
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 CauseAndSuppressedDemo.java
java CauseAndSuppressedDemo
~~~

Stdout chính xác:

~~~text
outer: startup failed
cause: read failed
suppressed: close failed
origin: readConfig(CauseAndSuppressedDemo.java:12)
~~~

**Diễn giải:**

- Exception ngoài cùng là `IllegalStateException("startup failed")`, được tạo ở dòng **20**, trong `start()`.
- `getCause()` trả `IOException("read failed")`, được tạo/ném ở dòng **12**, trong `readConfig()`.
- Khi rời try-with-resources, `FailingResource.close()` lại ném `IOException("close failed")` ở dòng **6**. Vì body đã ném lỗi, lỗi đóng này được gắn vào `cause.getSuppressed()`.
- Vì code lấy frame đầu từ **cause**, giá trị `origin` phản ánh đúng `readConfig(...:12)` thay vì dòng bọc lỗi ở `start`.

Chương trình giả định có ít nhất một suppressed exception để tập trung vào cơ chế đã chủ động tạo. Trong thư viện tổng quát, phải kiểm tra độ dài mảng `getSuppressed()` trước khi truy cập chỉ số 0; nhiều exception bình thường không có lỗi suppressed nào.

### 9.2. Không áp quy tắc suppressed tùy tiện sang mọi callback

`try-with-resources` có ngữ nghĩa suppressed được Java quy định. Nhưng việc hai callback bất đồng bộ cùng ném exception, hoặc `whenComplete` action ném khi source đã thất bại, **không tự bảo đảm** callback exception xuất hiện trong `getSuppressed()` của lỗi gốc. Chỉ suy luận suppressed khi API/logic thực tế cam kết như vậy. Muốn ghi nhận hai lỗi độc lập, cần kênh quan sát hoặc kết quả tổng hợp được thiết kế trước.

## 10. Đối chiếu đúng dòng nguồn cần đúng phiên bản artifact

Giả sử log production nói:

~~~text
at com.example.billing.InvoiceService.issue(InvoiceService.java:142)
~~~

Nếu bạn mở source hiện tại và dòng 142 chỉ còn là một comment, không nên kết luận log sai. Có thể:
- Production chạy jar/image từ commit cũ.
- Nhánh đang mở đã thêm/xóa các dòng trước đó.
- Build đang dùng generated source hoặc annotation processing.
- Dependency có hai phiên bản hoặc nhiều copy trên classpath.
- Shadow/shading/obfuscation đã đổi tên class hoặc bố trí bytecode.
- Mã source được compile với tùy chọn strip debug thông tin.
- Một số class được biến đổi/instrument khi load.

**Số dòng trong stack trace tương ứng với class file đang chạy**, không tự động trỏ tới nhánh Git mới nhất. Quy trình đối chiếu:

1. Xác định phiên bản ứng dụng thực tế qua build ID, git commit, container image digest hoặc release artifact.
2. Kiểm tra tên class, module, jar chứa class mà runtime nạp.
3. Mở source tương ứng **đúng bản build đó**; trong monorepo, xác định đúng module.
4. Tới file/dòng được ghi trong trace; đọc khoảng 10–20 dòng xung quanh, không chỉ một dòng rời.
5. Đối chiếu thông tin line table từ class file nếu source và trace không khớp.
6. Nếu lỗi phụ thuộc concurrency/data, tái hiện cùng input/cấu hình/phiên bản dependency, không chỉ thay đổi code cho khớp vị trí dòng.

### 10.1. Tên file không đủ để xác định duy nhất source

Một repo có thể có nhiều file tên `Service.java` ở các module khác nhau. `StackTraceElement.getFileName()` thường chỉ trả tên file, không trả đường dẫn tuyệt đối. Cần ghép **tên class đầy đủ và package** để xác định file phù hợp.

Ví dụ tên `com.example.billing.internal.InvoiceService` thường tương ứng với một source path giống `com/example/billing/internal/InvoiceService.java` trong module sở hữu class. Nhưng class có thể được sinh hoặc biến đổi; đó là quy tắc tổ chức thông thường, không phải một ánh xạ tuyệt đối với mọi compiler.

Với nested class:
- `Outer$Inner` có thể nằm trong cùng `Outer.java`.
- `Outer$1` có thể là anonymous class hoặc class được compiler tạo.
- `Outer$$Lambda$...`/`lambda$method$0` có thể liên quan lambda, không có một file tên đúng bằng synthetic class.

Do đó không cố tìm một file tên `Outer$Inner.java` chỉ vì frame in ra tên binary class như vậy.

### 10.2. Trùng class trên classpath

Hai jar có cùng tên class có thể khiến chương trình nạp phiên bản khác bản bạn đang mở. Để điều tra code runtime, có thể truy vấn `ProtectionDomain`/`CodeSource` của class đang chạy trong môi trường phù hợp. Ví dụ fragment:

~~~java
var source = InvoiceService.class.getProtectionDomain().getCodeSource();
System.out.println(source == null ? "unknown" : source.getLocation());
~~~

Fragment giả định `InvoiceService` có sẵn; `CodeSource` có thể `null` đối với một số loại class loader/module, và vị trí trả về không phải bằng chứng đầy đủ của container/deployment provenance. Tuy nhiên nó có thể giúp tìm jar class được nạp. Không in đường dẫn máy chủ/URL nhạy cảm vào log công khai; dùng kênh chẩn đoán nội bộ.

### 10.3. Jar và class file có thể kiểm tra bằng công cụ JDK

Tìm tên class trong một jar bằng:

~~~bash
jar tf path/to/application.jar
~~~

Mở line number table của class:

~~~bash
javap -classpath path/to/application.jar -l com.example.billing.InvoiceService
~~~

Các dòng trên là **lệnh tham khảo**, không phải lệnh chạy được nguyên trạng vì tên file và class chỉ minh họa. `jar tf` liệt kê entries; `javap -l` đọc thông tin line/local variables từ class nếu có. Với executable fat jar hoặc custom class loader, classpath và layout có thể khác jar thông thường; cần xác minh class file thực thay vì áp câu lệnh một cách máy móc.

Để truy đến bytecode cụ thể, dùng `javap -c -l`. Vẫn cần hiểu lời gọi/invariant ở source; bytecode và line table không nói trực tiếp liệu dữ liệu đầu vào có đúng hay không.

## 11. Không nhầm stack trace của logger với trace của exception

Trong logging, ba trường hợp cần tách:

1. **Log exception object:** `logger.error("failed", e)` với logging framework hỗ trợ tham số Throwable thường in stack của `e`.
2. **Log chỉ message:** `logger.error("failed: {}", e.getMessage())` có thể chỉ in chuỗi, không có frames.
3. **Gọi `Thread.currentThread().getStackTrace()` hoặc `StackWalker` tại nơi log:** tạo ảnh chụp call stack của **thread hiện tại ở thời điểm log**, không nhất thiết là trace nơi exception ban đầu được tạo.

Ví dụ `e.printStackTrace()` in exception theo handler đang có; với chương trình thực tế, thường nên truyền `e` vào hệ thống logging có cấu trúc để giữ context/request ID thay vì in tự do vào stderr từ nhiều thread. Nhưng ngay cả logger tốt cũng không sửa được một exception đã mất cause do rethrow sai.

### 11.1. `Throwable.getStackTrace()` khác `StackWalker`

`Throwable.getStackTrace()` trả những frame gắn với một đối tượng Throwable. `StackWalker`, giới thiệu trong Java 9, có thể quan sát call stack của **thread hiện tại** theo API `walk`/`forEach`. Nó không cho bạn đọc ngược call stack đã biến mất của worker khác lúc trước chỉ vì bạn có một object Future.

Trong code async, khi `Future.get()` ném `ExecutionException` trên thread nhận kết quả, frame của `ExecutionException` và frame của cause có thể thuộc **hai giai đoạn/lớp thực thi khác nhau**. Muốn tìm worker làm gì, hãy xem `getCause().getStackTrace()`; không xem stack hiện tại của thread đang catch rồi kết luận worker ném lỗi ở dòng `get()`.

## 12. Lambda, reflection, proxy và framework làm frame khó đọc hơn

Không phải mọi frame ứng dụng đều in ra một tên method “đẹp”. Trong Java 17 có thể gặp:

~~~text
at com.example.app.TaskService.lambda$execute$0(TaskService.java:74)
at java.base/java.lang.reflect.Method.invoke(Method.java:...)
at jdk.proxy1/jdk.proxy1.$Proxy0.fetch(Unknown Source)
at com.example.app.TaskService.execute(TaskService.java:61)
~~~

Đây là **trace minh họa**, vị trí/dòng và tên proxy phụ thuộc runtime/compiler. Các kiểu frame thường gặp:

| Dấu hiệu | Cách đọc |
| --- | --- |
| `lambda$someMethod$0` | Method tổng hợp cho phần thân lambda; mở method nguồn tương ứng và tới dòng liên quan |
| `<init>` | Constructor |
| `<clinit>` | Static initializer |
| `$Proxy...`, `$$Enhancer...`, Byte Buddy/CGLIB | Class được sinh/ủy quyền; tìm callback/target class ứng dụng |
| `Method.invoke` hoặc `Constructor.newInstance` | Reflection; xem cause/target method hoặc exception bọc |
| `CompletableFuture$...` / `ForkJoinPool...` | Hạ tầng async; tìm lambda/callable ứng dụng và cause |
| `Thread.run` / executor worker | Điểm vào thread; thường không phải nơi cần sửa nghiệp vụ |

Một frame `lambda$run$0(MyService.java:74)` vẫn có thể trỏ chính xác đến dòng bên trong lambda của `MyService.java`. Không cần tìm class `lambda$run$0.java`.

### 12.1. Async không có một “stack xuyên luồng” thông thường

Khi công việc chuyển từ thread A sang executor B rồi callback C, call stack ở A không còn là phần “phía dưới” trong stack ở B. Vì thế trace của worker không nhất thiết chứa controller đã gửi task. Nếu thiếu mã tác vụ, request ID, trace ID hoặc context truyền xuyên async, bạn có thể không biết worker gắn với request nào.

Cách chẩn đoán:
- Gắn `operationId`/`correlationId` ngay khi gửi task.
- Ghi nhận điểm submit và mốc task bắt đầu/kết thúc khi cần.
- Thu lỗi qua `Future.get()`, `CompletableFuture.join()` và phân tích `getCause()`.
- Dùng tracing/instrumentation có hỗ trợ async context nếu hệ thống cần quan hệ xuyên nhiều thread/service.
- Đọc frame ứng dụng trong **trace thực sự của exception**, không suy ra caller không xuất hiện là không có liên quan.

Đừng gắn stack trace từ hai thread khác nhau vào nhau rồi coi là một call stack liên tục. Có thể kết hợp hai sự kiện bằng correlation ID, nhưng đó là **một quan hệ nghiệp vụ/điều phối**, không phải quan hệ call stack Java tự nhiên.

### 12.2. `CompletionException` và `ExecutionException`

Ví dụ một task do `CompletableFuture.supplyAsync` tạo ra ném `IllegalStateException`. Caller gọi `join()` có thể nhận `CompletionException`. Caller gọi `Future.get()` có thể nhận `ExecutionException`. Ở đây nên:
1. Ghi nhận wrapper để hiểu tầng nào quan sát lỗi.
2. Đọc `getCause()` để tìm lỗi worker.
3. Tìm frame ứng dụng ở stack của cause.
4. Không chỉ sửa dòng `future.join()` hoặc `future.get()` vì nó xuất hiện trong stack ngoài cùng.

Lưu ý timeout hoặc cancellation cũng có các kiểu exception riêng và không tự chứng minh công việc bên dưới đã dừng; việc thu được stack trace không thay thế quản lý trạng thái và side effect.

## 13. Dòng nguồn chỉ là điểm bắt đầu: phân tích ngữ cảnh

Sau khi tìm frame `OrderService.java:47`, vẫn phải đọc đủ ngữ cảnh:

- Đó là một lời gọi nào? Tham số được tính ở đâu? Giá trị null/rỗng/âm có thể lọt qua tầng trước không?
- Có branch riêng cho dữ liệu lỗi mà test chưa bao phủ?
- Dòng đó nằm trong vòng lặp hay stream? Phần tử gây lỗi nào?
- Lỗi đến từ một constructor/static initializer dùng lúc class load lần đầu?
- Có transaction đang mở? Side effect đã commit chưa?
- Có worker khác đang thay đổi cùng state không?
- Có lớp wrapper chỉ thêm message hoặc đổi exception type mà không giữ cause?
- Dòng đó tồn tại ở **release nào**, môi trường nào và build flags nào?

Một ví dụ thường gặp:

~~~java
return orders.get(index).customer().name().trim();
~~~

Đây là fragment minh họa. Một `NullPointerException` cùng chỉ về một dòng có nhiều thao tác dereference. Số dòng riêng không nói chính xác `orders`, `orders.get(index)`, `customer()` hay `name()` là null. Java hiện đại có thể phát sinh **helpful NullPointerException message** khi điều kiện phù hợp, nhưng không nên chỉ dựa vào nó. Đọc biểu thức, đưa dữ liệu vào các biến có tên nếu cần và thêm test với trạng thái cụ thể.

Một dòng gọi:

~~~java
repository.save(order);
~~~

có thể gây lỗi trong JDBC/transaction/framework. Dòng được chỉ trong service cho biết **đường gọi đến storage**, không nhất thiết code service là nơi nguyên nhân bắt đầu. Xem cause SQL exception, constraint violation, connection state và dữ liệu đầu vào để quyết định sửa.

## 14. Lỗi thường gặp khi đối chiếu file và số dòng

### 14.1. Mở nhánh mới nhất thay vì bản đang chạy

Nếu log thuộc image build cũ, line 87 của nhánh hiện tại có thể không liên quan. **Xác minh commit/tag/build ID trước khi sửa.** Đây là một trong những nguyên nhân phổ biến khiến kỹ sư “không tìm thấy dòng gây lỗi”.

### 14.2. Tìm file bằng tên mà không xét package/module

`Service.java` có thể tồn tại nhiều nơi. Dùng FQCN và module/jar của class, không chỉ tên `FileName.java`.

### 14.3. Lấy frame ứng dụng đầu tiên của exception ngoài cùng rồi bỏ cause

Exception ngoài cùng có thể là wrapper. Đọc `Caused by`, nhất là với I/O, JDBC, reflection và CompletableFuture.

### 14.4. Xem `... N more` là những dòng bị mất

Nó mô tả suffix frames trùng giữa các trace, không phải ngẫu nhiên stack bị cắt. Tuy nhiên logger/thư viện cũng có thể **truncate** stacktrace thật sự do cấu hình; cần phân biệt hai hiện tượng.

### 14.5. Cho rằng mỗi frame phải có tên file và số dòng

`Unknown Source`, `Native Method`, class sinh động, class biên dịch `-g:none` hoặc source map đặc biệt có thể thiếu số dòng. Không bịa line number; tìm bằng method, bytecode, source/build và reproduction.

### 14.6. “Sửa” frame thư viện đầu tiên

Có thể lỗi do ứng dụng truyền tham số sai; đọc frame ứng dụng gần nhất và invariant đầu vào. Nhưng cũng đừng loại trừ lỗi thư viện nếu bằng chứng chỉ vào internal bug.

### 14.7. Chỉ tìm đoạn `throw`, không tìm nơi tạo exception

Một Throwable có thể được tạo trước khi ném hoặc bị dùng lại; trace gắn với thông tin thu thập stack của object, không nhất thiết là dòng `throw e` gần nhất. Tìm cách exception được tạo và rethrow.

### 14.8. Log `e.getMessage()` thay vì cả throwable

Message không chứa stack trace, cause và suppressed. Khi chẩn đoán lỗi không rõ, log throwable trong kênh nội bộ đáng tin, đồng thời kiểm soát thông tin nhạy cảm và khối lượng log.

### 14.9. Bỏ exception trong `Suppressed`

Lỗi cleanup có thể cung cấp manh mối thứ hai rất quan trọng; kiểm tra `getSuppressed()` khi có try-with-resources.

### 14.10. Coi frame của thread đang catch là frame của worker

Exception qua Future/CompletableFuture có wrapper tại thread caller; lỗi gốc có trace của thread thực thi task. Giữ hai lớp chứng cứ tách biệt.

## 15. Kiểm thử và công cụ để chứng minh ánh xạ

Một chu trình QA nhỏ, lặp được:

1. Viết một đoạn tạo lỗi có kiểm soát trong một file Java nhỏ.
2. Biên dịch bằng đúng mức Java cần hỗ trợ, ví dụ `javac --release 17`.
3. Gọi `Throwable.getStackTrace()` và in `getClassName()`, `getMethodName()`, `getFileName()`, `getLineNumber()`.
4. Mở file source đã biên dịch, đếm dòng và xác nhận các call sites.
5. Biên dịch lại cùng file với `-g:none`; quan sát việc mất tên file/line nhưng vẫn có class/method.
6. Nếu cần, dùng `javap -c -l` để đọc line number table tương ứng.
7. Thêm wrapper `throw new ...(..., e)`, rồi xác nhận trace ngoài và trace cause trỏ tới hai vị trí khác nhau.
8. Thêm try-with-resources mà `close()` ném để xác nhận suppressed exception.
9. Khi làm trên hệ thống thật, lặp lại bằng **artifact production có thể kiểm chứng**, không dùng file Java test để suy ra mọi môi trường đều giống nhau.

### 15.1. Ví dụ với JUnit 5

Đây là **fragment test**, giả định JUnit 5 đã được cấu hình, class `ApplicationFrameTraceDemo` được đặt trong phạm vi test, và có static import `assertThrows`, `assertEquals`, `assertTrue`:

~~~java
IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> ApplicationFrameTraceDemo.parse(" "));

StackTraceElement top = ex.getStackTrace()[0];
assertEquals("parse", top.getMethodName());
assertEquals("ApplicationFrameTraceDemo.java", top.getFileName());
assertTrue(top.getLineNumber() > 0);
~~~

Thường không nên hardcode số dòng 4 trong test unit nghiệp vụ, vì một comment được thêm ở đầu file sẽ làm test vỡ dù chương trình vẫn đúng. Khi chủ đích là **bài tập kiểm chứng line table**, việc đối chiếu với số dòng thật là hợp lệ; nhưng với unit test sản phẩm, nên assert loại exception/message/invariant, chỉ kiểm tra line number dương nếu muốn đảm bảo debug metadata.

### 15.2. Khi log thiếu dòng source trong production

Bảng kiểm tra:

| Dấu hiệu | Bước kiểm tra hợp lý |
| --- | --- |
| Frame có `(Unknown Source)` | Kiểm tra class file debug metadata và compiler/proguard settings |
| Frame ghi file/dòng nhưng không khớp repo | Kiểm tra build ID, commit, generated source và jar đang chạy |
| Frame thuộc `lambda$...` | Mở method bao lambda và dòng source tương ứng |
| Frame thuộc `$Proxy...` | Xác định target method, interceptor và cause |
| Chỉ thấy `CompletionException`/`ExecutionException` | Mở `getCause()`, xem trace của task gốc |
| Trace bị cắt bởi cấu hình logger | Điều chỉnh policy log stack trace có giới hạn và bảo vệ dữ liệu |
| Nhiều exception trùng tên message | Dựa vào timestamp, correlation ID, class/method/cause, không chỉ text message |
| Lỗi chỉ xuất hiện dưới tải | Thu thread/trace context, input, trạng thái chia sẻ; tái hiện race có điều phối |
| Số dòng âm | Kiểm tra `-g` và line table; đừng đoán line từ tên method |

## 16. Logging an toàn và đủ ngữ cảnh

Trong ứng dụng production, nên ghi đủ dữ liệu chẩn đoán nhưng tránh lộ thông tin nhạy cảm:

- **Exception type + cause chain**: biết lỗi nào thật sự phát sinh và nơi bị bọc.
- **Stack frame ứng dụng đáng chú ý**: class, method, file, line; nhưng giữ trace đầy đủ trong kênh nội bộ khi cần.
- **Correlation ID hoặc operation ID**: nối log trên các thread/tác vụ.
- **Build/release ID**: xác định source chính xác.
- **Thread name và module/service**: biết môi trường chạy.
- **Các thuộc tính nghiệp vụ an toàn**: trạng thái, loại thao tác, mã lỗi ổn định; tránh raw token, mật khẩu, dữ liệu cá nhân, SQL chứa thông tin nhạy cảm.

Tránh hai cực đoan: chỉ log `e.toString()` khiến thiếu chứng cứ, hoặc log toàn bộ request payload/token khiến rò rỉ thông tin. Dùng công cụ logging có structured fields, cấu hình level và policy retention phù hợp; không tự tạo một log line cực dài chứa tất cả dữ liệu.

### 16.1. Log một lần tại ranh giới phù hợp

Một exception có thể bị log ở repository, service, controller rồi lại bị log ở job handler, tạo nhiều stacktrace giống nhau. Thông thường nên **giữ cause khi chuyển tầng** và chọn điểm logging sở hữu việc báo lỗi, thay vì log-error rồi rethrow cùng exception ở mọi nơi. Tuy nhiên vẫn có tình huống mỗi tầng có metric hoặc audit riêng; khi đó thông tin nên được phân biệt rõ để không nhân số lượng sự cố thực tế.

Nếu một phương thức catch để phục hồi thành công, có thể ghi metric recovery phù hợp thay vì phát log lỗi mức cao như thể request cuối đã thất bại. Một stack trace có ý nghĩa khi nó được liên hệ với **trạng thái xử lý cuối cùng**.

## 17. Cây quyết định từ một frame đến hướng điều tra

~~~text
Có stack trace từ Java
|
+-- Có Caused by / Suppressed?
|     +-- Có -> đọc các trace riêng và xác định lỗi chính
|     +-- Không -> bắt đầu từ throwable hiện có
|
+-- Frame trên cùng thuộc ứng dụng?
|     +-- Có -> đối chiếu file:line, rồi xem caller phía dưới
|     +-- Không -> tìm frame ứng dụng gần đó và kiểm tra input/cause
|
+-- Có số dòng hợp lệ?
|     +-- Có -> mở đúng phiên bản source, xem vùng xung quanh dòng
|     +-- Không -> kiểm tra debug info/class/jar/generated source
|
+-- Có chắc source đúng artifact deploy?
|     +-- Không -> xác minh release/build/commit/classloader
|     +-- Có -> đối chiếu biểu thức, invariant, trạng thái thực
|
+-- Có thể tái hiện?
      +-- Có -> viết test cụ thể và sửa nguyên nhân
      +-- Không -> bổ sung telemetry an toàn và chẩn đoán race/async
~~~

Cây quyết định là phương pháp định hướng, không phải một chương trình tự động suy ra bug. Mục tiêu là rút ngắn thời gian đi từ **triệu chứng trong log** đến **nguyên nhân có thể chứng minh bằng mã và dữ liệu**.

## 18. Checklist khi đọc stack trace Java

- [ ] Đã giữ stack trace đủ dòng, bao gồm `Caused by`, `Suppressed` và `... N more` nếu có.
- [ ] Đã phân biệt exception ngoài cùng, exception gốc và lỗi cleanup.
- [ ] Đã tìm frame ứng dụng **trong trace của cause phù hợp**, không chỉ ở trace ngoài.
- [ ] Đã đọc đúng `class.method(File.java:line)`, bao gồm module/class loader prefix khi có.
- [ ] Đã đối chiếu đúng package, module, jar/image và build/commit triển khai.
- [ ] Đã xem source quanh dòng và các caller, không chỉ chỉnh dòng đầu trace.
- [ ] Đã cân nhắc lambda, constructor, static initializer, generated proxy, reflection và async wrapper.
- [ ] Đã hiểu rằng `Unknown Source`/line âm thường là giới hạn thông tin, không phải số dòng để đoán.
- [ ] Đã xác minh debug metadata khi source line thiếu hoặc lệch; dùng `javap -l` nếu phù hợp.
- [ ] Đã giữ `cause` khi wrap exception và không làm mất chứng cứ bằng cách chỉ log message.
- [ ] Đã phân biệt trace của exception với stack snapshot tại nơi log.
- [ ] Đã kiểm tra trạng thái nghiệp vụ, dữ liệu đầu vào, side effect và concurrency trước khi kết luận nguyên nhân.
- [ ] Đã thêm test chứng minh nguyên nhân/sửa chữa, không viết test quá dễ vỡ vì hardcode line number của source đang phát triển.
- [ ] Đã bảo vệ dữ liệu nhạy cảm và kiểm soát log volume trong production.

### Bài tập thực hành

Giả sử một dịch vụ xuất hóa đơn ném `CompletionException` khi callback nhận dữ liệu bất đồng bộ thất bại. Trace có các frame từ `CompletableFuture`, một `InvoiceService.lambda$generate$0` và một cause `SQLException`.

1. Chỉ rõ vì sao không nên sửa dòng `future.join()` chỉ vì wrapper xuất hiện ở đó.
2. Tìm cause sâu nhất và frame ứng dụng gần điểm lỗi, sau đó truy ngược caller để xác định dữ liệu đầu vào.
3. Kiểm tra bản artifact đang deploy có đúng với source IDE không và dùng `javap -l` khi dòng lệch.
4. Nếu `SQLException` bị bọc trong `IllegalStateException` nhưng không giữ cause, giải thích phần chứng cứ nào mất đi.
5. Nếu task đã ghi file rồi mới thất bại, giải thích vì sao chẩn đoán được frame ném lỗi **không đồng nghĩa file đã rollback**.
6. Viết một test cố tình truyền dữ liệu sai đến `InvoiceService`, ghi nhận exception và xác minh loại lỗi, cause, mã nghiệp vụ; không dựa vào một số dòng cố định để test chức năng.

**Kết luận:** Frame ứng dụng giúp xác định **một vị trí trong đường thực thi**, nhưng chẩn đoán đúng cần đi cùng **cause/suppressed, ngữ cảnh caller và đúng bản nguồn đã biên dịch**. Đọc `File.java:line` là bước bắt đầu điều tra, không phải lời phán quyết rằng dòng đó luôn là bug cần sửa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-004">Thông điệp NullPointerException chi tiết trong JDK hiện đại</a>

<details>
<summary>Xem chi tiết</summary>

# Thông điệp NullPointerException chi tiết trong JDK hiện đại

## 1. Vấn đề: stack trace có thể chỉ đúng dòng nhưng chưa chỉ đúng biểu thức

`NullPointerException` (NPE) là một ngoại lệ unchecked xuất hiện khi chương trình cố sử dụng một tham chiếu `null` ở vị trí cần object hoặc array. Trước khi JDK có *Helpful NullPointerExceptions*, lập trình viên thường nhìn thấy thông tin kiểu:

```text
java.lang.NullPointerException
    at Example.main(Example.java:18)
```

Dòng 18 có thể chứa nhiều phép truy cập liên tiếp:

```java
String city = customer.address().city.trim();
```

Đây là **mảnh mã minh họa** bên trong một phương thức, với `customer`, `address()`, `city` đã được định nghĩa. Nếu dòng này ném NPE, stack trace chỉ cho biết vị trí nhưng chưa làm rõ `customer` là `null`, `address()` trả về `null`, hay `city` là `null`. Nhà phát triển phải đặt breakpoint, xem giá trị hoặc chia dòng để tìm biểu thức gây lỗi.

JDK hiện đại có khả năng sinh **thông điệp NPE chi tiết**, hay *Helpful NullPointerExceptions*, để mô tả **thao tác nào không thể thực hiện** và **tham chiếu con nào là null**. Ví dụ, nếu `address()` trả về `null`, thông điệp có thể có dạng:

```text
Cannot read field "city" because the return value of "Customer.address()" is null
```

Thông tin này giúp thu hẹp điểm chẩn đoán rất nhanh. Tuy vậy, nó **không khẳng định nguyên nhân nghiệp vụ** vì sao địa chỉ bị thiếu, cũng không tự sửa lỗi hoặc bảo đảm dữ liệu luôn hợp lệ.

## 2. Phiên bản Java và cờ bật/tắt

Khả năng này được đưa vào OpenJDK theo **JEP 358 — Helpful NullPointerExceptions**, lần đầu có mặt trong **JDK 14**. Trong OpenJDK/HotSpot JDK 14, chi tiết mới mặc định **tắt** và có thể bật bằng:

```bash
java -XX:+ShowCodeDetailsInExceptionMessages -cp . HelpfulNpeDemo
```

Từ **JDK 15**, cờ `ShowCodeDetailsInExceptionMessages` được bật mặc định trên các bản HotSpot/OpenJDK thông dụng, bao gồm các JDK LTS như 17 và 21. Có thể chỉ định tường minh để bảo đảm ví dụ dùng đúng chế độ:

```bash
java -XX:+ShowCodeDetailsInExceptionMessages -cp . HelpfulNpeDemo
```

Nếu muốn so sánh hành vi khi không có thông điệp chi tiết, dùng:

```bash
java -XX:-ShowCodeDetailsInExceptionMessages -cp . HelpfulNpeDemo
```

**Dấu `+` bật, dấu `-` tắt.** Cờ nằm trong tham số của **lệnh chạy `java`**, không phải một annotation, không phải tham số của `javac` và không phải lệnh cần viết bên trong source code.

Các bản build khác nhà cung cấp JVM hoặc khác implementation có thể có khác biệt về thông điệp hay mức hỗ trợ. Nếu tính năng không hoạt động như mong đợi, trước hết xác nhận JDK/runtime thực tế:

```bash
java -version
javac -version
```

Có thể tra cứu bản thân tùy chọn trên một HotSpot VM hỗ trợ nó bằng lệnh:

```bash
java -XX:+PrintFlagsFinal -version
```

Lệnh cuối in nhiều JVM flags, trong đó có thể tìm `ShowCodeDetailsInExceptionMessages`. Đây là thao tác quan sát cấu hình, không sửa chương trình. Cờ và mặc định có thể thay đổi theo JVM, vì vậy hướng dẫn xử lý sự cố cần ghi rõ runtime kiểm thử.

## 3. Helpful NPE gồm những loại thông tin gì?

Thông điệp thường có hai phần:

1. **Hành động thất bại:** JVM không thể gọi một phương thức, đọc/ghi trường, truy cập array hay lấy chiều dài array vì receiver/reference là `null`.
2. **Biểu thức dẫn tới null:** tham chiếu nào hoặc giá trị trả về từ thao tác nào được xác định là `null` ngay trước bytecode gây lỗi.

Ví dụ các dạng thông điệp thường gặp:

| Biểu thức trong Java | Dạng chẩn đoán | Cách hiểu |
| --- | --- | --- |
| `obj.method()` khi `obj == null` | `Cannot invoke "..." because "obj" is null` | Không thể gọi instance method trên tham chiếu null |
| `obj.field` khi `obj == null` | `Cannot read field "field" because "obj" is null` | Không thể đọc trường từ object null |
| `obj.field = value` khi `obj == null` | `Cannot assign field "field" because "obj" is null` | Không thể gán trường vào object null |
| `values.length` khi `values == null` | `Cannot read the array length because "values" is null` | Array reference là null |
| `values[index]` khi `values == null` | `Cannot load from ... array because "values" is null` | Không thể đọc phần tử vì array là null |
| `values[index] = x` khi `values == null` | `Cannot store to ... array because "values" is null` | Không thể ghi phần tử vì array là null |
| `service.getProfile().getName()` khi `getProfile()` trả null | `Cannot invoke "..." because the return value of "..." is null` | Một phương thức trung gian trả null |

Bảng trên mô tả **mẫu thông điệp**, không cam kết mọi cách viết Java hay JVM sẽ dùng chính xác cùng chuỗi. Tên lớp, chữ ký phương thức, số biến cục bộ, cách compiler sinh bytecode và cách runtime trình bày có thể ảnh hưởng kết quả.

Một thông điệp như “return value of `getProfile()` is null” chỉ nói **kết quả trả về đã là null tại thời điểm dùng**, chưa kết luận `getProfile` có bug. Phương thức có thể được thiết kế cho phép trả `null`; việc dùng kết quả mà không kiểm tra mới là chỗ cần phân tích hợp đồng.

## 4. JVM biết biểu thức nào null bằng cách nào?

Cơ chế không yêu cầu JVM lưu bản đầy đủ của mã nguồn Java để phân tích trực tiếp từng dấu chấm. Trong implementation HotSpot, JVM dùng **bytecode** tại vị trí thao tác thất bại để suy ra:

- Lệnh bytecode nào đã yêu cầu tham chiếu object/array khác `null`;
- Thao tác đang cố thực hiện là đọc trường, ghi trường, gọi method hay truy cập array;
- Cách giá trị null đó được đưa lên operand stack, ví dụ đọc biến, lấy trường hoặc nhận return value từ một method.

Sau đó runtime tái dựng một mô tả hữu ích của **đường đi dữ liệu** trên bytecode. Vì vậy tính năng thường có thể nói rõ “return value of `Customer.address()` is null” ngay cả khi dòng Java có nhiều lời gọi liên tiếp.

Các chi tiết kỹ thuật cần phân biệt:

- JVM phân tích thông tin gắn với **bytecode đã ném lỗi**, không dùng một hệ thống suy luận nguyên nhân nghiệp vụ.
- Tên các method/field có thể lấy từ thông tin class và constant pool.
- Tên **biến local** có thể được sử dụng khi file `.class` có thông tin debug phù hợp; nếu không có, thông điệp có thể dùng biểu diễn dạng `<local1>` hoặc ký hiệu khác thay cho tên biến.
- Compiler có thể biến đổi biểu thức nguồn thành bytecode theo cách khác nhau. Vì thế thông điệp đủ để định vị lỗi nhưng không được xem là định dạng máy đọc bảo đảm ổn định giữa mọi JDK và mọi compiler.
- Thông điệp được tạo **theo nhu cầu** khi thông tin message được yêu cầu, chẳng hạn `getMessage()` hoặc ghi stack trace, để giảm công việc không cần thiết đối với exception không được in/đọc.

Đây là tính năng **chẩn đoán lúc chạy**, không phải trình kiểm tra `null` ở compile time hay hệ thống loại bỏ tất cả NPE. Nó không thể bảo đảm một property nào đó sẽ không trở thành null do concurrency hoặc dữ liệu từ bên ngoài.

## 5. Ví dụ Java 17+ hoàn chỉnh và kết quả đã kiểm chứng

Chương trình dưới đây tạo bốn tình huống:

1. Truy cập một field qua return value `null`;
2. Đọc `length` của array null;
3. Dùng `Objects.requireNonNull` với thông điệp chủ động;
4. Chủ động `throw new NullPointerException()` không có thông điệp.

Tạo file **`HelpfulNpeDemo.java`**:

```java
import java.util.Objects;

public class HelpfulNpeDemo {
    static final class Address {
        final String city;

        Address(String city) {
            this.city = city;
        }
    }

    static final class Customer {
        Address address() {
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            String city = new Customer().address().city;
            System.out.println(city);
        } catch (NullPointerException ex) {
            System.out.println("CHAIN: " + ex.getMessage());
        }

        try {
            String[] names = null;
            System.out.println(names.length);
        } catch (NullPointerException ex) {
            System.out.println("ARRAY: " + ex.getMessage());
        }

        try {
            Objects.requireNonNull(null, "email must not be null");
        } catch (NullPointerException ex) {
            System.out.println("REQUIRE: " + ex.getMessage());
        }

        try {
            throw new NullPointerException();
        } catch (NullPointerException ex) {
            System.out.println("EXPLICIT: " + ex.getMessage());
        }
    }
}
```

Biên dịch với debug information, rồi chạy bật tính năng:

```bash
javac -encoding UTF-8 -g HelpfulNpeDemo.java
java -XX:+ShowCodeDetailsInExceptionMessages HelpfulNpeDemo
```

**Standard output thực tế trên HotSpot/OpenJDK JDK 24 dùng để kiểm chứng ví dụ:**

```text
CHAIN: Cannot read field "city" because the return value of "HelpfulNpeDemo$Customer.address()" is null
ARRAY: Cannot read the array length because "names" is null
REQUIRE: email must not be null
EXPLICIT: null
```

Nếu tắt chi tiết:

```bash
java -XX:-ShowCodeDetailsInExceptionMessages HelpfulNpeDemo
```

**Standard output được kiểm chứng trong chế độ tắt:**

```text
CHAIN: null
ARRAY: null
REQUIRE: email must not be null
EXPLICIT: null
```

Kết quả trên minh họa hành vi tại runtime đã thử. Trong các JDK/JVM khác, **nội dung helpful message có thể khác nhẹ** về biểu diễn tên biến, chữ ký hoặc format; không nên khẳng định đây là giao thức text bất biến. Nguyên tắc đáng tin là loại NPE do JVM phát sinh được cung cấp thêm chi tiết khi tính năng hỗ trợ và được bật.

**Phân tích bốn tình huống:**

- `new Customer()` là non-null. `address()` trả `null`. Khi đọc `.city`, JVM xác định chính **return value của `address()`** là `null`. Vì vậy message không nói rằng `Customer` là null.
- `names` được gán `null`. Khi đọc `names.length`, lỗi xảy ra ở thao tác đọc chiều dài array; có thể hiện tên local `names` nhờ biên dịch với `-g`.
- `Objects.requireNonNull(null, "email must not be null")` chủ động tạo/ném NPE có thông điệp do ứng dụng xác định. Nó **không bị helpful message tự thay thế**.
- `throw new NullPointerException()` là NPE lập trình viên chủ động tạo mà **không truyền thông điệp**. Cơ chế bytecode dành cho lỗi dereference do JVM phát hiện không tự sinh giải thích cho lỗi này, nên `getMessage()` là `null` ở chương trình thử.

Ví dụ dùng `try/catch` ở từng case để tiếp tục demo và in kết quả. Trong ứng dụng thật, không nên tùy tiện bắt mọi NPE rồi tiếp tục như thể trạng thái ổn định; NPE thường cho thấy giả định `null` sai và cần được sửa tại nguồn hoặc ranh giới kiểm tra dữ liệu.

## 6. NPE do JVM tạo khác với NPE được ném rõ ràng

Một phân biệt quyết định là **ai tạo NPE và vì hành động gì**.

### JVM phát hiện null dereference

```java
String text = null;
int count = text.length();
```

Đây là mảnh code trong phương thức. Bản thân lệnh gọi `text.length()` yêu cầu receiver không-null, nên JVM tạo NPE ở điểm gọi. Khi helpful message được bật, JVM thường có đủ thông tin bytecode để nói `text` là null và không thể gọi `String.length()`.

### Ứng dụng chủ động ném NPE

```java
if (name == null) {
    throw new NullPointerException("name must not be null");
}
```

Đây là mảnh code với `name` đã định nghĩa. Exception được ứng dụng tạo với thông điệp rõ ràng; không cần và không nhận cơ chế phân tích null dereference của JVM. `Objects.requireNonNull(name, "name")` cũng là cách phổ biến để xác nhận tham số khác null và tạo thông điệp theo hợp đồng.

### NPE được chủ động tạo không có message

```java
throw new NullPointerException();
```

Đây cũng là mảnh code. Không nên kỳ vọng JVM nhìn ra biến nào gây lỗi nếu code chỉ yêu cầu ném một NPE rỗng. Theo Javadoc Java 17, `getMessage()` có thể trả **một thông điệp do implementation sinh hoặc `null`**, tùy thông tin nội bộ có sẵn; không nên thiết kế chức năng phụ thuộc vào nội dung NPE tự sinh.

**Hệ quả:** Helpful NPE không thay thế API validation, điều kiện tiền đề hoặc thông điệp domain. Nếu đối số không được phép null, `Objects.requireNonNull` cùng tên tham số và tài liệu `@throws` vẫn giúp người dùng API phát hiện lỗi sớm, sát ranh giới.

## 7. Tên biến trong thông điệp: vì sao đôi lúc chỉ thấy `<localN>`?

Một số helpful message có thể chứa tên biến Java:

```text
Cannot read the array length because "names" is null
```

Nhưng ở bản biên dịch khác có thể xuất hiện:

```text
Cannot read the array length because "<local1>" is null
```

Nguyên nhân thường là file class không chứa **thông tin local variable name** mà JVM có thể dùng để tái dựng tên nguồn. Có thể biên dịch với:

```bash
javac -g HelpfulNpeDemo.java
```

Tùy chọn `-g` yêu cầu `javac` tạo debug information đầy đủ, gồm thông tin biến local, dòng và nguồn. Có thể dùng dạng tùy chọn `-g:lines,vars,source` khi cần quy định loại thông tin cụ thể. Không được mặc định rằng mọi công cụ build đều bật cùng mức debug information.

**Sự khác biệt cần nhớ:**

- JVM vẫn có thể xác định thao tác thất bại và đường tham chiếu từ bytecode ngay cả khi không biết tên local chính xác.
- Tên field và method nhiều khi vẫn nhận diện được từ metadata class.
- Tên local hiện ra là **thông tin chẩn đoán**, không phải một public API contract. Đổi tên biến, tối ưu compiler hay phiên bản bytecode có thể đổi message mà không đổi hành vi lỗi.
- Khi minify/obfuscate, các tên xuất hiện có thể bị rút gọn và message cũng kém dễ đọc hơn; mapping của bộ obfuscator có thể cần thiết để debug.
- Đừng cố viết automated test theo đúng tên biến local xuất hiện trong message, trừ khi test chỉ dành để kiểm thử rất cụ thể một implementation JVM.

Debug information có thể làm lộ một phần tên local và cấu trúc nội bộ khi stack trace/log bị chia sẻ; trong môi trường production cần đánh giá chính sách log và quyền truy cập.

## 8. Những trường hợp lỗi có thể được mô tả chi tiết

Các thao tác bytecode liên quan tham chiếu có thể khiến JVM phát sinh NPE:

1. **Gọi instance method** trên object null: `service.run()` nếu `service == null`.
2. **Đọc field** từ null: `request.userId` nếu `request == null`.
3. **Ghi field** vào null: `request.status = status` khi `request == null`.
4. **Lấy độ dài array** null: `values.length`.
5. **Đọc phần tử array** null: `values[0]`.
6. **Ghi phần tử array** null: `values[0] = 3`.
7. **Dùng `null` trong phép `throw`**: `throw throwable` khi giá trị `throwable` là null; cách biểu diễn thông điệp tùy JVM.
8. **Autounboxing** một wrapper null, chẳng hạn chuyển `Integer` null thành `int`, thực chất cần một lời gọi unboxing như `intValue()` và có thể thấy helpful message cho thao tác đó.

Ví dụ unboxing (mảnh trong phương thức):

```java
Integer boxed = null;
int value = boxed;  // Unboxing gọi Integer.intValue() trên null
```

Từ thông điệp kiểu `Cannot invoke "java.lang.Integer.intValue()" because "boxed" is null`, nhà phát triển hiểu lỗi ở **unboxing của wrapper**. Nếu chỉ nhìn `int value = boxed`, có thể tưởng không có lời gọi phương thức nào. Cơ chế bytecode giải thích rõ hơn các chuyển đổi ngầm định.

Trường hợp null của **array** phải phân biệt với **phần tử trong array**. `names` null làm `names.length` lỗi ở array reference; còn `names` non-null nhưng `names[0]` null khiến `names[0].length()` lỗi ở lời gọi `length()`, không phải thao tác đọc phần tử từ array.

## 9. Chẩn đoán theo từng mắt xích của biểu thức

Xem dòng:

```java
String result = repository.find(id).details().owner().name();
```

Đây là mảnh code giả định có API `repository`, `id`, `find`, `details`, `owner`, `name`. Một helpful NPE có thể gợi ý:

- `repository` null → lỗi ở lời gọi `find(id)`;
- `find(id)` trả null → lỗi ở lời gọi `details()`;
- `details()` trả null → lỗi ở lời gọi `owner()`;
- `owner()` trả null → lỗi ở lời gọi `name()`.

Message thường chỉ vào **mắt xích ngay trước thao tác thất bại**, nhờ vậy người đọc không cần đoán cả chuỗi. Nhưng vẫn phải kiểm tra:

- Hợp đồng API: liệu `find` được phép trả null hay phải trả `Optional`?
- Dữ liệu: bản ghi thiếu quan hệ hay không tồn tại?
- Đồng thời: object có thể bị thread khác thay đổi sau khi được kiểm tra?
- Mapping: giá trị từ JSON/DB có thể không thỏa ràng buộc non-null?
- Thứ tự hiệu ứng: mỗi getter có tạo tác dụng phụ hay có thể tự ném exception khác?

**Không nên chỉ chèn `if (x == null)` để “dập” NPE mà không xác định tại sao giá trị đó hợp lệ hoặc không hợp lệ.** Một giá trị null có thể là trường hợp nghiệp vụ bình thường cần xử lý, hoặc là lỗi dữ liệu/invariant phải sửa.

## 10. Stack trace và helpful message bổ sung cho nhau

Một NPE đầy đủ thường có:

```text
java.lang.NullPointerException: Cannot invoke "..." because "..." is null
    at package.Class.method(Class.java:42)
    at package.Service.execute(Service.java:17)
```

Hai phần trả lời câu hỏi khác nhau:

- **Helpful message:** thao tác nào thất bại, tham chiếu nào null.
- **Stack trace:** NPE phát sinh trong phương thức nào, file/dòng nào và những lời gọi đồng bộ nào dẫn tới điểm đó.

Nếu log có message nhưng mất stack trace, có thể thiếu ngữ cảnh gọi thực tế. Nếu có stack trace nhưng message rỗng, có thể do NPE được tạo tường minh, tính năng bị tắt, metadata không khả dụng hoặc tình huống runtime khác. Vì vậy với lỗi cần chẩn đoán, nên dùng hệ thống logging truyền **exception object** để giữ đầy đủ stack trace, cause chain và suppressed exceptions khi có.

Ví dụ mảnh code dùng logging framework có API phù hợp:

```java
logger.error("Failed to process order {}", orderId, exception);
```

Đây là **mảnh minh họa framework logging**, không phải một chương trình Java SE tự biên dịch. Cú pháp tham số có thể khác giữa SLF4J, Log4j, JUL hoặc API đang dùng. Điều quan trọng là truyền cả exception object vào kênh log phù hợp, không chỉ lấy `exception.getMessage()` rồi đánh mất stack trace.

Khi NPE bị bọc trong `ExecutionException`, `CompletionException` hay exception domain, cần đi theo `getCause()` tới lỗi gốc; helpful message của cause có thể cung cấp nơi null dereference thật sự. Exception wrapper không tự sinh helpful message tương đương cho mã nguồn của nguyên nhân.

## 11. Tại sao không phải NPE nào cũng có thông điệp hữu ích?

Một số giới hạn thường gặp:

### 11.1. NPE do ứng dụng tạo

Nếu code `throw new NullPointerException()` hoặc sử dụng API phát sinh NPE với thông điệp chủ động, JVM không áp dụng phân tích bytecode của một null dereference do chính JVM phát hiện. Thông điệp có thể rỗng, do ứng dụng đặt, hoặc theo hợp đồng riêng của thư viện.

### 11.2. Thông tin nội bộ không còn khả dụng

Javadoc `NullPointerException.getMessage()` cho Java 17 nói rõ: mô tả verbose chỉ có thể sinh khi **thông tin nội bộ cần thiết còn tồn tại**. NPE đã **deserialize** không có thông tin nội bộ ấy, nên không được kỳ vọng tái tạo helpful message từ chính đối tượng exception đã tuần tự hóa. Nếu cần lưu dấu vết chẩn đoán dài hạn, hãy ghi thông tin lỗi phù hợp trước khi chuyển/serialize, thay vì trông chờ deserializer tái dựng runtime bytecode context.

### 11.3. Dòng mã/JVM không cung cấp được thông tin cần thiết

Một số đoạn code sinh ra bởi runtime, method ẩn, tối ưu JIT hoặc những tình huống nội bộ khác có thể không có dữ liệu để sinh thông điệp chi tiết. HotSpot từng có các tối ưu liên quan exception được ném thường xuyên; tùy bản runtime và điều kiện thực thi, stack trace hoặc message cũng có thể ít thông tin hơn. Không nên coi việc thiếu helpful message là bằng chứng rằng exception không phải null dereference.

### 11.4. Cấu hình bị tắt hoặc môi trường không tương thích

Trên JDK 14 mặc định tính năng tắt; trên JDK 15+ HotSpot thường bật mặc định nhưng có thể bị tắt qua `-XX:-ShowCodeDetailsInExceptionMessages`. Container, IDE hoặc build tool có thể chạy JDK khác với lệnh `java` trên terminal; hãy xác nhận **runtime thật của tiến trình** và các JVM options.

### 11.5. Thông điệp có thể thay đổi theo compiler và debug info

Một message có tên local `obj` trong build debug có thể chỉ là `<local1>` trong build khác. Đừng đánh đồng sự khác nhau của chuỗi message với việc logic ứng dụng đã thay đổi.

## 12. Cách xem message trong IDE và môi trường chạy

Ở terminal, một NPE không được bắt thường được runtime in cùng stack trace ra stderr theo chính sách uncaught exception. Trong IDE, debugger có thể dừng ngay tại dòng ném NPE, và vùng Variables/Call Stack giúp xem trực tiếp các tham chiếu. Helpful message giúp lọc nhanh biểu thức null, sau đó cần kiểm tra object và dữ liệu liên quan.

Quy trình chẩn đoán gợi ý:

1. **Xác nhận JDK đang chạy** bằng `java -version` hoặc cấu hình runtime của IDE/container.
2. **Đọc message và stack trace của exception gốc**, kể cả `cause` khi lỗi đã bị bọc.
3. **Tìm thao tác đầu tiên thất bại**, ví dụ “Cannot invoke” hay “Cannot read field”, cùng biểu thức được chỉ ra là null.
4. **Truy đến nguồn tạo giá trị**, ví dụ field chưa khởi tạo, trả null từ repository, deserialize thiếu dữ liệu, kết quả truy vấn rỗng.
5. **Đối chiếu hợp đồng non-null/nullability** và xử lý đúng: validation, giá trị default hợp lệ, Optional, guard clause hoặc sửa nguồn dữ liệu.
6. **Viết test hồi quy** bảo vệ nguyên nhân gốc, không dựa vào exact text của message do runtime sinh.
7. **Kiểm tra môi trường production** với log an toàn trước khi bật chế độ thông tin chi tiết hoặc tăng mức debug.

Nếu một dòng có quá nhiều thao tác, có thể tách thành các giá trị có tên rõ ràng để tăng khả năng đọc, debug, kiểm thử và xử lý null phù hợp:

```java
Address address = customer.address();
if (address == null) {
    throw new IllegalStateException("Customer has no address");
}
String city = address.city;
```

Đây là **mảnh code bên trong lớp/method có `Customer` và `Address` đã định nghĩa**. Tên exception minh họa giả định business rằng khách hàng **bắt buộc phải có địa chỉ**; nếu thiếu địa chỉ là trạng thái hợp lệ, nên chọn cách trả kết quả có thể biểu diễn thiếu dữ liệu thay vì ném lỗi trạng thái.

## 13. Bảo mật và quyền riêng tư khi ghi helpful message

Helpful NPE làm lộ thêm **tên lớp, method, field, chuỗi tham chiếu và có thể tên local variable** trong log hoặc exception text. Những thông tin này hỗ trợ developer nhưng cũng tiết lộ cấu trúc nội bộ ứng dụng. Một số nguyên tắc thực tế:

- Không trả nguyên stack trace hoặc detailed NPE message trực tiếp cho người dùng cuối qua HTTP response; dùng thông báo chung và mã theo dõi lỗi, còn chi tiết đặt trong log nội bộ có kiểm soát.
- Đánh giá độ nhạy cảm của tên biến, field, method và đối tượng trong tổ chức; đừng in dữ liệu khách hàng, token, mật khẩu hoặc connection string chỉ vì đang debug.
- Bảo đảm logger, APM, collector và nơi lưu trữ log có giới hạn quyền truy cập, chính sách retention và redaction phù hợp.
- Trong API công khai, trả mã lỗi miền nghiệp vụ ổn định; helpful NPE là **diagnostic text**, không phải hợp đồng lỗi để client parse.
- Nếu tắt chi tiết ở production vì chính sách nội bộ, lưu cách tái hiện có kiểm soát bằng môi trường thử nghiệm cùng bản build và JVM thích hợp.

Việc tắt helpful NPE không thay thế các biện pháp bảo vệ log khác; stack trace truyền thống vẫn có tên lớp, file và vị trí thực thi.

## 14. Có nên sửa ứng dụng bằng `try/catch (NullPointerException)`?

Thông thường **không**. Helpful NPE là công cụ phát hiện lỗi, không phải lời khuyên dùng `catch` để che trạng thái sai. Ví dụ:

```java
try {
    sendEmail(customer.email());
} catch (NullPointerException ignored) {
    // Bỏ qua, coi như gửi thành công.
}
```

Đây là **ví dụ sai có chủ đích**, với `customer` và `sendEmail` do ứng dụng định nghĩa. Nó có thể nuốt lỗi lập trình hoặc dữ liệu thiếu, trong khi thao tác thực tế chưa gửi được email. Hành vi caller nhìn thấy lúc đó dễ gây nhầm lẫn.

Nên sửa bằng một trong các cách theo hợp đồng:

- **Đối số bắt buộc khác null:** kiểm tra sớm bằng `Objects.requireNonNull` hoặc validation, nói rõ tên tham số.
- **Trường dữ liệu tùy chọn:** sử dụng nhánh xử lý thiếu dữ liệu, `Optional` khi phù hợp, hoặc kiểu kết quả mô tả rõ không có giá trị.
- **Bất biến object:** khởi tạo đầy đủ trong constructor, dùng field `final` khi hợp lý và tránh object ở trạng thái chưa sẵn sàng.
- **Dữ liệu từ DB/JSON/API:** kiểm tra mapping, schema, contract và tình trạng dữ liệu thực.
- **Concurrency:** bảo đảm publication/thread safety và đồng bộ những thay đổi khiến tham chiếu có thể chuyển sang null.
- **Bug lập trình:** viết test tái hiện và sửa nơi vi phạm giả định; không biến NPE thành giá trị thành công giả.

Ngoài ra, biểu thức `a.b.c` có thể rõ lỗi hơn sau khi tách thành nhiều bước, nhưng chỉ nên thêm guard khi nó phù hợp với hợp đồng. Nếu `b` phải khác null theo invariant, âm thầm dùng fallback có thể làm dữ liệu sai tiếp tục đi sâu hơn.

## 15. Phân biệt NPE với những lỗi có hình thức tương tự

Helpful NPE chỉ áp dụng cho `NullPointerException` phù hợp, không tự đổi thông điệp của các loại exception khác.

| Tình huống | Ngoại lệ phù hợp/thường gặp | Vì sao khác NPE |
| --- | --- | --- |
| Object receiver là null khi gọi instance method | `NullPointerException` | Thiếu tham chiếu đối tượng |
| Array reference là null khi truy cập `length` | `NullPointerException` | Chưa có array |
| Array tồn tại nhưng index vượt phạm vi | `ArrayIndexOutOfBoundsException` | Tham chiếu hợp lệ, chỉ số không hợp lệ |
| List tồn tại nhưng index vượt phạm vi | `IndexOutOfBoundsException` | Lỗi index, không phải null receiver |
| Gọi method khi object đã bị đóng | `IllegalStateException` hoặc API-specific | Sai trạng thái vòng đời, có thể không liên quan null |
| Dữ liệu request thiếu trường bắt buộc | Validation exception/`IllegalArgumentException` tùy API | Lỗi hợp đồng đầu vào có thể được xác định sớm |
| `Optional.empty().get()` | `NoSuchElementException` | Optional đại diện “không có giá trị”, không phải dereference null |

Ngay cả khi một biến `null` xuất hiện trong đoạn mã liên quan, exception thực tế vẫn có thể là loại khác nếu điểm ném do logic thư viện hoặc một phương thức chủ động chọn. Chẩn đoán phải dựa trên **exception thực tế, stack trace, cause và hợp đồng API**.

## 16. Kiểm thử đúng hành vi và tránh phụ thuộc thông điệp JVM

Một test hồi quy cho bug NPE nên kiểm tra **hành vi chức năng**. Ví dụ nếu `Customer.address()` được phép trả null, test nên kiểm tra endpoint/logic xử lý trường hợp không có địa chỉ theo hợp đồng thay vì chỉ khẳng định chứa chuỗi `"return value of"`.

Nếu đang kiểm thử riêng JEP 358 trên một runtime xác định, có thể dùng test chẩn đoán với điều kiện môi trường rõ ràng:

```java
try {
    String[] names = null;
    int count = names.length;
    throw new AssertionError("Expected NullPointerException: " + count);
} catch (NullPointerException ex) {
    String message = ex.getMessage();
    if (message == null || !message.contains("array length")) {
        throw new AssertionError("Helpful NPE not available", ex);
    }
}
```

Đây là **mảnh kiểm thử trong một phương thức** trên HotSpot với cờ bật; không phải test ứng dụng nên bắt buộc chạy trên mọi Java runtime. Điều kiện `message.contains("array length")` cũng là ràng buộc của implementation đang được xác nhận, có thể cần thay đổi khi runtime đổi format. Trong business test, nên kiểm tra kiểu exception và kết quả thật hoặc điều kiện hợp đồng.

Một số test hay sai:

- `assertEquals("Cannot invoke ...", ex.getMessage())` trong code nghiệp vụ → quá phụ thuộc JVM/compiler.
- Giả định mọi NPE mặc nhiên phải có `getMessage() != null` → sai với NPE tạo tường minh hoặc thiếu metadata.
- Giả định JDK 14 và JDK 17 có cùng cấu hình mặc định → sai.
- Giả định `javac` đã tạo tên biến local trong mọi bản build → cần kiểm tra `-g` và build configuration.
- Chỉ xem message ở wrapper `ExecutionException` mà không đọc `getCause()` → bỏ lỡ NPE thực sự.
- Cho rằng có helpful NPE thì có thể bỏ debugger, tracing, unit test hoặc validation → message không chỉ ra nguyên nhân nghiệp vụ.

## 17. Khi bật cờ mà không thấy thông điệp chi tiết

Một quy trình xử lý sự cố ngắn:

1. Kiểm tra `java -version` của **process thật**. Build tool, Docker image hoặc IDE có thể dùng runtime khác shell.
2. Đảm bảo JVM option đặt trước main class/JAR đúng cú pháp: `java -XX:+ShowCodeDetailsInExceptionMessages ...`.
3. Dùng ví dụ tạo NPE do **JVM tự phát sinh** bằng cách dereference null; đừng chỉ `throw new NullPointerException()`.
4. Dùng `ex.getMessage()` hoặc in stack trace thật; nếu logger chỉ in tên class và bỏ message, phần chi tiết có thể không được hiển thị.
5. Kiểm tra exception có phải **cause** nằm trong wrapper và có bị thay bằng exception mới không.
6. Nếu message có `<localN>`, thử biên dịch với `javac -g` để có local-variable metadata; lưu ý build pipeline có thể tước debug info.
7. Kiểm tra có đang đọc một NPE đã **deserialize** hoặc đã được chuyển đổi bằng cơ chế đặc biệt không.
8. Nếu JVM hỗ trợ nhưng output vẫn bất thường, đối chiếu tài liệu của bản vendor, JVM flags, tối ưu runtime và điều kiện tái hiện; tránh dựa vào format chuẩn chung không tồn tại.

Trong môi trường production, việc tái hiện nên giữ dữ liệu nhạy cảm an toàn. Không dump toàn bộ object, headers hoặc thông tin xác thực vào log chỉ để kiểm tra một NPE.

## 18. Giới hạn của công cụ và cách kết hợp để sửa tận gốc

Helpful NPE xác định **biểu thức null tại thời điểm dereference**, nhưng các câu hỏi sau vẫn cần điều tra:

- Giá trị null xuất phát từ API ngoài, mapping DB, DTO, uninitialized field hay một race condition?
- Null là hợp lệ theo domain hay là vi phạm invariant?
- Hệ thống có side effect trước khi ném NPE không; transaction đã commit hay cần rollback?
- Caller có retry nhầm một thao tác đã xử lý một phần sau khi nhận exception không?
- Lỗi chỉ xuất hiện trên một JVM, với compiler hoặc dữ liệu cụ thể hay không?

Quy trình xử lý thường là: **đọc helpful message → xem stack/cause → tái hiện input và state → kiểm tra hợp đồng nullability → sửa nguồn lỗi → kiểm thử hồi quy**. Nếu là API Java công khai, nên tài liệu hóa tiền điều kiện, dùng check có ý nghĩa và tránh trả null không rõ ràng.

## 19. Nguồn tham khảo chuẩn

- **JEP 358 — Helpful NullPointerExceptions:** https://openjdk.org/jeps/358 (đặc tả mục tiêu, thiết kế phân tích bytecode và giới hạn).
- **Javadoc `java.lang.NullPointerException` của Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NullPointerException.html (đặc biệt ghi chú của `getMessage()` về thông tin nội bộ và NPE deserialize).
- **Oracle JDK 14 Release Notes:** https://www.oracle.com/java/technologies/javase/14all-relnotes.html (tùy chọn `ShowCodeDetailsInExceptionMessages` và trạng thái mặc định của JDK 14).
- **Javadoc `java.util.Objects.requireNonNull`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html (kiểm tra tham số null với thông điệp do API định nghĩa).

Đây là các nguồn để kiểm chứng hành vi JDK; nội dung chuỗi message cụ thể có thể thay đổi theo runtime và compiler.

## 20. Checklist thực hành

1. Biết version: JDK 14 mặc định tắt, HotSpot JDK 15+ mặc định bật helpful messages.
2. Khi cần so sánh, chạy với `-XX:+ShowCodeDetailsInExceptionMessages` và `-XX:-ShowCodeDetailsInExceptionMessages`.
3. Đọc cả hành động thất bại (*Cannot invoke/read/assign...*) và biểu thức null (*because ... is null*).
4. Phân biệt NPE JVM phát sinh từ null dereference và NPE được application chủ động tạo.
5. Hiểu cơ chế phân tích bytecode và ảnh hưởng của `javac -g` tới tên biến local.
6. Đọc stack trace/cause chain để thấy bối cảnh, không chỉ mỗi message.
7. Đừng coi helpful message là một API ổn định để parse hoặc assert nguyên chuỗi trong test nghiệp vụ.
8. Giữ log chi tiết an toàn, không trả stack trace kỹ thuật ra người dùng cuối.
9. Kiểm tra nullability contract và dữ liệu gốc để sửa nguyên nhân thật.
10. Viết test hồi quy theo kết quả chức năng; dùng test message chỉ khi đang kiểm chứng một JVM có phiên bản xác định.

**Nguyên tắc cốt lõi:** JDK hiện đại giúp NPE dễ chẩn đoán hơn bằng cách nói **thao tác nào thất bại và tham chiếu nào null**. Đây là trợ giúp debug dựa trên bytecode, không phải bằng chứng về nguyên nhân nghiệp vụ và không thể thay thế validation, thread safety hay thiết kế hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-005">Ghi cả đối tượng ngoại lệ thay vì chỉ ghi getMessage</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi cả đối tượng ngoại lệ thay vì chỉ ghi `getMessage()` trong Java

Khi một thao tác Java thất bại, câu lệnh `logger.error("Lỗi: {}", exception.getMessage())` có thể tạo ra một dòng log trông có vẻ đủ thông tin. Nhưng khi cần biết lỗi phát sinh từ dòng mã nào, lời gọi nào dẫn đến lỗi hoặc có ngoại lệ nào nằm bên trong, thông điệp đơn lẻ thường không đủ. **Ghi đối tượng `Throwable` vào logger** giúp hệ thống lưu cả loại ngoại lệ, stack trace, chuỗi nguyên nhân (`cause`) và các ngoại lệ đi kèm (`suppressed exception`) mà formatter hỗ trợ.

Chủ đề này thuộc kỹ năng chẩn đoán và ghi log lỗi. Mục tiêu là phân biệt dữ liệu do `getMessage()` trả về với dữ liệu của toàn bộ ngoại lệ, dùng đúng API logging trong Java, giữ được nguyên nhân khi bọc/ném lại lỗi, và tránh tạo log nhiễu hoặc rò rỉ thông tin.

## 1. `Throwable` thực sự chứa những gì?

Trong Java, `Exception` và `Error` đều kế thừa `Throwable`. Một đối tượng `Throwable` có thể mang nhiều phần dữ liệu:

| Thành phần | Cách truy cập | Ý nghĩa |
| --- | --- | --- |
| Kiểu ngoại lệ | `exception.getClass()` | Xác định nhóm lỗi: `IOException`, `IllegalStateException`, `NumberFormatException`... |
| Thông điệp | `exception.getMessage()` | Mô tả ngắn do nơi tạo ngoại lệ cung cấp; **có thể là `null`** |
| Dòng mô tả | `exception.toString()` | Thông thường là tên lớp ngoại lệ kèm thông điệp nếu có |
| Ngăn xếp lời gọi | `exception.getStackTrace()` | Các khung stack liên quan tới lúc stack trace của ngoại lệ được tạo/điền |
| Nguyên nhân | `exception.getCause()` | Ngoại lệ bên dưới đã dẫn đến ngoại lệ hiện tại, nếu được liên kết |
| Ngoại lệ bị đính kèm | `exception.getSuppressed()` | Các ngoại lệ bổ sung, thường xuất hiện khi đóng tài nguyên bằng `try-with-resources` |

`getMessage()` **chỉ lấy một chuỗi**. Nó không thể tự mang stack trace, tên lớp ngoại lệ, chuỗi cause và suppressed exceptions. Ngay cả khi thông điệp ghi “Không đọc được hóa đơn”, ta vẫn cần biết thao tác đã lỗi tại đâu và nguyên nhân gốc là mất kết nối, quyền truy cập hay dữ liệu không hợp lệ.

Ví dụ:

```java
java.io.IOException root = new java.io.IOException("disk unavailable");
IllegalStateException error =
        new IllegalStateException("cannot load invoice 42", root);

System.out.println(error.getMessage());
System.out.println(error.getCause().getClass().getSimpleName());
```

Kết quả:

```text
cannot load invoice 42
IOException
```

Đây là **đoạn mã minh họa đặt trong phương thức**, không phải một chương trình Java độc lập. Hai dòng kết quả cho thấy: thông điệp của lỗi bên ngoài **không tự chứa** nguyên nhân `IOException`; người chẩn đoán phải theo `getCause()` hoặc dùng một formatter nhận cả `Throwable`.

## 2. So sánh: chỉ log thông điệp và log toàn bộ ngoại lệ

Trong ứng dụng dùng thư viện SLF4J (ví dụ SLF4J kết nối với Logback), so sánh:

```java
try {
    loadInvoice(invoiceId);
} catch (Exception exception) {
    logger.error("Cannot load invoice {}: {}",
            invoiceId, exception.getMessage()); // Thiếu Throwable.
}
```

với:

```java
try {
    loadInvoice(invoiceId);
} catch (Exception exception) {
    logger.error("Cannot load invoice {}", invoiceId, exception);
}
```

Hai khối trên là **mã minh họa bên trong ứng dụng** đã có `logger`, `invoiceId` và `loadInvoice(...)`, không thể chạy độc lập nếu chưa có những khai báo đó cùng thư viện SLF4J. Cú pháp `{}` là cơ chế tham số hóa thông điệp của SLF4J, **không phải** cú pháp `java.util.logging`.

Ở đoạn đầu, logger chỉ nhận chuỗi từ `exception.getMessage()`: thường mất tên loại ngoại lệ, các khung stack và nguyên nhân bên trong. Ở đoạn sau, logger nhận `Throwable` như một đối số ngoại lệ; với backend logging được cấu hình ghi lỗi, bản ghi có thể bao gồm stack trace và chuỗi cause.

Nếu không có tham số nghiệp vụ, cách rõ ràng nhất thường là:

```java
logger.error("Cannot load invoice", exception);
```

Ở đây tham số thứ hai là `Throwable`, thể hiện trực tiếp ý định ghi ngoại lệ. Với dạng có `{}`, hãy dùng đúng hợp đồng thư viện; trong SLF4J thông dụng, `Throwable` ở vị trí cuối của lời gọi tham số hóa được nhận diện là đối số ngoại lệ. Đừng cố bắt formatter đoán lỗi qua một chuỗi đã được chuyển đổi.

## 3. Ví dụ Java 17+ độc lập: logger nhận được cả `Throwable`

Java chuẩn đã có `java.util.logging` (JUL), không cần thêm thư viện. Ví dụ sau tạo handler nhỏ để in ra các trường đã nhận theo định dạng **cố định**, giúp kiểm tra không phụ thuộc timestamp hay mẫu formatter của môi trường. Trong ứng dụng thật, dùng handler/backend logging đã được cấu hình; phần handler thử nghiệm ở đây chỉ phục vụ quan sát.

Tạo tệp **`ExceptionLoggingDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ExceptionLoggingDemo {
    static void readInvoice() throws IOException {
        throw new IOException("disk unavailable");
    }

    static void processInvoice() {
        try {
            readInvoice();
        } catch (IOException cause) {
            throw new IllegalStateException("cannot load invoice 42", cause);
        }
    }

    public static void main(String[] args) {
        Logger logger = Logger.getLogger("demo.exception.logging");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        Handler demoHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                Throwable error = record.getThrown();
                StringWriter buffer = new StringWriter();

                if (error != null) {
                    error.printStackTrace(new PrintWriter(buffer));
                }

                String trace = buffer.toString();
                System.out.println("level=" + record.getLevel());
                System.out.println("logMessage=" + record.getMessage());
                System.out.println("throwableAttached=" + (error != null));
                System.out.println("originInTrace="
                        + trace.contains("at ExceptionLoggingDemo.readInvoice("));
                System.out.println("causeInTrace=" + trace.contains(
                        "Caused by: java.io.IOException: disk unavailable"));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        demoHandler.setLevel(Level.ALL);
        logger.addHandler(demoHandler);

        try {
            processInvoice();
        } catch (IllegalStateException exception) {
            System.out.println("onlyMessage=" + exception.getMessage());
            logger.log(Level.SEVERE, "Invoice processing failed", exception);
        } finally {
            logger.removeHandler(demoHandler);
            demoHandler.close();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ExceptionLoggingDemo.java
java ExceptionLoggingDemo
```

Đầu ra:

```text
onlyMessage=cannot load invoice 42
level=SEVERE
logMessage=Invoice processing failed
throwableAttached=true
originInTrace=true
causeInTrace=true
```

Giải thích:

1. `readInvoice()` ném `IOException("disk unavailable")`. `processInvoice()` bọc lỗi thành `IllegalStateException` kèm thông tin nghiệp vụ, **vẫn giữ nguyên nhân** qua constructor có đối số `cause`.
2. `exception.getMessage()` chỉ cho biết `cannot load invoice 42`. Nếu ứng dụng chỉ ghi giá trị đó, log không có stack trace/cause kèm theo.
3. `logger.log(Level.SEVERE, "Invoice processing failed", exception)` sử dụng overload nhận `Throwable`. `LogRecord.getThrown()` trong handler trả về đúng đối tượng ngoại lệ được gắn vào bản ghi.
4. `Throwable.printStackTrace(...)` cho phép handler kiểm tra bản trình bày đầy đủ có khung gọi `readInvoice()` và dòng `Caused by: java.io.IOException: disk unavailable`. Hai giá trị `true` chứng minh điều đó mà không phụ thuộc số dòng hoặc timestamp.

Với JUL, cách viết thông thường có thể chỉ là:

```java
logger.log(Level.SEVERE, "Failed to process invoice", exception);
```

Đây là một dòng minh họa trong phạm vi đã có `java.util.logging.Logger logger` và biến `exception`. Khi dùng `java.util.logging`, thông điệp tham số hóa theo mẫu `{0}`, `{1}` của `MessageFormat`, khác với `{}` trong SLF4J. Ví dụ:

```java
logger.log(Level.SEVERE, "Invoice {0} failed", new Object[] {invoiceId});
```

Dòng cuối chỉ minh họa **tham số hóa thông điệp** trong JUL; lời gọi này **không tự gắn ngoại lệ**. Nếu cần cả dữ liệu nghiệp vụ và `Throwable`, có thể dựng một thông điệp an toàn rồi gọi overload ba tham số `(Level, String, Throwable)`, hoặc dùng cơ chế bản ghi có ngữ cảnh của nền tảng logging.

## 4. Vì sao `getMessage()` thường không đủ để chẩn đoán?

### 4.1. Thông điệp có thể rỗng hoặc thiếu ngữ cảnh

Không phải constructor nào của exception cũng được truyền thông điệp:

```java
RuntimeException failure = new RuntimeException();
System.out.println(failure.getMessage() == null);
System.out.println(failure.getClass().getSimpleName());
```

Kết quả:

```text
true
RuntimeException
```

Đây là đoạn minh họa trong một phương thức. Log chỉ ghi `failure.getMessage()` có thể ghi `null`, trong khi tên lớp và stack trace vẫn giúp nhận diện lỗi. Một số API còn sử dụng thông điệp chung chung như “request failed”; chỉ có stack trace và cause mới giúp tìm đường đi thực tế.

### 4.2. Thông điệp không cho biết vị trí sinh lỗi

Hai thao tác cùng ném `IllegalArgumentException("invalid id")` có thể phát sinh tại hai nhánh xử lý khác nhau. `getMessage()` của chúng giống hệt nhau, còn stack trace giúp chỉ ra các phương thức và dòng mã liên quan đến từng lần tạo/ném lỗi.

Lưu ý: stack trace thường gắn với lúc exception được tạo hoặc khi `fillInStackTrace()` được gọi; không nên nghĩ logger tự phát minh lại lịch sử lời gọi tại thời điểm ghi log. Một exception được dùng lại hoặc được tạo với việc ghi stack trace bị vô hiệu hóa có thể không chứa các khung mong đợi. Stack trace cũng có thể thiếu/được rút gọn vì tối ưu hóa JVM hoặc cấu hình thư viện.

### 4.3. Thông điệp không chứa chuỗi nguyên nhân

Một lỗi nghiệp vụ có thể chứa nhiều tầng:

```text
IllegalStateException: cannot load invoice 42
    caused by IOException: disk unavailable
        caused by FileNotFoundException: /data/invoices/42.json
```

Ví dụ là một **chuỗi nguyên nhân khái niệm**, không phải stdout từ chương trình trên. Nếu chỉ ghi thông điệp của ngoại lệ ngoài cùng, ta có thể bỏ qua phần quan trọng nhất ở tầng I/O. Khi ghi cả `Throwable`, formatter chuẩn thường trình bày các khối `Caused by:` và các khung stack liên quan.

Không nên luôn bỏ tất cả wrapper để chỉ ghi cause sâu nhất. Ngoại lệ ngoài cùng có thể mang **ngữ cảnh có ý nghĩa**, như tên thao tác hoặc quy tắc nghiệp vụ đã thất bại. Ghi cả chuỗi giúp giữ đồng thời ngữ cảnh nghiệp vụ và nguyên nhân kỹ thuật.

### 4.4. Lỗi đóng tài nguyên có thể nằm trong `getSuppressed()`

Trong `try-with-resources`, nếu thân `try` ném ngoại lệ và thao tác `close()` cũng ném, Java giữ lỗi từ thân `try` làm lỗi chính và thêm lỗi đóng tài nguyên vào danh sách suppressed exceptions. `getMessage()` của lỗi chính không trình bày sự kiện bổ sung ấy.

## 5. Ví dụ thứ hai: log đầy đủ giữ được suppressed exception

Tạo tệp **`SuppressedExceptionDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class SuppressedExceptionDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void useResource() throws IOException {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IOException("read failed");
        }
    }

    public static void main(String[] args) {
        try {
            useResource();
        } catch (IOException exception) {
            System.out.println("onlyMessage=" + exception.getMessage());
            System.out.println("suppressedCount="
                    + exception.getSuppressed().length);

            StringWriter buffer = new StringWriter();
            exception.printStackTrace(new PrintWriter(buffer));
            String trace = buffer.toString();
            System.out.println("primaryInTrace="
                    + trace.contains("java.io.IOException: read failed"));
            System.out.println("suppressedInTrace=" + trace.contains(
                    "Suppressed: java.io.IOException: close failed"));
        }
    }
}
```

Chạy:

```bash
javac --release 17 SuppressedExceptionDemo.java
java SuppressedExceptionDemo
```

Đầu ra:

```text
onlyMessage=read failed
suppressedCount=1
primaryInTrace=true
suppressedInTrace=true
```

Lỗi `read failed` là ngoại lệ chính. Trong quá trình rời khỏi `try-with-resources`, `close()` ném thêm `close failed`; Java đính lỗi này vào ngoại lệ chính qua `addSuppressed` bên dưới cơ chế `try-with-resources`. Nếu chỉ log `getMessage()`, thông tin `close failed` biến mất khỏi bản ghi. Log toàn bộ `Throwable` bằng một backend hỗ trợ stack trace cho phép nhìn thấy cả hai.

`getSuppressed()` cũng có thể trống, và một số ngoại lệ có cơ chế suppression bị vô hiệu hóa. Không nên giả định mọi lỗi đóng tài nguyên luôn nằm ở suppressed: cách xử lý phụ thuộc cấu trúc `try`, tài nguyên và ngoại lệ nào được ném trước.

## 6. Đặt `Throwable` đúng vị trí ở các logging API

### 6.1. Java Util Logging (`java.util.logging`)

```java
logger.log(Level.SEVERE, "Cannot load customer", exception);
```

Đây là lựa chọn rõ ràng khi dùng JUL: đối số thứ ba là `Throwable`, được lưu trong `LogRecord`. Nếu muốn thay đổi thứ tự hoặc định dạng các trường, đó là trách nhiệm của formatter/handler.

Cần phân biệt với:

```java
logger.severe("Cannot load customer: " + exception.getMessage());
```

Đoạn thứ hai chỉ gửi một chuỗi tới JUL. Không có tham chiếu `Throwable` trong bản ghi nên formatter không thể suy ra stack trace hay cause.

### 6.2. SLF4J và backend như Logback

```java
logger.error("Cannot load customer {}", customerId, exception);
```

Đây là **đoạn mã minh họa** trong ứng dụng có SLF4J, logger và biến `customerId`. Trong cách dùng SLF4J thông thường, đối số cuối `Throwable` được gắn vào sự kiện logging, còn `{}` nhận `customerId`. Có thể dùng overload trực tiếp `logger.error("Cannot load customer", exception)` khi không cần tham số hóa.

Ngược lại:

```java
logger.error("Cannot load customer {}: {}",
        customerId, exception.getMessage());
```

Chỉ ghi thông điệp lỗi dưới dạng chuỗi; stack trace không đi cùng bản ghi.

Chú ý về API cụ thể: nhiều thư viện có overload khác nhau cho `String`, `Object`, varargs và `Throwable`. Nếu wrapper logging của dự án không nhận diện Throwable ở đối số cuối như SLF4J, hãy dùng overload hoặc trường `cause` đúng theo hợp đồng của wrapper đó. Không nên dựa vào cách một hàm tùy biến chuyển các đối số sang chuỗi.

### 6.3. `System.Logger` trong Java

Ngoài JUL, Java còn có `System.Logger`. Có thể viết:

```java
System.Logger log = System.getLogger("billing");
log.log(System.Logger.Level.ERROR, "Cannot load customer", exception);
```

Đây là đoạn minh họa có thể đặt trong một phương thức hoặc chương trình Java. Overload nhận `Throwable` giúp gửi thông tin ngoại lệ cho bộ ghi log của nền tảng. Cách trình bày cụ thể phụ thuộc cấu hình logging/provider.

Trong cả ba cách, nguyên tắc giống nhau: **chuyển chính đối tượng ngoại lệ vào API logging có hỗ trợ `Throwable`**; việc gọi `.getMessage()` trước khi log làm mất những trường thông tin chưa được chuyển đổi.

## 7. Giữ nguyên nhân khi bắt và ném lại ngoại lệ

Việc log đủ stack trace không cứu được chuỗi cause nếu mã đã làm mất nó trước đó.

Ví dụ **không nên làm**:

```java
try {
    repository.load(id);
} catch (java.io.IOException exception) {
    throw new IllegalStateException(exception.getMessage());
}
```

Đây là đoạn minh họa cần `repository` và `id`. Ngoại lệ mới không liên kết với `IOException` cũ. Logger nhận cả `IllegalStateException` sau này cũng không thể tái tạo nguyên nhân đã bị loại bỏ.

Thay bằng:

```java
try {
    repository.load(id);
} catch (java.io.IOException exception) {
    throw new IllegalStateException(
            "Cannot load record " + id, exception);
}
```

Constructor nhận `cause` giữ được ngoại lệ gốc, đồng thời thêm ngữ cảnh thao tác. Trong các trường hợp chỉ cần chuyển tiếp lỗi mà không thêm ý nghĩa, `throw exception;` có thể phù hợp hơn việc tạo wrapper mới.

**Tránh tạo wrapper vô ích ở mọi tầng.** Việc bọc chỉ có giá trị khi đổi tầng trừu tượng hoặc bổ sung thông tin cần thiết. Một chuỗi hàng chục `RuntimeException("failed", previous)` làm log khó đọc và khó nhóm lỗi, dù về kỹ thuật vẫn giữ cause.

## 8. Log tại ranh giới nào để tránh lặp và vẫn thấy lỗi?

Một exception có thể đi qua repository → service → controller → bộ xử lý lỗi toàn cục. Nếu mỗi tầng đều ghi `ERROR` cùng stack trace rồi ném tiếp, hệ thống có thể tạo nhiều bản ghi cho một sự cố. Điều này làm khó tìm nguyên nhân thực tế và tạo lưu lượng log lớn.

Một chiến lược thường dùng:

1. **Tầng thấp:** thêm ngữ cảnh rồi ném lại nếu lỗi không thể xử lý; chỉ log nếu tại đây có hành động phục hồi, quyết định nghiệp vụ hoặc thông tin chẩn đoán không thể bổ sung ở tầng cao.
2. **Tầng xử lý lỗi sở hữu quyết định cuối:** log `Throwable` **một lần** với mã yêu cầu/tên thao tác đủ để tìm lại, chuyển thành phản hồi hoặc trạng thái thất bại phù hợp.
3. **Tầng đã phục hồi được lỗi:** có thể ghi `WARN` cùng lý do fallback nếu cần theo dõi; không nhất thiết in toàn bộ stack trace cho một tình huống dự kiến xảy ra thường xuyên.
4. **Lỗi nghiêm trọng hoặc bất ngờ:** chuyển đủ nguyên nhân vào hệ thống giám sát, ưu tiên khả năng tìm stack trace và ngữ cảnh an toàn.

Không có một vị trí log `ERROR` duy nhất phù hợp cho mọi kiến trúc; điều quan trọng là **chủ sở hữu việc ghi log phải rõ**. Hãy cân nhắc log một lần tại ranh giới có đủ ngữ cảnh, thay vì log rồi ném lên vô hạn.

## 9. Ngoại lệ trong `CompletableFuture`, `ExecutorService` và callback

Với tác vụ bất đồng bộ, luồng gọi có thể nhận ngoại lệ **bọc** thay vì ngoại lệ gốc:

- `Future.get()` thường ném `ExecutionException`, `getCause()` chứa thất bại của tác vụ.
- `CompletableFuture.join()` thường ném `CompletionException`, `getCause()` chứa thất bại nguồn.
- Một callback `whenComplete((value, error) -> ...)` nhận `Throwable` khi stage thất bại; `error` có thể là ngoại lệ gốc hoặc lớp bọc kỹ thuật tùy pipeline.

Ví dụ minh họa một ranh giới log:

```java
try {
    future.join();
} catch (java.util.concurrent.CompletionException exception) {
    logger.error("Async invoice computation failed", exception);
    throw exception;
}
```

Đoạn này cần một `CompletableFuture<?> future` và `logger` của ứng dụng; sử dụng API `logger.error` theo SLF4J. Khi logger được truyền **cả `CompletionException`**, formatter có thể in chuỗi `Caused by:`. Nếu tầng xử lý muốn phân loại lỗi gốc, có thể gỡ `CompletionException`/`ExecutionException` để chọn chính sách, nhưng nên giữ chuỗi gốc trong log để biết đường đi qua future.

Một chương trình gọi `submit()` nhưng bỏ `Future` hoặc tạo `CompletableFuture` mà không có nơi quan sát lỗi có thể bỏ lỡ việc ghi log. Tác vụ thất bại không có nghĩa hệ thống tự động gọi logger. Thiết kế phải xác định **ai quan sát kết quả và chịu trách nhiệm xử lý lỗi**.

Trong callback `whenComplete`, nếu chỉ muốn quan sát, hãy tránh để chính câu lệnh log ném ngoại lệ mới. Callback bị lỗi có thể làm stage trả về thay đổi trạng thái. Với logger thông dụng, nguy cơ này hiếm, nhưng code tùy biến gọi mạng/I/O trong callback vẫn cần kiểm soát.

## 10. Đọc log đầy đủ để lần theo lỗi

Một bản ghi có stack trace kiểu:

```text
java.lang.IllegalStateException: cannot load invoice 42
    at ExceptionLoggingDemo.processInvoice(ExceptionLoggingDemo.java:...)
Caused by: java.io.IOException: disk unavailable
    at ExceptionLoggingDemo.readInvoice(ExceptionLoggingDemo.java:...)
```

Đây là **dạng minh họa**, dấu `...` chỉ số dòng phụ thuộc tệp nguồn chứ không phải stdout được cam kết. Khi phân tích:

- Dòng đầu mô tả **ngoại lệ đang được báo** tại ranh giới log.
- Các dòng `at ...` cho biết khung gọi liên quan đến stack trace. Kiểm tra khung đầu tiên thuộc mã ứng dụng, đồng thời xem đường dẫn gọi vào đó.
- `Caused by:` cho biết lỗi bên dưới được giữ bằng `getCause()`.
- `Suppressed:` nếu có, thường cho biết lỗi phụ như đóng tài nguyên.
- Phần `... N more` trong stack trace là cách Java rút gọn các khung trùng giữa cause và wrapper; không chứng minh dữ liệu nguyên nhân đã mất.

Đừng chỉ nhìn một dòng top-level và kết luận lỗi nằm ở controller, endpoint hoặc `join()`. Những vị trí đó có thể chỉ là nơi lỗi **được quan sát**, còn nguyên nhân ban đầu được tạo trong repository/worker ở một phần khác của chuỗi.

`exception.toString()` hữu ích hơn `getMessage()` khi cần cả tên lớp và thông điệp, nhưng **vẫn không thay thế** cho một log record đính kèm `Throwable`.

## 11. Nguyên tắc bảo mật, hiệu năng và chất lượng log

Log đầy đủ là công cụ chẩn đoán; cần dùng có trách nhiệm:

- **Không đưa bí mật vào thông điệp lỗi hoặc ngữ cảnh log.** Password, token truy cập, cookie, dữ liệu định danh nhạy cảm hoặc nội dung request tùy ý có thể bị ghi lại nếu ứng dụng lồng chúng vào exception message. Truyền cả `Throwable` không tự động ẩn các dữ liệu đó.
- **Giảm dữ liệu có tính cá nhân.** Dùng mã tương quan yêu cầu, mã nghiệp vụ không nhạy cảm và trạng thái lỗi đã được phép ghi. Nếu log tập trung có bộ lọc/masking, kiểm tra cả trường message và stack trace.
- **Không giả định logger sẽ luôn in stack trace.** Output phụ thuộc backend, formatter, cấu hình mức log, sampling và nơi lưu log. Cần kiểm tra cấu hình thực tế để xác nhận trường throwable được ghi.
- **Ưu tiên logging có tham số.** Với SLF4J, mẫu `logger.error("Failed order {}", orderId, exception)` giữ cấu trúc tham số và tránh nối chuỗi thông điệp thủ công. Chú ý API cụ thể của thư viện và cơ chế rút trích `Throwable`.
- **Giới hạn log lặp lại.** Một lỗi xảy ra hàng nghìn lần trong vòng lặp không nên vô điều kiện tạo hàng nghìn stack trace lớn. Cân nhắc cảnh báo theo lô, đếm metric, lấy mẫu hoặc giảm mức độ sau khi đã nhận diện sự cố.
- **Xử lý đúng mức độ lỗi.** `INFO`, `WARN`, `ERROR`/`SEVERE` nên phản ánh tác động và hành động cần thiết, không phải cứ có `Exception` là log mức cao nhất.
- **Không phụ thuộc `printStackTrace()` trực tiếp trên production.** Nó hữu ích khi học hoặc kiểm thử, nhưng ghi ra stderr không cung cấp đầy đủ định tuyến, mức log, mã tương quan và chính sách dữ liệu của nền tảng logging.
- **Không nuốt lỗi sau khi log.** Nếu phương thức không phục hồi được, hãy truyền lỗi hoặc trả kết quả thất bại phù hợp; ghi log rồi trả giá trị “thành công” không có giải thích khiến hệ thống khó phát hiện sai lệch.

## 12. Checklist chẩn đoán và câu hỏi tự kiểm tra

Khi gặp dòng “Failed to process” không rõ nguyên nhân, hãy kiểm tra:

1. Ở nhánh `catch`, hệ thống **đưa `Throwable` vào logger**, hay chỉ chuyển nó thành `getMessage()`/`toString()`?
2. Khi bọc lỗi, constructor **có nhận `cause`** không?
3. Trong log có tên loại ngoại lệ, stack trace, `Caused by:` và (nếu có) `Suppressed:` không?
4. Lỗi được log tại ranh giới sở hữu việc xử lý, hay bị ghi lặp lại ở nhiều tầng?
5. Với công việc bất đồng bộ, code **có giữ và kiểm tra `Future`/`CompletableFuture`**, hay lỗi bị bỏ qua?
6. Nội dung ghi log có chứa dữ liệu nhạy cảm hoặc tạo quá nhiều bản ghi trùng lặp không?

**Câu 1.** `exception.getMessage()` có thể là `null` không?  
**Trả lời:** Có. Nó chỉ là phần mô tả tùy chọn, không phải toàn bộ ngoại lệ.

**Câu 2.** `logger.error("Failed: {}", exception.getMessage())` có chắc lưu stack trace không?  
**Trả lời:** Không. Theo cách dùng SLF4J thông thường, logger chỉ nhận một chuỗi từ `getMessage()` trong trường hợp này.

**Câu 3.** Cách ghi với JUL để chuyển cả exception là gì?  
**Trả lời:** `logger.log(Level.SEVERE, "Operation failed", exception)`; overload này đặt `Throwable` vào `LogRecord`.

**Câu 4.** `throw new IllegalStateException(exception.getMessage())` có giữ cause không?  
**Trả lời:** Không. Dùng `new IllegalStateException("context", exception)` khi cần giữ nguyên nhân.

**Câu 5.** Vì sao `getSuppressed()` quan trọng?  
**Trả lời:** Nó có thể chứa lỗi phát sinh khi đóng tài nguyên, trong khi lỗi chính vẫn là ngoại lệ ném từ thân `try`.

**Câu 6.** Nên log full stack trace ở mọi tầng không?  
**Trả lời:** Không nhất thiết. Nên chọn ranh giới có đủ ngữ cảnh và trách nhiệm xử lý, tránh cùng một sự cố bị log lặp lại vô ích.

**Câu 7.** `toString()` có thay thế được log cả `Throwable` không?  
**Trả lời:** Không. `toString()` thông thường chứa tên lớp và thông điệp nhưng không mang toàn bộ stack trace/cause/suppressed dưới dạng một bản ghi lỗi đầy đủ.

Kết luận thực hành: **hãy truyền đối tượng ngoại lệ cho API logging có hỗ trợ `Throwable`**, giữ nguyên nhân khi bọc lỗi, và ghi ngữ cảnh thao tác an toàn. `getMessage()` có ích để trình bày một thông điệp ngắn, nhưng không đủ làm bằng chứng chẩn đoán khi lỗi cần được điều tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-006">Thêm ngữ cảnh thao tác và định danh có ích cho điều tra</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm ngữ cảnh thao tác và định danh có ích cho điều tra lỗi trong Java

> **Chủ đề:** Java Core → Exception → Chẩn đoán và ghi log. Mục tiêu của bài là biến một thông báo ngoại lệ chung chung thành bằng chứng đủ để truy vết *thao tác nào thất bại, đối tượng nghiệp vụ nào liên quan, request/tác vụ nào phát sinh lỗi, hệ thống đang ở bước nào và nguyên nhân gốc là gì*; đồng thời tránh làm lộ dữ liệu nhạy cảm hoặc gây nhiễu bởi log trùng lặp.

## 1. Tại sao chỉ ghi exception message là chưa đủ?

Giả sử dịch vụ xử lý hàng nghìn đơn hàng cùng lúc và log duy nhất là:

~~~text
ERROR java.lang.IllegalStateException: timeout
~~~

Thông báo này không nói rõ lỗi xảy ra khi xác nhận đơn, thanh toán, giữ hàng hay gửi thông báo; không thể nối nó với một request, đơn hàng, tác vụ nền hoặc dependency cụ thể. Stack trace xác định **đường gọi mã**, nhưng chưa chắc cho biết **ngữ cảnh nghiệp vụ** khiến lỗi phát sinh.

Một sự kiện log hữu ích có thể biểu đạt:

~~~text
level=ERROR event=order_save_failed operation=place_order
requestId=req-7e21 orderId=ord-1042
errorCode=ORDER_PERSIST_FAILED dependency=orders-db
exception=OrderProcessingException
cause=SQLTransientConnectionException
~~~

Đây là log **minh họa**, không phải đầu ra từ code chạy bên dưới. Các định danh trong ví dụ là dữ liệu giả. Nhờ những trường đó, người điều tra có thể tìm các sự kiện liên quan cùng request, kiểm tra đơn hàng cụ thể, phân nhóm theo mã lỗi và đối chiếu lỗi với dịch vụ phụ thuộc.

**Nguyên tắc chính:** *exception giải thích quá trình thất bại trong mã; context giải thích thất bại thuộc công việc nào*. Cần cả hai, nhưng không nên nhồi mọi dữ liệu có thể lấy được vào message hoặc log.

## 2. Bốn nhóm ngữ cảnh nên cân nhắc

Không phải mọi lỗi cần mọi trường. Hãy chọn ngữ cảnh có ích cho điều tra đúng với ranh giới và hợp đồng của thao tác.

| Nhóm | Trường điển hình | Câu hỏi trả lời | Lưu ý |
|---|---|---|---|
| Thao tác | `operation`, `event`, `stage`, `component` | Đang làm việc gì, ở bước nào? | Ưu tiên tên ổn định, có nghĩa nghiệp vụ |
| Liên kết yêu cầu | `requestId`, `correlationId`, `traceId`, `spanId` | Các log nào cùng một luồng nghiệp vụ? | Tránh sinh lại ID tùy tiện qua mỗi tầng |
| Đối tượng nghiệp vụ | `orderId`, `jobId`, `paymentAttemptId`, `messageId` | Đơn/tác vụ/lần thử nào bị tác động? | Dùng ID tối thiểu, có kiểm soát truy cập |
| Chẩn đoán kỹ thuật | `errorCode`, `dependency`, `attempt`, `elapsedMs`, `exception.type`, `cause` | Hỏng vì đâu, có thể tái hiện và thống kê không? | Phân biệt bằng chứng đo được và suy đoán |

Các trường nên có định nghĩa rõ. Chẳng hạn `orderId` chỉ ra **đơn hàng**, trong khi `requestId` chỉ ra **một request xử lý**; một đơn có thể được xử lý bởi nhiều request. `messageId` có thể được xử lý lại nhiều lần, nên `attempt` hoặc `jobExecutionId` giúp phân biệt từng lần thực thi.

### Tên thao tác: đủ chính xác nhưng ổn định

Nên dùng `operation=reserve_inventory`, `operation=charge_payment`, `operation=publish_order_event` thay vì các tên mơ hồ như `process`, `run` hoặc tên method dễ thay đổi do refactor. Một tên operation ổn định giúp:

- Tổng hợp số lỗi theo chức năng.
- So sánh log trước/sau các bản phát hành.
- Tạo truy vấn và cảnh báo không phụ thuộc stack trace.
- Đối chiếu các chặng khác nhau của cùng một quy trình.

Không đưa toàn bộ URL chứa token hoặc nguyên văn SQL có thông tin khách hàng vào tên operation. Sử dụng tên dạng mẫu (route/template) nếu cần mô tả endpoint.

## 3. Phân biệt request ID, correlation ID, trace ID và business ID

Những trường này liên quan nhưng **không đồng nghĩa**.

- **Request ID:** nhận diện một lần nhận request ở ranh giới hệ thống; hữu ích để tìm log của một request cụ thể.
- **Correlation ID:** liên kết nhiều message, request hoặc bước nằm trong cùng một quy trình nghiệp vụ. Có hệ thống dùng chung request ID làm correlation ID, nhưng cần một quy ước nhất quán.
- **Trace ID:** nhận diện một distributed trace; nhiều span thuộc cùng trace. Khi đã có công cụ tracing, nên dùng trace ID do tracing system cung cấp, không tự phát minh một ID khác rồi gọi là `traceId`.
- **Span ID:** nhận diện một đoạn công việc trong trace. Có thể thay đổi giữa các chặng; không dùng nó thay cho ID xuyên toàn bộ trace.
- **Business ID:** mã đơn hàng, giao dịch, job hoặc đối tượng bị tác động. Một đối tượng có thể xuất hiện trong nhiều request/trace khác nhau.
- **Attempt/Execution ID:** nhận diện một lần thử thực hiện công việc, đặc biệt quan trọng với retry, batch, queue và task định kỳ.

Ví dụ một payment attempt có thể có:

~~~text
traceId=4bf92f3577b34da6a3ce929d0e0e4736
requestId=req-e9b3
orderId=ord-321
paymentAttemptId=pat-02
attempt=2
~~~

Giá trị `traceId` ở đây chỉ là ví dụ định dạng hex, không phải ID trích từ một hệ thống thật.

**Không tự động tin định danh đến từ client.** Nếu HTTP header cung cấp request/correlation ID, hệ thống nên kiểm tra độ dài, ký tự cho phép, định dạng, chính sách tin cậy và tránh log injection. Khi không có ID hợp lệ, tạo ID phía server theo quy ước rồi truyền qua các tầng liên quan.

## 4. Context nằm ở đâu: tham số, ngoại lệ hay logging context?

Có ba cách thường được kết hợp tùy kiến trúc.

### 4.1. Truyền đối tượng context rõ ràng

~~~java
// Đoạn minh họa trong Java 17.
record OperationContext(
        String operation,
        String requestId,
        String orderId) {
}
~~~

Sau đó các tầng nhận `OperationContext context` như một tham số. Cách này thể hiện phụ thuộc rõ ràng, tương đối dễ test và phù hợp cả thread pool lẫn callback bất đồng bộ nếu lập trình viên thực sự truyền context theo tác vụ.

Ưu điểm: không phụ thuộc trạng thái ngầm của thread, dễ đọc hợp đồng. Nhược điểm: cần truyền qua nhiều lớp; không nên biến nó thành một đối tượng chứa mọi thứ hoặc kéo dữ liệu riêng tư vào nơi không cần thiết.

### 4.2. Gắn thêm thông tin có chọn lọc vào exception

Một exception domain có thể chứa error code và một vài ID cần thiết; khi chuyển lỗi qua tầng, nó vẫn cho caller biết thao tác liên quan:

~~~java
// Trích đoạn trong một lớp Java.
final class OrderWriteException extends RuntimeException {
    private final String errorCode;
    private final String orderId;

    OrderWriteException(String orderId, Throwable cause) {
        super("cannot save order", cause);
        this.errorCode = "ORDER_WRITE_FAILED";
        this.orderId = orderId;
    }

    String errorCode() {
        return errorCode;
    }

    String orderId() {
        return orderId;
    }
}
~~~

Đây là trích đoạn biên dịch được khi đặt vào một file/lớp thích hợp. Constructor `RuntimeException(String, Throwable)` giữ nguyên cause; trường typed giúp gọi chương trình phân loại mà không phải tách chuỗi message. Không đưa token, dữ liệu thanh toán hoặc payload lớn vào exception vì exception có thể lan sang log, telemetry hoặc response không kiểm soát.

### 4.3. Structured logging context / MDC

Các thư viện logging phổ biến, chẳng hạn SLF4J kết hợp backend có hỗ trợ `MDC` (Mapped Diagnostic Context), cho phép đặt các trường chung cho log trong phạm vi hiện tại. Đây là **khả năng của thư viện logging**, không phải API `MDC` có sẵn trong Java SE.

~~~java
// Minh họa cần SLF4J + logging backend thích hợp; không chạy chỉ bằng JDK.
try {
    org.slf4j.MDC.put("requestId", context.requestId());
    org.slf4j.MDC.put("operation", context.operation());
    service.execute(context);
} finally {
    org.slf4j.MDC.remove("requestId");
    org.slf4j.MDC.remove("operation");
}
~~~

MDC thường gắn với thread (tùy implementation), vì vậy phải **dọn trong finally** để tránh rò rỉ ID sang request kế tiếp được xử lý trên cùng thread pool. Trong luồng khác hoặc stage async, không nên giả định MDC tự truyền; cần capture/restore hoặc dùng cơ chế context propagation được framework hỗ trợ.

**Lựa chọn thực tế:** business/context object rõ ràng làm nguồn dữ liệu tin cậy; MDC hoặc structured logging là cách thuận tiện để xuất các trường vào nhiều dòng log ở một phạm vi đã kiểm soát.

## 5. Bọc ngoại lệ để thêm ngữ cảnh: không đánh mất cause

Một lỗi kỹ thuật có thể đi qua nhiều lớp:

~~~text
Repository: connection timeout
      ↓ cause
Application service: cannot persist order
      ↓ context
Request boundary: operation=place_order, requestId=..., orderId=...
~~~

Nếu chỉ ném:

~~~java
// Không nên làm như vậy nếu cần giữ nguyên nhân gốc.
throw new IllegalStateException("order failed");
~~~

thì tầng ngoài có thể mất khả năng thấy exception gốc, stack trace gốc và phân loại lỗi từ dependency.

Nên dùng:

~~~java
throw new IllegalStateException("order failed: " + orderId, originalCause);
~~~

Với dữ liệu nhạy cảm, thay vì ghép mọi giá trị vào message, giữ một số ID có kiểm soát trong trường typed và đưa vào structured log ở ranh giới. `Throwable.getCause()` cho phép đi ngược chuỗi lỗi, ví dụ `SQLException` → `RepositoryException` → `OrderWriteException`.

**Đừng bọc lại cùng một lỗi ở mọi tầng** mà không bổ sung ý nghĩa mới. Chuỗi năm lớp `RuntimeException("failed")` có thể làm log dài nhưng không thêm manh mối. Chỉ bọc ở ranh giới abstraction có sự chuyển đổi ngữ nghĩa, ví dụ từ lỗi JDBC sang lỗi lưu trữ đơn hàng.

### Lỗi dự kiến và lỗi bất ngờ

- Lỗi nghiệp vụ như hàng hết hoặc đơn đã đóng: một mã lỗi có cấu trúc và business ID có thể đủ; log mức WARN/INFO tùy chính sách và tần suất.
- Lỗi không dự kiến như vi phạm invariant, lỗi database hoặc coding bug: cần log mức ERROR khi tác động đủ nghiêm trọng, giữ stack trace/cause và context.
- Lỗi do timeout/cancellation: cần tách với failure thông thường; nếu task bị hủy đúng chính sách, ghi WARN/INFO có thể hợp lý hơn ERROR.
- Đừng dùng một mức log cố định chỉ dựa trên tên lớp exception, không xét tác động và kỳ vọng nghiệp vụ.

## 6. Chương trình Java 17 chạy độc lập: thêm context và giữ nguyên nhân gốc

Ví dụ này chỉ dùng Java SE, không phụ thuộc SLF4J để có thể biên dịch và chạy bằng `javac`. Ứng dụng production nên đưa các trường vào một structured logger thay vì nối tay chuỗi dài.

Lưu toàn bộ chương trình thành **`ExceptionContextDemo.java`**:

~~~java
import java.util.Objects;

public class ExceptionContextDemo {
    record OperationContext(
            String operation, String requestId, String orderId) {
        OperationContext {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(orderId, "orderId");
        }
    }

    static final class OrderWriteException extends RuntimeException {
        private final String errorCode;
        private final String orderId;

        OrderWriteException(String orderId, Throwable cause) {
            super("could not save order", cause);
            this.errorCode = "ORDER_WRITE_FAILED";
            this.orderId = orderId;
        }

        String errorCode() {
            return errorCode;
        }

        String orderId() {
            return orderId;
        }
    }

    static void persist() {
        throw new IllegalStateException("storage unavailable");
    }

    static void placeOrder(OperationContext context) {
        try {
            persist();
        } catch (IllegalStateException cause) {
            throw new OrderWriteException(context.orderId(), cause);
        }
    }

    static void handleRequest(OperationContext context) {
        try {
            placeOrder(context);
        } catch (OrderWriteException ex) {
            System.out.printf(
                    "level=ERROR operation=%s requestId=%s orderId=%s code=%s%n",
                    context.operation(), context.requestId(),
                    ex.orderId(), ex.errorCode());

            System.out.println("exception="
                    + ex.getClass().getSimpleName());

            Throwable cause = ex.getCause();
            System.out.println("cause="
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }

    public static void main(String[] args) {
        OperationContext context = new OperationContext(
                "place_order", "req-17", "ord-42");
        handleRequest(context);
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac ExceptionContextDemo.java
java ExceptionContextDemo
~~~

**Stdout chính xác:**

~~~text
level=ERROR operation=place_order requestId=req-17 orderId=ord-42 code=ORDER_WRITE_FAILED
exception=OrderWriteException
cause=IllegalStateException: storage unavailable
~~~

Tại sao ví dụ này có ích?

1. `OperationContext` lưu thông tin nhận diện thao tác ở dạng bất biến của Java record.
2. `persist()` ném lỗi kỹ thuật gốc. `placeOrder()` chuyển lỗi sang abstraction nghiệp vụ bằng `OrderWriteException` **và truyền `cause`**.
3. `handleRequest()` là ranh giới ghi nhận lỗi; nó có operation, request ID, order ID, error code, loại exception và nguyên nhân gốc.
4. Việc in ở ranh giới giúp tránh log toàn stack trace lặp lại ở từng tầng trong một request.
5. Đầu ra được tạo bằng giá trị cố định, không phụ thuộc thời gian, thread scheduler, tên máy hoặc logger implementation.

**Trong production:** ngoài những trường đang in, hãy truyền toàn bộ `Throwable` cho logging backend để giữ stack trace và chuỗi nguyên nhân. Ví dụ stdout cố ý chỉ hiển thị type/message giúp xác minh bài học với kết quả xác định, **không phải** lời khuyên bỏ stack trace khỏi log điều tra.

## 7. Structured logging có giá trị hơn ghép một chuỗi tự do

Một sự kiện log nên có các **field tách biệt** để hệ thống lưu trữ có thể filter, group, thống kê và liên kết.

Mẫu JSON minh họa (không phải output của chương trình):

~~~json
{
  "timestamp": "2026-10-19T03:00:00Z",
  "level": "ERROR",
  "service": "order-service",
  "event": "order_persist_failed",
  "operation": "place_order",
  "requestId": "req-17",
  "orderId": "ord-42",
  "errorCode": "ORDER_WRITE_FAILED",
  "dependency": "orders-db",
  "exception": {
    "type": "OrderWriteException",
    "causeType": "IllegalStateException"
  }
}
~~~

Giá trị timestamp và ID ở đây là giả định để minh họa schema, không phải dữ liệu thực. Hệ thống logging production thường tự bổ sung `timestamp`, `service`, `severity`, hostname và trace context; tránh làm mỗi team phát minh tên field khác nhau.

Lợi ích:

- Truy vấn `requestId = "req-17"` hoặc `errorCode = "ORDER_WRITE_FAILED"` đáng tin hơn tìm substring trong text.
- Nhóm lỗi theo code và operation mà không cần parse message.
- Dễ gắn với metrics/traces và dashboard.
- Hạn chế thay đổi truy vấn khi copywriting của message thay đổi.

Một log với `"order id req-17 failed"` không tốt bằng các field `orderId` và `requestId` riêng: hai ID có ngữ nghĩa khác và người điều tra không nên đoán bằng regex.

### Placeholder logging và Throwable

Nhiều framework logging có API parameterized logging, ví dụ SLF4J:

~~~java
// Minh họa: log là org.slf4j.Logger, context đã khai báo.
log.error("operation={} requestId={} orderId={} code={}",
        context.operation(), context.requestId(),
        context.orderId(), "ORDER_WRITE_FAILED", exception);
~~~

Đoạn trên **không phải code Java SE độc lập**: cần SLF4J và backend. Thứ tự tham số cuối cùng cần đúng với quy tắc API logger để backend nhận `exception` làm Throwable và ghi stack trace; trong thư viện/version khác hãy đọc chính xác overload hỗ trợ. Nếu backend hỗ trợ structured arguments/key-value pairs, ưu tiên chúng thay vì chỉ nhét giá trị vào chuỗi format.

Đừng dùng `ex.printStackTrace()` cùng với `log.error(..., ex)` một cách không kiểm soát: thường tạo bản sao stack trace trong hai kênh khác nhau.

## 8. Log một lần có trách nhiệm, không log mỗi lần catch

Một lỗi đi qua nhiều tầng có thể tạo ba bản log trùng:

~~~text
Repository: ERROR write failed
Service: ERROR order failed
Controller: ERROR request failed
~~~

Nếu cả ba đều in cùng stack trace, hệ thống có thể báo ba lỗi thay vì một và gây khó khăn khi xác định mức độ ảnh hưởng thật.

**Quy ước thực hành:**

- Tầng thấp **ném/bọc đúng ngữ nghĩa và giữ cause**.
- Tầng có quyền quyết định retry có thể log ở mức DEBUG/INFO cho mỗi attempt hoặc cập nhật metrics, tránh coi mỗi lần retry là lỗi cuối cùng.
- Tầng boundary chịu trách nhiệm phản hồi request/job/message nên log **một sự kiện failure cuối cùng đủ context** khi thực sự không phục hồi được.
- Trường hợp lỗi bị xử lý thành công bằng fallback, không nhất thiết log ERROR; nếu cần quan sát, log sự kiện recovery với mức phù hợp.
- Không áp dụng cứng “mỗi lỗi chỉ được log một lần” trong mọi kiến trúc: có thể có sự kiện riêng ở từng ranh giới quan trọng. Nhưng mỗi sự kiện phải bổ sung thông tin và mục đích mới, không nhân bản y hệt stack trace.

Người điều tra cần phân biệt một failure trải qua nhiều stage với nhiều failure độc lập. Dùng `requestId`/`traceId` và một `errorCode` ổn định hỗ trợ xác định quan hệ giữa các sự kiện.

## 9. Lựa chọn những định danh thực sự giúp điều tra

Các ID tốt cần thỏa mãn một số tiêu chí:

1. **Tìm được:** ID có thể dùng để truy vấn record/traces đúng quyền; ID ngẫu nhiên vô nghĩa và không lưu ở đâu thì ít giá trị.
2. **Ổn định theo phạm vi:** cùng một thao tác giữ ID xuyên qua các bước liên quan; retry có thể giữ business ID nhưng thay execution ID.
3. **Có tên rõ:** `orderId` không bị dùng để chứa `requestId`. Nếu một ID được sinh từ external provider, tên nên nêu namespace.
4. **Độ nhạy phù hợp:** có những ID vẫn là dữ liệu có thể liên kết tới người dùng; không phải ID nào cũng an toàn để log công khai.
5. **Độ dài và cardinality có kiểm soát:** đủ để phân biệt nhưng không biến nó thành tên metric label có cardinality cực lớn.
6. **Hợp đồng format:** nên chuẩn hóa `UUID`, ID từ tracing hoặc chuỗi theo một schema, có validation tại ranh giới.

### Request ID không thay business ID

Một request có thể cập nhật nhiều đơn; cùng một đơn cũng có thể trải qua nhiều request. Bởi vậy hai trường trả lời câu hỏi khác nhau:

~~~text
requestId=req-abc    orderId=ord-42     operation=create_order
requestId=req-def    orderId=ord-42     operation=cancel_order
~~~

Đây có thể là **hai thao tác hợp lệ khác nhau** trên cùng đơn. Nếu log chỉ có `orderId`, khó biết request nào gây lỗi; nếu chỉ có `requestId`, khó tìm toàn bộ lịch sử của đơn.

### Retry và định danh lần chạy

Khi một message được retry, hãy cân nhắc:

~~~text
messageId=msg-802 executionId=exec-1 attempt=1
messageId=msg-802 executionId=exec-2 attempt=2
~~~

ID message ổn định để gom mọi lần xử lý cùng nội dung; execution ID và attempt để phân tích một lần thất bại. Dù có `attempt=2`, không thể tự kết luận lần đầu không gây side effect: retry và idempotency phải được thiết kế riêng.

## 10. Truyền context qua CompletableFuture và thread pool

Một lỗi phổ biến là đặt request ID trong `ThreadLocal`/MDC ở thread HTTP, rồi dùng `CompletableFuture.supplyAsync(...)` trên executor khác và giả định context tự xuất hiện.

Trong thực tế, `ThreadLocal` gắn với thread cụ thể. Các task trong thread pool có thể chạy trên thread đã từng phục vụ request khác. Không tự truyền context có thể tạo log thiếu ID; không dọn MDC có thể tạo log **gắn nhầm ID khách hàng của request trước**.

Một cách độc lập framework là **truyền context như tham số bất biến**:

~~~java
// Mã minh họa trong phương thức có Executor executor,
// OperationContext context và phương thức repositorySave(context).
CompletableFuture<String> task =
        CompletableFuture.supplyAsync(() -> {
            try {
                repositorySave(context);
                return "saved";
            } catch (RuntimeException cause) {
                throw new OrderWriteException(context.orderId(), cause);
            }
        }, executor);
~~~

Đây là **trích đoạn, không phải chương trình độc lập**; các loại và biến phải được định nghĩa trong hệ thống. `context` được lambda capture có chủ đích, không phụ thuộc vào việc executor chọn thread nào.

Nếu dùng MDC:

1. Chụp những field cần thiết **ở trước ranh giới bất đồng bộ**.
2. Trong task, thiết lập MDC cho thời gian thực thi.
3. Dùng `try/finally` hoặc API scope tương ứng để restore giá trị trước đó (không chỉ clear toàn cục nếu một scope bên ngoài đang tồn tại).
4. Dọn context trên đường success, exception, cancellation.
5. Kiểm tra cách framework xử lý virtual threads, reactive chain hoặc structured context riêng của nó; không suy luận cơ chế truyền context chỉ từ tên thư viện.

### Future báo lỗi không tự mang toàn bộ context

Khi `future.get()` ném `ExecutionException` hoặc `future.join()` ném `CompletionException`, caller nhận wrapper và `cause` nhưng **không được bảo đảm nhận `ThreadLocal` của worker**. Vì vậy ID nghiệp vụ quan trọng nên được truyền rõ hoặc gắn vào exception/result có kiểm soát.

Nếu cần log thất bại tại ranh giới request, caller nên giữ `OperationContext` tương ứng với tác vụ, ví dụ map `requestId -> task` hoặc closure đi kèm future, thay vì suy đoán request ID từ tên thread.

## 11. Exception context trong các ranh giới khác

### HTTP request

Context hữu ích: `method`, route template, server-generated `requestId`, `operation`, authenticated subject ID đã được chuẩn hóa và được phép log, mã lỗi ứng dụng, latency, mã HTTP trả về. Không log nguyên auth header, cookie, token hoặc body tùy tiện.

Một ranh giới web có thể:

- Tạo hoặc xác minh correlation ID.
- Truyền ID xuống service/repository.
- Bắt lỗi ở boundary, map sang status/errorCode công khai.
- Log stack/cause trong kênh nội bộ, trả về response an toàn và `requestId` để hỗ trợ người dùng cung cấp cho support.

### Queue và event

Context hữu ích: `messageId`, `topic`, `partition`/`offset` (khi có), `consumerGroup`, `attempt`, business ID. Với retry, giữ một correlation ID xuyên suốt nhưng phân biệt execution attempt. Không bao giờ log toàn bộ payload nhạy cảm chỉ vì deserialization lỗi.

### Batch/cron job

Context hữu ích: `jobName`, `jobRunId`, `batchId`, `recordId`, `step` và số thứ tự nếu cần. Nếu một record trong batch lỗi, log bằng `recordId` (hợp lệ và an toàn) giúp truy tìm bản ghi cụ thể; `jobRunId` giúp tổng hợp một đợt chạy.

### Database và các dịch vụ ngoài

Context hữu ích: tên dependency ổn định, loại operation, server-side request ID của provider (nếu cho phép), retry attempt, elapsed time và mã lỗi chuẩn. Hạn chế ghi SQL đầy đủ có dữ liệu nội tuyến; dùng query name/template hoặc operation code. Không in connection string chứa password.

## 12. Bảo vệ dữ liệu nhạy cảm và chống log injection

Context hữu ích không đồng nghĩa **càng nhiều càng tốt**. Log được sao chép sang nhiều hệ thống lưu trữ, truy vấn và cảnh báo; nó có thể được xem bởi nhiều người hơn hệ thống nghiệp vụ nguồn.

**Không ghi vào log nếu không có cơ sở nghiệp vụ, quyền và biện pháp bảo vệ phù hợp:**

- Mật khẩu, API key, access token, refresh token, session cookie.
- Dữ liệu thẻ thanh toán đầy đủ, mã xác minh thẻ, bí mật xác thực.
- Nội dung giao dịch hoặc thông tin sức khỏe, địa chỉ, email, số điện thoại khi không thực sự cần.
- Raw request/response chứa thông tin cá nhân hoặc bí mật.
- Toàn bộ header, URL query string hoặc nội dung exception từ dịch vụ ngoài khi có thể chứa token.

Các biện pháp:

1. **Allowlist field:** quyết định trước trường nào được log, thay vì log toàn bộ object, `toString()` hoặc HTTP payload.
2. **Redaction:** che giá trị nhạy cảm trước khi chuyển cho logger; chú ý cả message và cause từ thư viện bên ngoài.
3. **Validation & encoding:** giới hạn chiều dài, kiểm tra ký tự ID do client cung cấp; xử lý newline/control characters để giảm nguy cơ chèn dòng log giả.
4. **Retention & access control:** xác định nơi lưu log, thời hạn lưu, quyền xem và đường xuất dữ liệu.
5. **Hash/pseudonymize khi thích hợp:** nếu cần liên kết sự kiện mà không hiện định danh nguyên bản, dùng cách biến đổi được bảo vệ phù hợp với chính sách; không mặc định hash thông thường biến thông tin thành vô danh.
6. **Kiểm thử:** đưa vào input chứa newline, control chars, chuỗi dài và nội dung nhạy cảm giả định để kiểm tra sự kiện log vẫn an toàn.

**Lưu ý:** ngay cả một `userId` hoặc `orderId` hợp lệ có thể trở thành dữ liệu liên kết cá nhân khi ghép với hệ thống khác. Đừng đánh đồng “không có tên người” với “không nhạy cảm”.

## 13. Phân biệt message, error code và ID

Một lỗi hữu ích thường có ba lớp thông tin khác nhau:

- **Exception message:** mô tả ngắn tình huống, có thể thay đổi để dễ hiểu hơn. Không dùng text message làm API machine-readable.
- **Error code:** mã ổn định mà hệ thống dùng để phân loại, ví dụ `ORDER_WRITE_FAILED` hoặc `INVENTORY_NOT_AVAILABLE`. Có thể tài liệu hóa cho caller hoặc người vận hành.
- **Identifiers:** request/business/job IDs để tìm sự kiện và dữ liệu liên quan.

Ví dụ:

~~~text
message=could not save order
errorCode=ORDER_WRITE_FAILED
requestId=req-17
orderId=ord-42
~~~

Nếu đổi message thành `order persistence failed`, truy vấn theo `errorCode` vẫn có thể giữ nguyên. Nếu mỗi exception tạo mã ngẫu nhiên thay cho error code ổn định, hệ thống không thể tổng hợp lỗi cùng loại.

Một ID điều tra như `incidentId` có thể giúp support tìm lỗi cụ thể; nhưng nếu không có ánh xạ tới log hoặc trace thực, việc hiển thị incident ID cho người dùng cũng chưa giải quyết được vấn đề.

## 14. Mức log và thời điểm ghi context

**Mức log** nên phản ánh tính kỳ vọng và tác động, không chỉ việc có exception.

| Tình huống | Mức thường cân nhắc | Context ưu tiên |
|---|---|---|
| Input không hợp lệ được API trả 400 bình thường | INFO/WARN hoặc không log từng yêu cầu nếu đã có metrics | route/operation, requestId, errorCode |
| Trùng message nhưng idempotency xử lý được | INFO/DEBUG | messageId, operation, attempt |
| Retry tạm thời do dependency quá tải | WARN/DEBUG cho attempt, ERROR cho failure cuối | dependency, retry count, correlationId |
| Lỗi lưu dữ liệu chưa thể phục hồi | ERROR | operation, businessId, requestId, cause |
| Cancellation có chủ đích | DEBUG/INFO tùy hệ thống | taskId, cancel reason đã chuẩn hóa |
| Vi phạm invariant hoặc lỗi bất ngờ | ERROR | operation, IDs, stack/cause, version |
| Quá hạn/hết thời gian chờ | WARN/ERROR theo tác động | dependency, elapsedMs, timeout policy, attempt |

Tránh log lỗi đầu vào bình thường ở mức ERROR cho mọi request: một đợt traffic không hợp lệ có thể khiến alert bị nhiễu và log volume cao. Ngược lại, không được giảm mức log của lỗi có nguy cơ mất dữ liệu chỉ vì nó được biểu diễn bằng unchecked exception.

### Tỷ lệ log và cardinality

Các ID có cardinality cao như `requestId` và `orderId` rất hữu ích trong **log/traces** để truy vấn một lần thực thi. Tuy nhiên, không nên dùng chúng làm label dimensions mặc định cho metrics vì tạo vô số time series. Metric tốt có thể nhóm theo `operation`, `errorCode` và `dependency`; khi cần điều tra một sự kiện cá biệt, tra từ metric sang log/tracing có ID chi tiết.

## 15. Bảo toàn ngữ cảnh khi retry, timeout và cancellation

Một lỗi có thể được retry:

~~~text
operation=save_order orderId=ord-42 attempt=1 outcome=retry
operation=save_order orderId=ord-42 attempt=2 outcome=success
~~~

Kết quả cuối là thành công, nhưng attempt đầu vẫn thất bại. Không đếm attempt failure là request failure cuối cùng nếu người dùng không bị ảnh hưởng. Một cảnh báo cuối nên ghi tổng số lần thử và nguyên nhân cuối nếu tất cả retry hết.

Với timeout:

- `TimeoutException` cho biết một giới hạn chờ bị vượt; nó **không chứng minh** thao tác ở dependency đã dừng hoặc chưa commit.
- Cần giữ `requestId`, `operation`, tên dependency, `elapsedMs` và các ID của request gửi sang provider nếu có.
- Nếu caller retry sau timeout, dùng idempotency key hoặc cơ chế điều tra trạng thái theo domain để tránh ghi trùng side effect.

Với cancellation/interruption:

- `CancellationException` và `InterruptedException` nên được phân biệt với lỗi nghiệp vụ.
- Nếu catch `InterruptedException` mà không thể truyền tiếp, thường cần `Thread.currentThread().interrupt()`, đồng thời ghi nhận ID tác vụ và kết thúc theo chính sách hủy.
- Đừng log ERROR một tác vụ bị hủy có chủ đích chỉ vì nó phát sinh exception; nhưng nếu hủy để lại dữ liệu sai, đó là một failure khác cần điều tra.

## 16. Điều tra từ một error event như thế nào?

Khi một sự kiện failure đủ context được ghi, quy trình thực hành có thể là:

1. **Khoanh vùng một sự kiện:** tìm theo `requestId`, `jobRunId` hoặc ID mà người dùng/support cung cấp.
2. **Xác định operation/stage:** xem hệ thống đang làm bước nào, dependency nào được gọi, có bao nhiêu retry/attempt.
3. **Đọc chuỗi exception:** phân biệt exception ở ranh giới ứng dụng với cause kỹ thuật, xem stack trace và suppressed exceptions khi có.
4. **Đối chiếu trace:** dùng `traceId` để xem luồng xuyên dịch vụ, tìm span có lỗi hoặc độ trễ bất thường.
5. **Kiểm tra business state:** dùng `orderId`/`jobId` theo cơ chế truy vấn có quyền để xác định dữ liệu đã commit hay chưa.
6. **Xem tính lặp lại:** group theo errorCode, operation, release/version, dependency và thời gian.
7. **Xác định hành động:** retry có an toàn không, cần compensation không, có ảnh hưởng nhiều khách hàng không.
8. **Ghi nhận kết luận:** phát hiện nguyên nhân gốc, chính sách sửa, test hồi quy và điều chỉnh thông tin log cần thiết.

**Một log chưa đủ để khẳng định nguyên nhân cuối cùng** chỉ vì chứa tên một dependency. Một `SQLException` có thể do cấu hình, network, pool hết kết nối hoặc DB quá tải. Context giúp đặt đúng câu hỏi; cần đối chiếu bằng chứng từ metrics/traces và trạng thái thực.

## 17. Các sai lầm phổ biến

1. **Chỉ log `ex.getMessage()`:** mất stack, cause và ngữ cảnh thao tác.
2. **Chỉ log stack trace:** thấy method lỗi nhưng không biết request hoặc đối tượng nghiệp vụ nào.
3. **Tự sinh request ID mới ở mỗi tầng:** làm đứt liên kết chuỗi sự kiện.
4. **Dùng một field chung `id`:** không biết đó là request, order hay message.
5. **Bọc exception rồi bỏ cause:** xóa dấu vết kỹ thuật gốc.
6. **Log y hệt cùng một exception ở mọi catch:** nhân bản sự kiện, tăng volume và dễ tạo alert trùng.
7. **Truyền toàn bộ DTO/object vào logger:** vô tình ghi dữ liệu nhạy cảm; `toString()` có thể đổi và gây rò rỉ.
8. **Dùng MDC nhưng không cleanup:** request tiếp theo trên cùng thread pool có thể bị gắn nhầm ID.
9. **Cho rằng context trong ThreadLocal tự xuyên qua CompletableFuture:** thiếu hoặc sai context khi đổi thread.
10. **Dùng ID khách hàng làm metric label:** cardinality quá cao.
11. **Coi error message là error code ổn định:** việc đổi câu chữ làm hỏng dashboard và phân loại lỗi.
12. **Trả stack trace nguyên vẹn cho HTTP client:** lộ thông tin triển khai, đường dẫn nội bộ hoặc bí mật; nên trả error code và request ID an toàn.
13. **Tin ID đến từ header mà không validation:** nguy cơ chuỗi quá dài, ký tự điều khiển hoặc log injection.
14. **Log toàn bộ URL/header/token trong tình huống lỗi:** tăng rủi ro bảo mật dữ liệu.
15. **Gắn `orderId` vào lỗi chưa xác định được order:** tạo ngữ cảnh sai; dùng null/omit field có tài liệu thay vì bịa ID.
16. **Hiểu timeout/cancel là chưa commit:** cần xác minh trạng thái nghiệp vụ chứ không dựa vào loại exception.

## 18. Cách kiểm thử khả năng điều tra của exception log

Một bộ test log nên kiểm tra **trường dữ liệu** thay vì so khớp nguyên chuỗi log có timestamp động:

| Kịch bản | Kiểm tra |
|---|---|
| Lỗi kỹ thuật được bọc bằng exception miền nghiệp vụ | `cause` không null và đúng loại; `errorCode` ổn định |
| Request thất bại ở service | Sự kiện có operation, requestId và business ID hợp lệ |
| Gọi API với ID thiếu hoặc quá dài | Validation/fallback ID hoạt động, không phá format log |
| Input chứa newline/control characters | Không thể làm giả một dòng/sự kiện log mới |
| Logging qua thread pool | Context không bị lẫn giữa hai task |
| Tác vụ CompletableFuture thất bại | Stage/boundary giữ đúng context và cause |
| Retry nhiều lần rồi thành công | Phân biệt attempt errors và outcome cuối |
| Timeout/hủy task | Có trạng thái phù hợp; không log sai “đã rollback” |
| Dữ liệu nhạy cảm trong exception từ dependency | Logger/redaction không để lộ secret ở message/cause |
| Failure gây nhiều tầng exception | Không tạo vô số bản log ERROR trùng cùng một nguyên nhân |

Đối với structured logger, nên dùng test appender hoặc collector thử nghiệm có khả năng đọc từng field và Throwable. Với chương trình Java SE đơn giản, có thể capture `System.out` để kiểm tra ví dụ, nhưng production không nên thay thế công cụ logging bằng print statement.

Một test tốt cho log context cần có **hai request khác nhau trên cùng executor một luồng**: mỗi task giữ đúng request ID, task sau không nhìn thấy ID task trước. Đây là cách phát hiện MDC leak mà test một request không nhìn thấy.

## 19. Checklist thiết kế logging cho một thao tác Java

Trước khi hoàn thành một API hoặc tác vụ nền, rà soát:

- **Tên operation:** ổn định, không mơ hồ và đúng ranh giới nghiệp vụ.
- **Correlation:** request/correlation/trace ID được tạo hoặc xác minh tại ingress rồi truyền nhất quán.
- **Business identity:** chỉ những ID thực sự giúp tra cứu, không nhồi toàn bộ payload.
- **Error classification:** có `errorCode` đủ ổn định, phân biệt failure cuối, retry, timeout và cancel.
- **Root cause:** exception được bọc bằng constructor có `cause`; log truyền Throwable cho backend.
- **Logging boundary:** tránh nhiều lớp cùng in một stack trace mà không thêm ý nghĩa.
- **Async propagation:** context được capture và truyền đúng qua executor/future; MDC được restore/cleanup.
- **Data minimization:** không log token, bí mật hoặc PII ngoài phạm vi cho phép; ID đầu vào được validate.
- **Diagnostic usefulness:** từ một dòng log có thể tìm request/trace và trạng thái đối tượng liên quan.
- **Verification:** test thành phần log, race/retry, input bất thường và trường hợp nhạy cảm.

## 20. Ghi nhớ cốt lõi

**Thêm ngữ cảnh thao tác và định danh có ích cho điều tra** là tạo đủ dấu vết để biến một exception riêng lẻ thành một sự kiện có thể tìm kiếm, liên kết và giải thích. Tối thiểu thường gồm **operation**, **request/correlation ID**, **business ID nếu có**, **error code** và **Throwable/cause** được giữ nguyên. Với tác vụ bất đồng bộ, cần truyền context qua ranh giới thread một cách có chủ đích.

Giá trị của log không được đo bằng số trường tối đa, mà bằng khả năng trả lời **đúng việc gì thất bại, ở đâu, cho đối tượng nào, do nguyên nhân nào và trạng thái hiện tại là gì**, trong khi vẫn giữ bảo mật, giới hạn cardinality và tránh thông tin sai lệch.

### Tham khảo

- [Java SE 17 — Throwable, cause và suppressed exceptions](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html)
- [Java SE 17 — java.util.logging.Logger](https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/Logger.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html)
- [SLF4J Manual — MDC and parameterized logging](https://www.slf4j.org/manual.html)
- [OpenTelemetry — Trace and span concepts](https://opentelemetry.io/docs/concepts/signals/traces/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-007">Chọn tầng chịu trách nhiệm ghi log để tránh lặp cùng một lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn tầng chịu trách nhiệm ghi log để tránh lặp cùng một lỗi trong Java

Một ngoại lệ có thể đi qua nhiều tầng: repository → service → controller → global exception handler. Nếu **mỗi tầng đều ghi `ERROR` kèm toàn bộ stack trace rồi ném tiếp**, một thất bại nghiệp vụ sẽ tạo nhiều sự kiện log giống nhau. Log trở nên nhiễu, tốn chi phí lưu trữ, khó đếm số sự cố thực và có thể khiến hệ thống cảnh báo nhiều lần cho cùng một request.

Bài này thuộc **Java Core – Exception / Chẩn đoán và ghi log**. Mục tiêu là xác định **tầng nào sở hữu quyết định xử lý lỗi và chịu trách nhiệm ghi log**, đồng thời giữ thông tin nguyên nhân xuyên các tầng. Ví dụ thuần JDK chạy trên **Java 17+**; các đoạn có `logger` là mã tích hợp minh họa cho logging framework.

## 1. Nguyên tắc chính: ghi log tại nơi chịu trách nhiệm xử lý kết quả

Hãy bắt đầu bằng câu hỏi: **“Tầng nào có đủ ngữ cảnh và thực sự quyết định điều gì xảy ra với thất bại này?”**

- Nếu một tầng **không giải quyết được** lỗi và chỉ truyền tiếp, thông thường **giữ cause, bổ sung ngữ cảnh có ích rồi ném tiếp; không ghi một stack trace `ERROR` tại đây**.
- Nếu một tầng **thật sự xử lý/phục hồi** lỗi và không còn truyền nó như lỗi tới caller, tầng đó là nơi có thể cần một log phù hợp (`DEBUG`, `INFO`, `WARN` hoặc `ERROR` tùy tác động).
- Nếu lỗi **vượt đến ranh giới đầu cuối** và request/job/message bị đánh dấu thất bại, ranh giới xử lý cuối cùng thường là nơi **ghi một sự kiện lỗi giàu ngữ cảnh**, sau đó trả mã lỗi hoặc hoàn tất xử lý.
- Nếu một thành phần khác đã chắc chắn ghi log thất bại cuối cùng (ví dụ framework exception resolver, job runner), cần biết **hợp đồng log sẵn có** để không tạo bản sao nữa.

“Ghi log một lần” ở đây là **một sự kiện lỗi chính cho một lần thất bại trong một phạm vi sở hữu**, thường là một request, một job hoặc một thao tác nghiệp vụ. Đây không phải lệnh cấm mọi log ở các tầng khác: log tiến trình, metric, cảnh báo về phục hồi, audit có mục đích, hay thông tin khác ngữ nghĩa vẫn có giá trị.

Một sơ đồ xử lý:

```text
Request R-100
   │
   ▼
Controller ───────────────┐
   │                     │
   ▼                     │
Service                  │
   │                     │
   ▼                     │
Repository               │
   └── ném IOException   │
          │              │
          ├─ Repository: giữ nguyên lỗi; không ERROR trùng
          ▼              │
      Service: thêm ngữ cảnh bằng cause; không ERROR trùng
          │              │
          ▼              │
     Controller / global handler
          │
          ├─ ghi một ERROR + Throwable + requestId
          └─ trả kết quả lỗi thích hợp
```

Điều cần tối ưu không phải “ít log nhất” mà là **mỗi log có một mục đích và một chủ sở hữu rõ ràng**.

## 2. Vì sao “log rồi throw” dễ gây trùng?

Giả sử các tầng đều làm như sau:

```java
try {
    repository.load();
} catch (IOException e) {
    logger.error("Repository failed", e);
    throw e;
}
```

Đây là **mảnh mã minh họa**, giả định `repository` và `logger` có sẵn. Nếu service lại:

```java
try {
    repository.load();
} catch (IOException e) {
    logger.error("Service failed", e);
    throw new IllegalStateException("Không tải được hồ sơ", e);
}
```

và controller/global handler thêm `logger.error("Request failed", e)`, một lỗi I/O đã thành **ba stack trace**. Các log có thể khác câu chữ nhưng vẫn cùng một failure chain, khiến người vận hành tưởng có ba sự cố độc lập.

Hệ quả thường gặp:

1. **Sai số đếm lỗi:** dashboard đếm log `ERROR` thấy ba dòng trong khi chỉ một request thất bại.
2. **Khó phân nhóm:** cùng lỗi hiện diện với nhiều message và wrapper exception.
3. **Tăng chi phí:** ba stack trace dài, đặc biệt nếu hệ thống log tập trung tính phí theo dữ liệu ingest.
4. **Nhiễu cảnh báo:** rule theo số lượng `ERROR` báo động nhanh hơn số lượng request lỗi thực tế.
5. **Lộ dữ liệu nhiều nơi:** mỗi bản sao có thể chứa stack, message, tham số hoặc thông tin nhạy cảm.
6. **Khó đọc timeline:** log trùng làm che các sự kiện khác thực sự hữu ích trước thời điểm thất bại.

Nguyên tắc thực hành thường được diễn đạt là **“log or throw”**: nếu tầng hiện tại phải ném tiếp, hãy để tầng chịu trách nhiệm sau ghi log. Đây là nguyên tắc thiết kế mặc định, **không phải quy tắc tuyệt đối**; trường hợp bảo mật, audit, chuẩn vận hành hoặc ranh giới hệ thống có thể cần sự kiện bổ sung với ý nghĩa khác biệt.

## 3. Tầng thấp: truyền ngoại lệ và giữ `cause`

Một repository thường biết rằng việc đọc dữ liệu thất bại nhưng chưa chắc biết request cuối cùng sẽ thất bại: service có thể thử cache, retry hoặc chọn nguồn dự phòng. Vì vậy repository nên **trả lỗi đúng hợp đồng** để tầng có đủ ngữ cảnh quyết định.

Ở các tầng trung gian, có hai lựa chọn chính:

- **Truyền nguyên ngoại lệ:** khi kiểu exception và hợp đồng API cho phép.
- **Bọc ngoại lệ và giữ cause:** khi cần chuyển từ lỗi kỹ thuật sang ngữ cảnh nghiệp vụ của tầng hiện tại.

Ví dụ:

```java
try {
    return dataSource.read();
} catch (IOException e) {
    throw new IllegalStateException("Không nạp được cấu hình của khách hàng", e);
}
```

Đây là **mảnh mã minh họa** bên trong phương thức có kết quả phù hợp, không phải chương trình độc lập. Constructor `IllegalStateException(message, e)` giữ liên kết `getCause()` để handler ngoài cùng vẫn nhìn được `IOException`.

Không nên bọc kiểu này:

```java
throw new IllegalStateException("Không nạp được cấu hình");
```

nếu đó là hành động thay thế cho một ngoại lệ vừa bắt, vì như vậy **làm mất cause**. Cũng không nên tạo wrapper ở mọi bước mà không thêm ngữ cảnh thực sự; một chuỗi wrapper quá sâu khiến stack khó đọc.

**Mục tiêu:** giữ được nguyên nhân và ngữ cảnh cần thiết để **một điểm ghi log cuối cùng** có thể cung cấp đầy đủ câu chuyện lỗi.

## 4. Ví dụ Java 17 chạy độc lập: một lỗi, một điểm ghi nhận

Tạo tệp `SingleBoundaryLogDemo.java`:

```java
import java.io.IOException;

public class SingleBoundaryLogDemo {
    static String readProfile() throws IOException {
        throw new IOException("database unavailable");
    }

    static String loadProfile() {
        try {
            return readProfile();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "không tải được hồ sơ", e);
        }
    }

    static String controller() {
        return loadProfile();
    }

    public static void main(String[] args) {
        try {
            controller();
        } catch (IllegalStateException failure) {
            // Minh họa sự kiện log tại ranh giới request.
            System.out.println(
                    "ERROR requestId=R-100 operation=profile.load");
            System.out.println("outer = "
                    + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());

            Throwable cause = failure.getCause();
            System.out.println("cause = "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SingleBoundaryLogDemo.java
java SingleBoundaryLogDemo
```

Output chính xác:

```text
ERROR requestId=R-100 operation=profile.load
outer = IllegalStateException: không tải được hồ sơ
cause = IOException: database unavailable
```

`readProfile()` phát sinh một `IOException`, `loadProfile()` bọc thêm ngữ cảnh **mà không tự ghi log**, `controller()` truyền tiếp. `main()` đóng vai trò **ranh giới request**, chịu trách nhiệm ghi một sự kiện cuối cùng. Các lệnh `System.out` chỉ nhằm cho đầu ra minh họa dễ kiểm tra; **ứng dụng production nên dùng logging framework** và truyền chính đối tượng Throwable để giữ stack trace.

Nếu dùng API logging kiểu SLF4J tại ranh giới, câu lệnh tương ứng có thể là:

```java
logger.error(
        "Không tải được hồ sơ, requestId={}",
        requestId,
        failure);
```

Đây là **mã tích hợp**, giả định `logger` tương thích với SLF4J và `requestId`, `failure` đã tồn tại. Trong một cấu hình logging thông dụng, đối tượng Throwable ở đối số cuối sẽ được in cùng stack trace và cause. Cần kiểm tra overload cụ thể của API/logger đang dùng; không dựa vào việc ghép `failure.getMessage()` để thay thế Throwable.

## 5. Tầng nào là ranh giới xử lý lỗi?

Tùy loại ứng dụng, “tầng cuối” có thể là:

| Ứng dụng | Điểm chịu trách nhiệm thường gặp | Kết quả xử lý |
|---|---|---|
| HTTP/REST | Exception handler toàn cục hoặc HTTP filter được giao quyền | Phản hồi HTTP hợp lệ, mã lỗi/correlation ID, ghi nhận request thất bại |
| Batch/CLI | Job runner hoặc entry point `main()` | Ghi kết quả job thất bại, exit status/chính sách retry |
| Consumer queue | Handler tại ranh giới tiêu thụ message | Quyết định ack/nack, retry, dead-letter, log lần thất bại cuối |
| Scheduler | Khung thực thi của scheduled job | Xác định task failure và theo dõi lịch chạy |
| Executor/Future | Thành phần **quan sát kết quả** `get()`/`join()` hoặc callback quản lý lỗi | Đọc cause, quyết định retry/hủy/fallback và log |
| Thư viện dùng lại | Thường **không** sở hữu kết quả cuối của ứng dụng | Ném exception có ý nghĩa, giữ cause; không tự log ERROR cho mọi caller |

Đây là **các vị trí thường gặp**, không phải tên lớp bắt buộc. Một framework có thể đã tự log exception chưa được xử lý; nhiều deployment còn có service mesh, tracing hoặc centralized error reporting. Trước khi tự thêm logger mới, phải xác định ai đã ghi sự kiện thất bại.

### Ví dụ Spring MVC (mảnh mã tích hợp)

Trong ứng dụng Spring Web có thể chọn một `@RestControllerAdvice` tập trung việc chuyển lỗi không mong đợi sang phản hồi chuẩn:

```java
@RestControllerAdvice
class ApiExceptionHandler {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handle(Exception error,
                                    HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        LOG.error("Unhandled request failure, requestId={}",
                  requestId, error);

        return ResponseEntity.status(500)
                .body(new ApiError("INTERNAL_ERROR", requestId));
    }
}
```

Đây là **ví dụ kiến trúc, không chạy độc lập**: cần Spring MVC, Jakarta Servlet, import tương ứng và record/class `ApiError`. Đoạn này cố ý chỉ minh họa **vị trí ownership**; trong thực tế cần phân loại 4xx/5xx, lấy request ID từ nguồn đáng tin (không tin mù quáng header do client nhập), quản lý security và tránh để framework hoặc filter khác ghi cùng stack trace lần nữa.

Không nên đưa nguyên `error.getMessage()` và stack trace của lỗi nội bộ vào HTTP response. Phản hồi cho client nên có mã lỗi an toàn cùng correlation ID; thông tin chi tiết thuộc hệ thống log được kiểm soát quyền.

## 6. Nếu tầng trung gian xử lý được lỗi, log tại đó có thể đúng

Một ngoại lệ **đã được xử lý và không truyền lên** không còn là request failure. Ví dụ remote API tạm lỗi nhưng service có cache hợp lệ. Chính service **sở hữu quyết định fallback**, nên có thể ghi một `WARN` hoặc metric về degraded mode; controller nhận dữ liệu thành công và không nên ghi `ERROR` cho cùng nguyên nhân.

Tạo tệp `RecoveredFailureLogDemo.java`:

```java
import java.io.IOException;

public class RecoveredFailureLogDemo {
    static String remoteName() throws IOException {
        throw new IOException("upstream unavailable");
    }

    static String loadName() {
        try {
            return remoteName();
        } catch (IOException e) {
            // Service quyết định phục hồi bằng dữ liệu cache.
            System.out.println(
                    "WARN fallback=cache reason=upstream_unavailable");
            return "Tên trong cache";
        }
    }

    public static void main(String[] args) {
        System.out.println("response = " + loadName());
    }
}
```

Chạy:

```bash
javac RecoveredFailureLogDemo.java
java RecoveredFailureLogDemo
```

Kết quả:

```text
WARN fallback=cache reason=upstream_unavailable
response = Tên trong cache
```

Ở đây `main` không nhận exception và không cần ghi thêm `ERROR`. Tầng service thực sự **đổi kết quả từ failure sang success có suy giảm chất lượng**, vì vậy log ở đó mang ý nghĩa khác: hệ thống đã dùng fallback. Với lỗi remote thường xuyên và có thể phục hồi, metric `fallback_count` hoặc log có lấy mẫu còn hiệu quả hơn hàng nghìn stack trace giống nhau.

Tùy nghiệp vụ, lỗi được phục hồi có thể không cần log ở mức `WARN`: một cache miss thông thường, `FileNotFoundException` dự kiến hoặc tín hiệu đóng worker thường nên là `INFO`/`DEBUG` hoặc không log. **Mức độ log phụ thuộc tính bất thường và tác động**, không chỉ phụ thuộc việc có exception.

## 7. Một thất bại được thử lại nhiều lần: đếm attempt khác đếm operation

Retry làm bài toán khó hơn: **một operation** có thể gồm nhiều **attempt**. Nếu mỗi attempt đều log `ERROR` kèm stack trace rồi tầng ngoài cùng cũng log tổng thất bại, dashboard dễ nhầm bốn sự cố thành một.

Phân biệt:

- **Attempt failure:** một lần thử bị lỗi, nhưng policy có thể thử tiếp. Có thể ghi metric `retry_attempts`, `DEBUG` hay log ngắn nếu cần điều tra.
- **Operation failure:** sau khi hết số lần thử, toàn bộ thao tác không đạt mục tiêu. Đây thường là nơi ghi `ERROR` cuối cùng, có đủ số attempt và cause cuối.
- **Operation success after retry:** không nên có `ERROR` “không thể hoàn thành” cho thao tác rốt cuộc đã thành công; có thể ghi metric hoặc `INFO/WARN` về độ trễ và việc sử dụng retry.

### Ví dụ độc lập: ba attempt, một log lỗi cuối

Tệp `RetryOnceLogDemo.java`:

```java
import java.io.IOException;

public class RetryOnceLogDemo {
    static String attemptRead(int attempt) throws IOException {
        throw new IOException("upstream unavailable");
    }

    static String readWithRetry() {
        IOException last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return attemptRead(attempt);
            } catch (IOException e) {
                last = e;
                // Ghi metric retry tại đây nếu cần, không ERROR mỗi lần.
            }
        }

        throw new IllegalStateException(
                "thất bại sau 3 lần thử", last);
    }

    public static void main(String[] args) {
        try {
            readWithRetry();
        } catch (IllegalStateException failure) {
            System.out.println(
                    "ERROR operation=remote.read attempts=3");
            System.out.println("error = " + failure.getMessage());
            System.out.println("cause = "
                    + failure.getCause().getClass().getSimpleName()
                    + ": " + failure.getCause().getMessage());
        }
    }
}
```

Lệnh:

```bash
javac RetryOnceLogDemo.java
java RetryOnceLogDemo
```

Output:

```text
ERROR operation=remote.read attempts=3
error = thất bại sau 3 lần thử
cause = IOException: upstream unavailable
```

Tác vụ được thử ba lần, nhưng log `ERROR` cho operation chỉ xuất hiện **một lần** ở cuối. Đoạn mẫu cố ý không ngủ để output dễ kiểm tra; retry production cần giới hạn attempt, timeout, exponential backoff/jitter phù hợp, kiểm tra lỗi nào thật sự có thể retry và **tính idempotent** để tránh lặp tác dụng phụ (ví dụ gửi thanh toán hai lần).

Nếu cần truy vết chi tiết từng attempt, hãy ghi `DEBUG` hoặc metric với `attempt`, `operationId`, kết quả phân loại; **không nhất thiết lặp toàn bộ stack trace**. Các attempt là sự kiện kỹ thuật riêng có ích, không phải ba operation failure độc lập.

## 8. Bất đồng bộ: ai quan sát và ai log exception?

Với `ExecutorService.submit(...)`, lỗi từ `Callable` thường được giữ trong `Future`. Nếu không ai gọi `get()`, lỗi có thể bị bỏ sót. Nếu nhiều component cùng đọc và log cùng một Future, lỗi có thể bị nhân bản.

Các điểm cần phân biệt:

- `Future.get()` có thể ném `ExecutionException`; **cause** nằm ở `getCause()`.
- `CompletableFuture.join()` thường báo exceptional completion qua `CompletionException`; cần xem cause bên dưới.
- `CompletableFuture.whenComplete(...)` thích hợp ghi nhận/quan sát kết quả mà không thay giá trị, nhưng **nếu vừa log ở callback vừa log tại `join()` của caller**, dễ trùng.
- `CompletableFuture.exceptionally(...)` và `handle(...)` có thể phục hồi lỗi thành kết quả bình thường; khi đó callback phục hồi có thể là nơi ghi nhận degraded mode, còn caller không ghi thêm operation failure.
- `cancel(...)`, `InterruptedException` và `CancellationException` là tín hiệu vòng đời quan trọng; không tự động xem là `ERROR`.

### Ví dụ độc lập: log tại nơi quan sát stage cuối

Tạo tệp `AsyncBoundaryLogDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AsyncBoundaryLogDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> parsed =
                CompletableFuture.completedFuture("abc")
                        .thenApply(Integer::parseInt);

        CompletableFuture<Integer> processed =
                parsed.thenApply(value -> value + 1);

        try {
            System.out.println(processed.join());
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            System.out.println(
                    "ERROR operation=parseAndIncrement");
            System.out.println("cause = "
                    + cause.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac AsyncBoundaryLogDemo.java
java AsyncBoundaryLogDemo
```

Kết quả:

```text
ERROR operation=parseAndIncrement
cause = NumberFormatException
```

`thenApply(Integer::parseInt)` thất bại; các stage downstream mang exceptional completion. Không có `ERROR` trung gian ở mỗi `thenApply`. `main`, nơi sở hữu kết quả cuối, ghi một sự kiện. Nếu dùng `whenComplete` để log ngay tại `parsed`, thì tầng quan sát cuối cần **không ghi lại cùng lỗi**, hoặc callback đó chỉ đo metric và không in stack trace.

Trong ứng dụng có nhiều Future độc lập, phải có chiến lược **quan sát từng operation**: một thất bại của hai job khác nhau thật sự là hai sự kiện cần log. “Log once” không có nghĩa bỏ qua nhiều operation chỉ vì chúng tình cờ ném cùng loại exception.

## 9. Phân loại mức log theo kết quả thật

Có exception không đồng nghĩa `ERROR`. Một ma trận quyết định có thể áp dụng:

| Tình huống | Mức ghi nhận thường hợp lý | Lý do |
|---|---|---|
| Request thất bại bất ngờ, chưa được phục hồi | `ERROR` ở boundary | Cần điều tra một operation failure |
| Lỗi tạm thời đã dùng fallback, dịch vụ còn hoạt động | `WARN`, đôi khi `INFO`/metric | Chất lượng suy giảm nhưng operation hoàn tất |
| Retry attempt chưa hết ngân sách | `DEBUG`/metric hoặc log ngắn | Chưa phải thất bại cuối |
| Đầu vào không hợp lệ dự kiến, trả HTTP 400 | Có thể `INFO`/metric hoặc không log | Đây có thể là hành vi client bình thường |
| Tác vụ bị hủy có chủ đích khi shutdown | `DEBUG`/`INFO` hoặc không log | Lifecycle bình thường |
| Lỗi bảo mật/audit cần dấu vết riêng | Log bảo mật có kiểm soát, mức theo chính sách | Mục đích khác với stack trace lỗi kỹ thuật |
| Lỗi đóng tài nguyên gây ảnh hưởng lớn | `ERROR` tại boundary chịu trách nhiệm | Có nguy cơ dữ liệu/tài nguyên bị hỏng |
| Exception đã bị propagate qua nhiều tầng | Một error log chính, giữ cause | Tránh lặp cùng một failure chain |

Mức log phải phù hợp SLA, yêu cầu vận hành và sự kỳ vọng của hệ thống. Ví dụ 404 thông thường không cần stack trace, nhưng 404 do dữ liệu hệ thống bất ngờ mất có thể cần điều tra.

Đừng chỉ dựa vào tên exception như `IllegalArgumentException` để tự động xem là lỗi do client; phải phân loại theo **ngữ cảnh**: exception này có thể do bug trong service chứ không phải input HTTP.

## 10. Ghi log đầy đủ một lần: nội dung nào là đủ?

Một sự kiện lỗi ở boundary nên có những trường thiết yếu, tùy hệ thống:

- **`operation`**: thao tác bị thất bại (`order.create`, `invoice.export`, `message.consume`).
- **`requestId` / `correlationId` / `traceId`**: nối sự kiện với request hoặc trace, dùng nguồn đáng tin.
- **`service`, `environment`, `version`**: nơi và bản build xảy ra lỗi (thường logger hạ tầng bổ sung).
- **`exception`**: Throwable đầy đủ, có cause và suppressed, được truyền tới logging API đúng overload.
- **`errorCode` / `failureClass`**: phân loại lỗi có thể tổng hợp, không phụ thuộc message biến động.
- **Kết quả thật**: HTTP status, job status, retry exhausted, fallback used, dead-letter route nếu liên quan.
- **Thông tin hữu ích nhưng an toàn**: mã đối tượng đã kiểm soát, attempt count, duration, dependency name.

Không nên đưa password, token, secret, key, cookie hoặc payload chứa dữ liệu cá nhân vào log. Exception message từ thư viện đôi khi đã chứa dữ liệu nội bộ; cần policy sanitization, quyền truy cập và retention phù hợp.

Ví dụ phương thức gọi logging theo placeholder (mã tích hợp, yêu cầu logger tương thích SLF4J):

```java
logger.error(
        "operation={} requestId={} errorCode={} attempts={}",
        operation,
        requestId,
        errorCode,
        attempts,
        failure);
```

Chỗ `failure` là **Throwable**, không phải `failure.toString()` hoặc `failure.getMessage()`. Chỉ string hóa message thường bỏ mất stack, `Caused by`, `Suppressed` và dòng nguồn. Một số hệ thống log JSON có field dành riêng cho exception; nên cấu hình encoder/appender để giữ thông tin này thay vì tự nối chuỗi dài.

### Correlation ID không phải chìa khóa duy nhất để đếm lỗi

Một request có thể có **nhiều failure độc lập** hoặc chứa nhiều item; một job có thể chạy nhiều operation. Nếu muốn chống duplicate logic, thiết kế khóa phân biệt `operationId + failureClass + attempt/phase`, cùng cơ chế sở hữu sự kiện. Không được tự động xóa mọi log có cùng `requestId` vì có thể loại bỏ thông tin khác nhau.

Một biến thể hữu ích là theo dõi **metric tổng số request thất bại** tại boundary, còn log chỉ là kênh điều tra. Metric nên đếm operation cuối cùng thay vì đếm số dòng `ERROR` vốn có thể thay đổi theo cấu hình.

## 11. `Caused by` và `Suppressed` cần được giữ nguyên xuyên các tầng

Khi một tầng bọc exception bằng constructor có `cause`, hệ thống có thể in:

```text
java.lang.IllegalStateException: Không tạo được báo cáo
    at app.ReportService.export(ReportService.java:40)
Caused by: java.io.IOException: Hết dung lượng ổ đĩa
    at app.FileWriter.save(FileWriter.java:20)
    ... 1 more
```

Đây là stack trace **minh họa**, số dòng chỉ để mô tả. `... 1 more` nói về frame trùng ở cuối stack, không phải một exception khác.

Nếu dùng `try-with-resources`, lỗi `close()` có thể nằm dưới `Suppressed:`. Một điểm log ở cuối với **toàn bộ Throwable** có thể giữ cả outer message, cause và suppressed. Nếu mỗi tầng tự log `e.getMessage()` rồi bỏ cause, sẽ vừa tạo duplicate vừa mất dữ liệu quan trọng.

Không phải log một lần thì thông tin ít hơn: **một stack trace đầy đủ có chuỗi cause/suppressed** thường giá trị hơn nhiều dòng lỗi rời rạc không có liên hệ.

## 12. Tránh trùng khi framework và runtime đã log

Trong nhiều framework, exception không xử lý có thể được ghi bởi:

- Bộ xử lý lỗi HTTP hoặc error dispatcher.
- Job scheduler, executor wrapper hoặc task framework.
- Consumer framework khi message handler ném lỗi.
- Container/app server khi request hoặc task thoát ra ngoài.
- Bộ thu thập telemetry/APM tạo exception event tự động.

Vì vậy, cần **đọc behavior của đường xử lý lỗi đang dùng**. Nếu global handler của bạn log rồi trả response bình thường, framework có thể không log stack lần nữa; nhưng nếu handler log rồi `throw` để framework xử lý, duplicate có thể xuất hiện.

Các hướng thiết kế thông thường:

1. **Cho framework sở hữu:** tầng ứng dụng thêm ngữ cảnh và ném tiếp; framework ghi lỗi tại boundary nếu đủ dữ liệu.
2. **Ứng dụng sở hữu:** một global handler ghi log và chuyển lỗi sang kết quả cuối; tránh tự log lại tại filter/controller/service.
3. **Tách metric/audit khỏi error log:** instrumentation có thể ghi metric hoặc span status mà không phải copy nguyên stack trace.

Không thể chọn đúng một cách chỉ bằng tên công nghệ. Cần kiểm tra cấu hình logger, exception resolver, code của middleware và log thực tế của một request lỗi đã kiểm soát.

## 13. Khi nào ngoại lệ cần được ghi nhiều lần?

Có những tình huống **khác mục đích hoặc khác ranh giới** nên nhiều sự kiện là hợp lý:

- Một service A gọi service B. B cần ghi lỗi operation của **chính B**; A cũng có thể ghi lỗi request của **chính A** nếu hợp đồng của A thất bại. Đây là **hai operation tại hai service**, dù cùng một nguyên nhân kỹ thuật, và nên được nối bằng trace/correlation ID.
- Một retry attempt có log mức `DEBUG` diễn giải thời điểm và backoff, trong khi exhaustion là một log `ERROR` cuối. Đây là **sự kiện khác ý nghĩa**, không phải lặp nguyên stack.
- Một sự kiện audit/security bắt buộc ghi độc lập với operation error; cấu trúc, retention và quyền đọc có thể khác.
- Một lỗi được phục hồi có `WARN` về fallback; một phần việc khác độc lập về sau lại thất bại. Chúng không phải cùng một thất bại để gộp.

Điều cần tránh là **sao chép cùng một exception stack với cùng kết luận “operation failed” ở từng tầng liên tiếp**, không thêm thông tin hoặc sở hữu kết quả mới.

Nếu có một lý do thực sự cần log trước khi ném tiếp, thông điệp phải nói rõ **sự kiện khác biệt** (ví dụ rollback thất bại riêng), lý do và mức độ. Đừng biến ngoại lệ này thành ngoại lệ được log ba lần chỉ vì ba nơi đều có `catch`.

## 14. Ranh giới transaction, rollback, cleanup

Một exception có thể khiến transaction rollback hoặc `close()` thất bại. Không phải mọi lỗi cùng xuất hiện trong một luồng đều là duplicate:

- **Lỗi chính:** thao tác nghiệp vụ bị thất bại.
- **Lỗi rollback/cleanup:** một thao tác khôi phục hệ thống khác cũng thất bại, có hậu quả riêng.
- **Ngoại lệ suppressed:** Java có thể lưu lỗi đóng tài nguyên bổ sung trong Throwable chính.

Tại boundary, cần ghi một sự kiện đủ chi tiết giữ các nhánh này. Nếu cleanup tạo **một incident độc lập** đòi hỏi alert hoặc can thiệp riêng, có thể ghi sự kiện riêng với nhãn rõ ràng, tránh dùng cùng câu “request failed” như lỗi chính.

Ví dụ dạng thiết kế (mảnh mã minh họa, `cleanup` do ứng dụng định nghĩa):

```java
try {
    performOperation();
} catch (Exception primary) {
    try {
        cleanup();
    } catch (Exception cleanupFailure) {
        primary.addSuppressed(cleanupFailure);
    }
    throw primary;
}
```

Phương thức bao ngoài phải xử lý/khai báo `Exception`; đoạn code không độc lập. `addSuppressed(...)` bảo toàn lỗi cleanup cùng exception chính, cho phép tầng có trách nhiệm ghi đầy đủ một lần. Nếu `cleanupFailure` có hậu quả riêng cần alert, instrumentation có thể ghi một metric hoặc sự kiện có nghĩa khác.

## 15. Logging, propagation và `InterruptedException`

`InterruptedException` là tín hiệu rằng **luồng hiện tại bị yêu cầu ngắt khi chờ**, thường liên quan shutdown/hủy tác vụ. Không nên mặc định log `ERROR` kèm stack ở mỗi tầng.

Trong một worker muốn ngừng hợp tác (mảnh mã đặt trong `Runnable` hoặc phương thức phù hợp):

```java
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

`queue` là một `BlockingQueue` đã khai báo. `Thread.currentThread().interrupt()` khôi phục cờ interrupt vì thao tác chờ thường xóa cờ khi ném ngoại lệ. Nếu shutdown này là **dự kiến**, log ở mức `DEBUG/INFO` hoặc không log; nếu là sự kiện không dự kiến, ranh giới worker có thể ghi chi tiết sau khi phân loại.

Đừng bắt `Exception` chung, in stack, rồi tiếp tục vòng lặp vô hạn: thao tác chờ có thể ném lại liên tục khi cờ interrupt vẫn đặt, đồng thời hệ thống bị log storm.

`CancellationException` của Future bị hủy cũng thường là lifecycle, không tự đồng nghĩa với lỗi nghiệp vụ. `ExecutionException` và `CompletionException` báo thất bại của tác vụ bất đồng bộ và thường chứa cause cần phân loại.

## 16. Đo lường và kiểm tra rằng log không bị trùng

Cách kiểm tra một thay đổi logging nên dựa trên **tình huống có thể lặp lại**:

1. Tạo một request/job có lỗi xác định, gắn một `requestId` duy nhất.
2. Xác nhận kết quả cuối đúng: status thất bại hoặc fallback, không chỉ nhìn console.
3. Đếm **sự kiện `ERROR` chính cho operation** theo requestId và event code. Thông thường mong một sự kiện cho một operation failure.
4. Kiểm tra `Throwable` được in đầy đủ với cause và suppressed khi cần, không chỉ một chuỗi message.
5. Kiểm tra cả retry: nhiều attempt nhưng chỉ một log `ERROR` nếu cuối cùng thất bại; nếu thành công thì không còn kết luận operation failed.
6. Kiểm tra async `get()`/`join()` và callback: tránh log cả callback lẫn boundary cho cùng thất bại.
7. Kiểm tra một lỗi được phục hồi: có thể xuất hiện `WARN`/metric về fallback, nhưng không tự tạo thêm error event ở controller.
8. Kiểm tra PII/secret và correlation ID; bảo đảm hệ thống log tập trung không tự sao chép event do pipeline.
9. So sánh **metric số operation thất bại** với số log lỗi cuối theo cùng cửa sổ thời gian; hai con số không bắt buộc luôn bằng nhau nếu có sampling hoặc nhiều event độc lập, nhưng sai lệch lớn cần giải thích.

Trong automated test, có thể dùng in-memory appender của logging framework để kiểm tra message/code, level, throwable và số event ở boundary. **Đừng chỉ so khớp toàn bộ stack trace từng ký tự** vì số dòng, tên frame và lớp JDK có thể thay đổi; điều cần xác nhận là ownership, cause và số lượng log chính.

### Quan sát với phân tán

Một service có thể nhận cùng requestId qua nhiều hop. Hãy gắn `service`, `operation` hoặc span ID để phân biệt event của mỗi hop. Log aggregation thường cung cấp khả năng group các event có cùng trace ID; nó không thay thế việc kiểm soát duplicate tại nguồn.

## 17. Các anti-pattern và cách sửa

### A. `catch` chỉ để log rồi throw nguyên

```java
try {
    repository.save(entity);
} catch (Exception e) {
    logger.error("save failed", e);
    throw e;
}
```

Đây là mảnh mã tích hợp giả định `repository`, `entity`, `logger` đã có. Nếu tầng này không làm gì thêm, hãy **bỏ `catch`** hoặc chỉ bọc lỗi có ý nghĩa theo hợp đồng và giữ cause. Để boundary chịu trách nhiệm log.

### B. In `printStackTrace()` đồng thời gọi logger

```java
e.printStackTrace();
logger.error("failed", e);
```

Hai cơ chế có thể cùng in stack, gây duplicate qua stdout/stderr và logger. Chọn cơ chế logging chuẩn của hệ thống.

### C. Log message nhưng bỏ Throwable

```java
logger.error("Request failed: {}", e.getMessage());
```

Nó có thể thiếu stack, cause và suppressed. Ở nơi chịu trách nhiệm ghi lỗi, truyền **đối tượng exception** vào đúng overload của logger.

### D. Bọc lỗi nhưng cắt cause

```java
throw new IllegalStateException("Service failed");
```

Nếu đang chuyển đổi một exception vừa bắt, truyền cause: `new IllegalStateException("Service failed", e)`. Không chuyển các lỗi thuộc kiểu checked thành thông điệp trần rồi bỏ chuỗi nguyên nhân.

### E. Retry mọi lỗi và log mọi lần

Retry cả lỗi validate dữ liệu bất biến có thể vô ích; `ERROR` từng attempt tạo storm. Phân loại lỗi retryable, đặt số lần, timeout/backoff, thống nhất operation ID và ghi kết luận ở cuối.

### F. Dùng một log level cho mọi exception

Một 404 hợp lệ hoặc thao tác hủy theo yêu cầu không giống DB outage. Chọn level theo **tác động vận hành** và kết quả, không theo tên kiểu Java.

### G. Không xác định owner khi dùng global handler

Controller log `ERROR`, service log `ERROR`, global advice cũng log `ERROR`, rồi container in thêm stack. Chọn rõ một nơi và kiểm tra framework có log mặc định không.

### H. Che lỗi bằng fallback không được giám sát

Nếu service trả cache thay cho dữ liệu mới, operation có thể thành công nhưng chất lượng suy giảm. Có thể ghi metric hoặc `WARN` để biết degraded mode mà không biến request thành `ERROR`.

### I. Coi log line là đơn vị đo sự cố

Đếm số `ERROR` dễ bị thay đổi do cấu hình và nhân bản. Dùng metric về **operation outcome**, request status, trace và event code ổn định; log để điều tra nguyên nhân.

### J. Tự tạo khóa chống trùng quá rộng

Nếu deduplicate theo mỗi `requestId`, có thể nuốt mất những lỗi độc lập trong cùng request. Thiết kế event identity theo **operation/failure phase/service**, không coi mọi Throwable cùng correlation ID là một lỗi.

## 18. Bảng quyết định khi review một `catch`

| Tình huống trong `catch` | Hành động thường đúng | Có ghi stack trace ở đây? |
|---|---|---|
| Không thể xử lý, ném tiếp nguyên lỗi | Truyền tiếp | Thường **không** |
| Chuyển lỗi kỹ thuật thành exception ngữ cảnh | Bọc exception giữ cause, ném tiếp | Thường **không** |
| Phục hồi thành công nhờ fallback | Trả dữ liệu/response dự phòng, cập nhật metric | Có thể `WARN`/`INFO`, không nhất thiết toàn stack |
| Hết retry và operation thất bại | Ném lên boundary hoặc xử lý tại chính boundary | **Có**, một sự kiện lỗi cuối |
| Handler HTTP quyết định response cuối | Phân loại, ghi sự kiện nếu chưa có chủ sở hữu khác | Có thể **có** |
| Worker bị interrupt theo shutdown | Khôi phục cờ/thoát theo hợp đồng | Thường không `ERROR` |
| Cleanup cũng thất bại | Giữ suppressed, đánh giá hậu quả độc lập | Chỉ tách log nếu có ý nghĩa độc lập |
| Audit bắt buộc bởi chính sách | Ghi sự kiện audit có cấu trúc | Theo policy, khác error log |

**Ghi nhớ:** Một Throwable có thể đi qua nhiều `catch`, nhưng mỗi `catch` không nhất thiết cần một log. Tầng trung gian giữ **cause và ngữ cảnh**, tầng phục hồi ghi nhận **điều đã xử lý**, còn ranh giới sở hữu kết quả thất bại ghi **một sự kiện chính giàu thông tin**. Rõ ownership giúp log đủ để điều tra và vẫn phản ánh đúng số operation thực sự thất bại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-008">Tránh ghi thông tin bí mật hoặc dữ liệu cá nhân vào message và log</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh ghi thông tin bí mật hoặc dữ liệu cá nhân vào message và log khi xử lý exception trong Java

## 1. Mục đích của chẩn đoán lỗi an toàn

Exception và log giúp trả lời **thao tác nào thất bại, khi nào, ở đâu, thuộc loại lỗi gì và cần xử lý tiếp thế nào**. Tuy nhiên, nếu lỗi được xây từ dữ liệu đầu vào hoặc thông tin của dependency, message và stack trace có thể vô tình chứa password, token, cookie, hồ sơ khách hàng, nội dung request hay dữ liệu riêng tư. Khi lỗi được ghi vào file, stdout, nền tảng giám sát, crash report hoặc trả về HTTP, dữ liệu đó có thể tồn tại trong nhiều bản sao với quyền truy cập và thời gian lưu khác nhau.

**Nguyên tắc:** chỉ ghi thông tin **cần thiết, an toàn và được phép**. Một bản ghi lỗi tốt thường có mã sự kiện, operation thuộc danh sách cho phép, mã lỗi ổn định, thời gian, correlation ID không nhạy cảm và trạng thái nghiệp vụ. Nó không cần chứa raw payload hay bất kỳ credential nào.

Một số khái niệm:

- **Secret:** thông tin dùng để xác thực hoặc cấp quyền, như password, API key, access token, refresh token, JWT, cookie phiên, OTP, private key.
- **PII / dữ liệu cá nhân:** thông tin liên quan đến một người có thể nhận diện, trực tiếp hoặc khi kết hợp với dữ liệu khác; ví dụ email, số điện thoại, địa chỉ, mã định danh, lịch sử hoạt động.
- **Exception message:** `Throwable.getMessage()` và các thông điệp được gắn vào Throwable.
- **Log sink:** bất kỳ đích nào nhận dữ liệu log, như console, file, cloud collector, APM, tracing agent, audit store và dịch vụ cảnh báo.
- **Data minimization:** chỉ thu thập và lưu đúng các trường cần cho mục đích đã được xác định.

Cần phân biệt **không đưa dữ liệu vào log** với **che dữ liệu ở giao diện người dùng**. Hai kiểm soát đều cần nhưng không thay thế nhau.

## 2. Các loại dữ liệu đặc biệt dễ bị lộ

| Nhóm dữ liệu | Ví dụ | Rủi ro thường gặp |
| --- | --- | --- |
| Credential | Password, token, API key, cookie, OTP | Chiếm đoạt tài khoản, mạo danh yêu cầu |
| Dữ liệu nhận diện | Họ tên, email, số điện thoại, địa chỉ | Lộ danh tính, liên hệ trái phép, liên kết hồ sơ |
| Dữ liệu nhạy cảm theo ngữ cảnh | Thông tin sức khỏe, tài chính, đơn hàng, vị trí | Xâm phạm quyền riêng tư và bí mật kinh doanh |
| Request/response tự do | JSON body, file upload, HTTP header | Chứa giá trị khó dự đoán hoặc dữ liệu bên thứ ba |
| Thông tin nội bộ | Connection string, cấu hình, đường dẫn, SQL đầy đủ | Lộ hạ tầng, endpoint và đôi khi credential |
| Mã tương quan | Request ID, trace ID, entity ID | Có thể liên kết hoạt động nếu không thiết kế hợp lý |

Không thể chỉ nhìn **tên trường** rồi kết luận nó an toàn. Ví dụ `userId` có thể là một định danh cá nhân, `requestId` có thể do client gửi lên chứa email, và `url` có thể có query parameter chứa token. Mỗi trường cần được phân loại theo **nguồn và mục đích**.

Phương án ít rủi ro nhất thường là **không thu thập giá trị nhạy cảm** thay vì thu thập rồi cố xóa khi đã gửi tới nhiều nơi.

## 3. Exception message không nên nối trực tiếp với dữ liệu đầu vào

Những mẫu dưới đây là **ví dụ nguy hiểm**, không phải mã khuyến nghị:

```java
throw new IllegalArgumentException("Invalid password: " + password);
throw new IllegalStateException("Unexpected bearer token: " + token);
throw new IOException("Remote request failed, body=" + requestBody);
```

Các biến `password`, `token`, `requestBody` do ứng dụng giả định đã định nghĩa. Một message có thể đi qua nhiều tầng như sau:

```text
Input của người dùng hoặc dependency
    -> exception message
    -> cause / suppressed exception
    -> logger / APM / console / HTTP error handler
    -> log collector, index, alert, export, backup
```

Ngay cả khi một controller cuối cùng trả response an toàn, một tầng trung gian hoặc auto-instrumentation agent có thể đã ghi nguyên Throwable.

Thay vì nhúng giá trị, dùng loại exception phù hợp và **mã phân loại lỗi**:

```java
throw new IllegalArgumentException("INVALID_CREDENTIALS");
```

Đây là đoạn trong phương thức, không phải chương trình độc lập. Trong hệ thống lớn, một class exception có `errorCode` là enum có thể rõ hơn một chuỗi tự do. Dù vậy, việc trả về mã “INVALID_CREDENTIALS” cho client hay không còn phụ thuộc chính sách không để lộ sự tồn tại tài khoản.

Đừng giả định message của thư viện/SDK là an toàn. Một HTTP client có thể đưa URL, header hoặc response body vào lỗi; JDBC driver có thể chứa SQL và dữ liệu; nhiều API bên ngoài có thể trả nguyên nội dung người dùng. **Nội dung exception từ dependency cần được coi là không đáng tin về tính an toàn dữ liệu.**

## 4. Những lệnh ghi lỗi có thể vô tình tiết lộ dữ liệu

### `getMessage()` và `toString()`

`ex.getMessage()` trả thông điệp từ nguồn lỗi; `ex.toString()` thường gồm class name và localized message. Hai cách ghi này đều có thể xuất secret:

```java
logger.error("Failed: {}", ex.getMessage());
System.err.println(ex);
```

Dòng đầu là **mảnh API logging kiểu SLF4J**, dòng thứ hai là Java SE trong phương thức có `ex`. Cả hai chỉ an toàn nếu dữ liệu Throwable đã được phép xuất qua sink cụ thể.

### Stack trace, cause và suppressed exceptions

`ex.printStackTrace()` hoặc nhiều framework khi gọi:

```java
logger.error("Operation failed", ex);
```

sẽ ghi loại lỗi, message, các stack frame, **cause chain** và thường cả **suppressed exceptions**. Message của `ex.getCause()` hoặc `ex.getSuppressed()` có thể mang dữ liệu nhạy cảm dù message của lỗi ngoài cùng đã được đổi thành chuỗi an toàn.

Không được suy ra rằng `throw new ServiceException("Safe message", original)` làm sạch toàn bộ cây lỗi. Nếu logger in cause, message của `original` vẫn có thể lộ. Tuy nhiên cũng không nên xóa mọi cause một cách vô điều kiện vì sẽ phá khả năng chẩn đoán; cần phân loại nơi nào được giữ Throwable đầy đủ và nơi nào chỉ được ghi event an toàn.

### `toString()` của DTO

Một `record` Java có `toString()` mặc định thường bao gồm giá trị các thành phần. Vì vậy:

```java
logger.debug("request={}", request);
```

là **mảnh code framework cần đánh giá**, kể cả khi không gọi `getMessage()`. Nếu `request` chứa password hoặc token, chuỗi biểu diễn có thể in toàn bộ. An toàn hơn là không log nguyên object chứa thông tin riêng tư, và tạo log event từ các trường allowlist.

## 5. Tách lỗi nội bộ khỏi thông báo công khai

Một request thất bại thường có ít nhất hai kênh:

**Kênh client:** báo mã lỗi ổn định, thông điệp dễ hiểu không tiết lộ cấu trúc hệ thống, và request ID không nhạy cảm khi cần hỗ trợ.

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "The service is temporarily unavailable",
  "requestId": "req-7"
}
```

Đây là **response JSON minh họa**; không phải định dạng mặc định của Java hay framework.

**Kênh vận hành:** ghi sự kiện dạng `event=OPERATION_FAILED`, `operation=FETCH_PROFILE`, `reason=UPSTREAM_UNAVAILABLE`, `request_id=req-7`, thời gian và số lần retry nếu cần. Không cần sao chép exception text từ dependency.

Đừng trả `ex.toString()` hoặc toàn bộ stack trace vào HTTP 500. Điều đó có thể tiết lộ token, nội dung đầu vào, tên class và đường dẫn nội bộ. **Request ID** phục vụ liên kết lỗi không được mang vai trò bearer token hay cấp quyền truy vấn dữ liệu.

Nếu cần xử lý sâu một sự cố, dùng quy trình chẩn đoán nội bộ được phân quyền và giới hạn thời gian, thay vì mặc định đưa toàn bộ raw exception vào log mà đội support, nhà cung cấp hoặc client có thể xem.

## 6. Danh sách trường cho phép thay vì log mọi thứ rồi che

Một schema sự kiện điển hình:

| Field | Nguồn phù hợp | Yêu cầu |
| --- | --- | --- |
| `event` | Hằng số/enum | Không nhận từ request |
| `operation` | Enum `FETCH_PROFILE` hoặc `CREATE_ACCOUNT` | Không ghép user-supplied URL |
| `reason` | Enum `INVALID_INPUT` hoặc `UPSTREAM_UNAVAILABLE` | Không biến raw exception message thành mã |
| `request_id` | Opaque ID do hạ tầng tạo/kiểm soát | Không dùng token/email làm ID |
| `duration_ms` | Số đo runtime | Không nhúng payload |
| `outcome` | Enum `SUCCESS`, `FAILURE`, `CANCELLED` | Phân biệt kết quả thật |
| `dependency` | Tên dịch vụ từ cấu hình đã biết | Không dùng endpoint tự do có query |

Đây là **allowlist**: mã chỉ cho phép những trường đã được xét đủ an toàn. Structured logging theo JSON hay placeholder không tự tạo ra bảo mật. Câu:

```java
logger.warn("email={} token={}", email, token);
```

vẫn làm lộ dữ liệu, dù dùng placeholder. Các mảnh logger ở đây giả định SLF4J hoặc API tương tự.

Một cách an toàn hơn:

```java
logger.warn("event={} operation={} reason={} request_id={}",
        "AUTH_FAILED", "SIGN_IN", "INVALID_CREDENTIALS", requestId);
```

Trong đó `requestId` đã được kiểm soát bởi hệ thống đáng tin cậy. Dù dùng whitelist, hãy kiểm tra các trường enum/ID được gán giá trị ở đâu. Một ID có pattern hợp lệ chưa chắc không mang dữ liệu cá nhân nếu được client chỉ định.

## 7. Ví dụ Java 17+ độc lập, biên dịch và chạy được

Ví dụ mô phỏng **hai exception chứa dữ liệu không nên xuất hiện trong log**: một `IOException` có header `Authorization` trong message và một `IllegalArgumentException` có email trong message. Handler không ghi trực tiếp hai exception đó mà tạo **sự kiện và response từ enum cố định**.

File **`SafeExceptionLoggingDemo.java`**:

```java
import java.io.IOException;

public class SafeExceptionLoggingDemo {
    enum Operation { FETCH_PROFILE, CREATE_ACCOUNT }
    enum FailureCode { UPSTREAM_UNAVAILABLE, INVALID_INPUT }

    record ApiError(int status, String code, String message) {}

    static void fetchProfile(String authorizationHeader) throws IOException {
        // A dependency may put request content in its exception message.
        throw new IOException("remote echoed " + authorizationHeader);
    }

    static void validateEmail(String email) {
        throw new IllegalArgumentException("Rejected email: " + email);
    }

    static void audit(String requestId, Operation operation, FailureCode code) {
        if (!requestId.matches("req-[0-9]{1,6}")) {
            throw new IllegalArgumentException("invalid internal request ID");
        }
        System.out.println("event=OPERATION_FAILED request_id=" + requestId
                + " operation=" + operation + " code=" + code);
    }

    static ApiError clientError(FailureCode code) {
        return switch (code) {
            case UPSTREAM_UNAVAILABLE ->
                    new ApiError(503, "SERVICE_UNAVAILABLE",
                            "The service is temporarily unavailable");
            case INVALID_INPUT ->
                    new ApiError(400, "INVALID_INPUT",
                            "The submitted data is invalid");
        };
    }

    public static void main(String[] args) {
        String demoToken = "demo-token-not-real";
        try {
            fetchProfile("Bearer " + demoToken);
        } catch (IOException ex) {
            // Never log ex, ex.getMessage(), or the token here.
            audit("req-7", Operation.FETCH_PROFILE,
                    FailureCode.UPSTREAM_UNAVAILABLE);
            ApiError error = clientError(FailureCode.UPSTREAM_UNAVAILABLE);
            System.out.println("client_status=" + error.status()
                    + " client_code=" + error.code());
        }

        try {
            validateEmail("user@example.invalid");
        } catch (IllegalArgumentException ex) {
            audit("req-8", Operation.CREATE_ACCOUNT, FailureCode.INVALID_INPUT);
            ApiError error = clientError(FailureCode.INVALID_INPUT);
            System.out.println("client_status=" + error.status()
                    + " client_code=" + error.code());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 SafeExceptionLoggingDemo.java
java SafeExceptionLoggingDemo
```

Standard output đã kiểm chứng:

```text
event=OPERATION_FAILED request_id=req-7 operation=FETCH_PROFILE code=UPSTREAM_UNAVAILABLE
client_status=503 client_code=SERVICE_UNAVAILABLE
event=OPERATION_FAILED request_id=req-8 operation=CREATE_ACCOUNT code=INVALID_INPUT
client_status=400 client_code=INVALID_INPUT
```

Giải thích:

1. `demoToken` là chuỗi giả **không phải credential thật**. `fetchProfile` cố tình mô phỏng một dependency kém an toàn làm lộ dữ liệu trong exception message.
2. Lệnh `catch` không in Throwable, message, stack trace hoặc header. Nó chọn `Operation`/`FailureCode` thuộc enum.
3. `ApiError` tạo response an toàn từ mã lỗi, không lấy nội dung tùy ý của exception.
4. `audit` chỉ in các giá trị đã kiểm soát. Trong production, sử dụng logger đã cấu hình sink, quyền truy cập, schema và retention; `System.out.println` chỉ giúp demo Java SE không cần thư viện.
5. Regex với request ID bảo vệ **hình thức**, không thay thế việc tạo ID không nhạy cảm từ hạ tầng tin cậy.
6. `record` và `switch expression` đều dùng được trong Java 17.

Giới hạn quan trọng: vì exception mô phỏng vẫn **chứa** dữ liệu thô trong memory, một auto-capture SDK, crash dump hay logger ở vị trí khác vẫn có thể thu chúng. Đừng lấy ví dụ này làm lý do cho phép code tạo exception chứa token; trong ứng dụng thật phải **giảm dữ liệu ở nguồn** và kiểm tra các sink ngoài đoạn handler.

## 8. Vì sao chỉ lọc bằng regex chưa đủ?

Một số ứng dụng cho phép log mọi chuỗi, sau đó áp regex để thay pattern email, `password=...`, `Bearer ...` hoặc số thẻ bằng `[REDACTED]`. Đây có thể là **lớp phòng thủ bổ sung**, nhưng không đủ làm chính sách duy nhất:

- Credential có nhiều dạng: header, query, JSON, base64, URL-encoded hoặc format riêng của SDK.
- Một token có thể xuất hiện **không có tiền tố** như `Bearer`.
- Dữ liệu cá nhân không chỉ là email/điện thoại; nội dung hội thoại, địa chỉ hay mã hồ sơ khó nhận diện bằng regex.
- Chuỗi có thể bị escape, cắt đoạn hoặc chứa nhiều dòng và ký tự điều khiển.
- Collector có thể lưu bản sao **trước khi** bộ lọc được áp dụng.
- Regex quá rộng xóa thông tin chẩn đoán hữu ích; regex quá hẹp bỏ lọt dữ liệu.
- Vendor thay đổi format message có thể làm bộ lọc cũ mất tác dụng.

Ưu tiên theo thứ tự: **không thu thập → không tạo exception chứa secret → chọn field allowlist → mask/redact ở nguồn nếu thật sự cần → filter bổ sung tại collector → kiểm thử mọi sink**.

**Hash không tự làm một giá trị thành vô danh.** Hash email dễ bị đối chiếu/dictionary attack khi miền giá trị đoán được; ngay cả ID giả danh cũng có thể liên kết với con người khi kết hợp dữ liệu khác. Nếu cần pseudonymization/tokenization, phải có threat model, bảo vệ khóa/bảng ánh xạ và chính sách truy cập phù hợp.

## 9. Không dump raw HTTP request, response hoặc SQL khi lỗi

Đây là mảnh code framework **không nên áp dụng trực tiếp**:

```java
catch (Exception ex) {
    logger.error("headers={} body={}", headers, requestBody, ex);
}
```

`headers` có thể chứa `Authorization`, `Cookie`, `Set-Cookie`, credential hoặc user-agent chứa thông tin riêng. `requestBody` có thể là hồ sơ khách hàng hoặc dữ liệu tài chính. Nếu exception in thêm cause, rủi ro tăng gấp nhiều lần.

Tương tự:

- URL đầy đủ có thể chứa query parameter nhạy cảm; ưu tiên tên operation hoặc **route template**, ví dụ `/customers/{id}` thay cho URL có ID thật.
- SQL statement có literal values hoặc JDBC bind parameters có thể chứa dữ liệu cá nhân; chỉ log mã câu lệnh/nhóm truy vấn và mã lỗi đã kiểm soát khi được phép.
- Response body từ dependency không phải message an toàn để phát tán trong log.
- Bật HTTP wire logging, JDBC trace/bind logging hoặc APM “capture all headers” ở production có thể làm lộ dữ liệu ngoài ý muốn.

Với client/server framework, chính sách cần áp dụng cả **filter, interceptor, exception resolver, access logger và HTTP client**. Chỉ sửa `catch` của một service không đủ nếu một middleware khác tự ghi nguyên request.

## 10. Cause chain, suppressed exception và lỗi từ async worker

Java có nhiều đường chuyển thông tin lỗi:

- `throw new ServiceException("safe", cause)` giữ `cause`; nếu logger in stack trace, message nhạy cảm của cause vẫn có thể lộ.
- `try-with-resources` có thể gắn lỗi từ `close()` thành suppressed exception; message thứ cấp cũng cần đánh giá.
- `Future.get()` có thể ném `ExecutionException` chứa cause từ worker.
- `CompletableFuture.join()` thường ném `CompletionException` có cause.
- Uncaught exception handler có thể in Throwable không qua controller.
- Framework scheduler có thể lưu exception của background task rồi báo về monitoring.

Điều này có nghĩa một dependency đã đưa secret vào Throwable thì **mọi điểm quan sát cause chain** đều có thể trở thành điểm rò rỉ. Không chỉ kiểm tra `getMessage()` của wrapper ngoài cùng.

Nếu phải giữ Throwable để phục vụ xử lý nội bộ, phân biệt rõ **khả năng giữ cause trong bộ nhớ** và **quyền xuất toàn bộ cause ra sink**. Có thể giữ mã lỗi/correlation ID ở log thông thường và dùng đường chẩn đoán hạn chế, được phê duyệt khi cần điều tra chi tiết. Không tự động cắt bỏ cause ở mọi tầng gây mất khả năng xác định nguyên nhân lỗi; nhưng cũng không mặc định lưu nguyên exception ở nơi nhiều người có quyền xem.

Với async work, một task có thể lỗi sau khi request ban đầu đã kết thúc. Phải xác định chủ sở hữu việc theo dõi `Future` hoặc stage, đặt policy log an toàn cho worker và tránh ghi cùng một exception nhiều lần tại từng dependent stage.

## 11. Correlation ID, tracing và MDC

Correlation ID giúp nối log từ nhiều service để truy nguyên một request **không cần in payload gốc**. ID cần được tạo hoặc xác minh bởi thành phần tin cậy, không dùng email, token, password hay mã truy cập có quyền bảo mật làm ID.

Một `X-Request-ID` do client gửi **không tự nhiên an toàn**: client có thể chèn dữ liệu cá nhân hoặc chuỗi điều khiển vào đó. Hệ thống nên kiểm tra độ dài/cú pháp, hoặc tự tạo internal ID và lưu quan hệ an toàn nếu thật sự cần.

Nhiều ứng dụng Java sử dụng `MDC` để gắn request ID vào log. Đây là mảnh code **minh họa SLF4J** trong một class có `service` và `safeRequestId`:

```java
MDC.put("request_id", safeRequestId);
try {
    service.process();
} finally {
    MDC.remove("request_id");
}
```

Cần import `org.slf4j.MDC` và cấu hình logging thích hợp; đoạn này **không độc lập** với Java SE. Thread pool có thể tái sử dụng worker; nếu không dọn context, một request có thể bị gắn nhầm metadata của request khác. `CompletableFuture` và các callback async không mặc nhiên kế thừa thread-local/MDC đúng như ngữ cảnh parent; dùng cơ chế context propagation của framework nếu có.

Không đặt nguyên JWT claims, request headers, email hoặc toàn bộ user profile vào MDC/baggage. Baggage có thể truyền qua nhiều service và log downstream, khiến phạm vi rò rỉ rộng hơn nhiều nơi tạo dữ liệu ban đầu.

## 12. Log injection từ input không đáng tin

Ngoài rủi ro privacy, attacker có thể đưa newline, tab, JSON fragment hoặc ký tự điều khiển vào input. Nếu log thẳng:

```java
logger.warn("Login failed for " + suppliedName);
```

một giá trị người dùng có thể tạo dòng log giả hoặc đánh lừa parser/alert. Đây là mảnh code **nguy hiểm để nhận diện**, không phải mẫu khuyến nghị.

Cách giảm rủi ro:

1. Tránh đưa user-supplied strings vào log nếu không có nhu cầu xác định.
2. Dùng event name và reason code lấy từ enum.
3. Với trường tự do thật sự cần thiết, giới hạn độ dài, chuẩn hóa/encode ký tự điều khiển phù hợp với định dạng log.
4. Dùng JSON encoder chuẩn, không tự ghép JSON string bằng phép nối chuỗi.
5. Kiểm tra bộ đọc log, dashboard và alert không coi nội dung log là lệnh/HTML đáng tin.

Parameterized logging làm việc với template tiện hơn, nhưng **không tự redaction dữ liệu** và không giải quyết toàn bộ log injection nếu giá trị chứa ký tự điều khiển.

## 13. Application log và audit log đều phải kiểm soát dữ liệu

**Application log** phục vụ chẩn đoán vận hành. **Security audit log** chứng minh hành động, thời điểm và kết quả của một sự kiện quan trọng. Audit log có thể có yêu cầu đặc biệt về tính toàn vẹn và thời gian lưu, nhưng điều đó **không hợp thức hóa việc ghi credential**.

Sự kiện đăng nhập thất bại nên ưu tiên mã sự kiện và kết quả, ví dụ:

```text
event=AUTHENTICATION_FAILURE
operation=SIGN_IN
outcome=DENIED
reason=INVALID_CREDENTIALS
request_id=req-7
```

Không ghi password người dùng nhập sai để “điều tra”. Nếu phải gắn một principal ID, hãy kiểm tra sự cần thiết và quyền đọc; đó có thể vẫn là dữ liệu cá nhân. Tránh phân biệt “tài khoản không tồn tại” với “sai password” trong response công khai nếu gây nguy cơ liệt kê tài khoản.

Với log bảo mật và log chẩn đoán, tránh dùng một kho dữ liệu mở rộng quyền truy cập để lưu tất cả chi tiết. Nếu cần xử lý sự cố đặc biệt, dùng môi trường chẩn đoán kiểm soát chặt và có thời gian giới hạn.

## 14. Mức DEBUG/TRACE không miễn trừ yêu cầu bảo vệ

Một anti-pattern thường xuất hiện trong điều tra production: tạm bật DEBUG/TRACE hoặc HTTP wire log để “thấy toàn bộ dữ liệu”. Điều này có thể khiến nhiều sink thu headers, body hoặc exception message mà bình thường không xuất hiện.

Mức log chỉ là **điều kiện về tần suất và độ chi tiết**, không phải cấp phép thu thập dữ liệu nhạy cảm.

- `ERROR`: event code, operation, request ID và context an toàn.
- `WARN`: tình trạng degraded, retry bất thường, mã lý do.
- `INFO`: mốc vận hành cần thiết.
- `DEBUG/TRACE`: chỉ thêm dữ liệu đã được phân loại và cho phép; không đưa password/token/body thô vào.

Nếu cần bật tạm mức chi tiết, phải có chủ sở hữu, phạm vi service, thời hạn tự tắt, giám sát khối lượng và kiểm tra cấu hình redaction/collector. Một kiểm tra kiểu `logger.isDebugEnabled()` chỉ giúp tránh tính dữ liệu khi DEBUG tắt; **không chứng minh nội dung sẽ an toàn khi DEBUG bật**.

## 15. Bảo vệ vòng đời log sau khi ghi

Ngay cả một event không chứa secret rõ ràng vẫn có thể cho biết lịch sử hoạt động. Cần quản lý:

- **Thu thập:** loại bỏ trường không cần ngay tại nguồn.
- **Vận chuyển:** bảo vệ kết nối appender/agent tới collector.
- **Lưu trữ:** phân quyền, mã hóa khi cần và phân tách môi trường.
- **Truy cập:** giới hạn tìm kiếm, xuất file, tải dashboard, API đọc log.
- **Retention:** chỉ giữ trong khoảng thời gian có mục đích vận hành/tuân thủ.
- **Sao chép:** nhận biết backup, index, data warehouse, cảnh báo/chatops có lưu bản sao.
- **Xóa và phản ứng sự cố:** có kế hoạch đánh giá phạm vi dữ liệu khi phát hiện rò rỉ.

Nếu **token thật** đã bị ghi nhầm vào log, chỉ xóa một dòng có thể chưa đủ: bản sao có thể đã tới collector, index, email alert hoặc backup. Quy trình xử lý có thể cần thu hồi/luân phiên credential, rà soát truy cập, gỡ bản sao theo khả năng và đánh giá nghĩa vụ thông báo áp dụng. Không dùng secret thật để “test liệu nó có bị ghi không”.

## 16. Những sai lầm thường gặp

| Cách làm | Tại sao không an toàn | Cách tốt hơn |
| --- | --- | --- |
| `throw new Exception("token=" + token)` | Token đi vào message và cause | Error code không nhạy cảm |
| `logger.error("{}", ex.getMessage())` | Nội dung dependency không được kiểm soát | Reason code và context allowlist |
| `logger.error("failed", ex)` không đánh giá dữ liệu | Stack trace/cause/suppressed có thể lộ secret | Policy riêng cho Throwable theo sink |
| `logger.debug("dto={}", dto)` | `toString()` có thể in password/email | Trường đã được chọn và phân loại |
| Ghi toàn bộ headers/body/SQL bind | Có thể chứa dữ liệu xác thực/cá nhân | Route template, status, statement ID |
| Trả `ex.toString()` trong HTTP 500 | Lộ class, message và nội bộ | Public code/message an toàn |
| Lọc mọi chuỗi bằng một regex | Không đủ nhận diện dữ liệu mới/mã hóa | Ngăn từ nguồn, allowlist, filter bổ sung |
| Hash email rồi coi là dữ liệu vô danh | Có thể đoán và đối chiếu | Threat model, pseudonymization đúng nghĩa |
| Dùng JWT/session làm request ID | Phát tán credential qua nhiều service | Server-generated opaque ID |
| Ghi cùng exception ở mỗi async stage | Bản sao log nhiều lần và lộ rộng | Một nơi chịu trách nhiệm quan sát lỗi |
| Tắt kiểm soát khi DEBUG/TRACE | Leak khi sự cố xảy ra | Mọi level phải tuân thủ policy |
| Chỉ xử lý leak ở app log | Collector, APM, proxy vẫn thu dữ liệu | Kiểm tra toàn bộ log pipeline |

## 17. Kiểm thử log và message an toàn

Ngoài unit test logic, nên có integration test đưa **dữ liệu giả dễ nhận biết** qua những đường lỗi thật hoặc mô phỏng, rồi kiểm tra mọi sink quan trọng.

| Tình huống | Kết quả cần xác minh |
| --- | --- |
| SDK ném exception có token giả | Log và client response không có token |
| Validation exception có email giả | Không lộ email qua error response hoặc appender |
| Throwable cause/suppressed chứa secret giả | Logger/collector không in nguyên exception |
| Request có `Authorization`/`Cookie` | Access/wire logs không chứa header nhạy cảm |
| DTO `toString()` chứa field password | Không dump object nguyên vẹn |
| Input có newline/control chars | Không chèn được sự kiện giả |
| `Future`/`CompletableFuture` lỗi | Đường quan sát async không log raw message |
| HTTP 4xx/5xx | Có mã lỗi và ID an toàn, không có stack |
| DEBUG/TRACE bật trong test | Chính sách dữ liệu vẫn được thực thi |
| Log tới APM/collector | Không tồn tại bản sao thô ngoài ứng dụng |

Dùng chuỗi canary **không phải bí mật thật** như `demo-token-not-real` hoặc `user@example.invalid`. Test nên assert chúng **không xuất hiện** trong nội dung thu từ logger test, response, trace event và các sink cần kiểm soát; đồng thời assert reason code/correlation ID an toàn **có xuất hiện** để chẩn đoán vẫn hữu ích.

Chỉ test một `catch` không đủ nếu middleware/agent còn tự thu Throwable; test cần mô phỏng toàn bộ đường dữ liệu phù hợp với kiến trúc. Đừng chỉ tìm `Bearer`, vì token có thể xuất hiện độc lập. Không phụ thuộc hoàn toàn vào snapshot text của exception message do bên ngoài tạo.

## 18. Checklist dùng khi review Java exception/logging

1. Mọi exception message được tạo mới đều tránh nối password, token, request body hoặc PII.
2. Không tự động biến `Throwable.getMessage()`/`toString()` thành HTTP response.
3. Không in raw `Throwable` ở sink rộng quyền khi source/cause chưa được đánh giá.
4. Log dùng event/operation/reason code và fields thuộc allowlist.
5. Correlation ID không chứa credential hoặc định danh không cần thiết; dữ liệu từ header ngoài phải được kiểm tra.
6. Logging không nhận nguyên object `record`/DTO có secret qua `toString()`.
7. Cause, suppressed exception, `CompletionException` và `ExecutionException` đều nằm trong đánh giá an toàn.
8. Wire logs, SQL bind logs, APM, stderr, job scheduler và cloud collector được cấu hình phù hợp.
9. Redaction là lớp bổ sung; không coi regex hoặc hashing mặc nhiên bảo vệ toàn diện.
10. Phân quyền, retention, encryption, backup và quy trình phản ứng sự cố đã được xác định.
11. Kiểm thử với token/email **giả** xác nhận log và response không chứa giá trị thô.
12. Vẫn giữ được thông tin vận hành: error code, operation, request ID, trạng thái và thời điểm.

**Kết luận:** muốn xử lý exception an toàn, hãy coi message và log là **đầu ra có thể bị sao chép ra ngoài ranh giới bảo mật**. Chọn dữ liệu được phép xuất ngay từ nguồn, tách lỗi nội bộ khỏi phản hồi công khai, quản lý cause chain và bảo vệ toàn bộ pipeline ghi log. Khả năng debug tốt đến từ event rõ ràng và correlation an toàn, không phải từ việc lưu tất cả dữ liệu mà exception tình cờ chứa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-009">Phân biệt thông tin chẩn đoán nội bộ với thông báo dành cho người dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thông tin chẩn đoán nội bộ với thông báo dành cho người dùng trong Java Exception

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **thông tin chẩn đoán nội bộ** (*diagnostic information*) với **thông báo công khai** (*user-facing error message*) khi một thao tác Java thất bại.
- Hiểu vì sao không nên trả trực tiếp `exception.getMessage()`, stack trace, đường dẫn file, câu SQL hoặc thông tin hạ tầng cho người dùng.
- Giữ ngoại lệ gốc và cause chain ở nơi chẩn đoán phù hợp, đồng thời thiết kế **loại lỗi, mã lỗi và thông điệp ổn định** cho ranh giới API.
- Phân loại lỗi validation, không tìm thấy tài nguyên, lỗi nghiệp vụ và lỗi hệ thống thay vì biến tất cả thành một thông báo hoặc một HTTP status giống nhau.
- Thiết kế `requestId`/`correlationId` để người dùng có thể báo sự cố mà không cần nhìn chi tiết kỹ thuật.
- Ghi log có cấu trúc, tránh rò rỉ dữ liệu nhạy cảm và tránh ghi cùng một lỗi lặp lại ở nhiều tầng.
- Kiểm thử rằng thông báo công khai không tiết lộ bí mật trong `Throwable` và vẫn giữ đủ thông tin để kỹ sư điều tra.

Bài sử dụng Java 17+ và ví dụ độc lập không phụ thuộc framework. Các đoạn về HTTP và thư viện logging được ghi rõ là minh họa ở ranh giới ứng dụng, không phải một HTTP server thực sự.

## 2. Một sự cố — hai nhóm người nhận thông tin

Giả sử chức năng đọc hóa đơn gọi tầng lưu trữ. Một lần đọc thất bại với `IOException` chứa chuỗi kỹ thuật như đường dẫn nội bộ, tên server hoặc địa chỉ kết nối.

**Kỹ sư vận hành** cần biết exception type, call stack, cause, thao tác nào lỗi, thời điểm, request ID và thành phần liên quan để tìm nguyên nhân.

**Người dùng** cần biết thao tác nào không thực hiện được, có thể làm gì tiếp theo, có nên thử lại không và cách cung cấp mã tham chiếu để bộ phận hỗ trợ tra cứu. Họ thường không cần biết địa chỉ máy chủ, tên lớp Java hoặc stack trace.

| Tiêu chí | Chẩn đoán nội bộ | Thông báo cho người dùng |
| --- | --- | --- |
| Mục đích | Phát hiện, phân loại, điều tra và khắc phục lỗi | Hiểu vấn đề và biết bước tiếp theo |
| Đối tượng | Kỹ sư/nhân sự có quyền phù hợp | Người dùng, client API hoặc hệ thống tích hợp |
| Nội dung | Throwable, stack trace, cause, suppressed, request ID, component và ngữ cảnh cần thiết | Mã lỗi ổn định, mô tả an toàn, hướng dẫn hợp lý, mã tham chiếu |
| Độ chi tiết | Có thể cao nhưng vẫn phải kiểm soát dữ liệu nhạy cảm | Chỉ mức chi tiết cần thiết, không hé lộ triển khai |
| Vòng đời | Log, tracing, hệ thống giám sát, incident record | UI, HTTP response, CLI, email, thông báo sản phẩm |
| Tính ổn định | Có thể thay theo phiên bản code, thư viện, cấu trúc hệ thống | Nên ổn định theo hợp đồng API và có thể dịch ngôn ngữ |
| Ví dụ | `IOException` trong `InvoiceRepository` gây `InvoiceLoadException` | “Không thể tải hóa đơn lúc này. Vui lòng thử lại sau. Mã tham chiếu: RQ-1042.” |

**Nguyên tắc cốt lõi:** không gửi bản ghi chẩn đoán nguyên trạng cho người dùng. Hãy **dịch lỗi tại ranh giới tin cậy**: giữ dữ liệu phù hợp cho kỹ sư, nhưng công bố một hợp đồng lỗi an toàn và dễ hiểu.

~~~text
Client gửi yêu cầu
        |
        v
Application / API boundary
        |
        v
Repository gọi hạ tầng
        |
        +--> IOException("host=db-private, path=/var/...")  [chi tiết nội bộ]
                    |
                    v
          InvoiceLoadException(message, cause)
                    |
          +---------+---------------------------------+
          |                                           |
          v                                           v
  Chẩn đoán nội bộ                              Public error
  type, cause, stack trace                     code + safe message
  requestId, operation                         + requestId
  log/traces có kiểm soát                      không lộ hạ tầng
~~~

## 3. Throwable chứa những gì và vì sao không công khai nguyên trạng?

Một `Throwable` trong Java có nhiều nguồn thông tin:

- `getClass().getName()`: tên lớp exception, thường phản ánh thư viện hoặc công nghệ đang dùng.
- `getMessage()`: văn bản chi tiết có thể do tầng I/O, JDBC, thư viện HTTP, framework hoặc lập trình viên tạo.
- `getCause()`: ngoại lệ nguyên nhân, có thể lại có cause riêng.
- `getStackTrace()`: đường gọi với tên class, phương thức, tệp nguồn và số dòng khi có.
- `getSuppressed()`: ngoại lệ bổ sung được giữ trong một số cơ chế, đặc biệt `try-with-resources`.
- `printStackTrace()`: cách biểu diễn Throwable, các nguyên nhân và những ngoại lệ suppressed phục vụ chẩn đoán.

Tất cả thông tin này **không có nghĩa là an toàn để xuất ra client**. Ví dụ `SQLException` có thể chứa chi tiết SQL và tên bảng; `IOException` có thể chứa đường dẫn máy chủ; exception từ SDK có thể mang endpoint, response body hoặc ID tài nguyên nội bộ. Một exception message không phải văn bản đã được duyệt về bảo mật và tính dễ hiểu.

### Ba lỗi công khai thường gặp

**Lỗi 1 — trả thẳng message của exception:**

~~~java
return error.getMessage();
~~~

Đây là **mã minh họa** trong một phương thức trả chuỗi. `getMessage()` có thể là `null`, khó hiểu, thay đổi giữa các phiên bản hoặc chứa thông tin nội bộ.

**Lỗi 2 — trả nguyên stack trace:**

~~~java
StringWriter writer = new StringWriter();
error.printStackTrace(new PrintWriter(writer));
return writer.toString();
~~~

Đây là **mã minh họa** giả định `error` là `Throwable` và đã import các lớp `java.io`. Nó đưa dữ liệu chẩn đoán vào lời đáp công khai. Stack trace có thể lộ tên package, class, phương thức, đường dẫn, thông tin nhạy cảm có trong message/cause.

**Lỗi 3 — che giấu tất cả bằng câu vô nghĩa:** người dùng chỉ nhận “Error” mà không biết yêu cầu nào bị từ chối, liệu thao tác có thể thử lại hoặc cần chỉnh input. Bảo mật **không đòi loại bỏ mọi thông tin hữu ích**; cần công bố thông tin an toàn, đúng hợp đồng, có thể hành động.

## 4. Tách ba tầng: nguyên nhân kỹ thuật, lỗi theo ý nghĩa API và thông báo công khai

Hãy phân biệt:

1. **Nguyên nhân kỹ thuật:** ví dụ `IOException`, `SQLException` hoặc lỗi kết nối tới dịch vụ phụ thuộc.
2. **Ngoại lệ tại tầng trừu tượng:** ví dụ `InvoiceLoadException`, `PaymentRejectedException` hoặc `DocumentNotFoundException`.
3. **Biểu diễn cho người dùng/client:** ví dụ `{code, message, requestId}` với HTTP status phù hợp.

Ranh giới repository/service có thể chuyển ngoại lệ kỹ thuật thành ngoại lệ tầng ứng dụng:

~~~java
try {
    return storage.read(invoiceId);
} catch (IOException original) {
    throw new InvoiceLoadException(
            "Không thể đọc hóa đơn " + invoiceId, original);
}
~~~

Đây là **đoạn minh họa**, giả định các biến/lớp tương ứng tồn tại và constructor `InvoiceLoadException(String, Throwable)` đã được khai báo.

Tầng service vẫn giữ **cause là chính đối tượng** `IOException` gốc để điều tra. Nhưng `InvoiceLoadException.getMessage()` **không nhất thiết** là message public: nó có thể chứa một ID nội bộ hoặc bối cảnh chỉ dành cho log.

Tại ranh giới HTTP/UI, một error mapper quyết định mã lỗi và thông điệp công khai theo loại lỗi đã được phân loại. Tầng giao tiếp **không** nên dùng nguyên tên class exception để tạo thông báo, vì tên class là chi tiết triển khai.

**Giữ cause phục vụ truy vết; lọc thông tin công khai phục vụ hợp đồng và bảo mật.** Đây là hai trách nhiệm tương hỗ, không mâu thuẫn.

## 5. Thiết kế error contract cho người dùng

Một dạng dữ liệu lỗi công khai có thể gồm:

~~~json
{
  "code": "DOCUMENT_UNAVAILABLE",
  "message": "Không thể tải tài liệu lúc này. Vui lòng thử lại sau.",
  "requestId": "RQ-1042"
}
~~~

Đây là **mẫu JSON minh họa**, không phải response của một HTTP server được chạy trong bài.

Các trường nên có hợp đồng rõ:

| Trường | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `code` | Mã lỗi có ý nghĩa ổn định cho client | Không gắn vào tên lớp/driver có thể thay đổi |
| `message` | Câu diễn đạt ngắn, dễ hiểu và an toàn | Không sao chép stack trace hoặc message kỹ thuật |
| `requestId` | Mã tham chiếu để tra log/trace | Không nhúng secret, token hoặc định danh nhạy cảm; kiểm soát độ dài/định dạng |
| `details` (tùy hợp đồng) | Thông tin có cấu trúc giúp sửa input | Chỉ chứa trường dữ liệu được phép công bố |
| HTTP status (nếu API HTTP) | Nhóm nguyên nhân ở lớp giao thức | Phải phản ánh đúng lỗi và hợp đồng nghiệp vụ |

Có thể hiển thị message bằng ngôn ngữ người dùng, trong khi `code` giữ ổn định để client ra quyết định mà không so sánh chuỗi ngôn ngữ tự nhiên.

Ví dụ lỗi validation:

~~~json
{
  "code": "INVALID_INPUT",
  "message": "Vui lòng nhập địa chỉ email hợp lệ.",
  "details": [
    {"field": "email", "reason": "invalid_format"}
  ],
  "requestId": "RQ-1043"
}
~~~

Đây cũng chỉ là **ví dụ biểu diễn JSON**. Trả tên trường như `email` có thể hợp lý nếu đây là input của chính người dùng; không tự động trả giá trị bí mật mà họ vừa nhập.

### Nội dung nào hữu ích cho người dùng?

- **Xác định hành động:** “Không thể tải tài liệu”, “Không thể lưu thay đổi” thay vì “Lỗi hệ thống”.
- **Trạng thái có thể hành động:** “Vui lòng kiểm tra định dạng email” đối với lỗi validation.
- **Khả năng thử lại có cơ sở:** với lỗi tạm thời, có thể hướng dẫn thử lại sau, nhưng tránh khuyên retry vô điều kiện các thao tác có tác dụng phụ.
- **Mã tham chiếu:** giúp hỗ trợ tra cứu mà không cần người dùng gửi stack trace, token hoặc thông tin riêng tư.
- **Không khẳng định quá mức:** timeout không chứng minh giao dịch chưa được thực hiện; thông báo phải thận trọng, đặc biệt với thanh toán hoặc tạo đơn hàng.

Thông điệp công khai nên ổn định về nghĩa, nhưng có thể thay đổi văn phong, bản dịch và phương thức hiển thị theo sản phẩm. Client không nên phân tích `message` để điều khiển logic; hãy sử dụng `code` và các trường có cấu trúc.

## 6. Ví dụ Java 17 độc lập: giữ cause, không để lộ chi tiết nội bộ

Tạo tệp **SafeErrorBoundaryDemo.java** với toàn bộ nội dung sau. Chương trình không gọi mạng hoặc cơ sở dữ liệu thật; nó giả lập lỗi I/O có chứa thông tin hạ tầng để kiểm chứng rằng public message không làm lộ thông tin đó.

~~~java
import java.io.IOException;

public class SafeErrorBoundaryDemo {

    static final class DocumentLoadException extends RuntimeException {
        DocumentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record PublicError(String code, String message, String requestId) {
    }

    static String loadDocument(String id) {
        try {
            throw new IOException(
                    "host=db-private.local; path=/srv/private; password=secret");
        } catch (IOException original) {
            throw new DocumentLoadException(
                    "Không đọc được tài liệu " + id, original);
        }
    }

    static PublicError toPublicError(
            DocumentLoadException error, String requestId) {
        return new PublicError(
                "DOCUMENT_UNAVAILABLE",
                "Không thể tải tài liệu lúc này. Vui lòng thử lại sau.",
                requestId);
    }

    public static void main(String[] args) {
        String requestId = "RQ-1042";

        try {
            loadDocument("invoice-7");
        } catch (DocumentLoadException error) {
            Throwable cause = error.getCause();

            // Thông tin chẩn đoán mẫu, cố ý không in message nhạy cảm.
            System.out.println("INTERNAL type: "
                    + error.getClass().getSimpleName());
            System.out.println("INTERNAL cause type: "
                    + cause.getClass().getSimpleName());
            System.out.println("INTERNAL cause retained: "
                    + (cause instanceof IOException));

            // Cấu trúc này mới là dữ liệu dành cho client.
            PublicError response = toPublicError(error, requestId);

            System.out.println("PUBLIC code: " + response.code());
            System.out.println("PUBLIC message: " + response.message());
            System.out.println("PUBLIC requestId: "
                    + response.requestId());

            boolean leaksSecret =
                    response.message().contains("db-private")
                    || response.message().contains("password")
                    || response.message().contains("/srv/private");

            System.out.println("PUBLIC leaks internals: " + leaksSecret);
        }
    }
}
~~~

Biên dịch và chạy với Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 SafeErrorBoundaryDemo.java
java SafeErrorBoundaryDemo
~~~

**Stdout dự kiến, xác định:**

~~~text
INTERNAL type: DocumentLoadException
INTERNAL cause type: IOException
INTERNAL cause retained: true
PUBLIC code: DOCUMENT_UNAVAILABLE
PUBLIC message: Không thể tải tài liệu lúc này. Vui lòng thử lại sau.
PUBLIC requestId: RQ-1042
PUBLIC leaks internals: false
~~~

### Những điểm quan trọng của ví dụ

1. Lỗi `IOException` được giả lập với message có hostname, đường dẫn và mật khẩu dạng chữ. Không dùng thông tin thật; các chuỗi này chỉ nhằm kiểm thử nguy cơ tiết lộ.
2. Tầng đọc dữ liệu tạo `DocumentLoadException` và truyền `original` làm cause bằng `super(message, cause)`. Thông tin lỗi vẫn được bảo toàn bên trong.
3. Tầng giao tiếp tạo `PublicError` từ loại lỗi và một `requestId` hợp lệ. Nó không gọi `error.getMessage()` hoặc `cause.getMessage()` để tạo lời đáp.
4. Các dòng `INTERNAL` minh họa **thông tin an toàn đã chọn** để in cho lớp học. Trong hệ thống thật, chẩn đoán thường được gửi tới logger/tracing có quyền truy cập; ví dụ không cố ý in toàn bộ cause nhạy cảm ra console.
5. Dòng `PUBLIC leaks internals: false` xác nhận public message không chứa những marker đã đưa vào exception gốc. Ở dự án thật, kiểm thử nên bao phủ **toàn bộ payload public** chứ không chỉ riêng trường message.
6. `PublicError` là record Java 17, dùng accessor `code()`, `message()`, `requestId()`. Chương trình có thể chạy độc lập, không cần dependency framework.

**Điểm giới hạn:** kiểm tra `contains` ở ví dụ là một phép thử minh họa, không thay thế rà soát bảo mật nội dung xuất ra. Một hệ thống có nhiều loại secret, identifier và payload cần cơ chế lọc, review hợp đồng và kiểm thử thích hợp hơn.

## 7. Phân loại lỗi: không phải lỗi nào cũng có một public message

Đối với API, đặc biệt là API HTTP, cần phân biệt lỗi do input, trạng thái tài nguyên, quy tắc nghiệp vụ và hạ tầng. Ví dụ định hướng:

| Tình huống | Loại lỗi tầng ứng dụng | HTTP status thường phù hợp | Ví dụ lời báo an toàn |
| --- | --- | --- | --- |
| Yêu cầu sai định dạng | Validation exception | 400 Bad Request | “Dữ liệu nhập không hợp lệ.” |
| Không xác thực hợp lệ | Authentication failure | 401 Unauthorized | “Vui lòng đăng nhập lại.” |
| Không có quyền thực hiện | Authorization failure | 403 Forbidden | “Bạn không có quyền thực hiện thao tác này.” |
| Tài nguyên không được tìm thấy theo hợp đồng | Not-found exception | 404 Not Found | “Không tìm thấy tài liệu.” |
| Xung đột trạng thái hoặc nghiệp vụ | Conflict/domain exception | 409 Conflict, nếu hợp đồng phù hợp | “Dữ liệu đã thay đổi. Vui lòng tải lại.” |
| Hạ tầng tạm thời không khả dụng | Service unavailable | 503 Service Unavailable nếu đúng tình trạng | “Dịch vụ tạm thời không khả dụng.” |
| Lỗi ngoài dự kiến ở server | Internal error | 500 Internal Server Error | “Không thể xử lý yêu cầu lúc này.” |

Bảng chỉ là **định hướng**; lựa chọn mã HTTP cụ thể phải dựa vào ý nghĩa và hợp đồng API. Không chuyển tất cả exception thành 500 chỉ để code ngắn hơn, cũng không gắn 503 cho mọi `IOException` nếu chưa chứng minh được tính “tạm thời không khả dụng”.

### Không lộ sự tồn tại của tài nguyên khi không được phép

Một yêu cầu truy cập tài liệu có thể dẫn tới 403 hoặc 404 tùy chính sách bảo mật. Có hệ thống cố ý không tiết lộ sự tồn tại của tài nguyên mà người dùng không có quyền xem. Vì vậy, việc ánh xạ exception vào thông điệp “tài liệu X tồn tại nhưng bạn không được xem” có thể tạo lỗ hổng về thông tin.

Nếu một ID không hợp lệ, thông báo chỉ nên bao gồm những phần input được phép hiển thị. Không đưa nguyên token, session ID hoặc thông tin của người dùng khác vào error response.

### Không khẳng định thao tác chưa được thực hiện chỉ vì timeout

Ví dụ API thanh toán bị timeout: backend có thể đã thực hiện giao dịch, nhưng kết quả chưa đến client. Thông báo “Thanh toán thất bại, hãy bấm gửi lại ngay” có thể dẫn tới thao tác trùng. Trong trường hợp này nên cung cấp trạng thái thận trọng, mã tham chiếu và cách kiểm tra kết quả/idempotency theo hợp đồng.

## 8. Ví dụ Java 17 độc lập: mapper chọn thông báo theo loại lỗi

Tệp **ErrorClassificationDemo.java** sau minh họa phân loại bốn tình huống và tạo các thông báo khác nhau. Nó **không** khởi động HTTP server; trường `status` chỉ mô phỏng mã trạng thái cần dùng ở adapter HTTP.

~~~java
import java.io.IOException;

public class ErrorClassificationDemo {

    static final class DocumentMissingException extends RuntimeException {
        DocumentMissingException(String message) {
            super(message);
        }
    }

    static final class BackendUnavailableException extends RuntimeException {
        BackendUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record ApiError(int status, String code, String message) {
    }

    static void execute(String scenario) {
        switch (scenario) {
            case "invalid" ->
                    throw new IllegalArgumentException("internal field x invalid");
            case "missing" ->
                    throw new DocumentMissingException("path=/srv/private/42");
            case "down" ->
                    throw new BackendUnavailableException(
                            "Storage backend failed",
                            new IOException("host=db-private.local"));
            case "unexpected" ->
                    throw new IllegalStateException("internal state corrupted");
            default -> {
                // Thành công: không ném ngoại lệ.
            }
        }
    }

    static ApiError mapToPublic(RuntimeException error) {
        if (error instanceof IllegalArgumentException) {
            return new ApiError(
                    400, "INVALID_INPUT", "Dữ liệu nhập không hợp lệ.");
        }
        if (error instanceof DocumentMissingException) {
            return new ApiError(
                    404, "NOT_FOUND", "Không tìm thấy tài liệu.");
        }
        if (error instanceof BackendUnavailableException) {
            return new ApiError(
                    503, "SERVICE_UNAVAILABLE",
                    "Dịch vụ tạm thời không khả dụng.");
        }
        return new ApiError(
                500, "INTERNAL_ERROR",
                "Không thể xử lý yêu cầu lúc này.");
    }

    public static void main(String[] args) {
        String[] scenarios = {
                "invalid", "missing", "down", "unexpected", "ok"
        };

        for (String scenario : scenarios) {
            try {
                execute(scenario);
                System.out.println(scenario + " -> SUCCESS");
            } catch (RuntimeException error) {
                ApiError response = mapToPublic(error);
                System.out.println(scenario + " -> "
                        + response.status() + " "
                        + response.code() + " | "
                        + response.message());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ErrorClassificationDemo.java
java ErrorClassificationDemo
~~~

**Stdout dự kiến, xác định:**

~~~text
invalid -> 400 INVALID_INPUT | Dữ liệu nhập không hợp lệ.
missing -> 404 NOT_FOUND | Không tìm thấy tài liệu.
down -> 503 SERVICE_UNAVAILABLE | Dịch vụ tạm thời không khả dụng.
unexpected -> 500 INTERNAL_ERROR | Không thể xử lý yêu cầu lúc này.
ok -> SUCCESS
~~~

Ví dụ minh họa được logic:

- `IllegalArgumentException` của yêu cầu đã chọn được ánh xạ thành lỗi input. Trong hệ thống thực, không nên bắt **mọi** `IllegalArgumentException` toàn cục rồi mặc định đổ lỗi cho client: lỗi cũng có thể xuất phát từ bug server. Adapter cần phân biệt exception do kiểm tra input có chủ đích với exception bất ngờ.
- `DocumentMissingException` biểu đạt chính sách tài nguyên không tìm thấy. Việc công khai 404 chỉ hợp lệ khi không làm lộ thông tin bị giới hạn truy cập.
- `BackendUnavailableException` có cause `IOException` giữ trong ứng dụng, nhưng response không tiết lộ hostname.
- `IllegalStateException` không dự kiến rơi vào nhánh lỗi server chung với message an toàn, thay vì trả `internal state corrupted`.
- Nhánh `ok` không ném exception, được xử lý như thành công. Không tạo “error envelope” cho một hoạt động vốn đã thành công.

Trong sản phẩm, nên định nghĩa các loại exception rõ nghĩa hơn thay vì dựa vào những exception hệ thống quá chung chung khi ánh xạ public code.

## 9. Ghi log nội bộ: cần đủ chẩn đoán, không đồng nghĩa ghi mọi dữ liệu

Ở tầng chẩn đoán, cần biết thao tác, kết quả, bối cảnh và exception. Một log event hữu ích thường chứa:

| Trường | Ví dụ | Lý do |
| --- | --- | --- |
| Timestamp | Thời điểm sự cố theo hệ thống log | Ghép sự kiện theo thời gian |
| Severity | `WARN`, `ERROR` | Phân loại mức ảnh hưởng theo chính sách |
| Event name | `document.load.failed` | Truy vấn/đếm theo loại sự kiện ổn định |
| Request/correlation ID | `RQ-1042` | Nối log và request/tracing |
| Component | `DocumentService` | Biết thành phần phát sinh |
| Operation | `loadDocument` | Biết thao tác đang làm |
| Safe resource reference | ID đã được kiểm soát/mã hóa nếu được phép | Khoanh vùng sự cố mà không lộ thông tin |
| Exception type | `DocumentLoadException` | Nhóm nguyên nhân |
| Cause chain, stack trace | Theo quyền và chính sách bảo mật | Chẩn đoán vị trí, bản chất và đường lan lỗi |

**Log nội bộ không mặc nhiên là nơi an toàn tuyệt đối.** Log có thể được xuất sang dịch vụ bên thứ ba, lưu lâu, nhân bản qua môi trường hoặc được nhiều người truy cập. Không có chính sách nào cho phép tự do ghi mật khẩu, access token, cookie xác thực, private key, dữ liệu thanh toán hay thông tin cá nhân chỉ vì log có nhãn “internal”.

### Java built-in logging

JDK có `java.util.logging.Logger`. Trong một lớp có biến `logger` và exception `error`, có thể dùng:

~~~java
logger.log(
        java.util.logging.Level.SEVERE,
        "document.load.failed requestId=" + requestId,
        error);
~~~

Đây là **đoạn mã minh họa**, giả định `logger` đã được tạo, `requestId` là giá trị an toàn và `error` là `Throwable`. Overload `Logger.log(Level, String, Throwable)` ghi nhận exception để logger/handler định dạng chi tiết khi phù hợp. Nó có ích hơn chỉ ghép `error.getMessage()`, vì cause và stack trace không bị chủ động loại bỏ.

Tuy nhiên, logger/formatter có thể in message của toàn bộ cause chain, trong đó **có thể chứa secret**. Vì thế “truyền Throwable cho logger” phải đi cùng thiết kế bảo mật dữ liệu, cấu hình nơi lưu, quyền truy cập, masking và kiểm thử. Nếu exception bên thứ ba có khả năng chứa bí mật, cần một chính sách lọc/redact đáng tin cậy trước khi xuất dữ liệu, không chỉ dựa vào việc đổi public message.

### Với SLF4J hoặc logging framework khác

Một thư viện logging thông dụng có thể cung cấp overload tương tự:

~~~java
logger.error("document.load.failed requestId={}", requestId, error);
~~~

Đoạn này là **mã minh họa sử dụng API của SLF4J**, không phải Java SE standalone; cần dependency và cấu hình logging cụ thể. Mục đích là chỉ ra cách truyền đối tượng exception cho logger thay vì chèn stack trace trực tiếp vào thông báo cho client. Với mọi logger, phải kiểm tra đúng overload/phiên bản thư viện được sử dụng và cách backend format Throwable.

Trường `requestId` nên nằm trong structured context nếu công cụ hỗ trợ, thay vì mã hóa mọi dữ liệu thành một chuỗi khó truy vấn. Nhưng cấu trúc log không thay thế yêu cầu loại trừ dữ liệu nhạy cảm.

## 10. Correlation ID và việc liên kết hai phía

**Request ID** là một mã tham chiếu cho một yêu cầu. **Correlation ID** có thể theo suốt nhiều hoạt động hoặc nhiều dịch vụ liên quan. **Trace ID** thường gắn với hệ thống distributed tracing. Tên gọi khác nhau theo hạ tầng; không coi chúng luôn là cùng một khái niệm.

Luồng đơn giản:

~~~text
Client -> API nhận request RQ-1042
              |
              +--> Service: loadDocument
              |          |
              |          +--> Adapter: IOException
              |
              +--> Log event: document.load.failed
              |       requestId = RQ-1042
              |       internal Throwable / safe context
              |
              +--> Response:
                      code = DOCUMENT_UNAVAILABLE
                      message = Không thể tải tài liệu lúc này.
                      requestId = RQ-1042
~~~

Người dùng có thể báo “Mã tham chiếu RQ-1042” cho hỗ trợ; kỹ sư có quyền phù hợp tra cứu log theo cùng mã.

### Những nguyên tắc quan trọng

- Tạo hoặc chuẩn hóa ID tại một ranh giới đáng tin cậy. Không mặc định tin mọi header ID do client gửi và ghi nguyên vào log.
- Kiểm soát định dạng và độ dài của ID để tránh log injection, dữ liệu tùy ý hoặc các giá trị quá lớn.
- ID nên không chứa secret, access token, email hay thông tin có thể định danh người dùng nếu không cần thiết.
- Truyền ID qua các thành phần theo cơ chế rõ ràng. Trường log nào cũng cần nhận đúng ID của request hiện tại.
- Giữ quyền truy cập và thời gian lưu log phù hợp. Một ID công khai không phải cơ chế cấp quyền tra cứu dữ liệu nội bộ.

Khi dùng thread pool hoặc callback async, không giả định mọi `ThreadLocal` tự động đi theo tác vụ. Nếu thư viện hỗ trợ MDC, cần truyền context theo cách framework quy định, dọn sạch context trong `finally` khi trả thread về pool và tránh ghép nhầm request ID của người dùng khác. Java SE không tự cung cấp một cơ chế MDC thống nhất tương đương mọi framework logging.

## 11. Ghi log ở đâu để tránh lặp cùng một lỗi?

Một lỗi có thể đi qua nhiều tầng:

~~~text
Repository -> Service -> Controller/API mapper -> HTTP response
~~~

Nếu repository, service và controller đều gọi `logger.error(..., error)` rồi ném lại cùng exception, một request lỗi có thể tạo ba bản stack trace giống nhau. Hệ thống theo dõi có thể đếm nhầm đây là ba sự cố, làm nhiễu chẩn đoán và tăng chi phí log.

Một nguyên tắc thực dụng:

- **Tầng biết nguyên nhân và phục hồi được:** có thể xử lý hoặc ghi sự kiện phục hồi nếu có giá trị quan sát.
- **Tầng chỉ thêm ý nghĩa API:** chuyển exception với cause; không nhất thiết log toàn bộ stack trace ở đó.
- **Ranh giới chịu trách nhiệm báo thất bại cuối cùng:** ghi log theo chính sách một lần với request ID, loại lỗi, bối cảnh và Throwable phù hợp.
- **Hệ thống giám sát ngoài ứng dụng:** có thể nhận metric/trace theo policy, không mặc định ghi trùng mọi stack trace.

Không có quy định rằng mọi ứng dụng chỉ được log một lần trong mọi hoàn cảnh. Nếu các tầng thêm **thông tin độc lập và có giá trị**, nhiều sự kiện mức độ phù hợp có thể hợp lý. Nhưng cần tránh lặp log giống nhau không có ngữ cảnh mới.

**Lưu ý:** lỗi validation mà người dùng tự sửa được thường không nên log ở mức nghiêm trọng tương đương sự cố mất database. Mức severity phải phản ánh tác động và kỳ vọng vận hành, không chỉ việc có exception.

## 12. Giữ nguyên cause chain và suppressed exceptions cho chẩn đoán

Khi chuyển lỗi kỹ thuật sang ngoại lệ có ý nghĩa hơn:

~~~java
public final class InvoiceLoadException extends RuntimeException {
    public InvoiceLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đây là **lớp Java độc lập** nếu lưu trong tệp `InvoiceLoadException.java` và sử dụng trong ngữ cảnh cùng tên. Constructor hai tham số giúp giữ `getCause()` là đối tượng Throwable gốc.

Phân biệt:

- `getMessage()` của wrapper: bối cảnh của tầng trừu tượng hiện tại.
- `getCause()`: ngoại lệ gây ra lỗi, gồm type, message, stack trace của chính nó.
- `getSuppressed()`: những exception phụ được giữ lại khi cần, ví dụ lỗi đóng tài nguyên trong `try-with-resources`.
- `printStackTrace()`: hình thức hiển thị chuỗi nguyên nhân phục vụ debug có kiểm soát.

Nếu chỉ sao chép `original.getMessage()` rồi tạo ngoại lệ mới không có cause, bạn có thể mất stack trace và suppressed exceptions quan trọng. Nếu chỉ nối tên lớp/method vào public message để “có đủ thông tin”, bạn làm rò chi tiết triển khai mà vẫn không tạo ra một cause chain có cấu trúc.

**Không nên tái tạo một exception từ message gốc** nếu có thể truyền trực tiếp Throwable ban đầu. Kiểm thử có thể dùng `assertSame(original, translated.getCause())` để chắc chắn giữ cùng đối tượng.

Dù vậy, **việc giữ cause không đồng nghĩa có quyền công bố cause cho tất cả các đối tượng**. Ở public boundary, mã lỗi và message vẫn phải được ánh xạ riêng.

## 13. Những nguy cơ dữ liệu nhạy cảm trong log và error response

Dữ liệu có thể bị rò qua nhiều kênh:

1. **Message của ngoại lệ nguồn:** thư viện có thể chèn SQL, URL, tên file, response body hoặc header chứa token.
2. **Message wrapper do ứng dụng viết:** lập trình viên tự ghép toàn bộ request payload hoặc input chứa PII vào lỗi.
3. **Structured log fields:** trường `requestBody`, `email`, `sessionId` hay `Authorization` có thể bị log vô tình.
4. **Stack trace và cause chain:** có thể kèm message nhạy cảm ở bất cứ cấp nào.
5. **Monitoring/tracing metadata:** tag hoặc baggage có thể chứa identifier nhạy cảm nếu gắn tùy tiện.
6. **Thông báo người dùng:** màn hình, email, CLI, chatbot hỗ trợ hoặc API có thể làm lộ chi tiết nội bộ cho bên không có quyền.
7. **Log injection:** dữ liệu không tin cậy có newline, ký tự điều khiển hoặc payload dài có thể gây nhiễu định dạng log nếu không xử lý.

### Biện pháp giảm rủi ro

- **Không đưa secret vào exception message ngay từ đầu** khi có quyền kiểm soát lớp ném exception.
- Chỉ log các trường thật sự cần chẩn đoán, áp dụng quy tắc loại trừ/masking nhất quán cho PII, credential và nội dung nhạy cảm.
- Kiểm tra và cấu hình logger/formatter, export pipeline, tracing agent và chính sách retention.
- Phân quyền cho hệ thống log/tracing và có quy trình xử lý sự cố dữ liệu.
- Từ chối hoặc chuẩn hóa chuỗi không tin cậy dùng trong log/ID, tránh phụ thuộc vào việc cắt chuỗi thủ công trong từng catch.
- Viết kiểm thử với canary secret giả: chèn marker vào cause, assert payload công khai không chứa marker.
- Với public message, ưu tiên **mã và template đã duyệt** thay vì xử lý “lọc từ cấm” trên message kỹ thuật không có cấu trúc.

**Không có danh sách từ khóa cố định nào chứng minh một chuỗi đã sạch toàn bộ dữ liệu nhạy cảm.** Cách đáng tin cậy nhất là kiến trúc chỉ cho phép dữ liệu công khai được chọn theo contract, thay vì xuất dữ liệu kỹ thuật rồi cố loại bỏ các chuỗi nguy hiểm.

## 14. Lỗi dự kiến và lỗi bất ngờ nên có mức công khai khác nhau

### Validation: nêu phần cần sửa, không tiết lộ quá mức

Ví dụ một form nhập số lượng sản phẩm. Thông báo “Số lượng phải lớn hơn 0” là hữu ích và thường an toàn. Nhưng không cần in tên class `NumberFormatException`, vị trí `Integer.parseInt` hay request JSON đầy đủ. Nếu đầu vào là mật khẩu, OTP hoặc token, tuyệt đối không trả nguyên giá trị đã nhập.

### Nghiệp vụ: truyền đạt trạng thái chính xác

Ví dụ đặt chỗ hết hạn hoặc số dư không đủ. Đây có thể là trạng thái nghiệp vụ dự kiến; người dùng cần biết cách xử lý, và kỹ sư có thể không cần ERROR stack trace cho mỗi trường hợp nếu hệ thống coi đó là normal rejection.

Tuy nhiên, nếu một quy tắc nghiệp vụ liên quan thông tin người dùng khác, phải xem xét quyền hiển thị. Không công bố “tài khoản khác đang sở hữu tài nguyên” nếu đó là thông tin bị hạn chế.

### Sự cố hạ tầng: thông báo an toàn, không suy đoán

Lỗi timeout, DNS, JDBC, storage hoặc service dependency có thể khiến thao tác không hoàn thành theo kỳ vọng. Public message thường chỉ nên nói thao tác hiện không thể xử lý và cung cấp mã tham chiếu. Nếu thực sự chứng minh lỗi tạm thời, có thể hướng dẫn thử lại theo chính sách.

Đừng ngụ ý retry là luôn an toàn đối với thanh toán, gửi email, tạo đơn hàng hoặc tác vụ có side effect. Khi không biết kết quả cuối, nên yêu cầu kiểm tra trạng thái giao dịch bằng một mã/idempotency key hoặc theo luồng sản phẩm đã định.

### Lỗi lập trình: không đổ trách nhiệm cho người dùng

Một `NullPointerException` hoặc `IllegalStateException` bất ngờ trong server thường không phải lỗi input của người dùng. Public mapping không nên nói “Bạn nhập sai” chỉ vì thấy tên exception. Hãy báo lỗi hệ thống an toàn, đồng thời giữ thông tin chẩn đoán đầy đủ trong phạm vi bảo mật.

## 15. Xử lý ngoại lệ tại ranh giới API

Một ranh giới xử lý lỗi có thể gồm:

~~~text
try {
    result = service.perform(request);
    return successResponse(result);
} catch (KnownValidationException e) {
    return safeValidationResponse(e);
} catch (KnownBusinessException e) {
    return safeBusinessResponse(e);
} catch (RuntimeException e) {
    logUnexpectedFailureWithContext(e);
    return safeInternalErrorResponse();
}
~~~

Đây là **pseudocode minh họa**, không phải Java standalone. Trong dự án, các hàm cần được định nghĩa và phân quyền dữ liệu đầu ra. Phải xử lý exception có phạm vi cụ thể, không bắt `Throwable` rồi tự động biến cả `Error` nghiêm trọng thành một response thành công giả.

Nếu ứng dụng dùng Spring, Jakarta REST, Servlet hoặc framework khác, nơi xử lý lỗi cuối thường là exception mapper, controller advice, filter hoặc lớp middleware phù hợp. Tên hook và cơ chế cụ thể phụ thuộc framework; nguyên tắc **thông điệp công khai riêng với diagnostic nội bộ** không thay đổi.

### Khi nào không nên trả lỗi kỹ thuật cho bên gọi?

Nếu public API dành cho lập trình viên, **có thể** trả một số thông tin kỹ thuật đã được thiết kế thành contract (mã validation, trường sai, tham số được phép công bố). Điều cần tránh là vô tình trả thông tin triển khai không ổn định hoặc nhạy cảm như raw SQL, nội dung token, địa chỉ nội bộ, stack trace và exception class.

Nói cách khác, “public API cho developer” không đồng nghĩa “debug endpoint không giới hạn”. Debug information phải qua quyền truy cập và chính sách bảo mật riêng.

## 16. Kiểm thử: public payload, cause và logging policy

Một bộ kiểm thử tốt xác minh hai phía **độc lập**.

| Trường hợp | Điều phải kiểm chứng |
| --- | --- |
| Lỗi I/O có cause chứa chuỗi nhạy cảm | Public code/message không chứa chuỗi đó |
| Lỗi được chuyển tại service | `translated.getCause()` là cùng Throwable gốc |
| Validation có loại lỗi đã biết | Client nhận code và thông điệp đúng |
| Lỗi backend không khả dụng | Client nhận message an toàn, không nhận host/path |
| Lỗi bất ngờ | Client nhận lỗi hệ thống chung, không nhận stack trace |
| Request ID | Có cùng mã hợp lệ trong response và sự kiện chẩn đoán được phép |
| Normal success | Không tạo lỗi hoặc ghi ERROR không cần thiết |
| Nhiều request đồng thời | Context không bị lẫn request ID giữa các luồng |
| Exception có suppressed/cause chain | Hệ thống chẩn đoán có thể truy vết theo chính sách |
| Lỗi validation lặp đi lặp lại | Severity/metrics không tạo cảnh báo sự cố không cần thiết |

### JUnit 5 — kiểm tra không rò thông tin từ message gốc

Đoạn sau **chỉ minh họa trong test project có JUnit 5** và lớp `SafeErrorBoundaryDemo` được truy cập đúng phạm vi:

~~~java
IOException original = new IOException("password=canary-secret");
SafeErrorBoundaryDemo.DocumentLoadException internal =
        new SafeErrorBoundaryDemo.DocumentLoadException(
                "Lỗi tầng lưu trữ", original);

SafeErrorBoundaryDemo.PublicError response =
        SafeErrorBoundaryDemo.toPublicError(internal, "RQ-TEST");

assertSame(original, internal.getCause());
assertEquals("DOCUMENT_UNAVAILABLE", response.code());
assertFalse(response.message().contains("canary-secret"));
assertFalse(response.message().contains("password="));
assertEquals("RQ-TEST", response.requestId());
~~~

Đoạn code này cần import `java.io.IOException` và các static assertion từ `org.junit.jupiter.api.Assertions`. Nó không phải chương trình Java độc lập. Một kiểm thử sản phẩm đầy đủ nên serialize cả API response rồi assert **toàn bộ payload** không chứa marker; cũng cần kiểm tra các field validation và thông báo lỗi phát sinh từ nhiều adapter.

### Kiểm thử logging không nên đòi từng dòng stack trace cố định

Tên tệp, số dòng, vị trí class và format logger có thể đổi theo JDK, phiên bản dependency hoặc môi trường. Nên kiểm tra những phần có hợp đồng như event name, request ID, loại lỗi, cause identity và chính sách redaction. Nếu muốn kiểm tra log một cách có cấu trúc, dùng appender/test handler phù hợp thay vì phân tích một chuỗi console mong manh.

Một phép kiểm tra “không có password trong public response” **không bảo đảm log nội bộ cũng an toàn**. Cần test riêng phần logging/export đối với secret giả, quyền truy cập và trường nhạy cảm theo yêu cầu bảo mật.

## 17. Lỗi phổ biến và cách sửa

1. **Trả thẳng `e.getMessage()`:** thay bằng public error mapper có code/message theo hợp đồng.
2. **Trả `e.toString()`:** nó thường chứa tên class và message kỹ thuật, không phải văn bản dành cho người dùng.
3. **Trả nguyên stack trace:** giữ cho chẩn đoán được phân quyền; response chỉ chứa thông tin được cho phép.
4. **Gộp tất cả vào “Lỗi không xác định”:** phân loại các lỗi dự kiến để người dùng có hướng xử lý.
5. **Mọi lỗi đều 500 hoặc 200 với `success=false`:** với HTTP API, chọn status theo nghĩa hợp đồng thay vì che mất phân loại.
6. **Mọi `IllegalArgumentException` đều là lỗi người dùng:** cần phân biệt lỗi validation có chủ đích với bug server.
7. **Bỏ cause khi chuyển lỗi:** dùng constructor nhận `Throwable cause` để giữ stack trace/nguyên nhân.
8. **Chỉ ghi `e.getMessage()` vào log:** có thể mất cause chain; thiết kế log exception theo chính sách bảo mật.
9. **Ghi mọi Throwable và request body nguyên trạng:** có thể rò secret hoặc PII vào log bên thứ ba.
10. **Ghi stack trace ở mọi tầng:** gây log trùng, nhiễu cảnh báo và tăng chi phí.
11. **Đưa token hoặc email vào requestId:** tạo định danh công khai chứa dữ liệu nhạy cảm.
12. **Tin request ID không kiểm soát:** có thể gây log injection, nhầm tương quan hoặc dữ liệu quá dài.
13. **Không dọn MDC/ThreadLocal trong thread pool:** có thể trộn ngữ cảnh giữa các request.
14. **So sánh client logic bằng message tiếng Việt/tiếng Anh:** nên dựa vào code có cấu trúc.
15. **Hướng dẫn retry mọi lỗi:** có thể gây thao tác lặp đối với hoạt động có side effect.
16. **Thông báo “không tồn tại” khi chính sách không cho phép tiết lộ:** cần thiết kế quyền truy cập và public status.
17. **Đồng nhất public safety với che tất cả:** thông báo phải an toàn **và** đủ hữu ích.
18. **Cho rằng “log internal” nên không cần bảo mật:** log vẫn cần kiểm soát dữ liệu, retention, quyền truy cập và export.
19. **Bắt mọi `Throwable` để xuất 500:** có thể che `Error` nghiêm trọng và làm sai vòng đời ứng dụng.
20. **Chỉ test message mà không test response body hoàn chỉnh:** có thể rò bí mật từ một trường khác.

## 18. Checklist review trước khi phát hành

Trước khi đưa tính năng xử lý lỗi vào vận hành, hãy kiểm tra:

- Đã tách rõ exception/cause nội bộ với public error DTO chưa?
- Mỗi loại lỗi dự kiến đã có code ổn định và thông điệp phù hợp chưa?
- Có trường thông tin người dùng có thể hành động, thay vì một câu lỗi quá chung, khi được phép công bố không?
- HTTP status hoặc trạng thái API đã phản ánh đúng nguyên nhân/contract chưa?
- Có trường `requestId` an toàn giúp hỗ trợ đối chiếu log không?
- Error response có lộ stack trace, exception type kỹ thuật, SQL, path, hostname, token, body nhạy cảm hay không?
- Log có đủ thông tin để phân biệt lỗi và truy cause, nhưng vẫn đáp ứng chính sách bảo mật không?
- Có tránh ghi log cùng một exception nhiều lần khi các tầng chỉ ném tiếp không?
- Khi dùng thread pool/async, có truyền và dọn correlation context đúng cách không?
- Thông báo retry có phù hợp với tính idempotent và thực tế giao dịch chưa chắc đã thất bại không?
- Kiểm thử đã cấy secret giả vào cause/message và kiểm tra **toàn bộ public response** chưa?
- Lỗi dự kiến, lỗi hệ thống bất ngờ và nhánh thành công đều được kiểm thử riêng chưa?

**Kết luận:** Một exception của Java chứa thông tin phong phú để **kỹ sư chẩn đoán nguyên nhân**, nhưng không phải mọi thông tin đó đều phù hợp hoặc an toàn cho **người dùng**. Thiết kế tốt giữ cause và ngữ cảnh cần thiết tại tầng nội bộ được kiểm soát; tại ranh giới giao tiếp, nó chuyển lỗi thành code ổn định, thông báo dễ hiểu, an toàn và mã tham chiếu có thể tra cứu. Hai kênh phải hỗ trợ nhau mà không đánh đổi bảo mật hoặc khả năng điều tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-010">Dùng debugger với exception breakpoint cho lỗi đã bắt hoặc chưa bắt</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng debugger với exception breakpoint cho lỗi đã bắt hoặc chưa bắt

## 1. Vì sao exception breakpoint hữu ích hơn chỉ đọc log?

Trong ứng dụng Java, lỗi có thể xuất hiện ở một phương thức cấp thấp, được **catch** tại tầng trung gian, rồi được chuyển thành kết quả dự phòng hoặc một exception khác. Nếu debugger chỉ dừng khi chương trình kết thúc bất thường, bạn có thể bỏ lỡ lỗi đã được bắt từ sớm. Ngược lại, nếu chỉ đọc thông điệp ở một catch block, bạn có thể chưa biết chính xác trạng thái biến và chuỗi lời gọi tại thời điểm exception đầu tiên phát sinh.

**Exception breakpoint** (điểm dừng theo exception) cho debugger cơ hội tạm dừng khi JVM thông báo một exception phù hợp được **ném** trong quá trình chạy. Sau khi tạm dừng, bạn có thể kiểm tra frame, giá trị biến, thread, stack và điều kiện dẫn đến lỗi. Tùy debugger, breakpoint có thể được cấu hình theo **exception được bắt (caught)**, **exception chưa có handler phù hợp (uncaught)**, hoặc cả hai.

Điểm phân biệt chính:

- **Line breakpoint:** dừng khi luồng thực thi tới một dòng mã nguồn hoặc vị trí bytecode tương ứng, bất kể có lỗi hay không.
- **Exception breakpoint:** dừng khi có sự kiện ném exception thuộc loại được chọn và thỏa điều kiện caught/uncaught mà debugger hỗ trợ.
- **Dừng ở catch block:** dừng khi logic bắt lỗi bắt đầu chạy; đây không nhất thiết là nơi exception đầu tiên được tạo ra.
- **Dừng khi process chết vì exception:** chỉ quan sát một nhóm trường hợp lỗi không được xử lý; không đủ để phân tích những exception đã bị catch, wrap hoặc chuyển thành fallback.

**Mục tiêu học:** dùng exception breakpoint để tìm *đúng nơi ném lỗi*, phân biệt *lỗi đã được bắt* và *lỗi chưa được bắt*, đọc dữ liệu ở thời điểm xảy ra, rồi đi theo đường exception để hiểu tác động nghiệp vụ. Debugger là công cụ chẩn đoán, không tự sửa lỗi hoặc thay thế kiểm thử.

## 2. “Caught” và “uncaught” nghĩa là gì?

**Caught exception** thường chỉ một sự kiện ném exception mà có một handler phù hợp trong đường thực thi Java. Ví dụ:

~~~java
// Mã minh họa; parse(...) được định nghĩa trong cùng class/ngữ cảnh.
try {
    int value = Integer.parseInt("abc");
} catch (NumberFormatException failure) {
    System.out.println("Input is invalid");
}
~~~

**NumberFormatException** được ném bởi quá trình chuyển chuỗi sang số; catch block xử lý nó. Khi chạy bình thường, chương trình có thể tiếp tục và không in stack trace ra stderr. Nhưng nếu bật breakpoint cho **caught NumberFormatException**, debugger có thể dừng **ngay tại sự kiện ném** — trước khi mã catch làm thay đổi trạng thái.

**Uncaught exception** thường chỉ sự kiện exception không có handler phù hợp trong đường xử lý hiện tại của JVM, cuối cùng có thể đi tới uncaught exception handler của thread. Ví dụ:

~~~java
// Mã minh họa bên trong main hoặc phương thức đang gọi.
int port = Integer.parseInt("abc");
// Không có catch phù hợp trong đường gọi.
~~~

Với một ứng dụng console đơn giản, lỗi này thường khiến thread **main** kết thúc và JVM in stack trace lên stderr. Tuy nhiên, việc cả process có kết thúc hay không còn tùy những thread không phải daemon khác, uncaught exception handler và môi trường chạy.

**Cần hiểu giới hạn của nhãn caught/uncaught:** Trình gỡ lỗi và cơ chế JVM debug xác định sự kiện theo khả năng tìm handler tại điểm ném trong stack phù hợp, không phải theo dự đoán nghiệp vụ kiểu “sau đó sẽ có ai log lỗi này hay không”. Một framework, thư viện executor, proxy hoặc mã do compiler sinh có thể bắt exception rồi báo lại qua một kết quả khác. Tùy IDE/debugger, nhãn hiển thị và cách phân loại sự kiện có thể khác nhau ở những ranh giới như vậy.

Đặc biệt, một exception đã bị **catch rồi throw lại** có thể tạo thêm sự kiện ném. Một exception **được bọc thành exception mới** tạo ra ít nhất hai đối tượng lỗi liên quan: original và wrapper. Debugger có thể dừng nhiều lần trong cùng một chuỗi sự cố, và đó không nhất thiết là nhiều bug độc lập.

## 3. Exception breakpoint dừng tại thời điểm nào?

Hình dung một chuỗi gọi:

~~~text
Main.main()
  -> OrderService.create()
     -> Validator.parse()
        -> Integer.parseInt("bad")
           [ném NumberFormatException]
     -> catch(NumberFormatException)
        -> ghi log / trả kết quả lỗi / throw wrapper
~~~

Nếu đặt line breakpoint trong catch, bạn chỉ bắt đầu quan sát khi JVM đã chuyển điều khiển vào catch. Exception breakpoint cho **NumberFormatException** có thể dừng ở nơi **Integer.parseInt** thực sự ném lỗi, với stack chứa các frame dẫn ngược về phương thức **Validator.parse** của ứng dụng. Bạn có thể xem input thực tế là **"bad"**, trạng thái object, điều kiện rẽ nhánh và những frame trước khi catch thay đổi dữ liệu.

Không nên coi vị trí hiển thị của IDE là tuyệt đối giống nhau trên mọi bản JDK. Exception có thể được ném trong mã thư viện, trong native code hoặc lớp proxy; IDE có thể tự chuyển hướng tới frame nguồn hiện có hoặc bỏ qua frame thư viện theo bộ lọc **Just My Code / step filters**. Vì vậy, cần phân biệt **vị trí debugger đưa lên màn hình** với **vị trí sự kiện ném thực tế** trong call stack.

### Exception breakpoint không tương đương “break ở lệnh throw”

Một exception có thể được tạo/ngầm ném bởi JVM hoặc thư viện mà không có lệnh **throw** ngay tại mã ứng dụng của bạn:

- **Integer.parseInt** ném NumberFormatException khi dữ liệu sai.
- Truy cập reference null có thể gây NullPointerException.
- Truy cập mảng sai chỉ số có thể gây ArrayIndexOutOfBoundsException.
- Phép ép kiểu không hợp lệ có thể gây ClassCastException.
- Gọi **Future.get()** có thể ném ExecutionException hoặc CancellationException.

Đặt exception breakpoint đúng loại giúp quan sát các lỗi này kể cả khi mã ứng dụng không chứa câu lệnh **throw new** tương ứng.

## 4. Điều kiện chuẩn bị để debug Java 17+

Một workflow học tập dễ tái hiện:

1. Cài JDK 17 hoặc mới hơn, cấu hình IDE sử dụng đúng JDK với project.
2. Biên dịch bằng chế độ có metadata dòng nguồn phù hợp. **javac** thông thường sinh thông tin line number; build bị tắt debug metadata có thể chỉ hiển thị **Unknown Source**.
3. Chạy bằng chế độ **Debug** thay vì chỉ **Run**, bảo đảm debugger đã attach vào JVM đúng process.
4. Mở cấu hình **Breakpoints** hoặc **Exception Breakpoints** trong IDE.
5. Thêm loại exception muốn quan sát, chẳng hạn **java.lang.NumberFormatException**.
6. Chọn bắt **caught**, **uncaught** hoặc cả hai tùy mục tiêu.
7. Dùng trường hợp lỗi nhỏ, có thể lặp lại, để xác nhận debugger thật sự dừng.
8. Khi dừng, xem **Threads**, **Frames/Call Stack**, **Variables** và **Evaluate Expression** nếu công cụ hỗ trợ.

Tên menu và bố cục trong IntelliJ IDEA, Eclipse, VS Code Java extensions có thể thay đổi theo phiên bản. Hãy tìm những lựa chọn tương ứng với **exception breakpoint**, **caught/uncaught**, **break on thrown exceptions**, **exception type/class filters**. Không giả định mọi IDE đều có cùng tên checkbox hoặc cùng mức hỗ trợ các bộ lọc.

**Lưu ý về quyền quan sát:** Code đang chạy dưới debugger có thể chứa token, password hoặc dữ liệu nhạy cảm trong memory và biến cục bộ. Chỉ debug môi trường và dữ liệu bạn được phép truy cập; cân nhắc hậu quả của **Evaluate Expression** vì gọi một method có side effect có thể làm thay đổi chương trình đang dừng.

## 5. Ví dụ Java 17+ độc lập: năm đường exception

Chương trình sau cố ý tạo năm tình huống để thử breakpoint:

1. **caught:** NumberFormatException được bắt tại cùng luồng.
2. **wrapped:** NumberFormatException được bắt rồi chuyển thành IllegalArgumentException có cause.
3. **suppressed:** lỗi parse là primary; IOException từ **close()** được đính kèm như suppressed.
4. **async:** NumberFormatException xảy ra trong tác vụ CompletableFuture; khi **join()**, caller nhận CompletionException.
5. **uncaught:** NumberFormatException thoát khỏi **main** mà không có catch phù hợp.

Lưu file **ExceptionBreakpointLab.java**:

~~~java
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExceptionBreakpointLab {
    static final class FaultyResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static int parsePort(String text) {
        return Integer.parseInt(text);
    }

    static void caughtCase() {
        try {
            parsePort("not-a-number");
        } catch (NumberFormatException problem) {
            System.out.println("caught: " + problem.getClass().getSimpleName());
        }
    }

    static void wrappedCase() {
        try {
            parsePort("bad-port");
        } catch (NumberFormatException original) {
            throw new IllegalArgumentException("invalid port", original);
        }
    }

    static void suppressedCase() throws IOException {
        try (FaultyResource resource = new FaultyResource()) {
            parsePort("bad");
        } catch (NumberFormatException original) {
            System.out.println("suppressed primary: "
                    + original.getClass().getSimpleName());
            System.out.println("suppressed close: "
                    + original.getSuppressed()[0].getClass().getSimpleName());
        }
    }

    static void asyncCase() {
        CompletableFuture<Integer> stage =
                CompletableFuture.supplyAsync(() -> parsePort("bad-async"));
        try {
            stage.join();
        } catch (CompletionException wrapper) {
            System.out.println("async wrapper: "
                    + wrapper.getClass().getSimpleName());
            System.out.println("async cause: "
                    + wrapper.getCause().getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length == 0 ? "caught" : args[0];
        switch (mode) {
            case "caught" -> caughtCase();
            case "wrapped" -> {
                try {
                    wrappedCase();
                } catch (IllegalArgumentException wrapper) {
                    System.out.println("wrapped outer: "
                            + wrapper.getClass().getSimpleName());
                    System.out.println("wrapped cause: "
                            + wrapper.getCause().getClass().getSimpleName());
                }
            }
            case "suppressed" -> suppressedCase();
            case "async" -> asyncCase();
            case "uncaught" -> {
                System.out.println("about to throw uncaught");
                System.out.flush();
                parsePort("bad-uncaught");
            }
            default -> System.out.println("unknown mode");
        }
    }
}
~~~

Biên dịch bằng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ExceptionBreakpointLab.java
~~~

Chạy riêng từng mode:

~~~bash
java ExceptionBreakpointLab caught
java ExceptionBreakpointLab wrapped
java ExceptionBreakpointLab suppressed
java ExceptionBreakpointLab async
java ExceptionBreakpointLab uncaught
~~~

Đầu ra **stdout chính xác** đã kiểm chứng cho bốn mode kết thúc bình thường:

**caught**

~~~text
caught: NumberFormatException
~~~

**wrapped**

~~~text
wrapped outer: IllegalArgumentException
wrapped cause: NumberFormatException
~~~

**suppressed**

~~~text
suppressed primary: NumberFormatException
suppressed close: IOException
~~~

**async**

~~~text
async wrapper: CompletionException
async cause: NumberFormatException
~~~

Với mode **uncaught**, chương trình in một dòng stdout trước khi ném lỗi:

~~~text
about to throw uncaught
~~~

Sau đó JVM thông thường in **stack trace của NumberFormatException ra stderr** và tiến trình Java kết thúc với mã lỗi khác 0. Stack trace stderr chứa tên file/dòng, frame nội bộ JDK và đường gọi **parsePort → main**; số dòng chính xác phụ thuộc bố cục mã nguồn và build. Không dùng một mẫu stack trace có số dòng cố định làm stdout của ví dụ.

**Vì sao suppressedCase khai báo throws IOException?** **FaultyResource.close()** có chữ ký throws IOException. Compiler phải xét khả năng thân try hoàn tất bình thường rồi close ném IOException làm primary. Trong đường chạy đang minh họa, parse ném NumberFormatException trước; close lỗi trở thành suppressed của NumberFormatException và được đọc trong catch tương ứng.

**Vì sao asyncCase bắt CompletionException?** **join()** báo exceptional completion thông thường dưới dạng CompletionException. Cause trong ví dụ là NumberFormatException do supplier tạo. Đây là hai thời điểm/đối tượng cần xem khi debug: lỗi gốc ở luồng chạy supplier và wrapper được quan sát tại consumer.

## 6. Thực hành breakpoint cho exception đã được bắt

Bắt đầu bằng lệnh:

~~~bash
java ExceptionBreakpointLab caught
~~~

Để debugger chạy được từ IDE, cấu hình **program arguments = caught** rồi chọn **Debug**.

Tạo exception breakpoint cho **java.lang.NumberFormatException** và bật tùy chọn **caught** nếu debugger hỗ trợ. Khi tiếp tục chạy:

1. JVM bắt đầu thực thi **caughtCase()**.
2. **parsePort("not-a-number")** gọi **Integer.parseInt**.
3. JDK tạo và ném NumberFormatException.
4. Breakpoint có thể tạm dừng tại sự kiện exception **trước** khi catch trong caughtCase thực thi.
5. Mở call stack để tìm **ExceptionBreakpointLab.parsePort** và **caughtCase**.
6. Chọn frame **parsePort** để xem biến **text = "not-a-number"**.
7. Continue/resume; catch sẽ in **caught: NumberFormatException**.

Nếu breakpoint chỉ bật **uncaught**, tình huống này thường không làm debugger dừng trên NumberFormatException theo tiêu chí uncaught, vì có catch phù hợp xử lý nó. Một số debugger có định nghĩa tùy chọn hoặc cơ chế filtering khác nhau; kiểm tra cấu hình thật và Java debugger đang dùng trước khi kết luận breakpoint lỗi.

**Điều học được:** Một exception bị bắt có thể là hoạt động bình thường của logic (ví dụ parse thử rồi trả lỗi nhập liệu), hoặc dấu hiệu bug đang bị che. Việc debugger tạm dừng ở caught exception không tự chứng minh rằng process đã thất bại. Cần hiểu catch làm gì sau đó.

### Nếu dừng trong frame thư viện

Khi breakpoint tạm dừng tại một phương thức của **java.lang.Integer**, mở **Call Stack** và chọn frame ứng dụng **parsePort**. Tại đây bạn kiểm tra input, dữ liệu truyền từ caller và quyết định gọi parse. Không nhất thiết phải **Step Into** sâu vào thư viện JDK, nhất là khi mã thư viện đã được kiểm thử và vấn đề thực tế nằm ở đối số ứng dụng cung cấp.

## 7. Thực hành breakpoint cho exception chưa được bắt

Chạy mode:

~~~bash
java ExceptionBreakpointLab uncaught
~~~

Bật breakpoint **NumberFormatException** với tiêu chí **uncaught**. Trong mode này, **parsePort("bad-uncaught")** được gọi từ main và không có catch NumberFormatException hoặc superclass phù hợp trong đường gọi ứng dụng này. Theo execution path của chương trình nhỏ, lỗi thoát khỏi main, nên debugger hỗ trợ uncaught exception breakpoints có cơ hội dừng khi exception xuất hiện.

Khi dừng:

1. Ghi nhận **thread hiện tại**: ở đây là main.
2. Xác nhận **exception type** và message chứa giá trị đầu vào không parse được.
3. Tìm **parsePort** trong Call Stack và chọn frame của ứng dụng.
4. Xem biến text và dòng gọi parseInt.
5. Xem frame **main** để hiểu caller đưa chuỗi từ đâu.
6. Resume để quan sát cơ chế uncaught exception handler và stack trace trên stderr.

**Chưa được bắt không đồng nghĩa “không có ai từng catch ở cả hệ thống”.** Một lỗi có thể không được bắt ở thread hiện tại nhưng thread uncaught handler, runtime, framework hoặc process supervisor vẫn thu thập/ghi nhận nó ở một cơ chế khác. Đừng suy ra một exception breakpoint không dừng nghĩa là không có lỗi; cấu hình debugger, attach sai process, class filter, handler của framework hoặc tối ưu runtime có thể ảnh hưởng sự kiện bạn quan sát.

### Nếu ứng dụng tiếp tục chạy sau uncaught exception

Ở chương trình nhiều thread, một worker có uncaught exception có thể kết thúc nhưng các thread khác tiếp tục chạy. Việc thread main kết thúc bất thường cũng không nhất thiết tắt một process còn thread non-daemon. Cần phân biệt **uncaught của thread** với **process termination** và lỗi được framework chuyển thành trạng thái của job.

## 8. Vì sao lỗi wrapped có thể dừng nhiều lần?

Chạy mode **wrapped** và thử hai exception breakpoints: **NumberFormatException** và **IllegalArgumentException**.

Trong **wrappedCase()**:

~~~java
// Đoạn trích từ chương trình đầy đủ.
try {
    parsePort("bad-port");
} catch (NumberFormatException original) {
    throw new IllegalArgumentException("invalid port", original);
}
~~~

Trình tự logic:

1. **Integer.parseInt** ném NumberFormatException.
2. **wrappedCase** bắt NumberFormatException.
3. Catch tạo một **IllegalArgumentException** mới với message **invalid port** và cause là original.
4. Lệnh **throw new ...** ném wrapper.
5. **main** bắt wrapper và in loại outer/cause.

Vì NumberFormatException **kế thừa IllegalArgumentException**, một breakpoint đặt trên **IllegalArgumentException** có thể khớp cả **NumberFormatException** lẫn wrapper, tùy debugger có áp dụng bộ lọc **bao gồm subclass** hay chỉ khớp chính xác class. Đây là lý do phổ biến dẫn đến số lần dừng nhiều hơn dự tính.

Đừng kết luận đã có ba sự cố riêng chỉ vì debugger dừng nhiều lần. Cần xem:

- **Exception object identity** (nếu debugger có thể hiển thị và so sánh).
- **Exception type thực tế** tại mỗi lần dừng.
- **Cause** của wrapper có phải original vừa bắt hay không.
- **Thread** và **stack frame** ở từng sự kiện.
- Việc một đoạn catch **throw lại cùng object** khác với **tạo wrapper object mới**.

Trong ví dụ, outer được bắt ở main nên nếu bật loại break khi **caught**, debugger có thể dừng ở sự kiện wrapper dù chương trình cuối cùng vẫn in kết quả và kết thúc bình thường.

## 9. Debug try-with-resources và lỗi suppressed

Chạy mode **suppressed**. Đặt exception breakpoints cho:

- **NumberFormatException** — lỗi chính từ **parsePort("bad")**.
- **IOException** — lỗi từ **FaultyResource.close()**.

Trình tự:

1. Phần thân try ném NumberFormatException.
2. JVM bắt đầu cleanup resource theo cơ chế try-with-resources.
3. **close()** ném IOException.
4. Cơ chế tự động giữ NumberFormatException là **primary** và gắn IOException vào **primary.getSuppressed()**.
5. Catch NumberFormatException đọc suppressed và in hai dòng kết quả.

Nếu debugger dừng ở IOException từ close, **điều đó không có nghĩa IOException là nguyên nhân gây ra lỗi parse**. Nó là lỗi phụ xảy ra trong khi đang thu hồi tài nguyên. Người điều tra cần đọc cả primary và suppressed, đồng thời hỏi liệu cleanup thất bại có làm tài nguyên hoặc trạng thái hệ thống không đáng tin không.

**Một bẫy trong debugger:** Khi pause ngay tại lệnh **throw new IOException(...)** trong close, bước gắn IOException vào danh sách suppressed của NumberFormatException **chưa chắc đã thực hiện xong**. Muốn xác minh mảng suppressed cuối cùng, hãy tiếp tục đến catch hoặc breakpoint sau điểm cleanup, rồi kiểm tra **original.getSuppressed()**. Trạng thái object tại điểm throw và sau khi cleanup hoàn tất là hai thời điểm khác nhau.

Nếu dùng **finally** thủ công với **close()** có thể ném exception, lỗi cleanup còn có thể che mất primary. Try-with-resources là cách chuẩn của Java để lưu các lỗi phụ mà vẫn giữ nguyên lỗi chính trong trường hợp này.

## 10. Debug exception bên trong CompletableFuture

Chạy mode **async** và đặt exception breakpoint cho NumberFormatException. Supplier:

~~~java
// Đoạn trích từ chương trình đầy đủ.
CompletableFuture<Integer> stage =
        CompletableFuture.supplyAsync(() -> parsePort("bad-async"));
~~~

được thực thi bởi executor mặc định của CompletableFuture trong ví dụ này. **NumberFormatException** ném trong thread thực thi supplier, không phải tại dòng **stage.join()** trên main. CompletableFuture thu thập lỗi để hoàn tất stage một cách bất thường; khi main gọi join, nó nhận **CompletionException** có cause là NumberFormatException.

Để debug hiệu quả:

1. Chạy bằng debugger có hỗ trợ debug thread khác main.
2. Bật break **NumberFormatException** (bao gồm caught nếu cần).
3. Khi dừng, xem **Threads** để xác định thread đang thực thi supplier.
4. Kiểm tra frame ứng dụng **parsePort** và tham số **text = "bad-async"**.
5. Resume rồi có thể quan sát **CompletionException** tại **join()** nếu đặt breakpoint cho loại này.
6. Xem **getCause()** của CompletionException để nối lỗi consumer nhận với lỗi worker tạo.
7. Không dùng mỗi frame **main** ở join để kết luận lỗi nằm trong join; điểm gây lỗi thực sự có thể ở lambda của supplier.

**Chú ý:** Lỗi trong CompletableFuture thường được framework **bắt** và chuyển thành exceptional completion. Vì vậy một exception mà bạn nghĩ “task bị hỏng” có thể được debugger phân loại theo **caught** ở thời điểm ném. Chỉ bật **uncaught** có thể không nhìn thấy lỗi gốc. Hãy chọn cả caught/uncaught khi đang điều tra, rồi thu hẹp sau khi tìm được vị trí.

Một số trình gỡ lỗi hỗ trợ **async stack traces** hoặc hiển thị liên kết giữa các stage; tính năng và độ đầy đủ tùy môi trường. Stack trace tiêu chuẩn của một Throwable không thể bảo đảm tái tạo toàn bộ hành trình request xuyên thread, executor và service. Dùng task ID, request ID hoặc correlation ID nếu cần nối sự kiện bên ngoài call stack của một thread.

## 11. Caught, uncaught và checked/unchecked là hai phân loại khác nhau

Một nhầm lẫn rất phổ biến là cho rằng:

- **Checked exception** đồng nghĩa lỗi sẽ được bắt.
- **Unchecked exception** đồng nghĩa lỗi sẽ không được bắt.

Hai phát biểu đều **sai**.

**Checked/unchecked** là quy tắc **kiểm tra tại compile time**:

- **IOException** là checked: phương thức gọi code có thể ném nó phải bắt hoặc khai báo throws theo luật Java.
- **NumberFormatException** và **IllegalArgumentException** là unchecked: compiler không buộc khai báo/catch.

Trong khi đó, **caught/uncaught** phản ánh **đường xử lý của exception lúc chạy**. Một RuntimeException hoàn toàn có thể được catch. Một checked exception có thể được khai báo throws qua nhiều tầng và cuối cùng không được một handler phù hợp bắt trong thread đang thực thi.

Ví dụ **main(String[] args) throws IOException** trong chương trình chính cho phép close() ném checked IOException đi ra ngoài nếu đường chạy không có catch phù hợp. Điều này không biến IOException thành unchecked; compiler chấp nhận vì chữ ký main đã khai báo loại lỗi.

Chọn exception breakpoint theo **đường cần quan sát**, không theo việc exception có bắt buộc ghi **throws** trong mã nguồn hay không.

## 12. Thêm breakpoint theo exception cụ thể hay Throwable chung?

Cấu hình breakpoint quá rộng có thể khiến debugger dừng liên tục ở hàng nghìn exception mà framework cố ý dùng làm tín hiệu điều khiển. Trong một ứng dụng lớn, nhiều thư viện thử parse, bắt lỗi rồi fallback, kiểm tra classpath, truy cập tài nguyên không tồn tại theo dự kiến hoặc dùng exception để triển khai cơ chế nội bộ.

Có ba chiến lược:

| Chiến lược | Khi nên dùng | Rủi ro |
| --- | --- | --- |
| **Một loại cụ thể** như NumberFormatException | Đã biết lỗi chính qua stack trace/log | Có thể bỏ qua wrapper hoặc loại lỗi khác |
| **Nhóm superclass** như IOException hoặc RuntimeException | Đang khoanh vùng một nhóm có quy luật | Có thể khớp nhiều subclass và gây dừng nhiều |
| **Throwable / All exceptions** | Điều tra ngắn khi chưa biết loại hoặc tìm lỗi bị nuốt | Rất nhiều sự kiện, chậm và khó tập trung |

**Khuyến nghị thực hành:** Bắt đầu từ exception type đã biết trên log hoặc từ hành vi quan sát. Bật caught khi nghi lỗi bị catch/nuốt; bật uncaught khi process hoặc thread kết thúc bất thường. Dùng filter theo package/class/thread/method khi IDE hỗ trợ. Sau khi xác định loại và vị trí, giảm phạm vi breakpoint để tránh nhiễu.

Cần hiểu inheritance khi đặt breakpoint superclass: NumberFormatException extends IllegalArgumentException, IllegalArgumentException extends RuntimeException, RuntimeException extends Exception. Không mặc định tính năng **subclass inclusion** được bật hoặc tắt giống nhau ở mọi debugger; hãy kiểm tra setting.

## 13. Bộ lọc class, package, thread và điều kiện

Một số IDE/debugger cho phép:

- Chỉ dừng khi exception phát sinh trong class hoặc package phù hợp.
- Bỏ qua thư viện JDK hoặc framework theo class filters.
- Chỉ quan sát một thread nhất định, nếu debugger hỗ trợ.
- Tạm ngắt tất cả thread hoặc chỉ thread chứa sự kiện.
- Dùng điều kiện biểu thức nếu có hỗ trợ.
- Chọn chỉ caught, chỉ uncaught, hoặc cả hai.

Các bộ lọc giảm nhiễu nhưng cũng có thể làm biến mất sự kiện cần xem. Ví dụ lỗi thực sự được ném bên trong **Integer.parseInt** thuộc package **java.lang**; nếu lọc chỉ cho dừng ở những exception ném trong package ứng dụng, debugger có thể không dừng dù một frame ứng dụng **parsePort** nằm trong stack.

Không nhầm **throwing class filter** với việc tìm **frame ứng dụng**. Một exception được ném bởi JDK có thể liên quan trực tiếp đến input sai từ ứng dụng.

Khi đặt điều kiện, cũng cần thận trọng:

- Điều kiện đánh giá tại breakpoint có thể chạy nhiều lần.
- Một số biểu thức tham chiếu biến chỉ có sẵn ở frame nhất định.
- Việc evaluate method có thể làm thay đổi trạng thái ứng dụng.
- Điều kiện phức tạp có thể làm debugger chậm và khó tái hiện.
- Nếu breakpoint không dừng, hãy thử bỏ bộ lọc trước khi kết luận exception không xuất hiện.

Một chiến lược gọn là **loại exception cụ thể → caught/uncaught đúng nhu cầu → loại bỏ filter quá chặt → kiểm tra thread/frame → thêm filter dần**.

## 14. Dùng Call Stack để đọc trạng thái ngay trước catch

Khi exception breakpoint kích hoạt, ưu tiên quy trình:

1. Xem **loại Throwable đang ném**, message và các thuộc tính liên quan nếu debugger hiện.
2. Xem **thread** đang dừng.
3. Xác định **frame đầu** trong debugger: có thể là thư viện Java.
4. Chuyển sang **frame đầu thuộc ứng dụng có liên quan**.
5. Đọc biến cục bộ, parameter, field của this và các object liên quan.
6. Đi xuống frame caller để tìm nguồn input và đường đi của dữ liệu.
7. So sánh với **điều kiện hợp lệ theo hợp đồng**, không chỉ đoán từ message.
8. Step/Resume để xem exception được catch ở đâu hoặc thoát ra ngoài.
9. Khi tới catch, kiểm tra logic quyết định retry, fallback, wrap, rollback hoặc logging.
10. Nếu kết quả cuối không khớp kỳ vọng, đặt thêm line breakpoint sau catch để xác minh trạng thái đã thay đổi.

**Step Into, Step Over và Step Out** điều khiển bước thực thi theo frame; chúng không tự động đưa bạn tới frame gốc của cause nếu bạn đang dừng ở wrapper. Hãy dùng cả Call Stack và đối tượng **Throwable.getCause()**.

Tại một breakpoint trong debugger, **stack trace của Throwable** và **call stack đang chạy** không nhất thiết giống hệt: Throwable thường giữ dấu vết lúc nó được tạo/thu nhận, trong khi debugger hiển thị stack của thread tại thời điểm pause. Đặc biệt với catch/rethrow, async và các wrapper mới, hai loại stack có thể khác nhau mà không mâu thuẫn.

## 15. Trường hợp exception được catch rồi nuốt

Một lỗi bị bỏ qua có thể khiến ứng dụng tiếp tục chạy nhưng tạo sai dữ liệu:

~~~java
// Phản mẫu minh họa, không phải chương trình độc lập.
try {
    updateDatabase();
} catch (Exception ignored) {
    // Không log, không trả lỗi và tiếp tục.
}
markOrderCompleted();
~~~

Nếu updateDatabase thất bại, dòng **markOrderCompleted()** vẫn có thể được gọi. Cách điều tra:

1. Nghi ngờ một loại exception theo nghiệp vụ hoặc log của dependency.
2. Bật exception breakpoint **caught** cho loại phù hợp.
3. Chạy lại tình huống lỗi.
4. Khi breakpoint kích hoạt, xác định call stack và thông điệp lỗi.
5. Continue đến catch hoặc đặt line breakpoint trong catch.
6. Kiểm tra phần catch có nuốt lỗi, thay exception, trả fallback hay tiếp tục sai hay không.
7. Sửa hợp đồng lỗi và logic cập nhật trạng thái, không chỉ thêm log nếu dữ liệu vẫn sai.

Với đoạn code trên, biến tên **ignored** không biến exception thành vô hại. Người viết API phải quyết định có được phép bỏ qua lỗi cụ thể nào trong ngữ cảnh đó. Nếu một thao tác là optional và lỗi thuộc tập có thể chấp nhận, cách xử lý nên có thông điệp, metrics hoặc fallback có lý do rõ ràng.

**Exception breakpoint caught** đặc biệt có ích ở đây vì “không thấy exception trên terminal” không có nghĩa exception không từng được ném.

## 16. Khi exception là điều khiển luồng hợp lệ

Không phải exception nào bị catch cũng là bug. Một số thao tác cố ý:

- Thử parse input và báo lỗi validation.
- Tìm optional file rồi fallback sang default có kiểm soát.
- Kiểm tra token/credentials rồi từ chối yêu cầu sai.
- Nhận CancellationException khi Future bị hủy theo yêu cầu.
- Nhận InterruptedException rồi truyền tín hiệu dừng lên tầng sở hữu tác vụ.
- Retry theo chính sách hữu hạn với backoff khi dependency tạm thời thất bại.

Nếu bật breakpoint cho mọi **caught exception**, debugger có thể dừng ở tất cả các tình huống được thiết kế này, gây hiệu ứng “first-chance exception noise”. Đừng sửa mã chỉ vì exception breakpoint đã dừng. Cần so sánh hành vi với hợp đồng, tần suất, bối cảnh và kết quả nghiệp vụ.

Cũng đừng gọi mọi exception caught là “handled successfully”: catch có thể rethrow, wrap, retry rồi thất bại, hoặc swallow sai. Hãy theo dõi từ **throw point** đến **boundary nhận kết quả cuối**.

## 17. Debugger và exception qua reflection, proxy, callback

Trong ứng dụng Java thực tế, lỗi có thể đi qua reflection hoặc proxy:

- **Method.invoke()** có thể bọc exception do method được gọi ném vào **InvocationTargetException**.
- Một proxy/framework có thể chuyển exception thấp thành loại exception riêng.
- Callback được framework gọi có thể bị catch bởi event loop hoặc scheduler.
- **Future.get()** có thể truyền lỗi tác vụ thông qua **ExecutionException**.
- **CompletableFuture.join()** thường truyền exceptional completion bằng **CompletionException**.

Trong những trường hợp này, một exception “nguyên bản” có thể được debugger xem là caught tại framework, dù request nghiệp vụ cuối cùng thất bại. Cần quan sát cả **throw point**, **wrapper creation**, và **boundary trả lỗi**.

Gợi ý cách đặt breakpoint:

1. Bắt đầu từ wrapper trong log, ví dụ CompletionException.
2. Xem **getCause()** để tìm exception gốc.
3. Đặt exception breakpoint cho loại cause, bật caught nếu framework có thể bắt nó.
4. Dùng class/thread filters để thu hẹp sau khi đã thấy điểm ném.
5. Đặt thêm line breakpoint ở nơi framework hoặc service chuyển lỗi nếu cần phân tích quá trình mapping.

Không kết luận **framework đã nuốt exception** chỉ vì nó catch để tạo Future đã hoàn tất bất thường. Cơ chế này có thể chính là hợp đồng báo lỗi của API bất đồng bộ.

## 18. Điều tra exception liên quan interruption và cancellation

Exception breakpoint cho **InterruptedException** có thể dừng ở một API blocking đang bị interrupt. Nhưng cần biết:

- **InterruptedException** biểu diễn thao tác chờ của **thread đó** đã nhận tín hiệu ngắt.
- **CancellationException** biểu diễn một Future/kết quả đã canceled, không nhất thiết đồng nghĩa worker đã ngừng.
- **TimeoutException** từ **Future.get(timeout)** biểu diễn lượt chờ quá hạn, không tự cancel task.
- **ExecutionException** của **Future.get()** có thể chứa cause là lỗi từ **worker**.

Các exception này có thể được catch rất thường xuyên trong executor, thread pool hoặc ứng dụng có timeout. Bật caught exception breakpoint cho **InterruptedException** trên toàn JVM có thể khiến debugger dừng tại các đường shutdown hoàn toàn hợp lệ.

Nên kiểm tra:

1. Thread nào bị interrupt và do ai yêu cầu?
2. Catch có **throw e** lên trên nếu hợp đồng cho phép không?
3. Nếu không thể truyền checked exception, catch có khôi phục **Thread.currentThread().interrupt()** và kết thúc phù hợp không?
4. Công việc đã thay đổi trạng thái trước khi dừng chưa?
5. Một Future canceled có tiếp tục có side effect không?
6. Các lần timeout có làm mã retry vô hạn hoặc tạo duplicate request không?

Debugger giúp nhìn trạng thái tại điểm lỗi, nhưng không tự bảo đảm rollback. Khi đang pause các thread, việc quan sát các race và timeout cũng có thể thay đổi vì **thời gian dừng dưới debugger** không giống thời gian thực thi bình thường.

## 19. Breakpoint làm thay đổi timing của ứng dụng nhiều luồng

Đặt exception breakpoint trong một hệ thống đồng thời có thể thay đổi hành vi bạn đang điều tra:

- Nếu debugger tạm dừng **all threads**, một worker đang giữ lock khiến các thread khác cùng dừng hoặc bị chặn.
- Nếu chỉ dừng **event thread**, các worker khác có thể tiếp tục sửa object và làm trạng thái biến đổi trước khi bạn xem xong.
- Pause có thể khiến các deadline của service bên ngoài hết hạn, vì hệ bên ngoài không bị debugger của bạn làm dừng.
- Một race rất hiếm khi chạy bình thường có thể biến mất hoặc xảy ra thường xuyên hơn khi có breakpoint.
- Method evaluation có side effect có thể gây deadlock nếu gọi hàm cần lock đang được một thread bị suspend giữ.

Do đó:

1. Xác định debugger tạm dừng **thread hiện tại** hay **toàn process**.
2. Ghi lại timestamp, thread name và correlation ID.
3. Không dựa vào một lần chạy dưới debugger như bằng chứng duy nhất của race.
4. Với lỗi timing, kết hợp logging có cấu trúc, metrics, thread dump, unit/concurrency tests.
5. Nếu lỗi biến mất khi debug, đừng vội loại trừ race hoặc visibility issue.

Trong môi trường production, attach debugger và suspend thread có thể ảnh hưởng SLA, request timeout và tính toàn vẹn hệ thống. Chỉ làm khi có quyền, quy trình vận hành và môi trường phù hợp; ưu tiên tái hiện trên staging/test.

## 20. Ghi nhận kết quả sau khi debug

Sau khi tìm được điểm phát sinh, tài liệu hóa ngắn gọn:

- **Loại exception gốc:** ví dụ NumberFormatException.
- **Nơi ném:** ví dụ Integer.parseInt được gọi bởi ExceptionBreakpointLab.parsePort.
- **Đầu vào/tình trạng:** chuỗi không thể chuyển thành số, biến text trong frame.
- **Đường bắt hoặc truyền lỗi:** caught, wrapped, suppressed, async hay uncaught.
- **Cause/suppressed:** lớp wrapper nào nhận lỗi, lỗi cleanup nào phụ.
- **Tác động:** request bị từ chối, task hoàn tất bất thường, dữ liệu có thay đổi hay không.
- **Giải pháp:** validation, hợp đồng API, trạng thái hoặc cơ chế cleanup nào cần sửa.
- **Cách tái hiện/test:** mode, input và assertion cần thiết.

Đừng chỉ chụp một ảnh màn hình debugger rồi bỏ qua thông tin có thể kiểm chứng bằng test. Việc sửa bug cần được chứng minh bằng test đúng trạng thái và loại exception, kể cả đường thành công.

### Tránh log trùng

Không nhất thiết thêm **printStackTrace()** ở mọi tầng. Nếu catch chỉ wrap/rethrow, một lỗi có thể xuất hiện thành nhiều bản log. Thường nên giữ **Throwable cause/suppressed** khi chuyển tầng và ghi log một lần ở ranh giới xử lý có trách nhiệm. Mục tiêu của exception breakpoint là **tìm nguồn**, không phải thêm noise vào production logs.

## 21. Kiểm thử để xác nhận nguyên nhân, không chỉ bắt đúng exception

Một test tốt cho chuỗi caught/wrapped/suppressed cần khẳng định:

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| Caught | Đã đi vào catch đúng loại, không có side effect không mong muốn |
| Uncaught | Exception thoát khỏi phạm vi thử nghiệm như dự kiến; với process con có thể kiểm tra exit code và stderr |
| Wrapped | Wrapper giữ cause đúng instance gốc, không chỉ trùng message |
| Suppressed | Primary giữ nguyên, close failure trong getSuppressed đúng thứ tự |
| Async | Consumer nhìn wrapper phù hợp, cause chỉ về lỗi supplier |
| Retry/fallback | Chỉ xử lý loại lỗi được cho phép; không biến lỗi nguy hiểm thành thành công |
| Multi-thread | Thread và trạng thái sau lỗi phù hợp với hợp đồng hủy/đồng bộ |

Một **exception breakpoint không phải unit test**. Nó chỉ cho phép dừng tại một sự kiện trong một lần chạy. Bạn vẫn cần test để xác nhận logic đúng ở nhiều input, thời điểm và môi trường. Với code ở mục 5, có thể dùng các mode chạy độc lập để xác minh loại exception và cause; riêng mode uncaught nên chạy như process con nếu muốn test exit code, tránh làm hỏng chính tiến trình test.

Nếu unit test dùng framework có assertThrows, nên assert loại exception và **getCause()**, **getSuppressed()**, trạng thái trước/sau, không dừng ở việc một exception bất kỳ xuất hiện.

## 22. Những lỗi cấu hình và lỗi sử dụng thường gặp

**Chỉ bật uncaught rồi không tìm thấy lỗi đã catch.** Bật caught exception breakpoint cho loại đúng, nhất là khi nghi lỗi bị framework bắt hoặc chuyển thành fallback.

**Bật Throwable + caught trên toàn JVM quá lâu.** Có thể tạo hàng nghìn lần dừng ở lỗi được xử lý hợp lệ. Thu hẹp type, class, package, thread hoặc condition có chủ đích.

**Tưởng debugger dừng nghĩa là bug chắc chắn.** Một exception được catch có thể là đường xử lý input sai hoàn toàn đúng. Phải kiểm tra hợp đồng nghiệp vụ.

**Nhầm frame đầu JDK với bug JDK.** Đọc frame ứng dụng đã gọi phương thức thư viện và đầu vào đã truyền.

**Quên subclass matching.** Breakpoint trên IllegalArgumentException có thể khớp NumberFormatException nếu cấu hình tính cả lớp con; số lần dừng lớn hơn dự tính.

**Không nhận ra wrapper.** Chỉ dừng tại CompletionException ở join rồi sửa consumer, bỏ qua NumberFormatException trong supplier. Phải xem cause.

**Quên lỗi cleanup.** IOException ở close có thể là suppressed, không phải primary. Xem Throwable tree trước khi kết luận.

**Cho rằng uncaught = cả process chắc chắn chết.** Một worker thread có thể kết thúc trong khi process vẫn chạy; framework có thể bắt và chuyển lỗi thành trạng thái job.

**Không kiểm tra debugger đang attach đúng JVM.** Có thể nhầm bản build, server instance, test fork hoặc process dùng port khác.

**Lấy biến ở frame sai.** Khi đang ở Integer.parseInt, biến local của parsePort có thể chưa hiển thị ở frame hiện tại; chuyển frame ứng dụng.

**Đánh giá biểu thức có side effect.** Method evaluation có thể mutate state, gọi I/O, lock hoặc ném exception mới, làm điều tra sai lệch.

**Cố định số dòng từ stack trace để so với source khác bản.** Xác nhận code và build đang chạy trước khi sửa.

**Để breakpoints nhạy cảm tồn tại trong môi trường cần ổn định.** Xóa/tắt các exception breakpoints quá rộng sau khi debug, giữ cấu hình tối thiểu cho lần tái hiện tiếp theo.

## 23. Checklist áp dụng từng bước

Trước khi kết thúc một phiên debug, hãy kiểm tra:

1. **Đúng JVM?** Debugger đang attach đúng process, classpath và phiên bản mã?
2. **Đúng exception type?** Breakpoint theo loại thực tế, không chỉ theo wrapper ở log?
3. **Caught/uncaught?** Chọn chế độ đúng với đường xử lý đang nghi ngờ?
4. **Inheritance?** Class breakpoint có match subclasses và tạo lần dừng phụ không?
5. **Filters?** Có loại trừ vị trí ném trong thư viện hoặc thread đang cần xem không?
6. **Throw point?** Đã xem frame của nơi Throwable được ném và frame ứng dụng liên quan chưa?
7. **Variables?** Đã ghi nhận đầu vào, trạng thái object và điều kiện dẫn đến lỗi chưa?
8. **Catch path?** Đã theo dõi exception đến catch, wrapper, retry, fallback hoặc uncaught handler chưa?
9. **Cause/suppressed?** Đã phân biệt lỗi chính, lỗi được wrap và lỗi cleanup chưa?
10. **Concurrency?** Đã xác định suspend policy và ảnh hưởng của việc pause tới thread/race/timeout chưa?
11. **Kết quả?** Đã xác minh tác động trạng thái và tạo test tái hiện chưa?
12. **Thu dọn?** Đã tắt breakpoint quá rộng, bảo vệ thông tin nhạy cảm và giữ log/test cần thiết chưa?

**Kết luận:** Exception breakpoint cho **caught exceptions** giúp tìm lỗi đã được bắt hoặc bị framework chuyển đổi mà console không thể hiện trực tiếp. Exception breakpoint cho **uncaught exceptions** giúp xác định đường lỗi không có handler phù hợp trong thread đang chạy. Cả hai đều có ích nhất khi kết hợp với **Call Stack**, biến cục bộ, **cause**, **suppressed** và hiểu biết về thread/async. Hãy dùng debugger để tìm chính xác *nơi exception được ném* và *nó được xử lý ở đâu*, rồi xác nhận nguyên nhân và cách sửa bằng kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-diagnostics-and-logging-011">Đối chiếu bytecode, phiên bản thư viện và mã nguồn khi trace không khớp</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu bytecode, phiên bản thư viện và mã nguồn khi trace không khớp

## 1. Vấn đề: stack trace trỏ tới một dòng không giống mã đang mở

Một exception Java có thể ghi:

~~~text
java.lang.IllegalStateException: failed to create invoice
    at com.example.billing.InvoiceService.create(InvoiceService.java:87)
    at com.example.billing.InvoiceController.submit(InvoiceController.java:32)
~~~

Đây là **trace minh họa**, không phải output từ chương trình chạy ở phần thực hành. Bạn mở `InvoiceService.java` trong IDE nhưng dòng 87 chỉ là comment, một lời gọi khác, hoặc thậm chí file hiện tại chỉ còn 75 dòng. Có nhiều nguyên nhân có thể xảy ra:

- JVM đang chạy **class file biên dịch từ phiên bản mã cũ**.
- Class đã được nạp từ **một JAR khác** với JAR bạn tưởng.
- Có nhiều bản của cùng một dependency, và **runtime chọn một bản khác** bản dùng để compile.
- Build sinh/biến đổi bytecode, shading, obfuscation hoặc instrument class.
- Thông tin `SourceFile` / `LineNumberTable` trong class thiếu, cũ hoặc không còn ánh xạ trực quan về source đang mở.
- Exception ngoài cùng chỉ là **wrapper**, còn dòng ném lỗi thực sự nằm trong `Caused by`.
- Đoạn thực thi thuộc lambda, bridge method, proxy hoặc class được sinh động; file và method trong trace không có ánh xạ một-một đơn giản sang một dòng Java nguồn hiện tại.

**Không sửa code theo số dòng đang hiển thị trong IDE nếu chưa xác minh artifact thực sự chạy.** Stack trace phản ánh thông tin mà runtime lấy từ bytecode/đối tượng exception và metadata của class đã được nạp; nó không tự cập nhật theo checkout Git hiện tại.

Mục tiêu của bài:

1. Hiểu vì sao bytecode chứa hoặc không chứa metadata dòng nguồn.
2. Dùng `javap` đối chiếu `LineNumberTable` với stack trace.
3. Xác định đúng bản class/JAR, class loader, module và dependency runtime.
4. Phân biệt lỗi source-line mismatch với lỗi version/binary compatibility.
5. Xử lý các tình huống khó: fat JAR, multi-release JAR, shading, obfuscation, generated code và async.
6. Tạo quy trình chẩn đoán có thể kiểm chứng bằng build ID, artifact và test tái hiện.

## 2. Ba lớp thông tin cần luôn tách biệt

Một lần điều tra thường liên quan đến ba lớp:

| Lớp | Chứa những gì? | Câu hỏi quan trọng |
| --- | --- | --- |
| **Source** | File `.java`, commit Git, generated sources, các dòng trong IDE | Đây có phải đúng source đã dùng để tạo class đang chạy? |
| **Artifact/bytecode** | File `.class`, JAR, WAR, executable JAR, image, module; constant pool, bytecode, debug attributes | Class file nào đã được compile, đóng gói và phân phối? |
| **Runtime** | JVM, class loaders, module path/classpath, container, JDK và dependency thực tế | JVM đã nạp bản class nào khi exception xảy ra? |

Ví dụ:

~~~text
Git commit A         -> javac -> Service.class (line 87)
                             -> app-old.jar
                             -> production JVM

Git commit B         -> IDE hiển thị Service.java (line 87 đã thay)
~~~

Nếu runtime vẫn chạy `app-old.jar`, việc IDE mở commit B không làm thay đổi số dòng trong stack trace từ production. Đổi nhánh Git hoặc recompile ở máy cá nhân cũng không chứng minh server đã dùng class mới.

**Mối liên hệ đúng** phải đi từ log → định danh deployment/runtime → class file thực sự được nạp → source/build tương ứng, không đi ngược từ “source mới nhất” rồi ép trace vào.

## 3. JVM dùng thông tin nào để hiển thị dòng nguồn?

Một class file có các cấu trúc/thuộc tính liên quan:

- **`SourceFile` attribute:** tên file nguồn được compiler ghi lại, thường là `Service.java`, không phải đường dẫn tuyệt đối tới repository.
- **`Code` attribute:** bytecode thực thi của từng phương thức cụ thể.
- **`LineNumberTable`:** ánh xạ từ **chỉ số bắt đầu của bytecode instruction (`start_pc`)** tới **số dòng source**. Nhiều instruction có thể cùng thuộc một dòng; một dòng source có thể có nhiều vị trí bytecode.
- **`LocalVariableTable`:** thông tin một số biến cục bộ (khi được xuất) để hỗ trợ debug; không phải thành phần bắt buộc để stack trace hiển thị file:line.
- Metadata của class/method, descriptors, constant pool và flags, giúp biết phương thức mà JVM thực thi dù source không còn ở máy.

Stack trace có thể hiện:

~~~text
at com.example.app.PriceService.compute(PriceService.java:114)
~~~

Nghĩa là runtime có thể quy chiếu frame đó tới `PriceService.java` dòng 114 theo thông tin gắn với class được nạp. Nó **không cam kết** rằng toàn bộ phép toán trong dòng 114 là nguyên nhân gốc, cũng không cam kết source bản checkout hiện tại là file đã biên dịch.

Đặc biệt, trong một biểu thức phức tạp hoặc nhiều instruction tương ứng cùng một dòng, stack trace thường chỉ định vị được **dòng**, không tự xác định chính xác subexpression nào gây exception.

### 3.1. `File.java:line` không phải đường dẫn tuyệt đối

`StackTraceElement.getFileName()` thường trả tên file như `OrderService.java`, không phải `src/main/java/com/example/OrderService.java`. Cần kết hợp tên class đầy đủ, package, module và artifact để biết file nào thực sự liên quan.

Class `Outer$Inner` thường đến từ file `Outer.java`; lambda có thể xuất hiện là `lambda$process$0` trong frame của một class. Không suy ra tên source bằng cách thêm `.java` vào tên synthetic class.

### 3.2. `Unknown Source` và số dòng âm

Nếu class thiếu `SourceFile` hoặc `LineNumberTable`, frame có thể hiện `Unknown Source` hoặc chỉ có tên file mà không có số dòng. Trong API `StackTraceElement`, `getFileName()` có thể là `null` và `getLineNumber()` có thể trả số âm.

Một số class không lưu stack trace có thể do constructor tùy biến, thông tin debug bị strip, obfuscator/bytecode transformer hoặc các cơ chế tối ưu/runtime đặc thù. **Không đoán số dòng** khi metadata không có; xác minh class đã nạp và tạo lại một bản build có debug info khi phù hợp.

## 4. Ví dụ runnable Java 17: số dòng lấy từ class file, không từ IDE

Dưới đây là chương trình độc lập minh họa stack trace có source-line metadata. Lưu thành **`BytecodeTraceDemo.java`** với dòng đầu tiên đúng là dòng khai báo class (không chèn dòng trống ở đầu) nếu muốn output số dòng khớp mẫu.

~~~java
public class BytecodeTraceDemo {
    static void verify(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative value: " + value);
        }
    }

    static void service() {
        verify(-3);
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IllegalArgumentException error) {
            System.out.println(error.getClass().getSimpleName() + ": " + error.getMessage());
            for (StackTraceElement element : error.getStackTrace()) {
                if (element.getClassName().equals(BytecodeTraceDemo.class.getName())) {
                    System.out.println(element.getMethodName() + " -> "
                            + element.getFileName() + ":" + element.getLineNumber());
                }
            }
        }
    }
}
~~~

Từ thư mục chứa source với JDK 17+:

~~~bash
javac --release 17 BytecodeTraceDemo.java
java BytecodeTraceDemo
~~~

Stdout đã kiểm chứng với `javac` mặc định lưu thông tin số dòng:

~~~text
IllegalArgumentException: negative value: -3
verify -> BytecodeTraceDemo.java:4
service -> BytecodeTraceDemo.java:9
main -> BytecodeTraceDemo.java:14
~~~

Đối chiếu trực tiếp với source:

- `verify`, dòng **4**: tạo/ném `IllegalArgumentException`.
- `service`, dòng **9**: gọi `verify(-3)`.
- `main`, dòng **14**: gọi `service()` bên trong `try`.

Trong trường hợp này, trace cho đường đi từ nơi exception phát sinh về các caller cùng class. Nếu thêm comment trước dòng 4 **và biên dịch lại**, số dòng mới sẽ phản ánh source mới. Nhưng nếu chỉnh source và **không biên dịch lại class đang chạy**, output từ bytecode cũ vẫn là `4`, `9`, `14`: đó là một ví dụ tối giản của tình huống source và artifact lệch nhau.

### 4.1. Thí nghiệm tắt metadata debug

Biên dịch lại cùng file bằng:

~~~bash
javac --release 17 -g:none BytecodeTraceDemo.java
java BytecodeTraceDemo
~~~

Stdout đã kiểm chứng:

~~~text
IllegalArgumentException: negative value: -3
verify -> null:-1
service -> null:-1
main -> null:-1
~~~

Tên class/method vẫn được JVM biết, nhưng tên file và số dòng đã mất trong thí nghiệm vì bytecode không còn debug attributes tương ứng. **`-g:none` không loại bỏ logic ném exception**; nó chỉ làm giảm khả năng đối chiếu trace với source.

Để quay về bản có thông tin line/source, biên dịch lại, ví dụ:

~~~bash
javac --release 17 -g:lines,source BytecodeTraceDemo.java
java BytecodeTraceDemo
~~~

Số dòng trả về như ví dụ mặc định với file nguồn đã cho.

Lưu ý: ghi đè `BytecodeTraceDemo.class` bằng bản mới sau mỗi lệnh compile; nếu muốn so sánh hai artifact đồng thời, hãy compile chúng vào **hai thư mục build khác nhau** bằng `-d` và chỉ rõ classpath tương ứng. Đó cũng là cách nên làm khi điều tra hai phiên bản thực tế.

## 5. Đọc bytecode và LineNumberTable bằng `javap`

`javap` là công cụ disassembler/inspector đi kèm JDK. Các tùy chọn hữu ích:

| Lệnh/tùy chọn | Mục đích |
| --- | --- |
| `javap -c -l ClassName` | In bytecode và bảng line/local-variable khi có |
| `javap -c -l -p ClassName` | Bao gồm phương thức private/package-private |
| `javap -v ClassName` | In thông tin class file chi tiết hơn: version, constant pool, attributes |
| `javap -classpath path/to/app.jar -c -l -p package.Class` | Kiểm tra class cụ thể từ một JAR |
| `javap -c -l -p path/to/Foo.class` | Kiểm tra trực tiếp một class file theo đường dẫn thích hợp |

Với ví dụ `BytecodeTraceDemo` sau khi biên dịch bản có metadata:

~~~bash
javap -c -l -p BytecodeTraceDemo
~~~

Các đoạn `LineNumberTable` liên quan của output chứa những ánh xạ như:

~~~text
static void verify(int);
    ...
    LineNumberTable:
      line 2: 0
      line 3: 0
      line 4: 4
      ...

static void service();
    ...
    LineNumberTable:
      line 8: 0
      line 9: 0
      ...

public static void main(java.lang.String[]);
    ...
    LineNumberTable:
      line 12: 0
      line 14: 0
      ...
~~~

Đây là **các trích đoạn minh họa được giản lược**, không phải toàn bộ output `javap`; các bytecode offset ngoài phần đã kiểm chứng có thể thay đổi theo compiler/options. Với test Java 17 ở trên, các ánh xạ `line 4: 4`, `line 9: 0`, `line 14: 0` đã được đối chiếu thực tế.

**Ý nghĩa của `line 4: 4`:** instruction tại bytecode offset 4 được gắn với source line 4. Đây không phải “dòng 4 ứng với bytecode dòng 4” hoặc địa chỉ bộ nhớ máy. Nếu có nhiều instruction thuộc line 4, bảng thường chỉ ghi vị trí bắt đầu cần cho ánh xạ.

Ở một số bản JDK, `javap -l` **mà không kèm `-c`** có thể chỉ in warning không xuất bảng line number như kỳ vọng; dùng `javap -c -l` hoặc `javap -v` để xem dữ liệu đầy đủ. Không lấy việc chạy một lệnh sai options rồi không thấy bảng làm bằng chứng class thiếu metadata.

### 5.1. Đối chiếu bytecode với source theo trình tự

1. Lấy **class file thực tế** từ artifact của môi trường lỗi.
2. Dùng `javap -c -l -p` với đúng classpath hoặc đường dẫn class file.
3. Tìm tên phương thức trong frame, kể cả descriptor/overload nếu có nhiều phương thức trùng tên.
4. Xem `LineNumberTable` cho method đó; xác nhận line được stack trace nhắc tới có trong metadata hay không.
5. Mở source của **build đã tạo class này**, tới đúng dòng; xem toàn bộ expression và các call sites gần đó.
6. Nếu source lệch nhưng LineNumberTable khớp trace, nhiều khả năng bạn đang mở sai source revision hoặc class bị sinh/biến đổi sau bước biên dịch.
7. Nếu LineNumberTable trong artifact lấy được không khớp trace, rất có thể bạn đã lấy **sai artifact/classloader/JAR**, hoặc runtime có bytecode instrumentation/transformation; quay lại bước xác minh class được nạp.

Trường hợp source line 100 chứa nhiều lời gọi, hãy đọc bytecode/operand stack hoặc chia biểu thức thành các bước có tên trong một test tái hiện để biết null/exception đến từ đâu. Không chỉnh code dựa duy nhất vào offset hay line table.

### 5.2. `-g`, `--release` và phiên bản runtime là các khái niệm khác nhau

- `-g:lines,source` yêu cầu compiler ghi thông tin nguồn/số dòng.
- `-g:none` tắt thông tin debug do `javac` xuất.
- `--release 17` yêu cầu compile nhắm tới API và bytecode theo Java 17; nó **không khóa phiên bản dependency bên thứ ba**, không bảo đảm deployment đã dùng đúng jar, và không bảo đảm release hiện tại của ứng dụng chính là bản bạn đang xem.
- `java -version` chỉ nói JDK/JRE tại nơi thực thi **lệnh đó**; muốn chẩn đoán server thật, cần xác minh runtime của chính tiến trình/server.
- Class file có `major version` theo Java target, nhưng version đó không mã hóa Git commit hay Maven artifact version.

Một chương trình có thể được biên dịch với `--release 17`, chạy trên JDK 21, dùng thư viện Java-compatible version mới, và vẫn có stack trace line do class đã biên dịch bằng source cũ. Những yếu tố này độc lập.

## 6. Tìm đúng JAR/class mà JVM đã nạp

Trước khi so source, hãy trả lời: **class đang bị lỗi đến từ đâu?** Trong một ứng dụng lớn có thể có nhiều class loaders, nested JAR, module path, plugin hoặc cùng tên class trong nhiều version.

### 6.1. Kiểm tra CodeSource bằng API

Đoạn Java sau là **fragment**, giả định `InvoiceService` là class ứng dụng có thể tham chiếu từ nơi chạy:

~~~java
Class<?> type = InvoiceService.class;
var domain = type.getProtectionDomain();
var codeSource = domain == null ? null : domain.getCodeSource();
System.out.println(codeSource == null
        ? "unknown"
        : codeSource.getLocation());
~~~

Nếu `CodeSource` tồn tại, URL trả về có thể chỉ ra thư mục classes hoặc JAR chứa class. Nhưng:
- `getCodeSource()` có thể `null`.
- Với JDK modules, custom loader, nested/fat jar hoặc class sinh động, vị trí hiển thị có thể không phản ánh một file JAR “thường” có thể mở trực tiếp.
- `CodeSource` không phải một proof đầy đủ về git commit/source revision.
- Đường dẫn server và thông tin triển khai không nên được log ra nơi công khai.

Đọc `getClassLoader()` và `getModule()` cũng có thể giúp phân biệt nơi lớp được nạp. Một số JDK classes có classloader `null` (bootstrap), không phải lỗi.

### 6.2. Log class loading của JVM

Với JVM hỗ trợ unified logging của Java 9+, có thể bật logging nạp class trong môi trường chẩn đoán:

~~~bash
java -Xlog:class+load=info -cp path/to/app.jar com.example.Main
~~~

Đây là lệnh **tham khảo**: `path/to/app.jar` và `com.example.Main` là placeholder đường dẫn/tên class, cần thay bằng artifact thật; với ứng dụng chạy bằng `java -jar`, module path hoặc app server, cách khởi động khác. Log nạp class có thể cho biết class được nạp từ nguồn nào.

Cần cân nhắc log volume và thông tin nhạy cảm, không tự bật debug dày đặc trên production lâu dài. Logging class load cho runtime bạn khởi chạy **không tự chứng minh** runtime khác trên production dùng cùng bộ class, trừ khi cấu hình/artifact đã được xác nhận.

### 6.3. Kiểm tra trong JAR

Ví dụ lệnh tham khảo:

~~~bash
jar tf path/to/library.jar
javap -classpath path/to/library.jar -c -l -p com.example.lib.Parser
~~~

`jar tf` liệt kê các entry được đóng gói. `javap` đọc class theo classpath bạn chỉ định. Đừng nhầm việc `javap` tìm thấy một class trong JAR với việc runtime chắc chắn **đã nạp đúng class đó**: class loader và thứ tự phân giải thực tế mới quyết định.

Một JAR có thể chứa tên `com/example/lib/Parser.class` giống JAR khác. Nếu có hai bản, kiểm tra artifact identity, checksums, vị trí classloader và kết quả load thực tế trước khi kết luận.

## 7. Phân biệt compile-time dependency và runtime dependency

Một lỗi phổ biến là IDE hoặc build compile dựa trên thư viện **v2**, nhưng runtime chạy cùng tên library **v1**.

Ví dụ tình huống giả định:

~~~text
Compile:
    app.class -> lib-api 2.0, có method parse(String, int)

Runtime:
    app.class -> lib-api 1.4, chỉ có parse(String)

Khi lời gọi được resolve:
    java.lang.NoSuchMethodError
~~~

Lỗi `NoSuchMethodError` thường là dấu hiệu **binary linking/runtime compatibility mismatch**, không phải checked exception do xử lý input thông thường. Frame ứng dụng tại dòng gọi có thể hoàn toàn đúng nếu nhìn source v2, nhưng JVM không tìm thấy method với tên/descriptor tương ứng ở class runtime.

Các lỗi cần phân biệt:

| Triệu chứng | Giải thích thường gặp | Kiểm tra |
| --- | --- | --- |
| `NoSuchMethodError` | Runtime type không có phương thức với descriptor mà bytecode gọi | `javap -p -s` trên bản class/runtime dependency; kiểm tra version |
| `NoSuchFieldError` | Field kỳ vọng ở compile không tồn tại ở runtime | Kiểm tra field descriptor và phiên bản dependency |
| `IncompatibleClassChangeError` | Class/interface/static/instance hoặc cấu trúc binary không tương thích | Kiểm tra thay đổi API và class file thực được nạp |
| `AbstractMethodError` | Runtime dispatch không tìm implementation phù hợp | Kiểm tra ABI của interface/implementation và mix versions |
| `ClassNotFoundException` | Cố tải class theo tên nhưng loader không tìm thấy | Classpath/module path, plugin loading, artifact layout |
| `NoClassDefFoundError` | Class cần khi linking/initialization không thể định nghĩa/sử dụng; cũng có thể liên quan lỗi init trước đó | Xem cause, lỗi init ban đầu, classloader và dependency |
| `UnsupportedClassVersionError` | Class file được biên dịch cho version JVM cao hơn runtime hỗ trợ (hoặc không được hỗ trợ) | `java -version` của tiến trình và `javap -verbose` của class |
| `ExceptionInInitializerError` | Static initializer ném exception | Xem `<clinit>`, cause và trạng thái class initialization |

Các lỗi liên kết nhiều trường hợp là `LinkageError`/`Error`, không nên “sửa” bằng `catch (Exception)` rồi trả fallback. Việc catch rộng có thể che nguyên nhân và khiến hệ thống tiếp tục trong trạng thái sai. **Giải quyết artifact/dependency compatibility là hướng chính**.

### 7.1. Method descriptor có quan trọng hơn tên method không?

Có. Bytecode tham chiếu method cùng **tên, lớp/interface và descriptor** (tham số/trả về). Hai bản thư viện cùng có phương thức tên `parse` nhưng descriptor khác vẫn có thể gây `NoSuchMethodError`.

Dùng:

~~~bash
javap -classpath path/to/library.jar -p -s com.example.lib.Parser
~~~

`-s` hiển thị internal type signatures (descriptors) bên cạnh signatures, hữu ích khi có overload, generic erasure, thay đổi kiểu primitive/reference hoặc return type. `javap -c -v` trên class gọi giúp nhìn constant pool và instruction invoke target khi cần.

Generic source có thể trông tương tự do type erasure, nhưng bridge methods và descriptor thực tế vẫn ảnh hưởng linking. Vì thế đừng kết luận “method tồn tại mà JVM báo NoSuchMethodError thì JVM sai” chỉ bằng cách tìm chữ `parse` trong IDE.

## 8. Truy vết dependency qua Maven/Gradle

Trong project Maven, các lệnh hay dùng:

~~~bash
mvn dependency:tree
mvn dependency:tree -Dverbose
~~~

Đây là **lệnh tham khảo cho project Maven có plugin phù hợp**; chi tiết các tùy chọn phụ thuộc phiên bản `maven-dependency-plugin`. Hãy kiểm tra dependency tree ở cấu hình đúng module/profile, xét dependency mediation, scope, exclusion và parent/BOM management. Một cây compile-time hiển thị đúng **không đủ** để chứng minh app server không tự cung cấp thêm một JAR runtime.

Trong project Gradle:

~~~bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency group:name --configuration runtimeClasspath
~~~

Đây là **lệnh tham khảo**; thay `group:name` bằng dependency thật. Trên Windows, wrapper thường là `gradlew.bat`. Lệnh `dependencyInsight` giúp xem tại sao version được chọn, conflict resolution và dependency chain trong configuration được kiểm tra. Tên configuration thực tế thay đổi theo plugin và loại project; chọn configuration tương ứng artifact runtime cần điều tra.

### 8.1. Vì sao dependency tree vẫn chưa đủ?

Bởi vì runtime có thể:
- Có dependency **provided** do application server cung cấp.
- Được đóng gói bằng fat/uber JAR khác với classpath của IDE.
- Có plugin và child classloader nạp bản khác.
- Đã bị container image cache hoặc deploy nhầm bản.
- Có class trùng trong nhiều JAR; classpath/module resolution chọn một lớp không như mong đợi.
- Dùng shaded/relocated class với namespace thay đổi.
- Đang chạy nhiều instance/version song song sau rollout.

Do đó kết hợp **dependency tree + contents of released artifact + runtime class loading evidence**. Không dùng một nguồn riêng lẻ để kết luận.

### 8.2. Cần so dependency trong đúng môi trường

Một build chạy với profile `prod` có thể khác profile `dev`; Gradle runtimeClasspath khác testRuntimeClasspath; IDE có thể thêm thư viện từ workspace mà JAR production không chứa. Bước kiểm chứng phải ghi nhận:
- Lệnh build/profile/environment thực tế.
- Lockfile/BOM/versions catalog hoặc resolved dependency versions.
- Build artifact hash và container image digest.
- Cách đóng gói và cách process được khởi động.
- JDK/runtime architecture/version thích hợp.
- Classloader hierarchy khi dùng plugin/app server.

Không suy rằng việc `mvn test` pass trên máy cá nhân chứng minh production đã chạy đúng những class đó.

## 9. Classpath, module path và class loader quyết định class nào được chọn

### 9.1. Classpath và duplicate class

Trên **class path truyền thống** (unnamed module), nếu nhiều JAR chứa cùng FQCN và có thể được một loader phân giải, thứ tự classpath và quy tắc loader có thể quyết định bản được dùng. Nhưng Java class loading thực tế còn chịu ảnh hưởng của **parent delegation**, custom class loaders và runtime embedding. Không viết luật đơn giản “JAR đầu tiên trong tất cả tình huống thắng” cho mọi deployment.

Ví dụ cấu trúc trên Linux/macOS:

~~~bash
java -cp "lib/legacy.jar:lib/new.jar:app/classes" com.example.Main
~~~

Trong Windows, dấu phân cách classpath thường là `;` thay vì `:`. Đây là ví dụ lệnh giả định; không chạy nguyên trạng nếu không có những artifact được liệt kê.

Với plugin loaders, application servers hoặc hot reload, cùng tên class có thể được nạp bởi **hai classloader khác nhau**, dẫn tới hai class runtime khác nhau ngay cả khi tên hiển thị giống nhau. Đặc biệt, lỗi `ClassCastException` kiểu “`com.example.Widget cannot be cast to com.example.Widget`” có thể liên quan classloader identity khác nhau.

### 9.2. Named modules

Trên module path, Java Platform Module System có cơ chế giải quyết module và đọc/exports rõ ràng. Cùng tên package/class trong nhiều module có thể gây vấn đề resolution/access theo quy tắc JPMS, khác với classpath phẳng. Khi stack trace có tiền tố như `my.module/com.example.Service`, dùng nó như manh mối module chứa frame. Không chỉ cắt bỏ prefix rồi đoán thư viện.

Một số công cụ hữu ích có thể là `java --list-modules`, `jar --describe-module --file path/to.jar` hoặc xem `module-info.class`. Các lệnh này chỉ có ý nghĩa khi áp dụng đúng module/artifact đang chạy.

### 9.3. Java launcher `-jar` và `-cp`

Khi chạy `java -jar executable.jar`, launcher dùng cơ chế classpath theo JAR manifest hoặc launcher riêng; chỉ nối thêm `-cp` bên ngoài không chắc có hiệu quả như chạy main class bằng `-cp`. Với Spring Boot executable JAR hoặc framework có nested-JAR class loader, runtime còn có logic nạp class riêng.

**Không đổi tùy ý `java -jar` thành `java -cp` chỉ để debug** rồi coi đó là cùng môi trường: bạn có thể vô tình thay đổi classpath và che mất chính lỗi cần điều tra. Hãy ghi nhận command line thực và tái hiện theo packaging gốc.

## 10. Hash, build ID và xác minh artifact

Khi trace không khớp source, cách đáng tin là lập một “chuỗi bằng chứng”:

~~~text
Log/trace + thời gian lỗi
          |
          v
Deployment ID / pod / instance / build ID
          |
          v
Binary artifact hoặc container image chính xác
          |
          v
JAR/class file được classloader nạp
          |
          v
Source commit + build config + generated sources tương ứng
          |
          v
So stack frame và LineNumberTable
~~~

Ở tầng artifact, có thể dùng checksum SHA-256 để nhận dạng một file JAR. Ví dụ lệnh trên PowerShell:

~~~powershell
Get-FileHash -Algorithm SHA256 -LiteralPath "C:\deploy\app.jar"
~~~

Lệnh trên là **mẫu thao tác**, đường dẫn mang tính minh họa. Trên hệ Unix có thể dùng `sha256sum app.jar`. Checksum giúp đối chiếu file **có cùng bytes hay không**; nó không tự chứng minh đây là source commit nào nếu tổ chức không lưu quan hệ build provenance.

Một artifact reproducible build có thể hỗ trợ truy vết source, nhưng không nên giả định mọi project sinh byte-identical JAR khi build lại: timestamps, tool versions, thứ tự đóng gói, generated code và metadata có thể tạo khác biệt. Nếu không thể tái tạo hash, cần đọc build manifest, CI release record, commit ID và kiểm tra class file cụ thể.

### 10.1. Nên lưu gì trong build metadata?

Các thuộc tính có giá trị chẩn đoán:
- Git commit SHA và trạng thái dirty/clean khi build.
- Build number, pipeline/job ID, thời điểm và môi trường build.
- Version ứng dụng và phiên bản JDK compiler.
- Artifact checksum và image digest.
- Resolved dependency versions hoặc lockfile.
- Tên module/component và thông tin generated source khi có.

Đừng chỉ ghi version dạng `1.0-SNAPSHOT` vào tất cả image rồi mong phân biệt hai bản deploy. Với release quan trọng, liên kết **build ID → immutable artifact → commit** là nền tảng để đối chiếu trace.

## 11. Shading, relocation và obfuscation: tên class có thể không còn như source

Khi Maven Shade/Gradle Shadow hoặc một công cụ bytecode transform **relocate** package, trace có thể hiển thị tên đã đổi. Ví dụ trong source dependency là `com.vendor.json.Parser`, nhưng runtime có thể chứa `com.myapp.shaded.vendor.json.Parser`. Mở file theo package mới trong repo có thể không có kết quả, vì class đến từ dependency đã được chuyển namespace lúc build.

Các câu hỏi cần kiểm tra:
- Có cấu hình shade/relocate không?
- Package/class được đổi từ đâu sang đâu?
- Source JAR tương ứng với version dependency nào?
- Debug metadata được giữ hay thay đổi qua transformation?
- Stack trace xảy ra trước hay sau instrumentation của agent?

Nếu dùng obfuscator, tên method/class có thể bị rút ngắn thành `a.b.c`; số dòng có thể bị xóa hoặc thay đổi. Một số hệ sinh thái có **mapping file** để giải mã; cần đúng mapping **của build đang deploy**. Mapping từ release khác có thể tạo kết quả giả đáng tin nhưng hoàn toàn sai.

**Đừng xem tên method obfuscated là tên method Java gốc.** Nếu ứng dụng có quy trình deobfuscation, áp dụng đúng file mapping và xác minh kết quả bằng class/JAR tương ứng.

### 11.1. Bytecode instrumentation

Monitoring agents, AOP, runtime weaving hoặc coverage tools có thể thêm wrapper và thay đổi đường gọi. Tùy công cụ, bytecode được instrument nhưng line numbers gốc được giữ một phần, hoặc có thể xuất hiện frame do công cụ sinh ra. Trace từ runtime instrumented có thể khác chương trình chạy cùng source khi **không** có agent.

Khi tái hiện bug, ghi nhận:
- Java agents ở lệnh khởi động.
- Build-time weaving/annotation processing.
- Proxy generators / Byte Buddy / CGLIB / aspect configurations.
- Runtime khác biệt về phiên bản instrumentation.

Không tắt agent rồi tuyên bố lỗi biến mất do code đã sửa nếu nguyên nhân có thể nằm ở instrumentation hoặc khác biệt timing.

## 12. Multi-release JAR: cùng tên class nhưng nhiều phiên bản bytecode

Một **multi-release JAR** có thể chứa class ở đường dẫn gốc và các phiên bản chuyên biệt:

~~~text
META-INF/MANIFEST.MF
com/example/Codec.class
META-INF/versions/9/com/example/Codec.class
META-INF/versions/17/com/example/Codec.class
~~~

Đây là cấu trúc minh họa; JAR phải được cấu hình multi-release đúng chuẩn, không phải mọi JAR có thư mục `META-INF/versions` đều được xử lý như một multi-release JAR.

Khi chạy trên runtime tương thích, JVM có thể chọn class versioned phù hợp với phiên bản runtime. Điều đó nghĩa là:
- Source của phiên bản gốc có thể khác implementation versioned.
- `javap` không được cấu hình version đúng có thể đọc bản class khác runtime.
- Cùng một JAR nhưng chạy trên Java 11 và Java 17 có thể dùng bytecode khác.

Có thể kiểm tra entries:

~~~bash
jar tf path/to/library.jar
~~~

Với JDK có hỗ trợ tùy chọn multi-release, có thể dùng công cụ như:

~~~bash
javap --multi-release 17 -classpath path/to/library.jar -c -l -p com.example.Codec
~~~

Lệnh mang tính **tham khảo**; cần xác minh JAR thật và phiên bản Java/toolchain đang dùng. Mục tiêu là kiểm tra đúng class bản Java 17 thay vì mặc định trỏ vào entry gốc.

### 12.1. Không nhầm target Java với entry Java-specific

Class ở `META-INF/versions/17` không có nghĩa source hiện tại đang compile bằng `--release 17` ở mọi module. Nó nói có một alternate implementation trong JAR cho runtime phù hợp. Hãy kiểm tra manifest `Multi-Release: true` và đường load thật. Nếu stack trace khớp class versioned nhưng IDE mở source base implementation, dòng và logic có thể khác xa.

## 13. Generated sources, annotation processors và code generation

Framework và tool build có thể tạo:
- Java source trong `target/generated-sources`, `build/generated` hoặc một generated directory tương đương;
- class bytecode trực tiếp không có file `.java` tương ứng;
- implementation cho interface, mapper, serializer, proxy;
- lambda/synthetic/bridge methods.

Nếu frame chỉ tới `com.example.generated.MapperImpl.map(MapperImpl.java:...)`:
1. Kiểm tra class đó có phải generated class không.
2. Tìm generated source **được sinh trong chính build production**.
3. Xác nhận processor/plugin version và cấu hình.
4. Mở file generated nếu có, không chỉ mở interface `Mapper` nguồn.
5. Xem caller đưa input gì vào generated code.
6. Nếu file generated được tạo lại từ source mới, số dòng có thể lệch; không dùng nó để suy ra line number production.

Với bytecode được sinh trực tiếp, không chắc có source để mở. Khi đó `javap -c -l -v`, bytecode inspection và cấu hình generator là các công cụ chính.

### 13.1. Lambda và bridge methods

Trace có thể chứa `lambda$process$0` hoặc bridge method do compiler sinh. Khi tìm trong IDE, tìm **method bao lambda** và source line được ghi trong `LineNumberTable`; không cố tìm một file tên `lambda$process$0.java`.

Generics có thể tạo bridge methods để duy trì polymorphism sau type erasure. Method có thể tồn tại trong bytecode nhưng không được viết tường minh ở source. Dùng `javap -v -p` để nhận diện flags như `ACC_BRIDGE`/`ACC_SYNTHETIC` và đọc method thực mà nó gọi khi cần.

## 14. JDK/JRE version và stack trace formatting

JDK version ảnh hưởng:
- Các method/frames bên trong thư viện chuẩn.
- Cách xử lý class file version.
- Cơ chế module/class loading.
- Helpful NullPointerException messages trong trường hợp được hỗ trợ.
- Tối ưu JIT và hành vi stack capture trong một số tình huống đặc biệt.

Không hardcode một số dòng từ JDK internals như `java.util.Objects.java:123` làm chân lý cho mọi version. Cùng phương thức JDK có thể ở dòng khác giữa các release/update.

### 14.1. Những thứ JIT tối ưu không làm bạn phải bỏ qua

JIT có thể inline hoặc tối ưu nhiều lời gọi, nhưng không vì vậy mà mọi line number sai đều do JIT. JVM cố giữ ngữ nghĩa exception/stack trace theo cách hỗ trợ chẩn đoán, trong khi có những tối ưu và tình huống đặc biệt có thể làm trace khác điều mong đợi. Chẳng hạn một số JVM có tối ưu cho exception ném lặp lại rất nhiều lần khiến stack trace bị lược bớt trong điều kiện cụ thể.

Nếu một trace lặp lại dưới tải rất cao bị thiếu frame, cần cân nhắc:
- Bytecode có line number table không?
- Throwable có bật stack trace không?
- Logger có truncate exception không?
- Có JVM flag/tối ưu gây thay đổi thông tin stack không?
- Exception bị tạo một lần rồi ném lại nhiều lần không?

**Chỉ suy luận về JIT sau khi đã loại trừ những nguyên nhân phổ biến** như sai artifact, sai source revision hoặc thiếu debug info.

## 15. Root cause nằm trong `Caused by`, không nhất thiết ở trace ngoài

Ví dụ:

~~~text
java.util.concurrent.CompletionException: task failed
    at java.base/java.util.concurrent.CompletableFuture...
    at com.example.app.ApiHandler.handle(ApiHandler.java:61)
Caused by: java.lang.NoSuchMethodError: ...
    at com.example.app.Adapter.callLibrary(Adapter.java:42)
    ...
~~~

Đây là trace **minh họa với dấu rút gọn**, không phải stack thật. Exception ngoài cùng đến từ cách future/callback truyền lỗi; cause là `NoSuchMethodError`, gợi ý runtime dependency ABI mismatch.

Điều tra đúng:
1. Xem cả exception ngoài cùng và cause, không chỉ frame có tên `CompletableFuture`.
2. Ở `Adapter.callLibrary:42`, xác định lời gọi method mà bytecode đang thực hiện.
3. Dùng `javap -c -v` trên **class Adapter đã deploy** để biết target method descriptor.
4. Dùng `javap -p -s` trên **library class được runtime nạp** để xem method đó có tồn tại không.
5. So dependency versions và runtime class loader.
6. Xác nhận artifact/build IDs và sửa packaging/dependency resolution thay vì chỉ thay `catch`.

Nếu cause là `IOException` hoặc exception nghiệp vụ thông thường, quy trình sẽ khác: cần kiểm tra input/state, không tự áp lý thuyết ABI mismatch cho mọi failure.

### 15.1. `... N more` và suppressed traces

`Throwable.printStackTrace()` có thể rút gọn các frame trùng giữa exception ngoài và cause thành `... N more`. Nó không có nghĩa class file thiếu đúng N dòng. `Suppressed` thường là lỗi cleanup trong try-with-resources; phần đó có stack riêng. Một logger cũng có thể cắt ngắn trace do cấu hình; cần phân biệt **nén frame trùng của Java** với **log truncation**.

Nếu log đã bị cắt mất cause hoặc suppressed, hãy bổ sung telemetry an toàn và thu lại tại lần tái hiện phù hợp. Không bịa một cause chỉ vì có một dòng trace rời.

## 16. Truy vết binary compatibility bằng bytecode, không chỉ source API

Giả sử `Service` trong app đã biên dịch với thư viện phiên bản B. Trong source B có:

~~~java
public String find(String key, int limit) { ... }
~~~

Đây là **chữ ký minh họa**, không phải Java class độc lập. Nhưng runtime nạp bản A chỉ có `find(String)`. Dù method gọi ở source app có tên `find`, JVM phải resolve theo descriptor đã được ghi trong constant pool và bytecode.

### 16.1. Cần so những gì?

- FQCN của class/interface được tham chiếu.
- Tên method/field.
- Các tham số và kiểu trả về trong JVM descriptor.
- `static` so với instance; class so với interface.
- Access modifiers và khả năng truy cập qua module.
- Bridge/erasure nếu có generics.
- API version thực và thay đổi binary-incompatible.

Các command tham khảo:

~~~bash
javap -classpath path/to/app.jar -c -v com.example.app.Service
javap -classpath path/to/lib.jar -p -s com.example.lib.Client
~~~

Output rất dài; chỉ tìm đúng method ref và implementation được JVM resolve. Điều quan trọng là đọc **class file thực trong artifact**, không chỉ `Client.java` của dependency sources JAR có thể không khớp.

### 16.2. Vì sao “rebuild sạch” đôi khi giúp nhưng chưa đủ?

Một clean build xóa stale class output và giúp phát hiện source/bytecode lệch trong workspace. Nhưng nếu pipeline deploy vẫn trỏ bản artifact cũ, hoặc container image vẫn chứa JAR cũ, clean build máy cá nhân không thay đổi runtime. Chỉ tuyên bố đã xử lý khi:
- Artifact mới có identity rõ ràng.
- Runtime deploy thực sự nạp nó.
- Mismatch không còn trong classloading evidence.
- Test/reproduction chạy trên artifact tương đương production.

## 17. Fat JAR, executable JAR, container và application server

Trong các deployment hiện đại, “class nằm trong app.jar” chưa chắc đơn giản:
- Spring Boot executable JAR có thể đặt application classes dưới `BOOT-INF/classes` và dependencies dưới `BOOT-INF/lib`, thường được load bởi launcher riêng.
- WAR được application server giải nén hoặc nạp bằng classloader ứng dụng, đồng thời server có thể cung cấp shared libraries.
- Container image có thể chứa nhiều bản JAR, scripts khởi động chọn một bản cụ thể.
- Plugin architecture có nhiều classloader với quyền ưu tiên khác nhau.
- Một release có thể chạy nhiều replica ở các version khác nhau trong rolling update.

Khi có trace không khớp, xác định **instance cụ thể phát ra log** bằng pod/container/process/build ID. Cùng một URL dịch vụ chưa chắc mọi request vào cùng một version trong giai đoạn rollout.

### 17.1. Cần gì ngoài `jar tf`?

`jar tf app.jar` có thể cho thấy layout, nhưng chưa đủ nếu:
- JAR chứa nested JAR dưới thư mục đặc biệt.
- Bytecode bị transform khi class load.
- Launcher chọn dependency theo layer/classloader.
- Có external config hoặc plugins bổ sung class.
- Runtime nạp class từ directory ngoài JAR.

Khi đó kết hợp build manifest, classloading diagnostics, CodeSource/classloader và startup command của **process thật**. Nếu chỉ copy một JAR ra máy khác và chạy bằng một command khác launcher, bạn có thể thấy kết quả không đại diện deployment.

## 18. Quy trình điều tra thực tế theo mức độ ưu tiên

### Giai đoạn A — Bảo toàn bằng chứng

Ghi nhận:
- Full exception class, message, stack trace, cause, suppressed.
- Timestamp, instance/pod, request/operation ID và thread.
- Ứng dụng/service version, build ID, image digest.
- JDK/runtime version của chính tiến trình.
- Hành động được thực hiện, input **đã ẩn thông tin nhạy cảm**, trạng thái transaction/job.

Không chỉnh sửa log gốc; có thể đánh dấu các frame ứng dụng trong bản sao để phân tích.

### Giai đoạn B — Xác minh nguồn của class

1. Tìm frame ứng dụng gần lỗi ở **đúng cause**.
2. Xác định FQCN, module và classloader.
3. Lấy class file thực trong artifact, xác minh JAR/source origin.
4. So checksum/artifact ID với CI release record.
5. Xác định có duplicate class hoặc dependency conflict không.

Nếu ngay bước này class được nạp không thuộc release dự kiến, đây là một **artifact/deployment problem** cần xử lý trước, không phải bài toán “line 87 viết gì”.

### Giai đoạn C — Đối chiếu bytecode và source

1. `javap -c -l -p` đúng class file để lấy LineNumberTable.
2. `javap -v` khi cần constant pool, major version, flags.
3. Nếu lỗi linking, dùng `javap -p -s` và phân tích descriptor.
4. Mở source đúng commit/release và generated source phù hợp.
5. Đọc caller/input và xác định line là vị trí ném hay gọi.
6. Cân nhắc shading, instrumentation, multi-release JAR và obfuscation mapping.

### Giai đoạn D — Kiểm chứng nguyên nhân bằng thực nghiệm

1. Tái hiện trên artifact giống production.
2. So behavior với artifact/version nghi ngờ.
3. Nếu thay dependency, dùng test nhắm đúng method/ABI bị thiếu.
4. Nếu vấn đề source/line metadata, compile bản có `-g:lines,source` khi cần và xác nhận trace.
5. Nếu lỗi concurrency/async, dùng test điều phối thay vì ngẫu nhiên sleep.
6. Kiểm tra side effect/trạng thái sau exception; đối chiếu được bytecode **không** đồng nghĩa dữ liệu đã rollback.

### Giai đoạn E — Kết luận và phòng ngừa

Báo cáo rõ:
- Class/JAR/commit nào đã chạy.
- Vì sao source trong IDE không khớp.
- Evidence: artifact hash, classloading source, LineNumberTable, dependency resolution hoặc reproducer.
- Root cause và cách sửa.
- Cách ngăn tái diễn: build provenance, dependency locking, clean/reproducible builds, artifact pinning, canary checks hoặc test compatibility.

Tránh chỉ ghi “sửa line 87” nếu thực chất sản phẩm chạy sai JAR.

## 19. Bảng triệu chứng → hướng kiểm tra

| Hiện tượng | Khả năng cần xem trước | Công cụ/bằng chứng |
| --- | --- | --- |
| Trace trỏ line lớn hơn file source hiện tại | Sai source revision, generated source khác | Build ID, Git commit, packaged source |
| Line đúng nhưng statement không liên quan | Stale class/JAR, đổi bố trí source, instrumentation | `javap -c -l`, artifact hash |
| `Unknown Source` hoặc `null:-1` | Thiếu debug info, class generated/obfuscated | `javap -v`, compile options |
| Source trong IDE có method nhưng runtime báo `NoSuchMethodError` | Sai library version/descriptor | Runtime class origin, `javap -p -s` |
| `NoClassDefFoundError` chỉ xuất hiện production | Thiếu runtime dependency hoặc class init lỗi | Cause, dependency tree, class loader |
| `ClassCastException` với cùng tên class ở hai phía | Hai class loaders hoặc duplicate APIs | Class identity, loader/module evidence |
| Trace có tên `lambda$...` | Synthetic method sinh từ lambda | Source method bao lambda, line table |
| Trace có tên class bị relocate | Shading/obfuscation | Relocation config, mapping, dependency version |
| Class khác nhau khi chạy JDK 11 và 17 | Multi-release JAR/JDK/library selection | Manifest, versioned entries, `javap --multi-release` |
| Log từ replica A khớp, replica B không | Mixed deployments | Pod/image digest/build ID |
| Trace chỉ có wrapper Future | Lỗi gốc nằm trong cause của task | `getCause()`, trace từng throwable |
| Không thấy lỗi cause đầy đủ | Logging truncation/cause mất khi bọc | Logger config, giữ nguyên throwable |
| Build lại vẫn thấy trace cũ | Deploy/chạy artifact cũ, cache/launcher | Process command, artifact hash, class load log |

Bảng là **định hướng**, không phải kết luận xác suất chắc chắn. Một triệu chứng có thể có nhiều nguyên nhân; xác minh bằng đúng class/runtime là bước quyết định.

## 20. Anti-pattern thường gây điều tra sai

### 20.1. “Trace nói dòng 55, hãy sửa file source đang mở ở dòng 55”

Sai nếu source không cùng phiên bản build. Hãy xác minh artifact, LineNumberTable và commit.

### 20.2. “IDE compile được nên production có method đó”

Compile thành công chỉ chứng minh compiler nhìn thấy method trong compile classpath. JVM production có thể nạp binary khác. Với `NoSuchMethodError`, kiểm tra runtime descriptor.

### 20.3. “Dependency tree hiển thị version mới, chắc runtime dùng nó”

Có thể app server cung cấp shared JAR, shaded dependency, plugin loader hoặc fat JAR mang version khác. Kết hợp với runtime classloading evidence.

### 20.4. “`javap` không in bảng khi chạy `-l`, tức class không có line numbers”

Tùy tổ hợp options, lệnh có thể báo warning; dùng `javap -c -l` hoặc `-v` đúng class trước khi kết luận.

### 20.5. “FileName.java nghĩa là file này nằm ở đường dẫn đó trong project”

Tên file không chứa path; FQCN/module/classloader mới giúp xác định chính xác.

### 20.6. “Frame đầu luôn chỉ bug cần sửa”

Frame đầu có thể là thư viện phát hiện input xấu; frame ứng dụng gần đó có thể là call site. Exception ngoài cùng còn có thể bọc lỗi cause sâu hơn.

### 20.7. “`javap` trên library.jar đã chứng minh JVM nạp chính JAR ấy”

Không: `javap` chỉ đọc path mà bạn chỉ định. Runtime có thể dùng JAR khác với FQCN trùng. Xác minh CodeSource/load log hoặc artifact thực sự của process.

### 20.8. “Cứ bắt exception là giải quyết mismatch”

Catching `LinkageError` không thay đổi binary ABI. Catch rộng có thể che vấn đề triển khai và khiến trạng thái không an toàn. Sửa classpath/dependency và kiểm thử compatibility.

### 20.9. “Clean build đồng nghĩa đã deploy”

Không. Build output, container image, rollout và process runtime là các bước khác nhau. Cần chứng cứ cho từng bước.

### 20.10. “Không thấy source nên phải có lỗi ở JDK”

Generated code, missing line table, obfuscation, fat JAR hoặc wrong source version thường thực tế hơn. Điều tra theo bằng chứng, không đoán.

## 21. Kiểm thử tự động cho sự khớp giữa binary và source

Một pipeline có thể bổ sung các kiểm tra sau:

1. **Unit tests và integration tests** trên đúng JDK target; không chỉ compile.
2. **Dependency lock hoặc BOM rõ ràng** để giảm version drift.
3. **Duplicate class detection** khi build/deploy đóng gói nhiều JAR.
4. **Smoke test trên artifact đã đóng gói**, chạy bằng launcher/entrypoint như production.
5. **Build metadata** nhúng hoặc xuất cùng artifact để truy commit/image digest.
6. **Binary compatibility checks** khi public API của thư viện thay đổi; đặc biệt ở module chia sẻ.
7. **Line number metadata policy** với module cần chẩn đoán: không vô tình strip `SourceFile`/`LineNumberTable`.
8. **Reproducibility checks** hoặc checksums cho release artifact.
9. **Class origin diagnostics** được bật tạm thời có kiểm soát khi cần.
10. **Canary và rollout verification** để tránh nhiều replica chạy khác version mà không nhận ra.

Ví dụ test một lỗi từ `BytecodeTraceDemo`:

~~~java
IllegalArgumentException error =
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> BytecodeTraceDemo.verify(-3));

StackTraceElement top = error.getStackTrace()[0];
org.junit.jupiter.api.Assertions.assertEquals(
        "BytecodeTraceDemo", top.getClassName());
org.junit.jupiter.api.Assertions.assertEquals(
        "verify", top.getMethodName());
~~~

Đây là **fragment JUnit 5 không độc lập**: cần dependency JUnit 5 và class `BytecodeTraceDemo` từ ví dụ runnable. Test về hành vi nghiệp vụ không nên hardcode line number `4`, vì thêm comment đầu file sẽ làm test vỡ dù logic đúng. Khi chủ đích kiểm tra **debug metadata trong release artifact**, có thể kiểm tra line > 0/`LineNumberTable` tồn tại ở giai đoạn CI riêng; phải xác nhận đó là một yêu cầu kỹ thuật thực sự.

### 21.1. Kiểm tra phiên bản binary bị lệch trong test

Tạo hai biến thể của một API ở hai JAR khác nhau và chạy app đã compile với v2 trên runtime classpath chỉ chứa v1. Nếu v1 thiếu method được gọi, test có thể tái hiện `NoSuchMethodError`. Thí nghiệm kiểu này nên chạy trong một tiến trình/loader cô lập và **tuyệt đối không thay thư viện chung của workstation hoặc repo** chỉ để chứng minh. Kết luận phải dựa trên descriptor và bản JAR thực được nạp.

## 22. Checklist chẩn đoán production

- [ ] Đã có **toàn bộ** exception, cause, suppressed, thread và context phù hợp.
- [ ] Đã xác định **instance/process** phát log, không chỉ tên service.
- [ ] Đã lấy deployment version, build ID, commit và artifact/image digest.
- [ ] Đã tìm đúng frame ứng dụng trong **trace của cause liên quan**.
- [ ] Đã xác định FQCN/package/module/classloader.
- [ ] Đã kiểm tra class/JAR thật được runtime nạp, không chỉ dependency trong IDE.
- [ ] Đã xác định có duplicate classes, classpath/module-path hoặc container loader đặc biệt không.
- [ ] Đã so LineNumberTable bằng `javap -c -l -p` hoặc metadata phù hợp.
- [ ] Đã đối chiếu source đúng commit và generated source nếu cần.
- [ ] Nếu lỗi linking, đã kiểm tra descriptors/method/field tại runtime bằng `javap -p -s`.
- [ ] Đã xem shading, obfuscation mapping, multi-release JAR và instrumentation.
- [ ] Đã phân biệt thiếu debug info với source/JAR không khớp.
- [ ] Đã kiểm tra runtime JDK version khi phân tích class file version và thư viện JDK.
- [ ] Đã tái hiện bằng artifact tương đương production trước khi tuyên bố sửa xong.
- [ ] Đã thêm test và cách phòng ngừa version drift/deploy nhầm.
- [ ] Đã bảo vệ dữ liệu nhạy cảm trong log, artifact metadata và thao tác chẩn đoán.

### Bài tập thực hành

Bạn nhận một trace từ một API Java:

~~~text
java.util.concurrent.CompletionException: java.lang.NoSuchMethodError: '...'
    at ...CompletableFuture...
Caused by: java.lang.NoSuchMethodError: ...
    at com.example.order.OrderGateway.send(OrderGateway.java:96)
    ... 3 more
~~~

Bản source trong IDE cho thấy `OrderGateway.java:96` chỉ là câu lệnh ghi log, và Maven/Gradle đang hiển thị thư viện client version mới.

Hãy thực hiện theo thứ tự:

1. Xác định bản deployment đã phát log và hash của artifact chạy.
2. Tìm class `OrderGateway` từ JAR/classloader thực thay vì dùng bản IDE.
3. Dùng `javap -c -l -p` đối chiếu dòng 96 của **class file ấy** với source ở commit phù hợp.
4. Dùng `javap -c -v` trên caller tìm method reference gây `NoSuchMethodError`, sau đó dùng `javap -p -s` trên bản dependency được runtime nạp để so descriptor.
5. Kiểm tra dependency conflict, shared libraries, shaded/nested JAR, module path và classpath thực tế.
6. Nếu hệ thống có nhiều replica, so version/artifact của tất cả instance; không kết luận từ một máy đơn lẻ.
7. Viết một integration test chạy từ packaged artifact bảo đảm method ABI đúng và stack trace/LineNumberTable đủ thông tin.
8. Mô tả root cause bằng bằng chứng: chính xác JAR/class nào đã bị lệch, vì sao source mới không khớp, và hành động sửa ở pipeline/deployment.

**Điểm cốt lõi:** Khi stack trace không khớp mã nguồn, câu hỏi đầu tiên không phải “dòng nào sai?” mà là **“JVM đang thực thi chính xác bytecode nào, được nạp từ đâu và được build từ source nào?”**. Chỉ khi ba lớp source–artifact–runtime khớp nhau thì số dòng trong trace mới trở thành bằng chứng đáng tin để sửa lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
