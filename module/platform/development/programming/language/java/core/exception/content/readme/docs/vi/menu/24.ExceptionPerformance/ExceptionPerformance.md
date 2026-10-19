<a id="back-to-top"></a>

# Hiệu năng

## Menu
- [Chi phí tạo Throwable và thu thập stack trace](#exception-performance-001)
- [Phân biệt chi phí tạo, ném, bắt và ghi log ngoại lệ](#exception-performance-002)
- [Không dùng ngoại lệ cho nhánh điều khiển thường xuyên](#exception-performance-003)
- [Không tạo message tốn kém khi chưa cần phát sinh lỗi](#exception-performance-004)
- [Stack trace bị lược bỏ và tối ưu phụ thuộc JVM](#exception-performance-005)
- [Đánh đổi khi tắt writableStackTrace hoặc tái sử dụng đối tượng ngoại lệ](#exception-performance-006)
- [Rủi ro khi dùng chung ngoại lệ có thông tin nguyên nhân hoặc lỗi phụ thay đổi](#exception-performance-007)
- [Đo bằng benchmark có tình huống lỗi và tần suất thực tế](#exception-performance-008)

## <a id="exception-performance-001">Chi phí tạo Throwable và thu thập stack trace</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí tạo `Throwable` và thu thập stack trace trong Java

Ngoại lệ giúp Java báo một sự cố kèm ngữ cảnh chẩn đoán. Khi tạo `new IllegalStateException(...)`, chương trình thường không chỉ cấp phát một đối tượng và lưu chuỗi thông điệp: cơ chế khởi tạo `Throwable` còn **ghi lại thông tin về ngăn xếp lời gọi (stack trace)** của luồng hiện tại. Việc này hữu ích khi một lỗi thật sự cần được điều tra, nhưng có thể trở thành chi phí đáng kể nếu ứng dụng tạo hàng nghìn hoặc hàng triệu ngoại lệ cho những tình huống vốn là một phần hoạt động bình thường.

Cần hiểu chính xác **chi phí phát sinh ở bước nào**, **dữ liệu nào được giữ lại**, và **cách tối ưu mà không đánh đổi khả năng tìm lỗi một cách mù quáng**. Không có một con số nanô giây cố định cho việc tạo ngoại lệ trong mọi JVM: thời gian phụ thuộc vào loại ngoại lệ, độ sâu ngăn xếp, cách chương trình chạy, tối ưu hóa của JVM và hoạt động ghi log.

## 1. `Throwable` là gì và vì sao việc tạo nó có thể tốn chi phí?

`Throwable` là lớp gốc của hai nhánh chính `Exception` và `Error`. Một đối tượng lỗi có thể có:

- **Thông điệp** (`getMessage()`): mô tả ngắn, có thể là `null`.
- **Nguyên nhân** (`getCause()`): đối tượng `Throwable` dẫn tới lỗi hiện tại.
- **Stack trace** (`getStackTrace()`): các khung lời gọi liên quan tới thời điểm thông tin stack được ghi nhận.
- **Suppressed exceptions** (`getSuppressed()`): các lỗi bổ sung, thường gặp khi đóng tài nguyên bằng `try-with-resources`.

Trong trường hợp khởi tạo thông thường, constructor của `Throwable` gọi `fillInStackTrace()` để ghi nhận trạng thái của ngăn xếp luồng. JVM phải làm công việc nhất định để lấy thông tin này; công việc thường tăng khi phải duyệt nhiều khung stack. Tạo chuỗi thông điệp, nguyên nhân, các trường riêng của lớp ngoại lệ và hoạt động cấp phát bộ nhớ cũng góp vào tổng chi phí.

Vì thế, một mã như:

```java
IllegalArgumentException error =
        new IllegalArgumentException("quantity must be positive");
```

là **đoạn minh họa trong một phương thức**, và riêng biểu thức `new` có thể đã thu thập thông tin về stack. Chưa cần có lệnh `throw` hay logger mới phát sinh phần công việc ấy.

**Đừng hiểu rằng `new Throwable` luôn buộc JVM lập ngay toàn bộ mảng `StackTraceElement[]` công khai.** Việc ghi nhận nội bộ khi khởi tạo và việc chuyển dữ liệu thành các đối tượng/mảng để `getStackTrace()` trả về là hai khâu cần phân biệt. Chi tiết lưu trữ, tính lười và các tối ưu hóa là vấn đề của triển khai JVM; lập trình viên nên dựa vào hợp đồng API, không đoán cấu trúc nội bộ.

## 2. Tách riêng bốn nhóm chi phí

| Hoạt động | Ví dụ | Chi phí cần quan sát |
| --- | --- | --- |
| Tạo đối tượng ngoại lệ | `new IllegalStateException("failed")` | Cấp phát, khởi tạo trường, ghi nhận stack tại thời điểm tạo theo cấu hình thường |
| Ném và truyền lỗi | `throw error;`, tìm `catch` phù hợp | Chuyển điều khiển, tháo các khung đang hoạt động, chạy `finally`/dọn tài nguyên liên quan |
| Đọc/biến đổi thông tin stack | `error.getStackTrace()`, `fillInStackTrace()` | Lấy mảng các phần tử stack, sao chép/hiện thực hóa biểu diễn, hoặc ghi lại stack tại thời điểm mới |
| Định dạng và ghi log | `printStackTrace()`, `logger.error("...", error)` | Chuyển stack/cause/suppressed thành nội dung hiển thị; đi qua bộ lọc, formatter, hàng đợi, đầu ra hoặc hệ thống tập trung |

Các hoạt động có thể xuất hiện riêng hoặc cùng nhau. Ví dụ **tạo một ngoại lệ rồi không bao giờ ném** vẫn có thể tốn chi phí thu thập stack. Ngược lại, **ném lại chính đối tượng đã tồn tại** không mặc nhiên ghi lại toàn bộ stack mới, dù việc chuyển điều khiển và xử lý nhánh lỗi vẫn có chi phí.

`getMessage()` chỉ lấy thông điệp; nó không thay thế stack trace khi cần tìm lỗi. Nhưng gọi `getStackTrace()` hoặc định dạng nguyên stack trong mọi lần kiểm tra trạng thái cũng có thể tạo nhiều phân bổ và công việc thừa. Nên thu thập/hiển thị chi tiết vào nơi thật sự phục vụ chẩn đoán.

## 3. Stack trace thường được ghi nhận khi tạo, không phải khi bắt

Một hiểu lầm phổ biến là `catch` hoặc `throw` sẽ tự chụp lại stack mới. Với đối tượng ngoại lệ được tạo theo cách thông thường, stack trace được ghi nhận khi khởi tạo, hoặc khi mã chủ động gọi `fillInStackTrace()` để cập nhật nó.

Ví dụ sau có thể chạy bằng Java 17 trở lên.

Tạo tệp **`ThrowableCaptureDemo.java`**:

```java
public class ThrowableCaptureDemo {
    static IllegalArgumentException createFailure() {
        return new IllegalArgumentException("invalid input");
    }

    static void throwExisting(IllegalArgumentException error) {
        throw error;
    }

    public static void main(String[] args) {
        IllegalArgumentException failure = createFailure();
        System.out.println("createdAt="
                + failure.getStackTrace()[0].getMethodName());

        try {
            throwExisting(failure);
        } catch (IllegalArgumentException caught) {
            System.out.println("sameObject=" + (caught == failure));
            System.out.println("topAfterThrow="
                    + caught.getStackTrace()[0].getMethodName());
        }

        StackTraceElement[] first = failure.getStackTrace();
        StackTraceElement[] second = failure.getStackTrace();
        System.out.println("returnedArraysDistinct=" + (first != second));

        failure.fillInStackTrace();
        System.out.println("topAfterRefresh="
                + failure.getStackTrace()[0].getMethodName());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ThrowableCaptureDemo.java
java ThrowableCaptureDemo
```

Đầu ra:

```text
createdAt=createFailure
sameObject=true
topAfterThrow=createFailure
returnedArraysDistinct=true
topAfterRefresh=main
```

Giải thích:

1. `createFailure()` là nơi đối tượng được tạo, nên phần tử stack đầu tiên mô tả phương thức đó.
2. `throwExisting()` ném lại **chính đối tượng** đã có; `sameObject=true`. Thông tin stack đầu tiên vẫn là `createFailure`, vì Java không tự điền stack mới mỗi khi cùng đối tượng được ném lại.
3. Hai lời gọi `getStackTrace()` trả về những mảng riêng để mã nhận có thể làm việc với mảng mà không trực tiếp thay đổi phần lưu trữ stack nội bộ của ngoại lệ. **Việc gọi nhiều lần không miễn phí** trong triển khai thông thường.
4. `fillInStackTrace()` được gọi từ `main`, nên vị trí đứng đầu của stack sau đó là `main`: phương thức này **cập nhật** thông tin stack gắn với đối tượng. Đây là hành vi chủ động, không phải điều xảy ra khi ném lại mặc định.

Chương trình này kiểm tra **ngữ nghĩa**, không đo thời gian chạy. Việc gọi `getStackTrace()[0]` giả định một ngoại lệ có stack được phép ghi và có khung gọi như ví dụ trên. Nếu stack bị vô hiệu hóa, bị rút gọn hoặc ngoại lệ đặc biệt không có các khung, mảng có thể rỗng; không nên truy cập `[0]` trong mã xử lý lỗi tổng quát nếu chưa kiểm tra độ dài.

## 4. Độ sâu ngăn xếp và số lượng ngoại lệ ảnh hưởng thế nào?

Hãy tưởng tượng hai tuyến gọi:

```text
main -> parse                -> new IllegalArgumentException(...)
main -> service -> workflow -> repository -> parser -> new IllegalArgumentException(...)
```

Ở trường hợp thứ hai, đường đi có nhiều khung hơn. Việc tạo stack trace có thể cần duyệt/ghi nhận nhiều thông tin hơn. Tuy vậy, kết quả đo không nhất thiết tăng tuyến tính hoàn hảo theo số khung, bởi JVM có thể tối ưu, giới hạn độ sâu hoặc dùng biểu diễn nội bộ khác nhau.

Trong một luồng xử lý bình thường, vài ngoại lệ thực sự xảy ra thường không phải vấn đề. Rủi ro xuất hiện khi hệ thống biến **luồng điều khiển thông thường** thành việc liên tục tạo ngoại lệ:

```java
try {
    return Integer.parseInt(raw);
} catch (NumberFormatException error) {
    return DEFAULT_VALUE;
}
```

Đây là đoạn minh họa trong một phương thức có biến `raw` và hằng `DEFAULT_VALUE`. Nếu đầu vào không hợp lệ là tình huống phổ biến trong một đường xử lý tần suất cao, nhiều `NumberFormatException` sẽ được tạo. Cần cân nhắc một API kiểm tra/parse trả kết quả có cấu trúc, nếu hợp đồng nghiệp vụ coi thất bại parse là nhánh thông thường. Tuy nhiên, **đừng tự xây parser mới chỉ để tránh exception** khi điều đó làm mất tính đúng đắn hoặc khiến mã khó bảo trì; hãy đo tải thật trước.

Một số ngoại lệ như lỗi truy cập mạng, ghi dữ liệu hay vi phạm bất biến là tình huống bất thường đúng nghĩa. Với chúng, giữ stack trace thường có giá trị chẩn đoán lớn hơn khoản tiết kiệm nhỏ khi bỏ nó.

## 5. Gọi `getStackTrace()` và `printStackTrace()` thêm chi phí gì?

`Throwable.getStackTrace()` trả một **mảng `StackTraceElement[]`** chứa các vị trí lời gọi. Mã dùng API có thể đọc, lọc, sắp xếp thông tin này. Việc tạo/copy mảng và hiện thực hóa các phần tử stack trong triển khai JVM có thể tạo cấp phát bổ sung; lập trình viên không nên giả định rằng đọc stack bằng API này là thao tác đơn giản như đọc một trường `boolean`.

Ví dụ:

```java
for (StackTraceElement frame : error.getStackTrace()) {
    if (frame.getClassName().startsWith("com.example.")) {
        System.out.println(frame);
    }
}
```

Đây là đoạn minh họa bên trong phương thức đã có `Throwable error`, không phải chương trình hoàn chỉnh. Nếu đặt đoạn mã vào một vòng xử lý lỗi lặp lại, việc liên tục biến toàn bộ stack thành mảng để chọn một vài khung có thể phát sinh chi phí không cần thiết.

`printStackTrace()` còn phải dựng bản trình bày dễ đọc gồm tên ngoại lệ, thông điệp, các khung stack và thông tin `Caused by:`/`Suppressed:`. Việc in ra console, file, socket hoặc bộ thu thập log có thể tốn thời gian và gây tranh chấp tài nguyên hơn cả thao tác tạo ngoại lệ — tùy nền tảng và cấu hình.

Nếu chỉ cần vị trí gọi hiện tại và **không muốn tạo một ngoại lệ chỉ để lấy stack**, Java 9+ cung cấp `StackWalker`:

```java
String caller = StackWalker.getInstance().walk(stream ->
        stream.findFirst()
                .map(frame -> frame.getClassName()
                        + "#" + frame.getMethodName())
                .orElse("unknown"));
```

Đoạn này minh họa bên trong một phương thức. `StackWalker` cho phép duyệt khung stack của luồng hiện tại theo nhu cầu, nhưng bản thân thao tác đi qua stack **vẫn có chi phí** và không thay thế `Throwable` khi cần lưu nguyên nhân của lỗi đã xảy ra. Khi cần stack của **ngoại lệ được tạo trước đó**, không thể chỉ đi qua stack hiện tại để tái dựng chính xác ngăn xếp của thời điểm lỗi.

## 6. Giảm chi phí bằng ngoại lệ tùy biến không ghi stack

Java có một constructor `protected` của `Throwable` và những lớp con như `RuntimeException`:

```java
RuntimeException(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace);
```

Chữ ký trên là **mô tả API**, không phải lời gọi constructor hợp lệ từ mã bên ngoài `RuntimeException`. Một lớp ngoại lệ tùy biến có thể gọi `super(message, cause, enableSuppression, writableStackTrace)` để quyết định có ghi nhận stack và có cho phép suppressed exceptions hay không.

Tạo tệp **`StackTraceOptionsDemo.java`**:

```java
public class StackTraceOptionsDemo {
    static final class NormalFailure extends RuntimeException {
        NormalFailure(String message) {
            super(message);
        }
    }

    static final class LightweightSignal extends RuntimeException {
        LightweightSignal(String message) {
            super(message, null, false, false);
        }
    }

    public static void main(String[] args) {
        RuntimeException normal = new NormalFailure("expected");
        RuntimeException lightweight = new LightweightSignal("expected");

        normal.addSuppressed(new IllegalStateException("cleanup"));
        lightweight.addSuppressed(new IllegalStateException("cleanup"));

        System.out.println("normalHasStack="
                + (normal.getStackTrace().length > 0));
        System.out.println("lightweightStackLength="
                + lightweight.getStackTrace().length);
        System.out.println("normalSuppressed="
                + normal.getSuppressed().length);
        System.out.println("lightweightSuppressed="
                + lightweight.getSuppressed().length);

        lightweight.fillInStackTrace();
        System.out.println("lightweightAfterFill="
                + lightweight.getStackTrace().length);
        System.out.println("lightweightMessage="
                + lightweight.getMessage());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 StackTraceOptionsDemo.java
java StackTraceOptionsDemo
```

Đầu ra:

```text
normalHasStack=true
lightweightStackLength=0
normalSuppressed=1
lightweightSuppressed=0
lightweightAfterFill=0
lightweightMessage=expected
```

Lớp `LightweightSignal` đã tắt **cả** khả năng ghi stack và suppression bằng hai đối số `false`. Vì vậy, `getStackTrace()` trả mảng rỗng, `fillInStackTrace()` không ghi stack, và `addSuppressed(...)` không lưu ngoại lệ kèm theo. Thông điệp `"expected"` vẫn có.

Hai cờ này **độc lập về ý nghĩa**: nếu chỉ muốn tắt stack trace nhưng vẫn giữ suppressed exceptions, constructor có thể dùng `super(message, cause, true, false)`. Ví dụ trên cố tình tắt cả hai để cho thấy quyết định giảm dữ liệu chẩn đoán cần được cân nhắc rõ.

**Đánh đổi quan trọng:** một đối tượng không có stack trace sẽ khó điều tra nơi được tạo. Đừng dùng `writableStackTrace=false` rộng khắp cho ngoại lệ bất ngờ, lỗi lập trình, hỏng dữ liệu hoặc lỗi cần phân tích sau sự cố. Chỉ cân nhắc cho một **tín hiệu được định nghĩa rõ, xảy ra thường xuyên và có thể chẩn đoán bằng thông tin khác**. Khi dùng lớp này, phải ghi nhận lý do và bảo đảm người bảo trì không nhầm nó với lỗi nghiêm trọng.

## 7. Có nên ghi đè `fillInStackTrace()` để trả về `this`?

Một kỹ thuật từng được dùng là ghi đè:

```java
@Override
public synchronized Throwable fillInStackTrace() {
    return this;
}
```

Đây là đoạn **minh họa một phương thức override trong lớp con của `Throwable`**, không phải chương trình độc lập. Trong các trường hợp phù hợp, nó ngăn bước ghi stack do `fillInStackTrace()` thực hiện. Tuy nhiên, kết quả phụ thuộc vào cách lớp được khởi tạo, hợp đồng kế thừa và nhu cầu chẩn đoán. Java đã có constructor cho phép tắt `writableStackTrace`; ưu tiên cách diễn đạt trực tiếp bằng constructor khi lớp ngoại lệ của bạn hỗ trợ.

Đặc biệt, không ghi đè chỉ để “tăng tốc” mọi ngoại lệ trước khi chứng minh đây là đường nóng. Việc thiếu vị trí lỗi có thể làm thời gian điều tra sự cố tăng mạnh. Cũng không nên dùng một **singleton `Throwable`** rồi ném lại từ nhiều luồng chỉ để tiết kiệm cấp phát: stack gắn với nơi tạo ban đầu dễ gây hiểu lầm, một số trạng thái ngoại lệ có thể thay đổi, và đối tượng dùng chung làm mất ngữ cảnh riêng của từng lần xảy ra lỗi.

## 8. `throw` không mặc nhiên chụp lại stack, nhưng xử lý ngoại lệ vẫn tốn công

Một câu lệnh `throw` của đối tượng đã tồn tại thường không thực hiện hành động giống `new Throwable()` lần nữa. Nó chuyển luồng điều khiển đến bộ xử lý thích hợp, có thể phải rời nhiều khung, chạy `finally`, giải phóng tài nguyên trong `try-with-resources` và thực thi những nhánh xử lý lỗi.

Vì vậy hãy tránh hai kết luận quá mức:

- “Nếu chỉ `throw` lại thì hoàn toàn miễn phí.” **Sai**: vẫn có chi phí chuyển điều khiển, chạy code xử lý và các tác dụng phụ liên quan.
- “Mọi `try/catch` đều chậm ngay cả khi không có lỗi.” **Không nên khẳng định như vậy**. Chi phí chính thường nằm ở đường ném và xử lý ngoại lệ, không phải sự tồn tại của cú pháp `try` quanh một luồng thành công. JVM có thể tối ưu các đường bình thường rất tốt; hãy đo thay vì loại bỏ cấu trúc xử lý lỗi cần thiết.

Hành vi còn phụ thuộc vào loại lỗi: có các ngoại lệ do JVM tạo tự động (chẳng hạn một số lỗi thao tác bytecode) và các đối tượng ngoại lệ do mã ứng dụng `new`. HotSpot có những tối ưu hóa đặc biệt cho một số lỗi JVM phát sinh lặp lại tại cùng vị trí, trong vài điều kiện có thể bỏ thông tin stack. Đây là **chi tiết triển khai, không phải cam kết chung của ngôn ngữ Java**; không nên dựa vào nó để thiết kế logging hoặc dự đoán stack trace sẽ luôn có/không có. Nếu cần kiểm tra, phải xem đúng phiên bản JVM và cấu hình hiện hành.

## 9. Đo hiệu năng đúng cách thay vì gán một con số cố định

Một microbenchmark kém thiết kế rất dễ dẫn đến nhận định sai rằng `Throwable` “tốn đúng X ns” hay “tắt stack luôn nhanh hơn Y lần”. Phép đo chịu ảnh hưởng bởi:

- **Warm-up/JIT**: các lời gọi đầu tiên khác những lần sau khi mã đã được biên dịch tối ưu.
- **Dead-code elimination / escape analysis**: kết quả không được sử dụng có thể bị tối ưu hóa, khiến bạn tưởng đang đo việc tạo đối tượng nhưng thật ra không đo điều đó.
- **Độ sâu stack**: tạo ngoại lệ ở độ sâu 2, 20, 100 có thể cho kết quả khác nhau.
- **Lớp ngoại lệ**: constructor và trường dữ liệu riêng có thể thay đổi khối lượng công việc.
- **Có gọi `getStackTrace()` không**: chỉ tạo `Throwable` khác việc lấy mảng stack.
- **Có định dạng/log ra thiết bị thật không**: chi phí formatter, đồng bộ, buffer, I/O, chính sách drop/sampling có thể lấn át thao tác còn lại.
- **Môi trường JVM**: bản JDK, GC, CPU, cờ VM, công việc khác trên máy và profile tải.

Với nghiên cứu nghiêm túc, dùng **JMH (Java Microbenchmark Harness)** hoặc công cụ profiler có phương pháp rõ ràng, báo cả các phân vị/độ phân tán khi phù hợp và lượng cấp phát (ví dụ byte/op) nếu công cụ hỗ trợ. Không dùng một vòng lặp `System.nanoTime()` quá ngắn rồi kết luận kết quả mang tính phổ quát.

Có thể chia thí nghiệm thành các nhánh độc lập:

| Phép thử | Mục tiêu | Điều cần kiểm soát |
| --- | --- | --- |
| `new NormalFailure(...)` | Chi phí tạo ngoại lệ có stack | Giữ cùng độ sâu gọi, tiêu thụ đối tượng |
| `new LightweightSignal(...)` | Chi phí tạo ngoại lệ không có stack | Cùng loại công việc và dữ liệu, nêu rõ đã hy sinh stack |
| `failure.getStackTrace()` | Chi phí đọc mảng stack | Tách khỏi thời gian tạo đối tượng |
| `failure.printStackTrace(writer)` | Chi phí tạo biểu diễn văn bản | Dùng writer phù hợp, loại trừ/báo riêng I/O |
| Ghi logger có Throwable | Chi phí toàn tuyến logging | Cấu hình backend, mức log, sink và sampling rõ ràng |

Nếu cần dùng JMH, benchmark tối thiểu thường tổ chức như sau (đây là **phác thảo**, cần thêm JMH dependency/import, state và cấu hình, không phải lớp chạy độc lập):

```java
@Benchmark
public void constructThrowable(org.openjdk.jmh.infra.Blackhole sink) {
    sink.consume(new IllegalStateException("expected"));
}

@Benchmark
public void materializeStack(org.openjdk.jmh.infra.Blackhole sink) {
    sink.consume(existingThrowable.getStackTrace());
}
```

Ở nhánh thứ hai, `existingThrowable` phải là state được tạo ở ngoài phép đo để đo việc đọc stack thay vì vô tình đo cả khởi tạo. Cần thiết kế state hợp lý với số luồng và tránh chia sẻ trạng thái mutable không có kiểm soát. JMH là thư viện ngoài JDK, không tự có chỉ vì dùng Java 17.

Trong ứng dụng thật, kết hợp microbenchmark với profiler và metrics: tỷ lệ exception trên mỗi request, chi phí CPU của `Throwable.fillInStackTrace`, tổng byte cấp phát, tần suất GC, thời gian xử lý request và lượng log. Một tối ưu làm microbenchmark nhanh hơn chưa chắc cải thiện độ trễ hệ thống nếu chi phí thực nằm ở I/O hoặc khóa.

## 10. Kỹ thuật giảm overhead mà vẫn giữ chẩn đoán

**(1) Đừng dùng exception để thay thế nhánh kết quả dự kiến.** Ví dụ kiểm tra điều kiện nghiệp vụ bình thường và trả kết quả có cấu trúc (`Optional`, enum trạng thái, kiểu kết quả riêng) thay vì ném ngoại lệ cho mỗi lần tra cứu không tìm thấy, nếu quy ước API coi “không tìm thấy” là kết quả thường. Với sai lệch bất ngờ, exception vẫn là cơ chế phù hợp.

**(2) Không tạo exception chỉ để lấy tên caller khi đã có API thích hợp.** Nếu mục tiêu là stack hiện tại hoặc một caller cụ thể, xem `StackWalker` hoặc cơ chế metadata/trace của framework. Việc đi qua stack vẫn có giá; đo và chỉ làm ở nơi cần thiết.

**(3) Giữ đối tượng `Throwable` khi log lỗi quan trọng.** Với SLF4J, một lời gọi minh họa trong ứng dụng có logger là:

```java
logger.error("Failed to process order {}", orderId, exception);
```

Đoạn này yêu cầu SLF4J, `logger`, `orderId`, `exception`. Nó mang cả exception cho backend để có thể ghi stack/cause; gọi `exception.getMessage()` rồi bỏ `exception` sẽ mất dữ liệu chẩn đoán. Nếu log volume cao, kiểm soát mức log, rate limit, sampling hoặc gom nhóm tại ranh giới xử lý. Đừng tắt stack trace của lỗi nghiêm trọng chỉ để giảm số byte log.

**(4) Chọn đúng điểm log.** Một lỗi qua repository → service → API có thể bị log ba lần; thường nên log một lần ở nơi có đầy đủ ngữ cảnh và trách nhiệm xử lý, rồi truyền trạng thái lỗi qua các tầng khác. Nếu một tầng xử lý được lỗi và trả fallback, log ở đó có thể hữu ích hơn.

**(5) Đừng bắt rồi ném lại ngoại lệ mới mà mất cause.** Nếu cần bọc lỗi, dùng constructor lưu nguyên nhân, ví dụ `new IllegalStateException("Could not save order", cause)`. Nếu mỗi tầng lại `new ...` một cách vô ích, chương trình vừa thêm chi phí tạo stack mới, vừa làm chuỗi nguyên nhân khó đọc.

**(6) Hạn chế truy xuất stack nhiều lần.** Nếu cần đọc stack để phân loại một lỗi cụ thể, đọc một lần vào biến rồi xử lý khi cần; tránh gọi `getStackTrace()` nhiều lần trong vòng lặp tìm một phần tử. Đừng lấy stack đầy đủ chỉ để quyết định mức log nếu có tín hiệu nghiệp vụ chính xác hơn.

**(7) Chỉ chọn ngoại lệ không có stack sau khi đánh giá ngữ nghĩa.** Một `LightweightSignal` có thể hợp lý cho cơ chế hủy/điều khiển nội bộ có hợp đồng riêng. Nhưng nếu caller bên ngoài có thể coi nó là lỗi cần điều tra, mất stack có thể gây tổn thất lớn.

**(8) Theo dõi việc ghi log bất đồng bộ.** Backend logging có thể hoãn định dạng hoặc đẩy sự kiện vào hàng đợi, giảm thời gian chặn luồng gọi trong một số cấu hình; việc đó không xóa chi phí tạo ngoại lệ, không loại bỏ tổng công việc formatter/I/O và có thể tạo áp lực bộ nhớ hoặc mất sự kiện theo chính sách hàng đợi. Phải đo toàn hệ thống.

## 11. Chi phí và tính đầy đủ của stack trace trong thực tế

Có những trường hợp không nên tin rằng mọi `Throwable` đều có stack trace chi tiết:

1. **Tắt writable stack trace**: constructor bốn tham số với `writableStackTrace=false` cho phép không điền stack; `getStackTrace()` trả mảng rỗng.
2. **Code chủ động thay đổi stack**: `fillInStackTrace()` ghi lại tại vị trí hiện tại, `setStackTrace(...)` có thể thay đổi cách stack được trả về cho đối tượng có stack ghi được. Không nên sửa stack để làm đẹp log nếu không có lý do kỹ thuật rõ ràng.
3. **Giới hạn/đặc điểm JVM**: stack có thể bị giới hạn, rút gọn hoặc vắng mặt trong các trường hợp đặc biệt do VM; một số tối ưu cho ngoại lệ do JVM tạo không nhất thiết áp dụng với ngoại lệ người dùng `new`.
4. **Lỗi nghiêm trọng**: khi JVM thiếu bộ nhớ hoặc stack quá sâu, hệ thống có thể không còn tài nguyên để tạo và in log như bình thường. Không thể coi stack trace đầy đủ là bảo đảm tuyệt đối trong mọi tình huống.
5. **Ranh giới tác vụ bất đồng bộ**: stack của exception ở worker thể hiện ngữ cảnh worker; khi `CompletableFuture.join()` hoặc `Future.get()` báo lỗi cho thread khác, lớp bọc và cause có thể mang những stack khác nhau. Cần giữ cause và mã tương quan để nối các ngữ cảnh.

Khi phân tích performance, số khung trong một trace không phải luôn bằng toàn bộ lịch sử nghiệp vụ của request. Stack mô tả trạng thái của **một luồng ở thời điểm ghi stack**, chứ không tự ghép được đường đi của một request qua nhiều thread hay message queue. Distributed tracing và correlation ID bổ sung thông tin đó, nhưng chúng cũng có chi phí riêng.

## 12. Sai lầm phổ biến

| Sai lầm | Vì sao chưa đúng | Cách hiểu/thực hành tốt |
| --- | --- | --- |
| “`throw` mới là lúc Java tạo stack trace.” | Khởi tạo `Throwable` thường đã ghi stack | Phân biệt tạo ngoại lệ với ném một đối tượng có sẵn |
| “`catch` luôn thêm nơi bắt vào stack.” | Bắt lỗi không tự điền lại stack | Dùng cause và trace sẵn có, chỉ gọi `fillInStackTrace()` nếu chủ đích muốn cập nhật |
| “`getMessage()` là đủ, rẻ hơn nên luôn chỉ log nó.” | Mất loại lỗi, stack, cause và suppressed | Với lỗi cần chẩn đoán, chuyển cả `Throwable` cho logger |
| “`new Exception()` và `new Object()` có cùng chi phí.” | Exception thường có bước ghi nhận stack và dữ liệu phụ | Đo chi phí tạo `Throwable` riêng |
| “Gọi `getStackTrace()` không cấp phát gì thêm.” | API trả mảng và có thể cần chuyển biểu diễn nội bộ | Tránh lặp lại vô ích; đo JVM đang dùng |
| “Tắt stack trace cho mọi lỗi là tối ưu chắc chắn.” | Mất vị trí phát sinh lỗi và giảm giá trị chẩn đoán | Chỉ cân nhắc cho tín hiệu dự kiến có hợp đồng riêng |
| “`Throwable` không stack thì không thể có message/cause.” | Cờ writable stack và các dữ liệu khác có ý nghĩa khác nhau | Kiểm tra constructor và dữ liệu được giữ |
| “Một microbenchmark ngắn là đủ định lượng chi phí production.” | JIT, GC, độ sâu stack, tải I/O, logger đều có thể làm kết quả khác | Kết hợp JMH, profiler và số liệu production |
| “Tái sử dụng một ngoại lệ singleton luôn an toàn.” | Ngữ cảnh mỗi lần ném khác nhau và đối tượng có trạng thái | Tránh chia sẻ lỗi mutable khi cần truy vết từng lần |
| “Thao tác log chỉ tốn tiền nếu mức ERROR bật.” | Đối tượng Throwable có thể đã được tạo từ trước dù log bị lọc | Tránh tạo ngoại lệ chỉ để phục vụ thông điệp log không cần thiết |

## 13. Tình huống tự kiểm tra

**Tình huống A.** Một hàm tạo `IllegalStateException` tại phương thức `create()`, rồi hàm khác ném lại đối tượng đó. Khung đầu của stack mặc định thường chỉ nơi nào?  
**Trả lời:** Nơi ngoại lệ được tạo/được điền stack gần nhất (`create()`), không nhất thiết là lời gọi `throw` sau này.

**Tình huống B.** Bạn gọi `error.fillInStackTrace()` ở một điểm mới. Điều gì thay đổi?  
**Trả lời:** Với throwable có thể ghi stack, thông tin stack được cập nhật theo trạng thái luồng tại điểm gọi. Làm vậy có thể làm mất dấu vết vị trí tạo gốc nếu không giữ ngữ cảnh trước đó.

**Tình huống C.** Dùng `super(message, null, true, false)` trong constructor ngoại lệ tùy biến. `getStackTrace()` và suppression ra sao?  
**Trả lời:** Stack bị vô hiệu hóa; `getStackTrace()` rỗng. Suppression vẫn được cho phép bởi tham số `true`, nên có thể thêm suppressed exceptions.

**Tình huống D.** Một endpoint trả nhiều lỗi nhập liệu dự kiến mỗi giây. Có nên thay tất cả bằng ngoại lệ không có stack?  
**Trả lời:** Cần đánh giá trước xem lỗi nhập liệu là kết quả thông thường của API và có thể trả kiểu kết quả/trạng thái phù hợp hay không. Nếu phải giữ exception, chỉ tắt stack sau khi đo và chắc chắn không cần thông tin đó cho chẩn đoán.

**Tình huống E.** Mỗi lần lỗi, chương trình gọi `getStackTrace()` trong một vòng lặp để tìm phương thức đầu tiên của ứng dụng. Điều gì đáng xem lại?  
**Trả lời:** Cách đọc nhiều lần có thể phát sinh công việc/cấp phát lặp lại. Có thể lấy mảng một lần rồi duyệt, hoặc dùng API khác phù hợp với mục tiêu.

**Tình huống F.** `logger.error("Bad: {}", new RuntimeException("x"))` nằm trong đường code chỉ nhằm ghi debug cho mỗi request. Dù logger tắt mức này, việc tạo `RuntimeException` có miễn phí không?  
**Trả lời:** Không thể coi là miễn phí: biểu thức tạo exception được đánh giá trước khi gọi logger, trừ khi toàn bộ nhánh được tránh hoặc có tối ưu hóa cụ thể. Không nên tạo throwable chỉ để trang trí thông điệp log thường xuyên.

**Tình huống G.** Tối ưu trong JMH cho thấy bỏ stack giảm phân bổ, nhưng khi production lỗi thì khó điều tra hơn. Quyết định nào đúng?  
**Trả lời:** So sánh lợi ích thực trên workload với mức mất thông tin chẩn đoán. Giữ trace của lỗi bất ngờ; tối ưu bằng thay đổi luồng điều khiển, giới hạn log hoặc chỉ dùng ngoại lệ nhẹ cho tín hiệu thực sự có thể chẩn đoán mà không cần stack.

## 14. Tài liệu tham khảo

- [Java 17 `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `fillInStackTrace`, `getStackTrace`, `printStackTrace`, `getSuppressed` và constructor với `writableStackTrace`.
- [Java 17 `RuntimeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): constructor `protected` dùng trong lớp ngoại lệ tùy biến.
- [Java 17 `StackWalker`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackWalker.html): đọc các khung ngăn xếp của luồng hiện tại theo nhu cầu.
- [JMH — Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/): bộ công cụ đo microbenchmark JVM với warm-up và khả năng kiểm soát tối ưu hóa.

Điều cần nhớ: **tạo `Throwable` thường tốn thêm công việc ghi stack; `getStackTrace()` và xuất log có thể cộng thêm chi phí; ném lại cùng đối tượng không tự chụp stack mới.** Hãy giữ dấu vết cho lỗi cần điều tra, đo theo môi trường thực và chỉ giảm hoặc bỏ stack trace trong những trường hợp có hợp đồng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-002">Phân biệt chi phí tạo, ném, bắt và ghi log ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chi phí tạo, ném, bắt và ghi log ngoại lệ trong Java

Khi phân tích hiệu năng xử lý exception của Java, câu “`try/catch` rất chậm” thường quá đơn giản và dễ dẫn đến tối ưu hóa sai chỗ. Một đường lỗi thực tế gồm nhiều hoạt động khác nhau: **tạo đối tượng exception**, **thu thập thông tin stack trace**, **ném và chuyển luồng điều khiển**, **tìm và thực thi `catch`**, **chạy cleanup**, sau đó có thể **định dạng và ghi log**. Các hoạt động ấy có chi phí khác nhau, xảy ra ở những thời điểm khác nhau và không phải lúc nào cũng xuất hiện cùng nhau.

Ví dụ, một khối `try` chạy thành công không đồng nghĩa JVM đã tạo exception. Một đối tượng `new IllegalArgumentException(...)` có thể đã tốn chi phí chụp stack trace **ngay lúc tạo**, dù chưa từng bị `throw`. Trái lại, một câu `catch` không được thực thi không có chi phí xử lý lỗi tương đương với ném exception. Trong production, việc in stack trace vào log ở tần suất rất cao có thể chiếm đáng kể CPU, cấp phát, I/O và dung lượng lưu trữ hơn cả phần catch.

Bài này giải thích từng giai đoạn, cách đo cho đúng, những sai lầm về log và các quyết định thiết kế để chỉ trả chi phí của exception khi thật sự cần thiết. Ví dụ sử dụng **Java 17+**.

## 1. Một exception có ít nhất bốn giai đoạn chi phí

Xét đường xử lý sau:

```java
try {
    validate(request);
} catch (InvalidRequestException ex) {
    logger.log(Level.WARNING, "Request không hợp lệ", ex);
}
```

Đây là **đoạn minh họa**, với `validate`, `request`, `InvalidRequestException` và `logger` do ứng dụng định nghĩa. Nếu `validate` chạy bình thường, không có handler nào cần xử lý exception. Nếu nó ném, có thể xảy ra các bước:

| Giai đoạn | Khi nào phát sinh | Công việc có thể tạo chi phí |
| --- | --- | --- |
| **Tạo exception** | Khi gọi constructor/factory của `Throwable` | Cấp phát object; tạo message/cause; mặc định ghi nhận backtrace tại thời điểm xây dựng. |
| **Ném exception** | Khi thực thi `throw` | Thay đổi luồng điều khiển; tìm handler phù hợp; unwinding các frame cần thiết; đi qua `finally` và đóng resource nếu có. |
| **Bắt và xử lý** | Khi đến một `catch` phù hợp | Chạy logic phân loại, đổi nghĩa, tính fallback, thống kê hoặc xử lý nghiệp vụ trong handler. |
| **Ghi log** | Khi code gọi logger/`printStackTrace` hoặc hệ thống quan sát khác | Định dạng thông điệp và stack trace, xử lý cause/suppressed, cấp phát chuỗi, đồng bộ/buffer, ghi console/file/network, tác động lên hàng đợi log. |

Ngoài bốn bước chính còn có chi phí gián tiếp: garbage collection, thay đổi tối ưu hóa JIT, áp lực cấp phát và tác dụng của tầng quan sát (tracing, metrics, log exporter). Các mức chi phí phụ thuộc JVM, loại exception, độ sâu stack, tần suất lỗi và cấu hình logging; **không có một con số nanosecond cố định đúng cho mọi ứng dụng**.

Một cách nhớ:

```text
Luồng thành công:
  kiểm tra → xử lý bình thường → trả kết quả
                   (không cần tạo/throw/catch/log exception)

Luồng thất bại:
  phát hiện lỗi
       ↓
  tạo Throwable (+ stack capture mặc định)
       ↓
  throw / unwind / finally
       ↓
  catch và chính sách phục hồi/báo lỗi
       ↓
  [có thể] format và ghi log
```

Sơ đồ mang tính khái niệm. Có thể **tạo mà không ném**, **ném object đã có**, **catch rồi rethrow**, hoặc **log object chưa từng được ném**. Việc tách giai đoạn giúp đo lường đúng.

## 2. Chi phí tạo exception: constructor và backtrace

Hầu hết exception Java kế thừa `Throwable`. Khi xây dựng một `Throwable` thông thường, constructor của `Throwable` gọi cơ chế thu thập trạng thái stack hiện tại thông qua `fillInStackTrace()`, trừ những cấu hình/implementation đặc biệt. Đó là lý do **tạo exception có thể đắt hơn tạo một object dữ liệu đơn giản**, ngay cả khi chưa ném.

```java
IllegalArgumentException error =
        new IllegalArgumentException("quantity must be positive");

// Chưa có câu lệnh throw, nhưng error đã được khởi tạo.
```

Đoạn trên là snippet hợp lệ trong phương thức Java. Trong JVM thông thường, thông tin vị trí tạo exception đã được ghi nhận; `getStackTrace()` có thể chuyển dữ liệu đó thành mảng `StackTraceElement` để đọc. Việc **thu thập backtrace** và việc **materialize/định dạng stack trace để hiển thị** là các công việc khác nhau: không nên coi `new Exception` và `printStackTrace` có chi phí giống nhau.

Các thành phần quan trọng của construction:

- **Allocation:** cấp phát object; chi phí thay đổi theo cơ chế cấp phát/JIT/GC.
- **Message:** chuỗi có thể có sẵn hoặc được tạo từ phép nối chuỗi, `String.format`, serialize JSON hoặc tính toán khác. Phần tính message trước lời gọi constructor cũng có thể đắt.
- **Cause:** liên kết tới `Throwable` gốc. Giữ cause giúp chẩn đoán, nhưng cây nguyên nhân có thể khiến log stack trace dài hơn.
- **Stack capture:** nhìn nhận ngữ cảnh thực thi khi tạo exception. Số frame và đặc điểm JIT/runtime ảnh hưởng chi phí.
- **Suppressed exceptions:** cấu trúc liên quan khi có nhiều lỗi, đặc biệt với `try-with-resources`. Chỉ phát sinh hoạt động đáng kể khi thật sự cần quản lý các lỗi phụ.

Không phải mọi runtime đều xây dựng biểu diễn stack trace theo cùng một cách; một số tối ưu hóa JVM áp dụng cho các loại lỗi hoặc vị trí ném đặc biệt. **Điều bảo đảm ở mức thiết kế** là không nên dựa vào việc JVM luôn tối ưu bỏ qua giá chụp stack của mọi exception được tạo.

### Tạo message tốn kém dù không ném

```java
IllegalStateException error =
        new IllegalStateException(
                "Invalid payload: " + expensiveSerialization(payload));
```

Đây là snippet minh họa, `payload` và `expensiveSerialization` do ứng dụng định nghĩa. Hàm serialize có thể chạy **trước khi** constructor exception nhận chuỗi. Nếu đoạn này được gọi thường xuyên mà hiếm khi ném, bạn có thể đã đưa chi phí message vào *hot path*.

Tốt hơn, chỉ tạo exception sau khi điều kiện lỗi thực sự được xác nhận:

```java
if (!isValid(payload)) {
    throw new IllegalStateException("Invalid payload");
}
```

Ở đây `isValid` và `payload` là ký hiệu minh họa. Kiểm tra tính hợp lệ vẫn có chi phí và cần thiết về nghiệp vụ, nhưng exception chỉ được tạo ở nhánh lỗi. Nếu cần chẩn đoán sâu, ghi thêm dữ liệu theo chính sách bảo mật và mức log thay vì serialize cả payload vào mọi message lỗi.

## 3. Chi phí ném: điều khiển luồng và unwind

Câu `throw` chuyển điều khiển ra khỏi đường bình thường tới một handler tương thích, hoặc tiếp tục truyền qua các frame gọi cho đến khi có handler hay tới ranh giới uncaught exception. Trong quá trình đó, Java phải thực hiện các `finally` áp dụng và các thao tác cleanup của `try-with-resources` trước khi rời phạm vi.

Ví dụ:

```java
void outer() {
    try {
        inner();
    } finally {
        releaseOwnedResource();
    }
}

void inner() {
    throw new IllegalStateException("failed");
}
```

Đây là snippet minh họa, `releaseOwnedResource` chưa định nghĩa. Khi `inner` ném, việc thoát qua `outer` làm `finally` chạy. Nếu cleanup có I/O hoặc khóa, **chi phí quan sát trong lần thất bại bao gồm cả công việc cleanup**, không đơn thuần là opcode `athrow`.

Cần tách hai việc:

- `throw new MyException(...)`: tạo **rồi** ném. Phép đo quanh câu này bao gồm constructor, stack capture và throw/unwind.
- `throw existingException`: ném lại **cùng object** hiện có; không gọi constructor mới chỉ vì thực thi `throw`.

Ném lại object không mặc nhiên cập nhật stack trace tới vị trí ném mới. Điều này có thể làm thông tin điều tra lỗi kém hữu ích. **Không dùng cache/reuse một exception toàn cục như mẹo tối ưu hóa chung**: stack trace có thể chỉ phản ánh nơi object được tạo, dữ liệu exception có thể bị dùng chung qua nhiều thread và các thông tin cause/suppressed khó quản lý.

Thay vì tối ưu bất chấp khả năng chẩn đoán, hãy xác định có thật sự cần dùng exception như tín hiệu cho tình huống xảy ra hàng triệu lần hay không.

## 4. Chi phí bắt: đừng nhầm `try/catch` với đường ném lỗi

Một khối `try` chứa đường thành công:

```java
int result;
try {
    result = Integer.parseInt("123");
} catch (NumberFormatException ex) {
    result = 0;
}
```

Ở trường hợp này không có `NumberFormatException` được tạo và ném chỉ vì có câu lệnh `catch`. JVM/JIT có thể tối ưu đường xử lý bình thường, mặc dù cấu trúc exception và khả năng xảy ra nhánh lỗi có thể ảnh hưởng một số tối ưu hóa tùy code/JVM.

Khi thực sự vào `catch`:

```java
catch (NumberFormatException ex) {
    failedRecords.incrementAndGet();
    return defaultValue;
}
```

Đây là snippet minh họa, `failedRecords` và `defaultValue` được định nghĩa ở nơi khác. Chi phí **logic trong catch** (counter, tạo giá trị dự phòng, ghi log, thao tác đồng bộ, truy cập DB...) có thể lớn hơn rất nhiều so với việc gán reference exception cho biến `ex`.

Đừng đo `try/catch` bằng cách luôn ném một exception rồi gọi đó là “chi phí của catch”. Đó là **chi phí tổng hợp của đường thất bại**, không chỉ handler.

Một cấu trúc `catch` không thực thi trong đường thành công thường không phải lý do hợp lý để bỏ kiểm tra lỗi. Ngược lại, ném exception để biểu diễn vòng lặp kết thúc bình thường hoặc một kết quả dự kiến xảy ra hàng triệu lần có thể là quyết định thiết kế gây chi phí không cần thiết.

## 5. Chi phí ghi log là một giai đoạn riêng

Ghi log không phải hoạt động bắt buộc của `throw` hoặc `catch`. Nó được kích hoạt khi code ghi nhận exception:

```java
try {
    work();
} catch (IOException ex) {
    logger.log(Level.WARNING, "Không đọc được file", ex);
    throw ex;
}
```

Snippet này giả định `work`, `logger` và `IOException` được khai báo/import. Handler vừa ghi log vừa rethrow, nên **caller vẫn nhận lỗi**. Nếu tầng cao hơn cũng log lại, cùng một sự cố có thể tạo nhiều bản ghi stack trace.

Một bản ghi lỗi có thể gây chi phí ở nhiều nơi:

1. Tính message, ghép chuỗi hoặc tạo object context.
2. Lấy và render stack trace; có thể đi qua cả `cause` và `getSuppressed()`.
3. Định dạng timestamp, thread, MDC/correlation ID, JSON hoặc các trường structured logging.
4. Xếp bản ghi vào hàng đợi hoặc đồng bộ với appender.
5. Ghi console, file, network hoặc hệ thống tập trung.
6. Mở rộng dữ liệu log, tiêu tốn CPU, băng thông, disk, retention và chi phí vận hành.

Với async logger, một phần chi phí có thể dời sang thread khác, nhưng **không biến mất**. Hàng đợi đầy có thể làm block, làm rơi log theo chính sách hoặc tăng độ trễ; cấu hình cụ thể quyết định.

`Throwable.toString()` chủ yếu biểu diễn loại và message, **không tương đương in toàn bộ stack trace**. `printStackTrace()` hoặc logging một `Throwable` dưới dạng stack trace có thể tạo ra nhiều dòng hơn đáng kể. `getStackTrace()` còn có thể materialize dữ liệu frame trước khi format thành text.

### “Logger tắt” không có nghĩa mọi đối số đều miễn phí

Xét:

```java
logger.fine("Trace: " + serializeExpensiveObject(value));
```

Đây là snippet minh họa; `logger` thuộc một logger thích hợp và `serializeExpensiveObject` giả định tồn tại. Java phải đánh giá đối số trước khi gọi method, nên phần serialize/nối chuỗi **vẫn có thể xảy ra** ngay cả khi mức `FINE` không được bật.

Khi dùng `java.util.logging.Logger`, có thể truyền `Supplier<String>` ở API phù hợp để tạo message lười:

```java
logger.log(Level.FINE, () -> "Trace: " + serializeExpensiveObject(value));
```

Snippet này cần import `java.util.logging.Level` và logger tương ứng. Khi level không được bật, cách ghi lười cho phép bỏ qua việc tạo message. Điều đó **không có nghĩa không có bất kỳ overhead nào**: vẫn có chi phí kiểm tra mức log, tạo/điều phối lambda tùy JIT và lời gọi logger.

Khi muốn log `Throwable`, chọn overload phù hợp với framework, ví dụ `logger.log(Level.WARNING, "Lỗi đọc", ex)` của `java.util.logging`. Tránh format stack trace thành chuỗi trước khi đã biết bản ghi có cần xuất hay không.

## 6. Ví dụ Java 17: đếm các giai đoạn mà không giả vờ đo thời gian

Ví dụ dưới đây tách “tạo object”, “ném”, “catch” và “log” bằng counters. Đây là **minh họa thứ tự và nơi phát sinh hoạt động**, không phải benchmark về nanoseconds.

Tạo file `ExceptionPhasesDemo.java`:

```java
public class ExceptionPhasesDemo {

    private static final class InvalidQuantityException
            extends RuntimeException {
        static int constructed;

        InvalidQuantityException(String message) {
            super(message);
            constructed++;
        }
    }

    private static int thrown;
    private static int caught;
    private static int logged;

    private static int addOne(int quantity) {
        if (quantity < 0) {
            InvalidQuantityException error =
                    new InvalidQuantityException("quantity < 0");
            thrown++;
            throw error;
        }
        return quantity + 1;
    }

    private static void recordLog(InvalidQuantityException error) {
        logged++;
        System.out.println("LOG " +
                error.getClass().getSimpleName() +
                ": " + error.getMessage());
    }

    public static void main(String[] args) {
        System.out.println("normal: " + addOne(2));

        InvalidQuantityException onlyCreated =
                new InvalidQuantityException("created only");
        System.out.println("created without throw: "
                + onlyCreated.getMessage());

        try {
            addOne(-1);
        } catch (InvalidQuantityException error) {
            caught++;
            recordLog(error);
        }

        System.out.println("constructed: "
                + InvalidQuantityException.constructed);
        System.out.println("thrown: " + thrown);
        System.out.println("caught: " + caught);
        System.out.println("logged: " + logged);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 ExceptionPhasesDemo.java
java ExceptionPhasesDemo
```

Stdout:

```text
normal: 3
created without throw: created only
LOG InvalidQuantityException: quantity < 0
constructed: 2
thrown: 1
caught: 1
logged: 1
```

Tại sao có số đếm này?

- `addOne(2)` thành công, không tạo `InvalidQuantityException`.
- `new InvalidQuantityException("created only")` gọi constructor một lần nhưng không ném hoặc log.
- `addOne(-1)` tạo một exception khác, tăng counter `thrown` rồi ném.
- `catch` bắt đúng một lần và gọi `recordLog` một lần.
- Vì vậy số lần tạo là **2**, trong khi số lần ném, bắt và ghi log đều là **1**.

Counter `thrown` trong demo tăng thủ công ngay trước `throw`; nó **không phải cơ chế JVM tự đếm exception**. Đừng đọc các số này như kết quả benchmark thời gian. Mục đích là chứng minh bốn hoạt động khác nhau bằng những điều kiện chạy có thể kiểm tra.

## 7. Minh họa eager và lazy message formatting

Để chứng minh chi phí tạo message có thể phát sinh ngay cả khi không in log, dùng một logger tối giản với `Supplier<String>`. Demo không đại diện cho cấu trúc nội bộ của các logging framework thực.

Tạo file `LazyLogDemo.java`:

```java
import java.util.function.Supplier;

public class LazyLogDemo {

    private static int formattingCalls;

    private static String format(Throwable error) {
        formattingCalls++;
        return error.getClass().getSimpleName()
                + ": " + error.getMessage();
    }

    private static void log(boolean enabled, Supplier<String> message) {
        if (enabled) {
            System.out.println("LOG " + message.get());
        }
    }

    public static void main(String[] args) {
        Throwable error = new IllegalStateException("bad data");

        log(false, () -> format(error));
        System.out.println("lazy disabled: " + formattingCalls);

        String eager = format(error);
        log(false, () -> eager);
        System.out.println("eager disabled: " + formattingCalls);

        log(true, () -> format(error));
        System.out.println("enabled: " + formattingCalls);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 LazyLogDemo.java
java LazyLogDemo
```

Stdout:

```text
lazy disabled: 0
eager disabled: 1
LOG IllegalStateException: bad data
enabled: 2
```

Dòng thứ nhất chứng minh lambda `format(error)` không được gọi khi logger giả lập bị tắt. Trong ví dụ eager, `format(error)` được gọi **trước `log`** nên counter vẫn tăng dù không in. Khi bật, supplier được đánh giá và in một bản ghi.

Bản thân object `error` đã được tạo trước các lệnh log và có chi phí tạo riêng. **Lazy logging chỉ giúp hoãn/bỏ qua phần đánh giá message**, không hoàn tác chi phí của một exception đã được tạo.

Với production logger, ưu tiên API hỗ trợ parameterized/lazy messages hoặc kiểm tra level trước một phép xây dựng context rất đắt. Kiểm tra level thủ công cho mọi log nhỏ không nhất thiết đáng công; nên đo những điểm thực sự nóng.

## 8. Chụp stack trace và định dạng stack trace không phải một việc

Thông tin vị trí lỗi rất hữu ích. Nhưng trong một số thiết kế đặc biệt, người ta muốn giảm chi phí tạo stack trace cho một loại tín hiệu xảy ra cực nhiều lần.

`Throwable` có protected constructor cho phép cấu hình **suppression** và khả năng ghi stack trace (*writable stack trace*). Nếu constructor của subclass gọi `super(message, cause, enableSuppression, writableStackTrace)` với `writableStackTrace=false`, implementation chuẩn sẽ không ghi stack trace cho object đó. Đây là lựa chọn **có đánh đổi lớn về khả năng chẩn đoán**, không phải mặc định nên dùng.

Tạo file `StackTraceChoiceDemo.java`:

```java
public class StackTraceChoiceDemo {

    private static final class LightweightFailure
            extends RuntimeException {
        LightweightFailure(String message) {
            super(message, null, true, false);
        }
    }

    public static void main(String[] args) {
        RuntimeException normal =
                new RuntimeException("normal failure");
        RuntimeException lightweight =
                new LightweightFailure("light failure");

        System.out.println("normal has frames: "
                + (normal.getStackTrace().length > 0));
        System.out.println("light frames: "
                + lightweight.getStackTrace().length);
        System.out.println("light message: "
                + lightweight.getMessage());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 StackTraceChoiceDemo.java
java StackTraceChoiceDemo
```

Trong JDK 17 thông thường, stdout:

```text
normal has frames: true
light frames: 0
light message: light failure
```

`normal` có backtrace từ lúc tạo. `lightweight` cố ý không giữ stack trace; vẫn có message và vẫn là exception hợp lệ. Tuy nhiên, một log sau này sẽ không cho biết chính xác chuỗi lời gọi tạo exception đó. Khi xử lý bug production, cái giá mất vị trí lỗi có thể cao hơn phần CPU tiết kiệm.

**Chỉ cân nhắc exception không stack trace khi** đây là lựa chọn được thiết kế có chủ đích, có ngữ cảnh chẩn đoán khác, đã có số đo chứng minh chi phí là vấn đề và lỗi không phải trạng thái bất thường cần điều tra đầy đủ. Không tắt stack trace cho mọi exception hoặc exception public quan trọng chỉ để “làm nhanh”.

Java cũng cho phép override `fillInStackTrace()` trong subclass; việc này thay đổi semantics và có thể gây khó chẩn đoán. Đừng sử dụng kỹ thuật đó như mẹo tối ưu chung mà không có test và tài liệu hợp đồng.

## 9. Tái sử dụng exception có thể làm sai thông tin lỗi

Một mẹo thường thấy:

```java
private static final RuntimeException BAD_INPUT =
        new IllegalArgumentException("bad input");

void validate(boolean valid) {
    if (!valid) {
        throw BAD_INPUT;
    }
}
```

Đây là snippet minh họa đặt trong một class. Mỗi lần thất bại không tạo object mới, nhưng `BAD_INPUT` thường có stack trace tại nơi field được khởi tạo, không phải tại điểm kiểm tra gây lỗi cụ thể. Cùng object còn có thể được dùng qua nhiều thread; nếu một nơi bổ sung suppressed exception hoặc thao tác với trạng thái object, việc điều tra trở nên khó hiểu.

Đừng tự động chuyển exception thành singleton vì thấy constructor đắt. Nếu tình huống “bad input” dự kiến xuất hiện ở tần suất cao, cân nhắc việc **trả kết quả validation có kiểu** thay vì ném exception cho mọi trường hợp.

Ngoại lệ tái sử dụng, no-stack exception và thay đổi semantics của stack trace chỉ nên xuất hiện sau khi đã hiểu nhu cầu chẩn đoán, API contract và dữ liệu profiling.

## 10. Khi nào không nên dùng exception làm luồng điều khiển thường xuyên?

Exception thích hợp để thể hiện **đường thất bại, precondition vi phạm hoặc trạng thái đặc biệt theo contract API**. Nhưng nếu một sự kiện xảy ra thường xuyên và được xem như một kết quả bình thường, có thể dùng API không ném lỗi.

Ví dụ tìm kiếm có thể “không thấy” rất thường xuyên:

```java
Optional<String> found = lookup(key);
if (found.isEmpty()) {
    // Đây là một kết quả hợp lệ của truy vấn.
}
```

Đây là snippet minh họa, giả định `lookup` trả `Optional<String>`. Nếu vắng mặt là trạng thái bình thường, `Optional.empty()` hoặc result type có thể rõ hơn việc tạo/ném `NoSuchElementException` cho mỗi lần miss.

Một API parse dữ liệu nhận input người dùng hàng loạt có thể trả:

```java
record ParseResult<T>(T value, String errorCode) {
    boolean isSuccess() {
        return errorCode == null;
    }
}
```

Đây là ví dụ kiểu dữ liệu minh họa với `record` (hỗ trợ trong Java 17), chưa xây dựng parser hoàn chỉnh. API thực cần xác định invariant (ví dụ chỉ một trong value/errorCode hiện diện) và tránh trường hợp `null` mơ hồ.

Tuy nhiên, không phải mọi `NumberFormatException` nên bị thay bằng kiểu result. Nếu chương trình hiếm khi nhận dữ liệu sai, code dùng exception có thể đơn giản, đúng semantics và không đáng tối ưu. **Hiệu năng không phải lý do bỏ validation hoặc che lỗi.**

So sánh:

| Tình huống | Hướng tiếp cận đáng cân nhắc |
| --- | --- |
| Một yêu cầu API hiếm khi vi phạm precondition | Ném exception rõ loại và message. |
| Parser xử lý hàng triệu giá trị, dữ liệu lỗi là tình huống dự kiến | Result/status cho từng record, gom lỗi và báo cáo cuối batch. |
| Kết nối mạng bất ngờ thất bại | Exception/I/O error có ngữ cảnh; xử lý retry theo chính sách. |
| Tra cứu optional key rất thường xuyên không có dữ liệu | `Optional`/kết quả vắng mặt, không biến miss bình thường thành exception burst. |
| Bug invariant nội bộ bị phá | Giữ exception/stack trace để chẩn đoán, không “tối ưu” bằng fallback giả. |

## 11. Vì sao một stack trace log lại có thể rất đắt?

Tạo stack trace và **log stack trace** khác nhau. `Throwable#printStackTrace` có thể duyệt danh sách frame, cause và suppressed exceptions, biến chúng thành text rồi đưa qua `PrintStream`/`PrintWriter`. Logging framework có thể làm điều tương tự với định dạng JSON, MDC và exporter riêng.

Chi phí log có thể tăng theo:

- Số exception mỗi giây.
- Độ sâu stack trace và số chuỗi `caused by`/`suppressed`.
- Kích cỡ metadata log và độ dài message.
- Console sync I/O, tốc độ đĩa, mạng hoặc throughput collector.
- Hàng đợi async bị đầy hoặc việc chuyển sang chế độ block/drop.
- Số lần cùng exception được ghi ở nhiều tầng.

Một lỗi lặp 10.000 lần/phút không chỉ tốn cấp phát exception; nếu mỗi lần ghi hàng chục dòng, logging có thể trở thành tải nặng và gây ra vòng phản hồi: hệ thống đã chậm vì lỗi lại chậm hơn vì log.

### Log ở ranh giới có trách nhiệm

Mẫu dễ gây **double logging**:

```java
try {
    repository.save(entity);
} catch (IOException ex) {
    logger.log(Level.SEVERE, "Repository failed", ex);
    throw ex;
}
```

Nếu service và request handler phía trên cũng log đầy đủ stack trace, một sự cố có thể thành ba bản ghi. Đoạn này chỉ là snippet minh họa; `repository`, `entity`, `logger` phải được định nghĩa đúng ngữ cảnh.

Một hướng thiết kế là:

- Tầng thấp **ném/đổi nghĩa có cause** khi cần, không luôn log.
- Tầng có đủ ngữ cảnh nghiệp vụ quyết định thông điệp và mức log.
- Tầng ngoài cùng hoặc owner workflow ghi một bản ghi lỗi có stack trace nếu thực sự cần.
- Với lỗi input dự kiến, thường log ở mức phù hợp, có thể không cần stack trace trên từng request.
- Dùng counters/metrics cho tần suất lỗi dự kiến; không thay monitoring bằng hàng triệu bản ghi giống nhau.

Không có quy tắc “chỉ được log đúng một lần” cho mọi hệ thống; một số lỗi cần audit và operational logging khác nhau. Nhưng **mỗi bản ghi cần mục đích rõ**, không chỉ để tự bảo đảm “ở đây cũng log”.

## 12. Chọn mức log và sampling theo ý nghĩa lỗi

Một gợi ý thực tế:

| Loại sự kiện | Hướng ghi nhận |
| --- | --- |
| Input validation sai thường gặp | Trả mã lỗi cho caller, đếm metric; log chọn lọc và tránh lộ payload. |
| Lỗi hệ thống bất thường có tác động | Log có stack trace và correlation ID ở tầng chịu trách nhiệm. |
| Lỗi mạng thoáng qua đã retry thành công | Metric retry/tỷ lệ lỗi; log mức phù hợp, tránh ghi full stack cho mỗi attempt nếu không cần. |
| Lỗi lập trình làm hỏng invariant | Giữ nguyên stack/cause; log phục vụ điều tra và cảnh báo. |
| Exception burst do một dependency down | Tăng metric, cảnh báo, có thể rate limit/sample log theo chính sách; không che mất tín hiệu tổng hợp. |

Sampling/rate limiting giúp tránh log storm nhưng cần giữ **số đếm tổng sự kiện** để biết quy mô. Nếu chỉ log 1% mà không có metric tương ứng, bảng log dễ đánh giá thấp mức độ nghiêm trọng.

Không dùng error message hoặc raw stack trace làm metric label có cardinality cực lớn. Ưu tiên loại lỗi, mã lỗi, nhóm subsystem hay thao tác hữu hạn. Dữ liệu nhạy cảm trong message phải được lọc hoặc mã hóa theo chính sách bảo mật.

## 13. try-with-resources, suppressed exception và chi phí cleanup

Khi một hoạt động lỗi, phần chi phí đo ở call-site có thể bao gồm cleanup. Với `try-with-resources`:

```java
try (InputStream in = Files.newInputStream(path)) {
    parse(in);
}
```

Đây là snippet minh họa, cần import `InputStream`, `Files` và biến `path`, hàm `parse`. Nếu `parse` ném exception, Java vẫn gọi `in.close()`. Nếu close cũng ném exception, exception close thường được gắn bằng `addSuppressed` vào primary exception.

Do đó, một benchmark đo `throw new IOException` bên trong resource scope có thể đang đo:
1. Constructor và stack capture của IOException chính.
2. Throw/unwind.
3. I/O cleanup (`close`), có thể đắt hơn nhiều.
4. Nếu close thất bại, tạo/cấp phát thêm exception và ghi suppressed state.
5. Catch/log phía bên ngoài nếu được gọi.

Đừng xem kết quả đó là chi phí thuần của opcode throw hay handler.

Quan trọng hơn: **không bỏ cleanup để giảm latency**. Rò rỉ file descriptor, connection, socket hay lock thường tệ hơn chi phí xử lý exception. Tối ưu đúng là sắp xếp quyền sở hữu, thời điểm đóng và xử lý lỗi phù hợp.

## 14. Checked và unchecked exception có tạo khác biệt hiệu năng không?

`IOException` là checked, `IllegalStateException` là unchecked. Quy tắc kiểm tra ở compile time khác nhau: checked exception phải được khai báo hoặc bắt theo luật Java; unchecked không bắt buộc. Nhưng **không có quy tắc chung** rằng checked exception luôn có chi phí runtime cao hơn hoặc thấp hơn unchecked chỉ vì chữ ký `throws`.

Sự khác biệt về chi phí thường đến từ:
- Loại exception, constructor/message và cách stack trace được xử lý.
- Tần suất tạo/ném.
- Độ sâu stack hoặc cleanup khi unwind.
- Logic của handler và logging.
- Kỹ thuật tối ưu của JIT và đặc tính runtime.

Không chuyển toàn bộ `IOException` thành `RuntimeException` chỉ với lý do “unchecked nhanh hơn”. Chuyển exception có thể hợp lý ở boundary để ổn định contract hoặc thêm context, nhưng việc tạo wrapper mới lại có thể thu thập một stack trace khác; nếu còn giữ cause, log đầy đủ có thể in cả hai chuỗi thông tin.

## 15. Các bẫy benchmark về hiệu năng exception

Microbenchmark tự viết kiểu:

```java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    try {
        throw new RuntimeException("failed");
    } catch (RuntimeException ignored) {
    }
}
System.out.println(System.nanoTime() - start);
```

Đây là snippet minh họa về **benchmark không đủ tin cậy**, không phải phép đo dùng để ra quyết định production. Nó trộn **construction + throw + catch**, có thể chịu tối ưu hóa JIT/GC, quá trình warm-up, môi trường CPU, mức log khác nhau và ảnh hưởng của code bị compiler chứng minh không có side effect có ích. Chạy một lần và lấy số nanosecond không đủ kết luận.

### Vì sao benchmark ngây thơ sai?

**Warm-up và JIT:** Những lần chạy đầu thường bao gồm class loading, compilation và trạng thái code chưa được tối ưu. Đường exception thường hiếm trong production nhưng benchmark lại ném liên tục, tạo profile hoàn toàn khác.

**Escape analysis và tối ưu:** JIT có thể tối ưu cấp phát/đường đi khác với dự đoán từ source, hoặc code không có kết quả quan sát bị loại bỏ. Benchmark phải đảm bảo thực hiện công việc có nghĩa.

**HotSpot fast-throw:** Một số bản HotSpot có tối ưu đặc biệt với một số exception **do JVM tự tạo** tại các vị trí lỗi lặp (ví dụ implicit null/bounds checks); stack trace có thể bị lược bỏ trong một số điều kiện. Kết quả không được suy rộng sang `throw new MyDomainException(...)` hoặc mọi JVM/JDK. Flag/runtime/version ảnh hưởng hành vi; cần xác minh JVM đích trước khi suy luận.

**Độ sâu và vị trí stack:** Tạo lỗi ở phương thức sâu có thể khác tạo lỗi ở đỉnh stack; `throw` qua nhiều frame/`finally` khác catch tại chỗ.

**Log level:** Benchmark ghi stack trace ra console/file không thể so sánh trực tiếp với đường chỉ `catch` rồi trả fallback. Log stdout/stderr còn phụ thuộc buffering và môi trường terminal.

**GC và allocation:** Vòng lặp tạo nhiều Throwable gây áp lực allocation; cần đo thêm throughput/allocations/GC, không chỉ thời gian một lần gọi.

**Branch probability:** Nếu 0,001% request lỗi, chi phí trung bình trên request rất khác khi test luôn throw 100%. Cần đo cả hot path thành công và error burst.

### Cách đo đúng hơn

Đối với microbenchmark Java, nên dùng **JMH (Java Microbenchmark Harness)**, có warm-up, nhiều iteration/fork và cơ chế hạn chế một số bẫy JIT/benchmark. JMH là thư viện/harness bên ngoài JDK, không có sẵn chỉ từ `javac`; cần thiết lập project/dependency phù hợp trước khi chạy.

Thiết kế các phép đo riêng:

1. **Successful path:** có `try/catch` nhưng không tạo exception.
2. **Construct-only:** tạo Throwable nhưng không `throw` và không log; đảm bảo object là kết quả quan sát được.
3. **Throw pre-created:** ném object có sẵn để cô lập gần hơn phần throw/catch, nhưng ghi rõ semantics stack trace khác đường thông thường.
4. **Throw new:** tạo và ném trong cùng nhánh; đại diện cho tình huống phổ biến nhưng không phải chi phí “ném thuần”.
5. **Catch + lightweight handling:** có phân loại/trả fallback mà không log.
6. **Catch + log disabled:** kiểm tra overhead của logging call và argument evaluation.
7. **Catch + log enabled:** đo format và appender thực; với async cần tính tải consumer/hàng đợi.
8. **Different stack depths/error rates:** kiểm tra ảnh hưởng độ sâu frame, các loại exception và tần suất lỗi.

Phân biệt **microbenchmark** với **profiling của ứng dụng thật**. Một thay đổi tối ưu constructor có thể chẳng ảnh hưởng p95 latency nếu network/I/O mới là bottleneck. Hãy kết hợp CPU profile, allocation profile, GC logs, log throughput và metrics của đường lỗi trước khi quyết định.

## 16. Ví dụ mã đo minh họa: cách tổ chức phép thử, không suy diễn số cố định

Ví dụ dưới đây in số lượng thao tác chứ **không** cố tạo benchmark từ `System.nanoTime`:

Tạo file `ErrorPathClassificationDemo.java`:

```java
public class ErrorPathClassificationDemo {

    private static int created;
    private static int handled;

    private static IllegalArgumentException newFailure() {
        created++;
        return new IllegalArgumentException("invalid");
    }

    private static boolean validate(int value) {
        if (value >= 0) {
            return true;
        }
        throw newFailure();
    }

    private static void process(int[] input) {
        for (int value : input) {
            try {
                validate(value);
            } catch (IllegalArgumentException ex) {
                handled++;
            }
        }
    }

    public static void main(String[] args) {
        process(new int[] {1, 2, -1, 3, -2, 4});
        System.out.println("items: 6");
        System.out.println("created: " + created);
        System.out.println("handled: " + handled);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ErrorPathClassificationDemo.java
java ErrorPathClassificationDemo
```

Stdout:

```text
items: 6
created: 2
handled: 2
```

Bài toán kiểm tra sáu đầu vào, chỉ hai giá trị âm đi qua đường exception. Đối với việc benchmark, ta sẽ cần chạy nhiều tỉ lệ lỗi (0%, 1%, 50%, 100%) với dữ liệu có ý nghĩa và harness phù hợp. **Không lấy 2/6 rồi kết luận 33% CPU dành cho exception**; đây chỉ là tỉ lệ item lỗi trong dữ liệu mẫu.

Khi kiểm tra hiệu năng production, đo **số lỗi trên mỗi request, số exception object được cấp phát, chi phí ghi log và latency tăng thêm**, thay vì chỉ nhìn số lần `catch` xuất hiện trong source code.

## 17. Thiết kế API sao cho ít lỗi nhưng vẫn dễ chẩn đoán

Một thiết kế cân bằng thường có các nguyên tắc:

**Dùng exception cho thất bại đáng được coi là exceptional.** Mọi vi phạm ràng buộc nghiêm trọng hoặc lỗi bất ngờ cần đường xử lý rõ. Đừng xóa `throw` vì sợ overhead nếu hiếm khi xảy ra.

**Dùng result type cho tình huống dự kiến tần suất cao.** Ví dụ parser batch nhận lỗi mỗi record là bình thường; trả trạng thái thay vì ném exception hàng triệu lần có thể có lợi và giúp người gọi phân loại chính xác.

**Tạo exception tại thời điểm biết có lỗi.** Tránh pre-construct object hoặc format thông điệp đắt trên successful path.

**Giữ cause khi dịch exception.** Tầng persistence có thể đổi `SQLException` thành `RepositoryUnavailableException` để ổn định hợp đồng, nhưng giữ `cause` để chẩn đoán. Việc tạo wrapper có chi phí; chỉ làm khi thực sự có giá trị về abstraction.

**Ghi log tại ranh giới hợp lý.** Khi bắt rồi rethrow, không bắt buộc phải log thêm stack trace tại chỗ. Caller thường có ngữ cảnh tốt hơn để quyết định mức severity và response.

**Bảo toàn lỗi và cleanup.** Dùng `try-with-resources`/`finally` đúng quyền sở hữu, phân biệt primary/cause/suppressed. Không hy sinh cleanup để cải thiện con số benchmark.

**Đừng làm logging biến validation error thành nguồn phát tán dữ liệu.** Không ghi nguyên token, thông tin xác thực, payload cá nhân vào message exception hay log.

**Quan sát bằng metrics có cardinality ổn định.** Đếm lỗi theo loại và hệ thống, thay vì gắn nguyên message/stack làm label.

## 18. So sánh các chiến lược trên tình huống thực tế

### Tình huống A: API nhận input sai với tần suất nhỏ

Giải pháp có thể là:

```java
if (quantity <= 0) {
    throw new IllegalArgumentException("quantity must be positive");
}
```

Đây là precondition rõ ràng. Nếu caller gửi sai hiếm, chi phí tạo/ném chỉ xảy ra ở nhánh lỗi. Handler HTTP có thể trả phản hồi 400, thống kê tỉ lệ validation và tránh in stack trace đầy đủ của mỗi yêu cầu nếu không cần.

### Tình huống B: import một triệu bản ghi, 30% bản ghi không hợp lệ

Đổi mọi record lỗi thành exception rồi log full stack sẽ tạo nhiều object và bản ghi lặp. Có thể dùng một result/status cho mỗi record, thu thập các mã lỗi, chỉ xuất báo cáo/tổng hợp theo batch. Khi phát sinh lỗi hệ thống bất thường trong quá trình import, exception với stack trace vẫn hữu ích.

### Tình huống C: dependency mạng lỗi theo đợt

Đôi khi chính việc gọi network chậm hơn nhiều so với constructor exception. Nhưng khi lỗi diện rộng, ghi log lặp ở mọi lớp tạo log storm. Dùng retry có giới hạn, circuit breaker nếu cần, metrics/tỷ lệ lỗi, sampling log và một nơi ghi thông điệp đủ ngữ cảnh. Không đánh giá “tăng tốc” chỉ bằng cách bỏ stack trace nếu bottleneck thật sự là network hoặc queue.

### Tình huống D: thư viện tạo exception trước để dùng cho nhiều request

Khi lỗi hiếm, pre-construct exception có thể đưa chi phí tạo vào startup và làm mất stack của vị trí request cụ thể. Nếu cache exception dùng chung, chẩn đoán có thể sai và suppressed/cause có thể bị chia sẻ. Chỉ làm khi có hợp đồng rõ ràng và thực sự được đo/kiểm chứng; đa số trường hợp nên tạo exception khi thất bại.

### Tình huống E: logger ở mức DEBUG tắt trong production

Nếu code vẫn gọi một formatter rất nặng trước logger, tắt log level chưa chắc đã bỏ chi phí chuẩn bị message. Dùng lazy/parameterized logging hoặc guard level cho biểu thức đắt; profile sau thay đổi để xác nhận lợi ích.

## 19. Lỗi thường gặp và cách sửa

**“Chỉ có `try/catch` cũng làm chương trình chậm tương đương ném lỗi.”** Sai. Đường không lỗi thường không tạo và ném exception; cần phân biệt xử lý bình thường với exception path.

**“Chi phí throw bằng chi phí new.”** Sai. Constructor/backtrace và throw/unwind là các giai đoạn khác nhau; `throw new` trộn cả hai.

**“Catch là giai đoạn tốn nhất.”** Không có quy tắc như vậy. Logic trong catch, log stack trace, I/O và cleanup có thể chiếm phần lớn chi phí.

**“Log level OFF thì không có chi phí.”** Đối số được tính trước lời gọi vẫn có thể tốn CPU/cấp phát. Dùng API lazy/parameterized để tránh phần format đắt khi không cần.

**“Chỉ cần `error.toString()` là đã in stack trace.”** Không. `toString` thường trả loại + message; `printStackTrace` và logging Throwable đầy đủ có thêm frame/cause/suppressed.

**“Log exception tại mọi tầng mới an toàn.”** Có thể tạo nhiều bản ghi cho cùng failure và che tín hiệu thật trong log storm. Ghi tại boundary có trách nhiệm, giữ cause khi truyền tiếp.

**“Cứ tắt stack trace để tăng tốc.”** Có thể làm mất thông tin điều tra critical bug. Tối ưu no-stack chỉ khi contract cho phép và profiling chứng minh lợi ích.

**“Reuse singleton exception luôn nhanh và an toàn.”** Dễ dẫn đến stack trace sai nơi ném và object dùng chung giữa nhiều thread. Tránh nếu chưa có lý do rõ ràng.

**“Checked exception đắt hơn unchecked exception vì compiler yêu cầu catch.”** Đây là quy tắc kiểu tại compile time, không phải bảng xếp hạng runtime overhead.

**“Dùng System.nanoTime một vòng lặp là đủ chứng minh.”** Microbenchmark cần warm-up, nhiều mẫu, chống JIT elimination và môi trường đo nhất quán; nên dùng JMH và xác minh trên workload thật.

**“Exception ít nhưng có rất nhiều log nên chắc bottleneck tại throw.”** Không. Cần đo riêng formatting, stack rendering và appender/hàng đợi log.

**“Bỏ finally/close để giảm chi phí khi lỗi.”** Cleanup đúng là một phần invariant. Tối ưu hóa làm rò rỉ tài nguyên là sai mục tiêu.

## 20. Checklist trước khi tối ưu

1. **Chi phí đang nằm ở đâu?** Constructor/stack capture, unwind, catch logic, cleanup, message formatting hay appender log?
2. **Tần suất nhánh lỗi thực tế?** 0,001%, 1%, 50% hay error burst 100%? Đường nóng của ứng dụng là gì?
3. **Exception có là kết quả dự kiến thường xuyên không?** Nếu có, `Optional`/status/result type có làm hợp đồng rõ hơn không?
4. **Có xây dựng message/format JSON trước khi biết cần lỗi/log không?** Có thể tạo lười không?
5. **Có log cùng failure ở nhiều tầng không?** Có thể giữ một event/metric rõ ownership không?
6. **Đang in full stack trace cho mọi lỗi input có tính dự kiến không?** Có thể dùng metric và sample log phù hợp không?
7. **Có giữ được cause, suppressed, cleanup và trạng thái thread không?** Đừng đánh đổi tính đúng đắn.
8. **Có cần stack trace để debug production không?** Nếu có, không vội dùng no-stack exception.
9. **Benchmark có tách các pha và chống sai lệch JIT/GC chưa?** Dùng JMH khi cần số microbenchmark.
10. **Kết quả có cải thiện production không?** Đối chiếu p95/p99 latency, allocation rate, CPU, GC, log throughput, queue drops và tốc độ xử lý.
11. **Có rủi ro vận hành hoặc bảo mật mới không?** Rate-limit log nhưng vẫn giữ metrics; không đưa dữ liệu nhạy cảm vào message.
12. **Regression test còn kiểm tra đúng lỗi không?** Tối ưu performance không được biến thất bại thành thành công giả hoặc thay đổi hợp đồng công khai ngoài ý muốn.

## 21. Tổng kết

**Tạo, ném, bắt và ghi log exception là bốn công việc khác nhau**. Trong Java thông thường, constructor `Throwable` có thể thu thập stack ngay khi tạo; `throw` chuyển luồng điều khiển và unwind; `catch` chạy logic xử lý; logger có thể format stack trace rồi đẩy dữ liệu qua I/O/hàng đợi. Đường thành công chỉ đi qua `try` không phải đã trả đủ mọi chi phí của đường exception.

Tối ưu hiệu quả thường bắt đầu bằng **không tạo exception trên hot path bình thường**, **không dùng exception cho kết quả dự kiến tần suất cao**, **không format log vô ích**, và **không ghi trùng stack trace ở nhiều tầng**. Khi cần số liệu, tách các thành phần khi benchmark, dùng JMH phù hợp và xác nhận bằng profiling workload thật. Vẫn phải giữ nguyên invariant, cleanup, thông tin cause/stack trace cần thiết và khả năng quan sát lỗi: hiệu năng tốt chỉ có ý nghĩa khi hệ thống tiếp tục đúng và có thể chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-003">Không dùng ngoại lệ cho nhánh điều khiển thường xuyên</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng ngoại lệ cho nhánh điều khiển thường xuyên trong Java

> **Chủ đề:** Java Core → Exception → Hiệu năng. Mục tiêu là phân biệt **thất bại bất thường** với **kết quả bình thường nhưng không thỏa điều kiện**, để lựa chọn giữa `if`, giá trị trả về, `Optional`/`OptionalInt`, result type và `throw/catch`. Nội dung giải thích cả chi phí hiệu năng, ngữ nghĩa API, ví dụ Java 17 chạy độc lập, các trường hợp phản ví dụ và cách đo đạc trước khi tối ưu.

## 1. Nguyên tắc: exception không phải câu lệnh if thứ hai

Ngoại lệ trong Java được thiết kế để biểu diễn và lan truyền một **điều kiện bất thường so với hợp đồng thao tác**. Cơ chế `throw`/`catch` giúp thoát khỏi đường xử lý thông thường, chuyển nguyên nhân lên tầng có thể xử lý và tự chạy các khối `finally` cần thiết.

Một nhánh xuất hiện thường xuyên nhưng **được dự kiến là kết quả hợp lệ** — không tìm thấy đối tượng, hết dữ liệu, người dùng bỏ trống ô tìm kiếm, hàng đợi tạm rỗng — thường nên được biểu diễn rõ bằng điều kiện hoặc kiểu kết quả. Nếu viết `throw` cho những trường hợp này trong vòng lặp nóng, bạn dễ làm mã khó đọc, làm nhiễu log, che giấu lỗi thật và trả thêm chi phí của cơ chế exception.

Ví dụ tư duy:

- **Bình thường:** không có thêm dòng nào trong file; một hàm đọc tới cuối luồng trả sentinel `-1` hoặc `null` theo contract.
- **Bình thường:** tra một key hợp lệ nhưng không có phần tử; API trả null/`Optional.empty()`/`Result.notFound` theo thiết kế.
- **Bất thường:** file chưa đọc xong nhưng storage phát sinh lỗi I/O; có thể ném `IOException`.
- **Bất thường:** một bất biến nội bộ bị phá, trạng thái API không hợp lệ hoặc caller vi phạm contract đã công bố; có thể ném exception thích hợp.
- **Theo ngữ cảnh:** nhập văn bản không parse được là bình thường với form tương tác nhiều đầu vào; nhưng có thể là lỗi cấu hình nghiêm trọng khi đọc một manifest mà nhà phát hành cam kết luôn hợp lệ.

Vì vậy, không có quy tắc “bất kỳ lỗi nào cũng không được ném” hay “cứ gọi API ném exception là code sai”. Điều quan trọng là **tần suất dự kiến, hợp đồng và điểm có quyền xử lý**.

## 2. Một phản ví dụ: duyệt danh sách bằng IndexOutOfBoundsException

Đây là **minh họa cách làm không nên dùng**:

~~~java
// Chỉ là đoạn phương thức, giả định List<String> items đã tồn tại.
int index = 0;
try {
    while (true) {
        process(items.get(index++));
    }
} catch (IndexOutOfBoundsException end) {
    // Cố coi lỗi vượt chỉ số là tín hiệu kết thúc.
}
~~~

Vấn đề:

1. Hết danh sách là điều kiện bình thường và có thể kiểm tra bằng `index < items.size()` hoặc iterator.
2. `get(index)` và mã trong `process(...)` đều có thể phát sinh exception; cách bắt quá rộng ở vòng ngoài có thể nhầm một bug thực sự với “đã duyệt xong”.
3. Một số triển khai `List` có thể thay đổi đồng thời; ví dụ không đưa ra hợp đồng consistency khi cấu trúc bị cập nhật.
4. Người đọc phải hiểu ngầm rằng exception được dùng như cách thoát loop, trong khi `for` hoặc `for-each` biểu đạt ý định trực tiếp hơn.
5. Tạo và ném exception thường xuyên khiến chương trình chịu thêm chi phí; mức chi phí cụ thể phải được đo trên JVM và tải thực tế.

Cách diễn đạt đúng:

~~~java
// Chỉ là đoạn phương thức, giả định List<String> items đã tồn tại.
for (String item : items) {
    process(item);
}
~~~

Hoặc khi thực sự cần chỉ số:

~~~java
for (int i = 0; i < items.size(); i++) {
    process(items.get(i));
}
~~~

**Ngoại lệ không nên thay thế điều kiện vòng lặp nếu chính điều kiện đó là một phần bình thường của thuật toán.**

## 3. Chi phí thực tế của việc ném ngoại lệ đến từ đâu?

Không nên hiểu đơn giản “từ khóa `throw` chậm”. Một lần exception thất bại có thể gồm nhiều công việc:

1. **Tạo đối tượng `Throwable`:** constructor của nhiều loại exception sử dụng `fillInStackTrace()` để ghi nhận thông tin stack, thường tốn hơn việc trả một giá trị đơn giản.
2. **Stack unwinding:** runtime đi ngược các frame cho tới handler phù hợp, thực hiện xử lý cleanup như `finally` hoặc try-with-resources.
3. **Ảnh hưởng tối ưu của JVM:** đường chạy khác thường có thể không được JIT tối ưu giống đường thành công; việc chuyển điều khiển và deoptimization có thể ảnh hưởng kết quả.
4. **Cấp phát và GC:** nhiều exception mới có thể tăng áp lực cấp phát và thu gom rác; mức ảnh hưởng phụ thuộc JVM, loại lỗi và khối lượng công việc.
5. **Xử lý/ghi log:** định dạng stack trace, serialization/logging exception hoặc chuyển nó qua nhiều tầng có thể còn tốn hơn việc ném riêng lẻ.
6. **Khả năng quan sát:** lượng log lỗi giả quá nhiều khiến alert, dashboard và việc phân loại sự cố kém hiệu quả.

Tuy nhiên cần chính xác:

- **Không có hằng số tốc độ cố định** cho exception so với `if`; kết quả thay đổi theo JVM, compiler, mức nóng của call site, số frame và tần suất lỗi.
- **Không phải mọi throwable đều xây stack trace đầy đủ:** một số exception/constructor tùy chọn bỏ stack trace, và JVM có thể có tối ưu đặc biệt cho những tình huống lặp lại.
- HotSpot có các hành vi tối ưu liên quan exception, nhưng **không dựa vào chi tiết riêng JVM** để biện hộ cho một nhánh điều khiển khó hiểu.
- Nếu exception **rất hiếm**, overhead của đường lỗi có thể không đáng kể; thiết kế API rõ nghĩa và bảo toàn nguyên nhân quan trọng hơn việc loại bỏ bằng mọi giá.
- Một kiểu trả về phức tạp hơn (`Optional`, result object) cũng có chi phí: cấp phát, boxing, phân nhánh hoặc chuyển dữ liệu. Không mặc định rằng thay `throw` bằng `Optional` luôn nhanh hơn.

**Tối ưu đúng là chọn ngữ nghĩa trước rồi đo đường nóng**; hiệu năng là một lý do mạnh, nhưng không phải lý do duy nhất.

## 4. Những nhánh thông thường nên có biểu diễn riêng

| Tình huống | Không nên lạm dụng | Biểu diễn dễ hiểu hơn | Lưu ý |
|---|---|---|---|
| Duyệt hết collection | Catch `IndexOutOfBoundsException` | `for`, iterator, `hasNext()` | Cần xét đồng thời nếu collection bị sửa |
| Hết dữ liệu byte | Catch exception “end of input” | `InputStream.read() == -1` | `IOException` vẫn chỉ lỗi I/O |
| Hết dòng văn bản | Catch `NullPointerException` sau `readLine()` | `readLine() == null` | Dòng rỗng `""` khác EOF |
| Hết phần tử iterator | Catch `NoSuchElementException` | `iterator.hasNext()` | Nếu có sửa đồng thời, vẫn phải tuân contract |
| Bản ghi không tồn tại | Throw rồi catch `NoSuchElementException` quanh lookup lặp lại | `Optional.empty()` hoặc result type | API có thể chủ động chọn throw cho invariant khác |
| Queue tạm rỗng | Catch `NoSuchElementException` từ `remove()` | `poll()` trả `null` khi rỗng | Chỉ phù hợp khi `null` không phải phần tử hợp lệ |
| Số nhập không hợp lệ thường xuyên | Gọi `parseInt` rồi catch trong mỗi vòng nóng | Parser có kết quả success/failure | Validate không được làm sai grammar/overflow |
| Quy tắc nghiệp vụ bị từ chối | Catch một exception cho mọi request “không đủ tồn kho” | Kết quả domain rõ ràng nếu việc từ chối là dự kiến | Vẫn có thể dùng exception ở API có contract khác |
| Option cấu hình bắt buộc thiếu | Bắt exception rồi dùng giá trị mặc định tùy tiện | Kiểm tra config lúc startup và fail fast khi sai | Đây có thể là failure thật, không nên giấu |

**Không tự thay một API chỉ vì có từ `Exception` trong chữ ký.** Nếu giao diện thư viện chọn ném checked/unchecked exception cho tình huống cụ thể, adapter của bạn có thể phải bắt nó ở ranh giới và chuyển sang kiểu kết quả phù hợp với miền nghiệp vụ.

## 5. Ví dụ Java 17 chạy độc lập: parse dữ liệu thường xuyên

Giả sử một bộ đọc import nhận nhiều chuỗi từ người dùng; dữ liệu không hợp lệ là chuyện dự kiến và cần được đếm/hiển thị. Có hai cách minh họa:

- Cách A: gọi `Integer.parseInt` trên mỗi chuỗi, bắt `NumberFormatException` để ghi nhận trường hợp không parse được.
- Cách B: hàm riêng cho **chuỗi số nguyên không âm hệ 10 chỉ chứa ký tự ASCII '0'..'9'**, trả `OptionalInt.empty()` khi không hợp lệ, không dùng exception làm kết quả từ chối thông thường.

Hai cách được thử trên cùng dữ liệu hợp lệ/phủ định tương đương; **chúng không có grammar giống hệt nhau cho mọi input**: `Integer.parseInt` cho phép dấu `+`/`-` theo quy tắc của nó, trong khi hàm kiểu kết quả ở ví dụ chỉ cho phép chữ số ASCII không dấu. Đây là **lựa chọn contract có chủ đích** cho dữ liệu nhập; không được coi parser này là thay thế drop-in cho mọi lần gọi `parseInt`.

Lưu toàn bộ thành **`ExceptionControlFlowDemo.java`**:

~~~java
import java.io.IOException;
import java.io.StringReader;
import java.util.OptionalInt;

public class ExceptionControlFlowDemo {
    record Summary(int accepted, int rejected, long sum) { }

    private static final String[] INPUTS =
            {"42", "7", "x", "", "2147483648", "001"};

    static Summary viaExceptions() {
        int accepted = 0;
        int rejected = 0;
        long sum = 0;

        for (String text : INPUTS) {
            try {
                int value = Integer.parseInt(text);
                accepted++;
                sum += value;
            } catch (NumberFormatException ex) {
                // Dữ liệu nhập không parse được là nhánh dự kiến.
                rejected++;
            }
        }
        return new Summary(accepted, rejected, sum);
    }

    static OptionalInt parseUnsignedDecimal(String text) {
        if (text == null || text.isEmpty()) {
            return OptionalInt.empty();
        }

        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return OptionalInt.empty();
            }

            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
                return OptionalInt.empty(); // Tránh overflow.
            }
            value = value * 10 + digit;
        }
        return OptionalInt.of(value);
    }

    static Summary viaResult() {
        int accepted = 0;
        int rejected = 0;
        long sum = 0;

        for (String text : INPUTS) {
            OptionalInt parsed = parseUnsignedDecimal(text);
            if (parsed.isPresent()) {
                accepted++;
                sum += parsed.getAsInt();
            } else {
                rejected++;
            }
        }
        return new Summary(accepted, rejected, sum);
    }

    static void print(String label, Summary summary) {
        System.out.printf(
                "%s: accepted=%d rejected=%d sum=%d%n",
                label, summary.accepted(),
                summary.rejected(), summary.sum());
    }

    public static void main(String[] args) throws IOException {
        Summary withExceptions = viaExceptions();
        Summary withResult = viaResult();

        print("exceptions", withExceptions);
        print("results", withResult);
        System.out.println("same summary: "
                + withExceptions.equals(withResult));

        StringReader reader = new StringReader("ab");
        int count = 0;
        while (reader.read() != -1) { // EOF là điều kiện thông thường.
            count++;
        }
        System.out.println("characters: " + count);
        System.out.println("read after EOF: " + reader.read());
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac ExceptionControlFlowDemo.java
java ExceptionControlFlowDemo
~~~

**Stdout chính xác:**

~~~text
exceptions: accepted=3 rejected=3 sum=50
results: accepted=3 rejected=3 sum=50
same summary: true
characters: 2
read after EOF: -1
~~~

Giải thích:

1. Dữ liệu hợp lệ `"42"`, `"7"` và `"001"` lần lượt cho 42, 7 và 1, tổng 50. `"x"`, `""` và `"2147483648"` bị từ chối.
2. `Integer.parseInt` ném `NumberFormatException` cho ba chuỗi không parse được. Đường B trả `OptionalInt.empty()`, không cần ném exception cho trường hợp từ chối.
3. Parser B kiểm tra ký tự và **tràn số trước khi nhân/cộng**. Nếu quên điều kiện overflow, `int` của Java có thể vòng giá trị âm mà không tự ném lỗi.
4. `OptionalInt.of(0)` là kết quả **thành công** khác `OptionalInt.empty()`. Nếu dùng một sentinel như `-1`, phải đảm bảo `-1` không nằm trong miền đầu ra hợp lệ; với số nguyên có thể âm, sentinel dễ gây mơ hồ.
5. `StringReader.read()` trả `-1` khi hết dữ liệu theo contract; đây là tín hiệu EOF bình thường, không phải lỗi phải tạo exception.
6. Chương trình **minh họa thiết kế và kết quả**, không phải benchmark hiệu năng. Nó không chứng minh đường B luôn chạy nhanh hơn đường A trên mọi JVM/dataset.

Nếu parser là một thành phần được dùng lại, hãy tài liệu hóa grammar rõ ràng: ký tự chấp nhận, dấu âm/dương, khoảng trắng, số 0 đầu, kích thước giới hạn, ký tự Unicode và hành vi với null. Việc “không throw khi input sai” không cho phép thay đổi ngầm tập dữ liệu được chấp nhận.

## 6. Dùng OptionalInt, Optional<T> và result type đúng ngữ nghĩa

### 6.1. OptionalInt khi kết quả có thể vắng mặt

`OptionalInt` biểu diễn **có một giá trị `int`** hoặc **không có giá trị**, tránh sentinel nằm trong miền dữ liệu. Nó có thể phù hợp với hàm `tryParse...`, tìm giá trị thống kê hoặc phép tìm kiếm.

~~~java
// Mã minh họa trong một phương thức.
OptionalInt result = parseUnsignedDecimal(userInput);
if (result.isPresent()) {
    use(result.getAsInt());
} else {
    showValidationError();
}
~~~

`userInput`, `use` và `showValidationError` là các thành phần ứng dụng giả định; đoạn này không phải chương trình độc lập. `OptionalInt` giúp caller nhìn thấy nhánh thất bại trong kiểu trả về nhưng chỉ mã hóa **có/không**, không nêu được *vì sao* thất bại.

### 6.2. Result type khi cần biết nguyên nhân từ chối

Nếu UI muốn báo “rỗng”, “không phải số” hay “vượt miền”, một `Result` có dữ liệu lỗi rõ nghĩa thường tốt hơn tạo exception mới cho mỗi chuỗi sai.

~~~java
// Các kiểu có thể đặt bên trong một lớp Java 17.
enum ParseError {
    EMPTY, NOT_DECIMAL, OVERFLOW
}

sealed interface ParseResult
        permits Parsed, Rejected {
}

record Parsed(int value) implements ParseResult {
}

record Rejected(ParseError reason) implements ParseResult {
}
~~~

Đây là **khai báo kiểu minh họa**, chưa có hàm parser đầy đủ. Caller có thể dùng `instanceof` hoặc pattern matching phù hợp Java 17 để phân nhánh theo kiểu. Một result type có thể mang message, offset ký tự sai hoặc mã lỗi ổn định, nhưng đừng nhồi stack trace vào mỗi kết quả từ chối bình thường.

### 6.3. Sentinel có hợp đồng rõ

`InputStream.read()` trả một số trong `0..255` khi đọc được byte, và `-1` khi EOF. `-1` an toàn vì không phải một giá trị byte hợp lệ của kết quả đọc đơn lẻ.

Trái lại, nếu một hàm tìm vị trí được phép trả vị trí âm, dùng `-1` làm “không tìm thấy” có thể gây mơ hồ. Sentinel chỉ tốt nếu **miền kết quả và tài liệu API** đảm bảo có một giá trị ngoài miền hợp lệ.

### 6.4. Null và trạng thái vắng mặt

`Map.get(key)` có thể trả `null` do thiếu key **hoặc** do key tồn tại và được ánh xạ tới `null` (với triển khai cho phép null). Vì thế khi cần phân biệt hai trạng thái, có thể cần `containsKey` hoặc một API có kiểu kết quả giàu ngữ nghĩa hơn.

Không thay tất cả exception thành null: null là tín hiệu yếu, và nếu không kiểm tra, nó có thể gây `NullPointerException` ở vị trí khó điều tra hơn. Đừng biến một lỗi bất ngờ thành null chỉ để “không dùng exception”.

## 7. Phân loại lỗi dự kiến theo tần suất và khả năng phục hồi

Cùng một tình huống có thể cần cách diễn đạt khác tùy bối cảnh:

| Hoạt động | Lỗi không parse được xuất hiện thế nào? | Gợi ý |
|---|---|---|
| Nhập từng ký tự vào ô số trên UI | Rất thường xuyên trong lúc người dùng đang gõ | Validator hoặc parse result, không log ERROR mỗi phím |
| Import một triệu bản ghi chưa được kiểm soát | Một tỷ lệ dự kiến không hợp lệ | Parse result + error code per record, thống kê theo batch |
| Đọc biến môi trường bắt buộc lúc startup | Lỗi hiếm, cấu hình không được sai | Exception fail fast với tên cấu hình và nguyên nhân |
| Giải mã một token đã ký từ nguồn không tin cậy | Nhiều token sai có thể xuất hiện do client | Xác thực/decoder API có kết quả phù hợp, giới hạn tài nguyên, không log secret |
| Chuyển kiểu giá trị mà code đã kiểm soát | Sai kiểu là bug hoặc vi phạm invariant | Exception giúp lộ lỗi lập trình |
| Đọc dữ liệu qua socket sau khi kết nối mất | Sự cố I/O cần caller xử lý hoặc retry | `IOException`/loại lỗi kết nối thích hợp |

**Đừng đổi lỗi cấu hình nghiêm trọng thành giá trị mặc định im lặng** chỉ để tránh exception: điều đó có thể khiến ứng dụng chạy với trạng thái sai và khó điều tra hơn.

## 8. Tránh kiểm tra “đoán lỗi” trước rồi vẫn phải parse lại

Một ý tưởng phổ biến là chạy regex trước `Integer.parseInt`:

~~~java
// Minh họa, không phải parser tổng quát.
if (input.matches("[0-9]+")) {
    int value = Integer.parseInt(input);
}
~~~

Cần lưu ý:

- Regex này **không kiểm tra overflow**. Một chuỗi 100 chữ số chỉ gồm ký tự `0..9` vẫn khiến `parseInt` ném exception.
- `matches` phải xử lý biểu thức chính quy; với workload cụ thể, bước này có thể tốn thêm CPU hơn việc parse trực tiếp.
- Grammar có thể không khớp: `parseInt` chấp nhận dấu cộng/trừ (với đầu vào và radix phù hợp), regex `[0-9]+` thì không.
- Có nguy cơ kiểm tra hai lần trên đường thành công: regex duyệt chuỗi, sau đó parser lại duyệt.
- Nếu input rất dài và không tin cậy, kiểm soát độ dài và giới hạn tính toán cũng quan trọng như chọn kiểu lỗi.

Một parser chuyên biệt kiểu `tryParse` nên kiểm tra **đúng grammar trong một lượt**, kiểm soát overflow, không ném exception trên nhánh từ chối thông thường. Nhưng việc tự viết parser có nguy cơ bug; nếu API chuẩn chỉ được gọi vài lần ở ranh giới, `Integer.parseInt` với `catch` có thể vẫn là lựa chọn tốt hơn về đơn giản và bảo trì.

## 9. Điều kiện bất thường vẫn nên là exception

Không dùng exception cho nhánh thường xuyên **không đồng nghĩa từ bỏ exceptions**. Hãy ném khi đó là cách phù hợp để diễn đạt contract:

- Tham số không hợp lệ so với public API: `IllegalArgumentException`, `NullPointerException` tại ranh giới (ví dụ `Objects.requireNonNull`).
- Trạng thái đối tượng không cho phép thao tác: `IllegalStateException`.
- Lỗi I/O không phải EOF bình thường: `IOException`.
- Vi phạm invariant, bug lập trình hoặc dependency thất bại mà tầng hiện tại không giải quyết được.
- Một thư viện đặt exception làm hợp đồng cho tình huống cụ thể; adapter có thể chuyển sang result type ở ranh giới phù hợp.

Một API được thiết kế theo quy tắc **exceptional failure** có thể lựa chọn:

~~~java
// Trích đoạn trong phương thức xác nhận đơn hàng.
if (orderAlreadyCommitted) {
    throw new IllegalStateException("order already committed");
}
~~~

Nếu trạng thái `orderAlreadyCommitted` là một lỗi gọi API hiếm, exception rất rõ nghĩa. Nhưng nếu hệ thống nhận lại cùng một message nhiều lần theo cơ chế at-least-once, “đã xử lý trước đó” có thể là một kết quả idempotent dự kiến, nên một enum outcome như `ALREADY_APPLIED` phù hợp hơn.

Vì vậy, hãy hỏi: *Caller có cần và có thể xử lý trường hợp này như một kết quả bình thường không? Nó xuất hiện thường xuyên đến mức nào?*

## 10. Exception làm control flow gây vấn đề cả về khả năng đọc

Ngoài CPU, cách dùng exception sai làm thay đổi **cấu trúc suy luận**:

~~~java
// Phản ví dụ minh họa; các hàm là giả định.
try {
    Item item = repository.findOrThrow(id);
    render(item);
} catch (NoSuchElementException ex) {
    renderEmptyState();
}
~~~

Đoạn này có thể hợp lý nếu `findOrThrow` có contract “bắt buộc tồn tại”. Nhưng nếu tìm không thấy là tình huống thường xuyên của một hộp tìm kiếm, ý định rõ hơn là:

~~~java
// Mã minh họa.
Optional<Item> item = repository.find(id);
if (item.isPresent()) {
    render(item.get());
} else {
    renderEmptyState();
}
~~~

Những nhược điểm của việc dùng exception trong đường thường xuyên:

1. Đọc code phải tìm handler có thể ở xa call site mới biết kết quả thông thường.
2. Catch sai tầng hoặc catch quá rộng có thể che bug thật.
3. Exception thường mang ý nghĩa “đã xảy ra lỗi”, dễ dẫn tới log và alert sai.
4. Kết quả không được biểu diễn rõ trong chữ ký trả về, làm caller bất ngờ.
5. Khó compose nhiều trạng thái expected failure nếu chỉ dùng một kiểu exception mơ hồ.

Ngược lại, result type quá nhiều nhánh có thể khiến code rườm rà nếu failure thực sự rất hiếm và không thể phục hồi ở tầng hiện tại. **Chọn cấu trúc biểu đạt ý định của API, không chạy theo một khẩu hiệu tối ưu.**

## 11. Chi phí stack trace: hiểu đúng, không tắt mù quáng

`Throwable` có khả năng lưu stack trace giúp người điều tra biết ngoại lệ phát sinh ở đâu. Điều này rất quan trọng với lỗi bất ngờ.

Một số cách “tối ưu” sai:

- Tạo một exception duy nhất rồi dùng lại cho mọi lỗi để tránh allocation. Stack trace có thể trở nên lỗi thời, thông tin context không đúng request và khó hiểu khi có nhiều luồng.
- Gọi `setStackTrace(new StackTraceElement[0])` cho mọi exception: mất dữ liệu điều tra trong khi chi phí tạo stack có thể đã phát sinh.
- Tạo custom exception loại bỏ stack trace cho tất cả lỗi để chạy nhanh hơn: có thể tiết kiệm nhưng đánh đổi observability, cần contract/thử nghiệm rất rõ.
- Dựa vào cơ chế *fast throw* của HotSpot như một bảo đảm hiệu năng chung. Chi tiết tối ưu có thể thay đổi theo JVM/version/cấu hình.
- Chỉ đo thời gian `throw` bằng một vòng lặp microbenchmark thiếu warmup và kết luận rằng kết quả áp dụng cho production.

Nếu thật sự có một nhánh thất bại lặp lại dày đặc và việc ghi stack trace **không có giá trị**, thiết kế nó thành normal result thường rõ ràng hơn thao tác tùy biến stack trace của exception.

## 12. Không dựa vào catch để xử lý lỗi API bị dùng sai liên tục

Lấy ví dụ liên tục gọi `List.get(index)` với chỉ số có thể không hợp lệ:

~~~java
// Không lý tưởng nếu index ngoài miền là tình huống thông thường.
try {
    consume(items.get(index));
} catch (IndexOutOfBoundsException ex) {
    useDefault();
}
~~~

Trong code xử lý UI khi chỉ số đến từ người dùng và out-of-range là tình huống dự kiến, có thể:

~~~java
if (index >= 0 && index < items.size()) {
    consume(items.get(index));
} else {
    useDefault();
}
~~~

Tuy nhiên, với collection bị truy cập đồng thời, kiểm tra `size()` trước rồi `get()` **không tự bảo đảm nguyên tử**: một thread khác có thể thay đổi danh sách giữa hai lời gọi. Cần tuân contract thread-safety của collection, dùng snapshot hoặc cơ chế đồng bộ phù hợp. Đừng đánh đổi exception control flow lấy một lỗi race mới.

Với external resource như file, đọc/ghi và mạng, phép kiểm tra trước đôi khi không loại được failure do trạng thái thay đổi giữa check và use. Khi lỗi bất thường có thể xảy ra, vẫn phải có đường xử lý exception phù hợp.

## 13. Logging: không biến một kết quả bình thường thành sự cố

Nếu hàng triệu dòng import có 5% không hợp lệ, ghi một stack trace ERROR cho từng dòng là một dạng phí tổn đáng kể và gây nhiễu.

Thay vì:

~~~java
// Phản ví dụ minh họa, log và parseInt do ứng dụng cung cấp.
try {
    int value = Integer.parseInt(input);
    save(value);
} catch (NumberFormatException ex) {
    log.error("invalid input", ex);
}
~~~

Cân nhắc một parser kết quả và **tổng hợp thống kê có giới hạn**:

~~~java
// Minh họa: parseUnsignedDecimal và metrics là các thành phần đã khai báo.
OptionalInt parsed = parseUnsignedDecimal(input);
if (parsed.isPresent()) {
    save(parsed.getAsInt());
} else {
    metrics.incrementRejectedCount();
}
~~~

Nếu cần trợ giúp người dùng sửa dữ liệu, có thể lưu error code và vị trí dòng cho một số lỗi phù hợp, áp dụng giới hạn kích thước, không log raw payload chứa dữ liệu nhạy cảm. Một batch có thể ghi `importId`, `accepted`, `rejected`, `errorCategories` thay vì một triệu stack traces.

**Ghi log exception khi nó giúp điều tra lỗi thật**; phân biệt ERROR, WARN, INFO và metrics theo mức độ ảnh hưởng. “Không throw” không tự khiến hệ thống quan sát tốt hơn nếu bạn nuốt hết nguyên nhân và không đo kết quả từ chối.

## 14. Benchmarks: cách kiểm chứng thay vì suy đoán

Để quyết định một nhánh exception có gây vấn đề hiệu năng không, cần đo các trường hợp gần với production. **Không tự tạo một kết luận tốc độ từ số lần chạy `System.nanoTime()` trong một loop đơn giản**: JIT warmup, dead-code elimination, inlining, allocation elimination, constant folding và GC có thể làm kết quả sai lệch.

### Chọn tỷ lệ dữ liệu hợp lệ/thất bại

Benchmark nên có:

- 100% hợp lệ.
- 99,9% hợp lệ (lỗi rất hiếm).
- 95% hợp lệ, 5% từ chối.
- 50% hợp lệ, 50% từ chối.
- Các input dài ngắn và phân bố lỗi thực tế (ký tự sai ở đầu/cuối, overflow).
- Trường hợp dữ liệu biến đổi, không toàn constant để JIT tối ưu quá mức.

Nếu branch bình thường hầu như không bị lỗi, `parseInt` chuẩn có thể rất hiệu quả và đơn giản; parser riêng có thể gây thêm overhead. Nếu dữ liệu sai rất thường xuyên, chi phí exception có thể chi phối throughput. **Mức cụ thể phải đo**.

### Công cụ: JMH

Java Microbenchmark Harness (JMH, dự án OpenJDK) cung cấp warmup, nhiều lượt đo, fork JVM, tránh một số lỗi benchmark phổ biến. Một **khung mã minh họa phụ thuộc JMH**:

~~~java
// Minh họa: cần dependency org.openjdk.jmh, không phải Java SE độc lập.
// @State(Scope.Thread)
// public class ParseBenchmark {
//     @Param({"0", "5", "50"})
//     public int invalidPercent;
//
//     @Benchmark
//     public int exceptionPath() {
//         // Dùng input thay đổi với đúng tỷ lệ invalidPercent,
//         // gọi parser thật, tiêu thụ toàn bộ kết quả.
//     }
//
//     @Benchmark
//     public int resultPath() {
//         // Dùng cùng input, cùng grammar và cùng chính sách xử lý,
//         // nhưng biểu diễn thất bại không qua exception.
//     }
// }
~~~

Khung trên là **sơ đồ thực nghiệm**, không phải một benchmark đầy đủ và không có đầu ra/điểm số được bịa đặt. Khi triển khai JMH thật cần đo kết quả được sử dụng để tránh compiler bỏ code, điều chỉnh dữ liệu để hai nhánh làm **công việc tương đương** và tránh tạo input trong vùng đang đo nếu điều đó không thuộc workload thật.

Các thông số nên quan sát thêm:

| Chỉ số | Ý nghĩa |
|---|---|
| Throughput (ops/s) | Có thể xử lý bao nhiêu phần tử mỗi giây |
| Latency trung bình và phân vị | Nhánh lỗi có kéo dài tail latency không |
| Allocation rate (B/op) | Có bao nhiêu byte cấp phát mỗi thao tác |
| GC count/time | Có thêm áp lực thu gom rác không |
| CPU profile | Thời gian tập trung ở parser, Throwable hay logging |
| Error ratio | Tần suất thực của trường hợp thất bại |
| Độ rõ API và khả năng điều tra | Không đổi correctness/observability lấy vài phần trăm giả định |

Đừng chỉ so sánh đường lỗi mà bỏ qua chi phí đường thành công. Đồng thời đừng thay đổi semantics parser giữa hai benchmark: nếu một bên nhận `+1` còn bên kia không, phép so sánh đã không tương đương.

## 15. Ngoại lệ hiếm và EAFP: khi nào bắt lỗi trực tiếp lại đúng?

Một số tình huống không thể tránh race giữa kiểm tra và thực hiện. Ví dụ kiểm tra file có tồn tại, sau đó mở file:

~~~java
// Trích đoạn minh họa; không loại được race.
if (java.nio.file.Files.exists(path)) {
    // File vẫn có thể bị xóa/đổi quyền trước khi mở.
    readFrom(path);
}
~~~

Ở đây, dù đã kiểm tra, việc mở/đọc vẫn có thể thất bại bởi `IOException`. Thiết kế đúng vẫn phải xử lý lỗi thao tác thật. Nếu file bắt buộc phải có và lỗi mở file là bất thường, dùng exception **đúng mục đích**.

**EAFP** (easier to ask forgiveness than permission, thuật ngữ phổ biến trong lập trình) không phải lý do để lạm dụng exception cho EOF hoặc mỗi phần tử loop. Nó hữu ích khi:

- Điều kiện không thể kiểm tra đáng tin trước khi thao tác.
- Failure hiếm hoặc có ý nghĩa bất thường.
- API chỉ cung cấp exception làm hợp đồng chính thức.
- Cần giữ cause, cleanup tài nguyên và chính sách retry.

Ngược lại, **LBYL** (look before you leap, kiểm tra trước) cũng không luôn nhanh hơn hoặc an toàn hơn nếu tạo bước kiểm tra trùng lặp, làm sai grammar hoặc mắc lỗi TOCTOU.

Kết luận của phần này: chọn dựa trên **semantics của operation, thread-safety và thực nghiệm**, chứ không cố thay mọi try/catch bằng if.

## 16. Không che bug bằng exception catch quá rộng

Một vòng lặp kiểu:

~~~java
while (true) {
    try {
        processNext();
    } catch (Exception ignored) {
        continue;
    }
}
~~~

có thể nuốt:

- Lỗi dữ liệu dự kiến.
- `NullPointerException` do bug.
- Lỗi kết nối tạm thời cần backoff.
- `InterruptedException` từ thao tác chặn, làm chương trình không tôn trọng tín hiệu hủy.
- Lỗi khiến trạng thái đối tượng không còn hợp lệ.

Cách đúng là xác định loại kết quả expected và loại failure khác nhau. Nếu worker cần chạy tiếp sau dữ liệu không hợp lệ, dùng một result type để ghi nhận rejected record. Nếu I/O thất bại, thực hiện retry/backoff có giới hạn hoặc chuyển lỗi lên tầng điều phối. Nếu bị ngắt, truyền `InterruptedException` khi có thể; nếu không, thường cần `Thread.currentThread().interrupt()` rồi rời vòng lặp.

Ví dụ dưới đây là **mã minh họa**, không phải chương trình độc lập:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    try {
        String raw = queue.take();
        OptionalInt parsed = parseUnsignedDecimal(raw);
        if (parsed.isEmpty()) {
            recordRejected();
            continue;
        }
        process(parsed.getAsInt());
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
    } catch (java.io.IOException ex) {
        handleInfrastructureFailure(ex);
        break;
    }
}
~~~

Để đoạn code hợp lệ trong dự án, `process` phải khai báo phù hợp `throws IOException`, cùng với `queue`, `recordRejected` và `handleInfrastructureFailure` được định nghĩa. Mục đích là **giữ riêng expected rejection, cancellation và infrastructure failure**.

## 17. Những lỗi thiết kế thường gặp

1. **Coi EOF là exception:** dùng `read() == -1` hoặc `readLine() == null` theo API; giữ `IOException` cho lỗi đọc thật.
2. **Dùng `IndexOutOfBoundsException` làm điều kiện kết thúc:** thay bằng iterator/`for`/`hasNext()`.
3. **Dùng `NoSuchElementException` cho “không tìm thấy” thường xuyên:** cân nhắc `Optional` hoặc result type; nhưng nếu contract bắt buộc tồn tại, exception vẫn hợp lý.
4. **Coi input invalid là bug:** với UI/import, đó thường là kết quả dự kiến; phân biệt với sai cấu hình/invariant.
5. **Bắt `RuntimeException` cho mọi input:** nuốt cả lỗi lập trình không liên quan.
6. **Tự viết validator lệch với parser:** không đồng nhất dấu `+`/`-`, Unicode, whitespace hoặc overflow.
7. **Prevalidate rồi parse lại mà không đo:** quét chuỗi hai lần có thể chậm hơn; còn gặp race hoặc loại input không đồng nhất.
8. **Dùng sentinel nằm trong miền giá trị hợp lệ:** caller không phân biệt failure với kết quả thật.
9. **Thay exception bằng null vô tội vạ:** dễ phát sinh `NullPointerException` tại nơi xa hơn.
10. **Dùng Optional mọi nơi vì “nhẹ”:** có thể làm API phức tạp và tốn tài nguyên, cần lý do semantics rõ.
11. **Đo tốc độ bằng một loop `nanoTime` duy nhất:** kết quả có thể phản ánh JIT/GC chứ không phải code thật.
12. **Lấy kết quả microbenchmark làm lý do đổi public API ngay:** phải xét compatibility và chi phí chuyển đổi cho caller.
13. **Tắt stack trace cho mọi exception:** bỏ mất dữ liệu chẩn đoán lỗi hiếm, nghiêm trọng.
14. **Tái sử dụng exception object chung xuyên các request:** context/stack có thể sai và khó debug.
15. **Log ERROR cho mỗi bản ghi bị từ chối:** tăng nhiễu, tăng I/O và cardinality, cần thống kê có giới hạn.
16. **Catch rồi bỏ interrupt:** làm worker khó dừng và che dấu chính sách cancellation.
17. **Dùng exception để rollback rồi coi như “bình thường”:** việc ném lỗi không tự hoàn tác trạng thái; cần thiết kế commit/transaction riêng.

## 18. Chiến lược thay đổi code hiện có an toàn

Khi gặp một hot path đang dùng exception để phân nhánh:

1. **Xác định contract hiện tại:** lỗi có thể là một trạng thái “bình thường” được caller kỳ vọng không? Những loại exception nào đang bị bắt?
2. **Đo tần suất:** tỷ lệ lỗi, số lần gọi, thời gian dành cho Throwable construction, logs, allocation và GC.
3. **Chọn kiểu kết quả:** boolean, `OptionalInt`, `Optional<T>`, enum/status code hay sealed result theo thông tin caller cần.
4. **Bảo toàn grammar và miền dữ liệu:** thử input null, rỗng, khoảng trắng, dấu, overflow, Unicode, dữ liệu cực dài. Không phá compatibility của parser.
5. **Chuyển đổi tại ranh giới phù hợp:** nếu public API hiện có contract ném exception, có thể giữ phương thức cũ và thêm `tryParse...` thay vì thay đổi im lặng mọi caller.
6. **Đảm bảo đường lỗi bất thường vẫn có cause:** khi dependency thật sự lỗi, không ép nó thành giá trị “không tìm thấy”.
7. **Tách kết quả dự kiến khỏi logging ERROR:** ghi metrics/rejection count và sample có giới hạn khi phù hợp.
8. **Đo sau thay đổi:** kiểm tra throughput/latency/allocation/GC trên dữ liệu đại diện và phiên bản JVM mục tiêu.
9. **Kiểm thử đường thành công và lỗi:** kiểm tra cả semantics lẫn khả năng quan sát lỗi hiếm.
10. **Chỉ giữ tối ưu khi đáng giá:** code đơn giản, đúng contract và dễ bảo trì quan trọng hơn một kết quả benchmark không đại diện.

## 19. Các bài kiểm thử nên có

| Trường hợp | Kỳ vọng |
|---|---|
| Dữ liệu hợp lệ | Parse chính xác, không bị bỏ qua |
| Chuỗi rỗng hoặc null | Trả trạng thái từ chối theo API, không dùng NPE ngoài ý muốn |
| Ký tự không thuộc grammar | Từ chối đúng, giữ vị trí/mã lỗi khi cần |
| Giá trị `Integer.MAX_VALUE` và hơn 1 | Chấp nhận giới hạn, từ chối overflow không vòng số |
| Số có dấu, leading zero, whitespace | Hành vi đúng grammar tài liệu hóa |
| Dữ liệu sai với tần suất cao | Không tạo exception/log ồ ạt trên nhánh expected |
| Lỗi I/O thực sự | Exception được giữ lại, không biến thành EOF |
| Không tìm thấy record | Kết quả expected, không che lỗi của dependency |
| Nhiều thread dùng chung cấu trúc | Kiểm tra và truy cập tuân thread-safety, tránh TOCTOU |
| Cancellation/interruption | Worker tôn trọng tín hiệu, không catch rộng rồi chạy mãi |
| Hiệu năng dưới tải tương tự production | Đo được throughput, latency và allocation với tỷ lệ lỗi xác định |
| Tương thích API | Caller cũ vẫn nhận kết quả/lỗi đúng hợp đồng sau refactor |

Đặc biệt, **kiểm thử correctness phải đi trước benchmark**. Một parser nhanh nhưng chấp nhận số bị overflow hoặc từ chối dữ liệu hợp lệ không phải là một tối ưu thành công.

## 20. Tổng kết

**Không dùng ngoại lệ cho nhánh điều khiển thường xuyên** nghĩa là không dùng `throw/catch` như một cách diễn đạt thay cho điều kiện bình thường mà thuật toán có thể biểu diễn trực tiếp. Với EOF, vòng lặp, tra cứu không có kết quả và dữ liệu người dùng bị từ chối thường xuyên, một điều kiện hoặc result type rõ nghĩa thường dễ đọc, dễ quan sát và có tiềm năng hiệu năng tốt hơn việc liên tục tạo exception.

Điều đó **không có nghĩa phải tránh exception bằng mọi giá**. Lỗi hạ tầng, vi phạm hợp đồng hoặc trạng thái bất thường vẫn cần đường exception đúng ngữ nghĩa, với `cause` và cleanup thích hợp. Đừng hy sinh grammar, invariant, thread-safety hoặc khả năng điều tra chỉ để giảm một số lần `throw` chưa được đo.

Công thức ra quyết định thực tế là: **xác định điều kiện expected → chọn kênh kết quả phù hợp → giữ exception cho failure thật → đo hot path bằng benchmark đáng tin → xác minh lại correctness và observability**.

### Tài liệu tham khảo

- [Java Language Specification 11 — Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html)
- [Java SE 17 — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- [Java SE 17 — NumberFormatException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NumberFormatException.html)
- [Java SE 17 — Integer.parseInt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String))
- [Java SE 17 — OptionalInt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalInt.html)
- [Java SE 17 — InputStream.read](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#read())
- [Java SE 17 — BufferedReader.readLine](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#readLine())
- [OpenJDK — Java Microbenchmark Harness (JMH)](https://openjdk.org/projects/code-tools/jmh/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-004">Không tạo message tốn kém khi chưa cần phát sinh lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Không tạo message tốn kém khi chưa cần phát sinh lỗi

Khi một ứng dụng Java kiểm tra dữ liệu hàng triệu lần, phần lớn lời gọi thường thành công. Nếu chương trình tạo trước thông điệp chẩn đoán dài rồi mới xác định có lỗi hay không, những lời gọi **hợp lệ** vẫn phải trả chi phí định dạng chuỗi, chuyển đối tượng thành text, đọc metadata hoặc cấp phát bộ nhớ. Giải pháp là giữ nguyên phép kiểm tra bắt buộc nhưng **trì hoãn tạo message cho đến khi nhánh lỗi thực sự xảy ra**.

Đây là bài về **Hiệu năng** trong **Java Core – Exception**, áp dụng cho Java 17+. Nó giải thích việc đánh giá đối số, sử dụng `if` đúng cách, `Objects.requireNonNull` với `Supplier<String>`, `Optional.orElseThrow`, logging có điều kiện, cách bảo toàn chất lượng chẩn đoán và cách đo hiệu năng đúng.

## 1. Chi phí xuất hiện ở đâu?

Xem lời gọi sau:

```java
Objects.requireNonNull(account,
        "account không hợp lệ: " + expensiveDiagnostic());
```

Trong Java, **các biểu thức đối số được đánh giá trước khi phương thức được gọi**. Vì thế `expensiveDiagnostic()` có thể chạy cả khi `account` khác `null` và `Objects.requireNonNull` trả về thành công. Phương thức nhận một `String` đã được chuẩn bị, nên không có cách tự bỏ qua công đoạn xây chuỗi đó.

Các công việc cần tách rõ:

| Công việc | Tác dụng | Có nên làm ở happy path? |
|---|---|---|
| Kiểm tra điều kiện | Xác định đầu vào hợp lệ hay không | **Có**, nếu hợp đồng yêu cầu |
| Tạo thông điệp lỗi | Giải thích điều kiện đã thất bại | **Không**, nếu chưa phát sinh lỗi |
| Khởi tạo Throwable | Tạo đối tượng ngoại lệ và thông tin stack trace thường đi kèm | **Không**, nếu chưa phát sinh lỗi |
| Ghi log | Gửi dữ liệu tới hệ thống chẩn đoán | Theo mức log, chính sách và kết quả thực |

Phép so sánh `amount < 0` thường rẻ. Một message gọi JSON serializer, duyệt hàng nghìn item hoặc truy cập metadata động có thể đắt hơn rất nhiều. Chương trình cần trả giá tạo message trên **đường lỗi**, nhưng không nên trả cùng khoản chi đó trên **đường thành công**.

Luồng chuẩn:

```text
Nhận giá trị
    |
    v
Kiểm tra điều kiện
    | đúng                      | sai
    v                           v
Tiếp tục, không tạo message    Xây message cần thiết
                                |
                                v
                         Tạo/throw exception
```

**Đừng bỏ validation để tối ưu.** Việc không kiểm tra một giá trị có thể làm ứng dụng nhận dữ liệu sai. Chúng ta chỉ di chuyển công việc phục vụ **chẩn đoán lỗi** vào nơi thật sự cần.

## 2. Tạo message bên trong nhánh `if`

Đây là mẫu rất rõ ràng:

```java
if (quantity < 0) {
    throw new IllegalArgumentException(
            "quantity=" + quantity + ", state=" + describeState());
}
```

Đoạn mã trên là **mảnh minh họa**; `quantity` và `describeState()` phải tồn tại trong phương thức. Nếu `quantity >= 0`, `describeState()` hoàn toàn không được gọi. Điều này đúng theo **ngữ nghĩa điều khiển luồng Java**, không cần đặt cược vào tối ưu hóa JIT.

So sánh mẫu tạo sớm:

```java
String message = "quantity=" + quantity
        + ", state=" + describeState();

if (quantity < 0) {
    throw new IllegalArgumentException(message);
}
```

Ở đây message được tạo ngay cả khi `quantity` hợp lệ. Nếu validation chạy trong một vòng lặp hoặc API tần suất cao, chi phí không cần thiết có thể tích lũy.

### Chương trình độc lập: đo số lần tạo message

Tệp `LazyErrorMessageDemo.java`:

```java
public class LazyErrorMessageDemo {
    private static int builds;

    static String describe(int amount) {
        builds++;
        return "amount âm: " + amount;
    }

    static void validate(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(describe(amount));
        }
    }

    public static void main(String[] args) {
        validate(10);
        System.out.println("valid builds = " + builds);

        try {
            validate(-3);
        } catch (IllegalArgumentException error) {
            System.out.println("error = " + error.getMessage());
        }

        System.out.println("all builds = " + builds);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac LazyErrorMessageDemo.java
java LazyErrorMessageDemo
```

Kết quả:

```text
valid builds = 0
error = amount âm: -3
all builds = 1
```

`validate(10)` vẫn kiểm tra điều kiện nhưng **không** gọi `describe`. Khi `validate(-3)` thất bại, chương trình mới tạo message rồi ném `IllegalArgumentException`.

Bộ đếm trong ví dụ chỉ giúp kiểm tra **thời điểm đánh giá**, không phải một benchmark. Nó chạy trong luồng `main`; không nên đưa biến đếm toàn cục thiếu đồng bộ vào hệ thống nhiều luồng để đo hiệu năng production.

## 3. `Objects.requireNonNull` và `Supplier<String>`

Java có hai overload đáng chú ý:

```java
static <T> T requireNonNull(T obj, String message)

static <T> T requireNonNull(
        T obj, java.util.function.Supplier<String> messageSupplier)
```

Overload đầu tiên nhận `String` đã được xây trước. Overload thứ hai nhận một **hàm sản sinh message**; nếu `obj != null`, `Supplier.get()` **không được gọi**. Nếu `obj == null`, phương thức gọi supplier để lấy message và ném `NullPointerException`.

Ví dụ:

```java
Objects.requireNonNull(user,
        () -> "Không có user, request=" + expensiveContext());
```

Đây là mảnh minh họa. `expensiveContext()` chỉ chạy khi `user` thực sự là `null`. Nếu message đã là hằng chuỗi như `"user không được null"`, overload `String` đơn giản và thường đủ tốt; không cần biến mọi hằng thành lambda.

### Chương trình độc lập: eager và lazy

Tệp `RequireNonNullMessageDemo.java`:

```java
import java.util.Objects;

public class RequireNonNullMessageDemo {
    private static int builds;

    static String message() {
        builds++;
        return "user không được null";
    }

    public static void main(String[] args) {
        String value = "Lan";

        Objects.requireNonNull(value, message());
        System.out.println("eager = " + builds);

        builds = 0;
        Objects.requireNonNull(value, () -> message());
        System.out.println("lazy valid = " + builds);

        try {
            Objects.requireNonNull(null, () -> message());
        } catch (NullPointerException error) {
            System.out.println("lazy error = " + error.getMessage());
        }
        System.out.println("lazy invalid = " + builds);
    }
}
```

Chạy:

```bash
javac RequireNonNullMessageDemo.java
java RequireNonNullMessageDemo
```

Kết quả:

```text
eager = 1
lazy valid = 0
lazy error = user không được null
lazy invalid = 1
```

Ở lời gọi eager, `message()` chạy trước `requireNonNull` dù `value` hợp lệ. Ở lời gọi lazy, lambda không thực thi cho `value` khác `null`. Với `null`, supplier chạy đúng một lần và message được gắn vào `NullPointerException`.

Một vài giới hạn quan trọng:

- `requireNonNull` dùng **`NullPointerException`**; đừng đổi sang API này nếu hợp đồng của phương thức yêu cầu `IllegalArgumentException` hoặc một exception nghiệp vụ khác.
- `Supplier.get()` có thể ném ngoại lệ. Nếu việc tạo message bị lỗi, caller có thể quan sát **lỗi từ supplier**, che mất lỗi null ban đầu.
- Supplier trả `null` sẽ tạo `NullPointerException` với message null. Không mặc định API sẽ tự tạo message hữu ích.
- Lambda có thể capture biến và đối tượng; việc tạo/capture lambda không phải lúc nào cũng có chi phí bằng không. Lợi ích rõ nhất là **không chạy tác vụ chẩn đoán tốn kém** trên đường hợp lệ.
- Tránh để supplier thực hiện I/O hoặc truy vấn mạng. Khi hệ thống đã gặp lỗi, việc tạo message phải càng đáng tin và nhanh càng tốt.

## 4. `Optional.orElseThrow`: trì hoãn cả việc tạo exception

Đôi khi không chỉ message mà **cả đối tượng ngoại lệ** cũng không cần tồn tại trên happy path. `Optional.orElseThrow(Supplier<? extends X>)` nhận một supplier **tạo exception**, chỉ gọi nó khi `Optional` rỗng.

```java
String name = maybeName.orElseThrow(
        () -> new IllegalStateException(
                "Không tìm thấy tên cho id=" + id));
```

Đây là mảnh minh họa: `maybeName` là `Optional<String>`, `id` đã khai báo. Nếu Optional có giá trị, Java trả giá trị đó, không tạo `IllegalStateException`.

### Chương trình độc lập

Tệp `OptionalThrowLazyDemo.java`:

```java
import java.util.Optional;

public class OptionalThrowLazyDemo {
    private static int exceptionsBuilt;

    static IllegalStateException missing() {
        exceptionsBuilt++;
        return new IllegalStateException("không tìm thấy hồ sơ");
    }

    public static void main(String[] args) {
        String value = Optional.of("OK")
                .orElseThrow(() -> missing());

        System.out.println("value = " + value);
        System.out.println("after present = " + exceptionsBuilt);

        try {
            Optional.<String>empty().orElseThrow(() -> missing());
        } catch (IllegalStateException error) {
            System.out.println("missing = " + error.getMessage());
        }

        System.out.println("after empty = " + exceptionsBuilt);
    }
}
```

Chạy:

```bash
javac OptionalThrowLazyDemo.java
java OptionalThrowLazyDemo
```

Đầu ra:

```text
value = OK
after present = 0
missing = không tìm thấy hồ sơ
after empty = 1
```

`Optional.orElseThrow()` **không có đối số** cũng tồn tại và ném `NoSuchElementException` khi rỗng. Dùng supplier khi muốn loại exception và message tùy biến; chọn loại lỗi phù hợp hợp đồng.

Đừng nhầm với:

```java
String value = optional.orElse(expensiveFallback());
```

`orElse` nhận **giá trị** đã được đánh giá, vì thế `expensiveFallback()` vẫn chạy khi Optional có giá trị. Nếu muốn fallback lazy, dùng:

```java
String value = optional.orElseGet(() -> expensiveFallback());
```

Hai mảnh cuối là ví dụ minh họa với `optional` kiểu `Optional<String>` và hàm `expensiveFallback()` trả `String`. `orElseGet` áp dụng cho **giá trị dự phòng**, còn `orElseThrow` áp dụng khi **thiếu giá trị là lỗi**. Điểm chung là supplier chỉ được dùng lúc cần.

## 5. `IllegalArgumentException` không có constructor nhận Supplier

Trong Java 17, constructor thông dụng là:

```java
new IllegalArgumentException(String message)
new IllegalArgumentException(String message, Throwable cause)
```

`IllegalArgumentException` không có constructor chuẩn `IllegalArgumentException(Supplier<String>)`. Việc viết:

```java
throw new IllegalArgumentException(() -> expensiveMessage());
```

là **không hợp lệ**. Nếu cần loại exception này, chỉ việc đặt phép tạo message vào nhánh sai:

```java
if (count <= 0) {
    throw new IllegalArgumentException(
            expensiveMessageFor(count));
}
```

Đây là mảnh mã minh họa. Không cần tạo lớp exception tùy chỉnh chỉ để trì hoãn tính chuỗi. Một câu `if` có thể vừa nhanh vừa dễ bảo trì.

Nếu dự án có nhiều kiểm tra cùng một hợp đồng, có thể viết tiện ích nhận supplier:

```java
static void requirePositive(
        int count, java.util.function.Supplier<String> message) {
    if (count <= 0) {
        throw new IllegalArgumentException(message.get());
    }
}
```

Đây là **phương thức hợp lệ có thể đặt trong class**. Cách sử dụng:

```java
requirePositive(itemsCount,
        () -> "Số phần tử không hợp lệ: " + itemsCount);
```

Đoạn dùng `itemsCount` là mảnh minh họa. Tiện ích chỉ có ích khi làm rõ một quy tắc dùng lại; tránh thêm abstraction phức tạp nếu chỉ có một chỗ gọi.

Đừng bỏ qua tính an toàn của supplier: nó có thể là `null`, trả `null`, hoặc ném lỗi. Cũng cần phân biệt việc trì hoãn tạo **message** với việc trì hoãn **validation**: phải đánh giá điều kiện đúng thời điểm, trên dữ liệu đúng trạng thái.

## 6. Chuỗi dài, `String.format` và `formatted(...)`

Ba cách tạo thông điệp phổ biến:

```java
"amount=" + amount

String.format("amount=%d", amount)

"amount=%d".formatted(amount)
```

`String.formatted` có từ Java 15, dùng được trên Java 17. Tất cả đều tạo chuỗi khi **biểu thức được đánh giá**. Nếu nằm bên trong nhánh lỗi, chúng không tạo message trên đường thành công:

```java
if (actual < min || actual > max) {
    throw new IllegalArgumentException(
            "actual=%d ngoài khoảng [%d, %d]"
                    .formatted(actual, min, max));
}
```

Đây là mảnh mã minh họa trong phương thức đã có `actual`, `min`, `max`.

Với chi phí nhỏ như nối hai số nguyên, **độ rõ ràng** thường quan trọng hơn tối ưu vi mô. `String.format` có chi phí định dạng và xử lý mẫu riêng; nhưng việc tranh luận `"x=" + x` so với `.formatted(x)` ít ý nghĩa khi lỗi chỉ xảy ra một lần mỗi triệu request.

Chi phí lớn hơn thường đến từ:

| Nguồn tạo message | Vì sao có thể đắt? | Lựa chọn |
|---|---|---|
| Duyệt collection lớn | Xây chuỗi dài, nhiều allocation | Chỉ lấy count hoặc vài ID an toàn |
| JSON serialization | Duyệt object graph, format | Không serialize toàn bộ trong exception |
| `toString()` của entity | Có thể duyệt quan hệ sâu hoặc lazy-load | In trường cần thiết, ổn định |
| Đọc stack/context | Thu thập cấu trúc lớn | Chỉ làm khi lỗi và khi cần |
| Lookup từ DB/network | I/O có thể treo hoặc lỗi thêm | Tránh dùng trong message |
| Format ngày/locale phức tạp | Chi phí parser/formatter | Dùng formatter phù hợp, trong nhánh lỗi |

Trong Java hiện đại, phép nối chuỗi bằng `+` được JVM tối ưu tốt trong nhiều tình huống; thay toàn bộ bằng `StringBuilder` thủ công hiếm khi là bước đầu tiên có giá trị. Hãy **loại bỏ công việc chạy không cần thiết** trước, rồi profile đường nóng để quyết định tối ưu biểu thức cụ thể.

## 7. Ví dụ xấu: serialize dữ liệu thành message trước khi kiểm tra

Giả sử chương trình phải xác minh số lượng đơn hàng. Cách dưới đây có thể làm việc vô ích:

```java
String fullDump = orders.stream()
        .map(Object::toString)
        .collect(java.util.stream.Collectors.joining(", "));

if (quantity < 0) {
    throw new IllegalArgumentException(
            "quantity sai, orders=" + fullDump);
}
```

Đây là **mảnh mã minh họa** với `orders` và `quantity` đã có trong phạm vi. Dù `quantity >= 0`, toàn bộ `orders` vẫn bị duyệt và chuyển thành text. Một message khổng lồ còn có thể gây chi phí log ingestion và rò rỉ dữ liệu.

Trong nhiều tình huống, chỉ cần:

```java
if (quantity < 0) {
    throw new IllegalArgumentException(
            "quantity âm: " + quantity
            + ", orderCount=" + orders.size());
}
```

Nếu cần đầy đủ một số phần tử để chẩn đoán, hãy giới hạn số item, bảo vệ dữ liệu nhạy cảm và chỉ thực hiện trong nhánh lỗi.

Một message có chất lượng tốt thường nêu **ràng buộc bị vi phạm, tên trường và một vài giá trị an toàn**, thay vì dump trạng thái toàn bộ ứng dụng.

## 8. Logging có điều kiện và message exception là hai cơ chế khác

Lazy logging cũng liên quan đến chủ đề hiệu năng, nhưng khác với việc tạo exception message.

Ví dụ framework logger kiểu SLF4J:

```java
logger.debug("Request {} đã hoàn thành", requestId);
```

Đây là **mảnh mã tích hợp**, không chạy độc lập nếu chưa thêm thư viện/logger. Placeholder thường giúp logger trì hoãn định dạng chuỗi khi mức log không bật. Nhưng ngữ nghĩa Java vẫn áp dụng: biểu thức làm **đối số** được đánh giá trước khi lời gọi diễn ra.

Ví dụ:

```java
logger.debug("Payload: {}", expensiveSerialize(payload));
```

Ngay cả khi log `DEBUG` bị tắt, `expensiveSerialize(payload)` vẫn được chạy để tạo đối số. Guard nếu logging API cho phép:

```java
if (logger.isDebugEnabled()) {
    logger.debug("Payload: {}", expensiveSerialize(payload));
}
```

Mã trên là ví dụ tích hợp, phụ thuộc logger và các biến đã định nghĩa. Một số API logging hiện đại có hỗ trợ supplier/lazy argument, nhưng cần tra hợp đồng **đúng phiên bản**, không giả định mọi framework hiểu Supplier.

Với **validation**, không được viết:

```java
if (logger.isDebugEnabled() && invalid) {
    throw new IllegalArgumentException("đầu vào sai");
}
```

Vì khi DEBUG tắt, điều kiện `invalid` có thể không được kiểm tra và lỗi bị bỏ qua. **Tính đúng của ứng dụng không phụ thuộc cấu hình logging.**

## 9. Message supplier phải an toàn ngay khi hệ thống đang lỗi

Một message được dựng *lazily* có thể tiết kiệm happy path nhưng vẫn gây rắc rối nghiêm trọng nếu dùng I/O hoặc thay đổi trạng thái.

Ví dụ không nên:

```java
if (user == null) {
    String accountName = remoteApi.lookupAccount();
    throw new IllegalArgumentException(
            "Không thấy user thuộc account " + accountName);
}
```

Đây là **mảnh minh họa**. Nếu `remoteApi.lookupAccount()` timeout hoặc ném ngoại lệ mới, caller có thể chỉ thấy lỗi mạng, còn lỗi null ban đầu bị che mất. Nếu hàm lookup gây side effect, chỉ việc tạo message có thể làm thay đổi dữ liệu.

Lựa chọn ổn định hơn:

```java
if (user == null) {
    throw new IllegalArgumentException(
            "user bắt buộc, requestId=" + requestId);
}
```

Cũng là mảnh minh họa, với `requestId` được chuẩn bị an toàn. Nếu bắt buộc thu thập thông tin thêm, hãy dùng hệ thống telemetry ở boundary có ngân sách thời gian và quy tắc an toàn rõ ràng.

Một số lưu ý thực tế:

- Không gọi DB hoặc HTTP chỉ để tạo chuỗi lỗi.
- Không gọi `toString()` trên object có lazy-loading ngoài dự kiến.
- Giới hạn độ dài message và bỏ dữ liệu bí mật (password, token, key, PII).
- Không dùng `Supplier.get()` có thể ném checked/unchecked exception mà không có chính sách.
- Nếu phải bọc ngoại lệ ở tầng cao, **giữ cause** bằng constructor `new IllegalStateException("...", original)` khi phù hợp.

Trì hoãn message giúp tránh công vô ích; nó không phải giấy phép biến message thành một thao tác tốn kém hoặc không đáng tin cậy.

## 10. Không tạo trước đối tượng Throwable

Ngoài message, việc tạo exception thường liên quan điền stack trace. Vì thế không nên khởi tạo một Throwable sẵn trước khi biết có lỗi:

```java
IllegalStateException cachedError =
        new IllegalStateException("không hợp lệ");

// Mãi sau mới kiểm tra:
if (invalid) {
    throw cachedError;
}
```

Đây là mảnh minh họa **không khuyến nghị**. Exception được tạo ngay cả khi `invalid` là `false`. Nếu tái sử dụng cùng exception nhiều lần, stack trace còn có thể phản ánh nơi tạo ban đầu chứ không phải vị trí phát sinh lỗi, làm việc điều tra khó hơn.

Thay bằng:

```java
if (invalid) {
    throw new IllegalStateException("không hợp lệ");
}
```

Tạo và ném exception khi điều kiện thật sự vi phạm. Tương tự, không nên dùng exception như một giá trị điều khiển thông thường trong vòng lặp nóng (ví dụ ném exception cho một kết quả dự kiến như “không tìm thấy trong cache” mỗi lần). Khi lỗi là trạng thái bình thường có tần suất cao, cân nhắc API trả `Optional`, mã trạng thái hoặc kiểu kết quả phù hợp.

Cần thận trọng với các kỹ thuật **vô hiệu hóa stack trace** để tăng tốc ngoại lệ: chúng có thể phá khả năng chẩn đoán, đặc biệt ở production. Đầu tiên hãy tối ưu **khi nào tạo lỗi** và **khi nào tạo message**, rồi mới benchmark trade-off đặc biệt nếu thực sự cần.

## 11. Ví dụ độc lập: thứ tự đánh giá không phụ thuộc tối ưu JIT

Tệp `MessageEvaluationOrderDemo.java`:

```java
import java.util.Objects;

public class MessageEvaluationOrderDemo {
    private static int calls;

    static String diagnostics() {
        calls++;
        return "diagnostic #" + calls;
    }

    static void eager(String value) {
        Objects.requireNonNull(value, diagnostics());
    }

    static void lazy(String value) {
        Objects.requireNonNull(value, () -> diagnostics());
    }

    public static void main(String[] args) {
        eager("OK");
        lazy("OK");
        System.out.println("after valid = " + calls);

        try {
            lazy(null);
        } catch (NullPointerException error) {
            System.out.println("caught = " + error.getMessage());
        }

        System.out.println("all calls = " + calls);
    }
}
```

Chạy:

```bash
javac MessageEvaluationOrderDemo.java
java MessageEvaluationOrderDemo
```

Kết quả chính xác:

```text
after valid = 1
caught = diagnostic #2
all calls = 2
```

Thân `diagnostics()` tăng bộ đếm, nên JIT không thể giả vờ không có side effect của lời gọi eager. Ví dụ làm rõ ngữ nghĩa chứ không phải cho biết lazy nhanh hơn bao nhiêu nanosecond.

Không nên dùng một bộ đếm không thread-safe như ví dụ này trong nhiều worker; nó chỉ để xác định trình tự đơn luồng.

## 12. Benchmark đúng: lazy không đồng nghĩa luôn nhanh hơn

Lợi ích của lazy message phụ thuộc **chi phí dựng message** và **tần suất lỗi**. Với một hằng chuỗi:

```java
Objects.requireNonNull(value, "value không được null");
```

không có công việc định dạng đắt đỏ cần tránh. Lambda có thể không đem lại ích lợi đo được, thậm chí khiến code rườm rà.

Một phép đo nghiêm túc nên tách các trường hợp sau:

| Kịch bản | Điều cần đo |
|---|---|
| Input hợp lệ, message eager đắt | Chi phí lãng phí trên happy path |
| Input hợp lệ, message lazy | Chi phí điều kiện và cấu trúc lambda |
| Input lỗi, message eager | Chi phí format và tạo exception |
| Input lỗi, message lazy | Chi phí gọi supplier, format và tạo exception |

Khi cần số liệu tin cậy, ưu tiên công cụ như **JMH** với warmup, nhiều fork, dữ liệu không hằng, kết quả được tiêu thụ đúng cách và tỷ lệ lỗi thực tế. Đừng lấy vài lần `System.nanoTime()` rồi kết luận chắc chắn về nanosecond/op vì JIT, escape analysis, GC, hệ điều hành và dead-code elimination đều có thể làm sai lệch phép đo.

Quan sát cả **allocation rate và GC**, không chỉ thời gian. Một message vô ích trong đường nóng có thể làm tăng áp lực bộ nhớ ngay cả khi độ trễ đơn lẻ khó phát hiện. Tuy nhiên, nếu profiling cho thấy chi phí thấp hơn việc duy trì abstraction mới, hãy ưu tiên code dễ đọc.

Nếu phần lớn giao dịch **đều lỗi**, tạo message lazy có thể không giúp được bao nhiêu. Cần sửa nguyên nhân lỗi hoặc thiết kế kết quả thông thường sao cho không lạm dụng exception.

## 13. Nguy cơ đọc dữ liệu trễ trong môi trường đa luồng

Một supplier ghi nhận đối tượng và **đọc trạng thái khi được gọi**, không nhất thiết khi lambda được tạo:

```java
Objects.requireNonNull(user,
        () -> "activeTasks=" + sharedTasks.size());
```

Đây là mảnh minh họa. Nếu `sharedTasks` thay đổi đồng thời, giá trị quan sát được phụ thuộc thời điểm `get()` chạy. Với overload `Objects.requireNonNull`, supplier được gọi ngay trong lời gọi khi giá trị null, nên khoảng thời gian thường nhỏ; nhưng các utility khác có thể **giữ supplier và gọi sau**, làm khác biệt thời gian đáng kể.

Nếu message cần snapshot nhất quán, có thể lưu các giá trị an toàn tại điểm kiểm tra hoặc dùng cơ chế synchronization hợp đồng yêu cầu. Đừng trì hoãn một phép kiểm tra bắt buộc đến khi dữ liệu đã đổi trạng thái. **Trì hoãn định dạng chẩn đoán**, không trì hoãn điều kiện bảo vệ tính đúng.

Đồng thời cân nhắc lifetime của lambda capture: supplier được giữ trong object sống lâu có thể kéo dài vòng đời của cả object graph được capture. Đừng dùng Supplier tùy tiện như cache message cho mọi request.

## 14. Những sai lầm thường gặp

### Dựng message trước khi kiểm tra

Sai:

```java
String details = expensiveDescribe(input);
if (invalid) {
    throw new IllegalArgumentException(details);
}
```

Đúng khi `details` chỉ phục vụ lỗi:

```java
if (invalid) {
    throw new IllegalArgumentException(expensiveDescribe(input));
}
```

Đây là các mảnh minh họa với biến/hàm do ứng dụng định nghĩa.

### Tưởng `Objects.requireNonNull(value, expensive())` là lazy

Không phải. Nó chọn overload nhận **String**, nên `expensive()` đã chạy trước lời gọi. Cần truyền supplier vào overload tương ứng:

```java
Objects.requireNonNull(value, () -> expensive());
```

### Nghĩ message luôn là chi phí lớn nhất

Khởi tạo Throwable thường liên quan stack trace; việc ném nhiều exception cho luồng bình thường có thể đắt hơn tối ưu biểu thức message. Tập trung vào khả năng thiết kế lại hot path trước khi tự viết formatter phức tạp.

### Dùng lazy supplier có truy vấn ngoài

Supplier chỉ chạy khi lỗi, nhưng đây là thời điểm không nên mở thêm kết nối hoặc gây lỗi mới. Giữ message đơn giản, dựa trên dữ liệu đã có.

### Chuyển mọi validation sang `Objects.requireNonNull`

Nó chỉ kiểm tra `null` và ném `NullPointerException`. Không dùng để thay điều kiện nghiệp vụ cần `IllegalArgumentException`, checked exception hoặc exception riêng.

### Dùng logger placeholder rồi vẫn serialize đối số

```java
logger.debug("Object={}", serialize(largeObject));
```

Java đánh giá `serialize` trước khi vào logger. Nếu không muốn chạy khi DEBUG tắt, dùng guard hoặc API lazy argument đúng chuẩn thư viện.

### Dùng message `toString()` vô hạn

Một object graph lớn hoặc có vòng tham chiếu có thể tạo chuỗi khổng lồ, lỗi hoặc lộ dữ liệu. Chỉ hiển thị thông tin có giá trị, kích thước giới hạn.

### Tắt stack trace để làm đẹp benchmark

Đây là đánh đổi lớn về chẩn đoán. Không tối ưu trước khi có số liệu thực tế và chính sách rõ về điều gì cần lưu trong lỗi.

### Nhầm kiểm tra với tạo message

Điều kiện bảo vệ dữ liệu vẫn phải chạy đúng lúc. Không trì hoãn validation với lập luận “thường không lỗi”; chỉ trì hoãn **công việc không cần trong trạng thái hợp lệ**.

## 15. Mẫu quyết định nhanh

| Nhu cầu | Cách dùng phù hợp |
|---|---|
| Kiểm tra một điều kiện và ném lỗi tùy chỉnh | `if (invalid) throw new ...(...)` |
| `Objects.requireNonNull` với message đắt | `Objects.requireNonNull(value, () -> message())` |
| `Optional` thiếu dữ liệu thì ném exception | `optional.orElseThrow(() -> new ...(...))` |
| `Optional` cần giá trị fallback đắt | `optional.orElseGet(() -> compute())` |
| Format nhiều trường chỉ khi điều kiện sai | Đặt format trong nhánh lỗi |
| Logging có đối số tính toán đắt khi level tắt | Guard hoặc supplier được logger hỗ trợ |
| Có lỗi dự kiến xảy ra quá thường xuyên | Xem lại API/luồng điều khiển, không lạm dụng exception |
| Muốn chứng minh nhanh hơn | Profile hoặc JMH với tỷ lệ success/failure đúng thực tế |

Để review một đoạn code, hãy hỏi: “Nếu dữ liệu **hợp lệ**, có hàm nào chỉ phục vụ lỗi vẫn được gọi không?” Nếu câu trả lời là có, dời công việc ấy vào nhánh lỗi hoặc dùng overload nhận supplier. Sau đó xác nhận loại exception và message khi **thật sự lỗi** vẫn đúng.

## 16. Checklist áp dụng trong production

- Kiểm tra input vẫn được thực hiện đúng điều kiện và thời điểm.
- Không xây `String.format`, `.formatted`, JSON, `toString()` đắt đỏ trên đường hợp lệ.
- Dùng overload `Supplier<String>` của `Objects.requireNonNull` **đúng chỗ**, không nhầm với overload `String`.
- Dùng `Optional.orElseThrow` khi thiếu dữ liệu là lỗi; đừng tạo exception trước khi biết Optional rỗng.
- Message an toàn, không có side effect nghiệp vụ, không đòi gọi mạng/DB và không tiết lộ secret.
- Loại exception, message, cause và hành vi caller khi lỗi được bảo toàn sau tối ưu.
- Không cache hoặc tái sử dụng Throwable sai ngữ cảnh.
- Log message không bị đánh giá sớm bởi đối số Java khi level tắt.
- Benchmark/monitor đủ tốt trước khi áp dụng các tối ưu phức tạp hơn.

**Ghi nhớ:** Java đánh giá đối số phương thức trước khi gọi. Vì vậy **đừng tạo thông điệp lỗi tốn kém khi đường kiểm tra còn có thể thành công**. Đặt công việc dựng message trong nhánh lỗi hoặc dùng API `Supplier` thật; giữ đầy đủ thông tin chẩn đoán khi có lỗi, và chỉ tối ưu sâu sau khi có số liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-005">Stack trace bị lược bỏ và tối ưu phụ thuộc JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Stack trace bị lược bỏ và tối ưu phụ thuộc JVM trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **stack trace được rút gọn khi hiển thị** với **stack trace thật sự trống hoặc không được ghi nhận**.
- Giải thích ý nghĩa dòng `... N more` trong `Throwable.printStackTrace()` khi có cause hoặc suppressed exception.
- Hiểu cơ chế chi phí của việc tạo, ghi nhận và hiển thị stack trace, đặc biệt ở đường lỗi xuất hiện thường xuyên.
- Nhận diện tối ưu thường gọi là **FastThrow** trong HotSpot, liên quan tùy chọn `-XX:+OmitStackTraceInFastThrow`, và hiểu vì sao không nên xem hành vi này là bảo đảm chung cho mọi JVM.
- Phân biệt tối ưu nội bộ JVM với việc **chủ động tắt writable stack trace** trong một lớp `Throwable` tùy biến.
- Chẩn đoán trường hợp log chỉ có tên lỗi hoặc stack trace thiếu frame: kiểm tra API `getStackTrace()`, cách log, cấu hình JVM và điều kiện tái hiện.
- Cân bằng hiệu năng và khả năng quan sát: không dùng exception cho luồng điều khiển thường xuyên, không tự ý xóa trace của lỗi quan trọng.
- Viết và chạy các ví dụ Java 17+ có kết quả xác định mà không dựa vào việc JIT có kích hoạt FastThrow hay không.

**Phạm vi:** Java Core Exception, khía cạnh hiệu năng và chẩn đoán. Các tùy chọn `-XX` được nhắc tới là tùy chọn của **HotSpot/OpenJDK tương thích**, không phải một phần cú pháp hay hợp đồng Java SE dùng chung cho mọi JVM.

## 2. Stack trace là gì, được tạo vào lúc nào?

`Throwable` trong Java lưu thông tin liên quan đến vị trí thực thi khi ngoại lệ được tạo hoặc khi phương thức `fillInStackTrace()` được gọi trong cơ chế thông thường. Khi ném một `Throwable` đã được tạo từ trước, JVM không mặc nhiên chụp lại trace mới ở mỗi lần `throw`.

Ví dụ:

~~~java
IllegalStateException failure = new IllegalStateException("invalid");
throw failure;
~~~

Đây là **đoạn mã minh họa** ở trong một phương thức có quyền ném `IllegalStateException`. Stack trace của `failure` bình thường phản ánh **nơi đối tượng ngoại lệ được khởi tạo**, không nhất thiết là dòng `throw failure` nếu hai vị trí khác nhau.

Mỗi phần tử `StackTraceElement` có thể chứa tên class, tên phương thức, tên file, số dòng và thông tin liên quan tới module/class loader trong các bản Java hỗ trợ. Tuy nhiên, một frame có thể không có số dòng hoặc thông tin file nguồn nếu runtime không cung cấp đủ dữ liệu.

Các thao tác thường gặp:

| API | Ý nghĩa |
| --- | --- |
| `new IllegalStateException("...")` | Tạo exception; trong trường hợp thông thường ghi nhận dấu vết thực thi |
| `throw exception` | Ném đối tượng; không có bảo đảm tự chụp lại trace mới khi tái ném |
| `exception.getStackTrace()` | Lấy mảng `StackTraceElement[]` mà exception hiện cung cấp |
| `exception.printStackTrace()` | In exception, các frame, chuỗi cause và suppressed theo cách hiển thị của JDK |
| `exception.getCause()` | Truy nguyên nhân được nối bằng cause |
| `exception.getSuppressed()` | Lấy các ngoại lệ phụ được giữ lại, chẳng hạn lỗi đóng tài nguyên |
| `exception.fillInStackTrace()` | Ghi lại stack trace theo điểm gọi hiện tại khi writable, thay đổi dấu vết được lưu |
| `exception.setStackTrace(elements)` | Thay thế các phần tử stack trace được công bố nếu cho phép ghi |

**Không đồng nhất** mảng frame trong `getStackTrace()` với văn bản được in bởi `printStackTrace()`. Văn bản in có thể áp dụng quy tắc rút gọn hoặc bị cắt bởi hệ thống thu thập log.

## 3. Ba hiện tượng “không thấy đầy đủ stack trace”

Cùng nhìn thấy một log ít dòng, ta có thể gặp những nguyên nhân rất khác nhau.

| Hiện tượng | Bản chất | Cách kiểm tra |
| --- | --- | --- |
| Log xuất hiện `... N more` | `printStackTrace` rút gọn **các frame chung** giữa exception và cause/suppressed | Đọc cause, so sánh mảng `getStackTrace()`, kiểm tra phần trước của log |
| `getStackTrace().length == 0` | Exception có thể không ghi frame: thiết kế custom Throwable, tối ưu JVM cho một số lỗi, hoặc cấu hình/điều chỉnh khác | Kiểm tra class exception, cách tạo, runtime, flags và khả năng tái hiện |
| Log chỉ ghi `exception.getMessage()` hoặc bị cắt | Hệ thống logging/collector chưa hiển thị toàn bộ đối tượng `Throwable` | So sánh log với `getStackTrace()`, kiểm tra cấu hình logger/formatter và giới hạn kích thước |

Một trường hợp thứ tư là **stack trace có một số frame nhưng ít hơn mong đợi**. Nguyên nhân có thể nằm ở thời điểm đối tượng được tạo, cách tái ném, các phương thức viết lại trace hoặc giới hạn ghi nhận/hiển thị. Không nên tự động quy mọi trace ngắn về JIT.

Nếu muốn biết có thật sự mất frame hay chỉ không được hiển thị, bước đầu hợp lý là kiểm tra `getStackTrace().length` trên **chính đối tượng exception** trước khi kết luận.

## 4. Dòng `... N more`: rút gọn khi in, không nhất thiết mất dữ liệu

Khi một exception bọc một exception khác, phần cuối stack trace của cả hai thường trùng nhau vì chúng cùng đi qua những lời gọi ở phía ngoài.

Ví dụ minh họa cấu trúc:

~~~text
java.lang.RuntimeException: Không thể xử lý
    at Example.service(Example.java:20)
    at Example.main(Example.java:30)
Caused by: java.lang.IllegalStateException: Hỏng dữ liệu
    at Example.repository(Example.java:10)
    at Example.service(Example.java:18)
    ... 1 more
~~~

Đây là **ví dụ hình thức trình bày**, không phải stdout chính xác của một chương trình. Trong cách in truyền thống của JDK, `... 1 more` có nghĩa **một frame cuối của cause trùng với phần cuối của stack trace bao ngoài đang được in**, nên không cần lặp lại nguyên frame đó.

Cơ chế:

1. `printStackTrace` in thông tin exception bên ngoài.
2. Khi in cause, nó tìm các phần tử cuối của cause trùng phần cuối stack trace ở cấp bao gần nhất.
3. Các frame trùng ở cuối có thể được thay bằng dòng `... N more`.
4. Mảng `getStackTrace()` của cause có thể vẫn chứa đầy đủ những phần tử đó.

Nếu cause lại có cause, mỗi cấp có thể được rút gọn so với trace đang bao nó. Các suppressed exceptions cũng có thể được hiển thị có thụt dòng và rút gọn frame chung.

**`... N more` không phải bằng chứng FastThrow hoặc mất stack trace tại JVM.** Nó là cách hiển thị giảm lặp. Nếu cần kiểm tra số frame nguyên bản, đọc trực tiếp `getStackTrace()` của ngoại lệ ở cấp tương ứng.

### Khi nào không có `... N more`?

Nếu không có phần đuôi frame chung, có thể không có dòng rút gọn. Nếu logger dùng định dạng riêng, nó cũng có thể chọn cách hiển thị khác với `printStackTrace`. Do đó không nên coi việc có/không có dòng này là điều kiện bắt buộc của mọi hệ thống ghi log.

## 5. Ví dụ Java 17 độc lập: phần in rút gọn nhưng cause vẫn còn frame

Lưu tệp **CommonFramesDemo.java**:

~~~java
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

public class CommonFramesDemo {

    static void repository() {
        throw new IllegalStateException("bad data");
    }

    static void service() {
        try {
            repository();
        } catch (IllegalStateException original) {
            throw new RuntimeException("service failure", original);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (RuntimeException outer) {
            Throwable cause = outer.getCause();
            int frameCountBefore = cause.getStackTrace().length;

            StringWriter buffer = new StringWriter();
            outer.printStackTrace(new PrintWriter(buffer));
            String rendered = buffer.toString();

            boolean abbreviated = Pattern
                    .compile("\\.\\.\\. \\d+ more")
                    .matcher(rendered)
                    .find();

            System.out.println("Outer has frames: "
                    + (outer.getStackTrace().length > 0));
            System.out.println("Cause has frames: "
                    + (frameCountBefore > 0));
            System.out.println("Print has Caused by: "
                    + rendered.contains("Caused by: "));
            System.out.println("Print abbreviates common frames: "
                    + abbreviated);
            System.out.println("Cause frames still available: "
                    + (cause.getStackTrace().length == frameCountBefore));
        }
    }
}
~~~

Biên dịch và chạy trên Java 17:

~~~bash
javac -encoding UTF-8 CommonFramesDemo.java
java CommonFramesDemo
~~~

**Stdout kỳ vọng trong cách in chuẩn của Java 17:**

~~~text
Outer has frames: true
Cause has frames: true
Print has Caused by: true
Print abbreviates common frames: true
Cause frames still available: true
~~~

Lý giải:

- `repository()` tạo `IllegalStateException`; `service()` bắt nó và tạo `RuntimeException` với `original` làm cause.
- Stack trace của wrapper và cause có phần đuôi lời gọi chung, nên cách in chuẩn rút gọn những frame trùng.
- Việc dựng chuỗi qua `StringWriter` không sửa mảng stack trace của cause.
- Chương trình **không cần JIT** và không thử kích hoạt bất kỳ tối ưu HotSpot đặc biệt nào.
- Dòng kiểm tra cuối cho thấy rút gọn trong văn bản không làm mảng `getStackTrace()` của cause bị xóa.

**Lưu ý về tính di động:** ký hiệu `... N more` là cách trình bày quen thuộc của `Throwable.printStackTrace` trên JDK chuẩn; các hệ thống logging bên ngoài có thể render theo định dạng khác. Ví dụ nhắm tới Java 17 với cách in chuẩn, không phải một giao thức văn bản giữa các JVM/logger bất kỳ.

## 6. Khi stack trace thật sự trống: chủ động tạo exception không ghi trace

Java cung cấp protected constructor của `Throwable` và các lớp con phù hợp với chữ ký:

~~~java
Throwable(String message, Throwable cause,
          boolean enableSuppression,
          boolean writableStackTrace)
~~~

Chữ ký ở trên **minh họa API protected**, không phải lời gọi khởi tạo class `Throwable` trong code hoàn chỉnh.

Trong lớp exception tự định nghĩa có thể dùng:

~~~java
public final class LightweightException extends RuntimeException {
    public LightweightException(String message) {
        super(message, null, true, false);
    }
}
~~~

Đây là **lớp độc lập**, lưu thành `LightweightException.java` nếu sử dụng riêng. Tham số `writableStackTrace=false` yêu cầu không ghi stack trace của đối tượng mới, nên `getStackTrace()` trả mảng rỗng. Các lần gọi `fillInStackTrace()` và `setStackTrace(...)` không khôi phục được frame theo cách thông thường trên đối tượng đã tắt writable stack trace.

**Đây là quyết định thiết kế của lớp exception**, không phải FastThrow. Nó có hiệu lực cả trong trường hợp ngoại lệ được tạo lần đầu; không cần lặp hàng nghìn lần.

### Đánh đổi

Ưu điểm có thể có:

- Giảm công việc và chi phí liên quan đến ghi nhận stack trace cho đối tượng exception.
- Hợp lý trong một số đường điều khiển được biết trước, thường xuyên và đã có đủ ngữ cảnh khác.

Nhược điểm:

- Mất vị trí tạo exception trong stack trace của chính nó.
- Khó truy ngược luồng gọi khi một lỗi bất thường xuất hiện.
- Nếu quyết định này được dùng rộng rãi, log production có thể chỉ còn tên lỗi và message.

**Không tối ưu bằng cách tắt stack trace cho mọi ngoại lệ.** Với lỗi hiếm và có giá trị chẩn đoán cao, chi phí đo đạc có thể nhỏ hơn nhiều so với thời gian khắc phục sự cố do không có trace.

## 7. Ví dụ Java 17 độc lập: writableStackTrace=false

Lưu tệp **DisabledTraceDemo.java**:

~~~java
public class DisabledTraceDemo {

    static final class LightweightFailure extends RuntimeException {
        LightweightFailure(String message) {
            super(message, null, true, false);
        }
    }

    public static void main(String[] args) {
        LightweightFailure failure = new LightweightFailure("expected");

        System.out.println("Initial frames: "
                + failure.getStackTrace().length);
        System.out.println("Cause: " + failure.getCause());

        StackTraceElement fake = new StackTraceElement(
                "Example", "method", "Example.java", 42);
        failure.setStackTrace(new StackTraceElement[]{fake});

        System.out.println("Frames after setStackTrace: "
                + failure.getStackTrace().length);

        failure.fillInStackTrace();
        System.out.println("Frames after fillInStackTrace: "
                + failure.getStackTrace().length);

        System.out.println("Message is still present: "
                + "expected".equals(failure.getMessage()));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 DisabledTraceDemo.java
java DisabledTraceDemo
~~~

**Stdout chính xác:**

~~~text
Initial frames: 0
Cause: null
Frames after setStackTrace: 0
Frames after fillInStackTrace: 0
Message is still present: true
~~~

Điểm đáng nhớ:

- `super(message, null, true, false)` tắt khả năng ghi stack trace cho **chính đối tượng** `LightweightFailure`.
- `getMessage()` vẫn còn; “không có frame” không có nghĩa Throwable mất tất cả thông tin.
- `getCause()` trả `null` vì ví dụ truyền cause `null`. Một exception tùy biến khác có thể vẫn giữ cause ngay cả khi không lưu trace riêng.
- `setStackTrace` và `fillInStackTrace` không tái tạo frame do writable stack trace đã tắt từ lúc khởi tạo.

Lưu ý: việc khởi tạo custom exception dạng này là lựa chọn chủ động, phải ghi rõ trong thiết kế API. Không sử dụng nó chỉ để làm gọn log một cách tùy tiện.

## 8. FastThrow: tối ưu phụ thuộc HotSpot/JIT

Ngoài trường hợp chủ động tắt writable stack trace, một số bản HotSpot có tối ưu thường gọi là **FastThrow**, gắn với tùy chọn:

~~~bash
-XX:+OmitStackTraceInFastThrow
~~~

Ý tưởng khái quát: khi JIT nhận diện một số **ngoại lệ runtime ngầm định** xảy ra nhiều lần ở cùng vị trí bytecode, runtime có thể dùng đường xử lý nhanh và bỏ việc ghi nhận stack trace chi tiết cho các lần ném lỗi được tối ưu. Mục tiêu là giảm overhead ở đường ngoại lệ lặp lại dày đặc.

Các tình huống hay được thảo luận gồm một số lỗi phát sinh từ phép toán hoặc thao tác bytecode, chẳng hạn:

- `NullPointerException` khi dereference null.
- `ArithmeticException` khi chia nguyên cho 0.
- `ArrayIndexOutOfBoundsException` ở truy cập mảng sai chỉ số.
- `ClassCastException` ở phép ép kiểu sai.

**Không coi danh sách này là quy tắc bảo đảm rằng mọi lần ném của các class đó đều bị rút trace.** Tối ưu phụ thuộc bản JVM, compiler, profile thực thi, ngữ cảnh bytecode, flags và trạng thái warm-up. Không phải tất cả exception thuộc các class trên đều được tạo bằng đường bytecode ngầm định; một exception được tạo tường minh bằng `new` không nhất thiết có cùng hành vi.

### Ví dụ một lỗi ngầm định

~~~java
static int divide(int divisor) {
    return 10 / divisor;
}
~~~

Đây là **mã minh họa**. Gọi `divide(0)` ném `ArithmeticException` do phép chia số nguyên cho 0. Nếu HotSpot tối ưu được đường lỗi lặp lại sau quá trình warm-up, một số lần ném sau có thể khác về chi phí và mức độ stack trace so với những lần đầu.

Không nên kết luận JVM có FastThrow chỉ vì một exception nhất định có trace ngắn trong một lần chạy; cũng không nên kết luận JVM không hỗ trợ FastThrow chỉ vì thử một vòng lặp mà vẫn thấy trace đầy đủ. Điều kiện tối ưu và ngưỡng kích hoạt **không phải API Java SE ổn định**.

## 9. Flag HotSpot và giới hạn tính di động

Trong một môi trường HotSpot/OpenJDK hỗ trợ flag này, có thể kiểm tra khác biệt bằng cách chạy cùng chương trình với:

~~~bash
java -XX:-OmitStackTraceInFastThrow YourMainClass
~~~

Đây là **cú pháp lệnh minh họa**. `YourMainClass` là tên lớp main thực tế cần thay thế khi sử dụng; không phải một chương trình standalone của bài. Dấu `-` trước tên flag vô hiệu hóa tùy chọn trong những HotSpot hỗ trợ. Không mặc định flag này tồn tại trên mọi JVM hoặc mọi bản phân phối Java.

Để biết runtime nào đang chạy:

~~~bash
java -version
~~~

Khi điều tra ở máy cụ thể, cần ghi lại ít nhất:

- Nhà cung cấp JVM và phiên bản JDK.
- Tùy chọn khởi động liên quan đến tối ưu và stack trace.
- Cách chạy ứng dụng (JIT thông thường, kiểm thử, cấu hình container/production).
- Exception type, nơi phát sinh và đặc điểm tần suất lặp lại.
- Đầu ra raw từ `getStackTrace()` so với output của logger.
- Thông tin cho biết có dùng exception class tùy biến tắt writable stack trace hoặc code gọi `setStackTrace` hay không.

**Cẩn trọng:** `-XX:-OmitStackTraceInFastThrow` chỉ giải quyết một nguyên nhân liên quan đến tối ưu của HotSpot. Nó không tự làm xuất hiện stack trace cho exception được thiết kế `writableStackTrace=false`, không sửa trace đã bị ứng dụng thay thế, cũng không khôi phục đoạn log đã bị collector cắt.

### Cờ cấu hình độ sâu và các tối ưu khác

Một số JVM còn có tùy chọn liên quan đến số frame tối đa được ghi nhận hoặc cách hiển thị chẩn đoán. Tên, giá trị mặc định và ý nghĩa cụ thể có thể khác theo implementation/phiên bản. Khi cần thay đổi, hãy đối chiếu tài liệu và thông tin flags của **đúng JVM đang chạy**. Đừng sao chép một bộ tham số `-XX` từ hướng dẫn cũ rồi coi đó là quy định chung của Java.

## 10. Ví dụ thăm dò FastThrow: đầu ra không bảo đảm giống nhau

Nếu muốn thăm dò một bản HotSpot cụ thể, có thể dùng chương trình sau. Đây là **một phép thử quan sát**, không phải kiểm thử khẳng định FastThrow chắc chắn phải được kích hoạt.

Lưu tệp **FastThrowProbe.java**:

~~~java
public class FastThrowProbe {

    static int divideBy(int value) {
        return 100 / value;
    }

    public static void main(String[] args) {
        int total = 0;
        int emptyTraces = 0;

        for (int i = 0; i < 200_000; i++) {
            try {
                divideBy(0);
            } catch (ArithmeticException error) {
                total++;
                if (error.getStackTrace().length == 0) {
                    emptyTraces++;
                }
            }
        }

        System.out.println("Caught: " + total);
        System.out.println("Empty traces range valid: "
                + (emptyTraces >= 0 && emptyTraces <= total));
        System.out.println("Observed empty traces: " + emptyTraces);
    }
}
~~~

Chạy với Java 17+:

~~~bash
javac -encoding UTF-8 FastThrowProbe.java
java FastThrowProbe
java -XX:-OmitStackTraceInFastThrow FastThrowProbe
~~~

Hai dòng đầu của stdout luôn có dạng:

~~~text
Caught: 200000
Empty traces range valid: true
~~~

Dòng thứ ba là **số đo thực tế**, không có một giá trị stdout cố định có thể hứa trước:

~~~text
Observed empty traces: <số đo từ 0 đến 200000>
~~~

Dòng trong khối text trên là **mẫu diễn giải định dạng**, không phải dòng stdout chính xác; chương trình sẽ in một số nguyên. Tùy runtime, mức tối ưu, chế độ thực thi và phiên bản JDK, giá trị có thể bằng 0 hoặc lớn hơn 0.

**Không sử dụng số lần lặp 200.000 làm công thức bảo đảm tái hiện.** JIT có thể không tạo đường ném tối ưu trong môi trường cụ thể. `getStackTrace()` cũng làm phép thử có chi phí và có thể tác động tới cấu hình tối ưu/quan sát. Mục tiêu chỉ là tạo một phép thử có thể đo đạc trên máy thực tế.

Nếu command có `-XX:-OmitStackTraceInFastThrow` bị JVM từ chối, điều đó cho thấy cần dùng tài liệu của runtime hiện có, không phải bằng chứng Java SE “bị lỗi”. Khi so sánh, giữ nguyên nguồn mã, cùng kiểu workload và ghi lại tùy chọn runtime.

## 11. NPE message và những khác biệt về chẩn đoán

Java hiện đại có thể cung cấp message chi tiết hơn cho một số `NullPointerException` phát sinh ngầm định. Message này chịu ảnh hưởng của runtime và cấu hình chẩn đoán. Nếu exception được tạo theo một đường nhanh bỏ stack trace, mức thông tin message có thể không giống một trường hợp ném thông thường.

Điểm cần học **không phải** thuộc lòng một chuỗi message NPE:

- Không dựa vào văn bản `NullPointerException.getMessage()` để xác định hợp đồng nghiệp vụ.
- Không giả định mọi NPE đều có message hữu ích hoặc cùng định dạng.
- Một NPE tạo tường minh bằng `new NullPointerException("...")` khác với NPE do JVM phát hiện ở thao tác dereference.
- Trong hệ thống log, cần giữ loại ngoại lệ, ngữ cảnh thao tác và thông tin tương quan; message có thể thay đổi giữa các phiên bản JDK.
- Nếu thấy NPE thiếu trace ở đường nóng, cần xét cả chính sách logging và khả năng tối ưu HotSpot, chứ không chỉ nghi ngờ lỗi tạo wrapper.

Khi cần chẩn đoán code sai, hãy tìm vị trí phát sinh từ trace có thể tin cậy, tái hiện trong cấu hình có khả năng quan sát đầy đủ và kiểm tra input/trạng thái có liên quan. Không sửa code dựa trên giả định “NPE nào cũng thuộc cùng một dòng”.

## 12. Những lý do khác khiến log không đầy đủ

### 12.1. Chỉ log message

~~~java
logger.warning(error.getMessage());
~~~

Đây là **mã minh họa** với biến `logger` và `error` đã tồn tại. Một lời gọi chỉ truyền message sẽ không tự ghi cause chain hay stack trace. Lỗi trong log thiếu thông tin **không chứng minh exception đã mất stack trace**.

Với `java.util.logging.Logger`, khi chính sách dữ liệu cho phép, có thể truyền cả `Throwable`:

~~~java
logger.log(java.util.logging.Level.SEVERE,
        "operation.failed", error);
~~~

Đây cũng là **mã minh họa**. Logger và formatter sẽ quyết định cách hiển thị; phải kiểm tra cấu hình, vì stack trace hoặc message kỹ thuật có thể chứa thông tin nhạy cảm.

### 12.2. Logger hoặc log collector giới hạn kích thước

Nhiều hệ thống log có thể áp giới hạn số ký tự, số dòng, số frame hoặc kích thước bản ghi; một số giao diện chỉ mở phần trace đầu tiên. Nếu output bị cắt ở tầng hiển thị/thu thập, việc đổi flag JIT không giải quyết được. Cần kiểm tra log raw, cài đặt formatter, giới hạn truyền dữ liệu và giao diện quan sát.

### 12.3. Exception được tạo ở nơi khác

~~~java
static final RuntimeException CACHED =
        new RuntimeException("cached");

static void fail() {
    throw CACHED;
}
~~~

Đây là **mã minh họa**. Stack trace của `CACHED` thường phản ánh nơi nó được khởi tạo trong quá trình khởi tạo lớp, chứ không chỉ ra chính xác vị trí mỗi lần `fail()` ném lại đối tượng đó. Đừng tái sử dụng exception có trace để tối ưu mà không hiểu mất khả năng gắn lỗi với từng lần xảy ra.

### 12.4. Ứng dụng tự thay đổi stack trace

~~~java
error.setStackTrace(new StackTraceElement[0]);
~~~

Đây là **mã minh họa** với `error` có sẵn. Nếu exception cho phép sửa trace, lời gọi này thay mảng được công bố thành rỗng. Đây không phải tối ưu JIT mà là tác động có chủ đích của ứng dụng hoặc thư viện.

### 12.5. Tắt writable stack trace

Lớp Throwable tự định nghĩa có thể dùng constructor bốn tham số với `writableStackTrace=false`. Trường hợp này cần sửa thiết kế class hoặc cách tạo exception nếu muốn có frame; không thể chỉ bật flag HotSpot để khôi phục.

### 12.6. Rút gọn cause/suppressed khi in

Dòng `... N more` chỉ lược phần cuối trùng nhau. Nhiều người nhầm việc này với trace rỗng và làm mất thời gian điều chỉnh cấu hình JVM không liên quan.

## 13. Cách chẩn đoán khi thấy stack trace bị thiếu

Áp dụng quy trình từ ít xâm lấn đến nhiều xâm lấn:

1. **Xác định đối tượng đang quan sát.** Đó là exception gốc, wrapper, cause hay một thông báo đã được chuyển đổi?
2. **Kiểm tra định dạng log.** Có `Caused by`, `Suppressed:`, `... N more` hay dấu hiệu bị cắt vì giới hạn dòng không?
3. **Đo số frame thực tế** bằng `error.getStackTrace().length` ở môi trường có thể chẩn đoán, nếu đang nắm được đối tượng Throwable.
4. **Duyệt cause một cách có kiểm soát.** Một wrapper có frame nhưng cause rỗng, hoặc ngược lại, là hai tình huống khác nhau.
5. **Kiểm tra class exception.** Có constructor tắt writable stack trace, override `fillInStackTrace()`, gọi `setStackTrace()` hoặc cache instance không?
6. **Kiểm tra phương thức log.** Đang truyền `Throwable` hay chỉ `getMessage()`? Logger/collector có giới hạn số frame không?
7. **Ghi lại môi trường JVM** bằng `java -version` và tùy chọn chạy. Có thay đổi JDK hoặc mode JIT gần thời điểm phát hiện vấn đề không?
8. **Nếu nghi HotSpot FastThrow,** tái hiện trên môi trường kiểm soát, so sánh với cấu hình hỗ trợ `-XX:-OmitStackTraceInFastThrow`.
9. **Nếu còn nghi truncation ở JVM,** kiểm tra tài liệu của đúng runtime về giới hạn độ sâu stack trace và các cấu hình liên quan.
10. **Đảm bảo bảo mật log:** không đưa raw stack trace của production có dữ liệu nhạy cảm ra nơi công khai chỉ để kiểm thử.

### Mẫu kiểm tra an toàn

~~~java
Throwable cause = error.getCause();
System.out.println("Top-level type: " + error.getClass().getName());
System.out.println("Top-level frames: " + error.getStackTrace().length);
System.out.println("Has cause: " + (cause != null));
if (cause != null) {
    System.out.println("Cause frames: " + cause.getStackTrace().length);
}
~~~

Đây là **mã minh họa**, giả định `error` đã tồn tại. Mục tiêu là phân biệt trace của wrapper và cause; không in message kỹ thuật nhạy cảm vào output công khai.

Một phép kiểm tra `getStackTrace().length == 0` chỉ mô tả trạng thái hiện tại của đối tượng, không tự chứng minh nguyên nhân. Khi không có quyền truy cập Throwable thật và chỉ có log đã format, cần cẩn trọng với mọi kết luận về hành vi của JVM.

## 14. Mối liên hệ giữa hiệu năng và khả năng chẩn đoán

Việc tạo và xử lý exception có thể tốn tài nguyên:

- Phân bổ đối tượng exception.
- Ghi nhận thông tin stack khi implementation cung cấp writable trace.
- Khởi tạo message và tạo các wrapper.
- Materialize/copy frame khi truy `getStackTrace()`.
- Định dạng, ghi và truyền stack trace qua logger/collector.
- Chi phí I/O và lưu trữ của log rất dài hoặc lặp lại.

Mức chi phí phụ thuộc runtime, JIT, độ sâu call stack, tần suất ném, logger và cách thu thập dữ liệu. Không nên khẳng định một con số cố định như “exception luôn chậm gấp 100 lần” khi chưa đo chính workload.

**Điều cần tối ưu đầu tiên** thường là tránh dùng exception để biểu đạt nhánh thành công/dự kiến xảy ra dày đặc. Ví dụ, nếu dữ liệu có thể thiếu theo nghiệp vụ, cân nhắc API trả `Optional`, một result type có cấu trúc, hoặc kiểm tra điều kiện mà không cần ném exception mỗi lần. Không biến mọi lỗi thật thành giá trị thành công giả chỉ để tăng throughput.

### Khi nào có thể cân nhắc exception nhẹ?

- Đường đi **rất nóng** và trạng thái được mô hình hóa rõ ràng như một tín hiệu nội bộ.
- Đã đo đạc bằng benchmark phù hợp và xác định overhead của stack capture là nút thắt.
- Có cơ chế khác để ghi đủ ngữ cảnh khi phát sinh lỗi thật.
- Người bảo trì chấp nhận được giới hạn chẩn đoán và API công bố rõ cách xử lý.

Ngay cả khi dùng exception nhẹ, cần tránh dùng chúng qua ranh giới dịch vụ cho người dùng mà không có thông tin tương quan, mã lỗi và bối cảnh. Các exception thực sự bất thường nên ưu tiên khả năng chẩn đoán.

### Vì sao cần đo trước khi tắt trace?

Nếu một lỗi xảy ra mỗi vài giờ, chi phí tạo stack trace có thể không đáng kể, còn việc mất call stack làm quá trình điều tra tốn rất nhiều thời gian. Ngược lại, ném hàng triệu exception/giây có thể gây áp lực GC và log; nhưng giải pháp tối ưu tốt hơn đôi khi là thay đổi luồng điều khiển, không phải tắt trace.

Khi benchmark exception, cần tránh sai lầm:

- So sánh hai đoạn code không cùng công việc.
- Không warm up JIT rồi coi số đo là steady state.
- In stack trace ra console trong vòng lặp và cho rằng đang đo riêng chi phí tạo exception.
- Không phân biệt exception ngầm định do JVM ném với exception tạo tường minh bằng `new`.
- Chạy một vòng lặp trên một máy rồi suy ra kết quả cho mọi JVM.
- Không xét GC, allocation rate, latency tail và chất lượng log.

Với benchmark nghiêm túc, có thể dùng JMH trong một dự án cấu hình phù hợp; JMH không phải API có sẵn chỉ bằng `javac` của Java SE. Dù đo bằng công cụ nào, phải ghi version runtime, JIT flags, tải và cách thu thập kết quả.

## 15. Logging: giữ thông tin cần thiết nhưng tránh flood

Khi exception lặp dày đặc, việc ghi full stack trace cho từng lần có thể làm hệ thống logging quá tải. Các biện pháp thích hợp có thể gồm:

- Dùng metric đếm số lỗi theo loại và thành phần, thay vì lưu hàng triệu stack trace giống nhau.
- Dùng sampling hoặc rate limiting có chủ đích tại tầng logging/telemetry; giữ trace của mẫu lỗi cần điều tra.
- Ghi correlation ID, operation, exception type và ngữ cảnh an toàn để ghép lỗi với request.
- Giữ cause chain của mẫu lỗi quan trọng; không chỉ log message nếu cần debug nguồn.
- Không phát tán raw stack trace hoặc thông tin hạ tầng tới người dùng hoặc log công khai.

**Sampling do ứng dụng/logger thực hiện khác với FastThrow của JVM.** Ở sampling, Throwable có thể vẫn giữ trace nhưng không được gửi lên collector ở mọi lần. Ở FastThrow, đường exception tối ưu có thể không lưu trace chi tiết. Hai hiện tượng cần được kiểm tra và giải quyết ở hai tầng riêng.

Nếu hệ thống đang mất thông tin chẩn đoán, thay vì bật full stack trace cho mọi request production vô thời hạn, có thể tái hiện trên môi trường kiểm soát hoặc áp dụng một chế độ ghi log được giới hạn về thời gian, lưu lượng và quyền truy cập.

## 16. Stack trace của cause và suppressed cũng cần được xét

Một wrapper có thể giữ cause:

~~~java
try {
    repositoryCall();
} catch (IllegalStateException original) {
    throw new RuntimeException("Operation failed", original);
}
~~~

Đây là **mã minh họa** với `repositoryCall()` được định nghĩa trong ứng dụng. Wrapper và cause là **hai đối tượng** có trace riêng. Nếu wrapper có trace mà cause bị tối ưu thành rỗng, logger có thể in wrapper với đầy đủ frame nhưng cause rất ngắn.

Ngược lại, nếu wrapper là lightweight exception không writable stack trace nhưng giữ cause bình thường, trace của cause vẫn có thể hữu ích. Bởi vậy khi điều tra, **đừng chỉ xem `getStackTrace()` ở exception ngoài cùng**.

Với `try-with-resources`, ngoại lệ khi đóng tài nguyên có thể được lưu trong `getSuppressed()` của lỗi chính. `printStackTrace` có cách hiển thị suppressed exception riêng và cũng có thể rút gọn các frame chung. Một dòng `... N more` dưới `Suppressed:` không đồng nghĩa lỗi đóng tài nguyên đã biến mất.

Đừng dùng việc thêm suppressed exception thủ công chỉ để “bù” cho mọi trace thiếu. Cause và suppressed thể hiện các quan hệ lỗi khác nhau; quan trọng hơn là giữ đúng đối tượng và ngữ nghĩa của chúng.

## 17. fillInStackTrace và việc tái sử dụng Throwable

`Throwable.fillInStackTrace()` có thể làm trace của exception phản ánh **điểm gọi mới** nếu đối tượng cho phép ghi stack trace. Nó không khôi phục các frame gốc đã bị bỏ qua do FastThrow hoặc do log collector cắt.

Ví dụ minh họa:

~~~java
RuntimeException error = new RuntimeException("cached");
error.fillInStackTrace();
throw error;
~~~

Đây là **đoạn mã** trong một phương thức Java. Việc gọi `fillInStackTrace` có thể làm thay đổi dấu vết đang lưu. Không dùng mẹo này để cố khôi phục chính xác “điểm phát sinh thật” của một lỗi đã xảy ra trước đó; nó chỉ ghi lại stack tại vị trí gọi hiện thời theo implementation.

Nếu một đối tượng exception được tái sử dụng giữa nhiều thread, việc đọc/viết trace hoặc context của nó có thể gây nhầm lẫn trong log; bản thân việc dùng một Throwable chung cũng không có nghĩa mỗi lần `throw` sẽ ghi nhận đầy đủ lịch sử riêng của từng lần lỗi.

Nhìn chung, với lỗi thực sự bất thường, hãy tạo exception phù hợp và giữ cause thay vì tái sử dụng một instance chung chỉ vì muốn giảm allocation. Nếu trạng thái có thể dự kiến và xảy ra thường xuyên, một result type phù hợp sẽ rõ nghĩa hơn.

## 18. Kiểm thử stack trace một cách đáng tin cậy

Nên kiểm thử **thuộc tính cần thiết** thay vì chụp nguyên văn stack trace rồi assert từng số dòng.

| Mục tiêu | Cách kiểm tra |
| --- | --- |
| Exception thường có trace | Kiểm tra `getStackTrace().length > 0` trong điều kiện chạy thông thường |
| Cause được giữ | Kiểm tra `wrapper.getCause() == original` |
| Cause còn frame sau khi in | So sánh `getStackTrace().length` trước và sau `printStackTrace` |
| Lightweight exception không có writable trace | Kiểm tra `getStackTrace().length == 0` |
| Thứ tự lỗi trong ứng dụng | Kiểm tra type/code/cause và bối cảnh đã xác định, không phụ thuộc số dòng stack |
| HotSpot FastThrow | Quan sát/benchmark trên runtime đích, **không** đặt assert rằng một lần lặp cụ thể phải không có trace |
| Logger bị cắt | So sánh dữ liệu Throwable raw và output thật của logger/collector |
| Public response | Kiểm tra không chứa stack trace, path, hostname hoặc secret |

Ví dụ với JUnit 5, đoạn sau là **mã minh họa trong lớp test có JUnit 5 và import phù hợp**:

~~~java
RuntimeException original = new RuntimeException("low-level");
RuntimeException wrapper = new RuntimeException("high-level", original);

assertSame(original, wrapper.getCause());
assertTrue(original.getStackTrace().length > 0);
assertTrue(wrapper.getStackTrace().length > 0);
~~~

Không cần assert một số frame cụ thể như 2 hay 3 nếu mục đích chỉ là bảo toàn cause: compiler, test framework và cách gọi có thể thay đổi số frame.

Không đặt unit test bắt buộc “sau 10.000 lần `ArithmeticException` chắc chắn phải có empty stack trace”. Điều đó biến chi tiết tối ưu của một implementation thành điều kiện đúng sai của code nghiệp vụ và khiến test không ổn định khi nâng cấp JDK.

## 19. Lỗi phổ biến và cách sửa

1. **Thấy `... N more` rồi cho rằng JVM đã xóa các frame:** kiểm tra cause và `getStackTrace()`; thường chỉ là rút gọn khi in.
2. **Thấy trace rỗng rồi chắc chắn đổ lỗi cho FastThrow:** còn nhiều nguyên nhân như custom Throwable hoặc thao tác `setStackTrace`.
3. **Cho rằng FastThrow áp dụng cho mọi exception:** cơ chế phụ thuộc bytecode, JIT, runtime và trường hợp cụ thể.
4. **Mong flag HotSpot hoạt động trên mọi JVM:** `-XX` không phải API Java SE có hợp đồng portable.
5. **Dùng một số vòng lặp cố định để test FastThrow:** quá trình tối ưu không có ngưỡng bảo đảm cho chương trình của bạn.
6. **Chỉ log `e.getMessage()`:** không thấy stack trace vì cách log, không phải vì JVM không lưu trace.
7. **Log full trace không giới hạn cho lỗi hàng triệu lần:** có thể tăng I/O, dung lượng lưu và ảnh hưởng latency.
8. **Tắt writable stack trace để sửa vấn đề log quá lớn:** có thể làm mất dữ liệu điều tra lỗi thật.
9. **Tạo một exception rồi ném lại nhiều lần, mong trace tự đổi:** trace có thể giữ vị trí tạo ban đầu.
10. **Gọi `fillInStackTrace()` sau lỗi và coi đó là trace gốc:** thao tác này có thể thay dấu vết thành điểm gọi mới.
11. **Không xem cause/suppressed:** lỗi gốc có thể ở một cấp khác với wrapper.
12. **Tin rằng logger luôn giữ đủ dữ liệu:** định dạng, sampling và collector có thể rút ngắn đầu ra.
13. **Dùng stack trace như một public API ổn định:** tên lớp, số dòng, định dạng và số frame có thể đổi khi tối ưu hoặc đổi phiên bản.
14. **Đưa raw trace cho người dùng cuối:** có thể lộ đường dẫn và thông tin triển khai; nên trả mã lỗi/thông báo an toàn và correlation ID.
15. **Đo exception bằng benchmark thiếu warm-up và kiểm soát môi trường:** kết quả khó dùng để quyết định kiến trúc.
16. **Tự động bỏ qua exception không có trace:** lỗi vẫn có thể nghiêm trọng; phải phân loại theo ngữ nghĩa và tác động thực tế.
17. **Đồng nhất `getStackTrace().length == 0` với `getCause() == null`:** hai thuộc tính độc lập.
18. **Gắn mọi trace thiếu với lỗi JVM:** kiểm tra trước mã nguồn, cấu hình logging, thời điểm tạo exception và điều kiện môi trường.

## 20. Checklist vận hành

Khi gặp log exception có vẻ thiếu stack trace, hãy hỏi:

- Đây là phần in `... N more` do các frame cuối trùng nhau, hay `getStackTrace()` của object thật rỗng?
- Đang xem wrapper, cause hay suppressed exception?
- Logger có nhận `Throwable` hay chỉ được truyền message?
- Trace có bị cắt do formatter, giao diện hoặc giới hạn ingestion không?
- Exception có constructor tắt writable stack trace, override `fillInStackTrace`, bị gọi `setStackTrace` hoặc được cache dùng lại không?
- Có phải lỗi runtime ngầm định xuất hiện dày đặc trên đường bytecode nóng trong HotSpot không?
- Đã ghi lại phiên bản JVM và flags trước khi so sánh chưa?
- Nếu muốn tắt FastThrow để chẩn đoán, đã xác nhận runtime hỗ trợ flag và dùng một phép thử kiểm soát chưa?
- Có đảm bảo log/traces không tiết lộ secret hoặc dữ liệu nhạy cảm không?
- Việc giảm chi phí exception có căn cứ đo lường và không phá khả năng điều tra lỗi quan trọng không?

**Kết luận:** “Stack trace bị lược bỏ” không mô tả duy nhất một hiện tượng. Dòng `... N more` thường chỉ **rút gọn khi in**; một exception tùy biến có thể **chủ động không ghi trace**; còn HotSpot có thể dùng **tối ưu FastThrow** để giảm chi phí của một số lỗi runtime xuất hiện nhiều lần. Hãy xác định đúng lớp nguyên nhân trước khi điều chỉnh JVM hoặc thiết kế exception. Với lỗi hiếm và quan trọng, khả năng chẩn đoán chính xác thường đáng giá hơn một tối ưu vi mô chưa đo đạc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-006">Đánh đổi khi tắt writableStackTrace hoặc tái sử dụng đối tượng ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi khi tắt writableStackTrace hoặc tái sử dụng đối tượng ngoại lệ

## 1. Bài toán hiệu năng và giá trị chẩn đoán

Trong Java, tạo một exception thông thường không chỉ có nghĩa cấp phát một object chứa thông điệp. Theo cách triển khai và cấu hình, lúc khởi tạo **Throwable**, JVM còn có thể thu thập dấu vết ngăn xếp (stack trace) để sau này cho biết nơi đối tượng lỗi được tạo, qua những phương thức nào, ở file và dòng nguồn nào. Việc tạo rất nhiều exception có thể gây chi phí CPU, cấp phát, áp lực garbage collector và chi phí ghi log khi in stack trace.

Một số hệ thống muốn tối ưu đường xử lý rất nóng bằng hai kỹ thuật:

1. **Tắt writableStackTrace** cho một loại exception chuyên dụng, khiến đối tượng không thu thập/không giữ stack trace theo cơ chế chuẩn.
2. **Tái sử dụng một đối tượng exception** đã được tạo từ trước, tránh tạo object và thu thập stack trace mới cho mỗi lần ném.

Cả hai đều có thể giảm chi phí trong những tình huống đo đạc cụ thể, nhưng chúng lấy đi hoặc làm sai lệch dữ liệu cần thiết để điều tra lỗi. Mối nguy hiểm lớn nhất không phải chỉ là “log có ít dòng hơn”, mà là **không còn xác định được lần lỗi thực tế phát sinh ở đâu, trong request nào, hay suppressed exceptions của lần nào**.

**Nguyên tắc thiết kế:** ưu tiên giữ exception đầy đủ cho lỗi bất thường cần chẩn đoán; chỉ tối ưu đường exception sau khi đo được đây là nút thắt hiệu năng, xác nhận tác động và có một kênh quan sát khác bù đắp dữ liệu bị mất. Đừng biến ngoại lệ thành đường điều khiển lặp thường xuyên chỉ vì có cách làm stackless.

Bài học này thuộc nhóm **Hiệu năng** trong **Java Core — Exception**: trọng tâm là semantics của Throwable, lợi ích/chi phí của tối ưu và cách ra quyết định, không phải mẹo tăng tốc vô điều kiện.

## 2. Stack trace được tạo và sử dụng như thế nào?

Một **Throwable** chứa các thành phần độc lập nhưng liên quan:

| Thành phần | Công dụng | API thường dùng |
| --- | --- | --- |
| Loại lỗi | Phân loại điều kiện thất bại | **getClass()**, catch theo class |
| Detail message | Diễn giải tình huống hoặc dữ liệu lỗi | **getMessage()** |
| Cause | Một Throwable là nguyên nhân của Throwable khác | **getCause()**, constructor có cause |
| Suppressed exceptions | Các lỗi phụ, thường từ cleanup | **addSuppressed()**, **getSuppressed()** |
| Stack trace | Dấu vết lời gọi được ghi nhận cho Throwable | **getStackTrace()**, **printStackTrace()** |
| Writable stack trace | Quy định Throwable có giữ/chỉnh stack trace theo cơ chế chuẩn không | Constructor được bảo vệ của **Throwable** |

Khi tạo exception theo đường thông thường, constructor của Throwable thường gọi logic **fillInStackTrace()** để ghi nhận ngữ cảnh thực thi vào object. Thời điểm thu nhận trace **thường là lúc xây Throwable**, không phải mỗi lần từ khóa **throw** được thực thi trên cùng một object.

Sau đó, **getStackTrace()** trả về mảng **StackTraceElement**, còn **printStackTrace()** trình bày các frame cùng cause và suppressed. Việc in/log stack trace cũng có thể có chi phí riêng, khác với chi phí thu thập trace ban đầu. Do đó, khi benchmark phải phân biệt:

- **Tạo exception:** cấp phát đối tượng, thực hiện constructor và có thể thu thập trace.
- **Ném và bắt:** chi phí kiểm soát luồng, unwinding tới handler.
- **Lấy stack:** gọi getStackTrace và chuyển dữ liệu thành các frame dễ đọc.
- **Định dạng và xuất log:** chuyển Throwable thành chuỗi, ghi file/console/network.
- **GC:** thu hồi các exception được tạo nhiều lần.

Tắt writableStackTrace có thể tác động tới phần thu thập và lưu trace, nhưng **không làm biến mất mọi chi phí của exception**, không loại bỏ mọi object allocation và không thay đổi luật checked/unchecked hoặc cách catch.

## 3. Constructor bốn tham số của Throwable

Java cung cấp constructor **protected** của Throwable:

~~~java
// Chữ ký API của java.lang.Throwable:
protected Throwable(String message, Throwable cause,
                    boolean enableSuppression,
                    boolean writableStackTrace);
~~~

Hai boolean không đồng nghĩa:

- **enableSuppression:** cho phép Throwable ghi nhận những exception được thêm qua **addSuppressed()**.
- **writableStackTrace:** cho phép Throwable thu thập và duy trì stack trace theo cơ chế chuẩn.

Các class exception con có thể expose cấu hình này qua constructor phù hợp. **Throwable** không mở constructor này như một public factory cho tất cả caller; cách dùng thông thường là kế thừa **Exception**, **RuntimeException** hoặc một class có constructor protected tương ứng, chẳng hạn **RuntimeException(String, Throwable, boolean, boolean)**.

Một class custom điển hình:

~~~java
// Mã minh họa trong file riêng hoặc dưới dạng nested class.
final class LightweightFailure extends RuntimeException {
    LightweightFailure(String message, Throwable cause) {
        super(message, cause, true, false);
        //                  ^ suppression enabled
        //                        ^ writableStackTrace disabled
    }
}
~~~

Cấu hình **true, false** ở đây **giữ suppressed exceptions** nhưng **không giữ stack trace chuẩn**. Nếu đặt **false, true**, ta **tắt suppressed** trong khi vẫn giữ stack trace. Hai trục cấu hình phải được chọn theo yêu cầu chẩn đoán, không tùy tiện gộp thành một cờ “nhẹ”.

Đối với exception có writableStackTrace=false, hợp đồng Throwable nêu constructor không gọi fillInStackTrace theo cách thông thường và các thao tác **fillInStackTrace()** hoặc **setStackTrace(...)** tiếp theo không bổ sung trace chuẩn; **getStackTrace()** trả mảng rỗng. Hành vi của subclass tự override những method này có thể khác, nên nhận định ở đây dành cho cơ chế chuẩn của Throwable/JDK và class ví dụ.

## 4. Ví dụ Java 17+ hoàn chỉnh: ba cấu hình và một exception tái sử dụng

Chương trình này kiểm chứng bằng dữ liệu rõ ràng, không benchmark bằng thời gian nhỏ dễ nhiễu:

- Một exception **fresh** có trace phản ánh hàm tạo/ném ở lần gọi đó.
- Một exception **stackless** có cause và suppressed, nhưng trace rỗng kể cả sau lời gọi fill/set.
- Một exception **suppression disabled** vẫn có stack trace nhưng không lưu lỗi phụ.
- Một exception **shared** được ném hai lần: cùng object, trace vẫn chỉ về nơi tạo ban đầu và suppressed từ lượt trước tiếp tục tồn tại.

Tạo file **StackTraceTradeoffDemo.java**:

~~~java
import java.io.IOException;

public class StackTraceTradeoffDemo {
    static final class ConfiguredFailure extends RuntimeException {
        ConfiguredFailure(String message, Throwable cause,
                          boolean suppression, boolean writableStackTrace) {
            super(message, cause, suppression, writableStackTrace);
        }
    }

    private static final ConfiguredFailure SHARED = makeShared();

    static ConfiguredFailure makeShared() {
        return new ConfiguredFailure("shared failure", null, true, true);
    }

    static void throwNew() {
        throw new ConfiguredFailure("new failure", null, true, true);
    }

    static void throwShared() {
        throw SHARED;
    }

    static String firstAppFrame(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(StackTraceTradeoffDemo.class.getName())) {
                return frame.getMethodName();
            }
        }
        return "(none)";
    }

    public static void main(String[] args) {
        try {
            throwNew();
        } catch (ConfiguredFailure failure) {
            System.out.println("fresh origin=" + firstAppFrame(failure));
            System.out.println("fresh has frames=" + (failure.getStackTrace().length > 0));
        }

        ConfiguredFailure stackless = new ConfiguredFailure(
                "stackless", new IOException("root"), true, false);
        System.out.println("stackless frames=" + stackless.getStackTrace().length);
        System.out.println("stackless cause=" + stackless.getCause().getMessage());
        stackless.addSuppressed(new IllegalStateException("cleanup"));
        System.out.println("stackless suppressed=" + stackless.getSuppressed().length);
        stackless.fillInStackTrace();
        stackless.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("Demo", "method", "Demo.java", 1)
        });
        System.out.println("stackless after fill/set=" + stackless.getStackTrace().length);

        ConfiguredFailure noSuppression = new ConfiguredFailure(
                "no suppression", null, false, true);
        noSuppression.addSuppressed(new IOException("secondary"));
        System.out.println("suppression disabled=" + noSuppression.getSuppressed().length);
        System.out.println("suppression-off has frames="
                + (noSuppression.getStackTrace().length > 0));

        ConfiguredFailure first = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                throwShared();
            } catch (ConfiguredFailure failure) {
                if (first == null) {
                    first = failure;
                    failure.addSuppressed(new IOException("from attempt 1"));
                }
                System.out.println("reuse " + attempt + " same=" + (first == failure)
                        + " origin=" + firstAppFrame(failure)
                        + " suppressed=" + failure.getSuppressed().length);
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 StackTraceTradeoffDemo.java
java StackTraceTradeoffDemo
~~~

Stdout đã được kiểm tra khi chạy trên Java 17+:

~~~text
fresh origin=throwNew
fresh has frames=true
stackless frames=0
stackless cause=root
stackless suppressed=1
stackless after fill/set=0
suppression disabled=0
suppression-off has frames=true
reuse 1 same=true origin=makeShared suppressed=1
reuse 2 same=true origin=makeShared suppressed=1
~~~

### Diễn giải kết quả

**Fresh origin = throwNew:** constructor được gọi trong phương thức throwNew. Vì đây là một đối tượng mới có writableStackTrace=true, frame ứng dụng đầu tiên được lưu có tên throwNew. Nó giúp người đọc log truy vết nơi exception được tạo.

**Stackless frames = 0:** constructor được gọi với writableStackTrace=false; không có frame tiêu chuẩn để xác định nơi tạo. Tuy nhiên **getCause()** vẫn trả IOException có message **root**; **getSuppressed()** vẫn có một lỗi cleanup. Tắt stack trace của **wrapper** không tự tắt trace của cause/suppressed là các Throwable khác.

**Stackless after fill/set = 0:** với triển khai được thử, gọi fillInStackTrace và setStackTrace không làm exception có trace trở lại khi flag writableStackTrace đã tắt. Vì thế đây không phải tính năng có thể tùy tiện bật/tắt trên cùng object ở một điểm sau đó.

**Suppression disabled = 0:** IOException được đưa vào addSuppressed nhưng không được lưu. Trong khi đó trace vẫn tồn tại. Điều này cho thấy hai cờ của constructor độc lập.

**Reuse origin = makeShared:** SHARED được tạo trong makeShared, không phải trong mỗi lần throwShared. Khi ném lại cùng object, trace không tự cập nhật theo lần ném mới. **Suppressed=1** ở lần thứ hai không phải lỗi cleanup mới; nó là thông tin được thêm từ lần trước và còn tồn tại trên cùng object.

Điểm cuối cùng là một lý do nghiêm trọng để thận trọng với **singleton exception dùng chung giữa nhiều request**.

## 5. writableStackTrace=false thực sự đánh đổi những gì?

Lợi ích tiềm năng của exception stackless:

- Không phải thu thập stack trace đầy đủ theo constructor chuẩn của Throwable.
- Có thể giảm chi phí CPU và một phần áp lực bộ nhớ khi tạo exception ở tần suất rất cao.
- Khi thật sự không cần từng điểm ném, tránh thu thập dữ liệu không được sử dụng.
- Một trace rỗng cũng làm dữ liệu stack của chính exception đó ngắn hơn khi log.

Nhưng chi phí thông tin bị mất:

- Không có frame nguồn để chỉ ra class/method/line tạo exception.
- Khi lỗi được truyền qua nhiều tầng, caller chỉ còn type, message, cause và context bổ sung nếu có.
- Không thể sau đó gọi **fillInStackTrace()** để lấy lại trace vốn đã bị loại bỏ ở thời điểm lỗi ban đầu.
- Việc phân biệt hai nơi khác nhau cùng ném loại exception có thể trở nên khó khăn.
- Công cụ log/APM hoặc hệ thống tổng hợp lỗi có thể nhóm sai nhiều sự cố chỉ còn cùng type và message.
- Nếu exception được bọc ở tầng cao, trace của wrapper cho biết nơi wrap nhưng không tái tạo được điểm gốc của exception stackless.

**Đánh đổi:** Bạn tiết kiệm một phần chi phí phát sinh tại đường lỗi, nhưng mất chứng cứ kỹ thuật có giá trị cao vào chính lúc cần điều tra. Đối với lỗi hiếm hoặc có ảnh hưởng tới dữ liệu, thường không đáng làm.

### Stackless wrapper không đồng nghĩa mọi trace đều rỗng

Trong ví dụ:

~~~java
// Mã minh họa trong một phương thức phù hợp.
RuntimeException stackless =
    new LightweightFailure("operation failed", new IOException("disk error"));
~~~

Đây là minh họa dùng class **LightweightFailure** ở mục 3. Trace của **stackless** rỗng, nhưng IOException **cause** được tạo như bình thường nên có thể có stack trace riêng. Nếu bạn tắt writableStackTrace trên wrapper để giảm overhead, mà vẫn tạo cause dày và in cây exception đầy đủ, lợi ích thực tế sẽ khác so với trường hợp hoàn toàn không có trace.

Ngược lại, nếu cả cause và wrapper đều bị thay thành các đối tượng không ghi dấu vết, lượng thông tin chẩn đoán mất đi càng lớn. Benchmark phải dùng đúng **chuỗi Throwable thực tế** của hệ thống, không chỉ một object mock.

## 6. enableSuppression và writableStackTrace hoàn toàn độc lập

Bốn tổ hợp cấu hình cần được hiểu rõ:

| enableSuppression | writableStackTrace | Kết quả mong đợi trong cơ chế Throwable chuẩn |
| --- | --- | --- |
| true | true | Có thể giữ suppressed và stack trace |
| true | false | Giữ suppressed; stack trace của chính Throwable rỗng |
| false | true | Không lưu lỗi qua addSuppressed; vẫn có stack trace |
| false | false | Không lưu suppressed và không giữ stack trace |

**Suppressed exception** đặc biệt quan trọng khi dùng **try-with-resources**. Nếu thân try ném lỗi A và **close()** ném lỗi B, cơ chế chuẩn sẽ ưu tiên A, thêm B vào A.getSuppressed() khi A hỗ trợ suppression.

Nếu **A** là một Throwable có **enableSuppression=false**, lỗi B không được giữ bằng **addSuppressed()** trên A. Khi chỉ xem stack trace/cause của A, bạn có thể **mất dấu vết lỗi cleanup**. Đây không chỉ là vấn đề “ít thông tin log” mà có thể che dấu kết quả thu hồi tài nguyên, rollback hoặc close đã thất bại.

Vì vậy, **không nên tắt suppression chỉ để giảm thêm chút overhead** khi API còn có khả năng dùng try-with-resources hoặc bổ sung lỗi phụ. Nếu lỗi phụ có ý nghĩa nghiệp vụ, giữ suppression là yêu cầu quan sát thiết yếu.

**Cách đọc code:** Ở constructor **super(message, cause, suppression, writable)**, boolean đầu nói về suppressed, boolean sau nói về stack trace. Viết tên tham số rõ ràng và dùng factory có tên là cách giảm nhầm lẫn thứ tự.

## 7. Tái sử dụng cùng đối tượng exception khác với tái sử dụng một loại exception

Hai mẫu dễ bị đánh đồng:

~~~java
// Mã minh họa, phương thức được đặt trong class thích hợp.
throw new IllegalStateException("invalid state");
~~~

Mỗi lần chạy tạo **một Throwable mới**, thường có stack trace gắn với lần tạo đó.

~~~java
// Mã minh họa, biến SHARED đã được tạo trước.
throw SHARED;
~~~

Mỗi lần ném dùng **cùng một Throwable đã tồn tại**. Tham chiếu, message, cause, suppressed list và trace đều thuộc về **object đó**. Từ khóa throw **không tạo bản sao Throwable** và không ghi trace mới tự động cho mỗi lần ném.

Có thể giảm cấp phát nhờ reuse, nhưng đổi lại:

1. Stack trace phản ánh nơi tạo object lần đầu, không phản ánh từng lần lỗi.
2. Message cố định khó chứa ngữ cảnh request cụ thể.
3. Cause có thể không liên quan lần ném mới hoặc không được đổi an toàn.
4. Suppressed exceptions có thể tích lũy từ nhiều lần xử lý.
5. Nhiều thread có thể cùng nhìn thấy/chỉnh một exception duy nhất.
6. Các công cụ chẩn đoán, logging và tính năng nhóm lỗi có thể không phân biệt được từng sự kiện.

**Đặc biệt nguy hiểm:** Reuse một exception thông thường **có stack trace** nhưng tưởng trace sẽ tự cập nhật mỗi lần throw. Đó là cách tạo ra một lỗi vừa có dấu vết sai lệch, vừa trông có vẻ rất đáng tin.

## 8. Vì sao không gọi fillInStackTrace() trên exception dùng chung mỗi lần ném?

Có thể gặp ý tưởng:

~~~java
// Phản mẫu minh họa, SHARED là cùng một Throwable mutable.
SHARED.fillInStackTrace();
throw SHARED;
~~~

Điều này không còn giữ được lợi ích “không thu thập trace”: **fillInStackTrace()** làm công việc ghi nhận stack mới. Trong môi trường nhiều luồng, hai request có thể cùng chỉnh stack trace của một object. Lỗi của request A có thể bị ghi lại stack bởi request B trước khi A kịp in log hoặc thu thập dấu vết.

Ngoài ra, **fillInStackTrace()** không xóa suppressed exceptions, không tự đổi cause và không tạo một bản sao độc lập cho từng lần throw. Việc “làm mới stack” không khắc phục bản chất **chia sẻ trạng thái mutable** của Throwable.

Nếu muốn trace đúng cho từng sự cố, cách bình thường và rõ ràng nhất là **tạo một exception riêng cho sự cố đó**. Nếu thật sự đã quyết định không cần trace, một loại exception stackless mới mỗi lần cũng có thể phù hợp hơn singleton chia sẻ, vì vẫn giữ được sự độc lập của message/cause/suppressed.

**Ngoại lệ với writableStackTrace=false:** Với cơ chế Throwable chuẩn, fillInStackTrace bị vô hiệu hóa và không thể dùng để lấy lại trace; đây không phải phương pháp chữa mất dữ liệu sau khi đã tắt.

## 9. Cause, suppressed và message tạo rủi ro khi chia sẻ

Các trường liên quan đến Throwable không nên được giả định là “read-only hoàn toàn” chỉ vì code của bạn không có setter message:

- Một số Throwable có thể cấu hình **cause** khi khởi tạo hoặc qua **initCause()** theo ràng buộc của JDK.
- **addSuppressed()** bổ sung một exception mới vào danh sách suppressed nếu suppression được bật.
- **setStackTrace()** thay đổi biểu diễn stack trace đối với Throwable có writableStackTrace phù hợp.
- **fillInStackTrace()** có thể thay đổi dấu vết theo lần gọi.
- Subclass tùy chỉnh còn có thể có những field mutable riêng.

Ví dụ, SHARED ở phần 4 được tạo với **suppression=true**. Sau lượt xử lý thứ nhất, catch gắn IOException “from attempt 1”. Trong lượt hai, **getSuppressed()** vẫn trả IOException đó, dù việc xử lý lần hai không gặp lỗi close tương ứng.

Điều này có thể dẫn đến:

- Log request thứ hai chứa lỗi cleanup của request thứ nhất.
- Thông tin nhạy cảm từ một tenant/request bị đưa vào log của tenant/request khác.
- Bộ đếm số lỗi phụ bị phình theo số lượt sử dụng.
- Công cụ quan sát báo nguyên nhân lỗi sai.
- Tránh được object allocation nhỏ nhưng gây khó hiểu lâu dài cho vận hành.

Dù **addSuppressed()** có những bảo vệ nội bộ của JDK để quản lý danh sách, điều đó **không biến một Throwable dùng chung thành đại diện đúng cho nhiều sự cố độc lập**. Tính đúng đắn nghiệp vụ và khả năng phân biệt từng lần lỗi là một vấn đề khác với việc một method có được đồng bộ nội bộ hay không.

## 10. Trường hợp đồng thời: cùng Throwable ở nhiều thread

Giả sử hai request A và B cùng dùng một singleton exception:

~~~java
// Mã minh họa; một shared exception được giữ ở field static.
static final RuntimeException REJECTED =
        new RuntimeException("rejected");

static void reject() {
    throw REJECTED;
}
~~~

Khi A và B đều gọi reject(), chúng cùng nhận **cùng một object**. Nếu một nhánh bắt A gọi addSuppressed, setStackTrace hoặc fillInStackTrace, nhánh B có thể quan sát các thay đổi đó. Nhiều logger ghi object Throwable ở thời điểm khác nhau cũng có thể tạo kết quả khác nhau.

Với một singleton stackless và suppression bị tắt, nguy cơ tích lũy dữ liệu giảm đi vì hai cơ chế đã bị vô hiệu hóa, nhưng vẫn có những vấn đề:

- Không có stack trace cho từng sự kiện.
- Cùng cause và message không mô tả từng request.
- Không thể gắn context riêng bằng suppressed.
- Một subclass có field mutable vẫn có thể chia sẻ trạng thái.
- Những logic dùng object identity để nhóm hoặc đếm exception có thể nhầm nhiều sự kiện là một.

Do đó **singleton stackless exception** chỉ có thể là một lựa chọn chuyên biệt khi mọi lần ném có **cùng ý nghĩa, không cần ngữ cảnh per-request**, không được chia sẻ dữ liệu mutable bổ sung và đã có quan sát khác. Với lỗi dữ liệu quan trọng, nên ưu tiên exception riêng cho từng thất bại.

## 11. Exception là điều kiện bất thường hay cơ chế điều khiển luồng?

Một câu hỏi về hiệu năng còn quan trọng hơn việc tắt trace: **vì sao exception xuất hiện với tần suất cao?**

Nếu code dùng exception để đi qua một nhánh xử lý được dự kiến xảy ra hàng triệu lần, hệ thống đang phải trả chi phí throw/catch và có thể cả trace để thực hiện điều mà một kết quả kiểm tra bình thường làm được.

Ví dụ phản mẫu khi đầu vào thường xuyên không phải số:

~~~java
// Mã minh họa trong một hàm đã có text.
int parseOrZero(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException ignored) {
        return 0;
    }
}
~~~

Đoạn này biên dịch nếu đặt trong class phù hợp. Nó có thể hợp lý trong ứng dụng nhỏ, nhưng nếu dữ liệu không hợp lệ là trường hợp **rất phổ biến trong hot path** thì tạo exception liên tục có thể đáng đo. Đừng lập tức tìm cách sửa thành “NumberFormatException không stack trace”, vì exception đó do JDK tạo và bạn không kiểm soát constructor của nó.

Các hướng cải thiện đúng hơn tùy bài toán:

1. Dùng parser trả **trạng thái/giá trị** thay vì ném exception cho trường hợp dự kiến.
2. Validate trước những điều kiện có thể kiểm tra rẻ, nhưng phải tránh tái tạo toàn bộ parser hai lần gây chi phí lớn hơn.
3. Phân loại đầu vào không hợp lệ thành một kiểu kết quả rõ ràng, chẳng hạn Optional, Result hoặc mã lỗi.
4. Chỉ dùng exception khi **hợp đồng không thể hoàn tất bình thường** hoặc lỗi thực sự bất thường.
5. Đo lại hiệu năng và độ phức tạp sau khi đổi.

**Chú ý:** Kiểm tra trước không bảo đảm luôn nhanh hơn parse trực tiếp. Nếu trường hợp lỗi rất hiếm, thêm một lượt validation có thể làm đường thành công chậm hơn. Quyết định dựa vào **tần suất thực tế của lỗi** và benchmark có cấu hình phù hợp.

## 12. Nơi có thể cân nhắc exception stackless

Không phải mọi ứng dụng dùng stackless exception đều sai. Một số thư viện hiệu năng cao dùng tín hiệu nội bộ đã biết trước, tần suất lớn, hoặc dùng API để truyền một tình trạng kết thúc đã được dự đoán. Tuy nhiên, cần kiểm tra phạm vi cụ thể:

| Tình huống | Có thể cân nhắc? | Điều kiện bắt buộc |
| --- | --- | --- |
| Trạng thái nội bộ thuần túy, không có dữ liệu per-request, đã đo là hot path | Có, thận trọng | Sự kiện được hiểu rõ, quan sát khác đầy đủ |
| Lỗi parse đầu vào người dùng cần ghi vị trí nguồn | Thường không | Giữ trace, type, message/cause đủ điều tra |
| Exception báo bug vi phạm bất biến không dự đoán trước | Không nên | Trace là dữ liệu cốt lõi để sửa bug |
| Lỗi database/network không ổn định | Thường không | Cần trace, cause, retry context, idempotency |
| Cơ chế dừng nội bộ không được truyền qua API công khai | Có thể | Được đóng gói, tài liệu hóa, có test đầy đủ |
| Singleton exception trong server nhiều tenant | Rủi ro cao | Không rò dữ liệu giữa các request, không dùng suppressed/context |
| Exception dùng trong hot loop làm điều kiện bình thường | Ưu tiên đổi thiết kế | Dùng return/result thay vì throw cho đường dự kiến |

Khi áp dụng stackless cho một API công khai, người sử dụng thư viện có thể không biết bạn đã bỏ stack trace, dẫn đến điều tra lỗi khó hơn. Một quyết định tối ưu ở thư viện có thể đẩy chi phí xử lý sự cố cho nhiều hệ thống downstream. Điều này cần được mô tả rõ trong contract hoặc tài liệu vận hành.

## 13. Phân biệt tối ưu loại exception và tối ưu từng instance

Có ít nhất ba phương án khác nhau:

**A. Exception bình thường, tạo mới:**

~~~java
// Mã minh họa.
throw new IllegalStateException("config changed unexpectedly");
~~~

Ưu tiên cho lỗi thực sự bất thường; có trace đúng, message có thể mô tả tình huống.

**B. Exception stackless, tạo mới:**

~~~java
// Mã minh họa; LightweightFailure từ mục 3.
throw new LightweightFailure("operation not permitted", cause);
~~~

Vẫn cấp phát một object cho sự kiện mới, nhưng bỏ thu thập trace cho object đó. Cause riêng và suppressed riêng có thể giữ ngữ cảnh. Phương án này không phải singleton và ít rủi ro “trộn request” hơn.

**C. Một exception duy nhất tái sử dụng:**

~~~java
// Mã minh họa; SHARED đã được định nghĩa trong class.
throw SHARED;
~~~

Có thể tránh cấp phát trên mỗi lần ném, nhưng trace/message/cause/suppressed thuộc về cùng object. Nếu SHARED có stack trace, vị trí trong trace không được cập nhật tự động; nếu SHARED stackless, không còn vị trí nào trong trace chính để xác định lần ném.

Nên chọn A làm mặc định. Nếu A có vấn đề đã đo, cân nhắc đổi luồng xử lý hoặc B trước khi nghĩ tới C. Không có bảo đảm B luôn đủ nhanh hay C luôn nhanh nhất, bởi chi phí phụ thuộc JVM, compiler, dạng exception, catch path và workload.

## 14. Những hiểu nhầm về fillInStackTrace()

**Throwable.fillInStackTrace()** là method có thể được gọi hoặc override. Nó không phải một lời hứa rằng mọi exception đều luôn có thể tái tạo trace của lần ném trong quá khứ.

Với đối tượng thông thường:

~~~java
// Mã minh họa; failure là Throwable có writableStackTrace=true.
failure.fillInStackTrace();
~~~

Lời gọi làm mới stack trace theo vị trí **đang gọi fillInStackTrace**, không quay về một thời điểm cũ mà JVM chưa ghi lại. Nếu gọi từ catch/logging ở tầng cao, trace mới có thể chỉ ra tầng cao đó và mất vị trí lỗi ban đầu.

Với writableStackTrace=false, **fillInStackTrace()** theo triển khai chuẩn không bổ sung trace; điều này đã được thử trong ví dụ Java 17 mục 4.

Cũng không nên override **fillInStackTrace()** chỉ để trả **this** trên mọi custom exception mà không đánh giá tác động:

~~~java
// Mã minh họa một kiểu custom exception chuyên dụng.
final class NoTraceFailure extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
~~~

Đây là phương pháp tối ưu từng được sử dụng, nhưng có những khác biệt về thời điểm constructor gọi method có thể override, inheritance, hành vi stack trace và khả năng bảo trì. Constructor có **writableStackTrace=false** thể hiện ý định rõ hơn khi phù hợp với loại Throwable bạn kiểm soát. Tránh xem override này là công thức tối ưu bắt buộc cho mọi exception class.

## 15. Đo hiệu năng đúng cách trước khi đưa ra quyết định

Một phép đo ngắn bằng **System.nanoTime()** bao quanh một vòng lặp throw/catch không đủ để kết luận việc tắt trace hay singleton exception luôn tốt hơn. JIT, escape analysis, class initialization, tiered compilation, CPU scheduling, GC và logging có thể làm các lần chạy khác nhau.

Cách tiếp cận chuẩn hơn là benchmark có khởi động và kiểm soát workload, thường dùng **JMH**. Nếu không thể dùng JMH, chỉ nên coi kết quả đo đơn giản là thông tin sơ bộ, không phải cơ sở quyết định kiến trúc.

Benchmark cần tách các phương án:

1. **Điều kiện bình thường trả status/boolean**.
2. **Tạo mới exception bình thường**, ném và bắt.
3. **Tạo mới custom exception stackless**, ném và bắt.
4. **Tái sử dụng exception bình thường**, ném và bắt.
5. **Tái sử dụng exception stackless**, ném và bắt.

Đồng thời nên có hai nhóm workload:

- **Đường thành công phổ biến:** có thể không tạo exception nào.
- **Đường lỗi theo tần suất thực tế:** ví dụ lỗi 0,01%, 1% hoặc 50% tùy số đo thực.

### Những chỉ số nên theo dõi

| Chỉ số | Vì sao quan trọng? |
| --- | --- |
| Throughput/operations per second | Năng lực xử lý tổng thể của đường mã |
| p50/p95/p99 latency | Mức độ biến động và độ trễ đuôi |
| Allocation bytes/op | Số byte cấp phát có liên quan tới GC |
| GC collections/time | Áp lực thu hồi bộ nhớ |
| CPU sampling/profiling | Constructor, fillInStackTrace, formatting/logging có thật là nút thắt không |
| Error rate và error types | Tần suất exception theo loại và nguồn |
| Chi phí observability | Stack logging, gửi dữ liệu APM, storage/network |
| Khả năng chẩn đoán sự cố | Có xác định được class/method/line và request liên quan không |

Nên đo trên cùng JDK, cùng cấu hình heap/GC, cùng kiểu build, input, mức tải và môi trường gần production. **Không đưa một con số “nhanh hơn X lần” vào tài liệu nếu chưa có benchmark áp dụng đúng ứng dụng**, bởi chi phí thu thập trace và tác động tối ưu phụ thuộc môi trường.

### Đo riêng việc tạo exception và in log

Nếu benchmark chỉ ném và bắt mà không in stack trace, nó có thể bỏ qua chi phí log thực tế. Ngược lại, nếu benchmark ghi toàn bộ stack vào console trong vòng lặp, chi phí I/O dễ áp đảo chi phí Throwable constructor. Cả hai đều có giá trị khi được đặt đúng câu hỏi, nhưng không thể thay thế nhau.

Đối với phương án stackless, benchmark cũng phải xét rằng thiếu trace có thể làm debugging hoặc grouping trong APM trở nên khó hơn. Chi phí vận hành khi một lỗi hiếm khó tái hiện có thể lớn hơn phần tiết kiệm nanosecond trong đường nóng.

## 16. Tác động của JVM, JIT và các tối ưu nội bộ

Không nên khẳng định cấu tạo stack trace có cùng chi phí trên mọi bản Java. HotSpot/JVM có thể sử dụng những tối ưu, biểu diễn nội bộ hoặc đường ném nhanh khác nhau. Một số exception do JVM tạo lặp rất nhiều trong tình huống đặc biệt có thể được tối ưu theo chính sách triển khai, khiến stack trace quan sát khác với các exception do Java code tạo ra.

Vì vậy cần lưu ý:

- Chi phí **new RuntimeException(...)** do mã ứng dụng tạo không đại diện cho tất cả exception JVM sinh ra.
- Một kết quả benchmark trên một phiên bản JDK không chắc chuyển nguyên sang phiên bản khác.
- Tối ưu liên quan tới JIT/hot throw sites có thể khác giữa code đang warm-up và code đã ổn định.
- Một thông điệp hoặc trace thiếu trong điều kiện đặc biệt không chứng minh Throwable nói chung luôn tắt trace.
- Flags JVM liên quan tới tối ưu exception là yếu tố triển khai; không nên dùng chúng như biện pháp thay thế cho thiết kế exception contract rõ ràng.

Nếu nghi JVM đã tối ưu mất một số stack trace trên đường lỗi cực nóng, đối chiếu phiên bản JDK, cờ runtime, số lần tái hiện, cách exception được tạo và log. Không mặc định đó là bằng chứng log pipeline hoặc JDK bị lỗi.

## 17. Stackless exception và logging/APM

Phần mềm quan sát lỗi thường dựa vào tổ hợp:

- Exception class và message.
- Top application frame.
- Full stack trace.
- Cause/suppressed chain.
- Tên service, operation, request ID/trace ID.
- Thread/async context và phiên bản deploy.

Khi writableStackTrace=false, **top application frame trên chính exception không tồn tại**. Công cụ nhóm lỗi chỉ theo loại và message có thể:

- Gộp những lỗi khác nhau vào một nhóm.
- Tăng false-positive khi cảnh báo chung.
- Không biết code path nào phát sinh thường nhất.
- Khiến việc điều tra sau deploy kém chính xác.
- Khó chứng minh một bản sửa đã loại bỏ đúng nơi ném lỗi.

Nếu bắt buộc dùng stackless, cần thay thế bằng metadata **được thiết kế trước**, ví dụ error code, event name, operation identifier, correlation ID và structured log riêng. Các metadata này thường nhẹ hơn full stack trace nhưng **không tương đương** một stack trace thực tế; chúng phụ thuộc vào việc mã tác giả ghi đúng nơi và đúng ngữ cảnh.

**Không đưa raw input nhạy cảm vào message** chỉ để bù stack trace. Dữ liệu trong lỗi và log vẫn phải được bảo vệ, đặc biệt khi exception singleton có thể bị dùng qua nhiều request/tenant.

## 18. Khi cần tạo một exception mới thay vì tái sử dụng

Ưu tiên object mới khi có bất kỳ đặc điểm sau:

- Mỗi thất bại cần message chứa ID hoặc trạng thái riêng.
- Nguyên nhân thực sự khác nhau và cần lưu cause chính xác.
- Có thể phát sinh suppressed exception khi đóng tài nguyên.
- Hai request/threads có thể thất bại đồng thời.
- Cần stack trace đúng vị trí gọi.
- Code phía ngoài có thể gọi **initCause**, **setStackTrace**, **fillInStackTrace** hoặc **addSuppressed**.
- Trạng thái lỗi cần được serialize, retry hoặc đưa qua ranh giới service với ngữ cảnh riêng.
- Lỗi thể hiện vi phạm invariant, bug hoặc thất bại có tác động tới dữ liệu.

Nếu performance thực sự quan trọng, một **fresh stackless custom exception** có thể là giải pháp trung gian: mỗi sự kiện vẫn có identity/cause/suppressed riêng, nhưng bỏ trace chính. Nếu cần trace theo điều kiện, có thể cân nhắc lấy mẫu chỉ một tỷ lệ lỗi có stack trace và giữ metrics tổng thể, song phải thiết kế sampling rõ ràng và tránh đánh mất các sự cố hiếm nghiêm trọng.

Không có một API Throwable tiêu chuẩn nào tự bảo đảm “cứ mỗi 100 lần ném thì ghi trace một lần”. Đây phải là chính sách ứng dụng, được test cùng hiệu năng và khả năng quan sát.

## 19. Một số lỗi dễ mắc với class stackless tùy chỉnh

**Nhầm thứ tự hai boolean constructor.** **super(message, cause, false, true)** tắt suppression nhưng vẫn bật trace, không phải điều ngược lại. Đặt tên tham số/constructor rõ ràng.

**Tưởng stackless cũng mất cause.** Cause là mối liên hệ riêng; **getCause()** có thể vẫn dùng được. Điều cần kiểm tra là đối tượng cause đã được tạo như thế nào.

**Tưởng tắt trace thì suppression tự tắt.** Hai lựa chọn độc lập. Tắt suppression có thể làm mất lỗi cleanup quan trọng.

**Tưởng ném lại cùng exception sẽ tự ghi vị trí mới.** Stack trace thường phản ánh lúc tạo object. Throw không đồng nghĩa fillInStackTrace.

**Tạo singleton có trace rồi dùng trên mọi request.** Log có thể luôn trỏ vào constructor/factory khởi tạo, không phải đường lỗi hiện tại.

**Gọi fillInStackTrace trên singleton chia sẻ.** Có thể triệt tiêu lợi ích hiệu năng và tạo race trong thông tin debug.

**Dùng same-object singleton kèm addSuppressed.** Lỗi cleanup của nhiều lần xử lý có thể bị trộn và lộ dữ liệu giữa các bối cảnh.

**Chỉ benchmark lỗi mà không tính đường thành công.** Nếu lỗi thực tế hiếm, tối ưu đường exception có thể không thay đổi đáng kể latency tổng thể.

**Bỏ stack trace trước khi có cách thay thế.** Khi sự cố hiếm xảy ra, đội vận hành chỉ còn message chung và không thể tìm throw site.

**Coi microbenchmark trên console là kết luận production.** Logging I/O và warm-up có thể lấn át các chi phí cần đo.

**Dùng exception làm cờ vòng lặp.** Nếu một tình huống xảy ra thường xuyên và không phải lỗi, nên cân nhắc API trả kết quả thay vì liên tục ném.

**Tối ưu khi chưa có số liệu.** Nếu hotspot không nằm ở Throwable constructor/stack capture, việc bỏ trace chỉ làm mất khả năng debug mà không đem lại lợi ích đáng kể.

## 20. Testing: đúng semantics trước khi đo tốc độ

Trước khi dùng custom stackless hay reused exception, viết test cho các hợp đồng sau:

| Kịch bản | Điều cần xác minh |
| --- | --- |
| Fresh exception bình thường | Có frame tương ứng với nơi tạo |
| Stackless exception | getStackTrace() rỗng |
| Stackless có cause | getCause() vẫn giữ cause theo thiết kế |
| Stackless có suppression=true | addSuppressed lưu lỗi phụ |
| Suppression=false, trace=true | getSuppressed() rỗng dù addSuppressed được gọi; trace vẫn có |
| Gọi fill/set trên stackless | Trace vẫn rỗng theo cơ chế Throwable chuẩn |
| Ném lại shared exception | Cùng object identity, trace không thay theo lần ném |
| Shared exception có suppressed | Lần sau vẫn thấy lỗi từ lần trước; test phát hiện leak |
| Nhiều thread sử dụng shared | Không được mặc định thông tin lỗi thuộc về từng request |
| Caller wrap exception | Cause/suppressed được truy vết đúng, không mất lỗi gốc |

Trong ví dụ chính, phép so **first == failure** khẳng định object reused. **firstAppFrame** chỉ đọc những frame thuộc class ứng dụng, giúp quan sát vị trí tạo mà không phụ thuộc số dòng JDK.

Không nên viết test cố định thời gian “tạo exception phải dưới X nanosecond” trên CI thông thường. Để kiểm tra tốc độ, dùng benchmark có điều kiện môi trường được kiểm soát và báo cáo phạm vi sai số. Unit test tập trung vào **correctness của API và thông tin lỗi**.

### Kiểm tra log thật

Nếu ứng dụng có formatter hoặc APM, tạo một tình huống lỗi test và xác minh:

1. Stackless object được biểu diễn đúng, không làm formatter crash vì trace rỗng.
2. Cause và suppressed của nó vẫn được hiển thị nếu tồn tại.
3. Lỗi bình thường vẫn có frame/dòng nguồn đủ điều tra.
4. Exception shared (nếu có) không bị đưa vào log kèm context của request khác.
5. Các dữ liệu nhạy cảm trong message/cause/suppressed được che theo chính sách.

## 21. Quy trình ra quyết định cho hot path

Một quyết định kỹ thuật hợp lý nên đi theo trình tự:

**Bước 1 — Xác định exception có phải đường xử lý bình thường?** Nếu đúng, cân nhắc thiết kế lại API trả trạng thái/giá trị trước khi tối ưu Throwable.

**Bước 2 — Đo tần suất exception thực tế.** Thu thập số lượng theo loại, request, endpoint, phiên bản, tải và thời gian. Nếu chỉ xảy ra rất ít, overhead có thể không đáng kể.

**Bước 3 — Profile đúng phần tốn chi phí.** Là khởi tạo Throwable, thu stack, xử lý catch, format log, I/O ghi log hay GC? Giải pháp phải nhắm vào nguyên nhân đo được.

**Bước 4 — Chọn thay đổi ít làm mất thông tin nhất.** Ví dụ giảm log trùng, giới hạn logging quá nhiều, thay exception bằng kết quả bình thường, hoặc tạo mới exception stackless chuyên dụng thay vì dùng singleton.

**Bước 5 — Giữ chính sách chẩn đoán.** Nếu cần stackless, bổ sung error code, operation, version và correlation ID, cùng đường bật log/tracing khi cần.

**Bước 6 — Test correctness.** Xác minh cause, suppressed, trace policy, thread safety, không rò dữ liệu và semantics khi throw lại.

**Bước 7 — Benchmark.** Dùng workload gần thực tế, cùng JDK/GC và độ tải, xem cả throughput, latency, allocation, logging và các đường lỗi.

**Bước 8 — Triển khai có kiểm soát.** Dùng rollout, metric và khả năng quay về trace đầy đủ khi phát hiện sự cố. Một thay đổi nhỏ trong Throwable có thể có ảnh hưởng lớn tới khả năng vận hành.

## 22. Câu hỏi thực hành và cách suy luận

**Câu hỏi 1: Nếu stack trace của một exception rỗng, chắc chắn đã dùng writableStackTrace=false?**

Không. Có thể là Throwable được tùy biến, stack trace bị cấu hình lại, một tối ưu JVM hoặc logging chỉ hiển thị thiếu dữ liệu. Hãy xem code tạo exception, kiểu JVM và trạng thái object thật trước khi kết luận.

**Câu hỏi 2: Có thể gọi fillInStackTrace() để khôi phục trace đã bỏ không?**

Không thể khôi phục chính xác một call stack quá khứ mà chưa được lưu lại. Với writableStackTrace=false trên Throwable chuẩn, fillInStackTrace không bổ sung trace. Với Throwable thông thường, gọi method sẽ thu nhận stack của **thời điểm gọi mới**, có thể không phải nơi lỗi gốc.

**Câu hỏi 3: Nếu suppression bị tắt, try-with-resources còn đóng resource không?**

Có. Việc gọi close và việc **lưu exception cleanup vào suppressed** là hai chuyện khác nhau. Khi lỗi chính có suppression=false, lỗi từ close có thể không được lưu trên nó, nhưng cơ chế đóng tài nguyên vẫn được thực hiện theo đường thoát.

**Câu hỏi 4: Reuse exception có phải luôn tạo kết quả sai?**

Không phải mọi trường hợp đều sai. Một số cơ chế nội bộ dùng singleton exception chỉ để báo một trạng thái dự kiến, không có thông tin per-event cần lưu. Tuy nhiên, kiểu API này phải được thiết kế có chủ đích, không chứa trạng thái dùng chung có thể bị sửa sai và phải có phương thức quan sát thích hợp. Không dùng nó làm mặc định cho lỗi cần chẩn đoán.

**Câu hỏi 5: Tại sao shared exception còn suppressed từ lần trước?**

Vì **addSuppressed()** cập nhật chính đối tượng Throwable đang được chia sẻ. Khi ném lại, object đó không được tạo lại nên danh sách suppressed của nó cũng không tự làm mới.

**Câu hỏi 6: Khi bật writableStackTrace=false trên wrapper, cause có còn stack trace không?**

Có thể có, nếu cause được xây dựng bình thường. Cấu hình stack trace của wrapper không tự áp lên mọi Throwable mà nó tham chiếu.

**Câu hỏi 7: Vì sao chưa nên áp dụng ngay cho mọi IllegalArgumentException?**

IllegalArgumentException thường cung cấp bằng chứng rằng caller vi phạm hợp đồng API. Bỏ trace có thể khiến bạn không biết caller nào đưa input sai. Nếu số lần lỗi lớn, trước tiên tìm và sửa caller hoặc validation trước khi loại bỏ thông tin chẩn đoán.

## 23. Checklist trước khi chấp nhận tối ưu

1. **Tính cần thiết:** Đã có profile chứng minh Throwable construction/stack trace nằm trên hot path chưa?
2. **Tần suất:** Exception đang xảy ra bao nhiêu lần, thuộc đường lỗi hiếm hay trường hợp bình thường?
3. **Phạm vi:** Custom exception chỉ dùng nội bộ hay bị lộ qua API cho hệ thống khác?
4. **Cấu hình:** Đã phân biệt enableSuppression với writableStackTrace theo thứ tự constructor chưa?
5. **Trace:** Người dùng còn cần class/method/line theo từng sự cố không?
6. **Cause:** Có giữ nguyên cause thích hợp cho từng lần lỗi không?
7. **Suppressed:** Có thể xảy ra lỗi cleanup không, và có bắt buộc giữ thông tin ấy không?
8. **Object identity:** Có đang reuse cùng Throwable qua nhiều request/thread không?
9. **Thread safety:** Có nguy cơ fillInStackTrace/setStackTrace/addSuppressed làm trộn thông tin không?
10. **Observability:** Có error code, request ID, operation và logging thay thế thông tin trace mất đi không?
11. **Benchmark:** Đã đo trên cùng JDK/workload và tách chi phí tạo exception, throw/catch, format/log, GC chưa?
12. **Tests:** Đã thử stackless, suppressed, cause, reuse và trường hợp lỗi kép chưa?
13. **Rollout:** Có thể bật lại full trace khi cần điều tra sự cố khó tái hiện không?
14. **Bảo mật:** Có kiểm tra ngữ cảnh Throwable dùng chung không làm lộ thông tin giữa user/tenant không?

**Kết luận:** Tắt **writableStackTrace** là đánh đổi **chi phí thu thập stack** lấy **mất dấu vị trí phát sinh lỗi**. Tái sử dụng Throwable còn đánh đổi nhiều hơn: dùng cùng object, cause/message/trace/suppressed qua những sự kiện khác nhau, làm thông tin chẩn đoán dễ sai lệch hoặc trộn giữa request. Hai kỹ thuật không phải lựa chọn tối ưu mặc định. Trong phần lớn mã ứng dụng, hãy **giữ exception bình thường**, sửa nguyên nhân khiến exception xuất hiện quá thường xuyên, và chỉ dùng stackless hoặc reuse sau khi đã đo được lợi ích và xác định được cách bảo toàn đúng nghĩa lỗi, trạng thái và khả năng vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-007">Rủi ro khi dùng chung ngoại lệ có thông tin nguyên nhân hoặc lỗi phụ thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro khi dùng chung ngoại lệ có thông tin nguyên nhân hoặc lỗi phụ thay đổi

Trong Java, một ngoại lệ (`Throwable`) không chỉ là tên lớp và thông điệp. Nó còn có thể mang **nguyên nhân** (`cause`), **ngoại lệ bị đính kèm** (`suppressed exceptions`) và **stack trace**. Những thông tin ấy giúp chẩn đoán sự cố, nhưng cũng khiến một đối tượng ngoại lệ trở thành nơi chứa **trạng thái thay đổi được**. Nếu tái sử dụng *cùng một đối tượng ngoại lệ* cho nhiều yêu cầu, nhiều lần thử hoặc nhiều luồng, dữ liệu của sự cố này có thể lẫn vào sự cố khác.

Ví dụ, request A và request B cùng sử dụng một `static final RuntimeException`. Khi A thêm ngoại lệ đóng tài nguyên vào `addSuppressed(...)` còn B ghi một lỗi đóng tài nguyên khác lên **cùng đối tượng**, log của A hoặc B có thể xuất hiện cả hai lỗi. Nếu một luồng gọi `fillInStackTrace()` để “cập nhật” vị trí ném, stack trace chung có thể đổi ngay trước lúc luồng khác ghi log. Dù các thao tác đơn lẻ có thể có bảo vệ đồng bộ nội bộ, **quyền sở hữu ngữ cảnh lỗi giữa các tác vụ vẫn sai**.

Bài này giải thích trạng thái nào của `Throwable` có thể thay đổi, `initCause()` giới hạn việc gán cause ra sao, vì sao suppressed exceptions dễ bị tích tụ, tác động của chia sẻ giữa các luồng, và cách cô lập lỗi mà vẫn cân nhắc hiệu năng.

## 1. Mô hình tư duy: một lần thất bại nên có ngữ cảnh lỗi riêng

Một ngoại lệ dùng để báo một sự cố cụ thể thường liên hệ các thông tin:

- **Lỗi chính**: thao tác nào thất bại và thông điệp phù hợp với sự cố đó.
- **Nguyên nhân** (*cause*): đối tượng ngoại lệ ở tầng thấp gây ra hoặc được dùng làm nguyên nhân cho lỗi chính.
- **Lỗi phụ** (*suppressed exceptions*): những lỗi bổ sung cần được giữ lại, chẳng hạn `close()` thất bại sau khi thân `try` đã ném lỗi chính.
- **Stack trace**: vị trí ghi nhận ngăn xếp lời gọi của đối tượng ngoại lệ, thường từ lúc khởi tạo.
- **Ngữ cảnh quan sát**: mã yêu cầu, tác vụ, thời điểm và logger xử lý, đôi khi nằm ngoài `Throwable`.

Hai yêu cầu độc lập dù cùng thất bại vì một loại lỗi cũng nên có **hai bản ghi sự cố phân biệt được**. Cùng loại ngoại lệ không có nghĩa phải dùng cùng đối tượng.

```text
Request A
  -> Exception A
       cause      -> IOException A
       suppressed -> CleanupException A
       stack      -> vị trí xảy ra A

Request B
  -> Exception B
       cause      -> IOException B
       suppressed -> CleanupException B
       stack      -> vị trí xảy ra B
```

Nếu dùng chung `Exception S`:

```text
Request A ------+
                +--> Exception S
Request B ------+       |
                        +--> cause: chỉ một nguyên nhân được gán
                        +--> suppressed: lỗi phụ của cả A lẫn B
                        +--> stack: vị trí tạo chung hoặc vị trí được ghi lại sau
```

Khi đọc một bản ghi log, ta không còn biết chắc dữ liệu nào thực sự thuộc về sự cố đang được báo nếu ứng dụng liên tục thêm hoặc chỉnh sửa thông tin chung.

## 2. `Throwable` chứa những phần trạng thái nào?

| Thuộc tính | API liên quan | Có thể thay đổi theo cách nào? | Rủi ro khi chia sẻ |
| --- | --- | --- | --- |
| Message | `getMessage()` | Thông điệp của lớp `Throwable` thường được đặt ở constructor; lớp con có thể cung cấp trạng thái riêng | Một thông điệp cố định không mô tả từng yêu cầu |
| Cause | `getCause()`, `initCause(Throwable)` | Nếu chưa được khởi tạo, `initCause` cho phép gán **một lần** | Luồng/request đầu tiên chiếm quyền gán; phần còn lại không thể đặt nguyên nhân của mình |
| Suppressed | `addSuppressed(Throwable)`, `getSuppressed()` | Có thể thêm nhiều ngoại lệ phụ khi suppression được bật | Danh sách tiếp tục tích lũy lỗi từ các sự cố khác nhau |
| Stack trace | `fillInStackTrace()`, `getStackTrace()`, `setStackTrace(...)` | Có thể được ghi lại hoặc thay đổi nếu writable stack trace được bật | Stack được thấy tại lúc ghi log có thể không phản ánh nơi lỗi của request hiện tại |
| Trường bổ sung trong lớp con | Getter/setter của lớp tùy biến | Tùy thiết kế lớp ngoại lệ | Race, dữ liệu cũ hoặc dữ liệu bị ghi đè giữa các luồng |

**Điểm quan trọng:** `static final Exception ERROR = ...` chỉ khiến **tham chiếu `ERROR` không được gán sang đối tượng khác**; nó không làm trạng thái của đối tượng `Throwable` bất biến.

Một lời gọi `throw ERROR;` cũng không tự tạo đối tượng mới. Hai lần ném có thể chuyển **chính cùng tham chiếu** qua các nhánh `catch`.

## 3. `initCause()` chỉ gán nguyên nhân một lần

Một số constructor của `Throwable` cho phép đặt cause ngay lúc tạo:

```java
new IllegalStateException("Cannot process order", originalError);
```

Đây là đoạn minh họa với biến `originalError` đã có kiểu `Throwable`. Cách này đặt nguyên nhân ngay khi tạo đối tượng và thường thích hợp khi chuyển lỗi từ tầng thấp sang tầng có ý nghĩa nghiệp vụ.

`Throwable.initCause(cause)` phục vụ những lớp ngoại lệ được khởi tạo **chưa gán cause**. Tuy nhiên:

1. Chỉ có thể gán cause một lần. Sau lần thành công, các lần `initCause(...)` tiếp theo ném `IllegalStateException`.
2. Ngay cả khi constructor đã khởi tạo cause bằng `null`, `initCause` vẫn có thể bị cấm bởi cause đã được coi là khởi tạo.
3. `initCause(this)` bị từ chối vì một throwable không thể là nguyên nhân trực tiếp của chính nó.
4. `initCause` **không phải setter để thay cause theo request**. Đừng dùng cùng một ngoại lệ rồi kỳ vọng mỗi lần thất bại sẽ có một `getCause()` mới.

Ví dụ minh họa bên trong một phương thức:

```java
Exception shared = new Exception("shared");
shared.initCause(new java.io.IOException("request A"));

try {
    shared.initCause(new java.io.IOException("request B"));
} catch (IllegalStateException rejected) {
    System.out.println("Cause was already initialized");
}
```

Sau nhánh trên, `shared.getCause()` vẫn liên hệ lỗi `"request A"`. Nếu request B dùng `shared` để báo thất bại, người đọc log có thể tưởng lỗi của A là nguyên nhân của B.

Java có cơ chế đồng bộ cho việc khởi tạo cause một lần, nhưng việc “chỉ một luồng thắng” không biến việc dùng chung thành đúng nghiệp vụ. Với hai luồng chạy đồng thời, luồng nào có thể gán cause trước là yếu tố không nên dùng làm nguồn quyết định dữ liệu chẩn đoán.

## 4. `addSuppressed()` có thể trộn lỗi của nhiều tác vụ

`Throwable.addSuppressed(Throwable)` được thiết kế để **giữ các lỗi phụ liên quan đến cùng một thất bại chính**, thường xảy ra trong `try-with-resources`. Ví dụ thân `try` ném `IOException("read failed")`, rồi `close()` cũng ném `IOException("close failed")`: Java thường giữ lỗi đọc là chính và gắn lỗi đóng tài nguyên thành suppressed exception.

Điều đó **không** có nghĩa `addSuppressed` là kho dữ liệu để nhiều request dùng chung. Nếu A và B cùng giữ một throwable và lần lượt gọi:

```java
shared.addSuppressed(new java.io.IOException("cleanup of A"));
shared.addSuppressed(new java.io.IOException("cleanup of B"));
```

thì `shared.getSuppressed()` có thể chứa **cả hai**. Đoạn này minh họa hậu quả khi sử dụng sai một `Throwable` dùng chung, không phải cách thiết kế nên áp dụng.

`addSuppressed` có hỗ trợ sử dụng an toàn ở mức thao tác thêm, nhưng **không tự phân nhóm suppressed exceptions theo request**. `getSuppressed()` trả một mảng chụp tại thời điểm gọi; lần đọc sau có thể chứa thêm phần tử mới. Thứ tự thêm khi các luồng chạy đồng thời không phải cách đáng tin để xác định yêu cầu nào xảy ra trước.

Ngoài ra, `addSuppressed(null)` và `addSuppressed(this)` là các tình huống không hợp lệ; Java từ chối thay vì ghi danh sách như thông thường. Một throwable được tạo với `enableSuppression=false` sẽ không ghi nhận suppressed exceptions, dù mã cố thêm chúng. Tắt suppression để giảm chi phí là **đánh đổi dữ liệu chẩn đoán**, không phải cách sửa lỗi trộn request.

## 5. Ví dụ Java 17+: một ngoại lệ dùng chung tích lũy ngữ cảnh của hai luồng

Ví dụ sử dụng hai luồng khác nhau cố tình thêm lỗi phụ từ hai tác vụ vào một `Throwable` chung. Để đầu ra ổn định, chương trình đợi cả hai luồng kết thúc rồi sắp xếp tên lỗi trước khi in; thứ tự thêm thực tế không cần cố định. Đây là **mã minh họa phản ví dụ** để hiểu rủi ro.

Tạo tệp **`SharedThrowableRiskDemo.java`**:

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class SharedThrowableRiskDemo {
    private static final IllegalStateException SHARED = createShared();

    private static IllegalStateException createShared() {
        return new IllegalStateException("shared failure");
    }

    private static void addCleanupFailure(
            String request, CountDownLatch start) {
        try {
            start.await();
            SHARED.addSuppressed(
                    new IOException("cleanup " + request));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("createdAt="
                + SHARED.getStackTrace()[0].getMethodName());

        SHARED.initCause(new IOException("backend A"));
        boolean secondCauseRejected = false;
        try {
            SHARED.initCause(new IOException("backend B"));
        } catch (IllegalStateException expected) {
            secondCauseRejected = true;
        }

        CountDownLatch start = new CountDownLatch(1);
        Thread a = new Thread(() -> addCleanupFailure("A", start));
        Thread b = new Thread(() -> addCleanupFailure("B", start));
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        String contexts = String.join(",",
                Arrays.stream(SHARED.getSuppressed())
                        .map(Throwable::getMessage)
                        .sorted()
                        .toList());

        System.out.println("secondCauseRejected="
                + secondCauseRejected);
        System.out.println("cause=" + SHARED.getCause().getMessage());
        System.out.println("suppressedCount="
                + SHARED.getSuppressed().length);
        System.out.println("suppressedContexts=" + contexts);

        SHARED.fillInStackTrace();
        System.out.println("refreshedAt="
                + SHARED.getStackTrace()[0].getMethodName());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 SharedThrowableRiskDemo.java
java SharedThrowableRiskDemo
```

Đầu ra:

```text
createdAt=createShared
secondCauseRejected=true
cause=backend A
suppressedCount=2
suppressedContexts=cleanup A,cleanup B
refreshedAt=main
```

Ý nghĩa từng kết quả:

- `SHARED` được tạo bởi `createShared()`. Stack trace ban đầu ghi nhận vị trí tạo đối tượng, dù sau đó đối tượng được dùng lại từ nhiều nơi.
- `initCause` gán `"backend A"` thành công; cố gán `"backend B"` bị từ chối. Cause còn lại **không phản ánh** yêu cầu B.
- Hai luồng độc lập thêm hai lỗi `cleanup A` và `cleanup B`; một bản ghi in toàn bộ ngoại lệ chung có thể kèm cả hai. Việc thêm có đồng bộ nội bộ không thể sửa được vấn đề **lẫn ngữ cảnh nghiệp vụ**.
- `fillInStackTrace()` trong `main` thay thông tin stack của **chính đối tượng chung**. Nếu một luồng khác đang chuẩn bị ghi nó ra log, dữ liệu được trình bày có thể khác theo thời điểm quan sát.

Trong thực tế, lỗi đóng tài nguyên nên gắn với ngoại lệ chính **của đúng thao tác có tài nguyên đó**. Ví dụ cố tình đưa chúng vào một đối tượng chung để chứng minh vì sao việc tái sử dụng như vậy nguy hiểm.

## 6. `fillInStackTrace()` và `setStackTrace()` làm mất định vị sự cố

Theo cách khởi tạo thông thường, `Throwable` ghi nhận stack trace tại lúc được tạo. Nếu tạo ngoại lệ chung khi khởi động ứng dụng rồi ném lại ở nhiều điểm, stack trace có thể liên tục chỉ về mã khởi tạo chứ không về nơi lỗi thực sự xảy ra.

Một số mã cố “sửa” vấn đề bằng cách gọi:

```java
shared.fillInStackTrace();
throw shared;
```

Đây là đoạn mã minh họa bên trong một phương thức có biến `shared`. `fillInStackTrace()` ghi lại thông tin stack cho **cùng đối tượng** khi có thể ghi stack. Nếu luồng A gọi trước rồi luồng B gọi sau, A không có một bản sao riêng để giữ dấu vết của mình: stack chung có thể đã bị B thay đổi trước lúc được định dạng hoặc phân tích.

`setStackTrace(StackTraceElement[])` cũng có thể thay đổi cách stack được hiển thị của throwable có stack ghi được. Việc thay đổi stack để trông như sự cố xuất phát từ vị trí khác đặc biệt nguy hiểm khi điều tra lỗi. Không nên “đánh dấu request” bằng cách chỉnh stack của lỗi chung.

`getStackTrace()` trả về **mảng bản sao** của phần tử stack, vì vậy chỉnh sửa một phần tử trong mảng vừa nhận không tự cập nhật stack lưu trong `Throwable`. Nhưng điều đó không làm chính đối tượng `Throwable` trở thành bất biến: các API cập nhật stack vẫn tồn tại, và mỗi lần đọc có thể quan sát một trạng thái khác sau khi thread khác thay đổi.

Việc logger chụp stack khi gọi hay định dạng trễ còn tùy backend/formatter. Nếu dùng logging bất đồng bộ, càng không nên dựa vào việc một `Throwable` được chia sẻ và tiếp tục thay đổi sau lời gọi log sẽ luôn xuất hiện với trạng thái tại đúng thời điểm log.

## 7. “Thread-safe” ở từng phương thức không bảo đảm ngữ cảnh chính xác

Cần phân biệt hai câu hỏi:

1. **Tính an toàn dữ liệu ở cấp thao tác:** khi hai luồng cùng thêm suppressed exception, cấu trúc lưu trữ có bị phá hỏng hay không?
2. **Tính đúng đắn nghiệp vụ:** các lỗi đó có thuộc cùng một sự cố chính và cùng một báo cáo chẩn đoán hay không?

Một API có thể có đồng bộ nội bộ nhưng vẫn sai nếu ứng dụng cho hai luồng **cùng sở hữu** một đối tượng mang ngữ cảnh. Java không tự biết `IOException("cleanup A")` thuộc request A còn `IOException("cleanup B")` thuộc request B. Một logger cũng không thể tách chúng thành hai sự cố chính xác chỉ dựa trên `getSuppressed()` của object chung.

Ví dụ về một chuỗi thao tác mà ứng dụng tưởng là “nhất quán”:

```text
Luồng A: chuẩn bị ghi log cho request A
Luồng B: thêm suppressed của request B
Luồng A: đọc getCause và getSuppressed rồi ghi log
Luồng B: gọi fillInStackTrace
Luồng A: định dạng các khung stack
```

Không có một snapshot nghiệp vụ **nguyên tử** bao gồm mọi trường cause, suppressed, stack, thông điệp bổ sung và metadata của request. Hơn nữa, khi đối tượng nguyên nhân (`getCause()`) cũng được chia sẻ và thay đổi, ngay cả một ngoại lệ wrapper mới của request A có thể tham chiếu đến dữ liệu không thuộc A.

**Chốt thiết kế:** an toàn luồng không thay thế cho quy tắc **mỗi thất bại cần có ngữ cảnh và quyền sở hữu riêng**.

## 8. Thiết kế đúng: tạo ngoại lệ riêng cho mỗi sự cố

Thông thường, một thao tác bắt được lỗi gốc sẽ tạo exception có ý nghĩa ở tầng của nó và **giữ nguyên nhân ngay trong constructor**:

```java
try {
    storage.save(order);
} catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Cannot save order " + order.id(), cause);
}
```

Đây là **đoạn minh họa** trong một phương thức có `storage`, `order` và kiểu trả về/kiểu nguồn phù hợp; không phải chương trình độc lập. Mỗi lần chạy nhánh `catch` tạo một `IllegalStateException` mới, liên kết với lỗi `IOException` của chính lần lưu đó.

Nếu cần ghi lỗi phụ, hãy thêm vào **ngoại lệ chính của cùng lần xử lý**, hoặc để `try-with-resources` thực hiện đúng quy tắc suppressed:

```java
try (Resource resource = openResource()) {
    resource.process();
}
```

Đây là đoạn minh họa cần các lớp/hàm `Resource`, `openResource` và `process` của ứng dụng. Khi cả thân `try` và `close()` đều ném lỗi, Java giữ ngoại lệ chính và thêm lỗi đóng tài nguyên thành suppressed. Không dùng một throwable global làm nơi tiếp nhận lỗi đóng tài nguyên của mọi request.

### Ví dụ Java 17+: hai request có đối tượng lỗi độc lập

Tạo tệp **`IsolatedThrowableDemo.java`**:

```java
import java.io.IOException;

public class IsolatedThrowableDemo {
    private static IllegalStateException fail(String request) {
        IOException cause = new IOException("storage " + request);
        IllegalStateException error = new IllegalStateException(
                "request " + request + " failed", cause);
        error.addSuppressed(new IOException("cleanup " + request));
        return error;
    }

    public static void main(String[] args) {
        IllegalStateException a = fail("A");
        IllegalStateException b = fail("B");

        System.out.println("differentErrors=" + (a != b));
        System.out.println("differentCauses="
                + (a.getCause() != b.getCause()));
        System.out.println("messageA=" + a.getMessage());
        System.out.println("causeA=" + a.getCause().getMessage());
        System.out.println("causeB=" + b.getCause().getMessage());
        System.out.println("suppressedA="
                + a.getSuppressed()[0].getMessage());
        System.out.println("suppressedB="
                + b.getSuppressed()[0].getMessage());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 IsolatedThrowableDemo.java
java IsolatedThrowableDemo
```

Đầu ra:

```text
differentErrors=true
differentCauses=true
messageA=request A failed
causeA=storage A
causeB=storage B
suppressedA=cleanup A
suppressedB=cleanup B
```

Khi dùng các đối tượng riêng, ngữ cảnh của A không bị B thêm vào. Ví dụ trên **mô phỏng** một lỗi cleanup bằng `addSuppressed` để kiểm tra việc cách ly, không có nghĩa mọi exception nên thêm lỗi suppressed thủ công. Trong ứng dụng thật, suppressed exception chỉ phù hợp khi nó thực sự là lỗi phụ của thất bại chính.

Cách này có chi phí cấp phát và thu thập stack, nhưng đó cũng chính là dữ liệu cần để truy nguyên một sự cố thực. Nếu số lỗi quá lớn, hãy xem nguyên nhân tạo lỗi bất thường và đo hiệu năng trước khi quyết định bỏ dữ liệu chẩn đoán.

## 9. Các trường hợp chia sẻ gián tiếp cũng cần chú ý

### 9.1. Wrapper mới nhưng cause vẫn là object chung

Ví dụ:

```java
static final java.io.IOException SHARED_CAUSE =
        new java.io.IOException("upstream unavailable");

RuntimeException a =
        new IllegalStateException("request A failed", SHARED_CAUSE);
RuntimeException b =
        new IllegalStateException("request B failed", SHARED_CAUSE);
```

Đây là **đoạn minh họa thuộc phần khai báo lớp/phương thức**, không phải một chương trình độc lập. `a != b`, nhưng `a.getCause() == b.getCause()`. Nếu `SHARED_CAUSE` có suppressed exceptions hoặc stack thay đổi, các wrapper đều tham chiếu tới thông tin đó.

Có trường hợp một sự cố gốc thực sự ảnh hưởng nhiều thao tác, ví dụ hệ thống khởi động thất bại và nhiều luồng cùng nhận một nguyên nhân. Việc chia sẻ **tham chiếu** khi đó có thể phản ánh quan hệ thật, nhưng cần thiết kế rõ **ai sở hữu và có được sửa cause nữa không**. Nếu mỗi request thất bại độc lập thì không nên dùng chung cause toàn cục.

### 9.2. Exception cache hoặc singleton để “tối ưu”

Dùng `static final Exception FAILURE = new Exception("failed")` tiết kiệm việc tạo mới ở các lần gọi sau, nhưng đi kèm stack trace ở nơi tạo đầu tiên và nguy cơ thông tin lỗi phụ lẫn vào nhau. Cần cân nhắc riêng chi phí theo tần suất sự cố, ngữ cảnh chẩn đoán và nhu cầu lưu stack/cause.

Một số hệ thống có thể dùng **đối tượng tín hiệu nhẹ có hợp đồng rất hẹp**, không mang ngữ cảnh request và vô hiệu hóa cả việc ghi stack lẫn suppression:

```java
final class FixedSignal extends RuntimeException {
    FixedSignal() {
        super("fixed signal", null, false, false);
    }
}
```

Đây chỉ là **khai báo lớp minh họa**, chưa phải chương trình độc lập. Constructor bốn tham số đã xác lập cause thành `null` và tắt suppression/stack writable. Nếu tái sử dụng đối tượng thuộc lớp này làm tín hiệu điều khiển nội bộ, hãy bảo đảm không thêm trạng thái mutable ở lớp con, không dùng nó để báo các lỗi cần stack thực, và tài liệu hóa ranh giới sử dụng. Đây là một ngoại lệ thiết kế có chủ đích, không phải mặc định cho tất cả failure.

Ngay cả với tín hiệu kiểu này, dùng kiểu kết quả/enum hoặc nhánh điều khiển bình thường thường dễ hiểu hơn khi tình huống thật sự là trạng thái dự kiến. Đừng dùng exception làm lệnh điều khiển chung chỉ vì đã tắt stack trace.

### 9.3. `ThreadLocal<Throwable>` chưa chắc giải quyết được vấn đề

Một throwable riêng cho mỗi `Thread` có thể tránh việc **hai thread đồng thời sửa cùng object**, nhưng executor thread thường được tái sử dụng cho nhiều request nối tiếp. Nếu không tạo lại/đặt lại đúng ngữ cảnh, suppressed exceptions và cause từ request cũ vẫn tồn tại. `ThreadLocal` còn có rủi ro giữ dữ liệu qua vòng đời worker và làm tăng sử dụng bộ nhớ nếu không dọn đúng chỗ.

Do đó, “mỗi thread một exception” vẫn khác “mỗi lần thất bại một exception”. Đơn vị sở hữu lỗi nên gắn với **sự cố/operation**, không mặc nhiên gắn với tuổi thọ của thread.

## 10. Tác động đến ghi log, theo dõi lỗi và bảo mật

Khi logger nhận cả `Throwable`, backend thường có thể ghi tên lớp ngoại lệ, stack trace, `Caused by:` và `Suppressed:`. Đó là một lợi thế chẩn đoán, nhưng lại phơi bày tác động của đối tượng dùng chung rõ hơn.

- **Log sai request:** lỗi phụ từ B xuất hiện trong log request A, gây hiểu nhầm hoặc làm người trực vận hành theo dấu nhầm.
- **Stack trace cũ hoặc bị thay:** stack chỉ về đoạn khởi tạo singleton, hoặc về thread vừa gọi `fillInStackTrace()`, không về nơi đã phát sinh lỗi của record đang xem.
- **Chẩn đoán không nhất quán:** cùng một tham chiếu throwable được log ở hai thời điểm có thể hiển thị số suppressed exceptions khác nhau.
- **Nguy cơ lộ dữ liệu giữa người dùng:** nếu message/cause/suppressed chứa dữ liệu riêng của request, việc dùng chung có thể khiến chi tiết của người dùng này xuất hiện trong bản ghi liên quan người dùng khác. Cần tránh đưa bí mật vào exception message ngay từ đầu.
- **Gom nhóm lỗi sai:** các hệ thống theo dõi lỗi có thể dựa vào stack và loại exception để nhóm sự cố; stack dùng chung hoặc bị chỉnh sửa làm nhóm lỗi kém chính xác.

Để giảm rủi ro, log tại ranh giới rõ trách nhiệm, chuyển **đối tượng ngoại lệ riêng của sự cố** cùng mã tương quan an toàn, giữ cause khi bọc lỗi và tránh ghi cùng stack trace nhiều lần không cần thiết. Việc dùng logging bất đồng bộ không làm một object dùng chung trở thành bất biến.

Đừng tạo một ngoại lệ mới **chỉ để chép message** và bỏ cause: `new IllegalStateException(shared.getMessage())` sẽ làm mất ngữ cảnh nguyên nhân thực. Nếu thật sự cần một ngoại lệ mới, lấy nguyên nhân *đúng của operation* và chuyển vào constructor phù hợp. Một nguyên nhân đã chia sẻ vẫn nên được đánh giá kỹ trước khi tham chiếu.

## 11. Hiệu năng: nên tối ưu đường lỗi thế nào?

Rủi ro chia sẻ throwable thường nảy sinh từ ý định tiết kiệm việc tạo ngoại lệ và stack trace. Đánh đổi cần phân tích ở cấp hệ thống:

| Lựa chọn | Lợi ích tiềm năng | Hạn chế quan trọng |
| --- | --- | --- |
| Tạo ngoại lệ mới cho mỗi failure | Ngữ cảnh/cause/suppressed/stack riêng | Có cấp phát và chi phí chụp stack |
| Dùng singleton throwable có stack | Giảm số đối tượng tạo mới | Stack cũ, trạng thái mutable dùng chung, log khó truy nguyên |
| Singleton tín hiệu không stack/suppression, không có ngữ cảnh | Có thể giảm chi phí trong một hợp đồng nội bộ đặc biệt | Mất stack; dễ bị dùng sai cho failure thật |
| Kết quả có cấu trúc cho trạng thái bình thường | Không tạo ngoại lệ trong nhánh dự kiến | Phải thiết kế API, gọi và kiểm tra kết quả rõ ràng |
| Giới hạn tốc độ log/lấy mẫu, đo lỗi theo metric | Giảm tải ghi log trùng lặp | Vẫn phải lưu đủ chẩn đoán cho lỗi cần điều tra |

Khi một kiểu lỗi xảy ra với tần suất cao, câu hỏi đầu tiên nên là: **đây có thật là ngoại lệ hay một kết quả thường xuyên theo hợp đồng API?** Nếu không tìm thấy mục tiêu là tình trạng dự kiến, `Optional`, enum hoặc kiểu kết quả có thể phù hợp hơn việc liên tục ném exception.

Nếu vẫn phải dùng exception, đo trên đúng JDK, tải thực và độ sâu stack. Java Microbenchmark Harness (JMH) hữu ích để đo chi phí tạo ngoại lệ; profiler và metrics giúp nhận biết tác động tới CPU, cấp phát, GC và log. Không suy ra từ một benchmark rằng việc bỏ stack trace cho mọi lỗi là an toàn.

Đặc biệt, `Throwable.addSuppressed` có thể khiến đối tượng dùng chung sống lâu chứa ngày càng nhiều tham chiếu tới ngoại lệ phụ và các object liên quan. Điều này tạo nguy cơ **giữ bộ nhớ lâu hơn cần thiết**, ngoài lỗi chẩn đoán. Không dùng danh sách suppressed như lịch sử sự cố vô hạn.

## 12. Checklist khi xem lại mã nguồn

Khi tìm thấy một `Throwable` được dùng qua nhiều yêu cầu hoặc luồng, kiểm tra:

1. Object đó được tạo **mỗi lần thất bại**, mỗi thread, hay một lần ở `static final`/cache?
2. Cause đã được đặt ngay khi tạo chưa? Có mã gọi `initCause` cho các request khác nhau không?
3. `addSuppressed` có chỉ nhận lỗi phụ thuộc **cùng một thao tác**, hay trộn dữ liệu của nhiều tác vụ?
4. Có nơi gọi `fillInStackTrace()` hoặc `setStackTrace(...)` trên object đang được dùng chung không?
5. Logger có thể định dạng sự kiện sau khi một thread khác đã sửa throwable không? Chức năng của backend cần được xác nhận.
6. Wrapper mới có đang giữ tham chiếu đến **cause toàn cục mutable** không?
7. Có cache/`ThreadLocal` khiến trạng thái lỗi sống lâu hơn một operation không?
8. Có thể thay ngoại lệ cho tình huống dự kiến bằng kiểu kết quả, hoặc giới hạn log thay vì dùng singleton throwable không?
9. Nếu cố ý dùng tín hiệu throwable không stack, hợp đồng có thực sự bảo đảm không chứa ngữ cảnh request hay dữ liệu cần điều tra không?
10. Trường hợp xấu nhất có làm log của người dùng này chứa thông tin của người dùng khác không?

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `static final RuntimeException SHARED = new RuntimeException("failed");` có đồng nghĩa đối tượng không thể thay đổi?  
**Trả lời:** Không. `final` chỉ cố định tham chiếu của trường. `Throwable` vẫn có các trạng thái như cause chưa khởi tạo, suppressed list và stack trace mà API cho phép cập nhật.

**Câu 2.** Hai request cùng gọi `shared.addSuppressed(...)`; phương thức hỗ trợ đồng bộ thì dữ liệu có được tách riêng không?  
**Trả lời:** Không. Danh sách vẫn thuộc **cùng một đối tượng**; đồng bộ thao tác không tự chia phần tử theo request.

**Câu 3.** Sau khi gọi `initCause(causeA)` thành công, có thể gọi `initCause(causeB)` để thay cause không?  
**Trả lời:** Không. Lần thứ hai bị từ chối bằng `IllegalStateException`.

**Câu 4.** Ném lại cùng exception từ hai method có tự tạo hai stack trace độc lập không?  
**Trả lời:** Không. Đây vẫn là một đối tượng. Nếu gọi `fillInStackTrace`, bạn cập nhật stack gắn với đối tượng chung, không tạo bản sao riêng.

**Câu 5.** Hai wrapper `new IllegalStateException("A", common)` và `new IllegalStateException("B", common)` có cause khác nhau không?  
**Trả lời:** Không. Chúng có object wrapper riêng nhưng vẫn giữ **cùng tham chiếu** `common`; nếu nó mang dữ liệu mutable, cả hai cùng nhìn thấy.

**Câu 6.** `getSuppressed()` trả về mảng riêng, vậy Throwable đã bất biến chưa?  
**Trả lời:** Chưa. Mảng trả về là bản chụp tại thời điểm gọi; một thread khác vẫn có thể thêm suppressed exception vào object gốc.

**Câu 7.** Đổi từ singleton throwable có stack sang singleton không stack có giải quyết tất cả nguy cơ không?  
**Trả lời:** Không. Nó bỏ một nguồn trạng thái chẩn đoán, đồng thời làm mất khả năng tìm vị trí lỗi. Chỉ cân nhắc khi đó là tín hiệu nội bộ không chứa ngữ cảnh request, có hợp đồng rõ và đã đo.

**Câu 8.** Cách an toàn thông thường khi hai request thất bại độc lập là gì?  
**Trả lời:** Tạo exception riêng cho từng sự cố, giữ cause đúng của sự cố trong constructor, chỉ gắn suppressed exceptions thực sự thuộc sự cố đó, rồi ghi log theo mã tương quan phù hợp.

## 14. Tham khảo API

- [Java 17 — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `initCause`, `addSuppressed`, `getSuppressed`, `getStackTrace`, `fillInStackTrace` và các constructor điều khiển suppression/stack trace.
- [Java 17 — `RuntimeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): các constructor đặt cause, suppression và writable stack trace.
- [Java 17 — `AutoCloseable` và `try-with-resources`](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): ví dụ ngoại lệ chính và suppressed exceptions khi đóng tài nguyên.

**Ghi nhớ:** một `Throwable` là **một đối tượng có trạng thái chẩn đoán**, không phải chỉ là mã lỗi bất biến. `initCause` không cho phép thay nguyên nhân tùy ý; suppressed exceptions có thể tích lũy; stack trace có thể bị cập nhật. Tái sử dụng object này giữa các sự cố độc lập dễ trộn dữ liệu và làm log sai sự thật. Hãy ưu tiên **mỗi sự cố có ngoại lệ riêng**, giữ cause đúng, dùng suppressed theo đúng quan hệ lỗi phụ và tối ưu đường lỗi dựa trên số liệu thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-performance-008">Đo bằng benchmark có tình huống lỗi và tần suất thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Đo bằng benchmark có tình huống lỗi và tần suất thực tế

Khi tối ưu xử lý exception trong Java, một kết quả kiểu “ném exception mất X ns” có thể đúng trong một phép thử rất hẹp nhưng **không phản ánh hiệu năng ứng dụng thật**. Hệ thống thực có những request thành công, những lỗi hiếm, các đợt lỗi dồn dập khi dependency gặp sự cố, đường xử lý khác nhau và chi phí log/cleanup. Nếu benchmark luôn ném lỗi ở 100% vòng lặp, bạn đang đo một workload không giống production; nếu benchmark không có lỗi, bạn cũng không biết ứng dụng sẽ chịu tải thế nào khi error burst.

Một benchmark tốt phải trả lời trước: **đo phần nào của quá trình xử lý lỗi, với phân phối tình huống và tần suất nào, trên JVM/hệ thống nào, bằng metric nào?** Chỉ sau khi xác định workload và bảo đảm tính đúng đắn của code mới có ý nghĩa để so sánh tốc độ.

Bài này thuộc mục **Hiệu năng** của Java core – exception. Trọng tâm là thiết kế phép đo có cả nhánh thành công và nhánh thất bại, không nhầm tỷ lệ lỗi với tỷ lệ CPU, sử dụng Java Microbenchmark Harness (JMH) khi cần microbenchmark, và kiểm chứng bằng profiling/load test trong ngữ cảnh thực tế. Mã ví dụ tương thích Java 17 trở lên.

## 1. Benchmark exception cần mô hình hóa điều gì?

Một lời gọi có thể trải qua:

```text
Nhận request / record
         |
         v
Kiểm tra điều kiện
     /           \
 Thành công     Thất bại
     |              |
 Tính toán      Tạo exception
     |              |
 Trả kết quả    throw/unwind
                    |
                    v
              catch hoặc boundary
                    |
                    v
            ghi log / metric / cleanup
                    |
                    v
             trả kết quả lỗi
```

Không phải tất cả nhánh thất bại đều có cùng chi phí. Một `IllegalArgumentException` được ném và bắt ngay trong phương thức sẽ khác `IOException` phát sinh sau một lần gọi mạng, đi qua nhiều tầng và có log stack trace. Vì vậy cần chọn rõ **đơn vị công việc** thay vì gọi chung mọi điều đó là “overhead của exception”.

Các yếu tố nên nêu trong định nghĩa benchmark:

| Yếu tố | Câu hỏi cần trả lời | Vì sao quan trọng |
| --- | --- | --- |
| Mục tiêu | Đo cấp phát, throughput, latency hay p99? | Mỗi metric trả lời vấn đề khác nhau. |
| Đường thành công | Việc gì diễn ra khi không lỗi? | Phần lớn lưu lượng có thể nằm ở nhánh này. |
| Đường thất bại | Exception nào, được tạo ở đâu, catch ở đâu? | Constructor/backtrace, throw và cleanup khác nhau. |
| Tần suất | 0%, 0,1%, 1%, 10%, 50%, 100% hay phân phối khác? | Giá thành trung bình thay đổi rất mạnh theo tỷ lệ lỗi. |
| Phân bố thời gian | Lỗi rải đều hay dồn thành burst? | Burst tạo áp lực log, GC và hàng đợi. |
| Logging | Tắt, bật, đồng bộ, bất đồng bộ hay sampling? | I/O/formatting có thể lấn át throw/catch. |
| Độ sâu call stack | Throw tại chỗ hay qua nhiều phương thức? | Stack capture/unwind và finally thay đổi. |
| Môi trường | JDK, GC, CPU, memory, log destination? | Không thể chuyển mọi con số giữa các máy. |
| Dữ liệu | Request đơn giản hay payload lớn, cache nóng/lạnh? | Có thể đo nhầm parsing/I/O thay vì exception. |

**Không có một tỷ lệ lỗi chuẩn chung cho mọi dịch vụ.** Một API người dùng có 0,1% lỗi trong giờ bình thường; một parser batch có 20% dữ liệu sai; một dịch vụ phụ thuộc hạ tầng có thể có 70% lỗi trong vài phút. Những tỷ lệ dưới đây đều là **kịch bản giả định để thiết kế phép thử**, không phải số liệu thống kê của một ứng dụng cụ thể.

## 2. Phân biệt chi phí từng pha với chi phí toàn đường lỗi

Một phép đo thực tế của `throw new IllegalStateException(...)` thường đã bao gồm **constructor, cấp phát, backtrace, throw/unwind và catch**. Nếu handler còn ghi log, kết quả còn có chi phí format/ghi bản ghi. Muốn tối ưu đúng nơi, cần chia nhóm benchmark:

1. **Successful path** – chạy phần bình thường, có thể có `try/catch` nhưng không ném.
2. **Create-only** – tạo đối tượng `Throwable` nhưng không `throw`; mục đích đánh giá riêng constructor/stack capture khi có thể.
3. **Throw pre-created** – ném một exception đã tạo; giúp tách tương đối phần throw/catch nhưng không còn stack trace tại vị trí lỗi mới và không mô phỏng đường production phổ biến.
4. **Throw new and catch** – đo đường lỗi quen thuộc gồm tạo, ném và bắt.
5. **Throw across frames** – ném ở tầng dưới, unwind qua một hay nhiều frame và `finally`/`try-with-resources`.
6. **Catch and handle** – logic phân loại, chuyển lỗi, trả fallback, cập nhật counter.
7. **Catch and log** – bổ sung render stack, formatting, appender, queue và flush theo cấu hình.
8. **End-to-end request** – validation, xử lý chính, persistence/network, error mapping, logging và response.

Khi báo cáo, ghi rõ phép đo thuộc nhóm nào. Không được lấy benchmark “throw new + catch + log” rồi kết luận **riêng** opcode `throw` chậm bằng toàn bộ thời gian đó.

### Cách nhìn về chi phí trung bình

Nếu chỉ có hai đường thành công/thất bại và bỏ qua tương tác hệ thống, độ trễ trung bình có thể được **ước lượng**:

\[
E[T] \approx (1-p)\,E[T\mid success] + p\,E[T\mid failure]
\]

Trong đó `p` là tỷ lệ lỗi thực tế, không phải con số tùy ý dùng trong vòng lặp benchmark. Ví dụ **minh họa bằng giả định**: success mất 1 đơn vị, failure mất 101 đơn vị; với `p = 0.01`, chi phí trung bình theo mô hình là `0.99·1 + 0.01·101 = 2` đơn vị. Điều này không có nghĩa CPU, p99 hay throughput thực tế tăng đúng hai lần, vì còn concurrency, scheduling, I/O, GC, logging và tải hệ thống.

Cách tính trung bình không mô tả được **tail latency** hay tình huống nhiều lỗi xảy ra đồng thời. Phép đo phải bổ sung latency distribution và workload burst khi những điều đó quan trọng.

## 3. Xác định tỷ lệ lỗi dựa trên production

Trước khi chạy benchmark, hãy xác định tần suất lỗi cho từng loại workload:

- **Theo request:** số request thất bại / tổng request trong cùng cửa sổ.
- **Theo record:** số record lỗi / tổng record xử lý; có thể khác tỷ lệ request vì một request chứa nhiều record.
- **Theo thao tác phụ thuộc:** số lần query/HTTP attempt lỗi / tổng số attempt; nếu một request retry nhiều lần, tỷ lệ attempt có thể khác tỷ lệ request cuối cùng bị lỗi.
- **Theo loại lỗi:** validation, data not found, timeout, connection reset, resource exhaustion, bug nội bộ… Không gộp tất cả thành một exception duy nhất.
- **Theo khoảng thời gian:** tỷ lệ 1 phút và tỷ lệ 1 giờ có thể khác nhau rất lớn; trung bình cả ngày có thể giấu một incident nghiêm trọng.

Nếu chưa có dữ liệu thật, hãy **nêu rõ giả định** và thử một ma trận có giá trị học tập, ví dụ:

| Kịch bản giả định | Tỷ lệ lỗi | Dùng để kiểm tra |
| --- | ---: | --- |
| Healthy | 0% | Baseline đường thành công. |
| Mostly healthy | 0,1% | Lỗi hiếm, chi phí exception được pha loãng. |
| Normal mixed | 1% | Mix ít lỗi để quan sát overhead khi lệch từ 0%. |
| Degraded | 10% | Thay đổi phân phối latency và tần suất log. |
| High rejection/bad batch | 50% | Workload mà lỗi là trạng thái rất thường xuyên. |
| Incident | 100% trong cửa sổ ngắn | Khả năng chịu burst, GC/logging/circuit breaker. |

Các tỷ lệ này là **mốc khảo sát**, không phải mục tiêu SLA và không có nghĩa “50% lỗi” là bình thường. Sau khi lấy metrics, thay ma trận giả định bằng phân phối của dịch vụ thật.

Cần phân biệt **tần suất exception được ném** với **tần suất request thất bại**. Một request có thể ném và bắt nhiều exception bên trong rồi vẫn hoàn thành thành công. Trong retry, một request có thể tạo 3 exception I/O trước khi lần thử thứ tư thành công. Phép đo theo request và phép đo theo exception event trả lời hai câu hỏi khác nhau.

## 4. Dữ liệu thực tế: phân phối lỗi và sự tương quan

Một benchmark ngẫu nhiên có 1% lỗi rải đều không tương đương workload 1% lỗi nhưng tất cả dồn vào một phút. Cả hai có cùng tỷ lệ tổng thể nhưng khác đặc điểm hệ thống:

**Lỗi rải đều:**

```text
S S S S E S S S S S S S S E S S S S S ...
```

**Lỗi dồn thành đợt:**

```text
S S S S S S S S E E E E E E E E S S ...
```

Ở đây `S` là thành công và `E` là lỗi. Burst có thể khiến nhiều request cùng ghi stack trace, làm đầy queue log, tăng allocation rate, gây GC và kéo dài p99. Cũng có thể nhiều thread cùng retry vào một dependency hỏng, dẫn đến tải tăng và incident kéo dài.

Các chiều workload hữu ích:

- **Phân phối thời gian:** rải đều, burst, theo chu kỳ, lỗi tương quan theo host hoặc shard.
- **Kích thước input:** bản ghi nhỏ/lớn, loại payload, độ sâu parse.
- **Loại lỗi:** validation giá rẻ khác I/O/network timeout hoặc exception khi cleanup.
- **Độ sâu stack:** catch gần nơi throw hay ở boundary trên cùng.
- **Tỷ lệ log:** 0%, tất cả, sampled; sync vs async.
- **Số worker:** 1 thread, số thread theo CPU, pool chịu queue contention.
- **Trạng thái hạ tầng:** cache hit/miss, dependency healthy/unavailable, disk/network throughput.

Muốn benchmark đo hiệu ứng của **tần suất** thì thay đổi một biến độc lập tại mỗi thí nghiệm, giữ các yếu tố khác ổn định. Nếu vừa thay tỷ lệ lỗi, vừa thay logger và số thread rồi quan sát throughput khác nhau, không thể quy nguyên nhân cho exception.

## 5. Ví dụ Java 17: tạo workload có tỷ lệ lỗi xác định

Bài trước tiên cần một chương trình **chạy độc lập**, chứng minh dữ liệu đầu vào thật sự có tỷ lệ lỗi dự kiến. Chương trình sau dựng 10.000 input, đưa giá trị âm vào đúng một phần theo phép hoán vị số học để tạo lỗi rải đều. Đồng thời so sánh với tình huống tất cả lỗi nằm ở đầu batch (*burst*). Kết quả chỉ đếm thành công/thất bại, **không giả vờ là số đo hiệu năng**.

Tạo file `ErrorRateScenarioDemo.java`:

```java
public class ErrorRateScenarioDemo {

    private static final int TOTAL = 10_000;

    private static final class InvalidValueException
            extends RuntimeException {
        InvalidValueException() {
            super("negative input");
        }
    }

    private record Stats(int success, int failures, long checksum) {}

    private static int process(int input) {
        if (input < 0) {
            throw new InvalidValueException();
        }
        return input + 1;
    }

    private static Stats run(int badCount, boolean burst) {
        int success = 0;
        int failures = 0;
        long checksum = 0;

        for (int i = 0; i < TOTAL; i++) {
            // 37 nguyên tố cùng nhau với 10000:
            // (i * 37) % TOTAL đi qua mỗi giá trị 0..9999 một lần.
            int position = burst ? i : (i * 37) % TOTAL;
            int input = position < badCount ? -1 : i;

            try {
                checksum += process(input);
                success++;
            } catch (InvalidValueException ex) {
                failures++;
            }
        }

        return new Stats(success, failures, checksum);
    }

    public static void main(String[] args) {
        int[] badCounts = {0, 10, 100, 1000, 5000, 10000};

        for (int count : badCounts) {
            Stats stats = run(count, false);
            System.out.println(
                "scattered " + count + "/10000"
                    + " success=" + stats.success()
                    + " failure=" + stats.failures()
            );
        }

        Stats burst = run(1000, true);
        System.out.println(
            "burst 1000/10000 success=" + burst.success()
                + " failure=" + burst.failures()
        );
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ErrorRateScenarioDemo.java
java ErrorRateScenarioDemo
```

Stdout chính xác:

```text
scattered 0/10000 success=10000 failure=0
scattered 10/10000 success=9990 failure=10
scattered 100/10000 success=9900 failure=100
scattered 1000/10000 success=9000 failure=1000
scattered 5000/10000 success=5000 failure=5000
scattered 10000/10000 success=0 failure=10000
burst 1000/10000 success=9000 failure=1000
```

Giải thích:

1. `TOTAL = 10_000` cố định. Do `gcd(37, 10000) = 1`, phép tính `(i * 37) % 10000` hoán vị tất cả các vị trí 0 đến 9999, vì vậy so sánh với `badCount` tạo **đúng** số lỗi cần thiết.
2. Với `badCount = 10`, tỷ lệ lỗi là 0,1%; `100` là 1%; `1000` là 10%; `5000` là 50%.
3. Nhánh `burst = true` chọn `position=i` nên 1000 lỗi đầu tiên nằm liền nhau, nhưng tổng số lỗi bằng trường hợp phân tán.
4. `Stats.checksum` giúp giữ dữ liệu xử lý trong mã; ví dụ không in checksum vì mục đích ở đây là kiểm chứng tỷ lệ.
5. Chương trình **không đo nanosecond, throughput hay p99**. Nó chỉ là bộ tạo tình huống đúng số lượng, có thể chuyển vào test/benchmark thực.
6. Trong benchmark thật, việc chuẩn bị dữ liệu nên tách khỏi vùng đo nếu mục tiêu là chi phí xử lý input, không phải chi phí dựng dataset.

Phép hoán vị đơn giản ở đây **không đại diện phân phối thời gian của production**, và khi thực hiện tuần tự chỉ thay đổi thứ tự lỗi giữa các phần tử. Để đánh giá burst gây queue contention, log backpressure và concurrency, cần workload đa luồng/load test có thời gian đến request được kiểm soát.

## 6. Vì sao không dùng System.nanoTime một lần làm kết luận?

Một ví dụ thường thấy:

```java
long begin = System.nanoTime();

for (int i = 0; i < 1_000_000; i++) {
    try {
        throw new IllegalArgumentException("bad");
    } catch (IllegalArgumentException ignored) {
    }
}

System.out.println(System.nanoTime() - begin);
```

Đây là **đoạn minh họa một phép thử chưa đủ tin cậy**, không phải benchmark chuẩn. Vấn đề gồm:

- Chỉ có **100% lỗi**, không mô tả request thực tế có 0,1% hoặc 1% lỗi.
- Gộp constructor/stack capture, `throw` và `catch` làm một.
- Không có phép thử đối chứng đường thành công với cùng dữ liệu/công việc.
- Một lần chạy có thể chưa warm-up JIT; kết quả trộn startup, class loading và GC.
- JIT có thể tối ưu code khác với dự đoán, đặc biệt khi kết quả không được tiêu thụ một cách có ý nghĩa.
- Thời gian đo tổng vòng lặp không cho biết phân phối latency request hoặc p99.
- Không log, không cleanup, không concurrency, không network, không lỗi dồn theo đợt.
- Thời điểm thread được OS scheduling và tải nền trên máy có thể chi phối biến thiên.

`System.nanoTime()` là API đồng hồ đơn điệu dùng để đo khoảng thời gian tương đối và có ích khi đo đúng cách, nhưng **bản thân API không biến một vòng lặp thành benchmark khoa học**. Không nên quảng bá một số đo duy nhất từ đoạn code trên như thông số hiệu năng của exception Java.

## 7. Dùng JMH cho microbenchmark

**JMH (Java Microbenchmark Harness)** là công cụ của hệ sinh thái OpenJDK để thiết kế microbenchmark có warm-up, measurement iterations, fork JVM, cách tiêu thụ kết quả chống loại bỏ công việc vô ích và công cụ thống kê phù hợp. JMH **không nằm trong thư viện chuẩn của JDK**, nên cần project và dependency JMH thích hợp.

Các thành phần quen thuộc:

| Chú thích/cấu hình | Công dụng |
| --- | --- |
| `@Benchmark` | Đánh dấu phương thức đo. |
| `@State(Scope.Thread)` | Dữ liệu benchmark riêng cho mỗi worker đo, giảm chia sẻ không chủ đích. |
| `@Param(...)` | Chạy từng cấu hình tỷ lệ lỗi/độ sâu stack/logging. |
| `@Setup(Level.Trial)` | Tạo input trước phép đo, tránh trộn chi phí dựng dataset. |
| `@BenchmarkMode(Mode.Throughput)` | Đo số invocation/đơn vị thời gian. |
| `@BenchmarkMode(Mode.AverageTime)` | Đo thời gian trung bình mỗi invocation/operation. |
| `@Warmup` / `@Measurement` | Thiết lập vòng làm nóng và đo. |
| `@Fork` | Chạy benchmark trong các JVM fork độc lập hơn. |
| `@Threads` | Kiểm soát số thread đo; cần hiểu shared state/contention. |
| `Blackhole` | Tiêu thụ dữ liệu mà không để kết quả bị bỏ qua dễ dàng. |
| `@OperationsPerInvocation` | Diễn giải số operations bên trong một invocation khi phù hợp với mục tiêu đo. |

Không cần gắn mọi annotation cùng lúc. Chọn những thông số thể hiện mục tiêu rồi **ghi rõ toàn bộ cấu hình trong báo cáo** để người khác có thể tái lập.

### Mẫu JMH: thay đổi tần suất lỗi có kiểm soát

Mã dưới đây là **lớp JMH thực**, không phải chương trình Java chỉ dùng `javac` trần: nó yêu cầu project đã cài JMH và annotation processor tương ứng. Ví dụ kiểm tra `badPerTenThousand` ở các mức 0%, 0,1%, 1%, 10%, 50%, 100%. Mỗi invocation xử lý một batch 10.000 record, tránh quyết định kiểu dữ liệu đầu vào bằng random bên trong vùng đo.

File `ErrorRateBenchmark.java` (đặt trong package project JMH, điều chỉnh package phù hợp):

```java
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(2)
public class ErrorRateBenchmark {

    private static final int TOTAL = 10_000;

    @Param({"0", "10", "100", "1000", "5000", "10000"})
    public int badPerTenThousand;

    private int[] values;

    @Setup(Level.Trial)
    public void prepare() {
        values = new int[TOTAL];
        for (int i = 0; i < TOTAL; i++) {
            int position = (i * 37) % TOTAL;
            values[i] = position < badPerTenThousand ? -1 : i;
        }
    }

    private static int withException(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        return value + 1;
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL)
    public long throwAndCatch() {
        long sum = 0;
        for (int value : values) {
            try {
                sum += withException(value);
            } catch (IllegalArgumentException ex) {
                // Đường lỗi được xử lý; không ghi log trong phép đo này.
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL)
    public long conditionalResult() {
        long sum = 0;
        for (int value : values) {
            if (value >= 0) {
                sum += value + 1;
            }
        }
        return sum;
    }
}
```

Cách chuẩn bị project JMH:

1. Tạo project Maven/Gradle hoặc template JMH chính thức theo version phù hợp với JDK đang dùng.
2. Thêm JMH core và annotation processor `jmh-generator-annprocess` theo cấu hình build tool; **không tự chọn một version khác nhau** cho hai module này.
3. Đặt lớp trong source set mà JMH annotation processor xử lý; một số cấu hình/template yêu cầu package có tên thay vì default package.
4. Build project theo hướng dẫn của template để sinh benchmark runner; chạy benchmark bằng runner JMH.
5. Ghi nhận command thực tế, version JMH/JDK, warm-up, measurement, fork, threads, CPU và GC khi công bố kết quả.

Ví dụ cú pháp chạy với **JMH runner đã được build sẵn**, không phải lệnh chạy bằng Java JDK thuần:

```bash
java -jar target/benchmarks.jar ErrorRateBenchmark -wi 3 -i 5 -f 2
```

Tên JAR và đường dẫn phụ thuộc cách project JMH được cấu hình; lệnh trên chỉ đúng khi build tạo `target/benchmarks.jar`. **Không có một số stdout cố định** cho benchmark này: số ns/op thực tế phụ thuộc máy, JVM và workload. Không nên chép kết quả giả vào tài liệu.

Điểm cần hiểu khi đọc kết quả:

- `@OperationsPerInvocation(TOTAL)` giúp JMH diễn giải phép đo một batch gồm 10.000 phần tử như số operations tương ứng. Nhưng phép đo vẫn gồm overhead vòng lặp, check và cộng dồn; không có “ns thuần của throw”.
- `throwAndCatch` có constructor/backtrace khi lỗi, nhưng `conditionalResult` không tạo exception. Đây là so sánh **hai chiến lược API/đường lỗi**, không phải đo riêng catch.
- `@Param` tạo kết quả theo từng tỷ lệ lỗi; khi phân tích, so sánh trong cùng cấu hình và cùng môi trường.
- `@State(Scope.Thread)` làm mỗi JMH worker có dataset riêng. Nếu muốn đo contention, cần một phép thử khác với shared state đã được xác định.
- Việc phương thức trả `long` giúp kết quả được tiêu thụ theo cơ chế JMH, giảm nguy cơ toàn bộ phép tính bị loại bỏ. Nhưng cần kiểm tra mã máy/profile nếu kết quả bất thường; không có benchmark nào miễn nhiễm mọi tối ưu.
- Ở tỷ lệ lỗi lớn, logic exception và logic branch không có cùng đặc tính bộ nhớ/JIT. Một kết quả chênh lệch không chứng minh “exception không bao giờ nên dùng”: còn phụ thuộc việc lỗi có là trạng thái nghiệp vụ dự kiến hay không.

Nếu cấu hình JMH cần đo **throughput theo batch** thay vì thời gian trung bình của một record, có thể bỏ `@OperationsPerInvocation` hoặc thay mode/đơn vị phù hợp. Luôn nói rõ “operation” là **một record** hay **một batch**.

## 8. Kiểm chứng dữ liệu và tính tương đương trước khi đo

Một benchmark chỉ có giá trị nếu hai phương án tạo **kết quả nghiệp vụ tương đương** trên cùng input. Với ví dụ trên:

- Cả `throwAndCatch` và `conditionalResult` cộng `value+1` cho mỗi input không âm.
- Với input âm, cả hai bỏ qua phần tử đó.
- Không có bên nào ghi log, retry hoặc gọi I/O.
- Bộ dữ liệu cố định được dựng từ cùng `badPerTenThousand`.
- Các phép tính đều trả `long` để tránh tràn `int` đối với tổng 10.000 phần tử.

Tuy nhiên, hai API **không tương đương về khả năng chẩn đoán**: một bên tạo `IllegalArgumentException` có stack trace, bên kia coi lỗi dữ liệu là một nhánh điều kiện không ném. Đây là khác biệt thiết kế phải được ghi nhận, không thể chỉ nhìn ns/op rồi tuyên bố branch luôn đúng.

Nếu muốn so sánh hai implementation có cùng hợp đồng exception, hãy giữ nguyên loại lỗi, message cần thiết, đường catch và semantic side effect; tránh để một implementation bỏ phần logging/validation mà implementation kia phải làm.

### Thử tần suất trước khi chạy benchmark

Có thể dùng test kiểm tra `@Param`:

```text
badPerTenThousand = 0      → 0 lỗi / 10.000 record
badPerTenThousand = 10     → 10 lỗi / 10.000 record
badPerTenThousand = 100    → 100 lỗi / 10.000 record
badPerTenThousand = 1000   → 1.000 lỗi / 10.000 record
badPerTenThousand = 5000   → 5.000 lỗi / 10.000 record
badPerTenThousand = 10000  → 10.000 lỗi / 10.000 record
```

Đây là bảng dữ liệu mong đợi theo code, không phải số hiệu năng. Kiểm tra count **ngoài vùng benchmark** để không thêm overhead của phép kiểm tra vào mẫu đo chính.

## 9. Tách một lỗi hiếm khỏi lỗi dồn đợt

Một phép đo JMH xử lý batch tuần tự thường cho biết thời gian trung bình theo workload, nhưng **không mô phỏng đầy đủ sự sụp đổ của hệ thống khi lỗi bùng phát cùng lúc**.

Để đánh giá burst:

1. Xây dựng hai kịch bản có **cùng tổng số request và tổng số lỗi**: phân tán so với lỗi tập trung theo cửa sổ thời gian.
2. Tạo tải bằng công cụ load test/driver bên ngoài, kiểm soát request rate và concurrency. Có thể mô phỏng dependency lỗi vào khoảng thời gian xác định.
3. Giữ số worker, queue capacity, log destination, timeout và retry policy như production.
4. Quan sát throughput, p50/p95/p99, request timeout, GC pause, CPU, memory, log dropped/block time và queue length.
5. Ghi lại thời điểm bắt đầu/kết thúc đợt lỗi để phân biệt thời gian hệ thống **chịu tải** và thời gian **hồi phục**.
6. Kiểm tra correctness: có job nào xử lý trùng, có thất thoát dữ liệu, có resource nào không cleanup, có status nào bị nhầm “thành công” do fallback không?

Ví dụ ứng dụng có 1.000 request/s và 1% lỗi tổng. Một kịch bản có 10 lỗi/s đều đặn khác kịch bản 100 lỗi/s trong một khoảng ngắn rồi không có lỗi. Với log sync hoặc queue giới hạn, hai kịch bản có thể gây độ trễ rất khác. Các con số ở đây chỉ để minh họa cách phân bố, **không phải số đo thực nghiệm**.

### Đừng để cơ chế tạo lỗi làm thay đổi workload ngoài ý muốn

Nếu fault injection được thực hiện bằng cách chủ động `Thread.sleep(500)` trước khi ném, bạn đang đo thêm **500 ms chờ** chứ không chỉ chi phí exception. Nếu thử timeout network, thời gian chờ là một phần đúng của scenario end-to-end nhưng không phải chi phí `throw`. Gắn nhãn scenario chính xác:

- `ValidationException` tại chỗ, không I/O.
- `IOException` do fake adapter ném ngay, không network.
- Timeout thật do network hoặc fake delay có chủ đích.
- Lỗi DB/HTTP có transaction, retry, backoff và cleanup thực tế.

Đây là các tình huống hữu ích khác nhau; không trộn thành một “benchmark exception” duy nhất.

## 10. Thay đổi tần suất mà không thêm random overhead vào hot path

Một lỗi thường gặp là:

```java
for (...) {
    if (random.nextDouble() < errorProbability) {
        throw new RuntimeException();
    }
    doWork();
}
```

Đây là snippet minh họa, `random`/`errorProbability`/`doWork` chưa định nghĩa. Nếu mục tiêu là đo chi phí của exception, bạn còn đo cả sinh số ngẫu nhiên, branch và sự thay đổi dữ liệu. Bản thân random có thể là phần workload thật, nhưng lúc đó phải ghi rõ.

Giải pháp phổ biến:

- **Precompute input** hoặc trạng thái phát lỗi trong `@Setup`.
- Dùng cùng dataset cho hai chiến lược.
- Dùng seed cố định nếu cần phép thử ngẫu nhiên có thể lặp.
- Kết hợp các mẫu phân bố khác nhau, không chỉ một pattern có tính dự đoán mạnh.
- Tách **tỷ lệ lỗi** khỏi **thứ tự lỗi**. Cùng 1% nhưng phân bố rải đều/burst/nhóm theo key có thể tạo chênh lệch JIT/cache/concurrency.
- Khi so sánh, đổi thứ tự chạy benchmark hoặc dùng fork độc lập để hạn chế ảnh hưởng nhiệt độ CPU, JIT state và cache state.

Không nhất thiết cấm random trong benchmark. Nếu random là phần công việc production phải làm, nó có thể là đối tượng đo. Quan trọng là **biết mình đang đo gì** và giữ mô hình nhất quán với câu hỏi.

## 11. Đo stack trace, construction, throw và catch riêng

Nếu profiling cho thấy exception allocation đáng kể, có thể thiết kế thêm các benchmark phụ:

**Construct-only:** tạo `new IllegalStateException("invalid")` nhưng không `throw`. Phải tiêu thụ object để JIT không xóa công việc một cách vô ích. Chi phí mặc định có thể gồm stack capture.

**Throw cached:** tạo exception ở setup, ném lại object cũ; đo khác biệt chi phí construction. Nhưng stack trace lúc ném lại không phải stack thực tế nơi thất bại; object dùng chung có rủi ro nếu đi nhiều thread. Đây là **kỹ thuật cách ly chi phí để hiểu**, không phải mẹo production nên áp dụng tự động.

**Throw new local:** tạo rồi ném/catch ngay, không log và không cleanup. So với construct-only để hiểu thêm phần control-flow/unwind, nhưng hiệu số giữa hai benchmark **không phải phép trừ tuyệt đối chính xác cho từng opcode**, vì JIT/code shape khác nhau.

**Throw across depth:** có chuỗi nhiều method và số frame thay đổi; phân biệt stack-capture với việc rời scope có `finally`.

**Log disabled/enabled:** benchmark riêng chi phí tạo message, render stack và appender. Một call log với formatting nặng dù level tắt có thể vẫn tốn CPU nếu biểu thức được đánh giá eager.

**Cleanup failure:** nếu `close()` cũng ném, chi phí khác vì Java phải giữ suppressed exception. Chỉ đưa vào scenario nếu đường này có ý nghĩa.

Cần ghi rõ mức bảo đảm thông tin lỗi. Exception không stack trace bằng constructor `Throwable(String, Throwable, boolean, boolean)` có thể tạo đường nhanh hơn nhưng **đánh đổi khả năng chẩn đoán**. Không so sánh với exception đầy đủ rồi coi chúng có hợp đồng tương đương.

### Cẩn thận với tối ưu fast-throw của HotSpot

Một số phiên bản/cấu hình HotSpot có tối ưu cho một số exception **do JVM tự sinh thường xuyên** tại cùng vị trí, ví dụ implicit `NullPointerException` hoặc bounds check. Stack trace có thể bị lược bỏ trong hoàn cảnh đặc biệt. Không suy rộng kết quả ấy sang `throw new DomainException(...)` hoặc mọi JVM.

Nếu benchmark cho số bất thường nhỏ sau warm-up, kiểm tra loại exception, JVM version/flags, số frame stack và assembly/profile nếu cần. **Không dùng behavior phụ thuộc HotSpot làm hợp đồng API ổn định**.

## 12. Logging: biến số cần đo tách biệt

Ghi log exception có thể vượt xa chi phí throw/catch nếu phát nhiều stack trace. Một phép thử production nên ghi rõ:

| Cấu hình | Điều đang đo | Điều dễ bị bỏ sót |
| --- | --- | --- |
| Không log | Constructor + throw/catch + handler nhẹ | Không đại diện đường vận hành có audit bắt buộc. |
| Log tắt theo level | Overhead kiểm tra level/chuẩn bị message | Biểu thức log eager vẫn có thể chạy trước khi logger quyết định bỏ qua. |
| Log message, không stack | Định dạng và appender cho thông điệp ngắn | Ít thông tin điều tra lỗi. |
| Log stack đầy đủ | Render frames/cause/suppressed, I/O | Chi phí tăng theo depth và chain, số dòng log. |
| Async logging | Enqueue và consumer xử lý nền | Hàng đợi đầy có thể block hoặc drop theo policy. |
| Sampling/rate limit | Lọc phần lớn log trong burst | Phải giữ metric tổng số lỗi để không che incident. |

Với `java.util.logging` có thể dùng dạng message supplier để hoãn phần formatting nếu không bật level:

```java
logger.log(Level.FINE, () -> expensiveMessage(context));
```

Đây là **snippet minh họa**, `logger`/`context`/`expensiveMessage` giả định đã có, cần import `java.util.logging.Level`. Supplier giúp tránh tạo message khi không cần trong cấu hình phù hợp, nhưng không làm chi phí của exception đã được tạo biến mất.

Không log nguyên payload, token, thông tin xác thực, SQL chứa dữ liệu nhạy cảm hoặc toàn bộ context cá nhân chỉ để benchmark có nhiều byte hơn. Log độ nặng giả định phải được tạo bằng dữ liệu kiểm thử không nhạy cảm.

### Log storm trong lỗi burst

Nếu 10.000 lỗi cùng ghi stack trace một lúc, logger có thể:
- Tăng mạnh allocation và GC;
- Chờ đồng bộ khi ghi console/file;
- Tăng queue size và block worker;
- Làm rơi bản ghi hoặc tạo thời gian chờ dài;
- Chiếm băng thông và dung lượng lưu trữ;
- Làm chậm việc xử lý các request không lỗi.

Vì thế khi đánh giá throughput/latency của dịch vụ, không chỉ bật/tắt `logger` rồi đo trung bình. Cần quan sát **log rate, queue occupancy, dropped log count, CPU appender và tốc độ drain** khi lỗi dồn theo thời gian.

## 13. Metric nên ghi cùng benchmark

Một bảng báo cáo tối thiểu có thể gồm:

| Metric | Đơn vị | Ý nghĩa |
| --- | --- | --- |
| Throughput | request/s hoặc record/s | Công việc hoàn thành trên đơn vị thời gian. |
| Mean latency | ms/operation | Trung bình; dễ bị ảnh hưởng bởi tỷ lệ success/failure. |
| p50/p95/p99 latency | ms | Phân phối độ trễ; đặc biệt quan trọng với incident. |
| Allocation rate | MB/s hoặc B/op | Áp lực cấp phát, stack trace, logging. |
| GC pause/frequency | ms/count | Ảnh hưởng đến tail latency/throughput. |
| CPU utilization | % theo core/process | Tải xử lý thực, không chỉ thời gian chờ. |
| Error/exception rate | lỗi/s và % request | Kiểm chứng scenario; tránh suy từ log count. |
| Log volume/drop/block | records/s, bytes/s, count, ms | Chi phí và mức độ mất dữ liệu quan sát. |
| Queue depth | số job/log record | Dấu hiệu backpressure/saturation. |
| Retry attempts | attempts/request | Phân biệt load gốc và tải phát sinh từ retry. |
| Resource usage | file descriptors, connections, threads | Tình trạng cleanup khi lỗi liên tiếp. |

Với JMH microbenchmark, kết quả `ns/op`, `ops/s` và confidence/error estimates hữu ích; chúng **không tự mang p99 latency của request end-to-end**. Muốn p99 cho dịch vụ, dùng công cụ tải/quan sát phù hợp có timestamp và kiểm soát workload. Nếu đo latency từ phía client, lưu ý network, TLS, scheduling và queueing.

### Quan sát bằng Java Flight Recorder và profiler

Java Flight Recorder (JFR) và profiler có thể hỗ trợ tìm hot spot, phân tích allocation, CPU, pause và exception activity theo khả năng/sự kiện đã bật. Khi thu thập cần ghi cấu hình recording và overhead của profiling. **Không giả định mọi sự kiện exception đều được ghi đầy đủ trong mọi cấu hình mặc định của JFR**, vì event settings/threshold có thể khác.

Một quy trình thực dụng:

1. Chạy baseline healthy và degraded với cùng JVM/cấu hình và dữ liệu đã kiểm soát.
2. Thu throughput/latency/allocations/GC/log metrics.
3. Profile để thấy chi phí ở constructor exception, stack trace, logger, serializer, I/O hay business code.
4. Chỉ tối ưu bộ phận chiếm đáng kể tài nguyên.
5. Chạy lại cùng scenario và xác nhận không giảm correctness, observability hay khả năng chẩn đoán.

Không nên tắt stack trace hoặc nuốt exception trước khi biết đây thật sự là bottleneck.

## 14. Benchmark phải phân biệt “request thành công sau retry” với “attempt lỗi”

Ví dụ một request gọi dependency ba lần:

```text
Request #1:
  attempt 1: IOException
  attempt 2: IOException
  attempt 3: success
Result: request thành công, nhưng 2 exception đã phát sinh.
```

Nếu hệ thống báo 0% request lỗi nhưng mỗi request retry hai lần, exception rate có thể rất cao. Benchmark theo `request` phải mô phỏng retry policy và backoff nếu đây là hành vi production; benchmark theo `attempt` nên gắn nhãn riêng.

Đừng so sánh một phiên bản “không retry” với một phiên bản “có retry” rồi kết luận phiên bản sau chậm vì exception. Nó đang làm nhiều công việc hơn.

Khi retry trên lỗi timeout, cần biết attempt cũ còn đang chạy hay không, có side effect không và có idempotency không. Không cần thiết kế benchmark làm phát sinh giao dịch thật ngoài môi trường kiểm thử chỉ để có “lỗi thực tế”.

## 15. Concurrency, pooling và ảnh hưởng của lỗi đến thread

Một phép thử một thread và một vòng lặp trong memory không phản ánh:

- Executor queue đang có backlog;
- Thread worker bị chặn khi logger sync;
- Cùng dependency không hoạt động làm hàng nghìn request retry;
- Circuit breaker chuyển trạng thái;
- Semaphore hoặc connection pool cạn vì cleanup không đúng;
- GC pause ảnh hưởng đồng thời nhiều request;
- Tail latency tăng khi một lượng nhỏ request giữ tài nguyên quá lâu.

Trong load test nên đo số worker và số request đồng thời, tránh dùng số thread cao không kiểm soát khiến benchmark đo thread scheduling overhead thay vì exception. Nếu workload là HTTP server, theo dõi open-loop request arrival rate (phát request theo tốc độ định trước) hoặc closed-loop concurrency có chủ đích và ghi rõ mô hình tải.

Một bẫy là **coordinated omission**: nếu tool sinh request mới chỉ sau khi request trước trả kết quả, lúc server chậm, nó tự giảm tải và không ghi nhận những request đáng lẽ đã đến. Khi cần SLO/tail latency, chọn mô hình tải và phép đo tránh che khoảng thời gian hệ thống quá tải.

## 16. Đảm bảo độ công bằng giữa hai implementation

Hai phương án muốn so sánh phải được đánh giá về **cùng khối lượng công việc và cùng hợp đồng**:

- Cùng input dataset/tỷ lệ lỗi/thứ tự input theo scenario.
- Cùng kết quả thành công và cùng cách xử lý lỗi.
- Cùng mức độ logging/metrics nếu đang đo end-to-end.
- Cùng tần suất retry, giới hạn deadline, cleanup.
- Cùng JVM flags, heap/GC, hardware, container limits.
- JMH warm-up/fork và môi trường ổn định tương đương.
- Không cho một phiên bản bỏ validation hoặc bỏ I/O rồi tuyên bố “nhanh hơn”.

Nếu phương án result type không cung cấp stack trace còn phương án exception có full trace, chúng **khác semantics**. Vẫn có thể so sánh để quyết định thiết kế, nhưng kết luận phải nêu rõ trade-off thay vì gọi đó là tối ưu “miễn phí”.

### Chống tối ưu hóa benchmark không thực tế

Các lỗi thường gặp:

- Kết quả bị bỏ qua nên JIT loại bỏ tính toán;
- Giá trị đầu vào là hằng mà JIT có thể suy ra luôn;
- Dữ liệu được tạo ngẫu nhiên trong vùng đo nhưng production đã có dữ liệu;
- Benchmark chỉ ném một exception singleton nên mất construction/stack capture;
- Benchmark chỉ dùng một loại `NullPointerException` implicit đang được JVM tối ưu đặc biệt;
- So sánh các phép đo chạy trên CPU bị thermal throttling khác nhau;
- Fork quá ít, chỉ lấy một số “nhanh nhất”;
- Lấy kết quả benchmark run không lỗi để dự đoán hiệu năng error burst.

JMH giúp giảm một số nguy cơ, nhưng **không tự sửa một workload sai**. Review kịch bản quan trọng hơn việc thêm nhiều annotation.

## 17. Báo cáo benchmark nên có cấu trúc thế nào?

Một báo cáo có thể dùng khung:

```text
Mục tiêu: đo chi phí lựa chọn xử lý lỗi cho parser batch.
JDK: <version và vendor thực tế>
JMH: <version thực tế>
Hardware/OS: <CPU, memory, giới hạn container thực tế>
GC/JVM flags: <giá trị thực tế>
Work unit: một record / một batch 10000 record
Scenario: 0%, 0.1%, 1%, 10%, 50%, 100% lỗi
Exception: IllegalArgumentException tạo mới; catch tại chỗ
Logging: OFF trong JMH; scenario riêng khi logging ON
Warm-up/measurement/forks: <giá trị thực tế>
Output: throughput, average time, B/op, GC metrics
Load test: request/s, p50/p95/p99, queue/log backpressure
Correctness: output và số lượng lỗi khớp giữa hai phương án
Kết luận: <thay đổi nào giúp workload nào, đánh đổi ra sao>
```

Đây là **mẫu báo cáo**, không phải số liệu đo và các mục giữa dấu `<...>` chỉ là tên trường cần điền từ phép thử thật. Không điền số giả để tạo vẻ đã benchmark.

Mỗi kết luận nên giới hạn bởi dữ liệu:

> Trong môi trường X, với input Y và tỷ lệ lỗi Z, phương án A có B/op thấp hơn phương án B và throughput tốt hơn; kết quả chưa chứng minh tác động lên p99 khi log được bật.

Không nên kết luận:

> Exception Java luôn chậm gấp 1000 lần và không nên dùng.

Đây là khẳng định thiếu điều kiện, không có một benchmark phù hợp mọi workload và mọi JVM.

## 18. Tình huống áp dụng: validation, parse và lỗi hệ thống

### 18.1. Lỗi validation hiếm

Một API công khai nhận phần lớn request hợp lệ, chỉ khoảng 0,1% vi phạm ràng buộc. Nếu hệ thống đã có exception rõ loại và hợp đồng tốt, độ trễ trung bình có thể không đáng kể. Benchmark đường thành công với `try/catch` và đường lỗi hiếm; đừng vội sửa API chỉ vì thấy benchmark 100% throw chậm.

### 18.2. Parser batch thường gặp dữ liệu sai

Nếu 20–40% record lỗi là **trạng thái được dự kiến**, việc ném exception cho từng record có thể tốn allocation/GC đáng kể. Một result type như `ValidationResult` có thể phù hợp hơn. Nhưng phải giữ thống kê lỗi và vị trí record; tối ưu không được xóa thông tin kiểm tra cần thiết.

### 18.3. Lỗi mạng diện rộng

Một lỗi network timeout có thể tiêu tốn hàng trăm milliseconds chờ; chi phí tạo `IOException` có thể rất nhỏ so với thời gian chờ. Nhưng khi hàng chục nghìn request đồng thời nhận exception rồi ghi full stack, logging/GC có thể trở thành tải lớn. Đo end-to-end, retry và log throughput, đừng chỉ đo throw/catch tại chỗ.

### 18.4. Worker bị ngắt và dọn dẹp tài nguyên

Nếu tác vụ bị cancel, `InterruptedException` có thể khiến `finally`/`try-with-resources` đóng socket, trả connection hoặc unlock. Benchmark đường cancel phải tính thời gian cleanup thật nếu câu hỏi là **thời gian shutdown**; không được bỏ cleanup để có con số đẹp.

### 18.5. CompletableFuture và task failure

`CompletableFuture` có thể lưu exceptional completion; `join`/`get` còn tạo hoặc đưa ra wrapper `CompletionException`/`ExecutionException` theo API. Nếu workload async, đo cả pipeline/callback, queueing, executor và cách fallback ảnh hưởng kết quả. Microbenchmark ném exception trên một thread không đại diện cho độ trễ hoàn thành cả async pipeline.

## 19. Những lỗi phổ biến khi đánh giá performance

**Luôn ném exception trong benchmark rồi áp dụng số liệu cho production có 0,1% lỗi.** Đây là sai mô hình tần suất. Phải đo workload mixed với tỷ lệ thực.

**Lấy số lượng log làm số lượng exception.** Một exception có thể bị log nhiều lần hoặc không được log; dùng counter ở boundary phù hợp.

**Giả định mọi lỗi giống nhau.** Validation, timeout, OOM, I/O, cancellation có chi phí và chính sách khác nhau.

**Dùng `System.nanoTime` một lần rồi chọn kết quả thấp nhất.** Chưa xử lý warm-up, JIT, GC, variance và noise môi trường.

**Đo không có control group.** Muốn nói throw/catch tạo overhead bao nhiêu, cần xác định baseline có cùng logic thành công/validation.

**Không kiểm tra tính đúng của dataset.** Nghĩ chạy ở 1% lỗi nhưng dữ liệu thật sinh 0% hoặc 100%, dẫn đến kết luận sai.

**Tạo random, format message và ghi log trong benchmark throw nhưng không ghi nhận.** Chi phí đang bị trộn; tách bài kiểm tra hoặc ghi rõ đó là workload end-to-end.

**Dùng cached exception nhưng suy rộng sang throw new.** Tái sử dụng object bỏ qua constructor/backtrace và thay stack trace semantics.

**Tắt stack trace chỉ để giảm ns/op.** Có thể làm mất thông tin điều tra production; cần chứng minh lợi ích và đánh đổi.

**Đo trung bình nhưng bỏ p99.** Exception burst có thể khiến một số request chậm nghiêm trọng dù mean chỉ tăng nhẹ.

**Không xét queue/log backpressure.** Async logging không làm chi phí biến mất mà chuyển sang consumer và hàng đợi.

**So sánh khác JDK/hardware/configuration.** Một số kết quả phụ thuộc JVM, JIT, GC và flag như fast-throw; cần ghi môi trường.

**Không chạy lại sau tối ưu.** Một kết luận có giá trị phải dựa trên phép đo lặp, cùng scenario và kiểm tra regression đúng.

## 20. Checklist thực hành

Trước khi tin một benchmark exception, hãy xác nhận:

- [ ] Đã xác định **câu hỏi đo**: constructor, throw/catch, logging hay end-to-end?
- [ ] Đã có baseline đường thành công và xử lý lỗi tương đương.
- [ ] Đã xác định **đơn vị**: invocation, record, request, task hay retry attempt.
- [ ] Đã có tỷ lệ lỗi dựa trên dữ liệu hoặc **đánh dấu rõ giả định**.
- [ ] Có ít nhất một kịch bản lỗi hiếm và một kịch bản lỗi cao/burst phù hợp.
- [ ] Input có cùng phân phối/kích thước giữa các implementation; đã test đếm lỗi.
- [ ] Mọi giá trị trả về được tiêu thụ; không vô tình benchmark code bị JIT loại bỏ.
- [ ] JMH có cấu hình warm-up, measurement, forks, threads và trạng thái rõ ràng.
- [ ] Log enabled/disabled, message formatting và output destination được ghi nhận.
- [ ] GC/allocation metrics được xem cùng CPU/throughput.
- [ ] Khi cần SLO, đã đo p95/p99 bằng load test có tải và concurrency phù hợp.
- [ ] Không lược bỏ cleanup, cause/stack trace hoặc validation để đạt số nhanh hơn.
- [ ] Báo cáo có môi trường, version, lệnh chạy và mức sai số; không dùng kết quả giả.
- [ ] Đã thử lại trên workload thực tế và kiểm tra correctness sau tối ưu.

## 21. Bài tập tự kiểm tra

**Câu 1.** API có 1 triệu request, 1.000 request lỗi. Benchmark ném exception trong tất cả 1 triệu iteration có đại diện tỷ lệ production không?

**Trả lời:** Không. Tỷ lệ request lỗi là `1000/1_000_000 = 0,1%`. Một phép thử 100% throw có thể là stress scenario, nhưng phải có phép thử 0,1% để mô phỏng bình thường.

**Câu 2.** Hai benchmark cùng 10% lỗi. A rải đều, B dồn hết lỗi vào giai đoạn đầu. Kết quả end-to-end có thể khác không?

**Trả lời:** Có. Burst có thể làm queue log đầy, contention, retry và GC tăng đột ngột. Hai tỷ lệ tổng bằng nhau không bảo đảm cùng p99.

**Câu 3.** Benchmark `throw new IllegalArgumentException` so với `throw cachedException` chênh đáng kể. Có nên dùng cached exception trong production?

**Trả lời:** Không kết luận vội. Cached exception bỏ qua chi phí tạo/stack capture ở mỗi lần và làm stack trace có thể không còn phản ánh nơi thất bại; cần xem contract, chẩn đoán và độ chính xác trước khi đánh đổi.

**Câu 4.** Một vòng `try/catch` không xảy ra lỗi có phải trả chi phí đầy đủ của stack trace không?

**Trả lời:** Không. Nếu không tạo Throwable và không throw, stack trace của exception không phát sinh chỉ vì có cấu trúc `catch`.

**Câu 5.** Ở đường lỗi I/O, phần lớn thời gian là timeout 2 giây. Việc tối ưu constructor exception có thể giảm p99 đáng kể không?

**Trả lời:** Không thể khẳng định. Nếu thời gian chờ I/O chiếm phần lớn, constructor có thể không phải bottleneck. Cần profiling và tách thời gian chờ, cleanup, retry, log.

**Câu 6.** JMH cho biết một phương án nhanh hơn 20% trong 100% failure, nhưng prod chỉ lỗi 0,1%. Có thể công bố toàn dịch vụ nhanh hơn 20% không?

**Trả lời:** Không. Cần đo mix thực tế và end-to-end. Kết quả cho trường hợp 100% failure không tự suy ra mức cải thiện toàn bộ workload.

## 22. Kết luận

**Đo exception đúng nghĩa là đo đúng workload.** Mỗi phép đo cần biết tỷ lệ lỗi, phân bố theo thời gian, đơn vị công việc, loại lỗi, đường catch/cleanup/logging và mục tiêu metric. Dùng JMH để khám phá chi phí vi mô dưới cấu hình có kiểm soát; dùng load test, JFR/profiler và metrics để xác nhận ảnh hưởng trong hệ thống thật. Đối chiếu thành công, lỗi hiếm, lỗi nhiều và error burst; không suy từ một phép thử 100% throw thành kết luận tuyệt đối cho mọi ứng dụng.

Tối ưu đáng tin là tối ưu **được đo, có thể tái lập và không làm thay đổi hợp đồng hoặc mất khả năng chẩn đoán**. Giảm exception trong đường nóng khi đó là trạng thái dự kiến có thể có ích; nhưng không hy sinh validation, stack trace cần thiết, cause, cleanup và logging có trách nhiệm chỉ để có con số ns/op nhỏ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
