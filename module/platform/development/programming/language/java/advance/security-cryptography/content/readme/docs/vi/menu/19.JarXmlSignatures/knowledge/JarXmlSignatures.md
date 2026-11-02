<a id="back-to-top"></a>

# 19. Ký JAR và XML

## Menu
- [19.1 Chữ ký JAR, manifest, digest entry và chuỗi chứng thư của người ký](#jar-xml-signatures-001)
- [19.2 Dùng jarsigner để ký, xác minh và kiểm tra cảnh báo ở chế độ strict](#jar-xml-signatures-002)
- [19.3 Timestamp, thời hạn chứng thư và giới hạn của kết quả xác minh JAR](#jar-xml-signatures-003)
- [19.4 Phân biệt tính toàn vẹn gói JAR với độ tin cậy hoặc quyền thực thi của mã](#jar-xml-signatures-004)
- [19.5 Mô hình XMLSignatureFactory, SignedInfo, Reference và KeyInfo](#jar-xml-signatures-005)
- [19.6 Canonicalization, transform và chữ ký detached, enveloped hoặc enveloping](#jar-xml-signatures-006)
- [19.7 Tạo chữ ký XML bằng DOMSignContext và xác minh bằng DOMValidateContext](#jar-xml-signatures-007)
- [19.8 Tách chọn khóa khỏi kiểm tra khóa tin cậy; giới hạn URI tham chiếu và secure validation](#jar-xml-signatures-008)

## <a id="jar-xml-signatures-001">19.1 Chữ ký JAR, manifest, digest entry và chuỗi chứng thư của người ký</a>

<details>
<summary>Xem chi tiết</summary>

# 19.1 — Chữ ký JAR, manifest, digest của entry và chuỗi chứng thư người ký

## Mục tiêu

Sau bài này, bạn có thể giải thích chữ ký số trong Java Archive (JAR) bảo vệ phần nào của gói, đọc được các tệp `META-INF/MANIFEST.MF`, `*.SF` và `*.RSA`/`*.EC`, phân biệt tính toàn vẹn với độ tin cậy của người ký, ký và kiểm tra một JAR bằng JDK 17+, và lấy thông tin người ký của từng entry qua API Java.

Phạm vi ở đây là **JAR signing**. Chữ ký XML là cơ chế khác và không nằm trong bài 19.1.

## 1. JAR được ký là gì?

JAR là một tệp lưu trữ theo định dạng ZIP chứa `.class`, resource và các mục siêu dữ liệu. Mỗi tệp hoặc thư mục bên trong gọi là một **entry**; ví dụ `com/example/App.class` hoặc `config/default.properties`.

Ký JAR dùng **khóa riêng** của người ký tạo chữ ký số, còn người kiểm tra dùng **khóa công khai** trong chứng thư để xác minh chữ ký. Cơ chế gắn các digest của dữ liệu JAR với chữ ký đó, từ đó giúp phát hiện việc thay đổi các entry đã ký.

Ba điều phải tách riêng:

- **Integrity (toàn vẹn):** các byte của một entry được ký có còn đúng như khi ký không?
- **Authenticity (xác thực chữ ký):** chữ ký có được tạo bởi khóa riêng tương ứng với khóa công khai trong chứng thư người ký không?
- **Trust (tin cậy):** chứng thư người ký có được hệ thống của bạn chấp nhận, được cấp đúng mục đích, còn hiệu lực và phù hợp chính sách không?

Một chữ ký hợp lệ **không tự chứng minh** mã nguồn an toàn, người ký đáng tin, hay toàn bộ entry trong JAR đều đã được ký. Java không tự coi mọi JAR có chữ ký là được phép chạy với quyền cao hơn.

## 2. Ba lớp liên kết nội dung với chữ ký

JAR ký thông thường có những tệp quan trọng sau:

| Thành phần | Chứa gì? | Vai trò |
| --- | --- | --- |
| `META-INF/MANIFEST.MF` | Thuộc tính chính và các section `Name: ...`, `SHA-256-Digest: ...` của entry | Liên kết **byte nội dung từng entry** với digest |
| `META-INF/JAR-DEMO.SF` (tên chỉ minh họa) | `Signature-Version`, digest của manifest và/hoặc digest của các section manifest | Liên kết **manifest** với dữ liệu sắp được ký |
| `META-INF/JAR-DEMO.RSA`, `.EC` hoặc `.DSA` | Signature block dạng nhị phân, chữ ký trên tệp `.SF` và thông tin chứng thư liên quan | Liên kết **`.SF`** với khóa của người ký |
| Entry ứng dụng | Ví dụ `Hello.class`, tài nguyên, cấu hình | Dữ liệu được kiểm tra đối chiếu digest khi đọc |

Tên `JAR-DEMO` phụ thuộc alias hoặc tùy chọn `-sigfile`, không phải tên cố định. Một JAR có thể có nhiều cặp `.SF` và signature block do nhiều người ký.

Sơ đồ logic:

~~~text
Byte của Hello.class
       │ SHA-256
       ▼
MANIFEST.MF: section Name: Hello.class + SHA-256-Digest
       │ digest của section manifest (và có thể cả manifest)
       ▼
JAR-DEMO.SF: các digest về manifest
       │ ký bằng private key
       ▼
JAR-DEMO.RSA: chữ ký của .SF + chứng thư người ký
       │
       ▼
Verifier dùng public key trong chứng thư xác minh chữ ký,
rồi đối chiếu .SF → manifest → byte thực của Hello.class
~~~

**Digest không phải chữ ký.** Digest là kết quả hàm băm, ai cũng có thể tính lại khi sửa file. Chữ ký số mới ràng buộc các digest đó với khóa riêng của người ký.

## 3. Manifest và digest của từng entry

`META-INF/MANIFEST.MF` có main section ở đầu, tiếp theo là các section riêng, ngăn nhau bởi dòng trống:

- Main section có `Manifest-Version: 1.0` và có thể có `Main-Class`, `Class-Path` hay các thuộc tính đóng gói khác.
- Section của entry bắt đầu bằng `Name: đường/dẫn/trong/jar`, sau đó có thể có `SHA-256-Digest`, `SHA-384-Digest` hoặc thuật toán khác tùy phiên bản/cấu hình.
- Giá trị digest là Base64 của **các byte nội dung entry sau khi giải nén**, không phải Base64 của tên tệp, không phải hash của toàn bộ JAR và không phải chữ ký bằng private key.

Có thể biểu diễn cách tính bằng công thức:

~~~text
entryDigest = Base64(SHA-256(uncompressedEntryBytes))
~~~

Ví dụ một section được nhận diện bởi `Name: Hello.class`; trường `SHA-256-Digest` tương ứng được `jarsigner` tạo từ byte của `Hello.class`. Không tự điền một chuỗi hash bất kỳ: verifier so sánh với digest tính từ byte thực tế.

**Định dạng là một phần của cơ chế.** Manifest là văn bản theo quy tắc riêng: thuộc tính dạng `Key: Value`, các section phân cách bằng dòng trống, dòng dài được tiếp nối bằng một dòng bắt đầu bằng khoảng trắng, và có giới hạn 72 byte cho mỗi dòng vật lý. Các byte, cách xuống dòng và biểu diễn section liên quan đến phép băm của `.SF`; vì vậy sửa manifest bằng công cụ formatter hoặc đổi encoding/xuống dòng sau khi ký có thể làm xác minh thất bại.

Không phải mọi entry có mặt trong JAR đều nhất thiết có section được ký. Với `jarsigner`, các file entry hiện có thông thường được ký (ngoại trừ tệp metadata chữ ký dành riêng). API ký cấp thấp có thể chỉ ký một tập con; entry mới được thêm vào sau khi ký cũng có thể không nằm trong phạm vi chữ ký trước đó.

## 4. Tệp `.SF` bảo vệ manifest như thế nào?

Tệp `META-INF/<tên>.SF` là **signature file**, nhưng **không phải** bản chữ ký nhị phân. Nó chứa các digest dùng để kiểm tra manifest:

- `Signature-Version: 1.0` xác định phiên bản signature file.
- `SHA-256-Digest-Manifest` (nếu có) là digest của toàn bộ manifest.
- `SHA-256-Digest-Manifest-Main-Attributes` (nếu có) là digest của main attributes của manifest.
- Mỗi section `Name: Hello.class` trong `.SF` chứa digest của **section tương ứng trong manifest**, khác với digest của nội dung `Hello.class` nằm trong manifest.

Ví dụ, hai trường cùng tên `SHA-256-Digest` nhưng ở hai tầng khác nhau có ý nghĩa khác:

| Trường | Dữ liệu được băm |
| --- | --- |
| `MANIFEST.MF` → `Name: Hello.class` → `SHA-256-Digest` | Byte nội dung `Hello.class` |
| `JAR-DEMO.SF` → `Name: Hello.class` → `SHA-256-Digest` | Byte của section `Hello.class` trong `MANIFEST.MF` |

Verifier thường thử digest của toàn bộ manifest trước. Nếu digest đó không có hoặc không khớp, đặc tả cho phép kiểm tra theo main attributes và từng section. Điều này hỗ trợ tình huống manifest được **bổ sung section mới** sau lần ký trước: chữ ký cũ vẫn có thể hợp lệ cho các entry ban đầu, miễn các section và byte đã ký không thay đổi. Không được suy diễn rằng phần bổ sung cũng được signer cũ bảo vệ.

## 5. Signature block và chuỗi chứng thư người ký

Tệp `META-INF/<tên>.RSA`/`.EC`/`.DSA` là signature block, thường ở dạng PKCS #7/CMS SignedData. Nó chứa chữ ký số của `.SF`, cùng chứng thư X.509 phục vụ xác minh người ký; không phải nơi lưu trữ private key. Tùy thuật toán của khóa và JDK, tên phần mở rộng sẽ khác nhau.

**Chuỗi chứng thư (certificate chain)** thường gồm:

1. **Leaf / signer certificate:** chứng thư đầu cuối chứa public key dùng xác minh chữ ký, chủ thể (Subject) và thời hạn.
2. **Intermediate CA certificate(s):** nếu người ký dùng CA, các chứng thư trung gian nối leaf đến CA cấp trên.
3. **Trust anchor:** gốc tin cậy do bên kiểm tra lựa chọn trong truststore/chính sách. Root không bắt buộc phải được nhúng vào JAR và việc xuất hiện trong JAR cũng không tự khiến nó đáng tin.

Chuỗi được xây dựng và đánh giá theo chính sách PKIX/X.509: chữ ký giữa các chứng thư, thời hạn, KeyUsage/ExtendedKeyUsage (nếu có), thuật toán được phép, trạng thái thu hồi khi yêu cầu, và trust anchor được chấp nhận. Chuỗi chứng thư **không thay thế** kiểm tra digest của entry; hai bước giải quyết hai vấn đề khác nhau.

Một JAR có thể được ký đúng bằng self-signed certificate. Khi đó kiểm tra chữ ký có thể thành công nhưng công cụ vẫn cảnh báo `self-signed certificate` hoặc `certificate chain not validated`. Đối với triển khai thực tế, chính sách phải xác định người ký nào được tin cậy; không tin chỉ vì certificate xuất hiện trong signature block.

**Timestamp (dấu thời gian):** nếu `jarsigner` dùng dịch vụ TSA, thông tin timestamp đã ký có thể được đính kèm; verifier kiểm tra chữ ký/chuỗi của TSA theo chính sách riêng. Timestamp tin cậy giúp chứng minh thời điểm ký nằm trong giai đoạn chứng thư người ký hợp lệ. Timestamp không giúp bỏ qua kiểm tra integrity và không sửa được một entry đã bị thay đổi.

## 6. Trình tự xác minh và điểm phát hiện sai lệch

Dưới góc nhìn người xác minh:

1. Mở JAR với chế độ verification. Xác minh chữ ký cryptographic trên `.SF` bằng public key trong certificate signer của signature block.
2. Xác minh nội dung liên quan trong manifest qua digest của `.SF`: toàn manifest hoặc main attributes/section theo quy tắc đặc tả.
3. Khi đọc **đến hết nội dung từng entry**, tính digest từ byte thực và đối chiếu digest ở section manifest được bảo vệ. Sai lệch có thể phát sinh `SecurityException`.
4. Xác định entry đó có bao nhiêu người ký và lấy chuỗi chứng thư của từng người ký.
5. Thực hiện kiểm tra **tin cậy/policy** riêng: người ký được cho phép không, chain có tới trust anchor mong đợi không, certificate, thuật toán, timestamp và các quy tắc doanh nghiệp có đạt không.
6. Kiểm kê **tất cả entry bắt buộc phải ký**. Sự thành công của một entry đã ký không đồng nghĩa các entry khác cũng được ký.

Tình huống thường gặp:

| Thay đổi hoặc trạng thái | Kết quả phải hiểu đúng |
| --- | --- |
| Sửa byte của `Hello.class` sau ký | Digest thực không còn khớp manifest đã ký; xác minh entry thất bại |
| Sửa `.SF` nhưng giữ signature block cũ | Chữ ký trên `.SF` không khớp |
| Chỉ thêm resource mới sau khi ký | Entry mới có thể **unsigned**; entry ban đầu vẫn có thể hợp lệ |
| Certificate tự ký, chưa được tin cậy | Integrity/signature có thể hợp lệ nhưng identity/trust chưa đạt |
| Certificate hết hạn hoặc bị thu hồi | Cần xét policy, thời điểm ký và timestamp tin cậy nếu có |
| Algorithm ký/digest nằm trong danh sách bị vô hiệu hóa | JDK có thể coi JAR như unsigned hoặc từ chối theo chính sách thuật toán |

Việc **repackaging, shading, re-compression, sửa manifest hoặc ghi lại JAR** sau ký phải được xem là thay đổi cần kiểm tra lại. Quy trình an toàn thường là **build hoàn tất → ký → verify → phát hành bất biến**.

## 7. Thực hành: tạo, ký và kiểm tra JAR trên JDK 17+

Các lệnh dưới đây chạy trong thư mục thực hành có JDK 17+ và `java`, `javac`, `jar`, `keytool`, `jarsigner` trên `PATH`. Chứng thư được tạo **chỉ để học tập**, không dùng làm chứng thư phát hành thật.

Tạo tệp **Hello.java**:

~~~java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Signed JAR demo");
    }
}
~~~

Biên dịch, đóng gói JAR chưa ký và chạy thử:

~~~bash
javac Hello.java
jar --create --file unsigned.jar Hello.class
java -cp unsigned.jar Hello
~~~

Stdout chính xác:

~~~text
Signed JAR demo
~~~

Tạo một cặp khóa RSA và chứng thư X.509 tự ký trong PKCS#12; `keytool` sẽ hỏi mật khẩu keystore qua terminal:

~~~bash
keytool -genkeypair -alias jar-demo -keyalg RSA -keysize 3072 -validity 90 -storetype PKCS12 -keystore demo.p12 -dname "CN=Training Signer, OU=Learning, O=Example, C=VN"
~~~

Dùng alias `jar-demo` để ký và xuất thành **signed.jar**, giữ nguyên **unsigned.jar**:

~~~bash
jarsigner -keystore demo.p12 -storetype PKCS12 -signedjar signed.jar unsigned.jar jar-demo
~~~

Công cụ sẽ hỏi mật khẩu. Đọc cấu trúc JAR và kiểm tra chữ ký:

~~~bash
jar --list --file signed.jar
jarsigner -verify -verbose -certs signed.jar
java -cp signed.jar Hello
~~~

Lệnh `jar --list` sẽ cho thấy `META-INF/MANIFEST.MF`, cặp tệp signature `META-INF/JAR-DEMO.SF` và `META-INF/JAR-DEMO.RSA`, cùng `Hello.class`. Tên và thứ tự entry hiển thị có thể thay đổi theo công cụ. Lệnh `java` cuối vẫn in đúng `Signed JAR demo` như trước.

Trong kết quả `jarsigner -verify -verbose -certs`, ký hiệu `s` cho biết chữ ký entry được xác minh, `m` cho biết entry có trong manifest; `k` liên quan việc tìm thấy chứng thư trong keystore đang dùng. Hãy kiểm tra **từng entry ứng dụng** và phần cảnh báo của công cụ. Vì bài demo dùng self-signed certificate và không có TSA, có thể thấy cảnh báo về chuỗi chứng thư và timestamp; đó là dự kiến, không phải dấu hiệu byte `Hello.class` đã bị sửa.

Trong CI/CD phát hành thực tế, có thể dùng `jarsigner -verify -strict -verbose -certs signed.jar` để biến một số **severe warnings** thành mã thoát khác 0. Lưu ý lệnh `-strict` đối với certificate tự ký của bài demo có thể thất bại đúng theo thiết kế. `-strict` không thay chính sách kiểm kê entry và tin cậy publisher của tổ chức.

## 8. Kiểm tra từng entry bằng API Java 17

`JarFile` có chế độ `verify=true` để kiểm tra tính toàn vẹn/chữ ký của nội dung entry khi đọc. `JarEntry.getCodeSigners()` hoặc `getCertificates()` chỉ có thông tin đáng tin cậy sau khi **đọc hết stream của entry**; hỏi trước thường trả về `null`. API này **không tự đánh giá mức tin cậy của certificate signer**.

Chương trình sau liệt kê người ký của các entry ứng dụng. Để ví dụ ngắn gọn, nó bỏ qua `META-INF/`; khi triển khai kiểm kê nghiêm ngặt phải xem cả các resource không thuộc signature metadata trong thư mục đó.

Tạo **InspectJar.java**:

~~~java
import java.io.OutputStream;
import java.security.CodeSigner;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class InspectJar {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: java InspectJar jar-file");
        }

        int signedEntries = 0;
        int unsignedEntries = 0;

        try (JarFile jar = new JarFile(args[0], true)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                // Đọc đến EOF mới hoàn tất kiểm tra digest/chữ ký cho entry.
                try (var input = jar.getInputStream(entry)) {
                    input.transferTo(OutputStream.nullOutputStream());
                }

                CodeSigner[] signers = entry.getCodeSigners();
                int count = signers == null ? 0 : signers.length;
                System.out.printf("%s | signers=%d%n", entry.getName(), count);

                if (count == 0) {
                    unsignedEntries++;
                    continue;
                }

                signedEntries++;
                for (CodeSigner signer : signers) {
                    var chain = signer.getSignerCertPath().getCertificates();
                    System.out.println("  certificate chain length: " + chain.size());
                    if (!chain.isEmpty() && chain.get(0) instanceof X509Certificate leaf) {
                        System.out.println("  signer subject: " + leaf.getSubjectX500Principal());
                    }
                    System.out.println("  has timestamp: " + (signer.getTimestamp() != null));
                }
            }
        }

        System.out.printf("Signed entries: %d, unsigned entries: %d%n",
                signedEntries, unsignedEntries);
    }
}
~~~

Chạy:

~~~bash
javac InspectJar.java
java InspectJar signed.jar
~~~

Với JAR nhỏ đã ký ở mục 7, chương trình sẽ nhận diện `Hello.class` có một signer; phần cuối:

~~~text
Signed entries: 1, unsigned entries: 0
~~~

Các dòng subject và độ dài certificate chain phụ thuộc certificate thực tế. Nếu entry được ký bị thay byte, chương trình có thể ném `SecurityException` trong lúc đọc hoặc xác minh. Nếu entry không được ký, `getCodeSigners()` trả về `null` dù chương trình đã đọc hết nó. `CodeSigner.getSignerCertPath()` cho chuỗi chứng thư của từng signer; `getTimestamp()` có thể `null` nếu ký không có timestamp.

**Giới hạn quan trọng:** chương trình minh họa chỉ kiểm tra chữ ký/digest và đếm signer. Để quyết định có **chấp nhận** publisher hay không, ứng dụng cần truststore/PKIX validation, danh sách signer được phép, chính sách thuật toán và timestamp. Đừng lấy `count > 0` làm quy tắc tin cậy trong môi trường thật.

## 9. Cách đọc kết quả đúng và kiểm thử âm tính

Cần phân biệt ba kết luận riêng biệt thay vì chỉ nhìn dòng `jar verified`:

1. **Byte của các entry được ký có khớp?** Kiểm tra phần xác minh chữ ký và digest; mọi entry bắt buộc ký phải được đọc/kiểm tra.
2. **Mọi entry quan trọng đều đã ký?** Kiểm kê file ứng dụng/resource được phép. Entry thêm sau khi ký có thể unsigned mà chữ ký trên entry cũ vẫn đúng.
3. **Người ký có được chấp nhận?** Xem chain, certificate validity/usage, trust anchor, revocation/timestamp và chính sách tổ chức.

Một bộ thử nghiệm nên có:

- **Mẫu chuẩn:** JAR ký, mọi entry cần bảo vệ có signer theo dự kiến.
- **Sửa nội dung entry:** dựng bản thử nghiệm bằng cách thay byte rồi kiểm tra lại; phải bắt được lỗi integrity.
- **Thêm entry chưa ký:** kiểm tra rằng quy trình phát hành phát hiện entry ngoài danh sách đã ký.
- **Self-signed/unknown CA:** crypto verification có thể hợp lệ, nhưng trust phải bị từ chối bởi policy thật.
- **Expired/disabled algorithm:** xem cảnh báo và mã thoát của `jarsigner -strict`, đối chiếu chính sách JDK đang chạy.
- **Nhiều người ký:** xác minh signer theo **entry**, không chỉ đếm tệp `.SF` toàn JAR.

Không cần phá khóa riêng để giả mạo một JAR: một attacker có thể thử chèn entry unsigned, đổi classpath, thay dependency, hoặc lợi dụng code loader bỏ qua bước kiểm tra policy. Vì vậy phát hành JAR an toàn còn cần khóa phiên bản dependency, kiểm tra provenance, giới hạn nguồn tải và không thực thi trực tiếp gói chưa được tin cậy.

## 10. Lỗi phổ biến và cách tránh

| Nhầm lẫn | Diễn giải/cách sửa |
| --- | --- |
| “Manifest là chữ ký số.” | Manifest chỉ chứa metadata và digest. Chữ ký cryptographic nằm trong signature block của `.SF`. |
| “SHA-256-Digest trong `.SF` là hash của file `.class`.” | Digest trong `.SF` trỏ tới **section manifest**; digest trong manifest trỏ tới **byte entry**. |
| “Có `META-INF/*.RSA` thì mọi file đều được ký.” | Có thể có entry unsigned; phải xem signer theo entry và chính sách phạm vi. |
| “`jarsigner -verify` thành công tức là publisher đáng tin.” | Chữ ký hợp lệ và certificate chain được tin cậy là hai kết luận tách biệt. |
| “Gọi `getCodeSigners()` ngay sau `getJarEntry()`.” | Phải đọc hết input stream với verification bật rồi mới lấy signer. |
| “Có certificate trong block nên root CA đương nhiên trusted.” | Trust anchor do phía verifier cấu hình; root được nhúng không tạo niềm tin. |
| “Sửa tên, nén lại hoặc chèn resource sau ký không sao.” | Luôn kiểm tra lại; các thay đổi có thể làm invalid signature hoặc sinh entry unsigned. |
| “Dùng chứng thư tự ký demo để publish production.” | Cần quy trình cấp phát/giữ private key, certificate code signing và chính sách tin cậy thật. |
| “Không có TSA thì chữ ký vô giá trị ngay.” | Chữ ký vẫn có thể xác minh; thiếu timestamp làm việc đánh giá hợp lệ theo thời gian khó hơn và có thể gây cảnh báo. |

## 11. Checklist triển khai

- Build artifact cuối cùng xong trước khi ký; lưu artifact đã ký bất biến.
- Dùng thuật toán hiện đại được JDK và chính sách của hệ thống chấp nhận; tránh SHA-1/MD5 và các thuật toán bị vô hiệu hóa.
- Bảo vệ private key bằng keystore/secret manager/HSM phù hợp; không hard-code mật khẩu trong mã hay log.
- Chọn signer certificate và chain đúng mục đích code signing; xác định trust anchor trước khi phát hành.
- Cân nhắc TSA đáng tin cậy, kiểm tra tính hợp lệ của TSA và kế hoạch xử lý hết hạn/thu hồi.
- Chạy `jarsigner -verify -verbose -certs` và `-strict` theo policy với certificate thật; đọc cảnh báo và exit code.
- Bảo đảm **mọi entry bắt buộc** có signer đúng; không chấp nhận resource unsigned bất ngờ.
- Với classloader tùy biến hoặc công cụ tự kiểm tra, đọc hết entry trước khi dùng `getCodeSigners()`; áp dụng kiểm tra trust riêng.
- Nếu thay đổi JAR sau ký, build/ký/verify lại và kiểm thử đường dẫn triển khai cuối cùng.

## 12. Câu hỏi tự kiểm tra

**1. Nếu thay `Hello.class` nhưng giữ nguyên manifest và `.SF` thì sao?** Verifier tính digest từ byte `Hello.class` mới; digest không khớp giá trị đã ký trong manifest nên thất bại khi xác minh entry.

**2. Vì sao `.SF` cần digest của section manifest?** Vì chữ ký số ký `.SF`, còn manifest chứa digest của byte entry. Digest section tạo cầu nối được bảo vệ giữa `.SF` và manifest.

**3. Tại sao certificate self-signed vẫn có thể xác minh chữ ký?** Public key của certificate xác minh được phép toán chữ ký, nhưng certificate tự ký không tự trở thành trust anchor của người kiểm tra.

**4. Vì sao `getCodeSigners()` có thể trả `null`?** Entry unsigned hoặc chưa được đọc/verify đến EOF; cũng phải kiểm tra chế độ verify và lỗi thuật toán/chữ ký.

**5. Nếu JAR được thêm file sau lần ký ban đầu, chứng thư người ký cũ đảm bảo file mới không?** Không. Cần kiểm tra phạm vi signer của chính file mới; chữ ký cũ chỉ có thể tiếp tục bảo vệ phần đã ký trước.

## Tài liệu chuẩn để đối chiếu

- JAR File Specification (Java 17), phần Signed JAR File và Signature Validation: https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html
- `jarsigner` tool: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jarsigner.html
- `java.util.jar.JarFile` (Java 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html
- `java.util.jar.JarEntry` (Java 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarEntry.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-002">19.2 Dùng jarsigner để ký, xác minh và kiểm tra cảnh báo ở chế độ strict</a>

<details>
<summary>Xem chi tiết</summary>

# 19.2. Dùng `jarsigner` để ký, xác minh và kiểm tra cảnh báo ở chế độ `-strict`

## 1. Bài toán và kết quả cần đạt

Khi phát hành một thư viện hoặc ứng dụng dạng JAR, người nhận cần kiểm tra hai điều riêng biệt: **các entry đã ký có bị thay đổi sau khi ký hay không** và **chứng thư của người ký có đáng tin theo chính sách của họ hay không**. JDK cung cấp `keytool` để quản lý khóa/chứng thư và `jarsigner` để ký, xác minh JAR.

Sau bài này, bạn có thể:

1. Giải thích cấu trúc chữ ký JAR và ranh giới giữa *integrity*, *authenticity* và *trust*.
2. Tạo keystore thử nghiệm, ký một JAR bằng khóa riêng rồi xác minh bằng `jarsigner -verify`.
3. Đọc cờ `s`/`m`/`k`, thông tin certificate, timestamp và cảnh báo trong `-verbose -certs`.
4. Dùng `-strict` để phát hiện severe warnings qua exit code, phân biệt lỗi thật với cảnh báo và cấu hình kiểm tra khi phát hành.

**Phạm vi:** ký và xác minh JAR bằng công cụ JDK; chữ ký XML là cơ chế khác, không được thực hiện bằng lệnh `jarsigner` này.

## 2. Chữ ký JAR chứng minh điều gì?

JAR là một archive dựa trên ZIP, thường chứa bytecode `.class`, resources và `META-INF/MANIFEST.MF`. Khi ký, `jarsigner` dùng **private key** của một alias trong keystore; chứng thư X.509 (có public key tương ứng) được đưa vào khối chữ ký để bên nhận có thể kiểm tra chữ ký.

Các thành phần quan trọng:

| Thành phần | Chức năng |
| --- | --- |
| `META-INF/MANIFEST.MF` | Có các section ghi digest của dữ liệu các entry được ký. |
| `META-INF/<SIGNER>.SF` | Chứa digest của manifest hoặc các section manifest; kết nối dữ liệu entry với chữ ký. |
| `META-INF/<SIGNER>.RSA` (hoặc `.DSA`/`.EC`) | Chứa chữ ký số của `.SF` và certificate chain của người ký. |
| Certificate chain | Cung cấp public key/danh tính được khai báo; **không tự động biến thành nguồn đáng tin**. |
| Timestamp token (nếu dùng TSA) | Chứng cứ thời điểm ký do dịch vụ đóng dấu thời gian cung cấp, hỗ trợ kiểm tra sau khi chứng thư hết hạn. |

Luồng kiểm tra ở mức khái niệm:

```text
Private key + dữ liệu JAR
    -> manifest digest của entry
    -> signature file (.SF)
    -> signature block (.RSA/.DSA/.EC)
    -> signed JAR

Người nhận:
    chữ ký block hợp lệ?
    -> digest .SF/manifest hợp lệ?
    -> byte của từng entry đã ký còn khớp?
    -> thuật toán, certificate chain, hạn dùng, chính sách trust phù hợp?
```

**Phân biệt ba kết luận:**

- **Integrity:** nội dung entry đã được ký chưa bị đổi kể từ khi ký.
- **Authentication:** chữ ký khớp public key trong certificate của signer.
- **Trust:** người nhận thực sự chấp nhận signer/certificate chain theo truststore, chính sách cấp phát, mục đích sử dụng, thời hạn và (khi yêu cầu) trạng thái thu hồi. Một JAR có thể xác minh được chữ ký nhưng signer vẫn **self-signed hoặc không đáng tin**.
- Ký JAR **không mã hóa** file, không xác nhận nội dung chương trình là an toàn, không tự bảo vệ các entry được thêm sau khi ký. Nếu tồn tại entry chưa ký, phải xem warning và kiểm tra phạm vi ký.

## 3. Các lệnh và tùy chọn thường dùng

| Công cụ/tùy chọn | Ý nghĩa thực tế |
| --- | --- |
| `keytool -genkeypair` | Tạo cặp khóa và chứng thư trong keystore để làm bài thử. |
| `keytool -list -v` | Xem alias, `Entry type`, fingerprint, chain, validity và extensions. |
| `jarsigner -keystore <path> <jar> <alias>` | Ký JAR bằng private key của alias; không dùng `-signedjar` thì file nguồn có thể bị thay thế. |
| `-signedjar <output.jar>` | Ghi kết quả ra JAR mới, giữ bản unsigned để đối chiếu. |
| `-storetype PKCS12` | Nêu tường minh loại keystore; PKCS12 là mặc định của JDK hiện đại. |
| `-digestalg SHA-256` | Chọn digest cho các entry; khác với thuật toán chữ ký. |
| `-sigalg SHA256withRSA` | Thuật toán ký với khóa RSA trong ví dụ; cần tương thích loại khóa. |
| `-verify` | Kiểm tra chữ ký/digest đã ký của JAR thay vì tạo chữ ký mới. |
| `-verbose`, `-certs` | Hiện từng entry, trạng thái `s/m/k` và thông tin signer/certificate. |
| `-strict` | Biến severe warnings thành **exit code khác 0**; không phải nút “bật thêm mật mã”. |
| `-tsa <URL>` | Nhờ Time Stamping Authority (TSA) cấp timestamp theo RFC 3161 khi ký. |
| `-revCheck` | Yêu cầu kiểm tra revocation (OCSP/CRL) khi ký/xác minh; có thể cần kết nối mạng. |

Có thể kiểm tra công cụ sẵn có qua `java -version`, `javac -version`, `jar --help`, `keytool -help`, `jarsigner -help`. Các ví dụ dưới đây dùng **JDK 17+**, với terminal đặt tại thư mục thực hành trống. Mỗi lệnh dài viết trên **một dòng** để chạy được trong PowerShell, Command Prompt và shell Unix. Giữ nguyên tên file/alias để đối chiếu.

## 4. Thực hành từ JAR chưa ký đến JAR đã ký

### Bước 1 — Tạo class và đóng gói JAR

Tạo file `HelloJar.java`:

```java
public class HelloJar {
    public static void main(String[] args) {
        System.out.println("Hello from JAR");
    }
}
```

Biên dịch, đóng gói với `Main-Class`, rồi chạy thử:

```shell
javac HelloJar.java
jar --create --file hello-unsigned.jar --main-class HelloJar HelloJar.class
java -jar hello-unsigned.jar
```

`stdout` của lệnh `java -jar`:

```text
Hello from JAR
```

`javac` tạo `HelloJar.class`; `jar` đóng gói bytecode và manifest. Nếu không khai báo `--main-class`, `java -jar` sẽ thiếu entry point, nhưng JAR vẫn có thể được ký.

### Bước 2 — Tạo keystore PKCS12 và signer thử nghiệm

```shell
keytool -genkeypair -alias demo-signer -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 365 -dname "CN=Demo JAR Signer, O=Example, C=VN" -ext "KU=digitalSignature" -ext "EKU=codeSigning" -keystore demo-signer.p12 -storetype PKCS12
```

Công cụ hỏi mật khẩu keystore. Trong ví dụ PKCS12 này, khóa riêng sử dụng cùng mật khẩu lưu trữ khi không chỉ định mật khẩu khóa riêng khác. Các trường có nghĩa:

- `-alias demo-signer`: tên entry có private key, phải dùng đúng khi ký.
- `RSA` 3072 bit và `SHA256withRSA`: lựa chọn minh họa tương thích JDK 17+.
- `-validity 365`: chứng thư thử nghiệm có hiệu lực 365 ngày tính theo ngày tạo; không phải thời hạn tự động gia hạn.
- `KU=digitalSignature`, `EKU=codeSigning`: khai báo mục đích ký mã; giá trị extension phải phù hợp với mục đích sử dụng trong môi trường thực.
- Không có CA ký chứng thư ở bước này, nên `keytool` tạo **self-signed certificate**. Đây là chủ ý để quan sát cảnh báo `-strict`, **không phải cấu hình phát hành**.

Xem entry trước khi ký:

```shell
keytool -list -v -keystore demo-signer.p12 -storetype PKCS12 -alias demo-signer
```

Xác nhận `Entry type: PrivateKeyEntry`, alias, certificate chain, fingerprints, thời hạn, `KeyUsage` và `ExtendedKeyUsage`. Giá trị fingerprint, thời gian và phần thông tin cụ thể phụ thuộc lần tạo, không có một output cố định cho tất cả máy. Giữ file `.p12` và mật khẩu ở nơi an toàn; **không commit private key** vào Git.

### Bước 3 — Ký JAR bằng `jarsigner`

```shell
jarsigner -keystore demo-signer.p12 -storetype PKCS12 -digestalg SHA-256 -sigalg SHA256withRSA -signedjar hello-signed.jar hello-unsigned.jar demo-signer
```

Lệnh có dạng `jarsigner [options] jar-file alias`. Nhập mật khẩu khi được hỏi. Kết quả là `hello-signed.jar`; `hello-unsigned.jar` vẫn giữ nguyên. Với signer self-signed và không có TSA, công cụ có thể báo cảnh báo **certificate chain not validated / self-signed** và **no timestamp**. Việc có dòng `jar signed.` không đồng nghĩa đã được mọi bên tin tưởng.

Kiểm tra các entry:

```shell
jar --list --file hello-signed.jar
java -jar hello-signed.jar
```

Danh sách phải có `HelloJar.class`, `META-INF/MANIFEST.MF` và các file chữ ký `META-INF/*.SF`, `META-INF/*.RSA` tương ứng với RSA signer. Chạy ứng dụng vẫn in chính xác:

```text
Hello from JAR
```

Tên file chữ ký phát sinh từ alias nhưng có thể được chuẩn hóa để tuân thủ tên entry chữ ký; hãy xem output thực tế của `jar --list`.

## 5. Xác minh: từ kiểm tra cơ bản đến đọc chi tiết

Chạy theo thứ tự:

```shell
jarsigner -verify hello-signed.jar
jarsigner -verify -verbose -certs hello-signed.jar
jarsigner -verify -strict -verbose -certs hello-signed.jar
```

- `-verify` kiểm tra sự nhất quán chữ ký và digest của các entry thuộc phạm vi ký. Khi kiểm tra mật mã thành công, kết quả thường có `jar verified.`, dù có thể kèm warning.
- `-verbose` liệt kê entry; phần chú giải thường bao gồm `s = signature was verified`, `m = entry is listed in manifest`, `k = at least one certificate was found in keystore`. Ví dụ các cờ `sm` bên cạnh `HelloJar.class` cho biết entry này có chữ ký đã xác minh và được ghi trong manifest. `k` phụ thuộc keystore dùng để đối chiếu, **không tự chứng minh signer được mọi máy tin cậy**.
- `-certs` cùng `-verify -verbose` hiển thị signer DN, certificate chain, ngày hiệu lực, và TSA nếu có. Đọc certificate thực tế thay vì chỉ nhìn thông điệp một dòng.
- Với cặp khóa self-signed ở bước 2, `-strict` **được kỳ vọng trả về exit code khác 0**, thường có bit `4` do `signerSelfSigned`/`chainNotValidated`, mặc dù bytecode chưa hề bị sửa. Kết quả và wording khác nhau tùy phiên bản JDK, cấu hình trust và các warning đồng thời.

Sau lệnh `jarsigner -verify -strict ...`, lấy exit code **ngay lập tức**, trước khi chạy lệnh khác:

```bash
# Bash / zsh (Linux, macOS):
echo $?
```

```powershell
# PowerShell (Windows):
$LASTEXITCODE
```

Ví dụ `jar verified, with signer errors` nghĩa là chữ ký có thể đã xác minh về mặt mật mã nhưng có **severe warning** về signer/điều kiện phát hành. Ngược lại, thông báo lỗi `java.lang.SecurityException` liên quan digest/chữ ký có thể chỉ ra JAR đã bị thay đổi hoặc hỏng, cần coi là **verification failure**.

### Tự kiểm tra đúng signer/alias

Có thể đưa keystore và alias vào lệnh verify:

```shell
jarsigner -verify -strict -verbose -certs -keystore demo-signer.p12 -storetype PKCS12 hello-signed.jar demo-signer
```

Khi cung cấp alias, `jarsigner` kiểm tra chữ ký có phù hợp với certificate thuộc alias đó hay không; thiếu hoặc sai alias/keystore có thể dẫn đến severe warning loại `notSignedByAlias` hoặc `aliasNotInStore`. Tuy nhiên, khớp alias trong một keystore thử nghiệm vẫn **không loại bỏ thực tế certificate là self-signed**. Dùng CA/chính sách trust có kiểm soát khi xác minh artefact phát hành.

## 6. `-strict` thực sự thay đổi điều gì?

Không dùng `-strict`, một số tình huống nguy hiểm chỉ được in thành cảnh báo nhưng lệnh vẫn có thể trả mã `0` nếu phần ký/xác minh không gặp lỗi trực tiếp. Dùng `-strict`, **severe warnings** được phản ánh vào mã thoát để script/CI phát hiện. Nó không sửa certificate, không tải một CA mới, không tự kích hoạt kiểm tra thu hồi và không buộc mọi cảnh báo thông tin thành lỗi.

Bảng mã theo tài liệu `jarsigner` của JDK 21:

| Exit code / bit | Nhóm tình huống | Cách hiểu |
| --- | --- | --- |
| `0` | Không có failure hoặc severe warning | Có thể vẫn còn **informational warnings**, như `noTimestamp`; phải đọc log/chính sách. |
| `1` | `failure` | Lệnh thất bại, lỗi cú pháp/keystore/khóa, hoặc kiểm tra chữ ký/digest thất bại. |
| `4` | `hasExpiredCert`, `notYetValidCert`, `chainNotValidated`, `signerSelfSigned`, `disabledAlg`; một số lỗi hết hạn timestamp | Certificate/chain/thuật toán không đáp ứng yêu cầu nghiêm ngặt. |
| `8` | `badKeyUsage`, `badExtendedKeyUsage`, `badNetscapeCertType` | Extension certificate không cho phép mục đích ký mã. |
| `16` | `hasUnsignedEntry` | Có entry dữ liệu chưa ký, nằm ngoài phạm vi xác minh integrity. |
| `32` | `notSignedByAlias`, `aliasNotInStore` | Signer không khớp alias/keystore được yêu cầu. |
| `64` | `tsaChainNotValidated` | Chuỗi chứng thư của TSA không được xác minh hợp lệ. |

**Mã severe warning là bitmask (OR):** ví dụ `4 | 8 = 12` nếu vừa có vấn đề certificate vừa sai KeyUsage. `1` biểu thị failure; đừng áp dụng phép tách bit như một lời giải thích thay thế cho log failure. Nhiều nguyên nhân cùng nằm trong bit `4`, nên chỉ đọc mã `4` **không xác định được lỗi chính xác**: phải phân tích phần cảnh báo ngay phía trên. Mã và tên warning có thể thay đổi theo JDK; với môi trường thực, đối chiếu tài liệu của chính phiên bản đang sử dụng.

### Warning nghiêm trọng và warning thông tin

**Severe warnings** có thể khiến `-strict` trả khác `0`: signer self-signed, chain không xác minh được, chứng thư hết hạn/chưa có hiệu lực, extension không cho ký mã, thuật toán bị vô hiệu, entry chưa ký, sai alias, TSA chain lỗi.

**Informational warnings** thường **không khiến `-strict` thất bại nếu đứng một mình**:

- `noTimestamp`: chữ ký không kèm timestamp; sau khi chứng thư hết hạn hoặc bị thu hồi trong tương lai, việc chứng minh thời điểm ký có thể trở nên khó hoặc không đạt chính sách.
- `hasExpiringCert`: certificate sắp hết hạn (khoảng sáu tháng theo tài liệu).
- `hasExpiringTsaCert`: chứng thư TSA sắp hết hạn.
- `legacyAlg`: thuật toán cũ sắp không được khuyến nghị/chấp nhận ở tương lai.
- `extraAttributesDetected`: thuộc tính POSIX permission/symlink được giữ lại nhưng không được chữ ký bảo vệ.

Đặc biệt: **`noTimestamp` không có bit severe warning riêng** trong bảng trên. Vì vậy `-strict` trả `0` không chứng tỏ JAR có timestamp. Nếu chính sách phát hành yêu cầu TSA, phải kiểm tra thông tin `Timestamped by`/timestamp của signer trong output.

## 7. Bốn tình huống cần chẩn đoán đúng

### Tình huống A — JAR hợp lệ về digest nhưng signer self-signed

Bạn tạo `demo-signer.p12` bằng `keytool -genkeypair`, ký `hello-unsigned.jar` rồi chạy `-verify -strict`. Nội dung `HelloJar.class` có thể có `s` và `m`; `jar verified` có thể đi kèm `signerSelfSigned` hoặc `chainNotValidated`. Với `-strict` thường xuất hiện bit `4`. Đây là bài học về **trust**, không phải bằng chứng file bị sửa.

Cách xử lý khi phát hành: dùng certificate signing với chain được bên nhận chấp nhận; phân phối/kiểm tra chuỗi chứng thư phù hợp, không xóa cảnh báo chỉ để pipeline xanh.

### Tình huống B — Một entry đã ký bị sửa sau khi ký

Nếu thay byte của `HelloJar.class` trong archive **sau khi ký** mà không ký lại, digest của dữ liệu không khớp manifest đã ký. `jarsigner -verify` có thể báo `SecurityException` hoặc thông báo digest invalid và thất bại. Không nên “sửa manifest tay” hoặc dùng `-strict` như giải pháp: phải **build lại JAR cuối cùng rồi ký lại** bằng khóa hợp lệ.

**Quy tắc pipeline:** compile → test → package → sign → verify → publish. Bất kỳ bước nào thay nội dung JAR sau sign đều đòi hỏi ký/verify lại.

### Tình huống C — Thêm resource sau khi ký

Nếu thêm `extra.txt` vào JAR đã ký mà entry mới chưa có chữ ký, chữ ký của các entry cũ vẫn có thể hợp lệ, song entry mới **không được bảo vệ**. `-verbose` giúp phân biệt entry đã ký và chưa ký; `-strict` có thể phát `hasUnsignedEntry` (bit `16`). Không nhầm “chữ ký cũ còn đúng” với “mọi nội dung hiện có đều đã được ký”.

Khi kiểm tra đóng gói, lưu ý các entry kỹ thuật dưới `META-INF/` và thư mục có thể không có cờ ký theo cách hiển thị thông thường. Tập trung vào entry dữ liệu/class thực tế và cảnh báo của `jarsigner`.

### Tình huống D — Chữ ký không có timestamp

Lệnh ký trong bài cố ý không có `-tsa`, do đó có thể thấy `noTimestamp`. Đây là cảnh báo **informational**. Dùng timestamp từ TSA được tổ chức chấp nhận khi cần chứng minh chữ ký được tạo lúc certificate còn hiệu lực:

```text
jarsigner -keystore <signer-keystore.p12> -storetype PKCS12 -tsa <https://TSA-da-duoc-phe-duyet> -signedjar <signed.jar> <unsigned.jar> <signer-alias>
```

Đây là **cú pháp minh họa**, phải thay mọi placeholder bằng giá trị thực và có TSA hoạt động. Kết nối mạng, certificate của TSA và cấu hình thuật toán là các điều kiện đầu vào. Timestamp hợp lệ **không làm certificate self-signed trở thành đáng tin**, không chữa chữ ký bị sửa và không thay thế việc đánh giá trust chain.

## 8. Quy trình xác minh cho build/CI và phát hành

Trình tự kiểm tra được khuyến nghị cho JAR cuối cùng:

1. **Build và chốt artifact:** sau compile/package không còn bước sửa `.class`, manifest hay tài nguyên.
2. **Ký với khóa phát hành:** private key được bảo vệ (vault/HSM/PKCS11 hoặc cơ chế bí mật phù hợp), alias đúng, chain certificate thích hợp và TSA nếu chính sách yêu cầu.
3. **Verify chính JAR sẽ phát hành:** chạy `jarsigner -verify -strict -verbose -certs <artifact.jar>`; lưu log và exit code ngay khi lệnh kết thúc. Exit code khác `0` là tín hiệu chặn để điều tra.
4. **Đánh giá log ngoài mã thoát:** xem có entry unsigned, signer có đúng người/chứng thư mong đợi, thuật toán có bị disabled/legacy, timestamp có cần thiết, thời hạn/chain có thỏa chính sách, và `-revCheck` có được bật khi yêu cầu hay không.
5. **Chính sách chấp nhận:** chỉ phát hành khi kết luận về integrity, identity/trust và các yêu cầu bổ sung đều đạt. Không xem `jar verified.` riêng lẻ là đủ.

Ví dụ lệnh CI đối với **artifact thực tế đã ký bằng certificate phù hợp**:

```shell
jarsigner -verify -strict -verbose -certs release.jar
```

Script/CI phải kiểm tra exit code của chính lệnh trên và lưu stdout/stderr làm bằng chứng. Nếu policy yêu cầu timestamp hoặc một signer cụ thể, bổ sung kiểm tra có cấu trúc đối với log/certificate/keystore và đối chiếu signer được cho phép; không suy diễn từ `exit 0`. **Không dùng JAR self-signed của bài lab để giả lập kết quả CI production thành công.**

Với `-revCheck`, xác minh có thể cần OCSP/CRL và mạng. Không bật/tắt ngầm mà phải theo chính sách kiểm tra thu hồi của hệ thống. Kiểm soát vòng đời, quyền truy cập, thay mới và thu hồi khóa ký; mật khẩu không nên truyền bằng literal trên command line trong môi trường thật (có thể lộ qua lịch sử lệnh/process list). `jarsigner` hỗ trợ `-storepass:env`/`-storepass:file` trong các cấu hình phù hợp, và cũng có thể nhắc nhập tương tác.

## 9. Lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân có thể | Hành động kiểm tra |
| --- | --- | --- |
| `jarsigner: key associated with ... not a private key` hoặc không tìm thấy alias | Alias sai hoặc entry là trusted certificate thay vì `PrivateKeyEntry` | `keytool -list -v -keystore ... -alias ...`; dùng đúng keystore/alias chứa private key. |
| Lỗi mở keystore/mật khẩu | Sai file, `-storetype`, storepass/keypass hoặc hạn chế provider | Kiểm tra đường dẫn, loại keystore, mật khẩu và nhà cung cấp khóa; không in secret vào log. |
| `jar verified.` nhưng `-strict` báo signer errors | Severe warning về self-signed/chain/cert/unsigned entries | Đọc warning cụ thể và mã bitmask; xử lý theo nguyên nhân, không bỏ `-strict`. |
| `hasUnsignedEntry` | Bổ sung entry sau khi ký hoặc build/ký sai thứ tự | Kiểm tra các entry chưa ký, build hoàn chỉnh rồi ký lại. |
| `invalid ... digest`/`SecurityException` | Entry đã ký thay đổi, hỏng, hoặc chữ ký/manifest không nhất quán | So sánh artifact nguồn; rebuild, ký lại và verify; không sửa chữ ký thủ công. |
| `badKeyUsage` hoặc `badExtendedKeyUsage` | Certificate không được phép dùng cho code signing | Sử dụng certificate có KU/EKU đúng theo chính sách CA. |
| `noTimestamp` | Không dùng TSA khi ký | Nếu policy yêu cầu, ký với TSA được phê duyệt và kiểm tra timestamp sau đó. |
| `disabledAlg` / `legacyAlg` | Thuật toán/key size vi phạm hoặc sắp vi phạm chính sách JDK | Dùng thuật toán/khóa hiện hành; không nới `jdk.jar.disabledAlgorithms` để né kiểm tra. |
| Kết quả khác giữa hai máy | Khác phiên bản JDK, truststore, chính sách thuật toán, thời gian, revocation hoặc certificate chain | Ghi nhận `java -version`, tham số, signer, chain và log; chuẩn hóa môi trường kiểm thử. |

## 10. Câu hỏi tự kiểm tra

1. **`jar verified.` có đủ để tin nhà phát hành không?** Không. Nó nói về xác minh chữ ký/digest; cần đánh giá signer identity, certificate chain, chính sách trust và warnings.
2. **Vì sao ví dụ lab có thể trả `4` trong `-strict`?** Signer được tạo bằng `keytool -genkeypair` là self-signed; `signerSelfSigned` và `chainNotValidated` thuộc nhóm severe bit `4`.
3. **`noTimestamp` có khiến `-strict` trả nonzero không?** Nếu chỉ có `noTimestamp` thì không; đây là informational warning. Policy riêng vẫn có thể từ chối artifact thiếu TSA.
4. **Hai warning cùng lúc sẽ ra mã gì?** Severe warning codes được OR, ví dụ `4` và `8` thành `12`; phải đọc log để xác định từng nguyên nhân.
5. **Thêm `config.properties` sau khi ký có an toàn không?** Không thể mặc định coi là được bảo vệ. Entry mới có thể unsigned dù chữ ký của entry cũ vẫn hợp lệ.
6. **Khi nào dùng `-revCheck`?** Khi chính sách yêu cầu kiểm tra revocation; cần dự liệu OCSP/CRL và kết nối mạng.
7. **Vì sao nên dùng `-signedjar`?** Để có JAR đầu ra riêng và tránh vô tình ghi đè artifact chưa ký, thuận tiện đối chiếu và tái lập.

## 11. Checklist thực hành

- [ ] Tạo `HelloJar.java`, chạy `javac`, đóng gói và chạy `hello-unsigned.jar` thành công.
- [ ] Tạo `demo-signer.p12` bằng `keytool`; xác nhận alias `demo-signer` là `PrivateKeyEntry`.
- [ ] Ký sang `hello-signed.jar`, quan sát hai file chữ ký trong `META-INF` và chạy JAR như trước.
- [ ] Chạy `-verify`, `-verify -verbose -certs` và `-verify -strict -verbose -certs`; đối chiếu cờ `s/m/k` với các entry.
- [ ] Lấy exit code ngay sau `-strict`, nhận diện severe warning self-signed/chain và tách bạch với informational `noTimestamp`.
- [ ] Giải thích được hành vi khi entry đã ký bị sửa hoặc entry mới chưa ký xuất hiện.
- [ ] Đề xuất được luồng build → sign → verify → publish với certificate/trust/TSA đúng chính sách.

**Nguồn đối chiếu theo JDK 21:** [jarsigner command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jarsigner.html) (cấu trúc chữ ký, `-verify`, `-strict`, warning/exit code) và [keytool command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html) (PKCS12, `-genkeypair`, X.509 KU/EKU). Khi triển khai trên JDK khác, kiểm tra tài liệu và cấu hình `java.security` của đúng phiên bản đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-003">19.3 Timestamp, thời hạn chứng thư và giới hạn của kết quả xác minh JAR</a>

<details>
<summary>Xem chi tiết</summary>

# 19.3. Timestamp, thời hạn chứng thư và giới hạn của kết quả xác minh JAR

> **Mục tiêu:** Phân biệt chữ ký mật mã trên các entry của JAR, độ tin cậy của chứng thư ký mã, bằng chứng về thời điểm ký và quyết định có chấp nhận artifact hay không. Biết dùng `jarsigner` để đọc kết quả đúng và nhận ra điều mà dòng `jar verified.` **không** bảo đảm.

## 1. Ba câu hỏi khác nhau khi kiểm tra JAR

Một JAR được ký số thường cần trả lời **ba câu hỏi độc lập**:

1. **Tính toàn vẹn:** Những entry được ký có còn đúng byte như khi ký không? Đây là việc kiểm tra chữ ký và digest.
2. **Danh tính và độ tin cậy:** Chứng thư chứa khóa công khai của signer thuộc về ai, chuỗi chứng thư có được xác thực tới trust anchor mà bên sử dụng chấp nhận không, và chứng thư có được phép dùng để ký mã không?
3. **Thời gian:** Có bằng chứng đáng tin cho thấy chữ ký đã tồn tại khi chứng thư còn hiệu lực không? Chứng thư đã hết hạn ở thời điểm kiểm tra có nhất thiết làm chữ ký lịch sử mất giá trị không?

Ba câu hỏi này liên quan nhưng không thay thế nhau. Một chữ ký đúng về mặt toán học có thể được tạo bởi chứng thư tự ký không đáng tin, bởi khóa đã bị lộ, hoặc chỉ bảo vệ một phần nội dung trong archive. Một timestamp hợp lệ không tự biến mã không an toàn thành mã an toàn.

## 2. Nhắc lại phạm vi ký trước khi xét thời gian

Timestamp không đứng độc lập: nó gắn với **chữ ký JAR**, trong khi các byte được ký nằm ở nhiều lớp. Khi ký bằng `jarsigner`, `MANIFEST.MF` chứa digest của **nội dung entry**, `META-INF/<SIGNER>.SF` bảo vệ thông tin tương ứng của manifest, và signature block `.RSA`/`.EC`/`.DSA` chứa chữ ký số trên `.SF`, certificate signer và có thể cả timestamp token của TSA. Cơ chế nhiều lớp này được giải thích kỹ ở bài 19.1; bài hiện tại dùng nó để đánh giá **thời điểm ký và hạn chứng thư**.

```text
entry đã ký → digest trong MANIFEST.MF → digest trong SIGNER.SF
                                          → chữ ký trong SIGNER.RSA/.EC
                                          → timestamp TSA (nếu có)
```

Verifier phải xác minh chữ ký trên `.SF` và digest của từng entry **được đọc đến hết**, sau đó mới áp dụng chính sách chứng thư/timestamp. Timestamp hợp lệ không biến các entry **chưa ký** thành được bảo vệ và không bảo đảm toàn bộ byte của ZIP (như phương pháp nén hoặc metadata) bất biến. Entry thêm sau khi ký có thể là unsigned dù entry cũ còn hợp lệ; khi quyết định chấp nhận artifact phải kiểm kê phạm vi ký và đọc cảnh báo `hasUnsignedEntry`.

## 3. Timestamp mật mã là gì, và không phải là gì?

**Timestamp của chữ ký JAR** là một token thời gian được một **Time Stamping Authority (TSA)** ký, theo giao thức như RFC 3161. Khi ký với `jarsigner -tsa`, công cụ trao đổi với TSA để lấy token cho dấu vết mật mã gắn với chữ ký rồi lưu token vào signature block. Bên kiểm tra có thể kiểm tra token này với danh tính, chuỗi chứng thư, chữ ký và chính sách tin cậy của TSA.

Timestamp đáng tin tạo căn cứ rằng **chữ ký đã tồn tại vào thời điểm TSA xác nhận**, nhờ đó có thể đánh giá chứng thư của signer tại thời điểm ký. Nó đặc biệt hữu ích khi JAR vẫn được sử dụng sau ngày `notAfter` của chứng thư signer.

Đừng nhầm với:

- Thời gian `last modified` của file `.jar` trên hệ điều hành hoặc trong ZIP: có thể bị thay đổi, không phải bằng chứng thời gian mật mã.
- Ngày build, ngày phát hành hoặc timestamp ghi trong manifest: là metadata do quy trình build tự ghi; một mình chúng không phải chứng thực độc lập của TSA.
- Ngày hiện tại của máy kiểm tra: là thời điểm đánh giá, không chứng minh được lúc chữ ký được tạo.
- Thời gian hết hạn của chứng thư TSA: là một giới hạn khác cần xem xét khi xác minh token; timestamp không có giá trị vĩnh viễn bất kể chứng thư/thuật toán/chính sách.

Một timestamp hợp lệ **không chứng minh** signer không bị đánh cắp khóa, nội dung không có mã độc, chứng thư chưa từng bị thu hồi, hoặc hệ thống build là an toàn. Nó cũng không thay thế kiểm tra chuỗi chứng thư TSA và chính sách của bên nhận.

## 4. Thời hạn chứng thư: phải xét thời điểm nào?

Chứng thư X.509 có khoảng hiệu lực từ `notBefore` đến `notAfter`. Nhưng đánh giá một JAR cần phân biệt:

- **Signer certificate:** chứng thư của chủ thể đã ký JAR; xem `notBefore`/`notAfter`, KeyUsage/ExtendedKeyUsage phù hợp code signing, đường dẫn chứng thư tới nguồn tin cậy và trạng thái thu hồi khi chính sách yêu cầu.
- **TSA certificate:** chứng thư bảo vệ timestamp; cũng cần xem chuỗi tin cậy, tính hợp lệ của token, thời hạn và thuật toán.
- **Thời điểm kiểm tra:** có thể muộn hơn rất nhiều so với thời điểm ký. Có timestamp hợp lệ cho phép xem xét tính hợp lệ của signer certificate tại thời điểm có bằng chứng ký, thay vì chỉ dùng trạng thái còn hạn hôm nay.

Ví dụ giả định: chứng thư ký mã có hiệu lực từ **01/01/2025** đến **31/12/2025**.

| Tình huống | Kết luận đúng |
| --- | --- |
| JAR ký ngày 01/10/2025, TSA đáng tin xác nhận cùng thời điểm; kiểm tra ngày 01/06/2026 | Việc chứng thư signer đã hết hạn **không tự nó** bác bỏ chữ ký lịch sử; còn phải kiểm tra timestamp, chuỗi tin cậy, thu hồi, thuật toán, nội dung và chính sách. |
| JAR có cùng chữ ký nhưng **không** có timestamp đáng tin; kiểm tra ngày 01/06/2026 | Không có bằng chứng thời điểm ký đủ mạnh từ timestamp; công cụ có thể cảnh báo/hạn chế chấp nhận khi chứng thư signer hết hạn. |
| TSA xác nhận chữ ký sau ngày 31/12/2025 | Timestamp không làm cho việc ký ngoài thời hạn chứng thư trở thành hợp lệ. |
| Chứng thư signer bị thu hồi do khóa lộ trước thời điểm timestamp | Timestamp không xóa sự kiện thu hồi/compromise; phải xét thời điểm và lý do thu hồi theo chính sách tin cậy. |
| Timestamp có nhưng chuỗi TSA không được tin cậy hoặc thuật toán đã bị vô hiệu hóa | Không thể dựa vào timestamp đó để kết luận thời gian ký đáng tin. |

**Gia hạn chứng thư không đồng nghĩa kéo dài chứng thư cũ.** Thực tế vận hành là cấp chứng thư mới và ký các bản phát hành mới bằng khóa/chứng thư phù hợp; lưu artifact cũ kèm timestamp và hồ sơ xác minh. Nếu cần tính xác thực trong nhiều năm, phải có chính sách bảo quản bằng chứng và nâng cấp thuật toán thích hợp.

## 5. Đọc `jarsigner -verify` và exit code cho đúng

Lệnh kiểm tra thường dùng:

```bash
jarsigner -verify -verbose -certs -strict app-signed.jar
```

Ý nghĩa các tùy chọn:

- `-verify`: kiểm tra chữ ký của JAR thay vì ký.
- `-verbose`: hiện các entry và trạng thái; `-certs` hiển thị thêm thông tin chứng thư khi kết hợp với `-verbose`.
- `-strict`: biến **cảnh báo nghiêm trọng** thành mã thoát khác 0; hữu ích cho CI/CD, nhưng không tự thay thế policy.
- `-revCheck`: bật kiểm tra thu hồi chứng thư qua OCSP/CRL khi khả dụng; có thể cần mạng và cấu hình PKIX/nguồn tin cậy.

Ví dụ kiểm tra thêm trạng thái thu hồi:

```bash
jarsigner -verify -verbose -certs -strict -revCheck app-signed.jar
```

Khi `-verbose` liệt kê entry, các ký tự thường gặp là `s` (chữ ký entry được xác minh), `m` (entry có trong manifest), `k` (tìm được ít nhất một chứng thư tương ứng trong keystore). **`k` không phải giấy bảo đảm nhà phát hành đúng như kỳ vọng của hệ thống.**

Cần đọc cả dòng cảnh báo, thông tin signer/TSA, trạng thái từng entry và exit code. `jar verified.` nói rằng các kiểm tra chữ ký áp dụng đã thành công; nó **không** đồng nghĩa mọi entry đều được ký, chứng thư được mọi nơi tin cậy, hay phần mềm an toàn. Không dùng `grep "jar verified"` làm điều kiện duy nhất cho pipeline.

Một số cảnh báo cần phân loại:

| Dấu hiệu | Cách hiểu và phản ứng |
| --- | --- |
| `noTimestamp` | Không có timestamp mật mã. Cần timestamp nếu quy trình phát hành yêu cầu khả năng kiểm chứng sau khi chứng thư signer hết hạn. |
| `hasExpiredCert` / `notYetValidCert` | Chứng thư signer ngoài thời hạn theo bối cảnh kiểm tra; xem timestamp và cảnh báo đầy đủ, không tự suy diễn từ một dòng. |
| `hasExpiredTsaCert` / `tsaChainNotValidated` | Bằng chứng thời gian hoặc chuỗi TSA có vấn đề; không dựa vào timestamp khi chưa xử lý. |
| `chainNotValidated` / `signerSelfSigned` | Chưa có chuỗi tin cậy phù hợp hoặc dùng chứng thư tự ký; chỉ chấp nhận khi có trust policy rõ ràng. |
| `badKeyUsage` / `badExtendedKeyUsage` | Chứng thư không phù hợp mục đích ký mã theo extension; không bỏ qua. |
| `hasUnsignedEntry` | Có entry không được bảo vệ tính toàn vẹn bằng chữ ký; rà soát toàn bộ payload và policy. |
| `disabledAlg` / `legacyAlg` | Thuật toán đã bị cấm hoặc đang bị coi là cũ/rủi ro; xem chính sách JDK đích và lập kế hoạch ký lại. |
| `jar is unsigned` | Không có chữ ký JAR có thể xác minh (hoặc chữ ký bị coi là không hợp lệ/không thể phân tích); không được coi là đã ký. |

Khi không có `-strict`, `jarsigner` vẫn có thể trả **exit code 0 dù có cảnh báo nghiêm trọng**; với `-strict` các cảnh báo nghiêm trọng ảnh hưởng đến exit code (các nhóm cảnh báo có thể được kết hợp theo bit). Cảnh báo thông tin, ví dụ `noTimestamp`, vẫn có thể đi cùng exit code 0. Vì vậy CI phải bổ sung policy cụ thể cho yêu cầu phát hành.

Cũng cần xem `jdk.jar.disabledAlgorithms` và `jdk.security.legacyAlgorithms` trong `$JAVA_HOME/conf/security/java.security` của **JDK thực tế dùng để kiểm tra/chạy**. Chữ ký sử dụng thuật toán bị cấm có thể bị coi như không ký, kể cả khi một JDK cũ trước đây chấp nhận. Đừng mặc định kết quả của mọi phiên bản JDK là giống nhau.

## 6. Thực hành JDK 17+: tạo, ký và kiểm tra JAR

Bài thực hành dùng **chứng thư tự ký, không có TSA**, nhằm làm rõ giới hạn của kết quả. Chạy các lệnh dưới đây trong Bash (Linux/macOS hoặc môi trường có Bash) với `jar`, `keytool`, `jarsigner` từ JDK 17 trở lên:

```bash
mkdir -p jar-sample
printf 'Hello signed archive\n' > jar-sample/payload.txt
jar --create --file input.jar -C jar-sample .

keytool -genkeypair \
  -alias lab \
  -keyalg RSA \
  -keysize 3072 \
  -validity 30 \
  -storetype PKCS12 \
  -keystore lab.p12 \
  -dname "CN=JAR Signing Lab, O=Training, C=VN"

jarsigner \
  -keystore lab.p12 \
  -storetype PKCS12 \
  -signedjar signed.jar \
  input.jar lab

jarsigner -verify -verbose -certs -strict signed.jar
jar --list --file signed.jar
```

`keytool` và `jarsigner` yêu cầu nhập mật khẩu tương tác. Không đưa mật khẩu khóa riêng hoặc keystore vào script/command line của bản phát hành. Vì đây là chứng thư **tự ký** và **không có TSA**, hãy dự kiến cảnh báo về mức độ tin cậy và thiếu timestamp; `-strict` có thể trả exit code khác 0 dù phần digest/chữ ký của payload vẫn xác minh được. Không dùng keystore của lab làm credential phát hành.

Trong quy trình ký phát hành có chứng thư đáng tin, người ký cấu hình URL TSA RFC 3161 thực tế vào biến môi trường `TSA_URL`, sau đó có thể dùng mẫu sau (các file và alias phải tồn tại trong hệ thống phát hành):

```bash
jarsigner \
  -keystore release.p12 \
  -storetype PKCS12 \
  -tsa "$TSA_URL" \
  -signedjar app-signed.jar \
  app-unsigned.jar release

jarsigner -verify -verbose -certs -strict app-signed.jar
```

Chỉ định `-tsa` tạo yêu cầu tới TSA. Sau khi ký, phải **kiểm tra lại output** để xác nhận thực sự có timestamp được công cụ nhận diện, chuỗi chứng thư TSA được tin cậy và không có cảnh báo nghiêm trọng. Nếu TSA không truy cập được hoặc token không hợp lệ thì không xem việc gọi lệnh là bằng chứng đã timestamp thành công.

### Thử bổ sung một entry sau khi ký

Tiếp tục trên **`signed.jar` của lab**, không làm với artifact phát hành:

```bash
mkdir -p later
printf 'added after signing\n' > later/addition.txt
jar --update --file signed.jar -C later addition.txt
jarsigner -verify -verbose -certs -strict signed.jar
```

Entry `payload.txt` vẫn có thể giữ chữ ký hợp lệ, nhưng `addition.txt` xuất hiện **sau khi ký** nên không được chữ ký cũ bảo vệ; `jarsigner` có thể báo `hasUnsignedEntry`. Đây là lý do kiểm tra chữ ký và kiểm tra **phạm vi entry được ký** phải đi cùng nhau. Nếu sửa byte của một entry đã ký, kiểm tra digest/chữ ký tương ứng sẽ thất bại.

## 7. Java API: phải đọc hết entry trước khi hỏi signer/timestamp

Trong ứng dụng Java, `JarFile` có thể thực hiện kiểm tra chữ ký khi đọc dữ liệu entry. Với entry đã ký, `JarEntry.getCodeSigners()` cho biết signer; mỗi `CodeSigner` có thể chứa `Timestamp`. Không hỏi metadata signer trước khi đọc đủ bytes rồi coi kết quả `null` là kết luận cuối cùng.

Tạo file **`InspectJarEntry.java`**:

```java
import java.io.OutputStream;
import java.security.CodeSigner;
import java.security.Timestamp;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class InspectJarEntry {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java InspectJarEntry <jar> <entry>");
            System.exit(2);
        }

        try (JarFile jar = new JarFile(args[0], true)) {
            JarEntry entry = jar.getJarEntry(args[1]);
            if (entry == null || entry.isDirectory()) {
                throw new IllegalArgumentException("Entry not found: " + args[1]);
            }

            try (var input = jar.getInputStream(entry)) {
                input.transferTo(OutputStream.nullOutputStream());
            }

            CodeSigner[] signers = entry.getCodeSigners();
            System.out.println("signed=" + (signers != null && signers.length > 0));

            if (signers != null) {
                for (CodeSigner signer : signers) {
                    Timestamp ts = signer.getTimestamp();
                    System.out.println("timestamp=" +
                        (ts == null ? "none" : ts.getTimestamp().toInstant()));
                }
            }
        }
    }
}
```

Biên dịch/chạy với JDK 17+ và JAR của lab **trước khi thêm `addition.txt`**:

```bash
javac InspectJarEntry.java
java InspectJarEntry signed.jar payload.txt
```

Với bản ký trong lab không có TSA, đầu ra kỳ vọng cho `payload.txt`:

```text
signed=true
timestamp=none
```

`signed=true` nghĩa là API đã tìm được signer cho entry sau khi đọc/kiểm tra nội dung. **Nó không tự kiểm tra chính sách tin cậy PKIX của ứng dụng, đối tượng signer mong đợi, kiểm tra thu hồi và độ an toàn của mã.** Với entry không ký, `getCodeSigners()` thường trả `null`. Với entry có digest sai, thao tác đọc có thể ném `SecurityException`. Muốn ra quyết định cho *cả JAR*, ứng dụng phải kiểm tra tất cả entry quan trọng, xử lý trường hợp không ký và áp dụng một trust policy độc lập.

## 8. Những giới hạn của kết quả xác minh

Một lần `jarsigner -verify` không tự trả lời các câu hỏi sau:

1. **Có đúng bên phát hành dự kiến không?** Chữ ký có thể khớp với public key nằm trong JAR, nhưng chỉ quy trình xác thực chuỗi chứng thư và kiểm tra publisher/subject/key dự kiến mới xác lập quyền tin cậy.
2. **Tất cả payload cần thiết đều được ký chưa?** Entry mới, nội dung không nằm trong phạm vi ký, nhiều signer và tài nguyên trong `META-INF` đều cần xét theo phạm vi sử dụng.
3. **Chứng thư có bị thu hồi không?** Kiểm tra thu hồi không được bật mặc định chỉ vì có `-verify`; `-revCheck` có thể phụ thuộc OCSP/CRL và mạng. Không có kết quả thu hồi chưa chắc có nghĩa “không bị thu hồi”.
4. **Timestamp có đáng tin và đúng thời điểm không?** Phải xác minh token TSA và chính sách thời gian; dữ liệu ngày tháng của file không đủ.
5. **JDK đang chạy có chấp nhận thuật toán không?** Các phiên bản JDK và `java.security` có thể áp đặt chính sách khác nhau về khóa, digest và chữ ký.
6. **Mã có an toàn/đúng chức năng không?** Ký mã không quét malware, không rà lỗ hổng dependency, không chứng minh source được build từ commit kỳ vọng và không bảo đảm môi trường thực thi an toàn.
7. **Artifact có đúng bản được phê duyệt không?** Vẫn cần pin tên publisher/khóa hoặc chứng thư mong đợi, hash artifact, provenance/build attestation và kiểm soát phân phối thích hợp.

Ngoài ra, khi ứng dụng tự đọc JAR bằng API, chữ ký của entry thường được xác minh khi **đọc hết dữ liệu entry**. Việc chỉ liệt kê tên file hoặc mở archive chưa chứng minh tất cả payload đã qua kiểm tra.

## 9. Checklist thực tế cho pipeline phát hành

Một policy kiểm tra JAR nghiêm túc nên thực hiện theo thứ tự:

1. **Xác nhận artifact kỳ vọng:** nguồn tải/phiên bản, hash SHA-256 của toàn JAR và provenance độc lập nếu có.
2. **Kiểm tra chữ ký:** dùng JDK mục tiêu, chạy `jarsigner -verify -verbose -certs -strict`; đọc exit code và toàn bộ output.
3. **Kiểm tra phạm vi:** kiểm kê entry thực thi, resources, service descriptors, cấu hình; không chấp nhận entry quan trọng không ký hoặc sự pha trộn signer ngoài policy.
4. **Kiểm tra danh tính:** chuỗi chứng thư signer/TSA, trust anchor, publisher hoặc public key dự kiến, KeyUsage/EKU và thời hạn đúng thời điểm.
5. **Kiểm tra thời gian/thu hồi:** đòi timestamp đáng tin nếu cần lưu hành sau khi chứng thư hết hạn; sử dụng `-revCheck` và chiến lược xử lý OCSP/CRL lỗi phù hợp mô hình rủi ro.
6. **Kiểm tra chính sách thuật toán:** vô hiệu hóa thuật toán yếu, xác minh trên các runtime Java thực sự hỗ trợ; không chỉ dựa vào máy build.
7. **Kiểm tra nội dung:** kết hợp kiểm thử, phân tích bảo mật, quản lý SBOM/dependencies và xác minh chuỗi cung ứng. Ghi lại bằng chứng của lần kiểm tra.

Nếu CI bắt buộc timestamp, phải coi cảnh báo `noTimestamp` là không đạt **theo policy**, kể cả khi công cụ trả exit code 0. Nếu CI bắt buộc mọi entry quan trọng có chữ ký thì phải kiểm tra rõ điều đó, kể cả khi dòng `jar verified.` xuất hiện.

## 10. Tự kiểm tra kiến thức

**Câu 1.** Một JAR có chữ ký và timestamp đáng tin lúc chứng thư signer còn hạn; hôm nay chứng thư hết hạn. Kết luận “JAR chắc chắn vô hiệu” có đúng không?

**Đáp:** Không. Timestamp có thể chứng minh thời điểm ký phù hợp. Vẫn cần xác minh TSA, đường dẫn chứng thư, thu hồi, phạm vi ký, thuật toán và policy.

**Câu 2.** Sau khi ký, một người thêm `config.properties` vào JAR. `jarsigner` còn kiểm tra đúng chữ ký của `Main.class`. Có thể kết luận mọi nội dung JAR nguyên vẹn không?

**Đáp:** Không. `Main.class` có thể còn nguyên, nhưng `config.properties` mới chưa được chữ ký cũ bảo vệ và có thể ảnh hưởng chương trình.

**Câu 3.** Dòng `jar verified.`, exit code 0 và chứng thư tự ký có đủ để chấp nhận artifact từ bên ngoài không?

**Đáp:** Không. Cần trust policy về publisher/khóa, chuỗi chứng thư, cảnh báo và entry được ký. Nếu không có `-strict`, cảnh báo nghiêm trọng vẫn có thể đi cùng exit code 0.

**Câu 4.** Thay `mtime` của file JAR thành ngày trước hạn chứng thư có thay được timestamp của TSA không?

**Đáp:** Không. `mtime` không được TSA ký và không tạo bằng chứng thời điểm của chữ ký.

## Tài liệu chuẩn để đối chiếu

- Oracle Java SE 25, **The jarsigner Command**: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jarsigner.html
- Oracle Java SE 25, **JAR File Specification – Signed JAR File**: https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html
- Oracle Java SE 25, **`java.security.Timestamp` API**: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Timestamp.html
- IETF **RFC 3161 – Internet X.509 PKI Time-Stamp Protocol (TSP)**: https://www.rfc-editor.org/rfc/rfc3161

**Nguyên tắc nhớ lâu:** Chữ ký xác nhận tính toàn vẹn của phần được ký; chứng thư và trust policy quyết định có tin signer không; TSA hỗ trợ chứng minh *khi nào* chữ ký tồn tại. Phải kiểm tra đủ cả ba trước khi chấp nhận JAR.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-004">19.4 Phân biệt tính toàn vẹn gói JAR với độ tin cậy hoặc quyền thực thi của mã</a>

<details>
<summary>Xem chi tiết</summary>

# 19.4 Phân biệt tính toàn vẹn gói JAR với độ tin cậy hoặc quyền thực thi của mã

> **Câu hỏi trọng tâm:** JAR có chữ ký hợp lệ thì điều gì đã được xác minh, ai quyết định người ký có đáng tin, và điều gì cho phép mã chạy hoặc truy cập tài nguyên?

## 1. Ba câu hỏi phải trả lời riêng

| Lớp kiểm soát | Câu hỏi cần trả lời | Bằng chứng/cơ chế điển hình | Điều **không** được suy ra |
| --- | --- | --- | --- |
| **Tính toàn vẹn (integrity)** | Các entry được bảo vệ trong JAR có còn đúng nội dung khi ký không? | Digest từng entry, chữ ký số, `jarsigner -verify` | Mã an toàn, tác giả đáng tin hoặc được cấp quyền chạy |
| **Danh tính và độ tin cậy (authenticity/trust)** | Chữ ký gắn với khóa nào, và tổ chức có chấp nhận khóa/người ký đó không? | Chứng thư X.509, chuỗi chứng thư, trust anchor hoặc khóa được pin, chính sách tin cậy | Nội dung không có mã độc hoặc không có lỗ hổng |
| **Quyền thực thi (authorization/privileges)** | Ai được khởi chạy chương trình; tiến trình được đọc, ghi, kết nối tới đâu? | Quyền người dùng hệ điều hành, ACL, container, sandbox của hệ điều hành, quyền dịch vụ và chính sách triển khai | Quyền được tự động cấp chỉ vì JAR có chữ ký |

Ba câu hỏi độc lập nhưng bổ sung cho nhau:

- **Chữ ký đúng**: nội dung **được chữ ký bao phủ** khớp với bản đã ký bằng khóa riêng tương ứng.
- **Người ký đáng tin**: bên triển khai có căn cứ độc lập để tin rằng khóa/chứng thư thuộc nhà phát hành được chấp nhận.
- **Được phép chạy**: môi trường triển khai chủ động cho phép thực thi, với các quyền cụ thể.

Không có phép suy luận “`jarsigner` báo `jar verified` ⇒ mã an toàn ⇒ Java tự cho quyền cao hơn”. Một JAR chưa ký vẫn có thể chạy; một JAR đã ký vẫn có thể chứa mã độc.

## 2. Ranh giới của dữ liệu mà chữ ký JAR bảo vệ

Cơ chế JAR signing gồm ba mắt xích: `MANIFEST.MF` ghi digest của từng **entry được ký**; tệp `META-INF/<SIGNER>.SF` ghi digest của manifest/những section liên quan; signature block `.RSA`/`.EC` chứa chữ ký trên `.SF` và certificate signer. Khi verifier đọc hết một entry được ký, nội dung mới được đối chiếu với digest đã được chữ ký bảo vệ. Bài 19.1 đi sâu vào cấu trúc; ở đây trọng tâm là **không được suy diễn phạm vi hoặc quyền từ chữ ký**.

Một JAR ký **không mã hóa** dữ liệu và không cam kết mọi byte ZIP/metadata bất biến. Có thể thêm entry mới chưa ký trong khi chữ ký trên entry cũ vẫn đúng; resource/`.class` mới được classloader sử dụng có thể thay đổi hành vi thực tế. Cũng có thể có nhiều signer khác nhau theo entry, hoặc thuật toán chữ ký bị JDK mới vô hiệu. Do đó, trước quyết định triển khai phải kiểm tra từng entry quan trọng, mọi cảnh báo `hasUnsignedEntry` và chính sách signer cho phép.

Chữ ký của `app.jar` không bao phủ các JAR phụ thuộc, file cấu hình ngoài archive hoặc dữ liệu tải lúc chạy. Ngay cả khi mọi entry cần thiết đều đã ký và verify đúng, nó chỉ thiết lập liên kết mật mã tới **khóa ký**, không tự quyết định ai được tin cậy hay tiến trình có quyền gì.

## 3. Chữ ký hợp lệ không đồng nghĩa người ký đáng tin

Chữ ký số cho biết bên kiểm tra đã dùng **public key phù hợp với private key tạo chữ ký**. Chứng thư đính kèm trong JAR có thể đủ để thực hiện phép kiểm tra toán học, nhưng chứng thư đó **có thể tự ký (self-signed)** hoặc không xây được chuỗi tới một tổ chức phát hành được tin cậy.

Hãy tách hai phép kiểm tra:

**A. Xác minh mật mã:** Chữ ký và digest có khớp không? Những entry nào thực sự có signer? Có thuật toán bị vô hiệu hóa không?

**B. Xác lập độ tin cậy:** Public key/chứng thư đó có đúng của nhà phát hành mong đợi không? Chuỗi chứng thư có kết thúc ở trust anchor được chấp nhận không? Chứng thư có đúng thời hạn, mục đích ký mã (`KeyUsage`/`ExtendedKeyUsage` khi áp dụng), tình trạng thu hồi và chính sách tổ chức không? Có cần đối chiếu fingerprint/key pin với nguồn tin cậy độc lập không?

Các cảnh báo như `signerSelfSigned`, `chainNotValidated`, `hasExpiredCert`, `badExtendedKeyUsage`, `disabledAlg` hoặc `noTimestamp` cần được xem xét theo chính sách. Chúng không giống lỗi digest do sửa file, nhưng có thể khiến artefact **không đủ điều kiện phát hành/chấp nhận**.

**Timestamp** do dịch vụ TSA cung cấp (nếu được dùng và xác minh) có thể giúp chứng minh thời điểm ký khi đánh giá hiệu lực chứng thư về sau. Timestamp **không chứng minh mã sạch**, không chữa chữ ký sai và không thay thế kiểm tra khóa người ký/chuỗi tin cậy.

Ngay cả chữ ký với chứng thư tin cậy cũng không chứng minh chất lượng mã: nhà phát hành có thể mắc lỗi, cố ý gửi mã nguy hiểm hoặc bị lộ private key. **Trust là một quyết định có chính sách, không phải thuộc tính tự sinh từ lệnh ký.**

### Chữ ký JAR khác checksum SHA-256 ở điểm nào?

- `SHA-256(file.jar)` so với một checksum **đã lấy từ kênh đáng tin** có thể kiểm tra file tải về có đúng byte hay không.
- Chữ ký số sử dụng private key và public key, giúp gắn bản được ký với một khóa. Nhưng muốn gắn khóa với **danh tính** thật, vẫn cần kiểm tra chứng thư/pinning.
- Nếu kẻ tấn công thay cả JAR lẫn checksum trên cùng trang bị chiếm quyền, checksum ở trang đó không đủ để xác lập nguồn gốc. Nếu kẻ tấn công nắm private key ký, một chữ ký hợp lệ cũng không đủ bảo đảm an toàn.

## 4. Chữ ký không phải giấy phép thực thi và cũng không phải sandbox

Lệnh `java -jar app.jar` do người dùng hoặc tiến trình chủ động gọi. Trong cấu hình Java thông thường, JVM **không yêu cầu mọi JAR đều phải được ký** mới được chạy. JAR ký số cũng **không tự xin hoặc nhận** quyền đọc file, mở socket, ghi database, chạy tiến trình con hoặc truy cập bí mật.

Có ba phạm vi quyền khác nhau:

1. **Có được khởi chạy không?** Phụ thuộc vào cách triển khai, người khởi chạy, filesystem/OS, admission policy, công cụ quản lý ứng dụng; chữ ký có thể là một điều kiện do **bên triển khai đặt ra**.
2. **Mã chạy trong tiến trình được làm gì?** Phụ thuộc quyền tài khoản OS, container/namespace, ACL, network policy, token/IAM và các cơ chế cô lập đang áp dụng. Mã Java thường có thể sử dụng các khả năng của chính tiến trình mà không cần “quyền bổ sung vì được ký”.
3. **Có truy cập được API/module Java không?** Quy tắc JPMS (`exports`, `opens`), visibility và class loading là các giới hạn kỹ thuật khác; chúng không thay thế xác minh nhà phát hành hay quyền của hệ điều hành.

**Bối cảnh phiên bản Java:**

- Trên **JDK 17–23**, `SecurityManager` đã bị đánh dấu deprecated for removal; một số ứng dụng cũ có thể cấu hình chính sách `java.policy` và quy tắc `signedBy` để liên hệ signer với các quyền Java. Đây là **chính sách được cấu hình tường minh** ở môi trường hỗ trợ, không phải hành vi tự động của chữ ký.
- Từ **JDK 24**, `SecurityManager` bị vô hiệu hóa vĩnh viễn, không thể bật lại lúc khởi động hoặc cài đặt trong runtime. Thiết kế bảo mật mới không nên dựa vào `java.policy`/`signedBy` để cách ly mã không đáng tin.
- Với ứng dụng hiện đại, dùng biện pháp ngoài JVM phù hợp như tài khoản dịch vụ ít quyền, quyền filesystem, container, giới hạn mạng, quản lý secrets, admission control và cô lập tiến trình.

Chứng thư mã hóa/chữ ký có thể được chính ứng dụng hoặc hệ thống phân phối dùng làm tín hiệu cho **quyết định cho phép cài đặt/chạy**; đó là một bước kiểm soát chủ động, không phải đặc quyền do định dạng JAR cấp.

## 5. Thực hành Java 17+ với `keytool`, `jar`, `jarsigner`

Bài thực hành chỉ dùng một chương trình không có quyền đặc biệt. Tạo file `Hello.java`:

```java
public final class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, signed JAR!");
    }
}
```

Biên dịch, đóng gói và chạy **trước khi ký** (JDK 17+):

```bash
javac Hello.java
jar --create --file hello.jar --main-class Hello Hello.class
java -jar hello.jar
```

`stdout` đúng của lệnh `java -jar hello.jar`:

```text
Hello, signed JAR!
```

Điều này chứng minh **không cần chữ ký để một JAR thông thường chạy**. Tiếp theo, tạo khóa *thử nghiệm* trong PKCS#12 (các lệnh dưới đây dùng chung cho terminal có JDK; `keytool`/`jarsigner` sẽ hỏi mật khẩu):

```bash
keytool -genkeypair -alias demo -keyalg RSA -keysize 3072 -validity 365 -storetype PKCS12 -keystore demo.p12 -dname "CN=Demo Signer,O=Lab,C=VN"
jarsigner -keystore demo.p12 -signedjar hello-signed.jar hello.jar demo
jar --list --file hello-signed.jar
jarsigner -verify -verbose -certs hello-signed.jar
jarsigner -verify -strict -verbose -certs hello-signed.jar
java -jar hello-signed.jar
```

Quan sát cần rút ra:

- JAR đã ký có các file `META-INF/*.SF` và signature block tương ứng; `-verbose -certs` hiển thị thông tin từng entry/chứng thư.
- Khi dùng khóa **self-signed** ở trên, xác minh mật mã có thể thành công trong khi công cụ vẫn cảnh báo self-signed/không xác thực được chuỗi; `-strict` có thể trả **mã thoát khác 0** vì các cảnh báo nghiêm trọng. Đó không tự động là bằng chứng file bị sửa.
- Lệnh `java -jar hello-signed.jar` vẫn in đúng một dòng `Hello, signed JAR!`. Ký không làm xuất hiện quyền mới hay thay đổi nội dung phương thức `main`.
- Mẫu này **không** phải cấu hình ký bản phát hành: không phân phối `demo.p12`, không đưa mật khẩu lên command line/CI logs; bảo vệ private key và dùng quy trình cấp chứng thư phù hợp khi phát hành thật.

**Thử tính toàn vẹn:** sửa chuỗi in ra trong `Hello.java`, chạy lại `javac Hello.java` rồi cập nhật entry đã ký:

```bash
javac Hello.java
jar --update --file hello-signed.jar Hello.class
jarsigner -verify -strict -verbose -certs hello-signed.jar
```

Entry `Hello.class` mới không còn khớp digest đã ký; kết quả xác minh phải báo lỗi integrity/signature tương ứng. Đừng “sửa” bằng cách xóa chữ ký rồi tuyên bố bản mới được xác minh: khi đó mất bằng chứng ký gốc. Muốn thay đổi nội dung hợp lệ, hãy build lại và **ký lại** theo quy trình phát hành.

## 6. Các tình huống dễ nhầm lẫn

| Tình huống | Kết luận chính xác |
| --- | --- |
| `jarsigner` báo chữ ký hợp lệ nhưng signer self-signed/không nằm trong trust store được chấp nhận | Có thể có bằng chứng nội dung không đổi theo khóa đó; **chưa xác lập độ tin cậy của nhà phát hành** |
| JAR được ký bởi nhà phát hành tin cậy nhưng có lỗi bảo mật hoặc mã độc | Chữ ký không phát hiện tính độc hại; cần review, phân tích phụ thuộc, test và kiểm soát phát hành |
| Một entry đã ký bị thay nội dung | Xác minh toàn vẹn entry đó phải thất bại; không được dùng nó như bản phát hành đã xác minh |
| Có entry chưa ký được bổ sung sau ký | Các entry gốc có thể còn hợp lệ; không được kết luận toàn bộ archive an toàn, cần kiểm tra entry chưa ký/đường nạp mã |
| JAR hoàn toàn chưa ký nhưng được gọi bằng `java -jar` | Vẫn có thể chạy bình thường nếu chương trình, JDK và môi trường cho phép |
| JAR có chứng thư đáng tin nhưng chạy bằng tài khoản không được đọc file nhạy cảm | Không có đặc quyền tự động để vượt quyền OS |
| JAR độc hại được chạy bằng tài khoản dịch vụ có quá nhiều quyền | Chữ ký không ngăn lạm dụng các quyền mà tiến trình đã có |

## 7. Quy trình đánh giá JAR trước khi triển khai

1. **Xác định artefact và nguồn:** lấy JAR từ nguồn phân phối kiểm soát được; chốt phiên bản, checksum từ kênh tin cậy và danh sách phụ thuộc.
2. **Kiểm tra toàn vẹn:** chạy `jarsigner -verify -strict -verbose -certs <file.jar>`; đọc **đầy đủ warning, mã thoát**, entry được ký/chưa ký, chữ ký/thuật toán. Không chỉ tìm chuỗi `jar verified`.
3. **Xác minh signer theo chính sách:** đối chiếu tên nhà phát hành, fingerprint/khóa được pin hoặc chuỗi PKIX tới trust anchor được chấp nhận; kiểm tra hiệu lực, mục đích chứng thư, thu hồi và timestamp khi chính sách yêu cầu.
4. **Đánh giá rủi ro của mã:** rà soát nguồn/artefact, SBOM, dependency versions, lỗ hổng, tải mã động, cấu hình và tác động của hành vi runtime. Chữ ký không thay thế bước này.
5. **Giới hạn quyền chạy:** chỉ cấp quyền cần thiết cho OS user/container/service account; ràng buộc filesystem, network, secrets; ghi log và giám sát.
6. **Xử lý sai lệch:** JAR bị sửa, signer ngoài danh sách chấp nhận, entry thiếu chữ ký bắt buộc hoặc thuật toán/chứng thư không đạt chính sách ⇒ **không triển khai** cho tới khi nhận bản phát hành và bằng chứng phù hợp.

### Tự kiểm tra hiểu bài

- **Câu 1:** “`jar verified`, nên thư viện chắc chắn an toàn?” → **Sai**: kết quả xác minh liên quan chữ ký/integrity; chưa xác nhận người ký đáng tin, độ an toàn của mã và quyền khi chạy.
- **Câu 2:** “Chữ ký self-signed không kiểm tra được thay đổi nội dung?” → **Sai**: có thể kiểm tra toán học bằng public key đi kèm; phần còn thiếu là căn cứ **tin đúng người ký**.
- **Câu 3:** “JAR được ký sẽ có quyền đọc mọi file?” → **Sai**: quyền do môi trường thực thi và chính sách triển khai quyết định.
- **Câu 4:** “Chỉ một số entry có chữ ký thì toàn bộ JAR chắc chắn nguyên vẹn?” → **Sai**: chỉ kết luận trong phạm vi entry được bảo vệ và đã xác minh, sau khi kiểm tra cảnh báo entry chưa ký.

**Ghi nhớ:** `Signature valid` trả lời “nội dung đã ký có bị đổi theo khóa này không?”; `Signer trusted` trả lời “ta có tin khóa/người ký này không?”; `Execution authorized` trả lời “ta có cho chạy và cấp những quyền gì?”. **Cần đánh giá cả ba, không dùng một kết quả thay cho hai kết quả còn lại.**

## Tài liệu chuẩn để đối chiếu

- Oracle, [JAR File Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html) — cấu trúc manifest, `.SF`, signature block và phạm vi xác minh.
- Oracle, [`jarsigner` — JDK 24](https://docs.oracle.com/en/java/javase/24/docs/specs/man/jarsigner.html) — kiểm tra chữ ký, cảnh báo, `-verbose`, `-certs`, `-strict`.
- Oracle, [The Security Manager Is Permanently Disabled — JDK 24](https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html) — thay đổi về mô hình cấp quyền Java kể từ JDK 24.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-005">19.5 Mô hình XMLSignatureFactory, SignedInfo, Reference và KeyInfo</a>

<details>
<summary>Xem chi tiết</summary>

# 19.5. Mô hình `XMLSignatureFactory`, `SignedInfo`, `Reference` và `KeyInfo`

Java XML Digital Signature API (JSR 105, gói `javax.xml.crypto.dsig`) cho phép tạo và kiểm tra chữ ký số theo chuẩn XML Signature (XMLDSig). Bài này tập trung vào **mô hình đối tượng và luồng dữ liệu**: `XMLSignatureFactory` tạo các thành phần, `Reference` xác định nội dung được băm, `SignedInfo` mô tả những gì thực sự được ký, còn `KeyInfo` mang thông tin hỗ trợ tìm khóa kiểm tra chữ ký.

## 1. Vì sao XMLDSig có nhiều đối tượng?

Một chữ ký XML cần trả lời **hai câu hỏi khác nhau**:

1. **Nội dung nào đã được ký và nội dung đó còn nguyên vẹn không?** `Reference` chỉ đến dữ liệu, áp dụng `Transform` theo thứ tự rồi tính `DigestValue`. Khi xác minh, digest phải khớp.
2. **Ai sở hữu khóa đã tạo chữ ký cho danh sách tham chiếu ấy?** `SignedInfo` chứa các tham chiếu và thuật toán; `SignatureValue` là chữ ký mật mã trên biểu diễn canonicalized của `SignedInfo`. Khóa công khai được xác minh theo chính sách tin cậy của ứng dụng; `KeyInfo` có thể đưa gợi ý để tìm khóa.

Sơ đồ cấu trúc logic:

~~~text
XMLSignature
├── SignedInfo                          ← nội dung được ký bằng khóa riêng
│   ├── CanonicalizationMethod          ← chuẩn hóa XML của SignedInfo
│   ├── SignatureMethod                 ← thuật toán ký SignedInfo
│   ├── Reference (1)
│   │   ├── URI                         ← dữ liệu đích
│   │   ├── Transforms                   ← xử lý dữ liệu trước khi băm
│   │   ├── DigestMethod                 ← thuật toán băm
│   │   └── DigestValue                  ← digest của dữ liệu sau transforms
│   └── Reference (2), ...              ← có thể ký nhiều tài nguyên
├── SignatureValue                      ← chữ ký trên SignedInfo đã chuẩn hóa
├── KeyInfo (tùy chọn)                  ← gợi ý khóa/chứng thư, không tự tạo niềm tin
└── Object (tùy chọn)                   ← dữ liệu bổ sung theo chuẩn XMLDSig
~~~

Quan hệ mật mã then chốt:

~~~text
Tài nguyên XML
  → dereference URI → Transforms → DigestMethod → DigestValue
                                             ↓
             Reference nằm trong SignedInfo
                                             ↓
            CanonicalizationMethod trên SignedInfo
                                             ↓
          SignatureMethod + khóa riêng → SignatureValue
~~~

`DigestValue` bảo vệ nội dung được tham chiếu; `SignatureValue` bảo vệ `SignedInfo` (bao gồm danh sách `Reference` và digest tương ứng). Hai kiểm tra đều cần thành công. Chữ ký số phát hiện thay đổi nội dung trong phạm vi đã ký; **không mã hóa dữ liệu và không tự chứng minh danh tính người ký**.

## 2. Vai trò và API của bốn đối tượng

| Thành phần | Vai trò | Điểm cần nhớ |
| --- | --- | --- |
| `XMLSignatureFactory` | Factory cho `XMLSignature`, `SignedInfo`, `Reference`, `DigestMethod`, `SignatureMethod`, `CanonicalizationMethod` và `Transform` | `XMLSignatureFactory.getInstance("DOM")` chọn cơ chế DOM; không phải khóa và không tự ký dữ liệu |
| `Reference` | Mô tả tài nguyên được bảo vệ: `URI`, chuỗi `Transforms`, `DigestMethod`, `DigestValue` | Digest được tính trên **kết quả sau transforms**; `URI` rỗng và `#id` có phạm vi khác nhau |
| `SignedInfo` | Đóng gói `CanonicalizationMethod`, `SignatureMethod` và ít nhất một `Reference` | Chính biểu diễn canonicalized của `SignedInfo` được ký bằng khóa riêng |
| `KeyInfo` | Cung cấp thông tin tùy chọn về khóa: `KeyValue`, `X509Data`, `KeyName`, ... | Có thể bị thay thế/giả mạo; phải đối chiếu khóa hoặc chứng thư với nguồn tin cậy của ứng dụng |

### 2.1. `XMLSignatureFactory`: điểm khởi đầu

~~~java
XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
~~~

`"DOM"` cho biết implementation thao tác trên cây DOM XML. Factory dựng các thành phần bất biến về mặt cấu trúc cho chữ ký; quá trình ký thực tế xảy ra khi gọi `XMLSignature.sign(DOMSignContext)`. Khi xác minh XML có sẵn, factory đọc cấu trúc XML qua `unmarshalXMLSignature(DOMValidateContext)`.

`XMLSignatureFactory` cũng cung cấp `getKeyInfoFactory()`. Không nhầm `XMLSignatureFactory` với `KeyPairGenerator`: factory XMLDSig chỉ lắp mô hình XML Signature; khóa riêng/khóa công khai được tạo hoặc lấy từ hệ thống quản lý khóa khác.

### 2.2. `Reference`: chính xác dữ liệu nào được bảo vệ?

Một `Reference` mô tả bốn điều:

- **`URI`:** `""` thường trỏ đến tài liệu XML hiện tại; `"#order-1"` trỏ đến phần tử mang ID đã được DOM nhận diện; URI ngoài tài liệu có thể liên quan tài nguyên mạng hoặc tệp và cần chính sách kiểm soát.
- **`Transforms`:** chuỗi xử lý có thứ tự trước khi tính digest. Ví dụ `Transform.ENVELOPED` loại chính phần tử chữ ký khỏi dữ liệu khi chữ ký được nhúng bên trong tài liệu đang ký.
- **`DigestMethod`:** thuật toán băm như `DigestMethod.SHA256`.
- **`DigestValue`:** digest được lưu trong XML Signature lúc ký; lúc xác minh, API tính lại và so sánh.

Ví dụ định nghĩa tham chiếu cho **toàn bộ tài liệu XML** theo kiểu *enveloped signature*:

~~~java
Reference ref = factory.newReference(
    "",
    factory.newDigestMethod(DigestMethod.SHA256, null),
    List.of(
        factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
        factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)
    ),
    null,
    null
);
~~~

Đây là đoạn mã trích từ chương trình đầy đủ ở mục 4; các biến `factory` và import cần được khai báo như trong chương trình đó. Việc canonicalize ở cuối chuỗi transform giúp xác định biểu diễn byte XML trước khi băm. Thứ tự transform có ý nghĩa: đổi thứ tự hoặc thuật toán có thể làm thay đổi digest.

Không đánh đồng `URI=""` với `URI="#order-1"`. Nếu ứng dụng chỉ cần xác nhận một phần tử nghiệp vụ, phải xác định đúng ID duy nhất và buộc logic nghiệp vụ sử dụng **chính phần tử đã xác minh**. Xác minh thành công một tham chiếu đến phần tử A không có nghĩa phần tử B mang dữ liệu tương tự cũng được ký.

### 2.3. `SignedInfo`: hợp đồng được ký

~~~java
SignedInfo signedInfo = factory.newSignedInfo(
    factory.newCanonicalizationMethod(
        CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null
    ),
    factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
    List.of(ref)
);
~~~

`CanonicalizationMethod` dùng để chuẩn hóa **chính `SignedInfo`** trước khi ký/kiểm chữ ký. `SignatureMethod.RSA_SHA256` dùng RSA với SHA-256 để ký `SignedInfo`; phương pháp này **khác** `DigestMethod.SHA256` dùng cho từng `Reference`. Có thể có nhiều `Reference` cùng được bảo vệ bởi một `SignatureValue`.

Chuẩn hóa XML (*canonicalization*, thường gọi C14N) xử lý những khác biệt cú pháp XML nhất định như thứ tự thuộc tính, nhưng **không cho phép sửa tùy ý nội dung hay chèn/xóa nút văn bản**. Với XML có namespace, chọn inclusive/exclusive canonicalization theo quy ước trao đổi; phía ký và phía xác minh phải tôn trọng đúng thuật toán được khai báo.

### 2.4. `KeyInfo`: thông tin khóa, không phải quyết định tin cậy

~~~java
KeyInfoFactory kif = factory.getKeyInfoFactory();
KeyValue keyValue = kif.newKeyValue(publicKey);
KeyInfo keyInfo = kif.newKeyInfo(List.of(keyValue));
~~~

Đây là đoạn mã trích: `publicKey` là khóa công khai phù hợp với khóa riêng dùng để ký. `KeyValue` nhúng khóa công khai; `X509Data` có thể chứa chứng thư X.509; `KeyName` chỉ là tên gợi ý.

**Cảnh báo an ninh:** nếu ứng dụng chấp nhận khóa từ `KeyInfo` một cách vô điều kiện thì kẻ tấn công có thể tự tạo cặp khóa, tự ký tài liệu của mình và chèn khóa công khai vào `KeyInfo`. Phép kiểm chữ ký có thể đúng về mật mã nhưng không chứng minh nguồn gửi được ủy quyền. Ứng dụng phải dùng khóa tin cậy đã ghim (*pinned key*), kho khóa được quản trị, hoặc chứng thư có chuỗi tin cậy và chính sách xác thực thích hợp. Bản thân `KeyInfo` thường không nằm trong phần được ký bởi `SignedInfo` trừ khi được tham chiếu/ký rõ ràng.

## 3. Luồng ký và luồng kiểm chứng

**Ký (signing)**

1. Chuẩn bị cây DOM, khóa riêng và đối tượng `DOMSignContext` chỉ ra vị trí chèn phần tử `Signature`.
2. Dùng `XMLSignatureFactory` tạo `DigestMethod`, `Transform` và từng `Reference`.
3. Tạo `SignedInfo` với canonicalization, signature method và danh sách tham chiếu.
4. Tùy chọn tạo `KeyInfo` để bên nhận có dữ liệu hỗ trợ tra khóa.
5. Tạo `XMLSignature` từ `SignedInfo`, `KeyInfo`; gọi `sign(context)`. Implementation dereference, biến đổi và băm từng reference, điền `DigestValue`, chuẩn hóa `SignedInfo` rồi tính `SignatureValue` bằng khóa riêng.
6. Lưu/truyền XML đã ký **mà không làm thay đổi nội dung nằm trong phạm vi ký**.

**Xác minh (validation)**

1. Parse XML an toàn, tìm đúng phần tử chữ ký mà ứng dụng dự định kiểm tra.
2. Xác định khóa công khai **tin cậy** từ bên ngoài hoặc bằng chính sách kiểm tra chứng thư; tạo `DOMValidateContext`.
3. Bật các ràng buộc an toàn của XMLDSig và giới hạn URI/transform theo chính sách.
4. `unmarshalXMLSignature(context)` để dựng mô hình từ DOM.
5. `XMLSignature.validate(context)` xác minh chữ ký trên `SignedInfo` **và** digest của các `Reference`. Trả về `true` chỉ khi tất cả kiểm tra cốt lõi hợp lệ.
6. Kiểm tra rằng phần tử đã được ký chính là phần tử mà nghiệp vụ sẽ xử lý, sau đó mới chấp nhận nội dung.

`validate() == true` có nghĩa các kiểm tra lõi XMLDSig thành công với khóa đã cung cấp, **không tự đồng nghĩa** với “tài liệu đến từ đối tác X”, “mọi phần tử trong tài liệu đã được ký” hay “nội dung hợp lệ theo quy tắc nghiệp vụ”.

## 4. Ví dụ Java 17+ chạy độc lập: ký và phát hiện sửa đổi XML

Lưu mã sau vào **`XmlSignatureModelDemo.java`**. Chương trình tạo một DOM đơn giản, ký toàn bộ tài liệu với khóa RSA 2048-bit được sinh cho lần chạy đó, xác minh bằng chính khóa công khai được cung cấp riêng cho trình kiểm tra, rồi sửa số tiền để chứng minh digest không còn khớp.

~~~java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XmlSignatureModelDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
        documentFactory.setNamespaceAware(true);
        Document document = documentFactory.newDocumentBuilder().newDocument();

        Element payment = document.createElement("Payment");
        document.appendChild(payment);
        Element amount = document.createElement("Amount");
        amount.setTextContent("100");
        payment.appendChild(amount);

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");

        Reference reference = factory.newReference(
            "",
            factory.newDigestMethod(DigestMethod.SHA256, null),
            List.of(
                factory.newTransform(
                    Transform.ENVELOPED, (TransformParameterSpec) null
                ),
                factory.newTransform(
                    CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null
                )
            ),
            null,
            null
        );

        SignedInfo signedInfo = factory.newSignedInfo(
            factory.newCanonicalizationMethod(
                CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null
            ),
            factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
            List.of(reference)
        );

        KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
        KeyValue keyValue = keyInfoFactory.newKeyValue(pair.getPublic());
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(keyValue));

        XMLSignature xmlSignature = factory.newXMLSignature(signedInfo, keyInfo);
        DOMSignContext signContext =
            new DOMSignContext(pair.getPrivate(), payment);
        signContext.setDefaultNamespacePrefix("ds");
        xmlSignature.sign(signContext);

        System.out.println("Truoc khi sua: " +
            verify(document, pair.getPublic()));

        amount.setTextContent("999");

        System.out.println("Sau khi sua: " +
            verify(document, pair.getPublic()));
    }

    private static boolean verify(Document document, PublicKey trustedKey)
            throws Exception {
        Node signatureNode = document.getElementsByTagNameNS(
            XMLSignature.XMLNS, "Signature"
        ).item(0);
        if (signatureNode == null) {
            return false;
        }

        DOMValidateContext validateContext = new DOMValidateContext(
            KeySelector.singletonKeySelector(trustedKey), signatureNode
        );
        validateContext.setProperty(
            "org.jcp.xml.dsig.secureValidation", Boolean.TRUE
        );

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        XMLSignature received = factory.unmarshalXMLSignature(validateContext);
        return received.validate(validateContext);
    }
}
~~~

Chạy với JDK 17 trở lên (API XML Crypto có sẵn trong module `java.xml.crypto`):

~~~bash
javac XmlSignatureModelDemo.java
java XmlSignatureModelDemo
~~~

Kết quả chuẩn:

~~~text
Truoc khi sua: true
Sau khi sua: false
~~~

Giải thích từng điểm quan trọng:

- `URI=""` chọn tài liệu XML hiện tại. Chữ ký được chèn **bên trong** `Payment`, vì thế cần `Transform.ENVELOPED` để không đưa chính `Signature` vào digest của tài liệu đang ký.
- Transform canonicalization chuyển dữ liệu tham chiếu sang dạng byte ổn định trước khi băm SHA-256. `SignedInfo` cũng được canonicalize riêng trước khi ký RSA-SHA256.
- `KeyInfo` chứa `KeyValue` giúp diễn đạt cấu trúc XMLDSig, nhưng hàm `verify` **cố ý dùng `trustedKey` được truyền từ bên ngoài**, không chấp nhận khóa do XML tự khai. Đây là khác biệt giữa kiểm đúng chữ ký và kiểm nguồn tin cậy.
- Sau khi sửa `Amount` từ `100` thành `999`, `DigestValue` lưu trong `SignedInfo` không đổi nhưng digest tính lại trên dữ liệu mới đã khác; `validate` trả `false`.
- Ví dụ dùng khóa sinh ngẫu nhiên trong bộ nhớ và không lưu XML ra tệp, thích hợp để học mô hình. Trong hệ thống thực tế, dùng khóa riêng được bảo vệ, cơ chế phân phối khóa công khai tin cậy và quy trình ký/kiểm qua biên hệ thống.

## 5. Khi cần `Reference URI="#id"` thay vì toàn bộ tài liệu

Trong nghiệp vụ, một XML có thể chứa nhiều vùng dữ liệu, trong khi chữ ký chỉ nên bảo vệ một phần tử cụ thể. Giả sử phần tử cần ký có thuộc tính `Id="order-1"`. Cả lúc ký lẫn lúc kiểm tra phải cho DOM biết thuộc tính ấy là ID; `setAttribute` đơn thuần không đảm bảo cơ chế dereference `#order-1` tìm đúng phần tử.

Đoạn minh họa **để chèn vào luồng dựng context tương ứng**, không phải chương trình độc lập:

~~~java
Element order = document.getDocumentElement();
order.setAttribute("Id", "order-1");

DOMSignContext signContext =
    new DOMSignContext(privateKey, order);
signContext.setIdAttributeNS(order, null, "Id");

// Dùng Reference URI="#order-1" thay vì URI="" khi dựng SignedInfo.
Reference ref = factory.newReference(
    "#order-1",
    factory.newDigestMethod(DigestMethod.SHA256, null),
    List.of(factory.newTransform(
        Transform.ENVELOPED, (TransformParameterSpec) null
    )),
    null,
    null
);

// Khi xác minh, sau khi tạo DOMValidateContext:
validateContext.setIdAttributeNS(order, null, "Id");
~~~

Biến `document`, `privateKey`, `factory` và `validateContext` thuộc luồng bao quanh. Khi ký fragment, cần chọn transform/canonicalization và vị trí chữ ký phù hợp với cấu trúc tài liệu. Tùy hệ thống, DOM parser đã biết ID từ schema/DTD, nhưng việc gắn ID rõ ràng vẫn giúp chính sách và code dễ kiểm soát.

**Không chỉ tin vào URI hợp lệ.** XML có nhiều nút trông giống `Order`, hoặc nhiều thuộc tính ID trùng giá trị, có thể tạo điều kiện cho *XML Signature Wrapping*: chữ ký hợp lệ đối với một nút nhưng ứng dụng lại xử lý nút khác. Hãy từ chối ID trùng, xác minh cấu trúc tài liệu và liên kết phần tử được reference với đối tượng nghiệp vụ thực sự được dùng.

## 6. Phân tích lỗi khi `validate()` trả `false`

Có hai nhóm nguyên nhân quan trọng:

1. **Lỗi chữ ký `SignedInfo`:** dữ liệu `SignedInfo`, `SignatureValue`, thuật toán hoặc khóa kiểm tra không khớp.
2. **Lỗi `Reference`:** một URI không dereference được, transform gặp lỗi, nội dung XML bị sửa, hoặc digest tính lại không khớp.

Có thể xem riêng kết quả từng thành phần **sau bước unmarshal**, khi đã có `received` và `validateContext`. Đây là đoạn chèn để chẩn đoán, không phải chương trình độc lập:

~~~java
boolean signatureValueValid =
    received.getSignatureValue().validate(validateContext);
System.out.println("SignatureValue: " + signatureValueValid);

for (Object item : received.getSignedInfo().getReferences()) {
    Reference ref = (Reference) item;
    System.out.println("Reference " + ref.getURI() + ": " +
        ref.validate(validateContext));
}
~~~

Không lấy ví dụ này làm quyết định bảo mật thay thế `received.validate(validateContext)`. Luồng xử lý thật phải từ chối tài liệu nếu một trong các kiểm tra lõi thất bại; khi `Reference` thất bại, không chấp nhận chữ ký chỉ vì `SignatureValue` còn đúng. Khi xuất log, tránh in dữ liệu bí mật hoặc toàn bộ XML không tin cậy.

## 7. Lưu ý bảo mật và khả năng tương tác

### Khóa và chứng thư

- Chỉ ký bằng khóa riêng được phép dùng và được bảo vệ, chẳng hạn từ keystore/HSM phù hợp. Kiểm chứng bằng khóa công khai đã được tin cậy hoặc chứng thư có xác minh chuỗi CA, thời hạn, mục đích sử dụng và chính sách ứng dụng.
- `KeyInfo` là dữ liệu hỗ trợ tra khóa, **không phải** nguồn tin cậy tự thân. `X509Data` chứa một certificate chưa đủ để kết luận certificate ấy đáng tin.
- Đối với hệ thống mới, ưu tiên thuật toán còn an toàn và được hỗ trợ như SHA-256 với RSA/ECDSA theo chính sách; không thiết kế mới quanh SHA-1/MD5 hoặc các khóa ngắn. Khả dụng thuật toán có thể phụ thuộc JDK/provider.

### XML, URI và transform

- Với XML nhận từ bên ngoài, cấu hình parser an toàn để chống XXE: cấm DTD/external entities khi không cần và chặn truy cập tài nguyên bên ngoài. Ví dụ ở mục 4 dùng `newDocument()` nên không parse XML không tin cậy.
- Hạn chế `Reference` đến tài nguyên dự kiến; không tự động cho phép dereference HTTP, file URI hoặc những transform phức tạp từ tài liệu do đối tác cung cấp. Giới hạn kích thước, số lượng tham chiếu và thời gian xử lý để tránh tiêu tốn tài nguyên.
- Bật secure validation trên `DOMValidateContext`, ví dụ property `org.jcp.xml.dsig.secureValidation = Boolean.TRUE` như mã ở trên. Đây là một lớp bảo vệ; không thay cho kiểm soát URI, ID và trust policy.
- Không pretty-print, thay namespace, sửa khoảng trắng có ý nghĩa hoặc biến đổi DOM sau khi ký rồi mặc định chữ ký vẫn hợp lệ. Canonicalization chuẩn hóa một tập khác biệt cú pháp nhất định, không làm hai tài liệu XML bất kỳ thành giống nhau.
- Nếu ký nhiều `Reference`, kiểm tra **tất cả** và chỉ xử lý các thành phần được yêu cầu theo mô hình nghiệp vụ. Cần xác minh đường dẫn/nơi xuất hiện của phần tử, ID duy nhất và quan hệ giữa phần tử đã ký với dữ liệu dùng thực tế.

### Phân biệt với ký JAR

Chủ đề cha bao gồm **ký JAR và XML**, nhưng đây là mô hình của **XMLDSig**. Ký JAR thông thường dùng `jarsigner` và metadata chữ ký trong `META-INF`; không dùng `XMLSignatureFactory` để thay thế quy trình ký JAR. Điểm chung là khóa, chữ ký số, digest và kiểm tin cậy; định dạng và phạm vi bảo vệ khác nhau.

## 8. Lỗi phổ biến và cách tự kiểm tra hiểu bài

| Hiểu sai/lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Coi `KeyInfo` như xác thực danh tính | XML tự khai khóa và tự ký vẫn có thể được chấp nhận | Xác thực khóa/chứng thư theo trust policy độc lập |
| Coi `SignatureValue` đúng là toàn bộ XML đúng | Dữ liệu ngoài các `Reference` có thể không được bảo vệ | Kiểm lõi XMLDSig và xác nhận đúng phần tử nghiệp vụ |
| Không dùng enveloped transform khi chèn chữ ký trong nội dung ký | Digest có thể sai do chứa chính nút `Signature` | Chọn transform phù hợp cấu trúc chữ ký |
| Nhầm `DigestMethod` với `SignatureMethod` | Chọn sai thuật toán hay hiểu sai quy trình | Digest từng `Reference`; ký canonicalized `SignedInfo` |
| Dùng `#id` mà không khai báo ID hoặc cho phép ID trùng | Không tìm được nút hoặc đối chiếu sai nút | Đăng ký ID, kiểm duy nhất và liên kết với cấu trúc mong đợi |
| Parser/dereferencer mở tài nguyên ngoài không kiểm soát | XXE, SSRF, rò rỉ dữ liệu hoặc DoS | Khóa external access, giới hạn URI và kích thước |
| Sửa XML sau khi ký | Digest hoặc chữ ký bị sai | Hoàn tất biến đổi dữ liệu trước khi ký; kiểm trên bản cuối |

**Câu hỏi tự đánh giá:**

1. Nếu `Reference URI=""` dùng chữ ký enveloped, tại sao cần `Transform.ENVELOPED`? — Để loại bỏ chính phần tử `Signature` khỏi dữ liệu được băm.
2. Thay `Amount` nhưng không thay `SignedInfo` có thể làm `SignatureValue` còn hợp lệ không? — Có thể chữ ký mật mã của `SignedInfo` vẫn hợp lệ, nhưng **digest của `Reference` sẽ sai**, nên kiểm tra lõi phải thất bại.
3. Vì sao không thể tin `KeyValue` trong `KeyInfo` ngay khi chữ ký verify thành công? — Vì ai cũng có thể tạo cặp khóa và chữ ký của riêng mình; phải gắn khóa đó với danh tính/ủy quyền bằng chính sách tin cậy.
4. Ký `#order-1` có đồng nghĩa bảo vệ mọi nút `Order` trong XML không? — Không; chỉ dữ liệu được dereference và biến đổi bởi `Reference` đó mới được bảo vệ. Ứng dụng phải sử dụng đúng nút đã xác minh.

**Ghi nhớ:** `XMLSignatureFactory` dựng mô hình → `Reference` xác định dữ liệu và digest → `SignedInfo` chứa hợp đồng được ký → `SignatureValue` chứng minh chữ ký trên hợp đồng → `KeyInfo` chỉ hỗ trợ tìm khóa → ứng dụng tự xác lập niềm tin và bảo đảm xử lý đúng dữ liệu đã ký.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-006">19.6 Canonicalization, transform và chữ ký detached, enveloped hoặc enveloping</a>

<details>
<summary>Xem chi tiết</summary>

# 19.6. Canonicalization, transform và chữ ký XML detached, enveloped, enveloping

Trong XML Digital Signature (XMLDSig), chữ ký số xác nhận tính toàn vẹn của **dữ liệu được tham chiếu** và chứng minh dữ liệu đó được ký bởi khóa riêng tương ứng với khóa công khai dùng để xác minh. Điểm khó của XMLDSig là một tài liệu XML có nhiều cách biểu diễn văn bản khác nhau: thứ tự thuộc tính, khai báo namespace, dấu nháy, ký pháp thẻ rỗng… Vì vậy, quy trình ký phải nêu rõ **chuẩn hóa XML (canonicalization)**, **các phép biến đổi (transforms)** và **đối tượng nào thật sự được ký**. Bài này tập trung vào ba nội dung ấy và ba cách bố trí chữ ký trong XML; chữ ký JAR là cơ chế riêng.

## 1. Cấu trúc của XML Signature và luồng tính toán

Một chữ ký XML thường chứa cấu trúc sau (mã XML dưới đây chỉ minh họa thành phần, không phải chữ ký có thể xác minh):

~~~xml
<ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
  <ds:SignedInfo>
    <ds:CanonicalizationMethod Algorithm="URI-canonicalization"/>
    <ds:SignatureMethod Algorithm="URI-signature-algorithm"/>
    <ds:Reference URI="#data-1">
      <ds:Transforms>
        <ds:Transform Algorithm="URI-transform"/>
      </ds:Transforms>
      <ds:DigestMethod Algorithm="URI-digest-algorithm"/>
      <ds:DigestValue>...</ds:DigestValue>
    </ds:Reference>
  </ds:SignedInfo>
  <ds:SignatureValue>...</ds:SignatureValue>
  <ds:KeyInfo><!-- thông tin khóa/chứng thư tùy chọn --></ds:KeyInfo>
  <ds:Object Id="object-1"><!-- dữ liệu tùy chọn --></ds:Object>
</ds:Signature>
~~~

- `Reference` định vị một đối tượng dữ liệu qua `URI`, có thể kèm chuỗi `Transforms`. `DigestMethod` (ví dụ SHA-256) băm **kết quả sau các biến đổi** thành `DigestValue`.
- `SignedInfo` tập hợp phương thức canonicalization, thuật toán ký và **một hoặc nhiều** `Reference`. `SignatureValue` là chữ ký mật mã trên dạng canonical của **SignedInfo**, không đơn giản là chữ ký trực tiếp trên toàn bộ tệp XML.
- `KeyInfo` có thể mang khóa công khai, chứng thư X.509 hoặc chỉ dẫn tìm khóa. Sự hiện diện của `KeyInfo` **không tự chứng minh khóa đáng tin**: ứng dụng còn phải xác thực nguồn khóa, chuỗi chứng thư, thời hạn, mục đích sử dụng, trạng thái thu hồi… theo chính sách.
- `Object` có thể chứa dữ liệu nằm trong chính phần tử `Signature`, thường gặp ở kiểu `enveloping`. Không bắt buộc phải có `Object` trong mọi chữ ký.

**Khi ký:** với mỗi `Reference`, lấy dữ liệu theo URI → chạy từng transform **đúng thứ tự** → chuyển thành chuỗi byte theo quy tắc áp dụng → tính digest → lưu `DigestValue`; tiếp đó canonicalize `SignedInfo` và ký bằng khóa riêng để tạo `SignatureValue`.

**Khi xác minh:** kiểm tra chữ ký của `SignedInfo` bằng khóa công khai **và** tính lại digest của từng `Reference` qua đúng chuỗi transform rồi so với `DigestValue`. Core validation chỉ thành công khi cả hai phần đạt; ứng dụng vẫn cần kiểm tra dữ liệu nào đã được ký và danh tính người ký có được tin cậy hay không.

~~~text
Reference.URI -> dữ liệu nguồn -> Transform 1 -> Transform 2 -> SHA-256 -> DigestValue
                                                                    |
SignedInfo gồm các Reference/DigestValue -> C14N SignedInfo -> RSA-SHA256 -> SignatureValue
~~~

## 2. Canonicalization (C14N) giải quyết điều gì?

Canonicalization chuyển một tập nút XML thành biểu diễn byte xác định để hai bên tính cùng một digest/chữ ký dù một số chi tiết cú pháp đầu vào khác nhau. Ví dụ, hai phần tử dưới đây tương đương về thứ tự thuộc tính:

~~~xml
<Item code="A" qty="2"></Item>
<Item qty='2' code='A'/>
~~~

Sau canonicalization thích hợp, thứ tự thuộc tính, dấu nháy thuộc tính, cách viết phần tử rỗng và một số chi tiết namespace được đưa về quy tắc chung. C14N thông thường bỏ XML declaration, dùng quy tắc mã hóa UTF-8 và xử lý xuống dòng theo quy định của XML/C14N.

**Nhưng C14N không có nghĩa là hai XML “cùng ý nghĩa nghiệp vụ” luôn ra cùng byte.** Nó không tự xóa mọi khoảng trắng: khoảng trắng tạo text node giữa các thẻ hoặc trong nội dung có thể ảnh hưởng digest; không tự sắp xếp lại phần tử con; không đổi `100.0` thành `100`; không chuẩn hóa Unicode NFC; không tự sửa namespace sai; không diễn giải mặc định kiểu dữ liệu theo XML Schema. Một trình formatter chèn dòng mới/indent vào phần dữ liệu đã ký vẫn có thể làm chữ ký hỏng.

Các họ thuật toán thường gặp:

| Phương pháp | Đặc điểm và khi chọn |
| --- | --- |
| Canonical XML 1.0 / 1.1 (inclusive) | Có thể mang theo ngữ cảnh namespace từ tổ tiên; C14N 1.1 có các xử lý cải tiến cho ngữ cảnh XML, hữu ích khi cần canonicalization inclusive theo profile liên thông. |
| Exclusive XML Canonicalization 1.0 | Hạn chế kéo namespace từ ancestor không thật sự cần thiết cho tập nút; thường thuận lợi khi ký fragment được chuyển giữa các XML envelope. |
| Bản “WithComments” | Giữ comment trong đầu ra; bản không có hậu tố này thường bỏ comment. Chọn nhất quán với yêu cầu ký và đối tác. |

Hai vị trí dễ nhầm:

1. `SignedInfo/CanonicalizationMethod` quy định canonicalization **cho chính SignedInfo** trước bước ký/kiểm tra `SignatureValue`.
2. `Reference/Transforms/Transform` có thể chỉ rõ một phép C14N **cho dữ liệu được tham chiếu** trước khi băm. Nếu nguồn là XML node-set và cần tính digest ổn định, nên khai báo transform C14N rõ ràng thay vì dựa vào chuyển đổi ngầm của engine.

Chúng độc lập: đặt Exclusive C14N trong `SignedInfo` **không tự động** áp Exclusive C14N cho mọi `Reference`. Thuật toán đều được xác định bằng URI chuẩn trong XMLDSig, và bên xác minh phải hỗ trợ cùng thuật toán/biến thể (có hay không có comment).

## 3. Transform: chọn chính xác cái gì được băm

Một `Reference` có dạng tư duy:

~~~text
Resolve URI -> đầu vào (octet stream hoặc XML node-set)
            -> Transform A -> Transform B -> ...
            -> octet stream -> DigestMethod -> DigestValue
~~~

Ví dụ các transform:

- **Enveloped-signature** (`http://www.w3.org/2000/09/xmldsig#enveloped-signature`): loại chính phần tử `Signature` đang chứa transform ra khỏi tập nút được băm. Nếu tài liệu đã ký chứa luôn chữ ký này, thiếu transform sẽ dẫn đến tự tham chiếu/vòng phụ thuộc.
- **Canonicalization transform** (ví dụ Exclusive C14N): biến node-set XML thành byte theo quy tắc cụ thể. Khi kết hợp với enveloped transform, thông thường **loại Signature trước, canonicalize sau**.
- **Base64**: giải mã dữ liệu base64 trước khi băm, nếu profile thực sự yêu cầu ký các byte đã giải mã.
- **XPath/XPath Filter**: chọn một phần tập nút; cấu hình phức tạp dễ ký thiếu trường dữ liệu quan trọng. XSLT và các transform tùy biến cần đánh giá bảo mật, mức hỗ trợ và chi phí thực thi; secure-validation trên JDK mặc định hạn chế nhiều loại nguy hiểm.

Ý nghĩa một số URI:

| `Reference URI` | Đối tượng điển hình |
| --- | --- |
| `""` | Cùng tài liệu XML hiện tại (thường là toàn bộ tài liệu trước các transform), không có nghĩa là chuỗi rỗng cần ký. |
| `"#data-1"` | Phần tử/nút cùng tài liệu được định danh bằng ID `data-1`. Phải đăng ký đúng kiểu ID trong DOM/JSR 105, và ID phải duy nhất. |
| `"#object-1"` | Ví dụ trỏ vào `ds:Object Id="object-1"` chứa dữ liệu ở kiểu enveloping. |
| URI ngoài tài liệu | Tài nguyên bên ngoài, có thể là byte stream; phải định nghĩa rõ bộ giải tham chiếu, base URI, MIME/profile và chính sách truy cập. Không giả định URL nào cũng nên được tải qua mạng. |

**Thứ tự transform là một phần của ý nghĩa chữ ký.** `enveloped → C14N` không thể tùy tiện đổi thành `C14N → enveloped` vì transform sau cần đầu vào là node-set. Chuỗi XPath “lọc mất” trường tiền hoặc người nhận có thể khiến chữ ký vẫn hợp lệ đối với phần còn lại nhưng không bảo vệ giao dịch mong muốn.

## 4. Detached, enveloped và enveloping khác nhau thế nào?

Phân loại dựa trên **vị trí phần tử Signature so với dữ liệu được ký**, không dựa vào thuật toán RSA/ECDSA hay vị trí lưu tệp.

### 4.1. Enveloped: chữ ký nằm bên trong tài liệu được ký

~~~xml
<Invoice Id="inv-1">
  <Amount>100</Amount>
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    <!-- SignedInfo chứa Reference URI="" và enveloped-signature transform -->
  </ds:Signature>
</Invoice>
~~~

`Signature` là phần tử con của `Invoice`. `Reference URI=""` trỏ tới tài liệu; transform enveloped loại **chính Signature đang ký** rồi mới canonicalize và băm nội dung còn lại. Phù hợp khi XML cần tự mang chữ ký, ví dụ chứng từ XML. Nếu ký trọn tài liệu, thay đổi dữ liệu có trong phần đã ký làm digest thất bại. Nếu chỉ ký một phần tử có ID, phải chỉ rõ scope thay vì mặc nhiên giả định toàn tài liệu được bảo vệ.

### 4.2. Detached: chữ ký nằm ngoài đối tượng được ký

~~~xml
<Envelope>
  <Payload Id="data-1">Hello</Payload>
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    <!-- SignedInfo chứa Reference URI="#data-1" -->
  </ds:Signature>
</Envelope>
~~~

Ở đây `Payload` và `Signature` là hai phần tử **cùng cấp**: chữ ký không nằm bên trong Payload, Payload không nằm bên trong Signature. Đây vẫn là *detached* dù hai phần tử ở **cùng một tệp XML**. Detached cũng có thể là tệp chữ ký tách khỏi tệp dữ liệu khác, tham chiếu bằng URI ngoài; trường hợp ngoài tệp phải kiểm soát chặt URI dereference và liên kết tệp. Vì `Signature` không nằm trong `Payload` được băm nên thường **không cần** enveloped transform. Không tự hiểu rằng toàn bộ `Envelope` đã được ký chỉ vì chữ ký nằm ở đó.

### 4.3. Enveloping: dữ liệu được ký nằm trong Signature

~~~xml
<ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
  <ds:SignedInfo>
    <!-- Reference URI="#object-1" -->
  </ds:SignedInfo>
  <ds:SignatureValue><!-- chữ ký trên SignedInfo --></ds:SignatureValue>
  <ds:Object Id="object-1">
    <Payload>Hello</Payload>
  </ds:Object>
</ds:Signature>
~~~

`Signature` bao bọc `ds:Object`, trong đó chứa dữ liệu. `Reference URI="#object-1"` thường băm **đối tượng Object được trỏ tới** sau transform, không tự động “chỉ băm text Hello”. Cách này tiện để đóng gói payload cùng chữ ký, nhưng ứng dụng vẫn phải biết lấy đúng Object nào và giải thích nội dung của nó.

| Tiêu chí | Enveloped | Detached | Enveloping |
| --- | --- | --- | --- |
| Quan hệ vị trí | Signature là con của dữ liệu được ký | Signature ở ngoài dữ liệu được ký | Dữ liệu nằm trong Object của Signature |
| Reference điển hình | `URI=""` hoặc `#id` của phần tử bao ngoài | `#id` của payload hoặc URI ngoài | `#id` của `ds:Object` |
| Transform hay gặp | Enveloped-signature, rồi C14N | C14N nếu ký XML node-set | C14N nếu ký Object XML |
| Rủi ro nhầm | Quên bỏ chính Signature khi digest | Tin rằng cả envelope đều được ký | Chọn sai Object hoặc nội dung nghiệp vụ |

Cả ba đều dùng được cùng digest/ký mạnh. **Kiểu bố trí không quyết định độ tin cậy**; quyết định là phạm vi dữ liệu được bảo vệ, thuật toán, key trust và cách ứng dụng sử dụng dữ liệu xác minh.

## 5. Java 17+: tạo và kiểm tra cả ba kiểu bằng JSR 105

JDK có API `javax.xml.crypto.dsig` (module `java.xml.crypto`), thường dùng cơ chế DOM qua `XMLSignatureFactory.getInstance("DOM")`. Chương trình sau khởi tạo dữ liệu **trực tiếp trong bộ nhớ**, tạo một cặp RSA 2048-bit để minh họa, ký ba tài liệu độc lập, xác minh cả ba và sửa dữ liệu đã ký để chứng minh phát hiện tampering. Nó không cần thư viện ngoài, file, mạng, keystore hoặc `KeyInfo`: khóa công khai được truyền **từ bên ngoài** vào bước verify.

Lưu nguyên mã dưới đây thành `XmlDsigDemo.java`:

~~~java
import java.security.*;
import java.util.List;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class XmlDsigDemo {
    private static final XMLSignatureFactory FACTORY =
            XMLSignatureFactory.getInstance("DOM");

    private static Document document() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().newDocument();
    }

    private static Transform exclusiveC14n() throws Exception {
        return FACTORY.newTransform(CanonicalizationMethod.EXCLUSIVE,
                (TransformParameterSpec) null);
    }

    private static SignedInfo signedInfo(String uri, List<Transform> transforms)
            throws Exception {
        Reference ref = FACTORY.newReference(
                uri,
                FACTORY.newDigestMethod(DigestMethod.SHA256, null),
                transforms, null, null);
        return FACTORY.newSignedInfo(
                FACTORY.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                        (C14NMethodParameterSpec) null),
                FACTORY.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                List.of(ref));
    }

    private static Document enveloped(PrivateKey key) throws Exception {
        Document doc = document();
        Element invoice = doc.createElement("Invoice");
        Element amount = doc.createElement("Amount");
        amount.setTextContent("100");
        invoice.appendChild(amount);
        doc.appendChild(invoice);

        Transform removeSelf = FACTORY.newTransform(
                Transform.ENVELOPED, (TransformParameterSpec) null);
        XMLSignature signature = FACTORY.newXMLSignature(
                signedInfo("", List.of(removeSelf, exclusiveC14n())), null);
        signature.sign(new DOMSignContext(key, invoice));
        return doc;
    }

    private static Document detached(PrivateKey key) throws Exception {
        Document doc = document();
        Element envelope = doc.createElement("Envelope");
        doc.appendChild(envelope);
        Element payload = doc.createElement("Payload");
        payload.setAttribute("Id", "data-1");
        payload.setTextContent("Hello");
        envelope.appendChild(payload);

        DOMSignContext ctx = new DOMSignContext(key, envelope);
        ctx.setIdAttributeNS(payload, null, "Id");
        FACTORY.newXMLSignature(
                signedInfo("#data-1", List.of(exclusiveC14n())), null)
                .sign(ctx);
        return doc;
    }

    private static Document enveloping(PrivateKey key) throws Exception {
        Document doc = document();
        Element payload = doc.createElement("Payload");
        payload.setTextContent("Hello");
        XMLObject object = FACTORY.newXMLObject(
                List.of(new DOMStructure(payload)), "object-1", null, null);
        XMLSignature signature = FACTORY.newXMLSignature(
                signedInfo("#object-1", List.of(exclusiveC14n())),
                null, List.of(object), null, null);
        signature.sign(new DOMSignContext(key, doc));
        return doc;
    }

    private static boolean verify(Document doc, PublicKey key) throws Exception {
        Node signatureNode = doc.getElementsByTagNameNS(
                XMLSignature.XMLNS, "Signature").item(0);
        if (signatureNode == null) {
            throw new IllegalArgumentException("Missing Signature");
        }

        DOMValidateContext ctx = new DOMValidateContext(key, signatureNode);
        NodeList payloads = doc.getElementsByTagName("Payload");
        for (int i = 0; i < payloads.getLength(); i++) {
            Element payload = (Element) payloads.item(i);
            if (payload.hasAttribute("Id")) {
                ctx.setIdAttributeNS(payload, null, "Id");
            }
        }
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        XMLSignature signature = FACTORY.unmarshalXMLSignature(ctx);
        return signature.validate(ctx);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keys = gen.generateKeyPair();

        Document a = enveloped(keys.getPrivate());
        Document b = detached(keys.getPrivate());
        Document c = enveloping(keys.getPrivate());

        System.out.println("enveloped: " + verify(a, keys.getPublic()));
        System.out.println("detached: " + verify(b, keys.getPublic()));
        System.out.println("enveloping: " + verify(c, keys.getPublic()));

        a.getElementsByTagName("Amount").item(0).setTextContent("900");
        System.out.println("enveloped after tamper: "
                + verify(a, keys.getPublic()));
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac XmlDsigDemo.java
java XmlDsigDemo
~~~

Kết quả dự kiến (đã đối chiếu bằng chạy thử; cặp khóa và byte chữ ký thay đổi giữa các lần chạy):

~~~text
enveloped: true
detached: true
enveloping: true
enveloped after tamper: false
~~~

Giải thích điểm chính:

- `signedInfo()` chọn `DigestMethod.SHA256` cho các Reference, `SignatureMethod.RSA_SHA256` cho chữ ký và `CanonicalizationMethod.EXCLUSIVE` cho `SignedInfo`. `exclusiveC14n()` tạo một **transform riêng** cho payload; không có chuyện một lựa chọn tự áp vào cả hai vị trí.
- `enveloped()` ký tài liệu bằng `Reference URI=""`, transform `Transform.ENVELOPED` rồi Exclusive C14N; `DOMSignContext(key, invoice)` đặt Signature bên trong Invoice.
- `detached()` đặt `Payload` và Signature cạnh nhau; `Reference URI="#data-1"` trỏ vào Payload. `setIdAttributeNS(payload, null, "Id")` cho engine biết `Id` là thuộc tính định danh, không chỉ là chuỗi ký tự tên “Id”.
- `enveloping()` tạo `XMLObject` với ID `object-1`, đưa payload vào Object và trỏ `Reference` tới chính Object. `DOMSignContext(key, doc)` đặt Signature làm gốc của tài liệu.
- `verify()` giải mã cấu trúc Signature bằng `unmarshalXMLSignature()` và gọi `validate()` để kiểm tra cả SignatureValue lẫn Reference digest. Ví dụ có đăng ký lại ID cho Payload ở lúc xác minh và bật `secureValidation` một cách tường minh.
- Lần cuối đổi `Amount` từ `100` thành `900` **sau khi ký**, khiến digest của Reference enveloped khác và xác minh trả `false`.

Đây là ví dụ học tập trên DOM do chương trình tự tạo. Trong ứng dụng thực, phải xử lý dữ liệu XML không tin cậy bằng parser được cấu hình an toàn, xác định Signature/Object/ID mong đợi theo giao thức nghiệp vụ và lấy khóa công khai từ kho tin cậy; không đơn giản chọn “Signature đầu tiên” như demo.

## 6. Kiểm tra và chẩn đoán khi xác minh thất bại

Với `XMLSignature.validate(context) == false`, phân biệt hỏng **chữ ký trên SignedInfo** và hỏng **digest của Reference**; chỉ kiểm tra SignatureValue mà bỏ các Reference là chưa đủ. Với đối tượng `XMLSignature signature` đã unmarshal và `DOMValidateContext context` đã cấu hình đúng, có thể dùng đoạn sau để chẩn đoán (đây là snippet trong một phương thức, không phải lớp Java chạy độc lập):

~~~java
boolean signedInfoOk = signature.getSignatureValue().validate(context);
System.out.println("SignatureValue valid: " + signedInfoOk);
for (Object item : signature.getSignedInfo().getReferences()) {
    Reference ref = (Reference) item;
    System.out.println(ref.getURI() + " digest valid: " + ref.validate(context));
}
~~~

| Triệu chứng | Khả năng cần kiểm tra |
| --- | --- |
| Digest của Reference sai, SignatureValue vẫn đúng | Payload bị thay đổi, mất/đổi whitespace text, transform khác với lúc ký, C14N/namespace khác, ID/URI resolve sang dữ liệu khác hoặc bytes từ nguồn detached đã đổi. |
| SignatureValue sai nhưng Reference đúng | `SignedInfo` bị sửa, canonicalization SignedInfo khác, sai public key hoặc thuật toán/provider không tương thích. |
| Lỗi không resolve `#id` | Thuộc tính ID chưa được đăng ký, ID trùng, fragment không tồn tại, ký sai node. |
| Lỗi khi tải URI ngoài | Base URI, quyền truy cập, resource policy hoặc dereferencer không được phép. |
| Lỗi về transform/algorithm | URI thuật toán unsupported, thứ tự/loại dữ liệu transform không tương thích hoặc secure-validation từ chối. |

Khi debug, lưu lại **ID của nút thực sự được ký**, thuật toán và thứ tự transform, cách cấu hình DOM parser, bytes sau transform (nếu công cụ cho phép) và certificate/key fingerprint. Không “chữa” lỗi bằng cách tắt kiểm tra digest, bỏ một Reference, thay nội dung source cho tới khi true hoặc tắt secure-validation.

## 7. Các yêu cầu an toàn quan trọng

1. **Chống XML Signature Wrapping:** Một XML có thể chứa node độc hại và node cũ được ký hợp lệ. Sau khi xác minh, ứng dụng phải xử lý **đúng node đã được Reference bảo vệ**, kiểm tra đúng đường dẫn/nội dung nghiệp vụ và namespace; không lấy một `Payload` khác theo tên thẻ hoặc “thẻ đầu tiên”. Áp đặt ID duy nhất, nhận biết trùng ID và chỉ chấp nhận URI/Reference/profile đã dự kiến. Đoạn tìm `Signature` đầu tiên và tìm `Payload` theo tên trong demo chỉ phù hợp dữ liệu tự tạo tin cậy.
2. **Không coi KeyInfo là chứng thư tin cậy:** Public key do thông điệp tự khai có thể là khóa của kẻ tấn công. Xác minh chain/trust anchor, hạn dùng, revocation khi policy yêu cầu, `KeyUsage`/`ExtendedKeyUsage`, identity của người ký và liên hệ tới giao dịch trước khi chấp nhận.
3. **Giữ secure validation:** JDK 17 bật chế độ này theo mặc định cho DOMValidateContext; ví dụ chủ động đặt `org.jcp.xml.dsig.secureValidation = Boolean.TRUE`. Chế độ này áp các hạn chế trên thuật toán, transform, số Reference và truy cập nguồn ngoài theo chính sách JDK; system property có thể ghi đè nên môi trường triển khai phải được kiểm soát.
4. **Giới hạn dereference:** Không cho XML không tin cậy tự do truy cập `http:`, `https:`, `file:` hoặc tài nguyên nội bộ qua Reference. Với detached ngoại vi, cung cấp `URIDereferencer` có allowlist, giới hạn kích thước/thời gian, cơ chế ràng buộc tài nguyên và nội dung xác định. Tránh SSRF và dữ liệu thay đổi giữa lúc ký và lúc xác minh.
5. **Chặn XXE/DTD độc hại ở bước parse:** Bật namespace-aware, secure processing và cấu hình cấm DTD/external entity, external DTD/schema access, XInclude theo nhu cầu; xử lý lỗi nếu tính năng hardening không được hỗ trợ. C14N và xác minh chữ ký không thay thế an toàn XML parser.
6. **Dùng thuật toán hiện đại và khóa bảo vệ đúng cách:** SHA-256 trở lên theo profile; RSA-SHA256 với khóa đủ mạnh là ví dụ tương thích rộng. Tránh SHA-1/MD5 và signature method yếu. Khóa riêng thực tế nằm trong KeyStore/HSM hoặc dịch vụ quản lý khóa, không tạo mới ngẫu nhiên sau mỗi lần phát hành như demo.
7. **Không thay đổi DOM sau khi ký:** Formatter, namespace injection, XML schema validation sinh default attribute, sửa encoding theo cách không giữ canonical form hoặc thêm text node có thể làm hỏng digest. Cần quy trình serialize/parse nhất quán và test khả năng liên thông với bên nhận.
8. **Kiểm soát profile nghiệp vụ:** Kiểm tra số lượng Reference, URI mong đợi, thuật toán, signer, timestamp, policy, mục tiêu tài liệu, các thuộc tính quan trọng (số tiền/người nhận) và thứ tự xác minh **trước** khi thực hiện giao dịch. `validate() == true` không tự xác nhận các điều kiện nghiệp vụ đó.

## 8. Cách chọn và bài tập tự kiểm chứng

- Chọn **enveloped** khi tài liệu XML tự mang chữ ký ở bên trong và phần bao ngoài chính là dữ liệu cần ký; nhớ transform loại Signature.
- Chọn **detached** khi muốn giữ payload và chữ ký tách biệt (cùng tài liệu như hai sibling hoặc hai tài nguyên), bảo đảm URI xác định đúng payload và tài nguyên ổn định.
- Chọn **enveloping** khi chủ động đóng gói dữ liệu vào `ds:Object` của chữ ký; kiểm tra Reference đến Object nào, không lấy nhầm Object chỉ vì tên thẻ.

Hãy tự thử trên chương trình: (a) đổi `Payload` sau ký ở detached và enveloping, xác minh phải thất bại; (b) bỏ `ctx.setIdAttributeNS` trong detached, quan sát engine không thể resolve `#data-1` theo cấu hình hiện tại; (c) thay vị trí Signature/envelope và so sánh hành vi inclusive với exclusive C14N trong môi trường có namespace ancestor; (d) kiểm tra rằng đổi thứ tự thuộc tính có thể không đổi canonical output nhưng chèn khoảng trắng text node có thể làm đổi digest. Khi làm bài tập với XML ngoài, luôn bật giới hạn parser/dereferencer và chỉ dùng dữ liệu thử nghiệm tin cậy.

**Tài liệu tham khảo:** [W3C XML Signature Syntax and Processing 1.1](https://www.w3.org/TR/xmldsig-core1/); [Java SE 17 — javax.xml.crypto.dsig](https://docs.oracle.com/en/java/javase/17/docs/api/java.xml.crypto/javax/xml/crypto/dsig/package-summary.html); [Java SE 17 — DOMValidateContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMValidateContext.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-007">19.7 Tạo chữ ký XML bằng DOMSignContext và xác minh bằng DOMValidateContext</a>

<details>
<summary>Xem chi tiết</summary>

# 19.7 Tạo chữ ký XML bằng DOMSignContext và xác minh bằng DOMValidateContext

## 1. Mục tiêu và phạm vi

Sau bài này, bạn có thể:

1. Giải thích vì sao XML Digital Signature (XML-DSig) cần cả `Reference`, `DigestValue` và `SignatureValue`.
2. Tạo chữ ký XML dạng **enveloped** bằng API JSR 105 có sẵn trong Java, đặt `<Signature>` vào đúng nút DOM bằng `DOMSignContext`.
3. Tìm chữ ký, thiết lập `DOMValidateContext`, đăng ký thuộc tính ID, xác minh chữ ký bằng **public key đáng tin cậy** và phân tích nguyên nhân thất bại.
4. Nhận diện các rủi ro thực tế: tham chiếu sai phần tử, XML signature wrapping, XXE, nguồn khóa không đáng tin, thuật toán yếu và thay đổi XML sau khi ký.

**Tình huống:** hệ thống A gửi một phần tử `Invoice` qua XML cho hệ thống B. A dùng private key để ký; B dùng public key đã được cấp phát, xác thực qua kênh tin cậy để kiểm tra nội dung hóa đơn có thay đổi sau lúc ký hay không và chữ ký có tương ứng với khóa mà B tin cậy hay không.

Chữ ký số hỗ trợ **tính toàn vẹn và xác thực nguồn theo chính sách tin cậy khóa**; nó **không mã hóa XML**, không tự bảo đảm người ký được phép thực hiện nghiệp vụ, và không tự bảo đảm mọi phần tử trong tài liệu đều đã được ký. Môi trường ví dụ dùng Java 17+, JDK module `java.xml.crypto` và `java.xml`, không cần thư viện Maven/Gradle bên ngoài.

## 2. XML-DSig thực sự ký điều gì?

Một chữ ký XML thường chứa các phần logic sau:

| Thành phần | Vai trò |
| --- | --- |
| `SignedInfo` | Mô tả các `Reference`, cách chuẩn hóa (`CanonicalizationMethod`) và thuật toán tạo chữ ký (`SignatureMethod`). |
| `Reference URI="#invoice-001"` | Chỉ rõ phần nội dung cần bảo vệ: phần tử có ID `invoice-001`. |
| `Transforms` | Biến đổi dữ liệu tham chiếu trước khi tính digest; với enveloped signature phải loại chính nút `Signature` khỏi dữ liệu được băm. |
| `DigestMethod` / `DigestValue` | Thuật toán băm (ví dụ SHA-256) và giá trị băm của **nội dung được tham chiếu sau transforms**. |
| `SignatureValue` | Chữ ký mật mã của **dạng canonicalized của `SignedInfo`**, được tạo bằng private key. |
| `KeyInfo` | Dữ liệu gợi ý về khóa/chứng thư; có thể không có. Chỉ có `KeyInfo` trong XML **không làm khóa trở nên đáng tin**. |

Luồng ký ở mức khái niệm:

```text
Invoice Id="invoice-001"
    -> Reference URI="#invoice-001"
    -> enveloped-signature transform (loại chính Signature)
    -> exclusive XML canonicalization
    -> SHA-256 -> DigestValue
SignedInfo chứa Reference và DigestValue
    -> canonicalization của SignedInfo
    -> RSA-SHA256 + private key
    -> SignatureValue
DOMSignContext -> chèn Signature vào DOM
```

Luồng xác minh làm ngược lại theo hai phép kiểm tra bổ sung nhau:

- **Reference validation:** giải tham chiếu `URI`, chạy transforms, tính lại digest và so với `DigestValue`. Phần nội dung được tham chiếu có còn nguyên vẹn không?
- **SignatureValue validation:** lấy `SignedInfo` đã canonicalize và kiểm tra `SignatureValue` bằng public key. `SignedInfo` có được ký bởi private key tương ứng không?

`XMLSignature.validate(context)` là **core validation**: thông thường chỉ thành công khi cả chữ ký trên `SignedInfo` lẫn các `Reference` cần kiểm tra đều hợp lệ. Vì vậy có tình huống `SignatureValue` vẫn hợp lệ nhưng `Reference` sai: nội dung hóa đơn bị sửa còn `SignedInfo`/chữ ký không đổi.

## 3. DOMSignContext và DOMValidateContext khác nhau thế nào?

| API | Nhiệm vụ | Điểm bắt buộc phải xác định |
| --- | --- | --- |
| `XMLSignatureFactory.getInstance("DOM")` | Tạo/đọc đối tượng XML-DSig theo DOM. | Cơ chế `DOM` và các thuật toán được hỗ trợ. |
| `DOMSignContext(privateKey, parentNode)` | Cung cấp private key và **nút cha chèn `Signature`** khi ký. | Đúng private key, đúng vị trí, đúng ID được `Reference` trỏ tới. |
| `XMLSignature.sign(signContext)` | Tính digest, chữ ký và marshal phần tử `Signature` vào DOM. | Không thay đổi tùy tiện nội dung đã ký sau khi tạo chữ ký. |
| `DOMValidateContext(trustedPublicKey, signatureNode)` | Cung cấp public key và **nút `Signature` cần xác minh**. | Public key từ nguồn tin cậy; nút chữ ký đúng đối tượng nghiệp vụ. |
| `XMLSignatureFactory.unmarshalXMLSignature(validateContext)` | Đọc cấu trúc `Signature` trong DOM thành đối tượng API. | Tài liệu được parse an toàn, có namespace. |
| `XMLSignature.validate(validateContext)` | Thực hiện core validation. | Chỉ dùng dữ liệu nghiệp vụ sau khi mọi kiểm tra cần thiết đều đạt. |

Cả hai context kế thừa `DOMCryptoContext`, có `setIdAttributeNS(element, namespaceURI, localName)` để đăng ký ánh xạ **giá trị ID → Element**. Một thuộc tính có tên `Id` không tự nhiên được parser coi là kiểu XML ID nếu không có schema/DTD/đăng ký phù hợp. Nếu tham chiếu `#invoice-001` không giải được, phép ký/xác minh có thể thất bại dù chữ ký và khóa đúng.

Đừng tái sử dụng một context cho nhiều cấu trúc chữ ký độc lập. Context gắn với trạng thái, nút và bộ ánh xạ riêng của lần xử lý đó.

## 4. Ví dụ hoàn chỉnh, chạy được trên Java 17+

Ví dụ tạo một `Invoice` bằng DOM, ký chính phần tử này (chữ ký được chèn vào bên trong `Invoice`), kiểm tra, rồi sửa `Amount` từ `1000` thành `2000` và kiểm tra lần nữa. Nó cố tình có `KeyInfo` chứa public key để minh họa cấu trúc XML, nhưng **khâu xác minh không lấy khóa từ XML**: nó nhận public key được bên nhận tin cậy độc lập.

Tạo tệp **`XmlSignatureDemo.java`** với toàn bộ nội dung sau:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class XmlSignatureDemo {
    public static void main(String[] args) throws Exception {
        // Khóa tạo tại chỗ chỉ để demo; hệ thống thật quản lý private key an toàn.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair keys = generator.generateKeyPair();

        // Tạo XML trong bộ nhớ, DOM parser/builder phải namespace-aware.
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        Document document = builderFactory.newDocumentBuilder().newDocument();

        Element invoice = document.createElement("Invoice");
        invoice.setAttribute("Id", "invoice-001");
        document.appendChild(invoice);

        Element amount = document.createElement("Amount");
        amount.setTextContent("1000");
        invoice.appendChild(amount);

        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");

        // Tham chiếu chính Invoice qua Id, không dùng URI rỗng hay URL từ ngoài.
        Reference reference = factory.newReference(
                "#invoice-001",
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(
                        factory.newTransform(
                                Transform.ENVELOPED, (TransformParameterSpec) null),
                        factory.newTransform(
                                CanonicalizationMethod.EXCLUSIVE,
                                (TransformParameterSpec) null)),
                null,
                null);

        SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(
                        CanonicalizationMethod.EXCLUSIVE,
                        (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                List.of(reference));

        // KeyInfo chỉ giúp mô tả public key, không quyết định trust.
        KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
        KeyValue publicKeyInfo = keyInfoFactory.newKeyValue(keys.getPublic());
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(publicKeyInfo));

        // Chèn Signature làm con của Invoice: kiểu enveloped signature.
        DOMSignContext signContext = new DOMSignContext(keys.getPrivate(), invoice);
        signContext.setIdAttributeNS(invoice, null, "Id");

        XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(signContext);

        NodeList signatures = document.getElementsByTagNameNS(
                XMLSignature.XMLNS, "Signature");
        if (signatures.getLength() != 1) {
            throw new IllegalStateException("Expected exactly one XML Signature");
        }
        Element signatureElement = (Element) signatures.item(0);

        verify(factory, keys.getPublic(), invoice, signatureElement, "Original");

        // Thay đổi nội dung đã được Reference bảo vệ nhưng không ký lại.
        amount.setTextContent("2000");
        verify(factory, keys.getPublic(), invoice, signatureElement, "Tampered");
    }

    private static void verify(
            XMLSignatureFactory factory,
            PublicKey trustedPublicKey,
            Element invoice,
            Element signatureElement,
            String label) throws Exception {

        DOMValidateContext context =
                new DOMValidateContext(trustedPublicKey, signatureElement);
        context.setIdAttributeNS(invoice, null, "Id");
        context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);

        XMLSignature signature = factory.unmarshalXMLSignature(context);

        boolean coreValid = signature.validate(context);
        boolean signatureValueValid =
                signature.getSignatureValue().validate(context);
        Reference reference =
                (Reference) signature.getSignedInfo().getReferences().get(0);
        boolean referenceValid = reference.validate(context);

        System.out.printf(
                "%s: core=%s, signatureValue=%s, reference=%s%n",
                label, coreValid, signatureValueValid, referenceValid);
    }
}
```

Trong thư mục có tệp này, chạy:

```bash
javac XmlSignatureDemo.java
java XmlSignatureDemo
```

Đầu ra:

```text
Original: core=true, signatureValue=true, reference=true
Tampered: core=false, signatureValue=true, reference=false
```

Kết quả này đã được kiểm tra với triển khai XML-DSig của JDK. Cặp khóa RSA được sinh mới mỗi lần nên giá trị chữ ký nhị phân khác nhau, nhưng ba kết quả boolean trong hai dòng trên không phụ thuộc giá trị khóa ngẫu nhiên.

### Giải thích từng điểm quyết định của ví dụ

**A. `newReference("#invoice-001", ...)`** bảo vệ phần tử có `Id="invoice-001"` chứ không mặc định bảo vệ mọi nút XML có thể có. `signContext.setIdAttributeNS(invoice, null, "Id")` thông báo cho cơ chế DOM rằng thuộc tính `Id` của phần tử này là ID hợp lệ trong context ký.

**B. `Transform.ENVELOPED`** loại chính phần tử `Signature` khi tính digest của `Invoice`. Nếu băm cả chữ ký mới chèn vào tài liệu, sẽ tạo phụ thuộc vòng: giá trị chữ ký làm thay đổi dữ liệu cần được ký. Phép biến đổi canonicalization bổ sung dùng quy tắc nhất quán khi chuyển cấu trúc XML thành dữ liệu để băm.

**C. `CanonicalizationMethod.EXCLUSIVE`** được chọn cho `SignedInfo` và transform canonicalization của `Reference` trong ví dụ. Hai vị trí này có nhiệm vụ khác nhau: một bên chuẩn hóa `SignedInfo` trước khi tạo/kiểm tra `SignatureValue`, bên kia chuẩn hóa dữ liệu mà `Reference` bảo vệ. Đừng coi chúng là cùng một bước.

**D. `SignatureMethod.RSA_SHA256`** dùng RSA với SHA-256 để ký `SignedInfo`; `DigestMethod.SHA256` dùng SHA-256 để băm nội dung tham chiếu. Cùng xuất hiện SHA-256 nhưng **chúng không phải một thao tác**. Thuật toán ở hai vị trí phải được bên nhận hỗ trợ và được chính sách bảo mật cho phép.

**E. `DOMSignContext(keys.getPrivate(), invoice)`** quyết định cả khóa ký lẫn vị trí marshal. Sau `signature.sign(signContext)`, tài liệu có một phần tử `Signature` trong namespace `http://www.w3.org/2000/09/xmldsig#`. Tên prefix có thể do provider chọn; hãy tìm bằng namespace URI và local name, không dựa vào chuỗi tên `ds:Signature`.

**F. `DOMValidateContext(trustedPublicKey, signatureElement)`** chỉ tới **phần tử chữ ký** chứ không phải `Invoice`. Bên nhận vẫn phải đăng ký `Id` để giải `#invoice-001`. Trong ví dụ cùng một public key sinh trong RAM được đưa trực tiếp vào verifier; ngoài thực tế bên nhận lấy public key/chứng thư qua kho khóa, cấu hình pinning hoặc hạ tầng tin cậy thích hợp.

**G. Kết quả thay đổi:** sửa `Amount` không đụng tới `SignedInfo` nên `signatureValue=true`, nhưng digest tính lại từ `Invoice` khác `DigestValue` đã ký nên `reference=false`, kéo theo `core=false`. Chỉ nhìn `getSignatureValue().validate(...)` sẽ bỏ sót sửa đổi dữ liệu tham chiếu.

## 5. Quy trình xác minh XML nhận từ hệ thống khác

Ví dụ trên chủ đích ký rồi xác minh cùng một DOM để thấy cơ chế. Khi nhận một tài liệu XML đã ký qua mạng, thay bằng chuỗi xử lý rõ ràng:

1. **Nhận XML trong giới hạn cho phép:** giới hạn kích thước byte, độ sâu/nút theo chính sách ứng dụng; parse XML an toàn, namespace-aware, tắt DTD/external entities.
2. **Xác định đúng tài liệu nghiệp vụ:** ví dụ yêu cầu đúng một `Invoice` tại vị trí mà giao thức quy định, có `Id` duy nhất. Không chọn tùy tiện phần tử đầu tiên trùng tên.
3. **Tìm đúng chữ ký:** tìm `Signature` theo namespace XML-DSig, yêu cầu đúng số lượng/vị trí mà giao thức mong đợi; không bỏ qua chữ ký phụ khả nghi.
4. **Nạp public key đáng tin cậy:** từ certificate đã thẩm định chain/trust anchor, thời hạn, mục đích sử dụng, danh tính, cơ chế thu hồi khi áp dụng; hoặc public key đã pin theo đối tác. Đừng quyết định trust bằng `KeyInfo` tự khai báo trong XML.
5. **Tạo `DOMValidateContext` mới** với khóa tin cậy và `Signature` element; đăng ký thuộc tính ID bằng `setIdAttributeNS` (hoặc cơ chế ID chuẩn tương đương) trên chính tài liệu đã parse.
6. **Bật chế độ xác minh an toàn**, giới hạn thuật toán, số `Reference`, transform, URI scheme và nơi truy cập theo policy. Khởi tạo `XMLSignature` bằng `unmarshalXMLSignature(context)`, rồi gọi `validate(context)`.
7. **Kiểm tra phạm vi được ký:** chỉ xử lý phần tử nghiệp vụ thực sự được `Reference` đáng tin trỏ tới. So sánh `Reference.getURI()` với URI/ID và số lượng reference mà giao thức yêu cầu, kiểm tra cấu trúc và các điều kiện nghiệp vụ sau khi core validation thành công.
8. **Chỉ công nhận bản tin nếu tất cả điều kiện đạt.** Một `core=true` là điều kiện mật mã cần thiết, chưa thay thế kiểm tra quyền hạn, replay protection, schema hay quy tắc nghiệp vụ.

### Parse XML không tin cậy một cách an toàn

Đoạn hàm độc lập dưới đây minh họa cấu hình JAXP cho đầu vào XML nhận từ bên ngoài. Trong ứng dụng thực tế, gọi hàm trước khi chọn node `Signature`, rồi thực hiện các bước xác minh ở trên.

```java
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

static Document parseSafely(InputStream xml) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setXIncludeAware(false);
    f.setExpandEntityReferences(false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return f.newDocumentBuilder().parse(xml);
}
```

Đây là **hàm minh họa để ghép vào một lớp Java**, không phải một tệp có `main` chạy độc lập. Khi parser/provider không hỗ trợ cấu hình bảo vệ bắt buộc, phải báo lỗi và từ chối xử lý, không âm thầm bỏ tùy chọn an toàn. Chặn DTD và truy cập entity ngoài giúp hạn chế XXE; riêng việc này không thay thế giới hạn tài nguyên và xác minh chữ ký.

Sau parse, thông tin "đây là ID" có thể không được giữ nếu không có schema/DTD. Với tài liệu kỳ vọng `Invoice Id="invoice-001"`, phần xác minh cần thao tác tương tự:

```java
DOMValidateContext context =
        new DOMValidateContext(trustedPublicKey, signatureElement);
context.setIdAttributeNS(invoiceElement, null, "Id");
context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
XMLSignature signature =
        XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
boolean valid = signature.validate(context);
```

Ở đây `trustedPublicKey`, `signatureElement` và `invoiceElement` là các đối tượng **đã được ứng dụng xác định và kiểm tra trước đó**; khối này minh họa quy trình gọi API, không phải chương trình độc lập. Đặc biệt, phải kiểm tra `invoiceElement` là phần tử nghiệp vụ duy nhất đúng vị trí và ID, tránh tự đăng ký một node do đối tượng tấn công chèn vào.

## 6. KeyInfo: tìm khóa khác với tin cậy khóa

`KeyInfo` có thể chứa `KeyValue`, `X509Data`, `KeyName` hoặc các gợi ý khác. Nó giúp bên nhận **tìm ứng viên khóa** khi đã có chính sách tin cậy; nhưng người sửa XML có thể thay `KeyInfo` và tự tạo một chữ ký hoàn toàn hợp lệ với khóa của họ. Nếu verifier lấy `KeyValue` bất kỳ trong tài liệu làm chuẩn tin cậy, kết quả `validate=true` chỉ chứng minh chữ ký khớp với **khóa do chính tài liệu cung cấp**, không chứng minh được người gửi được ủy quyền.

Hai cách thường gặp:

- **Đã biết public key của đối tác:** đọc từ cấu hình an toàn, trust store hoặc khóa được pin; khởi tạo `new DOMValidateContext(trustedPublicKey, signatureElement)` như ví dụ. Đây là lựa chọn dễ hiểu cho một đối tác có khóa cố định.
- **Có chứng thư X.509 trong `KeyInfo`:** dùng thông tin đó để tìm certificate ứng viên, nhưng phải thẩm định chuỗi PKIX đến trust anchor tin cậy, danh tính/điều kiện sử dụng và trạng thái chứng thư theo policy, rồi mới dùng public key bên trong để xác minh. Một chữ ký XML hợp lệ **không đồng nghĩa** chứng thư hoặc danh tính hợp lệ.

Private key dùng để ký cần được giữ bí mật, thường được quản lý qua keystore/HSM và cơ chế phân quyền. Tạo cặp RSA trong `main` chỉ phù hợp demo và test. Khi chuyển cho đối tác, chuyển XML đã ký và thông tin định danh khóa/chứng thư phù hợp; **không** chuyển private key.

## 7. An toàn khi giải Reference: chống XML signature wrapping

Một lỗi nguy hiểm là **ký đúng nhưng ứng dụng đọc nhầm phần dữ liệu khác**. Giả sử verifier xác minh `Reference URI="#invoice-001"` và nhận `core=true`, nhưng tầng nghiệp vụ lại dùng một phần tử `Invoice` khác do kẻ tấn công chèn vào làm "hóa đơn hiện tại". Chữ ký lúc đó không bảo vệ nội dung mà nghiệp vụ đang tin tưởng.

Biện pháp:

- Quy định rõ **vị trí phần tử được ký**, ID, số lượng `Signature` và `Reference`. Không chỉ gọi `getElementsByTagName("Invoice").item(0)` rồi mặc nhiên dùng nó.
- Bảo đảm các giá trị ID cần thiết là **duy nhất trong tài liệu**; từ chối bản tin có trùng ID, sai namespace hoặc bố cục không đúng hợp đồng.
- Sau core validation, xác nhận `Reference.getURI()` chỉ đến đúng node nghiệp vụ sẽ được dùng. Ứng dụng phải xử lý **chính node được bảo vệ**, không lấy một node khác cùng tên/giá trị trông tương tự.
- Giới hạn loại URI và dereferencer theo chính sách; với giao thức chỉ cho phép `#invoice-001`, hãy từ chối các tham chiếu `http:`, `file:`, tham chiếu rỗng hoặc tham chiếu tới tài nguyên khác. Không tải URL từ dữ liệu không tin cậy chỉ để kiểm chữ ký.
- Chỉ cho phép các thuật toán canonicalization, transform, digest và signature đã được phê duyệt. Đặt `org.jcp.xml.dsig.secureValidation` thành `Boolean.TRUE` trước `unmarshalXMLSignature`; giữ cập nhật chính sách `jdk.xml.dsig.secureValidationPolicy` của JDK, không tắt chế độ này để "chữa" tài liệu lỗi.

**Phân biệt:** giới hạn URI/tham chiếu, xác nhận đối tượng được bảo vệ và quyết định trust là **kiểm tra của ứng dụng**. `signature.validate(context)` không tự hiểu hợp đồng nghiệp vụ hoặc ngăn ứng dụng dùng sai node.

## 8. Canonicalization, namespace và thay đổi sau khi ký

XML có nhiều cách biểu diễn khác nhau. Ví dụ thứ tự thuộc tính và một số cách viết namespace có thể khác mà về cấu trúc vẫn tương đương; canonicalization biến XML về dạng chuẩn để tính digest/chữ ký nhất quán. Tuy nhiên **không có nghĩa mọi thay đổi trình bày đều vô hại**: khoảng trắng trong text node, thêm phần tử, đổi namespace hoặc sửa dữ liệu có thể thay đổi byte sau canonicalization và làm digest sai.

Khi xử lý XML đã ký:

- Không chạy format/pretty print, tái tạo DOM bằng bộ chuyển đổi khác hay biến đổi namespace sau khi ký nếu chưa chứng minh phép biến đổi bảo toàn nội dung canonicalized được bảo vệ.
- Tạo/xác minh trên DOM namespace-aware. Tránh phụ thuộc prefix `ds`: tên chính xác là cặp namespace URI + local name `Signature`.
- `Transform.ENVELOPED` dùng khi `Signature` **nằm trong** nội dung được ký. Với chữ ký detached/enveloping, cấu trúc và transforms khác; không tự giữ nguyên lựa chọn enveloped khi mô hình dữ liệu khác.
- Nếu đưa XML đi qua serialize/parse, phải đảm bảo bộ chuyển đổi không thay đổi dữ liệu được ký và ID được đăng ký lại trong context của DOM sau parse.

## 9. Chẩn đoán chữ ký không hợp lệ

Nếu `signature.validate(context)` trả về `false`, tách rõ hai lớp:

```java
boolean core = signature.validate(context);
boolean signedInfoSignature =
        signature.getSignatureValue().validate(context);
for (Object item : signature.getSignedInfo().getReferences()) {
    Reference ref = (Reference) item;
    System.out.println(ref.getURI() + ": " + ref.validate(context));
}
```

Đây là **khối kiểm tra bổ sung** đặt sau khi bạn đã unmarshal chữ ký với một `DOMValidateContext` hợp lệ, không phải chương trình độc lập. Kết quả chỉ phục vụ điều tra; từ chối bản tin ngay khi bất kỳ phép kiểm tra bắt buộc nào thất bại.

| Hiện tượng | Hướng kiểm tra |
| --- | --- |
| `SignatureValue=false` | Public key có đúng/trusted không, `SignedInfo` có bị sửa, thuật toán có đúng và được chấp nhận không? |
| `SignatureValue=true`, một `Reference=false` | Nội dung tham chiếu đã đổi, URI/ID trỏ sai node, thiếu transform, canonicalization/namespace hoặc serialization khác giữa hai bên. |
| Không tìm thấy `Signature` | Đã parse namespace-aware chưa, tài liệu có đúng định dạng và vị trí chữ ký không? |
| Lỗi `URI cannot be resolved` | Thuộc tính `Id` đã được đăng ký, giá trị ID duy nhất và URI khớp chưa? |
| Lỗi thuật toán/secure validation | Thuật toán hoặc transform bị chính sách JDK từ chối; sửa chuẩn ký và cấu hình phía phát hành theo policy thay vì tắt kiểm tra bảo vệ. |
| `core=true` nhưng nghiệp vụ vẫn đáng ngờ | Kiểm tra certificate/identity, quyền, replay, ID duy nhất, signed-node binding và schema. |

`XMLSignature.validate`, `SignatureValue.validate` và `Reference.validate` có thể cache kết quả trong cùng đối tượng. Khi tài liệu thay đổi, **tạo `DOMValidateContext` mới và unmarshal `XMLSignature` mới** trước khi kiểm lại; không gọi `validate` lần thứ hai trên cùng đối tượng rồi xem kết quả cũ là bằng chứng cho DOM hiện tại. Ví dụ ở mục 4 làm đúng điều này.

## 10. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Dùng public key lấy trực tiếp từ `KeyInfo` làm nguồn tin cậy | Kẻ tấn công có thể ký lại bằng khóa của họ. | Tách tìm khóa khỏi xác thực danh tính/trust; pin key hoặc kiểm tra X.509 chain/policy. |
| Ký `#id` nhưng không đăng ký ID | Resolver có thể không tìm thấy element. | Dùng `setIdAttributeNS` trên context tương ứng, kiểm tra ID duy nhất. |
| Truyền root `Invoice` vào `DOMValidateContext` thay vì `Signature` | Verifier cần node phần tử chữ ký. | Truyền đúng `signatureElement`. |
| Thiếu `Transform.ENVELOPED` cho chữ ký đặt bên trong payload | Digest có thể chứa chính chữ ký và không nhất quán. | Đặt transform phù hợp với kiểu chữ ký đã chọn. |
| Chỉ kiểm `getSignatureValue().validate` | Không phát hiện sửa nội dung tham chiếu. | Dùng `XMLSignature.validate` và xác minh các `Reference`/policy. |
| Parse XML không namespace-aware hoặc cho phép DTD từ nguồn không tin cậy | Không tìm đúng node XML-DSig hoặc mở đường cho XXE. | Parser namespace-aware và cấu hình bảo vệ bắt buộc. |
| Cho phép `Reference` truy cập URL tùy ý | Có thể làm phát sinh truy cập mạng/tệp ngoài phạm vi và làm tăng bề mặt tấn công. | Áp dụng URI allow-list; giới hạn dereferencer và tài nguyên. |
| Ký xong mới pretty print rồi gửi | Khoảng trắng/nút text hoặc namespace có thể bị biến đổi. | Chọn pipeline serialize ổn định; kiểm tra round trip với dữ liệu thực. |
| Thấy `core=true` rồi lấy "Invoice đầu tiên" xử lý | Có thể đọc node khác node được ký (wrapping). | Ràng buộc node nghiệp vụ với `Reference` đã kiểm và cấu trúc hợp đồng. |
| Dùng SHA-1/MD5 hoặc tắt secure validation để tương thích | Thuật toán yếu/policy bị vô hiệu hóa. | Thống nhất thuật toán hiện đại được hỗ trợ, như SHA-256 với RSA hoặc thuật toán đã phê duyệt. |

## 11. Tự luyện tập và tiêu chí hoàn thành

1. **Thử thay đổi payload:** chạy ví dụ, xác nhận `Original` có ba giá trị `true`; sửa `Amount` và quan sát `core=false`, `reference=false` nhưng `signatureValue=true`. Giải thích đúng vì sao.
2. **Thử thay khóa:** tạo một cặp RSA khác làm `trustedPublicKey` ở khâu validate; chữ ký phải không hợp lệ dù `Amount` chưa thay đổi. Đây là lỗi xác thực khóa, không phải digest của payload.
3. **Thử bỏ đăng ký ID:** xóa `context.setIdAttributeNS(invoice, null, "Id")` ở verifier và quan sát lỗi/việc xác minh không giải được URI trong môi trường hiện tại. Sau đó khôi phục đúng ID mapping.
4. **Thử thay đổi `SignedInfo`:** thay thuật toán, URI hoặc `DigestValue` trong XML đã ký mà không ký lại; xem kết quả `SignatureValue` và core validation. Tài liệu không còn đáng tin ngay cả khi phần hóa đơn chưa sửa.
5. **Thiết kế hợp đồng xác minh:** viết quy tắc bên nhận cần thực thi cho `Invoice`: chính xác một ID được ký, không duplicate ID, đúng nơi đặt chữ ký, không tham chiếu ngoài, khóa từ trust store và chỉ xử lý node được ký.

Bạn đã nắm bài khi có thể tự giải thích độc lập: **`DOMSignContext` đặt chữ ký ở đâu và dùng khóa gì; `DOMValidateContext` đọc chữ ký nào và dùng khóa nào; `Reference` bảo vệ node nào; vì sao core validation cần cả digest lẫn SignatureValue; và vì sao một chữ ký mật mã đúng vẫn có thể không đủ để chấp nhận yêu cầu nghiệp vụ.**

## 12. Tài liệu đối chiếu chính thức

- Oracle Javadoc: [`XMLSignatureFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignatureFactory.html), [`XMLSignature`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dsig/XMLSignature.html).
- Oracle Javadoc: [`DOMSignContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMSignContext.html), [`DOMValidateContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMValidateContext.html), [`DOMCryptoContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dom/DOMCryptoContext.html).
- Oracle Java Security Developer's Guide: [XML Digital Signature API Examples](https://docs.oracle.com/en/java/javase/21/security/security-developer-guide.pdf) (phần ký và xác minh XML).
- W3C: [XML Signature Syntax and Processing Version 1.1](https://www.w3.org/TR/xmldsig-core1/).

Các tài liệu này giải thích API/đặc tả; chính sách tin cậy khóa, URI, cấu trúc thông điệp và quyền nghiệp vụ vẫn phải được hệ thống sử dụng chữ ký xác định cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jar-xml-signatures-008">19.8 Tách chọn khóa khỏi kiểm tra khóa tin cậy; giới hạn URI tham chiếu và secure validation</a>

<details>
<summary>Xem chi tiết</summary>

# 19.8 Tách chọn khóa khỏi kiểm tra khóa tin cậy; giới hạn URI tham chiếu và secure validation

## 1. Vấn đề: chữ ký XML đúng về toán học vẫn có thể không đáng tin

Khi nhận một XML có chữ ký số, ứng dụng Java cần trả lời **ít nhất ba câu hỏi độc lập**:

1. **Khóa nào được dùng để kiểm tra chữ ký?** Thông tin gợi ý thường ở phần `ds:KeyInfo`, nhưng có thể được tra từ kho khóa/cấu hình bên ngoài.
2. **Khóa hoặc người ký đó có được ứng dụng tin cậy và được phép ký loại thông điệp này không?** Một cặp khóa tự tạo hoàn toàn có thể ký và tự kiểm tra chữ ký thành công; điều đó không biến người sở hữu khóa thành đối tác được ủy quyền.
3. **Chính xác dữ liệu nào đã được ký và ứng dụng sắp sử dụng dữ liệu nào?** Các `ds:Reference` xác định phạm vi dữ liệu; nếu ứng dụng đọc một nút khác với nút đã được xác minh, kẻ tấn công có thể đánh lừa nghiệp vụ dù chữ ký có kết quả hợp lệ.

Ngoài ba câu hỏi này còn có hai ranh giới về xử lý đầu vào: **không tùy tiện truy cập URI bên ngoài khi dereference** và **không cho XMLDSig xử lý thuật toán/transform/cấu trúc nguy hiểm**. Trong Java, `URIDereferencer` và chế độ `secure validation` là hai công cụ quan trọng, nhưng **không thay thế chính sách xác thực người ký và kiểm tra cấu trúc tài liệu của ứng dụng**.

Phạm vi bài: **XML Digital Signature (XMLDSig, JSR 105)** trong module Java `java.xml.crypto`, chủ yếu DOM API: `XMLSignatureFactory`, `DOMValidateContext`, `KeySelector`, `KeyInfo`, `URIDereferencer`. Phần ký JAR thuộc bài toán độc lập; không dùng thành công của `jarsigner` làm bằng chứng tin cậy cho một XML nhận từ mạng.

## 2. Đọc đúng cấu trúc XMLDSig

Một chữ ký XML có thể có dạng rút gọn:

~~~xml
<Envelope>
  <Payment Id="payment-42">
    <Amount>100</Amount>
  </Payment>
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    <ds:SignedInfo>
      <ds:CanonicalizationMethod Algorithm="..."/>
      <ds:SignatureMethod Algorithm="..."/>
      <ds:Reference URI="#payment-42">
        <ds:Transforms>...</ds:Transforms>
        <ds:DigestMethod Algorithm="..."/>
        <ds:DigestValue>...</ds:DigestValue>
      </ds:Reference>
    </ds:SignedInfo>
    <ds:SignatureValue>...</ds:SignatureValue>
    <ds:KeyInfo>...</ds:KeyInfo>
  </ds:Signature>
</Envelope>
~~~

Đây là **sơ đồ cấu trúc**, không phải chữ ký hợp lệ để copy và sử dụng. Trong tài liệu thật, URI thuật toán, digest, signature value và KeyInfo phải được tạo bằng API/chuẩn tương ứng.

- `SignedInfo` chứa danh sách `Reference`, phương pháp digest/transform và thuật toán ký. `SignatureValue` xác minh tính toàn vẹn/xác thực của **SignedInfo** với khóa đã chọn.
- Với mỗi `Reference`, trình xác minh lấy dữ liệu tại `URI`, áp dụng `Transforms`, tính digest và so với `DigestValue`. `XMLSignature.validate(context)` yêu cầu cả kiểm tra chữ ký lẫn reference digest thành công theo phép kiểm tra cốt lõi.
- `KeyInfo` là **dữ liệu hỗ trợ tìm khóa**: `KeyValue`, `KeyName`, `X509Data`, `RetrievalMethod`... Nó không mặc nhiên đáng tin. Đặc biệt, `KeyInfo` không phải một giấy chứng nhận quyền ký được ứng dụng tự động thừa nhận.
- XMLDSig có thể ký *enveloped* (Signature nằm trong dữ liệu được ký), *enveloping* (dữ liệu nằm trong Signature) hoặc *detached* (Signature tham chiếu dữ liệu bên ngoài phần tử Signature, có thể vẫn ở cùng XML). Mỗi kiểu kéo theo yêu cầu khác nhau về URI, transform và nút ứng dụng sử dụng.

Đừng suy từ `SignatureValue` hợp lệ rằng mọi phần tử trong tài liệu XML đều được ký: **chỉ dữ liệu do Reference xác định** mới được bảo vệ theo quy trình đã kiểm tra.

## 3. Tách bốn quyết định: chọn khóa, tin cậy, toàn vẹn, quyền nghiệp vụ

| Quyết định | Câu hỏi | Nơi chịu trách nhiệm | Điều cần tránh |
| --- | --- | --- | --- |
| **Key selection** | Lấy khóa ứng viên nào cho thuật toán ký? | `KeySelector` hoặc khóa cung cấp cho `DOMValidateContext` | Coi `KeyValue` bất kỳ trong XML như khóa tin cậy |
| **Trust / signer identity** | Khóa ứng viên có được tin cậy cho đối tác, vai trò, tenant và mục đích ký này? | Trust store, key pin, PKIX và policy ứng dụng | Đồng nhất “chữ ký verify được” với “người ký được ủy quyền” |
| **Cryptographic / reference validation** | `SignatureValue` và mọi reference digest có hợp lệ? | `XMLSignature.validate(...)` | Chỉ gọi `getSignatureValue().validate(...)` rồi bỏ qua Reference |
| **Signed data binding + authorization** | Nút sẽ đọc có chính là nút được tham chiếu và đúng vị trí/schema? Nội dung/operation có được chấp nhận? | XML schema, ràng buộc ID/vị trí, nghiệp vụ | Đọc một phần tử khác sau khi verify (*XML Signature Wrapping*) |

Một quy trình an toàn thường là:

~~~text
XML không tin cậy
      │
      ▼
Parse XML an toàn, giới hạn kích thước, DTD/entity
      │
      ▼
Xác định đúng Signature và payload theo hợp đồng tài liệu
      │
      ▼
Chọn ứng viên khóa, kiểm tra trust/purpose/identity
      │
      ▼
Chỉ cho phép Reference URI và transform đã duyệt
      │
      ▼
Bật secure validation → XMLSignature.validate(...)
      │
      ▼
Xác nhận đúng nút/giá trị đã được ký, đúng vị trí và ngữ cảnh
      │
      ▼
Phân quyền nghiệp vụ → chấp nhận hoặc từ chối
~~~

Thứ tự chi tiết có thể thay đổi theo định dạng và thư viện: ví dụ khi kiểm tra chứng chỉ nằm trong `KeyInfo`, cần phân tích một phần metadata không tin cậy để tìm ứng viên trước khi kiểm tra trust. Dù thứ tự kỹ thuật ra sao, **không trao trạng thái trusted/authorized trước khi mọi điều kiện đều đạt**.

### Tại sao `KeyInfo` không thể là nguồn tin cậy duy nhất?

Kẻ tấn công tự tạo cặp khóa `K_attacker`, ký `Payment Amount=999` bằng private key, rồi đính kèm public key `K_attacker` vào `KeyInfo`. Nếu validator lấy chính khóa đó và gọi `validate(...)`, chữ ký hoàn toàn có thể **đúng về toán học**. Nhưng ứng dụng không có căn cứ nào xác nhận `K_attacker` thuộc đối tác thanh toán được ủy quyền.

Một `KeyValueKeySelector` mẫu của tài liệu Oracle chỉ lấy `KeyValue` tương thích thuật toán; chính Oracle lưu ý mẫu này **không xác định khóa có đáng tin hay không**. Tương tự, chứng chỉ X.509 xuất hiện trong `X509Data` không tự chứng minh chuỗi CA hợp lệ, không chứng minh signer được phép ký giao dịch, và không thay thế kiểm tra thời gian/thu hồi/mục đích.

## 4. Thiết kế KeySelector và chính sách trust

`KeySelector` là một abstraction của JSR 105:

~~~java
public KeySelectorResult select(
    KeyInfo keyInfo,
    KeySelector.Purpose purpose,
    AlgorithmMethod method,
    XMLCryptoContext context
) throws KeySelectorException;
~~~

Đây là **chữ ký phương thức để giải thích API**, chưa phải chương trình độc lập. Nó có thể đọc gợi ý từ `KeyInfo` hoặc bỏ qua `KeyInfo` khi khóa đã được chọn an toàn từ bên ngoài.

Hai hướng thường dùng:

**A. Khóa công khai đã được pin hoặc lấy từ kho cấu hình tin cậy.** Ứng dụng xác định đối tác/tenant bằng kênh, cấu hình, metadata đã xác thực hoặc một khóa định danh ngoài XML, rồi tìm public key được phép sử dụng. `KeySelector` chỉ trả khóa nằm trong tập đã tin cậy, sau khi kiểm tra thuật toán và mục đích phù hợp. `KeyInfo` có thể được bỏ qua hoặc chỉ đóng vai trò gợi ý tra cứu. Không chấp nhận tùy tiện đổi public key từ chính XML đang kiểm tra.

**B. Chuỗi X.509.** Có thể dùng `X509Data` để lấy **chứng chỉ ứng viên**, nhưng sau đó phải kiểm tra PKIX theo trust anchor phù hợp, thời hạn, chuỗi CA và chính sách thu hồi (CRL/OCSP tùy hệ thống). Kiểm tra `KeyUsage` cho chữ ký, `ExtendedKeyUsage`/policy liên quan khi có yêu cầu, thuật toán/độ dài khóa và ánh xạ chứng chỉ sang đúng danh tính đối tác/role. Một chuỗi CA hợp lệ **chưa đủ** để suy ra chủ thể được quyền phê duyệt thanh toán; kiểm tra ủy quyền nghiệp vụ là bước riêng.

Trong code production, một số hệ thống làm trust validation **bên trong** `KeySelector`, hoặc xác minh ứng viên qua service trước rồi truyền `PublicKey`/selector chỉ cho phép khóa đã duyệt. Cả hai cách đều được nếu quá trình xác thực không coi lời khai của XML là authority. Khi metadata trả nhiều candidate, phải chọn duy nhất theo chính sách rõ ràng hoặc từ chối mơ hồ; không thử lần lượt các khóa vô hạn.

Tuyệt đối tránh các cách viết theo logic sau:

~~~java
// SAI VỀ TRUST: lấy KeyValue bất kỳ do bên gửi XML tự đính kèm
// rồi kết luận "người ký đáng tin" chỉ vì XMLSignature.validate() == true.
DOMValidateContext context =
    new DOMValidateContext(untrustedKeyValueSelector, signatureNode);
boolean signatureMathValid = xmlSignature.validate(context);
// signatureMathValid != signerTrusted
~~~

Đoạn trên chỉ biểu diễn **anti-pattern**, không phải chương trình chạy độc lập. Nếu muốn dùng `KeyName` như khóa tra cứu, coi đây là **hint trong dữ liệu không tin cậy**, tra một bản ghi đã cấu hình và thực hiện trust/authorization theo server-side policy.

## 5. Giới hạn URI: chỉ xác minh đúng dữ liệu cho phép

Mỗi `Reference` chứa `URI` có thể yêu cầu resolver lấy dữ liệu. Không nên để chuỗi do người gửi đặt tùy ý quyết định tài nguyên mà server sẽ đọc. Ví dụ:

| URI | Ý nghĩa thường gặp | Chính sách của hệ thống chỉ ký một `Payment` nội bộ |
| --- | --- | --- |
| `#payment-42` | Tham chiếu phần tử cùng document qua ID | **Cho phép** nếu ID duy nhất, đúng phần tử/đường dẫn, được đăng ký đúng |
| `#other-id` | Cũng cùng document nhưng khác đối tượng | **Từ chối** nếu hợp đồng yêu cầu đúng `#payment-42` |
| `""` | Tham chiếu toàn document trong nhiều profile XMLDSig | **Từ chối** nếu hợp đồng chỉ cho phép một ID cụ thể |
| `http://...`, `https://...` | Lấy tài nguyên mạng | **Từ chối** đối với profile này; tránh SSRF và phụ thuộc bên ngoài |
| `file:///...`, `../secrets.xml` | Tài nguyên nội bộ/đường dẫn tương đối | **Từ chối**; ngăn đọc file ngoài phạm vi |
| `ftp:...`, `jar:...`, `data:...`, `cid:...` | Các schema/giải pháp resolver khác | **Từ chối theo whitelist** trừ khi có một profile được thiết kế và đánh giá riêng |
| `#xpointer(...)` | Kiểu biểu thức chọn nội dung XML | **Từ chối** nếu profile chỉ hỗ trợ ID đơn giản |

Hai mức kiểm soát nên cùng tồn tại:

1. **Pre-check `SignedInfo.getReferences()`**: số lượng tham chiếu, URI chính xác, digest method, transform method và đích dự kiến. Nếu profile yêu cầu đúng một Reference tới `#payment-42`, hãy kiểm tra đúng điều đó; không để tình huống “chữ ký hợp lệ trên một thứ khác” thành thành công nghiệp vụ.
2. **Chặn ngay trong `URIDereferencer`**: trước khi delegating tới dereferencer của provider, kiểm tra URI nằm trong allowlist. Điều này giảm nguy cơ truy cập tài nguyên bất ngờ nếu có luồng dereference khác. Mỗi profile XML (SAML, SOAP, hóa đơn...) phải có whitelist phù hợp với hợp đồng của nó.

Ví dụ pattern cho cùng-document duy nhất:

~~~java
// Trích đoạn: "fac" là XMLSignatureFactory và "ctx" là DOMValidateContext.
URIDereferencer delegate = fac.getURIDereferencer();

ctx.setURIDereferencer((uriRef, cryptoContext) -> {
    if (!"#payment-42".equals(uriRef.getURI())) {
        throw new URIReferenceException("Reference URI not allowed");
    }
    return delegate.dereference(uriRef, cryptoContext);
});
~~~

Đây là **trích đoạn tích hợp**, có chương trình độc lập hoàn chỉnh ở mục 9. `setURIDereferencer` thay default dereferencer của context bằng resolver kiểm soát chính sách. Nếu ứng dụng thật cần tài nguyên detached, không mở tràn lan `http/file`: dùng đối tượng dữ liệu được cung cấp từ kho đã kiểm soát, xác thực danh tính tài nguyên, giới hạn kích thước/thời gian và trả dữ liệu đã được phép từ một resolver riêng.

**Đặc biệt:** `Reference.getURI()` không phải tất cả cơ chế truy xuất trong XML. `KeyInfo/RetrievalMethod` cũng có thể tham chiếu nội dung, và `Transform` có thể tạo đường đi xử lý phức tạp. Vì vậy phải giới hạn cả KeyInfo dạng được chấp nhận, transform whitelist, parser và các resolver liên quan; trường hợp không sử dụng `RetrievalMethod` thì nên từ chối nó.

## 6. Secure validation của JDK làm gì và chưa làm gì?

JDK hỗ trợ **XML Signature secure validation mode** cho `DOMValidateContext`; trên các bản JDK hiện đại (bao gồm Java 17/21) chế độ này được bật mặc định, nhưng code an toàn vẫn nên biểu thị ý định rõ ràng:

~~~java
DOMValidateContext ctx = new DOMValidateContext(trustedPublicKey, signatureNode);
ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
~~~

Đây là trích đoạn; `trustedPublicKey` phải có nguồn trust rõ ràng. Secure validation được áp dụng **trước khi unmarshal/validate**, để JDK kiểm soát cấu trúc/thuật toán được yêu cầu xử lý. Chính sách thực tế do security property `jdk.xml.dsig.secureValidationPolicy` trong cấu hình Java chi phối. Với cấu hình JDK 21 phổ biến, mode này giới hạn số Reference/Transform, từ chối XSLT transform, các thuật toán ký yếu như MD5/SHA-1, URI scheme `http`/`https`/`file`, vòng lặp `RetrievalMethod`, ID trùng trong các ràng buộc nó kiểm tra và các khóa quá ngắn. **Giới hạn và thuật toán cụ thể cần xem JDK/provider và chính sách đang chạy**, thay vì giả định bất biến ở mọi phiên bản.

Những thứ secure validation **không tự làm**:

- Không biết doanh nghiệp cho phép khóa/certificate nào ký loại chứng từ nào; không thay `KeySelector` và trust store/pinning/PKIX.
- Không biết một `#id` có phải là phần tử nghiệp vụ mà chương trình sẽ đọc; không tự chặn mọi biến thể *wrapping* khi cấu trúc dữ liệu vẫn hợp lệ ở mức XMLDSig.
- Không bảo đảm mọi URI scheme, local resolver hoặc transform do ứng dụng/provider bổ sung đều bị chặn theo profile nghiệp vụ; vẫn cần whitelist.
- Không cấu hình parser DOM an toàn thay cho ứng dụng: DTD/entity và nguồn tài nguyên của XSLT/schema là ranh giới khác.
- Không mặc nhiên xác minh tham chiếu bên trong mọi `Manifest` nếu profile dùng Manifest. Trên JDK, việc validate manifest liên quan property `org.jcp.xml.dsig.validateManifests`; đơn giản nhất là từ chối cấu trúc Manifest nếu hợp đồng không cho phép.

**Chú ý vận hành:** từ JDK 21, system property `-Dorg.jcp.xml.dsig.secureValidation=false` có thể ghi đè giá trị đặt trên context. Không đưa tùy chọn này vào production, phải kiểm tra cấu hình JVM thật và fail-closed nếu môi trường cho phép mode bị tắt trái chính sách. Một số thư viện XMLDSig/JAXP hoặc provider khác có behavior riêng: việc bật một Boolean không phải chứng minh toàn bộ chuỗi đã hardened.

## 7. Parser XML phải an toàn trước khi chạm tới chữ ký

Trước XMLDSig là bước parse input **chưa được tin cậy**. Khi parse XML nhận từ mạng, phải giới hạn payload size, nesting, thời gian, DTD/external entities để ngăn XXE và XML entity expansion. Ví dụ với `DocumentBuilderFactory` của JDK:

~~~java
// Trích đoạn cấu hình parser cho XML nhận từ nguồn ngoài (Java 17+).
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setNamespaceAware(true);
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);
dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

// Document doc = dbf.newDocumentBuilder().parse(trustedSizeLimitedInputStream);
~~~

Các import cần thiết cho trích đoạn là `javax.xml.XMLConstants` và `javax.xml.parsers.DocumentBuilderFactory`. **Fail closed** nếu parser/triển khai không hỗ trợ tính năng bảo vệ bắt buộc; không nuốt ngoại lệ rồi parse lại bằng cấu hình lỏng. Với pipeline phức tạp (SchemaFactory, TransformerFactory, XPath), cấu hình giới hạn external access cho **từng bộ xử lý liên quan**. `secure validation` của XMLDSig và `FEATURE_SECURE_PROCESSING` của JAXP là hai chính sách **khác nhau**.

Sau parse, việc chọn một phần tử ID cũng cần thận trọng: thuộc tính tên `Id`/`ID` **không mặc nhiên được DOM coi là kiểu ID** trong mọi document. Có thể đăng ký bằng `DOMValidateContext.setIdAttributeNS(element, null, "Id")` (hoặc DOM `setIdAttribute`) **sau khi ứng dụng đã xác nhận element dự kiến, tên/id và tính duy nhất**. Không dùng quét ngẫu nhiên “thấy attribute giống Id thì đăng ký hết” trên tài liệu không tin cậy.

## 8. XML Signature Wrapping: chữ ký đúng nhưng dữ liệu sai

Giả sử ứng dụng mong đọc `/Envelope/Payment[@Id='payment-42']`. Chữ ký vẫn chỉ đến bản gốc `Payment Id=payment-42`, trong khi kẻ tấn công chuyển bản gốc vào phần `Archive` và chèn một `Payment Amount=999` ở vị trí ứng dụng đang đọc. Nếu verifier chỉ so `DigestValue` của node cũ, còn logic kinh doanh lại lấy node mới bằng XPath không ràng buộc, **chữ ký có thể hợp lệ mà giao dịch vẫn bị thay đổi**.

Cách đối phó cần làm đồng thời:

1. **Xác định contract** trước khi kiểm tra: một root đúng schema/namespace, một Signature ở vị trí cho phép, một Payment ở vị trí cho phép, số lượng chính xác và ID duy nhất.
2. **Ràng buộc Reference URI với node sẽ sử dụng**: kiểm tra `#payment-42` giải quyết tới **chính đối tượng Element** mà code nghiệp vụ đọc, không chỉ tới một node nào đó có cùng tên/id.
3. **Hạn chế biến thể XMLDSig**: giới hạn transform, reference count và phần tử phát sinh; không chấp nhận tài liệu ngoài profile để “cho linh hoạt”.
4. **Sau khi validate, dùng chính đối tượng DOM đã kiểm tra** hoặc kết quả trích xuất đã ràng buộc; không parse lại, query lại rồi lấy một bản khác/nguồn khác. Không cho phép mutation của document giữa verify và business consumption.
5. **Validate nội dung nghiệp vụ**: ví dụ `Amount` phải có số hợp lệ, người ký có đúng vai trò, transaction id chưa dùng, timestamp/nonce phù hợp, chặn replay theo policy.

Các biện pháp `noDuplicateIds` của JDK có ích nhưng **không đủ** để thay thế ràng buộc vị trí, định danh và signed-data consumption ở tầng ứng dụng.

## 9. Demo Java 17+: khóa tin cậy được pin, Reference chỉ nội bộ, secure validation bật

Demo này là **một chương trình độc lập** dùng JDK tiêu chuẩn, không cần thư viện bên thứ ba. Nó sinh hai cặp RSA thử nghiệm: `trusted` và `outsider`; mỗi người tự ký một XML thanh toán và đặt khóa công khai của mình trong `KeyInfo`. Sau đó nó kiểm tra:

- Với khóa của chính outsider, chữ ký outsider hợp lệ **về toán học**.
- Với khóa `trusted` mà ứng dụng đã pin trước, chữ ký trusted hợp lệ còn chữ ký outsider **không được chấp nhận**.
- Khi sửa giá trị đã ký của giao dịch trusted, reference digest sai và kiểm tra thất bại.

**Giới hạn demo:** tạo DOM trong bộ nhớ để tập trung vào trust/Reference/secure validation. Ở hệ thống nhận XML từ mạng, bắt buộc thêm parser an toàn (mục 7), xác nhận schema và luồng dữ liệu đã được kiểm tra. Đây là minh họa *same-document detached signature* ký riêng node `Payment`, không phải mẫu chữ ký JAR hay mô phỏng PKIX/X.509.

~~~java
// File: XmlSignatureBoundariesDemo.java
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlSignatureBoundariesDemo {
    private static final String EXPECTED_URI = "#payment-42";

    // Khóa này tới từ trust policy của ứng dụng, KHÔNG tới từ XML nhận vào.
    private static final class PinnedKeySelector extends KeySelector {
        private final PublicKey pinned;

        PinnedKeySelector(PublicKey pinned) {
            this.pinned = pinned;
        }

        @Override
        public KeySelectorResult select(
                KeyInfo ignoredKeyInfo,
                Purpose purpose,
                AlgorithmMethod method,
                XMLCryptoContext context) throws KeySelectorException {
            if (purpose != Purpose.VERIFY
                    || !SignatureMethod.RSA_SHA256.equals(method.getAlgorithm())
                    || !"RSA".equals(pinned.getAlgorithm())) {
                throw new KeySelectorException("Unexpected signature algorithm or key");
            }
            return new KeySelectorResult() {
                @Override
                public Key getKey() {
                    return pinned;
                }
            };
        }
    }

    private static Document signPayment(KeyPair signer) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        Document doc = builderFactory.newDocumentBuilder().newDocument();

        Element root = doc.createElement("Envelope");
        doc.appendChild(root);

        Element payment = doc.createElement("Payment");
        payment.setAttribute("Id", "payment-42");
        payment.setIdAttribute("Id", true);
        root.appendChild(payment);

        Element amount = doc.createElement("Amount");
        amount.setTextContent("100");
        payment.appendChild(amount);

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        Reference reference = fac.newReference(
                EXPECTED_URI,
                fac.newDigestMethod(DigestMethod.SHA256, null),
                List.of(fac.newTransform(
                        CanonicalizationMethod.EXCLUSIVE,
                        (TransformParameterSpec) null)),
                null, null);

        SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(
                        CanonicalizationMethod.EXCLUSIVE,
                        (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                List.of(reference));

        // Người ký có thể tự điền bất cứ KeyValue nào.
        // KeyInfo là gợi ý tìm khóa, KHÔNG là chứng cứ về trust.
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        KeyInfo keyInfo = kif.newKeyInfo(
                List.of(kif.newKeyValue(signer.getPublic())));

        XMLSignature signature = fac.newXMLSignature(signedInfo, keyInfo);
        DOMSignContext signContext = new DOMSignContext(signer.getPrivate(), root);
        signContext.setIdAttributeNS(payment, null, "Id");
        signature.sign(signContext);
        return doc;
    }

    private static boolean validatePayment(
            Document doc, PublicKey independentlyTrustedKey) throws Exception {
        Element root = doc.getDocumentElement();
        if (!"Envelope".equals(root.getTagName())) {
            return false;
        }

        NodeList payments = root.getElementsByTagName("Payment");
        NodeList signatures = doc.getElementsByTagNameNS(
                XMLSignature.XMLNS, "Signature");
        if (payments.getLength() != 1 || signatures.getLength() != 1) {
            return false;
        }

        Element payment = (Element) payments.item(0);
        Node signatureNode = signatures.item(0);

        // Contract giản lược: Payment và Signature phải là con trực tiếp.
        if (payment.getParentNode() != root
                || signatureNode.getParentNode() != root
                || !"payment-42".equals(payment.getAttribute("Id"))) {
            return false;
        }

        int duplicateIdCount = 0;
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if ("payment-42".equals(element.getAttribute("Id"))) {
                duplicateIdCount++;
            }
        }
        if (duplicateIdCount != 1) {
            return false;
        }

        // Kiểm tra element ứng dụng thực sự sẽ sử dụng.
        NodeList amountNodes = payment.getElementsByTagName("Amount");
        if (amountNodes.getLength() != 1
                || amountNodes.item(0).getParentNode() != payment) {
            return false;
        }

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext ctx = new DOMValidateContext(
                new PinnedKeySelector(independentlyTrustedKey),
                signatureNode);
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        ctx.setIdAttributeNS(payment, null, "Id");

        // Chỉ cho phép đúng #payment-42. Chặn mọi tài nguyên bên ngoài.
        URIDereferencer delegate = fac.getURIDereferencer();
        ctx.setURIDereferencer((uri, cryptoContext) -> {
            if (!EXPECTED_URI.equals(uri.getURI())) {
                throw new URIReferenceException("Blocked Reference URI");
            }
            return delegate.dereference(uri, cryptoContext);
        });

        XMLSignature xmlSig = fac.unmarshalXMLSignature(ctx);

        if (!SignatureMethod.RSA_SHA256.equals(
                xmlSig.getSignedInfo().getSignatureMethod().getAlgorithm())
                || !CanonicalizationMethod.EXCLUSIVE.equals(
                xmlSig.getSignedInfo().getCanonicalizationMethod().getAlgorithm())) {
            return false;
        }

        List<Reference> refs = xmlSig.getSignedInfo().getReferences();
        if (refs.size() != 1 || !EXPECTED_URI.equals(refs.get(0).getURI())
                || !DigestMethod.SHA256.equals(
                refs.get(0).getDigestMethod().getAlgorithm())
                || refs.get(0).getTransforms().size() != 1
                || !CanonicalizationMethod.EXCLUSIVE.equals(
                refs.get(0).getTransforms().get(0).getAlgorithm())) {
            return false;
        }

        // Phải hợp lệ cả SignatureValue lẫn Reference digest.
        return xmlSig.validate(ctx);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); // Khóa test; không xuất private key ra ngoài.
        KeyPair trusted = generator.generateKeyPair();
        KeyPair outsider = generator.generateKeyPair();

        Document fromTrusted = signPayment(trusted);
        Document fromOutsider = signPayment(outsider);

        // Test toán học: khóa outsider có thể tự xác minh chữ ký của mình.
        System.out.println("outsiderMathValid="
                + validatePayment(fromOutsider, outsider.getPublic()));

        // Trust policy: chỉ public key 'trusted' được ứng dụng chấp nhận.
        System.out.println("trustedAccepted="
                + validatePayment(fromTrusted, trusted.getPublic()));
        System.out.println("outsiderAccepted="
                + validatePayment(fromOutsider, trusted.getPublic()));

        Element signedAmount = (Element) fromTrusted
                .getElementsByTagName("Amount").item(0);
        signedAmount.setTextContent("999"); // Sửa nội dung đã được ký.
        System.out.println("tamperedAccepted="
                + validatePayment(fromTrusted, trusted.getPublic()));
    }
}
~~~

