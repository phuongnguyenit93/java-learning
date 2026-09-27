# Bootstrap, Platform và Application ClassLoader

Sau chương Lifecycle, ta biết JVM cần loader để biến class bytes thành runtime type. Câu hỏi tiếp theo là: **trong một tiến trình Java 21 bình thường, loader nào đã tồn tại sẵn trước khi ta viết custom loader?**

JDK hiện đại tổ chức phần lớn class loading mặc định thành một chuỗi khái niệm:

```text
Bootstrap loader
       ↑
Platform ClassLoader
       ↑
Application/System ClassLoader
       ↑
class của ứng dụng
```

Các loader này có phạm vi trách nhiệm khác nhau. Hiểu đúng chúng giúp ta đọc `Class#getClassLoader()`, chẩn đoán “class ở đâu ra”, và chuẩn bị cho parent delegation ở chương sau.

Vì sao Java không gom mọi thứ vào một loader duy nhất? Việc tách ownership thành các tầng giúp runtime tạo ranh giới rõ giữa **core JDK classes**, **platform classes** và **application classes**. Nhờ vậy class nền tảng có một nơi sở hữu ổn định, code ứng dụng không phải tự định nghĩa lại chúng, và custom loader sau này có thể chọn chính xác tầng nào làm parent để quyết định phần nào được chia sẻ và phần nào cần cô lập.

## <a id="bootstrap-loader">Bootstrap loader</a>

Bootstrap loader chịu trách nhiệm cho các class nền tảng cốt lõi mà JVM/JDK runtime cần, ví dụ nhiều class trong `java.base`.

```java
public class BootstrapDemo {
    public static void main(String[] args) {
        System.out.println(String.class.getClassLoader());
        System.out.println(Object.class.getClassLoader());
    }
}
```

Trong JVM thông thường, kết quả là:

```text
null
null
```

`null` ở đây **không có nghĩa class không có loader**. Java API dùng `null` để biểu diễn rằng class được bootstrap loader định nghĩa, vì bootstrap loader không được công khai như một object `java.lang.ClassLoader` bình thường.

Cách hiểu:

```text
String.class.getClassLoader() == null
→ bootstrap-defined class
→ không phải “không ai load String”
```

Trong Java hiện đại, không nên hiểu các core runtime class như thể chúng chỉ đến từ một `rt.jar` kiểu Java 8. JDK 9+ dùng module runtime image; cách lưu trữ có thể khác, nhưng vai trò bootstrap loading vẫn còn.

## <a id="platform-loader">Platform ClassLoader</a>

`ClassLoader.getPlatformClassLoader()` trả về platform ClassLoader.

Vai trò của nó là load các platform classes không thuộc tập bootstrap cốt lõi nhưng vẫn là một phần của runtime/platform modules.

```java
public class PlatformLoaderDemo {
    public static void main(String[] args) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        System.out.println(platform);
        System.out.println(platform.getParent());
    }
}
```

`platform.getParent()` thường trả `null`, tức parent logic của nó là bootstrap loader được API biểu diễn bằng `null`.

Một cách quan sát khác:

```java
System.out.println(java.sql.Driver.class.getClassLoader());
```

Trên JDK chuẩn, nhiều class thuộc platform modules như `java.sql` được platform loader định nghĩa. Không nên hard-code tên concrete implementation class của loader vào logic nghiệp vụ; API contract quan trọng hơn tên implementation nội bộ.

## <a id="application-loader">Application/System ClassLoader</a>

Application ClassLoader là loader thông thường dùng cho code ứng dụng trên classpath/module-path.

```java
public class ApplicationLoaderDemo {
    public static void main(String[] args) {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader defining = ApplicationLoaderDemo.class.getClassLoader();

        System.out.println(system);
        System.out.println(defining);
        System.out.println(system == defining);
    }
}
```

Trong cách khởi chạy thông thường, class của project sẽ do system/application loader định nghĩa, nên phép so sánh cuối thường là `true`.

Tên “system ClassLoader” đến từ API `ClassLoader.getSystemClassLoader()`; trong JVM thông thường nó đóng vai application loader. Java cho phép cấu hình custom system loader, vì vậy code thư viện không nên suy ra quá nhiều chỉ từ concrete class name của loader.

Đối với ví dụ plugin xuyên suốt:

```text
Plugin API nằm trong ứng dụng
→ Application ClassLoader load

plugin implementation đóng gói cùng ứng dụng
→ Application ClassLoader cũng có thể load

plugin implementation ở nguồn riêng
→ chương CustomClassLoader sẽ dùng loader con
```

## <a id="loader-chain">Chuỗi parent của các loader có sẵn và biểu diễn bootstrap bằng null</a>

Ta có thể quan sát chuỗi từ một class của ứng dụng:

```java
public class LoaderChainDemo {
    public static void main(String[] args) {
        ClassLoader loader = LoaderChainDemo.class.getClassLoader();

        while (loader != null) {
            System.out.println(loader);
            loader = loader.getParent();
        }

        System.out.println("<bootstrap represented by null>");
    }
}
```

Kết quả quan sát thường có dạng:

```text
Application/System loader
Platform loader
<bootstrap represented by null>
```

Đừng đọc chuỗi này như cây kế thừa của object Java. Nó là **quan hệ parent dùng cho delegation**.

```text
application loader
getParent()
→ platform loader

platform loader
getParent()
→ null
→ bootstrap boundary
```

Một bảng định hướng:

| Loader | Trách nhiệm điển hình | API quan sát |
| --- | --- | --- |
| Bootstrap | Core runtime classes | `SomeCoreClass.class.getClassLoader() == null` |
| Platform | Platform modules/classes | `ClassLoader.getPlatformClassLoader()` |
| Application/System | Application classpath/module-path | `ClassLoader.getSystemClassLoader()` |

Điểm quan trọng là một yêu cầu nạp class từ application loader không có nghĩa application loader sẽ tự định nghĩa class đó. Nó thường **delegate lên parent trước**. Chương tiếp theo giải thích chính xác vì sao chuỗi này hoạt động theo hướng đó.
