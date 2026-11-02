<a id="back-to-top"></a>

# 2. Kiến trúc JCA

## Menu
- [2.1 Mô hình engine class, SPI và security provider](#jca-architecture-001)
- [2.2 Lấy dịch vụ qua getInstance và lựa chọn provider theo thứ tự ưu tiên](#jca-architecture-002)
- [2.3 Tra cứu tên thuật toán chuẩn, alias và phạm vi hỗ trợ của từng provider](#jca-architecture-003)
- [2.4 Phân biệt API bắt buộc của Java SE với thuật toán do một JDK cung cấp](#jca-architecture-004)
- [2.5 Khám phá Provider.Service và danh sách thuật toán đang có trong tiến trình](#jca-architecture-005)
- [2.6 AlgorithmParameterSpec, AlgorithmParameters và việc lưu tham số thuật toán](#jca-architecture-006)
- [2.7 Vòng đời khởi tạo, cập nhật, kết thúc và tái sử dụng đối tượng mật mã](#jca-architecture-007)
- [2.8 Xử lý NoSuchAlgorithmException, NoSuchProviderException và lỗi tham số](#jca-architecture-008)
- [2.9 Tính tương thích và trạng thái thay đổi khi dùng dịch vụ mật mã đồng thời](#jca-architecture-009)

## <a id="jca-architecture-001">2.1 Mô hình engine class, SPI và security provider</a>

<details>
<summary>Xem chi tiết</summary>

# 2.1 Mô hình engine class, SPI và security provider

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao ứng dụng Java gọi một API mật mã thống nhất nhưng vẫn sử dụng được nhiều cách triển khai khác nhau.
- Phân biệt chính xác **engine class** (API ứng dụng gọi), **Service Provider Interface – SPI** (hợp đồng triển khai) và **security provider** (đơn vị đăng ký/cung cấp dịch vụ).
- Theo dõi đường đi của lời gọi `getInstance()`, hiểu vai trò của `Provider.Service` và thứ tự ưu tiên provider.
- Đọc thông tin provider thực tế, xử lý lỗi tra cứu thuật toán và nhận ra các rủi ro khi cố định provider hoặc viết SPI.

## 1. Bức tranh tổng thể của JCA

**Java Cryptography Architecture (JCA)** là kiến trúc API và cơ chế chọn triển khai các dịch vụ bảo mật trong Java: hàm băm, chữ ký số, mã hóa, sinh khóa, quản lý khóa/chứng chỉ, số ngẫu nhiên bảo mật, v.v. Các API trải trên `java.security` và `javax.crypto`; tên **JCE** thường dùng cho nhóm API mật mã mở rộng nhưng cùng sử dụng kiến trúc provider của JCA.

JCA tách câu hỏi **“ứng dụng muốn làm gì?”** khỏi **“mã nào thực hiện thuật toán?”**. Ứng dụng chọn loại dịch vụ và thuật toán; framework tìm một provider có triển khai tương ứng, sau đó tạo engine để ứng dụng sử dụng.

```text
Ứng dụng Java
    |
    | MessageDigest.getInstance("SHA-256")
    v
Engine class / API: java.security.MessageDigest
    |
    | tra cứu (service type, algorithm)
    v
Danh sách security provider / Provider.Service
    |
    | chọn triển khai phù hợp
    v
Lớp provider triển khai MessageDigestSpi
    |
    | engineUpdate(), engineDigest(), engineReset(), ...
    v
Kết quả digest trả về qua API MessageDigest
```

Sơ đồ diễn tả luồng logic. Ở một số engine (đặc biệt loại cần khóa, như `Signature`/`Cipher`), việc quyết định triển khai cụ thể có thể được trì hoãn đến khi khởi tạo với khóa hoặc tham số; không nên giả định mọi `getInstance()` đều khóa cứng provider ngay tại thời điểm gọi.

Ba lợi ích chính:

1. **Độc lập triển khai:** mã ứng dụng không cần khởi tạo lớp triển khai mật mã của từng hãng.
2. **Khả năng mở rộng:** provider mới có thể đăng ký thêm thuật toán/triển khai mà không cần thay đổi API engine.
3. **Khả năng thay thế và tích hợp:** có thể chọn provider phần mềm, provider bên thứ ba, hoặc provider kết nối thiết bị/HSM khi môi trường hỗ trợ.

## 2. Ba vai trò không được nhầm lẫn

| Thành phần | Ai sử dụng/định nghĩa? | Trách nhiệm chính | Ví dụ |
| --- | --- | --- | --- |
| **Engine class** | Lập trình viên ứng dụng sử dụng | API công khai, giữ trạng thái sử dụng và điều phối lời gọi | `MessageDigest`, `Signature`, `Cipher` |
| **SPI class** | Tác giả provider triển khai | Hợp đồng các thao tác `engine...()` mà provider phải hiện thực | `MessageDigestSpi`, `SignatureSpi`, `CipherSpi` |
| **Security provider** | Đơn vị triển khai/cấu hình và runtime JCA | Đăng ký tên dịch vụ, thuật toán, alias, lớp triển khai và thông tin provider | Một đối tượng kế thừa `java.security.Provider` |
| **Provider.Service** | Framework JCA dùng để tra cứu | Mô tả một dịch vụ cụ thể: loại engine, thuật toán, implementation class, alias, thuộc tính | `("MessageDigest", "SHA-256")` |

**Engine class** là điểm vào ổn định. Ví dụ `MessageDigest.getInstance("SHA-256")` trả về đối tượng kiểu `MessageDigest`; code chỉ gọi `update()`, `digest()`, `reset()`, không cần biết provider cài thuật toán ở lớp nào.

**SPI** thường là lớp trừu tượng có hậu tố `Spi`. Provider viết lớp kế thừa rồi triển khai các phương thức `protected engine...`. Engine chuyển yêu cầu từ API ứng dụng đến các thao tác SPI tương ứng. Chẳng hạn:

- `MessageDigest.update(byte[])` → thao tác cập nhật trên `MessageDigestSpi`.
- `MessageDigest.digest()` → `engineDigest()` của triển khai.
- `Signature.initSign(privateKey)` → logic `engineInitSign(...)` của triển khai.
- `Cipher.init(...)` và `Cipher.doFinal(...)` → các thao tác khởi tạo/xử lý của `CipherSpi`.

Một số implementation có cấu trúc lớp kế thừa đặc thù; điều cần nắm là **hợp đồng engine/SPI và cơ chế provider**, không phải giả định mọi engine đều được hiện thực bằng đúng một kiểu wrapper nội bộ.

**Provider** là đối tượng `Provider` mô tả một tập dịch vụ. Nó không đồng nghĩa với một thuật toán, một engine instance, hay nhất thiết một thư viện đơn lẻ. Một provider có thể công bố nhiều dịch vụ và chỉ hỗ trợ một phần những thuật toán JCA định nghĩa.

## 3. Nhận diện engine và SPI tương ứng

| Loại dịch vụ | Engine API | Hợp đồng SPI | Ví dụ tên thuật toán/transformation |
| --- | --- | --- | --- |
| Hàm băm | `java.security.MessageDigest` | `MessageDigestSpi` | `SHA-256` |
| Chữ ký số | `java.security.Signature` | `SignatureSpi` | `SHA256withRSA` |
| Mã hóa/giải mã | `javax.crypto.Cipher` | `CipherSpi` | `AES/GCM/NoPadding` |
| Xác thực thông điệp | `javax.crypto.Mac` | `MacSpi` | `HmacSHA256` |
| Số ngẫu nhiên bảo mật | `java.security.SecureRandom` | `SecureRandomSpi` | Tùy thuật toán/provider |
| Kho khóa | `java.security.KeyStore` | `KeyStoreSpi` | `PKCS12`, `PKCS11` (tùy provider) |

Phân biệt hai chiều thông tin:

- **Service type** là loại engine, ví dụ `MessageDigest` hoặc `Cipher`.
- **Algorithm** là tên thuật toán trong service đó, ví dụ `SHA-256` hoặc `HmacSHA256`. Riêng `Cipher` thường yêu cầu **transformation** đầy đủ `algorithm/mode/padding`, ví dụ `AES/GCM/NoPadding`.

Do đó `SHA-256` không phải provider; `MessageDigest` không phải thuật toán; `SUN`/`SunJCE` trong một số JDK là tên provider, không phải tên loại engine. Danh sách và tên provider được cài đặt phụ thuộc JDK và môi trường.

## 4. `getInstance()` chọn provider ra sao?

Ba dạng factory thông dụng (minh họa với `MessageDigest`):

```java
MessageDigest automatic = MessageDigest.getInstance("SHA-256");

// Chỉ thành công khi provider có tên này đã được đăng ký trong JVM:
MessageDigest named = MessageDigest.getInstance("SHA-256", "SUN");

// Có thể truyền trực tiếp đối tượng Provider mà không đăng ký toàn cục:
MessageDigest explicit = MessageDigest.getInstance("SHA-256", providerObject);
```

Đoạn trên là **minh họa các overload**, không phải chương trình chạy độc lập: `providerObject` phải là biến `Provider` hợp lệ; tên `SUN` chỉ có ý nghĩa trên runtime thật sự cài provider đó.

Khi không chỉ định provider, framework tra cứu các provider đã đăng ký theo **thứ tự ưu tiên** và tìm dịch vụ phù hợp với loại engine + tên thuật toán. Đối với engine cần khóa/tham số, khả năng hỗ trợ khóa cũng có thể ảnh hưởng đến provider được chọn trong bước khởi tạo. Khi bạn truyền provider theo tên hay đối tượng, lựa chọn bị giới hạn vào provider được chỉ định; nếu không có dịch vụ phù hợp, lời gọi thất bại thay vì tự động chuyển sang provider khác.

`Provider.Service` ghi nhận thông tin giúp framework tạo triển khai:

- `getType()`: loại dịch vụ, chẳng hạn `MessageDigest`.
- `getAlgorithm()`: tên thuật toán chính.
- `getProvider()`: provider sở hữu service.
- `getClassName()`: tên lớp triển khai do provider công bố.
- `getAttribute(name)`: thuộc tính tùy chọn; alias có thể ánh xạ nhiều tên về cùng một dịch vụ.

Tài liệu/API không đảm bảo mọi máy đều có cùng tập provider, thứ tự provider hoặc mọi alias. Nên ưu tiên **tên thuật toán chuẩn**, chỉ cố định provider khi có yêu cầu cụ thể đã được kiểm chứng (ví dụ thiết bị, chứng nhận hoặc khả năng tương thích).

## 5. Thực hành 1: tra cứu engine và xác định provider thực tế

Lưu chương trình sau thành **`JcaEngineLookup.java`** (Java 17 trở lên):

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;
import java.util.HexFormat;

public class JcaEngineLookup {
    public static void main(String[] args) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Provider provider = digest.getProvider();
        byte[] result = digest.digest("abc".getBytes(StandardCharsets.UTF_8));

        System.out.println("SHA-256: " + HexFormat.of().formatHex(result));
        System.out.println("Provider selected: " + (provider != null));
        System.out.println("Service registered: " +
                (provider.getService("MessageDigest", "SHA-256") != null));
        System.out.println("Provider name: " + provider.getName());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 JcaEngineLookup.java
java JcaEngineLookup
```

Ba dòng đầu cho kết quả sau trên runtime có hỗ trợ SHA-256:

```text
SHA-256: ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
Provider selected: true
Service registered: true
```

Dòng cuối `Provider name: ...` hiển thị **tên provider do JVM thực tế lựa chọn** (ví dụ `SUN` trên một số JDK), nên không được xem một tên cụ thể là đầu ra bất biến.

Điều cần quan sát:

1. Mã ứng dụng chỉ yêu cầu loại dịch vụ `MessageDigest` và thuật toán `SHA-256`.
2. `digest.getProvider()` cho biết provider đang cung cấp engine đó.
3. `getService("MessageDigest", "SHA-256")` kiểm tra bản ghi dịch vụ của provider; `Provider.Service` mô tả triển khai, còn `MessageDigest` là API để gọi thao tác.
4. SHA-256 của chuỗi `"abc"` là kết quả thuật toán chuẩn; provider khác tuân thủ cùng thuật toán phải cho cùng digest.

Muốn khám phá danh sách provider, có thể chạy đoạn sau trong một chương trình Java bất kỳ:

```java
for (Provider p : java.security.Security.getProviders()) {
    System.out.println(p.getName() + " / " + p.getVersionStr());
    for (Provider.Service s : p.getServices()) {
        if (s.getType().equalsIgnoreCase("MessageDigest")) {
            System.out.println("  " + s.getAlgorithm());
        }
    }
}
```

Đây là **đoạn mã nhúng**, không có `main`/import riêng. Tên, số lượng provider và thuật toán in ra thay đổi theo JVM, cấu hình bảo mật và provider được cài thêm.

## 6. Thực hành 2: tự tạo SPI và provider tối giản để thấy đường đi của API

Ví dụ dưới đây **chỉ mô phỏng cơ chế đăng ký và ủy nhiệm**, hoàn toàn **không phải thuật toán băm mật mã**. Nó cộng các byte theo modulo 2³² rồi trả về bốn byte. Kẻ tấn công có thể dễ dàng tạo va chạm; tuyệt đối không dùng cho lưu mật khẩu, chữ ký số, kiểm tra tính toàn vẹn có đối thủ, hoặc hệ thống thực tế.

Lưu thành **`JcaSpiProviderDemo.java`** (Java 17 trở lên):

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.Provider;
import java.util.HexFormat;
import java.util.Objects;

public class JcaSpiProviderDemo {
    public static final class EducationalProvider extends Provider {
        public EducationalProvider() {
            super("EduProvider", "1.0", "Provider minh hoa, KHONG dung trong bao mat");

            // Đăng ký service type + algorithm -> lớp triển khai SPI.
            put("MessageDigest.EDU-CHECKSUM32", ToyDigestSpi.class.getName());
        }
    }

    public static final class ToyDigestSpi extends MessageDigestSpi {
        private int sum;

        @Override
        protected void engineUpdate(byte input) {
            sum += input & 0xff;
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

    public static void main(String[] args) throws Exception {
        Provider provider = new EducationalProvider();

        // Provider object được dùng trực tiếp, không cần Security.addProvider().
        MessageDigest digest = MessageDigest.getInstance(
                "EDU-CHECKSUM32", provider);

        byte[] bytes = digest.digest("ABC".getBytes(StandardCharsets.US_ASCII));
        System.out.println(digest.getProvider().getName());
        System.out.println(HexFormat.of().formatHex(bytes));
        System.out.println(provider.getService(
                "MessageDigest", "EDU-CHECKSUM32") != null);
    }
}
```

Chạy:

```bash
javac --release 17 JcaSpiProviderDemo.java
java JcaSpiProviderDemo
```

Đầu ra đã được kiểm tra:

```text
EduProvider
000000c6
true
```

Giải thích theo đúng các lớp trách nhiệm:

1. `EducationalProvider` là **provider**: khai báo tên `EduProvider` và đăng ký `MessageDigest.EDU-CHECKSUM32` trỏ tới lớp SPI.
2. `ToyDigestSpi` là **implementation**: override các phương thức `engineUpdate`, `engineDigest`, `engineReset` và `engineGetDigestLength`. Các phép cộng là cơ chế checksum giả lập, không có tính an toàn mật mã.
3. `MessageDigest.getInstance(..., provider)` là **factory của engine**: thông qua thông tin provider để tạo engine dựa trên SPI đã đăng ký.
4. `digest.digest("ABC"... )` đi qua API của `MessageDigest` rồi kích hoạt SPI. Các byte ASCII `A=65`, `B=66`, `C=67` có tổng `198 = 0xC6` nên bốn byte kết quả là `00 00 00 C6`.
5. Đưa thẳng đối tượng `Provider` cho factory **không thay đổi danh sách provider toàn cục**. Cách này phù hợp để quan sát/thử nghiệm một provider riêng.

Ở provider sản xuất, việc cài SPI còn đòi hỏi xử lý trạng thái, tham số, lỗi, đối tượng khóa, hiệu năng, luồng đồng thời, kiểm thử theo chuẩn và bảo vệ trước tấn công. Cú pháp `Provider.put(...)` ở đây là cách đăng ký đơn giản cho mục đích học tập; JCA còn cung cấp `Provider.Service` và `putService(...)` để khai báo dịch vụ một cách có cấu trúc.

## 7. Đăng ký provider và thứ tự ưu tiên

Có ba tình huống thường gặp:

| Tình huống | Cách dùng | Phạm vi ảnh hưởng |
| --- | --- | --- |
| Dùng mặc định | `getInstance("SHA-256")` | JCA chọn từ provider đã đăng ký |
| Chỉ định provider | `getInstance("SHA-256", provider)` | Chỉ engine được tạo; đối tượng provider không bắt buộc đăng ký toàn cục |
| Thay đổi danh sách | `Security.addProvider(p)` / `Security.insertProviderAt(p, position)` | Ảnh hưởng thứ tự tìm kiếm provider trong cùng JVM |

`Security.getProviders()` trả danh sách provider đã đăng ký theo thứ tự ưu tiên. `addProvider` thường thêm provider vào cuối danh sách nếu chưa có; `insertProviderAt` yêu cầu vị trí bắt đầu từ 1 và dùng khi thực sự cần thay đổi mức ưu tiên. Provider cũng có thể được cấu hình lúc khởi động qua cấu hình bảo mật Java tùy bản phân phối/cách chạy.

**Không chèn/xóa hoặc đổi ưu tiên provider tùy tiện trong xử lý mỗi request:** thay đổi danh sách toàn cục có thể làm các thành phần khác trong JVM chọn triển khai khác, kéo theo thay đổi tương thích, hiệu năng hoặc chính sách mật mã. Với thư viện, ưu tiên không tự động thay đổi toàn cục; chỉ định `Provider` trực tiếp khi hợp đồng thực sự đòi hỏi.

## 8. Khi nào chọn sai hoặc không tìm thấy provider?

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| `NoSuchAlgorithmException` | Không có service/algorithm phù hợp, hoặc không thể tạo engine | Kiểm tra chính xác service type, tên thuật toán, provider có cài và `getServices()` |
| `NoSuchProviderException` | Tên provider chỉ định chưa đăng ký | Kiểm tra `Security.getProviders()`; cân nhắc overload nhận `Provider` |
| `NoSuchPaddingException` với `Cipher` | Transformation yêu cầu padding mà provider không hỗ trợ | Kiểm tra tên `algorithm/mode/padding` và khả năng triển khai |
| `InvalidKeyException` khi `init(...)` | Khóa không phù hợp thuật toán/provider hoặc bị provider từ chối | Kiểm tra kiểu khóa, kích thước, khả năng provider/HSM |
| `ProviderException` hoặc lỗi khởi tạo | Triển khai provider/hạ tầng phía dưới có vấn đề | Giữ stack trace, xem cấu hình provider và nguyên nhân gốc |

Ví dụ `Cipher.getInstance("AES/GCM/NoPadding")` yêu cầu **transformation**, không chỉ “AES”. Việc tạo engine thành công cũng **không đồng nghĩa** đã có khóa/nonce hợp lệ hoặc đã thao tác đúng; với GCM phải bảo đảm nonce không tái sử dụng với cùng khóa và xử lý authentication tag đúng cách. Các vấn đề đó thuộc mức sử dụng thuật toán, ngoài trách nhiệm thuần túy của cơ chế chọn provider.

Một số engine có thể trì hoãn chọn provider đến `init(...)` vì cần xét hỗ trợ khóa. Lấy `getProvider()` có thể buộc lựa chọn sớm ở một số implementation. Vì vậy khi gỡ lỗi, hãy ghi **engine, algorithm/transformation, khóa/tham số, tên provider thực tế và thời điểm phát sinh lỗi** thay vì chỉ dựa vào thứ tự provider.

## 9. Những hiểu lầm và lỗi thiết kế thường gặp

- **Nhầm API với implementation:** ứng dụng gọi `MessageDigest`, không nên khởi tạo trực tiếp lớp SPI của một provider bằng reflection. SPI là hợp đồng dành cho tác giả provider.
- **Nhầm provider với thuật toán:** provider chỉ là bên cung cấp dịch vụ; việc `getInstance()` trả về đối tượng không chứng minh thuật toán đã được dùng an toàn trong quy trình nghiệp vụ.
- **Cố định tên vendor trên mọi môi trường:** provider `SUN` hay `SunJCE` không phải cam kết chung cho mọi JVM. Nếu thật sự cần nhà cung cấp cụ thể, kiểm tra điều kiện triển khai ngay lúc khởi động.
- **Cho rằng thứ tự ưu tiên luôn quyết định mọi trường hợp:** engine có trạng thái khởi tạo/khóa có thể chọn provider muộn hoặc từ chối khóa không tương thích.
- **Dùng chung engine mutable giữa nhiều thread:** `MessageDigest`, `Signature`, `Cipher` thường giữ trạng thái đang xử lý; không mặc định coi instance là thread-safe. Tạo instance riêng theo tác vụ hoặc quản lý đồng bộ đúng hợp đồng.
- **Xem checksum/`MessageDigest` như mã hóa hay lưu mật khẩu an toàn:** digest không đảo ngược thành plaintext và không thay thế được hàm băm mật khẩu có salt, cost phù hợp. `EDU-CHECKSUM32` ở ví dụ chỉ dạy SPI.
- **Bỏ qua lỗi khi provider thay đổi:** cùng API nhưng khác provider có thể khác khả năng hỗ trợ, cách xử lý tham số, hiệu năng hoặc yêu cầu triển khai. Kiểm thử trên runtime đích và sử dụng test vector chuẩn.

## 10. Tự kiểm tra kiến thức

1. Tại sao `MessageDigest.getInstance("SHA-256")` không cần biết lớp Java cài SHA-256 của provider nào? **Vì engine là API ổn định, còn JCA dùng bảng service của provider để tìm implementation SPI.**
2. Nếu muốn thử một provider nhưng không muốn đổi cấu hình cho toàn JVM, dùng overload nào? **`getInstance(algorithm, Provider)`.**
3. `Provider.Service` khác `MessageDigest` thế nào? **Service là bản mô tả loại dịch vụ/thuật toán/lớp triển khai; MessageDigest là đối tượng engine mà ứng dụng thao tác.**
4. Vì sao `getInstance()` thành công chưa chứng minh thao tác chữ ký/mã hóa chắc chắn thành công? **Vì bước `init` với khóa/tham số, hỗ trợ provider và các điều kiện mật mã vẫn có thể thất bại.**

Khi hiểu mối quan hệ **engine API → tra cứu provider/service → SPI implementation**, bạn có thể đọc chính xác một lệnh JCA, chẩn đoán vì sao runtime chọn một provider cụ thể, và biết phần code nào thuộc ứng dụng hay thuộc nhà triển khai thuật toán.

## Tài liệu chính thức để đối chiếu

- [Java 17 — JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)
- [Java 17 — How to Implement a Provider](https://docs.oracle.com/en/java/javase/17/security/howtoimplaprovider.html)
- [Java 17 — Provider.Service API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.Service.html)
- [Java 17 — MessageDigestSpi API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigestSpi.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-002">2.2 Lấy dịch vụ qua getInstance và lựa chọn provider theo thứ tự ưu tiên</a>

<details>
<summary>Xem chi tiết</summary>

# 2.2 Lấy dịch vụ qua `getInstance` và lựa chọn provider theo thứ tự ưu tiên

Trong Java Cryptography Architecture (JCA), ứng dụng yêu cầu **loại dịch vụ + tên thuật toán**, còn JCA tìm một **provider** cung cấp triển khai tương ứng. Điểm mấu chốt của bài này là biết `getInstance(...)` chọn triển khai nào, vì sao kết quả có thể khác giữa các môi trường và lúc nào nên chỉ định provider.

Sau bài học, bạn có thể:
- Phân biệt **engine class**, **algorithm/transformation**, **provider** và **SPI**.
- Sử dụng ba dạng `getInstance`: chọn tự động, khóa theo tên provider, khóa theo đối tượng `Provider`.
- Đọc thứ tự provider, kiểm tra một dịch vụ có được công bố và nhận biết provider thực sự đang dùng.
- Giải thích trường hợp provider ưu tiên cao không phù hợp, ngoại lệ khi không có dịch vụ, và những lưu ý về chọn provider trễ.
- Quyết định khi nào để JCA tự chọn và khi nào phải cấu hình provider cụ thể.


## 1. Đọc đúng câu hỏi đặt ra cho `getInstance`

Trước khi chọn provider, cần xác định **loại engine** và **yêu cầu thuật toán**. Trong `MessageDigest.getInstance("SHA-256")`, `MessageDigest` là API engine, `SHA-256` là algorithm, còn provider là triển khai sẽ được JCA chọn. Mỗi provider đăng ký các cặp service/algorithm qua `Provider.Service`, kết nối tới implementation SPI. Đây là các vai trò khác nhau: một provider có thể cung cấp nhiều dịch vụ, và một ứng dụng có thể dùng nhiều provider cho nhiều engine.

| Điều cần quyết định | Ví dụ | Hệ quả khi nhầm |
| --- | --- | --- |
| **Engine** | `MessageDigest`, `Signature`, `Cipher` | Đưa `AES` vào digest hoặc `SHA-256` vào cipher là sai loại dịch vụ |
| **Algorithm/transformation** | `SHA-256`, `SHA256withRSA`, `AES/GCM/NoPadding` | Tên cipher thiếu mode/padding có thể khiến hành vi phụ thuộc provider |
| **Provider selection** | Tự tìm, khóa theo tên, hoặc truyền đối tượng `Provider` | Khả năng tương thích, HSM và chính sách fallback thay đổi |

Bài này **đi sâu vào quyết định lựa chọn**: khi nào dùng overload tự động, khi nào buộc một provider, ưu tiên nào có hiệu lực và tại sao lựa chọn có thể diễn ra muộn đến `init`. Các chi tiết viết SPI/provider nằm ở phần kiến trúc; tại đây, mã thí nghiệm sẽ đo **sự thay đổi provider thực tế** khi thay thứ tự đăng ký.

## 2. Ba cách gọi `getInstance`

Dùng `MessageDigest` làm ví dụ; các engine có phương thức tương tự, nhưng ngoại lệ và tham số có thể khác.

| Lệnh | Cách chọn | Ràng buộc |
| --- | --- | --- |
| `MessageDigest.getInstance("SHA-256")` | JCA tìm trong các provider đã đăng ký theo cơ chế ưu tiên của JVM. | Linh hoạt, có khả năng tận dụng provider thích hợp trên môi trường đang chạy. |
| `MessageDigest.getInstance("SHA-256", "SUN")` | Chỉ dùng provider **có tên** `SUN`. | Provider phải đã được đăng ký; tên chỉ là ví dụ, không bảo đảm tồn tại. |
| `MessageDigest.getInstance("SHA-256", provider)` | Chỉ dùng đúng **đối tượng** `Provider` được truyền vào. | Với overload này, đối tượng provider không nhất thiết phải có trong danh sách đã đăng ký. |

Không có overload tự động “thử provider khác” khi **đã chỉ định rõ** provider mà nó không hỗ trợ thuật toán. Nếu `getInstance("SHA-256", "ProviderX")` thất bại, bạn phải xử lý ngoại lệ hoặc có chính sách fallback tường minh của ứng dụng. Đừng âm thầm đổi sang provider khác nếu yêu cầu bảo mật bắt buộc dùng một HSM hoặc provider được kiểm định.

Lưu ý tên thuật toán và loại engine là hai thành phần của khóa tìm kiếm: `MessageDigest/SHA-256` khác với `Signature/SHA256withRSA`. Với `Cipher`, hãy nêu transformation đầy đủ (thuật toán/chế độ/padding), chẳng hạn `AES/GCM/NoPadding`; `Cipher.getInstance("AES")` thiếu thông tin chế độ và padding, dễ dẫn đến lựa chọn không mong muốn tùy provider.

Java định nghĩa **standard algorithm names**; nên dùng tên chuẩn thay vì dựa vào alias riêng của một provider. Tên thuật toán được tra cứu không phân biệt chữ hoa/thường theo API, nhưng điều đó không biến một tên sai thành một thuật toán hợp lệ.

## 3. Provider được chọn như thế nào?

### 3.1. Thứ tự ưu tiên khi không chỉ định provider

`Security.getProviders()` trả về các provider đang đăng ký của JVM theo thứ tự ưu tiên; **vị trí 1 là ưu tiên cao nhất**. Danh sách được thiết lập lúc khởi động từ cấu hình bảo mật của JVM và có thể được ứng dụng thay đổi bằng API.

Giả sử danh sách logic như sau:

| Vị trí | Provider | Các dịch vụ công bố trong ví dụ |
| --- | --- | --- |
| 1 | P1 | `Signature/SHA256withRSA` |
| 2 | P2 | `MessageDigest/SHA-256` và `Signature/SHA256withRSA` |
| 3 | P3 | `MessageDigest/SHA-256` |

Khi gọi `MessageDigest.getInstance("SHA-256")`, P1 bị bỏ qua vì không có dịch vụ digest được yêu cầu; P2 là ứng viên phù hợp đầu tiên. Với `Signature.getInstance("SHA256withRSA")`, P1 là ứng viên có ưu tiên cao nhất. Khi chỉ định P3 trực tiếp cho `SHA-256`, thứ tự P1/P2 không quyết định kết quả.

Đây là **mô hình mặc định**; không suy ra rằng provider ở hàng đầu tiên của `Security.getProviders()` luôn được dùng trong mọi trường hợp. Còn có những điểm tinh tế sau:

1. **Phạm vi engine và thuật toán:** provider được ưu tiên cao chỉ có ý nghĩa nếu công bố đúng loại dịch vụ và tên thuật toán (hoặc alias tương thích).
2. **Có khả năng khởi tạo/sử dụng:** một dịch vụ được công bố không đồng nghĩa mọi khóa, tham số hoặc hoàn cảnh sử dụng đều được chấp nhận. Ngoại lệ khi gọi `init` hay thực hiện phép toán phải được xử lý riêng.
3. **Ưu tiên theo thuật toán của JDK:** bản triển khai tham chiếu JDK có security property `jdk.security.provider.preferred`, có thể đặt thứ tự ưu tiên **cho thuật toán cụ thể** khác với thứ tự hiện ra từ `Security.getProviders()`. Vì vậy, danh sách đăng ký là thông tin cần kiểm tra, còn `getProvider()` của đối tượng sử dụng mới là bằng chứng về provider đã chọn.
4. **Chọn provider trễ:** một số engine/triển khai, đặc biệt `Signature`, có thể trì hoãn việc chốt provider cho đến khi `initSign`/`initVerify` biết khóa cụ thể. Gọi `getProvider()` **trước** lúc `init` trong các trường hợp đó có thể buộc chọn sớm và làm mất cơ hội tìm triển khai phù hợp với khóa. Nên kiểm tra provider sau khởi tạo khi cần quan sát chính xác.
5. **Thay đổi động:** thứ tự provider có thể bị thay đổi sau khi JVM khởi động; kết quả truy vấn ở hai thời điểm không nhất thiết trùng nhau. Đừng giả định thay đổi danh sách sẽ chuyển provider của các đối tượng đã tạo.

Một cách nhớ chính xác: **`getInstance(algorithm)` yêu cầu JCA tìm triển khai phù hợp theo chính sách lựa chọn của JVM; `getInstance(algorithm, provider)` ràng buộc lựa chọn vào provider được chỉ định.**

### 3.2. Phân biệt chọn thuật toán, chọn provider và khởi tạo

- **Chọn thuật toán:** `getInstance("SHA-256")` xác định yêu cầu làm hàm băm SHA-256.
- **Chọn provider:** JCA xác định lớp triển khai cung cấp yêu cầu đó.
- **Khởi tạo/tác nghiệp:** các engine như `Cipher`/`Signature` còn cần `init` với khóa, chế độ thao tác và tham số phù hợp. `getInstance` thành công không bảo đảm `init` thành công.

Ví dụ: provider công bố thuật toán chữ ký nhưng không chấp nhận một loại `PrivateKey` nhất định có thể ném `InvalidKeyException` tại `initSign`. Không nên hiểu đó là “thuật toán không tồn tại”; cũng không nên tùy tiện đổi provider nếu khóa chỉ nằm trong thiết bị phần cứng bắt buộc.

## 4. Quan sát provider và service trên JVM hiện tại

Các API cốt lõi:

- `Security.getProviders()`: mọi provider đã đăng ký theo thứ tự ưu tiên chung.
- `Security.getProvider("Tên")`: lấy provider đăng ký theo tên; trả `null` nếu không có.
- `Security.getProviders("MessageDigest.SHA-256")`: lọc provider công bố dịch vụ/thuật toán này; có thể trả **`null`** khi không có ứng viên.
- `provider.getService("MessageDigest", "SHA-256")`: lấy mô tả `Provider.Service`, hoặc `null` nếu provider không công bố dịch vụ đó.
- `provider.getServices()`: xem những dịch vụ provider công bố; tránh in toàn bộ trong log vận hành.
- `engine.getProvider()`: provider của engine đã được chọn; với các engine chọn trễ, xem sau `init`.
- `Security.getProperty("jdk.security.provider.preferred")`: tham khảo cấu hình ưu tiên riêng của JDK khi cần giải thích kết quả không trùng thứ tự chung.

### Ví dụ chạy được: kiểm tra SHA-256 và provider thực tế

Lưu toàn bộ thành **`JcaDiscovery.java`** (Java 17+):

~~~java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;

public class JcaDiscovery {
    public static void main(String[] args) throws GeneralSecurityException {
        Provider[] installed = Security.getProviders();

        System.out.println("Registered providers (global order):");
        for (int i = 0; i < installed.length; i++) {
            Provider p = installed[i];
            Provider.Service service = p.getService("MessageDigest", "SHA-256");
            System.out.printf("%d. %s | SHA-256: %s%n",
                    i + 1, p.getName(),
                    service == null ? "not advertised" : "advertised");
        }

        Provider[] candidates =
                Security.getProviders("MessageDigest.SHA-256");
        System.out.println("Advertised SHA-256 providers: "
                + (candidates == null ? 0 : candidates.length));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Provider selected = digest.getProvider();
        String hex = HexFormat.of().formatHex(
                digest.digest("abc".getBytes(StandardCharsets.UTF_8)));

        System.out.println("Selected provider: " + selected.getName());
        System.out.println("SHA-256(abc): " + hex);

        // Hai cách khóa vào đúng provider đã quan sát:
        MessageDigest byName =
                MessageDigest.getInstance("SHA-256", selected.getName());
        MessageDigest byObject =
                MessageDigest.getInstance("SHA-256", selected);
        System.out.println("Explicit name/object: "
                + byName.getProvider().getName() + "/"
                + byObject.getProvider().getName());
    }
}
~~~

Chạy ở thư mục chứa tệp:

~~~bash
javac JcaDiscovery.java
java JcaDiscovery
~~~

Giá trị **bất biến** trong đầu ra là:

~~~text
SHA-256(abc): ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Số lượng provider, thứ tự, tên provider được chọn và phần `Explicit name/object` **phụ thuộc JVM/cấu hình**; không nên chép một danh sách `SUN`/`SunJCE` làm kết quả cố định. Trong phạm vi chương trình này, hai overload tường minh đều phải trả về đúng tên provider đã được chọn cho `digest`.

Ví dụ dùng `SHA-256` chỉ để quan sát JCA. Đừng dùng một digest SHA-256 đơn lẻ làm phương pháp lưu mật khẩu; đó là bài toán khác, cần thuật toán dẫn xuất mật khẩu chuyên dụng.

## 5. Thí nghiệm độc lập: thay thứ tự ưu tiên để nhìn thấy sự khác biệt

Để kiểm chứng mà không phụ thuộc danh sách provider cài sẵn, ta đăng ký **hai provider đồ chơi**, cùng công bố thuật toán giả `LESSON-ONLY`. Chúng trả về một byte ký hiệu khác nhau: A cho `0a`, B cho `0b`, **hoàn toàn không phải hàm băm mật mã**. Chỉ chạy ví dụ này trong một chương trình thử nghiệm riêng.

Lưu thành **`ProviderPriorityDemo.java`** (Java 17+):

~~~java
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;

public class ProviderPriorityDemo {
    // SPI giả, chỉ trả về marker; không tạo digest bảo mật.
    public static class A extends MessageDigestSpi {
        @Override
        protected void engineUpdate(byte input) {}

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {}

        @Override
        protected byte[] engineDigest() {
            return new byte[] { 0x0a };
        }

        @Override
        protected void engineReset() {}

        @Override
        protected int engineGetDigestLength() {
            return 1;
        }
    }

    public static class B extends A {
        @Override
        protected byte[] engineDigest() {
            return new byte[] { 0x0b };
        }
    }

    static class DemoProvider extends Provider {
        DemoProvider(String name, Class<? extends MessageDigestSpi> spi) {
            super(name, "1.0", "Teaching-only marker, NOT cryptography");
            putService(new Service(this, "MessageDigest", "LESSON-ONLY",
                    spi.getName(), null, null));
        }
    }

    private static void show(String label, MessageDigest md) {
        System.out.println(label + " -> " + md.getProvider().getName()
                + ":" + HexFormat.of().formatHex(
                        md.digest(new byte[] { 42 })));
    }

    public static void main(String[] args) throws Exception {
        String pa = "LessonA";
        String pb = "LessonB";

        if (Security.getProvider(pa) != null
                || Security.getProvider(pb) != null) {
            throw new IllegalStateException("Provider name taken");
        }

        try {
            Security.insertProviderAt(new DemoProvider(pb, B.class), 1);
            Security.insertProviderAt(new DemoProvider(pa, A.class), 1);
            // A vừa chiếm vị trí 1; B bị đẩy xuống vị trí 2.

            show("default", MessageDigest.getInstance("LESSON-ONLY"));
            show("pinned B", MessageDigest.getInstance("LESSON-ONLY", pb));

            Security.removeProvider(pa);
            show("after removing A",
                    MessageDigest.getInstance("LESSON-ONLY"));
        } finally {
            Security.removeProvider(pa);
            Security.removeProvider(pb);
        }
    }
}
~~~

~~~bash
javac ProviderPriorityDemo.java
java ProviderPriorityDemo
~~~

Với một JVM bình thường không có cấu hình đặc biệt cho thuật toán giả này, stdout:

~~~text
default -> LessonA:0a
pinned B -> LessonB:0b
after removing A -> LessonB:0b
~~~

Phân tích từng bước:

1. Đăng ký B ở vị trí 1, rồi đăng ký A ở vị trí 1; A trở thành provider ưu tiên nhất cho `MessageDigest/LESSON-ONLY`.
2. `getInstance("LESSON-ONLY")` chọn A. Dùng tên `LessonB` tường minh vẫn chọn B, bất kể thứ tự.
3. Sau khi bỏ A, lần gọi `getInstance` **mới** chọn B; ví dụ không yêu cầu một engine đã được tạo phải tự thay provider.
4. `finally` dọn hai provider ví dụ ngay cả khi phát sinh ngoại lệ.

`Security.insertProviderAt(provider, 1)` chèn provider ở đầu; `Security.addProvider(provider)` thêm vào cuối danh sách, còn `Security.removeProvider(name)` gỡ provider. Hai hàm thêm trả **`-1`** nếu provider cùng tên đã được cài đặt, nên khi viết mã thật cần kiểm tra kết quả. Nếu muốn **chuyển vị trí** một provider đã đăng ký, API quy định phải gỡ rồi đăng ký lại; không giả định `insertProviderAt` tự di chuyển provider cũ.

**Cảnh báo về phạm vi:** danh sách provider là trạng thái bảo mật ở cấp JVM; thay đổi nó trong ứng dụng nhiều luồng có thể ảnh hưởng các phần khác. Ví dụ trên chỉ để học và kiểm chứng; trong hệ thống thực tế, cấu hình provider cần được quản lý ở giai đoạn khởi động, với kiểm thử và kiểm soát triển khai.

## 6. Xử lý lỗi đúng điểm phát sinh

| Tình huống | Ngoại lệ/kết quả đáng chú ý | Cách hiểu và xử lý |
| --- | --- | --- |
| Không provider nào cung cấp `MessageDigest/SHA-256` | `NoSuchAlgorithmException` từ `getInstance` | Kiểm tra tên thuật toán, dịch vụ, danh sách và cấu hình provider. |
| Chỉ định tên provider chưa được đăng ký | `NoSuchProviderException` | Kiểm tra `Security.getProvider(name)` và cách cấu hình/deploy provider. |
| Provider có tồn tại nhưng không công bố thuật toán được chỉ định | `NoSuchAlgorithmException` | Tên provider hợp lệ không có nghĩa nó hỗ trợ mọi engine/algorithm. |
| Chỉ định chuỗi tên provider rỗng/null | `IllegalArgumentException` | Cần tên provider không rỗng với overload nhận `String`. |
| Chỉ định đối tượng `Provider` là null | `IllegalArgumentException` | Cần tham chiếu đối tượng hợp lệ. |
| `Signature`/`Cipher` nhận khóa hoặc tham số không phù hợp lúc `init` | Chẳng hạn `InvalidKeyException`/`InvalidAlgorithmParameterException` | Phân tích tương thích khóa/tham số; `getInstance` thành công chưa chứng minh thao tác mật mã sẽ thành công. |
| `Cipher` nhận transformation với padding không được hỗ trợ | Có thể gặp `NoSuchPaddingException` | Kiểm tra chính xác transformation và nhà cung cấp; đừng lặng lẽ đổi sang cấu hình yếu hơn. |
| `Security.getProviders(filter)` không thấy ứng viên | Trả `null` (không phải mảng rỗng được bảo đảm) | Kiểm tra null trước khi duyệt hoặc gọi `length`. |

Không nên viết kiểu “thử `getInstance` với provider A; bắt mọi `Exception` rồi âm thầm dùng provider B”. Trong yêu cầu HSM/FIPS, fallback như vậy có thể vi phạm mục tiêu bảo mật. Với phần mềm phổ thông, thường hãy dùng overload không gắn provider và xử lý lỗi rõ ràng khi dịch vụ không sẵn có.

## 7. Vì sao provider được chọn khác giữa máy phát triển và production?

Những nguyên nhân thường gặp:

- **Khác bản phân phối JDK hoặc hệ điều hành:** tập provider sẵn có và các dịch vụ được hỗ trợ khác nhau.
- **Khác cấu hình JVM:** `conf/security/java.security` (đường dẫn điển hình trong JDK) và các security properties liên quan, đặc biệt `jdk.security.provider.preferred` của bản triển khai tham chiếu.
- **Có đăng ký provider bổ sung:** thư viện mật mã, thiết bị HSM/PKCS#11, provider tối ưu phần cứng hoặc cấu hình khi khởi động.
- **Khóa không tương thích:** một provider có thuật toán nhưng không xử lý được một đối tượng khóa cụ thể. Với engine chọn trễ, provider thực tế có thể chỉ được chốt sau `init`.
- **Thời điểm quan sát:** ứng dụng có thể thêm/gỡ provider trong lúc chạy.

Quy trình chẩn đoán gọn:

1. Ghi nhận `java -version`, cấu hình bảo mật và tên engine/algorithm/transformation yêu cầu.
2. In `Security.getProviders()` cùng thứ tự; lọc đúng dịch vụ qua `getProviders("MessageDigest.SHA-256")` hoặc `Provider.getService`.
3. Với `MessageDigest`, đọc `getProvider()` sau `getInstance`; với `Signature` hoặc `Cipher`, ưu tiên kiểm tra sau `init` tương ứng.
4. Kiểm tra khóa/tham số và ngoại lệ tại đúng điểm phát sinh; nếu kết quả không khớp thứ tự chung, kiểm tra chính sách ưu tiên theo thuật toán của JDK.
5. Nếu buộc dùng provider cụ thể, xác minh tên, dịch vụ, deployment và hành vi thao tác thực tế trên môi trường đích.

Tên provider là thông tin chẩn đoán hữu ích, nhưng **không phải hợp đồng di động giữa mọi JDK**. `SUN` trong một bản OpenJDK không phải đảm bảo bắt buộc ở một Java runtime khác.

## 8. Khi nào để tự chọn, khi nào khóa provider?

**Ưu tiên để JCA tự chọn** (`getInstance(algorithm)`) khi bài toán cần thuật toán chuẩn và tính di động giữa các môi trường. Cách này cho phép JVM dùng provider đã cấu hình, bao gồm các bản triển khai được tối ưu cho môi trường đó.

**Chỉ định provider** (`getInstance(algorithm, name/object)`) khi yêu cầu nghiệp vụ hoặc an ninh thực sự cần một triển khai xác định, ví dụ khóa chỉ dùng được qua HSM, cơ chế PKCS#11, chứng nhận cần kiểm chứng hoặc quy tắc triển khai bắt buộc. Khi đó cần làm rõ cơ chế cài provider, thứ tự đăng ký, quản lý khóa, ngoại lệ và **không tự động fallback ngoài chính sách**.

Không dùng việc thay provider để thay thế việc chọn thuật toán, mode và tham số an toàn. Ví dụ, chọn đúng provider vẫn không làm một transformation `AES` thiếu mode/padding trở nên rõ ràng.

## 9. Câu hỏi tự kiểm tra

1. Danh sách có P1 (chỉ `Signature/SHA256withRSA`), P2 (chỉ `MessageDigest/SHA-256`) và P3 (cả hai). `MessageDigest.getInstance("SHA-256")` thường chọn ai? **P2**, nếu không có ngoại lệ ưu tiên theo thuật toán hoặc điều kiện triển khai đặc biệt.
2. `MessageDigest.getInstance("SHA-256", "P1")` có tự chuyển sang P2 không? **Không**; nếu P1 không hỗ trợ `MessageDigest/SHA-256` sẽ ném `NoSuchAlgorithmException`.
3. `MessageDigest.getInstance("SHA-256", providerObject)` có yêu cầu providerObject phải đăng ký trong `Security.getProviders()` không? **Không**; overload nhận đối tượng có thể dùng provider chưa đăng ký.
4. Vì sao chỉ nhìn thứ tự từ `Security.getProviders()` chưa đủ kết luận provider thật được dùng? **Vì** phải xét engine/algorithm, khả năng khởi tạo, cấu hình ưu tiên theo thuật toán và có thể cả chọn provider trễ sau `init`.
5. Tại sao không nên gọi `Signature.getProvider()` trước `initSign(key)` chỉ để log? **Vì** một số triển khai có thể chốt provider sớm, bỏ qua lựa chọn dựa trên loại khóa.

## 10. Tài liệu chính thức

- [Oracle JCA Reference Guide (JDK 17)](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html) — kiến trúc engine/provider, đăng ký và thứ tự ưu tiên.
- [MessageDigest API (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html) — các overload `getInstance`, `getProvider` và ngoại lệ; có ghi chú `jdk.security.provider.preferred`.
- [Security API (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html) — `getProviders`, `insertProviderAt`, `addProvider`, `removeProvider`.
- [Signature API (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html) — chu trình `initSign`/`initVerify` và lỗi liên quan đến khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-003">2.3 Tra cứu tên thuật toán chuẩn, alias và phạm vi hỗ trợ của từng provider</a>

<details>
<summary>Xem chi tiết</summary>

# 2.3 Tra cứu tên thuật toán chuẩn, alias và phạm vi hỗ trợ của từng provider

## Mục tiêu

Sau bài này, bạn có thể (1) chọn đúng **service type** và **tên thuật toán chuẩn** khi gọi JCA/JCE, (2) phân biệt tên chuẩn với **alias** mà một provider tự đăng ký, (3) tra cứu các dịch vụ của từng provider, và (4) kiểm tra khả năng dùng thực tế thay vì suy đoán từ danh sách tên.

Trong Java Cryptography Architecture (JCA), ứng dụng yêu cầu một dịch vụ bằng cặp **loại dịch vụ + tên thuật toán**. Ví dụ, `MessageDigest.getInstance("SHA-256")` yêu cầu dịch vụ `MessageDigest` với thuật toán `SHA-256`. JCA tìm một `Provider` có implementation tương ứng (SPI); JCE là phần API cho các chức năng như `Cipher`, `Mac`, `KeyAgreement`. Hai hệ API dùng chung cơ chế provider.

## 1. Phân biệt service type, tên chuẩn, alias và transformation

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Service type / engine** | Nhóm chức năng API; tên thuật toán chỉ có ý nghĩa trong nhóm này | `MessageDigest`, `Signature`, `Mac`, `Cipher`, `KeyFactory` |
| **Standard algorithm name** | Tên quy ước trong *Java Security Standard Algorithm Names*; dùng để viết mã dễ chuyển giữa provider | `SHA-256`, `SHA256withRSA`, `HmacSHA256`, `PBKDF2WithHmacSHA256` |
| **Provider service name** | Tên chính mà provider đăng ký cho một `Provider.Service`; có thể là tên chuẩn hoặc phần mở rộng của provider | `SHA-256` trong một dịch vụ `MessageDigest` |
| **Alias** | Tên thay thế trỏ đến cùng dịch vụ trong **một provider cụ thể**; không được mặc định mọi provider đều hỗ trợ | `SHA256` có thể là alias của `SHA-256` |
| **Cipher transformation** | Tên thuật toán mã hóa kèm mode/padding, có dạng `algorithm/mode/padding` | `AES/GCM/NoPadding` |
| **OID** | Định danh số của thuật toán trong một số chuẩn ASN.1/X.509; khả năng nhận OID làm tên JCA tùy engine/provider | `1.2.840.113549.1.1.11` chỉ là một OID, không phải cú pháp thay thế phổ quát |

Một số cặp engine và tên thường gặp:

| API gọi | Service type | Tên truyền vào |
| --- | --- | --- |
| `MessageDigest.getInstance("SHA-256")` | `MessageDigest` | `SHA-256` |
| `Mac.getInstance("HmacSHA256")` | `Mac` | `HmacSHA256` |
| `Signature.getInstance("SHA256withRSA")` | `Signature` | `SHA256withRSA` |
| `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` | `SecretKeyFactory` | `PBKDF2WithHmacSHA256` |
| `Cipher.getInstance("AES/GCM/NoPadding")` | `Cipher` | transformation đầy đủ |
| `CertificateFactory.getInstance("X.509")` | `CertificateFactory` | `X.509` |

**Không tráo đổi tên giữa các engine:** `SHA-256` là digest, `HmacSHA256` là MAC dùng khóa, còn `SHA256withRSA` là tên thuật toán chữ ký. `AES` cũng không đồng nghĩa với một cấu hình mã hóa có mode và padding đã xác định.

**Tên chuẩn không đồng nghĩa với “chắc chắn được cài”.** Specification quy định cách gọi tên, còn phạm vi implementation đến từ JDK, provider được đăng ký và môi trường vận hành. Tên chuẩn không phân biệt hoa/thường, nhưng một alias như `SHA256` không tự xuất hiện chỉ vì tên `SHA-256` hợp lệ; nên viết đúng dạng chuẩn trong mã ứng dụng.

## 2. JCA phân giải tên thuật toán qua provider thế nào?

Luồng khái quát:

`engine.getInstance(tên [, provider])` → tìm service đúng engine → xét tên chính/alias → tạo implementation → (nếu cần) khởi tạo bằng khóa/tham số → thực hiện thao tác.

- **Không chỉ định provider:** JCA/JCE tìm implementation trong các provider đã cấu hình. `Security.getProviders()` hiển thị danh sách provider đã đăng ký và thứ tự ưu tiên chung; lựa chọn thực tế còn có thể chịu cấu hình ưu tiên riêng theo thuật toán và cơ chế chọn provider khi khởi tạo.
- **Chỉ định provider:** `MessageDigest.getInstance("SHA-256", provider)` hoặc `Cipher.getInstance("AES/GCM/NoPadding", provider)` thử đúng provider được cung cấp; điều này giúp kiểm chứng phạm vi từng provider.
- **Alias có phạm vi cục bộ:** một provider có thể đăng ký `Alg.Alias.MessageDigest.SHA256` trỏ đến `SHA-256`; provider khác có thể không đăng ký alias đó. Cách đăng ký cụ thể là chi tiết của provider.
- **Phân biệt tên trả về:** `Provider.Service.getAlgorithm()` là tên đăng ký của service; `MessageDigest.getAlgorithm()` có thể giữ nguyên tên bạn đã yêu cầu, kể cả alias. Không dùng giá trị trả về từ engine làm bằng chứng tên chuẩn của specification.

Ví dụ, nếu `p.getService("MessageDigest", "SHA256")` trả về service với `getAlgorithm()` bằng `SHA-256`, bạn đã có bằng chứng **provider `p`** nhận `SHA256` làm tên thay thế của dịch vụ đó. Không được suy rộng kết quả sang một provider khác hoặc một bản JDK khác.

## 3. Các API tra cứu và giới hạn của chúng

| Cách tra cứu | Bạn biết được | Điều chưa được chứng minh |
| --- | --- | --- |
| `Security.getProviders()` | Provider đã đăng ký trong JVM đang chạy, thứ tự danh sách và tên/phiên bản của chúng | Một provider có hỗ trợ thuật toán cụ thể hay không |
| `Security.getAlgorithms("MessageDigest")` | Tập tên thuật toán được môi trường quảng bá cho engine đó | Provider cụ thể, alias đầy đủ, hay thao tác với khóa/tham số có thành công không |
| `p.getServices()` | Các service mà provider `p` công bố; mỗi service có `getType()`, `getAlgorithm()`, `getClassName()` | Mọi tên alias có thể dùng và mọi biến thể transformation |
| `p.getService(type, name)` | Service của **provider đó** cho tên chính **hoặc alias**; `null` nếu không tìm thấy | Khởi tạo/thao tác với khóa, mode, padding, phần cứng có thành công không |
| `Security.getProviders("MessageDigest.SHA-256")` | Các provider khớp điều kiện filter đăng ký (có thể trả `null`) | Provider nào được engine chọn ở thời điểm thực thi |
| `MessageDigest.getInstance(name, p)` | Provider `p` có thể cung cấp instance cho tên digest đã hỏi | Mọi API khác hoặc mọi biến thể cùng họ thuật toán đều hoạt động |
| `Cipher.getInstance(transformation, p)` | Provider `p` chấp nhận yêu cầu tạo cipher với transformation cụ thể | Khóa, IV/nonce, độ dài khóa, policy, thiết bị và `doFinal` sẽ thành công |

Không có API công khai `Provider.Service.getAliases()`. Bạn có thể kiểm tra **một alias ứng viên** bằng `getService(type, alias)` hoặc `getInstance(alias, provider)`. Các thuộc tính cũ dạng `Alg.Alias.<type>.<alias>` (nếu có) hữu ích để chẩn đoán, nhưng không nên coi việc duyệt chúng là cách chuẩn, đầy đủ và portable để liệt kê mọi alias.

Với `Cipher`, `p.getService("Cipher", "AES/GCM/NoPadding") == null` **không có nghĩa** provider không làm được AES-GCM: provider có thể đăng ký dịch vụ `Cipher.AES` rồi xử lý mode/padding khi `Cipher.getInstance` phân giải transformation. Vì vậy hãy kiểm tra bằng transformation đầy đủ qua **chính API `Cipher.getInstance`**.

## 4. Chương trình Java 17: tra cứu provider, alias và transformation

Tạo tệp `JcaProviderLookup.java`:

```java
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public class JcaProviderLookup {
    public static void main(String[] args) throws Exception {
        Set<String> digests = Security.getAlgorithms("MessageDigest");
        System.out.println("Registered MessageDigest names: " + digests.size());

        System.out.println("Providers exposing SHA-256 MessageDigest:");
        for (Provider provider : Security.getProviders()) {
            Provider.Service service = provider.getService("MessageDigest", "SHA-256");
            if (service != null) {
                System.out.printf("  %s -> %s%n",
                        provider.getName(), service.getAlgorithm());
            }
        }

        System.out.println("Lookup canonical name and possible alias:");
        for (String name : List.of("SHA-256", "SHA256")) {
            try {
                MessageDigest digest = MessageDigest.getInstance(name);
                System.out.printf("  %s -> algorithm=%s, provider=%s%n",
                        name, digest.getAlgorithm(), digest.getProvider().getName());
            } catch (NoSuchAlgorithmException e) {
                System.out.println("  " + name + " -> unsupported");
            }
        }

        System.out.println("Providers accepting AES/GCM/NoPadding factory:");
        for (Provider provider : Security.getProviders()) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
                System.out.println("  " + provider.getName() + " -> " + cipher.getAlgorithm());
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                // Provider này không cung cấp transformation được yêu cầu.
            }
        }

        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest("abc".getBytes(StandardCharsets.UTF_8));
        System.out.println("SHA-256(abc): " + HexFormat.of().formatHex(hash));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac --release 17 JcaProviderLookup.java
java JcaProviderLookup
```

**Cách đọc kết quả:**

- Số lượng digest, tên provider và số dòng từng nhóm **phụ thuộc JDK, provider và cấu hình máy**; không được cố định chúng thành một danh sách “mọi Java đều có”.
- `SHA256` có thể thành công hoặc in `unsupported`. Ngay cả khi thành công, dòng `algorithm=SHA256` không chứng minh đó là tên chuẩn; nó có thể là tên được truyền vào engine.
- Dòng cuối là phần **có kết quả xác định** (khi SHA-256 khả dụng và hoạt động đúng):

```text
SHA-256(abc): ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

Mã này dùng `HexFormat` (Java 17+) và không thêm/bớt/thay đổi provider của JVM. Nó kiểm tra khả năng **tạo** `Cipher`, chưa kiểm tra khả năng mã hóa/giải mã thật.

## 5. Kiểm tra alias cho **một provider**, tránh kết luận toàn cục

Đoạn sau minh họa, đặt **bên trong một phương thức có khai báo `throws NoSuchAlgorithmException`** (hoặc xử lý ngoại lệ) và import `java.security.*`:

```java
Provider p = MessageDigest.getInstance("SHA-256").getProvider();
Provider.Service standard = p.getService("MessageDigest", "SHA-256");
Provider.Service candidate = p.getService("MessageDigest", "SHA256");

boolean sameRegisteredService =
        standard != null
        && candidate != null
        && standard.getAlgorithm().equalsIgnoreCase(candidate.getAlgorithm());

System.out.println(p.getName() + " accepts SHA256 alias: "
        + sameRegisteredService);
```

Đây là phép thử với **`p` tại thời điểm hiện tại**. Nó xác định hai tên phân giải đến cùng *tên service đăng ký* trong provider đó; để xác nhận dùng được, hãy gọi thêm `MessageDigest.getInstance("SHA256", p)` rồi thực hiện `digest`. Đừng lấy một danh sách alias của một JDK hoặc một thư viện rồi hardcode với giả định mọi môi trường sẽ nhận.

Một service còn có thể có thuộc tính như `SupportedKeyClasses` hoặc `SupportedKeyFormats` (tùy engine và provider), đọc qua `service.getAttribute(name)`; đây là thông tin hỗ trợ tra cứu, **không thay thế việc khởi tạo bằng khóa thật**.

## 6. “Có hỗ trợ” phải được xác nhận theo từng mức

Ví dụ với `AES/GCM/NoPadding`:

1. **Tên hợp lệ:** tài liệu chuẩn xác nhận các thành phần `AES`, `GCM`, `NoPadding` có ý nghĩa. Điều này chưa chỉ ra provider đang cài.
2. **Đăng ký service:** kiểm tra `p.getServices()` và tên liên quan; không kết luận toàn bộ transformation từ việc nhìn một dòng `Cipher.AES`.
3. **Tạo engine:** thử `Cipher.getInstance("AES/GCM/NoPadding", p)`. Nếu ném `NoSuchAlgorithmException`/`NoSuchPaddingException`, provider đó không đáp ứng yêu cầu factory đã thử.
4. **Khởi tạo và thực thi:** thử `init`, `doFinal` và giải mã/kiểm tra xác thực bằng khóa và tham số đại diện cho môi trường thật. Lỗi `InvalidKeyException`, `InvalidAlgorithmParameterException` hoặc lỗi provider/hardware có thể chỉ xuất hiện ở bước này.

Ví dụ kiểm thử hoạt động thực tế cần tạo khóa AES hợp lệ, nonce 96-bit ngẫu nhiên và `GCMParameterSpec(128, nonce)`; mã hóa bằng `Cipher.ENCRYPT_MODE`, rồi giải mã bằng `Cipher.DECRYPT_MODE` với cùng key/nonce **chỉ để khôi phục đúng một bản mã thử nghiệm** và so sánh plaintext. Với hệ thống thật, **không tái sử dụng cùng nonce cho hai lần mã hóa với cùng khóa GCM**, và phải lưu/truyền nonce tương ứng cùng ciphertext/tag để có thể giải mã. Nếu provider HSM/PKCS#11 yêu cầu khóa nằm trong thiết bị, một `SecretKeySpec` từ RAM có thể bị từ chối dù transformation được hỗ trợ.

Dùng `Cipher.getInstance("AES")` để “kiểm tra AES-GCM” là sai phạm vi: tên `AES` không ấn định mode/padding và có thể chọn giá trị mặc định không an toàn cho ứng dụng. Đối với mật mã khóa đối xứng, luôn nêu rõ transformation được thiết kế.

## 7. Quy trình tra cứu có thể áp dụng trong dự án

1. **Xác định engine và thao tác cần làm.** Ví dụ checksum → `MessageDigest`; MAC có khóa → `Mac`; chữ ký → `Signature`; mã hóa → `Cipher`.
2. **Chọn tên chuẩn đúng phiên bản Java mục tiêu.** Đọc *Java Security Standard Algorithm Names* theo bản Java sẽ triển khai; kiểm tra cả tên mode/padding và yêu cầu tham số.
3. **Chụp thông tin runtime.** Ghi phiên bản Java, tên/phiên bản provider liên quan, `Security.getProviders()`, tên thuật toán đã hỏi và transformation cụ thể.
4. **Lọc provider và kiểm tra alias ứng viên.** Với engine thông thường dùng `p.getService(type, name)` và `getInstance(name, p)`; với `Cipher` kiểm tra trực tiếp transformation trên từng provider.
5. **Xác nhận thao tác.** Với digest: băm test vector; với signature: ký rồi verify; với cipher: round-trip với tham số/khóa thực tế; với provider phần cứng: kiểm tra trên môi trường/slot tương ứng.
6. **Giữ tên chuẩn trong production.** Chỉ dùng alias hoặc tên riêng provider khi có lý do tương thích rõ ràng và test đúng provider/JDK mục tiêu.

### Diễn giải lỗi thường gặp

| Hiện tượng | Hướng kiểm tra |
| --- | --- |
| `NoSuchAlgorithmException` khi gọi `getInstance` | Sai service type/tên, thiếu provider, hoặc provider đã chỉ định không có dịch vụ đó; thử tên chuẩn và tra cứu từng provider |
| `NoSuchPaddingException` | Padding trong transformation không được implementation nhận; kiểm tra đủ `algorithm/mode/padding` |
| `NoSuchProviderException` ở overload dùng tên provider | Tên provider chưa được đăng ký hoặc viết sai; đối chiếu `Security.getProviders()` |
| `getService("Cipher", "AES/GCM/NoPadding") == null` nhưng `Cipher.getInstance` thành công | Provider đăng ký ở mức thuật toán hoặc transformation con; không xem đây là mâu thuẫn |
| Alias chạy trên máy A, lỗi trên máy B | Khác provider, phiên bản JDK, cấu hình bảo mật hoặc module/thiết bị; chuyển sang tên chuẩn và kiểm thử lại |
| `getInstance` thành công, `init` thất bại | Kiểm tra độ dài/loại khóa, tham số, policy, FIPS, quyền HSM và hỗ trợ chế độ cụ thể |
| `getAlgorithms` có tên nhưng thao tác thất bại | Danh sách công bố không cam kết một tổ hợp khóa/params hay trạng thái thiết bị đều hoạt động |

**Lưu ý an toàn:** việc một thuật toán được đăng ký không làm nó phù hợp cho thiết kế mới. MD5, SHA-1 cho chữ ký chống giả mạo, DES và ECB có những hạn chế bảo mật nghiêm trọng; sự hiện diện trong provider có thể nhằm mục đích tương thích. Hãy đánh giá lựa chọn thuật toán, kích thước khóa, mode, xác thực và quản lý nonce theo yêu cầu bảo mật hiện hành.

## 8. Bài tự kiểm tra

- Trên JVM của bạn, những provider nào nhận `MessageDigest/SHA-256`? Mỗi provider công bố tên service nào?
- Với **chính từng provider đó**, `SHA256` có được nhận như alias không? Hãy phân biệt kết quả `getService` với `getInstance`.
- Provider nào chấp nhận `Cipher.getInstance("AES/GCM/NoPadding", p)`? Kết quả có khác với việc tìm `p.getService("Cipher", "AES/GCM/NoPadding")` không? Giải thích lý do.
- Nếu cùng mã chạy khác kết quả ở JDK/máy khác, bạn cần lưu những thông tin nào để tái hiện và kiểm tra vấn đề?

## Tài liệu chính thức

- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên chuẩn, engine, mode, padding và các yêu cầu implementation.
- [Java SE 17 — JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): mô hình engine/provider và cách tìm implementation.
- [Java SE 17 — `Provider` và `Provider.Service`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.html): tra cứu dịch vụ, tên chính và alias.
- [Java SE 17 — `Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): transformation, lựa chọn provider và khởi tạo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-004">2.4 Phân biệt API bắt buộc của Java SE với thuật toán do một JDK cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# 2.4 Phân biệt API bắt buộc của Java SE với thuật toán do một JDK cung cấp

## Mục tiêu

Khi viết ứng dụng mật mã bằng Java, cần trả lời **ba câu hỏi khác nhau**:

1. **Java SE quy định API nào?** Ví dụ `MessageDigest`, `Signature`, `SecureRandom` trong `java.security`, hoặc `Cipher`, `Mac` trong `javax.crypto` là các API chuẩn mà mã ứng dụng có thể lập trình dựa vào.
2. **Java SE yêu cầu triển khai những thuật toán nào?** Đây là tập tối thiểu được nêu riêng trong mục **Security Algorithm Implementation Requirements** của đặc tả tương ứng với *phiên bản Java SE*.
3. **JDK/runtime đang chạy thực tế cung cấp những gì?** Các provider có sẵn hoặc được cài thêm quyết định danh sách thuật toán, transformation, định dạng keystore và một số đặc tính triển khai thực tế.

Ba câu hỏi có liên hệ nhưng không đồng nghĩa. **Có lớp API** không bảo đảm **mọi tên thuật toán** đều dùng được; **tên thuật toán được chuẩn hóa** cũng không có nghĩa Java SE yêu cầu mọi implementation hỗ trợ tên đó.

> Trong bài này, các ví dụ về tập bắt buộc lấy **Java SE 17** làm mốc để kiểm chứng cụ thể. Với Java SE 21, 25 hoặc phiên bản khác, phải kiểm tra *đúng phiên bản đặc tả* trước khi kết luận. Một bản JDK là sản phẩm triển khai Java SE kèm các công cụ; những provider và thuật toán mở rộng có thể khác nhau giữa bản phân phối, hệ điều hành, cấu hình và phiên bản cập nhật.

## 1. Bốn tầng cần phân biệt

| Tầng | Ý nghĩa | Ví dụ | Suy luận hợp lệ |
| --- | --- | --- | --- |
| **API/engine của Java SE** | Lớp, phương thức và hợp đồng lập trình chuẩn | `MessageDigest.getInstance(String)`, `Cipher.getInstance(String)`, `Security.getProviders()` | Có thể biên dịch mã sử dụng API trên phiên bản Java SE đã định, miễn dùng module/API phù hợp. |
| **Standard algorithm name** | Tên chuẩn để yêu cầu một loại dịch vụ | `SHA-256`, `SHA3-256`, `ChaCha20-Poly1305`, `RSASSA-PSS`, `PKCS12` | Nếu implementation cung cấp dịch vụ ấy, nên dùng tên chuẩn. **Chưa chứng minh dịch vụ bắt buộc phải có.** |
| **Java SE required implementation** | Những dịch vụ/thuật toán và kích thước khóa tối thiểu mà *mỗi implementation tuân thủ phiên bản Java SE đó* phải hỗ trợ | Java SE 17 yêu cầu `MessageDigest`/`SHA-256` và `Cipher`/`AES/GCM/NoPadding` với khóa AES 128 bit | Có cơ sở kỳ vọng tính khả chuyển giữa các implementation **tuân thủ Java SE 17** trong cấu hình bình thường. |
| **Provider capability tại runtime** | Thuật toán thực tế do provider đang đăng ký cung cấp; có thể nhiều hơn mức tối thiểu | Provider `SUN` hỗ trợ `SHA3-256`; `SunJCE` hỗ trợ `ChaCha20-Poly1305` trong Oracle JDK 17 | Khả dụng trong runtime/provider tương ứng; phải kiểm tra khi triển khai sang môi trường khác. |

**Chú ý về từ “bắt buộc”:** Đặc tả yêu cầu *toàn bộ implementation Java SE* cung cấp dịch vụ tối thiểu; không yêu cầu **từng provider riêng lẻ** đều cung cấp mọi dịch vụ đó. Một provider có thể chỉ triển khai `Signature`, provider khác chỉ triển khai `Cipher`.

## 2. Cơ chế JCA: API ổn định, thuật toán được chọn qua provider

Ứng dụng gọi một **engine API** như `MessageDigest`, `Signature` hoặc `Cipher`; JCA tìm provider có triển khai phù hợp và ủy quyền thao tác cho phần hiện thực phía sau API (Service Provider Interface, thường gọi là **SPI**).

```text
Mã ứng dụng
    |
    | MessageDigest.getInstance("SHA-256")
    v
Engine API chuẩn Java SE (MessageDigest)
    |
    | tìm dịch vụ MessageDigest/SHA-256
    v
JCA provider selection
    |
    +---- Provider A: không cung cấp dịch vụ
    |
    +---- Provider B: cung cấp SHA-256 --> implementation/SPI
    |
    +---- Provider C: có thể cũng cung cấp SHA-256
```

- Không chỉ định provider: `MessageDigest.getInstance("SHA-256")` để framework tìm implementation trong các provider đã đăng ký theo quy tắc lựa chọn của runtime.
- Chỉ định provider: `MessageDigest.getInstance("SHA-256", "SomeProvider")` chỉ chấp nhận provider được nêu; nếu provider vắng mặt hoặc không cung cấp đúng dịch vụ thì thao tác thất bại, dù provider khác có thể hỗ trợ.
- `Security.getProviders()` cho biết các provider được đăng ký; `getProvider()` trên đối tượng engine cho biết provider thực tế được chọn. Provider như `SUN`, `SunJCE`, `SunRsaSign`, `SunEC` là tên triển khai cụ thể của một số bản JDK, **không phải tên provider bắt buộc của Java SE**.
- Provider có thể đi kèm JDK, là thư viện bên thứ ba, hoặc tích hợp hệ điều hành/HSM. “JDK hỗ trợ” không nhất thiết có nghĩa chính JDK tự viết mọi thuật toán đó.

Cần phân biệt thêm hai nguồn tài liệu: **Java Security Standard Algorithm Names** chuẩn hóa tên và liệt kê yêu cầu tối thiểu; **JDK Providers Documentation** mô tả dịch vụ được các provider của *một JDK cụ thể* triển khai.

## 3. Java SE 17 thực sự yêu cầu những gì?

Một số trường hợp điển hình trích theo mục **Security Algorithm Implementation Requirements** của Java SE 17:

| Engine/service | Mức tối thiểu được Java SE 17 yêu cầu | Phạm vi cần hiểu đúng |
| --- | --- | --- |
| `MessageDigest` | `SHA-1`, `SHA-256` | `SHA3-256` và `SHA-512` là các tên chuẩn, nhưng không có trong **danh sách tối thiểu** của engine này ở Java SE 17. |
| `Cipher` | `AES/GCM/NoPadding`, `AES/CBC/PKCS5Padding`, `AES/CBC/NoPadding`, `AES/ECB/NoPadding`, `AES/ECB/PKCS5Padding` với AES **128 bit**, cùng một số transformation DESede và RSA được liệt kê cụ thể | Yêu cầu AES-128 **không đồng nghĩa** mọi tổ hợp mode/padding hoặc mọi cỡ khóa AES đều bắt buộc. AES/ECB thuộc tập tương thích, không phải khuyến nghị bảo mật cho dữ liệu nhiều khối. |
| `Signature` | `SHA1withDSA`, `SHA256withDSA`, `SHA1withRSA`, `SHA256withRSA` | `RSASSA-PSS` có tên chuẩn và được nhiều JDK hỗ trợ, nhưng không nằm trong tập tối thiểu Java SE 17 ở đây. |
| `Mac` | `HmacSHA1`, `HmacSHA256` | Một tên HMAC khác xuất hiện trong danh mục standard names chưa chắc là bắt buộc. |
| `KeyGenerator` | `AES` (khóa 128 bit), `DESede` (168 bit), `HmacSHA1`, `HmacSHA256` | Không thể suy ra mọi cỡ khóa hoặc mọi thuật toán sinh khóa đều được bảo đảm. |
| `KeyStore` | `PKCS12` | `JKS`, `JCEKS` được biết đến và có thể có trong JDK cụ thể nhưng không phải định dạng tối thiểu được yêu cầu cho engine này. |
| `SecureRandom` | Phải có mặc định do implementation cung cấp; **không có tên thuật toán cụ thể nào bắt buộc** trong bảng yêu cầu | Không giả định mọi Java SE 17 đều có `SHA1PRNG`, `NativePRNG` hay `DRBG` chỉ từ việc lớp `SecureRandom` tồn tại. |

Bảng trên chỉ chọn những engine quan trọng để so sánh; đặc tả còn nêu yêu cầu với `KeyFactory`, `KeyPairGenerator`, `KeyAgreement`, `CertificateFactory`, `SSLContext` và các loại dịch vụ khác. Ví dụ: `KeyStore/PKCS12` và `Signature/SHA256withRSA` thuộc mức tối thiểu, trong khi provider được chọn để hiện thực chúng vẫn tùy runtime.

**Hai giới hạn quan trọng:**

1. “Bắt buộc hỗ trợ” là yêu cầu **khả năng tương thích**, không phải đánh giá thuật toán **an toàn cho dự án mới**. Các thuật toán cũ như SHA-1 hoặc DESede có thể xuất hiện trong danh sách bắt buộc để phục vụ tương thích; không vì thế mà nên chọn chúng cho thiết kế mật mã mới.
2. Có engine instance mới chỉ xác nhận tìm được implementation. Việc `init`, tạo khóa, ký, giải mã còn phụ thuộc kích thước/loại khóa, tham số, policy bảo mật, trạng thái token/HSM và các ràng buộc runtime.

## 4. Những gì một JDK có thể cung cấp thêm

Theo tài liệu **JDK Providers Documentation** của Oracle JDK 17:

- Provider `SUN` liệt kê nhiều `MessageDigest` như `SHA-224`, `SHA-384`, `SHA-512` và `SHA3-256`, ngoài `SHA-1`/`SHA-256` tối thiểu. Việc chúng có tên chuẩn hoặc có trong Oracle JDK 17 **không nâng chúng thành yêu cầu tối thiểu của mọi Java SE 17**.
- Provider `SunJCE` có các transformation `ChaCha20-Poly1305`, nhiều biến thể AES và các dịch vụ `Mac`, `KeyGenerator`, `SecretKeyFactory` khác. `ChaCha20-Poly1305` là ví dụ rõ: tên đã được chuẩn hóa, Oracle JDK 17 hỗ trợ, nhưng không có trong bảng `Cipher` tối thiểu Java SE 17.
- Provider `SunRsaSign` có `RSASSA-PSS` và các thuật toán chữ ký RSA khác. Tính sẵn có của `RSASSA-PSS` trong bản phân phối đang chạy cần kiểm tra riêng.
- `SUN` có `KeyStore/JKS`; `SunJCE` có `KeyStore/JCEKS`. Tên/định dạng này có trong hệ sinh thái JDK nhưng không thay thế hợp đồng `PKCS12` tối thiểu.

Dù cùng tên thuật toán, provider vẫn có thể khác nhau về tham số chấp nhận, hiệu năng, hỗ trợ phần cứng và cấu hình. Mở rộng của một JDK/provider chỉ trở thành **phụ thuộc của ứng dụng** nếu ta chủ động yêu cầu hoặc kiểm tra nó.

## 5. Thực hành: hỏi runtime thay vì đoán

Tạo file **`JcaAvailability.java`** với nội dung Java 17+ sau. Chương trình kiểm tra ba dịch vụ tối thiểu của Java SE 17, in provider thực sự được chọn và thử một digest có tên chuẩn nhưng không bắt buộc là `SHA3-256`.

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import javax.crypto.Cipher;

public final class JcaAvailability {
    public static void main(String[] args) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        Cipher aesGcm = Cipher.getInstance("AES/GCM/NoPadding");
        Signature rsa = Signature.getInstance("SHA256withRSA");

        System.out.println("Java SE 17 minimum: OK");
        System.out.println("SHA-256 provider: " + sha256.getProvider().getName());
        System.out.println("AES/GCM provider: " + aesGcm.getProvider().getName());
        System.out.println("SHA256withRSA provider: " + rsa.getProvider().getName());

        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
            System.out.println("Optional SHA3-256: PRESENT (" +
                    sha3.getProvider().getName() + ")");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Optional SHA3-256: ABSENT");
        }
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac --release 17 JcaAvailability.java
java JcaAvailability
```

**Ví dụ kết quả** trên Oracle JDK 17 với các provider mặc định (tên provider và dòng optional **có thể khác** trên runtime khác):

```text
Java SE 17 minimum: OK
SHA-256 provider: SUN
AES/GCM provider: SunJCE
SHA256withRSA provider: SunRsaSign
Optional SHA3-256: PRESENT (SUN)
```

**Cách diễn giải kết quả:**

- Nếu ba dòng bắt buộc được khởi tạo thành công, runtime hiện tại đáp ứng *những trường hợp vừa kiểm tra*. Chúng không phải một bài kiểm thử tuân thủ toàn bộ Java SE 17.
- Nếu `SHA3-256` báo `ABSENT`, **không được kết luận Java SE 17 bị thiếu API hoặc không tuân thủ đặc tả**; thuật toán này không thuộc mức tối thiểu Java SE 17.
- Nếu `getInstance` ném exception cho một dịch vụ vốn thuộc tập tối thiểu, cần xem lại bản JDK, provider đăng ký, cấu hình/policy và môi trường triển khai. Không nên tự động suy luận toàn bộ API Java SE đã biến mất.
- Ví dụ mới chỉ lấy **đối tượng engine**, chưa mã hóa hoặc ký dữ liệu. Đối với AES-GCM thật, phải dùng khóa hợp lệ, nonce/IV duy nhất phù hợp với khóa và xử lý authentication tag đúng cách; không tái sử dụng nonce với cùng khóa.

Để xem các provider đã cài và dịch vụ mà từng provider công bố, có thể dùng đoạn mã **minh họa đặt bên trong một phương thức**:

```java
for (java.security.Provider provider : java.security.Security.getProviders()) {
    System.out.println(provider.getName());
    for (java.security.Provider.Service service : provider.getServices()) {
        if (service.getType().equals("MessageDigest")) {
            System.out.println("  MessageDigest: " + service.getAlgorithm());
        }
    }
}
```

`Security.getAlgorithms("MessageDigest")` cũng liệt kê các tên digest được đăng ký trong runtime. Tuy nhiên, khi kiểm tra một **Cipher transformation** đầy đủ (algorithm/mode/padding), hãy thử `Cipher.getInstance("...")` thay vì chỉ so khớp chuỗi trong danh sách dịch vụ: provider có thể công bố tên cipher cơ sở và các mode/padding được hỗ trợ qua thuộc tính thay vì mọi transformation dưới dạng một entry độc lập.

## 6. Cách chọn API, tên thuật toán và provider trong ứng dụng

### Trường hợp A: cần chạy trên mọi implementation Java SE 17 tuân thủ chuẩn

Dùng **API công khai** và một thuật toán nằm trong **danh sách tối thiểu** của đúng Java SE 17. Ví dụ:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
Signature signature = Signature.getInstance("SHA256withRSA");
```

Đây là các **lời gọi minh họa**; chúng cần được đặt trong phương thức có xử lý exception, và `Cipher`/`Signature` phải `init` với khóa, tham số phù hợp trước khi xử lý dữ liệu. Không chỉ định tên provider nếu ứng dụng không có yêu cầu riêng về provider.

### Trường hợp B: muốn dùng thuật toán hiện đại/khả năng mở rộng ngoài tập tối thiểu

Ví dụ ứng dụng chọn `ChaCha20-Poly1305`, `SHA3-256` hoặc `RSASSA-PSS` vì yêu cầu giao thức hoặc thiết kế. Có thể dùng **cùng engine API chuẩn**, nhưng cần:

1. Ghi rõ **tên dịch vụ, tên thuật toán, tham số và phiên bản runtime/provider tối thiểu** mà ứng dụng yêu cầu.
2. Kiểm tra hỗ trợ trên **mọi môi trường đích**, tốt nhất lúc khởi động và trong kiểm thử tích hợp; thông báo cấu hình thiếu hỗ trợ một cách rõ ràng.
3. Khi có fallback, chỉ dùng fallback **được giao thức và chính sách bảo mật cho phép**. Không tự động “hạ cấp” sang thuật toán yếu hơn để tránh lỗi.
4. Kiểm thử cả `init`/thao tác thực tế, không dừng ở bước `getInstance`.

Nếu môi trường yêu cầu HSM, provider đã kiểm định hoặc tích hợp native cụ thể, việc chỉ định provider có thể là chủ ý hợp lý; lúc đó tên provider và quy trình cài/đăng ký là một phần của hợp đồng triển khai. Khi không có yêu cầu này, hard-code `"SUN"`, `"SunJCE"` hay `"SunRsaSign"` làm giảm khả chuyển.

### Trường hợp C: muốn bảo đảm transformation và tham số rõ ràng

Với `Cipher`, tên chỉ gồm thuật toán, ví dụ `Cipher.getInstance("AES")`, để provider quyết định mode/padding mặc định. Một số provider dùng ECB theo mặc định, dễ tạo thiết kế không an toàn cho dữ liệu nhiều khối. Hãy chọn **transformation đầy đủ** như `AES/GCM/NoPadding` và xử lý đúng nonce, tag và khóa. Sự tồn tại của tên chuẩn `AES` không bảo đảm mọi transformation hay mặc định an toàn như nhau.

## 7. Lỗi phổ biến và cách phân loại khi chẩn đoán

| Biểu hiện/nhận định | Vấn đề thật sự | Cách xử lý |
| --- | --- | --- |
| “`Cipher` là API Java SE nên mọi cipher đều chạy.” | Nhầm **engine API** với tập thuật toán bắt buộc. | Tra bảng yêu cầu tối thiểu theo phiên bản; nếu ngoài bảng, kiểm tra provider/runtime. |
| “Có trong **Standard Names** thì mọi JDK phải hỗ trợ.” | Nhầm **tên đã chuẩn hóa** với **implementation bắt buộc**. | Tìm tiếp mục **Security Algorithm Implementation Requirements**. |
| `NoSuchAlgorithmException` | Không tìm được implementation phù hợp cho yêu cầu hiện tại, hoặc yêu cầu thuật toán/provider không có. | Kiểm tra spelling/tên chuẩn, version, provider đã đăng ký và dịch vụ được hỗ trợ. |
| `NoSuchPaddingException` khi lấy `Cipher` | Padding được yêu cầu không được hỗ trợ cho transformation/provider đang chọn. | Dùng transformation chính xác, có hỗ trợ và phù hợp thiết kế. |
| `NoSuchProviderException` khi gọi overload có tên provider | Provider được chỉ định không được đăng ký. | Kiểm tra cấu hình cài provider hoặc bỏ ràng buộc provider nếu không cần. |
| `InvalidKeyException` hoặc `InvalidAlgorithmParameterException` khi `init` | Thuật toán có thể đã được tìm thấy, nhưng khóa/tham số không hợp lệ. | Xem cỡ khóa, loại khóa, IV/nonce, policy, HSM và yêu cầu của provider. |
| “Đã bắt buộc hỗ trợ SHA-1/DESede thì nên dùng trong dự án mới.” | Nhầm mục tiêu tương thích với đánh giá an toàn. | Chọn thuật toán/độ dài khóa theo tiêu chuẩn bảo mật hiện hành của hệ thống. |
| “`SHA-256` chạy với provider mặc định thì chắc chạy với `SunJCE`.” | Một provider riêng **không phải** toàn bộ tập provider của Java SE. | Dùng overload không chỉ định provider, trừ khi có yêu cầu thực sự. |

Một tình huống đặc biệt: chính sách như `jdk.tls.disabledAlgorithms` hoặc chính sách kiểm tra certificate có thể **hạn chế cách một thuật toán được sử dụng trong TLS/chứng chỉ** mà không có nghĩa engine đó đã bị xóa khỏi JCA. Vì vậy cần phân biệt *service availability*, *khả năng khởi tạo bằng key/tham số* và *quy tắc cho từng giao thức*.

## 8. Quy trình ra quyết định nhanh

1. **Xác định Java SE baseline** của ứng dụng, chẳng hạn Java SE 17; phân biệt phiên bản biên dịch (`--release 17`) với JDK/runtime thực sự khi chạy.
2. **Chọn engine API chuẩn** theo tác vụ (`MessageDigest`, `Cipher`, `Signature`, `Mac`, `KeyStore`...).
3. **Tra Standard Algorithm Names** để dùng tên đúng; sau đó **tra bảng Implementation Requirements** xem thuật toán/transformation/cỡ khóa đó có thuộc mức tối thiểu hay không.
4. **Nếu ngoài mức tối thiểu**, đọc tài liệu provider của bản JDK mục tiêu, dùng `getInstance` để kiểm tra trên runtime thực và kiểm thử thao tác với khóa/tham số thật.
5. **Ghi rõ dependency và chính sách xử lý khi thiếu**; không gắn cứng tên provider hoặc chọn fallback yếu chỉ vì tiện.
6. **Kiểm tra nhiều môi trường** nếu ứng dụng triển khai trên nhiều vendor JDK, OS, container, cấu hình provider hoặc HSM.

**Tự kiểm tra kiến thức:**

- Java SE 17 có API `MessageDigest`; liệu `SHA3-256` bắt buộc? **Không**. Tên chuẩn có tồn tại và Oracle JDK 17 có thể cung cấp, nhưng bảng tối thiểu chỉ nêu `SHA-1` và `SHA-256` cho `MessageDigest`.
- Java SE 17 yêu cầu `AES/GCM/NoPadding` với khóa 128 bit; liệu suy ra tất cả JDK phải hỗ trợ mọi cỡ khóa AES và mọi mode? **Không**.
- `KeyStore.getInstance("PKCS12")` và `KeyStore.getInstance("JKS")` có cùng tính khả chuyển theo Java SE 17? **Không**. `PKCS12` thuộc yêu cầu tối thiểu; `JKS` là một định dạng/triển khai thường có trong các JDK cụ thể.
- Một thuật toán có trong `SunJCE` có được dùng từ API `Cipher` chuẩn? **Có**, khi provider đã đăng ký và cung cấp transformation đó; việc dùng API chuẩn không biến thuật toán bổ sung thành bảo đảm của mọi implementation Java SE.

## Tài liệu gốc để đối chiếu

- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): đối chiếu phần **Standard Names** với **Security Algorithm Implementation Requirements**.
- [Java SE 17 — Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): engine API, SPI, provider selection và cách yêu cầu implementation.
- [Oracle JDK 17 — JDK Providers Documentation](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): danh sách provider, thuật toán và transformation của bản JDK được mô tả.
- [Java SE 17 — Signature API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html): ví dụ API document nêu rõ những signature algorithm Java SE yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-005">2.5 Khám phá Provider.Service và danh sách thuật toán đang có trong tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# 2.5. Khám phá `Provider.Service` và danh sách thuật toán đang có trong tiến trình

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích quan hệ giữa kiến trúc Java Cryptography Architecture (JCA), `Provider`, `Provider.Service` và các API kiểu `getInstance(...)`.
- Liệt kê **provider đang đăng ký trong chính JVM hiện tại**, các loại dịch vụ và thuật toán mà chúng công bố.
- Tìm provider cung cấp một cặp **loại dịch vụ + thuật toán**, xem thông tin triển khai và xác nhận provider thực sự được chọn khi tạo đối tượng.
- Phân biệt **được quảng bá trong registry**, **có thể tạo instance**, và **sử dụng được với key/tham số thực tế**.

Ví dụ dùng Java 17+; danh sách provider và thuật toán phụ thuộc JDK, nhà cung cấp JDK, bản cấu hình, thư viện cài đặt và thời điểm quan sát. Không có một danh sách đầu ra cố định cho mọi máy.

## 1. `Provider.Service` nằm ở đâu trong JCA?

JCA dùng kiến trúc có thể thay thế implementation. Ứng dụng gọi một **engine API** như `MessageDigest`, `Signature`, `KeyStore` hoặc `SecureRandom`. Một **provider** đăng ký các implementation cho từng loại dịch vụ và tên thuật toán. `Provider.Service` là **bản mô tả đăng ký** cho một implementation: dịch vụ gì, thuật toán nào, provider nào, lớp triển khai nào và có những thuộc tính gì.

```text
Ứng dụng: MessageDigest.getInstance("SHA-256")
                    |
            JCA engine / factory
                    |
       Danh sách provider được đăng ký
                    |
          Provider -> Provider.Service
                    |
              Implementation (SPI)
```

- `Security.getProviders()` trả về các provider **đã đăng ký** trong JVM, theo thứ tự ưu tiên (vị trí 1 được xét trước). Một JAR có trên classpath/module path chưa đồng nghĩa provider của nó đã được đăng ký.
- `provider.getServices()` trả về tập các bản mô tả dịch vụ mà provider công bố. Cùng một loại và thuật toán có thể xuất hiện ở **nhiều provider** khác nhau.
- Với `MessageDigest.getInstance("SHA-256")`, JCA tìm implementation phù hợp qua cơ chế lựa chọn provider. Nếu không chỉ định provider, đừng giả định tên provider luôn cố định. Một số engine có thể trì hoãn việc chọn implementation đến khi biết key/tham số.
- Registry có thể thay đổi khi ứng dụng đăng ký, chèn ưu tiên hoặc gỡ provider; kết quả khám phá chỉ phản ánh **tiến trình và thời điểm đã đọc**.

Các engine tiêu biểu: `MessageDigest` (băm), `Signature` (chữ ký số), `Cipher` (mã hóa/giải mã), `Mac` (mã xác thực thông điệp), `KeyAgreement` (thỏa thuận khóa), `KeyFactory`, `KeyGenerator`, `KeyStore`, `SecureRandom` và `SSLContext`. Mỗi engine có ý nghĩa, thuật toán và điều kiện sử dụng khác nhau.

## 2. Bộ API khám phá cần biết

| Câu hỏi | API Java | Ý nghĩa |
| --- | --- | --- |
| JVM đã đăng ký những provider nào? | `Security.getProviders()` | Mảng provider theo thứ tự ưu tiên. |
| Tìm một provider theo tên? | `Security.getProvider(name)` | Trả về `Provider` hoặc `null`. |
| Provider cung cấp những dịch vụ nào? | `provider.getServices()` | Tập `Provider.Service` không thể sửa qua tập trả về; không dựa vào thứ tự duyệt. |
| Provider có cặp type/algorithm này không? | `provider.getService(type, algorithm)` | Trả về descriptor hoặc `null`; tên thuật toán có thể là tên chính hoặc alias provider biết. |
| Tên thuật toán đang được quảng bá cho một loại dịch vụ? | `Security.getAlgorithms(type)` | Tập tên thuật toán/kiểu có sẵn theo registry, không thể sửa trực tiếp; nếu không có trả tập rỗng. |
| Provider nào công bố một cặp dịch vụ/thuật toán? | `Security.getProviders("MessageDigest.SHA-256")` | Mảng provider phù hợp, hoặc `null` nếu không có. |
| Instance cụ thể đã chọn provider nào? | `engineInstance.getProvider()` | Provider thực tế của đối tượng, có ý nghĩa hơn dự đoán từ registry. |

Trên mỗi `Provider.Service`, các getter thiết yếu là:

- `getProvider()`: provider sở hữu descriptor.
- `getType()`: tên loại dịch vụ, chẳng hạn `MessageDigest`.
- `getAlgorithm()`: tên thuật toán hoặc kiểu do provider đăng ký, chẳng hạn `SHA-256`.
- `getClassName()`: tên lớp triển khai mà provider khai báo; chủ yếu hữu ích cho chẩn đoán, **không có nghĩa** ứng dụng nên tự tải hay khởi tạo lớp SPI đó.
- `getAttribute(name)`: thuộc tính tùy chọn theo tên, trả `null` khi không có. Ví dụ một số dịch vụ/provider có `KeySize` hoặc `SupportedKeyClasses`; đừng giả định tất cả đều có.
- `supportsParameter(...)` và `newInstance(...)` là các cơ chế framework dùng theo quy ước từng engine. Trong code ứng dụng, ưu tiên factory công khai (`MessageDigest.getInstance`, `Cipher.getInstance`...) thay vì tự gọi `newInstance(...)`.

**Chi tiết quan trọng về alias:** `Provider.Service` có thể chứa các tên thay thế bên trong descriptor nhưng **không có public getter `getAliases()`**. Muốn kiểm tra một alias cụ thể, dùng `provider.getService(type, alias)` hoặc thử factory tương ứng; đừng suy ra danh sách alias đầy đủ chỉ từ `getAlgorithm()`.

## 3. Chương trình liệt kê provider, service và thuật toán

Lưu toàn bộ mã sau vào **`ProviderExplorer.java`**. Mặc định chỉ in các service `MessageDigest` để dễ đọc. Có thể truyền loại dịch vụ khác, hoặc `*` để xem tất cả service. Chương trình **chỉ quan sát**, không thêm/gỡ/thay đổi provider.

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public final class ProviderExplorer {
    private ProviderExplorer() {
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        if (args.length > 1) {
            System.err.println("Cách dùng: java ProviderExplorer [serviceType|*]");
            return;
        }

        String typeFilter = args.length == 0 ? "MessageDigest" : args[0];
        Provider[] providers = Security.getProviders();

        System.out.printf("Java: %s (%s)%n",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"));
        System.out.printf("So provider: %d%n", providers.length);

        for (int i = 0; i < providers.length; i++) {
            Provider provider = providers[i];
            System.out.printf("%n[%02d] %s | version %s%n",
                    i + 1, provider.getName(), provider.getVersionStr());

            provider.getServices().stream()
                    .filter(service -> typeFilter.equals("*")
                            || service.getType().equalsIgnoreCase(typeFilter))
                    .sorted(Comparator
                            .comparing(Provider.Service::getType,
                                    String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Provider.Service::getAlgorithm,
                                    String.CASE_INSENSITIVE_ORDER))
                    .forEach(service -> System.out.printf(
                            "  %-20s %-32s %s%n",
                            service.getType(),
                            service.getAlgorithm(),
                            service.getClassName()));
        }

        if (!typeFilter.equals("*")) {
            Set<String> names = Security.getAlgorithms(typeFilter);
            TreeSet<String> sortedNames = new TreeSet<>(names);

            System.out.printf("%nThuat toan (%s): %d%n",
                    typeFilter, names.size());
            for (String name : sortedNames) {
                System.out.println("  " + name);
            }
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        System.out.printf("%nSHA-256 duoc chon tu provider: %s%n",
                digest.getProvider().getName());
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac -encoding UTF-8 ProviderExplorer.java
java ProviderExplorer
java ProviderExplorer Signature
java ProviderExplorer Cipher
java ProviderExplorer "*"
```

Cách đọc kết quả:

1. Dòng `Java` ghi phiên bản và vendor runtime; `So provider` là số lượng provider **đang đăng ký**, không phải số provider có trên máy.
2. `[01]`, `[02]`, ... thể hiện **thứ tự ưu tiên ban đầu** từ `Security.getProviders()`. Mỗi dòng dịch vụ gồm **type**, **algorithm** và **implementation class**; kết quả đã được sắp xếp **trong từng provider** để dễ đối chiếu.
3. Cuối kết quả, `Security.getAlgorithms(typeFilter)` cho tập **tên thuật toán phân biệt theo service type** trên toàn bộ provider. Tập này là bản tổng hợp, vì vậy tên xuất hiện ở hai provider vẫn chỉ là một mục trong tập.
4. Dòng cuối cho provider của instance `MessageDigest("SHA-256")` mà JCA đã tạo. Đó là quan sát tại runtime, không phải cam kết mọi máy đều chọn cùng provider.
5. Với `*`, chương trình duyệt **mọi loại dịch vụ** và bỏ qua phần tổng hợp `Security.getAlgorithms` vì `*` không phải tên engine hợp lệ. Kết quả có thể rất dài.

Ví dụ, trên một JDK có provider tên `SUN`, bạn *có thể* thấy `MessageDigest / SHA-256` thuộc provider này. Tên provider, version, số service, thứ tự, lớp triển khai và tên thuật toán thực tế **không được coi là output cố định**. Nếu lọc loại không được đăng ký, không có dòng dịch vụ tương ứng và tập thuật toán có thể rỗng.

## 4. Tra cứu dịch vụ cụ thể và các provider cùng hỗ trợ

Đoạn sau là **snippet đặt bên trong một phương thức** (với `import java.security.Provider;` và `import java.security.Security;`), không phải chương trình độc lập:

```java
Provider[] candidates =
        Security.getProviders("MessageDigest.SHA-256");

if (candidates == null) {
    System.out.println("Khong co provider phu hop");
} else {
    for (Provider provider : candidates) {
        Provider.Service service =
                provider.getService("MessageDigest", "SHA-256");

        System.out.printf("%s -> %s%n",
                provider.getName(),
                service == null ? "(khong co descriptor)" :
                        service.getClassName());
    }
}
```

Chỉ rõ cả **type và algorithm** rất quan trọng: `SHA-256` trong `MessageDigest` không phải dịch vụ cùng loại với một thuật toán `Signature` hay `Mac`. `Security.getProviders(filter)` trả `null` khi không có provider thỏa điều kiện; cần kiểm tra trước khi duyệt. Khi đã có `Provider`, `getService(type, algorithm)` hỗ trợ tên thuật toán chính hoặc **alias mà provider nhận biết**, và có thể trả `null`.

Khi cần in metadata, ví dụ `service.getAttribute("KeySize")`, hãy xử lý `null`. Attribute là thông tin provider công bố, không thay thế kiểm tra ràng buộc thực tế của key, mode, padding hay policy.


## 5. Đọc metadata sâu mà không nhầm descriptor với engine

Sau khi đã chạy `ProviderExplorer` ở mục 3, chọn một dòng `MessageDigest / SHA-256` hoặc một service khác và kiểm tra các tầng sau. `Provider.Service` là **metadata có thể tra cứu**, còn việc tạo engine và thực hiện phép toán là **bằng chứng sử dụng**. Không cần lặp một chương trình băm `abc` chỉ để chứng minh giá trị SHA-256 đã biết.

~~~java
// Snippet đặt trong một phương thức có xử lý ngoại lệ thích hợp.
// "provider" là một Provider lấy từ Security.getProviders().
Provider.Service service = provider.getService("MessageDigest", "SHA-256");

if (service != null) {
    System.out.println("type=" + service.getType());
    System.out.println("algorithm=" + service.getAlgorithm());
    System.out.println("class=" + service.getClassName());
    System.out.println("provider=" + service.getProvider().getName());

    // Một thuộc tính tùy chọn; null là bình thường.
    String formats = service.getAttribute("SupportedKeyFormats");
    System.out.println("formatsAdvertised=" + (formats != null));

    // Kiểm tra khả năng tạo instance bằng API công khai, không new SPI.
    java.security.MessageDigest engine =
            java.security.MessageDigest.getInstance("SHA-256", provider);
    System.out.println("digestLength="
            + engine.digest("catalog".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8)).length);
}
~~~

Với SHA-256 đúng chuẩn, `digestLength=32` khi nhánh chạy được. **Tên class, provider và attribute** phụ thuộc implementation, nên không thể ghi stdout cố định cho chúng. Đây là snippet để chèn vào vòng lặp `ProviderExplorer`, không phải file độc lập; import `java.security.Provider` là đủ nếu đã có biến `provider`.

Điểm cần đối chiếu: (a) descriptor tồn tại; (b) engine tạo được với **chính provider này**; (c) digest dùng được. Với `Signature` hoặc `Cipher`, bước (c) phải gồm `init` với khóa/tham số đại diện cho điều kiện thật. `supportsParameter(...)` là một gợi ý nội bộ theo loại service: kết quả `true` có thể chỉ nghĩa “có thể hỗ trợ/chưa rõ”, **không phải xác nhận rằng mọi khóa hay operation đều chạy được**.


## 6. Ba mức bằng chứng khi lập bản đồ dịch vụ runtime

| Bằng chứng | Kết luận được phép | Kết luận chưa được phép |
| --- | --- | --- |
| `Security.getAlgorithms(type)` | Tên đang được quảng bá theo service type trong registry | Toàn bộ alias, mọi transformation hoặc khả năng với khóa cụ thể |
| `provider.getServices()` / `getService(type, algorithm)` | Descriptor của provider, type, tên chính/alias, class triển khai, attribute tùy chọn | Provider chắc chắn tạo được mọi engine và xử lý thành công |
| `getInstance(..., provider)` + `init` + phép toán | Tổ hợp thuật toán, key, tham số được thử trên runtime/provider này | Mọi key, mọi version, mọi trạng thái HSM đều được bảo đảm |

**Bẫy catalog:** `Security.getAlgorithms("Cipher")` không sinh tất cả tổ hợp mode/padding; một provider có `Cipher.AES` có thể chấp nhận `AES/GCM/NoPadding` dù `getService("Cipher", "AES/GCM/NoPadding")` không cho descriptor trực tiếp. Khi kiểm tra transformation, hãy thử chính `Cipher.getInstance(transformation, provider)` và thao tác có tham số hợp lệ. Tương tự, alias có thể được `getService` phân giải dù không xuất hiện như một service riêng; `Provider.Service` không có public `getAliases()`.

**Bẫy ưu tiên:** thứ tự `Security.getProviders()` giúp mô tả registry, nhưng không thay thế việc quan sát `engine.getProvider()` khi engine đã khởi tạo đủ thông tin. **Bẫy an toàn:** registry có thể còn MD5 hoặc thuật toán cũ vì tương thích; có service không đồng nghĩa được phép dùng trong giao thức mới.

Bảng khám phá là **ảnh chụp tại thời điểm chạy**. Nếu ứng dụng thêm/gỡ provider trong cùng JVM, hoặc chạy trên HSM/JDK khác, cần chụp lại cấu hình; không mang một bản catalog từ laptop áp đặt cho production.

## 7. Quy trình chẩn đoán có thể lặp lại

Khi một môi trường báo `NoSuchAlgorithmException`, `NoSuchPaddingException` hoặc provider được chọn khác dự kiến, tiến hành theo thứ tự:

1. Ghi **`java.version`, `java.vendor`** và danh sách `Security.getProviders()` của chính tiến trình lỗi. Không lấy danh sách của laptop để kết luận về container/server đang chạy.
2. Xác định **engine type chính xác** (ví dụ `MessageDigest` hay `Cipher`) và tên thuật toán/transformation mà code thật đang yêu cầu. Lấy `Security.getAlgorithms(type)` để định hướng, rồi tìm descriptor qua từng `provider.getService(type, algorithm)`.
3. Tìm các provider ứng viên với `Security.getProviders("Type.Algorithm")` khi bộ lọc phù hợp; nhớ kiểm tra kết quả `null`. Với transformation `Cipher`, kiểm tra thêm bằng factory thực tế.
4. Thử `getInstance(...)` và, nếu cần, `init(...)`/thao tác thật với key và tham số hợp lệ. Đọc `getProvider()` của đối tượng đã chọn và ghi lỗi đầy đủ nếu thất bại.
5. So sánh **cùng phiên bản JDK, security configuration, provider đăng ký, thứ tự ưu tiên, dependencies và môi trường chạy**. Provider PKCS#11/HSM/native có thể phụ thuộc cấu hình thiết bị; có tên trong registry vẫn chưa chứng minh sẵn sàng vận hành.
6. Giữ chẩn đoán ở chế độ chỉ đọc; tránh thêm/gỡ provider toàn cục chỉ để “thử”, vì có thể làm thay đổi hành vi các luồng khác trong cùng JVM. Không log key, mật khẩu, secret hay dữ liệu nhạy cảm.

## 8. Những nhầm lẫn phổ biến và tự kiểm tra

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “JAR provider có trên classpath là đã được chọn.” | Provider cần được đăng ký hoặc được cơ chế runtime nạp phù hợp; quan sát bằng `Security.getProviders()`. |
| “`getServices()` có thứ tự ưu tiên trong từng Set.” | Thứ tự ưu tiên nằm ở **mảng provider**; hãy tự sort service khi in. |
| “`Security.getAlgorithms("Cipher")` liệt kê mọi transformation.” | Đây là tập tên được registry công bố cho service type, không phải tích Descartes của thuật toán/mode/padding. |
| “Có `Provider.Service` nghĩa là mọi key đều được chấp nhận.” | Cần thử factory và thao tác thực với key/params; implementation có thể từ chối. |
| “`getClassName()` là lớp API nên new trực tiếp.” | Nên dùng engine factory; SPI là chi tiết implementation. |
| “Provider đầu tiên luôn là provider mà `Cipher` dùng.” | Có thể xảy ra lựa chọn muộn theo key và tham số; kiểm tra instance thực tế. |
| “Thuật toán có trong registry thì an toàn.” | Khả năng cung cấp không phải khuyến nghị bảo mật. |

**Bài tự kiểm tra:**

1. Chạy `java ProviderExplorer MessageDigest` và `java ProviderExplorer Signature`. So sánh provider nào đăng ký các type này và vì sao hai danh sách thuật toán khác nhau.
2. Chạy `java ProviderExplorer Cipher`, sau đó tra API `Cipher.getInstance("AES/GCM/NoPadding")`. Giải thích vì sao không thể suy ra mọi transformation chỉ từ `getAlgorithms("Cipher")`.
3. Tìm một cặp `type + algorithm` có ở hơn một provider (nếu môi trường có). So sánh `getClassName()`, sau đó quan sát `getProvider()` từ instance được tạo mà không ép provider.
4. Nếu `Security.getProviders("MessageDigest.SHA-256")` trả `null`, điều đó nói gì về registry của tiến trình tại thời điểm gọi? Vì sao chưa nên kết luận cùng một điều về JVM khác?

## Tài liệu API chính thức (Java 17)

- [`Security` — `getProviders`, `getAlgorithms`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html)
- [`Provider` — `getServices`, `getService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.html)
- [`Provider.Service` — cấu trúc descriptor và các phương thức](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.Service.html)
- [JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-006">2.6 AlgorithmParameterSpec, AlgorithmParameters và việc lưu tham số thuật toán</a>

<details>
<summary>Xem chi tiết</summary>

# 2.6. AlgorithmParameterSpec, AlgorithmParameters và việc lưu tham số thuật toán trong JCA

## 1. Bài toán: khóa và tham số thuật toán là hai phần khác nhau

Trong Java Cryptography Architecture (JCA), **khóa** là vật liệu mật mã dùng cho phép mã hóa, giải mã, ký hoặc xác minh; **tham số thuật toán** xác định cách một thuật toán vận hành trong một lần sử dụng cụ thể. Có đúng khóa mà thiếu tham số cần thiết vẫn có thể không giải mã hoặc xác minh được dữ liệu.

Các trường hợp tiêu biểu:

- **AES-GCM:** cần khóa, nonce/IV và độ dài authentication tag. Nonce không phải khóa, nhưng *không được trùng giữa hai lần mã hóa dùng cùng khóa*.
- **AES-CBC:** cần IV bên cạnh khóa. IV có thể công khai nhưng CBC tự nó không đảm bảo tính toàn vẹn.
- **Password-based encryption (PBE):** cần salt và số vòng lặp, đôi khi cả tham số thuật toán bên trong; không nhầm những giá trị này với mật khẩu.
- **RSA-PSS:** bên ký và bên xác minh cần thống nhất digest, mask generation function, độ dài salt và trailer field.
- **RSA-OAEP:** có lựa chọn digest, MGF và nguồn dữ liệu nhãn (label).

Khi thiết kế lưu trữ bản mã, phải xác định **những gì cần lưu để khôi phục đúng phép toán**: tên thuật toán/mode, tham số, ciphertext/tag, tham chiếu tới khóa và AAD nếu có. Không nên coi tất cả các giá trị đó là “khóa”, cũng không nên giả định provider tự tái tạo được tham số của một lần mã hóa đã qua.

## 2. Phân biệt các đối tượng trong JCA

| API | Bản chất | Dùng để làm gì |
| --- | --- | --- |
| `java.security.spec.AlgorithmParameterSpec` | Marker interface, không tự định nghĩa phương thức encode | Đại diện **có kiểu** cho các trường tham số mà engine có thể nhận. |
| `java.security.AlgorithmParameters` | Đối tượng JCA dựa trên implementation của security provider | Giữ tham số của một thuật toán; chuyển đổi giữa spec và byte encoding, đọc metadata thuật toán/provider. |
| `java.security.AlgorithmParameterGenerator` | Engine JCA có provider | Sinh các tham số theo khả năng của thuật toán/provider; không phải đối tượng dùng để giải mã một blob đã lưu. |
| `java.security.Key`, `SecretKey`, `PrivateKey` | Vật liệu khóa | Thực hiện phép mật mã, phải được quản lý theo chính sách khóa. |

`AlgorithmParameterSpec` là một cách biểu diễn *transparent*: ứng dụng truy cập các trường được lớp cụ thể công bố. Ví dụ `GCMParameterSpec.getIV()` trả IV và `getTLen()` trả độ dài tag **tính bằng bit**. `IvParameterSpec` chỉ mô tả IV, không chứa độ dài tag; `PSSParameterSpec` lại có các trường hoàn toàn khác. Bản thân interface `AlgorithmParameterSpec` **không có** `getEncoded()`.

`AlgorithmParameters` là biểu diễn *opaque* theo nghĩa ứng dụng gọi API chung và giao việc diễn giải cho provider. Những phương thức quan trọng:

- `AlgorithmParameters.getInstance("GCM")`: yêu cầu đối tượng cho tên thuật toán tham số `GCM`; JCA chọn provider hỗ trợ.
- `init(AlgorithmParameterSpec)`: khởi tạo từ một spec có kiểu.
- `init(byte[])` hoặc `init(byte[], String format)`: nhập byte encoding và giải mã theo format tương ứng.
- `getParameterSpec(GCMParameterSpec.class)`: trích xuất một spec có kiểu phù hợp.
- `getEncoded()` hoặc `getEncoded(String format)`: mã hóa tham số thành byte theo format chính hoặc format được hỗ trợ.
- `getAlgorithm()` và `getProvider()`: xem thuật toán tham số và provider đang dùng.

Một `AlgorithmParameters` mới chỉ được `init(...)` **một lần**. Muốn giải mã một bộ tham số khác, tạo instance mới. Không phải mọi loại spec đều có implementation `AlgorithmParameters` tương ứng ở mọi provider.

### Liên hệ với kiến trúc provider và engine

`Cipher`, `Signature`, `KeyPairGenerator`, `AlgorithmParameters` là các API/engine JCA; lớp SPI phía dưới do provider triển khai. Lựa chọn thuật toán/provider quyết định loại spec và encoding nào được chấp nhận.

- `Cipher.init(mode, key, AlgorithmParameterSpec)` phù hợp khi ứng dụng đã có spec có kiểu.
- `Cipher.init(mode, key, AlgorithmParameters)` phù hợp khi đã có đối tượng tham số.
- `Cipher.getParameters()` lấy tham số thực tế mà cipher dùng sau khi khởi tạo; có thể chứa giá trị mà provider tự sinh. Nếu cipher không dùng tham số, phương thức có thể trả `null`.
- `Signature.setParameter(PSSParameterSpec)` cấu hình tham số chữ ký RSA-PSS; không dùng `Cipher` để ký.
- `"AES/GCM/NoPadding"` là **Cipher transformation**, còn `"GCM"` là tên thuật toán tham số trong ví dụ. Không đưa nguyên transformation vào `AlgorithmParameters.getInstance` mà không kiểm tra hỗ trợ.

## 3. Chọn đúng lớp AlgorithmParameterSpec

| Spec | Các trường đặc trưng | Bối cảnh |
| --- | --- | --- |
| `GCMParameterSpec(tagBits, iv)` | Tag length (bit), IV/nonce (byte) | AES-GCM, ví dụ tag 128 bit và nonce 12 byte. |
| `IvParameterSpec(iv)` | IV | AES-CBC/CTR khi mode/provider yêu cầu; không diễn tả riêng độ dài tag GCM. |
| `PBEParameterSpec(salt, iterations)` | Salt, iteration count; có constructor nhận nested spec | PBE; không phải nơi lưu mật khẩu. |
| `PSSParameterSpec(...)` | Digest, MGF, MGF parameters, salt length, trailer field | RSA-PSS. |
| `OAEPParameterSpec(...)` | Digest, MGF, label source | RSA-OAEP. |
| `ECGenParameterSpec(curveName)` | Tên đường cong | Khởi tạo bộ sinh khóa EC. |

Cần phân biệt `PBEKeySpec` (thuộc họ `KeySpec`, phục vụ biểu diễn vật liệu để dẫn xuất khóa bằng `SecretKeyFactory`) với `PBEParameterSpec` (thuộc họ `AlgorithmParameterSpec`). Việc lưu salt và iteration count **không đồng nghĩa** với việc lưu mật khẩu hay khóa đã dẫn xuất.

Với AES-GCM, `new GCMParameterSpec(128, iv)` chứa IV và tag length; giá trị 128 là **bit**, tương ứng 16 byte tag. IV 12 byte tương ứng 96 bit, là kích thước thường dùng. `getIV()` trả mảng sao chép; tuy vậy ứng dụng vẫn phải kiểm soát nguồn sinh và uniqueness của IV.

## 4. Vòng đời tham số: tạo, dùng, encode, lưu và khôi phục

```text
MÃ HÓA
  key (bí mật) + nonce mới + tagBits
                  |
                  v
            GCMParameterSpec
                  |
                  v
      Cipher.init(ENCRYPT_MODE, key, spec)
                  |
      updateAAD(aad) -> doFinal(plaintext)
                  |
                  +--------> ciphertext || authentication tag
                  |
            getParameters()
                  |
                  v
           AlgorithmParameters
                  |
             getEncoded()
                  |
                  +--------> encodedParams để lưu

GIẢI MÃ
  encodedParams -> AlgorithmParameters.getInstance("GCM")
                  -> init(encodedParams)
                  -> Cipher.init(DECRYPT_MODE, key, parameters)
                  -> updateAAD(cùng aad)
  ciphertext || tag -> doFinal(...) -> plaintext hoặc lỗi xác thực
```

Có hai chiến lược lưu:

1. **Định dạng tường minh:** lưu riêng `iv` và `tagBits` cùng bản mã. Khi đọc, dựng lại `GCMParameterSpec(tagBits, iv)`. Cách này dễ xác định giao thức lưu trữ và chia sẻ với hệ thống không dùng Java.
2. **Encoding của `AlgorithmParameters`:** lưu `parameters.getEncoded()` và khôi phục bằng `parameters.init(encodedBytes)`. Cách này tiện trong hệ sinh thái provider tương thích, nhưng cần kiểm thử format/phiên bản khi muốn trao đổi giữa nhiều implementation.

**Tuyệt đối không dùng `toString()` của `AlgorithmParameters` hoặc spec làm dữ liệu lưu trữ.** Nó không có cam kết parse ngược. Cũng không nên dùng Java object serialization thay cho một giao thức lưu trữ có version và schema rõ ràng.

## 5. Ví dụ hoàn chỉnh Java 17+: AES-GCM round-trip với AlgorithmParameters

Ví dụ dưới đây dùng JDK có provider hỗ trợ `AES/GCM/NoPadding` và `AlgorithmParameters` tên `GCM` (như SunJCE của JDK phổ biến). Chương trình tạo key AES trong bộ nhớ, nonce mới, mã hóa có AAD, encode tham số, tạo đối tượng khác để decode và giải mã. Sau đó thay AAD để kiểm tra xác thực.

Tạo tệp `GcmParametersRoundTrip.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class GcmParametersRoundTrip {
    public static void main(String[] args) throws Exception {
        // Sinh key demo. Hệ thống thật phải quản lý key riêng.
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] aad = "document:v1".getBytes(StandardCharsets.UTF_8);
        byte[] input = "Ma hoa va giai ma JCA".getBytes(StandardCharsets.UTF_8);

        Cipher encryption = Cipher.getInstance("AES/GCM/NoPadding");
        encryption.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        encryption.updateAAD(aad);
        byte[] ciphertextAndTag = encryption.doFinal(input);

        AlgorithmParameters used = encryption.getParameters();
        byte[] serializedParams = used.getEncoded();

        AlgorithmParameters restored = AlgorithmParameters.getInstance("GCM");
        restored.init(serializedParams);
        GCMParameterSpec restoredSpec =
                restored.getParameterSpec(GCMParameterSpec.class);

        Cipher decryption = Cipher.getInstance("AES/GCM/NoPadding");
        decryption.init(Cipher.DECRYPT_MODE, key, restored);
        decryption.updateAAD(aad);
        byte[] output = decryption.doFinal(ciphertextAndTag);

        System.out.println("Nonce length: " + restoredSpec.getIV().length);
        System.out.println("Tag bits: " + restoredSpec.getTLen());
        System.out.println("Plaintext matches: " + Arrays.equals(input, output));
        System.out.println("Text: " + new String(output, StandardCharsets.UTF_8));

        try {
            Cipher bad = Cipher.getInstance("AES/GCM/NoPadding");
            bad.init(Cipher.DECRYPT_MODE, key, restoredSpec);
            bad.updateAAD("document:v2".getBytes(StandardCharsets.UTF_8));
            bad.doFinal(ciphertextAndTag);
            System.out.println("Tamper detected: false");
        } catch (AEADBadTagException expected) {
            System.out.println("Tamper detected: true");
        }
    }
}
```

Chạy:

```bash
javac GcmParametersRoundTrip.java
java GcmParametersRoundTrip
```

Stdout (key, nonce, ciphertext ngẫu nhiên nhưng các dòng hiển thị này ổn định):

```text
Nonce length: 12
Tag bits: 128
Plaintext matches: true
Text: Ma hoa va giai ma JCA
Tamper detected: true
```

**Giải thích:**

- `encryption.getParameters()` lấy tham số mà cipher thực dùng; `used.getEncoded()` trả byte **tham số**, không phải byte của key hay bản mã.
- `restored.init(serializedParams)` diễn giải dữ liệu đã encode. `getParameterSpec(GCMParameterSpec.class)` trích xuất lại IV và tag length để kiểm tra.
- `decryption.init(..., key, restored)` cho thấy có thể truyền trực tiếp `AlgorithmParameters` vào `Cipher`, không buộc phải dựng lại spec.
- `updateAAD` phải xảy ra trước xử lý bản mã trong GCM; cùng key, IV, ciphertext/tag **và đúng byte AAD** thì mới xác thực thành công.
- `Cipher.doFinal` ở chế độ mã hóa GCM trả ciphertext kèm authentication tag. Khi lưu phải giữ **trọn vẹn** mảng `ciphertextAndTag`.
- Khi AAD đổi, `doFinal` ném `AEADBadTagException`. Trong ứng dụng thật, phải từ chối dữ liệu và không trả plaintext chưa xác thực.

Demo chỉ giữ key trong process hiện tại. Nó **không chứng minh** có thể giải mã sau khi chương trình kết thúc mà không lưu/truy xuất key riêng. Dù `serializedParams` được lưu thành công, việc mất key vẫn khiến bản mã không thể giải mã.

### Lưu thật vào file hoặc cơ sở dữ liệu

Đoạn dưới là **mảnh mã bổ sung**, dùng sau khi đã có `serializedParams` và `ciphertextAndTag` từ chương trình trên; thêm imports `java.nio.file.Files` và `java.nio.file.Path`:

```java
Files.write(Path.of("gcm-parameters.bin"), serializedParams);
Files.write(Path.of("gcm-ciphertext.bin"), ciphertextAndTag);

// Trong quy trình đọc lại:
byte[] paramsBytes = Files.readAllBytes(Path.of("gcm-parameters.bin"));
byte[] encryptedBytes = Files.readAllBytes(Path.of("gcm-ciphertext.bin"));
AlgorithmParameters fromFile = AlgorithmParameters.getInstance("GCM");
fromFile.init(paramsBytes);
// Dùng key và AAD phù hợp rồi giải mã encryptedBytes.
```

Tệp tham số **không phải** tệp key. Với production, nên gói metadata, độ dài trường, ciphertext/tag và version thành một record có schema rõ ràng thay vì dựa vào hai tên tệp cố định. Đảm bảo key lấy từ keystore/KMS hoặc cơ chế quản lý khóa an toàn.

## 6. Thiết kế định dạng lưu trữ có version, tính tương thích và AAD

Một bản ghi mã hóa cần mô tả chính xác cách giải mã mà không làm lộ vật liệu khóa. Các trường nên cân nhắc:

| Trường | Ý nghĩa |
| --- | --- |
| `version` | Phiên bản schema lưu trữ; tránh đoán format trong tương lai. |
| `transformation` | Ví dụ `AES/GCM/NoPadding`, xác định algorithm/mode/padding. |
| `keyId` | Tham chiếu tới khóa hợp lệ trong keystore/KMS; không chứa khóa bí mật. |
| `iv` + `tagBits` **hoặc** `parameterAlgorithm` + `parameterEncoding` + `parameterBytes` | Một cách lưu tường minh hoặc một cách lưu qua `AlgorithmParameters`; ghi rõ format và độ dài. |
| `ciphertextAndTag` | Trọn vẹn bản mã kèm tag của GCM. |
| `aad` hoặc quy tắc tái tạo AAD | Phải giữ đúng **từng byte** cho phía giải mã. |

Nếu metadata như tenant, documentId, version hay keyId ảnh hưởng đến quyền truy cập hoặc cách diễn giải dữ liệu, nên **ràng buộc chúng bằng AAD** hoặc cơ chế toàn vẹn thích hợp. Không nhận lựa chọn thuật toán yếu từ metadata chưa được xác thực. Phía đọc cần giới hạn kích thước, kiểm tra thuật toán cho phép, tagBits, IV length và version trước khi dùng.

Trong JSON, có thể biểu diễn byte bằng Base64, nhưng **Base64 không mã hóa bí mật hay xác thực dữ liệu**. Định dạng nhị phân cần có độ dài các trường rõ ràng; khi đọc không được mặc định rằng các trường byte có chiều dài vô hạn hoặc đủ dữ liệu.

### Format và provider compatibility

Theo API, `getEncoded()` trả primary encoding; nếu có đặc tả ASN.1 cho tham số thì primary format sử dụng ASN.1. Tuy nhiên, *không* suy ra mọi provider đều hỗ trợ cùng tên thuật toán, cùng format phụ hoặc đọc được mọi encoding của nhau. `getEncoded(String format)` và `init(byte[], String format)` chỉ dùng được khi provider hỗ trợ format yêu cầu.

Đối với dữ liệu lưu lâu dài, nhiều runtime hoặc liên thông Java với hệ khác, cách an toàn về kỹ thuật là định nghĩa format lưu ở mức trường (`iv`, `tagBits`, v.v.), ghi rõ phiên bản, rồi viết test round-trip giữa các runtime/provider mục tiêu. Nếu chọn `AlgorithmParameters.getEncoded()` thì phải lưu thông tin format và có bài kiểm thử tương thích thực tế.

## 7. Giới hạn an toàn và lỗi thường gặp

1. **Nonce AES-GCM phải duy nhất với mỗi lần mã hóa dưới cùng khóa.** Nonce 12 byte từ `SecureRandom` là cách minh họa thực dụng; hệ thống xử lý lượng dữ liệu lớn cần kiểm soát khả năng trùng, giới hạn lượt mã hóa mỗi khóa và chính sách thay khóa. Không dùng nonce cố định, zero-IV hoặc tái sử dụng nonce cũ.
2. **Không nhầm tham số công khai với khóa.** IV, salt thường có thể đi cùng bản mã; key vẫn cần kiểm soát truy cập và quản lý vòng đời riêng. Đừng lưu key cạnh ciphertext chỉ để tiện giải mã.
3. **Không bỏ authentication tag hay nuốt lỗi xác thực.** GCM là AEAD: `AEADBadTagException` phải dẫn tới từ chối dữ liệu. Với CBC, chỉ lưu IV không tự cung cấp tính toàn vẹn.
4. **Không đoán AAD.** AAD được xác thực dù không mã hóa; AAD khi giải mã phải tái tạo đúng byte và lấy từ nguồn ngữ cảnh đáng tin cậy.
5. **Không tin provider sẽ tự sinh lại IV trong lúc giải mã.** Provider có thể sinh tham số ở chế độ mã hóa; khi đó phải lấy và lưu `Cipher.getParameters()` hoặc `getIV()` sau khi `init`.
6. **Không coi mọi spec hợp lệ ở mọi mode/provider.** GCM cần cả tag length; `IvParameterSpec` không thể thay thế hợp đồng `GCMParameterSpec` một cách an toàn/tổng quát.
7. **Không lưu `toString()` hoặc Java object serialization để round-trip.** Cần trường có schema hoặc byte encoding được xác định và kiểm thử.
8. **Không bỏ bước validate input.** Byte tham số, tagBits, salt/iteration, thuật toán, version và chiều dài ciphertext từ phía không tin cậy phải được kiểm tra trước khi cấp phát hay giải mã.

| Lỗi/triệu chứng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `InvalidAlgorithmParameterException` khi `Cipher.init` | Spec không phù hợp transformation/provider, thiếu tham số hoặc kích thước không được hỗ trợ | Kiểm tra tên transformation, spec type, IV/tag length và provider. |
| `NoSuchAlgorithmException` khi `AlgorithmParameters.getInstance` | Tên thuật toán tham số không được provider hỗ trợ | Kiểm tra danh sách provider, dùng đúng algorithm name (không nhầm transformation). |
| `InvalidParameterSpecException` | Spec không phù hợp, chưa khởi tạo hoặc cố init lại object | Tạo instance mới, chọn đúng spec/algorithm. |
| `IOException` khi decode/encode tham số | Blob bị cắt/hỏng, encoding sai hoặc object chưa khởi tạo | Kiểm tra size, format, version và encoding provider. |
| `AEADBadTagException` | Key, IV, AAD, ciphertext hoặc tag không khớp | Từ chối bản mã; điều tra dữ liệu lưu/truyền, không hạ cấp bảo mật để bỏ lỗi. |
| `Cipher.getParameters()` trả `null` | Cipher không có tham số cần xuất, hoặc chưa ở trạng thái phù hợp | Đọc hợp đồng thuật toán/mode và thời điểm gọi; không tự bịa IV. |
| Chạy được trên máy A, lỗi trên máy B | Khác provider/encoding, key hoặc AAD | Test interoperability; dùng định dạng field-level có version khi cần. |

### Checklist trước khi triển khai

- Đã xác định rõ transformation, keyId, format/version, cách lưu IV/tag và ciphertext/tag?
- Mỗi bản mã mới có nonce duy nhất dưới cùng khóa, và nonce đó được lưu đủ để giải mã?
- AAD được tính lại chính xác và những metadata nhạy cảm đã được ràng buộc xác thực?
- Lưu key riêng bằng cơ chế đáng tin cậy, có rotation và kiểm soát truy cập?
- Có test round-trip, test ciphertext/AAD bị sửa, test dữ liệu bị cắt và test provider khác?
- Có whitelist thuật toán, giới hạn kích thước và từ chối các tham số không hợp lệ?

**Tự thực hành:** sửa ví dụ lần lượt thay (a) 1 byte của ciphertext, (b) AAD, (c) IV lúc giải mã; cả ba phải không vượt qua xác thực. Sau đó thay `AlgorithmParameters` bằng cách lưu `iv` và `tagBits` riêng, khôi phục `GCMParameterSpec`, và đối chiếu ưu/nhược của hai contract lưu trữ. Không đưa khóa thật vào source hay bài tập.

## 8. Tài liệu API chính thức

- [AlgorithmParameters — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/AlgorithmParameters.html): init, encoding, provider, getParameterSpec.
- [AlgorithmParameterSpec — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/AlgorithmParameterSpec.html): marker interface của các lớp spec.
- [Cipher — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): init, getParameters, updateAAD, yêu cầu nonce GCM.
- [GCMParameterSpec — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): IV, tag length.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-007">2.7 Vòng đời khởi tạo, cập nhật, kết thúc và tái sử dụng đối tượng mật mã</a>

<details>
<summary>Xem chi tiết</summary>

# 2.7 Vòng đời khởi tạo, cập nhật, kết thúc và tái sử dụng đối tượng mật mã

## 1. Vì sao phải hiểu vòng đời?

Trong Java Cryptography Architecture (JCA) và Java Cryptography Extension (JCE), các lớp như `MessageDigest`, `Mac`, `Signature`, `Cipher` và `KeyAgreement` là những **engine object**: ứng dụng gọi API thống nhất, còn cryptographic provider thực hiện thuật toán. Mỗi đối tượng thường giữ **trạng thái có thể thay đổi**: thuật toán/provider, khóa, chế độ, tham số (IV/nonce, padding…), dữ liệu đã đưa vào và bộ đệm nội bộ.

Một lời gọi `getInstance(...)` chỉ chọn yêu cầu về thuật toán/implementation; **không có nghĩa** mọi engine đã sẵn sàng xử lý dữ liệu. Một lời gọi `update(...)` có thể chỉ nạp thêm dữ liệu, chưa tạo ra kết quả hoàn chỉnh. Một lời gọi kết thúc như `digest()`, `doFinal()`, `sign()` hoặc `verify()` hoàn tất **một thao tác mật mã**, nhưng không đồng nghĩa đóng hay hủy đối tượng. Quy tắc đặt lại trạng thái khác nhau theo lớp, thuật toán và trường hợp lỗi.

Sau bài này, cần phân biệt được: (1) tạo engine với khởi tạo khóa/chế độ; (2) `update` với `final`; (3) reset với re-init; (4) tái sử dụng về mặt API với tái sử dụng **an toàn về mật mã**, đặc biệt là AES-GCM.

## 2. Mô hình vòng đời chung

```text
getInstance(algorithm)
        |
        v
[Đối tượng engine được tạo]
        |
        | init(key/mode/parameters) khi API yêu cầu
        v
[Đã khởi tạo cho một loại thao tác]
        |
        | update(data), có thể gọi 0..n lần
        | updateAAD(aad) trước dữ liệu đối với AEAD
        v
[Đang tích lũy/xử lý một thông điệp]
        |
        | digest / doFinal / sign / verify / generateSecret
        v
[Kết thúc thông điệp, nhận kết quả hoặc lỗi]
        |
        +--> engine reset theo hợp đồng API, có thể dùng tiếp
        |
        +--> phải init lại / tạo instance mới cho khóa, IV, mode, phiên mới
        |
        +--> lỗi: không giả định trạng thái còn dùng được; khởi tạo lại
```

Đây là **mô hình khái quát**, không phải một sơ đồ trạng thái chung được Java bảo đảm cho mọi engine. Cụ thể:

- **Tạo:** `MessageDigest.getInstance("SHA-256")` trả về đối tượng ở trạng thái đã khởi tạo. Ngược lại, `Mac` cần `init(key)`; `Signature` cần `initSign(privateKey)` hoặc `initVerify(publicKey)`; `Cipher` cần `init(opmode, key, parameters)`.
- **Khởi tạo:** khóa, hướng thao tác, thuật toán và tham số phải tương thích. `Cipher.ENCRYPT_MODE` khác `Cipher.DECRYPT_MODE`; `Signature` ở chế độ ký khác chế độ xác minh.
- **Cập nhật:** nhiều `update` theo thứ tự tương đương xử lý phép nối các khối dữ liệu *trong cùng một thao tác*, với điều kiện dữ liệu và tham số giống nhau. Dữ liệu đã nạp trước đó vẫn ảnh hưởng tới kết quả đến khi kết thúc/reset.
- **Kết thúc:** phương thức cuối có thể xử lý khối còn đệm, padding, MAC/tag hoặc kiểm tra chữ ký. Vì vậy không được chỉ gọi `update` rồi xem kết quả là đã hoàn tất.
- **Bắt đầu thông điệp mới:** tuân thủ hợp đồng reset của từng API và yêu cầu an toàn về key/nonce. `reset()` không phải là phương thức phổ quát của mọi lớp; `init(...)` lại thiết lập ngữ cảnh mới.
- **Hủy:** các engine này thường không có giao thức `close()` chung như stream. Đối tượng hết tham chiếu không có nghĩa khóa bí mật đã được xóa ngay khỏi bộ nhớ.

## 3. Bảng hành vi theo engine

| Engine | Trước khi nạp dữ liệu | Nạp dữ liệu / pha trung gian | Hoàn tất | Trạng thái sau khi hoàn tất thành công |
| --- | --- | --- | --- | --- |
| `MessageDigest` | `getInstance("SHA-256")` là đủ | `update(...)` | `digest()` hoặc `digest(bytes)` | Tự reset về trạng thái digest rỗng; `reset()` có thể bỏ phần đang tích lũy |
| `Mac` | `getInstance("HmacSHA256")` rồi `init(secretKey)` | `update(...)` | `doFinal()` hoặc `doFinal(bytes)` | Reset về trạng thái ngay sau lần `init` gần nhất, giữ khóa; `reset()` cũng làm vậy |
| `Signature` | `initSign(privateKey)` hoặc `initVerify(publicKey)` | `update(...)` | `sign()` hoặc `verify(signature)` | Reset về chế độ ký/xác minh đã khởi tạo và khóa tương ứng |
| `Cipher` | `init(ENCRYPT_MODE/DECRYPT_MODE, key, params)` | `update(...)`; AEAD: `updateAAD(...)` trước dữ liệu | `doFinal(...)` | API mô tả reset về trạng thái sau `init` khi thành công; **vẫn cần IV mới cho mỗi lần mã hóa GCM cùng khóa** |
| `KeyAgreement` | `init(privateKey)` | `doPhase(peerPublicKey, lastPhase)`, có thể nhiều pha theo thuật toán | `generateSecret()` | Reset về trạng thái sau `init`; phiên mới cần các pha mới, và thường nên dùng khóa phiên mới tùy giao thức |
| `SecureRandom` | Tạo/getInstance, seed nội bộ theo provider | `nextBytes(...)`/`generateSeed(...)` | Không có thao tác final cho mỗi thông điệp | Có thể lấy nhiều lần; không có mô hình `update/doFinal` |
| `KeyGenerator` / `KeyPairGenerator` | Chọn thuật toán, nên `init` kích thước/tham số và `SecureRandom` thích hợp | Không có luồng `update` dữ liệu thông điệp | `generateKey()` / `generateKeyPair()` | Có thể tiếp tục sinh khóa; mỗi lần sinh ra vật liệu khóa mới |

**Không suy luận từ tên phương thức rằng mọi lớp đều có quy tắc reset giống nhau.** Ví dụ `Mac.reset()` giữ khóa HMAC hiện tại, còn `Cipher` không có `reset()` công khai tương ứng để tự đổi IV. Cũng không nên dùng `getInstance` để kiểm tra sự sẵn sàng của khóa hay trạng thái runtime.

## 4. Tích lũy dữ liệu: `update` và thao tác kết thúc

Giả sử dữ liệu UTF-8 của thông điệp là `"Hello JCA"` và được chia thành `"Hello "` và `"JCA"`:

```text
update("Hello ") + digest("JCA")
       cùng thông điệp
digest("Hello JCA") sau khi engine đã reset
       tạo ra cùng giá trị SHA-256
```

`MessageDigest.digest(byte[])` **vừa cập nhật dữ liệu cuối vừa kết thúc**. Tương tự, `Mac.doFinal(byte[])` tiêu thụ thêm dữ liệu cuối. Đừng vô tình gọi `update(fullMessage)` rồi `doFinal(fullMessage)`: khi đó thông điệp được xử lý hai lần. Một lệnh `doFinal()` không có dữ liệu mới kết thúc toàn bộ dữ liệu từ các `update` trước đó.

Đối với `Cipher`, `update` có thể trả về `null` hoặc 0 byte vì provider đang giữ dữ liệu để hoàn thành khối/padding/tag. `doFinal` mới đưa ra phần cuối; mã hóa và giải mã phải ghép các phần theo đúng thứ tự. Với `Cipher.getOutputSize(inputLength)`, tham số là **số byte đầu vào còn dự kiến xử lý ở thời điểm đó**, và kết quả là dung lượng đầu ra ước tính cần cho `update/doFinal`; khi dùng overload ghi vào buffer phải kiểm tra vị trí, độ dài và `ShortBufferException`.

`Signature` không trả chữ ký tại `update`: phương thức này chỉ tích lũy thông điệp cần ký hoặc xác minh. `sign` cần khóa riêng, `verify` cần khóa công khai và trả `boolean` (hoặc ném `SignatureException` khi chữ ký/định dạng/trạng thái không hợp lệ). Không đổi chiều ký/xác minh khi chưa gọi phương thức `initSign`/`initVerify` tương ứng.

### Ví dụ 1 — Hash và HMAC dùng lại đúng cách

Lưu thành **`DigestMacLifecycleDemo.java`**; chạy bằng Java 17+.

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DigestMacLifecycleDemo {
    public static void main(String[] args) throws Exception {
        byte[] a = "Hello ".getBytes(StandardCharsets.UTF_8);
        byte[] b = "JCA".getBytes(StandardCharsets.UTF_8);
        byte[] whole = "Hello JCA".getBytes(StandardCharsets.UTF_8);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(a);
        byte[] digest1 = md.digest(b);
        byte[] digest2 = md.digest(whole);
        System.out.println("digestReset=" + Arrays.equals(digest1, digest2));
        System.out.println("sha256Bytes=" + digest1.length);

        byte[] keyBytes = "demo-key-for-hmac".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        mac.update(a);
        byte[] tag1 = mac.doFinal(b);
        byte[] tag2 = mac.doFinal(whole);
        System.out.println("macReset=" + Arrays.equals(tag1, tag2));
        System.out.println("hmacBytes=" + tag1.length);
    }
}
```

```bash
javac -encoding UTF-8 DigestMacLifecycleDemo.java
java DigestMacLifecycleDemo
```

```text
digestReset=true
sha256Bytes=32
macReset=true
hmacBytes=32
```

Sau `digest(b)`, `MessageDigest` trở lại trạng thái rỗng, nên `digest(whole)` không bị ghép nhầm vào thông điệp trước. Sau `mac.doFinal(b)`, `Mac` trở lại trạng thái đã nạp cùng khóa. `reset()` của `Mac` cũng bỏ dữ liệu tích lũy nhưng **không đổi khóa**; muốn đổi khóa thì `init(newKey)`.

Khóa HMAC viết trực tiếp bằng chuỗi ở đây chỉ phục vụ quan sát vòng đời. Trong hệ thống thật, hãy tạo/lưu khóa có entropy phù hợp, ví dụ dùng `KeyGenerator` hoặc dịch vụ quản lý khóa. SHA-256 là hàm băm **không có khóa**; HMAC-SHA256 kiểm tra tính toàn vẹn/xác thực khi hai phía chia sẻ bí mật. Không dùng riêng hash để thay thế MAC.

## 5. `Cipher` và điểm dễ sai nhất: AES-GCM

`Cipher.getInstance("AES/GCM/NoPadding")` chỉ tạo engine với transformation (thuật toán/chế độ/padding). Để mã hóa, cần `init(ENCRYPT_MODE, key, GCMParameterSpec)`; giải mã cần `init(DECRYPT_MODE, key, GCMParameterSpec)`. Trong `GCMParameterSpec(tagBits, iv)`, `tagBits=128` là độ dài authentication tag theo **bit**, còn `iv` thường dài **12 byte (96 bit)**.

Một phiên mã hóa GCM diễn ra theo thứ tự:

1. Chọn **IV/nonce không lặp với cùng khóa**; có thể tạo IV 12 byte ngẫu nhiên bằng `SecureRandom` nếu quản lý rủi ro trùng nonce phù hợp.
2. `cipher.init(ENCRYPT_MODE, key, new GCMParameterSpec(128, iv))`.
3. Nếu có **AAD** (metadata cần xác thực nhưng không mã hóa), gọi `updateAAD(aad)` **trước** `update/doFinal` xử lý plaintext.
4. Gọi `update` 0..n lần, rồi `doFinal` đúng một lần cho thông điệp đó; kết quả chứa ciphertext và authentication tag (với cách trả mảng byte của Java GCM).
5. Lưu/truyền IV cùng ciphertext+tag và quy ước AAD cho phía giải mã. IV không bắt buộc giữ bí mật, nhưng phải duy nhất trong phạm vi khóa.
6. Với **thông điệp mã hóa tiếp theo dưới cùng khóa, phải init lại bằng IV mới**. Không lấy việc `doFinal` reset nội bộ làm lý do tái dùng key+IV; tái dùng cặp này có thể phá vỡ bảo mật và cho phép giả mạo.

Phía giải mã phải dùng **cùng khóa, IV, AAD** và xử lý ciphertext+tag. `AEADBadTagException` ở `doFinal` nghĩa là xác thực thất bại (có thể do sửa dữ liệu, sai khóa/IV/AAD hoặc dữ liệu bị hỏng). **Loại bỏ toàn bộ plaintext của lần giải mã lỗi**; không xuất hay sử dụng plaintext từng phần trước khi tag được xác nhận.

### Ví dụ 2 — Update, doFinal, GCM tag và re-init khi giải mã

Lưu thành **`AesGcmLifecycleDemo.java`**; chạy bằng Java 17+.

```java
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AesGcmLifecycleDemo {
    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        byte[] aad = "doc:123".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "Hello authenticated world!".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        encrypt.updateAAD(aad);
        int middle = plain.length / 2;
        byte[] first = encrypt.update(plain, 0, middle);
        byte[] last = encrypt.doFinal(plain, middle, plain.length - middle);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (first != null) {
            buffer.writeBytes(first);
        }
        buffer.writeBytes(last);
        byte[] ciphertextAndTag = buffer.toByteArray();

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        decrypt.updateAAD(aad);
        byte[] recovered = decrypt.doFinal(ciphertextAndTag);
        System.out.println("roundTrip=" + Arrays.equals(plain, recovered));

        byte[] tampered = ciphertextAndTag.clone();
        tampered[tampered.length - 1] ^= 1;
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        decrypt.updateAAD(aad);
        try {
            decrypt.doFinal(tampered);
            System.out.println("tamperRejected=false");
        } catch (AEADBadTagException expected) {
            System.out.println("tamperRejected=true");
        }
    }
}
```

```bash
javac -encoding UTF-8 AesGcmLifecycleDemo.java
java AesGcmLifecycleDemo
```

```text
roundTrip=true
tamperRejected=true
```

`encrypt.update(...)` có thể đưa ra phần ciphertext đầu hoặc chưa trả byte nào; ví dụ xử lý cả `null` rồi ghép cùng `doFinal`. Với cùng khóa và IV, GCM tính tag để phía nhận kiểm tra cả ciphertext lẫn AAD. Chương trình chủ động gọi lại `decrypt.init(...)` trước lần giải mã kiểm tra bị sửa, nhằm bắt đầu thao tác rõ ràng. **Không tái sử dụng IV của ví dụ này cho lần mã hóa khác với cùng `key`**; lần mã hóa tiếp theo phải lấy IV mới và `encrypt.init(...)` lại. Việc mã hóa tạo ciphertext khác nhau mỗi lần nên ví dụ chỉ in ra kết quả kiểm tra ổn định, không in ciphertext.

Trong sản phẩm, nên đóng gói mỗi bản ghi thành các trường có phiên bản/algorithm, IV, ciphertext+tag, cùng quy tắc AAD thống nhất. Kiểm tra độ dài dữ liệu và giới hạn bộ đệm trước khi cấp phát; không đưa dữ liệu chưa xác thực vào parser, database hoặc luồng phản hồi.

## 6. `Signature`: một đối tượng, hai chế độ có chủ đích

Một `Signature` hoạt động theo một trong hai luồng riêng:

- **Ký:** `getInstance(algorithm) → initSign(privateKey) → update(message) → sign()`. Sau `sign` thành công, trạng thái quay về như sau `initSign`; có thể `update`/`sign` thông điệp mới với cùng khóa.
- **Xác minh:** `getInstance(algorithm) → initVerify(publicKey) → update(message) → verify(signatureBytes)`. Sau `verify`, trạng thái quay về như sau `initVerify`. Kết quả `false` nghĩa là chữ ký không khớp; byte chữ ký sai định dạng hoặc lỗi provider có thể gây `SignatureException`.
- Chuyển từ ký sang xác minh, đổi người ký hoặc đổi khóa: phải gọi `initVerify`/`initSign` thích hợp, không chỉ `update`. Thuật toán tạo chữ ký và xác minh phải tương thích, bao gồm cả tham số thuật toán nếu có (ví dụ RSA-PSS).

### Ví dụ 3 — Ký, xác minh rồi tái sử dụng

Lưu thành **`SignatureLifecycleDemo.java`**; chạy bằng Java 17+.

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public class SignatureLifecycleDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] a = "record:".getBytes(StandardCharsets.UTF_8);
        byte[] b = "42".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(a);
        signer.update(b);
        byte[] signature1 = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(a);
        verifier.update(b);
        System.out.println("firstValid=" + verifier.verify(signature1));

        signer.update(a);
        signer.update(b);
        byte[] signature2 = signer.sign();
        verifier.update(a);
        verifier.update(b);
        System.out.println("reuseSignVerify=" + verifier.verify(signature2));

        verifier.update("record:".getBytes(StandardCharsets.UTF_8));
        verifier.update("43".getBytes(StandardCharsets.UTF_8));
        System.out.println("modifiedValid=" + verifier.verify(signature1));
    }
}
```

```bash
javac -encoding UTF-8 SignatureLifecycleDemo.java
java SignatureLifecycleDemo
```

```text
firstValid=true
reuseSignVerify=true
modifiedValid=false
```

Sau mỗi `sign()` và `verify(...)` thành công, cùng instance xử lý thông điệp mới được. Nếu quên gọi `update` cho thông điệp mới, API sẽ ký/kiểm tra **thông điệp rỗng**; nếu chỉ cập nhật một phần, nội dung đã ký không còn khớp nội dung mong muốn. Khóa RSA được sinh khi chương trình chạy nên bytes chữ ký không được in thành một giá trị cố định. `SHA256withRSA` ở đây là ví dụ vòng đời; lựa chọn thuật toán/chính sách ký trong sản phẩm phải đáp ứng yêu cầu bảo mật riêng.

## 7. Reset, re-init, clone và đối tượng mới: chọn đúng hành vi

| Nhu cầu | Cách xử lý đúng | Điều cần tránh |
| --- | --- | --- |
| Bỏ dữ liệu hash đang dở | `MessageDigest.reset()`; hoặc dùng `digest()` để hoàn tất rồi tự reset | Nghĩ rằng dữ liệu cũ vẫn được giữ sau `digest()` |
| HMAC thông điệp khác, **cùng key** | Sau `doFinal()`, `update`/`doFinal` thông điệp tiếp; hoặc `mac.reset()` trước khi bắt đầu | Gọi `reset()` rồi mặc định key đã thay |
| HMAC với **key khác** | `mac.init(newKey)` | Dùng cùng instance chỉ với `reset()` |
| Signature thông điệp khác, **cùng chế độ/key** | Sau `sign/verify`, gọi `update` thông điệp mới | Nhầm `verify(false)` thành chưa kết thúc và trộn dữ liệu |
| Đổi khóa/người ký/chế độ Signature | `initSign(...)` hoặc `initVerify(...)` | Dùng object đang ở chế độ đối lập |
| AES-GCM mã hóa thông điệp mới | IV/nonce duy nhất với key, rồi `cipher.init(ENCRYPT_MODE, key, new GCMParameterSpec(...))` | Gọi lại `doFinal` với cùng key+IV |
| Sau lỗi Cipher/Signature/KeyAgreement | Bỏ kết quả chưa xác thực; `init` lại hoặc tạo instance mới trước thao tác khác | Dựa vào trạng thái nội bộ chưa biết sau exception |
| Sao chép trạng thái một digest | `clone()` chỉ khi provider hỗ trợ `Cloneable` và có mục đích rõ | Giả định mọi provider hoặc engine đều clone được |

`reset` thường có nghĩa **loại bỏ trạng thái của thông điệp hiện tại**, chứ không xóa an toàn khóa khỏi bộ nhớ. `init` là ranh giới để thay đổi khóa, hướng hoặc tham số thao tác. Tạo **instance mới cho mỗi yêu cầu** thường dễ hiểu, dễ kiểm tra và tránh rò rỉ trạng thái hơn một pool instance tái sử dụng phức tạp; chi phí khởi tạo cần đo thực tế nếu muốn tối ưu.

`KeyAgreement` cũng có sự phân biệt này: `generateSecret()` kết thúc một chuỗi `doPhase` và reset về trạng thái đã `init`, nhưng **không tự tạo private key mới**. Giao thức yêu cầu ephemeral keys/forward secrecy cần quản lý vật liệu khóa phiên riêng, không suy luận an toàn từ reset API.

## 8. Sai thứ tự và xử lý lỗi

| Triệu chứng / exception | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `IllegalStateException` ở `Mac.update`, `Cipher.update/doFinal` hoặc `KeyAgreement.doPhase` | Chưa `init`; sai mode; engine không còn trạng thái hợp lệ | Xác định pha dự kiến, init đúng trước khi thao tác |
| `InvalidKeyException` / `InvalidAlgorithmParameterException` | Khóa, IV, tham số không tương thích thuật toán/mode | Kiểm tra loại khóa, kích thước, `AlgorithmParameterSpec`; không ghi log khóa |
| `NoSuchAlgorithmException` / `NoSuchPaddingException` | Transformation hoặc provider không hỗ trợ | Dùng tên thuật toán chuẩn và cấu hình provider rõ ràng |
| `ShortBufferException` | Mảng đầu ra không đủ chỗ ở overload có output buffer | Tính dung lượng bằng `getOutputSize` khi phù hợp, giữ đúng offset |
| `IllegalBlockSizeException` / `BadPaddingException` | Kích thước/padding sai, key sai hoặc ciphertext hỏng trong các mode tương ứng | Từ chối dữ liệu, không thử dùng plaintext nghi ngờ; xử lý lỗi thống nhất |
| `AEADBadTagException` | Tag GCM sai do ciphertext, AAD, IV hoặc khóa không khớp | Hủy kết quả, không xử lý plaintext; coi đây là lỗi xác thực |
| `SignatureException` hoặc `verify(...) == false` | Sai trạng thái/định dạng, hoặc chữ ký không hợp lệ | Phân biệt exception kỹ thuật với kết quả xác minh thất bại |

Một số **phản ví dụ** (minh họa sai, không phải chương trình độc lập):

```java
Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
// c.update(input); // SAI: chưa init khóa, mode, IV.

Mac m = Mac.getInstance("HmacSHA256");
// m.doFinal(message); // SAI: chưa init key.

// Sau khi AES-GCM mã hóa xong:
// c.doFinal(nextMessage); // SAI về bảo mật nếu tái sử dụng key+IV.
```

Sau exception, API có thể không bảo đảm bộ đệm/trạng thái còn đủ tin cậy; hãy loại bỏ thao tác hiện tại và **khởi tạo lại** trước khi thử lần khác. Đặc biệt, lỗi giải mã không nên trả ra thông báo chi tiết về padding, tag, khóa hay dữ liệu đầu vào cho client: thông báo khác nhau có thể trở thành kênh rò rỉ thông tin.

## 9. Thiết kế sử dụng trong ứng dụng thực

**Ranh giới thông điệp.** Mỗi request/file/chunk được xử lý như một thao tác rõ ràng: xác định algorithm + key + mode + IV/AAD → khởi tạo → truyền dữ liệu đúng thứ tự → final → kiểm tra kết quả. Chỉ công bố plaintext sau khi xác thực thành công nếu dùng AEAD. Với streaming file lớn, không được phát tán plaintext từ `update` giải mã GCM trước khi xác minh tag cuối.

**Ranh giới luồng.** Tránh cho nhiều thread cùng gọi `update/doFinal` trên một `Cipher`, `Mac`, `MessageDigest` hay `Signature` đang hoạt động. Các engine giữ trạng thái thông điệp; chia sẻ đồng thời có thể làm lẫn dữ liệu và sinh lỗi không tái hiện. Tạo object theo tác vụ/thread hoặc đồng bộ toàn bộ chuỗi thao tác nếu có lý do đặc biệt. Đừng chỉ đồng bộ riêng `doFinal`.

**Ranh giới khóa và nonce.** Khóa là bí mật: không log, không hardcode trong sản phẩm, không gửi kèm ciphertext. IV GCM có thể công khai nhưng cặp **key+IV không được lặp ở phía mã hóa**. Khi xoay khóa, đổi phiên, đổi người dùng hoặc đổi vai trò ký/xác minh, tạo ngữ cảnh engine thích hợp. Với HMAC kiểm tra giá trị nhận được, dùng so sánh byte phù hợp như `MessageDigest.isEqual(expected, received)` thay cho việc so từng byte rồi dừng ngay khi khác.

**Ranh giới provider.** `getInstance("...")` chọn implementation từ các provider có sẵn; hỗ trợ thuật toán, kiểm tra tham số, buffering và chi phí khởi tạo có thể khác nhau. Dựa vào hợp đồng public API, kiểm thử provider/JDK chạy thật và không suy luận rằng kết quả `update` luôn có độ dài cố định. Khi yêu cầu thuật toán/hardware provider cụ thể, xác định `getProvider()` và cấu hình rõ ràng.

**Ranh giới tài nguyên.** Không cần và không thể dựa vào `close()` chung để làm sạch mọi engine. Giới hạn vòng đời tham chiếu tới key và buffer nhạy cảm; xóa mảng byte có thể xóa khi không còn sử dụng bằng `Arrays.fill` là biện pháp bổ sung nhưng không bảo đảm xóa mọi bản sao bên trong JVM/provider. Không giữ đối tượng mật mã trong singleton dùng chung nếu chưa có thiết kế vòng đời chặt chẽ.

## 10. Tự kiểm tra hiểu bài

1. Sau `MessageDigest.digest()`, `digest(bytes)` tiếp theo có tính tiếp thông điệp trước không? **Không**; `digest` hoàn tất và reset state.
2. Sau `Mac.doFinal()`, muốn dùng HMAC key cũ cho thông điệp mới cần `init` lại không? **Không bắt buộc**; `Mac` đã reset về state sau `init`. Nếu đổi key, **cần** `init(newKey)`.
3. Sau `Cipher.doFinal()` với AES-GCM, có nên mã hóa thông điệp khác bằng key+IV cũ không? **Tuyệt đối không**. Hãy tạo IV mới và init lại.
4. `Cipher.update` trả `null` có đồng nghĩa không xử lý dữ liệu? **Không**; provider có thể đệm và trả kết quả ở lần sau hoặc `doFinal`.
5. Có được dùng plaintext nhận từ `update` giải mã GCM trước `doFinal` không? **Không**; tính xác thực chưa được xác nhận.
6. Sau `Signature.verify(signature)` trả `false`, thông điệp mới cần gì? **Nạp lại toàn bộ thông điệp mới** (và init lại nếu muốn đổi key/mode); trạng thái xác minh của message trước đã kết thúc.
7. Có thể gọi `update`/`doFinal` đồng thời từ nhiều request trên một engine không? **Không nên**; mỗi thông điệp cần một chuỗi thao tác tuần tự và state độc lập.

## Tài liệu API chính thức (Java SE 21)

- [JCA Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html)
- [MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Mac](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html)
- [Cipher và lưu ý về GCM/IV](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [KeyAgreement](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyAgreement.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-008">2.8 Xử lý NoSuchAlgorithmException, NoSuchProviderException và lỗi tham số</a>

<details>
<summary>Xem chi tiết</summary>

# 2.8 Xử lý `NoSuchAlgorithmException`, `NoSuchProviderException` và lỗi tham số trong JCA

Trong Java Cryptography Architecture (JCA), một phép toán mật mã thường đi qua ba giai đoạn: **chọn loại dịch vụ/thuật toán và provider**, **khởi tạo bằng khóa/tham số**, rồi **thực hiện phép toán**. Lỗi xuất hiện ở giai đoạn nào cho biết cách sửa đúng: sửa cấu hình, sửa dữ liệu/khóa đầu vào, hay xử lý kết quả xác thực. Không nên gom tất cả thành “lỗi mã hóa” và thử một thuật toán yếu hơn.

Bài này tập trung vào các ngoại lệ của JCA/JCE khi dùng các engine như `MessageDigest`, `Signature`, `KeyGenerator`, `KeyPairGenerator`, `Mac` và `Cipher`. Ví dụ chạy với **Java 17+**; thuật toán, provider và quy tắc vận hành cụ thể vẫn chịu ảnh hưởng của JDK, security policy và provider được cài đặt.

## 1. Ngoại lệ nằm ở đâu trong luồng thực thi?

```text
Cấu hình ứng dụng: engine + tên thuật toán/transformation + provider (tùy chọn)
        |
        v
getInstance(...)  --> Không có thuật toán?     NoSuchAlgorithmException
        |           --> Provider tên chỉ định? NoSuchProviderException
        |           --> Padding không hỗ trợ?  NoSuchPaddingException (Cipher)
        v
init(...)         --> Khóa không phù hợp?      InvalidKeyException
        |           --> Tham số không phù hợp? InvalidAlgorithmParameterException
        |           --> Kích thước/giá trị sai? InvalidParameterException (tùy API)
        v
update/doFinal    --> Giải mã AEAD không xác thực? AEADBadTagException
        |           --> Lỗi vận hành khác?      Các ngoại lệ API cụ thể
        v
Kết quả đã được kiểm tra
```

Ở `getInstance(...)`, JCA tìm **implementation của đúng loại engine** từ security provider. Cùng một chuỗi tên có thể hợp lệ với engine này nhưng vô nghĩa với engine khác: `SHA-256` là tên `MessageDigest`, `HmacSHA256` là tên `Mac`, `AES` là tên thuật toán cho `KeyGenerator`, còn `AES/GCM/NoPadding` là transformation của `Cipher`.

| Ngoại lệ | Checked? | Thường phát sinh tại | Ý nghĩa và cách xử lý |
| --- | --- | --- | --- |
| `NoSuchAlgorithmException` | Có | `getInstance(...)` | Không có implementation cho **engine + algorithm/transformation + provider** đang yêu cầu; kiểm tra tên, loại engine, phiên bản JDK và khả năng provider. |
| `NoSuchProviderException` | Có | `getInstance(algorithm, String providerName)` | Tên provider chỉ định **không được đăng ký** trong JVM; kiểm tra cấu hình/đăng ký provider trước khi chọn thuật toán khác. |
| `NoSuchPaddingException` | Có | `Cipher.getInstance(...)` | Padding của transformation không được hỗ trợ; rà lại toàn bộ `algorithm/mode/padding`, không tự đổi padding. |
| `InvalidKeyException` | Có | `Cipher.init(...)`, `Mac.init(...)`, `Signature.initSign/initVerify(...)` | Khóa sai loại, kích thước, encoding hoặc không được provider chấp nhận. |
| `InvalidAlgorithmParameterException` | Có | `Cipher.init(..., AlgorithmParameterSpec)`, `KeyPairGenerator.initialize(AlgorithmParameterSpec)` | Cấu trúc/tham số thuật toán không phù hợp (ví dụ thông số GCM hoặc nhóm/curve không được hỗ trợ). |
| `InvalidParameterException` | Không | Một số overload `initialize(int)`, `Cipher.init` với `opmode` sai | Giá trị tham số dạng số/chế độ không hợp lệ theo contract API; là `RuntimeException`. |
| `IllegalArgumentException` / `NullPointerException` | Không | Kiểm tra đầu vào và một số factory/constructor | Thường do đầu vào chương trình null, rỗng hoặc sai dạng; chi tiết phụ thuộc **overload**, không được suy luận rằng mọi API ném cùng loại. |
| `InvalidKeySpecException` | Có | `KeyFactory.generatePrivate/generatePublic`, `SecretKeyFactory.generateSecret` | Biểu diễn khóa (`KeySpec`) không phù hợp hoặc không thể chuyển thành khóa theo thuật toán. |
| `AEADBadTagException` | Có | `Cipher.doFinal(...)` khi giải mã AEAD | Xác thực bản tin thất bại: có thể do bản mã, khóa, IV/nonce, tag hoặc AAD không khớp. Không trả về plaintext, không bỏ xác thực để “cứu” dữ liệu. |
| `ProviderException` | Không | Bên trong provider | Lỗi runtime của provider/thiết bị; cần chẩn đoán môi trường, không coi là input validation thông thường. |

**Ghi nhớ:** `GeneralSecurityException` là lớp cha của nhiều checked exceptions ở trên, nhưng **không bao trùm** `InvalidParameterException`, `IllegalArgumentException` hoặc `ProviderException`. Bắt `GeneralSecurityException` quá sớm sẽ làm mất nhánh xử lý cụ thể; bắt `Exception` rồi im lặng sẽ che nguyên nhân.

## 2. Phân biệt chính xác “thiếu thuật toán” và “thiếu provider”

Lấy `MessageDigest` làm ví dụ:

```java
MessageDigest.getInstance("SHA-256");
// Tự tìm provider hỗ trợ MessageDigest/SHA-256 trong cấu hình JVM.

MessageDigest.getInstance("SHA-256", providerName);
// Yêu cầu đúng provider đã đăng ký có tên providerName.

MessageDigest.getInstance("SHA-256", providerObject);
// Dùng đối tượng Provider cụ thể; đối tượng này không nhất thiết đã đăng ký.
```

Ba overload trên có contract khác nhau:

1. **Không chỉ định provider:** `NoSuchAlgorithmException` khi không provider nào được phép/sẵn có cung cấp implementation tương ứng. Không có `NoSuchProviderException` vì không yêu cầu tên provider.
2. **Chỉ định tên `String`:** `NoSuchProviderException` khi tên không được đăng ký; `NoSuchAlgorithmException` khi provider có tồn tại nhưng không cung cấp `MessageDigest/SHA-256`. Với `MessageDigest.getInstance(String, String)`, tên provider null/rỗng gây `IllegalArgumentException`, thuật toán null gây `NullPointerException`.
3. **Chỉ định đối tượng `Provider`:** không có `NoSuchProviderException` trong chữ ký; một provider có thể được truyền trực tiếp dù chưa đăng ký. Nếu implementation thiếu, vẫn là `NoSuchAlgorithmException`; đối tượng provider null gây `IllegalArgumentException`.

Không nên kiểm tra provider bằng tên rồi kết luận rằng nó hỗ trợ mọi thuật toán. `Security.getProvider(name) != null` **chỉ chứng minh tên provider đã được đăng ký**; lời gọi `getInstance` của đúng engine mới là kiểm chứng khả năng thực tế. Với `Cipher`, còn cần kiểm chứng transformation và `init` vì việc lựa chọn implementation/provider có thể tiếp tục trong quá trình khởi tạo.

### 2.1 Lỗi thường bị nhầm

- `MessageDigest.getInstance("AES")` không đúng engine: `AES` là cipher/key algorithm, không phải digest.
- `Cipher.getInstance("SHA-256")` không đúng engine: `SHA-256` dùng để băm, không phải transformation mã hóa.
- `Cipher.getInstance("AES")` không mô tả mode và padding, nên có thể dùng mặc định phụ thuộc provider và không đạt yêu cầu an toàn. Hãy cấu hình transformation đầy đủ như `AES/GCM/NoPadding` khi phù hợp thiết kế.
- Tên provider như `SunJCE`, `SUN` hoặc provider HSM/PKCS#11 không được giả định luôn có trên mọi JVM. **Tên provider khác với tên thuật toán**.
- “Không có provider tên X” và “provider X có nhưng không hỗ trợ thuật toán Y” cần hai hướng sửa khác nhau.

## 3. Ví dụ chạy được: chẩn đoán lỗi lookup

Lưu đúng tên tệp **`JcaLookupDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.HexFormat;

public final class JcaLookupDemo {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        try {
            MessageDigest.getInstance("NO_SUCH_DIGEST_FOR_DEMO_2026");
        } catch (NoSuchAlgorithmException ex) {
            System.out.println("1. Khong co thuat toan MessageDigest");
        }

        String missingProvider = "TRAINING_PROVIDER_NOT_INSTALLED";
        while (Security.getProvider(missingProvider) != null) {
            missingProvider += "_";
        }

        try {
            MessageDigest.getInstance("SHA-256", missingProvider);
        } catch (NoSuchProviderException ex) {
            System.out.println("2. Provider chua duoc dang ky");
        }

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] bytes = sha256.digest("abc".getBytes(StandardCharsets.UTF_8));
        System.out.println("3. SHA-256(abc): " + HexFormat.of().formatHex(bytes));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 JcaLookupDemo.java
java JcaLookupDemo
```

Kết quả trên JDK Java 17+ thông thường (không có custom provider cố tình đăng ký tên thuật toán demo):

```text
1. Khong co thuat toan MessageDigest
2. Provider chua duoc dang ky
3. SHA-256(abc): ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

**Giải thích:**

- Lần gọi thứ nhất yêu cầu một thuật toán digest không được cài đặt. Ngoại lệ xuất hiện **trước** khi băm dữ liệu.
- Lần gọi thứ hai dùng thuật toán có sẵn `SHA-256` nhưng yêu cầu tên provider không được đăng ký; vòng `while` tránh trùng tên ngẫu nhiên với một provider hiện có.
- Lần cuối để JCA tự chọn provider hỗ trợ `SHA-256` và băm `abc`. `HexFormat` có từ Java 17 nên không cần thư viện ngoài.

Đây là minh họa để **phân biệt hai ngoại lệ**, không phải cơ chế thử tuần tự tên provider trong production. Ứng dụng nên cấu hình thuật toán đã kiểm duyệt và chỉ pin provider khi có yêu cầu kỹ thuật hoặc tuân thủ cụ thể.

## 4. “Lỗi tham số” phát sinh ở giai đoạn nào?

**(a) Tham số đầu vào của Java/API:** `null`, tên rỗng, chế độ hoạt động sai, kích thước âm. Nên kiểm tra sớm bằng `Objects.requireNonNull`, kiểm tra tập giá trị cho phép và độ dài, sau đó dùng lỗi lập trình/cấu hình phù hợp. Không dựa vào một loại ngoại lệ giống nhau cho mọi engine.

**(b) Tham số thuật toán:** `GCMParameterSpec`, `PBEParameterSpec`, `ECGenParameterSpec`, `AlgorithmParameterSpec` khác. Một object có thể tạo được nhưng vẫn bị provider từ chối tại `init`, thường bằng `InvalidAlgorithmParameterException`. Việc hỗ trợ cỡ tag, key, curve hoặc parameter set có thể phụ thuộc provider và security policy.

**(c) Khóa:** `SecretKey`, `PrivateKey`, `PublicKey` có thể sai thuật toán, độ dài hoặc không tương thích với thao tác. `InvalidKeyException` xuất hiện khi khởi tạo `Cipher`, `Mac`, `Signature`; không nên biến nó thành `NoSuchAlgorithmException` hoặc thử khóa khác một cách âm thầm. Nếu lỗi xảy ra lúc chuyển đổi `KeySpec`, hãy xem `InvalidKeySpecException`.

**(d) Bản tin đầu vào sau khởi tạo:** với authenticated encryption (AEAD) như AES-GCM, `AEADBadTagException` là **thất bại xác thực**, không đồng nghĩa provider bị thiếu và cũng không nhất thiết là “tham số đầu vào sai”. Tất cả khóa, nonce/IV, tag và AAD phải khớp giữa hai đầu.

Ví dụ ký hiệu:

```java
// Chọn transformation: lỗi lookup có thể là NoSuchAlgorithmException
// hoặc NoSuchPaddingException.
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

// Khởi tạo: key sai có thể là InvalidKeyException;
// thông số GCM không phù hợp có thể là InvalidAlgorithmParameterException.
cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));

// Khi giải mã GCM: doFinal(...) có thể ném AEADBadTagException
// nếu dữ liệu không xác thực. Đừng bỏ qua kiểm tra này.
```

Đoạn trên là **mã minh họa từng API**, chưa phải chương trình độc lập: cần định nghĩa `aesKey`, `iv` và ngữ cảnh gọi. Ví dụ chạy đầy đủ ở mục kế tiếp.

### 4.1 Checklist tham số an toàn cho AES-GCM

- Chỉ sử dụng khóa AES hợp lệ, thông thường **128/192/256 bit** theo chính sách. Không lấy mật khẩu thô hoặc chuỗi UTF-8 tùy ý làm khóa AES; dùng KDF thích hợp nếu xuất phát từ mật khẩu.
- Trong ví dụ, dùng IV dài **12 byte (96 bit)** và tag **128 bit**. Đó là cấu hình khuyến nghị phổ biến, không phải khẳng định mọi provider chấp nhận mọi tổ hợp kích thước.
- **Không bao giờ dùng lại cùng cặp (key, IV/nonce) khi mã hóa GCM.** Với lưu lượng lớn, cần thiết kế quản lý nonce để kiểm soát va chạm; chỉ tạo IV ngẫu nhiên trong ví dụ đơn giản không thay thế thiết kế đó.
- Có thể công khai IV cùng bản mã; IV không phải khóa bí mật. Tag thường nằm ở cuối byte array trả về từ `Cipher.doFinal` của GCM.
- AAD, nếu sử dụng, phải giống hệt lúc mã hóa/giải mã và được đưa qua `updateAAD(...)` **trước** phần dữ liệu.
- Không phát tán plaintext chưa được xác thực và không tự bỏ tag, đổi mode hay đổi thuật toán để qua lỗi `AEADBadTagException`.

## 5. Ví dụ chạy được: tách lỗi khởi tạo và lỗi xác thực AES-GCM

Ví dụ này dùng provider mặc định có hỗ trợ `AES/GCM/NoPadding`, đóng gói `IV(12 byte) || ciphertext+tag`, mã hóa/giải mã thành công và cố ý sửa tag để thấy nhánh lỗi. Lưu đúng tên **`GcmFailureDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class GcmFailureDemo {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    public static void main(String[] args) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey key = generator.generateKey();

            byte[] payload = encrypt("Hello JCA".getBytes(StandardCharsets.UTF_8), key);
            byte[] recovered = decrypt(payload, key);
            System.out.println("Giai ma: " + new String(recovered, StandardCharsets.UTF_8));

            byte[] tampered = payload.clone();
            tampered[tampered.length - 1] ^= 1; // Sua mot bit cua tag.
            try {
                decrypt(tampered, key);
                throw new AssertionError("Tag da sua ma van duoc chap nhan");
            } catch (AEADBadTagException ex) {
                System.out.println("Tag bi sua: xac thuc that bai");
            }

        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
            throw new IllegalStateException("Algorithm/transformation khong kha dung", ex);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException ex) {
            throw new IllegalArgumentException("Key hoac tham so khong hop le", ex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Loi xu ly mat ma", ex);
        }
    }

    private static byte[] encrypt(byte[] plain, SecretKey key)
            throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertextWithTag = cipher.doFinal(plain);

        byte[] payload = new byte[IV_BYTES + ciphertextWithTag.length];
        System.arraycopy(iv, 0, payload, 0, IV_BYTES);
        System.arraycopy(ciphertextWithTag, 0, payload, IV_BYTES,
                ciphertextWithTag.length);
        return payload;
    }

    private static byte[] decrypt(byte[] payload, SecretKey key)
            throws GeneralSecurityException {
        if (payload == null || payload.length < IV_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Payload thieu IV hoac tag");
        }

        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] ciphertextWithTag = Arrays.copyOfRange(payload, IV_BYTES,
                payload.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(ciphertextWithTag);
    }
}
```

```bash
javac -encoding UTF-8 GcmFailureDemo.java
java GcmFailureDemo
```

Kết quả với provider JDK thông thường hỗ trợ AES-GCM:

```text
Giai ma: Hello JCA
Tag bi sua: xac thuc that bai
```

Ý nghĩa các nhánh:

- `NoSuchAlgorithmException` / `NoSuchPaddingException`: engine hoặc transformation không khả dụng ở môi trường này. Đây thường là lỗi **khả năng/cấu hình**, nên phát hiện khi khởi động.
- `InvalidKeyException` / `InvalidAlgorithmParameterException`: kiểm tra nguồn khóa, loại khóa, size, transformation, IV/tag/AAD và provider. **Không** chuyển sang cipher/mode yếu hơn.
- `AEADBadTagException`: xử lý như **bản tin không xác thực**, trả thông báo thích hợp cho bên gọi nhưng không tiết lộ khóa, plaintext hoặc chi tiết mật mã nhạy cảm.
- `IllegalArgumentException` do payload thiếu cấu trúc hợp lệ là lỗi **kiểm tra đầu vào của chương trình**; nó khác với ngoại lệ checked do provider ném.
- `GeneralSecurityException` cuối cùng chỉ dành cho các trường hợp chưa được phân loại; vẫn phải giữ `cause` để điều tra trong hệ thống tin cậy.

Việc sửa một bit trong tag tạo lỗi xác thực trên provider GCM bình thường; nguyên tắc an toàn là **từ chối** dữ liệu khi kiểm tra tag thất bại. Ví dụ không hướng dẫn lưu khóa lâu dài, xoay khóa hoặc format dữ liệu liên phiên bản; các phần đó cần thiết kế riêng trong hệ thống thực tế.

## 6. Chiến lược xử lý lỗi đúng trong ứng dụng

### 6.1 Phân loại theo nguyên nhân và thời điểm

| Nhóm | Ví dụ | Hành động khuyến nghị |
| --- | --- | --- |
| **Cấu hình/khả năng không hợp lệ** | Sai algorithm, sai transformation, provider chưa cài, thiếu HSM provider | Kiểm tra ngay lúc startup; ghi log đã lọc thông tin về engine, tên thuật toán, tên provider và môi trường; **fail fast** thay vì vận hành âm thầm với thuật toán khác. |
| **Dữ liệu/khóa/tham số không hợp lệ** | Key sai độ dài, `AlgorithmParameterSpec` không được chấp nhận, payload thiếu IV/tag | Kiểm tra nguồn dữ liệu; phân biệt lỗi client với lỗi cấu hình nội bộ; xử lý theo contract của API. |
| **Xác thực thất bại** | `AEADBadTagException` | Từ chối bản tin; không trả plaintext; không thử chế độ không xác thực để khôi phục. |
| **Provider/runtime lỗi** | `ProviderException`, lỗi thiết bị mật mã, khả năng provider thay đổi | Thu thập telemetry có kiểm soát, cảnh báo và điều tra môi trường; chỉ retry khi operation thực sự an toàn/idempotent và chính sách cho phép. |

Hãy xác định rõ **ai sở hữu cấu hình** trước khi quyết định phản hồi. Nếu ứng dụng cấu hình `AES/GCM/NoPadding` cố định mà JVM không hỗ trợ, lỗi đó thường phải được sửa ở triển khai, không được quy thành lỗi nhập liệu của người dùng. Nếu API nhận payload từ client và thiếu IV, ứng dụng có thể từ chối đầu vào theo contract. Với payload mã hóa sai tag, không tiết lộ chi tiết đủ để tạo oracle phân biệt nhiều dạng thất bại.

### 6.2 Mẫu kiểm tra cấu hình lúc khởi động

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.Set;

public final class DigestFactory {
    private static final Set<String> ALLOWED = Set.of("SHA-256", "SHA-384", "SHA-512");

    public static MessageDigest create(String algorithm, String providerName)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (algorithm == null || !ALLOWED.contains(algorithm)) {
            throw new IllegalArgumentException("Digest algorithm ngoai allowlist");
        }

        // null/rong mang nghia la de JCA tu chon provider.
        if (providerName == null || providerName.isBlank()) {
            return MessageDigest.getInstance(algorithm);
        }

        if (Security.getProvider(providerName) == null) {
            throw new NoSuchProviderException("Provider khong duoc dang ky: "
                    + providerName);
        }
        return MessageDigest.getInstance(algorithm, providerName);
    }
}
```

Đây là **lớp hỗ trợ biên dịch độc lập nhưng không có `main`**: gọi `DigestFactory.create(...)` ở bước khởi tạo cấu hình ứng dụng. Tên thuật toán được giới hạn bằng allowlist; `providerName` chỉ là tùy chọn khi chính sách cho phép chạy độc lập provider. Nếu chính sách **bắt buộc HSM/provider cụ thể**, không được đổi sang provider mặc định khi gặp `NoSuchProviderException`.

Không nên dùng dữ liệu request không đáng tin cậy để quyết định thuật toán/provider. Nếu cần hỗ trợ migration, hãy thiết kế **phiên bản định dạng + allowlist thuật toán đã phê duyệt**; chỉ chuyển giữa các cấu hình tương đương đáp ứng chính sách, không fallback từ thuật toán hiện đại sang MD5, SHA-1 cho chức năng bảo mật, DES, ECB hoặc mode không xác thực.

### 6.3 Chẩn đoán có thứ tự

1. **Xác định API vừa ném lỗi:** `MessageDigest`, `Cipher`, `KeyPairGenerator` hay `Signature`? Ghi nhận đúng `getInstance`, `init`, `doFinal` thay vì đoán chỉ từ thông điệp ngoại lệ.
2. **Kiểm tra tên chính xác và loại engine:** `SHA-256` khác `HmacSHA256`; `AES/GCM/NoPadding` là transformation của `Cipher`; không chỉ sửa chữ hoa/thường theo cảm tính.
3. **Nếu chỉ định provider theo tên:** `Security.getProvider(providerName)` có `null` không? Nếu có provider, gọi đúng `getInstance` để xác minh dịch vụ/thuật toán. Có thể liệt kê `Security.getProviders()` và `Security.getAlgorithms("MessageDigest")` để khám phá môi trường, nhưng danh sách không thay thế việc khởi tạo thực tế.
4. **Xác nhận runtime:** version/vendor JDK, security properties, provider priority, module/dependency của provider ngoài JDK, HSM/PKCS#11 và khác biệt dev/test/prod. Một implementation có trên laptop không bảo đảm có trong container production.
5. **Nếu lỗi tại `init`:** so sánh thuật toán của `Key`, kích thước khóa, mode, `AlgorithmParameterSpec`, tag/IV/nonce, policy và API overload đã dùng.
6. **Nếu lỗi tại `doFinal`:** kiểm tra định dạng payload, tính toàn vẹn và AAD. Với AEAD, phân loại thất bại xác thực; không ghi khóa, plaintext hay vật liệu mật mã nhạy cảm vào log.
7. **Ghi nguyên nhân có kiểm soát:** ở log nội bộ lưu loại ngoại lệ, vị trí giai đoạn, thuật toán/provider không nhạy cảm, mã tương quan và `cause`; phản hồi công khai dùng thông báo an toàn, không đưa stack trace/provider internals ra client.

Chẩn đoán dưới đây chỉ liệt kê **tên** provider/thuật toán, không xuất vật liệu mật mã. Chạy trong môi trường tin cậy khi cần:

```java
import java.security.Provider;
import java.security.Security;

public class JcaEnvironmentInfo {
    public static void main(String[] args) {
        for (Provider provider : Security.getProviders()) {
            System.out.println("Provider: " + provider.getName());
        }
        System.out.println("Digest algorithms: "
                + Security.getAlgorithms("MessageDigest"));
    }
}
```

Kết quả **phụ thuộc JVM** nên không có một stdout cố định. Không dùng danh sách này để chọn ngẫu nhiên “thuật toán đầu tiên chạy được”.

## 7. Anti-pattern và cách sửa

| Cách làm sai | Vì sao nguy hiểm/khó debug | Cách thay thế |
| --- | --- | --- |
| `catch (Exception e) { return null; }` | Nuốt nguyên nhân, tạo `NullPointerException` ở xa nguồn. | Phân loại ngoại lệ, giữ cause, trả/throw lỗi mang ngữ cảnh. |
| Gặp `NoSuchAlgorithmException` thì thử `MD5`, `SHA-1`, `DES` hoặc `AES/ECB/...`. | Hạ tiêu chuẩn bảo mật âm thầm; có thể thay đổi tính chất dữ liệu. | Kiểm tra cấu hình, dependency và provider; fail fast hoặc migration theo chính sách đã phê duyệt. |
| Bắt `NoSuchProviderException` rồi bỏ tên provider và retry mặc định. | Có thể chuyển thao tác ra ngoài HSM/provider bắt buộc. | Chỉ được fallback provider khi chính sách cho phép một cách rõ ràng; nếu không, dừng và báo lỗi triển khai. |
| Gộp `InvalidKeyException`, `AEADBadTagException`, `NoSuchAlgorithmException` thành “sai mật khẩu”. | Che lỗi hệ thống, rò rỉ logic và làm sai vận hành. | Tách nhóm cấu hình, tham số/khóa và xác thực thất bại. |
| Truyền nguyên chuỗi tự do từ request vào `Cipher.getInstance`. | Người gọi có thể quyết định thuật toán/mode ngoài chính sách. | Cố định cấu hình hoặc ánh xạ mã phiên bản sang allowlist. |
| Dùng lại IV với cùng key GCM hoặc bỏ qua `AEADBadTagException`. | Gây tổn hại tính bảo mật/toàn vẹn dữ liệu. | Bảo đảm nonce duy nhất; từ chối bản tin không xác thực. |
| Ghi key, plaintext, raw secret, stack trace đầy đủ ra log công khai. | Rò rỉ dữ liệu nhạy cảm. | Ghi mã lỗi/correlation ID và thông tin cấu hình phi bí mật ở log được bảo vệ. |

## 8. Tự kiểm tra hiểu bài

**Tình huống A.** `MessageDigest.getInstance("AES")` ném `NoSuchAlgorithmException`. Có nên cài thêm provider ngay?  
**Giải:** Trước hết xem lại **engine**; `AES` không phải digest. Dùng `MessageDigest` cho `SHA-256` hoặc engine phù hợp với mục tiêu thực tế. Không mặc định là thiếu provider.

**Tình huống B.** `MessageDigest.getInstance("SHA-256", "MyHsm")` ném `NoSuchProviderException`.  
**Giải:** Tên `MyHsm` chưa được đăng ký trong JVM. Kiểm tra cài đặt/khởi tạo provider, tên và cấu hình runtime. Nếu quy định dùng HSM, dừng thay vì tự đổi provider.

**Tình huống C.** Provider có tên `MyHsm`, nhưng cùng câu lệnh ném `NoSuchAlgorithmException`.  
**Giải:** Provider đã tồn tại nhưng không cung cấp implementation `MessageDigest/SHA-256` theo yêu cầu. Kiểm tra đúng engine, thuật toán, provider capabilities và policy.

**Tình huống D.** `Cipher.getInstance("AES/GCM/NoPadding")` thành công, nhưng `init` thất bại.  
**Giải:** Đây là lúc xét `InvalidKeyException` hoặc `InvalidAlgorithmParameterException`, chứ không mặc nhiên kết luận thiếu thuật toán. Kiểm tra key, tag/nonce và contract provider.

**Tình huống E.** `Cipher.doFinal` lúc giải mã GCM ném `AEADBadTagException`.  
**Giải:** Thất bại xác thực; không trả plaintext và không dùng mode bỏ xác thực. Điều tra nguồn bản mã, key, IV/nonce, tag, AAD và định dạng dữ liệu.

### Tiêu chí hoàn thành

Người học có thể: (1) chỉ ra đúng giai đoạn ném lỗi; (2) phân biệt ba overload `getInstance` với provider; (3) tách `InvalidKeyException`, `InvalidAlgorithmParameterException`, `InvalidParameterException` và lỗi xác thực; (4) khởi tạo JCA/JCE với cấu hình rõ ràng và **không fallback hạ cấp bảo mật**; (5) tái hiện, chẩn đoán, báo lỗi có giữ nguyên nhân mà không lộ secret.

## Tài liệu chính thức

- [Java SE 21 — MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html): ba overload `getInstance` và các ngoại lệ.
- [Java SE 21 — NoSuchProviderException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/NoSuchProviderException.html): định nghĩa provider được yêu cầu nhưng không khả dụng.
- [Java SE 21 — Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html): transformation, `getInstance`, `init`, GCM/AEAD và `doFinal`.
- [Java SE 21 — KeyPairGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyPairGenerator.html): `InvalidParameterException` với keysize và `InvalidAlgorithmParameterException` với `AlgorithmParameterSpec`.
- [Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): tên thuật toán/engine tiêu chuẩn và các transformation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jca-architecture-009">2.9 Tính tương thích và trạng thái thay đổi khi dùng dịch vụ mật mã đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# 2.9 Tính tương thích và trạng thái thay đổi khi dùng dịch vụ mật mã đồng thời

## 1. Vì sao hai vấn đề này phải được xem xét cùng nhau?

Một ứng dụng Java có thể thực hiện hàng nghìn tác vụ mật mã đồng thời: băm nội dung tải lên, xác minh chữ ký webhook, tính HMAC, mã hóa dữ liệu người dùng, hoặc giải mã các thông điệp đến từ dịch vụ khác. Những tác vụ có thể chạy trên nhiều thread của web server, thread pool, tác vụ bất đồng bộ hoặc nhiều instance ứng dụng.

Để kết quả chính xác và an toàn, cần đồng thời giải quyết **hai nhóm vấn đề độc lập nhưng liên quan**:

1. **Tính tương thích (interoperability / compatibility):** Bên tạo và bên tiêu thụ dữ liệu mật mã có thống nhất thuật toán, tham số, định dạng byte, biểu diễn khóa và phiên bản giao thức không? Các Java Security Provider khác nhau có cùng hỗ trợ những lựa chọn đó không?
2. **Trạng thái và tính đồng thời (state / concurrency):** Một đối tượng JCA đang giữ dữ liệu từ thao tác trước, khóa nào, chế độ ký/xác minh hay mã hóa/giải mã nào? Nếu nhiều thread cùng gọi update, init hoặc doFinal trên đối tượng đó, liệu một yêu cầu có lẫn dữ liệu/trạng thái của yêu cầu khác không?

**Mấu chốt:** “Cùng tên thuật toán” chưa đủ bảo đảm tương thích giữa hai hệ thống; “cùng dùng một đối tượng Java” cũng chưa bảo đảm được phép gọi đồng thời.

### Mục tiêu sau bài

Sau khi học, bạn có thể:

- Phân biệt **engine API**, **provider implementation** và **trạng thái riêng của engine instance** trong JCA.
- Xác định trạng thái thay đổi ở MessageDigest, Mac, Signature, Cipher và ý nghĩa của init, update, digest/sign/verify/doFinal, reset.
- Chọn phạm vi sở hữu đối tượng mật mã an toàn khi chạy đa luồng.
- Nhận diện lỗi do provider/default, tham số, encoding, định dạng khóa và nonce/IV bị dùng lại.
- Thiết kế và kiểm thử hợp đồng byte/thuật toán để nhiều ứng dụng, phiên bản hoặc provider tương tác được với nhau.


## 2. Tách cấu hình provider khỏi trạng thái của mỗi phép toán

Trong JCA/JCE, `getInstance` tạo engine (ví dụ `Cipher`, `Mac`, `Signature`) thông qua implementation của provider. **Provider registry** mô tả khả năng/cấu hình ở mức JVM, còn **engine instance** giữ khóa, mode, tham số và dữ liệu đang xử lý của **một phép toán cụ thể**. Cùng một provider có thể phục vụ nhiều engine độc lập; chia sẻ một engine instance đang xử lý dữ liệu giữa hai request lại là vấn đề khác hẳn.

| Ranh giới | Được chia sẻ khi có chính sách? | Rủi ro nếu nhầm |
| --- | --- | --- |
| Tên thuật toán, cấu hình bất biến, provider đã đăng ký | Có | Cấu hình thay đổi giữa các môi trường gây khác biệt |
| Đối tượng khóa/handle | Có điều kiện theo provider và quyền sử dụng | Lộ khóa, không tương thích HSM hoặc thay khóa giữa chừng |
| `MessageDigest`, `Mac`, `Cipher`, `Signature` đang xử lý | Không mặc định; nên sở hữu theo tác vụ | Xen `init/update/final` từ request khác, gây kết quả sai hoặc rò trạng thái |
| `SecureRandom` | API cho phép dùng đồng thời | Vẫn cần đo contention/entropy tùy provider |

Vì vậy kiểm tra **tương thích giao thức** và **quyền sở hữu state** phải song hành: đúng thuật toán/byte format không cứu được một phép tính bị thread khác xen vào; tách engine theo request cũng không cứu được hai bên không thống nhất IV, AAD, key hay định dạng chữ ký.

## 3. “Tương thích” thực sự bao gồm những lớp nào?

| Lớp | Cần thống nhất | Ví dụ không tương thích |
| --- | --- | --- |
| **API/khả năng hỗ trợ** | Provider có thuật toán và operation cần thiết; JDK/thư viện/hardware có hỗ trợ | JVM A có AES/GCM, provider B không hỗ trợ cấu hình hoặc kích thước khóa tương ứng. |
| **Thuật toán và mode** | Tên/biến thể/thuật toán ký, mã hóa, MAC, digest | Cùng khóa AES nhưng một bên dùng GCM, bên kia dùng CBC; hoặc một bên ký RSASSA-PSS, bên kia xác minh SHA256withRSA/PKCS#1 v1.5. |
| **Tham số mật mã** | Nonce/IV, tag length, salt, iteration count, padding, hash/MGF và tham số phù hợp | Một bên dùng tag GCM 128 bit, bên kia diễn giải 96 bit; RSA-PSS có salt length khác. |
| **Byte input** | UTF-8, thứ tự trường, canonical serialization, AAD, dấu xuống dòng, các byte được ký | Hai bên tưởng ký “cùng JSON” nhưng khoảng trắng, thứ tự key hoặc Unicode encoding khác nhau. |
| **Wire format** | Cách đóng gói version, key ID, IV, ciphertext, tag, signature; Base64/hex nếu có | Một bên gửi ciphertext||tag, bên kia mong tag ở trường riêng; đảo vị trí IV. |
| **Key format / trust** | Thuật toán, mục đích dùng, cách import public/private/secret key, nguồn tin cậy | Public key ở X.509 SubjectPublicKeyInfo nhưng bên nhận đọc như PKCS#8 private key. |
| **Vòng đời và phiên bản** | Phiên bản thông điệp, key rotation, thời gian hiệu lực, chính sách chấp nhận thuật toán | Bản ghi cũ thiếu key ID khi ứng dụng đã xoay khóa. |
| **Môi trường thực thi** | Provider, chính sách/FIPS, token/hardware, khả năng vận hành | HSM không cho xuất private key, hoặc một provider không cho dùng khóa thuộc provider khác. |

**Tương thích chức năng** nghĩa là có thể giải mã/xác minh một cách nhất quán khi dùng cùng giao thức; **an toàn bảo mật** còn đòi hỏi thuật toán, tham số, nguồn khóa và quy tắc sử dụng đúng. Một thiết kế dùng ECB có thể “tương thích” giữa hai provider nhưng không đạt yêu cầu bảo mật dữ liệu ứng dụng.

### Quy tắc thực hành

- Yêu cầu transformation rõ ràng, chẳng hạn **AES/GCM/NoPadding**, thay vì chỉ **AES** rồi phụ thuộc mode/padding mặc định của provider.
- Xác định thuật toán ký kèm tham số chính xác. Với **RSASSA-PSS** phải thống nhất hash, MGF, salt length và trailer field; với **RSA-OAEP** phải thống nhất các tham số OAEP được giao thức sử dụng.
- Tách **định dạng truyền/ghi** khỏi đối tượng engine. Không cố lưu đối tượng Cipher/Signature đang chạy hoặc dựa vào trạng thái nội bộ của provider như một định dạng trao đổi.
- Quy định encoding, field ordering, framing/version và byte sequence trước khi ký hoặc MAC. JSON object bằng nhau về ý nghĩa không mặc nhiên giống nhau ở mức byte.
- Chấp nhận có điều kiện: **getInstance thành công chưa chứng minh init/doFinal sẽ thành công** với khóa, tag/IV hay tham số cụ thể. Cần kiểm thử chính tổ hợp triển khai.


## 4. Đơn vị an toàn là cả chuỗi thao tác, không phải từng method call

Phần vòng đời riêng của từng engine đã có bài giải thích chi tiết. Ở góc nhìn **đa luồng**, điều quan trọng hơn là một **transaction mật mã** phải giữ nguyên quyền sở hữu engine từ lúc khởi tạo cho đến khi có kết quả cuối cùng. Ngay cả khi `doFinal` hoặc `digest` tự reset state, chúng **không tự khóa toàn bộ chuỗi thao tác** để bảo vệ trước thread khác.

| Engine | State mà các lời gọi cùng sửa | Race khi dùng chung một instance | Cách sở hữu an toàn |
| --- | --- | --- | --- |
| `MessageDigest` | Các byte đã `update`, digest state | Băm nhầm phần dữ liệu của request khác; thread kia `digest/reset` trước | Một instance trên mỗi phép băm |
| `Mac` | Key đã `init`, các byte đang tính MAC | Xen `update/doFinal` tạo MAC sai dù cùng key | Một instance theo message; có thể dùng lại **tuần tự** khi API reset |
| `Signature` | Chế độ `initSign/initVerify`, key, message pending | Request khác đổi mode/key hoặc thêm bytes trước khi `sign/verify` | Một instance theo chữ ký |
| `Cipher` | Encrypt/decrypt mode, key, IV, AAD, buffer, authentication state | Key/IV bị ghi đè, plaintext/ciphertext xen kẽ; lỗi GCM nghiêm trọng | Một instance theo bản tin, IV mới cho mỗi lần **mã hóa** GCM cùng key |
| `KeyAgreement` | Private key và pha thỏa thuận đang diễn ra | Peer public key của hai phiên bị trộn | Một instance theo phiên thỏa thuận |
| `SecureRandom` | PRNG state thuộc implementation | **Ngoại lệ được API quy định:** đối tượng có thể gọi đồng thời | Có thể dùng chung sau khi kiểm tra performance/provider |

### 4.1. Vì sao `synchronized` từng lời gọi vẫn chưa đủ?

Giả sử A chạy `mac.update(a)`, sau đó B chạy `mac.update(b)`, cuối cùng A gọi `mac.doFinal()`. **Dù từng phương thức riêng lẻ được khóa**, giá trị A tính có thể là HMAC của `a || b`, không phải `a`. Nếu thật sự dùng một instance chung, phải khóa cả chuỗi `init → update → doFinal` như một giao dịch; đơn giản hơn thường là tạo instance riêng.

Với GCM, còn có invariant **không trùng (key, IV) giữa các lần mã hóa**. Một race trong bộ cấp nonce có thể gây lỗi mật mã ngay cả khi từng Cipher instance hoàn toàn độc lập; vấn đề cấp IV sẽ được kiểm thử ở mục 8. Với AEAD giải mã, không phát tán plaintext chưa kiểm chứng tag.

### 4.2. Vì sao `SecureRandom` là ngoại lệ?

Java quy định `SecureRandom` an toàn cho nhiều thread, bằng khả năng thread-safe của SPI hoặc đồng bộ truy cập do lớp bao quản lý. Tuy nhiên provider có thể chờ entropy/reseed hoặc có contention. Cam kết này **không lan sang** `MessageDigest`, `Mac`, `Signature`, `Cipher` hoặc `KeyAgreement`; các engine ấy cần policy sở hữu riêng.

## 5. Các tình huống lỗi đồng thời điển hình

### 5.1. Đặt engine stateful vào static field hoặc singleton

Một service trong web framework thường là singleton, nghĩa là nhiều request cùng gọi vào một instance service. Nếu service giữ Cipher, Mac hoặc MessageDigest như **field dùng chung**, các method update/init/doFinal tác động lên cùng trạng thái. Dưới tải nhẹ chương trình có thể trông bình thường; dưới tải cao sinh kết quả sai, exception ngẫu nhiên hoặc nguy cơ rò rỉ dữ liệu giữa các request.

**Thiết kế tốt hơn:** một lời gọi xử lý request tạo/khởi tạo engine độc lập, dùng từ đầu đến cuối, rồi loại bỏ; chỉ chia sẻ những cấu hình bất biến, tài liệu key ID và key handle có contract dùng đồng thời phù hợp.

### 5.2. Khóa từng phương thức nhưng quên toàn bộ giao dịch

Nếu chỉ synchronized(update), synchronized(doFinal) riêng lẻ, thread khác vẫn xen vào giữa hai lời gọi. Để chia sẻ một engine stateful bằng khóa, toàn bộ chuỗi thao tác phải được tuần tự hóa. Cách này khó bảo trì, dễ deadlock và làm mất lợi ích xử lý song song. Instance riêng theo tác vụ thường đơn giản hơn.

### 5.3. Dùng ThreadLocal hoặc object pool mà quên reset

**ThreadLocal** có thể giữ một engine riêng cho mỗi thread nhưng không tự giải quyết việc tái sử dụng qua các request trên cùng thread pool. Phải init/reset đúng, tránh tồn đọng context người dùng trước; cân nhắc remove khi không còn cần và xử lý vòng đời thread. Object pool còn cần quy trình checkout → khởi tạo → xử lý → xử lý lỗi → trả lại, kiểm tra loại provider, khóa và tham số.

Riêng **AES-GCM**, tái sử dụng một Cipher trong ThreadLocal không tự tạo nonce mới; **phải thay IV và khởi tạo lại trước mỗi lần mã hóa**. Không vì muốn tối ưu mà giữ IV của request trước.

### 5.4. Chia sẻ buffer mutable

Dù mỗi thread có engine riêng, vẫn có thể gây lỗi nếu cùng thao tác trên một **ByteBuffer**, byte[] đang bị ghi, hoặc tập tham số mutable. Một task sửa input sau khi task khác bắt đầu ký/HMAC có thể khiến chữ ký không khớp. Chỉ chia sẻ dữ liệu thực sự không thay đổi, chuyển quyền sở hữu buffer rõ ràng, hoặc tạo bản sao khi cần.

### 5.5. Nonce bị lặp ở nhiều thread hoặc nhiều instance ứng dụng

Nếu hai thread lấy cùng giá trị counter vì read/modify/write không đồng bộ, chúng có thể dùng trùng IV. Dùng **AtomicLong** hoặc một bộ cấp số duy nhất giúp tránh lặp **trong phạm vi mà bộ cấp số đó thực sự quản lý**. Điều này **không đủ** nếu nhiều JVM/container có cùng khóa nhưng mỗi nơi lại bắt đầu counter từ 0.

Một chiến lược IV/nonce cho production phải được thiết kế **theo từng khóa** và bao phủ restart, nhiều node, backup/restore và lượng message dự kiến. Nếu dùng IV ngẫu nhiên 96 bit, cần đánh giá xác suất va chạm và giới hạn lượng message theo chính sách thuật toán; không gọi xác suất nhỏ là bảo đảm tuyệt đối. Không dùng timestamp đơn thuần, Random, hay IV cố định như một cơ chế cấp nonce an toàn.

### 5.6. “Trùng kết quả” do dữ liệu và provider không thống nhất

Ngay cả khi hoàn toàn không có race, hai hệ thống có thể tạo ra chữ ký/MAC khác nhau do charset, thứ tự field, cách chuẩn hóa JSON, salt length, tag length hoặc thuật toán biến thể khác. **Đừng quy mọi lỗi BadPaddingException, AEADBadTagException hay Signature.verify(false) cho concurrency.** Trước tiên cần xác minh đúng giao thức byte và key ID, sau đó mới cô lập lỗi đồng thời.

## 6. Mô hình sở hữu an toàn: cấu hình chung, engine theo tác vụ

| Thành phần | Có thể chia sẻ? | Điều kiện |
| --- | --- | --- |
| String tên thuật toán/transformation, tham số cấu hình bất biến | Có | Được định nghĩa rõ và không sửa giữa một giao dịch. |
| SecretKey/PublicKey/PrivateKey | Có điều kiện | Key object/handle và provider hỗ trợ cách dùng đang chọn; kiểm soát quyền và vòng đời khóa; không sửa byte nguồn/chia sẻ dữ liệu private key bừa bãi. |
| SecureRandom | Có | API bảo đảm thread-safe; đo throughput/latency của provider cụ thể. |
| MessageDigest, Mac, Signature, Cipher dùng cho các request độc lập | **Nên tách** | Tạo engine riêng cho mỗi thao tác để không chia sẻ trạng thái. |
| Counter cấp IV, trạng thái phiên bản khóa | Có với đồng bộ đúng phạm vi | Phải bảo đảm uniqueness/consistency xuyên các worker/process nếu cùng khóa. |
| Byte array chứa plaintext/key/material | Cần thận trọng | Có thể bị sửa; kiểm soát quyền sở hữu, sao chép và xóa khi thích hợp. |
| Provider configuration / Security provider order | Là cấu hình runtime | Xác định lúc khởi động và coi là hợp đồng triển khai; không sửa provider list liên tục theo request. |

**Mẫu áp dụng cho handler đa luồng:**

1. Xác định rõ operation, algorithm, key ID/version, nonce/AAD và input bytes của request.
2. Tạo engine mới với **getInstance** (và provider nếu thực sự cần pin).
3. Khởi tạo bằng khóa và tham số đúng cho request.
4. Chạy toàn bộ update/finalize trong **cùng task/thread sở hữu engine**; không công bố engine ra singleton.
5. Trả về **bytes kết quả kèm metadata định dạng**, không trả đối tượng Cipher/Mac/Signature.
6. Xử lý exception, không tiếp tục dùng trạng thái lỗi; giải phóng tài nguyên/đóng session khi API/provider yêu cầu.
7. Bảo vệ key lifecycle, kiểm tra authorization và lưu vết phù hợp ở lớp ứng dụng.

Thiết kế per-operation thường là điểm xuất phát an toàn. **Chỉ tối ưu bằng reuse khi đã đo hiệu năng, kiểm chứng contract provider và kiểm soát lifecycle hoàn chỉnh.**

## 7. Ví dụ Java 17+: nhiều tác vụ băm SHA-256 và HMAC chạy song song

Chương trình tạo **32 tác vụ** trong thread pool. Mỗi tác vụ dùng **MessageDigest riêng** và **Mac riêng**, rồi so với hai test vector chuẩn (input và expected output được cố định). Phần cuối kiểm chứng việc digest() và Mac.doFinal() reset state khi tái sử dụng **tuần tự**.

Tạo file **ConcurrentJcaStateDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ConcurrentJcaStateDemo {
    private static final byte[] HASH = HexFormat.of().parseHex(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    private static final byte[] HMAC = HexFormat.of().parseHex(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");

    private static boolean calculate() throws Exception {
        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        boolean hashOk = Arrays.equals(HASH, digest.digest(abc));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "key".getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        byte[] data = "The quick brown fox jumps over the lazy dog"
                .getBytes(StandardCharsets.US_ASCII);
        boolean macOk = Arrays.equals(HMAC, mac.doFinal(data));
        return hashOk && macOk;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(pool.submit(() -> calculate()));
            }
            int ok = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    ok++;
                }
            }
            System.out.println("32 tác vụ đúng: " + ok + "/32");
        } finally {
            pool.shutdown();
        }

        Mac sequential = Mac.getInstance("HmacSHA256");
        sequential.init(new SecretKeySpec(
                "key".getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        byte[] bytes = "abc".getBytes(StandardCharsets.US_ASCII);
        System.out.println("Mac doFinal đặt lại trạng thái: " +
                Arrays.equals(sequential.doFinal(bytes), sequential.doFinal(bytes)));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        System.out.println("MessageDigest digest đặt lại trạng thái: " +
                Arrays.equals(digest.digest(bytes), digest.digest(bytes)));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 --release 17 ConcurrentJcaStateDemo.java
java ConcurrentJcaStateDemo
~~~

Kết quả:

~~~text
32 tác vụ đúng: 32/32
Mac doFinal đặt lại trạng thái: true
MessageDigest digest đặt lại trạng thái: true
~~~

### Điều ví dụ chứng minh

1. **Mỗi lần calculate() tạo engine riêng** nên các lệnh update/digest/doFinal của các tác vụ không dùng chung trạng thái của một engine.
2. **Test vector xác nhận kết quả bytes**, thay vì chỉ so hai lần tính có thể cùng sai vì một lỗi cài đặt khác.
3. **Cùng một Mac hoặc MessageDigest có thể được dùng lại tuần tự** sau doFinal/digest theo contract của API. Điều này không nói rằng chia sẻ đúng instance đó giữa các task là an toàn.
4. Chuỗi **"key"** chỉ là test vector công khai, **không phải bí mật production**. Trên hệ thống thật, khóa HMAC phải có entropy đủ, được lưu và cấp quyền đúng.
5. Số 32/32 chỉ cho biết phép chạy ví dụ này thành công; không thể dùng một lần chạy để “chứng minh” mọi thao tác, mọi provider hay mọi trường hợp race đều an toàn.

Trong ví dụ, ta cố ý **không** tạo một static MessageDigest/Mac dùng chung. Nếu đặt chúng trong một singleton rồi gọi từ nhiều thread, tính đúng đắn không còn được bảo đảm bởi contract chung.

## 8. Ví dụ Java 17+: AES-GCM đa luồng và IV không bị lặp trong phạm vi ví dụ

Đây là trường hợp quan trọng hơn vì lỗi concurrency có thể biến thành lỗ hổng mật mã dù kết quả giải mã đôi khi trông đúng.

Chương trình tạo **khóa AES mới cho một lần chạy**, dùng một **prefix 4 byte** tạo ngẫu nhiên và một **AtomicLong 8 byte** cấp giá trị khác nhau cho từng message trong cùng JVM. Mỗi task tạo **Cipher riêng**, dùng IV 12 byte mới, AAD cố định, mã hóa rồi giải mã cùng message. Sau đó thử sửa tag để kiểm tra thao tác giải mã bị từ chối.

Mục tiêu của cách cấp IV ở đây chỉ là minh họa **không trùng IV trong một lần chạy với một khóa vừa tạo**. Đây **không phải thuật toán cấp IV production cho khóa được dùng lại qua nhiều lần restart hoặc nhiều node**.

Tạo file **ConcurrentGcmDemo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class ConcurrentGcmDemo {
    private static final byte[] AAD =
            "orders:v1".getBytes(StandardCharsets.UTF_8);

    private record Envelope(byte[] iv, byte[] ciphertextWithTag) {}

    private static Envelope encrypt(
            SecretKey key, byte[] iv, String message) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] result = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return new Envelope(iv, result);
    }

    private static String decrypt(SecretKey key, Envelope envelope)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, envelope.iv()));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(envelope.ciphertextWithTag());
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] nextIv(byte[] prefix, AtomicLong sequence) {
        long number = sequence.getAndIncrement();
        if (number < 0) {
            throw new IllegalStateException("IV sequence exhausted");
        }
        return ByteBuffer.allocate(12).put(prefix).putLong(number).array();
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] prefix = new byte[4];
        new SecureRandom().nextBytes(prefix);
        AtomicLong sequence = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    byte[] iv = nextIv(prefix, sequence);
                    String message = "order-" + index;
                    return message.equals(decrypt(key, encrypt(key, iv, message)));
                }));
            }
            int ok = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    ok++;
                }
            }
            System.out.println("GCM song song: " + ok + "/32");

            Envelope original = encrypt(key, nextIv(prefix, sequence), "order-99");
            byte[] modified = original.ciphertextWithTag().clone();
            modified[modified.length - 1] ^= 1;

            try {
                decrypt(key, new Envelope(original.iv(), modified));
                System.out.println("Sai: chấp nhận bản mã sửa");
            } catch (AEADBadTagException expected) {
                System.out.println("Tag sai: đã từ chối");
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 --release 17 ConcurrentGcmDemo.java
java ConcurrentGcmDemo
~~~

Kết quả:

~~~text
GCM song song: 32/32
Tag sai: đã từ chối
~~~

### Đọc kỹ những điều được và chưa được bảo đảm

- **Cipher riêng cho từng phép toán:** hai task không gọi init/updateAAD/doFinal trên cùng một Cipher instance.
- **Một khóa AES dùng nhiều lần là điều có thể thiết kế**, nhưng **mỗi lần mã hóa AES-GCM với khóa đó phải dùng IV duy nhất**. AtomicLong bảo đảm các lần lấy số không trùng trong phạm vi counter của ví dụ; byte prefix không thay đổi trong lần chạy.
- **Khóa được tạo mới khi chạy chương trình**. Nếu triển khai có khóa tồn tại lâu dài, các JVM khác nhau, khởi động lại hoặc khôi phục snapshot thì counter riêng không bảo đảm toàn cục. Cần thiết kế bộ cấp IV phối hợp với key version và giới hạn sử dụng theo hướng dẫn của thuật toán.
- **Envelope** là record chứa tham chiếu byte[]: record không tự làm byte[] bất biến sâu. Trong ví dụ, buffer chỉ được đọc hoặc clone trước khi sửa; ở ứng dụng thật cần kiểm soát quyền sở hữu, bản sao và dữ liệu có thể bị sửa đồng thời.
- **CiphertextWithTag** là chuỗi byte do JCE GCM trả về với tag gắn cùng bản mã. Khi lưu hoặc truyền, phải định nghĩa rõ format (version, key ID, IV length, IV, ciphertext/tag, AAD rule); người nhận không thể tự đoán các giá trị này.
- **AAD** không bị mã hóa nhưng được xác thực. Nếu bên nhận truyền AAD khác, tag sẽ không hợp lệ. Lỗi AEADBadTagException không chứng minh một cách duy nhất rằng có kẻ tấn công; cũng có thể do dữ liệu hỏng, sai khóa, sai IV hoặc sai AAD.

Ví dụ chỉ minh họa concurrency, state và tính toàn vẹn. Bảo vệ dữ liệu production còn đòi hỏi KMS/secret management, giới hạn lượng dữ liệu/mỗi khóa, rotation, chính sách truy cập và giải mã thông điệp cũ.

## 9. Thiết kế format để các provider và phiên bản ứng dụng tương tác được

**Không truyền đối tượng engine; truyền dữ liệu và tham số được định nghĩa rõ.** Ví dụ cho AES-GCM:

~~~text
Envelope V1 (định dạng do ứng dụng tự quy định, chỉ là ví dụ thiết kế)
  version:          1 byte
  keyIdLength:      1 byte
  keyId:            UTF-8 bytes, keyIdLength byte
  iv:               12 byte
  ciphertextAndTag: phần còn lại (tag 16 byte ở cuối)
  AAD:              bytes được xác định riêng bằng hợp đồng giao thức
~~~

Định dạng trên chỉ là **đề xuất giao thức minh họa**, không phải format do JCA chuẩn hóa. Nếu triển khai cần xác định giới hạn độ dài, xử lý input sai format, version nâng cấp, và byte nào thuộc AAD để bảo vệ metadata trước sửa đổi. Đừng để key ID/tenant ID quan trọng bị thay đổi mà không được xác thực hoặc đối chiếu với ngữ cảnh tin cậy.

Một bản ghi thường cần:

1. **Version:** cho phép đọc dữ liệu cũ khi thay cách đóng gói/thuật toán được hỗ trợ.
2. **Key ID / key version:** giúp chọn đúng khóa khi rotation; phải kiểm tra quyền dùng khóa, không dùng ID đến từ nguồn không đáng tin để truy cập tùy ý.
3. **Algorithm parameters:** IV, tag length, các tham số cần thiết. Có thể cố định trong version để bớt metadata, nhưng phải ghi trong đặc tả.
4. **Payload:** ciphertext và tag hoặc signature/MAC với format/encoding đã thống nhất.
5. **AAD/canonicalization:** quy định chính xác byte dùng làm dữ liệu được xác thực.
6. **Chính sách kiểm tra:** reject unknown version, khóa không hợp lệ, tag/signature sai; không tự fallback sang cơ chế yếu.

### Khóa có thể dùng qua provider khác không?

JCA hướng tới tính tương thích giữa các implementation, nhưng **không nên giả định mọi đối tượng Key đều chuyển được sang mọi provider**.

- Public key thông thường có thể được biểu diễn ở dạng **X.509 SubjectPublicKeyInfo**; private key phần mềm ở dạng **PKCS#8** nếu cho phép export. Cần dùng **KeyFactory** với key spec phù hợp.
- SecretKeySpec có thể mô tả khóa đối xứng từ bytes, nhưng một khóa **HSM/PKCS#11** có thể là handle không xuất được và bị ràng buộc với provider/token.
- Key.getEncoded() **có thể trả null** khi khóa không cho phép xuất dạng mã hóa. Không dùng getEncoded() như một bước mặc định để “di chuyển” private key.
- Khi đưa khóa vào một provider mới, vẫn phải kiểm chứng thuật toán, mục đích sử dụng, policy và khả năng init. Một khóa từ provider A không tự động tương thích với mọi API/provider B.

### Băm, MAC và chữ ký có cần định dạng không?

Có. Ví dụ SHA-256 tạo **32 byte**, nhưng bên khác có thể hiển thị hex chữ thường, hex chữ hoa hay Base64: chúng là cách biểu diễn khác nhau của **cùng bytes**. HMAC cũng cần thống nhất input bytes và thứ tự tính. Chữ ký số còn có đặc tả encoding của chữ ký tùy thuật toán (ví dụ một số định dạng chữ ký ECDSA khác nhau không thể được coi là như nhau). Tên “RSA” đơn lẻ không đủ xác định padding, digest và profile chữ ký.

Nếu dùng JSON cho dữ liệu ký, hãy chuẩn hóa theo một quy tắc rõ hoặc ký đúng raw payload mà giao thức định nghĩa; không chuyển đổi object → JSON lại ở bên nhận rồi mặc định byte giống bản gốc.

## 10. Provider và khả năng tương thích khi triển khai

### 10.1. Định danh provider trong chẩn đoán

Các câu lệnh sau là **mảnh minh họa dùng trong một method có throws phù hợp**, không phải chương trình Java độc lập:

~~~java
var digest = java.security.MessageDigest.getInstance("SHA-256");
System.out.println(digest.getAlgorithm());
System.out.println(digest.getProvider().getName());

var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
System.out.println(cipher.getAlgorithm());
System.out.println(cipher.getProvider().getName());
~~~

Tên provider in ra **phụ thuộc runtime**, không nên ghi cứng kết quả khi dạy hoặc kiểm thử đa nền tảng. Có thể xem danh sách provider được đăng ký bằng **Security.getProviders()**. Khi kiểm thử, cần ghi lại JDK, provider thực tế, algorithm/transformation, loại khóa và tham số; không log nội dung khóa, plaintext hay bí mật.

### 10.2. Có nên chỉ định provider cụ thể?

- **Không chỉ định provider:** giúp ứng dụng tận dụng cài đặt phù hợp trên JVM đang chạy; vẫn phải kiểm thử điều kiện hỗ trợ và hợp đồng mật mã.
- **Chỉ định provider:** hữu ích khi bắt buộc dùng HSM, provider đã qua kiểm định, chính sách/FIPS hoặc khi cần hành vi/khả năng được xác minh cụ thể; có thể giảm tính di động giữa môi trường.
- Thay đổi thứ tự Security provider, cấu hình JDK hoặc dependency provider trong khi hệ thống đang chạy có thể ảnh hưởng **các lần tạo engine sau đó**; engine hiện có không tự chuyển cài đặt một cách kỳ diệu.

Không nên sửa cấu hình provider theo request để “thử cho chạy”. Quyết định provider là cấu hình kiến trúc/triển khai và cần test rõ ràng.

### 10.3. Đừng phụ thuộc default

Phản ví dụ: **Cipher.getInstance("AES")** cho provider tự chọn mode/padding mặc định. Điều này vừa có nguy cơ không an toàn vừa làm kết quả không nhất quán giữa môi trường. **Cipher.getInstance("AES/GCM/NoPadding")** nêu yêu cầu rõ hơn, nhưng vẫn phải thống nhất IV, tag, AAD và format.

Tương tự, hãy ghi rõ tên thuật toán HMAC, thuật toán chữ ký và tham số liên quan; tránh cơ chế fallback “nếu ký chuẩn không được thì chuyển sang thuật toán cũ yếu hơn” mà không được thiết kế và kiểm soát.

## 11. Kiểm thử đúng: tách compatibility test khỏi concurrency test

| Nhóm kiểm thử | Cách thực hiện | Lỗi được phát hiện |
| --- | --- | --- |
| **Known-answer test** | Cùng input/key/params cho expected digest/HMAC/ciphertext khi thuật toán có vector chuẩn | Sai thuật toán, encoding, padding, profile, implementation. |
| **Cross-provider / cross-version** | Provider A tạo, provider B đọc/xác minh; đảo chiều khi phù hợp | Khác định dạng key/signature, mode, tham số, khả năng hỗ trợ. |
| **Tamper/negative test** | Đổi 1 bit ciphertext/tag/AAD, thay chữ ký/khóa, sai key ID | Không xác minh tag/chữ ký, xử lý lỗi/fallback không đúng. |
| **Concurrency/stress test** | Chạy hàng trăm/nghìn tác vụ độc lập với expected outputs và nhiều thread | Race khi dùng chung engine/buffer/counter, dữ liệu request bị trộn. |
| **Lifecycle test** | init → update → doFinal; dùng lại tuần tự; thử exception rồi khởi tạo lại | Hiểu sai reset, dùng sai mode, reuse với trạng thái lỗi. |
| **Nonce/key lifecycle test** | Chạy đồng thời trên nhiều node, restart/snapshot, rotation | IV trùng theo khóa, key ID cũ không giải mã được, khóa không nhất quán. |
| **Runtime matrix** | Kiểm tra môi trường JDK, provider, FIPS/HSM, policy, độ dài khóa | NoSuchAlgorithm/NoSuchPadding/InvalidKey/InvalidAlgorithmParameter trong production. |
| **Performance & liveness** | Đo throughput, contention, provider lock, HSM session pool, entropy wait | Tắc nghẽn, starvation, timeout và chi phí getInstance/init. |

Một bài stress test chạy xanh **không chứng minh không có data race**. Chứng minh thiết kế an toàn dựa trước hết vào ownership, API contract và invariant (như một IV cho mỗi lần mã hóa), sau đó dùng test để bắt lỗi cài đặt.

### Checklist review một dịch vụ đa luồng

- Có engine stateful nào là field/static của singleton hay bị share giữa request không?
- Mỗi tác vụ có sở hữu trọn chuỗi init → update → finalize không?
- Input buffer, key parameter, IV/AAD có bị thread khác sửa khi đang xử lý không?
- Với AES-GCM, có thể chứng minh hoặc đánh giá theo thiết kế **không lặp (key, IV)** khi scale/restart không?
- Có giao thức byte chuẩn: charset, trường dữ liệu, version, key ID, tag/padding, tham số và thứ tự không?
- Có negative test cho dữ liệu sai, khóa sai, replay khi giao thức yêu cầu không?
- Có test trên môi trường/provider thực tế và ghi lại các thay đổi provider/JDK không?
- Có giữ bí mật khỏi log, bảo vệ private/secret keys và giới hạn truy cập hợp lý không?

## 12. Phân biệt lỗi state, lỗi khả năng hỗ trợ và lỗi dữ liệu

| Triệu chứng | Hướng kiểm tra |
| --- | --- |
| **NoSuchAlgorithmException** hoặc **NoSuchPaddingException** | Provider có cung cấp thuật toán/transformation đúng tên không? JDK và chính sách có khác nhau không? |
| **InvalidKeyException** | Key size, key type, purpose và khả năng import/provider handle có hợp lệ không? |
| **InvalidAlgorithmParameterException** | IV, tag length, PSS/OAEP/GCM parameters có đúng contract/provider không? |
| **IllegalStateException** khi update/finalize | Đã init chưa? Mode đang là gì? Có thread khác re-init hoặc finalize cùng engine không? |
| **AEADBadTagException** | Sai key/IV/AAD, ciphertext/tag hỏng hoặc bị sửa; kiểm tra format, byte serialization và nonce. |
| **Signature.verify trả false** | Public key, byte input, algorithm/profile, encoding chữ ký và tham số có khớp không? |
| **Lỗi chỉ xuất hiện khi tải cao** | Engine/buffer singleton, ThreadLocal reuse, counter IV, provider/HSM contention, key rotation race. |

Không dùng thông báo lỗi chi tiết có chứa khóa, plaintext hoặc metadata nhạy cảm trong API phản hồi cho client. Hãy log nguyên nhân kỹ thuật ở mức đủ để chẩn đoán, kèm request ID/algorithm version đã được phê duyệt, không log secrets.

## 13. Một thiết kế ứng dụng Java thực tế

Giả sử có dịch vụ web xác thực webhook đồng thời:

1. Mỗi request có đúng **raw payload bytes**, chữ ký/MAC, key ID và timestamp theo giao thức đã thỏa thuận.
2. Server kiểm tra định dạng, giới hạn payload, thời hạn và replay/idempotency theo yêu cầu.
3. Thành phần tìm khóa nhận key ID và trả về đúng key/version đã được phép sử dụng; cấu hình provider đã cố định từ deployment.
4. Service tạo **Mac hoặc Signature instance mới trong method**; init đúng khóa, update trên raw bytes/canonical bytes đã định nghĩa, kiểm tra kết quả.
5. Chỉ khi xác thực thông điệp hợp lệ mới đi tiếp tới xử lý nghiệp vụ và kiểm soát quyền.
6. Tại lớp nghiệp vụ, xử lý idempotent để thông điệp hợp lệ bị gửi lặp không tạo giao dịch lặp.
7. Khi thay provider/JDK, chạy lại compatibility vectors, negative tests và concurrency tests trước khi triển khai.

Tương tự, với dịch vụ mã hóa dữ liệu, **mỗi thao tác mã hóa** cần một Cipher với khóa/IV/AAD đúng; bản ghi phải lưu metadata giải mã theo version, còn bộ quản lý IV và key lifecycle phải đủ an toàn dưới tải đồng thời, restart và nhiều instance.

## 14. Câu hỏi tự kiểm tra

**1. Mac.doFinal() reset về trạng thái sau init. Có thể dùng một Mac instance cho nhiều request song song không?**  
Không suy ra được. Reset hỗ trợ **tái sử dụng tuần tự**; các chuỗi update và doFinal ở các thread khác nhau vẫn có thể xen vào nhau. Nên tạo Mac riêng cho mỗi tác vụ.

**2. Vì sao cùng một chương trình AES-GCM chạy trên hai JVM có thể không tương thích?**  
Không chỉ cần cùng transformation. Hai bên phải thống nhất key, IV, tag length, AAD và byte format; provider của mỗi bên phải hỗ trợ cấu hình đó.

**3. Cipher.doFinal() reset state, vậy dùng lại Cipher GCM vừa mã hóa để mã hóa message khác có an toàn không?**  
Không nếu vẫn dùng cặp key/IV cũ. Phải thiết lập một IV mới hợp lệ, duy nhất với khóa đó; thường tạo Cipher mới cho thao tác tiếp theo.

**4. Có thể đặt một SecureRandom trong singleton để các thread dùng chung không?**  
Có, Java quy định SecureRandom thread-safe. Tuy nhiên vẫn cần xem throughput, hành vi block/reseed và provider thực tế. Không áp dụng kết luận tương tự cho Cipher, Signature, Mac hay MessageDigest.

**5. Signature.verify(false) chắc chắn chứng minh có race condition?**  
Không. Lỗi cũng có thể nằm ở public key, thuật toán/tham số, định dạng chữ ký hoặc byte input khác nhau. Kiểm tra compatibility contract trước, rồi mới phân tích concurrency.

**6. AtomicLong sinh nonce duy nhất trong một JVM có đủ cho cluster dùng cùng khóa không?**  
Không. Một node khác có thể dùng lại cùng dãy counter; restart/khôi phục trạng thái cũng vậy. Uniqueness cần được bảo đảm trong **toàn bộ phạm vi dùng chung khóa**.

**7. Vì sao không nên dựa vào getEncoded() cho mọi private key?**  
Các khóa thuộc HSM/PKCS#11 có thể là key handle không cho xuất. Khả năng chuyển khóa phụ thuộc provider, loại khóa và chính sách an toàn; getEncoded có thể trả null.

## 15. Ghi nhớ và tài liệu đối chiếu

Tính tương thích trong JCA là khả năng các phía thống nhất **thuật toán + tham số + bytes + khóa/định dạng + giao thức/version + chính sách provider**. Tính đúng đắn khi chạy đồng thời phụ thuộc **quyền sở hữu stateful engine**, thứ tự gọi phương thức và các invariant bảo mật như **mỗi lần mã hóa AES-GCM không lặp key/IV**. Thiết kế tốt thường chia sẻ cấu hình và quản lý khóa có kiểm soát, tạo engine riêng cho từng tác vụ và kiểm thử tính tương thích trên môi trường triển khai thật.

Tài liệu API chuẩn cho JDK 21:

- [JCA Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html)
- [MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Mac](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html)
- [Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [SecureRandom](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)

</details>

- [Quay lại đầu trang](#back-to-top)