Biên dịch/chạy với JDK 17+ có XMLDSig DOM provider mặc định (không cần dependency Maven/Gradle):

~~~bash
javac XmlSignatureBoundariesDemo.java
java XmlSignatureBoundariesDemo
~~~

Kết quả logic kỳ vọng:

~~~text
outsiderMathValid=true
trustedAccepted=true
outsiderAccepted=false
tamperedAccepted=false
~~~

Các dòng in minh họa khác biệt giữa **cryptographic validity** và **trust decision**. Dòng `outsiderMathValid=true` cố ý truyền khóa outsider như khóa đã chọn nhằm chứng minh khóa tự tạo có thể cho kết quả verify hợp lệ; **không được sử dụng cách đó để quyết định trust trong production**. Trong nghiệp vụ thật, truyền `trustedPublicKey` phải do cấu hình/trust service xác định, không do XML yêu cầu.

`validatePayment` ở trên chỉ mô phỏng profile XML đơn giản, với một Payment và một Signature ở gốc; không chứng minh tổng quát cho mọi profile SOAP, SAML hoặc XML invoice. Với dữ liệu nhận từ ngoài, kiểm tra an toàn XML parser, ràng buộc schema + namespace/ID + vị trí + các phần tử không được ký, đồng thời chống sửa DOM giữa lúc verify và sử dụng. Code ví dụ không bảo đảm chính sách PKIX, tenant authorization, replay protection hay mục đích chứng chỉ.

