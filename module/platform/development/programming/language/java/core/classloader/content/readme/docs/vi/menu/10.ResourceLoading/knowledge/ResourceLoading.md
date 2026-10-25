<a id="back-to-top"></a>

# Tài nguyên

## Menu
- [Resource là gì và quan hệ với classpath](#resource-loading-001)
- [Class.getResource: đường dẫn tương đối và dấu / ở đầu](#resource-loading-002)
- [ClassLoader.getResource: đường dẫn tính từ gốc tài nguyên](#resource-loading-003)
- [getResourceAsStream và xử lý trường hợp không tìm thấy](#resource-loading-004)
- [getResources, resources và liệt kê nhiều tài nguyên cùng tên](#resource-loading-005)
- [findResource, findResources và điểm mở rộng cho loader tùy chỉnh](#resource-loading-006)
- [Thứ tự ủy quyền tài nguyên phải nhất quán với chính sách nạp lớp](#resource-loading-007)
- [file:, jar:, jrt: và giới hạn chuyển URL thành File](#resource-loading-008)
- [Đọc tài nguyên trong JAR và đóng InputStream đúng cách](#resource-loading-009)
- [Tài nguyên named module và các quy tắc đóng gói, opens](#resource-loading-010)

## <a id="resource-loading-001">Resource là gì và quan hệ với classpath</a>

<details>
<summary>Xem chi tiết</summary>

# Resource là gì và quan hệ với classpath trong Java

## 1. Resource là gì?

Trong Java, **resource** là dữ liệu mà chương trình muốn đọc theo một **tên tài nguyên** (resource name), không phụ thuộc trực tiếp vào việc dữ liệu đang nằm trong một thư mục trên đĩa hay trong một tệp JAR. Resource thường là dữ liệu đi kèm ứng dụng hoặc thư viện:

- Tệp cấu hình mặc định: `config/app.properties`, `logback.xml`, `META-INF/services/...`.
- Mẫu văn bản, trang HTML, câu lệnh SQL, bản dịch: `templates/email.html`, `sql/schema.sql`, `messages_vi.properties`.
- Ảnh, biểu tượng, chứng chỉ công khai, dữ liệu thử nghiệm, tệp JSON hoặc dữ liệu nhị phân.
- Ngay cả file `.class` cũng có thể được tìm như một resource, mặc dù **tìm bytes tài nguyên** và **định nghĩa một kiểu Java** là hai thao tác khác nhau.

Ví dụ, ứng dụng muốn đọc `demo/config/message.txt`. Khi chạy ở môi trường phát triển, bytes có thể nằm tại `resources/demo/config/message.txt`. Khi đóng gói, chúng có thể nằm **bên trong JAR** với entry name `demo/config/message.txt`. Mã Java dùng tên resource thích hợp có thể đọc cả hai tình huống mà không cần tự giả định đường dẫn file vật lý.

**Điểm cốt lõi:** *Classpath* cung cấp **các gốc tìm kiếm (search roots)** cho cả class và resource trong kiểu chạy classpath truyền thống. *Resource name* là đường dẫn **tương đối với gốc đó**, thường phân tách bằng dấu `/`. Một resource **không cần là Java class**, không có constructor, và không tự trở thành đối tượng `Class<?>` chỉ vì xuất hiện trên classpath.

Bài này giải thích chính xác tên resource, cấu trúc classpath, Java API tìm resource, khác biệt giữa `Class` và `ClassLoader`, việc chạy trong IDE/JAR, xử lý thiếu tài nguyên, nhiều tài nguyên cùng tên và lưu ý với JPMS từ Java 9.

## 2. Classpath là gì?

**Classpath** (đường tìm lớp) là tập các vị trí mà công cụ Java có thể sử dụng để tìm class và resource trong cách chạy dựa trên classpath. Khi truyền `-cp` hoặc `--class-path` cho lệnh `java`, bạn chỉ định những thành phần như:

- **Thư mục gốc** chứa các thư mục package và các file đã biên dịch.
- **Tệp JAR** chứa các class file và resource entries.
- Nhiều thư mục/JAR được liệt kê theo cú pháp phân tách thích hợp với hệ điều hành.

Ví dụ trên Windows:

~~~powershell
java -cp "out;resources;lib/util.jar" demo.Main
~~~

Tương đương về ý tưởng trên Linux/macOS:

~~~bash
java -cp "out:resources:lib/util.jar" demo.Main
~~~

Dấu phân tách classpath là `;` trên Windows và `:` trên Linux/macOS. Dấu `/` nằm **bên trong tên resource** là một quy tắc khác: `demo/config/message.txt` có cùng tên logic trên các hệ điều hành.

Khi `resources` là một **classpath root**, resource `demo/config/message.txt` ứng với:

~~~text
resources/
└── demo/
    └── config/
        └── message.txt
~~~

Khi `out` là classpath root, class có binary name `demo.Main` ứng với:

~~~text
out/
└── demo/
    └── Main.class
~~~

**Không truyền nguyên một đường dẫn file resource như thể đó là classpath root.** Nếu đưa `resources/demo/config/message.txt` vào `-cp` thay vì thư mục `resources`, cơ chế tìm kiếm thông thường sẽ không hiểu đó là một entry cung cấp đúng resource name `demo/config/message.txt`.

Classpath dùng khi **biên dịch** và khi **chạy** có thể khác nhau. `javac -cp` giúp trình biên dịch phân giải các kiểu cần thiết; `java -cp` quyết định đường tìm khi khởi chạy runtime. Một chương trình compile thành công vẫn có thể không đọc được resource nếu gói chạy bỏ quên thư mục resource/JAR.

## 3. Mối quan hệ giữa class và resource trên classpath

Một class và một resource bất kỳ đều có thể được cung cấp từ cùng thư mục/JAR:

~~~text
project/
├── src/
│   └── demo/
│       └── Main.java
├── resources/
│   ├── app.properties
│   └── demo/
│       └── config/
│           └── message.txt
└── out/
    └── demo/
        └── Main.class
~~~

Nếu chạy với `out` và `resources` cùng nằm trên classpath:

- `demo.Main` là **binary name của class**; JVM/class loader tìm biểu diễn class thích hợp, ví dụ entry `demo/Main.class`.
- `demo/config/message.txt` là **resource name**, trỏ đến bytes tùy ý không cần cú pháp Java.
- `app.properties` là resource đặt **ở classpath root**, không thuộc package `demo`.
- `Main.class` có thể được xem như resource bytes bằng tên `demo/Main.class`, nhưng đọc stream không tương đương `ClassLoader.defineClass` và không làm code trong class chạy.

Về phía Java, người viết chương trình thường không cần biết resource nằm trong `file:` URL hay `jar:` URL. Một resource có thể ở trong JAR, trong mô-đun runtime hoặc được custom loader cung cấp bằng cơ chế khác.

**Không nên hiểu classpath là một chuỗi đường dẫn filesystem duy nhất.** Đây là cơ chế tìm kiếm nhiều vị trí, đồng thời những class loader riêng có thể sở hữu phạm vi tìm kiếm khác nhau. Trong Java 9+, named modules còn có quy tắc resource riêng, được trình bày ở phần sau.

## 4. Resource name khác filesystem path

Resource name có đặc trưng:

- Dùng **dấu `/`** để ngăn các thành phần, như `demo/config/message.txt`.
- Thường được hiểu **tương đối với classpath root hoặc phạm vi tìm của class loader**.
- Có phân biệt chữ hoa/chữ thường tùy nguồn đóng gói và môi trường; nên viết đúng tên và không dựa vào sự khoan dung của filesystem.
- Không được giả định là đường dẫn tuyệt đối tới file ngoài đĩa.
- Không tự động lấy `user.dir` làm gốc nếu API được gọi là `ClassLoader.getResource`.

Đối chiếu ba cách truy cập khác nhau:

| Cách viết | Ý nghĩa |
| --- | --- |
| `Path.of("config/app.properties")` | Đường dẫn file tương đối theo working directory của tiến trình |
| `ClassLoader.getSystemResource("config/app.properties")` | Tên resource đối với system class loader |
| `MyClass.class.getResource("config/app.properties")` | Resource **tương đối package của MyClass** theo quy tắc của `Class` |

Cả ba có thể đọc được các bytes tương tự nếu bạn cố ý bố trí thư mục thích hợp, nhưng **không có hợp đồng rằng chúng trỏ cùng nơi**.

Ví dụ working directory là `C:\work\project`, còn resource nằm trong `app.jar`. `Path.of("config/app.properties")` sẽ tra một file trên đĩa liên hệ working directory; nó **không tự chui vào JAR** để đọc entry `config/app.properties`. Ngược lại, `ClassLoader.getResourceAsStream` có thể đọc entry bên trong JAR nếu JAR thuộc miền tìm resource.

## 5. Ba API quan trọng: URL, InputStream và liệt kê

Các phương thức thường dùng:

| API | Trả về | Khi nên dùng |
| --- | --- | --- |
| `ClassLoader.getResource(String)` | `URL` hoặc `null` | Cần địa chỉ logic của một resource, hoặc mở stream từ URL |
| `ClassLoader.getResourceAsStream(String)` | `InputStream` hoặc `null` | Muốn đọc bytes trực tiếp, kể cả khi resource ở JAR |
| `ClassLoader.getResources(String)` | `Enumeration<URL>` | Muốn tìm **tất cả** resource trùng tên có thể nhìn thấy |
| `ClassLoader.resources(String)` | `Stream<URL>` | API Java 9+ để stream nhiều resource |
| `ClassLoader.getSystemResource(String)` | `URL` hoặc `null` | Tìm qua **system class loader** |
| `Class.getResource(String)` | `URL` hoặc `null` | Tìm theo lớp tham chiếu; hỗ trợ tên tương đối package |
| `Class.getResourceAsStream(String)` | `InputStream` hoặc `null` | Đọc bytes theo lớp tham chiếu, hỗ trợ quy tắc package |

`URL` chỉ địa điểm hoặc khả năng truy cập một resource; nó không đảm bảo là file local. `InputStream` cung cấp bytes và cần đóng bằng `try-with-resources`. Khi `getResourceAsStream` trả `null`, nguyên nhân có thể là tài nguyên không tìm thấy, quyền truy cập/resource encapsulation, hoặc cách đóng gói sai.

**Ưu tiên mở stream để đọc nội dung**, thay vì cố biến mọi URL thành `File`. Đối với `jar:file:...!/entry`, phép chuyển `Path.of(url.toURI())` theo filesystem mặc định thường không hoạt động như với URL `file:`.

## 6. Quy tắc đặt tên: Class.getResource và ClassLoader.getResource

Đây là phần dễ gây lỗi nhất.

Giả sử class đang thực thi có package `demo`:

~~~java
package demo;

public class Reader {
}
~~~

Và file resource đặt ở vị trí logic:

~~~text
classpath root/
└── demo/
    └── config/
        └── message.txt
~~~

Các lời gọi tương ứng:

| Lời gọi | Tên resource logic sau khi áp dụng quy tắc | Kỳ vọng |
| --- | --- | --- |
| `Reader.class.getResource("config/message.txt")` | `demo/config/message.txt` | Tìm được |
| `Reader.class.getResource("/demo/config/message.txt")` | `demo/config/message.txt` | Tìm được |
| `Reader.class.getResource("message.txt")` | `demo/message.txt` | Không có nếu resource chỉ ở `demo/config/` |
| `Reader.class.getClassLoader().getResource("demo/config/message.txt")` | `demo/config/message.txt` | Tìm được |
| `Reader.class.getClassLoader().getResource("config/message.txt")` | `config/message.txt` | Không có trong bố trí này |
| `ClassLoader.getSystemResource("demo/config/message.txt")` | `demo/config/message.txt` | Tìm được trong cách chạy chuẩn nếu đúng trên system classpath |

**Điểm khác biệt:**

- `Class.getResource("relative/name")`: nếu tên **không bắt đầu bằng `/`**, Java tự thêm phần package của class đó (`demo/` trong ví dụ).
- `Class.getResource("/absolute/name")`: nếu tên **bắt đầu bằng `/`**, API `Class` bỏ dấu `/` đầu và dùng tên **tính từ gốc**.
- `ClassLoader.getResource("name")`: tên đã được coi là tính từ **gốc tìm của loader**, nên thông thường **không đặt dấu `/` đầu**. Truyền `"/demo/config/message.txt"` vào `ClassLoader.getResource` không phải cách gọi chuẩn tương đương với `Class.getResource("/demo/config/message.txt")`.
- Với một `Class` thuộc **named module**, `Class.getResource` tìm theo module của class, ngoài quy tắc package; không thể hoàn toàn quy nó về việc gọi system class loader.

Nên chọn một kiểu tìm nhất quán: với resource đi theo class/thư viện, `MyClass.class.getResourceAsStream(...)` thường thích hợp; với một loader cụ thể, `loader.getResourceAsStream("absolute/from/loader/root")` thích hợp.

## 7. Ví dụ Java 17+ chạy được: dùng resource ở thư mục classpath

Mục tiêu là chạy **cùng code** khi resource nằm trong thư mục `resources` hoặc khi cả code và resource được đóng trong JAR. Không sử dụng framework hoặc thư viện ngoài JDK.

### 7.1. Cấu trúc dự án thực hành

Tạo đúng các file sau:

~~~text
resource-lab/
├── src/
│   └── demo/
│       └── ResourceExample.java
└── resources/
    ├── app.properties
    └── demo/
        └── config/
            └── message.txt
~~~

Nội dung **resources/demo/config/message.txt** (một dòng; cuối file có newline hay không đều được):

~~~text
Hello from classpath!
~~~

Nội dung **resources/app.properties**:

~~~properties
mode=demo
~~~

### 7.2. File Java đầy đủ

File **src/demo/ResourceExample.java**:

~~~java
package demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URL;

public class ResourceExample {
    private static String read(String resourceName) throws IOException {
        try (InputStream input =
                     ResourceExample.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Missing resource: " + resourceName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws IOException {
        ClassLoader loader = ResourceExample.class.getClassLoader();

        URL relative = ResourceExample.class.getResource(
                "config/message.txt");
        URL absolute = ResourceExample.class.getResource(
                "/demo/config/message.txt");
        URL byLoader = loader.getResource(
                "demo/config/message.txt");

        System.out.println("relative: " + (relative != null));
        System.out.println("absolute: " + (absolute != null));
        System.out.println("by loader: " + (byLoader != null));

        System.out.println("message: " +
                read("/demo/config/message.txt"));
        System.out.println("properties: " +
                read("/app.properties"));
    }
}
~~~

Chương trình có `package demo;` và cần tên file đúng `ResourceExample.java`. Nó lấy ba URL nhưng **không in chính URL**, vì địa chỉ vật lý thay đổi giữa máy chạy và giữa thư mục/JAR. Hàm `read` dùng `try-with-resources` để stream luôn được đóng, kiểm tra `null` để báo thiếu resource rõ ràng, giải mã UTF-8 tường minh và bỏ whitespace ở đầu/cuối thông qua `trim()` để stdout nhất quán khi file có hoặc không có newline cuối.

### 7.3. Biên dịch và chạy trên Windows (PowerShell)

Đứng tại thư mục `resource-lab`:

~~~powershell
javac --release 17 -d out src/demo/ResourceExample.java
java -cp "out;resources" demo.ResourceExample
~~~

### 7.4. Biên dịch và chạy trên Linux/macOS

~~~bash
javac --release 17 -d out src/demo/ResourceExample.java
java -cp "out:resources" demo.ResourceExample
~~~

### 7.5. Stdout chính xác

Cả hai cách chạy trên, nếu cây thư mục và nội dung resource đúng, đều in:

~~~text
relative: true
absolute: true
by loader: true
message: Hello from classpath!
properties: mode=demo
~~~

**Giải thích theo dòng:**

1. `relative: true`: `ResourceExample.class.getResource("config/message.txt")` thêm tiền tố package `demo/`, nên tìm `demo/config/message.txt`.
2. `absolute: true`: `Class.getResource("/demo/config/message.txt")` bỏ slash đầu và tìm từ gốc.
3. `by loader: true`: loader nhận tên đã tính từ gốc `demo/config/message.txt`.
4. `message: ...`: `getResourceAsStream` đọc bytes của file message qua Class, không đòi một `File` trên đĩa.
5. `properties: ...`: tên `/app.properties` là resource ở gốc, không phải `demo/app.properties`.

Như vậy, resource không phải một class Java; đây là dữ liệu dạng văn bản, cùng được tìm qua cơ chế classpath.

## 8. Chạy lại ví dụ từ JAR

Tại thư mục `resource-lab`, sau khi đã compile tạo `out/demo/ResourceExample.class`, đóng gói cả class và resource vào JAR bằng công cụ JDK:

~~~bash
jar --create --file resource-example.jar -C out . -C resources .
~~~

Lệnh `jar` lấy nội dung thư mục `out` và `resources` làm các entry **tính từ gốc JAR**. Có thể kiểm tra:

~~~bash
jar --list --file resource-example.jar
~~~

Những entry quan trọng phải có:

~~~text
demo/ResourceExample.class
demo/config/message.txt
app.properties
~~~

JAR có thể chứa thêm manifest và entry thư mục; không cần so chính xác mọi dòng listing. Chạy chỉ với JAR trên classpath:

~~~bash
java -cp resource-example.jar demo.ResourceExample
~~~

**Stdout vẫn như mục 7.5**, vì cùng các resource names còn tồn tại tại gốc JAR:

~~~text
relative: true
absolute: true
by loader: true
message: Hello from classpath!
properties: mode=demo
~~~

Sự khác biệt nằm ở địa chỉ resource do URL trỏ tới: trong lúc phát triển nó có thể có dạng `file:...`, còn trong JAR thường là `jar:file:...!/demo/config/message.txt`. Code mở `InputStream` có thể tiếp tục hoạt động trong cả hai cách đóng gói.

**Không thay stream bằng `new File(...)` hoặc `Path.of(url.toURI())` một cách máy móc** chỉ vì ví dụ chạy được trên file URL. Đối với JAR, resource entry không phải một đường dẫn file thông thường trên filesystem mặc định.

## 9. Khi nào dùng Properties, Reader và InputStream?

Nếu resource là dữ liệu nhị phân như PNG hoặc bytecode, đọc bytes qua `InputStream`. Nếu là văn bản, cần chọn charset rõ ràng; Java 17 có thể dùng:

~~~java
try (InputStream input =
             MyClass.class.getResourceAsStream("/data/note.txt")) {
    if (input == null) {
        throw new java.io.FileNotFoundException("data/note.txt");
    }
    String content = new String(
            input.readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8
    );
}
~~~

**Đây là đoạn minh họa đặt trong một phương thức**, giả sử `MyClass` và resource tồn tại.

Với resource `.properties`, có thể dùng `java.util.Properties`:

~~~java
java.util.Properties props = new java.util.Properties();
try (InputStream input =
             MyClass.class.getResourceAsStream("/app.properties")) {
    if (input == null) {
        throw new java.io.FileNotFoundException("app.properties");
    }
    props.load(input);
}
String mode = props.getProperty("mode");
~~~

Đây cũng là đoạn minh họa trong phương thức. Lưu ý `Properties.load(InputStream)` sử dụng quy tắc encoding của định dạng properties truyền thống (ISO-8859-1, Unicode escapes), **không tự giải mã mọi tệp UTF-8 theo cách giống `new String(bytes, UTF_8)`**. Nếu muốn dùng UTF-8 cho file properties theo chủ đích, cung cấp một `Reader` với charset tương ứng qua `Properties.load(Reader)`.

**Không ghi nội dung runtime vào resource bên trong JAR.** Resources đóng gói chủ yếu là dữ liệu triển khai đọc được; cấu hình cần thay đổi thường thuộc file, volume, biến môi trường hoặc dịch vụ cấu hình ngoài artifact.

## 10. Parent delegation và vùng tìm kiếm resource

Đối với `ClassLoader.getResource(name)`, cách triển khai mặc định tìm ở **parent class loader trước**, rồi tìm resource mà loader hiện tại cung cấp qua `findResource`. Điều này tương tự ý tưởng parent-first của class loading, nhưng không phải mọi custom loader nhất thiết dùng đúng chiến lược đó.

Ví dụ:

~~~text
           ParentLoader
            ├── config/app.properties   [bản parent]
            |
            v
           ChildLoader
            └── config/app.properties   [bản child]
~~~

Nếu loader con dùng `getResource("config/app.properties")` theo parent-first mặc định, resource của **parent** có thể là kết quả đầu tiên. Khi có nhiều resource trùng tên, kết quả còn phụ thuộc cấu hình search path, custom loader và named modules.

Một ứng dụng plugin đôi khi cần tìm resource của chính plugin thay vì resource của host. Khi đó phải chọn loader/phạm vi lookup đúng: loader của class plugin, loader theo hợp đồng của framework hoặc **Thread Context ClassLoader** khi API cụ thể yêu cầu.

Không mặc định `ClassLoader.getSystemResource(...)` tìm được mọi resource mà một plugin hoặc application server có thể truy cập. System class loader và một custom plugin loader có thể sở hữu miền tìm khác nhau.

## 11. Nhiều resource trùng tên: getResource so với getResources

Khi classpath chứa hai JAR có cùng entry `META-INF/services/com.example.Service`, gọi:

~~~java
ClassLoader loader = Thread.currentThread().getContextClassLoader();

java.net.URL first = loader.getResource(
        "META-INF/services/com.example.Service");
java.util.Enumeration<java.net.URL> all = loader.getResources(
        "META-INF/services/com.example.Service");
~~~

**Đoạn minh họa**: cần có loader, resource và môi trường thích hợp; `getResources` khai báo `throws IOException` nên code phải đặt trong phương thức xử lý ngoại lệ này.

Ý nghĩa:

- `getResource` tìm **một URL**, thường là resource đầu tiên theo chính sách tìm kiếm của loader.
- `getResources` trả nhiều URL mà loader nhìn thấy. Trong Java 9+, `loader.resources(name)` cung cấp `Stream<URL>` với ý tưởng tương tự.
- Nhiều JAR cùng cung cấp một tên resource là tình huống hợp lệ; thường gặp ở Java Service Provider Interface (SPI).
- Không dựa vào **thứ tự của các URL** như API nghiệp vụ khi Java/JDK không bảo đảm thứ tự ổn định; ở named modules, thứ tự tìm các module có thể đặc biệt khó đoán.
- Đừng chỉ lấy resource đầu tiên khi công việc của bạn thực sự yêu cầu hợp nhất nhiều providers hoặc nhiều tệp cấu hình.

Để quan sát các URL thay vì đoán đường dẫn:

~~~java
while (all.hasMoreElements()) {
    System.out.println(all.nextElement());
}
~~~

Đây là đoạn tiếp của mẫu ở trên; URL có thể có scheme và vị trí khác nhau giữa các môi trường, nên không công bố một stdout cố định.

## 12. Resource và ClassLoader không đồng nghĩa nạp Class

Xét hai API:

~~~java
Class<?> type = loader.loadClass("demo.ResourceExample");
java.net.URL url = loader.getResource("demo/ResourceExample.class");
~~~

**Đoạn minh họa**, giả sử biến `loader` hợp lệ và tên class/resource có mặt.

- `loadClass` được thiết kế để lấy một **Class runtime** từ tên nhị phân. JVM thực hiện các quy tắc loading/linking liên quan.
- `getResource` được thiết kế để tìm **dữ liệu** bằng tên đường dẫn; nó trả URL hoặc null.
- Việc đọc bytes từ `getResourceAsStream("demo/ResourceExample.class")` không tự thực hiện `defineClass`.
- Class loading có thể dùng bytes từ classpath/JAR, nhưng cách tìm/định nghĩa Class không bắt buộc là phép gọi `getResource` đơn giản từ Java source.
- Class được load qua custom loader có thể không công bố bytes gốc dưới dạng resource URL; ngược lại, một resource `.class` tìm được không nhất thiết có thể định nghĩa thành class hợp lệ bằng tên bạn tự chọn.

Đặc biệt, **class binary name** dùng dấu chấm giữa package, ví dụ `demo.ResourceExample`; **resource path** của file class dùng slash và đuôi `.class`, ví dụ `demo/ResourceExample.class`. Với lớp thành viên `Outer.Inner`, binary name thường chứa `$`, và resource path dạng `pkg/Outer$Inner.class`.

## 13. Resource khác gì tài nguyên filesystem bên ngoài ứng dụng?

Chọn resource đóng gói khi dữ liệu **đi cùng phiên bản ứng dụng**: template email mặc định, icons, SQL migration scripts (tùy framework), mẫu dữ liệu hoặc cấu hình mẫu. Chọn filesystem/database/config service khi dữ liệu cần **thay đổi mà không rebuild artifact** hoặc cần quyền ghi.

| Trường hợp | Cơ chế phù hợp |
| --- | --- |
| Đọc template đóng cùng JAR | `getResourceAsStream` |
| Đọc ảnh đi kèm thư viện | `Class.getResource` hoặc stream |
| Đọc file cấu hình do người vận hành đặt ngoài JAR | `Path` / `Files` hoặc API cấu hình của framework |
| Ghi logs, uploads, cache | Filesystem/volume/object storage, không ghi vào JAR resource |
| Tìm class theo binary name | `ClassLoader.loadClass`, không coi resource bytes là Class |
| Đọc tài nguyên từ plugin riêng | Loader của plugin hoặc cơ chế do framework chỉ định |
| Gộp nhiều SPI descriptors cùng tên | `getResources` hoặc API `ServiceLoader` phù hợp |

Một chương trình có thể sử dụng **cả hai**: resource mặc định trong JAR làm fallback, rồi đọc cấu hình ngoài đĩa để ghi đè. Cần quy định thứ tự ưu tiên rõ và không dựa vào thứ tự ngẫu nhiên của resource trùng tên.

## 14. Khác biệt quan trọng với JPMS (Java 9+)

Từ Java 9, Java Platform Module System (JPMS) giới thiệu **named module**. Lúc này không phải mọi tài nguyên đều được tìm bằng cách “duyệt classpath” thông thường.

- Classpath truyền thống đưa class vào **unnamed module** tương ứng loader.
- Module path chứa named modules có cơ chế resolution, class loading và encapsulation riêng.
- Với `Class.getResource` / `Class.getResourceAsStream`, nếu class thuộc named module, việc tìm resource có liên hệ **module của class đó**, và quyền truy cập được kiểm soát theo quy tắc `Module.getResourceAsStream`.
- Với `ClassLoader.getResource` hoặc `getResourceAsStream`, **non-.class resource trong một package của named module thường chỉ có thể được tìm khi package đó được opened unconditionally** (khai báo `opens p;` không giới hạn đích), theo Javadoc Java 17. Đây là ràng buộc nghiêm hơn một số trường hợp truy cập `Class.getResource` từ trong chính module hoặc module đã được cấp quyền mở.
- Với resource nằm ngoài một package của module, quy tắc encapsulation dành cho package không áp dụng theo cùng cách. Dù vậy cách đóng gói/cung cấp resource vẫn phải đúng.
- Một resource trong named module có thể không tìm được qua API dùng system class loader dù nhìn thấy entry bên trong modular JAR; hãy cân nhắc dùng `MyClass.class.getResourceAsStream(...)` trong ngữ cảnh module thích hợp.

Ví dụ declaration:

~~~java
module com.example.reader {
    // Khi cần mở package demo.assets không điều kiện:
    opens demo.assets;
}
~~~

Đây là **minh họa module descriptor**, không phải file hoạt động độc lập; package và resource thực tế cần tồn tại. Chỉ thêm `opens` khi hợp đồng thiết kế yêu cầu, vì mở package ảnh hưởng đến phạm vi truy cập phản chiếu/runtime.

**Không nhầm `exports` và `opens`:** `exports` phục vụ khả năng truy cập các public types từ module khác; `opens` liên quan deep reflection và các quy tắc resource encapsulation. Chỉ `exports` một package không nhất thiết làm non-.class resource trong đó hiển thị qua `ClassLoader.getResource`.

## 15. Resources trong Gradle, Maven và IDE

Trong dự án Java chuẩn, cấu trúc thường là:

~~~text
src/
├── main/
│   ├── java/
│   │   └── demo/
│   │       └── App.java
│   └── resources/
│       ├── app.properties
│       └── demo/
│           └── config/
│               └── message.txt
└── test/
    ├── java/
    └── resources/
        └── test-data.json
~~~

**Với Gradle Java plugin** và **Maven convention** thông thường, `src/main/resources` là thư mục đầu vào; build tool sao chép các tài nguyên vào output phù hợp để chúng hiện diện trên runtime classpath hoặc bên trong JAR.

- Gradle Java plugin thường copy resource đến `build/resources/main`; classes output thường nằm trong `build/classes/java/main`. Khi đóng JAR, tài nguyên được đưa vào các entry tương ứng.
- Maven thông thường copy resource đến `target/classes`, cạnh compiled classes từ `src/main/java`.
- `src/test/resources` phục vụ test classpath; không được giả định tự đi vào production JAR.
- IDE có thể thêm resource directories vào classpath tự động; chạy lệnh `java` thủ công đòi bạn cung cấp đúng path hoặc dùng JAR build output. Đây là lý do “chạy trong IDE được nhưng chạy JAR không thấy resource” xảy ra thường xuyên.

Với Spring Boot executable JAR có cấu trúc đóng gói riêng, resource vẫn có thể được class loader của runtime tìm theo đúng tên, nhưng entry bên trong có thể nằm tại một tầng như `BOOT-INF/classes/...`. Không tự chuyển đường dẫn resource thành filesystem path dựa vào bố cục bên trong JAR của một framework.

Nên kiểm tra artifact thật bằng:

~~~bash
jar --list --file application.jar
~~~

Lệnh áp dụng khi `application.jar` là file JAR bạn đang kiểm tra. Kiểm tra tên resource **tương đối với gốc tìm kiếm của môi trường chạy**, không chỉ nhìn file có tồn tại đâu đó trong mã nguồn.

## 16. Xử lý khi getResourceAsStream trả null

Một lỗi phổ biến là:

~~~java
try (InputStream input =
             MyClass.class.getResourceAsStream("settings.json")) {
    String text = new String(input.readAllBytes());
}
~~~

Đây là **mã minh họa lỗi**, giả sử có `MyClass`. Nếu resource không tìm thấy, `input == null` và `readAllBytes()` gây `NullPointerException`; thông báo không chỉ rõ tên resource.

Mẫu xử lý tốt hơn:

~~~java
String name = "/config/settings.json";
try (InputStream input = MyClass.class.getResourceAsStream(name)) {
    if (input == null) {
        throw new java.io.FileNotFoundException(
                "Classpath resource missing: " + name);
    }
    byte[] bytes = input.readAllBytes();
}
~~~

Đây là **đoạn minh họa trong phương thức**. Trong sản phẩm thực tế, có thể cần kèm tên module, loader hoặc danh sách classpath roots phục vụ debug.

Nếu không tìm thấy resource, kiểm tra theo thứ tự:

1. **Đúng tên, đúng chữ hoa/thường, đúng dấu slash?** Ví dụ `app.properties` khác `config/app.properties`.
2. **Dùng Class hay ClassLoader?** `Class.getResource("x")` tìm tương đối package; `ClassLoader.getResource("x")` tìm từ gốc loader.
3. **Classpath root có đúng không?** `resources` hay `out` có nằm trong classpath của quá trình chạy thực tế?
4. **File có nằm trong JAR/output không?** Dùng `jar --list` và xem thư mục output của build.
5. **Đang chạy trong module hay plugin riêng?** Có thể resource nằm trong module chưa opened, hoặc loader khác có phạm vi tra cứu riêng.
6. **Có nhiều resource trùng tên không?** Kiểm tra `getResources` để biết có nhiều nơi cung cấp dữ liệu.
7. **Có cố biến JAR URL thành File không?** Đọc bằng stream hoặc API filesystem JAR/ZIP thích hợp.
8. **IDE và runtime dùng cùng cấu hình không?** So sánh arguments, working directory, classpath/module path, output artifact.

Nếu API yêu cầu `InputStream`, đóng stream bằng `try-with-resources` và xử lý `IOException`. Nếu chỉ cần biết tồn tại, `getResource` trả URL hoặc null, nhưng kết quả có thể phụ thuộc quyền truy cập và module encapsulation.

## 17. Các lỗi hiểu sai thường gặp

| Hiểu sai | Điều đúng |
| --- | --- |
| “Resource là Java class” | Resource là dữ liệu có tên; `.class` chỉ là một loại file có thể được tìm |
| “Classpath là thư mục chứa source code” | Runtime classpath chứa các **gốc output/JAR** cần thiết, không tự hiểu `src/main/java` hay `src/main/resources` |
| “Tên resource là full OS path” | Tên thường là đường dẫn tương đối với gốc tìm kiếm bằng dấu `/` |
| “`Class.getResource("x")` và `ClassLoader.getResource("x")` luôn giống nhau” | Cái thứ nhất áp dụng package-relative khi không có `/` đầu |
| “Phải thêm `/` vào mọi resource name” | `ClassLoader.getResource` thường dùng tên từ gốc **không có slash đầu** |
| “Resource tìm được là file local” | Nó có thể ở JAR, module runtime hoặc provider khác |
| “`getResourceAsStream` luôn trả stream” | Có thể trả `null` nếu không tìm thấy/không được phép truy cập |
| “Một resource name luôn là một URL duy nhất” | Nhiều classpath entries có thể chứa cùng tên; `getResources` tìm nhiều |
| “`new File(url.getPath())` luôn hợp lệ” | Sai với JAR URL và resource không nằm trên filesystem thông thường |
| “System loader luôn tìm được resource của plugin” | Plugin loader/TCCL có phạm vi riêng |
| “`exports` đủ cho mọi non-.class resource trong JPMS” | Một số resource lookup chịu quy tắc `opens`, đặc biệt qua `ClassLoader` |
| “File có trong IDE là chắc chắn có trong packaged JAR” | Cần kiểm tra build output/artifact thật |

## 18. Bài tập tự kiểm tra

**1. Resource `icons/logo.png` nằm trong `src/main/resources/icons/logo.png`. Khi build tool đưa nội dung vào classpath root, tên để gọi `loader.getResource` là gì?**  
`icons/logo.png`, không bao gồm `src/main/resources`.

**2. Class thuộc package `demo`. `Demo.class.getResource("data.txt")` tìm tên nào?**  
`demo/data.txt`. Muốn từ gốc, dùng `Demo.class.getResource("/data.txt")`.

**3. Vì sao `loader.getResource("/demo/data.txt")` có thể trả null?**  
Vì API ClassLoader dùng tên từ gốc **không cần slash đầu**. Dùng `demo/data.txt`.

**4. Tại sao mã chạy trong IDE đọc được resource nhưng `java -cp out ...` không thấy?**  
Có thể `resources` không có trên runtime classpath; IDE tự bổ sung resource directory, còn lệnh thủ công chỉ đưa `out`.

**5. Tại sao không dùng `Path.of(url.toURI())` cho mọi resource?**  
Resource URL có thể dùng scheme `jar:`, `jrt:` hoặc provider khác, không tương đương file của filesystem mặc định.

**6. Có hai JAR chứa `META-INF/services/...` cùng tên. Nên dùng API nào để xem tất cả?**  
`ClassLoader.getResources(name)` hoặc `ClassLoader.resources(name)` (Java 9+).

**7. `ClassLoader.loadClass("demo.Main")` và `getResource("demo/Main.class")` có trả cùng kiểu dữ liệu không?**  
Không. Một API trả `Class<?>`, API kia trả `URL` tới bytes tài nguyên.

**8. File trong named module có thể bị che dù tồn tại vật lý không?**  
Có. Resource encapsulation và quy tắc `opens`/module ảnh hưởng khả năng tìm, nhất là qua ClassLoader API.

**9. File `app.properties` phải được sửa sau deploy; có nên ghi trở lại trong JAR không?**  
Không nên. Chọn cấu hình ngoài artifact hoặc nguồn lưu trữ dành cho dữ liệu có quyền ghi.

**10. Khi resource stream null, có nên tự động chọn một resource cùng tên ở JAR bất kỳ không?**  
Không. Cần làm rõ loader, module, classpath và chính sách ưu tiên, tránh phụ thuộc vào resource vô tình hiện diện.

## 19. Tài liệu chuẩn

- **Java SE 17, `ClassLoader` API** — getResource, getResources, getResourceAsStream, resource names, delegation và module visibility: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17, `Class` API** — getResource/getResourceAsStream, quy tắc tên tương đối package và tuyệt đối: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17, `Module` API** — module resource encapsulation và getResourceAsStream: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- **Java 17, `java` command** — runtime classpath và tùy chọn -cp: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Java 17, `jar` command** — tạo và liệt kê nội dung JAR: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-002">Class.getResource: đường dẫn tương đối và dấu / ở đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Class.getResource: đường dẫn tương đối và dấu / ở đầu

## 1. Bản chất của Class.getResource

Trong Java, *resource* là tệp dữ liệu dùng lúc chạy: cấu hình, template, thông báo, ảnh, chứng chỉ, v.v. Resource có thể ở một thư mục trên classpath, trong JAR hoặc trong một named module. Để tìm resource dựa trên một lớp, Java có:

~~~java
URL url = SomeClass.class.getResource("config.properties");
InputStream in = SomeClass.class.getResourceAsStream("config.properties");
~~~

`getResource` trả về `java.net.URL`, còn `getResourceAsStream` trả `InputStream`; cả hai có thể trả `null` khi tài nguyên không được tìm thấy hoặc không thể truy cập. Hai API **dùng chung quy tắc diễn giải tên đường dẫn**.

Điểm quan trọng nhất: **dấu `/` ở đầu chuỗi tên resource quyết định điểm bắt đầu tìm kiếm**.

- **Không có `/` đầu:** đường dẫn tương đối với **package của đối tượng `Class` gọi phương thức**.
- **Có `/` đầu:** đường dẫn tuyệt đối từ **gốc không gian tên resource**. API bỏ dấu `/` đầu khi chuyển tên cho cơ chế tìm kiếm bên dưới.
- Dấu `/` là dấu phân cách tên resource bất kể hệ điều hành; nó **không phải** đường dẫn gốc ổ đĩa hay filesystem.
- Không phải source file đang nằm ở đâu hoặc working directory là yếu tố quyết định tên tương đối. Quan trọng là **package của class được chọn** và **vị trí resource tại runtime**.

Ví dụ, class `com.acme.orders.OrderService` và resource `com/acme/orders/config.properties` thuộc cùng package resource. Câu lệnh `OrderService.class.getResource("config.properties")` chỉ định chính file đó; nếu thêm dấu `/` mà không thêm package thì lại yêu cầu resource **khác** ở root.

## 2. Thuật toán chuẩn hóa tên đường dẫn

Tài liệu `Class.getResource(String)` quy định cách chuyển tên resource sang tên tuyệt đối:

1. Nếu `name` bắt đầu bằng dấu `/`, bỏ dấu `/` đầu và dùng phần còn lại.
2. Nếu không, lấy package của class đang được tham chiếu, đổi dấu `.` thành `/`, ghép thêm `/` và chuỗi `name`.

Với `package com.acme.orders;`:

| Lời gọi | Tên tuyệt đối được tra cứu | Giải thích |
| --- | --- | --- |
| `getResource("config.properties")` | `com/acme/orders/config.properties` | Tương đối package |
| `getResource("/config.properties")` | `config.properties` | Root resource |
| `getResource("/com/acme/orders/config.properties")` | `com/acme/orders/config.properties` | Cùng tên như dòng 1 |
| `getResource("templates/mail.txt")` | `com/acme/orders/templates/mail.txt` | Thư mục con package |
| `getResource("/templates/mail.txt")` | `templates/mail.txt` | Thư mục con root |
| `getResource("com/acme/orders/config.properties")` | `com/acme/orders/com/acme/orders/config.properties` | Sai thường gặp: nhân đôi package |

Đây là **tên được yêu cầu tìm**, không đảm bảo tìm được. Muốn biết có tồn tại, phải xét classpath/JAR/module, class loader và quyền truy cập.

Hình dung classpath root là `out/classes`:

~~~text
out/classes/
├── root.txt
└── com/
    └── acme/
        └── orders/
            ├── OrderService.class
            ├── config.properties
            └── templates/
                └── mail.txt
~~~

Những lời gọi sau có kết quả dự kiến nếu chỉ có các resource nêu trong cây:

~~~java
OrderService.class.getResource("config.properties");  // FOUND
OrderService.class.getResource("/com/acme/orders/config.properties"); // FOUND
OrderService.class.getResource("/config.properties"); // NOT_FOUND
OrderService.class.getResource("/root.txt");           // FOUND
OrderService.class.getResource("root.txt");            // NOT_FOUND
OrderService.class.getResource("templates/mail.txt");  // FOUND
~~~

Đoạn này chỉ minh họa biểu thức, không phải chương trình biên dịch độc lập; hãy xét chúng như các lệnh nằm trong một phương thức hợp lệ. Trong ứng dụng nhiều JAR, tên bị đánh dấu NOT_FOUND vẫn có thể có bản ở entry khác, nên phải kiểm tra classpath runtime thực tế.

## 3. Phân biệt Class.getResource với ClassLoader.getResource

Cùng tên `getResource` nhưng **quy tắc đường dẫn không giống nhau**:

| API | Có tự thêm package? | Dấu `/` ở đầu |
| --- | --- | --- |
| `SomeClass.class.getResource(name)` | Có, khi tên không bắt đầu bằng `/` | Có ý nghĩa tuyệt đối; Java loại bỏ ký tự đó |
| `SomeClass.class.getResourceAsStream(name)` | Có, tương tự `Class.getResource` | Cùng quy tắc |
| `someLoader.getResource(name)` | Không | Truyền tên từ gốc, thông thường **không thêm** dấu `/` |
| `someLoader.getResourceAsStream(name)` | Không | Dùng tên từ gốc |
| `ClassLoader.getSystemResource(name)` | Không | Dùng tên từ gốc |

Ví dụ tên thật của resource là `com/acme/orders/config.properties`:

~~~java
URL a = OrderService.class.getResource("config.properties");
URL b = OrderService.class.getResource(
        "/com/acme/orders/config.properties");

ClassLoader loader = OrderService.class.getClassLoader();
URL c = loader.getResource("com/acme/orders/config.properties");
~~~

Cả ba biểu thức tìm cùng **tên logic** trong tình huống classpath/loader thông dụng, nhưng không phải lúc nào chúng cũng bắt buộc trả cùng resource trong môi trường named module hoặc custom loader. Đoạn trên giả định `OrderService` và `loader` tồn tại, và loader khác `null`.

**Lỗi phổ biến:** `loader.getResource("/com/acme/orders/config.properties")`. Không có bước “bỏ slash đầu” trong hợp đồng `ClassLoader.getResource`; loader tích hợp thường không tìm thấy và trả `null`. Cách dùng portable là bỏ dấu `/` trước tên truyền cho `ClassLoader`.

**Cảnh báo bootstrap:** `String.class.getClassLoader()` thường bằng `null`, nên gọi tiếp `.getResource(...)` sẽ gây `NullPointerException`. Với Class của kiểu bootstrap, dùng trực tiếp API `Class.getResource` hoặc API phù hợp với resource mong tìm; đừng giả định có một đối tượng loader Java để gọi.

## 4. Bài thực hành Java 17+: thư mục package và root

Tạo hai nhóm file: Java source và ba text resources.

~~~text
example/
├── src/
│   └── demo/
│       └── ResourceDemo.java
└── resources/
    ├── global.txt
    └── demo/
        ├── config.properties
        └── assets/
            └── help.txt
~~~

Nội dung `resources/demo/config.properties` là `ENV=training`, `resources/global.txt` là `ROOT`, và `resources/demo/assets/help.txt` là `HELP`. Mỗi file có một dòng text thông thường.

### File src/demo/ResourceDemo.java

~~~java
package demo;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ResourceDemo {
    private static void check(String name, URL url) {
        System.out.println(name + " -> "
                + (url != null ? "FOUND" : "NOT_FOUND"));
    }

    public static void main(String[] args) throws Exception {
        check("relative same-package",
                ResourceDemo.class.getResource("config.properties"));
        check("absolute same-package",
                ResourceDemo.class.getResource("/demo/config.properties"));
        check("relative root",
                ResourceDemo.class.getResource("global.txt"));
        check("absolute root",
                ResourceDemo.class.getResource("/global.txt"));
        check("wrong root",
                ResourceDemo.class.getResource("/config.properties"));
        check("relative subfolder",
                ResourceDemo.class.getResource("assets/help.txt"));

        check("loader root-path",
                ResourceDemo.class.getClassLoader()
                        .getResource("demo/config.properties"));
        check("loader leading-slash",
                ResourceDemo.class.getClassLoader()
                        .getResource("/demo/config.properties"));

        try (InputStream in = ResourceDemo.class
                .getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new IllegalStateException("config missing");
            }
            String content = new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            System.out.println("contents -> " + content);
        }
    }
}
~~~

`ResourceDemo` thuộc package `demo`, nên tên tương đối `config.properties` được đổi thành `demo/config.properties`. Trong khi đó, `/global.txt` chỉ định root resource. Ví dụ cũng cố ý dùng cách gọi ClassLoader với và không với dấu slash để thấy hai API khác nhau.

### Biên dịch và chạy trên Linux/macOS

Đứng trong thư mục `example`:

~~~bash
mkdir -p out/demo/assets
javac -d out src/demo/ResourceDemo.java
cp resources/demo/config.properties out/demo/config.properties
cp resources/demo/assets/help.txt out/demo/assets/help.txt
cp resources/global.txt out/global.txt
java -cp out demo.ResourceDemo
~~~

### Biên dịch và chạy trên Windows PowerShell

Đứng trong thư mục `example`:

~~~powershell
New-Item -ItemType Directory -Force out\demo\assets | Out-Null
javac -d out src\demo\ResourceDemo.java
Copy-Item resources\demo\config.properties out\demo\config.properties
Copy-Item resources\demo\assets\help.txt out\demo\assets\help.txt
Copy-Item resources\global.txt out\global.txt
java -cp out demo.ResourceDemo
~~~

Phải **copy cả resources**, vì `javac -d out ...` không tự copy tệp `.properties`/`.txt`.

Kết quả stdout khi chạy với các resource đã đặt đúng vị trí:

~~~text
relative same-package -> FOUND
absolute same-package -> FOUND
relative root -> NOT_FOUND
absolute root -> FOUND
wrong root -> NOT_FOUND
relative subfolder -> FOUND
loader root-path -> FOUND
loader leading-slash -> NOT_FOUND
contents -> ENV=training
~~~

Kết quả trên đã được kiểm chứng bằng `javac`/`java` trên JDK thực. Dòng `loader leading-slash -> NOT_FOUND` phản ánh application/system loader chuẩn được dùng trong thí nghiệm; đừng coi việc một custom loader nhận dấu slash đầu là hợp đồng được đảm bảo.

### Đóng gói resource trong JAR

Khi thư mục `out` chứa `demo/ResourceDemo.class` và các resource ở đúng đường dẫn, chạy:

~~~bash
jar --create --file resources-demo.jar -C out .
java -cp resources-demo.jar demo.ResourceDemo
~~~

Các lệnh này cũng áp dụng trong Windows PowerShell với JDK 17+. **Đầu ra y hệt** phiên bản chạy từ folder, và đã được kiểm chứng bằng một JAR chứa cả class lẫn resource. Đây là lý do nên đọc resource bằng Java resource API thay vì hardcode đường dẫn filesystem.

## 5. Đường dẫn tương đối gắn với Class nào?

Giả sử có hai lớp ở hai package:

~~~java
package alpha;
public class A {}
~~~

~~~java
package beta;
public class B {}
~~~

Hai đoạn trên là **hai file Java riêng**. Lời gọi `A.class.getResource("data.txt")` tìm `alpha/data.txt`, còn `B.class.getResource("data.txt")` tìm `beta/data.txt`.

Ngay cả khi lời gọi `A.class.getResource("data.txt")` được viết và thực thi bởi code trong `beta.B`, base của resource vẫn là package `alpha`. Điều cần nhìn là **đối tượng Class** phía trước `.getResource`, không phải package của caller hoặc tên thư mục nơi process đang chạy.

Với nested class, package base cũng là package của kiểu đó, không tự thêm tên nested class. Với lớp thuộc default package, tên tương đối không có package prefix. Trong code ứng dụng, nên dùng package rõ ràng để tránh ambiguity khi đóng gói.

Một chiến lược ổn định cho thư viện là dùng `MyLibraryClass.class.getResource("library-config.json")` khi resource nằm cạnh class của thư viện, hoặc dùng đường dẫn root `"/com/vendor/library-config.json"` nếu cần chỉ định tên rõ ràng, tránh vô tình đọc resource khác cùng tên.

## 6. Đọc dữ liệu: URL, InputStream, encoding và null

`Class.getResource` trả `URL`, thích hợp khi API nhận URL hoặc khi muốn mở bằng `url.openStream()`. `Class.getResourceAsStream` trả stream trực tiếp. Cả hai có thể trả `null`; không nên gọi `url.openStream()` khi chưa kiểm tra.

Một mẫu đọc bytes an toàn:

~~~java
try (InputStream in = MyClass.class
        .getResourceAsStream("/config/app.json")) {
    if (in == null) {
        throw new IllegalStateException(
                "Resource not found: /config/app.json");
    }
    String json = new String(in.readAllBytes(),
            StandardCharsets.UTF_8);
    // Xử lý json bằng parser JSON phù hợp.
}
~~~

Đây là **đoạn mã minh họa bên trong phương thức**, giả định đã import `InputStream` và `StandardCharsets`, đồng thời file tồn tại. `readAllBytes` phù hợp với resource nhỏ; với dữ liệu lớn nên đọc theo từng chunk. *Try-with-resources* đóng stream cả khi xử lý có exception.

Đừng mặc định API `Properties.load(InputStream)` diễn giải text UTF-8 như `new String(..., UTF_8)`: nó có quy tắc encoding truyền thống riêng. Nếu resource là properties UTF-8, hãy xem xét `Properties.load(Reader)` với reader được cấu hình encoding phù hợp.

**Không convert URL resource thành File một cách mù quáng:**

~~~java
URL url = MyClass.class.getResource("/config/app.json");
File file = new File(url.getFile()); // không đáng tin khi chạy từ JAR
~~~

Đây là **phản ví dụ**, không phải khuyến nghị. Bên trong JAR, URL có thể là `jar:file:...!/config/app.json`; entry không phải một file độc lập trên ổ đĩa. Dùng stream hoặc API xử lý archive/URL thích hợp, và chỉ trích xuất ra file tạm khi API khác thật sự bắt buộc cần filesystem path.

## 7. Vị trí runtime không phải vị trí thư mục source

Với Maven/Gradle Java layout phổ biến:

~~~text
src/
└── main/
    ├── java/com/acme/app/Main.java
    └── resources/
        ├── global.properties
        └── com/acme/app/banner.txt
~~~

Thông thường build tool copy nội dung dưới `src/main/resources` vào **root output resource** rồi đóng gói cùng class. Khi đó `Main.class.getResource("banner.txt")` tìm `com/acme/app/banner.txt`; `Main.class.getResource("/global.properties")` tìm resource gốc. Không viết tên `"src/main/resources/global.properties"`, vì prefix nguồn đó **không còn là một phần tên resource runtime**.

Nếu chạy trực tiếp bằng `javac` và `java`, chỉ build source sẽ không tự bảo đảm resource được copy. Nếu chạy từ IDE mà tìm được còn JAR thì không, kiểm tra quy trình đóng gói. Có thể dùng `jar tf app.jar` để xem entry thực tế (tên entry được phân cách bằng dấu `/`).

**Đặc tính cần nhớ:** `getResource("config.txt")` không dựa trên current working directory. Đổi `user.dir` không làm package base thay đổi; nhưng nếu bạn chạy với classpath **tương đối** rồi đổi working directory trước khi khởi chạy JVM, classpath entry có thể chỉ tới nơi khác.

## 8. JAR, named module và custom loader: trường hợp đặc biệt

### JAR

Resource được đặt trong JAR theo tên entry như `demo/config.properties`. Quy tắc dấu `/` đầu của `Class.getResource` vẫn dựa trên **tên logic**; chỉ cơ chế mở dữ liệu vật lý đổi từ file sang JAR. Nếu resource chỉ nằm trong source tree mà không được đóng gói vào JAR, API không tự tìm lại source tree.

### Named module (Java 9+)

Với lớp trong **named module**, `Class.getResource`/`getResourceAsStream` tìm trong module của lớp, và truy cập resource còn chịu **encapsulation** của module. Đặc biệt, resource không phải `.class` nằm trong package không được mở tới module đang gọi có thể không truy cập được dù tên và file đều đúng.

Dấu `/` đầu **không vượt qua quyền mở package**. Nếu đường dẫn sau chuẩn hóa đã đúng mà kết quả `null`, kiểm tra `module-info.java` và quy tắc `opens`, không chỉ thử thêm/bỏ slash.

`ClassLoader.getResource` với named module cũng có quy tắc riêng, trong đó tài nguyên không phải `.class` trong package named module thường phải nằm ở package **opened unconditionally** để API `ClassLoader` công khai có thể tìm. Do đó không khẳng định hai API `Class.getResource` và `ClassLoader.getResource` luôn hoàn toàn tương đương khi làm việc với named modules.

### Custom class loader

Với lớp ở unnamed module, `Class.getResource` sử dụng defining loader của đối tượng class được chọn để tìm tài nguyên sau bước chuẩn hóa tên. Một plugin được nạp từ custom child loader có thể nhìn thấy resource riêng của plugin, còn một class của parent có thể không thấy file đó.

Mặc định `ClassLoader.getResource` thường thử parent trước rồi mới `findResource` trong loader hiện tại, nhưng loader tùy biến có thể có chính sách khác. Nếu nhiều JAR có resource cùng tên, kết quả còn phụ thuộc loader và thứ tự tìm. Đây là lý do nên chọn đối tượng `Class` đại diện đúng thành phần sở hữu resource.

Thread context class loader chỉ liên quan nếu ứng dụng/framework **chủ động** dùng nó; nó không tự trở thành base cho mọi lời gọi `SomeClass.class.getResource`.

## 9. Chẩn đoán trường hợp getResource trả null

| Triệu chứng | Nguyên nhân phổ biến | Cách xử lý |
| --- | --- | --- |
| `MyClass.class.getResource("config.yml") == null` | File ở root, nhưng tên tương đối đang tìm trong package. | Dùng `"/config.yml"` hoặc đặt file dưới package. |
| `MyClass.class.getResource("/pkg/config.yml") == null` | Resource chưa copy vào output/JAR, sai tên, sai chữ hoa/thường. | Xem cây output, dùng `jar tf` kiểm tra entry. |
| Gọi `getResource("pkg/config.yml")` trong class package `pkg` | Bị nhân đôi prefix package: `pkg/pkg/config.yml`. | Dùng `"config.yml"` hoặc `"/pkg/config.yml"`. |
| `loader.getResource("/pkg/config.yml") == null` | Dùng sai quy tắc dấu `/` đầu của `ClassLoader`. | Dùng `loader.getResource("pkg/config.yml")`. |
| Chạy folder được, JAR lỗi | Resource không được đóng gói hoặc code ép URL thành File. | Đóng gói resource, đọc qua InputStream. |
| Trong named module có file mà vẫn null | Package resource bị encapsulated. | Kiểm tra `opens` và caller module. |
| Chỉ fail khi chuyển Windows sang Linux | Sai chữ hoa/thường tên entry, dùng `\` cho resource. | Dùng tên chính xác và dấu `/`. |
| Có nhiều class loader | Lớp được chọn cho lookup do loader không thấy resource định nghĩa. | Kiểm tra `clazz.getClassLoader()` và nguồn resource thực. |

Không dùng mỗi `System.out.println(getResource(...))` rồi hardcode chuỗi URL tuyệt đối vào test. URL có thể chứa thư mục tạm, prefix `file:`, `jar:` hoặc encoding khác nhau theo môi trường. Test nên kiểm tra **tồn tại** và **nội dung** dữ liệu.

## 10. Checklist và câu hỏi ôn tập

Trước khi gọi API, xác định: **(1)** package của class được dùng; **(2)** tên entry resource tại runtime; **(3)** muốn tìm tương đối hay từ root; **(4)** dùng `Class` hay `ClassLoader`; **(5)** class loader/module nào cung cấp resource; **(6)** tài nguyên có nằm trong JAR không; **(7)** đã kiểm tra null, đóng stream và chỉ định encoding chưa.

**Câu 1.** Class `org.demo.Widget` cần đọc `org/demo/config.xml`. Hai tên resource nào đưa vào `Widget.class.getResource` là tương đương?

**Đáp:** `"config.xml"` và `"/org/demo/config.xml"`: đều chuẩn hóa thành `org/demo/config.xml`.

**Câu 2.** File `global.txt` nằm tại root classpath. Vì sao `Widget.class.getResource("global.txt")` thường không tìm thấy?

**Đáp:** Nó tìm `org/demo/global.txt`. Dùng `Widget.class.getResource("/global.txt")` để yêu cầu tên root.

**Câu 3.** Có nên thêm dấu slash đầu khi đổi từ `Widget.class.getResource("/org/demo/config.xml")` sang `Widget.class.getClassLoader().getResource(...)` không?

**Đáp:** Không. ClassLoader nhận `"org/demo/config.xml"`, không tự bỏ slash đầu; còn phải xử lý nếu class loader bằng `null`.

**Câu 4.** Đổi folder sang JAR có làm quy tắc dấu slash đầu của `Class.getResource` thay đổi?

**Đáp:** Không. Tên logic vẫn vậy; chỉ cách lưu trữ/mở resource đổi, nên thường đọc bằng stream thay vì ép URL thành file vật lý.

## Tài liệu chuẩn

- [Class.getResource(String), Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getResource(java.lang.String)): tên tương đối và chuẩn hóa slash đầu.
- [Class.getResourceAsStream(String), Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getResourceAsStream(java.lang.String)): quy tắc tra cứu và trả InputStream.
- [ClassLoader.getResource(String), Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#getResource(java.lang.String)): tên slash-separated từ gốc và parent delegation.
- [Module.getResourceAsStream(String), Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String)): tài nguyên thuộc named modules và quyền truy cập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-003">ClassLoader.getResource: đường dẫn tính từ gốc tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# ClassLoader.getResource: đường dẫn tính từ gốc tài nguyên

## 1. Vấn đề: “gốc tài nguyên” là gốc nào?

Trong Java, **ClassLoader.getResource(String name)** giúp tìm dữ liệu đóng gói cùng chương trình như file cấu hình, template, ảnh, chứng chỉ công khai hoặc file văn bản. Tài nguyên được nhận diện bởi **tên đường dẫn phân tách bằng dấu /**, chẳng hạn:

~~~text
config/app.properties
templates/email.html
images/logo.png
com/example/messages.txt
~~~

Điểm quan trọng: **ClassLoader.getResource tìm tên tài nguyên tương đối với các gốc tìm kiếm của class loader**, không tự thêm tên package của class đang gọi. Vì vậy:

~~~java
ClassLoader loader = Example.class.getClassLoader();
URL url = loader.getResource("config/app.properties");
~~~

Tên **config/app.properties** được tìm tại **gốc của từng classpath entry / nguồn tài nguyên mà loader quản lý**. Nó không mặc định trở thành **com/example/config/app.properties** chỉ vì Example ở package com.example. Đây cũng không phải đường dẫn tuyệt đối của hệ điều hành, cũng không tự suy ra từ current working directory.

**Mục tiêu bài học:** chọn đúng resource name, phân biệt API của Class và ClassLoader, đọc stream an toàn, hiểu parent delegation và resource trong JAR/module, xử lý nhiều resource trùng tên, và chẩn đoán tại sao tài nguyên có trong project mà runtime vẫn trả về null.

## 2. Tên tài nguyên tương ứng với vị trí nào?

Giả sử chương trình sau khi build có cấu trúc:

~~~text
out/
├── demo/
│   ├── ResourceRootDemo.class
│   └── local.txt
└── config/
    └── app.properties
~~~

Trong trường hợp thư mục **out** nằm trên application classpath, ta có:

| Resource name truyền vào ClassLoader | Nơi tìm trong entry out | Kỳ vọng |
| --- | --- | --- |
| **config/app.properties** | out/config/app.properties | Có |
| **demo/local.txt** | out/demo/local.txt | Có |
| **local.txt** | out/local.txt | Không, nếu chỉ có file ở demo/ |
| **demo/config/app.properties** | out/demo/config/app.properties | Không |
| **/config/app.properties** | Không phải tên tương đối đúng trong ví dụ thông thường | Thường không tìm thấy |
| **config\\app.properties** | Không dùng dấu backslash làm dấu phân cách resource | Không đúng cách ghi thông thường |

Gốc ở đây là **out** vì đó là classpath entry. Nếu classpath thay bằng một JAR, gốc tương ứng là **root của các entry trong JAR**. Nếu loader tùy biến lấy resources từ nguồn khác, “gốc” do loader đó quy định.

**Không cần và không nên thêm dấu /** ở đầu đối số của ClassLoader.getResource. Dấu slash đầu chỉ có ý nghĩa đặc biệt với **Class.getResource**, không phải quy tắc mà ClassLoader.getResource mặc định loại bỏ.

### 2.1 Dấu / là dấu phân tách chuẩn của resource name

Dù ứng dụng chạy trên Windows, resource name vẫn viết:

~~~java
loader.getResource("config/app.properties");   // Đúng
loader.getResource("demo/local.txt");          // Đúng
~~~

Không viết theo dạng đường dẫn máy Windows như:

~~~java
loader.getResource("C:\\project\\config\\app.properties"); // Không phải resource name phù hợp
loader.getResource("config\\app.properties");                  // Sai quy ước slash
~~~

Đối số **name** là tên logic của tài nguyên trong không gian tra cứu của loader. Nó không phải đường dẫn filesystem và không nhất thiết có thể chuyển thành **java.nio.file.Path**.

### 2.2 Classpath có thể có nhiều gốc

Ví dụ lệnh:

~~~bash
java -cp out:assets:lib/util.jar demo.Main
~~~

trên Linux/macOS; trên Windows, dấu phân cách classpath là **;**, không phải **:**:

~~~powershell
java -cp "out;assets;lib/util.jar" demo.Main
~~~

Loader có thể tìm một tên tài nguyên trong các nguồn thích hợp **out**, **assets**, **util.jar** và những nguồn do parent cung cấp. Không gắn resource name với một thư mục filesystem duy nhất.

**Lưu ý:** có nhiều kiểu class loader, module path và môi trường tùy biến; ví dụ classpath trên mô tả hành vi thông thường của application loader với unnamed module, không phải cam kết rằng mọi loader đều đọc cùng một danh sách thư mục.

## 3. API ClassLoader.getResource trả gì?

Chữ ký:

~~~java
public URL getResource(String name);
~~~

Khi thành công, API trả về **java.net.URL** trỏ đến vị trí có thể dùng để đọc resource. Khi không tìm được hoặc không được phép truy cập (theo các quy tắc thích hợp), phương thức trả **null**. Truyền **null** làm name có thể ném **NullPointerException** theo API.

Sử dụng an toàn:

~~~java
ClassLoader loader = MyClass.class.getClassLoader();
URL resource = loader.getResource("config/app.properties");

if (resource == null) {
    throw new IllegalStateException("Missing config/app.properties");
}

try (InputStream in = resource.openStream()) {
    // Đọc dữ liệu...
}
~~~

Đây là snippet để đặt trong ứng dụng có MyClass, imports và phần đọc dữ liệu; chưa là một chương trình độc lập. Phần 6 có ví dụ đủ file, lệnh chạy và stdout.

**URL không phải Path:** khi chạy từ thư mục, URL có thể bắt đầu bằng **file:**; khi đóng gói vào JAR, URL thường có dạng **jar:file:...!/config/app.properties**; tài nguyên hệ JDK có thể có dạng **jrt:**. Vì thế không nên lấy **url.getFile()** rồi ép thành **File** để đọc trong mọi môi trường.

## 4. So sánh ClassLoader.getResource và Class.getResource

Đây là nguồn gây nhầm lẫn lớn nhất.

### 4.1 Quy tắc của ClassLoader

Với ClassLoader:

~~~java
ClassLoader loader = Demo.class.getClassLoader();

URL a = loader.getResource("config/app.properties");
URL b = loader.getResource("demo/local.txt");
~~~

Tên truyền vào được hiểu là **tên đường dẫn từ gốc resource search**. Không có quy tắc tự ghép tên package **demo** vào trước resource name. Đừng đặt slash đầu chuỗi.

### 4.2 Quy tắc của Class

Với Class:

~~~java
URL a = Demo.class.getResource("local.txt");
URL b = Demo.class.getResource("/config/app.properties");
URL c = Demo.class.getResource("/demo/local.txt");
~~~

Nếu **Demo** thuộc package **demo**:

- **getResource("local.txt")**: tên không bắt đầu bằng slash, **Class** tạo tên tuyệt đối **demo/local.txt**.
- **getResource("/config/app.properties")**: tên bắt đầu bằng slash, **Class** bỏ slash đó rồi tra **config/app.properties** từ gốc.
- **getResource("/demo/local.txt")**: bỏ slash đầu, tra **demo/local.txt** từ gốc.

Như vậy, **Class.getResource("/demo/local.txt")** và **ClassLoader.getResource("demo/local.txt")** có thể cùng tìm một resource trong chương trình classpath đơn giản. Nhưng Class và ClassLoader không phải các API hoàn toàn đồng nhất: trong **named module**, Class.getResource gắn với module của Class và kiểm tra encapsulation theo quy tắc dành cho caller.

### 4.3 Bảng đối chiếu

Giả sử **demo/LocalDemo.class**, **demo/local.txt**, **config/app.properties** cùng ở một classpath root:

| Biểu thức | Tên được tìm trong ví dụ | Kết quả |
| --- | --- | --- |
| **loader.getResource("demo/local.txt")** | demo/local.txt | Tìm thấy |
| **loader.getResource("local.txt")** | local.txt | Không thấy |
| **loader.getResource("/demo/local.txt")** | Tên có slash đầu không được ClassLoader tự bỏ | Không thấy trong loader classpath thông thường |
| **LocalDemo.class.getResource("local.txt")** | demo/local.txt | Tìm thấy |
| **LocalDemo.class.getResource("/demo/local.txt")** | demo/local.txt | Tìm thấy |
| **LocalDemo.class.getResource("config/app.properties")** | demo/config/app.properties | Không thấy |
| **LocalDemo.class.getResource("/config/app.properties")** | config/app.properties | Tìm thấy |

**Quy tắc dễ nhớ:**

~~~text
ClassLoader.getResource("x/y.txt")
            |
            └─ x/y.txt tính từ gốc loader

Class.getResource("y.txt")
            |
            └─ package/của/Class/y.txt

Class.getResource("/x/y.txt")
            |
            └─ x/y.txt tính từ gốc (Class bỏ dấu / đầu)
~~~

Không dùng một biến API khác để “sửa” đường dẫn không đúng; hãy xác định xem tài nguyên được đóng gói ở root entry nào và API nào đáp ứng ý định.

## 5. Đường dẫn từ gốc không phải thư mục project đang mở

Câu hỏi thường gặp: “Tôi có file trong thư mục **src/main/resources**, tại sao getResource không nhận chuỗi **src/main/resources/config/app.properties**?”

Trong ứng dụng Maven/Gradle thông thường, file dưới **src/main/resources** được **copy thành resource output**, rồi thư mục output được đưa lên runtime classpath hoặc được đóng gói vào JAR:

~~~text
project/
├── src/main/java/demo/App.java
├── src/main/resources/
│   └── config/app.properties
└── target/classes/                 (ví dụ Maven)
    ├── demo/App.class
    └── config/app.properties
~~~

Resource name khi chạy thông thường là:

~~~java
loader.getResource("config/app.properties");
~~~

**Không phải:**

~~~java
loader.getResource("src/main/resources/config/app.properties");
~~~

vì tên nguồn **src/main/resources** không nằm trong cấu trúc resource runtime của artifact được build theo quy ước này.

Tương tự với Gradle, các task xử lý resources thường tạo output resources và đóng gói vào classpath/JAR. Cần kiểm tra artifact **thực tế** nếu build đã được cấu hình khác mặc định.

### 5.1 CWD chỉ có thể ảnh hưởng gián tiếp

Nếu bạn chạy:

~~~bash
java -cp out:assets demo.ResourceRootDemo
~~~

thì phần **out** và **assets** là các classpath entries dạng tương đối. Chúng được diễn giải tương đối với nơi chạy lệnh lúc khởi động JVM. **Nhưng resource name** vẫn là **config/app.properties**, không phải đường dẫn tương đối tùy ý tới thư mục làm việc.

Nếu đổi working directory khiến các path trong **-cp** không còn đúng, resource có thể mất. Nguyên nhân khi ấy nằm ở **classpath root được cấu hình**, không phải ClassLoader.getResource tự tìm bằng CWD.

## 6. Ví dụ chạy được số 1: kiểm tra root, package và dấu slash

Tạo cấu trúc thư mục:

~~~text
project/
├── src/
│   └── demo/
│       └── ResourceRootDemo.java
└── assets/
    ├── config/
    │   └── app.properties
    └── demo/
        └── local.txt
~~~

**assets/config/app.properties**:

~~~properties
mode=demo
~~~

**assets/demo/local.txt**:

~~~text
welcome-from-package
~~~

**src/demo/ResourceRootDemo.java**:

~~~java
package demo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ResourceRootDemo {
    private static String readText(InputStream source) throws Exception {
        if (source == null) {
            return "<missing>";
        }

        try (InputStream in = source) {
            return new String(
                    in.readAllBytes(),
                    StandardCharsets.UTF_8
            ).strip();
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ResourceRootDemo.class.getClassLoader();

        System.out.println("loader root = "
                + (loader.getResource("config/app.properties") != null));

        System.out.println("loader leading slash = "
                + (loader.getResource("/config/app.properties") != null));

        System.out.println("class relative = "
                + (ResourceRootDemo.class.getResource("local.txt") != null));

        System.out.println("class absolute = "
                + (ResourceRootDemo.class.getResource("/config/app.properties")
                        != null));

        System.out.println("class mistaken relative = "
                + (ResourceRootDemo.class.getResource("config/app.properties")
                        != null));

        Properties properties = new Properties();
        try (InputStream in = loader.getResourceAsStream(
                "config/app.properties")) {
            if (in == null) {
                throw new IllegalStateException("Missing properties");
            }
            properties.load(in);
        }

        System.out.println("mode = " + properties.getProperty("mode"));
        System.out.println("local = "
                + readText(loader.getResourceAsStream("demo/local.txt")));
    }
}
~~~

Đứng tại thư mục **project**, biên dịch:

~~~bash
javac -d out src/demo/ResourceRootDemo.java
~~~

Chạy trên Linux/macOS:

~~~bash
java -cp out:assets demo.ResourceRootDemo
~~~

Hoặc trên Windows PowerShell:

~~~powershell
java -cp "out;assets" demo.ResourceRootDemo
~~~

**Stdout kỳ vọng** với application classpath đúng như trên, resource files chính xác như ví dụ và không có resource cùng tên ở parent loader:

~~~text
loader root = true
loader leading slash = false
class relative = true
class absolute = true
class mistaken relative = false
mode = demo
local = welcome-from-package
~~~

### 6.1 Vì sao ra kết quả như vậy?

- **loader root = true**: file **assets/config/app.properties** nằm dưới root **assets**, và application loader nhận tên **config/app.properties**.
- **loader leading slash = false**: **ClassLoader.getResource** không chuyển chuỗi **/config/app.properties** thành root-relative như Class.getResource; loader classpath thông thường không tìm tên có slash đầu này.
- **class relative = true**: ResourceRootDemo thuộc package **demo**, nên **Class.getResource("local.txt")** tra **demo/local.txt**.
- **class absolute = true**: Class.getResource loại bỏ slash đầu và tra **config/app.properties**.
- **class mistaken relative = false**: Class.getResource không bỏ qua package khi tên không có slash; nó tra **demo/config/app.properties**, đường dẫn này không có.
- **mode** và **local** chứng minh resources được **đọc qua InputStream**, không cần biết chúng là file hệ điều hành hay entry trong archive.

### 6.2 Cách kiểm tra nếu stdout khác

Nếu mọi giá trị đều false, kiểm tra đúng working directory, classpath separator của OS, hai file tài nguyên có được tạo đúng không và classpath thực tế có mục **assets** không. Nếu chỉ một giá trị thay đổi bất ngờ, kiểm tra liệu có **resource trùng tên** trong parent loader/JAR khác hay custom loader làm khác quy tắc mặc định không.

**Quan trọng:** ví dụ dùng API của unnamed module trên classpath tiêu chuẩn. Trong named modules, cần xét thêm **module encapsulation** như phần 10.

## 7. Dùng getResourceAsStream: đọc dữ liệu, không cần biết URL

Trong nhiều trường hợp cần **nội dung** chứ không cần URL. ClassLoader có:

~~~java
public InputStream getResourceAsStream(String name);
~~~

Ví dụ ngắn trong một method có **loader** và exception handling:

~~~java
try (InputStream in = loader.getResourceAsStream(
        "config/app.properties")) {
    if (in == null) {
        throw new IllegalStateException(
                "Resource missing: config/app.properties"
        );
    }

    Properties props = new Properties();
    props.load(in);
}
~~~

Quy tắc:

- **Null** có nghĩa resource không tìm được hoặc không được cung cấp theo policy truy cập thích hợp.
- **InputStream phải đóng** sau khi đọc; dùng try-with-resources.
- **Properties.load(InputStream)** theo chuẩn dùng quy tắc mã hóa ISO-8859-1 và escaping của properties format. Nếu file cần UTF-8 không phải ASCII, cân nhắc **Properties.load(Reader)** với **InputStreamReader(..., UTF_8)** hoặc API parser phù hợp.
- Với file văn bản UTF-8 thông thường, dùng InputStreamReader / new String(bytes, UTF_8), không dựa vào default charset ngầm.
- Không gọi **available()** để suy ra tổng chiều dài tài nguyên; available chỉ thể hiện lượng byte có thể đọc ngay mà không block.
- Không mở lại stream nhiều lần nếu bạn có thể đọc một lượt và cache nội dung phù hợp.

### 7.1 Không chuyển URL thành File bằng mọi giá

Đoạn sau có thể chỉ hoạt động khi resource thật sự nằm trên filesystem:

~~~java
// KHÔNG dùng chung cho file/JAR/module:
File file = new File(url.getFile());
~~~

Khi đóng gói thành JAR, URL có thể có dạng:

~~~text
jar:file:/path/to/app.jar!/config/app.properties
~~~

Không có file hệ điều hành riêng tại **/path/to/app.jar!/config/app.properties**. Đây là entry bên trong archive. Tốt nhất đọc bằng **URL.openStream()** hoặc **ClassLoader.getResourceAsStream()**; chỉ chuyển thành **Path** nếu protocol và filesystem provider thực sự hỗ trợ, và bạn quản lý vòng đời filesystem tương ứng.

## 8. Đóng gói JAR không làm đổi resource name

Dùng cùng ví dụ phần 6, đứng tại **project** sau khi đã compile:

~~~bash
jar --create --file demo-resources.jar -C out . -C assets .
jar --list --file demo-resources.jar
~~~

Trong archive sẽ có những entry như:

~~~text
demo/ResourceRootDemo.class
demo/local.txt
config/app.properties
~~~

Các metadata entry như **META-INF/MANIFEST.MF** cũng có thể xuất hiện, nên danh sách trên chỉ liệt kê entry đang quan tâm.

Chạy JAR bằng classpath trên Linux/macOS hoặc Windows đều dùng một entry duy nhất:

~~~bash
java -cp demo-resources.jar demo.ResourceRootDemo
~~~

Stdout kỳ vọng **giống phần 6** vì resource name vẫn là **config/app.properties** và **demo/local.txt**. Loader chỉ thay nguồn lấy bytes: từ thư mục assets sang các JAR entries.

**Bài học:** không ghép tên file JAR vào đối số **getResource**:

~~~java
// Sai ý nghĩa khi muốn đọc entry:
loader.getResource("demo-resources.jar/config/app.properties");

// Đúng:
loader.getResource("config/app.properties");
~~~

Thay đổi cách đóng gói không bắt bạn thay resource name nếu **đường dẫn của resource bên trong root** giữ nguyên.

### 8.1 Giới hạn của ví dụ đóng gói

Nếu JAR được module hóa và đặt trên module path, quy tắc tìm non-class resources còn phụ thuộc named module và mức mở package. Ví dụ trên dùng plain classpath, không có **module-info.java**.

## 9. Parent delegation: ClassLoader.getResource tìm ở đâu trước?

Theo implementation mặc định trong ClassLoader API:

1. Tìm tài nguyên thông qua **parent loader**; nếu không có parent, tìm trên đường tra resource của bootstrap loader phù hợp.
2. Nếu parent không tìm thấy, gọi **findResource(name)** của loader hiện tại.
3. Trả URL đầu tiên tìm được, hoặc null nếu không có resource hợp lệ.

~~~text
child.getResource("config/app.properties")
             │
             ▼
   parent.getResource(name)
             │
       ┌─────┴──────┐
       │ found URL? │
       └─────┬──────┘
       true  │ false
         │   ▼
         │ child.findResource(name)
         │   │
         └───┤
             ▼
        return URL / null
~~~

**Quan trọng:** Một file nằm trong JAR của child loader chưa chắc được lấy nếu parent cũng có resource **cùng tên**; parent có thể thắng theo delegation mặc định. Đừng nghĩ tất cả resource trùng tên luôn được chọn từ entry gần nhất với class đang gọi.

### 9.1 findResource và getResource khác nhau

- **getResource(name)** là entry point public của loader, xử lý delegation theo hợp đồng mặc định.
- **findResource(name)** là hook protected được subclass override để tự tìm resource trong nguồn mà nó quản lý.
- **findResources(name)** là hook protected để tìm nhiều resource cùng tên trong nguồn của loader.
- Cài đặt custom loader có thể override getResource và chọn policy khác; khi đó cần kiểm tra implementation thực tế.

Một custom loader chỉ muốn thêm nguồn resource thông thường nên override **findResource** (và **findResources** nếu hỗ trợ multiple resources), đồng thời giữ delegation mặc định khi không có lý do rõ ràng để thay.

### 9.2 Resource lookup không đồng nghĩa class loading

**loader.getResource("demo/Widget.class")** có thể tìm được class-file bytes, nhưng việc tìm resource **không tự tạo một java.lang.Class** cho Widget. **ClassLoader.loadClass("demo.Widget")** là quá trình tìm/định nghĩa runtime type khác về ngữ nghĩa.

Ngược lại, một custom loader có thể cung cấp Class qua bytecode sinh trong bộ nhớ nhưng không cho **getResource("demo/Widget.class")** tìm được stream tương ứng. Không dùng getResource làm phép thử duy nhất để khẳng định một class sẽ load được.

## 10. Named modules: resource có tồn tại nhưng vẫn không được tìm thấy

Từ Java 9, named modules có cơ chế **encapsulation** đối với nhiều non-class resources. Với **ClassLoader.getResource(name)** và **ClassLoader.getResources(name)**, Javadoc quy định:

- Resources trong named modules chịu quy tắc encapsulation theo **Module.getResourceAsStream**.
- Ngoại trừ trường hợp tên kết thúc bằng **.class**, resource thuộc package của named module thường chỉ được API **ClassLoader.getResource** tìm thấy khi package đó được **opened unconditionally** (mở không điều kiện), ngay cả khi caller ở trong cùng module.
- Nếu không thỏa, **getResource** có thể trả null dù resource thực sự được đóng gói trong module.
- Nếu nhiều module do cùng loader định nghĩa có resource trùng tên, thứ tự tìm qua modules có thể không xác định.

Đây là khác biệt với ví dụ classpath đơn giản.

### 10.1 Ví dụ module-info.java minh họa

Giả sử named module **sample.resources** chứa tài nguyên tại package **sample.internal**, và bạn muốn **ClassLoader.getResource("sample/internal/settings.json")** có thể tìm được qua loader từ bên ngoài. Module declaration có thể viết:

~~~java
module sample.resources {
    opens sample.internal;
}
~~~

**opens sample.internal;** là mở package không điều kiện. Trong khi đó **opens sample.internal to another.module;** chỉ mở có điều kiện cho module được nêu, không đáp ứng quy tắc mở **unconditionally** của ClassLoader.getResource cho non-class resource.

Không nên mở mọi package chỉ để một đoạn code tùy ý đọc tài nguyên; module encapsulation có lý do về thiết kế API và giới hạn truy cập. Khi tài nguyên gắn với một module cụ thể và caller có quyền truy cập thích hợp, có thể cân nhắc **Class.getResourceAsStream** hoặc **Module.getResourceAsStream**, vốn có ngữ nghĩa module-aware và quy tắc caller tương ứng.

### 10.2 Class.getResource trong named module cũng có quy tắc riêng

Với một Class thuộc **named module**, **Class.getResource** tìm tài nguyên trong module của Class, chứ không chỉ tự thêm package rồi dựa trên classpath search chung. Nó còn áp dụng encapsulation theo quyền của caller. Vì vậy:

~~~java
SomeClass.class.getResource("/sample/internal/settings.json");
~~~

không phải phép biến đổi chắc chắn tương đương với:

~~~java
SomeClass.class.getClassLoader()
        .getResource("sample/internal/settings.json");
~~~

trong mọi named-module application. Tránh diễn giải máy móc quy tắc hai dấu slash khi đã có module boundaries.

## 11. getResources: nếu cùng tên có nhiều bản thì sao?

Một loader còn có API:

~~~java
public Enumeration<URL> getResources(String name) throws IOException;
~~~

Nó tìm **nhiều URL** cùng tên. Implementation mặc định tìm parent trước rồi bổ sung tài nguyên từ **findResources(name)** của loader hiện tại. Đây là công cụ hữu ích khi phát hiện resource bị JAR khác che khuất.

Java 9+ còn có:

~~~java
public Stream<URL> resources(String name);
~~~

Hàm stream lazy và có thể bọc lỗi I/O thành **UncheckedIOException** khi duyệt; nếu muốn code dễ kiểm soát checked exception, **getResources** vẫn là lựa chọn rõ ràng.

### 11.1 Ví dụ chạy được số 2: hai classpath roots có cùng tên

Tạo:

~~~text
project/
├── src/demo/ResourceOrderDemo.java
├── assets-a/demo/banner.txt
└── assets-b/demo/banner.txt
~~~

**assets-a/demo/banner.txt**:

~~~text
blue
~~~

**assets-b/demo/banner.txt**:

~~~text
green
~~~

**src/demo/ResourceOrderDemo.java**:

~~~java
package demo;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class ResourceOrderDemo {
    static String read(URL url) throws Exception {
        if (url == null) {
            return "<missing>";
        }

        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader =
                ResourceOrderDemo.class.getClassLoader();
        String name = "demo/banner.txt";

        System.out.println("first = " + read(loader.getResource(name)));

        Enumeration<URL> matches = loader.getResources(name);
        int count = 0;

        while (matches.hasMoreElements()) {
            count++;
            System.out.println("resource " + count + " = "
                    + read(matches.nextElement()));
        }

        System.out.println("count = " + count);
    }
}
~~~

Biên dịch:

~~~bash
javac -d out src/demo/ResourceOrderDemo.java
~~~

Linux/macOS:

~~~bash
java -cp out:assets-a:assets-b demo.ResourceOrderDemo
~~~

Windows PowerShell:

~~~powershell
java -cp "out;assets-a;assets-b" demo.ResourceOrderDemo
~~~

Với application classpath thông thường trong lệnh này, không có bản trùng do parent cung cấp, stdout kỳ vọng:

~~~text
first = blue
resource 1 = blue
resource 2 = green
count = 2
~~~

Nếu đảo thứ tự classpath roots:

~~~bash
java -cp out:assets-b:assets-a demo.ResourceOrderDemo
~~~

trên Linux/macOS, hoặc Windows:

~~~powershell
java -cp "out;assets-b;assets-a" demo.ResourceOrderDemo
~~~

thì bản được chọn đầu thường là **green**, và thứ tự enumeration đổi tương ứng, miễn loader tìm các classpath entries theo thứ tự cấu hình của ví dụ.

**Giới hạn:** Không được khái quát thứ tự trên cho **mọi custom loader** hay khi có **nhiều named modules** chứa cùng resource. Theo Javadoc, thứ tự search modules trong một loader có thể không xác định. Khi một dự án thực sự yêu cầu cấu hình ưu tiên rõ ràng, hãy thiết kế nguồn và quy tắc ưu tiên thay vì phụ thuộc ngẫu nhiên vào resource trùng tên.

### 11.2 Dùng getResources để chẩn đoán trùng file

Nếu ứng dụng dùng nhiều JAR và load nhầm **META-INF/services/...**, **config/defaults.properties** hoặc template, in các URLs từ getResources. Đừng in nguyên nội dung nhạy cảm; việc liệt kê URL đủ giúp biết resource nằm ở những nguồn nào. Kết quả có thể là **jar:** URL hoặc nguồn tùy biến khác.

## 12. Context class loader, system loader và loader của Class

Java có nhiều con đường chọn loader, không nên nhầm:

| Cách lấy loader | Thông thường dùng cho |
| --- | --- |
| **MyClass.class.getClassLoader()** | Defining loader của MyClass; phù hợp khi muốn resource gắn với component chứa MyClass. |
| **ClassLoader.getSystemClassLoader()** | System/application loader của tiến trình JVM. |
| **ClassLoader.getSystemResource(name)** | Resource lookup qua system loader. |
| **Thread.currentThread().getContextClassLoader()** | Loader được đặt vào thread để phục vụ một số framework, service provider và runtime container. |

Một thư viện chạy trong application server có thể được parent loader định nghĩa nhưng cần tìm resource của plugin qua **thread context class loader**. Khi đó resource có thể không được nhìn thấy qua loader của chính class thư viện.

Ví dụ minh họa đặt trong method (không phải một chương trình standalone):

~~~java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

ClassLoader defining =
        SomeLibrary.class.getClassLoader();

URL fromContext = context == null ? null
        : context.getResource("plugin/settings.properties");

URL fromDefining = defining == null ? null
        : defining.getResource("plugin/settings.properties");
~~~

Hai URL có thể khác hoặc một bên null. Đừng chỉ đổi loader cho đến khi “hết null”; hãy làm rõ **resource thuộc component nào, ai sở hữu resource và runtime đang dùng loader nào**.

## 13. Chẩn đoán khi getResource trả null

Nếu bạn thấy:

~~~java
URL url = loader.getResource("config/app.properties");
// url == null
~~~

hãy kiểm tra có hệ thống.

### 13.1 Resource name có đúng từ root không?

Kiểm tra lỗi thừa/thiếu prefix:

- Sai với ClassLoader: **/config/app.properties** (slash đầu).
- Sai khi file đã được copy vào root: **src/main/resources/config/app.properties**.
- Sai khi file thực tế nằm trong package: **local.txt** thay vì **demo/local.txt**.
- Sai separator: **config\\app.properties** thay vì **config/app.properties**.

### 13.2 Resource đã được copy vào build output/JAR chưa?

Tồn tại trong Git repository **không chứng minh** nó đã được đóng gói vào artifact đang chạy. Kiểm tra thư mục output hay entries trong JAR:

~~~bash
jar --list --file demo-resources.jar
~~~

Dòng **config/app.properties** phải xuất hiện ở đúng root entry. Nếu dùng Maven/Gradle, kiểm tra nguồn resource, include/exclude filters và kết quả build.

### 13.3 Application chạy bằng classpath nào?

Phân biệt:

- Classpath khi compile với **javac**.
- Classpath khi launch bằng **java**.
- Module path nếu dùng named modules.
- Classpath trong IDE/test runner/container.

Việc compile được code **không đảm bảo** runtime launcher mang theo mọi file resource, vì compiler không nhất thiết đọc tài nguyên đó.

### 13.4 Đang hỏi đúng ClassLoader chưa?

So sánh:

~~~java
System.out.println(MyClass.class.getClassLoader());
System.out.println(ClassLoader.getSystemClassLoader());
System.out.println(Thread.currentThread().getContextClassLoader());
~~~

Đây là snippet đặt trong method với MyClass có thật. Kết quả loader khác nhau có thể hợp lệ; kiểm tra parent delegation và scope của plugin.

### 13.5 Có named module encapsulation không?

Đặc biệt với non-.class resources ở package của named modules: ClassLoader.getResource có thể không tìm thấy nếu package không được **opened unconditionally**, dù file tồn tại. Kiểm tra **module-info.java**, package của resource, nơi module được load và API caller đang dùng.

### 13.6 Tài nguyên có tên trùng bị che khuất không?

Nếu getResource trả URL nhưng nội dung “sai phiên bản”, dùng **getResources(name)** liệt kê các bản được tìm, xác minh thứ tự parent/classpath/JAR. Không giả định cùng filename chỉ có một bản trên classpath.

### 13.7 Có thực sự là lỗi I/O hay URL?

- **getResource == null**: thiếu resource, không được nhìn thấy hoặc không được phép theo API.
- **getResource trả URL nhưng url.openStream() lỗi**: tài nguyên có thể trở nên không truy cập được, archive bị đóng, vấn đề quyền hoặc I/O.
- **getResourceAsStream trả null**: không tự xem là stream rỗng; đây là dấu hiệu không tìm được/không có quyền truy cập.
- **name == null**: API báo NullPointerException, không phải “resource vắng mặt”.

Tách bước tìm tên khỏi bước mở và đọc stream để biết lỗi nằm ở đâu.

## 14. Resource lookup và bảo mật

Tài nguyên có thể chứa dữ liệu cấu hình nhạy cảm hoặc template có giá trị trong hệ thống, nhưng **ClassLoader.getResource không phải API bảo mật để che giấu bí mật**. Nếu đưa secret vào JAR, người có archive thường có thể đọc nội dung. Không đóng gói credentials hoặc private keys không được bảo vệ như resource công khai.

Nếu resource name đến từ input bên ngoài, hãy áp dụng allowlist và kiểm soát tên phù hợp. Không cho phép người dùng tùy ý truyền đường dẫn **../** rồi hy vọng loader tự ngăn mọi truy cập ngoài phạm vi. Trách nhiệm bảo vệ đường dẫn, tên nguồn và quyền truy cập còn phụ thuộc implementation cụ thể của custom loader và URL protocol.

Nếu tài nguyên lớn, cân nhắc streaming thay vì đọc tất cả bytes bằng **readAllBytes()**. Ví dụ dùng readAllBytes chỉ để code học tập ngắn và resource nhỏ. Đóng InputStream đúng cách bằng try-with-resources.

## 15. Tình huống thực hành và cách lựa chọn API

| Nhu cầu | Cách tiếp cận |
| --- | --- |
| Resource cố định ở root của component trên classpath | **MyClass.class.getClassLoader().getResource("config/app.properties")** nếu loader không null. |
| Resource nằm cạnh một class trong cùng package | **MyClass.class.getResource("local.txt")**. |
| Resource nằm từ root nhưng muốn gọi qua Class | **MyClass.class.getResource("/config/app.properties")**. |
| Chỉ cần đọc bytes/text | **getResourceAsStream** + try-with-resources. |
| Cần URL để một API khác mở tài nguyên | **getResource** rồi kiểm tra null và protocol; không coi luôn là File. |
| Nghi nhiều bản resource trùng tên | **getResources** / **resources**. |
| Resources của plugin không thuộc loader thư viện | Kiểm tra loader plugin hoặc thread context loader theo hợp đồng framework. |
| Named module có resource đóng kín | Cân nhắc **Module.getResourceAsStream** và quy tắc module openness, thay vì chỉ đổi dấu slash. |
| Resource nằm trên filesystem ngoài artifact | Dùng **Path/Files** với đường dẫn được cấu hình rõ; không đánh tráo với ClassLoader.getResource. |

**Phân biệt hai nguồn dữ liệu:**

~~~text
Filesystem external configuration:
    Path.of("/etc/myapp/config.yml")
    Files.newInputStream(path)

Classpath/module resource:
    loader.getResourceAsStream("config/defaults.yml")
~~~

Đây là hai mô hình phân phối khác nhau. Resources đóng gói thường phù hợp dữ liệu mặc định hoặc read-only; file external phù hợp cấu hình triển khai có thể thay đổi độc lập.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** App có class **com.example.Tool** và resource **com/example/help.txt**. Gọi **Tool.class.getClassLoader().getResource("help.txt")** có tự tìm dưới package com.example không?

**Đáp án:** Không. ClassLoader.getResource nhận tên từ root; hãy dùng **com/example/help.txt**, hoặc **Tool.class.getResource("help.txt")** nếu muốn tìm tương đối từ package.

**Câu 2.** Khi dùng ClassLoader, chuỗi **/config/app.properties** có ý nghĩa “tìm ở root” giống Class.getResource không?

**Đáp án:** Không. ClassLoader không áp dụng quy tắc bỏ slash đầu. Dùng **config/app.properties**. Với Class.getResource mới dùng slash đầu để chỉ root.

**Câu 3.** Vì sao resource tồn tại ở **src/main/resources/config/app.properties** nhưng code lại tìm **config/app.properties**?

**Đáp án:** Build thông thường copy nội dung của resources folder vào root resource output hoặc JAR. Tên src/main/resources không phải một phần của resource name runtime.

**Câu 4.** Đổi từ chạy thư mục classpath sang chạy JAR có buộc đổi đối số getResource không?

**Đáp án:** Không nếu đường dẫn entry bên trong root vẫn như cũ. API trả URL với scheme khác, nhưng resource name giữ nguyên.

**Câu 5.** Vì sao gọi **new File(url.getFile())** không đọc được resource bên trong JAR?

**Đáp án:** Entry JAR không tồn tại như file riêng ngoài filesystem. Hãy dùng **URL.openStream** hoặc **getResourceAsStream**.

**Câu 6.** Loader con có config.txt, parent cũng có config.txt. Với implementation ClassLoader mặc định, bản nào thường được tìm trước?

**Đáp án:** Parent trước, sau đó loader con gọi findResource nếu chưa tìm được.

**Câu 7.** Resource trong named module có thể tồn tại mà ClassLoader.getResource trả null không?

**Đáp án:** Có. Non-.class resources trong package của named module có thể bị module encapsulation che khuất nếu package không được opened unconditionally.

**Câu 8.** ClassLoader.getResource trả URL nhưng nội dung không đúng bản. API nào giúp tìm các bản trùng tên?

**Đáp án:** **getResources(name)** (hoặc **resources(name)** trên Java 9+), rồi kiểm tra từng nguồn.

**Câu 9.** Có thể xác nhận **loadClass("com.example.Tool")** sẽ thành công chỉ nhờ getResource("com/example/Tool.class") trả URL?

**Đáp án:** Không. Tìm class-file resource và JVM class loading là hai cơ chế khác về ý nghĩa; custom loaders và module rules có thể xử lý khác.

**Câu 10.** ClassLoader.getResource có tìm một file bất kỳ tương đối với CWD không?

**Đáp án:** Không. Nó tra các resource roots của loader; CWD chỉ có thể tác động gián tiếp tới cách các relative classpath entries được diễn giải khi chương trình khởi chạy.

## 17. Ghi nhớ

**Công thức thực hành quan trọng nhất:**

~~~text
ClassLoader.getResource("dir/file.txt")
     → dir/file.txt từ gốc resource search của loader

SomeClass.class.getResource("file.txt")
     → package/of/SomeClass/file.txt

SomeClass.class.getResource("/dir/file.txt")
     → dir/file.txt từ gốc (Class bỏ slash đầu)
~~~

- Resource name dùng **/**, không dùng OS path.
- **Classpath root** có thể là thư mục, JAR hoặc nguồn do custom loader cung cấp.
- URL có thể là **file:**, **jar:**, **jrt:** hoặc dạng khác; ưu tiên **openStream** thay vì giả định File.
- **getResourceAsStream** phù hợp việc đọc resource; luôn kiểm tra null và đóng stream.
- Parent-first delegation có thể quyết định URL được trả về; **getResources** giúp điều tra tên trùng.
- Trong **named modules**, resource encapsulation có thể ảnh hưởng lookup.
- Tài nguyên trên filesystem bên ngoài artifact là câu chuyện khác; dùng **Files/Path** khi đúng nhu cầu.
- Một resource tìm thấy không chứng minh lớp tương ứng đã được JVM load hoặc initialize.

### Tài liệu chuẩn

- **ClassLoader Java SE 21 API** – getResource, getResources, findResource, getResourceAsStream: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- **Class Java SE 21 API** – getResource và quy tắc tên relative/absolute: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- **Module Java SE 21 API** – getResourceAsStream và module encapsulation: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html
- **java Command Java SE 21** – -classpath và module/class loading: https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-004">getResourceAsStream và xử lý trường hợp không tìm thấy</a>

<details>
<summary>Xem chi tiết</summary>

# getResourceAsStream và xử lý trường hợp không tìm thấy tài nguyên trong Java

**`getResourceAsStream`** là API giúp đọc một **resource** được đóng gói cùng ứng dụng Java hoặc nhìn thấy được qua cơ chế tra cứu của class loader: tệp cấu hình, mẫu văn bản, dữ liệu JSON, chứng chỉ, biểu tượng, v.v. Nó trả về **`InputStream` nếu tìm được**, hoặc **`null` nếu không tìm được** — chứ không tự động ném `FileNotFoundException` cho một tài nguyên vắng mặt.

Điều này khiến các lỗi như `NullPointerException` khi tạo `InputStreamReader`, ứng dụng chạy trong IDE nhưng hỏng sau khi đóng gói JAR, hoặc "tệp nằm trong project mà JVM vẫn không thấy" rất phổ biến. Cách giải quyết là phân biệt **resource path**, **loader được dùng**, **tài nguyên thực sự nằm ở đâu trên runtime classpath/module path**, **chính sách xử lý missing** và **đóng luồng đúng cách**.

Bài viết sử dụng **JDK 17+**. Các chương trình hoàn chỉnh dùng `javac`/`java`; các ví dụ đóng gói resource minh họa cả Windows PowerShell và Linux/macOS, vì thao tác copy file khác nhau theo hệ điều hành.

## 1. Trước khi xử lý `null`, xác định nơi tra cứu

Một resource name như `config/app.properties` là tên dữ liệu trong phạm vi tìm kiếm của class loader hoặc module, không phải đường dẫn file tương đối với thư mục đang đứng. Lúc chạy, cùng tên đó có thể trỏ tới file trong thư mục output, entry trong JAR hoặc resource do một loader tùy chỉnh cung cấp.

Khi `getResourceAsStream(name)` trả `null`, hãy phân biệt ba câu hỏi:

1. **Tên có đúng không?** Với `ClassLoader` tên được tính từ resource root; với `Class` có thể tương đối với package.
2. **Nguồn có chứa resource không?** Cần kiểm tra output/JAR của bản thực sự chạy, không chỉ file trong `src/main/resources`.
3. **Ngữ cảnh có cho phép tìm không?** Loader được chọn và module encapsulation có thể khiến file hiện hữu không nhìn thấy được.

`ClassLoader.loadClass` nạp định nghĩa lớp, còn `getResourceAsStream` chỉ tìm/mở dữ liệu. Đừng thay resource lookup bằng `Path.of(name)` hay ép một URL `jar:` thành `File`: hai cách đó bỏ qua cách JAR và module tổ chức dữ liệu.

## 2. Hai họ API cùng tên nhưng quy tắc đường dẫn khác nhau

Phân biệt:

```java
ClassLoader loader = Example.class.getClassLoader();
InputStream a = loader.getResourceAsStream("config/app.properties");
InputStream b = Example.class.getResourceAsStream("/config/app.properties");
InputStream c = Example.class.getResourceAsStream("local.txt");
```

Đây là **snippet minh họa**, cần import `java.io.InputStream` và một class `Example` có thật. Hai cách lấy resource có chính sách diễn giải tên:

### `ClassLoader.getResourceAsStream(String name)`

- Tên là **resource name từ gốc namespace của loader**; ví dụ `"config/app.properties"`.
- Dùng dấu `/` để phân tách các phần của resource name, kể cả khi chạy trên Windows.
- **Không thêm `/` ở đầu**. `loader.getResourceAsStream("/config/app.properties")` thường không tìm được resource tại `config/app.properties`, vì loader không có bước chuẩn loại leading slash giống `Class.getResource...`.
- Thư mục output hoặc các JAR mà loader nhìn thấy quyết định có tài nguyên ấy không.
- `getResourceAsStream` thường dựa vào `getResource` rồi mở stream; nếu không có URL hoặc không mở được, kết quả có thể là `null`. Không coi `null` chỉ có một nguyên nhân duy nhất là sai tên.

### `Class.getResourceAsStream(String name)`

- Nếu `name` **bắt đầu bằng `/`**, tìm từ **gốc namespace**: `SomeClass.class.getResourceAsStream("/config/app.properties")`.
- Nếu `name` **không bắt đầu bằng `/`**, tên được hiểu **tương đối với package của class**: với `demo.Example`, `Example.class.getResourceAsStream("local.txt")` tìm resource tương ứng `demo/local.txt`.
- Cách này tiện cho tài nguyên nằm ngay cạnh class, nhưng cũng dễ sai khi người viết nghĩ `"config/app.properties"` luôn là đường dẫn từ classpath root.

### Bảng quy đổi

Giả sử `demo.Example` nằm trong package `demo` và runtime có hai resource `config/app.properties`, `demo/local.txt`:

| Lệnh gọi | Resource name được tra cứu | Kỳ vọng |
| --- | --- | --- |
| `loader.getResourceAsStream("config/app.properties")` | `config/app.properties` | Tìm được |
| `loader.getResourceAsStream("/config/app.properties")` | `/config/app.properties` | Thường không tìm được |
| `Example.class.getResourceAsStream("/config/app.properties")` | `config/app.properties` | Tìm được |
| `Example.class.getResourceAsStream("local.txt")` | `demo/local.txt` | Tìm được |
| `Example.class.getResourceAsStream("/demo/local.txt")` | `demo/local.txt` | Tìm được |
| `Example.class.getResourceAsStream("config/app.properties")` | `demo/config/app.properties` | Không tìm được nếu chỉ có resource ở gốc |

Trên Java 9+, named modules có các quy tắc **encapsulation** đối với resource. Vì vậy bảng trên là mô hình lookup thông thường cho tài nguyên của classpath/unnamed module; với named modules còn phải xét quyền truy cập và cách tài nguyên được đóng gói.

## 3. Không tìm thấy trả `null`: phải kiểm tra trước khi đọc

Sai lầm rất thường gặp:

```java
InputStream input =
        Example.class.getClassLoader()
                .getResourceAsStream("config/missing.properties");
Properties props = new Properties();
props.load(input); // input == null: thất bại do dùng null
```

Đoạn trên là **code minh họa lỗi**, không phải bài thực hành nên làm. Nếu không tìm được resource, `getResourceAsStream` trả `null`. Khi chuyển `null` cho `Properties.load` hoặc `new InputStreamReader(input)`, ứng dụng có thể ném `NullPointerException`, khiến người đọc log không rõ resource nào đã bị thiếu.

Đúng hơn là **quyết định chính sách** trước khi đọc:

- **Resource bắt buộc:** báo lỗi với **tên resource và ngữ cảnh loader/module** đủ để debug. Có thể ném `FileNotFoundException` hoặc `IllegalStateException` tùy API boundary.
- **Resource tùy chọn:** dùng giá trị mặc định được tài liệu hóa; không làm như thể resource đã tồn tại.
- **Resource cung cấp bởi plugin:** nếu loader không nhìn thấy, kiểm tra plugin dependency/packaging, không âm thầm đọc nhầm bản từ loader khác.
- **Resource có nội dung nhạy cảm:** lỗi có thể ghi resource name đã được kiểm soát, nhưng tránh in secrets hoặc URL chứa credentials vào log.

Một mẫu bắt buộc, không che nguyên nhân:

```java
static InputStream openRequired(ClassLoader loader, String name)
        throws java.io.FileNotFoundException {
    InputStream stream = loader.getResourceAsStream(name);
    if (stream == null) {
        throw new java.io.FileNotFoundException(
                "Classpath resource not found: " + name);
    }
    return stream;
}
```

Đây là một **method minh họa**; gọi nó từ class cụ thể, đặt kết quả trong `try-with-resources`. Không truyền `loader == null` vào mẫu trên; với bootstrap classes hoặc module khác, hãy chọn API lookup phù hợp thay vì giả định luôn có loader object.

### `FileNotFoundException` là do mã của ta ném, không phải do `getResourceAsStream`

Nếu resource thiếu, `getResourceAsStream` trả `null`. Trong mẫu `openRequired`, chính ứng dụng lựa chọn ném `FileNotFoundException` để diễn đạt contract "resource bắt buộc". Nhờ vậy stacktrace rõ hơn nhưng không được nói rằng phương thức JDK trực tiếp ném lỗi này.

## 4. Thực hành A — đọc `.properties` UTF-8, kiểm tra null và đóng stream

Tạo một project nhỏ:

```text
resource-lab/
├── src/
│   └── demo/
│       └── ConfigDemo.java
└── resources/
    └── config/
        └── app.properties
```

**`resources/config/app.properties`:**

```properties
app.name=Inventory
timeout.seconds=15
welcome=Chào Java
```

**`src/demo/ConfigDemo.java`:**

```java
package demo;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class ConfigDemo {
    public static void main(String[] args) throws Exception {
        String resourceName = "config/app.properties";
        ClassLoader loader = ConfigDemo.class.getClassLoader();

        InputStream input = loader.getResourceAsStream(resourceName);
        if (input == null) {
            throw new FileNotFoundException(
                    "Missing required resource: " + resourceName);
        }

        Properties properties = new Properties();
        try (InputStream stream = input;
             InputStreamReader reader =
                     new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        System.out.println("name=" +
                properties.getProperty("app.name"));
        System.out.println("timeout=" +
                properties.getProperty("timeout.seconds"));
        System.out.println("welcome=" +
                properties.getProperty("welcome"));
    }
}
```

**Biên dịch** từ thư mục `resource-lab`:

```sh
javac --release 17 -d out src/demo/ConfigDemo.java
```

Lệnh `javac -d out` chỉ tạo `.class`. Ta cần **chép resource vào thư mục output** để namespace runtime có entry `config/app.properties`.

Trên **Windows PowerShell**:

```powershell
New-Item -ItemType Directory -Force out\config | Out-Null
Copy-Item resources\config\app.properties out\config\app.properties
java -cp out demo.ConfigDemo
```

Trên **Linux/macOS**:

```sh
mkdir -p out/config
cp resources/config/app.properties out/config/app.properties
java -cp out demo.ConfigDemo
```

**Stdout của lệnh `java`, khi tệp được lưu là UTF-8:**

```text
name=Inventory
timeout=15
welcome=Chào Java
```

**Vì sao chương trình chạy đúng?**

1. `ConfigDemo.class.getClassLoader()` trả về loader nhìn thấy `out` do `-cp out`.
2. `"config/app.properties"` là tên resource từ classpath root, tương ứng `out/config/app.properties` sau khi copy.
3. `getResourceAsStream` trả `InputStream`; chúng ta kiểm tra `null` trước khi đưa nó vào Reader.
4. `InputStreamReader(stream, StandardCharsets.UTF_8)` giải mã bytes văn bản với charset rõ ràng, giữ dấu tiếng Việt.
5. `Properties.load(Reader)` đọc từng cặp `key=value`; `getProperty` trả chuỗi giá trị.
6. `try-with-resources` đóng Reader và stream, kể cả khi `Properties.load` ném `IOException`.

**Chi tiết charset đáng nhớ:** `Properties.load(InputStream)` theo Java API diễn giải byte theo ISO-8859-1 (các ký tự ngoài bảng mã phải escape). Muốn đọc tệp properties **UTF-8 trực tiếp**, dùng `Properties.load(Reader)` với `InputStreamReader(StandardCharsets.UTF_8)` như trên. Đừng tùy tiện thay bằng `properties.load(input)` nếu tệp có dấu tiếng Việt.

**Không nhầm runtime working directory và classpath:** đường dẫn `resources/config/app.properties` tồn tại trong source tree không bảo đảm loader thấy nó. Trình chạy dùng `-cp out`, nên tài nguyên phải nằm trong `out` (hoặc trong JAR/classpath khác mà loader nhìn thấy). Trên Maven/Gradle, resource processing thường copy từ `src/main/resources` vào output, nhưng ví dụ thủ công này yêu cầu bước copy rõ ràng.

## 5. Thực hành B — chứng minh package-relative và classpath-root khác nhau

Bài tiếp theo dùng một class ở package `demo` và hai resources có **cùng filename** nhưng nằm ở hai vị trí khác nhau:

```text
path-lab/
├── src/demo/PathDemo.java
└── resources/
    ├── messages.txt
    └── demo/
        └── messages.txt
```

**`resources/messages.txt`:**

```text
hello-from-root
```

**`resources/demo/messages.txt`:**

```text
hello-from-package
```

**`src/demo/PathDemo.java`:**

```java
package demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PathDemo {
    static String text(InputStream input) throws IOException {
        if (input == null) {
            return "<missing>";
        }
        try (InputStream stream = input) {
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = PathDemo.class.getClassLoader();

        System.out.println("relative=" +
                text(PathDemo.class.getResourceAsStream("messages.txt")));
        System.out.println("class absolute=" +
                text(PathDemo.class.getResourceAsStream("/messages.txt")));
        System.out.println("loader root=" +
                text(loader.getResourceAsStream("messages.txt")));
        System.out.println("loader leading slash missing=" +
                (loader.getResourceAsStream("/messages.txt") == null));
        System.out.println("missing=" +
                text(PathDemo.class.getResourceAsStream("absent.txt")));
    }
}
```

Biên dịch:

```sh
javac --release 17 -d out src/demo/PathDemo.java
```

**Windows PowerShell** — copy đúng hai resources rồi chạy:

```powershell
New-Item -ItemType Directory -Force out\demo | Out-Null
Copy-Item resources\messages.txt out\messages.txt
Copy-Item resources\demo\messages.txt out\demo\messages.txt
java -cp out demo.PathDemo
```

**Linux/macOS**:

```sh
mkdir -p out/demo
cp resources/messages.txt out/messages.txt
cp resources/demo/messages.txt out/demo/messages.txt
java -cp out demo.PathDemo
```

**Stdout của lệnh `java`:**

```text
relative=hello-from-package
class absolute=hello-from-root
loader root=hello-from-root
loader leading slash missing=true
missing=<missing>
```

Nhận xét:

- `PathDemo.class.getResourceAsStream("messages.txt")` dùng package của `PathDemo` → `demo/messages.txt`.
- `PathDemo.class.getResourceAsStream("/messages.txt")` dùng **classpath root** → `messages.txt`.
- `loader.getResourceAsStream("messages.txt")` cũng dùng tên từ **gốc namespace loader** → `messages.txt`.
- `loader.getResourceAsStream("/messages.txt")` không áp dụng quy tắc bỏ slash của `Class`; do vậy không thấy resource đã đóng gói.
- `PathDemo.class.getResourceAsStream("absent.txt")` cho `null`, và helper `text` biến thành chuỗi hiển thị `<missing>`. Trong hệ thống thật, nên phân biệt resource bắt buộc/tùy chọn chứ không luôn nuốt trường hợp thiếu.

Trong ví dụ học tập, `trim()` chỉ loại khoảng trắng/newline ở cuối để stdout dễ kiểm tra. Không dùng `trim()` bừa bãi cho các định dạng mà whitespace mang ý nghĩa, như template thụt dòng, chữ ký hoặc file cấu hình yêu cầu chính xác từng byte.

## 6. Thực hành C — phân biệt resource bắt buộc và resource tùy chọn

Đây là một API wrapper dễ áp dụng: **bắt buộc thì ném lỗi có thông tin**, còn **tùy chọn thì fallback được chỉ định tường minh**.

Cấu trúc:

```text
missing-lab/
├── src/demo/MissingDemo.java
└── resources/templates/report.txt
```

**`resources/templates/report.txt`:**

```text
Report template
```

**`src/demo/MissingDemo.java`:**

```java
package demo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MissingDemo {
    static String required(ClassLoader loader, String name)
            throws IOException {
        InputStream opened = loader.getResourceAsStream(name);
        if (opened == null) {
            throw new FileNotFoundException(
                    "Resource not found: " + name);
        }
        try (InputStream stream = opened) {
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    static String optional(ClassLoader loader, String name,
                           String fallback) throws IOException {
        InputStream opened = loader.getResourceAsStream(name);
        if (opened == null) {
            return fallback;
        }
        try (InputStream stream = opened) {
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws IOException {
        ClassLoader loader = MissingDemo.class.getClassLoader();

        System.out.println("required=" +
                required(loader, "templates/report.txt"));
        try {
            required(loader, "templates/absent.txt");
        } catch (FileNotFoundException e) {
            System.out.println("missing=" + e.getMessage());
        }
        System.out.println("optional=" +
                optional(loader, "templates/absent.txt", "fallback"));
    }
}
```

Biên dịch:

```sh
javac --release 17 -d out src/demo/MissingDemo.java
```

Copy tài nguyên và chạy trên **Windows PowerShell**:

```powershell
New-Item -ItemType Directory -Force out\templates | Out-Null
Copy-Item resources\templates\report.txt out\templates\report.txt
java -cp out demo.MissingDemo
```

Trên **Linux/macOS**:

```sh
mkdir -p out/templates
cp resources/templates/report.txt out/templates/report.txt
java -cp out demo.MissingDemo
```

**Stdout của lệnh `java`:**

```text
required=Report template
missing=Resource not found: templates/absent.txt
optional=fallback
```

Giải thích contract:

- `required(...)` ném `FileNotFoundException` **chỉ khi lookup trả `null`**. Khi resource có nhưng đọc thất bại, `IOException` từ luồng được truyền ra nguyên nhân tương ứng.
- `optional(...)` chỉ trả fallback **khi không tìm được resource**; nó **không coi lỗi I/O khi đọc một resource có thật là như chưa có**. Cách này tránh che lỗi file/JAR bị hỏng.
- Cả hai phương thức dùng `try-with-resources` nên đóng luồng kể cả khi việc đọc phát sinh lỗi.
- Fallback `"fallback"` là ví dụ cho tùy chọn hợp lệ. Không tự động đưa fallback cho cấu hình quan trọng như khóa ký, endpoint thanh toán hay certificate bắt buộc.
- Tên resource là một phần của thông điệp lỗi, giúp debug khác biệt giữa `templates/report.txt` và `templates/absent.txt`.

Một ứng dụng thực tế có thể trả `Optional<String>` cho resource tùy chọn. Tuy nhiên nếu dùng `Optional`, đừng ép mọi exception thành `Optional.empty()`: **vắng mặt** và **đọc thất bại** là hai trạng thái khác nhau.

## 7. Resource đóng gói trong JAR vẫn được đọc qua cùng API

Sau khi đã có thư mục `out` của ví dụ A (bao gồm `demo/ConfigDemo.class` và `config/app.properties`), đóng gói:

```sh
jar --create --file resource-demo.jar -C out .
jar --list --file resource-demo.jar
java -cp resource-demo.jar demo.ConfigDemo
```

Đây là các lệnh có trong JDK 17+. Trong output `jar --list`, hãy tìm **cả**:

```text
demo/ConfigDemo.class
config/app.properties
```

Hai dòng trên là **các entries cần kiểm tra**, không phải cam kết toàn bộ output của `jar --list` chỉ có hai dòng hay đúng thứ tự ấy.

**Stdout của lệnh `java` trong ví dụ A vẫn là:**

```text
name=Inventory
timeout=15
welcome=Chào Java
```

Điều này hoạt động vì JVM và `URLClassLoader`/built-in loader biết đọc resource dưới dạng entry của JAR. Ứng dụng không cần đoán đường dẫn tạm để giải nén.

Tránh cách sau:

```java
URL url = ConfigDemo.class.getResource("/config/app.properties");
File file = new File(url.getFile());
```

Đây là **mẫu dễ sai** (thiếu import `URL`/`File` nếu copy độc lập): nó có thể tình cờ chạy khi `url` là `file:` nhưng không đáng tin khi URL là `jar:file:...!/...` hoặc khi tên tệp có dấu cách/percent-encoding. Với dữ liệu chỉ cần đọc, **`getResourceAsStream`** loại bỏ sự phụ thuộc vào URL→filesystem path.

Nếu một thư viện thực sự đòi `Path`, bạn có thể phải copy nội dung từ stream sang **temporary file** và quản lý xóa file; hoặc dùng API filesystem provider dành cho JAR khi phù hợp. Nhưng không nên làm thế chỉ để đọc vài kilobytes cấu hình.

## 8. Classpath, working directory và thứ tự build

Một lý do khiến resource chạy trong IDE nhưng không chạy khi dùng `java -cp` là **khác cách đóng gói**:

```text
source layout:                         runtime -cp out:
src/demo/ConfigDemo.java              out/demo/ConfigDemo.class
resources/config/app.properties       out/config/app.properties
```

Dòng `resources/config/app.properties` không được `javac` tự copy vào `out`. Khi chạy `java -cp out demo.ConfigDemo`, loader tra cứu namespace dưới `out`. Nếu quên copy, `getResourceAsStream("config/app.properties") == null`.

Trong dự án dùng Maven hoặc Gradle, thư mục convention như `src/main/resources` thường được build tool copy vào class output/JAR. Đây là **hành vi build tooling**, không phải một tính năng tự động của `ClassLoader`. Luôn kiểm tra artifact runtime:

- Thư mục classes/output có file nằm đúng relative path chưa?
- JAR có entry resource không? Kiểm bằng `jar --list --file app.jar`.
- IDE run configuration có classpath khác bản release không?
- Artifact fat/uber JAR có merge tài nguyên từ nhiều dependency đúng chính sách không?
- Test resources có chỉ xuất hiện trong test classpath, không xuất hiện trong application artifact không?
- Container deployment có bỏ sót hoặc thay thư mục/JAR bằng bản cũ không?

**Working directory** ảnh hưởng những lệnh như `Files.readString(Path.of("config/app.properties"))`; nó **không tự quyết định root của classpath resource lookup**. Trong các ví dụ, ta nhắc chạy từ project root để lệnh copy và `jar` đi đúng đường dẫn, không phải vì `getResourceAsStream` tìm resource từ working directory.

### Không thêm `src/main/resources` vào đường dẫn tra cứu

Nếu build đã copy `src/main/resources/config/app.properties` thành `config/app.properties` ở classpath root, gọi đúng là:

```java
loader.getResourceAsStream("config/app.properties");
```

Không phải `"src/main/resources/config/app.properties"`, vì `src/main/resources` là **vị trí trong repository**, không phải bắt buộc là một phần của **resource name trong artifact**.

## 9. `getResourceAsStream`, `getResource` và `getResources`

Ba API có mục đích khác nhau:

- **`getResourceAsStream(name)`:** muốn **đọc nội dung** ngay. Nhận `InputStream` và đóng sau khi dùng.
- **`getResource(name)`:** muốn **biết URL được chọn** trước khi mở stream. Hữu ích để debug nơi resource được tìm thấy, nhưng URL có thể đại diện entry của JAR hoặc giao thức tùy runtime.
- **`getResources(name)`:** muốn **liệt kê các resource trùng tên** mà loader tìm được qua cơ chế của nó. Trả về `Enumeration<URL>`, không phải `InputStream`.

Ví dụ method hỗ trợ debug khi nghi ngờ **trùng tên resource ở nhiều JAR**:

```java
static void printResources(ClassLoader loader, String name)
        throws java.io.IOException {
    java.util.Enumeration<java.net.URL> urls =
            loader.getResources(name);
    while (urls.hasMoreElements()) {
        System.out.println(urls.nextElement());
    }
}
```

Đây là method **minh họa**, cần được gọi với loader và tên resource thực tế; kết quả URL phụ thuộc môi trường nên không có một stdout cố định. `getResources` có thể giúp chẩn đoán nhiều JAR cung cấp `META-INF/services/...`, `config/app.properties` hay metadata cùng tên.

**Đừng tự áp đặt rằng `getResource` phải chọn "JAR được liệt kê đầu tiên trong source tree"**. Thứ tự lookup phụ thuộc loader graph, classpath/module placement và implementation. Default `ClassLoader.getResource` thông thường ủy quyền cho parent trước khi tự tìm, nhưng custom loaders có thể override cơ chế resource lookup. Nếu ứng dụng yêu cầu ưu tiên rõ, cần thiết kế/kiểm thử chính sách riêng thay vì dựa vào thứ tự ngầm.

Với resources từ nhiều module ở các layers khác nhau, hành vi khám phá còn phụ thuộc module access và class loader cụ thể. Không dùng `getResources` như cách bỏ qua encapsulation.

## 10. Quan hệ với parent delegation và nhiều class loaders

**Resource lookup không giống class loading**, dù cả hai thường dựa vào loader graph:

```text
HostLoader
  ├── class: api.Plugin
  └── resource: config/defaults.properties
       ▲
       │ parent
  PluginLoader
  ├── class: impl.MyPlugin
  └── resource: plugin/settings.json
```

Khi plugin gọi:

```java
InputStream stream =
        pluginLoader.getResourceAsStream("config/defaults.properties");
```

kết quả phụ thuộc **policy của `pluginLoader` và ancestor**. Với loader parent-first mặc định, resource của parent có thể được trả về. Nhưng custom plugin loaders có thể override `getResource` hoặc `getResources`; một loader dùng child-first class policy chưa chắc dùng cùng thứ tự cho resources.

### Chọn loader nào?

Có nhiều lựa chọn:

| Bối cảnh | Lựa chọn đáng cân nhắc | Vì sao |
| --- | --- | --- |
| Resource đóng gói cùng class `demo.MyComponent` | `MyComponent.class.getResourceAsStream("name.txt")` | Package-relative, gắn lookup với runtime `Class<?>` cụ thể. |
| Resource tại root của classpath mà loader đang quản lý | `loader.getResourceAsStream("config/x.properties")` | Tường minh root, tường minh loader. |
| Plugin cần resource nằm trong JAR plugin | Loader đã định nghĩa plugin class, hoặc `PluginImplementation.class.getResourceAsStream(...)` | Tránh mặc định hỏi system loader không nhìn thấy plugin JAR. |
| Framework tìm provider/resource theo thread context | `Thread.currentThread().getContextClassLoader()` khi contract framework quy định | TCCL có thể do container/plugin framework cấu hình. |
| Class loader của một class là `null` | `SomeClass.class.getResourceAsStream(...)` hoặc API module/phương án hệ thống phù hợp | Không gọi method trên loader `null`; thường class được bootstrap định nghĩa. |

### Class loader của resource và class loader định nghĩa class khác nhau

Ví dụ plugin JAR có class `impl.Plugin` do plugin loader định nghĩa. Nếu code host cố gọi:

```java
HostMain.class.getClassLoader()
        .getResourceAsStream("plugin/settings.json");
```

có thể nhận `null`, vì host loader không nhìn thấy JAR plugin (thường plugin loader là **child**, host là **parent**). Ngược lại, code trong plugin dùng `impl.Plugin.class.getResourceAsStream("/plugin/settings.json")` có thể thấy resource riêng nếu lookup được loader/module của plugin cho phép.

**Đừng đổi tất cả sang system loader** khi thấy `null`. Phải biết resource thuộc artifact/module nào và **loader nào được thiết kế để nhìn thấy nó**.

## 11. Thread Context Class Loader (TCCL) không phải loader mặc định của mọi API

Đọc TCCL:

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();
```

Đây là snippet hợp lệ trong method. Trong launcher phổ biến, main thread có TCCL là system loader; framework có thể đổi khi thực thi plugin code.

Hai lời gọi sau **không tương đương**:

```java
MyService.class.getResourceAsStream("service.json");
Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("service.json");
```

- Lời gọi trên class dùng **package-relative** với `MyService` và lookup theo class/module.
- Lời gọi qua TCCL dùng **resource name từ root namespace** của TCCL và chính loader object đó. Nó không tự gắn với package `MyService`.
- TCCL có thể là `null` trong một số ngữ cảnh; nếu framework chấp nhận điều đó, cần quyết định fallback tường minh. Không dereference null.
- `ClassLoader.getSystemResourceAsStream(name)` là một API static dùng **system class loader**, không phải mặc định dùng TCCL.
- TCCL không thay đổi parent của những loader khác và không làm resource không có trong artifact tự xuất hiện.

Các API framework như `ServiceLoader` có thể dùng TCCL cho discovery, nhưng code tự gọi `SomeClass.class.getResourceAsStream` **không tự chuyển sang TCCL** chỉ vì thread đang chạy trong plugin context.

## 12. Named modules: tìm thấy class nhưng resource vẫn có thể bị che

Từ Java 9, resource lookup qua **named modules** có quy tắc **strong encapsulation**. Javadoc của `ClassLoader.getResource`/`getResourceAsStream` chỉ rõ resource trong các package của named module có thể không nhìn thấy nếu package đó **không được mở vô điều kiện** cho các lookup kiểu này (trừ những quy định riêng như resource kết thúc bằng `.class`). Một package có `exports` không đồng nghĩa mọi resource bên trong đều có thể đọc qua loader từ bên ngoài.

Các khái niệm phải phân biệt:

- **Module readability**: code ở module A có đọc module B hay không?
- **Exports**: package có cho các module khác truy cập public types không?
- **Opens**: package có cho phép cơ chế reflection/resource access theo quy tắc JPMS không?
- **Loader visibility**: loader có thể xác định/tra cứu mục resource hay không?
- **Resource access**: API nào được gọi, từ module/caller nào, package có được mở cho caller đó không?

Nếu code **bên trong một module** cần đọc resource được đóng gói trong chính module, `SomeClass.class.getResourceAsStream(...)` là lựa chọn tự nhiên vì API này gắn với module của class. Trong các trường hợp cross-module phức tạp, `Module.getResourceAsStream(name)` có quy tắc cụ thể về quyền truy cập; cần đọc Javadoc và kiểm thử trong cấu hình module thực tế.

Ví dụ module descriptor:

```java
module com.example.reports {
    exports com.example.reports.api;
    opens com.example.reports.templates;
}
```

Đây là **module-info.java minh họa**, không phải ví dụ chạy độc lập. `opens` vô điều kiện cho package có thể làm resource trong package ấy nhìn thấy qua một số API resource lookup. Tuy nhiên mở package công khai cũng có ảnh hưởng về deep reflection; **không nên mở tràn lan chỉ để tránh một lỗi path**. Nếu module tự đọc resource riêng, nên dùng API và thiết kế package/module phù hợp.

Khi đã kiểm tra resource nằm trong JAR/module nhưng `getResourceAsStream` vẫn là `null`, đừng kết luận ngay loader sai; **encapsulation** có thể là nguyên nhân.

## 13. Đọc dữ liệu text, binary và quản lý charset

`getResourceAsStream` trả byte stream. API không biết resource là UTF-8, ISO-8859-1, PNG hay Protobuf. Chính caller phải quyết định **cách giải mã nội dung**.

### Text nhỏ

```java
static String readUtf8(java.io.InputStream stream)
        throws java.io.IOException {
    try (java.io.InputStream in = stream) {
        return new String(in.readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
```

Method này nhận **stream không null**, đọc hết vào bộ nhớ và đóng stream. Chỉ phù hợp resource có kích thước vừa phải; đừng dùng `readAllBytes()` cho một resource rất lớn hoặc nguồn untrusted có thể stream không giới hạn.

### Text lớn

Với tài liệu lớn, đọc theo buffer hoặc `BufferedReader`:

```java
try (InputStream input = loader.getResourceAsStream(name)) {
    if (input == null) {
        throw new FileNotFoundException(name);
    }
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
            // Xử lý từng dòng thay vì giữ toàn bộ vào RAM.
        }
    }
}
```

Đây là **snippet**, giả sử đã khai báo `loader`, `name` và import `java.io.*`/`java.nio.charset.StandardCharsets`. Chú ý `try-with-resources` **chấp nhận reference null** lúc khởi tạo resource, nhưng ta vẫn phải kiểm tra trước khi dùng; việc có `try (...)` không làm resource thiếu tự tạo stream. Đóng `BufferedReader` cũng đóng luồng đầu vào bên dưới.

### Binary

Đối với PNG/ZIP/keystore/certificate bytes, **không** gọi `new String(bytes, UTF_8)` rồi ghi ngược ra file nếu muốn giữ từng byte. Hãy sao chép stream sang output:

```java
try (InputStream input = loader.getResourceAsStream(name)) {
    if (input == null) {
        throw new FileNotFoundException(name);
    }
    try (OutputStream output = Files.newOutputStream(destination)) {
        input.transferTo(output);
    }
}
```

Snippet cần biến `loader`, `name`, `destination` và imports `java.io.*`/`java.nio.file.Files`. `InputStream.transferTo` có từ Java 9, truyền bytes mà không đổi charset. Trước khi cho ghi file theo `destination`, ứng dụng vẫn phải xác thực đường dẫn được phép. Không dùng resource lookup như cơ chế cấp quyền tùy ý để đọc/ghi file trên máy.

### Resource có thể rỗng

`getResourceAsStream` trả **non-null stream** cho resource tồn tại nhưng 0 byte; đọc sẽ trả EOF. **Không đồng nhất file rỗng với missing**. Nếu config bắt buộc phải có nội dung, sau khi mở stream cần validation riêng (ví dụ parser yêu cầu key bắt buộc).

## 14. Đóng luồng, lỗi I/O và quyền sở hữu tài nguyên

Một tài nguyên có thể nằm trong JAR và việc đọc có thể giữ handle tới archive hoặc kết nối nguồn dữ liệu. **Đóng stream sau khi dùng** để trả tài nguyên về hệ thống và tránh resource leaks.

Mẫu code nên tuân theo:

1. **Tra cứu:** `InputStream opened = loader.getResourceAsStream(name)`.
2. **Kiểm tra missing:** `opened == null` thì ném lỗi hoặc fallback theo contract.
3. **Đọc trong `try-with-resources`.**
4. **Parse/validate nội dung.**
5. **Truyền lỗi I/O hoặc bọc kèm ngữ cảnh mà vẫn giữ cause.**

Không nên:

```java
try {
    InputStream input = loader.getResourceAsStream(name);
    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
} catch (Exception ignored) {
    return "";
}
```

Đây là **mẫu sai**: vừa có `NullPointerException` khi missing, vừa có thể nuốt `IOException`, lại không đóng stream. Chuỗi rỗng sẽ đánh đồng resource thiếu, resource hỏng và resource có nội dung rỗng.

Một wrapper production có thể chọn ném `UncheckedIOException` từ API không khai báo checked exceptions. Nếu vậy hãy **giữ cause gốc** để người dùng phân biệt "not found" với "cannot read" và biết resource name, loader/context liên quan.

### Resource nằm trong JAR và stream có thể mở được nhưng đọc hỏng

`getResourceAsStream` có thể trả stream trước khi các lỗi đọc bytes được phát hiện. Không giả định non-null stream có nghĩa dữ liệu hoàn chỉnh hoặc hợp lệ. Với JSON/CSV/properties, cần xử lý parse errors và validation riêng; log tên resource và nguyên nhân, không tự động chuyển mọi lỗi thành `null`.

## 15. Một resource tồn tại nhiều lần: ưu tiên và xung đột

Hai dependency JAR cùng có `config/app.properties`:

```text
a.jar!/config/app.properties
b.jar!/config/app.properties
```

Nếu ứng dụng dùng:

```java
ClassLoader loader = App.class.getClassLoader();
URL selected = loader.getResource("config/app.properties");
```

Đây là snippet minh họa cần `App`/`URL`. Loader có thể trả **một URL được chọn** theo policy của nó. Không có ý nghĩa chuẩn rằng `a.jar` luôn được ưu tiên trong mọi build và launcher. Nếu nhiều resources cần hợp nhất, `getResources(name)` có thể cung cấp enumeration để ứng dụng áp chính sách rõ ràng.

Trường hợp điển hình:

- `META-INF/services/<service binary name>` thường xuất hiện ở nhiều JAR và cần cơ chế discovery đọc nhiều resources.
- `logging.properties` hay `config/app.properties` xuất hiện ở nhiều JAR có thể tạo lỗi cấu hình khó thấy nếu lookup chỉ trả bản đầu tiên.
- Resource trùng tên giữa host/plugin có thể vô tình lấy bản của parent thay vì plugin.

Cách phòng tránh:

- Dùng tên resource có namespace rõ, ví dụ `plugins/my-feature/settings.json`.
- Định nghĩa nơi đặt config override có thứ tự ưu tiên **được ghi trong tài liệu**.
- Nếu plugin phải có dữ liệu riêng, dùng loader/module của plugin hoặc một API cung cấp resource tường minh.
- Kiểm thử **artifact thực tế**, không chỉ test file nguồn trong IDE.
- In/ghi log URL bằng `getResource` khi debug, nhưng tránh tiết lộ đường dẫn nhạy cảm trong thông báo dành cho người dùng cuối.

## 16. Kiểm tra bảo mật: đừng biến resource name thành đường dẫn tùy ý

Khi tên resource được tạo từ đầu vào không tin cậy:

```java
String name = "templates/" + userChoice + ".txt";
InputStream stream = loader.getResourceAsStream(name);
```

Đây là snippet giải thích nguy cơ. Không được giả định việc cộng chuỗi như vậy tự ngăn tên có dấu `../`, package namespace bất ngờ hoặc một resource nhạy cảm nằm trong artifact. **Ưu tiên allowlist** các template được phép thay vì nhận đường dẫn tùy ý:

```java
static String allowedTemplate(String choice) {
    return switch (choice) {
        case "welcome" -> "templates/welcome.txt";
        case "goodbye" -> "templates/goodbye.txt";
        default -> throw new IllegalArgumentException(
                "Unknown template: " + choice);
    };
}
```

Đây là method Java 17+ hợp lệ để đặt trong class, nhưng không phải chương trình độc lập. Nó ánh xạ các giá trị nghiệp vụ sang resource names cố định, giúp dễ audit. Nếu resource data cần bảo mật, không đóng gói credentials/password production trực tiếp vào JAR và xem việc che tên file như một cơ chế bảo vệ.

**Lưu ý:** `getResourceAsStream` là API tra cứu và đọc; nó không tạo sandbox hoàn chỉnh. Một custom loader hoặc URL handler có thể có side effects, và resource contents vẫn cần validation trước khi dùng.

## 17. Bảng lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân hay gặp | Cách kiểm tra và sửa |
| --- | --- | --- |
| `getResourceAsStream(...) == null` dù tệp trong repository | Build không copy resource vào classpath/module artifact | Kiểm tra `out/...`, `target/classes`, `build/resources/main` và JAR entry đúng đường dẫn. |
| `null` khi dùng `"/config/x"` qua `ClassLoader` | Có slash đầu khiến tên không khớp | Dùng `loader.getResourceAsStream("config/x")`. |
| `null` khi dùng `SomeClass.class.getResourceAsStream("x")` | API tìm theo package của class, không từ root | Chuyển thành `"/x"` nếu resource nằm ở root, hoặc đặt file trong package. |
| Chạy IDE được, JAR thất bại | Resource không được đóng gói hoặc bị shadow | Xem `jar --list`, build artifact, classpath và URL lookup. |
| `NullPointerException` ở `InputStreamReader`/`Properties.load` | Stream trả `null` chưa được kiểm tra | Check trước, báo lỗi kèm resource name, không nuốt lỗi. |
| Văn bản tiếng Việt lỗi dấu | Đọc text với charset sai, hoặc `Properties.load(InputStream)` dùng ISO-8859-1 | Đọc với `InputStreamReader(StandardCharsets.UTF_8)` và `Properties.load(Reader)`. |
| `IOException` khi resource có thật | Lỗi I/O khi mở/đọc archive/network hoặc nguồn loader | Giữ cause, phân biệt với missing, kiểm tra artifact và nguồn. |
| Resource ở plugin JAR nhưng host không thấy | Host loader không nhìn được JAR ở child plugin loader | Dùng plugin-defined class/loader hoặc thiết kế bridge phù hợp. |
| Đọc nhầm cấu hình ở JAR khác | Nhiều resources cùng tên và lookup chọn bản khác | Dùng `getResources`/`getResource` để debug, namespace và thứ tự ưu tiên rõ. |
| Trong named module tìm class được nhưng resource không đọc được | Module resource encapsulation | Kiểm tra package `opens` và chọn `Class`/`Module` resource API phù hợp. |
| `FileNotFoundException` khi dùng `Files.newInputStream` nhưng lookup resource khác kết quả | File-system path ≠ classpath resource name | Chọn đúng API theo nơi lưu resource. |
| Resource tồn tại nhưng nội dung rỗng | Entry có 0 byte | Tách tồn tại (non-null stream) khỏi content validation. |
| Memory tăng khi đọc file lớn | `readAllBytes` nạp toàn bộ content vào RAM | Stream theo buffer/line, giới hạn dung lượng và size check. |

### Một quy trình debug ngắn nhưng đủ thông tin

1. Ghi **resource name thực tế**, có/không có leading slash.
2. Ghi **API được dùng**: `Class.getResourceAsStream` hay `ClassLoader.getResourceAsStream`; hai API không cùng quy tắc path.
3. Xác định **Class/ClassLoader/Module** được dùng để lookup. Với plugin, không mặc định dùng system loader.
4. Kiểm tra file **đã vào runtime artifact** với đúng case và directory chưa. Trên Linux, case khác nhau sẽ tạo tên entry khác nhau; JAR entries cũng có tên case-sensitive.
5. Dùng `getResource(name)` để xem URL được chọn, hoặc `getResources(name)` khi nghi trùng tên.
6. Nếu chạy named module, kiểm tra **module encapsulation** và package opens.
7. Nếu có stream mà vẫn lỗi, xem **charset, parser, nội dung rỗng, IOException, kích thước**.
8. Với resource bắt buộc, ném lỗi rõ ràng; với resource tùy chọn, áp fallback được thiết kế.

## 18. Gợi ý kiểm thử tự động

Đối với module có tài nguyên quan trọng, nên kiểm tra:

- **Unit test đọc resource bắt buộc**: nội dung parse được, có keys bắt buộc, giá trị đúng schema.
- **Test resource thiếu**: phương thức wrapper ném exception mong muốn, thông điệp có resource name; không trả chuỗi trống mơ hồ.
- **Test resource tùy chọn**: fallback chỉ dùng khi missing; lỗi parse/I/O không bị coi là missing.
- **Test UTF-8**: nội dung có dấu tiếng Việt hoặc ký tự ngoài ASCII vẫn đọc chính xác.
- **Test package-relative versus root**: đường dẫn được chuẩn hóa đúng; slash đầu xử lý phù hợp API.
- **Integration test chạy từ JAR cuối cùng**: kiểm tra file thật sự được đóng gói và loader thấy tài nguyên.
- **Test nhiều loader/plugin** nếu kiến trúc hỗ trợ: resource plugin được lookup bởi loader phù hợp, không bị bản ở parent che khuất không mong muốn.
- **Test named module** nếu dùng JPMS: package opens/encapsulation và các resource API hoạt động trong môi trường production giống test.

### Mẫu assertion độc lập build tool

Ví dụ kiểm tra một resource bắt buộc trên classpath:

```java
static void assertResourcePresent(Class<?> anchor, String absoluteName) {
    try (InputStream input = anchor.getResourceAsStream(absoluteName)) {
        if (input == null) {
            throw new AssertionError(
                    "Missing packaged resource: " + absoluteName);
        }
    } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
    }
}
```

Đây là **method minh họa**, cần import `java.io.InputStream`, đặt trong class và gọi với `absoluteName` bắt đầu bằng `/` khi muốn root. Dùng `try-with-resources` kể cả trong assertion để tránh resource leak. Nếu resource cần kiểm chứng nội dung, đọc/parse sau khi xác nhận non-null.

Không chỉ test với IDE source roots. Integration test nên dùng **distribution artifact cuối cùng** vì lỗi thường nằm ở khâu đóng gói.

## 19. Những ngộ nhận cần tránh

- **"Không tìm thấy resource thì `getResourceAsStream` ném `FileNotFoundException`."** Sai: API trả `null`; caller tự chọn xử lý.
- **"Thêm `/` ở đầu resource name luôn đúng."** Sai: `Class` hỗ trợ absolute resource name có slash; `ClassLoader` thường yêu cầu tên **không slash đầu**.
- **"Mọi resource path đều tính từ working directory."** Sai: classpath/module lookup khác `Path.of(...)` theo filesystem.
- **"Thấy tệp trong `src/main/resources` thì JVM luôn đọc được."** Sai: build/deploy phải đóng gói nó vào runtime classpath/module image.
- **"`Class.forName` nạp class rồi thì resource ở cạnh class chắc chắn cũng nạp theo."** Sai: class loading và resource lookup là hai API và hai hành vi.
- **"Resource trong JAR phải giải nén ra file vật lý mới dùng được."** Sai: `getResourceAsStream` đọc trực tiếp archive entry qua loader hỗ trợ.
- **"Nếu stream khác null thì dữ liệu chắc chắn hợp lệ."** Sai: tệp có thể rỗng, lỗi I/O hoặc parse.
- **"Có thể bỏ `close()` vì tài nguyên đi cùng ứng dụng."** Sai: stream vẫn cần đóng để giải phóng handles.
- **"`Properties.load(InputStream)` mặc định dùng UTF-8."** Sai: API truyền thống dùng ISO-8859-1; với UTF-8 hãy dùng Reader.
- **"Một resource trùng tên ở nhiều JAR luôn được hợp nhất."** Sai: `getResourceAsStream` thường trả một kết quả; muốn liệt kê dùng `getResources`.
- **"Thay TCCL sẽ khiến mọi `SomeClass.class.getResource...` dùng loader mới."** Sai: API trên `Class` dùng ngữ cảnh class/module tương ứng.
- **"Nếu resource tồn tại thì JPMS không thể chặn lookup."** Sai: named module encapsulation có thể ảnh hưởng.
- **"Không tìm thấy nên cứ trả rỗng và tiếp tục."** Sai với resource bắt buộc: như vậy che lỗi build, deploy và cấu hình.

## 20. Câu hỏi tự kiểm tra

**1. Khi `loader.getResourceAsStream("config/missing.properties")` không tìm thấy, kết quả thông thường là gì?** `null`, không phải `FileNotFoundException` do method trực tiếp ném.

**2. `Demo.class.getResourceAsStream("sample.txt")` tìm ở đâu nếu `Demo` thuộc package `demo`?** Ở `demo/sample.txt` trong namespace resource mà class/module nhìn thấy.

**3. Muốn tìm `sample.txt` ở root bằng API trên `Class` thì viết gì?** `Demo.class.getResourceAsStream("/sample.txt")`.

**4. Muốn tìm cùng entry ở root bằng `ClassLoader` thì viết gì?** `loader.getResourceAsStream("sample.txt")`, không có slash đầu.

**5. Vì sao file tồn tại trong repository nhưng resource lookup vẫn null?** File có thể chưa được copy/đóng gói đúng vào runtime classpath/module artifact, hoặc loader/module không nhìn thấy.

**6. Tại sao bắt buộc kiểm tra null trước khi tạo `InputStreamReader`?** Vì lookup không tìm được sẽ trả `null` và Reader không thể đọc từ một stream null.

**7. Tại sao dùng `try-with-resources`?** Đảm bảo đóng stream khi đọc xong hoặc khi có exception, giảm rò file/JAR handles.

**8. Muốn đọc `.properties` chứa ký tự tiếng Việt UTF-8 bằng Java thì làm thế nào?** Tạo `InputStreamReader(stream, StandardCharsets.UTF_8)` rồi gọi `Properties.load(Reader)`.

**9. Vì sao `new File(classUrl.getFile())` không đáng tin cho resource trong JAR?** Vì URL có thể là `jar:...!...` hoặc bị percent-encoding, không tương ứng đường dẫn file vật lý.

**10. Một plugin có resource riêng nhưng system loader không thấy; sửa thế nào?** Tra bằng loader/class/module gắn với plugin hoặc thiết kế loader graph phù hợp, không chỉ đổi filename.

**11. Khi hai JAR có cùng resource name, API nào giúp kiểm tra mọi candidate?** `ClassLoader.getResources(name)` trả enumeration các URL có thể nhìn thấy.

**12. Resource tồn tại nhưng stream có 0 byte: có phải missing không?** Không. Non-null stream là dấu hiệu resource được mở; cần validate nội dung riêng.

**13. Trong named module, điều gì ngoài path có thể ngăn đọc resource?** Encapsulation, package `opens` và quy tắc của API `ClassLoader`/`Class`/`Module` được sử dụng.

**14. Resource tùy chọn nên xử lý lỗi I/O khi đọc như thế nào?** Không đồng nhất IOException với resource vắng mặt; báo lỗi hoặc áp chính sách xử lý rõ ràng.

## 21. Tài liệu chuẩn

- [Java SE 17 — `ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `getResource`, `getResourceAsStream`, `getResources`, parent delegation và resource/module encapsulation.
- [Java SE 17 — `Class` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getResource` và `getResourceAsStream`, tên tuyệt đối bắt đầu bằng `/` và tên tương đối với package.
- [Java SE 17 — `Module` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): `getResourceAsStream` trong module, điều kiện truy cập qua encapsulation.
- [Java SE 17 — `InputStream` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): quản lý stream, `readAllBytes`, `transferTo` và `close`.
- [Java SE 17 — `Properties` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html): khác biệt `load(InputStream)` so với `load(Reader)` và mã hóa ký tự.
- [JDK 17 — `jar` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html): tạo và liệt kê nội dung JAR, xác thực resource đã được đóng gói.

**Ghi nhớ:** **`getResourceAsStream` tìm resource qua loader/module và trả stream hoặc `null`; nó không tìm trong thư mục source theo phép màu, cũng không tự ném exception khi thiếu.** Hãy chọn đúng cách diễn giải đường dẫn (`Class` tương đối với package, `ClassLoader` từ resource root), xác minh artifact runtime, kiểm tra null, đọc với charset/format thích hợp, đóng stream và phân biệt **không tồn tại** với **đọc/parse thất bại**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-005">getResources, resources và liệt kê nhiều tài nguyên cùng tên</a>

<details>
<summary>Xem chi tiết</summary>

# getResources, resources và liệt kê nhiều tài nguyên cùng tên

## 1. Khi nào cần liệt kê thay vì tìm một resource?

Một Java application có thể dùng nhiều JAR. Hai hoặc nhiều JAR hoàn toàn có thể chứa **resource cùng tên**, chẳng hạn:

~~~text
parent.jar
└── demo/resource/lesson-source.txt     → PARENT

a.jar
└── demo/resource/lesson-source.txt     → A

b.jar
└── demo/resource/lesson-source.txt     → B
~~~

Nếu chỉ gọi `ClassLoader.getResource(name)`, thường nhận được **một URL đầu tiên** theo đường tìm kiếm của ClassLoader. Nhưng một plugin manager, bộ đọc metadata, hệ thống service discovery hoặc công cụ kiểm tra cấu hình có thể cần **mọi** URL khớp với cùng tên. Khi đó hai API đáng chú ý là:

~~~java
Enumeration<URL> all = loader.getResources(name);
Stream<URL> stream = loader.resources(name);
~~~

Bài học tập trung vào sự khác nhau giữa **tìm một resource** và **liệt kê mọi resource nhìn thấy được**, thứ tự parent/child, cách duyệt `Enumeration` so với `Stream`, và những trường hợp dễ nhầm khi resource trùng tên.

**Phân biệt ngay từ đầu:** Resource thường là dữ liệu như file văn bản, cấu hình, ảnh, `META-INF/services/...`; tên resource không phải tên binary class. `getResources` không phải lệnh đọc tất cả class của một JAR và không liệt kê toàn bộ các file trong JAR khi bạn chỉ cung cấp một tên resource.

## 2. Bốn API thường gặp

Với một đối tượng `ClassLoader loader` và tên tài nguyên `"demo/resource/lesson-source.txt"`:

| API | Kết quả | Ý nghĩa |
|---|---|---|
| `loader.getResource(name)` | `URL` hoặc `null` | Lấy **một** resource đầu tiên theo quy tắc tìm |
| `loader.getResourceAsStream(name)` | `InputStream` hoặc `null` | Mở stream cho resource đầu tiên; caller phải đóng |
| `loader.getResources(name)` | `Enumeration<URL>` | Liệt kê **các URL khớp** mà loader nhìn thấy |
| `loader.resources(name)` | `Stream<URL>` | Truy cập các URL khớp qua Java Stream API (từ Java 9) |

Cả `getResources` và `resources` làm việc với **URL**, không trực tiếp trả byte nội dung. Muốn đọc nội dung thì mở `URL.openStream()` hoặc một API phù hợp khác, rồi đóng stream sau khi xử lý.

Một ví dụ về hình thức API; mã này chỉ để minh họa, chưa phải chương trình chạy độc lập:

~~~java
String name = "demo/resource/lesson-source.txt";
ClassLoader loader = Thread.currentThread().getContextClassLoader();

URL first = loader.getResource(name);
Enumeration<URL> urls = loader.getResources(name);
Stream<URL> stream = loader.resources(name);
~~~

`getResources` có thể ném `IOException`; các I/O lỗi phát sinh khi tiêu thụ `resources` có thể được bọc trong `UncheckedIOException`. Trong ứng dụng thực tế, cũng cần xử lý trường hợp `loader == null` nếu lấy loader từ context hoặc `SomeClass.class.getClassLoader()`.

## 3. Cơ chế tìm theo ClassLoader

Đối với `ClassLoader` tuân **parent-first delegation**, thứ tự tìm thường là:

~~~text
child.getResources("demo/resource/lesson-source.txt")
                   │
                   ▼
            Hỏi parent trước
                   │
           PARENT (nếu có)
                   │
                   ▼
        Duyệt nguồn riêng của child
                   │
              A rồi B
                   │
                   ▼
         Enumeration/Stream URL
           [PARENT, A, B]
~~~

Ví dụ ta tạo parent `URLClassLoader` chứa `parent.jar`. Child cũng là `URLClassLoader` nhưng chứa hai URL theo thứ tự `[a.jar, b.jar]`.

- `child.getResource(name)` tìm **PARENT** trước, trả URL trong `parent.jar`.
- `child.getResources(name)` liệt kê **PARENT, A, B** trong thứ tự của cấu hình thí nghiệm.
- `child.resources(name)` biểu diễn các URL ấy bằng `Stream<URL>`.
- `parent.getResources(name)` không tự duyệt tài nguyên riêng của child, nên trong ví dụ chỉ có **PARENT**.
- Một loader khác không có `parent.jar` trong chuỗi tìm, nhưng có `[a.jar,b.jar]`, sẽ liệt kê **A, B**.

Các quy tắc này giải thích **ví dụ dùng URLClassLoader mặc định**. Một ClassLoader tùy biến có thể override `getResource` hoặc `getResources`, hoặc cung cấp resource từ nguồn không phải JAR. Quy tắc resource của named modules và ảnh hưởng của `opens` cũng phải xét riêng.

## 4. Ví dụ Java 17+ đầy đủ với ba JAR tài nguyên

Ví dụ tạo ba JAR chỉ chứa resource text; host chủ động tạo loader cha và con. Ta chọn một đường resource mang tính riêng cho bài học để tránh trùng ngoài ý muốn với tài nguyên JDK hoặc host.

### 4.1. Cấu trúc thư mục

~~~text
resources-demo/
├── src/
│   └── demo/host/
│       └── ResourceDemo.java
└── resource-src/
    ├── parent/
    │   └── demo/resource/lesson-source.txt
    ├── a/
    │   └── demo/resource/lesson-source.txt
    └── b/
        └── demo/resource/lesson-source.txt
~~~

Các tệp `lesson-source.txt` chứa đúng nhãn tương ứng, mỗi file một dòng:

| Đường dẫn | Nội dung |
|---|---|
| `resource-src/parent/demo/resource/lesson-source.txt` | `PARENT` |
| `resource-src/a/demo/resource/lesson-source.txt` | `A` |
| `resource-src/b/demo/resource/lesson-source.txt` | `B` |

Dùng văn bản UTF-8 không BOM; dấu xuống dòng cuối file có thể có hoặc không vì chương trình sử dụng `strip()`.

### 4.2. File Java chạy được

Tạo file **`src/demo/host/ResourceDemo.java`**:

~~~java
package demo.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ResourceDemo {
    private static final String NAME = "demo/resource/lesson-source.txt";

    private ResourceDemo() {
    }

    private static String content(URL url) throws IOException {
        if (url == null) {
            return "(missing)";
        }
        try (InputStream stream = url.openStream()) {
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    private static String content(InputStream input) throws IOException {
        if (input == null) {
            return "(missing)";
        }
        try (InputStream stream = input) {
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    private static String all(Enumeration<URL> urls) throws IOException {
        List<String> names = new ArrayList<>();
        while (urls.hasMoreElements()) {
            names.add(content(urls.nextElement()));
        }
        return String.join(" > ", names);
    }

    private static String all(Stream<URL> urls) {
        try (urls) {
            return urls.map(url -> {
                try {
                    return content(url);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }).collect(Collectors.joining(" > "));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: ResourceDemo <parent.jar> <a.jar> <b.jar>");
        }

        Path parentJar = Path.of(args[0]).toAbsolutePath().normalize();
        Path aJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path bJar = Path.of(args[2]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(parentJar)
                || !Files.isRegularFile(aJar)
                || !Files.isRegularFile(bJar)) {
            throw new IllegalArgumentException("All three JARs must exist");
        }

        URL parentUrl = parentJar.toUri().toURL();
        URL aUrl = aJar.toUri().toURL();
        URL bUrl = bJar.toUri().toURL();

        // Parent chứa parent.jar; child chứa a.jar, b.jar.
        // Platform loader không có file demo/resource/lesson-source.txt.
        try (URLClassLoader parent = new URLClassLoader(
                     new URL[] {parentUrl},
                     ClassLoader.getPlatformClassLoader());
             URLClassLoader child = new URLClassLoader(
                     new URL[] {aUrl, bUrl}, parent);
             URLClassLoader noParentJar = new URLClassLoader(
                     new URL[] {aUrl, bUrl},
                     ClassLoader.getPlatformClassLoader())) {

            System.out.println("getResource: "
                    + content(child.getResource(NAME)));
            System.out.println("getResourceAsStream: "
                    + content(child.getResourceAsStream(NAME)));

            System.out.println("getResources: "
                    + all(child.getResources(NAME)));
            System.out.println("resources: "
                    + all(child.resources(NAME)));

            System.out.println("parent getResources: "
                    + all(parent.getResources(NAME)));
            System.out.println("without parent.jar: "
                    + all(noParentJar.getResources(NAME)));

            System.out.println("missing single resource: "
                    + (child.getResource("demo/resource/not-found.txt")
                    == null));
        }
    }
}
~~~

**Các chi tiết có chủ đích:**

- Ta chọn `ClassLoader.getPlatformClassLoader()` làm tổ tiên cho loader cha và loader `noParentJar`. Điều này tránh vô tình tìm resource trong classpath ứng dụng host ở phép thử. Tên tài nguyên cũng được chọn riêng để chắc chắn không nằm trong các JDK modules thông thường.
- Loader `child` có parent chính là URLClassLoader giữ `parent.jar`; đường tìm kiếm riêng của child là `a.jar` rồi `b.jar`.
- Hai overload `content(URL)` và `content(InputStream)` đều đóng stream. Trường hợp resource không tồn tại được biểu diễn rõ là `"(missing)"`.
- `all(Enumeration<URL>)` sử dụng `hasMoreElements()`/`nextElement()` theo thứ tự enumerate.
- `all(Stream<URL>)` dùng Java Stream API; lỗi đọc URL bên trong lambda được chuyển từ `IOException` checked sang `UncheckedIOException` để không nuốt lỗi.
- Chương trình in **nội dung từng resource**, tránh in `jar:file:/...` có đường dẫn tuyệt đối phụ thuộc máy.

### 4.3. Biên dịch và đóng JAR

Chạy lệnh từ thư mục `resources-demo/`, sau khi tạo các file ở trên.

**Bash (Linux, macOS, Git Bash):**

~~~bash
mkdir -p build/classes dist

javac -encoding UTF-8 \
  -d build/classes src/demo/host/ResourceDemo.java

jar --create --file dist/parent.jar -C resource-src/parent .
jar --create --file dist/a.jar -C resource-src/a .
jar --create --file dist/b.jar -C resource-src/b .

java -cp build/classes \
  demo.host.ResourceDemo dist/parent.jar dist/a.jar dist/b.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/classes,dist | Out-Null

javac -encoding UTF-8 -d build/classes src/demo/host/ResourceDemo.java

jar --create --file dist/parent.jar -C resource-src/parent .
jar --create --file dist/a.jar -C resource-src/a .
jar --create --file dist/b.jar -C resource-src/b .

java -cp build/classes demo.host.ResourceDemo dist/parent.jar dist/a.jar dist/b.jar
~~~

Chú ý: **không** đưa ba resource JAR lên `-cp` của lệnh chạy host; chương trình sẽ tải chúng qua các `URLClassLoader` mà nó tự tạo. Nếu vô tình đặt `parent.jar` hay `a.jar` trên application classpath, mô hình tìm resource trong ví dụ có thể khác dự kiến.

Kiểm tra JAR nếu cần:

~~~bash
jar --list --file dist/parent.jar
jar --list --file dist/a.jar
jar --list --file dist/b.jar
~~~

Cả ba JAR phải có entry chính xác `demo/resource/lesson-source.txt`. Output của `jar --list` có thể chứa thêm `META-INF/MANIFEST.MF` và các entry thư mục.

### 4.4. Stdout chính xác

Với đúng nội dung 3 file text và lệnh chạy đã cho, kết quả là:

~~~text
getResource: PARENT
getResourceAsStream: PARENT
getResources: PARENT > A > B
resources: PARENT > A > B
parent getResources: PARENT
without parent.jar: A > B
missing single resource: true
~~~

**Giải thích từng dòng:**

1. `getResource` lấy resource đầu tiên trong thứ tự parent-first, vì vậy là `PARENT`.
2. `getResourceAsStream` mở resource đầu tiên ấy, nên cũng đọc ra `PARENT`.
3. `getResources` kết hợp resource từ parent và nguồn URL của child theo thứ tự dự kiến: `PARENT > A > B`.
4. `resources` trả một Stream của các URL khớp với cùng tên; khi tiêu thụ bằng `collect`, ví dụ cho ra cùng thứ tự `PARENT > A > B`.
5. `parent.getResources` chỉ thấy `parent.jar` và tổ tiên của nó; không tìm ngược trong JAR của child nên chỉ có `PARENT`.
6. `noParentJar` trỏ tới A rồi B, parent của nó là platform loader, nên nó không thấy `parent.jar` và cho `A > B`.
7. `getResource` trả `null` với tên không tồn tại.

Đây là **một cấu hình được kiểm soát với URLClassLoader chuẩn**, không phải cam kết mọi ClassLoader đều sắp xếp resource theo cách tương tự.

## 5. `getResources` và `resources` khác nhau ra sao?

### 5.1. `Enumeration<URL>` của `getResources`

~~~java
Enumeration<URL> urls = loader.getResources("example/config.properties");
while (urls.hasMoreElements()) {
    URL url = urls.nextElement();
    // Xử lý từng URL
}
~~~

Đây là API lâu đời, phù hợp thư viện Java cũ và những nơi muốn duyệt lần lượt với `while`. `getResources` khai báo `throws IOException`. `Enumeration` cung cấp thứ tự phần tử từ loader, nhưng bản thân interface Enumeration không tự áp đặt thứ tự cụ thể cho mọi loại nguồn.

**Không tạo giả định “Enumeration nghĩa là không có duplicate”.** Nếu nhiều JAR khác nhau chứa tên resource được hỏi, kết quả có thể có nhiều URL. Nếu cùng resource được xuất hiện qua nhiều đường tìm kiếm, việc phân biệt/trùng lặp kết quả phụ thuộc implementation; caller cần tự xử lý duplicate theo yêu cầu nghiệp vụ.

### 5.2. `Stream<URL>` của `resources`

Từ Java 9, `ClassLoader.resources(String)` trả một `Stream<URL>` để dùng các phép biến đổi/lọc/tập hợp:

~~~java
try (Stream<URL> urls = loader.resources("example/config.properties")) {
    List<URL> matches = urls.toList(); // Java 16+
    System.out.println("Count: " + matches.size());
}
~~~

Đoạn này **chỉ minh họa** khi đã có `loader` và các import thích hợp. Trên Java 17+, `Stream.toList()` khả dụng. Khi dùng Stream:

- Stream thường được xử lý **lười (lazy)**: URL được duyệt khi có terminal operation như `toList()` hoặc `collect(...)`.
- Stream **dùng một lần**; đã tiêu thụ thì không gọi terminal operation thứ hai trên chính Stream ấy.
- Không chạy `parallel()` nếu thứ tự là một phần logic ưu tiên và bạn chưa kiểm chứng contract của collector/nguồn.
- Nếu việc duyệt resource dẫn tới I/O error, theo tài liệu `resources` lỗi có thể xuất hiện dưới dạng `UncheckedIOException` trong khi tiêu thụ Stream.
- `try-with-resources` cho Stream là thực hành rõ ràng khi viết thư viện tổng quát, đặc biệt nếu pipeline có `onClose` handlers. **Không suy diễn** rằng đóng Stream có thể đóng được mọi `InputStream` do bạn đã chủ động mở trong `map`; các input stream đó phải được đóng riêng.

`resources` **không chuyển nội dung của resource thành Stream text lines**. Nó là Stream của **URL**, mỗi URL vẫn cần được đọc riêng khi muốn xử lý dữ liệu.

### 5.3. Chọn API nào?

| Nhu cầu | API nên dùng |
|---|---|
| Một file cấu hình đầu tiên theo loader | `getResource` |
| Một stream để đọc file đầu tiên | `getResourceAsStream` |
| Liệt kê đầy đủ các URL trùng tên và xử lý tuần tự | `getResources` |
| Lọc/sắp xếp/chuyển đổi các URL bằng Stream API | `resources` |
| Nạp service provider từ nhiều JAR | `ServiceLoader`, vì còn phải xử lý định dạng descriptor và hợp đồng service |
| Liệt kê tất cả mục trong một JAR | `JarFile`/ZIP API hoặc hệ thống đọc JAR, **không** phải `getResources(name)` |

## 6. Thứ tự liệt kê quan trọng đến mức nào?

Khi một loader con mặc định ủy quyền cho parent, các resource mà parent thấy được thường xuất hiện trước. Đối với `URLClassLoader` chuẩn, các URL của chính nó được tìm theo thứ tự đường tìm kiếm, ví dụ `[a.jar, b.jar]`. Nhưng:

- ClassLoader con tùy chỉnh có thể đổi cả `getResource` và `getResources`; nếu override một phương thức mà bỏ qua phương thức kia, hành vi của API đơn resource và nhiều resource có thể không tương thích.
- Nếu resource thuộc **named module**, quy tắc đóng gói module và khả năng mở package có thể khiến resource không hiển thị như trong ví dụ classpath.
- Nếu thêm JAR bằng classpath wildcard `lib/*`, thứ tự mở rộng các JAR không nhất thiết ổn định. Không thiết kế độ ưu tiên cấu hình dựa trên tên file alphabet.
- JAR manifest `Class-Path`, loader của framework, hoặc classpath được xây dựng bởi tool có thể thêm nguồn resource vào đường tìm kiếm.
- Nếu cần thứ tự nghiệp vụ ổn định, tốt hơn là định nghĩa **quy tắc ưu tiên rõ ràng** trong ứng dụng (ví dụ config version, priority field, thứ tự plugin) rồi áp dụng sau khi thu thập URL, thay vì trông chờ thứ tự ngẫu nhiên của môi trường.

**Không được đánh đồng thứ tự resource với class identity.** Với class cùng tên, một loader thường chọn một class được định nghĩa tương ứng. Với resource trùng tên, `getResources` có thể trả **nhiều URL** để caller tự quyết định gộp, ưu tiên hoặc xử lý toàn bộ.

## 7. Cùng resource name nhưng nhiều URL: cách quyết định xử lý

Nếu kết quả `getResources(name)` là `[PARENT, A, B]`, ứng dụng có thể chọn ba chiến lược khác nhau:

**Chọn bản đầu tiên:** Dùng `getResource` khi ứng dụng có quy tắc “parent hoặc nguồn đứng trước được ưu tiên”. Không cần enumerate nhiều file nếu chỉ muốn một resource.

**Gộp các cấu hình:** Duyệt `getResources` hoặc `resources`, parse mỗi file và gộp theo quy tắc xác định, ví dụ “lấy bản sau ghi đè key trước” hoặc “trùng key là lỗi”. Phải nêu thứ tự gộp rõ ràng và bảo đảm ổn định theo yêu cầu nghiệp vụ.

**Xử lý tất cả:** Đối với metadata dịch vụ, file index hoặc rule fragments, có thể cần từng resource. Trùng file không có nghĩa nên bỏ qua: mỗi JAR có thể cung cấp một khai báo độc lập.

Ví dụ mẫu gộp theo thứ tự gặp các URL (mã minh họa, không phải chương trình chạy độc lập):

~~~java
Enumeration<URL> urls = loader.getResources("features.properties");
while (urls.hasMoreElements()) {
    URL url = urls.nextElement();
    try (InputStream in = url.openStream()) {
        // Parse file này rồi merge theo quy tắc của ứng dụng.
    }
}
~~~

Nếu file có thể đến từ code hoặc thư viện không tin cậy, không tự động thực thi nội dung hay mở URL ngoài phạm vi cần thiết. URL là vị trí truy cập resource, không phải chứng nhận rằng nội dung đúng hoặc an toàn.

## 8. Resource name, đường dẫn tuyệt đối và tương đối

### 8.1. `ClassLoader.getResource` không tự thêm package

Với `ClassLoader`, thường dùng tên resource theo **đường dẫn từ root** và **không có dấu `/` đầu**:

~~~java
loader.getResource("demo/resource/lesson-source.txt");
~~~

Nếu bạn truyền `"/demo/resource/lesson-source.txt"` cho `ClassLoader.getResource`, việc tìm có thể thất bại vì dấu slash đầu được xem là một phần của tên resource. Hãy dùng path từ root như trong JAR.

### 8.2. `Class.getResource` có quy tắc khác

Với `Class.getResource`:

~~~java
// Ví dụ minh họa với ResourceDemo đã được định nghĩa.
ResourceDemo.class.getResource("local.txt");
ResourceDemo.class.getResource("/demo/resource/lesson-source.txt");
~~~

Tên **không bắt đầu bằng `/`** được diễn giải tương đối với package của class; tên **bắt đầu bằng `/`** được diễn giải từ root. Tuy nhiên, `ResourceDemo.class` thuộc **system loader** và không tự nhìn thấy JAR do child loader tạo trong ví dụ. Vì vậy chỉ sửa tên sang dạng tuyệt đối **không tự làm resource riêng của child trở nên nhìn thấy được**.

Nếu mục tiêu là JAR plugin, hãy tra cứu bằng đúng loader của plugin, ví dụ `child.getResources(name)`, thay vì mặc định dùng class loader của host.

### 8.3. Tên resource không phải tên lớp

Tệp `demo/resource/lesson-source.txt` khác với tên `demo.resource.lesson-source`. Resource sử dụng đường dẫn dấu slash; một class có tên `demo.plugin.Greeter` thường có resource bytecode tương ứng là `demo/plugin/Greeter.class`, nhưng việc truy xuất bytecode qua resource API có thể chịu ràng buộc module/loader và không phải phương pháp tổng quát để “liệt kê class”.

## 9. ServiceLoader: trường hợp thực tế cần nhiều resource cùng tên

Trong mô hình classpath, Java SPI dùng các tệp provider configuration nằm tại:

~~~text
META-INF/services/<tên đầy đủ của interface dịch vụ>
~~~

Ví dụ với `example.api.PaymentProvider`, hai plugin JAR có thể đều có:

~~~text
META-INF/services/example.api.PaymentProvider
~~~

JAR A khai báo `example.plugins.CardProvider`, JAR B khai báo `example.plugins.WalletProvider`. Khi `ServiceLoader` được cung cấp đúng loader nhìn thấy cả hai JAR, nó có thể đọc nhiều tệp descriptor cùng tên và tìm nhiều provider.

Điều quan trọng:

- `ServiceLoader` là API khám phá provider, hiểu định dạng descriptor, kiểm tra lớp/provider có phù hợp interface dịch vụ và khởi tạo đối tượng khi cần.
- `ClassLoader.getResources("META-INF/services/...")` chỉ trả **URL**. Nếu tự parse descriptor, bạn phải tự xử lý comment, encoding, cú pháp, thứ tự và class loader.
- Service interface phải được host và plugin hiểu như **cùng runtime type**; hai interface cùng tên nhưng do loader riêng định nghĩa có thể dẫn tới `ServiceConfigurationError`.
- Trong **JPMS named modules**, service binding dựa vào `uses` và `provides ... with ...` và có thể không cần descriptor classpath truyền thống. Không mô tả toàn bộ JPMS chỉ bằng `META-INF/services`.

Mã minh họa khi đã có `PaymentProvider` và `pluginLoader`:

~~~java
ServiceLoader<PaymentProvider> providers =
        ServiceLoader.load(PaymentProvider.class, pluginLoader);

for (PaymentProvider provider : providers) {
    // Sử dụng provider theo shared interface.
}
~~~

Đoạn này không phải chương trình độc lập: nó giả định interface, provider và loader đã được cấu hình. Đối với ví dụ chạy ở phần 4, ta chỉ dùng resource text để tập trung vào hành vi của API liệt kê.

## 10. Lưu ý JPMS và tính đóng gói của resource

Trên Java 9+, các lớp và resource có thể nằm trong **named modules**. Quy tắc resource lookup có khác biệt đáng kể so với các JAR nằm trên classpath (unnamed modules).

Một số non-class resource trong named module không được trả về khi package chứa resource **không được mở vô điều kiện**, tùy API ClassLoader đang dùng. Nói cách khác, chỉ vì file nằm trong module JAR không có nghĩa `loader.getResources(name)` nhìn thấy file ấy từ mọi ngữ cảnh.

Khi có nghi vấn:

1. Xác minh tài nguyên nằm trong JAR/module thực tế được runtime sử dụng.
2. Xác minh module và package chứa tài nguyên, các khai báo `opens` hoặc `open module` nếu cần.
3. Kiểm tra loader đã yêu cầu và cách framework xây dựng `ModuleLayer`.
4. Kiểm tra có dùng sai đường dẫn `ClassLoader` so với `Class.getResource`.
5. Không tự thêm `--add-opens` hàng loạt chỉ để che lỗi cấu hình; hiểu lý do module đang giới hạn truy cập.

Ví dụ phần 4 **không dùng named modules**, vì thế kết quả resource lookup không phụ thuộc vào khai báo module.

## 11. Thực hành an toàn khi liệt kê nhiều resource

### 11.1. Đóng stream nội dung đúng cách

`URL.openStream()` hoặc `getResourceAsStream` trả `InputStream` phải được đóng; dùng `try-with-resources`. Với nhiều URL, mở và đóng **lần lượt** thường có lợi hơn việc mở toàn bộ stream cùng lúc.

Không nên giữ các stream/JAR handle mở lâu khi plugin cần được thay thế. `URLClassLoader.close()` đóng các JAR handle của loader, nhưng không biến mọi `InputStream` còn đang dùng thành một đối tượng được quản lý vòng đời tự động.

### 11.2. Lỗi I/O không phải “không tìm thấy”

Phân biệt:

- `getResource(name) == null`: loader không tìm được URL theo cơ chế tra cứu.
- `getResources(name)` trả Enumeration rỗng: không có resource phù hợp mà loader có thể liệt kê.
- `IOException` trong lúc enumerate hoặc đọc URL: quá trình truy cập lỗi, không nhất thiết resource không tồn tại.
- `UncheckedIOException` trong xử lý Stream: lỗi I/O bị bọc khi làm việc qua Java Stream API.

Đừng viết `catch (IOException e) { return Collections.emptyList(); }` nếu lỗi I/O cần được báo; làm vậy sẽ âm thầm biến “đọc thất bại” thành “không có resource”.

### 11.3. Dedupe phải phù hợp yêu cầu

Nếu danh sách có cùng URL lặp lại, `distinct()` trên Stream URL hoặc một `Set<URL>` có thể hữu ích với trường hợp cụ thể, nhưng URL equality/normalization có các chi tiết phức tạp; trong một số môi trường, so sánh URL có thể gây hành vi không mong muốn. Khi cần dedupe an toàn, chọn khóa rõ ràng như tên plugin + relative resource path hoặc URI được chuẩn hóa theo chính sách đã kiểm soát.

Nếu hai JAR **khác nhau** cùng cung cấp một resource name nhưng nội dung khác, bỏ bớt chỉ vì tên giống nhau thường làm mất dữ liệu cần xử lý.

## 12. Các lỗi phổ biến

| Hiện tượng | Vì sao xảy ra | Cách xử lý |
|---|---|---|
| `getResource` chỉ trả PARENT dù A/B có cùng resource | Parent-first và chỉ trả URL đầu tiên | Dùng `getResources`/`resources` nếu cần tất cả |
| `getResources` không thấy file của plugin | Dùng system/host loader không thấy URL của plugin | Truyền loader plugin thích hợp |
| `ClassLoader.getResource("/a/b.txt")` trả null | Tên bị thêm dấu slash đầu | Dùng `"a/b.txt"` |
| `Class.getResource("x.txt")` không tìm đúng root | Tên tương đối với package class | Dùng `"/x.txt"` nếu thật sự muốn từ root và đúng loader |
| Chỉ xử lý một tệp `META-INF/services` | `getResource` chỉ có một kết quả | Dùng `ServiceLoader` hoặc enumerate descriptor đúng |
| `Stream has already been operated upon or closed` | Stream `resources` đã tiêu thụ một lần | Tạo stream mới khi cần duyệt lại |
| Kết quả lẫn nhiều JAR không theo thứ tự dự kiến | Parent, URL order, wildcard, framework loader | In danh sách URL, kiểm tra cấu hình loader và đường tìm |
| `IOException` khi mở resource | JAR/URL không đọc được hoặc stream đã lỗi | Báo lỗi thật, đóng tài nguyên đúng cách |
| Tệp nằm trong named module nhưng không nhìn thấy | Đóng gói module, package chưa được mở phù hợp | Xem module ownership và `opens` |
| Đóng loader làm lỗi đọc tài nguyên về sau | Resource lookup hoặc stream bị dùng sau khi loader đóng | Hoàn tất xử lý trong vòng đời loader còn hoạt động |

## 13. Tự kiểm tra

**Câu 1.** Nếu parent có một `settings.txt` và hai JAR child cũng có file cùng tên, `getResource` và `getResources` thường trả gì?

**Đáp án:** `getResource` trả URL đầu tiên của parent, còn `getResources` có thể enumerate cả parent và hai resource child, theo thứ tự parent-first của loader chuẩn.

**Câu 2.** `resources(name)` có trả về Stream các dòng văn bản trong file không?

**Đáp án:** Không. Đây là `Stream<URL>`. Muốn đọc text phải mở URL tương ứng và xử lý input stream.

**Câu 3.** `getResources(name)` có thể dùng như API liệt kê toàn bộ entry một JAR không?

**Đáp án:** Không. Nó tìm các tài nguyên có **tên cụ thể** mà ClassLoader nhìn thấy. Nếu cần liệt kê tất cả entry, dùng `JarFile` hoặc ZIP API.

**Câu 4.** Vì sao một framework dùng `ServiceLoader` có thể tìm nhiều provider dù các JAR đều chứa `META-INF/services/<service>`?

**Đáp án:** Nhiều descriptor cùng tên có thể được tìm qua loader; `ServiceLoader` có quy tắc đọc và xử lý provider từ những descriptor ấy.

**Câu 5.** Vì sao `ResourceDemo.class.getResource("/demo/resource/lesson-source.txt")` trong host không thay thế được `child.getResources("demo/resource/lesson-source.txt")`?

**Đáp án:** `Class.getResource` dùng loader/module gắn với class của host, không tự thấy tài nguyên riêng do child loader quản lý.

**Câu 6.** Đóng Java Stream trả về từ `resources` có tự đóng các `InputStream` mà code mở sau đó trong quá trình xử lý không?

**Đáp án:** Không nên giả định như vậy. Mỗi input stream do ứng dụng mở phải có cơ chế đóng riêng, thường là `try-with-resources` trong hàm đọc.

## 14. Tài liệu chính thức

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `getResource`, `getResourceAsStream`, `getResources` và `resources`.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): đường tìm URL và vòng đời loader.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider configuration, khám phá nhiều provider.
- [Java SE 17 — Class.getResource](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getResource(java.lang.String)): đường dẫn tương đối/absolute theo class.
- [Java SE 17 — Java Platform Module System](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): phạm vi module và quyền truy cập tài nguyên.

**Ghi nhớ:** `getResource` tìm một URL đầu tiên; `getResources` cung cấp `Enumeration<URL>` của nhiều tài nguyên cùng tên; `resources` cung cấp `Stream<URL>` (Java 9+). Kết quả phụ thuộc vào loader cụ thể, parent delegation, đường URL, và trong named modules còn có quy tắc module. Liệt kê URL không tự đọc hoặc gộp nội dung — chính ứng dụng phải quyết định cách xử lý và đóng stream đúng cách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-006">findResource, findResources và điểm mở rộng cho loader tùy chỉnh</a>

<details>
<summary>Xem chi tiết</summary>

# findResource, findResources và điểm mở rộng cho ClassLoader tùy chỉnh

## 1. Khi class nạp được nhưng tài nguyên vẫn “không tìm thấy”

Ứng dụng Java thường đóng gói cùng bytecode các tài nguyên như tệp `.properties`, mẫu HTML, JSON, ảnh, chứng chỉ công khai, metadata `META-INF/services/...` hoặc cấu hình plugin. Nạp được `com.example.Plugin` **không bảo đảm** tìm được `config/settings.properties`: cơ chế tìm **class** và cơ chế tìm **resource** có các API, đường tìm và quyền truy cập riêng.

Giả sử đoạn mã chạy:

```java
ClassLoader loader = Plugin.class.getClassLoader();
java.net.URL url = loader.getResource("plugin/settings.properties");
```

Đây là snippet minh họa, giả định `Plugin` đã được định nghĩa. Khi `url == null`, trước khi kết luận “quên đóng gói file”, cần kiểm tra **tên resource**, **loader được dùng**, **delegation**, liệu loader tùy chỉnh có cung cấp `findResource`/`findResources` hay không, và ranh giới của **named module**.

Bài này tập trung vào **hai điểm mở rộng**:

- `protected URL findResource(String name)`: tìm **một URL** tới resource tại nguồn mà class loader tùy chỉnh quản lý.
- `protected Enumeration<URL> findResources(String name) throws IOException`: tìm **toàn bộ URL** tương ứng trong nguồn cục bộ của loader, thường phục vụ việc gom nhiều file cấu hình hoặc provider descriptor.

Chúng kết hợp với các phương thức **public** `getResource`, `getResources`, `resources` và `getResourceAsStream`. Hiểu rõ sự khác nhau này là điều kiện để thiết kế loader plugin nhất quán.

## 2. Bản đồ API: người dùng gọi gì, loader override gì?

| API | Phạm vi/chức năng | Kết quả khi không tìm thấy | Có thể override ở custom loader? |
| --- | --- | --- | --- |
| `ClassLoader.getResource(name)` | Tra một resource bằng thuật toán delegation mặc định: parent trước, local sau | `null` | Có, nhưng thường **không cần** nếu chỉ muốn đổi nguồn local |
| `ClassLoader.getResources(name)` | Gom resource từ parent, sau đó từ `findResources` local | `Enumeration` rỗng | Có; phải bảo đảm thứ tự nhất quán với `getResource` |
| `ClassLoader.resources(name)` (Java 9+) | `Stream<URL>` từ cơ chế nhiều resource; thường lazy | Stream rỗng | Có nhưng không cần nếu `getResources` đã đúng |
| `ClassLoader.getResourceAsStream(name)` | Lấy `InputStream` để đọc resource đầu tiên | `null` | Có; thường không cần nếu `getResource` đúng |
| `protected findResource(name)` | Điểm mở rộng local để tìm **một URL** | `null` | **Có — nơi custom loader thường implement** |
| `protected findResources(name)` | Điểm mở rộng local để tìm **nhiều URL** | Enumeration rỗng | **Có — phải implement nếu cần nhiều resource local** |
| `ClassLoader.getSystemResource(name)` | Tìm resource thông qua system class loader | `null` | Phương thức static; không thay thế resource lookup của plugin |
| `Class.getResource(name)` | Tìm theo ngữ cảnh package của chính class (khác quy tắc tên dưới đây) | `null` | Đây là API của `Class`, không phải extension point của `ClassLoader` |
| `Module.getResourceAsStream(name)` | Tìm resource trong một module cụ thể, có kiểm tra đóng gói module | `null` | Không thay thế trách nhiệm của loader hỗ trợ module |

**Mặc định trong JDK 17:**

- `ClassLoader.findResource(String)` trả `null`.
- `ClassLoader.findResources(String)` trả enumeration **không có phần tử**.
- `ClassLoader.getResource(String)` tìm parent trước; chỉ khi parent không tìm thấy mới gọi `findResource(String)`.
- `ClassLoader.getResources(String)` gom danh sách từ parent trước rồi thêm các URL do `findResources(String)` local cung cấp.
- `ClassLoader.resources(String)` mặc định gọi `getResources` và biến enumeration thành stream; `IOException` xảy ra khi xử lý stream có thể được bọc trong `UncheckedIOException`.

**Hệ quả dễ bỏ sót:** override **chỉ** `findResource` có thể làm `getResource` tìm được file cục bộ, trong khi `getResources` vẫn **không nhìn thấy** file đó nếu `findResources` của lớp con chưa được override. Hai điểm mở rộng độc lập; không nên mặc định `findResources` của `ClassLoader` sẽ gọi `findResource` giúp bạn.

## 3. Luồng delegation: getResource và getResources không tương đương

Giả sử parent và child cùng chứa file `demo/config.properties`:

```text
Root / ParentLoader
    └── demo/config.properties   (version: parent)

Plugin / ChildLoader
    └── demo/config.properties   (version: child)
```

Với việc giữ nguyên public API mặc định:

```text
child.getResource("demo/config.properties")
    ├── parent.getResource(...)          -> tìm thấy ở parent
    └── child.findResource(...)          -> KHÔNG cần gọi
Kết quả: URL của parent
```

Ngược lại:

```text
child.getResources("demo/config.properties")
    ├── parent.getResources(...)         -> URL của parent
    └── child.findResources(...)         -> URL của child
Kết quả: Enumeration [parent URL, child URL]
```

Điều này có hai mục đích:

1. **`getResource` chọn một nguồn phù hợp** với chiến lược parent-first.
2. **`getResources` cho phép khám phá nhiều nguồn** cùng tên, chẳng hạn nhiều JAR mỗi JAR cung cấp file `META-INF/services/com.example.spi.Plugin`.

Thứ tự parent trước local là hợp đồng của implementation mặc định. Một framework có thể **override** `getResource`/`getResources` để áp dụng child-first theo namespace, nhưng khi làm thế phải duy trì **tính nhất quán**: resource đầu tiên từ `getResources(name)` nên giống resource mà `getResource(name)` chọn cho cùng tên khi cùng điều kiện truy cập.

**Lưu ý:** `getResources` là enumeration, không phải `List` đã gộp và khử trùng; cùng một nội dung có thể xuất hiện từ nhiều vị trí. Cần tự định nghĩa cách xử lý nhiều cấu hình trùng nhau, không mặc định “JVM chỉ trả bản mới nhất”.

## 4. Tên resource: dấu /, package, classpath root

Java xác định tên resource của `ClassLoader` bằng **đường dẫn có dấu `/`**, không phải binary name của class:

| Muốn tìm | Tên truyền cho `ClassLoader.getResource` |
| --- | --- |
| Config tại root classpath `application.properties` | `"application.properties"` |
| Config thuộc package `com.acme` | `"com/acme/settings.properties"` |
| Service descriptor | `"META-INF/services/com.acme.spi.Plugin"` |
| Class file trong nguồn classpath | `"com/acme/Plugin.class"` (nhưng tra file class không đồng nghĩa dùng API nạp class) |

Với **`ClassLoader.getResource`**, không thêm dấu `/` đầu tên: dùng `"com/acme/config.properties"`. Với **`Class.getResource`**, quy tắc khác:

- `SomeClass.class.getResource("config.properties")`: tìm tương đối **theo package của SomeClass**.
- `SomeClass.class.getResource("/com/acme/config.properties")`: tên tuyệt đối từ root; dấu `/` đầu bị loại trước khi chuyển sang tra cứu.
- `SomeClass.class.getResourceAsStream(...)` giữ quy tắc tên tương ứng.

Ví dụ minh họa trong class `com.acme.SomeClass`:

```java
java.net.URL viaClassRelative =
        SomeClass.class.getResource("config.properties");

java.net.URL viaClassAbsolute =
        SomeClass.class.getResource("/com/acme/config.properties");

java.net.URL viaLoader =
        SomeClass.class.getClassLoader()
                .getResource("com/acme/config.properties");
```

Đây là snippet minh họa, giả định class và file resource thật sự tồn tại. Với một **unnamed module**, ba cách này thường tra tới cùng tài nguyên ở cùng package nếu cấu hình classpath phù hợp; với **named module**, đường tra và quy tắc encapsulation có thể khác, cần xem phần JPMS.

**Không truyền** `"com.acme.config.properties"` nếu file thực tế có tên `com/acme/config.properties`. Không dùng `File.separator` thay `/` để đặt tên resource trong JAR.

### Trong JAR, resource không nhất thiết là file thường trên đĩa

Nếu resource nằm trong JAR, URL có thể có dạng `jar:file:/.../plugin.jar!/demo/config.properties`. Nếu nằm trên module image hoặc do loader tùy chỉnh cấp, nó có thể không phải `file:` hay `jar:file:`.

Vì vậy dùng `url.openStream()` hoặc `getResourceAsStream()` để đọc, và đóng stream bằng try-with-resources. Đừng tự ép `new File(url.toURI())` cho mọi loại URL vì đường `jar:` không phải đường file thường và URL có thể sử dụng giao thức đặc biệt.

## 5. Chương trình Java 17 hoàn chỉnh: hai loader, hai nguồn tài nguyên

Ví dụ này thực sự xây dựng **hai loader có quan hệ parent-child**, trong đó cả hai có tài nguyên trùng tên. Ta override **cả** `findResource` và `findResources` để kết quả đơn/nhiều cùng nhất quán. Không cần Maven, Gradle, JAR ngoài hay kết nối mạng.

Tạo file `ResourceLookupDemo.java`:

```java
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ResourceLookupDemo {

    private static final String CONFIG =
            "lesson_resource_demo_2026/config.txt";

    static final class DirectoryResources extends ClassLoader {
        private final Path root;

        DirectoryResources(Path directory, ClassLoader parent) {
            super(parent);
            this.root = directory.toAbsolutePath().normalize();
        }

        private Path localPath(String name) {
            // Bài thực hành chỉ dùng các tên resource kiểm soát được.
            if (name.startsWith("/")
                    || name.indexOf('\\') >= 0
                    || name.contains("..")) {
                return null;
            }
            Path candidate = root.resolve(name).normalize();
            return candidate.startsWith(root) ? candidate : null;
        }

        @Override
        protected URL findResource(String name) {
            Path file = localPath(name);
            if (file == null || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                return file.toUri().toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        }

        @Override
        protected Enumeration<URL> findResources(String name)
                throws IOException {
            URL local = findResource(name);
            if (local == null) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(local));
        }
    }

    private static String read(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("resource-lookup-");
        Path parentRoot = temp.resolve("parent");
        Path childRoot = temp.resolve("child");
        Path parentFile = parentRoot.resolve(CONFIG);
        Path childFile = childRoot.resolve(CONFIG);

        try {
            Files.createDirectories(parentFile.getParent());
            Files.createDirectories(childFile.getParent());
            Files.writeString(parentFile, "PARENT\n");
            Files.writeString(childFile, "CHILD\n");

            DirectoryResources parent = new DirectoryResources(
                    parentRoot,
                    ClassLoader.getPlatformClassLoader());
            DirectoryResources child = new DirectoryResources(
                    childRoot, parent);

            URL first = child.getResource(CONFIG);
            System.out.println("getResource=" + read(first));

            Enumeration<URL> urls = child.getResources(CONFIG);
            List<String> values = new ArrayList<>();
            while (urls.hasMoreElements()) {
                values.add(read(urls.nextElement()));
            }
            System.out.println("getResources=" + values);

            System.out.println("missing="
                    + (child.getResource(
                            "lesson_resource_demo_2026/missing.txt")
                            == null));
            System.out.println("leadingSlash="
                    + (child.getResource("/" + CONFIG) == null));
        } finally {
            Files.deleteIfExists(parentFile);
            Files.deleteIfExists(childFile);
            Files.deleteIfExists(parentFile.getParent());
            Files.deleteIfExists(childFile.getParent());
            Files.deleteIfExists(parentRoot);
            Files.deleteIfExists(childRoot);
            Files.deleteIfExists(temp);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ResourceLookupDemo.java
java ResourceLookupDemo
```

**Stdout chính xác trong điều kiện chạy chuẩn**, khi platform loader không có resource trùng tên của bài thực nghiệm:

```text
getResource=PARENT
getResources=[PARENT, CHILD]
missing=true
leadingSlash=true
```

**Cách đọc kết quả:**

1. `child.getResource(CONFIG)` hỏi parent trước. Parent có `CONFIG`, nên trả URL của parent: dòng `getResource=PARENT`.
2. `child.getResources(CONFIG)` gom enumeration từ parent và từ `findResources` của child; mỗi loader cung cấp một URL, nên thứ tự hiển thị là `[PARENT, CHILD]`.
3. Tên `missing.txt` không tồn tại ở platform/parent/child trong thí nghiệm, nên `getResource` trả `null`.
4. Tên resource bắt đầu bằng `/` không thuộc cách đặt tên mà `ClassLoader.getResource` mong đợi; `DirectoryResources.localPath` từ chối tên này, nên kết quả là `true`.
5. Mọi tài nguyên được đọc bằng `URL.openStream` và đóng bởi try-with-resources. Thư mục tạm cùng hai file đều được dọn ở `finally` sau khi không còn stream mở.

### Vì sao ví dụ dùng một method hỗ trợ localPath?

Resource có thể được đặt tên từ cấu hình, đường dẫn do người dùng nhập hoặc dữ liệu bên ngoài. Khi chuyển tên resource thành `Path`, cần tránh các tên dạng `../secret.txt` thoát khỏi root. Ví dụ áp dụng kiểm tra **lexical**: từ chối dấu `/` đầu, dấu `\\` và chuỗi `..`, sau đó chuẩn hóa và so sánh root.

**Giới hạn bảo mật cần hiểu đúng:** các kiểm tra lexical không đủ chống việc thư mục con trong root là **symbolic link** trỏ ra ngoài. Trong hệ thống thực sự xử lý tên resource không tin cậy, cần kiểm tra path thực tế, quyền truy cập, symlink policy, giới hạn file size và nguồn được phép trước khi đọc. Ví dụ hướng dẫn chỉ sử dụng file tự tạo và tên cố định.

### Nếu chỉ override findResource trong DirectoryResources?

`child.getResource(CONFIG)` vẫn có thể tìm parent, và một resource **chỉ ở child** có thể được tìm thấy bởi `findResource`. Nhưng `child.getResources(CONFIG)` **không tự gọi findResource để thu thập local URL**; nếu không override `findResources`, phiên bản mặc định cho phần local trả enumeration rỗng. Vì vậy mọi hệ thống cần liệt kê nhiều resource phải có triển khai `findResources` tương ứng.

## 6. Một hay nhiều tài nguyên: vì sao không nên “lấy đại URL đầu tiên”?

Có các trường hợp sử dụng khác nhau:

| Nhu cầu | Cách dùng gợi ý | Quy tắc xử lý |
| --- | --- | --- |
| Chỉ đọc một cấu hình với parent-first priority | `getResource` / `getResourceAsStream` | Xác định rõ nguồn nào có quyền ưu tiên |
| Liệt kê các descriptor cùng tên trong nhiều JAR | `getResources` | Duyệt toàn bộ URL, parse từng nguồn và xử lý lỗi riêng |
| Dùng API stream để lọc URL | `resources` | Có thể gặp `UncheckedIOException` khi đánh giá stream |
| Muốn ghi log mọi nơi cung cấp cấu hình | `getResources` | Không tự kết luận các URL trả về là duy nhất |
| Chỉ tìm nguồn local của loader tùy chỉnh | `findResource` / `findResources` trong subclass | Đây là protected extension point, không phải API public gọi từ khắp ứng dụng |

Ví dụ snippet hợp lệ khi đã có đối tượng `loader`:

```java
String name = "META-INF/services/com.acme.spi.Plugin";
Enumeration<URL> entries = loader.getResources(name);
while (entries.hasMoreElements()) {
    URL descriptor = entries.nextElement();
    try (InputStream in = descriptor.openStream()) {
        // Đọc đúng charset/định dạng của descriptor cần xử lý.
    }
}
```

Đây là snippet (chưa có `main`, giả định loader và các import), dùng để minh họa duyệt resource. Khi làm ServiceLoader thật, nên dùng `ServiceLoader` để framework xử lý giao thức provider descriptor hoặc khai báo `provides ... with ...` trong module, thay vì viết parser thủ công mà bỏ qua các quy tắc.

**Thứ tự không phải “độ mới phiên bản”.** Nếu nhiều JAR có `config.properties`, parent-first có thể trả file của parent trước dù child chứa bản mới hơn; nếu code chỉ lấy URL đầu tiên, bản của child sẽ không được sử dụng. Đây là chính sách của loader; cần thiết kế explicit nếu muốn ưu tiên plugin.

## 7. Khi cần child-first resource lookup

Một số hệ plugin cho phép thư viện của plugin dùng tài nguyên riêng dù parent có file trùng tên. Có thể chọn chính sách **child-first** cho một namespace resource cụ thể:

```text
getResource("plugin-private/config.yaml")
    -> tìm resource trong plugin trước
    -> nếu không có mới delegate lên parent
```

Tuy nhiên, override `getResource` mà **không** xử lý `getResources` cho nhất quán có thể tạo lỗi khó phát hiện:

- `getResource(name)` trả URL của child.
- `getResources(name)` tiếp tục trả parent trước.
- Một thư viện đọc resource đơn và một thư viện enumerate cùng tên nhận kết quả khác về ưu tiên.

Nếu có lý do thật sự cần child-first, thiết kế cả **hai API** để kết quả đầu của enumeration phản ánh cùng chính sách, kiểm tra cách tránh lặp URL và đảm bảo quyền truy cập vào parent khi phù hợp.

Ở nhiều ứng dụng, một biện pháp đơn giản hơn là đặt resource riêng vào namespace không xung đột, ví dụ `plugin-a/config.yaml` thay vì dùng chung `config.yaml` ở root. Điều này giảm sự phụ thuộc vào thứ tự JAR và delegation.

**Đừng thay đổi toàn bộ class loading sang child-first chỉ để sửa một resource.** Class loading ảnh hưởng danh tính kiểu, API/SPI và những package cần chia sẻ; hãy điều chỉnh resource lookup ở mức cần thiết.

## 8. Class resource và resource loader có thể khác nhau

Để lấy tài nguyên đi cùng một class, người ta thường chọn:

```java
InputStream stream =
        Plugin.class.getResourceAsStream("settings.properties");
```

Với class `Plugin` ở package `com.acme.plugin`, đây là cách tìm tương đối trong package ấy. Nếu code dùng:

```java
Plugin.class.getClassLoader()
        .getResourceAsStream("settings.properties");
```

nó lại tra resource ở **classpath root**. Sự khác biệt dấu `/` / package là nguyên nhân phổ biến dẫn đến `null`.

Hai yếu tố nữa:

1. `Class.getResource` lấy ngữ cảnh từ **class cụ thể** (và module của class đó, nếu là named module). Một thư viện ở loader cha không tự nhìn xuống mọi resource của plugin con.
2. `Thread.currentThread().getContextClassLoader()` (TCCL) là một loader được gắn trên thread, do code/framework quản lý. Một số API khám phá provider (như cách sử dụng `ServiceLoader` thông thường) có thể dựa vào TCCL, nhưng đổi TCCL **không làm thay đổi** defining loader của class đã nạp.

Khi giao tiếp host–plugin, ghi rõ nơi phải đặt descriptor `META-INF/services/...` và loader nào dùng để tìm. Nếu host chỉ dùng system loader mà resource nằm ở loader plugin, việc đóng gói đúng file vẫn chưa đủ.

Đặc biệt nếu code chạy trong thread pool, khi tạm đổi TCCL phải khôi phục trong `finally` để tránh để ngữ cảnh plugin tồn tại ngoài vòng đời tác vụ:

```java
ClassLoader old = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    // Gọi API discovery dùng context class loader nếu cần.
} finally {
    Thread.currentThread().setContextClassLoader(old);
}
```

Đây là đoạn minh họa, giả định `pluginLoader` đã được tạo; không phải chương trình độc lập.

## 9. JPMS: named modules có thêm quy tắc nhìn thấy tài nguyên

Từ Java 9, cùng một class loader có thể định nghĩa class từ **nhiều module**. Việc đọc resource trong named module chịu **module encapsulation** ngoài delegation thông thường.

### 9.1. ClassLoader.getResource(s) và yêu cầu opens

Theo Java SE 17 API, `ClassLoader.getResource`, `getResources` và `resources` bị giới hạn khi tài nguyên nằm trong package của **named module**:

- Ngoại lệ cho resource có tên kết thúc bằng `.class` theo quy tắc API.
- Với resource **không phải `.class`** trong package của named module, các API này thông thường chỉ trả resource khi package ấy được **opened unconditionally**.
- `exports` phục vụ việc truy cập API/type của module; không đồng nghĩa với việc `opens` tất cả tài nguyên để truy cập qua class loader.
- Một resource không nằm trong package của named module có cách áp dụng quy tắc khác; không nên áp đặt máy móc rằng mọi resource của mọi named module đều phải mở package.

Vì vậy, file rõ ràng có trong module JAR nhưng `loader.getResource("com/acme/internal/settings.json")` vẫn có thể trả `null` do package không được mở vô điều kiện.

### 9.2. Class.getResource và Module.getResourceAsStream

Khi code có class thuộc named module:

- `SomeClass.class.getResource(...)` sử dụng ngữ cảnh module của class và delegation module-aware tương ứng.
- `Module.getResourceAsStream(name)` dùng module đích và thực hiện kiểm tra quyền truy cập theo caller module; một package có thể được mở **có điều kiện** tới module gọi.
- Không suy ra rằng `ClassLoader.getResource` sẽ cho kết quả tương tự với `Module.getResourceAsStream` trong tất cả cấu hình JPMS.

Class loader hỗ trợ định nghĩa module có protected overload:

```java
protected URL findResource(String moduleName, String name)
        throws IOException
```

Đây là chữ ký API (không phải chương trình độc lập). Overload này hỗ trợ tra resource thuộc module có tên, và là nền tảng cho những API của `Class`/`Module`. Implementation mặc định:

- Nếu `moduleName == null`, thử delegate về `findResource(String name)`.
- Nếu `moduleName != null`, trả `null`; loader tùy chỉnh thực sự hỗ trợ nhiều named modules nên override để tra đúng module.

Bản thân protected overload này không tự thi hành toàn bộ quy tắc encapsulation dành cho API của caller; người triển khai phải hiểu hợp đồng của từng điểm gọi.

**Không nên dùng `--add-opens` như một bản vá chung cho mọi lỗi resource lookup**: nhiều API `ClassLoader.getResource` yêu cầu **unconditional opens** theo hợp đồng, và nguyên nhân thực tế cũng có thể là đường dẫn resource, launcher, module path hoặc loader lựa chọn sai.

## 10. findResource so với findClass, và đóng gói resource

`findClass("com.acme.Plugin")` tìm hoặc định nghĩa bytecode của **class**. `findResource("com/acme/plugin.yaml")` tìm một **resource URL**. Không có hợp đồng nào bảo đảm override một phương thức làm phương thức còn lại tự hỗ trợ cùng nguồn dữ liệu.

Ví dụ, custom loader có thể đọc `plugin/Greeter.class` từ một database để gọi `defineClass`, nhưng nếu không triển khai resource lookup, `getResource("plugin/defaults.yaml")` vẫn có thể trả `null`.

Tương tự, `javac` **không tự động copy mọi file `.properties` hoặc `.json`** từ thư mục source sang output classes. Nhiều công cụ build có bước xử lý resources riêng:

- Maven thông thường copy `src/main/resources` vào output theo lifecycle resources, rồi đóng gói vào JAR.
- Gradle Java plugin thông thường xử lý `src/main/resources` qua `processResources` vào output resources phù hợp.
- Nếu dùng `javac` đơn lẻ, cần tự đưa resource vào đúng classpath root/JAR và xác minh cấu trúc bằng công cụ như `jar tf app.jar`.

Để resource `com/acme/config.properties` tra bằng `getResource("com/acme/config.properties")`, JAR phải có entry với đúng đường dẫn đó; đặt file ngoài JAR rồi dùng đường classpath sai root sẽ khiến lookup thất bại.

**Một số nguồn chỉ đọc được qua loader/framework riêng**, ví dụ nested JAR của một executable application framework; đừng suy luận mọi cấu trúc JAR đều tra được bằng `new File(url.getPath())`.

## 11. Những lỗi và ngoại lệ thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| `getResource(name) == null` | Sai tên, thiếu resource, khác loader, JPMS chặn hoặc không có custom `findResource` | In tên resource, loader được dùng, kiểm tra JAR, module và path |
| `getResourceAsStream(name) == null` | Resource không tìm thấy/không thể mở theo hợp đồng | Kiểm tra cùng nguyên nhân như `getResource`, đừng dereference `null` |
| `getResource` tìm thấy nhưng `getResources` không thấy bản child | Override `findResource` nhưng chưa override `findResources` | Kiểm tra implementation của hai điểm mở rộng |
| `getResources` trả nhiều kết quả khác kỳ vọng | Có file trùng tên ở parent, nhiều JAR hoặc nhiều loader source | Duyệt URL, xem thứ tự delegation và quy tắc merge |
| `getResource` lấy cấu hình cũ | Parent-first đã chọn file parent | Tách namespace hoặc thiết kế priority phù hợp |
| `IOException` khi duyệt/đọc resource | Lỗi I/O từ `getResources` hoặc `URL.openStream` | Đọc cause, đóng stream, kiểm tra permission/nguồn |
| `UncheckedIOException` khi xử lý `resources(name)` stream | Lỗi I/O phát sinh ở quá trình đánh giá stream | Xử lý riêng hoặc dùng `getResources` phù hợp |
| `SecurityException` / không thấy URL | Quyền truy cập bị giới hạn bởi môi trường hoặc module | Đối chiếu SecurityManager cũ, permissions và module opening |
| ServiceLoader báo thiếu provider | Descriptor thiếu, sai tên SPI hoặc provider không load/không cast được | Kiểm tra `META-INF/services`, SPI defining loader, classpath/module path |
| Resource URL có `jar:` nên File API báo lỗi | Tài nguyên nằm trong JAR, không là file thường | Dùng `openStream` thay vì giả định file system path |
| Sau redeploy resource cũ vẫn xuất hiện | Loader cũ/cache hoặc classpath runtime chưa đổi | Kiểm tra loader sống, URL cache, vòng đời và tài nguyên đã deploy |

**Đọc nguyên nhân trước khi “sửa”.** Một resource có thể tồn tại đúng đường nhưng được lấy từ loader khác; thêm bản trùng tên vào classpath không tự giải quyết ưu tiên parent-first.

## 12. Thiết kế custom loader: tính nhất quán, hiệu năng và tài nguyên

### 12.1. Thứ tự và tính đầy đủ

Khi override `findResource` và `findResources`, cần quyết định:

1. **Nguồn local nào hợp lệ?** Thư mục, JAR, bộ nhớ, database hay endpoint được kiểm soát.
2. **Tên resource ánh xạ ra nguồn thế nào?** Chuẩn hóa `/`, package path và ngăn path traversal.
3. **Một resource có nhiều bản local không?** Nếu có, `findResources` phải có khả năng trả nhiều URL, không chỉ gọi `findResource` một lần như loader thư mục mẫu chỉ chứa tối đa một file cho mỗi tên.
4. **URL có mở được stream ổn định không?** Một URL chỉ “trông đúng” nhưng `openStream()` không hoạt động là một implementation thiếu hoàn chỉnh.
5. **Parent vs local ưu tiên ra sao?** Nếu override cả public API, bảo đảm `getResource` và phần tử đầu của `getResources` tương thích.
6. **Thời điểm enumerate và lifecycle:** nếu resources bị sửa/xóa ngay giữa quá trình tra và mở stream, `URL.openStream` có thể thất bại sau khi URL đã được trả về.

### 12.2. Xử lý dữ liệu không tin cậy

Không tin tưởng resource name được cấu hình bên ngoài: tránh đọc ngoài root, symlink escape, nguồn URL bất ngờ, file cực lớn và mô hình tải từ mạng thiếu timeout/giới hạn. Chỉ cấp quyền đọc tới những nguồn đã được thiết kế.

Trong bài ví dụ, override `findResource` trả `null` nếu không thể tạo URL, theo hợp đồng API. Với loader thực tế, hãy ghi log chẩn đoán phù hợp ở tầng ứng dụng (không lộ thông tin nhạy cảm) và xác định cách xử lý lỗi I/O, thay vì nuốt mọi exception làm việc tìm file bỗng nhiên “thành công rỗng”.

### 12.3. Tính đồng thời và cache

Nhiều thread cùng gọi `getResource` / `getResources` không tạo ra một khóa initialization lớp như `<clinit>`. Nếu loader có state mutable, cache, connection tới backend hoặc bộ đếm, cần cơ chế đồng bộ riêng thích hợp.

Với file/JAR có khả năng thay đổi sau khi khởi chạy, cache resource URL hoặc nội dung cần có chính sách invalidation rõ ràng. Không giả định JVM tự reload resource chỉ vì file đã được thay; một số framework snapshot hoặc cache ở tầng riêng.

### 12.4. Đóng loader và stream

Nếu dùng `URLClassLoader`, `close()` giải phóng tài nguyên loader quản lý (ví dụ các JAR handle), nhưng không bảo đảm class đã định nghĩa unload lập tức. Cần đóng `InputStream` riêng cho mỗi lần đọc. Nếu muốn reload plugin, thường phải kiểm soát vòng đời class loader, reference tới class/instance và cache/descriptor; không chỉ thay thế file trong JAR đang được dùng.

### 12.5. Không tự viết URL handler nếu không cần

Nếu resource đã nằm trong thư mục/JAR bình thường, `URLClassLoader` có triển khai tìm class/resource từ URL, do đó đa số trường hợp không cần tự override các method trên. Chỉ viết custom loader khi nguồn bytecode/resource khác biệt hoặc có yêu cầu policy đặc biệt; thêm một lớp tùy biến không cần thiết dễ làm mất các tính năng JAR/module mà runtime đã hỗ trợ.

## 13. Quy trình chẩn đoán resource không tìm thấy

1. **Ghi chính xác tên resource** được truyền, bao gồm dấu `/` đầu nếu có, chữ hoa/thường và vị trí package.
2. **Xác định API cụ thể**: `Class.getResource`, `ClassLoader.getResource` hay `getResources`? Chúng có quy tắc tên/tra khác.
3. **Xác định loader**: class đang chạy do loader nào định nghĩa; API dùng TCCL, system loader hay plugin loader?
4. **Xác minh resource thật trong artifact deploy**: với JAR dùng `jar tf file.jar`, đối chiếu entry name; với thư mục classes xem classpath root.
5. **Đọc tất cả kết quả** của `getResources` nếu có xung đột: URL nào xuất hiện trước, liệu parent có tài nguyên cùng tên không?
6. **Với custom loader, kiểm tra riêng từng điểm mở rộng** `findResource` / `findResources`. Lưu ý mặc định cả hai không tự đọc thư mục tùy biến.
7. **Với named modules, kiểm tra package đã opens hay chưa** và API cụ thể cần unconditional opens hay opening theo caller module.
8. **Đọc qua URL.openStream()**; nếu không đọc được dù URL tồn tại, phân biệt lỗi lookup với lỗi I/O khi mở.
9. **Kiểm tra vòng đời**: loader đóng chưa, thread context loader đang trỏ vào đâu, resource có bị xóa/cập nhật giữa chừng không?
10. **Làm smoke test bằng artifact chạy thật**, tránh chỉ kiểm tra resource tồn tại trong source tree mà không được copy vào output/JAR.

Snippet chẩn đoán dùng được trong ứng dụng nếu đã có `ClassLoader loader`:

```java
String name = "com/acme/config.properties";
System.out.println("first=" + loader.getResource(name));
Enumeration<URL> matches = loader.getResources(name);
int count = 0;
while (matches.hasMoreElements()) {
    System.out.println("match[" + count++ + "]="
            + matches.nextElement());
}
System.out.println("count=" + count);
```

Đây là **snippet**, cần import `java.net.URL`, `java.util.Enumeration` và xử lý `IOException` ở caller. URL in ra phục vụ chẩn đoán nguồn, tránh để URL chứa token hoặc đường dẫn nhạy cảm xuất hiện trong log sản xuất.

## 14. Những ngộ nhận thường gặp

**“Nạp được class tức các file .properties đi kèm chắc chắn cũng tìm được.”** Sai: class loading và resource lookup tách biệt. Class có thể được tạo từ bytecode mà loader không biết tìm file resource khác.

**“Override findResource là đủ cho cả getResource và getResources.”** Sai: `findResources` mặc định là enumeration rỗng, cần override khi muốn tìm nhiều resource local.

**“getResource và getResources luôn trả cùng một URL.”** Một API trả một URL, API kia trả có thể nhiều; với triển khai mặc định, URL đầu tiên của enumeration nên thống nhất với getResource trong điều kiện truy cập tương ứng.

**“Tên resource luôn phải có dấu / đầu.”** Với `ClassLoader.getResource`, tên theo đường dẫn resource không bắt đầu bằng slash; dấu `/` đầu là quy tắc đặc biệt của `Class.getResource` khi muốn absolute path.

**“findResource có thể trả về Path hoặc File.”** Chữ ký trả `URL`; có thể trỏ tới jar, file hoặc giao thức khác có thể mở stream, không nhất thiết là file.

**“Tên resource trong Java dùng File.separator của Windows.”** Tên tài nguyên theo API dùng `/`, không dùng `\\` giữa các segment.

**“Thấy đúng file trong module JAR thì loader.getResource chắc chắn trả URL.”** Named module có encapsulation; `ClassLoader.getResource` có thể yêu cầu package opened unconditionally cho resource không phải .class.

**“Không thấy provider thì chỉ cần thêm findClass.”** ServiceLoader discovery còn cần provider metadata đúng, resource lookup đúng và nhất quán SPI type identity.

**“Close URLClassLoader thì mọi stream đã mở tự đóng.”** Stream cần được đóng độc lập; lifecycle URL/JAR và class unloading là vấn đề khác.

## 15. Bài tập tự kiểm tra

**Câu 1.** Parent và child đều có `demo/config.properties`. Với delegation mặc định, `child.getResource` thường trả bản nào?

**Đáp án:** Bản parent, vì `getResource` hỏi parent trước và chỉ gọi `findResource` local khi parent không tìm thấy.

**Câu 2.** `child.getResources("demo/config.properties")` trả những gì nếu parent và child đều cung cấp và loader hỗ trợ findResources local?

**Đáp án:** Enumeration với các URL do parent trả trước, tiếp theo URL của child. Nội dung có thể trùng nhau nhưng API không tự quy tắc hóa theo phiên bản.

**Câu 3.** Custom loader chỉ override `findResource` và có resource riêng ở child. Tại sao `getResources` không thấy resource ấy?

**Đáp án:** Vì `getResources` gọi extension point `findResources` để thu thập URL local; `findResources` mặc định trả rỗng và không tự dùng `findResource`.

**Câu 4.** Với class trong `com.acme`, `SomeClass.class.getResource("config.properties")` khác gì `SomeClass.class.getClassLoader().getResource("config.properties")`?

**Đáp án:** Phương thức trên `Class` tra tương đối từ package `com/acme/`; phương thức trên `ClassLoader` tra từ root theo đúng tên không có tiền tố package.

**Câu 5.** `getResource` trả một URL bắt đầu bằng `jar:file:`. Có nên `new File(url.toURI())` để đọc?

**Đáp án:** Không giả định URL là file local. Dùng `url.openStream()` và đóng bằng try-with-resources; xử lý lỗi I/O.

**Câu 6.** Một resource JSON nằm trong package của named module nhưng `ClassLoader.getResource` trả null. Bước kiểm tra thêm?

**Đáp án:** Kiểm tra package ấy có được mở **unconditionally** theo điều kiện của ClassLoader resource lookup hay không; đồng thời xác minh tên resource, module/loader và phương thức tra.

**Câu 7.** Khi nào nên dùng `Module.getResourceAsStream` thay cho `ClassLoader.getResource`?

**Đáp án:** Khi muốn truy cập resource trong một module cụ thể và cần tuân theo module/caller access rules, thay vì phụ thuộc vào tìm kiếm tổng quát của class loader.

## 16. Kết luận và tài liệu chính thức

`findResource` và `findResources` là **hai điểm mở rộng resource lookup** của `ClassLoader`. Phương thức thứ nhất hỗ trợ tìm một URL local, phương thức thứ hai hỗ trợ enumerate nhiều URL local; chúng độc lập và cần triển khai nhất quán. Với public API mặc định, `getResource` ưu tiên parent khi tìm một resource, còn `getResources` kết hợp parent rồi local. Thiết kế đúng cơ chế này giúp plugin và framework quản lý cấu hình, metadata và service providers đáng tin cậy, đồng thời tránh lỗi thứ tự tìm kiếm và ranh giới module.

Tài liệu đối chiếu:

- Java SE 17 `ClassLoader` API (`getResource`, `getResources`, `findResource`, `findResources`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `Class` API (`getResource`, `getResourceAsStream`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 `Module` API (`getResourceAsStream`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java SE 17 `URLClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-007">Thứ tự ủy quyền tài nguyên phải nhất quán với chính sách nạp lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự ủy quyền tài nguyên phải nhất quán với chính sách nạp lớp

Khi viết `ClassLoader` tùy biến, ta thường quan tâm đến `loadClass()`: ưu tiên hỏi **parent** trước (*parent-first*) hay tự tìm lớp trong nguồn của **child** trước (*child-first*)? Tuy nhiên, một ứng dụng Java còn tải các **resource** như `.properties`, template, schema, cấu hình logging, metadata `META-INF/services/...` hoặc tệp dữ liệu đóng gói cùng thư viện. Các resource được tìm bằng `getResource()`, `getResources()` và `getResourceAsStream()`.

**Quy tắc thiết kế cốt lõi:** Nếu một plugin/class được định nghĩa từ nguồn child thì các resource **gắn với triển khai đó** thường cũng phải được chọn từ child; nếu class dùng chung do parent định nghĩa thì metadata/resource tương ứng cần có chính sách rõ ràng từ parent. Khi đổi `loadClass()` sang child-first nhưng để `getResource()` và `getResources()` giữ parent-first mặc định, ta có thể nhận **code của child nhưng config/metadata của parent**, tạo hành vi rất khó phát hiện.

Lưu ý chính xác: Java **không bắt buộc mọi resource phải luôn theo một thứ tự duy nhất với mọi lớp**. Một số cấu hình cần parent-first có chủ ý, một số resource cần tổng hợp từ nhiều nơi. Điều cần bảo đảm là **chính sách tra cứu nhất quán với phạm vi sở hữu resource và hợp đồng nạp lớp**, đặc biệt với những resource gắn chặt với một class/provider.

## 1. Hai đường tìm kiếm phải hiểu riêng

| Hoạt động | API thường gặp | Mục tiêu |
| --- | --- | --- |
| Nạp định nghĩa lớp | `ClassLoader.loadClass(String)`, `Class.forName(..., loader)` | Nhận `Class<?>` có defining loader phù hợp. |
| Tìm một resource | `ClassLoader.getResource(String)` | Trả về **URL đầu tiên** phù hợp hoặc `null`. |
| Mở stream tài nguyên | `ClassLoader.getResourceAsStream(String)` | Thường dựa trên resource đã chọn từ `getResource`, trả `InputStream` hoặc `null`. |
| Tìm nhiều resource cùng tên | `ClassLoader.getResources(String)` | Trả một `Enumeration<URL>` của các bản tìm được theo chính sách loader. |
| Tìm resource liên quan tới lớp | `SomeClass.class.getResource(...)` | Áp dụng quy tắc tên tương đối/tuyệt đối theo package, rồi tìm trong ngữ cảnh của lớp/module. |

Trong `java.lang.ClassLoader` mặc định, đường nạp lớp thông thường tuân theo **parent delegation**: kiểm tra đã nạp, nhờ parent trước, sau đó mới tự tìm qua `findClass()`. Với resource, cách triển khai `getResource()` mặc định cũng thử parent trước rồi mới gọi `findResource()`; `getResources()` mặc định liệt kê kết quả từ parent trước rồi tới `findResources()`.

Nhưng đây là **hai cơ chế riêng**. Nếu chỉ ghi đè `loadClass` mà không xét API resource, class có thể theo child-first trong khi resource vẫn parent-first. Tương tự, nếu chỉ ghi đè `getResource` nhưng quên `getResources`, việc tìm một resource và nhiều resource có thể khác chính sách.

### Sơ đồ vấn đề

~~~text
               Cùng tên: plugin/Engine.class
                         plugin/config.properties

                       Parent      Child
                         │           │
          class?          │           └──── CHỌN Engine của Child
                         │
          resource?       └──────────────── CHỌN config của Parent

             Engine Child + config Parent
                 → sai schema, sai feature flag,
                   hành vi khó tái hiện
~~~

Đây là một tình huống thường gặp khi cùng tên resource tồn tại ở nhiều JAR/thư mục. Nếu parent không có resource trùng thì vấn đề có thể bị che giấu trong giai đoạn phát triển; sau khi nâng cấp host hoặc plugin, resource mới xuất hiện ở parent và bắt đầu chiếm ưu tiên bất ngờ.

## 2. Parent-first và child-first: nhất quán nghĩa là gì?

**Parent-first** ưu tiên lớp và resource do parent cung cấp:

~~~text
loadClass("demo.Marker")         → Parent thử trước → Parent.Marker
getResource("demo/id.properties")→ Parent thử trước → Parent resource
getResources(...)                → Parent entries trước, child entries sau
~~~

Ưu điểm là chia sẻ API và tài nguyên chung, tránh plugin tự thay thế những class do host sở hữu. Nếu resource thực sự thuộc về phiên bản plugin child, parent-first không phải lúc nào cũng là lựa chọn mong muốn.

**Child-first** chỉ định child ưu tiên các class và resource **trong phạm vi cô lập đã chọn**:

~~~text
loadClass("plugin.impl.Engine")     → Child thử trước → Child.Engine
getResource("plugin/engine.yml")   → Child thử trước → Child resource
getResources("plugin/engine.yml")  → Child entries trước, parent entries sau
~~~

Lưu ý: có thể và thường **nên** giữ parent-first với `java.*` và package API hợp đồng chung, chỉ child-first với implementation riêng. Tránh child-first toàn bộ không có ranh giới rõ ràng, vì dễ tạo duplicate interface/class, `ClassCastException` hoặc rắc rối linking.

**Một-resource** và **nhiều-resource** có yêu cầu riêng:

- `getResource(name)` chọn **một** vị trí; thay thứ tự có thể đổi kết quả ngay.
- `getResources(name)` có thể tìm **nhiều** bản. Thứ tự enumeration có thể ảnh hưởng việc merge cấu hình hoặc khám phá provider. Không nên tùy tiện đảo/xóa kết quả nếu framework dựa trên ngữ nghĩa hợp nhất.
- `getResourceAsStream(name)` thường ủy quyền đến `getResource` của chính loader; nếu custom loader đổi cách tìm URL thì đường stream cần được xem xét nhất quán.
- `findResource` và `findResources` được thiết kế để tìm **resource cục bộ** của loader; việc ủy quyền parent thuộc lớp phương thức `get...`.

**Bất biến nên kiểm tra cho các resource gắn chặt với class:** Sau khi biết class `C` thực sự do loader nào định nghĩa, resource đóng gói cùng `C` phải được lấy theo contract của thành phần đó, tránh vô tình dùng một bản của thành phần khác.

## 3. Ví dụ Java 17 chạy độc lập: nhìn thấy hai chính sách

Chương trình sau tự tạo hai thư mục tạm `parent` và `child`. Cả hai có cùng tên class `demo.Marker` và resource `demo/id.properties`, nhưng nội dung khác nhau. Nó dùng `JavaCompiler` của JDK để biên dịch hai bản class tại thời điểm chạy, rồi quan sát class và resource do từng loader chọn.

**Tệp duy nhất: `ResourceDelegationDemo.java`**

~~~java
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class ResourceDelegationDemo {
    private static final String CLASS_NAME = "demo.Marker";
    private static final String RESOURCE = "demo/id.properties";

    static class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);

                if (type == null) {
                    if (CLASS_NAME.equals(name)) {
                        try {
                            type = findClass(name);
                        } catch (ClassNotFoundException notLocal) {
                            type = super.loadClass(name, false);
                        }
                    } else {
                        type = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        public URL getResource(String name) {
            if (RESOURCE.equals(name)) {
                URL local = findResource(name);
                if (local != null) {
                    return local;
                }
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name)
                throws IOException {
            if (!RESOURCE.equals(name)) {
                return super.getResources(name);
            }

            List<URL> result = Collections.list(findResources(name));
            result.addAll(Collections.list(
                    getParent().getResources(name)));
            return Collections.enumeration(result);
        }
    }

    private static void buildVersion(Path directory, String owner)
            throws IOException {
        Path source = directory.resolve("src/demo/Marker.java");
        Path resource = directory.resolve(RESOURCE);

        Files.createDirectories(source.getParent());
        Files.createDirectories(resource.getParent());
        Files.writeString(source,
                "package demo; public class Marker { " +
                "public static String owner() { return \"" +
                owner + "\"; } }");
        Files.writeString(resource, "owner=" + owner + "\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a JDK, not a JRE");
        }

        int exit = compiler.run(
                null, null, null,
                "-d", directory.toString(), source.toString());

        if (exit != 0) {
            throw new IllegalStateException("Compilation failed: " + exit);
        }
    }

    private static String readOwner(URL url) throws IOException {
        if (url == null) {
            return "(missing)";
        }

        Properties props = new Properties();
        try (InputStream input = url.openStream()) {
            props.load(input);
        }
        return props.getProperty("owner");
    }

    private static void inspect(String label, ClassLoader loader)
            throws Exception {
        Class<?> type = loader.loadClass(CLASS_NAME);
        String classOwner =
                (String) type.getMethod("owner").invoke(null);

        String firstResource =
                readOwner(loader.getResource(RESOURCE));

        Enumeration<URL> found = loader.getResources(RESOURCE);
        List<String> resourceOrder = new ArrayList<>();

        while (found.hasMoreElements()) {
            resourceOrder.add(readOwner(found.nextElement()));
        }

        System.out.println(label + " class: " + classOwner);
        System.out.println(label + " resource: " + firstResource);
        System.out.println(label + " resources: " +
                String.join(" -> ", resourceOrder));
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("resource-order-");
        Path parentDir = Files.createDirectories(root.resolve("parent"));
        Path childDir = Files.createDirectories(root.resolve("child"));

        buildVersion(parentDir, "PARENT");
        buildVersion(childDir, "CHILD");

        try (URLClassLoader parent = new URLClassLoader(
                    new URL[] { parentDir.toUri().toURL() }, null);
             URLClassLoader parentFirst = new URLClassLoader(
                    new URL[] { childDir.toUri().toURL() }, parent);
             ChildFirstLoader childFirst = new ChildFirstLoader(
                    new URL[] { childDir.toUri().toURL() }, parent)) {

            inspect("Parent-first", parentFirst);
            inspect("Child-first", childFirst);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa `ResourceDelegationDemo.java`, sử dụng **JDK 17+** (cần `javac` và system compiler của module `jdk.compiler`):

~~~bash
javac --release 17 ResourceDelegationDemo.java
java ResourceDelegationDemo
~~~

**Stdout dự kiến với JDK 17+ tiêu chuẩn và các URL thư mục do chương trình tạo:**

~~~text
Parent-first class: PARENT
Parent-first resource: PARENT
Parent-first resources: PARENT -> CHILD
Child-first class: CHILD
Child-first resource: CHILD
Child-first resources: CHILD -> PARENT
~~~

Giải thích:

1. `buildVersion` sinh hai `demo.Marker` (mỗi bản trả tên chủ sở hữu khác nhau) và hai resource trùng tên `demo/id.properties`.
2. `parent` có URL trỏ đến bản PARENT; cả `parentFirst` và `childFirst` trỏ bản CHILD nhưng dùng cùng parent.
3. `parentFirst` là `URLClassLoader` mặc định, nên `loadClass` cùng `getResource` ủy quyền parent trước; kết quả đều là PARENT.
4. `ChildFirstLoader.loadClass` tự gọi `findClass` trước **chỉ cho `demo.Marker`**, sau đó mới fallback sang parent. Với những lớp khác (đặc biệt JDK classes), nó giữ hành vi chuẩn.
5. `ChildFirstLoader.getResource` ưu tiên `findResource` của child cho đúng tên resource liên quan `demo.Marker`, sau đó fallback về parent.
6. `ChildFirstLoader.getResources` trả danh sách child trước rồi đến parent **cho resource đã chọn**; các resource khác sử dụng triển khai chuẩn. Vì vậy kết quả enumeration theo hai chính sách đổi thứ tự.
7. `getClassLoadingLock(name)` và `findLoadedClass(name)` tránh tự định nghĩa trùng và giúp giữ đồng bộ hành vi nạp lớp; `resolveClass` chỉ gọi khi yêu cầu resolve.

**Phạm vi của mã:** Đây là demo lớp/resource trên **classpath dạng thư mục** với một tên class cụ thể. Loader thực tế cần kiểm soát package, bảo vệ API chung, thứ tự resource trùng, lỗi I/O và quan hệ module rõ hơn. `getResources` ở ví dụ cố ý không loại duplicate URL; khi ứng dụng muốn merge/provider discovery cần xét việc lọc trùng và giữ thứ tự theo hợp đồng.

### Nếu chỉ ghi đè loadClass() thì sao?

Nếu `ChildFirstLoader` chỉ có override `loadClass`, còn giữ nguyên `getResource` và `getResources` của `ClassLoader`/`URLClassLoader`, ta có thể quan sát:

~~~text
Child-first class: CHILD
Child-first resource: PARENT
Child-first resources: PARENT -> CHILD
~~~

Đây là **kết quả phân tích cho biến thể** bỏ hai override resource, không phải output của chương trình hoàn chỉnh đang cung cấp. Nó minh họa chính xác nguyên nhân cần đồng bộ chính sách khi class và resource có liên quan.

## 4. Phân biệt Class.getResource và ClassLoader.getResource

Hai API dùng **quy tắc tên resource khác nhau**, dễ dẫn đến chẩn đoán nhầm thứ tự loader:

| Gọi API | Cách hiểu đường dẫn |
| --- | --- |
| `loader.getResource("config/app.properties")` | Tên resource tính từ **root**, không bắt đầu bằng dấu `/`. |
| `SomeClass.class.getResource("config/app.properties")` | Tên tương đối tính từ **package của SomeClass**. |
| `SomeClass.class.getResource("/config/app.properties")` | Tên tuyệt đối tính từ **root** (bỏ dấu `/` đầu trước khi lookup). |

Ví dụ khi `demo.Marker` tìm `demo/id.properties`:

~~~java
// Fragment minh họa với Class<?> markerType đã có.
markerType.getResource("id.properties");
markerType.getResource("/demo/id.properties");
markerType.getClassLoader().getResource("demo/id.properties");
~~~

Ba lời gọi đều có thể nói tới resource tương ứng với package `demo`, **nhưng đường tìm và ngữ nghĩa module của Class.getResource có thể khác**. Đoạn code là fragment, không nằm trong ví dụ hoàn chỉnh phần trước.

Đừng thêm dấu `/` đầu tên cho `ClassLoader.getResource` rồi quy kết việc trả `null` là do parent/child; vấn đề có thể chỉ là **tên resource sai quy ước**.

**Với named modules (Java 9+)**, tra cứu non-`.class` resource còn chịu ảnh hưởng module encapsulation/`opens`; thứ tự loader không phải yếu tố duy nhất. Với `Class.getResource`, module định nghĩa class có thể quyết định cách và phạm vi truy cập resource. Ví dụ Java 17 ở phần 3 dùng URL classpath/unnamed module để làm rõ delegation độc lập với JPMS.

## 5. getResources(): thứ tự tổng hợp quan trọng như thế nào?

Nhiều framework không tìm **một** file mà duyệt **tất cả** resource cùng tên. Ví dụ:

- Metadata `META-INF/services/com.acme.api.Plugin` cho cơ chế SPI của Java.
- Nhiều tệp cấu hình cùng tên do host và plugin cung cấp.
- Resources cho discovery, routing, template registry, hoặc cấu hình thư viện.

Trong `ClassLoader` mặc định, `getResources` liệt kê parent trước rồi local của child. Nếu class được ưu tiên từ child nhưng resource SPI của parent xuất hiện trước, code có thể khám phá provider không khớp phiên bản class/API đã nạp hoặc merge config theo thứ tự bất ngờ.

**Ba chính sách khác nhau phải được xác định rõ:**

1. **First-wins**: lấy resource đầu tiên (thường qua `getResource`); child-first hay parent-first quyết định kết quả.
2. **Last-wins**: đọc mọi resource rồi ghi đè giá trị theo thứ tự; một thứ tự đảo có thể làm bản cuối chiếm ưu tiên.
3. **Merge-all**: cần toàn bộ resource, nhưng có thể cần chống duplicate hoặc xử lý xung đột tên/provider.

Không nên đánh đồng `getResources` với việc tìm một resource duy nhất. Một triển khai custom đôi khi có quyền trả các bản từ cả parent và child, nhưng phải giữ được **quy tắc thứ tự nhất quán và có tài liệu**. Với `ServiceLoader`, còn có điều kiện provider type và khả năng nạp class của provider bằng loader được chọn; đổi thứ tự resource **không đảm bảo** ServiceLoader có thể cast được provider nếu API interface đã bị các loader định nghĩa trùng.

### Ví dụ xung đột metadata

~~~text
parent:
  com/acme/api/Codec.class
  META-INF/services/com.acme.api.Codec  → com.acme.api.DefaultCodec

child:
  com/acme/plugin/FastCodec.class
  META-INF/services/com.acme.api.Codec  → com.acme.plugin.FastCodec
~~~

Trong thiết kế hợp lệ, **interface Codec được chia sẻ** từ parent; class FastCodec do plugin loader định nghĩa nhưng implements đúng interface ấy. Resource SPI có thể được tổng hợp qua `getResources` để cả hai provider có thể được tìm. Không nên định nghĩa lại `Codec` trong child và mong việc đảo thứ tự `getResources` sửa được `ClassCastException`.

## 6. Khi nào nên ưu tiên child resource và khi nào giữ parent resource?

Không nên áp đặt child-first cho mọi thứ. Một chính sách thực tế thường có ranh giới:

| Nhóm class/resource | Quy tắc gợi ý | Vì sao |
| --- | --- | --- |
| Java platform và API host chia sẻ | Parent-first | Duy trì type identity/API contract và hành vi nền tảng. |
| Implementation riêng của plugin | Child-first nếu cần isolation | Cho phép plugin dùng phiên bản triển khai riêng. |
| Resource đi kèm implementation plugin | Child-first tương ứng | Tránh plugin đọc config/schema/template của host. |
| Resource cấu hình host được chủ động override bởi plugin | Thỏa thuận rõ first-wins hay merge | Có thể cần parent-first, child-first hoặc merge theo mục tiêu nghiệp vụ. |
| `META-INF/services/...` | Thường duyệt nhiều resource | Giữ provider discovery đầy đủ và bảo đảm API chung. |

Chính sách có thể triển khai theo **namespace/package/resource prefix** hoặc theo mapping rõ ràng giữa plugin và resource. Điều quan trọng là xác định **ai sở hữu** từng đường dẫn và **điều gì xảy ra nếu hai bên cùng tên**.

Với loader child-first được viết tùy biến, những phương thức thường cần xem xét như một bộ liên quan:

~~~text
loadClass / findClass
getResource / findResource
getResources / findResources
getResourceAsStream
~~~

`getResourceAsStream` trong triển khai chuẩn dựa trên `getResource`; thông thường không cần override riêng nếu hành vi mở stream theo URL đã đáp ứng. Tuy nhiên nếu custom loader lưu resource trong bộ nhớ, filesystem phi chuẩn hay xử lý stream đặc biệt, cần kiểm tra đường stream cũng theo cùng chính sách.

## 7. Lỗi phổ biến khi triển khai

### 7.1. Chỉ đổi loadClass, quên getResource

**Triệu chứng:** `MyPlugin.class.getClassLoader()` là child nhưng `loader.getResource("plugin/config.yml")` trả URL trong JAR parent. Plugin có thể đọc cấu hình sai phiên bản.

**Khắc phục:** Thống nhất chính sách cho resource gắn với implementation. In URL được chọn và class defining loader khi debug.

### 7.2. getResource child-first nhưng getResources parent-first

**Triệu chứng:** Đọc một file thấy bản child, còn framework dùng enumeration lại nạp provider/config parent trước.

**Khắc phục:** Đặt test cho cả `getResource` và thứ tự `getResources` của tên resource quan trọng. Với resource cần merge, kiểm tra danh sách đầy đủ, không chỉ phần tử đầu.

### 7.3. Ghi đè loadClass mà phá parent delegation

**Triệu chứng:** `ClassCastException` với hai bản cùng tên API, `LinkageError` hoặc lớp JDK bị chặn tìm.

**Khắc phục:** Giữ parent-first cho API/JDK, chỉ child-first các package implementation có kiểm soát; sử dụng `findLoadedClass`, `getClassLoadingLock` và `resolveClass` đúng hợp đồng.

### 7.4. Nghĩ URL trả về là file trên đĩa

Resource có thể có scheme `jar:`, `jrt:` hoặc URL từ loader đặc thù. Không nên ép `Paths.get(url.toURI())` cho mọi URL. Thường nên dùng `url.openStream()` hoặc `getResourceAsStream` khi chỉ cần đọc bytes. Với URL `jar:`, đường đến một entry trong JAR không phải file riêng ở filesystem mặc định.

### 7.5. Nhầm tên tương đối/tuyệt đối

`ClassLoader.getResource("demo/id.properties")` và `Marker.class.getResource("id.properties")` có thể cùng tên resource logic, nhưng `ClassLoader.getResource("/demo/id.properties")` là sai quy tắc thông thường. Luôn xác nhận đường dẫn trong JAR bằng công cụ phù hợp.

### 7.6. Module access ảnh hưởng resource lookup

Với named module, resource đóng trong package không mở có thể không được trả về qua API từ bên ngoài, ngay cả khi class loader thích hợp nhìn thấy module đó. Đừng giải quyết mọi `null` bằng việc đảo thứ tự parent/child; xem module ownership và `opens`/encapsulation khi cần.

### 7.7. Thay đổi global TCCL không quản lý vòng đời

`Thread.currentThread().getContextClassLoader()` được nhiều framework dùng để chọn loader cho resource/provider. Đổi TCCL mà không khôi phục trong `finally` có thể gây kết quả khác nhau theo thread và giữ plugin loader cũ sau redeploy. Dùng TCCL có chủ đích và quản lý vòng đời.

## 8. Quy trình điều tra khi class đúng nhưng resource sai

1. **Xác định class đích** và in `Class.getClassLoader()` để biết defining loader thực sự. Không giả định loader gọi `loadClass` đã tự định nghĩa class.
2. **Xác định tên resource chính xác**: đường dẫn tuyệt đối hay tương đối, có package prefix, có dấu `/` đầu không.
3. **In URL đầu tiên** từ `loader.getResource(name)` và duyệt **mọi URL theo thứ tự** từ `loader.getResources(name)`.
4. **So sánh nơi class được định nghĩa** với nơi resource được chọn: child class đi kèm parent config là dấu hiệu đáng nghi.
5. **Kiểm tra tài nguyên được dùng qua đường nào**: `SomeClass.getResource`, `ClassLoader.getResource`, `ServiceLoader` hoặc TCCL có thể khác.
6. **Kiểm tra chính sách parent-first/child-first** của tên class và tên resource, cùng fallback khi child không có.
7. **Kiểm tra module/resource encapsulation** nếu là named module.
8. **Khóa hành vi mong muốn bằng kiểm thử nhỏ** với hai bản class/resource trùng tên, xác nhận cả `getResource` và `getResources` như chương trình Java 17 phía trên.

Một đoạn chẩn đoán ngắn (fragment, giả sử có `type` và `resourceName`):

~~~java
ClassLoader loader = type.getClassLoader();
System.out.println("Defining loader: " + loader);

URL first = loader.getResource(resourceName);
System.out.println("First resource: " + first);

Enumeration<URL> all = loader.getResources(resourceName);
while (all.hasMoreElements()) {
    System.out.println("Candidate: " + all.nextElement());
}
~~~

Fragment trên cần import `java.net.URL`, `java.util.Enumeration`, xử lý `IOException`, và giả định `loader` không null. Khi `type` do bootstrap loader định nghĩa (`getClassLoader() == null`), cần dùng API tra cứu resource phù hợp thay vì gọi method trên null.

## 9. Tự kiểm tra

**Câu 1.** Một custom loader nạp `plugin.Engine` bằng child-first nhưng giữ `getResource` mặc định. Parent và child cùng có `plugin/settings.properties`. Resource nào thường được chọn?

**Đáp án:** Parent-first resource lookup thường chọn **bản parent**; kết quả có thể không khớp implementation `Engine` từ child.

**Câu 2.** Vì sao override `getResource` chưa đủ nếu framework dùng `getResources`?

**Đáp án:** `getResources` có quy tắc liệt kê riêng. Framework có thể thấy bản parent trước dù `getResource` đang ưu tiên child.

**Câu 3.** Có nên child-first mọi `java.*` và interface API host để đồng bộ với plugin không?

**Đáp án:** Không. Giữ parent-first cho platform và API chung giúp tránh hai định nghĩa kiểu khác nhau. Chỉ áp dụng child-first có phạm vi cho implementation/resource thuộc plugin.

**Câu 4.** Tên nào phù hợp với `ClassLoader.getResource` nếu tệp nằm ở `demo/id.properties`?

**Đáp án:** `"demo/id.properties"`, không có dấu `/` đầu.

**Câu 5.** Với `META-INF/services`, liệu chỉ đảo `getResources` sang child-first là đủ sửa mọi lỗi `ServiceLoader`?

**Đáp án:** Không. Cần bảo đảm các provider được tìm thấy **triển khai đúng cùng interface API** do loader mà consumer kỳ vọng định nghĩa; còn phải xét module/discovery và khả năng nạp provider.

## Ghi nhớ

**Class loading và resource lookup là hai đường tra cứu khác nhau nhưng có liên hệ về ngữ nghĩa.** Với parent-first, mặc định class/resource thường ưu tiên parent; với child-first có chọn lọc, resource gắn với implementation cần được điều phối đồng bộ qua `getResource`, `getResources` và đường stream liên quan. Xác định rõ **class nào thuộc parent hay child**, **resource nào thuộc thành phần đó** và **thứ tự first-wins/merge-all** để tránh kết hợp code từ một phiên bản với cấu hình/metadata từ phiên bản khác.

**Tài liệu chuẩn:**

- Java SE 17 API, `ClassLoader`, các phương thức `loadClass`, `getResource`, `getResources`, `findResource`, `findResources`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `URLClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 API, `Class.getResource`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 API, `ServiceLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-008">file:, jar:, jrt: và giới hạn chuyển URL thành File</a>

<details>
<summary>Xem chi tiết</summary>

# file:, jar:, jrt: và giới hạn chuyển URL tài nguyên thành File trong Java

Một tài nguyên (*resource*) trong ứng dụng Java không nhất thiết nằm trong một tệp thông thường của hệ điều hành. Khi dùng `Class.getResource(...)` hoặc `ClassLoader.getResource(...)`, bạn nhận một `java.net.URL` mô tả **cách truy cập tài nguyên**, chứ không nhận cam kết rằng có thể gọi `new File(...)` để thao tác trên nó.

Ba dạng URL hay gặp khi học class loader và tài nguyên trên Java 17+ là:

| Scheme | Ví dụ minh họa | Tài nguyên ở đâu? |
|---|---|---|
| **`file:`** | `file:/opt/app/classes/demo/config.txt` | Tệp/thư mục trên một filesystem, thường gặp khi chạy từ thư mục `classes` |
| **`jar:`** | `jar:file:/opt/app/lib/app.jar!/demo/config.txt` | Một entry nằm bên trong archive JAR |
| **`jrt:`** | `jrt:/java.base/java/lang/String.class` | Nội dung runtime image theo mô hình module của JDK 9+ |

**Nguyên tắc quan trọng:** Khi mục tiêu chỉ là **đọc dữ liệu resource**, ưu tiên `getResourceAsStream(...)` hoặc `URL.openStream()`. Chỉ chuyển sang `Path`/`File` khi thực sự cần API tệp và đã xác minh URL có scheme và filesystem phù hợp.

## 1. Resource name khác URL, Path và File

Với lớp:

```java
package demo;

public final class App {
}
```

và cấu trúc triển khai:

```text
classes/
└── demo/
    ├── App.class
    └── config.txt
```

Bạn có thể tra resource theo hai cách:

```java
URL a = App.class.getResource("config.txt");
URL b = App.class.getClassLoader()
        .getResource("demo/config.txt");
```

Cả hai tìm cùng resource trong ví dụ, nhưng quy tắc tên khác nhau:

- **`Class.getResource("config.txt")`:** tên không bắt đầu bằng `/` được giải quyết **tương đối với package của class** (`demo/config.txt`).
- **`Class.getResource("/demo/config.txt")`:** dấu `/` đầu chỉ resource tuyệt đối tính từ gốc phạm vi lookup của class, và được bỏ trước khi tra bằng tên resource.
- **`ClassLoader.getResource("demo/config.txt")`:** tên resource luôn dùng dạng đường dẫn tuyệt đối trong namespace của loader **không có `/` đầu**. Dùng `"/demo/config.txt"` với API này thường không tìm thấy resource trong mô hình classpath thông thường.

Đối số `"demo/config.txt"` là **resource name**, không phải URL hay một đường dẫn filesystem tuyệt đối. Nó sử dụng `/` theo quy ước của Java ngay cả trên Windows.

Các đối tượng khác nhau phục vụ nhu cầu khác nhau:

| Đối tượng | Biểu diễn | Dùng khi |
|---|---|---|
| `String` resource name | `demo/config.txt` | Yêu cầu class loader tìm tài nguyên |
| `URL` | `jar:file:/.../app.jar!/demo/config.txt` | Xác định vị trí/cách mở tài nguyên |
| `URI` | `jar:file:/.../app.jar!/demo/config.txt` | Biểu diễn định danh theo cú pháp URI, không tự bảo đảm có filesystem |
| `Path` | Đường dẫn trong **một `FileSystem` cụ thể** | Dùng các API `Files.readString`, `Files.list`, v.v. |
| `File` | Đối tượng đại diện cho tên đường dẫn filesystem mặc định | Dùng legacy APIs yêu cầu `java.io.File` |
| `InputStream` | Luồng byte từ một nguồn đã mở | Đọc nội dung mà **không cần** tệp vật lý |

Một `URL` hợp lệ có thể trỏ tới JAR entry, JDK module image hoặc nguồn khác. Việc gọi `getResource(...)` thành công chỉ cho thấy resource có thể được định vị; nó **không chứng minh** resource có một tệp độc lập trên disk.

## 2. Dạng file: — khi có thể chuyển thành Path

Nếu chương trình chạy với `java -cp classes ...`, resource trong thư mục output thường có URL dạng:

```text
file:/home/user/project/classes/demo/config.txt
```

Khi scheme là `file` và URI phù hợp provider của filesystem hiện tại, cách chuyển nên dùng là:

```java
URL url = App.class.getResource("config.txt");
if (url == null) {
    throw new IllegalStateException("Missing config.txt");
}
if (!"file".equalsIgnoreCase(url.getProtocol())) {
    throw new IllegalStateException("Not a filesystem URL");
}

Path path = Path.of(url.toURI());
String content = Files.readString(path, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa** đặt trong một phương thức có khai báo/nhập `App`, `URL`, `Path`, `Files`, `StandardCharsets` và xử lý các checked exceptions. Với Java 17, `Path.of(URI)` là API phù hợp khi muốn chuyển URI sang `Path` qua filesystem provider hỗ trợ.

**Vì sao không dùng `new File(url.getFile())`?**

Một URL file có thể chứa mã hóa percent-encoding:

```text
file:/home/user/My%20Project/classes/demo/config.txt
```

- `url.getPath()` và `url.getFile()` cung cấp thành phần URL; chuỗi có thể còn `%20` đại diện cho khoảng trắng.
- `new File(url.getFile())` có thể diễn giải `%20` thành ba ký tự `%`, `2`, `0` trong tên file thật, dẫn tới “file not found”.
- `url.toURI()` và `Path.of(uri)` dùng quy tắc URI/filesystem provider để xử lý biểu diễn đường dẫn.
- Không tự thay `"%20"` bằng dấu cách hoặc dùng `URLDecoder.decode(...)` trên **toàn bộ đường dẫn URL**: `URLDecoder` dành cho dữ liệu form/query theo quy tắc riêng (ví dụ dấu `+`), không phải một hàm chuyển mọi URL thành filesystem path.

Trên Windows, URL file có thể mang dạng đại diện cho ổ đĩa hoặc đường UNC; đừng tự bỏ `file:`, tự nối dấu `\` hay suy diễn ký tự ổ đĩa từ chuỗi. Để filesystem provider xử lý qua `URI` và bắt exception nếu URL không được hỗ trợ.

### Những giới hạn ngay cả với file:

- `Path.of(url.toURI())` có thể không dùng được nếu `file:` URI chứa authority/host không được provider hỗ trợ trong môi trường đang chạy.
- Tệp có thể bị xóa/đổi quyền **sau** lúc lấy URL; việc URL hợp lệ không đảm bảo `Files.readString` luôn thành công.
- Một URL file có thể là đường dẫn tới **directory**; `Files.readString` áp dụng cho file nội dung, không có nghĩa mọi path đều có thể đọc như text.
- Với tài nguyên cần ghi, đừng mặc định thư mục `classes`/classpath có thể ghi hoặc thích hợp lưu dữ liệu ứng dụng. Một bản triển khai khác có thể chuyển resource vào JAR.

## 3. Dạng jar: — entry trong archive, không phải file độc lập

Khi đóng gói resource bên trong `app.jar`, cùng resource có thể được tìm thấy dưới URL:

```text
jar:file:/opt/app/app.jar!/demo/config.txt
```

Cú pháp chung của URL JAR theo `JarURLConnection`:

```text
jar:<url-của-JAR>!/<entry-name>
```

Tách các phần:

- `jar:`: giao thức URL chỉ resource thuộc JAR.
- `file:/opt/app/app.jar`: URL của **JAR chứa** tài nguyên; trong một số trường hợp URL JAR bên trong có thể là nguồn không phải local file.
- `!/`: dấu phân cách giữa container JAR và entry.
- `demo/config.txt`: tên entry trong archive.

**Không có một filesystem file thông thường mang tên `demo/config.txt` ngay bên cạnh JAR**. Các byte của entry nằm bên trong archive.

Ví dụ truy cập đúng:

```java
URL url = App.class.getResource("config.txt");
try (InputStream input = url.openStream()) {
    String text = new String(input.readAllBytes(),
            StandardCharsets.UTF_8);
    System.out.println(text);
}
```

Đây là đoạn minh họa yêu cầu xử lý `url == null` và `IOException` trong phương thức gọi; với nội dung nhỏ và đã biết encoding, nó hoạt động cho cả `file:` và `jar:`. `readAllBytes()` tải toàn bộ nội dung vào RAM; với resource lớn, nên đọc theo luồng và đặt giới hạn kích thước phù hợp.

### Vì sao new File(jarURL.toURI()) thất bại?

`java.io.File` chỉ biểu diễn tên đường dẫn trên filesystem mặc định; nó không diễn giải `!/entry` là đường dẫn tệp trong ZIP. Ví dụ:

```java
URL url = App.class.getResource("config.txt");
File file = new File(url.toURI()); // Không đúng cho jar: URL
```

Khi `url` có scheme `jar`, `new File(URI)` ném `IllegalArgumentException` vì constructor này yêu cầu URI theo scheme `file` phù hợp. Việc chuyển đổi chuỗi `url.getFile()` thành `File` cũng không giải quyết được: nó chứa thông tin archive và entry, không phải đường dẫn file bình thường.

**Cẩn thận thêm:** `Path.of(url.toURI())` không phải phép màu tự động biến mọi `jar:` URL thành `Path`. Với `jar` scheme, bạn cần ZIP/JAR filesystem provider và filesystem tương ứng phải được mở. Nếu chưa có filesystem cho JAR hoặc provider không hiện diện, có thể nhận `FileSystemNotFoundException` hay `ProviderNotFoundException`. Vì vậy lời khuyên “dùng `Path.of(url.toURI())` cho tất cả resource URLs” là sai.

### Nếu thật sự cần metadata của JAR

Có thể dùng `JarURLConnection` khi biết URL là `jar:`:

```java
URL url = App.class.getResource("config.txt");
if (url != null && "jar".equals(url.getProtocol())) {
    URLConnection raw = url.openConnection();
    if (raw instanceof JarURLConnection jar) {
        System.out.println(jar.getEntryName());
        System.out.println(jar.getJarFileURL());
    }
}
```

Đây là snippet minh họa với imports `java.net.URL`, `java.net.URLConnection`, `java.net.JarURLConnection` và khả năng ném `IOException`. `getEntryName()` trả tên entry; `getJarFileURL()` trả URL của archive, **không phải** entry. `getJarFileURL()` có thể không dùng scheme file trong mọi môi trường.

`JarURLConnection` cho phép truy cập metadata/manifest/`JarEntry`, nhưng nên chú ý quản lý connection, caching, tài nguyên và lỗi I/O. Nếu chỉ cần nội dung, `openStream()` hoặc `getResourceAsStream` đơn giản hơn.

## 4. Dạng jrt: — tài nguyên trong Java runtime image

Kể từ Java 9, JDK dùng runtime image theo hệ thống module (JEP 220) thay vì một tệp `rt.jar` kiểu JDK 8 cho các lớp nền tảng. Trên các JDK 17+ thông thường, resource của `java.lang.String` có thể có URL như:

```text
jrt:/java.base/java/lang/String.class
```

Nó có nghĩa:

- `jrt:`: giao thức/namespace resource của Java runtime image.
- `java.base`: module chứa class.
- `java/lang/String.class`: đường resource trong module.

Ví dụ:

```java
URL url = String.class.getResource("String.class");
System.out.println(url.getProtocol()); // jrt trên JDK 17 thông thường
```

Đây là snippet đặt trong method có import `java.net.URL` và xử lý `url == null` khi cần. **Đừng viết code yêu cầu mọi Java runtime và custom image luôn trả đúng cùng một chuỗi URL**: cách tài nguyên được tìm thấy phụ thuộc loader, module và runtime implementation. Trong bài thực hành với JDK tiêu chuẩn, `jrt:` là dạng mong đợi.

### jrt: không có nghĩa file vật lý

Dù URL chứa `java/lang/String.class`, đó không phải file thường nằm dưới thư mục `java.base` trên đĩa. Vì vậy:

```java
new File(jrtUrl.toURI()); // Không hợp lệ: scheme không phải file:
```

là sai về mô hình. Giống `jar:`, URL `jrt:` chỉ một nguồn tài nguyên đặc biệt; `java.io.File` không có khả năng đại diện tùy ý cho entry trong runtime image.

Khi chỉ cần đọc, vẫn dùng stream:

```java
URL url = String.class.getResource("String.class");
if (url == null) {
    throw new IllegalStateException("Runtime resource unavailable");
}
try (InputStream input = url.openStream()) {
    byte[] firstBytes = input.readNBytes(4);
    // Đọc bytecode class từ runtime resource khi được cung cấp.
}
```

Đây là snippet minh họa, không tự biên dịch nếu thiếu các imports/phương thức xử lý exception. Với bytecode chuẩn, 4 byte đầu thường là magic `CA FE BA BE`, nhưng đọc stream resource chỉ nên dùng để minh họa, không thay cho API reflection khi cần metadata của class.

### JRT FileSystem khác File

JDK có thể cung cấp provider **JRT FileSystem** của NIO. Khi provider này khả dụng, bạn có thể thao tác với `Path` thuộc filesystem `jrt:`:

```java
URI uri = URI.create(
        "jrt:/java.base/java/lang/String.class");

Path path = Path.of(uri);
byte[] bytes = Files.readAllBytes(path);
```

Đây là đoạn minh họa cho JDK hỗ trợ `jrt:` provider. `path` là đường dẫn **trên filesystem ảo của runtime image**, không phải đường dẫn `File` trên filesystem mặc định. Đừng chuyển nó thành `path.toFile()` và kỳ vọng thành công; NIO `Path.toFile()` có thể ném `UnsupportedOperationException` với filesystem không phải default provider.

Với custom runtime tối giản hoặc môi trường không có provider phù hợp, `Path.of(jrtUri)` có thể thất bại. Khi chỉ đọc resource của class hiện có, cách ít phụ thuộc hơn vẫn là `getResourceAsStream` hoặc `URL.openStream`.

## 5. Thí nghiệm Java 17+: cùng một resource tạo ra file: hoặc jar:

Ví dụ dưới đây tạo **một chương trình Java duy nhất** và một tệp resource. Chạy lần thứ nhất với thư mục `classes`; chạy lần thứ hai bằng `app.jar`. Nội dung resource không đổi, nhưng protocol URL khác vì cấu trúc triển khai khác.

Tạo cây:

```text
resource-demo/
├── src/
│   └── demo/
│       └── ResourceSchemesDemo.java
└── classes/
    └── demo/
        └── data/
            └── message.txt
```

Nội dung **`src/demo/ResourceSchemesDemo.java`**:

```java
package demo;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceSchemesDemo {
    public static void main(String[] args) throws Exception {
        URL resource = ResourceSchemesDemo.class
                .getResource("data/message.txt");
        if (resource == null) {
            throw new IllegalStateException("Missing demo resource");
        }

        URL runtime = String.class.getResource("String.class");
        if (runtime == null) {
            throw new IllegalStateException("Missing runtime class resource");
        }

        System.out.println("Application protocol: "
                + resource.getProtocol());
        System.out.println("Runtime protocol: "
                + runtime.getProtocol());

        try (InputStream stream = resource.openStream()) {
            String message = new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Message: " + message.trim());
        }

        if ("file".equals(resource.getProtocol())) {
            Path file = Path.of(resource.toURI());
            System.out.println("File exists: "
                    + Files.exists(file));
        } else if ("jar".equals(resource.getProtocol())) {
            JarURLConnection connection =
                    (JarURLConnection) resource.openConnection();
            System.out.println("JAR entry: "
                    + connection.getEntryName());
        }
    }
}
```

Trên **Bash** (Linux/macOS/Git Bash), trong thư mục `resource-demo`, tạo tệp, biên dịch và chạy:

```bash
mkdir -p classes/demo/data
printf 'Hello resource!\n' > classes/demo/data/message.txt

javac --release 17 -d classes src/demo/ResourceSchemesDemo.java
java -cp classes demo.ResourceSchemesDemo
```

Đầu ra trên JDK 17+ tiêu chuẩn khi chạy từ thư mục:

```text
Application protocol: file
Runtime protocol: jrt
Message: Hello resource!
File exists: true
```

Đóng gói rồi chạy chương trình từ JAR:

```bash
jar --create --file app.jar -C classes .
java -cp app.jar demo.ResourceSchemesDemo
```

Đầu ra:

```text
Application protocol: jar
Runtime protocol: jrt
Message: Hello resource!
JAR entry: demo/data/message.txt
```

Đây là **ví dụ đã kiểm chứng bằng cách biên dịch với `javac --release 17` và chạy hai chế độ** trên JDK có JRT. Lệnh `jar --create` đưa cả `demo/ResourceSchemesDemo.class` và `demo/data/message.txt` vào archive.

Vì không khai báo `Main-Class` trong manifest, bài thử **dùng `java -cp app.jar demo.ResourceSchemesDemo`**, không dùng `java -jar app.jar`. `java -jar` là chế độ khác và cần entry point trong manifest.

Nếu chạy trong môi trường đã có một resource cùng tên ở vị trí ưu tiên khác trên classpath, một custom class loader hoặc packaging đặc biệt, URL có thể khác. Hãy quan sát `getProtocol()` thay vì gắn cứng xử lý dựa trên tên file.

### Chạy trên PowerShell

Đối với Windows PowerShell, thay hai dòng tạo thư mục và tệp bằng:

```powershell
New-Item -ItemType Directory -Force -Path classes/demo/data | Out-Null
[System.IO.File]::WriteAllText(
    "classes/demo/data/message.txt",
    "Hello resource!" + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
```

Sau đó chạy cùng lệnh `javac`, `java`, `jar` như phần Bash. Đường dẫn `classes/demo/data` được Java và công cụ JDK hiểu trên Windows; phần code Java luôn dùng `/` trong resource name.

## 6. Quy tắc an toàn: đọc resource từ stream, không giả định File

Một method tái sử dụng được cho classpath resource nhỏ:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResourceText {
    private ResourceText() {
    }

    public static String read(String name) throws IOException {
        try (InputStream input =
                ResourceText.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Resource not found: " + name);
            }
            return new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
```

Tệp **`ResourceText.java`** này có thể biên dịch bằng:

```bash
javac --release 17 ResourceText.java
```

Nó là utility class **không chứa `main`**, nên không phải chương trình độc lập có stdout. Caller có thể dùng `ResourceText.read("/demo/data/message.txt")` nếu resource đó nằm trong classpath/module và có thể truy cập theo các quy tắc tương ứng.

Ưu điểm:

- Không cần hỏi nguồn là `file:` hay `jar:`.
- Không cần trích xuất resource ra thư mục tạm.
- Khi đổi cách packaging từ `classes` sang JAR, code đọc resource giữ nguyên.
- `try-with-resources` đảm bảo đóng stream khi đọc xong.

Hạn chế:

- `readAllBytes()` không phù hợp với resource rất lớn hoặc không tin cậy; dùng đọc theo buffer/stream và giới hạn dung lượng.
- `getResourceAsStream` trả `null` nếu không tìm được/không được phép truy cập; phải kiểm tra.
- Nếu cần chỉnh sửa dữ liệu, resource nhúng trong JAR không nên được dùng làm nơi lưu. Hãy chọn một external `Path` (file cấu hình, thư mục dữ liệu, database...) rồi đọc/ghi qua `Files`.
- Với tài nguyên remote hoặc loader riêng, việc mở stream có thể có lỗi I/O/network; cần chính sách timeout, logging và bảo vệ nguồn đầu vào phù hợp.

Một quy tắc thực tế: **classpath resource chủ yếu là tài sản đọc-only của ứng dụng** (template, schema, ảnh, cấu hình mặc định). Dữ liệu người dùng hoặc state ứng dụng nên được quản lý riêng khỏi JAR.

## 7. Nếu buộc phải có Path bên trong JAR: ZIP FileSystem

Java NIO hỗ trợ filesystem provider cho ZIP/JAR thông qua module `jdk.zipfs` trong các JDK có cung cấp nó. Khi cần dùng các API `Files` với entry bên trong JAR, có thể chủ động **mở ZIP filesystem** thay vì ép resource URL thành `File`.

Ví dụ minh họa (archive là local file):

```java
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ZipFsExample {
    public static void main(String[] args) throws Exception {
        Path archive = Path.of("app.jar").toAbsolutePath();
        URI zipUri = URI.create("jar:" + archive.toUri());

        try (FileSystem fs =
                     FileSystems.newFileSystem(zipUri, Map.of())) {
            Path entry = fs.getPath("/demo/data/message.txt");
            System.out.println(Files.readString(entry).trim());
        }
    }
}
```

Đây là ví dụ bổ sung **có `main`**, yêu cầu có `app.jar` từ thí nghiệm trước, `jdk.zipfs` có sẵn và entry tương ứng tồn tại. Biên dịch bằng `javac --release 17 ZipFsExample.java`, chạy `java ZipFsExample` từ thư mục có `app.jar` sẽ in:

```text
Hello resource!
```

Đặc điểm:

- `FileSystems.newFileSystem` tạo/mở **filesystem ảo** gắn với archive. `entry` là `Path` **bên trong ZIP filesystem**, không phải `File` của hệ điều hành.
- Cần đóng `FileSystem` khi xong, và lưu ý lifecycle/caching khi nhiều thành phần dùng cùng một archive. Đóng filesystem trong khi code khác còn dùng `Path` có thể dẫn tới lỗi.
- Không tự động hỗ trợ mọi scheme `jar:` (ví dụ URL của JAR remote, nested JAR theo framework riêng). Với các trường hợp đó, các giao thức/loader chuyên dụng cần xử lý riêng.
- Trích xuất entry ra file tạm cũng là phương án nếu thư viện bắt buộc nhận `File`, nhưng phải quản lý vòng đời và quyền truy cập file tạm, không nhầm vị trí đó là nguồn resource gốc.

Với nhu cầu chỉ đọc vài byte hoặc parse text/JSON, **stream vẫn đơn giản hơn ZipFileSystem**.

## 8. Khi thư viện bắt buộc nhận File thì làm gì?

Một số API cũ yêu cầu tham số `java.io.File` thay vì `InputStream`. Có ba phương án:

| Nhu cầu | Cách triển khai |
|---|---|
| Có thể sửa API hoặc chọn overload khác | Ưu tiên API nhận `InputStream`, `Reader`, `URL` hoặc `ByteBuffer` |
| URL thực sự là local `file:` | Kiểm tra scheme, chuyển `url.toURI()` → `Path.of(uri)` → `path.toFile()` trên filesystem mặc định |
| Resource nằm trong JAR/JRT nhưng API chỉ nhận `File` | Tạo bản sao ra file tạm có quản lý, hoặc chuyển sang thư viện nhận stream; không ép URL đặc biệt thành `File` |

Ví dụ copy ra file tạm:

```java
URL resource = MyApp.class.getResource("/assets/report-template.bin");
if (resource == null) {
    throw new IllegalStateException("Missing template");
}

Path temp = Files.createTempFile("report-template-", ".bin");
try {
    try (InputStream in = resource.openStream()) {
        Files.copy(in, temp,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    legacyApiThatRequiresFile(temp.toFile());
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là **đoạn minh họa**, không độc lập vì cần `MyApp`, `legacyApiThatRequiresFile(...)` và các imports phù hợp. Trong production:

- Nếu legacy API giữ file sau khi method trả về, **không xóa quá sớm**.
- Chú ý quyền đọc file tạm, dữ liệu bí mật và cleanup kể cả khi có exception.
- Không để tên file tạm có thể bị dự đoán/ghi đè bởi nguồn không tin cậy.
- Với tài nguyên rất lớn, cân nhắc streaming và giới hạn dung lượng.
- Việc dùng `deleteOnExit()` không thay thế quản lý file temp dài hạn ở server process.

## 9. Ngoại lệ và lỗi hay gặp

| Hiện tượng | Nguyên nhân điển hình | Cách khắc phục |
|---|---|---|
| `getResource(...) == null` | Tên resource sai, sai prefix `/`, resource không đóng gói, module access | Kiểm tra name, loader và entry thực tế |
| `new File(url.toURI())` ném `IllegalArgumentException` | URL là `jar:` hoặc `jrt:`, không phải `file:` | Dùng stream hoặc provider phù hợp |
| `Path.of(jarUri)` ném `FileSystemNotFoundException` | Chưa mở JAR filesystem cho URI | Mở ZIP filesystem có quản lý hoặc đọc qua stream |
| File tồn tại nhưng `Files.readString` báo không tìm thấy | Dùng `new File(url.getFile())` với `%20`/URL-encoding, path sai | Dùng `Path.of(url.toURI())` cho `file:` URI |
| `FileSystemProviderNotFoundException` / `ProviderNotFoundException` | Provider cho scheme cần dùng không hiện diện | Kiểm tra runtime image/module/provider; fallback stream |
| `UnsupportedOperationException` từ `Path.toFile()` | Path thuộc filesystem ảo không phải filesystem mặc định | Dùng `Files` trên Path, không buộc thành File |
| `IOException` khi `openStream()` | Tài nguyên không đọc được, archive lỗi, remote I/O, permission | Xử lý I/O và đóng stream, không tự giả định thiếu class |
| Trên IDE chạy được, đóng JAR lại lỗi | Resource không được copy vào JAR, code giả định file trên disk | Kiểm tra archive, thay đọc File bằng stream |
| `jrt:` không truy cập được trên môi trường tối giản | Provider/resource/runtime image khác biệt | Dùng APIs cho module/resource phù hợp, tránh hardcode filesystem |

*Ghi chú tên lỗi:* Với Java 17, lớp exception về filesystem provider tên là `java.nio.file.ProviderNotFoundException`. Hãy dùng đúng tên này trong code; cụm `FileSystemProviderNotFoundException` đôi khi chỉ là cách diễn giải không chính thức, **không phải tên một exception class chuẩn**.

## 10. Một số trường hợp đặc biệt trong hệ sinh thái Java

### Named modules và resource encapsulation

Trên Java 9+, class loader resource lookup còn bị chi phối bởi **module encapsulation**. Với `ClassLoader.getResource`, tài nguyên **không kết thúc bằng `.class`** trong package của một named module thường chỉ có thể được trả về khi package được `opens` vô điều kiện, theo quy tắc API. Do đó `null` không luôn có nghĩa file không tồn tại.

`Class.getResource` và `Module.getResourceAsStream` cũng có những quy tắc tương ứng về resource trong module. Khi cần tài nguyên nội bộ của module, hãy cân nhắc API phù hợp và các quyền truy cập được quy định; không cố sửa bằng cách coi `jrt:` URL là đường dẫn file vật lý.

### Nested JAR và framework packaging

Một ứng dụng đóng gói theo framework có thể chứa JAR lồng bên trong, ví dụ `BOOT-INF/lib/dependency.jar`. URL do custom class loader trả về có thể là dạng đặc biệt hoặc cấu trúc phức tạp hơn `jar:file:...!/entry` thông thường.

Đừng viết parser URL thủ công kiểu:

```java
String path = url.toString().split("!/")[0];
```

Cách này bỏ qua URL encoding, multiple layers, nguồn remote và khác biệt giữa loader implementations. Nếu chỉ đọc tài nguyên, dùng `openStream()`. Nếu cần xác định archive thật, sử dụng API chính thức của framework/loader hoặc `JarURLConnection` khi URL đúng giao thức JAR chuẩn.

### HTTP, dữ liệu sinh động và resource không phải file

Không phải mọi loader chỉ đọc từ filesystem. Loader tùy biến có thể trả `http:`, `https:` hoặc protocol khác. Trong trường hợp đó, tạo `File` là vô nghĩa. URL protocol cũng không có cam kết nhất quán giữa các class loader plugins; resource có thể được sinh trong bộ nhớ hoặc cung cấp qua API riêng.

### Phân biệt class resource và đường đến JAR đang chạy

`MyApp.class.getResource("MyApp.class")` thường trả URL chỉ tới **entry .class** hoặc file .class cụ thể, không phải đường filesystem chứa ứng dụng. Nếu bạn muốn nơi lưu dữ liệu bên cạnh chương trình đang chạy, đừng bắt đầu bằng cách cắt chuỗi resource URL: cách đóng gói và class loader có thể thay đổi. Hãy dùng cấu hình ngoài, working directory có kiểm soát hoặc API deployment chính thức.

`ProtectionDomain.getCodeSource().getLocation()` đôi khi cho URL của JAR hoặc thư mục nguồn nạp code, nhưng có thể `null` hoặc không phản ánh ý nghĩa deployment bạn cần, đặc biệt với module system/custom loaders. Nó không thay thế một cấu hình rõ ràng cho đường dẫn dữ liệu.

## 11. Checklist chọn API đúng

Khi xử lý một resource, tự hỏi:

1. **Mình muốn đọc nội dung hay cần đường dẫn filesystem?** Nếu đọc, dùng `getResourceAsStream`/`openStream`.
2. **Tên tài nguyên đúng kiểu chưa?** `Class.getResource` có tương đối package; `ClassLoader.getResource` yêu cầu tên không có `/` đầu.
3. **Resource có thể là null không?** Luôn kiểm tra và báo lỗi dễ hiểu.
4. **Scheme thực tế là gì?** Quan sát `url.getProtocol()`, không suy đoán từ IDE hoặc tên file.
5. **Nếu là `file:`**, đã chuyển `url.toURI()` sang `Path` qua provider tương ứng, thay vì tự decode chuỗi?
6. **Nếu là `jar:`**, có thật cần `Path` không? Stream có thể đủ; ZIP filesystem chỉ dùng khi API cần thao tác với entries.
7. **Nếu là `jrt:`**, có hiểu đó là runtime image ảo, không phải `rt.jar` hoặc file vật lý?
8. **Nếu dùng JPMS**, package/resource có được phép truy cập qua API đang gọi không?
9. **Nếu cần File cho thư viện cũ**, đã quản lý file tạm, bảo mật và cleanup phù hợp?
10. **Đã kiểm tra cả chạy từ thư mục và chạy từ JAR chưa?** Hai cấu hình thường phơi bày các giả định sai về đường dẫn.

## 12. Tự kiểm tra kiến thức

**1. `getResource("config.txt")` trả `URL` có nghĩa resource là một file thật trên disk?** Không. URL có thể là `file:`, `jar:`, `jrt:` hoặc scheme khác.

**2. Vì sao `new File(url.getFile())` không nên dùng ngay với file URL có khoảng trắng?** Vì thành phần URL có thể chứa `%20`, không tương đương tên filesystem đã được giải mã đúng.

**3. URL `jar:file:/app/app.jar!/demo/config.txt` trỏ tới đâu?** Entry `demo/config.txt` **bên trong** archive `app.jar`. Không có file thông thường độc lập ở đường dẫn đó.

**4. `new File(jarUrl.toURI())` có hợp lệ không?** Không cho JAR URI: constructor `File(URI)` yêu cầu file URI phù hợp.

**5. `Path.of(jrtUri)` có thể sử dụng trong JDK hỗ trợ `jrt:` provider không?** Có, nhưng đó là `Path` thuộc JRT filesystem ảo; không mặc nhiên đổi được sang `java.io.File`.

**6. Tại sao đọc một resource bằng stream tốt hơn việc ép thành File?** Stream dùng được với nhiều protocol, đóng gói JAR hoặc thư mục, giảm coupling với filesystem layout.

**7. `ClassLoader.getResource("/demo/config.txt")` có giống `Class.getResource("/demo/config.txt")` không?** Không. `ClassLoader.getResource` dùng tên resource không bắt đầu `/`; `Class.getResource` có quy tắc xử lý `/` đầu riêng.

**8. Khi resource nằm trong named module, `getResource` trả null có chắc resource bị thiếu?** Không. Có thể do quy tắc module encapsulation hoặc quyền truy cập resource.

**9. Tại sao IDE chạy được nhưng executable JAR báo lỗi FileNotFoundException?** Code có thể đang dùng `new File(...)` giả định resource được triển khai dưới dạng filesystem file, trong khi sau packaging nó thành JAR entry.

**10. Nếu thư viện bắt buộc nhận File với resource nhúng JAR, cách tiếp cận hợp lý là gì?** Ưu tiên overload nhận stream nếu có; nếu không có, copy resource vào file tạm với quản lý vòng đời và bảo mật phù hợp.

## 13. Tài liệu chuẩn

- Java SE 17 API, `ClassLoader.getResource/getResourceAsStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `Class.getResource/getResourceAsStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 API, `JarURLConnection` (cú pháp URL `jar:` và metadata): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/JarURLConnection.html
- Java SE 17 API, `File(URI)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html
- Java SE 17 API, `Path.of(URI)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html
- Java SE 17 API, `FileSystems` và ZIP filesystem provider: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystems.html
- JEP 220, *Modular Run-Time Images* (thay đổi từ `rt.jar` sang runtime image): https://openjdk.org/jeps/220

**Ghi nhớ:** `file:` thường có thể chuyển thành `Path` trên filesystem tương ứng; `jar:` chỉ một entry trong archive; `jrt:` chỉ nội dung runtime image theo module. **URL resource không đồng nghĩa với `File`.** Khi chỉ cần đọc nội dung, hãy dùng stream để code tiếp tục hoạt động khi ứng dụng đổi từ thư mục classes sang JAR hoặc Java runtime module.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-009">Đọc tài nguyên trong JAR và đóng InputStream đúng cách</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc tài nguyên trong JAR và đóng InputStream đúng cách

> **Phạm vi:** Java Core → ClassLoader → Tài nguyên. Mục tiêu là đọc văn bản, cấu hình và dữ liệu nhị phân đã đóng gói trong JAR bằng resource API, phân biệt tên tài nguyên tương đối/tuyệt đối, xử lý `null` và lỗi I/O, sử dụng `try-with-resources` để đóng `InputStream`, đồng thời tránh phụ thuộc vào đường dẫn file vật lý. Mã minh họa dùng Java 17+.

## 1. Vì sao đọc tài nguyên trong JAR khác đọc file trên ổ đĩa?

Trong giai đoạn phát triển, ta có thể đặt `config.properties` hoặc `message.txt` ở thư mục dự án và đọc được bằng `Files.readString(Path.of(...))`. Nhưng sau khi build thành một JAR:

```text
demo.jar
├── META-INF/
│   └── MANIFEST.MF
└── demo/
    ├── JarResourceDemo.class
    └── message.txt
```

`demo/message.txt` là **entry bên trong archive**, không phải file độc lập có đường dẫn OS kiểu `/home/app/demo/message.txt` hoặc `C:\app\demo\message.txt`. Khi chạy `java -cp demo.jar demo.JarResourceDemo`, JVM/ClassLoader có thể tìm entry này bằng tên resource và trả một `InputStream` để đọc bytes.

Phân biệt hai câu hỏi:

1. **Tìm resource ở đâu?** Đây là trách nhiệm của `Class.getResource...`, `ClassLoader.getResource...`, module resource API hoặc cơ chế loader chuyên biệt.
2. **Đọc resource và quản lý vòng đời thế nào?** Đây là trách nhiệm của mã sử dụng `InputStream`, charset/decoder, kiểm tra lỗi và **đóng stream**.

Hướng tiếp cận di động là **resource name → InputStream → đọc dữ liệu → close**. Đừng biến một JAR resource thành `File` chỉ để dùng API của hệ thống file nếu bài toán chỉ là đọc nội dung.

## 2. Chọn API phù hợp để đọc entry trong JAR

Khi mục tiêu là **đọc bytes và đóng stream**, bắt đầu từ API đưa thẳng tới `InputStream`:

| Trường hợp | API phù hợp | Điều cần kiểm tra |
| --- | --- | --- |
| Resource nằm cạnh class `MyClass` | `MyClass.class.getResourceAsStream("data.txt")` | Tên tính từ package của class |
| Resource được gọi bằng tên từ root | `MyClass.class.getResourceAsStream("/demo/data.txt")` | Dấu `/` đầu thuộc quy tắc `Class` |
| Đã chọn một loader cụ thể | `loader.getResourceAsStream("demo/data.txt")` | Không thêm `/` đầu; loader có thể không thấy resource |
| Cần mở dữ liệu từ URL đã tìm được | `url.openStream()` | Kiểm tra URL không `null`, xử lý `IOException` |

Cả `Class.getResourceAsStream` và `ClassLoader.getResourceAsStream` có thể trả `null`: resource thiếu, tên sai, loader khác hoặc giới hạn module đều cần được xét. Nếu cần **liệt kê** nhiều bản cùng tên, dùng `loader.getResources(name)` rồi mở và đóng stream từ từng URL. Với named module, `Module.getResourceAsStream(name)` có chính sách `opens` riêng; không suy ra khả năng truy cập của nó từ kết quả `ClassLoader`.

Tìm resource không đồng nghĩa nạp một class hay chạy static initializer. Trong JAR, entry không phải `File` độc lập trên đĩa; với bài toán chỉ đọc nội dung, ưu tiên dùng stream.

## 3. Đường dẫn: relative, absolute và dấu gạch chéo

Giả sử class gọi API là `demo.JarResourceDemo`, resource ở entry `demo/message.txt`.

**Với `Class` API:**

```java
// Tìm demo/message.txt: tên tương đối theo package "demo"
JarResourceDemo.class.getResourceAsStream("message.txt");

// Tìm demo/message.txt: dấu "/" đầu là tuyệt đối từ root
JarResourceDemo.class.getResourceAsStream("/demo/message.txt");

// Tìm message.txt ngay ở root archive/classpath, KHÔNG phải demo/message.txt
JarResourceDemo.class.getResourceAsStream("/message.txt");
```

**Với `ClassLoader` API:**

```java
ClassLoader loader = JarResourceDemo.class.getClassLoader();

// Đúng: tên từ resource root
loader.getResourceAsStream("demo/message.txt");

// Không dùng "/" đầu với ClassLoader API
loader.getResourceAsStream("/demo/message.txt");
```

Các snippet trên phải nằm trong code có class `demo.JarResourceDemo`, ví dụ một method của chính lớp đó; chúng minh họa **quy tắc lookup**, không phải chương trình Java độc lập.

Lý do: `Class.getResource...` khi tên không bắt đầu `/` sẽ thêm package prefix (thay dấu `.` thành `/`). Nếu tên bắt đầu `/`, nó bỏ dấu `/` trước khi tìm. Trong khi đó, `ClassLoader.getResource...` nhận tên đã tính từ root; **không có quy tắc tự loại bỏ dấu `/`** giống như `Class` API.

Bảng tóm tắt:

| Lời gọi từ `demo.JarResourceDemo` | Tên thực tìm |
| --- | --- |
| `getResourceAsStream("message.txt")` | `demo/message.txt` |
| `getResourceAsStream("/demo/message.txt")` | `demo/message.txt` |
| `getResourceAsStream("/message.txt")` | `message.txt` |
| `getClassLoader().getResourceAsStream("demo/message.txt")` | `demo/message.txt` |

Trong JAR, entry names dùng dấu `/`, thường **phân biệt hoa/thường**. Đừng dùng dấu `\` làm package separator hoặc tự biến một resource name thành đường dẫn Windows. Đừng đặt `src/main/resources` vào tên runtime nếu build tool đã copy nội dung thư mục đó làm classpath root.

## 4. Bài thực hành độc lập: đóng gói, đọc và đóng stream

Ví dụ được thiết kế để **chạy thật từ JAR**, không chỉ chạy từ IDE. Chỉ cần JDK 17+ và các lệnh `javac`, `jar`, `java`.

### Cấu trúc source

```text
src/
  demo/
    JarResourceDemo.java
resources/
  demo/
    message.txt
classes/           (được javac tạo)
demo.jar           (được jar tạo)
```

**File `src/demo/JarResourceDemo.java`:**

```java
package demo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class JarResourceDemo {
    private static String readText(InputStream input) throws IOException {
        if (input == null) {
            throw new FileNotFoundException("demo/message.txt");
        }

        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .stripTrailing();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("relative: " + readText(
                JarResourceDemo.class.getResourceAsStream("message.txt")));

        System.out.println("absolute: " + readText(
                JarResourceDemo.class.getResourceAsStream("/demo/message.txt")));

        System.out.println("loader: " + readText(
                JarResourceDemo.class.getClassLoader()
                        .getResourceAsStream("demo/message.txt")));

        URL url = JarResourceDemo.class.getResource("message.txt");
        System.out.println("protocol: " + url.getProtocol());
        System.out.println("missing: "
                + (JarResourceDemo.class.getResourceAsStream("missing.txt")
                        == null));
    }
}
```

**File `resources/demo/message.txt` (UTF-8):**

```text
Hello from inside JAR
```

### Biên dịch và đóng gói

Tại thư mục gốc chứa `src` và `resources`:

```bash
javac -d classes src/demo/JarResourceDemo.java
jar --create --file demo.jar -C classes . -C resources .
jar --list --file demo.jar
java -cp demo.jar demo.JarResourceDemo
```

Đối với Windows PowerShell/CMD, ví dụ này cũng dùng được với dấu `/` trong đối số của các công cụ JDK. Nếu muốn kiểm tra nội dung, `jar --list --file demo.jar` sẽ có các entry `demo/JarResourceDemo.class` và `demo/message.txt`; danh sách đầy đủ còn có manifest/directory entries nên không hardcode toàn bộ output của `jar`.

**Stdout của lệnh `java -cp demo.jar demo.JarResourceDemo` đã được kiểm tra bằng OpenJDK 21:**

```text
relative: Hello from inside JAR
absolute: Hello from inside JAR
loader: Hello from inside JAR
protocol: jar
missing: true
```

Điểm đáng chú ý:

- `java -cp demo.jar`: ứng dụng lấy cả class và resource từ cùng archive.
- Ba API lookup đầu đều tìm đúng entry `demo/message.txt` theo ba cách khác nhau.
- `try (input)`, được Java 9+ hỗ trợ với biến tham số **effectively final**, đóng stream khi rời block, kể cả khi đọc phát sinh `IOException`.
- `input.readAllBytes()` đọc bytes; `new String(..., UTF_8)` giải mã văn bản bằng **charset xác định**. Không phụ thuộc system-default encoding.
- `stripTrailing()` chỉ dùng trong ví dụ để bỏ newline cuối file cho output ổn định; ứng dụng thật không nên tự strip/trim khi khoảng trắng cuối là dữ liệu có ý nghĩa.
- `protocol: jar` cho thấy URL nhận được thuộc dạng `jar:...!/demo/message.txt` khi chạy từ JAR. Phần đường dẫn thực tế không cố định giữa máy.
- `missing: true` minh họa resource không có thì API trả `null`, thay vì tự ném `FileNotFoundException` tại bước lookup. Hàm `readText` chủ động ném checked exception có nghĩa rõ ràng trong trường hợp resource bắt buộc bị thiếu.

**Tại sao không có `try-with-resources` trong `main`?** Vì helper `readText` **nhận quyền sở hữu** stream mà caller tạo, đóng stream bên trong rồi trả `String` (không trả một stream còn mở). Đây là một hợp đồng thiết kế có chủ đích; phương thức công khai phải nói rõ ai chịu trách nhiệm close, tránh đóng hai lần hoặc giữ stream rò rỉ. Phần 5 đưa mẫu thường gặp, nơi chính caller mở và đóng resource.

## 5. Mẫu ưu tiên: try-with-resources ngay tại nơi sử dụng

Nếu không cần helper nhận ownership, nên viết trực tiếp:

```java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Bên trong method Java có throws IOException:
String name = "/demo/message.txt";

try (InputStream in = MyClass.class.getResourceAsStream(name)) {
    if (in == null) {
        throw new FileNotFoundException("Classpath resource: " + name);
    }

    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    System.out.println(text);
}
```

`MyClass` là class minh họa cần thay bằng class thật của bạn; đoạn code trên **không là một chương trình độc lập**. `try-with-resources` chấp nhận giá trị resource là `null`: nếu `null` thì không có đối tượng nào để đóng. Ta vẫn cần kiểm tra `null` **trước khi gọi `readAllBytes()`**, bởi vì nếu không, lỗi quan sát được sẽ là `NullPointerException` thiếu ngữ cảnh.

Với API truy cập bằng class loader:

```java
// Bên trong method có tham chiếu tới Class<?> owner
ClassLoader loader = owner.getClassLoader();
if (loader == null) {
    // owner có thể được định nghĩa bởi bootstrap loader;
    // cân nhắc ClassLoader.getSystemResourceAsStream(...) hoặc API Class.
}

try (InputStream in =
             owner.getResourceAsStream("/demo/message.txt")) {
    if (in == null) {
        throw new FileNotFoundException("demo/message.txt");
    }
    // Đọc dữ liệu rồi tự động đóng InputStream.
}
```

Ở đây `owner` được giả định là một biến `Class<?>` do caller truyền; snippet không độc lập. Không gọi `loader.getResourceAsStream(...)` nếu `loader == null`. **Dùng `owner.getResourceAsStream`** thường đơn giản hơn, đặc biệt khi muốn gắn lookup với class cụ thể và không muốn đoán loader toàn cục.

### Điều gì xảy ra khi có exception?

`try-with-resources`:

1. Lấy stream cần sử dụng.
2. Chạy phần thân `try`.
3. Gọi `close()` **khi thoát khỏi block**, dù code thành công hay ném exception.
4. Nếu phần thân và `close()` cùng ném exception, lỗi của close thường được lưu như **suppressed exception** của lỗi chính.

Không cần gọi `close()` thủ công trước mỗi `return` hay trong cả `catch` và `finally`. Nếu xây API trả `InputStream` cho caller, **caller** phải nhận trách nhiệm đóng; nếu đã đóng bên trong method thì không thể trả stream đã đóng rồi mong caller tiếp tục đọc.

`IOException` có thể phát sinh khi đọc **hoặc** đóng. Đừng viết `catch (IOException ignored) {}` để che lỗi khi file cấu hình quan trọng đã bị cắt/đọc không hoàn chỉnh.

## 6. Chọn cách đọc theo dữ liệu: text, binary, lớn hay nhỏ

### Văn bản nhỏ, UTF-8 rõ ràng

`InputStream.readAllBytes()` (Java 9+) thích hợp với **resource nhỏ có kích thước đã biết/được kiểm soát**.

```java
try (InputStream in =
        MyClass.class.getResourceAsStream("/demo/settings.json")) {
    if (in == null) {
        throw new FileNotFoundException("demo/settings.json");
    }
    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
```

Đoạn này là snippet trong method có `throws IOException` và class `MyClass` phù hợp. Nó chỉ đọc chuỗi JSON, **không parse/validate JSON**.

### Đọc văn bản bằng Reader có chỉ định charset

Khi cần đọc theo dòng hoặc giảm bộ nhớ tạm so với `readAllBytes()`:

```java
try (InputStream in =
             MyClass.class.getResourceAsStream("/demo/words.txt")) {
    if (in == null) {
        throw new FileNotFoundException("demo/words.txt");
    }

    try (var reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
        for (String line; (line = reader.readLine()) != null; ) {
            System.out.println(line);
        }
    }
}
```

Đây là snippet không độc lập. `BufferedReader` giúp đọc từng dòng; đóng reader cũng đóng stream bên dưới. Hai lớp `try-with-resources` cùng quản lý stream có thể làm `close()` được gọi lặp lại; `InputStream` thông thường xử lý được, nhưng **thiết kế sạch hơn** là khai báo một reader trong try-with-resources sau khi đã kiểm tra stream mở, hoặc chỉ một owner có trách nhiệm đóng. Ví dụ dưới đây rõ ràng hơn khi cần reader:

```java
InputStream raw = MyClass.class.getResourceAsStream("/demo/words.txt");
if (raw == null) {
    throw new FileNotFoundException("demo/words.txt");
}

try (var reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(raw, StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

`reader.close()` tự đóng `InputStreamReader` rồi `raw`. Mọi snippet ở đây cần ngữ cảnh import hoặc tên đầy đủ và xử lý `IOException`.

### Dữ liệu nhị phân: không ép thành String

Với ảnh hoặc dữ liệu nhị phân, giữ bytes nguyên vẹn:

```java
try (InputStream in =
             MyClass.class.getResourceAsStream("/demo/logo.png")) {
    if (in == null) {
        throw new FileNotFoundException("demo/logo.png");
    }
    byte[] image = in.readAllBytes();
    // Đưa bytes cho decoder/consumer nhị phân thích hợp.
}
```

Đừng dùng `new String(bytes, UTF_8)` để làm bước trung gian cho ảnh: quá trình decode/encode có thể thay đổi dữ liệu không phải text.

### Resource lớn: đọc từng phần

`readAllBytes()` có thể tiêu tốn rất nhiều bộ nhớ, nhất là khi input kích thước không tin cậy. Với dữ liệu lớn có thể dùng mảng đệm, giới hạn tổng bytes, hoặc `transferTo(OutputStream)` nếu phù hợp luồng xử lý:

```java
try (InputStream in =
             MyClass.class.getResourceAsStream("/demo/large.bin")) {
    if (in == null) {
        throw new FileNotFoundException("demo/large.bin");
    }

    byte[] buffer = new byte[8192];
    long total = 0;
    int count;
    while ((count = in.read(buffer)) != -1) {
        total += count;
        if (total > 50_000_000L) {
            throw new IOException("Resource exceeds permitted limit");
        }
        // Xử lý đúng buffer[0..count), không dùng toàn bộ 8192 byte.
    }
}
```

Đoạn này chỉ minh họa cơ chế giới hạn và stream consumption; với nguồn không tin cậy, quy trình còn cần giới hạn tổng dung lượng thực tế **trước khi thực hiện tác vụ tốn kém**, không chỉ đếm sau khi đọc. Chú ý `InputStream.available()` **không phải** kích thước file: API chỉ cho số byte ước lượng có thể đọc không chặn, không dùng để cấp phát mảng chính xác.

## 7. Vì sao không nên ép URL trong JAR thành java.io.File?

Thao tác có vẻ hợp lý:

```java
URL url = MyClass.class.getResource("/demo/message.txt");
java.io.File file = new java.io.File(url.toURI());
```

Đoạn trên là snippet minh họa, cần xử lý `URISyntaxException` và kiểm tra `url != null`. Nó có thể hoạt động khi resource là một file trực tiếp trong thư mục output (URL scheme `file:`) nhưng **thường không hoạt động khi nằm trong JAR** (URL scheme `jar:`). `File`/`Path.of(URI)` mặc định cần URI có filesystem provider phù hợp; `jar:file:/...!/demo/message.txt` không tự biến thành file thực trong filesystem mặc định.

Không nên làm:

- Ghép `System.getProperty("user.dir") + "/src/main/resources/..."` để đọc resource trong deployed JAR.
- Ép mọi `URL` resource thành `new File(...)` rồi dùng `Files.readString`.
- Bắt `FileSystemNotFoundException` và tự đổi thành path bằng cách cắt chuỗi `!`: dễ sai với encoding, nested JAR, tùy biến loader.

Đúng cho **bài toán chỉ đọc content**:

```java
try (InputStream in = MyClass.class.getResourceAsStream("/demo/message.txt")) {
    if (in == null) {
        throw new FileNotFoundException("demo/message.txt");
    }
    // Xử lý bytes hoặc chuyển sang Reader.
}
```

Nếu cần **duyệt toàn bộ entries**, xác minh chữ ký, truy cập metadata của JAR riêng biệt, lúc đó mới cân nhắc `java.util.jar.JarFile`, `java.util.zip.ZipFile` hoặc ZIP/JAR FileSystem Provider. Đó là API có yêu cầu/lifecycle khác và thường cần **đường dẫn tới archive thật**, không phải phương án thay thế cơ học cho resource stream.

## 8. Khi cần dùng JarFile trực tiếp: đóng cả JarFile lẫn InputStream

Nếu ứng dụng nhận đường dẫn tới một archive thật (không chỉ là classpath resource) và cần đọc chính xác một entry, `JarFile` là lựa chọn thích hợp.

```java
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

// Bên trong method Java có throws IOException:
try (JarFile jar = new JarFile("demo.jar")) {
    JarEntry entry = jar.getJarEntry("demo/message.txt");
    if (entry == null) {
        throw new FileNotFoundException("demo/message.txt");
    }

    try (InputStream in = jar.getInputStream(entry)) {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println(text);
    }
}
```

Đây là snippet trong method có xử lý `IOException`. Ý nghĩa:

- `JarFile` **mở archive và quản lý tài nguyên của archive**; đóng bằng outer try-with-resources.
- `jar.getInputStream(entry)` cấp stream đọc bytes của entry; đóng bằng inner try-with-resources.
- `JarEntry.getName()` là tên bên trong JAR với dấu `/`, không phải path tuyệt đối của OS.
- Đường `"demo.jar"` ở đây là **file JAR trên disk** relative với working directory; nếu bạn chỉ có classpath resource từ custom loader mà không có file vật lý thì không mặc nhiên dùng được `JarFile`.

Đừng dùng `JarFile` chỉ để lấy một file cấu hình nếu `Class.getResourceAsStream` đã đáp ứng. Càng không quên đóng `JarFile` khi duyệt nhiều entries vì có thể giữ file descriptors, archive handles và bộ đệm nén.

## 9. Nhiều tài nguyên cùng tên và ClassLoader khác nhau

Classpath có thể chứa hai JAR đều có entry `demo/config.properties`. Khi đó:

- `getResource("demo/config.properties")` thường trả một URL được tìm theo chính sách tìm kiếm của loader.
- `getResourceAsStream(...)` đọc từ một resource mà cơ chế lookup đã chọn.
- `getResources("demo/config.properties")` có thể cung cấp nhiều URL; dùng khi **cố ý muốn tổng hợp** nhiều resources (ví dụ metadata plugins), chứ không phải cách “lấy bản mới nhất”.

Ví dụ:

```java
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

// Đặt trong method có throws IOException:
ClassLoader loader = Thread.currentThread().getContextClassLoader();
if (loader == null) {
    loader = ClassLoader.getSystemClassLoader();
}

Enumeration<URL> all = loader.getResources("demo/config.properties");

while (all.hasMoreElements()) {
    URL url = all.nextElement();
    try (InputStream in = url.openStream()) {
        // Đọc từng bản cấu hình và gộp theo quy tắc ứng dụng.
    }
}
```

Không cố cast loader thành `URLClassLoader` để hỏi đường JAR; trên JDK 9+ application loader mặc định không buộc là `URLClassLoader`. Thứ tự giữa các resources phụ thuộc loader, classpath/module search và duplicate packages; **không dùng thứ tự ngẫu nhiên thay cho chính sách merge**.

**ClassLoader identity quan trọng:** `SomeClass.class.getClassLoader()` chỉ loader **định nghĩa** class. Trong container/plugin, cùng resource name có thể có kết quả khác khi hỏi system loader, thread context loader hoặc plugin loader. Nếu tài nguyên “đi cùng” một class, thường nên gọi `ThatClass.class.getResourceAsStream` để tránh phải đoán loader nào đang nhìn thấy nó.

### Resource trong named module

Kể từ Java 9, resource lookup có thể chịu **module encapsulation**. Theo API Java SE, tài nguyên không phải `.class` nằm trong package của named module có thể **không hiện ra** qua `ClassLoader.getResource...` nếu package không được mở một cách phù hợp; đặc biệt, ClassLoader API có yêu cầu **unconditionally opened package** đối với nhiều resource trong named modules.

`Class.getResourceAsStream` dùng semantics của module chứa class, và việc truy cập còn phụ thuộc caller/module và quy tắc `Module.getResourceAsStream`. Điều này nghĩa là `null` **không đủ để kết luận file bị thiếu**: file có thể tồn tại nhưng không được phép truy cập qua API/caller đó. Khi đóng gói named modules, hãy kiểm tra `module-info.java`, `opens` và phương thức tìm tài nguyên; đừng thử sửa bằng cách ghép path OS.

## 10. Resource không tìm thấy: thiết kế xử lý thất bại rõ ràng

`getResourceAsStream` có thể trả `null` do:

1. Entry không tồn tại trong JAR hoặc thư mục classpath.
2. Sai tên package, thiếu/thừa dấu `/`, sai chữ hoa/chữ thường.
3. Resource không được copy vào output JAR.
4. Khác class loader hoặc thread context loader.
5. Named module encapsulation hoặc giới hạn truy cập.
6. Lỗi/giới hạn mở stream từ URL, tùy implementation và hoàn cảnh.

**Resource bắt buộc:** báo lỗi ngay với tên resource rõ ràng, chẳng hạn:

```java
InputStream raw = MyClass.class.getResourceAsStream("/demo/settings.json");
if (raw == null) {
    throw new IllegalStateException(
            "Required resource missing: /demo/settings.json");
}
try (raw) {
    // parse dữ liệu bắt buộc
}
```

Snippet này không độc lập. `IllegalStateException` là quyết định thiết kế cho configuration bắt buộc; cũng có thể chọn `FileNotFoundException` trong API xử lý `IOException`. Tránh để `NullPointerException` ở dòng đọc stream vì thông tin chẩn đoán kém.

**Resource tùy chọn:** có thể trả cấu hình mặc định hoặc `Optional` sau khi kiểm tra `null`, nhưng phải phân biệt **thật sự thiếu** với **I/O error trong lúc đọc**. Không nên tự động biến mọi `IOException` thành “dùng giá trị mặc định” nếu dữ liệu sai/hỏng cần được phát hiện.

### Kiểm tra JAR đóng gói

Từ thư mục có archive:

```bash
jar --list --file demo.jar
```

Xác nhận đúng entry `demo/message.txt` (hoặc tên cấu hình thực tế). Nếu dùng Maven, thông lệ là `src/main/resources/demo/message.txt` sẽ được copy về output classpath/JAR tương ứng; với Gradle Java plugin, `src/main/resources` cũng là nguồn resource mặc định. Tuy nhiên cần kiểm tra cấu hình build vì filters/resource exclusions có thể thay đổi nội dung/đường dẫn.

Không cần sửa `-cp` chỉ để truy cập một entry mà archive đã có trong classpath đúng: tập trung xác minh tên resource và loader. Nếu JAR chứa tài nguyên nhưng không nằm trong nguồn tìm kiếm của loader dùng để lookup, mới cần điều chỉnh cấu hình runtime.

## 11. Những lỗi quản lý stream phổ biến

| Lỗi | Rủi ro | Cách sửa |
| --- | --- | --- |
| Gọi `getResourceAsStream(...).readAllBytes()` ngay | `NullPointerException` nếu không tìm thấy. | Lưu stream, kiểm tra `null`, xử lý tên resource cụ thể. |
| Không đóng stream từ `getResourceAsStream` hoặc `URL.openStream` | Rò tài nguyên, file handle hoặc network handle tùy nguồn. | Dùng `try-with-resources` quanh stream. |
| Trả `InputStream` đã bị đóng bên trong helper | Caller không thể đọc stream đúng. | Thiết lập ownership: caller đóng nếu API trả stream; helper đóng nếu trả bytes/text. |
| Dùng `File` cho URL `jar:` | Thất bại khi chuyển URI hoặc giả định sai filesystem. | Đọc bằng `InputStream`; dùng `JarFile` khi thật sự quản lý archive vật lý. |
| Giải mã văn bản bằng default charset | Nội dung tiếng Việt/UTF-8 bị sai tùy môi trường. | Dùng `StandardCharsets.UTF_8` hoặc charset được đặc tả trong định dạng. |
| Dùng `new String(imageBytes, UTF_8)` cho ảnh | Hỏng dữ liệu nhị phân. | Giữ bytes và dùng decoder nhị phân thích hợp. |
| Dùng `available()` làm size toàn file | Có thể đọc thiếu/cấp phát sai. | Đọc đến EOF hoặc theo format length có giới hạn. |
| Đọc resource cực lớn bằng `readAllBytes()` | OOM hoặc áp lực GC. | Streaming theo chunks, giới hạn bytes. |
| Dùng `reader.lines()` rồi quên đóng reader | Stream liên kết có thể giữ resource mở. | Đặt reader trong try-with-resources, đặc biệt khi trả stream lười. |
| Quên đóng `JarFile` hoặc `ZipFile` | Archive handle có thể giữ file không xóa/ghi lại được. | Bọc object archive bằng try-with-resources. |
| Một utility tự đóng stream do caller truyền mà không nêu | Caller bị bất ngờ khi muốn sử dụng tiếp. | Ghi rõ ownership hoặc API nhận `Supplier<InputStream>` với hợp đồng phù hợp. |
| Nuốt `IOException` lúc đọc/close | Mất dữ liệu hoặc che lỗi runtime. | Ghi nhận/cấp lỗi lên caller, xử lý suppressed exceptions. |

Về file handle: ngay cả sau khi **đã đóng một `InputStream`**, implementation URL/ClassLoader có thể cache và quản lý JAR ở cấp khác. Việc đóng stream là **nghĩa vụ của người dùng stream**, nhưng không khẳng định toàn bộ archive cache của JVM/custom loader bị giải phóng ngay. Nếu bạn tự tạo `URLClassLoader` phục vụ plugin, hãy quản lý cả vòng đời/`close()` của chính loader khi thích hợp.

## 12. Thực hành thiết kế API đọc config an toàn

Một helper dùng trong code sản xuất nên:

- Nhận **`Class<?> owner`** hoặc class loader/Module cụ thể để chủ động xác định không gian tìm kiếm.
- Dùng resource name **rõ ràng, có quy tắc** (ví dụ `"/demo/config.properties"` cho `Class` API).
- Kiểm tra `null` rồi tạo exception mô tả resource.
- Đọc bằng charset xác định hoặc decoder dữ liệu thích hợp.
- Đóng stream đúng một chỗ theo hợp đồng ownership.
- Không tiết lộ URL/path tuyệt đối trong logs nhạy cảm nếu có thông tin deployment không muốn công khai.
- Có giới hạn kích thước tối đa với resources đến từ nguồn không tin cậy.

Ví dụ helper độc lập có thể tái sử dụng (đặt trong file `ResourceText.java` ở default package nếu copy nguyên):

```java
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResourceText {
    private ResourceText() {}

    public static String requiredUtf8(Class<?> owner, String absoluteName)
            throws IOException {
        if (!absoluteName.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Expected absolute resource name: " + absoluteName);
        }

        try (InputStream in = owner.getResourceAsStream(absoluteName)) {
            if (in == null) {
                throw new FileNotFoundException(absoluteName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

Đây là **một class biên dịch được**; chưa có `main` để tự chạy và cần caller truyền `owner` + `absoluteName`. Helper giữ nguyên whitespace/newline của resource; không `trim` hoặc `strip` nội dung cấu hình. `owner` không được `null` theo contract bạn định nghĩa; có thể thêm `Objects.requireNonNull` nếu input là dữ liệu ngoại vi. Nếu resource lớn, nên dùng phiên bản streaming hoặc giới hạn bytes thay vì `readAllBytes()`.

Ví dụ cách gọi bên trong class của ứng dụng:

```java
String text = ResourceText.requiredUtf8(
        MyApplication.class, "/demo/config.properties");
```

`MyApplication` là class minh họa, không có sẵn trong file `ResourceText.java`; phải thay bằng class của ứng dụng và xử lý `IOException`. Khi chạy trong named module, vẫn cần quyền truy cập resource thích hợp như mục 9.

## 13. Câu hỏi tự kiểm tra

1. **Tài nguyên `demo/message.txt` trong JAR có bắt buộc tồn tại thành file `demo/message.txt` ngoài ổ đĩa không?** Không. Nó là entry của archive; đọc bằng resource stream.
2. **Từ class `demo.Example`, `getResourceAsStream("message.txt")` khác `getResourceAsStream("/message.txt")` thế nào?** Tên đầu tìm `demo/message.txt`, tên thứ hai tìm `message.txt` ở root.
3. **`ClassLoader.getResourceAsStream("/demo/message.txt")` là tên chuẩn không?** Không; ClassLoader API nhận resource name từ root **không có dấu `/` đầu**. Truyền `"demo/message.txt"`.
4. **API trả `null` có thể có nghĩa gì?** Thiếu resource, sai tên/loader, bị encapsulation/giới hạn truy cập hoặc không thể lấy stream theo hành vi implementation; không tự khẳng định file không có.
5. **Đọc trong JAR bằng `File` hay `getResourceAsStream` nên chọn gì?** Đọc content bằng resource stream; `File` chỉ phù hợp nếu thật sự có đường dẫn filesystem và API yêu cầu.
6. **Tại sao try-with-resources cần thiết?** Đóng stream tự động ở cả đường thành công và exception, hạn chế rò handle.
7. **Ai đóng stream do helper trả ra?** Tùy hợp đồng API. Nếu trả `InputStream` còn mở thì caller phải đóng; nếu helper đọc rồi trả `String`/`byte[]` thì helper nên đóng stream trước khi trả.
8. **`readAllBytes` luôn thích hợp với mọi resource?** Không, chỉ cho dữ liệu có kích thước hợp lý/được kiểm soát; resource lớn cần streaming và giới hạn.
9. **Resource có cùng tên trong hai JAR thì `getResourceAsStream` đọc cả hai?** Không; nó mở một resource tìm được. Dùng `getResources` khi thực sự muốn liệt kê nhiều bản.
10. **Tại sao `JarFile` có hai mức tài nguyên cần đóng?** Vì có handle của archive và stream của từng entry; try-with-resources quản lý cả hai.
11. **Đã đóng stream đồng nghĩa file JAR bị unload khỏi JVM?** Không. Class loader và URL caching có vòng đời riêng; stream close chỉ đảm bảo trách nhiệm đóng stream mà caller sở hữu.
12. **Trong named module, không thấy resource luôn nghĩa là build bỏ quên file?** Không. Còn có ràng buộc encapsulation/opens và API tra cứu.

## 14. Tài liệu chuẩn

- [Java SE 21 — `Class.getResourceAsStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getResourceAsStream(java.lang.String)): tên relative/absolute theo package, resource trong named modules.
- [Java SE 21 — `ClassLoader` resource APIs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `getResource`, `getResourceAsStream`, `getResources` và search order.
- [Java SE 21 — `Module.getResourceAsStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String)): encapsulation và module resource lookup.
- [Java SE 21 — `InputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html): `readAllBytes`, `transferTo`, `available` và `close`.
- [Java SE 21 — `AutoCloseable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html): vòng đời của try-with-resources.
- [Java SE 21 — `JarFile`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/jar/JarFile.html): đọc entry và đóng archive.
- [Java SE 21 — `jar` tool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jar.html): đóng gói class và resources từ các thư mục.
- [The Java Language Specification, `14.20.3 — try-with-resources](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3): semantics close và suppressed exceptions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-010">Tài nguyên named module và các quy tắc đóng gói, opens</a>

<details>
<summary>Xem chi tiết</summary>

# Tài nguyên trong named module: quy tắc đóng gói và `opens`

Khi chuyển ứng dụng Java từ classpath sang **named module (JPMS, Java 9+)**, lập trình viên có thể gặp tình huống: file `.properties` hoặc `.txt` **đã nằm trong JAR**, một class của module đọc được, nhưng `ClassLoader.getResourceAsStream()` lại trả về `null`. Đây không nhất thiết là lỗi đường dẫn hay đóng gói: **resource trong package của named module có thể bị đóng gói (encapsulated)**. Khả năng đọc còn phụ thuộc **API đang dùng**, **module của caller** và **package có được `opens` hay không**.

Bài học giải thích việc định danh tài nguyên, cấu trúc module/JAR, cách tìm bằng `Class`, `Module`, `ClassLoader`, ý nghĩa của `exports` / `opens` / `open module` / `--add-opens`, rồi thực hành bằng một project Java 17+ có thể biên dịch và chạy được.

## 1. Resource và named module là gì?

**Resource** là dữ liệu được ứng dụng nạp lúc runtime: văn bản, cấu hình, template, ảnh, schema, bản dịch, metadata… Chúng thường nằm cạnh `.class` trong thư mục output, trong JAR hoặc trong module image. Resource thông thường **không phải một Java class** và `javac` không tự chép mọi file dữ liệu đi theo mã nguồn.

**Named module** là module Java có tên xác định, thường khai báo bằng `module-info.java` và được đưa vào module path hoặc một module layer phù hợp. Một module có thể chứa:

```text
demo.resources (named module)
├── module-info.class
├── demo/
│   ├── app/
│   │   └── Main.class
│   └── assets/
│       ├── Anchor.class
│       └── note.txt
└── META-INF/
    └── build-info.txt
```

Ví dụ trên có class `demo.assets.Anchor`, vì vậy **`demo.assets` là một package thực sự thuộc module**. File `demo/assets/note.txt` có resource name gắn với package đó. Ngược lại, `META-INF/build-info.txt` nằm ngoài một Java package hợp lệ (`META-INF` không phải tên package Java), nên không bị encapsulation theo cùng cách.

Không nên đánh đồng ba khái niệm:

- **Resource path**: đường dẫn với dấu `/`, ví dụ `demo/assets/note.txt`.
- **Package name**: tên có dấu `.`, ví dụ `demo.assets`.
- **Module name**: ví dụ `demo.resources`, xác định không gian module và một phần chính sách đóng gói.

Class loader có thể định nghĩa các class thuộc nhiều module, nhưng điều đó **không có nghĩa tất cả resource trong các module ấy công khai như trên classpath cũ**.

## 2. Hai loại đóng gói cần tách biệt: vật lý và truy cập

Một file có thể rơi vào bốn trạng thái sau:

| File có trong module output/JAR? | Tên resource đúng? | Package được mở phù hợp? | Có thể đọc bằng API đang dùng? |
|---|---|---|---|
| Không | Bất kỳ | Bất kỳ | Không |
| Có | Không | Có | Không, vì lookup sai tên |
| Có | Có | Không | **Có thể bị trả `null`** do encapsulation |
| Có | Có | Có | Có thể đọc, nếu loader có thể tìm resource và không có lỗi I/O khác |

Vì vậy **`null` không chứng minh file không tồn tại**. Cần xác minh đồng thời: nội dung artifact, tên resource, package/module và chính xác API/caller nào đang yêu cầu.

### Quy tắc resource package theo `Module.getResourceAsStream`

Với một resource như `a/b/c/config.properties`, tên package được suy ra từ phần đứng trước dấu `/` cuối cùng, tức `a.b.c`.

- Nếu `a.b.c` **là một package thuộc named module**, resource `config.properties` bị encapsulated. Caller ở module khác chỉ đọc được qua `Module.getResourceAsStream` nếu package mở tới module của caller.
- Nếu tên tài nguyên **kết thúc bằng `.class`**, đặc tả không áp dụng resource encapsulation này cho resource đó.
- Nếu tên resource không chỉ tới một package thuộc module, resource **không bị encapsulated** theo quy tắc ấy. Ví dụ `META-INF/MANIFEST.MF` có thành phần `META-INF` không phải package Java.
- Khả năng truy cập vẫn cần file thực sự hiện diện và tìm thấy; quy tắc mở package không làm xuất hiện resource bị thiếu.

**Đặc biệt:** một thư mục `config/` chứa vài file không tự biến thành package chỉ vì nó tồn tại trong JAR. Trái lại, nếu `demo.assets` có class thuộc module thì `demo/assets/note.txt` rõ ràng là resource trong package `demo.assets`.

## 3. Ba API đọc resource có quy tắc khác nhau

### 3.1. `Class.getResourceAsStream` và `Class.getResource`

```java
InputStream relative =
        Anchor.class.getResourceAsStream("note.txt");

InputStream absolute =
        Anchor.class.getResourceAsStream("/demo/assets/note.txt");
```

Đây là **mảnh minh họa**, với `Anchor` ở package `demo.assets` và có file `note.txt`.

Khi dùng `Class`:

1. Nếu tên **không bắt đầu bằng `/`**, Java ghép với package của class đại diện: `"note.txt"` thành `demo/assets/note.txt`.
2. Nếu tên bắt đầu bằng `/`, tên được hiểu theo đường dẫn tuyệt đối tính từ gốc resource space: `"/demo/assets/note.txt"` được chuẩn hóa thành `demo/assets/note.txt`.
3. Nếu class thuộc **named module**, lookup được hướng tới chính module đó và tuân theo encapsulation của `Module.getResourceAsStream`. Caller trong **cùng module** có thể truy cập resource trong package của chính module (module mở package cho chính nó); caller ở module khác cần package được mở thích hợp.

`Class.getResource` trả `URL` hoặc `null`; `Class.getResourceAsStream` trả `InputStream` hoặc `null`. Có resource mà không đọc được còn có thể phát sinh lỗi I/O khi mở/đọc stream.

### 3.2. `Module.getResourceAsStream(String)`

```java
Module module = Anchor.class.getModule();
try (InputStream in =
        module.getResourceAsStream("demo/assets/note.txt")) {
    // Xử lý in; có thể null.
}
```

API này chọn **module cụ thể** bằng đối tượng `Module` và dùng resource name theo dạng `/`, có thể bỏ dấu `/` đầu. Quan trọng nhất: **kiểm tra quyền mở package đối với module của caller**.

Trong cùng named module, code của module có thể đọc resource của chính module mà không phải public hóa package ra mọi nơi. Nếu code ở module khác gọi API này, package chứa resource cần mở tới **module caller** (mở công khai hoặc qualified opens tương ứng).

`Module.getResourceAsStream` khai báo `throws IOException`. Vì vậy nên dùng `try-with-resources` và xử lý cả trường hợp `null` lẫn lỗi I/O thực sự.

### 3.3. `ClassLoader.getResourceAsStream` / `getResource`

```java
ClassLoader loader = Anchor.class.getClassLoader();
InputStream in =
        loader.getResourceAsStream("demo/assets/note.txt");
```

Đây là mảnh minh họa; phải tự đóng stream nếu khác `null`.

**Điểm rất quan trọng của named module:** theo JDK 17, với resource **không kết thúc bằng `.class`** nằm trong package của một named module, `ClassLoader.getResource` / `getResources` / `getResourceAsStream` chỉ tìm được resource khi package được **opened unconditionally** — nghĩa là mở vô điều kiện cho tất cả module.

Điều này đúng **ngay cả khi caller của `ClassLoader.getResourceAsStream` nằm trong cùng module**. API `ClassLoader` không dùng ngữ cảnh module caller giống `Module.getResourceAsStream`.

Với resource `demo/assets/note.txt` trong module `demo.resources`:

- Module không khai báo `opens demo.assets;`: `Anchor.class.getResourceAsStream("note.txt")` khi gọi từ cùng module có thể đọc được, nhưng `loader.getResourceAsStream("demo/assets/note.txt")` trả `null`.
- Khi thêm `opens demo.assets;` vào `module-info.java`, cả hai đường đọc đều có thể tìm thấy resource.
- Nếu chỉ dùng `opens demo.assets to some.consumer;`, điều đó **không biến package thành opened unconditionally**, nên `ClassLoader.getResourceAsStream` vẫn không phải lựa chọn tin cậy cho resource này.

### Bảng so sánh

| API | Kiểu tên resource | Scope tìm kiếm | Quy tắc `opens` với resource thường trong package của named module |
|---|---|---|---|
| `Anchor.class.getResourceAsStream("note.txt")` | Tương đối package của Anchor | Module chứa Anchor | Package cần open tới caller module; tự gọi từ cùng module được |
| `Anchor.class.getResourceAsStream("/demo/assets/note.txt")` | Tuyệt đối từ gốc resource | Module chứa Anchor | Như trên |
| `Anchor.class.getModule().getResourceAsStream("demo/assets/note.txt")` | Đường dẫn trong module | Module được chỉ định | Package cần open tới caller module; cùng module được |
| `Anchor.class.getClassLoader().getResourceAsStream("demo/assets/note.txt")` | Đường dẫn với `/`, không tự thêm package | Qua class loader | Package cần **open unconditionally**, trừ ngoại lệ như `.class` |
| `ClassLoader.getSystemResourceAsStream("demo/assets/note.txt")` | Đường dẫn với `/` | Qua system loader | Cũng chịu quy tắc named-module resources của `ClassLoader` |

Không có API nào “bỏ qua mọi ranh giới module”. Các API có *scope* và quy tắc truy cập khác nhau.

## 4. `exports`, `opens`, `open module` khác nhau

Xem module descriptor:

```java
module demo.resources {
    exports demo.app;
    opens demo.assets;
}
```

Đây là **ví dụ khai báo**, không phải file riêng ngoài `module-info.java`.

| Khai báo | Mục đích chính | Có đủ cho `ClassLoader.getResourceAsStream` đọc `demo/assets/note.txt`? |
|---|---|---|
| Không khai báo gì cho `demo.assets` | Đóng package với module khác | Không |
| `exports demo.assets;` | Cho module đọc phù hợp truy cập public types/members | **Không** — exports không đồng nghĩa opens |
| `exports demo.assets to consumer;` | Xuất public API có chọn lọc | Không |
| `opens demo.assets;` | Mở package vô điều kiện để deep reflection và resource access | **Có**, nếu resource tồn tại |
| `opens demo.assets to consumer;` | Mở package cho consumer đã chỉ định | **Không**, vì không unconditional |
| `open module demo.resources { ... }` | Mở mọi package trong module | Có với các resource package hiện diện, nhưng phạm vi quyền rộng |

**`exports` và `opens` không thay thế nhau**:

- `exports` chủ yếu phục vụ truy cập các **public Java types và members** cho module đọc phù hợp.
- `opens` mở package cho khả năng reflective access sâu theo quy tắc JPMS và **ảnh hưởng trực tiếp đến việc tìm resource**.
- `opens ... to m1, m2;` chỉ mở cho danh sách module cụ thể.
- `open module ...` mở toàn bộ package trong module; không nên chọn chỉ để sửa một resource bị `null` nếu chỉ cần mở package nhỏ hơn.

Trong cùng module, mã Java thường dùng trực tiếp package không `exports` và truy cập resource của mình qua `Class`/`Module`. **Không cần xuất public API chỉ để đọc file cấu hình nội bộ**.

### `--add-opens` có tương đương `opens p;` không?

Không hoàn toàn. Ví dụ:

```bash
java --add-opens demo.resources/demo.assets=ALL-UNNAMED \
     --module-path mods -m demo.resources/demo.app.Main
```

Tùy chọn này mở `demo.assets` **có mục tiêu** tới các unnamed modules. Nó không giống khai báo mở vô điều kiện `opens demo.assets;`. Vì thế đừng mặc định `ClassLoader.getResourceAsStream` sẽ tìm được mọi resource của package đó sau `--add-opens`. Muốn truy cập trong chính module, dùng `Class`/`Module` thường chính xác hơn; nếu cần classloader-based lookup rộng, cân nhắc `opens` vô điều kiện và đánh giá hệ quả encapsulation.

**Không dùng `--add-exports` thay `--add-opens`** để giải quyết encapsulation của non-`.class` resources.

## 5. Thí nghiệm Java 17+: file có thật nhưng `ClassLoader` trả `null`

Ví dụ sau chạy ở chế độ **named module** với Java 17+. Nó được tổ chức đủ để phân biệt **lỗi thiếu resource** và **resource bị encapsulated**.

### 5.1. Cấu trúc project

```text
project/
├── src/
│   ├── module-info.java
│   └── demo/
│       ├── app/
│       │   └── Main.java
│       └── assets/
│           └── Anchor.java
├── resources/
│   ├── demo/
│   │   └── assets/
│   │       └── note.txt
│   └── META-INF/
│       └── build-info.txt
├── mods/                    (sinh khi biên dịch)
└── dist/                    (sinh khi tạo modular JAR)
```

Có hai package được định nghĩa bởi code: `demo.app` và `demo.assets`. Resource `demo/assets/note.txt` nằm trong package `demo.assets`, trong khi `META-INF/build-info.txt` không thuộc một package Java hợp lệ.

### 5.2. Tệp `src/module-info.java`

```java
module demo.resources {
}
```

Ban đầu **không `exports` và không `opens`** để kiểm chứng quy tắc mặc định. `Main` và `Anchor` cùng thuộc module nên mã trong module có thể tham chiếu trực tiếp `Anchor`.

### 5.3. Tệp `src/demo/assets/Anchor.java`

```java
package demo.assets;

public final class Anchor {
    private Anchor() {
    }
}
```

Class này đóng vai trò **mốc package** khi gọi `Anchor.class.getResourceAsStream("note.txt")`. Ta không cần tạo instance Anchor.

### 5.4. Tệp `src/demo/app/Main.java`

```java
package demo.app;

import demo.assets.Anchor;
import java.io.InputStream;

public final class Main {
    private static boolean exists(InputStream in) throws Exception {
        if (in == null) {
            return false;
        }
        try (in) {
            return in.read() != -1;
        }
    }

    public static void main(String[] args) throws Exception {
        Module module = Anchor.class.getModule();
        ClassLoader loader = Anchor.class.getClassLoader();
        String name = "demo/assets/note.txt";

        System.out.println("named module: " + module.isNamed());
        System.out.println("Module API: "
                + exists(module.getResourceAsStream(name)));
        System.out.println("Class relative: "
                + exists(Anchor.class.getResourceAsStream("note.txt")));
        System.out.println("Class absolute: "
                + exists(Anchor.class.getResourceAsStream("/" + name)));
        System.out.println("ClassLoader API: "
                + exists(loader.getResourceAsStream(name)));
        System.out.println("META-INF resource: "
                + exists(loader.getResourceAsStream(
                        "META-INF/build-info.txt")));
    }
}
```

Hàm `exists` dùng `try-with-resources` để đóng stream và kiểm tra tài nguyên có ít nhất một byte. Các file dữ liệu trong ví dụ sẽ được tạo không rỗng, do đó kết quả `true` không bị lẫn với resource rỗng. Trong ứng dụng thực tế, nên đặt tên `read...` nếu cần đọc/parse toàn bộ file, thay vì chỉ dùng phép thử này.

### 5.5. File dữ liệu

`resources/demo/assets/note.txt`:

```text
hello
```

`resources/META-INF/build-info.txt`:

```text
build=test
```

Hai file trên là dữ liệu thông thường, **không** phải source Java.

### 5.6. Biên dịch và copy resource

Các lệnh sau dùng Bash trên Linux/macOS, chạy ở thư mục `project`:

```bash
mkdir -p mods/demo.resources/demo/assets
mkdir -p mods/demo.resources/META-INF
mkdir -p dist

javac -d mods/demo.resources \
    src/module-info.java \
    src/demo/app/Main.java \
    src/demo/assets/Anchor.java

cp resources/demo/assets/note.txt \
   mods/demo.resources/demo/assets/note.txt

cp resources/META-INF/build-info.txt \
   mods/demo.resources/META-INF/build-info.txt
```

**Vì sao cần `cp`?** `javac` chỉ biên dịch các file `.java` đã chỉ định; nó không tự lấy tất cả `.txt` từ `resources/`. Muốn module thực sự chứa file cấu hình, phải copy vào output theo đúng resource path hoặc cấu hình build tool để thực hiện việc đó.

Lệnh chạy:

```bash
java --module-path mods -m demo.resources/demo.app.Main
```

**Stdout chính xác khi module chưa khai báo `opens`:**

```text
named module: true
Module API: true
Class relative: true
Class absolute: true
ClassLoader API: false
META-INF resource: true
```

### 5.7. Vì sao kết quả trông ngược trực giác?

- `module.isNamed()` trả `true`: code đang ở named module.
- `Module API: true`: caller `demo.app.Main` thuộc **chính module chứa resource**, nên `Module.getResourceAsStream` tìm được `demo/assets/note.txt`.
- `Class relative/absolute: true`: `Anchor.class` định vị resource trong cùng module; cú pháp relative và absolute cùng trỏ đúng file.
- `ClassLoader API: false`: file vẫn tồn tại; khác biệt là package `demo.assets` **chưa opened unconditionally** nên classloader-based resource lookup không thấy non-`.class` resource trong package đó.
- `META-INF resource: true`: path không thuộc Java package trong module, do vậy quy tắc package encapsulation không chặn nó.

**Đây không phải lỗi class loader bị hỏng.** Cùng một project, cùng runtime, cùng resource name tuyệt đối nhưng API có encapsulation rule khác nhau.

### 5.8. Thay `module-info.java` bằng `opens demo.assets;`

Sửa **chỉ tệp `src/module-info.java`** trong project ví dụ thành:

```java
module demo.resources {
    opens demo.assets;
}
```

Biên dịch lại module descriptor và class:

```bash
javac -d mods/demo.resources \
    src/module-info.java \
    src/demo/app/Main.java \
    src/demo/assets/Anchor.java

java --module-path mods -m demo.resources/demo.app.Main
```

File `.txt` đã được copy vào thư mục output và không bị `javac` xóa trong các lệnh trên.

**Stdout lúc này:**

```text
named module: true
Module API: true
Class relative: true
Class absolute: true
ClassLoader API: true
META-INF resource: true
```

Chỉ cần thêm **`opens demo.assets;`** là kết quả `ClassLoader API` đổi từ `false` thành `true`. Đây là minh chứng trực tiếp cho quy tắc **unconditional opens**.

### 5.9. Đóng gói thành modular JAR và chạy tiếp

Sau khi biên dịch bản có `opens`, đóng gói **cùng output đã có resource**:

```bash
jar --create --file dist/demo.resources.jar \
    -C mods/demo.resources .

jar --list --file dist/demo.resources.jar

java --module-path dist -m demo.resources/demo.app.Main
```

Output của lệnh `jar --list` có các entry quan trọng như:

```text
module-info.class
demo/app/Main.class
demo/assets/Anchor.class
demo/assets/note.txt
META-INF/build-info.txt
```

Danh sách thực tế có thể kèm `META-INF/MANIFEST.MF` hoặc metadata khác, tùy công cụ JAR. Điều cần kiểm tra là **hai resource thật sự hiện diện**, đúng đường dẫn, và `module-info.class` đã được đóng gói.

Với bản `opens`, chạy từ `dist/demo.resources.jar` cho **cùng sáu dòng stdout** của phần 5.8. Đây là bài kiểm tra đóng gói: nếu chạy từ thư mục `mods` thành công nhưng từ JAR thất bại, cần tìm vấn đề ở quá trình copy/resource packaging thay vì mặc định đổ lỗi cho `opens`.

## 6. Khi đọc resource từ module khác

Giả sử có named module `demo.consumer`, cần đọc `demo/assets/note.txt` nằm ở module `demo.resources`. Với API:

```java
Module provider = Anchor.class.getModule();
try (InputStream in =
        provider.getResourceAsStream("demo/assets/note.txt")) {
    // Caller đang chạy trong demo.consumer.
}
```

Đây là **mảnh minh họa**, chỉ biên dịch khi consumer có quyền tham chiếu tới `Anchor` hoặc bằng một cách phù hợp lấy được đối tượng `Module` provider.

Nếu `demo.resources` chưa mở `demo.assets` cho consumer, `getResourceAsStream` có thể trả `null` dù resource tồn tại. Có các cách mở:

```java
module demo.resources {
    opens demo.assets to demo.consumer;
}
```

Khai báo này mở **có chọn lọc** cho `demo.consumer`; chỉ cần nêu đúng target module. Nếu consumer trực tiếp import public class `Anchor`, cần xét **`exports demo.assets to demo.consumer;`** và module readability riêng, vì **`opens` không mặc nhiên là lời khai báo public compile-time API cho consumer**.

Một phương án giảm kết nối giữa consumer và package nội bộ là provider module đưa ra **public resource-reading API** ở package được `exports`. Provider code tự dùng `Class`/`Module` để đọc resource nội bộ, rồi trả dữ liệu theo hợp đồng API. Cách đó thường an toàn hơn việc mở một package nội bộ chỉ để consumer tìm file.

**Lưu ý quan trọng:** `opens demo.assets to demo.consumer;` đủ cho `Module.getResourceAsStream` được consumer gọi nếu các điều kiện khác hợp lệ; nhưng **không đủ** cho quy tắc classloader-based lookup đòi hỏi mở **vô điều kiện**.

## 7. Các trường hợp resource không nằm trong package

Không phải mọi resource trong named module đều bị encapsulated. Ví dụ:

```text
META-INF/services/com.example.Service
META-INF/build-info.txt
config/application.properties
demo/assets/note.txt
```

Nhìn vào nơi chứa các Java packages thực tế:

- `META-INF` không phải Java package hợp lệ; resource ở đó không bị package encapsulation theo `Module.getResourceAsStream`.
- `config/application.properties` chỉ bị xem như resource trong package `config` nếu `config` thực sự là một package của named module. Nếu không có package tương ứng trong module, resource không bị encapsulated bởi quy tắc package.
- `demo/assets/note.txt` nằm trong package `demo.assets` khi module có class `demo.assets.Anchor`; khi ấy quy tắc `opens` áp dụng.
- Nếu resource path kết thúc bằng `.class`, đặc tả có ngoại lệ với resource encapsulation. **Không đồng nghĩa mọi `.class` đều công khai để thực thi như API Java**, vì class visibility, module exports và linking là các cơ chế khác.

Đừng xem việc chuyển file vào `META-INF` là thủ thuật để vượt quyền một cách tùy tiện. Hãy chọn vị trí dựa trên mục đích dữ liệu và hợp đồng build/module; resource public và resource private nên được thiết kế có chủ đích.

### `META-INF/services` và `ServiceLoader`

Với ứng dụng classpath truyền thống, service provider configuration thường nằm ở `META-INF/services/...`. Trong **named modules**, `ServiceLoader` còn dùng các khai báo trong module descriptor:

```java
module demo.provider {
    provides com.example.spi.Handler
        with com.example.provider.HandlerImpl;
}
```

Đây là **ví dụ cú pháp minh họa**; nó cần các kiểu và module phù hợp mới biên dịch. Không được thay thế cơ chế `provides`/`uses` của named module bằng giả định rằng đọc `META-INF/services` thủ công là tương đương service loading qua JPMS.

## 8. `open module` và công cụ CLI: nên áp dụng ở mức nào?

Một module có thể khai báo:

```java
open module demo.resources {
}
```

Mọi package của module được mở vô điều kiện. Resource lookup qua `ClassLoader` có thể nhìn thấy các resource thường trong các package tương ứng nếu chúng hiện diện.

Nhưng mở cả module thường **rộng hơn cần thiết**: code khác cũng có thể có thêm khả năng deep reflection vào các package ấy. Nếu chỉ một package chứa resource cần được công khai, ưu tiên:

```java
module demo.resources {
    opens demo.assets;
}
```

Nếu mục tiêu chỉ là **đọc dữ liệu nội bộ**, lại có lựa chọn bảo toàn encapsulation tốt hơn: **đừng mở**; dùng `Anchor.class.getResourceAsStream(...)` hoặc `Anchor.class.getModule().getResourceAsStream(...)` từ code **cùng module**.

### Cân nhắc với thư viện/framework

Framework có thể dùng `ClassLoader.getResource(...)` hoặc thread context class loader để tìm tài nguyên theo path. Khi chuyển framework từ classpath sang named module, mã ứng dụng dùng `Class` đọc được không đảm bảo framework cũng đọc được.

Các hướng xử lý phải dựa trên API thật sự của framework:

1. Nếu framework hỗ trợ truyền `InputStream`, `URL` hoặc cấu hình đã đọc sẵn, provider có thể tự đọc bằng `Class`/`Module` rồi truyền cho framework.
2. Nếu framework buộc dùng `ClassLoader.getResource` và package chứa tài nguyên phải công khai, cân nhắc `opens p;` với ý thức rằng phạm vi mở vô điều kiện.
3. Nếu framework tải service providers, annotation/metadata hoặc module layers, xem cơ chế JPMS chuyên dụng của framework; đừng sửa `exports`/`opens` theo thử-sai mà không hiểu caller.
4. Nếu cần resource chia sẻ độc lập module package, thiết kế vị trí/đường dẫn resource ở phạm vi công khai hợp lệ ngay từ đầu.

## 9. Phân biệt lỗi đóng gói, tên đường dẫn và module encapsulation

Một thứ tự chẩn đoán thực tế:

**Bước 1: In module và loader**

```java
Class<?> anchor = Anchor.class;

System.out.println(anchor.getModule().getName());
System.out.println(anchor.getModule().isNamed());
System.out.println(anchor.getClassLoader());
```

Đây là mảnh code khi `Anchor` tồn tại trong scope. Trên classpath thông thường, `getModule().isNamed()` có thể là `false` (unnamed module), nên không nên áp dụng bừa quy tắc của named module.

**Bước 2: Xác nhận nội dung vật lý**

```bash
jar --list --file dist/demo.resources.jar
```

Tìm chính xác `demo/assets/note.txt`, `module-info.class` và resource khác. `javac` không copy file data tự động; build tool có thể bị cấu hình bỏ qua `resources/`.

**Bước 3: Xác nhận resource name**

```java
Anchor.class.getResourceAsStream("note.txt");
Anchor.class.getResourceAsStream("/demo/assets/note.txt");
Anchor.class.getClassLoader()
    .getResourceAsStream("demo/assets/note.txt");
```

Hai cách đầu (dưới `Class`) tương đương trong ví dụ vì Anchor ở `demo.assets`. `ClassLoader` cần đường dẫn từ gốc, không tự ghép package và không dùng dấu `/` đầu như quy ước relative của `Class`.

**Bước 4: Xác nhận package và trạng thái mở**

```java
Module module = Anchor.class.getModule();
String pkg = "demo.assets";

System.out.println(module.getPackages().contains(pkg));
System.out.println(module.isOpen(pkg));
System.out.println(module.isOpen(pkg, module));
```

Với module chưa khai báo `opens` trong thí nghiệm, các kết quả là:

```text
true
false
true
```

Giải thích: package tồn tại; **không opened unconditionally**; nhưng package của module được xem là open đối với **chính module đó**. Đây chính là khác biệt mà `ClassLoader` và `Module` resource APIs biểu hiện.

**Bước 5: Chọn API đúng và chỉ mở đúng phạm vi cần thiết**

Nếu file dùng nội bộ module, ưu tiên API module/class. Nếu bên ngoài cần resource, quyết định rõ module nào được phép đọc và framework sử dụng API nào. Chỉ khi thực sự cần lookup dựa trên class loader cho mọi bên mới cân nhắc unconditional opens.

## 10. Những lỗi hiểu sai phổ biến

1. **“File đã nằm trong JAR thì `ClassLoader.getResourceAsStream` phải đọc được.”** Sai với resource trong package của named module chưa mở vô điều kiện.
2. **“`exports p;` là đủ cho tất cả resource trong p.”** Sai: exports chủ yếu điều khiển truy cập public Java API, không thay thế opens cho resource encapsulation.
3. **“Tôi đang gọi `ClassLoader` từ cùng module nên sẽ luôn đọc được.”** Sai: classloader-based lookup yêu cầu package opens unconditional cho non-`.class` resources trong named module.
4. **“`opens p to m;` tương đương `opens p;`.”** Sai: một cái mở có chọn lọc, một cái mở vô điều kiện.
5. **“`Class.getResourceAsStream("note.txt")` tìm từ gốc JAR.”** Sai: khi không có dấu `/` đầu, nó ghép với package của class.
6. **“`Module.getResourceAsStream` luôn tìm toàn bộ module graph.”** Sai: API đọc resource **trong module object đã chọn**.
7. **“Resource dưới `META-INF` mặc nhiên là package trong module.”** Sai: `META-INF` không phải Java package, nên không bị encapsulated theo cùng quy tắc.
8. **“`javac` tự đóng gói mọi file `resources`.”** Sai: phải copy hoặc cấu hình build tool.
9. **“Đọc được resource nghĩa là class trong package ấy được public/exported.”** Sai: resource access và truy cập Java types là hai cơ chế khác nhau.
10. **“`--add-opens ...=ALL-UNNAMED` mở package vô điều kiện cho mọi API.”** Sai: nó mở có mục tiêu cho unnamed modules.
11. **“Một named module luôn nằm trong file JAR.”** Sai: module có thể tồn tại dưới dạng exploded module directory, modular JAR hoặc runtime image.
12. **“Một resource `.class` không bị encapsulated nên tôi có thể dùng mọi class private/internal.”** Sai: ngoại lệ resource lookup không bỏ qua linkage, Java access hay JPMS exports.
13. **“Trả `null` luôn là lỗi Java module system.”** Sai: tên sai, file thiếu, đóng gói sai, class loader khác hoặc resource path mismatch cũng có thể trả `null`.

## 11. Checklist build & packaging resource cho named module

- [ ] Có `module-info.java` và chạy đúng `--module-path ... -m module/class` khi cần thử named module.
- [ ] `javac` đã tạo `module-info.class` và tất cả class đúng package path.
- [ ] Resource data được copy vào **module output** đúng tên và đường dẫn.
- [ ] Modular JAR / exploded module thực sự chứa file sau build.
- [ ] Tên truyền cho `Class.getResourceAsStream` relative/absolute đúng quy tắc dấu `/`.
- [ ] Tên truyền cho `Module.getResourceAsStream` dùng đường dẫn trong module.
- [ ] Tên truyền cho `ClassLoader.getResourceAsStream` là path với `/`, không dựa vào package tương đối.
- [ ] Đã biết tên package suy ra từ resource và package đó có thuộc module không.
- [ ] Đã xác định caller ở cùng module hay module khác.
- [ ] Chỉ dùng `exports` cho nhu cầu public Java API; dùng `opens` khi cần mở package.
- [ ] Chỉ dùng **unconditional opens** nếu API dựa trên `ClassLoader` thực sự cần resource của package.
- [ ] Đã kiểm tra với artifact cuối (JAR/module image), không chỉ thư mục output của IDE.
- [ ] Đã đóng `InputStream` bằng `try-with-resources` để không rò rỉ tài nguyên.

## 12. Tự kiểm tra

**Câu 1.** `demo.assets` là package thuộc named module `demo.resources`. File `demo/assets/note.txt` tồn tại. Module không khai báo `opens`. Tại sao code trong chính module đọc được bằng `Anchor.class.getResourceAsStream("note.txt")` nhưng `Anchor.class.getClassLoader().getResourceAsStream("demo/assets/note.txt")` trả `null`?

**Đáp án:** `Class` dùng lookup theo module và xét quyền mở cho module caller (chính module có quyền đọc dữ liệu của mình). `ClassLoader` chỉ hiển thị resource thường trong package của named module nếu package được opened **unconditionally**.

**Câu 2.** Thêm `exports demo.assets;` có chắc sửa kết quả `ClassLoader` trong câu 1 không?

**Đáp án:** Không. Exports không tương đương opens; cần `opens demo.assets;` khi dùng classloader-based lookup, hoặc dùng API khác đúng phạm vi.

**Câu 3.** `opens demo.assets to demo.consumer;` có cho consumer dùng `Module.getResourceAsStream` không? Có bảo đảm `ClassLoader.getResourceAsStream` hoạt động không?

**Đáp án:** Nó có thể cho code trong module `demo.consumer` đọc resource bằng Module API (giả định resource tìm thấy và các điều kiện khác hợp lệ). Nhưng không mở **vô điều kiện**, nên không đủ điều kiện bắt buộc của classloader-based lookup cho non-`.class` resource trong package này.

**Câu 4.** Tại sao `META-INF/build-info.txt` được `ClassLoader` đọc trong ví dụ mà `demo/assets/note.txt` không được?

**Đáp án:** `META-INF` không phải tên Java package hợp lệ; resource ở đó không bị package encapsulation. `demo.assets` là package thực của module và chưa opened unconditionally.

**Câu 5.** Nếu chạy được ở `mods/demo.resources` nhưng đóng gói JAR xong không thấy resource, bước đầu kiểm tra gì?

**Đáp án:** Dùng `jar --list --file ...` để xác nhận resource được chép vào modular JAR đúng path; kiểm tra build resource copying trước khi thay `opens`.

## 13. Mô hình ghi nhớ

```text
Muốn đọc một resource trong named module:
                 |
                 v
File có trong module output/JAR và đúng path?
          /                 \
       Không                 Có
         |                    |
  Sửa packaging        Resource có nằm trong
                       package thật của module?
                           /        \
                        Không        Có
                          |           |
                 Không encapsulated  Caller/API nào?
                 theo package rule    |
                                     +-- Class/Module:
                                     |     same module hoặc
                                     |     package open tới caller
                                     |
                                     +-- ClassLoader:
                                           package phải
                                           opened unconditionally
                                           (với non-.class resource)
```

**Tóm tắt ba nguyên tắc:**

1. **Đúng artifact và resource name trước:** chưa copy file hay gọi nhầm path thì mọi `opens` đều vô ích.
2. **Named module có resource encapsulation:** `Class`/`Module` cân nhắc caller module; `ClassLoader` yêu cầu opens vô điều kiện đối với resource thường trong package của named module.
3. **Chọn mức mở nhỏ nhất đáp ứng API:** nội bộ module nên đọc bằng `Class`/`Module`; xuất public API dùng `exports`; mở package cho reflection/resource khi thật sự cần bằng `opens` phù hợp.

### Tài liệu chuẩn

- [Java SE 17 — Module.getResourceAsStream: module resource encapsulation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String))
- [Java SE 17 — Class.getResourceAsStream: đường dẫn tương đối và named module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getResourceAsStream(java.lang.String))
- [Java SE 17 — ClassLoader.getResource / getResourceAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#getResourceAsStream(java.lang.String))
- [Java Language Specification, `7.7.2: Module Declarations, exports và opens](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.2)


</details>

- [Quay lại đầu trang](#back-to-top)
