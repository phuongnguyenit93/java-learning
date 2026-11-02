<a id="back-to-top"></a>

# 20. Provider và phần cứng

## Menu
- [20.1 Đăng ký provider bằng cấu hình hoặc Security API và tác động tới việc chọn dịch vụ](#security-providers-001)
- [20.2 Provider.configure, Provider.Service và ranh giới khi mở rộng SPI](#security-providers-002)
- [20.3 SunPKCS11 làm cầu nối tới thư viện PKCS#11 của thiết bị](#security-providers-003)
- [20.4 Cấu hình slot, token, đăng nhập và truy cập PKCS11 KeyStore](#security-providers-004)
- [20.5 Sử dụng private key không xuất được qua Cipher hoặc Signature](#security-providers-005)
- [20.6 Quản lý phiên thiết bị, lỗi PIN, token bị tháo và khả năng chạy đồng thời](#security-providers-006)
- [20.7 Khác biệt giữa provider phần mềm, HSM và kho khóa của hệ điều hành](#security-providers-007)
- [20.8 Phân biệt có hỗ trợ thuật toán với đáp ứng yêu cầu tuân thủ của môi trường](#security-providers-008)

## <a id="security-providers-001">20.1 Đăng ký provider bằng cấu hình hoặc Security API và tác động tới việc chọn dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

# 20.1 Đăng ký provider bằng cấu hình hoặc Security API và tác động tới việc chọn dịch vụ

## 1. Mục tiêu và mô hình tư duy

**Security provider** trong Java là một thành phần cung cấp hiện thực cho các *dịch vụ bảo mật* thuộc Java Cryptography Architecture (JCA) và những API liên quan. Ứng dụng thường gọi API chung như `MessageDigest`, `Signature`, `KeyStore`, `Cipher`, `Mac` hoặc `SSLContext` thay vì tự khởi tạo lớp triển khai thuật toán.

Cần phân biệt ba thứ:

- **Engine/API:** `MessageDigest.getInstance("SHA-256")` là lời yêu cầu một dịch vụ `MessageDigest` cho thuật toán `SHA-256`.
- **Provider:** đối tượng `java.security.Provider` có *tên*, *phiên bản*, danh sách service và lớp thực hiện tương ứng; ví dụ một provider đăng ký service theo cặp `MessageDigest/SHA-256`.
- **SPI (Service Provider Interface):** lớp thực hiện công việc thật, chẳng hạn `MessageDigestSpi`, `SignatureSpi`, `CipherSpi`. Engine ủy nhiệm hoạt động cho SPI được chọn.

Provider có thể cung cấp thuật toán bằng phần mềm, dùng thư viện hệ điều hành hoặc kết nối với phần cứng như HSM/smart card. **Đăng ký provider** chỉ làm cho Java biết provider tồn tại và có thể xét nó khi tìm dịch vụ; việc provider thật sự được chọn còn phụ thuộc thuật toán, thứ tự ưu tiên, khóa, cấu hình và khả năng của provider.

```text
Application: MessageDigest.getInstance("SHA-256")
                 |
                 v
          JCA/JCE engine registry
                 |
                 v
   Danh sách provider đã đăng ký
       ưu tiên 1 -> 2 -> 3 -> ...
                 |
        service + algorithm?
                 |
                 v
        Provider.Service / SPI
                 |
                 v
        MessageDigest instance
```

Bài này tập trung vào **cách đưa provider vào danh sách và cách danh sách đó ảnh hưởng tới lựa chọn dịch vụ**, không xây dựng thuật toán mã hóa thực tế.

## 2. Các khái niệm cần nắm trước khi cấu hình

| Khái niệm | Cách hiểu / ví dụ |
| --- | --- |
| Provider name | Tên định danh như `SUN`, `SunJCE`, `SunPKCS11` hoặc tên do vendor cấp; tên provider có phân biệt chữ hoa/thường. |
| Service type | Loại engine: `MessageDigest`, `Signature`, `Cipher`, `KeyStore`, `SecureRandom`, `SSLContext`... |
| Algorithm | Tên thuật toán/dịch vụ: `SHA-256`, `SHA256withRSA`, `AES/GCM/NoPadding`, `PKCS11`... |
| Provider order | Thứ tự ưu tiên của provider **đã đăng ký**; vị trí trong `Security.getProviders()` là chỉ số mảng bắt đầu từ 0, còn `insertProviderAt` nhận vị trí bắt đầu từ 1. |
| Provider service | Thông tin `Provider.Service` ánh xạ service type + algorithm đến lớp SPI và thuộc tính liên quan. |
| Provider selection | Quyết định engine nhận hiện thực của provider nào khi gọi `getInstance(...)`, đôi khi còn phụ thuộc bước `init`. |

Ví dụ, `SUN` và `SunJCE` là hai provider **khác nhau**, dù đều có thể xuất hiện trong cùng JDK. Không nên suy ra chỉ từ một cái tên rằng provider cung cấp mọi thuật toán hoặc mọi biến thể của thuật toán.

Một provider đã có thư viện/JAR/module trên classpath hoặc module path **chưa đồng nghĩa đã đăng ký vào danh sách ưu tiên**. Ngược lại, nếu khai báo một provider nhưng lớp không tải được hoặc provider không hỗ trợ service yêu cầu thì lời gọi `getInstance` vẫn có thể thất bại.

## 3. Cách 1: Đăng ký provider bằng cấu hình JVM

### 3.1. File security properties mặc định

Trong JDK hiện đại, file nền tảng thường nằm tại:

```text
<JAVA_HOME>/conf/security/java.security
```

Nó chứa các thuộc tính `security.provider.1`, `security.provider.2`, ... để mô tả **thứ tự ưu tiên lúc JVM khởi động**. Ví dụ mang tính minh họa:

```properties
security.provider.1=SUN
security.provider.2=SunRsaSign
security.provider.3=SunEC
# ... các provider còn lại của JDK cụ thể ...
security.provider.13=com.example.security.MyProvider
```

Ở đây, `MyProvider` là **tên lớp minh họa**, chỉ hợp lệ nếu ứng dụng thực sự có lớp provider tương ứng và nạp được lớp đó. **Số 13 không phải mặc định chung của mọi JDK**: phải xem danh sách `security.provider.n` hiện tại, chọn vị trí phù hợp và tránh ghi đè entry có sẵn. Mỗi JDK/vendor/phiên bản có thể có danh sách mặc định khác nhau.

Nếu đặt provider lên đầu danh sách, nó được xét trước cho nhiều dịch vụ; nếu đặt cuối danh sách, các provider ưu tiên cao hơn được xét trước. **Không thêm provider vào vị trí 1 tùy tiện**: thay thế một provider nền tảng có thể làm hỏng chức năng TLS, chữ ký, key store hoặc các API khác.

### 3.2. File security properties bổ sung khi khởi động

Thay vì sửa file thuộc JDK (dễ mất khi nâng cấp), có thể tạo file bổ sung, ví dụ `custom-security.properties`:

```properties
# Ví dụ: chỉ thêm khi số 13 thực sự còn trống trong JDK đang dùng.
security.provider.13=com.example.security.MyProvider
```

Sau đó khởi động:

```bash
java -Djava.security.properties=custom-security.properties -cp app.jar com.example.Main
```

Đây là **cú pháp minh họa**; lệnh chỉ chạy khi `com.example.Main` và provider vendor đã được đóng gói đúng. Cú pháp một dấu `=` sau tên system property yêu cầu JVM **nạp thêm** file so với file security properties chính; thuộc tính trùng tên trong file bổ sung sẽ ghi đè giá trị tương ứng.

Cú pháp sau có **ý nghĩa khác**:

```bash
java -Djava.security.properties==replacement.security -cp app.jar com.example.Main
```

Hai dấu `==` liên tiếp sau `properties` làm file `replacement.security` **thay thế hoàn toàn** file security properties mặc định. Nếu file thay thế thiếu provider, cấu hình TLS hoặc chính sách thuật toán, môi trường có thể không khởi động hoặc không dùng được các dịch vụ cần thiết. Chỉ dùng khi thực sự quản trị toàn bộ cấu hình bảo mật và đã kiểm thử.

Một số runtime có thể vô hiệu hóa việc nạp file bổ sung bằng `security.overridePropertiesFile=false`; kiểm tra chính sách JDK đang sử dụng. **Cấu hình static có hiệu lực khi JVM được khởi tạo**: sửa file trong lúc ứng dụng đang chạy không phải cách cập nhật chắc chắn; cần khởi động lại và xác minh.

### 3.3. `Security.setProperty` khác đăng ký provider

Hai nhóm property dễ nhầm nhau:

- `System.getProperty("java.security.properties")` đọc **Java system property** dùng để chỉ đường dẫn file cấu hình khi JVM khởi động.
- `Security.getProperty("security.provider.1")` đọc **Java security property** đã được security subsystem nạp.

Ví dụ:

```java
String configured = java.security.Security.getProperty("security.provider.1");
System.out.println(configured);
```

Kết quả tùy máy/JDK; không giả định luôn là `SUN`.

Dù có `Security.setProperty(String key, String value)`, **không dùng `Security.setProperty("security.provider.N", "...")` để mong danh sách provider tự đổi trong một JVM đã chạy**. Danh sách provider được thiết lập khi khởi tạo; nếu muốn thêm/bớt/đổi ưu tiên **trong chương trình đang chạy**, dùng API đăng ký provider ở mục tiếp theo.

## 4. Cách 2: Đăng ký và điều chỉnh bằng `java.security.Security`

```java
import java.security.Provider;
import java.security.Security;

// Chỉ minh họa các API. MyProvider do thư viện thực tế cung cấp.
Provider custom = new MyProvider();
int endPosition = Security.addProvider(custom);
int firstPosition = Security.insertProviderAt(new OtherProvider(), 1);
Provider[] ordered = Security.getProviders();
Provider byName = Security.getProvider("MyProvider");
Security.removeProvider("MyProvider");
```

Đây là **đoạn API minh họa**, không tự biên dịch nếu chưa có `MyProvider` và `OtherProvider`.

| API | Tác dụng và kết quả |
| --- | --- |
| `Security.addProvider(provider)` | Thêm provider vào **cuối** danh sách. Trả vị trí 1-based nếu thêm được; `-1` nếu provider cùng tên đã đăng ký. |
| `Security.insertProviderAt(provider, position)` | Chèn provider vào vị trí **1-based** mong muốn; các provider phía sau dịch xuống. Trả vị trí thực tế; `-1` nếu đã đăng ký cùng tên. |
| `Security.removeProvider(name)` | Gỡ provider khỏi danh sách đăng ký; không có lỗi chỉ vì không tìm thấy tên. |
| `Security.getProviders()` | Lấy snapshot danh sách provider theo thứ tự ưu tiên. |
| `Security.getProvider(name)` | Lấy provider đang đăng ký theo tên hoặc `null` nếu không có. |
| `Security.getProviders("MessageDigest.SHA-256")` | Lọc các provider khai báo service/algorithm tương ứng; có thể trả `null` nếu không có kết quả. |

**Cần nhớ:**

1. Nếu provider đã đăng ký, gọi `addProvider` hoặc `insertProviderAt` với tên trùng không đưa nó đến vị trí mới. Muốn thay đổi vị trí phải **remove rồi insert lại**, cân nhắc tác động đến toàn JVM.
2. Thay đổi bằng `Security` API ảnh hưởng **phạm vi JVM**, không chỉ một thread/request. Nên cấu hình lúc khởi tạo ứng dụng, trước khi các subsystem đồng thời tạo engine instances.
3. Engine instance đã tạo được gắn với một triển khai/provider cụ thể; không giả định việc đổi danh sách sẽ tự chuyển instance đang sử dụng. Các lệnh `getInstance` tiếp theo mới chịu ảnh hưởng từ thứ tự hiện hành.
4. Việc gỡ provider không xóa file JAR, không xóa khóa và không hủy session tới thiết bị. Đây là thay đổi **registration**, không phải lệnh quản trị phần cứng.
5. Chỉ đăng ký provider đến từ nguồn đáng tin. Một provider ưu tiên cao cho `Signature`, `Cipher`, `Mac` hoặc `SecureRandom` có thể xử lý khóa và dữ liệu nhạy cảm.

## 5. Cơ chế lựa chọn dịch vụ khi gọi `getInstance`

Với engine có hỗ trợ provider selection, các dạng điển hình là:

```java
MessageDigest a = MessageDigest.getInstance("SHA-256");
MessageDigest b = MessageDigest.getInstance("SHA-256", "SUN");
MessageDigest c = MessageDigest.getInstance("SHA-256", providerObject);
```

Đây là các **biểu thức minh họa API**; `providerObject` phải được cung cấp trước, và tên `SUN` chỉ dùng khi runtime thật có provider đó.

- **Không chỉ rõ provider:** JCA tìm một provider hỗ trợ loại dịch vụ + thuật toán, thường duyệt theo thứ tự ưu tiên. Provider không có service này bị bỏ qua. Ví dụ `MessageDigest.getInstance("SHA-256")` lấy một implementation phù hợp.
- **Chỉ rõ tên provider:** `getInstance(algorithm, "ProviderName")` bỏ qua cuộc tìm kiếm tự động, yêu cầu provider **đã đăng ký** có tên này. Không có provider thì ném `NoSuchProviderException`; provider có nhưng không hỗ trợ thuật toán thì thường ném `NoSuchAlgorithmException`.
- **Truyền đối tượng `Provider`:** `getInstance(algorithm, providerObject)` dùng trực tiếp đối tượng; đối với nhiều engine, provider object **không nhất thiết đã có tên trong registry**, miễn là có service và được sử dụng đúng theo API. Phù hợp khi cần ràng buộc một provider cụ thể có cấu hình riêng.

Ví dụ quan sát provider được chọn:

```java
MessageDigest md = MessageDigest.getInstance("SHA-256");
System.out.println(md.getProvider().getName());
```

Đây là đoạn minh họa: stdout **phụ thuộc JDK và cấu hình**, không nên ghi cứng tên provider vào bài kiểm tra chạy đa môi trường.

### 5.1. Thứ tự ưu tiên là quan trọng nhưng không phải quy tắc duy nhất

- Nếu provider ưu tiên số 1 không hỗ trợ `SHA-256`, JCA bỏ qua nó và xét provider tiếp theo.
- Nếu nhiều provider hỗ trợ cùng service/algorithm, thứ tự có ảnh hưởng lớn khi gọi không chỉ rõ provider.
- Java có thể có chính sách ưu tiên **theo từng thuật toán** qua security property `jdk.security.provider.preferred`. Property này có thể khiến lựa chọn cho service cụ thể **khác với thứ tự** mà `Security.getProviders()` hiển thị; property đó không tự đăng ký provider.
- Với một số engine như `Cipher`, `Signature`, `Mac`, `KeyAgreement`, JDK có thể **trì hoãn chọn provider** đến `init(...)` để kiểm tra tính tương thích với `Key`. Vì vậy, nếu cần biết provider thật sự được dùng, hãy kiểm tra `getProvider()` **sau khi khởi tạo bằng khóa**. Một `getProvider()` quá sớm còn có thể tác động tới hành vi lựa chọn.
- Provider xuất hiện trong danh sách không bảo đảm mọi khóa, mode, padding hay thiết bị đều sử dụng được. Chẳng hạn khóa HSM không xuất ra được có thể chỉ được provider HSM tương ứng xử lý.

Khi muốn dự đoán provider thực sự: **kiểm tra service + thuật toán + các ưu tiên chuyên biệt + điều kiện khởi tạo**, thay vì chỉ nhìn vị trí trong mảng.

### 5.2. Ma trận kết quả thường gặp

| Yêu cầu | Điều xảy ra |
| --- | --- |
| Không chỉ định provider; A ưu tiên 1 hỗ trợ `DEMO8`, B ưu tiên 2 cũng hỗ trợ | Với demo `MessageDigest` đơn giản, chọn A. |
| Chèn B vào vị trí 1, A xuống sau | Lời gọi `getInstance("DEMO8")` mới chọn B. |
| `getInstance("DEMO8", "DemoA")` | Chọn A dù B đứng trước, miễn A đang đăng ký và hỗ trợ thuật toán. |
| Không provider nào hỗ trợ `DEMO8` | `NoSuchAlgorithmException`. |
| Chỉ đích danh provider không đăng ký | `NoSuchProviderException` với overload nhận tên. |
| Provider có tên đúng nhưng không cung cấp thuật toán | `NoSuchAlgorithmException` thay vì tự chuyển sang provider khác khi đã pin. |
| Provider hỗ trợ thuật toán nhưng không tương thích khóa | Bước `init` có thể chọn provider khác (nếu không pin và engine hỗ trợ delayed selection) hoặc báo lỗi khóa/cấu hình. |

## 6. Demo Java 17+: cả hai cách đăng ký và lựa chọn provider

Để thực nghiệm độc lập, ta tạo một **provider đồ chơi** cung cấp service `MessageDigest.DEMO8`. SPI `ToyDigest` chỉ cộng byte modulo 256: **đây không phải hàm băm bảo mật, không được dùng thay SHA-256 hoặc bất kỳ thuật toán mật mã thật nào**. Hai provider `DemoA` và `DemoB` dùng chung một SPI để so sánh cách chọn implementation mà không tạo ra kết quả mật mã khác nhau.

Lưu toàn bộ mã sau trong **`ProviderSelectionDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;

public class ProviderSelectionDemo {
    public static final class DemoProvider extends Provider {
        // Constructor không tham số để thử đăng ký bằng file cấu hình.
        public DemoProvider() {
            this("DemoA");
        }

        public DemoProvider(String name) {
            super(name, "1.0", "Provider for an educational checksum only");
            put("MessageDigest.DEMO8", ToyDigest.class.getName());
        }
    }

    // SPI minh họa; hoàn toàn không có tính an toàn mật mã.
    public static final class ToyDigest extends MessageDigestSpi {
        private int sum;

        @Override
        protected void engineUpdate(byte value) {
            sum = (sum + (value & 0xff)) & 0xff;
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                engineUpdate(input[i]);
            }
        }

        @Override
        protected byte[] engineDigest() {
            byte[] result = {(byte) sum};
            engineReset();
            return result;
        }

        @Override
        protected void engineReset() {
            sum = 0;
        }

        @Override
        protected int engineGetDigestLength() {
            return 1;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--from-config")) {
            Provider configured = Security.getProvider("DemoA");
            System.out.println("Configured: " + (configured != null));
            System.out.println("Selected: " +
                MessageDigest.getInstance("DEMO8").getProvider().getName());
            return;
        }

        Provider first = new DemoProvider("DemoA");
        Provider priority = new DemoProvider("DemoB");

        System.out.println("Added DemoA: " +
            (Security.addProvider(first) > 0));
        System.out.println("Inserted DemoB at 1: " +
            (Security.insertProviderAt(priority, 1) == 1));

        MessageDigest defaultDigest = MessageDigest.getInstance("DEMO8");
        MessageDigest namedDigest = MessageDigest.getInstance("DEMO8", "DemoA");
        System.out.println("Default provider: " +
            defaultDigest.getProvider().getName());
        System.out.println("Explicit provider: " +
            namedDigest.getProvider().getName());
        System.out.println("Checksum abc: " + HexFormat.of().formatHex(
            defaultDigest.digest("abc".getBytes(StandardCharsets.US_ASCII))));

        Security.removeProvider("DemoB");
        System.out.println("After removal: " +
            MessageDigest.getInstance("DEMO8").getProvider().getName());
    }
}
```

Biên dịch bằng JDK 17 trở lên:

```bash
javac --release 17 ProviderSelectionDemo.java
java ProviderSelectionDemo
```

Kết quả kiểm chứng khi chạy từ môi trường JDK thông thường chưa đăng ký sẵn `DemoA`/`DemoB`:

```text
Added DemoA: true
Inserted DemoB at 1: true
Default provider: DemoB
Explicit provider: DemoA
Checksum abc: 26
After removal: DemoA
```

Giải thích:

1. `addProvider(DemoA)` đăng ký ở cuối.
2. `insertProviderAt(DemoB, 1)` đặt B lên đầu, đẩy các provider khác xuống.
3. `getInstance("DEMO8")` chọn B vì đây là service đơn giản có ở cả A và B.
4. `getInstance("DEMO8", "DemoA")` chọn A bất kể thứ tự.
5. `'a' + 'b' + 'c' = 97 + 98 + 99 = 294`; modulo 256 là 38, hay `0x26`, nên in `26`.
6. Sau khi gỡ B khỏi registry, lời gọi mới chọn A.

`DemoA`/`DemoB` là tên kiểm soát trong demo, không nằm trong danh sách chuẩn Java. Khi dùng provider thực, tên lấy từ `provider.getName()`, còn danh sách thuật toán từ `provider.getServices()`.

### 6.1. Chạy chính demo bằng cấu hình security properties

Giữ nguyên `ProviderSelectionDemo.java` đã biên dịch. Tạo file **`demo-provider.security`** ở cùng thư mục:

```properties
security.provider.13=ProviderSelectionDemo$DemoProvider
```

Dấu `$` là một phần tên **binary class của nested static class** trong Java, không phải biến shell. Ví dụ giả định vị trí `13` đang trống; **phải điều chỉnh số thứ tự** nếu file `java.security` của JDK thật có cấu trúc khác. Đây là ví dụ học tập, không phải cấu hình khuyến nghị cho dự án thực.

Chạy một **JVM mới** với file bổ sung:

```bash
java -Djava.security.properties=demo-provider.security -cp . ProviderSelectionDemo --from-config
```

Khi provider được nạp thành công, stdout là:

```text
Configured: true
Selected: DemoA
```

Ở nhánh `--from-config`, chương trình **không gọi** `Security.addProvider`; kết quả `Configured: true` chứng minh provider được đọc qua security properties lúc khởi động. Cách này đã được kiểm chứng trên JDK có `security.provider.13` còn trống. Nếu lớp `DemoProvider` không có trên classpath/module path hoặc khai báo sai tên, kết quả sẽ khác hoặc `getInstance` thất bại.

Với provider bên thứ ba có tên như `com.vendor.crypto.VendorProvider`, thông thường cần cung cấp JAR/module đúng tài liệu vendor, dùng tên lớp mà provider hỗ trợ trong `security.provider.n` và kiểm thử ở JVM thực tế. Khi chạy trong module có tên, việc cung cấp và tải provider phải tương thích cấu hình module/service của thư viện.

## 7. Trường hợp thực tế: provider gắn với PKCS#11, HSM và smart card

Trong chủ đề **Provider và phần cứng**, `SunPKCS11` là ví dụ quan trọng: Java dùng nó như lớp kết nối từ JCA/JCE tới **thư viện PKCS#11 native của vendor**. Nó không tự thay thế thư viện thiết bị và không biến mọi thuật toán/khóa thành thao tác HSM.

Một file cấu hình PKCS#11 thường có dạng minh họa:

```properties
name=MyToken
library=/opt/vendor/lib/libpkcs11.so
slotListIndex=0
```

Tên thư viện, slot và khả năng token phải lấy từ vendor. Cấu hình trên **không chạy được nếu không có đúng thiết bị, thư viện native và driver**. Đường dẫn `.so` minh họa Linux; trên hệ điều hành khác sẽ là thư viện tương ứng.

Đăng ký động theo API của `Provider.configure` (đoạn minh họa yêu cầu phần cứng thật):

```java
import java.security.Provider;
import java.security.Security;

Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("This JDK has no SunPKCS11 provider");
}

Provider tokenProvider = base.configure("/opt/app/conf/pkcs11.cfg");
int position = Security.addProvider(tokenProvider);
if (position < 0) {
    throw new IllegalStateException("Configured token provider already registered");
}
System.out.println("Registered provider: " + tokenProvider.getName());
```

**Phải đăng ký đối tượng do `configure` trả về**; không bỏ qua giá trị trả về rồi đăng ký nhầm `base`, vì `configure` có thể tạo provider mới đã được cấu hình. Muốn ưu tiên provider HSM, cần chính sách cụ thể về thứ tự hoặc chỉ định provider trực tiếp:

```java
// Đoạn minh họa: tokenProvider được tạo như ở trên.
java.security.KeyStore tokenStore =
    java.security.KeyStore.getInstance("PKCS11", tokenProvider);
```

Đoạn này chưa đăng nhập token hoặc load keystore; thực tế có thể cần PIN/callback, thiết bị và session management.

Một cách cấu hình tĩnh (dành cho JDK/vendor hỗ trợ) là entry như:

```properties
security.provider.13=SunPKCS11 /opt/app/conf/pkcs11.cfg
```

Đây là **phương án thay thế** ví dụ `ProviderSelectionDemo$DemoProvider` ở mục trước, không đồng thời dùng hai giá trị khác nhau tại `security.provider.13`.

**Hệ quả vận hành:** nếu yêu cầu khóa **không xuất khỏi HSM** hoặc bắt buộc dùng provider đã được chứng nhận, chỉ chèn provider vào thứ tự cao hơn là chưa đủ. Cần xác định rõ provider cho `KeyStore`, sinh/tải khóa bằng API phù hợp, dùng `Signature`/`Cipher` có khả năng xử lý khóa token, xác minh provider **sau `init`**, và từ chối fallback sang phần mềm nếu chính sách cấm.

## 8. Kiểm tra provider và chẩn đoán việc lựa chọn

### 8.1. Liệt kê runtime đang có

Đoạn code hoàn chỉnh ngắn sau có thể lưu thành `InspectProviders.java`:

```java
import java.security.Provider;
import java.security.Security;

public class InspectProviders {
    public static void main(String[] args) {
        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            Provider p = providers[i];
            System.out.printf("%d. %s (%s)%n",
                i + 1, p.getName(), p.getVersionStr());
        }

        Provider[] digestProviders =
            Security.getProviders("MessageDigest.SHA-256");
        System.out.println("SHA-256 implementations: " +
            (digestProviders == null ? 0 : digestProviders.length));
    }
}
```

```bash
javac --release 17 InspectProviders.java
java InspectProviders
```

Không có stdout cố định cho lệnh này: danh sách và phiên bản provider phụ thuộc runtime, module, vendor và thiết lập startup. Đây là **điều cần quan sát** khi debug, không phải lỗi vì số lượng khác máy hướng dẫn.

Muốn kiểm tra một service cụ thể của provider:

```java
Provider p = Security.getProvider("SUN");
if (p != null) {
    Provider.Service service = p.getService("MessageDigest", "SHA-256");
    System.out.println(service == null ? "Not provided" : service.getClassName());
}
```

Đây là snippet minh họa cần đặt trong chương trình có import `java.security.*`; tên provider và service có thể không tồn tại ở JDK khác. `getServices()` liệt kê những gì provider **công bố**; không bảo đảm token phần cứng đang online hoặc mọi khóa tương thích.

### 8.2. Gỡ lỗi từ lệnh khởi động

Ở các JDK hỗ trợ những cờ sau:

```bash
java -Djava.security.debug=properties -version
java -XshowSettings:security -version
java -Djava.security.debug=jca -cp . ProviderSelectionDemo
```

`properties` giúp xem giá trị security properties thực tế, `-XshowSettings:security` giúp kiểm tra thiết lập bảo mật của JDK, còn `jca` hữu ích khi phân tích provider selection và parse cấu hình ưu tiên chuyên biệt. Mức chi tiết/log và cờ được hỗ trợ có thể thay đổi theo JDK; chạy trong môi trường kiểm thử và **không đăng log bí mật hoặc nội dung khóa**.

Quy trình debug hiệu quả:

1. Xác minh **đúng JVM đang chạy** (`java -version`, `java.home`) và file security properties tương ứng.
2. Xác minh provider có trong `Security.getProviders()`, tên/phiên bản/vị trí cụ thể.
3. Xác minh `getService(type, algorithm)` hoặc `Security.getProviders(filter)` thấy service yêu cầu.
4. Kiểm tra việc chỉ định provider rõ ràng, `jdk.security.provider.preferred` và cấu hình chèn provider động.
5. Với `Cipher/Signature/Mac/KeyAgreement`, kiểm tra chọn provider **sau** bước `init` với key thật.
6. Nếu dùng HSM, xác minh native library, kiến trúc 32/64-bit, token/slot, session, quyền truy cập và key compatibility.

## 9. Lỗi thường gặp và cách xử lý

| Lỗi / ngộ nhận | Vì sao sai và cách sửa |
| --- | --- |
| “Đã copy JAR provider nên Java tự chọn nó.” | Có thư viện chưa đủ; cần đăng ký đúng cách và xác nhận service có mặt. |
| “`addProvider` sẽ đưa provider mới lên đầu.” | Nó thêm cuối danh sách; dùng `insertProviderAt(..., 1)` nếu thực sự cần ưu tiên cao. |
| “Chạy `insertProviderAt` hai lần sẽ dời provider lên vị trí mới.” | Provider có cùng tên đã đăng ký thì lần sau thường trả `-1`; muốn thay thứ tự cần remove rồi insert. |
| “`security.provider.13` luôn là ô trống.” | Vị trí tùy JDK. Kiểm tra file gốc trước khi bổ sung. |
| “`System.setProperty` hoặc `Security.setProperty("security.provider.N", ...)` sẽ lập tức cài provider.” | Không thay đổi đáng tin cậy registry đã xây; dùng `addProvider`/`insertProviderAt` trong JVM đang chạy. |
| “Gọi `getInstance` không chỉ rõ provider luôn chọn phần tử 1.” | Phải xét service/algorithm, chính sách ưu tiên riêng và khả năng khởi tạo bằng khóa. |
| “`getProvider()` trước `Cipher.init` đã cho biết chính xác provider chạy mã hóa.” | Có thể bị delayed selection; quan sát sau `init` với key thực tế. |
| “Gỡ provider sẽ thay provider của engine đã tạo.” | Engine đã tạo có trạng thái riêng; gỡ registry ảnh hưởng những lần phân giải mới. |
| “Provider đứng đầu thì chắc chắn mọi thao tác đều qua HSM.” | HSM có giới hạn cơ chế, token, key và session. Kiểm tra provider thực tế và không fallback trái chính sách. |
| “Hai dấu `==` trong `-Djava.security.properties==file` cũng chỉ thêm file.” | Nó thay thế cấu hình mặc định; có nguy cơ vô hiệu các provider/thiết lập bảo mật nền tảng. |
| “`NoSuchProviderException` và `NoSuchAlgorithmException` là một.” | Lỗi thứ nhất có thể do provider chỉ đích danh không đăng ký; lỗi thứ hai do không tìm thấy implementation thuật toán trong phạm vi tìm kiếm. |

Ngoài ra, thay đổi registry ở một ứng dụng server đang xử lý đồng thời có thể làm các request tạo instance mới nhận provider khác nhau. Quản lý provider nên là phần **bootstrap/điều hành cấu hình**, có kiểm thử khởi động và quan sát runtime, tránh tùy ý thay từ request nghiệp vụ.

## 10. Nguyên tắc sử dụng trong dự án thực

- **Mặc định ưu tiên API độc lập provider** (`getInstance(algorithm)`) khi không có ràng buộc chuyên biệt: dễ di chuyển giữa JDK/OS và có thể tận dụng triển khai phần cứng hoặc tối ưu hóa mới.
- **Pin provider có chủ đích** (`getInstance(algorithm, provider)`) khi bắt buộc dùng HSM, PKCS#11, tiêu chuẩn chứng nhận hoặc khóa không xuất khỏi thiết bị. Cần fail closed khi provider mong muốn vắng mặt; không tự chuyển sang provider khác nếu điều đó vi phạm chính sách.
- **Đăng ký một lần khi khởi động**, xác minh tên provider, service bắt buộc và quá trình `init` với loại key thật. Lập tài liệu về thứ tự, môi trường và quá trình rollback.
- **Đối chiếu giữa môi trường**: phát triển, CI và production có thể chạy JDK vendor/version hoặc module image khác nhau; không viết test dựa vào số provider hay tên triển khai JDK cố định.
- **Bảo vệ chuỗi cung ứng provider**: dùng thư viện có nguồn gốc tin cậy, kiểm soát phiên bản, chữ ký/phụ thuộc và đường dẫn native; provider là thành phần nhạy cảm của đường đi mật mã.
- **Không nhầm ưu tiên với chính sách thuật toán**: dù provider có thể cung cấp thuật toán, các ràng buộc TLS/chứng chỉ, lựa chọn mode/padding và chính sách bảo mật còn có thể ngăn việc sử dụng.

## 11. Câu hỏi tự kiểm tra

1. **Tại sao `Security.addProvider(p)` trả `-1`?** Thường vì đã có provider cùng tên trong registry; không thể dùng lời gọi đó để đổi ưu tiên.
2. **Tại sao `MessageDigest.getInstance("SHA-256")` có thể trả provider khác nhau trên hai máy?** Vì danh sách/phiên bản provider, policy và thuật toán được hỗ trợ khác nhau.
3. **Phân biệt một dấu `=` và hai dấu `==` trong `-Djava.security.properties`?** Một dấu nạp file bổ sung vào master; hai dấu thay toàn bộ master.
4. **Vì sao `Security.setProperty("security.provider.1", "...")` không phải cách đáng tin để đăng ký provider khi ứng dụng đã chạy?** Vì registry provider đã được xây; phải dùng các phương thức thao tác registry.
5. **Khi nào nên truyền đối tượng `Provider` vào `getInstance`?** Khi có yêu cầu dùng đúng triển khai/cấu hình, đặc biệt provider token/HSM có cấu hình riêng.
6. **Vì sao provider được in ra trước `Signature.initSign(privateKey)` có thể khác provider thực sự thực hiện ký?** Vì JCA có thể trì hoãn lựa chọn đến lúc biết khóa và khả năng hỗ trợ của provider.
7. **Vì sao nhà cung cấp HSM cần thêm bước xác minh ngoài `Security.getProviders()`?** Vì token, native PKCS#11 library, khóa không xuất, cơ chế ký, session và PIN phải hoạt động thật.
8. **Muốn chứng minh provider static đã được đăng ký, xem gì?** Chạy JVM mới, đọc `Security.getProvider(name)` và gọi một service cụ thể không hề đăng ký động trong chương trình.

## 12. Tài liệu tham khảo chính thức

- [Java SE 21 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html)
- [Java SE 21 — Security Properties File](https://docs.oracle.com/en/java/javase/21/security/security-properties-file.html)
- [Java SE 21 — `java.security.Security` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Security.html)
- [Java SE 21 — `java.security.Provider` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Provider.html)
- [Java SE 21 — JDK Providers](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html)
- [Java SE 21 — PKCS#11 Reference Guide](https://docs.oracle.com/en/java/javase/21/security/pkcs11-reference-guide.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-002">20.2 Provider.configure, Provider.Service và ranh giới khi mở rộng SPI</a>

<details>
<summary>Xem chi tiết</summary>

# 20.2. Provider.configure, Provider.Service và ranh giới khi mở rộng SPI

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

1. Phân biệt **JCA/JCE engine API**, **Provider**, **Provider.Service** và **SPI implementation**; giải thích vì sao bốn thành phần này không hoán đổi vai trò cho nhau.
2. Giải thích chính xác `Provider.configure(String)`, `Provider.isConfigured()` và yêu cầu **phải sử dụng Provider mà configure trả về**.
3. Đọc/đăng ký `Provider.Service` bằng `getService`, `getServices`, `putService`; hiểu type, algorithm, implementation class, alias, attribute, `newInstance` và `supportsParameter`.
4. Xác định ranh giới khi viết provider mới: khi nào cần subclass `Provider`, khi nào cần subclass một `*Spi`, khi nào chỉ cần dùng provider có sẵn hoặc cấu hình HSM/PKCS#11.
5. Viết và chạy ví dụ Java 17+ minh họa hai API nêu trên mà không cần thiết bị bảo mật; nhận diện khác biệt với cấu hình token thật.

**Phạm vi:** đây là phần mở rộng JCA provider và SPI trong Java, đặt trong ngữ cảnh provider phần mềm/phần cứng. Kiến thức về thuật toán mật mã chỉ phục vụ giải thích hợp đồng kỹ thuật; bài không yêu cầu tự thiết kế thuật toán mã hóa thực tế.

## 2. Bản đồ trách nhiệm: engine, provider, service và SPI

JCA (Java Cryptography Architecture) cho phép ứng dụng yêu cầu một **dịch vụ bảo mật theo tên** thay vì gọi trực tiếp lớp triển khai của từng nhà cung cấp.

~~~text
Ứng dụng
   |
   | MessageDigest.getInstance("SHA-256")
   v
JCA engine API (MessageDigest, Signature, Cipher, KeyStore, ...)
   |
   | Chọn Provider được đăng ký hoặc Provider chỉ định tường minh
   v
Provider (tên, phiên bản, thông tin, danh mục dịch vụ)
   |
   | getService(type, algorithm)
   v
Provider.Service (type + algorithm + class + alias + attributes)
   |
   | newInstance / cơ chế tạo implementation thích hợp
   v
SPI implementation (MessageDigestSpi, SignatureSpi, CipherSpi, ...)
   |
   v
Code phần mềm / thư viện native / PKCS#11 / HSM / token
~~~

Ví dụ `MessageDigest.getInstance("SHA-256")` trả về **engine facade** là `MessageDigest`. JCA giải quyết provider hỗ trợ `MessageDigest/SHA-256`, tạo hoặc gắn một `MessageDigestSpi` tương ứng và đưa ra API thống nhất `update`, `digest`. Code nghiệp vụ thông thường chỉ làm việc với engine; không tự khởi tạo `MessageDigestSpi`.

Ba loại “service” dễ bị lẫn:

- **JCA service type**: chuỗi nhận diện nhóm API, ví dụ `MessageDigest`, `Signature`, `Cipher` hoặc `KeyStore`.
- **Provider.Service**: bản mô tả một service mà một `Provider` công bố; đây là metadata và factory hook.
- **Java ServiceLoader service**: cơ chế đóng gói/tìm lớp triển khai qua `META-INF/services` hoặc `provides ... with ...` trong Java module system. Đây là **cách phân phối/phát hiện provider**, không thay cho `Provider.Service` của từng thuật toán.

Hai phương thức có tên liên quan nhưng nghĩa khác hẳn:

~~~java
Provider configured = provider.configure("..."); // cấu hình Provider
Provider.Service service =
        configured.getService("MessageDigest", "SHA-256"); // tra một service
~~~

`configure` không phải `getService`; `Service` không phải interface SPI; và SPI không quyết định chính sách chọn provider của toàn JVM.

## 3. Provider: bộ danh mục và điểm mở rộng ở cấp nhà cung cấp

`java.security.Provider` là abstract class (kế thừa `Properties`). Một provider có:

| Thuộc tính/trách nhiệm | Ý nghĩa |
| --- | --- |
| `getName()` | Tên định danh provider, ví dụ các tên của JDK hoặc provider vendor |
| `getVersionStr()` | Chuỗi phiên bản của provider; dùng API mới thay cho `getVersion()` đã deprecated |
| `getInfo()` | Mô tả người đọc được, **không** phải chứng nhận an toàn |
| `getService(type, algorithm)` | Tìm service theo type và algorithm/alias, trả `null` nếu không tồn tại |
| `getServices()` | Xem tập `Provider.Service` không thể sửa qua tập trả về |
| `putService(Service)` | Đăng ký service trong subclass `Provider`; phương thức `protected` |
| `removeService(Service)` | Gỡ đăng ký service theo hợp đồng của Provider; cũng `protected` |
| `configure(arg)` | Cấu hình provider nếu provider cụ thể hỗ trợ |
| `isConfigured()` | Hỏi provider đã đủ cấu hình để sử dụng hay chưa |

**Provider không tự động bằng HSM**. Provider có thể triển khai thuật toán thuần Java, gọi mã native, trung gian tới hệ điều hành hoặc thiết bị phần cứng. Một provider cũng có thể chỉ hỗ trợ một phần nhỏ các service/algorithm. Việc một engine/algorithm xuất hiện trong metadata chưa bảo đảm phần cứng đang cắm, key đang hợp lệ, PIN đã được mở hoặc một thao tác sẽ thành công.

Các JDK thường có provider mặc định và danh sách thứ tự ưu tiên. Không giả định mọi runtime, JDK vendor hay policy đều có cùng các tên provider và thuật toán.

## 4. `Provider.configure(String)`: cấu hình và giá trị trả về

Chữ ký:

~~~java
public Provider configure(String configArg)
~~~

Quy tắc quan trọng:

- `configure` là API **tùy chọn** cho subclass `Provider`. Triển khai mặc định ném `UnsupportedOperationException`; không nên gọi bừa trên provider bất kỳ.
- Khi subclass hỗ trợ cấu hình, `configArg` có ngữ nghĩa **do chính provider đó quy định**: có thể là đường dẫn file cấu hình, tên profile hoặc tham số riêng vendor. API JCA không định nghĩa một cú pháp cấu hình chung cho tất cả provider.
- Provider có thể tự cập nhật trạng thái và trả lại chính `this` **hoặc** tạo **Provider mới** rồi trả về. Do đó luôn lưu kết quả: `Provider configured = base.configure(arg);`.
- Không có quy tắc rằng `configure` tự đăng ký kết quả với `Security`. **Cấu hình** và **đăng ký toàn JVM** là hai thao tác khác nhau.
- Đối số `null` không hợp lệ; cấu hình sai có thể dẫn tới `InvalidParameterException` hoặc lỗi vendor cụ thể. Đối với provider không hỗ trợ, thường là `UnsupportedOperationException`.

`Provider.isConfigured()` có mặc định trả `true`; provider cần cấu hình tường minh có thể override để báo `false` trước khi hoàn tất. Vì vậy:

~~~java
Provider base = /* đã lấy từ đúng provider/vendor */;
Provider configured = base.configure("/path/to/provider-config");
if (!configured.isConfigured()) {
    throw new IllegalStateException("Provider chưa sẵn sàng");
}
~~~

Đây là **đoạn minh họa**, không phải chương trình chạy độc lập: cú pháp `configArg` phải được thay đúng theo provider thực tế. Không suy từ `isConfigured() == true` rằng mọi token, key hoặc cơ chế crypto đang sẵn sàng ở thời điểm gọi.

### Vì sao giữ Provider trả về là bắt buộc?

Giả sử `base.configure("token-A")` trả về instance `configured` và giữ `base` không đổi. Nếu tiếp tục gọi `Security.addProvider(base)` hoặc `Cipher.getInstance(..., base)`, bạn đang sử dụng **đối tượng sai**. Việc cấu hình đã thành công cho `configured` không đồng nghĩa `base` tự nhận được service hay trạng thái mới.

Trong code quản trị provider, nên xem instance sau cấu hình là danh tính được truyền xuyên suốt tới bước chọn engine, đăng ký và kiểm tra service. Nếu configure có thể sinh provider mang tên khác, cần kiểm tra `getName()` của **instance trả về**.

## 5. `Provider.Service` chứa những gì?

`Provider.Service` đại diện cho **một triển khai được công bố**. Constructor phổ biến:

~~~java
new Provider.Service(
        provider,
        "MessageDigest",                    // type
        "LEARN-SUM32",                     // algorithm
        "org.example.crypto.Sum32Spi",     // implementation class
        java.util.List.of("LEARN-SUM"),    // aliases
        java.util.Map.of("ImplementedIn", "Software") // attributes
);
~~~

Giải nghĩa:

- `provider`: đúng Provider sở hữu service, không phải provider khác hoặc bản cấu hình cũ.
- `type`: nhóm engine có hợp đồng SPI tương ứng, ví dụ `MessageDigest`.
- `algorithm`: tên thuật toán/biến thể mà ứng dụng có thể yêu cầu; cần ổn định và đúng ngữ nghĩa.
- `className`: tên đầy đủ của lớp Java triển khai **SPI phù hợp**, có thể cần public constructor, khả năng truy cập/module phù hợp hoặc factory tùy biến.
- `aliases`: tên thay thế trỏ về cùng service; alias giúp tương thích tên gọi, không tạo thuật toán mới.
- `attributes`: metadata chuỗi như `ImplementedIn` hoặc các thuộc tính về key support mà JCA nhận biết. Đây không phải bản chứng thực tự động rằng provider thực sự hỗ trợ mọi khóa/thiết bị.

`Service` có getter `getType()`, `getAlgorithm()`, `getProvider()`, `getClassName()`, `getAttribute(name)` và hai behavior hook `newInstance(parameter)`, `supportsParameter(parameter)`. Instance `Service` là mô tả bất biến theo API; danh mục service của `Provider` có thể thay khi provider sửa đăng ký theo hợp đồng.

`Provider.getService("MessageDigest", "learn-sum")` có thể tìm được service qua alias và đối sánh tên algorithm không phân biệt hoa/thường. Nếu không tìm được, trả `null`; không tự tải class mới từ Maven và không tự cấu hình HSM.

**Ranh giới quan trọng:** tự tạo một `Provider.Service` object chưa làm nó xuất hiện trong danh mục. Provider subclass phải đăng ký nó bằng `putService`, hoặc sử dụng cơ chế đăng ký properties tương thích. Không dùng `new Provider.Service(...)` như thay thế cho `putService(...)`.

## 6. `newInstance` và `supportsParameter`: hook của framework, không phải API nghiệp vụ

**`Provider.Service.newInstance(Object constructorParameter)`** là factory method mà framework sử dụng để tạo SPI implementation. Triển khai mặc định dùng reflection với constructor theo yêu cầu của engine. Đối với đa số standard JCA services, tham số constructor là `null` và implementation cần no-args constructor phù hợp; `CertStore` là ngoại lệ tiêu biểu dùng `CertStoreParameters`.

Khi tích hợp HSM, subclass của `Provider.Service` có thể override `newInstance` để:

- Trả SPI đã được gắn đúng token/session hoặc context của provider.
- Chọn implementation theo capabilities thiết bị.
- Thực hiện phần khởi tạo provider-specific và chuyển lỗi thành ngoại lệ thích hợp.

Khi override, vẫn phải tuân thủ **hợp đồng constructorParameter của engine** và loại implementation mong đợi. Không dùng factory này để trả về tùy tiện một Java object không phải SPI hoặc để bỏ qua validation của engine.

**`Provider.Service.supportsParameter(Object parameter)`** là phép thử khả năng hỗ trợ nhanh, thường liên quan `Key` cho các service như `Signature`, `Cipher`, `Mac`, `KeyAgreement`. Quy tắc trả về:

- `false`: service không thể dùng tham số đó.
- `true`: có khả năng dùng được, **hoặc** chưa thể xác định nhanh; không phải lời bảo đảm rằng thao tác cuối cùng sẽ thành công.
- Provider có thể dùng thuộc tính `SupportedKeyFormats`, `SupportedKeyClasses` hoặc override phương thức để kiểm tra chính xác hơn.

Không dùng `supportsParameter` như kiểm tra bảo mật toàn diện thay cho `initSign`, `initVerify`, `init` hay thao tác phần cứng thực. Với các service khác, việc gọi hook này có thể không có ý nghĩa hoặc gây `InvalidParameterException` theo API.

## 7. Ranh giới kỹ thuật khi triển khai SPI

Mỗi JCA engine công bố API cho ứng dụng và một SPI cho provider:

| Engine ứng dụng gọi | Lớp SPI mà provider triển khai | Loại trách nhiệm |
| --- | --- | --- |
| `MessageDigest` | `MessageDigestSpi` | Quy tắc update, tạo digest, reset |
| `Signature` | `SignatureSpi` | Khởi tạo key, update, sign/verify |
| `Cipher` | `CipherSpi` | Khởi tạo khóa/chế độ/padding, update/doFinal |
| `Mac` | `MacSpi` | Khởi tạo secret key, update, doFinal |
| `KeyStore` | `KeyStoreSpi` | Nạp/đọc/lưu và truy vấn key/certificate |
| `SecureRandom` | `SecureRandomSpi` | Cung cấp bytes ngẫu nhiên theo hợp đồng engine |
| `KeyAgreement` | `KeyAgreementSpi` | Khởi tạo khóa và pha thỏa thuận |
| `KeyFactory` | `KeyFactorySpi` | Chuyển đổi/đọc key specification |

Đăng ký `Provider.Service(type, algorithm, className, ...)` phải tương thích với **engine type** và **SPI implementation class**. Ví dụ gán một subclass `MessageDigestSpi` dưới type `Cipher` không biến nó thành `CipherSpi`; JCA sẽ không thể dùng đúng hợp đồng.

Giới hạn thực tế khi mở rộng:

1. **Ứng dụng dùng engine, provider dùng SPI.** Client code nên gọi `Signature.getInstance(...)`, không gọi thẳng một `SignatureSpi` do bạn tự tạo.
2. **Một service không có nghĩa tạo được engine hoàn toàn mới.** Java đã định nghĩa các engine chuẩn; thêm type tên tùy ý vào Provider không sinh ra `MyEngine.getInstance` của JDK.
3. **Không mạo nhận tên thuật toán chuẩn.** Nếu thuật toán chỉ là phép tổng byte, không công bố là `SHA-256` hoặc `AES`. Tên, output, kích thước khóa, format, exceptions và semantics phải tuân chuẩn khi công bố tên chuẩn.
4. **Không xem alias như cơ chế chuyển đổi giao thức.** Alias là tên tra cứu, không bổ sung mode/padding hoặc đổi dạng key một cách bí mật.
5. **Lớp triển khai cần truy cập được.** Đặc biệt với JPMS module, việc đóng gói package, module descriptor, ServiceLoader và reflection cần xem xét; className đúng chưa đủ nếu module/constructor không đáp ứng.
6. **Khi cần backend vendor, ưu tiên provider/vendor API sẵn có.** Tự cài `CipherSpi` mới để nối tới HSM thường gây rủi ro về khóa, trạng thái phiên, chuẩn thuật toán và kiểm toán.
7. **Không can thiệp nội bộ của JDK** bằng các lớp `sun.security.*`/`com.sun.*` không được cung cấp như public supported API chỉ để né giới hạn SPI. Điều này kém tương thích giữa các phiên bản Java.

Một provider còn phải chịu trách nhiệm về hiệu năng, thread safety, báo lỗi khi token mất kết nối, tài nguyên native, vòng đời key và khả năng hoạt động với nhiều bản JDK.

## 8. Đăng ký Provider khác với cấu hình Provider

Có ba cách chọn provider thường gặp:

~~~java
MessageDigest a = MessageDigest.getInstance("SHA-256");
MessageDigest b = MessageDigest.getInstance("SHA-256", "SUN");
MessageDigest c = MessageDigest.getInstance("SHA-256", providerObject);
~~~

Đây là **những dòng minh họa trong ngữ cảnh có sẵn `providerObject`**; tên `SUN` chỉ là ví dụ phụ thuộc JDK, không bảo đảm ở mọi runtime.

| Cách gọi | Điều kiện | Ảnh hưởng |
| --- | --- | --- |
| `getInstance(algorithm)` | Có provider phù hợp trong danh sách được chọn | JCA chọn theo thứ tự/chính sách provider hiện hành |
| `getInstance(algorithm, "TênProvider")` | Provider tên đó phải **được đăng ký** trong danh sách | Bắt buộc chọn provider theo tên |
| `getInstance(algorithm, providerObject)` | Đối tượng Provider có service phù hợp | **Không cần** đăng ký toàn cục |

Một số JDK có tùy chọn ưu tiên riêng cho từng thuật toán (`jdk.security.provider.preferred`), nên không phải lúc nào chỉ nhìn thứ tự `Security.getProviders()` cũng đủ kết luận thuật toán nào sẽ được chọn. Có thể kiểm tra provider thực nhận qua `engine.getProvider().getName()`; với một số engine, việc chọn provider có thể trì hoãn đến lúc khởi tạo bằng key.

Đăng ký động:

~~~java
int position = Security.addProvider(configuredProvider);
// position == -1: provider có cùng tên đã được đăng ký.
~~~

`Security.insertProviderAt` đặt vị trí ưu tiên cụ thể; `Security.removeProvider` gỡ provider theo tên. Các thao tác này ảnh hưởng **danh sách provider của tiến trình JVM**, có thể tác động tới nhiều thư viện, ứng dụng con và API `getInstance` không chỉ định provider. Không tự ý đưa custom provider lên đầu chỉ để “chạy được” một tính năng.

Đăng ký tĩnh thường qua `conf/security/java.security` của JDK, còn provider package có thể dùng Java `ServiceLoader` để được phát hiện/khởi tạo theo cách runtime hỗ trợ. **Discovery không tự động đồng nghĩa với đăng ký**; luôn kiểm tra danh sách thực tế bằng `Security.getProviders()`.

## 9. Ví dụ Java 17+ đầy đủ: cấu hình Provider, đăng ký Service và gọi SPI

Đây là **ví dụ sư phạm**, không dùng HSM và không cần PKCS#11. Thuật toán `LEARN-SUM32` chỉ **cộng giá trị unsigned của từng byte** rồi xuất 4 byte big-endian. **Nó không phải hash mật mã, không chống collision và tuyệt đối không được sử dụng để bảo vệ dữ liệu, password hay chữ ký.** Mục đích duy nhất là nhìn thấy đường đi JCA → Provider.Service → SPI.

Tạo file **ProviderSpiDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.Provider;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProviderSpiDemo {
    // CHECKSUM GIẢ LẬP để học về SPI; không có thuộc tính bảo mật.
    public static final class Sum32Spi extends MessageDigestSpi {
        private int sum;

        public Sum32Spi() {}

        @Override
        protected void engineUpdate(byte input) {
            sum += Byte.toUnsignedInt(input);
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {
            Objects.checkFromIndexSize(offset, len, input.length);
            for (int i = offset; i < offset + len; i++) {
                engineUpdate(input[i]);
            }
        }

        @Override
        protected byte[] engineDigest() {
            int value = sum;
            engineReset();
            return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
            };
        }

        @Override
        protected void engineReset() {
            sum = 0;
        }

        @Override
        protected int engineGetDigestLength() {
            return 4;
        }
    }

    public static final class DemoProvider extends Provider {
        private final boolean configured;

        public DemoProvider() {
            this(false);
        }

        private DemoProvider(boolean configured) {
            super(configured ? "LessonReady" : "LessonBase",
                    "1.0", "Educational provider only");
            this.configured = configured;
            if (configured) {
                putService(new Service(
                        this, "MessageDigest", "LEARN-SUM32",
                        Sum32Spi.class.getName(),
                        List.of("LEARN-SUM"),
                        Map.of("ImplementedIn", "Software")));
            }
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public Provider configure(String configArg) {
            if (!"enable".equals(configArg)) {
                throw new InvalidParameterException("Expected enable");
            }
            return new DemoProvider(true);
        }
    }

    public static void main(String[] args) throws Exception {
        Provider base = new DemoProvider();
        Provider ready = base.configure("enable");
        Provider.Service service =
                ready.getService("MessageDigest", "LEARN-SUM");

        System.out.println(base.isConfigured());
        System.out.println(ready.isConfigured());
        System.out.println(base == ready);
        System.out.println(service.getType() + ":" + service.getAlgorithm());

        MessageDigest digest = MessageDigest.getInstance("LEARN-SUM", ready);
        byte[] result = digest.digest(
                "abc".getBytes(StandardCharsets.UTF_8));

        System.out.println(digest.getProvider().getName());
        System.out.println(HexFormat.of().formatHex(result));
        System.out.println(ready.getService("MessageDigest", "UNKNOWN") == null);
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 --release 17 ProviderSpiDemo.java
java ProviderSpiDemo
~~~

**Stdout chính xác:**

~~~text
false
true
false
MessageDigest:LEARN-SUM32
LessonReady
00000126
true
~~~

Lý do kết quả như vậy:

1. `new DemoProvider()` chưa cấu hình nên `isConfigured() == false`.
2. `base.configure("enable")` tạo **instance mới** `ready` đã cấu hình; `base == ready` trả `false`.
3. Constructor của instance mới dùng `putService` để đăng ký `MessageDigest/LEARN-SUM32` với alias `LEARN-SUM`.
4. `getService("MessageDigest", "LEARN-SUM")` tìm được canonical algorithm `LEARN-SUM32`.
5. `MessageDigest.getInstance("LEARN-SUM", ready)` dùng **Provider object trực tiếp**; không cần `Security.addProvider`.
6. Phép cộng byte ASCII/UTF-8 của “abc”: 97 + 98 + 99 = 294 = 0x00000126, vì thế in `00000126`.
7. Tên algorithm không tồn tại `UNKNOWN` khiến `getService` trả `null`.

Ví dụ cố ý làm cấu hình bằng chuỗi `enable` và đổi tên provider từ `LessonBase` sang `LessonReady`. Đây là **quy ước riêng của DemoProvider**, không phải quy tắc bắt buộc của `Provider.configure`. Provider thực tế có thể trả chính nó và giữ nguyên tên.

Một chi tiết thiết kế: `Sum32Spi` là class `public static` với public no-args constructor, giúp cơ chế khởi tạo mặc định của `Provider.Service` tạo instance đúng. Các phương thức `engine*` là `protected` để triển khai hợp đồng SPI; code nghiệp vụ gọi qua `MessageDigest`. Không nên dùng `Service.newInstance(null)` rồi cast sang SPI để thay thế engine.

## 10. Provider.Service được đăng ký bằng cách nào?

Trong subclass `Provider`, lối hiện đại là dùng `putService`:

~~~java
putService(new Provider.Service(
        this,
        "MessageDigest",
        "ACME-DIGEST",
        "com.example.crypto.AcmeDigestSpi",
        java.util.List.of("ACME-HASH"),
        java.util.Map.of("ImplementedIn", "Software")));
~~~

**Đây là đoạn minh họa, không chạy độc lập** vì `com.example.crypto.AcmeDigestSpi` chưa được định nghĩa. Nó cho thấy API đăng ký cần đặt trong subclass có quyền truy cập `protected putService`.

Lối cũ dựa trên thuộc tính chuỗi:

~~~java
put("MessageDigest.ACME-DIGEST", "com.example.crypto.AcmeDigestSpi");
put("Alg.Alias.MessageDigest.ACME-HASH", "ACME-DIGEST");
~~~

JCA vẫn hỗ trợ các thuộc tính tương thích, nhưng `Provider.Service` nên được ưu tiên khi viết provider mới, nhất là khi cần factory tùy biến hoặc module system. Không nên đăng ký cùng một type/algorithm bằng cả hai cách rồi thay đổi liên tục; sự trùng lặp dễ khiến metadata, alias và instance behavior khó kiểm soát.

`Provider.Service` là metadata và có thể công bố `className`, nhưng **không cấp quyền chạy code** và **không khẳng định class hợp lệ** chỉ vì có chuỗi tên. Tại thời điểm engine chọn và khởi tạo, lớp phải truy cập được, đúng SPI và thỏa constructor contract. Việc đăng ký thành công cũng chưa bảo đảm thao tác mật mã với khóa cụ thể thành công.

## 11. Tình huống phần cứng: configure provider PKCS#11 đúng chỗ

Trong JDK có thể có provider `SunPKCS11` (tên/khả năng tùy bản phân phối JDK). Nó là cầu nối JCA tới thư viện PKCS#11 của nhà cung cấp token/HSM. Luồng ý tưởng:

~~~text
Cấu hình vendor (library, slot/token, cơ chế đăng nhập)
       |
       v
Provider SunPKCS11 chưa cấu hình
       | configure("/etc/security/token.cfg")
       v
Provider trả về, có cấu hình
       | (tùy kiến trúc) addProvider hoặc truyền object trực tiếp
       v
KeyStore / Signature / Cipher API
       |
       v
PKCS#11 native library -> token/HSM -> key và thao tác cryptographic
~~~

Đoạn mã **minh họa tích hợp, KHÔNG chạy độc lập** vì cần file cấu hình, PKCS#11 library và token thật:

~~~java
Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("JDK không cung cấp SunPKCS11");
}

Provider tokenProvider = base.configure("/etc/security/token.cfg");
if (!tokenProvider.isConfigured()) {
    throw new IllegalStateException("PKCS#11 provider chưa cấu hình");
}

// Chọn provider qua object, không buộc đổi thứ tự toàn JVM:
KeyStore keyStore = KeyStore.getInstance("PKCS11", tokenProvider);
// Phần load/login và key access phụ thuộc thư viện/token cụ thể.
~~~

Các import cần có nếu đưa đoạn trên vào lớp thực tế: `java.security.Provider`, `java.security.Security` và `java.security.KeyStore`. Thực tế còn phải xác minh provider này hỗ trợ đúng service, algorithm, key type và mechanism của thiết bị.

**Ba tầng lỗi khác nhau:**

- `configure` thất bại: cấu hình library/slot/file sai, thư viện native không tải được, hoặc token không sẵn sàng.
- `getService`/`getInstance` thất bại: provider không quảng bá dịch vụ/algorithm theo tên được yêu cầu.
- `KeyStore.load`/`Signature.initSign`/`sign` thất bại: PIN/session/key/permission/thuật toán hoặc trạng thái HSM không phù hợp.

`Provider.configure` không xuất private key từ HSM, không tạo key trong token và không tự đăng nhập cho mọi ứng dụng. Với HSM, private key thường được biểu diễn bằng handle/đối tượng provider-specific, còn phép ký được thực thi trong thiết bị hoặc qua thư viện native. Chính sách không xuất khóa, session và PIN thuộc thiết bị/vendor cùng cấu hình hệ thống.

Không nhúng PIN, password hay đường dẫn secret thật vào bài viết, log, repository. Khi cần callback đăng nhập hoặc rotation key, dùng cách do provider/tổ chức hỗ trợ.

## 12. ServiceLoader, Java module và phạm vi đăng ký

Để phát hiện một provider **được đóng gói** bằng Java ServiceLoader, có hai cách thông dụng:

**Classpath JAR:** resource `META-INF/services/java.security.Provider` có nội dung là tên đầy đủ của class Provider, ví dụ:

~~~text
com.example.crypto.ExampleProvider
~~~

**Named Java module:** khai báo service implementation trong `module-info.java` (đoạn minh họa):

~~~java
module com.example.provider {
    provides java.security.Provider
            with com.example.crypto.ExampleProvider;
}
~~~

Lớp `ExampleProvider` phải thực sự tồn tại và đáp ứng yêu cầu truy cập/constructor của cơ chế nạp. Provider implementation và các lớp SPI có thể cần những quyết định `exports`/`opens` hoặc factory tùy biến tùy cách engine khởi tạo, không suy từ một đoạn `provides` đơn lẻ.

Sơ đồ giới hạn:

~~~text
Đóng gói và discovery (module-info / META-INF/services)
               |
               v
Tạo/lấy một Provider instance
               |
               +-- configure(arg) nếu cần
               |
               +-- Security.addProvider(...) nếu cần global registry
               |
               +-- getInstance(..., providerObject) nếu muốn chỉ định trực tiếp
               |
               v
Provider.Service -> SPI class
~~~

Không đánh đồng:

- **ServiceLoader thấy một lớp Provider** với **Provider đã có trong `Security.getProviders()`**;
- **Provider đã cài** với **Provider đã cấu hình đúng token**;
- **Service đã đăng ký** với **service có thể dùng một khóa/mode cụ thể**.

Lựa chọn dạng triển khai phải dựa trên nhu cầu phân phối và phiên bản JDK mục tiêu. Đặc biệt với vendor provider, có thể có thêm yêu cầu về trust, JAR signing, native library, module path hoặc security policy tùy loại service và môi trường; không tự giả định một JAR thuần Java là đủ cho mọi engine.

## 13. Vì sao ranh giới SPI càng quan trọng với HSM?

Khi ứng dụng dùng `Signature.getInstance("SHA256withRSA", tokenProvider)`, có ít nhất bốn tầng hợp đồng:

1. **Engine API** quy định cách initSign/update/sign và exception.
2. **Provider.Service** công bố thuật toán, aliases, implementation và thông tin hỗ trợ.
3. **SignatureSpi** chuyển các thao tác thành lệnh phù hợp cho backend.
4. **PKCS#11/native/token** quyết định key handle, mechanism, phiên đăng nhập và tính sẵn có thực tế.

Một key tạo từ provider phần mềm có thể không dùng được với token provider; một private key phần cứng có thể không cung cấp encoded bytes và không chuyển sang provider khác tùy ý. Vì vậy việc **chọn provider** và **nguồn Key** cần tương thích. `supportsParameter` có thể giúp loại bỏ nhanh nhưng vẫn phải kiểm tra `initSign`/`sign`.

Không dùng khả năng `Provider.Service.newInstance` để bỏ qua phân quyền của token, tự ghi private key vào heap hay biến hóa một thuật toán thành thuật toán khác. Ranh giới SPI là nơi kết nối thiết bị theo **hợp đồng**, không phải nơi vô hiệu hóa kiểm soát bảo mật.

### Đặc tính một provider tốt cần bảo vệ

- Tên thuật toán, alias, provider name và version rõ ràng, ổn định, không xung đột tùy tiện.
- Implementation đúng chuẩn engine; exception được phân loại hợp lý: thuật toán không có, tham số không hợp lệ, key không được chấp nhận, token không sẵn sàng.
- Quản lý session, native resource, thread safety, logout/PIN và thời hạn/rotation key.
- Không rò bí mật qua `toString`, stacktrace, debug log hoặc fallback sang provider phần mềm ngoài ý muốn.
- Test trên JDK/vendor/OS/thiết bị và chế độ module/classpath thực tế, không chỉ test `getService != null`.

## 14. Thứ tự triển khai một custom provider

Khi thực sự có nhu cầu viết provider, tiến hành theo thứ tự:

1. **Chốt engine và algorithm** cần cung cấp. Kiểm tra có thể dùng provider hiện có trước khi tự triển khai.
2. **Viết SPI implementation** theo abstract methods/constructor và semantics chuẩn của engine. Có test đúng/sai input, reset/state, exception và chuẩn đầu ra.
3. **Viết subclass Provider** với name/version/info rõ ràng và `putService` cho từng algorithm; khai báo aliases/attributes đúng.
4. **Chỉ thêm `configure` và `isConfigured` nếu thật sự có cấu hình riêng.** Định nghĩa cú pháp, validation, thời điểm kết nối tới backend, ownership của resource và việc trả `this` hay instance mới.
5. **Đóng gói provider** cho classpath/module path; kiểm tra lớp SPI và provider có thể được framework nạp.
6. **Chọn cách sử dụng**: truyền `Provider` object trực tiếp để cô lập phạm vi hoặc đăng ký có chủ đích vào `Security`.
7. **Kiểm tra với engine thật** (`MessageDigest.getInstance`, `Signature.initSign`, `KeyStore.load`...), không chỉ nhìn metadata.
8. **Kiểm tra tích hợp**, nhất là lỗi token rút ra, key sai loại, alias sai, quyền truy cập, nhiều thread và shutdown.

Một ví dụ unit test cho mẫu `ProviderSpiDemo` ở mục 9 có thể đối chiếu: cấu hình rỗng không có service; `ready` có service và alias; `MessageDigest` chọn đúng provider; input “abc” ra `00000126`; gọi digest hai lần không giữ lại tổng cũ; invalid configArg bị từ chối.

## 15. Lỗi phổ biến và cách khoanh vùng

| Triệu chứng | Nguyên nhân đáng kiểm tra | Hành động |
| --- | --- | --- |
| `UnsupportedOperationException` khi gọi configure | Provider không hỗ trợ cấu hình qua API này | Kiểm tra docs vendor; không tự ép gọi |
| `InvalidParameterException` ở configure | configArg sai cú pháp/giá trị theo provider | Soát đường dẫn, profile, slot và schema cấu hình |
| `isConfigured() == false` | Provider cần cấu hình trước khi dùng | Giữ kết quả configure, kiểm tra trạng thái đúng instance |
| `getService(...) == null` | Sai type/algorithm/alias hoặc service không được công bố | In danh mục `getServices()` cho đúng provider |
| `NoSuchProviderException` | Gọi overload nhận tên nhưng provider chưa cài | Kiểm tra `Security.getProvider(name)`; cân nhắc overload nhận object |
| `NoSuchAlgorithmException` | Không có matching service hoặc không tạo được implementation | Kiểm tra type/algorithm, className, constructor, module, dependency |
| `ClassNotFoundException`/`IllegalAccess...` trong nguyên nhân lỗi | SPI class không tải/truy cập được | Kiểm tra packaging, public constructor, JPMS access |
| `InvalidKeyException`/`InvalidAlgorithmParameterException` | Khóa hoặc tham số không hợp với engine/provider | Kiểm tra key type/size/mechanism; đừng chỉ tin alias |
| `ProviderException` hoặc lỗi native vendor | Token bị ngắt, session hỏng, thư viện/thiết bị lỗi | Kiểm tra log backend, lifecycle token, quyền và retry policy |
| Test pass với provider object nhưng fail khi chọn theo tên | Provider chưa được đăng ký hoặc tên khác sau configure | Đọc `ready.getName()`, `Security.getProviders()` |

Không đổi global provider order, tắt kiểm tra khóa hay sửa code SPI vô căn cứ khi chưa xác định **lỗi ở tầng nào**. Debug bắt đầu bằng hỏi: “lỗi cấu hình provider?”, “thiếu service?”, “không tạo được SPI?”, hay “SPI đã chạy nhưng backend/key thất bại?”.

## 16. Trường hợp trước và sau: lỗi dùng sai kết quả configure

**Sai ngữ cảnh:**

~~~java
Provider base = Security.getProvider("SunPKCS11");
base.configure("/etc/security/token.cfg");
Security.addProvider(base);  // bỏ kết quả configure: có thể cài sai instance
~~~

**Đúng ngữ cảnh:**

~~~java
Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("Không tìm thấy SunPKCS11");
}
Provider ready = base.configure("/etc/security/token.cfg");
// Kiểm tra ready, chọn dùng trực tiếp hoặc đăng ký có chủ đích:
KeyStore tokenStore = KeyStore.getInstance("PKCS11", ready);
~~~

Hai đoạn chỉ minh họa cách sử dụng API, cần JDK, cấu hình và token phù hợp mới chạy được. Phương án thứ hai giữ đúng instance sau configure và truyền trực tiếp vào engine, giảm thay đổi provider selection của các thành phần khác trong JVM.

## 17. Checklist QA trước khi đưa provider vào ứng dụng

- [ ] `Provider` đúng identity, instance trả về từ configure được giữ và dùng nhất quán.
- [ ] `isConfigured`, danh mục `getServices` và `getService` phản ánh thực tế sau cấu hình.
- [ ] Mỗi `Service` có type/algorithm/className/alias đúng, không mạo nhận thuật toán.
- [ ] SPI implementation đúng subclass, constructor/module visibility và contract của engine.
- [ ] `MessageDigest/Signature/Cipher/KeyStore.getInstance` chọn đúng provider khi cần; không phụ thuộc tên/độ ưu tiên ngầm.
- [ ] Các khóa và tham số hợp với provider phần cứng; test thao tác thật, không chỉ metadata.
- [ ] Có test nhiều thread, reset, shutdown, token unavailable và key rotation trong môi trường cho phép.
- [ ] Không log PIN/secret/key; provider-order và cấu hình chỉ thay đổi trong phạm vi được chấp thuận.

## 18. Bài tập vận dụng

**Bài tập 1 — Phân tích cấu trúc.** Từ chương trình `ProviderSpiDemo.java`, chỉ ra object nào là provider chưa cấu hình, object nào đã cấu hình, dòng nào đăng ký service, dòng nào tra alias, dòng nào khởi tạo SPI thông qua JCA. Giải thích vì sao không có `Security.addProvider` mà ví dụ vẫn chạy.

**Bài tập 2 — Lỗi chọn tên.** Nếu sửa `MessageDigest.getInstance("LEARN-SUM", ready)` thành `MessageDigest.getInstance("LEARN-SUM", "LessonReady")` mà không đăng ký, dự đoán ngoại lệ. Đáp án: `NoSuchProviderException` trong môi trường không có provider đã đăng ký tên đó; overload nhận String cần provider nằm trong global registry.

**Bài tập 3 — Ranh giới API.** Một developer thêm service type `"MyCryptoEngine"` và trông đợi `Cipher.getInstance("MyCryptoEngine")` tự hoạt động. Hãy giải thích: `Cipher` tìm type `Cipher` và transformation tương ứng; một type tự đặt không tạo engine JDK mới.

**Bài tập 4 — Hardware.** Một provider PKCS#11 cấu hình thành công, `getService("Signature", "SHA256withRSA")` khác `null` nhưng ký bằng private key thất bại. Đề xuất trình tự điều tra: type của key, key thuộc token nào, mechanism/permission, login/session, PIN, trạng thái token, exception thực tế. Không kết luận “configure đã thành công thì phần cứng phải ký được”.

**Bài tập 5 — Factory.** Bạn muốn `Provider.Service.newInstance` gắn một SPI với hardware session. Nêu những điều không thể thay đổi: return type phải đúng SPI, constructorParameter phải đúng quy ước engine, không bỏ qua validation và không tạo API engine JDK mới chỉ bằng đặt service name.

## 19. Tài liệu chuẩn tham khảo

- `java.security.Provider` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Provider.html
- `java.security.Provider.Service` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Provider.Service.html
- `java.security.Security` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Security.html
- `java.security.MessageDigest` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html
- Oracle “How to Implement a Provider in the Java Cryptography Architecture”: https://docs.oracle.com/en/java/javase/22/security/howtoimplaprovider.html
- Java Security Properties File (JDK 21): https://docs.oracle.com/en/java/javase/21/security/security-properties-file.html

**Điểm cần ghi nhớ khi chuyển sang triển khai thật:** `configure` xử lý trạng thái provider, `Provider.Service` công bố dịch vụ và tạo SPI, engine chịu trách nhiệm API sử dụng, còn token/backend chịu trách nhiệm key và phép toán thực tế. Luôn kiểm chứng cả bốn tầng trên đúng JDK và môi trường của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-003">20.3 SunPKCS11 làm cầu nối tới thư viện PKCS#11 của thiết bị</a>

<details>
<summary>Xem chi tiết</summary>

# 20.3 SunPKCS11 làm cầu nối tới thư viện PKCS#11 của thiết bị

## 1. Vấn đề và mục tiêu

Một ứng dụng Java cần ký tài liệu bằng khóa riêng nằm trong HSM (Hardware Security Module), smart card hoặc một token mật mã. Ứng dụng đã dùng giao diện chuẩn `java.security.Signature`, nhưng khóa thuộc thiết bị bên ngoài và thao tác ký phải đi qua thư viện do nhà sản xuất cung cấp. **SunPKCS11** là provider trong JDK đóng vai trò cầu nối: chuyển các thao tác mật mã từ Java Cryptography Architecture / Java Cryptography Extension (JCA/JCE) sang giao diện **PKCS#11** của thư viện native.

Sau bài này, cần giải thích được:

- PKCS#11, thư viện nhà sản xuất, thiết bị/token và SunPKCS11 là **bốn thành phần khác nhau**.
- Cấu hình `name`, `library` và `slot`/`slotListIndex` để tạo một provider Java gắn với token phù hợp.
- Cách dùng `Provider.configure(...)`, `KeyStore.getInstance("PKCS11", provider)` và `Signature.getInstance(..., provider)` để ký bằng khóa của token.
- Vì sao một `PrivateKey` Java có thể chỉ là **handle** tham chiếu đến khóa mà không mang byte khóa riêng.
- Cách phân biệt lỗi cấu hình native library, thiết bị/slot, xác thực PIN, alias và cơ chế mật mã.

## 2. Kiến trúc: SunPKCS11 là bộ chuyển tiếp JCA/JCE → PKCS#11

```text
                    Ứng dụng Java
         KeyStore  |  Signature  |  Cipher
                      |
                    JCA/JCE
                      |
           Provider SunPKCS11 (JDK)
           module jdk.crypto.cryptoki
                      |
               Native PKCS#11 API
           C_Initialize, C_OpenSession,
             C_Login, C_Sign*, ...
                      |
         Thư viện PKCS#11 của vendor
      Linux .so / Windows .dll / macOS .dylib
                      |
           Driver / dịch vụ / kết nối
                      |
         HSM, smart card, token mật mã
                hoặc soft token
```

**Giải thích từng lớp:**

1. **JCA/JCE** cung cấp các API Java như `KeyStore`, `Signature`, `Cipher` và `KeyPairGenerator`. Ứng dụng gọi API này và chọn provider thực hiện.
2. **SunPKCS11** là provider của JDK, nằm trong module `jdk.crypto.cryptoki`. Nó ánh xạ thao tác JCA/JCE sang PKCS#11. Nó **không tự chứa toàn bộ thuật toán mật mã của thiết bị** hay tự tạo driver phần cứng.
3. **PKCS#11 / Cryptoki** là chuẩn API ở phía native cho token mật mã: quản lý slot/token/session, đăng nhập, đối tượng khóa/chứng thư và các cơ chế ký, giải mã, sinh khóa.
4. **Thư viện native** do vendor (hoặc phần mềm soft token) cung cấp thực thi giao diện PKCS#11, đôi khi nói chuyện với HSM qua mạng hoặc một dịch vụ trung gian.
5. **Token/thiết bị** quản lý quyền dùng khóa và thực hiện các thao tác mà nó hỗ trợ.

Dùng SunPKCS11 **không đồng nghĩa chắc chắn có phần cứng**: một thư viện PKCS#11 có thể là soft token. Ngược lại, JDK cung cấp SunPKCS11 **không có nghĩa** đã có sẵn thư viện PKCS#11 của thiết bị. Phải cài đặt đúng thư viện/driver của vendor và cấu hình đúng endpoint hoặc slot.

### Những khái niệm PKCS#11 cần phân biệt

| Thuật ngữ | Ý nghĩa thực tế |
| --- | --- |
| `Slot` | Một vị trí giao tiếp PKCS#11 được thư viện báo cáo; slot có thể có hoặc chưa có token |
| `Token` | Thiết bị hoặc kho đối tượng mật mã gắn với slot; có thông tin nhãn, serial, trạng thái |
| `Session` | Ngữ cảnh giao tiếp với token; cần thiết cho truy vấn đối tượng và hoạt động mật mã |
| `Login` | Trạng thái xác thực user PIN hoặc phương thức do token hỗ trợ; yêu cầu phụ thuộc thao tác |
| `Object` | Đối tượng PKCS#11 như private key, public key, certificate hoặc secret key |
| `CKA_ID`, `CKA_LABEL` | Thuộc tính nhận diện và ánh xạ đối tượng/alias; không phải chính byte khóa |
| `Mechanism` | Thuật toán/cơ chế như `CKM_SHA256_RSA_PKCS`, `CKM_RSA_PKCS`; token công bố khả năng hỗ trợ |

**Một lời gọi ký điển hình:** Java gọi `Signature.initSign(privateKey)` → `Signature.update(data)` → `Signature.sign()`; SunPKCS11 chuẩn bị session/cơ chế và gọi API PKCS#11 phù hợp của native library → token thực thi phần việc được thiết bị hỗ trợ và trả signature bytes. Những thao tác như băm dữ liệu có thể diễn ra một phần ở phía host, tùy mechanism và ánh xạ provider; không được mặc định mọi bước đều chạy trong HSM.

## 3. Cài đặt đúng ba thành phần trước khi dùng Java

Để thực hành với thiết bị thật cần có:

1. **JDK có module `jdk.crypto.cryptoki`**, chứa base provider tên `SunPKCS11`. Bản JDK thu gọn bằng `jlink` có thể đã bỏ module này.
2. **Native PKCS#11 library của thiết bị**, đúng nền tảng và kiến trúc với tiến trình JVM (ví dụ JVM 64-bit cần native library 64-bit tương thích), cùng các thư viện phụ thuộc, driver hoặc kết nối tới HSM.
3. **Token có thể truy cập**, slot phù hợp, cơ chế ký được hỗ trợ, user PIN/phương thức đăng nhập và khóa đã được provision phù hợp. Mẫu dưới đây dùng **khóa RSA đã có trên token**, kèm chứng thư chứa public key tương ứng.

Kiểm tra module và khả năng khám phá base provider bằng các lệnh JDK:

```bash
java --list-modules
jshell
```

Trong JShell:

```java
import java.security.Security;
System.out.println(Security.getProvider("SunPKCS11") != null);
```

Trên JDK đầy đủ có module cryptoki và base provider, biểu thức trên in `true`. `true` **chỉ chứng minh provider có sẵn trong runtime**, chưa chứng minh có native library, HSM, token, PIN đúng hoặc thuật toán hỗ trợ. `false` yêu cầu kiểm tra bản JDK và tập module trước khi kết nối thiết bị.

## 4. Cấu hình SunPKCS11: library và slot

Ví dụ file cấu hình `token.cfg` (đường dẫn thư viện chỉ minh họa; **phải thay bằng đường dẫn native library thực tế**):

```properties
name = LabHSM
library = /opt/vendor/lib/libpkcs11.so
slotListIndex = 0
```

Ý nghĩa:

- `name` là phần đuôi của tên provider; sau cấu hình provider có tên **`SunPKCS11-LabHSM`**.
- `library` là **đường dẫn tới thư viện native PKCS#11**. Trên Windows thường là `.dll`, Linux là `.so`, macOS là `.dylib`. Nó không phải đường dẫn tới file `.jar`, chứng thư hay file `.p12`.
- `slotListIndex = 0` chọn **phần tử đầu tiên** trong danh sách slot do hàm `C_GetSlotList` của thư viện trả về. Đây là **chỉ số trong danh sách**, không nhất thiết là ID slot bằng số 0.
- Có thể thay bằng `slot = <numeric-slot-id>` nếu đã biết ID slot thực. **Không chỉ định đồng thời** `slot` và `slotListIndex`. Nếu không khai báo cả hai, giá trị mặc định tương đương `slotListIndex = 0`.

Ví dụ một thiết bị xuất danh sách slot ID `[5, 12]`: `slotListIndex=0` chọn slot ID `5`; `slot=12` chọn slot ID `12`. Thứ tự danh sách có thể thay đổi khi môi trường/thư viện thay đổi, vì vậy không đoán từ vị trí USB hoặc số serial.

Một vài tùy chọn hữu ích khác:

| Tùy chọn | Dùng khi nào |
| --- | --- |
| `showInfo = true` | Kiểm tra thông tin provider, slot, token và các cơ chế công bố; dùng khi chẩn đoán |
| `enabledMechanisms = { CKM_RSA_PKCS }` | Giới hạn mechanism SunPKCS11 được phép sử dụng, khi thiết bị và nhu cầu đã được xác minh |
| `disabledMechanisms = { CKM_... }` | Tránh mechanism không phù hợp hoặc có lỗi tương thích; không dùng đồng thời với `enabledMechanisms` |
| `attributes(...) = { ... }` | Điều chỉnh thuộc tính PKCS#11 lúc tạo/nhập đối tượng nếu vendor cần; sai thuộc tính có thể làm thao tác thất bại |

Với lần kết nối đầu tiên, nên bắt đầu bằng cấu hình **tối thiểu** và chỉ thêm giới hạn mechanism/attributes khi đã có bằng chứng cần thiết. `showInfo` và debug có thể cung cấp thông tin hạ tầng, nên quản lý log theo môi trường.

## 5. Cấu hình provider ở Java 17+

Cách chuẩn cho Java hiện đại là lấy base provider `SunPKCS11` rồi gọi `Provider.configure(configPath)`:

```java
Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("JDK lacks SunPKCS11");
}
Provider tokenProvider = base.configure("token.cfg");
System.out.println(tokenProvider.getName()); // SunPKCS11-LabHSM
```

*Đoạn này là trích đoạn minh họa bên trong một chương trình đã import `java.security.*`; chương trình hoàn chỉnh nằm ở phần 7.*

**Quan trọng:** phải dùng **provider được `configure` trả về**. Không được bỏ kết quả gọi `configure` rồi dùng lại object `base` chưa cấu hình.

Có hai cách lựa chọn provider:

- **Chỉ định ngay tại API:** `KeyStore.getInstance("PKCS11", tokenProvider)` hoặc `Signature.getInstance("SHA256withRSA", tokenProvider)`. Cách này minh bạch về thiết bị được gọi; ví dụ đầy đủ dưới đây dùng cách này.
- **Đăng ký vào danh sách provider JVM:** `Security.addProvider(tokenProvider)`, sau đó ứng dụng có thể tra cứu bằng tên hoặc để JCA chọn theo thứ tự provider. Với hệ thống nhiều thiết bị, đừng dựa hoàn toàn vào thứ tự ngầm định.

Cũng có thể cấu hình tĩnh trong file `$JAVA_HOME/conf/security/java.security` theo quy tắc `security.provider.N=SunPKCS11 <config-path>` của phiên bản JDK; thay đổi danh sách provider toàn cục cần được kiểm soát và thường đòi hỏi khởi động lại JVM. Với ứng dụng riêng, cấu hình động giúp kiểm soát rõ ràng instance và slot.

Không nên dùng constructor nội bộ `new sun.security.pkcs11.SunPKCS11(...)` trong mã ứng dụng hiện đại: đây là lớp triển khai nội bộ; dùng API công khai `Provider.configure`.

## 6. Đọc khóa từ token qua `KeyStore("PKCS11")`

Java không mở một file keystore thông thường trong ví dụ này. `KeyStore` là **view từ Java lên các đối tượng token** mà SunPKCS11 ánh xạ được:

```java
KeyStore ks = KeyStore.getInstance("PKCS11", tokenProvider);
ks.load(null, userPin);
Key key = ks.getKey("signing-key", null);
```

- `getInstance("PKCS11", tokenProvider)` ràng buộc keystore với provider đã cấu hình cho slot/token.
- Tham số đầu tiên của `load(null, userPin)` là `InputStream`: `null` cho biết không đọc file keystore. Tham số thứ hai là **PIN** để provider đăng nhập khi cần.
- `ks.aliases()` cho biết những entry SunPKCS11 biểu diễn được, **không bảo đảm liệt kê mọi đối tượng thô** của thiết bị.
- `getKey(alias, null)` có thể trả `PrivateKey` đại diện cho khóa trong token. Với khóa private *non-extractable*, object Java chứa tham chiếu/handle, không mang byte private key. Không trông chờ `getEncoded()` trả private key.
- Đối với nhiều private key entry, SunPKCS11 cần tìm **certificate có `CKA_ID` trùng với private key** để tạo ánh xạ alias; alias thường dựa trên `CKA_LABEL` của certificate và có thể khác nhãn do vendor hiển thị. Không thấy alias **chưa đủ kết luận** khóa đã biến mất.

Một số cờ của đối tượng PKCS#11 cần hiểu:

- `CKA_TOKEN = true`: đối tượng lưu trên token thay vì chỉ tồn tại trong session hiện tại.
- `CKA_PRIVATE = true`: việc truy cập có thể cần người dùng đã đăng nhập.
- `CKA_SENSITIVE` / `CKA_EXTRACTABLE`: ràng buộc xuất hoặc đọc vật liệu khóa tùy loại khóa/cơ chế/token.
- `CKA_SIGN = true`: cho phép dùng khóa trong thao tác ký; có khóa RSA mà thuộc tính không cho ký vẫn không dùng được.

Những cờ đó được token/vendor enforcement; chỉ thấy tên `PKCS11` trên provider **không chứng minh khóa luôn non-extractable**. Phải kiểm chứng chính sách tạo khóa và thuộc tính thực tế của token.

### PKCS#11 và PKCS#12 không phải một

| `PKCS11` | `PKCS12` |
| --- | --- |
| Chuẩn API cho token và HSM, thường truy cập qua native library | Định dạng file lưu khóa/chứng thư, thường mang đuôi `.p12`/`.pfx` |
| `KeyStore.load(null, pin)` cho token | Thường `KeyStore.load(inputStream, password)` để đọc file |
| Private key có thể là handle tới thiết bị | Private key có thể được giải mã vào bộ nhớ tiến trình sau khi mở file |
| Bảo vệ và quyền dùng khóa do token/thiết bị áp dụng | Bảo vệ nội dung file dựa vào mật khẩu/mã hóa và quyền lưu trữ |

## 7. Ví dụ hoàn chỉnh: ký RSA qua SunPKCS11 và xác minh chữ ký

**Điều kiện trước:** có JDK 17+, file `token.cfg` hợp lệ, token đã online, user PIN, **private key RSA cho phép ký** đã được provision và certificate đi kèm có public key tương ứng. Biết alias xuất hiện trong PKCS#11 KeyStore. Ví dụ không tự khởi tạo token hoặc tạo khóa trên thiết bị vì việc đó phụ thuộc vendor và quyền quản trị.

Tạo file **`Pkcs11SignDemo.java`**:

```java
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;

public final class Pkcs11SignDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java Pkcs11SignDemo <config-file> <key-alias>");
        }

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException(
                    "SunPKCS11 unavailable: check jdk.crypto.cryptoki module");
        }

        // configure() tra ve provider gan voi thu vien + slot duoc chon.
        Provider token = base.configure(args[0]);

        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Run from a terminal to enter the PIN securely");
        }

        char[] pin = console.readPassword("User PIN: ");
        if (pin == null) {
            throw new IllegalStateException("No PIN supplied");
        }

        try {
            // null = khong co file keystore; PIN dung de login token.
            KeyStore ks = KeyStore.getInstance("PKCS11", token);
            ks.load(null, pin);

            Key candidate = ks.getKey(args[1], null);
            if (!(candidate instanceof PrivateKey privateKey)) {
                throw new IllegalArgumentException(
                        "No private key for alias: " + args[1]);
            }

            Certificate certificate = ks.getCertificate(args[1]);
            if (certificate == null) {
                throw new IllegalArgumentException(
                        "No certificate for alias: " + args[1]);
            }

            byte[] data = "SunPKCS11 bridge"
                    .getBytes(StandardCharsets.UTF_8);

            // Buoc ky: provider duoc chi dinh ro, su dung khoa token.
            Signature signer = Signature.getInstance("SHA256withRSA", token);
            signer.initSign(privateKey);
            signer.update(data);
            byte[] signed = signer.sign();

            // Buoc xac minh: public key tu certificate; khong can PIN.
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(certificate);
            verifier.update(data);

            if (!verifier.verify(signed)) {
                throw new SecurityException("Signature mismatch");
            }

            System.out.println("Provider: " + token.getName());
            System.out.println("Verification: OK");
        } finally {
            Arrays.fill(pin, '\0');
        }
    }
}
```

Biên dịch và chạy từ terminal có token/thư viện thật:

```bash
javac Pkcs11SignDemo.java
java Pkcs11SignDemo token.cfg "signing-key"
```

Thay `"signing-key"` bằng alias **thực tế** đã được liệt kê từ token. Chương trình hỏi `User PIN:` mà không in PIN ra màn hình. **Nếu** file cấu hình có `name=LabHSM`, khóa và chứng thư RSA hợp lệ, token hỗ trợ ký và bước xác minh thành công, hai dòng `stdout` do chương trình in là:

```text
Provider: SunPKCS11-LabHSM
Verification: OK
```

Khi chưa có HSM/soft token và vendor library đúng, bài thực hành **không thể hoàn tất thao tác ký**; những lỗi tải thư viện hoặc không thấy token là kết quả thiếu điều kiện thực hành, không phải bằng chứng mã Java ký sai.

**Vì sao ví dụ này có giá trị?**

1. `base.configure(...)` xây provider dành riêng cho thiết bị/slot.
2. `KeyStore("PKCS11")` tìm handle của private key từ token sau khi đăng nhập.
3. `Signature.getInstance("SHA256withRSA", token)` **ép bước ký dùng SunPKCS11 đã cấu hình**. Nếu thuật toán/mechanism không được hỗ trợ, chương trình thất bại rõ ràng.
4. `Signature.getInstance("SHA256withRSA")` ở bước xác minh dùng public key trong certificate, có thể thực hiện bằng provider phần mềm trên máy. Không cần xuất private key.
5. `verifier.verify(signed) == true` chỉ chứng minh chữ ký khớp **public key và dữ liệu đã dùng**; nó **không** tự xác minh certificate chain, danh tính người sở hữu certificate, mục đích chứng thư hoặc chính sách tin cậy.

**Cẩn trọng về PIN:** `char[]` được xóa trong `finally` để giảm tồn lưu trong bộ nhớ ứng dụng, nhưng không bảo đảm xóa mọi bản sao có thể nằm trong JVM/native library. Đừng hard-code PIN, đưa PIN vào command line, log, exception hoặc git. Nếu thiết bị dùng PIN pad/protected authentication path, có thể cần `CallbackHandler`/`AuthProvider` và quy trình riêng của vendor thay vì `Console.readPassword`.

## 8. Chọn thuật toán: JCA name ↔ PKCS#11 mechanism

Một tên thuật toán Java không phải một lời hứa thiết bị sẽ hỗ trợ:

```text
Signature.getInstance("SHA256withRSA", tokenProvider)
                    |
             SunPKCS11 dang ky SPI
                    |
      Mechanism token co the su dung:
        CKM_SHA256_RSA_PKCS
      hoac CKM_RSA_PKCS / CKM_RSA_X_509
      (anh xa va dieu kien khac nhau)
                    |
                C_Sign*
```

SunPKCS11 đăng ký dịch vụ theo **giao điểm** giữa những gì nó hỗ trợ và những mechanism native token công bố, có xét `enabledMechanisms`/`disabledMechanisms`. Dùng `Signature.getInstance(algorithm, tokenProvider)` có lợi vì nếu token không cung cấp đúng dịch vụ, `NoSuchAlgorithmException` xuất hiện thay vì âm thầm đổi sang một provider khác.

Nếu dùng `Signature.getInstance("SHA256withRSA")` **không chỉ định provider**, cơ chế lựa chọn của JCA có thể chọn một provider khác dựa trên thứ tự và loại key. Điều đó dễ làm sai yêu cầu “phải ký bằng đúng HSM”. Sau khi tạo engine, kiểm tra `signer.getProvider().getName()` và theo dõi log kỹ thuật để xác nhận provider vận hành.

Không tự giả định:

- Có `CKM_RSA_PKCS` thì thiết bị đã hỗ trợ mọi cách đệm RSA hoặc mọi biến thể RSA-PSS.
- Có key RSA thì `CKA_SIGN` nhất thiết được bật.
- Dùng SunPKCS11 khiến mọi phép tính băm, sinh số ngẫu nhiên hoặc xác minh đều chạy bên trong phần cứng.
- Khóa từ một token có thể chuyển sang instance SunPKCS11 của slot khác. Handle/định danh khóa bị ràng buộc vào token và session liên quan.

Chọn thuật toán ký theo tiêu chuẩn sản phẩm, key type, cơ chế và chính sách token. Không hạ xuống cơ chế yếu chỉ để tránh lỗi tương thích.

## 9. Công cụ kiểm tra token và các trường hợp vận hành

Có thể kiểm tra những entry mà SunPKCS11 công bố qua `keytool` (JDK hiện đại hỗ trợ `-addprovider`):

```bash
keytool -list -storetype PKCS11 -keystore NONE -addprovider SunPKCS11 -providerarg token.cfg -providerName SunPKCS11-LabHSM
```

- `-keystore NONE` nghĩa là không có file keystore trên đĩa; dữ liệu đến từ token.
- `-providerarg token.cfg` cấu hình provider với native library/slot tương ứng.
- `-providerName` giúp xác định đúng instance provider khi có nhiều provider.
- `keytool` thường hỏi PIN tương tác; với thiết bị có protected authentication path, tham khảo tùy chọn `-protected` theo hướng dẫn vendor. Tránh `-storepass <PIN>` trên command line vì lộ qua process list/history.
- Không dùng thao tác sinh/xóa/ghi đè khóa thật trên token chỉ để thử kết nối.

Trường hợp smart card được tháo và cắm lại, `KeyStore.Builder` với `CallbackHandlerProtection` có thể quản lý prompt khi cần lấy lại token. Với ứng dụng cần đăng nhập ngoài thao tác keystore, `AuthProvider` cung cấp API `login`/`logout`; phải sử dụng đúng instance provider đã cấu hình. Cần thiết kế lại khi token bị tháo, session mất hiệu lực, PIN sai hoặc HSM unavailable; không giả định reference `PrivateKey` cũ luôn sử dụng được sau sự kiện đó.

### Chẩn đoán có chủ đích

```bash
java -Djava.security.debug=sunpkcs11,pkcs11keystore Pkcs11SignDemo token.cfg "signing-key"
```

Hoặc tạm đặt `showInfo = true` trong file cấu hình. Debug giúp quan sát tải thư viện, slot/token và ánh xạ keystore. Chỉ bật trong môi trường chẩn đoán được kiểm soát: log có thể chứa metadata thiết bị/alias hoặc thông tin triển khai.

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Không có base `SunPKCS11` | Runtime `jlink` thiếu `jdk.crypto.cryptoki` hoặc cấu hình provider bị thay đổi | Kiểm tra modules và danh sách provider |
| Lỗi tải `library` / `UnsatisfiedLinkError` / `ProviderException` lúc cấu hình | Sai đường dẫn, kiến trúc 32/64-bit, DLL/SO phụ thuộc chưa tìm thấy, quyền filesystem | Kiểm tra vendor SDK, đường dẫn, phụ thuộc native và kiến trúc JVM |
| `CKR_TOKEN_NOT_PRESENT`, slot không đúng | Slot trống, sai `slot` hoặc `slotListIndex`, card bị tháo | Kiểm tra token/slot do vendor liệt kê, tránh đoán slot |
| `CKR_PIN_INCORRECT` / `CKR_USER_NOT_LOGGED_IN` | Sai PIN hoặc chưa xác thực đúng user/token | Dừng thử PIN liên tục để tránh khóa thẻ; dùng quy trình xác thực phù hợp |
| Không tìm thấy `alias` hoặc `getKey(...)` trả `null` | Khác `CKA_LABEL`, thiếu certificate có `CKA_ID` khớp, token chưa login, key không hiện qua KeyStore | Kiểm tra token object và ánh xạ certificate–private key |
| `NoSuchAlgorithmException` hoặc `CKR_MECHANISM_INVALID` | Token không hỗ trợ cơ chế được yêu cầu hay cơ chế bị vô hiệu hóa trong config | Đối chiếu `showInfo`, mechanism thực tế và chính sách thuật toán |
| `CKR_KEY_FUNCTION_NOT_PERMITTED` | Thuộc tính key không cho thao tác ký | Kiểm tra `CKA_SIGN`, quyền khóa và chính sách vendor |
| `CKR_DEVICE_ERROR` / session invalid / timeout | Token, phiên hoặc dịch vụ HSM không sẵn sàng | Xem log vendor, tình trạng mạng/HSM, khởi tạo lại session có kiểm soát |

Không cố giải quyết mọi lỗi bằng cách đổi `slotListIndex` hay cho provider tự fallback về software: đó có thể khiến ứng dụng gọi nhầm token hoặc phá vỡ yêu cầu giữ khóa trong HSM.

## 10. Nguyên tắc bảo mật và triển khai

**Bảo vệ khóa.** Tạo và quản lý khóa theo quy trình của HSM/token; nếu yêu cầu khóa không xuất được, xác minh `CKA_EXTRACTABLE = false` và các thuộc tính liên quan theo chính sách. `PrivateKey` được trả về có thể là handle; API Java không bảo đảm bằng tên lớp rằng khóa chắc chắn được tạo trong phần cứng.

**Bảo vệ PIN và phiên.** Không hard-code hay đặt PIN vào argument/log; ưu tiên cơ chế nhập bí mật phù hợp. Hạn chế quyền tài khoản chạy ứng dụng. Token có số lần thử PIN giới hạn và số session hữu hạn; thiết kế connection/session lifecycle, cơ chế retry có giới hạn và xử lý card removal.

**Kiểm soát thư viện native.** SunPKCS11 nạp mã native có quyền trong tiến trình JVM. Xác minh nguồn gốc và phiên bản SDK vendor, cập nhật bảo mật, quyền file, thư viện phụ thuộc và cấu hình kết nối. Thư viện sai hoặc độc hại không được Java sandbox tự ngăn lại.

**Ràng buộc đúng thiết bị.** Một ứng dụng dùng nhiều HSM/slot phải gắn alias, provider và cấu hình với môi trường cụ thể. Theo dõi token identity/serial qua API hoặc công cụ vendor khi yêu cầu kiểm soát tính đúng đắn. Không đánh đồng hai provider chỉ vì đều có tiền tố `SunPKCS11-`.

**Cơ chế vs chính sách.** SunPKCS11 thực hiện chuyển tiếp kỹ thuật. Nó không tự bảo đảm chứng thư đáng tin, chữ ký được người dùng có thẩm quyền chấp thuận, thuật toán đạt chính sách, không xuất khóa, có audit hay tính sẵn sàng cao. Những điều đó cần được thiết kế và xác minh độc lập.

**Tính sẵn sàng.** Kiểm tra khả năng khởi động khi token vắng mặt, session timeout, chuyển thiết bị, giới hạn throughput, HSM cluster và cách rollback phiên bản SDK. Với các tác vụ bảo mật bắt buộc hardware-backed, phải **fail closed** nếu không truy cập được token; không tự chuyển sang khóa/phép ký bằng software.

## 11. Tình huống suy luận và bài tự kiểm tra

**Tình huống 1:** `Security.getProvider("SunPKCS11") != null` và chương trình báo `CKR_TOKEN_NOT_PRESENT`. Điều gì xảy ra?

> JDK có base provider nhưng chưa sử dụng được token. Cần kiểm tra native library, slot được chọn và tình trạng thiết bị; sự tồn tại của provider không chứng minh đã kết nối HSM.

**Tình huống 2:** `KeyStore("PKCS11")` liệt kê certificate nhưng không có private key alias mà bạn mong đợi. Có nên kết luận khóa không tồn tại?

> Chưa. SunPKCS11 ánh xạ đối tượng token thành `KeyStore` theo quy tắc, trong đó private key thường phải được ghép với certificate theo `CKA_ID`. Cần kiểm tra đối tượng bằng công cụ quản trị token và quy tắc alias.

**Tình huống 3:** `Signature.getInstance("SHA256withRSA")` ký thành công trên máy có cài SunPKCS11. Đã chứng minh ký bằng HSM chưa?

> Chưa. Phải xác nhận provider thực tế và key được lấy từ token tương ứng. Để tránh lựa chọn mơ hồ, chỉ định `Signature.getInstance("SHA256withRSA", configuredProvider)`.

**Tình huống 4:** Java trả về object `PrivateKey`. Có nghĩa key bytes đã rời khỏi thiết bị?

> Không. Với khóa non-extractable, object có thể chỉ là handle. Tuy nhiên yêu cầu khóa không xuất được phải được kiểm tra bằng chính sách và thuộc tính token, không suy ra chỉ từ interface Java.

**Tình huống 5:** Một thư viện PKCS#11 hoạt động trên soft token. Có thể nói SunPKCS11 đảm bảo ký “bằng phần cứng”?

> Không. SunPKCS11 nối tới **implementation PKCS#11**; implementation đó có thể là phần mềm. Phải kiểm tra đích thực tế là HSM/token được chấp nhận.

## Tài liệu tham khảo chính thức

- Oracle, [PKCS#11 Reference Guide — JDK 24](https://docs.oracle.com/en/java/javase/24/security/pkcs11-reference-guide1.html): cấu trúc bridge, `Provider.configure`, cấu hình `library`/`slotListIndex`, login, ánh xạ khóa và mechanism.
- Oracle, [Java Cryptography Architecture Reference Guide — JDK 24](https://docs.oracle.com/en/java/javase/24/security/java-cryptography-architecture-jca-reference-guide.html): mô hình `Provider` và lựa chọn thuật toán JCA.
- Oracle, [`keytool` — JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html): `-addprovider`, `-providerarg`, `-storetype PKCS11` và `-keystore NONE`.

**Điểm cốt lõi:** **SunPKCS11 là cầu nối Java ↔ thư viện PKCS#11**, còn chính thư viện vendor và token quyết định khóa nào tồn tại, cơ chế nào dùng được và thao tác nào được phép. Viết Java bằng JCA/JCE vẫn cần cấu hình provider, xác thực token và kiểm soát chính sách của thiết bị đúng cách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-004">20.4 Cấu hình slot, token, đăng nhập và truy cập PKCS11 KeyStore</a>

<details>
<summary>Xem chi tiết</summary>

# 20.4. Cấu hình slot, token, đăng nhập và truy cập PKCS11 KeyStore

Khi một ứng dụng Java cần dùng khóa trong HSM (Hardware Security Module), smart card hoặc token mật mã, ta thường không đọc khóa riêng từ file `.p12`/`.jks`. Thay vào đó, ứng dụng đi qua **PKCS#11** và nhà cung cấp mật mã (**security provider**) để thao tác với đối tượng khóa mà token quản lý. Bài này hướng dẫn cách chọn đúng **slot**, xác định **token**, cấu hình **SunPKCS11 provider**, đăng nhập bằng **user PIN**, mở **`KeyStore` loại `PKCS11`**, tìm alias và dùng khóa cho phép toán mật mã.

Bài tập trung vào Java 17+ với provider `SunPKCS11` có trong các bản JDK hỗ trợ module `jdk.crypto.cryptoki`. Việc thực thi với token thật còn yêu cầu thư viện native PKCS#11 của nhà sản xuất và thiết bị/partition đã được cấu hình. Các đường dẫn thư viện và slot ở đây được ghi rõ là **ví dụ cấu hình**; phải xác nhận giá trị thực bằng công cụ của token trước khi chạy.

## 1. Phân biệt thư viện, slot, token, session và KeyStore

~~~text
Ứng dụng Java (KeyStore, Signature, Cipher, ...)
        │  JCA/JCE
        ▼
Provider SunPKCS11 đã configure cho một slot
        │  native bridge
        ▼
Thư viện PKCS#11 của nhà sản xuất (.so / .dll / .dylib)
        │
        ├── Slot 0 ── Token A (smart card/partition)
        │                 ├── session đăng nhập
        │                 ├── certificate
        │                 ├── public key
        │                 └── private key (có thể không cho xuất)
        └── Slot 1 ── Token B / chưa cắm token
~~~

Sơ đồ chỉ mô tả các lớp khái niệm, không khẳng định số ID hay thứ tự slot thực tế.

| Khái niệm | Bản chất | Dùng để làm gì? |
| --- | --- | --- |
| **PKCS#11 module/library** | Thư viện native do hãng thiết bị hoặc phần mềm cung cấp | Hiện thực API như `C_GetSlotList`, `C_OpenSession`, `C_Login`, `C_FindObjects`, ký và giải mã |
| **Slot** | Vị trí logic mà module xuất ra; có `CK_SLOT_ID` riêng | Chỉ đúng nơi có token mong muốn |
| **Token** | Thực thể lưu/quản lý đối tượng mật mã trong slot, có thể là card, USB, HSM partition hay soft token | Sở hữu PIN/chính sách và các khóa/chứng thư |
| **Session** | Phiên kết nối PKCS#11 đến token, có thể chưa xác thực hoặc đã đăng nhập | Truy cập và thao tác các object; session do provider quản lý trong luồng dùng JCA |
| **Object** | Khóa công khai, khóa riêng, khóa bí mật, certificate, v.v. với các thuộc tính `CKA_*` | Cung cấp vật liệu/cơ chế cho các phép toán được phép |
| **SunPKCS11 provider** | Cầu nối JCA/JCE ↔ native PKCS#11 | Đưa các dịch vụ như `KeyStore`, `Signature`, `Cipher` ra Java khi token hỗ trợ |
| **PKCS11 KeyStore** | Góc nhìn `java.security.KeyStore` lên đối tượng trong token | Liệt kê alias, đọc certificate và nhận tham chiếu đến khóa có quyền sử dụng |

Một thư viện native có thể có nhiều slot; một token được gắn với slot theo cách module báo cáo. **Slot không phải token, token không phải file KeyStore**, và alias Java không mặc nhiên trùng với token label hoặc `CKA_LABEL` của một object.

Với khóa riêng không cho trích xuất, `PrivateKey` Java thường chỉ là **tham chiếu/handle** đến object trên token. `privateKey.getEncoded()` có thể trả `null`; dùng `Signature.initSign(privateKey)` để đề nghị provider thực hiện phép ký trên token, không ép xuất khóa vào bộ nhớ ứng dụng. Tính không xuất được của khóa phụ thuộc cờ thuộc tính và chính sách token, không đến từ tên `PKCS11`.

## 2. Chọn slot đúng: `slot` khác `slotListIndex`

Hai tham số chính trong file cấu hình SunPKCS11:

- **`slot = 7`**: chọn slot có **ID thực** `CK_SLOT_ID = 7` trong danh sách do module cung cấp. Giá trị `7` ở đây chỉ là ví dụ; slot ID thực có thể lớn, không liên tục hoặc thay đổi khi driver/token thay đổi.
- **`slotListIndex = 0`**: chọn **phần tử đầu tiên trong danh sách tất cả slot** do `C_GetSlotList` trả về. Đây là *chỉ số vị trí từ 0*, không phải slot ID. Khi không khai báo cả hai, SunPKCS11 mặc định `slotListIndex = 0`.
- **Chỉ khai báo một trong hai**. Không đặt đồng thời `slot` và `slotListIndex`.

Ví dụ module trả danh sách ID slot `[4, 25, 108]`:

| Cấu hình | Slot được chọn |
| --- | --- |
| `slotListIndex = 0` | Slot ID `4` |
| `slotListIndex = 1` | Slot ID `25` |
| `slot = 25` | Slot ID `25` |
| `slot = 1` | Không thể suy ra là slot thứ hai; chỉ đúng nếu có slot mang ID `1` |

Danh sách này là ví dụ để giải thích phép ánh xạ, **không phải kết quả từ một thiết bị thật**.

Trước khi chọn cấu hình, hãy dùng công cụ liệt kê slot của nhà sản xuất. Nếu đã cài OpenSC và driver tương thích, có thể kiểm tra ở mức thông tin, chưa cần đăng nhập:

~~~bash
pkcs11-tool --module /opt/vendor/lib/libpkcs11.so --list-slots
~~~

`/opt/vendor/lib/libpkcs11.so` là đường dẫn minh họa; thay bằng thư viện PKCS#11 thực được cài trên máy. Đối chiếu ID slot, trạng thái token hiện diện, token label và serial theo tài liệu của hãng. Token label là thông tin nhận diện hữu ích, nhưng cấu hình `slot`/`slotListIndex` vẫn chọn theo slot, không tự động chọn theo label. Với thiết bị có thể rút/cắm hoặc slot list động, cần đánh giá khả năng thay đổi ánh xạ sau khi khởi động lại.

## 3. File cấu hình SunPKCS11: thư viện và một slot

Tạo file văn bản tên **`pkcs11.cfg`** cho một provider instance. Ví dụ trên Linux khi thư viện nhà sản xuất nằm tại đường dẫn đã kiểm tra:

~~~properties
name = TrainingToken
library = /opt/vendor/lib/libpkcs11.so
slotListIndex = 0
~~~

Ba dòng này có ý nghĩa:

1. `name` là hậu tố tên provider, nên provider sẽ có tên **`SunPKCS11-TrainingToken`**. Đây là tên của **Java provider**, không nhất thiết bằng token label.
2. `library` là **đường dẫn tuyệt đối** đến shared library native PKCS#11 của nhà sản xuất. Java không tự cung cấp driver của thiết bị.
3. `slotListIndex` chọn slot trong danh sách do module trả về. Sau khi xác minh đúng ID, có thể thay riêng dòng này bằng `slot = 7` nếu slot ID thực là `7`.

Với Windows, `library` trỏ đến `.dll`; với Linux thường là `.so`; với macOS có thể là `.dylib`. Java, OS, driver và thư viện native phải tương thích kiến trúc (ví dụ JVM 64-bit với DLL 64-bit) và thư viện phải tải được các native dependency cần thiết. Đường dẫn token thực tế có thể chứa khoảng trắng; tuân theo cú pháp config của phiên bản JDK và tài liệu hãng, đừng đoán đường dẫn.

**Các lưu ý quan trọng:**

- **Không đưa PIN, mật khẩu hoặc khóa riêng vào `pkcs11.cfg`.** File này mô tả provider/driver/slot, không phải kho bí mật để đăng nhập.
- Khi chọn nhiều slot/token, dùng các file cấu hình/provider instance riêng với `name` phân biệt; chọn đúng provider trong mã Java.
- `showInfo = true` có thể bật để chẩn đoán provider/slot/mechanism, nhưng chỉ dùng tạm ở môi trường kiểm soát vì log có thể chứa metadata thiết bị.
- `enabledMechanisms`/`disabledMechanisms` là chính sách thuật toán do provider cho phép; cấu hình sai có thể khiến `Signature` hoặc `KeyStore` không còn dịch vụ cần dùng. Chỉ giới hạn theo kiểm kê mechanism và yêu cầu bảo mật.
- Đừng tự thêm `attributes = compatibility` để chữa mọi lỗi: cấu hình thuộc tính khóa có thể ảnh hưởng đến khả năng trích xuất/sử dụng đối tượng. Chỉ điều chỉnh `CKA_*` theo chính sách HSM và tài liệu vendor.
- NSS/`nss*` là nhánh cấu hình chuyên biệt; không trộn tùy tiện tham số NSS với `library`/`slot` thông thường.

## 4. Nạp provider trong Java 17+

Với JDK hỗ trợ SunPKCS11, module `jdk.crypto.cryptoki` cung cấp implementation này. Cách cấu hình động được ưu tiên khi một ứng dụng cần kiểm soát cụ thể token:

~~~java
import java.security.Provider;
import java.security.Security;

// Đoạn trích dùng trong main hoặc hàm có tham số configPath.
Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("JDK does not provide SunPKCS11");
}
Provider pkcs11 = base.configure(configPath);
Security.addProvider(pkcs11);
System.out.println(pkcs11.getName());
~~~

Đây là **đoạn trích**, không phải chương trình độc lập: biến `configPath` phải trỏ tới file `pkcs11.cfg`, ví dụ đầy đủ nằm ở mục 7.

Phải sử dụng **đối tượng `Provider` do `configure(...)` trả về**, vì `configure` có thể tạo một provider mới. Đừng giữ provider gốc chưa cấu hình rồi gọi `KeyStore.getInstance("PKCS11", base)`. Nếu config có `name = TrainingToken`, tên sau cấu hình thông thường là `SunPKCS11-TrainingToken`.

Có thể đăng ký provider trong `$JAVA_HOME/conf/security/java.security` thay cho cấu hình động, nhưng cách đó thay đổi cấu hình bảo mật của cả bản cài JDK và thường cần restart ứng dụng. Đối với một chương trình học hoặc dịch vụ muốn chọn đúng HSM slot, cách cấu hình động thường rõ ràng hơn.

Khi cần một provider cụ thể, dùng overload có tham số provider:

~~~java
KeyStore tokenStore = KeyStore.getInstance("PKCS11", pkcs11);
~~~

Đoạn trên giả định các import/biến `KeyStore`, `pkcs11` đã được khai báo. Nếu chỉ gọi `KeyStore.getInstance("PKCS11")`, cơ chế tìm kiếm provider có thể chọn một SunPKCS11 instance khác khi hệ thống có nhiều token. Không dùng `KeyStore.getInstance("JKS")` hay `"PKCS12"` để mong mở trực tiếp slot PKCS#11.

## 5. Đăng nhập token: user PIN, callback và protected path

### 5.1. Login với `KeyStore.load(null, pin)`

Nhiều token yêu cầu **user PIN** cho thao tác với private key hoặc đối tượng cần xác thực. Cách cơ bản của SunPKCS11:

~~~java
KeyStore keyStore = KeyStore.getInstance("PKCS11", pkcs11);
keyStore.load(null, userPin);
~~~

Đây là đoạn trích: `userPin` phải là `char[]` nhận an toàn từ người dùng hoặc cơ chế quản lý bí mật, còn `pkcs11` phải được cấu hình như trên.

- Tham số `InputStream` đầu tiên là `null` vì KeyStore gắn với token, **không có file `.jks`/`.p12` để đọc**.
- Tham số thứ hai là PIN để provider thực hiện đăng nhập thích hợp vào token. Việc token có yêu cầu login hay không phụ thuộc thao tác và cấu hình thiết bị.
- `user PIN` cho người sử dụng khóa khác với `SO PIN` (*Security Officer*, dùng cho quản trị/khởi tạo token theo cơ chế hãng). Không dùng SO PIN cho thao tác nghiệp vụ thông thường.
- PIN sai nhiều lần có thể bị token khóa theo chính sách phần cứng. Không retry tự động vô hạn và không in PIN ra log.
- PIN chỉ nên nằm trong `char[]` trong thời gian cần dùng, sau đó `Arrays.fill(pin, '\0')`. Điều này giảm thời gian tồn tại trong heap, nhưng **không bảo đảm xóa mọi bản sao trong native memory hoặc driver**.

### 5.2. Login động với `KeyStore.Builder`

Khi token có thể tháo/cắm, yêu cầu nhập PIN qua UI hoặc cần callback, Java cung cấp:

- `KeyStore.CallbackHandlerProtection` dùng một `javax.security.auth.callback.CallbackHandler`.
- `KeyStore.Builder.newInstance("PKCS11", pkcs11, protection)` tạo builder gắn với đúng provider.
- `builder.getKeyStore()` truy cập KeyStore, handler xử lý `PasswordCallback` để nhận PIN khi cần.
- Khi hoạt động với smart card, builder có thể giúp xử lý lại việc nhập PIN sau khi thay token; ứng dụng vẫn phải xác minh đang làm việc với **đúng** token/khóa được ủy quyền.

Callback handler phải thực sự hỗ trợ `PasswordCallback`; một handler bỏ qua callback sẽ không có cách cung cấp PIN. Đây là lựa chọn nâng cao cho luồng xác thực, không bắt buộc để hiểu ví dụ `load(null, pin)` trong bài này.

### 5.3. `AuthProvider.login` và session

SunPKCS11 cũng hỗ trợ kiểu `AuthProvider` với `login(subject, callbackHandler)`/`logout()`. Cách này hữu ích khi thao tác cần đăng nhập **trước hoặc ngoài** việc load KeyStore. Đừng coi việc login là “đăng nhập Java user”: đây là trạng thái xác thực với PKCS#11 token/session do provider điều phối.

Tránh gọi `logout()` bừa bãi trong ứng dụng đa luồng dùng chung token: logout của một component có thể làm thao tác khác mất phiên xác thực; với tùy chọn cấu hình đặc biệt `destroyTokenAfterLogout`, provider instance thậm chí không thể tái sử dụng sau logout. Hãy thiết kế vòng đời provider/session theo tài liệu thiết bị.

### 5.4. Token có PIN pad/biometric (*protected authentication path*)

Một số token cho người dùng nhập PIN trên bàn phím phần cứng hoặc thiết bị xác thực riêng. Khi token **thực sự hỗ trợ protected authentication path**, có thể dùng đường đăng nhập cho token tự lấy thông tin xác thực thay vì đọc PIN trong Java, chẳng hạn `keyStore.load(null, null)` với luồng và provider tương thích.

Đừng áp dụng `load(null, null)` tùy tiện cho token chỉ hỗ trợ nhập PIN trong ứng dụng: có thể xảy ra lỗi đăng nhập. Trong công cụ `keytool`, tùy chọn `-protected` dành cho protected path và không đi kèm tùy chọn truyền mật khẩu. **`protected=true` trong JAAS `KeyStoreLoginModule` là tùy chọn của JAAS**, không phải dòng mặc định cần thêm vào file `pkcs11.cfg` ở mục 3.

## 6. `PKCS11 KeyStore` biểu diễn các khóa như thế nào?

Đối với ứng dụng Java, API truy cập quen thuộc:

~~~java
KeyStore keyStore = KeyStore.getInstance("PKCS11", pkcs11);
keyStore.load(null, userPin);

for (java.util.Enumeration<String> aliases = keyStore.aliases();
     aliases.hasMoreElements();) {
    String alias = aliases.nextElement();
    System.out.println(alias + " isKeyEntry=" + keyStore.isKeyEntry(alias));
}
~~~

Đoạn trích giả định `pkcs11` và `userPin` có sẵn. `aliases()` cho các mục mà **implementation KeyStore dựng được từ đối tượng token**; không nhất thiết liệt kê tất cả object mà API PKCS#11 mức thấp nhìn thấy.

- `keyStore.getCertificate(alias)` lấy certificate nếu alias có chứng thư được ánh xạ.
- `keyStore.isKeyEntry(alias)` kiểm tra alias được trình bày như một entry khóa; `isCertificateEntry(alias)` kiểm tra entry chỉ chứa chứng thư.
- `keyStore.getKey(alias, null)` nhận `Key` từ entry sau khi đã đăng nhập. Với private key không được trích xuất, kết quả thường là object `PrivateKey` đại diện token key.
- `keyStore.getCertificateChain(alias)` có thể trả về chuỗi chứng thư nếu token/provider cung cấp; không giả định chuỗi đầy đủ hay đã đáng tin.
- Tên alias có thể được xác định bởi mapping certificate/private key và thuộc tính `CKA_ID`/`CKA_LABEL` theo implementation. **Token label**, **alias KeyStore** và **object label** là ba khái niệm khác nhau.

Nếu `aliases()` rỗng, có thể token chưa có entry phù hợp, thiếu certificate ghép với private key, quyền đăng nhập/visibility khác, hoặc module không ánh xạ đối tượng đó thành KeyStore entry. Không kết luận ngay HSM hoàn toàn không có khóa. Dùng công cụ quản trị của hãng để kiểm tra object type, `CKA_ID`, label, chính sách và chứng thư, với quyền chỉ đọc phù hợp.

**Giới hạn quan trọng:** `PKCS11 KeyStore` là một abstraction phù hợp cho key/certificate theo JCA, không thay thế mọi thao tác quản lý HSM như quản trị partition, PIN reset, điều khiển session, policy hoặc sao lưu token. Thao tác tạo/xóa/ghi khóa qua KeyStore có thể thay đổi token thật và phụ thuộc vendor; bài này chỉ đọc và thực hiện ký thử có kiểm soát.

## 7. Chương trình Java 17+ hoàn chỉnh: mở token, liệt kê alias và tùy chọn ký thử

Điều kiện trước khi chạy:

1. JDK 17+ có module `jdk.crypto.cryptoki` và được phép nạp provider `SunPKCS11`.
2. Thư viện PKCS#11 native đã cài, tương thích với JVM và được điền **đúng đường dẫn** vào `pkcs11.cfg`.
3. Slot được chọn thực sự có token, driver đã sẵn sàng và người sử dụng có **user PIN** hợp lệ.
4. Chỉ khi yêu cầu ký thử, dùng alias của **khóa RSA cho phép ký** đã có trên token, với certificate tương ứng. Chương trình **không tạo hay xóa khóa**.

Lưu file **`Pkcs11KeyStoreDemo.java`**:

~~~java
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Enumeration;

public final class Pkcs11KeyStoreDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 2) {
            System.err.println(
                "Usage: java Pkcs11KeyStoreDemo <config-file> [rsa-key-alias]");
            return;
        }

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException(
                "SunPKCS11 provider unavailable in this JDK/runtime");
        }

        // Use the configured provider returned by configure(...).
        Provider tokenProvider = base.configure(args[0]);
        Security.addProvider(tokenProvider);

        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                "Run from an interactive terminal to enter the user PIN");
        }
        char[] pin = console.readPassword("User PIN for token: ");
        if (pin == null) {
            throw new IllegalStateException("PIN entry cancelled");
        }

        KeyStore tokenStore = KeyStore.getInstance("PKCS11", tokenProvider);
        try {
            // null stream: the KeyStore resides on the selected token.
            tokenStore.load(null, pin);
        } finally {
            Arrays.fill(pin, '\0');
        }

        System.out.println("Provider: " + tokenProvider.getName());
        int count = 0;
        Enumeration<String> names = tokenStore.aliases();
        while (names.hasMoreElements()) {
            String alias = names.nextElement();
            Certificate cert = tokenStore.getCertificate(alias);
            System.out.println(
                "Alias=" + alias
                    + ", keyEntry=" + tokenStore.isKeyEntry(alias)
                    + ", certificate=" + (cert != null));
            count++;
        }
        System.out.println("Visible aliases: " + count);

        if (args.length == 2) {
            String alias = args[1];
            Key key = tokenStore.getKey(alias, null);
            if (!(key instanceof PrivateKey privateKey)
                    || !"RSA".equalsIgnoreCase(key.getAlgorithm())) {
                throw new IllegalArgumentException(
                    "Alias is not an accessible RSA private key: " + alias);
            }

            Certificate certificate = tokenStore.getCertificate(alias);
            if (certificate == null) {
                throw new IllegalStateException(
                    "No matching certificate for alias: " + alias);
            }

            byte[] message =
                "Java PKCS11 signing demo".getBytes(StandardCharsets.UTF_8);
            Signature signer =
                Signature.getInstance("SHA256withRSA", tokenProvider);
            signer.initSign(privateKey);
            signer.update(message);
            byte[] signature = signer.sign();

            // Verify using the certificate public key.
            // This checks signature mathematics, not certificate trust.
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(certificate);
            verifier.update(message);
            System.out.println(
                "Signature verifies: " + verifier.verify(signature));
        }
    }
}
~~~

Biên dịch và chạy **trong terminal tương tác**:

~~~bash
javac Pkcs11KeyStoreDemo.java
java --add-modules jdk.crypto.cryptoki Pkcs11KeyStoreDemo pkcs11.cfg
~~~

Khi đã xem được alias của khóa RSA phù hợp và được phép dùng thử, truyền alias làm tham số thứ hai:

~~~bash
java --add-modules jdk.crypto.cryptoki Pkcs11KeyStoreDemo pkcs11.cfg signing-key
~~~

`signing-key` trong lệnh trên **chỉ là tên alias minh họa**: dùng chính alias được in ra từ token. Tránh đưa PIN vào đối số dòng lệnh, biến môi trường hoặc commit source; chương trình đọc PIN không hiển thị qua `Console.readPassword`. `System.console()` có thể bằng `null` khi chạy trong IDE hoặc môi trường không có TTY.

**Kết quả không có một stdout cố định cho mọi máy.** Nếu cấu hình mẫu `name = TrainingToken` hợp lệ, chương trình sẽ in `Provider: SunPKCS11-TrainingToken` và các dòng `Alias=...` dựa trên nội dung token thật; `Visible aliases` phụ thuộc cấu hình và dữ liệu. Chế độ ký thử chỉ in `Signature verifies: true` khi khóa RSA, chứng thư và mechanism có thể dùng thành công để ký/kiểm tra; các lỗi token/library/permission/algorithm sẽ phát sinh ngoại lệ. Không giả lập danh sách alias hay chữ ký thành công khi chưa có HSM.

**Phân tích từng bước:**

- `Security.getProvider("SunPKCS11")` lấy provider gốc; `configure(args[0])` tạo instance cho slot đã chọn. Việc lưu provider trả về tránh sai provider.
- `KeyStore.getInstance("PKCS11", tokenProvider)` **ràng buộc đúng provider**, không để Java tự chọn token khác.
- `load(null, pin)` thực hiện truy cập keystore token và đăng nhập theo yêu cầu. Không có file keystore Java; `null` đầu tiên là cố ý.
- `aliases()` chỉ đọc danh sách entry nhìn thấy được. Chương trình không tạo, ghi, xóa certificate/khóa.
- `getKey(alias, null)` trả về token-backed private key khi alias và quyền phù hợp. Không gọi `getEncoded()` vì khóa có thể được đánh dấu không xuất được.
- `Signature.getInstance("SHA256withRSA", tokenProvider)` yêu cầu đúng provider làm thao tác ký; thuật toán và thuộc tính khóa phải được token hỗ trợ. `Signature` xác minh sử dụng public key từ certificate để kiểm toán phép ký, **chưa xác minh tin cậy chứng thư**.
- PIN được điền vào `char[]` và xóa ngay sau `load`. Provider/native code có thể duy trì session hợp lệ; việc xóa mảng Java không đồng nghĩa logout token.

Nếu thiết bị dùng protected authentication path, thay luồng nhập PIN và `load(null, pin)` bằng quy trình phù hợp thiết bị (ví dụ `load(null, null)` trên token hỗ trợ). Không ép chạy ví dụ nguyên trạng với PIN pad mà không xác nhận tài liệu vendor.

## 8. Kiểm tra read-only bằng `keytool`

Với JDK 17 có hỗ trợ tùy chọn thêm provider động, lệnh tham khảo sau liệt kê entry trong token mà không tạo/xóa đối tượng:

~~~bash
keytool -list -keystore NONE -storetype PKCS11 -addprovider SunPKCS11 -providerarg pkcs11.cfg
~~~

`-keystore NONE` nghĩa là không có file keystore cần mở; `-storetype PKCS11` chọn token keystore; `-providerarg` cung cấp file config cho SunPKCS11. Công cụ sẽ yêu cầu xác thực khi cần; **không dùng `-storepass <PIN>` trên lệnh thực** vì giá trị có thể bị ghi vào lịch sử shell/process listing.

Nếu đã đăng ký provider tĩnh trong `java.security`, có thể dùng `-providerName SunPKCS11-TrainingToken` để chọn đúng instance cùng `-keystore NONE -storetype PKCS11 -list`. Thiết bị dùng protected path cần tùy chọn `-protected` theo hướng dẫn của `keytool`; không truyền PIN cùng lúc.

`keytool` liệt kê entry của KeyStore Java, còn công cụ PKCS#11 của hãng có thể hiển thị nhiều object loại khác không được ánh xạ vào KeyStore. Hai danh sách khác nhau không nhất thiết là dữ liệu bị mất.

## 9. Tình huống lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân cần xác minh | Hướng xử lý |
| --- | --- | --- |
| `Security.getProvider("SunPKCS11") == null` | Bản JDK/runtime image không chứa hoặc không cung cấp provider/module `jdk.crypto.cryptoki` | Kiểm tra bản JDK và module đã cài, chạy với `--add-modules jdk.crypto.cryptoki` khi thích hợp |
| Không nạp được `library` | Sai đường dẫn, thư viện thiếu, sai 32/64-bit, thiếu native dependency | Xác minh đường dẫn tuyệt đối, kiến trúc JVM/OS/driver, dependency loader |
| `CKR_SLOT_ID_INVALID` hoặc không thấy token | Chọn nhầm `slot`/`slotListIndex`, token chưa cắm hoặc slot list thay đổi | Liệt kê slot bằng công cụ vendor, so ID thật với vị trí chỉ số; kiểm tra token present |
| `CKR_TOKEN_NOT_PRESENT` | Token bị tháo, slot rỗng, HSM partition chưa khả dụng | Kiểm tra cắm thiết bị/kết nối, cập nhật chọn slot và lifecycle khi token thay |
| `CKR_PIN_INCORRECT` hoặc login thất bại | Nhầm user PIN với SO PIN, sai PIN, policy khóa tài khoản, session không hợp lệ | Dừng thử liên tục, kiểm tra chính sách PIN và tình trạng khóa với quản trị viên thiết bị |
| `KeyStore` không thấy alias | Object chưa được ánh xạ, thiếu certificate tương ứng, permission/visibility khác, chọn sai token | So sánh `CKA_ID`/`CKA_LABEL`/cert bằng công cụ vendor; xác nhận đúng provider |
| `NoSuchAlgorithmException` với `SHA256withRSA` | Provider/token không cung cấp thuật toán hay PKCS#11 mechanism phù hợp, bị disable | Xem danh sách mechanism, thuộc tính khóa và chính sách provider; không tự hạ xuống thuật toán yếu |
| `InvalidKeyException` / lỗi `CKR_KEY_FUNCTION_NOT_PERMITTED` | Khóa không đúng loại, không cho ký hoặc thuộc tính `CKA_SIGN` không phù hợp | Dùng key/cert được cấp đúng mục đích, kiểm tra quyền và mechanism của token |
| Ký được nhưng kiểm tra certificate không đáng tin | Khóa ký đúng toán học nhưng certificate không được ủy quyền/tin cậy | Xác minh chuỗi chứng thư, trust anchor, key usage, chính sách và trạng thái thu hồi |
| Chạy từ IDE báo không có console | `System.console()` bằng `null` | Chạy ví dụ trong terminal hoặc triển khai callback PIN riêng cho GUI/server |
| Sau khi tháo/cắm lại, thao tác thất bại | Session/handle cũ không còn hiệu lực hoặc token mới xuất hiện | Khởi tạo lại provider/KeyStore theo lifecycle được hỗ trợ; tránh tái sử dụng object khóa cũ |

SunPKCS11 có công cụ chẩn đoán:

~~~text
showInfo = true
~~~

Dòng trên là **tùy chọn thêm vào file cấu hình** khi cần xem thông tin provider/slot/mechanism. Để xem log chi tiết trong **môi trường test kiểm soát**, có thể chạy Java với một trong các tham số JVM:

~~~bash
java -Djava.security.debug=sunpkcs11 --add-modules jdk.crypto.cryptoki Pkcs11KeyStoreDemo pkcs11.cfg
~~~

Hoặc chọn `-Djava.security.debug=pkcs11keystore` cho thông tin KeyStore. Debug có thể chứa thông tin thiết bị, alias và cấu hình: không bật đại trà ở production, không chụp/đăng log có dữ liệu nhạy cảm. Các thông báo `CKR_*` là lỗi PKCS#11 native có thể được bọc trong Java exception; xem **cause chain** nhưng không in thông tin xác thực.

## 10. Những nguyên tắc để vận hành an toàn

### Phân quyền và vật liệu khóa

- **Định danh đúng token trước khi dùng khóa:** phối hợp cấu hình slot, serial/token label, certificate fingerprint hoặc định danh quản trị theo chính sách; tránh coi slot index đầu tiên là thiết bị đáng tin vĩnh viễn.
- **Tách user PIN và SO PIN:** tài khoản nghiệp vụ không cần quyền quản trị/khởi tạo token. Hạn chế số lần đăng nhập lỗi, quản lý PIN bằng quy trình bí mật được phê duyệt.
- **Không trích xuất private key:** sử dụng đối tượng `PrivateKey` do provider cung cấp. Kiểm tra thuộc tính `CKA_SENSITIVE`, `CKA_EXTRACTABLE`, `CKA_SIGN` ở phía quản trị token; không suy đoán mọi khóa token đều bất khả xuất.
- **Bảo vệ certificate/trust:** lấy certificate từ token để kiểm chữ ký không đồng nghĩa certificate đã đáng tin; xác minh hệ thống chứng thực và quy tắc nghiệp vụ trước khi chấp nhận danh tính người ký.
- **Không dùng thuật toán yếu để “chạy cho được”:** chọn cơ chế băm/chữ ký phù hợp chính sách và danh sách supported mechanisms của thiết bị.

### Lifecycle và ứng dụng nhiều luồng

- Quản lý vòng đời provider, session và token theo driver; việc cắm/rút smart card có thể làm object `Key`/KeyStore cũ mất hiệu lực.
- Đừng tự ép một thư viện chỉ hỗ trợ đơn luồng phục vụ đồng thời nhiều PKCS#11 consumer trong một process. Kiểm tra cam kết thread safety, giới hạn session và cơ chế pool của hãng.
- Một provider instance được cấu hình cho một slot; nếu nhiều slot/tenant cần key riêng, tách instance, alias policy và quyền truy cập. Không để một lời gọi `KeyStore.getInstance("PKCS11")` tình cờ chọn khóa khác.
- Khi lỗi PIN/token/hardware, log mã lỗi và loại thao tác đủ để điều tra; không log PIN, dữ liệu nhạy cảm, nội dung khóa, hoặc full stack trace ở nơi công khai. Dừng retry nếu có nguy cơ khóa PIN.
- Chỉ đọc và ký thử khi có quyền. `KeyStore.setEntry`, `deleteEntry`, key generation hay token initialization có thể **thay đổi/xóa đối tượng thực**; không đưa vào bước kiểm tra kết nối thông thường.

## 11. Tự kiểm tra kiến thức

1. **`slot = 2` có nghĩa slot thứ ba không?** Không. Nó trỏ tới slot **ID 2**; slot thứ ba trong danh sách là `slotListIndex = 2`.
2. **`KeyStore.load(null, pin)` có mở tệp `.jks` hay `.p12` không?** Không. `null` stream kết hợp `PKCS11`/provider báo rằng kho dữ liệu là token, còn PIN phục vụ login.
3. **Token label có phải luôn là alias?** Không. Token label nhận diện token, còn alias là ánh xạ entry Java; `CKA_LABEL` của từng object là lớp tên khác.
4. **Vì sao phải lấy provider trả về từ `configure`?** Vì provider chưa cấu hình chưa gắn với library/slot đã chọn; `configure` có thể trả về instance khác.
5. **Có thể gọi `getEncoded()` để sao chép khóa riêng HSM không?** Không nên giả định. Khóa bị hạn chế xuất thường chỉ có handle Java và `getEncoded() == null`; phép ký chạy qua provider.
6. **`Signature.verify(...) == true` chứng minh điều gì?** Chứng minh phép kiểm chữ ký đúng theo public key và dữ liệu được đưa vào; **không tự chứng minh** certificate/đối tác thuộc danh sách tin cậy.
7. **Vì sao không retry PIN sai liên tục?** Vì token có thể đếm lần sai và khóa tài khoản/đòi quy trình mở khóa do quản trị thực hiện.
8. **Khi nào cần `KeyStore.Builder`?** Khi cần callback PIN và lifecycle token động; truy cập token tĩnh đơn giản có thể dùng `load(null, pin)`.

## 12. Checklist thao tác cho một token mới

1. Xác nhận JDK và module `jdk.crypto.cryptoki`, đường dẫn native library và kiến trúc JVM phù hợp.
2. Liệt kê slot theo driver, phân biệt **ID slot** với **index trong danh sách**; kiểm tra token hiện diện, label và serial.
3. Tạo `pkcs11.cfg` với `name`, `library` và **duy nhất một** tham số slot.
4. Configure provider, giữ instance trả về; chỉ định chính instance ấy trong `KeyStore.getInstance("PKCS11", provider)`.
5. Đăng nhập bằng user PIN an toàn hoặc protected path thực sự được hỗ trợ; không dùng SO PIN thay thế.
6. Liệt kê alias/certificate; xác nhận khóa đúng, quyền sử dụng, mechanism và trust policy.
7. Thực hiện ký/kiểm thử có kiểm soát; quản lý lỗi thiết bị, session, logout và bảo vệ log.

**Kết nối ý tưởng cốt lõi:** `library` xác định **PKCS#11 implementation** → `slot`/`slotListIndex` xác định **nơi có token** → `SunPKCS11` nối token với **JCA/JCE** → `KeyStore.load(null, PIN)` cung cấp **luồng đăng nhập** → `KeyStore` và `Signature` sử dụng **khóa do token quản lý** theo quyền và cơ chế hỗ trợ. Phải kiểm soát riêng **đúng token, quyền PIN và độ tin cậy certificate**; việc một phép ký chạy thành công chưa giải quyết cả ba yếu tố.

Tài liệu tham khảo: [Oracle Java SE 17 — PKCS#11 Reference Guide](https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-005">20.5 Sử dụng private key không xuất được qua Cipher hoặc Signature</a>

<details>
<summary>Xem chi tiết</summary>

# 20.5 Sử dụng private key không xuất được qua `Cipher` hoặc `Signature`

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích một `PrivateKey` “không xuất được” (non-exportable/unextractable) nghĩa là gì và vì sao mã Java **vẫn có thể sử dụng khóa** để ký số hoặc giải mã.
2. Xác định vai trò riêng của `KeyStore`, `Provider`, `Signature`, `Cipher` và phần cứng HSM/smartcard trong một lệnh mật mã.
3. Dùng JCA/JCE để gọi `Signature.initSign(privateKey)`; hiểu khi nào `Cipher.init(DECRYPT_MODE, privateKey)` có thể thực hiện trực tiếp trên token.
4. Phân biệt khả năng của **thuật toán**, **provider**, **token**, **key policy** và **quyền của phiên đăng nhập**; xử lý thất bại mà không xuất hay sao chép private key.
5. Tránh nhầm “ký bằng private key” với “mã hóa bằng private key” và tránh suy luận rằng `getEncoded() == null` là bằng chứng phần cứng tuyệt đối.

**Nguyên tắc trung tâm:** Ứng dụng Java có thể giữ một **đối tượng tham chiếu tới private key** mà không giữ **các byte private key**. Khi gọi đúng JCA/JCE provider, thao tác private-key được chuyển vào thiết bị hoặc dịch vụ quản lý khóa đang nắm khóa thực.

## 1. Phân biệt private key, đối tượng `PrivateKey` và quyền sử dụng

Ở môi trường phần mềm thông thường, một `PrivateKey` có thể chứa hoặc cho phép xuất dạng PKCS#8 bằng `getEncoded()`. Với HSM, smartcard hoặc một crypto provider quản lý khóa bên ngoài JVM, một đối tượng Java triển khai `PrivateKey` có thể chỉ mang thông tin nhận dạng khóa và liên kết đến **key handle/session**.

```text
Ứng dụng Java
  |
  | KeyStore.getKey(alias, ...)
  v
PrivateKey của provider (opaque handle; không chứa bytes private)
  |
  +-- Signature.initSign(privateKey) ---> Provider/SPI ---> token ký ---> chữ ký
  |
  +-- Cipher.init(DECRYPT_MODE, key) ---> Provider/SPI ---> token giải mã ---> dữ liệu rõ
                                         (chỉ nếu hỗ trợ và được phép)
```

Ví dụ, `getEncoded()` có thể trả về `null` vì đối tượng khóa không hỗ trợ encoding. **Không phải** vì `null` có một cơ chế mã hóa đặc biệt. Khóa có thể được quản lý bởi phần cứng; tính “không xuất được” thực sự phụ thuộc thuộc tính khóa và các biện pháp của nhà cung cấp, không phụ thuộc duy nhất vào một phương thức Java.

Cần phân biệt bốn câu:

| Khẳng định | Ý nghĩa |
|---|---|
| “`PrivateKey` là interface Java” | Một API đại diện cho khóa; không quy định vật liệu khóa phải nằm trong heap JVM |
| “`getEncoded() == null`” | Đối tượng Java không cung cấp bytes khóa qua API encoding này |
| “`CKA_EXTRACTABLE = false`” với PKCS#11 | Theo policy của token, khóa không được xuất bằng cơ chế wrap/extract thông thường của PKCS#11 |
| “Khóa chưa từng tồn tại ngoài HSM” | Yêu cầu mạnh hơn, cần kiểm tra quy trình sinh/import, thuộc tính lịch sử và bằng chứng/audit của thiết bị |

Các thuộc tính PKCS#11 thường gặp cho `CKO_PRIVATE_KEY`:

- `CKA_SENSITIVE = true`: cấm đọc một số thuộc tính bí mật của khóa theo đặc tả token.
- `CKA_EXTRACTABLE = false`: không cho xuất/wrap private key theo cơ chế được bảo vệ.
- `CKA_NEVER_EXTRACTABLE = true`: báo rằng khóa **chưa từng** có `CKA_EXTRACTABLE = true` trong lịch sử quản lý thuộc tính trên token.
- `CKA_SIGN = true`: khóa được phép thực hiện thao tác ký; không tự động cho phép giải mã.
- `CKA_DECRYPT = true`: khóa được phép giải mã; không tự động cho phép ký.
- `CKA_UNWRAP = true`: một quyền khác dành cho thao tác unwrap, không đồng nghĩa `CKA_DECRYPT` ở mọi API/token.

Các thuộc tính được đặt lúc tạo/provision khóa và chịu quy tắc của token. Không thể lấy một khóa có quyền `CKA_SIGN` rồi suy luận nó được phép `CKA_DECRYPT`. Một khóa được **import** vào thiết bị có thể vẫn tồn tại bản gốc bên ngoài; policy “không thể xuất từ nay về sau” không tự chứng minh provenance.

**Cảnh báo thực tế:** Tránh gọi `getEncoded()` để thử lấy bytes của khóa phần cứng trong đường xử lý sản xuất. Nếu provider trả dữ liệu, lời gọi ấy đã vượt khỏi chủ đích không xuất khóa. Hãy xác minh chính sách qua công cụ quản trị/audit chính thức của HSM hoặc nhà cung cấp.

## 2. `Signature` và `Cipher` sử dụng private key cho hai mục đích khác nhau

| Tác vụ | API thích hợp | Input là khóa nào? | Output |
|---|---|---|---|
| Ký thông điệp/chứng từ | `Signature.initSign(privateKey)`, `update`, `sign` | Private key có quyền ký | Bytes chữ ký; private key vẫn ở nơi lưu khóa |
| Xác minh chữ ký | `Signature.initVerify(publicKey)`, `update`, `verify` | Public key | `true/false` cho tính hợp lệ mật mã |
| Mã hóa dữ liệu nhỏ bằng RSA | `Cipher.init(ENCRYPT_MODE, publicKey)` | Public key | Ciphertext |
| Giải mã RSA được hỗ trợ | `Cipher.init(DECRYPT_MODE, privateKey)` | Private key có quyền giải mã | Plaintext đi ra khỏi thao tác |
| Giải mã dữ liệu AES | `Cipher.init(DECRYPT_MODE, secretKey)` | `SecretKey` (có thể cũng là handle HSM) | Plaintext |

**Không dùng `Cipher.ENCRYPT_MODE` với RSA private key để “tự tạo chữ ký”.** Một số provider có thể hỗ trợ phép toán RSA dạng thô/PKCS#1, nhưng nó không thay thế các lược đồ chữ ký có chuẩn định dạng và băm/chống giả mạo như `SHA256withRSA`, `RSASSA-PSS` hay `SHA256withECDSA`. Ký số phải đi qua `Signature`.

Không phải loại private key nào cũng giải mã được: khóa EC dùng ECDSA/Ed25519 chủ yếu dành cho **ký**, không dùng trực tiếp cho `Cipher` RSA. Nếu cần mã hóa khối lượng lớn, thường dùng **mã hóa lai (hybrid encryption)** với AES-GCM cho dữ liệu; phần asymmetric/KEM chỉ bọc/trao đổi khóa phiên. Quyền unwrap của một HSM và việc khóa AES có được giữ trong HSM hay không phải được thiết kế riêng.

Khi private key không xuất được, **đầu ra của phép toán vẫn xuất hiện**: chữ ký được gửi cho bên kiểm tra; plaintext giải mã có thể trở về RAM ứng dụng. HSM bảo vệ **vật liệu private key**, không tự bảo vệ tất cả dữ liệu mà ứng dụng tạo ra hoặc đọc được.

## 3. Đường đi qua JCA/JCE và nhà cung cấp phần cứng

Trong Java, `KeyStore` là API truy cập khóa và chứng chỉ, `Provider` là thành phần đăng ký hiện thực các dịch vụ, còn `Signature`/`Cipher` là engine thực hiện thao tác.

```text
                           Các đối tượng API trong JVM
  Java app -----------------------------------------------------------
    |                     |                          |
    | KeyStore            | Signature                | Cipher
    | getKey(alias)       | getInstance("...")       | getInstance("...")
    v                     v                          v
  SunPKCS11 hoặc provider JCA/JCE tương thích thiết bị
    |                     |                          |
    | key lookup          | SignInit/Sign             | DecryptInit/Decrypt
    v                     v                          v
  Native PKCS#11 library / HSM vendor SDK (thiết bị hoặc dịch vụ từ xa)
    |
    +-- đối tượng khóa (key handle, policy, session, PIN/role)
    +-- xử lý mật mã theo cơ chế thiết bị cho phép
```

`SunPKCS11` đi kèm các bản JDK tương thích và là **cầu nối** sang thư viện PKCS#11 gốc của nhà cung cấp. `SunPKCS11` không tự mang khóa trong Java hay tự làm mọi phép mật mã; token và thư viện native quyết định cơ chế nào thực sự khả dụng.

Không phải mọi HSM đều có PKCS#11: một số có JCE provider riêng hoặc API mạng/SDK đặc thù. Mô hình tư duy vẫn tương tự, nhưng cách lấy `PrivateKey`, đăng nhập và chọn engine phải theo tích hợp chính thức của nhà cung cấp.

**Điều kiện cần đồng thời để một lệnh thành công:**

1. Provider được cấu hình đúng và có thể kết nối token/slot phù hợp.
2. `PrivateKey` tham chiếu khóa trên token, và ứng dụng đã xác thực quyền sử dụng khóa.
3. Provider cung cấp dịch vụ/thuật toán cụ thể; thiết bị hỗ trợ mechanism tương ứng.
4. Thuộc tính khóa cho phép đúng hành động (`CKA_SIGN`, `CKA_DECRYPT`…) và loại khóa khớp thuật toán.
5. Phiên token vẫn hoạt động, đáp ứng PIN, role, policy, hạn mức và yêu cầu xác thực theo từng lần dùng khóa nếu có.

Một lời gọi `Signature.getInstance("SHA256withRSA")` thành công **chưa đủ** để kết luận `initSign` hay `sign` sẽ thành công trên một private key phần cứng cụ thể.

## 4. Cách nạp private key từ PKCS#11 mà không xuất bytes

Giả sử HSM đã được nhà quản trị provision một cặp khóa RSA và chứng chỉ tương ứng, cung cấp thư viện PKCS#11. Một file cấu hình `pkcs11.cfg` có dạng minh họa:

```properties
name = SigningToken
library = /opt/vendor/pkcs11/libvendorpkcs11.so
slotListIndex = 0
```

Đường dẫn library, tên slot và cấu hình thực tế **phải thay bằng thông tin đúng từ nhà cung cấp/thiết bị**, đồng thời slot được chọn cần có khóa và chứng chỉ phù hợp. `slotListIndex = 0` là vị trí trong danh sách slot mà thư viện cung cấp, không nhất thiết là ID cố định của một token trong mọi môi trường.

Quy trình Java:

1. Lấy provider nền `SunPKCS11`.
2. Gọi `configure("pkcs11.cfg")` và **giữ đối tượng `Provider` trả về**; đó mới là provider đã gắn cấu hình thiết bị.
3. Tạo `KeyStore.getInstance("PKCS11", configuredProvider)`.
4. Gọi `load(null, pin)` để mở/login token; với `PKCS11` không đọc file keystore bytes.
5. Gọi `getKey(alias, null)` để nhận `PrivateKey` dạng provider handle. Thường lấy public key qua `getCertificate(alias).getPublicKey()`.
6. Đưa **nguyên đối tượng `PrivateKey`** vào `Signature.initSign` hay `Cipher.init`; không biến nó thành `PKCS8EncodedKeySpec`.

Với trường hợp phải dùng đúng HSM, truyền provider cụ thể vào `getInstance(..., provider)` để tránh một engine phần mềm không hiểu loại key handle. Có thể đăng ký provider với `Security.addProvider(...)` cho việc chọn tự động, nhưng không cần nếu tất cả `KeyStore`/`Signature`/`Cipher` đều được tạo bằng chính provider object vừa cấu hình. Đăng ký toàn cục thay đổi registry của JVM và nên là quyết định có chủ đích.

Một alias trong `PKCS11 KeyStore` có thể phụ thuộc vào cách liên kết certificate với private key và thuộc tính `CKA_ID`/`CKA_LABEL`; không giả định rằng mọi private key trên token đều hiện thành entry có certificate trong `KeyStore` Java.

## 5. Ví dụ chạy thật với Java 17+: kiểm tra API trên khóa phần mềm

Trước khi kết nối phần cứng, ví dụ dưới đây giúp thấy rõ `Signature` khác `Cipher` ra sao. **Đây là khóa RSA phần mềm được tạo trong JVM, có thể xuất; ví dụ không giả lập HSM và không chứng minh private key không xuất được.** Hai hành động ký và giải mã dùng cùng interface `PrivateKey`, nên ta có thể đối chiếu với phần token tiếp theo.

Tạo file `CryptoSoftwareDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class CryptoSoftwareDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        byte[] message = "document-v1".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        System.out.println("Signature valid: " + verifier.verify(signature));

        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        String transformation = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
        Cipher encryption = Cipher.getInstance(transformation);
        encryption.init(Cipher.ENCRYPT_MODE, pair.getPublic(), oaep);
        byte[] ciphertext = encryption.doFinal(message);

        Cipher decryption = Cipher.getInstance(transformation);
        decryption.init(Cipher.DECRYPT_MODE, pair.getPrivate(), oaep);
        byte[] plaintext = decryption.doFinal(ciphertext);
        System.out.println("OAEP round-trip valid: " +
                Arrays.equals(message, plaintext));
        System.out.println("Software private key encodable: " +
                (pair.getPrivate().getEncoded() != null));
    }
}
```

Trong thư mục chứa file:

```bash
javac --release 17 CryptoSoftwareDemo.java
java CryptoSoftwareDemo
```

**Output trên JDK thông thường** (đã kiểm chứng bằng biên dịch/chạy trên OpenJDK):

```text
Signature valid: true
OAEP round-trip valid: true
Software private key encodable: true
```

Giải thích:

- `Signature` thực hiện sign với private key, verify bằng public key. `Signature` không cần xuất private key để giao cho bên xác minh.
- `Cipher` mã hóa bằng **public key**, giải mã bằng **private key**. Trong ví dụ dùng RSA-OAEP với SHA-256 và tham số MGF1/SHA-256 **được chỉ định rõ ở hai phía** để tránh hiểu lầm giữa tên transformation và mặc định provider.
- Dòng cuối cho thấy khóa phần mềm của JDK thông thường **có thể được encode**. Với khóa opaque trên token, không viết mã phụ thuộc vào `getEncoded()` hoặc giả định có thể dựng lại private key bằng `KeyFactory`.

Không dùng RSA để mã hóa trực tiếp file hoặc payload dài: RSA có giới hạn kích thước đầu vào theo modulus/padding. Với dữ liệu lớn, thiết kế envelope encryption (AES-GCM + cơ chế bọc/trao đổi khóa được hỗ trợ) và kiểm tra toàn bộ chuỗi dữ liệu.

## 6. Ví dụ `Signature` trên private key không xuất được từ HSM

Ví dụ Java dưới đây dùng **PKCS#11 token có thật**. Nó biên dịch được trên Java 17+, nhưng để chạy cần thư viện vendor, token hoạt động, alias thực, khóa RSA được phép ký và certificate public key tương ứng. Không có hardware/token thì **không có output thành công để khẳng định**.

Tạo file `HsmSignatureDemo.java`:

```java
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;

public class HsmSignatureDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java HsmSignatureDemo <pkcs11-config> <key-alias>");
            return;
        }

        Provider bridge = Security.getProvider("SunPKCS11");
        if (bridge == null) {
            throw new IllegalStateException("SunPKCS11 is unavailable");
        }
        Provider tokenProvider = bridge.configure(args[0]);

        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Interactive console required");
        }
        char[] pin = console.readPassword("Token PIN: ");
        if (pin == null) throw new IllegalStateException("PIN input canceled");

        KeyStore store = KeyStore.getInstance("PKCS11", tokenProvider);
        try {
            store.load(null, pin);
        } finally {
            Arrays.fill(pin, '\0');
        }

        Key key = store.getKey(args[1], null);
        if (!(key instanceof PrivateKey privateKey)) {
            throw new IllegalStateException("Alias is not a private key");
        }
        Certificate certificate = store.getCertificate(args[1]);
        if (certificate == null) {
            throw new IllegalStateException("Alias has no certificate");
        }

        byte[] data = "document-v1".getBytes(StandardCharsets.UTF_8);
        Signature signer =
                Signature.getInstance("SHA256withRSA", tokenProvider);
        signer.initSign(privateKey);
        signer.update(data);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(data);
        System.out.println("Signature valid: " + verifier.verify(signature));
        System.out.println("Key algorithm: " + privateKey.getAlgorithm());
    }
}
```

Biên dịch:

```bash
javac --release 17 HsmSignatureDemo.java
```

Chạy tại **terminal tương tác** với đường dẫn config và alias HSM đã được cấp:

```bash
java HsmSignatureDemo /etc/crypto/pkcs11.cfg server-signing-key
```

Ví dụ giả định khóa là RSA và provider hỗ trợ `SHA256withRSA`. Nếu chạy thành công với khóa/certificate đúng thì `Signature valid: true` và `Key algorithm: RSA` là kết quả thường gặp. Tên alias, PIN, provider và quyền khóa tùy token nên không coi đây là output đảm bảo.

**Những gì mã thực sự làm:**

1. `Provider.configure(...)` liên kết Java bridge với thư viện gốc. Dùng object trả về, không dùng object `bridge` chưa được cấu hình.
2. `store.load(null, pin)` cung cấp PIN cho token; biến `pin` của ứng dụng được xóa trong `finally` sau lệnh load. Trên thiết bị dùng PIN-pad hoặc cơ chế login khác, thay bằng callback/`AuthProvider` theo hướng dẫn vendor.
3. `getKey(alias, null)` lấy một **reference/handle** đến khóa, không gọi `getEncoded()` và không đưa private key lên log hay file.
4. `Signature.getInstance("SHA256withRSA", tokenProvider)` buộc engine ký dùng đúng provider có handle. Lệnh `sign()` yêu cầu token thực thi phép ký theo mechanism mà token/provider hỗ trợ. Tùy mechanism, bước băm có thể diễn ra trong JVM hoặc token; không nên hứa toàn bộ dữ liệu đầu vào đều ở phần cứng.
5. `Signature.getInstance("SHA256withRSA")` để verify có thể dùng public key từ certificate bằng provider phù hợp khác; **bên xác minh không cần private key**.

Kết quả `verify(...) == true` chỉ xác nhận chữ ký khớp public key/dữ liệu/thuật toán. Nó **không** tự chứng minh certificate đáng tin, chuỗi CA hợp lệ, chứng chỉ chưa bị thu hồi, hay private key thật sự được sinh trong HSM; các chính sách này phải đánh giá riêng.

`System.console()` có thể `null` khi chạy qua IDE hoặc môi trường không có TTY. Khi đó thiết kế cơ chế xác thực an toàn (callback, OS credential channel, vault hoặc identity của workload) phù hợp vendor, tránh hard-code PIN hoặc truyền PIN trên command line. Token có `CKA_ALWAYS_AUTHENTICATE` hoặc bắt PIN lại cho mỗi lần dùng khóa sẽ cần luồng xác thực theo provider thay vì giả định `load` là đủ.

## 7. `Cipher` với private key opaque: dùng đúng thao tác và kiểm tra thực tế provider

Với RSA, thao tác private-key phía `Cipher` thường là **giải mã** hoặc **unwrap**. `Cipher` không biến private key thành byte array; provider dùng key handle để yêu cầu token thực hiện phép toán.

Mã ngắn minh họa **trường hợp provider thật sự hỗ trợ RSA-OAEP với private key trên thiết bị** (đây là đoạn đặt trong method, với `tokenProvider`, `privateKey`, `ciphertext` đã có):

```java
OAEPParameterSpec oaep = new OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT);
Cipher cipher = Cipher.getInstance(
        "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", tokenProvider);
cipher.init(Cipher.DECRYPT_MODE, privateKey, oaep);
byte[] plaintext = cipher.doFinal(ciphertext);
```

Các import tương ứng: `javax.crypto.Cipher`, `javax.crypto.spec.OAEPParameterSpec`, `javax.crypto.spec.PSource` và `java.security.spec.MGF1ParameterSpec`. Mã này là **minh họa phụ thuộc capability của provider**, không phải ví dụ chạy độc lập hay cam kết SunPKCS11 có OAEP.

**Điểm cần kiểm tra:** Trong bảng thuật toán SunPKCS11 của tài liệu Oracle Java 21, các transformation RSA `Cipher` được liệt kê là `RSA/ECB/NoPadding` và `RSA/ECB/PKCS1Padding`. Bảng đó **không liệt kê RSA-OAEP**. Vì vậy, dù `SunJCE` phần mềm hỗ trợ OAEP, không được suy ra `Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", tokenProvider)` chắc chắn chạy. Ngay cả khi token native có `CKM_RSA_PKCS_OAEP`, bridge JCA/JCE của phiên bản JDK/vendor cụ thể vẫn phải expose transformation đó. Kiểm tra service provider và tài liệu/tích hợp thực tế trước khi chọn kiến trúc.

Với một hệ thống cũ bắt buộc giải mã RSA PKCS#1 v1.5 và provider cho phép `CKM_RSA_PKCS`, đây là method Java **chỉ minh họa compatibility**, không phải thuật toán ưu tiên cho thiết kế mới:

```java
static byte[] decryptLegacyRsa(
        byte[] ciphertext, PrivateKey privateKey, Provider tokenProvider)
        throws java.security.GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(
            "RSA/ECB/PKCS1Padding", tokenProvider);
    cipher.init(Cipher.DECRYPT_MODE, privateKey);
    return cipher.doFinal(ciphertext);
}
```

Đoạn method này cần import `javax.crypto.Cipher`, `java.security.PrivateKey` và `java.security.Provider`. Nó phải được tích hợp với một **giao thức đã có biện pháp chống padding oracle**; không thiết kế API giải mã nhận ciphertext tùy ý rồi trả thông tin lỗi padding khác nhau. `RSA/ECB/NoPadding` cũng không phải phép mã hóa an toàn để dùng trực tiếp.

Đối với hệ thống mới, hãy ưu tiên giải pháp được chuẩn hóa và được cả hai đầu hỗ trợ (ví dụ RSA-OAEP nếu provider có, hoặc KEM/SDK được vendor hỗ trợ) cùng mã hóa dữ liệu đối xứng có xác thực. **Không tự hạ cấp sang PKCS1Padding/NoPadding** chỉ để tránh lỗi `NoSuchPaddingException`.

Trong envelope encryption, phân biệt:

- RSA private key nằm lại trên HSM, nhưng `Cipher.doFinal` có thể trả về **khóa phiên AES hoặc plaintext** cho JVM. Điều này đáp ứng mục tiêu *private key không xuất*, chưa chắc đáp ứng mục tiêu *mọi khóa và dữ liệu đều nằm trong HSM*.
- Nếu muốn AES key cũng không xuất, thiết bị phải hỗ trợ unwrap/derive thành **token-resident `SecretKey`** với policy phù hợp và một engine AES có thể dùng handle đó. Cần kiểm tra `CKA_UNWRAP`, `CKA_ENCRYPT`/`CKA_DECRYPT` và hành vi nhà cung cấp.
- Xác minh token có mechanism, trạng thái session và key usage phù hợp trước khi triển khai; `PrivateKey.getAlgorithm()` trả `RSA` không chứng minh khóa đó có quyền giải mã.

## 8. Vì sao một số cách “chuyển khóa sang provider khác” thất bại?

Khi một phần mềm nhận `PrivateKey` chứa handle phần cứng:

```text
Token A: PrivateKey(handle = token-specific)
      |
      +-- Signature của Provider A: có thể nhận handle -> ký
      |
      +-- Signature của Provider B: có thể không hiểu handle -> InvalidKeyException
      |
      +-- KeyFactory + PKCS8EncodedKeySpec: không thể dựng lại nếu không có bytes
```

`KeyFactory.getKeySpec(privateKey, PKCS8EncodedKeySpec.class)` hoặc `new PKCS8EncodedKeySpec(privateKey.getEncoded())` là cách **trích xuất/khôi phục khóa phần mềm**; với opaque key, nó có thể thất bại hoặc vi phạm mục tiêu bảo vệ khóa. Không cố ép kiểu sang `RSAPrivateCrtKey` để đọc modulus/private exponent. Các object token key thường chỉ triển khai `PrivateKey` tổng quát; việc chúng không triển khai interface chi tiết là có chủ đích.

Để giải quyết lỗi provider mismatch, chọn engine hỗ trợ chính handle đó; không sửa bằng cách xuất khóa ra RAM, lưu file PKCS#8 hay “tạo bản sao key” sang provider khác. Provider nào thực hiện thao tác cần được kiểm chứng qua `signature.getProvider().getName()` hoặc `cipher.getProvider().getName()` sau khi khởi tạo, đồng thời xác nhận thiết bị và phiên phù hợp.

**Chú ý:** Pin provider cụ thể là hợp lý khi bắt buộc dùng một khóa token. Với các thao tác không dùng token, việc hard-code provider JDK có thể làm giảm tính di động; hãy phân biệt hai trường hợp.

## 9. An toàn vận hành khi private key nằm trong HSM

**Quản trị khóa:** Sinh khóa trực tiếp trong HSM nếu cần cam kết không có bản sao private key bên ngoài; thiết lập usage flags tối thiểu. Quản lý `CKA_EXTRACTABLE`, `CKA_SENSITIVE`, xuất/wrap, clone/backup và chính sách xóa/rotation bằng công cụ vendor. Đừng đặt cả quyền `SIGN` lẫn `DECRYPT` nếu nghiệp vụ chỉ cần một quyền.

**Quản trị quyền dùng khóa:** Một attacker có quyền gọi `sign()` liên tục vẫn có thể lạm dụng private key dù không lấy được bytes. Cần xác thực caller, phân quyền theo alias/purpose, tách môi trường, audit, quota/rate limiting, approval hoặc quorum cho thao tác nhạy cảm. Kiểm tra thiết bị có yêu cầu mỗi lần ký phải xác thực lại hay không.

**Quản trị dữ liệu:** Chữ ký có thể được xuất công khai; plaintext giải mã thì rất nhạy cảm. Hạn chế ghi log request chứa dữ liệu rõ, PIN, raw key, session token và dữ liệu đã giải mã. Xóa biến chứa PIN sau sử dụng; dùng luồng nhập an toàn. Không mặc định rằng dọn mảng Java bằng `Arrays.fill` bảo đảm mọi bản sao dữ liệu nhạy cảm đã bị xóa ở native/JVM.

**Tính sẵn sàng:** Kết nối HSM là tài nguyên có session và giới hạn song song. Token rút ra, mạng đứt, PIN khóa, hết session, cơ chế disabled hoặc thay slot đều làm ký/giải mã lỗi. Thiết kế retry phù hợp (tránh ký lặp giao dịch nghiệp vụ), idempotency, health checks, HA/rotation và quy trình backup phục hồi không vi phạm policy key.

**Kiểm chứng bảo mật:** Chỉ kiểm thử `getEncoded() == null` là quá yếu. Kết hợp kiểm tra thuộc tính khóa trên token, policy nhà cung cấp, việc không xuất được qua các API có quyền phù hợp, chứng cứ sinh/provision khóa, audit và quy trình vận hành. Phép ký hợp lệ chứng minh possession/use of key, không chứng minh nguồn gốc phần cứng nếu thiếu bằng chứng bổ sung.

## 10. Chẩn đoán lỗi theo đúng lớp

| Triệu chứng | Giải thích có thể | Hướng kiểm tra |
|---|---|---|
| `Security.getProvider("SunPKCS11") == null` | JDK/JRE không có hoặc chưa kích hoạt module/provider tương ứng | Kiểm tra bản JDK, module `jdk.crypto.cryptoki`, cấu hình security |
| `ProviderException` khi `configure(...)` | Sai đường dẫn/thư viện native, bitness/ABI, thiếu dependency, token/slot chưa sẵn | Kiểm tra file cấu hình, thư viện vendor, OS, slot/token và log provider |
| `KeyStore.load(null, pin)` lỗi | PIN sai, token bị khóa, thiết bị chưa login/không sẵn | Kiểm tra cơ chế login, role, retry count, trạng thái thiết bị |
| `getKey(alias, null)` trả `null` | Alias không có entry private key thấy được trong KeyStore này | Kiểm tra alias, `CKA_ID`/certificate association, slot và quyền |
| `NoSuchAlgorithmException` hoặc `NoSuchPaddingException` | Provider không quảng bá engine/transformation đó | Kiểm tra `Provider.Service` và bảng mechanisms; đừng tự động hạ cấp |
| `InvalidKeyException` tại `initSign`/`init` | Key không tương thích provider, sai loại khóa, hoặc usage bị cấm | Xác minh provider, `CKA_SIGN`/`CKA_DECRYPT`, tên thuật toán, token identity |
| `SignatureException` / native PKCS#11 error | Khóa/session/mechanism/token không đáp ứng thao tác | Đọc native `CKR_*`, quyền, rate limits, session state, PIN-per-use |
| `BadPaddingException` khi giải mã | Ciphertext/params sai hoặc kiểm tra padding thất bại | Kiểm tra thuật toán, OAEP hash/MGF1/label, ciphertext; tránh lộ lỗi oracle |
| `verify(...)` trả `false` | Dữ liệu, public key, algorithm hoặc chữ ký không khớp | Kiểm tra đúng chứng chỉ, encoding byte chính xác, dữ liệu sau canonicalization |
| Token ký được nhưng plaintext giải mã lỗi | Quyền ký khác quyền giải mã hoặc provider thiếu mechanism | Kiểm tra `CKA_DECRYPT`, transformation, kích thước khóa, ciphertext format |

Để quan sát vấn đề PKCS#11 ở môi trường kiểm thử, Oracle mô tả `showInfo=true` trong cấu hình provider và các cờ `-Djava.security.debug=sunpkcs11` hoặc `-Djava.security.debug=pkcs11keystore`. Chỉ bật debug trong phạm vi được kiểm soát; không chia sẻ log có thông tin thiết bị, danh tính hoặc dữ liệu nhạy cảm.

## 11. Tình huống thiết kế

**Tình huống A — ký bản kê giao dịch bằng HSM:** Service nhận bản kê, kiểm tra quyền ký, chuẩn hóa chính xác bytes cần ký, gọi `Signature.initSign(hsmPrivateKey)`, lưu chữ ký và ID/certificate tương ứng. Phía đối tác `verify` bằng public key và thực thi certificate trust policy. **Không** lưu PEM private key trên ổ đĩa ứng dụng.

**Tình huống B — giải mã một RSA envelope cũ:** Dữ liệu được mã hóa bằng public key và padding đã được thống nhất; HSM có `CKA_DECRYPT`. Service chọn `Cipher` provider và algorithm đúng, nhận plaintext trong vùng nhớ ứng dụng rồi xử lý bảo mật. Nếu token chỉ hỗ trợ PKCS#1 v1.5, phải xử lý giới hạn legacy và giảm nguy cơ oracle bằng thiết kế giao thức hiện có; không mặc nhiên coi đây là lựa chọn cho dữ liệu mới.

**Tình huống C — khóa được đánh dấu non-exportable nhưng có thể ký sai mục đích:** Một microservice bị chiếm quyền không thể lấy bytes private key nhưng vẫn có thể yêu cầu HSM ký nội dung giả. Khả năng “khóa không xuất” giảm rủi ro sao chép khóa, **không** thay thế authorization, kiểm soát nội dung được ký và giám sát lệnh HSM.

**Tình huống D — ứng dụng Java đổi từ token A sang B:** Java API `PrivateKey`, `Signature`/`Cipher` có thể giữ nguyên, nhưng cấu hình provider, alias, chứng chỉ, khả năng cơ chế, cách login và policy có thể khác. Di chuyển thiết bị phải kiểm tra feature matrix và dữ liệu thực tế; không suy luận hai token PKCS#11 có đầy đủ thuật toán tương đồng.

## 12. Câu hỏi tự kiểm tra

1. Private key có `getEncoded() == null` thì `Signature.sign()` còn chạy được không? **Có thể**, nếu đối tượng đó là handle hợp lệ và provider/token thực hiện được thuật toán ký với quyền tương ứng.
2. Kết quả `Signature.verify(...) == true` chứng minh key nằm trong HSM không? **Không**. Nó chứng minh tính hợp lệ mật mã với public key trên dữ liệu được kiểm tra.
3. Một private key RSA được phép `CKA_SIGN` có tự được phép `Cipher.DECRYPT_MODE` không? **Không**. Phải có quyền giải mã và provider hỗ trợ cipher/mechanism.
4. Token native có RSA-OAEP thì `SunPKCS11` Java 21 tự động expose `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` không? **Không thể giả định**; phải kiểm tra danh sách thuật toán của Java provider và bản JDK/vender cụ thể.
5. `Cipher.doFinal(...)` với opaque RSA private key có đảm bảo plaintext không rời HSM không? **Không**; thông thường byte[] plaintext được trả lại cho JVM.
6. Vì sao không dùng `Cipher.ENCRYPT_MODE` với RSA private key làm chữ ký? Vì chữ ký có **chuẩn algorithm/hash/padding, semantics xác minh và bảo vệ** riêng; API đúng là `Signature`.
7. Nếu `getKey(alias, null)` trả một `PrivateKey` nhưng `Signature.initSign` báo `InvalidKeyException`, nên làm gì? Kiểm tra provider sử dụng handle, loại khóa, key usage, token/session và mechanism; không xuất khóa.

## Tài liệu tham khảo chính thức

- Oracle, *PKCS#11 Reference Guide* (Java 21): https://docs.oracle.com/en/java/javase/21/security/pkcs11-reference-guide1.html — cấu hình SunPKCS11, token login, danh sách thuật toán hỗ trợ và cơ chế token key.
- Oracle, *Java Cryptography Architecture (JCA) Reference Guide* (Java 21): https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html — provider/engine, `Signature`, `KeyStore` và `Cipher`.
- Oracle, *JDK Providers Documentation* (Java 21): https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html — dịch vụ/thuật toán của SunJCE, SunPKCS11 và các provider JDK.
- Java SE 21 API, `Key`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Key.html — `getFormat()`/`getEncoded()` có thể trả `null` khi không có encoding.
- OASIS, *PKCS #11 Base Specification v2.40*: https://docs.oasis-open.org/pkcs11/pkcs11-base/v2.40/os/pkcs11-base-v2.40-os.html — `CKA_EXTRACTABLE`, `CKA_SENSITIVE`, `CKA_SIGN`, `CKA_DECRYPT`, `CKA_UNWRAP` và thuộc tính lịch sử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-006">20.6 Quản lý phiên thiết bị, lỗi PIN, token bị tháo và khả năng chạy đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# 20.6. Quản lý phiên thiết bị, lỗi PIN, token bị tháo và khả năng chạy đồng thời

> **Ngữ cảnh:** Java Security/JCA-JCE dùng khóa nằm trong smartcard, USB token hoặc HSM qua provider PKCS#11. Trọng tâm là vòng đời phiên với thiết bị, cách xử lý sai/khóa PIN, mất kết nối khi token bị tháo và điều phối nhiều luồng an toàn. Cần phân biệt hành vi do chuẩn PKCS#11 quy định với hành vi cụ thể của Java provider và thiết bị.

## 1. Học xong bài này cần nắm gì?

- Phân biệt **slot, token, session, trạng thái login, đối tượng khóa và Java provider**; giải thích đối tượng nào phải được tạo lại sau khi tháo hoặc thay token.
- Thiết kế luồng mở kết nối → xác thực PIN → thực hiện thao tác → hoàn tất/giải phóng, có timeout và đường lỗi rõ ràng.
- Phân loại sai PIN, PIN đã khóa, token không tồn tại, session không hợp lệ và hết session; chọn chính sách xử lý phù hợp, không thử PIN liên tục hay tự nâng quyền.
- Giải thích vì sao có nhiều Java thread **không có nghĩa** được phép dùng chung một PKCS#11 session hoặc một đối tượng `Signature`.
- Áp dụng backpressure, giới hạn concurrency, kiểm tra lại định danh token sau hot-plug và chính sách thử lại có kiểm soát.
- Biết phạm vi của `SunPKCS11`, `KeyStore.Builder`, `AuthProvider` và điều gì cần kiểm chứng thêm với tài liệu driver/HSM.

## 2. Mô hình các lớp: Java API không đồng nhất với session phần cứng

```text
Ứng dụng Java (request/worker threads)
      │ KeyStore, PrivateKey, Signature, Cipher, Mac
      ▼
JCA/JCE + provider đã cấu hình (ví dụ SunPKCS11)
      │ Quản lý object handle, session, login, mapping exception
      ▼
PKCS#11 Cryptoki library của nhà sản xuất (native)
      │ C_OpenSession, C_Login, C_Sign..., C_CloseSession...
      ▼
Slot/reader ── Token (smartcard/USB token/HSM partition)
                 └─ Private key / certificate / policy / PIN state
```

| Thành phần | Vai trò | Giới hạn cần nhớ |
| --- | --- | --- |
| **Slot** | Vị trí logic thiết bị/token mà PKCS#11 liệt kê | Số slot hoặc `slotListIndex` không phải danh tính tin cậy của token; topology có thể thay đổi. |
| **Token** | Thiết bị/partition lưu khóa và trạng thái xác thực | Token có thể bị tháo, reset, thay bằng token khác; tình trạng PIN khóa thường được thiết bị duy trì. |
| **Session PKCS#11** | Kết nối logic đến token (R/O hoặc R/W) | Có handle, state và giới hạn số lượng; một session thông thường chỉ chạy một chuỗi thao tác tại một thời điểm. |
| **Login state** | Trạng thái người dùng/SO của ứng dụng đối với token | PKCS#11 chia sẻ login/logout state giữa **các session của cùng ứng dụng với cùng token**; logout không chỉ ảnh hưởng một request riêng. |
| **Object handle/PrivateKey** | Tham chiếu để dùng đối tượng khóa trên token | Handle phiên và đối tượng có thể bị vô hiệu khi session đóng/token tháo, logout hoặc backend reset; không xem handle cũ là bền vững. |
| **JCA/JCE engine** | `Signature`, `Cipher`, `Mac`, `KeyStore`... | Stateful, cần quản lý theo operation; provider quản lý native sessions bên dưới. |
| **Provider `SunPKCS11`** | Adapter JCA/JCE của JDK đến thư viện PKCS#11 | Không bảo đảm mọi provider/driver cung cấp cùng cơ chế hot-swap, exception, pooling hay concurrency. |

Ở **raw PKCS#11**, ứng dụng tự gọi `C_OpenSession` / `C_CloseSession`. Khi dùng **Java `SunPKCS11`**, provider thực hiện nhiều thao tác này bên trong; ứng dụng thường **không sở hữu `CK_SESSION_HANDLE`** và không nên giả vờ có API Java chuẩn `openSession()`/`closeSession()` tương ứng.

Phiên R/O cho phép đọc đối tượng token và thực hiện thao tác mật mã phù hợp. Chỉ khi cần tạo, sửa, xóa **token objects** mới cần R/W session, theo quyền và giới hạn token. R/W không có nghĩa được phép bypass xác thực. Với token có PIN, quyền truy cập **private key** thông thường cần login; yêu cầu thực tế tùy thuộc chính sách từng token và thuộc tính khóa.

## 3. Vòng đời session và login

### 3.1. Quy trình hợp lý cho dịch vụ ký số

1. **Discover và xác minh thiết bị:** nhận ra slot có token; xác định đúng token dự kiến bằng identity đã lưu và tín hiệu đáng tin khác (certificate/public-key fingerprint, cấu hình partition, chính sách tin cậy). Alias giống nhau không đủ để chấp nhận token mới.
2. **Load provider theo cấu hình:** nạp đúng native library, chọn slot, đảm bảo cơ chế ký, version và driver tương thích; không âm thầm dùng một provider khác nếu không được phép.
3. **Login có chủ đích:** để `KeyStore.load(...)`/`KeyStore.Builder` hoặc `AuthProvider` kích hoạt login theo cách được hỗ trợ. PIN lấy từ kênh phù hợp, không hardcode.
4. **Tạo/thuê tài nguyên theo operation:** giới hạn số operation, không share mutable `Signature`; đảm bảo token/generation chưa đổi trước thao tác.
5. **Thực hiện và kết thúc:** xử lý thành công hoặc lỗi, kết thúc operation native đúng quy trình và trả tài nguyên ở `finally`.
6. **Quản lý logout:** logout khi phiên làm việc kết thúc hoặc theo chính sách; trước đó phải bảo đảm không còn thao tác in-flight dùng cùng login state. Không logout sau mỗi request nếu những request khác đang dùng token.
7. **Tháo/thay token:** đánh dấu mọi tham chiếu thuộc generation cũ là không dùng được; dừng cấp operation mới, kiểm tra lại identity và đăng nhập lại khi token phù hợp trở lại.

**Hai mức “phiên”:** phiên đăng nhập/ngữ cảnh nghiệp vụ của người dùng là một khái niệm; PKCS#11 session là tài nguyên của thư viện và token. Không dùng một PKCS#11 session làm “HTTP session” riêng cho mỗi user bằng trực giác, đặc biệt vì login state của các session thuộc cùng ứng dụng/token có thể dùng chung.

### 3.2. Sơ đồ trạng thái cấp ứng dụng

```text
ABSENT --(insert/discover)--> NEEDS_LOGIN --(PIN hợp lệ)--> READY
   ^                             │  ▲                         │
   │                             │  └--(PIN sai: dừng hỏi lại)--┘
   │                             └--(PIN bị khóa)--> PIN_LOCKED
   │                                                   │
   │                                     quy trình mở khóa theo vendor
   │
   +---(remove/device lost)--- READY / NEEDS_LOGIN / PIN_LOCKED
                                  │
                                  └--(degraded, drain/reject in-flight)
```

Đây là **state machine của ứng dụng**, không phải toàn bộ state machine PKCS#11. Trạng thái `PIN_LOCKED` là tín hiệu từ thiết bị: ứng dụng không được tự chuyển nó thành `READY` chỉ vì người dùng cắm lại **cùng** token; quy trình reset/mở khóa do quản trị viên và thiết bị quy định.

Khi thiết bị đang `READY` nhưng xảy ra lỗi kết nối, có thể cần một trạng thái nội bộ như `DEGRADED`/`RECONNECTING`: ngừng nhận request mới, đợi/huỷ request đang chạy theo deadline và chỉ cấp quyền dùng lại sau một **re-initialization** thực sự. Không giữ nguyên tham chiếu `PrivateKey` cũ rồi coi trạng thái “reconnected” là đủ.

### 3.3. Shared login và logout có tác động rộng

Trong PKCS#11, khi một session login vào token, những session khác của **cùng ứng dụng/token** chuyển sang cùng trạng thái login. Khi logout, các session ấy cũng mất trạng thái login; object handles riêng tư có thể không còn hợp lệ. Điều đó dẫn tới race điển hình:

```text
Thread A                    Thread B
initSign(privateKey)
                            logout token
sign() -> fail/undefined assumptions about object state
```

Đừng dùng logout trong `finally` của từng yêu cầu nếu các luồng khác còn dùng khóa. Một người quản lý lifecycle chung phải biết khi nào mọi operation kết thúc, khi nào có thể logout, và khi nào phải shutdown/recreate provider theo tài liệu nhà sản xuất. Nếu có nhiều quy trình sử dụng cùng HSM, tác động có thể còn phụ thuộc session/account isolation của vendor.

## 4. Sai PIN, PIN bị khóa và xác thực lại

### 4.1. Những tình huống khác nhau về bản chất

| Trạng thái/PKCS#11 return code | Ý nghĩa thường gặp | Chính sách ứng dụng |
| --- | --- | --- |
| `CKR_PIN_INCORRECT` | PIN được đưa vào để xác thực **không đúng** | Không tự thử lại PIN cũ. Hiển thị lỗi an toàn, yêu cầu thao tác có ý thức của người dùng và áp dụng rate limit. |
| `CKR_PIN_LOCKED` | PIN bị token khóa sau các lần xác thực không hợp lệ hoặc theo policy | **Ngừng toàn bộ thử login**; kích hoạt quy trình mở khóa theo nhà cung cấp/quản trị. |
| `CKR_PIN_EXPIRED` | PIN hết hạn, phải đổi khi token hỗ trợ | Chuyển sang flow đổi PIN được vendor hỗ trợ; không dùng mẹo đổi alias/khóa để né. |
| `CKR_USER_NOT_LOGGED_IN` | Operation yêu cầu login nhưng hiện không có | Xác minh session/token hiện tại; thực hiện login hợp lệ đúng một lần theo policy, không suy diễn là “PIN sai”. |
| `CKR_USER_ALREADY_LOGGED_IN` | Ứng dụng/token đã login | Kiểm tra race/shared state; không nhất thiết là lỗi xác thực. |
| `CKR_SESSION_HANDLE_INVALID` | Handle đã không còn hợp lệ, có thể sau token removal | Invalidate state/handle và khởi tạo lại nếu thiết bị đúng đã quay lại. |
| `CKR_TOKEN_NOT_PRESENT`, `CKR_DEVICE_REMOVED` | Không có token hoặc bị tháo trong khi thao tác | Ngừng dùng token; chuyển degraded/absent; không retry trên handle cũ. |
| `CKR_SESSION_COUNT` | Thử mở session vượt giới hạn token | Backpressure, đóng session dư khi có thể, giới hạn concurrency, đo tải; retry có giới hạn sau khi giải phóng tài nguyên. |

Các code trên thuộc **PKCS#11 native API**. Ở JCA/JCE, exception có thể là `IOException`, `KeyStoreException`, `UnrecoverableKeyException`, `ProviderException` hoặc exception do provider/vendor tạo ra. Java **không bảo đảm một loại exception duy nhất tương ứng 1:1 với một mã `CKR_*`**. Phân loại theo API, nguyên nhân gốc đáng tin và tài liệu driver, đồng thời giữ thông điệp hiển thị an toàn. Không viết xử lý production dựa trên phép `message.contains("CKR_PIN_LOCKED")` không được nhà cung cấp cam kết.

### 4.2. Vì sao không tự retry PIN?

Một số smartcard khóa sau **rất ít** lần nhập sai; retry tự động trong application hoặc nhiều replica cùng gọi login có thể làm cạn quota nhanh. Việc truyền cùng PIN sai ba lần trong vòng retry bằng exponential backoff vẫn là ba lần sai trên thiết bị. Hệ thống cần:

- **0 automatic retries** sau `PIN_INCORRECT` hoặc `PIN_LOCKED`; chỉ tiếp nhận một lần nhập mới có chủ ý, tuân theo chính sách thiết bị.
- Chặn brute force ở cấp API/gateway; nếu có nhiều service dùng cùng token, giới hạn theo token và người dùng thay vì mỗi process đếm độc lập.
- Không mặc định biết số lần PIN còn lại; cờ như `CKF_USER_PIN_COUNT_LOW`, `CKF_USER_PIN_FINAL_TRY`, `CKF_USER_PIN_LOCKED` (nếu được cung cấp) là tín hiệu trạng thái thiết bị, không phải bộ đếm chính xác xuyên mọi vendor.
- Đối với PIN bị khóa, dùng quy trình khôi phục/admin/PIN unblock theo chính sách. **Không gọi thao tác khởi tạo/xóa token để “reset PIN”** nếu chưa có phê duyệt vì có thể làm mất đối tượng khóa.

### 4.3. PIN đi qua bộ nhớ như thế nào?

`KeyStore.load(null, pin)` có thể nhận `char[]` chứa PIN để provider login; ứng dụng phải giới hạn vòng đời `char[]`, xóa bằng `Arrays.fill(pin, '\0')` ngay sau khi provider trả về (tốt nhất trong `finally`) và tránh chuyển PIN thành `String` bất biến, log, URL, command-line flag hoặc file mã nguồn. Việc xóa `char[]` **không đảm bảo** xóa mọi bản sao trong native driver/provider.

`KeyStore.CallbackHandlerProtection` giúp callback yêu cầu PIN khi cần, thay vì lưu PIN trong cấu hình. Nếu thiết bị có **protected authentication path** (PIN pad/biometric), ưu tiên flow được vendor hỗ trợ để PIN không đi qua process Java. Handler phải phục vụ đúng `PasswordCallback`; không giả định luôn có console trong server headless và không tự lặp prompt khi PIN lỗi.

## 5. Token bị tháo: điều gì hỏng và làm sao phục hồi?

### 5.1. Tại sao session cũ không dùng lại được?

Theo PKCS#11, tháo token sẽ làm vô hiệu các session gắn với token. Nếu token bị tháo **trước** khi gọi hàm, lỗi có thể là `CKR_SESSION_HANDLE_INVALID`; nếu bị tháo **trong lúc** hàm chạy, có thể là `CKR_DEVICE_REMOVED`. Không dựa vào một error code đơn lẻ để khẳng định chính xác thời điểm thiết bị bị tháo: native driver và thứ tự ưu tiên lỗi có thể khác trực giác.

Ngay cả khi cùng một chiếc token được cắm lại, **không bảo đảm** session handle, object handle, login, key object ở Java hoặc trạng thái operation đang dở vẫn hữu hiệu. Provider có thể cache kết nối/khóa; thiết kế phải coi toàn bộ object/generation liên quan là nghi vấn sau hot-plug.

### 5.2. Recovery pipeline an toàn

1. Phát hiện bằng thao tác thất bại hoặc sự kiện slot của vendor (native `C_WaitForSlotEvent` nếu được hỗ trợ); có thể dùng polling có giới hạn nếu cần. Không giả định Java `KeyStore` có API chuẩn để lắng nghe USB removal.
2. **Trip circuit breaker** cho token/slot đó: không cấp operation mới; trả `SERVICE_UNAVAILABLE` hoặc chuyển sang thiết bị dự phòng **được cấu hình và cấp quyền**.
3. Gắn generation/epoch mới, làm vô hiệu những request, cache `KeyStore`, `PrivateKey`, `Signature` và object handles phụ thuộc vào thiết bị cũ; cho thao tác đang chạy kết thúc/lỗi theo deadline và thiết kế rollback nghiệp vụ.
4. Khi phát hiện token mới: kiểm tra đúng target partition/serial/certificate/public key và policy; không chọn chỉ vì alias giống hoặc slot index trùng.
5. Tạo lại tài nguyên theo hướng dẫn provider; dùng `KeyStore.Builder` hoặc flow reopen do nhà sản xuất hỗ trợ. Login lại khi cần; nếu PIN bị khóa thì vẫn bị khóa.
6. Chạy health check không làm lộ khóa (ví dụ đọc metadata được phép; hoặc test signing bằng key kiểm tra chuyên dụng nếu policy cho phép), sau đó mới mở lại traffic.
7. Ghi log sự kiện transition và thời gian downtime; bảo vệ identifiers nhạy cảm.

**Bài toán kết quả không chắc chắn:** nếu token biến mất khi `Signature.sign()` đang trả kết quả, native operation **có thể đã được thực hiện** dù ứng dụng chưa nhận bytes. Đừng phát lại nghiệp vụ ký số “mù” sau reconnect: cần operation/job ID, lưu trạng thái ở service và quy tắc idempotency/kiểm tra kết quả phù hợp. Một signature mới có thể khác bytes nhưng tương đương về tính đúng, hoặc tạo thêm audit entry/counter của HSM; vì vậy retry phải có quyết định của tầng nghiệp vụ.

### 5.3. Nhận diện đúng token sau khi cắm lại

`slotListIndex = 0` chỉ có nghĩa “phần tử trong danh sách slot được driver trả ra” ở một ngữ cảnh. Nó **không** là khóa định danh bền vững khi người vận hành đổi reader/thứ tự thiết bị. Phối hợp những bằng chứng đủ tin cậy: cấu hình slot/partition, token label/serial từ driver, public key fingerprint của certificate dự kiến, chứng chỉ và chain theo trust policy, loại key và capability. Không tin mỗi tên alias hoặc label do thiết bị bất kỳ khai báo.

## 6. Khả năng chạy đồng thời và session pool

### 6.1. Quy tắc gốc của PKCS#11

Một native PKCS#11 session thông thường chỉ có **một operation đang hoạt động**. Gọi song song vào **cùng session handle** từ hai thread cho hành vi không được chuẩn đảm bảo, ngay cả khi thư viện có cơ chế khóa hỗ trợ multi-thread. Nếu muốn song song thật sự, dùng các session khác nhau (nếu token hỗ trợ), hoặc tuần tự hóa truy cập.

**Lưu ý:** “library thread-safe” không suy ra “một session thread-safe”. `C_Initialize` và các khả năng khóa của thư viện quyết định việc có thể gọi vào library từ nhiều thread; quy tắc tránh đồng thời trên cùng session vẫn phải được tôn trọng. Token cũng có thể không hỗ trợ số session hoặc loại operation mong muốn; thực tế throughput phụ thuộc phần cứng, firmware và license.

| Cấp tài nguyên | Chia sẻ có an toàn không? | Mẫu thiết kế |
| --- | --- | --- |
| `Provider` / factory JCA | Có thể được dùng làm cấu hình chung; provider quản lý native state | Một instance đã cấu hình cho từng slot/partition được phép, quản lý bởi lifecycle manager. |
| `KeyStore`/cache alias | Không mặc định thread-safe khi load/reload và lấy key | Đồng bộ tác vụ load/refresh; cache theo generation của token, làm mới sau hot-swap. |
| `Signature`, `Cipher`, `Mac` | **Không share** một instance mutable cho các request đồng thời | Tạo engine mới cho mỗi operation; `initSign` / `update` / `sign` nằm trong phạm vi một request. |
| `CK_SESSION_HANDLE` native | Không truy cập đồng thời trong cùng session | Một owner tại một thời điểm, hoặc session pool cho mỗi operation; không trả handle hỏng lại pool. |
| Login/logout token | Có trạng thái chung cho các session trong ứng dụng | Một coordinator quản lý authentication và logout khi không còn in-flight operations. |
| Token/HSM throughput | Có giới hạn, có thể kém scale khi tăng thread | Hàng đợi hữu hạn, `Semaphore`/bulkhead, timeout, metrics và load test theo vendor. |

### 6.2. Giới hạn operation thay vì tăng thread vô hạn

Với một thiết bị hỗ trợ thực tế 4 operation đồng thời, mở 100 Java worker threads không tạo ra 100 kênh ký độc lập. Nó có thể gây `CKR_SESSION_COUNT`, tăng latency, gây lỗi native, làm backlog/OOM và lặp login. Dùng `Semaphore(maxInflight)` (có thể đặt thấp hơn giới hạn ghi trên token để dành headroom), deadline nhận permit và hàng đợi bounded; trả lỗi quá tải có kiểm soát thay vì tràn tài nguyên.

Bất kỳ `acquire` thành công phải có `release` trong `finally`; dừng trước khi acquire nếu token không `READY`. Sau acquire, **kiểm tra lại generation và identity** vì thiết bị có thể bị tháo trong lúc chờ. Nếu token biến mất trong native call, fail closed và invalidate tài nguyên; không gắn một `Signature` cũ vào một token mới.

### 6.3. Bốn race condition điển hình

1. **Login/logout race:** thread B logout trong khi A đang ký → private handles/authorization state của A có thể hỏng. Chỉ logout sau drain in-flight jobs.
2. **Hot-plug race:** A kiểm tra token có mặt, chờ semaphore; B tháo token; A được permit và ký bằng key cũ → phải kiểm tra **lại** epoch sau khi lấy permit.
3. **Session-pool reuse:** A trả session lỗi vào pool; B nhận lại rồi gọi `sign` → kiểm tra health, discard session/engine sai và rebuild thay vì recycle bằng mọi giá.
4. **Pool cạn:** nhiều request đồng thời không có backpressure → `CKR_SESSION_COUNT` và connection storm. Cần bounded queue + timeout + adaptive concurrency theo đo lường.

### 6.4. Quan sát và vận hành

Theo dõi `active_operations`, `semaphore_wait_ms`, `queue_depth`, `token_present`, `login_state`, `session_exhaustion_count`, `pin_failure_count`, `token_removal_count`, `provider_recovery_ms`, tỷ lệ lỗi theo **nhóm** (PIN/device/session/unsupported/timeout), và phiên bản firmware/driver trong inventory bảo mật. Không đưa PIN, private-key material, nguyên vẹn mã lỗi chứa secret, hoặc raw cryptographic input vào log. Correlation ID cho request và generation ID giúp tìm race khi token bị thay.

## 7. Tích hợp với Java 17: SunPKCS11, KeyStore và callback

Phần này là **đoạn tích hợp có điều kiện**, yêu cầu HSM/token và native library thực sự. Không chạy được nếu máy chỉ có JDK; các tham số đường dẫn/slot/alias phải lấy từ cấu hình triển khai tin cậy.

Một tệp cấu hình SunPKCS11 ví dụ (đường dẫn chỉ minh họa, phụ thuộc OS/vendor):

```properties
name = SigningHsm
library = /usr/local/lib/libvendorpkcs11.so
slotListIndex = 0
```

Nạp instance đã cấu hình và khai báo callback PIN từ ứng dụng:

```java
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import javax.security.auth.callback.CallbackHandler;

// pinHandler do ứng dụng cung cấp: PasswordCallback từ UI/PIN-pad
// hoặc kho bí mật theo chính sách; không tự in/log PIN.
Provider base = Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("SunPKCS11 provider unavailable");
}
Provider configured = base.configure("/etc/security/signing-hsm.cfg");

// Provider nên được truyền tường minh để không chọn nhầm token.
// Có thể gọi Security.addProvider(configured) nếu ứng dụng cần đăng ký
// và có chính sách quản lý vòng đời/độ ưu tiên rõ ràng.
KeyStore.CallbackHandlerProtection protection =
        new KeyStore.CallbackHandlerProtection(pinHandler);
KeyStore.Builder builder =
        KeyStore.Builder.newInstance("PKCS11", configured, protection);
KeyStore ks = builder.getKeyStore();
```

Các biến ngoài đoạn trích: `pinHandler` là một `CallbackHandler` được tạo trong ứng dụng, có thể phục vụ `PasswordCallback`; đường dẫn trỏ đến config có thật và native library có thể load. Đây **không phải class độc lập**, và `slotListIndex` không được tin như định danh token tuyệt đối.

Theo hướng dẫn JDK, `KeyStore.Builder` hỗ trợ luồng smartcard được cắm/tháo và yêu cầu PIN khi cần; với SunPKCS11, nó có thể hỏi lại khi thay card. Tuy nhiên **không suy ra** mọi HSM vendor và mọi tổ hợp driver/JDK có thể tự hồi sinh sau mọi lỗi. Cần kiểm chứng vòng đời và recovery trên thiết bị thật; nhiều hệ thống dùng một coordinator để thay builder/provider hoặc restart worker cách ly.

Khi có private key thật và đang ở trạng thái READY, một operation ký có thể dùng JCA:

```java
import java.security.Key;
import java.security.PrivateKey;
import java.security.Signature;

// ks và configured là các biến từ đoạn tạo provider phía trên.
// alias, expected certificate/key identity được lấy từ cấu hình tin cậy.
Key key = ks.getKey("signing-key", null);
if (!(key instanceof PrivateKey privateKey)) {
    throw new IllegalStateException("Expected a token-backed private key");
}
Signature signer = Signature.getInstance("SHA256withRSA", configured);
signer.initSign(privateKey);
signer.update(documentDigestOrMessage);
byte[] signature = signer.sign();
```

`documentDigestOrMessage` là một `byte[]` được tầng nghiệp vụ chuẩn bị đúng với quy ước thuật toán; **`SHA256withRSA` tự băm input**, vì vậy nếu đã băm trước thì phải chọn giao thức/thuật toán phù hợp, tránh băm hai lần. `Signature` mới được tạo **cho mỗi operation**. Code chưa thể hiện verify certificate chain, token identity, timeout, error classification và concurrency gate; các phần này là trách nhiệm của dịch vụ bao quanh.

Một lựa chọn khác là `KeyStore.load(null, char[] pin)` để thực hiện login với PIN; đây là cách đơn giản cho keystore tương đối tĩnh. Với thiết bị tháo/cắm lại, `KeyStore.Builder` và callback phù hợp hơn. `AuthProvider.login(subject, handler)` và `logout()` dành cho những trường hợp cần quản lý login ở cấp provider; **không** tự ý logout lúc có thread khác sử dụng cùng token.

## 8. Java 17+ chạy độc lập: mô phỏng lifecycle và bounded concurrency

Ví dụ dưới dùng **thiết bị giả lập trong bộ nhớ**. Nó không gọi `SunPKCS11`, không có PIN thật, không tạo chữ ký mật mã thật và **không thể mô phỏng** chính xác hành vi native driver. Mục tiêu là học cách đặt một coordinator ở **bên ngoài** JCA: khóa PIN thì ngừng; tháo token thì invalidation; sau đổi token phải xác minh identity; cho tối đa 2 operation đang chạy cùng lúc.

Lưu tên tệp `TokenSessionDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenSessionDemo {
    enum State { ABSENT, NEEDS_LOGIN, READY, PIN_LOCKED }

    static final class Gate {
        private final Semaphore slots = new Semaphore(2, true);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();

        private State state = State.ABSENT;
        private String serial;
        private long generation;

        synchronized void inserted(String newSerial) {
            serial = newSerial;
            generation++;
            state = State.NEEDS_LOGIN;
        }

        // Chỉ mô phỏng báo lỗi từ thiết bị. Không tự đếm hay retry PIN.
        synchronized void incorrectPin() {
            if (state != State.NEEDS_LOGIN)
                throw new IllegalStateException("INVALID_STATE");
        }

        synchronized void pinLocked() {
            if (state != State.ABSENT) state = State.PIN_LOCKED;
        }

        synchronized void loginSuccess() {
            if (state != State.NEEDS_LOGIN)
                throw new IllegalStateException("LOGIN_DENIED");
            state = State.READY;
        }

        synchronized void removed() {
            generation++;
            serial = null;
            state = State.ABSENT;
        }

        synchronized State state() { return state; }

        private synchronized long requireReady(String expected) {
            if (state != State.READY)
                throw new IllegalStateException("NOT_READY");
            if (!expected.equals(serial))
                throw new IllegalStateException("TOKEN_MISMATCH");
            return generation;
        }

        private synchronized void checkVersion(String expected, long version) {
            if (state != State.READY || generation != version
                    || !expected.equals(serial)) {
                throw new IllegalStateException("TOKEN_CHANGED");
            }
        }

        String sign(String expected, int job) throws InterruptedException {
            long version = requireReady(expected);

            // Giới hạn tải; không giữ một mutable Signature dùng chung.
            if (!slots.tryAcquire(2, TimeUnit.SECONDS))
                throw new IllegalStateException("BUSY");
            int n = active.incrementAndGet();
            peak.accumulateAndGet(n, Math::max);
            try {
                checkVersion(expected, version); // recheck sau khi chờ permit
                Thread.sleep(15); // mô phỏng device operation, KHÔNG ký thật
                checkVersion(expected, version); // fail closed nếu đổi token
                return "signature-" + job;
            } finally {
                active.decrementAndGet();
                slots.release();
            }
        }

        int peak() { return peak.get(); }
        int freePermits() { return slots.availablePermits(); }
    }

    public static void main(String[] args) throws Exception {
        Gate gate = new Gate();
        gate.inserted("TOKEN-A");
        System.out.println("inserted=" + gate.state());

        gate.incorrectPin(); // sự kiện sai PIN giả lập
        System.out.println("bad_pin=" + gate.state());
        gate.pinLocked();
        System.out.println("locked=" + gate.state());

        gate.removed();
        System.out.println("removed=" + gate.state());

        // Cắm TOKEN-B là token KHÁC; TOKEN-A nếu bị khóa vẫn bị khóa thật.
        gate.inserted("TOKEN-B");
        gate.loginSuccess();
        try {
            gate.sign("TOKEN-A", 99);
        } catch (IllegalStateException ex) {
            System.out.println("old_token=" + ex.getMessage());
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                final int job = i;
                futures.add(pool.submit(() -> gate.sign("TOKEN-B", job)));
            }
            int completed = 0;
            for (Future<String> future : futures) {
                if (future.get().startsWith("signature-")) completed++;
            }
            System.out.println("completed=" + completed);
            System.out.println("bounded=" + (gate.peak() <= 2));
            System.out.println("permits_restored=" + (gate.freePermits() == 2));
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac --release 17 TokenSessionDemo.java
java TokenSessionDemo
```

Stdout mong đợi:

```text
inserted=NEEDS_LOGIN
bad_pin=NEEDS_LOGIN
locked=PIN_LOCKED
removed=ABSENT
old_token=TOKEN_MISMATCH
completed=6
bounded=true
permits_restored=true
```

**Giải thích:** có bốn worker nhưng chỉ hai `slots`; `Semaphore` chặn vượt giới hạn và `finally` trả permit. Việc kiểm tra token/generation **cả trước và sau khi lấy permit** giúp từ chối tham chiếu cũ. Một event `incorrectPin()` **không** thử PIN lần nữa. Đổi từ `TOKEN-A` sang `TOKEN-B` phải đi qua `NEEDS_LOGIN`, đồng thời request đòi token cũ bị chặn. Một cuộc gọi mô phỏng có thể hoàn thành bên trong mặc dù epoch đổi sau đó; hệ thống thật vẫn phải giải quyết “kết quả không chắc chắn” bằng trạng thái nghiệp vụ/idempotency.

**Giới hạn rõ ràng:** `Gate` không đại diện cho native session, không xác thực serial, không quản lý driver; cả `Thread.sleep` và chuỗi `signature-N` chỉ mô phỏng timing/output để minh họa coordinator. Khi tích hợp thật, `sign(...)` phải gọi operation JCA bên trong gate sau khi xác minh key/token và phân loại lỗi; không tin vào chuỗi `expected` do client trực tiếp gửi.

## 9. Quy tắc retry, chuyển trạng thái và phục hồi theo lỗi

| Lỗi/tình huống | Có retry ngay không? | Hành động cần thiết |
| --- | --- | --- |
| Sai PIN | **Không** | Không dùng lại PIN cũ; chờ user/admin nhập lại có chủ đích, rate-limit và ghi audit không chứa PIN. |
| PIN locked/expired | **Không** | Chặn login/operation; flow mở khóa/đổi PIN hợp lệ theo token. |
| Token removed/not present | **Không trên handle cũ** | Invalidate provider-related references; đợi token phù hợp, kiểm tra identity, khởi tạo lại. |
| Session handle invalid | **Không trên session cũ** | Discard session/engine; tái khám phá, xác thực lại và kiểm chứng tính nhất quán trước khi cấp request. |
| Session count exhausted | **Có thể, có giới hạn** | Backpressure, chờ permit/session được giải phóng; deadline và bounded retry. |
| Operation active/session used concurrently | **Không mù** | Tìm lỗi ownership/cleanup; đưa session lỗi ra khỏi pool, hoàn tất/hủy/đóng theo native API hợp lệ. |
| Vendor device error, timeout | **Chỉ theo loại lỗi** | Circuit breaker, health check, bounded recovery; đánh dấu kết quả ký có thể không chắc chắn. |
| Sai token/key identity | **Không** | Dừng, cảnh báo cấu hình/thiết bị; không chọn khóa khác chỉ vì alias tương tự. |

Không retry theo vòng lặp vô hạn với `catch (Exception e)`; đặc biệt không catch `ProviderException` rồi âm thầm trả một chữ ký từ phần mềm nếu yêu cầu phải dùng HSM. Những policy như dùng thiết bị thứ hai, software fallback, key rotation hay tự động login lại phải được chủ sản phẩm và security policy cho phép từ trước.

## 10. Kiểm thử theo kịch bản, không chỉ đường thành công

### 10.1. Ma trận thử nghiệm

| Trường hợp | Cách kích hoạt trong test/staging | Kết quả đúng |
| --- | --- | --- |
| Một lần PIN sai | Cố tình nhập sai trên **token thử nghiệm** | Không tự retry, thông báo an toàn, không log PIN; vẫn tuân theo lockout thật. |
| PIN bị khóa | Dùng simulator/vendor test fixture, tránh cố tình khóa token sản xuất | Không tiếp tục login; cần quy trình recovery. |
| Tháo trước operation | Gỡ token giữa lúc request còn trong queue | Recheck epoch nhận ra device absent; không ký bằng tham chiếu cũ. |
| Tháo trong operation | Fault injection/hardware test | Fail closed, loại bỏ state cũ, đánh dấu trạng thái nghiệp vụ chưa chắc chắn nếu cần. |
| Thay bằng token khác cùng alias | Cắm token B có alias trùng A | Chặn nếu fingerprint/identity không đúng expectation. |
| Vượt capacity | Load test tăng mức concurrent requests đến giới hạn | Queue bounded/timeout, không leak session/permit; latency và memory có giới hạn. |
| Logout giữa các request | Test coordinator với operation in-flight | Không cho logout trước drain; không tạo race shared login. |
| Vendor restart/network HSM lỗi | Simulator hoặc sandbox | Circuit breaker và chỉ phục hồi khi đạt health/identity check. |
| Operation lỗi sau khi initSign | Inject exception khi provider đang ký | Giải phóng permit; engine/session lỗi không bị tái sử dụng sai. |

Không chạy thử nhiều PIN sai trên HSM/token thật đang vận hành; dùng thiết bị kiểm thử hoặc khả năng fault injection được cấp phép. Với các thao tác ký, không dùng private production key để tạo thử hàng loạt nếu chính sách audit/chi phí/token counter không cho phép.

### 10.2. Các lỗi thiết kế dễ xuất hiện

**Lỗi 1 — Dùng một `Signature` singleton:**

```java
// Sai: một engine mutable bị nhiều request gọi initSign/update/sign xen kẽ.
static Signature sharedSigner;
```

**Sửa:** một `Signature` mới mỗi operation; gắn request với token/key generation đúng và đóng/rút tài nguyên ở đường lỗi.

**Lỗi 2 — Thấy PIN sai thì tự thử lại:**

```text
Wrong PIN -> retry x 3 -> token locked -> mọi request mất dịch vụ
```

**Sửa:** không auto retry PIN; chỉ nhận credential mới qua flow xác thực rõ ràng, có rate-limit tập trung.

**Lỗi 3 — Reinsert xong dùng key cũ:**

```text
A (serial=001) -> removal -> B (serial=002)
cache["signing-key"] vẫn trỏ private key/handle từ A
```

**Sửa:** generation-based invalidation, kiểm tra fingerprint/identity, rebuild keystore/engine, login lại khi cần.

**Lỗi 4 — Logout ở cuối từng request:**

```text
Request A: sign()
Request B: logout()  (ảnh hưởng login state chung)
Request A: sign() lỗi bất ngờ
```

**Sửa:** một lifecycle manager điều phối drain + logout, tách request lifecycle khỏi token login lifecycle.

**Lỗi 5 — Cứ thêm Java threads khi HSM chậm:**

Không giải quyết giới hạn session hoặc firmware, chỉ tạo backlog. Cần concurrency limit theo token, bounded queue, deadline, metrics và load test để tìm throughput thực.

## 11. Checklist triển khai ở production

- [ ] Xác định token/HSM, driver version, JDK version, slot/partition, cơ chế ký và danh tính public key/certificate dự kiến; ghi nhận quy trình thay token.
- [ ] Cấu hình explicit provider, tránh fallback sai chính sách; xác minh khóa nằm trong thiết bị theo policy (không mặc nhiên export private key).
- [ ] Thiết kế login với callback/PIN pad; không hardcode, log, cache `String` PIN; dừng tự retry khi PIN sai/khóa.
- [ ] Duy trì state machine và generation khi token insert/remove/reset; chỉ mở lại traffic sau identity + health check.
- [ ] Giới hạn concurrency theo token/partition bằng semaphore/queue và timeout; không share mutable JCA engine hoặc PKCS#11 session.
- [ ] Quản lý logout khi mọi operation phụ thuộc token đã drain; không logout trong `finally` của từng HTTP request.
- [ ] Có error classification theo driver/JCA exception thực, circuit breaker và chính sách recovery với kết quả ký không chắc chắn.
- [ ] Có test fault injection/hot-plug/timeout/load thực; theo dõi latency, token errors và session pressure; kiểm soát thông tin nhạy cảm trong log.

## 12. Tài liệu tham khảo

1. **OASIS PKCS#11 Cryptographic Token Interface v3.1**, phần *Session Management*, *Return values* và mã `CKR_*`: https://docs.oasis-open.org/pkcs11/pkcs11-spec/v3.1/os/pkcs11-spec-v3.1-os.html
2. **OASIS PKCS#11 User Guide v3.2**, phần *Sessions*, *Concurrency*, *Handles*, *Shared login state*: https://docs.oasis-open.org/pkcs11/pkcs11-ug/v3.2/pkcs11-ug-v3.2.html
3. **Oracle Java 17 — PKCS#11 Reference Guide**, `SunPKCS11`, `Token Login` và `KeyStore.Builder`: https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html
4. **Java SE 17 — KeyStore.Builder API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.Builder.html
5. **Java SE 17 — Provider.configure API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.html
6. **Java SE 17 — AuthProvider API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/AuthProvider.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-007">20.7 Khác biệt giữa provider phần mềm, HSM và kho khóa của hệ điều hành</a>

<details>
<summary>Xem chi tiết</summary>

# 20.7. Khác biệt giữa provider phần mềm, HSM và kho khóa của hệ điều hành

## Mục tiêu

Bài này so sánh ba cách Java sử dụng khóa mật mã: **provider phần mềm**, **Hardware Security Module (HSM)** và **kho khóa gốc của hệ điều hành**. Sau khi học, bạn cần phân biệt rõ nơi thực hiện phép toán, nơi giữ khóa, mức độ chống trích xuất, ranh giới tin cậy, cách Java kết nối, chi phí vận hành và những giới hạn khi chuyển đổi giữa các lựa chọn.

Đây là chủ đề của **Java Security/JCA**, không chỉ là lựa chọn tên file chứa khóa. Một ứng dụng có thể dùng nhiều provider cùng lúc: SHA-256 chạy trong provider phần mềm, khóa ký nằm trong HSM, còn chứng chỉ tin cậy lấy từ kho của hệ điều hành.

## 1. Bốn khái niệm dễ nhầm: API, provider, keystore và phần cứng

- **JCA/JCE (Java Cryptography Architecture/Extension):** những API chuẩn như `Signature`, `Cipher`, `KeyPairGenerator`, `KeyStore`, `KeyAgreement`, `Mac`. Mã gọi API mà không cần trực tiếp làm việc với thư viện native của thiết bị.
- **Security provider:** một hiện thực `java.security.Provider` đăng ký dịch vụ, thuật toán và tên keystore mà nó hỗ trợ. Ví dụ các provider JDK như `SUN`, `SunRsaSign`, `SunJCE`, `SunEC`; tên và tập dịch vụ phụ thuộc JDK.
- **KeyStore:** API `java.security.KeyStore` quản lý entry và alias. Nó có thể ánh xạ tới **file** (`PKCS12`), **token/HSM** (`PKCS11`), hoặc **kho hệ điều hành** (`Windows-MY`, `KeychainStore`). `KeyStore` không nhất thiết là một file; nó cũng không mặc định mã hóa mọi dữ liệu mà ứng dụng sử dụng.
- **Nơi giữ khóa / ranh giới vận hành:** khóa có thể là bytes trong bộ nhớ JVM, khóa trong file có mật khẩu, handle tới private key trong thiết bị, hoặc khóa được quản lý bởi dịch vụ/OS. Đây là thuộc tính của **cách cấp phát và chính sách khóa**, không thể suy ra hoàn toàn từ tên interface.

Sơ đồ tổng thể:

```text
Java application
       |
       +-- Signature / Cipher / KeyPairGenerator / KeyStore (JCA/JCE API)
                          |
                 Java Security Provider
                  /         |           \
         Software         SunPKCS11     Native OS provider
        SUN/SunJCE/       bridge         SunMSCAPI / Apple
        SunRsaSign/...       |                |
              |         PKCS#11 library   Windows store /
        JVM crypto code      |             macOS Keychain
              |            HSM / token        |
       Key bytes có thể   Key handle;          Khóa/chứng chỉ
       xuất / lưu file     thiết bị xử lý      theo chính sách OS
```

**Phân biệt then chốt:** `SunPKCS11` là **cầu nối** tới thư viện PKCS#11 native; bản thân tên này không chứng minh khóa đang ở trong HSM thật. Một thư viện PKCS#11 có thể đại diện cho HSM vật lý, smart card hoặc token phần mềm để thử nghiệm. Tương tự, có khóa trong Windows Certificate Store hay macOS Keychain **không tự chứng minh** nó được bảo vệ bởi TPM/Secure Enclave.

## 2. Bảng so sánh tổng quan

| Khía cạnh | Provider phần mềm | HSM / crypto token qua PKCS#11 | Kho khóa hệ điều hành |
| --- | --- | --- | --- |
| Nơi tính toán | Thư viện chạy trên CPU host/JVM (có thể tận dụng tăng tốc CPU) | Token/HSM thực hiện các operation mà nó hỗ trợ; Java đi qua provider + driver | API/mật mã native của OS; một số khóa có thể được phần cứng bảo vệ tùy cấu hình |
| Nơi lưu private key | Bytes trong RAM; có thể được lưu vào `PKCS12`/nguồn khác | Key object trong token, Java thường thấy handle/đối tượng tham chiếu | Kho Windows/macOS và container key native tương ứng |
| Khả năng xuất private key | Thường có thể xuất nếu key và policy cho phép | Có thể cấm bằng thuộc tính token, ví dụ non-extractable | Phụ thuộc key provider, key policy, user và phần cứng hỗ trợ |
| Quyền truy cập | File permission, secret manager, mật khẩu, process | Slot/token, PIN, user/role, device policy, session | User/machine identity, OS ACL, key access control, đôi khi yêu cầu tương tác |
| Mức cô lập khỏi host | Không phải ranh giới phần cứng riêng | Có thể có ranh giới phần cứng/tamper resistance; phụ thuộc sản phẩm và cấu hình | Phụ thuộc OS và khả năng hardware-backed; thường tích hợp tốt với tài khoản máy |
| Độ trễ | Thường thấp nhất cho thao tác thường xuyên | Qua driver/thiết bị/network (nếu HSM mạng), có chi phí phiên/throughput | Qua bridge native/OS, phụ thuộc API và yêu cầu xác nhận |
| Tính di động | Cao nếu thuật toán và định dạng phổ biến | Cần driver PKCS#11, phiên bản, slot, cơ chế, quản trị HSM | Gắn với Windows/macOS, user/machine, provider tương ứng |
| Backup/khôi phục | Backup an toàn file/secret; kiểm soát bản sao | Theo quy trình HA/replication/backup đặc thù HSM, có thể không export khóa | Theo cơ chế roaming/backup/key recovery của OS hoặc doanh nghiệp |
| Chi phí vận hành | Thấp đến trung bình | Thường cao: thiết bị/dịch vụ, HA, vận hành, quota, PIN/session | Ít hạ tầng crypto riêng nhưng có chi phí quản lý endpoint/identity |
| Ví dụ phù hợp | Dev/test, ứng dụng thông thường với secret management tốt | CA key, code signing, khóa ký có quy định bảo vệ cao | Khóa mTLS của ứng dụng doanh nghiệp trên máy được quản lý |

Bảng này mô tả các **kịch bản thường gặp**, không phải cam kết tuyệt đối. Ví dụ software key có thể nằm trong KMS thông qua API, HSM có thể hỗ trợ một phần thuật toán, và một OS store có thể trỏ tới khóa phần cứng.

## 3. Provider phần mềm trong Java

### 3.1. Cơ chế

Khi gọi `Signature.getInstance("SHA256withRSA")` hoặc `KeyGenerator.getInstance("AES")` mà không chỉ định provider, JCA xét các provider theo quy tắc lựa chọn hiện hành. Provider đáp ứng dịch vụ nhận phép toán. Với khóa thông thường do provider phần mềm sinh ra, ứng dụng thường giữ được đối tượng `PrivateKey`/`SecretKey` và một số khóa có thể trả bytes qua `getEncoded()`.

Các provider phần mềm phổ biến đi kèm OpenJDK/Oracle JDK gồm `SUN` (ví dụ `KeyStore.PKCS12`), `SunRsaSign` (chữ ký RSA), `SunJCE` (AES, HMAC, v.v.) và `SunEC` (EC/EdDSA). Không coi việc thấy tên provider là chứng chỉ đánh giá bảo mật hoặc danh sách thuật toán bất biến trên mọi bản phân phối JDK.

Ưu điểm:

- Cài đặt đơn giản; không cần thiết bị/driver chuyên dụng cho phần lớn tác vụ.
- Thường có throughput tốt đối với mã hóa dữ liệu khối, hash, TLS và tác vụ phổ thông.
- Triển khai linh hoạt trên server, container, máy phát triển; thuật toán chuẩn dễ thử nghiệm.

Giới hạn:

- Nếu key material được giải mã vào RAM hoặc có `getEncoded()`, tiến trình bị xâm nhập, memory dump và logging sai có thể làm lộ khóa.
- Mật khẩu file keystore, quyền file, quyền process, backup, rotation và cách cấp secret quyết định nhiều hơn bản thân phần mở rộng `.p12`.
- `PKCS12` cho phép đóng gói và bảo vệ khóa trong file; **không tạo ranh giới HSM**. Copy được file và biết đủ bí mật giải mã thì có thể phục hồi khóa.
- Không dùng file keystore như một “secret manager toàn năng”, không hardcode mật khẩu production vào mã hoặc image.

### 3.2. Ví dụ Java 17+ chạy độc lập: ký và lưu khóa AES trong PKCS12

**Tệp `SoftwareProviderDemo.java`**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class SoftwareProviderDemo {
    public static void main(String[] args) throws Exception {
        // Dùng provider mặc định theo cấu hình JCA của JDK.
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair keyPair = rsa.generateKeyPair();

        byte[] message = "provider-demo".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        byte[] signed = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);
        System.out.println("Signature verified: " + verifier.verify(signed));

        // Mật khẩu chỉ dành cho demo trong RAM: KHÔNG dùng giá trị
        // hardcode hoặc xuất file chứa khóa production.
        char[] password = "demo-only-change-me".toCharArray();
        try {
            KeyGenerator aes = KeyGenerator.getInstance("AES");
            aes.init(256);
            SecretKey secret = aes.generateKey();

            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, password); // tạo kho mới trong bộ nhớ
            store.setEntry("demo-aes",
                    new KeyStore.SecretKeyEntry(secret),
                    new KeyStore.PasswordProtection(password));

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            store.store(bytes, password); // giả lập lưu file PKCS12

            KeyStore reopened = KeyStore.getInstance("PKCS12");
            reopened.load(
                    new ByteArrayInputStream(bytes.toByteArray()),
                    password);
            SecretKey recovered = (SecretKey)
                    reopened.getKey("demo-aes", password);

            System.out.println("PKCS12 key restored: "
                    + Arrays.equals(
                            secret.getEncoded(), recovered.getEncoded()));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Chạy với Java 17+:

```bash
javac SoftwareProviderDemo.java
java SoftwareProviderDemo
```

Output logic trên JDK chuẩn hỗ trợ `RSA`, `SHA256withRSA`, `AES` và `PKCS12`:

```text
Signature verified: true
PKCS12 key restored: true
```

RSA signature bytes, AES key bytes và serialized PKCS12 **khác nhau qua mỗi lần chạy**. Ví dụ tạo/đọc kho ngay trong RAM để dễ học; trong sản xuất, phải lưu file với quyền phù hợp, quản lý mật khẩu ngoài mã và xóa/rotation bí mật theo quy trình. `Arrays.fill(password, '\0')` chỉ giảm thời gian tồn tại của **bản sao char[]**, không bảo đảm xóa mọi bản sao trong JVM hoặc garbage collector.

`Signature.getProvider()` có thể dùng để quan sát provider đã được chọn, nhưng với một số engine như `Signature`, provider có thể được lựa chọn muộn lúc `initSign(key)`. Nếu cần biết provider thực dùng, hãy kiểm tra **sau khi khởi tạo bằng khóa**.

## 4. HSM và PKCS#11 trong Java

### 4.1. HSM làm gì?

**Hardware Security Module** là thiết bị hoặc dịch vụ có ranh giới bảo vệ khóa và cơ chế thực hiện những phép mật mã được hỗ trợ bằng khóa nằm trong vùng kiểm soát của nó. Khi tạo khóa trong HSM với thuộc tính ngăn xuất, Java có thể gọi `Signature.initSign(privateKeyHandle)` mà không được cấp bytes của private key để đem ra ngoài thiết bị.

HSM thường hỗ trợ các chức năng theo sản phẩm: tạo/nhập khóa, ký, decrypt hoặc unwrap key, quản lý slot/session, phân quyền, audit, rotation/backup có kiểm soát. Tính năng nào dùng được phụ thuộc hardware, firmware, license, cấu hình và PKCS#11 mechanisms. Không mặc định HSM sẽ xử lý mọi phép `Cipher`, `Signature` hoặc thuật toán Java hỗ trợ.

**Non-extractable là chính sách trên key object**, không phải do thấy chữ “HSM”:

- Thuộc tính PKCS#11 như `CKA_EXTRACTABLE=false`, `CKA_SENSITIVE=true` là phần của chính sách đối với khóa, tùy cách tạo và khả năng thực thi.
- `privateKey.getEncoded() == null` thường gặp với khóa không xuất được, nhưng **không đủ làm chứng cứ** rằng khóa đang ở HSM thật hoặc chính sách đã được chứng nhận.
- “Không export được” không đồng nghĩa “không thể bị lạm dụng”: kẻ có quyền gọi ký vẫn có thể yêu cầu HSM ký dữ liệu trái phép. Quyền sử dụng khóa, chính sách signing, approval và audit vẫn thiết yếu.
- Token phần mềm (ví dụ PKCS#11 dùng trong môi trường test) có thể hiện diện với giao diện giống HSM vật lý; đánh giá trust boundary theo sản phẩm thực tế.

### 4.2. SunPKCS11 là adapter, không phải thiết bị

Trong Oracle/OpenJDK 17, `SunPKCS11` nằm ở module `jdk.crypto.cryptoki`. Nó chuyển các API JCA tới thư viện PKCS#11 native (`.so`, `.dll`, `.dylib`) của vendor. Các lớp còn lại vẫn là `KeyStore`, `Signature`, `Cipher`, `KeyPairGenerator`, v.v. nếu driver hỗ trợ. Chuỗi phụ thuộc:

```text
Java Signature.initSign(...) / Signature.sign()
          |
      SunPKCS11 provider (đã configure)
          |
      PKCS#11 vendor library + slot/session/PIN
          |
      HSM hoặc token tương ứng
          |
      Operation bằng private key, trả signature bytes
```

Ví dụ file cấu hình **`/etc/app/pkcs11.cfg`** cho một máy Linux giả định; địa chỉ và thư viện **phải thay bằng thông tin vendor thực tế**:

```properties
name = SigningHsm
library = /opt/vendor/lib/libvendorpkcs11.so
slotListIndex = 0
```

`slotListIndex = 0` nghĩa là chọn phần tử đầu trong danh sách slot mà thư viện báo; **không phải** khẳng định slot ID thật bằng 0. Cấu hình cụ thể có thể cần `slot`, chính sách mechanism, session hoặc tham số vendor khác. Phiên bản native library phải phù hợp kiến trúc JVM, thiết bị/daemon và quyền OS.

**Tệp `HsmConnectDemo.java`** — mã Java 17+ **biên dịch được**, nhưng chỉ chạy thành công khi có PKCS#11 library, token, slot và PIN hợp lệ:

```java
import java.io.Console;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

public class HsmConnectDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: java HsmConnectDemo <pkcs11-config-path>");
        }

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException("SunPKCS11 unavailable");
        }

        // configure trả về đối tượng provider đã cấu hình.
        Provider hsm = base.configure(args[0]);
        Security.addProvider(hsm);

        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run in a terminal for PIN");
        }
        char[] pin = console.readPassword("Token PIN: ");
        if (pin == null) {
            throw new IllegalStateException("No PIN supplied");
        }

        try {
            KeyStore tokenStore = KeyStore.getInstance("PKCS11", hsm);
            tokenStore.load(null, pin);
            System.out.println("Token keystore opened: true");
            System.out.println("Provider: " + tokenStore.getProvider().getName());
        } finally {
            Arrays.fill(pin, '\0');
        }
    }
}
```

Biên dịch:

```bash
javac HsmConnectDemo.java
```

Ví dụ chạy trên môi trường đã provision HSM:

```bash
java HsmConnectDemo /etc/app/pkcs11.cfg
```

Nếu mở token thành công, có dòng `Token keystore opened: true`; tên provider sẽ phụ thuộc `name` trong cấu hình (thường `SunPKCS11-SigningHsm`). Nếu thiết bị không tồn tại, chưa đăng nhập, sai slot/driver hoặc PIN lỗi thì **chương trình ném exception**, không có output thành công để giả lập. Thử PIN sai nhiều lần có thể khóa token theo chính sách thiết bị. `System.console()` có thể bằng `null` khi chạy qua IDE/service; service cần cơ chế nhận PIN/authorization tương thích với vendor và không hardcode PIN.

Sau khi mở token, mẫu thao tác ký **chỉ là fragment** và còn đòi hỏi alias private key/thuật toán tương ứng đã tồn tại trên HSM:

```java
// tokenStore và hsm từ chương trình kết nối ở trên.
String alias = "document-signing";
java.security.Key key = tokenStore.getKey(alias, null);
if (!(key instanceof java.security.PrivateKey privateKey)) {
    throw new IllegalStateException("No private key at alias");
}

java.security.Signature signature =
        java.security.Signature.getInstance("SHA256withRSA", hsm);
signature.initSign(privateKey);
signature.update("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
byte[] signed = signature.sign();
// Gửi signature bytes và chứng chỉ/public key cho phía xác minh.
```

Fragment này yêu cầu HSM hỗ trợ đúng mechanism RSA SHA-256 và alias tồn tại; nó không phải `main` hoàn chỉnh. Trong nhiều cấu hình, `getKey(alias, null)` dùng session đã login; một số token yêu cầu callback, PIN hoặc policy khác. Đừng lấy `getEncoded()` của private key làm bước bắt buộc.

### 4.3. Lưu ý vận hành

- **Key creation:** sinh khóa trong HSM nếu yêu cầu không cho private key tồn tại dạng plaintext trên host; nhập một khóa phần mềm rồi đánh dấu non-extractable **không xóa lịch sử bản sao** đã có bên ngoài.
- **Phiên và đăng nhập:** session pooling và giới hạn phiên/đăng nhập của token có thể gây bottleneck; tránh tạo provider và đăng nhập mới cho từng message.
- **Hiệu suất:** chữ ký RSA/ECDSA thường phù hợp với HSM; mã hóa dung lượng lớn nên cân nhắc **envelope encryption** (khóa dữ liệu ngắn hạn, dữ liệu mã hóa ở ứng dụng, HSM quản lý/wrap khóa) nếu phù hợp cơ chế được phê duyệt.
- **Tính sẵn sàng:** cân nhắc HSM cluster/partition, failover, restore, network timeout, thao tác có tính idempotent, quota và retry phù hợp. Không âm thầm fallback sang khóa phần mềm khi chính sách buộc dùng HSM.
- **Truy cập:** kiểm soát ai được yêu cầu sử dụng khóa, loại operation được phép, log metadata, rotate/cert lifecycle và separation of duties.
- **Kiểm chứng:** tính chống trích xuất hoặc chứng chỉ an toàn HSM phải được xác minh bằng cấu hình, chính sách và bằng chứng của thiết bị, không suy ra từ `SunPKCS11`.

PKCS#11 là giao diện thư viện native. Một **cloud KMS** cung cấp ký/giải mã qua REST/gRPC có thể sử dụng HSM bên trong, nhưng Java thường tích hợp qua SDK/API riêng; nó **không tự biến thành `KeyStore.PKCS11`**. So sánh HSM và KMS cần xét kỹ semantics của key version, IAM, latency, quota và tính năng.

## 5. Kho khóa gốc của hệ điều hành

### 5.1. Windows: SunMSCAPI và Windows-MY

Provider `SunMSCAPI` là cầu nối Java tới dịch vụ mật mã native của Windows. Các type keystore trên Oracle/OpenJDK 17 gồm:

- `Windows-MY` (alias của `Windows-MY-CURRENTUSER`): chứng chỉ cá nhân và private key được liên kết của tài khoản hiện tại.
- `Windows-ROOT` (alias của `Windows-ROOT-CURRENTUSER`): nơi chứa chứng chỉ gốc tin cậy của tài khoản hiện tại; **không nhầm nó với kho private key để ký**.
- `Windows-MY-LOCALMACHINE` và `Windows-ROOT-LOCALMACHINE`: các kho phạm vi máy, với quyền truy cập khác so với current user.

**Fragment Java chạy trên JDK Windows có SunMSCAPI**:

```java
KeyStore personal = KeyStore.getInstance("Windows-MY");
personal.load(null, null);
System.out.println(personal.getProvider().getName()); // SunMSCAPI
```

Fragment dùng `java.security.KeyStore`, cần ở trong class/method hoặc JShell. Nó có thể thất bại do JDK/OS/quyền hiện tại. Để ký bằng private key liên kết với chứng chỉ, ứng dụng cần tìm đúng alias, đọc `PrivateKey` theo khả năng provider, khởi tạo `Signature` tương thích và nhận quyền theo Windows key storage ACL.

Mở được `Windows-MY` **không bảo đảm** có private key có thể dùng. Một certificate entry có thể chỉ có public certificate; private key có thể ở user khác, machine khác, TPM hoặc provider native không hỗ trợ thao tác bạn yêu cầu. Dịch vụ Windows, account triển khai và container có thể nhìn thấy bộ alias khác máy người dùng tương tác.

### 5.2. macOS: Apple provider và KeychainStore

Trên JDK có provider `Apple`, kiểu `KeyStore` `KeychainStore` cung cấp quyền truy cập tới macOS Keychain:

```java
KeyStore keychain = KeyStore.getInstance("KeychainStore");
keychain.load(null, null);
System.out.println(keychain.getProvider().getName()); // Apple
```

Đây cũng là **fragment cho macOS/JDK tương thích**. Quyền truy cập phụ thuộc login session, keychain đang mở, key access control, process identity và có thể cần xác nhận của người dùng. Keychain và Secure Enclave là hai khái niệm riêng: một entry Keychain **không đồng nghĩa** khóa được sinh/lưu trong Secure Enclave, và việc Java `KeychainStore` có thể sử dụng mọi loại khóa phần cứng là điều phải kiểm tra theo phiên bản/provider.

### 5.3. Ưu điểm và giới hạn

**Ưu điểm:** có thể tận dụng OS key ACL, policy doanh nghiệp, certificate enrollment, trust management, smart card/tài khoản máy, và tránh phân phối file chứa private key riêng nếu khóa đã được provision trong kho OS.

**Giới hạn:** ứng dụng đa nền tảng phải xử lý nhiều keystore type; khác biệt giữa user store và machine store; service/headless jobs có thể không có interactive session; quy trình backup/roaming/rotation phụ thuộc OS; những operation không được bridge Java hỗ trợ sẽ thất bại dù OS có API tương ứng.

Nói cách khác, kho OS thích hợp khi ứng dụng cần tích hợp **danh tính và vòng đời thiết bị/người dùng**. Nó không tự động là một HSM, cũng không thay thế quy trình xác thực chain chứng chỉ hoặc kiểm tra certificate purpose của ứng dụng.

## 6. Quy tắc chọn provider và tránh lỗi gắn nhầm khóa

JCA có hai cách chọn provider:

```java
// Linh hoạt: chọn provider theo cấu hình, khả năng và khóa thực tế.
Signature flexible = Signature.getInstance("SHA256withRSA");

// Cố định: chỉ dùng provider đã chọn.
Signature pinned = Signature.getInstance("SHA256withRSA", hsm);
```

Cả hai dòng là fragment; `hsm` là đối tượng `Provider` đã cấu hình ở mục 4. Với nghiệp vụ có ràng buộc khóa HSM, thường cần xác định rõ khóa thuộc provider nào và bảo đảm không fallback sai policy. Với tác vụ hash/AES thông thường, chọn thuật toán mà không ghim provider giúp ứng dụng ít phụ thuộc nền tảng.

**Bẫy quan trọng:** `Signature.getInstance(...)` tạo engine nhưng provider có thể được lựa chọn muộn sau `initSign(privateKey)`. Nếu khóa là handle phần cứng và buộc signature engine của một provider phần mềm, lệnh `initSign` có thể ném `InvalidKeyException` vì provider đó không hiểu loại khóa. Ngược lại, `Signature.getInstance("SHA256withRSA", hsm)` có thể ném `NoSuchAlgorithmException` nếu HSM không có mechanism phù hợp.

Cách chẩn đoán an toàn:

```java
Signature engine = Signature.getInstance("SHA256withRSA");
engine.initSign(privateKey);
System.out.println("Actual provider: " + engine.getProvider().getName());
```

Đây là fragment khi đã có `PrivateKey privateKey`. **Gọi `getProvider()` sau `initSign`** để biết provider đã thực sự chọn trong lần khởi tạo đó; provider của `KeyStore` chỉ cho biết backend truy cập khóa và **không nhất thiết** bằng provider làm toàn bộ phép toán khác trong ứng dụng.

Nếu có yêu cầu dữ liệu/khóa “luôn trong HSM”, phải xác minh cả **nguồn sinh khóa, thuộc tính non-extractable, provider của phép toán, đường đi dữ liệu, quyền truy cập và audit**. Kiểm tra mỗi `getEncoded()==null` hay tên `Provider` không đủ.

## 7. Các kiểu KeyStore không tương đương nhau

| Type điển hình | Backend | Cách khởi tạo thường gặp | Điều không nên suy diễn |
| --- | --- | --- | --- |
| `PKCS12` | Container file hoặc stream, provider hỗ trợ PKCS12 | `KeyStore.getInstance("PKCS12")`; `load(InputStream, char[])` | Có file `.p12` không có nghĩa có HSM |
| `JKS` | Định dạng keystore Java cũ | `KeyStore.getInstance("JKS")` | JKS không mặc định tốt hơn PKCS12 cho khóa sản xuất |
| `PKCS11` | Token native qua configured provider | `getInstance("PKCS11", hsm)`; `load(null, pin)` | PKCS#11 không mặc định là HSM vật lý |
| `Windows-MY` | Personal store Windows | `getInstance("Windows-MY")`; `load(null, null)` | Không bảo đảm private key hiện có, có quyền hoặc hardware-backed |
| `Windows-ROOT` | Trust roots Windows | `getInstance("Windows-ROOT")`; `load(null, null)` | Trust anchors không phải private signing key |
| `KeychainStore` | macOS Keychain qua Apple provider | `getInstance("KeychainStore")`; `load(null, null)` | Không bảo đảm Secure Enclave hoặc khả năng dùng mọi key type |

Với `PKCS12`, dữ liệu serialized có thể được ghi ra file; với `PKCS11` hoặc OS store, `KeyStore.load(null, ...)` thường có nghĩa **kết nối kho đã tồn tại**, không tạo file mới. Phương thức `store(OutputStream, char[])`, `setEntry`, `deleteEntry` và khả năng export/private key có khác biệt lớn tùy provider; phải kiểm thử thay vì áp đặt cách dùng file keystore lên token.

Một hệ thống TLS có thể dùng `KeyManagerFactory` với kho private key của service và `TrustManagerFactory` với kho trust anchors khác. **Keystore chứa danh tính** và **truststore dùng đánh giá peer** là hai vai trò logic; cả hai dùng API `KeyStore` nhưng không có nghĩa nên dùng cùng nguồn dữ liệu hoặc cùng ACL.

## 8. Lựa chọn theo tình huống thực tế

### Tình huống A: ứng dụng nội bộ, mã hóa dữ liệu lớn

Nhu cầu: mã hóa lưu trữ dung lượng lớn, throughput cao; quy định bảo vệ khóa ở mức thông thường. Phương án khả thi là AES-GCM từ provider phần mềm và một secret manager/`PKCS12` được bảo vệ phù hợp để lưu hoặc cấp khóa. Chú ý quản lý nonce/IV AES-GCM và rotation khóa; chỉ lựa chọn “provider nhanh” không bảo đảm bảo mật ứng dụng.

Nếu yêu cầu khóa gốc không xuất khỏi HSM, cân nhắc mô hình envelope: HSM/KMS quản lý key-encryption key, ứng dụng chỉ xử lý data-encryption key có tuổi thọ ngắn. Mô hình phải thừa nhận data key vẫn có thể hiện diện trong RAM ứng dụng; tính bảo mật phụ thuộc cách cấp/giải mã và thời gian sống của nó.

### Tình huống B: hệ thống ký văn bản/CA có yêu cầu audit và chống trích xuất

Dùng khóa ký được tạo trong HSM/token có chính sách non-extractable, quyền `sign` theo role, approval và audit. Java có thể dùng `SunPKCS11` nếu vendor hỗ trợ. Kiểm tra cơ chế ký (RSA/ECDSA/PSS), key usage, certificate chain, key version, throughput và quy trình khôi phục sau sự cố.

Nếu thiết bị hỗ trợ ký nhưng không hỗ trợ một dạng padding/algorithm cụ thể, đừng tự thay đổi sang thuật toán yếu hay silently chuyển sang software. Thay đổi cơ chế ký cần thiết kế và phê duyệt tương thích của các bên xác minh.

### Tình huống C: ứng dụng desktop / mTLS trên máy Windows doanh nghiệp

Nếu Windows đã provision client certificate và private key cho đúng user/machine, `Windows-MY` và `SunMSCAPI` có thể giảm nhu cầu tự phân phối file `.p12`. Phải kiểm tra service identity, alias, EKU/key usage, chain, private key ACL và khả năng JSSE dùng khóa native.

Ứng dụng chạy bằng Windows Service hoặc trong container có thể không thấy kho `CURRENTUSER` của người đã đăng nhập. Di chuyển app sang Linux đòi hỏi một phương án cấp danh tính khác; copy code Java không tự chuyển cả private key và ACL.

### Tình huống D: công cụ macOS có khóa/chứng chỉ được cấp cho user

Dùng `KeychainStore` khi cần tích hợp lifecycle và access control của macOS. Nếu yêu cầu bắt buộc Secure Enclave, phải chứng minh được khóa được tạo và dùng với hardware-backed API phù hợp; không suy ra chỉ từ `KeychainStore`. Headless CI trên macOS có thể gặp vấn đề keychain chưa unlock hoặc cần tương tác.

### Tình huống E: microservice triển khai nhiều OS/container

Provider phần mềm với secret manager thường thuận lợi nhất cho tính di động. HSM qua PKCS#11 yêu cầu kiến trúc JVM + native vendor library đúng OS/CPU, thiết bị/daemon/network, session management và phương án HA. HSM có thể hợp lý khi có yêu cầu khóa ký bất biến hay non-extractable; hãy kiểm tra hiệu năng và khả năng failover trước khi dùng cho đường xử lý tần suất cao.

## 9. Mô hình ra quyết định

Trước khi chọn, trả lời các câu hỏi theo thứ tự:

1. **Đe dọa cần chặn là gì?** Mất file backup, kẻ có quyền đọc disk, dump RAM, kẻ chiếm quyền máy chủ, hay người có quyền gọi API ký trái phép? Các lựa chọn ngăn những loại đe dọa khác nhau.
2. **Private key có được phép xuất không?** Nếu **không**, phải có key generation/policy thực thi non-extractable và bằng chứng của backend. Nếu **có**, yêu cầu bảo vệ khi lưu và di chuyển.
3. **Thuật toán và thao tác cần dùng là gì?** Phải khớp cơ chế thực tế: RSA, EC/P-256, EdDSA, PSS, AES-GCM, unwrap, certificate operation, import/export.
4. **Ứng dụng chạy ở đâu?** Desktop, Windows Service, Linux container, Kubernetes, macOS, cloud; có interactive login không, có driver/slot/network không?
5. **Tần suất/độ trễ/độ sẵn sàng?** HSM có session limits/latency; OS provider chịu ACL; phần mềm tăng linh hoạt nhưng có rủi ro lộ key material.
6. **Vòng đời?** Ai tạo, cấp quyền, rotate, thu hồi, backup, phục hồi, decommission, cập nhật chứng chỉ và giữ audit? Có test disaster recovery định kỳ không?
7. **Khả năng kiểm toán?** Cần hardware attestation/chứng chỉ đánh giá, evidence của key policy và log theo key ID hay chỉ cần bảo vệ theo OS và bí mật ứng dụng?

**Quy tắc thực dụng:** chọn nơi giữ khóa và ranh giới tin cậy **trước**, sau đó chọn provider/keystore tương thích để Java truy cập. API `KeyStore` giống nhau không làm cho mô hình bảo vệ khóa giống nhau.

## 10. Lỗi phổ biến và cách kiểm tra

| Triệu chứng | Kiểm tra trước | Cách xử lý hợp lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` / `NoSuchProviderException` | Provider hiện hữu, module JDK, thuật toán được đăng ký | Kiểm tra `Security.getProviders()`, `Provider.getServices()`; dùng JDK/vendor hỗ trợ |
| `KeyStoreException: PKCS11 not found` | `SunPKCS11` đã configure chưa, đã chỉ định đúng provider chưa | Dùng provider đã cấu hình; kiểm tra module `jdk.crypto.cryptoki` |
| Không tải được native PKCS#11 library | Đường dẫn/bitness JVM, dependency native, driver/daemon, permission | Cài thư viện đúng architecture, kiểm tra vendor SDK và log ở môi trường an toàn |
| Token not present / slot not found | `slot` so với `slotListIndex`, token chưa gắn, token partition | Kiểm tra cấu hình slot và trạng thái token, không thử PIN ngẫu nhiên |
| PIN/login error, hết session | PIN/callback policy, locked token, session pooling | Xem trạng thái quản trị, giới hạn retry và lifecycle login |
| `InvalidKeyException` khi ký | Engine của provider khác backend khóa hoặc mechanism không hỗ trợ | Xác nhận provider sau `initSign`, loại key handle, chọn cơ chế/engine phù hợp |
| `getEncoded() == null` | Khóa có thể không export được | Đừng ép xuất bytes; dùng key handle với service tương thích, xác minh policy riêng |
| Windows-MY không thấy alias | User/machine store khác nhau, account chạy service, permission | Kiểm tra identity và container/ACL thực tế, không đoán alias |
| KeychainStore không truy cập được | Keychain locked, permission/UI prompt, headless process | Kiểm tra quyền và cách unlock/access phù hợp chính sách OS |
| Đổi sang HSM thì latency tăng | Token session, network, per-operation overhead, mechanism | Benchmark, pooling được quản trị, envelope model nếu phù hợp |
| Rotate khóa làm xác minh thất bại | Cert chain, key ID/version, verifier chỉ giữ key mới | Thiết kế giai đoạn overlap, hỗ trợ xác minh bản cũ theo vòng đời |

Khi debug SunPKCS11, tùy phiên bản JDK có các tùy chọn `-Djava.security.debug=sunpkcs11` hoặc `-Djava.security.debug=pkcs11keystore`; chỉ dùng trong môi trường kiểm soát vì log chi tiết có thể tiết lộ metadata cấu hình, alias, path hoặc trạng thái nhạy cảm. Không in ra PIN/private key hay token bí mật.

## 11. Migration và kiểm thử tích hợp

### 11.1. Từ PKCS12 phần mềm sang HSM

Một lộ trình hợp lý: kiểm kê key/certificate và dependency → quyết định key có được phép nhập hay cần **sinh mới trong HSM** → provision role/slot/cơ chế → tạo HSM key/certificate mới nếu cần → cấu hình `SunPKCS11` và alias → kiểm thử ký/xác minh với verifier hiện tại → triển khai có khả năng kiểm tra key ID/version → rotate theo lịch và thu hồi key cũ.

Không thể mặc định lấy một khóa **non-extractable** từ HSM cũ rồi export sang HSM mới hoặc `PKCS12`. Chuyển đổi thường cần cơ chế secure backup/replication được vendor hỗ trợ, key wrapping trong phạm vi được chính sách cho phép, hoặc chuyển sang cặp khóa mới. Không xóa key cũ khi dữ liệu chữ ký lịch sử hoặc certificate chain vẫn còn cần xác minh.

### 11.2. Bộ test khuyến nghị

- **API contract:** thuật toán/parameter, provider name sau init, keystore type/alias, key usage, certificate chain, mục tiêu trust.
- **Khả dụng:** token vắng, HSM timeout, reboot, failover, session exhaustion, PIN hết hạn/khóa; ứng dụng fail-closed với khóa bắt buộc phải ở hardware.
- **Quyền:** chạy đúng service account, thử tài khoản không có quyền, thử operation bị cấm, kiểm tra audit. Không thử brute-force PIN.
- **Bảo mật khóa:** key được tạo đúng backend, policy non-extractable được chứng thực, không xuất private key ra log/bộ nhớ ngoài phạm vi thiết kế.
- **Hiệu năng:** P50/P95/P99 ký/giải mã, throughput, connection/session pool, độ trễ mạng, startup.
- **Chuyển đổi:** rotation, overlap trust, rollback phiên bản phần mềm **không** rollback key policy; backup và diễn tập khôi phục có kiểm soát.

Provider PKCS#11 giả lập bằng token phần mềm có ích cho integration test Java, nhưng không thay thế kiểm thử cuối trên đúng thiết bị HSM và chứng minh thuộc tính bảo vệ phần cứng.

## 12. Những kết luận sai cần tránh

1. **“`KeyStore.getInstance("PKCS12")` nghĩa là khóa đang an toàn trong HSM.”** Sai: đây là kho dữ liệu theo định dạng PKCS12, có thể chứa key material dạng xuất được và tồn tại trên filesystem.
2. **“Provider `SunPKCS11` luôn là HSM.”** Sai: SunPKCS11 kết nối một PKCS#11 library; library có thể dẫn tới smart card, HSM hoặc token phần mềm.
3. **“`Windows-MY` và `KeychainStore` luôn giữ khóa trong TPM/Secure Enclave.”** Sai: backend/khóa cụ thể và chính sách OS mới quyết định nguồn bảo vệ.
4. **“Private key không export được thì không thể bị lạm dụng.”** Sai: người có quyền truy cập thao tác ký/giải mã có thể lạm dụng dịch vụ mà không cần trích xuất key bytes.
5. **“Mọi provider đều hỗ trợ cùng thuật toán và mọi loại `PrivateKey`.”** Sai: algorithm, mechanism, padding, key type và key usage là giới hạn thực tế.
6. **“Chỉ cần copy file sang Linux là mang theo được khóa Windows-MY.”** Sai: OS store có key container và ACL riêng; phải provision hoặc chuyển khóa theo quy trình được phép.
7. **“HSM luôn nhanh hơn vì có phần cứng.”** Sai: overhead của driver, session và network có thể khiến tác vụ nhỏ chậm hơn software; benchmark theo workload.
8. **“Lưu khóa trên HSM là xong toàn bộ bảo mật TLS và chữ ký.”** Sai: chain/cert, policy ký, quyền truy cập ứng dụng, availability, audit và xử lý dữ liệu vẫn cần thiết.

## 13. Câu hỏi tự kiểm tra

1. Một file `service.p12` có mật khẩu mạnh giúp chống những rủi ro gì, và tại sao vẫn khác private key non-extractable trên HSM?
2. Nếu `KeyStore.getInstance("PKCS11", hsm).load(null, pin)` chạy thành công, bạn còn phải kiểm tra điều gì trước khi tuyên bố hệ thống đáp ứng yêu cầu “hardware-backed”?
3. Vì sao `Signature.getInstance("SHA256withRSA", "SunRsaSign")` có thể không dùng được với private key được trả từ HSM?
4. Khác biệt giữa `Windows-MY`, `Windows-ROOT` và hai biến thể `LOCALMACHINE` là gì? Service account ảnh hưởng kết quả ra sao?
5. Vì sao `KeychainStore` không đồng nghĩa Secure Enclave?
6. Khi nào nên mã hóa dữ liệu hàng GB bằng AES-GCM trong ứng dụng và chỉ để HSM xử lý key-encryption key?
7. Chuyển non-extractable key từ một HSM sang nền tảng khác có thể phải thay đổi quy trình chứng chỉ/chữ ký thế nào?
8. Bạn sẽ thiết kế test fail-closed như thế nào để ứng dụng không ký bằng khóa phần mềm khi HSM ngừng hoạt động?

## Tài liệu tham khảo chính thức

- [Java SE 17 — Security Developer's Guide](https://docs.oracle.com/en/java/javase/17/security/).
- [JDK 17 Providers — SUN, SunJCE, SunRsaSign, SunMSCAPI, Apple, SunPKCS11](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html).
- [JDK 17 — PKCS#11 Reference Guide](https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html).
- [Java SE 17 — Provider API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.html), [Security API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html), [KeyStore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html), [Signature API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html).

**Ghi nhớ:** Chọn provider phải bắt đầu bằng **yêu cầu bảo vệ khóa và ranh giới tin cậy**. Software provider ưu tiên linh hoạt/hiệu năng; HSM cung cấp policy khóa và isolation khi được triển khai đúng; kho OS tích hợp với user/machine và lifecycle thiết bị. Các ứng dụng Java có thể dùng cả ba, miễn là nguồn khóa, quyền truy cập và nơi thực hiện từng phép toán được kiểm chứng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-providers-008">20.8 Phân biệt có hỗ trợ thuật toán với đáp ứng yêu cầu tuân thủ của môi trường</a>

<details>
<summary>Xem chi tiết</summary>

# 20.8. Phân biệt hỗ trợ thuật toán với đáp ứng yêu cầu tuân thủ của môi trường

## 1. Mục tiêu và tình huống mở đầu

Giả sử ứng dụng Java gọi thành công `Cipher.getInstance("AES/GCM/NoPadding")`, mã hóa rồi giải mã ra đúng dữ liệu ban đầu. Nhóm phát triển kết luận: “Hệ thống đã đạt yêu cầu FIPS/tuân thủ vì AES-GCM là thuật toán được chấp thuận và máy có HSM”. **Kết luận đó chưa có đủ bằng chứng.** Câu lệnh Java chỉ cho thấy một triển khai có khả năng cung cấp phép biến đổi được yêu cầu trong môi trường hiện tại; nó không cho biết toàn bộ quá trình mật mã đã chạy bên trong module được xác thực, ở chế độ được phép, với khóa và tham số đúng chính sách.

Bài này tập trung vào ranh giới của **JCA/JCE provider, token/HSM và tuân thủ khi triển khai**. Sau bài học, bạn có thể:

1. Phân biệt **algorithm availability**, **runtime functionality**, **cryptographic validation** và **environment compliance**.
2. Xác định provider thật sự phục vụ từng thao tác `Cipher`, `Signature`, `KeyGenerator`, `SecureRandom` hoặc `KeyStore`; hiểu tác động của thứ tự provider, chọn ngầm và chọn tường minh.
3. Giải thích vì sao provider hoặc phần cứng “hỗ trợ AES/RSA/ECDSA”, “có PKCS#11” hay “có chứng thư FIPS” vẫn chưa đủ để khẳng định ứng dụng đáp ứng chính sách.
4. Thiết kế kiểm tra và bộ bằng chứng triển khai để kết luận **đạt / không đạt / chưa đủ bằng chứng** đối với một yêu cầu cụ thể, tránh gắn nhãn “compliant” chỉ nhờ một bài smoke test.

**Phạm vi:** lập luận kỹ thuật và bằng chứng về mật mã trong Java; mọi chuẩn/pháp luật/phạm vi kiểm toán cụ thể phải do chủ sở hữu chính sách và bộ phận phụ trách tuân thủ xác định. “Tuân thủ” không có nghĩa chung chung, tự động áp dụng cho mọi tổ chức.

## 2. Bốn câu hỏi phải tách riêng

| Tầng kiểm tra | Câu hỏi đúng | Bằng chứng phù hợp | Điều **không** suy ra được |
| --- | --- | --- | --- |
| **A. Hỗ trợ (availability)** | JVM/provider có công bố hoặc cung cấp thuật toán/transformation? | `Security.getProviders()`, `Provider.getServices()`, `Cipher.getInstance(...)`, `Signature.getInstance(...)` | Thuật toán đã chạy được với đúng khóa, tham số, token hoặc policy. |
| **B. Hoạt động (functionality)** | Phép toán thực tế có `init`, `update`, `doFinal` / `sign` / `verify` thành công ở cấu hình đang chạy? | Bài thử tích hợp, log provider *sau khi khởi tạo*, kết quả positive/negative tests. | Implementation là module mật mã đã được xác thực hoặc mọi chế độ sử dụng đều được phép. |
| **C. Xác thực module/thuật toán (validation)** | Triển khai mật mã **chính xác** và cấu hình/mode của nó có thuộc phạm vi chứng nhận cần thiết? | Hồ sơ chứng nhận, module version, firmware/software build, Security Policy, chế độ approved, danh sách dịch vụ/thuật toán. | Cấu hình ứng dụng, khóa, audit, quy trình và toàn hệ thống đã đạt chính sách. |
| **D. Tuân thủ môi trường (compliance)** | **Chính sách cụ thể** của hệ thống áp dụng và được chứng minh từ đầu đến cuối? | Ma trận yêu cầu–bằng chứng, vận hành, quản lý khóa, phân quyền, log/audit, tài liệu kiểm toán. | Một chứng thư provider/HSM có thể thay toàn bộ nghĩa vụ bảo mật khác. |

Quy tắc suy luận an toàn:

```text
"Provider có AES-GCM"
    không suy ra "hoạt động với khóa/parameter/token này".
"Round-trip AES-GCM thành công"
    không suy ra "chạy trong approved cryptographic module".
"Module đã được chứng nhận"
    không suy ra "ứng dụng đang gọi đúng module, đúng version và approved mode".
"Đúng module + đúng mode"
    vẫn chưa suy ra "toàn bộ môi trường đáp ứng mọi yêu cầu".
```

Không đánh đồng ba từ sau:

- **Approved algorithm**: thuật toán/chế độ sử dụng được cho phép *theo chuẩn và bối cảnh nhất định*, với yêu cầu về key size, nonce, padding, tag length và giới hạn sử dụng.
- **Validated cryptographic module**: đối tượng được kiểm định trong một ranh giới bảo mật và cấu hình/phiên bản cụ thể (phần mềm, firmware, phần cứng hoặc kết hợp); chứng nhận module không tự động mở rộng cho mọi ứng dụng sử dụng nó.
- **Compliant deployment**: việc dùng thuật toán và module trong **môi trường thực tế** đáp ứng tất cả quy định áp dụng, kể cả các kiểm soát ngoài phần mật mã.

## 3. Provider trong Java là lớp định tuyến API, không phải giấy chứng nhận

JCA/JCE tách API (như `MessageDigest`, `Signature`, `Cipher`, `KeyGenerator`, `KeyStore`, `SecureRandom`) khỏi triển khai thực tế thông qua `java.security.Provider` và các SPI tương ứng.

Ví dụ:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
```

Đây là **mảnh mã minh họa, không phải chương trình độc lập**. Khi không chỉ định provider, JCA/JCE tìm triển khai phù hợp trong danh sách provider đã đăng ký, chịu ảnh hưởng của thứ tự ưu tiên và cơ chế lựa chọn của JDK. Việc chọn provider có thể được trì hoãn tới khi biết loại khóa hoặc khi khởi tạo; bởi vậy nên ghi nhận `cipher.getProvider()` **sau `init(...)`** để biết provider phục vụ thao tác quan sát được. Các thao tác khác có thể dùng provider khác.

Ngược lại, phiên bản có provider cụ thể:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", selectedProvider);
```

Cũng chỉ là **mảnh mã minh họa**: `selectedProvider` phải được lấy từ cấu hình đã kiểm chứng của môi trường. Nếu provider này không hỗ trợ transformation/khóa/tham số yêu cầu, nên để thao tác **thất bại rõ ràng**, tránh âm thầm chuyển sang một software provider trái chính sách. Khóa do một provider tạo ra không nhất thiết dùng được trong provider khác; đặc biệt với khóa trong HSM hoặc token không thể xuất.

Các phép kiểm tra và hạn chế:

| API/dấu hiệu | Điều thực sự biết được | Vì sao không đủ để kết luận tuân thủ |
| --- | --- | --- |
| `Security.getProviders()` | Provider đang được đăng ký và thứ tự chung | Provider có mặt không đảm bảo thao tác sẽ được định tuyến qua nó. |
| `Provider.getServices()` | Dịch vụ và thuật toán provider **khai báo** | Có thể còn cần mechanism, key type, token login, thư viện native và trạng thái thiết bị. |
| `Security.getProviders("Cipher.AES")` | Provider có đăng ký dịch vụ `Cipher`/`AES` | Không khẳng định hỗ trợ **cụ thể** `AES/GCM/NoPadding`, tag/IV/key size hoặc sử dụng approved mode. |
| `Cipher.getInstance("AES/GCM/NoPadding")` | Có thể tạo engine cho transformation từ một provider | `init`/`doFinal` vẫn có thể lỗi; chưa biết đây là provider được chấp nhận theo policy. |
| `Cipher.getProvider()` sau `init` | Triển khai chọn cho thao tác `Cipher` | Không chứng minh nguồn khóa, RNG, module version, trạng thái approved mode hay chứng thư. |
| `Cipher.getMaxAllowedKeyLength("AES")` | Giới hạn key length do JCE jurisdiction policy áp dụng | Không xác nhận HSM, FIPS, mức bảo vệ khóa hoặc cho phép của chính sách riêng. |
| Mã hóa/giải mã thành công | Mã và cấu hình thử nghiệm hoạt động đối với trường hợp đó | Không kiểm tra toàn bộ chế độ lỗi, chính sách vận hành, trust hoặc quy định. |

**Bẫy thường gặp:** `Provider.getService("Cipher", "AES/GCM/NoPadding")` trả `null` không nhất thiết có nghĩa `Cipher.getInstance("AES/GCM/NoPadding")` sẽ thất bại. Một số provider đăng ký dịch vụ `AES` kèm các mode/padding có thể xử lý. Hãy thử transformation đầy đủ với `Cipher.getInstance` và với đúng tham số/khóa, thay vì suy luận chỉ từ khóa tra cứu metadata.

### Khác nhau giữa các engine

- `Cipher`: có thể là phần mềm hoặc offload xuống HSM/token; transformation đúng phải bao gồm **algorithm/mode/padding**.
- `Signature`: ký/verify với key object cụ thể; cùng tên `SHA256withRSA` không cho biết private key nằm trong HSM hay RAM.
- `KeyGenerator` / `KeyPairGenerator`: nguồn phát sinh khóa và khả năng xuất khóa phụ thuộc provider/token. Tạo khóa bởi software rồi nhập vào HSM có thể không phù hợp chính sách “key must be generated inside validated boundary”.
- `SecureRandom`: provider RNG độc lập với provider của `Cipher`; ngẫu nhiên an toàn theo tiêu chí API vẫn chưa chứng minh RNG thuộc validated boundary hay đáp ứng policy.
- `KeyStore`: chọn `PKCS12`, `JKS`, `PKCS11` là chọn cơ chế lưu/định danh khóa, không mặc nhiên công bố trạng thái chứng nhận FIPS. `PKCS12` là định dạng keystore; `PKCS11` là giao diện token, không phải bản chứng nhận.

## 4. Bài thực hành Java 17+: kiểm tra khả năng nhưng **không** tự kết luận compliant

### 4.1. Chương trình độc lập

Tạo file `CapabilityVsCompliance.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CapabilityVsCompliance {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    public static void main(String[] args) throws Exception {
        // Demo chạy trên JDK thông thường: mỗi lần chạy tạo một khóa mới.
        SecureRandom random = new SecureRandom();
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128, random);
        SecretKey key = keyGenerator.generateKey();

        byte[] iv = new byte[12]; // 96-bit IV; chỉ một lần mã hóa với key này
        random.nextBytes(iv);
        byte[] aad = "message-type:v1".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "provider-check".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance(TRANSFORMATION);
        encrypt.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        encrypt.updateAAD(aad);
        byte[] encrypted = encrypt.doFinal(plain);

        // Để so sánh cùng implementation, pin Cipher giải mã vào provider vừa dùng.
        Cipher decrypt = Cipher.getInstance(TRANSFORMATION, encrypt.getProvider());
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        decrypt.updateAAD(aad);
        byte[] restored = decrypt.doFinal(encrypted);

        byte[] tampered = encrypted.clone();
        tampered[0] ^= 1;
        Cipher check = Cipher.getInstance(TRANSFORMATION, encrypt.getProvider());
        check.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        check.updateAAD(aad);
        boolean tamperingRejected = false;
        try {
            check.doFinal(tampered);
        } catch (AEADBadTagException expected) {
            tamperingRejected = true;
        }

        // Log chẩn đoán ra stderr: giá trị khác nhau giữa máy/JDK/provider.
        System.err.println("Cipher provider: " + encrypt.getProvider().getName());
        System.err.println("KeyGenerator provider: " + keyGenerator.getProvider().getName());
        System.err.println("SecureRandom provider: " + random.getProvider().getName());

        System.out.println("Round-trip verified: " + Arrays.equals(plain, restored));
        System.out.println("Tampering rejected: " + tamperingRejected);
        System.out.println("Compliance decision: UNDETERMINED");
    }
}
```

Biên dịch và chạy trong terminal có JDK 17+:

```shell
javac CapabilityVsCompliance.java
java CapabilityVsCompliance
```

Ba dòng **stdout** ổn định trên JDK thông thường có hỗ trợ AES-GCM:

```text
Round-trip verified: true
Tampering rejected: true
Compliance decision: UNDETERMINED
```

Ngoài ba dòng trên, chương trình ghi tên ba provider vào **stderr**; tên và thứ tự hiển thị với stdout phụ thuộc môi trường và terminal, nên không cố định trong ví dụ. Một JDK tùy biến có thể không cung cấp `AES/GCM/NoPadding` hoặc từ chối khóa/tham số: khi đó chương trình thất bại và phải kiểm tra cấu hình, không được sửa kết quả đầu ra để làm báo cáo “đạt”.

**Bạn vừa chứng minh điều gì?** Với đúng JVM và provider mà tiến trình đã chọn, một khóa AES 128 bit, IV 96 bit, authentication tag 128 bit và AAD phù hợp đã mã hóa/giải mã thành công; bản mã bị sửa đã bị từ chối. Bạn **chưa** chứng minh rằng ba thao tác provider chạy trong module cryptographic đã được xác thực, rằng khóa nằm trong HSM, rằng cấu hình đáp ứng một chứng thư hoặc rằng môi trường có đầy đủ kiểm soát vận hành.

**Lưu ý AES-GCM trong sản phẩm:** IV/nonce phải **không tái sử dụng với cùng key**; tag size, giới hạn tổng số bản mã mỗi key, nguồn IV, bảo vệ khóa, authentication failure và key rotation phải theo chính sách và tài liệu kỹ thuật. Việc demo tạo key mới mỗi lần và dùng một IV cho một message không phải hệ thống quản lý nonce hoàn chỉnh.

### 4.2. Cách mở rộng kiểm tra cho môi trường có ràng buộc

Khi chính sách yêu cầu provider/hardware cụ thể, đừng chỉ chạy chương trình trên và kết luận. Hãy đưa vào **bộ kiểm tra riêng có cấu hình triển khai**:

1. Đọc danh tính provider được cho phép và phiên bản hợp lệ từ cấu hình đã được duyệt; xác nhận provider thực tế sau `init` và các engine tham gia (RNG, tạo khóa, ký/mã hóa) là đúng.
2. Yêu cầu `getInstance(transformation, approvedProvider)` với provider đã được xác minh. Từ chối khởi tạo khi provider/token không khả dụng; không chủ động fallback sang provider khác nếu chính sách cấm.
3. Dùng **key handle từ token/HSM** khi yêu cầu khóa nằm trong module. Thử nghiệm với khóa phần mềm `SecretKeySpec` hoặc key do `SunJCE` tạo **không chứng minh** thao tác dùng đúng khóa có bảo vệ phần cứng.
4. Kiểm tra **tham số thật**: key size, mode, IV/tag length, padding, thuật toán ký/hash, nguồn random, lifecycle; kiểm thử cả trường hợp âm như sửa bản mã/tag hoặc dùng sai khóa.
5. Thu thập báo cáo trạng thái token/module/approved mode bằng giao diện chính thức của vendor và bằng chứng vận hành. `getProvider().getName()` **không phải** API chuẩn để đọc trạng thái FIPS của mọi module.

Không nên bật các thuật toán yếu chỉ để chứng minh hệ thống “có nhiều capability”. Trong môi trường có chính sách chặt, điều cần chứng minh nhiều khi là **các thao tác bị cấm không thể được sử dụng**, hoặc bị kiểm soát bằng policy, chứ không phải provider quảng cáo càng nhiều càng tốt.

## 5. Provider, PKCS#11 và HSM: nơi dễ tạo ra “đảm bảo giả”

### 5.1. SunPKCS11 chỉ là cầu nối tới thư viện native

`SunPKCS11` là provider dùng JCA/JCE để gọi API PKCS#11 trong thư viện/token được cấu hình. Nó **không tự thực hiện** toàn bộ mật mã dưới tên “FIPS”, và không tự mang theo một HSM đã được chứng nhận. Thiết bị thực tế có thể là smartcard, HSM, accelerator hoặc software PKCS#11; loại thiết bị và các cơ chế được hỗ trợ phụ thuộc cấu hình.

Một triển khai PKCS#11 yêu cầu tối thiểu:

- Thư viện native đúng phiên bản và hệ điều hành, slot/token phù hợp.
- Provider đã `configure`/đăng ký; quyền truy cập và đăng nhập token theo cơ chế phù hợp.
- Cơ chế PKCS#11 hỗ trợ thuật toán/mode/khóa đã yêu cầu.
- Key handle, quyền sở hữu khóa, policy export/wrap/generate, trạng thái token hoạt động.
- Đối chiếu **đúng module cryptographic và Security Policy của vendor** nếu có yêu cầu chứng nhận.

Việc cấu hình PKCS#11 hợp lệ **không tự đồng nghĩa** khóa đã được sinh trên token, mã hóa chắc chắn chạy trên token, hoặc module được sử dụng ở approved mode.

### 5.2. Ví dụ ba con đường “AES-GCM hoạt động”

| Con đường | Kết quả có thể thấy | Rủi ro suy luận |
| --- | --- | --- |
| JDK software provider xử lý AES-GCM | `getInstance`, `init`, `doFinal` và round-trip đều thành công | Có thể **không dùng** HSM, dù HSM đã được cắm vào máy. |
| `SunPKCS11` định tuyến tới token | Cipher hoạt động và log cho thấy provider PKCS#11 | Token/module/version/mode/khóa/thuật toán thực tế vẫn phải xác minh trong ranh giới đã được chứng nhận. |
| HSM có bản chứng nhận, nhưng ứng dụng dùng provider khác | HSM hiện diện, dịch vụ vẫn chạy bình thường | Chứng nhận của HSM **không bảo đảm** phép toán mà ứng dụng thực hiện thuộc phạm vi đó. |

Một API có thể chọn provider A cho `Cipher` và provider B cho `SecureRandom`/`KeyGenerator`. Nếu policy yêu cầu **toàn bộ các chức năng mật mã liên quan** ở trong validated boundary, phải chứng minh cả luồng khóa, nguồn random, việc sinh/cấp IV, ký/mã hóa và các dịch vụ liên quan, theo chính sách cụ thể. Không thể dùng riêng một log `Cipher provider` để kết luận cho tất cả.

**Không xuất khóa** là yêu cầu khác với “không thấy byte khóa”. Ví dụ `PrivateKey.getEncoded() == null` có thể là tín hiệu khóa opaque/non-exportable tại một provider, nhưng không tự chứng minh HSM, chứng nhận module, policy chống key wrapping hoặc nguồn sinh khóa. Phải đối chiếu thuộc tính/key policy của token và bằng chứng từ thiết bị.

## 6. Ví dụ FIPS 140: câu hỏi về **module** và chế độ vận hành

Khi một tổ chức yêu cầu mật mã qua module phù hợp **FIPS 140-3** (hoặc FIPS 140-2 theo phạm vi chuyển tiếp được chính sách áp dụng), cần phân biệt:

- **Thuật toán được chấp thuận:** AES, SHA-2, RSA/ECDSA có thể có các ứng dụng/mode/parameter phù hợp chuẩn. Chỉ nhìn tên “AES”, “RSA” hoặc “SHA-256” là chưa đủ; nhiều cách dùng hoặc tham số cụ thể bị hạn chế.
- **Algorithm validation:** các kết quả kiểm định thuật toán theo chương trình ACVP/CAVP được dùng làm thành phần bằng chứng; **một chứng nhận thuật toán đơn lẻ không tương đương chứng nhận module FIPS 140**.
- **Module validation (CMVP):** chứng nhận gắn với module/phạm vi và phiên bản được liệt kê, Security Policy và các điều kiện áp dụng. Có thể cần phân biệt bản ghi Active/Historical/Revoked hoặc caveat và quy định chuyển tiếp tại thời điểm kiểm tra.
- **Approved mode of operation:** một module có thể cung cấp cả chế độ approved và non-approved hoặc yêu cầu cài đặt, cấu hình, khởi động/self-test và thao tác cụ thể để sử dụng chế độ approved. Đọc Security Policy và hướng dẫn chính thức của vendor.
- **Operational context:** chứng nhận một thành phần không tự kiểm định cả ứng dụng Java, hệ điều hành, giao thức, người vận hành hoặc đường đi dữ liệu.

Ví dụ: tổ chức yêu cầu khóa ký phải được tạo và sử dụng trong module FIPS đã xác thực, ở approved mode. Provider A hỗ trợ `Signature.getInstance("SHA256withRSA")` nhưng khóa riêng ở file PKCS12 trên ổ đĩa. Provider B gọi HSM có chứng nhận nhưng đang ở cấu hình không được Security Policy công nhận. **Cả hai đều có thể tạo chữ ký RSA hợp lệ về mặt toán học; cả hai vẫn chưa chứng minh đạt yêu cầu nêu trên**. Ngược lại, nếu module B đúng phiên bản, đúng approved mode và private key được tạo/sử dụng đúng ranh giới, phần còn lại vẫn cần bằng chứng về policy thuật toán, cấp quyền, audit và quản trị khóa.

### Bằng chứng tối thiểu khi nói “FIPS validated module”

Phải trả lời được những câu hỏi cụ thể sau:

1. **Chứng nhận nào?** Số certificate trong danh mục CMVP, vendor, tên module chính xác, Security Policy của module đó; không dùng riêng chứng chỉ marketing của sản phẩm.
2. **Phiên bản nào?** Phiên bản phần cứng, firmware, software, module build và platform/environment được liệt kê hoặc được phép theo certificate/caveat.
3. **Chế độ nào?** Module có ở approved mode hay không; lệnh/config/điều kiện nào để bật và nhận biết; có self-test/health status theo hướng dẫn không?
4. **Dịch vụ nào?** Thuật toán, mode, key size, random/key generation, service và tham số cụ thể có nằm trong danh mục được phép của module và chính sách triển khai?
5. **Đường đi thực tế nào?** Tiến trình Java gọi provider nào, provider gọi thư viện/token nào, key handle ở đâu; có đường vòng software hoặc fallback không?
6. **Ai vận hành?** Quyền quản trị, khởi tạo, rotation, backup, recovery, revocation, audit, monitoring và kiểm soát thay đổi có đúng yêu cầu không?

Cần xác nhận tình trạng chứng nhận hiện hành trong **NIST CMVP validated modules** và caveat/Security Policy. Phạm vi được chứng nhận **không tự lan** từ một model sản phẩm sang mọi phiên bản sản phẩm hoặc cloud service dùng cùng thương hiệu.

## 7. Ma trận quyết định với ba tình huống thực tế

### Tình huống A — Máy có HSM, Java dùng software provider

**Đầu vào:** policy quy định mã hóa khóa dữ liệu trong module FIPS được chấp nhận; HSM đã lắp và nhận diện. `Cipher.getInstance("AES/GCM/NoPadding")` không chỉ định provider, round-trip thành công, `encrypt.getProvider().getName()` ghi một software provider.

**Kết luận:** **chưa đáp ứng hoặc chưa chứng minh đáp ứng** yêu cầu dùng module đã duyệt, tùy định nghĩa scope của policy. HSM có mặt không đủ. Phải xác nhận định tuyến tới provider/token yêu cầu và chứng minh toàn bộ đường đi khóa, trạng thái module và approved mode.

### Tình huống B — Java chọn đúng token, nhưng phiên bản/mode sai

**Đầu vào:** ứng dụng ghi log đúng provider kết nối PKCS#11; lệnh ký thành công. Hồ sơ vận hành cho thấy firmware phiên bản khác certificate hoặc token chưa bật approved mode theo Security Policy.

**Kết luận:** **không thể công nhận đạt bằng chứng FIPS từ chứng thư đang viện dẫn**; cần xác định cấu hình nằm ngoài/thuộc phạm vi chứng nhận, sửa cấu hình và thu thập chứng cứ từ vendor và danh mục CMVP. Không sửa bằng cách đổi tên provider trong log.

### Tình huống C — Module đúng nhưng sử dụng tham số hoặc quy trình sai

**Đầu vào:** module validated và approved mode đã được chứng minh. Ứng dụng tái sử dụng IV AES-GCM với cùng key, dùng key do nơi khác sinh trái policy hoặc bỏ kiểm soát cấp quyền/audit.

**Kết luận:** **không đáp ứng** yêu cầu mật mã/tuân thủ tương ứng dù thao tác API thành công. Phải sửa cách dùng thuật toán hoặc quy trình; giấy chứng nhận module không thay thế các ràng buộc sử dụng.

### Bảng chọn kết luận

| Trạng thái bằng chứng | Kết luận báo cáo | Hành động |
| --- | --- | --- |
| Chỉ có tên thuật toán/provider, hoặc round-trip thành công | **CHƯA ĐỦ BẰNG CHỨNG** | Lập ma trận policy và xác minh triển khai thực tế. |
| Có bằng chứng sai provider/không đúng mode/vi phạm tham số bị cấm | **KHÔNG ĐẠT** | Dừng hoạt động có điều kiện, sửa thiết kế/cấu hình, kiểm tra lại. |
| Có certificate module nhưng thiếu version/approved-mode/runtime audit | **CHƯA ĐỦ BẰNG CHỨNG** | Bổ sung hồ sơ từ CMVP, vendor và hệ thống. |
| Mọi yêu cầu đã ánh xạ tới bằng chứng đúng thời điểm và đúng scope | **ĐẠT TRONG PHẠM VI ĐÁNH GIÁ** | Lưu trạng thái, điều kiện hiệu lực, ngày đánh giá và quy trình kiểm soát thay đổi. |

Không dùng “đạt FIPS”, “đạt PCI” hoặc “đạt compliance” nếu chưa chỉ rõ **yêu cầu nào**, **phiên bản nào**, **phạm vi nào**, **thời điểm nào** và **ai phê duyệt**. FIPS 140 nói về tính phù hợp của *cryptographic module*; những quy định về ngành, dữ liệu, hợp đồng và vận hành thường có phạm vi rộng hơn.

## 8. Cách xây dựng bộ kiểm tra tuân thủ theo yêu cầu

### 8.1. Viết yêu cầu thành mệnh đề có thể kiểm chứng

Thay vì câu chung “ứng dụng phải hỗ trợ crypto mạnh”, viết:

- “Dịch vụ ký số dùng **provider/token đã duyệt**; private key được tạo và giữ trong module đáp ứng chính sách, không thể xuất ra dưới dạng plaintext”.
- “Phép ký dùng thuật toán/mode/key size và certificate profile được cho phép trong phạm vi module và ứng dụng”.
- “Nếu HSM/token offline hoặc phiên bản không đạt, luồng ký phải **fail closed**, không tự dùng software fallback”.
- “Bản ghi phiên bản, cấu hình, trạng thái approved mode, identity key, hoạt động ký và cảnh báo được thu thập theo yêu cầu audit”.
- “AES-GCM dùng chính sách sinh/duy trì IV duy nhất trên mỗi key, xác thực đầy đủ ciphertext/tag, kiểm soát quyền dùng key và rotate theo lịch/giới hạn”.

Các giá trị cụ thể (ví dụ key size tối thiểu, approved algorithms, kỳ rotation, thời gian giữ log) phải được lấy từ **chính sách áp dụng** thay vì tự nhận mọi hệ thống giống nhau.

### 8.2. Ánh xạ yêu cầu sang nơi xác minh

| Yêu cầu | Kiểm tra bằng Java/JCA | Kiểm tra ngoài ứng dụng |
| --- | --- | --- |
| Đúng provider thực sự | Provider của engine **sau `init`**; pin provider khi bắt buộc; quan sát lỗi khi provider vắng | Đối chiếu config deployment, trình tự provider, thư viện native, token/slot. |
| Thuật toán và parameters được phép | `getAlgorithm`, `GCMParameterSpec`, type/key length, trường hợp âm và kiểm thử tích hợp | Module Security Policy; bảng thuật toán approved; giới hạn protocol/policy. |
| Khóa nằm trong boundary phù hợp | Tạo/tải key handle qua provider/token; không vô tình tạo `SecretKeySpec` từ secret ngoài boundary | Attributes và lifecycle của HSM: generate/import/export/wrap/backup, quản trị key. |
| Module đúng chứng nhận | API không cung cấp bằng chứng chứng nhận phổ quát | Số CMVP certificate, version, caveat, approved mode, trạng thái thiết bị/vendor. |
| Thiếu token thì từ chối | Test cố ý làm token/provider unavailable ở môi trường an toàn | Giám sát, alert, khôi phục và đánh giá chính sách fallback. |
| Audit/kiểm soát thay đổi | Log điều kiện chẩn đoán không chứa secret; test truy vết sự kiện | Phân quyền, retention, bất biến log, firmware upgrades, review định kỳ. |

**Tách hai nhóm test:**

- **Functional tests:** JCA engine có khởi tạo/ký/mã hóa/giải mã, xử lý tamper, key types, IV/tag và lỗi đúng.
- **Compliance gates:** actual provider/module/firmware + policy/approved mode + key lifecycle + logs/caveats chứng minh từng yêu cầu. Functional pass **không** tự khiến compliance gate pass.

Đừng biến một log text đơn lẻ hoặc giá trị tên provider thành nguồn sự thật duy nhất. Khi có thể, kết hợp trạng thái thiết bị, thông tin điều khiển từ HSM/KMS/vendor và cấu hình thực tế để có bằng chứng độc lập. Tránh ghi key material, PIN, token bí mật hoặc dữ liệu người dùng vào log kiểm toán.

### 8.3. Bộ hồ sơ phù hợp để review và audit

Một bản ghi đánh giá hữu ích gồm:

1. **Scope:** dịch vụ/ứng dụng, môi trường (dev/test/prod), thời điểm đánh giá, dữ liệu và quy định áp dụng.
2. **Runtime:** Java vendor/version, runtime image/build hash, provider/version/order, thư viện PKCS#11, OS/firmware, token/slot và thông số triển khai có ảnh hưởng.
3. **Policy:** yêu cầu định lượng về thuật toán, mode, key size, trust, key storage/rotation, recovery, audit, vận hành và điều kiện thất bại.
4. **Module evidence:** CMVP certificate, Security Policy/caveats, module boundary, cấu hình approved mode, trạng thái/chứng cứ vận hành.
5. **Execution evidence:** provider được chọn của từng engine liên quan, kết quả positive/negative integration tests, thử nghiệm mất token, không có fallback ngoài chính sách.
6. **Disposition:** bảng đối chiếu yêu cầu → bằng chứng → `PASS`/`FAIL`/`UNKNOWN` → người phê duyệt, phiên bản và ngày hết hiệu lực cần kiểm tra lại.

Nếu firmware, JDK provider list, HSM config, provider library, crypto policy, key lifecycle hoặc hạ tầng cloud thay đổi, bằng chứng cũ có thể không còn đủ; cần đánh giá lại **đúng phần bị ảnh hưởng**. Tên class Java không đổi không bảo đảm phạm vi chứng nhận giữ nguyên.

## 9. Những kết luận sai hay gặp

| Nhận định sai | Sửa cách hiểu |
| --- | --- |
| “Có `AES` trong `getServices()` nên AES-GCM được hỗ trợ ở mọi điều kiện.” | Kiểm tra **full transformation**, key/params và token trong môi trường thật. |
| “`Cipher.getInstance` không ném exception nên sẵn sàng sản xuất.” | Chạy `init/doFinal`, negative tests và đánh giá chính sách, key lifecycle, logging. |
| “Dùng `Security.insertProviderAt(...)` ở ưu tiên số 1 nghĩa là FIPS compliant.” | Ưu tiên không bằng chứng nhận/mode; phải xác minh provider được **chọn thực** và module phía sau. |
| “Tên provider chứa `PKCS11` là đủ.” | `SunPKCS11` là bridge; phải xác minh thư viện/token/mechanism và ranh giới đã chứng nhận. |
| “HSM có giấy FIPS thì ứng dụng Java nào cũng được chứng nhận.” | Chứng nhận gắn module/phiên bản/phạm vi/approved mode; app phải thực sự dùng đúng. |
| “Chỉ cần danh mục thuật toán ACVP/CAVP.” | Algorithm validation **không thay** chứng nhận module và bằng chứng môi trường. |
| “`getEncoded()==null` chứng minh private key luôn an toàn trong HSM.” | Cần xác minh nguồn gốc, thuộc tính token, quyền wrap/export và đường đi key. |
| “AES-GCM round-trip pass, nonce dùng thế nào cũng được.” | Tái sử dụng nonce cùng key có thể gây lỗi bảo mật nghiêm trọng; phải có chính sách uniqueness và giới hạn. |
| “Mọi thứ trong app đều được provider phục vụ.” | RNG, tạo khóa, digest, signature và cipher có thể chọn các provider khác nhau. |
| “Chính sách FIPS giống toàn bộ PCI DSS/FedRAMP/quy định dữ liệu.” | Mỗi bộ yêu cầu có **scope riêng** và nhiều kiểm soát phi mật mã; chỉ kết luận trong phạm vi được kiểm toán. |
| “Không có lỗi runtime tức là policy đã đạt.” | Trạng thái đúng có thể là `UNKNOWN` cho đến khi đủ bằng chứng/duyệt. |

## 10. Bài tập tình huống và câu hỏi tự kiểm tra

**Bài tập 1 — Phân loại bằng chứng.** Bạn có ba thông tin: (a) `Security.getProviders("Cipher.AES")` trả một provider; (b) ứng dụng AES-GCM round-trip thành công; (c) có certificate CMVP nhưng chưa biết firmware/version trên máy. Xếp tầng A–D ở mục 2.

**Đáp án:** (a) chỉ chứng minh mức **availability** chung của AES, chưa xác nhận transformation; (b) thêm bằng chứng **functionality** cho môi trường thử; (c) là điểm bắt đầu của **validation evidence** nhưng chưa chứng minh đúng runtime/certificate scope. Chưa đủ để kết luận **environment compliance**.

**Bài tập 2 — Thiết kế gate.** Policy yêu cầu mọi private-key signing chạy trong HSM, fail closed nếu token mất kết nối. Nên test gì ngoài `Signature.getInstance("SHA256withRSA")`?

**Đáp án:** ghi nhận provider sau `initSign`, dùng key handle thật từ token, chứng minh signed operation được HSM thực hiện, xác minh certificate/module/mode, thử tắt token ở môi trường test để quan sát lỗi (không chuyển software), kiểm soát key generation/export và audit.

**Bài tập 3 — Đọc báo cáo.** Một report viết “AES 256, PKCS11, FIPS enabled, PASS”. Chưa có certificate ID, firmware, thông số AES-GCM hay source của khóa.

**Đáp án:** không đủ dữ liệu để xác minh; yêu cầu evidence cụ thể và chuyển trạng thái sang `UNKNOWN` hoặc `CHƯA ĐỦ BẰNG CHỨNG`. Đừng suy ra `FAIL` kỹ thuật nếu chưa có chứng cứ vi phạm, nhưng cũng không ghi `PASS`.

**Bài tập 4 — Câu hỏi nhanh:**

1. **`Cipher.getInstance` có luôn chọn provider đầu tiên trong `Security.getProviders()`?** Không hẳn; provider nào *hỗ trợ đúng dịch vụ/điều kiện* mới được chọn, JDK có quy tắc ưu tiên riêng và provider selection có thể trễ tới lúc `init`.
2. **Một provider không có HSM có mã hóa mạnh được không?** Có thể. “Mạnh về mật mã” và “đúng cryptographic module được yêu cầu” là hai mệnh đề khác nhau.
3. **Certificate module FIPS có đảm bảo AES-GCM không bị nonce reuse?** Không. Ứng dụng hoặc hệ thống phải tuân thủ giới hạn dùng algorithm trong phạm vi module.
4. **Tại sao cần ghi nhận `SecureRandom` và `KeyGenerator` riêng?** Vì JCA có thể dùng provider khác với `Cipher`, ảnh hưởng đến nguồn khóa và ranh giới được bảo vệ.
5. **Khi nào kết luận `PASS`?** Khi từng yêu cầu áp dụng được đối chiếu với bằng chứng hợp lệ, đủ scope, đúng phiên bản/chế độ và được người có thẩm quyền duyệt.
6. **Có thể dùng provider name để chứng minh approved mode không?** Không có API Java chuẩn chung cho mọi HSM/module làm việc đó; dùng cách xác minh được vendor và Security Policy công bố.

## 11. Checklist cuối bài

- [ ] Nêu được khác biệt availability → functionality → module validation → deployment compliance.
- [ ] Hiểu các lựa chọn provider không chỉ qua tên mà còn qua engine/key và cấu hình runtime.
- [ ] Chạy `CapabilityVsCompliance.java`, quan sát stdout và **stderr provider**; giải thích vì sao kết quả không phải giấy chứng nhận.
- [ ] Giải thích được vì sao SunPKCS11/HSM/chứng chỉ ACVP riêng lẻ không tự bảo đảm toàn hệ thống đạt FIPS 140.
- [ ] Biết tra certificate, Security Policy, version, caveat và approved mode khi policy yêu cầu validated module.
- [ ] Xây dựng được ma trận yêu cầu → bằng chứng → `PASS/FAIL/UNKNOWN`, có gate chống fallback, key misuse và thiếu audit.
- [ ] Không gắn nhãn compliance khi chưa đủ bằng chứng, hoặc khi phạm vi đánh giá chưa được xác định.

**Tài liệu chính thức để đối chiếu:**

- [Java Cryptography Architecture (JCA) Reference Guide, JDK 21](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html): kiến trúc provider, `getInstance`, thứ tự và lựa chọn triển khai.
- [Cipher API, JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html): ý nghĩa transformation đầy đủ, `getInstance` và provider.
- [PKCS#11 Reference Guide, JDK 21](https://docs.oracle.com/en/java/javase/21/security/pkcs11-reference-guide1.html): SunPKCS11 là cầu nối tới thư viện/token native, yêu cầu cấu hình và cơ chế hỗ trợ.
- [NIST CMVP — Validated Modules](https://csrc.nist.gov/projects/cryptographic-module-validation-program/validated-modules): danh sách chứng nhận module và phân biệt chứng nhận thuật toán với module.
- [NIST CMVP — Certificate Caveats](https://csrc.nist.gov/Projects/cryptographic-module-validation-program/validated-modules/caveats): điều kiện cài đặt, khởi tạo và approved mode liên quan trực tiếp đến hiệu lực của chứng nhận.

Luôn dùng tài liệu tương ứng **JDK, module, firmware, chính sách và thời điểm triển khai thực tế**, đặc biệt khi viện dẫn tình trạng chứng nhận.


</details>

- [Quay lại đầu trang](#back-to-top)