## 10. Tình huống triển khai thật: ai được phép ký, ký cái gì?

Giả sử dịch vụ nhận một hóa đơn XML:

- Mỗi khách hàng/nhà cung cấp có tập public key hoặc CA/trust anchor được cấu hình độc lập. Một `KeyName` trong XML chỉ giúp tìm candidate trong **đúng namespace tenant** đã xác định bằng nguồn tin cậy khác; tuyệt đối không nhận tenant mới chỉ từ tên tự khai.
- XML có thể chứa `X509Data`. Hệ thống phải phân giải chứng chỉ ứng viên, kiểm tra chuỗi theo đúng trust anchors, mục đích chữ ký, thời gian và chính sách thu hồi. Mapping của certificate subject/SAN đến nhà cung cấp phải có quy tắc ứng dụng, không chỉ dựa vào tên chuỗi.
- `Reference` chỉ được trỏ đến một hoặc một nhóm phần tử invoice đã quy định: ví dụ `#invoice-body-123`. Không chấp nhận URL ngoài, attachment tùy ý hay transform không nằm trong profile; nếu phải ký attachment, thiết kế cơ chế tải tệp/cấp nội dung riêng, không cho DOM engine tự fetch.
- Khi `XMLSignature.validate` thành công, ứng dụng vẫn phải đối chiếu invoice ID, đơn vị tiền, số tiền, đối tác ký, trạng thái invoice và giới hạn hành động. Chống replay bằng message ID, transaction log hoặc nonce theo business policy.
- Những trường cực kỳ nhạy cảm như người thụ hưởng, tài khoản chuyển tiền, amount, currency, action type và tenant ID phải nằm **trong vùng được Reference bảo vệ**, nếu đó là dữ liệu tạo ra quyết định bảo mật.

