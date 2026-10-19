<a id="back-to-top"></a>

# Nguyên nhân và lỗi phụ

## Menu
- [Exception chaining và ý nghĩa của quan hệ cause](#cause-and-suppressed-001)
- [Giữ nguyên nguyên nhân khi bọc ngoại lệ](#cause-and-suppressed-002)
- [Phân biệt ngoại lệ ngoài cùng với nguyên nhân sâu nhất](#cause-and-suppressed-003)
- [Suppressed exception và quan hệ với try-with-resources](#cause-and-suppressed-004)
- [Phân biệt cause với suppressed exception](#cause-and-suppressed-005)
- [Nhiều lần close thất bại và thứ tự lỗi phụ](#cause-and-suppressed-006)
- [Giới hạn với self-cause, self-suppression và giá trị null](#cause-and-suppressed-007)
- [Đọc cây ngoại lệ thay vì chỉ đọc dòng thông điệp cuối](#cause-and-suppressed-008)
- [Giữ cả lỗi chính và lỗi cleanup khi quản lý tài nguyên thủ công](#cause-and-suppressed-009)

## <a id="cause-and-suppressed-001">Exception chaining và ý nghĩa của quan hệ cause</a>

<details>
<summary>Xem chi tiết</summary>

# Exception chaining và ý nghĩa của quan hệ `cause` trong Java

**Exception chaining** (liên kết chuỗi ngoại lệ) là kỹ thuật để một đối tượng ngoại lệ **giữ tham chiếu đến ngoại lệ đã gây ra nó**. Trong Java, liên kết này được biểu diễn chủ yếu qua `Throwable.getCause()` và các constructor nhận tham số `Throwable cause`.

Giả sử lúc khởi động một ứng dụng, tầng đọc tệp thất bại vì `IOException`. Tầng cấu hình hiểu rằng *không thể nạp cấu hình*, còn tầng dịch vụ hiểu rằng *không thể khởi động dịch vụ*. Nếu mỗi tầng chỉ thay lỗi cũ bằng một lỗi mới mà không giữ nguyên nhân, thông tin chẩn đoán sẽ mất. Exception chaining cho phép vừa **diễn đạt vấn đề đúng ngữ cảnh của mỗi tầng**, vừa **giữ nguyên dấu vết nguyên nhân gốc**.

~~~text
StartupException: Không thể khởi động dịch vụ
    |
    +-- cause --> ConfigurationException: Không thể nạp cấu hình
                      |
                      +-- cause --> IOException: Không tìm thấy tệp
~~~

Ở ví dụ trên, người dùng tầng dịch vụ nhìn thấy lỗi `StartupException`, trong khi người điều tra vẫn có thể lần theo `getCause()` để biết lỗi I/O ban đầu.

**Điểm cốt lõi:** `cause` là **liên kết đến một đối tượng `Throwable` khác**, không phải chuỗi thông điệp được sao chép, không phải stack trace và cũng không phải danh sách suppressed exceptions. Bài học này giải thích cách xây dựng chuỗi ngoại lệ, ý nghĩa của từng tầng, những lựa chọn khi xử lý và các lỗi thiết kế thường gặp trong Java 17+.

## 1. Vì sao cần exception chaining?

Một chức năng phần mềm thường trải qua nhiều tầng:

~~~text
Ứng dụng / giao diện
    |
    v
Service / nghiệp vụ
    |
    v
Repository / cấu hình
    |
    v
File system / database / network
~~~

Khi tầng thấp gặp lỗi, tầng cao có thể không muốn để lộ trực tiếp loại lỗi thấp tầng trong API. Ví dụ, API khởi động dịch vụ có thể chọn `ServiceStartupException` thay vì bắt bên gọi hiểu tất cả các biến thể `IOException`.

Nhưng nếu chỉ làm:

~~~java
catch (java.io.IOException error) {
    throw new IllegalStateException("Khởi động thất bại");
}
~~~

thì ngoại lệ mới không có cause. `getCause()` trả `null`; stack trace và thông điệp của `IOException` ban đầu không được giữ trong quan hệ nguyên nhân của ngoại lệ mới.

Một cách biểu đạt tốt hơn:

~~~java
catch (java.io.IOException error) {
    throw new IllegalStateException(
            "Khởi động thất bại vì không đọc được cấu hình", error);
}
~~~

Các đoạn này là **mảnh mã trong ngữ cảnh một `try` có thể ném `IOException`**, không phải chương trình độc lập. Đoạn thứ hai giữ:

- **Kiểu của lỗi tầng ngoài:** `IllegalStateException`.
- **Message tầng ngoài:** tại sao thao tác ở tầng này thất bại.
- **Cause:** chính đối tượng `IOException` ban đầu.
- **Stack trace riêng:** đường gọi tại nơi tạo ngoại lệ tầng ngoài.
- **Stack trace của cause:** nguồn gốc lỗi ở tầng thấp, được giữ trong đối tượng cause.

Nhờ vậy, phần mềm có thể tách **hợp đồng xử lý ở tầng cao** khỏi **thông tin kỹ thuật để chẩn đoán**.

## 2. Một mắt xích của chuỗi được biểu diễn như thế nào?

Mỗi `Throwable` có thể giữ một nguyên nhân trực tiếp:

~~~java
java.io.IOException root =
        new java.io.IOException("Không tìm thấy tệp");

IllegalStateException outer =
        new IllegalStateException("Không thể tải cấu hình", root);

System.out.println(outer.getCause() == root); // true
~~~

Kết quả `true` chứng minh `getCause()` trả về **cùng tham chiếu đối tượng** `root`, không phải một ngoại lệ mới được sao chép từ message.

Các API liên quan:

| API | Vai trò |
| --- | --- |
| `getMessage()` | Trả thông điệp của **chính ngoại lệ đang xét** |
| `getCause()` | Trả **nguyên nhân trực tiếp** của ngoại lệ, nếu có |
| `initCause(Throwable)` | Thiết lập cause một lần nếu trạng thái cause chưa được khởi tạo |
| `getStackTrace()` | Trả dấu vết lời gọi đã ghi cho **chính đối tượng đó** |
| `getSuppressed()` | Trả danh sách các lỗi phụ được đính kèm, **khác cause** |
| `printStackTrace()` | Hiển thị lỗi và thường cả thông tin cause/suppressed để chẩn đoán |

**`getCause()` chỉ đi một tầng.** Nếu A bọc B và B bọc C, thì:

~~~text
A.getCause() == B
B.getCause() == C
C.getCause() == null
~~~

Nếu muốn tìm lỗi gốc sâu nhất, cần đi qua từng `getCause()`. Việc “sâu nhất” thường hữu ích để điều tra, nhưng **lỗi quan trọng nhất cho người gọi chưa chắc là lỗi sâu nhất**. Tầng nghiệp vụ có thể cần xử lý theo loại ngoại lệ bên ngoài chứ không theo nguồn gốc vật lý thấp tầng.

## 3. Constructor thông thường để thiết lập cause

Các lớp ngoại lệ phổ biến có một số dạng constructor như:

~~~java
new IllegalStateException("Thông điệp");
new IllegalStateException("Thông điệp", original);
new IllegalStateException(original);
~~~

Đây là mảnh minh họa có biến `original` là `Throwable` phù hợp.

### Dạng `(String message, Throwable cause)`

Thường là lựa chọn rõ ràng nhất khi **bọc lỗi và bổ sung ngữ cảnh**:

~~~java
throw new IllegalStateException(
        "Không thể hoàn tất nghiệp vụ nhập dữ liệu", original);
~~~

- Message mô tả thất bại ở tầng hiện tại.
- Cause giữ ngoại lệ trước đó.
- Caller có thể đọc riêng từng phần.

### Dạng `(Throwable cause)`

Một số lớp, như `IllegalStateException`, có overload cause-only:

~~~java
IllegalStateException wrapper =
        new IllegalStateException(
                new java.io.IOException("Kết nối bị ngắt"));
~~~

Với các constructor cause-only dựa trên `Throwable`, message của wrapper thường được tạo từ `cause.toString()` nếu cause khác `null`. Điều đó thường tạo chuỗi có **tên lớp của cause** lẫn message.

Nếu tầng hiện tại có một hành động thất bại cần diễn giải, nên cân nhắc overload `(String, Throwable)`.

### Constructor không được kế thừa

Không phải mọi lớp con của `Throwable` đều có sẵn mọi overload. Constructor **không được kế thừa** như phương thức thông thường. Nếu khai báo exception riêng, cần chủ động cung cấp overload nhận cause khi phù hợp:

~~~java
public class ConfigurationException extends Exception {
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Đoạn trên là một **lớp độc lập** nếu lưu trong `ConfigurationException.java`. Việc gọi `super(message, cause)` mới chuyển dữ liệu chẩn đoán tới cơ chế của `Throwable`.

## 4. Ba cách xử lý lỗi: phục hồi, ném lại, bọc với cause

Không phải mọi ngoại lệ xuất hiện đều nên được bọc. Khi viết khối `catch`, hãy xem tầng hiện tại có đủ thông tin để giải quyết vấn đề hay không.

### Cách A — Phục hồi: không cần lan truyền lỗi

~~~java
static int readLimitOrDefault(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        return 10;
    }
}
~~~

Phương thức minh họa này chọn `10` nếu chuỗi không phải số. Cách đó chỉ hợp lý nếu hợp đồng cho phép một giá trị mặc định khi dữ liệu hỏng. Khi đã phục hồi thành công, **không cần ném một wrapper chỉ để cho thấy đã từng có ngoại lệ**.

Nếu dữ liệu bắt buộc chính xác, việc trả mặc định có thể che lỗi — phải thiết kế theo yêu cầu nghiệp vụ.

### Cách B — Ném lại: giữ đúng đối tượng và loại lỗi

~~~java
static String readRequired(java.nio.file.Path path)
        throws java.io.IOException {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException e) {
        System.err.println("Không đọc được tệp bắt buộc");
        throw e;
    }
}
~~~

Đây là phương thức Java hợp lệ; `throw e;` ném lại **cùng đối tượng `IOException`**, không tạo một mắt xích cause mới. Nó giữ loại checked exception ban đầu theo hợp đồng.

Cần tránh ghi cùng một lỗi ở quá nhiều tầng: có thể gây log trùng, nhiễu tín hiệu và khó đếm số sự cố thực tế. Nếu tầng hiện tại không bổ sung quyết định hữu ích, đôi khi **không bắt rồi ném lại** là cách đơn giản hơn.

### Cách C — Bọc: đổi ý nghĩa lỗi nhưng giữ nguyên nhân

~~~java
static String loadServiceConfig(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Không thể chuẩn bị cấu hình dịch vụ", cause);
    }
}
~~~

Phương thức này đổi từ checked `IOException` sang unchecked `IllegalStateException`. Đây là một **quyết định API**: caller tầng trên giờ nhận lỗi thuộc hợp đồng khác, nhưng có thể truy tìm `IOException` thông qua cause.

Không nên tự động dùng kiểu unchecked chỉ để khỏi viết `throws`. Nếu caller thực sự có cách xử lý lỗi I/O, việc giữ checked `IOException` hoặc dùng checked exception nghiệp vụ có thể thích hợp hơn.

## 5. Exception chaining qua nhiều tầng

Giả sử:

- Tầng I/O phát sinh `IOException`.
- Tầng cấu hình đổi nó thành `ConfigurationException`.
- Tầng khởi tạo dịch vụ đổi nó thành `StartupException`.

Mô hình mã:

~~~java
static void initializeService() {
    try {
        loadConfiguration();
    } catch (ConfigurationException cause) {
        throw new StartupException(
                "Không thể khởi động dịch vụ", cause);
    }
}
~~~

Đây là đoạn minh họa; `loadConfiguration()` và hai lớp ngoại lệ cần được định nghĩa. Chuỗi có thể mang ý nghĩa:

| Tầng | Loại lỗi | Message | Vai trò |
| --- | --- | --- | --- |
| Service | `StartupException` | Dịch vụ không thể khởi động | Hợp đồng tầng ứng dụng |
| Configuration | `ConfigurationException` | Không nạp được cấu hình | Ngữ cảnh xử lý cấu hình |
| I/O | `IOException` | Thiếu tệp hoặc kết nối hỏng | Nguyên nhân kỹ thuật ban đầu |

Mỗi tầng tạo một wrapper **chỉ khi có giá trị**. Nếu mọi tầng đều tạo một lỗi với message mơ hồ như `"Failed"`, chain sẽ dài nhưng không bổ sung thông tin.

### Cause là quan hệ “vì sao”, không nhất thiết là quan hệ subtype

Không cần mọi ngoại lệ trong chuỗi phải cùng lớp hoặc có quan hệ kế thừa. `StartupException`, `ConfigurationException` và `IOException` có thể thuộc các nhánh khác nhau của cây lớp ngoại lệ; chúng liên kết với nhau bằng **tham chiếu đối tượng cause**.

Do đó:

~~~java
Throwable cause = outer.getCause();
~~~

là quan hệ đối tượng, không phải phép ép `outer` thành kiểu của nguyên nhân. Đừng nhầm **quan hệ `extends`** giữa lớp với **quan hệ `getCause()`** giữa những lần lỗi cụ thể.

## 6. Cause tác động thế nào đến `catch`?

Một ngoại lệ được bắt theo **kiểu của đối tượng đang truyền tại thời điểm đó**, không tự động theo các kiểu nằm sâu trong cause.

~~~java
try {
    throw new IllegalStateException(
            "Không chạy được",
            new java.io.IOException("Không có tệp"));
} catch (IllegalStateException e) {
    System.out.println("Bắt được lỗi tầng ngoài");
}
~~~

Đoạn này hợp lệ trong một phương thức Java và khối `catch` sẽ được chạy.

Nhưng:

~~~java
try {
    throw new IllegalStateException(
            "Không chạy được",
            new java.io.IOException("Không có tệp"));
} catch (java.io.IOException e) {
    System.out.println("Không tới đây");
}
~~~

Đây là **phản ví dụ không biên dịch** vì khối `try` ở mã trên không có đường ném checked `IOException` trực tiếp; việc một `IllegalStateException` chứa `IOException` làm cause không biến nó thành một `IOException` được ném. Ngay cả khi bố cục `catch` hợp lệ do khối `try` còn lời gọi khác ném I/O, nhánh `catch (IOException e)` **không nhận được riêng đối tượng `IllegalStateException`** đang truyền.

Nếu API muốn caller xử lý tùy nguyên nhân, cần quyết định cách thiết kế hợp đồng: có thể giữ loại checked exception, sử dụng loại lỗi nghiệp vụ riêng hoặc có cơ chế kiểm tra cause **có kiểm soát** tại ranh giới thích hợp. Không biến việc duyệt chuỗi cause thành cách thay thế bừa bãi cho hợp đồng ngoại lệ rõ ràng.

## 7. Cause khác với việc nối chuỗi message

Phản ví dụ:

~~~java
catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Không tải được cấu hình: " + cause.getMessage());
}
~~~

Đoạn trên chỉ nhúng **văn bản** của lỗi gốc vào message mới; `getCause()` của wrapper vẫn thường là `null`.

Phiên bản giữ quan hệ nguyên nhân:

~~~java
catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Không tải được cấu hình", cause);
}
~~~

Hai đoạn là **những mảnh catch minh họa**, không phải một tệp Java độc lập. Phiên bản thứ hai cho phép công cụ:

- Lần theo nguyên nhân gốc có cấu trúc.
- Đọc stack trace riêng của lỗi thấp tầng.
- Nhận biết kiểu lỗi gốc mà không phải phân tích chuỗi.
- Đọc đầy đủ chuỗi cause của nhiều tầng.

Bạn vẫn có thể bổ sung thông tin an toàn vào message như tên tác vụ, tên tham số hoặc mã thao tác, nhưng **đừng chỉ dùng message thay cho cause**.

## 8. Java 17+ — chương trình chạy độc lập

Chương trình sau cố ý mô phỏng thất bại ở ba tầng. Nó có ví dụ thành công, ném lại không bọc, bọc nhiều tầng, cách làm mất liên kết và trường hợp lỗi đóng tài nguyên được giữ ở `suppressed` thay vì `cause`.

Lưu thành tệp **`ExceptionChainingDemo.java`**:

~~~java
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ExceptionChainingDemo {

    static final class ConfigurationException extends Exception {
        ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class StartupException extends RuntimeException {
        StartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class ClosingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failure");
        }
    }

    public static void main(String[] args) {
        System.out.println("== success ==");
        System.out.println("status=" + start(false));

        System.out.println("== chained failure ==");
        try {
            start(true);
        } catch (StartupException e) {
            printChain(e);
            System.out.println("root="
                    + rootCause(e).getClass().getSimpleName());
        }

        System.out.println("== rethrow ==");
        try {
            rethrowOriginal();
        } catch (IOException e) {
            System.out.println("caught=" + e.getMessage());
        }

        System.out.println("== lost cause ==");
        try {
            loseCause();
        } catch (StartupException e) {
            System.out.println("message=" + e.getMessage());
            System.out.println("cause=" + e.getCause());
        }

        System.out.println("== suppressed ==");
        try (ClosingResource ignored = new ClosingResource()) {
            throw new ConfigurationException(
                    "work failed", new IOException("read failure"));
        } catch (Exception e) {
            System.out.println("primary=" + e.getMessage());
            System.out.println("cause=" + e.getCause().getMessage());
            System.out.println(
                    "suppressed=" + e.getSuppressed().length);
            System.out.println(
                    "suppressedMessage="
                            + e.getSuppressed()[0].getMessage());
        }
    }

    static String readFile(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("config missing");
        }
        return "ready";
    }

    static String loadConfig(boolean fail)
            throws ConfigurationException {
        try {
            return readFile(fail);
        } catch (IOException cause) {
            throw new ConfigurationException(
                    "load configuration failed", cause);
        }
    }

    static String start(boolean fail) {
        try {
            return loadConfig(fail);
        } catch (ConfigurationException cause) {
            throw new StartupException(
                    "service startup failed", cause);
        }
    }

    static void rethrowOriginal() throws IOException {
        try {
            readFile(true);
        } catch (IOException e) {
            throw e;
        }
    }

    static void loseCause() {
        try {
            loadConfig(true);
        } catch (ConfigurationException e) {
            throw new StartupException(
                    "text only: " + e.getMessage(), null);
        }
    }

    static void printChain(Throwable error) {
        Set<Throwable> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<Throwable, Boolean>());
        int depth = 0;
        Throwable current = error;

        while (current != null && visited.add(current)) {
            System.out.println(
                    "level" + depth + "="
                            + current.getClass().getSimpleName()
                            + ": " + current.getMessage());
            current = current.getCause();
            depth++;
        }
        if (current != null) {
            System.out.println("cycle detected");
        }
    }

    static Throwable rootCause(Throwable error) {
        Set<Throwable> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<Throwable, Boolean>());
        Throwable current = error;
        Throwable last = error;

        while (current != null && visited.add(current)) {
            last = current;
            current = current.getCause();
        }
        return last;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ExceptionChainingDemo.java
java ExceptionChainingDemo
~~~

**Stdout chính xác:**

~~~text
== success ==
status=ready
== chained failure ==
level0=StartupException: service startup failed
level1=ConfigurationException: load configuration failed
level2=IOException: config missing
root=IOException
== rethrow ==
caught=config missing
== lost cause ==
message=text only: load configuration failed
cause=null
== suppressed ==
primary=work failed
cause=read failure
suppressed=1
suppressedMessage=close failure
~~~

### Giải thích từng tình huống

**Thành công:** `start(false)` gọi qua các tầng, `readFile(false)` trả `"ready"` và không có ngoại lệ nào được tạo trong các khối `catch`.

**Lỗi được liên kết:** `readFile(true)` ném `IOException("config missing")`. `loadConfig` bắt lỗi và tạo `ConfigurationException` có cause là đối tượng `IOException` ấy. `start` bắt `ConfigurationException` rồi tạo `StartupException` chứa nó làm cause. `printChain` đọc từng tầng và `rootCause` tìm tầng cuối.

**Ném lại nguyên bản:** `rethrowOriginal()` bắt `IOException` rồi `throw e;`. Không tạo wrapper hoặc chuỗi cause mới, do đó caller nhận đúng lỗi I/O và message gốc.

**Làm mất cause:** `loseCause()` tạo `StartupException` với tham số cause là `null`, chỉ sao chép một phần message. Caller không thể đi từ wrapper về `ConfigurationException` và `IOException` bằng `getCause()`. Đây là phản ví dụ có chủ đích.

**Lỗi phụ:** Try-with-resources có `ConfigurationException("work failed", IOException("read failure"))` làm lỗi chính; khi `ClosingResource.close()` ném `IOException("close failure")`, Java giữ lỗi đóng trong `getSuppressed()`. Cause của lỗi chính vẫn là `IOException("read failure")`; lỗi suppressed là một đối tượng khác.

## 9. Cách tìm nguyên nhân gốc an toàn

Cách đơn giản, khi bạn kiểm soát hoàn toàn chuỗi và biết nó không có vòng:

~~~java
Throwable current = error;
while (current.getCause() != null) {
    current = current.getCause();
}
~~~

Đây là **mảnh minh họa** giả định `error` không null và chuỗi không có vòng. `current` cuối cùng là một ngoại lệ không có cause.

Nhưng với những lớp `Throwable` tùy biến hoặc dữ liệu từ nhiều nguồn, một chuỗi cause có thể không kết thúc như kỳ vọng. `initCause` ngăn **tự làm cause trực tiếp**, nhưng không phải là lời hứa mọi chuỗi tùy ý đều không có vòng gián tiếp.

Mã độc lập nên có cơ chế phát hiện vòng bằng **identity của tham chiếu** (như `IdentityHashMap` trong chương trình chính). Không phụ thuộc vào `equals()` khi câu hỏi là “đây có phải cùng một đối tượng ngoại lệ hay không?”.

### Nguyên nhân sâu nhất không nhất thiết là kết luận nghiệp vụ

Một `IOException` sâu nhất chỉ nói rằng có lỗi I/O ở tầng thấp; ứng dụng có thể cần xử lý vấn đề ở mức `ConfigurationException` để thông báo cho người dùng hoặc quyết định retry. Đừng tự động bỏ qua mọi mắt xích ngoài và chỉ hiển thị exception cuối.

Ngoài ra, việc đọc chain và chọn thông điệp hiển thị cần cân nhắc dữ liệu nhạy cảm: cause sâu có thể chứa đường dẫn, thông tin kết nối, dữ liệu nhập hoặc thông tin kỹ thuật không nên đưa thẳng ra UI công khai.

## 10. `initCause`: phương án dự phòng khi không có constructor nhận cause

Một số lớp ngoại lệ cũ chỉ có constructor message. Nếu lớp cho phép, `Throwable.initCause(Throwable)` có thể gắn cause **một lần** sau khi tạo đối tượng:

~~~java
Throwable wrapped = new Throwable("legacy wrapper");
wrapped.initCause(new java.io.IOException("original"));
System.out.println(wrapped.getCause().getMessage());
// original
~~~

Dạng này thường ít được ưu tiên hơn:

~~~java
Throwable wrapped = new Throwable(
        "legacy wrapper",
        new java.io.IOException("original"));
~~~

Constructor giúp đối tượng hoàn chỉnh ngay từ đầu và giảm nguy cơ quên gắn nguyên nhân.

### Hai trạng thái có cùng `getCause() == null`

Một khác biệt rất quan trọng:

~~~java
Throwable open = new Throwable("message");
Throwable locked = new Throwable("message", null);

System.out.println(open.getCause());   // null
System.out.println(locked.getCause()); // null
~~~

Dù getter trả `null` ở cả hai, trạng thái cause khác nhau:

- `open`: constructor không nhận cause, vì vậy cause **chưa được khởi tạo**; `initCause(root)` có thể hợp lệ.
- `locked`: constructor có tham số cause đã **khởi tạo cause bằng null**; `initCause(root)` sẽ ném `IllegalStateException`.

Quy tắc `initCause`:

1. Chỉ dùng khi trạng thái cause chưa từng được khởi tạo.
2. Không đặt chính đối tượng đang xét thành cause trực tiếp của nó.
3. Sau lần thiết lập thành công, kể cả `initCause(null)`, không thể thiết lập lần nữa.

### Những lỗi `initCause` có thể ném

~~~java
Throwable a = new Throwable("A");
Throwable b = new Throwable("B");

a.initCause(b);     // Thành công.
a.initCause(null);  // IllegalStateException.
~~~

Đây là đoạn **minh họa có lỗi runtime ở câu cuối**, không phải ví dụ để chạy liên tục mà không xử lý lỗi.

Tự làm cause:

~~~java
Throwable a = new Throwable("A");
a.initCause(a); // IllegalArgumentException.
~~~

Không nên đưa cấu trúc cause vào trạng thái vòng lặp hoặc thay đổi nó ở nhiều vị trí trong luồng xử lý. Thiết kế tốt thường thiết lập cause **lúc tạo exception**, sau đó chỉ đọc nó.

## 11. Chuỗi nguyên nhân và checked/unchecked exceptions

Khi một ngoại lệ được bọc, **kiểu của wrapper** xác định nghĩa vụ checked/unchecked ở ranh giới phương thức hiện tại.

~~~java
static String loadConfiguration(java.nio.file.Path path) {
    try {
        return java.nio.file.Files.readString(path);
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "Cấu hình không khả dụng", cause);
    }
}
~~~

Đây là một phương thức minh họa đầy đủ về cú pháp. `Files.readString` có thể ném checked `IOException`, nhưng phương thức bọc nó thành unchecked `IllegalStateException`. Vì vậy, caller của `loadConfiguration` không bị trình biên dịch yêu cầu `catch IOException` chỉ do cause là `IOException`.

Nếu muốn caller **phải** cân nhắc thất bại của bước cấu hình, có thể dùng lớp riêng kế thừa `Exception`:

~~~java
public class ConfigurationLoadException extends Exception {
    public ConfigurationLoadException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

Lớp trên có thể lưu trong `ConfigurationLoadException.java`. Phương thức ném nó cần bắt hoặc khai báo theo quy tắc checked exception của Java.

**Cause không thay đổi loại checked/unchecked của wrapper.** `catch` ở caller xét kiểu của đối tượng được ném ra ngoài, không tự động duyệt cause để so khớp.

### Không bọc một ngoại lệ chỉ để thoát quy tắc `throws`

Việc đổi checked thành unchecked cần có lý do thiết kế: tầng gọi có khả năng phục hồi hay không, hợp đồng API muốn biểu đạt điều gì, và liệu việc buộc caller bắt ngoại lệ có mang lại lợi ích hay không.

Một câu hỏi hữu ích: **“Caller của tầng này cần biết loại thất bại nào để quyết định hành động tiếp theo?”** Câu trả lời thường quan trọng hơn việc giảm số dòng `try/catch`.

## 12. Exception chaining khác suppressed exceptions

Suppressed exceptions là **lỗi phụ được giữ lại** khi một lỗi chính vẫn được ưu tiên, đặc biệt trong try-with-resources. Chúng **không phải** những mắt xích cause tiếp theo.

Ví dụ:

~~~java
Throwable primary = new Throwable(
        "Lưu dữ liệu thất bại",
        new java.io.IOException("Ghi tệp thất bại"));

primary.addSuppressed(
        new java.io.IOException("Đóng luồng thất bại"));
~~~

Đây là đoạn Java minh họa. Kết quả:

~~~text
Throwable: Lưu dữ liệu thất bại
    |
    +-- cause --> IOException: Ghi tệp thất bại
    |
    +-- suppressed --> IOException: Đóng luồng thất bại
~~~

| Quan hệ | Phương thức đọc | Ý nghĩa |
| --- | --- | --- |
| Cause | `getCause()` | Một lỗi làm nguyên nhân cho lỗi khác |
| Suppressed | `getSuppressed()` | Một hoặc nhiều lỗi phụ được giữ lại mà không thay thế lỗi chính |

Không dùng `addSuppressed` thay cho việc lưu cause khi đang bọc lỗi giữa các tầng. Tương tự, lỗi khi đóng tài nguyên xuất hiện trong cùng quá trình dọn dẹp thường không nên được ép thành cause của lỗi nghiệp vụ ban đầu.

**Một ngoại lệ có thể đồng thời có cause và nhiều suppressed exceptions.** Khi phân tích log, cả hai có thể quan trọng.

## 13. Stack trace qua các mắt xích

Mỗi đối tượng `Throwable` thường có **stack trace riêng**, được ghi lúc tạo đối tượng theo cơ chế mặc định. Khi tạo wrapper mới:

~~~java
try {
    readFile();
} catch (java.io.IOException cause) {
    throw new IllegalStateException(
            "Không thể khởi tạo", cause);
}
~~~

Đây là mảnh mã minh họa trong một phương thức có `readFile()` phù hợp.

- Stack trace của `cause` có thể chỉ ra nơi lỗi I/O được tạo/ném ban đầu.
- Stack trace của wrapper có thể cho biết nơi một tầng đã quyết định chuyển lỗi thành `IllegalStateException`.
- `printStackTrace()` thường trình bày thông tin nhiều tầng dưới dạng quan hệ `Caused by:`.

Không nên kiểm thử đầu ra `printStackTrace()` bằng cách so khớp từng dòng số thứ tự, đường dẫn tệp hay tên frame, vì chúng phụ thuộc điểm chạy và môi trường biên dịch. Trong ví dụ Java chạy được ở phần trên, đầu ra được xây bằng `getMessage()`, `getCause()` và tên lớp để có tính xác định.

### Dùng `throw e;` không tạo wrapper

Nếu ném lại cùng một đối tượng bằng `throw e;`, Java không tự thêm một mắt xích `getCause()` mới. Stack trace của đối tượng đó cũng không tự được viết lại hoàn toàn mỗi lần ném. Khi muốn bổ sung bối cảnh bằng một đối tượng ngoại lệ mới, hãy tạo wrapper với message và cause rõ nghĩa.

### Đừng biến stack trace thành hợp đồng máy đọc

Cause và stack trace có cấu trúc khác nhau. Dựa vào tên frame để chọn cách xử lý nghiệp vụ dễ hỏng khi refactor hoặc thay đổi JVM. Nếu cần quyết định máy móc, thiết kế mã lỗi hoặc loại exception cụ thể.

## 14. Khi nào nên tạo một mắt xích mới?

Một wrapper nên có mục đích. Cân nhắc các câu hỏi sau:

1. **Có ranh giới abstraction không?** Ví dụ từ I/O sang cấu hình, từ cấu hình sang khởi động dịch vụ.
2. **Message mới có bổ sung bối cảnh không?** Ví dụ thông báo tên thao tác hoặc trạng thái nghiệp vụ hợp lệ, không chỉ `"error"`.
3. **Caller sẽ xử lý loại ngoại lệ mới khác loại cũ không?**
4. **Cause có được giữ lại đầy đủ không?**
5. **Việc bọc có làm lộ chi tiết nội bộ vào API công khai không?** Có thể giữ cause cho log nội bộ nhưng dùng thông điệp người dùng riêng ở ranh giới UI.
6. **Có thêm giá trị chẩn đoán hay chỉ lặp lại một lỗi qua nhiều tầng?**

Bảng quyết định:

| Tình huống | Hướng thường hợp lý |
| --- | --- |
| Caller hiểu và xử lý được ngoại lệ gốc | Cho lan truyền hoặc ném lại |
| Tầng hiện tại có fallback hợp lệ | Xử lý và trả kết quả theo hợp đồng |
| Tầng hiện tại cần thay đổi abstraction | Bọc thành loại lỗi phù hợp, giữ cause |
| Chỉ muốn thêm log | Cân nhắc ghi một lần ở ranh giới giám sát, tránh catch/rethrow vô ích |
| Lỗi khi đóng tài nguyên phụ trong lúc đã có lỗi chính | Sử dụng suppressed exceptions theo cơ chế thích hợp |
| Muốn bọc lỗi nhưng chỉ có constructor message | Dùng `initCause` nếu được hỗ trợ hoặc cải tiến loại ngoại lệ |

Một chuỗi lỗi tốt **ngắn vừa đủ nhưng đủ ngữ cảnh**. Mỗi tầng mới cần giúp người đọc hiểu thêm *tại sao thao tác ở tầng đó không thành công*.

## 15. Khai báo lỗi của API và trải nghiệm người dùng

Exception chaining phục vụ hai đối tượng khác nhau:

- **Lập trình viên vận hành:** cần cause, stack trace, mã lỗi và bối cảnh kỹ thuật để điều tra.
- **Người dùng cuối:** cần câu trả lời rõ ràng, an toàn, có thể hành động — chẳng hạn “Không thể tải dữ liệu. Hãy thử lại sau.”

Không nên hiển thị nguyên toàn bộ `toString()` hoặc `printStackTrace()` của chuỗi cause ra giao diện công cộng. Các thông điệp kỹ thuật có thể vô tình chứa đường dẫn tệp, chuỗi kết nối, tham số truy vấn hoặc dữ liệu nhạy cảm.

Ở API công khai, có thể dùng:

- Một mã lỗi ổn định, ví dụ `CONFIG_UNAVAILABLE`.
- Thông điệp cho người dùng không tiết lộ nội bộ.
- Correlation/request ID để tìm log nội bộ tương ứng.
- Cause chain được lưu và xử lý ở hệ thống quan sát đáng tin cậy theo chính sách riêng tư.

**Không phải mọi chi tiết hữu ích trong log đều phù hợp để trả về cho bên gọi bên ngoài.**

## 16. Cẩn thận khi log nhiều lần qua các tầng

Một sai lầm phổ biến:

~~~java
catch (java.io.IOException e) {
    e.printStackTrace();
    throw new IllegalStateException("Service failed", e);
}
~~~

Đoạn này ghi lỗi tại tầng dưới rồi còn chuyển nó lên tầng trên. Nếu tầng trên cũng in stack trace, cùng sự cố có thể xuất hiện nhiều lần, gây khó đếm và làm log dài.

Cách tổ chức thường phù hợp:

- Tầng thấp **giữ nguyên cause**, không nhất thiết log mỗi lần chuyển lỗi.
- Tầng trung gian **bổ sung ngữ cảnh** khi thực sự thay đổi hợp đồng.
- Tầng biên của ứng dụng **ghi lỗi một lần** với đầy đủ chuỗi cause và correlation ID, khi cần.

Ngoại lệ có thể cần log ở nhiều nơi vì các mục tiêu khác nhau, nhưng đó nên là quyết định có chủ ý, không phải mặc định mọi `catch` đều `printStackTrace()`.

## 17. Một số loại wrapper thường gặp trong thư viện Java

Không phải exception chaining chỉ xuất hiện trong ngoại lệ tùy biến. Nhiều API Java dùng wrapper để biểu diễn sự khác biệt giữa lỗi gốc và bối cảnh gọi.

Ví dụ:

- `java.util.concurrent.ExecutionException` thường bọc lỗi của một tác vụ được lấy qua `Future.get()`.
- `java.util.concurrent.CompletionException` có thể bọc lỗi khi một chuỗi xử lý bất đồng bộ hoàn tất bất thường.
- `java.lang.reflect.InvocationTargetException` có thể giữ ngoại lệ được ném bởi phương thức được gọi qua reflection.

Khi làm việc với các API này, cần hiểu **loại wrapper của API** và cách truy cập cause theo hợp đồng. Tuy nhiên, không nên tùy tiện “unwrap” toàn bộ lỗi ở mọi tầng: một số wrapper mang ý nghĩa về cơ chế gọi hoặc ranh giới đồng bộ/bất đồng bộ cần được giữ.

Ví dụ tổng quát:

~~~java
try {
    future.get();
} catch (java.util.concurrent.ExecutionException e) {
    Throwable taskFailure = e.getCause();
    // Xử lý theo hợp đồng của tác vụ và tầng gọi.
}
~~~

Đây là đoạn minh họa; `future` cần là một `Future` và phương thức bao quanh còn phải đáp ứng các ngoại lệ checked khác của `get()` như `InterruptedException`. Đừng xem nó là chương trình độc lập.

## 18. Những sai lầm thường gặp

### Sai lầm 1 — Sao chép message nhưng bỏ cause

`new IllegalStateException("Failed: " + e.getMessage())` có thể làm mất quan hệ nguyên nhân. Dùng overload nhận cause khi cần bảo toàn lỗi gốc.

### Sai lầm 2 — Nhầm cause với lớp cha của exception

`getCause()` trả một **đối tượng lỗi**, không trả superclass trong cây kế thừa. Loại wrapper và loại cause có thể không liên quan về `extends`.

### Sai lầm 3 — Tưởng `catch` so khớp theo toàn chuỗi cause

Không. Handler nhận theo loại ngoại lệ đang truyền. `IOException` nằm trong cause không tự biến wrapper thành `IOException`.

### Sai lầm 4 — Bọc mọi lỗi qua mọi lớp

Chuỗi quá dài, message mơ hồ và log trùng làm việc điều tra khó hơn. Chỉ bọc khi cần ý nghĩa/hợp đồng mới.

### Sai lầm 5 — Thay đổi checked thành unchecked mà không xét hợp đồng

Wrapper quyết định nghĩa vụ biên dịch của caller. Đừng dùng unchecked chỉ để tránh xử lý lỗi được kỳ vọng.

### Sai lầm 6 — Dùng `getCause() == null` để đoán quyền gọi `initCause`

Cause có thể **đã được khởi tạo bằng null**. Getter trả null không chứng minh còn được gọi `initCause(...)`.

### Sai lầm 7 — Gọi `initCause` hai lần

`initCause` không phải setter thay đổi nguyên nhân liên tục. Gọi lại sau khi cause đã khởi tạo ném `IllegalStateException`.

### Sai lầm 8 — Tạo cause tự trỏ tới chính đối tượng

`error.initCause(error)` bị từ chối. Vòng gián tiếp giữa các đối tượng khác nhau cũng có thể khiến công cụ duyệt cause không an toàn nếu không phòng ngừa.

### Sai lầm 9 — Nhầm suppressed exception với cause

Lỗi đóng tài nguyên được giữ làm suppressed **không tự trở thành cause** của lỗi chính.

### Sai lầm 10 — Cho rằng lỗi gốc sâu nhất luôn là lỗi duy nhất quan trọng

Ngoại lệ ở tầng cao có thể mô tả hợp đồng nghiệp vụ rõ hơn, trong khi lỗi sâu nhất chỉ cung cấp chi tiết kỹ thuật.

### Sai lầm 11 — Tưởng wrapper có cause là chỉ cần in message wrapper

Message không thay thế stack trace và thông tin chuỗi nguyên nhân. Khi điều tra, dùng logging có cấu trúc và truyền nguyên đối tượng ngoại lệ đến bộ ghi log phù hợp.

### Sai lầm 12 — Đưa toàn bộ cause ra API công khai

Thông tin cause có thể lộ dữ liệu nội bộ. Tách thông điệp dành cho người dùng với dữ liệu quan sát nội bộ.

### Sai lầm 13 — Tái sử dụng một đối tượng exception cho nhiều sự cố

Cause, message và stack trace của một đối tượng thường phản ánh một bối cảnh cụ thể. Dùng lại cho những lỗi không liên quan dễ khiến việc chẩn đoán sai.

### Sai lầm 14 — Duyệt cause không kiểm tra vòng tham chiếu

Nếu lớp tùy biến hoặc nguồn dữ liệu bất thường tạo vòng, vòng lặp `while (getCause() != null)` có thể không dừng. Các tiện ích chung nên chặn vòng bằng identity.

### Sai lầm 15 — Nghĩ ném lại `throw e;` tạo thêm một mắt xích

Không. Nó ném lại **cùng đối tượng**, không tạo wrapper mới và không tự cập nhật cause.

## 19. Câu hỏi tự kiểm tra

1. Exception chaining giải quyết vấn đề gì khi lỗi đi qua nhiều tầng?
2. `getCause()` trả trực tiếp loại dữ liệu nào?
3. Một exception có thể có message và cause độc lập với nhau không?
4. `outer.getCause() == original` nói lên điều gì?
5. `getCause()` đi đến lỗi sâu nhất hay chỉ một tầng?
6. Khi nào nên `throw e;` thay vì `throw new WrapperException("...", e);`?
7. Khi nào nên phục hồi và không cần lan truyền lỗi?
8. Nếu chỉ nối `e.getMessage()` vào chuỗi, điều gì có thể bị mất?
9. Vì sao `catch (IOException)` không bắt `IllegalStateException` có cause là `IOException`?
10. Quan hệ `extends` giữa lớp và quan hệ cause giữa đối tượng khác nhau thế nào?
11. Nếu wrapper là unchecked nhưng cause là checked, caller có buộc bắt cause theo quy tắc checked không?
12. Suppressed exceptions được dùng cho tình huống nào khác cause?
13. Có thể đồng thời có cause và suppressed exceptions không?
14. Dùng `initCause` trong trường hợp nào là hợp lý?
15. Vì sao `new Throwable("x", null)` không cho `initCause(...)` lần nữa?
16. Việc ném lại cùng một exception có tự tạo wrapper và cause mới không?
17. Tại sao logger có thể cần cả stack trace của wrapper và cause?
18. Vì sao một phương thức tìm lỗi gốc nên bảo vệ khỏi vòng cause?
19. Nguyên nhân sâu nhất có luôn là thông báo tốt nhất cho người dùng không?
20. Tại sao cần tránh in toàn bộ cause chain ra API công khai?
21. Nêu ví dụ một wrapper của thư viện chuẩn Java và vai trò của cause.
22. Tại sao ghi cùng exception ở mọi tầng thường gây vấn đề?
23. Nếu chỉ có một nguyên nhân I/O đơn giản đã phù hợp hợp đồng, có nhất thiết phải bọc không?
24. Khi lớp ngoại lệ tùy biến cần cause, constructor nên gọi phương thức nào của lớp cha?
25. Khi nhìn thấy `getSuppressed().length > 0`, có thể suy ra `getCause() != null` không?

### Đáp án gợi ý

1. Giữ nguyên nhân kỹ thuật khi bổ sung ngữ cảnh ở các tầng cao.
2. Một tham chiếu `Throwable` hoặc `null`.
3. Có; constructor nhận `(String, Throwable)` thể hiện điều đó.
4. Cause là **cùng đối tượng** `original`.
5. Chỉ một tầng.
6. Ném lại khi giữ nguyên hợp đồng/loại lỗi phù hợp; bọc khi cần đổi abstraction và thêm ngữ cảnh.
7. Khi tầng hiện tại có kết quả thay thế hợp lệ theo hợp đồng.
8. Liên kết cause, kiểu lỗi gốc và stack trace có cấu trúc.
9. Handler so khớp loại ngoại lệ đang truyền, không tự duyệt cause.
10. `extends` là quan hệ giữa lớp; cause là liên kết giữa những đối tượng lỗi cụ thể.
11. Không chỉ vì cause; nghĩa vụ checked phụ thuộc lỗi đang được ném và chữ ký API.
12. Những lỗi phụ được giữ khi ưu tiên một lỗi chính, ví dụ lỗi đóng tài nguyên.
13. Có.
14. Khi API không có constructor nhận cause nhưng đối tượng chưa khởi tạo cause và lớp cho phép.
15. Constructor đã khởi tạo cause bằng null.
16. Không.
17. Chúng mô tả những vị trí/ngữ cảnh khác nhau trên đường xảy ra và chuyển đổi lỗi.
18. Để tránh duyệt vô hạn khi có liên kết nguyên nhân vòng.
19. Không; lỗi tầng nghiệp vụ có thể phù hợp hơn.
20. Có thể lộ thông tin kỹ thuật và dữ liệu nhạy cảm.
21. Ví dụ `ExecutionException` giữ lỗi tác vụ làm cause.
22. Gây log trùng, nhiễu tín hiệu và khó đếm sự cố.
23. Không, có thể truyền tiếp hoặc ném lại.
24. `super(message, cause)` khi có overload phù hợp.
25. Không; cause và suppressed là hai quan hệ độc lập.

## 20. Ghi nhớ

**Exception chaining bảo toàn quan hệ “lỗi này xảy ra vì lỗi nào” qua `Throwable.getCause()`.** Khi chuyển một lỗi từ tầng kỹ thuật sang tầng nghiệp vụ, hãy bổ sung message có ý nghĩa và giữ chính ngoại lệ gốc làm cause bằng constructor `(String, Throwable)`. Chỉ dùng `initCause` cho trường hợp đặc biệt chưa được khởi tạo nguyên nhân. Phân biệt **cause** với **suppressed exceptions**, **throw lại cùng đối tượng** với **tạo wrapper mới**, và **kiểu lỗi tầng ngoài** với **loại nguyên nhân nằm bên trong**. Một chuỗi ngoại lệ tốt giúp API rõ ràng và quá trình chẩn đoán chính xác mà không làm lộ thông tin nội bộ không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-002">Giữ nguyên nguyên nhân khi bọc ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ nguyên nguyên nhân khi bọc ngoại lệ trong Java

## 1. Mục tiêu học tập

Khi một phương thức bắt được ngoại lệ ở tầng thấp rồi cần thông báo thất bại bằng một loại ngoại lệ phù hợp hơn với tầng trên, nó có thể **bọc ngoại lệ** (*exception wrapping*). Cách bọc đúng phải giữ được **nguyên nhân gốc** để lập trình viên không mất thông tin quan trọng về vị trí và bản chất sự cố.

Ví dụ: thư viện đọc tệp ném `IOException`, lớp nhập dữ liệu muốn báo `ImportException`. Nếu chỉ tạo `new ImportException("Import failed")`, stack trace của lỗi đọc có thể mất khỏi thông tin ngoại lệ được truyền lên. Nếu tạo `new ImportException("Import failed", original)`, lỗi mới thêm ngữ cảnh nghiệp vụ nhưng vẫn truy vết về `IOException` gốc qua `getCause()`.

Bài học giúp bạn:

- Phân biệt **ngoại lệ bao ngoài** (*wrapper*) với **nguyên nhân** (*cause*) và **ngoại lệ suppressed**.
- Sử dụng constructor `Throwable(String, Throwable)` hoặc constructor tương ứng của `Exception`/`RuntimeException` và các lớp con.
- Thiết kế custom exception hỗ trợ nguyên nhân, giữ nguyên đối tượng `Throwable` ở chuỗi lỗi.
- Dùng `initCause(Throwable)` trong các trường hợp cần tương thích API cũ, đồng thời hiểu giới hạn của nó.
- Tìm nguyên nhân gốc qua nhiều tầng mà không đánh mất thông tin các tầng trung gian.
- Quyết định khi nào nên **ném lại nguyên ngoại lệ**, khi nào nên **bọc**, và khi nào nên **xử lý tại chỗ**.
- Tránh anti-pattern chỉ sao chép `getMessage()`, log lặp, che mất cause hoặc chuyển sai loại checked/unchecked.
- Đọc `printStackTrace()` và phân biệt `Caused by:` với `Suppressed:` trong chẩn đoán.

Các chương trình chạy độc lập dùng Java 17 trở lên, có lệnh `javac`/`java` và `stdout` dự kiến.

## 2. Vì sao mất `cause` là một lỗi thiết kế?

Giả sử lớp dưới có phương thức:

```java
static int parse(String text) {
    return Integer.parseInt(text);
}
```

Nếu `text` không chứa số nguyên hợp lệ, `Integer.parseInt` ném `NumberFormatException`. Một tầng ứng dụng có thể bắt lỗi và chuyển thành ngoại lệ có ngữ nghĩa nghiệp vụ:

```java
try {
    return parse(text);
} catch (NumberFormatException original) {
    throw new IllegalStateException("Invalid configuration");
}
```

Hai đoạn trên là **mảnh minh họa** trong một lớp/phương thức có biến `text` thích hợp. Cách viết cuối có vấn đề: ngoại lệ bao ngoài vẫn có stack trace của nơi **bọc lỗi**, nhưng mối liên hệ chính thức đến đối tượng `NumberFormatException` ban đầu không được lưu. Người đọc chỉ thấy “Invalid configuration”, không còn trực tiếp xem được lỗi parse, lời gọi gây lỗi và những thông tin gắn với đối tượng cũ qua chuỗi `cause`.

Cách đúng:

```java
try {
    return parse(text);
} catch (NumberFormatException original) {
    throw new IllegalStateException(
            "Invalid configuration: expected an integer", original);
}
```

Ở đây:

- `original` là chính đối tượng `NumberFormatException` đã được bắt.
- `IllegalStateException` mới đại diện cho thất bại ở tầng cấu hình.
- Constructor có đối số thứ hai lưu `original` làm **cause**.
- `getCause()` trên wrapper trả tham chiếu tới `original`, không phải một ngoại lệ mới tái tạo từ message.
- Khi xem stack trace đầy đủ, Java có thể biểu diễn cả hai tầng và nơi lỗi gốc phát sinh.

**Nguyên tắc:** bọc lỗi phải thêm thông tin hữu ích **mà vẫn giữ lỗi gốc**, không được chỉ thay tên lỗi rồi đánh mất đường truy vết.

## 3. Mô hình `Throwable`: message, cause, stack trace và suppressed

Một đối tượng `Throwable` có nhiều thông tin khác nhau:

| Thành phần | API chính | Ý nghĩa |
| --- | --- | --- |
| Loại exception | `getClass()` | Tầng/nguyên nhân thất bại được biểu diễn bằng kiểu Java |
| Thông điệp | `getMessage()` | Mô tả ngắn, có thể là `null` |
| Chuỗi nguyên nhân | `getCause()` | Ngoại lệ khiến lỗi hiện tại xuất hiện hoặc được bọc lại |
| Vị trí thực thi | `getStackTrace()`, `printStackTrace()` | Các frame liên quan khi stack trace được ghi nhận |
| Lỗi phụ | `getSuppressed()` | Những lỗi bổ sung được gắn vào cùng Throwable, thường khi cleanup |

Sơ đồ quan hệ:

```text
ImportException: "Cannot import orders"       <-- wrapper
    |
    +-- getCause()
            |
            v
      IOException: "Cannot read orders.csv"    <-- cause
            |
            +-- getCause() == null

Mỗi Throwable trong sơ đồ có thể có stack trace
và danh sách getSuppressed() riêng.
```

Đừng coi `getCause()` và `getSuppressed()` là hai tên của cùng một cơ chế. `cause` giải thích quan hệ **gây ra/bị bọc bởi**; suppressed giữ những lỗi **bổ sung** (ví dụ đóng resource thất bại sau khi đọc tệp lỗi).

Một wrapper thường có stack trace được tạo tại **vị trí bọc**; cause giữ stack trace ở **vị trí gốc**. Giữ cả hai giúp biết không chỉ *lỗi nào xảy ra* mà còn *tầng nào đã chuyển đổi lỗi*.

## 4. Constructor nhận nguyên nhân: cách thông dụng nhất

Nhiều lớp exception Java có constructor:

```java
new RuntimeException("context", cause);
new IllegalStateException("context", cause);
new java.io.IOException("context", cause);
```

Đây là các biểu thức minh họa, cần biến `cause` kiểu `Throwable` được khai báo. Các lớp custom nên cung cấp constructor tương tự khi có nhu cầu bọc.

Ví dụ định nghĩa:

```java
class ImportException extends Exception {
    ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là lớp Java hợp lệ. Gọi `super(message, cause)` để lớp cha `Exception` lưu nguyên nhân trong cơ chế của `Throwable`. Không cần tự tạo một field `Throwable rootCause` nếu chỉ muốn dùng chức năng tiêu chuẩn.

### Vì sao tham số nguyên nhân dùng `Throwable`?

`Throwable` là kiểu cha của `Exception` và `Error`. Constructor nhận `Throwable` cho phép bảo toàn chính xác đối tượng lỗi nguồn mà không buộc nó phải cùng phân cấp với wrapper.

Tuy vậy, khả năng **truyền được một `Error`** không có nghĩa ứng dụng nên bắt mọi `Error` rồi chuyển thành ngoại lệ có thể phục hồi. Nhiều `Error` biểu thị sự cố nghiêm trọng của JVM hoặc môi trường chạy; hãy giữ phạm vi bắt và bọc phù hợp với hợp đồng.

### Dùng constructor có cause, không dựng lại ngoại lệ từ chuỗi

Nếu đã có `IOException original`, tránh:

```java
throw new IOException(original.getMessage());
```

Mảnh mã này có thể tạo một `IOException` mới, nhưng không lưu `original` làm cause. Thông điệp có thể giống nhau mà quan hệ nguyên nhân vẫn bị mất.

Nên viết:

```java
throw new IOException("Unable to read configuration", original);
```

Trong hai mảnh minh họa, `original` giả định đã được bắt hoặc khai báo. Dạng thứ hai giữ nguyên đối tượng lỗi gốc. Việc thêm ngữ cảnh vẫn nên ngắn gọn và mô tả được **thao tác nào đã thất bại**.

## 5. Ví dụ hoàn chỉnh: bọc `NumberFormatException` thành lỗi cấu hình

Tạo file **`PreservedCauseDemo.java`**:

```java
public class PreservedCauseDemo {
    static class ConfigurationException extends Exception {
        ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static int readPort(String text) throws ConfigurationException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException original) {
            throw new ConfigurationException(
                    "Port must be an integer", original);
        }
    }

    public static void main(String[] args) {
        try {
            readPort("abc");
        } catch (ConfigurationException failure) {
            System.out.println("outer="
                    + failure.getClass().getSimpleName());
            System.out.println("message=" + failure.getMessage());
            System.out.println("cause="
                    + failure.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy Java 17+:

```bash
javac PreservedCauseDemo.java
java PreservedCauseDemo
```

`stdout` chính xác:

```text
outer=ConfigurationException
message=Port must be an integer
cause=NumberFormatException
```

Giải thích:

1. `Integer.parseInt("abc")` ném `NumberFormatException`.
2. `readPort` bắt đúng ngoại lệ được ném trong phần thân.
3. `new ConfigurationException(..., original)` tạo đối tượng lỗi mới với ngữ cảnh “Port must be an integer”.
4. `main` nhận `ConfigurationException`, có thể xử lý lỗi cấu hình theo hợp đồng ứng dụng.
5. `failure.getCause()` vẫn là `NumberFormatException` gốc; thông điệp và stack trace của lỗi parse không mất khỏi chuỗi nguyên nhân.

Không cần tự gọi `fillInStackTrace()` hay tạo một `NumberFormatException` mới để “phục dựng” cause. Việc giữ **tham chiếu đến lỗi gốc** mới bảo toàn đầy đủ những gì đối tượng ấy đã ghi nhận.

## 6. Chuỗi nguyên nhân qua nhiều tầng

Trong hệ thống phân lớp, lỗi có thể được bọc nhiều lần:

```text
IOException                 lỗi ở API đọc dữ liệu
     |
     | bọc thành
     v
StorageException             lỗi tầng lưu trữ
     |
     | bọc thành
     v
ImportException              lỗi tầng nghiệp vụ
```

Khi đọc theo `getCause()` từ ngoại lệ ngoài cùng:

```text
ImportException
  -> StorageException
      -> IOException
          -> null
```

Đây là chuỗi **cause**, không phải danh sách suppressed. Mỗi tầng nên cung cấp ngữ cảnh có ích chứ không chỉ lặp lại “failed” một cách vô nghĩa.

### Ví dụ chạy được: nguyên nhân qua ba tầng

Tạo file **`CauseChainDemo.java`**:

```java
import java.io.IOException;

public class CauseChainDemo {
    static class StorageException extends Exception {
        StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static class ImportException extends Exception {
        ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String readSource() throws IOException {
        throw new IOException("source unavailable");
    }

    static String load() throws StorageException {
        try {
            return readSource();
        } catch (IOException original) {
            throw new StorageException("cannot load source", original);
        }
    }

    static void importData() throws ImportException {
        try {
            load();
        } catch (StorageException original) {
            throw new ImportException("cannot import records", original);
        }
    }

    public static void main(String[] args) {
        try {
            importData();
        } catch (ImportException failure) {
            Throwable current = failure;
            while (current != null) {
                System.out.println(
                        current.getClass().getSimpleName()
                        + ": " + current.getMessage());
                current = current.getCause();
            }
        }
    }
}
```

```bash
javac CauseChainDemo.java
java CauseChainDemo
```

`stdout`:

```text
ImportException: cannot import records
StorageException: cannot load source
IOException: source unavailable
```

Chuỗi được xây như sau:

- `readSource()` tạo lỗi gốc `IOException`.
- `load()` nhận lỗi đó và bọc trong `StorageException`.
- `importData()` bọc `StorageException` trong `ImportException`.
- `main` lần theo từng liên kết cause, không cần biết chi tiết lỗi I/O ở thời điểm viết mã nghiệp vụ.

Với một throwable đến từ **nguồn bất kỳ** hoặc thư viện tùy biến, code duyệt cause theo ý tưởng nên có cơ chế nhận biết vòng tham chiếu để tránh lặp vô hạn nếu một chuỗi lỗi bị tạo bất thường. Đối với chuỗi tuyến tính do ba lớp của ví dụ tạo, vòng lặp `while` kết thúc khi `getCause() == null`.

### Không tự bọc mọi tầng nếu không thêm thông tin

Nếu `load()` và `importData()` chỉ cùng truyền lỗi I/O mà không bổ sung ý nghĩa gì, việc bọc ở cả hai tầng có thể làm log phức tạp, tạo stack trace dài và che hợp đồng API. Bọc khi có **ranh giới trừu tượng** hoặc nhu cầu bổ sung ngữ cảnh có giá trị.

## 7. `initCause(Throwable)`: khi không có constructor phù hợp

Một số lớp exception cũ không có constructor nhận cause. Khi đó, `Throwable.initCause(cause)` có thể được dùng để liên kết nguyên nhân **sau khi tạo đối tượng**, miễn là nguyên nhân chưa được thiết lập.

Ví dụ:

```java
LegacyException wrapper = new LegacyException("cannot process");
wrapper.initCause(original);
throw wrapper;
```

Đây là mảnh minh họa, giả định `LegacyException` đã tồn tại và `original` là `Throwable`. Tốt nhất vẫn dùng constructor nhận cause nếu API cung cấp.

### Giới hạn của `initCause`

- Chỉ gọi được **một lần** khi trạng thái cause vẫn chưa được khởi tạo.
- Nếu cause đã được khởi tạo (kể cả đã được constructor ghi nhận là `null`), gọi tiếp `initCause` sẽ ném `IllegalStateException`.
- Không được dùng **chính throwable nhận lời gọi** làm cause của nó; self-causation gây `IllegalArgumentException`.
- Truyền `null` có thể là giá trị cause khi trạng thái chưa được khởi tạo; đừng hiểu nó là cách “reset” quan hệ cause.
- `initCause` trả lại chính đối tượng `Throwable` nhận lời gọi, thuận tiện cho một số kiểu viết nhưng không buộc bạn phải dùng chaining.
- Với mã mới do bạn quản lý, constructor `(String, Throwable)` thường rõ ràng và an toàn hơn việc tạo đối tượng rồi nhớ liên kết sau.

### Ví dụ chạy được: thiết lập cause một lần

Tạo file **`InitCauseDemo.java`**:

```java
import java.io.IOException;

public class InitCauseDemo {
    static class LegacyException extends Exception {
        LegacyException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        IOException root = new IOException("disk failed");
        LegacyException wrapper =
                new LegacyException("load operation failed");

        wrapper.initCause(root);
        System.out.println("cause=" + wrapper.getCause().getMessage());

        try {
            wrapper.initCause(new IOException("second cause"));
        } catch (IllegalStateException failure) {
            System.out.println("second init rejected");
        }

        try {
            LegacyException self = new LegacyException("self");
            self.initCause(self);
        } catch (IllegalArgumentException failure) {
            System.out.println("self cause rejected");
        }
    }
}
```

```bash
javac InitCauseDemo.java
java InitCauseDemo
```

`stdout`:

```text
cause=disk failed
second init rejected
self cause rejected
```

`super(message)` trong custom exception giữ trạng thái cause chưa gán theo cơ chế `Throwable(String)`, nên lần `initCause(root)` đầu thành công. Lần tiếp theo bị từ chối. Với self-causation, Java cũng không chấp nhận xây cause trỏ trực tiếp về chính đối tượng.

### Không dùng `initCause` sau constructor đã nhận cause

Mã minh họa:

```java
IOException wrapped = new IOException("cannot read", original);
wrapped.initCause(anotherFailure); // Sai: cause đã được khởi tạo.
```

Đoạn này sẽ ném `IllegalStateException` tại runtime (giả định `original` và `anotherFailure` là các đối tượng `Throwable` hợp lệ), vì constructor đã thiết lập cause. Không dùng `initCause` như cơ chế “đổi nguyên nhân” sau này.

## 8. `cause` không phải `suppressed exception`

Hai loại liên kết có thể cùng tồn tại:

```text
ImportException: cannot import
    |
    +-- cause: IOException("read failed")
    |
    +-- suppressed: IOException("close failed")
```

Một số tình huống điển hình:

- **Bọc lỗi từ tầng dưới:** `new ImportException("...", original)` → dùng `cause`.
- **Đọc file lỗi, đóng file cũng lỗi trong TWR:** lỗi đọc là primary, lỗi đóng thường là `suppressed`.
- **Đóng file lỗi khi phần thân thành công:** lỗi đóng có thể là primary, không nhất thiết là suppressed.
- **Một lỗi nghiệp vụ vừa bị bọc vừa có cleanup failure:** wrapper có cause; suppressed có thể nằm trên chính cause hoặc wrapper tùy tầng/cơ chế quản lý resource thực tế. Không được suy luận rằng suppressed luôn nằm ở tầng ngoài cùng.

### Ví dụ chạy được: có cả nguyên nhân và lỗi phụ

Tạo file **`CauseAndSuppressedDemo.java`**:

```java
import java.io.IOException;

public class CauseAndSuppressedDemo {
    public static void main(String[] args) {
        IOException readFailure =
                new IOException("read failed");
        readFailure.addSuppressed(
                new IOException("reader close failed"));

        IllegalStateException wrapper =
                new IllegalStateException("cannot load report", readFailure);

        System.out.println("outer=" + wrapper.getMessage());
        System.out.println("cause=" + wrapper.getCause().getMessage());
        System.out.println("outer suppressed="
                + wrapper.getSuppressed().length);
        System.out.println("cause suppressed="
                + wrapper.getCause().getSuppressed().length);
        System.out.println("cleanup="
                + wrapper.getCause().getSuppressed()[0].getMessage());
    }
}
```

```bash
javac CauseAndSuppressedDemo.java
java CauseAndSuppressedDemo
```

`stdout`:

```text
outer=cannot load report
cause=read failed
outer suppressed=0
cause suppressed=1
cleanup=reader close failed
```

Giải thích: `readFailure` có một suppressed exception; nó được đưa nguyên vẹn vào cause của wrapper. Constructor nhận cause **không chuyển** danh sách suppressed từ cause sang wrapper. Vì thế `wrapper.getSuppressed().length == 0`, còn suppressed nằm ở `wrapper.getCause()`.

Trong log, `printStackTrace()` của wrapper thường trình bày chuỗi cause và những suppressed của từng throwable theo cấu trúc; không nên chỉ kiểm tra `wrapper.getSuppressed()` rồi kết luận không có lỗi cleanup ở các nguyên nhân sâu hơn.

## 9. `try-with-resources` kết hợp với bọc ngoại lệ

`try-with-resources` tự đóng tài nguyên `AutoCloseable` theo thứ tự ngược. Khi phần thân thất bại và `close()` cũng thất bại, Java giữ lỗi thân làm primary và gắn lỗi đóng làm suppressed. Nếu tầng ngoài bọc primary, **nên bảo toàn chính đối tượng primary cùng suppressed của nó**.

### Ví dụ chạy được: bảo toàn nguyên nhân lẫn suppressed qua wrapper

Tạo file **`WrappedResourceFailureDemo.java`**:

```java
import java.io.IOException;

public class WrappedResourceFailureDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static class OperationException extends Exception {
        OperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void execute() throws OperationException {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IOException("read failed");
        } catch (IOException original) {
            throw new OperationException("report generation failed", original);
        }
    }

    public static void main(String[] args) {
        try {
            execute();
        } catch (OperationException failure) {
            Throwable cause = failure.getCause();
            System.out.println("outer=" + failure.getMessage());
            System.out.println("cause=" + cause.getMessage());
            System.out.println("suppressed="
                    + cause.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac WrappedResourceFailureDemo.java
java WrappedResourceFailureDemo
```

`stdout`:

```text
outer=report generation failed
cause=read failed
suppressed=close failed
```

Từng bước:

1. `execute()` bắt đầu vùng TWR với `BrokenResource`.
2. Phần thân ném `IOException("read failed")`.
3. `close()` ném `IOException("close failed")`; JVM/TWR đính kèm lỗi này vào `getSuppressed()` của lỗi đọc.
4. Handler `catch (IOException original)` nhận đối tượng primary đã có suppressed.
5. `new OperationException(..., original)` bọc **đúng đối tượng** và giữ liên kết cause.
6. Caller có thể xem cause, rồi truy cập suppressed gắn với cause đó.

Nếu viết `new OperationException(original.getMessage(), null)` hoặc chỉ copy message, quan hệ cause và lỗi close sẽ bị bỏ mất khỏi ngoại lệ được truyền lên. Đây là ví dụ thực tế tại sao giữ nguyên đối tượng lỗi quan trọng hơn việc giữ một chuỗi văn bản.

## 10. Khi nào nên `throw original;` thay vì bọc?

**Không phải mọi ngoại lệ đều cần wrapper.** Nếu tầng gọi hiện tại không thay đổi ý nghĩa hoặc không cần chuyển hợp đồng, việc ném lại nguyên ngoại lệ thường đơn giản và trung thực hơn.

Ví dụ:

```java
static String readLine(java.io.BufferedReader reader)
        throws java.io.IOException {
    try {
        return reader.readLine();
    } catch (java.io.IOException original) {
        // Có thể ghi nhận một ngữ cảnh thực sự hữu ích ở đây.
        throw original;
    }
}
```

Đây là **mảnh phương thức**. `throw original;` truyền **cùng đối tượng IOException**, giữ message, cause, suppressed và stack trace mà đối tượng đã có. Tuy nhiên, đoạn `catch` chỉ để ném lại mà không thêm ý nghĩa thường là dư thừa; có thể để exception tự lan truyền.

Sử dụng wrapper khi:

- Đang đi qua ranh giới trừu tượng: I/O → lưu trữ → nghiệp vụ.
- Tầng trên không nên phụ thuộc một loại lỗi kỹ thuật chi tiết, nhưng vẫn cần truy vết.
- API cần bổ sung thông tin ngữ cảnh như tên thao tác, nguồn dữ liệu hoặc mã giao dịch (không lộ dữ liệu bí mật).
- Chính sách error handling của tầng trên có hợp đồng khác và có lý do rõ ràng.

Giữ nguyên lỗi hoặc bắt xử lý trực tiếp khi:

- API của phương thức đã khai báo loại checked exception phù hợp.
- Không có thêm ngữ nghĩa hữu ích để đưa vào wrapper.
- Bên gọi cần xử lý chính kiểu gốc.
- Đang bắt lỗi chỉ để log rồi ném lên mà không thêm gì: nên để logging ở một ranh giới thích hợp để tránh log lặp.

### Không bọc rồi nuốt ngoại lệ

Anti-pattern:

```java
try {
    save();
} catch (Exception original) {
    new IllegalStateException("Save failed", original);
    // Không throw: ngoại lệ mới bị bỏ đi.
}
```

Đây là **mảnh mã thiết kế sai có chủ ý**, cần định nghĩa `save()`. Chỉ tạo đối tượng wrapper mà không `throw` hay đưa vào cơ chế báo lỗi không làm caller biết thao tác thất bại. Chú ý dùng:

```java
throw new IllegalStateException("Save failed", original);
```

nếu chính sách thực sự là truyền lỗi lên bằng unchecked exception.

## 11. Checked và unchecked exception khi bọc

Bọc có thể đổi **loại ngoại lệ mà bên gọi phải xử lý**; đó là quyết định thiết kế API, không chỉ quyết định log.

Ví dụ một phương thức gọi API checked:

```java
static void load() throws java.io.IOException {
    throw new java.io.IOException("missing file");
}
```

Nếu method gọi bọc thành `IllegalStateException`:

```java
static void initialize() {
    try {
        load();
    } catch (java.io.IOException original) {
        throw new IllegalStateException(
                "Startup configuration could not be loaded", original);
    }
}
```

Đây là **các mảnh phương thức minh họa** cùng lớp; `initialize()` không cần khai báo `throws IOException`, vì nó xử lý IOException bằng việc ném unchecked wrapper. Nhưng điều này **không đồng nghĩa lỗi đã được phục hồi**. Bên gọi có thể vẫn thất bại ở runtime nếu không có handler thích hợp.

Nếu sử dụng checked wrapper:

```java
class StartupException extends Exception {
    StartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

thì method ném `StartupException` phải khai báo `throws StartupException` hoặc caller xử lý tương ứng. Cần phân biệt:

| Lựa chọn | Ảnh hưởng tới caller | Lưu nguyên nhân |
| --- | --- | --- |
| Ném lại checked exception gốc | Caller tiếp tục bắt/khai báo kiểu checked đó | Giữ đối tượng gốc |
| Bọc thành checked exception mới | Caller bắt/khai báo kiểu checked mới | Có nếu truyền `original` vào cause |
| Bọc thành unchecked exception | Không bắt buộc catch/throws ở compile time | Có nếu truyền `original` vào cause |
| Chỉ tạo wrapper rồi bỏ qua | Caller có thể tưởng thao tác thành công | Không có báo lỗi hợp lệ |
| Chỉ copy `original.getMessage()` | Có thể đổi hợp đồng, nhưng mất liên kết cause | Không |

Chọn checked/unchecked theo khả năng xử lý của caller, quy ước module và mức độ dự kiến của sự cố. Không dùng unchecked chỉ để “thoát khỏi trình biên dịch” mà không thiết kế phương án xử lý thất bại.

## 12. Lỗi cần tránh khi viết custom exception

### 12.1. Không quên truyền cause lên superclass

Sai lầm:

```java
class BadImportException extends Exception {
    BadImportException(String message, Throwable cause) {
        super(message); // Cause nhận vào nhưng bị bỏ qua.
    }
}
```

Đây là **ví dụ class hợp lệ về cú pháp nhưng sai chức năng** nếu API hứa giữ cause. Tham số `cause` bị bỏ qua; `getCause()` trên wrapper mặc định không trỏ về lỗi gốc.

Sửa:

```java
class GoodImportException extends Exception {
    GoodImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 12.2. Không lưu riêng cause mà bỏ API chuẩn

Ví dụ không nên chỉ có:

```java
class NonstandardException extends Exception {
    private final Throwable original;

    NonstandardException(Throwable original) {
        this.original = original;
    }
}
```

Đây là class minh họa có thể biên dịch, nhưng `getCause()` của `Throwable` không phản ánh field `original` vừa tạo. Những logger và công cụ chẩn đoán chuẩn sẽ không tự tìm được nguyên nhân qua API chuẩn. Nếu cần thông tin đặc thù, hãy vẫn gọi `super(message, cause)`, rồi thêm field nghiệp vụ **không thay thế** cause.

### 12.3. Không biến cause thành chuỗi

`cause.toString()` có thể cho thông điệp hữu ích, nhưng không thay thế liên kết Throwable. Nếu lưu chỉ chuỗi lỗi, bạn có thể đánh mất kiểu ngoại lệ, stack trace, suppressed exceptions và những trường bổ sung trên lớp con.

### 12.4. Không cập nhật cause hai lần

Nếu constructor đã dùng `super(message, cause)`, `initCause(newCause)` sau đó thường gây `IllegalStateException`. Một throwable không có hợp đồng thay đổi cause liên tục sau khi đã thiết lập.

### 12.5. Không dùng `catch (Throwable)` chỉ để bọc mọi thứ

`Throwable` bao gồm cả `Error`. Bắt và chuyển mọi `Error` thành một `RuntimeException` có vẻ “đồng nhất” nhưng có thể che đi lỗi nghiêm trọng liên quan JVM/tài nguyên mà chương trình không thể xử lý an toàn. Chỉ bắt những kiểu phù hợp với hợp đồng và chiến lược xử lý.

### 12.6. Không đánh mất thời điểm lỗi ban đầu vì tạo lại cause

Đoạn:

```java
catch (java.io.IOException original) {
    throw new IllegalStateException(
            "Cannot read", new java.io.IOException(original.getMessage()));
}
```

Đây là mảnh **sai về bảo toàn chẩn đoán**: “cause” mới là một đối tượng `IOException` khác, stack trace được ghi ở chỗ **tạo mới**, không phải nơi lỗi gốc phát sinh. Hãy truyền trực tiếp `original`.

## 13. In stack trace và đọc chuỗi nguyên nhân

Gọi:

```java
failure.printStackTrace();
```

thường in ngoại lệ vào `System.err`, bao gồm các liên kết `Caused by:` và `Suppressed:` có liên quan. Không nên kỳ vọng stack trace ở một dòng hoặc các số dòng source cố định: thông tin class, file, module, vị trí dòng và cách rút gọn frame có thể thay đổi giữa các bản build/JVM.

Ví dụ khái niệm:

```text
ImportException: cannot import
    at ImportService.importData(...)
Caused by: StorageException: cannot read
    at StorageService.load(...)
Caused by: IOException: access denied
    at FileService.open(...)
```

Đây là **sơ đồ rút gọn**, không phải `stdout` chính xác của chương trình; dấu `...` chỉ các frame mô tả.

Một số logger nhận cả đối tượng Throwable:

```java
logger.error("Import failed", failure);
```

Đây là **mảnh minh họa framework logging**, không phải cú pháp Java SE độc lập vì `logger` cần đến API của thư viện. Mục tiêu: truyền **đối tượng Throwable**, không chỉ `failure.getMessage()`. Kiểu API và cách format phụ thuộc thư viện logger cụ thể.

Đừng log cùng ngoại lệ ở mọi tầng rồi ném tiếp nếu không cần. Một thực hành phổ biến là tầng thấp chỉ thêm ngữ cảnh khi bọc; ranh giới xử lý cuối quyết định log và thông điệp cho người dùng.

### Thông tin bổ sung trong message

Một message wrapper tốt nên nêu thao tác và ngữ cảnh an toàn:

- `"Cannot parse import batch 42"`.
- `"Cannot load application configuration"`.
- `"Could not persist invoice"`.

Tránh message quá chung chung như `"error"`, nhưng cũng không đưa mật khẩu, token, nội dung dữ liệu nhạy cảm, chuỗi kết nối đầy đủ hoặc dữ liệu người dùng không cần thiết vào lỗi/log. Cause có thể tự chứa thông tin nhạy cảm nên chính sách ghi log và hiển thị stack trace vẫn quan trọng.

## 14. Ví dụ hoàn chỉnh: bỏ qua cause so với giữ cause

Tạo file **`CauseLossComparisonDemo.java`**:

```java
import java.io.IOException;

public class CauseLossComparisonDemo {
    static IllegalStateException withoutCause(IOException original) {
        return new IllegalStateException("service failed");
    }

    static IllegalStateException withCause(IOException original) {
        return new IllegalStateException("service failed", original);
    }

    public static void main(String[] args) {
        IOException root = new IOException("database unavailable");

        IllegalStateException lost = withoutCause(root);
        IllegalStateException kept = withCause(root);

        System.out.println("lost cause=" + (lost.getCause() == null));
        System.out.println("kept identity="
                + (kept.getCause() == root));
        System.out.println("root message="
                + kept.getCause().getMessage());
    }
}
```

```bash
javac CauseLossComparisonDemo.java
java CauseLossComparisonDemo
```

`stdout`:

```text
lost cause=true
kept identity=true
root message=database unavailable
```

Mấu chốt là `kept.getCause() == root` trả về `true`: constructor nhận cause giữ **chính đối tượng** `IOException` ban đầu, không tạo bản sao. Ngược lại, `withoutCause` không lưu liên kết; một công cụ chỉ có đối tượng `lost` không thể lần theo cause để tìm `root`.

## 15. Chẩn đoán cause một cách an toàn

Khi cần tìm nguyên nhân gốc ở một hệ thống có nhiều wrapper, bạn có thể đi theo `getCause()`. Tuy vậy, không nên viết một vòng lặp vô hạn nếu đối tượng Throwable từ thư viện tùy biến tạo cấu trúc bất thường.

Ví dụ dùng `IdentityHashMap` để dừng nếu gặp lại cùng một **đối tượng throwable**:

Tạo file **`SafeCauseTraversalDemo.java`**:

```java
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class SafeCauseTraversalDemo {
    static void show(Throwable failure) {
        Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());

        Throwable current = failure;
        while (current != null && visited.add(current)) {
            System.out.println(
                    current.getClass().getSimpleName()
                    + ": " + current.getMessage());
            current = current.getCause();
        }
    }

    public static void main(String[] args) {
        IOException root = new IOException("read failed");
        IllegalStateException middle =
                new IllegalStateException("load failed", root);
        RuntimeException outer =
                new RuntimeException("request failed", middle);

        show(outer);
    }
}
```

```bash
javac SafeCauseTraversalDemo.java
java SafeCauseTraversalDemo
```

`stdout`:

```text
RuntimeException: request failed
IllegalStateException: load failed
IOException: read failed
```

`IdentityHashMap` sử dụng đồng nhất đối tượng làm cơ sở khóa, phù hợp khi muốn không truy lại cùng một `Throwable`. Vòng lặp chỉ đi theo `getCause()`; nó **không** tự duyệt `getSuppressed()` tại từng tầng. Nếu xây công cụ phân tích ngoại lệ hoàn chỉnh, cần duyệt cả cause và suppressed theo một cấu trúc đồ thị và theo dõi đối tượng đã gặp để tránh lặp.

Trong ứng dụng bình thường, các logger và `printStackTrace()` thường đã trình bày đủ thông tin; đừng tự viết hệ thống duyệt exception tree nếu không có nhu cầu thực tế.

## 16. Bảng quyết định: ném lại, bọc hay xử lý?

| Hoàn cảnh | Cách xử lý thường phù hợp | Lý do |
| --- | --- | --- |
| Cùng loại checked exception vẫn nằm trong hợp đồng phương thức | Để lan truyền hoặc `throw original;` | Giữ đúng kiểu gốc, ít lớp bọc |
| Chuyển tầng I/O sang API nghiệp vụ có hợp đồng lỗi riêng | `throw new DomainException(context, original);` | Thêm ngữ cảnh và giữ nguyên nhân |
| Tầng hiện tại có thể retry/phục hồi an toàn | Xử lý cụ thể ở đây | Không cần ném đến tầng trên nếu đã khắc phục |
| Checked exception trở thành lỗi khởi tạo không thể tiếp tục | Có thể bọc unchecked với cause | Bên gọi cần chính sách dừng/ghi log rõ ràng |
| Lỗi resource close xảy ra khi đã có lỗi thân TWR | Giữ primary, để close thành suppressed | Bảo toàn cả hai lỗi |
| Custom exception chưa có constructor nhận cause | `initCause(original)` một lần, nếu hợp lệ | Duy trì khả năng truy vết ở API cũ |
| Custom exception do bạn thiết kế mới | Constructor `(String, Throwable)` | Công khai hợp đồng và an toàn hơn |
| Tầng trung gian chỉ bắt để in message rồi ném tiếp | Cân nhắc bỏ catch thừa | Tránh log lặp và mất ngữ cảnh |
| Gặp `Error` nghiêm trọng | Không bắt/bọc bừa bãi | Có thể không có điều kiện phục hồi an toàn |

Đây là các nguyên tắc thiết kế, không phải quy tắc tuyệt đối. Quyết định cuối cần theo hợp đồng API, khả năng khôi phục, mức độ quan trọng của dữ liệu và chính sách vận hành.

## 17. Những nhầm lẫn phổ biến

**“Bọc lỗi bằng ngoại lệ mới là đã giữ nguyên nhân.”** Sai. Nếu constructor không nhận `cause`, quan hệ cause có thể không được lưu.

**“Chỉ cần nối `original.getMessage()` vào message của wrapper.”** Sai. Message không thay thế đối tượng Throwable, stack trace hoặc suppressed exceptions.

**“`getCause()` trả lỗi mới do `RuntimeException` tự tạo.”** Sai. Nếu constructor nhận đối tượng `original`, `getCause()` trả chính tham chiếu ấy.

**“`initCause` có thể gọi bao nhiêu lần cũng được.”** Sai. Cause thường chỉ được khởi tạo một lần; những lần đặt lại gây `IllegalStateException`.

**“Có thể đặt cause là chính wrapper.”** Sai. Self-causation trực tiếp bị từ chối.

**“`getCause()` và `getSuppressed()` giống nhau.”** Sai. Cause thể hiện quan hệ nguyên nhân; suppressed ghi lỗi bổ sung.

**“Bọc một lỗi có suppressed sẽ tự chuyển suppressed sang wrapper.”** Sai. Thường suppressed vẫn nằm trên đối tượng cause ban đầu, trừ khi code chủ động thêm vào wrapper.

**“Bọc checked exception thành unchecked nghĩa là lỗi đã được xử lý.”** Sai. Nó chỉ đổi hợp đồng kiểm tra compile-time; runtime vẫn có thể thất bại.

**“Nên bắt `Throwable` để bọc luôn `Error` cho thống nhất.”** Không nên coi đây là mặc định; nhiều `Error` báo tình huống không thể phục hồi.

**“Mỗi lớp nên bọc thêm một lớp ngoại lệ dù không thêm thông tin.”** Không. Chuỗi wrapper quá dày làm phức tạp chẩn đoán.

**“Muốn log nguyên nhân thì chỉ cần `getMessage()`.”** Không đủ trong nhiều trường hợp; message bỏ qua stack trace, cause và suppressed.

**“`printStackTrace()` chỉ hiển thị ngoại lệ ngoài cùng.”** Không đúng: nó được thiết kế để thể hiện chuỗi cause và suppressed có liên quan.

**“Một custom exception tự lưu field `original` là công cụ Java sẽ nhận ra cause.”** Không tự động. Hãy gọi `super(message, cause)` hoặc liên kết chuẩn hợp lệ qua `initCause`.

**“Dùng `new IOException(original.getMessage())` là tương đương giữ IOException cũ.”** Sai. Đây là đối tượng mới, ghi stack trace mới và không tự chứa liên kết với object gốc.

**“Đã có cause thì không cần xem suppressed.”** Sai. Lỗi đóng resource có thể nằm trong suppressed của cause sâu bên dưới.

## 18. Câu hỏi tự kiểm tra

1. **Khi bọc `IOException` thành `ImportException`, làm thế nào giữ nguyên nhân?** Dùng constructor `new ImportException("context", original)` và gọi `super(message, cause)` trong lớp custom.
2. **Chỉ viết `new ImportException(original.getMessage())` có giữ object gốc không?** Không.
3. **`getCause()` trả kiểu gì?** `Throwable` hoặc `null`, tùy trạng thái cause.
4. **`getCause()` có trả chính đối tượng đã truyền vào constructor không?** Có; không tự clone.
5. **Cause khác suppressed ở điểm nào?** Cause nói về nguyên nhân/quan hệ bọc, suppressed ghi lỗi phụ, thường ở cleanup.
6. **`IOException` đọc thất bại có suppressed từ `close()`. Sau khi bọc IOException, suppressed nằm ở đâu?** Trên chính đối tượng IOException cause nếu chưa chủ động chuyển/sửa.
7. **Có nên tạo một exception mới chỉ với message của cause rồi truyền nó như cause không?** Không, vì làm mất đối tượng và stack trace gốc.
8. **`initCause` có thể thay nguyên nhân đã được constructor đặt không?** Không; việc khởi tạo lại gây `IllegalStateException`.
9. **Truyền chính object exception làm cause của nó hợp lệ không?** Không, gây `IllegalArgumentException`.
10. **Khi hợp đồng phương thức vẫn cho phép checked IOException và không có ngữ cảnh mới, cần wrapper không?** Thường không; có thể để ngoại lệ tự lan truyền.
11. **Bọc IOException thành IllegalStateException có tự khắc phục lỗi đọc không?** Không.
12. **Điều gì xảy ra nếu handler tạo wrapper nhưng không `throw`?** Caller có thể không được thông báo lỗi; wrapper chỉ là đối tượng bị bỏ qua.
13. **Nên bắt mọi `Error` để bọc thành exception nghiệp vụ?** Không; nhiều `Error` mang ngữ nghĩa sự cố nghiêm trọng.
14. **Có nên log exception ở mọi tầng rồi ném tiếp không?** Thường không; chọn ranh giới log phù hợp để tránh lặp.
15. **Một chain dài gồm wrapper → cause → cause có mất lỗi gốc không?** Không nếu mỗi tầng giữ nguyên đối tượng cause tương ứng.
16. **Nên duyệt `getCause()` vô hạn mà không kiểm tra khi phân tích đối tượng lỗi tùy biến?** Không. Với công cụ tổng quát có thể theo dõi danh tính đối tượng để ngăn vòng.
17. **Có thể vừa có cause vừa có suppressed trên cùng `Throwable` không?** Có; chúng phục vụ hai mục đích khác nhau.
18. **Khi nào nên dùng `initCause`?** Chủ yếu khi tương thích với exception API cũ không có constructor phù hợp và cause chưa được đặt.

## 19. Tài liệu Java 17

- [Java SE 17 — Throwable constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): constructor lưu `message`, `cause`, suppression và stack trace.
- [Java SE 17 — Throwable.getCause](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getCause()): truy cập nguyên nhân được lưu.
- [Java SE 17 — Throwable.initCause](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#initCause(java.lang.Throwable)): gắn nguyên nhân một lần và các giới hạn.
- [Java SE 17 — Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): các lỗi bổ sung độc lập với cause.
- [Java SE 17 — RuntimeException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/RuntimeException.html): constructor với `(String, Throwable)`.
- [Java SE 17 — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): checked exception có thể nhận cause.
- [Java Language Specification 17 — `11, Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): ném, bắt và kiểm tra ngoại lệ.
- [Java Language Specification 17 — `14.20.3, try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): primary/suppressed trong quá trình đóng resource.

**Ghi nhớ:** Khi cần bọc ngoại lệ, hãy truyền **chính đối tượng ngoại lệ đã bắt** vào constructor nhận `cause`, ví dụ `throw new DomainException("meaningful context", original);`. Việc đó cho phép tầng trên xử lý bằng loại lỗi phù hợp mà vẫn lần theo `getCause()` để đến nguyên nhân gốc cùng stack trace và suppressed exceptions. Chỉ dùng `initCause()` khi cần và hợp lệ; không bao giờ coi việc sao chép message là thay thế cho bảo toàn chuỗi nguyên nhân.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-003">Phân biệt ngoại lệ ngoài cùng với nguyên nhân sâu nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngoại lệ ngoài cùng với nguyên nhân sâu nhất trong Java

Khi một thao tác Java thất bại, ngoại lệ mà tầng trên cùng **bắt được hoặc nhìn thấy** không nhất thiết là ngoại lệ **đã bắt đầu chuỗi thất bại ở tầng thấp nhất**. Một tầng I/O có thể ném `IOException`; tầng dịch vụ bắt lỗi I/O rồi ném `IllegalStateException` có `IOException` làm nguyên nhân; tầng khởi động tiếp tục bọc lỗi dịch vụ thành `RuntimeException`. Khi đó có ba đối tượng ngoại lệ khác nhau và ba thông điệp phục vụ những ngữ cảnh khác nhau.

Cách gọi hữu ích:

- **Ngoại lệ ngoài cùng (outermost exception)** là đối tượng `Throwable` đang được quan sát tại điểm xử lý hoặc ghi log, ví dụ tham số `ex` trong `catch` của tầng trên cùng. Nó có thể là đối tượng mới được tạo để bọc lỗi ở tầng bên dưới.
- **Nguyên nhân trực tiếp (immediate cause)** là kết quả của `outer.getCause()` khi khác `null`.
- **Nguyên nhân sâu nhất (deepest cause, đôi khi gọi root cause theo nghĩa kỹ thuật)** là đối tượng ở cuối một chuỗi `getCause()` hữu hạn, nơi `getCause() == null`.
- **Lỗi phụ (suppressed exception)** được truy cập bằng `getSuppressed()`; nó là một nhánh khác, **không tự động thuộc chuỗi `getCause()`**.

**Điểm quan trọng:** “nguyên nhân sâu nhất trong chuỗi `Throwable`” không tự động đồng nghĩa với **nguyên nhân gốc rễ về nghiệp vụ, hạ tầng hoặc vận hành**. Chuỗi cause chỉ là các liên kết ngoại lệ mà mã nguồn đã tạo; muốn tìm lỗi thực sự cần kết hợp dữ liệu đầu vào, log, trạng thái hệ thống và bối cảnh thao tác.

Bài này giải thích cách đọc outermost/cause/deepest, cách bảo toàn thông tin khi bọc ngoại lệ, cách phân biệt cause với suppressed, cách tránh mất nguyên nhân và cách duyệt chuỗi an toàn trên Java 17+.

## 1. Một lỗi có thể đi qua nhiều tầng và đổi tên ngoại lệ

Giả sử ứng dụng có ba tầng:

~~~text
Tầng khởi động ứng dụng: start()
    └── Tầng dịch vụ: service()
        └── Tầng đọc dữ liệu: repository()
~~~

Ở tầng thấp nhất, `repository()` gặp sự cố đọc thiết bị và ném:

~~~java
throw new java.io.IOException("ổ đĩa không phản hồi");
~~~

Tầng `service()` không thể tải cấu hình. Thay vì ném nguyên `IOException` trực tiếp, tầng này có thể bọc:

~~~java
catch (java.io.IOException ex) {
    throw new IllegalStateException("không tải cấu hình", ex);
}
~~~

Tầng `start()` tiếp tục cung cấp ngữ cảnh khởi động:

~~~java
catch (IllegalStateException ex) {
    throw new RuntimeException("khởi động thất bại", ex);
}
~~~

Đối tượng cuối cùng mà người gọi `start()` nhìn thấy là `RuntimeException("khởi động thất bại")`. Nhưng `getCause()` của nó trỏ tới `IllegalStateException("không tải cấu hình")`, và `getCause()` của đối tượng đó trỏ tới `IOException("ổ đĩa không phản hồi")`.

Cấu trúc đối tượng:

~~~text
RuntimeException: khởi động thất bại     ← OUTERMOST tại caller
    │
    └── cause → IllegalStateException: không tải cấu hình
                    │
                    └── cause → IOException: ổ đĩa không phản hồi
                                     │
                                     └── cause → null  ← DEEPEST
~~~

Khi ứng dụng bắt `RuntimeException`, Java **không tự động truyền ngoại lệ sâu nhất vào biến `catch`**. Biến đó tham chiếu tới ngoại lệ đang được ném ra ngoài; muốn xem nguyên nhân sâu hơn, bạn cần gọi `getCause()` theo từng tầng.

## 2. Bốn khái niệm cần phân biệt

| Khái niệm | Cách xác định | Ý nghĩa thực tế |
| --- | --- | --- |
| Outermost | Đối tượng vừa được bắt, nhận từ API hoặc ghi log | Phân loại lỗi ở ranh giới hiện tại; thường mang ngữ cảnh dễ hiểu đối với tầng gọi |
| Immediate cause | `outer.getCause()` | Lỗi gần nhất dẫn tới việc tạo/wrap outermost |
| Deepest cause | Tầng cuối theo chuỗi `getCause()` tới `null` | Ngoại lệ ở đáy chuỗi liên kết nguyên nhân đã được ghi lại |
| Suppressed | `outer.getSuppressed()` hoặc suppressed trên một cause bất kỳ | Các lỗi phụ, thường phát sinh khi dọn dẹp tài nguyên trong lúc lỗi chính đang tồn tại |

Ví dụ:

~~~java
Throwable deepest = new java.io.IOException("không đọc được");
Throwable middle = new IllegalStateException("dịch vụ thất bại", deepest);
Throwable outer = new RuntimeException("yêu cầu thất bại", middle);

System.out.println(outer.getMessage());                    // yêu cầu thất bại
System.out.println(outer.getCause().getMessage());         // dịch vụ thất bại
System.out.println(outer.getCause().getCause().getMessage()); // không đọc được
~~~

Đây là **đoạn minh họa nằm trong một phương thức**. Ba đối tượng có **danh tính riêng**. Một đối tượng exception được tạo tại tầng cao không xóa đối tượng tầng thấp nếu constructor có lưu cause đúng cách.

Từ “ngoài cùng” mang tính **tương đối so với điểm quan sát**. Nếu bạn bắt ngay tại `repository()`, `IOException` có thể là đối tượng được bắt ở đó. Nếu dịch vụ đã bọc và truyền lỗi lên, `IllegalStateException` là outermost tại caller của dịch vụ. Nếu tầng khởi động lại bọc, `RuntimeException` mới là outermost tại caller ngoài cùng.

Không nên gắn cho một lớp ngoại lệ một vai trò “outermost” cố định ở mọi bối cảnh.

## 3. Chương trình Java 17+ hoàn chỉnh: duyệt từ ngoài cùng xuống sâu nhất

Lưu tệp `CauseChainDemo.java`:

~~~java
import java.io.IOException;

public class CauseChainDemo {
    static void repository() throws IOException {
        throw new IOException("ổ đĩa không phản hồi");
    }

    static void service() {
        try {
            repository();
        } catch (IOException ex) {
            throw new IllegalStateException("không tải cấu hình", ex);
        }
    }

    static void start() {
        try {
            service();
        } catch (IllegalStateException ex) {
            throw new RuntimeException("khởi động thất bại", ex);
        }
    }

    public static void main(String[] args) {
        try {
            start();
        } catch (RuntimeException outer) {
            System.out.println("Ngoài cùng: " + outer.getMessage());
            Throwable current = outer;
            int level = 0;
            while (current != null) {
                System.out.println("Tầng " + level + ": "
                        + current.getClass().getSimpleName()
                        + " - " + current.getMessage());
                if (current.getCause() == null) {
                    System.out.println("Sâu nhất: " + current.getMessage());
                }
                current = current.getCause();
                level++;
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CauseChainDemo.java
java CauseChainDemo
~~~

Stdout chính xác:

~~~text
Ngoài cùng: khởi động thất bại
Tầng 0: RuntimeException - khởi động thất bại
Tầng 1: IllegalStateException - không tải cấu hình
Tầng 2: IOException - ổ đĩa không phản hồi
Sâu nhất: ổ đĩa không phản hồi
~~~

### Phân tích luồng từng tầng

1. `repository()` tạo và ném một đối tượng `IOException` vì mô phỏng lỗi I/O.
2. `service()` bắt `IOException` và tạo **đối tượng mới** `IllegalStateException("không tải cấu hình", ex)`. Tham số thứ hai liên kết lỗi I/O thành **cause**.
3. `start()` bắt `IllegalStateException`, tạo thêm đối tượng `RuntimeException("khởi động thất bại", ex)` với lỗi dịch vụ làm cause.
4. `main()` nhận chính `RuntimeException` từ tầng khởi động; đó là **outermost tại `main`**.
5. Vòng lặp bắt đầu từ outermost và gọi `getCause()` từng tầng tới `null`. Lỗi sâu nhất theo chuỗi là `IOException("ổ đĩa không phản hồi")`.
6. Trong ví dụ, mọi constructor bọc đều **giữ nguyên cause**, nên không tầng nào bị mất khỏi chuỗi.

Đầu ra này cố ý chỉ in **class đơn giản và message** để ổn định. Stack trace thật còn chứa các frame theo tệp và số dòng, là thông tin nên ghi lại khi chẩn đoán lỗi thực tế.

## 4. `getCause()` là liên kết nguyên nhân, không phải “ngoại lệ Java tự tìm”

`Throwable.getCause()` trả về **tham chiếu nguyên nhân đã được thiết lập** cho đối tượng, hoặc `null` khi không có nguyên nhân khả dụng. Nó không tự truy vấn hệ thống, không phân tích stack trace để suy luận lỗi sâu hơn và không tự tìm ra “root cause” từ log.

Hai cách liên kết phổ biến:

~~~java
// Cách 1: Constructor nhận message và cause.
throw new IllegalStateException("bước nghiệp vụ thất bại", original);
~~~

~~~java
// Cách 2: Một số Throwable cũ hỗ trợ initCause.
// Dùng khi nguyên nhân chưa được khởi tạo trước đó.
IllegalStateException wrapper = new IllegalStateException("bước nghiệp vụ thất bại");
wrapper.initCause(original);
throw wrapper;
~~~

Hai đoạn là **minh họa trong ngữ cảnh có `original` khác `null`**. Không được áp dụng `initCause()` tùy tiện sau một constructor đã thiết lập cause, kể cả thiết lập thành `null`. Phần lớn mã Java mới nên ưu tiên constructor `(message, cause)` khi lớp ngoại lệ cung cấp.

Việc bọc lỗi **không bắt buộc**: nếu tầng trên không có lý do thêm ngữ cảnh hoặc đổi hợp đồng, có thể `throw ex;` (ném lại cùng đối tượng) và không tạo thêm tầng cause. Một chuỗi sâu 10 lớp không mặc nhiên hữu ích hơn chuỗi ngắn nếu các lớp chỉ lặp lại thông điệp.

Cũng không nên tạo một ngoại lệ mới mà **bỏ qua lỗi gốc**:

~~~java
// Không tốt nếu cần bảo toàn thông tin điều tra.
catch (IOException ex) {
    throw new IllegalStateException("không tải cấu hình");
}
~~~

Khi đó `IllegalStateException` có `getCause() == null` dù `IOException` thực sự đã xảy ra. Người chỉ nhìn chuỗi `getCause()` ở ngoài sẽ **không thể tìm lại** lỗi I/O chỉ từ đối tượng wrapper mới này.

Nên dùng:

~~~java
catch (IOException ex) {
    throw new IllegalStateException("không tải cấu hình", ex);
}
~~~

Lưu ý về ví dụ: các khối `catch` trên cần đặt trong một phương thức có `try` chứa lời gọi phát sinh `IOException`.

## 5. Ngoại lệ sâu nhất có phải nguyên nhân gốc rễ thực sự không?

Không nhất thiết. **Deepest cause là một thuật ngữ về cấu trúc dữ liệu**, còn **root cause trong chẩn đoán vận hành** là câu trả lời cho “vì sao hệ thống không đạt kết quả mong muốn”.

Ví dụ 1: Chuỗi `IllegalStateException → IOException`, lỗi sâu nhất `IOException("Connection reset")`. Nhưng nguyên nhân vận hành có thể là cấu hình mạng, proxy, chính sách firewall, hết kết nối hoặc máy chủ quá tải. Bản thân `IOException` không chỉ ra duy nhất nguyên nhân đó.

Ví dụ 2: `InvalidOrderException → NumberFormatException`. Đối tượng sâu nhất có thể là `NumberFormatException`, nhưng lỗi nghiệp vụ cốt lõi có thể là dữ liệu CSV sai định dạng hoặc một hợp đồng API cho phép chuỗi rỗng đi qua.

Ví dụ 3: tầng thấp ném `NullPointerException`, tầng cao bọc thành `ServiceException`. `NullPointerException` ở cuối chuỗi có thể phản ánh một bug, nhưng muốn tìm nguyên nhân cần nhìn biến nào `null`, yêu cầu nào kích hoạt và code nào vi phạm bất biến.

Vì thế khi phân tích sự cố:

- Bắt đầu từ **outermost** để hiểu thao tác cấp cao nào thất bại.
- Đọc **toàn bộ cause chain**, không chỉ hai đầu.
- Đối chiếu **stack trace**, log, input và trạng thái chạy.
- Kiểm tra **suppressed exceptions**, đặc biệt khi dùng tài nguyên.
- Kết luận nguyên nhân gốc rễ thực sự từ **bằng chứng thực thi**, không chỉ vì một đối tượng nằm ở vị trí sâu nhất.

Nhiều thư viện gọi kết quả truy đến cuối `getCause()` là `rootCause` trong API. Đây là cách đặt tên tiện dụng, nhưng không phải một phép chứng minh tự động về nguyên nhân vận hành.

## 6. Nếu `getCause()` là `null`

`null` có thể có nhiều ý nghĩa:

1. Đối tượng thực sự là lỗi ban đầu và không có wrapper trước nó.
2. Người viết API chưa thiết lập cause, dù lỗi hiện tại phát sinh sau một lỗi khác.
3. Lỗi được chủ động công bố như thất bại nghiệp vụ độc lập, không có nguyên nhân dạng `Throwable` cần ghi lại.
4. Một wrapper bị tạo sai cách, làm mất chuỗi nguyên nhân.

Vì vậy **không được suy ra** rằng ngoại lệ có `getCause() == null` chắc chắn là lỗi đầu tiên từng xảy ra trong cả ứng dụng.

Ví dụ:

~~~java
// Đoạn minh họa đặt trong phương thức.
try {
    Integer.parseInt("abc");
} catch (NumberFormatException ex) {
    IllegalStateException wrapped = new IllegalStateException("dữ liệu không hợp lệ");
    System.out.println(wrapped.getCause() == null); // true
}
~~~

`NumberFormatException` đã xảy ra, nhưng `wrapped` không lưu nó. Trong log của `wrapped`, nguyên nhân sâu nhất kỹ thuật chính là `wrapped`; đó chỉ là **đáy chuỗi còn lại**, không phải toàn bộ lịch sử hệ thống.

Đừng gọi `outer.getCause().getMessage()` một cách vô điều kiện. Nếu cause là `null`, bạn sẽ gây `NullPointerException` mới ngay trong logic xử lý lỗi.

## 7. Một ngoại lệ duy nhất cũng có thể là outermost và deepest

Không phải lúc nào cũng có ba tầng:

~~~java
try {
    throw new IllegalArgumentException("tuổi âm");
} catch (IllegalArgumentException ex) {
    System.out.println(ex == ex);                  // true
    System.out.println(ex.getCause() == null);     // true
}
~~~

Đoạn minh họa chỉ chứa **một đối tượng ngoại lệ**, vừa là đối tượng được bắt ở ngoài cùng của ví dụ, vừa là tầng cuối trong chuỗi cause. Câu `ex == ex` chỉ minh họa đồng nhất đối tượng; trong code thật bạn sẽ so sánh với một tham chiếu khác nếu cần kiểm tra có tạo wrapper mới hay không.

Khi viết một hàm `deepestCause(Throwable t)`, nếu `t` khác `null` và `t.getCause() == null`, kết quả theo định nghĩa kỹ thuật thường chính là `t`, **không phải `null`**. Ngược lại, nếu API của bạn chọn trả `null` để biểu thị “không có cause riêng”, đó là một **hợp đồng khác** cần đặt tên và tài liệu rõ ràng.

## 8. `cause` là chuỗi liên kết; `suppressed` là các nhánh phụ

`Throwable` không chỉ có một tham chiếu tới cause, mà còn có **mảng suppressed exceptions**. Để hình dung:

~~~text
IllegalStateException: không nạp được dữ liệu  ← outermost
    |
    └── Caused by: IOException: đọc tệp lỗi   ← deepest theo getCause()
                       |
                       ├── Suppressed: IOException: đóng tệp lỗi
                       └── Suppressed: ... (nếu có thêm)
~~~

Trong cấu trúc này:

- Outermost là `IllegalStateException` vì nó đang được bắt ở tầng trên.
- `IllegalStateException.getCause()` trỏ tới `IOException("đọc tệp lỗi")`.
- `IOException("đọc tệp lỗi").getCause()` là `null`, nên đó là deepest cause.
- Lỗi `IOException("đóng tệp lỗi")` nằm trong **`getSuppressed()` của lỗi đọc**, không phải trong `getCause()`.
- `outermost.getSuppressed()` có thể rỗng, dù một cause bên dưới có suppressed exception.

**Quan hệ khác nhau:** cause trả về tối đa **một tham chiếu trực tiếp** trên mỗi `Throwable`, còn suppressed có thể gồm **nhiều đối tượng**. Mỗi suppressed exception lại có thể có cause và suppressed của riêng nó.

Một trình duyệt chỉ chạy:

~~~java
Throwable current = outermost;
while (current != null) {
    System.out.println(current);
    current = current.getCause();
}
~~~

sẽ **không in các suppressed exceptions**. Đó không phải bug trong `getCause()`; người viết mã chỉ đang duyệt một loại liên kết.

## 9. Chương trình Java 17+: nhìn cause và suppressed cùng lúc

Lưu tệp `SuppressedCauseChainDemo.java`:

~~~java
import java.io.IOException;

public class SuppressedCauseChainDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("đóng tệp lỗi");
        }
    }

    static void read() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("đọc tệp lỗi");
        }
    }

    static void service() {
        try {
            read();
        } catch (IOException ex) {
            throw new IllegalStateException("không nạp được dữ liệu", ex);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IllegalStateException outer) {
            Throwable root = outer.getCause();
            System.out.println("Ngoài cùng: " + outer.getMessage());
            System.out.println("Sâu nhất: " + root.getMessage());
            System.out.println("Suppressed ngoài cùng: " + outer.getSuppressed().length);
            System.out.println("Suppressed nguyên nhân: " + root.getSuppressed().length);
            System.out.println("Lỗi phụ: " + root.getSuppressed()[0].getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SuppressedCauseChainDemo.java
java SuppressedCauseChainDemo
~~~

Stdout chính xác:

~~~text
Ngoài cùng: không nạp được dữ liệu
Sâu nhất: đọc tệp lỗi
Suppressed ngoài cùng: 0
Suppressed nguyên nhân: 1
Lỗi phụ: đóng tệp lỗi
~~~

Giải thích:

1. Trong `read()`, thân `try-with-resources` ném `IOException("đọc tệp lỗi")` làm **ngoại lệ chính**.
2. Khi rời phần thân, Java tự gọi `Resource.close()` và gặp `IOException("đóng tệp lỗi")`; lỗi đóng được thêm vào **suppressed** của lỗi đọc.
3. `service()` bắt lỗi đọc (đã có suppressed), rồi tạo một `IllegalStateException` mới có lỗi đọc làm cause.
4. `main()` bắt `IllegalStateException` làm outermost. Vì wrapper mới không được gắn suppressed, `outer.getSuppressed().length == 0`.
5. Nguyên nhân của wrapper là `IOException("đọc tệp lỗi")`, nên `root.getSuppressed().length == 1`.
6. Chương trình lấy phần tử đầu `getSuppressed()[0]` vì đường thực thi của **ví dụ cố định này** chắc chắn tạo một suppressed exception. Trong code tổng quát phải kiểm tra độ dài mảng trước khi truy cập.

Nếu chỉ đọc `outer.getMessage()`, bạn mất thông tin về lỗi đọc. Nếu chỉ đọc `outer.getCause().getMessage()`, bạn bỏ qua lỗi đóng. Cả hai đều có thể quan trọng, nhưng mang vai trò khác nhau.

## 10. Cách đọc `printStackTrace()` và phần `Caused by:`

`Throwable.printStackTrace()` của một chuỗi wrapper thông thường có thể xuất ra một dạng như:

~~~text
java.lang.RuntimeException: khởi động thất bại
    at Example.start(Example.java:...)
    at Example.main(Example.java:...)
Caused by: java.lang.IllegalStateException: không tải cấu hình
    at Example.service(Example.java:...)
    ...
Caused by: java.io.IOException: ổ đĩa không phản hồi
    at Example.repository(Example.java:...)
    ...
~~~

Đây là **sơ đồ minh họa**, không phải đầu ra cố định từ tệp `CauseChainDemo.java`: tên lớp/tệp, số dòng, cách rút gọn frame và in stack trace phụ thuộc mã thực tế.

Khi đọc từ trên xuống:

- Dòng đầu mô tả **outermost** mà ta đang in.
- Mỗi phần `Caused by:` thể hiện một liên kết cause sâu hơn.
- Phần `Caused by:` cuối cùng thường tương ứng với **deepest cause của chuỗi hữu hạn**.
- Dòng `Suppressed:` nếu có là lỗi phụ, có thể xuất hiện dưới **bất kỳ tầng Throwable nào**, không chỉ ở outermost.
- Các dòng `... N more` (khi xuất hiện) là cách rút gọn các frame stack trùng ở chuỗi liên quan; chúng **không** phải đối tượng ngoại lệ mới.

Đừng đồng nhất *dòng stack trace cao nhất* với *đáy chuỗi cause*. Outermost có stack trace riêng tại thời điểm wrapper được tạo; deepest có stack trace riêng của nó, thường gần vị trí phát sinh lỗi cấp thấp.

## 11. Stack trace và chuỗi cause là hai chiều thông tin khác nhau

Một `Throwable` có thể có stack trace với các frame gọi hàm nhưng **không có cause**. Ngược lại, một wrapper có `cause` nhưng được cấu hình không ghi stack trace ở chính nó. Vì vậy:

- `getStackTrace()` trả các **frame** được ghi nhận cho đối tượng hiện tại.
- `getCause()` trả **đối tượng Throwable khác** được chỉ định là nguyên nhân.
- `getSuppressed()` trả **các đối tượng Throwable khác** được ghi nhận như lỗi phụ.

Ví dụ nếu:

~~~java
Throwable error = new IllegalArgumentException("sai dữ liệu");
Throwable wrapper = new IllegalStateException("không xử lý được", error);
~~~

thì `wrapper.getStackTrace()` không phải “cùng thứ” với `wrapper.getCause().getStackTrace()`. Chúng mô tả các điểm tạo/ghi trace khác nhau của các đối tượng khác nhau.

Khi ném lại cùng đối tượng bằng `throw ex;`, Java **không mặc định tạo wrapper mới**, nên danh tính và chuỗi cause vẫn như cũ. Khi `throw new SomeException("...", ex);`, bạn có một đối tượng ngoài cùng mới, thường mang stack trace ở vị trí wrap.

Việc `fillInStackTrace()` hoặc `setStackTrace()` có thể thay đổi trace của đối tượng theo hợp đồng API, nên không nên giả định một stack trace bất biến mãi mãi trong mọi thư viện. Tuy nhiên những thay đổi đó không tự tạo thêm liên kết cause.

## 12. Hàm tìm nguyên nhân sâu nhất: trường hợp đơn giản

Với các ngoại lệ do ứng dụng tạo bằng cách bọc bình thường, chuỗi cause thường hữu hạn và kết thúc ở `null`. Khi chắc chắn không có vòng lặp, cách viết dễ hiểu là:

~~~java
// Mã minh họa bên trong một lớp.
static Throwable deepestCause(Throwable error) {
    if (error == null) {
        return null;
    }
    Throwable current = error;
    while (current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
~~~

Ví dụ:

~~~java
Throwable low = new java.io.IOException("I/O lỗi");
Throwable outer = new IllegalStateException("dịch vụ lỗi", low);

System.out.println(deepestCause(outer) == low); // true
~~~

Các đoạn này là **minh họa cần đặt trong một lớp**, với phương thức `deepestCause` nằm cùng phạm vi truy cập hoặc được gọi đúng cách. Hàm trả về ngoại lệ cuối theo `getCause()`, không truy cập `getSuppressed()`.

Nhưng đoạn `while` đơn giản **giả định chuỗi không có vòng tham chiếu**. Trong mã công cụ chẩn đoán phải xử lý đầu vào tùy ý từ các thư viện khác, nên phòng trường hợp chuỗi `getCause()` bất thường bị lặp.

## 13. Chuỗi `cause` có thể bị tạo thành vòng: không có “đáy” hữu hạn

Java không cho một đối tượng tự dùng **chính nó** làm cause qua `initCause`, nhưng mã tùy chỉnh vẫn có thể tạo **vòng giữa nhiều đối tượng** nếu ghép các liên kết `cause` sai cách.

Ví dụ A trỏ tới B, B trỏ về A:

~~~text
Exception("A") ──cause──> Exception("B")
      ^                         |
      └────────cause────────────┘
~~~

Nếu dùng vòng lặp `while (current.getCause() != null)` thông thường, nó sẽ chạy vô hạn. Cách duyệt an toàn nên theo dõi những **đối tượng Throwable đã thấy theo danh tính tham chiếu**, và nếu gặp lại đối tượng cũ thì báo có vòng thay vì tự tuyên bố một deepest cause.

Tệp `CauseWalkerDemo.java`:

~~~java
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class CauseWalkerDemo {
    static void printChain(Throwable outer) {
        Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = outer;
        int index = 0;

        while (current != null) {
            if (!visited.add(current)) {
                System.out.println("Phát hiện vòng: " + current.getMessage());
                return;
            }
            System.out.println(index + ": " + current.getMessage());
            Throwable next = current.getCause();
            if (next == null) {
                System.out.println("Đáy chuỗi: " + current.getMessage());
                return;
            }
            current = next;
            index++;
        }
    }

    public static void main(String[] args) {
        Exception a = new Exception("A");
        Exception b = new Exception("B");
        a.initCause(b);
        b.initCause(a);
        printChain(a);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CauseWalkerDemo.java
java CauseWalkerDemo
~~~

Stdout chính xác:

~~~text
0: A
1: B
Phát hiện vòng: A
~~~

Ở ví dụ này **không tồn tại deepest cause theo định nghĩa “`getCause() == null`”**: đi mãi sẽ quay lại A. Đây là chuỗi được tạo cố ý để kiểm thử an toàn, không phải thiết kế ngoại lệ nên dùng trong ứng dụng.

`IdentityHashMap` được dùng để so sánh theo **danh tính đối tượng (`==`)**, không phụ thuộc vào `equals()` tùy chỉnh nếu có. Bất kỳ công cụ duyệt cả cause và suppressed cũng cần tính tới khả năng một đối tượng bị tham chiếu lặp lại theo nhiều đường khác nhau.

## 14. `getSuppressed()` có thể nằm ở bất kỳ tầng nào

Khi phân tích ngoại lệ ngoài cùng `outer`, bạn không nên chỉ tìm suppressed ở `outer` rồi kết luận toàn bộ chuỗi không có lỗi phụ. Một lỗi phụ có thể gắn vào cause thứ nhất, thứ hai hoặc sâu hơn:

~~~text
Outer A
└── cause B
     ├── suppressed S1
     ├── suppressed S2
     └── cause C
          └── suppressed S3
~~~

Đoạn trên là **sơ đồ quan hệ**, không biểu diễn thứ tự thực thi tuyệt đối. Nếu mục tiêu là hiển thị đầy đủ một chuỗi thất bại, bạn cần xem **cả `getCause()` và `getSuppressed()` trên từng đối tượng đã gặp**, với cơ chế tránh vòng lặp/trùng tham chiếu.

Nói ngắn gọn:

- Chỉ lấy **deepest cause**: đi theo `getCause()`.
- Muốn **tất cả lỗi liên quan**: đi cả cause và suppressed, lưu visited theo danh tính.
- Muốn **nguyên nhân vận hành thực sự**: kết hợp thêm dữ liệu hệ thống và hiểu biết về thao tác.

Một suppressed exception cũng có thể chỉ ra lỗi tài nguyên quan trọng, ví dụ không lưu được dữ liệu khi đóng stream; trong vài tình huống, lỗi phụ đó đáng xử lý hơn một phần của lỗi ngoài cùng. Thứ tự trong cây Throwable là mô hình lưu trữ **thông tin ngoại lệ**, không phải bảng xếp hạng mức độ ưu tiên khắc phục sự cố.

## 15. Khi nào nên bọc ngoại lệ và khi nào ném lại?

**Bọc ngoại lệ** thích hợp khi một tầng muốn thêm ngữ cảnh có giá trị hoặc chuyển từ lỗi kỹ thuật sang lỗi thuộc hợp đồng của tầng đó.

Ví dụ tầng đọc file:

~~~java
// Đoạn minh họa trong catch (IOException ex).
throw new ConfigurationLoadException("Không nạp được cấu hình", ex);
~~~

Đoạn này cần lớp `ConfigurationLoadException` được định nghĩa với constructor nhận `(String, Throwable)`. Nó nên giải thích thao tác mà tầng hiện tại đảm nhận và giữ cause.

**Ném lại cùng ngoại lệ** thích hợp khi tầng hiện tại không có phương án xử lý, và loại ngoại lệ vẫn là hợp đồng hợp lý cho caller:

~~~java
// Đoạn minh họa trong catch (IOException ex),
// phương thức chứa có throws IOException.
throw ex;
~~~

Không nên bọc liên tiếp ở mọi tầng chỉ với thông điệp gần như nhau:

~~~text
RuntimeException("lỗi")
└── RuntimeException("lỗi")
    └── RuntimeException("lỗi")
        └── IOException("lỗi đọc")
~~~

Chuỗi trên giữ được cause nhưng có thể làm log dài và khó đọc mà không tăng khả năng điều tra. Ngược lại, việc **bỏ cause** khi bọc tạo ra outermost không còn liên kết tới nguồn lỗi. Hãy chọn cách bọc theo **ranh giới abstraction và thông tin mới thực sự bổ sung**, không theo thói quen.

Trong API công khai, cũng cần cân nhắc thông điệp và thông tin chẩn đoán có thể chứa dữ liệu nhạy cảm; giữ cause nội bộ phù hợp với chính sách log và thông tin trả cho người dùng.

## 16. Các wrapper quen thuộc trong Java

Nhiều API tạo một ngoại lệ ở ranh giới thực thi để báo rằng một tác vụ/phản chiếu/chuỗi hoàn thành bất thường. Một số ví dụ:

- `ExecutionException` thường xuất hiện khi gọi `Future.get()` và tác vụ đã thất bại; nguyên nhân của nó có thể là ngoại lệ do tác vụ ném.
- `CompletionException` thường xuất hiện trong một số đường của `CompletableFuture` và có thể bọc lỗi thực tế ở cause.
- `InvocationTargetException` có thể xuất hiện khi gọi phương thức qua reflection; `getCause()` hoặc API lịch sử `getTargetException()` giúp xem lỗi phát sinh từ phương thức được gọi.
- Các lớp ngoại lệ riêng của framework cũng có thể bọc `IOException`, `SQLException` hoặc một exception nghiệp vụ.

Không nên **tự động bỏ mọi lớp wrapper** mà không xem điều kiện lỗi. Ví dụ `ExecutionException` nói cho caller biết **đường lỗi liên quan tới kết quả tác vụ bất đồng bộ**, còn cause có thể nói lỗi nào xảy ra bên trong. Cả hai ngữ cảnh đều hữu ích.

Một lưu ý khác: `Future.get()` cũng có thể ném những loại lỗi khác như `InterruptedException` hoặc `CancellationException`. Không nên suy luận rằng mọi exception xuất hiện ở API bất đồng bộ đều chắc chắn có cause hoặc có tầng sâu hơn.

## 17. Thu thập dữ liệu lỗi tại ranh giới xử lý

Một chiến lược chẩn đoán tốt khi xử lý ngoại lệ cấp cao:

1. **Ghi tên thao tác và bối cảnh an toàn**, ví dụ loại request, id giao dịch không nhạy cảm, giai đoạn xử lý.
2. **Giữ ngoại lệ ngoài cùng**, vì nó cho biết tầng nào coi sự cố là thất bại.
3. **Giữ toàn bộ Throwable khi log** thay vì chỉ chuỗi `getMessage()`; một logger phù hợp có thể hiển thị stack trace, cause và suppressed.
4. **Truy theo `getCause()`** khi cần nhóm lỗi theo tầng hoặc tìm exception cấp thấp nhất.
5. **Kiểm tra `getSuppressed()`** trên những tầng có tài nguyên được đóng hoặc logic cleanup.
6. **Không ghi log cùng ngoại lệ ở mọi tầng** nếu chỉ chuyển tiếp; log lặp gây nhiễu và làm khó xác định số sự cố thực tế.
7. **Không phụ thuộc vào message cố định** làm API điều khiển logic; ưu tiên loại ngoại lệ, mã lỗi được tài liệu hóa hoặc kết quả có cấu trúc.

Ví dụ minh họa ngắn:

~~~java
// Đoạn minh họa trong catch (Exception ex).
logger.error("Không xử lý được yêu cầu", ex);
~~~

`logger` trong ví dụ cần là logger có API phù hợp với chữ ký `error(String, Throwable)`. Đây là **mẫu ý tưởng**, không phải chương trình độc lập; mục tiêu là cho hệ thống log nhận **đối tượng Throwable**, giữ thông tin cause/suppressed thay vì chỉ `ex.getMessage()`.

Ở tầng API trả về cho người dùng, có thể hiển thị thông điệp an toàn, trong khi log nội bộ giữ thông tin chi tiết theo chính sách quan sát hệ thống. Không tự động đẩy toàn bộ stack trace tới người dùng cuối.

## 18. Các lỗi thường gặp

| Nhầm lẫn | Hệ quả | Cách sửa |
| --- | --- | --- |
| Ngoại lệ bắt ở `catch` luôn là lỗi gốc | Bỏ qua wrapper, không tìm được lỗi tầng thấp | Duyệt `getCause()` khi cần |
| `outer.getCause()` luôn là deepest cause | Nếu có nhiều tầng, đó chỉ là nguyên nhân trực tiếp | Duyệt tới cause cuối hoặc phát hiện vòng |
| `getCause() == null` chứng minh lỗi chưa từng có nguyên nhân khác | Cause có thể không được ghi lại khi bọc | Kiểm tra code tạo exception và log liên quan |
| Bọc ngoại lệ mà bỏ đối tượng gốc | Mất quan hệ nguyên nhân và trace của lỗi trước | Dùng constructor `(message, cause)` |
| `throw ex` tạo thêm một wrapper | Thực tế thường ném lại cùng đối tượng | Phân biệt rethrow với `throw new` |
| Chỉ xem suppressed ở outermost | Lỗi phụ có thể nằm ở một cause sâu hơn | Xem suppressed trên từng tầng Throwable |
| Coi suppressed chính là cause | Sai mô hình quan hệ | Phân biệt `getCause()` và `getSuppressed()` |
| Luôn tin deepest cause là nguyên nhân vận hành cuối cùng | Có thể chẩn đoán sai lỗi hệ thống/dữ liệu | Đối chiếu input, môi trường, stack trace, logs |
| Gọi `getCause().getMessage()` không kiểm tra `null` | Có thể tạo `NullPointerException` trong handler | Kiểm tra `getCause()` trước |
| Duyệt `getCause()` vô hạn với Throwable tùy ý | Có thể gặp vòng tham chiếu | Theo dõi visited bằng identity |
| Chỉ log `ex.getMessage()` | Mất stack trace, cause, suppressed | Log đối tượng exception đầy đủ |
| Bọc ngoại lệ mọi tầng với thông điệp lặp | Cause chain dài, nhiễu log | Chỉ bọc tại ranh giới có ngữ cảnh mới |
| Dùng `getSuppressed()[0]` mà không kiểm tra | `ArrayIndexOutOfBoundsException` nếu mảng rỗng | Kiểm tra độ dài trước khi truy cập |

## 19. Bài tập tự kiểm tra

**Câu 1.** `RuntimeException("start", IllegalStateException("service", IOException("read")))`: ngoại lệ nào ngoài cùng?

**Đáp án:** `RuntimeException("start")` nếu đó là đối tượng caller đang bắt hoặc quan sát ở ranh giới hiện tại.

**Câu 2.** Đối tượng nào sâu nhất nếu chuỗi `getCause()` kết thúc ở `IOException("read")`?

**Đáp án:** `IOException("read")`.

**Câu 3.** `outer.getCause()` có luôn trả deepest cause không?

**Đáp án:** Không. Nó trả **nguyên nhân trực tiếp**; phải tiếp tục đi theo `getCause()` để tìm tầng cuối.

**Câu 4.** Nếu outermost có `getCause() == null`, có thể kết luận không có lỗi cấp thấp từng xảy ra không?

**Đáp án:** Không. Lỗi có thể không được lưu vào cause khi tạo wrapper.

**Câu 5.** `IOException` sâu nhất có một suppressed `IOException("close fail")`. Lỗi đóng có nằm trong chuỗi `getCause()` không?

**Đáp án:** Không chỉ vì nó là suppressed. Phải xem `getSuppressed()` của đối tượng chứa lỗi phụ.

**Câu 6.** Muốn giữ lỗi gốc khi chuyển `IOException` thành `IllegalStateException`, nên viết gì?

**Đáp án:** `throw new IllegalStateException("mô tả tầng mới", ex);`.

**Câu 7.** Nếu chỉ có một đối tượng ngoại lệ và `getCause() == null`, đối tượng nào là deepest cause theo cách duyệt kỹ thuật?

**Đáp án:** Chính đối tượng đó.

**Câu 8.** Khi chuỗi `getCause()` có vòng A → B → A, có deepest cause hữu hạn không?

**Đáp án:** Không. Mã duyệt phải phát hiện vòng và báo chuỗi bất thường.

**Câu 9.** Một outermost wrapper có stack trace riêng, còn cause có stack trace riêng. Hai trace có nhất thiết giống nhau không?

**Đáp án:** Không; hai đối tượng có thể được tạo tại các vị trí khác nhau.

**Câu 10.** Vì sao không nên chẩn đoán chỉ bằng deepest cause?

**Đáp án:** Vì đó chỉ là đáy chuỗi liên kết ngoại lệ, chưa chắc chỉ ra nguyên nhân vận hành/kinh doanh thật sự; vẫn cần ngữ cảnh và bằng chứng bổ sung.

## 20. Tài liệu tham khảo chính thức

- [Java SE 17 API — `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `initCause()`, `getSuppressed()`, `addSuppressed()`, `printStackTrace()` và stack trace.
- [Java SE 17 API — `IOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html): ngoại lệ checked thường xuất hiện ở tầng I/O.
- [Java SE 17 API — `ExecutionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): exception wrapper cho kết quả tác vụ thất bại.
- [Java SE 17 API — `CompletionException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html): wrapper trong xử lý hoàn thành bất đồng bộ.
- [Java SE 17 API — `InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): lỗi phát sinh khi gọi thông qua reflection.
- [Java Language Specification 17 — §11, Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html): phân cấp, ném và xử lý ngoại lệ.
- [Java Language Specification 17 — §14.20.3, `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cơ chế suppressed exceptions khi đóng tài nguyên.

**Kết luận:** ngoại lệ ngoài cùng là đối tượng được quan sát tại điểm xử lý; nguyên nhân sâu nhất là điểm cuối hữu hạn của liên kết `getCause()`; còn `getSuppressed()` lưu các nhánh lỗi phụ khác. Hãy giữ cause khi bọc, kiểm tra suppressed ở những tầng liên quan, và kết hợp toàn bộ stack trace cùng bối cảnh thực thi trước khi kết luận nguyên nhân gốc rễ của sự cố.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-004">Suppressed exception và quan hệ với try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# Suppressed exception và quan hệ với try-with-resources trong Java

Trong Java, một thao tác có thể ném **nhiều ngoại lệ liên quan tới cùng một lần thực hiện**: công việc chính thất bại, rồi thao tác đóng tài nguyên cũng thất bại. Nếu chương trình chỉ giữ ngoại lệ sau cùng, thông tin về lỗi ban đầu có thể bị che khuất. Cơ chế **suppressed exceptions** của `Throwable` cho phép gắn những ngoại lệ phụ vào **ngoại lệ chính (primary exception)** mà không làm mất đối tượng lỗi ban đầu.

`try-with-resources` là nơi cơ chế này thường xuất hiện: Java tự động đóng các tài nguyên `AutoCloseable`. Khi thân `try` đang ném exception A và `close()` lại ném exception B, **A thường tiếp tục là exception chính; B được lưu trong `A.getSuppressed()`**. Đây là điểm khác biệt quan trọng so với một khối `finally` thông thường có thể ném B và che mất A.

Bài này đi từ khái niệm, API của `Throwable`, thứ tự khởi tạo/đóng, các tình huống một hoặc nhiều tài nguyên thất bại, `getCause()` so với `getSuppressed()`, việc vô hiệu hóa suppression, đến cách kiểm thử Java 17+.

## 1. Vì sao cần suppressed exception?

Hình dung việc ghi tệp:

1. Mở kết nối hoặc tệp.
2. Thực hiện thao tác ghi.
3. Thao tác ghi ném `IOException("write failed")`.
4. Chương trình cố đóng tài nguyên.
5. `close()` ném `IOException("close failed")`.

Cả hai lỗi đều cần được biết. **Lỗi ghi** giải thích vì sao tác vụ chính thất bại; **lỗi đóng** có thể gợi ý tài nguyên không được giải phóng đúng, hoặc thao tác flush/close chưa hoàn tất.

Nếu dùng cleanup thủ công thiếu bảo vệ:

```java
try {
    writeData();
} finally {
    closeResource();
}
```

đây là **mã khung minh họa**, chưa có định nghĩa hai phương thức. Khi `writeData()` ném lỗi A và `closeResource()` ném B, theo quy tắc hoàn tất của `finally`, **B có thể thay thế A** như exception lan ra ngoài. Java không tự nối A thành cause hoặc suppressed của B trong cấu trúc `try/finally` đó.

Nếu dùng `try-with-resources`:

```java
try (SomeResource resource = open()) {
    resource.writeData();
}
```

đây cũng là **mã khung**, với `SomeResource` triển khai `AutoCloseable`. Khi thân `try` ném A và `close()` ném B, cơ chế do Java thực hiện sẽ cố giữ **A là primary** và gắn **B vào danh sách suppressed của A**, với những điều kiện cụ thể như suppression của primary không bị vô hiệu hóa.

**Suppressed không có nghĩa “lỗi không đáng quan tâm”.** Nó chỉ mô tả quan hệ ưu tiên khi nhiều lỗi cùng xuất hiện; hệ thống vẫn cần kiểm tra, ghi nhận và xử lý những ảnh hưởng của lỗi cleanup.

## 2. Ba loại thông tin phải phân biệt: primary, cause và suppressed

| Khái niệm | Ý nghĩa | Cách truy cập |
| --- | --- | --- |
| **Primary exception** | Ngoại lệ chính đang được ném hoặc được handler nhận trong một tình huống | Chính biến exception ở `catch`, chẳng hạn `e` |
| **Cause** | Ngoại lệ là nguyên nhân dẫn đến một ngoại lệ khác, thường khi một tầng bọc/chuyển loại lỗi | `e.getCause()` |
| **Suppressed exception** | Ngoại lệ phụ được lưu cùng primary thay vì thay thế nó | `e.getSuppressed()` |

Ví dụ nghiệp vụ:

- Repository ném `IOException` do không đọc được tệp.
- Service tạo `ConfigurationException("Không thể khởi động", ioError)`. `ioError` trở thành **cause** của wrapper.
- Khi resource được đóng, `close()` ném lỗi khác; nếu lỗi đó được suppressed vào `ioError`, nó nằm trong **`ioError.getSuppressed()`**, không tự động chuyển sang danh sách suppressed của wrapper.
- Caller có thể phải theo cả **chuỗi cause** lẫn **các suppressed exception trên từng node** để chẩn đoán đầy đủ.

Cause và suppressed không phải hai tên gọi cho cùng một mảng. Một `Throwable` có thể có **một cause** và **nhiều suppressed exceptions**, mỗi object lại có stack trace riêng.

## 3. API của `Throwable`: `getSuppressed()` và `addSuppressed()`

Các phương thức quan trọng:

```java
public final synchronized void addSuppressed(Throwable exception)
public final synchronized Throwable[] getSuppressed()
```

Đây là **chữ ký API minh họa** (không phải khai báo được tự viết lại trong ứng dụng). `addSuppressed` gắn một Throwable phụ vào đối tượng hiện tại; `getSuppressed` trả về **mảng các suppressed exception** đã được ghi nhận.

Những tính chất cần hiểu:

- `getSuppressed()` trả mảng, không phải một exception đơn lẻ.
- Với object không có suppressed exception, kết quả là **mảng rỗng**, không phải `null`.
- Mảng được trả về là một **bản sao**; thay phần tử của mảng đó không sửa danh sách nội bộ trên Throwable.
- Các phần tử có thứ tự theo lúc được thêm; với `try-with-resources`, thường gắn với **thứ tự đóng tài nguyên**.
- `addSuppressed(null)` không hợp lệ và ném `NullPointerException`.
- Không được tự suppress chính object vào nó: `e.addSuppressed(e)` ném `IllegalArgumentException` vì **self-suppression** không hợp lệ.
- Nếu suppression đã được vô hiệu hóa khi tạo Throwable, `addSuppressed` không lưu thêm lỗi phụ như bình thường.

Bạn hiếm khi phải tự gọi `addSuppressed` nếu đang dùng `try-with-resources` chuẩn; Java sẽ thực hiện việc liên kết khi đúng tình huống.

## 4. Ví dụ đầu tiên: tự thêm một lỗi phụ

Lưu tệp **`SuppressedApiDemo.java`**:

```java
public class SuppressedApiDemo {
    public static void main(String[] args) {
        IllegalStateException primary =
                new IllegalStateException("Lỗi thao tác");
        IllegalArgumentException cleanup =
                new IllegalArgumentException("Lỗi cleanup");

        primary.addSuppressed(cleanup);

        System.out.println("Primary: " + primary.getMessage());
        System.out.println("Số suppressed: "
                + primary.getSuppressed().length);
        System.out.println("Phụ: "
                + primary.getSuppressed()[0].getMessage());
        System.out.println("Cùng object phụ: "
                + (primary.getSuppressed()[0] == cleanup));
        System.out.println("Cause là null: "
                + (primary.getCause() == null));
    }
}
```

Biên dịch/chạy trên Java 17+:

```bash
javac -encoding UTF-8 --release 17 SuppressedApiDemo.java
java SuppressedApiDemo
```

**Stdout chính xác** với console UTF-8:

```text
Primary: Lỗi thao tác
Số suppressed: 1
Phụ: Lỗi cleanup
Cùng object phụ: true
Cause là null: true
```

Kết quả cho thấy danh sách suppressed chứa **đúng object `cleanup`** (không có đối tượng mới được tạo chỉ để suppress), và không liên quan tới `getCause()`. `getMessage()` của primary vẫn là `"Lỗi thao tác"`.

Nếu muốn đọc nhiều lỗi phụ, hãy **duyệt mảng** từ `getSuppressed()`. Trong ứng dụng thật, không giả định luôn có phần tử `[0]`; cần kiểm tra độ dài trước khi lấy chỉ số.

## 5. `try-with-resources` tự động kết nối primary và suppressed

`try-with-resources` dùng dạng:

```java
try (Resource resource = open()) {
    work(resource);
} catch (SomeException e) {
    handle(e);
}
```

Đây là **khung minh họa**, các kiểu và phương thức chưa định nghĩa. Giả sử `Resource implements AutoCloseable`, quá trình là:

1. Đánh giá biểu thức khởi tạo resource.
2. Nếu thành công, chạy thân `try`.
3. Khi rời thân `try`, Java **cố gọi `close()`** trên resource khác `null`.
4. Nếu thân `try` đã ném A, lúc `close()` ném B, Java **gắn B vào A bằng cơ chế suppressed** nếu việc thêm lỗi được hỗ trợ.
5. Sau cleanup, exception chính được chuyển đến handler thích hợp; `catch` gắn trực tiếp với câu lệnh đó chạy **sau khi đã cố đóng tài nguyên**.

Nếu thân `try` kết thúc bình thường mà `close()` ném B, thì **B không có A để bám vào** và trở thành exception chính.

Điểm cốt lõi là `try-with-resources` không cố “bắt hết lỗi” để chương trình thành công. Nó quản lý tài nguyên và **bảo toàn đường lan truyền ngoại lệ** theo một cấu trúc ít dễ mắc lỗi hơn `finally` tự viết.

## 6. Ví dụ đầy đủ: thân `try` lỗi, `close()` cũng lỗi

Lưu **`PrimaryAndSuppressedDemo.java`**:

```java
public class PrimaryAndSuppressedDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close() chạy");
            throw new IllegalStateException("Không đóng được");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Thực hiện công việc");
            throw new IllegalArgumentException("Dữ liệu không hợp lệ");
        } catch (IllegalArgumentException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Số suppressed: "
                    + e.getSuppressed().length);
            for (Throwable secondary : e.getSuppressed()) {
                System.out.println("Suppressed: "
                        + secondary.getMessage());
            }
        }
        System.out.println("Sau try/catch");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 PrimaryAndSuppressedDemo.java
java PrimaryAndSuppressedDemo
```

**Stdout chính xác:**

```text
Thực hiện công việc
close() chạy
Primary: Dữ liệu không hợp lệ
Số suppressed: 1
Suppressed: Không đóng được
Sau try/catch
```

Lý giải:

- `IllegalArgumentException` là lỗi A từ thân `try`.
- `Resource.close()` chạy khi rời block và ném `IllegalStateException` B.
- Java không thay A bằng B. B được thêm vào `A.getSuppressed()`.
- `catch (IllegalArgumentException e)` nhận **A**, không phải B.
- Lệnh sau cấu trúc chạy vì handler đã kết thúc bình thường.

Nếu chỉ có handler `catch (IllegalStateException e)` ở cùng tầng, nó **không** bắt A chỉ vì A có B kiểu `IllegalStateException` trong danh sách suppressed. Việc chọn `catch` dựa trên **kiểu exception chính đang được ném**, không dựa trên các suppressed exceptions.

## 7. Nếu chỉ `close()` thất bại thì sao?

Nếu thân `try` chạy bình thường và `close()` ném exception, lỗi đóng trở thành lỗi chính của câu lệnh. Không có primary cũ để suppress vào.

Lưu **`CloseOnlyDemo.java`**:

```java
public class CloseOnlyDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đóng tài nguyên");
            throw new IllegalStateException("Đóng lỗi");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Thân try hoàn tất");
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
            System.out.println("Suppressed: "
                    + e.getSuppressed().length);
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CloseOnlyDemo.java
java CloseOnlyDemo
```

**Stdout chính xác:**

```text
Thân try hoàn tất
Đóng tài nguyên
Bắt: Đóng lỗi
Suppressed: 0
```

Thao tác trong thân `try` đã hoàn thành, nhưng **toàn bộ câu lệnh try-with-resources vẫn thất bại** vì không đóng được tài nguyên. Không nên báo thành công cuối cùng nếu hợp đồng thao tác đòi hỏi đóng hoặc flush thành công.

Đây là một sai lầm về thuật ngữ phổ biến: “exception phát sinh trong close luôn là suppressed”. **Không đúng**; nó chỉ là suppressed nếu có một exception đang được ưu tiên giữ lại (hoặc có một lỗi đóng xảy ra trước trong quá trình đóng nhiều resource).

## 8. Nhiều tài nguyên: thứ tự khởi tạo và đóng

Với:

```java
try (Resource first = open("A");
     Resource second = open("B");
     Resource third = open("C")) {
    performWork();
}
```

đây là **mã khung minh họa**. Java đánh giá các phần khai báo tài nguyên **từ trái sang phải**: A, B, C. Khi rời block, tài nguyên đã khởi tạo thành công được cố đóng **từ phải sang trái**: C, B, A.

Nếu thân `try` ném exception A0 và cả ba `close()` đều ném C0, B0, A1, thì A0 vẫn là primary; các lỗi đóng có thể được thêm suppressed theo **C0 → B0 → A1**.

Nếu thân `try` không ném lỗi, nhưng `close()` của C ném C0 trước, C0 trở thành primary. Khi `close()` của B rồi A tiếp tục ném, chúng có thể được suppressed **vào C0**.

Không nhầm thứ tự trong phần khai báo với thứ tự trong `getSuppressed()`; thứ tự suppressed gắn với các lỗi phát sinh thực tế trong quá trình dọn dẹp.

## 9. Ví dụ: nhiều lỗi đóng trong khi thân `try` đã có primary

Lưu **`MultipleCloseFailuresDemo.java`**:

```java
public class MultipleCloseFailuresDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
            throw new IllegalStateException("Lỗi đóng " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B");
             Resource c = new Resource("C")) {
            System.out.println("Trong try");
            throw new IllegalArgumentException("Lỗi xử lý");
        } catch (IllegalArgumentException e) {
            System.out.println("Chính: " + e.getMessage());
            System.out.println("Số phụ: " + e.getSuppressed().length);
            for (Throwable secondary : e.getSuppressed()) {
                System.out.println("Phụ: " + secondary.getMessage());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 MultipleCloseFailuresDemo.java
java MultipleCloseFailuresDemo
```

**Stdout chính xác:**

```text
Mở A
Mở B
Mở C
Trong try
Đóng C
Đóng B
Đóng A
Chính: Lỗi xử lý
Số phụ: 3
Phụ: Lỗi đóng C
Phụ: Lỗi đóng B
Phụ: Lỗi đóng A
```

Mỗi `Resource.close()` ném lỗi, nhưng Java vẫn cố đóng tất cả tài nguyên đã khởi tạo thành công. Lỗi của C, B, A được giữ lại thay vì thay thế primary từ thân `try`.

Điều này **không tự chứng minh cleanup thành công**. Việc `close()` đã được gọi không có nghĩa mỗi resource đã được giải phóng hoàn toàn. Suppressed exception chính là bằng chứng cần xem xét thêm trạng thái tài nguyên.

## 10. Nếu thân `try` thành công nhưng hai `close()` cùng thất bại

Đây là trường hợp khó hơn: không có lỗi chính từ thân `try`. Java đóng tài nguyên sau cùng trước, và **ngoại lệ đóng đầu tiên được ném sẽ là primary**. Những lỗi đóng tiếp theo sẽ được giữ làm suppressed vào primary đó.

Ví dụ đầy đủ **`CloseBecomesPrimaryDemo.java`**:

```java
public class CloseBecomesPrimaryDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
            throw new IllegalStateException("Lỗi " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            System.out.println("Work thành công");
        } catch (IllegalStateException e) {
            System.out.println("Primary: " + e.getMessage());
            for (Throwable secondary : e.getSuppressed()) {
                System.out.println("Suppressed: "
                        + secondary.getMessage());
            }
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 CloseBecomesPrimaryDemo.java
java CloseBecomesPrimaryDemo
```

**Stdout chính xác:**

```text
Work thành công
Đóng B
Đóng A
Primary: Lỗi B
Suppressed: Lỗi A
```

B được đóng trước A, nên lỗi B trở thành primary. Khi việc đóng A cũng lỗi, lỗi A trở thành suppressed của lỗi B.

**Không có quy tắc “resource khai báo đầu tiên luôn là primary”.** Primary phụ thuộc đường thực thi: có lỗi từ thân `try` hay không, thứ tự đóng và lỗi nào xuất hiện trước khi các resource đang được quản lý được dọn dẹp.

## 11. Khởi tạo tài nguyên thất bại: cleanup vẫn có thể tạo suppressed

Với nhiều resource, một constructor hoặc factory ở vị trí sau có thể ném exception trước khi thân `try` bắt đầu. Java vẫn cố đóng những resource **đã khởi tạo thành công trước đó**.

Giả sử:

- Resource A đã mở thành công.
- Đang tạo resource B thì constructor ném `IllegalArgumentException`.
- Java cố đóng A.
- `A.close()` ném `IllegalStateException`.

Lỗi khởi tạo B có thể là **primary**, còn lỗi đóng A là **suppressed** của lỗi khởi tạo B. Thân `try` không được thực thi.

Ví dụ Java 17 **`InitializationSuppressedDemo.java`**:

```java
public class InitializationSuppressedDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Khởi tạo " + name);
            if ("B".equals(name)) {
                throw new IllegalArgumentException("Không tạo được B");
            }
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
            throw new IllegalStateException("Không đóng được " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            System.out.println("Không chạy đến đây");
        } catch (IllegalArgumentException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Số suppressed: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 InitializationSuppressedDemo.java
java InitializationSuppressedDemo
```

**Stdout chính xác:**

```text
Khởi tạo A
Khởi tạo B
Đóng A
Primary: Không tạo được B
Số suppressed: 1
Suppressed: Không đóng được A
```

A được tạo thành công và do cấu trúc try-with-resources quản lý. B ném lỗi trong constructor, nên B **không được xem là resource khởi tạo thành công để Java gọi `close()`**, nhưng A vẫn được đóng.

Quy tắc này rất hữu ích cho những chuỗi mở file, stream, socket hoặc tài nguyên kết nối: lỗi ở tài nguyên sau không khiến các tài nguyên trước bị bỏ quên.

## 12. Tài nguyên `null` có tạo suppressed exception không?

Trong try-with-resources, resource specification có thể chứa tham chiếu `null`. Nếu resource là `null`, Java **không gọi `close()` trên tham chiếu đó**. Bởi vậy resource `null` không tự tạo lỗi đóng và không bổ sung suppressed exception chỉ vì bằng `null`.

Lưu **`NullResourceSuppressedDemo.java`**:

```java
public class NullResourceSuppressedDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("Lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (Resource missing = null) {
            throw new IllegalArgumentException("Lỗi trong try");
        } catch (IllegalArgumentException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed: "
                    + e.getSuppressed().length);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 NullResourceSuppressedDemo.java
java NullResourceSuppressedDemo
```

**Stdout chính xác:**

```text
Primary: Lỗi trong try
Suppressed: 0
```

Mặc dù `Resource.close()` được viết để luôn ném exception, method đó không được gọi vì biến tài nguyên `missing` là `null`. Ngoại lệ chính vẫn là `IllegalArgumentException` từ thân `try`, với danh sách suppressed rỗng.

Phân biệt: **đọc hoặc gọi method trên resource `null` ở thân `try` vẫn có thể ném `NullPointerException`**. Điều try-with-resources làm an toàn ở đây là bỏ qua *lời gọi close tự động* cho tham chiếu `null`, không phải làm mọi thao tác trên `null` trở nên hợp lệ.

## 13. `getCause()` khác `getSuppressed()` trong ứng dụng thực

Xét hệ thống:

- Thao tác mạng ném `IOException`.
- Khi đóng stream, `close()` cũng ném `IOException` khác; lỗi đóng bị suppressed vào lỗi mạng.
- Tầng dịch vụ bọc lỗi mạng bằng `ServiceException` có `cause` là exception mạng.

Khi caller nhận `ServiceException`:

```text
ServiceException                  (wrapper)
└── cause -> IOException A        (lỗi chính ở tầng I/O)
    └── suppressed -> IOException B   (lỗi đóng stream)
```

**Không phải** `ServiceException.getSuppressed()` sẽ tự chứa B. B nằm trên object A, được wrapper lưu làm cause.

Ví dụ **`CauseVsSuppressedDemo.java`**:

```java
import java.io.IOException;

public class CauseVsSuppressedDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    static void repository() throws IOException {
        try (Resource resource = new Resource()) {
            throw new IOException("read failed");
        }
    }

    static void service() {
        try {
            repository();
        } catch (IOException e) {
            throw new IllegalStateException("Service failed", e);
        }
    }

    public static void main(String[] args) {
        try {
            service();
        } catch (IllegalStateException wrapper) {
            Throwable cause = wrapper.getCause();
            System.out.println("Wrapper: " + wrapper.getMessage());
            System.out.println("Cause: " + cause.getMessage());
            System.out.println("Wrapper suppressed: "
                    + wrapper.getSuppressed().length);
            System.out.println("Cause suppressed: "
                    + cause.getSuppressed().length);
            System.out.println("Lỗi phụ: "
                    + cause.getSuppressed()[0].getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 CauseVsSuppressedDemo.java
java CauseVsSuppressedDemo
```

**Stdout chính xác:**

```text
Wrapper: Service failed
Cause: read failed
Wrapper suppressed: 0
Cause suppressed: 1
Lỗi phụ: close failed
```

Cần xem **cả chuỗi cause và danh sách suppressed tại những object liên quan**. Chỉ gọi `wrapper.getSuppressed()` rồi kết luận “không có lỗi cleanup” sẽ bỏ sót B trên object `cause`.

## 14. `printStackTrace()` hiển thị suppressed như thế nào?

`Throwable.printStackTrace()` in ra mô tả exception và các stack frame. Khi có suppressed exceptions, dạng trình bày thông thường gồm những dòng có nhãn `Suppressed:`; quan hệ cause được thể hiện riêng dưới nhãn `Caused by:`.

Ví dụ minh họa không yêu cầu đầu ra cố định:

```java
try {
    runOperation();
} catch (Exception e) {
    e.printStackTrace();
}
```

Đây là **mã khung**; `runOperation()` phải được định nghĩa. `printStackTrace()` không đối số in ra **standard error** (`System.err`), không phải stdout, và định dạng có thể phụ thuộc JVM, số dòng nguồn, cách build và vị trí code.

Một mô tả tương tự:

```text
java.io.IOException: read failed
    at ...
    Suppressed: java.io.IOException: close failed
        at ...
```

Đây chỉ là **mẫu trực quan**, không phải stack trace thực được kiểm chứng theo số dòng. Trong API logging hỗ trợ `Throwable`, truyền trực tiếp đối tượng exception thường giúp giữ thông tin stack trace, cause và suppressed tốt hơn chỉ log `e.getMessage()`.

**`getMessage()` và `toString()` mặc định không tự liệt kê suppressed exceptions.** Nếu log chỉ chuỗi ngắn mà không giữ đối tượng Throwable hoặc các quan hệ của nó, các lỗi cleanup có thể bị mất khỏi hệ thống quan sát.

## 15. `addSuppressed()` không phải `initCause()`

Hai cơ chế giải quyết hai loại quan hệ:

```java
Throwable primary = new IllegalStateException("Work failed");
Throwable secondary = new IllegalArgumentException("Cleanup failed");

primary.addSuppressed(secondary);
```

Điều đó không làm `primary.getCause()` trở thành `secondary`. Muốn thể hiện nguyên nhân của một wrapper có thể dùng constructor chứa cause:

```java
Throwable root = new IllegalArgumentException("Input failed");
Throwable wrapper =
        new IllegalStateException("Service failed", root);
```

Đây là **các đoạn minh họa độc lập**. Một Throwable có thể có cause và nhiều suppressed. Trong cơ chế try-with-resources, việc thêm suppressed phục vụ **lỗi phụ phát sinh khi đang thực hiện cleanup**, chứ không biểu diễn rằng lỗi cleanup là nguyên nhân tạo ra lỗi công việc trước đó.

**Không dùng `initCause()` để gắn mọi lỗi đóng vào một cause chain.** Điều đó làm mờ quan hệ thời gian và nghiệp vụ; với `try-with-resources` nên đọc danh sách suppressed được tạo sẵn.

## 16. Quy tắc tự thêm suppressed: không dùng `null` và không self-suppression

`addSuppressed` có quy tắc an toàn riêng để tránh dữ liệu sai:

```java
IllegalStateException e =
        new IllegalStateException("Primary");
e.addSuppressed(new IllegalArgumentException("Cleanup"));

// e.addSuppressed(null); // Ném NullPointerException.
// e.addSuppressed(e);    // Ném IllegalArgumentException.
```

Đây là **đoạn minh họa**. Lệnh thứ hai và thứ ba được comment để không phá ví dụ. Trong ứng dụng thực, một thuật toán tự động quản lý danh sách lỗi cần tránh **gắn chính primary vào primary**, đồng thời không truyền exception `null`.

`getSuppressed()` trả mảng mới; bạn có thể lọc, duyệt và in các phần tử như dữ liệu đọc. Đừng thay phần tử trên mảng kết quả rồi kỳ vọng thay đổi danh sách suppressed nội bộ.

Nếu tự triển khai cleanup phức tạp, hãy cân nhắc API `AutoCloseable`/`try-with-resources` để tránh phải tự quản lý hàng loạt quy tắc như thứ tự thêm lỗi, exception checked/unchecked và các trạng thái thất bại khi khởi tạo.

## 17. Có thể tắt suppression trong một lớp ngoại lệ tùy chỉnh

`Throwable` có constructor protected:

```java
protected Throwable(String message, Throwable cause,
                    boolean enableSuppression,
                    boolean writableStackTrace)
```

Tham số `enableSuppression` cho phép lớp con **tắt khả năng ghi suppressed exceptions**. Đây là trường hợp ít gặp trong ứng dụng nghiệp vụ nhưng quan trọng khi suy luận chính xác: `try-with-resources` vẫn cố gắn lỗi đóng, nhưng primary đã tắt suppression thì `getSuppressed()` có thể vẫn rỗng.

Ví dụ độc lập **`SuppressionDisabledDemo.java`**:

```java
public class SuppressionDisabledDemo {
    static class NoSuppressionException extends Exception {
        NoSuppressionException(String message) {
            super(message, null, false, true);
        }
    }

    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close bị lỗi");
            throw new IllegalStateException("cleanup failed");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            throw new NoSuppressionException("work failed");
        } catch (NoSuppressionException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed: "
                    + e.getSuppressed().length);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressionDisabledDemo.java
java SuppressionDisabledDemo
```

**Stdout chính xác:**

```text
close bị lỗi
Primary: work failed
Suppressed: 0
```

Dù `close()` ném `IllegalStateException`, object primary không lưu suppressed do `enableSuppression=false`. Điều đó **không làm `close()` thành công**, và không có nghĩa lỗi phụ chưa từng xảy ra.

Thực hành: đừng tắt suppression nếu ứng dụng cần chẩn đoán lỗi cleanup đồng thời. Cũng không nhầm `enableSuppression` với `writableStackTrace`; đó là hai tùy chọn khác nhau. Tắt writable stack trace ảnh hưởng các frame của exception, còn tắt suppression ảnh hưởng danh sách lỗi phụ.

## 18. Lưu ý về một lỗi rất hiếm: self-suppression khi đóng

Cơ chế suppression yêu cầu **primary và suppressed không phải cùng object**. `Throwable.addSuppressed(this)` không hợp lệ.

Đa số `close()` tạo exception mới hoặc ném một object độc lập, nhưng nếu ứng dụng cố tình **tái sử dụng đúng cùng exception instance** cả ở thân `try` và trong `close()`, thao tác liên kết suppressed có thể gặp lỗi self-suppression. Đây là một lý do nữa để **không tái sử dụng exception object toàn cục** cho các tình huống độc lập.

Trong thiết kế thông thường:

- Mỗi thất bại nên có Throwable riêng, trừ trường hợp có lý do và hợp đồng rất rõ.
- Để Java giữ primary/suppressed tự động.
- Nếu tự dùng `addSuppressed`, kiểm tra không tự thêm chính object đó và không truyền `null`.
- Khi gặp dấu vết khó hiểu từ một cơ chế đặc biệt, đừng vội giả định rằng try-with-resources đã ghi mọi lỗi được mong đợi; kiểm tra cả cách thư viện tạo và tái sử dụng exception.

Không cần triển khai self-suppression bằng ví dụ mặc định vì đây không phải cách sử dụng hợp lệ của API; hiểu giới hạn này là đủ để tránh một nhóm bug hiếm nhưng khó chẩn đoán.

## 19. Cleanup thủ công bằng `finally` có nguy cơ che lỗi chính

So sánh hai cấu trúc:

**Cleanup thủ công có thể làm mất primary:**

```java
try {
    doWork();
} finally {
    resource.close(); // Nếu cũng ném lỗi, có thể che lỗi do doWork().
}
```

**Try-with-resources giữ lỗi phụ có cấu trúc:**

```java
try (Resource resource = openResource()) {
    doWork();
}
```

Cả hai đều là **mã khung minh họa**, cần định nghĩa `Resource` và các phương thức. `finally` không tự có quy tắc ghi suppressed vào primary nếu cleanup ném exception mới. Nếu nguyên nhân lỗi chính phải được giữ, một mã `finally` ngây thơ là rủi ro.

Nếu một API cũ không triển khai `AutoCloseable`, có thể **viết adapter implements AutoCloseable** thay vì tự xử lý mọi tình huống primary/suppressed. Điều đó vẫn phải bảo đảm adapter thực sự sở hữu tài nguyên, đóng đúng đối tượng và không đóng nhầm resource dùng chung.

## 20. Ví dụ adapter cho API cũ

Một thư viện cũ có thể chỉ có phương thức `release()`:

```java
class LegacySession {
    void work() {
        throw new IllegalArgumentException("work failed");
    }

    void release() {
        throw new IllegalStateException("release failed");
    }
}

class SessionAdapter implements AutoCloseable {
    private final LegacySession session;

    SessionAdapter(LegacySession session) {
        this.session = session;
    }

    void work() {
        session.work();
    }

    @Override
    public void close() {
        session.release();
    }
}
```

Đây là **hai lớp minh họa**. Sử dụng chúng trong một phương thức:

```java
try (SessionAdapter session =
             new SessionAdapter(new LegacySession())) {
    session.work();
}
```

Khi `work()` ném `IllegalArgumentException` và `release()` ném `IllegalStateException`, try-with-resources giữ lỗi công việc là primary và gắn lỗi release suppressed (với mặc định suppression đang bật).

Đừng cố “sửa” lỗi bằng cách bắt rồi bỏ qua mọi `Exception` trong `close()`: làm vậy có thể mất dấu một sự cố cleanup thật sự. Nếu một lỗi đóng có thể được xem là vô hại theo hợp đồng, chính adapter hoặc tầng cao hơn phải quyết định dựa trên **loại lỗi cụ thể**, không bỏ qua tất cả.

## 21. Checked exception từ `AutoCloseable.close()` ảnh hưởng chữ ký phương thức

`AutoCloseable.close()` có khai báo:

```java
void close() throws Exception;
```

Đây là **chữ ký API**. Vì vậy khi kiểu tĩnh của resource là `AutoCloseable`, compiler có thể yêu cầu handler hoặc mệnh đề `throws Exception` cho việc đóng.

Ví dụ:

```java
static void run(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("Trong try");
    }
}
```

Đây là **phương thức minh họa**, dùng cú pháp Java 9+ đối với biến tham số effectively final. `throws Exception` đến từ khả năng `close()` ném checked `Exception`, ngay cả khi thân `try` không ném lỗi.

Nếu kiểu tĩnh là một lớp con override `close()` không khai báo checked exception, nghĩa vụ `throws` có thể hẹp hơn hoặc biến mất. Nếu là `java.io.Closeable`, phương thức `close()` khai báo `throws IOException`, hẹp hơn hợp đồng chung của `AutoCloseable`.

Mối liên quan với suppressed: **khi close ném checked exception**, nó có thể là primary hoặc suppressed tùy đường thực thi. Dù đang xét trường hợp suppressed, chữ ký của tài nguyên vẫn được compiler phân tích; không được bỏ qua xử lý checked exception chỉ vì bạn nghĩ lỗi đóng sẽ luôn được suppress.

## 22. Resource đóng thứ tự nào và có dừng khi một `close()` lỗi không?

Câu trả lời là: **đóng theo thứ tự ngược khai báo và vẫn cố đóng các tài nguyên còn lại đã khởi tạo thành công**.

Ví dụ ba resource A, B, C:

```text
Khởi tạo: A -> B -> C
Thân try chạy
Đóng:      C -> B -> A
```

Nếu `C.close()` ném, Java không tự bỏ qua B và A. Nó vẫn cố đóng B và A; nếu chúng cũng ném, các lỗi được giữ theo primary/suppressed như đã phân tích.

Đây là khác biệt lớn so với code tự viết như:

```java
finally {
    c.close();
    b.close();
    a.close();
}
```

Đây là **mã khung phản ví dụ**. Nếu `c.close()` ném, `b.close()` và `a.close()` có thể không bao giờ chạy, trừ khi lập trình viên tự thêm handler riêng. Try-with-resources sinh cơ chế dọn dẹp phù hợp để hạn chế tình trạng đó.

**Lưu ý:** nếu một resource bằng `null`, Java bỏ qua lời gọi close trên nó, nhưng vẫn xử lý các resource khác theo thứ tự. Nếu khởi tạo tài nguyên sau thất bại, chỉ những resource **đã khởi tạo thành công trước đó** được cố đóng.

## 23. Khi nào `getSuppressed()` rỗng?

Có nhiều lý do hợp lệ:

1. Thân `try` không có lỗi và các `close()` đều thành công.
2. Thân `try` ném lỗi, nhưng `close()` không ném lỗi nào.
3. Thân `try` ném lỗi và resource là `null` nên không có close tự động trên resource đó.
4. Thân `try` thành công, chỉ một `close()` ném: exception đóng là **primary**, không phải suppressed.
5. Một Throwable tùy chỉnh đã **tắt suppression**.
6. Một exception được tạo bên ngoài try-with-resources mà chưa có ai gọi `addSuppressed`.
7. Bạn đang kiểm tra **wrapper** trong khi các suppressed nằm trên **cause** của wrapper.

Vì vậy, **`getSuppressed().length == 0` không đủ để chứng minh không có lỗi cleanup ở toàn bộ tác vụ**. Cần biết object đang kiểm tra, đường gọi thực tế, loại resource và việc có wrapper/cause hay không.

Đây cũng là lý do nên log lỗi dưới dạng Throwable có cấu trúc, thay vì chỉ in một message rồi vứt bỏ object.

## 24. Kiểm thử lỗi đồng thời bằng Java 17

Một bài kiểm thử tốt không chỉ xác nhận “đã ném exception”; nó cần xác nhận **ngoại lệ chính đúng loại** và **lỗi cleanup đã được giữ lại**, không làm đổi primary.

Ví dụ **`SuppressedAssertionsDemo.java`**:

```java
public class SuppressedAssertionsDemo {
    static class FailingResource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("cleanup");
        }
    }

    static void run() {
        try (FailingResource resource = new FailingResource()) {
            throw new IllegalArgumentException("work");
        }
    }

    public static void main(String[] args) {
        try {
            run();
            throw new AssertionError("Không có lỗi chính");
        } catch (IllegalArgumentException e) {
            if (!"work".equals(e.getMessage())) {
                throw new AssertionError("Sai primary");
            }

            Throwable[] others = e.getSuppressed();
            if (others.length != 1
                    || !(others[0] instanceof IllegalStateException)
                    || !"cleanup".equals(others[0].getMessage())) {
                throw new AssertionError("Sai suppressed");
            }

            System.out.println("PASS: primary và suppressed chính xác");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedAssertionsDemo.java
java SuppressedAssertionsDemo
```

**Stdout chính xác:**

```text
PASS: primary và suppressed chính xác
```

Ví dụ chủ động dùng `throw new AssertionError(...)` để test thất bại, không phụ thuộc tùy chọn `-ea` của từ khóa `assert`. Trên đường đúng, có một suppressed exception kiểu `IllegalStateException` với message `"cleanup"`.

Một bộ test đầy đủ còn nên kiểm tra khi **chỉ close thất bại**, **nhiều close thất bại**, **khởi tạo resource thất bại**, và **resource bằng null**, vì những đường này có primary/suppressed khác nhau.

## 25. Những lỗi hiểu sai thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Suppressed là ngoại lệ đã bị xử lý hết và không cần quan tâm.” | Nó vẫn là một lỗi cần chẩn đoán; suppressed chỉ giữ quan hệ lỗi phụ so với primary. |
| “`close()` ném lỗi thì lỗi đó luôn là suppressed.” | Nếu thân `try` thành công, lỗi close đầu tiên có thể là primary. |
| “Có một tài nguyên thì chỉ có một lỗi cần xử lý.” | Thân `try` và `close()` có thể ném hai exception khác nhau. |
| “Khi nhiều close cùng thất bại, chỉ tài nguyên đóng cuối được giữ.” | Try-with-resources cố đóng các resource còn lại và có thể giữ các lỗi đóng dưới dạng suppressed. |
| “Suppressed chính là `getCause()`.” | Không; `getCause()` và `getSuppressed()` mô tả các quan hệ khác nhau. |
| “Wrapper tự sao chép suppressed của cause.” | Không tự động. Suppressed thường vẫn nằm trên object gốc là cause. |
| “`getSuppressed()` trả `null` khi không có lỗi.” | Nó trả **mảng rỗng**. |
| “Chỉ `try-with-resources` mới dùng được `addSuppressed()`.” | Không; `addSuppressed()` là API public của `Throwable`, nhưng Java tự dùng trong try-with-resources khi phù hợp. |
| “Gọi `e.addSuppressed(e)` là hợp lệ.” | Không. Java cấm self-suppression. |
| “`addSuppressed(null)` chỉ bị bỏ qua.” | Không; tham số `null` không hợp lệ. |
| “Nếu `getSuppressed()` rỗng thì chắc chắn không từng có lỗi close.” | Không nhất thiết: có thể close là primary, suppression tắt, hoặc lỗi nằm trên cause. |
| “`finally` và try-with-resources tự quản lý lỗi giống nhau.” | Không. `finally` ném lỗi mới có thể che primary; try-with-resources có cơ chế suppressed. |
| “Tất cả exception đều hỗ trợ ghi suppressed.” | Lớp con có thể tắt suppression bằng constructor thích hợp. |
| “`printStackTrace()` chỉ hiện message primary.” | Thông thường nó còn hiển thị frames, cause và suppressed khi có. |
| “Mọi lời gọi close đều thành công vì Java tự gọi.” | Auto-close nghĩa là Java **cố gọi**, không bảo đảm cleanup thành công. |
| “Checked exception từ close không cần xét vì sẽ luôn bị suppressed.” | Không; close có thể là primary và compiler vẫn kiểm tra hợp đồng checked exception. |

## 26. Câu hỏi tự kiểm tra

**Câu 1.** Thân `try-with-resources` ném A, `close()` ném B. Ngoại lệ chính là gì?  
**Đáp án:** Thông thường A. B được gắn suppressed vào A nếu suppression đang bật và không vi phạm quy tắc thêm suppressed.

**Câu 2.** Thân `try` chạy thành công, `close()` ném B. B nằm ở đâu?  
**Đáp án:** B là exception chính của cấu trúc; không có A trước đó để gắn suppressed.

**Câu 3.** Tài nguyên A khai báo trước B, cả hai `close()` lỗi và thân `try` không lỗi. Ngoại lệ nào là primary?  
**Đáp án:** Lỗi từ `B.close()`, vì B đóng trước; lỗi từ `A.close()` thường trở thành suppressed của lỗi B.

**Câu 4.** Thân `try` ném A, resource C đóng trước B rồi A-resource. Nếu cả ba close cùng lỗi, thứ tự suppressed theo thứ tự nào?  
**Đáp án:** Theo thứ tự lỗi phát sinh khi đóng: C, B, A-resource.

**Câu 5.** Factory resource thứ hai ném exception trong khi resource thứ nhất đã mở. Resource thứ nhất có được đóng không?  
**Đáp án:** Java cố đóng resource đã khởi tạo thành công trước đó; lỗi đóng có thể suppressed vào lỗi khởi tạo.

**Câu 6.** `getSuppressed()` trả về gì nếu không có suppressed?  
**Đáp án:** Một mảng `Throwable[]` rỗng.

**Câu 7.** Có thể gọi `primary.addSuppressed(primary)` để đánh dấu lỗi lặp không?  
**Đáp án:** Không. Self-suppression bị cấm và ném `IllegalArgumentException`.

**Câu 8.** Một wrapper có `getCause()` là exception A, còn B suppressed trên A. `wrapper.getSuppressed()` có tự chứa B không?  
**Đáp án:** Không. Cần kiểm tra `wrapper.getCause().getSuppressed()` đối với trường hợp đó.

**Câu 9.** `printStackTrace()` khác `getMessage()` như thế nào?  
**Đáp án:** `getMessage()` chỉ trả thông điệp; `printStackTrace()` in mô tả lỗi, stack frames và có thể gồm cause/suppressed, theo cách trình bày của runtime.

**Câu 10.** Nếu suppression bị tắt trên primary, `getSuppressed().length` có thể là bao nhiêu dù close lỗi?  
**Đáp án:** Có thể bằng 0, vì object không lưu suppressed được thêm như bình thường.

**Câu 11.** `finally { resource.close(); }` có thể che lỗi chính như thế nào?  
**Đáp án:** Nếu thân `try` đã ném A và `close()` trong `finally` ném B, `finally` kết thúc bất thường bằng B và B có thể thay A là exception được ném ra.

**Câu 12.** Resource `null` có tự ném lỗi đóng không?  
**Đáp án:** Không. Try-with-resources bỏ qua lời gọi `close()` tự động cho tham chiếu `null`.

**Câu 13.** Vì sao cần test cả trường hợp lỗi công việc và lỗi cleanup đồng thời?  
**Đáp án:** Để bảo đảm không làm mất primary và suppressed được giữ đúng loại, đúng thứ tự, thay vì chỉ xác nhận có một exception được ném.

**Câu 14.** Suppressed có giống kết quả rollback hoặc phục hồi tài nguyên không?  
**Đáp án:** Không. Nó chỉ bảo toàn thông tin lỗi; rollback/cleanup thành công cần cơ chế riêng.

## 27. Checklist thực hành

Khi viết hoặc review mã xử lý tài nguyên:

1. **Tài nguyên có `AutoCloseable` không?** Nếu có, ưu tiên `try-with-resources`.
2. **Thân `try` có thể ném lỗi gì?** Nhận diện primary có thể xuất hiện.
3. **`close()` có thể ném lỗi gì?** Xem hợp đồng checked/unchecked và ảnh hưởng dữ liệu.
4. **Nếu cả hai lỗi đồng thời, primary có được giữ không?** Kiểm tra `getSuppressed()` thay vì chỉ message.
5. **Nếu chỉ close lỗi, chương trình có coi đó là lỗi chính không?**
6. **Nếu nhiều resource, thứ tự khởi tạo trái→phải, đóng phải→trái có được hiểu đúng không?**
7. **Nếu một constructor/factory thất bại, những resource trước đó đã mở có được cố đóng không?**
8. **Nếu resource bằng `null`, cleanup có bỏ qua nó và vẫn đóng những resource khác không?**
9. **Có exception wrapper không?** Theo `getCause()` đến primary gốc và kiểm tra suppressed tại đúng object.
10. **Suppression có bị tắt ở custom exception không?** Nếu có, tác động chẩn đoán đã được hiểu chưa?
11. **Có mã tự gọi `addSuppressed` không?** Kiểm tra không truyền `null` hoặc chính primary.
12. **Có `finally` nào ném exception mới che lỗi chính không?**
13. **Logging có ghi object Throwable, stack trace, cause và suppressed khi cần không?**
14. **Kiểm thử đã có trường hợp simultaneous work + cleanup failures chưa?**
15. **Đã xác định chính sách sau lỗi cleanup chưa?** Dữ liệu có thể cần rollback, kết nối có thể hỏng, hoặc tác vụ không được báo thành công.

## 28. Ghi nhớ quan trọng

**Suppressed exception là một Throwable phụ được gắn vào exception chính qua `addSuppressed()` và được đọc bằng `getSuppressed()`.** Nó không phải cause, không tự làm lỗi biến mất, và không tự phục hồi tác vụ.

**`try-with-resources` là cơ chế tạo suppressed phổ biến nhất**. Khi công việc chính ném A và `close()` ném B, Java thường giữ A làm primary và đính B dưới dạng suppressed. Nếu công việc chính không ném lỗi, lỗi close đầu tiên có thể trở thành primary; với nhiều resource, những lỗi close tiếp theo được đính suppressed theo thứ tự đóng ngược khai báo.

Khi chẩn đoán, hãy xem **đúng object**: wrapper có thể chứa nguyên nhân A qua `getCause()`, trong khi suppressed B nằm trên A. Khi thiết kế, ưu tiên quản lý tài nguyên bằng `try-with-resources`, kiểm tra cả đường thất bại kép, giữ nguyên nhân gốc và không coi việc đã gọi `close()` là bảo đảm cleanup thành công.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-005">Phân biệt cause với suppressed exception</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `cause` với `suppressed exception` trong Java

> **Chủ đề:** Java Core → Exception → Nguyên nhân và lỗi phụ. Một đối tượng `Throwable` có thể mang **nguyên nhân** qua `getCause()` và đồng thời mang **những ngoại lệ bị ghi nhận kèm** qua `getSuppressed()`. Hai cơ chế độc lập này phục vụ các câu hỏi khác nhau: **cause** trả lời “lỗi nào dẫn đến việc tạo ra lỗi hiện tại?”, còn **suppressed** trả lời “có những lỗi phụ nào xảy ra trong khi một lỗi chính đã được giữ lại?”. Trong Java, try-with-resources từ Java 7 là trường hợp điển hình tự tạo mối quan hệ suppressed khi thân `try` và `close()` cùng thất bại.

## 1. Nhìn nhanh: hai mối quan hệ khác nhau

Giả sử dịch vụ xử lý yêu cầu gặp lỗi đọc dữ liệu `IOException`, chuyển nó thành `IllegalStateException` có ngữ cảnh nghiệp vụ và đồng thời việc dọn dẹp một tài nguyên khác cũng thất bại.

```text
IllegalStateException: Không hoàn tất yêu cầu
├── cause: IOException: Không đọc được nguồn
└── suppressed: IOException: Không đóng được tài nguyên
```

Ở đây:

- `IllegalStateException` là **ngoại lệ hiện tại** mà caller có thể nhận được.
- `getCause()` trỏ đến `IOException("Không đọc được nguồn")`: nguyên nhân đã được gắn khi tạo/khởi tạo wrapper.
- `getSuppressed()` trả mảng chứa `IOException("Không đóng được tài nguyên")`: lỗi phụ được ghi lại, không thay thế ngoại lệ hiện tại.
- Caller không tự động có hai nhánh `catch` cho cause và suppressed. **Handler `catch` chọn theo kiểu thực tế của ngoại lệ đang được ném**, không tự bóc `cause` hay lặp qua `suppressed`.

Một `Throwable` có thể chỉ có cause, chỉ có suppressed, có cả hai hoặc không có mối quan hệ nào.

### 1.1. Bảng so sánh cốt lõi

| Tiêu chí | `cause` | `suppressed exception` |
| --- | --- | --- |
| Câu hỏi trả lời | “Lỗi nào gây ra lỗi này?” | “Lỗi nào khác cần ghi nhận mà không thay thế lỗi chính?” |
| API để đọc | `Throwable.getCause()` | `Throwable.getSuppressed()` |
| Kiểu trả về | `Throwable` hoặc `null` | `Throwable[]`, có thể rỗng |
| Cách gắn | Constructor nhận `cause` hoặc `initCause(...)` | `addSuppressed(...)` hoặc tự động bởi try-with-resources |
| Số lượng trực tiếp | Tối đa một cause trực tiếp | Có thể có nhiều suppressed trực tiếp |
| Quan hệ thường thấy | Lỗi hạ tầng được bọc thành lỗi tầng cao | Lỗi đóng tài nguyên khi đã có lỗi chính |
| Có thay kiểu exception đang ném không? | Không | Không |
| Tự tham gia chọn `catch` không? | Không | Không |
| Cấu trúc | Một chuỗi liên kết nguyên nhân (cause chain) | Một danh sách gắn vào mỗi Throwable; phần tử có thể có cause/suppressed riêng |
| Tự động có mọi lúc không? | Không; phải gắn/cấu trúc bởi API | Không; try-with-resources tự gắn trong điều kiện phù hợp |

**Cách nhớ:** `cause` biểu đạt **quan hệ nguyên nhân**; `suppressed` biểu đạt **những thất bại được giữ lại bên cạnh lỗi chính**. Không nên gọi một exception trong `getSuppressed()` là “cause thứ hai” vì đó là quan hệ dữ liệu khác.

## 2. Bối cảnh: `Throwable` lưu thông tin nào?

Trong hệ thống Java, `java.lang.Throwable` là lớp gốc của `Exception` và `Error`. Nó cung cấp các API hữu ích:

- `getMessage()`: thông điệp mô tả lỗi.
- `getCause()`: đối tượng gây ra lỗi này nếu được thiết lập.
- `getSuppressed()`: mảng các exception bị ghi nhận phụ.
- `getStackTrace()`: các `StackTraceElement` thuộc trace của chính Throwable.
- `printStackTrace()`: in báo cáo lỗi cho người đọc, có thể gồm cause và suppressed.
- `initCause(Throwable cause)`: thiết lập nguyên nhân trong những trường hợp API cho phép.
- `addSuppressed(Throwable exception)`: gắn một ngoại lệ phụ.

Những thông tin trên **không phải một cấu trúc phẳng**. Mỗi `Throwable` có message và stack trace của riêng mình; `cause` trỏ tới Throwable khác; mảng suppressed có thể chứa nhiều Throwable, và mỗi phần tử lại có stack, cause hoặc suppressed riêng.

### 2.1. Cause không phải một `StackTraceElement`

`getCause()` trả **một đối tượng Throwable**, không phải frame trong stack. Hai ngoại lệ wrapper và cause thường có hai trace riêng: trace wrapper phản ánh vị trí tạo wrapper, trace cause phản ánh vị trí được ghi nhận của lỗi gốc.

Tương tự, `getSuppressed()` trả **các đối tượng Throwable**, không phải stack frame hay thông điệp chuỗi. Khi cần phân tích chi tiết, duyệt đối tượng và xem message, type, stack theo mục đích.

## 3. `cause`: nguyên nhân dẫn đến exception hiện tại

Tình huống phổ biến: API tầng đọc file ném `IOException`, còn tầng dịch vụ muốn thông báo lỗi có ý nghĩa hơn đối với caller. Thay vì làm mất exception gốc, tầng dịch vụ tạo wrapper:

```java
try {
    readSettings();
} catch (java.io.IOException original) {
    throw new IllegalStateException(
            "Không tải được cấu hình", original);
}
```

Đây là mảnh code minh họa; `readSettings()` phải được định nghĩa. Ở đây:

1. `original` là đối tượng `IOException` đã bắt.
2. `new IllegalStateException("...", original)` tạo **đối tượng mới** và gắn cause là `original`.
3. `throw` ném **wrapper**, không trực tiếp ném lại `original`.
4. Caller có thể bắt `IllegalStateException` hoặc kiểu cha, sau đó dùng `getCause()` để truy nguyên.

**Ý nghĩa:** nguyên nhân lưu dấu failure thấp hơn đã được chuyển thành ngữ cảnh cao hơn, không phải một lỗi “thứ hai phát sinh song song”.

### 3.1. Java 17 — ví dụ chạy được về cause và wrapper

Tạo file **`CauseWrapperDemo.java`**:

```java
import java.io.IOException;

public class CauseWrapperDemo {
    static void loadFile() throws IOException {
        throw new IOException("Nguồn không đọc được");
    }

    static void loadService() {
        try {
            loadFile();
        } catch (IOException original) {
            throw new IllegalStateException(
                    "Không tải được dịch vụ", original);
        }
    }

    public static void main(String[] args) {
        try {
            loadService();
        } catch (IllegalStateException e) {
            System.out.println("Đang bắt: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("Cause: "
                    + e.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + e.getCause().getMessage());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
        }
    }
}
```

Biên dịch và chạy với JDK 17:

```bash
javac -encoding UTF-8 CauseWrapperDemo.java
java CauseWrapperDemo
```

Stdout chính xác:

```text
Đang bắt: IllegalStateException
Message: Không tải được dịch vụ
Cause: IOException
Cause message: Nguồn không đọc được
Suppressed count: 0
```

Chương trình không tạo lỗi phụ nào, vì vậy mảng suppressed của wrapper rỗng. `getCause()` trả đúng đối tượng được truyền cho constructor wrapper, giữ nguyên loại và message từ tầng đọc file.

### 3.2. Chọn kiểu wrapper có chủ đích

`IllegalStateException` chỉ là lựa chọn ví dụ. Trong API thật có thể dùng exception tùy biến, ví dụ `ConfigurationLoadException`, để biểu đạt hợp đồng. Không tự động bọc mọi `IOException` thành `RuntimeException` chỉ để “né” checked exception. Hãy cân nhắc ai có khả năng xử lý lỗi, phạm vi API và nguyên nhân nào cần caller thấy.

Cấu trúc wrapper hợp lý thường là:

```java
class ConfigurationLoadException extends Exception {
    ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **khai báo lớp hợp lệ**, không có `main`. Caller của method ném loại checked này cần bắt hoặc khai báo theo quy tắc Java.

## 4. `initCause()` và giới hạn của quan hệ nguyên nhân

Ngoài constructor nhận cause, `Throwable` có phương thức:

```java
Throwable initCause(Throwable cause)
```

Phương thức cho phép thiết lập cause **một lần** trong trường hợp đối tượng chưa được khởi tạo cause. Đây là API cần thiết khi sử dụng những lớp exception/constructor không nhận cause.

### 4.1. Java 17 — thiết lập cause đúng một lần

Tạo file **`InitCauseDemo.java`**:

```java
import java.io.IOException;

public class InitCauseDemo {
    public static void main(String[] args) {
        IOException root = new IOException("Lỗi đọc");
        IllegalArgumentException wrapper =
                new IllegalArgumentException("Không hợp lệ");

        wrapper.initCause(root);
        System.out.println("Cause: "
                + wrapper.getCause().getClass().getSimpleName());

        try {
            wrapper.initCause(new IOException("Lỗi mới"));
        } catch (IllegalStateException e) {
            System.out.println("Gắn lần hai: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 InitCauseDemo.java
java InitCauseDemo
```

Stdout:

```text
Cause: IOException
Gắn lần hai: IllegalStateException
```

Sau lần gọi đầu, cause đã được khởi tạo. Việc gọi `initCause(...)` lần hai bị từ chối, không âm thầm thay thế nguyên nhân cũ.

### 4.2. Khi nào không gọi được `initCause()`?

- Nếu constructor đã khởi tạo cause (kể cả một constructor nhận `null` nhưng đã đánh dấu trạng thái cause được khởi tạo), `initCause(...)` có thể ném `IllegalStateException`.
- Không thể dùng chính Throwable làm cause của nó: `e.initCause(e)` không hợp lệ, gây `IllegalArgumentException`.
- Nên ưu tiên constructor nhận cause nếu API cung cấp; không tạo wrapper rồi quên gắn nguyên nhân.
- Chỉ dùng `initCause()` khi có lý do tương thích hoặc thiết kế API cụ thể; đừng coi nó là setter tự do thay đổi lịch sử lỗi.

**Chú ý khác biệt:** `getCause() == null` không tự chứng minh rằng `initCause(...)` vẫn được phép. Một exception có thể đã được khởi tạo cause bằng `null` nên API không cho thay đổi nữa.

## 5. `suppressed exception`: ngoại lệ phụ được ghi nhận mà không thay thế lỗi chính

Một Throwable có thể nhận nhiều ngoại lệ phụ:

```java
Throwable primary = new IllegalStateException("Lỗi chính");
primary.addSuppressed(new java.io.IOException("Lỗi đóng A"));
primary.addSuppressed(new java.io.IOException("Lỗi đóng B"));
```

Đây là mảnh code minh họa trong method. Nếu duyệt `primary.getSuppressed()`, ta thấy hai đối tượng `IOException`. Nhưng:

- `primary.getCause()` vẫn có thể là `null`.
- `getSuppressed()[0]` không tự trở thành cause.
- `addSuppressed(...)` **không ném** exception phụ theo luồng điều khiển Java. Nó chỉ lưu thông tin vào đối tượng chính.
- Handler `catch` đang chạy không tự chuyển sang xử lý suppressed exception như một exception mới.
- Các đối tượng phụ giữ message và stack trace riêng, phục vụ phân tích nếu cần.

### 5.1. Vì sao cần suppressed?

Trong một thao tác có hai giai đoạn, lỗi gốc xảy ra trong lúc **làm việc**, sau đó một lỗi khác xảy ra trong lúc **dọn dẹp**. Nếu code chỉ giữ lỗi cuối, có thể mất thông tin quan trọng nhất về thất bại ban đầu.

Ví dụ:

```text
Đọc dữ liệu thất bại         → IOException("read failed")   [primary]
Đóng luồng cũng thất bại      → IOException("close failed")  [suppressed]
```

Java 7 đưa cơ chế suppressed vào `Throwable` và dùng nó trong try-with-resources. Nó cho phép bảo toàn lỗi làm việc **và** không quên lỗi đóng, mà không nhất thiết ghi đè lỗi thứ nhất bằng lỗi thứ hai.

### 5.2. Java 17 — thao tác `addSuppressed` trực tiếp

Tạo file **`ManualSuppressedDemo.java`**:

```java
import java.io.IOException;

public class ManualSuppressedDemo {
    public static void main(String[] args) {
        IllegalStateException primary =
                new IllegalStateException("Xử lý thất bại");

        primary.addSuppressed(new IOException("Đóng A thất bại"));
        primary.addSuppressed(new IOException("Đóng B thất bại"));

        System.out.println("Primary: " + primary.getMessage());
        System.out.println("Cause: " + primary.getCause());
        System.out.println("Suppressed count: "
                + primary.getSuppressed().length);

        for (Throwable e : primary.getSuppressed()) {
            System.out.println("Suppressed: " + e.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ManualSuppressedDemo.java
java ManualSuppressedDemo
```

Stdout:

```text
Primary: Xử lý thất bại
Cause: null
Suppressed count: 2
Suppressed: Đóng A thất bại
Suppressed: Đóng B thất bại
```

`getCause()` trả `null` vì code không thiết lập cause; hai lỗi phụ được giữ riêng. Chương trình này không ném `primary` — ta chỉ tạo đối tượng và ghi thông tin để quan sát API. Trong thực tế thường một Throwable được ném/bắt rồi ghi suppressed theo yêu cầu xử lý.

## 6. Try-with-resources: nơi suppressed phát sinh tự động

Đây là tình huống phải hiểu rõ nhất.

```java
try (MyResource resource = open()) {
    perform(resource);
}
```

Đây là mã mẫu; `MyResource`/`open`/`perform` chưa được định nghĩa.

Nếu `perform(...)` ném một Throwable **P**, Java vẫn gọi `resource.close()`. Nếu `close()` cũng ném Throwable **C**, TWR ưu tiên giữ **P làm primary** và gắn **C vào danh sách suppressed của P**. Do đó:

- `catch` ở tầng ngoài nhận đối tượng P đang lan truyền.
- `P.getSuppressed()` chứa C nếu việc gắn suppressed diễn ra theo cơ chế bình thường.
- `P.getCause()` không tự đổi thành C.
- Lỗi C không kích hoạt riêng `catch` dựa trên kiểu của C, vì C không trở thành exception chính đang bay ra.

Nếu thân thành công nhưng `close()` thất bại, lỗi đóng có thể trở thành **primary** vì trước đó không có Throwable từ thân để ưu tiên.

### 6.1. Java 17 — thân và close đều lỗi

Tạo file **`TwrBodyAndCloseDemo.java`**:

```java
import java.io.IOException;

public class TwrBodyAndCloseDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đóng tài nguyên");
            throw new IOException("Không đóng được");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Xử lý dữ liệu");
            throw new IllegalStateException("Dữ liệu không hợp lệ");
        } catch (Exception e) {
            System.out.println("Primary type: "
                    + e.getClass().getSimpleName());
            System.out.println("Primary message: " + e.getMessage());
            System.out.println("Cause: " + e.getCause());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
            System.out.println("Suppressed type: "
                    + e.getSuppressed()[0].getClass().getSimpleName());
            System.out.println("Suppressed message: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy Java 17:

```bash
javac -encoding UTF-8 TwrBodyAndCloseDemo.java
java TwrBodyAndCloseDemo
```

Stdout chính xác:

```text
Xử lý dữ liệu
Đóng tài nguyên
Primary type: IllegalStateException
Primary message: Dữ liệu không hợp lệ
Cause: null
Suppressed count: 1
Suppressed type: IOException
Suppressed message: Không đóng được
```

Đây là ví dụ **không có cause**, nhưng có **một suppressed exception**. Nó chứng minh vì sao “lỗi nào ném trước thì là cause, lỗi sau là suppressed” không phải định nghĩa chính xác: lỗi thân chính không được bọc, lỗi đóng là phụ; **không có chuỗi cause nào được thiết lập**.

### 6.2. Quy tắc quan trọng của handler `catch`

Trong ví dụ, handler `catch (Exception e)` nhận `IllegalStateException`, vì đó là đối tượng primary đang được ném. Việc suppressed là `IOException` **không** khiến chương trình tự gọi `catch (IOException)`.

Nếu viết các handler riêng, `catch (IllegalStateException)` nhận lỗi thân; `catch (IOException)` có thể nhận lỗi đóng nếu lỗi đóng trở thành primary **trong tình huống thân thành công**. Trong một source file thực, compiler còn xét các nguồn checked exception từ `close()`.

## 7. Nếu thân thành công nhưng đóng thất bại: close trở thành primary

Tạo **`TwrClosePrimaryDemo.java`**:

```java
import java.io.IOException;

public class TwrClosePrimaryDemo {
    static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            System.out.println("Đang đóng");
            throw new IOException("Lỗi đóng");
        }
    }

    public static void main(String[] args) {
        try (FailingClose r = new FailingClose()) {
            System.out.println("Thân thành công");
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed count: "
                    + e.getSuppressed().length);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwrClosePrimaryDemo.java
java TwrClosePrimaryDemo
```

Stdout:

```text
Thân thành công
Đang đóng
Primary: Lỗi đóng
Suppressed count: 0
```

**Giải thích:** không có lỗi từ thân `try`, nên `IOException("Lỗi đóng")` là exception chính thoát khỏi TWR. Nó không thể “bị suppressed dưới một primary của thân” vì primary như vậy không tồn tại.

Điều này quan trọng với writer, buffered output, transaction-adjacent resources và các API mà `close()` có thể thực hiện công việc cuối cùng. “Đã in dòng thành công trong thân try” không bảo đảm operation đã thành công đến cuối vòng đời resource.

## 8. Nhiều tài nguyên: suppressed có thể là một danh sách

Java khởi tạo tài nguyên trong `try (...)` theo thứ tự khai báo (trái sang phải), đóng **ngược thứ tự khai báo** (phải sang trái).

Với hai resource A và B:

```text
Khởi tạo A → Khởi tạo B → Thân try
                               ↓
                            Đóng B
                               ↓
                            Đóng A
                               ↓
                        catch ở bên ngoài
```

Nếu thân đã ném primary P, cả B và A đóng đều lỗi, P có thể có hai suppressed, thường theo thứ tự đóng B rồi A.

### 8.1. Java 17 — hai lỗi đóng phụ bên cạnh lỗi thân

Tạo **`TwrMultipleSuppressedDemo.java`**:

```java
import java.io.IOException;

public class TwrMultipleSuppressedDemo {
    static final class Failing implements AutoCloseable {
        private final String name;

        Failing(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() throws IOException {
            System.out.println("Đóng " + name);
            throw new IOException("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Failing a = new Failing("A");
             Failing b = new Failing("B")) {
            System.out.println("Thân try");
            throw new IllegalArgumentException("body");
        } catch (Exception e) {
            System.out.println("Primary: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("Suppressed: "
                        + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 TwrMultipleSuppressedDemo.java
java TwrMultipleSuppressedDemo
```

Stdout:

```text
Mở A
Mở B
Thân try
Đóng B
Đóng A
Primary: body
Suppressed: close B
Suppressed: close A
```

Khi cả thân và hai lần đóng đều thất bại, lỗi thân vẫn là primary. Cả hai lỗi đóng được lưu trong mảng suppressed theo thứ tự B → A, đúng thứ tự đóng resource của TWR.

### 8.2. Nếu thân thành công nhưng nhiều lần đóng đều lỗi?

Khi không có lỗi thân, exception của **resource được đóng đầu tiên** có thể làm primary; exception phát sinh từ việc đóng các resource tiếp theo được suppressed vào nó.

Trong ví dụ A và B, B đóng trước, nên `IOException("close B")` thường là primary, còn `IOException("close A")` suppressed. Đừng mặc định suppressed luôn “lỗi đóng tài nguyên thứ hai” theo thứ tự khai báo; hãy đọc **thứ tự đóng ngược**.

### 8.3. Nếu khởi tạo tài nguyên thứ hai thất bại?

TWR vẫn đóng những tài nguyên **đã khởi tạo thành công trước đó**. Nếu khởi tạo B ném lỗi P rồi `A.close()` lại ném C, P thường là primary, C bị suppressed.

Resource chưa được khởi tạo thành công **không** được TWR bên ngoài gọi `close()` như một đối tượng đã nhận quyền quản lý. Nếu constructor tự mở resource phụ rồi ném giữa chừng, trách nhiệm dọn dẹp nội bộ là của implementation đó.

## 9. Cause và suppressed có thể tồn tại đồng thời

Không có quy tắc cấm một `Throwable` vừa có cause, vừa có suppressed. Ví dụ: tầng dịch vụ bọc một `IOException` thành lỗi dịch vụ, đồng thời có một lỗi dọn dẹp phụ phải giữ lại.

Tạo file **`CauseAndSuppressedTogetherDemo.java`**:

```java
import java.io.IOException;

public class CauseAndSuppressedTogetherDemo {
    public static void main(String[] args) {
        IOException source = new IOException("Nguồn hỏng");
        IllegalStateException service =
                new IllegalStateException("Dịch vụ thất bại", source);

        service.addSuppressed(new IOException("Dọn tệp lỗi"));
        service.addSuppressed(new IOException("Dọn kết nối lỗi"));

        System.out.println("Current: "
                + service.getClass().getSimpleName());
        System.out.println("Cause: "
                + service.getCause().getMessage());
        System.out.println("Suppressed count: "
                + service.getSuppressed().length);
        System.out.println("Suppressed 0: "
                + service.getSuppressed()[0].getMessage());
        System.out.println("Suppressed 1: "
                + service.getSuppressed()[1].getMessage());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CauseAndSuppressedTogetherDemo.java
java CauseAndSuppressedTogetherDemo
```

Stdout:

```text
Current: IllegalStateException
Cause: Nguồn hỏng
Suppressed count: 2
Suppressed 0: Dọn tệp lỗi
Suppressed 1: Dọn kết nối lỗi
```

**Điểm cốt lõi:** `getCause()` là một giá trị, còn `getSuppressed()` là một mảng. Cả hai đều được đọc từ **cùng đối tượng hiện tại** là `service`.

Một exception suppressed cũng có thể có **cause của riêng nó**. Vì thế khi làm công cụ phân tích lỗi, không nên giả định cấu trúc luôn là một chuỗi phẳng. Cần quan sát các mối quan hệ theo từng đối tượng.

## 10. `getCause()` có thể tạo thành chuỗi nguyên nhân

Nếu exception được bọc qua nhiều tầng:

```text
ApplicationException("request failed")
    └── cause: ServiceException("load failed")
            └── cause: IOException("read failed")
```

`getCause()` trên exception ngoài cùng trả cause trực tiếp, không tự trả lỗi sâu nhất. Nếu cần đi sâu, duyệt từng tầng.

### 10.1. Java 17 — duyệt cause chain

Tạo **`CauseChainDemo.java`**:

```java
import java.io.IOException;

public class CauseChainDemo {
    public static void main(String[] args) {
        IOException root = new IOException("read failed");
        IllegalStateException middle =
                new IllegalStateException("load failed", root);
        RuntimeException outer =
                new RuntimeException("request failed", middle);

        Throwable current = outer;
        int depth = 0;

        while (current != null) {
            System.out.println(depth + ": "
                    + current.getClass().getSimpleName()
                    + " / " + current.getMessage());
            current = current.getCause();
            depth++;
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CauseChainDemo.java
java CauseChainDemo
```

Stdout:

```text
0: RuntimeException / request failed
1: IllegalStateException / load failed
2: IOException / read failed
```

Ví dụ dùng chuỗi cause thông thường không có vòng lặp. Khi viết công cụ phân tích Throwable **không đáng tin hoặc có cấu trúc tùy biến**, nên sử dụng tập các đối tượng đã thăm nếu đệ quy qua cả cause và suppressed để tránh lặp vô hạn. Tránh suy đoán “depth càng lớn thì lỗi càng nghiêm trọng”; chuỗi chỉ phản ánh các lần bọc lỗi.

### 10.2. Có nên luôn lấy cause sâu nhất để hiển thị?

Không mặc định. Lỗi ngoài cùng mang **ngữ cảnh nghiệp vụ** hữu ích, ví dụ thất bại khi tải cấu hình; lỗi gốc mang thông tin kỹ thuật, ví dụ đọc file thất bại. Một báo cáo tốt có thể giữ cả hai. Nếu chỉ hiển thị root cause cho người dùng cuối, đôi khi lộ thông tin nội bộ hoặc làm mất ngữ cảnh ở tầng cao.

## 11. `printStackTrace()` thể hiện hai quan hệ ra sao?

`Throwable.printStackTrace()` là phương tiện hiển thị cho con người. Trong một báo cáo thông thường:

- Throwable hiện tại xuất hiện ở dòng đầu cùng trace của nó.
- Suppressed exceptions được chú thích **`Suppressed:`** và hiển thị theo cấp lồng phù hợp.
- Cause được hiển thị qua **`Caused by:`** cùng stack trace của cause.
- Các phần chung của stack có thể được rút gọn ở dạng **`... N more`**.

Ví dụ mô tả:

```text
java.lang.IllegalStateException: Dịch vụ thất bại
    at example.Service.run(Service.java:42)
    Suppressed: java.io.IOException: Lỗi đóng
        at example.Resource.close(Resource.java:18)
Caused by: java.io.IOException: Nguồn hỏng
    at example.Reader.read(Reader.java:27)
```

**Đây là sơ đồ minh họa**, không phải stdout/stderr cố định của một chương trình. Đường dẫn nguồn, dòng, số frame và thứ tự rút gọn phụ thuộc cấu trúc Throwable và môi trường thực thi.

Nếu chạy `printStackTrace()` không tham số, Java thường in ra `System.err`. Không nên so sánh nguyên văn stack trace trong unit test bằng line number và chuỗi `toString()` khi điều cần kiểm tra là loại exception hoặc quan hệ cause/suppressed.

### 11.1. `getStackTrace()` không thay cho `getCause()` và `getSuppressed()`

`getStackTrace()` trả **mảng `StackTraceElement[]` của chính Throwable hiện tại**, không tự gom trace của các ngoại lệ khác thành một mảng. Muốn phân tích quan hệ nguyên nhân/phụ:

```java
Throwable cause = failure.getCause();
Throwable[] suppressed = failure.getSuppressed();
StackTraceElement[] ownFrames = failure.getStackTrace();
```

Đây là mảnh code minh họa giả sử biến `failure` là `Throwable`. Ba API trả ba loại dữ liệu khác nhau. Tùy yêu cầu logging, có thể dùng logger ghi nguyên đối tượng Throwable để không đánh mất cause/suppressed, thay vì chỉ log `failure.getMessage()`.

## 12. Quy tắc và các lỗi thường gặp của `addSuppressed()`

`addSuppressed(Throwable exception)` không cho phép mọi tham số.

- `addSuppressed(null)` không hợp lệ; ném `NullPointerException`.
- `e.addSuppressed(e)` không hợp lệ vì tự-suppression; ném `IllegalArgumentException`.
- Có thể gắn **nhiều Throwable** vào cùng đối tượng primary.
- API không tự kết luận rằng mọi phần tử được gắn là lỗi close. Người viết code có thể chủ động gắn exception từ một bước phụ khác khi hợp đồng phù hợp.
- Không nên dùng `addSuppressed` làm kho lưu các lỗi validation thông thường nếu một cấu trúc kết quả lỗi có danh sách thông báo sẽ diễn đạt rõ hơn.

### 12.1. Java 17 — kiểm tra tham số không hợp lệ

Tạo file **`SuppressedValidationDemo.java`**:

```java
public class SuppressedValidationDemo {
    public static void main(String[] args) {
        Throwable primary = new IllegalStateException("primary");

        try {
            primary.addSuppressed(null);
        } catch (NullPointerException e) {
            System.out.println("null: "
                    + e.getClass().getSimpleName());
        }

        try {
            primary.addSuppressed(primary);
        } catch (IllegalArgumentException e) {
            System.out.println("self: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("Suppressed count: "
                + primary.getSuppressed().length);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SuppressedValidationDemo.java
java SuppressedValidationDemo
```

Stdout:

```text
null: NullPointerException
self: IllegalArgumentException
Suppressed count: 0
```

Cả hai thao tác gắn không hợp lệ đều bị từ chối, nên danh sách suppressed vẫn rỗng.

### 12.2. Mảng `getSuppressed()` là bản sao

`getSuppressed()` trả một mảng mới; sửa mảng do caller giữ **không sửa danh sách nội bộ của Throwable**. Vì phần tử là các tham chiếu tới Throwable, việc thay một phần tử của mảng sao chép không tự thay danh sách suppressed bên trong primary. Tuy nhiên đối tượng Throwable được tham chiếu vẫn có trạng thái riêng, nên không coi việc trả mảng sao chép là sao chép sâu mọi Throwable.

### 12.3. Không có API `setSuppressed(...)`

Trong API chuẩn, thao tác thêm là `addSuppressed(...)`. Không có setter công khai để thay toàn bộ danh sách suppressed theo kiểu `setSuppressed(Throwable[])`. Nếu cần một báo cáo lỗi có thể sửa/xóa các phần tử tùy ý, hãy sử dụng cấu trúc dữ liệu nghiệp vụ riêng thay vì cố biến Throwable thành container tổng quát.

## 13. Vô hiệu hóa suppression: trường hợp nâng cao

`Throwable` có constructor được bảo vệ:

```java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
```

Các lớp con có thể chọn **`enableSuppression = false`**. Khi suppression bị vô hiệu hóa, việc gọi `addSuppressed(...)` không thêm ngoại lệ vào mảng; `getSuppressed()` trả mảng rỗng. Trường hợp này đặc thù và thường không cần với exception nghiệp vụ thông thường.

### 13.1. Java 17 — minh họa suppression bị tắt

Tạo file **`SuppressionDisabledDemo.java`**:

```java
import java.io.IOException;

public class SuppressionDisabledDemo {
    static final class QuietException extends Exception {
        QuietException(String message) {
            super(message, null, false, true);
        }
    }

    public static void main(String[] args) {
        QuietException error = new QuietException("primary");
        error.addSuppressed(new IOException("cleanup"));

        System.out.println("Primary: " + error.getMessage());
        System.out.println("Suppressed count: "
                + error.getSuppressed().length);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SuppressionDisabledDemo.java
java SuppressionDisabledDemo
```

Stdout:

```text
Primary: primary
Suppressed count: 0
```

**Cảnh báo thiết kế:** tắt suppression có thể làm mất thông tin lỗi đóng tài nguyên rất hữu ích. Tính năng dành cho những tình huống ngoại lệ được thiết kế đặc biệt, không phải tối ưu mặc định.

### 13.2. `cause` có phụ thuộc vào suppression không?

Không. Hai cơ chế được điều khiển/thiết lập riêng. Một Throwable được vô hiệu hóa suppression vẫn có thể có cause nếu constructor/`initCause` cho phép. Một Throwable có suppression bình thường cũng không tự có cause.

## 14. Thứ tự thời gian không đồng nghĩa với cause

Một hiểu nhầm phổ biến:

> “Exception thứ nhất luôn là cause; exception thứ hai luôn là suppressed.”

Điều này **không đúng**.

- Trong TWR, exception từ **thân `try`** được ưu tiên làm primary khi đã tồn tại. Exception phát sinh sau trong `close()` có thể bị suppressed, **không** phải cause.
- Khi tầng service bọc exception hạ tầng thành exception nghiệp vụ, lỗi gốc được gắn làm cause, dù thao tác bọc diễn ra **sau** khi lỗi gốc được ném.
- Khi chỉ có lỗi `close()`, lỗi đóng có thể trở thành **primary** dù nó xảy ra muộn trong vòng đời resource.
- Một Throwable có thể vừa có cause vừa có suppressed, nên không thể phân loại dựa trên “đầu tiên/thứ hai” mà bỏ ngữ nghĩa API.

**Quy tắc thực hành:** đọc quan hệ bằng **`getCause()`** và **`getSuppressed()`**. Đừng suy ra quan hệ chỉ từ thứ tự các dòng log.

## 15. Lỗi khi dùng `finally` so với try-with-resources

Mẫu cũ:

```java
Resource resource = open();
try {
    perform(resource);
} finally {
    resource.close();
}
```

Đây là mảnh minh họa. Nếu `perform` đã ném P và `resource.close()` ném C trong `finally`, lỗi C có thể **che mất** lỗi P vì `finally` hoàn tất bất thường.

TWR:

```java
try (Resource resource = open()) {
    perform(resource);
}
```

Khi được áp dụng đúng, TWR tự cố gắng đóng resource và gắn lỗi C thành suppressed của P khi P đã có. Đó là một lợi ích quan trọng ngoài việc giảm số dòng.

Không có nghĩa TWR tự phục hồi/rollback: dữ liệu có thể đã bị thay đổi trước khi exception phát sinh. Primary và suppressed chủ yếu bảo toàn **thông tin chẩn đoán**, còn tính nhất quán nghiệp vụ cần transaction, kiểm tra trước thay đổi, hoặc cơ chế phục hồi phù hợp.

### 15.1. `finally` vẫn có giá trị

`finally` hữu ích cho các công việc không thuộc giao thức `AutoCloseable`, ví dụ ghi nhận trạng thái một phiên xử lý hay khôi phục context. Chỉ cần tránh đóng resource TWR lần hai và tránh `return` hoặc ném lỗi mới trong `finally` nếu mục tiêu là giữ exception gốc.

Nếu một công việc trong `finally` có thể ném, thiết kế riêng cách lưu lỗi này mà không vô tình che primary. Đừng thêm `addSuppressed` thủ công khắp nơi nếu TWR đã xử lý close theo hợp đồng.

## 16. Thiết kế chiến lược xử lý khi có cả hai loại dữ liệu lỗi

Tình huống giả định: một giao dịch đọc file thất bại, kết nối logging bị lỗi trong lúc dọn dẹp. Thiết kế xử lý cần trả lời:

1. **Lỗi chính là gì?** Kiểu và message của Throwable đang được ném.
2. **Cause là gì?** Exception nào đã dẫn đến wrapper ở tầng hiện tại?
3. **Suppressed gồm gì?** Có lỗi đóng/dọn dẹp phụ cần báo cáo không?
4. **Trạng thái sau lỗi ra sao?** Resource có thể đã đóng một phần; các side effect nghiệp vụ có thể đã thực hiện.
5. **Tầng nào chịu trách nhiệm xử lý?** Tầng hạ tầng có thể chuyển lỗi, tầng API có thể báo failure, tầng vận hành có thể log và cảnh báo.

Một chiến lược tốt thường **không** bắt `Throwable` rồi chuyển mọi thứ thành “OK”. Nên bắt kiểu phù hợp hoặc để lỗi lan lên boundary, bảo đảm trạng thái thất bại được truyền đến caller và giữ cause/suppressed cho điều tra.

### 16.1. Duyệt quan hệ mà không nhầm với kiểm soát luồng

Ví dụ phương thức minh họa:

```java
static void inspect(Throwable error) {
    System.out.println("Current: " + error.getMessage());

    Throwable cause = error.getCause();
    if (cause != null) {
        System.out.println("Cause: " + cause.getMessage());
    }

    for (Throwable suppressed : error.getSuppressed()) {
        System.out.println("Suppressed: "
                + suppressed.getMessage());
    }
}
```

Đây là method có thể đặt trong một lớp. Nó chỉ **đọc** quan hệ lỗi; không ném lại, không tự đóng resource và không biến suppressed thành exception đang xử lý. Nếu cần ném tiếp, thực hiện `throw` theo hợp đồng checked/unchecked phù hợp.

### 16.2. Những thông tin nào nên log?

Ở boundary ứng dụng, logger nên có thể ghi đối tượng Throwable đầy đủ, để không mất:

- Loại và message của exception ngoài cùng.
- Trace và cause chain.
- Các suppressed exceptions và trace của chúng.
- Ngữ cảnh request/job (theo chính sách bảo mật và quyền riêng tư).

Chỉ ghi `getMessage()` là chưa đủ khi chẩn đoán lỗi phức tạp. Nhưng cũng không nên trả toàn bộ trace và chuỗi cause cho client công khai vì có thể lộ class, file, đường gọi, nguồn dữ liệu hoặc thông tin nhạy cảm. Trả lỗi có cấu trúc/mã tham chiếu cho client và lưu chi tiết ở hệ thống log được kiểm soát thường hợp lý hơn.

## 17. Các lỗi thường gặp cần tránh

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Cause và suppressed là hai tên cho cùng một lỗi.” | Là hai quan hệ độc lập, đọc qua hai API khác nhau. |
| “`getCause()` trả một mảng các lỗi.” | Trả một `Throwable` hoặc `null`. |
| “`getSuppressed()` trả một Throwable hoặc `null`.” | Trả `Throwable[]`, có thể rỗng. |
| “Lỗi `close()` luôn là cause.” | TWR thường gắn lỗi close thành suppressed khi đã có primary. |
| “Thân thành công, close lỗi thì close vẫn là suppressed.” | Khi không có primary trước đó, lỗi close có thể làm primary. |
| “Có suppressed thì `getCause()` không thể có dữ liệu.” | Một Throwable có thể có cả cause và suppressed. |
| “`catch(IOException)` tự bắt suppressed IOException.” | Handler so kiểu primary Throwable đang lan truyền. |
| “`throw new RuntimeException(e.getMessage())` đã giữ nguyên nhân.” | Chỉ sao chép message; cần constructor truyền cause để liên kết ngoại lệ gốc. |
| “`initCause` là setter gọi bao nhiêu lần cũng được.” | Cause chỉ được khởi tạo một lần theo hợp đồng. |
| “`addSuppressed(null)` sẽ bị bỏ qua.” | Ném `NullPointerException`. |
| “`e.addSuppressed(e)` hợp lệ vì muốn ghi cùng lỗi.” | Tự-suppression bị cấm và ném `IllegalArgumentException`. |
| “`getSuppressed()` là stack trace phụ.” | Chứa các đối tượng Throwable; mỗi cái có stack riêng. |
| “Có nhiều suppressed thì phải có nhiều cause.” | Một Throwable có tối đa một cause trực tiếp; suppressed là danh sách riêng. |
| “TWR bảo đảm tự rollback trạng thái.” | TWR tự quản lý `close()`; rollback cần cơ chế khác. |
| “`printStackTrace()` chỉ in trace của wrapper.” | Dạng in thường có `Caused by` và `Suppressed` để thể hiện quan hệ. |
| “Nếu lỗi đóng được suppressed thì không quan trọng.” | Lỗi phụ vẫn có thể chỉ ra rò rỉ tài nguyên, ghi không hoàn tất hoặc vấn đề hệ thống cần điều tra. |
| “Có thể dùng cause chain làm danh sách mọi lỗi đồng thời.” | Cause mô tả chuỗi nguyên nhân; cần cấu trúc tổng hợp riêng nếu muốn lưu nhiều lỗi ngang hàng theo nghiệp vụ. |

## 18. Bài tập thực hành

### Bài 1 — Nhận diện loại quan hệ

Cho sơ đồ:

```text
IllegalStateException("service")
  cause → IOException("read")
  suppressed → IOException("closeA")
  suppressed → IOException("closeB")
```

Hỏi:

1. `getCause()` trả gì?
2. `getSuppressed().length` bằng bao nhiêu?
3. `catch(IOException)` có tự bắt primary `IllegalStateException` không?
4. Muốn in các lỗi đóng thì duyệt API nào?

**Đáp án:** cause là `IOException("read")`; suppressed count là `2`; handler `IOException` không bắt primary; dùng `getSuppressed()`.

### Bài 2 — Thay đổi tình huống TWR

Chạy `TwrBodyAndCloseDemo` rồi bỏ câu lệnh ném `IllegalStateException` trong thân. Dự đoán loại primary và suppressed count. **Định hướng:** chỉ còn lỗi close, nên primary thường là `IOException` từ `close()`, suppressed rỗng.

### Bài 3 — Thêm tài nguyên thứ ba

Trong `TwrMultipleSuppressedDemo`, thêm resource C vào cuối danh sách. Giữ thân ném `IllegalArgumentException`. Nếu cả A/B/C đóng lỗi, dự đoán thứ tự đóng và mảng suppressed. **Đáp án:** đóng C → B → A; suppressed lần lượt `close C`, `close B`, `close A`.

### Bài 4 — Cause chain ba tầng

Dùng `CauseChainDemo`, thêm một wrapper có thông điệp `"API failed"`. Duyệt `getCause()` và in depth, type, message. Xác nhận wrapper mới có thể có cause là wrapper cũ mà không làm thay đổi cause gốc đã lưu.

### Bài 5 — `initCause` không phải setter tự do

Trong `InitCauseDemo`, giải thích vì sao gọi `initCause` lần thứ hai bị từ chối. Sau đó thử tạo `new IllegalArgumentException("wrapper", root)` rồi gọi `initCause(other)` và dự đoán vì sao constructor đã khởi tạo quan hệ cause.

### Bài 6 — Bảo toàn thông tin khi bọc exception

Viết một method `loadConfiguration()` gọi API đọc có thể ném `IOException`. Bắt nó rồi ném `ConfigurationLoadException("Cannot load config", e)` với constructor truyền cause. Tại caller, in loại wrapper và loại cause. Nếu còn lỗi dọn dẹp, hãy phân biệt rõ cơ chế suppressed thay vì gắn lỗi đó thành cause mới vô nghĩa.

### Bài 7 — Mảng suppressed là bản sao

Tạo `Throwable primary`, gắn hai suppressed. Gọi `getSuppressed()` hai lần lấy hai mảng. Thay phần tử đầu của mảng thứ nhất, rồi đọc lại trên primary. Vì sao danh sách suppressed của primary không bị thay đổi?

### Bài 8 — Thiết kế logging

Một request API gặp `IllegalStateException` với cause là lỗi đọc file và hai suppressed từ dọn dẹp. Thiết kế phản hồi lỗi gửi client và thông tin log nội bộ. **Định hướng:** client nhận mã lỗi/correlation id phù hợp; logger ghi Throwable đầy đủ, không đẩy stack trace và đường file nội bộ ra ngoài.

### Bài 9 — Lỗi chính và tính toàn vẹn dữ liệu

Giả sử `writer.write(...)` ném `IOException("write")` và `writer.close()` lại ném `IOException("close")`. Trình bày primary và suppressed nếu dùng TWR. Sau đó giải thích tại sao việc giữ thông tin lỗi **không chứng minh** file đã rollback hoặc không chứa dữ liệu ghi dở.

## 19. Checklist hiểu và vận dụng đúng

- [ ] Tôi giải thích được `cause` là nguyên nhân được gắn vào lỗi hiện tại, còn `suppressed` là lỗi phụ được lưu kèm chưa?
- [ ] Tôi biết `getCause()` trả một Throwable hoặc `null`, còn `getSuppressed()` trả mảng không-null không?
- [ ] Tôi hiểu `getCause()` có thể tạo chuỗi wrapper, còn suppressed có thể chứa nhiều Throwable chưa?
- [ ] Tôi sử dụng constructor nhận `cause` và không làm mất lỗi gốc khi chuyển tầng chưa?
- [ ] Tôi hiểu `initCause` chỉ được gọi hợp lệ một lần trong điều kiện thích hợp chưa?
- [ ] Tôi biết `addSuppressed(null)`/tự-suppression không hợp lệ chưa?
- [ ] Tôi phân biệt primary của TWR với lỗi đóng bị suppressed chưa?
- [ ] Tôi hiểu khi thân TWR thành công nhưng close lỗi, lỗi đóng có thể là primary chưa?
- [ ] Tôi biết với nhiều resource Java đóng ngược thứ tự khai báo và có thể tích lũy nhiều suppressed chưa?
- [ ] Tôi đã kiểm tra `getSuppressed()` thay vì chỉ đọc message của primary khi dọn dẹp cũng lỗi chưa?
- [ ] Tôi hiểu `catch` dựa trên kiểu exception đang ném, không dựa vào cause hoặc suppressed chưa?
- [ ] Tôi tránh dùng `finally` để đóng lại resource TWR hoặc vô tình che lỗi chính chưa?
- [ ] Tôi không nhầm bảo toàn thông tin exception với rollback/khôi phục trạng thái nghiệp vụ chưa?
- [ ] Tôi log Throwable đầy đủ ở nơi phù hợp nhưng không để lộ stack trace nội bộ ra client công khai chứ?
- [ ] Tôi đã test các tình huống chỉ có cause, chỉ có suppressed, cả hai, không có cái nào, và nhiều lỗi đóng chưa?

**Ghi nhớ cuối:** **Cause** nối lỗi hiện tại với **nguyên nhân** (`getCause()`, constructor nhận cause hoặc `initCause()`); **suppressed exceptions** giữ **các thất bại phụ** (`getSuppressed()`, `addSuppressed()`), đặc biệt khi try-with-resources đang đóng tài nguyên sau khi thân đã lỗi. Cả hai quan hệ cùng tồn tại được, không tự thay đổi handler `catch`, và phải được giữ lại khi chẩn đoán lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-006">Nhiều lần close thất bại và thứ tự lỗi phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều lần close thất bại và thứ tự lỗi phụ trong Java

> **Java Core – Exception** · Phần: **Nguyên nhân và lỗi phụ** · Kiến thức Java 7+ về `try-with-resources`, `Throwable.getSuppressed()` và thứ tự đóng tài nguyên; ví dụ chạy với **Java 17+**.

## 1. Vấn đề cần giải quyết

Một đoạn Java có thể mở nhiều tài nguyên: tệp, socket, luồng I/O, kết nối cơ sở dữ liệu hoặc đối tượng tự triển khai `AutoCloseable`. Ngay cả khi thao tác chính thất bại, Java vẫn phải cố giải phóng các tài nguyên đã mở. Nhưng **nếu nhiều lời gọi `close()` cùng ném ngoại lệ**, chương trình nên báo lỗi nào trước? Có mất lỗi chính hay bỏ qua việc đóng tài nguyên còn lại không?

Câu trả lời của `try-with-resources`:

1. **Đóng theo thứ tự ngược với khai báo:** tài nguyên bên phải đóng trước, rồi dần sang trái.
2. **Cố đóng những tài nguyên còn lại** kể cả khi một lệnh `close()` ném lỗi.
3. **Giữ lại primary exception** (*ngoại lệ chính*) đã tồn tại từ initializer hoặc thân `try`, nếu có.
4. **Gắn lỗi từ `close()` vào primary** bằng `Throwable.addSuppressed()`, để đọc bằng `getSuppressed()`.
5. **Nếu chưa có primary**, lỗi đầu tiên xảy ra trong quá trình đóng trở thành primary; các lỗi đóng tiếp theo được suppressed vào exception ấy.

Kết quả là một đối tượng lỗi chính có thể đi kèm **nhiều ngoại lệ phụ**, chứ không phải Java ngừng đóng sau lỗi đầu tiên hoặc ném đồng thời nhiều exception độc lập.

Ví dụ khái quát:

~~~java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    doWork();
}
~~~

Đây là khung mã minh họa; cần định nghĩa các kiểu và phương thức để chạy độc lập. Nếu tất cả mở thành công, thứ tự đóng là **C → B → A**.

- Nếu `doWork()` ném lỗi X và cả ba lệnh đóng đều lỗi, **X là primary**; danh sách suppressed có lỗi đóng **C, B, A** theo thứ tự phát sinh.
- Nếu `doWork()` thành công nhưng cả ba lệnh đóng lỗi, **lỗi đóng C là primary**; suppressed chỉ gồm **lỗi đóng B, A**.
- Nếu C mở thất bại thì body không chạy, C không được đăng ký thành công để đóng; B và A vẫn được đóng theo **B → A**. **Lỗi mở C là primary**, lỗi đóng B/A là suppressed.

Bài này tập trung vào **nhiều lần close thất bại và thứ tự lỗi phụ**. Kỹ năng cần đạt: phân biệt *primary*, *suppressed* và *cause*, dự đoán đúng thứ tự đóng/lỗi, cũng như truy xuất đủ dữ liệu khi debug.

## 2. Phân biệt ba khái niệm: primary, suppressed, cause

### Primary exception

**Primary** là exception đang giữ vai trò chính của đường thất bại. Với try-with-resources, đó thường là exception phát sinh **trước khi bắt đầu đóng** (từ initializer/thân try). Nếu các bước trước đều thành công, lỗi **đầu tiên** trong chuỗi đóng trở thành primary.

Primary là đối tượng đi tới nhánh `catch` tương ứng, nếu được bắt. Nó **không nhất thiết** là exception xảy ra đầu tiên trong toàn bộ ứng dụng: việc đóng có thể chạy sau các bước thành công, và caller cũng có thể bọc ngoại lệ bằng wrapper khác. Luôn xét **phạm vi một cấu trúc try-with-resources**.

### Suppressed exception

Một exception phát sinh trong khi dọn dẹp có thể bị **suppressed** vào primary để không che mất nguyên nhân đang xử lý. API:

~~~java
Throwable[] extra = primary.getSuppressed();
~~~

trả về một mảng các đối tượng `Throwable` đã được gắn làm lỗi phụ. Phần tử ở chỉ số 0 là exception được thêm vào danh sách **sớm nhất**; các phần tử tiếp theo phản ánh thứ tự được thêm.

Trong chuỗi đóng C → B → A, nếu đã có primary và cả ba lần đóng lỗi thì:

~~~text
primary       = body failed
suppressed[0] = close C failed
suppressed[1] = close B failed
suppressed[2] = close A failed
~~~

Đây là ví dụ theo tình huống vừa nêu, không phải quy tắc rằng *mọi* suppressed exception trong Java đều do `close()`. Ứng dụng có thể tự gọi `addSuppressed()`; một thư viện cũng có thể bổ sung thông tin theo chính sách riêng. Thứ tự mảng là **thứ tự thêm vào**, còn thứ tự C/B/A là kết quả cụ thể của cách try-with-resources đóng tài nguyên.

### Cause

`getCause()` thể hiện **exception gây ra** một exception khác trong quan hệ *exception chaining*:

~~~java
throw new IllegalStateException("Cannot save document", ioFailure);
~~~

Đây là mã minh họa, giả định `ioFailure` là một Throwable phù hợp. Wrapper `IllegalStateException` mới có **cause = ioFailure**. Trong khi đó, suppressed exceptions là một danh sách phụ **trên một đối tượng Throwable**, không phải một liên kết cause duy nhất.

| Thuộc tính | Ý nghĩa | API truy xuất |
| --- | --- | --- |
| Primary | Exception chính được ném từ một phạm vi xử lý | Đối tượng nhận ở `catch` |
| Cause | Nguyên nhân được liên kết khi tạo/bọc exception | `ex.getCause()` |
| Suppressed | Các exception phụ được gắn vào cùng đối tượng, thường từ cleanup | `ex.getSuppressed()` |

**Không dùng hai thuật ngữ “cause” và “suppressed” thay thế nhau**: một primary có thể vừa chứa cause vừa chứa nhiều suppressed; từng suppressed cũng có stack trace và cause riêng.

## 3. Quy tắc thứ tự đóng: từ phải sang trái, kể cả khi close lỗi

Các tài nguyên trong header được khởi tạo từ **trái sang phải**, nhưng được đóng từ **phải sang trái**. Ví dụ:

~~~java
try (Resource a = open("A");
     Resource b = open("B");
     Resource c = open("C")) {
    work();
}
~~~

Đây là ví dụ minh họa chưa độc lập. Dòng thời gian nếu tất cả resource đã được khởi tạo:

~~~text
open A
open B
open C
work
close C    ← được gọi đầu tiên
close B    ← vẫn được gọi nếu close C ném lỗi
close A    ← vẫn được gọi nếu close B ném lỗi
~~~

Lý do là ngữ nghĩa của try-with-resources tương đương về mặt cơ chế với các phạm vi tài nguyên lồng nhau: tài nguyên mở sau được đóng trước, theo kiểu *last in, first out*.

Thứ tự này **xác định cả thứ tự lỗi phụ**. Ví dụ nếu body đã có lỗi X và cả C, B, A đều ném khi đóng, Java gặp lỗi đóng C trước, sau đó B, rồi A; lỗi sẽ được thêm vào suppressed theo cùng thứ tự đó.

Nếu một tài nguyên có tham chiếu `null`, phần đóng tự động **không gọi** `close()` trên nó. Nếu việc đóng một resource diễn ra bình thường, nó cũng **không góp exception** vào suppressed. Vì vậy các mục suppressed có thể ít hơn số tài nguyên đã khai báo.

## 4. Chương trình Java 17 hoàn chỉnh và stdout kiểm chứng

Lưu toàn bộ chương trình thành **`SuppressedCloseOrderDemo.java`**. Mọi ví dụ đều dùng cùng một lớp `TracedResource` có thể chủ động gây lỗi ở initializer hoặc `close()`; các message được viết cố định để output không phụ thuộc file hệ thống.

~~~java
import java.io.IOException;

public class SuppressedCloseOrderDemo {
    private static final class TracedResource implements AutoCloseable {
        private final String name;
        private final boolean failClose;

        private TracedResource(String name, boolean failOpen, boolean failClose)
                throws IOException {
            System.out.println("open " + name);
            if (failOpen) {
                throw new IOException("open " + name + " failed");
            }
            this.name = name;
            this.failClose = failClose;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    private static TracedResource open(
            String name, boolean failOpen, boolean failClose)
            throws IOException {
        return new TracedResource(name, failOpen, failClose);
    }

    private static void report(IOException ex) {
        System.out.println("primary=" + ex.getMessage());
        Throwable[] suppressed = ex.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            System.out.println("suppressed[" + i + "]="
                    + suppressed[i].getMessage());
        }
    }

    private static void bodyFails() {
        System.out.println("== body fails ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true);
             TracedResource c = open("C", false, true)) {
            System.out.println("body");
            throw new IOException("body failed");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void closeOnlyFails() {
        System.out.println("== close only fails ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true);
             TracedResource c = open("C", false, true)) {
            System.out.println("body ok");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void openFails() {
        System.out.println("== open fails ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true);
             TracedResource c = open("C", true, true)) {
            System.out.println("unreachable body");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void middleCloseFails() {
        System.out.println("== middle close fails ==");
        try (TracedResource a = open("A", false, true);
             TracedResource b = open("B", false, true);
             TracedResource c = open("C", false, false)) {
            System.out.println("body ok");
        } catch (IOException ex) {
            report(ex);
        }
    }

    private static void clean() {
        System.out.println("== clean ==");
        try (TracedResource a = open("A", false, false);
             TracedResource b = open("B", false, false)) {
            System.out.println("body ok");
        } catch (IOException ex) {
            report(ex);
        }
        System.out.println("after");
    }

    public static void main(String[] args) {
        bodyFails();
        closeOnlyFails();
        openFails();
        middleCloseFails();
        clean();
    }
}
~~~

### Biên dịch và chạy

Dùng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 -encoding UTF-8 SuppressedCloseOrderDemo.java
java SuppressedCloseOrderDemo
~~~

**Stdout chính xác** đã đối chiếu sau khi biên dịch và chạy:

~~~text
== body fails ==
open A
open B
open C
body
close C
close B
close A
primary=body failed
suppressed[0]=close C failed
suppressed[1]=close B failed
suppressed[2]=close A failed
== close only fails ==
open A
open B
open C
body ok
close C
close B
close A
primary=close C failed
suppressed[0]=close B failed
suppressed[1]=close A failed
== open fails ==
open A
open B
open C
close B
close A
primary=open C failed
suppressed[0]=close B failed
suppressed[1]=close A failed
== middle close fails ==
open A
open B
open C
body ok
close C
close B
close A
primary=close B failed
suppressed[0]=close A failed
== clean ==
open A
open B
body ok
close B
close A
after
~~~

Chương trình được biên dịch với `javac --release 17` và chạy để kiểm chứng output. Không cần file đầu vào, thư viện bên ngoài hay chạy JUnit. Những tình huống gây lỗi đều được bắt và in theo một định dạng ổn định.

## 5. Giải thích tình huống body lỗi và ba close cũng lỗi

Phương thức `bodyFails()` mở A, B, C thành công; thân try in `body` rồi ném:

~~~java
throw new IOException("body failed");
~~~

Lúc đó primary đã tồn tại **trước khi giai đoạn đóng bắt đầu**. Java lần lượt:

1. Gọi `c.close()`, in `close C`, nhận `IOException("close C failed")` và thêm vào suppressed của body exception.
2. Gọi `b.close()`, in `close B`, nhận `IOException("close B failed")` và thêm vào suppressed tiếp theo.
3. Gọi `a.close()`, in `close A`, nhận `IOException("close A failed")` và thêm vào suppressed thứ ba.
4. Chuyển primary `IOException("body failed")` đến catch; handler in primary rồi duyệt mảng suppressed.

**Thứ tự bắt buộc trong cấu trúc này:**

~~~text
primary=body failed
suppressed[0]=close C failed
suppressed[1]=close B failed
suppressed[2]=close A failed
~~~

Đây là lý do quan trọng phải đọc **cả primary lẫn suppressed**. Nếu chỉ in `ex.getMessage()`, bạn chỉ thấy `body failed` và mất dấu ba vấn đề ở quá trình đóng.

**Không nhầm:** không có ba câu lệnh `throw` độc lập cùng thoát khỏi method một lúc. Java tiếp tục thực hiện cleanup và đính kèm ngoại lệ đóng vào một primary duy nhất.

## 6. Giải thích tình huống chỉ close thất bại

Trong `closeOnlyFails()`, mọi tài nguyên khởi tạo thành công và thân try cũng in `body ok`, không có exception trước khi bắt đầu đóng.

- C đóng đầu tiên và ném lỗi. Vì chưa có primary, lỗi `close C failed` được chọn làm **primary**.
- B đóng tiếp theo, cũng ném lỗi; lỗi B được suppressed vào lỗi C.
- A đóng cuối cùng, cũng ném lỗi; lỗi A là suppressed thứ hai.

Output:

~~~text
primary=close C failed
suppressed[0]=close B failed
suppressed[1]=close A failed
~~~

**Lỗi đóng đầu tiên không phải phần tử `suppressed[0]`**, bởi trong trường hợp không có lỗi trước đó, lỗi đóng đầu tiên **đã là primary**. Đây là khác biệt với phần 5, nơi primary tồn tại từ thân try, nên mọi lỗi đóng đều thuộc suppressed.

Từ đó không thể kết luận rằng body thành công thì method chắc chắn thành công: `close()` có thể biến một lần xử lý tưởng như bình thường thành exception.

## 7. Giải thích tình huống initializer cuối thất bại

Trong `openFails()`:

~~~java
try (TracedResource a = open("A", false, true);
     TracedResource b = open("B", false, true);
     TracedResource c = open("C", true, true)) {
    System.out.println("unreachable body");
}
~~~

Đây là đoạn trích từ chương trình đầy đủ, cần catch/throws như bên trên. A và B mở thành công. C bắt đầu khởi tạo, in `open C`, nhưng constructor ném `IOException("open C failed")` **trước khi trả về** một TracedResource thành công.

Hậu quả:

- **Body không chạy**, nên không có dòng `unreachable body`.
- **C không được tự động đóng** bởi resource specification này, vì initializer của C chưa hoàn thành và chưa giao tài nguyên C để quản lý. Không có `close C` trong stdout.
- **B và A đã được đăng ký thành công** nên vẫn được đóng, theo B → A.
- Lỗi mở C là primary; lỗi đóng B rồi A được suppressed theo thứ tự.

~~~text
primary=open C failed
suppressed[0]=close B failed
suppressed[1]=close A failed
~~~

Cần phân biệt **initializer đã bắt đầu làm việc** với **resource đã được khởi tạo thành công**. Nếu một factory tự mở file hoặc socket bên trong rồi thất bại trước khi bàn giao resource, caller không thể tự biết các tài nguyên trung gian ấy. Factory/constructor phải thiết kế cleanup riêng.

## 8. Nếu resource ngoài cùng đóng thành công, lỗi phụ bắt đầu từ đâu?

Trong `middleCloseFails()`:

- C đóng trước và **không lỗi**.
- B đóng tiếp theo, **ném lỗi đầu tiên** → lỗi B là primary.
- A đóng cuối cùng, cũng lỗi → lỗi A là suppressed.

~~~text
primary=close B failed
suppressed[0]=close A failed
~~~

Do C không ném exception, C **không xuất hiện trong mảng suppressed**. Số phần tử của suppressed **không bắt buộc bằng số resource được khai báo**, cũng không bằng số resource đã đóng. Chỉ các exception thực tế được thêm vào danh sách mới xuất hiện.

Ở nhánh `clean()`, cả việc mở, body và đóng đều bình thường, nên **không có primary và không có suppressed**. Chương trình in `after` sau khi mọi tài nguyên được đóng xong. Đó là đối chứng để thấy việc cleanup diễn ra cả khi không có lỗi.

## 9. Bảng quyết định để xác định primary và suppressed

Giả định header là A, B, C từ trái sang phải. Mỗi hàng là một lần chạy độc lập:

| Kịch bản | Thứ tự close được thử | Primary | Danh sách suppressed theo thứ tự |
| --- | --- | --- | --- |
| Body ném X, C/B/A đều lỗi đóng | C, B, A | X | C, B, A |
| Body thành công, C/B/A đều lỗi đóng | C, B, A | Lỗi C | Lỗi B, A |
| Body thành công, C đóng tốt, B/A lỗi | C, B, A | Lỗi B | Lỗi A |
| Body thành công, C/B đóng tốt, A lỗi | C, B, A | Lỗi A | Rỗng |
| C lỗi lúc khởi tạo, B/A lỗi đóng | B, A | Lỗi mở C | Lỗi B, A |
| B lỗi lúc khởi tạo, A lỗi đóng | A | Lỗi mở B | Lỗi A |
| Body ném X, mọi close thành công | C, B, A | X | Rỗng |
| Body và mọi close thành công | C, B, A | Không có | Không có |

**Công thức suy luận:** Ghi ra các resource **đã khởi tạo thành công**. Đảo thứ tự danh sách ấy để tìm chuỗi `close()`. Nếu có exception từ body/initializer, giữ nó làm primary; mỗi lỗi đóng vào suppressed. Nếu chưa có exception chính, lỗi đóng đầu tiên là primary; lỗi đóng sau là suppressed.

Bảng này giả định những exception được tạo bình thường, suppression được bật (mặc định với các exception chuẩn trong ví dụ), không có mã tự gọi `addSuppressed()` ở nơi khác và không có exception mới từ catch/finally ngoài thay thế đường điều khiển.

## 10. Tại sao Java vẫn cố close những resource còn lại?

Mô hình try-with-resources tương đương về hành vi đóng với **các phạm vi tài nguyên lồng nhau**, mỗi tài nguyên có dọn dẹp riêng:

~~~java
try (Resource a = openA()) {
    try (Resource b = openB()) {
        try (Resource c = openC()) {
            doWork();
        }
    }
}
~~~

Đây là cấu trúc minh họa; cần định nghĩa kiểu/method phù hợp. Về mặt quan sát, resource trong cùng một header được xử lý như cấu trúc lồng nhau đó, vì thế tài nguyên tạo cuối rời phạm vi đầu tiên.

Một cách mô tả ý tưởng cho **một** resource (*pseudocode*, không phải bản sao nguyên văn của mã compiler sinh ra):

~~~java
Throwable primary = null;
try {
    body();
} catch (Throwable error) {
    primary = error;
    throw error;
} finally {
    if (resource != null) {
        if (primary != null) {
            try {
                resource.close();
            } catch (Throwable closeError) {
                primary.addSuppressed(closeError);
            }
        } else {
            resource.close();
        }
    }
}
~~~

Các biến/method trong pseudocode là tượng trưng. Với nhiều resource, các phạm vi lồng nhau tiếp tục đóng từ trong ra ngoài ngay cả khi resource phía trong ném lỗi, nên một lần đóng thất bại không đồng nghĩa bỏ qua những tài nguyên còn lại.

Đây là sự khác biệt với đoạn tự gọi close thủ công thiếu xử lý ngoại lệ:

~~~java
c.close();
b.close();
a.close();
~~~

Nếu `c.close()` ném exception ở dòng đầu, hai lời gọi `b.close()` và `a.close()` **có thể không chạy**. Ngay cả khi dùng `finally` thủ công, để tái tạo chính xác cơ chế primary/suppressed của try-with-resources cũng cần quản lý lỗi cẩn thận, dễ thiếu hoặc che nguyên nhân gốc.

## 11. getSuppressed() trả về gì và duyệt như thế nào?

`Throwable.getSuppressed()` trả về một mảng `Throwable[]` chứa các đối tượng đã được đính kèm vào exception đó. Một primary có thể có 0, 1 hoặc nhiều suppressed exception.

Ví dụ:

~~~java
catch (IOException ex) {
    System.err.println("PRIMARY: " + ex);
    for (Throwable suppressed : ex.getSuppressed()) {
        System.err.println("SUPPRESSED: " + suppressed);
    }
}
~~~

Đây là một handler minh họa trong ngữ cảnh try phù hợp, không phải chương trình độc lập.

### Thứ tự trong mảng có ý nghĩa gì?

Theo hợp đồng API, các exception suppressed được trả về theo **thứ tự đã bị suppress/thêm vào**. Try-with-resources đóng theo thứ tự ngược với khai báo, nên nếu mỗi resource ném lỗi khi đóng và đã có primary thì thứ tự mảng thường chính là **thứ tự các lần đóng lỗi**.

Ví dụ nếu C, B, A cùng ném:

~~~text
getSuppressed()[0] → lỗi C.close()
getSuppressed()[1] → lỗi B.close()
getSuppressed()[2] → lỗi A.close()
~~~

Nhưng nếu C.close() thành công, không có phần tử dành cho C. Nếu body đã tự gọi `primary.addSuppressed(extra)` trước khi cleanup, danh sách có thể **chứa extra trước các lỗi close**. Vì vậy không nên suy từ index sang một tài nguyên cụ thể nếu không biết cách exception được xây dựng.

### Có thể tự gọi addSuppressed không?

Có. API:

~~~java
primary.addSuppressed(other);
~~~

gắn một Throwable khác vào primary khi suppression được bật. Nó hữu ích trong các cơ chế cleanup tùy chỉnh, nhưng **không nên tự thêm lại lỗi close** mà try-with-resources đã thêm, vì sẽ gây lặp và làm sai cách diễn giải.

Quy tắc API cần nhớ:

- `addSuppressed(null)` ném `NullPointerException`.
- `primary.addSuppressed(primary)` ném `IllegalArgumentException` vì không cho phép tự suppress chính mình.
- Mặc định các exception chuẩn cho phép suppression. Nếu exception được tạo với suppression disabled, `addSuppressed()` không lưu thêm lỗi phụ.
- `getSuppressed()` trả về một mảng mới mỗi lần gọi; sửa mảng do getter trả về không làm thay đổi danh sách bên trong exception.

Những tính chất này giúp phân biệt suppressed exception với một collection do ứng dụng tự lưu. Trong mã nghiệp vụ thông thường, **đọc suppressed để chẩn đoán** là nhu cầu phổ biến hơn việc tự gọi addSuppressed.

## 12. In stack trace để không bỏ sót lỗi phụ

Nếu chương trình chỉ ghi:

~~~java
System.err.println(ex.getMessage());
~~~

thì sẽ không thể biết primary có bao nhiêu suppressed exception, cũng không nhìn thấy stack trace nguồn gốc của từng lỗi. Đối với debug, hãy giữ cả đối tượng Throwable khi log:

~~~java
ex.printStackTrace();
~~~

Theo API Java, `printStackTrace()` hiển thị exception chính, các cause và những suppressed exception liên quan, với cấu trúc thụt lề giúp phân biệt.

Ví dụ hình thức minh họa:

~~~text
java.io.IOException: body failed
    at ...
    Suppressed: java.io.IOException: close C failed
        at ...
    Suppressed: java.io.IOException: close B failed
        at ...
    Suppressed: java.io.IOException: close A failed
        at ...
~~~

**Đây không phải output tuyệt đối.** Số frame, số dòng, tên lớp và phần rút gọn kiểu `... N more` phụ thuộc call stack và định dạng của JVM. Không nên viết test so sánh nguyên văn toàn bộ `printStackTrace()`.

Với logging framework phù hợp, truyền **đối tượng exception** cho logger, thay vì chỉ lưu message; ví dụ API `java.util.logging`:

~~~java
logger.log(java.util.logging.Level.SEVERE, "Processing failed", ex);
~~~

Đây là đoạn minh họa, giả định có biến `logger` đã khai báo. Một hệ thống quan sát tốt nên lưu được **primary, cause, suppressed và stack trace** nhưng vẫn tuân thủ chính sách tránh log thông tin nhạy cảm.

## 13. Cause và suppressed trong trường hợp bọc exception

Giả sử tầng xử lý không muốn lộ trực tiếp `IOException` đến caller, nên bọc:

~~~java
try {
    executeWork();
} catch (IOException ex) {
    throw new IllegalStateException("Batch processing failed", ex);
}
~~~

Đây là mã minh họa, giả định `executeWork()` khai báo ném IOException.

Nếu `ex` có ba suppressed exception, wrapper mới có:

- `wrapper.getCause() == ex`.
- `ex.getSuppressed()` vẫn chứa ba lỗi close.
- `wrapper.getSuppressed()` **không tự sao chép** ba lỗi ấy sang wrapper.

Muốn xem lỗi đóng, người điều tra cần đi theo **cause** để tới đối tượng `ex` rồi xem suppressed trên đó. Nếu chỉ xem suppressed của wrapper mới, rất có thể mảng rỗng. Đây là lý do công cụ logging nên hỗ trợ **cả cây Throwable**, không chỉ exception ngoài cùng.

**Không nên tự chuyển suppressed sang wrapper bằng cách copy bừa**: một lỗi xuất hiện nhiều lần có thể gây log trùng và làm thay đổi cấu trúc nguyên nhân. Giữ exception gốc làm cause thường đã bảo tồn dữ liệu.

## 14. Khi suppression bị vô hiệu hóa

Một subclass của Throwable có thể dùng constructor bảo vệ để đặt `enableSuppression=false`:

~~~java
class NoSuppressionException extends Exception {
    NoSuppressionException(String message) {
        super(message, null, false, true);
    }
}
~~~

Đây là định nghĩa lớp minh họa, có thể đặt ở cấp top-level hoặc trong class khác. Tham số thứ ba `false` **tắt suppression**, còn tham số thứ tư `true` vẫn cho phép ghi stack trace. Hai tính năng này độc lập.

Nếu một `NoSuppressionException` là primary trong body, còn các lời gọi close sau đó ném lỗi:

- Java vẫn thực hiện cơ chế đóng các tài nguyên đã mở.
- Nó vẫn thử suppress lỗi close vào primary theo quy tắc tương ứng.
- Do **primary tắt suppression**, lỗi close không được lưu trong mảng suppressed.
- `primary.getSuppressed().length` sẽ bằng 0.

Điều này rất quan trọng khi debug một hệ thống sử dụng exception tùy biến: **mảng suppressed rỗng không phải bằng chứng tuyệt đối rằng mọi close đều thành công**. Có thể suppression đã bị tắt theo thiết kế exception.

Đừng tắt suppression hoặc stack trace cho exception nghiệp vụ chỉ để tối ưu hiệu năng mà chưa cân nhắc khả năng chẩn đoán. Nếu quan sát nhiều lỗi cleanup là quan trọng, hãy giữ suppression bật.

## 15. Nếu finally bên ngoài lại throw thì sao?

Với:

~~~java
try (Resource a = openA(); Resource b = openB()) {
    work();
} catch (IOException ex) {
    report(ex);
} finally {
    after();
}
~~~

Đây là cấu trúc minh họa chưa độc lập. Việc đóng a/b xảy ra **trước khi catch/finally của extended try-with-resources** xử lý kết quả. Sau cleanup và xử lý lỗi, một `finally` ở ngoài vẫn được thực thi theo quy tắc Java.

Nếu chính `finally` ngoài ném một exception khác, kết quả điều khiển cuối cùng có thể chuyển sang exception mới; điều đó là **quy tắc của finally**, tách biệt với cơ chế suppressed do try-with-resources tự thực hiện.

Trong mã sản xuất, tránh những đoạn cleanup tự viết phức tạp trong finally khi đã có try-with-resources, và đừng dùng `return` trong finally để vô tình che exception được bảo toàn cẩn thận từ resource cleanup.

## 16. Mở resource cũng có thể lỗi trước khi body bắt đầu

Suppressed không chỉ dành cho trường hợp body đã chạy. Nếu khởi tạo tài nguyên thứ ba thất bại, những tài nguyên trước đó vẫn được đóng và có thể ném exception. Trong ví dụ `openFails()`:

~~~text
open A
open B
open C
close B
close A
primary=open C failed
suppressed[0]=close B failed
suppressed[1]=close A failed
~~~

B và A được đóng dù thân try **không được vào**. Vì thế nếu một chương trình gặp lỗi khởi tạo kết nối/tệp, **vẫn phải xem suppressed** để biết các kết nối/tệp đã mở trước đó có được giải phóng thành công hay không.

Ngược lại, initializer của C đã bắt đầu nhưng không trả về tài nguyên C thành công, nên cấu trúc này không cố `C.close()`. Nếu factory C đã mở resource trung gian rồi thất bại, trách nhiệm cleanup ở chính factory, không ở resource specification đã không nhận được C.

## 17. Sự liên quan của kiểu ngoại lệ close

`AutoCloseable.close()` được khai báo có thể ném `Exception`. Một lớp cụ thể thường thu hẹp kiểu này, như ví dụ `TracedResource.close() throws IOException`. Khi phân tích checked exception ở compile time, Java xem xét những ngoại lệ có thể đến từ initializer, body và close.

Vì vậy một ví dụ:

~~~java
static void handle() throws IOException {
    try (TracedResource resource = open("R", false, true)) {
        System.out.println("work");
    }
}
~~~

là minh họa dựa trên lớp `TracedResource` ở chương trình chính. Phương thức này phải khai báo/handle `IOException`, ngay cả khi body chỉ in text, bởi initializer/close vẫn có thể ném IOException.

Nếu resource có kiểu tĩnh là `AutoCloseable` và `close()` khai báo ném `Exception`, bạn có thể cần xử lý hoặc khai báo kiểu checked rộng hơn. Multi-catch hoặc nhiều catch riêng vẫn áp dụng theo quan hệ subtype.

Không phải mọi close thất bại đều thuộc IOException: các triển khai có thể ném unchecked exception. Cơ chế primary/suppressed căn cứ vào các Throwable thực tế được ném; hãy kiểm tra hợp đồng tài nguyên.

## 18. Những lỗi hiểu sai hay gặp

- **“Nhiều close lỗi thì lỗi cuối cùng thắng.”** Sai: nếu body/initializer đã lỗi, chúng giữ primary; nếu chưa lỗi, **lỗi close đầu tiên** là primary.
- **“Suppressed luôn bắt đầu bằng lỗi resource đóng đầu tiên.”** Chỉ đúng nếu đã có primary trước lúc đóng; nếu chưa có, lỗi đóng đầu tiên chính là primary.
- **“Resource nào khai báo trước đóng trước.”** Sai: đóng từ **phải sang trái**.
- **“B.close lỗi thì A.close không chạy.”** Sai với try-with-resources: Java vẫn cố đóng các tài nguyên trước đó.
- **“Body chạy bình thường nghĩa là không có exception.”** Sai: close có thể tạo primary.
- **“Nếu initializer cuối lỗi thì không resource nào đóng.”** Sai: các resource trước đó đã khởi tạo thành công được đóng.
- **“Initializer lỗi vẫn được tự động close.”** Không nếu nó chưa bàn giao một resource được khởi tạo thành công; factory cần tự cleanup nội bộ.
- **“Lỗi bị suppressed là lỗi đã bị mất.”** Sai: bình thường có thể truy xuất qua `getSuppressed()`.
- **“Suppressed và cause là cùng một danh sách.”** Sai: `getCause()` là liên kết nguyên nhân; `getSuppressed()` trả về mảng lỗi phụ.
- **“Bọc primary vào wrapper sẽ tự chuyển suppressed sang wrapper.”** Sai: suppressed ở primary được giữ khi primary làm cause, không tự copy thành suppressed của wrapper.
- **“Đổi phần tử trong mảng getSuppressed thay đổi Throwable gốc.”** Sai: getter trả mảng mới.
- **“Suppressed rỗng chứng minh close không lỗi.”** Không tuyệt đối; suppression có thể đã bị tắt.
- **“Có ba resource nên luôn có ba suppressed.”** Sai: những resource đóng thành công không tạo lỗi; khi không có primary trước, lỗi close đầu tiên thành primary.
- **“Chỉ cần in message của IOException.”** Dễ mất cả suppressed, cause và dấu vết stack.
- **“Try-with-resources tự rollback những lần ghi file/database.”** Sai: nó quản lý đóng tài nguyên, không tạo transaction.
- **“Finally bên ngoài luôn bảo toàn primary đã có.”** Nếu finally tự ném/return bất thường, nó có thể che hoặc thay đổi đường điều khiển.

## 19. Cách tự giải một bài dự đoán output

Đối với mọi bài có nhiều resource, thực hiện lần lượt:

1. **Ghi danh sách initializer** theo thứ tự từ trái sang phải, đánh dấu initializer đầu tiên không hoàn tất bình thường nếu có.
2. **Ghi những resource đã mở thành công**. Nếu initializer thứ n lỗi, các initializer sau n không chạy.
3. **Xác định body có chạy hay không**, và body có ném exception nào không.
4. **Xác định primary ban đầu**: lỗi initializer/body nếu có; nếu không, chưa có primary trước khi close.
5. **Đảo danh sách tài nguyên thành công** để có thứ tự đóng (bên phải trước).
6. **Duyệt từng close theo thứ tự**. Nếu close thành công: không thêm lỗi. Nếu close lỗi và chưa có primary: chọn nó làm primary. Nếu đã có primary: gắn vào suppressed (trừ khi suppression bị tắt hay trường hợp đặc biệt theo API).
7. **Chỉ sau cleanup mới xét catch/finally ở ngoài**; xem handler có thay đổi hay truyền tiếp exception không.
8. **Viết stdout theo thời gian xảy ra**, không dự đoán theo thứ tự khai báo catch hay theo thứ tự trái→phải của resource.

### Bài tập 1

Khai báo A, B, C. Body ném X; C.close() thành công; B.close() ném Y; A.close() ném Z. Primary và suppressed?

**Đáp án:** primary X; suppressed [Y, Z]. Thứ tự close vẫn C → B → A, nhưng C không đóng góp suppressed vì không lỗi.

### Bài tập 2

Khai báo A, B, C. Body thành công; C.close() ném X; B.close() ném Y; A.close() thành công.

**Đáp án:** primary X; suppressed [Y]. A.close() vẫn được gọi dù có hai lỗi trước đó.

### Bài tập 3

A mở thành công; B khởi tạo thất bại với X; A.close() ném Y.

**Đáp án:** body không chạy. Primary X; suppressed [Y]. B không được close tự động nếu chưa khởi tạo thành công.

### Bài tập 4

Body ném X, và các lần close ném Y rồi Z. Sau đó code bọc X bằng `new IllegalStateException("failed", X)`. Lỗi Y/Z nằm ở đâu?

**Đáp án:** Y và Z là suppressed của X. Wrapper mới có cause là X; suppressed của wrapper không tự chứa Y/Z.

### Bài tập 5

Body thành công, C đóng bình thường, B đóng lỗi X, A đóng lỗi Y. Có thể viết `suppressed[0]=X` không?

**Đáp án:** Không. X là **primary**; suppressed[0] là Y.

### Bài tập 6

Một primary được tạo với suppression disabled; body ném primary, A.close() và B.close() đều lỗi. Có thể dựa vào `getSuppressed().length == 0` để khẳng định hai close thành công không?

**Đáp án:** Không. Các exception cleanup có thể không được lưu do suppression bị tắt.

## 20. Tài liệu Java chính thức

- [Java SE 17 JLS – try-with-resources (mục 14.20.3)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cú pháp resource specification, vòng đời tài nguyên và cleanup.
- [Java SE 17 JLS – basic try-with-resources (mục 14.20.3.1)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.1): cơ chế tương đương các try lồng nhau, primary và suppressed.
- [Java SE 17 JLS – extended try-with-resources (mục 14.20.3.2)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3.2): phối hợp cleanup với catch và finally.
- [Java SE 17 API – Throwable.addSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#addSuppressed(java.lang.Throwable)): quy tắc gắn exception phụ.
- [Java SE 17 API – Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): mảng exception phụ và thứ tự trả về.
- [Java SE 17 API – AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng phương thức `close()`.
- [Oracle Java Tutorials – try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): ví dụ cơ bản về primary và suppressed; khi cần độ chính xác theo phiên bản, ưu tiên JLS Java 17.

**Kết luận:** Khi có nhiều tài nguyên, Java đóng theo thứ tự **ngược với thứ tự khai báo** và vẫn cố đóng phần còn lại nếu một lần close thất bại. Nếu đã có lỗi từ initializer/body, lỗi đó là **primary**, còn lỗi đóng được thêm vào suppressed theo thứ tự xảy ra. Nếu chỉ close thất bại, **lỗi close đầu tiên là primary**, các lỗi sau là suppressed. Muốn debug đầy đủ, hãy theo dõi cả **primary, cause, suppressed và stack trace**, không chỉ message của lỗi chính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-007">Giới hạn với self-cause, self-suppression và giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn với self-cause, self-suppression và giá trị `null` trong Java

Đối tượng `Throwable` có hai cơ chế liên kết lỗi cần phân biệt: **cause** — nguyên nhân trực tiếp của một ngoại lệ — và **suppressed exceptions** — các ngoại lệ phụ bị giữ lại khi một ngoại lệ khác đang được truyền ra ngoài. Cả hai giúp giữ bối cảnh chẩn đoán, nhưng Java đặt ra những quy tắc khác nhau về việc tham chiếu tới **chính đối tượng đó** và cách xử lý giá trị `null`.

Bốn quy tắc quan trọng nhất:

- **Self-cause bị cấm:** `ex.initCause(ex)` ném `IllegalArgumentException`. Một ngoại lệ không thể được chỉ định là nguyên nhân trực tiếp của chính nó qua API này.
- **Self-suppression bị cấm:** `ex.addSuppressed(ex)` cũng ném `IllegalArgumentException`. Một ngoại lệ không thể suppress chính nó.
- **`null` có thể là cause:** `initCause(null)` hoặc constructor nhận cause bằng `null` là hợp lệ. Tuy nhiên, `initCause(null)` vẫn **tiêu thụ quyền khởi tạo cause một lần duy nhất**.
- **`null` không thể là suppressed exception:** `ex.addSuppressed(null)` ném `NullPointerException`. Muốn không có lỗi phụ thì **không gọi `addSuppressed`**, thay vì truyền `null`.

Điểm dễ gây lỗi là kiểm tra bằng `getCause() == null` rồi nghĩ rằng chưa từng khởi tạo cause. Giá trị trả về `null` chỉ có nghĩa chưa biết hoặc không có nguyên nhân; nó **không phản ánh** liệu quyền gọi `initCause` đã được sử dụng hay chưa.

Bài này giải thích hợp đồng Java SE 21 của `Throwable.initCause()`, `addSuppressed()`, `getCause()`, `getSuppressed()`, tình huống đặc biệt khi tắt suppression, hệ quả trong `try-with-resources` và các mẫu thiết kế giúp tránh làm mất thông tin lỗi. Các ví dụ chạy bằng JDK **17 trở lên**.

## 1. Phân biệt cause và suppressed exception trước khi xét giới hạn

Một ngoại lệ có thể chứa cả một cause lẫn nhiều suppressed exceptions:

```text
DataLoadException("cannot load")
  |
  +-- getCause() --> IOException("read failed")
  |                     |
  |                     +-- getSuppressed()[0]
  |                           --> IOException("close failed")
  |
  +-- getSuppressed()[0]
        --> RuntimeException("secondary cleanup")
```

Sơ đồ trên là **mô hình quan hệ**, không phải cú pháp Java. Nó cho thấy hai quan hệ có thể tồn tại trên các đối tượng khác nhau trong cùng một đồ thị lỗi.

| Tiêu chí | Cause | Suppressed |
| --- | --- | --- |
| Mục đích | Giải thích tại sao ngoại lệ hiện tại xảy ra | Giữ những ngoại lệ phụ đã bị bỏ qua để một lỗi khác được truyền ra |
| API thiết lập | Constructor nhận `Throwable cause` hoặc `initCause(cause)` | `addSuppressed(exception)` |
| API đọc | `getCause()` | `getSuppressed()` |
| Số lượng qua API chuẩn | Tối đa một tham chiếu cause cho mỗi `Throwable` | Danh sách nhiều phần tử, theo thứ tự thêm |
| Giá trị khi không có | `null` | Mảng `Throwable[]` rỗng |
| Truyền `null` lúc thiết lập | Được chấp nhận trong các constructor phù hợp và `initCause(null)` | Không hợp lệ: `NullPointerException` |
| Trỏ tới chính đối tượng | `initCause(this)` bị từ chối | `addSuppressed(this)` bị từ chối |
| Cơ chế phổ biến | Bọc lỗi khi đi qua các tầng nghiệp vụ | `try-with-resources` khi đóng tài nguyên cũng phát sinh lỗi |

**Không có quan hệ tự động một-một giữa cause và suppressed.** Một ngoại lệ bị suppress không tự trở thành cause; một cause không tự xuất hiện trong `getSuppressed()`.

## 2. Self-cause: `initCause(this)` bị từ chối

`Throwable.initCause(Throwable cause)` dùng để gắn một nguyên nhân sau khi đối tượng đã được tạo, thường để hỗ trợ các lớp ngoại lệ cũ không có constructor nhận cause.

Chữ ký:

```java
public synchronized Throwable initCause(Throwable cause)
```

Theo Javadoc Java 21, nếu tham số chính là đối tượng `Throwable` đang được cập nhật, phương thức ném **`IllegalArgumentException`**. Hệ quả là:

```java
Exception failure = new Exception("failure");
failure.initCause(failure); // Sai: tự làm nguyên nhân của chính mình.
```

Đây là **đoạn cố ý gây ngoại lệ lúc chạy**, không phải lỗi biên dịch. Việc tự liên kết làm cause vô nghĩa về mặt quan hệ nhân quả và có nguy cơ tạo đồ thị chẩn đoán không hợp lệ.

Điểm so sánh quan trọng:

```java
Exception a = new Exception("same");
Exception b = new Exception("same");

a.initCause(b); // Hợp lệ: a và b là HAI đối tượng khác nhau.
```

Hai đối tượng có thể cùng kiểu, cùng message, thậm chí có stack trace tương tự; điều Java cấm là truyền **chính tham chiếu tới a** làm cause của a. Phép kiểm tra dựa trên **danh tính đối tượng**, không phải việc hai ngoại lệ có nội dung giống nhau.

### Ví dụ chạy được: tự gắn cause bị từ chối

Lưu thành `SelfCauseDemo.java`:

```java
public class SelfCauseDemo {
    public static void main(String[] args) {
        Exception failure = new Exception("failure");

        try {
            failure.initCause(failure);
        } catch (IllegalArgumentException ex) {
            System.out.println("self=" + ex.getClass().getSimpleName());
        }

        System.out.println("causeAfterRejected=" + failure.getCause());

        Exception original = new Exception("original");
        failure.initCause(original);
        System.out.println("linked=" + (failure.getCause() == original));

        try {
            failure.initCause(new Exception("replacement"));
        } catch (IllegalStateException ex) {
            System.out.println("secondInit="
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SelfCauseDemo.java
java SelfCauseDemo
```

Kết quả:

```text
self=IllegalArgumentException
causeAfterRejected=null
linked=true
secondInit=IllegalStateException
```

Phân tích:

1. Lần gọi `initCause(failure)` bị từ chối vì self-cause.
2. Đối tượng vẫn có thể gắn cause hợp lệ sau đó, vì lần gọi bị từ chối chưa đặt cause thành công.
3. Gắn `original` thành công, nên so sánh danh tính `==` trả `true`.
4. Lần gọi `initCause` kế tiếp bị từ chối vì cause đã được khởi tạo.

Không nên xử lý self-cause bằng cách âm thầm bỏ qua rồi trả lỗi không có nguyên nhân nếu sự cố đó phản ánh thiết kế exception chaining sai. Hãy xác định đúng ngoại lệ gốc và ngoại lệ wrapper.

## 3. Khởi tạo cause được phép **một lần**, kể cả khi đặt `null`

Đây là chi tiết quan trọng hơn việc `getCause()` trả gì.

Khi tạo một đối tượng bằng constructor **không nhận cause**, ví dụ `new Exception("message")`, trạng thái cause ban đầu là **chưa được khởi tạo**. Bạn có thể gọi `initCause(...)` **một lần**.

Sau khi gọi:

```java
Exception failure = new Exception("failure");
failure.initCause(null); // Hợp lệ và tiêu thụ một lần khởi tạo.
```

`getCause()` vẫn trả `null`. Nhưng một lần `initCause(...)` khác sẽ ném **`IllegalStateException`**, ngay cả khi lần đầu đã truyền `null`.

Với constructor **đã nhận tham số cause**, ví dụ `new Exception("message", null)`, cause được xem là **đã khởi tạo**, dù giá trị của nó là `null`. Vì thế `initCause(...)` cũng không được gọi sau constructor ấy.

| Cách tạo / thao tác | `getCause()` | Có được gọi `initCause(...)` sau đó không? |
| --- | --- | --- |
| `new Exception("x")` | `null` | **Có**, nếu chưa từng gọi `initCause` |
| `new Exception("x").initCause(null)` | `null` | **Không**, đã khởi tạo một lần |
| `new Exception("x", null)` | `null` | **Không**, cause đã được khai báo trong constructor |
| `new Exception("x", original)` | `original` | **Không**, đã khởi tạo trong constructor |
| `new Exception("x").initCause(original)` | `original` | **Không**, lần khởi tạo đã được dùng |

Các constructor `Throwable(Throwable cause)` và `Throwable(String message, Throwable cause)` có cùng giới hạn được nêu trong Javadoc; các lớp con có thể có constructor tương ứng.

### Ví dụ Java 17+: `null` vẫn được coi là một lần khởi tạo

Lưu thành `NullCauseDemo.java`:

```java
public class NullCauseDemo {
    public static void main(String[] args) {
        Exception deferred = new Exception("deferred");

        System.out.println("initial=" + deferred.getCause());
        deferred.initCause(null);
        System.out.println("afterNull=" + deferred.getCause());

        try {
            deferred.initCause(new Exception("late"));
        } catch (IllegalStateException ex) {
            System.out.println("repeat="
                    + ex.getClass().getSimpleName());
        }

        Exception fromConstructor = new Exception("ctor", null);
        System.out.println("ctorCause=" + fromConstructor.getCause());

        try {
            fromConstructor.initCause(new Exception("late"));
        } catch (IllegalStateException ex) {
            System.out.println("ctorAlreadyInitialized="
                    + ex.getClass().getSimpleName());
        }
    }
}
```

```bash
javac -encoding UTF-8 NullCauseDemo.java
java NullCauseDemo
```

Kết quả:

```text
initial=null
afterNull=null
repeat=IllegalStateException
ctorCause=null
ctorAlreadyInitialized=IllegalStateException
```

Bài học ở đây: **`getCause() == null` không đủ để suy ra có thể gọi `initCause(...)`**. Không có API công khai thông thường để hỏi “đã gọi initCause chưa?”; hãy chọn cách thiết kế nhất quán, ưu tiên constructor nhận cause khi tạo một ngoại lệ wrapper mới.

Một số lớp ngoại lệ có thể override `getCause()` vì lý do tương thích với cơ chế chaining cũ. Khi dùng lớp chuyên biệt, nên đọc hợp đồng của lớp đó thay vì suy đoán mọi trạng thái nội bộ từ giá trị trả về.

## 4. Self-suppression: `addSuppressed(this)` bị từ chối

Chữ ký:

```java
public final synchronized void addSuppressed(Throwable exception)
```

Hàm `addSuppressed` ghi một lỗi phụ vào danh sách của một lỗi chính. Đối tượng phụ **phải là một tham chiếu `Throwable` khác với chính đối tượng chính**.

Ví dụ sai:

```java
Exception failure = new Exception("work");
failure.addSuppressed(failure); // IllegalArgumentException
```

Đây là mã hợp lệ khi biên dịch, nhưng ném lỗi lúc thực thi. Nền tảng lỗi là **self-suppression**: một ngoại lệ vừa tự là lỗi chính, vừa tự là lỗi phụ của chính nó.

Hai ngoại lệ có **cùng message nhưng khác đối tượng** thì không phải self-suppression:

```java
Exception primary = new Exception("same text");
Exception extra = new Exception("same text");

primary.addSuppressed(extra); // Hợp lệ.
```

Sự khác nhau được xác định bằng danh tính đối tượng. `addSuppressed` **không thay thế** ngoại lệ chính, không biến `extra` thành cause và không tự ném lại `extra`. Nó chỉ bổ sung liên kết lỗi phụ.

### Ví dụ Java 17+: phân biệt self và một đối tượng khác

Lưu thành `SelfSuppressionDemo.java`:

```java
public class SelfSuppressionDemo {
    public static void main(String[] args) {
        Exception failure = new Exception("failure");

        try {
            failure.addSuppressed(failure);
        } catch (IllegalArgumentException ex) {
            System.out.println("self="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("afterSelf="
                + failure.getSuppressed().length);

        Exception secondary = new Exception("failure");
        failure.addSuppressed(secondary);

        System.out.println("count=" + failure.getSuppressed().length);
        System.out.println("sameObject="
                + (failure.getSuppressed()[0] == failure));
        System.out.println("secondaryMessage="
                + failure.getSuppressed()[0].getMessage());
    }
}
```

```bash
javac -encoding UTF-8 SelfSuppressionDemo.java
java SelfSuppressionDemo
```

Kết quả:

```text
self=IllegalArgumentException
afterSelf=0
count=1
sameObject=false
secondaryMessage=failure
```

Nhìn vào `sameObject=false` có thể thấy lỗi phụ **không phải đối tượng chính**, dù hai đối tượng có cùng thông điệp. Lần self-suppression bị từ chối không tạo phần tử mới trong danh sách.

## 5. Giá trị `null`: phân biệt chính xác ba API

Ba thao tác có chữ ký gần giống nhau nhưng hợp đồng khác nhau:

| Biểu thức | Kết quả |
| --- | --- |
| `ex.initCause(null)` | **Hợp lệ** nếu cause chưa từng được khởi tạo; tiêu thụ quyền khởi tạo |
| `new Exception("message", null)` | **Hợp lệ**; cause được xem là đã khởi tạo thành `null` |
| `ex.addSuppressed(null)` | **`NullPointerException`** |
| `ex.getCause()` khi không có/không rõ nguyên nhân | Trả `null` |
| `ex.getSuppressed()` khi không có suppressed exceptions | Trả mảng **rỗng**, không phải `null` |
| `throw null;` | Hợp lệ ở mức cú pháp nhưng khi chạy ném **`NullPointerException`** |
| `ex.setStackTrace(null)` | **`NullPointerException`**, thuộc API stack trace khác |

Lý do là mỗi API có mục đích riêng:

- **Cause `null`:** cho biết không có thông tin nguyên nhân. Có những ngoại lệ độc lập không cần cause.
- **Suppressed `null`:** không mô tả một đối tượng ngoại lệ nào. Danh sách suppressed chỉ nhận các đối tượng `Throwable` thực sự.
- **`throw null`:** câu lệnh ném cần một tham chiếu ngoại lệ; JVM tạo/ném `NullPointerException` khi biểu thức đánh giá thành `null`.

Không dùng `null` để làm dấu phân cách trong danh sách suppressed. Nếu không có lỗi phụ, để danh sách rỗng.

### Ví dụ Java 17+: xử lý `addSuppressed(null)`

Lưu thành `SuppressedNullDemo.java`:

```java
public class SuppressedNullDemo {
    public static void main(String[] args) {
        Exception primary = new Exception("main");

        try {
            primary.addSuppressed(null);
        } catch (NullPointerException ex) {
            System.out.println("nullRejected="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("empty="
                + (primary.getSuppressed().length == 0));

        try {
            throw null;
        } catch (NullPointerException ex) {
            System.out.println("throwNull="
                    + ex.getClass().getSimpleName());
        }
    }
}
```

```bash
javac -encoding UTF-8 SuppressedNullDemo.java
java SuppressedNullDemo
```

Kết quả:

```text
nullRejected=NullPointerException
empty=true
throwNull=NullPointerException
```

Ở ví dụ trên, lỗi `NullPointerException` từ `addSuppressed(null)` là lỗi về **tham số API**. Lỗi từ `throw null` là lỗi do **câu lệnh ném một giá trị tham chiếu bằng `null`**. Cả hai là cùng một loại `NullPointerException` nhưng đến từ hai cơ chế khác nhau.

## 6. Khi tắt suppression, Java vẫn kiểm tra tham số không hợp lệ

`Throwable` có constructor bảo vệ:

```java
protected Throwable(
        String message,
        Throwable cause,
        boolean enableSuppression,
        boolean writableStackTrace)
```

Một lớp ngoại lệ con có thể gọi constructor tương ứng của `Exception` để đặt `enableSuppression=false`. Khi suppression bị tắt:

- `getSuppressed()` luôn trả mảng rỗng.
- `addSuppressed(other)` với **đối tượng khác hợp lệ** không ghi gì vào danh sách.
- **Nhưng** `addSuppressed(null)` vẫn ném `NullPointerException`.
- **Và** `addSuppressed(this)` vẫn ném `IllegalArgumentException`.
- Cờ `enableSuppression` và `writableStackTrace` là hai thiết lập độc lập; tắt suppression không tự yêu cầu stack trace bị tắt.

Đây là một điểm tinh tế trong Javadoc: khi suppression bị vô hiệu hóa, `addSuppressed` chỉ không lưu dữ liệu, **không bỏ qua bước xác thực đối số**.

### Ví dụ Java 17+: kiểm tra chế độ suppression disabled

Lưu thành `DisabledSuppressionDemo.java`:

```java
public class DisabledSuppressionDemo {
    static final class NoSuppressionException extends Exception {
        NoSuppressionException(String message) {
            super(message, null, false, true);
        }
    }

    public static void main(String[] args) {
        NoSuppressionException primary =
                new NoSuppressionException("main");

        primary.addSuppressed(new Exception("ignored"));
        System.out.println("count=" + primary.getSuppressed().length);

        try {
            primary.addSuppressed(null);
        } catch (NullPointerException ex) {
            System.out.println("null="
                    + ex.getClass().getSimpleName());
        }

        try {
            primary.addSuppressed(primary);
        } catch (IllegalArgumentException ex) {
            System.out.println("self="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("finalCount="
                + primary.getSuppressed().length);
    }
}
```

```bash
javac -encoding UTF-8 DisabledSuppressionDemo.java
java DisabledSuppressionDemo
```

Kết quả:

```text
count=0
null=NullPointerException
self=IllegalArgumentException
finalCount=0
```

Lớp `NoSuppressionException` tắt danh sách suppressed ở constructor. Việc thêm một ngoại lệ hợp lệ không có tác dụng; hai đối số không hợp lệ vẫn bị từ chối. Trong triển khai nghiệp vụ thông thường, **không nên tắt suppression chỉ để giảm dung lượng log**, bởi việc này làm mất thông tin về các lỗi cleanup.

## 7. Vì sao self-suppression có thể xuất hiện trong `try-with-resources`?

Thông thường, thân `try` và `close()` tạo **hai đối tượng ngoại lệ độc lập**. Java giữ lỗi thân làm primary và gọi `primary.addSuppressed(closeFailure)`. Nếu lập trình viên cố tình dùng lại **chính cùng một đối tượng `Throwable`** ở cả hai nơi, lời gọi nội bộ có thể trở thành:

```java
Throwable primary = shared;
Throwable closeFailure = shared;

primary.addSuppressed(closeFailure); // Self-suppression!
```

Đây là mô hình giải thích, không phải mã sinh ra theo từng dòng giống hệt bởi mọi trình biên dịch. Khi `primary == closeFailure`, `addSuppressed` bị từ chối bằng `IllegalArgumentException`. Như vậy một lỗi về **cách tổ chức đối tượng ngoại lệ** có thể làm thay đổi ngoại lệ cuối cùng thoát ra thay vì giữ lỗi chính theo quy tắc thông thường.

### Chương trình Java 17+: cố ý ném lại cùng một đối tượng

Lưu thành `SameThrowableResourceDemo.java`:

```java
public class SameThrowableResourceDemo {
    static final IllegalStateException SHARED =
            new IllegalStateException("shared failure");

    static class BadResource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close");
            throw SHARED;
        }
    }

    public static void main(String[] args) {
        try (BadResource resource = new BadResource()) {
            System.out.println("body");
            throw SHARED;
        } catch (IllegalArgumentException ex) {
            System.out.println("observed="
                    + ex.getClass().getSimpleName());
            System.out.println("causeIsShared=" + (ex.getCause() == SHARED));
        }
    }
}
```

```bash
javac -encoding UTF-8 SameThrowableResourceDemo.java
java SameThrowableResourceDemo
```

Kết quả theo quy tắc `Throwable.addSuppressed` trên JDK 17+:

```text
body
close
observed=IllegalArgumentException
causeIsShared=true
```

Quá trình:

1. Thân `try` ném `SHARED`.
2. Java gọi `close()` và `close()` cũng ném **chính tham chiếu `SHARED`**.
3. Logic giữ ngoại lệ chính cố gắn `SHARED` vào danh sách suppressed của **chính `SHARED`**.
4. `addSuppressed` ném `IllegalArgumentException` do self-suppression. Trong JDK thông thường, ngoại lệ `IllegalArgumentException` ấy giữ `SHARED` làm cause.
5. `catch (IllegalArgumentException)` bắt lỗi mới. Đây không phải kết quả mong muốn của một cơ chế cleanup bình thường.

**Ví dụ này là bài học về trường hợp cần tránh**, không phải hướng dẫn xây lớp tài nguyên. Dùng một đối tượng exception toàn cục và ném đi ném lại giữa nhiều tầng có thể gây trace cũ, cause/suppressed khó quản lý và các vấn đề đa luồng.

### Cách đúng: tạo hai đối tượng lỗi riêng

Lưu thành `DistinctResourceFailureDemo.java`:

```java
public class DistinctResourceFailureDemo {
    static class BrokenResource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    public static void main(String[] args) {
        try (BrokenResource resource = new BrokenResource()) {
            throw new IllegalStateException("body failed");
        } catch (IllegalStateException ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("count=" + ex.getSuppressed().length);
            System.out.println("suppressed="
                    + ex.getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac -encoding UTF-8 DistinctResourceFailureDemo.java
java DistinctResourceFailureDemo
```

Kết quả:

```text
primary=body failed
count=1
suppressed=close failed
```

Lỗi thân và lỗi đóng là hai đối tượng mới, vì vậy `try-with-resources` có thể giữ lỗi thân làm primary và đính kèm lỗi đóng như dự kiến.

## 8. Nguyên tắc chọn ngoại lệ chính khác với tự tạo quan hệ cause

Trong `try-with-resources`, ngoại lệ đã tồn tại từ thân `try` được ưu tiên và ngoại lệ `close()` sau đó có thể bị suppress. Điều này không có nghĩa lỗi đóng **gây ra** lỗi thân.

Ngược lại, khi một tầng service bắt `IOException` rồi tạo `ServiceException`, ngoại lệ tầng thấp mới là **nguyên nhân** theo chuỗi cause của lỗi wrapper.

```java
try {
    repositoryOperation();
} catch (IOException ex) {
    throw new ServiceException("Unable to process request", ex);
}
```

Đây là **đoạn minh họa**, giả định `repositoryOperation() throws IOException` và `ServiceException` có constructor nhận cause. Đối tượng `ex` là cause của ngoại lệ mới; nó **không phải suppressed** theo mặc định.

Một thiết kế có thể chứa cả hai quan hệ:

```text
ServiceException("request failed")
    └── cause → IOException("read failed")
                   └── suppressed[0] → IOException("close failed")
```

Khi đọc log, cần xét cause chain và suppressed ở từng tầng. Không nên làm phẳng tất cả thành một message rồi đánh mất sự khác biệt giữa **lỗi gây ra lỗi khác** và **lỗi phát sinh phụ trong cleanup**.

## 9. `getSuppressed()` trả mảng độc lập và có thể rỗng

Với `Throwable.getSuppressed()`:

- Nếu chưa có ngoại lệ bị suppress, trả về mảng **rỗng**.
- Nếu suppression bị tắt, cũng trả về mảng **rỗng**, kể cả khi đã thử thêm một ngoại lệ hợp lệ.
- Mảng trả về là dữ liệu để đọc; **thay đổi các phần tử trên mảng trả về không sửa danh sách suppressed đang lưu**.
- Các phần tử mảng là **tham chiếu tới các đối tượng `Throwable` thực tế**, không phải bản sao sâu của từng đối tượng. Các đối tượng lỗi liên quan vẫn có thể có cause/stack trace riêng.

Đoạn minh họa trong một phương thức:

```java
Exception root = new Exception("root");
Exception first = new Exception("first");
root.addSuppressed(first);

Throwable[] snapshot = root.getSuppressed();
snapshot[0] = new Exception("replacement");

System.out.println(root.getSuppressed()[0].getMessage());
// In ra: first
```

Không được dùng `getSuppressed() == null` để kiểm tra không có lỗi phụ; API công bố mảng, nên dùng `getSuppressed().length == 0`.

Nếu muốn lưu danh sách thông tin lỗi vào cấu trúc khác, cần phân biệt **sao chép mảng tham chiếu** với **sao chép các đối tượng Throwable**. Không nên tự sửa dữ liệu chẩn đoán của các ngoại lệ đang được chia sẻ chỉ để đổi cách hiển thị log.

## 10. Có thể có cùng một đối tượng suppressed nhiều lần không?

`addSuppressed` cấm **một ngoại lệ suppress chính nó**, nhưng Javadoc không đặt yêu cầu tự động khử trùng lặp theo message hoặc danh tính đối với **các đối tượng khác với chủ thể**.

Ví dụ:

```java
Exception primary = new Exception("primary");
Exception sameExtra = new Exception("extra");

primary.addSuppressed(sameExtra);
primary.addSuppressed(sameExtra);

System.out.println(primary.getSuppressed().length); // 2
```

Đây là đoạn minh họa trong phương thức. Hai lần thêm cùng một đối tượng **khác với primary** được ghi thành hai phần tử theo thứ tự gọi. Điều đó không gây lỗi self-suppression vì `sameExtra != primary`.

Tuy nhiên, lặp cùng một lỗi phụ trong danh sách có thể làm log khó đọc và gây hiểu nhầm là hai lần lỗi độc lập. Nếu tự xây bộ thu thập lỗi, hãy quyết định rõ chính sách khử lặp bằng **danh tính** hay **sự kiện**, không dựa đơn giản vào việc hai thông điệp lỗi bằng nhau.

## 11. Cause khác `null` nhưng truy cập không an toàn vẫn gây NPE

Một lỗi thực hành rất phổ biến:

```java
Exception ex = new Exception("independent");
System.out.println(ex.getCause().getMessage()); // NPE khi cause == null
```

Đây là ví dụ **cố ý gây lỗi lúc chạy**. `getCause()` có thể trả `null`; phải kiểm tra trước khi dereference.

Mẫu hợp lệ:

```java
Throwable cause = ex.getCause();
if (cause == null) {
    System.out.println("No known cause");
} else {
    System.out.println(cause.getMessage());
}
```

Trong ví dụ sau, `ex` đã tồn tại trong scope. Với `getSuppressed()` thì không cần kiểm tra array `null` theo hợp đồng chuẩn, nhưng vẫn phải kiểm tra **độ dài** trước khi truy cập phần tử đầu:

```java
Throwable[] extras = ex.getSuppressed();
if (extras.length > 0) {
    System.out.println(extras[0].getMessage());
}
```

Không nên viết `ex.getSuppressed()[0]` mà không kiểm tra nếu ứng dụng có thể không tạo lỗi phụ. Những mã như vậy thường làm phát sinh `ArrayIndexOutOfBoundsException` trong lúc đang xử lý một ngoại lệ khác, che khuất lỗi cần phân tích.

## 12. Thiết kế exception mới: chọn constructor nhận cause thay vì `initCause` muộn

Khi có quyền thiết kế một lớp ngoại lệ riêng, cách thường rõ ràng nhất là khai báo constructor nhận cause:

```java
class DataLoadException extends Exception {
    DataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **lớp hợp lệ**, không chứa `main` để chạy độc lập. Khi dùng:

```java
throw new DataLoadException("Cannot load configuration", original);
```

Ngoại lệ mới đã có cause chính xác từ lúc tạo. Không cần gọi thêm `initCause()` và cũng tránh nguy cơ vô tình khởi tạo hai lần.

`initCause()` vẫn hữu ích khi bạn buộc phải tích hợp một kiểu ngoại lệ cũ không cung cấp constructor nhận cause. Nếu dùng, hãy gọi **ngay sau khi khởi tạo** và chỉ một lần:

```java
LegacyException wrapper = new LegacyException("operation failed");
wrapper.initCause(original);
throw wrapper;
```

Đây là **mẫu minh họa** với `LegacyException` không có constructor cause, có chữ ký `initCause` phù hợp, và `original` là một `Throwable` khác với `wrapper`. Việc trì hoãn gắn cause cho đến sau khi ngoại lệ đã đi qua nhiều tầng làm trạng thái lỗi khó đoán hơn.

**Không chuyển một ngoại lệ thành cause của chính nó** để “giữ nguyên stack trace”. Nếu cần ném lại đúng lỗi đó, dùng `throw original;` (khi kiểu checked phù hợp với chữ ký phương thức). Nếu cần thêm ngữ cảnh, tạo **wrapper khác** và truyền `original` làm cause.

## 13. Các trường hợp khác về chu trình tham chiếu

Java cấm *self-cause trực tiếp* qua `initCause(this)` và *self-suppression trực tiếp* qua `addSuppressed(this)`. Không nên suy ra rằng các API này **tự kiểm tra mọi chu trình tham chiếu phức tạp**.

Ví dụ, hai đối tượng được tạo chưa có cause có thể được liên kết sai thành vòng gián tiếp:

```java
Exception a = new Exception("A");
Exception b = new Exception("B");

a.initCause(b);
b.initCause(a); // Không phải b.initCause(b); tạo chu trình gián tiếp.
```

Đây là mã minh họa **không nên áp dụng**. Cả hai lời gọi đều không truyền chính đối tượng nhận vào làm tham số, nên giới hạn self-cause trực tiếp không tự bảo vệ khỏi vòng gián tiếp kiểu A → B → A. Các công cụ in lỗi có thể phát hiện và đánh dấu chu trình để tránh in vô hạn, nhưng việc tạo quan hệ không có ý nghĩa này vẫn làm thông tin điều tra kém tin cậy.

Tương tự, tránh tạo những mạng liên kết cause/suppressed qua lại giữa nhiều đối tượng nếu không có ý nghĩa thật về nguyên nhân và cleanup. Xử lý tập hợp lỗi theo mô hình **hướng từ lỗi báo cáo về nguyên nhân hoặc lỗi phụ**, không biến nó thành một đồ thị vòng tùy ý.

Nếu cần nhiều lỗi độc lập trong một bước xử lý, dùng một ngoại lệ tổng hợp với các suppressed exceptions **khác đối tượng chính**, hoặc một cấu trúc kết quả tổng hợp có chủ đích. Không tạo cause giả để xếp nhiều lỗi ngang hàng.

## 14. Những sai lầm thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| `ex.initCause(ex)` | `IllegalArgumentException` do self-cause | Tạo wrapper khác và gắn `ex` làm cause của wrapper |
| `ex.addSuppressed(ex)` | `IllegalArgumentException` do self-suppression | Chỉ thêm đối tượng lỗi phụ riêng biệt |
| `ex.addSuppressed(null)` | `NullPointerException` | Không thêm gì nếu không có lỗi phụ |
| `ex.getCause() == null` rồi mặc định có thể gọi `initCause` | Có thể gặp `IllegalStateException` | Gắn cause lúc tạo hoặc theo dõi chính sách khởi tạo một lần |
| `new Exception("x", null)` rồi gọi `initCause(real)` | `IllegalStateException` | Dùng constructor với cause thật ngay từ đầu |
| `initCause(null)` rồi cố thay cause sau | `IllegalStateException` | Chỉ khởi tạo once; dùng null khi thực sự không có cause |
| Tắt suppression rồi nghĩ đối số `null`/self được bỏ qua | Vẫn gặp NPE/IAE | Luôn truyền một đối tượng lỗi hợp lệ, khác primary |
| Dùng chung một exception instance cho thân `try` và `close()` | Có thể gây self-suppression từ mã đóng tự động | Tạo ngoại lệ mới cho mỗi sự cố độc lập |
| Chỉ kiểm tra `getSuppressed() != null` | Không kiểm tra được danh sách rỗng | Kiểm tra `getSuppressed().length > 0` |
| Truy cập `getCause().getMessage()` không kiểm tra | NPE khi cause vắng mặt | Kiểm tra `getCause() != null` trước |
| Truy cập `getSuppressed()[0]` khi không có lỗi phụ | `ArrayIndexOutOfBoundsException` | Kiểm tra độ dài mảng |
| Bọc lỗi chỉ bằng message | Mất stack trace/cause/suppressed gốc | Truyền đối tượng gốc vào constructor nhận cause |
| Tạo chu trình cause gián tiếp | Dấu vết khó đọc, có thể xuất hiện cảnh báo circular reference | Giữ đồ thị nguyên nhân có ý nghĩa và không có vòng |
| Thêm cùng một lỗi phụ nhiều lần | Có thể nhân bản cùng đối tượng trong log | Xác định chính sách deduplication khi tổng hợp |

## 15. Checklist khi phân tích một lỗi có cause và suppressed

1. **Xác định đối tượng đang được ném:** primary là đối tượng nào, kiểu gì, message nào?
2. **Đọc cause:** `getCause()` có `null` không? Nếu có, đừng suy ra rằng `initCause` chưa từng chạy.
3. **Đọc suppressed:** `getSuppressed()` có bao nhiêu phần tử? Các đối tượng phụ có được tạo ở thời điểm cleanup/đóng tài nguyên không?
4. **Kiểm tra danh tính:** có phải mã đang cố thêm chính primary vào danh sách suppressed hoặc gắn nó làm cause của chính nó không?
5. **Rà soát khởi tạo:** cause được thiết lập qua constructor hay `initCause`? Có gọi nhiều lần không?
6. **Xét các giá trị null:** cause có thể không có; suppressed không được thêm `null`; các câu lệnh `throw null` và dereference `getCause()` cần xử lý riêng.
7. **Xem chế độ suppression:** lớp ngoại lệ custom có tắt suppression không? Việc thiếu suppressed trong log đôi khi do chính thiết kế lớp.
8. **Xem logic `try-with-resources`:** lỗi thân/đóng có tạo các đối tượng riêng biệt không? Có phát sinh self-suppression khi tái ném một singleton exception không?
9. **Xem ranh giới tầng:** nếu bọc lỗi, wrapper có giữ nguyên đối tượng gốc làm cause không?
10. **Chỉ log bằng thông tin đáng tin:** `printStackTrace()` có thể hiển thị cause và suppressed, nhưng thông tin liên kết do code cung cấp vẫn cần được thiết kế đúng.

## 16. Tự kiểm tra

**Câu 1.** `ex.initCause(ex)` phát sinh lỗi gì?  
**`IllegalArgumentException`** vì một đối tượng không thể là cause trực tiếp của chính nó.

**Câu 2.** `ex.addSuppressed(ex)` phát sinh lỗi gì?  
**`IllegalArgumentException`** vì self-suppression.

**Câu 3.** `ex.initCause(null)` có hợp lệ không? Có thể gọi lại `initCause(real)` sau đó không?  
**Hợp lệ nếu trước đó cause chưa được khởi tạo. Không gọi lại được**: lần đầu đã tiêu thụ quyền khởi tạo.

**Câu 4.** `new Exception("x", null)` có cho phép gắn một cause khác sau này bằng `initCause` không?  
**Không.** Constructor nhận cause đã khởi tạo cause thành `null`.

**Câu 5.** `ex.addSuppressed(null)` ném gì?  
**`NullPointerException`**. Danh sách rỗng được biểu diễn bằng mảng rỗng từ `getSuppressed()`, không bằng phần tử null.

**Câu 6.** Khi `enableSuppression=false`, gọi `addSuppressed(null)` có bị từ chối không?  
**Có**, vì API vẫn xác thực đối số.

**Câu 7.** Hai ngoại lệ khác đối tượng nhưng cùng message có thể tạo quan hệ cause hoặc suppressed không?  
**Có** khi quan hệ đó có ý nghĩa: điều bị cấm ở đây là đối tượng tự liên kết với chính nó, không phải trùng message.

**Câu 8.** Trong `try-with-resources`, thân và `close()` cùng ném một singleton exception instance có thể gây lỗi gì?  
**Self-suppression**, có thể dẫn tới `IllegalArgumentException` khi Java cố gắn ngoại lệ vào chính nó.

**Câu 9.** `getSuppressed() == null` có phải cách kiểm tra không có lỗi phụ?  
**Không.** Phương thức trả mảng, thường rỗng nếu không có lỗi phụ.

**Câu 10.** `getCause() == null` có chứng minh cause chưa từng được khởi tạo không?  
**Không.** Cause có thể đã được đặt thành `null` qua constructor hoặc `initCause(null)`.

## Tài liệu tham khảo

- [Java SE 21 — `Throwable.initCause()`, `getCause()`, `addSuppressed()`, `getSuppressed()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html).
- [JLS Java SE 21 — §14.20.3: `try-with-resources` và suppressed exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS Java SE 21 — §14.18: `throw` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.18).

**Ghi nhớ:** Java cho phép cause bằng `null` nhưng chỉ cho phép khởi tạo cause **một lần**; `getCause()==null` không chứng minh trạng thái còn được khởi tạo. Trong khi đó, suppressed exception phải là một đối tượng `Throwable` **khác chính đối tượng chứa nó và khác `null`**. Tuân thủ những giới hạn này giúp bảo toàn thông tin lỗi gốc và tránh tạo ra ngoại lệ mới ngay trong quá trình xử lý lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-008">Đọc cây ngoại lệ thay vì chỉ đọc dòng thông điệp cuối</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc cây ngoại lệ thay vì chỉ đọc dòng thông điệp cuối trong Java

Trong Java, một thông báo lỗi như `"import failed"` thường chỉ mô tả **thất bại ở tầng đang xử lý**, không phải toàn bộ nguyên nhân. Phía sau một ngoại lệ có thể có chuỗi **`cause`** dẫn tới lỗi kỹ thuật ban đầu, và tại mỗi mắt xích còn có những **`suppressed exceptions`** phát sinh khi đóng tài nguyên hoặc khi hệ thống cố hoàn tất một công việc phụ.

Vì vậy, muốn chẩn đoán chính xác, đừng chỉ đọc một dòng `getMessage()`, dòng đầu tiên của stack trace, hay dòng `Caused by` cuối cùng. Hãy nhìn vào **toàn bộ đồ thị ngoại lệ** cùng các vị trí trong stack trace.

**Ví dụ vấn đề:**

```text
java.lang.IllegalStateException: Cannot import batch
    at ... <frames of service>
    Suppressed: java.lang.IllegalArgumentException: audit cleanup failed
        at ... <frames of cleanup>
Caused by: java.io.IOException: source read failed
    at ... <frames of I/O>
    Suppressed: java.io.IOException: channel close failed
        at ... <frames of close>
```

Đây là **mẫu định dạng minh họa**, không phải output cố định có số dòng thật. Đọc duy nhất dòng `source read failed` sẽ bỏ sót **hai lỗi phụ**: lỗi cleanup của service và lỗi đóng channel trên đối tượng `IOException`. Ngược lại, chỉ nhìn lỗi cleanup cũng không đủ để kết luận nguyên nhân thất bại ban đầu.

## 1. Từ chuỗi nguyên nhân tới cây ngoại lệ

`Throwable` cung cấp hai quan hệ cơ bản giữa các đối tượng lỗi:

- **`getCause()`**: trả về **một** `Throwable` làm nguyên nhân trực tiếp, hoặc `null`. Lần tiếp tục `getCause()` tạo thành *cause chain*.
- **`getSuppressed()`**: trả về mảng `Throwable[]` gồm **không, một hoặc nhiều** lỗi được ghi nhận bổ sung bên cạnh ngoại lệ chính. Mỗi lỗi suppressed cũng có thể có cause hoặc suppressed của riêng nó.

Một mô hình thường gặp:

```text
IllegalStateException: import failed             [ROOT: ngoại lệ bên ngoài]
├── CAUSE ── IOException: read failed
│            └── SUPPRESSED ── IOException: channel close failed
├── SUPPRESSED[0] ── IllegalStateException: rollback failed
└── SUPPRESSED[1] ── IOException: audit log close failed
                           └── CAUSE ── IOException: device full
```

Ở đây, **ROOT** là **ngoại lệ ngoài cùng người gọi đang giữ**, không nhất thiết là “root cause” theo cách nói thông thường. Những nhãn `CAUSE` và `SUPPRESSED` mô tả **các cạnh khác nhau**, không phải mọi nút đều có vai trò ngang nhau.

Hình trên gọi là “cây” để dễ đọc, nhưng nói chính xác hơn, các tham chiếu `Throwable` tạo thành một **đồ thị**: cùng một đối tượng có thể được tham chiếu từ nhiều nơi và các lớp ngoại lệ tùy chỉnh có thể tạo quan hệ vòng. Trình duyệt lỗi tổng quát nên tránh duyệt lặp vô hạn.

### Hai từ “gốc” dễ gây nhầm

| Thuật ngữ | Cách hiểu hữu ích |
| --- | --- |
| **Ngoại lệ ngoài cùng** | Đối tượng đang được ném tới handler/caller hiện tại; ví dụ `IllegalStateException("import failed")` |
| **Nguyên nhân trực tiếp** | Đối tượng mà `outer.getCause()` trả về |
| **Nguyên nhân sâu nhất** | Nút cuối cùng khi lần theo liên tục `getCause()`; chưa chắc là lý do nghiệp vụ duy nhất |
| **Ngoại lệ chính** | Lỗi được ưu tiên giữ khi đồng thời có lỗi trong thao tác chính và lỗi phụ, đặc biệt trong `try-with-resources` |
| **Ngoại lệ suppressed** | Lỗi đính kèm theo quan hệ phụ, không tự trở thành cause |

**Cảnh báo:** đừng lấy “nguyên nhân sâu nhất” rồi tự động bỏ tất cả phần còn lại. Một `IOException` sâu có thể chỉ báo “connection reset”, nhưng tầng ngoài mới biết **tác vụ, đơn hàng hoặc tài nguyên nào** đã bị ảnh hưởng. Suppressed có thể báo thêm sự cố dọn dẹp hoặc tác động chưa hoàn tất.

## 2. Một `Throwable` mang những trường nào?

Đối tượng `java.lang.Throwable` cho phép kiểm tra:

| API | Kết quả | Cần lưu ý |
| --- | --- | --- |
| `getClass().getName()` | Tên lớp ngoại lệ runtime | Khác với tên lớp trong khai báo `catch` |
| `getMessage()` | Thông điệp chi tiết | Có thể `null`; chưa chắc an toàn để công khai |
| `getLocalizedMessage()` | Thông điệp địa phương hóa | Mặc định thường như `getMessage()`; subtype có thể ghi đè |
| `toString()` | Thường là tên lớp cùng message | Không in đầy đủ cây lỗi và frames |
| `getCause()` | Một ngoại lệ nguyên nhân hoặc `null` | Chỉ đi theo nhánh cause |
| `getSuppressed()` | Mảng lỗi phụ, có thể rỗng | Có thể có suppressed ở **bất kỳ nút nào** |
| `getStackTrace()` | Mảng `StackTraceElement[]` | Dấu vết tại thời điểm được ghi, có thể thiếu frame |
| `printStackTrace()` | In biểu diễn nhiều lớp lỗi | Mặc định ra `System.err`, không trả `String` |

Ví dụ:

```java
IllegalStateException top =
        new IllegalStateException(
                "service failed",
                new java.io.IOException("read failed"));

System.out.println(top.getMessage());
System.out.println(top.getCause().getMessage());
System.out.println(top.getSuppressed().length);
```

Đây là một đoạn Java **minh họa**, cần đặt trong lớp/phương thức. Với đối tượng trên:

```text
service failed
read failed
0
```

Đọc riêng `top.getMessage()` chỉ thấy `service failed`. Đọc riêng cause chỉ thấy `read failed`. Không phần nào tự kể đầy đủ diễn biến; cần kết hợp.

## 3. Phân biệt `getMessage()`, `toString()` và `printStackTrace()`

Ba mức biểu diễn hay bị nhầm:

**`getMessage()` — một chuỗi mô tả:** 

```java
System.out.println(ex.getMessage());
```

**`toString()` — tóm tắt loại lỗi kèm message:**

```java
System.out.println(ex);
```

**`printStackTrace()` — trình bày thông tin lỗi, call stack và các quan hệ:**

```java
ex.printStackTrace();
```

Các câu trên là ví dụ nếu `ex` là biến kiểu `Throwable` hợp lệ. Với hành vi mặc định của `Throwable`, `toString()` chứa tên lớp và message nếu có. `printStackTrace()` xuất ra **stderr** và trả `void`; nó có thể in nhiều đoạn `Suppressed:`, `Caused by:` và các frame `at ...`.

Không có phép gọi:

```java
// Cố ý không biên dịch: printStackTrace() trả void.
String trace = ex.printStackTrace();
```

Nếu cần biểu diễn dạng chuỗi, có thể ghi vào `StringWriter`:

```java
java.io.StringWriter buffer = new java.io.StringWriter();
ex.printStackTrace(new java.io.PrintWriter(buffer));
String trace = buffer.toString();
```

Đây là đoạn minh họa, không nên tự chuyển lỗi thành chuỗi ở mọi tầng. Trong ứng dụng thật, một logger hỗ trợ nhận `Throwable` trực tiếp thường thích hợp hơn.

### Bẫy “dòng cuối cùng” trong stack trace

Một bản `printStackTrace` có thể kết thúc bằng `... 5 more`, một frame của cause sâu, hoặc một nhánh suppressed lồng nhau, tùy cấu trúc. **Dòng cuối không phải quy ước đại diện cho nguyên nhân cuối cùng**. Ký hiệu `... N more` chỉ nói rằng các frame trùng đuôi đã được rút gọn khi trình bày.

Muốn hiểu lỗi, đọc **nhãn quan hệ và các đối tượng** chứ không dựa trên vị trí một dòng.

## 4. Cách đọc stack trace theo cấu trúc thay vì từ dưới lên một cách máy móc

Khi bắt gặp một bản trace nhiều tầng, quy trình hữu ích là:

1. **Đọc ngoại lệ ngoài cùng:** loại lỗi, thông điệp, thao tác nào được báo thất bại.
2. **Xem những stack frames liên quan:** chỗ nào tạo/ném ngoại lệ, lớp nào gọi tới nó. Không nhầm frame của wrapper với frame của cause.
3. **Lần theo `Caused by`:** đọc từng nấc bọc lỗi để biết tầng nào chuyển ngữ nghĩa; chú ý cause có thể có message và stack riêng.
4. **Quay lại các nhánh `Suppressed` ở từng nút:** kiểm tra lỗi khi đóng tài nguyên, rollback, flush, dọn dẹp hoặc thao tác phụ.
5. **Đánh giá mức độ ảnh hưởng:** lỗi phụ có thể ảnh hưởng dữ liệu hoặc tài nguyên dù không phải nguyên nhân đầu tiên.
6. **Đối chiếu log có cấu trúc:** request ID, tên tác vụ, trạng thái giao dịch, phiên bản dịch vụ, timestamp, nhưng tránh sao chép dữ liệu nhạy cảm vào báo cáo lỗi.

Đừng chỉ làm bước 3 rồi dừng; mỗi nút cause lại có thể chứa các nhánh suppressed.

### Thứ tự hiển thị không phải thứ tự thời gian tuyệt đối

`Throwable.printStackTrace()` trình bày lỗi theo **cấu trúc liên kết** với indentation và các frame. Nó không phải hệ thống ghi sự kiện theo thời gian. Một lỗi cause được tạo sớm hơn wrapper; lỗi suppressed có thể xảy ra sau thao tác chính; nhưng **vị trí in trong văn bản không đủ để suy ra toàn bộ dòng thời gian** giữa các thread hoặc các hoạt động bất đồng bộ.

Nếu cần phân tích diễn biến chính xác, dùng thêm timestamp/trace ID có cấu trúc tại những ranh giới quan trọng.

## 5. `Caused by` thể hiện việc bọc ngoại lệ như thế nào?

Giả sử tầng đọc dữ liệu ném `IOException`, tầng nghiệp vụ muốn biểu diễn lỗi ở cấp tác vụ:

```java
static void loadProfile() {
    try {
        readProfileFile();
    } catch (java.io.IOException cause) {
        throw new IllegalStateException(
                "profile loading failed",
                cause);
    }
}
```

Đây là **phương thức minh họa**, giả định `readProfileFile()` tồn tại và ném `IOException`.

Tại tầng ngoài, đối tượng đang được ném là `IllegalStateException`, có stack trace tại nơi tạo wrapper. `getCause()` dẫn tới `IOException` gốc với stack trace riêng. Khi in đầy đủ, Java sẽ biểu diễn liên kết này dưới nhãn `Caused by:`.

**Phân biệt:**

- `throw original;`: ném lại **cùng đối tượng**, thường giữ stack trace đã được ghi.
- `throw new X("context", original);`: tạo ngoại lệ **mới** có cause là `original`.
- `throw new X("context");`: tạo ngoại lệ mới mà **không tự bảo toàn cause**.

Caller bắt theo **kiểu runtime của đối tượng ngoài cùng**, không tự động bắt một cause bên trong theo kiểu của cause đó. Chẳng hạn `catch (IOException)` không bắt một `IllegalStateException` chỉ vì cause là `IOException`.

### Root cause có luôn là nút cuối của `getCause()`?

“Nút sâu nhất trong cause chain” là định nghĩa kỹ thuật của một điểm cuối trên nhánh cause. Nhưng về vận hành, đó **không luôn là câu trả lời hoàn chỉnh**:

- `TimeoutException` sâu nhất cho biết timeout; service ngoài cho biết **thao tác nào** bị timeout.
- `NumberFormatException` sâu nhất cho biết không parse được số; tầng nghiệp vụ cho biết **trường dữ liệu nào** sai.
- Một thư viện có thể bọc sai hoặc che mất cause.
- Nhánh suppressed có thể hé lộ lỗi đóng/rollback thứ hai cần xử lý độc lập.
- Cùng một lỗi kỹ thuật có thể dẫn đến nhiều hậu quả nghiệp vụ khác nhau.

Vì vậy, hãy đọc cause chain để hiểu **nguồn phát sinh**, còn wrapper và suppressed giúp hiểu **ngữ cảnh và hậu quả bổ sung**.

## 6. `Suppressed` là nhánh phụ, không phải một cause thứ hai

Một ngoại lệ có thể mang nhiều suppressed exceptions:

```java
IllegalStateException primary =
        new IllegalStateException("main operation failed");

primary.addSuppressed(
        new java.io.IOException("close reader failed"));
primary.addSuppressed(
        new java.io.IOException("close audit stream failed"));
```

Đoạn trên là **mã minh họa**. Ở đây:

- `primary.getCause()` là `null`.
- `primary.getSuppressed().length` là `2`.
- Mỗi suppressed exception có thể lại có `getCause()` hoặc `getSuppressed()` riêng.

`getSuppressed()` không phải `getCause()`. Một lỗi đóng reader **không nhất thiết là nguyên nhân** làm thao tác chính thất bại; nó phát sinh trong quá trình dọn dẹp và được giữ lại thay vì che khuất lỗi chính.

### Vì sao suppressed có thể nằm trên một cause, không phải ở ngoại lệ ngoài?

Giả sử tầng thấp dùng `try-with-resources` và ném `IOException("read failed")`. Khi `close()` ném lỗi, Java gắn lỗi đóng vào **đối tượng IOException đó**. Sau đó tầng service bọc IOException trong `IllegalStateException("service failed", cause)`.

Sơ đồ:

```text
IllegalStateException("service failed")
└── CAUSE → IOException("read failed")
             └── SUPPRESSED → IOException("close failed")
```

Lúc này:

```java
outer.getSuppressed().length
```

có thể là `0`, nhưng:

```java
outer.getCause().getSuppressed().length
```

lại là `1`. Đây là lý do **chỉ gọi `getSuppressed()` trên wrapper ngoài cùng có thể bỏ sót lỗi**.

Hai câu trên là biểu thức minh họa, giả định `outer` tham chiếu đúng ngoại lệ và cause không null.

### Lỗi phụ vẫn cần quan tâm

Không phải suppressed luôn “không quan trọng”. Ví dụ:

- Lỗi đóng output stream có thể báo dữ liệu chưa được ghi hết.
- Lỗi rollback hoặc dọn dẹp có thể để tài nguyên ở trạng thái không xác định.
- Lỗi đóng audit channel có thể khiến một số dữ liệu kiểm toán không được ghi nhận.
- Nhiều lần đóng resource cùng thất bại có thể báo sự cố cơ sở hạ tầng rộng hơn lỗi chính.

**Vai trò suppressed là giữ thông tin theo đúng ngữ nghĩa**, không phải quyết định tự động bỏ qua vấn đề.

## 7. Ví dụ Java 17+ hoàn chỉnh: dựng và duyệt cây ngoại lệ

Để hiển thị cấu trúc ổn định mà không lệ thuộc số dòng stack trace, chương trình sau tạo một đồ thị ngoại lệ với cause và suppressed ở nhiều tầng. Nó dùng `IdentityHashMap` để nhận diện **cùng một đối tượng Throwable**, tránh lặp vô hạn nếu đồ thị chứa tham chiếu vòng hoặc dùng chung một đối tượng.

Lưu tệp **`ThrowableTreeDemo.java`**:

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ThrowableTreeDemo {

    static Throwable buildFailure() {
        IOException read =
                new IOException("storage read failed");

        read.addSuppressed(
                new IOException("channel close failed"));

        IllegalStateException service =
                new IllegalStateException(
                        "load profile failed", read);

        service.addSuppressed(
                new IllegalArgumentException(
                        "unlock cleanup failed"));

        IllegalStateException audit =
                new IllegalStateException("audit close failed");

        audit.addSuppressed(
                new IOException("audit stream close failed"));

        service.addSuppressed(audit);
        return service;
    }

    static void printTree(
            Throwable error,
            String indent,
            String relation,
            Set<Throwable> visited) {

        if (error == null) {
            return;
        }

        System.out.println(
                indent + relation
                + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage()));

        if (!visited.add(error)) {
            System.out.println(indent + "  [already visited]");
            return;
        }

        Throwable cause = error.getCause();
        if (cause != null) {
            printTree(cause, indent + "  ", "CAUSE -> ", visited);
        }

        Throwable[] suppressed = error.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            printTree(
                    suppressed[i],
                    indent + "  ",
                    "SUPPRESSED[" + i + "] -> ",
                    visited);
        }
    }

    public static void main(String[] args) {
        Throwable error = buildFailure();

        Set<Throwable> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<Throwable, Boolean>());

        printTree(error, "", "ROOT -> ", visited);

        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        String trace = buffer.toString();

        System.out.println(
                "TRACE_HAS_CAUSE="
                + trace.contains(
                        "Caused by: java.io.IOException: storage read failed"));

        System.out.println(
                "TRACE_HAS_SUPPRESSED="
                + trace.contains(
                        "Suppressed: java.lang.IllegalArgumentException: "
                        + "unlock cleanup failed"));

        System.out.println(
                "CAUSE_SUPPRESSED_COUNT="
                + error.getCause().getSuppressed().length);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ThrowableTreeDemo.java
java ThrowableTreeDemo
```

**Stdout dự kiến chính xác:**

```text
ROOT -> IllegalStateException: load profile failed
  CAUSE -> IOException: storage read failed
    SUPPRESSED[0] -> IOException: channel close failed
  SUPPRESSED[0] -> IllegalArgumentException: unlock cleanup failed
  SUPPRESSED[1] -> IllegalStateException: audit close failed
    SUPPRESSED[0] -> IOException: audit stream close failed
TRACE_HAS_CAUSE=true
TRACE_HAS_SUPPRESSED=true
CAUSE_SUPPRESSED_COUNT=1
```

### Vì sao ví dụ này thực sự duyệt “cây” thay vì chỉ đi theo một nhánh?

- `printTree` nhận một **nút `Throwable`**.
- Nếu có `getCause()`, chương trình đi vào **nhánh nguyên nhân**.
- Sau đó nó duyệt **toàn bộ mảng `getSuppressed()`** của **chính nút đang xét**.
- Mỗi cause hoặc suppressed lại được duyệt bằng cùng một hàm, nên các ngoại lệ phụ ở cấp sâu **không bị bỏ sót**.
- `visited` dùng đồng nhất tham chiếu (*reference identity*), không dùng `equals()` hay so sánh message. Hai lỗi khác nhau có thể có cùng kiểu và cùng thông điệp; chúng vẫn là hai đối tượng cần ghi nhận.
- Việc in `[already visited]` nếu gặp lại cùng đối tượng giúp tránh duyệt vòng/lặp vô hạn trong các cấu trúc bất thường.

Chương trình cũng lấy raw `printStackTrace` vào `StringWriter` rồi **chỉ kiểm tra dấu hiệu ổn định** như `Caused by` và `Suppressed`. Số dòng tệp, package hay frame cụ thể có thể thay đổi nên không hard-code toàn bộ bản stack trace vào stdout.

### Thứ tự duyệt do ứng dụng lựa chọn

Ví dụ in **cause trước, suppressed sau** để ưu tiên phân tích nguyên nhân chính. Đây là **quy ước trình bày do ví dụ chọn**, không phải cam kết rằng `Throwable.printStackTrace()` in theo cùng thứ tự. Hãy luôn phân biệt **đồ thị dữ liệu** với **cách trình bày văn bản** của từng công cụ.

Trong hệ thống thực, một bộ duyệt tổng quát còn nên giới hạn độ sâu, số nút và dung lượng message, để một đồ thị lỗi lớn hoặc dữ liệu không đáng tin không làm log quá tải.

## 8. Ví dụ Java 17+ hoàn chỉnh: lỗi đọc, lỗi đóng và wrapper ở tầng trên

Một tình huống tự nhiên tạo nhánh suppressed là `try-with-resources`. Khi phần thân ném lỗi đọc và `close()` cũng thất bại, Java bảo toàn lỗi đọc làm ngoại lệ chính và giữ lỗi đóng trong `getSuppressed()`.

Lưu tệp **`ResourceCauseTreeDemo.java`**:

```java
import java.io.IOException;

public class ResourceCauseTreeDemo {

    static final class TrackedResource implements AutoCloseable {
        private final String name;

        TrackedResource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
            throw new IllegalStateException(
                    "close failed: " + name);
        }
    }

    static void readBatch() throws IOException {
        try (TrackedResource first = new TrackedResource("A");
             TrackedResource second = new TrackedResource("B")) {

            System.out.println("work: reading");
            throw new IOException("source read failed");
        }
    }

    static void importBatch() {
        try {
            readBatch();
        } catch (IOException cause) {
            throw new IllegalStateException(
                    "batch import failed", cause);
        }
    }

    public static void main(String[] args) {
        try {
            importBatch();
        } catch (IllegalStateException outer) {
            System.out.println("OUTER: " + outer.getMessage());
            System.out.println(
                    "OUTER_SUPPRESSED="
                    + outer.getSuppressed().length);

            Throwable cause = outer.getCause();

            System.out.println(
                    "CAUSE: " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());

            System.out.println(
                    "CAUSE_SUPPRESSED="
                    + cause.getSuppressed().length);

            for (Throwable secondary : cause.getSuppressed()) {
                System.out.println(
                        "SECONDARY: " + secondary.getMessage());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ResourceCauseTreeDemo.java
java ResourceCauseTreeDemo
```

**Stdout dự kiến chính xác:**

```text
open A
open B
work: reading
close B
close A
OUTER: batch import failed
OUTER_SUPPRESSED=0
CAUSE: IOException: source read failed
CAUSE_SUPPRESSED=2
SECONDARY: close failed: B
SECONDARY: close failed: A
```

### Diễn biến chính xác

1. `TrackedResource A` và `B` được tạo; mỗi constructor in tên tương ứng.
2. Phần thân `try` ném `IOException("source read failed")`.
3. Java gọi `close()` theo **thứ tự ngược khai báo**: B trước, A sau.
4. Cả hai `close()` ném `IllegalStateException`. Vì thân `try` đã có ngoại lệ chính, lỗi đóng được **gắn suppressed trên chính đối tượng `IOException`**.
5. Tầng `importBatch` bắt `IOException` đó và bọc thành `IllegalStateException("batch import failed", cause)`.
6. Handler ở `main` nhận wrapper. `outer.getSuppressed()` rỗng, nhưng `outer.getCause().getSuppressed()` có **hai** lỗi đóng B và A.
7. Nếu người điều tra chỉ đọc message của wrapper hoặc chỉ xem suppressed của wrapper, họ sẽ **không phát hiện đầy đủ hai lỗi cleanup**.

Đây chính là lý do đề bài yêu cầu **đọc cây ngoại lệ**, không chỉ đọc thông điệp cuối.

### Không đánh đồng lỗi đóng với lỗi chính

Trong ví dụ này, lỗi đọc nguồn là nguyên nhân khiến xử lý batch dừng lại. Lỗi đóng B và A xảy ra trong lúc dọn dẹp. Chúng không làm cho ngoại lệ chính biến thành `IllegalStateException` tại tầng đọc, nhưng vẫn quan trọng: trong ứng dụng thật, lỗi đóng có thể ảnh hưởng việc flush, hủy kết nối hoặc trạng thái resource.

## 9. Cách hiển thị của `printStackTrace` có thể khác cây tự dựng

`Throwable.printStackTrace()` thường in ngoại lệ ngoài cùng, các frame, rồi nội dung suppressed và cause theo cách định dạng của Java. Ví dụ đoạn:

```text
java.lang.IllegalStateException: batch import failed
    at example.ImportService.importBatch(ImportService.java:<line>)
Caused by: java.io.IOException: source read failed
    at example.Reader.readBatch(Reader.java:<line>)
    Suppressed: java.lang.IllegalStateException: close failed: B
        at example.Resource.close(Resource.java:<line>)
    Suppressed: java.lang.IllegalStateException: close failed: A
        at example.Resource.close(Resource.java:<line>)
```

Đây chỉ là **hình dạng minh họa**. Java có thể thêm/rút gọn nhiều dòng `at ...` và `... N more`. Ý nghĩa không đổi: suppressed B/A được **gắn với cause `IOException`**, không phải wrapper `IllegalStateException` bên ngoài.

Dấu thụt đầu dòng rất hữu ích: nó cho thấy nhánh lỗi thuộc ngoại lệ nào. Nếu log collector làm phẳng hoặc cắt mất thụt đầu dòng, bản văn có thể trở nên khó hiểu; khi đó cần giữ dữ liệu có cấu trúc hoặc stack trace nguyên vẹn.

### `... N more` nghĩa là gì?

Khi cause và wrapper chia sẻ phần cuối của call stack, `printStackTrace()` có thể ghi:

```text
... 4 more
```

Nghĩa là bốn frame cuối trùng với những frame đã được in ở phần bao ngoài, nên được rút gọn. Nó **không có nghĩa đã mất bốn tầng cause** hoặc Java chỉ giữ bốn frame. `getStackTrace()` vẫn là API có cấu trúc để xem các frame thuộc từng đối tượng lỗi.

**Không dùng văn bản `printStackTrace` làm giao thức máy móc ổn định.** Tên module, class loader, số dòng, số frame và cách trình bày có thể thay đổi khi build/runtime thay đổi. Khi cần xử lý chương trình, hãy đọc `getCause()` và `getSuppressed()` trực tiếp.

## 10. Tại sao chỉ lấy “root cause” vẫn chưa đủ?

Một hàm tiện ích đơn giản thường làm:

```java
static Throwable deepestCause(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
        current = current.getCause();
    }
    return current;
}
```

Đây là **hàm minh họa**; nó hợp lý với một cause chain hữu hạn, không có vòng. Tuy nhiên:

- Nó **chỉ đi theo `getCause()`**, không đọc nhánh suppressed của bất kỳ nút nào.
- Nó **bỏ thông điệp nghiệp vụ của wrapper**, khiến người điều tra khó biết thao tác nào thất bại.
- Nó không trả toàn bộ stack trace, chỉ trả một đối tượng cuối chuỗi.
- Với các đồ thị bất thường có vòng tham chiếu, vòng lặp `while` không có bảo vệ có thể không kết thúc.
- “Nguyên nhân cuối cùng” chưa chắc là thứ duy nhất quan trọng với dữ liệu đã ghi dở, cleanup thất bại hoặc giao dịch không hoàn tất.

Nếu chỉ cần quyết định một nhánh retry theo lỗi mạng cụ thể, kiểm tra cause chain có thể hữu ích. Nhưng nếu mục tiêu là **báo cáo đầy đủ sự cố**, hãy duyệt cause **và** suppressed cùng với các stack traces liên quan.

### Đừng dựa vào chuỗi message để phân loại

Không nên viết:

```java
if (ex.getMessage().contains("connection")) {
    // Quyết định thử lại.
}
```

Vấn đề: `getMessage()` có thể `null`, thông điệp thay đổi theo phiên bản/thư viện/ngôn ngữ, và từ `connection` có thể xuất hiện trong nhiều lỗi bản chất khác nhau. Khi chương trình cần phân loại để hành động, ưu tiên **kiểu ngoại lệ**, mã lỗi có cấu trúc hoặc trạng thái chính thức do API cung cấp.

Bộ duyệt cây là công cụ **chẩn đoán**, không phải lời khuyên coi mọi suppressed/cause đều có cùng chính sách retry.

## 11. Thuật toán duyệt an toàn: identity, giới hạn và không phá dữ liệu gốc

Ví dụ `ThrowableTreeDemo` đã dùng:

```java
Set<Throwable> visited =
        java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Throwable, Boolean>());
```

Đây là đoạn minh họa. Tại sao không chỉ dùng `HashSet`?

- Mục tiêu của `visited` là hỏi **đã gặp đúng đối tượng lỗi này chưa**, không hỏi hai lỗi có cùng thông điệp hoặc `equals()` hay không.
- Một lớp `Throwable` tùy chỉnh có thể ghi đè `equals()`/`hashCode()`.
- So sánh **reference identity** tránh gộp nhầm hai đối tượng lỗi khác nhau.
- Quan hệ có thể chia sẻ một cùng đối tượng ở nhiều nhánh; in nhãn *already visited* giữ cấu trúc đủ hiểu mà không đệ quy vô hạn.

Trong hệ thống cho phép tiếp nhận lỗi từ plugin hay extension, cần bổ sung:

1. **Giới hạn độ sâu**: tránh stack overflow của chính bộ duyệt nếu đồ thị cực sâu.
2. **Giới hạn số nút**: tránh tốn CPU/bộ nhớ trên lỗi có quá nhiều suppressed.
3. **Giới hạn số ký tự message/stack**: một exception có thể chứa dữ liệu đầu vào dài bất thường.
4. **Xử lý lỗi khi formatter chạy**: một lớp tùy chỉnh có thể ghi đè các phương thức biểu diễn, nên không nên giả định mọi quá trình định dạng đều vô hại.
5. **Không sửa đối tượng gốc khi đọc**: tránh `setStackTrace`, `initCause` hay `addSuppressed` chỉ để “đẹp log”.
6. **Bảo vệ dữ liệu nhạy cảm**: không in raw message ra các kênh công khai.

Những giới hạn này dành cho công cụ điều tra tổng quát; ví dụ giáo dục cố ý nhỏ để thấy rõ các nhánh.

### Không phải mọi đồ thị đều thuần “cây”

`Throwable.addSuppressed()` cấm trực tiếp tự suppress chính mình, nhưng các tham chiếu qua cause hoặc suppressed ở nhiều đối tượng có thể làm cấu trúc phức tạp. Java `printStackTrace` cũng có biện pháp đánh dấu tham chiếu vòng khi trình bày. Vì vậy tư duy **duyệt đồ thị có phát hiện lặp** là an toàn hơn giả định mọi Throwable chỉ xuất hiện ở một nhánh.

## 12. Log đầy đủ nhưng không log trùng và không lộ bí mật

Một lỗi có thể đi qua repository → service → controller → task boundary. Nếu tầng nào cũng gọi `printStackTrace()` rồi `throw ex;`, một sự cố có thể tạo nhiều bản trace trùng hoặc gần trùng. Điều này không làm cây ngoại lệ “đầy đủ hơn”; nó khiến log nhiễu và tốn tài nguyên.

Gợi ý phân chia:

- **Tầng thấp** ném đúng loại lỗi, cung cấp cause khi cần, quản lý tài nguyên và giữ suppressed của lỗi chính.
- **Tầng nghiệp vụ** chỉ bọc khi bổ sung ngữ cảnh thật hoặc thay đổi hợp đồng lỗi.
- **Ranh giới xử lý** quyết định phục hồi, trả kết quả thất bại, hoặc ghi log tập trung.
- **Logger** nhận đối tượng `Throwable` để formatter có thể trình bày cause và suppressed.

Ví dụ với Java Util Logging:

```java
java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger("batch.import");

logger.log(
        java.util.logging.Level.SEVERE,
        "Batch import failed",
        ex);
```

Đây là **đoạn minh họa**, `ex` cần là `Throwable` đã được bắt ở ranh giới phù hợp. Truyền nguyên đối tượng giúp logger giữ thông tin nhiều lớp. Chỉ viết `logger.severe(ex.getMessage())` thì thiếu thông tin cause/stack/suppressed.

### Tránh lộ dữ liệu nhạy cảm từ bất kỳ nút nào

Không chỉ message của ngoại lệ ngoài cùng có thể chứa dữ liệu nhạy cảm. Cause hoặc suppressed do một thư viện tạo ra có thể mang:

- Đường dẫn tệp và cấu hình nội bộ.
- Chuỗi kết nối hoặc thông tin đăng nhập nếu code xử lý thiếu an toàn.
- Định danh người dùng, nội dung yêu cầu, payload dữ liệu.
- Chi tiết triển khai dịch vụ, bảng database, câu SQL.

Do đó:

- Không trả cây stack trace nguyên văn cho API client hoặc người dùng cuối.
- Không đính kèm mật khẩu, token, cookie, mã xác thực vào message lúc tạo ngoại lệ.
- Kiểm soát truy cập, thời gian lưu và mức độ chi tiết của log nội bộ.
- Dùng request ID/correlation ID **không nhạy cảm** cho việc nối log, thay vì chép nguyên payload.
- Khi báo lỗi công khai, đưa ra mã lỗi ổn định và thông báo thích hợp; giữ chi tiết kỹ thuật tại hệ thống quan sát có quyền truy cập đúng.

Một cây lỗi “đầy đủ” về mặt kỹ thuật không có nghĩa được phép công khai mọi chi tiết của nó.

## 13. Khi nào nên xử lý từng nhánh, khi nào chỉ quan sát?

Đọc được nhiều nhánh **không đồng nghĩa** rằng tất cả phải được bắt và xử lý riêng ở cùng một tầng.

Ví dụ:

| Tình huống | Điều có thể cần làm |
| --- | --- |
| Cause là lỗi nhập liệu đã được phân loại | Trả lỗi validation thích hợp, không retry vô hạn |
| Cause là lỗi mạng tạm thời theo hợp đồng API | Cân nhắc retry có giới hạn và idempotency |
| Suppressed là lỗi đóng stream sau khi đọc thất bại | Lưu dấu chẩn đoán về cleanup, kiểm tra trạng thái tài nguyên |
| Suppressed là lỗi rollback | Báo vận hành vì trạng thái giao dịch có thể cần điều tra |
| Cause sâu nhất là lỗi thư viện nhưng wrapper có mã nghiệp vụ | Giữ cả hai: phân loại theo hợp đồng tầng phù hợp, điều tra kỹ thuật qua cause |
| Không có cause/suppressed | Đọc kiểu ngoại lệ, message, stack và ngữ cảnh công việc hiện có |

Không tự động retry chỉ vì thấy một `IOException` ở đâu đó trong cây. Nó có thể là suppressed của thao tác đóng sau một lỗi chính khác, hoặc đã được wrapper biểu diễn thành lỗi không thể phục hồi. Chính sách xử lý phải xét **vai trò của nhánh** và hợp đồng của tầng đang ra quyết định.

## 14. Những hiểu lầm và lỗi thường gặp

| Ngộ nhận/lỗi | Vì sao không đúng | Cách tốt hơn |
| --- | --- | --- |
| “Thông điệp cuối cùng là toàn bộ lỗi” | Bỏ wrapper, cause, suppressed và stack | Đọc đối tượng lỗi cùng quan hệ |
| “Lấy sâu nhất trong `getCause()` là đủ” | Không duyệt suppressed, mất ngữ cảnh | Duyệt cả cause và suppressed |
| “Suppressed là một cause khác” | Đây là hai quan hệ ngữ nghĩa khác nhau | Phân biệt `getCause()` và `getSuppressed()` |
| “Suppressed chỉ nằm ở root” | Mỗi nút `Throwable` có thể mang suppressed riêng | Xem suppressed ở **mọi nút** |
| “`printStackTrace` in một chuỗi ổn định” | Số dòng/khung/định dạng thay đổi | Kiểm thử kiểu, cause, suppressed; không so raw trace |
| “`... N more` nghĩa là mất dữ liệu” | Chỉ rút gọn khung trùng trong bản in | Đọc `getStackTrace()` nếu cần |
| “Chỉ lấy `ex.getMessage()` khi log lỗi” | Mất dữ liệu chẩn đoán | Truyền `Throwable` trực tiếp cho logger |
| “Ném wrapper mới thì cause tự giữ” | Không nếu constructor không nhận cause | Dùng `new X(message, original)` khi thích hợp |
| “Suppressed không quan trọng” | Có thể báo lỗi ghi/rollback/close | Kiểm tra ảnh hưởng vận hành |
| “Hai lỗi có cùng message là một” | Có thể là hai đối tượng khác nhau | Dùng reference identity khi cần duyệt |
| “Cứ bắt `Throwable` rồi nuốt lỗi” | Có thể giấu `Error` và trạng thái không thể phục hồi | Đặt chính sách xử lý ở tầng phù hợp |
| “Có trace là biết chính xác dòng thời gian” | Stack không phải event log nhiều thread | Dùng timestamp, correlation/tracing có cấu trúc |
| “Có thể gửi nguyên trace cho client” | Có nguy cơ lộ thông tin | Phân tách phản hồi công khai và log nội bộ |

## 15. Bài tập kiểm tra cách đọc cây ngoại lệ

**Bài A — tìm nút có suppressed**

```text
ServiceException("job failed")
└── CAUSE → IOException("read failed")
             └── SUPPRESSED → IOException("close failed")
```

**Hỏi:** `serviceError.getSuppressed().length` có thể bằng bao nhiêu, và cần đọc ở đâu để thấy lỗi đóng?

**Đáp án:** có thể bằng `0`. Lỗi đóng nằm trên **cause**; đọc `serviceError.getCause().getSuppressed()` sau khi kiểm tra cause không null.

**Bài B — handler bắt gì?**

`IllegalStateException` ngoài cùng có cause `IOException`. Handler chỉ viết `catch (IOException ex)` có tự bắt wrapper không?

**Không.** Handler xét loại runtime của đối tượng đang được ném, không tự đi qua cause. Muốn xử lý wrapper phải bắt kiểu phù hợp hoặc điều chỉnh thiết kế API.

**Bài C — hai suppressed exceptions**

Thân `try-with-resources` ném `IOException("body")`, B.close rồi A.close ném lỗi. Lỗi nào thường là chính?

**`IOException("body")`**; hai lỗi đóng được gắn suppressed trên ngoại lệ chính, theo thứ tự đóng B trước A.

**Bài D — dòng `... 3 more`**

Nó có nghĩa ba ngoại lệ bị mất không?

**Không.** Đó thường là ba **khung stack trùng phần cuối** đã được rút gọn khi in một nhánh cause/suppressed.

**Bài E — cùng một exception xuất hiện hai lần**

Bộ duyệt thấy một đối tượng `Throwable` được tham chiếu lại ở nhánh khác. Có nên tiếp tục đệ quy vô điều kiện không?

**Không.** Dùng tập đối tượng đã thăm theo reference identity và ghi nhận việc đã gặp để tránh lặp/cycles.

**Bài F — lỗi nào đáng quan tâm hơn?**

Một lỗi đọc là primary; rollback thất bại nằm trong suppressed. Có được bỏ qua rollback vì chỉ là “phụ” không?

**Không nên tự động bỏ qua.** Lỗi rollback có thể ảnh hưởng tính nhất quán; cách xử lý dựa vào hợp đồng giao dịch và mức độ tác động của hệ thống.

**Bài G — chỉ đọc log message**

Log chỉ còn dòng `"batch failed"` và không lưu `Throwable`. Có thể tái tạo chắc chắn source line, cause và suppressed chỉ từ message này không?

**Không.** Thông tin đã không được ghi nhận trong dòng log; không thể suy ra đáng tin. Cần giữ đối tượng lỗi hoặc bản trace/cấu trúc tương ứng tại ranh giới logging.

**Bài H — cây hay chuỗi?**

Nếu một cause có ba suppressed và một suppressed lại có cause, tại sao chỉ lặp `while (ex.getCause() != null)` là chưa đủ?

Vì vòng lặp này chỉ đi theo **một loại cạnh `cause`**, bỏ tất cả cạnh suppressed và nguyên nhân nằm sâu trong các nhánh phụ.

## 16. Checklist điều tra lỗi thực tế

Khi cần hiểu một exception Java nhiều tầng:

1. **Giữ đối tượng `Throwable` ngoài cùng** hoặc bản stack trace đầy đủ được ghi đúng ranh giới.
2. **Xác định loại và thông điệp ngoài** để biết tác vụ/hợp đồng nào thất bại.
3. **Lần theo `getCause()`** từng tầng, không dừng ở message đầu hoặc dòng cuối.
4. **Tại mỗi tầng, đọc `getSuppressed()`**, rồi tiếp tục duyệt các nhánh con.
5. **Giữ phân biệt cause/suppressed** trong báo cáo: nguyên nhân khác với lỗi cleanup.
6. **Quan sát stack trace của từng nút** khi cần vị trí code; nhớ nơi ghi stack không nhất thiết là nơi rethrow.
7. **Hiểu `... N more`** là rút gọn khung trùng chứ không phải một lỗi mới.
8. **Tránh suy diễn từ văn bản log** khi đã có API dữ liệu `Throwable`; không parse message để quyết định nghiệp vụ.
9. **Dùng identity và giới hạn duyệt** nếu viết công cụ duyệt tổng quát.
10. **Đánh giá cả lỗi chính và lỗi phụ** về ảnh hưởng dữ liệu/tài nguyên, không coi suppressed là vô hại.
11. **Ghi log một cách tập trung** và giữ cause đúng khi chuyển lỗi; không lặp lại toàn bộ trace ở mọi tầng.
12. **Kiểm soát dữ liệu nhạy cảm** trong mọi message, cause, suppressed và stack trace trước khi lưu/chia sẻ.

## Kết luận

**Một ngoại lệ Java không chỉ là một thông điệp.** Đối tượng `Throwable` có **kiểu lỗi, message, stack trace, một cause trực tiếp và danh sách suppressed**. Vì mỗi cause hoặc suppressed lại có thể dẫn tới các đối tượng lỗi khác, ta cần suy nghĩ theo **cấu trúc phân nhánh** thay vì chỉ đọc một dòng `Caused by` cuối cùng.

Cách điều tra đáng tin cậy là đọc **ngữ cảnh của wrapper**, **nguyên nhân trên chuỗi `getCause()`**, **các nhánh `getSuppressed()` ở mọi nút**, rồi đối chiếu stack frames và log vận hành. Điều này giữ được cả **thất bại chính** lẫn **lỗi cleanup/rollback liên quan**, giúp chẩn đoán đúng mà không phụ thuộc vào câu chữ hay số dòng của một bản stack trace.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cause-and-suppressed-009">Giữ cả lỗi chính và lỗi cleanup khi quản lý tài nguyên thủ công</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ cả lỗi chính và lỗi cleanup khi quản lý tài nguyên thủ công trong Java

## 1. Mục tiêu học tập

Khi một thao tác thất bại, chương trình có thể cần **dọn dẹp tài nguyên** như stream, tệp, kết nối, khóa hoặc trạng thái tạm. Nếu dọn dẹp cũng ném ngoại lệ, bạn có **hai thông tin lỗi quan trọng**: lỗi làm công việc thất bại ban đầu và lỗi phát sinh khi dọn dẹp.

Một `finally` tưởng như đúng có thể khiến lỗi cleanup **che mất lỗi chính**:

```java
try {
    doWork();        // Ném IOException("work failed").
} finally {
    resource.close(); // Cũng ném IOException("close failed").
}
```

Đây là **mảnh minh họa** phụ thuộc `doWork()` và `resource` có sẵn. Nếu cả hai dòng đều ném, ngoại lệ thoát khỏi `finally` có thể thay thế ngoại lệ đang lan truyền từ `try`; người gọi chỉ quan sát trực tiếp lỗi `close failed`, còn `work failed` có thể bị mất khỏi chuỗi chẩn đoán.

Muốn giữ cả hai, hãy ưu tiên `try-with-resources` khi phù hợp. Nếu phải quản lý thủ công, hãy xác định **ngoại lệ chính** (*primary*) và đính kèm lỗi dọn dẹp vào nó qua `Throwable.addSuppressed(...)`. Khi công việc thành công nhưng cleanup lỗi, **lỗi cleanup phải được truyền lên hoặc xử lý**, thay vì âm thầm bị bỏ qua.

Sau bài học, bạn có thể:

- Phân biệt lỗi primary, lỗi cleanup, `cause` và `suppressed`.
- Giải thích vì sao `finally { close(); }` thuần túy có thể che mất lỗi đang chờ.
- Viết một mẫu cleanup thủ công đúng cho cả đường thành công và đường thất bại.
- Bảo toàn thứ tự khi nhiều tài nguyên được đóng; tiếp tục cố đóng tài nguyên còn lại nếu một `close()` gặp lỗi.
- Quản lý đúng thất bại khi **khởi tạo tài nguyên thứ hai** hoặc **trả về sớm**.
- Tránh bỏ qua `RuntimeException`/`Error` khi chỉ bắt `IOException`; hiểu giới hạn của `catch (Throwable)`.
- Giữ nguyên stack trace, cause và suppressed thay vì chỉ sao chép thông điệp.
- Biết khi nào nên dùng `try-with-resources`, khi nào cleanup thủ công thật sự có giá trị.

Ví dụ biên dịch/chạy với Java 17+, không phụ thuộc hệ thống tệp hoặc mạng, nên có `stdout` xác định.

## 2. Phân biệt lỗi chính và lỗi dọn dẹp

Giả sử một thao tác gửi dữ liệu làm hai việc:

1. `writeData()` ghi dữ liệu và thất bại: `IOException("write failed")`.
2. Khi kết thúc, `close()` cũng thất bại: `IOException("close failed")`.

Hai lỗi đều quan trọng, nhưng chúng không có cùng vai trò:

```text
operation starts
      |
      v
writeData() throws IOException("write failed")
      |
      |  lỗi chính / primary
      v
cleanup: close() throws IOException("close failed")
      |
      |  lỗi bổ sung / suppressed
      v
Caller nhận IOException("write failed")
  + getSuppressed()[0] = IOException("close failed")
```

**Lỗi chính** là lý do ban đầu khiến đơn vị công việc không hoàn thành. **Lỗi cleanup** là một thất bại tiếp theo trong lúc giải phóng tài nguyên. Java có cơ chế `Throwable.addSuppressed(Throwable)` để liên kết lỗi cleanup với lỗi chính mà không thay thế nó.

Điểm cần hiểu:

- `primary.addSuppressed(cleanup)` **không ném** `cleanup`; nó ghi nhận lỗi bổ sung trên đối tượng `primary`.
- `primary.getSuppressed()` trả mảng `Throwable[]` chứa các lỗi đã được ghi nhận.
- `getCause()` thể hiện quan hệ nguyên nhân khi một ngoại lệ được bọc bằng ngoại lệ khác; nó **không đồng nghĩa** với `getSuppressed()`.
- Nếu công việc thành công nhưng cleanup gặp lỗi, lỗi cleanup thường trở thành **primary**, vì trước đó không có lỗi chính.
- “Suppressed” nghĩa là **không thay thế primary**, không phải “không đáng quan tâm”.

Bảng quyết định:

| Công việc | Cleanup | Kết quả nên quan sát |
| --- | --- | --- |
| Thành công | Thành công | Hoàn thành bình thường |
| Thất bại A | Thành công | Ném A |
| Thành công | Thất bại B | Ném B |
| Thất bại A | Thất bại B | Ném A, gắn B bằng `addSuppressed` |

Đây là chính sách giữ lỗi gốc mà `try-with-resources` cung cấp cho tài nguyên `AutoCloseable`.

## 3. Vì sao `finally` thủ công có thể đánh mất lỗi đầu tiên?

Java thực thi `finally` khi rời `try` theo đường bình thường, `return` hoặc exception. Nếu `finally` tự hoàn thành đột ngột bằng một ngoại lệ mới, lý do hoàn thành đột ngột từ `try` có thể bị **thay thế**.

Tạo tệp **`LosingPrimaryDemo.java`**:

```java
import java.io.IOException;

public class LosingPrimaryDemo {
    static void run() throws IOException {
        try {
            throw new IOException("work failed");
        } finally {
            throw new IOException("cleanup failed");
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IOException failure) {
            System.out.println("observed=" + failure.getMessage());
            System.out.println("suppressed="
                    + failure.getSuppressed().length);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac LosingPrimaryDemo.java
java LosingPrimaryDemo
```

`stdout`:

```text
observed=cleanup failed
suppressed=0
```

Cơ chế:

1. `try` ném `IOException("work failed")`.
2. Trước khi ngoại lệ ấy thoát, `finally` chạy.
3. `finally` ném `IOException("cleanup failed")`.
4. Ngoại lệ thứ hai trở thành ngoại lệ quan sát được; lỗi công việc không được tự đính kèm `suppressed`.
5. `getSuppressed().length` của ngoại lệ mới là 0 trong ví dụ này.

Không nên cố “sửa” bằng cách đặt `return` trong `finally`: `return` cũng có thể **nuốt hoàn toàn ngoại lệ** đang chờ. Một `finally` chuyên dọn dẹp không nên thay đổi lý do thoát một cách bất ngờ.

### Vì sao bắt rồi chỉ log cleanup cũng không đủ?

Cách viết:

```java
try {
    doWork();
} finally {
    try {
        resource.close();
    } catch (IOException cleanupFailure) {
        System.err.println(cleanupFailure.getMessage());
    }
}
```

Đây là **mảnh minh họa**. Nó không làm lỗi close thay thế lỗi của `doWork()`, nhưng có thể **bỏ qua lỗi close về mặt hợp đồng**: nếu công việc thành công mà close thất bại, phương thức vẫn có thể hoàn thành bình thường. Với luồng ghi, lỗi close/flush có thể là lỗi nghiêm trọng liên quan dữ liệu chưa được ghi ra an toàn.

Để giữ cả hai lỗi một cách có cấu trúc, cần phân biệt **đã có primary hay chưa**. Nếu có, đính kèm cleanup; nếu chưa, cleanup trở thành lỗi báo ra.

## 4. `addSuppressed` và `getSuppressed` trong mẫu quản lý thủ công

API trên `java.lang.Throwable`:

```java
primary.addSuppressed(cleanupFailure);
Throwable[] extras = primary.getSuppressed();
```

Đây là **mảnh mã** với hai biến `Throwable` đã tồn tại.

Các quy tắc cần nắm:

- `addSuppressed` **thêm một đối tượng `Throwable`** vào lỗi đang nhận lời gọi.
- Không được truyền `null` (gây `NullPointerException`) hoặc chính đối tượng primary vào `primary.addSuppressed(primary)` (gây `IllegalArgumentException`).
- Với một exception được tạo bình thường, suppression được bật theo mặc định. Một lớp throwable đặc biệt có thể tắt suppression qua constructor protected phù hợp, khi đó `getSuppressed()` vẫn là mảng rỗng.
- `getSuppressed()` không trả `null`. Mảng kết quả là bản sao, nhưng chứa tham chiếu tới các đối tượng `Throwable` đã gắn.
- Đính kèm suppressed không tự `throw` lỗi chính. Bạn vẫn phải **ném tiếp primary**, trả trạng thái thất bại hoặc xử lý nó theo hợp đồng.
- Không nên thêm một ngoại lệ cleanup vào primary nhiều lần nếu nó đã được `try-with-resources` ghi nhận tự động.
- Một suppressed exception cũng có thể có `cause` và suppressed riêng; đừng chỉ đọc message của primary rồi kết luận không còn lỗi nào khác.

### Mẫu thuật toán cốt lõi

```text
primary = null
thử thực hiện công việc
    nếu công việc lỗi:
        primary = lỗi đó

thử dọn dẹp
    nếu dọn dẹp lỗi:
        nếu primary chưa có:
            primary = lỗi dọn dẹp
        ngược lại:
            gắn lỗi dọn dẹp thành suppressed của primary

nếu primary tồn tại:
    ném primary hoặc báo thất bại theo hợp đồng
```

Đây là **pseudocode**, không phải mã Java chạy trực tiếp. Điểm đặc biệt so với `finally` thuần túy là **không để cleanup exception vô tình thay primary**.

## 5. Ví dụ Java đầy đủ: quản lý một resource theo bốn kịch bản

Tạo tệp **`ManualCleanupDemo.java`**:

```java
import java.io.IOException;

public class ManualCleanupDemo {
    static class TrackedResource implements AutoCloseable {
        private final boolean closeFails;

        TrackedResource(boolean closeFails) {
            this.closeFails = closeFails;
        }

        void work(boolean fail) throws IOException {
            if (fail) {
                throw new IOException("work failed");
            }
        }

        @Override
        public void close() throws IOException {
            System.out.println("closing");
            if (closeFails) {
                throw new IOException("close failed");
            }
        }
    }

    static void execute(boolean workFails, boolean closeFails)
            throws IOException {
        TrackedResource resource = new TrackedResource(closeFails);
        Throwable primary = null;

        try {
            resource.work(workFails);
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary != null) {
                try {
                    resource.close();
                } catch (Throwable cleanup) {
                    if (cleanup != primary) {
                        primary.addSuppressed(cleanup);
                    }
                }
            } else {
                resource.close();
            }
        }
    }

    static void show(String label, boolean workFails,
                     boolean closeFails) {
        System.out.println("-- " + label + " --");
        try {
            execute(workFails, closeFails);
            System.out.println("success");
        } catch (IOException failure) {
            System.out.println("primary=" + failure.getMessage());
            for (Throwable extra : failure.getSuppressed()) {
                System.out.println("suppressed=" + extra.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        show("both ok", false, false);
        show("work only", true, false);
        show("close only", false, true);
        show("both fail", true, true);
    }
}
```

```bash
javac ManualCleanupDemo.java
java ManualCleanupDemo
```

`stdout` chính xác:

```text
-- both ok --
closing
success
-- work only --
closing
primary=work failed
-- close only --
closing
primary=close failed
-- both fail --
closing
primary=work failed
suppressed=close failed
```

**Từng nhánh của `execute`:**

1. **Thành công cả hai:** `work()` hoàn thành, biến `primary` vẫn null; `finally` gọi `close()` thành công; method kết thúc bình thường.
2. **Chỉ work lỗi:** `catch (Throwable failure)` ghi nhận lỗi chính và ném lại. `finally` đóng thành công; IOException cũ tiếp tục lan ra.
3. **Chỉ close lỗi:** chưa có primary, nên `resource.close()` được gọi trong nhánh `else` và ném `IOException("close failed")` như lỗi chính.
4. **Cả hai lỗi:** primary là chính đối tượng `IOException("work failed")`. Trong nhánh cleanup, IOException từ `close()` được bắt và thêm vào `primary` bằng `addSuppressed`. `finally` hoàn thành bình thường, nên Java tiếp tục ném **đối tượng primary ban đầu** cùng danh sách suppressed.

### Vì sao `catch (Throwable)` ở đây vẫn biên dịch với `throws IOException`?

Đây là cơ chế **precise rethrow** (Java 7+). Tham số `failure` trong `catch` không bị gán lại, nên trình biên dịch có thể dựa trên những checked exception mà `try` thực sự có thể ném. Trong ví dụ, `resource.work(...)` khai báo `IOException`; các ngoại lệ unchecked có thể được ném mà không cần thêm `throws`. Do đó `throw failure;` không buộc chữ ký `execute` phải ghi `throws Throwable`.

Đoạn này chủ ý sử dụng `catch (Throwable)` **ở bên trong vùng bảo toàn lỗi** để không bỏ sót việc gắn lỗi cleanup vào một primary thuộc `RuntimeException` hoặc `Error`. Nó **không có nghĩa** ứng dụng nên bắt rồi phục hồi mọi `Error`. Lệnh `throw failure;` truyền lỗi ban đầu ra ngoài; mã không cố tiếp tục thực hiện nghiệp vụ sau sự cố.

Cần thận trọng hơn nữa trong sản phẩm:

- Nếu một `Error` nghiêm trọng xảy ra (ví dụ thiếu bộ nhớ), thao tác tạo suppressed, ghi log hoặc cleanup có thể cũng không an toàn/không thực hiện được.
- Một số chương trình muốn dùng kiểu cụ thể hơn để giảm mức bắt. Tuy nhiên, chỉ bắt `IOException` sẽ không bảo toàn chính sách suppressed cho một `RuntimeException` hoặc `Error` bất ngờ trong phần thân.
- Mã tổng quát phải xét **toàn bộ checked exception** do công việc và `close()` có thể ném, không áp cứng `IOException` của ví dụ.
- `try-with-resources` đã giải quyết phần logic này và thường là giải pháp dễ kiểm chứng hơn.

### Tại sao cần kiểm tra `cleanup != primary`?

`addSuppressed` cấm một `Throwable` suppress chính nó. Mặc dù ít gặp, resource tự định nghĩa có thể ném **chính đối tượng Throwable đang là primary**. Trong mẫu thủ công, `if (cleanup != primary)` tránh tạo một `IllegalArgumentException` mới chỉ vì self-suppression, nhờ đó không vô tình che lỗi gốc.

**Không suy ra** mẫu này luôn tái hiện mọi tình huống bytecode của `try-with-resources`. Đây là chính sách bảo toàn lỗi **có chủ đích** của ví dụ; JLS định nghĩa ngữ nghĩa chính thức của TWR, và khi cùng một đối tượng được ném trong các tình huống bất thường, xử lý self-suppression của mã thủ công cần được thiết kế rõ ràng.

## 6. Mẫu tối giản khi chỉ có `IOException`: hiểu rõ giới hạn

Nếu một API chắc chắn chỉ muốn xử lý lỗi checked I/O và không cần mô phỏng hoàn toàn các trường hợp unchecked, bạn có thể thấy mã sau:

```java
IOException primary = null;
try {
    performIo();
} catch (IOException failure) {
    primary = failure;
} finally {
    try {
        resource.close();
    } catch (IOException cleanup) {
        if (primary == null) {
            primary = cleanup;
        } else if (primary != cleanup) {
            primary.addSuppressed(cleanup);
        }
    }
}
if (primary != null) {
    throw primary;
}
```

Đây là **mảnh mã minh họa** trong phương thức `throws IOException`, giả định `performIo()` và `resource` phù hợp. Nó mô tả cách gom một lỗi I/O chính và một lỗi I/O cleanup; tuy nhiên **không phải phiên bản tổng quát an toàn cho mọi Throwable**.

**Giới hạn nguy hiểm:** nếu `performIo()` ném một `RuntimeException` hoặc `Error` mà `catch (IOException)` không bắt, khối `finally` vẫn chạy. Nếu `close()` ném `IOException`, handler cleanup sẽ bắt IOException và ghi vào biến `primary` (đang null). Vì control flow đang thoát do lỗi unchecked nên đoạn `if (primary != null)` **sau finally không chạy**, và ngoại lệ cleanup không được gắn vào unchecked primary. Lỗi công việc vẫn truyền đi, nhưng bạn đã bỏ mất chẩn đoán về cleanup.

Nếu yêu cầu là **giữ cả lỗi chính và lỗi cleanup bất kể primary thuộc nhánh nào**, hãy dùng mẫu `catch (Throwable)` rồi ném lại (trong phạm vi phù hợp), hoặc `try-with-resources`.

Đừng giới thiệu mẫu tối giản này như một bản thay thế hoàn chỉnh cho TWR.

## 7. Tài nguyên `AutoCloseable` và checked exception

`AutoCloseable.close()` khai báo `throws Exception`; các implementation có thể thu hẹp loại ngoại lệ trong override, ví dụ `Closeable.close() throws IOException` hoặc một lớp đóng không ném checked exception.

Khi viết cleanup thủ công, **kiểu tĩnh** của resource ảnh hưởng tới nghĩa vụ bắt/khai báo:

```java
AutoCloseable resource = obtainResource();
try {
    useResource(resource);
} finally {
    resource.close(); // Có thể ném Exception theo AutoCloseable.
}
```

Đây là mảnh minh họa. Phương thức chứa nó phải xử lý hoặc khai báo `Exception`, trừ khi được tổ chức lại phù hợp. Không thể giả định mọi `AutoCloseable` chỉ ném `IOException`.

Ngược lại, trong `ManualCleanupDemo`, kiểu resource là `TrackedResource` với `close() throws IOException` nên `execute` chỉ cần khai báo checked `IOException`.

Một số lưu ý:

- `close()` có thể ném unchecked exception dù chữ ký chỉ liệt kê checked exception; xem contract của implementation.
- `close()` có thể có side effect và không nhất thiết idempotent với mọi `AutoCloseable`.
- Không dùng `catch (Exception)` rồi trả kết quả thành công nếu cleanup thất bại có ý nghĩa đối với dữ liệu.
- `ThreadDeath` và những `Error` nghiêm trọng không nên trở thành điều kiện phục hồi mặc định; cơ chế suppress chỉ bảo toàn thông tin lỗi trong khi vẫn truyền primary ra ngoài.

## 8. Quản lý **nhiều** tài nguyên thủ công: đóng hết và giữ thứ tự

Khi có hai tài nguyên độc lập, cleanup tốt thường cố đóng cả hai theo thứ tự ngược với thứ tự chúng được sở hữu/khởi tạo thành công. Một `finally` đơn giản như sau **có vấn đề**:

```java
finally {
    second.close();
    first.close();
}
```

Nếu `second.close()` ném, Java bỏ qua lệnh `first.close()`. Hậu quả: tài nguyên đầu tiên có thể bị leak. Hơn nữa, lỗi close thứ hai có thể che mất lỗi từ phần thân.

Một mẫu có kiểm soát là:

1. Giữ một biến `Throwable primary` đại diện cho lỗi ban đầu (nếu có).
2. Đóng `second`, nếu lỗi thì thêm nó vào primary hoặc trở thành primary nếu chưa có.
3. **Bất kể bước 2 có lỗi**, tiếp tục đóng `first`.
4. Ghi lại mọi lỗi sau thành suppressed của primary hiện hành.
5. Sau cleanup, ném lỗi primary hoặc trả kết quả nếu không có lỗi.

Đây cũng là cơ chế trực giác của `try-with-resources` khi có nhiều resource.

### Chương trình chạy được: nhiều lần đóng đều thất bại

Tạo file **`ManualMultipleResourcesDemo.java`**:

```java
import java.io.IOException;

public class ManualMultipleResourcesDemo {
    static class Resource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Resource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failOnClose) {
                throw new IOException("close " + name + " failed");
            }
        }
    }

    static Throwable closeKeepingPrimary(
            Resource resource, Throwable primary) {
        if (resource == null) {
            return primary;
        }

        try {
            resource.close();
        } catch (Throwable cleanup) {
            if (primary == null) {
                return cleanup;
            }
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
        return primary;
    }

    static void execute(boolean failBody) throws Throwable {
        Resource first = new Resource("first", true);
        Resource second = new Resource("second", true);

        Throwable primary = null;
        try {
            System.out.println("body");
            if (failBody) {
                throw new IOException("body failed");
            }
        } catch (Throwable failure) {
            primary = failure;
        } finally {
            primary = closeKeepingPrimary(second, primary);
            primary = closeKeepingPrimary(first, primary);
        }

        if (primary != null) {
            throw primary;
        }
    }

    public static void main(String[] args) {
        for (boolean failBody : new boolean[] {true, false}) {
            System.out.println("-- failBody=" + failBody + " --");
            try {
                execute(failBody);
            } catch (Throwable failure) {
                System.out.println("primary=" + failure.getMessage());
                for (Throwable extra : failure.getSuppressed()) {
                    System.out.println("suppressed=" + extra.getMessage());
                }
            }
        }
    }
}
```

```bash
javac ManualMultipleResourcesDemo.java
java ManualMultipleResourcesDemo
```

`stdout`:

```text
-- failBody=true --
body
close second
close first
primary=body failed
suppressed=close second failed
suppressed=close first failed
-- failBody=false --
body
close second
close first
primary=close second failed
suppressed=close first failed
```

Ở trường hợp `failBody=true`, lỗi từ phần thân đã tồn tại. Cả hai lỗi đóng trở thành suppressed theo thứ tự đóng: `second` trước, `first` sau.

Ở trường hợp `failBody=false`, lần đóng đầu tiên gặp lỗi là `second.close()`. Vì chưa có primary, lỗi đóng thứ hai về mặt thời gian trở thành primary; lỗi của `first.close()` được gắn vào nó. Cả hai tài nguyên vẫn được cố đóng ngay cả khi `second.close()` ném.

**Vì sao ví dụ khai báo `throws Throwable`?** Đây là một **mô hình học tập tổng quát** cần truyền lại đúng đối tượng primary bất kể là checked exception, `RuntimeException` hay `Error`. Trong API sản phẩm, `throws Throwable` thường quá rộng. Có thể thu hẹp chữ ký theo kiểu lỗi thực tế, dùng precise rethrow hoặc viết helper quản lý tài nguyên theo các generic exception type, nhưng không nên hy sinh tính đúng để chỉ giảm một từ khóa. Nếu dùng `try-with-resources` với các kiểu resource cụ thể, trình biên dịch thường tự kiểm tra những checked exception hợp lệ cho API tốt hơn.

### Self-suppression và duplicate suppressed

Helper kiểm tra `cleanup != primary` vì không được suppress cùng một đối tượng vào chính nó. Nếu resource liên tục ném **một đối tượng cleanup đã được gắn** ở nhiều chỗ, `addSuppressed` không tự loại trùng; việc quản lý đóng và ownership cần ngăn thêm lỗi trùng vô ý.

Trong mã thực, khi cleanup có thể thay đổi trạng thái của nhiều thành phần, bạn phải quyết định có nên cố dọn tiếp sau một số `Error` nghiêm trọng hay không. Mẫu trên phản ánh một chính sách thu thập lỗi rõ ràng nhưng không hứa phục hồi được mọi sự cố JVM.

## 9. Lỗi ngay trong giai đoạn **khởi tạo** tài nguyên

Một trường hợp dễ bỏ sót là lỗi xảy ra **trước khi vào khối try cuối cùng**:

```java
Resource first = openFirst();   // Thành công.
Resource second = openSecond(); // Ném IOException.
try {
    doWork(first, second);
} finally {
    second.close();
    first.close();
}
```

Đây là **mảnh mã sai thiết kế có chủ ý**: nếu `openSecond()` ném, Java chưa bước vào `try/finally` nên tài nguyên `first` chưa được cleanup theo khối này.

Cách tổ chức tốt hơn là bắt đầu phạm vi bảo vệ **ngay sau khi nhận ownership** của `first`. Nếu phải dùng cleanup thủ công, hãy khởi tạo biến `second` theo trạng thái chưa có, rồi đảm bảo `first` được đóng kể cả khi bước tạo `second` lỗi.

### Ví dụ chạy được: khởi tạo resource thứ hai thất bại

Tạo file **`InitializationCleanupDemo.java`**:

```java
import java.io.IOException;

public class InitializationCleanupDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException(name + " close failed");
        }
    }

    static Resource openSecond() throws IOException {
        throw new IOException("open second failed");
    }

    static void run() throws IOException {
        Resource first = new Resource("first");
        Throwable primary = null;

        try {
            Resource second = openSecond();
            try {
                System.out.println(second);
            } finally {
                second.close();
            }
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary != null) {
                try {
                    first.close();
                } catch (Throwable cleanup) {
                    if (cleanup != primary) {
                        primary.addSuppressed(cleanup);
                    }
                }
            } else {
                first.close();
            }
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IOException failure) {
            System.out.println("primary=" + failure.getMessage());
            for (Throwable extra : failure.getSuppressed()) {
                System.out.println("suppressed=" + extra.getMessage());
            }
        }
    }
}
```

```bash
javac InitializationCleanupDemo.java
java InitializationCleanupDemo
```

`stdout`:

```text
close first
primary=open second failed
suppressed=first close failed
```

Phân tích:

1. `first` được tạo thành công và thuộc quyền sở hữu của `run()`.
2. Bước `openSecond()` ném `IOException("open second failed")`.
3. `second` không được khởi tạo nên khối `try/finally` bên trong **chưa bắt đầu**.
4. `catch (Throwable)` của phạm vi ngoài giữ lỗi khởi tạo làm primary và ném lại.
5. `finally` bên ngoài đóng `first`; việc đóng cũng lỗi và được đính kèm vào primary.
6. Bên gọi thấy lý do khởi tạo thất bại cùng lỗi cleanup của resource đã được mở trước đó.

Mẫu tổ chức thực tế nên đơn giản hơn nếu có thể dùng `try-with-resources`:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    doWork(first, second);
}
```

Đây là **mảnh minh họa**. Khi khởi tạo resource thứ hai thất bại trong header, Java tự đóng resource thứ nhất đã khởi tạo thành công và quản lý lỗi đóng như suppressed.

## 10. `return` trong `try` và lỗi `close()` khi trả về

Một phương thức có thể chuẩn bị giá trị trả về trước khi chạy `finally`. Nếu cleanup ném ngoại lệ, lời gọi **có thể không trả được giá trị** dù biểu thức `return` đã được đánh giá.

Ví dụ:

```java
static String calculate() throws IOException {
    Resource resource = open();
    try {
        return "ok";
    } finally {
        resource.close(); // Có thể ném IOException.
    }
}
```

Đây là mảnh minh họa, cần định nghĩa `Resource` và `open()`. Nếu `close()` thất bại, lỗi đóng thoát ra và **thay thế kết quả trả về dự kiến**. Vì phần thân chỉ có `return "ok"`, chưa có ngoại lệ công việc cần bảo toàn; lỗi đóng trở thành primary. Đó là hành vi phù hợp với chính sách “không báo thành công nếu dọn dẹp thất bại”.

Nếu một biểu thức `return compute();` tự ném ngoại lệ, thì primary xuất hiện ngay lúc đánh giá biểu thức. Cleanup phải giữ lỗi đó và đính kèm lỗi đóng như suppressed nếu phát sinh, thay vì thay thế nguyên nhân ban đầu.

**Không đặt `return` trong `finally`** để cố giữ kết quả: nó có thể che cả lỗi công việc lẫn lỗi cleanup. Với API trả kết quả, nên hoàn thành dọn dẹp rồi mới quyết định thành công, hoặc dùng `try-with-resources` để ngôn ngữ làm đúng việc.

## 11. Resource không thể đóng lần hai và quyền sở hữu tài nguyên

Giữ ngoại lệ không thể tách khỏi **quyền sở hữu tài nguyên**.

- Nếu một phương thức **tự mở** stream và không chuyển ownership ra ngoài, thường nó có trách nhiệm đóng.
- Nếu phương thức chỉ **mượn** `InputStream` hoặc `Connection` từ caller, tự gọi `close()` có thể sai kể cả khi code suppressed chính xác.
- Nếu trả một stream đang mở cho caller, phương thức không nên đóng stream trước khi trả; caller nhận trách nhiệm cleanup sau khi ownership được chuyển.
- Nếu nhiều wrapper cùng quản lý một delegate, đóng wrapper có thể đóng delegate và dẫn tới **double-close**. Một số API cho phép đóng lặp, nhưng `AutoCloseable` không bảo đảm mọi implementation đều idempotent.
- Với JDBC pool, `Connection.close()` có thể mang nghĩa trả logical handle về pool; phải xét scope của transaction hoặc caller đang giữ quyền quản lý.

**Lỗi cleanup của một tài nguyên không thuộc quyền sở hữu hiện tại** là hậu quả của quyết định đóng sai, không phải vấn đề có thể “chữa” chỉ bằng `addSuppressed()`.

Khi tạo mã cleanup thủ công, hãy ghi rõ đối tượng nào đã được khởi tạo thành công và ai có quyền đóng, trước khi tính đến cách bảo toàn exception.

## 12. Khác biệt giữa `cause` và `suppressed` khi bọc lỗi

Đôi khi tầng ứng dụng cần chuyển một `IOException` thành ngoại lệ nghiệp vụ. Hãy **giữ chính đối tượng gốc** làm cause; lỗi cleanup đã gắn trên nó sẽ được giữ nguyên:

```java
try {
    lowLevelOperation();
} catch (IOException original) {
    throw new IllegalStateException("Import failed", original);
}
```

Đây là mảnh minh họa; `lowLevelOperation()` phải được khai báo phù hợp.

Nếu `original.getSuppressed()` có lỗi đóng stream, caller có thể quan sát:

```text
IllegalStateException: Import failed     [wrapper]
    |
    +-- getCause() -> IOException        [primary thấp hơn]
                          |
                          +-- getSuppressed() -> close failure
```

Không cần chuyển thủ công mọi suppressed từ cause sang wrapper nếu chỉ muốn **giữ thông tin lỗi**. Constructor `(message, cause)` đã lưu chính đối tượng gốc cùng danh sách suppressed của nó. Nếu tự sao chép từng suppressed rồi gắn cả trên wrapper, log có thể chứa thông tin lặp hoặc gây hiểu sai quan hệ lỗi.

Trường hợp `cause` và `suppressed`:

| API | Mục đích |
| --- | --- |
| `new DomainException("...", original)` | Bọc lỗi, giữ liên kết nhân quả với lỗi gốc |
| `primary.addSuppressed(cleanup)` | Gắn lỗi dọn dẹp bổ sung mà không đổi primary |
| `wrapper.getCause()` | Đọc nguyên nhân của wrapper |
| `primary.getSuppressed()` | Đọc lỗi phụ trực tiếp gắn trên primary |
| `printStackTrace()` | Trình bày stack trace, cause và suppressed nếu có |

### Ví dụ chạy được: bọc lỗi thủ công vẫn giữ suppressed

Tạo tệp **`WrappedManualCleanupDemo.java`**:

```java
import java.io.IOException;

public class WrappedManualCleanupDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("cleanup failed");
        }
    }

    static class ImportException extends Exception {
        ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static void importData() throws ImportException {
        Resource resource = new Resource();
        IOException primary = null;

        try {
            throw new IOException("read failed");
        } catch (IOException failure) {
            primary = failure;
        } finally {
            try {
                resource.close();
            } catch (IOException cleanup) {
                if (primary == null) {
                    primary = cleanup;
                } else if (primary != cleanup) {
                    primary.addSuppressed(cleanup);
                }
            }
        }

        if (primary != null) {
            throw new ImportException("cannot import", primary);
        }
    }

    public static void main(String[] args) {
        try {
            importData();
        } catch (ImportException failure) {
            System.out.println("outer=" + failure.getMessage());
            System.out.println("cause="
                    + failure.getCause().getMessage());
            System.out.println("suppressed="
                    + failure.getCause().getSuppressed()[0].getMessage());
        }
    }
}
```

```bash
javac WrappedManualCleanupDemo.java
java WrappedManualCleanupDemo
```

`stdout`:

```text
outer=cannot import
cause=read failed
suppressed=cleanup failed
```

Ở đây code cố ý chỉ tạo **checked IOException** trong `try` và `close`, đủ để minh họa việc wrapper giữ cause có suppressed. Đó không phải template tổng quát cho mọi unchecked exception như mẫu ở mục 5. Khi viết helper tổng quát, hãy bảo toàn cả trường hợp phần thân ném `RuntimeException`/`Error`.

## 13. So sánh cleanup thủ công với `try-with-resources`

Một mục tiêu của `try-with-resources` (Java 7+) là tránh phải viết lại logic bảo toàn primary/suppressed phức tạp.

### Thủ công

```java
Resource resource = acquire();
Throwable primary = null;
try {
    work(resource);
} catch (Throwable failure) {
    primary = failure;
    throw failure;
} finally {
    if (primary != null) {
        try {
            resource.close();
        } catch (Throwable cleanup) {
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
    } else {
        resource.close();
    }
}
```

Đây là **mảnh mã ý tưởng**; phương thức bao ngoài phải xử lý/khai báo các checked exception của `acquire`, `work` và `close`, đồng thời tuân thủ các quy tắc precise rethrow nếu dùng `catch (Throwable)`. Mã cần bao phủ đầy đủ các đường resource acquisition, nhất là nếu nhiều bước khởi tạo có thể lỗi.

### TWR

```java
try (Resource resource = acquire()) {
    work(resource);
}
```

Đây cũng là **mảnh minh họa**. Nếu resource thực thi `AutoCloseable` và scope có quyền đóng, TWR bảo đảm cleanup khi rời scope, giữ lỗi thân làm primary và ghi lỗi close thành suppressed theo quy tắc JLS.

| Tiêu chí | Cleanup thủ công | `try-with-resources` |
| --- | --- | --- |
| Ít mã boilerplate | Không | Có |
| Đóng khi `return`/exception | Phải tự tổ chức đúng | Do ngôn ngữ quản lý |
| Bảo toàn lỗi thân và lỗi close | Phải bắt, giữ và `addSuppressed` đúng | Tự động trong tình huống chuẩn |
| Nhiều resource | Phải bảo đảm đóng hết, đúng thứ tự | Đóng ngược thứ tự header |
| Resource khởi tạo sau thất bại | Phải bảo vệ resource đã tạo | Tự đóng resource đã quản lý trước đó |
| Kiểm tra checked exception | Phải theo chữ ký từng phương thức | Compiler suy theo resource và thân |
| Cleanup không biểu diễn bằng `AutoCloseable` | Có thể cần | Cần adapter hoặc cơ chế bổ sung |
| Quyền sở hữu resource | Phải xét | **Vẫn phải xét** |

Dùng cleanup thủ công khi có nhu cầu thật: API legacy chỉ có phương thức giải phóng riêng, nhiều bước rollback cần xử lý đặc thù, hay tài nguyên không triển khai `AutoCloseable` và adapter không phù hợp. Không chọn thủ công chỉ vì muốn “kiểm soát hơn” nhưng lại bỏ sót primary/suppressed.

## 14. Khi cleanup không phải chỉ là `close()`

Một giao dịch có thể có nhiều bước hoàn tác: rollback, hủy tệp tạm, giải phóng lock. Không phải bước nào cũng phù hợp đưa vào `try-with-resources` một cách máy móc.

Ví dụ pseudocode:

```text
primary = lỗi của công việc (nếu có)
nếu công việc thất bại:
    thử rollback
        nếu rollback cũng lỗi: addSuppressed vào primary
thử xóa tệp tạm
    nếu lỗi: addSuppressed vào primary; hoặc là primary nếu chưa có
thử giải phóng tài nguyên còn lại
    nếu lỗi: ghi nhận tương tự
ném primary hoặc hoàn thành bình thường
```

Đây là **pseudocode mô tả chính sách**. Trong ứng dụng thật, rollback chỉ cần chạy theo điều kiện transaction, còn cleanup của những resource bắt buộc vẫn cần trên cả đường thành công và thất bại.

### Không cố tiếp tục sử dụng trạng thái đã thất bại

Ghi nhận suppressed không làm rollback thành công, không sửa dữ liệu, không khôi phục connection và không giải phóng tài nguyên thay bạn. Nếu một lần cleanup lỗi, tài nguyên có thể vẫn ở trạng thái không xác định. Hệ thống cần chính sách quan sát/khắc phục: log lỗi cùng primary, đánh dấu job thất bại, hủy scope hoặc yêu cầu can thiệp tùy mức độ.

### Không dùng một lỗi cleanup làm `cause` của lỗi công việc đã xảy ra trước

Nếu lỗi ghi dữ liệu xảy ra **trước**, rồi lỗi đóng phát sinh sau, lỗi đóng không phải nguyên nhân làm lỗi ghi xuất hiện. Đặt close failure làm `cause` của work failure thường thể hiện sai quan hệ nhân quả; dùng suppressed đúng hơn.

## 15. Các trường hợp ít gặp cần biết

### 15.1. Suppression có thể được vô hiệu hóa

Một custom exception có thể gọi constructor protected của `Throwable`/`Exception` với `enableSuppression=false`. Khi đó, `primary.addSuppressed(cleanup)` không ghi thêm lỗi, và `primary.getSuppressed()` trả mảng rỗng.

Trong đa số ứng dụng, giữ suppression bật là lựa chọn phù hợp. Không nên nói rằng `addSuppressed` chắc chắn luôn lưu được lỗi phụ trong mọi loại custom Throwable.

### 15.2. Exception từ `close()` có thể giống đối tượng primary

Nếu chính **một đối tượng Throwable** bị ném lại từ `close()`, `addSuppressed` trên cùng đối tượng gây self-suppression. Mẫu thủ công trong bài tránh điều đó bằng so sánh tham chiếu `cleanup != primary`. Nếu bạn cần một chính sách khác, phải xây dựng một cơ chế biểu diễn lỗi phù hợp, không chỉ bỏ điều kiện và để lỗi mới che primary.

### 15.3. `finally` không được bảo đảm khi JVM dừng cưỡng bức

`finally` và TWR hoạt động theo đường thực thi Java thông thường. Chúng không bảo đảm chạy khi JVM bị dừng cưỡng bức, `Runtime.halt` được gọi hoặc tiến trình gặp sự cố ngoài mô hình exception bình thường. Không dựa vào cleanup duy nhất để giữ tính toàn vẹn của dữ liệu cần khả năng phục hồi sau mất điện.

### 15.4. `close()` cũng có thể là thao tác nghiệp vụ quan trọng

Một output stream có buffer có thể flush khi đóng. Nếu đóng thất bại, đừng in “saved successfully” hoặc tiếp tục như thể dữ liệu đã bền vững. Đôi khi **cleanup failure là primary**, và đó là điều đúng nếu công việc trước đó chưa gây lỗi.

### 15.5. Log thông tin lỗi mà không đánh mất cấu trúc

`System.err.println(failure.getMessage())` không hiển thị cause và suppressed. Khi cần phân tích, có thể dùng `failure.printStackTrace()` hoặc logging framework nhận **đối tượng throwable**. Tránh in stack trace nhạy cảm cho người dùng cuối và tránh ghi cùng lỗi lặp ở mọi tầng.

## 16. Những lỗi và hiểu nhầm phổ biến

**“`finally { resource.close(); }` luôn giữ lỗi gốc.”** Sai. Nếu close ném, ngoại lệ close có thể thay thế ngoại lệ từ work.

**“Bắt lỗi close và không làm gì là an toàn.”** Không hẳn. Nếu work thành công mà close lỗi, bạn có thể báo thành công sai và mất dấu việc flush/đóng thất bại.

**“`addSuppressed` tự ném primary.”** Sai. Nó chỉ ghi nhận một lỗi phụ vào đối tượng primary; code vẫn phải xử lý hoặc truyền lỗi theo hợp đồng.

**“Lỗi close luôn là suppressed.”** Sai. Nếu không có primary trước đó, lỗi close trở thành primary.

**“Chỉ cần bắt `IOException` trong work là đã lưu được mọi primary.”** Sai. Work vẫn có thể ném `RuntimeException` hay `Error` và cần xét riêng khi thiết kế mẫu cleanup tổng quát.

**“Đã gọi `close()` của resource thứ hai thì chắc chắn resource thứ nhất cũng được đóng.”** Sai trong cleanup thủ công dùng hai lời gọi liên tiếp nếu lời gọi đầu ném ngoại lệ và không được bắt.

**“Đóng hai resource phải từ trái sang phải.”** Không phải quy tắc TWR; TWR đóng ngược thứ tự header. Khi tự quản lý, chọn thứ tự theo phụ thuộc thực tế, thường đóng ngược thứ tự sở hữu/khởi tạo.

**“Nếu tạo resource thứ hai thất bại, `finally` viết ở phía sau vẫn luôn đóng resource thứ nhất.”** Sai. Chưa vào khối `try` thì `finally` của nó không hoạt động.

**“Bọc exception bằng message là đủ.”** Sai. Phải giữ chính đối tượng primary làm cause để truy vết stack trace và suppressed.

**“`getSuppressed()` trả về null khi trống.”** Sai. Nó trả mảng rỗng.

**“Có thể `primary.addSuppressed(primary)`.”** Sai. Java từ chối self-suppression trực tiếp.

**“Nếu không có suppressed thì chắc chắn không có lỗi cleanup.”** Không chắc. Cleanup có thể chưa chạy, lỗi đã bị bỏ qua, hoặc suppression trên primary bị tắt.

**“Mọi `AutoCloseable.close()` đều chỉ ném `IOException`.”** Sai. `AutoCloseable.close()` khai báo `throws Exception`; kiểu cụ thể có thể khác.

**“Dùng TWR là không cần xét ownership.”** Sai. Nếu resource được mượn từ caller hay framework, đóng tự động sai scope vẫn gây hỏng luồng công việc.

**“Suppressed là lỗi đã được sửa.”** Sai. Đây chỉ là cơ chế **giữ thông tin**, không phải phục hồi.

**“Đóng trùng một resource luôn vô hại.”** Không. Tùy API, close có thể có side effect, ném lỗi hoặc không idempotent.

**“Bắt mọi `Throwable` rồi tiếp tục chạy sau `Error` là thực hành tốt.”** Không. Nếu catch Throwable để giữ nguyên lỗi lúc cleanup, cần **ném lại** và không xem các `Error` nghiêm trọng là tình huống phục hồi mặc định.

## 17. Bài tập tự kiểm tra

1. **Work ném A, close ném B, `finally` gọi close trực tiếp: có thể quan sát lỗi nào?** B có thể thay A nếu không có cơ chế giữ A.
2. **Muốn giữ A, B ở đâu?** A là primary; B là suppressed gắn vào A qua `addSuppressed`.
3. **Work thành công, close ném B, nên làm gì?** B trở thành primary hoặc được xử lý như thất bại theo hợp đồng; không tự báo thành công.
4. **`addSuppressed(B)` có thay đổi kiểu của A không?** Không.
5. **Nếu có B và C từ hai bước cleanup, có thể gắn cả hai không?** Có, qua hai lần `addSuppressed` trên primary, theo thứ tự ghi nhận.
6. **Hai resource first/second, second close lỗi: có nên cố đóng first?** Có nếu hợp đồng cleanup yêu cầu và có thể thực hiện an toàn; không để lỗi đầu ngăn việc đóng resource còn lại.
7. **Nếu work thành công và second close là lỗi đầu tiên, còn first close lỗi sau, primary là gì?** Lỗi đóng second; lỗi đóng first là suppressed của nó.
8. **Nếu việc tạo second thất bại trước khi vào try, first có chắc được đóng bởi finally phía sau không?** Không.
9. **Tại sao bắt duy nhất `IOException` chưa đủ làm mẫu tổng quát?** Vì work/cleanup có thể có unchecked `RuntimeException` hoặc `Error`.
10. **Có thể dùng `primary.addSuppressed(primary)` không?** Không, gây `IllegalArgumentException`.
11. **Nếu custom primary có suppression disabled, `getSuppressed()` như thế nào?** Mảng rỗng; không ghi được lỗi phụ bằng cơ chế chuẩn này.
12. **Bọc primary thành DomainException thì lưu ở đâu?** Ở `cause` của wrapper; suppressed của primary đi theo đối tượng cause.
13. **Lỗi rollback phát sinh sau lỗi ghi dữ liệu có phải cause của lỗi ghi không?** Thường không; đó là lỗi bổ sung và nên là suppressed nếu cùng đơn vị công việc.
14. **Vì sao TWR thường an toàn hơn?** Java tự đóng đúng scope, bảo toàn primary/suppressed và xử lý nhiều resource theo quy tắc được đặc tả.
15. **Một phương thức được truyền resource borrowed có nên tự động đóng để tránh leak không?** Không nếu hợp đồng caller giữ ownership; hãy xác định quyền đóng.
16. **`finally` chạy chắc chắn khi process bị kill cưỡng bức?** Không.
17. **Đọc chỉ `getMessage()` có đủ chẩn đoán lỗi cleanup không?** Thường không; cần xem throwable đầy đủ và `getSuppressed()`.
18. **Nếu một handler bắt `Throwable` để nhớ primary, sau cleanup có nên nuốt lỗi không?** Không; phải ném lại hoặc đưa sang cơ chế báo thất bại tương ứng, không giả định có thể tiếp tục.

## 18. Tài liệu chuẩn Java 17

- [Java Language Specification 17 — `14.20.2, Execution of try-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): ngoại lệ mới từ `finally` có thể thay thế lý do thoát cũ.
- [Java Language Specification 17 — `14.20.3, try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): cách Java giữ primary/suppressed, thứ tự đóng và lỗi khi khởi tạo resource.
- [Java Language Specification 17 — `11.2, Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html#jls-11.2): checked exception và precise rethrow.
- [Java SE 17 API — Throwable.addSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#addSuppressed(java.lang.Throwable)): kiểm tra null và self-suppression.
- [Java SE 17 API — Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()): đọc danh sách lỗi phụ.
- [Java SE 17 API — AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng `close()`, checked exception và idempotency.
- [Java SE 17 API — Throwable.getCause](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getCause()): phân biệt nguyên nhân và lỗi phụ.

**Ghi nhớ:** Khi phải dọn dẹp thủ công, đừng để lỗi cleanup **che mất** lỗi công việc. Nếu đã có primary, giữ nguyên đối tượng đó và gắn lỗi cleanup bằng `addSuppressed`; nếu chưa có primary, lỗi cleanup phải được báo như một lỗi thực sự. Với nhiều resource, cố đóng các resource còn thuộc quyền sở hữu, giữ thứ tự và cả các lỗi phát sinh. Nếu dùng được `try-with-resources`, hãy ưu tiên nó để tránh tự triển khai sai một quy trình xử lý ngoại lệ vốn rất dễ sót nhánh.


</details>

- [Quay lại đầu trang](#back-to-top)
