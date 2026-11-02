<a id="back-to-top"></a>

# 22. Vận hành và kiểm thử

## Menu
- [22.1 Phân biệt security property với system property và cách nạp java.security](#security-operations-testing-001)
- [22.2 jdk.tls.disabledAlgorithms, jdk.certpath.disabledAlgorithms và chính sách thuật toán](#security-operations-testing-002)
- [22.3 Kiểm tra thay đổi mặc định theo bản cập nhật JDK thay vì chép cấu hình cũ](#security-operations-testing-003)
- [22.4 Chẩn đoán bằng javax.net.debug, java.security.debug và nhật ký đã loại bí mật](#security-operations-testing-004)
- [22.5 Kiểm thử bằng bộ dữ liệu đã biết kết quả và kiểm tra tương thích giữa provider](#security-operations-testing-005)
- [22.6 Sửa một byte của ciphertext, tag, AAD hoặc chữ ký để kiểm tra đường từ chối](#security-operations-testing-006)
- [22.7 Kiểm thử chứng thư hết hạn, sai hostname, thiếu intermediate và CA không tin cậy](#security-operations-testing-007)
- [22.8 Kiểm thử sai khóa, sai mật khẩu, tham số không khớp và dữ liệu bị cắt](#security-operations-testing-008)
- [22.9 Đo hiệu năng trong khi vẫn bảo toàn nonce, chi phí dẫn xuất và phạm vi dùng khóa](#security-operations-testing-009)
- [22.10 Lập kế hoạch đổi khóa, đổi thuật toán và khôi phục sau sự cố mà không tự hạ mức bảo vệ](#security-operations-testing-010)

## <a id="security-operations-testing-001">22.1 Phân biệt security property với system property và cách nạp java.security</a>

<details>
<summary>Xem chi tiết</summary>

# 22.1 Phân biệt security property với system property và cách nạp java.security

## 1. Vì sao hai loại property dễ bị nhầm trong vận hành Java?

Một ứng dụng Java có thể nhận nhiều cấu hình dưới dạng `key=value`. Tuy cùng trông giống “property”, **security property** và **system property** là **hai tập cấu hình khác nhau**, có API đọc/ghi, nguồn nạp và thời điểm tác động khác nhau. Đặt một cấu hình vào sai tập có thể khiến chương trình chạy bình thường nhưng chính sách mật mã, TLS hoặc provider không đổi như mong muốn.

Ví dụ, kỹ sư muốn giới hạn thuật toán TLS nên thêm cờ JVM:

~~~bash
java -Djdk.tls.disabledAlgorithms=SomeValue MyApp
~~~

Lệnh này đặt **system property** tên `jdk.tls.disabledAlgorithms`. Trong triển khai JDK/JSSE thông dụng, `jdk.tls.disabledAlgorithms` là **security property** được đọc qua cấu hình bảo mật của JDK. Cờ trên không phải cách đúng để thay đổi danh sách thuật toán cấm của JSSE. Trường hợp này đặc biệt nguy hiểm vì người vận hành có thể tưởng đã áp dụng chính sách, trong khi thực tế chưa làm vậy.

Bài học này giải thích nguồn gốc, thứ tự nạp, cách thay đổi và cách **xác minh cấu hình thực sự có hiệu lực**, với trọng tâm Java 17+ và công cụ JDK hiện đại. Nội dung vừa đủ để vận hành an toàn các ứng dụng sử dụng JCA/JCE, JSSE, chứng chỉ, chữ ký JAR và các cơ chế Java Security.

### Kết quả cần đạt

Sau bài, bạn có thể:

- Phân biệt `System.getProperty(...)` với `Security.getProperty(...)` và biết khi nào dùng từng API.
- Tìm đúng `java.security` của **JVM thực sự đang chạy**, không đoán theo biến `JAVA_HOME` của shell.
- Giải thích ý nghĩa của `-Djava.security.properties=<URL>` và `-Djava.security.properties==<URL>`.
- Hiểu vì sao thay đổi property sau khi thư viện bảo mật đã khởi tạo có thể **không** đổi hành vi.
- Kiểm tra cấu hình bằng code, `-Djava.security.debug=properties` và `-XshowSettings:security` khi phiên bản JDK hỗ trợ.
- Xây dựng cách thay đổi, kiểm thử và rollback chính sách bảo mật mà không vô tình xóa cấu hình mặc định.

## 2. Hai “namespace” riêng biệt trong cùng một JVM

| Khía cạnh | **System property** | **Security property** |
| --- | --- | --- |
| Đối tượng quản lý | `java.lang.System` | `java.security.Security` |
| Đọc | `System.getProperty("key")` | `Security.getProperty("key")` |
| Ghi trong chương trình | `System.setProperty("key", "value")` | `Security.setProperty("key", "value")` |
| Nạp thông thường | Cờ `-Dkey=value`, giá trị JVM thiết lập, code Java; từng ứng dụng có thể bổ sung cách riêng | Tệp master `conf/security/java.security`, tệp bổ sung được khai báo, hoặc API `Security.setProperty` |
| Phạm vi | JVM process hiện tại | JVM process hiện tại |
| Mục đích điển hình | Đường dẫn, cấu hình môi trường, các cờ hệ thống/JSSE/JVM riêng | Chính sách bảo mật JDK, danh sách provider, thuật toán bị hạn chế, default security settings |
| Thay đổi khi ứng dụng đang chạy | Map có thể được sửa; tác động phụ thuộc bên tiêu thụ | Map có thể được sửa; một số phần bảo mật đã nạp/cache sẽ không áp dụng thay đổi |
| Tồn tại sau khi kết thúc process? | Không, trừ khi cấu hình nguồn được giữ ở launcher hoặc file | Không, trừ khi cấu hình nguồn được giữ ở master/overlay hoặc cơ chế khởi động |

Hai API **không đọc chung một Map**. Cùng tên khóa vẫn có thể có hai giá trị khác nhau:

~~~text
System properties:      demo.scope = "system-value"
Security properties:    demo.scope = "security-value"
~~~

Khi gọi `System.getProperty("demo.scope")`, chương trình nhận `system-value`; khi gọi `Security.getProperty("demo.scope")`, chương trình nhận `security-value`. Không có quy tắc “property trùng tên thì system thắng” hoặc “security tự kế thừa từ system” một cách tổng quát. **Tài liệu của từng property quyết định nơi nó được tiêu thụ**.

Đặc biệt, tên **`java.security.properties`** là **một system property điều khiển việc nạp tập security properties**, còn những mục nằm trong tệp `java.security` là security properties. Việc một system property *điều khiển cơ chế nạp* không khiến hai tập trở thành một.

### Những ví dụ đặt đúng chỗ

| Tên khóa | Thông thường thuộc | Công dụng |
| --- | --- | --- |
| `java.home` | System | Đường dẫn runtime thực tế mà process đang sử dụng |
| `java.security.properties` | System | Chỉ định tệp security properties bổ sung hoặc tệp thay thế |
| `java.security.debug` | System | Bật trace chẩn đoán của Java Security |
| `javax.net.ssl.trustStore` | System | Chỉ định keystore chứa CA/trust anchors mặc định cho một số đường đi của JSSE |
| `javax.net.ssl.trustStoreType` | System | Loại keystore tương ứng |
| `jdk.tls.disabledAlgorithms` | Security | Hạn chế thuật toán, protocol, cipher suite và điều kiện khóa theo JSSE |
| `jdk.certpath.disabledAlgorithms` | Security | Hạn chế thuật toán khi kiểm tra đường đi chứng chỉ |
| `jdk.jar.disabledAlgorithms` | Security | Hạn chế thuật toán trong kiểm tra chữ ký JAR |
| `security.provider.1`, `security.provider.2`, ... | Security | Đăng ký provider theo thứ tự ưu tiên |
| `keystore.type` | Security | Loại keystore mặc định của JDK khi một API cần mặc định |
| `ssl.TrustManagerFactory.algorithm` | Security | Thuật toán `TrustManagerFactory` mặc định |
| `security.overridePropertiesFile` | Security trong master | Cho phép/không cho phép cơ chế chỉ định tệp bổ sung qua launcher |

Bảng mô tả semantics phổ biến trên OpenJDK/Oracle JDK; **không suy ra nơi đọc một property mới từ tên có tiền tố `jdk.` hoặc `java.`**. Ví dụ `java.security.debug` là *system property* dù tên bắt đầu bằng `java.security`.

## 3. Tệp master `java.security` nằm ở đâu?

Trên JDK 9 trở lên (bao gồm Java 17 và 21), bản master mặc định nằm tại:

~~~text
<java.home>/conf/security/java.security
~~~

Ví dụ:

~~~text
Linux/macOS: /opt/jdk-21/conf/security/java.security
Windows:     C:\Java\jdk-21\conf\security\java.security
~~~

Các đường dẫn trên chỉ là ví dụ; JDK có thể nằm ở vị trí khác. **`System.getProperty("java.home")`** là cách xác nhận `java.home` của process đang chạy. Nó có thể khác biến môi trường `JAVA_HOME` của terminal, cấu hình Gradle daemon, IDE hoặc container host. Nếu dùng JDK 8 cũ, cấu trúc thường là `lib/security/java.security` trong Java runtime/JRE; khi soạn và triển khai mới nên kiểm tra tài liệu đúng phiên bản.

`java.security` là tệp thuộc bộ cài JDK, chứa nhiều dòng:

~~~properties
# Các dòng bắt đầu bằng # là comment.
keystore.type=pkcs12
ssl.TrustManagerFactory.algorithm=PKIX

# Ví dụ cấu hình provider; nội dung/thứ tự thật khác theo JDK.
security.provider.1=SUN
security.provider.2=SunRsaSign
~~~

Đoạn trên **minh họa cấu trúc** và tên khóa, không phải cấu hình master đầy đủ được khuyến nghị để thay thế. Các JDK vendor/bản vá có thể thay đổi danh sách provider, thuật toán bị vô hiệu hóa và giá trị mặc định. **Không copy ví dụ này để thay thế toàn bộ tệp mặc định.**

Các điểm cần nhớ:

- Đây là định dạng gần với `java.util.Properties`: mỗi định nghĩa kiểu `key=value`, dòng `#` là chú thích; không phải YAML/JSON.
- Mục cấu hình vắng mặt không tương đương mục có `key=` rỗng. `Security.getProperty("key")` trả `null` khi không tìm thấy và có thể trả chuỗi rỗng khi tệp định nghĩa giá trị rỗng.
- Chỉnh trực tiếp tệp trong thư mục JDK tác động đến **các JVM tương lai dùng chính JDK đó** và có thể bị ghi đè khi cập nhật JDK. Với triển khai ứng dụng, ưu tiên **tệp overlay riêng, có quản lý phiên bản và quyền file chặt**, khi cấu hình cho phép.
- Tệp master chứa chính sách nhạy cảm như provider registration và các thuật toán bị hạn chế; thay đổi phải được kiểm thử trên **đúng build JDK/vendor**.

## 4. JVM nạp security properties theo trình tự nào?

Sơ đồ khái niệm cho JDK 17/21 thông dụng:

~~~text
Khởi động JVM với các JVM arguments và system properties
                   │
                   ├─ System properties (bao gồm -Djava.security.properties)
                   │
                   ▼
Khởi tạo lớp java.security.Security / JDK security framework
                   │
                   ▼
Đọc master: <java.home>/conf/security/java.security
                   │
                   ├─ Đọc security.overridePropertiesFile trong master
                   │
                   ▼
Nếu được phép và có -Djava.security.properties
                   │
                   ├─ dấu "=": nạp thêm file, giá trị trùng khóa ghi đè
                   └─ dấu "==": thay thế toàn bộ bộ security properties mặc định
                   │
                   ▼
Tập Security properties ban đầu của JVM
                   │
                   ├─ API Security.getProperty / các JCA, JSSE consumer
                   └─ Một số giá trị có thể được cache khi framework khởi tạo
~~~

Cụ thể:

1. **Launcher tạo system properties** từ runtime và cờ `-D` truyền *trước tên main class hoặc `-jar`*. Các tham số này phải vào **JVM được chạy thật**.
2. Khi lớp `java.security.Security`/các thành phần bảo mật khởi tạo, JDK nạp master ở `conf/security/java.security`.
3. Nếu master cho phép tệp tùy biến (mặc định `security.overridePropertiesFile=true` trên Oracle/OpenJDK phổ biến), JDK xét system property `java.security.properties`. Có thể nạp thêm tệp hoặc thay toàn bộ.
4. Các giá trị security properties được phân phối cho framework. **Một số subsystem đọc/cache sớm** nên thay đổi sau giai đoạn này có thể không làm hành vi thay đổi.
5. Code Java vẫn có thể gọi `Security.getProperty`/`Security.setProperty`; điều đó không đồng nghĩa file được đọc lại hay tất cả service được reconfigure.

Đây là **luồng logic**, không khẳng định mọi property được mọi lớp nạp ở cùng một thời điểm. Thời điểm khởi tạo chi tiết phụ thuộc JDK/provider/subsystem. Vì vậy với chính sách quan trọng, **đặt ở startup, khởi động JVM mới, kiểm tra giá trị và kiểm thử hành vi**.

Nếu `security.overridePropertiesFile=false` trong master, tệp chỉ định qua `-Djava.security.properties` có thể bị bỏ qua theo chính sách này. Không “sửa” bằng cách đổi thêm cờ `-Dsecurity.overridePropertiesFile=true`: đây bản thân nó là **security property**, và master đã quyết định quyền dùng overlay. Thay đổi chính sách ấy phải được thực hiện bởi người quản trị JDK/hệ thống có thẩm quyền.

## 5. Một dấu `=` và hai dấu `==` có ý nghĩa hoàn toàn khác

Giả sử tệp gốc đã có:

~~~properties
# Master minh họa:
keystore.type=pkcs12
security.provider.1=SUN
security.provider.2=SunRsaSign
training.example=from-master
~~~

Và tệp bổ sung `extra.security` có:

~~~properties
# Extra minh họa:
training.example=from-extra
training.newKey=only-in-extra
~~~

### Chế độ bổ sung: `-Djava.security.properties=<URL>`

~~~bash
# URL trong ví dụ phải được thay bằng URI file thật trên máy chạy JVM.
java -Djava.security.properties=file:/absolute/path/extra.security MyApp
~~~

Đây là **một dấu bằng sau tên property** (ký tự `=` trong `-Dkey=value`). JVM tải master trước rồi tải tệp thêm. Khi hai file định nghĩa cùng một tên khóa, giá trị từ tệp thêm được chọn vì nạp sau:

~~~text
Security.getProperty("training.example") -> "from-extra"
Security.getProperty("training.newKey")  -> "only-in-extra"
Security.getProperty("keystore.type")    -> vẫn lấy từ master
security.provider.1, .2                -> vẫn lấy từ master
~~~

Phần hiển thị trên chỉ minh họa nguyên tắc trộn; không bảo đảm JDK thực tế dùng giá trị provider/keystore của ví dụ. **Append/overlay** là lựa chọn thường an toàn hơn vì giữ hầu hết mặc định bảo mật khác của JDK.

Lưu ý: nếu override một danh sách như `jdk.tls.disabledAlgorithms`, hệ thống thường **thay thế giá trị cả khóa**, không tự cộng thêm từng phần tử. Ghi một danh sách quá ngắn có thể **gỡ bỏ các thuật toán cấm mà JDK mặc định đã đặt**. Muốn tăng hạn chế phải so sánh đầy đủ giá trị hiện hành rồi xây dựng danh sách mới đúng policy; không coi append file đồng nghĩa append từng phần tử trong giá trị property.

### Chế độ thay thế: `-Djava.security.properties==<URL>`

~~~bash
java -Djava.security.properties==file:/absolute/path/complete.security MyApp
~~~

Có **hai dấu bằng liên tiếp sau tên property**; dấu đầu của cú pháp `-Dkey=value`, dấu thứ hai là ký tự đầu của *value* dùng để báo chế độ replace. Khi được cho phép, **tệp này thay toàn bộ tập security properties mặc định của master**. Những khóa không có trong tệp mới không tự được thừa hưởng từ master; nhiều dịch vụ JCA/JSSE có thể không khởi tạo/hoạt động như kỳ vọng nếu thiếu provider và policy cần thiết.

Chế độ này dùng cho profile JDK được xây dựng, quản lý và kiểm thử đầy đủ. **Không dùng một tệp chỉ 1–2 dòng để thử override hoàn toàn**; nó có thể làm mọi thuật toán/provider thiết yếu biến mất hoặc làm yếu chính sách.

| Cờ JVM | Nạp master | Nạp tệp ngoài | Giá trị trùng khóa |
| --- | --- | --- | --- |
| Không có `-Djava.security.properties` | Có | Không | Theo master |
| `-Djava.security.properties=file:/...` | Có | Bổ sung | Tệp ngoài ghi đè |
| `-Djava.security.properties==file:/...` | Thực hiện bước khởi tạo/kiểm tra master theo implementation, nhưng **tập cấu hình kết quả được thay thế** | Thay thế | Chỉ lấy tập cấu hình thay thế |

Bảng trên giả định master cho phép chỉ định tệp ngoài và tệp ngoài hợp lệ. **Cấu hình result** khác với việc khẳng định JDK không bao giờ truy cập master ở chế độ replace; không suy ra workflow kiểm tra/khởi tạo nội bộ chỉ từ hiệu lực cấu hình.

## 6. Hands-on: chứng minh overlay và system property ở hai nơi khác nhau

Tạo một tệp riêng có tên `security-overlay.properties` với nội dung an toàn cho demo:

~~~properties
# Chỉ là marker học tập, không thay đổi thuật toán hoặc provider của JDK.
training.profile=from-security-overlay
~~~

Lưu tệp vào thư mục mà process Java đọc được và lấy **file URI tuyệt đối**. Ví dụ máy Linux có thể lưu tại `/opt/demo/config/security-overlay.properties` rồi dùng `file:/opt/demo/config/security-overlay.properties`. Trên Windows, `C:\demo\security-overlay.properties` thường biểu diễn bằng URI `file:/C:/demo/security-overlay.properties`. Các đường dẫn này chỉ là ví dụ, không giả định chúng tồn tại.

### Chương trình đọc hai namespace (Java 17+)

~~~java
// File: SecurityConfigProbe.java
import java.security.Security;

public class SecurityConfigProbe {
    public static void main(String[] args) {
        String key = "training.profile";
        String securityValue = Security.getProperty(key);
        String systemValue = System.getProperty(key);

        System.out.println("security=" + securityValue);
        System.out.println("system=" + systemValue);
        System.out.println("jdk=" + System.getProperty("java.version"));
        System.out.println("java.home=" + System.getProperty("java.home"));
        System.out.println("overlay.option="
                + System.getProperty("java.security.properties"));
    }
}
~~~

Biên dịch:

~~~bash
javac SecurityConfigProbe.java
~~~

Chạy trên Linux/macOS với file URI ví dụ:

~~~bash
java -Djava.security.properties=file:/opt/demo/config/security-overlay.properties -Dtraining.profile=from-system SecurityConfigProbe
~~~

Các dòng đầu dự kiến:

~~~text
security=from-security-overlay
system=from-system
~~~

Ba dòng còn lại lần lượt hiển thị phiên bản JDK, đường dẫn Java runtime thật, và `file:/opt/demo/config/security-overlay.properties`. Giá trị cụ thể của `java.version` và `java.home` phụ thuộc môi trường nên không nên bịa thành một stdout cố định.

Nếu đổi thứ tự hai tham số `-D` thì kết quả không đổi; hai key đặt ở hai namespace khác nhau. Nếu bỏ `-Dtraining.profile=from-system`, dòng `system` thường thành `null` trong JVM chưa được set cùng key ở nơi khác, nhưng dòng `security` vẫn là `from-security-overlay`. Nếu bỏ cờ `-Djava.security.properties`, JDK không tự tìm tệp `security-overlay.properties` chỉ vì nó tồn tại trong thư mục.

**Kiểm tra sau thực thi:** xác nhận người chạy thực sự đã truyền hai cờ vào **JVM ứng dụng**, và xem tệp master hiện hữu. Trong môi trường CI/container/IDE, sửa biến shell nhưng không cập nhật launch configuration có thể khiến ứng dụng dùng cấu hình cũ.

## 7. Demo độc lập: `System.setProperty` và `Security.setProperty` không đồng bộ

Chương trình sau chỉ dùng **một khóa tự đặt cho học tập**, không thay chính sách TLS hay provider:

~~~java
// File: PropertyScopeDemo.java
import java.security.Security;

public class PropertyScopeDemo {
    private static final String KEY = "lesson.scope.example";

    public static void main(String[] args) {
        System.setProperty(KEY, "system");
        Security.setProperty(KEY, "security");

        System.out.println("afterSet.system=" + System.getProperty(KEY));
        System.out.println("afterSet.security=" + Security.getProperty(KEY));

        System.setProperty(KEY, "system-updated");

        System.out.println("afterUpdate.system=" + System.getProperty(KEY));
        System.out.println("afterUpdate.security=" + Security.getProperty(KEY));
    }
}
~~~

Biên dịch/chạy với Java 17+:

~~~bash
javac PropertyScopeDemo.java
java PropertyScopeDemo
~~~

Stdout chính xác trên JDK tiêu chuẩn, khi chương trình được phép đặt các property này:

~~~text
afterSet.system=system
afterSet.security=security
afterUpdate.system=system-updated
afterUpdate.security=security
~~~

Giải thích: dòng `Security.setProperty` thay đổi **security property map** của JVM đang chạy; dòng `System.setProperty` đổi **system property map** khác. Sau khi đổi chỉ system property, `Security.getProperty` vẫn trả `security`. Không có việc tự động ghi lại `java.security` lên đĩa; khi process thoát, các thay đổi runtime tự mất.

**Lưu ý:** ví dụ này chỉ chứng minh sự tách biệt API. Không sử dụng cách `Security.setProperty("jdk.tls.disabledAlgorithms", ...)` giữa lúc server đã chạy để triển khai chính sách. Một số lớp bảo mật có thể đã snapshot/cache các giá trị và tiếp tục vận hành theo cấu hình cũ.

## 8. Đọc/ghi security property: API, ý nghĩa và hạn chế

Hai API cơ bản:

~~~java
// Trích đoạn dùng trong ứng dụng; chưa phải chương trình độc lập.
String tlsPolicy = Security.getProperty("jdk.tls.disabledAlgorithms");
String certPolicy = Security.getProperty("jdk.certpath.disabledAlgorithms");
String defaultStore = Security.getProperty("keystore.type");

// Chỉ minh họa khả năng đặt property theo API, không phải cấu hình sản xuất.
Security.setProperty("training.runtime.marker", "enabled");
~~~

`Security.getProperty` trả `String` hoặc `null` nếu không có key; dùng nó để xác minh **giá trị đang nằm trong security property map**. Việc map chứa giá trị không chứng minh từng đối tượng SSLContext/CertPathValidator đang chạy đã cập nhật theo map ở thời điểm bạn nhìn.

`Security.setProperty` thay đổi cấu hình runtime trong cùng JVM nếu được cho phép. Về mặt bảo mật/vận hành, có bốn điều cần phân biệt:

1. **Value updated:** map trả đúng giá trị mới sau `setProperty`.
2. **Consumer loaded:** thành phần như JSSE/JCA/PKIX có đọc lại property chưa, hay đã cache?
3. **New instances vs existing instances:** tạo một đối tượng mới có thể đọc cấu hình khác với socket/context/tài nguyên đã tạo trước đó; không mặc định mọi instance cập nhật.
4. **File persistence:** thay đổi không tự sửa master/overlay; restart về cấu hình cũ nếu nguồn startup không thay đổi.

Vì cấu hình bảo mật thường cần nhất quán từ startup, lựa chọn triển khai ưu tiên là **tệp bảo mật đúng nguồn + startup flag + JVM mới + kiểm thử hành vi**. Khi cần thay đổi provider trong runtime, sử dụng API được thiết kế cho provider như `Security.addProvider` hoặc `Security.insertProviderAt` với đánh giá riêng; không đơn giản sửa chuỗi `security.provider.1` và giả định toàn bộ danh sách provider được rebuild.

## 9. System property thực sự được nạp từ đâu?

System properties là một map chứa nhiều thông tin khởi tạo do JVM/launcher tạo (ví dụ `java.home`, `java.version`, `user.dir`, `file.encoding` theo đặc tả phiên bản), cùng các giá trị ứng dụng truyền vào bằng `-D` và code Java.

Cú pháp chuẩn:

~~~bash
java -Dapp.profile=dev -Dapp.timeout=2500 MyApp
~~~

Trong code:

~~~java
// Trích đoạn API.
String profile = System.getProperty("app.profile");
String defaulted = System.getProperty("app.timeout", "5000");
System.setProperty("app.profile", "staging");
String runtimeHome = System.getProperty("java.home");
~~~

**`-D` tạo system property.** Trừ khi tài liệu của một tính năng JDK quy định rõ rằng system property đó được dùng làm công tắc cho security subsystem (chẳng hạn `java.security.properties` hoặc `java.security.debug`), `-D` không biến một security property cùng tên thành cấu hình bảo mật JDK.

Nguồn runtime có thể đến từ launcher/IDE, cấu hình Gradle/Maven plugin, service manager, wrapper script, biến môi trường có tác dụng chèn JVM options hoặc API. Các công cụ xây dựng có thể có **JVM riêng**: `-Dfoo=bar` truyền cho Gradle daemon không tự động đồng nghĩa tham số ấy đã được truyền cho JVM của service con. Kiểm tra runtime của ứng dụng, không chỉ terminal hoặc build log.

Không coi `System.getProperties()` là danh sách security properties. Ngược lại, `Security.getProperty("java.home")` có thể trả null trong khi `System.getProperty("java.home")` trả đường dẫn thực tế. Tránh dùng tên `security.*` cho cấu hình nghiệp vụ rồi suy rằng JDK tự biết tên đó; JDK chỉ dùng các tên được tài liệu hóa.

## 10. Một số security properties có ảnh hưởng thực tế lớn

| Security property | Tác động phổ biến | Lưu ý khi vận hành |
| --- | --- | --- |
| `jdk.tls.disabledAlgorithms` | Những thuật toán/protocol/cipher suite hoặc điều kiện khóa không được JSSE chấp nhận | Dựa vào danh sách hiệu lực của JDK đang chạy; đừng ghi đè bằng danh sách thiếu phần tử cấm |
| `jdk.certpath.disabledAlgorithms` | Hạn chế trong kiểm tra đường đi chứng chỉ | Ảnh hưởng việc xác nhận CA/chain; có thể tạo lỗi handshake hoặc kiểm tra chữ ký |
| `jdk.jar.disabledAlgorithms` | Hạn chế chữ ký JAR yếu khi kiểm tra | Có thể làm artifact cũ bị coi là unsigned hoặc không còn đáng tin trong một số ngữ cảnh |
| `security.provider.n` | Danh sách provider mặc định và thứ tự ưu tiên | Xóa/thay provider có thể khiến `Cipher`, `Signature`, `KeyStore` hoặc TLS không tìm thấy implementation |
| `keystore.type` | Loại keystore mặc định khi code yêu cầu default | Không phải đường dẫn hay mật khẩu của truststore |
| `ssl.KeyManagerFactory.algorithm` / `ssl.TrustManagerFactory.algorithm` | Thuật toán mặc định cho factory khi không chỉ định cụ thể | Giá trị được dùng theo cách subsystem khởi tạo |
| `crypto.policy` | Cách JCE sử dụng chính sách mức độ mật mã | Cần kiểm tra docs/vendor/JDK khi điều chỉnh |
| `securerandom.source` | Nguồn đầu vào phục vụ triển khai SecureRandom của một số provider | Không đoán behavior entropy từ tên; tùy JDK và OS |

Ví dụ, TLS handshake thất bại khi chuyển JDK 17 sang bản vá mới có thể do **default disabled algorithms thay đổi**, dù không ai sửa code ứng dụng. Trước khi nới lỏng chính sách, so sánh chính xác runtime/JDK vendor, security properties, certificate chain, key sizes và negotiated protocol/cipher suite giữa hai môi trường. Nới lỏng cấm thuật toán là thay đổi an toàn thông tin, không nên thực hiện chỉ để “cho chạy được”.

Một property đọc đúng không đảm bảo nó áp dụng tới mọi framework: một thư viện TLS riêng, một provider bên thứ ba hoặc `SSLContext` được khởi tạo bằng cấu hình ứng dụng có thể có quy tắc riêng. Cần kiểm tra **chủ thể tiêu thụ property** trong tài liệu và quan sát hành vi runtime.

## 11. Mối quan hệ với truststore, keystore và TLS

Tình huống thường gặp: ứng dụng cần tin cậy CA nội bộ khi gọi HTTPS.

Có thể dùng **system property** cho cấu hình truststore mặc định của các đường đi JSSE hỗ trợ:

~~~bash
java -Djavax.net.ssl.trustStore=/opt/app/certs/truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 MyApp
~~~

Câu lệnh minh họa **không đưa mật khẩu thật lên CLI**; cơ chế cấp credential phải phù hợp với môi trường và tránh rò rỉ qua command line/log. Một ứng dụng có thể tự tạo `SSLContext`/`TrustManager` nên không nhất thiết sử dụng các default system properties này.

Nếu muốn đặt thuật toán mặc định cho `TrustManagerFactory`, đó thường là một **security property**:

~~~properties
# Ví dụ trong overlay java.security, chỉ dùng theo chính sách đã duyệt:
ssl.TrustManagerFactory.algorithm=PKIX
~~~

Hai cấu hình trả lời hai câu hỏi khác nhau: **“nạp truststore ở đâu?”** và **“factory algorithm mặc định là gì?”**. Đặt `-Dssl.TrustManagerFactory.algorithm=...` sẽ chỉ tạo system property cùng tên; không nên kỳ vọng nó thay security property.

Ngược lại, nhập `javax.net.ssl.trustStore=/...` vào `java.security` không có nghĩa JSSE sẽ tự dùng giá trị đó như system property. Một tệp chứa cú pháp `key=value` không quyết định loại property; **API/consumer nào đọc khóa** mới quyết định.

## 12. JDK 24+ bổ sung `include` trong security properties file

**Lưu ý phiên bản:** cơ chế `include` để lồng nhiều tệp security properties vào nhau là tính năng được bổ sung trong **JDK 24** (xem Oracle JDK 24 Migration Guide). Không áp dụng cú pháp này cho JDK 17/21 rồi mặc định nó sẽ hoạt động như trên JDK 24.

Với JDK 24+ có hỗ trợ, tệp master hoặc tệp bổ sung có thể chứa chỉ thị:

~~~properties
# Chỉ minh họa trên JDK 24+:
include /opt/app/security/security-profile.properties
~~~

Các property trong file được include sẽ được nạp ở vị trí chỉ thị. Nếu cùng một key được định nghĩa trước và sau `include`, thứ tự có thể quyết định giá trị cuối. Các include lồng nhau cần có giới hạn để tránh vòng lặp; JDK hiện hành xử lý trường hợp đường dẫn không hợp lệ/vòng lặp theo chính sách triển khai. Không dùng `include` như một cơ chế thay thế tin cậy dữ liệu từ mạng.

JDK 24+ cũng hỗ trợ các placeholder bằng **system properties** trong đường dẫn include, ví dụ:

~~~properties
include ${java.home}/conf/security/team-profile.security
~~~

Phải kiểm tra file thực tế tồn tại và đường dẫn được resolve trên JDK đang chạy. Đặc biệt, với file include nằm trên hệ thống tệp, **quyền chỉnh sửa của file và mọi thư mục cha** là một phần của threat model: người sửa được file/đường dẫn được phép thay đổi chính sách bảo mật của JVM.

Phân biệt rõ:

- `-Djava.security.properties=file:/...` là **cơ chế launcher** có trên JDK 17/21 để chỉ định tệp bổ sung.
- `include path` nằm **bên trong nội dung security properties file**, là cơ chế quản lý nhiều tệp của các JDK hỗ trợ (JDK 24+).
- Trong `include`, đường dẫn filesystem không dùng cùng cú pháp URL với giá trị của `-Djava.security.properties`. Tham chiếu tài liệu của đúng JDK trước khi viết profile dùng include.

Để triển khai Java 17/21, cách ít phụ thuộc nhất là một **overlay file duy nhất** với các khóa cần thay và truyền bằng launcher; đừng tự thiết kế include syntax cho JDK chưa hỗ trợ.

## 13. Debug: thấy giá trị hiệu lực thay vì đoán cấu hình

### A. Trong ứng dụng: đọc đúng API

~~~java
// Trích đoạn quan sát, không thay đổi cấu hình.
System.out.println("runtime: " + System.getProperty("java.runtime.version"));
System.out.println("java.home: " + System.getProperty("java.home"));
System.out.println("overlay arg: " + System.getProperty("java.security.properties"));
System.out.println("keystore type: " + Security.getProperty("keystore.type"));
System.out.println("TLS restrictions: "
        + Security.getProperty("jdk.tls.disabledAlgorithms"));
~~~

Nếu `overlay arg` xuất hiện nhưng `keystore type`/`TLS restrictions` không đúng mong muốn, kiểm tra `security.overridePropertiesFile`, cú pháp của URI/path, nội dung tệp và điều kiện runtime. Nếu đang thay security property bằng `-D<security-key>=...` thì bạn đã đặt nhầm namespace.

### B. Chẩn đoán của JDK

Bật trace nạp security properties khi khởi chạy ứng dụng:

~~~bash
java -Djava.security.debug=properties MyApp
~~~

Các dòng trace có tiền tố `properties` cho biết cấu hình bảo mật được nạp và giá trị kết quả; đây là **system property điều khiển debug**, không phải security property. Trên JDK có hỗ trợ (ví dụ JDK 21+), có thể xem thiết lập bảo mật:

~~~bash
java -XshowSettings:security -version
java -XshowSettings:security:properties -version
~~~

Tham số `-XshowSettings:security` có các danh mục về properties, providers và TLS theo tài liệu JDK tương ứng. Nếu lệnh không được JDK phiên bản cũ hỗ trợ, dùng code probe hoặc `-Djava.security.debug=properties`. Cần phân biệt những gì **JVM load** với những gì **một SSLContext/provider đã khởi tạo thực sự dùng**: với lỗi TLS, kiểm tra cả kết quả handshake, certificate validation và cấu hình framework.

Với những hệ thống bật Java Flight Recorder, JDK hiện đại có event `jdk.InitialSecurityProperty` để quan sát giá trị ban đầu. Đây là phương án quan sát chuyên sâu, không cần thiết cho bài test cơ bản.

**An toàn log:** output có thể tiết lộ đường dẫn cấu hình, tên provider, cấu trúc deployment và giá trị nhạy cảm. Không bật debug rộng trên production quá lâu hoặc gửi toàn bộ log công khai; sử dụng kiểm soát truy cập và lọc dữ liệu theo chính sách.

### C. Quy trình xác minh một thay đổi

1. Ghi nhận `java -version` của **JVM ứng dụng** và `java.home` runtime.
2. Xem master/overlay thật sự nằm ở đâu và ai có quyền sửa.
3. Xác nhận JVM nhận đúng `-Djava.security.properties=...` và cơ chế bổ sung được cho phép.
4. Đọc giá trị qua `Security.getProperty` (không qua `System.getProperty`), đồng thời đối chiếu trace.
5. Thử tác động trong điều kiện kiểm thử có kiểm soát: TLS handshake được phép/bị chặn, kiểm tra CertPath, hay provider được chọn đúng.
6. Restart JVM khi thay cấu hình startup; tránh suy luận từ trạng thái một process đã cache config.
7. Có đường rollback chính sách và kiểm thử lại sau cập nhật JDK/vendor.

## 14. Thay đổi cấu hình trong thực tế: từ development đến production

**Tập tin overlay nên là artifact của vận hành.** Thay vì patch master của JDK trong image, lưu overlay riêng cùng cấu hình triển khai đã review, giới hạn quyền ghi, kiểm soát phiên bản, dùng đường dẫn tuyệt đối và đưa launcher argument vào manifest/service command. Khi nâng JDK, so sánh baseline mới để tránh giữ mãi override gây yếu bảo mật.

Một ví dụ trên Linux (minh họa câu lệnh khởi động, đường dẫn phụ thuộc môi trường):

~~~bash
java \
  -Djava.security.properties=file:/opt/app/config/security-extra.properties \
  -Dapp.environment=production \
  -jar service.jar
~~~

`-Djava.security.properties` và `-Dapp.environment` đứng **trước `-jar`** vì đó là JVM options. Nếu viết:

~~~bash
java -jar service.jar -Djava.security.properties=file:/opt/app/config/security-extra.properties
~~~

thì phần `-D...` nằm **sau** tên JAR, thường chỉ trở thành đối số của chương trình trong `main(String[] args)`; nó không phải JVM system property có tác dụng nạp tệp bảo mật. Với Gradle/Maven/IDE/container, đảm bảo options nằm trong cấu hình chạy **JVM ứng dụng**.

Thay file overlay trên đĩa trong khi JVM đang chạy **không làm JDK tự nạp lại file**. Một rolling restart với canary test là workflow an toàn hơn để cập nhật cấu hình chính sách mật mã trong hệ thống nhiều instance. Nếu thay danh sách provider, cần kiểm thử cả khởi động, keystore, TLS, ký/xác minh, và các thuật toán đang sử dụng.

Tối thiểu nên lưu trong change record: đường dẫn security file và JDK build; danh sách khóa thay và giá trị trước/sau; lý do bảo mật; kết quả test happy path/negative path; người duyệt; cách rollback. Tránh đưa mật khẩu và private key vào tệp `java.security` hoặc log chẩn đoán.

## 15. Các lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân có thể xảy ra | Cách xử lý |
| --- | --- | --- |
| `System.getProperty("jdk.tls.disabledAlgorithms")` có giá trị mới nhưng TLS không đổi | Đặt security property bằng `-D` sai namespace | Kiểm tra `Security.getProperty`; sửa overlay hoặc master theo chính sách |
| Đã sửa `java.security` nhưng JVM không thay đổi | Sửa nhầm JDK, process chưa restart, hoặc đọc nhầm runtime path | Log `java.home` của process, restart sau cấu hình |
| Có `-Djava.security.properties=...` nhưng key vẫn như mặc định | File sai URI/path, thiếu quyền, master không cho overlay, key bị ghi đè bởi cấu hình khác | Chạy debug properties, xác minh file và policy |
| Có dấu `==` nhưng các thuật toán JCA không tìm thấy | Đã thay toàn bộ master bằng tệp không đầy đủ | Khôi phục append hoặc dùng profile thay thế đầy đủ đã được kiểm thử |
| Thêm một mục vào `jdk.tls.disabledAlgorithms` bằng cách ghi đè cả key | Vô tình xóa các hạn chế khác khỏi danh sách | So sánh toàn bộ giá trị gốc; dùng danh sách đầy đủ theo policy |
| `Security.setProperty` trả về bình thường nhưng TLS session cũ vẫn dùng cấu hình cũ | Cache/initialization của subsystem, SSLContext/socket đã tạo | Cấu hình startup, tạo JVM/context mới theo đặc tả |
| Cùng tên property nhưng hai API cho kết quả khác | Hai namespace độc lập | Đúng hành vi; đọc từ API mà consumer sử dụng |
| Java 21 không nhận `include` như kỳ vọng | Cơ chế include là tính năng JDK 24+ | Dùng overlay `-Djava.security.properties` ở JDK 17/21 |
| Giá trị đọc từ dev khác production | Khác JDK vendor/patch, master, overlay, service launcher, image hoặc secret management | So sánh java.home, phiên bản, startup args và security debug có kiểm soát |
| Ghi debug bảo mật lên log công khai | Trace chẩn đoán chứa metadata cấu hình và có thể chứa thông tin nhạy cảm | Thu hẹp debug, bảo vệ log, lọc trước chia sẻ |

**Quy tắc quan trọng:** “Không có lỗi khi gán property” chỉ chứng minh thao tác cập nhật map diễn ra. Một thay đổi bảo mật chỉ được coi là hoàn thành sau khi đã **đọc lại đúng property** và **chứng minh bằng bài test hành vi** rằng chính sách được tiêu thụ theo thiết kế.

## 16. Bài tập tình huống và đáp án

**Bài 1.** Team chạy `java -Djdk.certpath.disabledAlgorithms=... MyApp` và thấy `System.getProperty` có giá trị mới. Có thể khẳng định CertPath dùng danh sách mới không?

**Đáp án:** Không. Đây là security property trong JDK phổ biến; phải cấu hình trong master/overlay và đọc bằng `Security.getProperty`. Sau đó kiểm thử xác minh chain phù hợp; đừng bỏ qua cache hoặc khác biệt JDK/provider.

**Bài 2.** `java.security.properties` đã trỏ vào tệp bổ sung, nhưng `Security.getProperty("training.profile")` là `null`. Kể ba kiểm tra đầu tiên.

**Đáp án:** (1) Chắc JVM đang chạy nhận đúng `-Djava.security.properties` trước tên class/`-jar`; (2) tệp tồn tại, URI/path và quyền đọc đúng; (3) master cho phép override và key có đúng tên/cú pháp. Dùng `-Djava.security.debug=properties` để đối chiếu.

**Bài 3.** Hai lệnh sau khác nhau thế nào?

~~~bash
java -Djava.security.properties=file:/opt/app/extra.security MyApp
java -Djava.security.properties==file:/opt/app/extra.security MyApp
~~~

**Đáp án:** Lệnh đầu thêm/ghi đè các khóa từ tệp extra lên master; lệnh sau thay toàn bộ tập cấu hình security của master bằng nội dung tệp chỉ định. Nếu tệp extra chỉ có một marker học tập, lệnh thứ hai rất nguy hiểm và có thể khiến provider/algorithms bị thiếu.

**Bài 4.** Bạn gọi `Security.setProperty("ssl.TrustManagerFactory.algorithm", "MyAlg")`, nhưng đối tượng `TrustManagerFactory` tạo từ trước vẫn không đổi. Đây có phải lỗi JVM?

**Đáp án:** Không nhất thiết. Việc cập nhật map không bảo đảm cấu hình đã cache được áp dụng lại. Cần đọc docs của component, kiểm tra thời điểm khởi tạo và thử trên JVM/context mới. Với thay đổi lâu dài, cấu hình trước startup.

**Bài 5.** Một nhóm tạo tệp `java.security` đầy đủ bằng cách copy từ JDK 17, rồi dùng `==` để thay thế khi nâng lên JDK 21. Có rủi ro gì?

**Đáp án:** JDK 21 có thể thêm/đổi provider, thuật toán hạn chế và mặc định; profile cũ đã thay toàn bộ làm mất thay đổi bảo mật của JDK mới. Phải so sánh baseline vendor/bản vá, kiểm thử thay thế đầy đủ hoặc chuyển sang overlay phạm vi nhỏ.

**Bài 6.** Bên vận hành đặt `javax.net.ssl.trustStore` trong file overlay rồi hỏi vì sao ứng dụng không đổi truststore. Giải thích.

**Đáp án:** `javax.net.ssl.trustStore` thường là system property được JSSE sử dụng trong đường đi mặc định. Overlay `java.security` tạo security property cùng tên, không tự tạo system property. Đưa tham số vào launcher của JVM ứng dụng hoặc cấu hình theo API/framework đang sử dụng.

**Bài 7.** Trong Docker, container có JDK 21 nhưng lệnh `java -version` bạn chạy trên host báo JDK 24. Nên dùng đường dẫn master nào?

**Đáp án:** Dùng Java runtime trong **container của ứng dụng**; kiểm tra `System.getProperty("java.home")` và phiên bản JVM ở **chính process ứng dụng**, rồi tìm tệp `conf/security/java.security` tương ứng. Đường dẫn JDK host không quyết định cấu hình trong container.

## 17. Tài liệu chuẩn và kiểm chứng

- Oracle Java SE 21 — **The Security Properties File**: https://docs.oracle.com/en/java/javase/21/security/security-properties-file.html
- Oracle Java SE 21 — **java.security.Security API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Security.html
- Oracle Java SE 21 — **Troubleshooting Security** (debug và `-XshowSettings`): https://docs.oracle.com/en/java/javase/21/security/troubleshooting-security.html
- Oracle Java SE 21 — **JSSE Reference Guide** (TLS/security properties): https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html
- Oracle JDK 24 — **Security Updates** (bổ sung `include`): https://docs.oracle.com/en/java/javase/24/migrate/security-updates.html
- Oracle Java SE 24 — **The Security Properties File** (syntax và thứ tự `include`): https://docs.oracle.com/en/java/javase/24/security/security-properties-file.html

**Ghi nhớ:** `System` và `Security` quản lý **hai không gian property riêng**. `-Djava.security.properties` là *system property để chỉ tệp nạp security properties*; một dấu `=` nghĩa là overlay, hai dấu `==` nghĩa là thay thế master theo quy tắc của JDK. Chọn đúng file của runtime, nạp cấu hình trước khi subsystem bảo mật khởi tạo, đọc lại bằng `Security.getProperty` và kiểm thử **hành vi bảo mật thực tế** trước khi khẳng định thay đổi đã có hiệu lực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-002">22.2 jdk.tls.disabledAlgorithms, jdk.certpath.disabledAlgorithms và chính sách thuật toán</a>

<details>
<summary>Xem chi tiết</summary>

# 22.2. `jdk.tls.disabledAlgorithms`, `jdk.certpath.disabledAlgorithms` và chính sách thuật toán

## 1. Mục tiêu và bối cảnh vận hành

Sau bài này, bạn có thể giải thích được **hai lớp chính sách thuật toán mật mã của JDK** — giới hạn thuật toán trong TLS/DTLS và giới hạn thuật toán khi xác minh chuỗi chứng chỉ — rồi cấu hình, kiểm tra và triển khai thay đổi mà không vô tình hạ mức bảo vệ đang có.

Trong một hệ thống Java production, việc thư viện “có hỗ trợ” một thuật toán không đồng nghĩa thuật toán đó “được phép dùng” cho một kết nối TLS hoặc một chứng chỉ. JDK có các **security property** để từ chối giao thức, thuật toán ký, kiểu trao đổi khóa, kích thước khóa hoặc đường chứng chỉ không đạt chính sách.

Hai property trọng tâm:

- `jdk.tls.disabledAlgorithms`: SunJSSE dùng để hạn chế những thuật toán/cơ chế không được phép trong thương lượng TLS/DTLS, bao gồm phiên bản giao thức, bộ mã, thuật toán ký, nhóm trao đổi khóa và điều kiện trên khóa. Khi xác minh chứng chỉ trong TLS, những hạn chế liên quan từ property này cũng có thể được kiểm tra.
- `jdk.certpath.disabledAlgorithms`: triển khai PKIX CertPath của JDK dùng để loại những thuật toán/khóa không đạt yêu cầu trong xây dựng và xác minh **certification path** (chuỗi chứng chỉ), kể cả một số cấu trúc phục vụ xác minh như CRL hoặc OCSP response.

**Phạm vi:** đây là cấu hình trong JVM đang chạy. Nó không tự đổi cấu hình Nginx, trình duyệt, OpenSSL, server đối tác hay thiết bị cân bằng tải. Và cũng không thay thế kiểm soát nguồn tin cậy (trust anchors), hostname verification, thời hạn chứng chỉ, revocation, key usage, hay chính sách cấp quyền nghiệp vụ.

## 2. Khác nhau giữa TLS policy và CertPath policy

| Khía cạnh | `jdk.tls.disabledAlgorithms` | `jdk.certpath.disabledAlgorithms` |
| --- | --- | --- |
| Thành phần áp dụng chính | SunJSSE và phần TLS/DTLS của JDK | PKIX CertPath builder/validator và các thành phần sử dụng nó |
| Đối tượng bị ràng buộc | Phiên bản TLS, cipher suite, key exchange, named group, signature scheme, khóa/chứng chỉ trong TLS | Thuật toán ký chứng chỉ, khóa công khai, chuỗi chứng chỉ, CRL/OCSP liên quan |
| Ví dụ dễ thấy | Không thương lượng TLSv1.0/TLSv1.1; từ chối RC4 hoặc DH khóa quá yếu | Từ chối chứng chỉ có RSA key quá ngắn hoặc SHA-1 trong điều kiện nhất định |
| Thời điểm phát hiện | Khi dựng TLS context/handshake và xác thực peer, tùy trường hợp | Khi build/validate certification path |
| Phạm vi ứng dụng | Kết nối JSSE dùng cùng runtime: HTTPS client, TLS server, mTLS, JDBC TLS nếu driver dựa vào JSSE, v.v. | Xác minh chuỗi X.509 qua PKIX, có thể cả trong và ngoài TLS |
| Có thể dùng `SSLParameters` để vượt qua? | Không được phép thương lượng thuật toán đã bị chính sách JDK cấm | Không; phải dùng đường xác minh và trust policy hợp lệ |
| Có chứng minh ứng dụng đã an toàn chưa? | Chưa: cần handshake thật, endpoint identity, trust, key lifecycle | Chưa: cần đúng trust anchors, kiểm tra danh tính, revocation khi bắt buộc |

Đường đi khái niệm của một TLS handshake có xác thực chứng chỉ:

```text
Client JSSE                                      Server JSSE
     |                                               |
     |  ClientHello: supportedVersions, suites, ...  |
     |---------------------------------------------->|
     |             lựa chọn phiên bản / suite /     |
     |             groups / signature schemes        |
     |       theo capabilities và TLS policy         |
     |<----------------------------------------------|
     |  ServerHello + Certificate + các token khác    |
     |                                               |
     |  Xác minh certificate path: PKIX / trust       |
     |  + jdk.certpath.disabledAlgorithms             |
     |  + các giới hạn TLS liên quan                  |
     |  + kiểm tra tên peer theo ứng dụng/protocol    |
     |                                               |
     |-------- Finished / application data ---------->|
```

Đây là sơ đồ logic, không phải số lượng/bố cục message cố định cho mọi phiên bản TLS. Trong TLS 1.3, thứ tự và nội dung handshake khác TLS 1.2. Cả hai lớp chính sách có thể cùng làm một kết nối thất bại; phải đọc log để phân biệt nguyên nhân thật.

### Một trường hợp thường gây nhầm

Một server hỗ trợ TLS 1.2 và có chứng chỉ dùng `SHA256withRSA` nhưng khóa RSA quá ngắn. Khả năng thương lượng TLS 1.2 không đảm bảo chứng chỉ được chấp nhận. Ngược lại, một chuỗi chứng chỉ mạnh không cứu được kết nối nếu hai phía không còn giao thức/cipher suite nào cùng được phép sử dụng.

## 3. “Có thuật toán” khác “thuật toán được phép”

Hệ sinh thái Java có nhiều lớp điều khiển:

1. **Provider capabilities:** `Signature.getInstance("SHA1withRSA")` có thể tạo object thành công vì provider vẫn cung cấp engine. Điều đó **không** chứng minh một chứng chỉ ký bằng SHA-1 được PKIX hoặc JSSE cho phép.
2. **Supported TLS:** `SSLContext.getSupportedSSLParameters()` có thể liệt kê protocol/cipher có implementation. **Supported** không có nghĩa được thương lượng dưới security policy hiện tại.
3. **Enabled TLS:** `getDefaultSSLParameters()` hoặc `SSLSocket.getEnabledProtocols()` cho biết giá trị được bật ban đầu/đã cấu hình trên object. Đó vẫn **chưa phải** kết quả thương lượng.
4. **Security property restrictions:** giá trị trong `jdk.tls.disabledAlgorithms` và `jdk.certpath.disabledAlgorithms` có thể khiến handshake/CertPath validation thất bại dù ứng dụng cố chỉ định thuật toán.
5. **Application policy:** `SSLParameters.setAlgorithmConstraints(...)`, chọn protocol/cipher, trust manager và kiểm tra danh tính có thể bổ sung hạn chế riêng, tùy cách ứng dụng tích hợp.

Ví dụ tưởng như hợp lệ nhưng **không có hiệu lực vượt policy**:

```java
// Đây là fragment dùng một SSLSocket đã tạo, chưa kết nối.
// Nếu TLSv1 đang nằm trong jdk.tls.disabledAlgorithms,
// yêu cầu bên dưới không thể khiến TLSv1 được thương lượng.
socket.setEnabledProtocols(new String[] { "TLSv1" });
```

Đừng chỉ in `getEnabledProtocols()` rồi kết luận server có thể handshake TLSv1. Phải thử kết nối thực và xem **negotiated protocol** từ `SSLSession.getProtocol()` sau handshake thành công, đồng thời kiểm tra certpath.

## 4. Cú pháp chung của DisabledAlgorithms

Các mục trong property được phân tách bởi dấu phẩy. Một mục thường gồm **tên thuật toán** và có thể có một hay nhiều **constraint** đi kèm:

```text
DisabledAlgorithms :=
    DisabledAlgorithm (, DisabledAlgorithm)*

DisabledAlgorithm :=
    AlgorithmName [Constraint] (& Constraint)* 
    | include OtherSecurityProperty

Constraint :=
    keySize <|<=|==|!=|>=|> N
    | jdkCA
    | denyAfter YYYY-MM-DD
    | usage TLSServer|TLSClient|SignedJAR [nhiều usage]
```

Đây là ký pháp mô tả cấu trúc, không phải file `java.security` để dán trực tiếp. Xem phần chú thích cú pháp ngay trong `<JAVA_HOME>/conf/security/java.security` của JDK thực tế.

### 4.1. Dấu phẩy tạo các quy tắc từ chối độc lập

Ví dụ:

```properties
jdk.certpath.disabledAlgorithms=MD2, MD5, RSA keySize < 2048
```

Đây là **dòng minh họa cú pháp**, không phải policy hoàn chỉnh cho production. Có ba điều kiện từ chối độc lập: MD2, MD5, hoặc khóa RSA có kích thước dưới 2048 bit trong ngữ cảnh CertPath. Thêm danh sách ngắn này để **thay thế** property đang có sẽ có nguy cơ xóa các giới hạn bảo mật khác của JDK.

Trong biểu thức `RSA keySize < 2048`, dấu `<` là so sánh **nhỏ hơn**, nên khóa 2048 bit không bị từ chối bởi **chính điều kiện này**; khóa 1024 bit thì có. `RSA keySize <= 2048` sẽ từ chối cả 2048 bit. Không nhầm `<` và `<=` khi review chính sách.

### 4.2. Ký hiệu `&` kết hợp ràng buộc trên cùng mục

```properties
jdk.certpath.disabledAlgorithms=SHA1 jdkCA & usage TLSServer
```

Đây là ví dụ một mục, không phải thay thế cho toàn bộ policy. Ý nghĩa: từ chối SHA-1 trong **certificate path cho TLS server authentication** khi đường chứng chỉ kết thúc ở **trust anchor do JDK đánh dấu `jdkCA`** trong bộ root mặc định tương ứng. Không nên diễn giải thành “cấm mọi SHA-1 trong Java” hoặc “mọi chuỗi tới root tự thêm đều bị cùng ràng buộc này”.

- `usage TLSServer`: giới hạn trong bối cảnh xác thực chứng chỉ server của TLS.
- `usage TLSClient`: giới hạn khi xác thực certificate chain của TLS client/mTLS.
- `usage SignedJAR`: usage dành cho đường ký/xác minh JAR ở nơi triển khai hỗ trợ; đừng nhầm với chính sách TLS.
- `jdkCA`: điều kiện về loại trust anchor mà triển khai JDK nhận biết. Tự import một root CA nội bộ vào truststore tùy biến **không tự làm nó thành `jdkCA`**.
- `denyAfter YYYY-MM-DD`: điều kiện dựa vào mốc ngày UTC theo semantics được JDK nêu trong policy; không giống `NotAfter` của chứng chỉ. Hành vi với chữ ký JAR có timestamp cần xem riêng.
- `include jdk.disabled.namedCurves`: đưa tập named curve bị vô hiệu hóa từ **security property khác** vào policy; không phải đường dẫn file.

**Cảnh báo về logic:** dấu phẩy giữa các mục nghĩa là bất kỳ mục phù hợp nào cũng có thể khiến thuật toán bị chặn; `&` dùng để ràng buộc nhiều điều kiện cho một mục. Thêm ràng buộc tùy tiện không có nghĩa tăng độ an toàn nếu bạn đồng thời thay mất các mục còn lại.

### 4.3. Tên thuật toán và named curves

Trong những triển khai JDK nêu trên, các ràng buộc có thể khớp thành phần của tên thuật toán (ví dụ SHA1 trong `SHA1withRSA`) và danh sách named curves. Không thay tên thuật toán thành tên tùy ý: nên dùng **standard algorithm names** JCA/JSSE và kiểm tra thực tế. Ví dụ `TLSv1`, `RC4`, `DH keySize < 2048`, `RSA keySize < 2048`, `secp224r1` có các ý nghĩa khác nhau.

- `TLSv1` là phiên bản giao thức TLS 1.0, không phải từ khóa “mọi TLS”.
- `DH keySize < 2048` thường nói về Diffie–Hellman key exchange với key size dưới ngưỡng trong ngữ cảnh áp dụng.
- `RSA keySize < 2048` nói về ràng buộc kích cỡ khóa RSA, không phải tự động thay thế RSA bằng thuật toán khác.
- Danh sách named group đang hỗ trợ cũng chịu ảnh hưởng bởi provider/JDK và cấu hình như `jdk.tls.namedGroups`; property đó là lựa chọn riêng, không phải cách vượt qua `disabledAlgorithms`.

## 5. `jdk.tls.disabledAlgorithms`: chặn yếu tố nào trong TLS?

SunJSSE dùng property này để giới hạn việc lựa chọn hoặc sử dụng:

- **Protocol version:** `SSLv3`, `TLSv1`, `TLSv1.1`; mỗi phiên bản JDK có baseline khác nhau, các bản JDK 17 cập nhật thường chặn TLS 1.0/1.1 theo mặc định.
- **Cipher suites/cipher components:** RC4, DES, 3DES hoặc các bộ mã cụ thể có thể nằm trong danh sách tùy bản phát hành.
- **Key exchange:** kích thước DH, EC, kiểu trao đổi khóa và named groups. Loại bỏ nhóm yếu có thể làm hai peer không còn nhóm chung.
- **Signature schemes/algorithm:** ví dụ chính sách chống MD5 với RSA hoặc giới hạn chữ ký SHA-1 trong handshake, tùy cơ chế và JDK.
- **Peer authentication:** khi dùng chứng chỉ, những ràng buộc liên quan được áp dụng thêm lúc xây dựng/kiểm tra chain, song song với `jdk.certpath.disabledAlgorithms`.

Ví dụ **dòng rút gọn để giải thích**, không phải baseline JDK thật:

```properties
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, RC4, DH keySize < 2048
```

Dòng này chỉ giúp đọc cú pháp: ngăn thương lượng ba phiên bản/giao thức đã nêu và các điều kiện cấm còn lại. **Không deploy dòng rút gọn này**: khi override security property, các mục ban đầu không được tự giữ lại. Để thay đổi an toàn, phải bắt đầu từ toàn bộ giá trị effective hiện có và chỉ thêm những constraint được phê duyệt.

### Tình huống thực tế: client được bật TLS 1.2 nhưng vẫn handshake thất bại

Giả sử server cũ chỉ có cipher suite dùng RC4. Client mở `SSLContext("TLS")` và `setEnabledProtocols(new String[] {"TLSv1.2"})`. Cả hai có vẻ dùng TLS 1.2, nhưng nếu RC4 bị `jdk.tls.disabledAlgorithms` chặn thì không còn cipher suite chấp nhận được. Dấu hiệu có thể là `SSLHandshakeException` với thông tin “no appropriate protocol”, “no cipher suites in common”, hoặc “handshake_failure”, tùy peer/JDK. Cần kiểm tra **danh sách giao nhau** theo tất cả các điều kiện, không chỉ so tên phiên bản TLS.

Tương tự, một service có chứng chỉ hợp lệ nhưng chỉ hỗ trợ một signature scheme/EC named group đã bị cấm vẫn có thể bắt tay thất bại. Chứng chỉ, cipher suite và key exchange cần đạt chính sách cùng lúc.

## 6. `jdk.certpath.disabledAlgorithms`: ranh giới của kiểm tra chuỗi chứng chỉ

PKIX CertPath xây dựng/xác minh một đường từ chứng chỉ cuối (end-entity/leaf) qua những CA trung gian đến một trust anchor tin cậy. Trong JDK, property này có thể chặn:

- Certificate signature dùng MD2/MD5 hoặc thuật toán chữ ký bị loại khỏi policy.
- Khóa RSA, DSA, EC dưới ngưỡng; cần hiểu một thuật toán trong certificate có thể bị kiểm tra theo nhiều luật.
- Thuật toán/khóa trong thông tin revocation mà implementation sử dụng (CRL, signed OCSP response), tùy đường kiểm tra.
- SHA-1 theo điều kiện `jdkCA`, `usage TLSServer` hoặc các luật khác nếu được cấu hình rõ.

Điều kiện certpath có thể làm fail HTTPS client khi server gửi certificate chain yếu, hoặc fail TLS server khi kiểm tra chứng chỉ client trong mTLS. Nó cũng có thể ảnh hưởng một ứng dụng Java trực tiếp dùng `CertPathValidator`/`CertPathBuilder` với PKIX mà không mở TLS.

### 6.1. Trust anchor là trường hợp riêng

Theo ngữ nghĩa của JDK PKIX được tài liệu hóa, ràng buộc `disabledAlgorithms` không áp lên **trust anchor** hoặc chứng chỉ self-signed ở vai trò anchor theo cùng cách như những certificate được kiểm tra trong path. Điều đó **không có nghĩa** bất kỳ self-signed certificate nào đều mặc nhiên đáng tin.

Phải phân biệt:

1. **Trust decision:** chương trình dùng truststore/root nào, có thực sự tin CA đó hay không.
2. **Path validation:** chữ ký, thời hạn, basic constraints, key usage, name constraints và các giới hạn khác được kiểm tra như thế nào.
3. **Algorithm constraints:** thuật toán/kích thước khóa trong path có nằm trong policy cấm không.
4. **Endpoint identity:** chứng chỉ hợp lệ về PKIX nhưng CN/SAN không khớp hostname vẫn không phải danh tính server bạn định kết nối.

Nếu bạn thêm một CA tự ký yếu làm trust anchor vào truststore, đừng suy rằng policy algorithm tự động bảo vệ hoàn toàn khỏi root sai. Việc quản lý trust anchors là một lớp kiểm soát độc lập.

### 6.2. Cẩn thận với SHA-1

`SHA1 jdkCA & usage TLSServer` chỉ là một quy tắc **có điều kiện**. Một chain ký SHA-1 đi tới CA nội bộ không được đánh dấu `jdkCA` có thể không bị đúng quy tắc này chặn, trong khi một quy tắc rộng hơn `SHA1` sẽ tác động nhiều trường hợp khác. Không suy ra rằng chỉ cần chuyển truststore thì certificate SHA-1 lập tức được phép: còn có chính sách khác, `jdk.tls.disabledAlgorithms`, các checker và giới hạn của provider. Mục tiêu của doanh nghiệp nên là **thay thế chứng chỉ/thuật toán yếu**, không né điều kiện bằng cách đổi anchor.

### 6.3. Revocation không được bật chỉ vì có disabledAlgorithms

Chính sách thuật toán và kiểm tra thu hồi là hai chuyện khác nhau. Bật/chỉnh `jdk.certpath.disabledAlgorithms` không đồng nghĩa JVM sẽ tự động kiểm tra CRL hoặc OCSP cho mọi chứng chỉ. Nếu tổ chức yêu cầu revocation checking, phải cấu hình PKIX/TrustManager/OCSP phù hợp, kiểm tra lỗi mạng và quyết định fail-open/fail-closed bằng chính sách riêng.

## 7. Security property khác system property

Các property `jdk.tls.disabledAlgorithms` và `jdk.certpath.disabledAlgorithms` là **Java security properties**.

Đúng cách xem:

```java
String tlsPolicy = java.security.Security.getProperty(
        "jdk.tls.disabledAlgorithms");
String certPathPolicy = java.security.Security.getProperty(
        "jdk.certpath.disabledAlgorithms");
```

`System.getProperty("jdk.tls.disabledAlgorithms")` không phải nguồn lấy security policy. Tương tự, khởi chạy:

```bash
java -Djdk.tls.disabledAlgorithms=TLSv1 -jar app.jar
```

**không phải cách đáng tin để thay security property này**. `-Dname=value` đặt **system property**, còn JDK đọc hai chính sách nêu trên qua `java.security.Security`. Có các system properties khác được JSSE công nhận, như `jdk.tls.client.protocols`, nhưng chúng không đổi bản chất của security property.

### 7.1. `java.security` nằm ở đâu?

Với Java 9+ và Java 17, file mặc định thường là:

```text
<JAVA_HOME>/conf/security/java.security
```

Vị trí và bản sửa đổi cụ thể tùy JDK/vendor/image. Container có thể dùng một JRE/JDK riêng biệt với JDK trên máy build. `java -version` hiển thị runtime của lệnh `java` đó, nhưng chưa chứng minh service production dùng cùng binary. Cần kiểm tra `java.home` của **process thực sự** rồi xác định file/properties được nạp.

Java security properties được đọc khi subsystem bảo mật khởi tạo; một số thành phần cache giá trị. Sửa file đang chạy rồi hy vọng mọi kết nối mới tự đổi policy là không an toàn. **Khởi động lại process** theo quy trình rollout có kiểm chứng.

### 7.2. File override có một dấu `=` hay hai dấu `==`?

JDK hỗ trợ chỉ định file security properties bổ sung bằng system property **`java.security.properties`** lúc khởi động:

```bash
java -Djava.security.properties=/opt/app/security-override.properties -jar app.jar
```

Một dấu `=` sau tên property: file bổ sung được đọc **sau** cấu hình mặc định; nếu cùng key, **cả giá trị key được thay bằng giá trị trong file bổ sung**, không tự gắn thêm vài mục với dấu phẩy.

```bash
java -Djava.security.properties==/opt/app/full-java-security.properties -jar app.jar
```

Hai dấu `==` trong tham số: cấu hình bổ sung được dùng theo chế độ **thay thế toàn bộ** tập security properties mặc định trong những JDK hỗ trợ cơ chế này. Cách này có thể làm mất danh sách provider và các cấu hình thiết yếu khác, nên chỉ dùng nếu bạn quản trị rõ toàn bộ file và có test tương thích. Không dùng `==` như cách nhanh để sửa một ràng buộc TLS.

**Vấn đề quan trọng nhất khi triển khai:** dù dùng overlay một dấu `=`, nếu file chứa một dòng `jdk.tls.disabledAlgorithms=TLSv1.2` thì nó có thể thay toàn bộ chuỗi đang chứa RC4, DES, khóa yếu, named curves... bằng **chỉ** `TLSv1.2`. Việc “thêm chính sách” như thế thực ra có thể **gỡ bỏ nhiều biện pháp chặn khác**.

### 7.3. Cách tăng hạn chế mà vẫn giữ baseline

Quy trình an toàn:

1. Đọc **giá trị effective** của từng security property trên đúng JDK/container hiện tại.
2. Đọc version, vendor và update level; lưu snapshot của baseline.
3. Soạn **giá trị đầy đủ mới**: giữ tất cả điều kiện cũ, thêm điều kiện mới theo cú pháp và thứ tự phù hợp (đặc biệt khi có nhiều `keySize` của cùng thuật toán).
4. Đưa đúng **giá trị hoàn chỉnh** vào file overlay được quản lý bằng IaC/config và review diff.
5. Khởi động một JVM **mới**, in/kiểm tra effective property; thử handshake/certificate fixtures cả tích cực và tiêu cực.
6. Triển khai theo nhóm nhỏ, đo lỗi, có rollback bằng phiên bản policy đã biết tốt; rollback không đồng nghĩa chấp nhận vĩnh viễn thuật toán yếu.

Ví dụ nếu policy hiện tại đã có `DH keySize < 1024` và tổ chức muốn yêu cầu DH từ 2048 bit trở lên: cần xác định biểu thức hiện tại và thay/ghép thành luật chặn `DH keySize < 2048` mà **không đánh mất các mục khác**. Chỉ thay chữ “1024” trong một file mặc định mà chưa xác minh nguồn cấu hình khác cũng có thể gây sai lệch giữa các node.

### 7.4. Đổi policy ngay trong mã thì sao?

`Security.setProperty(key, value)` có API hợp lệ, nhưng nhiều thành phần JDK có thể đọc/cached các giá trị này từ rất sớm, kể cả khi JVM còn đang khởi tạo lớp bảo mật. Nếu gọi `setProperty` sau khi JSSE/CertPath đã khởi tạo, việc đọc lại qua `Security.getProperty` không đảm bảo mọi engine sẽ áp dụng giá trị mới. Phương án vận hành đáng tin cậy là **đặt cấu hình trước khi process khởi chạy và kiểm tra bằng JVM mới**. Không thiết kế nút admin runtime để nới lỏng policy trong process đang phục vụ người dùng.

## 8. Java 17+ chạy độc lập: quan sát effective policy

Ví dụ sau **chỉ đọc cấu hình và capability**, không kết nối mạng, không sửa policy, không xử lý secret và không cần chứng chỉ.

**Tệp `AlgorithmPolicyProbe.java`:**

```java
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public final class AlgorithmPolicyProbe {
    private AlgorithmPolicyProbe() {}

    private static void showProperty(String key) {
        String value = Security.getProperty(key);
        System.out.println(key + " = "
                + (value == null ? "(not configured)" : value));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java version: "
                + System.getProperty("java.version"));
        System.out.println("Java home: "
                + System.getProperty("java.home"));

        showProperty("jdk.tls.disabledAlgorithms");
        showProperty("jdk.certpath.disabledAlgorithms");
        showProperty("jdk.disabled.namedCurves");
        showProperty("jdk.tls.legacyAlgorithms");

        // JCA provider có engine không đồng nghĩa policy TLS/PKIX cho phép.
        try {
            Signature.getInstance("SHA1withRSA");
            System.out.println("SHA1withRSA engine available: true");
        } catch (java.security.NoSuchAlgorithmException ex) {
            System.out.println("SHA1withRSA engine available: false");
        }

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLParameters defaults = context.getDefaultSSLParameters();
        SSLParameters supported = context.getSupportedSSLParameters();

        System.out.println("Default TLS protocols: "
                + Arrays.toString(defaults.getProtocols()));
        System.out.println("Supported TLS protocols: "
                + Arrays.toString(supported.getProtocols()));

        // Đối chiếu system property để thấy hai namespace khác nhau.
        System.out.println("System TLS property: "
                + System.getProperty("jdk.tls.disabledAlgorithms"));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac AlgorithmPolicyProbe.java
java AlgorithmPolicyProbe
```

Output **phụ thuộc JDK/vendor/bản cập nhật và file cấu hình**. Ví dụ, các dòng giao thức trên một JDK thường có thể trông như sau:

```text
Default TLS protocols: [TLSv1.3, TLSv1.2]
Supported TLS protocols: [TLSv1.3, TLSv1.2, TLSv1.1, TLSv1, SSLv3]
```

Đây là output **minh họa cấu hình phổ biến**, không phải lời hứa danh sách cố định: có JDK không báo đủ các protocol cũ. Chương trình còn in giá trị property dài và `java.home` của máy chạy. Việc engine `SHA1withRSA` còn tồn tại **không** phải chứng nhận SHA-1 được phép trong TLS/CertPath. Và danh sách `Supported TLS protocols` không chứng minh rằng các giao thức đó có thể được thương lượng thành công.

Nếu `SHA1withRSA engine available: true` nhưng chứng chỉ ký SHA-1 vẫn bị từ chối ở TLS server, điều đó hoàn toàn phù hợp với cơ chế security policy.

## 9. Phân biệt security policy với các cài đặt TLS khác

Một số cấu hình dễ bị dùng nhầm:

| Cơ chế | Mục đích | Quan hệ với disabledAlgorithms |
| --- | --- | --- |
| `jdk.tls.disabledAlgorithms` | Giới hạn toàn cục của JSSE theo security property | Nền chặn bắt buộc trong implementation liên quan |
| `jdk.certpath.disabledAlgorithms` | Giới hạn thuật toán PKIX validation | Áp ở CertPath, cộng với giới hạn TLS khi dùng trong JSSE |
| `jdk.tls.legacyAlgorithms` | Đánh dấu một số thuật toán legacy; thường ít ưu tiên khi còn lựa chọn mạnh hơn | Legacy **khác** disabled; không mặc định bị cấm hoàn toàn |
| `jdk.security.legacyAlgorithms` | Báo/tổ chức các thuật toán legacy trong các công cụ như `keytool`/`jarsigner` | Warning về legacy **không** tương đương cấm TLS/CertPath |
| `jdk.jar.disabledAlgorithms` | Giới hạn trong xác minh code/JAR signing | Không phải thay thế cho TLS và PKIX |
| `jdk.tls.client.protocols` | Hỗ trợ cấu hình protocol client mặc định trong JSSE | Không bật lại protocol bị disabled |
| `jdk.tls.namedGroups` | Chọn danh sách named groups TLS khi implementation hỗ trợ | Không thể cho phép named group mà policy đã cấm |
| `SSLParameters.setProtocols(...)` | Cấu hình protocol trên một TLS endpoint cụ thể | Không vượt qua disabledAlgorithms |
| `SSLParameters.setCipherSuites(...)` | Cấu hình bộ mã được yêu cầu/bật trên endpoint | Không làm cipher yếu được phép nếu đã bị cấm |
| `SSLParameters.setAlgorithmConstraints(...)` | Bổ sung giới hạn thuật toán/key theo kết nối | Không thay thế policy toàn cục và certpath |
| Truststore / `TrustManager` | Quyết định CA và đường tin cậy | Danh sách CA tin cậy và thuật toán được phép là hai lớp khác nhau |
| `HostnameVerifier` / endpoint identification | Xác định chứng chỉ có đúng host mình kết nối | Một certificate path hợp lệ vẫn có thể không đúng hostname |

Những thứ trên không phải một danh sách “đặt giá trị một lần là đủ”. Mỗi ứng dụng có thể có code thiết lập `SSLContext`, keystore, trust manager hay `SSLParameters` riêng. Phải kiểm tra client/server nào thực sự dùng cấu hình đó.

## 10. Hai ca lỗi cụ thể: TLS cũ và chứng chỉ yếu

### Ca A — server legacy chỉ hỗ trợ TLS 1.0

**Triệu chứng:** Java 17+ client gọi dịch vụ legacy nhận `SSLHandshakeException`, trong log có phiên bản TLS không phù hợp hoặc không có protocol chung.

Cách phân tích:

1. Xác định **JVM thực** của client: `java.home`, vendor/build và giá trị effective của `jdk.tls.disabledAlgorithms`.
2. Kiểm tra server thực sự có TLS 1.2/1.3 hay không bằng công cụ kiểm thử kết nối được phê duyệt. Đừng dựa vào tài liệu cũ.
3. Dùng `javax.net.debug` để xem client đề xuất protocol/cipher nào và tại đâu handshake bị từ chối.
4. Nếu chỉ có TLS 1.0, **ưu tiên nâng cấp/đặt gateway được kiểm soát** để chuyển sang phiên bản TLS được phép. Không âm thầm xóa `TLSv1` khỏi policy toàn production JVM chỉ để “chữa lỗi”.
5. Sau migration, làm negative test để chắc kết nối TLS 1.0 bị từ chối và positive test để TLS 1.2/1.3 vẫn hoạt động.

Nếu tổ chức cho phép ngoại lệ chuyển tiếp rất ngắn, nó cần phê duyệt cụ thể, giới hạn service/network/JVM, theo dõi lưu lượng và deadline loại bỏ. Việc nới chính sách ở một process dùng chung nhiều client khiến tất cả các kết nối của process có thể bị ảnh hưởng.

### Ca B — server dùng intermediate certificate ký SHA-1 hoặc khóa yếu

**Triệu chứng:** protocol và cipher hợp lệ nhưng PKIX báo lỗi về algorithm constraints, `CertPathValidatorException` hoặc chứng chỉ không đáp ứng chính sách.

Cách phân tích:

1. Đọc toàn bộ chain thực sự server gửi: leaf, intermediate, thuật toán ký và kích thước public key; phân biệt với root nằm trong client truststore.
2. Xác định điều kiện từ `jdk.certpath.disabledAlgorithms`: cấm toàn bộ SHA-1 hay chỉ `SHA1 jdkCA & usage TLSServer`; có `RSA keySize < N` không.
3. Xác nhận trust anchor thực sự được dùng và bối cảnh `TLSServer` hay `TLSClient`. Một chữ ký SHA-1 trên certificate không có cùng hệ quả trong mọi policy.
4. Kiểm tra `jdk.tls.disabledAlgorithms` vì JSSE có thể áp thêm điều kiện vào certificate path trong handshake.
5. Ưu tiên cấp lại certificate chain/khóa đạt chuẩn và kiểm thử cả client Java cũ/mới cần hỗ trợ. Thay truststore, tắt validation hoặc custom “trust all” không giải quyết vấn đề an toàn.

### Ca C — mTLS client certificate bị chặn

Client certificate có thể được server Java kiểm tra trong bối cảnh `usage TLSClient`, trong khi server certificate lại nằm ở `usage TLSServer`. Vì vậy, test một chiều HTTPS thành công không chứng minh mTLS hai chiều thành công. Kiểm tra chain của **cả hai phía**, truststore ở hai phía và usage/constraint tương ứng.

## 11. Debug có hệ thống

### 11.1. Các lệnh chẩn đoán

Để kiểm tra cấu hình lúc JDK khởi động (dùng trên môi trường test có quyền và logging được kiểm soát):

```bash
java -Djava.security.debug=properties -cp . AlgorithmPolicyProbe
```

Debug `properties` giúp xem property sau khi xử lý file security policy. Trong các JDK hỗ trợ, bạn cũng có thể thử:

```bash
java -XshowSettings:security:properties -version
```

Để xem handshake, chain validation và các lựa chọn của JSSE trong một **ứng dụng test kết nối thực**, chạy JVM với:

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager -Djava.security.debug=certpath -jar app-test.jar
```

`app-test.jar` chỉ là tên minh họa cho ứng dụng test nội bộ; lệnh không phải output của `AlgorithmPolicyProbe`. Các trace thường chứa hostname, danh tính certificate, alias, và nhiều thông tin vận hành. Không bật debug chi tiết vô thời hạn trên production hoặc đưa log nhạy cảm vào nơi công khai.

### 11.2. Những bằng chứng cần thu thập

- Thông tin process: `java.version`, `java.vendor`, `java.home`, container image/runtime hash, startup flags.
- Giá trị security property effective sau merge file, không chỉ một file mặc định bạn tình cờ mở được.
- ClientHello/ServerHello: protocol được đề nghị và cuối cùng thương lượng, cipher suite, named group và signature scheme khi log có.
- Chuỗi chứng chỉ thực tế, thuật toán ký mỗi certificate, kích thước khóa, key usage và trust anchor.
- Exception đầy đủ với nguyên nhân gốc. `SSLHandshakeException` là lớp bao ngoài, có thể bắt nguồn từ protocol mismatch, `CertPathValidatorException`, key usage, hostname hoặc chính sách.
- Kết quả positive/negative test trên cùng build JDK và policy file đã dự kiến deploy.

Không kết luận “lỗi certpath” chỉ vì cuối stack trace có chữ “handshake”. Cần phân biệt lỗi giao thức, trust, chứng chỉ, thuật toán và ứng dụng.

## 12. Thiết kế test có thể tái lập

Một test tốt cần so sánh **trước và sau** trên cùng JDK build và cùng fixtures. Khuyến nghị xây môi trường cô lập gồm TLS server/mTLS server test với certificate và ciphers do nhóm kiểm thử quản lý. Không nhắm vào hệ thống thật chưa được cho phép.

| Fixture hoặc thay đổi | Kỳ vọng |
| --- | --- |
| TLS 1.3/TLS 1.2, certificate chain mạnh, hostname đúng | Handshake và kiểm tra identity thành công nếu truststore được cấu hình đúng |
| Chỉ TLS 1.0 trong khi policy cấm `TLSv1` | Handshake thất bại |
| Chỉ cipher RC4 khi policy cấm `RC4` | Không chọn được cipher chung, handshake thất bại |
| Leaf/intermediate có RSA key dưới ngưỡng CertPath | PKIX validation từ chối trong bối cảnh áp dụng |
| Chain SHA-1 tới anchor `jdkCA` với `usage TLSServer` | Bị từ chối khi điều kiện tương ứng thực sự khớp |
| Chain SHA-1 tới CA nội bộ không có `jdkCA` | Cần xác minh bằng policy đầy đủ; không mặc định cùng kết quả với hàng trên |
| Chứng chỉ leaf hợp thuật toán nhưng hostname khác | Phải thất bại khi bật endpoint identity verification đúng |
| Truststore thiếu CA gốc | Validation thất bại dù thuật toán/cipher mạnh |
| CA bị thu hồi hoặc chứng chỉ bị thu hồi | Kết quả phụ thuộc revocation checking đã được bật/cấu hình; riêng disabledAlgorithms không đủ |

Không dùng `SSLContext.init(null, new TrustManager[]{trustAll}, null)` làm “fix” test để vượt lỗi PKIX: một TrustManager bỏ qua trust validation khiến test mất mục đích. Với test thuật toán, luôn dùng TrustManager/PKIX thật và CA test được quản lý.

### Gợi ý điều kiện assert

- Positive test assert handshake thành công **và** `SSLSession.getProtocol()`/`getCipherSuite()` đạt chính sách; kiểm tra endpoint identity và certificate chain.
- Negative test assert **lý do** bị từ chối khi có thể (ví dụ disabled algorithm hay no cipher common), không chỉ “thất bại bất kỳ”, vì lỗi network/DNS không chứng minh policy hoạt động.
- Tạo certificate và key fixtures với key size/cấu hình phù hợp từng case. Một certificate tự ký đứng riêng ở vai trò root trust anchor sẽ **không** đại diện đầy đủ cho kiểm thử hạn chế thuật toán trên intermediate certificate.
- Kiểm thử cả HTTPS client, HTTPS server và mTLS server/client nếu cùng JVM dùng các workload khác nhau.
- Chạy test với `-Djava.security.properties` và process mới; không dựa vào `Security.setProperty` sau khi JSSE đã chạy.
- Khóa phiên bản JDK của CI test, rồi thử lại khi nâng patch JDK vì baseline nhà cung cấp có thể tăng mức hạn chế.

## 13. Những lỗi vận hành và cách tránh

| Hiện tượng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Đặt `-Djdk.tls.disabledAlgorithms=...` nhưng không đổi kết quả | Đã nhầm system property với security property | Đọc bằng `Security.getProperty`, sửa file/overlay security properties |
| File overlay chỉ ghi một mục mới | Override **toàn bộ** danh sách property gốc | Tái tạo full baseline rồi thêm constraint và diff |
| Thay `java.security.properties==...` làm service mất provider/không khởi động | Đã thay toàn bộ config mặc định | Dùng overlay với một dấu `=` khi chỉ muốn thay vài key; giữ đầy đủ giá trị từng key |
| `Security.setProperty` trả về giá trị mới nhưng handshake không đổi | JSSE/CertPath đã cache policy | Đổi cấu hình trước JVM start và tạo process mới |
| `getSupportedProtocols()` thấy TLSv1 | API mô tả implementation, không mô tả policy cho handshake | Kiểm tra default/effective policy và handshake thật |
| Chỉ cho phép TLS 1.2 nhưng handshake vẫn thất bại | Cipher, group, signature, certificate path hoặc trust không tương thích | Dùng JSSE debug và certpath trace để định vị |
| PKIX fail sau nâng cấp JDK | Baseline JDK cấm thêm thuật toán/khóa/chữ ký cũ | Cấp lại certificate/key phù hợp; test trên runtime mới |
| Nhập CA nội bộ làm lỗi SHA-1 biến mất | Điều kiện `jdkCA` khác với root tự thêm | Không coi đây là cách hợp lệ để né policy; thay certificate yếu |
| Cấm `RSA keySize < 2048` làm một số client cũ lỗi | Key-size rule áp tới đường xác thực liên quan | Kiểm kê certificate và dependency trước rollout |
| `keytool` warning nhưng TLS vẫn kết nối | Legacy/warning policy không đồng nhất disabled policy | Xác định đúng property và usage |
| Chứng chỉ hợp lệ về chữ ký nhưng kết nối host sai | Chưa xác minh endpoint identity | Bật hostname/endpoint verification tương ứng protocol |
| Không có exception certpath nhưng vẫn dùng certificate bị thu hồi | Revocation checking chưa được cấu hình | Thiết kế và kiểm thử OCSP/CRL riêng |

Đừng giải quyết `PKIX path building failed` bằng cách tạo TrustManager chấp nhận mọi chứng chỉ. Đừng thay danh sách cipher riêng lẻ để hồi sinh giao thức đã bị policy cấm. Và đừng quy lỗi cho server khi ứng dụng thật đang dùng JDK image/config khác với lệnh chẩn đoán trên máy của bạn.

## 14. Quy trình đổi chính sách ở môi trường production

**Bước 1 — Inventory:** lập bản đồ các client/server Java, cổng, TLS versions/ciphers, service certificates và intermediate CA, kích thước key, phiên bản JDK, truststore và đường cấu hình. Đừng chỉ kiểm tra phía server: service Java có thể vừa nhận inbound TLS vừa gọi outbound HTTP/JDBC/mTLS.

**Bước 2 — Policy intent:** ghi rõ mục tiêu, ví dụ từ chối TLS 1.0/1.1, không chấp nhận DH dưới 2048 bit, không cho certificate RSA dưới 2048 bit trong PKIX. Phân biệt điều kiện **toàn cục** với điều kiện chỉ cho loại usage; chọn ngữ nghĩa phù hợp và kiểm chứng bằng fixture.

**Bước 3 — Build/config snapshot:** đọc security properties effective của từng JDK distribution, sao lưu bản gốc, tạo file overlay chứa **đầy đủ giá trị** của từng key được thay đổi. Code review diff theo dấu phẩy/`&`; chú ý `include` và `denyAfter`. Tạo checksum và quản lý phiên bản policy file.

**Bước 4 — Compatibility testing:** kiểm tra các case positive/negative, mTLS, chain CA nội bộ, load balancer, trust anchor, key rotation, các JDBC driver/HTTP client có sử dụng JSSE, cũng như client cũ cần nâng cấp. Chạy trên đúng JDK/container image mới.

**Bước 5 — Staged rollout:** canary rồi tăng dần. Quan sát handshake errors theo nguyên nhân, failed certificate validation, số lượng phiên TLS/cipher negotiated, latency và tỷ lệ kết nối từ đối tác. Với một thay đổi chặn thuật toán yếu, thông báo cho đối tác trước khi bật chặn ở quy mô lớn.

**Bước 6 — Safe rollback:** giữ bản policy cũ và cơ chế trở lại nhanh trên **một process mới**, có approval và thời hạn. Không tự động giảm giới hạn toàn hệ thống sau vài lỗi handshake; cần phân biệt sự cố cấu hình thật với đối tác còn dùng mật mã yếu. Hoàn thành migration bằng cách loại bỏ ngoại lệ tạm.

**Bước 7 — Continuous compliance:** test lại khi nâng patch JDK vì nhà cung cấp có thể cập nhật giá trị mặc định hoặc hành vi validator; kiểm kê chứng chỉ sắp hết hạn và thuật toán đang sử dụng để chủ động đổi trước khi bản JDK mới chặn.

## 15. Vấn đề nâng cấp JDK và đa nhà cung cấp

Đừng copy một danh sách `jdk.tls.disabledAlgorithms` từ bài blog cũ sang JDK 17/21/24 rồi tuyên bố đó là chuẩn hiện hành. Các bản cập nhật bảo mật có thể đổi baseline, loại bỏ giao thức cũ, đổi named curves/signature schemes, thêm điều kiện với chứng chỉ hoặc key. Các vendor distribution và image tối giản có thể cấu hình khác.

Các bước kiểm chứng theo **runtime**:

```java
System.out.println(System.getProperty("java.vendor"));
System.out.println(System.getProperty("java.version"));
System.out.println(System.getProperty("java.home"));
System.out.println(java.security.Security.getProperty(
        "jdk.tls.disabledAlgorithms"));
System.out.println(java.security.Security.getProperty(
        "jdk.certpath.disabledAlgorithms"));
```

Đây là fragment đặt trong `main` hoặc JShell. Nếu ứng dụng đóng gói bằng `jlink`, container hoặc native stack khác JSSE, phải đánh giá thành phần thực sự thực hiện TLS/PKIX. Các security properties này thuộc cách triển khai JDK: **provider, TrustManager hoặc TLS stack thứ ba không bắt buộc tuân thủ chúng theo cùng cách**. Một kết nối dùng thư viện native ngoài JVM có thể cần chính sách riêng.

Thêm một điểm quan trọng: policy JDK là **hàng rào phía Java**, còn mật mã dùng ở từng kết nối cuối cùng là sự kết hợp của phiên bản giao thức, key/cert, provider capabilities, triển khai phía đối tác, cấu hình ứng dụng và trust. Đo lường sau rollout mới xác nhận được mục tiêu.

## 16. Câu hỏi tự kiểm tra

1. Vì sao `Signature.getInstance("SHA1withRSA")` tạo được engine không chứng minh server certificate SHA-1 sẽ được JSSE tin?
2. `jdk.tls.disabledAlgorithms` và `jdk.certpath.disabledAlgorithms` khác nhau ở đâu, và tại sao một TLS handshake có thể chịu cả hai?
3. Nếu `getSupportedSSLParameters().getProtocols()` có `TLSv1` thì liệu client Java 17 chắc chắn thương lượng được TLS 1.0 không?
4. `SHA1 jdkCA & usage TLSServer` khác `SHA1` ở những điều kiện nào? Root CA do công ty thêm vào có tự trở thành `jdkCA` không?
5. Với `RSA keySize < 2048`, khóa 2048 bit bị chính điều kiện đó chặn không? Nếu thay bằng `<=` thì sao?
6. Vì sao thêm `jdk.tls.disabledAlgorithms=TLSv1.2` vào một file overlay có thể **làm yếu** cấu hình bảo mật?
7. Tại sao `System.setProperty("jdk.certpath.disabledAlgorithms", "...")` không tương đương `Security.setProperty(...)` hoặc cập nhật file `java.security`?
8. Khi client nhận `SSLHandshakeException`, cần bằng chứng nào để phân biệt protocol mismatch, cipher mismatch và CertPath algorithm constraints?
9. Vì sao thêm CA nội bộ vào truststore không phải cách thay thế việc gia hạn certificate/khóa yếu?
10. Bạn cần test khác nhau thế nào cho HTTPS một chiều và mTLS khi policy có `usage TLSServer` và `usage TLSClient`?

## 17. Tài liệu chuẩn để tra cứu

- [Java SE 17 — The Security Properties File](https://docs.oracle.com/en/java/javase/17/security/security-properties-file.html): vị trí, override, `Security.getProperty`/`Security.setProperty` và debug.
- [Java SE 17 — Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): cấu hình TLS/DTLS và các cơ chế giới hạn thuật toán.
- [Oracle Java Security — Disabling and Restricting Cryptographic Algorithms](https://ops.java/security/articles/disabling-crypto-algorithms/): cú pháp đầy đủ của `disabledAlgorithms`, điều kiện `jdkCA`, `usage` và kiểm tra thực tế.
- [Java SE 17 — `java.security.Security` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html) và [`AlgorithmConstraints` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/AlgorithmConstraints.html).
- [Java SE 17 — `SSLParameters` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html) và [`CertPathValidator` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html).

**Ghi nhớ:** `jdk.tls.disabledAlgorithms` ngăn lựa chọn thuật toán/giao thức không được phép trong JSSE; `jdk.certpath.disabledAlgorithms` kiểm soát thuật toán của đường chứng chỉ PKIX. Điều chỉnh chính sách phải bắt đầu từ **giá trị effective đầy đủ của đúng JVM**, giữ các hạn chế hiện hành, thử bằng handshake và certification-path fixtures thật, rồi triển khai bằng process mới có giám sát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-003">22.3 Kiểm tra thay đổi mặc định theo bản cập nhật JDK thay vì chép cấu hình cũ</a>

<details>
<summary>Xem chi tiết</summary>

# 22.3 Kiểm tra thay đổi mặc định theo bản cập nhật JDK thay vì chép cấu hình cũ

> **Ngữ cảnh:** Java Advance — security-cryptography, chủ đề **22. Vận hành và kiểm thử**. Khi nâng JDK, cần xem *mặc định bảo mật thực sự có hiệu lực* và thử nghiệm các luồng mật mã/TLS của ứng dụng trước khi quyết định có cần cấu hình bổ sung. Không tự động sao chép nguyên file `java.security` của JDK cũ sang JDK mới.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích tại sao *cập nhật bản vá* JDK cũng có thể thay đổi TLS, certificate validation, provider, keystore hay kiểm tra JAR ký số ngay cả khi mã ứng dụng không đổi.
2. Phân biệt cấu hình trong `conf/security/java.security`, system property `-D...`, thiết lập của ứng dụng và trạng thái hiệu lực do JCA/JSSE cung cấp.
3. Thu thập baseline có thể đối chiếu theo **vendor + full JDK build + runtime + môi trường**, thay vì đoán giá trị mặc định từ tài liệu của một phiên bản khác.
4. Viết và chạy công cụ Java 17+ đọc các security properties, provider order và TLS defaults mà **không cần truy cập mạng hay tiết lộ secret**.
5. Thiết kế bộ kiểm thử tích cực/tiêu cực cho handshake TLS, chứng chỉ, keystore, chữ ký JAR và các cơ chế provider.
6. Phân loại thay đổi là chủ ý cần thích nghi, lỗi tương thích phải sửa, hay một rủi ro bảo mật phải chặn; chỉ tạo ngoại lệ cấu hình khi có đánh giá, phạm vi và thời hạn rõ ràng.

## 2. Vì sao “giữ nguyên cấu hình cũ” là phản xạ sai?

Hệ thống chạy Java chịu tác động của **ít nhất bốn lớp**:

| Lớp | Ví dụ | Ai quyết định và rủi ro khi nâng cấp |
| --- | --- | --- |
| Bản phân phối JDK đang chạy | Vendor, bản build, runtime image, security providers, bundled `cacerts` | Bản vá có thể loại bỏ thuật toán hoặc CA yếu, cập nhật provider hay thay default. |
| Security properties | `jdk.tls.disabledAlgorithms`, `jdk.certpath.disabledAlgorithms`, `jdk.jar.disabledAlgorithms`, `keystore.type` | Cùng tên property có thể đổi giá trị và cách áp dụng qua các JDK. |
| System properties, application config và runtime flags | `javax.net.ssl.trustStore`, `jdk.tls.client.protocols`, `-Djava.security.properties=...`, TLS parameters do app đặt | Có thể ghi đè hoặc làm cho default JDK không được sử dụng như dự tính. |
| Kết nối/credential/tài nguyên thực tế | Chứng chỉ của peer, mTLS, JAR đã ký, PKCS#12, HSM, proxy TLS | Một snapshot trông tốt vẫn có thể thất bại khi handshake hoặc xác minh chứng chỉ thực. |

*Ví dụ tình huống*: phiên bản JDK mới siết `jdk.certpath.disabledAlgorithms`, vì vậy một certificate chain trước đây được chấp nhận nay có thể bị từ chối. Việc “khắc phục” bằng cách chép danh sách cấm cũ sẽ có nguy cơ **mở lại thuật toán yếu**. Hướng đúng là kiểm tra chứng chỉ/key size/chữ ký của đối tác, điều chỉnh chuỗi tin cậy hoặc thay certificate.

Một tình huống khác: JDK từ 9 trở đi thường dùng `PKCS12` làm kiểu keystore mặc định. Nếu ứng dụng cũ gọi `KeyStore.getInstance(KeyStore.getDefaultType())` nhưng ngầm mong chờ hành vi của JKS, việc chép lại `keystore.type=jks` trên máy mới có thể khiến tất cả ứng dụng dùng runtime đó lệ thuộc cấu hình cũ. Nên khai báo rõ định dạng file tại nơi cần thiết, có kế hoạch chuyển đổi keystore và thử load/read/write bằng đúng provider đang triển khai.

**Hai nguyên tắc:**

- **Default của JDK là một đầu vào cần xác minh theo build**, không phải hợp đồng bất biến giữa các bản cập nhật.
- **Behavior mới là tiêu chuẩn nghiệm thu:** kiểm tra handshake, chữ ký, trust path và keystore với dữ liệu thật hoặc fixture đại diện; diff file cấu hình chỉ là một phần bằng chứng.

## 3. Những nhóm thay đổi cần kiểm tra khi nâng JDK

| Nhóm | Những gì có thể khác | Bằng chứng/ca kiểm thử nên lấy |
| --- | --- | --- |
| TLS/JSSE | Giao thức mặc định được bật; tập cipher suites, thứ tự ưu tiên; named groups; certificate/key constraints | `SSLContext` default/supported protocols, default cipher suites, handshake thực với peer cũ/mới; test SSL/TLS không đạt chính sách bị từ chối. |
| CertPath/PKIX | Thuật toán chữ ký, độ dài khóa, path building, trust anchors, chính sách thu hồi | `jdk.certpath.disabledAlgorithms`, kiểm tra chuỗi CA thật, trường hợp CA thiếu/sai, hostname sai, certificate hết hạn. |
| Signed JAR | Thuật toán và key size được chấp nhận cho chữ ký JAR | `jdk.jar.disabledAlgorithms`; `jarsigner -verify -strict -verbose -certs` trên artifact đại diện. |
| KeyStore/TrustStore | Kiểu mặc định, thuật toán bảo vệ PKCS#12, file `cacerts`, cấu hình truststore riêng | `KeyStore.getDefaultType()`, keytool liệt kê alias/cert fingerprint, load PKCS#12/JKS thực, thử TLS trust. |
| JCA/JCE providers | Danh sách/thứ tự provider, thuật toán sẵn có, lựa chọn provider, chính sách crypto | `Security.getProviders()`, `getInstance(...)` của các algorithm ứng dụng dùng, test ký/xác minh/mã hóa/giải mã. |
| SecureRandom và TLS session | Provider/algorithm mặc định, entropy source, protocol compatibility/session behavior | In `SecureRandom` algorithm nếu cần, test tạo key/random dưới tải, hiệu năng/timeout TLS; không log secret/seed. |
| Cấu hình triển khai | `-D` flags, `JAVA_HOME`, image container, truststore override, FIPS/PKCS#11 | Startup command, hash image, thực tế `java.home`, bản build từ `java -version`, thông số provider/HSM. |

Danh sách này là **điểm kiểm tra**, không khẳng định bản JDK đang dùng chắc chắn thay đổi tất cả các mục. Không gán “JDK 17/21/25 mặc định X” cho mọi vendor, mọi bản vá và mọi cấu hình; phải đo trên runtime đích.

### Supported, default-enabled và negotiated là ba điều khác nhau

Với JSSE, `getSupportedSSLParameters()` có thể trả về giao thức **provider biết cách xử lý**, kể cả giao thức bị hạn chế bởi security property. `getDefaultSSLParameters()` thể hiện giá trị khởi tạo mặc định của context; ứng dụng còn có thể thay đổi trên `SSLSocket`/`SSLEngine`. Cấu hình thuật toán bị vô hiệu qua `jdk.tls.disabledAlgorithms` có thể tiếp tục bị chặn trong handshake kể cả khi ứng dụng gọi `setEnabledProtocols` hoặc `setEnabledCipherSuites`.

Điều cuối cùng cần đo là **giao thức/cipher suite/peer certificate thực sự được chấp nhận trong một handshake đúng môi trường**. “Supported” không đồng nghĩa “được phép”; “enabled mặc định” cũng không bảo đảm kết nối thành công.

## 4. Tìm đúng JDK thực sự chạy ứng dụng

Trước khi so sánh:

1. Xác định **runtime đang chạy**, không chỉ SDK mà IDE hoặc build server đã dùng để biên dịch. Chụp `java -version` của dịch vụ/container/pod và `System.getProperty("java.home")` trong chính tiến trình ứng dụng.
2. Ghi full build string (ví dụ `17.0.x+build`), vendor, OS/architecture, image digest và thời điểm snapshot. Cùng major version không có nghĩa cùng CPU/PSU và bộ CA.
3. Ghi phương thức cấu hình: environment variables, tham số `-D`, file security override, `java.security` được dùng, provider cài thêm, TLS proxy, truststore và keystore riêng.
4. Kiểm tra `<java-home>/conf/security/java.security` của bản JDK mới. Với JDK 9+, vị trí điển hình nằm dưới `conf/security`; nếu dùng custom runtime image, kiểm tra đường dẫn trong chính image ấy.
5. Phân biệt JDK bản mới **sạch mặc định** với bản chạy cùng override của ứng dụng: cần chụp cả hai để biết thay đổi đến từ nhà cung cấp hay cấu hình của mình.

Có thể ghi log phiên bản ở startup (đã lược thông tin máy nhạy cảm). Nếu công ty thay base image, nhớ lấy lại baseline ở container sản xuất hoặc staging giống sản xuất; chạy thử trên JDK của laptop **không** xác nhận runtime của server.

## 5. Bản chất của security property và system property

`java.security.Security.getProperty("jdk.tls.disabledAlgorithms")` đọc *security property* do Java Security Framework quản lý. `System.getProperty("jdk.tls.disabledAlgorithms")` chỉ đọc *JVM system property* cùng tên nếu có; đó **không phải** một cách thay thế tương đương. Một giá trị `-Djdk.tls.disabledAlgorithms=...` không được tự suy ra là đã cập nhật security property mà JSSE sử dụng.

Cấu hình hiệu lực có thể đến từ:

- File security master `<java-home>/conf/security/java.security`, có thể chứa thêm `include` hoặc cơ chế cấu hình được bản JDK hỗ trợ.
- File bổ sung qua `-Djava.security.properties=<URL>`: file này được nạp bổ sung, và các key trùng có thể override giá trị master.
- `-Djava.security.properties==<URL>`: dùng **hai dấu bằng sau tên property**, thay thế toàn bộ file master bằng file chỉ định. Rất dễ làm mất cấu hình provider, constraints hoặc thiết lập quan trọng; không áp dụng làm “mẹo sửa lỗi” trong sản xuất.
- API `Security.setProperty(...)`: chỉ nên dùng có chủ đích, thường phải thực hiện trước khi thành phần liên quan được khởi tạo; một số giá trị có thể đã cache và đổi muộn không tác dụng.
- Các system property riêng của JSSE như `javax.net.ssl.trustStore`, `javax.net.ssl.keyStore` và `jdk.tls.client.protocols`; ứng dụng còn có thể tự tạo `SSLContext` và trust/key managers.

**Cách làm đúng:** dùng `Security.getProperty`, `SSLContext` và behavior test để đọc cấu hình thực có hiệu lực. Đừng dựa riêng vào file text hoặc một biến môi trường; đồng thời không khởi động với một file `java.security` sao chép nguyên từ bản cũ để “giữ tương thích”.

## 6. Java 17+: chụp Security Snapshot có thể so sánh

Chương trình dưới đây **độc lập, chạy được trên JDK 17+**, không mở kết nối mạng và không xuất password, private key hoặc nội dung truststore. Nó ghi bản JDK, các security property quan trọng, thứ tự provider, default type, default/supported protocols và cipher suites. Dùng chung **một file class** khi chạy trên JDK cũ/mới để phép so sánh có ý nghĩa.

Tạo file `JdkSecuritySnapshot.java`:

```java
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class JdkSecuritySnapshot {
    private static void emit(String key, String value) {
        String safe = value == null ? "<unset>"
                : value.replace("\r", " ").replace("\n", " ");
        System.out.println(key + "=" + safe);
    }

    private static void emitArray(String key, String[] values) {
        // Giữ thứ tự: preference order cũng có thể là tín hiệu thay đổi.
        emit(key, String.join(",", values));
    }

    public static void main(String[] args) throws Exception {
        emit("java.runtime.version",
                System.getProperty("java.runtime.version"));
        emit("java.vendor", System.getProperty("java.vendor"));
        emit("java.vm.name", System.getProperty("java.vm.name"));
        emit("java.home", System.getProperty("java.home"));

        for (String name : List.of(
                "jdk.tls.disabledAlgorithms",
                "jdk.tls.legacyAlgorithms",
                "jdk.certpath.disabledAlgorithms",
                "jdk.jar.disabledAlgorithms",
                "keystore.type",
                "crypto.policy")) {
            emit("security." + name, Security.getProperty(name));
        }

        emit("keystore.defaultType", KeyStore.getDefaultType());
        emit("trustManager.defaultAlgorithm",
                TrustManagerFactory.getDefaultAlgorithm());
        emit("keyManager.defaultAlgorithm",
                KeyManagerFactory.getDefaultAlgorithm());

        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            emit("provider." + (i + 1),
                    providers[i].getName() + "/" + providers[i].getVersionStr());
        }

        SSLContext context = SSLContext.getDefault();
        emit("tls.context.provider", context.getProvider().getName());
        emitArray("tls.default.protocols",
                context.getDefaultSSLParameters().getProtocols());
        emitArray("tls.supported.protocols",
                context.getSupportedSSLParameters().getProtocols());
        emitArray("tls.default.cipherSuites",
                context.getDefaultSSLParameters().getCipherSuites());

        // Chỉ ghi true/false để tránh lộ đường dẫn cấu hình khi chia sẻ log.
        for (String name : List.of(
                "javax.net.ssl.trustStore",
                "javax.net.ssl.trustStoreType",
                "javax.net.ssl.keyStore",
                "jdk.tls.client.protocols",
                "java.security.properties")) {
            emit("system." + name + ".configured",
                    Boolean.toString(System.getProperty(name) != null));
        }
    }
}
```

Biên dịch với toolchain hỗ trợ Java 17+:

```bash
javac --release 17 JdkSecuritySnapshot.java
java JdkSecuritySnapshot
```

**Output phụ thuộc chính JDK và môi trường**. Mỗi dòng có dạng `key=value`, ví dụ sẽ có các nhóm sau (đây là **khung minh họa, không phải stdout cố định**):

```text
java.runtime.version=<full runtime build>
java.vendor=<JDK vendor>
security.jdk.tls.disabledAlgorithms=<effective constraint list>
security.jdk.certpath.disabledAlgorithms=<effective constraint list>
security.jdk.jar.disabledAlgorithms=<effective constraint list>
keystore.defaultType=<actual default keystore type>
provider.1=<first provider/version>
tls.context.provider=<actual provider>
tls.default.protocols=<default enabled protocols>
tls.supported.protocols=<supported protocols>
tls.default.cipherSuites=<default cipher suites>
system.javax.net.ssl.trustStore.configured=<true or false>
```

Không copy đoạn output minh họa để làm baseline. **Baseline phải đến từ chương trình chạy thực tế.** Thứ tự provider và cipher suites được giữ nguyên để không bỏ sót thay đổi ưu tiên; giá trị `<unset>` nghĩa là property không được thiết lập trong runtime đang kiểm tra, không mặc nhiên nghĩa mọi ràng buộc liên quan đều đã tắt.

### So sánh hai JDK

Ví dụ với Linux/macOS; thay đường dẫn bằng đường dẫn của hai bản runtime đã cài, chạy từ thư mục chứa file `.class`:

```bash
"/opt/jdk-old/bin/java" -cp . JdkSecuritySnapshot > security-old.txt
"/opt/jdk-new/bin/java" -cp . JdkSecuritySnapshot > security-new.txt
diff -u security-old.txt security-new.txt
```

Tương đương Windows PowerShell (đường dẫn chỉ để minh họa):

```powershell
& "C:\jdks\jdk-old\bin\java.exe" -cp . JdkSecuritySnapshot > security-old.txt
& "C:\jdks\jdk-new\bin\java.exe" -cp . JdkSecuritySnapshot > security-new.txt
Compare-Object (Get-Content security-old.txt) (Get-Content security-new.txt)
```

`Compare-Object` dạng trên phù hợp để xem tập dòng thêm/bớt; nếu cần phát hiện **thay đổi thứ tự dòng**, dùng công cụ diff theo dòng hoặc so sánh file tuần tự có kiểm soát. Chênh lệch `java.home` và `java.runtime.version` là mong đợi; đừng biến toàn bộ file snapshot thành “golden string” bắt buộc bằng nhau khi nâng JDK. Cần phân loại thay đổi theo nhóm bảo mật.

**Hạn chế snapshot:** chương trình chỉ hỏi context JSSE mặc định; ứng dụng sử dụng `SSLContext`, provider hoặc `SSLEngine` riêng có thể hành xử khác. Snapshot cũng không thể nói chứng chỉ của endpoint thực tế có hợp lệ, revocation checking có đúng policy, hoặc JAR đã ký có verify được hay không. Các mục đó cần test ở những phần sau.

## 7. Thêm policy gate: chặn cấu hình TLS mặc định yếu

Một baseline diff cho biết “cái gì khác”, còn test theo **security contract** cho biết “thay đổi đó có đạt yêu cầu không”. Ví dụ chính sách của một hệ thống chỉ cho phép TLS 1.2 trở lên; ở đây kiểm tra ít nhất các protocol cũ **không nằm trong danh sách mặc định bật**.

Tạo file `SecurityProtocolGate.java`:

```java
import java.util.Set;
import javax.net.ssl.SSLContext;

public final class SecurityProtocolGate {
    public static void main(String[] args) throws Exception {
        Set<String> forbidden = Set.of("SSLv3", "TLSv1", "TLSv1.1");
        String[] defaults = SSLContext.getDefault()
                .getDefaultSSLParameters().getProtocols();

        for (String protocol : defaults) {
            if (forbidden.contains(protocol)) {
                throw new IllegalStateException(
                        "Forbidden default TLS protocol: " + protocol);
            }
        }
        System.out.println(
                "PASS: no forbidden TLS protocol in JSSE defaults");
    }
}
```

Chạy:

```bash
javac --release 17 SecurityProtocolGate.java
java SecurityProtocolGate
```

**Stdout khi môi trường đạt điều kiện:**

```text
PASS: no forbidden TLS protocol in JSSE defaults
```

Nếu phát hiện protocol bị cấm, chương trình ném `IllegalStateException` và thoát lỗi — đây là tín hiệu đúng để CI chặn bản nâng cấp. Nếu đạt PASS, nó chỉ chứng minh điều kiện **default-enabled** vừa kiểm tra; chưa chứng minh không thể ép bật protocol bằng config khác, không chứng minh thuật toán bị cấm ở mọi provider và chưa thử handshake thực. Vì vậy policy gate này là một lớp kiểm thử, **không thay test bảo mật đầu cuối**.

Bộ test thực tế nên thêm kiểm tra protocol/cipher **đã negotiated**, hostname verification, chain trust, certificate validity, client authentication và failure đúng kỳ vọng. Không hardcode “JDK mới phải có chính xác bao nhiêu cipher suites” nếu yêu cầu tổ chức không đòi điều đó.

## 8. Quy trình kiểm thử nâng JDK theo từng bước

### Bước 1 — Chốt inventory và baseline trước thay đổi

Lưu lại:

- Phiên bản runtime thực, vendor, JDK image digest, provider/PKCS#11/HSM, tham số khởi động.
- Snapshot chương trình ở mục 6 trong staging có cấu hình gần production.
- Các endpoint/peer phụ thuộc: HTTPS, mTLS, LDAP(S), SMTP STARTTLS, message broker, database TLS, Kerberos khi có liên quan.
- Danh sách keystore/truststore (format, nguồn cấp, vòng đời, certificate fingerprint/expiry; **không** sao chép secret vào báo cáo), signed artifacts và certificate chain đối tác.
- Các ngoại lệ bảo mật đang có: `-Djava.security.properties`, code `Security.setProperty`, custom trust managers, cipher/protocol allowlist.

Định nghĩa một **security contract** theo chức năng: tối thiểu giao thức TLS, thuật toán/cỡ khóa, CA được tin, cách verify hostname, signed-JAR expectations, provider bắt buộc (nếu HSM/FIPS), mục tiêu sẵn sàng và performance. Đây là tiêu chí đánh giá chính; baseline cũ chỉ giúp điều tra.

### Bước 2 — Đọc release notes và đo default mới trong môi trường sạch

- Đọc security release notes/critical patch update notes của đúng **vendor, full version và build**: thay đổi TLS protocol/algorithm constraints, trust anchors, provider, signed-JAR constraints, PKCS#12, sửa lỗi security.
- Cài JDK mới trong môi trường thử nghiệm riêng; chạy snapshot **chưa áp cấu hình override của ứng dụng** để xem chính xác bản phân phối mang đến gì.
- Chạy lại trong môi trường ứng dụng có overrides hiện tại để biết override có che mất default mới, hoặc thay đổi ngữ nghĩa sau cập nhật.
- So sánh `Security.getProperty(...)` hiệu lực với file `conf/security/java.security`; chú ý `include`, các `-D` và provider bổ sung.
- Phân loại từng chênh lệch: *siết bảo mật*, *thay đổi tương thích*, *thay đổi provider/trust source* hoặc *khác biệt môi trường chưa giải thích*.

Đối với bản JDK hỗ trợ, lệnh `java -XshowSettings:security:properties -version` giúp xem security properties; `java -XshowSettings:security:providers -version` và `java -XshowSettings:security:tls -version` giúp khảo sát thêm. Kiểm tra khả năng hỗ trợ trên bản JDK cụ thể; công cụ `JdkSecuritySnapshot` vẫn là đường đo nhất quán cho Java 17+.

### Bước 3 — Chạy ma trận test dương và âm

| Luồng | Test dương phải thành công | Test âm phải thất bại đúng kỳ vọng |
| --- | --- | --- |
| HTTPS client | Kết nối endpoint hợp lệ với CA/hostname phù hợp, negotiate TLS/cipher đạt policy | Server certificate hết hạn, chain không tin cậy, hostname sai, hoặc protocol/cipher bị cấm. |
| TLS server / mTLS | Client hợp lệ có certificate và key usage phù hợp, request được xác thực | Client thiếu chứng chỉ nếu bắt buộc mTLS, certificate sai trust/expired/revoked theo policy. |
| CertPath | Xây dựng và xác minh chain được tổ chức chấp nhận | Chain dùng thuật toán/cỡ khóa bị cấm hoặc trust anchor không được tin. |
| Keystore/truststore | Load được PKCS#12/JKS đúng format, alias, password, key/cert; có thể sign/decrypt khi thiết kế yêu cầu | File hỏng, sai password, alias không tồn tại, provider không tương thích. |
| Signed JAR | `jarsigner -verify -strict` artifact hợp lệ trong policy hiện tại | Artifact dùng thuật toán chữ ký bị cấm hoặc đã bị sửa đổi. |
| JCA/JCE | `Signature`/`Cipher`/`Mac`/`KeyAgreement` thực hiện đúng fixture/known-answer test, provider đúng yêu cầu | Thuật toán không được chấp nhận, khóa sai/cỡ khóa yếu, dữ liệu bị sửa; không fallback ngầm. |

Test phải chạy **bằng runtime mới** trên image hoặc môi trường tương đương; test bằng `javac` của JDK mới nhưng `java` của JDK cũ không chứng minh gì về provider/runtime mới. Đưa cả ca kiểm thử về business flow (đăng nhập mTLS, gọi API, verify artifact trong deploy pipeline) để bắt lỗi tích hợp.

### Bước 4 — Quan sát TLS negotiation, certpath và chữ ký

Các lệnh dưới đây dùng cho môi trường được phép kiểm thử, không ghi secrets trong terminal command hoặc log:

```bash
# Kiểm tra JDK đang dùng
java -version

# Chạy chương trình trong môi trường có cấu hình ứng dụng
java JdkSecuritySnapshot

# Xác minh chữ ký artifact mẫu; -strict coi cảnh báo nghiêm trọng là lỗi
jarsigner -verify -strict -verbose -certs signed-artifact.jar

# Liệt kê chứng chỉ trong truststore mặc định được keytool sử dụng
keytool -list -cacerts
```

Với lỗi khó tái hiện, bật debug **trong môi trường thử nghiệm**, giới hạn thời gian và chỉ chọn mục cần thiết:

```bash
# Cấu hình security properties có hiệu lực (rất nhiều thông tin)
java -Djava.security.debug=properties JdkSecuritySnapshot

# Chi tiết handshake JSSE của chương trình có kết nối thực
java -Djavax.net.debug=ssl:handshake MyApplication

# Chẩn đoán xây dựng/xác minh certificate path
java -Djava.security.debug=certpath MyApplication
```

`MyApplication` trong ví dụ là tên entry point ứng dụng thực tế; các lệnh debug không thể chạy độc lập khi chưa có chương trình/kết nối tương ứng. Log TLS có thể chứa hostname, certificate metadata và thông tin kết nối; không đẩy nguyên log lên kho công khai, không bật tràn lan ở production. Các property debug khác nhau cho từng subsystem; kiểm tra tài liệu chính xác trước khi bật.

**Đừng chỉ nhìn câu “handshake successful”**. Ghi lại TLS version và cipher suite thực negotiated, certificate chain, hostname policy, provider và loại truststore. Cùng một giao dịch thành công có thể thỏa hoặc vi phạm policy tùy thuật toán/identity đã negotiated.

### Bước 5 — Triển khai canary, quyết định và rollback

Trên canary được giám sát, đo tỷ lệ handshake fail, lỗi `SSLHandshakeException`, `CertPathValidatorException`, `NoSuchAlgorithmException`, `UnrecoverableKeyException`, lỗi PKCS#11/provider, độ trễ TLS và các use case bảo mật. Chỉ chấp nhận rollout khi:

1. Các bài test dương/âm đều đúng.
2. Security contract đạt trên JDK mới, không có downgrade được mở ngoài ý muốn.
3. Khác biệt baseline đã được giải thích và ghi nhận (chấp nhận, thay đổi ứng dụng/đối tác, hoặc chặn nâng cấp).
4. Có kế hoạch rollback **runtime/image** và cấu hình riêng, không biến việc bỏ constraints thành “rollback” mặc định.
5. Ngoại lệ tạm thời (nếu bất khả kháng) có người sở hữu, phạm vi tác động, mức rủi ro, thời hạn loại bỏ và test giám sát.

## 9. Chẩn đoán các thay đổi thường bị hiểu sai

### 9.1. “Sau nâng JDK bị `SSLHandshakeException` nên bật lại TLSv1”

Đây là **triệu chứng**, không phải bằng chứng JDK “lỗi”. Cần xác định client/server, TLS protocols được chào, cipher suites/elliptic curves/key exchange, certificate chain, hostname và policy của mỗi phía. Nếu peer chỉ hỗ trợ protocol yếu, ưu tiên **nâng peer hoặc qua gateway được quản trị có phân tách rủi ro**, thay vì nới toàn bộ `jdk.tls.disabledAlgorithms` cho mọi kết nối. Một phương án trung gian phải có security review và ngày kết thúc.

### 9.2. “Supported còn thấy TLSv1 nên chắc JDK vẫn cho dùng”

Sai vì *supported* ≠ *enabled mặc định* ≠ *được phép negotiated*. Ràng buộc `jdk.tls.disabledAlgorithms` có thể ngăn handshake bất chấp danh sách implementation hỗ trợ hoặc code gọi `setEnabledProtocols`. Dùng cả snapshot và negative handshake test.

### 9.3. “Cùng truststore path nhưng thiếu CA sau nâng”

Có thể do bản JDK mới mang tập CA khác, truststore ứng dụng ghi đè, OS/vendor tích hợp CA khác, hoặc chain-building/algorithm constraints siết lại. Kiểm tra chính xác file được load, certificate fingerprint, chain, trust anchor, kết quả `CertPathValidator`. Không bật trust-all manager, không vô hiệu hóa hostname verification.

### 9.4. “JAR vẫn có chữ ký nhưng bị coi là unsigned”

`jarsigner` có thể coi artifact ký bằng thuật toán bị liệt kê trong `jdk.jar.disabledAlgorithms` như không được ký hợp lệ. Giải pháp là tái ký artifact bằng thuật toán và key size phù hợp, điều chỉnh pipeline phát hành và timestamping nếu có; không sao chép lại danh sách thuật toán cấm cũ để làm chữ ký yếu “hợp lệ” trở lại.

### 9.5. “KeyStore.getDefaultType vẫn trả PKCS12, vậy file JKS tự đổi?”

Không. `KeyStore.getDefaultType()` chỉ cho biết kiểu mặc định **khi code hỏi**, không nói file của bạn được chuyển định dạng. Cần xác minh định dạng thực, provider đọc file, thuật toán bảo vệ, alias/password và quy trình migrate. Với file tồn tại lâu dài, truyền kiểu keystore rõ ràng theo hợp đồng cấu hình thay vì dựa vào một default đang có thể thay đổi.

### 9.6. “Cấu hình `-D` đã đặt rồi nhưng JDK bỏ qua”

Có thể `-Dfoo` là system property trong khi thành phần cần **security property** `Security.getProperty("foo")`; cũng có thể property được đọc/cached ở thời điểm khởi tạo, hoặc ứng dụng đã tạo context/provider riêng. Xác minh đường đi của từng property từ tài liệu API và từ log/snapshot. Không thử nhiều giá trị ngẫu nhiên ở production.

### 9.7. “Cùng JDK major mà máy test khác production”

Chênh lệch vendor/build, file `java.security`, order của JCA provider, OS trust store, private CA, FIPS mode, HSM, policy file và cấu hình app đều có thể tạo khác biệt. Kiểm tra image digest và runtime **tại tiến trình**; test phải tái lập cùng môi trường.

## 10. Khi nào mới được tạo cấu hình override?

Một quy tắc tốt là **ưu tiên để bản JDK mới tự mang default bảo mật đã được cập nhật**. Khi thực sự cần override, chỉ áp dụng các key có mục đích rõ ràng và đã qua review.

Ví dụ mô hình thêm một file override nhỏ theo cơ chế được JDK hỗ trợ:

```bash
# Ví dụ cú pháp; file thực phải được review, chỉ chứa key cần thiết.
java -Djava.security.properties=file:/opt/app/conf/security-overrides.properties JdkSecuritySnapshot
```

Điều này **không** tương đương với:

```text
-Djava.security.properties==file:/opt/app/conf/old-java.security
```

Dạng thứ hai **thay master**, có thể mất security providers, algorithm restrictions hoặc cấu hình mới; đưa toàn bộ `old-java.security` sang JDK mới sẽ che các hardening từ bản cập nhật. Đừng áp dụng một trong hai dạng khi chưa biết cụ thể property nào cần đổi, tác động phạm vi nào và vì sao an toàn. Đặc biệt không dùng `jdk.tls.disabledAlgorithms` hay `jdk.certpath.disabledAlgorithms` cũ để “gỡ chặn” một protocol/chữ ký yếu.

Nếu cần cấu hình theo từng build, lưu **diff/patch nhỏ và test trên từng JDK mục tiêu** trong source control, ghi owner/rationale/expiry; sau khi nâng version, đánh giá lại liệu override còn cần không. Tránh dùng `Security.setProperty` sau khi thành phần mã hóa/TLS đã khởi tạo với hy vọng nó tự thay đổi toàn bộ hệ thống đang chạy.

## 11. Ma trận quyết định sau khi diff

| Thay đổi quan sát được | Bước xác minh | Hành động hợp lý |
| --- | --- | --- |
| Thêm TLS protocol hoặc chữ ký yếu vào danh sách disabled | Negative tests kiểm tra thực sự bị chặn; positive tests với peer hiện đại | Giữ hardening; sửa peer/chứng chỉ/artifact cũ nếu bị ảnh hưởng. |
| JCA provider xuất hiện, biến mất hoặc đổi thứ tự | Test algorithm/provider được app yêu cầu, compare provider thực `getInstance(...).getProvider()` | Nếu phụ thuộc provider đặc thù, chỉ định provider rõ ràng và test tích hợp. |
| Thay `keystore.type` hoặc kiểu xử lý PKCS#12 | Load và thao tác với keystore đúng format, fixture có password/key thực tế trong vault | Chỉ định format tại nơi sử dụng, migrate và test, không ép JDK dùng default cũ toàn cục. |
| Thêm/xóa CA trong `cacerts` hoặc truststore hiệu lực | Compare CA fingerprints và thử chain hợp lệ/không hợp lệ | Cập nhật PKI/CA của đối tác hoặc truststore có kiểm soát; không tắt xác minh. |
| Signed JAR bị đánh dấu insecure/unsigned | Chạy `jarsigner -verify -strict`, kiểm tra thuật toán và certificate | Ký lại với thuật toán hợp lệ, cập nhật pipeline. |
| Snapshot giống nhưng handshake lỗi | Kiểm tra ứng dụng tạo context riêng, DNS/hostname, SNI/ALPN, peer config và network | Sửa tầng đúng nguyên nhân; không suy diễn chỉ từ danh sách property. |
| Override cũ khiến JDK mới mất hardening | So bản sạch và bản áp override, test negative | Thu nhỏ hoặc bỏ override; bắt buộc review security. |

## 12. Đưa kiểm tra vào CI/CD mà không làm pipeline giòn

Một pipeline nâng JDK đủ ý nghĩa có thể gồm:

1. **Build trên toolchain mục tiêu** và xác nhận deploy image chạy đúng full version (có thể dùng `javac --release 17` để giữ mức tương thích bytecode, nhưng runtime test phải chạy trên JDK mới).
2. **Snapshot job**: chạy `JdkSecuritySnapshot` bằng runtime cũ/mới; lưu file text có hash, thời gian, vendor/build; tự động tạo diff và phân nhóm thay đổi.
3. **Policy tests**: chạy `SecurityProtocolGate`, kiểm tra những thuật toán tối thiểu bắt buộc có, và các thuật toán/protocol yếu phải bị chặn theo policy.
4. **Integration tests**: local fixture TLS/mTLS/PKIX/keystore/JAR; kết nối với các peer thật trong staging khi được phép, gồm negative tests.
5. **Release approval**: người chịu trách nhiệm xem diff + test + release notes + ngoại lệ, xác nhận tương thích và không giảm bảo mật.
6. **Canary và rollback**: theo dõi chỉ báo lỗi/từ chối kết nối và lộ trình trở về image trước đó nếu regression không thể sửa kịp.

Không nên fail CI chỉ vì `java.runtime.version`, `java.home` hay provider version khác baseline cũ: đó có thể là thay đổi hiển nhiên. Cần fail khi một **security invariant** vi phạm, critical flow thất bại, hoặc một thay đổi có rủi ro chưa được giải thích/duyệt.

Tuyệt đối không đưa truststore password, key material, API token, access key, full TLS debug logs hoặc thông tin nội bộ không cần thiết vào artifact CI công khai. Nội dung snapshot Java không chủ đích in secret, nhưng `java.home` vẫn có thể lộ cấu trúc đường dẫn; xử lý theo chính sách chia sẻ nội bộ.

## 13. Tình huống minh họa: cùng app, khác kết quả sau bản vá JDK

Hệ thống gồm Java HTTPS client, một service đối tác và signed plugin JAR. Sau khi đổi runtime:

1. **Thất bại gọi HTTPS:** `SSLHandshakeException` xảy ra ở endpoint cũ. Snapshot cho thấy constraints TLS khác; debug và handshake test phát hiện peer chỉ dùng protocol/cipher không đạt policy. Quyết định nâng endpoint hoặc gateway có kiểm soát, **không giảm constraints mặc định trên mọi connection**.
2. **Thất bại xác minh plugin JAR:** `jarsigner -verify -strict` cho biết thuật toán ký không đạt policy. Quyết định cập nhật signing key/algorithm và ký lại artifact; không làm yếu `jdk.jar.disabledAlgorithms`.
3. **Một endpoint khác vẫn hoạt động:** diff truststore có CA mới nhưng thử nghiệm certificate/hostname vẫn đạt. Ghi nhận đây là **thay đổi đã kiểm chứng**; không cần sao chép `cacerts` cũ chỉ để snapshot giống hệt.
4. **Một máy canary khác biệt:** `java.home` chỉ sang custom JDK và thứ tự provider khác staging. Quyết định sửa image/runtime provenance, chạy lại baseline và test trước khi kết luận về bản JDK.

Chính việc **so sánh default mới + kiểm thử behavior** giúp đội vận hành biết thay đổi nào đáng giữ vì hardening, thay đổi nào cần sửa dependency, và thay đổi nào là lỗi triển khai.

## 14. Checklist nghiệm thu cho bài 22.3

- [ ] Có bản JDK đầy đủ: vendor, version/build, `java.home`, image digest, OS, provider config.
- [ ] Có snapshot security properties, TLS defaults, provider order, keystore/truststore settings **cho cả JDK cũ và mới**.
- [ ] Đã so kết quả từ JDK sạch với kết quả khi ứng dụng áp override; không còn dùng full `java.security` sao chép từ JDK cũ.
- [ ] Đã đọc release notes đúng build, ghi lại tác động của các thay đổi bảo mật.
- [ ] Đã kiểm thử handshake dương/âm, hostname/certificate validation, mTLS khi dùng, keystore load và JAR signature nếu hệ thống phụ thuộc.
- [ ] Đã dùng chính JDK runtime mục tiêu để chạy test, không nhầm với JDK build/IDE.
- [ ] Security contract đạt, thay đổi expected được ghi nhận; chưa giải thích thì chặn rollout.
- [ ] Override/ngoại lệ (nếu có) được thu nhỏ, review, có owner và ngày rà soát/gỡ bỏ.
- [ ] Có canary, logging phù hợp, điều kiện rollback và không ghi secret trong báo cáo.

## 15. Câu hỏi tự kiểm tra

1. **Tại sao diff `java.security` không đủ?** Vì application có thể dùng `-D`, custom provider, SSLContext riêng, khác truststore; chỉ behavior thực mới xác nhận kết quả an toàn.
2. **Vì sao không copy nguyên `jdk.tls.disabledAlgorithms` từ JDK cũ?** Vì làm vậy có thể vô tình khôi phục TLS/cipher yếu đã bị bản mới cấm. Phải tìm nguyên nhân incompatibility và sửa peer/cert trước.
3. **Khác nhau giữa `Security.getProperty` và `System.getProperty`?** Chúng đọc hai kho cấu hình khác nhau; cùng một tên key không tự suy ra giá trị tương đương.
4. **Nếu TLSv1 xuất hiện ở `supported.protocols` nhưng không ở `default.protocols`, đã chứng minh nó bị vô hiệu mọi nơi chưa?** Chưa; cần đọc constraints và làm negative handshake test ở cấu hình thực.
5. **Nếu `jarsigner` gọi artifact “unsigned” sau update, bạn thay đổi gì?** Kiểm tra thuật toán ký, khóa và certificate; ký lại artifact theo policy hoặc sửa supply-chain pipeline, không tự mở lại thuật toán yếu.
6. **Vì sao một JDK patch update cũng cần test?** Vì security fixes/defaults/trust anchors/provider behavior có thể đổi ở bản vá; không chỉ major upgrade mới ảnh hưởng bảo mật.
7. **Bài tập:** Chạy `JdkSecuritySnapshot` trên hai runtime đã được phê duyệt, ghi một bảng thay đổi (security property/provider/TLS/keystore/truststore), thiết kế tối thiểu hai positive và hai negative test cho từng flow liên quan, rồi viết quyết định *accept / fix dependency / block* cho mỗi khác biệt.

## Tài liệu chính thức để đối chiếu

- Oracle Java SE 17 — [The Security Properties File](https://docs.oracle.com/en/java/javase/17/security/security-properties-file.html): security properties, cách override và debug.
- Oracle Java SE 25 — [Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/25/security/java-secure-socket-extension-jsse-reference-guide.html): disabled algorithms, JSSE handshake, TLS constraints.
- Oracle Java SE 25 — [Troubleshooting Security](https://docs.oracle.com/en/java/javase/25/security/troubleshooting-security.html): `java.security.debug`, `-XshowSettings:security`.
- Oracle Java SE 24 — [The jarsigner Command](https://docs.oracle.com/en/java/javase/24/docs/specs/man/jarsigner.html): `jdk.jar.disabledAlgorithms`, kiểm tra chữ ký JAR.
- Oracle Java SE 17 — [Security API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html): `getProperty` và provider APIs.
- Oracle Java SE 17 — [SSLContext API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html): default/supported SSL parameters.
- Oracle Java SE 17 — [KeyStore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): `getDefaultType()`.
- OpenJDK/Java — [JDK Trust Management](https://ops.java/security/trust): truststores, `cacerts`, PKCS#12 và bối cảnh vận hành.

**Luôn đối chiếu tài liệu và release notes của chính JDK vendor/build được triển khai:** URL của một phiên bản khác là tài liệu tham khảo cơ chế, không phải bằng chứng rằng default trên hệ thống của bạn giống hệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-004">22.4 Chẩn đoán bằng javax.net.debug, java.security.debug và nhật ký đã loại bí mật</a>

<details>
<summary>Xem chi tiết</summary>

# 22.4 Chẩn đoán bằng `javax.net.debug`, `java.security.debug` và nhật ký đã loại bí mật

## Mục tiêu học tập

Trong một ứng dụng Java có TLS, chữ ký số, chứng chỉ, keystore hoặc provider phần cứng, lỗi thường biểu hiện bằng một exception ngắn, trong khi nguyên nhân nằm ở nhiều tầng. Sau bài này, bạn có thể:

1. Phân biệt trace **JSSE/TLS** do `javax.net.debug` tạo với trace **JCA/Java Security** do `java.security.debug` tạo, đồng thời kết hợp chúng với nhật ký ứng dụng.
2. Chọn **flag nhỏ nhất đủ chẩn đoán** cho handshake, trust chain/PKIX, mTLS client certificate, keystore, provider, JAAS và PKCS#11.
3. Đọc trace theo trình tự thời gian và xác định **bước thất bại đầu tiên**, tránh suy luận lỗi dựa vào dòng cuối hoặc một tên exception chung chung.
4. Thiết kế log theo **danh sách trường được phép ghi (allowlist)** để không xuất mật khẩu, token, cookie, PIN, private key, dữ liệu giải mã, handshake dump hoặc thông điệp lỗi chứa bí mật.
5. Tạo một lần tái hiện có phạm vi, thu thập bằng chứng, bảo vệ và xóa log đúng cách; trở về cấu hình không-debug sau khi xử lý.

**Tư duy nền:** `javax.net.debug` trả lời “TLS/JSSE đang thương lượng và kiểm chứng điều gì?”. `java.security.debug` trả lời “JCA, PKIX, keystore, JAAS và security provider đang chọn/kiểm chứng điều gì?”. Nhật ký ứng dụng trả lời “request nghiệp vụ nào, ở giai đoạn nào, với kết quả nào?”. Cần ghép **ba góc nhìn** qua thời gian, kết nối và mã tương quan; không bật tất cả cờ rồi đưa toàn bộ log lên hệ thống hỗ trợ.

## 1. Ba nguồn quan sát có phạm vi khác nhau

| Nguồn | Phạm vi chủ yếu | Có thể phát hiện | Hạn chế quan trọng |
|---|---|---|---|
| `-Djavax.net.debug=...` | JSSE và TLS/DTLS của implementation tương ứng | ClientHello/ServerHello, chọn phiên bản/cipher suite, trao đổi chứng chỉ, trust/key manager, cảnh báo/đóng kết nối | Không phải logger nghiệp vụ; format và mức thông tin phụ thuộc JDK/provider; log có thể lộ metadata hay dữ liệu nhạy cảm |
| `-Djava.security.debug=...` | Java Security/JCA: chọn provider, xây dựng đường chứng chỉ, keystore, JAAS, PKCS#11… | Provider không có thuật toán, lỗi dựng certificate path, sự kiện keystore, phiên token | Không thấy đầy đủ HTTP request hay logic nghiệp vụ; nhiều tùy chọn debug có volume lớn |
| Log ứng dụng/thư viện | Request, traceId, operation, stage, exception type, outcome | Request nào lỗi, ai gọi service nào, thời điểm và tương quan với downstream | Nếu log nguyên headers/body/stack trace thì dễ lọt secret; không chứng minh mọi chi tiết TLS/JCA |

Ví dụ một API gọi downstream HTTPS rồi gặp `SSLHandshakeException`. Log nghiệp vụ có thể cho biết `traceId=req-42` và `phase=tls_connect`. `javax.net.debug` có thể cho biết handshake đã tới bước nhận certificate; `java.security.debug=certpath` có thể cho biết PKIX không xây dựng được trust path. Ba tín hiệu kết hợp **gợi ý** trust configuration hoặc server certificate chain; để kết luận vẫn cần xác minh đúng truststore, chain, hostname, endpoint và môi trường.

Không đồng nhất **lỗi Java Security** với **lỗi TLS**. Lỗi `Signature.getInstance(...)` có thể xuất hiện trong JCA mà không có một kết nối mạng nào. Ngược lại, TLS dùng JSSE nhưng có thể gọi JCA để xây dựng chain, kiểm chứng chữ ký hay lấy key từ provider phần cứng; khi đó hai dạng trace cùng xuất hiện là bình thường.

## 2. `javax.net.debug`: chọn trace TLS vừa đủ

Hệ thống SunJSSE của JDK hỗ trợ các tùy chọn debug qua Java **system property** `javax.net.debug`. Các tùy chọn được truyền vào **lúc khởi động JVM**, trước tên lớp main hoặc `-jar`:

```bash
java -Djavax.net.debug=ssl,handshake TlsProbe api.example.test 443
```

`TlsProbe` là chương trình hoàn chỉnh ở mục 6. Dấu phẩy hoặc dấu hai chấm có thể phân cách các option của SunJSSE. Cần chạy thật một thao tác TLS thì mới có handshake trace; chỉ tạo `SSLContext` chưa chắc sinh các dòng `ClientHello`/`ServerHello`.

Các nhóm thường gặp trong Oracle Java 21:

| Option | Khi nào dùng | Điều cần thận trọng |
|---|---|---|
| `ssl` | Bật phạm vi trace JSSE/SSL/TLS | Có thể nhiều dòng trong ứng dụng tạo nhiều kết nối |
| `ssl,handshake` | Nghi vấn TLS version, cipher, certificate, failure alert | Có thể chứa certificate, server name, key exchange metadata và môi trường kết nối |
| `ssl,handshake,trustmanager` | Điều tra truststore, chọn trust anchor và chứng chỉ peer | Có thể in chi tiết chứng chỉ/đường dẫn; không chia sẻ nguyên bản |
| `ssl,handshake,keymanager` | Điều tra lựa chọn chứng chỉ client trong mTLS | Có thể tiết lộ alias, DN, issuer và thông tin định danh |
| `ssl,session` / `ssl,sessioncache` | Phân tích reuse/resumption session và hành vi phiên | Session metadata có thể nhạy cảm; output không ổn định giữa phiên bản |
| `ssl,record` | Điều tra record framing/đóng kết nối khi cần thiết | Tăng lượng log, khó phân tích với nhiều kết nối |
| `ssl,handshake,data` / `ssl,handshake,verbose` | Mở rộng nội dung handshake | Dump/chi tiết rất lớn; tránh thu thập ngoài môi trường cô lập |
| `ssl,record,plaintext` | In dữ liệu record dạng plaintext | **Có nguy cơ lộ dữ liệu ứng dụng trước/sau mã hóa**; không phù hợp mặc định |
| `ssl,record,packet` | In raw packets | Có thể lộ metadata/dữ liệu; cần quy trình kiểm soát chặt |
| `all` | Bật mọi loại JSSE debug | **Không dùng cho thu thập thường quy hoặc trong production** |

Tùy chọn `help` liệt kê flag mà bản JDK thực tế hỗ trợ, ví dụ:

```bash
java -Djavax.net.debug=help TlsProbe api.example.test 443
```

Ở một số implementation, thông tin trợ giúp chỉ hiện khi lớp JSSE tương ứng được khởi tạo; quá trình có thể dừng trước khi chạy ứng dụng chính. **Không chuẩn hóa parser theo log mẫu trên blog**: tài liệu Oracle nhấn mạnh định dạng debug là internal, có thể đổi theo bản JDK và provider khác không nhất thiết dùng tiện ích SunJSSE.

Nên bắt đầu bằng `ssl,handshake` cho một lần kết nối được kiểm soát. Chỉ bật thêm `trustmanager` hoặc `keymanager` nếu câu hỏi cần trả lời liên quan chính đến kiểm chứng peer hoặc chọn client certificate. Đừng bật `plaintext`, `packet` hay `all` để “cho chắc”.

### Đọc những mốc TLS có ý nghĩa

Ở một trao đổi TLS 1.3 đầy đủ (không reuse session), trace có thể xuất hiện các giai đoạn:

```text
ClientHello -> ServerHello -> EncryptedExtensions
            -> Certificate -> CertificateVerify -> Finished
            -> Application Data
```

Đây là **sơ đồ đơn giản hóa**, không phải thứ tự mà mọi trace/provider bắt buộc in ra. TLS 1.2, TLS 1.3 PSK/resumption, mutual TLS và cảnh báo lỗi có dòng khác nhau; TLS 1.3 mã hóa nhiều thông điệp sau ServerHello trên đường truyền. Trace JSSE có thể trình bày thông tin đã được xử lý trong JVM, không thể suy ra đây là bản chụp raw packet trên mạng.

Khi debug, ghép theo **connection/thread/time** và tìm:

- Có tạo socket và gửi `ClientHello` chưa? Nếu lỗi DNS, TCP, proxy, timeout xảy ra trước TLS thì `javax.net.debug` có thể không giúp.
- Server đã phản hồi `ServerHello` và chọn version/cipher nào? Có `protocol_version` hoặc `handshake_failure` alert không?
- Đã nhận certificate peer chưa? Trust manager đã kiểm tra được chain với truststore nào?
- Nếu mTLS, key manager có chứng chỉ client phù hợp không và server có yêu cầu client auth không?
- Alert phát ra từ **phía local** hay nhận từ **peer**? Đừng coi mọi alert có cùng nguyên nhân.

Không giải thích một dòng `close_notify` hay cảnh báo ở cuối như nguyên nhân gốc nếu trước đó đã có lỗi PKIX/hostname verification. Cũng đừng khẳng định “server không tin client certificate” chỉ vì client có `SSLHandshakeException`; cần server log hoặc dấu hiệu mTLS cụ thể để phân biệt.

## 3. `java.security.debug`: JCA, đường chứng chỉ và provider

`java.security.debug` là một Java system property khác, không phải flag của JSSE:

```bash
java -Djava.security.debug=certpath TlsProbe api.example.test 443
```

Các nhóm hữu ích theo Oracle Java 21:

| Option | Quan sát điều gì? | Ví dụ câu hỏi |
|---|---|---|
| `certpath` | Xây dựng và xác minh certificate path (PKIX) | Vì sao chain không nối được tới trust anchor? |
| `provider` | Hoạt động Java security provider/JCA engine | Nhà cung cấp nào đăng ký hoặc được dùng cho `Signature`/`Cipher`? |
| `jca` | JCA engine classes/algorithm resolution | `getInstance` chọn dịch vụ ra sao? |
| `keystore`, `pkcs12` | Keystore và format PKCS#12 | File keystore/truststore có đang được đọc không? |
| `pkcs11`, `pkcs11keystore`, `sunpkcs11` | Session/KeyStore/provider PKCS#11 | Vì sao token/alias/slot không dùng được? |
| `configfile`, `configparser`, `logincontext` | Cấu hình/login JAAS | Vì sao entry JAAS hoặc LoginModule thất bại? |
| `properties` | Việc đọc/cấu hình security properties | JDK đang lấy cấu hình security từ đâu? |
| `x509` | X.509 certificate | Một bước xử lý chứng chỉ đã đi đâu? |
| `securerandom` | Khởi tạo/sử dụng nguồn ngẫu nhiên | Vì sao chọn provider/random implementation khác kỳ vọng? |

Có thể ghép các option bằng dấu phẩy:

```bash
java -Djava.security.debug=certpath,keystore TlsProbe api.example.test 443
```

Cờ `certpath` có các tùy chọn mở rộng như `ocsp` và `verbose` dành cho các bài toán revocation/path cụ thể; những chế độ này có thể ghi dữ liệu OCSP và thêm chi tiết từ chứng chỉ. Chỉ sử dụng sau khi xác nhận đúng bản JDK, có phạm vi mạng/nhật ký phù hợp và đã quyết định cần trả lời câu hỏi nào.

Các thao tác **read-only** để xác nhận cấu hình security trước khi tạo trace dài:

```bash
java -XshowSettings:security:providers -version
java -XshowSettings:security:tls -version
java -Djava.security.debug=help -version
```

`-XshowSettings:security:providers` liệt kê provider được cấu hình; `:tls` cho biết một số thiết lập liên quan TLS. Bản JDK khác nhau có thể khác output. Những lệnh này cũng có thể lộ môi trường máy/đường dẫn/cấu hình; thu thập và chia sẻ có kiểm soát.

**Bẫy thường gặp:** `provider`, `jca` hoặc `certpath` chỉ trace code **thực sự được chạy**. Một provider có tên trong registry không chứng minh engine đã sử dụng nó. Khi ứng dụng chọn `Cipher.getInstance("AES/GCM/NoPadding", provider)`, phải kiểm tra service/algorithm và thời điểm exception; `NoSuchAlgorithmException` có thể báo thiếu engine, `InvalidKeyException` báo key không phù hợp dù engine được tạo thành công.

Không mặc định lỗi chứng chỉ phải sửa bằng `trustAll`, bỏ hostname verification hoặc sửa algorithm constraints. Các hành động đó thay đổi ranh giới tin cậy thay vì giải quyết chứng chỉ/cấu hình thật.

## 4. Chọn debug theo triệu chứng — sơ đồ quyết định

```text
Lỗi nhìn thấy
 |
 +-- Trước khi có TLS handshake?
 |      -> DNS / TCP / proxy / firewall / timeout; dùng log mạng phù hợp
 |
 +-- TLS handshake/SSLHandshakeException?
 |      -> javax.net.debug=ssl,handshake
 |           |
 |           +-- Trust path/certificate?
 |           |      -> thêm trustmanager; java.security.debug=certpath
 |           |
 |           +-- Client certificate/mTLS?
 |           |      -> thêm keymanager; xác minh log phía server
 |           |
 |           +-- Version/cipher/alert?
 |                  -> handshake + policy/cấu hình hai đầu
 |
 +-- KeyStore / PKCS#11 / cryptography?
 |      -> java.security.debug=keystore,provider hoặc pkcs11keystore
 |
 +-- JAAS LoginContext?
 |      -> java.security.debug=configfile,logincontext
 |
 +-- Log nghiệp vụ thiếu traceId/stage?
        -> bổ sung log allowlist, không log header/token/payload thô
```

Đây là **trình tự ra quyết định**, không phải lệnh phải bật nhiều flag cùng lúc. Bắt đầu từ lỗi tối thiểu, tạm thời mở rộng đúng nhóm, thu một lần tái hiện và dừng.

### Trường hợp 1: `PKIX path building failed`

Một đoạn exception thường thấy (nội dung có thể khác):

```text
javax.net.ssl.SSLHandshakeException:
    PKIX path building failed: ...
```

Đặt câu hỏi theo thứ tự: client dùng truststore nào? Server gửi leaf/intermediate chain gì? Chứng chỉ còn hạn và không vi phạm các ràng buộc trust/algorithm? Có bị proxy TLS interception? Chứng chỉ server có đúng danh tính endpoint đang kết nối?

Dùng `javax.net.debug=ssl,handshake,trustmanager` và `java.security.debug=certpath` trong **một lần tái hiện ngắn**. Trace certpath giúp xem đường xây dựng/điểm fail. Không tùy tiện import leaf certificate từ mạng vào truststore production; phải kiểm tra CA hoặc mối quan hệ tin cậy đã được duyệt và đúng endpoint trước khi cấu hình lại.

### Trường hợp 2: certificate hostname mismatch

Một chain được CA tin cậy **vẫn có thể** không khớp tên host. Trong HTTPS, hostname verification là lớp bảo vệ độc lập với CA path. Khi dùng `SSLSocket` trực tiếp, ứng dụng phải yêu cầu thuật toán endpoint identification phù hợp, chẳng hạn `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`. Ví dụ ở mục 6 bật rõ điều này. Không xử lý mismatch bằng cách tắt verification hay đổi toàn bộ `TrustManager` thành trust-all.

### Trường hợp 3: mTLS không chọn client certificate

Nếu server yêu cầu xác thực bằng chứng chỉ client, client cần private key, certificate chain, key manager và quyền dùng khóa. `ssl,handshake,keymanager` giúp chẩn đoán việc lựa chọn alias/certificate; `java.security.debug=keystore` có thể hỗ trợ khi keyStore không tải được; với HSM, kiểm tra thêm provider PKCS#11 và mechanism. Vẫn cần server log để biết server đã thấy/chấp nhận chứng chỉ nào. Client key manager **không** phải trust manager: một cái cung cấp identity của client, cái kia đánh giá peer.

### Trường hợp 4: `NoSuchAlgorithmException` hoặc `InvalidKeyException`

Đây có thể là lỗi provider/algorithm/key ở JCA/JCE mà không liên quan handshake TLS. Dùng `provider`/`jca` trước; với HSM thêm `sunpkcs11` hoặc `pkcs11keystore` nếu cần. Kiểm tra loại khóa, `Provider.Service`, chính xác transformation, key usage và trạng thái token. Không dùng `java.security.debug=all` chỉ để nhận biết tên provider.

## 5. Cách chạy và thu log có kiểm soát

Chỉ bật cờ debug khi đã có một giả thuyết và một kịch bản ngắn. Ví dụ trên Bash, khi đã tạo `TlsProbe.class`:

```bash
java \
  -Djavax.net.debug=ssl,handshake,trustmanager \
  -Djava.security.debug=certpath \
  TlsProbe api.example.test 443
```

Trên Windows PowerShell, dùng **một dòng** để tránh quy tắc escape của các shell khác nhau:

```powershell
java '-Djavax.net.debug=ssl,handshake,trustmanager' '-Djava.security.debug=certpath' TlsProbe api.example.test 443
```

Các cờ `-D` là tùy chọn JVM, phải đặt **trước** tên lớp main hoặc `-jar`. Chúng không phải tham số `args` được truyền cho chương trình. Nếu ứng dụng chạy trong container/JVM được điều phối, áp dụng đúng vào tiến trình cần đo; cờ trên shell của máy quản trị không tự ảnh hưởng JVM đang chạy ở máy khác.

Khi bảo toàn log phục vụ điều tra:

- Thu một **cửa sổ thời gian ngắn**, một môi trường được phép và một số request cố định. Nếu không cần, đừng lưu raw TLS trace trên central logger dùng chung.
- Phân biệt `stdout` và `stderr` của chương trình với debug từ JDK. Có thể có stack trace và log framework đan xen; không suy đoán thứ tự tuyệt đối từ một file đã trộn nhiều process.
- Ghi **JDK vendor/version**, cấu hình runtime liên quan, loại client/server, thời gian UTC, traceId, endpoint **đã được phân loại/anonymize**, alias hoặc chứng chỉ **nếu được phép**, các flag đã bật và thời điểm tắt.
- Thêm mã tương quan từ log ứng dụng như `traceId`, `requestId`. JDK TLS debug không đảm bảo tự in traceId ứng dụng, nhất là với connection pooling, async, HTTP/2 hoặc thread tái sử dụng. Tương quan bằng connection/time/thread chỉ là đầu mối, phải xác nhận trước kết luận.
- Lưu file debug với quyền truy cập tối thiểu, không mở chia sẻ công khai, đặt thời hạn lưu ngắn và xóa raw copy sau khi tạo bản trích dẫn đã kiểm duyệt.
- Không đính kèm các file chứa PIN, key material, token, session cookie, request body, plaintext, packet dumps hoặc đầy đủ certificate/private configuration khi gửi issue/trợ giúp ngoài phạm vi được phép.
- Sau điều tra, **gỡ flag ở cấu hình triển khai/launcher**, restart đúng JVM nếu cần và xác nhận lượng log quay về bình thường.

Không bật `javax.net.debug=all` hoặc `java.security.debug=all` lâu dài trên production: volume lớn, giảm tính đọc được và có nguy cơ phát tán dữ liệu nhạy cảm. Các trace này do JDK/provider phát ra, thường **không đi qua** filter/redaction của logger ứng dụng. Vì vậy “logger của service đã mask Authorization” **không đảm bảo** JDK debug output đã an toàn.

## 6. Ví dụ Java 17+: TLS probe có hostname verification

Mã sau thực hiện **kết nối HTTPS/TLS trực tiếp tới host, port mà người chạy chỉ định**, đặt connect/read timeout và gọi `startHandshake()`. Nó bật endpoint identification `HTTPS` cho `SSLSocket` (điểm dễ bỏ sót khi viết probe tay) và chỉ in một số thông tin không cần payload. Nó không gửi HTTP request.

Tạo file `TlsProbe.java`:

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class TlsProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java TlsProbe <host> <port>");
            System.exit(2);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (SSLSocket socket = (SSLSocket)
                SSLSocketFactory.getDefault().createSocket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);

            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);

            socket.startHandshake();
            SSLSession session = socket.getSession();
            System.out.println("event=tls_connected protocol=" +
                    session.getProtocol());
        } catch (IOException error) {
            System.err.println("event=tls_failed exceptionType=" +
                    error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
```

Biên dịch bằng JDK 17+:

```bash
javac --release 17 TlsProbe.java
```

Chạy đối với **endpoint mà bạn có quyền kiểm thử**:

```bash
java -Djavax.net.debug=ssl,handshake TlsProbe api.example.test 443
```

Khi cần kiểm tra trust path, thử trong môi trường được kiểm soát:

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager -Djava.security.debug=certpath TlsProbe api.example.test 443
```

**Kết quả không cố định** vì phụ thuộc DNS, mạng, TLS version/cipher, chứng chỉ và truststore. Một kết nối thành công có thể in `event=tls_connected protocol=TLSv1.3` (hoặc phiên bản khác). Nếu thất bại, ứng dụng chỉ in `event=tls_failed exceptionType=SSLHandshakeException`, `SocketTimeoutException`, `ConnectException`… tùy lỗi thực tế; exit code bằng 1. JSSE/JCA debug, nếu bật, sẽ in thêm các dòng riêng và **không được xem là log đã loại bí mật**.

Đoạn mã có chủ ý **không** in `error.getMessage()`, `error.toString()`, `printStackTrace()`, certificate chain hay raw URL. Điều này bảo vệ log do chương trình tự tạo; không ngăn trace JDK do các cờ `-D` tạo ra. Trong production, không nên mở một endpoint probe tùy ý cho người dùng chưa xác thực; nếu tích hợp công cụ chẩn đoán vào service, cần allowlist endpoint và hạn chế nguy cơ SSRF.

**Giới hạn:** Probe dùng `SSLSocket` qua TCP và cấu hình trust/key mặc định của JVM. Nó không tự tái hiện proxy, HTTP client, ALPN, mTLS client keystore tùy chỉnh, session pooling hoặc cơ chế app container của hệ thống thật. Thực hiện chẩn đoán trên đường gọi tương đương mới có chứng cứ phù hợp.

## 7. Ví dụ Java 17+: logger allowlist không ghi exception message

Sửa log ứng dụng để chỉ lưu **trường chẩn đoán cần thiết**, thay vì lấy toàn bộ exception. Ví dụ dưới đây cố ý tạo một `SSLHandshakeException` nhân tạo và chỉ in type của exception.

Tạo file `SafeTlsAuditDemo.java`:

```java
import javax.net.ssl.SSLHandshakeException;

public class SafeTlsAuditDemo {
    enum Stage { CERTIFICATE_VALIDATION, HANDSHAKE, PROVIDER_LOOKUP }

    static void logFailure(String traceId, Stage stage, Exception error) {
        if (!traceId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid trace identifier");
        }
        System.out.printf(
            "event=tls_failure traceId=%s stage=%s exceptionType=%s%n",
            traceId, stage.name(), error.getClass().getSimpleName());
    }

    public static void main(String[] args) {
        SSLHandshakeException failure =
            new SSLHandshakeException("Synthetic diagnostic details not logged");
        logFailure("req-42", Stage.CERTIFICATE_VALIDATION, failure);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 SafeTlsAuditDemo.java
java SafeTlsAuditDemo
```

**Stdout chính xác** (đã biên dịch và chạy):

```text
event=tls_failure traceId=req-42 stage=CERTIFICATE_VALIDATION exceptionType=SSLHandshakeException
```

Vì sao an toàn hơn cách thường thấy?

- Chỉ ba trường `traceId`, `stage`, `exceptionType` được ghi; `stage` là enum, không nhận chuỗi tùy ý từ người dùng.
- `traceId` có danh sách ký tự/độ dài cho phép; tránh ký tự xuống dòng hay thao tác log injection. Trong hệ thống thực, ID nên do thành phần tin cậy sinh và có quy tắc bảo vệ riêng.
- `error.getClass().getSimpleName()` không bao gồm thông điệp exception, endpoint hay nội dung certificate. Ngoại lệ ở đây được tạo **chỉ để minh họa đường ghi log**, không đại diện cho một lỗi PKIX thật.
- Mã không gọi `error.getMessage()`, `error.toString()` hay log nguyên đối tượng `Throwable`. Với framework logging, truyền exception làm tham số cuối thường sẽ in stack trace/message, cần xem kỹ cơ chế của thư viện đang dùng.

Ví dụ không phải hệ thống SIEM đầy đủ; production nên dùng log có cấu trúc (structured logging), quy tắc phân loại dữ liệu, quyền truy cập, retention, sampling, metric và tracing đáng tin cậy. Mấu chốt là **chọn đúng trường được phép ghi từ đầu**, không trông cậy vào chuỗi regex để xóa mọi bí mật sau khi đã log.

## 8. Chính sách “nhật ký đã loại bí mật” phải cụ thể

Một câu như “hãy mask thông tin nhạy cảm” chưa đủ. Cần xác định **thông tin nào được phép** xuất hiện theo từng nguồn và từng cấp độ debug.

| Nhóm thông tin | Cách xử lý ưu tiên |
|---|---|
| `event`, `stage`, `outcome`, exception class, mã lỗi do ứng dụng định nghĩa | Cho phép, dạng enum/constant cố định; có thể dùng cho metric |
| `traceId`/`requestId` | Cho phép nếu ID được sinh ngẫu nhiên/kiểm soát, không chứa user PII, token và có retention thích hợp |
| Service hoặc endpoint | Ghi service logical name hoặc nhãn host đã duyệt; tránh nguyên URL có username/password/query |
| Tên provider/algorithm/JDK version | Có thể ghi có kiểm soát; cân nhắc rò rỉ thông tin môi trường và bảo mật nội bộ |
| Certificate subject/SAN/serial/fingerprint | Phân loại theo môi trường; chỉ giữ trường cần thiết, xác nhận không chứa identifier riêng tư |
| HTTP `Authorization`, `Cookie`, `Set-Cookie`, bearer/JWT, API key, session ID | **Không ghi** dù đã hứa mask trên pipeline hậu xử lý |
| Mật khẩu, PIN HSM, private key, seed, symmetric key, plaintext giải mã | **Không ghi**; không chuyển dữ liệu thô vào exception/log argument |
| HTTP request/response body, query, URL credentials | Mặc định không ghi; nếu bắt buộc phân tích thì xây test fixture không có dữ liệu thật |
| `javax.net.debug` `all`/`plaintext`/`packet` và raw JDK trace | Xem là **artifact nhạy cảm**, cô lập, xem trước khi chia sẻ, xóa theo thời hạn |
| Stack trace và exception message nguyên văn | Chỉ lưu trong kênh bảo vệ riêng khi chính sách cho phép; dữ liệu bên trong không được bảo đảm đã sạch |

Nếu phải làm sạch một bản trace trước khi chia sẻ, cần phân tích toàn bộ nguồn dữ liệu chứ không chỉ các chuỗi có từ khóa `password` hoặc `token`. Secret có thể xuất hiện dưới dạng Base64, hex, URI query, JSON nhiều dòng, certificate metadata, request body hoặc trong thông báo của nhà cung cấp HSM.

**Ba tầng bảo vệ:**

1. **Ngăn sinh ra log quá nhạy cảm:** không bật `plaintext`/`packet`/`all`, không gọi `toString()` của request/header/secret object, không đưa credentials vào URL, argv hay log context.
2. **Thu và kiểm soát truy cập:** giới hạn nơi ghi, thời gian, ai được đọc, mã hóa khi truyền/lưu, tránh tự động ship raw JDK logs sang hệ thống tập trung.
3. **Kiểm duyệt artifact trước khi chia sẻ:** trích riêng vài dòng đã giảm thông tin hoặc dựng lại tình huống bằng dữ liệu giả; kiểm tra thủ công/automated scans theo chính sách. Regex có thể bổ trợ nhưng không đảm bảo bắt hết.

Không ghi bí mật rồi gọi `String.replaceAll(...)` như một “bảo đảm” vì pattern không bao quát mọi encoding/bối cảnh. Ở tầng ứng dụng, ưu tiên allowlist field và dữ liệu do hệ thống sinh. Ở tầng JDK debug, phải kiểm soát **đầu ra nguyên bản** vì không chắc nó đi qua sanitizer/log framework của ứng dụng.

### Mẫu bản ghi điều tra đã giảm thông tin

```text
timeUtc=2026-10-31T10:00:00Z
service=orders-client
traceId=req-42
operation=downstream_tls_connect
phase=certificate_validation
outcome=failed
exceptionType=SSLHandshakeException
tlsProbe=handshake_only
securityDebug=certpath
rawDebugArtifact=restricted
```

Đây là **dữ liệu giả minh họa cấu trúc**, không phải kết quả từ hệ thống thật. Không thêm token, request body, full URL, private key, certificate chain hay stack trace vào bản ghi chia sẻ. Chỉ dùng traceId để nối với raw log nằm trong vùng kiểm soát; không gộp raw debug vào bản ghi đã giảm thông tin.

## 9. Trình tự điều tra có thể lặp lại

1. **Định danh lỗi:** ghi service/version/JDK/vendor, time window (UTC), thao tác nghiệp vụ, exception **type**, phần kết nối (outbound hay inbound TLS) và môi trường.
2. **Xác định tầng:** nếu DNS/TCP fail trước TLS thì dùng network telemetry; nếu TLS handshake fail, dùng JSSE; nếu PKIX/provider/keystore fail, thêm đúng Java Security option; nếu ứng dụng từ chối quyền sau TLS thì xem policy nghiệp vụ.
3. **Xác định một giả thuyết:** ví dụ “truststore JVM thiếu CA phát hành intermediate” hoặc “mTLS client không chọn certificate có issuer phù hợp”.
4. **Tái hiện có phạm vi:** một request tới endpoint kiểm thử đã được phép, bật `ssl,handshake`; chỉ mở rộng `trustmanager`/`certpath` nếu cần, không bật mọi flag.
5. **Đọc theo sự kiện đầu tiên:** trước alert/exception cuối cùng, tìm lựa chọn version/cipher, certificate, trust manager, key manager và kết quả path. So sánh với cấu hình hai phía, không chỉ đếm error lines.
6. **Tạo bản tóm lược đã lọc bí mật:** traceId, stage, exception class, config difference đã xác minh, đề xuất khắc phục và bằng chứng rút gọn. Bản raw giữ riêng có access control.
7. **Sửa đúng ranh giới:** cấp chain hợp lệ, thay truststore/endpoint configuration đúng chính sách, chỉnh client key alias hoặc mechanism provider. Không tắt TLS verification.
8. **Chạy lại và gỡ debug:** xác minh kết nối đúng identity, quyền và giao thức; tắt flag, xác nhận không còn trace không cần thiết; xóa/retire dữ liệu nhạy cảm theo policy.

Giai đoạn xác nhận cần tránh “fix” khiến kết nối luôn thành công bằng cách tin mọi certificate. Kết quả đúng phải là **kết nối tới đúng peer với trust policy đúng**, không đơn thuần là không còn exception.

## 10. Bảng triệu chứng → tín hiệu cần lấy → hướng giải thích

| Triệu chứng | Tín hiệu ban đầu | Lớp cần điều tra |
|---|---|---|
| `UnknownHostException`, `ConnectException` hoặc `SocketTimeoutException` trước `startHandshake` | Log kết nối TCP/proxy/DNS với endpoint logical name, timeout | Mạng, DNS, firewall, routing; JSSE handshake có thể chưa chạy |
| `SSLHandshakeException` kèm `PKIX path building failed` | `ssl,handshake,trustmanager` + `certpath` | CA/trust anchor, intermediate, truststore, đường chứng chỉ, policy |
| `CertificateException` nói SAN/hostname không khớp | Host được gọi, SAN của cert đã kiểm duyệt, trạng thái endpoint identification | Hostname verification, SNI/proxy và server certificate |
| `handshake_failure` hoặc `protocol_version` alert | `ssl,handshake`; đối chiếu cấu hình hai phía | TLS protocol/cipher/signature schemes hoặc chính sách của server |
| Lỗi mTLS, client không gửi certificate | `ssl,handshake,keymanager` + log server được phép | Client keyStore, alias, issuer, certificate request, HSM token |
| `UnrecoverableKeyException` khi lấy key | `keystore`/`pkcs12`, cấu hình alias và KeyStore | Loại store, password/entry/key, không ghi mật khẩu |
| `NoSuchAlgorithmException`/`NoSuchPaddingException` | `provider`/`jca`, type/algorithm đã yêu cầu | Registry provider và dịch vụ được hỗ trợ |
| `InvalidKeyException` khi dùng `Signature`/`Cipher` | Provider đã chọn, loại/key usage, thiết bị nếu có | Key handle/provider mismatch, policy token, mechanism |
| `LoginException` ở JAAS | `configfile`/`logincontext` | Cấu hình entry, LoginModule, control flag và credential |
| Raw log quá lớn hoặc có thông tin nhạy cảm | Cờ debug đang bật, đường thu stdout/stderr, shipping pipeline | Phạm vi logging, access control, retention và quy trình redaction |

Các triệu chứng trong bảng là **gợi ý chẩn đoán**, không phải ánh xạ một-một. Một lần handshake thất bại có thể chứa nhiều exception lồng nhau; phụ thuộc cả proxy, truststore tùy chỉnh, TLS stack của JDK và dịch vụ đích.

## 11. Những lỗi tư duy và vận hành cần tránh

**Bật mọi thứ để biết nhiều hơn.** Kết quả thường là quá nhiều log, lẫn nhiều connection và có nguy cơ lộ dữ liệu. Khởi đầu bằng cờ nhỏ nhất tương ứng câu hỏi, tăng một chiều theo bằng chứng.

**Đọc `alert` cuối như nguyên nhân.** Alert có thể là **hệ quả** của certpath/hostname/algorithm failure đã xảy ra trước. Kiểm tra dấu thời gian, phía phát alert và root cause của exception chain.

**Cho rằng `java.security.debug=certpath` luôn hiện khi có lỗi TLS.** Nó chỉ in khi đường PKIX tương ứng được chạy trong implementation phù hợp. Nếu kết nối thất bại trước bước chứng chỉ hoặc provider riêng dùng cơ chế khác, không có certpath trace chưa đủ kết luận rằng chứng chỉ hợp lệ.

**Đồng nhất truststore với keystore private key.** Truststore chứa trust anchors phục vụ **đánh giá peer**; keystore identity của mTLS có private key/certificate cho **client**. Một thiết lập sai có thể khiến client không chứng thực server hoặc không trình certificate. Log trustmanager/keymanager phục vụ hai hướng khác nhau.

**Bỏ xác thực vì test nhanh.** Trust-all manager hoặc tắt hostname verification tạo lỗ hổng MITM và khiến bài kiểm thử không đại diện production. Dùng CA/chain và SAN đúng hoặc môi trường test có trust anchor được quản trị.

**In nguyên exception để tránh mất dữ liệu.** Exception message/stack có thể chứa URL, username, path, cert subject, dữ liệu vendor hoặc object toString. Bản ghi chia sẻ nên giữ exception *type* và một mã lỗi do ứng dụng kiểm soát; nơi cần raw stack phải có mức bảo vệ riêng.

**Chỉ che Authorization rồi coi log đã sạch.** Cookie, query string, request body, plaintext TLS, headers khác, PIN HSM, cipher inputs và token trong message vẫn có thể rò. JDK debug có thể không đi qua filter ứng dụng.

**Lấy output của một JDK làm hợp đồng ổn định.** Debug lines là diagnostic interface nội bộ, có thể đổi theo release/vender. Dùng chúng để điều tra thực tế, không viết parser production phụ thuộc tuyệt đối vào câu chữ hoặc số dòng cố định.

**Bật cờ `-D` trong mã sau khi hệ thống TLS đã khởi tạo.** Việc khởi tạo/caching trong provider có thể khiến option muộn không cho output kỳ vọng. Với điều tra tái lập, đặt JVM startup flags trước khi bắt đầu, khởi động đúng process và xác minh cờ có hiệu lực.

## 12. Bài luyện tập tư duy

1. **Ứng dụng gọi HTTPS và báo `PKIX path building failed`:** chọn gì trước? **`javax.net.debug=ssl,handshake,trustmanager` cùng `java.security.debug=certpath`**, rồi xác minh truststore, chain, SAN và môi trường; không chuyển sang trust-all.
2. **mTLS có `CertificateRequest` nhưng client không gửi certificate:** quan sát gì? **`ssl,handshake,keymanager`**, keyStore/alias/certificate phù hợp và server log nếu được phép. Kiểm tra private-key usage và provider HSM nếu khóa nằm ngoài JVM.
3. **`Signature.getInstance("SHA256withRSA")` ném `NoSuchAlgorithmException` nhưng không có kết nối TLS:** dùng công cụ nào? **`java.security.debug=jca,provider`** và danh sách provider/service; `javax.net.debug` có thể không giúp.
4. **Ứng dụng có field `traceId` và `exception.getMessage()`:** nên log gì? **traceId đã validate, stage, outcome và exception class**; không log message nguyên vẹn nếu chưa có chính sách/kiểm duyệt an toàn.
5. **Có yêu cầu gửi toàn bộ `javax.net.debug=all` lên issue công khai:** xử lý thế nào? Thu hẹp điều tra, tạo bản tóm lược trường allowlist và bằng chứng đã kiểm duyệt; raw trace giữ ở kho hạn chế truy cập hoặc tái hiện bằng dữ liệu giả.
6. **Sau khi sửa truststore, TLS thành công khi tắt hostname verification:** đã sửa đúng chưa? **Chưa**. Kết nối chỉ hợp lệ khi chain và identity peer đều qua kiểm tra; phải khắc phục certificate/hostname gốc.
7. **Không thấy `ClientHello` dù bật `ssl,handshake`:** điều này chứng minh server từ chối TLS không? **Không**. Có thể DNS/TCP/proxy lỗi trước TLS, debug flag sai JVM, hoặc TLS implementation không sử dụng tiện ích này.
8. **Một file log đã chạy regex thay `password=...` bằng `password=[REDACTED]`:** có thể công bố an toàn chưa? **Chưa thể khẳng định**. Cần kiểm tra các ngữ cảnh mang bí mật khác và ưu tiên dựng bản trích dẫn từ các trường cho phép.

## Tài liệu chính thức

- Oracle Java 21, *Java Secure Socket Extension (JSSE) Reference Guide* — Debugging Utilities và Debugging TLS Connections: https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html
- Oracle Java 21, *Troubleshooting Security* — `java.security.debug` và `-XshowSettings:security`: https://docs.oracle.com/en/java/javase/21/security/troubleshooting-security.html
- Java SE 21 API, `SSLParameters.setEndpointIdentificationAlgorithm`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html
- Java SE 21 API, `SSLSocket` / `startHandshake`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocket.html
- Oracle Java 21, *Java Cryptography Architecture (JCA) Reference Guide* — provider, engine classes và KeyStore: https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-005">22.5 Kiểm thử bằng bộ dữ liệu đã biết kết quả và kiểm tra tương thích giữa provider</a>

<details>
<summary>Xem chi tiết</summary>

# 22.5. Kiểm thử bằng bộ dữ liệu đã biết kết quả và kiểm tra tương thích giữa cryptographic provider trong Java

## 1. Mục tiêu và phạm vi

Trong Java Security / Java Cryptography Architecture (JCA/JCE), ứng dụng có thể gọi cùng một API như `MessageDigest`, `Mac`, `Cipher`, `Signature`, `KeyAgreement` hoặc `KeyStore` nhưng nhận implementation từ **security provider** khác nhau: provider có sẵn trong JDK, thư viện bên thứ ba, hệ điều hành hoặc HSM qua PKCS#11. Hai provider cùng quảng bá một tên thuật toán không tự bảo đảm rằng chúng tương thích với mọi tham số, cách đóng gói, kiểu khóa hoặc chính sách bảo mật.

Bài này tập trung vào **hai loại kiểm thử bổ sung nhau**:

1. **Known Answer Test (KAT):** đưa vào một bộ input cố định, đã được chuẩn hoặc nguồn độc lập xác nhận, rồi đối chiếu output **đúng từng byte** với kết quả dự kiến. Mục tiêu là chứng minh đường thực thi thuật toán + tham số + encoding cho kết quả đúng đối với vector đó.
2. **Provider interoperability test:** để provider A tạo hoặc bảo vệ dữ liệu, provider B xác minh hoặc giải mã, rồi đổi chiều B → A. Mục tiêu là kiểm tra **tính tương thích định dạng, tham số, khóa và thao tác thực tế**, không chỉ việc cả hai chạy độc lập.

Đây là kiến thức thuộc giai đoạn **vận hành và kiểm thử an toàn mã hóa**: phát hiện sai cấu hình khi đổi JDK/provider, kiểm tra các bản cập nhật, đo mức độ tương thích HSM hoặc provider mới, ngăn hệ thống âm thầm rơi về thuật toán/giá trị mặc định không phù hợp.

**Phân biệt ngay từ đầu:** một lần encrypt → decrypt thành công trên cùng provider là *round-trip*, chưa phải KAT độc lập; hai provider cho cùng output ở một vector chưa chứng minh chúng đã hỗ trợ an toàn mọi ca; và KAT PASS **không phải chứng nhận FIPS 140**, không thay thế thiết kế giao thức an toàn hay security review.

## 2. KAT là gì? Vì sao round-trip chưa đủ?

Giả sử phần mã hóa và giải mã của provider cùng nhầm lẫn một chi tiết: cách xây dựng counter, thứ tự byte hoặc cách tách authentication tag. Khi mã hóa rồi giải mã bằng **cùng một lỗi**, round-trip vẫn có thể trả lại plaintext ban đầu. KAT sử dụng kết quả đã biết độc lập để phát hiện sai lệch ấy.

```text
KAT:
  input cố định + key + IV/nonce + parameters
                   |
             Provider đang test
                   |
             output thực nhận
                   |
      so với expected lấy từ chuẩn độc lập
                   |
           PASS hoặc FAIL từng byte

Round-trip:
  plaintext --> encrypt(A) --> decrypt(A) --> plaintext?
  Chỉ kiểm tra hai chiều có nhất quán; không chứng minh kết quả chuẩn.

Cross-provider:
  plaintext --> encrypt(A) --> decrypt(B) --> plaintext?
  plaintext --> encrypt(B) --> decrypt(A) --> plaintext?
  Kèm KAT độc lập và các trường hợp dữ liệu lỗi.
```

Một test suite tốt có **nhiều lớp chứng cứ**:

| Lớp kiểm thử | Câu hỏi được trả lời | Giới hạn |
| --- | --- | --- |
| KAT SHA-256/HMAC/AES-GCM | Kết quả của vector chuẩn có khớp chính xác? | Chỉ kiểm tra các trường hợp đã chọn |
| Round-trip | Cùng provider có hoàn tất luồng hai chiều? | Có thể bỏ sót lỗi chung của cả hai chiều |
| KAT trên từng provider | Mỗi implementation có tạo/nhận đúng vector? | Không phủ đủ chuyển giao dữ liệu giữa hai provider |
| Cross-provider A → B, B → A | Dữ liệu do A tạo có được B hiểu đúng và ngược lại? | Nếu chỉ có một provider thì **chưa có** cặp để kiểm thử |
| Negative/tamper tests | Dữ liệu bị sửa, tag sai, AAD sai có bị từ chối? | Không tự bảo đảm chống side-channel, quản lý khóa hoặc nonce |
| Integration/CI test | Hệ thống thật có dùng đúng provider, HSM, key store và giao thức mong muốn? | Cần chạy trên môi trường triển khai và key policy thực |

**Expected result phải độc lập.** Không tạo file expected bằng cách chạy chính implementation đang được kiểm thử rồi lưu làm “golden” nếu chưa đối chiếu chuẩn khác. Khi dùng corpus bên ngoài, cần ghi lại nguồn, phiên bản, test case, hash của file fixture và người phê duyệt thay đổi để tránh lỗi “cập nhật expected theo kết quả sai”.

## 3. Cấu trúc một test vector có thể tái lập

Với mỗi vector, lưu rõ:

- **Nguồn chuẩn và test ID:** ví dụ FIPS 180-4, RFC 4231 Test Case 1, NIST GCM Example/Case; không ghi “theo Internet” hoặc “tự sinh”.
- **Primitive và transformation chính xác:** `SHA-256`, `HmacSHA256`, `AES/GCM/NoPadding`; không chỉ ghi `AES` vì mode/padding mặc định có thể phụ thuộc provider.
- **Input bytes:** plaintext, key, IV/nonce, AAD, salt, tag hoặc digest, cùng độ dài và encoding. “abc” là các byte ASCII `61 62 63`, không phải sáu byte của chuỗi ký tự `"616263"`.
- **Tham số:** key size, IV length, authentication tag length **tính bằng bit** (trong `GCMParameterSpec`), thứ tự đưa AAD vào, MGF digest/salt length với RSA-PSS, parameter spec với RSA-OAEP, v.v.
- **Output mong đợi:** digest/MAC hoặc ciphertext và tag; định nghĩa rõ `ciphertext || tag` hay tag lưu riêng, định dạng hex/base64 và thứ tự byte.
- **Hành vi âm tính:** sửa một bit ciphertext/tag, dùng sai AAD/key/nonce, dữ liệu cắt cụt và ngoại lệ mong đợi.
- **Điều kiện chạy:** JDK, provider tên/phiên bản, OS, runtime crypto policy, cấu hình FIPS/HSM, key origin và yêu cầu capability.

Để tránh lỗi dễ gặp:

| Sai khác đầu vào | Hậu quả |
| --- | --- |
| UTF-8 so với UTF-16 hoặc default charset | Byte plaintext khác → digest/MAC khác |
| Hex string so với raw bytes | Cipher làm việc trên dữ liệu khác vector |
| GCM `tLen = 16` thay vì `128` | API nhận **bit**; 16 có thể sai/không được hỗ trợ |
| AAD đưa sau `update/doFinal` | Sai thứ tự API GCM, có thể ném `IllegalStateException` |
| `IV = 12 bytes` bị coi là 12 bits | Độ dài nonce sai |
| Tag đặt trước ciphertext thay vì nối sau | Hai provider không hiểu cùng wire format |
| Chữ hoa/chữ thường của hex | Là chuyện biểu diễn, phải so byte hoặc chuẩn hóa |
| Secret key nhập từ hardware token bị yêu cầu `getEncoded()` | Key không export được; test phải thực hiện qua handle/provider phù hợp |

Trong test, có thể dùng `HexFormat.of().parseHex(...)` (Java 17+) để chuyển ASCII hex → byte[]. Đối chiếu byte dùng `MessageDigest.isEqual(actual, expected)` hoặc `Arrays.equals` trong test; khi so bí mật trong ứng dụng, cân nhắc hàm so sánh không phụ thuộc sớm vào nội dung và threat model. **Không log production keys hoặc secret plaintext** chỉ để debug KAT; các giá trị khóa số 0 ở ví dụ dưới đây là **dữ liệu test công khai**, tuyệt đối không dùng cho dữ liệu thật.

## 4. Ba bộ KAT cơ bản có nguồn độc lập

### 4.1. SHA-256 với `"abc"`

Input theo byte ASCII:

```text
Input = 616263
SHA-256 =
ba7816bf8f01cfea414140de5dae2223
b00361a396177a9cb410ff61f20015ad
```

Kết quả là 32 byte (256 bit). Phép so sánh phải ghép hai dòng hex thành một chuỗi 64 ký tự, không ghép thêm dấu xuống dòng vào digest. SHA-256 không có secret key, IV hoặc provider-specific padding. Đây là test đơn giản để phát hiện sai byte encoding, sai thuật toán hoặc output khác chuẩn.

### 4.2. HMAC-SHA-256 — RFC 4231, Test Case 1

RFC 4231 quy định:

```text
Key (20 bytes): 0b lặp 20 lần
Data (ASCII):  "Hi There"
Data (hex):    4869205468657265
HMAC-SHA-256:
b0344c61d8db38535ca8afceaf0bf12b
881dc200c9833da726e9376c2e32cff7
```

HMAC phải được kiểm tra bằng **key và data bytes** đúng nguồn vector. Với JCA, service là `Mac`, tên chuẩn thông dụng là `HmacSHA256`; digest `SHA-256` độc lập **không thay thế được** HMAC. Một provider chỉ có `MessageDigest:SHA-256` chưa chắc có `Mac:HmacSHA256`.

### 4.3. AES-128-GCM — vector công khai của NIST

Trong ví dụ AES-GCM chuẩn:

```text
Key = 00000000000000000000000000000000    (16 bytes)
IV  = 000000000000000000000000            (12 bytes, 96 bits)
AAD = empty
PT  = 00000000000000000000000000000000    (16 bytes)
CT  = 0388dace60b6a392f328c2b971b2fe78
TAG = ab6e47d42cec13bdf53a67b21257bddf    (16 bytes, 128 bits)
```

Trong Java `AES/GCM/NoPadding`, với API `Cipher.doFinal(plaintext)`, output mã hóa gồm **ciphertext nối với authentication tag**: `CT || TAG`. Đây là 32 byte đối với vector trên. Khi giải mã, `Cipher.doFinal(CT || TAG)` xác minh tag trước khi trả về plaintext hoàn chỉnh.

Có thể bổ sung vector AES-GCM **plaintext rỗng** (cùng key/IV 0, AAD rỗng), trong đó CT rỗng và tag là `58e2fccefa7e3061367f1d57a4e7455a`. Test cả input rỗng giúp kiểm tra các góc cạnh mà một payload 16 byte không bao phủ. Những vector này là **dữ liệu xác minh thuật toán**, không phải mẫu key/nonce cho ứng dụng thật.

**Cảnh báo bắt buộc:** GCM yêu cầu IV/nonce **không lặp lại với cùng key trong quá trình mã hóa thực**. Việc chạy cùng public KAT nhiều lần chỉ dành cho môi trường kiểm thử cách ly. Không đưa key/IV toàn số 0 hoặc logic “fixed IV” vào nghiệp vụ hoặc production. Với dữ liệu thật, cần chiến lược tạo nonce duy nhất, theo dõi vòng đời key và giới hạn số lần mã hóa theo thiết kế.

### 4.4. Vì sao phải có cả negative vector?

Một provider có thể tính CT đúng ở happy path nhưng xử lý lỗi tag sai không đúng. Ít nhất kiểm tra:

1. Sửa một bit trong **tag** → giải mã phải từ chối.
2. Sửa một bit trong **ciphertext** → giải mã phải từ chối.
3. Thay đổi **AAD** khi nhận (nếu ban đầu có AAD) → phải từ chối.
4. Sai key hoặc IV, cắt cụt input → không được trả payload đã được coi là hợp lệ.
5. Với giao thức cho phép tag length có cấu hình, thử độ dài hợp lệ và bất hợp lệ; không âm thầm chấp nhận tag rút ngắn ngoài chính sách.

Trong Java, lỗi tag của AEAD thường biểu hiện thành `AEADBadTagException` khi `doFinal` giải mã. Từ phía giao thức, không được sử dụng hay gửi plaintext như dữ liệu đã xác thực trước khi tag được kiểm tra thành công; tránh stream kết quả trung gian chưa xác minh sang bước xử lý nhạy cảm.

## 5. Java JCA chọn provider như thế nào?

Một service được yêu cầu bằng engine class và tên thuật toán/transformation:

```java
MessageDigest sha = MessageDigest.getInstance("SHA-256");
Mac mac = Mac.getInstance("HmacSHA256");
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
```

Đây là **đoạn minh họa tích hợp**, không phải chương trình độc lập. Nếu không chỉ định provider, JCA dùng cơ chế chọn provider ưu tiên hỗ trợ service; thứ tự/chiến lược có thể chịu ảnh hưởng cấu hình JDK như `jdk.security.provider.preferred` và lúc khởi tạo provider. Không suy provider thực dùng chỉ từ phần tử đầu tiên của `Security.getProviders()`. Nên kiểm tra `cipher.getProvider()`, `mac.getProvider()` hoặc `sha.getProvider()` trên instance **đang dùng**, sau khi khởi tạo nếu cần, rồi ghi metadata phục vụ kiểm thử.

Để test một implementation cụ thể, có thể chỉ định provider tường minh:

```java
Provider p = /* provider đã được lấy từ Security.getProviders() */;
Cipher c = Cipher.getInstance("AES/GCM/NoPadding", p);
```

Đây là **đoạn minh họa**. Truyền `Provider` object vào `getInstance` giúp ép test trực tiếp implementation đó, tránh tự động chọn provider khác khi cái được nhắm tới thiếu thuật toán. Trong ứng dụng thông thường, Oracle khuyến nghị hạn chế hardcode tên như `"SunJCE"` vì có thể không tồn tại trên JDK khác và cản trở provider tối ưu hoặc hardware. Test compatibility có thể cần pin provider cố ý để kiểm chứng.

Các API dễ lẫn:

- `Security.getProviders()` trả provider đã đăng ký, **không phải** danh sách provider chắc chắn hỗ trợ một transformation cụ thể.
- `provider.getServices()`/`provider.getService("Cipher", "...")` giúp khám phá khai báo service, nhưng **đăng ký** không chứng minh có thể `init` với key/tag/nonce thực cần.
- `Cipher.getInstance("AES")` không chỉ định mode/padding; mặc định có thể khác nhau, thậm chí là ECB. **Luôn yêu cầu transformation đầy đủ**.
- `Cipher.getProvider()` phản ánh provider đã được chọn cho instance đó. Nếu cần xác định nơi thực thi, đọc sau bước khởi tạo và đối chiếu capability/key type.
- `NoSuchAlgorithmException`/`NoSuchPaddingException` nghĩa là service/transformation không có ở provider được chọn; khác với `InvalidKeyException`, `InvalidAlgorithmParameterException` hoặc lỗi token/HSM xảy ra **sau khi service tồn tại**.
- Alias không thống nhất giữa provider. Ưu tiên **Java Security Standard Algorithm Names** và tên chuẩn giao thức; ví dụ `SHA-256` và `HmacSHA256` trong JCA.

Khi provider được cấu hình động, ghi rõ JDK build, phiên bản library, thứ tự provider, cấu hình bảo mật và biến môi trường ảnh hưởng. Không tự động đánh dấu tất cả lỗi từ HSM thành “algorithm unsupported”.

## 6. Một chương trình Java 17+ tự chạy: KAT + kiểm tra provider

Ví dụ dưới đây minh họa trọn quy trình:

- KAT chuẩn SHA-256, HMAC-SHA-256 và AES-128-GCM.
- Giải mã AES-GCM vector công khai và **từ chối tag đã sửa**.
- Khám phá **từng provider đã đăng ký** có `AES/GCM/NoPadding`.
- Buộc từng provider chạy KAT **cùng một vector độc lập**.
- Nếu có từ **hai provider AES-GCM khả dụng**, chạy mọi cặp có hướng A → B và B → A với AAD, và kiểm tra AAD sai bị từ chối.
- Nếu chỉ có một provider, công bố **SKIP** cho cross-provider. Không gọi một vòng round-trip một-provider là “interop PASS”.

Tệp `CryptoKatInteropDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoKatInteropDemo {
    private static final HexFormat HEX = HexFormat.of();
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte[] KEY = HEX.parseHex(
            "00000000000000000000000000000000");
    private static final byte[] IV = HEX.parseHex(
            "000000000000000000000000");
    private static final byte[] PLAIN = HEX.parseHex(
            "00000000000000000000000000000000");
    private static final byte[] EXPECTED_GCM = HEX.parseHex(
            "0388dace60b6a392f328c2b971b2fe78"
          + "ab6e47d42cec13bdf53a67b21257bddf");

    private static void check(byte[] actual, byte[] expected, String name) {
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new AssertionError(name + ": got " + HEX.formatHex(actual));
        }
    }

    private static Cipher newCipher(Provider provider)
            throws GeneralSecurityException {
        return provider == null
                ? Cipher.getInstance(TRANSFORMATION)
                : Cipher.getInstance(TRANSFORMATION, provider);
    }

    private static byte[] encrypt(Provider provider, byte[] aad)
            throws GeneralSecurityException {
        Cipher c = newCipher(provider);
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                new GCMParameterSpec(128, IV));
        if (aad.length > 0) {
            c.updateAAD(aad);
        }
        return c.doFinal(PLAIN);
    }

    private static byte[] decrypt(Provider provider, byte[] input, byte[] aad)
            throws GeneralSecurityException {
        Cipher c = newCipher(provider);
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                new GCMParameterSpec(128, IV));
        if (aad.length > 0) {
            c.updateAAD(aad);
        }
        return c.doFinal(input);
    }

    private static void expectBadTag(Provider provider, byte[] input, byte[] aad)
            throws GeneralSecurityException {
        try {
            decrypt(provider, input, aad);
        } catch (AEADBadTagException expected) {
            return;
        }
        throw new AssertionError("Tampered GCM data was accepted");
    }

    public static void main(String[] args) throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(
                "abc".getBytes(StandardCharsets.US_ASCII));
        check(sha, HEX.parseHex(
                "ba7816bf8f01cfea414140de5dae2223"
              + "b00361a396177a9cb410ff61f20015ad"), "SHA-256 KAT");
        System.out.println("SHA-256 KAT: PASS");

        byte[] macKey = new byte[20];
        Arrays.fill(macKey, (byte) 0x0b);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        check(mac.doFinal("Hi There".getBytes(StandardCharsets.US_ASCII)),
                HEX.parseHex("b0344c61d8db38535ca8afceaf0bf12b"
                           + "881dc200c9833da726e9376c2e32cff7"), "HMAC KAT");
        System.out.println("HMAC-SHA-256 KAT: PASS");

        check(encrypt(null, new byte[0]), EXPECTED_GCM, "GCM KAT");
        check(decrypt(null, EXPECTED_GCM, new byte[0]), PLAIN, "GCM decrypt");
        System.out.println("AES-GCM KAT: PASS");

        byte[] corrupted = EXPECTED_GCM.clone();
        corrupted[corrupted.length - 1] ^= 1;
        expectBadTag(null, corrupted, new byte[0]);
        System.out.println("AES-GCM corrupted tag: REJECTED");

        List<Provider> capable = new ArrayList<>();
        for (Provider p : Security.getProviders()) {
            try {
                Cipher.getInstance(TRANSFORMATION, p);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException absent) {
                continue;
            }
            check(encrypt(p, new byte[0]), EXPECTED_GCM, p.getName() + " KAT");
            check(decrypt(p, EXPECTED_GCM, new byte[0]), PLAIN,
                    p.getName() + " decrypt");
            capable.add(p);
        }
        if (capable.isEmpty()) {
            throw new AssertionError("No AES/GCM provider can be tested");
        }
        System.out.println("AES-GCM provider KATs: PASS (" + capable.size()
                + " provider(s))");

        if (capable.size() < 2) {
            System.out.println("Cross-provider GCM: SKIP (need 2 providers)");
        } else {
            byte[] aad = "header-v1".getBytes(StandardCharsets.US_ASCII);
            byte[] wrongAad = "header-v2".getBytes(StandardCharsets.US_ASCII);
            int pairs = 0;
            for (Provider producer : capable) {
                byte[] sealed = encrypt(producer, aad);
                for (Provider consumer : capable) {
                    if (producer == consumer) {
                        continue;
                    }
                    check(decrypt(consumer, sealed, aad), PLAIN,
                            producer.getName() + " -> " + consumer.getName());
                    expectBadTag(consumer, sealed, wrongAad);
                    pairs++;
                }
            }
            System.out.println("Cross-provider GCM: PASS (" + pairs + " pairs)");
        }
    }
}
```

Chạy với JDK 17 trở lên (không cần thư viện ngoài JDK):

```bash
javac -encoding UTF-8 CryptoKatInteropDemo.java
java CryptoKatInteropDemo
```

**Ví dụ stdout đã kiểm thử trên một môi trường chỉ có một provider đáp ứng AES-GCM:**

```text
SHA-256 KAT: PASS
HMAC-SHA-256 KAT: PASS
AES-GCM KAT: PASS
AES-GCM corrupted tag: REJECTED
AES-GCM provider KATs: PASS (1 provider(s))
Cross-provider GCM: SKIP (need 2 providers)
```

Hai dòng cuối **phụ thuộc môi trường**. Nếu có ít nhất hai provider AES-GCM khả dụng và mọi cặp được kiểm tra thành công, dòng cuối có dạng `Cross-provider GCM: PASS (N pairs)` với `N = count * (count - 1)`. Nếu một provider quảng bá transformation nhưng từ chối khởi tạo key/IV bắt buộc, chương trình **thất bại** thay vì âm thầm tính đó là provider thành công. Nếu muốn test riêng một HSM có chính sách khóa không export được, phải dùng **harness và key fixture dành riêng cho HSM**; không hạ chuẩn yêu cầu bằng cách coi mọi lỗi import raw key là SKIP.

**Cách hiểu các hàm trong chương trình:**

- `check` so byte mong đợi với byte nhận được; cùng một expected từ chuẩn được dùng cho các provider, không lấy output của A để định nghĩa expected cho B.
- `newCipher(provider)` dùng provider mặc định khi tham số `null`, hoặc ép implementation cụ thể khi nhận `Provider`, giúp tránh báo sai provider.
- `encrypt/decrypt` khởi tạo instance riêng từng lần, đặt explicit `GCMParameterSpec(128, IV)` và cung cấp AAD **trước** `doFinal`. Public test key/IV chỉ dùng trong test.
- `expectBadTag` buộc dữ liệu bị sửa phải ném `AEADBadTagException`; nếu decrypt trả về mà không kiểm tra tag, test FAIL.
- Vòng lặp `capable` bỏ qua provider **không khai báo được** transformation nhưng vẫn **thử KAT trên provider đã khai báo**. Việc chỉ hỗ trợ factory method không đủ tính là PASS.
- Cross-provider kiểm tra cả **producer → consumer** và **sai AAD** cho mỗi cặp có hướng; chỉ kiểm tra đa provider thực sự khi môi trường đáp ứng điều kiện có từ hai provider.

Trong CI, nên log `provider.getName()`, `getVersionStr()` và tên JDK/OS cho từng kết quả, giới hạn log ở thông tin cấu hình và fixture công khai. Ví dụ cố tình chỉ in số lượng để stdout ngắn và không phụ thuộc tên provider.

## 7. Thế nào là một phép thử tương thích giữa provider đúng nghĩa?

**Không đủ** để nói “provider A và B đều có `Cipher.AES`”. Interop yêu cầu một bên tạo artifact mà bên khác tiêu thụ được, và cả hai thống nhất mọi tham số, wire format và key semantics.

### 7.1. Ma trận có hướng

Giả sử ba provider A, B, C đều hỗ trợ cùng profile (ví dụ AES-128-GCM, 96-bit IV, 128-bit tag):

| Producer → Consumer | A | B | C |
| --- | --- | --- | --- |
| **A** | Round-trip nội bộ | A→B | A→C |
| **B** | B→A | Round-trip nội bộ | B→C |
| **C** | C→A | C→B | Round-trip nội bộ |

Các cặp **A→B và B→A đều quan trọng**: khả năng tạo output, giải mã, chính sách key/parameter và behavior có thể bất đối xứng. Một bảng đầy đủ còn kiểm tra output có bằng vector chuẩn, tình huống AAD/tag sai và ranh giới độ dài. Không phải provider nào cũng có đủ capability; ma trận phải nêu **SUPPORTED / UNSUPPORTED / PASS / FAIL / SKIP** có nghĩa, không bỏ qua ô trống.

Nếu key nằm trong HSM và không export được, không cố copy raw key sang provider B. Đặt bài test theo mô hình hợp lệ: import public key được phép, cho provider A ký bằng key handle và provider B verify bằng public key, hoặc dùng fixture cùng key được provision độc lập ở từng nơi theo quy trình an toàn. Với symmetric key trong HSM, không thể mặc định A và B cùng truy cập secret.

### 7.2. Hợp đồng tương thích cần thống nhất

- **Thuật toán:** tên chuẩn và transformation đầy đủ; chẳng hạn `AES/GCM/NoPadding` chứ không phải `AES`.
- **Key semantics:** bit length; raw key, `SecretKeySpec`, PKCS#8 private key, X.509 SubjectPublicKeyInfo public key, hoặc handle không export được; key usages và attributes.
- **Parameters:** IV/nonce, AAD, tag length, RSA-OAEP digest/MGF, RSA-PSS digest/MGF/salt/trailer, curve name và encoding.
- **Wire format:** vị trí IV, ciphertext, authentication tag; framing, header version, byte order, Base64/hex, DER, PEM wrapper và serialization version.
- **Failure semantics:** malformed input hoặc authentication failure **phải bị từ chối**, không chỉ “throw được exception nào đó”.
- **Security policy:** provider được phép theo môi trường, FIPS mode (nếu có), thuật toán đã bị vô hiệu hóa, thời hạn key và tính tương thích khi nâng cấp.

Khi dữ liệu sản xuất được lưu hoặc truyền giữa service chạy JDK khác nhau, phải đưa **JDK/provider/version cũ → mới** vào ma trận. Test cùng JVM với hai provider là bước hữu ích nhưng chưa chứng minh tương thích qua REST/Kafka/file format hoặc với library được cấu hình khác.

## 8. Những điểm interop thường sai ngoài AES-GCM

### 8.1. RSA-OAEP: tên transformation không kể hết MGF1 parameters

`RSA/ECB/OAEPWithSHA-256AndMGF1Padding` cho biết một phần profile, nhưng **digest của MGF1 có thể phụ thuộc parameter/default provider**. Nếu muốn hai bên chắc chắn cùng profile, xây dựng `OAEPParameterSpec` tường minh và yêu cầu hai provider chấp nhận:

```java
OAEPParameterSpec spec = new OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT);
Cipher rsa = Cipher.getInstance(
        "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", provider);
rsa.init(Cipher.ENCRYPT_MODE, publicKey, spec);
```

Đây là **đoạn tích hợp, không độc lập**: cần import `javax.crypto.Cipher`, `javax.crypto.spec.OAEPParameterSpec`, `javax.crypto.spec.PSource`, `java.security.spec.MGF1ParameterSpec`, cùng `provider`/`publicKey` hợp lệ. Kiểm thử encrypt A → decrypt B bằng **private key tương ứng** và đổi chiều phù hợp với quyền sở hữu key. OAEP là cơ chế **có tính ngẫu nhiên**, nên không đòi hai lần encrypt cùng plaintext cho cùng ciphertext. Thay vào đó, kiểm tra khả năng giải mã, tham số, các vector tiêu chuẩn/nguồn độc lập khi có và negative cases.

### 8.2. RSA-PSS: phải chốt salt length và digest

Dùng `Signature.getInstance("RSASSA-PSS", provider)` với tham số:

```java
PSSParameterSpec spec = new PSSParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
Signature signer = Signature.getInstance("RSASSA-PSS", provider);
signer.setParameter(spec);
signer.initSign(privateKey);
signer.update(messageBytes);
byte[] signature = signer.sign();
```

Đây là **đoạn tích hợp**, cần import `java.security.Signature`, `java.security.spec.PSSParameterSpec`, `java.security.spec.MGF1ParameterSpec` và các biến khóa/message hợp lệ. Provider có thể cần thứ tự gọi/setParameter khác tùy SPI; kiểm chứng contract trên phiên bản thực tế. Salt ngẫu nhiên có thể khiến chữ ký khác byte giữa các lần ký dù đều đúng. Test **sign A → verify B** bằng public key phù hợp, và verify phải FAIL khi sửa message/signature hoặc sai PSS parameters.

### 8.3. ECDSA: DER và IEEE P1363 là hai dạng byte khác nhau

Một chữ ký ECDSA chứa hai số nguyên `(r, s)`. Một số API chuyển chúng thành cấu trúc ASN.1 DER `SEQUENCE(INTEGER r, INTEGER s)`; một số API/profile dùng fixed-width `r || s` (IEEE P1363). Cùng đường cong, khóa và thông điệp **không thể** giả định chuỗi byte signature giống nhau nếu format khác. Java có các standard names như `SHA256withECDSA` và, trên JDK hỗ trợ, `SHA256withECDSAinP1363Format`. Cần chốt format theo giao thức, kiểm thử vector tiêu chuẩn tương ứng và không giải mã “có vẻ hợp lệ” bằng parser tự viết thiếu kiểm tra độ dài/canonical encoding.

### 8.4. KeyStore, PKCS#11/HSM và TLS

- **KeyStore**: hai provider có thể hỗ trợ `PKCS12` nhưng khác default encryption, password handling, alias, certificate chain hoặc cấu hình. Test load/save/reload và khả năng đọc bởi consumer thực. Không lấy Java serialization của `PrivateKey` làm wire format.
- **PKCS#11/HSM**: `Key.getEncoded() == null` có thể là thiết kế đúng (key không export); API factory support chưa chứng minh quyền sử dụng key, mechanism, session hoặc token đang đăng nhập. Test trong môi trường key policy phù hợp, phân biệt `UNSUPPORTED`, `KEY_POLICY_DENIED`, `TOKEN_UNAVAILABLE` và `CRYPTO_MISMATCH`.
- **TLS/JSSE**: cùng algorithm không đồng nghĩa giao thức bắt tay tương thích. TLS còn phụ thuộc cipher suite, certificate chain, endpoint identification, protocol version, provider JSSE, security policy và keystore/truststore. Cần bài integration test riêng với client/server thực; đừng dùng kết quả AES-GCM KAT để tuyên bố TLS end-to-end đạt yêu cầu.

## 9. Thiết kế negative tests để phát hiện lỗi bảo mật

KAT positive chỉ kiểm tra input chuẩn. Security suite phải buộc implementation **từ chối đầu vào bị giả mạo hoặc sai chính sách**:

| Trường hợp | Hành vi mong đợi |
| --- | --- |
| AES-GCM tag đổi 1 bit | Không xuất plaintext được coi là xác thực; `AEADBadTagException` hoặc lỗi xác thực rõ ràng ở API tương ứng |
| AES-GCM ciphertext đổi 1 bit | Từ chối vì authentication không khớp |
| GCM AAD bị đổi hoặc bị bỏ | Từ chối khi tag được kiểm tra |
| Sai IV/nonce hoặc key khi decrypt | Không thể xác thực payload; từ chối |
| Cắt cụt ciphertext/tag, sai tag length | Lỗi dữ liệu/parameter, không tiếp tục dùng output |
| RSA-PSS sai message, public key, MGF digest hoặc salt length | Verify trả `false` hoặc ném lỗi phù hợp; không chấp nhận chữ ký |
| HMAC sai key/data hoặc MAC không đúng độ dài | Verify phải từ chối, không tự cắt chuỗi hoặc tự đổi encoding |
| Thuật toán/parameter bị chính sách cấm | Fail rõ ràng, không tự đổi về thuật toán yếu hơn |
| Hardware key không có quyền sign/decrypt | Báo lỗi quyền/key policy; không tự export hoặc tạo key thay thế |
| Provider/middleware mất kết nối giữa thao tác | Lỗi được báo và rollback/cleanup hợp lệ; không ghi trạng thái “verified” |

Nếu API dùng thao tác streaming, cần bảo đảm **không xử lý plaintext nhạy cảm trước khi tag/authentication được xác minh**. Kiểm thử thời điểm nhận lỗi, quy trình đóng tài nguyên, trạng thái sau exception và retry logic. Một provider có thể trả ngoại lệ cụ thể khác ở các lớp wrapper, nhưng **yêu cầu bảo mật quan trọng là không chấp nhận dữ liệu chưa xác thực**.

Các trường hợp biên nên gồm input rỗng, 1 byte, 15/16/17 byte, payload nhiều khối, AAD rỗng/không rỗng, tag tối đa và tối thiểu theo **policy**, input lớn và phân mảnh nhiều lần `update`. Với hash/MAC, test exact byte input ở nhiều độ dài và test reset/reuse theo hợp đồng API, tránh chia sẻ `Mac`/`Cipher` instance không an toàn giữa nhiều thread.

## 10. Ma trận CI và quy tắc PASS / FAIL / SKIP

Trong pipeline build, không phải mỗi agent đều có mọi provider, OS hoặc HSM. Vì vậy test report phải phân biệt:

| Trạng thái | Ý nghĩa | Hành động |
| --- | --- | --- |
| `PASS` | Đã chạy ca kiểm thử cần thiết và đúng expected | Ghi vector ID, provider, JDK/OS, số ca |
| `FAIL` | Output/behavior không đúng chuẩn hoặc policy | Chặn release theo độ bắt buộc; giữ log tối thiểu có khả năng tái hiện |
| `UNSUPPORTED` | Provider mục tiêu thực sự không hỗ trợ service/parameter theo contract | Nếu yêu cầu bắt buộc thì FAIL gate; nếu optional thì công bố capability |
| `SKIP` | Điều kiện môi trường cho ca test không có (ví dụ chỉ một provider, thiếu token kiểm thử) | Không tính là PASS; yêu cầu môi trường đủ điều kiện ở gate phù hợp |
| `ERROR` | Harness/setup/IO/token/JDK lỗi không phân loại được | Điều tra; không âm thầm chuyển thành PASS hoặc UNSUPPORTED |

Một ma trận triển khai có thể chia:

1. **Every commit:** SHA-256/HMAC/AES-GCM KAT trên provider mặc định, GCM negative tests, formatting/encoding tests. Thời gian chạy nhỏ và kết quả ổn định.
2. **Provider compatibility job:** kiểm tra KAT trên mỗi provider được hỗ trợ chính thức, sau đó mọi cặp có hướng; chọn rõ provider và lấy tên/version, không dựa vào thứ tự implicit.
3. **Runtime/JDK upgrade job:** chạy đồng thời với JDK cũ và JDK mới, thu metadata về provider, thực hiện đọc artifact do version trước tạo ra và ngược lại.
4. **HSM/FIPS integration job:** chạy trên môi trường có token/slot/key policy thực, dùng test key riêng do quản trị cấp và role hợp lệ; không đặt secret trong test logs.
5. **Deployment smoke test:** chạy một tập fixture nhỏ khi cấu hình provider hoặc crypto policy thay đổi, kiểm tra không fallback sang cấu hình yếu hơn.

### Tiêu chí gate đề xuất

- Nếu contract sản phẩm bắt buộc **AES-GCM provider X** thì `UNSUPPORTED` hoặc `ERROR` của X là **gate FAIL**, không phải bỏ qua.
- Nếu contract yêu cầu **khả năng tương thích A↔B**, kết quả `SKIP (chỉ có A)` **không đạt yêu cầu release**, dù KAT trên A PASS.
- Nếu một provider phụ là optional, việc `UNSUPPORTED` có thể không chặn build nhưng phải thể hiện rõ trong báo cáo capability và phạm vi hỗ trợ.
- Việc chuyển JDK/provider version cần baseline về thuật toán, parameter, output format và expected exceptions; không “accept new output” chỉ để test xanh.

**Ghi hồ sơ tối thiểu:** test ID, vector source/version/hash, engine/algorithm/transformation, provider name/version, JDK vendor/version, OS, thành phần FIPS/token (không chứa secrets), kết quả, trường hợp SKIP và lý do. Không cần log plaintext nhạy cảm hoặc khóa thực để có tính tái lập.

## 11. Quy trình chẩn đoán khi provider không tương thích

Khi một ca FAIL, đi theo trình tự cụ thể:

1. **Xác nhận input bytes:** dump *chỉ fixture công khai*, độ dài byte, encoding và digest của fixture; lỗi UTF-8/base64/hex rất phổ biến.
2. **Xác định implementation thực thi:** instance `getProvider()`, version, transformation canonical, config/JDK. Phân biệt provider mặc định khác với provider test ép chọn.
3. **Kiểm tra parameters:** nonce/tag length/AAD order với AEAD; digest/MGF/salt với OAEP/PSS; curve + signature format với ECDSA; key size/usage/format.
4. **Kiểm tra capability:** service có được đăng ký không, có instantiate được không, init chấp nhận key thật không, HSM có token/slot/login/usage hợp lệ không.
5. **So với expected độc lập:** nếu KAT trên một provider FAIL, **sửa lỗi tại đó trước** khi kết luận lỗi do interop. Nếu cả hai KAT PASS mà cross FAIL, kiểm tra **wire format và serialization**.
6. **Negative-case behavior:** dữ liệu sai có bị từ chối hay bị coi là thành công; phân loại mức nghiêm trọng cao nếu authentication failure bị bỏ qua.
7. **Tái hiện tối thiểu:** thu hẹp thành fixture byte có hash được ghi nhận, provider pin, parameter spec rõ, chạy lại trên môi trường triển khai; chỉ mở rộng logging khi an toàn.

Đừng sửa bằng cách hardcode “provider A tốt hơn”, lược bỏ AAD, đổi chiều tag, hạ thuật toán hoặc đổi sang `Cipher.getInstance("AES")` mà chưa xác định contract đúng. Những “cách sửa” này có thể biến một lỗi tương thích thành rủi ro bảo mật.

## 12. Lỗi phổ biến khi viết KAT và interop tests

1. **Tạo expected bằng chính code đang test:** kết quả tự khẳng định bản thân. Dùng nguồn công khai đã xác minh độc lập.
2. **Chỉ thử encrypt/decrypt cùng provider:** không phát hiện nhiều lỗi chung và chưa kiểm tra A→B.
3. **Bỏ qua AAD/nonce/tag length:** có thể cho false PASS với profile đơn giản, nhưng FAIL trong giao thức thật.
4. **Dựa vào default algorithm/padding:** mỗi provider có thể chọn mode/padding khác. Dùng transformation và parameter spec đầy đủ.
5. **Dùng fixed GCM IV trong production theo ví dụ KAT:** cực kỳ nguy hiểm khi tái sử dụng nonce cùng key. Vector số 0 chỉ dành cho test.
6. **Đánh đồng `getInstance` thành support đầy đủ:** key type/hardware policy/tag length có thể khiến `init` hoặc `doFinal` fail.
7. **Ép raw key cho HSM key không export:** phải test bằng handle hoặc đã provision key theo quy trình tương ứng.
8. **So byte ciphertext ngẫu nhiên:** RSA-OAEP, RSA-PSS hoặc ECDSA ngẫu nhiên có thể tạo byte khác dù hợp lệ; kiểm thử đúng invariant và tham số.
9. **Không phân biệt DER/P1363:** hai bên verify thất bại do signature encoding, không phải lỗi toán học.
10. **Ẩn `SKIP` bằng báo cáo tổng `PASS`:** dễ phát hành hệ thống chưa từng có bài test interop trên môi trường đạt điều kiện.
11. **Không kiểm tra tamper:** positive KAT thành công nhưng thao tác xác thực bị bypass trong đường xử lý.
12. **Thay expected sau upgrade mà không đối chiếu nguồn:** bỏ qua thay đổi thuật toán/serialization ngoài ý muốn.
13. **Lấy provider theo vị trí cố định:** cơ chế ưu tiên và cấu hình JDK thay đổi; test phải ghi nhận implementation thực.
14. **Chỉ test một OS/JDK:** provider native/hardware và alias, key store, TLS có thể khác hệ thống triển khai.

## 13. Bài tập tự kiểm tra

**A.** Hai lần mã hóa AES-GCM cùng plaintext, key và IV trong public KAT trả cùng ciphertext/tag. Điều đó có bình thường không? **Có trong vector test xác định**; nhưng cùng key/IV **không được tái sử dụng** để mã hóa các thông điệp sản xuất khác nhau. KAT không phải mẫu nonce management cho production.

**B.** Provider A mã hóa rồi tự giải mã thành công; kết quả có chứng minh A tuân chuẩn NIST GCM chưa? **Chưa.** Cần so với vector độc lập và kiểm tra tag/AAD fail cases.

**C.** Máy CI chỉ có một provider AES-GCM. Sau khi nó qua KAT, có thể ghi “cross-provider PASS” không? **Không.** Phải ghi `SKIP (need 2 providers)` và triển khai job có ít nhất hai implementation phù hợp nếu interop là yêu cầu.

**D.** Provider A và B đều có transformation RSA-OAEP-SHA-256 nhưng không giải mã chéo được. Nên xem trước yếu tố nào? **Digest của MGF1, OAEPParameterSpec, key pair, dữ liệu ciphertext/encoding**, sau đó kiểm tra policy/provider logs.

**E.** AES-GCM ciphertext/tags trùng expected, nhưng đổi AAD khi giải mã mà code vẫn báo thành công. Suite có đạt không? **Không.** Negative test bảo vệ authentication thất bại; đó là lỗi cần chặn.

**F.** HSM từ chối `SecretKeySpec` import, nhưng chấp nhận key handle nội bộ. Có phải AES-GCM hoàn toàn không được hỗ trợ? **Không thể kết luận.** Capability của hardware key và policy là phần riêng; tạo harness với key fixture hợp lệ.

## 14. Tài liệu chuẩn nên đối chiếu

- [NIST FIPS 180-4, Secure Hash Standard](https://csrc.nist.gov/pubs/fips/180-4/upd1/final): quy tắc SHA-256 và các vector hash tiêu chuẩn.
- [RFC 4231, HMAC-SHA test vectors](https://www.rfc-editor.org/rfc/rfc4231.html) — đặc biệt §4.2 Test Case 1, key 20 byte `0b` và data “Hi There”.
- [NIST SP 800-38D, GCM and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): thuật toán GCM, IV, GHASH/tag và các điều kiện bảo mật; dùng các test vector AES-GCM được công bố độc lập trong bộ kiểm thử NIST.
- [NIST AES-GCM public test vectors trong BoringSSL](https://boringssl.googlesource.com/boringssl/+/e8c862cf1421/crypto/cipher/test/aes_128_gcm_tests.txt): đối chiếu case key/nonce 0 và output `0388...||ab6e...`, gồm cả test input rỗng.
- [RFC 5116, Authenticated Encryption with Associated Data](https://www.rfc-editor.org/rfc/rfc5116.html): mô hình AEAD, AES-GCM profile, tag và điều kiện nonce.
- [Java SE 21: `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html), [`GCMParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html), [`Mac`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html), [`MessageDigest`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html): hợp đồng engine, parameter, AAD, authentication tag.
- [Oracle JCA Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html) và [JDK Providers](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html): provider selection, SPI, standard names, aliases và giới hạn từng implementation.
- [Java SE 21: `OAEPParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html), [`PSSParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/PSSParameterSpec.html), [`Security`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Security.html) và [`Provider`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Provider.html): cách pin tham số và tìm implementation thực.

**Điểm cốt lõi:** KAT chứng minh **output của một vector chuẩn khớp từng byte**; interop chứng minh **artifact do provider A tạo được provider B tiêu thụ đúng hợp đồng**, kèm kiểm thử đổi chiều và tamper. Chỉ khi kết hợp vector độc lập, tham số tường minh, negative cases và một môi trường thật sự có nhiều provider, báo cáo kiểm thử mới có giá trị để ra quyết định nâng cấp hoặc triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-006">22.6 Sửa một byte của ciphertext, tag, AAD hoặc chữ ký để kiểm tra đường từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# 22.6. Sửa một byte của ciphertext, tag, AAD hoặc chữ ký để kiểm tra đường từ chối

## 1. Mục tiêu

Đây là bài **negative testing** cho mật mã và đường xử lý dữ liệu đã được bảo vệ. Sau khi học, bạn cần thực hiện được các việc sau:

1. Phân biệt **ciphertext**, **authentication tag**, **AAD (Additional Authenticated Data)**, **IV/nonce**, **chữ ký số** và **dữ liệu được ký**.
2. Tạo bản dữ liệu hợp lệ, kiểm tra đường chấp nhận trước rồi mới cố ý thay **đúng một byte** của từng trường.
3. Dùng Java 17+ kiểm thử AES-GCM từ chối khi sửa ciphertext, tag hoặc AAD thông qua `AEADBadTagException`.
4. Dùng `Signature.verify` kiểm thử việc sửa một byte trong chữ ký hoặc thông điệp đã ký.
5. Xác minh ứng dụng **không trả plaintext chưa xác thực, không ghi database, không phát event, không cập nhật trạng thái** khi test bị từ chối.
6. Phân biệt dữ liệu bị giả mạo với lỗi parser, key/provider/hạ tầng và các lỗi cấu hình khiến test không còn có ý nghĩa.

Mục tiêu là chứng minh **hệ thống từ chối dữ liệu không còn hợp lệ** qua một phép thử tái lập được. Ví dụ trong bài tạo dữ liệu và khóa thử nghiệm tại chỗ; không dùng secret production, không phá dữ liệu của người khác và không tự triển khai thuật toán mật mã mới.

## 2. Sơ đồ những byte nào được xác thực

Ví dụ AES-GCM:

~~~text
                    ENCRYPTION
key K -----------┐
nonce/IV N ------|
plaintext P -----|--> AES-GCM ----> ciphertext C || authentication tag T
AAD A -----------┘

                    DECRYPTION
K + N + A + C + T ---> kiểm tra tag ---> trả plaintext P nếu tag đúng
                                     \
                                      ---> từ chối nếu tag sai
~~~

- **P (plaintext)** là dữ liệu chưa mã hóa; ứng dụng chỉ được sử dụng khi đã qua xác thực.
- **C (ciphertext)** là dữ liệu được mã hóa. Thay một bit ciphertext nhưng giữ tag gốc sẽ khiến kiểm tra tính toàn vẹn thất bại với xác suất áp đảo.
- **T (tag)** là kết quả xác thực của AEAD. Với ví dụ chọn `GCMParameterSpec(128, iv)`, tag dài 128 bit, tức **16 byte**.
- **A (AAD)** là metadata không mã hóa nhưng được xác thực cùng bản mã; ví dụ tenant ID, version hoặc routing context. Byte AAD khi giải mã phải đúng byte đã đưa vào tính tag khi mã hóa.
- **N (nonce/IV)** là tham số của lần mã hóa. Với GCM, dùng lại cùng nonce khi mã hóa bằng cùng key có thể gây hậu quả bảo mật nghiêm trọng. Ví dụ chỉ mã hóa một thông điệp với một key mới và nonce ngẫu nhiên 12 byte.

JCE AES-GCM có thể trả về `ciphertext || tag` trong cùng mảng byte:

~~~text
sealed = [C0 C1 ... Cn] [T0 T1 ... T15]
         ^              ^
         byte ciphertext vùng tag 16 byte cuối
~~~

Trong ví dụ, plaintext khác rỗng nên `sealed[0]` chắc chắn thuộc ciphertext, còn `sealed[sealed.length - 1]` thuộc tag. Ở giao thức khác, các trường có thể tách riêng hoặc có header đứng trước; **phải xác nhận layout trước khi sửa**.

**Chữ ký số khác AEAD:** bên ký dùng private key và chính các byte của thông điệp để tạo signature. Bên nhận dùng public key để verify; chữ ký không tự động giữ bí mật dữ liệu. Nếu metadata phải có tính toàn vẹn, ứng dụng phải đưa nó vào nội dung được ký theo giao thức đã định.

## 3. Nguyên tắc “baseline hợp lệ trước, sửa đúng một byte sau”

Một test có `decrypt` thất bại không tự chứng minh code chống giả mạo hoạt động. Nó có thể thất bại vì provider không tồn tại, key sai, IV sai hoặc file đã hỏng từ đầu. Vì vậy một nhóm test đúng phải có:

1. **Baseline dương tính:** encrypt bằng key/nonce/AAD phù hợp rồi decrypt ra đúng plaintext; ký dữ liệu rồi verify trả `true`.
2. **Bản sao không làm hỏng baseline:** dùng `clone()` trên mảng cần sửa; mỗi test xuất phát từ bản gốc đã xác thực.
3. **Một biến thay đổi:** lật một bit trong **một byte** của một trường; giữ nguyên key, IV, thuật toán và các trường không được test.
4. **Đường gọi thật:** chạy lại chính `Cipher.doFinal` hoặc `Signature.verify` mà production sử dụng.
5. **Assertion đảo chiều:** hệ thống chấp nhận payload đã sửa phải khiến test **FAIL** rõ ràng.
6. **Kiểm tra sau xác thực:** khi tích hợp lên tầng ứng dụng, kiểm chứng không có side effect và không công bố plaintext trước khi xác thực xong.

Có thể thay một byte bằng:

~~~java
byte[] mutated = original.clone();
mutated[index] ^= 0x01;
~~~

`0x01` đảo **một bit** ở byte chỉ định, vì vậy mảng đổi đúng một byte, không đổi độ dài. Không dùng XOR với 0, không sửa trực tiếp mảng gốc. Khi test cần ghi bằng chứng, kiểm tra có đúng một vị trí `mutated[i] != original[i]`.

**Quan trọng:** phải sửa **sau khi** đã tạo tag hoặc chữ ký. Nếu sửa plaintext rồi **mã hóa/ký lại bằng key hợp lệ**, bạn đã tạo một thông điệp mới hợp lệ; đó không còn là bài kiểm thử đường từ chối giả mạo.

## 4. Test matrix bắt buộc

| Test | Phần giữ nguyên | Phần sửa đúng một byte | Kỳ vọng |
| --- | --- | --- | --- |
| AES-GCM baseline | Tất cả | Không sửa | Giải mã ra plaintext gốc |
| AES-GCM ciphertext | Key, nonce, AAD, tag | Byte trong ciphertext | `AEADBadTagException`, không chấp nhận plaintext |
| AES-GCM tag | Key, nonce, AAD, ciphertext | Byte trong tag | `AEADBadTagException` |
| AES-GCM AAD | Key, nonce, ciphertext, tag | Byte trong AAD | `AEADBadTagException` |
| Signature baseline | Public key, signed message, signature | Không sửa | `verify == true` |
| Signature bytes | Public key, signed message | Byte trong signature | `verify == false` hoặc từ chối signature sai encoding |
| Signed message | Public key, signature | Byte trong message | `verify == false` |

Đối với AEAD, sửa ciphertext, tag hoặc AAD thường đều dẫn tới **cùng một lỗi tag mismatch**, không thể suy từ ngoại lệ rằng chắc chắn lỗi phát sinh ở trường nào. Với chữ ký, `verify` có thể trả `false` hoặc ném `SignatureException` cho chữ ký sai định dạng/kiểu; đừng gom hết mọi `GeneralSecurityException` thành “tamper được phát hiện”.

## 5. Vì sao sửa ciphertext, tag và AAD đều phải bị từ chối?

AES-GCM thuộc nhóm AEAD, bảo đảm cả **confidentiality** (bí mật dữ liệu) lẫn **integrity/authenticity** (tính toàn vẹn và xác thực) theo giả định dùng khóa/nonce đúng. Tag được tính từ key, nonce, ciphertext và AAD:

~~~text
K,N,C,A -----> GCM authentication -----> T
K,N,C',A ----> authentication khác -----> T' (khác T với xác suất áp đảo)
K,N,C,A' ----> authentication khác -----> T' (khác T với xác suất áp đảo)
~~~

**Sửa ciphertext:** receiver có `C'` nhưng vẫn có `T` của `C`. Bước verify thất bại. Ngay cả khi một số cơ chế streaming tạo bytes tạm trước khi kiểm tra tag, ứng dụng **không được dùng bytes đó** cho đến khi xác thực hoàn tất.

**Sửa tag:** ciphertext không thay đổi, nhưng tag nhận được `T'` không còn khớp kết quả tự tính. Không phải cứ sửa tag là “không decrypt được” do ciphertext hỏng; đây là kiểm tra tính xác thực của toàn bộ thông điệp.

**Sửa AAD:** dữ liệu metadata bên ngoài ciphertext thay đổi; receiver dùng `A'` để kiểm tra `T` sẽ thất bại. Đó là lý do cần đưa vào AAD những thông tin mà hệ thống muốn ràng buộc: tenant, phiên bản, loại record, context do hệ thống tin cậy xác định.

Trong JCE, gọi `updateAAD(aad)` **trước** khi truyền ciphertext vào `update` hoặc `doFinal`. `Cipher.doFinal` ở chế độ GCM xác minh authentication tag và khi sai có thể ném `AEADBadTagException`, subclass của `BadPaddingException`; GCM `NoPadding` không biến lỗi này thành lỗi padding thông thường.

Không nên viết test đòi “mọi sự cố đều là `AEADBadTagException`”: thiếu key, tag field không hợp lệ về chiều dài, provider lỗi hoặc cấu hình thuật toán sai có thể đi qua ngoại lệ khác và cần được phân loại đúng.

## 6. Vì sao sửa chữ ký hoặc dữ liệu được ký phải bị từ chối?

API Java:

~~~java
Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(publicKey);
verifier.update(messageBytes);
boolean valid = verifier.verify(signatureBytes);
~~~

Đây là **đoạn minh họa**, cần khai báo `publicKey`, `messageBytes` và `signatureBytes` trong ngữ cảnh thực tế.

- Nếu cùng message bytes, public key và signature hợp lệ, `valid == true`.
- Nếu sửa **một byte trong chữ ký** nhưng giữ public key/message, chữ ký không còn khớp: `verify` thường trả `false`. Nếu encoding của chữ ký bị hỏng, một số provider/thuật toán có thể ném `SignatureException`.
- Nếu giữ chữ ký hợp lệ nhưng sửa **một byte trong message**, hash/đầu vào verify thay đổi; `verify` không còn khớp.
- Không dùng private key để kiểm chứng; không lấy “decode base64 thành công” làm tiêu chí chấp nhận chữ ký.

Cần định nghĩa chính xác bytes được ký. Một payload JSON có thể có nhiều biểu diễn byte khác nhau sau serialize; nếu ứng dụng ký raw bytes, việc canonicalize khác trước verify sẽ làm sai chữ ký dù thông tin nghiệp vụ trông giống nhau. Nếu muốn bảo vệ header/tenant/route bằng signature, hãy bảo đảm chúng thuộc **message được ký** theo chuẩn giao thức.

Ví dụ sử dụng `SHA256withRSA` và khóa RSA 2048 bit để chạy độc lập với JDK tiêu chuẩn; bài không tuyên bố đây là lựa chọn chữ ký tối ưu cho mọi hệ thống. Với RSA-PSS, Ed25519 hoặc ECDSA, nguyên tắc test vẫn giống nhau nhưng chi tiết mã hóa chữ ký và exception cần theo provider/chuẩn cụ thể.

## 7. Ví dụ Java 17+ đầy đủ: tự tạo dữ liệu và kiểm tra từ chối

Tạo file **TamperRejectDemo.java**. Chương trình dùng AES-GCM với key mới, nonce mới, tag 128 bit và AAD có ý nghĩa, sau đó tạo keypair RSA để thử chữ ký. Không cần HSM, DB, network hay thư viện bổ sung.

~~~java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class TamperRejectDemo {
    private static final int TAG_BITS = 128;
    private static final byte[] MESSAGE =
            "amount=100".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD =
            "tenant=blue;v=1".getBytes(StandardCharsets.UTF_8);

    private static byte[] aesGcm(
            int mode, SecretKey key, byte[] iv,
            byte[] aad, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static boolean gcmRejects(
            SecretKey key, byte[] iv,
            byte[] aad, byte[] sealed) throws GeneralSecurityException {
        try {
            aesGcm(Cipher.DECRYPT_MODE, key, iv, aad, sealed);
            return false;
        } catch (AEADBadTagException expected) {
            return true;
        }
    }

    private static boolean verify(
            PublicKey key, byte[] message,
            byte[] signatureBytes) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(signatureBytes);
    }

    private static void requireReject(String label, boolean rejected) {
        if (!rejected) {
            throw new AssertionError(label + " was accepted");
        }
        System.out.println(label + ": REJECTED");
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator aesKeys = KeyGenerator.getInstance("AES");
        aesKeys.init(128);
        SecretKey key = aesKeys.generateKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        byte[] sealed = aesGcm(Cipher.ENCRYPT_MODE, key, iv, AAD, MESSAGE);
        byte[] clear = aesGcm(Cipher.DECRYPT_MODE, key, iv, AAD, sealed);
        if (!Arrays.equals(clear, MESSAGE)) {
            throw new AssertionError("GCM baseline failed");
        }
        System.out.println("GCM baseline: ACCEPTED");

        byte[] badCiphertext = sealed.clone();
        badCiphertext[0] ^= 1;
        requireReject("ciphertext byte",
                gcmRejects(key, iv, AAD, badCiphertext));

        byte[] badTag = sealed.clone();
        badTag[badTag.length - 1] ^= 1;
        requireReject("tag byte",
                gcmRejects(key, iv, AAD, badTag));

        byte[] badAad = AAD.clone();
        badAad[0] ^= 1;
        requireReject("AAD byte",
                gcmRejects(key, iv, badAad, sealed));

        KeyPairGenerator rsaKeys = KeyPairGenerator.getInstance("RSA");
        rsaKeys.initialize(2048);
        KeyPair pair = rsaKeys.generateKeyPair();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(MESSAGE);
        byte[] signatureBytes = signer.sign();
        if (!verify(pair.getPublic(), MESSAGE, signatureBytes)) {
            throw new AssertionError("signature baseline failed");
        }
        System.out.println("signature baseline: ACCEPTED");

        byte[] badSignature = signatureBytes.clone();
        badSignature[badSignature.length - 1] ^= 1;
        requireReject("signature byte",
                !verify(pair.getPublic(), MESSAGE, badSignature));

        byte[] badSignedMessage = MESSAGE.clone();
        badSignedMessage[0] ^= 1;
        requireReject("signed message byte",
                !verify(pair.getPublic(), badSignedMessage, signatureBytes));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 --release 17 TamperRejectDemo.java
java TamperRejectDemo
~~~

**Stdout dự kiến (đã kiểm tra thực thi với JDK):**

~~~text
GCM baseline: ACCEPTED
ciphertext byte: REJECTED
tag byte: REJECTED
AAD byte: REJECTED
signature baseline: ACCEPTED
signature byte: REJECTED
signed message byte: REJECTED
~~~

Khóa AES, nonce và keypair RSA được sinh mới nên **ciphertext/signature bytes thay đổi mỗi lần chạy**. Nhưng trạng thái baseline và các phép thử bị từ chối phải như trên. Nếu một phép thử bị sửa mà vẫn được chấp nhận, `requireReject` ném `AssertionError`, báo test thất bại.

Mẫu `gcmRejects` chỉ bắt `AEADBadTagException`. Ngoại lệ khác được truyền lên làm test thất bại; đây là chủ ý để không đánh dấu lỗi môi trường là “phát hiện tamper”. Ví dụ dùng `verify` với chữ ký RSA độ dài không đổi, nơi byte bị sửa thường dẫn đến `false`; khi dùng thuật toán/provider khác phải thêm xử lý trường hợp encoding bị hỏng.

## 8. Giải thích từng ca kiểm thử trong chương trình

### 8.1. Ciphertext bị sửa

~~~java
byte[] badCiphertext = sealed.clone();
badCiphertext[0] ^= 1;
requireReject("ciphertext byte",
        gcmRejects(key, iv, AAD, badCiphertext));
~~~

Chỉ đổi một bit đầu ciphertext. Tag vẫn là tag của ciphertext cũ; key/IV/AAD vẫn như baseline. `doFinal` không thể chấp nhận dữ liệu sai xác thực trong trường hợp bình thường. Test sẽ ném `AssertionError` nếu bất ngờ trả plaintext.

### 8.2. Tag bị sửa

~~~java
byte[] badTag = sealed.clone();
badTag[badTag.length - 1] ^= 1;
requireReject("tag byte",
        gcmRejects(key, iv, AAD, badTag));
~~~

Vì JCE trả `ciphertext || tag` và tag 16 byte, phần tử cuối thuộc tag. Không sửa ciphertext. Tag truyền đến receiver khác tag được tính lại từ key/IV/AAD/ciphertext.

### 8.3. AAD bị sửa

~~~java
byte[] badAad = AAD.clone();
badAad[0] ^= 1;
requireReject("AAD byte",
        gcmRejects(key, iv, badAad, sealed));
~~~

Giữ `sealed` nguyên và đưa AAD khác vào decryption. Điều này kiểm tra rằng metadata được đưa vào tag, không chỉ là trường ngoài để log. Nếu đường sản xuất không dùng `updateAAD` đúng cách, test tương ứng có thể phơi bày thiếu ràng buộc metadata.

### 8.4. Chữ ký bị sửa

~~~java
byte[] badSignature = signatureBytes.clone();
badSignature[badSignature.length - 1] ^= 1;
requireReject("signature byte",
        !verify(pair.getPublic(), MESSAGE, badSignature));
~~~

Giữ message và public key. Không dùng private key để ký lại. Nếu chữ ký đã hỏng nhưng verify vẫn true, đây là lỗi cần điều tra.

### 8.5. Dữ liệu được ký bị sửa

~~~java
byte[] badSignedMessage = MESSAGE.clone();
badSignedMessage[0] ^= 1;
requireReject("signed message byte",
        !verify(pair.getPublic(), badSignedMessage, signatureBytes));
~~~

Giữ signature gốc, đổi một bit message. Kết quả verify phải false. Đây là ca bổ sung quan trọng: bảo vệ integrity không chỉ dành riêng cho mảng chữ ký.

## 9. Phân loại kết quả: rejection, malformed và lỗi môi trường

| Kết quả quan sát | Ví dụ | Kết luận đúng |
| --- | --- | --- |
| Baseline thành công | Decrypt trả đúng plaintext, signature verify true | Điều kiện cần để negative test đáng tin |
| Tamper được từ chối | `AEADBadTagException` cho ciphertext/tag/AAD đã sửa | PASS nếu không có side effect |
| Chữ ký không khớp | `verify` trả `false` sau khi sửa | PASS nếu message/verify path đúng |
| Chữ ký sai encoding | `SignatureException` do input signature đã bị sửa | Từ chối an toàn nếu test rõ trường hợp này; xác minh không phải lỗi provider |
| Parser từ chối trước crypto | Base64 không hợp lệ, thiếu field | PASS cho test format, **không đủ bằng chứng** cho test cryptographic tamper |
| Lỗi môi trường | KeyStore thiếu, `NoSuchAlgorithmException`, HSM mất session | FAIL/INCONCLUSIVE; sửa fixture/thiết lập trước |
| Payload bị sửa nhưng vẫn được dùng | Decrypt trả plaintext hoặc verify true và xử lý nghiệp vụ | SECURITY FAIL |
| Ứng dụng nuốt ngoại lệ | Tag sai nhưng API trả thành công hoặc phát plaintext trước lỗi | SECURITY FAIL |

Không dùng `catch (Exception e) { return true; }` trong helper kiểm tra tamper. Nó sẽ làm test xanh giả khi mất file key, chọn sai transformation, hết quyền HSM hoặc gặp bug không liên quan.

Tag mismatch cũng có thể do sai key, IV, AAD, ciphertext hoặc tag. Nếu chỉ nhìn `AEADBadTagException` mà không giữ baseline và biến thử nghiệm cố định, bạn không biết chính xác trường nào đã gây lỗi.

## 10. Từ phép thử mật mã sang đường từ chối của ứng dụng

Một thư viện crypto từ chối đúng chưa đủ nếu dịch vụ đã thực thi business logic trước khi hoàn tất xác thực. Luồng lý tưởng:

~~~text
Request / message / encrypted record
        |
        v
Kiểm tra kích thước và cú pháp envelope
        |
        v
Lấy key/nonce/AAD và ciphertext/tag đúng protocol
        |
        v
Verify tag hoặc chữ ký trên đủ bytes cần xác thực
        |
        +-- thất bại --> trả lỗi an toàn, không side effect
        |
        +-- thành công --> parse plaintext và áp policy/authorization
                              |
                              v
                         commit / publish / response
~~~

Trong API, các assert nên kiểm tra **cả kết quả và tác dụng phụ**:

- Response không có plaintext chưa xác thực hoặc thông tin nội bộ về key/tag.
- Không tạo/đổi record, không trừ tiền, không cấp quyền, không publish event khi token/record bị sửa.
- Không đưa thông điệp vào hàng đợi “xử lý thành công”, không ack như một request hợp lệ nếu hợp đồng yêu cầu reject.
- Sau ca âm tính, bản baseline vẫn chạy được; tránh giữ `Cipher` ở trạng thái không hợp lệ rồi reuse.
- Nếu cần audit, log mã lỗi đã chuẩn hóa và request correlation ID, không dump secret/nonce/key/plaintext production.

Mã HTTP tùy API có thể khác nhau theo thiết kế (400, 401, 403 hoặc 422). Bài không áp đặt một mã chung. Điểm bắt buộc là không xử lý dữ liệu giả mạo và không biến crypto failure thành success.

## 11. Không được công bố plaintext trước khi xác minh tag

Một lỗi thiết kế:

~~~text
cipher.update(...) -> plaintext tạm
   -> parse JSON -> ghi database / phát event
cipher.doFinal(...) -> AEADBadTagException
~~~

Ngay cả khi API cuối cùng trả lỗi, tác dụng phụ đã xảy ra. Cần ưu tiên:

~~~text
Nhận đủ ciphertext + tag + AAD
   -> xác minh và giải mã hoàn chỉnh
   -> chỉ khi thành công mới đưa plaintext vào business layer
   -> chỉ sau đó mới ghi state, gửi event hoặc phát response
~~~

Nếu giải mã streaming trên dữ liệu lớn, phải có ranh giới buffer/commit để không phát plaintext ra ngoài trước khi xác thực toàn bộ. API JCE và provider có thể khác nhau về lượng bytes trả ra khi gọi `update`, nên không được dựa vào sự may mắn của một provider.

**Lưu ý riêng của JDK:** `CipherInputStream` có thể nuốt `BadPaddingException` và các ngoại lệ kiểm tra tính toàn vẹn; tài liệu Oracle cảnh báo nó có thể không phù hợp để giải mã authenticated mode như GCM. Khi cần chắc chắn báo lỗi tag và không xử lý dữ liệu lỗi, dùng trực tiếp `Cipher` và quản lý hoàn tất `doFinal` trước khi commit.

## 12. Byte boundary của envelope, JSON và base64

Giả sử hệ thống lưu:

~~~text
version=1
nonce=<base64>
ciphertext=<base64>
tag=<base64>
tenantId=blue
signature=<base64>
~~~

Đây là cấu trúc minh họa, không phải payload có thể dùng trực tiếp. Khi test trên REST/DB/broker:

1. Tạo message hợp lệ từ **đường tạo dữ liệu thật**.
2. Clone toàn bộ envelope; xác định trường test sẽ sửa.
3. Decode base64 sang byte[]; đổi **đúng một byte** ở mảng đó.
4. Encode lại base64 hợp lệ và giữ các trường khác không đổi.
5. Gửi qua cùng API/consumer/reader; kiểm tra rejection và side effects.

Nếu chỉ sửa một ký tự base64 tùy tiện, test có thể bị parser từ chối trước khi tới `Cipher.doFinal`. Điều đó vẫn là input validation, nhưng không thể kết luận tag verification đã được chạy.

Nếu nội dung được ký sử dụng canonical JSON hoặc thông điệp dạng nhị phân có schema, chỉ sửa byte nào nằm trong phần thực sự được ký. Đổi whitespace rồi được canonicalize về dữ liệu cũ không còn là phép thử thay đổi bytes sau canonicalization.

Nếu envelope ghép `nonce || ciphertext || tag`, chỉ số mảng tổng không giống `sealed` trong chương trình trên. Phải ghi rõ offset và chiều dài từng phần trước khi mutate; tag có thể không luôn dài 16 byte nếu protocol chọn một độ dài khác.

## 13. Kết nối test với JUnit/CI

Trong project dùng JUnit 5, có thể biểu diễn nguyên tắc bằng:

~~~java
assertThrows(AEADBadTagException.class,
        () -> aesGcm(Cipher.DECRYPT_MODE, key, iv, AAD, badTag));

assertFalse(verify(publicKey, signedMessage, badSignature));
~~~

**Đây chỉ là đoạn minh họa**, chưa phải file Java độc lập: cần thư viện JUnit 5, import Assertions, fixture key và các helper được cung cấp trong phạm vi test. Hàm trong `TamperRejectDemo` hiện là `private`; khi chuyển sang test class, đặt helper ở đúng package/phạm vi hoặc viết test trực tiếp.

Một test tích hợp có thể được mô tả ngắn:

~~~text
given: payload hợp lệ đã được xử lý qua baseline
when:  clone và lật đúng một bit của một byte ở trường X
then:  request/record bị từ chối bởi đường xác thực
and:   không có side effect nghiệp vụ
and:   không tiết lộ plaintext/secret
and:   metric ghi nhận rejection phù hợp
~~~

Tên test rõ ràng:

~~~text
gcm_validRecord_isAccepted
gcm_changedCiphertext_isRejected
gcm_changedTag_isRejected
gcm_changedAad_isRejected
signature_changedSignature_isRejected
signature_changedMessage_isRejected
~~~

Nên chạy test trên JDK/provider mục tiêu và trên backend thật ở môi trường thử nghiệm khi dùng HSM/PKCS#11. Unit test mock `Cipher` hoặc mock `Signature` chỉ chứng minh logic test giả lập, chưa xác minh cơ chế crypto thực.

## 14. Các lỗi test phổ biến

**Không clone:** dùng `sealed[0] ^= 1` trực tiếp rồi test tag tiếp, khiến “tag case” thực ra có hai trường hỏng. Đây không còn là phép thử một byte.

**Sửa sai field:** một envelope có header và nonce ở đầu; `packet[0]` không phải ciphertext. Kiểm tra layout thay vì đoán offset.

**Mã hóa/ký lại:** mutate plaintext rồi encrypt lại, hoặc mutate message rồi sign lại bằng private key sẽ tạo dữ liệu mới hợp lệ. Muốn test từ chối, phải dùng **tag hoặc chữ ký gốc**.

**Nuốt mọi exception:** thiếu provider hoặc key sai cũng bị đánh dấu PASS. Chỉ catch đúng lỗi dự kiến cho case crypto.

**Nhận plaintext trước xác thực:** một số đường streaming có thể trả bytes tạm. Không parse/commit trước khi tag đã được xác minh.

**Dựa riêng vào HTTP status:** API trả 4xx nhưng DB đã bị ghi; test vẫn phải FAIL nếu tác dụng phụ trái phép xảy ra.

**AAD không được ràng buộc:** metadata trong request bị sửa nhưng code decrypt dùng AAD hard-coded hoặc metadata gốc khác; test tưởng đã thử AAD nhưng thực tế chưa cung cấp AAD sửa vào crypto.

**Mất tính phân biệt parser/crypto:** sửa một ký tự JSON hoặc base64 làm request invalid, không chứng minh service reject được tag sai nhưng định dạng hợp lệ.

**So sánh chuỗi thay bytes:** encoding UTF-8, normalization và serializing có thể thay nhiều byte. Khi muốn “một byte”, làm việc trên byte[] đã xác định.

**Giả định GCM chống replay:** GCM không tự phát hiện một record hợp lệ bị phát lại nguyên vẹn. Cần test replay riêng bằng sequence number, timestamp, nonce registry hoặc policy giao thức.

**Tái sử dụng nonce cùng key:** mỗi phép **mã hóa** GCM cần nonce duy nhất cho key; trong demo chỉ có một lần mã hóa, còn nhiều lần **giải mã** cùng bản thử để kiểm tra rejection không tạo vi phạm này.

## 15. Debug khi test báo kết quả đáng ngờ

| Triệu chứng | Cách khoanh vùng |
| --- | --- |
| Baseline GCM thất bại | Kiểm tra key, nonce, tag size, thứ tự `updateAAD`, byte layout, provider |
| Sửa ciphertext mà vẫn chấp nhận | Kiểm tra đúng byte ciphertext đã đổi sau encrypt; có bỏ tag/`doFinal` hoặc fallback không |
| Sửa tag mà không từ chối | Xác định 16-byte tag thật, offset và đối tượng sealed đã mutate có được dùng không |
| Sửa AAD mà vẫn chấp nhận | Kiểm tra `updateAAD` ở cả encrypt/decrypt, nguồn AAD và canonicalization |
| Chữ ký sai nhưng verify true | Kiểm tra byte[] chữ ký thực đưa vào verify, public key, message và mock/fallback |
| Chữ ký sai ném `SignatureException` | Xác định có do encoding bị phá hay lỗi provider/state; phân loại tường minh |
| Ca nào cũng từ chối | Dừng kết luận; sửa baseline/key/config trước |
| API trả lỗi nhưng DB đổi | Lỗi thứ tự xử lý/transaction; kiểm tra “verify trước, commit sau” |
| Test xanh nhưng không thấy log verify | Có thể test chỉ dừng ở parser; thêm instrumentation để chứng minh đến crypto boundary |

Đối với hệ thống vendor/HSM, một số lỗi runtime có thể dùng ngoại lệ riêng. Đừng gộp “token unavailable” với “chữ ký sai”. Dữ liệu QA nên ghi tên ca, thuật toán, provider/JDK, vị trí byte được mutate, kết quả baseline, loại rejection và có/không side effect. Không dump key, secret, dữ liệu production hoặc stacktrace công khai.

## 16. Tình huống thực tế

### A. Tenant được xác thực qua AAD

Một hệ thống lưu ciphertext của tenant Blue với AAD `tenant=blue;v=1`. Người dùng thay field thành `tenant=red;v=1` nhưng giữ ciphertext/tag. Nếu ứng dụng tự xây AAD từ tenant đã được xác thực là Red khi giải mã, tag phải fail. Nếu code bỏ qua tenant khi tính AAD, bạn có thể không có sự ràng buộc metadata như mong muốn. Cần test đúng context request, không lấy tenant từ một trường do attacker kiểm soát làm chân lý duy nhất.

### B. Chữ ký của webhook

Webhook có body và signature. Test giữ signature gốc, lật một bit body sau khi ký. Consumer phải từ chối trước khi tạo order hoặc emit message. Nếu consumer parse JSON rồi đổi canonicalization, cần xác định chính xác bytes mà sender và receiver ký/verify theo giao thức webhook.

### C. Encrypted record bị sửa ở storage

Tạo record AES-GCM hợp lệ, chụp bản sao, sửa một byte ciphertext lưu trong fixture, giữ nonce/tag/AAD. Reader của application phải từ chối trước khi dựng domain object hoặc ghi cache như dữ liệu hợp lệ. Không sửa dữ liệu thật đang được các hệ khác sử dụng.

### D. Chữ ký phần cứng

Nếu verify qua HSM/provider, cần chạy cùng test chữ ký gốc và chữ ký sửa một byte, nhưng phải phân biệt `false` hoặc rejected signature với lỗi mất session/key handle. Thất bại do HSM offline không chứng minh tamper detection hoạt động.

## 17. Bài tập mở rộng

1. Với `TamperRejectDemo.java`, lật bit tại vị trí giữa ciphertext thay vì byte đầu; giải thích vì sao vẫn phải bị từ chối.
2. Lật một bit ở byte đầu thay vì byte cuối của tag; xác minh rejection và giải thích cách xác định biên tag.
3. Sửa AAD để đổi một byte trong tên tenant; giải thích tại sao AAD tuy có thể nhìn thấy rõ nhưng vẫn cần tính toàn vẹn.
4. Giữ `signatureBytes`, sửa một byte trong `MESSAGE`, dự đoán `verify`. Sau đó giải thích vì sao ký lại message đã sửa cho kết quả khác hẳn.
5. Tạo fixture API ở môi trường thử nghiệm: sau khi thay một byte ciphertext, kiểm tra status, database transaction, event count, response body và metric rejection.
6. Thử một payload có base64 sai cú pháp và một payload base64 hợp lệ nhưng ciphertext đã bị sửa; phân biệt **format-rejection** với **authentication-rejection**.
7. Tìm trong code ứng dụng mọi điểm xử lý trước `Cipher.doFinal` và thiết kế assertion cho việc không cấp plaintext/quyền trước tag verification.

## 18. Checklist đánh giá hoàn thành

- [ ] Baseline của AES-GCM **ACCEPTED** và plaintext bằng bytes ban đầu.
- [ ] Baseline chữ ký **ACCEPTED** qua `Signature.verify`.
- [ ] Các case ciphertext, tag, AAD, signature và signed message được thực hiện riêng.
- [ ] Mỗi case clone dữ liệu gốc, sửa một byte (ít nhất một bit) và không đổi chiều dài.
- [ ] Không ký/mã hóa lại sau khi mutation.
- [ ] Bắt đúng exception dự kiến; lỗi cấu hình/runtime vẫn làm test thất bại.
- [ ] Nếu dữ liệu đã sửa được chấp nhận, test **FAIL** thay vì tiếp tục.
- [ ] Integration test quan sát không có plaintext/side effect khi bị từ chối.
- [ ] Đã xác minh offset theo layout thật và dữ liệu sau decode/serialize.
- [ ] Log/response không tiết lộ secret; baseline vẫn chạy sau các negative case.
- [ ] Test chạy được trên Java 17+ và provider mục tiêu.

## 19. Tài liệu đối chiếu

- Java SE 21 `Cipher`: mô tả AES-GCM, yêu cầu `updateAAD` trước dữ liệu, tính duy nhất của nonce và kiểm tra tag: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
- Java SE 21 `GCMParameterSpec`: tag length tính bằng bit, IV bytes: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
- Java SE 21 `AEADBadTagException`: lỗi xác thực tag: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/AEADBadTagException.html
- Java SE 21 `Signature`: `sign`, `verify`, `SignatureException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html
- Java SE 21 `CipherInputStream`: cảnh báo có thể nuốt lỗi integrity ở authenticated mode: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/CipherInputStream.html

**Kết quả cần đạt:** có bằng chứng rõ ràng rằng bản tin hợp lệ đi qua, còn khi cố ý thay đúng một byte trong ciphertext, tag, AAD hay chữ ký thì **xác thực bị từ chối và nghiệp vụ không xử lý dữ liệu giả mạo**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-007">22.7 Kiểm thử chứng thư hết hạn, sai hostname, thiếu intermediate và CA không tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 22.7. Kiểm thử chứng thư hết hạn, sai hostname, thiếu intermediate và CA không tin cậy

## Mục tiêu

Kết thúc bài này, bạn có thể thiết kế một bộ **negative test TLS/X.509** trong Java 17+ để chứng minh ứng dụng **từ chối kết nối** khi chứng thư máy chủ hết hạn, tên máy chủ không khớp, máy chủ gửi thiếu chứng thư intermediate hoặc chuỗi chứng thư dẫn đến CA không được tin cậy. Bạn sẽ biết cách cô lập nguyên nhân, tạo fixture trong PKI thử nghiệm, chạy Java `HttpClient` và OpenSSL, đọc lỗi JSSE/PKIX, kiểm tra cả đường thành công và phòng ngừa các “cách sửa” nguy hiểm như bỏ qua xác minh chứng thư.

**Tiêu chí thành công của bài kiểm thử:** chứng thư hợp lệ phải đi qua TLS; mỗi chứng thư lỗi phải bị **từ chối tại tầng TLS/certificate validation**, trước khi client chấp nhận dữ liệu ứng dụng. Một request trả HTTP 401/403/500 **không** chứng minh TLS hỏng: nếu đã nhận HTTP status thì handshake có thể đã hoàn tất thành công.

Đây là bài thuộc **22. Vận hành và kiểm thử** trong Java security/cryptography. Các ví dụ dùng CA và server **riêng cho phòng thử nghiệm**. Không dùng private key sản xuất, không thay `cacerts` của máy thật và không thay đổi đồng hồ của hệ điều hành để làm test.

## 1. Mô hình xác minh chứng thư TLS cần hiểu trước khi viết test

Máy chủ TLS thông thường đưa cho client một chứng thư cuối (leaf/end-entity) và một hoặc nhiều intermediate. Client xây dựng, kiểm tra **đường dẫn chứng thực (certification path)** dẫn về một **trust anchor** mà nó đã tin cậy, rồi kiểm tra danh tính endpoint mà nó định kết nối.

```text
        Truststore của Java client (đã cho phép)
                      Root CA A
                         |
                  ký Intermediate A
                         |
                 ký leaf cho API
             SAN = DNS:api.lab.test
                         ^
                         |
   TLS server gửi leaf + Intermediate A
                         |
         Client kết nối https://api.lab.test:8443
                         |
       1. Xác minh chữ ký và chuỗi CA hợp lệ
       2. Kiểm tra hiệu lực thời gian của chứng thư
       3. Kiểm tra điều kiện CA, key usage, EKU, policy
       4. So khớp identity của endpoint với SAN
       5. Chỉ sau đó mới giao tiếp HTTP qua TLS
```

**Root CA** được cấu hình sẵn trong **truststore phía client**; thông thường server không cần gửi root. **Intermediate** là chứng thư CA nằm giữa leaf và root; trong bài test thiếu intermediate, máy chủ bỏ intermediate khi gửi chain nhưng phía client chỉ tin root và không có cách tìm intermediate từ nguồn khác. Cần phân biệt “thiếu intermediate” với “client không tin root”.

### Các kiểm tra độc lập nhưng liên quan

| Khía cạnh | Cần đúng | Nếu sai |
| --- | --- | --- |
| **Validity** | Thời điểm xác minh nằm trong `notBefore`–`notAfter` của các chứng thư cần kiểm tra | Chứng thư hết hạn hoặc chưa có hiệu lực |
| **Certificate path / PKIX** | Chữ ký nối được đến trust anchor, basic constraints, key usage, các quy tắc thuật toán và policy phù hợp | `PKIX path building failed` hoặc `path validation failed` |
| **Hostname / endpoint identity** | Tên host mà ứng dụng muốn gọi khớp identity hợp lệ trong `subjectAltName` (SAN) | `No subject alternative DNS name matching ...` hoặc lỗi tương đương |
| **Trust anchor** | CA cuối của chuỗi là một anchor có trong tập CA **được client tin** | CA tự ký/CA khác không được tin |
| **Purpose** | Chứng thư dùng đúng vai trò TLS server, constraints phù hợp | Bị từ chối vì EKU, key usage hoặc constraints |
| **Revocation, nếu policy bật** | Không bị thu hồi theo phương án OCSP/CRL đã chọn | Lỗi khác bốn trường hợp chính của bài |

Theo RFC 5280, kiểm tra `notBefore/notAfter` và đường dẫn PKIX là việc riêng với xác minh **identity của dịch vụ TLS**. RFC 9525 đặt tên dịch vụ trong SAN, không nên dựa vào Common Name (CN) làm identity. Quy tắc thực tế của thư viện và phiên bản Java có thể khác; với test này hãy phát hành **SAN tường minh** và xác minh hành vi của đúng client đang chạy.

Một handshake có thể thất bại vì **nhiều nguyên nhân đồng thời**. Nếu bạn cố thử “hostname sai” trên một chứng thư cũng đã hết hạn và do CA lạ ký, kết quả “TLS bị từ chối” không chứng minh hostname verification đang hoạt động. Mỗi ca cần giữ mọi điều kiện khác hợp lệ.

## 2. Case 1 — Chứng thư hết hạn hoặc chưa đến ngày có hiệu lực

Leaf X.509 chứa hai mốc UTC: `notBefore` và `notAfter`. Client mặc định dùng thời điểm hiện tại để kiểm tra hiệu lực; `notAfter` ở quá khứ dẫn tới chứng thư hết hạn. `notBefore` ở tương lai là **not yet valid**, một lỗi riêng nên test bổ sung. Lệch thời gian client/server/KDC hoặc máy chạy CI cũng có thể làm chứng thư hợp lệ bị đánh giá sai; test phải ghi lại mốc kiểm tra ở UTC.

Ví dụ fixture:

```text
Root CA A (trusted, còn hạn)
  -> Intermediate A (còn hạn)
     -> leaf "api.lab.test"
         SAN = DNS:api.lab.test
         notBefore = 2020-01-01T00:00:00Z
         notAfter  = 2020-01-02T00:00:00Z
         server gửi leaf + intermediate đầy đủ
```

Kiểm thử năm 2026 với truststore chứa Root A phải bị từ chối do thời hạn leaf, **không** do CA lạ, SAN hay thiếu intermediate. Tránh dùng chứng thư tự ký hết hạn làm trust anchor rồi kết luận client phải luôn từ chối: nhiều validator xử lý **trust anchor** khác với leaf/intermediate, và đây không phải negative test thời hạn leaf đáng tin cậy.

Kiểm tra metadata độc lập bằng OpenSSL:

```bash
openssl x509 -in expired-leaf.pem -noout \
  -subject -issuer -dates -ext subjectAltName
```

Cần thấy SAN đúng, issuer đúng và `notAfter` đã qua. Lệnh này **chỉ đọc metadata**, chưa chứng minh client kiểm tra thời hạn. Trong Java, `X509Certificate.checkValidity()` kiểm tra thời hạn hiện tại, còn `checkValidity(Date)` dùng thời điểm truyền vào. Cả hai chỉ xử lý **thời gian của chứng thư đó**, không xác minh signature chain, hostname hay trust anchor.

Để thử các mốc ranh giới mà không sửa đồng hồ máy, unit test offline có thể dùng `X509Certificate.checkValidity(Date)` và `PKIXParameters.setDate(Date)` trong bài kiểm tra đường dẫn PKIX. **Handshake TLS thông thường vẫn dùng đồng hồ và trust configuration thật của runtime**; không coi offline test là bằng chứng thay thế integration test.

## 3. Case 2 — Sai hostname / Subject Alternative Name

Client kết nối tới `https://api.lab.test`, nhưng server trình chứng thư hợp lệ cho `other.lab.test` (chỉ có `SAN DNS:other.lab.test`). Chuỗi CA có thể hoàn toàn hợp lệ, ngày tháng và EKU có thể đúng; **danh tính đích không khớp** nên client phải từ chối.

```text
Client URL host:        api.lab.test
Server leaf SAN:        DNS:other.lab.test
Truststore:             Root CA A (issuer hợp lệ)
Validity:               còn hạn
Server chain:           đầy đủ
Kết quả mong đợi:       TLS REJECT (endpoint identity)
```

Các điểm cần biết:

- Tên tham chiếu lấy từ **hostname đích mà ứng dụng có chủ ý kết nối**, không phải tự động lấy từ trường `Subject CN` của chứng thư. RFC 9525 ưu tiên SAN; khả năng fallback CN ở một số implementation cũ không phải thiết kế mới nên dựa vào.
- Nếu URL dùng IP như `https://127.0.0.1`, muốn khớp IP cần `iPAddress SAN` phù hợp; `DNS:localhost` không tự động khớp địa chỉ IP. Để thử sai hostname, **giữ URL host là `api.lab.test`**, chỉ thay SAN leaf.
- `SNI (Server Name Indication)` giúp server chọn chứng thư trong lúc TLS handshake. **SNI khác với hostname verification**: gửi SNI đúng không có nghĩa client đã xác minh SAN. Dùng hostname đích nhất quán để server trả đúng fixture.
- Với wildcard, `*.example.test` theo quy tắc chuẩn hiện đại chỉ dùng cho **một label bên trái** (ví dụ `a.example.test`), không khớp `a.b.example.test`. Trong test chính, dùng SAN cụ thể để tránh tranh cãi về provider.
- Một chứng thư có SAN DNS cho `other.lab.test` nhưng CN cho `api.lab.test` vẫn nên được coi là sai theo kiểm tra SAN; đây là ca kiểm tra tốt để phát hiện client/logic tự viết dựa sai vào CN.

**Java chuẩn:** `HttpsURLConnection` và `java.net.http.HttpClient` cấu hình HTTPS endpoint identity verification mặc định; không tắt khi chạy negative test. Nếu tự dùng `SSLSocket`/`SSLEngine`, việc xác minh hostname có thể cần đặt `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` **trước handshake**, theo protocol cần thiết. Chỉ tạo `SSLContext` có trust manager tốt **không tự bảo đảm** mọi kiểu socket tự viết đã kiểm tra hostname.

Ví dụ fragment cho `SSLSocket` (phụ thuộc socket đã tạo với host phù hợp, không phải chương trình độc lập):

```java
SSLSocket socket = /* tạo từ SSLSocketFactory với hostname đích */;
SSLParameters parameters = socket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(parameters);  // trước startHandshake()
socket.startHandshake();             // phải lỗi nếu SAN không khớp
```

Đừng “sửa” lỗi bằng `HostnameVerifier` luôn trả `true` hoặc bằng cách tắt endpoint identification. Đó là biến một kết nối tới máy chủ không đúng danh tính thành “thành công giả”.

## 4. Case 3 — Thiếu intermediate trong chuỗi do máy chủ gửi

Giả sử Root A ký Intermediate A, và Intermediate A ký leaf cho `api.lab.test`. Client chỉ trust Root A, không lưu Intermediate A; máy chủ lại chỉ gửi **leaf**, không gửi Intermediate A. Đường dẫn **leaf → Root A** không nối được chữ ký, do đó path builder không xây dựng được chain hợp lệ.

```text
Đúng: server gửi [ leaf, Intermediate A ]
      client có [ Root A ] -> path xây dựng được

Sai:  server gửi [ leaf ]  (thiếu Intermediate A)
      client có [ Root A ] -> không đủ mắt xích
```

Kiểm tra này có một bẫy vận hành quan trọng: path builder **có thể tìm intermediate ở nơi khác**, tùy provider/cấu hình, ví dụ intermediate đã có trong local store, cache, hay tải qua Authority Information Access (AIA). Nếu vậy, server gửi thiếu intermediate nhưng client vẫn kết nối được. **Không kết luận “thiếu intermediate là an toàn” từ một lần Java/browser kết nối thành công**; cần kiểm tra chính xác wire chain và cấu hình client.

Để ca negative test có kết quả xác định:

1. Test server chỉ phát leaf, không chèn intermediate từ cấu hình chain của reverse proxy, load balancer hoặc TLS termination.
2. Truststore riêng của Java **chỉ có Root A**, không có Intermediate A.
3. Leaf không có AIA `caIssuers` có thể dùng để lấy Intermediate A, hoặc môi trường thử nghiệm cô lập khả năng lấy AIA và cache; ghi rõ điều kiện thực tế của client/provider.
4. DNS/SNI/SAN, validity, EKU và root trust phải hợp lệ.
5. Chạy cả baseline có intermediate và ca thiếu intermediate trên **cùng client/JDK**; nếu baseline thành công còn ca thiếu fail, mới phân lập được nguyên nhân.

**Không coi việc server không gửi root là lỗi:** client phải có trust anchor riêng. Phần lớn cấu hình HTTPS chuẩn gửi leaf + intermediate(s) nhưng không gửi root; root có mặt trên server (hoặc gửi kèm) cũng **không tự làm client tin nó**.

Một thông báo `PKIX path building failed: unable to find valid certification path to requested target` có thể đến từ **thiếu intermediate** *hoặc* **CA không tin cậy**. Vì thế phải xem chain server đã phát và truststore phía client thay vì chỉ so khớp chuỗi lỗi.

## 5. Case 4 — CA không tin cậy

Máy chủ có thể gửi **đầy đủ** leaf + intermediate của **Root CA B**, nhưng Java client được cấu hình chỉ tin **Root CA A**. Dù hostname, validity và chữ ký nội bộ đều đúng, không có trust anchor được phép cho chain B nên handshake phải thất bại.

```text
Server: leaf api.lab.test -> Intermediate B -> Root B
Client truststore: Root A
Kết quả: REJECT

Server: leaf api.lab.test -> Intermediate A -> Root A
Client truststore: Root A
Kết quả: ACCEPT (nếu mọi điều kiện khác đúng)
```

Điểm phân biệt với thiếu intermediate: ở case CA không tin cậy, **chuỗi do server gửi đã đầy đủ các intermediate cần thiết**, nhưng root tương ứng **không nằm trong danh sách tin cậy của client**. Trường hợp root A bị thay bằng root B trong truststore là thay đổi policy, không phải “bổ sung intermediate”.

Đừng import leaf của server không rõ nguồn hoặc thêm CA lạ vào `cacerts` chỉ để hết lỗi. Nếu hệ thống thực sự dùng CA riêng, hãy xác minh fingerprint và nguồn CA qua kênh độc lập, cập nhật truststore theo change control, giới hạn phạm vi trust và chạy lại kiểm tra hostname/thời hạn/chuỗi.

Cần lưu ý truststore có thể khác nhau giữa:

- JDK được ứng dụng dùng và JDK của shell chạy `keytool`.
- Container image, máy developer, máy CI, máy chủ thực.
- Java `javax.net.ssl.trustStore`/`SSLContext` tùy biến và truststore của hệ điều hành hoặc trình duyệt.
- Từng service/tenant nếu ứng dụng tạo `SSLContext` riêng.

Vì thế câu “Chrome mở được” không chứng minh Java service của bạn có cùng CA hay cùng cơ chế lấy intermediate.

## 6. Ma trận fixture: thay **đúng một điều kiện** cho mỗi ca

Tạo CA thử nghiệm, **không dùng khóa private của production**. Dùng hai root độc lập (A và B), hai intermediate tương ứng, và nhiều leaf; cấu hình nhiều cổng TLS cùng chạy trong mạng lab.

| ID | Endpoint test | Leaf time | SAN leaf | Server gửi | Client tin | Kỳ vọng |
| --- | --- | --- | --- | --- | --- | --- |
| **OK** | `api.lab.test:8443` | Còn hạn | `DNS:api.lab.test` | Leaf A + Inter A | Root A | **ACCEPT** |
| **EXP** | `api.lab.test:8444` | **Hết hạn** | `DNS:api.lab.test` | Leaf A + Inter A | Root A | **REJECT** |
| **NAME** | `api.lab.test:8445` | Còn hạn | **`DNS:other.lab.test`** | Leaf A + Inter A | Root A | **REJECT** |
| **CHAIN** | `api.lab.test:8446` | Còn hạn | `DNS:api.lab.test` | **Chỉ leaf A** | Root A (không Inter A) | **REJECT** khi không lấy intermediate khác |
| **ROOT** | `api.lab.test:8447` | Còn hạn | `DNS:api.lab.test` | Leaf B + Inter B | **Chỉ Root A** | **REJECT** |
| **FUTURE** (bổ sung) | `api.lab.test:8448` | **Chưa có hiệu lực** | `DNS:api.lab.test` | Leaf A + Inter A | Root A | **REJECT** |

Các cổng là **ví dụ**, không mặc định có dịch vụ thật. Cho `api.lab.test` phân giải tới localhost hoặc IP lab bằng DNS/hosts của môi trường kiểm thử; client phải dùng URL chứa tên đó, không âm thầm thay URL thành `127.0.0.1`. Mọi fixture phải hỗ trợ TLS hiện đại tương thích client và không mắc lỗi khác như private key không khớp, chain dùng thuật toán bị chặn hay thiếu EKU `serverAuth`; nếu không bạn không thể cô lập một lỗi duy nhất.

### Tối thiểu cần có trong bộ fixture

1. **Root A** và **Root B** với `basicConstraints CA:TRUE` và key usage ký chứng thư; private key được lưu riêng, không đặt trên server leaf.
2. **Intermediate A/B** được root tương ứng ký, có `CA:TRUE`, key usage cho CA, validity chứa thời điểm test.
3. **Leaf hợp lệ** với `CA:FALSE`, SAN, key usage và EKU thích hợp với TLS server; validity không quá ngắn đến mức CI chạy trễ là hết hạn ngoài ý muốn.
4. **Leaf expired** được Intermediate A ký, chỉ khác validity (có `notAfter` trong quá khứ).
5. **Leaf wrong-SAN** được Intermediate A ký, chỉ khác SAN so với baseline.
6. **Chain thiếu intermediate** dùng **đúng leaf hợp lệ** của baseline, nhưng server thay cấu hình gửi chain để bỏ Inter A.
7. **Untrusted-root** là chuỗi hợp lệ dưới Root B, trong khi client chỉ trust Root A.
8. **Truststore riêng** `lab-root-a.p12` chỉ chứa certificate của Root A; không nhét Inter A/B, root B hay leaf negative vào.

Cố định dữ liệu fixture trong repository test an toàn **chỉ nếu** khóa thử nghiệm không có giá trị ngoài test và có chính sách rõ ràng. Ưu tiên phát hành tự động bằng test CA trong CI, private key tồn tại ngắn hạn, không đưa private key có khả năng ký cho production domain vào git. Gắn metadata fixture (SAN, issuer, serial, thời hạn, expected result) để người khác nhận ra test đang xác minh gì.

### Thời gian và phát hành chứng thư expired

Để tạo chứng thư đã hết hạn, dùng test CA hỗ trợ đặt `notBefore`/`notAfter` rõ ràng, ví dụ chức năng CA của OpenSSL với `-startdate` và `-enddate` hoặc PKI test tool tương ứng. **Không coi `-days 1` là cách tạo chứng thư expired ngay**; nó thường tạo chứng thư còn hạn trong một ngày. Kiểm tra thời gian **sau khi phát hành** bằng `openssl x509 -dates`.

Để test ranh giới hiệu lực ở một thời điểm cố định mà không đổi thời gian toàn máy, dùng unit test `PKIXParameters.setDate` và `X509Certificate.checkValidity(Date)` trên fixture. Ca TLS runtime riêng phải dùng chứng thư thực sự expired tại thời điểm CI và theo đồng hồ máy chạy.

## 7. Kiểm tra chain và hostname bằng OpenSSL / keytool

Đây là cách **chẩn đoán độc lập** với Java. Không dùng các lệnh để bật bỏ qua xác minh trong ứng dụng.

### 7.1. Xem chính xác chứng thư server **đã gửi**

```bash
openssl s_client \
  -connect api.lab.test:8446 \
  -servername api.lab.test \
  -showcerts < /dev/null
```

`-showcerts` hiển thị danh sách server đưa qua wire; đó **không** phải đường dẫn đã được xác minh. Đếm và xem issuer/subject/SAN của từng chứng thư: case `CHAIN` phải chỉ gửi leaf. Nếu server gửi thêm Intermediate A do proxy tự chèn, fixture không còn kiểm tra “missing intermediate”.

**Bẫy:** `openssl s_client` mặc định có thể tiếp tục handshake khi certificate verification thất bại. Không lấy “đã in ra handshake” hoặc “có dòng SSL-Session” làm PASS. Dùng `-verify_return_error`, `-verify_hostname` và CA file rõ ràng khi cần biến verification failure thành lỗi có thể kiểm tra được.

### 7.2. Kiểm tra path + tên đích với trust anchor xác định

```bash
openssl s_client \
  -connect api.lab.test:8443 \
  -servername api.lab.test \
  -verify_hostname api.lab.test \
  -verify_return_error \
  -CAfile root-a.pem < /dev/null
```

Thay port lần lượt `8444`, `8445`, `8446`, `8447` để quan sát bốn ca. `-servername` chọn virtual host/chứng thư, còn `-verify_hostname` là **yêu cầu riêng** kiểm tra endpoint identity. `-CAfile` chỉ định root tin cậy của lab. Ghi lại certificate subject/issuer, validation error và verify return code; kiểm tra exit status theo OpenSSL version trong CI thay vì so khớp chính xác một dòng tiếng Anh.

Kiểm tra file chain offline khi có đủ PEM:

```bash
# Đường dẫn tốt từ leaf đến Root A qua Intermediate A
openssl verify \
  -CAfile root-a.pem \
  -untrusted intermediate-a.pem \
  -verify_hostname api.lab.test \
  good-leaf.pem

# Bỏ intermediate khỏi tập chứng thư ứng viên:
# trong lab chỉ còn root-a.pem và good-leaf.pem
openssl verify \
  -CAfile root-a.pem \
  -verify_hostname api.lab.test \
  good-leaf.pem
```

Lệnh thứ nhất sẽ xác minh thành công nếu fixture và constraints đúng. Lệnh thứ hai dự kiến lỗi trong lab vì thiếu issuer intermediate; đừng đồng nhất bài kiểm tra file offline này với việc xác nhận server có gửi intermediate hay không. Chỉ `s_client -showcerts` tại đúng endpoint mới cho bằng chứng về chain server phát.

### 7.3. Xem SAN, validity và truststore

```bash
openssl x509 -in good-leaf.pem \
  -noout -subject -issuer -dates -ext subjectAltName

keytool -list -v \
  -keystore lab-root-a.p12 \
  -storetype PKCS12 \
  -storepass changeit
```

Mật khẩu `changeit` chỉ dành cho truststore **lab** minh họa; không để mật khẩu thật trong script command line, log hoặc repo. `keytool` phải thuộc đúng Java runtime hoặc version phù hợp với ứng dụng khi kiểm tra behavior. Ghi rõ alias và fingerprint Root A; `keytool -list` có thể hiện certificate đã import nhưng không chứng minh app đang **thực sự sử dụng** file đó.

Nếu chưa có truststore test chứa Root A, tạo riêng (không sửa `cacerts`):

```bash
keytool -importcert -noprompt \
  -alias lab-root-a \
  -file root-a.pem \
  -keystore lab-root-a.p12 \
  -storetype PKCS12 \
  -storepass changeit
```

Chỉ import sau khi xác minh `root-a.pem` là CA thử nghiệm chủ ý của bạn. Nếu truststore đã có alias/entry thì lệnh có thể từ chối import, hãy kiểm tra và quản lý fixture thay vì ghi đè ngẫu nhiên.

## 8. Chương trình Java 17+ chạy được: xác nhận TLS accept/reject

Chương trình dưới đây là **probe tích hợp**: tải truststore PKCS12 test, khởi tạo `TrustManagerFactory` chuẩn, tạo `SSLContext` và `HttpClient` chuẩn. Không sử dụng `trust-all`, không override hostname verifier, không bỏ certificate checking. Nó tạo HTTP GET với timeout và **không tự theo redirect** để test đúng endpoint.

**Điều kiện chạy:** phải dựng trước server fixture ở đúng URL, cấu hình DNS/hosts lab và `lab-root-a.p12`. Nếu server không tồn tại, code **báo lỗi kết nối**, không đánh dấu PASS cho negative test.

Tệp `TlsCertificateProbe.java`:

```java
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

public final class TlsCertificateProbe {
    private enum Expectation { ACCEPT, REJECT }

    private static SSLContext context(Path truststore, char[] password)
            throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(truststore)) {
            store.load(in, password);
        }

        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), new SecureRandom());
        return context;
    }

    // Chỉ xem lỗi liên quan TLS, không coi connection-refused là test PASS.
    private static boolean hasTlsCause(Throwable error) {
        for (Throwable cause = error; cause != null;
                cause = cause.getCause()) {
            if (cause instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println(
                    "Usage: java TlsCertificateProbe ACCEPT|REJECT "
                    + "<truststore.p12> <test-password> <https-url>");
            System.exit(2);
        }

        Expectation expected = Expectation.valueOf(
                args[0].toUpperCase(Locale.ROOT));
        URI uri = URI.create(args[3]);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null) {
            throw new IllegalArgumentException("HTTPS URL required");
        }

        HttpClient client = HttpClient.newBuilder()
                .sslContext(context(Path.of(args[1]),
                        args[2].toCharArray()))
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = client.send(
                    request, HttpResponse.BodyHandlers.discarding());
            if (expected == Expectation.REJECT) {
                throw new AssertionError(
                        "Expected TLS rejection; got HTTP "
                        + response.statusCode());
            }
            System.out.println("PASS ACCEPT");
        } catch (IOException error) {
            if (!hasTlsCause(error)) {
                throw error; // DNS, TCP, timeout... không phải TLS reject.
            }
            if (expected == Expectation.ACCEPT) {
                throw new AssertionError(
                        "Unexpected TLS rejection", error);
            }
            System.out.println("PASS REJECT");
        }
    }
}
```

Biên dịch:

```bash
javac TlsCertificateProbe.java
```

Lệnh dùng với **các endpoint lab đã cấu hình đúng như ma trận**:

```bash
java TlsCertificateProbe ACCEPT lab-root-a.p12 changeit \
  https://api.lab.test:8443/ping

java TlsCertificateProbe REJECT lab-root-a.p12 changeit \
  https://api.lab.test:8444/ping

java TlsCertificateProbe REJECT lab-root-a.p12 changeit \
  https://api.lab.test:8445/ping

java TlsCertificateProbe REJECT lab-root-a.p12 changeit \
  https://api.lab.test:8446/ping

java TlsCertificateProbe REJECT lab-root-a.p12 changeit \
  https://api.lab.test:8447/ping
```

Khi năm fixture được cấu hình và server đều sẵn sàng, stdout từ **năm lần chạy theo đúng thứ tự**:

```text
PASS ACCEPT
PASS REJECT
PASS REJECT
PASS REJECT
PASS REJECT
```

Nếu test không đạt, chương trình ném exception/assertion và không in `PASS` cho ca đó. `HttpClient` có thể trả HTTP 404 cho endpoint `/ping` sau khi TLS hợp lệ; đó vẫn là **ACCEPT TLS**, vì mục tiêu đang kiểm thử TLS handshake. Nếu muốn kiểm tra chức năng HTTP riêng, phải thêm assertion HTTP status/body thành một test độc lập.

**Giới hạn của chương trình mẫu:** Nhánh `REJECT` xác định lỗi xảy ra trong TLS (`SSLException` ở cause chain), nhưng **chưa tự chứng minh nguyên nhân cụ thể là expired/hostname/chain/CA**. Việc phân lập đã được làm bằng fixture: chỉ đổi một điều kiện so với baseline, đồng thời kiểm tra certificate metadata, chain do server gửi và truststore. Trong kiểm thử nâng cao, có thể ghi lại cause/reason có cấu trúc hoặc xác minh leaf theo riêng từng tiêu chí; không chốt test bằng substring exception phụ thuộc vendor/JDK.

### Tại sao dùng TrustManagerFactory chuẩn?

```text
KeyStore chứa trustedCertEntry Root A
        |
TrustManagerFactory.init(store)
        |
SSLContext.init(..., trustManagers, ...)
        |
HttpClient với kiểm tra HTTPS mặc định
        |
TLS handshake / cert chain / endpoint identity
```

Thiết kế này làm **tập trust anchor của test trở nên xác định**, tránh việc truststore của JDK, OS hoặc container thay đổi mà test vô tình thành công. Dùng `null` cho `KeyManager[]` vì bài kiểm thử này là **xác minh chứng thư server một chiều**, không yêu cầu client certificate/mTLS.

Không thay `TrustManager` bằng implementation có `checkServerTrusted` rỗng, không dùng `SSLContext` “trust-all” và không đặt thuộc tính nội bộ để bỏ hostname verification. Nếu test âm tính đột nhiên pass sau một “workaround”, đó có thể là **lỗ hổng mà bài kiểm thử cần phát hiện**.

## 9. Java JSSE và PKIX: đọc lỗi đúng tầng

Khi TLS thất bại, exception bên ngoài thường là `SSLHandshakeException`, `SSLException` hoặc `IOException` bao một lỗi sâu hơn. Không có cam kết mọi JDK, TLS provider và thư viện HTTP sẽ trả **cùng một message**.

| Ca | Dấu hiệu thường thấy (không cố định) | Bằng chứng quan trọng hơn message |
| --- | --- | --- |
| Expired | `CertificateExpiredException` hoặc `CertPathValidatorException` liên quan validity | `notAfter` đã qua, CA/SAN/chain còn đúng |
| Not yet valid | `CertificateNotYetValidException` | `notBefore` còn ở tương lai |
| Wrong hostname | `No subject alternative DNS name matching ...` hoặc `No name matching ...` | Host URL không nằm trong SAN, trust chain hợp lệ |
| Missing intermediate | `SunCertPathBuilderException: unable to find valid certification path ...` | Server gửi leaf thiếu issuer; client chỉ có Root A |
| Untrusted CA | `PKIX path building failed` / no trust anchor | Chain server đầy đủ dưới Root B; truststore chỉ Root A |
| Wrong EKU/key usage | `Extended key usage does not permit ...` hoặc lỗi constraints | Metadata EKU/key usage không phù hợp |
| Wrong protocol/cipher | `handshake_failure`, `protocol_version` | Không thể quy lỗi cho certificate khi chưa điều tra |

Dữ liệu chẩn đoán nên gồm thời điểm UTC, phiên bản JDK/provider, hostname/SNI, TLS endpoint (port), fingerprint/subject/issuer/SAN của leaf **công khai**, chain được server gửi, tên truststore và alias CA test, nguyên nhân sâu nhất ở dạng đã lọc. **Không** ghi private key, token bearer, session secrets hoặc bản dump TLS thô vào hệ thống log mặc định.

### Bật JSSE debug có mục tiêu

Trong môi trường lab, trước khi mở probe, có thể bật:

```bash
java -Djavax.net.debug=ssl,handshake,trustmanager \
     -Djava.security.debug=certpath \
     TlsCertificateProbe REJECT lab-root-a.p12 changeit \
     https://api.lab.test:8446/ping
```

`javax.net.debug` hỗ trợ xem TLS handshake/trust manager với SunJSSE; `java.security.debug=certpath` cung cấp thêm thông tin dựng đường dẫn chứng thực. Tùy provider/version, output và tùy chọn có thể khác. Debug có thể sinh log lớn, chứa metadata/cấu trúc nhạy cảm; chỉ bật tạm trong test cách ly, hạn chế quyền truy cập, không công bố nguyên log lên ticket công khai.

Kiểm tra theo thứ tự:

1. Client có thực sự gọi hostname và cổng dự kiến? SNI có làm server chọn nhầm cert không?
2. Server có gửi đúng leaf/SAN, đúng intermediate và thứ tự hợp lý không?
3. Thời hạn, thuật toán ký, KU/EKU, basic constraints có đúng không?
4. TrustManager đang dùng truststore nào; Root A có thực sự là trust anchor không?
5. Lỗi phát sinh ở path validation hay hostname verification; có lỗi TLS protocol độc lập không?
6. Một client khác (OpenSSL/browser) có khác behavior vì truststore, AIA, cache hay hostname verification khác không?

**Đừng dựa duy nhất vào text lỗi**. Thiếu intermediate và untrusted root đôi khi có cùng `PKIX path building failed`; expired và wrong hostname cũng có thể đều được đóng gói thành `SSLHandshakeException`.

## 10. Thiết kế assertion và quy trình kiểm thử không sinh false positive

### Baseline và tính độc lập

Mỗi lần test nên xác minh trước rằng:

- Test server đang lắng nghe, **trả đúng fixture** (serial/fingerprint/SAN/issuer) ở đúng cổng và hostname/SNI.
- `OK` kết nối được bằng **cùng bộ client/TrustManagerFactory/truststore**. Nếu OK fail vì cấu hình CA hoặc TLS không tương thích, toàn bộ negative test chưa có ý nghĩa.
- Mỗi negative fixture chỉ có một thuộc tính khác OK; các thuộc tính khác không bị vô tình hỏng.
- Các negative request bị từ chối **trong TLS**. DNS failure, timeout, TCP refusal hay HTTP 403 không được đánh dấu certificate test PASS.
- Với missing intermediate, đã xác minh wire chain không chứa Intermediate A và client không có intermediate qua nguồn phụ.

Một hệ thống kiểm thử lý tưởng kiểm soát cả **leaf certificate, chain đã gửi, truststore, hostname đích, thời gian và TLS endpoint**. Tránh dùng website công khai như “expired demo” cho regression dài hạn: dữ liệu/chứng thư/chain có thể thay đổi, server có thể đóng cửa hoặc bị CDN/proxy thay thế.

### Đừng test âm tính bằng cách nuốt mọi exception

```java
// SAI: test này "pass" cả khi DNS hỏng, máy chủ chưa chạy.
try {
    httpClient.send(request, handler);
    throw new AssertionError("Expected failure");
} catch (Exception ignored) {
    // Nuốt cả lỗi assert nếu cấu trúc try/catch đặt sai!
}
```

Phải phân loại lỗi TLS, giữ lỗi kết nối mạng tách biệt, và để test fail nếu handshake bất ngờ thành công. Nếu client được thiết kế retry, cấu hình không tự retry sang HTTP plaintext hoặc host/CA khác trong test chứng thư. Tắt tự theo redirect (hoặc assert đích redirect) để không vô tình kiểm tra server thứ hai.

### Thêm những ca ở ranh giới

| Input/biến thể | Mong đợi |
| --- | --- |
| Leaf đúng SAN, chain hoàn chỉnh, Root A trusted, valid | Kết nối TLS thành công |
| Leaf hết hạn vài giây và vài năm | Từ chối; CI dùng đồng hồ UTC ổn định |
| Leaf chưa tới `notBefore` | Từ chối |
| SAN sai trong khi CN đúng | Từ chối theo policy SAN hiện đại |
| Host dùng IP nhưng cert chỉ có DNS SAN | Từ chối IP mismatch |
| Wildcard `*.lab.test` với hai mức subdomain | Không tự cho phép khớp sâu tùy tiện |
| Thiếu Intermediate A nhưng Root A vẫn trusted | Từ chối trong lab không có nguồn trung gian khác |
| Chỉ thiếu **Root A** trong wire chain, nhưng truststore có Root A | Vẫn có thể kết nối; đây không phải lỗi |
| Full chain dưới Root B, truststore chỉ Root A | Từ chối |
| Nhập Root B vào truststore test riêng | Ca Root B có thể thành công (positive control có chủ đích), không sửa truststore policy chính |
| Server trả leaf/key không khớp | TLS fail vì nguyên nhân khác; không dùng để chứng minh bốn ca |
| Proxy/CDN chuyển đổi certificate | Test phải xác minh cert thực sự được client nhận |
| Network bị chặn hoặc hostname không resolve | Test phải báo lỗi môi trường, không coi là TLS reject |

### Kết nối được tái sử dụng

Nếu một HTTP client đã có connection TLS tồn tại trước khi bạn đổi chứng thư trên server, request tiếp theo có thể **tái sử dụng connection** và không thực hiện certificate validation lại. Tương tự, TLS session resumption có thể làm cách đo khác với handshake đầy đủ. Với các negative test, dùng client mới, endpoint/cổng riêng, fixture ổn định trước khi kết nối, và đảm bảo ca đang kiểm tra **handshake mới**. Không sửa certificate/keystore của server giữa chừng rồi suy ra request tiếp theo chắc chắn đã nhìn thấy cert mới.

## 11. Khác biệt giữa HTTPS, raw SSLSocket, mTLS và reverse proxy

**HTTPS client dùng Java `HttpClient`/`HttpsURLConnection`:** xác minh chain và hostname theo behavior mặc định của implementation; với `HttpClient` không cần tự thêm `HostnameVerifier`. Nếu thư viện HTTP khác (OkHttp, Apache HttpClient, Netty, Spring client...) được dùng trong production, nên **chạy lại cùng bộ fixture qua thư viện đó**, vì cấu hình trust/hostname/connection pooling có thể khác.

**Raw `SSLSocket` / `SSLEngine`:** một socket tự tạo không nhất thiết biết identity phải đối chiếu nếu developer không đặt endpoint identification. Hãy test case `NAME` với chính code socket đó. Kiểm tra path thành công nhưng hostname sai mà socket vẫn handshake là dấu hiệu policy chưa đầy đủ.

**Mutual TLS (mTLS):** cùng bốn dạng lỗi có thể áp dụng cho **certificate phía client**, nhưng lúc này server là phía xác minh client. Hostname của server và identity của client không phải cùng một khái niệm; server thường kiểm tra client cert path/EKU/identity theo policy của nó, không dùng quy tắc HTTPS hostname như đối với URL. Khi test mTLS, tách hai hướng `server-auth` và `client-auth`, giữ đúng truststore của từng phía.

**Proxy/load balancer/service mesh:** TLS có thể kết thúc tại gateway rồi mở kết nối TLS khác tới backend:

```text
Java client -- TLS #1 --> Gateway -- TLS #2 --> Backend
             cert A                 cert B
             trust A                trust B
```

Client chỉ kiểm tra cert của **TLS #1**. Nếu lỗi nằm ở cert backend nhưng gateway vẫn trả 502, đó là **TLS #2 lỗi** và phải test bằng client/agent ở gateway hoặc probe trực tiếp backend theo chính sách; browser không thể chứng minh lỗi trust backend chỉ bằng một dòng 502. Với SNI trên nhiều virtual host, route test phải khớp cổng/hostname cụ thể.

**Container/JDK updates:** trust anchor và thuật toán bị chặn có thể thay đổi sau khi nâng JDK hoặc thay base image. Test trên runtime thực sự triển khai, kể cả truststore tự cấu hình. Nếu probe Java và production dùng trust context khác nhau, kết quả probe chỉ mô tả probe.

## 12. “Sửa nhanh” khiến test mất giá trị và tăng rủi ro

| Thao tác | Vì sao sai | Hướng đúng |
| --- | --- | --- |
| Dùng `TrustManager` có `checkServerTrusted` rỗng | Chấp nhận mọi CA/cert | TrustManagerFactory/PKIX với CA được phê duyệt |
| `HostnameVerifier` luôn trả `true` | Bỏ kiểm tra endpoint identity | Sửa URL/SAN/SNI, giữ hostname verification |
| Dùng HTTP plaintext khi HTTPS thất bại | Giảm bảo mật kết nối | Fail closed và báo lỗi cấu hình |
| Import leaf bất kỳ vào global `cacerts` | Tin cậy ngoài phạm vi và che lỗi chain | Sửa server gửi intermediate; quản lý CA có kiểm soát |
| Tắt certificate validation chỉ trong “test” nhưng copy cấu hình sang prod | Test pass giả và dễ lọt cấu hình nguy hiểm | Negative tests phải dùng đúng behavior bảo vệ thật |
| Thay đồng hồ toàn CI để tạo expired test | Ảnh hưởng token, DB, build và test khác | Phát hành expired fixture, offline `PKIXParameters.setDate` |
| Chỉ assert `SSLHandshakeException` không xem fixture | Nhiều lỗi khác nhau đều ra exception đó | Baseline + cô lập fixture + inspect SAN/chain/truststore |
| Dùng cert tự ký hết hạn làm root rồi test leaf expiry | Trust anchor có semantics riêng | Leaf expired do CA test vẫn còn hiệu lực ký |
| Tin chain đã có trên disk chứng minh server gửi chain | Proxy/TLS server có thể gửi subset khác | `openssl s_client -showcerts` tại endpoint thực |
| Chỉ test bằng browser hoặc `curl -k` | Trust config khác và `-k` tắt xác minh | Chạy Java client thật + OpenSSL với CAfile/hostname check |

### Quy trình sửa lỗi trên môi trường thật

1. **Expired:** xác minh server đang trình certificate nào, thời gian UTC, certificate renewal/ACME workflow, deploy đúng full chain và phục hồi trước giờ hết hạn. Test lại bằng Java client thật; thiết lập cảnh báo trước hạn.
2. **Hostname:** xác nhận URL đích và reverse proxy/SNI, phát hành cert với SAN đúng; đừng đổi client sang hostname khác nếu điều đó định tuyến sai service.
3. **Missing intermediate:** cấu hình server `fullchain` đúng theo CA, không yêu cầu mỗi client import intermediate bằng tay. Kiểm tra wire chain sau deploy.
4. **Untrusted CA:** xác minh PKI policy và nguồn root. Nếu CA đúng nhưng JDK chưa trust, cập nhật truststore qua quy trình có phê duyệt; không vô hiệu hóa kiểm tra chữ ký.
5. **Sau khắc phục:** chạy lại `OK + cả bốn negative fixtures`. Một sửa đổi phải giữ nguyên tính chất “fail closed” của các ca âm tính.

## 13. Tích hợp CI và vận hành giám sát

### Quy trình CI gợi ý

```text
1. Tạo/khởi động CA lab, leaf và intermediate fixture
2. Dựng 5 TLS endpoints từ fixture: OK, EXP, NAME, CHAIN, ROOT
3. Ghi nhận fingerprint/SAN/issuer/notBefore/notAfter/wire chain
4. Tạo truststore chỉ có Root A
5. Chờ kiểm tra readiness qua cơ chế riêng (không dùng TLS test)
6. Chạy Java 17 probe: OK phải ACCEPT, 4 ca xấu phải REJECT
7. Chạy OpenSSL diagnostic để xác minh fixture đúng đặc tính
8. Xóa các file key tạm/khóa log; lưu báo cáo không chứa secret
```

**Readiness** phải phân biệt server chưa chạy với TLS bị từ chối. Có thể kiểm tra cổng listening trong nội bộ lab, kiểm tra health qua kênh kiểm soát riêng, hoặc chờ log “ready”; không đánh dấu negative test pass chỉ vì kết nối thất bại.

Một báo cáo CI có ích gồm:

| Trường | Ý nghĩa |
| --- | --- |
| `caseId` | OK / EXP / NAME / CHAIN / ROOT |
| `referenceHost`, `port` | Đúng host/port client định truy cập |
| `tlsEndpoint` | Proxy hay backend mà client thật sự kiểm tra |
| `expected / actual` | ACCEPT hoặc REJECT ở tầng TLS |
| `certificateFingerprint` | Bằng chứng fixture hiện diện; **không phải secret** |
| `SAN / issuer / validity` | Tách nguyên nhân sai host, CA, thời hạn |
| `wireChainCount` | Phát hiện server gửi thiếu intermediate |
| `trustAnchorAlias` | CA client được phép trust |
| `jdkVersion / provider` | Giải thích khác biệt hành vi |
| `failureClass` | Nhóm lỗi sau khi đã lọc dữ liệu nhạy cảm |

### Cảnh báo vận hành cần thiết

Kiểm thử trước phát hành không thay giám sát certificate rotation. Theo dõi `notAfter`, thay đổi issuer/SAN/fingerprint, chuỗi intermediate từ **các vị trí mạng client thật**, kết quả connection test với đúng JDK/truststore, và chứng thư ở từng TLS termination point. Lên lịch cảnh báo đủ sớm trước khi hết hạn, xác nhận certificate mới đã được **serve** sau reload, kiểm tra cả SNI virtual host, và phát hiện server chỉ còn leaf sau cấu hình lại.

**Lưu ý về revocation:** OCSP/CRL, certificate transparency, pinning hoặc chính sách thuật toán là những kiểm tra bổ sung tùy hệ thống. Một test chỉ kiểm tra bốn lỗi ở đây không chứng minh tất cả các điều kiện PKI đều an toàn. Với OCSP/CRL, định nghĩa rõ policy cho sự cố mạng (hard fail/soft fail), vì behavior mặc định của client/provider có thể khác nhau.

## 14. Checklist cuối bài

1. Có **positive control** cùng truststore/JDK chứng minh leaf đúng SAN, ngày tháng và chain chạy được không?
2. Case expired có leaf do CA **được tin** ký, SAN đúng, chain đầy đủ và thật sự `notAfter` trong quá khứ không?
3. Case wrong hostname có leaf còn hạn và chain tin cậy, nhưng **SAN không khớp reference host** không?
4. Case missing intermediate có client chỉ trust Root A, server chỉ gửi leaf, không có nguồn phụ để tìm intermediate không?
5. Case untrusted CA có **full intermediate chain** dưới Root B, nhưng truststore chỉ có Root A không?
6. Client có dùng đúng hostname/SNI, đúng truststore và đúng TLS endpoint/port không?
7. Negative test chỉ PASS khi **TLS certificate verification từ chối**, còn DNS/TCP timeout/HTTP status được phân biệt không?
8. Java client có giữ hostname verification và PKIX trust manager tiêu chuẩn không?
9. Có xử lý connection pooling/session resumption để mỗi test thực sự handshake với fixture đang mong đợi không?
10. Sau sửa lỗi, cả đường OK và bốn đường REJECT vẫn chạy đúng, không có “bypass” nào lọt vào config prod không?

## 15. Tài liệu chuẩn và API liên quan

- [RFC 5280 — X.509 Certification Path Validation](https://www.rfc-editor.org/rfc/rfc5280): validity, issuer chain, trust anchor, basic constraints và path validation.
- [RFC 9525 — Service Identity in TLS](https://www.rfc-editor.org/rfc/rfc9525): SAN, DNS-ID/IP-ID, wildcard và endpoint identity; thay thế RFC 6125.
- [Oracle Java 17 — X509Certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html): `checkValidity()` và `checkValidity(Date)`.
- [Oracle Java 17 — PKIXParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html): `setDate`, trust anchors và validation parameters.
- [Oracle Java 17 — SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setEndpointIdentificationAlgorithm`.
- [Oracle Java 17 — HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html) và [TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html): client HTTPS và trust manager.
- [Oracle — JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): TLS configuration, truststore và debug (`javax.net.debug`).
- [OpenSSL — s_client](https://docs.openssl.org/master/man1/openssl-s_client/), [OpenSSL — verify](https://docs.openssl.org/master/man1/openssl-verification-options/): xem server chain và kiểm tra hostname/trust với CA xác định.

**Ghi nhớ:** một certificate không tự “đáng tin” chỉ vì có chữ ký hợp lệ. Java client phải kiểm tra **đúng thời gian, đúng tên đích, đủ đường dẫn CA và đúng trust anchor**; test âm tính phải chứng minh riêng từng hàng rào vẫn từ chối kết nối xấu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-008">22.8 Kiểm thử sai khóa, sai mật khẩu, tham số không khớp và dữ liệu bị cắt</a>

<details>
<summary>Xem chi tiết</summary>

# 22.8 — Kiểm thử sai khóa, sai mật khẩu, tham số không khớp và dữ liệu bị cắt

## 1. Mục tiêu và phạm vi

Một chức năng mật mã không chỉ phải giải mã hoặc xác minh **đúng** trong điều kiện lý tưởng. Nó còn phải từ chối dữ liệu sai, hỏng, thiếu hoặc không nhất quán **một cách có kiểm soát**. Nếu hệ thống vẫn trả về bản rõ, xác nhận chữ ký sai, xuất file chưa kiểm chứng hoặc tự chuyển sang thuật toán yếu khi gặp lỗi, việc có một phép mã hóa “đúng thuật toán” vẫn chưa đủ để an toàn.

Sau bài này, bạn có thể:

- Thiết kế các phép thử âm tính cho **sai khóa**, **sai mật khẩu**, **tham số mật mã không khớp** và **ciphertext/chữ ký/dữ liệu đóng gói bị cắt**.
- Phân biệt lỗi **định dạng đầu vào**, lỗi **khởi tạo khóa/tham số**, lỗi **xác thực mật mã** và lỗi **nghiệp vụ/ủy quyền**.
- Dùng Java Cryptography Architecture (JCA/JCE) trên Java 17+ để kiểm chứng AES-GCM, PBKDF2, chữ ký số, và hiểu các khác biệt với RSA-OAEP, CBC hoặc HMAC.
- Viết test có *positive control*, mỗi test chỉ thay **một biến** so với vector hợp lệ, assert kết quả từ chối đúng lớp và không lộ bản rõ.
- Tránh các lỗi triển khai khi đọc stream, giải mã theo từng phần, xử lý key rotation và báo lỗi ra client.

**Phạm vi:** vận hành và kiểm thử thành phần mật mã Java trong một hệ thống thực tế. Ví dụ thực hành sử dụng AES-GCM, PBKDF2-HMAC-SHA256 và RSA signature trong JDK 17. Đây là kiến thức kiểm thử, không phải hướng dẫn thử đoán mật khẩu trên tài khoản của người khác.

## 2. Đặt đúng tiêu chí “thất bại an toàn”

Ta bắt đầu với một bản mã hợp lệ:

~~~text
plaintext + encryption key + nonce/IV + AAD
                        │
                  encrypt (AEAD)
                        ▼
                  ciphertext + tag
                        │
          decrypt cùng key, IV, AAD
                        ▼
                   plaintext
~~~

Với một cơ chế **authenticated encryption with associated data (AEAD)** như AES-GCM, tag xác thực ràng buộc bản mã với key, IV/nonce và AAD. Khi một thông số thiết yếu không đúng, việc xác thực tag sẽ thất bại với xác suất gần như chắc chắn. Trong Java, lỗi thường được báo tại `Cipher.doFinal(...)` bằng `AEADBadTagException`; đây là lớp con của `BadPaddingException`.

Nhưng *một test thất bại* không đủ để chứng minh hệ thống an toàn. Cần xác nhận đồng thời:

1. **Positive control thành công:** cùng một fixture, khóa/tham số/dữ liệu gốc giải mã ra chính xác byte ban đầu.
2. **Negative control bị từ chối:** sai khóa, sai mật khẩu hoặc dữ liệu bị sửa **không** trả về bản rõ được ứng dụng chấp nhận.
3. **Đúng ranh giới lỗi:** đầu vào sai cấu trúc bị parser từ chối trước khi tính toán tốn tài nguyên; sai authentication thất bại sau bước kiểm tra tag.
4. **Không có side effect:** không ghi đè file đích, không phát sự kiện thành công, không commit dữ liệu, không gửi kết quả xuống hệ thống khác trước khi xác thực xong.
5. **Thông báo nhất quán:** người dùng nhận lỗi tổng quát, không bị lộ key, password, plaintext, tag, stack trace hoặc thông tin nội bộ giúp phân biệt bí mật.
6. **Đúng phạm vi kiểm thử:** chỉ thao tác trên test fixture, môi trường cô lập và khóa thử nghiệm; không phụ thuộc dữ liệu sản xuất.

Dù thuật toán có thể ném exception đặc trưng, **không dùng tên/lời nhắn exception của provider làm API contract public**. Ở lớp bên ngoài, có thể quy về lỗi như `DECRYPTION_FAILED` hoặc `INVALID_ENCRYPTED_OBJECT`, nhưng log kỹ thuật nội bộ vẫn phải đủ để phân biệt định dạng, cấu hình và lỗi vận hành mà không ghi bí mật.

## 3. Thiết kế ma trận kiểm thử

Có hai loại đầu vào khác nhau cần kiểm tra:

- **Ciphertext hoặc chữ ký không hợp lệ về mật mã:** đúng cấu trúc byte, nhưng không xác minh được với key/parameter đã cho.
- **Envelope không hợp lệ về định dạng:** thiếu header, version lạ, trường độ dài âm, nonce sai chiều dài theo hợp đồng ứng dụng, tag quá ngắn hoặc dữ liệu vượt hạn mức. Trường hợp này nên bị chặn **trước** khi gọi crypto provider.

| Nhóm | Biến bị thay so với fixture chuẩn | Kỳ vọng và mức độ bảo đảm |
| --- | --- | --- |
| Sai khóa AES nhưng vẫn đúng 128/256 bit | Đảo một bit của key | AES-GCM không xác thực tag; thường `AEADBadTagException` |
| Khóa AES có độ dài không hợp lệ | Key 7 byte chẳng hạn | `InvalidKeyException` tại `init`, không tới bước giải mã |
| Sai mật khẩu | Password khác khi chạy PBKDF2 | Sinh key khác, nên AES-GCM bị từ chối ở bước tag |
| Sai salt PBKDF2 | Đảo một bit trong salt | Key khác, GCM xác thực thất bại |
| Sai iteration count/key length/KDF | Dùng cấu hình khác | Có thể không sinh key tương đương; với đúng key length nhưng khác iteration, GCM tag thất bại |
| Sai nonce/IV có cùng độ dài | Đảo một bit của IV | GCM tag thất bại |
| Sai độ dài IV theo format ứng dụng | Thay IV 12 byte bằng 11 byte | Parser/validator từ chối; không cần dựa vào provider |
| Sai AAD | Đổi mã record/tenant/version gắn với dữ liệu | GCM tag thất bại |
| Sửa ciphertext hoặc tag | Đảo một bit | GCM tag thất bại |
| Cắt 1 byte khỏi ciphertext có tag | Giữ header, rút ngắn `ciphertext\|\|tag` | GCM không xác thực được |
| Cắt chỉ còn ít hơn chiều dài tag | Còn 15 byte khi tag 16 byte | Validator từ chối như dữ liệu không hoàn chỉnh |
| Cắt giữa header/length field | Thiếu byte của field bắt buộc | Parser trả lỗi cấu trúc/EOF |
| Sai public key khi verify chữ ký | Public key từ key pair khác | `Signature.verify(...)` thường trả `false` với chữ ký đúng độ dài |
| Đổi thông điệp đã ký | Đảo byte dữ liệu, giữ chữ ký | `verify` trả `false` |
| Sai mật khẩu keystore | Mật khẩu không mở được JKS/PKCS12 | Lỗi tải hoặc khôi phục key; loại/cause tùy keystore và provider |
| Thuật toán/parameter không hỗ trợ | Transformation, tag size, key spec không được chấp nhận | `NoSuchAlgorithmException`, `NoSuchPaddingException`, `InvalidAlgorithmParameterException`, ... tùy vị trí |

**Không ép mọi lỗi thành cùng exception nội bộ.** Một key sai nhưng hợp lệ về kích thước khác với một key có format không hợp lệ; dữ liệu bị cắt trước header khác với ciphertext bị cắt sau tag. Có thể thống nhất HTTP response, nhưng integration test nên giữ phân loại kỹ thuật.

## 4. Fixture đúng và cách tạo lỗi có chủ đích

Để tránh false positive, xây test theo trình tự:

1. Tạo dữ liệu gốc có độ dài biết trước; chọn một mật khẩu thử nghiệm hoặc key phát sinh trong bộ test.
2. Mã hóa bằng thuật toán, tham số và định dạng đã được quy định, dùng nonce/IV **mới** cho lần mã hóa.
3. Giải mã bản gốc và so sánh **byte-by-byte** với plaintext ban đầu. Nếu bước này thất bại thì không chạy các negative case.
4. Với mỗi case, tạo **bản sao riêng** của envelope rồi thay đúng **một** thành phần: key, mật khẩu, salt, iteration, IV, AAD, ciphertext, tag hoặc độ dài.
5. Gọi **cùng đường giải mã production** hoặc hàm lõi được production sử dụng, không thay bằng một đường “test giả” khác.
6. Assert bị từ chối; coi việc chạy hết mà không ném exception, trả trạng thái thành công hoặc ghi file đích là **test FAIL**.
7. Lặp trên version JDK, security provider và định dạng file mà sản phẩm thực sự hỗ trợ.

Ví dụ bộ dữ liệu đóng gói:

~~~text
magic | formatVersion | kdfId | iterations | salt |
cipherId | IV/nonce | aadContext | ciphertextAndTag
~~~

Đây là **sơ đồ**, không phải wire format hoàn chỉnh. Một triển khai thực tế phải xác định độ dài từng trường, byte order, giới hạn tối đa, cách biểu diễn AAD, danh sách thuật toán cho phép và versioning. Chỉ những trường hợp parser nhận đúng format mới được chuyển tới JCE.

Những tham số **không bí mật** như salt và IV vẫn phải đúng. Đừng nhầm “không cần giấu” với “không cần bảo đảm toàn vẹn”. Format/version/KDF ID có thể cần được ràng buộc vào AAD hoặc một cấu trúc xác thực phù hợp. Không cho bản mã tự khai iteration count tùy ý không giới hạn; kẻ gửi dữ liệu có thể lợi dụng để gây tốn CPU.

## 5. AES-GCM: phép thử sai key, IV, AAD, tag và ciphertext

AES-GCM có hai chức năng: bảo mật nội dung và kiểm tra toàn vẹn/xác thực. Trên JDK 17, dùng transformation `AES/GCM/NoPadding`, `GCMParameterSpec` và `Cipher.updateAAD` khi có associated data.

Các thông số thường gặp trong một format đã thống nhất:

- AES key 128 hoặc 256 bit. **Key phải đúng byte**, không chỉ đúng tên alias.
- IV/nonce 12 byte (96 bit) theo cấu hình thông dụng. **Không tái sử dụng nonce với cùng key để mã hóa**; tái sử dụng có thể phá tính bảo mật và tính xác thực GCM.
- Authentication tag 16 byte (128 bit) nếu `GCMParameterSpec(128, iv)`. Số `128` là **bit**, không phải byte.
- AAD chứa metadata gắn với đối tượng, chẳng hạn tenant ID, record ID hoặc format version; AAD không được mã hóa nhưng được xác thực. Decrypt phải truyền **chính xác** AAD của encrypt, bao gồm cách encode và thứ tự byte.
- Với JCE AES-GCM, `doFinal(plaintext)` khi encrypt thường trả về `ciphertext` và tag nối vào cuối. Decrypt nhận chính xác dạng byte mà provider yêu cầu.

Ví dụ:

~~~java
// Snippet yêu cầu SecretKey key, byte[] iv (12 byte),
// byte[] aad và byte[] plaintext đã có.
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
cipher.updateAAD(aad);            // Gửi AAD trước khi xử lý plaintext
byte[] ciphertextAndTag = cipher.doFinal(plaintext);
~~~

Nếu `key`, `iv` hoặc `aad` sai lúc decrypt, tag sẽ không được xác minh. Không thể nhìn vào `AEADBadTagException` rồi kết luận chắc chắn lỗi do người dùng nhập sai password: cùng một tín hiệu có thể do **sai key, sai AAD, sai IV, sửa byte hoặc cắt dữ liệu**.

**Tránh nhầm lẫn:** một exception khi gọi `Cipher.init(...)` không phải là chứng cứ đã kiểm tra tag. Ví dụ `InvalidKeyException` với key sai kích thước xảy ra **trước khi decrypt**, còn tag mismatch xảy ra trong quá trình `doFinal(...)`.

## 6. Chương trình Java 17 đầy đủ: positive control + negative cases

Ví dụ dưới đây dùng **JDK 17+ thuần**, không cần dependency bên ngoài. Nó sử dụng PBKDF2-HMAC-SHA256 để tạo key AES-128 từ mật khẩu thử nghiệm, AES-GCM để mã hóa, và kiểm thử chữ ký RSA SHA-256. Chương trình cố tình dùng biến `char[]` để password có thể được xóa sau test; việc quản lý vòng đời key thật còn phải được thiết kế riêng.

Tạo file **CryptoNegativeTests.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoNegativeTests {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    private static final int AES_KEY_BITS = 128;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    // Envelope lưu mảng byte để demo dễ đọc; production cần
    // defensive copy và một format serialize có length validation.
    record Envelope(byte[] salt, byte[] iv, byte[] ciphertextAndTag) {}

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    private static SecretKey deriveKey(
            char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                password, salt, iterations, AES_KEY_BITS);
        try {
            byte[] raw = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            try {
                return new SecretKeySpec(raw, "AES");
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static Envelope encrypt(
            char[] password, byte[] plain, byte[] aad)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);

        SecretKey key = deriveKey(password, salt, ITERATIONS);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);

        return new Envelope(salt, iv, cipher.doFinal(plain));
    }

    private static void validateEnvelope(Envelope envelope) {
        if (envelope == null
                || envelope.salt() == null
                || envelope.iv() == null
                || envelope.ciphertextAndTag() == null
                || envelope.salt().length != SALT_BYTES
                || envelope.iv().length != IV_BYTES
                || envelope.ciphertextAndTag().length < TAG_BYTES) {
            throw new IllegalArgumentException("Malformed envelope");
        }
    }

    private static byte[] decryptWithKey(
            SecretKey key, Envelope envelope, byte[] aad)
            throws GeneralSecurityException {
        validateEnvelope(envelope);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, envelope.iv()));
        cipher.updateAAD(aad);
        return cipher.doFinal(envelope.ciphertextAndTag());
    }

    private static byte[] decrypt(
            char[] password, Envelope envelope, byte[] aad, int iterations)
            throws GeneralSecurityException {
        validateEnvelope(envelope);
        SecretKey key = deriveKey(password, envelope.salt(), iterations);
        return decryptWithKey(key, envelope, aad);
    }

    private static void expectRejected(
            String caseName, Class<? extends Exception> type,
            CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (Exception exception) {
            if (!type.isInstance(exception)) {
                throw new AssertionError(
                        caseName + ": unexpected exception type", exception);
            }
            System.out.println(caseName + "=REJECTED");
            return;
        }
        throw new AssertionError(caseName + ": unexpectedly accepted");
    }

    private static boolean verifySignature(
            PublicKey key, byte[] message, byte[] signature)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        char[] password = "demo-password".toCharArray();
        char[] wrongPassword = "wrong-password".toCharArray();

        try {
            byte[] message = "Hello, authenticated data!"
                    .getBytes(StandardCharsets.UTF_8);
            byte[] aad = "record:42".getBytes(StandardCharsets.UTF_8);

            Envelope valid = encrypt(password, message, aad);

            byte[] decoded = decrypt(password, valid, aad, ITERATIONS);
            System.out.println(
                    "gcm-valid=" + Arrays.equals(message, decoded));

            // Case 1: khóa AES cùng kích thước nhưng sai một bit.
            byte[] originalKey = deriveKey(
                    password, valid.salt(), ITERATIONS).getEncoded();
            byte[] wrongKeyBytes = originalKey.clone();
            wrongKeyBytes[0] ^= 1;
            SecretKey wrongKey = new SecretKeySpec(wrongKeyBytes, "AES");
            Arrays.fill(originalKey, (byte) 0);
            Arrays.fill(wrongKeyBytes, (byte) 0);

            expectRejected("wrong-AES-key", AEADBadTagException.class,
                    () -> decryptWithKey(wrongKey, valid, aad));

            // Case 2: sai mật khẩu dẫn tới khóa PBKDF2 khác.
            expectRejected("wrong-password", AEADBadTagException.class,
                    () -> decrypt(
                            wrongPassword, valid, aad, ITERATIONS));

            // Case 3: salt sai một bit.
            byte[] badSalt = valid.salt().clone();
            badSalt[0] ^= 1;
            Envelope changedSalt = new Envelope(
                    badSalt, valid.iv(), valid.ciphertextAndTag());
            expectRejected("wrong-salt", AEADBadTagException.class,
                    () -> decrypt(password, changedSalt, aad, ITERATIONS));

            // Case 4: sai iteration count khi dẫn xuất key.
            expectRejected("wrong-iterations", AEADBadTagException.class,
                    () -> decrypt(password, valid, aad, ITERATIONS + 1));

            // Case 5: IV vẫn đủ 12 byte nhưng sai một bit.
            byte[] badIv = valid.iv().clone();
            badIv[0] ^= 1;
            Envelope changedIv = new Envelope(
                    valid.salt(), badIv, valid.ciphertextAndTag());
            expectRejected("wrong-IV", AEADBadTagException.class,
                    () -> decrypt(password, changedIv, aad, ITERATIONS));

            // Case 6: AAD không khớp metadata lúc mã hóa.
            byte[] wrongAad = "record:43"
                    .getBytes(StandardCharsets.UTF_8);
            expectRejected("wrong-AAD", AEADBadTagException.class,
                    () -> decrypt(password, valid, wrongAad, ITERATIONS));

            // Case 7: sửa byte ciphertext.
            byte[] changedBytes = valid.ciphertextAndTag().clone();
            changedBytes[0] ^= 1;
            Envelope changedCiphertext = new Envelope(
                    valid.salt(), valid.iv(), changedBytes);
            expectRejected("changed-ciphertext", AEADBadTagException.class,
                    () -> decrypt(
                            password, changedCiphertext, aad, ITERATIONS));

            // Case 8: sửa byte cuối (authentication tag).
            byte[] changedTag = valid.ciphertextAndTag().clone();
            changedTag[changedTag.length - 1] ^= 1;
            Envelope changedTagEnvelope = new Envelope(
                    valid.salt(), valid.iv(), changedTag);
            expectRejected("changed-tag", AEADBadTagException.class,
                    () -> decrypt(
                            password, changedTagEnvelope, aad, ITERATIONS));

            // Case 9: cắt một byte, vẫn có đủ 16 byte đầu để vào JCE.
            byte[] cutOne = Arrays.copyOf(
                    valid.ciphertextAndTag(),
                    valid.ciphertextAndTag().length - 1);
            Envelope truncated = new Envelope(
                    valid.salt(), valid.iv(), cutOne);
            expectRejected("cut-one-byte", AEADBadTagException.class,
                    () -> decrypt(password, truncated, aad, ITERATIONS));

            // Case 10: thậm chí không đủ độ dài authentication tag.
            byte[] cutBelowTag = Arrays.copyOf(
                    valid.ciphertextAndTag(), TAG_BYTES - 1);
            Envelope tooShort = new Envelope(
                    valid.salt(), valid.iv(), cutBelowTag);
            expectRejected("cut-below-tag", IllegalArgumentException.class,
                    () -> decrypt(password, tooShort, aad, ITERATIONS));

            // Case 11: IV sai kích thước theo contract của envelope.
            byte[] shortIv = Arrays.copyOf(valid.iv(), IV_BYTES - 1);
            Envelope invalidIv = new Envelope(
                    valid.salt(), shortIv, valid.ciphertextAndTag());
            expectRejected("short-IV", IllegalArgumentException.class,
                    () -> decrypt(password, invalidIv, aad, ITERATIONS));

            // Chữ ký số: cùng message, một public key đúng và một sai.
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair correctPair = generator.generateKeyPair();
            KeyPair otherPair = generator.generateKeyPair();

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(correctPair.getPrivate());
            signer.update(message);
            byte[] signature = signer.sign();

            System.out.println("signature-valid=" +
                    verifySignature(
                            correctPair.getPublic(), message, signature));
            System.out.println("signature-wrong-public-key=" +
                    verifySignature(
                            otherPair.getPublic(), message, signature));

            byte[] modifiedMessage = message.clone();
            modifiedMessage[0] ^= 1;
            System.out.println("signature-changed-message=" +
                    verifySignature(
                            correctPair.getPublic(),
                            modifiedMessage, signature));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrongPassword, '\0');
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac CryptoNegativeTests.java
java CryptoNegativeTests
~~~

Stdout dự kiến với JDK 17 thông dụng (SunJCE/SunRsaSign, không tính thông báo của runtime bên ngoài):

~~~text
gcm-valid=true
wrong-AES-key=REJECTED
wrong-password=REJECTED
wrong-salt=REJECTED
wrong-iterations=REJECTED
wrong-IV=REJECTED
wrong-AAD=REJECTED
changed-ciphertext=REJECTED
changed-tag=REJECTED
cut-one-byte=REJECTED
cut-below-tag=REJECTED
short-IV=REJECTED
signature-valid=true
signature-wrong-public-key=false
signature-changed-message=false
~~~

Dữ liệu mã hóa dùng salt/IV ngẫu nhiên nên **ciphertext mỗi lần chạy khác nhau**; kết luận pass/reject vẫn ổn định. Tính không thể giả mạo của tag GCM là bảo đảm mật mã với xác suất cực nhỏ để một bản sửa ngẫu nhiên qua được xác thực; không tuyên bố đó là xác suất bằng đúng 0.

Các điểm cần hiểu đúng trong code:

- `Envelope` ở đây là record chứa mảng để **minh họa**; record mặc định **không tự defensive-copy mảng**. Test sử dụng `clone()` khi thay từng thành phần để fixture gốc không bị thay đổi. Production nên tạo immutable envelope có clone khi nhận và trả mảng, serialize có kiểm tra độ dài và xác thực metadata.
- `validateEnvelope` được gọi trước `Cipher.init` để ràng buộc **contract của format**: salt 16 byte, IV 12 byte, ciphertext kèm tag ít nhất 16 byte. Đây là yêu cầu của ứng dụng này, không phải khẳng định mọi provider GCM cấm IV 11 byte.
- Sai mật khẩu, salt hoặc iteration count đều khiến PBKDF2 cho kết quả khác; **không có cách đáng tin cậy chỉ nhìn `AEADBadTagException` để biết trường nào sai**.
- Mọi kết quả giải mã chỉ được trả về sau `doFinal`. Không ghi bản rõ ra file hay gửi cho bên khác trước khi xác minh tag.
- `expectRejected` không chấp nhận “bất kỳ exception nào là pass”: nếu code ném exception khác loại dự kiến, test thất bại và cần điều tra. Trong bộ test nhiều provider, nên định nghĩa tập exception có cơ sở từ đặc tả và adapter thực tế thay vì phụ thuộc thông điệp chuỗi của exception.
- Ví dụ dùng PBKDF2 và AES-128 để dễ chạy trên JDK 17, không định nghĩa chính sách mật mã tối ưu cho mọi hệ thống. Tối ưu iteration count hoặc chuyển KDF có khả năng chống GPU tốt hơn cần tuân chính sách bảo mật và định dạng tương thích của sản phẩm.

## 7. Sai mật khẩu: PBE, keystore và chính sách lỗi

### 7.1. PBKDF2 là quá trình dẫn xuất khóa

Trong cách dùng password-based encryption minh họa:

~~~text
password + salt + iterations + PRF + keyLength
                          │
                          ▼
                     derived AES key
                          │
                   AES-GCM decrypt
                          ▼
                   tag đúng / sai
~~~

Tham số của KDF phải **khớp chính xác** với lúc mã hóa:

- Password byte/char và quy tắc mã hóa ký tự. Khi thay đổi cách chuẩn hóa Unicode hoặc char encoding giữa các phiên bản ứng dụng, cùng mật khẩu hiển thị có thể dẫn đến key khác.
- Salt. Salt thường không bí mật, cần lưu kèm dữ liệu đã mã hóa và phải dùng lại đúng byte khi giải mã; salt mới dành cho lần **mã hóa mới**, không tự tạo lại để giải mã.
- PRF/KDF (ví dụ `PBKDF2WithHmacSHA256`), iteration count, key length.
- Thuật toán mã hóa, mode, padding, nonce/IV, tag length và AAD.

Đừng quy tất cả lỗi đầu vào thành “sai password” trước khi kiểm tra format: một record bị cắt, iteration không hợp lệ hoặc ciphertext bị sửa cũng gây lỗi. Ở tầng public, có thể giữ thông báo chung; ở tầng hỗ trợ vận hành, log correlation ID và loại lỗi đã được phân loại **không chứa password/salt/key/ciphertext**, đủ để tra soát sự cố.

**Password trong Java:** `PBEKeySpec` dùng `char[]` và có `clearPassword()`; sau test nên xóa mảng `char[]` của caller bằng `Arrays.fill` khi phù hợp. Việc xóa mảng không bảo đảm mọi bản sao tạm của mật khẩu trong JVM bị xóa; tránh in mật khẩu/derived key ra log. Không hard-code password và iteration count của ví dụ vào production.

### 7.2. Mật khẩu mở keystore khác password của dữ liệu

Một keystore JKS hoặc PKCS12 có thể có **store password** và **key-entry password** (tùy format/provider và cách tạo). Cần tách test:

- Sai store password lúc `KeyStore.load(inputStream, password)`.
- Store password đúng nhưng sai password khi `KeyStore.getKey(alias, keyPassword)`, nếu keystore hỗ trợ các mật khẩu riêng.
- Alias không tồn tại, alias trỏ đến certificate entry thay vì private key, hoặc key type không đúng.
- File keystore bị cắt/hỏng trước khi giải mã bất kỳ dữ liệu nghiệp vụ nào.
- Keystore hiện diện nhưng chain/chứng thư hết hạn hoặc key không được phép theo policy.

**Đừng assert chính xác một thông báo lỗi cho mọi JDK và keystore type.** `IOException`, `UnrecoverableKeyException` hoặc cause của chúng phụ thuộc bước xử lý và provider. Với một số keystore, đưa `null` thay mật khẩu làm thay đổi cơ chế xác minh integrity; **không** dùng `null` để “thử vượt” kiểm tra mật khẩu.

Đối với dịch vụ production, xác minh keystore có thể load đúng ngay khi khởi động, nhưng tuyệt đối không log mật khẩu, không dùng fallback key/keystore khi lỗi và không âm thầm tạo key mới làm mất khả năng giải mã dữ liệu cũ.

## 8. Tham số không khớp: phân loại đúng để tránh “sửa sai” nguy hiểm

| Tham số | Lỗi thử nghiệm | Ứng dụng nên xử lý |
| --- | --- | --- |
| `AES/GCM/NoPadding` | Bản mã được ghi với mode khác | Reject; không thử decrypt tuần tự bằng các mode yếu hoặc không xác thực |
| AES key length | Key 128/256 bit khác dữ liệu | Reject; không cắt/nối byte key để “khớp” |
| Tag length (bit) | Encrypt tag 128 bit, decrypt chỉ 96 bit | Cấu hình không khớp; thất bại tag hoặc lỗi parameter tùy provider |
| IV/nonce | Giá trị hoặc độ dài không đúng contract | Reject; không tạo IV mới khi decrypt |
| AAD | Tenant, file path, record ID, version khác | Tag mismatch; xác minh dùng **exact bytes** |
| PBKDF2 | Sai PRF, salt, iterations, key length | Không suy ra cùng key; tag fail hoặc lỗi dựng key |
| RSA-OAEP | MGF1 hash/label khác hoặc private key sai | Giải mã thất bại, thường `BadPaddingException` |
| RSA-PSS | Hash, MGF, salt length không phù hợp | Signature verification fail hoặc lỗi tham số |
| Key alias/version | Đọc nhầm key đã rotate | Chọn key bằng key ID có kiểm soát; không thử mọi key không giới hạn |
| Encoding | Base64 URL vs Base64 chuẩn, text encoding sai | Parser/decoder từ chối hoặc dữ liệu sau decode không xác thực |

**Đặc biệt với RSA-OAEP:** transformation name và `OAEPParameterSpec` phải thống nhất giữa hai phía, bao gồm MGF1 digest và label nếu sử dụng. Ví dụ "RSA/ECB/OAEPWithSHA-256AndMGF1Padding" phải được đối chiếu provider và bộ tham số thực tế; nếu yêu cầu giao thức chính xác, hãy đặt `OAEPParameterSpec` tường minh ở cả encrypt/decrypt và viết test vectors. Sai private key hoặc OAEP parameter thường biểu hiện như lỗi padding/decoding; không trả nguyên lỗi khác nhau ra API public.

**Với AES-CBC/PKCS5Padding:** sai key hoặc dữ liệu bị sửa thường gây `BadPaddingException`, nhưng **không phải lúc nào cũng vậy**. Một số bản mã bị thay đổi vẫn có padding hợp lệ và tạo ra plaintext vô nghĩa; CBC không cung cấp bảo đảm xác thực. Vì vậy không viết test “mọi lần đảo bit trong CBC đều phải ném `BadPaddingException`”. Để bảo vệ dữ liệu cũ dùng CBC, cần MAC phù hợp trên envelope theo thiết kế *encrypt-then-MAC*, hoặc nâng cấp sang AEAD; tuyệt đối không giao plaintext chưa xác thực cho nghiệp vụ.

**Với AES-CTR hoặc các stream mode không có MAC:** cắt/tráo bit có thể không tạo exception mà chỉ làm bản rõ ngắn hơn hoặc bị thay đổi. Kiểm thử sai dữ liệu chỉ có nghĩa khi hệ thống có cơ chế xác thực/kiểm tra độ dài đã được bảo vệ.

## 9. Dữ liệu bị cắt: parser, ciphertext, tag và stream

Dữ liệu bị cắt có thể xuất hiện bởi lỗi truyền tải, lưu file dang dở, giới hạn upload, bug serialize, đứt network connection hoặc tác động không đáng tin. Hãy tách bốn mức:

1. **Thiếu header:** chưa đủ magic/version/length fields. Parser phải trả lỗi định dạng, không chạm tới crypto.
2. **Thiếu salt/IV/metadata:** số byte nhỏ hơn contract của envelope. Từ chối trước KDF/decrypt.
3. **Thiếu ciphertext:** vẫn đủ tag ở cuối hay không? Nếu dữ liệu gốc có ciphertext dài nhưng bị cắt và tag thay đổi vị trí, AEAD tag sẽ không xác thực. Nếu chỉ còn dưới số byte tag tối thiểu, format validator có thể từ chối ngay.
4. **Thiếu phần cuối của nhiều bản ghi/chunk:** cần kiểm tra **tổng số chunk**, thứ tự, độ dài và tín hiệu kết thúc đã được xác thực; chỉ xác thực riêng từng chunk không đủ phát hiện một đuôi chunk bị xóa nếu giao thức không ràng buộc số lượng/điểm kết thúc.

### 9.1. Đừng dùng `available()` để xác định dữ liệu đã đọc hết

`InputStream.available()` chỉ cho biết ước lượng byte có thể đọc mà không block, **không phải kích thước file** hay số byte còn lại toàn stream. Để đọc trường cố định, dùng `DataInputStream.readFully(byte[])` hoặc `InputStream.readNBytes(n)` kèm kiểm tra đúng số byte; EOF sớm là lỗi.

Ví dụ **snippet parser** (cần có `DataInputStream in` và quota đầu vào đã được xác định; không phải chương trình Java độc lập):

~~~java
final int SALT_LENGTH = 16;
final int IV_LENGTH = 12;
final int MIN_TAG_LENGTH = 16;
final int MAX_BODY_LENGTH = 8 * 1024 * 1024;

int bodyLength = in.readInt();  // Big-endian, theo wire format đã chọn
if (bodyLength < MIN_TAG_LENGTH || bodyLength > MAX_BODY_LENGTH) {
    throw new java.io.IOException("Invalid ciphertext length");
}

byte[] salt = new byte[SALT_LENGTH];
byte[] iv = new byte[IV_LENGTH];
byte[] body = new byte[bodyLength];

in.readFully(salt); // EOFException nếu bị cắt
in.readFully(iv);
in.readFully(body);
~~~

Đây chỉ là minh họa **kiểm tra chiều dài**; một wire format hoàn chỉnh còn cần magic/version/KDF/algorithm ID/iteration count, giới hạn tổng file, cấm trailing bytes khi contract không cho phép, và kiểm tra tính xác thực metadata. Không cấp phát mảng theo số `bodyLength` do file khai báo trước khi kiểm tra hạn mức, nếu không attacker có thể gây cấp phát bộ nhớ quá lớn.

### 9.2. Kiểm thử cắt từng vị trí

Đối với một envelope hợp lệ `original`, nên chạy kiểm thử theo các offset có ý nghĩa:

- 0 byte; 1 byte; trước và sau magic/version.
- Trước, trong và sau trường length.
- Sau khi có một phần salt; một phần IV; đủ header nhưng thiếu body.
- Chỉ còn ít hơn tag length; cắt byte cuối cùng của tag.
- Cắt một byte trong ciphertext nhưng giữ hoặc di chuyển tag.
- Body đúng số byte nhưng bị đổi byte; footer/checksum không khớp.
- Có dữ liệu thêm phía sau nếu format nghiêm cấm trailing data.
- Dữ liệu công bố chiều dài rất lớn hoặc số lượng chunk bất thường để thử giới hạn bộ nhớ/CPU.

Không nên chỉ assert “có một exception”: hãy kiểm tra **không có file thành công**, không cập nhật trạng thái business và không trả một phần dữ liệu như kết quả hợp lệ.

### 9.3. Bẫy streaming với AEAD

Java `CipherInputStream` và `CipherOutputStream` có các ngữ nghĩa ngoại lệ riêng. Tài liệu JDK 17 cảnh báo các lớp này **có thể bắt và không ném lại** một số `BadPaddingException`/lỗi integrity trong quá trình giải mã. Vì vậy chúng không phù hợp làm nền tảng cho một kiểm tra **bắt buộc phải quan sát được thất bại xác thực** của GCM nếu không có lớp kiểm chứng khác.

Đối với đối tượng vừa kích thước, dùng `Cipher.doFinal(byte[])` và **chỉ** cung cấp plaintext sau khi thành công. Đối với dữ liệu lớn:

- Thiết kế giao thức chunked AEAD có nonce duy nhất cho từng chunk, AAD chứa vị trí và định danh đối tượng, cùng tổng số chunk hoặc final marker được xác thực.
- Hoặc decrypt vào tệp tạm bị cô lập, chỉ *publish/atomic rename* sau khi xác thực toàn bộ; loại bỏ tệp tạm khi thất bại.
- Không ghi byte plaintext chưa được xác thực vào output stream người dùng có thể đọc ngay.
- Không bỏ qua exception trong `close()`/`doFinal()` và không đưa dữ liệu lỗi trở thành cache/file hợp lệ.
- Test việc network stream kết thúc sớm, timeout giữa chunk, mất chunk cuối, trùng chunk và đảo thứ tự.

Một stream chỉ cung cấp byte cho ứng dụng từng đoạn không tự chứng minh đoạn cuối đã được xác thực; AEAD có thể đòi hỏi bước kiểm tra tag cuối cùng. **Không chấp nhận thành công trước khi kiểm tra xong toàn bộ thông điệp hoặc toàn bộ cấu trúc chunk đã xác thực.**

## 10. Sai khóa: phân biệt decrypt, chữ ký số, HMAC và key rotation

### 10.1. Symmetric decryption và public-key operations

| Phép toán | “Sai khóa” có nghĩa gì? | Kết quả kỳ vọng |
| --- | --- | --- |
| AES-GCM decrypt | Key AES đúng kích thước nhưng khác byte | Tag không khớp; không trả plaintext |
| AES key init | Key có kích thước hoặc thuật toán không được chấp nhận | Lỗi khởi tạo, thường `InvalidKeyException` |
| RSA-OAEP decrypt | Private key không tương ứng public key đã mã hóa | Lỗi OAEP/padding hoặc định dạng theo provider |
| RSA signature verify | Public key không tương ứng private key đã ký | `verify(...)` trả `false` hoặc lỗi cho input malformed |
| HMAC verify | MAC key sai | Tag tính lại không khớp; code ứng dụng phải so sánh an toàn |
| JAR signature | Signer certificate/chain khác kỳ vọng | Cryptographic verify/trust policy phải kiểm tra theo entry |
| Key wrapping | Wrapping/unwrapping key sai | Unwrap thất bại; không dùng key được unwrap nếu thiếu xác thực |

Lưu ý **RSA encryption khác RSA signature**: private key dùng decrypt, còn public key dùng verify chữ ký. Không đồng nhất việc `Signature.verify` trả `false` với `Cipher.doFinal` ném `BadPaddingException`.

### 10.2. Chữ ký sai: false hay exception?

`java.security.Signature.verify(...)` thường trả `false` khi thông điệp bị sửa hoặc dùng public key khác phù hợp về thuật toán/kích thước. Nhưng signature bị cắt, quá dài, sai encoding hoặc bộ tham số không đúng có thể khiến provider ném `SignatureException` hoặc `InvalidKeyException`; test phải xác định **hai outcome đều không được trở thành chấp nhận chữ ký**.

Đối với kiểm tra chữ ký trong hệ thống thực:

- Verify **đúng byte thông điệp đã ký**, kể cả charset, canonicalization, delimiter, file metadata cần được ký.
- Chọn algorithm/parameters theo format được phép; không để input tự chọn algorithm yếu hoặc bỏ verify.
- Verify cryptographic signature và kiểm tra **trust/ủy quyền signer** là hai bước khác nhau.
- Test khóa đúng nhưng message khác; message đúng nhưng public key khác; signature sửa; signature cắt; certificate hết hạn/không được tin cậy; key bị revoke theo chính sách.
- Không tự chuyển sang chế độ “không cần chữ ký” khi verify lỗi.

### 10.3. HMAC và so sánh tag

Nếu dùng HMAC thay vì AEAD, ứng dụng cần tự tính và so sánh tag bằng cách tránh lộ thời gian so sánh theo prefix. Trong Java, `MessageDigest.isEqual(byte[], byte[])` là tiện ích so sánh an toàn hơn `String.equals` hoặc vòng lặp dừng ở byte đầu sai. Tuy nhiên vẫn cần kiểm tra length/format trước, giới hạn đầu vào và bảo đảm dùng đúng context, key/version. Không tự rút ngắn tag không có cơ sở.

### 10.4. Key rotation và định danh key

Khi hệ thống rotate key, lỗi “sai khóa” có thể xuất phát từ metadata key ID sai, kho key thiếu version cũ, cache không nhất quán hoặc lỗi cấu hình giữa các node. Test các ca:

- Bản mã cũ còn key version hợp lệ và decrypt được trong thời hạn lưu trữ.
- Key ID không tồn tại hoặc không được phép → từ chối rõ ràng ở tầng nội bộ.
- Key ID chỉ vào key khác → xác thực thất bại; không chấp nhận plaintext.
- Không có key → không tự tạo key mới rồi trả dữ liệu giả/đã rỗng.
- Giới hạn số key được thử; không thử lần lượt vô hạn tất cả key và không log key bytes.

Key ID do envelope cung cấp là **dữ liệu chưa được tin cậy**; phải được kiểm soát theo policy truy cập key, chống đọc tùy ý alias/secret. Nếu có fallback nhiều key hợp lệ theo chính sách rotate, thứ tự/phạm vi phải cố định và đo lường được, không làm rò thông tin nhạy cảm cho caller.

## 11. Kiến trúc xử lý lỗi vận hành và logging

Sự khác biệt giữa lỗi xử lý đầu vào và lỗi crypto nội bộ cần được giữ đủ rõ cho vận hành, trong khi client chỉ thấy thông báo phù hợp. Một cách tổ chức:

~~~text
Input bytes / request
         │
         ▼
  Kiểm tra độ lớn, magic, version, length, cấu trúc
     │                                    │
  malformed                         well-formed
     │                                    │
     ▼                                    ▼
Reject format                   Resolve key theo policy
                                          │
                                  Cipher/Signature init
                                          │
                                   doFinal / verify
                               ┌──────────┴────────────┐
                             FAIL                    PASS
                               │                       │
                       Reject auth/trust       Publish plaintext/result
                       (no side effects)      (sau xác thực xong)
~~~

Ví dụ mapping:

| Failure class (nội bộ) | Ví dụ | Public response nên làm gì? |
| --- | --- | --- |
| `INVALID_FORMAT` | Header/length bị cắt, version lạ, field vượt quota | Báo đối tượng mã hóa không hợp lệ; không nêu byte nội bộ |
| `INVALID_CRYPTO_PARAMETER` | Tag bits không hỗ trợ, IV sai contract, algorithm bị cấm | Từ chối; log code cấu hình, không log secret |
| `AUTHENTICATION_FAILED` | Sai key/password/AAD, tag mismatch, ciphertext bị sửa | Thông báo chung; không khẳng định password sai |
| `SIGNATURE_REJECTED` | `verify` false hoặc signature malformed | Không chấp nhận artifact/operation |
| `KEY_UNAVAILABLE` | Key alias không tồn tại, key vault lỗi | Từ chối; metric và quy trình khôi phục riêng |
| `POLICY_REJECTED` | Key/trust chain/thuật toán không được phép | Từ chối; log lý do chính sách ở mức an toàn |
| `INTERNAL_FAILURE` | Provider/service lỗi bất ngờ | Không downgrade; yêu cầu can thiệp vận hành |

Một lớp service có thể trả một lỗi công khai chung cho vài trường hợp để tránh tạo oracle, nhưng không cần ép các metrics nội bộ thành một mã. Với CBC/PKCS#7 hoặc RSA padding, **phân biệt công khai quá chi tiết giữa lỗi padding và lỗi MAC/format** có thể tạo padding oracle. Thời gian xử lý và hành vi phụ cũng cần được đánh giá theo threat model; chỉ thay chuỗi thông báo chưa chắc loại bỏ oracle.

Logging có ích gồm: correlation ID, key **identifier đã được kiểm soát** (không phải key bytes), format version, loại lỗi đã chuẩn hóa, JDK/provider, số byte đầu vào, elapsed time theo mức cần thiết. Không log plaintext, password, secret key, toàn bộ ciphertext, nội dung tag hoặc stack trace cho client. Lưu log đủ lâu theo chính sách và giới hạn người được xem.

## 12. Thiết kế test tự động đáng tin cậy

Một bộ test tốt nên gồm ba tầng:

**Unit test**: kiểm tra validator, parser, chiều dài và mapping lỗi; không cần JCE cho mỗi lỗi định dạng. Dùng dữ liệu fixture nhỏ, xác định rõ tag length và giới hạn size.

**Integration test JCA/provider**: dùng key thật trong test, encrypt → decrypt đúng, sau đó mutate một phần và kỳ vọng tag reject; kiểm tra algorithm/parameter support trên JDK được triển khai. Không phụ thuộc vào chuỗi thông báo bên trong `AEADBadTagException`.

**End-to-end test**: đưa file mã hóa bị cắt, sai key version hoặc AAD tenant khác qua đường xử lý thật. Kiểm tra không ghi file đích, transaction không commit, không trả HTTP 200, không gửi sự kiện “processed”, temp file được dọn và audit/metric đủ dùng.

Các nguyên tắc test quan trọng:

1. **Mỗi case có baseline:** một lần decrypt đúng trước khi thay đúng một yếu tố. Tránh lỗi fixture phá hỏng tất cả test và khiến ta tưởng đã chặn được tampering.
2. **Assert hành vi ngoài exception:** kiểm tra plaintext đã được so đúng ở positive control; ở negative case không có tác dụng phụ/đầu ra được tin cậy.
3. **Không hard-code ciphertext ngẫu nhiên trong stdout:** dùng `SecureRandom` trong mỗi lần encrypt; kiểm tra quan hệ pass/reject. Với test vectors chuẩn cần byte cố định, dùng vector công khai đã được kiểm chứng và không tái sử dụng nonce cố định trong hoạt động production.
4. **Test theo profile cấu hình:** thuật toán, provider, JDK, keystore type, header version, encoding và policy có thể khác nhau giữa môi trường.
5. **Test tập trung vào ranh giới:** offset 0, 1, tag length ± 1, body length 0/1/max/max+1, IV length ± 1, iteration count 0/1/max/max+1.
6. **Không thiết kế exception oracle:** khi public API nhận dữ liệu giả, thông báo và side effect phải nhất quán theo chính sách.
7. **Không chấp nhận fallback âm thầm:** lỗi GCM không thể biến thành “decrypt không cần tag”, lỗi provider không dẫn tới thuật toán yếu, lỗi keystore không sinh key mới rồi ghi đè dữ liệu.
8. **Tách lỗi xác thực với lỗi phân quyền:** decrypt thành công vẫn chưa có nghĩa caller được đọc nội dung; verify chữ ký thành công vẫn cần trust/policy.

Trong dự án JUnit 5, có thể viết các assertion theo mẫu sau, khi đã có hàm và fixture tương ứng (đoạn mã **không độc lập**, tên service/fixture cần định nghĩa trong dự án):

~~~java
// JUnit 5 pseudocode: không phải một chương trình runnable độc lập.
assertArrayEquals(originalPlaintext,
        cryptoService.decrypt(validEnvelope, correctKey));

assertThrows(AEADBadTagException.class,
        () -> cryptoService.decrypt(validEnvelope, wrongKey));

assertThrows(InvalidEnvelopeException.class,
        () -> cryptoService.decrypt(truncatedHeader, correctKey));

assertFalse(signatureService.verify(
        originalMessage, signature, unrelatedPublicKey));
~~~

Không nên *mock* `Cipher.doFinal` chỉ để khiến test trả một exception mong muốn rồi xem đó là chứng minh cryptography an toàn. Cần ít nhất một integration test thực với provider mà sản phẩm dùng.

## 13. Checklist review trước khi phát hành

- [ ] Luồng hợp lệ giải mã chính xác byte gốc; có known-answer test hoặc positive control được bảo trì.
- [ ] Sai key đúng kích thước được từ chối; key sai kích thước bị validator/provider từ chối.
- [ ] Sai password, salt, iteration, KDF ID và key version được thử độc lập.
- [ ] IV sai byte, IV sai length, AAD sai và tag sai đều bị từ chối đúng thời điểm.
- [ ] Ciphertext bị cắt ở header, body, tag và file cuối được kiểm thử; parser giới hạn length trước cấp phát.
- [ ] Thông điệp đã ký thay đổi và public key không tương ứng khiến verify fail; signer trust kiểm tra riêng.
- [ ] Không dựa vào `BadPaddingException` để chứng minh tính toàn vẹn của CBC không có MAC.
- [ ] Với AEAD, không xuất bản plaintext trước khi kiểm tra tag hoàn tất; rà soát việc dùng `CipherInputStream`/`CipherOutputStream`.
- [ ] Không bỏ qua lỗi `doFinal`, không swallow exception trong stream/async callback, không tự retry với thuật toán yếu.
- [ ] Kiểm soát key ID, rotation, quyền truy cập kho khóa và tài khoản dịch vụ.
- [ ] Public error không làm lộ khác biệt bí mật; logging và metric đủ phân biệt lỗi kỹ thuật.
- [ ] Trường hợp lỗi không làm rò file tạm, không cập nhật trạng thái thành công và không phát sinh side effect.
- [ ] Test chạy trên JDK/provider/keystore/dialect thực tế; các quy tắc nonce, tag, KDF và AAD được tài liệu hóa.

## 14. Tự kiểm tra

**1. Nếu nhập sai password nhưng AES-GCM ném `AEADBadTagException`, có thể kết luận chắc chắn password sai không?** Không. Cùng lỗi có thể đến từ sai key, salt, iteration, IV, AAD hoặc ciphertext/tag bị hỏng. Cần dựa vào bối cảnh vận hành và kiểm tra envelope, không đoán một nguyên nhân tuyệt đối từ exception.

**2. Vì sao cắt đúng một byte ở đuôi bản mã GCM có thể gây lỗi tag dù file vẫn còn hơn 16 byte?** GCM xác minh tag trên đúng thông điệp; khi byte bị cắt, cách tách ciphertext/tag hoặc nội dung tag nhận được không còn khớp, nên xác thực thất bại.

**3. Nếu decrypt AES-CBC sai key nhưng không ném exception, đó có phải bug của JCE?** Không nhất thiết. CBC với padding không có cơ chế xác thực; một plaintext rác vẫn có thể có padding hợp lệ. Cần AEAD hoặc MAC đúng thiết kế để kiểm tra authenticity.

**4. Chữ ký số sai public key luôn phải ném exception?** Không. Với chữ ký đúng cấu trúc, `Signature.verify(...)` thường trả `false`; malformed signature, key hoặc parameter không hợp lệ có thể ném exception.

**5. Khi GCM tag chưa xác minh xong, có thể stream một phần plaintext ra response để giảm latency?** Không nên. Không để caller sử dụng plaintext chưa được xác thực; chọn buffer, file tạm hoặc thiết kế chunk AEAD có ràng buộc toàn bộ thông điệp.

**6. Vì sao không lấy `InputStream.available()` để kiểm tra file đã được đọc đủ?** Nó không đại diện cho tổng số byte còn lại của stream. Dùng đúng length được kiểm soát, đọc đủ byte và kiểm tra EOF/trailing bytes.

**7. Nếu bản mã ghi key ID không tồn tại, có nên thử hết mọi key trong kho không?** Không theo mặc định. Cần chính sách key rotation có phạm vi cố định, giới hạn công việc và không tạo ra key-oracle hoặc lộ thông tin về kho khóa.

**8. Có thể coi mọi test negative pass chỉ vì ném bất kỳ exception nào không?** Không. Test phải phân biệt lỗi format, lỗi configuration, lỗi auth và lỗi nghiệp vụ; đồng thời xác minh không trả plaintext và không có side effect.

## Tài liệu chính thức để đối chiếu

- Oracle JDK 17 — `javax.crypto.Cipher` (AEAD, GCM, AAD, yêu cầu uniqueness của IV): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html
- Oracle JDK 17 — `javax.crypto.AEADBadTagException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html
- Oracle JDK 17 — `javax.crypto.spec.GCMParameterSpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
- Oracle JDK 17 — `javax.crypto.spec.PBEKeySpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html
- Oracle JDK 17 — `java.security.Signature`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html
- Oracle JDK 17 — `javax.crypto.CipherInputStream` (lưu ý khả năng nuốt lỗi integrity): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/CipherInputStream.html
- Oracle JDK 17 — `java.security.KeyStore`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html
- NIST SP 800-38D — Galois/Counter Mode (GCM) và GMAC: https://csrc.nist.gov/pubs/sp/800/38/d/final


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-009">22.9 Đo hiệu năng trong khi vẫn bảo toàn nonce, chi phí dẫn xuất và phạm vi dùng khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 22.9 Đo hiệu năng trong khi vẫn bảo toàn nonce, chi phí dẫn xuất và phạm vi dùng khóa

> **Mục tiêu:** đo tốc độ các thao tác mật mã Java một cách có thể kiểm chứng mà **không** dùng lại nonce với cùng khóa, **không** hạ chi phí dẫn xuất khóa chỉ để được số đo đẹp và **không** mở rộng quyền/phạm vi dùng khóa vượt thiết kế.

## 1. Ba bất biến bảo mật phải giữ nguyên khi benchmark

Benchmark mật mã là chạy thật những thao tác có thể tiêu thụ nonce, khóa, tài nguyên CPU/bộ nhớ hoặc quyền trên HSM. Không thể coi vòng lặp đo là ngoại lệ của hệ thống bảo mật.

| Ràng buộc | Bất biến phải giữ | Sai lầm làm kết quả nhanh nhưng không còn an toàn |
| --- | --- | --- |
| **Nonce / IV** | Với AES-GCM, cặp **(key, IV)** dùng để **mã hóa** phải duy nhất theo quy tắc của hệ thống, kể cả warmup, retry và chạy đa luồng | Dùng một IV cố định để loại chi phí sinh IV; reset bộ đếm trước mỗi lượt đo |
| **Chi phí dẫn xuất** | Số vòng lặp PBKDF2 hoặc tham số bộ nhớ/thời gian Argon2id phải phản ánh cấu hình bảo mật đang đánh giá | Giảm từ cấu hình triển khai xuống một vài vòng rồi ngoại suy tuyến tính tùy tiện |
| **Phạm vi khóa** | Mỗi khóa phải được dùng đúng mục đích, tenant, domain và giới hạn vòng đời/khối lượng | Dùng một khóa benchmark chung cho nhiều tenant, luồng, node hoặc mục đích mà không chia nonce namespace |
| **Tính đúng đắn** | Xác thực tag, AAD, khóa và dữ liệu sau giải mã; nếu thao tác lỗi thì không tính là thành công | Chỉ đo `Cipher.init` mà không `doFinal`; bỏ qua `AEADBadTagException` hoặc chỉ kiểm tra output length |
| **Môi trường** | Ghi rõ JDK, provider, CPU, native/HSM path, số luồng, kích thước dữ liệu và ranh giới thời gian | So sánh số đo giữa provider khác nhau rồi kết luận một thuật toán tuyệt đối nhanh hơn |

Một hệ thống mã hóa tốt luôn đạt **đúng + an toàn + đủ hiệu năng**. Nếu một “tối ưu hóa” phá bất biến, benchmark đó không đại diện cho phương án có thể triển khai.

## 2. Xác định chính xác ta đang đo gì

Các thao tác có chi phí và tính chất rất khác nhau:

- **Symmetric AEAD:** `AES/GCM/NoPadding` mã hóa/giải mã, xác thực tag, xử lý AAD, sinh/quản lý IV, cấp phát buffer, khởi tạo `Cipher`.
- **Dẫn xuất từ mật khẩu:** `PBKDF2WithHmacSHA256` hoặc Argon2id/scrypt dùng CPU và có thể dùng nhiều bộ nhớ **có chủ đích**. Không phải biến thể của mã hóa AES, không thể thay bằng SHA-256 một lần.
- **Dẫn xuất từ key material ngẫu nhiên:** HKDF (phân tách khóa theo purpose/context) khác KDF *chậm* để chống đoán mật khẩu. Không dùng HKDF trực tiếp thay PBKDF2/Argon2id cho mật khẩu ít entropy.
- **Asymmetric/HSM:** thao tác ký hoặc giải mã khóa trong token có chi phí phiên, giao tiếp native, mạng và hàng đợi; không thể suy ra từ benchmark AES chạy hoàn toàn trong JVM.

Nên có **hai lớp đo riêng**:

1. **Primitive/provider benchmark:** đo đúng API và phần chi phí muốn cô lập; ví dụ chỉ mã hóa dữ liệu trên `Cipher` đã chọn, còn cấp khóa diễn ra ngoài vùng đo. Phải vẫn bảo toàn IV.
2. **End-to-end benchmark:** đo toàn bộ đường đi mà ứng dụng thực chạy: tạo/đăng ký nonce, truy vấn key ID, đọc cấu hình, xác thực, mã hóa, đóng gói IV+ciphertext+tag, I/O, queue, RPC HSM, giải mã và kiểm tra tag.

Nếu sản phẩm chỉ dẫn xuất khóa **một lần khi mở khóa** rồi mã hóa nhiều bản ghi, không cộng PBKDF2 vào từng bản ghi trong báo cáo throughput. Nếu sản phẩm dẫn xuất cho **mỗi yêu cầu đăng nhập**, bắt buộc tính chi phí và tải đồng thời của từng lần xác thực.

### Chỉ số cần lưu

| Chỉ số | Cách hiểu |
| --- | --- |
| `ops/s = số thao tác thành công / số giây` | Tốc độ hoạt động dưới tải xác định; ghi rõ số thread và provider |
| `MiB/s = bytes plaintext xử lý / 2^20 / giây` | Thông lượng dữ liệu, không được gọi nhầm là tốc độ mã hóa *raw* nếu còn setup và cấp phát |
| `latency p50/p95/p99` | Phân bố thời gian từng request; lấy nhiều mẫu trong workload thật, không suy p99 từ ba mẫu |
| `CPU%, allocations, GC, peak RSS` | Dùng khi so sánh cấu hình KDF, buffer và concurrency |
| `failure rate, retry, token busy/queue` | Không để các lần thất bại làm “nhanh” giả tạo |

Trong Java, `System.nanoTime()` phù hợp để đo khoảng thời gian trôi qua; không dùng `currentTimeMillis()` cho vòng lặp ngắn. Tránh in log, ghi file hay làm I/O bên trong vòng đo nếu không chủ đích đo chúng. Warmup JVM/JIT và tách nhiều lượt/fork để tránh kết quả phụ thuộc giai đoạn khởi động. Đối với microbenchmark nghiêm túc, dùng [OpenJDK JMH](https://github.com/openjdk/jmh/); ví dụ tự chạy ở dưới chỉ để minh họa bất biến.

## 3. Nonce khi đo AES-GCM: không có “ngoại lệ test”

`AES/GCM/NoPadding` là AEAD: bảo vệ tính bí mật **và** xác thực ciphertext + AAD. `GCMParameterSpec(128, iv)` yêu cầu tag 128 bit và IV do chương trình cấp. **Lặp lại IV với cùng AES key cho hai lần mã hóa GCM có thể phá nghiêm trọng bảo mật**, bao gồm tính bí mật và tính xác thực; Java API yêu cầu đổi IV khi khởi tạo lại để mã hóa.

```text
ĐÚNG (một key K; chỉ xét encryption):
warmup:  (K, IV-0), (K, IV-1), ... (K, IV-999)
measure: (K, IV-1000), (K, IV-1001), ...

SAI:
warmup:  (K, IV-0) x 1000 lần
measure: (K, IV-0) x 5000 lần

CŨNG SAI:
thread A: (K, counter=1)
thread B: (K, counter=1)  // cả hai tạo trùng IV
```

Có hai nhóm chiến lược IV cho GCM mà thiết kế cần chọn và duy trì nhất quán theo khóa:

- **IV đếm có cấu trúc (deterministic construction):** kết hợp phần định danh cố định duy nhất cho phân vùng với counter không lặp. Với **một key tươi chỉ dùng trong một chương trình benchmark**, có thể dùng IV 96 bit gồm tiền tố 32 bit và counter 64 bit; không reset counter giữa warmup và measurement. Cần dừng trước khi counter tràn.
- **IV sinh bằng CSPRNG** khi chính sách chấp nhận random construction: đảm bảo nguồn ngẫu nhiên phù hợp, theo dõi rủi ro va chạm tăng theo số lượt dùng cùng khóa và các giới hạn của chuẩn/chính sách. Một IV ngẫu nhiên 96 bit không phải phép bảo đảm toán học tuyệt đối không trùng sau vô hạn lượt.

**Khi có nhiều node dùng cùng khóa**, tiền tố cho mỗi node/phân vùng phải được phân phối **không trùng**, và counter phải có trạng thái bền vững/atomic tương ứng. Không thể lấy `new SecureRandom().nextInt()` cho prefix của từng node dùng chung key rồi tuyên bố chắc chắn không va chạm. Các giải pháp thực tế gồm phân hoạch namespace nonce được quản trị, bộ cấp counter tập trung, hoặc khóa dẫn xuất riêng cho từng node/purpose (với thiết kế key separation đúng chuẩn). Nếu node khởi động lại và có thể reset counter dưới cùng key, phải dừng mã hóa hoặc đổi key trước khi tạo nonce mới.

**IV thường không cần bí mật**: có thể lưu/cấp kèm ciphertext; yêu cầu là tính **duy nhất với khóa** theo thiết kế, không phải giấu IV. Với AES-GCM, lưu IV cùng tag và ciphertext, đồng thời giữ đúng `keyId`, version/algorithm và AAD cần thiết để giải mã.

Một quy tắc vận hành quan trọng là **warmup và run thử vẫn được tính vào quota IV, số lần mã hóa và ngân sách sử dụng key**. Một lần retry không được mã hóa lại bằng IV cũ cùng khóa, kể cả khi request trước bị timeout và không rõ đã hoàn thành hay chưa. **Decrypt lại** cùng bản mã/IV để kiểm tra là chuyện khác với **encrypt lại** cùng (key, IV).

## 4. Giữ nguyên chi phí dẫn xuất khóa

KDF chậm xử lý mật khẩu là biện pháp tăng chi phí dò đoán offline. **Tốc độ KDF thấp hơn không mặc nhiên là lỗi cần tối ưu**: giảm vòng lặp/giảm bộ nhớ có thể làm attacker thử nhiều ứng viên hơn.

- Với `PBKDF2WithHmacSHA256`, tham số chính là số vòng lặp và chiều dài đầu ra. Trong ví dụ Java 17+, ta dùng **600.000 vòng** để minh họa cấu hình có chi phí thực. Đây là mức được OWASP Password Storage Cheat Sheet khuyến nghị cho PBKDF2-HMAC-SHA256 tại thời điểm biên soạn; phải đối chiếu chính sách hiện hành và thử tải trên hệ thống đích.
- Với **Argon2id**, cần đo đồng thời bộ nhớ (`m`), số pass (`t`), parallelism (`p`), throughput và áp lực RAM khi nhiều request đồng thời. Java SE 17 không tự cung cấp Argon2id như một `SecretKeyFactory` chuẩn; khi cần, dùng thư viện đã kiểm tra và kiểm soát provider/native dependencies.
- **Salt** cho thao tác dẫn xuất từ mật khẩu cần là giá trị thích hợp, duy nhất theo từng credential/đối tượng. Ví dụ dùng 16 byte mới cho mỗi lần dẫn xuất *độc lập*. Khi **xác minh lại cùng credential hoặc mở lại cùng dữ liệu**, phải lấy **salt và work factor đã lưu của chính credential/bản ghi đó**; không tạo salt mới rồi mong thu được cùng derived key.
- Không đưa password thật, session token, key sản phẩm hoặc salt của khách hàng vào log/fixture. Một mật khẩu giả cố định dùng làm **dữ liệu benchmark** không phải credential để triển khai. Giữ password trong `char[]` và xóa khi có thể; hiểu rằng Java không thể bảo đảm xóa mọi bản sao trung gian.
- Tạo `SecretKeyFactory` trước khoảng đo nếu chỉ cần chi phí `generateSecret`, và công bố chi phí này đã **không** bao gồm tạo factory. Nếu đang mô phỏng login request thực, phải đo cả các thành phần được thực thi trong từng request.

**Không trộn chi phí:** AES đã nhận `SecretKey` nhị phân thì không cần PBKDF2. Trường hợp mật khẩu dẫn xuất key mã hóa: đo đường mở khóa KDF riêng, sau đó đo AEAD với key đã dẫn xuất; khi báo cáo end-to-end phải ghép theo **tần suất thực tế** của thao tác. Không tối ưu bằng cách cache trực tiếp mật khẩu lâu dài hoặc chia sẻ derived key giữa các tenant không liên quan.

## 5. Phạm vi sử dụng khóa (key scope) quyết định ý nghĩa của số đo

Một khóa phải có **mục đích, chủ sở hữu, key ID/version, thuật toán, thời hạn và ngân sách sử dụng**. Nonce namespace luôn được xét **trong cùng một key**; tách tenant trên giấy tờ bằng AAD không tạo ra tính duy nhất IV giữa hai tenant vẫn dùng chung key.

Ví dụ quản trị:

| Phạm vi | Thiết kế phù hợp | Lỗi cần tránh |
| --- | --- | --- |
| `tenant A` và `tenant B` | Tạo key riêng hoặc subkey được dẫn xuất/phân tách đúng domain; mỗi key có nonce allocator riêng | Cùng key, hai worker cùng reset counter và trùng IV |
| `purpose=encrypt` / `purpose=mac` | Dùng key riêng theo purpose, hoặc key separation với HKDF từ master key entropy cao | Lấy cùng một byte key cho mọi primitive mà không có thiết kế |
| Key version V1 → V2 | Phát hành key ID/version rõ, theo dõi ciphertext cũ và chỉ mã hóa mới bằng V2 | Đổi key nhưng đánh mất metadata, khiến decrypt không xác định key |
| Nhiều tiến trình / restart | Counter bền vững, cấp phát dải không trùng hoặc per-process subkey, chống dùng lại sau crash | Reset counter về 0 với key cũ |
| Đo nhiều kích thước payload | Tính cả số message và tổng dữ liệu/blocks dưới mỗi key theo giới hạn thuật toán/chính sách | Giữ một key mãi mãi để chạy test vô hạn |

NIST SP 800-38D đặt các yêu cầu/giới hạn cho việc dùng GCM, bao gồm cấu trúc IV và số lần sử dụng dưới khóa. Khi benchmark tải lớn phải xem **tổng lưu lượng theo key trên tất cả tiến trình**, không chỉ vòng lặp hiện tại. Xoay vòng key theo giới hạn được phê duyệt; không coi “key rotation làm benchmark phức tạp” là lý do bỏ qua.

**AAD để ràng buộc ngữ cảnh:** ví dụ AAD có thể mã hóa tenant ID, mục đích, loại bản ghi, phiên bản giao thức (một biểu diễn bytes ổn định/canonical). Khi giải mã phải cung cấp đúng AAD đã mã hóa; sai AAD dẫn đến xác thực GCM thất bại. AAD giúp phát hiện tráo ngữ cảnh nhưng **không thay thế** key separation hoặc nonce uniqueness.

## 6. Ví dụ hoàn chỉnh Java 17+: đo AES-GCM và PBKDF2 độc lập

Chương trình sau đáp ứng các điều kiện:

1. Sinh **key AES-256 mới** mỗi lần khởi chạy. Chỉ có **một luồng** dùng key đó.
2. Tạo IV GCM 12 byte từ prefix và counter tăng duy nhất, **kể cả warmup**. Prefix là giá trị ngẫu nhiên để minh họa; trong chương trình này IV không trùng vì counter một chiều và key chỉ dùng trong lần chạy. **Đừng sao chép bộ cấp IV đơn tiến trình này sang hệ phân tán với key dùng chung.**
3. Đo toàn bộ `nonce() + Cipher.getInstance + init + updateAAD + doFinal + allocations`, chưa tính tạo key, tạo payload hay giải mã kiểm chứng ở cuối. Đây là **full-call cost trong mẫu**, không phải tốc độ primitive tối thiểu.
4. Đo KDF với 600.000 vòng, salt 16 byte sinh **ngoài khoảng thời gian** cho mỗi mẫu để tập trung vào `derive`. Một warmup và ba mẫu là **minh họa**, không đủ lập p95/p99 hay kết luận hiệu năng sản xuất.
5. Kiểm tra giải mã GCM đúng dữ liệu/IV/AAD và tiêu thụ kết quả qua biến `volatile` để giảm nguy cơ phép tính bị loại bỏ.

Tạo file **`CryptoPerfSafe.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public final class CryptoPerfSafe {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile int sink;

    private static byte[] nonce(int prefix, long counter) {
        if (counter < 0) {
            throw new IllegalStateException("Nonce counter exhausted");
        }
        return ByteBuffer.allocate(12).putInt(prefix).putLong(counter).array();
    }

    private static byte[] encrypt(SecretKey key, byte[] iv,
                                  byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(SecretKey key, byte[] iv,
                                  byte[] aad, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private static void measureGcm() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, RANDOM);
        SecretKey key = generator.generateKey(); // Fresh key per program run.
        byte[] data = new byte[4096];
        RANDOM.nextBytes(data);
        byte[] aad = "tenant=demo|purpose=archive|v=1"
                .getBytes(StandardCharsets.UTF_8);

        int prefix = RANDOM.nextInt();
        long counter = 0; // One sequence for warmup AND measurement.
        int checksum = 0;
        final int warmup = 1_000;
        final int count = 5_000;
        for (int i = 0; i < warmup; i++) {
            byte[] ct = encrypt(key, nonce(prefix, counter++), aad, data);
            checksum ^= ct[0] & 0xff;
        }

        byte[] lastIv = null;
        byte[] lastCt = null;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            lastIv = nonce(prefix, counter++);
            lastCt = encrypt(key, lastIv, aad, data);
            checksum ^= lastCt[0] & 0xff;
        }
        long elapsed = System.nanoTime() - start;

        boolean verified = Arrays.equals(
                data, decrypt(key, lastIv, aad, lastCt));
        if (!verified) {
            throw new SecurityException("GCM round trip failed");
        }
        sink = checksum; // Keep generated results observable.
        double seconds = elapsed / 1_000_000_000.0;
        double mibPerSec = (count * (double) data.length)
                / (1024.0 * 1024.0 * seconds);
        double avgUs = elapsed / (count * 1_000.0);
        System.out.printf(Locale.ROOT,
                "AES-GCM: ops=%d, throughput=%.2f MiB/s, avg=%.2f us, verified=%b%n",
                count, mibPerSec, avgUs, verified);
    }

    private static byte[] derive(SecretKeyFactory factory, char[] password,
                                 byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void measureKdf() throws GeneralSecurityException {
        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        char[] demoPassword = "synthetic-benchmark-password".toCharArray();
        final int iterations = 600_000;
        final int samples = 3;
        long[] nanos = new long[samples];
        int checksum = 0;
        try {
            byte[] warmSalt = new byte[16];
            RANDOM.nextBytes(warmSalt);
            byte[] warm = derive(factory, demoPassword, warmSalt, iterations);
            Arrays.fill(warm, (byte) 0);

            for (int i = 0; i < samples; i++) {
                byte[] salt = new byte[16]; // Unique for this derivation.
                RANDOM.nextBytes(salt);
                long start = System.nanoTime();
                byte[] result = derive(factory, demoPassword, salt, iterations);
                nanos[i] = System.nanoTime() - start;
                checksum ^= result[0] & 0xff;
                Arrays.fill(result, (byte) 0);
            }
        } finally {
            Arrays.fill(demoPassword, '\0');
        }
        sink ^= checksum;
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT,
                "PBKDF2-HMAC-SHA256: iterations=%d, samples=%d, median=%.2f ms%n",
                iterations, samples, nanos[samples / 2] / 1_000_000.0);
    }

    public static void main(String[] args) throws Exception {
        measureGcm();
        measureKdf();
    }
}
```

Biên dịch/chạy bằng JDK 17+ (không cần thư viện bên ngoài):

```bash
javac CryptoPerfSafe.java
java CryptoPerfSafe
```

Ví dụ **dạng output**; các số tốc độ/phần trăm thời gian **thay đổi theo máy** nên không cung cấp một giá trị cố định:

```text
AES-GCM: ops=5000, throughput=<giá trị đo> MiB/s, avg=<giá trị đo> us, verified=true
PBKDF2-HMAC-SHA256: iterations=600000, samples=3, median=<giá trị đo> ms
```

Dạng trên là **minh họa chỗ đặt số đo**, không phải stdout byte-for-byte; khi chạy thật, `printf` thay các placeholder thành số thập phân. Mẫu Java đã được biên dịch và chạy thành công với JDK 24; nó dùng API có từ Java 17. Không chép kết quả máy thử vào SLA khi chưa đo trên máy triển khai.

**Đọc từng điểm trong mã:**

- `counter` bắt đầu 0 và chỉ tăng; warmup dùng 0–999, measurement dùng 1000–5999. Với một key mới duy nhất, đây là 6.000 IV khác nhau; khóa/IV không bị đặt lại ở giữa.
- `nonce(prefix, counter)` dùng `ByteBuffer.putInt` (32 bit) rồi `putLong` (64 bit) tạo 96 bit. Prefix không phân phối qua cluster ở ví dụ này.
- `encrypt` dùng tag 128 bit, xử lý AAD **trước** plaintext, rồi `doFinal` tạo ciphertext cùng tag. `decrypt` dùng đúng IV và AAD để kiểm tra round-trip.
- `mibPerSec` tính bytes **plaintext** 4096 × 5000 chia thời gian; nó bao gồm setup/cấp phát/IV của mỗi operation. Chỉ số `avg` là trung bình trong vòng đó, không phải p95.
- `measureKdf` tạo một salt ngẫu nhiên 16 byte mới mỗi mẫu, đo `SecretKeyFactory.generateSecret` qua `PBEKeySpec` và xóa các bản sao byte khóa đã xuất. Chi phí tạo `SecretKeyFactory` và `SecureRandom.nextBytes(salt)` nằm **ngoài** khoảng đo.
- Không đưa số đo nhanh của GCM đi thay cho thời gian cấp khóa PBKDF2. Không dùng key benchmark trong sản phẩm: chương trình chủ đích không lưu IV/ciphertext/key sau khi kết thúc.

### Tại sao không dùng cùng IV để kết quả ổn định?

Thay vòng lặp bằng `encrypt(key, FIXED_IV, ...)` mỗi lần sẽ khiến kết quả **không còn đo một quy trình GCM an toàn**. JDK cũng có thể phát hiện/từ chối reuse trên cùng một `Cipher` instance, nhưng tạo instance mới không biến reuse thành an toàn. Giữ đúng IV allocator trong benchmark, rồi nếu muốn cô lập chi phí của nó hãy đo thành mục riêng và trình bày rõ ranh giới.

### Tại sao không giảm PBKDF2 xuống 1.000 vòng?

Số đo khi đổi work factor không còn thuộc cấu hình bảo mật cần kiểm định. Chọn mức theo threat model/chính sách và đo CPU/RAM/độ trễ, rồi **thiết kế sức chứa** (rate-limit đăng nhập, giới hạn concurrency, queue, scale-out, caching những dữ liệu *không nhạy cảm* phù hợp), thay vì làm suy yếu KDF để được throughput cao.

## 7. Tổ chức benchmark production-grade

Để so sánh phiên bản, provider và môi trường ổn định hơn, chuyển các phương thức mã hóa/dẫn xuất sang **OpenJDK JMH** và có bài đo end-to-end độc lập.

**Fixture/state:**

- Tạo key và dữ liệu thử hợp lệ theo đơn vị phù hợp (fork/thread/iteration). Nếu JMH chạy nhiều thread và dùng chung key, phải có bộ cấp IV đồng bộ toàn bộ thread; thuận tiện hơn là mỗi thread có key/subkey riêng và counter riêng, nhưng cần ghi rõ khác biệt này trong kịch bản đo.
- Thống nhất việc **nonce generation nằm trong hay ngoài timed body**. Không tạo một IV duy nhất trong `@Setup` rồi dùng lặp cho hàng triệu lời gọi mã hóa.
- Đo các payload (ví dụ 1 KiB, 4 KiB, 1 MiB), số thread, provider, kiểu buffer và bộ nhớ, có đủ warmup, iteration và JVM forks. Không giả định các lần đo cùng JDK nhưng khác provider là so sánh tương đương.
- Dùng JMH `Blackhole` hoặc tiêu thụ kết quả đúng cách, kiểm tra giải mã và phát hiện tag sai trong bài test riêng, ghi nhận số lần exception. Một thao tác thất bại không được coi là successful operation.
- Với PBKDF2/Argon2id, đo ở cấu hình triển khai thực và concurrency thật. Có thể đo nhiều cấu hình để phân tích trade-off, nhưng **không hợp thức hóa** cấu hình thấp hơn yêu cầu bảo mật.
- Với HSM/PKCS#11, chạy tải có kiểm soát trên thiết bị/lab và ghi rõ session pool, network latency, quota cơ chế và key policy. Không ngoại suy từ `SunJCE` software sang provider phần cứng.

**Quy trình đo lặp lại được:**

1. Chốt giả thuyết (đang tối ưu encrypt latency hay throughput login?) và mô tả chính xác luồng được tính phí.
2. Ghi môi trường: JDK + provider + OS/CPU/AES acceleration, thuật toán + tag size, kích thước payload + AAD, key/salt/IV strategy, threads/forks, thời lượng, resource limits.
3. Viết **security assertions** trước: `(key, IV)` không trùng, key đúng tenant/purpose, tag/AAD round-trip thành công, KDF parameters không giảm, không vượt quota key.
4. Warmup, rồi đo nhiều iteration/fork có tách phase. Ghi thời gian thực, tỉ lệ thành công, GC/CPU/memory và percentiles với số mẫu đủ lớn.
5. Thử under load: concurrent login KDF, token saturation, restart/multi-node nonce allocator, key rotation. Dừng bài đo khi quota token/key hoặc lỗi nonce có dấu hiệu vi phạm.
6. Đối chiếu performance và security gate; một phương án nhanh nhưng fail bất biến phải bị loại, kể cả p99 đẹp hơn.

### Diễn giải ví dụ báo cáo

| Cấu hình | Diễn giải đúng |
| --- | --- |
| GCM encrypt 4 KiB, IV an toàn, 1 thread | Throughput/latency cho **API path được đo**, không phản ánh tự động chi phí network, storage hay multi-tenant |
| PBKDF2-HMAC-SHA256, 600.000 rounds, 3 sample | Có thể xem thời gian KDF tham khảo trên máy chạy, **chưa đủ** cho p95 hay capacity planning |
| 16 luồng đăng nhập dùng KDF bộ nhớ lớn | Cần tính peak RAM và độ trễ request đồng thời, giới hạn queue/concurrency |
| GCM khi chuyển provider JCA sang PKCS#11 | Kết quả có thể đổi vì native driver/session/device; phải kiểm tra đúng provider và key scope |
| “Tăng tốc” bằng reuse IV GCM | **Không hợp lệ** dù số đo cao hơn; phải thay thiết kế |

## 8. Các kiểm thử âm tính có giá trị

Đo tốc độ đi cùng kiểm tra tính đúng và bảo mật:

- **AAD mismatch:** cùng ciphertext/IV/key nhưng AAD khác phải bị từ chối với lỗi xác thực, thường `AEADBadTagException`; không xử lý như dữ liệu “không lỗi”.
- **Ciphertext/tag tamper:** thay một byte trong ciphertext/tag, giải mã phải thất bại, không trả plaintext không xác thực cho caller.
- **Wrong key / wrong key ID:** không chấp nhận dữ liệu với key không thuộc tenant/purpose/version đã định.
- **Nonce allocator concurrency:** các tác vụ song song không thể phát cùng nonce trong cùng key. Thử crash, restart, recovery để chắc chắn counter không lùi; bài kiểm thử này **không cần thực hiện encrypt trùng IV** để “xác nhận rủi ro”.
- **KDF drift:** regression test xác nhận work factor, loại thuật toán, salt format được lưu/đọc đúng; migration tăng cost cho credential cũ có lộ trình.
- **Quota/accounting:** warmup và lỗi retry vẫn được ghi nhận vào tổng số lời gọi, không vượt giới hạn của key/HSM.

**Không làm trong test:** lấy khóa sản xuất để benchmark, để password/PIN thật trong fixture, gộp cache khiến bỏ qua thao tác KDF cần đo, bỏ qua lỗi xác thực rồi chỉ chia tổng iteration cho thời gian, hoặc chuyển “tạm thời” sang IV cố định để so sánh nhanh.

## 9. Checklist trước khi công bố một kết quả

- [ ] Thuật toán, key size, tag size, AAD và dạng payload đúng như triển khai.
- [ ] Mỗi GCM encryption với cùng key có IV riêng, tính cả warmup/retry/fork/thread.
- [ ] Có chính sách nonce namespace/rotation khi nhiều tiến trình dùng chung key; không reset counter sau restart.
- [ ] Đã xác định key ID, tenant, purpose, key lifetime, tổng operation/byte quota và phiên bản key.
- [ ] KDF có salt đúng, work factor đạt ngưỡng chính sách; không nhầm salt và IV.
- [ ] Đã tách số đo KDF, AES, key lookup, IO/RPC nếu cần; xác định cost nằm trong hay ngoài timed region.
- [ ] Có round-trip/tag/AAD/wrong-key tests; exception không được coi là phép đo thành công.
- [ ] Ghi JDK, provider, CPU, threads, warmup, forks, payload distribution, GC/memory và thống kê latency.
- [ ] Không lộ password, key, PIN, secret, ciphertext nhạy cảm hoặc thông tin HSM qua log benchmark.

## Tài liệu chuẩn

1. Oracle, [`Cipher` Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html) — yêu cầu IV duy nhất cho GCM và thứ tự `updateAAD` trước khi xử lý ciphertext.
2. Oracle, [`GCMParameterSpec`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html) — IV và chiều dài authentication tag.
3. NIST, [SP 800-38D: GCM and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final) — yêu cầu xây dựng IV, phạm vi sử dụng khóa và các giới hạn bảo mật.
4. OWASP, [Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — lựa chọn/tuning cost cho Argon2id và PBKDF2, salt và tính chịu tải.
5. OpenJDK, [Java Microbenchmark Harness (JMH)](https://github.com/openjdk/jmh/) — thiết kế phép đo JVM, warmup, forks và tiêu thụ kết quả benchmark.

**Điểm cần nhớ:** hiệu năng mật mã chỉ có ý nghĩa khi đang đo **cùng phương án an toàn**. Nonce được quản lý theo key, chi phí KDF được giữ theo chính sách, và key chỉ hoạt động trong đúng domain/tenant/vòng đời của nó. Không đánh đổi những điều này để có một số `ops/s` lớn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-operations-testing-010">22.10 Lập kế hoạch đổi khóa, đổi thuật toán và khôi phục sau sự cố mà không tự hạ mức bảo vệ</a>

<details>
<summary>Xem chi tiết</summary>

# 22.10. Lập kế hoạch đổi khóa, đổi thuật toán và khôi phục sau sự cố mà không tự hạ mức bảo vệ

Trong hệ thống Java, khóa mật mã và thuật toán có vòng đời riêng: khóa hết thời hạn sử dụng, tài khoản bị lộ, nhà cung cấp đổi HSM/KMS, chứng thư sắp hết hạn, hoặc một thuật toán không còn đáp ứng yêu cầu an toàn. Khi ấy, nhiệm vụ không chỉ là "đổi chuỗi cấu hình": dữ liệu cũ vẫn cần truy cập đúng quyền, các dịch vụ cần tương thích trong giai đoạn chuyển tiếp, và ứng dụng không được âm thầm quay về khóa/thuật toán yếu để giữ hệ thống chạy.

Mục tiêu của bài:

- Phân biệt **xoay vòng khóa (key rotation)**, **di trú thuật toán (algorithm migration)**, **thu hồi/hủy khóa (revocation/destruction)** và **khôi phục sau sự cố (incident recovery)**.
- Thiết kế kế hoạch có inventory, trạng thái khóa, phiên bản dữ liệu, mốc chuyển tiếp, đường khôi phục, tiêu chí nghiệm thu và trách nhiệm rõ ràng.
- Duy trì khả năng đọc dữ liệu cũ **khi được chính sách cho phép**, trong khi thao tác bảo vệ mới luôn sử dụng cấu hình đạt chuẩn hiện hành.
- Xử lý tình huống khóa bị lộ, khóa thất lạc, KMS ngừng hoạt động hoặc thuật toán suy yếu mà **không fallback thiếu kiểm soát**.
- Thực hành bằng Java 17+ với `KeyGenerator`, `Cipher`, `GCMParameterSpec` và key registry minh họa có quyền `ACTIVE` / `DECRYPT_ONLY` / `BLOCKED`.

> **Nguyên tắc xuyên suốt:** Khả dụng là mục tiêu vận hành, nhưng không phải lý do để tắt xác thực, bỏ kiểm tra chữ ký/tag, tự chọn thuật toán yếu hoặc dùng khóa đã bị thu hồi. Nếu không thể chứng minh thao tác được phép và an toàn, trả lỗi có kiểm soát và kích hoạt quy trình khắc phục.

## 1. Bốn loại thay đổi dễ bị nhầm lẫn

| Hoạt động | Thay đổi gì? | Điều gì xảy ra với tài sản cũ? |
| --- | --- | --- |
| **Đổi khóa định kỳ** | Thay `keyId` và key material cho cùng mục đích/thuật toán | Có thể cần khóa cũ ở chế độ đọc/kiểm chứng trong một thời gian; dần mã hóa lại hoặc chuyển quyền sử dụng |
| **Đổi thuật toán** | Thay suite, primitive, mode, padding hoặc độ mạnh khóa theo chính sách | Cần lưu metadata của định dạng cũ và bộ giải mã/xác minh legacy được kiểm soát; thường phải di trú dữ liệu và đối tác |
| **Thu hồi vì nghi lộ khóa** | Dừng tin cậy vào khóa bị nghi xâm phạm và thay thế bằng khóa an toàn | Chữ ký/token có thể bị giả mạo; ciphertext có thể đã lộ. Không mặc định cho phép tiếp tục dùng khóa cũ như khi rotation bình thường |
| **Khôi phục sau sự cố** | Khôi phục dịch vụ, dữ liệu, khả năng truy cập và tính tin cậy từ bản sao/khóa dự phòng đã được kiểm chứng | Phải đối chiếu tính toàn vẹn và thời điểm phục hồi; không được vô hiệu hóa kiểm tra mật mã để "mở khóa" hệ thống |

Ba thuộc tính cần bảo vệ là **confidentiality** (không tiết lộ dữ liệu), **integrity/authenticity** (không chấp nhận dữ liệu giả mạo) và **availability** (dịch vụ có thể vận hành). Chúng khác nhau: phục hồi được dữ liệu sau mất khóa không chứng minh dữ liệu không từng bị đọc trái phép; khôi phục uptime không thể xóa bỏ tác động của một khóa ký đã bị chiếm đoạt.

**Cryptoperiod** là khoảng thời gian mà khóa được phép dùng cho một hoặc nhiều mục đích. Nên phân biệt *originator usage* (tạo chữ ký, mã hóa/MAC mới) với *recipient usage* (giải mã, kiểm tra chữ ký/MAC hợp lệ theo chính sách). Hạn sử dụng cho **tạo bảo vệ mới** có thể ngắn hơn thời gian cần giữ khóa để xử lý dữ liệu lịch sử. Không có một chu kỳ "30/90 ngày" mặc nhiên đúng cho mọi khóa; thiết kế theo rủi ro, loại khóa, thiết bị, hạn mức sử dụng, quy định và yêu cầu kinh doanh.

## 2. Kiểm kê trước khi đưa ra ngày đổi khóa

Không thể lập lịch migration an toàn nếu không biết khóa và thuật toán đang bảo vệ tài sản nào. Tạo danh mục quản lý khóa với tối thiểu các trường:

| Trường | Ví dụ minh họa | Cần biết để... |
| --- | --- | --- |
| `purpose` / `system` | `billing-data-encryption` | Gắn khóa với mục đích, dịch vụ, owner |
| `keyId` / version | `billing-dek-v7` | Chọn đúng khóa khi đọc dữ liệu lịch sử; tránh "thử mọi khóa" |
| `algorithmSuite` | `AES-256-GCM-v1` | Biết mode, tag, nonce, định dạng và chính sách kiểm tra |
| `owner` / approver | nhóm mật mã và chủ sản phẩm | Có người chịu trách nhiệm quyết định và phê duyệt |
| `createdAt`, `activatedAt`, `retireAt` | mốc đã được phê duyệt | Tính vòng đời và deadline rõ ràng |
| `state` / permitted operations | `ACTIVE`, `DECRYPT_ONLY`, `BLOCKED` | Cưỡng chế quyền mã hóa/ký/giải mã/kiểm chứng |
| `location` / custody | KMS alias, HSM slot, vùng DR | Xác định nhà quản lý vật liệu khóa và nơi phục hồi |
| `dependencies` | ứng dụng, job, mobile clients, partners | Phát hiện consumer chưa hỗ trợ version mới |
| `dataScope` / inventory | object store, DB columns, backups | Tính khối lượng dữ liệu cần xử lý |
| `recoveryPolicy` / audit | RTO/RPO, dual control, logs | Chứng minh có thể khôi phục có kiểm soát |

Danh mục phải tách **định danh khóa logic** và **phiên bản vật liệu khóa bất biến**. Ví dụ KMS alias `billing/current` có thể trỏ tới phiên bản mới, nhưng ciphertext cần metadata đủ để định vị đúng key version đã mã hóa. Nếu chỉ lưu alias "current", dữ liệu cũ có thể mất khả năng giải mã sau chuyển alias.

Trong Java, các điểm tích hợp cần điều tra gồm `Cipher`, `Signature`, `Mac`, `KeyStore`, `SSLContext`, `KeyManagerFactory`, `TrustManagerFactory`, provider JCA/JCE, thư viện JWT/JWS/JWE và API của KMS/HSM. Kiểm tra cả các consumer ngoài ứng dụng chính: batch ETL, backup restore, tác vụ ký định kỳ, công cụ điều tra sự cố, ứng dụng mobile cũ, đối tác xác minh chữ ký.

## 3. Thiết lập chính sách "không tự hạ mức bảo vệ"

### 3.1. Trạng thái khóa thay vì một biến `currentKey`

Mô hình tham khảo:

~~~text
GENERATED → STAGED → ACTIVE ────────→ DECRYPT_ONLY → RETIRED → DESTROYED
                       │                 │
                       └── nghi lộ ──────┴──→ COMPROMISED / BLOCKED
~~~

Đây là sơ đồ chính sách ứng dụng, không phải các trạng thái bắt buộc của mọi KMS:

- `STAGED`: khóa đã tạo, thử nghiệm, phân phối an toàn; **chưa** được phép mã hóa/ký production.
- `ACTIVE`: được phép thực hiện bảo vệ mới theo đúng purpose và suite được phê duyệt.
- `DECRYPT_ONLY`: chỉ được dùng để **đọc/giải mã dữ liệu lịch sử hợp lệ** trong giai đoạn migration; không cấp quyền tạo ciphertext/chữ ký mới. Đối với chữ ký, có thể đặt trạng thái `VERIFY_ONLY` riêng vì ý nghĩa trust khác với giải mã.
- `RETIRED`: không còn hoạt động thường xuyên; có thể còn tồn tại trong lưu trữ khóa theo chính sách phục hồi có kiểm soát.
- `COMPROMISED/BLOCKED`: cách ly khi nghi lộ, cấm bảo vệ mới và xử lý theo runbook. Quyền giải mã dữ liệu cũ, forensic hay xác minh tài liệu lịch sử phải được quyết định riêng, có cô lập, phê duyệt và truy vết; **không cho phép đường đọc mặc định dùng như khóa bình thường**.
- `DESTROYED`: không có con đường phục hồi được phê duyệt. Không hủy trước khi kiểm tra nhu cầu dữ liệu lưu trữ và backup.

**Không có chuyển trạng thái tự động từ `BLOCKED` về `ACTIVE` để chữa lỗi production.** Nếu key bị nghi xâm phạm, rollback triển khai ứng dụng không tự hợp thức hóa việc tiếp tục ký/mã hóa bằng key đó.

### 3.2. Hai chính sách khác nhau: ghi mới và đọc cũ

1. **Write/sign/encrypt policy:** chỉ chọn **một hoặc tập khóa ACTIVE được phê duyệt**, một tập suite hiện hành đã kiểm tra, luôn tạo IV/nonce đúng quy tắc. Khi provider/KMS báo lỗi, fail closed; không tự đổi sang ECB, CBC thiếu xác thực, SHA-1, khóa ngắn hoặc chế độ `NoPadding` không phù hợp.
2. **Read/verify/decrypt policy:** chọn khóa bằng `keyId`/metadata được ràng buộc với object, kiểm tra suite thuộc **allowlist đọc cũ**. Chỉ giải mã legacy khi dữ liệu và khóa vẫn đáng tin theo phạm vi và thời hạn cho phép. Không thử mọi key hay mọi thuật toán để tìm thứ "chạy được".
3. **Incident policy:** nếu khóa đã có khả năng bị lộ, khả năng đọc dữ liệu kỹ thuật khác với quyền tin cậy sử dụng dữ liệu. Cần cô lập, quyết định xử lý phạm vi bị ảnh hưởng, thu hồi/khóa tài sản và thay đổi credential; không đồng nhất `DECRYPT_ONLY` của rotation định kỳ với trạng thái compromised.

Nguyên tắc ở boundary API: client chỉ gửi định danh format/version; **server quyết định** key registry, suite được phép và quyền sử dụng. Metadata do khách gửi **không có quyền** tự chọn provider, đường dẫn khóa, URL tải JWKS, loại thuật toán hay cơ chế bỏ kiểm tra signature.

## 4. Định dạng ciphertext, chữ ký và metadata phải hỗ trợ di trú

### 4.1. Envelope encryption và các lớp khóa

Trong hệ thống lưu dữ liệu, mô hình envelope encryption thường dùng:

~~~text
Plaintext ──(DEK v1, AEAD)──> Ciphertext + tag + nonce
                 │
                 └── DEK được wrap bởi KEK v3 ở KMS/HSM
                             │
                       keyId/KEK version
~~~

**DEK** (*Data Encryption Key*) mã hóa payload; **KEK** (*Key Encryption Key*) bao/wrap DEK và thường nằm trong KMS/HSM. Dữ liệu bản ghi nên lưu có kiểm soát các trường như:

~~~json
{
  "formatVersion": 1,
  "suite": "AES-256-GCM-v1",
  "dataKeyId": "dek-ref-73",
  "wrappingKeyId": "kms-key-v3",
  "nonce": "<base64-96-bit-IV>",
  "ciphertext": "<base64-ciphertext-and-tag>",
  "contextId": "invoice:1234"
}
~~~

Đây là schema minh họa; placeholder `<...>` chỉ mô tả giá trị. Nếu DEK được wrap, envelope thực tế còn cần trường `wrappedDEK` hoặc tham chiếu đến nơi chứa wrapped DEK. Dữ liệu định danh context, `suite` và `keyId` cần được đưa vào **AAD** hoặc cấu trúc đã được xác thực thích hợp, và ứng dụng phải đối chiếu context với bản ghi nghiệp vụ mong đợi. Không coi phần header tự khai là nguồn tin cậy.

**Hai thao tác migration khác nhau:**

- **Rewrap KEK:** unwrap DEK bằng KEK cũ trong miền tin cậy được ủy quyền rồi wrap lại bằng KEK mới. Có thể không cần giải mã lại payload nếu DEK, suite và rủi ro không đổi. Bản thân thao tác này **không thay DEK** và **không loại bỏ việc dữ liệu có thể đã bị lộ** nếu DEK bị xâm phạm.
- **Re-encrypt bằng DEK/suite mới:** xác thực và giải mã ciphertext bằng cấu hình cũ được cho phép, sau đó dùng DEK/suite mới với nonce mới để mã hóa lại payload, cập nhật metadata nguyên tử và xác nhận đọc được. Cần khi DEK bị thay hoặc thuật toán payload phải nâng cấp.

Nên tách migration dữ liệu mã hóa với rotation khóa transport, signing, JWT và TLS; một thao tác rewrap không tự thay thế các bước reissue chứng thư hay thu hồi token.

### 4.2. Không tái sử dụng nonce/IV với AES-GCM

AES-GCM là chế độ mã hóa có xác thực (*AEAD*). Dùng `Cipher.getInstance("AES/GCM/NoPadding")` với `GCMParameterSpec`; ứng dụng phải bảo đảm **nonce không lặp với cùng một khóa**. Ví dụ học dùng IV 96 bit mới từ `SecureRandom` cho mỗi lần mã hóa. Trong hệ thống lưu lượng lớn, nhiều writer, hoặc cần giới hạn xác suất va chạm chặt chẽ, thiết kế cơ chế phân bổ nonce duy nhất, hạn mức số lượt mã hóa theo key và lịch đổi khóa dựa trên số lượt sử dụng theo chính sách NIST SP 800-38D. **Không dùng lại nonce cũ khi re-encrypt bằng cùng key**, kể cả khi bản ghi có cùng plaintext.

`Cipher.updateAAD(...)` phải được gọi trước `update(...)`/`doFinal(...)`. Khi `Cipher.doFinal(...)` ở phía giải mã trả `AEADBadTagException`, coi là lỗi toàn vẹn/xác thực; tuyệt đối không bỏ tag, thử CBC hoặc trả plaintext một phần.

### 4.3. Vì sao không tin version/header tự khai?

Một attacker có thể sửa trường `alg`, `kid`, `formatVersion`, `nonce` hoặc token headers. Ứng dụng cần:

- Parse đúng schema; giới hạn độ dài, ký tự, số lượng phiên bản, kích thước ciphertext; không cho path traversal hay URL lạ qua `kid`.
- Ánh xạ `keyId` vào registry nội bộ đã cấp quyền, có purpose/tenant/issuer/alg được ràng buộc, không cho tùy ý truyền một khóa khác.
- Từ chối suite không nằm trong allowlist và cặp key–suite không hợp lệ.
- Ràng buộc các trường quan trọng vào AEAD AAD hoặc dữ liệu được ký, kiểm tra context và tính toàn vẹn **trước khi xử lý nghiệp vụ**.
- Chỉ cho reader legacy trong phân đoạn dữ liệu/phạm vi hợp đồng được phê duyệt. Mỗi đường legacy có owner, thời hạn và mục tiêu loại bỏ.

## 5. Lập kế hoạch từ khảo sát đến cutover

Bản kế hoạch cần hai tuyến song song: **chuyển trạng thái khóa** và **chuyển dữ liệu/consumer**. Không chọn ngày hủy khóa cũ dựa duy nhất trên ngày phát hành phiên bản mới.

| Giai đoạn | Việc phải làm | Điều kiện qua cổng (gate) |
| --- | --- | --- |
| **0. Khảo sát** | Kiểm kê keyId/alg/provider, độ nhạy, bản sao dữ liệu, chứng thư, đối tác, thời gian giữ backup; đo số consumer legacy | Biết owner, blast radius, mục tiêu RTO/RPO và khả năng truy hồi dữ liệu |
| **1. Chuẩn bị** | Xác định suite mới/allowlist; sinh key trên KMS/HSM; xây reader tương thích đọc cũ có kiểm soát; dựng metric và audit | Reader mới giải được fixture hợp lệ của cả format được phép; từ chối unsupported/tampered |
| **2. Canary** | Kích hoạt writer mới cho một nhóm hạn chế; đo tỷ lệ giải mã/lỗi tag, độ trễ, error budget, hiệu năng KMS | Không có plaintext loss, các consumer mục tiêu đều đọc được, alert hoạt động |
| **3. Chuyển ghi mới** | Chuyển key/suite hiện hành atomically theo cấu hình/feature flag có chữ ký/quy trình phê duyệt; khóa cũ chuyển `DECRYPT_ONLY` | Không còn luồng ghi bằng khóa cũ; metric số bản ghi format mới tăng đúng |
| **4. Di trú lịch sử** | Rewrap hoặc re-encrypt theo phạm vi; checkpoint, retry idempotent, compare-and-swap; xử lý backup/archive | Số dữ liệu còn key cũ giảm về ngưỡng kế hoạch; có kiểm tra mẫu/full theo policy |
| **5. Thu hồi** | Chặn reader legacy thông thường theo deadline, rút quyền ký/mã hóa cũ, vô hiệu hóa mapping; hủy khóa khi đủ điều kiện | Không còn bản sao cần đọc bằng key đó, restore test đạt; phê duyệt destruction |
| **6. Theo dõi** | Audit và giám sát key/suite usage, drift, retries, KMS latency, khôi phục định kỳ | Có bằng chứng cutover và không phát sinh cơ chế fallback mới |

**Thiết kế rollback trước khi canary:**

- Trước khi khóa cũ bị nghi lộ, có thể rollback **mã ứng dụng** hoặc hạ tỷ lệ canary; nhưng writer rollback vẫn phải tuân thủ **chính sách suite/key hiện hành**. Nếu bản build cũ chỉ hiểu encryption yếu, bản build đó không phải rollback được phê duyệt.
- Dùng chiến lược **expand → migrate → contract**: trước phát hành reader đọc được format mới, sau đó writer chuyển sang mới, cuối cùng mới bỏ reader cũ.
- Rollback không được trỏ alias về key bị compromised, tái dùng nonce, tắt validation hoặc bật lại thuật toán bị cấm. Nếu không còn bản build cũ tương thích, lựa chọn là roll forward/khôi phục qua DR đã chuẩn bị hoặc tạm dừng chức năng.
- Chỉ coi migration thành công khi dữ liệu lẫn bản sao lưu, luồng DR và consumer dùng trên thực tế được kiểm chứng; tỷ lệ 100% của bảng dữ liệu chính chưa đủ nếu còn archive phục hồi bằng khóa cũ.

## 6. Ví dụ Java 17+: rotation AES-GCM và từ chối downgrade

Chương trình dưới đây minh họa **rotation định kỳ đã lên kế hoạch**, trong đó `key-v1` ban đầu được dùng để mã hóa, sau đó chuyển sang `DECRYPT_ONLY` để giải mã bản ghi cũ; `key-v2` nhận quyền mã hóa mới. Bản ghi cũ được mã hóa lại bằng `key-v2`, rồi `key-v1` bị chặn. Chương trình còn thử sửa ciphertext và truyền suite không được duyệt.

Đây là **mô hình thực hành trong bộ nhớ**: hai khóa được tạo ngẫu nhiên bằng `KeyGenerator`, key registry là `HashMap`, chưa có KMS/HSM, lưu trữ bền vững, transaction, kiểm soát đa tiến trình hoặc phục hồi sau mất khóa. **Không áp dụng trạng thái `DECRYPT_ONLY` này cho khóa đã bị lộ mà chưa hoàn tất đánh giá và cô lập sự cố.**

Lưu vào **`SafeKeyRotationDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SafeKeyRotationDemo {
    private static final String SUITE = "AES-256-GCM-v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, KeyVersion> KEYRING = new HashMap<>();

    enum State { ACTIVE, DECRYPT_ONLY, BLOCKED }

    record KeyVersion(SecretKey key, State state) {}

    record Envelope(
        String suite,
        String keyId,
        String resourceId,
        byte[] nonce,
        byte[] ciphertext
    ) {}

    private static SecretKey generateKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private static byte[] aad(
            String suite, String keyId, String resourceId) {
        // Demo encoding; production should use canonical length-prefixed encoding.
        String metadata = "purpose=invoice|" + suite + "|"
            + keyId + "|" + resourceId;
        return metadata.getBytes(StandardCharsets.UTF_8);
    }

    private static Envelope encrypt(
            String plaintext, String keyId, String resourceId)
            throws GeneralSecurityException {
        KeyVersion version = KEYRING.get(keyId);
        if (version == null || version.state() != State.ACTIVE) {
            throw new GeneralSecurityException(
                "Encryption key is not ACTIVE");
        }

        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce); // Must be unique per key in real deployments.
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.ENCRYPT_MODE,
            version.key(),
            new GCMParameterSpec(128, nonce)
        );
        cipher.updateAAD(aad(SUITE, keyId, resourceId));

        byte[] encrypted = cipher.doFinal(
            plaintext.getBytes(StandardCharsets.UTF_8));
        return new Envelope(SUITE, keyId, resourceId, nonce, encrypted);
    }

    private static String decrypt(
            Envelope envelope, String trustedResourceId)
            throws GeneralSecurityException {
        // Reject unknown suites; never try a weaker algorithm as fallback.
        if (!SUITE.equals(envelope.suite())
                || !trustedResourceId.equals(envelope.resourceId())) {
            throw new GeneralSecurityException(
                "Unsupported suite or wrong resource");
        }
        if (envelope.nonce().length != 12
                || envelope.ciphertext().length < 16) {
            throw new GeneralSecurityException(
                "Malformed ciphertext envelope");
        }

        KeyVersion version = KEYRING.get(envelope.keyId());
        if (version == null || version.state() == State.BLOCKED) {
            throw new GeneralSecurityException(
                "Key unavailable or blocked");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            version.key(),
            new GCMParameterSpec(128, envelope.nonce())
        );
        cipher.updateAAD(aad(
            envelope.suite(), envelope.keyId(), trustedResourceId));
        byte[] plaintext = cipher.doFinal(envelope.ciphertext());
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public static void main(String[] args)
            throws GeneralSecurityException {
        KEYRING.put("key-v1",
            new KeyVersion(generateKey(), State.ACTIVE));
        Envelope legacy =
            encrypt("invoice-old", "key-v1", "invoice:101");

        KeyVersion old = KEYRING.get("key-v1");
        KEYRING.put("key-v1",
            new KeyVersion(old.key(), State.DECRYPT_ONLY));
        KEYRING.put("key-v2",
            new KeyVersion(generateKey(), State.ACTIVE));

        Envelope fresh =
            encrypt("invoice-new", "key-v2", "invoice:102");
        System.out.println(
            "legacy-readable=" + decrypt(legacy, "invoice:101"));
        System.out.println(
            "new-readable=" + decrypt(fresh, "invoice:102"));

        // Planned migration of the historical record to the new key.
        Envelope migrated = encrypt(
            decrypt(legacy, "invoice:101"),
            "key-v2", "invoice:101");
        KEYRING.put("key-v1",
            new KeyVersion(old.key(), State.BLOCKED));
        System.out.println(
            "migrated-readable=" + decrypt(migrated, "invoice:101"));

        boolean oldBlocked = false;
        try {
            decrypt(legacy, "invoice:101");
        } catch (GeneralSecurityException expected) {
            oldBlocked = true;
        }
        System.out.println("old-key-blocked=" + oldBlocked);

        byte[] corrupted = fresh.ciphertext().clone();
        corrupted[0] ^= 1;
        Envelope tampered = new Envelope(
            fresh.suite(), fresh.keyId(), fresh.resourceId(),
            fresh.nonce().clone(), corrupted);
        boolean tamperRejected = false;
        try {
            decrypt(tampered, "invoice:102");
        } catch (AEADBadTagException expected) {
            tamperRejected = true;
        }
        System.out.println("tamper-rejected=" + tamperRejected);

        Envelope unapproved = new Envelope(
            "AES/CBC/PKCS5Padding", fresh.keyId(), fresh.resourceId(),
            fresh.nonce(), fresh.ciphertext());
        boolean downgradeRejected = false;
        try {
            decrypt(unapproved, "invoice:102");
        } catch (GeneralSecurityException expected) {
            downgradeRejected = true;
        }
        System.out.println("downgrade-rejected=" + downgradeRejected);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac SafeKeyRotationDemo.java
java SafeKeyRotationDemo
~~~

Stdout dự kiến của chương trình độc lập (giá trị khóa và nonce sinh ngẫu nhiên nhưng **không** in ra, nên các dòng này xác định):

~~~text
legacy-readable=invoice-old
new-readable=invoice-new
migrated-readable=invoice-old
old-key-blocked=true
tamper-rejected=true
downgrade-rejected=true
~~~

**Vì sao ví dụ có ý nghĩa:**

1. Mọi ciphertext gắn `suite`, `keyId`, `resourceId` và nonce. Reader kiểm suite, key state và resource ID do phía gọi tin cậy cung cấp; metadata mật mã được đưa vào AAD.
2. Ngay sau cutover, `key-v1` chỉ còn `DECRYPT_ONLY`. Gọi `encrypt(...,"key-v1",...)` ở giai đoạn này sẽ thất bại vì code chỉ chấp nhận `ACTIVE`.
3. Re-encrypt tạo nonce mới, không sao chép ciphertext cũ. Sau migration, khóa cũ `BLOCKED` nên bản ghi chưa chuyển không thể đọc được bằng luồng bình thường. Trong hệ thống thật chỉ thực hiện sau kiểm kê/restore test và quyết định retention.
4. Tag GCM được kiểm trong `doFinal`. Sửa ciphertext gây `AEADBadTagException`; ứng dụng không cứu lỗi bằng trả plaintext chưa xác thực.
5. Suite `AES/CBC/PKCS5Padding` do bản ghi tự khai bị từ chối ngay; không có lệnh `Cipher.getInstance(envelope.suite())` để đối tượng không tin cậy quyết định thuật toán.
6. `HashMap` và byte arrays trong demo **không thread-safe và không phải key vault**. Production cần KMS/HSM, key references bất biến, kho metadata giao dịch và cấu trúc AAD chuẩn hóa/không mơ hồ (ví dụ mã hóa theo trường độ dài).
7. Demo minh họa rotation bình thường, **không chứng minh an toàn khi khóa bị xâm phạm**. Kẻ tấn công từng có `key-v1` có thể đã giải mã những ciphertext cũ mà họ lấy được; chuyển dữ liệu sang `key-v2` chỉ bảo vệ bản sao mới từ thời điểm chuyển, không xóa rò rỉ đã xảy ra.

## 7. Di trú dữ liệu thực tế: transaction, checkpoint và backup

Một job migrate phải tồn tại được khi process chết, retry và nhiều worker chạy song song. Đề xuất luồng cho mỗi bản ghi:

~~~text
Đọc record + generation/version (v)
  → kiểm tra trạng thái key cũ và quyền migration
  → xác thực tag / decrypt
  → mã hóa bằng key mới + nonce mới
  → ghi atomically nếu generation vẫn == v
  → đọc và xác nhận bản mới
  → audit checkpoint (không log plaintext)
~~~

**Điểm kỹ thuật cần làm rõ:**

- **Idempotency:** Nếu job restart sau khi ghi thành công, nhận diện record đã có key version mới và bỏ qua; không re-encrypt vô hạn hay tạo hai bản cùng được xem là nguồn chính.
- **Compare-and-swap/optimistic locking:** `UPDATE ... WHERE id=? AND version=?` để tránh ghi đè bản ghi vừa bị user cập nhật. Nếu conflict, đọc lại từ nguồn mới rồi xử lý lại; không dùng dữ liệu stale.
- **Đánh dấu committed:** cập nhật ciphertext, suite, keyId, nonce, version trong **một transaction** hoặc nguyên tắc tương đương cho object storage; tránh tình trạng metadata chỉ khóa mới nhưng ciphertext còn khóa cũ.
- **Giới hạn tải:** throttle để không làm nghẽn HSM/KMS; theo dõi rate, retry có backoff cho lỗi tạm thời được phân loại. Không retry vô hạn khi tag sai hoặc key bị thu hồi.
- **Xác minh:** so số record, tỷ lệ thành công, lỗi tag, lỗi key, sampling và integrity checks; không log plaintext hoặc key material để chứng minh tiến độ.
- **Backup/snapshot:** snapshot cũ vẫn có thể chứa ciphertext cần key cũ. Cần xác định retention và cách restore: giữ khóa lịch sử an toàn theo chính sách hoặc xây kế hoạch chuyển cả snapshot, trước khi xóa key cũ.
- **Khả năng phục hồi:** tập restore phải bao gồm dữ liệu, metadata format/key version, được ủy quyền dùng key phù hợp, cấu hình KMS/HSM và identity policy. Backup DB đơn thuần không khôi phục được nếu mất DEK/KEK duy nhất.

**Cẩn thận với rewrap:** quay vòng KEK có thể rewrap wrapped DEK nhanh hơn re-encrypt payload, nhưng không thay DEK. Nếu nguyên nhân là DEK lộ hoặc thuật toán payload không còn phù hợp, chỉ đổi KEK là **chưa giải quyết rủi ro**. Ngược lại, nếu chỉ KEK có dấu hiệu mất kiểm soát, đánh giá phạm vi DEK đã có thể bị unwrap và quyết định có cần thay DEK; không có một quy tắc rewrap luôn đủ cho mọi sự cố.

## 8. Di trú chữ ký số, certificate, JWT và TLS

Đối tượng "khóa" không chỉ dành cho dữ liệu mã hóa. Kế hoạch thay khóa ký và certificate phải giải quyết một rủi ro đặc biệt: **dữ liệu có thể verify đúng về toán học nhưng vẫn đến từ khóa không còn được tin cậy**.

### 8.1. Chữ ký số và lịch sử tài liệu

- Tạo cặp khóa ký mới với thuật toán/chính sách phù hợp; phân phối public key/certificate theo kênh tin cậy; kiểm tra consumer có provider/algorithm hỗ trợ.
- Sau cutover, **chỉ ký tài liệu mới bằng khóa mới**. Có thể giữ public key/certificate cũ để xác minh chữ ký lịch sử trong giới hạn thời gian/phạm vi đã phê duyệt.
- Nếu private key ký cũ **bị lộ**, không cho phép ký mới hoặc xác thực giao dịch hiện hành bằng nó. Cần thu hồi certificate khi phù hợp, thông báo relying parties, đánh giá chữ ký sinh trong khoảng nghi ngờ.
- Xác minh chữ ký lịch sử cần phân biệt **thời điểm ký tin cậy**, dấu thời gian tin cậy và trạng thái thu hồi tại thời điểm thích hợp. Thời gian do chính tài liệu tự khai không đủ chứng minh chữ ký được tạo trước lúc khóa bị xâm phạm.
- Không "fix" xác minh bằng việc bỏ kiểm tra chain/trust, key usage, certificate expiry/revocation hay chấp nhận bất kỳ public key nào nhúng trong tài liệu.

### 8.2. JWT/JWS/JWE: đặc biệt tránh algorithm confusion

Token JWT có thể mang trường `kid` và `alg`. Bên xác minh phải chọn key theo issuer/trust registry được thiết lập từ trước và kiểm tra cặp key–alg được phép; **không** dùng `alg` do JWT tự khai để bật `none` hoặc lẫn RSA public key với HMAC secret.

Mô hình chuyển signer:

1. Triển khai verifier mới chấp nhận **chính xác** key cũ và mới của issuer tin cậy, mỗi key gắn algorithm/purpose đã định.
2. Công bố public key mới qua kênh tin cậy (ví dụ JWKS thuộc issuer đã cấu hình); sau đó mới phát token bằng private key mới.
3. Đợi hết vòng đời token cũ hợp lệ và kiểm tra consumer; gỡ public key cũ khi hết cửa sổ chấp nhận.
4. Nếu private key cũ bị xâm phạm, **đẩy nhanh thu hồi** và xử lý session/token ảnh hưởng, dù token cũ chưa hết `exp`. Có thể cần invalidate server-side token/session theo chính sách.

Không tin vào URL `jku`/`x5u` tự cung cấp trong token để tải khóa tùy ý, và không lấy `kid` làm đường dẫn file hoặc SQL fragment không được kiểm soát.

### 8.3. TLS/mTLS và certificate service

Cần thay key pair/certificate, cấu hình server/client và trust store của bên đối tác có điều phối. Ưu tiên deploy certificate mới trước khi certificate cũ hết hạn; kiểm tra SNI, chain, hostname, SAN, mục đích sử dụng, OCSP/CRL theo hệ thống. Với mTLS, phối hợp cả chính sách trust anchors/issuer và danh sách client certificates được chấp nhận.

Rollback TLS **không** được bật lại protocol/cipher suite đã cấm chỉ vì client cũ không hỗ trợ. Chọn remediation được chấp thuận: nâng client, gateway trung gian có kiểm soát/chính sách tách biệt, dừng giao dịch chưa tương thích, hoặc gia hạn cutover **chỉ khi** vẫn còn bộ cấu hình đạt yêu cầu bảo mật. KMS outage và TLS certificate rotation là hai miền failure khác nhau, cần runbook khác nhau.

### 8.4. Hash và mật khẩu

Nếu hệ thống phải nâng thuật toán hash mật khẩu, không thể giải mã hash cũ để chuyển hàng loạt. Quy trình thường là nhận diện format/parameters của bản ghi, kiểm tra mật khẩu người dùng nhập bằng cơ chế cũ **chỉ khi cơ chế đó còn được phép**, sau khi xác thực thành công thì hash lại bằng cấu hình mạnh hiện hành và ghi atomically. Với thuật toán đã bị cấm hoặc bị tấn công nghiêm trọng, có thể phải yêu cầu reset mật khẩu thay vì tiếp tục hỗ trợ xác minh yếu.

Chữ ký/MAC, mật khẩu, mã hóa dữ liệu và TLS có quy tắc migration riêng. Không tái sử dụng cùng key material cho nhiều mục đích để "tiết kiệm thao tác đổi khóa".

## 9. Kịch bản sự cố và phản ứng phù hợp

### Kịch bản A — Khóa gần hết cryptoperiod, không có dấu hiệu lộ

1. Chuẩn bị key mới và xác minh các consumer.
2. Chuyển ghi mới sang key mới, đưa key cũ vào `DECRYPT_ONLY`/`VERIFY_ONLY` theo policy.
3. Migration dữ liệu cũ, chứng minh khôi phục backup, thu hẹp cửa sổ sử dụng khóa cũ.
4. Thu hồi và hủy khóa khi không còn phụ thuộc hợp lệ, lưu bằng chứng phê duyệt và audit.

Đây là case mà đọc dữ liệu cũ trong transition thường được phép. Không áp dụng kết luận này tự động cho khóa compromised.

### Kịch bản B — Nghi lộ hoặc bị lộ DEK/KEK

1. **Contain:** xác định key version, hệ thống, principal, tài sản, thời gian nghi xâm phạm; tạm dừng thao tác bảo vệ mới bằng khóa đó và khóa các quyền truy cập trái phép. Bảo toàn chứng cứ và log theo quy trình incident.
2. **Assess:** phân biệt lộ trực tiếp DEK, lộ KEK nhưng khả năng unwrap DEK không rõ, key bị sử dụng sai hay mất quyền kiểm soát KMS. Xác định ciphertext nào bên tấn công có thể đã lấy được.
3. **Replace:** cấp khóa mới trên miền tin cậy được xác minh, chuyển producer mới; re-encrypt/re-wrap theo đánh giá cụ thể, xoay credential/API token liên quan nếu bị ảnh hưởng.
4. **Recover:** xác minh toàn vẹn dữ liệu và policy, phục hồi cấu hình sạch, chạy lại luồng xử lý hợp lệ có replay protection, kiểm tra lưu lượng và alert.
5. **Disclose/learn:** thực hiện yêu cầu thông báo theo chính sách/luật áp dụng; sửa root cause, ngăn tái sử dụng key và chứng minh mức bảo vệ mới.

**Không thể thu hồi khả năng đọc đối với ciphertext mà kẻ tấn công đã giải mã hoặc sao chép key trước đó.** Rotation giảm phơi nhiễm về sau; không đảo ngược rò rỉ quá khứ.

### Kịch bản C — Khóa thất lạc hoặc HSM/KMS không khả dụng

- Phân biệt **mất quyền truy cập tạm thời** (outage, credential hết hạn, policy sai) và **mất vĩnh viễn key material**. Đừng giải quyết bằng khóa mặc định hard-code hoặc tắt mã hóa.
- Đổi hướng lưu lượng tới KMS/HSM dự phòng **đã ủy quyền**, với identity, audit và cấu hình key version tương thích, sau khi kiểm tra failover theo hợp đồng vendor.
- Dùng backup key/escrow chỉ nếu chính sách cho phép và đã chuẩn bị trước, với multi-person control, audit, môi trường khôi phục cô lập và kiểm tra tính toàn vẹn. Không tạo "master password" dùng chung cho mọi đối tác.
- Nếu key gốc mất vĩnh viễn mà không tồn tại key escrow/backup hợp lệ và chỉ có ciphertext, dữ liệu có thể **không thể phục hồi**. Không có thuật toán "cứu" AES-GCM bằng việc bỏ authentication tag.
- Khôi phục dữ liệu từ bản snapshot tin cậy cần kiểm tra checksum/signature/AEAD, thời điểm backup, RPO và sự nhất quán giữa dữ liệu, metadata và key version.

### Kịch bản D — Thuật toán hoặc provider phát hiện lỗ hổng

- Tạm chặn **bảo vệ mới** bằng suite bị cấm/không đáng tin; đánh giá mức ảnh hưởng và khả năng bị khai thác thay vì giả định mọi dữ liệu đều bị phá.
- Triển khai suite mới và thư viện/provider đã vá, thực hiện kiểm thử liên thông, migration và lịch loại bỏ verifier/reader cũ.
- Nếu legacy suite không thể được dùng an toàn, **không cố giải mã/verify ở production bình thường**. Chỉ xử lý ngoại lệ theo quy trình risk-approved, môi trường cô lập, không cấp quyền truy cập nghiệp vụ dựa trên kết quả chưa xác thực.
- Nếu nhà cung cấp KMS chỉ hỗ trợ thuật toán cũ, cách khắc phục là đổi nhà cung cấp hoặc kiến trúc, không hạ policy của toàn hệ thống.

## 10. Runbook khôi phục: từ phát hiện đến xác nhận an toàn

**P0 — Phát hiện và khai báo**

- Chỉ rõ sự kiện (key compromise, algorithm emergency, KMS outage, mất backup key, expired certificate), mức độ ảnh hưởng, time window và service owner.
- Ghi incident ID, keyId/version, suite, principal, hệ thống nhận ảnh hưởng, đối tượng dữ liệu và bằng chứng liên quan.
- Phân loại đây là outage đơn thuần, vi phạm integrity, hay có khả năng compromise confidentiality. Người phụ trách an toàn thông tin quyết định trạng thái tin cậy.

**P1 — Cô lập và bảo đảm trust boundary**

- Thu hồi credential/truy cập bị nghi lộ, giới hạn khả năng ký/mã hóa mới, dừng job có nguy cơ tiếp tục ghi dữ liệu yếu.
- Không thay "tất cả key" mù quáng khiến mất khóa cần đọc dữ liệu chưa di trú. Quản lý danh sách phụ thuộc và khóa liên quan theo trạng thái có giám sát.
- Không đưa private key vào log, chat, ticket hoặc script chẩn đoán; log mã keyId và mã lỗi thay vì vật liệu khóa.

**P2 — Khôi phục trên nền an toàn**

- Bật key/suite mới đã được phê duyệt, chuyển writer mới; xác nhận các consumer và phía đối tác sẵn sàng.
- Khôi phục từ backup tin cậy với đầy đủ metadata key version; kiểm tra hash, AEAD tag, chữ ký và access control.
- Di trú đối tượng, revoke/cancel/reissue credential hoặc certificate theo bản chất sự cố; xử lý các token/session có thể bị giả mạo.
- Kiểm tra authn/authz, đặc biệt là dữ liệu do khóa ký bị lộ có thể giả mạo. Không dùng chữ ký "hợp lệ" với key đã bị chiếm làm chứng cứ duy nhất.

**P3 — Đánh giá trước khi mở lại**

- Chạy smoke test ghi mới/đọc cũ hợp lệ, verify signature, restore test, concurrent writes và failure injection.
- Đo tỷ lệ dữ liệu migrated, lỗi key, lỗi tag, xác minh backup và consumer lag.
- Chỉ mở lại khi qua gate: tất cả đường tạo bảo vệ mới sử dụng key/suite ACTIVE được duyệt; đường legacy bị hạn chế; không có downgrade path; log/audit còn hoạt động.
- Ghi quyết định chấp nhận rủi ro (nếu có), owner và ngày hết hạn. Không biến ngoại lệ sự cố thành cấu hình mặc định.

**P4 — Rà soát sau sự cố**

- Đánh giá dữ liệu có thể đã bị lộ, người dùng cần thông báo, root cause và các bước ngăn tái diễn.
- Thu hẹp/loại bỏ reader legacy; rà soát hard-coded keys, stale tokens, duplicate keys, backup retention.
- Cập nhật tabletop exercise và diễn tập khôi phục, bảo đảm có thể hoàn thành lần sau **mà không cần tắt cơ chế bảo vệ**.

## 11. Kiểm thử và tiêu chí nghiệm thu

Một migration chỉ đạt khi các trường hợp âm tính được kiểm tra, không chỉ khi "decrypt thành công".

| Ca kiểm thử | Kết quả phải có |
| --- | --- |
| Encrypt/sign mới bằng `ACTIVE` | Thành công với suite/key version mới |
| Encrypt/sign mới bằng `DECRYPT_ONLY` hoặc `BLOCKED` | **Từ chối**, không fallback |
| Đọc dữ liệu cũ có key `DECRYPT_ONLY` còn được cho phép | Thành công, đúng resource/tenant, trả dữ liệu có tag hợp lệ |
| Đọc bản ghi gắn keyId chưa biết, key đã bị chặn | **Từ chối** và audit |
| Header tự khai thuật toán yếu hoặc thay `kid`, context | **Từ chối** theo allowlist/AAD và trust binding |
| Tamper ciphertext, nonce, tag hoặc AAD | **Từ chối**; không trả một phần plaintext |
| KMS timeout/mất kết nối | Lỗi an toàn, retry giới hạn cho trường hợp tạm thời; không sinh key mặc định |
| Job migrate bị kill giữa bước ghi | Record nhất quán; restart xử lý idempotent, không mất dữ liệu |
| Hai writer cập nhật cùng record | CAS/transaction chặn stale overwrite |
| Backup trước thời điểm đổi key | Restore đúng dữ liệu, key version và policy hoặc có runbook ngoại lệ được phê duyệt |
| Certificate/key ký bị lộ | Token/chữ ký mới bằng khóa bị lộ không được tin; signer mới được tin theo policy |
| Consumer cũ không đọc được format mới | Canary chặn cutover; nâng consumer hoặc chọn phương án an toàn được phê duyệt |

Kiểm thử hiệu năng và vận hành riêng: throughput khi re-encrypt, hạn mức KMS/HSM, số API calls trên mỗi bản ghi, khóa/nonce usage, độ trễ cấp key, throttling, queue backlog, concurrency và tăng trưởng backup.

### Các metric cần đặt cảnh báo

- `encrypt_operations{keyId, suite}`, `sign_operations{keyId, suite}`: sau cutover **không được** tăng đối với key/suite đã ngừng ghi.
- `decrypt_operations{legacyKeyId}`, `legacy_objects_remaining`: phải giảm theo kế hoạch, tránh "legacy forever".
- `aead_tag_failures`, `unknown_key_or_suite_rejections`, `verification_failures`: báo động theo baseline, không tự fallback.
- `kms_errors`, `kms_latency`, `token_login_failures`: phân biệt lỗi availability và integrity/security.
- `rotation_job_progress`, `migration_conflicts`, `retry_exhausted`, `restore_drill_success`: giúp biết tốc độ và độ tin cậy của cutover.

Key ID và suite là metadata được kiểm soát; **không** xuất plaintext, PIN, secret hoặc private key vào metric labels. Tránh label có cardinality không giới hạn do dữ liệu attacker gửi.

## 12. Phê duyệt và phân quyền

Quy trình rotation/incident nên có ít nhất các vai trò:

- **System/data owner:** xác nhận dữ liệu nghiệp vụ, retention, thời gian downtime/RTO/RPO, đối tác tiêu thụ.
- **Crypto/security owner:** duyệt algorithm suite, key strength, key purposes, trust anchors, thời gian cho phép legacy và quyết định khi nghi compromise.
- **KMS/HSM operator:** sinh/cấp quyền/thu hồi/escrow/destruction theo chính sách; có kiểm soát nhiều người khi cần, không xuất khóa riêng tùy tiện.
- **Release/SRE:** chuẩn bị canary, giao thức failover, backup/restore, metric/alerts và rollout có checkpoint.
- **Audit/incident commander:** lưu chứng cứ mốc thay đổi, phê duyệt, sai lệch, thông báo và báo cáo sau sự cố.

Khi đổi khóa theo lịch, người triển khai không được tự hạ suite vì consumer chưa sẵn sàng. Khi sự cố thật, người ứng cứu không được tự chọn "cho phép SHA-1 thêm một ngày" hoặc tắt certificate validation để khôi phục uptime; lựa chọn này phải qua chính sách rủi ro và đáp ứng yêu cầu bảo mật tối thiểu. Nếu không có giải pháp đáp ứng chính sách, tạm dừng hoạt động ảnh hưởng và khôi phục theo phương án đã thiết kế.

## 13. Lỗi phổ biến và hậu quả

| Sai lầm | Vì sao nguy hiểm | Hướng xử lý |
| --- | --- | --- |
| Chỉ lưu alias `current` cùng ciphertext | Sau rotation không biết phiên bản key đã mã hóa bản ghi | Lưu `keyId`/format bất biến; quản lý mapping tin cậy |
| Thử mọi key/cipher cho tới khi decrypt được | Có thể chấp nhận downgrade, gây oracle và mất audit | Lựa chọn key/suite từ registry + authenticated metadata và allowlist |
| Dùng AES-GCM nhưng tái sử dụng nonce với cùng key | Mất bảo đảm bảo mật và xác thực | Đảm bảo unique nonce, đo usage và rotate theo hạn mức |
| Tắt AEAD tag/signature/certificate verification khi lỗi | Dữ liệu giả mạo thành dữ liệu "hợp lệ" | Fail closed, xử lý outage/incident qua runbook |
| Đổi KEK rồi coi DEK bị lộ đã được xử lý | Payload vẫn dùng DEK cũ | Phân loại compromise; re-encrypt bằng DEK mới khi cần |
| Xóa khóa cũ ngay sau chuyển writer | Mất khả năng đọc backup/dữ liệu lịch sử | Kiểm kê toàn bộ consumer/snapshot và diễn tập restore trước destruction |
| Giữ khóa compromised trong trust store như bình thường | Tin nhầm chữ ký/token giả mạo | Thu hồi/cách ly, đánh giá chữ ký lịch sử theo chứng cứ tin cậy |
| Rollback bản build cũ có suite yếu | Vi phạm baseline dù hệ thống có vẻ hoạt động | Roll forward hoặc rollback chỉ khi tuân thủ security floor |
| Chạy job di trú không transaction/idempotency | Có thể mất dữ liệu hoặc sai metadata key version | CAS, checkpoint, audit và thử kill/restart |
| Chọn cryptoperiod chỉ theo lịch cố định | Bỏ qua giới hạn nonce/số lượt ký hoặc sự cố | Xem usage, exposure, policy và sự thay đổi threat model |

## 14. Bảng kiểm trước khi tuyên bố hoàn tất

- [ ] Tất cả key/suite được kiểm kê theo purpose, owner, phiên bản, cryptoperiod và trạng thái.
- [ ] Policy tách biệt *create new protection*, *read/verify legacy*, *compromised recovery*; key và algorithm allowlist được cưỡng chế trong mã và hệ thống quản lý.
- [ ] Reader/producer và đối tác đã thử format mới, metadata và AAD; không có alg/kid confusion hoặc đường fallback tự động.
- [ ] Khóa mới đã được sinh/quản lý trên KMS/HSM hoặc kho khóa được phê duyệt; quyền và audit đã kiểm thử.
- [ ] Job migrate có idempotency, concurrency control, checkpoint và dữ liệu dự phòng; thống kê và kiểm tra tính toàn vẹn đạt yêu cầu.
- [ ] Đã kiểm tra backup/snapshot và restore thực tế với metadata và key versions còn cần; có RTO/RPO và chủ sở hữu.
- [ ] Khóa cũ không còn được dùng để mã hóa/ký mới; bản ghi chưa di trú đã có quyết định có thể kiểm toán.
- [ ] Chữ ký, certificate, JWT và TLS nếu bị ảnh hưởng đã có kế hoạch riêng cho trust, client, revoke và kiểm chứng.
- [ ] Cổng rollback không cho phép tự hạ thuật toán, bỏ xác minh hoặc dùng lại key compromised.
- [ ] Đã diễn tập outage, compromise, lost key, malformed ciphertext và lỗi KMS; giám sát và runbook hoạt động.
- [ ] Có người phê duyệt việc retired/destroyed và báo cáo được dữ liệu/consumer nào không còn phụ thuộc khóa cũ.

**Ghi nhớ:** Đổi khóa và đổi thuật toán là quá trình **thay đổi trạng thái tin cậy của cả hệ thống**, không đơn thuần là đổi config. Thiết kế metadata/version, policy ghi mới–đọc cũ, runbook và kiểm tra trước khi cần xử lý sự cố. Khôi phục thành công phải giữ nguyên ranh giới bảo mật: **chặn hành vi không được phép, xác minh đúng trước khi chấp nhận và không dùng phương án yếu để làm dịch vụ "có vẻ chạy lại".**

### Tài liệu tham khảo

- [NIST SP 800-57 Part 1 Rev. 5 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final): vòng đời khóa, cryptoperiod và biện pháp quản lý.
- [NIST SP 800-131A Rev. 2 — Transitioning the Use of Cryptographic Algorithms and Key Lengths](https://csrc.nist.gov/pubs/sp/800/131/a/r2/final): tư duy lập lộ trình nâng thuật toán và độ mạnh khóa.
- [NIST SP 800-38D — Galois/Counter Mode (GCM)](https://csrc.nist.gov/pubs/sp/800/38/d/final): yêu cầu mã hóa xác thực và IV/nonce.
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): rotation và xử lý ciphertext/backup.
- [OWASP JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html): `alg`/`kid` và trust validation.
- [Java SE 17 Cipher API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): JCA/JCE AEAD, `updateAAD` và `AEADBadTagException`.


</details>

- [Quay lại đầu trang](#back-to-top)