Điểm ranh giới: **trust key** có thể đúng nhưng **authorization** vẫn sai (chứng chỉ của nhân viên được tin cậy nhưng nhân viên không có quyền phê duyệt hóa đơn trị giá đó). Ngược lại, đúng doanh nghiệp và đúng role nhưng thông điệp bị sửa digest thì cũng phải từ chối.

## 11. Các trường hợp tấn công, triệu chứng và hướng xử lý

| Tình huống | Vì sao nguy hiểm | Hành vi an toàn |
| --- | --- | --- |
| Attacker tự tạo cặp khóa rồi gửi `KeyValue` tương ứng | Validate toán học có thể thành công | Chỉ dùng khóa từ trust policy hoặc xác minh đầy đủ X.509 + quyền ký |
| `KeyName="admin-key"` trong XML | Tên do bên gửi tự khai | Tra lookup trong kho cấu hình đã được phân vùng và xác thực |
| `Reference URI="http://169.254.169.254/..."` | SSRF tới metadata hoặc máy nội bộ | Chặn mọi URI ngoài allowlist trước dereference; không rely riêng vào provider defaults |
| `Reference URI="file:///..."` | Đọc file máy chủ nếu resolver cho phép | Không nhận URI file; dùng resolver chỉ nội bộ và giới hạn quyền |
| `Reference URI="#signed-old"`, business đọc `#new` | XML Signature Wrapping | Kiểm tra đúng element ID/namespace/vị trí và sử dụng chính node đã verify |
| ID trùng ở nhiều nơi | Resolver chọn sai node | Phát hiện ID trùng; kiểm tra kiểu ID và cấu trúc, từ chối |
| Transform XSLT/XPath phức tạp | Truy xuất/tính toán ngoài ý muốn, DoS | Secure validation + whitelist transform theo profile |
| SHA-1/MD5 hoặc khóa quá yếu | Có rủi ro mật mã | Secure validation và policy thuật toán hiện hành của tổ chức |
| Tắt `secureValidation` để “sửa lỗi verify” | Mở lại cơ chế nguy hiểm | Tìm nguyên nhân policy/legacy; thay chữ ký/cơ chế, không hạ hàng rào production |
| Chữ ký hợp lệ nhưng signer sai tenant/role | Vượt quyền nghiệp vụ | Map signer identity đến tenant, purpose, role và quyết định ủy quyền |
| Chữ ký hợp lệ nhưng Message ID được gửi lại | Replay attack | Chặn duplicate theo nonce/transaction ID và thời hạn |

Khi điều tra lỗi, **không log dữ liệu nhạy cảm** từ XML, khóa riêng, hay nội dung resolver lấy được. Có thể ghi an toàn metadata có giới hạn như mã lỗi, correlation ID, loại algorithm, URI bị chặn đã được lọc, và kết quả từng giai đoạn.

## 12. Cách kiểm thử theo các ranh giới bảo mật

Nên có các nhóm ca kiểm thử độc lập:

1. **Positive control:** XML hợp lệ, signer đã pin/PKIX hợp lệ, một Reference đúng ID, transform được cho phép, nội dung ở đúng vị trí → pass.
2. **Trust negative:** cùng nội dung nhưng dùng khóa outsider tự ký; KeyInfo/KeyName tự khai “trusted”; chứng chỉ hết hạn/CA không tin cậy/không đúng purpose; signer hợp lệ nhưng sai tenant hoặc chưa được ủy quyền → fail.
3. **Integrity negative:** sửa nội dung đã ký, đổi `DigestValue`, thay `SignedInfo` hoặc `SignatureValue` → fail.
4. **URI negative:** `#wrong-id`, URI rỗng, relative path, `file:`, `https:`, URI với encoding khác, XPointer không được hỗ trợ → fail *trước khi truy cập tài nguyên ngoài*. Kiểm thử resolver bằng mock để chứng minh không có network/filesystem access.
5. **Structure negative:** duplicate ID, duplicate Signature, payload bị bọc/chuyển, thêm payload giả ở vị trí business đọc, sai namespace, nhiều Reference ngoài hợp đồng → fail.
6. **Resource negative:** DTD/XXE, entity expansion, XML quá lớn, nhiều reference/transform, XSLT transform, thuật toán yếu → bị từ chối hoặc xử lý trong giới hạn tài nguyên.
7. **Runtime policy negative:** khởi động với config cố tình tắt secure validation hoặc thay JDK/provider → policy triển khai phải phát hiện và không cho hệ thống âm thầm vận hành với kiểm tra yếu hơn.

### Gỡ lỗi có kiểm soát khi `XMLSignature.validate(...)` trả false

Ở môi trường test với đầu vào đã kiểm soát, có thể tách lỗi phần chữ ký và lỗi reference:

~~~java
// Trích đoạn chẩn đoán; không dùng để kết luận hợp lệ từng phần.
boolean signatureValueValid = xmlSig.getSignatureValue().validate(ctx);
boolean firstReferenceValid = refs.get(0).validate(ctx);
System.out.println("signatureValueValid=" + signatureValueValid);
System.out.println("firstReferenceValid=" + firstReferenceValid);
~~~

`signatureValueValid=true` nhưng `firstReferenceValid=false` thường hướng điều tra tới URI/ID/transform/canonicalization hoặc payload đã thay đổi; ngược lại cần kiểm tra khóa/thuật toán/SignatureValue. **Dù một thành phần trả true, chỉ chấp nhận tài liệu khi full validation và toàn bộ trust/structure/business policy đều pass**.

Không bật property cache reference data hoặc dump raw dereferenced bytes trong production khi không thật cần thiết; dữ liệu đó có thể nhạy cảm và gây tốn bộ nhớ.

## 13. Checklist triển khai thực tế

- [ ] Có profile XMLDSig được đặc tả: root/namespace/schema, chữ ký ở đâu, signed payload nào, số Reference, ID policy, algorithms và transforms được phép.
- [ ] Dữ liệu XML được parse an toàn; DTD/external entities và nguồn truy xuất ngoài bị chặn, input size và tài nguyên xử lý được giới hạn.
- [ ] Mọi khóa xác minh đến từ trust policy hoặc được xác thực bằng pin/PKIX đúng trust anchor; `KeyInfo` chỉ cung cấp candidate/hint.
- [ ] Trust được gắn với danh tính đối tác, tenant, vai trò và loại thông điệp; có xử lý vòng đời khóa, rotation, revoke.
- [ ] Bật secure validation trên validation context; kiểm tra JDK/provider và cấu hình JVM không vô hiệu hóa chính sách.
- [ ] Mọi `Reference URI` và transform được kiểm soát trước validate; `URIDereferencer` fail-closed; xử lý `KeyInfo/RetrievalMethod` theo whitelist.
- [ ] Có duy nhất và xác minh đúng node ID/namespace/vị trí; nghiệp vụ chỉ tiêu thụ node đã xác minh.
- [ ] Gọi `XMLSignature.validate(ctx)` và kiểm tra kết quả; lỗi hoặc exception đều từ chối, không có fallback yếu.
- [ ] Áp dụng authorization, input validation và chống replay sau verify; log an toàn và giám sát bất thường.
- [ ] Có test negative cho outsider key, URI ngoài, duplicate ID, wrapping, key rotation, thay đổi thuật toán và điều kiện môi trường.

## 14. Câu hỏi tự kiểm tra

**1. XML chứa `KeyValue` và `validate(ctx)` trả true. Có thể ghi “trusted signer” không?** Không. Chỉ chứng minh phép xác minh với public key đã dùng. Còn phải xác nhận khóa đó tin cậy và được phép ký đúng thông điệp.

**2. Chỉ bật `secureValidation=true` đã đủ để ngăn SSRF?** Không. Mode có các hạn chế URI mặc định, nhưng ứng dụng vẫn cần allowlist chặt cho profile và mọi resolver/transform/tài nguyên bên ngoài. Đối với nội dung chỉ cùng-document, chặn tuyệt đối URI khác `#id` đã định nghĩa.

**3. Nếu payload đã ký hợp lệ nhưng ứng dụng dùng payload giả được chèn ở vị trí mới, kết quả nghiệp vụ có đáng tin không?** Không. Đây là failure của signed-data binding/XML Signature Wrapping. Phải kiểm tra đúng element theo ID, vị trí, namespace và dùng element đó.

**4. Chứng chỉ X.509 trong `KeyInfo` do một CA công khai phát hành, vậy được quyền ký hóa đơn của công ty A?** Chưa đủ. Cần PKIX với trust policy đúng, mục đích chứng chỉ, ánh xạ tổ chức và quyền ký loại chứng từ đó.

**5. Vì sao phải kiểm tra `Reference` ngay cả khi `SignatureValue` đúng?** Bởi SignatureValue xác minh SignedInfo; Reference digest xác nhận dữ liệu thật sau dereference/transform có đúng nội dung đã ký hay không.

## 15. Nguồn chính thống để đối chiếu

- Oracle Java SE 21 — **Java XML Digital Signature API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/module-summary.html
- Oracle Java SE 21 — **`KeySelector`**: https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/KeySelector.html
- Oracle Java SE 21 — **`DOMValidateContext`**: https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/dsig/dom/DOMValidateContext.html
- Oracle Java SE 21 — **`URIDereferencer`**: https://docs.oracle.com/en/java/javase/21/docs/api/java.xml.crypto/javax/xml/crypto/URIDereferencer.html
- Oracle Security Developer's Guide — **XML Digital Signature API Overview and Tutorial**: https://docs.oracle.com/en/java/javase/21/security/java-xml-digital-signature-api-overview-and-tutorial.html
- Oracle JDK 21 Release Notes — **secureValidation system property**: https://www.oracle.com/java/technologies/javase/21-relnote-issues.html
- W3C — **XML Signature Syntax and Processing 1.1**: https://www.w3.org/TR/xmldsig-core1/
- OWASP — **XML External Entity Prevention Cheat Sheet**: https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html

**Thông điệp cốt lõi:** `KeySelector` tìm khóa; **trust policy** xác minh khóa/người ký có được chấp nhận hay không; `XMLSignature.validate` kiểm tra chữ ký và digest; **URI whitelist + secure validation + parser hardening + signed-element binding** bảo đảm quá trình kiểm tra không làm điều ngoài dự kiến và dữ liệu nghiệp vụ thực sự là dữ liệu đã ký. Cần đủ tất cả các điều kiện đó mới có thể xem XML nhận từ bên ngoài là đáng tin cho hành động dự kiến.

</details>

- [Quay lại đầu trang](#back-to-top)
